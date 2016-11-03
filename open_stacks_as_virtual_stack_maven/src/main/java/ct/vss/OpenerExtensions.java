package ct.vss;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.*;
import javafx.geometry.Point3D;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.*;

import static ij.IJ.log;
import static ij.IJ.runMacroFile;
import static ij.IJ.save;

/** Opens the nth image of the specified TIFF stack.*/
class OpenerExtensions extends Opener {

    public OpenerExtensions() {

    }

    /*
    public ImagePlus openCroppedTiffStackUsingFirstIFD(FileInfo fi0, int z) {
        int nz = 1;
        int x = 0;
        int nx = fi0.width;
        int y = 0;
        int ny = fi0.height;
        ImagePlus imp = openCroppedTiffStackUsingFirstIFD(fi0, z, nz, x, nx,  y,  ny);
        return imp;
    }*/

    /** Returns an InputStream for the image described by this FileInfo. */
    // https://github.com/imagej/imagej1/blob/master/ij/io/FileOpener.java#L442
    public InputStream createInputStream(FileInfo fi) throws IOException, MalformedURLException {
        InputStream is = null;
        if (fi.inputStream!=null)
            is = fi.inputStream;
        else {
            if (fi.directory.length()>0 && !fi.directory.endsWith(Prefs.separator))
                fi.directory += Prefs.separator;
            File f = new File(fi.directory + fi.fileName);
            is = new FileInputStream(f);
        }
        return is;
    }

    void skip(InputStream in, long skipCount) throws IOException {
        if (skipCount > 0) {
            long bytesRead = 0;
            int skipAttempts = 0;
            long count;
            while (bytesRead < skipCount) {
                count = in.skip(skipCount - bytesRead);
                skipAttempts++;
                if (count == -1 || skipAttempts > 5) break;
                bytesRead += count;
                //IJ.log("skip: "+skipCount+" "+count+" "+bytesRead+" "+skipAttempts);
            }
        }
    }

    void read(InputStream in, byte[] buffer) {
        int bufferSize = buffer.length;
        int bufferCount = 0;
        int count;
        try {
            while (bufferCount < bufferSize) { // fill the buffer
                count = in.read(buffer, bufferCount, bufferSize - bufferCount);
                if (count == -1) {
                    if (bufferCount > 0)
                        for (int i = bufferCount; i < bufferSize; i++) buffer[i] = 0;
                    buffer = null;
                    return; //EOF Error
                }
                bufferCount += count;
            }
        } catch (IOException e) {
            IJ.log("" + e);
            buffer = null;
            return;
        }
    }

    public ImagePlus openCroppedTiffStackUsingFirstIFD(FileInfo fi0, Point3D p, Point3D pr) {

        log("# openCroppedTiffStackUsingFirstIFD");

        if (fi0==null) return null;

        // round the values
        int x = (int) (p.getX()+0.5);
        int y = (int) (p.getY()+0.5);
        int z = (int) (p.getZ()+0.5);
        int nx = 2 * (int) pr.getX() + 1;
        int ny = 2 * (int) pr.getY() + 1;
        int nz = 2 * (int) pr.getZ() + 1;

        log("filename: " + fi0.fileName);
        log("fi0.nImages: " + fi0.nImages);
        log("z,nz,x,nx,y,ny: " + z +","+ nz +","+ x +","+ nx +","+ y +","+ ny);

        if (z<0 || z>fi0.nImages)
            throw new IllegalArgumentException("z="+z+" is out of range");
        // do the same for nx and ny and so on

        //long startTime = System.currentTimeMillis();

        FileInfo fi = (FileInfo) fi0.clone(); // make a deep copy so we can savely modify it to load what we want
        long size = fi.width*fi.height*fi.getBytesPerPixel();
        fi.longOffset = fi.getOffset() + (z*(size+fi.gapBetweenImages));
        fi.longOffset = fi.longOffset + (y*fi.width+x)*fi.getBytesPerPixel();
        fi.offset = 0;
        fi.nImages = nz;
        fi.gapBetweenImages += (int) (fi.width-(x+nx-1));
        fi.gapBetweenImages += (int) (fi.height-(y+ny))*fi.width;
        fi.gapBetweenImages += (int) (y*fi.width);
        fi.gapBetweenImages += (int) (x-1);
        fi.gapBetweenImages *= fi.getBytesPerPixel();
        //log("  fi.gapBetweenImages: "+fi.gapBetweenImages);

        int[] newStripLengths = new int[ny];
        int[] newStripOffsets = new int[ny];
        for (int i=0; i<newStripLengths .length; i++) {
            newStripLengths[i] = nx * fi.getBytesPerPixel();
            newStripOffsets[i] = i * fi.width * fi.getBytesPerPixel();
        }

        fi.stripOffsets = newStripOffsets;
        fi.stripLengths = newStripLengths;
        fi.height = ny;
        fi.width = nx;

        FileOpener fo = new FileOpener(fi);
        ImagePlus imp = fo.open(false);
        //long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("opened in [ms]: " + elapsedTime);
        return imp;
    }

    public ImagePlus openTiffStackSliceUsingIFDs(FileInfo[] info, int z) {
        ImagePlus imp;

        long startTime = System.currentTimeMillis();
        FileOpener fo = new FileOpener(info[z]);
        imp = fo.open(false);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("Whole slice opened in [ms]: " + elapsedTime);

        return imp;
    }

