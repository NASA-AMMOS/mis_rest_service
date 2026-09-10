package jpl.mipl.mars.mis;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.JsonString;
import javax.json.JsonArray;
import javax.json.JsonObject;

/**
 *
 * @author Marsette Vona
 */
public class VolumeMISParams extends MISParams {

    public final double[][] planes;
    public final double[][] spheres;
    public final double[] surface;
    public final double[] sphereRadiusSquared;

    public static double[][] parseQuads(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        int n = values != null ? values.length : 0;
        double[][] ret = new double[n][];
        for (int i = 0; i < n; i++) {
            String what = name + "[" + i + "]";
            ret[i] = parseDoubleArray(values[i], what);
            if (ret[i] == null || ret[i].length != 4) {
                throw new IllegalArgumentException(what + " must be 4 comma separated numbers");
            }
        }
        return ret;
    }

    public static double[][] splitQuads(double[] array, String what) {
        if (array.length % 4 != 0) {
            throw new IllegalArgumentException(what + " must be a multiple of 4 comma separated numbers");
        }
        int n = array.length / 4;
        double[][] ret = new double[n][];
        for (int i = 0; i < n; i++) {
            ret[i] = new double[4];
            System.arraycopy(array, 4 * i, ret[i], 0, 4);
        }
        return ret;
    }

    public static double[][] parseJsonQuadArray(JsonObject json, String name) {
        if (json.containsKey(name)) {
            var value = json.get(name);
            if (value instanceof JsonArray) {
                JsonArray arr = (JsonArray)value;
                double[][] ret = new double[arr.size()][];
                for (int i = 0; i < arr.size(); i++) {
                    String what = name + "[" + i + "]";
                    try {
                        ret[i] = parseJsonDoubleArray(arr.getJsonArray(i), what);
                    } catch (ClassCastException ex) {
                        throw new IllegalArgumentException("JSON value for " + what + " is not a number");
                    }
                    if (ret[i] == null || ret[i].length != 4) {
                        throw new IllegalArgumentException(what + " must be a multiple of 4 comma separated numbers");
                    }
                }
                return ret;
            } else {
                throw new IllegalArgumentException("JSON value for " + name + " is a " + value.getValueType() +
                                                   ", not an array");
            }
        } else {
            return null;
        }
    }

    public static VolumeMISParams parseGET(HttpServletRequest request) throws IOException {
        return new VolumeMISParams(request.getParameter("url"),
                                   parseQuads(request, "plane"),
                                   parseQuads(request, "sphere"),
                                   parseDoubleArray(request.getParameter("surface"), "surface"));
    }

    public static VolumeMISParams parseJson(JsonObject json) {
        return new VolumeMISParams(parseJsonString(json, "rdr_url", null),
                                   parseJsonQuadArray(json, "planes"),
                                   parseJsonQuadArray(json, "spheres"),
                                   parseJsonDoubleArray(json, "surface"));
    }

    public String getLogMessage() {
        return surface == null ? "volume query" : "surface stats";
    }

    public VolumeMISParams(String imgUrl, double[][] planes, double[][] spheres, double[] surface) {

        super(imgUrl);
            
        pfx += surface == null ? "(volume) " : "(surface stats) ";

        if ((planes == null || planes.length == 0) && (spheres == null || spheres.length == 0)) {
            throw new IllegalArgumentException("at least one plane or sphere must be specified");
        }

        if (surface != null && surface.length != 4) {
            throw new IllegalArgumentException("surface must be 4 comma separated numbers");
        }

        for (int i = 0; planes != null && i < planes.length; i++) {
            for (int j = 0; j < planes[i].length; j++) {
                if (Double.isNaN(planes[i][j])) {
                    throw new IllegalArgumentException("planes[" + i + "][" + j + "] invalid: " + planes[i][j]);
                }
            }
        }

        for (int i = 0; spheres != null && i < spheres.length; i++) {
            for (int j = 0; j < spheres[i].length; j++) {
                if (Double.isNaN(spheres[i][j]) || (j == 3 && spheres[i][j] < 0)) {
                    throw new IllegalArgumentException("spheres[" + i + "][" + j + "] invalid: " + spheres[i][j]);
                }
            }
        }

        for (int i = 0; surface != null && i < surface.length; i++) {
            if (Double.isNaN(surface[i])) {
                throw new IllegalArgumentException("surface[" + i + "] invalid: " + surface[i]);
            }
        }

        if (spheres != null) {
            sphereRadiusSquared = new double[spheres.length];
            for (int i = 0; i < spheres.length; i++) {
                double r = spheres[i][3];
                sphereRadiusSquared[i] = r * r;
            }
        } else {
            sphereRadiusSquared = null;
        }

        this.planes = planes;
        this.spheres = spheres;
        this.surface = surface;
    }
}

