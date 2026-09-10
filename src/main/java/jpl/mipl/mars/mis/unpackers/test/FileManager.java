package jpl.mipl.mars.mis.unpackers.test;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;


/**
 *	Manages a global list of FileRefs and ensures a unique
 *	read in of each filepath from memory. Should only be instantiated once (as a singleton). 
 *	Provides access point for the file data to the end user of the instance. Files are stored
 *	in a LinkedHashMap with lookup based on the filepath (gaurunteed unique)
 *	@see jpl.mipl.mars.mis.FileRef
 *	@author Nolan Miller
 */
public class FileManager{

	private Map<String,FileRef> files;

	/**
	 *	Default constructor makes an instantiates the file repository
	 *	(empty). This file container is not static so the FileManager must
	 *	be instatiated once GLOBALLY.
	 */
	public FileManager(){
		files = new LinkedHashMap<String,FileRef>();
	}

	/**
	 *	Prints out a test statment to stdout. It contains a list of every
	 *	file currently read in as well as reference count for how many
	 *	users are currently registered with that file.
	 */
	public void test(){
		for(String s : files.keySet()){
			System.out.println(s + "---" + files.get(s).getRefCount());
		}
	}
	/**
	 *	Adds the given file to the open list and forces a data load - if 
	 *	file was already present the reference count is incremented. Returns
	 * 	false and prints error message to stdout if the file could not be
	 *	loaded. The list is not changed if the file could not be loaded.
	 *	@param url the String path for the file to be loaded
	 *	@return true/false whether or not the load was a success
	 */
	public boolean add(String url){
		FileRef f = files.get(url);
		if(f==null){
			FileRef fr = new FileRef(url);
			if(fr.loadData(url)){
				files.put(url,fr);
				return true;
			}
			return false;
		}
		else{
			f.addRef();
		}
		return true;
	}

	/**
	 *	Decrements the reference count to the given file and deletes the
	 *	FileRef instance if the reference count is 0 after decrementation.
	 *	@param url the String path for the file to be removed/decremented.
	 */	
	public void remove(String url){
		FileRef f = files.get(url);
		if(f!=null){
			f.removeRef();
			if(f.noRefs()){
				files.remove(url);
			}
		}
	}

	/**
	 *	Gets the pixel data for the given file at the given indexes formatted as a json dict. 
	 *	@param id the unique id (path) String of the file to get the pixel data from (it will
	 *	not be reopened, the path is just a uid)
	 *  @param fileType string imagetype for the file being queried
	 *	@param x the integer index for the target pixel column
	 *	@param y the integer index for the target pixel row
	 *	@return the String returned by the FileRefs getData method
	 */
	public Map getData(String id, String fileType, int x, int y){
		FileRef f = files.get(id);
		if(f!=null){
			return f.getDataAt(x,y,fileType);
		}
		else{
			System.out.println("file is not in map!");
		}
		return null;
	}
	
	/**
	 *	Gets the pixel data for the given file at the given indexes. 
	 *	@param id the unique id (path) String of the file to get the pixel data from (it will
	 *	not be reopened, the path is just a uid)
	 *	@param x the integer index for the target pixel collum
	 *	@param y the integer index for the target pixel row
	 *	@return the String returned by the FileRefs getData method
	 */
	public String getRawData(String id, int x, int y){
		FileRef f = files.get(id);
		if(f!=null){
			return f.getRawDataAt(x,y);
		}
		else{
			System.out.println("file is not in map!");
		}
		return null;
	}

    public long getModTime(String id){
        FileRef f = files.get(id);
        if(f!=null){
            return f.getModTime();
        }
        else{
            System.out.println("file is not in map!");
        }
        return 0;
    }

    public List<String> getKeys(String id, String fileType){
		FileRef f = files.get(id);
		if(f!=null){
			return new ArrayList(f.getDataAt(0,0,fileType).keySet());//check that this is correct order
		}
		else{
			System.out.println("file is not in map!");
		}
		return null;
    }
    public List getVals(String id, String fileType, int x, int y){
		FileRef f = files.get(id);
		if(f!=null){
			return new ArrayList(f.getDataAt(x,y,fileType).values());
		}
		else{
			System.out.println("file is not in map!");
		}
		return null;
    }

}
