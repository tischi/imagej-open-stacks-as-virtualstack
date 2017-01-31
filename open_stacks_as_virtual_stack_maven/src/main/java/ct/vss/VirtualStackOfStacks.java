package ct.vss;

/**
 * Created by tischi on 27/10/16.
 */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import static ij.IJ.log;




// todo: replace all == with "equals"

/**
 This class represents an array of disk-resident image stacks.
 */
public class VirtualStackOfStacks extends ImageStack {
    int nSlices;
    int nX, nY, nZ, nC, nT;
    FileInfoSer[][][] infos;  // c, t, z
    String fileType = "tiff"; // h5
    String directory = "";
    String[] channelFolders;
    String[][][] fileList;
    String h5DataSet;

    /** Creates a new, empty virtual stack of required size */
    public VirtualStackOfStacks(String directory, String[] channelFolders, String[][][] fileList, int nC, int nT, int nX, int nY, int nZ, String fileType, String h5DataSet) {
        super();

        this.directory = directory;
        this.nC = nC;
        this.nT = nT;
        this.nZ = nZ;
        this.nX = nX;
        this.nY = nY;
        this.nSlices = nC*nT*nZ;
        this.fileType = fileType;
        if(channelFolders==null) {
            this.channelFolders = new String[nC];
            for(int ic=0; ic<nC; ic++) this.channelFolders[ic] = "";
        } else {
            this.channelFolders = channelFolders;
        }
        this.fileList = fileList;
        this.infos = new FileInfoSer[nC][nT][];
        this.h5DataSet = h5DataSet;

        if(Globals.verbose) {
            logStatus();
        }

    }

    public VirtualStackOfStacks(String directory, FileInfoSer[][][] infos) {
        super();

        this.infos = infos;
        this.directory = directory;
        nC = infos.length;
        nT = infos[0].length;

        if(infos[0][0][0].isCropped) {
            nX = (int) infos[0][0][0].pCropSize[0];
            nY = (int) infos[0][0][0].pCropSize[1];
            nZ = (int) infos[0][0][0].pCropSize[2];
        } else {
            nX = (int) infos[0][0][0].width;
            nY = (int) infos[0][0][0].height;
            nZ = (int) infos[0][0].length;
        }

        nSlices = nC*nT*nZ;

        if(infos[0][0][0].fileName.endsWith(".h5"))
            this.fileType = "h5";
        if(infos[0][0][0].fileName.endsWith(".tif"))
            this.fileType = "tif";

        if(Globals.verbose) {
            logStatus();
        }

    }

    public void logStatus() {
            log("# VirtualStackOfStacks");
            log("fileType: "+fileType);
            log("x: "+nX);
            log("y: "+nY);
            log("z: "+nZ);
            log("c: "+nC);
            log("t: "+nT);
    }

    public FileInfoSer[][][] getFileInfosSer() {
        return(infos);
    }

    public String getDirectory() {
        return directory;
    }

    public int numberOfUnparsedFiles() {
        int numberOfUnparsedFiles = 0;
        for(int c = 0; c < nC; c++ )
            for(int t = 0; t < nT; t++)
                if (infos[c][t] == null)
                    numberOfUnparsedFiles++;

        return numberOfUnparsedFiles;
    }

