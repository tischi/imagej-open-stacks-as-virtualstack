package ct.vss;

import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import javafx.geometry.Point3D;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import static ij.IJ.log;


/** Opens a folder of stacks as a virtual stack. */
public class OpenStacksAsVirtualStack implements PlugIn {

    private static boolean grayscale;
    private static double scale = 100.0;
    private int n, start, increment;
    private int nC, nT, nZ, nX, nY;
    private String filter;
    private String info1;
    private String directory;
    private String[] channelFolders;
    private String[][] lists; // c, t
    private String fileOrder;
    private String fileType;
    private static NonBlockingGenericDialog gd;

    public OpenStacksAsVirtualStack() {
    }

    public void run(String arg) {
        showDialog();
    }

    public void showDialog() {

        gd = new NonBlockingGenericDialog("Stack Streaming Tools");

        // set iconImage
        // todo: make a panel with logo and the other stuff next to each other
        //ClassLoader classLoader = getClass().getClassLoader();
        //ImagePlus impIcon = IJ.openImage(classLoader.getResource("logo01-61x61.jpg").getFile());
        //if(impIcon!=null) gd.addImage(impIcon);

        //gd.addMessage("");
        //gd.addMessage("Version: "+Globals.version);
        //gd.addMessage("Contact: tischer@embl.de");

        Button[] bts = new Button[4];

        /*
        if(new File(directory+"ovs.ser").exists()) {
            log("Found ovs file.");
            imp = openFromInfoFile(directory,"ovs.ser");
        } else {
            imp = openFromDirectory(directory, null);
        }*/

        bts[0] = new Button("Open folder");
        bts[0].addActionListener(new ActionListener() {
                                     @Override
                                     public void actionPerformed(ActionEvent e) {
                 if (updateGuiVariables()) {
                     directory = IJ.getDirectory("Select a Directory");
                     if (directory == null)
                         return;
                     Thread t1 = new Thread(new Runnable() {
                         public void run() {
                                 try {
                                     ImagePlus imp = openFromDirectory(directory, null);
                                     imp.show();
                                 } finally {
                                     //...
                                 }
                         }
                     });
                     t1.start();
                 }
             }
         });
        bts[1] = new Button("Open file");
        bts[1].addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updateGuiVariables()) {
                    String filePath = IJ.getFilePath("Select *.ser file");
                    if (filePath == null)
                        return;
                    File file = new File(filePath);
                    ImagePlus imp = openFromInfoFile(file.getParent()+"/", file.getName());
                    imp.show();
                }
            }
        });

        bts[2] = new Button("Crop");
        bts[2].addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updateGuiVariables()) {
                    ImagePlus impCropped = crop(IJ.getImage());
                    if(impCropped!=null)
                        impCropped.show();
                }
            }
        });
        bts[3] = new Button("Duplicate to RAM");
        bts[3].addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updateGuiVariables()) {
                    duplicateToRAM(IJ.getImage());
                }
                }
        });


        final Panel buttons = new Panel();
        GridBagLayout bgbl = new GridBagLayout();
        buttons.setLayout(bgbl);
        GridBagConstraints bgbc = new GridBagConstraints();
        bgbc.anchor = GridBagConstraints.EAST;

        for(Button bt : bts) {
            bgbc.insets = new Insets(0, 0, 0, 0);
            bgbl.setConstraints(bt, bgbc);
            buttons.add(bt);
        }
        gd.addPanel(buttons,GridBagConstraints.EAST,new Insets(5,5,5,5));
        //bgbl = (GridBagLayout)gd.getLayout();
        //bgbc = bgbl.getConstraints(buttons); bgbc.gridx = 0;
        //bgbl.setConstraints(buttons,bgbc);


        // gd location
        /*
        ImagePlus imp = IJ.getImage();
        int gdX = (int) imp.getWindow().getLocationOnScreen().getX() + imp.getWindow().getWidth() + 10;
        int gdY = (int) imp.getWindow().getLocationOnScreen().getY() + 30;
        gd.centerDialog(false);
        gd.setLocation(gdX, gdY);
        gd.getHeight();
        */

        // add logging checkbox
        final Checkbox cbLogging = new Checkbox("Verbose logging", false);
        cbLogging.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                Globals.verbose = cbLogging.getState();
            }
        });

        final Panel panel0 = new Panel();
        GridBagLayout gbl0 = new GridBagLayout();
        GridBagConstraints gbc0 = new GridBagConstraints();
        panel0.setLayout(gbl0);
        gbc0.anchor = GridBagConstraints.EAST;

        gbc0.insets = new Insets(0,0,0,5);
        gbl0.setConstraints(cbLogging, gbc0);
        panel0.add(cbLogging);

        gd.addPanel(panel0,GridBagConstraints.EAST,new Insets(5,5,5,5));

        gd.addHelp("https://github.com/tischi/imagej-open-stacks-as-virtualstack/blob/master/README.md");

        gd.showDialog();

    }

    public boolean updateGuiVariables() {
        /*
        Roi roi = imp.getRoi();

        if ((roi != null) && (roi.getPolygon().npoints == 1)) {

            // get values
            int x = roi.getPolygon().xpoints[0];
            int y = roi.getPolygon().ypoints[0];
            int z = imp.getZ() - 1;
            gui_t = imp.getT() - 1;

            int iTxt = 0, iChoice = 0;
            int rx = new Integer(getTextFieldTxt(gd, iTxt++));
            int ry = new Integer(getTextFieldTxt(gd, iTxt++));
            int rz = new Integer(getTextFieldTxt(gd, iTxt++));
            gui_dz = new Integer(getTextFieldTxt(gd, iTxt++));
            gui_dt = new Integer(getTextFieldTxt(gd, iTxt++));
            double marginFactor = new Double(getTextFieldTxt(gd, iTxt++));
            gui_bg = new Integer(getTextFieldTxt(gd, iTxt++));
            gui_iterations = new Integer(getTextFieldTxt(gd, iTxt++));
            gui_tMax = (new Integer(getTextFieldTxt(gd, iTxt++))) - 1;
            iTxt++; // frame slider
            gui_c = (new Integer(getTextFieldTxt(gd, iTxt++))) - 1;
            Choice centeringMethod = (Choice) gd.getChoices().get(iChoice++);
            gui_centeringMethod = centeringMethod.getSelectedItem();

            //double marginFactorCrop = new Double(getTextFieldTxt(gd, iTxt++));

            gui_pCenterOfMassRadii = new Point3D(rx, ry, rz);
            gui_pStackRadii = gui_pCenterOfMassRadii.multiply(marginFactor);
            gui_pCropRadii = gui_pCenterOfMassRadii;
            gui_pStackCenter = new Point3D(x, y, z);

            return(true);

        } else {

            IJ.showMessage("Please use IJ's 'Point selection tool' to mark an object in your image.");
            return(false);

        }
        */
        return(true);
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

            log("Loading ovs info file...");
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
            ImagePlus imp = makeImagePlus(stack, infos[0][0][0]);
            return(imp);

        } else {
            return (null);
        }
    }

    public ImagePlus openFromDirectory(String directory, String filter) {

        log("# openFromDirectory");

        this.directory = directory;
        this.filter = filter;

        String dataSet = "Data111";

        // todo: depending on the fileOrder do different things
        // todo: add the filter to the getFilesInFolder function
        this.channelFolders = getFoldersInFolder(directory);

        if (channelFolders == null) {
            log("No channel sub-folders found.");
            return(null);
        }
        this.nC = channelFolders.length;
        this.lists = new String[nC][];
        for (int i = 0; i < nC; i++) {
            lists[i] = getFilesInFolder(directory + channelFolders[i]);
            if (lists[i] == null) {
                log("No files found!");
                return(null);
            }
        }
        // todo consistency check the list lengths
        this.nT = lists[0].length;

        // figure out the filetype
        if(lists[0][0].endsWith(".h5")) {
            fileType = "h5";
            log("File type: "+fileType);
        } else if(lists[0][0].endsWith(".tif")) {
            fileType = "tif";
            log("File type: "+fileType);
        } else {
            IJ.showMessage("Unsupported file type: "+lists[0][0]);
            return(null);
        }

        FileInfo[] info = null;
        FileInfo fi = null;
        FileInfoSer[] infoSer = null;
        String path = "";
        VirtualStackOfStacks stack = null;

        log("Obtaining information from all files...");
        // loop through filtered list and add file-info
        try {

            for (int c = 0; c < nC; c++) {

                for (int t = 0; t < nT; t++) {

                    String ctPath = directory + channelFolders[c] + "/" + lists[c][t];

                    if (fileType == "tif") {

                        long startTime = System.currentTimeMillis();
                        FastTiffDecoder ftd = new FastTiffDecoder(directory + channelFolders[c], lists[c][t]);
                        info = ftd.getTiffInfo();

                        // first file
                        if (t == 0 && c == 0) {

                            log("IFD reading time [ms]: "+(System.currentTimeMillis()-startTime));
                            log("c:" + c + "; t:" + t + "; file:" + lists[c][t]);
                            log("info.length " + info.length);
                            log("info[0].compression " + info[0].compression);
                            log("info[0].stripLengths.length " + info[0].stripLengths.length);
                            log("info[0].rowsPerStrip " + info[0].rowsPerStrip);

                            fi = info[0];
                            if (fi.nImages > 1) {
                                nZ = fi.nImages;
                                fi.nImages = 1;
                            } else {
                                nZ = info.length;
                            }
                            nX = fi.width;
                            nY = fi.height;

                            // init the VSS
                            stack = new VirtualStackOfStacks(directory, new Point3D(nX, nY, nZ), nC, nT, fileType);

                        } else {

                            if (Globals.verbose) {
                                log("IFD reading time [ms]: "+(System.currentTimeMillis()-startTime));
                                log("c:" + c + "; t:" + t + "; file:" + lists[c][t]);
                                log("info.length " + info.length);
                                log("info[0].compression " + info[0].compression);
                                log("info[0].stripLengths.length " + info[0].stripLengths.length);
                                log("info[0].rowsPerStrip " + info[0].rowsPerStrip);
                            }

                        }

                        // convert ij.io.FileInfo[] to FileInfoSer[]
                        infoSer = new FileInfoSer[nZ];
                        for (int i = 0; i < nZ; i++) {
                            infoSer[i] = new FileInfoSer((FileInfo) info[i].clone());
                            infoSer[i].directory = channelFolders[c] + "/"; // relative path to main directory
                            infoSer[i].fileTypeString = fileType;
                        }

                        stack.setStack(infoSer, t, c);

                    }

                    if (fileType == "h5") {

                        // first file
                        if (t == 0 && c == 0) {
                            IHDF5Reader reader = HDF5Factory.openForReading(ctPath);
                            HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation("/"+dataSet);

                            nZ = (int)dsInfo.getDimensions()[0];
                            nY = (int)dsInfo.getDimensions()[1];
                            nX = (int)dsInfo.getDimensions()[2];

                            // init the VSS
                            stack = new VirtualStackOfStacks(directory, new Point3D(nX, nY, nZ), nC, nT, fileType);
                        }

                        if (Globals.verbose) {
                            log("nX " + nX);
                            log("nY " + nY);
                            log("nZ " + nZ);
                        }

                        // construct a FileInfoSer
                        // todo: this could be much leaner
                        // e.g. the nX, nY and bit depth
                        infoSer = new FileInfoSer[nZ];
                        for (int i = 0; i < nZ; i++) {
                            infoSer[i] = new FileInfoSer();
                            infoSer[i].fileName = lists[c][t];
                            infoSer[i].directory = channelFolders[c] + "/";
                            infoSer[i].width = nX;
                            infoSer[i].height = nY;
                            infoSer[i].bytesPerPixel = 2; // todo: how to get the bit-depth from the info?
                            infoSer[i].h5DataSet = dataSet;
                            infoSer[i].fileTypeString = fileType;
                        }

                        stack.setStack(infoSer, t, c);

                    }

                    //IJ.showProgress(t+c*nT, nT*nC);
                    IJ.showStatus(""+(t+c*nT)+"/"+nT*nC);
                }
            }
        } catch(Exception e) {
            IJ.showMessage("Error: "+e.toString());
        }

        IJ.showStatus(""+(nC*nT)+"/"+nC*nT);

        ImagePlus imp = null;
        if(stack!=null && stack.getSize()>0) {
            imp = makeImagePlus(stack, infoSer[0]);
            writeFileInfosSer(stack.getFileInfosSer(), directory+"ovs.ser");
        }
        return(imp);

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
        log("# getFilesInFolder: " + directory);
        // todo: can getting the file-list be faster?
        String[] list = new File(directory).list();
        if (list == null || list.length == 0)
            return null;
        list = this.sortFileList(list);
        log("Number of files: " + list.length);
        log("Sorted files:");
        for(String item : list) log("" + item);
        if (list == null) return null;
        else return (list);
    }

    String[] getFoldersInFolder(String directory) {
        log("# getFoldersInFolder: " + directory);
        String[] list = new File(directory).list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });
        if (list == null || list.length == 0)
            return null;
        list = this.sortFileList(list);
        for(String item : list) log("" + item);
        return (list);

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

    // todo: call the OffsetSize method from this
	public static ImagePlus openCroppedCenterRadiusFromInfos(ImagePlus imp, FileInfoSer[][][] infos, Point3D[] pc, Point3D pr, int tMin, int tMax) {
		int nC = infos.length;
        int nT = tMax-tMin+1;
        int nZ = infos[0][0].length;

        FileInfoSer[][][] croppedInfos = new FileInfoSer[nC][nT][nZ];

        if(Globals.verbose){
            log("# OpenStacksAsVirtualStack.openCroppedFromInfos");
            log("tMin: "+tMin);
            log("tMax: "+tMax);
        }

        OpenerExtensions oe = new OpenerExtensions();

        for(int c=0; c<nC; c++) {

            for(int t=tMin; t<=tMax; t++) {

                for(int z=0; z<nZ; z++) {

                    croppedInfos[c][t-tMin][z] = (FileInfoSer) infos[c][t][z].clone();
                    croppedInfos[c][t-tMin][z].isCropped = true;
                    croppedInfos[c][t-tMin][z].pCropOffset = pc[t].subtract(pr);
                    croppedInfos[c][t-tMin][z].pCropSize = pr.multiply(2).add(1, 1, 1);
                    //log("c "+c);
                    //log("t "+t);
                    //log("z "+z);
                    //log("offset "+croppedInfos[c][t-tMin][z].pCropOffset.toString());

                }

            }

        }

        VirtualStackOfStacks parentStack = (VirtualStackOfStacks) imp.getStack();
        VirtualStackOfStacks stack = new VirtualStackOfStacks(parentStack.getDirectory(), croppedInfos);

        return(makeImagePlus(stack, croppedInfos[0][0][0]));

    }

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

        OpenerExtensions oe = new OpenerExtensions();

        for(int c=0; c<nC; c++) {

            for(int t=tMin; t<=tMax; t++) {

                for(int z=0; z<nZ; z++) {

                    croppedInfos[c][t-tMin][z] = (FileInfoSer) infos[c][t][z].clone();
                    if(croppedInfos[c][t-tMin][z].isCropped) {
                        croppedInfos[c][t-tMin][z].pCropOffset = po[t].add(croppedInfos[c][t-tMin][z].pCropOffset);
                    } else {
                        croppedInfos[c][t - tMin][z].isCropped = true;
                        croppedInfos[c][t - tMin][z].pCropOffset = po[t];
                    }
                    croppedInfos[c][t-tMin][z].pCropSize = ps;
                    //log("c "+c);
                    //log("t "+t);
                    //log("z "+z);
                    //log("offset "+croppedInfos[c][t-tMin][z].pCropOffset.toString());

                }

            }

        }

        VirtualStackOfStacks parentStack = (VirtualStackOfStacks) imp.getStack();
        VirtualStackOfStacks stack = new VirtualStackOfStacks(parentStack.getDirectory(), croppedInfos);
        return(makeImagePlus(stack, croppedInfos[0][0][0]));

    }

    private static ImagePlus makeImagePlus(VirtualStackOfStacks stack, FileInfoSer fi) {
        int nC=stack.nC;
        int nT=stack.nT;
        int nZ=stack.nZ;

        double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		ImagePlus imp = new ImagePlus(fi.directory, stack);

        if (imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32)
			imp.getProcessor().setMinAndMax(min, max);
		imp.setFileInfo(fi.getFileInfo()); // saves FileInfo of the first image

        if(Globals.verbose) {
            log("# OpenStacksAsVirtualStack.makeImagePlus");
            log("nC: "+nC);
            log("nZ: "+nZ);
            log("nT: " + nT);
        }

        imp.setDimensions(nC, nZ, nT);
        imp.setOpenAsHyperStack(true);
        if(nC>1) imp.setDisplayMode(IJ.COMPOSITE);
        imp.setPosition(1, (int) nZ/2, 1);
		imp.resetDisplayRange();
		return(imp);
	}

    public static ImagePlus crop(ImagePlus imp) {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if (vss == null) {
            IJ.showMessage("Wrong image type.");
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
            return impCropped;

        } else {
            IJ.showMessage("Please put a rectangular ROI on the image.");
            return null;
        }
    }

    public void duplicateToRAM(ImagePlus imp) {
        final ImagePlus impDup = null;

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if (vss == null) {
            IJ.showMessage("Wrong image type.");
        }

        Roi roi = imp.getRoi();
        if (roi != null && roi.isArea()) {
            imp = crop(imp);
        }

        final ImagePlus finalImp = imp;
        // the thread just serves to make the IJ.progress show
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                try {
                    final ImagePlus impDup = new Duplicator().run(finalImp);
                    impDup.show();
                } finally {
                    //...
                }
            }
        });
        t1.start();

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


        OpenStacksAsVirtualStack ovs = null;

        // todo: remove the initialisation from the constructor and put it into openFromDirectory

        /*if(interactive) {
            ovs = new OpenStacksAsVirtualStack();
            ovs.run("");
            Registration register = new Registration();
            register.run("");
        }*/


        //String directory = "/Users/tischi/Desktop/example-data/MATLABtiff/";
        //String directory = "/Users/tischi/Desktop/example-data/luxendo/";

        String directory = "/Users/tischi/Desktop/example-data/compressedSingleStrip/";
        String filter = null;

        //String directory = "/Users/tischi/Desktop/example-data/MATLABtiff/";
        //String filter = null;

        //String openingMethod = "tiffLoadAllIFDs";

        //OpenHDF5test oh5 = new OpenHDF5test();
        //oh5.openOneFileAsImp("/Users/tischi/Desktop/example-data/luxendo/ch0/fused_t00000_c0.h5");
        //Globals.verbose = true;
        ovs = new OpenStacksAsVirtualStack();
        ovs.run("");

        //ImagePlus imp = ovs.openFromDirectory(directory, null);
        //ImagePlus imp = ovs.openFromInfoFile(directory, "ovs.ser");
        //imp.show();

        //Registration register = new Registration(imp);
        //register.showDialog();

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

