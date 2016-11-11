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
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;


import static ij.IJ.log;
import static ij.IJ.runMacroFile;
import static ij.IJ.save;

/** Opens the nth image of the specified TIFF stack.*/
class OpenerExtensions extends Opener {

    // Compression modes
    public static final int COMPRESSION_UNKNOWN = 0;
    public static final int COMPRESSION_NONE= 1;
    public static final int LZW = 2;
    public static final int LZW_WITH_DIFFERENCING = 3;
    public static final int JPEG = 4;
    public static final int PACK_BITS = 5;

    public OpenerExtensions() {

    }

    long skip(InputStream in, long skipCount, long pointer) throws IOException {
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
        return(pointer+skipCount);
    }

    long read(InputStream in, byte[] buffer, long pointer) {
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
                    return(-1); //EOF Error
                }
                bufferCount += count;
            }
        } catch (IOException e) {
            IJ.log("" + e);
            buffer = null;
            return(-1);
        }
        //log("read: buffer.length "+buffer.length);
        return(pointer+(long)bufferSize);
    }

    long skip(FileImageInputStream in, long skipCount, long pointer) throws IOException {
        in.seek(pointer+skipCount);
        return(pointer+skipCount);
    }

    long read(FileImageInputStream in, byte[] buffer, long pointer) {
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
                    return(-1); //EOF Error
                }
                bufferCount += count;
            }
        } catch (IOException e) {
            IJ.log("" + e);
            buffer = null;
            return(-1);
        }
        //log("read: buffer.length "+buffer.length);
        return(pointer+(long)bufferSize);
    }

    void setShortPixels(FileInfo fi, short[] pixels, int base, byte[] buffer){
        int bytesPerPixel = 2;
        int pixelsRead = (int) buffer.length/bytesPerPixel;
        //log("setShortPixels: base "+base);
        //log("setShortPixels: pixelsRead "+pixelsRead);
        //log("setShortPixels: pixels.length "+pixels.length);

        if (fi.intelByteOrder) {
            if (fi.fileType==FileInfo.GRAY16_SIGNED)
                for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
                    pixels[i] = (short)((((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff))+32768);
            else
                for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
                    pixels[i] = (short)(((buffer[j+1]&0xff)<<8) | (buffer[j]&0xff));
        } else {
            if (fi.fileType==FileInfo.GRAY16_SIGNED)
                for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
                    pixels[i] = (short)((((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff))+32768);
            else
                for (int i=base,j=0; i<(base+pixelsRead); i++,j+=2)
                    pixels[i] = (short)(((buffer[j]&0xff)<<8) | (buffer[j+1]&0xff));
        }
    }

    void setShortPixelsFromAllStrips(FileInfo fi, short[] pixels, int imByteWidth, byte[] buffer){
        int nz = fi.stripLengths.length;
        int ip = 0;
        int bs, be;

        for(int z=0; z<nz; z++ ) {
            bs = z * imByteWidth;
            be = bs + fi.stripLengths[0];

            if (fi.intelByteOrder) {
                if (fi.fileType == FileInfo.GRAY16_SIGNED)
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) ((((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff)) + 32768);
                else
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) (((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff));
            } else {
                if (fi.fileType == FileInfo.GRAY16_SIGNED)
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) ((((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff)) + 32768);
                else
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) (((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff));
            }
        }
    }

    public ImagePlus openTiffStackSliceUsingIFDs(FileInfo[] info, int z) {
        ImagePlus imp;
        long startTime = System.currentTimeMillis();
        FileOpener fo = new FileOpener(info[z]);
        imp = fo.open(false);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime;
        if(Globals.verbose) {
            log("OpenerExtension.openTiffStackSliceUsingIFDs");
            log("Whole slice opened in [ms]: " + elapsedTime);
            log("Reading speed [MB/s]: " + (info[z].width*info[z].height*info[z].getBytesPerPixel())/((elapsedTime+0.001)*1000));
        }

        return imp;
    }

    public FileInfo[] cropFileInfo(FileInfo[] info, int dz, Point3D p, Point3D pr) {
        //log("OpenerExtensions.cropFileInfo:");

        // round the values
        /*
        int x = (int) (p.getX() - pr.getX());
        int y = (int) (p.getY() - pr.getY());
        int z = (int) (p.getZ() - pr.getZ());
        int nx = (int) (2.0 * pr.getX() + 1.5001); // to enable evenly sized stacks
        int ny = (int) (2.0 * pr.getY() + 1.5001);
        int nz = (int) ((2.0 * pr.getZ() / dz + 1.5001) );
        */
        int x = (int) (p.getX()+0.5);
        int y = (int) (p.getY()+0.5);
        int z = (int) (p.getZ()+0.5);
        int rx = (int) (pr.getX()+0.5);
        int ry = (int) (pr.getY()+0.5);
        int rz = (int) (pr.getZ()+0.5);
        int nx = (int) (2 * rx + 1);
        int ny = (int) (2 * ry + 1);
        int nz = (int) (2 * rz + 1);
        x=x-rx;
        y=y-ry;
        z=z-rz;

        if(dz>1) {
            nz = (int) (1.0*nz/dz + 0.5);
        }

        if(Globals.verbose) {
            log("# OpenerExtension.cropFileInfo:");
            log("filename: " + info[0].fileName);
            log("dz: " + dz);
            log("rx,ry,rz: " + pr.getX() + "," + pr.getY() + "," + pr.getZ());
            log("z,nz,x,nx,y,ny: " + z + "," + nz + "," + x + "," + nx + "," + y + "," + ny);
            log("info.length: " + info.length);
        }

        FileInfo[] infoModified = new FileInfo[nz];
        FileInfo fi = info[0];

        for (int iz=z, jz=z; iz<(z+nz); iz++, jz+=dz){
            if (jz<0 || jz>=info.length) {
                IJ.showMessage("z=" + jz + " is out of range. Please reduce your z-radius.");
                throw new IllegalArgumentException("z=" + jz + " is out of range; iz="+iz);
            }
            infoModified[iz-z] = (FileInfo) info[jz].clone();
            infoModified[iz-z].nImages = 1;
            infoModified[iz-z].longOffset = infoModified[iz-z].getOffset();
            infoModified[iz-z].offset = 0;
            infoModified[iz-z].stripLengths = new int[ny];
            infoModified[iz-z].stripOffsets = new int[ny];
            for (int i=0; i<ny; i++) {
                infoModified[iz-z].stripLengths[i] = nx * fi.getBytesPerPixel();
                infoModified[iz-z].stripOffsets[i] = (int) infoModified[iz-z].getOffset() + ((((y+i)*fi.width) + x) * fi.getBytesPerPixel());
                //infoModified[iz-z].stripOffsets[i] = (int) (i * fi.width * fi.getBytesPerPixel());
            }
            infoModified[iz-z].height = ny;
            infoModified[iz-z].width = nx;
            // next line is necessary for the IJ TiffReader, should not be
            infoModified[iz-z].longOffset += (y*fi.width+x)*fi.getBytesPerPixel();
            //log(""+(iz-z)+" "+info[iz].getOffset());
            //log(""+infoModified[iz-z].stripOffsets[0]);
            //log(""+(iz-z)+" "+infoModified[iz-z].getOffset());
        }
        //for(int i=0; i<infoModified.length; i++){
        //    log(""+infoModified[i].stripOffsets[0]);
        //}

        return(infoModified);
    }

    public ImagePlus openCroppedTiffStackUsingIFDs(FileInfo[] info, int dz, Point3D p, Point3D pr) {
        long startTime;
        boolean hasStrips = false;
        boolean isCompressed = false;

        if (info==null) return null;
        FileInfo fi = info[0];

        // compute ranges to be loaded
        int xc = (int) (p.getX()+0.5);
        int yc = (int) (p.getY()+0.5);
        int zc = (int) (p.getZ()+0.5);
        int rx = (int) (pr.getX()+0.5);
        int ry = (int) (pr.getY()+0.5);
        int rz = (int) (pr.getZ()+0.5);
        int xs = xc-rx;
        int ys = yc-ry;
        int zs = zc-rz;
        int xe = xc+rx;
        int ye = yc+ry;
        int ze = zc+rz;
        int nx = xe - xs + 1;
        int ny = ye - ys + 1;
        int nz = ze - zs + 1;

        if(dz>1) {
            nz = (int) (1.0*nz/dz + 0.5);
        }


        // only needed for uncompress, but does not hurt to instantiate anyway
        ImageReader reader = new ImageReader(fi);

        if(Globals.verbose) {
            log("# openCroppedTiffStackUsingIFDs");
            log("directory: " + info[0].directory);
            log("filename: " + info[0].fileName);
            log("rx,ry,rz: " + pr.getX() + "," + pr.getY() + "," + pr.getZ());
            log("zs,dz,ze,nz,xs,xe,ys,ye: " + zs + "," + dz + "," + ze + "," + nz + "," + xs + "," + xe + "," + ys + "," + ye);
            log("info.length: " + info.length);

        }


        // get size of image before cropping
        int imByteWidth = fi.width*fi.getBytesPerPixel();

        FileOpener fo;

        ImageStack stackStream = new ImageStack(info[0].width,info[0].height);
        ImageProcessor ip;

        int stripPixelLength = (int) fi.stripLengths[0]/fi.getBytesPerPixel();
        int stripByteLength = (int) fi.stripLengths[0];
        byte[] strip = new byte[fi.stripLengths[0]];
        byte[] buffer = new byte[ny*imByteWidth];

        short[][] pixels = new short[nz][nx*ny];


        long skippingTime = 0;
        long readingTime = 0;
        long uncompressTime = 0;
        long settingStackTime = 0;
        long settingPixelsTime = 0;
        long bufferReadingTime = 0;


        //log("strip.length "+strip.length);
        //log("pixels.length "+pixels.length);
        //log("imByteWidth "+imByteWidth);

        long startTimeInputStream = System.currentTimeMillis();
        String openMethod = "allStrips";
        //openMethod = "stripByStrip";

        try {
            // get input stream to file
            File f = new File(fi.directory + fi.fileName);
            InputStream in = new FileInputStream(f);
            //InputStream in = new BufferedInputStream(new FileInputStream(f));
            //FileImageInputStream in = new FileImageInputStream(f);

            int readLength;
            long pointer=0L;

            for(int z=zs; z<=ze; z+=dz) {

                if (z<0 || z>=info.length) {
                    IJ.showMessage("z=" + z + " is out of range. Please reduce your z-radius.");
                    throw new IllegalArgumentException("z=" + z + " is out of range");
                }

                // get current FileInfo
                fi = info[z];

                if((fi.stripOffsets!=null&&fi.stripOffsets.length>1)) {
                    hasStrips = true;
                }

                if((fi.compression>0)) {
                    isCompressed = true;
                }


                if(Globals.verbose) {
                    log("z-plane " + z);
                    log("hasStrips: " + hasStrips);
                    log("isCompressed: " + isCompressed);
                }

                if(hasStrips) {

                    // skip to first strip of this z-plane
                    startTime = System.currentTimeMillis();
                    pointer = skip(in, info[z].stripOffsets[ys] - pointer, pointer);
                    skippingTime += (System.currentTimeMillis() - startTime);

                    // compute read length
                    readLength = 0;
                    for (int y = ys; y <= ye; y++) {
                        readLength += fi.stripLengths[y];
                    }

                    log("read: " + hasStrips);

                    // read all data
                    startTime = System.currentTimeMillis();
                    buffer = new byte[readLength];
                    pointer = read(in, buffer, pointer);
                    readingTime += (System.currentTimeMillis() - startTime);

                    log("buffer.length: " + buffer.length);

                    // deal with compression


                    if(fi.compression == LZW) {
                        for (int y = ys; y <= ye; y++) {
                            System.arraycopy(array, 0, data, size, length);

                            readLength += fi.stripLengths[y];
                            buffer = reader.lzwUncompress(buffer);
                    }


                    }
                    log("buffer.length: " + buffer.length);

                    // store strips in pixel array
                    startTime = System.currentTimeMillis();
                    setShortPixelsFromAllStrips(fi, pixels[z], imByteWidth, buffer);
                    settingPixelsTime += (System.currentTimeMillis() - startTime);

                } else {

                    // ...

                }

            } // z
            
            in.close();

        } catch (Exception e) {
            IJ.handleException(e);
        }


        if(Globals.verbose) {
            int byteRead = nz*(xe-xs)*(ye-ys)*fi.getBytesPerPixel();
            log("OpenerExtensions.openCroppedTiffStackUsingIFDs");
            log("Skipping [ms]: " + skippingTime);
            log("Reading [ms]: " + readingTime);
            log("Effective reading speed [MB/s]: " + byteRead/((readingTime+0.001)*1000));
            log("UnCompress [ms]" + uncompressTime);
            log("Setting pixels [ms]: " + settingPixelsTime);
            log("Setting stack [ms]: " + settingStackTime);
        }

        ImagePlus impStream = new ImagePlus("One stream",stackStream);

        return impStream;
    }


}


/*


    public ImagePlus OLDopenCroppedTiffStackUsingIFDs(FileInfo[] infoAll, int dz, Point3D p, Point3D pr) {
        //log("# openCroppedTiffStackUsingIFDs");
        long startTime, stopTime, elapsedTime;
        FileInfo[] info;

        if (infoAll==null) return null;
        FileInfo fi = infoAll[0];

        // get size of image before cropping
        int imByteWidth = infoAll[0].width*infoAll[0].getBytesPerPixel();

        // add strips to the FileInfo to enable reading of a subset of the data
        // todo: as this turns out to be slow anyway, i could get rid of it and just compute the
        // start and end point of the region that is to be read
        if((fi.stripOffsets!=null&&fi.stripOffsets.length>1)) {
            info = infoAll;
        } else { // no strips present, need to add my own ones
            info = cropFileInfo(infoAll, dz, p, pr);
        }

        FileOpener fo;
        FileInfo fi = info[0];

        // Read via one input stream

        ImageStack stackStream = new ImageStack(info[0].width,info[0].height);
        ImageProcessor ip;

        int stripPixelLength = (int) fi.stripLengths[0]/fi.getBytesPerPixel();
        int stripByteLength = (int) fi.stripLengths[0];
        int nz = info.length;
        int ny = fi.stripOffsets.length;
        byte[] strip = new byte[fi.stripLengths[0]];
        byte[] buffer = new byte[ny*imByteWidth];

        short[][] pixels = new short[nz][fi.width*fi.height];
        int pixelCount;
        long pointer = 0L;
        fi = info[0];

        long skippingTime = 0;
        long readingTime = 0;
        long settingStackTime = 0;
        long settingPixelsTime = 0;
        long bufferReadingTime = 0;


        //log("strip.length "+strip.length);
        //log("pixels.length "+pixels.length);
        //log("imByteWidth "+imByteWidth);

        long startTimeInputStream = System.currentTimeMillis();
        String openMethod = "allStrips";
        //openMethod = "stripByStrip";

        try {
            File f = new File(fi.directory + fi.fileName);
            //InputStream in = new BufferedInputStream(new FileInputStream(f));
            InputStream in = new FileInputStream(f);
            //FileImageInputStream in = new FileImageInputStream(f);

            for(int z=0; z<nz; z++) {

                if(openMethod == "stripByStrip") {

                    for (int y = 0; y < ny; y++) {

                        // skip to strip
                        startTime = System.currentTimeMillis();
                        pointer = skip(in, info[z].stripOffsets[y] - pointer, pointer);
                        skippingTime += (System.currentTimeMillis() - startTime);

                        // read strip
                        startTime = System.currentTimeMillis();
                        pointer = read(in, strip, pointer);
                        readingTime += (System.currentTimeMillis() - startTime);

                        // store strip in pixel array
                        startTime = System.currentTimeMillis();
                        pixelCount = y * stripPixelLength;
                        setShortPixels(fi, pixels[z], pixelCount, strip);
                        settingPixelsTime += (System.currentTimeMillis() - startTime);

                    }

                    // add pixels to stack
                    startTime = System.currentTimeMillis();
                    ip = new ShortProcessor(fi.width, fi.height, (short[]) pixels[z], null);
                    stackStream.addSlice(ip);
                    settingStackTime += (System.currentTimeMillis()-startTime);

                } else if (openMethod == "allStrips") {

                    // skip to first strip
                    startTime = System.currentTimeMillis();
                    pointer = skip(in, info[z].stripOffsets[0] - pointer, pointer);
                    skippingTime += (System.currentTimeMillis() - startTime);

                    // read all strips
                    startTime = System.currentTimeMillis();
                    pointer = read(in, buffer, pointer);
                    readingTime += (System.currentTimeMillis() - startTime);

                    // store strips in pixel array
                    startTime = System.currentTimeMillis();
                    setShortPixelsFromAllStrips(fi, pixels[z], imByteWidth, buffer);
                    settingPixelsTime += (System.currentTimeMillis() - startTime);

                    // add pixels to stack
                    startTime = System.currentTimeMillis();
                    ip = new ShortProcessor(fi.width, fi.height, (short[]) pixels[z], null);
                    stackStream.addSlice(ip);
                    settingStackTime += (System.currentTimeMillis()-startTime);

                }

            } // z

            in.close();
        } catch (Exception e) {
            IJ.handleException(e);
        }


        if(Globals.verbose) {
            int byteRead = nz*fi.width*fi.height*fi.getBytesPerPixel();
            log("OpenerExtensions.openCroppedTiffStackUsingIFDs");
            log("Skipping [ms]: " + skippingTime);
            log("Reading [ms]: " + readingTime);
            log("Reading speed [MB/s]: " + byteRead/((readingTime+0.001)*1000));
            log("Setting pixels [ms]: " + settingPixelsTime);
            log("Setting stack [ms]: " + settingStackTime);
        }

        ImagePlus impStream = new ImagePlus("One stream",stackStream);

        return impStream;
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

 */
