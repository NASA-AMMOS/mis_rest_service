package jpl.mipl.mars.mis.unpackers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  This is a static method "Toolbox" class that aims to allow unpacking
 *  of sample data into meaningful values that are linked to the proper names.
 *  TypeBands.xml is used to generate a mapping from imagetypes to unpackinging
 *  methods and this is done either explicitly or at first call to unpack. Thus
 *  changes made to TypeBands.xml will not propagate unless forceLoad is called
 *  or the environment is restarted.
 *	@author Nolan Miller
 */
public class Unpacker{
    
    private static final String CONFIG_PROP_NAME = "mis.unpacker.config.file";
    
    private static Map<String,AbstractUnpacker> store = null;
    private static DefaultUnpacker fallback = null;
    /**
     *  This is the primary mehtod called by the user. It is the only one really
     *  needed and will return a mapping from the proper sample name to the sample
     *  values specified either by the float array or the int array depending upon
     *  the image type. If no valid unpacker is found, a best guess default unpacker is used.
     *  @param imageType the PHX/MER/MSL imagetype id string that specifies the RDR and allows for proper unpacking
     *  @param fpixel the array of float values associated with the querried pixel
     *  @param ipixel the array of int values associated with the querried pixel
     *  @return a map of strings to objects where the objects are either Strings (rare), 
     *  Floats (most common) or Integers (uncommon).
     */
    public static Map<String,Object> unpack(String imageType, float[] fpixel, int[] ipixel){
        loadIfNeeded();
        if(store.containsKey(imageType))
            return store.get(imageType).parse(fpixel,ipixel);
        return fallback.parse(fpixel,ipixel);
    }
    /**
     *  Forces a (re)loading of the values specified in the config file
     */
    public static void forceLoad(){
        readInFile();
    }
    /**
     *  Loads the values from the config file iff they haven't already been loaded
     *  (even if changes have since been made to the config file).
     */
    public static void loadIfNeeded(){
        if(store ==null){
            readInFile();
        }
    }
    /**
     *  Checks if the given type values is a known type and if it will be properly
     *  unpacked by the unpack method. IE if a non-default unpacker will be used
     *  to unpack a type of the given variety.
     *  @param type the PHX/MER/MSL imagetype id string that specifies an RDR type
     *  @return boolean value representing if type is known (true) or unknown (false)
     */
    public static boolean isValidType(String type){
        if(store==null)
            readInFile();
        System.out.println("is the type:"+type+" valid?:"+(store.containsKey(type)?"yes":"no"));
        return store.containsKey(type);
    }

    protected static InputStream readInOverride()
    {
        InputStream is = null;
        
        if (System.getProperty(CONFIG_PROP_NAME) != null)
        {
            String configPath = System.getProperty(CONFIG_PROP_NAME);
            File configFile = new File(configPath);
            if (configFile.isFile())
            {
                try {
                    is = new FileInputStream(configFile);
                } catch (IOException ioEx) {
                    System.err.println("Error loading config file: "+configFile.getAbsolutePath());
                    ioEx.printStackTrace();
                    is = null;
                }                
            }            
        }
        
        return is;
    }
    
    protected static InputStream getConfigStream()
    {
        InputStream is = null;
        is = readInOverride();
        if (is == null)
        {
            is = Unpacker.class.getResourceAsStream("TypeBands.xml");
        }
        return is;
    }
    
    private static void readInFile(){
        fallback = new DefaultUnpacker();
        InputStream pathStream = null;
        
        pathStream = getConfigStream();
        
        if(pathStream == null)
        {
            System.out.println("failure to find TypeBands.xml config file");
            return;
        }
        
        Document doc = null;
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(pathStream);
        } catch(Exception ex){
            System.out.println("Exception Parsing TypeBands.xml:" + ex.getMessage());
        }
        
        if(doc == null)
        {
            System.out.println("failure to parse TypeBands.xml");
            return;
        }
        store = new HashMap<String,AbstractUnpacker>();

