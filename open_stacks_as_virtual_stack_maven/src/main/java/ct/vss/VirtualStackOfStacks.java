package ct.vss;

/**
 * Created by tischi on 27/10/16.
 */

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import static ij.IJ.log;

/**
 This class represents an array of disk-resident image stacks.
 */
public class VirtualStackOfStacks extends ImageStack {
    int nSlices;
    int nX, nY, nZ, nC, nT;
    protected FileInfoSer[][][] infos;  // c, t, z
    protected String fileType = "tiff"; // h5
    protected String directory = "";

    /** Creates a new, empty virtual stack of required size */
    public VirtualStackOfStacks(String directory, Point3D pSize, int nC, int nT, String fileType) {
        super();
        this.directory = directory;
        this.nX = (int)pSize.getX();
        this.nY = (int)pSize.getY();
        this.nZ = (int)pSize.getZ();
        this.nC = nC;
        this.nT = nT;
        this.fileType = fileType;
        this.infos = new FileInfoSer[nC][nT][];
        nSlices = nC*nT*nZ;

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

    /* Adds an image stack */
    public void setStack(FileInfoSer[] info, int t, int c) {
        if (info==null)
            throw new IllegalArgumentException("'info' is null!");
        infos[c][t] = info;
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

        ImagePlus imp = new OpenerExtensions().openCroppedStackOffsetSize(directory, infos[c][t], dz, po, ps);

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

    public ImagePlus getCroppedFrameCenterRadii(int t, int c, int dz, Point3D pc, Point3D pr) {

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

        ImagePlus imp = new OpenerExtensions().openCroppedStackCenterRadii(directory, infos[c][t], dz, pc, pr);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        } else {
            return imp;
        }
    }

    public ImagePlus getFullFrame(int t, int c) {
        Point3D po, ps;

        po = new Point3D(0, 0, 0);
        if(infos[0][0][0].isCropped) {
            ps = infos[0][0][0].getCropSize();
        } else {
            ps = new Point3D(nX, nY, nZ);
        }

        return(getCroppedFrameOffsetSize(t, c, 1, po, ps));

    }

    public ImagePlus getCroppedFrameOffsetSize(int t, int c, int dz, Point3D po, Point3D ps) {

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getCroppedFrameOffsetSize");
            log("t: "+t);
            log("c: "+c);
        }

        FileInfoSer fi = infos[0][0][0];

        if(fi.isCropped) {
            po = po.add(fi.getCropOffset());
        }

        ImagePlus imp = new OpenerExtensions().openCroppedStackOffsetSize(directory, infos[c][t], dz, po, ps);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        } else {
            return imp;
        }
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

