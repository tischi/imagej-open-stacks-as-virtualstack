package ct.vss;

import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.BitBuffer;
import ij.io.Opener;
import javafx.geometry.Point3D;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static ij.IJ.log;

// hdf5:  http://svnsis.ethz.ch/doc/hdf5/hdf5-14.12/

/** Opens the nth image of the specified TIFF stack.*/
class OpenerExtensions extends Opener {

    // Compression modes
    public static final int COMPRESSION_UNKNOWN = 0;
    public static final int COMPRESSION_NONE = 1;
    public static final int LZW = 2;
    public static final int LZW_WITH_DIFFERENCING = 3;
    public static final int JPEG = 4;
    public static final int PACK_BITS = 5;
    private static final int CLEAR_CODE = 256;
    private static final int EOI_CODE = 257;

    /** 8-bit unsigned integer (0-255). */
    public static final int GRAY8 = 0;

    /** 16-bit signed integer (-32768-32767). Imported signed images
     are converted to unsigned by adding 32768. */
    public static final int GRAY16_SIGNED = 1;

    /** 16-bit unsigned integer (0-65535). */
    public static final int GRAY16_UNSIGNED = 2;

    /** 32-bit signed integer. Imported 32-bit integer images are
     converted to floating-point. */
    public static final int GRAY32_INT = 3;

    /** 32-bit floating-point. */
    public static final int GRAY32_FLOAT = 4;

    /** 8-bit unsigned integer with color lookup table. */
    public static final int COLOR8 = 5;

    /** 24-bit interleaved RGB. Import/export only. */
    public static final int RGB = 6;

    /** 24-bit planer RGB. Import only. */
    public static final int RGB_PLANAR = 7;

    /** 1-bit black and white. Import only. */
    public static final int BITMAP = 8;

    /** 32-bit interleaved ARGB. Import only. */
    public static final int ARGB = 9;

    /** 24-bit interleaved BGR. Import only. */
    public static final int BGR = 10;

    /** 32-bit unsigned integer. Imported 32-bit integer images are
     converted to floating-point. */
    public static final int GRAY32_UNSIGNED = 11;

    /** 48-bit interleaved RGB. */
    public static final int RGB48 = 12;

    /** 12-bit unsigned integer (0-4095). Import only. */
    public static final int GRAY12_UNSIGNED = 13;

    /** 24-bit unsigned integer. Import only. */
    public static final int GRAY24_UNSIGNED = 14;

    /** 32-bit interleaved BARG (MCID). Import only. */
    public static final int BARG  = 15;

    /** 64-bit floating-point. Import only.*/
    public static final int GRAY64_FLOAT  = 16;

    /** 48-bit planar RGB. Import only. */
    public static final int RGB48_PLANAR = 17;

    /** 32-bit interleaved ABGR. Import only. */
    public static final int ABGR = 18;

    /** 32-bit interleaved CMYK. Import only. */
    public static final int CMYK = 19;

