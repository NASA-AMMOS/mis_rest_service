package jpl.mipl.mars.mis;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

import jpl.mipl.mars.viewer.api.Constants;
import jpl.mipl.mars.viewer.image.config.ImageConfiguration;
import jpl.mipl.mars.viewer.image.config.ImageConfigurationSingleton;
import jpl.mipl.mars.viewer.finder.AbstractMarsImageFileFinder;
import jpl.mipl.mars.viewer.finder.mission.m20combined.M20FlatUberImageFileFinder;
import jpl.mipl.mars.viewer.finder.mission.mslcombined.MslUberOdsImageFileFinder;
import jpl.mipl.mars.viewer.finder.mission.cadre.CadreOdsImageFileFinder;

import javax.json.JsonObject;

/**
 *
 * @author Marsette Vona
 */
public class ExplicitMISParams extends MISParams {

    public static final String[] SUPPORTED_MISSIONS = { "MSL", "M20", "CADRE" };
    public static final String DEFAULT_MISSION = "M20";

    public final float[] lines; //always same length as samples
    public final float[] samples; //always same length as lines 

    public final int origin; //always 0 or 1

    public final boolean batch; //force return of JSON array even for single pixel query

    public final boolean label;

    //NOTE: this is the image type corresponding to the product type in the imgUrl product ID
    //the frontend may supply it as a URL parameter, which it may get from the image_type metadata in OCS
    //and that is in turn populated in a mission dependent way by parsing the imgUrl when it was ingested
    //so this is computable from imgUrl if it was not given as a URL param, if you also know what mission to use
    public final String rdrType;

    public final SparseImage.InterpMode interp;

    public static String checkMission(String mission) {
        if (mission == null || mission.trim().length() == 0) {
            mission = DEFAULT_MISSION;
        } else {
            mission = mission.trim();
        }
        mission = mission.toUpperCase();
        if ("M2020".equals(mission)) {
            mission = "M20";
        }
        final String fm = mission;
        if (!Arrays.stream(SUPPORTED_MISSIONS).anyMatch(m -> m.equals(fm))) {
            log.warn("unknown mission " + mission);
        }
        return mission;
    }

    private static AbstractMarsImageFileFinder getFileFinder(String mission) {
        try {
            switch (checkMission(mission)) {
            case "MSL": return new MslUberOdsImageFileFinder("https://");
            case "M20": return new M20FlatUberImageFileFinder("https://");
            case "CADRE": return new CadreOdsImageFileFinder(SparseImage.fsInputDir);
            default: throw new IllegalStateException("unknown mission " + mission);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("error instantiating file finder: " + ex.getMessage());
        }
    }

    private static ImageConfiguration getImageConfig() {
        try {
            return ImageConfigurationSingleton.getConfiguration();
        } catch (Exception ex) {
            throw new IllegalStateException("error instantiating image configuration: " + ex.getMessage());
        }
    }

    private static String getImageTypeId(String filename, String mission) {
        //allow filename to be a filesystem path or a URL
        int sep = Math.max(filename.lastIndexOf(File.separator), filename.lastIndexOf('/'));
        if (sep >= 0) {
            filename = filename.substring(sep + 1);
        }
        String ucf = filename.toUpperCase();
        if (ucf.startsWith("WARPED-")) {
            ucf = ucf.substring(7);
            filename = filename.substring(7);
        }
        if (ucf.startsWith("ICM-")) {
            return "disparity";
        }
        var ff = getFileFinder(mission);
        var ic = getImageConfig();
        String productType = ff.extractImageType(filename, Constants.PRODUCT_TYPE_RDR);
        try {
            String id = ic.getImageTypeId(ff.getProductNamespace(), productType);
            if (id != null) {
                return id;
            } else {
                throw new Exception("no RDR type for product type " + productType);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("error looking up image type: " + ex.getMessage());
        }
    }

    public static ExplicitMISParams parseGET(HttpServletRequest request, String mission) throws IOException {
        return new ExplicitMISParams(request.getParameter("url"), //already URL decoded
                                     parseFloatArray(request.getParameter("line"), "line"),
                                     parseFloatArray(request.getParameter("sample"), "sample"),
                                     parseInt(request.getParameter("origin"), "origin", 1),
                                     parseBool(request.getParameter("batch")),
                                     parseBool(request.getParameter("label")),
                                     request.getParameter("rdr"),
                                     parseEnum(request.getParameter("interp"), "interp",
                                               SparseImage.InterpMode.class, SparseImage.InterpMode.none),
                                     mission);
    }

    public static ExplicitMISParams parseJson(JsonObject json, String mission) {
        return new ExplicitMISParams(parseJsonString(json, "rdr_url", null),
                                     parseJsonFloatArray(json, "line", "lines"),
                                     parseJsonFloatArray(json, "sample", "samples"),
                                     parseJsonInt(json, "origin", 1),
                                     parseJsonBool(json, "batch", false),
                                     parseJsonBool(json, "label", false),
                                     parseJsonString(json, "rdr_type", "auto"),
                                     parseEnum(parseJsonString(json, "interp", "none"), "interp",
                                               SparseImage.InterpMode.class, SparseImage.InterpMode.none),
                                     mission);
    }

    public String getLogMessage() {
        return lines.length + " samples";
    }

    public ExplicitMISParams(String imgUrl, float[] lines, float samples[], int origin, boolean batch, boolean label,
                             String rdrType, SparseImage.InterpMode interp, String mission) {
        super(imgUrl);
            
        pfx += "(explicit) ";

        if (rdrType == null || rdrType.toLowerCase().equals("auto")) {
            try {
                rdrType = getImageTypeId(imgUrl, mission);
            } catch (Exception ex) {
                log.warn(pfx + "could not autodetect product type, proceeding as EDR");
                rdrType = "edr";
            }
        }
            
        this.rdrType = rdrType;

        if (lines == null || samples == null) {
            throw new IllegalArgumentException("missing lines or samples");
        }

        if (lines.length != samples.length) {
            throw new IllegalArgumentException("different numbers of lines and samples");
        }

        if (lines.length < 1) {
            throw new IllegalArgumentException("zero lines or samples");
        }

        if (origin != 0 && origin != 1) {
            throw new IllegalArgumentException("origin must be 0 or 1");
        }

        for (int i = 0; i < lines.length; i++) {
            if (Float.isNaN(lines[i]) || lines[i] < origin) {
                throw new IllegalArgumentException("line " + i + " invalid: " + lines[i]);
            }
            if (Float.isNaN(samples[i]) || samples[i] < origin) {
                throw new IllegalArgumentException("sample " + i + " invalid: " + samples[i]);
            }
        }

        this.lines = lines;
        this.samples = samples;
        this.origin = origin;
        this.batch = batch;
        this.label = label;
        this.interp = interp;
    }
}
