//package ij.plugin;
import ij.plugin.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.process.*;


/** Opens a folder of stacks as a virtual stack. */
public class Open_Stacks_As_VirtualStack implements PlugIn {

	private static boolean grayscale;
	private static double scale = 100.0;
	private int n, start, increment;
	private String filter;
	private FileInfo fi;
	private String info1;

	public void run(String arg) {
		String directory = IJ.getDirectory("Select a Directory");
		if (directory==null)
			return;
		Macro.setOptions(null); // Prevents later use of OpenDialog from reopening the same file
		
		IJ.log("Obtaining file list...");
		String[] list = new File(directory).list();
		if (list==null || list.length==0)
			return;
		IJ.register(Open_Stacks_As_VirtualStack.class);
		IJ.log("Sorting file list...");
		list = sortFileList(list);
		if (list==null) return;
		if (IJ.debugMode) IJ.log("FolderOpener: "+directory+" ("+list.length+" files)");
		int width=0,height=0,depth=0,type=0;
		FileInfo[] info = null;
		FileInfo fi = null;
		VirtualStackOfStacks stack = null;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		
		try {
			
			IJ.log("Obtaining info from first file...");
			
			for (int i=0; i<list.length; i++) {
				
				info = new Opener().getTiffFileInfo(directory+list[i]);
				IJ.log("file info length: "+info.length);												
				fi = info[0];
				IJ.log("filename: "+fi.fileName);								
				IJ.log("bit-depth: "+8*fi.getBytesPerPixel());
				IJ.log("nx: "+fi.width);
				IJ.log("ny: "+fi.height);
				IJ.log("nImages: "+fi.nImages);
				IJ.log("offset: "+fi.getOffset());
				IJ.log(""+fi.toString());
				if (fi!=null) {
					width = fi.width;
					//height = imp.getHeight();
					//depth = imp.getNSlices();
					//type = imp.getType();
					//fi = imp.getOriginalFileInfo();
					if (!showDialog(info, list))
						return;
					break;
				}
			}
			if (width==0) {
				IJ.showMessage("Import Sequence", "This folder does not appear to contain any TIFF,\n"
				+ "JPEG, BMP, DICOM, GIF, FITS or PGM files.");
				return;
			}

			if (n<1)
				n = list.length;
			if (start<1 || start>list.length)
				start = 1;
			if (start+n-1>list.length)
				n = list.length-start+1;
			int filteredImages = n;
			if (filter!=null && (filter.equals("") || filter.equals("*")))
				filter = null;
			if (filter!=null) {
				filteredImages = 0;
				for (int i=start-1; i<start-1+n; i++) {
					if (list[i].indexOf(filter)>=0)
						filteredImages++;
				}
				if (filteredImages==0) {
					IJ.error("None of the "+n+" files contain\n the string '"+filter+"' in their name.");
					return;
				}
			}
			n = filteredImages;
			
			int count = 0;
			int counter = 0;
			ImagePlus imp = null;
			for (int i=start-1; i<list.length; i++) {
				IJ.log("Processing "+list[i]);
				if (filter!=null && (list[i].indexOf(filter)<0))
					continue;
				if ((counter++%increment)!=0)
					continue;
				//if (stack==null)
				//	imp = new Opener().openImage(directory, list[i]);
				//if (imp==null) continue;
				if (stack==null) {
					
					depth = 0;
					if( fi.nImages > 0) 
						depth = fi.nImages;
					else
						depth = info.length;
					
					// only opens first slice
					IJ.log("Obtaining additional information by opening first slice of first stack...");
					imp = new OpenerExtensions().openPlaneInTiffUsingGivenFileInfo(directory, list[i], 1, info);
					//imp = new Opener().openImage(directory+list[i], 1);
					ColorModel cm = imp.getProcessor().getColorModel();
					width = imp.getWidth();
					height = imp.getHeight();
					type = imp.getType();
					//depth = imp.getNSlices();
					stack = new VirtualStackOfStacks(width, height, depth, cm, directory, info);
				 }
				count = stack.getNStacks()+1;
				IJ.showStatus(count+"/"+n);
				IJ.showProgress((double)count/n);
				stack.addStack(list[i]);
				if (count>=n)
					break;
			}
		} catch(OutOfMemoryError e) {
			IJ.outOfMemory("FolderOpener");
			if (stack!=null) stack.trim();
		}
		if (stack!=null && stack.getSize()>0) {
			ImagePlus imp2 = new ImagePlus("Stack", stack);
			if (imp2.getType()==ImagePlus.GRAY16 || imp2.getType()==ImagePlus.GRAY32)
				imp2.getProcessor().setMinAndMax(min, max);
			imp2.setFileInfo(fi); // saves FileInfo of the first image
			if (imp2.getStackSize()==1 && info1!=null)
				imp2.setProperty("Info", info1);
			int nC = 1;
			int nZ = (int) imp2.getNSlices() / stack.getNStacks();
			int nT = stack.getNStacks();
			imp2.setDimensions(nC, nZ, nT);
			imp2.setOpenAsHyperStack(true); 
			imp2.show();
		}
		IJ.showProgress(1.0);
	}
	
