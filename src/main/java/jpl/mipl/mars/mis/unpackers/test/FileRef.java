package jpl.mipl.mars.mis.unpackers.test;

import jpl.mipl.mars.viewer.image.RenderedOpLoader;
import jpl.mipl.mars.mis.unpackers.Unpacker;

//import org.json.simple.JSONObject;

import java.util.Map;
import java.util.LinkedHashMap;

import java.io.File;


import javax.media.jai.RenderedOp;
import javax.media.jai.PlanarImage;
import java.awt.image.Raster;
import javax.imageio.ImageIO;

/**
 *	Contains the read-in file data associated with a given image 
 *	file as well as methods for extracting pixel data and life-span control.
 *	Each instance should have a unique file on a global scope to conserve 
 *	memory space and subsequent openings should only increment the reference 
 *	count. Best implementation is through a manager singleton such as
 *	@see jpl.mipl.mars.mis.FileManager . This class also takes care of unpacking pixel
 *	information for appropriate image types.
 *	@author Nolan Miller
 */
public class FileRef{
	private int refCount;
	private Raster image;
	private String path;
	private boolean loaded;
    private long modTime;
        
	/**
	 *	Constructs a FileReference object with a single reference registered
	 *	(after construction there is no need to increment count for the 
	 *	constructed ref). Default constructor is discouraged as FileRef is 
	 *	meaningless without an associated path (which the String 
	 *	constructor includes). For instance to be usefull, a path must be 
	 *	given and it must be instructed to load the file.
	 */
	public FileRef(){
		refCount = 1;
		path = "";
		loaded = false;
        modTime = 0;
    }

	/**
	 *	Constructs a FileReference object with the given path and sets the
	 *	reference count to 1. @see #loadData() must still be called in order
	 *	to load the data from the provided path.
	 *	@param url the String that will be loaded as the pixel data source. 
	 */
	public FileRef(String url){
		refCount = 1;
		path = url;
		image = null;
		loaded = false;
        File tmp = new File(url);
        modTime = tmp.lastModified();
	}

    public long getModTime(){
        return modTime;
    }
    
	/**
	 *	Forces a load of the data at the current path. Upon failure error 
	 *	messages are printed to stdout but errors are not thrown. 
	 *	@return true/false whether the load was a success 
	 */
	public boolean loadData(){
//				ImageIO.scanForPlugins();
//				System.out.println(""+jpl.mipl.io.vicar.VicarIO.isCodecAvailable());
//				for(String s : ImageIO.getReaderFormatNames()){
//					System.out.println(s);	
//				}

		RenderedOp load = null;
		PlanarImage pi = null;
		try{
			load = RenderedOpLoader.loadImage(this.path);
			pi = load.getNewRendering(); //will fail if load is null (ie doesn't exist)
			image = pi.copyData();
		}catch(Exception e){
			e.printStackTrace();	
			Throwable cause = e.getCause();
			if (cause != null)
				cause.printStackTrace();
			System.out.println("Failure to read file: "+this.path);
		}
		if(image==null)
			return false;
		loaded=true;
		return true;
	}

	/**
	 *	Sets the path to the given string and forces a load of the data.
	 *  @param url URL of the resource to be loaded
	 *	@return true/false whether the load was successful 
	 *	(the path is set regardless)
	 */
	public boolean loadData(String url){
		path = url;
		return loadData();
	}

	/**
	 *	Increments the reference count (should be called when another owner 
	 *	requests access to the file
	 */
	public void addRef(){
		refCount++;
	}

	/** 
	 *	Decrements the reference count. If it is negative, it is reset to 0.
	 *	(ref count may not be less that 0)
	 */
	public void removeRef(){
		refCount--;
		if(refCount<0)
			refCount = 0;
	}

	/**
	 *	Checks if there are no references currently registered for the 
	 *	FileRef.
	 *	@return true/false if reference count is less than 1
	 */
	public boolean noRefs(){
		return refCount<=0;
	}

	/**
	 *	Getter for the reference count
	 *	@return the integer reference count
	 */
	public int getRefCount(){
		return refCount;
	}

	/**
	 *	Gets the pixel value at collum x, row y. If no data is present for 
	 *	given pixel, an empty response is returned ([]). Responses are a
	 *	String formatted array of the band values enclosed in square brackets
	 *	and separated by commas. All values are returned as floating point
	 *	to ensure support for all underylying data types.
	 *	@param x integer index for collum of desired pixel
	 *	@param y integer index for row of desired pixel
	 *	@return String representation of band data for pixel 
	 */
	String getRawDataAt(int x,int y){
		if(!this.loaded)
			this.loadData();
		float[] vals = null; 
		String ret = "";

		if(image!=null){
			try{
				vals = image.getPixel(x,y,vals);
			}catch(Exception e){
				return "[]";
			}
			ret = "[";
			for(float i : vals){
				ret+=i;
				ret+=",";
			}
			if(ret.equals("["))
				return "[]";
			ret = ret.substring(0,ret.length()-1) + "]";
		}
		else
			ret= "[]";
		return ret;
	}

	/**
	 *	Gets map pixel value at collum x, row y. If no data is present for 
	 *	given pixel, an empty response is returned. Responses are a
	 *	json formatted dictionaries of the band values and band names
	 *	@param x integer index for collum of desired pixel
	 *	@param y integer index for row of desired pixel
     *  @param type string imagetype for the file being queried
	 *	@return Json representation of band data for pixel 
	 */
	Map getDataAt(int x,int y,String type){
		if(type == null)
            type = "unk";

        if(!this.loaded)
			this.loadData();
		float[] fvals = null; 
		int[] ivals = null;
		
        Map<String,Object> pix = new LinkedHashMap<String,Object>();
		
		if(image!=null){
			try{
				fvals = image.getPixel(x,y,fvals);
				ivals = image.getPixel(x,y,ivals);
                pix = Unpacker.unpack(type,fvals,ivals);
			}
			catch(Exception e){
				System.out.println("something happened here.");
				fvals = null;
			}
		}
		return pix;		
	}

    

}
