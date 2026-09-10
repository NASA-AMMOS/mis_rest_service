package jpl.mipl.mars.mis.unpackers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extension of the StandardUnpacker which adds the ability
 * to map values to strings.  This lookup can be applied
 * to all bands or specific bands. Any unsuccessful lookup
 * results in the String format of the original float value 
 * being returned.
 * 
 * @author Nicholas Toole (Nicholas.T.Toole@jpl.nasa.gov)
 * @version $Id: $
 *
 */
public class StandardLookupUnpacker extends StandardUnpacker
{
    public static final int[] ALL_BANDS = new int[0];
  
    protected Map<int[], Map<Integer, String>> bandLookupMap;
  
    /**
     * Constructor
     */
    public StandardLookupUnpacker()
    {
        super();
        
        this.bandLookupMap = new HashMap<int[], Map<Integer, String>>();
    }
    
    
    /**
     * Convenience method that accepts a string representation
     * of the band array and returns a int[].  The special return
     * value of an empty array represents ALL bands.
     *
     * @param str String formatted int array (i.e. "{}" or
     * "{0,1}"
     * @return Associated int[] array
     */
    public static int[] parseLookupBands(String str)
    {
        int[] bandVals = null;
        
        if (str == null)
            return null;
        
        str = str.trim();
        if (str.isEmpty())
        {
            bandVals = ALL_BANDS;
        }
        else 
        {
            int[] intArray = ParseUtil.toIntArray(str);
            if (intArray == null)
                intArray = ALL_BANDS;
            
            bandVals = intArray;
        }
        
        return bandVals;
    }
    
    /**
     * Adds a int-String map associated with an array of bands
     * The empty array means all bands
     * @param bandArray int[] indicating which bands perform the
     * associated lookup
     * @param lookup Lookup map from int to String
     */
    public void addLookup(int[] bandArray, Map<Integer, String> lookup)
    {
        if (bandArray != null && bandArray.length == 0)
            bandArray = ALL_BANDS;
        
        bandLookupMap.put(bandArray ,lookup);
    }
    
    /**
     * Returns the lookup instance associated with requested
     * band.  If no match, then we will check if ALL_BANDS
     * has been set, returning that lookup if found, otherise
     * null is returned
     * @param band Band index
     * @return Lookup map if found, null otherwise
     */
    protected Map<Integer, String> getLookupForBand(int band)
    {
        Map<Integer, String> lookup = null;
        
        Iterator<int[]> keyIt = bandLookupMap.keySet().iterator();
        while (keyIt.hasNext() && lookup == null)
        {
            int[] curArray = keyIt.next();
            for (int i = 0; i < curArray.length; ++i)
            {
                if (curArray[i] == band)
                    lookup = bandLookupMap.get(curArray);
            }
        }
        
        if (lookup == null)
            lookup = bandLookupMap.get(ALL_BANDS);
        
        return lookup;
    }
    
    /**
     * Overrides the parse method from parent, still
     * performs a lookup for band NAME, but then also
     * performs lookup for values using the lookup
     * instances maintained by this object
     */
    public Map<String,Object> parse(float[] fvals, int[] ivals)
    {
        //create empty map
        Map<String,Object> ret = new LinkedHashMap<String,Object>();
        
        //wil need this to determine the band names
        final int arrLen = fvals.length;
        
        final String[] theBandNames = get(arrLen); //bandNames.get(arrLen); 
        
        for(int i = 0;i<fvals.length;i++)
        {
            String theBandName  = theBandNames[i];
            String theBandValue = valueToName(i, ivals[i], fvals[i]);
            
            ret.put(theBandName, theBandValue);
        }
        
        return ret;
    }
    
    /**
     * Performs lookup of int value based on the band index. 
     * @param bandIndex Band index
     * @param iValue int value, which will be used for lookup key
     * @param fValue Default value returns if lookup unsuccessful
     * @return String representation of the value, possible translated
     */
    protected String valueToName(int bandIndex, int iValue, float fValue)
    {
        String name = null;
        
        Map<Integer, String> valueNameLookup = getLookupForBand(bandIndex);
        
        //perform lookup        
        if (valueNameLookup != null)
        {
            name = valueNameLookup.get(iValue);
        }
               
        //check for no match
        if (name == null)
            name = fValue+"";
        
        return name;        
    }
}