    // uncompress
    byte[][] symbolTable = new byte[4096][1];

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
        return (pointer + skipCount);
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
                    return (-1); //EOF Error
                }
                bufferCount += count;
            }
        } catch (IOException e) {
            IJ.log("" + e);
            buffer = null;
            return (-1);
        }
        //log("read: buffer.length "+buffer.length);
        return (pointer + (long) bufferSize);
    }

    // todo: make this faster and easier by removing all the if statements?!
    public void setShortPixelsFromAllStrips(FileInfoSer fi, short[] pixels, int ys, int ny, int xs, int nx, int imByteWidth, byte[] buffer) {
        int ip = 0;
        int bs, be;

        for (int y = ys; y < ys + ny; y++) {

            bs = y * imByteWidth + xs * fi.bytesPerPixel;
            be = bs + nx * fi.bytesPerPixel;

            if (fi.intelByteOrder) {
                if (fi.fileType == GRAY16_SIGNED)
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) ((((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff)) + 32768);
                else
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) (((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff));
            } else {
                if (fi.fileType == GRAY16_SIGNED)
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) ((((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff)) + 32768);
                else
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) (((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff));
            }
        }
    }

    private long readTiffOnePlaneCroppedRows(FileInfoSer fi, FileInputStream in, long pointer, byte[][] buffer, int z, int zs, int ys, int ye) {
        boolean hasStrips = false;
        int readLength;
        long readStart;

        if ((fi.stripOffsets != null && fi.stripOffsets.length > 1)) {
            hasStrips = true;
        }

        /*
        if(Globals.verbose) {
            log("# readFromPlane");
            log("z-plane " + z);
            log("hasStrips: " + hasStrips);
        }*/

        try {

            if (hasStrips) {

                // convert rows to strips
                int rps = fi.rowsPerStrip;
                int ss = (int) ys / rps;
                int se = (int) ye / rps;
                readStart = fi.stripOffsets[ss];

                readLength = 0;
                for (int s = ss; s <= se; s++) {
                    readLength += fi.stripLengths[s];
                }

            } else {  // no strips

                // convert rows to bytes
                readStart = fi.longOffset + ys * fi.width * fi.bytesPerPixel;
                readLength =  (int) (fi.longOffset + ((ye-ys)+1) * fi.width * fi.bytesPerPixel - readStart);

            }

            // SKIP to first strip (row) that we need to read of this z-plane
            //startTime = System.currentTimeMillis();
            pointer = skip(in, readStart - pointer, pointer);
            //skippingTime += (System.currentTimeMillis() - startTime);

            // READ data
            //startTime = System.currentTimeMillis();
            buffer[z-zs] = new byte[readLength];
            pointer = read(in, buffer[z-zs], pointer);
            //readingTime += (System.currentTimeMillis() - startTime);


        } catch (Exception e) {
            IJ.handleException(e);
        }


        return (pointer);

    }

    public ImagePlus openCroppedStackOffsetSize(FileInfoSer[] info, int dz, Point3D po, Point3D ps) {

        // compute ranges to be loaded

        int xs = (int) (po.getX() + 0.5);
        int ys = (int) (po.getY() + 0.5);
        int zs = (int) (po.getZ() + 0.5);
        int xe = xs + (int) (ps.getX() + 0.5) - 1;
        int ye = ys + (int) (ps.getY() + 0.5) - 1;
        int ze = zs + (int) (ps.getZ() + 0.5) - 1;

        int nz = (int) ps.getZ();

        if (dz > 1) {
            nz = (int) (1.0 * nz / dz + 0.5);
        }

        ImagePlus imp = null;
        if(info[0].fileTypeString.equals("tif")) {
            imp = openCroppedTiffStackUsingIFDs(info, zs, ze, nz, dz, xs, xe, ys, ye);
        } else if(info[0].fileTypeString.equals("h5")) {
            imp = openCroppedH5stack(info, zs, ze, nz, dz, xs, xe, ys, ye);
        } else {
            IJ.showMessage("unsupported file type: "+info[0].fileTypeString);
        }

        return(imp);

    }

    public ImagePlus openCroppedStackCenterRadii(FileInfoSer[] info, int dz, Point3D pc, Point3D pr) {

        // compute ranges to be loaded
        int xc = (int) (pc.getX() + 0.5);
        int yc = (int) (pc.getY() + 0.5);
        int zc = (int) (pc.getZ() + 0.5);
        int rx = (int) (pr.getX() + 0.5);
        int ry = (int) (pr.getY() + 0.5);
        int rz = (int) (pr.getZ() + 0.5);
        int xs = xc - rx;
        int ys = yc - ry;
        int zs = zc - rz;
        int xe = xc + rx;
        int ye = yc + ry;
        int ze = zc + rz;
        int nz = ze - zs + 1;

        if (dz > 1) {
            nz = (int) (1.0 * nz / dz + 0.5);
        }

        ImagePlus imp = null;

        if(info[0].fileTypeString == "tif")
            imp = openCroppedTiffStackUsingIFDs(info, zs, ze, nz, dz, xs, xe, ys, ye);
        else if(info[0].fileTypeString == "h5")
            imp = openCroppedH5stack(info, zs, ze, nz, dz, xs, xe, ys, ye);

        return(imp);

    }

    // todo make Points from the ints
    public ImagePlus openCroppedH5stack(FileInfoSer[] info, int zs, int ze, int nz, int dz, int xs, int xe, int ys, int ye) {
        long startTime;
        long readingTime = 0;
        long totalTime = 0;
        long threadInitTime = 0;
        long allocationTime = 0;

        if (info == null) {
            IJ.showMessage("FileInfo was empty; could not load data.");
            return null;
        }

        FileInfoSer fi = info[0];

        int nx = xe - xs + 1;
        int ny = ye - ys + 1;

        if(Globals.verbose) {
            log("# openCroppedH5stack");
            log("directory: " + fi.directory);
            log("filename: " + fi.fileName);
            log("info.length: " + info.length);
            log("zs,dz,ze,nz,xs,xe,ys,ye: " + zs + "," + dz + "," + ze + "," + nz + "," + xs + "," + xe + "," + ys + "," + ye);
        }

        totalTime = System.currentTimeMillis();

        // initialisation and allocation
        startTime = System.currentTimeMillis();
        ImageStack stack = ImageStack.create(nx, ny, nz, fi.bytesPerPixel*8);
        ImagePlus imp = new ImagePlus("cropped", stack);
        allocationTime += (System.currentTimeMillis() - startTime);

        // load everything in one go
        startTime = System.currentTimeMillis();
        IHDF5Reader reader = HDF5Factory.openForReading(fi.directory + fi.fileName);
        final MDShortArray block = reader.uint16().readMDArrayBlockWithOffset(fi.h5DataSet, new int[]{nz, ny, nx}, new long[]{zs, ys, xs});
        final short[] asFlatArray = block.getAsFlatArray();
        readingTime += (System.currentTimeMillis() - startTime);

        // put into stack
        // todo: could be done in parallel threads
        int imShortSize = nx*ny;

        for(int z=zs; z<=ze; z+=dz) {
            short[] pixels = (short[]) imp.getStack().getPixels(z-zs+1);
            System.arraycopy(asFlatArray, (z-zs)*imShortSize, pixels, 0, imShortSize);
        }

        totalTime = (System.currentTimeMillis() - totalTime);

        if(Globals.verbose) {
            int usefulBytesRead = nz*nx*ny*fi.bytesPerPixel;
            log("readingTime [ms]: " + readingTime);
            log("pixels read: "+asFlatArray.length);
            log("effective reading speed [MB/s]: " + usefulBytesRead/((readingTime+0.001)*1000));
            log("allocationTime [ms]: "+allocationTime);
            log("threadInitTime [ms]: "+threadInitTime);
            //log("additional threadRunningTime [ms]: "+threadRunningTime);
            log("totalTime [ms]: " + totalTime);
            //log("Processing [ms]: " + processTime);
        }

        return(imp);
    }

    public ImagePlus openCroppedTiffStackUsingIFDs(FileInfoSer[] info, int zs, int ze, int nz, int dz, int xs, int xe, int ys, int ye) {
        long startTime;
        long readingTime = 0;
        long totalTime = 0;
        long threadInitTime = 0;

        if (info == null) return null;
        FileInfoSer fi = info[0];

        int nx = xe - xs + 1;
        int ny = ye - ys + 1;

        if(Globals.verbose) {
            log("# openCroppedTiffStackUsingIFDs");
            log("info.length: " + info.length);
            log("fi.directory: " + fi.directory);
            log("fi.filename: " + fi.fileName);
            log("fi.compression: " + fi.compression);
            log("fi.intelByteOrder: " + fi.intelByteOrder);
            log("fi.bytesPerPixel: " + fi.bytesPerPixel);
            log("zs,dz,ze,nz,xs,xe,ys,ye: " + zs + "," + dz + "," + ze + "," + nz + "," + xs + "," + xe + "," + ys + "," + ye);
        }

        totalTime = System.currentTimeMillis();

        // initialisation and allocation
        startTime = System.currentTimeMillis();


        int imByteWidth = fi.width*fi.bytesPerPixel;
        ImageStack stack = ImageStack.create(nx, ny, nz, fi.bytesPerPixel*8);
        byte[][] buffer = new byte[nz][1];
        short[][] pixels = new short[nz][nx*ny];
        ExecutorService es = Executors.newCachedThreadPool();

        long allocationTime = (System.currentTimeMillis() - startTime);


        try {
            // get input stream to file
            File f = new File(fi.directory + fi.fileName);
            FileInputStream in = new FileInputStream(f);

            if(in==null) {
                IJ.showMessage("Could not open file: "+fi.directory+fi.fileName);
                throw new IllegalArgumentException("could not open file");
            }
            //InputStream in = new BufferedInputStream(new FileInputStream(f));
            //FileImageInputStream in = new FileImageInputStream(f);

            long pointer=0L;

            for(int z=zs; z<=ze; z+=dz) {

                if (z<0 || z>=info.length) {
                    IJ.showMessage("z=" + z + " is out of range. Please reduce your z-radius.");
                    throw new IllegalArgumentException("z=" + z + " is out of range");
                }

                fi = info[z];

                //
                // Read y subset from current z-slice
                //

                startTime = System.currentTimeMillis();
                pointer = readTiffOnePlaneCroppedRows(fi, in, pointer, buffer, z, zs, ys, ye) ;
                readingTime += (System.currentTimeMillis() - startTime);

                //
                // Decompress Rearrange Crop X And Put Into Stack
                //

                startTime = System.currentTimeMillis();
                es.execute(new process2stack(fi, stack, pixels, buffer, z, zs, ze, ys, ye, ny, xs, xe, nx, imByteWidth));
                threadInitTime += (System.currentTimeMillis() - startTime);

                //decompressRearrangeCropAndPutIntoStack(fi, stack, pixels, buffer, z, zs, ze, ys, ye, ny, xs, xe, nx, imByteWidth);


            } // z

            in.close();

        } catch (Exception e) {
            IJ.handleException(e);
        }

        startTime = System.currentTimeMillis();
        try {
            es.shutdown();
            while(!es.awaitTermination(1, TimeUnit.MINUTES));
        }
        catch (InterruptedException e) {
            System.err.println("tasks interrupted");
        }
        long threadRunningTime = (System.currentTimeMillis() - startTime);


        ImagePlus imp = new ImagePlus("One stream", stack);
        //imp.show();

        totalTime = (System.currentTimeMillis() - totalTime);

        if(Globals.verbose) {
            int usefulBytesRead = nz*nx*ny*fi.bytesPerPixel;
            log("readingTime [ms]: " + readingTime);
            log("effective reading speed [MB/s]: " + usefulBytesRead/((readingTime+0.001)*1000));
            log("allocationTime [ms]: "+allocationTime);
            log("threadInitTime [ms]: "+threadInitTime);
            log("additional threadRunningTime [ms]: "+threadRunningTime);
            log("totalTime [ms]: " + totalTime);
            //log("Processing [ms]: " + processTime);
        }

        return imp;
    }

    public FileInfoSer[] cropInfoCenterRadius(FileInfoSer[] info, int dz, Point3D pc, Point3D pr) {

        // todo: make this some methods in some class!!!
        int x = (int) (pc.getX() + 0.5);
        int y = (int) (pc.getY() + 0.5);
        int z = (int) (pc.getZ() + 0.5);
        int rx = (int) (pr.getX() + 0.5);
        int ry = (int) (pr.getY() + 0.5);
        int rz = (int) (pr.getZ() + 0.5);
        int nx = (int) (2 * rx + 1);
        int ny = (int) (2 * ry + 1);
        int nz = (int) (2 * rz + 1);
        x = x - rx;
        y = y - ry;
        z = z - rz;

        if (dz > 1) {
            nz = (int) (1.0 * nz / dz + 0.5);
        }

        if (Globals.verbose) {
            log("# OpenerExtension.cropInfo:");
            log("filename: " + info[0].fileName);
            log("dz: " + dz);
            log("rx,ry,rz: " + pr.getX() + "," + pr.getY() + "," + pr.getZ());
            log("z,nz,x,nx,y,ny: " + z + "," + nz + "," + x + "," + nx + "," + y + "," + ny);
            log("info.length: " + info.length);
        }

        FileInfoSer[] croppedInfo = new FileInfoSer[nz];
        FileInfoSer fi = info[0];



        /*if(fi.fileTypeString == "tif") {

            for (int iz = z, jz = z; iz < (z + nz); iz++, jz += dz) {
                if (jz < 0 || jz >= info.length) {
                    IJ.showMessage("z=" + jz + " is out of range. Please reduce your z-radius.");
                    throw new IllegalArgumentException("z=" + jz + " is out of range; iz=" + iz);
                }
                croppedInfo[iz-z] = (FileInfoSer) info[jz].clone();
                croppedInfo[iz-z].longOffset = croppedInfo[iz-z].longOffset;
                croppedInfo[iz-z].stripLengths = new int[ny];
                croppedInfo[iz-z].stripOffsets = new int[ny];
                for (int i = 0; i < ny; i++) {
                    croppedInfo[iz-z].stripLengths[i] = nx * fi.bytesPerPixel;
                    croppedInfo[iz-z].stripOffsets[i] = (int) croppedInfo[iz-z].longOffset + ((((y + i) * fi.width) + x) * fi.bytesPerPixel);
                    //infoModified[iz-z].stripOffsets[i] = (int) (i * fi.width * fi.bytesPerPixel);
                }
                croppedInfo[iz-z].height = ny;
                croppedInfo[iz-z].width = nx;
            }

        } else if(fi.fileTypeString == "h5") {

        }*/

        return (croppedInfo);
    }

}

