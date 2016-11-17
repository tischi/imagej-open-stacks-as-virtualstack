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
    int nZ, nC, nT;
    protected FileInfoSer[][][] infos;  // c, t, z
    protected String fileType = "tiff"; // h5

    /** Creates a new, empty virtual stack of required size */
    public VirtualStackOfStacks(Point3D pSize, int nC, int nT, String fileType) {
        super((int)pSize.getX(), (int)pSize.getY(), null);
        this.nZ = (int)pSize.getZ();
        this.nC = nC;
        this.nT = nT;
        this.fileType = fileType;

        if(Globals.verbose) {
            log("# VirtualStackOfStacks");
            log("x: "+(int)pSize.getX());
            log("y: "+(int)pSize.getY());
            log("z: "+(int)pSize.getZ());
            log("c: "+nC);
            log("t: "+nT);
        }
        this.infos = new FileInfoSer[nC][nT][];
        nSlices = nC*nT*nZ;
    }

    public VirtualStackOfStacks(FileInfoSer[][][] infos) {
        this.infos = infos;
        nC = infos.length;
        nT = infos[0].length;
        nZ = infos[0][0].length;
        nSlices = nC*nT*nZ;
        if(infos[0][0][0].fileName.endsWith(".h5"))
            this.fileType = "h5";
        if(infos[0][0][0].fileName.endsWith(".tif"))
            this.fileType = "tif";

    }


    public FileInfoSer[][][] getFileInfosSer() {
        return(infos);
    }

    /** Adds an image stack. */
    public void addStack(FileInfoSer[] info, int t, int c) {
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
        ImagePlus imp = null;

        n -= 1;

        int c = (n % nC);
        int z = ((n-c)%(nZ*nC))/nC;
        int t = (n-c-z*nC)/(nZ*nC);

        FileInfoSer[] info = infos[c][t];

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getProcessor");
            log("nZ: "+nZ);
            log("nC: "+nC);
            log("nT: "+nT);
            log("requested slice: "+n);
            log("c: "+c);
            log("z: "+z);
            log("t: "+t);
            log("opening file: "+info[0].fileName);
            log("opening z-slice [one-based]: "+(z+1));
        }

        if(fileType == "tif")
            imp = new OpenerExtensions().openCroppedTiffStackUsingIFDs(info, z, z, 1, 1, 0, info[0].width - 1, 0, info[0].height - 1);
        if (fileType == "h5")
            imp = new OpenerExtensions().openCroppedH5stack(info, z, z, 1, 1, 0, info[0].width - 1, 0, info[0].height - 1);
        if (imp==null) {
            log("Error: loading failed!");
            return null;
        }
        return imp.getProcessor();
    }

    public ImagePlus getCroppedFrameAsImagePlus(int t, int c, int dz, Point3D p, Point3D pr) {
        int iFile = 0;
        FileInfoSer[] info = infos[c][t];

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getCroppedFrameAsImagePlus");
            log("t: "+t);
            log("c: "+c);
            log("directory: "+info[0].directory);
            log("filename: "+info[0].fileName);
        }

        ImagePlus imp = new OpenerExtensions().openCroppedStack(info, dz, p, pr);
        if (imp==null) {
            log("Error: loading failed!");
            return null;
        } else {
            return imp;
        }
    }

    /** Returns the number of slices in this stack. */
    public int getSize() {
        return nSlices;
    }

    /** Returns the number of stacks in this stack. */
    public int getNStacks() {
        return nT*nC;
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

