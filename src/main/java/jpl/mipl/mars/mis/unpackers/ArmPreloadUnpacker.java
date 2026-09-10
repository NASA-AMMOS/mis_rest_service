package jpl.mipl.mars.mis.unpackers;

import jpl.mipl.mars.mis.unpackers.AbstractUnpacker;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;


import java.io.FileInputStream;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  An extension of AbstractUnpacker designed to take care of ArmPreload 
 *  RDR DN value mapping. Implemented for now as a speical case (rather 
 *  than a StandardUnpacker) although if this functionality is not used, this
 *  will soon become superflous in favor of including msl_config_names with
 *  the config xml.
 *	@author Nolan Miller
 */
public class ArmPreloadUnpacker extends AbstractUnpacker{
//this could probably be a simple unpacker
    private final static String[] msl_config_names = {"ARM_SO_EU_WU","ARM_SO_EU_WD","ARM_SO_ED_WU","ARM_SO_ED_WD",
                                                      "ARM_SI_EU_WU","ARM_SI_EU_WD","ARM_SI_ED_WU","ARM_SI_ED_WD"};

    /**
     *  Maps the correct Arm name to the supplied force value.
     *  utilizes the fvals parameter. Values units are Newtons (N)
     *  @see jpl.mipl.mars.mis.unpackers.AbstractUnpacker 
     */
    public Map<String,Object> parse(float[] fvals, int[] ivals){
        Map<String,Object> ret = new LinkedHashMap<String,Object>();
        for(int i = 0;i<fvals.length&&i<msl_config_names.length;i++){
            ret.put(msl_config_names[i],fvals[i]);
        }
        return ret;
    }
}
