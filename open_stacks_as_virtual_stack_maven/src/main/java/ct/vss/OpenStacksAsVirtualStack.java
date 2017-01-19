package ct.vss;

import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ij.IJ.log;


/** Opens a folder of stacks as a virtual stack. */
public class OpenStacksAsVirtualStack implements PlugIn {

    private static boolean grayscale;
    private static double scale = 100.0;
    private int n, start, increment;
    private int nC, nT, nZ, nX, nY;
    private String filter;
    private String info1;
    private String[] channelFolders;
    private String[][] lists; // c, t
    private String[][][] ctzFileList;
    private String fileOrder;
    private String fileType;
    private static NonBlockingGenericDialog gd;
    public int iProgress=0, nProgress=100;
    public String h5DataSet = "Data111";

    // todo: stop loading thread upon closing of image

    public OpenStacksAsVirtualStack() {
    }

    public void run(String arg) {
        showDialog();
    }

    public void showDialog() {
        StackStreamToolsGUI sstGUI = new StackStreamToolsGUI();
        sstGUI.showDialog();
    }

    boolean showOpenDialog(String[] list) {
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
        filter = gd.getNextString();
        nC = (int) gd.getNextNumber();
        fileOrder = gd.getNextChoice();
        return true;
    }

    public ImagePlus openFromInfoFile(String directory, String fileName){

        File f = new File(directory+fileName);

        if(f.exists() && !f.isDirectory()) {

            log("Loading: "+directory+fileName);
            FileInfoSer[][][] infos = readFileInfosSer(directory+fileName);

            nC = infos.length;
            nT = infos[0].length;
            nZ = infos[0][0].length;

            if(Globals.verbose) {
                log("nC: " + infos.length);
                log("nT: " + infos[0].length);
                log("nz: " + infos[0][0].length);
            }

            // init the VSS
            VirtualStackOfStacks stack = new VirtualStackOfStacks(directory, infos);
            ImagePlus imp = makeImagePlus(stack);
            return(imp);

        } else {
            return (null);
        }
    }

