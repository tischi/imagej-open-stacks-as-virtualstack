package ct.vss;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.io.Opener;

import static ij.IJ.log;

/** Opens the nth image of the specified TIFF stack. */
class OpenerExtensions extends Opener {

    public OpenerExtensions() {
    }

    public ImagePlus openPlaneInTiffUsingGivenFileInfo(String directory, String filename, int n, FileInfo[] info) {
        //log("openPlaneInTiffUsingGivenFileInfo");
        //log("  directory:" + directory);
        //log("  filename:" + filename);
        //log("  slice:" + n);
        if (info==null) return null;
        FileInfo fi = null;
        if (info.length==1 && info[0].nImages>1) {
            /** in this case getTiffFileInfo will only open the first IDF; as this is rather fast we do it
             for every file to see different fi.offset, which does happen */
            FileInfo[] infoThisFile = new Opener().getTiffFileInfo(directory+filename);
            fi = infoThisFile[0];
            if (n<1 || n>fi.nImages)
                throw new IllegalArgumentException("N out of 1-"+fi.nImages+" range");
            long size = fi.width*fi.height*fi.getBytesPerPixel();
            fi.longOffset = fi.getOffset() + (n-1)*(size+fi.gapBetweenImages);
            fi.offset = 0;
            fi.nImages = 1;
        } else {
            /** it would take to long to open all IFDs again; so we hope that the ones from the
             first file work */
            fi = (FileInfo) info[0].clone();
            fi.fileName = filename;
            fi.directory = directory;
            if (n<1 || n>info.length)
                throw new IllegalArgumentException("N out of 1-"+info.length+" range");
            fi.longOffset = info[n-1].getOffset();
            fi.offset = 0;
            fi.stripOffsets = info[n-1].stripOffsets;
            fi.stripLengths = info[n-1].stripLengths;
        }
        FileOpener fo = new FileOpener(fi);
        return fo.open(false);
    }


    public ImagePlus openCroppedTiffPlaneUsingGivenFileInfo(String directory, String filename, int n, int x, int width, int y, int height, FileInfo[] info) {

        log("openCroppedTiffPlaneUsingGivenFileInfo");
        log("  directory:" + directory);
        log("  filename:" + filename);
        log("  slice:" + n);
        log("  x:" + x);
        log("  w:" + width);
        log("  y:" + y);
        log("  h:" + height);

        if (info==null) return null;

        FileInfo fi = null;

        if (info.length==1 && info[0].nImages>1) {
            /** in this case getTiffFileInfo will only open the first IDF; as this is rather fast we do it
             for every file to see different fi.offset, which does happen */
            FileInfo[] infoThisFile = new Opener().getTiffFileInfo(directory+filename);
            fi = infoThisFile[0];
            if (n<1 || n>fi.nImages)
                throw new IllegalArgumentException("N out of 1-"+fi.nImages+" range");
            long size = fi.width*fi.height*fi.getBytesPerPixel();
            fi.longOffset = fi.getOffset() + (n-1)*(size+fi.gapBetweenImages);
            fi.longOffset = fi.longOffset + (y*fi.width+x)*fi.getBytesPerPixel();
            fi.offset = 0;
            fi.nImages = 0;
            int[] newStripLengths = new int[height];
            int[] newStripOffsets = new int[height];
            for (int i=0; i<newStripLengths .length; i++) {
                newStripLengths[i] = width * fi.getBytesPerPixel();
                newStripOffsets[i] = i * fi.width * fi.getBytesPerPixel();
            }
            fi.stripOffsets = newStripOffsets;
            fi.stripLengths = newStripLengths;
            fi.height = height;
            fi.width = width;

        } else {
            /** it would take to long to open all IFDs again; so we hope that the ones from the
             first file work */
            log("  IFD array case");
            fi = (FileInfo) info[0].clone();
            fi.fileName = filename;
            fi.directory = directory;
            if (n<1 || n>info.length)
                throw new IllegalArgumentException("N out of 1-"+info.length+" range");
            fi.longOffset = info[n-1].getOffset() + (y*fi.width+x)*fi.getBytesPerPixel();  // offset to upper left corner of ROI
            fi.offset = 0;
            fi.stripOffsets = info[n-1].stripOffsets;
            fi.stripLengths = info[n-1].stripLengths;

            //IJ.log("fi.width: "+fi.width);
            //IJ.log("fi.height: "+fi.height);
            //IJ.log("fi.getBytesPerPixel: "+fi.getBytesPerPixel());
            //IJ.log("stripLengths.length: "+fi.stripLengths.length);
            //for (int i=0; i<1; i++) {
            //	IJ.log("  stripLengths "+fi.stripLengths[i]);
            //	IJ.log("  stripOffsets  "+fi.stripLengths[i]);
            //}
            int[] newStripLengths = new int[height];
            int[] newStripOffsets = new int[height];
            for (int i=0; i<newStripLengths .length; i++) {
                newStripLengths[i] = width * fi.getBytesPerPixel();
                newStripOffsets[i] = i * fi.width * fi.getBytesPerPixel();
            }
            fi.stripOffsets = newStripOffsets;
            fi.stripLengths = newStripLengths;
            fi.height = height;
            fi.width = width;
            //IJ.log("stripLengths.length: "+fi.stripLengths.length);
            //for (int i=0; i<1; i++) {
            //	IJ.log("  stripLengths "+fi.stripLengths[i]);
            //	IJ.log("  stripOffsets  "+fi.stripLengths[i]);
            //}

        }
        long startTime = System.currentTimeMillis();
        FileOpener fo = new FileOpener(fi);
        ImagePlus imp =  fo.open(false);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("  elapsed time: " + elapsedTime);
        return imp;
    }