	boolean showDialog(FileInfo[] info, String[] list) {
		int fileCount = list.length;
		VirtualOpenerDialog gd = new VirtualOpenerDialog("Sequence Options", info, list);
		gd.addNumericField("Number of Stacks:", fileCount, 0);
		gd.addNumericField("Starting Stack:", 1, 0);
		gd.addNumericField("Increment:", 1, 0);
		gd.addStringField("File Name Contains:", "");
		gd.addMessage("10000 x 10000 x 1000 (100.3MB)");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		n = (int)gd.getNextNumber();
		start = (int)gd.getNextNumber();
		increment = (int)gd.getNextNumber();
		if (increment<1)
			increment = 1;
		filter = gd.getNextString();
		return true;
	}

	String[] sortFileList(String[] rawlist) {
		int count = 0;
		for (int i=0; i< rawlist.length; i++) {
			String name = rawlist[i];
			if (name.startsWith(".")||name.equals("Thumbs.db")||name.endsWith(".txt"))
				rawlist[i] = null;
			else
				count++;
		}
		if (count==0) return null;
		String[] list = rawlist;
		if (count<rawlist.length) {
			list = new String[count];
			int index = 0;
			for (int i=0; i< rawlist.length; i++) {
				if (rawlist[i]!=null)
					list[index++] = rawlist[i];
			}
		}
		int listLength = list.length;
		boolean allSameLength = true;
		int len0 = list[0].length();
		for (int i=0; i<listLength; i++) {
			if (list[i].length()!=len0) {
				allSameLength = false;
				break;
			}
		}
		if (allSameLength)
			{ij.util.StringSorter.sort(list); return list;}
		int maxDigits = 15;		
		String[] list2 = null;	
		char ch;	
		for (int i=0; i<listLength; i++) {
			int len = list[i].length();
			String num = "";
			for (int j=0; j<len; j++) {
				ch = list[i].charAt(j);
				if (ch>=48&&ch<=57) num += ch;
			}
			if (list2==null) list2 = new String[listLength];
			if (num.length()==0) num = "aaaaaa";
			num = "000000000000000" + num; // prepend maxDigits leading zeroes
			num = num.substring(num.length()-maxDigits);
			list2[i] = num + list[i];
		}
		if (list2!=null) {
			ij.util.StringSorter.sort(list2);
			for (int i=0; i<listLength; i++)
				list2[i] = list2[i].substring(maxDigits);
			return list2;	
		} else {
			ij.util.StringSorter.sort(list);
			return list;   
		}	
	}

}

class VirtualOpenerDialog extends GenericDialog {
	FileInfo[] info;
	int fileCount;
	boolean eightBits;
	String saveFilter = "";
	String[] list;

	public VirtualOpenerDialog(String title, FileInfo[] info, String[] list) {
		super(title);
		this.info = info;
		this.list = list;
		this.fileCount = list.length;
	}

	protected void setup() {
		setStackInfo();
	}
	
	public void itemStateChanged(ItemEvent e) {
		setStackInfo();
	}
	
	public void textValueChanged(TextEvent e) {
		setStackInfo();
	}

	void setStackInfo() {
		FileInfo fi = info[0];
		int width = fi.width;
		int height = fi.height;
		int depth = 0;

		if(fi.nImages>0) 
			depth = fi.nImages;
		else
			depth = info.length;

		int n = getNumber(numberField.elementAt(0));
		int start = getNumber(numberField.elementAt(1));
		int inc = getNumber(numberField.elementAt(2));
		 
		if (n<1)
			n = fileCount;
		if (start<1 || start>fileCount)
			start = 1;
		if (start+n-1>fileCount)
			n = fileCount-start+1;
		if (inc<1)
			inc = 1;
		TextField tf = (TextField)stringField.elementAt(0);
		String filter = tf.getText();
		// IJ.write(nImages+" "+startingImage);
		if (!filter.equals("") && !filter.equals("*")) {
			int n2 = n;
			n = 0;
			for (int i=start-1; i<start-1+n2; i++)
				if (list[i].indexOf(filter)>=0) {
					n++;
					//IJ.write(n+" "+list[i]);
				}
			saveFilter = filter;
		}
		((Label)theLabel).setText("x:"+width+" y:"+height+" z:"+depth+" t:"+n);
	}

	public int getNumber(Object field) {
		TextField tf = (TextField)field;
		String theText = tf.getText();
		double value;
		Double d;
		try {d = new Double(theText);}
		catch (NumberFormatException e){
			d = null;
		}
		if (d!=null)
			return (int)d.doubleValue();
		else
			return 0;
	  }

}


/**
This class represents an array of disk-resident images.
*/
class VirtualStackOfStacks extends ImageStack{
	static final int INITIAL_SIZE = 100;
	String path;
	int depth;
	int nSlices;
	int nStacks;
	String[] names;
	FileInfo[] info;
	
