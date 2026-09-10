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
 *  An extension of AbstractUnpacker designed to take care of Reachability
 *  RDR DN value mapping. This class could be implemented as a simple unpacker
 *  the # bands determine what key set is used but no unpacking is done.
 *  Each band coresponds to reachability of a arm/inst configuration. This is the PHX/MER
 *  equivalent of ArmReachabilty @see jpl.mipl.mars.mis.unpackers.ArmReachabilityUnpacker
 *  it uses the number of samples in ivals to differentiate between PHX and MER (12-&gt;PHX 16-&gt;MER)
 *  @author Nolan Miller
 */
public class ReachabilityUnpacker extends AbstractUnpacker{
    protected static final String[] mer_config_names = {
        "MI_ELBOW_UP_WRIST_UP","MI_ELBOW_UP_WRIST_DOWN","MI_ELBOW_DOWN_WRIST_UP","MI_ELBOW_DOWN_WRIST_DOWN", 
        "RAT_ELBOW_UP_WRIST_UP","RAT_ELBOW_UP_WRIST_DOWN","RAT_ELBOW_DOWN_WRIST_UP","RAT_ELBOW_DOWN_WRIST_DOWN", 
        "MB_ELBOW_UP_WRIST_UP","MB_ELBOW_UP_WRIST_DOWN","MB_ELBOW_DOWN_WRIST_UP","MB_ELBOW_DOWN_WRIST_DOWN", 
        "APXS_ELBOW_UP_WRIST_UP","APXS_ELBOW_UP_WRIST_DOWN","APXS_ELBOW_DOWN_WRIST_UP","APXS_ELBOW_DOWN_WRIST_DOWN"};

    protected static final String[] phx_config_names = {
        "SCOOP_ELBOW_UP","SCOOP_ELBOW_DOWN","SCOOP_BTM_ELBOW_UP","SCOOP_BTM_ELBOW_DOWN","BLADE_ELBOW_UP","BLADE_ELBOW_DOWN",
        "ISAD1_ELBOW_UP","ISAD1_ELBOW_DOWN","ISAD2_ELBOW_UP","ISAD2_ELBOW_DOWN","TECP_ELBOW_UP","TECP_ELBOW_DOWN" };
    
    /**
     *  Extracts values for the configurations from sample list
     *  it uses ivals and formats the return along with headers for the user.
     */
    public Map<String,Object> parse(float[] fvals, int[] ivals){
        List<Integer> indexes= new ArrayList<Integer>();

        Map<String,Object> ret = new LinkedHashMap<String,Object>();

        //use the mer mapping
        if(ivals.length == mer_config_names.length){
            for(int i = 0;i<ivals.length&&i<mer_config_names.length;i++){
                ret.put(mer_config_names[i],ivals[i]);
            }            
        }
        //use the phx mapping
        else if(ivals.length == phx_config_names.length){
            for(int i = 0;i<ivals.length&&i<phx_config_names.length;i++){
                ret.put(phx_config_names[i],ivals[i]);
            }
        }
        //unknown
        else{
            //???
            ret.put("unknown number of bands.","error");
            for(int i = 0;i<ivals.length;i++){
                ret.put("band "+i,ivals[i]);
            }
        }
        return ret;
    }
}
