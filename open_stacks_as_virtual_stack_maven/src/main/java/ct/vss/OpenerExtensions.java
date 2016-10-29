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

    public ImagePlus openCroppedTiffStackUsingOneIFD(FileInfo fi0, int z, int nz, int x, int nx, int y, int ny) {

        log("openCroppedTiffStackUsingFirstIFD");

        if (fi==null) return null;

        log("  directory:" + fi.directory);
        log("  filename:" + fi.fileName);
        log("  z:" + z);
        log("  nz:" + nz);
        log("  x:" + x);
        log("  nx:" + nx);
        log("  y:" + y);
        log("  ny:" + ny);

        if (nz<1 || nz>fi.nImages)
            throw new IllegalArgumentException("N out of 1-"+fi.nImages+" range");
        // do the same for nx and ny and so on

        long startTime = System.currentTimeMillis();

        fi = (FileInfo) fi0.clone(); // make a deep copy so we can savely modify it to load what we want
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
        log(" fi.gapBetweenImages: "+fi.gapBetweenImages);

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
        ImagePlus imp =  fo.open(false);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("time to open data [ms]: " + elapsedTime);
        return imp;
    }


}

