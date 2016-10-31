package ct.vss;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import ij.plugin.Filters3D;
import static ij.IJ.log;

/**
 * Created by tischi on 31/10/16.
 */

public class Registration {
    VirtualStackOfStacks vss;
    int nx, ny, nz;
    public final static int MEAN=10, MEDIAN=11, MIN=12, MAX=13, VAR=14, MAXLOCAL=15; // Filters3D

    public Registration(VirtualStackOfStacks vss) {
        this.vss = vss;
    }

    public Positions3D computeDrifts3D(int t, int nt, int z, int nz, int x, int nx, int y, int ny, String method, int bg) {
        Positions3D positions = new Positions3D(nt, t, vss.getWidth(), vss.getHeight(), vss.nSlices, nx, ny, nz);
        ImageStack stack;
        Point3D pRef, pCurr, pDiff;
        Point3D posGlobalCurr = new Point3D(x, y, z);

        this.nx = nx;
        this.ny = ny;
        this.nz = nz;

        int it = t;
        // set position of reference image
        positions.setPosition(it, posGlobalCurr);
        stack = getImageStack(positions.getPosition(it));
        // compute internal position
        //long startTime = System.currentTimeMillis();
        //stack = Filters3D.filter(stack, MEAN, 4, 4, 2);
        //long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("filtered stack in [ms]: " + elapsedTime);
        pRef = centerOfMass16bit(stack, bg);
        //pRef = maxLoc16bit(stack);
        log("pRef "+t+": "+pRef.toString());

        for (it = t + 1; it < nt; it++) {

            // use current position to extract next image
            positions.setPosition(it, posGlobalCurr); // update position of this image

            // compute internal position
            stack = getImageStack(positions.getPosition(it));
            pCurr = centerOfMass16bit(stack, bg);
            //pCurr = maxLoc16bit(stack);
            log("pCurr "+it+": "+pCurr.toString());

            // compute difference
            pDiff = pCurr.subtract(pRef);
            log("pDiff "+it+": "+pDiff.toString());
            posGlobalCurr = posGlobalCurr.add(pDiff);

            // update
            // todo:
            // - make this save for out-of-bouds
            // - also have a linear motion model

            // update position of this image; this will make sure that it is within the bounds
            positions.setPosition(it, posGlobalCurr);

        }
        log("Drift correction done.");
        log("");
        return (positions);
    }

    private ImageStack getImageStack(int[] p) {
        //log("Registration.getImageStack p[0]: "+p[0]+" z:"+p[3]);
        long startTime = System.currentTimeMillis();
        ImagePlus imp = vss.getCroppedFrameAsImagePlus(p[0], 0, p[3], nz, p[1], nx, p[2], ny);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("loaded stack in [ms]: " + elapsedTime);
        //imp.show();
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

    public Point3D maxLoc16bit(ImageStack stack) {
        long startTime = System.currentTimeMillis();
        int vmax = 0, xmax = 0, ymax = 0, zmax = 0;
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
                    if (v > vmax) {
                        xmax = x;
                        ymax = y;
                        zmax = z;
                        vmax = v;
                    }
                    i++;
                }
            }
        }

        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xmax,ymax,zmax));
    }

}
