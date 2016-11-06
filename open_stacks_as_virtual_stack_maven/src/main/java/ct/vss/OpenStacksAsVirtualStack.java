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
import ij.plugin.HyperStackConverter;
import javafx.geometry.Point3D;

import static ij.IJ.log;
import static ij.IJ.open;

/** Opens a folder of stacks as a virtual stack. */
public class OpenStacksAsVirtualStack implements PlugIn {

    private static boolean grayscale;
    private static double scale = 100.0;
    private int n, start, increment;
    private int nChannels, nFrames, nSlices;
    private String filter;
    private String info1;
    private String directory;
    private String[] list;
    private String openingMethod; // tiffUseIFDsFirstFile;
    private String order; // ct, tc


    public OpenStacksAsVirtualStack() {
        // empty constructor for opening from FileInfo[]
    }

    public OpenStacksAsVirtualStack(String directory, String filter, int start, int increment, int n, int nChannels, String openingMethod, String order) {
        this.directory = directory;
        this.filter = filter;
        this.start = start;
        this.increment = increment;
        this.n = n; // set = -1 if open all
        this.list = getFilesInFolder(directory);
        this.openingMethod = openingMethod;
        this.order = order;
        this.nChannels = nChannels;
        // constructor for opening from directory
    }

    public void run(String arg) {
        this.directory = IJ.getDirectory("Select a Directory");
        if (directory == null)
            return;
        //Macro.setOptions(null); // Prevents later use of OpenDialog from reopening the same file
        //IJ.register(Open_Stacks_As_VirtualStack.class);

        this.list = getFilesInFolder(directory);
        if (!showDialog(list)) return;

        // todo: add this to gui
        this.openingMethod = "tiffUseIFDsFirstFile";
        ImagePlus imp = openFromDirectory();
        imp.show();


    }

    boolean showDialog(String[] list) {
        int fileCount = list.length;
        VirtualOpenerDialog gd = new VirtualOpenerDialog("Sequence opening options", list);
        gd.addNumericField("Number of stacks:", fileCount, 0);
        gd.addNumericField("Starting stack:", 1, 0);
        gd.addNumericField("Increment:", 1, 0);
        gd.addStringField("File name contains:", "");
        gd.addNumericField("Number of channels:", 1, 0);
        String[] fileOrders = {"ct", "tc"};
        gd.addChoice("File order:", fileOrders, "ct");

        gd.showDialog();
        if (gd.wasCanceled())
            return false;

        // set global variables
        n = (int) gd.getNextNumber();
        start = (int) gd.getNextNumber();
        increment = (int) gd.getNextNumber();
        if (increment < 1)
            increment = 1;
        nChannels = (int) gd.getNextNumber();
        filter = gd.getNextString();
        order = gd.getNextString();
        return true;
    }

    String[] sortFileList(String[] rawlist) {
        int count = 0;
        for (int i = 0; i < rawlist.length; i++) {
            String name = rawlist[i];
            if (name.startsWith(".") || name.equals("Thumbs.db") || name.endsWith(".txt"))
                rawlist[i] = null;
            else
                count++;
        }
        if (count == 0) return null;
        String[] list = rawlist;
        if (count < rawlist.length) {
            list = new String[count];
            int index = 0;
            for (int i = 0; i < rawlist.length; i++) {
                if (rawlist[i] != null)
                    list[index++] = rawlist[i];
            }
        }
        int listLength = list.length;
        boolean allSameLength = true;
        int len0 = list[0].length();
        for (int i = 0; i < listLength; i++) {
            if (list[i].length() != len0) {
                allSameLength = false;
                break;
            }
        }
        if (allSameLength) {
            ij.util.StringSorter.sort(list);
            return list;
        }
        int maxDigits = 15;
        String[] list2 = null;
        char ch;
        for (int i = 0; i < listLength; i++) {
            int len = list[i].length();
            String num = "";
            for (int j = 0; j < len; j++) {
                ch = list[i].charAt(j);
                if (ch >= 48 && ch <= 57) num += ch;
            }
            if (list2 == null) list2 = new String[listLength];
            if (num.length() == 0) num = "aaaaaa";
            num = "000000000000000" + num; // prepend maxDigits leading zeroes
            num = num.substring(num.length() - maxDigits);
            list2[i] = num + list[i];
        }
        if (list2 != null) {
            ij.util.StringSorter.sort(list2);
            for (int i = 0; i < listLength; i++)
                list2[i] = list2[i].substring(maxDigits);
            return list2;
        } else {
            ij.util.StringSorter.sort(list);
            return list;
        }
    }

