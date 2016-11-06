package ct.vss;

/**
 * Created by tischi on 27/10/16.
 */

import java.awt.image.*;
import ij.*;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import static ij.IJ.log;
import javafx.geometry.Point3D;

/**
 This class represents an array of disk-resident images.
 */
public class VirtualStackOfStacks extends ImageStack {

    static final int INITIAL_SIZE = 100;
    int nSlices;
    int nStacks;
    int nZ, nC, nT;

    String order; // "ct", "tc"
    protected FileInfo[][] infos;

    /** Creates a new, empty virtual stack. */
    public VirtualStackOfStacks(Point3D pSize, int nC, String order) {
        super((int)pSize.getX(), (int)pSize.getY(), null);
        this.nZ = (int)pSize.getZ();
        this.nC = nC;
        this.order = order;
        if(Globals.verbose) {
            log("VirtualStackOfStacks");
            log("x: "+(int)pSize.getX());
            log("y: "+(int)pSize.getY());
            log("z: "+(int)pSize.getZ());
        }
        this.infos = new FileInfo[INITIAL_SIZE][];
    }

    public FileInfo[][] getFileInfos() {
        return(infos);
    }

    /** Adds an stack to the end of the stack. */
    public void addStack(FileInfo[] info) {
        if (info==null)
            throw new IllegalArgumentException("'info' is null!");
        nSlices = nSlices + nZ;
        nStacks ++;
        nT = (int) (nStacks/nC);
        if (nStacks==infos.length) {
            FileInfo[][] tmp_infos = new FileInfo[nStacks*2][];
            System.arraycopy(infos, 0, tmp_infos, 0, nStacks);
            infos = tmp_infos;
        }
        infos[nStacks-1] = info;
        //("Added file: "+infos[nStacks-1][0].fileName);
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
            infos[i-1] = infos[i];
        infos[nSlices-1] = null;
        nSlices--;
    }

    /** Deletes the last slice in the stack. */
    public void deleteLastSlice() {
        if (nSlices>0)
            deleteSlice(nSlices);
    }

    public String getOrder() {
        return(order);
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
        n -= 1;

        int c = (n % nC);
        int z = ((n-c)%(nZ*nC))/nC;
        int t = (n-c-z*nC)/(nZ*nC);

        int iFile = 0;
        if(order=="tc") {
            iFile = t + (nT*c);
        }
        if(order=="ct") {
            iFile = (t*nC) + c;
        }

        if(Globals.verbose) {
            log("# VirtualStackOfStacks.getProcessor");
            log("order: "+order);
            log("nZ: "+nZ);
            log("nC: "+nC);
            log("nT: "+nT);
            log("requested slice: "+n);
            log("c: "+c);
            log("z: "+z);
            log("t: "+t);
            log("opening iFile [zero-based]: "+iFile);
            log("opening filename: "+infos[iFile][0].fileName);
            log("opening z-slice [one-based]: "+(z+1));
        }

        ImagePlus imp = new OpenerExtensions().openTiffStackSliceUsingIFDs(infos[iFile], z);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        }
        return imp.getProcessor();
    }

    public ImagePlus getCroppedFrameAsImagePlus(int t, int c, int dz, Point3D p, Point3D pr) {

        //log("opening slices " + z + " to " + (z+nz-1) + " of " + path + names[t]);
        //FileInfo fi = (FileInfo) fiRef.clone(); // make a deep copy so we can savely modify it to load what we want
        //fi.directory = path;

        ImagePlus imp = new OpenerExtensions().openCroppedTiffStackUsingIFDs(infos[t], dz, p, pr);
        //ImagePlus imp = new OpenerExtensions().openCroppedTiffStackUsingFirstIFD(fi, z, nz, x, nx, y, ny);

        if (imp==null) {
            log("Error: loading failed!");
            return null;
        }
        return imp;
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
        nFile = (n-1) / nZ;
        return infos[nFile][0].fileName;
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