class process2stack implements Runnable {
    private Thread t;
    private String threadName;

    // todo: make the compression modes part of the fi object?

    // Compression modes
    public static final int COMPRESSION_UNKNOWN = 0;
    public static final int COMPRESSION_NONE = 1;
    public static final int LZW = 2;
    public static final int LZW_WITH_DIFFERENCING = 3;
    public static final int JPEG = 4;
    public static final int PACK_BITS = 5;
    private static final int CLEAR_CODE = 256;
    private static final int EOI_CODE = 257;

    /** 16-bit signed integer (-32768-32767). Imported signed images
     are converted to unsigned by adding 32768. */
    public static final int GRAY16_SIGNED = 1;

    /** 16-bit unsigned integer (0-65535). */
    public static final int GRAY16_UNSIGNED = 2;

    // uncompress
    byte[][] symbolTable = new byte[4096][1];

    // input
    ImageStack stack;
    short[][] pixels;
    byte[][] buffer;
    FileInfoSer fi;
    int z, zs, ze, ys, ye, ny, xs, xe, nx, imByteWidth;

    process2stack(FileInfoSer fi, ImageStack stack, short[][] pixels, byte[][] buffer, int z, int zs, int ze, int ys, int ye, int ny, int xs, int xe, int nx, int imByteWidth) {
        threadName = ""+z;
        this.fi = fi;
        this.stack = stack;
        this.buffer = buffer;
        this.pixels = pixels;
        this.z = z;
        this.zs = zs;
        this.ze = ze;
        this.ys = ys;
        this.ye = ye;
        this.ny = ny;
        this.xs = xs;
        this.xe = xe;
        this.nx = nx;
        this.imByteWidth = imByteWidth;
        //log("Creating process2stack of slice: " +  threadName );
    }