	/** Creates a new, empty virtual stack. */
	public VirtualStackOfStacks(int width, int height, int depth, ColorModel cm, String path, FileInfo[] info) {
		super(width, height, cm);
		this.path = path;
		this.depth = depth;
		this.info = info;
		names = new String[INITIAL_SIZE];
		//IJ.log("VirtualStackOfStacks: "+path);
	}

	 /** Adds an stack to the end of the stack. */
	public void addStack(String name) {
		if (name==null) 
			throw new IllegalArgumentException("'name' is null!");
		nSlices = nSlices + depth;
		nStacks ++;
	   IJ.log("adding stack "+nStacks+":"+name);
	   //IJ.log("total number of slices:"+nSlices);
	   if (nStacks==names.length) {
			String[] tmp = new String[nStacks*2];
			System.arraycopy(names, 0, tmp, 0, nStacks);
			names = tmp;
		}
		names[nStacks-1] = name;
	}

   /** Does nothing. */
	public void addSlice(String sliceLabel, Object pixels) {
	}

	/** Does nothing.. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
	}
	
	/** Does noting. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
	}

	/** Deletes the specified slice, were 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
			if (nSlices<1)
				return;
			for (int i=n; i<nSlices; i++)
				names[i-1] = names[i];
			names[nSlices-1] = null;
			nSlices--;
		}
	
	/** Deletes the last slice in the stack. */
	public void deleteLastSlice() {
		if (nSlices>0)
			deleteSlice(nSlices);
	}
	   
   /** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		else
			return null;
	}		
	
	 /** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	public void setPixels(Object pixels, int n) {
	}

   /** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		int nFile, nSlice;
		// get z-th slice of a tif stack
		nFile = (int) (n-1)/depth;
		nSlice = n - nFile * depth;
		// IJ.log("requested slice: "+n);
		IJ.log("opening slice "+nSlice+" of "+path+names[nFile]);
		//ImagePlus imp = new Opener().openImage(path+names[nFile], nSlice);
                        ImagePlus imp = new OpenerExtensions().openPlaneInTiffUsingGivenFileInfo(path, names[nFile], nSlice, info);
		if (imp!=null) {
			int w = imp.getWidth();
			int h = imp.getHeight();
			int type = imp.getType();
			ColorModel cm = imp.getProcessor().getColorModel();
		} else {
			IJ.log("Error: loading failed!");	
			return null;
		}		
		return imp.getProcessor();
	 }
 
	 /** Returns the number of slices in this stack. */
	public int getSize() {
		return nSlices;
	}

	 /** Returns the number of stacks in this stack. */
	public int getNStacks() {
		return nStacks;
	}

	/** Returns the file name of the Nth image. */
	public String getSliceLabel(int n) {
		int nFile;
		nFile = (int) n / depth;
		return names[nFile];
	}
	
	/** Returns null. */
	public Object[] getImageArray() {
		return null;
	}

   /** Does nothing. */
	public void setSliceLabel(String label, int n) {
	}

	/** Always return true. */
	public boolean isVirtual() {
		return true;
	}

   /** Does nothing. */
	public void trim() {
	}
		
}


/** Opens the nth image of the specified TIFF stack. */
class OpenerExtensions extends Opener { 

	public OpenerExtensions() {
	}
 
	public ImagePlus openPlaneInTiffUsingGivenFileInfo(String directory, String filename, int n, FileInfo[] info) {
		IJ.log("openPlaneInTiffUsingGivenFileInfo");
		IJ.log("  directory:"+directory);
		IJ.log("  filename:"+filename);
		IJ.log("  slice:"+n);
		if (info==null) return null;
		FileInfo fi = null;
		if (info.length==1 && info[0].nImages>1) {
			/** in this case getTiffFileInfo will only open the first IDF; as this is rather fast we do it
			for every file to see different fi.offset, which does happen */
			FileInfo[] infoThisFile = new Opener().getTiffFileInfo(directory+filename);
			fi = (FileInfo) infoThisFile[0];
			if (n<1 || n>fi.nImages)
				throw new IllegalArgumentException("N out of 1-"+fi.nImages+" range");
			long size = fi.width*fi.height*fi.getBytesPerPixel();
			fi.longOffset = fi.getOffset() + (n-1)*(size+fi.gapBetweenImages);
			fi.offset = 0;
			fi.nImages = 1;
		} else {
			/** it would take to long to open all IFDs again; so we hope that the ones from the
			first file work */
			fi = (FileInfo) info[0].clone();
			fi.fileName = filename;
			fi.directory = directory;		
			if (n<1 || n>info.length)
				throw new IllegalArgumentException("N out of 1-"+info.length+" range");
			fi.longOffset = info[n-1].getOffset();
			fi.offset = 0;
			fi.stripOffsets = info[n-1].stripOffsets; 
			fi.stripLengths = info[n-1].stripLengths; 
		}
		FileOpener fo = new FileOpener(fi);
		return fo.open(false);
	}
}