    String[] getFilesInFolder(String directory) {
        log("");
        log("# Finding files in folder: " + directory);
        // todo: can getting the file-list be faster?
        String[] list = new File(directory).list();
        if (list == null || list.length == 0)
            return null;
        log("Number of files: " + list.length);
        list = this.sortFileList(list);
        log("Sorted files:");
        for(String item : list) log("" + item);
        if (list == null) return null;
        else return (list);
    }


    public ImagePlus openFromDirectory() {
        boolean checkOffsets = false;
        FileInfo[] info = null;
        FileInfo fi = null;
        VirtualStackOfStacks stack = null;

        try {
            // loop through file list
            int count = 0;
            int counter = 0;
            for (int i = start - 1; i < list.length; i++) {

                // skip files that don't obey the filter
                if (filter != null && (!list[i].contains(filter)))
                    continue;
                if ((counter++ % increment) != 0)
                    continue;

                if (openingMethod == "tiffUseIFDsFirstFile") {

                    if (count == 0) {
                        log("Obtaining IFDs from first file and use for all.");
                        info = Opener.getTiffFileInfo(directory + list[i]);
                        fi = info[0];
                        // init stack
                        if (stack == null) {
                            // Collect image stack information
                            ColorModel cm = null;
                            if (fi.nImages > 1) {
                                nSlices = fi.nImages;
                                fi.nImages = 1;
                            } else {
                                nSlices = info.length;
                            }
                            // Initialise stack
                            stack = new VirtualStackOfStacks(new Point3D(fi.width, fi.height, nSlices), nChannels, order);
                        }
                        stack.addStack(info);
                    } else {
                        FileInfo[] infoModified = new FileInfo[info.length];
                        for (int j = 0; j < info.length; j++) {
                            infoModified[j] = (FileInfo) info[j].clone();
                            infoModified[j].fileName = list[i];
                        }
                        stack.addStack(infoModified);
                    }

                    count = stack.getNStacks();
                }

                if(n>=0) {
                    IJ.showProgress((double) (count+1) / n);
                    if ((count+1) >= n)
                        break;
                }

            }
        } catch (OutOfMemoryError e) {
            IJ.outOfMemory("FolderOpener");
            if (stack != null) stack.trim();
            //}catch(IOException e){
            //	  e.printStackTrace();
        }

        ImagePlus impFinal = null;
        if(stack!=null && stack.getSize()>0) {
            nFrames = stack.getNStacks()/nChannels;
            impFinal = makeImagePlus(stack, fi, nChannels, nFrames, nSlices, order);
            }
        IJ.showProgress(1.0);
        return(impFinal);

    }

    // todo: implement
    public void checkFiles() {
    /*
    if(checkOffsets) {
        log("# Checking offsets to first image in all files");
        long offset0 = 0;
        boolean differentOffsets = false;
        for (int i = 0; i < list.length; i++) {
            try {
                TiffDecoderExtension tde = new TiffDecoderExtension(directory, list[i]);
                fi = tde.getFirstIFD();
                if (fi == null) {
                    IJ.showMessage("Tiff file checking", "Could not open " + directory + list[i]);
                }
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
        if (differentOffsets) {
            log("");
            log("There have been different offsets!");
            log("");
        } else {
            log("All offsets are the same.");
        }
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
            //log(""+info[j].getOffset());
            if (j==0) {
                gapBetweenFirstImages = gapBetweenImages;
                sizeOfFirstImage = size;
                log("gapBetweenImages "+ j + ": " + gapBetweenFirstImages);
                log("image size "+ j + ": " + sizeOfFirstImage);
            } else if (gapBetweenImages != gapBetweenFirstImages) {
                log("gapBetweenImages " + j + ": " + gapBetweenImages);
                log("image size "+ j + ": " + size);
                gapBetweenFirstImages = gapBetweenImages;
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
*/



}

	public static ImagePlus openFromCroppedFileInfo(ImagePlus imp, FileInfo[][] infos, Point3D[] pos, Point3D radii, int tMin, int tMax) {
		Point3D size = radii.multiply(2);
        size = size.add(new Point3D(1,1,1));
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack(); // needed below to get the order (ct or tc);
        String order = vss.getOrder();
        VirtualStackOfStacks stack = new VirtualStackOfStacks(size, imp.getNChannels(), order);
		OpenerExtensions oe = new OpenerExtensions();

		FileInfo[] infoModified = new FileInfo[tMax-tMin];
		for(int it=tMin; it<=tMax; it++) {
			infoModified = oe.cropFileInfo(infos[it], 1, pos[it], radii);
			stack.addStack(infoModified);
		}
        return(makeImagePlus(stack, infoModified[0], imp.getNChannels(), stack.getNStacks()/imp.getNChannels(), infoModified.length, order));
	}

