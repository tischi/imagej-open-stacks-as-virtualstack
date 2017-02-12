package ct.vss;

import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.io.FileSaver;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import loci.common.services.ServiceFactory;
import loci.formats.ImageWriter;
import loci.formats.meta.IMetadata;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;
import loci.formats.tiff.IFD;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ij.IJ.log;
import static java.awt.Desktop.*;


// todo: - find out why loading and saving info file is so slow
// todo: - save smaller info files

// todo: saving as tiff stacks does not always work, e.g. after object tracking
// todo: check if all files are parsed before allowing to "crop as new stream"

// todo: rearrange the GUI

/** Opens a folder of stacks as a virtual stack. */
public class OpenStacksAsVirtualStack implements PlugIn {

    private int nC, nT, nZ, nX, nY;
    private String[] channelFolders;
    private String[][] lists; // c, t
    private String[][][] ctzFileList;
    private String fileType;
    final String LOAD_CHANNELS_FROM_FOLDERS = "Subfolders";
    private static NonBlockingGenericDialog gd;
    //public String h5DataSet = "Data111";
    //private String filenamePattern = "_Target--";
    AtomicInteger iProgress = new AtomicInteger(0);
    int nProgress = 100;

    // todo: stop loading thread upon closing of image

    // todo: increase speed of Leica tif parsing, possible?

    // todo: make an editable dropdown list for the fileNamePattern

    public OpenStacksAsVirtualStack() {
    }

