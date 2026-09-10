package jpl.mipl.mars.mis;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 *
 * @author Marsette Vona
 */
public class SparseImage {

    public static final int DEF_MAX_CACHED_SAMPLES = 1000 * 1000;
    public static final int DEF_MAX_CACHED_RECORD_BYTES = 10 * 1000 * 1000;

    public static volatile String fsInputDir;

    public enum DataType { U8, S16, S32, F32, F64 };
    public enum InterpMode { none, interp_dn, interp_nonzero };
    public enum CompressionType { NONE, BASIC, BASIC2 };
    
    public final int bands;
    public final int lines;
    public final int samples;
    
    public final ByteOrder byteOrder;
    public final DataType dataType;
    public final CompressionType compressionType;

    public final int maxCachedSamples;
    public final int maxCachedRecordBytes;

    public final boolean fullRead;

    private final long imageStartByte;
    private final int bytesPerSample;
    
    private final S3Helper s3;
    private final String s3Bucket;
    private final String s3Key;

    private final File inputFile;
    private final String inputURL;

    private final boolean debug;

    private final String pfx;

    private LinkedHashMap<CacheKey, Double> sampleCache;
    private LinkedHashMap<CacheKey, byte[]> recordCache;
    private HashMap<CacheKey, Long> compressedStartCache;

    private byte[][][] fullReadBufs;

    private final int[] decompressorCoverage = new int[8];

    private static final Logger log = LoggerFactory.getLogger(SparseImage.class);

    public static String getLocalPath(String url) {
        url = url.replace("\\", "/");
        if (url.toLowerCase().startsWith("file://")) {
            url = url.substring(7);
        }
        if (fsInputDir != null && fsInputDir.length() > 0) {
            url = fsInputDir + url;
        }
        return url;
    }

