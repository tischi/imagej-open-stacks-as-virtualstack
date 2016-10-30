package ct.vss;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.awt.event.*;
import ij.*;
import ij.ImagePlus;
import ij.io.Opener;
import ij.io.FileInfo;
import ij.gui.*;
import ij.plugin.PlugIn;

import static ij.IJ.log;

/** Opens a folder of stacks as a virtual stack. */
public class Open_Stacks_As_VirtualStack implements PlugIn {

	private static boolean grayscale;
	private static double scale = 100.0;
	private int n=0, start=0, increment=0;
	private String filter;
	private String info1;

	public void run(String arg) {
		String directory = IJ.getDirectory("Select a Directory");
		if (directory == null)
			return;
		//Macro.setOptions(null); // Prevents later use of OpenDialog from reopening the same file
		//IJ.register(Open_Stacks_As_VirtualStack.class);
		ImagePlus imp = openStacksAsVirtualStack(directory, 0);
		imp.show();
	}

	public Open_Stacks_As_VirtualStack() {

	}

	public ImagePlus openStacksAsVirtualStack(String directory, int inc) {
		this.increment = inc;
        FileInfo[] info = null;
		FileInfo[][] infos = null;
		FileInfo fi = null;
		VirtualStackOfStacks stack = null;
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
        ImagePlus imp2 = null;

		log("");
        log("");
        log("# Analyzing folder: "+directory);
        String[] list = new File(directory).list();
		if (list==null || list.length==0)
			return null;
        log("Number of files: " + list.length);
        list = this.sortFileList(list);
		if (list==null) return null;

		try {

			log("# Checking offsets to first image in all files");
			long offset0 = 0;
			boolean differentOffsets = false;
			for(int i=0; i<list.length; i++) {
				try {
					TiffDecoderExtension tde = new TiffDecoderExtension(directory, list[i]);
					fi = tde.getFirstIFD();
				} catch (IOException ex) {
					IJ.showMessage("File Checking", ex.toString());
					return null;
				}
				if (i == 0) {
					offset0 = fi.getOffset();
					log("File: " + list[i]);
					log("Offset: " + fi.getOffset());
				} else if (fi.getOffset() != offset0) {
					log("File: " + list[i]);
					log("Offset:" + i + ": " + fi.getOffset());
					differentOffsets = true;
				}
			}
			if(differentOffsets) {
				log("");log("There have been different offsets!");log("");
			} else {
				log("All offsets are the same.");
			}

            log("# Analyzing IFDs from: " + list[0]);
            info = Opener.getTiffFileInfo(directory + list[0]);

            if (info == null) {
                log("Failed to open file!");
                return (null);
            }

            if (info[0].width==0) {
                IJ.showMessage("File Checking", "This folder does not appear to contain only TIFF Stacks");
                return null;
            }

            fi = info[0]; // Set first IFD as reference

            if(info.length > 1) {
                log("Number of IFDs: " + info.length);
                log("nImages: " + info[0].nImages);

                int size = 0, sizeOfFirstImage = 0, gapBetweenImages = 0, gapBetweenFirstImages = 0;
                for (int j = 0; j < info.length-1; j++) {
                    size = info[j].width*info[j].height*info[j].getBytesPerPixel();
                    gapBetweenImages = (int) (info[j+1].getOffset() - info[j].getOffset() - size);
					log(""+info[j].getOffset());
                    if (j==0) {
                        gapBetweenFirstImages = gapBetweenImages;
                        sizeOfFirstImage = size;
                        log("gapBetweenImages "+ j + ": " + gapBetweenFirstImages);
                        log("image size "+ j + ": " + sizeOfFirstImage);
                    } else if (gapBetweenImages != gapBetweenFirstImages) {
                        log("gapBetweenImages " + j + ": " + gapBetweenImages);
                        log("image size "+ j + ": " + size);
                        //IJ.showMessage("Import image stack", "Inconsistent image stack; check log window!");
                        //return null;
                    }
                    if (size != sizeOfFirstImage) {
                        log("Size of image 1: " + sizeOfFirstImage);
                        log("Size of image "+j+": " + size);
                        log("Gap between images: " + gapBetweenImages);
                        //IJ.showMessage("Import image stack", "Inconsistent image stack; check log window!");
                        //return null;
                    }
                    //log("Size image: "+info[j].width*info[j].height*info[j].getBytesPerPixel());
                    //log("Gap between images: " + gapBetweenImages);
                }
                log("Size of all images: "+info[0].width*info[0].height*info[0].getBytesPerPixel());
                log("Gap between all images: " + gapBetweenImages);
                fi.gapBetweenImages = gapBetweenFirstImages;
            } else {
                log("Number of IFDs: " + info.length);
                log("nImages: " + fi.nImages);
                log("Image size [B]: " + fi.width * fi.height * fi.getBytesPerPixel());
                log("gapBetweenImages: " + fi.gapBetweenImages);
            }
            log("File checking done.\n");

            if (increment == 0) {  // only show user dialog if increment is not set
                if (!showDialog(info, list))
                    return null;
            }

            // Collect files according to filter
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
					return null;
				}
			}
			n = filteredImages;
			
