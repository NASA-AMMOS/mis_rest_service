package jpl.mipl.mars.mis.unpackers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *  An extension of AbstractUnpacker designed to take care of ArmReachability
 *  RDR DN value mapping. Unlike many other implementations of AbstractUnpacker,
 *  this class adds header data to the map it returns to aid the end user.
 *  The return values are extracted from the int array and are pulled
 *  as 2bit numbers packed into the first 16 bits of each sample.
 *  @author Nolan Miller
 */

public class ArmReachabilityUnpacker extends AbstractUnpacker{
    
    protected static final String[] msl_instr_names = {"DRILL", "DRT",  "MAHLI", "APXS", "SCOOP_TIP"};
    
    protected static final String[] m20_instr_names = {"DRILL", "GDRT", "WATSON", "SHERLOC", "PIXL", "FCS"};

    /**
     *  Extracts 8 values each ranging from 0 to 3 from each sample
     *  in ivals and formats the return along with headers for the user.
     */
    public Map<String,Object> parse(float[] fvals, int[] ivals){
        //the key values are "Band 1" "Band 2" etc
        List<Integer> indexes= new ArrayList<Integer>();

        
        String[] msn_instr_names = msl_instr_names;
        if (ivals.length == m20_instr_names.length)
            msn_instr_names = m20_instr_names;
        
        Map<String,Object> ret = new LinkedHashMap<String,Object>();
        ret.put("*Shoulder","  O  O  O  O  I  I  I  I");
        ret.put("*Elbow",   "  U  U  D  D  U  U  D  D");
        ret.put("*Wrist",   "  U  D  U  D  U  D  U  D");

        for(int i = 0; i<ivals.length && i<msn_instr_names.length; i++){
            String val =" ";
            for (int ms = 7; ms  >= 0; ms--)
            {
                //draw out the ms th 2 bit chunk
                int part  = (((0x03<<(ms*2))&ivals[i])>>(ms*2))&0x03;
                val+=" "+part+" ";
            }
            //Users apparently don't want to see the Hex values
            //val+=String.format("0x%04X", ivals[i]&0x0000FFFF);
            ret.put(msn_instr_names[i], val);
        }
        return ret;
    }
}