    /** Adds an image stack from file infos */
    public void setStackFromFile(int t, int c) {
        FileInfo[] info = null;
        FileInfoSer[] infoSer = null;
        FastTiffDecoder ftd;

        long startTime = System.currentTimeMillis();

        try {


            if ( fileType.equals("tif stacks") ) {

                ftd = new FastTiffDecoder(directory + channelFolders[c], fileList[c][t][0]);
                info = ftd.getTiffInfo();

                // convert FileInfo[] to FileInfoSer[]
                infoSer = new FileInfoSer[nZ];
                for (int z = 0; z < nZ; z++) {
                    infoSer[z] = new FileInfoSer(info[z]);
                    infoSer[z].directory = channelFolders[c] + "/"; // relative path to main directory
                    infoSer[z].fileTypeString = fileType;
                }

            } else if (fileType.equals("leica single tif")) {


                infoSer = new FileInfoSer[nZ];

                //
                // open all IFDs from all files and convert to FileInfoSer
                // (this is necessary if they are compressed in any way)
                //
                for (int z = 0; z < nZ; z++) {

                    ftd = new FastTiffDecoder(directory + channelFolders[c], fileList[c][t][z]);
                    info = ftd.getTiffInfo();
                    infoSer[z] = new FileInfoSer(info[0]); // just duplicate from first file
                    infoSer[z].directory = channelFolders[c] + "/"; // relative path to main directory
                    infoSer[z].fileName = fileList[c][t][z];
                    infoSer[z].fileTypeString = fileType;

                }


            } else if (fileType.equals("h5")) {

                //
                // construct a FileInfoSer
                // todo: this could be much leaner
                // e.g. the nX, nY and bit depth
                //
                infoSer = new FileInfoSer[nZ];
                for (int i = 0; i < nZ; i++) {
                    infoSer[i] = new FileInfoSer();
                    infoSer[i].fileName = fileList[c][t][0];
                    infoSer[i].directory = channelFolders[c] + "/";
                    infoSer[i].width = nX;
                    infoSer[i].height = nY;
                    infoSer[i].bytesPerPixel = 2; // todo: how to get the bit-depth from the info?
                    infoSer[i].h5DataSet = h5DataSet;
                    infoSer[i].fileTypeString = fileType;
                }

            } // h5

        } catch(Exception e) {

            IJ.showMessage("Error: "+e.toString());

        }

        this.infos[c][t] = infoSer;

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

    /** Does noting. */
    public void deleteSlice(int n) {
        /*
        if (n<1 || n>nSlices)
            throw new IllegalArgumentException("Argument out of range: "+n);
        if (nSlices<1)
            return;
        for (int i=n; i<nSlices; i++)
            infos[i-1] = infos[i];
        infos[nSlices-1] = null;
        nSlices--;
        */
    }

    /** Deletes the last slice in the stack. */
    public void deleteLastSlice() {
        /*if (nSlices>0)
            deleteSlice(nSlices);
            */
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
    /* the n is computed by IJ assuming the czt ordering
    n = ( c + z*nC + t*nZ*nC ) + 1
    */
    public ImageProcessor getProcessor(int n) {
        // recompute c,z,t
        n -= 1;
        int c = (n % nC);
        int z = ((n-c)%(nZ*nC))/nC;
        int t = (n-c-z*nC)/(nZ*nC);

        ImagePlus imp;


        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getProcessor");
            log("requested slice [one-based]: "+(n+1));
            log("c [one-based]: "+ (c+1));
            log("z [one-based]: "+ (z+1));
            log("t [one-based]: "+ (t+1));
            log("opening file: "+directory+infos[c][t][0].directory+infos[c][t][0].fileName);
        }

        int dz = 1;
        Point3D po, ps;
        if(infos[c][t] == null) {
            //ImagePlus imp0 = IJ.getImage();
            //imp0.setPosition(1,(int)imp0.getNSlices()/2,1);
            //imp0.updateAndDraw();
            //IJ.showMessage("The file corresponding to this time point has not been analyzed yet.\n" +
            //        "Please wait...");
            setStackFromFile(t, c);
        }

        FileInfoSer fi = infos[c][t][0];

        if(fi.isCropped) {
            // load cropped slice
            po = new Point3D(fi.pCropOffset[0],fi.pCropOffset[1],fi.pCropOffset[2]+z);;
            ps = new Point3D(fi.pCropSize[0],fi.pCropSize[1],1);

        } else {
            // load full slice
            po = new Point3D(0,0,z);
            ps = new Point3D(fi.width,fi.height,1);
        }

        // todo: call the getCube... method from here

        imp = new OpenerExtensions().openCroppedStackOffsetSize(directory, infos[c][t], dz, po, ps);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        }

        return imp.getProcessor();

    }

    public boolean isCropped() {
        return(infos[0][0][0].isCropped);
    }

    public Point3D getCropOffset() {
        return(new Point3D(infos[0][0][0].pCropOffset[0], infos[0][0][0].pCropOffset[1], infos[0][0][0].pCropOffset[2]));
    }

    public Point3D getCropSize() {
        return(new Point3D(infos[0][0][0].pCropSize[0], infos[0][0][0].pCropSize[1], infos[0][0][0].pCropSize[2]));
    }