    public ImagePlus openFromDirectory(String directory, String filter) {
        int iChannelFolder = 0;
        int t=0,z=0,c=0;
        ImagePlus imp = null;
        Matcher matcherZ, matcherT;
        FileInfo[] info;
        FileInfo fi0;

        // todo: depending on the fileOrder do different things
        // todo: add the filter to the getFilesInFolder function

        //
        // Check for sub-folders
        //
        channelFolders = getFoldersInFolder(directory);

        if (channelFolders == null) {
            lists = new String[1][];
            lists[0] = getFilesInFolder(directory);
            channelFolders = new String[] {""}; // one empty subdirectory
        } else {
            lists = new String[channelFolders.length][];
            for (int i = 0; i < channelFolders.length; i++){
                lists[i] = getFilesInFolder(directory + channelFolders[i]);
                if (lists[i] == null) {
                    log("No files found in folder "+channelFolders[i]);
                    return (null);
                }
            }
        }

        // todo consistency check the list lengths

        //
        // generate a nC,nT,nZ fileList
        //

        if(lists[0][0].endsWith(".h5")) {

            fileType = "h5";

            if (channelFolders == null) nC = 1;
            else nC = channelFolders.length;
            nT = lists[0].length;

            IHDF5Reader reader = HDF5Factory.openForReading(directory + channelFolders[c] + "/" + lists[0][0]);
            HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation("/" + h5DataSet);

            nZ = (int) dsInfo.getDimensions()[0];
            nY = (int) dsInfo.getDimensions()[1];
            nX = (int) dsInfo.getDimensions()[2];

            // sort into the final file list
            ctzFileList = new String[nC][nT][nZ];

            for (iChannelFolder = 0; iChannelFolder < channelFolders.length; iChannelFolder++) {
                for (t = 0; t<lists[iChannelFolder].length; t++) {
                    c = iChannelFolder;
                    z = 0;
                    ctzFileList[c][t][z] = lists[iChannelFolder][t];
                }
            }


        }  else if(lists[0][0].endsWith(".tif") && lists[0][0].contains("_Target--")) {

            fileType = "leica single tif";

            // todo: add C
            Pattern patternZ = Pattern.compile(".*--Z(.*).tif.*");
            Pattern patternT = Pattern.compile(".*--t(.*)--.*");

            // check how many C, T and Z there are
            for (iChannelFolder = 0; iChannelFolder < channelFolders.length; iChannelFolder++) {
                for (String fileName : lists[iChannelFolder]) {
                    matcherZ = patternZ.matcher(fileName);
                    matcherT = patternT.matcher(fileName);
                    if (matcherZ.matches()) {
                        z = Integer.parseInt(matcherZ.group(1).toString());
                        if (z >= nZ) nZ = z + 1;
                    }
                    if (matcherT.matches()) {
                        t = Integer.parseInt(matcherT.group(1).toString());
                        if (t >= nT) nT = t + 1;
                    }
                }
            }
            nC = 1;

            // sort into the final file list
            ctzFileList = new String[nC][nT][nZ];

            for (iChannelFolder = 0; iChannelFolder < channelFolders.length; iChannelFolder++) {
                for (String fileName : lists[iChannelFolder]) {
                    matcherZ = patternZ.matcher(fileName);
                    matcherT = patternT.matcher(fileName);
                    if (matcherZ.matches()) {
                        z = Integer.parseInt(matcherZ.group(1).toString());
                        if (z >= nZ) nZ = z + 1;
                    }
                    if (matcherT.matches()) {
                        t = Integer.parseInt(matcherT.group(1).toString());
                        if (t >= nT) nT = t + 1;
                    }
                    c = 0;
                    ctzFileList[c][t][z] = fileName;
                }
            }

            try {
                FastTiffDecoder ftd = new FastTiffDecoder(directory + channelFolders[0], ctzFileList[0][0][0]);
                info = ftd.getTiffInfo();
            } catch(Exception e) {
                info = null;
                IJ.showMessage("Error: "+e.toString());
            }

            fi0 = info[0];
            nX = fi0.width;
            nY = fi0.height;

        } else if(lists[0][0].endsWith(".tif")) {

            fileType = "tif stacks";

            if (channelFolders == null) nC = 1;
            else nC = channelFolders.length;
            nT = lists[0].length;

            try {
                FastTiffDecoder ftd = new FastTiffDecoder(directory + channelFolders[0], lists[0][0]);
                info = ftd.getTiffInfo();
            } catch(Exception e) {
                info = null;
                IJ.showMessage("Error: "+e.toString());
            }

            fi0 = info[0];
            if (fi0.nImages > 1) {
                nZ = fi0.nImages;
                fi0.nImages = 1;
            } else {
                nZ = info.length;
            }
            nX = fi0.width;
            nY = fi0.height;

            // sort into the final file list
            ctzFileList = new String[nC][nT][nZ];

            for (iChannelFolder = 0; iChannelFolder < channelFolders.length; iChannelFolder++) {
                for (t = 0; t<lists[iChannelFolder].length; t++) {
                    c = iChannelFolder;
                    z = 0;
                    ctzFileList[c][t][z] = lists[iChannelFolder][t];
                }
            }

        } else {

            IJ.showMessage("Unsupported file type: "+lists[0][0]);
            return(null);

        }

        nProgress = nT*nC; iProgress = 0;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                updateStatus("Analyzed file");
            }
        });
        thread.start();

        // init the VSS
        VirtualStackOfStacks stack = new VirtualStackOfStacks(directory, channelFolders, ctzFileList, nC, nT, nX, nY, nZ, fileType, h5DataSet);

        // set the file information for each c, t, z
        try {

            for (t = 0; t < nT; t++) {

                for (c = 0; c < nC; c++) {

                    stack.setStackFromFile(t, c);

                    iProgress = t+c*nT+1;

                } // c-loop

                // show window at 1st time-point
                if (t == 0) {

                    if (stack != null && stack.getSize() > 0) {
                        imp = makeImagePlus(stack);
                        imp.show();
                    } else {
                        IJ.showMessage("Something went wrong loading the first image stack!");
                        return(null);
                    }
                    imp.show();

                }

            } // t-loop

        } catch(Exception e) {
            IJ.showMessage("Error: "+e.toString());
        }

        iProgress = nProgress;

        return(imp);

    }

    public void saveAsTiffStacks(ImagePlus imp, String path) {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileSaver fs;

        for (int c = 0; c < imp.getNChannels(); c++) {

            for (int t = 0; t < imp.getNFrames(); t++) {

                ImagePlus impCT = vss.getFullFrame(t, c, new Point3D(1,1,1));
                fs = new FileSaver(impCT);
                String sC = String.format("%1$02d",c);
                String sT = String.format("%1$05d",t);
                String pathCT = path + "--C"+sC+"--T"+sT+".tif";
                fs.saveAsTiffStack(pathCT);
                nProgress = nT*nC;
                iProgress = t+c*nT+1;

            }
        }

        iProgress = nProgress;

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
        //log("# getFilesInFolder: " + directory);
        // todo: can getting the file-list be faster?
        String[] list = new File(directory).list();
        if (list == null || list.length == 0)
            return null;
        list = this.sortFileList(list);
        //log("Number of files: " + list.length);
        //log("Sorted files:");
        for(String item : list) {
            //log("" + item);
        }
        if (list == null) return null;
        else return (list);
    }

    String[] getFoldersInFolder(String directory) {
        //log("# getFoldersInFolder: " + directory);
        String[] list = new File(directory).list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });
        if (list == null || list.length == 0)
            return null;
        list = this.sortFileList(list);
        for(String item : list) {
            //log("" + item);
        }
        return (list);

    }

    public boolean updateStatus(String message) {
        while(iProgress<nProgress) {
            IJ.wait(50);
            IJ.showStatus(message+" " + iProgress + "/" + nProgress);
        }
        IJ.showStatus(""+nProgress+"/"+nProgress);
        return true;
    }

    public boolean writeFileInfosSer(FileInfoSer[][][] infos, String path) {

        try{
            FileOutputStream fout = new FileOutputStream(path, true);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(infos);
            oos.flush();
            oos.close();
            fout.close();
            log("Wrote: " + path);
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            log("Could not write: " + path);
            return false;
        }

    }

    public FileInfoSer[][][] readFileInfosSer(String path) {
        try {
            FileInputStream fis = new FileInputStream(path);
            ObjectInputStream ois = new ObjectInputStream(fis);
            FileInfoSer infos[][][] = (FileInfoSer[][][]) ois.readObject();
            ois.close();
            fis.close();
            return(infos);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

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

    // opens a new (info-based) view on the data
    // todo: call the OffsetSize method from this
    public static ImagePlus openCroppedCenterRadiusFromInfos(ImagePlus imp, FileInfoSer[][][] infos, Point3D[] pc, Point3D pr, int tMin, int tMax) {
		int nT = tMax-tMin+1;

        Point3D[] po = new Point3D[nT];
        Point3D ps = pr.multiply(2).add(1, 1, 1);
        Point3D pcCurate;

        for(int t=tMin; t<=tMax; t++) {
            pcCurate = OpenStacksAsVirtualStack.curatePosition(imp, pc[t-tMin], pr);
            po[t-tMin] = pcCurate.subtract(pr);
        }

        return(openCroppedOffsetSizeFromInfos(imp, infos, po, ps, tMin, tMax));

    }

    public static Point3D curatePosition(ImagePlus imp, Point3D p, Point3D pr) {
        boolean shifted = false;

        // round the values
        int x = (int) (p.getX()+0.5);
        int y = (int) (p.getY()+0.5);
        int z = (int) (p.getZ()+0.5);
        int rx = (int) (pr.getX()+0.5);
        int ry = (int) (pr.getY()+0.5);
        int rz = (int) (pr.getZ()+0.5);

        // make sure that the ROI stays within the image bounds
        if (x-rx < 0) {x = rx; shifted = true;}
        if (y-ry < 0) {y = ry; shifted = true;}
        if (z-rz < 0) {z = rz; shifted = true;}

        if (x+rx > imp.getWidth()-1) {x = imp.getWidth()-rx-1; shifted = true;}
        if (y+ry > imp.getHeight()-1) {y = imp.getHeight()-ry-1; shifted = true;}
        if (z+rz > imp.getNSlices()-1) {z = imp.getNSlices()-rz-1; shifted = true;}

        // check if it is ok now, otherwise the chose radius is simply too large
        if (x-rx < 0)  {
            IJ.showMessage("x_radius*margin_factor is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        if (y-ry < 0){
            IJ.showMessage("y_radius*margin_factor is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        if (z-rz < 0) {
            IJ.showMessage("z_radius*margin_factor is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        if(shifted) {
            log("!! image: "+imp.getTitle()+": cropping region needed to be shifted to stay within image bounds.");
        }
        return(new Point3D(x,y,z));
    }

    // opens a new (info-based) view on the data
    public static ImagePlus openCroppedOffsetSizeFromInfos(ImagePlus imp, FileInfoSer[][][] infos, Point3D[] po, Point3D ps, int tMin, int tMax) {
        int nC = infos.length;
        int nT = tMax-tMin+1;
        int nZ = infos[0][0].length;

        FileInfoSer[][][] croppedInfos = new FileInfoSer[nC][nT][nZ];

        if(Globals.verbose){
            log("# OpenStacksAsVirtualStack.openCroppedFromInfos");
            log("tMin: "+tMin);
            log("tMax: "+tMax);
        }

        for(int c=0; c<nC; c++) {

            for(int t=tMin; t<=tMax; t++) {

                for(int z=0; z<nZ; z++) {

                    croppedInfos[c][t-tMin][z] = new FileInfoSer(infos[c][t][z]);
                    if(croppedInfos[c][t-tMin][z].isCropped) {
                        croppedInfos[c][t-tMin][z].setCropOffset(po[t].add(croppedInfos[c][t - tMin][z].getCropOffset()));
                    } else {
                        croppedInfos[c][t - tMin][z].isCropped = true;
                        croppedInfos[c][t - tMin][z].setCropOffset(po[t-tMin]);
                    }
                    croppedInfos[c][t-tMin][z].setCropSize(ps);
                    //log("c "+c);
                    //log("t "+t);
                    //log("z "+z);
                    //log("offset "+croppedInfos[c][t-tMin][z].pCropOffset.toString());

                }

            }

        }

        VirtualStackOfStacks parentStack = (VirtualStackOfStacks) imp.getStack();
        VirtualStackOfStacks stack = new VirtualStackOfStacks(parentStack.getDirectory(), croppedInfos);
        return(makeImagePlus(stack));

    }

    private static ImagePlus makeImagePlus(VirtualStackOfStacks stack) {
        int nC=stack.nC;
        int nZ=stack.nZ;
        int nT=stack.nT;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        FileInfoSer[][][] infos = stack.getFileInfosSer();
        FileInfoSer fi = infos[0][0][0];

        ImagePlus imp = new ImagePlus(fi.directory, stack);
        imp.setTitle(fi.directory);

        if (imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32)
			imp.getProcessor().setMinAndMax(min, max);
		imp.setFileInfo(fi.getFileInfo()); // saves FileInfo of the first image

        if(Globals.verbose) {
            log("# OpenStacksAsVirtualStack.makeImagePlus");
            log("nC: "+nC);
            log("nZ: "+nZ);
            log("nT: "+nT);
        }

        imp.setDimensions(nC, nZ, nT);
        imp.setOpenAsHyperStack(true);
        //if(nC>1) imp.setDisplayMode(IJ.COMPOSITE);
        imp.setPosition(1, (int) nZ/2, 1);
		imp.resetDisplayRange();
		return(imp);
	}

    public static ImagePlus crop(ImagePlus imp) {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if (vss == null) {
            IJ.showMessage("Wrong image type." +
                    " This method is only implemented for streamed (virtual) image stacks.");
            return null;
        }
        FileInfoSer[][][] infos = vss.getFileInfosSer();
        if(infos[0][0][0].compression==6) {
            IJ.showMessage(
                    "Cropping functionality is currently not supported for ZIP compressed data."
                    );
            return null;
        }
        Roi roi = imp.getRoi();
        if (roi != null && roi.isArea()) {

            int tMin = 0;
            int tMax = vss.nT - 1;
            int zMin = 0;

            Point3D[] po = new Point3D[vss.nT];
            for (int t = 0; t < vss.nT; t++) {
                po[t] = new Point3D(roi.getBounds().getX(), roi.getBounds().getY(), zMin);
            }
            Point3D ps = new Point3D(roi.getBounds().getWidth(), roi.getBounds().getHeight(), vss.nZ);

            ImagePlus impCropped = openCroppedOffsetSizeFromInfos(imp, vss.getFileInfosSer(), po, ps, tMin, tMax);
            impCropped.setTitle(imp.getTitle()+"-crop");
            return impCropped;

        } else {
            IJ.showMessage("Please put a rectangular ROI on the image.");
            return null;
        }
    }

    public ImagePlus duplicateToRAM(ImagePlus imp) {

        VirtualStackOfStacks stack = (VirtualStackOfStacks) imp.getStack();
        if (stack == null) {
            IJ.showMessage("Wrong image type.");
        }

        // crop if wanted
        Roi roi = imp.getRoi();
        if (roi != null && roi.isArea()) {
            imp = crop(imp);
            stack = (VirtualStackOfStacks) imp.getStack();
        }

        nProgress = stack.nSlices;

        ImageStack stack2 = null;
        int n = stack.nSlices;
        for (int i=1; i<=n; i++) {
            iProgress = i;
            ImageProcessor ip2 = stack.getProcessor(i);
            if (stack2==null)
                stack2 = new ImageStack(ip2.getWidth(), ip2.getHeight(), imp.getProcessor().getColorModel());
            stack2.addSlice(stack.getSliceLabel(i), ip2);
        }
        ImagePlus imp2 = imp.createImagePlus();
        imp2.setStack("DUP_"+imp.getTitle(), stack2);
        String info = (String)imp.getProperty("Info");
        if (info!=null)
            imp2.setProperty("Info", info);
        int[] dim = imp.getDimensions();
        imp2.setDimensions(dim[2], dim[3], dim[4]);
        if (imp.isHyperStack())
            imp2.setOpenAsHyperStack(true);
        return(imp2);

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




        // todo: remove the initialisation from the constructor and put it into openFromDirectory

        /*if(interactive) {
            ovs = new OpenStacksAsVirtualStack();
            ovs.run("");
            Registration register = new Registration();
            register.run("");
        }*/


        //final String directory = "/Users/tischi/Desktop/Gustavo_Crop/";
        //final String directory = "/Users/tischi/Desktop/example-data/iSPIM tif stacks/";
        final String directory = "/Users/tischi/Desktop/example-data/Leica single tif files/";

        //final String directory = "/Users/tischi/Desktop/example-data/luxendo/";

        //final String directory = "/Users/tischi/Desktop/example-data/compressed/";
        //final String directory = "/Volumes/My Passport/Res_13/";
        //String directory = "/Users/tischi/Desktop/example-data/MATLABtiff/";
        //String filter = null;

        //String openingMethod = "tiffLoadAllIFDs";

        //OpenHDF5test oh5 = new OpenHDF5test();
        //oh5.openOneFileAsImp("/Users/tischi/Desktop/example-data/luxendo/ch0/fused_t00000_c0.h5");
        //Globals.verbose = true;
        final OpenStacksAsVirtualStack ovs = new OpenStacksAsVirtualStack();
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                ImagePlus imp = ovs.openFromDirectory(directory, null);
            }
        });
        t1.start();
        IJ.wait(1000);
        ovs.showDialog();
        Registration register = new Registration(IJ.getImage());
        register.run("");


        /*
        if (Mitosis_ome) {
            ovs = new OpenStacksAsVirtualStack("/Users/tischi/Desktop/example-data/Mitosis-ome/", null, 1, 1, -1, 2, "tiffUseIFDsFirstFile", "tc");
            ImagePlus imp = ovs.openFromDirectory();
            imp.show();
            Registration register = new Registration(imp);
            register.showDialog();
        }

        if (MATLAB) {
            ovs = new OpenStacksAsVirtualStack("/Users/tischi/Desktop/example-data/MATLABtiff/", null, 1, 1, -1, 1, "tiffUseIFDsFirstFile", "tc");
            ImagePlus imp = ovs.openFromDirectory();
            imp.show();
            Registration register = new Registration(imp);
            register.showDialog();
		}
        */

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


class StackStreamToolsGUI extends JPanel implements ActionListener, ItemListener {

    String[] actions = {"Stream from folder",
            "Stream from info file",
            "Save as info file",
            "Save as tiff stacks",
            "Crop as new stream",
            "Duplicate to RAM",
            "Report issue"};

    JCheckBox cbLog = new JCheckBox("Verbose logging");
    JTextField tfH5DataSet = new JTextField("Data111", 10);
    JFileChooser fc;

    public void StackStreamToolsGUI() {
    }

    public void showDialog() {
        JFrame frame = new JFrame("Data Streaming Tools");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container c = frame.getContentPane();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));

        String[] toolTipTexts = getToolTipFile("DataStreamingHelp.html");

        // Buttons
        JButton[] buttons = new JButton[actions.length];
        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new JButton(actions[i]);
            buttons[i].setActionCommand(actions[i]);
            buttons[i].addActionListener(this);
            buttons[i].setToolTipText(toolTipTexts[i]);
        }

        // Textfields
        JLabel labelH5DataSet = new JLabel("Hdf5 data set name: ");
        labelH5DataSet.setLabelFor(tfH5DataSet);

        // Checkboxes
        cbLog.setSelected(false);
        cbLog.addItemListener(this);

        int i = 0, j = 0;

        ArrayList<JPanel> panels = new ArrayList<JPanel>();

        panels.add(new JPanel());
        panels.get(j).add(buttons[i++]);
        panels.get(j).add(buttons[i++]);
        c.add(panels.get(j++));

        panels.add(new JPanel());
        panels.get(j).add(buttons[i++]);
        panels.get(j).add(buttons[i++]);
        c.add(panels.get(j++));

        panels.add(new JPanel());
        panels.get(j).add(buttons[i++]);
        panels.get(j).add(buttons[i++]);
        c.add(panels.get(j++));

        panels.add(new JPanel());
        panels.get(j).add(labelH5DataSet);
        panels.get(j).add(tfH5DataSet);
        c.add(panels.get(j++));

        panels.add(new JPanel());
        panels.get(j).add(cbLog);
        c.add(panels.get(j++));

        panels.add(new JPanel());
        panels.get(j).add(buttons[i++]);
        c.add(panels.get(j++));

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

    }

    public void itemStateChanged(ItemEvent e) {
        Object source = e.getItemSelectable();

        if (source == cbLog) {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                Globals.verbose = false;
            } else {
                Globals.verbose = true;
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        int i = 0;
        final OpenStacksAsVirtualStack osv = new OpenStacksAsVirtualStack();

        osv.h5DataSet = tfH5DataSet.getText();

        if (e.getActionCommand().equals(actions[i++])) {

            // Open from folder
            final String directory = IJ.getDirectory("Select a Directory");
            if (directory == null)
                return;

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    osv.openFromDirectory(directory, null);
                }
            });
            t1.start();

        } else if (e.getActionCommand().equals(actions[i++])) {

            // Open from file
            String filePath = IJ.getFilePath("Select *.ser file");
            if (filePath == null)
                return;
            File file = new File(filePath);
            ImagePlus imp = osv.openFromInfoFile(file.getParent() + "/", file.getName());
            imp.show();

        } else if (e.getActionCommand().equals(actions[i++])) {

            // "Save as info file"
            ImagePlus imp = IJ.getImage();
            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
            if(vss==null) {
                IJ.showMessage("This is only implemented for a VirtualStacks of stacks");
                return;
            }
            fc = new JFileChooser(vss.getDirectory());
            int returnVal = fc.showSaveDialog(StackStreamToolsGUI.this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                int numberOfUnparsedFiles = vss.numberOfUnparsedFiles();
                if(numberOfUnparsedFiles > 0) {
                    IJ.showMessage("There are still "+numberOfUnparsedFiles+
                            " files in the folder that have not been parsed yet.\n" +
                            "Please try again later (check ImageJ's status bar).");
                    return;
                }

                log("Saving: " + file.getAbsolutePath());
                osv.writeFileInfosSer(vss.getFileInfosSer(), file.getAbsolutePath());
            } else {
                log("Save command cancelled by user.");
            }
        } else if (e.getActionCommand().equals(actions[i++])) {

            // "Save as tiff stacks"
            //    IJ.showMessage("Not yet implemented.");
            ImagePlus imp = IJ.getImage();
            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
            if(vss==null) {
                IJ.showMessage("This is only implemented for a VirtualStacks of stacks");
                return;
            }
            fc = new JFileChooser(vss.getDirectory());
            int returnVal = fc.showSaveDialog(StackStreamToolsGUI.this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                final File file = fc.getSelectedFile();
                log("Saving to: " + file.getAbsolutePath() + "...");
                // do the job
                Thread t1 = new Thread(new Runnable() {
                    public void run() {
                        osv.saveAsTiffStacks(IJ.getImage(), file.getAbsolutePath());
                    }
                }); t1.start();
                // update progress status
                Thread t2 = new Thread(new Runnable() {
                    public void run() {
                        osv.iProgress=0; osv.nProgress=1;
                        osv.updateStatus("Saving file");
                    }
                }); t2.start();

            } else {
                log("Save command cancelled by user.");
                return;
            }


            //} else if (e.getActionCommand().equals(actions[i++])) {
            // "Save as h5 stacks"
        //    IJ.showMessage("Not yet implemented.");
        }  else if (e.getActionCommand().equals(actions[i++])) {

            //
            // Crop As New Stream
            //


            ImagePlus imp2 = osv.crop(IJ.getImage());
            if (imp2 != null)
                imp2.show();

        } else if (e.getActionCommand().equals(actions[i++])) {
            //
            // duplicate to RAM
            //

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    ImagePlus imp2 = osv.duplicateToRAM(IJ.getImage());
                    if (imp2 != null)
                        imp2.show();

                }
            });
            t1.start();

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    osv.updateStatus("Duplicated slice");
                }
            });
            t2.start();

        } else if (e.getActionCommand().equals(actions[i++])) {
                //
                // Report issue
                //
                String url = "https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues";
                if (Desktop.isDesktopSupported()) {
                    try {
                        final URI uri = new URI(url);
                        Desktop.getDesktop().browse(uri);
                    } catch (URISyntaxException uriEx) {
                        IJ.showMessage(uriEx.toString());
                    } catch (IOException ioEx) {
                        IJ.showMessage(ioEx.toString());
                    }
                } else {
                    IJ.showMessage("Could not open browser, please report issue here: \n" +
                            "https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues");

                }
            }

        }

    private String[] getToolTipFile(String fileName) {

        ArrayList<String> toolTipTexts = new ArrayList<String>();

        //Get file from resources folder
        //ClassLoader classLoader = getClass().getClassLoader();
        //File file = new File(classLoader.getResource(fileName).getFile());

        //try {

        InputStream in = getClass().getResourceAsStream("/"+fileName);
        BufferedReader input = new BufferedReader(new InputStreamReader(in));
        Scanner scanner = new Scanner(input);

        StringBuilder sb = new StringBuilder("");


        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if(line.equals("###")) {
                toolTipTexts.add(sb.toString());
                sb = new StringBuilder("");
            } else {
                sb.append(line);
            }

        }

        scanner.close();

        //} catch (IOException e) {

        //    log("Did not find tool tip file 2.");
        //    e.printStackTrace();

        //}

        return(toolTipTexts.toArray(new String[0]));
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
		//((Label)theLabel).setText("Number of files:"+n);
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



                /*
                if (openingMethod == "tiffUseIFDsFirstFile") {

                    if (count == 0) {
                        log("Obtaining IFDs from first file and use for all.");
                        info = Opener.getTiffFileInfo(directory + list[i]);
                        fi = info[0];
                        // Collect image stack information
                        ColorModel cm = null;
                        if (fi.nImages > 1) {
                            nZ = fi.nImages;
                            fi.nImages = 1;
                        } else {
                            nZ = info.length;
                        }
                        // Initialise stack
                        stack = new VirtualStackOfStacks(new Point3D(fi.width, fi.height, nZ), nC, nT);
                        stack.addStack(info);
                    } else { // construct IFDs from first file
                        FileInfo[] infoModified = new FileInfo[info.length];
                        for (int j = 0; j < info.length; j++) {
                            infoModified[j] = (FileInfo) info[j].clone();
                            infoModified[j].fileName = list[i];
                        }
                        stack.addStack(infoModified);
                    }

                    count = stack.getNStacks();
                }*/

