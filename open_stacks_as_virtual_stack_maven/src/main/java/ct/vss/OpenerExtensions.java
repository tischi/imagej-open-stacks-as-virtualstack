package ct.vss;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.io.Opener;

import static ij.IJ.log;

/** Opens the nth image of the specified TIFF stack.*/
class OpenerExtensions extends Opener {

    public OpenerExtensions() {

    }

    // todo: make special version when whole image is opened
    public ImagePlus openCroppedTiffStackUsingFirstIFD(FileInfo fi0, int z) {
        int nz = 1;
        int x = 0;
        int nx = fi0.width;
        int y = 0;
        int ny = fi0.height;
        ImagePlus imp = openCroppedTiffStackUsingFirstIFD(fi0, z, nz, x, nx,  y,  ny);
        return imp;
    }

    public ImagePlus openCroppedTiffStackUsingFirstIFD(FileInfo fi0, int z, int nz, int x, int nx, int y, int ny) {

        log("# openCroppedTiffStackUsingFirstIFD");

        if (fi0==null) return null;

        log("filename: " + fi0.fileName);
        log("fi0.nImages: " + fi0.nImages);
        log("z,nz,x,nx,y,ny: " + z +","+ nz +","+ x +","+ nx +","+ y +","+ ny);

        if (z<0 || z>fi0.nImages)
            throw new IllegalArgumentException("z="+z+" is out of range");
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

    // todo: make special version when whole image is opened
    public ImagePlus openCroppedTiffStackUsingIFDs(FileInfo[] info, int z) {
        int nz = 1;
        int x = 0;
        int nx = info[0].width;
        int y = 0;
        int ny = info[0].height;
        ImagePlus imp = openCroppedTiffStackUsingIFDs(info, z, nz, x, nx, y, ny);
        return imp;
    }

    public ImagePlus openCroppedTiffStackUsingIFDs(FileInfo[] info, int z, int nz, int x, int nx, int y, int ny) {

        log("# openCroppedTiffStackUsingIFDs");

        if (info==null) return null;

        //for(int i=0; i<info.length; i++) {
        //    log(""+info[i].getOffset());
        //}

        log("filename: " + info[0].fileName);
        log("z,nz,x,nx,y,ny: " + z +","+ nz +","+ x +","+ nx +","+ y +","+ ny);
        log("info.length: " + info.length);

        if (z<0 || z>info.length)
            throw new IllegalArgumentException("z="+z+" is out of range");
        // do the same for nx and ny and so on

        long startTime = System.currentTimeMillis();

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
            infoModified[iz-z].longOffset = infoModified[iz-z].getOffset();
            infoModified[iz-z].longOffset += (y*fi.width+x)*fi.getBytesPerPixel();
            infoModified[iz-z].offset = 0;
            infoModified[iz-z].stripLengths = new int[ny];
            infoModified[iz-z].stripOffsets = new int[ny];
            for (int i=0; i<ny; i++) {
                infoModified[iz-z].stripLengths[i] = nx * fi.getBytesPerPixel();
                infoModified[iz-z].stripOffsets[i] = (int) infoModified[iz-z].getOffset() + i * fi.width * fi.getBytesPerPixel();
                //infoModified[iz-z].stripOffsets[i] = (int) i * fi.width * fi.getBytesPerPixel();
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

        ImagePlus imp = openTiffStack(infoModified);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("opened in [ms]: " + elapsedTime);
        return imp;
    }


}

