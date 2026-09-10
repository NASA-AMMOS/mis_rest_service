package jpl.mipl.mars.mis.unpackers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extension of the StandardLookupUnpacker which adds a
 * translation to arm-config set for the second band.
 * For all other bands, the parent impl is used.
 * 
 * @author Nicholas Toole (Nicholas.T.Toole@jpl.nasa.gov)
 *
 */

public class ArmConfigLookupUnpacker extends StandardLookupUnpacker
{

    public static String[]  arm_config_names = new String[] {
            "SIEDWD", //"SHOUDLER_IN_ELBOW_DOWN_WRIST_DOWN"  (least significant bit)
            "SIEDWU",  //"SHOUDLER_IN_ELBOW_DOWN_WRIST_UP",
            "SIEUWD",  //"SHOUDLER_IN_ELBOW_UP_WRIST_DOWN",
            "SIEUWU",  //"SHOUDLER_IN_ELBOW_UP_WRIST_UP",
            "SOEDWD",  //"SHOUDLER_OUT_ELBOW_DOWN_WRIST_DOWN",
            "SOEDWU",  //"SHOUDLER_OUT_ELBOW_DOWN_WRIST_UP",
            "SOEUWD",  //"SHOUDLER_OUT_ELBOW_UP_WRIST_DOWN",
            "SOEUWU"};  //"SHOUDLER_OUT_ELBOW_UP_WRIST_UP"}; (most significant bit)
    
    
    protected String demultArmConfigs(float fValue)
    {
        int value = (int) fValue;
        
        List<String> list = new ArrayList<String>();
        
        int bitCount = arm_config_names.length;
        for (int i = bitCount-1; i >= 0; i--) 
        {
            boolean enabled = (value & (1 << i)) != 0;
            if (enabled)
                list.add(arm_config_names[i]);
        }
        String asList = Arrays.toString(list.toArray());
        String rVal = asList + " ("+value+")";
        return rVal;
                
    }
    
    /**
     * Performs arm-config translation for second band, otherwise
     * relies on parent impl. 
     * @param bandIndex Band index
     * @param iValue int value, which will be used for lookup key
     * @param fValue Default value returns if lookup unsuccessful
     * @return String representation of the value, possible translated
     */
    protected String valueToName(int bandIndex, int iValue, float fValue)
    {
        String name = null;
        
        //insert our behavior for config 
        if (bandIndex == 1)
        {
            name = this.demultArmConfigs(fValue);
        }
        else
        {
            name = super.valueToName(bandIndex, iValue, fValue);
        }
       
        //check for no match
        if (name == null)
            name = fValue+"";
                
        return name;        
    }
    
}
