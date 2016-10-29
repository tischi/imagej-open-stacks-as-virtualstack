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

    // todo: make special version when whole image is opened
    public ImagePlus openCroppedTiffStackUsingFirstIFD(FileInfo fi0, int z) {
        int nz = fi0.nImages;
        int x = 0;
        int nx = fi0.width;
        int y = 0;
        int ny = fi0.height;
        ImagePlus imp = openCroppedTiffStackUsingFirstIFD(fi0, z, nz, x, nx,  y,  ny);
        return imp;
    }
        // todo: make special version when whole image is opened
    public ImagePlus openCroppedTiffStackUsingFirstIFD(FileInfo fi0, int z, int nz, int x, int nx, int y, int ny) {

        log("# openCroppedTiffStackUsingFirstIFD");

        if (fi0==null) return null;

        log("filename: " + fi0.fileName);
        log("z,nz,x,nx,y,ny: " + z +","+ nz +","+ x +","+ nx +","+ y +","+ ny);

        if (nz<1 || nz>fi0.nImages)
            throw new IllegalArgumentException("N out of 1-"+fi0.nImages+" range");
        // do the same for nx and ny and so on

        long startTime = System.currentTimeMillis();

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
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("opened in [ms]: " + elapsedTime);
        return imp;
    }


}