    public ImagePlus getFullFrame(int t, int c, Point3D pSubSample) {
        Point3D po, ps;

        po = new Point3D(0, 0, 0);
        if(infos[0][0][0].isCropped) {
            ps = infos[0][0][0].getCropSize();
        } else {
            ps = new Point3D(nX, nY, nZ);
        }

        ImagePlus imp = getCubeByTimeOffsetAndSize(t, c, po, ps, pSubSample);
        if( (int)pSubSample.getX()>1 || (int)pSubSample.getY()>1) {
            return(resizeWidthAndHeight(imp,(int)pSubSample.getX(),(int)pSubSample.getY()));
        } else {
            return(imp);
        }
    }

    public ImagePlus getCubeByTimeOffsetAndSize(int t, int c, Point3D po, Point3D pSize, Point3D pSubSample) {

        if (Globals.verbose) {
            log("# VirtualStackOfStacks.getCroppedFrameOffsetSize");
            log("t: " + t);
            log("c: " + c);
        }

        FileInfoSer fi = infos[0][0][0];

        if (fi.isCropped) {
            po = po.add(fi.getCropOffset());
        }

        if (infos[c][t] == null) {
            // file info not yet loaded => get it!
            setStackFromFile(t, c);
        }

        // todo: load less if out-of-bounds
        ImagePlus imp = new OpenerExtensions().openCroppedStackOffsetSize(directory, infos[c][t], (int) pSubSample.getZ(), po, pSize);

        if (imp == null) {
            log("Error: loading failed!");
            return null;
        }

        // todo: pad with zeros if loaded less


        if ((int) pSubSample.getX() > 1 || (int) pSubSample.getY() > 1) {
            return (resizeWidthAndHeight(imp, (int) pSubSample.getX(), (int) pSubSample.getY()));
        } else {
            return (imp);
        }
    }

    public ImagePlus resizeWidthAndHeight(ImagePlus imp, int dx, int dy) {
        int nSlices = imp.getStackSize();
        int nx = imp.getWidth(), ny = imp.getHeight();
        ImagePlus imp2 = imp.createImagePlus();
        ImageStack stack1 = imp.getStack();
        ImageStack stack2 = new ImageStack(nx/dx, ny/dy);
        ImageProcessor ip1, ip2;
        int method = ImageProcessor.NEAREST_NEIGHBOR;
        if (nx == 1 || ny == 1)
            method = ImageProcessor.NONE;
        for (int i = 1; i <= nSlices; i++) {
            ip1 = stack1.getProcessor(i);
            ip1.setInterpolationMethod(method);
            ip2 = ip1.resize(nx/dx, ny/dy, false);
            if (ip2 != null)
                stack2.addSlice("", ip2);
        }
        imp2.setStack("", stack2);
        return(imp2);
    }

    public int getSize() {
        return nSlices;
    }

    public int getWidth() {
        return nX;
    }

    public int getHeight() {
        return nY;
    }

    /** Returns the file name of the Nth image. */
    public String getSliceLabel(int n) {
        //int nFile;
        //nFile = (n-1) / nZ;
        //return infos[nFile][0].fileName;
        return "slice label";
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
        return true; // do we need this?
    }

    /** Does nothing. */
    public void trim() {
    }

}


/*
    public void deleteSlice(int n) {
        if (n<1 || n>nSlices)
            throw new IllegalArgumentException("Argument out of range: "+n);
        if (nSlices<1)
            return;
        for (int i=n; i<nSlices; i++)
            infos[i-1] = infos[i];
        infos[nSlices-1] = null;
        nSlices--;
    }

    /** Deletes the last slice in the stack.
    public void deleteLastSlice() {
        if (nSlices>0)
            deleteSlice(nSlices);
    }*/

/*
// todo: put the conversion from centerRadii to offsetSize into this function
    public ImagePlus getCubeByTimeCenterAndRadii(int t, int c, Point3D psub, Point3D pc, Point3D pr) {

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getCroppedFrameCenterRadii");
            log("t: "+t);
            log("c: "+c);
            }

        FileInfoSer fi = infos[0][0][0];

        if(fi.isCropped) {
            // load cropped slice
            pc = pc.add(fi.getCropOffset());
        }

        if(infos[c][t] == null) {
            // file info not yet loaded => get it!
            setStackFromFile(t, c);
        }

        ImagePlus imp = new OpenerExtensions().openCroppedStackCenterRadii(directory, infos[c][t], (int) psub.getZ(), pc, pr);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        } else {
            return imp;
        }
    }
    */