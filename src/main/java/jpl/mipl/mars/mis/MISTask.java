package jpl.mipl.mars.mis;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.json.JsonArrayBuilder;

import org.apache.commons.io.IOUtils;

import jpl.mipl.mars.mis.unpackers.Unpacker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Marsette Vona
 */
public class MISTask {
        
    public static final Logger log = LoggerFactory.getLogger(MISTask.class);

    public static final double DEF_HISTOGRAM_BIN_WIDTH = 0.001;
    public static final int DEF_HISTOGRAM_MAX_BINS = 10000;

    public static boolean debug;
    public static boolean quiet;

    public static String[] ignoreSubdirs = null;
    public static Pattern[] rdrUrlWhitelistPatterns = null;

    public static double histogramBinWidth = DEF_HISTOGRAM_BIN_WIDTH;
    public static int histogramMaxBins = DEF_HISTOGRAM_MAX_BINS;

    public static int maxCachedSamples = SparseImage.DEF_MAX_CACHED_SAMPLES;
    public static int maxCachedRecordBytes = SparseImage.DEF_MAX_CACHED_RECORD_BYTES;

    public final MISParams params;
    public final boolean isS3, isHTTP;
    public final S3Helper s3;

    private final String pfx;

    public  static void setLogLevel(Logger log, String level) {
        try {
            var clazz = log.getClass(); //org.slf4j.impl.SimpleLogger
            var cll = clazz.getDeclaredField("currentLogLevel");
            var llo = clazz.getDeclaredField("LOG_LEVEL_" + level);
            cll.setAccessible(true);
            llo.setAccessible(true);
            cll.set(log, llo.getInt(log));
        } catch (Exception ex) {
            log.error("error setting log level " + level, ex);
        }
    }

    public static String getVersion(Class clazz) {
        InputStream str = null;
        try {
            str = clazz.getResourceAsStream("/resources/version");
            return str != null ? new String(IOUtils.toByteArray(str), StandardCharsets.UTF_8).trim() : "UNKNOWN";
        } catch (Exception ex) {
            return "UNKNOWN, error: " + ex.toString();
        } finally {
            if (str != null) {
                try {
                    str.close();
                } catch (Exception ex) {
                    //ignore
                }
            }
        }
    }

    public static String hms(double ms)
    {
        String sign = ms < 0 ? "-" : "";
        if (ms == 0) {
            return "0s";
        } else if (ms < 1e3) {
            return String.format("%s%dms", sign, (int)ms);
        } else if (ms < 60 * 1e3) {
            double s = 1e-3 * ms;
            return String.format("%s%.3fs", sign, s);
        } else if (ms < 60 * 60 * 1e3) {
            int s = (int)(1e-3 * ms);
            return String.format("%s%dm%ds", sign, s / 60, s % 60);
        } else {
            int s = (int)(1e-3 * ms);
            return String.format("%s%dh%dm%ds", sign, s / (60 * 60), (s / 60) % 60, s % 60);
        }
    }

    private static volatile String version = null;
    public static String getVersion() {
        if (version == null) {
            version = getVersion(MISTask.class);
        }
        return version;
    }

    public MISTask(MISParams params, S3Helper s3) {
        isS3 = S3Helper.isS3Url(params.imgUrl); //check first to catch http[s]://BUCKET.s3-REGION.amazonaws.com/KEY
        isHTTP = !isS3 && HTTPHelper.isHTTPUrl(params.imgUrl);
        if (isS3 && s3 == null) {
            throw new IllegalArgumentException("S3 service required for URL " + params.imgUrl);
        }
        this.params = params;
        this.s3 = s3;
        pfx = params.pfx;
    }

    public MISTask(MISParams params) { this(params, null); }

    public boolean isStreaming() {
        if (params instanceof VolumeMISParams) {
            var vp = (VolumeMISParams)params;
            return vp.surface == null;
        }
        return false;
    }

    public String getLogMessage() {
        return params.getLogMessage();
    }

    public String run() throws IOException {
        long start = System.currentTimeMillis();
        String ret;
        if (params instanceof ExplicitMISParams) {
            ret = run((ExplicitMISParams)params);
        } else if (params instanceof VolumeMISParams) {
            ret = run((VolumeMISParams)params);
        } else {
            throw new IllegalArgumentException("unknown task type " + params.getClass().getSimpleName());
        }
        if (!quiet) {
            log.info(pfx + "processed in " + hms(System.currentTimeMillis() - start));
        }
        return ret;
    }

