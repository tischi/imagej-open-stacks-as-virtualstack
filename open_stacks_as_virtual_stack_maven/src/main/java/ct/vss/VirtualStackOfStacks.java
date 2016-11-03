package ct.vss; /**
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
    int depth;
    int nSlices;
    int nStacks;
    protected FileInfo[][] infos;

    /** Creates a new, empty virtual stack. */
    public VirtualStackOfStacks(Point3D pSize) {
        super((int)pSize.getX(), (int)pSize.getY(), null);
        this.depth = (int)pSize.getZ();
        this.infos = new FileInfo[INITIAL_SIZE][];
    }

    public FileInfo[][] getFileInfos() {
        return(infos);
    }

    /** Adds an stack to the end of the stack. */
    public void addStack(FileInfo[] info) {
        if (info==null)
            throw new IllegalArgumentException("'info' is null!");
        nSlices = nSlices + depth;
        nStacks ++;
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
        int iFile, z;
        // get z-th slice of a tif stack
        iFile = (int) (n-1) / depth;
        //log("iFile: "+iFile); log("filename: "+infos[iFile][0].fileName);
        z = (n-1) - iFile * depth; // zero-based in my opener functions
        // IJ.log("requested slice: "+n);
        //log("opening slice " + nSlice + " of " + path + names[nFile]);
        // potentially check and adapt first offset again by loading the first IFD of this file
        // ...
        //log("opening slices " + z + " to " + (z+nz-1) + " of " + path + names[t]);
        //FileInfo fi = (FileInfo) fiRef.clone(); // make a deep copy so we can savely modify it to load what we want
        //fi.fileName = names[nFile];
        //ImagePlus imp = new OpenerExtensions().openCroppedTiffStackUsingFirstIFD(fi, z);
        //ImagePlus imp = new OpenerExtensions().openCroppedTiffStackUsingIFDs(infos[iFile], z);
        ImagePlus imp = new OpenerExtensions().openTiffStackSliceUsingIFDs(infos[iFile], z);

        if (imp==null) {
            //int w = imp.getWidth();
            //int h = imp.getHeight();
            //int type = imp.getType();
            //ColorModel cm = imp.getProcessor().getColorModel();
        //} else {
            log("Error: loading failed!");
            return null;
        }
        return imp.getProcessor();
    }

    public ImagePlus getCroppedFrameAsImagePlus(int t, int c, Point3D p, Point3D pr) {

        //log("opening slices " + z + " to " + (z+nz-1) + " of " + path + names[t]);
        //FileInfo fi = (FileInfo) fiRef.clone(); // make a deep copy so we can savely modify it to load what we want
        //fi.directory = path;

        ImagePlus imp = new OpenerExtensions().openCroppedTiffStackUsingIFDs(infos[t], p, pr);
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
        nFile = (n-1) / depth;
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
        return true;
    }

    /** Does nothing. */
    public void trim() {
    }

}

