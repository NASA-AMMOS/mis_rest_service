package jpl.mipl.mars.mis.unpackers;

import jpl.mipl.mars.mis.unpackers.AbstractUnpacker;

import java.util.Map;
import java.util.HashMap;
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
 *  This is the goto unpacker for any image type that only requires
 *  variation on key names by the number of bands (and no extra processing).
 *  It should have all the needed band length, name set pairs added where
 *  each band lenth is unique and equal to the length of the name list passed.
 *  Usually used in conjunction with an XML config file to load all the settings.
 *	@author Nolan Miller
 */
public class StandardUnpacker extends AbstractUnpacker{
    
    public    static final int    DEFAULT_BAND_LOOKUP = -1;
    protected static final String DEFAULT_LABEL_PREFIX = "band";
    
    protected Map<Integer,String[]> bandNames = null;
    
    //field holds the label name for undefined labels
    //the band number will be appended to it
    protected String labelPrefix = DEFAULT_LABEL_PREFIX;
    
    
    public StandardUnpacker(){
        bandNames = new HashMap<Integer,String[]>();
    }

    /**
     *  adds a band length mapping from the number of samples to a list of
     *  (ordered) names corresponding to sample keys. This should be called
     *  for unique integer values only and never for the same mapping twice for
     *  one instantiation.
     *  @param count the number of bands that should map to this key list
     *  @param names the array of string representing the ordered list of names
     */
    public void add(Integer count, String[] names){
        bandNames.put(count,names);
    }
    
    /**
     * returns an ordered list of sample keys based on the input 
     * band count.  If a match to the count is found, the associated
     * label list will be returned.  Otherwise, we will generate a new
     * label list. by combining labels provided by the default
     * entry.  If count is greater than the length of that default
     * list, then we will fill the remaining labels using the 
     * label prefix, if provided, or simply band.  
     * @param count band count
     * @return String of labels corresponding to count
     */
    public String[] get(Integer count)
    {
        String[] result = null;
        
        if (bandNames.containsKey(count))
        {
            result = bandNames.get(count);
        }
        else
        {
            //so we don't have a specific list of labels, so we
            //will use the default, possibly supplemented if it
            //is not long enough
            
            final String[] reference = bandNames.get(DEFAULT_BAND_LOOKUP);
            
            final int refLen = reference == null ? 0 : reference.length;
            
            result = new String[count];
            
            //fill in the initial bands from reference array
            for (int i = 0; i < refLen; ++i)
                result[i] = reference[i];
            
            //complete the rest using the prefix label
            for (int i = refLen; i < count; ++i)
            {                           
                result[i] = this.labelPrefix + i;
            }
            
            //now, lets store the result since it should be the same
            this.bandNames.put(count, result);
        }
        
        return result;
    }
    
    public void setLabelPrefix(String prefix)
    {
        if (prefix == null)
            prefix = DEFAULT_LABEL_PREFIX;
        
        this.labelPrefix = prefix;
    }    
    
    
    /**
     *  creates the map object from the key names specified to the fvals
     *  passed in where name[i] maps to fval[i]. Should only be called for 
     *  float[] fvals of a length that has been specified by the add method.
     *  @param fvals the float array of samples for the pixel queried
     *  @param ivals the int array of samples for the pixel queried (unused)
     *  @return a map from the proper name to the named value (float)
     */
    public Map<String,Object> parse(float[] fvals, int[] ivals)
    {
        
        Map<String,Object> ret = new LinkedHashMap<String,Object>();
        
        //query for the label array
        final int datasize = fvals == null ? 0 : fvals.length;
        String[] labels = this.get(datasize);
        if (labels != null)
        {            
            for(int i = 0;i<fvals.length;i++)
            {        
                String curLabel = labels[i];
                ret.put(curLabel, fvals[i]);
            }
        }
        return ret;
    }
}