    public void run(String arg) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                showDialog();
            }
        });
    }

    public void showDialog() {
        StackStreamToolsGUI sstGUI = new StackStreamToolsGUI();
        sstGUI.showDialog();
    }

    public boolean checkIfHdf5DataSetExists(IHDF5Reader reader, String hdf5DataSet) {
        String dataSets = "";
        boolean dataSetExists = false;

        for (String dataSet : reader.getGroupMembers("/")) {
            if (dataSet.equals(hdf5DataSet)) {
                dataSetExists = true;
            }
            dataSets += "- " + dataSet + "\n";
        }

        if (!dataSetExists) {
            IJ.showMessage("The selected Hdf5 data set does not exist; " +
                    "please change to one of the following:\n\n" +
                    dataSets);
        }

        return dataSetExists;
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


    // todo: get rid of all the global variables

    public ImagePlus openFromDirectory(String directory, String channelPattern, String filterPattern, String hdf5DataSet, int nIOthreads) {
        int t = 0, z = 0, c = 0;
        ImagePlus imp = null;
        fileType = "not determined";
        FileInfo[] info;
        FileInfo fi0;

        // todo: depending on the fileOrder do different things
        // todo: add the filter to the getFilesInFolder function
        // todo: find a clean solution for the channel folder presence or absence!
        // todo: consistency check the list lengths

        if (channelPattern.equals(LOAD_CHANNELS_FROM_FOLDERS)) {

            //
            // Check for sub-folders
            //

            log("checking for sub-folders...");
            channelFolders = getFoldersInFolder(directory);
            if (channelFolders != null) {
                lists = new String[channelFolders.length][];
                for (int i = 0; i < channelFolders.length; i++) {
                    lists[i] = getFilesInFolder(directory + channelFolders[i], filterPattern);
                    if (lists[i] == null) {
                        log("no files found in folder: " + directory + channelFolders[i]);
                        return (null);
                    }
                }
                log("found sub-folders => interpreting as channel folders.");
            } else {
                log("no sub-folders found.");
            }

        } else {

            //
            // Get files in main directory
            //
            log("checking for files in folder: " + directory);
            lists = new String[1][];
            lists[0] = getFilesInFolder(directory, filterPattern);

            if (lists[0] != null) {
                //
                // check if it is Leica single tiff SPIM files
                //
                Pattern patternLeica = Pattern.compile("LightSheet 2.*");
                for (String fileName : lists[0]) {
                    if (patternLeica.matcher(fileName).matches()) {
                        fileType = "leica single tif";
                        log("detected fileType: " + fileType);
                        break;
                    }
                }
            }

        }

        //
        // generate a nC,nT,nZ fileList
        //

        if (fileType.equals("leica single tif")) {

            //
            // Do special stuff related to leica single files
            //

            Matcher matcherZ, matcherC, matcherT, matcherID;
            Pattern patternC = Pattern.compile(".*--C(.*).tif");
            Pattern patternZnoC = Pattern.compile(".*--Z(.*).tif");
            Pattern patternZwithC = Pattern.compile(".*--Z(.*)--C.*");
            Pattern patternT = Pattern.compile(".*--t(.*)--Z.*");
            Pattern patternID = Pattern.compile(".*?_(\\d+).*"); // is this correct?

            if (lists[0].length == 0) {
                IJ.showMessage("No files matching this pattern were found: " + filterPattern);
                return null;
            }

            // check which different fileIDs there are
            // those are three numbers after the first _
            // this happens due to restarting the imaging
            Set<String> fileIDset = new HashSet();
            for (String fileName : lists[0]) {
                matcherID = patternID.matcher(fileName);
                if (matcherID.matches()) {
                    fileIDset.add(matcherID.group(1));
                }
            }
            String[] fileIDs = fileIDset.toArray(new String[fileIDset.size()]);

            // check which different C, T and Z there are for each FileID

            ArrayList<HashSet<String>> channels = new ArrayList<HashSet<String>>();
            ArrayList<HashSet<String>> timepoints = new ArrayList<HashSet<String>>();
            ArrayList<HashSet<String>> slices = new ArrayList<HashSet<String>>();

            //
            // Deal with different file-names due to series being restarted during the imaging
            //
            for (String fileID : fileIDs) {
                channels.add(new HashSet<String>());
                timepoints.add(new HashSet<String>());
                slices.add(new HashSet<String>());
                log("FileID: " + fileID);
            }

            for (int iFileID = 0; iFileID < fileIDs.length; iFileID++) {

                Pattern patternFileID = Pattern.compile(".*?_" + fileIDs[iFileID] + ".*");

                for (String fileName : lists[0]) {

                    if (patternFileID.matcher(fileName).matches()) {

                        matcherC = patternC.matcher(fileName);
                        if (matcherC.matches()) {
                            // has multi-channels
                            channels.get(iFileID).add(matcherC.group(1));
                            matcherZ = patternZwithC.matcher(fileName);
                            if (matcherZ.matches()) {
                                slices.get(iFileID).add(matcherZ.group(1));
                            }
                        } else {
                            // has only one channel
                            matcherZ = patternZnoC.matcher(fileName);
                            if (matcherZ.matches()) {
                                slices.get(iFileID).add(matcherZ.group(1));
                            }
                        }

                        matcherT = patternT.matcher(fileName);
                        if (matcherT.matches()) {
                            timepoints.get(iFileID).add(matcherT.group(1));
                        }
                    }
                }

            }

            nT = 0;
            int[] tOffsets = new int[fileIDs.length + 1]; // last offset is not used, but added anyway
            tOffsets[0] = 0;

            for (int iFileID = 0; iFileID < fileIDs.length; iFileID++) {

                nC = Math.max(1, channels.get(iFileID).size());
                nZ = slices.get(iFileID).size(); // must be the same for all fileIDs

                log("FileID: " + fileIDs[iFileID]);
                log("  Channels: " + nC);
                log("  TimePoints: " + timepoints.get(iFileID).size());
                log("  Slices: " + nZ);

                nT += timepoints.get(iFileID).size();
                tOffsets[iFileID + 1] = nT;
            }

            //
            // sort into the final file list
            //

            ctzFileList = new String[nC][nT][nZ];

            for (int iFileID = 0; iFileID < fileIDs.length; iFileID++) {

                Pattern patternFileID = Pattern.compile(".*" + fileIDs[iFileID] + ".*");

                for (String fileName : lists[0]) {

                    if (patternFileID.matcher(fileName).matches()) {

                        // figure out which C,Z,T the file is
                        matcherC = patternC.matcher(fileName);
                        matcherT = patternT.matcher(fileName);
                        if (nC > 1) matcherZ = patternZwithC.matcher(fileName);
                        else matcherZ = patternZnoC.matcher(fileName);

                        if (matcherZ.matches()) {
                            z = Integer.parseInt(matcherZ.group(1).toString());
                        }
                        if (matcherT.matches()) {
                            t = Integer.parseInt(matcherT.group(1).toString());
                            t += tOffsets[iFileID];
                        }
                        if (matcherC.matches()) {
                            c = Integer.parseInt(matcherC.group(1).toString());
                        } else {
                            c = 0;
                        }

                        ctzFileList[c][t][z] = fileName;

                    }
                }
            }

            try {
                FastTiffDecoder ftd = new FastTiffDecoder(directory, ctzFileList[0][0][0]);
                info = ftd.getTiffInfo();
            } catch (Exception e) {
                info = null;
                IJ.showMessage("Error: " + e.toString());
            }

            fi0 = info[0];
            nX = fi0.width;
            nY = fi0.height;

        } else {

            //
            // either tif stacks or h5 stacks
            //

            if (channelPattern.equals(LOAD_CHANNELS_FROM_FOLDERS)) {

                nC = channelFolders.length;
                nT = lists[0].length;

            } else {

                // todo: this could be multiple channels
                nC = 1;
                channelFolders = new String[]{""};
                nT = lists[0].length; // todo: this would be wrong as well

            }

            //
            // Get some infos from the first file
            //
            if (lists[0][0].endsWith(".tif")) {

                fileType = "tif stacks";

                try {
                    FastTiffDecoder ftd = new FastTiffDecoder(directory + channelFolders[0], lists[0][0]);
                    info = ftd.getTiffInfo();
                } catch (Exception e) {
                    info = null;
                    IJ.showMessage("Error: " + e.toString());
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

            } else if (lists[0][0].endsWith(".h5")) {

                fileType = "h5";

                IHDF5Reader reader = HDF5Factory.openForReading(directory + channelFolders[c] + "/" + lists[0][0]);

                if (!checkIfHdf5DataSetExists(reader, hdf5DataSet)) return null;

                HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation("/" + hdf5DataSet);

                nZ = (int) dsInfo.getDimensions()[0];
                nY = (int) dsInfo.getDimensions()[1];
                nX = (int) dsInfo.getDimensions()[2];

            } else {

                IJ.showMessage("Unsupported file type: " + lists[0][0]);
                return (null);

            }

            log("File type: "+fileType);

            //
            // sort into the final file list
            //

            ctzFileList = new String[nC][nT][nZ];

            for (c = 0; c < channelFolders.length; c++) {
                for (t = 0; t < lists[c].length; t++) {
                    for (z = 0; z < nZ; z++) {
                        // all z with same file-name
                        ctzFileList[c][t][z] = lists[c][t];
                    }
                }
            }

        }

        //
        // init the virtual stack
        //

        VirtualStackOfStacks stack = new VirtualStackOfStacks(directory, channelFolders, ctzFileList, nC, nT, nX, nY, nZ, fileType, hdf5DataSet);
        imp = new ImagePlus("stream", stack);

        //
        // set file information for each c, t, z
        //

        try {

            nProgress = nT; iProgress.set(0);
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    updateStatus("Analyzed file");
                }
            });
            thread.start();


            ExecutorService es = Executors.newCachedThreadPool();
            nProgress = nT;

            for(int iThread=0; iThread<=nIOthreads; iThread++) {

                es.execute(new AnalyzeStackFiles(imp));

            }


        } catch(Exception e) {
            IJ.showMessage("Error: "+e.toString());
        }

        return(imp);

    }


    public static String getLastDir(String fileOrDirPath) {
        boolean endsWithSlash = fileOrDirPath.endsWith(File.separator);
        String[] split = fileOrDirPath.split(File.separator);
        if(endsWithSlash) return split[split.length-1];
        else return split[split.length];
    }

    class AnalyzeStackFiles implements Runnable {
        ImagePlus imp;

        AnalyzeStackFiles(ImagePlus imp) {
            this.imp = imp;
        }

        public void run() {

            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();

            while(true) {

                int t = iProgress.getAndAdd(1);

                if ((t+1) > nProgress) return;

                for (int c = 0; c < vss.nC; c++) {

                    vss.setStackFromFile(t, c);

                }

                // show image window once time-point 0 is loaded
                if (t == 0) {

                    if (vss != null && vss.getSize() > 0) {
                        imp = makeImagePlus(vss);
                    } else {
                        IJ.showMessage("Something went wrong loading the first image stack!");
                        return;
                    }

                    imp.show();

                    // todo: get the selected directory as name
                    imp.setTitle("image");

                    // show compression
                    FileInfoSer[][][] infos = vss.getFileInfosSer();
                    if(infos[0][0][0].compression == 1)
                        log("Compression = None");
                    else if(infos[0][0][0].compression == 2)
                        log("Compression = LZW");
                    else if(infos[0][0][0].compression == 6)
                        log("Compression = ZIP");
                    else
                        log("Compression = "+infos[0][0][0].compression);

                }


            } // t-loop



            }

        }

    class SaveToStacks implements Runnable {
        ImagePlus imp;
        String fileType, path;
        String compression;
        int rowsPerStrip;

        SaveToStacks(ImagePlus imp, String path, String fileType, String compression, int rowsPerStrip) {
            this.imp = imp;
            this.fileType = fileType;
            this.path = path;
            this.compression = compression;
            this.rowsPerStrip = rowsPerStrip;
        }

        public void run() {

            VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
            ImagePlus impCT = null;

            while(true) {

                int t = iProgress.getAndAdd(1);
                if ((t+1) > nProgress) return;

                if(compression.equals("LZW")) {

                    for (int c = 0; c < imp.getNChannels(); c++) {

                        impCT = vss.getFullFrame(t, c, new Point3D(1, 1, 1));
                        String sC = String.format("%1$02d", c);
                        String sT = String.format("%1$05d", t);
                        String pathCT = path + "--C" + sC + "--T" + sT + ".ome.tif";

                        try {

                            ServiceFactory factory = new ServiceFactory();
                            OMEXMLService service = factory.getInstance(OMEXMLService.class);
                            IMetadata omexml = service.createOMEXMLMetadata();
                            omexml.setImageID("Image:0", 0);
                            omexml.setPixelsID("Pixels:0", 0);
                            omexml.setPixelsBinDataBigEndian(Boolean.TRUE, 0, 0);
                            omexml.setPixelsDimensionOrder(DimensionOrder.XYZCT, 0);
                            omexml.setPixelsType(PixelType.UINT16, 0);
                            omexml.setPixelsSizeX(new PositiveInteger(impCT.getWidth()), 0);
                            omexml.setPixelsSizeY(new PositiveInteger(impCT.getHeight()), 0);
                            omexml.setPixelsSizeZ(new PositiveInteger(impCT.getNSlices()), 0);
                            omexml.setPixelsSizeC(new PositiveInteger(1), 0);
                            omexml.setPixelsSizeT(new PositiveInteger(1), 0);

                            int channel = 0;
                            omexml.setChannelID("Channel:0:" + channel, 0, channel);
                            omexml.setChannelSamplesPerPixel(new PositiveInteger(1), 0, channel);

                            ImageWriter writer = new ImageWriter();
                            writer.setCompression(TiffWriter.COMPRESSION_LZW);
                            writer.setValidBitsPerPixel(impCT.getBytesPerPixel()*8);
                            writer.setMetadataRetrieve(omexml);
                            writer.setId(pathCT);
                            writer.setWriteSequentially(true); // ? is this necessary
                            TiffWriter tiffWriter = (TiffWriter) writer.getWriter();
                            long[] rowsPerStripArray = new long[1];
                            rowsPerStripArray[0] = rowsPerStrip;

                            for (int z = 0; z < impCT.getNSlices(); z++) {
                                IFD ifd = new IFD();
                                ifd.put(IFD.ROWS_PER_STRIP, rowsPerStripArray);
                                tiffWriter.saveBytes(z, shortToByteBigEndian((short[])impCT.getStack().getProcessor(z+1).getPixels()),ifd);
                            }

                            writer.close();

                        } catch (Exception e) {
                            log("exception");
                            IJ.showMessage(e.toString());
                        }
                    }

                } else {  // no compression: use ImageJ's FileSaver

                    for (int c = 0; c < imp.getNChannels(); c++) {
                        impCT = vss.getFullFrame(t, c, new Point3D(1, 1, 1));
                        FileSaver fileSaver = new FileSaver(impCT);
                        String sC = String.format("%1$02d", c);
                        String sT = String.format("%1$05d", t);
                        String pathCT = path + "--C" + sC + "--T" + sT + ".tif";
                        fileSaver.saveAsTiffStack(pathCT);
                    }

                }

                Globals.threadlog("saved time-point: " + (t+1) + " of " + nProgress);

            }

        }

    }

    byte[] shortToByteBigEndian(short[] input)
    {
        int short_index, byte_index;
        int iterations = input.length;

        byte[] buffer = new byte[input.length * 2];

        short_index = byte_index = 0;

        for(/*NOP*/; short_index != iterations; /*NOP*/)
        {
            buffer[byte_index] = (byte) ((input[short_index] & 0xFF00) >> 8);
            buffer[byte_index + 1]  = (byte) (input[short_index] & 0x00FF);
            ++short_index; byte_index += 2;
        }

        return buffer;
    }

    String[] sortAndFilterFileList(String[] rawlist, String filterPattern) {
        int count = 0;

        Pattern patternFilter = Pattern.compile(filterPattern);

        for (int i = 0; i < rawlist.length; i++) {
            String name = rawlist[i];
            if (name.endsWith(".tif") || name.endsWith(".h5") )
                count++;
            else if (!patternFilter.matcher(name).matches())
                count++;
            else
                rawlist[i] = null;

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

    String[] getFilesInFolder(String directory, String filterPattern) {
        // todo: can getting the file-list be faster?
        String[] list = new File(directory).list();
        if (list == null || list.length == 0)
            return null;
        list = this.sortAndFilterFileList(list, filterPattern);
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
        //list = this.sortFileList(list);
        return (list);

    }

    public boolean updateStatus(String message) {
        while(iProgress.get()<nProgress) {
            IJ.wait(50);
            IJ.showStatus(message+" " + iProgress.get() + "/" + nProgress);
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
            iProgress.set(nProgress);
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

    //public static Point3D curatePositionOffsetSize(ImagePlus imp, Point3D po, Point3D ps) {
    //    boolean[] shifted = new boolean[1];
    //    return(curatePositionOffsetSize(imp, po, ps, shifted));
    //}

    public static Point3D curatePositionOffsetSize(ImagePlus imp, Point3D po, Point3D ps, boolean[] shifted) {
        shifted[0] = false;

        // round the values
        int x = (int) (po.getX()+0.5);
        int y = (int) (po.getY()+0.5);
        int z = (int) (po.getZ()+0.5);
        int xs = (int) (ps.getX()+0.5);
        int ys = (int) (ps.getY()+0.5);
        int zs = (int) (ps.getZ()+0.5);

        // make sure that the ROI stays within the image bounds
        if (x < 0) {x = 0; shifted[0] = true;}
        if (y < 0) {y = 0; shifted[0] = true;}
        if (z < 0) {z = 0; shifted[0] = true;}

        if (x+xs > imp.getWidth()-1) {x = imp.getWidth()-xs-1; shifted[0] = true;}
        if (y+ys > imp.getHeight()-1) {y = imp.getHeight()-ys-1; shifted[0] = true;}
        if (z+zs > imp.getNSlices()-1) {z = imp.getNSlices()-zs-1; shifted[0] = true;}

        // check if it is ok now, otherwise the chosen radius simply is too large
        if (x < 0)  {
            IJ.showMessage("object size in x is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        if (y < 0){
            IJ.showMessage("object size in y is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        if (z < 0) {
            IJ.showMessage("object size in z is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        //if(shifted[0]) {
        //    log("++ region was shifted to stay within image bounds.");
        //}
        return(new Point3D(x,y,z));
    }

    // creates a new view on the data
    public static ImagePlus makeCroppedVirtualStack(ImagePlus imp, Point3D[] po, Point3D ps, int tMin, int tMax) {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileInfoSer[][][] infos = vss.getFileInfosSer();

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

        ImagePlus imp = new ImagePlus("", stack);

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

    public static ImagePlus crop(ImagePlus imp, int zMin, int zMax) {

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

        if(zMax-zMin+1 > vss.nZ) {
            IJ.showMessage(
                    "The z-cropping range is larger than the data; please change the values."
            );
            return null;
        }

        if(zMax<=zMin) {
            IJ.showMessage(
                    "zMax of the cropping range needs to be larger than zMin; please change the values."
            );
            return null;
        }


        Roi roi = imp.getRoi();
        if (roi != null && roi.isArea()) {

            int tMin = 0;
            int tMax = vss.nT - 1;

            Point3D[] po = new Point3D[vss.nT];
            for (int t = 0; t < vss.nT; t++) {
                po[t] = new Point3D(roi.getBounds().getX(), roi.getBounds().getY(), zMin);
            }
            Point3D ps = new Point3D(roi.getBounds().getWidth(), roi.getBounds().getHeight(), zMax-zMin+1);

            ImagePlus impCropped = makeCroppedVirtualStack(imp, po, ps, tMin, tMax);
            impCropped.setTitle(imp.getTitle()+"-crop");
            return impCropped;




        } else {

            IJ.showMessage("Please put a rectangular selection on the image.");
            return null;

        }
    }

    public ImagePlus duplicateToRAM(ImagePlus imp) {

        final VirtualStackOfStacks stack = Globals.getVirtualStackOfStacks(imp);
        if(stack==null) return(null);

        nProgress = stack.nSlices;

        ImageStack stack2 = null;
        int n = stack.nSlices;
        for (int i=1; i<=n; i++) {
            iProgress.set(i);
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
        //final String directory = "/Users/tischi/Desktop/example-data/Leica single tif files/";
        //final String directory = "/Users/tischi/Desktop/example-data/Leica single tif files 2channels/";

        //final String directory = "/Users/tischi/Desktop/example-data/luxendo/";

        final String directory = "/Users/tischi/Desktop/example-data/bbb/";
        //final String directory = "/Users/tischi/Desktop/example-data/Nils--MATLAB--Compressed/";

        // final String directory = "/Volumes/USB DISK/Ashna -test/";
        // final String directory = "/Users/tischi/Desktop/example-data/Ashna-Leica-Target-LSEA/";



        //final String directory = "/Volumes/My Passport/Res_13/";
        //final String directory = "/Users/tischi/Desktop/example-data/tracking_test/";
        //final String directory = "/Volumes/almfspim/tischi/SPIM-example-data/Nils-MATLAB--tif-stacks--1channel--lzw-compressed/";
        //String filter = null;

        //String openingMethod = "tiffLoadAllIFDs";

        //OpenHDF5test oh5 = new OpenHDF5test();
        //oh5.openOneFileAsImp("/Users/tischi/Desktop/example-data/luxendo/ch0/fused_t00000_c0.h5");
        //Globals.verbose = true;
        final OpenStacksAsVirtualStack ovs = new OpenStacksAsVirtualStack();
        Thread t1 = new Thread(new Runnable() {
            public void run() {
                int nIOthreads = 10;
                ovs.openFromDirectory(directory, ".*", ".*", "Data", nIOthreads);
            }
        });
        t1.start();
        IJ.wait(3000);
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


    class StackStreamToolsGUI extends JPanel implements ActionListener, FocusListener, ItemListener {

        String[] actions = {"Stream from folder",
                "Stream from info file",
                "Save as info file",
                "Save as tiff stacks",
                "Duplicate to RAM",
                "Crop as new stream",
                "Report issue"};


        JCheckBox cbLog = new JCheckBox("Verbose logging");
        JCheckBox cbLZW = new JCheckBox("LZW compression");

        JTextField tfCropZminZmax = new JTextField("0,0", 7);
        JTextField tfIOThreads = new JTextField("1", 2);
        JTextField tfRowsPerStrip = new JTextField("10", 3);

        //JTextField tfFileNamePattern = new JTextField(".*LSEA00.*", 10);

        JComboBox filterPatternComboBox = new JComboBox(new String[] {".*",".*_Target--.*",".*--LSEA00--.*",".*--LSEA01--.*"});
        JComboBox channelPatternComboBox = new JComboBox(new String[] {"None",LOAD_CHANNELS_FROM_FOLDERS,"--C.*--"});
        JComboBox hdf5DataSetComboBox = new JComboBox(new String[] {"Data","Data111","Data222","Data444"});

        JFileChooser fc;

        public void StackStreamToolsGUI() {
        }

        public void showDialog() {

            JFrame frame = new JFrame("Data Streaming Tools");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            Container c = frame.getContentPane();
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));

            String[] toolTipTexts = getToolTipFile("DataStreamingHelp.html");
            ToolTipManager.sharedInstance().setDismissDelay(10000000);

            // Buttons
            JButton[] buttons = new JButton[actions.length];
            for (int i = 0; i < buttons.length; i++) {
                buttons[i] = new JButton(actions[i]);
                buttons[i].setActionCommand(actions[i]);
                buttons[i].addActionListener(this);
                buttons[i].setToolTipText(toolTipTexts[i]);
            }

            // Checkboxes
            cbLog.setSelected(false);
            cbLog.addItemListener(this);
            cbLZW.setSelected(true);

            int i = 0, j = 0;

            ArrayList<JPanel> panels = new ArrayList<JPanel>();

            panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
            panels.get(j).add(new JLabel("STREAMING"));
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(buttons[i++]);
            panels.get(j).add(buttons[i++]);
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(new JLabel("Filename pattern:"));
            panels.get(j).add(filterPatternComboBox);
            filterPatternComboBox.setEditable(true);
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(new JLabel("Channel pattern:"));
            channelPatternComboBox.setEditable(true);
            panels.get(j).add(channelPatternComboBox);
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(new JLabel("HDF5 data set:"));
            panels.get(j).add(hdf5DataSetComboBox);
            hdf5DataSetComboBox.setEditable(true);
            c.add(panels.get(j++));

            c.add(new JSeparator(SwingConstants.HORIZONTAL));
            panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
            panels.get(j).add(new JLabel("SAVING"));
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(buttons[i++]);
            panels.get(j).add(buttons[i++]);
            panels.get(j).add(buttons[i++]);
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(new JLabel("LZW chunks [ny]"));
            panels.get(j).add(tfRowsPerStrip);
            panels.get(j).add(cbLZW);
            c.add(panels.get(j++));

            c.add(new JSeparator(SwingConstants.HORIZONTAL));
            panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
            panels.get(j).add(new JLabel("CROPPING"));
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(new JLabel("zMin, zMax:"));
            panels.get(j).add(tfCropZminZmax);
            panels.get(j).add(buttons[i++]);
            c.add(panels.get(j++));

            c.add(new JSeparator(SwingConstants.HORIZONTAL));
            panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
            panels.get(j).add(new JLabel("OTHER"));
            c.add(panels.get(j++));

            panels.add(new JPanel());
            panels.get(j).add(new JLabel("I/O threads"));
            panels.get(j).add(tfIOThreads);
            panels.get(j).add(cbLog);
            panels.get(j).add(buttons[i++]);
            c.add(panels.get(j++));

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

        }

        public void focusGained(FocusEvent e) {
            //
        }

        public void focusLost(FocusEvent e) {
            JTextField tf = (JTextField) e.getSource();
            if (!(tf == null)) {
                tf.postActionEvent();
            }
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

            // todo: get rid of the global osv class variables.

            final OpenStacksAsVirtualStack osv = new OpenStacksAsVirtualStack();
            final String h5DataSet = (String)hdf5DataSetComboBox.getSelectedItem();
            final int nSavingThreads = new Integer(tfIOThreads.getText());
            final int rowsPerStrip = new Integer(tfRowsPerStrip.getText());
            final String filterPattern = (String)filterPatternComboBox.getSelectedItem();
            final String channelPattern = (String)channelPatternComboBox.getSelectedItem();

            if (e.getActionCommand().equals(actions[i++])) {

                // Open from folder
                final String directory = IJ.getDirectory("Select a Directory");
                if (directory == null)
                    return;

                Thread t1 = new Thread(new Runnable() {
                    public void run() {
                        osv.openFromDirectory(directory, channelPattern, filterPattern, h5DataSet, nSavingThreads);
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

                //
                // "Save as info file"
                //

                ImagePlus imp = IJ.getImage();
                final VirtualStackOfStacks vss = Globals.getVirtualStackOfStacks(imp);
                if(vss==null) return;

                fc = new JFileChooser(vss.getDirectory());
                int returnVal = fc.showSaveDialog(StackStreamToolsGUI.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    final File file = fc.getSelectedFile();

                    //
                    // Check that all image files have been parsed
                    //
                    int numberOfUnparsedFiles = vss.numberOfUnparsedFiles();
                    if(numberOfUnparsedFiles > 0) {
                        IJ.showMessage("There are still "+numberOfUnparsedFiles+
                                " files in the folder that have not been parsed yet.\n" +
                                "Please try again later (check ImageJ's status bar).");
                        return;
                    }

                    //
                    // Save the info file
                    //
                    Thread t1 = new Thread(new Runnable() {
                        public void run() {
                            log("Saving: " + file.getAbsolutePath());
                            osv.writeFileInfosSer(vss.getFileInfosSer(), file.getAbsolutePath());
                        }
                    }); t1.start();

                    // update progress status
                    Thread t2 = new Thread(new Runnable() {
                        public void run() {
                            osv.iProgress.set(0);
                            osv.nProgress=1;
                            osv.updateStatus("Saving info file");
                        }
                    });
                    t2.start();

                } else {
                    log("Save command cancelled by user.");
                }

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // "Save as tiff stacks"
                //
                ImagePlus imp = IJ.getImage();
                final VirtualStackOfStacks vss = Globals.getVirtualStackOfStacks(imp);
                if(vss==null) return;

                //
                // Check that all image files have been parsed
                //
                int numberOfUnparsedFiles = vss.numberOfUnparsedFiles();
                if(numberOfUnparsedFiles > 0) {
                    IJ.showMessage("There are still "+numberOfUnparsedFiles+
                            " files in the folder that have not been parsed yet.\n" +
                            "Please try again later (check ImageJ's status bar).");
                    return;
                }


                fc = new JFileChooser(vss.getDirectory());
                int returnVal = fc.showSaveDialog(StackStreamToolsGUI.this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    final File file = fc.getSelectedFile();

                    imp = IJ.getImage();

                    nProgress = imp.getNFrames();
                    iProgress.set(0);

                    Thread thread = new Thread(new Runnable() {
                        public void run() {
                            updateStatus("Saved file");
                        }
                    });
                    thread.start();

                    String compression = "";

                    if(cbLZW.isSelected())
                        compression="LZW";

                    // Normal sequential saving (for debugging)
                    //SaveToStacks saveToStacks = new SaveToStacks(imp, file.getAbsolutePath(), "tiffStacks", compression, rowsPerStrip);
                    //saveToStacks.run();

                    // Multi-threaded saving (for speed)
                    ExecutorService es = Executors.newCachedThreadPool();
                    for(int iThread=0; iThread<nSavingThreads; iThread++) {
                        es.execute(new SaveToStacks(imp, file.getAbsolutePath(), "tiffStacks", compression, rowsPerStrip));
                    }


                } else {
                    log("Save command cancelled by user.");
                    return;
                }

                //} else if (e.getActionCommand().equals(actions[i++])) {
                // "Save as h5 stacks"
                //    IJ.showMessage("Not yet implemented.");

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
                // Crop As New Stream
                //

                ImagePlus imp = IJ.getImage();
                final VirtualStackOfStacks vss = Globals.getVirtualStackOfStacks(imp);
                if(vss==null) return;

                //
                // Check that all image files have been parsed
                //

                int numberOfUnparsedFiles = vss.numberOfUnparsedFiles();
                if(numberOfUnparsedFiles > 0) {
                    IJ.showMessage("There are still "+numberOfUnparsedFiles+
                            " files in the folder that have not been parsed yet.\n" +
                            "Please try again later (check ImageJ's status bar).");
                    return;
                }

                //
                // Get z cropping range
                //

                String[] sA = tfCropZminZmax.getText().split(",");
                if(sA.length!=2) {
                    IJ.showMessage("Something went wrong parsing the zMin, zMax croppping values.\n" +
                            "Please check that there are two comma separated values.");
                    return;
                }
                ImagePlus imp2 = osv.crop(imp, new Integer(sA[0]), new Integer(sA[1]));
                if (imp2 != null)
                    imp2.show();

            }  else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Report issue
                //

                String url = "https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues";
                if (isDesktopSupported()) {
                    try {
                        final URI uri = new URI(url);
                        getDesktop().browse(uri);
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

}