    public String run(ExplicitMISParams params) throws IOException {

        String url = (isS3 || isHTTP) ? params.imgUrl : SparseImage.getLocalPath(params.imgUrl);
        if ((isS3 && !s3.doesObjectExist(url)) || (isHTTP && !HTTPHelper.exists(url)) ||
            (!isS3 && !isHTTP && !(new File(url)).exists())) {
            throw new FileNotFoundException(url);
        }

        if (!quiet) {
            log.info(pfx + "sampling " + params.lines.length + " pixels as type=" + params.rdrType +
                     ", label=" + params.label + ", interp=" + params.interp);
        }

        boolean fullRead = false;
        var img = new SparseImage(params.imgUrl, debug, pfx, maxCachedSamples, maxCachedRecordBytes, fullRead, s3);

        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        JsonObjectBuilder objectBuilder = null;
        String json = null;
        for (int i = 0; i < params.lines.length; i++) {

            //note: double can store all 32 bit int and 32 bit float values without loss
            double[] pixel =
                img.read(params.lines[i] - params.origin, params.samples[i] - params.origin, params.interp);

            if (params.lines.length > 1 || params.batch) {
                objectBuilder = Json.createObjectBuilder();
                objectBuilder.add("line", params.lines[i]);
                objectBuilder.add("sample", params.samples[i]);
                objectBuilder.add("origin", params.origin);
            }
            
            if (params.label) {
                JsonObjectBuilder unpacked = unpack(pixel, params.rdrType);
                if (objectBuilder != null) {
                    objectBuilder.add("data", unpacked);
                } else {
                    json = unpacked.build().toString();
                }
            } else {
                JsonArrayBuilder arr = toJsonArray(pixel, img.isFloat());
                if (objectBuilder != null) {
                    objectBuilder.add("data", arr);
                } else {
                    json = arr.build().toString();
                }
            }

            if (objectBuilder != null) {
                arrayBuilder.add(objectBuilder);
            }
        }

        return json != null ? json : arrayBuilder.build().toString();
    }

    public String run(VolumeMISParams params) throws IOException {
        if (params.surface == null) {
            throw new IllegalArgumentException("volume query without surface must be run in streaming mode");
        }
        var stats = new PlaneStats(params.surface);
        forEachPoint(new PointHandler() {
                public void handleInlier(double[] p, int n, int index, int indexInside) {
                    stats.addPoint(p);
                }
            });
        return stats.toString();
    }

    public void run(OutputStream out) throws IOException {

        if (!(params instanceof VolumeMISParams)) {
            throw new IllegalArgumentException("explicit query must be run in non-streaming mode");
        }
        var vp = (VolumeMISParams)params;

        if (vp.surface != null) {
            throw new IllegalArgumentException("volume query with surface must be run in non-streaming mode");
        }

        long start = System.currentTimeMillis();

        out.write("[\n".getBytes("UTF-8"));
        forEachPoint(new PointHandler() {
                public void handleInlier(double[] p, int n, int index, int indexInside) throws IOException {
                    if (indexInside > 0) {
                        out.write(",\n".getBytes("UTF-8"));
                    }
                    out.write("[".getBytes("UTF-8"));
                    out.write(Double.valueOf(p[0]).toString().getBytes("UTF-8"));
                    out.write(",".getBytes("UTF-8"));
                    out.write(Double.valueOf(p[1]).toString().getBytes("UTF-8"));
                    out.write(",".getBytes("UTF-8"));
                    out.write(Double.valueOf(p[2]).toString().getBytes("UTF-8"));
                    out.write("]".getBytes("UTF-8"));
                }
            });

        out.write("\n]\n".getBytes("UTF-8"));

        if (!quiet) {
            log.info(pfx + "processed in " + hms(System.currentTimeMillis() - start));
        }
    }

    private static JsonObjectBuilder unpack(double[] pixel, String rdrType) throws IOException {

        int[] ipixel = new int[pixel.length];
        float[] fpixel = new float[pixel.length];
        for (int b = 0; b < pixel.length; b++) {
            ipixel[b] = (int)pixel[b];
            fpixel[b] = (float)pixel[b];
        }

        Map<String, Object> unpacked = Unpacker.unpack(rdrType, fpixel, ipixel);

        JsonObjectBuilder builder = Json.createObjectBuilder();

        for (Map.Entry<String, Object> entry : unpacked.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof String) {
                builder.add(key, (String)val);
            } else if (val instanceof Float) {
                float fv = ((Float)val).floatValue();
                if (!Float.isInfinite(fv) && !Float.isNaN(fv)) {
                    builder.add(key, fv);
                } else {
                    builder.add(key, ((Float)val).toString());
                }
            } else if (val instanceof Double) {
                double dv = ((Double)val).doubleValue();
                if (!Double.isInfinite(dv) && !Double.isNaN(dv)) {
                    builder.add(key, dv);
                } else {
                    builder.add(key, ((Double)val).toString());
                }
            } else if (val instanceof Integer) {
                builder.add(key, ((Integer)val).intValue());
            } else {
                throw new IOException("unsupported unpacked data type " + val.getClass().getName());
            }
        }