    public void run() {
        //log("Running " +  threadName );

        boolean hasStrips = false;
        if ((fi.stripOffsets != null && fi.stripOffsets.length > 1)) {
            hasStrips = true;
        }

        // check what we have read
        int rps = fi.rowsPerStrip;
        int ss = ys / rps; // the int is doing a floor()
        int se = ye / rps;

        log("fi.compression: "+fi.compression);

        if(hasStrips) {

            if(fi.compression == COMPRESSION_NONE) {
                // do nothing
            } else if (fi.compression == LZW) {

                // needs to hold all data present in the uncompressed strips
                byte[] unCompressedBuffer = new byte[(se - ss + 1) * rps * imByteWidth];

                int pos = 0;
                for (int s = ss; s <= se; s++) {
                    int stripLength = fi.stripLengths[s];
                    byte[] strip = new byte[stripLength];

                    // get strip from read data
                    try {
                        System.arraycopy(buffer[z - zs], pos, strip, 0, stripLength);
                    } catch (Exception e) {
                        log("" + e.toString());
                        log("------- s [#] : " + s);
                        log("stripLength [bytes] : " + strip.length);
                        log("pos [bytes] : " + pos);
                        log("pos + stripLength [bytes] : " + (pos + stripLength));
                        log("z-zs : " + (z - zs));
                        log("buffer[z-zs].length : " + buffer[z - zs].length);
                        log("imWidth [bytes] : " + imByteWidth);
                        log("rows per strip [#] : " + rps);
                        log("ny [#] : " + ny);
                        log("(s - ss) * imByteWidth * rps [bytes] : " + ((s - ss) * imByteWidth * rps));
                        log("unCompressedBuffer.length [bytes] : " + unCompressedBuffer.length);
                    }

                    //log("strip.length " + strip.length);
                    // uncompress strip
                    strip = lzwUncompress(strip, imByteWidth * rps);

                    // put uncompressed strip into large array
                    System.arraycopy(strip, 0, unCompressedBuffer, (s - ss) * imByteWidth * rps, imByteWidth * rps);
                    pos += stripLength;
                }
                buffer[z - zs] = unCompressedBuffer;
            } else {
                log("Unknown compression: "+fi.compression);
            }

            //
            // copy the correct x and y subset into the image stack
            //
            ys = ys % rps; // we might have to skip a few rows in the beginning because the strips can hold several rows

            setShortPixelsCropXY(fi, (short[]) stack.getPixels(z - zs + 1), ys, ny, xs, nx, imByteWidth, buffer[z - zs]);

        } else { // no strips

            setShortPixelsCropXY(fi, (short[]) stack.getPixels(z - zs + 1), ys, ny, xs, nx, imByteWidth, buffer[z - zs]);

        }

    }