        Element elm = doc.getDocumentElement();//image_type_bands
        NodeList types = elm.getChildNodes();//image_ids
        for(int i = 0;i<types.getLength();i++)
        {
            Node nextType = types.item(i);//image_id
            if(nextType.getNodeName().equalsIgnoreCase("image_id"))
            {
                Element curElmt = (Element) nextType;
                
                String name = curElmt.getAttribute("name");//ie xyz/disparity/etc
                String type = curElmt.getAttribute("type");
                
                AbstractUnpacker unpacker = null;

                //all types that do not require byte unpacking
                if(type!=null && (type.equalsIgnoreCase("basic") ||
                                  type.equalsIgnoreCase("basiclookup") ||
                                  type.equalsIgnoreCase("armconfiglookup")))
                {
                    //basiclookup type is subclass of basic, so
                    //assign references accordingly...
                    //oh, armconfiglookup is subclass of basiclookup, so
                    //same...
                    
                    StandardUnpacker        stdUnpacker  = null;
                    StandardLookupUnpacker  slkUnpacker  = null;

                    if (type.equalsIgnoreCase("armconfiglookup"))
                    {
                        //we don't invoke any ArmConfig methods, so parent ref is fine
                        slkUnpacker = new ArmConfigLookupUnpacker();
                        stdUnpacker  = slkUnpacker;
                    }
                    else if (type.equalsIgnoreCase("basiclookup"))
                    {
                        slkUnpacker = new StandardLookupUnpacker();
                        stdUnpacker = slkUnpacker;
                    }
                    else
                    {
                        stdUnpacker = new StandardUnpacker();
                    }
                                        
                    //either once is assigned to the generic unpacker
                    unpacker = stdUnpacker;
                    
                    NodeList bands = nextType.getChildNodes();//bands
                    for(int b=0;b<bands.getLength();b++)
                    {
                        Node band = bands.item(b);//bands
                        if(band.getNodeName().equalsIgnoreCase("bands"))
                        {
                            Element bandEle = (Element) band;
                            
                            
                            String lenAttr = bandEle.getAttribute("length");
                            if (lenAttr == null)  //unnecessary but why not
                                lenAttr = "";
                            final boolean isReferenceBands = lenAttr.equals("*");
                            
                            //prefix is used whenever we need a name for a band
                            //that is not captured by one of the bands entries
                            final String labelPrefix = bandEle.getAttribute("prefix");
                            if (labelPrefix != null && !labelPrefix.isEmpty())
                            {
                                stdUnpacker.setLabelPrefix(labelPrefix);
                            }
                            
                            NodeList samples = band.getChildNodes();//values
                            String[] names = new String[samples.getLength()];
                            int valueCount = 0;
                            for(int s = 0;s<samples.getLength();s++)
                            {
                                Node sample = samples.item(s);//value
                                if(sample.getNodeName().equalsIgnoreCase("value"))
                                {
                                    names[valueCount] = sample.getTextContent();
                                    valueCount++;
                                }
                            }
                            
                            String[] prunedNames = new String[valueCount];
                            for(int k = 0; k<valueCount; k++){
                                prunedNames[k] = names[k];
                            }
                            
                            int lookupValue = isReferenceBands ? 
                                              StandardUnpacker.DEFAULT_BAND_LOOKUP :
                                              valueCount;
                            
                            stdUnpacker.add(lookupValue,prunedNames);
                        }
                    }
                    
                    //---------------------
                    
                    
                    //we have an instance or StandardLookup (or a subclass)
                    if(slkUnpacker != null )
                    {
                        NodeList children = nextType.getChildNodes();
                        for(int c=0;c<children.getLength();c++)
                        {
                            Node child = children.item(c);
                            
                            if(child.getNodeName().equalsIgnoreCase("lookup"))
                            {
                                Element childElement = (Element) child;   
                                String bandsAttr = childElement.getAttribute("bands");                                
                                int[] bandArray = StandardLookupUnpacker.parseLookupBands(bandsAttr);
                                Map<Integer, String> valueLookup = new Hashtable<Integer, String>();
                                
                                NodeList grandchildren = child.getChildNodes();//entries                 
                                
                                int v = 0;
                                for(int s = 0;s<grandchildren.getLength();s++)
                                {
                                    Node grandchild = grandchildren.item(s);//value
                                    if(grandchild.getNodeName().equalsIgnoreCase("entry"))
                                    {
                                        Element grandchildElement = (Element) grandchild;
                                        
                                        NamedNodeMap attributes = grandchild.getAttributes();
                                        String key = null, val = null;
                                        
                                        key = grandchildElement.getAttribute("key");
                                        val = grandchildElement.getAttribute("value");
                                                                           
                                        if (key != null && val != null)
                                        {
                                            try {
                                                Integer curInt = Integer.valueOf(key);
                                                valueLookup.put(curInt, val);
                                            } catch (NumberFormatException nfEx) {                                                
                                            }                                   
                                        }                                        
                                    }
                                }
                                
                                slkUnpacker.addLookup(bandArray, valueLookup);
                                
                            }
                        }
                        
                        
                    }
                }

                //all types that require special byte treatement
                else if(type!=null && type.equalsIgnoreCase("complex"))
                {
                    NodeList classNodeCandidates = nextType.getChildNodes();
                    String clazz = "jpl.mipl.mars.mis.unpackers.DefaultUnpacker";
                    Object cls = null;
                    for(int c = 0;c<classNodeCandidates.getLength();c++)
                    {
                        Node candidate = classNodeCandidates.item(c);
                        if(candidate.getNodeName().equalsIgnoreCase("class"))
                        {
                            String can = candidate.getTextContent();
                            if(can!=null&&!can.equals(""))
                            {
                                clazz = can;
                                break;
                            }
                        }
                    }
                    try{
                        cls = Class.forName(clazz).newInstance();
                    }catch(Exception e){
                        System.out.println("failture to create class: "+clazz);
                    }
                    if(cls instanceof AbstractUnpacker)
                    {
                        unpacker = (AbstractUnpacker)cls;
                    }
                    else
                    {
                        unpacker = new DefaultUnpacker();
                    }
                }
                else
                {
                    unpacker = new DefaultUnpacker();
                }

                //Useful for debugging
                //  System.out.println("added a "+((unpacker instanceof StandardUnpacker)?"Standard":"Complex")+"for: "+name);
                
                store.put(name,unpacker);
            }
        }
    }
}
