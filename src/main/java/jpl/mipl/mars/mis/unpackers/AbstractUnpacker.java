package jpl.mipl.mars.mis.unpackers;

import java.util.Map;
import java.util.HashMap;

import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Abstract super class extended by all Unpackers that will be used
 *  to extract band data from an added image.
 *	@author Nolan Miller
 */
public abstract class AbstractUnpacker{
    /**
     *  Uses the implementation specific setup to extract the band data
     *  from the float[] and int[] samples passed in. For some implementations
     *  this method may be inflating (|Map| &gt; |fvals|). It is up to the implementation
     *  what the return type(Object) is although it is usually a String, Integer or a Float
     *  some implemenations use float[] some use int[] so ensure |fvals| = |ivals|
     *  @param fvals a float[] taken from the raster as a float sample
     *  @param ivals an int[] taken from the raster as an int sample
     *  @return Map&lt;String,Object&gt; a map from band key to band value. 
     */
    public abstract Map<String,Object> parse(float[] fvals, int[] ivals);    
}
