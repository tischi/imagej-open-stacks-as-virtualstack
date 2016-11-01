package ct.vss;

import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import ij.plugin.Filters3D;
import static ij.IJ.log;
import ij.plugin.frame.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;

/**
 * Created by tischi on 31/10/16.
 */

// https://imagej.nih.gov/ij/developer/source/ij/plugin/frame/ContrastAdjuster.java.html

public class Registration implements PlugIn {

    VirtualStackOfStacks vss;
    ImagePlus imp;
    public final static int MEAN=10, MEDIAN=11, MIN=12, MAX=13, VAR=14, MAXLOCAL=15; // Filters3D
    private static NonBlockingGenericDialog gd;

    public Registration(ImagePlus imp, boolean gui) {
        this.imp = imp;
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        this.vss = vss;
        if(gui) showDialog();
    }

    public void run(String arg) {

    }
    // todo: button: Show ROI
    public void showDialog() {
        gd = new NonBlockingGenericDialog("Registration");
        gd.addSlider("Radius x:", 0, (int) imp.getWidth() / 2, 40);
        gd.addSlider("Radius y:", 0, (int) imp.getHeight() / 2, 40);
        gd.addSlider("Radius z:", 0, (int) imp.getNSlices() / 2, 20);
        gd.addNumericField("Image background",100,0);
        gd.addNumericField("Iterations",5,0);
        //gd.addStringField("File Name Contains:", "");
        //((Label)theLabel).setText(""+imp.getTitle());
        Button bt = new Button("Recompute position");
        bt.addActionListener(new ActionListener() {
                                 public void actionPerformed(ActionEvent e) {
                                     Roi roi = imp.getRoi();
                                     Scrollbar s;
                                     TextField tf;
                                     Point3D p = new Point3D(0,0,0);
                                     if ((roi != null) && (roi.getPolygon().npoints == 1)) {

                                         // get values
                                         int x = roi.getPolygon().xpoints[0];
                                         int y = roi.getPolygon().ypoints[0];
                                         int z = imp.getZ() - 1;
                                         int t = imp.getT() - 1;
                                         //int nx = (int) getNextNumber();
                                         log("" + t + " " + z + " " + x + " " + y);
                                         s = (Scrollbar)gd.getSliders().get(0);
                                         int rx = (int) s.getValue();
                                         s = (Scrollbar)gd.getSliders().get(1);
                                         int ry = (int) s.getValue();
                                         s = (Scrollbar)gd.getSliders().get(2);
                                         int rz = (int) s.getValue();
                                         tf = (TextField) gd.getNumericFields().get(3);
                                         int bg = new Integer(tf.getText());
                                         tf = (TextField) gd.getNumericFields().get(4);
                                         int iterations = new Integer(tf.getText());
                                         // compute new center
                                         // todo
                                         // make faster by loading a bit generous?
                                         for(int i=0; i<iterations; i++) {
                                             log("ITERATION "+i);
                                             ImageStack stack = getImageStack(t, new Point3D(x-rx, y-ry, z-rz), 2*rx+1, 2*ry+1, 2*rz+1);
                                             // computes center of mass in cropped region
                                             p = centerOfMass16bit(stack, bg);
                                             x = (int)(p.getX()+x-rx); y=(int)(p.getY()+y-ry); z=(int)(p.getZ()+z-rz);
                                         }

                                         // show on image, computing back to global coordinates
                                         Roi r = new PointRoi(x, y);
                                         Roi bounds = new Roi(x-rx,y-ry,2*rx+1,2*ry+1);
                                         imp.setPosition(0, (int) p.getZ() + z + 1, t + 1);
                                         imp.deleteRoi();
                                         imp.setRoi(r);
                                         imp.setOverlay(bounds, Color.blue, 2, null);
                                     } else {
                                         log("No PointTool selection");
                                     }
                                 }
                             });
        gd.add(bt);
        //gd.addDialogListener(this);
        gd.showDialog();
    }

    //public boolean dialogItemChanged(GenericDialog gd, AWTEvent awtEvent) {
    //    log("aaaa");
        //});

    //    return false;
    //}

    public Point3D[] computeDrifts3D(int t, int nt, int z, int nz, int x, int nx, int y, int ny, String method, int bg) {
        Point3D[] points = new Point3D[vss.nSlices];
        ImageStack stack;
        Point3D pRef, pCurr, pDiff;
        Point3D posGlobalCurr = new Point3D(x, y, z);

        int it = t;
        // set position of reference image
        points = setPosition(points, it, posGlobalCurr, nx, ny, nz);
        stack = getImageStack(it,points[it], nx, ny, nz);
        // compute internal position
        //long startTime = System.currentTimeMillis();
        //stack = Filters3D.filter(stack, MEAN, 4, 4, 2);
        //long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("filtered stack in [ms]: " + elapsedTime);
        pRef = centerOfMass16bit(stack, bg);
        //pRef = maxLoc16bit(stack);
        log("pRef "+t+": "+pRef.toString());

        for (it = t + 1; it < nt; it++) {

            // use current position to extract next image
            points = setPosition(points, it, posGlobalCurr, nx, ny, nz); // update position of this image

            // compute internal position
            stack = getImageStack(it,points[it],nx,ny,nz);
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
            points = setPosition(points, it, posGlobalCurr, nx, ny, nz);

        }
        log("Drift correction done.");
        log("");
        return(points);
    }

    private ImageStack getImageStack(int it, Point3D p, int nx, int ny, int nz) {
        //log("Registration.getImageStack p[0]: "+p[0]+" z:"+p[3]);
        long startTime = System.currentTimeMillis();
        ImagePlus imp = vss.getCroppedFrameAsImagePlus(it, 0, (int)p.getZ(), nz, (int)p.getX(), nx, (int)p.getY(), ny);
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("loaded stack in [ms]: " + elapsedTime);
        //imp.show();
        return(imp.getStack());
    }

    public Point3D[] setPosition(Point3D[] points, int it, Point3D p, int nx, int ny, int nz) {

        if (it < 0 || it >= points.length) {
            throw new IllegalArgumentException("t="+it+" is out of range");
        }

        // round the values
        int x = (int) (p.getX()+0.5);
        int y = (int) (p.getY()+0.5);
        int z = (int) (p.getZ()+0.5);

        // make sure that the ROI stays within the image bounds
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (z < 0) z = 0;

        if (x+nx >= imp.getWidth()) x = imp.getWidth()-nx;
        if (y+ny >= imp.getHeight()) y = imp.getHeight()-ny;
        if (z+nz >= imp.getNSlices()) z = imp.getNSlices()-nz;

        points[it] = new Point3D(x,y,z);

        return(points);
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


// http://imagej.1557.x6.nabble.com/Getting-x-y-coordinates-of-the-multi-point-tool-td4490440.html


class RegistrationDialog extends NonBlockingGenericDialog {
    ImagePlus imp;

    public RegistrationDialog(ImagePlus imp) {
        super("Registration");
        this.imp = imp;
    }



    protected void setup() {
        //setPositionInfo();
    }

    public void itemStateChanged(ItemEvent e) {
        log(""+e);
    }

    public void keyTyped(KeyEvent e) {
        log(""+e.getKeyChar());
    }


    public void textValueChanged(TextEvent e) {
        //setStackInfo();
    }

    //public void actionPerformed(java.awt.event.ActionEvent e){
    //    log(""+e);
    //}

    //void setPositionInfo() {
    //    ((Label)theLabel).setText(""+imp.getTitle());
   // }


}

