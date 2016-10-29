package ct.vss; /**
 * Created by tischi on 27/10/16.
 */

import java.awt.image.*;
import ij.*;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import static ij.IJ.log;

/**
 This class represents an array of disk-resident images.
 */
public class VirtualStackOfStacks extends ImageStack {

    static final int INITIAL_SIZE = 100;
    String path;
    int depth;
    int nSlices;
    int nStacks;
    String[] names;
    FileInfo fiRef;
    FileInfo[] info;

    /** Creates a new, empty virtual stack. */
    public VirtualStackOfStacks(int width, int height, int depth, ColorModel cm, String path, FileInfo fi, FileInfo[] info) {
        super(width, height, cm);
        this.path = path;
        this.depth = depth;
        this.info = info;
        this.fiRef = fi;
        this.info = info;
        names = new String[INITIAL_SIZE];
    }

    /** Adds an stack to the end of the stack. */
    public void addStack(String name) {
        if (name==null)
            throw new IllegalArgumentException("'name' is null!");
        nSlices = nSlices + depth;
        nStacks ++;
        //log("adding stack " + nStacks + ":" + name);
        //IJ.log("total number of slices:"+nSlices);
        if (nStacks==names.length) {
            String[] tmp = new String[nStacks*2];
            System.arraycopy(names, 0, tmp, 0, nStacks);
            names = tmp;
        }
        names[nStacks-1] = name;
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
            names[i-1] = names[i];
        names[nSlices-1] = null;
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
        int nFile, nSlice;
        // get z-th slice of a tif stack
        nFile = (n-1) /depth;
        nSlice = n - nFile * depth;
        // IJ.log("requested slice: "+n);
        log("opening slice " + nSlice + " of " + path + names[nFile]);
        // potentially check and adapt first offset again by loading the first IFD of this file
        // ...
        //log("opening slices " + z + " to " + (z+nz-1) + " of " + path + names[t]);
        FileInfo fi = (FileInfo) fiRef.clone(); // make a deep copy so we can savely modify it to load what we want
        fi.directory = path;
        fi.fileName = names[nFile];
        ImagePlus imp = new OpenerExtensions().openCroppedTiffStackUsingFirstIFD(fi, nSlice);
        if (imp!=null) {
            int w = imp.getWidth();
            int h = imp.getHeight();
            int type = imp.getType();
            ColorModel cm = imp.getProcessor().getColorModel();
        } else {
            log("Error: loading failed!");
            return null;
        }
        return imp.getProcessor();
    }

    public ImagePlus getCroppedFrameAsImagePlus(int t, int c, int z, int nz, int x, int nx, int y, int ny) {

        //log("opening slices " + z + " to " + (z+nz-1) + " of " + path + names[t]);
        FileInfo fi = (FileInfo) fiRef.clone(); // make a deep copy so we can savely modify it to load what we want
        fi.directory = path;
        fi.fileName = names[t];
        ImagePlus imp = new OpenerExtensions().openCroppedTiffStackUsingFirstIFD(fi, z, nz, x, nx, y, ny);

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
        nFile = n / depth;
        return names[nFile];
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