    public SparseImage(String url, boolean debug, String pfx, int maxCachedSamples, int maxCachedRecordBytes,
                       boolean fullRead, S3Helper s3) throws IOException {

        this.debug = debug;
        this.pfx = pfx != null ? pfx : ("[" + url + "] ");

        this.maxCachedSamples = maxCachedSamples;
        this.maxCachedRecordBytes = maxCachedRecordBytes;

        this.fullRead = fullRead;

        if (S3Helper.isS3Url(url)) { //check first to catch http[s]://BUCKET.s3-REGION.amazonaws.com/KEY
            if (s3 == null) {
                throw new IllegalArgumentException("S3 service required for URL " + url);
            }
            this.s3 = s3;
            AmazonS3URI s3Uri = new AmazonS3URI(url);
            s3Bucket = s3Uri.getBucket();
            s3Key = s3Uri.getKey();
            inputFile = null;
            inputURL = null;
        } else if (HTTPHelper.isHTTPUrl(url)) {
            this.s3 = null;
            s3Bucket = null;
            s3Key = null;
            inputFile = null;
            inputURL = url;
        } else {
            this.s3 = null;
            s3Bucket = null;
            s3Key = null;
            inputFile = new File(getLocalPath(url));
            inputURL = null;
        }
        
        int vicarLabelStartByte = 0;
        int vicarLabelLength = 0;
        
        String magic = readString(0, 8);
        switch (magic) {
            case "LBLSIZE ":
            case "LBLSIZE=": { //VICAR with no PDS/ODL wrapper
                if (debug) log.info(pfx + "VICAR without PDS/ODL wrapper");
                vicarLabelStartByte = 0;
                vicarLabelLength = parseVicarLabelLength(vicarLabelStartByte);
                imageStartByte = vicarLabelStartByte + vicarLabelLength;
                break;
            }
            case "ODL_VERS": case "PDS_VERS": { //PDS/ODL wrapper
                if (debug) log.info(pfx + "VICAR with PDS/ODL wrapper");
                int[] starts = parsePDSHeader();
                vicarLabelStartByte = starts[0];
                vicarLabelLength = parseVicarLabelLength(vicarLabelStartByte);
                imageStartByte = starts[1];
                break;
            }
            default: throw new IOException("unrecognized start \"" + magic + "\" of VICAR or PDS file");
        }

        if (debug) log.info(pfx + "VICAR label start " + vicarLabelStartByte + ", length " + vicarLabelLength +
                            ", image start " + imageStartByte);

        Map<String, String> vicarHeader = parseVICARHeader(vicarLabelStartByte, vicarLabelLength);
        
        if (!"BSQ".equals(vicarHeader.get("ORG"))) {
            throw new IllegalArgumentException("only band sequential VICAR data is supported");
        }

        if (parseInt(vicarHeader.get("NBB"), "NBB") != 0 || parseInt(vicarHeader.get("NLB"), "NLB") != 0) {
            throw new IllegalArgumentException("VICAR binary prefix or header not supported");
        }
        
        bands = parseInt(vicarHeader.get("NB"), "NB");
        lines = parseInt(vicarHeader.get("NL"), "NL");
        samples = parseInt(vicarHeader.get("NS"), "NS");

        if (bands <= 0 || lines <= 0 || samples <= 0) {
            throw new IOException("empty VICAR image: non-positive bands, lines, or samples");
        }
        
        String fmt = vicarHeader.get("FORMAT");
        if ("".equals(fmt)) {
            throw new IOException("VICAR FORMAT header not found");
        }
        switch (fmt) {
            case "BYTE":              dataType = DataType.U8;  bytesPerSample = 1; break;
            case "HALF": case "WORD": dataType = DataType.S16; bytesPerSample = 2; break;
            case "FULL": case "LONG": dataType = DataType.S32; bytesPerSample = 4; break;
            case "REAL":              dataType = DataType.F32; bytesPerSample = 4; break;
            case "DOUB":              dataType = DataType.F64; bytesPerSample = 8; break;
            default: throw new IllegalArgumentException("unspported VICAR format: " + fmt);
        }

        String cmp = vicarHeader.get("COMPRESS");
        if ("".equals(cmp)) {
            compressionType = CompressionType.NONE;
        } else {
            switch (cmp) {
                case "NONE": compressionType = CompressionType.NONE; break;
                case "BASIC": compressionType = CompressionType.BASIC; break;
                case "BASIC2": compressionType = CompressionType.BASIC2; break;
                default: throw new IOException("unspported VICAR compression: " + cmp);
            }
        }

        if (debug) log.info(pfx + "lines=" + lines + ", samples=" + samples + ", bands=" + bands + ", format=" + fmt +
                            ", compression=" + cmp);
        
        if (dataType == DataType.F32 || dataType == DataType.F64) {
            
            String rfmt = vicarHeader.get("REALFMT");
            if (!"".equals(rfmt)) {
                switch (rfmt) {
                    case "IEEE": byteOrder = ByteOrder.BIG_ENDIAN; break;
                    case "RIEEE": byteOrder = ByteOrder.LITTLE_ENDIAN; break;
                    default: throw new IllegalArgumentException("unsupported VICAR REALFMT: " + rfmt);
                }
                if (debug) log.info(pfx + rfmt + " REALFMT");
            } else {
                //default is VAX wich we don't support
                throw new IOException("VICAR REALFMT header not found");
            }
            
        } else if (dataType != DataType.U8) {
            
            String ifmt = vicarHeader.get("INTFMT");
            if (!"".equals(ifmt)) {
                switch (ifmt) {
                    case "HIGH": byteOrder = ByteOrder.BIG_ENDIAN; break;
                    case "LOW": byteOrder = ByteOrder.LITTLE_ENDIAN; break;
                    default: throw new IllegalArgumentException("unsupported VICAR INTFMT: " + ifmt);
                }
                if (debug) log.info(pfx + ifmt + " INTFMT");
            } else {
                byteOrder = ByteOrder.LITTLE_ENDIAN;
                if (debug) log.info(pfx + "missing INTFMT, defaulting to little endian");
            }
            
        } else {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        }
    }

    public SparseImage(String url, boolean debug, String pfx, int maxCachedSamples, int maxCachedRecordBytes,
                       boolean fullRead) throws IOException {
        this(url, debug, pfx, maxCachedSamples, maxCachedRecordBytes, fullRead, null);
    }

    private InputStream getInputStream() throws IOException {
        if (s3 != null) {
            return s3.getObject(s3Bucket, s3Key);
        } else if (inputURL != null) {
            return HTTPHelper.getStream(inputURL);
        } else {
            return new BufferedInputStream(new FileInputStream(inputFile));
        }
    }

