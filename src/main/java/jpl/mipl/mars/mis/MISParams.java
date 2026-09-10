package jpl.mipl.mars.mis;

import java.io.File;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.JsonString;
import javax.json.JsonNumber;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Marsette Vona
 */
public abstract class MISParams {

    public static final Logger log = LoggerFactory.getLogger(MISTask.class);

    public static final String[] PDS_EXTS = new String[] { "img", "vic" };

    public final String imgUrl;

    public String pfx;

    public static boolean hasExt(String url, String[] exts) {
        int lastDot = url.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < url.length() - 1) {
            String ext = url.substring(lastDot + 1).toLowerCase();
            for (int i = 0; i < exts.length; i++) {
                if (ext.equals(exts[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean checkUrl(String url) {
        if (url.contains("../")) { //avoid tricks like ../../../pwned
            return false;
        }
        if (MISTask.rdrUrlWhitelistPatterns != null && MISTask.rdrUrlWhitelistPatterns.length > 0) {
            boolean ok = false;
            for (int i = 0; i < MISTask.rdrUrlWhitelistPatterns.length; i++) {
                if (MISTask.rdrUrlWhitelistPatterns[i].matcher(url).matches()) {
                    ok = true;
                    break;
                }
            }
            if (!ok) return false;
        }
        if (MISTask.ignoreSubdirs != null) {
            for (int i = 0; i < MISTask.ignoreSubdirs.length; i++) {
                if (url.contains("/" + MISTask.ignoreSubdirs[i] + "/")) {
                    return false;
                }
            }
        }
        return hasExt(url, PDS_EXTS);
    }

    public static float[] parseFloatArray(String str, String name) {
        try {
            if (str == null || str.isEmpty()) {
                return null;
            } else if (!str.contains(",")) {
                return new float[] { Float.parseFloat(str) };
            } else {
                String[] strs = str.split(",");
                float[] ret = new float[strs.length];
                for (int i = 0; i < strs.length; i++) {
                    ret[i] = Float.parseFloat(strs[i]);
                }
                return ret;
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("error parsing " + name);
        }
    }

    public static double[] parseDoubleArray(String str, String name) {
        try {
            if (str == null || str.isEmpty()) {
                return null;
            } else if (!str.contains(",")) {
                return new double[] { Double.parseDouble(str) };
            } else {
                String[] strs = str.split(",");
                double[] ret = new double[strs.length];
                for (int i = 0; i < strs.length; i++) {
                    ret[i] = Double.parseDouble(strs[i]);
                }
                return ret;
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("error parsing " + name);
        }
    }

    public static double parseDouble(String str, String name, double def) {
        try {
            return str != null && !str.isEmpty() ? Double.parseDouble(str) : def;
        } catch (Exception ex) {
            throw new IllegalArgumentException("error parsing " + name);
        }
    }

    public static double parseDouble(String str, double def) {
        return parseDouble(str, "double", def);
    }

    public static int parseInt(String str, String name, int def) {
        try {
            return str != null && !str.isEmpty() ? Integer.parseInt(str) : def;
        } catch (Exception ex) {
            throw new IllegalArgumentException("error parsing " + name);
        }
    }

    public static int parseInt(String str, int def) {
        return parseInt(str, "integer", def);
    }

    public static boolean parseBool(String str, boolean def) {
        return (str != null && !str.isEmpty()) ? str.toLowerCase().equals("true") : def;
    }

    public static boolean parseBool(String str) {
        return parseBool(str, false);
    }

    public static <T extends Enum<T>> T parseEnum(String str, String name, Class<T> type, T def) {
        try {
            return str != null && !str.isEmpty() ? Enum.valueOf(type, str) : def;
        } catch (Exception ex) {
            throw new IllegalArgumentException("error parsing " + name);
        }
    }

    public static <T extends Enum<T>> T parseEnum(String str, Class<T> type, T def) {
        return parseEnum(str, "enum", type, def);
    }

    public static float[] parseJsonFloatArray(JsonObject json, String singularName, String pluralName) {
        if (json.containsKey(singularName) && json.containsKey(pluralName)) {
            throw new IllegalArgumentException("JSON request cannot contain both " +
                                               singularName + " and " + pluralName);
        } else if (json.containsKey(singularName)) {
            return new float[] { parseJsonFloat(json, singularName) };
        } else if (json.containsKey(pluralName)) {
            var value = json.get(pluralName);
            if (value instanceof JsonArray) {
                JsonArray arr = (JsonArray)value;
                float[] ret = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    try { 
                        ret[i] = (float)(arr.getJsonNumber(i).doubleValue());
                    } catch (ClassCastException ex) {
                        throw new IllegalArgumentException("JSON value for " + pluralName +
                                                           "[" + i + "] is not a number");
                    }
                }
                return ret;
            } else if (value instanceof JsonString) {
                return parseFloatArray(((JsonString)value).getString(), pluralName);
            } else {
                throw new IllegalArgumentException("JSON value for " + pluralName + " is a " + value.getValueType() +
                                                   ", not an array of numbers or a comma delimited string");
            }
        } else {
            return null;
        }
    }

    public static double[] parseJsonDoubleArray(JsonValue json, String name) {
        if (json instanceof JsonObject) {
            var obj = (JsonObject)json;
            if (obj.containsKey(name)) {
                json = obj.get(name);
            } else {
                return null;
            }
        }
        if (json instanceof JsonArray) {
            JsonArray arr = (JsonArray)json;
            double[] ret = new double[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                try {
                    ret[i] = arr.getJsonNumber(i).doubleValue();
                } catch (ClassCastException ex) {
                    throw new IllegalArgumentException("JSON value for " + name + "[" + i + "] is not a number");
                }
            }
            return ret;
        } else if (json instanceof JsonString) {
            return parseDoubleArray(((JsonString)json).getString(), name);
        } else {
            throw new IllegalArgumentException("JSON " + name + " is a " + json.getValueType() +
                                               ", not an array of numbers or a comma delimited string");
        }
    }

    public static String parseJsonString(JsonObject json, String name, String def) {
        if (!json.containsKey(name)) {
            if (def != null) {
                return def;
            } else {
                throw new IllegalArgumentException("JSON missing " + name);
            }
        }
        var value = json.get(name);
        if (value instanceof JsonString) {
            return ((JsonString)value).getString();
        } else {
            throw new IllegalArgumentException("JSON value for " + name + " is a " + value.getValueType() +
                                               ", not string");
        }
    }

    public static int parseJsonInt(JsonObject json, String name, int def) {
        if (!json.containsKey(name)) {
            return def;
        }
        var value = json.get(name);
        if (value instanceof JsonNumber) {
            return ((JsonNumber)value).intValue();
        } else {
            throw new IllegalArgumentException("JSON value for " + name + " is a " + value.getValueType() +
                                               ", not number");
        }
    }

    public static float parseJsonFloat(JsonObject json, String name) {
        if (!json.containsKey(name)) {
            throw new IllegalArgumentException("JSON missing " + name);
        }
        var value = json.get(name);
        if (value instanceof JsonNumber) {
            return (float)(((JsonNumber)value).doubleValue());
        } else {
            throw new IllegalArgumentException("JSON value for " + name + " is a " + value.getValueType() +
                                               ", not number");
        }
    }

    public static double parseJsonDouble(JsonObject json, String name) {
        if (!json.containsKey(name)) {
            throw new IllegalArgumentException("JSON missing " + name);
        }
        var value = json.get(name);
        if (value instanceof JsonNumber) {
            return ((JsonNumber)value).doubleValue();
        } else {
            throw new IllegalArgumentException("JSON value for " + name + " is a " + value.getValueType() +
                                               ", not number");
        }
    }

    public static boolean parseJsonBool(JsonObject json, String name, boolean def) {
        if (!json.containsKey(name)) {
            return def;
        }
        var value = json.get(name);
        if (value == JsonValue.FALSE) {
            return false;
        } else if (value == JsonValue.TRUE) {
            return true;
        } else {
            throw new IllegalArgumentException("JSON value for " + name + " is a " + value.getValueType() +
                                               ", not true or false");
        }
    }

    public static MISParams parseRequest(HttpServletRequest request, String mission) throws IOException {
        if ("GET".equals(request.getMethod())) {
            if (request.getParameter("plane") != null || request.getParameter("sphere") != null) {
                return VolumeMISParams.parseGET(request);
            } else {
                return ExplicitMISParams.parseGET(request, mission);
            }
        } else if ("POST".equals(request.getMethod()) && "application/json".equals(request.getContentType())) {
            try (var reader = request.getReader()) {
                return parseJson(Json.createReader(reader).readObject(), mission);
            }
        } else {
            throw new IllegalArgumentException("unrecognized HTTP request method " + request.getMethod() +
                                               " and/or content type " + request.getContentType() +
                                               ", must be either GET or POST application/json");
        }
    }

    private static MISParams parseJson(JsonObject json, String mission) {
        if (json.containsKey("planes") || json.containsKey("spheres")) {
            return VolumeMISParams.parseJson(json);
        } else {
            return ExplicitMISParams.parseJson(json, mission);
        }
    }

    public abstract String getLogMessage();

    protected MISParams(String imgUrl) {
            
        if (imgUrl == null || imgUrl.isEmpty()) {
            throw new IllegalArgumentException("no image URL");
        }
            
        imgUrl = imgUrl.replace(File.separator, "/"); //always normalize slashes

        if (!checkUrl(imgUrl)) {
            throw new IllegalArgumentException("unsupported input file " + imgUrl);
        }
            
        pfx = MISTask.getVersion() + " [" + imgUrl + "] ";

        this.imgUrl = imgUrl;
    }
}