    // todo: make special version when whole image is opened
    /*
    public ImagePlus openCroppedTiffStackUsingIFDs(FileInfo[] info, int z) {
        Point3D p = new Point3D(0,0,z);
        Point3D pr = new Point3D(info[0].width/2-0.5, info[0].height/2-0.5, 0); // to also open images with even widths correctly
        ImagePlus imp = openCroppedTiffStackUsingIFDs(info, p, pr);
        return imp;
    }*/

    public FileInfo[] cropFileInfo(FileInfo[] info, Point3D p, Point3D pr) {
        //log("OpenerExtensions.cropFileInfo:");

        // round the values
        int x = (int) (p.getX() - pr.getX() + 0.5);
        int y = (int) (p.getY() - pr.getY() + 0.5);
        int z = (int) (p.getZ() - pr.getZ() + 0.5);
        int nx = (int) (2 * pr.getX() + 1.0001);
        int ny = (int) (2 * pr.getY() + 1.0001);
        int nz = (int) (2 * pr.getZ() + 1.0001);

        //log("filename: " + info[0].fileName);
        //log("z,nz,x,nx,y,ny: " + z +","+ nz +","+ x +","+ nx +","+ y +","+ ny);
        //log("info.length: " + info.length);

        if (z<0 || z>info.length)
            throw new IllegalArgumentException("z="+z+" is out of range");
        // do the same for nx and ny and so on

        FileInfo[] infoModified = new FileInfo[nz];
        FileInfo fi = info[0];

        // adjusted gap
        //int addedGapBetweenImages = 0;
        //addedGapBetweenImages += (int) (fi.width-(x+nx-1));
        //addedGapBetweenImages += (int) (fi.height-(y+ny))*fi.width;
        //addedGapBetweenImages += (int) (y*fi.width);
        //addedGapBetweenImages += (int) (x-1);
        //addedGapBetweenImages *= fi.getBytesPerPixel();

        // todo check if this is slow and make faster
        for (int iz=z; iz<(z+nz); iz++){
            infoModified[iz-z] = (FileInfo) info[iz].clone();
            infoModified[iz-z].nImages = 1;
            infoModified[iz-z].longOffset = infoModified[iz-z].getOffset();
            //infoModified[iz-z].longOffset += (y*fi.width+x)*fi.getBytesPerPixel();
            infoModified[iz-z].longOffset += (y*fi.width+x)*fi.getBytesPerPixel();
            infoModified[iz-z].offset = 0;
            infoModified[iz-z].stripLengths = new int[ny];
            infoModified[iz-z].stripOffsets = new int[ny];
            for (int i=0; i<ny; i++) {
                infoModified[iz-z].stripLengths[i] = nx * fi.getBytesPerPixel();
            //    infoModified[iz-z].stripOffsets[i] = (int) infoModified[iz-z].getOffset() + ((((y+i)*fi.width) + x) * fi.getBytesPerPixel());
                infoModified[iz-z].stripOffsets[i] = (int) (i * fi.width * fi.getBytesPerPixel());
            }
            infoModified[iz-z].height = ny;
            infoModified[iz-z].width = nx;
            //log(""+(iz-z)+" "+info[iz].getOffset());
            //log(""+infoModified[iz-z].stripOffsets[0]);
            //log(""+(iz-z)+" "+infoModified[iz-z].getOffset());
        }
        //for(int i=0; i<infoModified.length; i++){
        //    log(""+infoModified[i].stripOffsets[0]);
        //}

        return(infoModified);
    }

    public ImagePlus openCroppedTiffStackUsingIFDs(FileInfo[] infoAll, Point3D p, Point3D pr) {
        //log("# openCroppedTiffStackUsingIFDs");
        long startTime, stopTime, elapsedTime;

        if (infoAll==null) return null;

        //for(int i=0; i<info.length; i++) {
        //    log(""+info[i].getOffset());
        //}

        FileInfo[] info = cropFileInfo(infoAll, p, pr);



        //ImagePlus imp = openTiffStack(infoModified);
        ImagePlus imp;
        ImageProcessor ip;
        ImageStack stack=null;
        FileOpener fo;
        FileInfo fi = info[0];


        // Read via one input stream
        startTime = System.currentTimeMillis();

        byte[] row = new byte[fi.width*fi.getBytesPerPixel()];
        short[] pixels = new short[fi.width*fi.height];
        int rowCount = 0;
        int pixelCount = 0;
        fi = info[0];
        try {
            InputStream in = createInputStream(fi);
            in.skip(fi.getOffset());
            for(int z=0; z<info.length; z++) {
                for (int y = 0; y < fi.stripOffsets.length; y++) {
                    read(in, row);
                    pixelCount = y * fi.width;
                    setPixels(pixels, pixelCount, row);
                    in.skip(fi.width);
                    if (row == null) {
                        log("Reading of row failed");
                    }
                    rowCount++;
                }

                ip = new ShortProcessor(fi.width, fi.height, (short[]) pixels, null);
                stack.addSlice(ip);
            }
            //imp = new ImagePlus(fi.fileName, ip);
            in.close();
        } catch (Exception e) {
            IJ.handleException(e);
        }
        log("Number of rows: "+rowCount);
        log("Input stream [ms]: " + (System.currentTimeMillis()-startTime));


        // Loop via multiple input streams
        startTime = System.currentTimeMillis();
        for(int i=0; i<info.length; i++) {
            fi = info[i];
            fo = new FileOpener(fi);
            imp = fo.open(false);
            if(i==0){
                stack = imp.getStack();
            } else {
                stack.addSlice(imp.getProcessor());
            }
        }
        imp = new ImagePlus("",stack);
        log("Multiple streams [ms]: " + (System.currentTimeMillis()-startTime));
        imp.show();

        //imp.show();
        return imp;
    }


}