    public byte[] lzwUncompress(byte[] input, int byteCount) {
        long startTimeGlob = System.nanoTime();
        long totalTimeGlob = 0;
        long startTime0, totalTime0 = 0;
        long startTime1, totalTime1 = 0;
        long startTime2, totalTime2 = 0;
        long startTime3, totalTime3 = 0;
        long startTime4, totalTime4 = 0;
        long startTime5, totalTime5 = 0;
        long startTime6, totalTime6 = 0;
        long startTime7, totalTime7 = 0;
        long startTime8, totalTime8 = 0;
        long startTime9, totalTime9 = 0;

        //startTime1 = System.nanoTime();

        if (input==null || input.length==0)
            return input;

        int bitsToRead = 9;
        int nextSymbol = 258;
        int code;
        int symbolLength, symbolLengthMax=0;
        int oldCode = -1;
        //ByteVector out = new ByteVector(8192);
        byte[] out = new byte[byteCount];
        int iOut = 0, i;
        int k=0;
        BitBuffer bb = new BitBuffer(input);

        byte[] byteBuffer1 = new byte[16];
        byte[] byteBuffer2 = new byte[16];

        // todo: can this be larger?
        //byte[] symbol = new byte[100];

        //totalTime1 = (System.nanoTime() - startTime1);

        //while (out.size()<byteCount) {
        while (iOut<byteCount) {

            //startTime2 = System.nanoTime();

            code = bb.getBits(bitsToRead);

            //totalTime2 += (System.nanoTime() - startTime2);


            if (code==EOI_CODE || code==-1)
                break;
            if (code==CLEAR_CODE) {
                //startTime4 = System.nanoTime();
                // initialize symbol table
                for (i = 0; i < 256; i++)
                    symbolTable[i][0] = (byte)i;
                nextSymbol = 258;
                bitsToRead = 9;
                code = bb.getBits(bitsToRead);
                if (code==EOI_CODE || code==-1)
                    break;
                //out.add(symbolTable[code]);
                System.arraycopy(symbolTable[code], 0, out, iOut, symbolTable[code].length);
                iOut += symbolTable[code].length;
                oldCode = code;
                //totalTime4 += (System.nanoTime() - startTime4);

            } else {
                if (code<nextSymbol) {
                    //startTime6 = System.nanoTime();
                    // code is in table
                    //startTime5 = System.nanoTime();
                    //out.add(symbolTable[code]);
                    symbolLength = symbolTable[code].length;
                    System.arraycopy(symbolTable[code], 0, out, iOut, symbolLength);
                    iOut += symbolLength;
                    //totalTime5 += (System.nanoTime() - startTime5);
                    // add string to table

                    //ByteVector symbol = new ByteVector(byteBuffer1);
                    //symbol.add(symbolTable[oldCode]);
                    //symbol.add(symbolTable[code][0]);
                    int lengthOld = symbolTable[oldCode].length;


                    //byte[] newSymbol = new byte[lengthOld+1];
                    symbolTable[nextSymbol] = new byte[lengthOld+1];
                    System.arraycopy(symbolTable[oldCode], 0, symbolTable[nextSymbol], 0, lengthOld);
                    symbolTable[nextSymbol][lengthOld] = symbolTable[code][0];
                    //symbolTable[nextSymbol] = newSymbol;

                    oldCode = code;
                    nextSymbol++;
                    //totalTime6 += (System.nanoTime() - startTime6);

                } else {

                    //startTime3 = System.nanoTime();
                    // out of table
                    ByteVector symbol = new ByteVector(byteBuffer2);
                    symbol.add(symbolTable[oldCode]);
                    symbol.add(symbolTable[oldCode][0]);
                    byte[] outString = symbol.toByteArray();
                    //out.add(outString);
                    System.arraycopy(outString, 0, out, iOut, outString.length);
                    iOut += outString.length;
                    symbolTable[nextSymbol] = outString; //**
                    oldCode = code;
                    nextSymbol++;
                    //totalTime3 += (System.nanoTime() - startTime3);

                }
                if (nextSymbol == 511) { bitsToRead = 10; }
                if (nextSymbol == 1023) { bitsToRead = 11; }
                if (nextSymbol == 2047) { bitsToRead = 12; }
            }

        }

        totalTimeGlob = (System.nanoTime() - startTimeGlob);
        /*
        log("total : "+totalTimeGlob/1000);
        totalTimeGlob = 1000;
        log("fraction1 : "+(double)totalTime1/totalTimeGlob);
        log("fraction2 : "+(double)totalTime2/totalTimeGlob);
        log("fraction3 : "+(double)totalTime3/totalTimeGlob);
        log("fraction4 : "+(double)totalTime4/totalTimeGlob);
        log("fraction5 : "+(double)totalTime5/totalTimeGlob);
        log("fraction6 : "+(double)totalTime6/totalTimeGlob);
        log("fraction7 : "+(double)totalTime7/totalTimeGlob);
        log("fraction8 : "+(double)totalTime8/totalTimeGlob);
        log("fraction9 : "+(double)totalTime9/totalTimeGlob);
        log("symbolLengthMax "+symbolLengthMax);
        */

        return out;
    }

    public void setShortPixelsCropXY(FileInfoSer fi, short[] pixels, int ys, int ny, int xs, int nx, int imByteWidth, byte[] buffer) {
        int ip = 0;
        int bs, be;
        if(fi.bytesPerPixel!=2) {
            log("Unsupported bit depth: "+fi.bytesPerPixel*8);
        }

        for (int y = ys; y < ys + ny; y++) {

            bs = y * imByteWidth + xs * fi.bytesPerPixel;
            be = bs + nx * fi.bytesPerPixel;

            if (fi.intelByteOrder) {
                if (fi.fileType == GRAY16_SIGNED)
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) ((((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff)) + 32768);
                else
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) (((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff));
            } else {
                if (fi.fileType == GRAY16_SIGNED)
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) ((((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff)) + 32768);
                else
                    for (int j = bs; j < be; j += 2)
                        pixels[ip++] = (short) (((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff));
            }
        }
    }

    public void start () {
        //log("Starting " +  threadName );
        if (t == null) {
            t = new Thread (this, threadName);
            t.start ();
        }
    }

}

/** A growable array of bytes. */
class ByteVector {
    private byte[] data;
    private int size;

    public ByteVector() {
        data = new byte[10];
        size = 0;
    }

    public ByteVector(int initialSize) {
        data = new byte[initialSize];
        size = 0;
    }

    public ByteVector(byte[] byteBuffer) {
        data = byteBuffer;
        size = 0;
    }

    public void add(byte x) {
        if (size>=data.length) {
            doubleCapacity();
            add(x);
        } else
            data[size++] = x;
    }

    public int size() {
        return size;
    }

    public void add(byte[] array) {
        int length = array.length;
        while (data.length-size<length)
            doubleCapacity();
        System.arraycopy(array, 0, data, size, length);
        size += length;
    }

