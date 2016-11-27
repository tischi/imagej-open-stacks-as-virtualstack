package ct.vss;

import ij.*;
import ij.gui.*;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import java.awt.*;
import java.awt.event.*;

import static ij.IJ.log;


/**
 * Created by tischi on 31/10/16.
 */

// todo: how to behave when the track is leaving the image bounds?

public class Registration implements PlugIn, ImageListener {

    VirtualStackOfStacks vss;
    ImagePlus imp;
    public final static int MEAN=10, MEDIAN=11, MIN=12, MAX=13, VAR=14, MAXLOCAL=15; // Filters3D
    private static NonBlockingGenericDialog gd;
    private final static Point3D pOnes = new Point3D(1,1,1);
    // gui variables
    int gui_c, gui_t, gui_tMax, gui_bg;
    int gui_iterations = 6;
    int gui_dz = 1;
    int gui_dt = 1;
    Point3D gui_pStackCenter = new Point3D(100,100,10);
    Point3D gui_pStackRadii = new Point3D(100,100,10);
    Point3D gui_pCenterOfMassRadii = new Point3D(100,100,10);
    Point3D gui_pCropRadii = new Point3D(100,100,10);
    Point3D[] pTracked;
    int tMinTrack=-1, tMaxTrack=-1;
    private String gui_centeringMethod = "center of mass";

    public Registration(ImagePlus imp) {
        this.imp = imp;
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        this.vss = vss;
        this.pTracked = new Point3D[imp.getNFrames()];
        log("pTracked.length "+pTracked.length);
        ImagePlus.addImageListener(this);
    }

    public Registration() {
        // for run method
    }

    public void run(String arg) {
        this.imp = IJ.getImage();
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        this.vss = vss;
        this.pTracked = new Point3D[imp.getNFrames()];
        ImagePlus.addImageListener(this);
        showDialog();

    }

