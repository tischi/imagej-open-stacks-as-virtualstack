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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

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
    public static final int ZIP = 6;
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

    public void readTiffOnePlaneCroppedRows(FileInfoSer fi0, FileInfoSer fi, RandomAccessFile in, byte[][] buffer, int z, int zs, int dz, int ys, int ye) {
        boolean hasStrips = false;
        int readLength;
        long readStart;

        if (fi.stripOffsets != null && fi.stripOffsets.length > 1) {
            hasStrips = true;
        }

        try {

            if (hasStrips) {

                // convert rows to strips
                int rps = fi0.rowsPerStrip;
                int ss = (int) (1.0*ys/rps);
                int se = (int) (1.0*ye/rps);
                readStart = (long)fi.stripOffsets[ss] & 0xffffffffL; // interpret the int as unsigned
                // 3rps 012,345,678   8/3
                if(Globals.verbose) {
                    log("number of strips: "+fi.stripLengths.length);
                    log("rows per strip: "+rps);
                    log("min strip read: "+ss);
                    log("max strip read: "+se);
                }
                readLength = 0;
                if(se>=fi.stripLengths.length) {
                    log("!!!! strip is out of bounds");
                }
                for (int s = ss; s <= se; s++) {
                    readLength += fi.stripLengths[s];
                }

            } else {  // none or one strip

                if(fi0.compression == ZIP) {
                    // read all data
                    readStart = fi.longOffset;
                    readLength = fi.stripLengths[0];

                } else {
                    // read subset
                    // convert rows to bytes
                    readStart = fi.longOffset + ys * fi0.width * fi0.bytesPerPixel;
                    readLength = ((ye-ys)+1) * fi0.width * fi0.bytesPerPixel;
                }

                if(Globals.verbose) {
                    log("fi.longOffset: "+fi.longOffset);
                    log("ys: "+ys);
                    log("readStart: "+readStart);
                    log("readLength: "+readLength);
                }

            }

            // SKIP to first strip (row) that we need to read of this z-plane
            //startTime = System.currentTimeMillis();

            //pointer = skip(in, readStart - pointer, pointer);
            in.seek(readStart); // is this really slow??

            //skippingTime += (System.currentTimeMillis() - startTime);

            // READ data
            //startTime = System.currentTimeMillis();
            buffer[(z-zs)/dz] = new byte[readLength];
            //pointer = read(in, buffer[z-zs], pointer);
            in.readFully(buffer[(z-zs)/dz]);
            //readingTime += (System.currentTimeMillis() - startTime);


        } catch (Exception e) {
            IJ.handleException(e);
        }

    }

    public ImagePlus openCroppedStackOffsetSize(String directory, FileInfoSer[] info, int dz, Point3D po, Point3D ps) {

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
        if(info[0].fileTypeString.equals("tif stacks")) {
            imp = openCroppedTiffStackUsingIFDs(directory, info, zs, ze, nz, dz, xs, xe, ys, ye);
        } else if(info[0].fileTypeString.equals("leica single tif")) {
            imp = openCroppedTiffStackUsingIFDs(directory, info, zs, ze, nz, dz, xs, xe, ys, ye);
        } else if(info[0].fileTypeString.equals("h5")) {
            imp = openCroppedH5stack(directory, info, zs, ze, nz, dz, xs, xe, ys, ye);
        } else {
            IJ.showMessage("unsupported file type: "+info[0].fileTypeString);
        }

        return(imp);

    }

    // todo make Points from the ints
    public ImagePlus openCroppedH5stack(String directory, FileInfoSer[] info, int zs, int ze, int nz, int dz, int xs, int xe, int ys, int ye) {
        long startTime;
        long readingTime = 0;
        long totalTime = 0;
        long threadInitTime = 0;
        long allocationTime = 0;
        short[] asFlatArray=null, pixels;
        MDShortArray block;
        boolean dataSetExists = false;


        if (info == null) {
            IJ.showMessage("FileInfo was empty; could not load data.");
            return null;
        }

        FileInfoSer fi = info[0];

        int nx = xe - xs + 1;
        int ny = ye - ys + 1;
        int imShortSize = nx * ny;


        if(Globals.verbose) {
            log("# openCroppedH5stack");
            log("root directory: " + directory);
            log("fi.directory: " + fi.directory);
            log("fi.filename: " + fi.fileName);
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
        IHDF5Reader reader = HDF5Factory.openForReading(directory + fi.directory + fi.fileName);

        if(dz==1) {

            // read everything in one go
            block = reader.uint16().readMDArrayBlockWithOffset(fi.h5DataSet, new int[]{nz, ny, nx}, new long[]{zs, ys, xs});
            asFlatArray = block.getAsFlatArray();

            // put plane-wise into stack
            for (int z = zs; z <= ze; z ++) {
                pixels = (short[]) imp.getStack().getPixels(z - zs + 1);
                System.arraycopy(asFlatArray, (z - zs) * imShortSize, pixels, 0, imShortSize);
            }

        } else { // sub-sample in z

            // read plane wise
            int z = zs;
            for (int iz=1; iz<=nz; iz++, z+=dz) {

                block = reader.uint16().readMDArrayBlockWithOffset(fi.h5DataSet, new int[]{1, ny, nx}, new long[]{z, ys, xs});
                asFlatArray = block.getAsFlatArray();
                pixels = (short[]) imp.getStack().getPixels(iz);
                System.arraycopy(asFlatArray, 0, pixels, 0, imShortSize);

            }

        }
        readingTime += (System.currentTimeMillis() - startTime);

        totalTime = (System.currentTimeMillis() - totalTime);

        if(Globals.verbose) {
            log("readingTime [ms]: " + readingTime);
            log("pixels read: "+asFlatArray.length);
            log("effective reading speed [MB/s]: " + (double)nz*nx*ny*fi.bytesPerPixel/((readingTime+0.001)*1000));
            log("allocationTime [ms]: "+allocationTime);
            //log("threadInitTime [ms]: "+threadInitTime);
            //log("additional threadRunningTime [ms]: "+threadRunningTime);
            log("totalTime [ms]: " + totalTime);
            //log("Processing [ms]: " + processTime);
        }

        return(imp);
    }

    public ImagePlus openCroppedTiffStackUsingIFDs(String directory, FileInfoSer[] info, int zs, int ze, int nz, int dz, int xs, int xe, int ys, int ye) {
        long startTime;
        long readingTime = 0;
        long totalTime = 0;
        long threadInitTime = 0;
        FileInfoSer fi;
        File file;

        if (info == null) return null;
        FileInfoSer fi0 = info[0];

        int nx = xe - xs + 1;
        int ny = ye - ys + 1;

        if(Globals.verbose) {
            log("# openCroppedTiffStackUsingIFDs");
            log("root directory: " + directory);
            log("info.length: " + info.length);
            log("fi0.directory: " + fi0.directory);
            log("fi0.filename: " + fi0.fileName);
            log("fi0.compression: " + fi0.compression);
            log("fi0.intelByteOrder: " + fi0.intelByteOrder);
            log("fi0.bytesPerPixel: " + fi0.bytesPerPixel);
            log("zs,dz,ze,nz,xs,xe,ys,ye: " + zs + "," + dz + "," + ze + "," + nz + "," + xs + "," + xe + "," + ys + "," + ye);
        }

        totalTime = System.currentTimeMillis();

        // initialisation and allocation
        startTime = System.currentTimeMillis();
        int imByteWidth = fi0.width*fi0.bytesPerPixel;
        // todo: this is not necessary to allocate new, but could be filled
        ImageStack stack = ImageStack.create(nx, ny, nz, fi0.bytesPerPixel*8);
        byte[][] buffer = new byte[nz][1];
        ExecutorService es = Executors.newCachedThreadPool();

        try {

            // read plane wise
            int z = zs;

            for (int iz=0; iz<nz; iz++, z+=dz) {

                if (z<0 || z>=info.length) {
                    IJ.showMessage("z=" + z + " is out of range. Please reduce your cropping z-radius.");
                    return null;
                }

                //
                // Read, decompress, rearrange, crop X, and put into stack
                //

                es.execute(new process2stack(directory, info, stack, buffer, z, zs, ze, dz, ys, ye, ny, xs, xe, nx, imByteWidth));

            }

            // wait until all z-planes are read
            try {
                es.shutdown();
                while(!es.awaitTermination(1, TimeUnit.MINUTES));
            }
            catch (InterruptedException e) {
                System.err.println("tasks interrupted");
            }

        } catch (Exception e) {
            IJ.handleException(e);
        }


        ImagePlus imp = new ImagePlus("One stream", stack);
        //imp.show();

        totalTime = (System.currentTimeMillis() - totalTime);

        if(Globals.verbose) {
            int usefulBytesRead = nz*nx*ny*fi0.bytesPerPixel;
            log("readingTime [ms]: " + readingTime);
            log("effective reading speed [MB/s]: " + usefulBytesRead/((readingTime+0.001)*1000));
            log("threadInitTime [ms]: "+threadInitTime);
            log("totalTime [ms]: " + totalTime);
            //log("Processing [ms]: " + processTime);
        }

        return imp;
    }

    /** Decompresses and sorts data into an ImageStack **/
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
        public static final int ZIP = 6;
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
        byte[][] buffer;
        FileInfoSer[] info;
        FileInfoSer fi0, fi;
        RandomAccessFile in;
        private String directory;
        int z, zs, ze, dz, ys, ye, ny, xs, xe, nx, imByteWidth;


        process2stack(String directory, FileInfoSer[] info, ImageStack stack, byte[][] buffer, int z, int zs, int ze, int dz, int ys, int ye, int ny, int xs, int xe, int nx, int imByteWidth) {
            threadName = ""+z;
            this.directory = directory;
            this.info = info;
            this.stack = stack;
            this.buffer = buffer;
            this.z = z;
            this.zs = zs;
            this.ze = ze;
            this.ys = ys;
            this.dz =  dz;
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
            RandomAccessFile inputStream = null;

            this.fi0 = info[0];
            this.fi = info[z];

            File file = new File(directory + fi.directory + fi.fileName);
            try {
                inputStream = new RandomAccessFile(file, "r");

                if (inputStream == null) {
                    IJ.showMessage("Could not open file: " + fi.directory + fi.fileName);
                    throw new IllegalArgumentException("could not open file");
                }

                if((fi.compression!=1) && (fi.compression!=2) && (fi.compression!=6)) {
                    IJ.showMessage("Tiff compression not implemented: fi.compression = " + fi.compression);
                    return;
                }

                //startTime = System.currentTimeMillis();
                long pointer = 0; // todo: This is actually not used at all in the readTiffOnePlaneCroppedRows, or?
                readTiffOnePlaneCroppedRows(fi0, fi, inputStream, buffer, z, zs, dz, ys, ye);
                //readingTime += (System.currentTimeMillis() - startTime);
                inputStream.close();

            } catch (Exception e) {
                IJ.handleException(e);
            }

            boolean hasStrips = false;

            if ((fi.stripOffsets != null && fi.stripOffsets.length > 1)) {
                hasStrips = true;
            }

            // check what we have read
            int rps = fi0.rowsPerStrip;
            int ss = ys / rps; // the int is doing a floor()
            int se = ye / rps;

            if(hasStrips) {

                if(fi0.compression == COMPRESSION_NONE) {

                    // do nothing

                }  else if (fi0.compression == LZW) {

                    // init to hold all data present in the uncompressed strips
                    byte[] unCompressedBuffer = new byte[(se - ss + 1) * rps * imByteWidth];

                    int pos = 0;
                    for (int s = ss; s <= se; s++) {

                        // todo: multithreading here

                        int stripLength = fi.stripLengths[s];
                        byte[] strip = new byte[stripLength];

                        // get strip from read data
                        try {
                            System.arraycopy(buffer[(z - zs)/dz], pos, strip, 0, stripLength);
                        } catch (Exception e) {
                            log("" + e.toString());
                            log("------- s [#] : " + s);
                            log("stripLength [bytes] : " + strip.length);
                            log("pos [bytes] : " + pos);
                            log("pos + stripLength [bytes] : " + (pos + stripLength));
                            log("z-zs : " + (z - zs));
                            log("z-zs/dz : " + (z - zs)/dz);
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

                    buffer[(z - zs)/dz] = unCompressedBuffer;

                } else {

                    IJ.showMessage("Tiff compression not implemented: fi0.compression = "+fi0.compression);
                    return;

                }

                //
                // copy the correct x and y subset into the image stack
                //

                ys = ys % rps; // we might have to skip a few rows in the beginning because the strips can hold several rows
                setShortPixelsCropXY((short[]) stack.getPixels((z - zs)/dz + 1), ys, ny, xs, nx, imByteWidth, buffer[(z - zs)/dz]);

            } else { // no strips

                if (fi0.compression == ZIP) {

                    /** TIFF Adobe ZIP support contributed by Jason Newton. */
                    ByteArrayOutputStream imageBuffer = new ByteArrayOutputStream();
                    byte[] tmpBuffer = new byte[1024];
                    Inflater decompressor = new Inflater();

                    decompressor.setInput(buffer[(z - zs)/dz]);
                    try {
                        while(!decompressor.finished()) {
                            int rlen = decompressor.inflate(tmpBuffer);
                            imageBuffer.write(tmpBuffer, 0, rlen);
                        }
                    } catch(DataFormatException e){
                        IJ.log(e.toString());
                    }
                    decompressor.end();

                    buffer[(z - zs)/dz] = imageBuffer.toByteArray();

                    setShortPixelsCropXY((short[]) stack.getPixels((z - zs)/dz + 1), ys, ny, xs, nx, imByteWidth, buffer[(z - zs)/dz]);

                } else {

                    ys = 0; // the buffer contains only the correct y-range
                    setShortPixelsCropXY((short[]) stack.getPixels((z - zs)/dz + 1), ys, ny, xs, nx, imByteWidth, buffer[(z - zs)/dz]);

                }

                if(Globals.verbose) {
                    log("z: " + z);
                    log("zs: " + zs);
                    log("dz: " + dz);
                    log("(z - zs)/dz: "+(z - zs)/dz);
                    log("buffer.length : " + buffer.length);
                    log("buffer[z-zs].length : " + buffer[z - zs].length);
                    log("imWidth [bytes] : " + imByteWidth);
                    log("ny [#] : " + ny);
                    short[] pixels = (short[]) stack.getPixels((z - zs)/dz + 1);
                    log("stack.getPixels((z - zs)/dz + 1).length: "+pixels.length);
                }


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
                    if (nextSymbol == 4095) { IJ.showMessage("Symbol Table of LZW uncompression became too large. +" +
                            "Please contact tischitischer@gmail.com"); return null; };
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

        public void setShortPixelsCropXY(short[] pixels, int ys, int ny, int xs, int nx, int imByteWidth, byte[] buffer) {
            int ip = 0;
            int bs, be;
            if(fi0.bytesPerPixel!=2) {
                IJ.showMessage("Unsupported bit depth: "+fi0.bytesPerPixel*8);
            }

            for (int y = ys; y < ys + ny; y++) {

                bs = y * imByteWidth + xs * fi0.bytesPerPixel;
                be = bs + nx * fi0.bytesPerPixel;

                if (fi0.intelByteOrder) {
                    if (fi0.fileType == GRAY16_SIGNED)
                        for (int j = bs; j < be; j += 2)
                            pixels[ip++] = (short) ((((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff)) + 32768);
                    else
                        for (int j = bs; j < be; j += 2)
                            pixels[ip++] = (short) (((buffer[j + 1] & 0xff) << 8) | (buffer[j] & 0xff));
                } else {
                    if (fi0.fileType == GRAY16_SIGNED)
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