    void doubleCapacity() {
        //IJ.log("double: "+data.length*2);
        byte[] tmp = new byte[data.length*2 + 1];
        System.arraycopy(data, 0, tmp, 0, data.length);
        data = tmp;
    }

    public void clear() {
        size = 0;
    }

    public byte[] toByteArray() {
        byte[] bytes = new byte[size];
        System.arraycopy(data, 0, bytes, 0, size);
        return bytes;
    }

}


/*


    long skip(FileImageInputStream in, long skipCount, long pointer) throws IOException {
        in.seek(pointer + skipCount);
        return (pointer + skipCount);
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
                    return (-1); //EOF Error
                }
                bufferCount += count;
            }
        } catch (IOException e) {
            IJ.log("" + e);
            buffer = null;
            return (-1);
        }
        //log("read: buffer.length "+buffer.length);
        return (pointer + (long) bufferSize);
    }

    void setShortPixels(FileInfoSer fi, short[] pixels, int base, byte[] buffer) {
        int bytesPerPixel = fi.bytesPerPixel;
        int pixelsRead = (int) buffer.length / bytesPerPixel;
        log("setShortPixels: base " + base);
        log("setShortPixels: pixels in buffer " + pixelsRead);
        log("setShortPixels: pixels.length " + pixels.length);

        if (fi.intelByteOrder) {
            if (fi.fileType == GRAY16_SIGNED)
                for (int i = base, j = 0; i < (base + pixelsRead); i++, j += 2)
                    pixels[i] = (short) ((((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff)) + 32768);
            else
                for (int i = base, j = 0; i < (base + pixelsRead); i++, j += 2)
                    pixels[i] = (short) (((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff));
        } else {
            if (fi.fileType == GRAY16_SIGNED)
                for (int i = base, j = 0; i < (base + pixelsRead); i++, j += 2)
                    pixels[i] = (short) ((((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff)) + 32768);
            else
                for (int i = base, j = 0; i < (base + pixelsRead); i++, j += 2)
                    pixels[i] = (short) (((buffer[j] & 0xff) << 8) | (buffer[j + 1] & 0xff));
        }
    }



    public ImagePlus OLDopenCroppedTiffStackUsingIFDs(FileInfo[] infoAll, int dz, Point3D p, Point3D pr) {
        //log("# openCroppedTiffStackUsingIFDs");
        long startTime, stopTime, elapsedTime;
        FileInfo[] info;

        if (infoAll==null) return null;
        FileInfo fi = infoAll[0];

        // get size of image before cropping
        int imByteWidth = infoAll[0].width*infoAll[0].bytesPerPixel;

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

        int stripPixelLength = (int) fi.stripLengths[0]/fi.bytesPerPixel;
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
            int byteRead = nz*fi.width*fi.height*fi.bytesPerPixel;
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
        long size = fi.width*fi.height*fi.bytesPerPixel;
        fi.longOffset = fi.getOffset() + (z*(size+fi.gapBetweenImages));
        fi.longOffset = fi.longOffset + (y*fi.width+x)*fi.bytesPerPixel;
        fi.offset = 0;
        fi.nImages = nz;
        fi.gapBetweenImages += (int) (fi.width-(x+nx-1));
        fi.gapBetweenImages += (int) (fi.height-(y+ny))*fi.width;
        fi.gapBetweenImages += (int) (y*fi.width);
        fi.gapBetweenImages += (int) (x-1);
        fi.gapBetweenImages *= fi.bytesPerPixel;
        //log("  fi.gapBetweenImages: "+fi.gapBetweenImages);

        int[] newStripLengths = new int[ny];
        int[] newStripOffsets = new int[ny];
        for (int i=0; i<newStripLengths .length; i++) {
            newStripLengths[i] = nx * fi.bytesPerPixel;
            newStripOffsets[i] = i * fi.width * fi.bytesPerPixel;
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

    /*
    private void process2stack(FileInfo fi, ImageStack stack, short[][] pixels, byte[][] buffer, int z, int zs, int ze, int ys, int ye, int ny, int xs, int xe, int nx, int imByteWidth) {

        // check what we have read
        int rps = fi.rowsPerStrip;
        int ss = (int) ys / rps;
        int se = (int) ye / rps;

        //log(""+ss);
        //log(""+se);
        //log(""+fi.compression);

        // deal with compression
        if(fi.compression==LZW) {

            log("lzw uncompression of slice " + z);

            byte[] unCompressedBuffer = new byte[ny * imByteWidth];

            int pos = 0;
            for (int s = ss; s <= se; s++) {
                int stripLength = fi.stripLengths[s];
                byte[] strip = new byte[stripLength];
                // get strip from read data
                System.arraycopy(buffer[z-zs], pos, strip, 0, stripLength);
                //log("strip.length " + strip.length);
                // uncompress strip
                strip = lzwUncompress(strip, imByteWidth);

                //log("strip.length [pixels] " + strip.length/fi.bytesPerPixel);
                //log("imWidth [pixels] " + imByteWidth/fi.bytesPerPixel);

                // put uncompressed strip into large array
                System.arraycopy(strip, 0, unCompressedBuffer, (s - ss) * imByteWidth * rps, imByteWidth * rps);
                pos += stripLength;
            }

            buffer[z-zs] = unCompressedBuffer;

            //log("uncompressed buffer.length: " + buffer[z-zs].length);
        }


        //
        // Rearrange data into pixels, crop it and put into image stack
        //

        // convert pixels to 16bit gray values and store in pixels[z]
        //log("buffer.length: " + buffer[0].length);
        //log("ny*imByteWidth " + (ny*imByteWidth));

        // store strips in pixel array
        ys=ys%rps;
        setShortPixelsFromAllStrips(fi, pixels[z-zs], ys, ny, xs, nx, imByteWidth, buffer[0]);

        // add pixels to stack
        stack.addSlice(new ShortProcessor(nx, ny, (short[])pixels[z-zs],null));

    };
*/