    // todo: button: Show ROI
    public void showDialog() {

        gd = new NonBlockingGenericDialog("Track & Crop");

        // set iconImage
        //ClassLoader classLoader = getClass().getClassLoader();
        //ImagePlus impIcon = IJ.openImage(classLoader.getResource("logo01-61x61.jpg").getFile());
        //if(impIcon!=null) gd.addImage(impIcon);

        //gd.addMessage("");
        gd.addSlider("Object radius x [pix]", 0, (int) imp.getWidth() / 2, 30);
        gd.addSlider("Object radius y [pix]", 0, (int) imp.getHeight() / 2, 30);
        gd.addSlider("Object radius z [pix]", 0, (int) imp.getNSlices() / 2, 5);
        //gd.addSlider("Tracking dz [pix]", 1, (int) imp.getNSlices() / 2, 1);
        //gd.addSlider("Tracking dt [frames]", 1, Math.max(1, (int) imp.getNFrames()/5), 1);
        //gd.addNumericField("Tracking margin factor", 2, 1);
        gd.addNumericField("Image background value", 100, 0);
        //gd.addNumericField("Center computation iterations", 6, 0);
        gd.addSlider("Track until [frame]", 1, (int) imp.getNFrames(), imp.getNFrames());
        //gd.addSlider("Browse track", 1, (int) imp.getNFrames(), 1);
        gd.addSlider("Track channel", 1, (int) imp.getNChannels(), 1);
        //String [] centeringMethodChoices = {"centroid","center of mass"};
        //gd.addChoice("Centering method", centeringMethodChoices, "center of mass");

        Button btCorrectCurrent = new Button("Locate");
        btCorrectCurrent.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (updateGuiVariables()) {
                    // only 'track' current position
                    Thread t1 = new Thread(new Runnable() {
                        public void run() {
                            try {
                                track3D(gui_c, gui_t, gui_t, 1, gui_dz, gui_pStackCenter, gui_pStackRadii, gui_pCenterOfMassRadii, gui_bg, gui_iterations);
                                showTrackOnFrame(imp);
                            } finally {
                                //...
                            }
                            }
                    });
                    t1.start();
                }

            }
        });

        Button btTrack = new Button("Track");
        btTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updateGuiVariables()) {
                    // clean up track
                    for(int i=0; i<pTracked.length; i++) {
                        pTracked[i] = null;
                    }
                    tMinTrack=gui_t; tMaxTrack=gui_tMax;
                    Thread t1 = new Thread(new Runnable() {
                        public void run() {
                            track3D(gui_c, gui_t, gui_tMax, gui_dt, gui_dz, gui_pStackCenter, gui_pStackRadii, gui_pCenterOfMassRadii, gui_bg, gui_iterations);
                            showTrackOnFrame(imp);
                        }
                    });
                    t1.start();
                }
            }
        });
        /*
        Button btSaveTrack = new Button("Save coordinates");
        btSaveTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updateGuiVariables()) {
                    IJ.showMessage("Not yet implemented.\n Please contact tischitischer@gmail.com if you need this feature.");
                }
            }s
        });*/
        Button btCropTrack = new Button("Crop along Track");
        btCropTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updateGuiVariables()) {
                    FileInfoSer[][][] infos = vss.getFileInfosSer();
                    //Point3D[] pos = new Point3D[tMaxTrack-tMinTrack];
                    //System.arraycopy(pTracked, tMinTrack, pos, 0, tMaxTrack-tMinTrack);

                    ImagePlus impCropped = OpenStacksAsVirtualStack.openCroppedCenterRadiusFromInfos(imp, infos, pTracked, gui_pCropRadii, tMinTrack, tMaxTrack);

                    impCropped.show();
                    impCropped.setPosition(0, (int)(impCropped.getNSlices()/2+0.5), 0);
                    impCropped.resetDisplayRange();
                }
            }
        });

        /*Button btReviewTrack = new Button("Review track");
        btReviewTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (updateGuiVariables()) {
                    FileInfoSer[][][] infos = vss.getFileInfosSer();
                    //Point3D[] pos = new Point3D[tMaxTrack-tMinTrack];
                    //System.arraycopy(pTracked, tMinTrack, pos, 0, tMaxTrack-tMinTrack);
                    Point3D[] pTrackCenters = new Point3D[imp.getNFrames()];
                    for(int i=0; i<pTrackCenters.length; i++) {
                        pTrackCenters[i] = pTracked[(int) (tMinTrack + (tMaxTrack - tMinTrack) / 2)];
                        log(""+pTrackCenters[i].toString());
                    }
                    ImagePlus impCropped = OpenStacksAsVirtualStack.openCroppedCenterRadiusFromInfos(imp, infos, pTrackCenters, gui_pCropRadii, tMinTrack, tMaxTrack);

                    impCropped.show();
                    impCropped.setPosition(0, (int)(impCropped.getNSlices()/2+0.5), 0);
                    impCropped.resetDisplayRange();
                }
            }
        });*/


        //final Scrollbar sbCurrentFrame = (Scrollbar) gd.getSliders().get(6);
        //sbCurrentFrame.addAdjustmentListener(new AdjustmentListener() {
        //    @Override
        //    public void adjustmentValueChanged(AdjustmentEvent e) {
        //        //log("Frame scrollbar:"+sbCurrentFrame.getValue());
        //        imp.setPosition(imp.getC(), imp.getZ(), new Integer(sbCurrentFrame.getValue()));
        //        showTrackOnFrame();
        //    }
        //});

        final Panel buttons = new Panel();
        GridBagLayout bgbl = new GridBagLayout();
        buttons.setLayout(bgbl);
        GridBagConstraints bgbc = new GridBagConstraints();
        bgbc.anchor = GridBagConstraints.EAST;

        //bgbc.insets = new Insets(0,0,0,5);
        //bgbl.setConstraints(btSaveTrack, bgbc);
        //buttons.add(btSaveTrack);

        //bgbc.insets = new Insets(0,0,0,5);
        //bgbl.setConstraints(btReviewTrack, bgbc);
        //buttons.add(btReviewTrack);


        bgbc.insets = new Insets(0,0,0,0);
        bgbl.setConstraints(btCorrectCurrent,bgbc);
        buttons.add(btCorrectCurrent);

        bgbc.insets = new Insets(0,0,0,5);
        bgbl.setConstraints(btTrack,bgbc);
        buttons.add(btTrack);

        bgbc.insets = new Insets(0,0,0,5);
        bgbl.setConstraints(btCropTrack, bgbc);
        buttons.add(btCropTrack);



        gd.addPanel(buttons,GridBagConstraints.EAST,new Insets(5,5,5,5));
        bgbl = (GridBagLayout)gd.getLayout();
        bgbc = bgbl.getConstraints(buttons); bgbc.gridx = 0;
        bgbl.setConstraints(buttons,bgbc);


        // gd location
        int gdX = (int) imp.getWindow().getLocationOnScreen().getX() + imp.getWindow().getWidth() + 10;
        int gdY = (int) imp.getWindow().getLocationOnScreen().getY() + 30;
        gd.centerDialog(false);
        gd.setLocation(gdX, gdY);

        gd.getHeight();

        // log window location
        log("# Registration");
        Window lw = WindowManager.getFrame("Log");
        if (lw!=null) {
            lw.setLocation(gdX, gdY+450);
            lw.setSize(600, 300);
        }

        // add logging checkbox
        final Checkbox cbLogging = new Checkbox("Verbose logging", false);
        cbLogging.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                Globals.verbose = cbLogging.getState();
            }
        });

        final Panel panel0 = new Panel();
        GridBagLayout gbl0 = new GridBagLayout();
        GridBagConstraints gbc0 = new GridBagConstraints();
        panel0.setLayout(gbl0);
        gbc0.anchor = GridBagConstraints.EAST;

        gbc0.insets = new Insets(0,0,0,5);
        gbl0.setConstraints(cbLogging, gbc0);
        panel0.add(cbLogging);

        gd.addPanel(panel0,GridBagConstraints.EAST,new Insets(5,5,5,5));
        //bgbl = (GridBagLayout)gd.getLayout();
        //bgbc = bgbl.getConstraints(buttons); bgbc.gridx = 0;
        //bgbl.setConstraints(buttons,bgbc);

        //gd.add(cbLogging);

        gd.addHelp("https://github.com/tischi/imagej-open-stacks-as-virtualstack/blob/master/README.md");


        //gd.setForeground(Color.white);
        gd.showDialog();

    }

    public void imageClosed(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageOpened(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageUpdated(ImagePlus imp) {
        // has the slice been changed?
        if(imp == this.imp) {
            showTrackOnFrame(imp);
        } else {
            //
        }
    }

    public boolean updateGuiVariables() {
        Roi roi = imp.getRoi();

        if ((roi != null) && (roi.getPolygon().npoints == 1)) {

            // get values
            int x = roi.getPolygon().xpoints[0];
            int y = roi.getPolygon().ypoints[0];
            int z = imp.getZ() - 1;
            gui_t = imp.getT() - 1;

            int iTxt = 0, iChoice = 0;
            int rx = new Integer(getTextFieldTxt(gd, iTxt++));
            int ry = new Integer(getTextFieldTxt(gd, iTxt++));
            int rz = new Integer(getTextFieldTxt(gd, iTxt++));
            //gui_dz = new Integer(getTextFieldTxt(gd, iTxt++));
            //gui_dt = new Integer(getTextFieldTxt(gd, iTxt++));
            double marginFactor = 1; //new Double(getTextFieldTxt(gd, iTxt++));
            gui_bg = new Integer(getTextFieldTxt(gd, iTxt++));
            //gui_iterations = new Integer(getTextFieldTxt(gd, iTxt++));
            gui_tMax = (new Integer(getTextFieldTxt(gd, iTxt++))) - 1;
            gui_c = (new Integer(getTextFieldTxt(gd, iTxt++))) - 1;
            //Choice centeringMethod = (Choice) gd.getChoices().get(iChoice++);
            //gui_centeringMethod = centeringMethod.getSelectedItem();

            //double marginFactorCrop = new Double(getTextFieldTxt(gd, iTxt++));

            gui_pCenterOfMassRadii = new Point3D(rx, ry, rz);
            gui_pStackRadii = gui_pCenterOfMassRadii.multiply(marginFactor);
            gui_pCropRadii = gui_pCenterOfMassRadii.multiply(marginFactor);
            gui_pStackCenter = new Point3D(x, y, z);

            return(true);

        } else {

            IJ.showMessage("Please use IJ's 'Point selection tool' to mark an object in your image.");
            return(false);

        }

    }

    public void showTrackOnFrame(ImagePlus imp) {
        Point3D pCenter = pTracked[imp.getT() - 1];
        if (pCenter != null) {
            if(Globals.verbose) {
                log("Registration.showTrackOnFrame pTracked: "+pCenter.toString());
            }
            //log("showTrackOnFrame: pCenter: "+pCenter.toString());
            int rx = (int) gui_pStackRadii.getX();
            int ry = (int) gui_pStackRadii.getY();
            Roi r = new PointRoi(pCenter.getX(), pCenter.getY());
            Overlay o = new Overlay();
            Roi cropBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);
            //o.add(cropBounds);
            rx = (int) gui_pCenterOfMassRadii.getX();
            ry = (int) gui_pCenterOfMassRadii.getY();
            Roi comBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);
            o.add(comBounds);
            imp.setPosition(imp.getC(), ((int) pCenter.getZ() + 1), imp.getT());
            imp.deleteRoi();
            imp.setRoi(r);
            o.setLabelColor(Color.blue);
            imp.setOverlay(o);
        } else {
            if(Globals.verbose) {
                log("Registration.showTrackOnFrame: No track available for this time point");
            }
            Overlay o = new Overlay();
            imp.setOverlay(o);
        }
    }

    public String getTextFieldTxt(GenericDialog gd, int i) {
        TextField tf = (TextField) gd.getNumericFields().get(i);
        return(tf.getText());
    }

    public void track3D(int c, int t, int tMax, int dt, int dz, Point3D pStackCenter, Point3D pStackRadii, Point3D pCenterOfMassRadii, int bg, int iterations) {
        ImageStack stack;
        Point3D pOffset, pLocalCenter;
        long startTime, stopTime, elapsedTime;

        if(Globals.verbose) {
            log("Registration.track3D:");
            log("t, tMax, dt, dz "+t+","+tMax+","+dt+","+dz);
            log("pTracked.length "+pTracked.length);
        }

        for (int it=t; it<=tMax; it=it+dt) {

            log("### track3D: Analzying time-point "+it);

            // get stack, ensuring that extracted stack is still within bounds
            pStackCenter = curatePosition(pStackCenter, pStackRadii);
            startTime = System.currentTimeMillis();
            stack = getImageStack(it, c, dz, pStackCenter, pStackRadii);
            stopTime = System.currentTimeMillis(); elapsedTime = stopTime - startTime;
            log("Loaded data [ms]: " + elapsedTime);
            log("Loading speed [MB/s]: " + stack.getHeight()*stack.getWidth()*stack.getSize()*2/((elapsedTime+0.001)*1000));

            // compute center of mass (in zero-based local stack coordinates)
            startTime = System.currentTimeMillis();
            pLocalCenter = iterativeCenterOfMass16bit(stack, bg, pCenterOfMassRadii, iterations);
            // correct for the sub-sampling in z
            pLocalCenter = new Point3D(pLocalCenter.getX(),pLocalCenter.getY(),dz*pLocalCenter.getZ());
            stopTime = System.currentTimeMillis(); elapsedTime = stopTime - startTime;
            log("Computed center of mass [ms]: " + elapsedTime);

            // compute offset to zero-based center of stack
            pOffset = pLocalCenter.subtract(pStackRadii);
            //pOffset = pOffset.subtract(pOnes);
            log("Detected shift [pixel]:"+
                    " x:"+String.format("%.2g", pOffset.getX())+
                    " y:"+String.format("%.2g", pOffset.getY())+
                    " z:"+String.format("%.2g", pOffset.getZ()));

            // update time-points, using linear interpolation
            for(int j=0; j<dt; j++) {
                if((it+j)<pTracked.length)
                    pTracked[it+j] = pStackCenter.add(pOffset.multiply((j+1.0)/dt));
            }

            // update next center using Brownian motion model (position of next is same as this)
            // todo:
            // - also have a linear motion model for the update, i.e. add pOffset*2
            pStackCenter = pStackCenter.add(pOffset);

            IJ.showStatus(""+it+"/"+tMax);
            IJ.showProgress((double)(it-t)/(tMax-t));

        }
        log("Tracking done.");
        IJ.showStatus(""+tMax+"/"+tMax);
        IJ.showProgress(1.0);
        log("");
        return;
    }

    private ImageStack getImageStack(int t, int c, int dz, Point3D p, Point3D pr) {
        //log("Registration.getImageStack p[0]: "+p[0]+" z:"+p[3]);
        if(Globals.verbose) {
            log("Registration.getImageStack");
            log(""+imp.getStackIndex(c+1,1,t+1));
        }

        long startTime = System.currentTimeMillis();
        ImageStack stack = vss.getCroppedFrameCenterRadii(t, c, dz, p, pr).getStack();
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime;
        //log("loaded stack in [ms]: " + elapsedTime);
        //imp.show();
        return(stack);
    }

    public Point3D curatePosition(Point3D p, Point3D pr) {

        // round the values
        int x = (int) (p.getX()+0.5);
        int y = (int) (p.getY()+0.5);
        int z = (int) (p.getZ()+0.5);
        int rx = (int) (pr.getX()+0.5);
        int ry = (int) (pr.getY()+0.5);
        int rz = (int) (pr.getZ()+0.5);

        // make sure that the ROI stays within the image bounds
        if (x-rx < 0) x = rx;
        if (y-ry < 0) y = ry;
        if (z-rz < 0) z = rz;

        if (x+rx > imp.getWidth()-1) x = imp.getWidth()-rx;
        if (y+ry > imp.getHeight()-1) y = imp.getHeight()-ry;
        if (z+rz > imp.getNSlices()-1) z = imp.getNSlices()-rz;

        // check if it is ok now, otherwise the chose radius is simply too large
        if (x-rx < 0)  {
            IJ.showMessage("x_radius*margin_factor is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        if (y-ry < 0){
            IJ.showMessage("y_radius*margin_factor is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }
        if (z-rz < 0) {
            IJ.showMessage("z_radius*margin_factor is too large; please reduce!");
            throw new IllegalArgumentException("out of range");
        }

        return(new Point3D(x,y,z));
    }

    public Point3D iterativeCenterOfMass16bit(ImageStack stack, int bg, Point3D radii, int iterations) {
        Point3D pMin, pMax;

        Point3D pCenter = new Point3D(stack.getWidth()/2+0.5,stack.getHeight()/2+0.5,stack.getSize()/2+0.5);
        //log(""+radii.toString());
        //log(""+pCenter.toString());

        for(int i=0; i<iterations; i++) {
            //log("# Iteration "+i);
            pMin = pCenter.subtract(radii);
            pMax = pCenter.add(radii);
            pCenter = computeCenter16bit(stack, bg, pMin, pMax);
            //log("iterativeCenterOfMass16bit: center: "+pCenter.toString());
        }
        return(pCenter);
    }

    public Point3D computeCenter16bit(ImageStack stack, int bg, Point3D pMin, Point3D pMax) {

        //long startTime = System.currentTimeMillis();
        double sum = 0.0, xsum = 0.0, ysum = 0.0, zsum = 0.0;
        int i, v;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xmin = 0 > (int) pMin.getX() ? 0 : (int) pMin.getX();
        int xmax = (width-1) < (int) pMax.getX() ? (width-1) : (int) pMax.getX();
        int ymin = 0 > (int) pMin.getY() ? 0 : (int) pMin.getY();
        int ymax = (height-1) < (int) pMax.getY() ? (height-1) : (int) pMax.getY();
        int zmin = 0 > (int) pMin.getZ() ? 0 : (int) pMin.getZ();
        int zmax = (depth-1) < (int) pMax.getZ() ? (depth-1) : (int) pMax.getZ();

        Point3D pMinCorr = new Point3D(xmin,ymin,zmin);
        Point3D pMaxCorr = new Point3D(xmax,ymax,zmax);

        /*
        log("centerOfMass16bit: pMin: "+pMin.toString());
        log("centerOfMass16bit: pMax: "+pMax.toString());
        log("centerOfMass16bit: pMinCorr: "+pMinCorr.toString());
        log("centerOfMass16bit: pMaxCorr: "+pMaxCorr.toString());
        */

        // compute one-based, otherwise the numbers at x=0,y=0,z=0 are lost for the center of mass
        for(int z=zmin+1; z<=zmax+1; z++) {
            ImageProcessor ip = stack.getProcessor(z);
            short[] pixels = (short[]) ip.getPixels();
            i = 0;
            for (int y = ymin+1; y<=ymax+1; y++) {
                i = (y-1) * width + xmin; // zero-based location in pixel array
                for (int x = xmin+1; x<=xmax+1; x++) {
                    v = pixels[i] & 0xffff;
                    if (v >= bg) {
                        if (gui_centeringMethod == "center of mass") {
                            sum += v;
                            xsum += x * v;
                            ysum += y * v;
                            zsum += z * v;
                        } else if (gui_centeringMethod == "centroid") {
                            sum += 1;
                            xsum += x;
                            ysum += y;
                            zsum += z;
                        } // could add maximum here
                    }
                    i++;
                }
            }
        }
        // computation is one-based; result should be zero-based
        double xCenter = (xsum / sum) - 1;
        double yCenter = (ysum / sum) - 1;
        double zCenter = (zsum / sum) - 1;

        //long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime; log("center of mass in [ms]: " + elapsedTime);

        return(new Point3D(xCenter,yCenter,zCenter));
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


    /*
    public void addTrackAsOverlay() {
        Point3D pCenter;
        Overlay o = new Overlay();

        for(int i=0; i<pTracked.length; i++) {
            pCenter = pTracked[i];
            if (pCenter != null) {
                //log("showTrackOnFrame: pCenter: "+pCenter.toString());
                int rx = (int) gui_pStackRadii.getX();
                int ry = (int) gui_pStackRadii.getY();
                Roi r = new PointRoi(pCenter.getX(), pCenter.getY());
                Roi cropBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);
                cropBounds.set
                o.add(cropBounds);
                rx = (int) gui_pCenterOfMassRadii.getX();
                ry = (int) gui_pCenterOfMassRadii.getY();
                Roi comBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);
                o.add(comBounds);
                imp.setPosition(0, ((int) pCenter.getZ() + 1), imp.getT());
                imp.deleteRoi();
                imp.setRoi(r);
                o.setLabelColor(Color.blue);
            }
        }
        imp.setOverlay(o);
    }*/


// http://imagej.1557.x6.nabble.com/Getting-x-y-coordinates-of-the-multi-point-tool-td4490440.html



/*
class TrackingGUI implements ActionListener, ImageListener {

    String[] actions = {
            "Open from stacks",
            "Open from info file",
            "Save as info file",
            "Save as tiff stacks",
            "Save as h5 stacks",
            "Crop",
            "Duplicate to RAM"};

    public void TrackingGUI() {
    }


    public void showDialog() {

        ImagePlus.addImageListener(this);

        JFrame frame = new JFrame("Tracking");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container c = frame.getContentPane();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));

        gd.addSlider("Object radius x [pix]", 0, (int) imp.getWidth() / 2, 30);
        gd.addSlider("Object radius y [pix]", 0, (int) imp.getHeight() / 2, 30);
        gd.addSlider("Object radius z [pix]", 0, (int) imp.getNSlices() / 2, 5);


        JButton[] buttons = new JButton[actions.length];

        for (int i = 0; i < buttons.length; i++) {
            buttons[i] = new JButton(actions[i]);
            buttons[i].setActionCommand(actions[i]);
            buttons[i].addActionListener(this);
        }

        int i = 0, j = 0;

        JPanel[] panels = new JPanel[3];

        panels[j] = new JPanel();
        panels[j].add(buttons[i++]);
        panels[j].add(buttons[i++]);
        c.add(panels[j++]);

        panels[j] = new JPanel();
        panels[j].add(buttons[i++]);
        panels[j].add(buttons[i++]);
        panels[j].add(buttons[i++]);
        c.add(panels[j++]);

        panels[j] = new JPanel();
        panels[j].add(buttons[i++]);
        panels[j].add(buttons[i++]);
        c.add(panels[j++]);

        //button.setAlignmentX(Component.CENTER_ALIGNMENT);

        //Display the window.
        frame.pack();
        frame.setVisible(true);

    }

    public void imageClosed(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageOpened(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageUpdated(ImagePlus imp) {
        // has the slice been changed?
        int slice = imp.getCurrentSlice();
        log("current slice: "+slice);
    }


    public void actionPerformed(ActionEvent e) {
        int i = 0;
        final OpenStacksAsVirtualStack osv = new OpenStacksAsVirtualStack();

        if (e.getActionCommand().equals(actions[i++])) {
            // Open from folder
            final String directory = IJ.getDirectory("Select a Directory");
            if (directory == null)
                return;

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    ImagePlus imp = osv.openFromDirectory(directory, null);
                    imp.show();
                }
            });
            t1.start();

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    osv.updateStatus();
                }
            });
            t2.start();

        } else if (e.getActionCommand().equals(actions[i++])) {

            // Open from file
            String filePath = IJ.getFilePath("Select *.ser file");
            if (filePath == null)
                return;
            File file = new File(filePath);
            ImagePlus imp = osv.openFromInfoFile(file.getParent() + "/", file.getName());
            imp.show();

        } else if (e.getActionCommand().equals(actions[i++])) {
            // "Save as info file"
            IJ.showMessage("Not yet implemented.");
        } else if (e.getActionCommand().equals(actions[i++])) {
            // "Save as tiff stacks"
            IJ.showMessage("Not yet implemented.");
        } else if (e.getActionCommand().equals(actions[i++])) {
            // "Save as h5 stacks"
            IJ.showMessage("Not yet implemented.");
        }  else if (e.getActionCommand().equals(actions[i++])) {
            // crop
            ImagePlus imp2 = osv.crop(IJ.getImage());
            if (imp2 != null)
                imp2.show();

        } else if (e.getActionCommand().equals(actions[i++])) {
            // duplicate to RAM
            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    ImagePlus imp2 = osv.duplicateToRAM(IJ.getImage());
                    if (imp2 != null)
                        imp2.show();

                }
            });
            t1.start();

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    osv.updateStatus();
                }
            });
            t2.start();

        }



    }
}

*/