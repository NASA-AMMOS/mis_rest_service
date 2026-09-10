package jpl.mipl.mars.mis.unpackers;

import jpl.mipl.mars.mis.unpackers.AbstractUnpacker;

import java.util.Map;
import java.util.LinkedHashMap;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  An extension of AbstractUnpacker that provides a best guess mapping
 *  when no image type is available or the image type provided is unkown.
 *	@author Nolan Miller
 */
public class DefaultUnpacker extends AbstractUnpacker{
    /**
     *  The provides the value mapping and does a best guess based on
     *  the number of bands present. For 3 bands it assumes rgb, for 2
     *  it assumes line,sample, for 1 it assumes dn, and for all others
     *  it provides an enumerated band# key.
     */
    public Map<String,Object> parse(float[] fvals, int[] ivals){
        Map<String,Object> ret = new LinkedHashMap<String,Object>();
        
        if(fvals.length == 3){ 
            ret.put("_r",""+fvals[0]);
            ret.put("_g",""+fvals[1]);
            ret.put("_b",""+fvals[2]);
        }   
        if(fvals.length == 2){ 
            ret.put("_line",""+fvals[0]);
            ret.put("_sample",""+fvals[1]);
        }   
        if(fvals.length == 1){ 
            ret.put("_dn",""+fvals[0]);
        }   
        if(fvals.length > 3){ 
            int i = 0;
            for(float p : fvals){
                i++;
                ret.put("_band"+i,""+p);
            }   
        }   
        return ret;
    }
}
