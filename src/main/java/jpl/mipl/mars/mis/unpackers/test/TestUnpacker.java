package jpl.mipl.mars.mis.unpackers.test;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Frankly, it is a pain to test this when we are just editing the TypeBands.xml
 * file.  So this test class simplifies that by using the same FileManager class
 * the Web-socket service uses to load files and extra their values (both keys
 * and translated values) for a given filepath, imagetype, and image coordinates.
 * Expected arguments:  imagePath  imageType xCoord yCoord
 * 
 * You can also create a text file with these entries per line, with # as comment lines.
 * Expected arguments:  indexFilePath
 *
 * @author Nicholas Toole (Nicholas.T.Toole@jpl.nasa.gov)
 * @version $Id: $
 *
 */
public class TestUnpacker
{
       
    protected FileManager fileMgr;
    
    public TestUnpacker()
    {     
        fileMgr = new FileManager();
    }
    
    public void runTest(String indexFilepath)
    {
        List<InputData> lines = parseFile(indexFilepath);
        if (lines.isEmpty())
        {
            System.err.println("Unable to parse any test cases from: "+indexFilepath);
            return;
        }
        
        for (InputData line : lines)
        {
            runTest(line.path, line.type, line.x, line.y);
        }
    }
    
    public void runTest(String filepath, String imagetype, int x, int y)
    {
        List<String> values = getValues(filepath, imagetype, x, y);
        
        StringBuffer buffer = new StringBuffer();
        buffer.append("Product : ").append(filepath).append("\n");
        buffer.append("Type    : ").append(imagetype).append("\n");
        buffer.append("Values at ("+x+","+y+")  :\n");
        
        for (String value : values)
        {
            buffer.append(value).append("\n");
        }
        System.out.println(buffer.toString());
        System.out.println();
    }
    
    protected List<String> getValues(String filepath, String imagetype, int x, int y)
    {
        fileMgr.add(filepath);
        
        List<String> keys = fileMgr.getKeys(filepath, imagetype);
        List         vals = fileMgr.getVals(filepath, imagetype, x, y);
        
        if (keys == null)
            keys = new ArrayList<String>();
        if (vals == null)
            vals = new ArrayList<String>();
        
        int max = Math.max(keys.size(), vals.size());
        
        List<String> lines = new ArrayList<String>();
        for (int i = 0; i < max; ++i)
        {
            String val = i < vals.size() ? vals.get(i).toString() : "NULL";
            String key = i < keys.size() ? keys.get(i).toString() : "NULL";
            
            String line = key + " = " + val;
            lines.add(line);            
        }
        
        fileMgr.remove(filepath);
        
        return lines;
    }
    
    public static void main(String[] args)
    {
        if (args.length != 1 && args.length != 4)
        {
            System.err.println("Expected arguments: ");
            System.err.println("                    filepath imagetpye xCoord yCoord");
            System.err.println("                    indexFileFormattedAsAbove");
            System.exit(2);
        }
        
        
        
        File tmp = new File(args[0]);
        if (!tmp.isFile())
        {
            System.err.println("Argument '"+args[0]+"' is not a file!");            
            System.exit(2);
        }
        
        if (args.length == 1)
        {
            TestUnpacker tester = new TestUnpacker();            
            tester.runTest(args[0]);
            return;
        }
        else
        {
        
        
            int x = 0, y = 0;
            try {
                x = Integer.parseInt(args[2]);
                y = Integer.parseInt(args[3]);
            } catch (NumberFormatException nfEx) {
                System.err.println("Argument '"+args[2]+"' and/or '"+args[3]+" is not an integer");            
                System.exit(2);
            }
            
            if (x < 0 || y < 0)
            {
                System.err.println("Arguments '"+args[2]+"' and '"+args[3]+" MUST be a POSITIVE integer");            
                System.exit(2);
            }
            
            try {
                TestUnpacker tester = new TestUnpacker();            
                tester.runTest(args[0], args[1], x, y);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);            
            }
        }
        
    }
    
    public List<InputData> parseFile(String indexFile)
    {
        List<InputData> inputList = new ArrayList<InputData>();
        LineNumberReader reader = null;
        try {
            reader = new LineNumberReader(new FileReader(indexFile));
            String line = null;
            while ((line = reader.readLine()) != null)
            {
                InputData data = parseLine(line);
                if (data != null)
                    inputList.add(data);
            }
            
            
        } catch (Exception ex) {
            
        } finally {
            if (reader != null) 
            { try { reader.close(); } catch (Exception ex) {}}
        }
        
        return inputList;
    }
    
    protected InputData parseLine(String line)
    {
        if (line == null)
            return null;
        line = line.trim();
        if (line.isEmpty())
            return null;
        if (line.startsWith("#"))
            return null;
        
        String[] parts = line.split("\\s+");
        
        if (parts.length < 4)
            return null;
        
        File file = new File(parts[0]);
        if (!file.isFile())
            return null;
        
        int x = 0, y = 0;
        try {
            x = Integer.parseInt(parts[2]);
            y = Integer.parseInt(parts[3]);
        } catch (Exception ex) {
            return null;
        }
        
        TestUnpacker.InputData data = new InputData(parts[0], parts[1], x, y);
        
        return data;
    }
    
    class InputData
    {       
        String path, type;
        int x, y;
        public InputData(String p, String t, int a, int b)
        {
            path = p;
            type = t;
            x = a;
            y= b;
        }
    }
}