        return builder;
    }
        
    private static JsonArrayBuilder toJsonArray(double[] pixel, boolean isFloat)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (int b = 0; b < pixel.length; b++) {
            if (isFloat) {
                if (!Double.isInfinite(pixel[b]) && !Double.isNaN(pixel[b])) {
                    builder.add(pixel[b]);
                } else {
                    builder.add(Double.valueOf(pixel[b]).toString());
                }
            } else {
                builder.add((int)pixel[b]);
            }
        }
        return builder;
    }

    public static double signedDistance(double[] point, double[] plane) {
        return point[0] * plane[0] + point[1] * plane[1] + point[2] * plane[2] - plane[3];
    }

    public static double distanceSquared(double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double dz = b[2] - a[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private interface PointHandler {
        void handleInlier(double[] p, int n, int index, int indexInside) throws IOException;
    }

    private boolean validPoint(double[] p) {
        //TODO also check for invalid pixel values according to RDR header
        return p[0] != 0 || p[1] != 0 || p[2] != 0;
    }

    private boolean insideVolume(double[] p, VolumeMISParams params) {
        for (int i = 0; params.planes != null && i < params.planes.length; i++) {
            if (signedDistance(p, params.planes[i]) > 0) {
                return false;
            }
        }
        for (int i = 0; params.spheres != null && i < params.spheres.length; i++) {
            if (distanceSquared(p, params.spheres[i]) > params.sphereRadiusSquared[i]) {
                return false;
            }
        }
        return true;
    }

    private void forEachPoint(PointHandler handler) throws IOException {
        if (!(params instanceof VolumeMISParams)) {
            throw new IllegalArgumentException("volume iteration requires volume params");
        }
        boolean fullRead = true;
        var img = new SparseImage(params.imgUrl, debug, pfx, maxCachedSamples, maxCachedRecordBytes, fullRead, s3);
        if (img.bands != 3) {
            throw new IllegalArgumentException("requires three band image");
        }
        int n = img.lines * img.samples;
        double[] p = new double[3];
        int index = 0, indexInside = 0;
        for (int line = 0; line < img.lines; line++) {
            for (int sample = 0; sample < img.samples; sample++) {
                img.read(line, sample, SparseImage.InterpMode.none, p);
                if (validPoint(p) && insideVolume(p, (VolumeMISParams)params)) {
                    handler.handleInlier(p, n, index, indexInside);
                    indexInside++;
                }
                index++;
                if (!quiet && (index == n || index % 100000 == 0)) {
                    log.info(pfx + " processed " + index + " points, " + indexInside + " inside volume");
                }
            }
        }
    }

    private static class PlaneStats {

        private final double[] plane;

        private int numPoints;

        private double minDistance, maxDistance;

        private double outliersBelow, outliersAbove;

        private final int[] histogramBelow, histogramAbove;

        public PlaneStats(double[] plane) {
            this.plane = plane;
            minDistance = Double.POSITIVE_INFINITY;
            maxDistance = Double.NEGATIVE_INFINITY;
            histogramBelow = new int[MISTask.histogramMaxBins];
            histogramAbove = new int[MISTask.histogramMaxBins];
        }

        public void addPoint(double[] p) {
            numPoints++;
            double d = MISTask.signedDistance(p, plane);
            if (d < minDistance) {
                minDistance = d;
            }
            if (d > maxDistance) {
                maxDistance = d;
            }
            double w = MISTask.histogramBinWidth;
            int n = MISTask.histogramMaxBins;
            if (d >= n * w) {
                outliersAbove++;
            } else if (d >= 0) {
                histogramAbove[(int)Math.min(d / w, n - 1)]++; //each bucket is half open with the bottom inclusive
            } else if (d >= -(n * w)) {
                double f = -d / w;
                int i = (int)f;
                if (i > n) {
                    outliersBelow++; //shouldn't get here but whatever
                } else if (i > 0 && i == f) { //each bucket is half open with the bottom inclusive
                    histogramBelow[i - 1]++;
                } else {
                    histogramBelow[(int)Math.min(i, n - 1)]++;
                }
            } else {
                outliersBelow++;
            }
        }

        private static JsonArrayBuilder toJsonArray(int[] histogram) {
            int n = 0;
            for (int i = 0; i < histogram.length; i++) {
                if (histogram[i] > 0) {
                    n = i + 1;
                }
            }
            JsonArrayBuilder builder = Json.createArrayBuilder();
            for (int i = 0; i < n; i++) {
                builder.add(histogram[i]);
            }
            return builder;
        }

        public String toString() {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            builder.add("num_points", numPoints);
            builder.add("min_distance", numPoints > 0 ? minDistance : 0);
            builder.add("max_distance", numPoints > 0 ? maxDistance : 0);
            builder.add("histogram_bin_width", MISTask.histogramBinWidth);
            builder.add("histogram_outliers_below", outliersBelow);
            builder.add("histogram_outliers_above", outliersAbove);
            builder.add("histogram_below", toJsonArray(histogramBelow));
            builder.add("histogram_above", toJsonArray(histogramAbove));
            return builder.build().toString();
        }
    }
}