			int count = 0;
			int counter = 0;

			for (int i=start-1; i<list.length; i++) {
				//IJ.log("Processing "+list[i]);
				if (filter!=null && (list[i].indexOf(filter)<0))
					continue;
				if ((counter++%increment)!=0)
					continue;
				if (stack==null) {

					// only opens first slice
					//log("Obtaining additional information by opening first slice of first stack...");
					//imp = new OpenerExtensions().openPlaneInTiffUsingGivenFileInfo(directory, list[i], 1, info);
					//imp = new Opener().openImage(directory+list[i], 1);
                    // Collect image stack information

                    int depth;
                    ColorModel cm = null;
                    if( fi.nImages > 1) {
                        depth = fi.nImages;
                    }
                    else {
                        depth = info.length;
                        fi.nImages = info.length;
                    }

                    stack = new VirtualStackOfStacks(fi.width, fi.height, fi.nImages, cm, directory, fi, info);

				}
				count = stack.getNStacks()+1;
				//IJ.showStatus(count+"/"+n);
				info = Opener.getTiffFileInfo(directory + list[i]);
				// add stack to virtual stack
				stack.addStack(list[i], info);
				IJ.showProgress((double) count / n);
				if (count>=n)
					break;
			}
		} catch(OutOfMemoryError e) {
			IJ.outOfMemory("FolderOpener");
			if (stack!=null) stack.trim();
		//}catch(IOException e){
		//	  e.printStackTrace();
		}
		if (stack!=null && stack.getSize()>0) {
            imp2 = new ImagePlus("Stack", stack);
			if (imp2.getType()==ImagePlus.GRAY16 || imp2.getType()==ImagePlus.GRAY32)
				imp2.getProcessor().setMinAndMax(min, max);
			imp2.setFileInfo(fi); // saves FileInfo of the first image
			if (imp2.getStackSize()==1 && info1!=null)
				imp2.setProperty("Info", info1);
			int nC = 1;
			int nZ = imp2.getNSlices() / stack.getNStacks();
			int nT = stack.getNStacks();
			imp2.setDimensions(nC, nZ, nT);
			imp2.setOpenAsHyperStack(true);
            imp2.resetDisplayRange();
			//ImageProcessor ipc = stack.getCroppedProcessor(10, 690, 100, 430, 100);
			//ImagePlus impc = new ImagePlus("ipc",ipc);
			//impc.show();
		}
		IJ.showProgress(1.0);
        return(imp2);
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

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads an
	 * image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */
	public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = Open_Stacks_As_VirtualStack.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();
		//IJ.debugMode = true;

		Open_Stacks_As_VirtualStack ovs = new Open_Stacks_As_VirtualStack();

        //ImagePlus imp = ovs.openStacksAsVirtualStack("/Users/tischi/Desktop/example-data/T88200-IJtiff/", 1);
        ImagePlus imp = ovs.openStacksAsVirtualStack("/Users/tischi/Desktop/example-data/MATLABtiff/", 1);
		//ImagePlus imp = ovs.openStacksAsVirtualStack("/Users/tischi/Desktop/example-data/T88200-OMEtiff/", 1);
        //imp.show();

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        ImagePlus impC = vss.getCroppedFrameAsImagePlus(0,0,0,2,754,100,417,100);
		//ImagePlus impC = vss.getCroppedFrameAsImagePlus(0,1,20,10,30,70,30,70); //T88200-OMEtiff
		impC.show();
		impC.setPosition(5);
		impC.resetDisplayRange();

		// open the Clown sample
		//ImagePlus image = IJ.openImage("http://imagej.net/images/clown.jpg");
		//image.show();

		// run the plugin
		//IJ.runPlugIn(clazz.getName(), "");
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