	private static ImagePlus makeImagePlus(VirtualStackOfStacks stack, FileInfo fi, int nChannels, int nFrames, int nSlices, String order) {
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		ImagePlus imp = new ImagePlus(fi.directory, stack);
		if (imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32)
			imp.getProcessor().setMinAndMax(min, max);
		imp.setFileInfo(fi); // saves FileInfo of the first image
		//if (imp.getStackSize()==1 && info1!=null)
		//   imp.setProperty("Info", info1);
        if(Globals.verbose) {
            log("# OpenStacksAsVirtualStack.makeImagePlus");
            log("nChannels: "+nChannels);
            log("nSlices: "+nSlices);
            log("nFrames: "+nFrames);
        }
        imp.setDimensions(nChannels, nSlices, nFrames);
        //imp = HyperStackConverter.toHyperStack(imp, nChannels, nSlices, nFrames, order, "Composite");
        imp.setOpenAsHyperStack(true);
		imp.resetDisplayRange();
		return(imp);
	}

	// main method for debugging
    public static void main(String[] args) {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		Class<?> clazz = OpenStacksAsVirtualStack.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring("file:".length(), url.length() - clazz.getName().length() - ".class".length());
		System.setProperty("plugins.dir", pluginsDir);

		// start ImageJ
		new ImageJ();
        //IJ.run("Memory & Threads...", "maximum=3000 parallel=4 run");

		//Globals.verbose = true;

        boolean MATLAB = false;
        boolean Mitosis_ome = true;
        boolean MATLAB_EXTERNAL = false;
		boolean OME_MIP = false;
		boolean OME = false;
        boolean OME_drift = false;

        OpenStacksAsVirtualStack ovs = null;


        if (Mitosis_ome) {
            ovs = new OpenStacksAsVirtualStack("/Users/tischi/Desktop/example-data/Mitosis-ome/", null, 1, 1, -1, 2, "tiffUseIFDsFirstFile", "tc");
            ImagePlus imp = ovs.openFromDirectory();
            imp.show();
            Registration register = new Registration(imp);
            register.showDialog();
        }
        if (MATLAB) {
            ovs = new OpenStacksAsVirtualStack("/Users/tischi/Desktop/example-data/MATLABtiff/", null, 1, 1, -1, 1, "tiffUseIFDsFirstFile", "xyzct");
            ImagePlus imp = ovs.openFromDirectory();
            imp.show();
			Registration register = new Registration(imp);
			register.showDialog();
		}

        /*
        if (MATLAB_EXTERNAL) {
            ImagePlus imp = ovs.open("/Volumes/My Passport/Res_13/", "Tiff: Use IFDs of first file for all", 1);
            imp.show();
            Registration register = new Registration(imp);
            register.showDialog();

        }
        if(OME) {
            // intialise whole data set
            ImagePlus imp = ovs.open("/Users/tischi/Desktop/example-data/T88200-OMEtiff/", "Tiff: Use IFDs of first file for all", 1);
            imp.show();

            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

            // open virtual subset
            FileInfo[][] infos = vss.getFileInfos();
            int t=0,nt=2,nz=10,ny=70,nx=70;
            int[] z = {30,29};
            int[] x = {50,55};
            int[] y = {34,34};
            //ImagePlus impVirtualCropSeries = ovs.openCropped(infos, t, nt, nz, nx, ny, z, x, y);
            //ImagePlus impVirtualCropSeries = ovs.openCropped(infos, nz, nx, ny, positions);

            //impVirtualCropSeries.show();

        }

        if (OME_drift) {
            ImagePlus imp = ovs.open("/Users/tischi/Desktop/example-data/T88200-OMEtiff-registration-test/", "Tiff: Use IFDs of first file for all", 1);
            imp.setTitle("AAAA");
            imp.show();
            //VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

            // compute drift
            Registration register = new Registration(imp);

            //Positions3D positions = register.computeDrifts3D(0,3,24,69-24,45,80,27,80, "center_of_mass", 200);
            //positions.printPositions();

            // open drift corrected as virtual stack
            //FileInfo[][] infos = vss.getFileInfos();
            //ImagePlus impVirtualCropSeries = ovs.openCropped(infos, 69-24, 70, 70, positions);
            //impVirtualCropSeries.show();

        }

		if (OME_MIP) {
			ImagePlus imp = ovs.open("/Users/tischi/Desktop/example-data/OME_MIPs/", "Tiff: Use IFDs of first file for all", 1);
            imp.show();
            Registration register = new Registration(imp);
        }
    */

    }

}



class VirtualOpenerDialog extends GenericDialog {
	int fileCount;
	boolean eightBits;
	String saveFilter = "";
	String[] list;

	public VirtualOpenerDialog(String title, String[] list) {
		super(title);
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
		((Label)theLabel).setText("Number of files:"+n);
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

