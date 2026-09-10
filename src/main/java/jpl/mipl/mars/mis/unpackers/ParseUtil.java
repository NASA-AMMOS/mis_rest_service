package jpl.mipl.mars.mis.unpackers;

/**
 * Parse utility contains convenient parsing methods.
 *
 * @author Nicholas Toole (Nicholas.T.Toole@jpl.nasa.gov)
 * @version $Id: $
 *
 */

public class ParseUtil
{

    //---------------------------------------------------------------------
    
    public static int[] toIntArray(String input)
    {
        String[] strArray = toStringArray(input);
        if (strArray == null)
            return null;
        
        int[] intArray = toIntArray(strArray);
        if (intArray == null)
            return null;
        
        return intArray;
    }
    
    
    //---------------------------------------------------------------------
    public static int[] toIntArray(String[] strArray)
    {
        int result[] = new int[strArray.length];
        try {
            for (int i = 0; i < strArray.length; ++i)
            {
                result[i] = Integer.parseInt(strArray[i]);
            }
        } catch (Exception ex) {
            result = null;
        }
        return result;
    }
    
    //---------------------------------------------------------------------
    
    public static String[] toStringArray(String input)
    {
        String[] result = null;
        
        if (input == null)
            return null;
        
        try {
            String current = input;
            
            int begIndex, endIndex;
            begIndex = current.indexOf("[");
            endIndex = current.indexOf("]");
            if (begIndex == -1 && endIndex == -1)
            {
                begIndex = current.indexOf("{");
                endIndex = current.indexOf("}");
            }
            
            if (begIndex == -1 || endIndex == -1)
                return null;
            if (begIndex > endIndex)
                return null;
            
            current = current.substring(begIndex+1, endIndex);
            current = current.trim();
            if (current.isEmpty())
                result = new String[0];
            else            
                result = current.split("\\s*,\\s*");
            
        } catch (Exception ex) {
            result = null;
        }
        
        
        return result;
    }
    
    //---------------------------------------------------------------------
    
}