    private byte[] getRange(long start, long end) throws IOException {
        long ne = end - start + 1;
        if (s3 != null) {
            byte[] ret = s3.getRange(s3Bucket, s3Key, start, end);
            if (ret.length != ne) {
                throw new IOException("read " + ret.length + " bytes, expected " + ne);
            }
            return ret;
        } else if (inputURL != null) {
            byte[] ret = HTTPHelper.getRange(inputURL, start, end);
            if (ret.length != ne) {
                throw new IOException("read " + ret.length + " bytes, expected " + ne);
            }
            return ret;
        } else {
            if (ne > Integer.MAX_VALUE) {
                throw new IOException("cannot read " + ne + " > " + Integer.MAX_VALUE + " bytes from local file");
            }
            var bufs = new byte[1][];
            bufs[0] = new byte[(int)ne];
            long nr = getRange(start, bufs, ne);
            if (nr != ne) {
                throw new IOException("read " + nr + " bytes, expected " + ne);
            }
            return bufs[0];
        }
    }

    private long getRange(long start, byte[][] bufs, long max) throws IOException {
        if (s3 != null) {
            return s3.getRange(s3Bucket, s3Key, start, bufs, max);
        } else if (inputURL != null) {
            long nr = HTTPHelper.getRange(inputURL, start, bufs, max);
            if (nr < 0) throw new IOException("HTTP(S) range request not supported for " + inputURL);
            return nr;
        } else {
            long len = 0;
            for (int i = 0; i < bufs.length && len < max; i++) {
                if (bufs[i] != null) {
                    len += bufs[i].length;
                }
            }
            if (len > max) {
                len = max;
            }
            try (var raf = new RandomAccessFile(inputFile, "r")) {
                raf.seek(start);
                for (int nb = 0, nr = 0, i = 0, off = 0; nb < len; nb += nr, off += nr) {
                    int left = bufs[i] != null ? bufs[i].length - off : 0;
                    if (left == 0) {
                        i++;
                        off = 0;
                        nr = 0;
                    } else {
                        long rb = len - nb;
                        if (rb > left) {
                            rb = left;
                        }
                        nr = raf.read(bufs[i], off, (int)rb);
                        if (nr < 0) {
                            return nb;
                        }
                    }
                }
                return len;
            }
        }
    }

    private String readString(int start, int length) throws IOException {
        return new String(getRange(start, start + length - 1), "UTF-8");
    }

    private static int parseInt(String str, String name) throws IOException {
        if (str == null) {
            throw new IOException("header not found: " + name);
        }
        try {
            str = str.trim();
            if (str.length() > 0) {
                return Integer.parseInt(str.split("\\s+")[0]); //parse first whitespace separated token
            } else {
                return 0;
            }
        } catch (NumberFormatException ex) {
            throw new IOException("error parsing header: " + name);
        }
    }
    
    private int[] parsePDSHeader() throws IOException {
        String recordType = null;
        int recordBytes = 0;
        int imageHeaderRecord = 0;
        int imageRecord = 0;
        InputStream stream = null;
        try {
            stream = getInputStream();
            int lineNumber = 1;
            String line = null;
            while ((line = readPDSLine(stream)) != null) {
                line = line.trim();
                if ("END".equals(line)) {
                    break;
                }
                String[] tok = line.split("=");
                if (tok.length == 2) {
                    String key = tok[0].trim();
                    String val = tok[1].trim();
                    switch (key) {
                        case "RECORD_TYPE": recordType = val; break;
                        case "RECORD_BYTES": recordBytes = parseInt(val, "RECORD_BYTES"); break;
                        case "^IMAGE_HEADER": imageHeaderRecord = parseInt(val, "^IMAGE_HEADER"); break;
                        case "^IMAGE": imageRecord = parseInt(val, "^IMAGE"); break;
                    }
                }
                if (recordType != null && recordBytes > 0 && imageHeaderRecord > 0 && imageRecord > 0) {
                    break;
                }
                if (++lineNumber >= 100) {
                    break;
                }
            }
        } finally {
            if (stream instanceof ResponseInputStream) {
                //avoid org.apache.http.ConnectionClosedException
                ((ResponseInputStream)stream).abort(); //https://stackoverflow.com/a/53706281
            } else if  (stream != null) {
                stream.close(); 
            }
        }
        if (recordType == null) {
            throw new IOException("missing PDS/ODL header RECORD_TYPE");
        }
        if (!"FIXED_LENGTH".equals(recordType)) {
            throw new IOException("unsupported PDS/ODL RECORD_TYPE " + recordType);
        }
        if (recordBytes <= 0) {
            throw new IOException("missing PDS/ODL header RECORD_BYTES");
        }
        if (imageHeaderRecord <= 0) {
            throw new IOException("missing PDS/ODL header ^IMAGE_HEADER");
        }
        if (imageRecord <= 0) {
            throw new IOException("missing PDS/ODL header ^IMAGE");
        }
        return new int[] { recordBytes * (imageHeaderRecord - 1), //Vicar label start byte
                           recordBytes * (imageRecord - 1) }; //image start byte
    }