    /*public ImagePlus openTiffStackSliceUsingIFDs(FileInfo[] info, int z) {
        ImagePlus imp;
        long startTime = System.currentTimeMillis();
        FileOpener fo = new FileOpener(info[z]);
        imp = fo.open(false);
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        if (Globals.verbose) {
            log("OpenerExtension.openTiffStackSliceUsingIFDs");
            log("Whole slice opened in [ms]: " + elapsedTime);
            log("Reading speed [MB/s]: " + (info[z].width * info[z].height * info[z].bytesPerPixel) / ((elapsedTime + 0.001) * 1000));
        }

        return imp;
    }*/

    /*
    public FileInfo[] cropFileInfo(FileInfo[] info, int dz, Point3D p, Point3D pr) {
        //log("OpenerExtensions.cropFileInfo:");

        // round the values

        //int x = (int) (p.getX() - pr.getX());
        //int y = (int) (p.getY() - pr.getY());
        //int z = (int) (p.getZ() - pr.getZ());
        //int nx = (int) (2.0 * pr.getX() + 1.5001); // to enable evenly sized stacks
        //int ny = (int) (2.0 * pr.getY() + 1.5001);
        //int nz = (int) ((2.0 * pr.getZ() / dz + 1.5001) );

        int x = (int) (p.getX() + 0.5);
        int y = (int) (p.getY() + 0.5);
        int z = (int) (p.getZ() + 0.5);
        int rx = (int) (pr.getX() + 0.5);
        int ry = (int) (pr.getY() + 0.5);
        int rz = (int) (pr.getZ() + 0.5);
        int nx = (int) (2 * rx + 1);
        int ny = (int) (2 * ry + 1);
        int nz = (int) (2 * rz + 1);
        x = x - rx;
        y = y - ry;
        z = z - rz;

        if (dz > 1) {
            nz = (int) (1.0 * nz / dz + 0.5);
        }

        if (Globals.verbose) {
            log("# OpenerExtension.cropFileInfo:");
            log("filename: " + info[0].fileName);
            log("dz: " + dz);
            log("rx,ry,rz: " + pr.getX() + "," + pr.getY() + "," + pr.getZ());
            log("z,nz,x,nx,y,ny: " + z + "," + nz + "," + x + "," + nx + "," + y + "," + ny);
            log("info.length: " + info.length);
        }

        FileInfo[] infoModified = new FileInfo[nz];
        FileInfo fi = info[0];

        for (int iz = z, jz = z; iz < (z + nz); iz++, jz += dz) {
            if (jz < 0 || jz >= info.length) {
                IJ.showMessage("z=" + jz + " is out of range. Please reduce your z-radius.");
                throw new IllegalArgumentException("z=" + jz + " is out of range; iz=" + iz);
            }
            infoModified[iz - z] = (FileInfo) info[jz].clone();
            infoModified[iz - z].nImages = 1;
            infoModified[iz - z].longOffset = infoModified[iz - z].getOffset();
            infoModified[iz - z].offset = 0;
            infoModified[iz - z].stripLengths = new int[ny];
            infoModified[iz - z].stripOffsets = new int[ny];
            for (int i = 0; i < ny; i++) {
                infoModified[iz - z].stripLengths[i] = nx * fi.bytesPerPixel;
                infoModified[iz - z].stripOffsets[i] = (int) infoModified[iz - z].getOffset() + ((((y + i) * fi.width) + x) * fi.bytesPerPixel);
                //infoModified[iz-z].stripOffsets[i] = (int) (i * fi.width * fi.bytesPerPixel);
            }
            infoModified[iz - z].height = ny;
            infoModified[iz - z].width = nx;
            // next line is necessary for the IJ TiffReader, should not be
            infoModified[iz - z].longOffset += (y * fi.width + x) * fi.bytesPerPixel;
            //log(""+(iz-z)+" "+info[iz].getOffset());
            //log(""+infoModified[iz-z].stripOffsets[0]);
            //log(""+(iz-z)+" "+infoModified[iz-z].getOffset());
        }
        //for(int i=0; i<infoModified.length; i++){
        //    log(""+infoModified[i].stripOffsets[0]);
        //}

        return (infoModified);
    }
    */


/*
    private void decompressRearrangeCropAndPutIntoStack(FileInfo fi, ImageStack stack, short[][] pixels, byte[][] buffer, int z, int zs, int ze, int ys, int ye, int ny, int xs, int xe, int nx, int imByteWidth) {

        // check what we have read
        int rps = fi.rowsPerStrip;
        int ss = (int) ys / rps;
        int se = (int) ye / rps;

        //log(""+ss);
        //log(""+se);
        //log(""+fi.compression);

        // deal with compression
        if(fi.compression==LZW) {

            //log("lzw uncompression of slice " + z);

            byte[] unCompressedBuffer = new byte[ny * imByteWidth];

            int pos = 0;
            for (int s = ss; s <= se; s++) {
                int stripLength = fi.stripLengths[s];
                byte[] strip = new byte[stripLength];
                // get strip from read data
                System.arraycopy(buffer[z-zs], pos, strip, 0, stripLength);
                //log("strip.length " + strip.length);
                // uncompress strip
                strip = lzwUncompressAAA(strip, imByteWidth);

                //log("strip.length [pixels] " + strip.length/fi.bytesPerPixel);
                //log("imWidth [pixels] " + imByteWidth/fi.bytesPerPixel);

                // put uncompressed strip into large array
                System.arraycopy(strip, 0, unCompressedBuffer, (s - ss) * imByteWidth * rps, imByteWidth * rps);
                pos += stripLength;
            }

        buffer[z-zs] = unCompressedBuffer;

        //log("uncompressed buffer.length: " + buffer[z-zs].length);
        }


        //
        // Rearrange data into pixels, crop it and put into image stack
        //

        // convert pixels to 16bit gray values and store in pixels[z]
        //log("buffer.length: " + buffer[z-zs].length);
        //log("ny*imByteWidth " + (ny*imByteWidth));

        // store strips in pixel array
        ys=ys%rps;
        setShortPixelsFromAllStrips(fi, pixels[z-zs], ys, ny, xs, nx, imByteWidth, buffer[z-zs]);

        // add pixels to stack
        stack.addSlice(new ShortProcessor(nx, ny, (short[])pixels[z-zs],null));

    }

    public byte[] lzwUncompressAAA(byte[] input, int byteCount) {
        long startTimeGlob = System.nanoTime();
        long totalTimeGlob = 0;
        long startTime0, totalTime0 = 0;
        long startTime1, totalTime1 = 0;
        long startTime2, totalTime2 = 0;
        long startTime3, totalTime3 = 0;
        long startTime4, totalTime4 = 0;
        long startTime5, totalTime5 = 0;
        long startTime6, totalTime6 = 0;
        long startTime7, totalTime7 = 0;
        long startTime8, totalTime8 = 0;
        long startTime9, totalTime9 = 0;

        //startTime1 = System.nanoTime();

        if (input==null || input.length==0)
            return input;

        int bitsToRead = 9;
        int nextSymbol = 258;
        int code;
        int symbolLength, symbolLengthMax=0;
        int oldCode = -1;
        //ByteVector out = new ByteVector(8192);
        byte[] out = new byte[byteCount];
        int iOut = 0, i;
        int k=0;
        BitBuffer bb = new BitBuffer(input);

        byte[] byteBuffer1 = new byte[16];
        byte[] byteBuffer2 = new byte[16];

        // todo: can this be larger?
        //byte[] symbol = new byte[100];

        //totalTime1 = (System.nanoTime() - startTime1);

        //while (out.size()<byteCount) {
        while (iOut<byteCount) {

            //startTime2 = System.nanoTime();

            code = bb.getBits(bitsToRead);

            //totalTime2 += (System.nanoTime() - startTime2);


            if (code==EOI_CODE || code==-1)
                break;
            if (code==CLEAR_CODE) {
                //startTime4 = System.nanoTime();
                // initialize symbol table
                for (i = 0; i < 256; i++)
                    symbolTable[i][0] = (byte)i;
                nextSymbol = 258;
                bitsToRead = 9;
                code = bb.getBits(bitsToRead);
                if (code==EOI_CODE || code==-1)
                    break;
                //out.add(symbolTable[code]);
                System.arraycopy(symbolTable[code], 0, out, iOut, symbolTable[code].length);
                iOut += symbolTable[code].length;
                oldCode = code;
                //totalTime4 += (System.nanoTime() - startTime4);

            } else {
                if (code<nextSymbol) {
                    //startTime6 = System.nanoTime();
                    // code is in table
                    //startTime5 = System.nanoTime();
                    //out.add(symbolTable[code]);
                    symbolLength = symbolTable[code].length;
                    System.arraycopy(symbolTable[code], 0, out, iOut, symbolLength);
                    iOut += symbolLength;
                    //totalTime5 += (System.nanoTime() - startTime5);
                    // add string to table

                    //ByteVector symbol = new ByteVector(byteBuffer1);
                    //symbol.add(symbolTable[oldCode]);
                    //symbol.add(symbolTable[code][0]);
                    int lengthOld = symbolTable[oldCode].length;


                    //byte[] newSymbol = new byte[lengthOld+1];
                    symbolTable[nextSymbol] = new byte[lengthOld+1];
                    System.arraycopy(symbolTable[oldCode], 0, symbolTable[nextSymbol], 0, lengthOld);
                    symbolTable[nextSymbol][lengthOld] = symbolTable[code][0];
                    //symbolTable[nextSymbol] = newSymbol;

                    oldCode = code;
                    nextSymbol++;
                    //totalTime6 += (System.nanoTime() - startTime6);

                } else {

                    //startTime3 = System.nanoTime();
                    // out of table
                    ByteVector symbol = new ByteVector(byteBuffer2);
                    symbol.add(symbolTable[oldCode]);
                    symbol.add(symbolTable[oldCode][0]);
                    byte[] outString = symbol.toByteArray();
                    //out.add(outString);
                    System.arraycopy(outString, 0, out, iOut, outString.length);
                    iOut += outString.length;
                    symbolTable[nextSymbol] = outString; //**
                    oldCode = code;
                    nextSymbol++;
                    //totalTime3 += (System.nanoTime() - startTime3);

                }
                if (nextSymbol == 511) { bitsToRead = 10; }
                if (nextSymbol == 1023) { bitsToRead = 11; }
                if (nextSymbol == 2047) { bitsToRead = 12; }
            }

        }

        totalTimeGlob = (System.nanoTime() - startTimeGlob);
        /*
        log("total : "+totalTimeGlob/1000);
        totalTimeGlob = 1000;
        log("fraction1 : "+(double)totalTime1/totalTimeGlob);
        log("fraction2 : "+(double)totalTime2/totalTimeGlob);
        log("fraction3 : "+(double)totalTime3/totalTimeGlob);
        log("fraction4 : "+(double)totalTime4/totalTimeGlob);
        log("fraction5 : "+(double)totalTime5/totalTimeGlob);
        log("fraction6 : "+(double)totalTime6/totalTimeGlob);
        log("fraction7 : "+(double)totalTime7/totalTimeGlob);
        log("fraction8 : "+(double)totalTime8/totalTimeGlob);
        log("fraction9 : "+(double)totalTime9/totalTimeGlob);
        log("symbolLengthMax "+symbolLengthMax);
        */
//
//        return out;
//    }
