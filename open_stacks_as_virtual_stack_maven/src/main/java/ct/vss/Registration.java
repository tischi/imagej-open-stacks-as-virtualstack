package ct.vss;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import static ij.IJ.log;

/**
 * Created by tischi on 31/10/16.
 */

public class Registration {
    VirtualStackOfStacks vss;
    int nx, ny, nz;

    public Registration(VirtualStackOfStacks vss) {
        this.vss = vss;
    }

    public Positions3D computeDrifts3D(int t, int nt, int z, int nz, int x, int nx, int y, int ny, String method, int bg) {
        Positions3D positions = new Positions3D(nt, t, vss.getWidth(), vss.getHeight(), vss.nSlices, nx, ny, nz);
        ImageStack stack;
        Point3D comRef, comCurr, comDiff;
        Point3D posCurr = new Point3D(x, y, z);

        this.nx = nx;
        this.ny = ny;
        this.nz = nz;

        // set position of reference image
        positions.setPosition(t, posCurr);

        // compute reference center of mass
        comRef = centerOfMass16bit(getImageStack(positions.getPosition(t)), bg);
        log("comRef "+t+": "+comRef.toString());

        for (int it = t + 1; it < nt; it++) {

            // use current position to extract next image
            positions.setPosition(it, posCurr); // update position of this image

            // compute reference center of mass at next predicted position
            comCurr = centerOfMass16bit(getImageStack(positions.getPosition(it)), bg);
            log("comCurr "+it+": "+comCurr.toString());

            // compute difference
            comDiff = comCurr.subtract(comRef);
            log("comDiff "+it+": "+comDiff.toString());
            posCurr = posCurr.add(comDiff);

            // update
            // todo:
            // - make this save for out-of-bouds
            // - also have a linear motion model

            // update position of this image; this will make sure that it is within the bounds
            positions.setPosition(it, posCurr);

        }
        log("Drift correction done.");
        log("");
        return (positions);
    }

    private ImageStack getImageStack(int[] p) {
        log("Registration.getImageStack p[0]: "+p[0]);
        ImagePlus imp = vss.getCroppedFrameAsImagePlus(p[0], 0, p[3], nz, p[1], nx, p[2], ny);
        imp.show();
        return(imp.getStack());
    }

    public Point3D centerOfMass16bit(ImageStack stack, int bg) {
        long startTime = System.currentTimeMillis();
        double sum = 0.0, xsum = 0.0, ysum = 0.0, zsum = 0.0;
        int i, v;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();

        for(int z=1; z <= depth; z++) {
            ImageProcessor ip = stack.getProcessor(z);
            short[] pixels = (short[]) ip.getPixels();
            i = 0;
            for (int y = 1; y <= height; y++) {
                i = (y-1) * width;
                for (int x = 1; x <= width; x++) {
                    v = pixels[i] & 0xffff;
                    if (v >= bg) {
                        sum += v;
                        xsum += x * v;
                        ysum += y * v;
                        zsum += z * v;
                    }
                    i++;
                }
            }
        }
        // computation is one-based; result should be zero-based
        double xCenterOfMass = (xsum / sum) - 1;
        double yCenterOfMass = (ysum / sum) - 1;
        double zCenterOfMass = (zsum / sum) - 1;

        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xCenterOfMass,yCenterOfMass,zCenterOfMass));
    }

}