    //PDS/ODL statements can span lines when the value is a quoted string, sequence or set
    //this impl doesn't handle those cases, only numeric values
    //this impl is still a little broken in that a valid PDS file could exist where
    //e.g. a string could contain RECORD_TYPE=FOO on its own line
    //but yeah, the legacy impl didn't handle that either...
    //(note that PDS comments can't span lines, and must appear at the end of a line)
    private static String readPDSLine(InputStream stream) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; true; i++) {
            int c = stream.read();
            if (c < 0) { //EOF
                break; 
            }
            if (c == '\r' || c == '\n' || c == 11 /* vertical tab */ || c == 12 /* form feed */) {
                //https://pds.jpl.nasa.gov/datastandards/pds3/standards/sr/Chapter12.pdf
                //pds statements *should* be delimited by \r\n
                //but *can* be delimited by any sequence of "format effectors"
                //which are newline, carriage return, vertical tab, or form feed
                //if we're reading such a character at the beginning of a line
                //it's still part of the ending of the previous line, or the line is empty
                if (sb.length() > 0) { //got first "format effector" char at end of line
                    break; 
                }
            } else if (sb.length() < 100) { //this impl only needs to handle short lines, discard suffix of long line
                sb.append((char)c);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
    
    private Map<String, String> parseVICARHeader(int start, int length) throws IOException {

        Map<String, String> ret = new HashMap<String, String>();

        ret.put("ORG", "");
        ret.put("NB", "");
        ret.put("NL", "");
        ret.put("NS", "");
        ret.put("FORMAT", "");
        ret.put("REALFMT", "");
        ret.put("INTFMT", "");
        ret.put("NLB", "");
        ret.put("NBB", "");
        ret.put("COMPRESS", "");

        //https://www-mipl.jpl.nasa.gov/external/VICAR_file_fmt.pdf
        //"[VICAR] Keywords are strings, up to 32 characters in length,
        //and consist of uppercase characters, underscores (_), and numbers (but should start with a letter)"
        //note in Java \w is [a-zA-Z_0-9]
        Matcher matcher = Pattern //NAME='VAL', NAME='FOO ''BAR'' BAZ', NAME=VAL
            .compile("\\s*(\\w+)\\s*=\\s*(?:(?:'((?:[^']*(?:'')?)*)')|(\\S+))")
            .matcher(readString(start, length));

        int numRemaining = ret.size();
        while (numRemaining > 0 && matcher.find()) {
            String key = matcher.group(1);
            if ("PROPERTY".equals(key) || "TASK".equals(key)) {
                break; //only read system labels, which always come before the first PROPERTY label
            }
            String val = matcher.group(2);
            val = val != null ? val.trim() : matcher.group(3);
            if (ret.containsKey(key) && val != null && val.length() > 0) {
                if ("".equals(ret.get(key))) {
                    numRemaining--;
                }
                ret.put(key, val);
            }
        }

        return ret;
    }

    //read the 100 chars after "LBLSIZE=" or "LBLSIZE "
    //these *shold* include the full integer string of the VICAR label size, possibly plus subsequent tokens 
    //note that it is also possible for there to be an unlimited amount of whitespace before and after the =
    //that seprates LBLSIZE from its value
    //so this could fail on a valid VICAR header in such a case
    private int parseVicarLabelLength(int labelStart) throws IOException {
        String str = readString(labelStart + 8, 100).trim();
        if (str.startsWith("=")) {
            str = str.substring(1);
        }
        return parseInt(str, "LBLSIZE"); //will use first whitespace separated token of str
    }
    
    public boolean isFloat() {
        return dataType == DataType.F32 || dataType == DataType.F64;
    }
    
    public double[] read(float line, float sample, InterpMode interp) throws IOException {
        return read(line, sample, interp, new double[bands]);
    }
    
    public double[] read(float line, float sample, InterpMode interp, double[] ret) throws IOException {
        int l = (int)line;
        int s = (int)sample;
        if (interp == InterpMode.none) {
            for (int b = 0; b < bands; b++) {
                ret[b] = read(l, s, b);
            }
        } else {
            int nl = l < (lines - 1) ? (l + 1) : l;
            int ns = s < (samples - 1) ? (s + 1) : s;
            double v = line - l;
            double h = sample - s;
            switch (interp) {
                case interp_dn: {
                    for (int b = 0; b < bands; b++) {
                        ret[b] = bilinearInterp(read(l, s, b), read(l, ns, b), read(nl, s, b), read(nl, ns, b), v, h);
                    }
                    break;
                }
                case interp_nonzero: {
                    for (int b = 0; b < bands; b++) {
                        ret[b] = bilinearInterpNonzero(readNonzero(l, s, b), readNonzero(l, ns, b),
                                                       readNonzero(nl, s, b), readNonzero(nl, ns, b),
                                                       v, h);
                    }
                    break;
                }
            default: throw new IllegalArgumentException("unknown interp mode " + interp);
            }
        }
        if (debug && compressionType != CompressionType.NONE) {
            StringBuilder sb = new StringBuilder();
            sb.append(pfx);
            sb.append(compressionType);
            sb.append(" decompressor packet tallies:");
            for (int t = 0; t < 8; t++) {
                sb.append(" ");
                sb.append(decompressorCoverage[t]); 
                sb.append(" type ");
                sb.append(t);
                if (t < 7) {
                    sb.append(",");
                }
            }
            log.info(sb.toString());
        }
        return ret;
    }

    private double readNonzero(int line, int sample, int band) throws IOException {
        
        double p0 = read(line, sample, band);
        if (p0 != 0) {
            return p0;
        }
        
        double p1 = read(line - 1, sample, band, true);
        double p2 = read(line + 1, sample, band, true);
        double p3 = read(line, sample - 1, band, true);
        double p4 = read(line, sample + 1, band, true);
        if (p1 != 0 && p2 != 0 &&  p3 != 0 && p4 != 0) {
            return 0.25 * (p1 + p2 + p3 + p4);
        }
        
        double p5 = read(line - 1, sample - 1, band, true);
        double p6 = read(line + 1, sample - 1, band, true);
        double p7 = read(line - 1, sample + 1, band, true);
        double p8 = read(line + 1, sample + 1, band, true);
        if (p5 != 0 && p6 != 0 && p7 != 0 && p8 != 0) {
            return 0.25 * (p5+p6+p7+p8);
        }
        
        if (p1 != 0 && p2 != 0) {
            return 0.5 * (p1 + p2);
        }
        if (p3 != 0 && p4 != 0) {
            return 0.5 * (p3 + p4);
        }
        if (p5 != 0 && p8 != 0) {
            return 0.5 * (p5 + p8);
        }
        if (p6 != 0 && p7 != 0) {
            return 0.5 * (p6 + p7);
        }
        
        return 0; //can't be filled
    }
    
    private static double bilinearInterp(double ul, double ur, double ll, double lr, double v, double h) {
        double u = (1.0 - h) * ul + h * ur;
        double l = (1.0 - h) * ll + h * lr;
        return (1.0 - v) * u + v * l;
    }
    
    private static double bilinearInterpNonzero(double ul, double ur, double ll, double lr, double v, double h) {
        return (ul == 0 || ur == 0 || ll == 0 || lr == 0) ? 0 : bilinearInterp(ul, ur, ll, lr, v, h);
    }

    private double toDouble(byte[] bytes) {
        switch (dataType) {
            case U8: return ((int)(bytes[0]))&0xff;
            case S16: return ByteBuffer.wrap(bytes).order(byteOrder).getShort();
            case S32: return ByteBuffer.wrap(bytes).order(byteOrder).getInt();
            case F32: return ByteBuffer.wrap(bytes).order(byteOrder).getFloat();  //big endian = exponent first
            case F64: return ByteBuffer.wrap(bytes).order(byteOrder).getDouble(); //
            default: throw new IllegalArgumentException("unknown data type " + dataType);
        }
    }
    
    private byte[] sampleBuf;
    private double readSample(byte[] record, int sample) {
        if (sampleBuf == null) sampleBuf = new byte[bytesPerSample];
        System.arraycopy(record, sample * bytesPerSample, sampleBuf, 0, bytesPerSample);
        return toDouble(sampleBuf);
    }
    
    private double read(int line, int sample, int band, boolean clamp) throws IOException {
        if (line < 0 || line >= lines || sample < 0 || sample >= samples || band < 0 || band >= bands) {
            if (clamp) {
                return 0;
            } else {
                throw new IllegalArgumentException("invalid access at line=" + line + ", sample=" + sample +
                                                   ", band=" + band + " in image with " + lines + " lines, " +
                                                   samples + " samples, " + bands + " bands");
            }
        }
        double value = getCachedSample(line, sample, band);
        if (!Double.isNaN(value)) {
            if (debug) log.info(pfx + "using cached sample for line=" + line + ", sample=" + sample + ", band=" + band);
            return value;
        }
        byte[] record = getCachedRecord(line, band);
        if (fullRead && compressionType == CompressionType.NONE && record == null) {
            readBlock(line);
            record = getCachedRecord(line, band);
        }
        if (record != null) {
            if (debug && !fullRead) log.info(pfx + "using cached data for line=" + line + ", band=" + band);
            return readSample(record, sample);
        }
        switch (compressionType) {
            case NONE: {
                if (debug) log.info(pfx + "fetching value for line=" + line + ", sample=" + sample + ", band=" + band);
                long start = imageStartByte + bytesPerSample * (samples * (band * lines + (long)line) + sample);
                value = toDouble(getRange(start, start + bytesPerSample - 1));
                break;
            }
            case BASIC: case BASIC2: {
                if (debug) log.info(pfx + "decompressing value for line=" + line + ", sample=" + sample +
                                    ", band=" + band);
                if (debug) log.info(pfx + "fetching compressed data for line=" + line + ", band=" + band);
                long start = getCompressedStartByte(line, band);
                boolean incrLine = line < lines - 1 || band == bands - 1;
                long nextStart = getCompressedStartByte(incrLine ? line + 1 : 0, incrLine ? band : band + 1);
                long end = nextStart - (compressionType == CompressionType.BASIC ? 5 : 1);
                record = decompress(getRange(start, end));
                cacheRecord(line, band, record);
                value = readSample(record, sample);
                break;
            }
            default: throw new IllegalStateException("unknown compression type " + compressionType);
        }
        cacheSample(line, sample, band, value);
        return value;
    }

    private double read(int line, int sample, int band) throws IOException {
        return read(line, sample, band, false);
    }
    
    private static class CacheKey {

        public final int line;
        public final int sample;
        public final int band;
        public final int hash;

        public CacheKey(int line, int sample, int band) {
            this.line = line;
            this.sample = sample;
            this.band = band;
            hash = Objects.hash(line, sample, band);
        }
        
        public CacheKey(int line, int band) {
            this(line, 0, band);
        }

        @Override public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (!(o instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey)o;
            return other.line == line && other.sample == sample  && other.band == band;
        }

        @Override public int hashCode() {
            return hash;
        }
    }

    private double getCachedSample(int line, int sample, int band) {
        if (fullRead || sampleCache == null) return Double.NaN;
        CacheKey k = new CacheKey(line, sample, band);
        return sampleCache.containsKey(k) ? sampleCache.get(k) : Double.NaN;
    }

    private void cacheSample(int line, int sample, int band, double value) {
        if (fullRead) return;
        if (sampleCache == null) {
            sampleCache = new LinkedHashMap<CacheKey, Double>(100, 0.75f, /* accessOrder */ true);
        }
        int numToRemove = (sampleCache.size() + 1) - maxCachedSamples;
        if (numToRemove > 0) {
            if (debug) log.info(pfx + "removing " + numToRemove + " LRU cached samples");
            for (var it = sampleCache.entrySet().iterator(); it.hasNext() && numToRemove > 0; ) {
                it.remove();
                numToRemove--;
            }
        }
        sampleCache.put(new CacheKey(line, sample, band), value);
    }

    private byte[] getCachedRecord(int line, int band) {
        CacheKey k = new CacheKey(line, band);
        return recordCache != null && recordCache.containsKey(k) ? recordCache.get(k) : null;
    }

    private void cacheRecord(int line, int band, byte[] record) {
        if (recordCache == null) {
            recordCache = new LinkedHashMap<CacheKey, byte[]>(100, 0.75f, /* accessOrder */true);
        } else {
            int max = maxCachedRecordBytes / record.length;
            int numToRemove = (recordCache.size() + 1) - max; //+1 because we're about to add a new one
            if (numToRemove > 0) {
                if (debug) log.info(pfx + "removing " + numToRemove + " LRU cached decompression records");
                for (var it = recordCache.entrySet().iterator(); it.hasNext() && numToRemove > 0; ) {
                    it.remove();
                    numToRemove--;
                }
            }
        }
        if (record.length < maxCachedRecordBytes) {
            recordCache.put(new CacheKey(line, band), record);
        }
    }

    private void readBlock(int startLine) throws IOException {
        if (!fullRead || compressionType != CompressionType.NONE) {
            return;
        }
        int bytesPerRecord = samples * bytesPerSample; 
        int numLines = maxCachedRecordBytes / (bands * bytesPerRecord);
        if (numLines > 0) {
            if (fullReadBufs == null) {
                fullReadBufs = new byte[bands][][];
                for (int band = 0; band < bands; band++) {
                    fullReadBufs[band] = new byte[numLines][];
                    for (int i = 0; i < numLines; i++) {
                        fullReadBufs[band][i] = new byte[bytesPerRecord];
                    }
                }
            }
            int endLine = startLine + numLines - 1;
            if (endLine >= lines) {
                endLine = lines - 1;
                numLines = endLine - startLine + 1;
            }
            if (debug) log.info(pfx + "fetching block of lines " + startLine + " to " + endLine + " of " + lines);
            if (recordCache != null) {
                recordCache.clear();
            }
            for (int band = 0; band < bands; band++) {
                long start = imageStartByte + bytesPerSample * samples * (band * lines + (long)startLine);
                long ne = (long)bytesPerRecord * numLines;
                long nb = getRange(start, fullReadBufs[band], ne);
                if (nb != ne) {
                    throw new IOException("read " + nb + " bytes, expected " + ne);
                }
                for (int i = 0; i < numLines; i++) {
                    cacheRecord(startLine + i, band, fullReadBufs[band][i]);
                }
            }
        }
    }

    private long getCompressedStartByte(int line, int band) throws IOException {
        if (compressedStartCache == null) {
            compressedStartCache = new HashMap<CacheKey, Long>();
        }
        CacheKey k = new CacheKey(line, band);
        if (compressedStartCache.containsKey(k)) {
            return compressedStartCache.get(k);
        }
        switch (compressionType) {
            case BASIC: {
                long pos = imageStartByte;
                int nr = 0;
                for (int b = 0; b <= band; b++) {
                    for (int l = 0; l <= (b < band ? lines : line); l++) {
                        CacheKey tk = new CacheKey(l, b);
                        if (!compressedStartCache.containsKey(tk)) {
                            compressedStartCache.put(tk, pos + 4);
                        }
                        CacheKey nk = l < (lines - 1) ? new CacheKey(l + 1, b)
                            : b < (bands - 1) ? new CacheKey(0, b + 1)
                            : new CacheKey(lines, bands - 1); //sentinel
                        if (compressedStartCache.containsKey(nk)) {
                            pos = compressedStartCache.get(nk) - 4;
                        } else {
                            ByteBuffer buf = ByteBuffer.wrap(getRange(pos, pos + 3));
                            pos += Integer.toUnsignedLong(buf.order(ByteOrder.LITTLE_ENDIAN).getInt());
                            compressedStartCache.put(nk, pos + 4);
                            nr++;
                        }
                    }
                }
                if (debug) log.info(pfx + "read " + nr + " BASIC compressed record lengths " +
                                    "(" + compressedStartCache.size() + " cached)");
                break;
            }
            case BASIC2: {
                long tableLength = 4L * bands * lines;
                if (debug) {
                    log.info(pfx + "reading table of " + (tableLength / 4) + " BASIC2 compressed record lengths");
                }
                byte[] table = getRange(imageStartByte, imageStartByte + tableLength - 1);
                IntBuffer intBuf = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
                long pos = imageStartByte + tableLength;
                for (int b = 0; b < bands; b++) {
                    for (int l = 0; l < lines; l++) {
                        compressedStartCache.put(new CacheKey(l, b), pos);
                        pos += Integer.toUnsignedLong(intBuf.get());
                    }
                }
                compressedStartCache.put(new CacheKey(lines, bands - 1), pos); //sentinel
                break;
            }
            default: throw new IllegalStateException("unsupported compression type " + compressionType);
        }
        return compressedStartCache.get(k);
    }

    private static class BitStream {

        private byte[] bits;
        private int pos;
        private int mask = 0x80;

        public BitStream(byte[] bits) {
            this.bits = bits;
        }

        public int read(int n) throws IOException {
            if (n < 0 || n > 8) {
                throw new IllegalArgumentException("invalid number of bits to read " + n);
            }
            if (pos + n > bits.length * 8L) {
                throw new IOException("attempt to read past end of bitstream");
            }
            int ret = 0;
            if (n == 8 && pos % 8 == 0) {
                ret = bits[pos / 8] & 0xff;
                pos += 8;
            }
            else {
                while (n > 0) {
                    ret = ret<<1;
                    ret |= ((bits[pos / 8] & mask) >>> (7 - (pos % 8))) & 0xff;
                    pos++;
                    n--;
                    mask = mask == 1 ? 0x80 : mask >>> 1;
                }
            }
            return ret;
        }
    }

    private byte[] decompress(byte[] bits) throws IOException {
        BitStream bitStream = new BitStream(bits);
        byte[] record = new byte[samples * bytesPerSample];
        if (debug) log.info(pfx + "decompressing " + (bits.length * 8L) + " " + compressionType +
                            " compressed bits to " + record.length + " bytes ");
        int runLen = 0;
        int runVal = 0;
        int lastVal = 0;
        for (int i = 0; i < bytesPerSample; i++) {
            for (int j = 0; j < samples; j++) {
                int val = 0;
                if (runLen > 0) {
                    val = runVal;
                    runLen--;
                } else {
                    int d = bitStream.read(3);
                    if (d < 7) {
                        val = lastVal + d - 3;
                        decompressorCoverage[0]++;
                    } else {
                        int t = bitStream.read(1);
                        if (t == 0) {
                            val = bitStream.read(8);
                            decompressorCoverage[1]++;
                        } else {
                            int type = 0;
                            int n = bitStream.read(4);
                            if (n < 15) {
                                runLen = n + 4;
                                type = 2;
                            } else {
                                int b = bitStream.read(8);
                                if (b < 255) {
                                    runLen = b + 15 + 4;
                                    type = 4;
                                } else {
                                    runLen = 0;
                                    runLen |= bitStream.read(8);
                                    runLen |= bitStream.read(8) << 8;
                                    runLen |= bitStream.read(8) << 16;
                                    runLen += 4;
                                    type = 6;
                                }
                            }
                            int e = bitStream.read(3);
                            if (e < 7) {
                                runVal = lastVal + e - 3;
                                decompressorCoverage[type]++;
                            } else {
                                runVal = bitStream.read(8);
                                decompressorCoverage[type+1]++;
                            }
                            val = runVal;
                            runLen--;
                        }
                    }
                }
                record[j * bytesPerSample + i] = (byte)val;
                lastVal = val;
            }
        }
        return record;
    }
}