    public ImagePlus openCroppedTiffStackUsingGivenFileInfo(String directory, String filename, int z, int depth, int x, int width, int y, int height, FileInfo[] info) {

        log("openCroppedTiffStackUsingGivenFileInfo");
        log("  directory:" + directory);
        log("  filename:" + filename);
        log("  x:" + x);
        log("  w:" + width);
        log("  y:" + y);
        log("  h:" + height);
        log("  z:" + y);
        log("  d:" + depth);

        if (info==null) return null;

        FileInfo fi = null;

        if (info.length==1 && info[0].nImages>1) {
            /** in this case getTiffFileInfo will only open the first IDF; as this is rather fast we do it
             for every file to see different fi.offset, which does happen */
            FileInfo[] infoThisFile = new Opener().getTiffFileInfo(directory+filename);
            fi = infoThisFile[0];
            if (depth<1 || depth>fi.nImages)
                throw new IllegalArgumentException("N out of 1-"+fi.nImages+" range");
            long size = fi.width*fi.height*fi.getBytesPerPixel();
            fi.longOffset = fi.getOffset() + (depth-1)*(size+fi.gapBetweenImages);
            fi.longOffset = fi.longOffset + (y*fi.width+x)*fi.getBytesPerPixel();
            fi.offset = 0;
            fi.nImages = depth;
            int[] newStripLengths = new int[height];
            int[] newStripOffsets = new int[height];
            for (int i=0; i<newStripLengths .length; i++) {
                newStripLengths[i] = width * fi.getBytesPerPixel();
                newStripOffsets[i] = i * fi.width * fi.getBytesPerPixel();
            }
            fi.stripOffsets = newStripOffsets;
            fi.stripLengths = newStripLengths;
            fi.height = height;
            fi.width = width;

        } else {
            /** it would take to long to open all IFDs again; so we hope that the ones from the
             first file work */
            log("  IFD array case");
            fi = (FileInfo) info[0].clone();
            fi.fileName = filename;
            fi.directory = directory;
            if (depth<1 || depth>info.length)
                throw new IllegalArgumentException("N out of 1-"+info.length+" range");
            fi.longOffset = info[depth-1].getOffset() + (y*fi.width+x)*fi.getBytesPerPixel();  // offset to upper left corner of ROI
            fi.offset = 0;
            fi.stripOffsets = info[depth-1].stripOffsets;
            fi.stripLengths = info[depth-1].stripLengths;
            fi.nImages = depth;

            //IJ.log("fi.width: "+fi.width);
            //IJ.log("fi.height: "+fi.height);
            //IJ.log("fi.getBytesPerPixel: "+fi.getBytesPerPixel());
            //IJ.log("stripLengths.length: "+fi.stripLengths.length);
            //for (int i=0; i<1; i++) {
            //	IJ.log("  stripLengths "+fi.stripLengths[i]);
            //	IJ.log("  stripOffsets  "+fi.stripLengths[i]);
            //}
            int[] newStripLengths = new int[height];
            int[] newStripOffsets = new int[height];
            for (int i=0; i<newStripLengths .length; i++) {
                newStripLengths[i] = width * fi.getBytesPerPixel();
                newStripOffsets[i] = i * fi.width * fi.getBytesPerPixel();
            }
            fi.stripOffsets = newStripOffsets;
            fi.stripLengths = newStripLengths;
            fi.height = height;
            fi.width = width;
            //IJ.log("stripLengths.length: "+fi.stripLengths.length);
            //for (int i=0; i<1; i++) {
            //	IJ.log("  stripLengths "+fi.stripLengths[i]);
            //	IJ.log("  stripOffsets  "+fi.stripLengths[i]);
            //}

        }
        long startTime = System.currentTimeMillis();
        FileOpener fo = new FileOpener(fi);
        ImagePlus imp =  fo.open(false);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("  elapsed time: " + elapsedTime);
        return imp;
    }


}

