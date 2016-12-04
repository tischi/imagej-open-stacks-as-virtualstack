package ct.vss;

import ij.*;
import ij.gui.*;
import ij.io.Opener;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;

import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.lang.String;

import static ij.IJ.log;


/**
 * Created by tischi on 31/10/16.
 */

// todo: how to behave when the track is leaving the image bounds?

public class Registration implements PlugIn, ImageListener {

    VirtualStackOfStacks vss;
    ImagePlus imp;
    ImagePlus impTR; // track review
    int tTR = 0;
    public final static int MEAN=10, MEDIAN=11, MIN=12, MAX=13, VAR=14, MAXLOCAL=15; // Filters3D
    private static NonBlockingGenericDialog gd;
    private final static Point3D pOnes = new Point3D(1,1,1);
    // gui variables
    int gui_c, gui_t, gui_ntTracking, gui_bg;
    int gui_iterations = 6;
    int gui_dz = 1;
    int gui_dt = 1;
    Point3D gui_pStackCenter = new Point3D(100,100,10);
    Point3D gui_pStackRadii = new Point3D(100,100,10);
    Point3D gui_pCenterOfMassRadii = new Point3D(100,100,10);
    Point3D gui_pCropRadii = new Point3D(100,100,10);
    Track[] Tracks;
    Roi[] rTrackStarts = new Roi[100];
    int gui_selectedTrack;
    int nTracks = 0;
    int tMinTrack=-1, tMaxTrack=-1;
    private String gui_centeringMethod = "center of mass";


    // todo: deal with the 100 as max number of tracks
    public Registration(ImagePlus imp) {
        this.imp = imp;
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        this.vss = vss;
        this.Tracks = new Track[100];
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
        this.Tracks = new Track[100];
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
        gd.addStringField("Tracking radii xy,z [pix]", "30,5");
        gd.addStringField("Cropping radii xy,z [pix]", "90,10");
        gd.addStringField("Track length [frames]", String.valueOf(imp.getNFrames()));
        gd.addStringField("Image background value", "100");

        //gd.addNumericField("Center computation iterations", 6, 0);
        //gd.addSlider("Track length [frames]", 1, (int) imp.getNFrames(), imp.getNFrames());
        //gd.addSlider("Browse track", 1, (int) imp.getNFrames(), 1);
        //gd.addSlider("Track channel", 1, (int) imp.getNChannels(), 1);
        //String [] centeringMethodChoices = {"centroid","center of mass"};
        //gd.addChoice("Centering method", centeringMethodChoices, "center of mass");

        Button btAddTrackStart = new Button("Add track start");
        btAddTrackStart.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGuiVariables();
                Roi r = imp.getRoi();
                if (!r.getTypeAsString().equals("Point")) {
                    IJ.showMessage("Please use IJ's 'Point selection tool' to mark objects.");
                    return;
                }
                addTrackStart(imp);
            }
        });

        Button btCorrectCurrent = new Button("Locate");
        btCorrectCurrent.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                if (updateGuiVariables()) {

                    // launch tracking
                    ExecutorService es = Executors.newCachedThreadPool();
                    es.execute(new track3D(gui_selectedTrack, 1, gui_dz, gui_pStackRadii, gui_pCenterOfMassRadii, gui_bg, gui_iterations));

                    // wait until finished
                    try {
                        es.shutdown();
                        while(!es.awaitTermination(1, TimeUnit.MINUTES));
                    } catch (InterruptedException ex) {
                        System.err.println("tasks interrupted");
                    }

                    //showTrackOnFrame(imp, pTracked);

                }

            }
        });

        Button btTrack = new Button("Track");
        btTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                updateGuiVariables();

                // launch tracking
                ExecutorService es = Executors.newCachedThreadPool();
                for(int iTrack=0; iTrack<nTracks; iTrack++) {
                    if(!Tracks[iTrack].completed) {
                        es.execute(new track3D(iTrack, 1, gui_dz, gui_pStackRadii, gui_pCenterOfMassRadii, gui_bg, gui_iterations));
                    }
                }

                // wait until finished
                try {
                    es.shutdown();
                    while(!es.awaitTermination(1, TimeUnit.MINUTES));
                } catch (InterruptedException ex) {
                    System.err.println("tasks interrupted");
                }

                // todo: just add them as overlays
                // - square overlays throughout z; cross where z is right
                //
                //showTracksOnFrame(imp, Tracks);
            }
        });


        Button btSaveTrack = new Button("Save coordinates");
        btSaveTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGuiVariables();
                IJ.showMessage("Not yet implemented.\n Please contact tischitischer@gmail.com if you need this feature.");
            }
        });


        Button btReviewTrack = new Button("Review tracks");
        btReviewTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGuiVariables();
            }
        });

        Button btCropTrack = new Button("View cropped tracks");
        btCropTrack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGuiVariables();
                showCroppedTracks();
            }
        });



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

        bgbc.insets = new Insets(0,0,0,5);
        bgbl.setConstraints(btAddTrackStart, bgbc);
        buttons.add(btAddTrackStart);

        bgbc.insets = new Insets(0,0,0,5);
        bgbl.setConstraints(btTrack, bgbc);
        buttons.add(btTrack);

        bgbc.insets = new Insets(0,0,0,0);
        bgbl.setConstraints(btCropTrack, bgbc);
        buttons.add(btCropTrack);

        //bgbc.insets = new Insets(0,0,0,5);
        //bgbl.setConstraints(btReviewTrack, bgbc);
        //buttons.add(btReviewTrack);

        //bgbc.insets = new Insets(0,0,0,5);
        //bgbl.setConstraints(btCropTrack, bgbc);
        //buttons.add(btCropTrack);

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

    public void addTrackStart(ImagePlus imp) {
        int x,y,z,t;
        Roi r = imp.getRoi();

        if(!r.getTypeAsString().equals("Point")) {
            IJ.showMessage("Please use IJ's 'Point selection tool' to mark objects.");
            return;
        }

        x = r.getPolygon().xpoints[0];
        y = r.getPolygon().ypoints[0];

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;
        if(t+gui_ntTracking > imp.getNFrames()) {
            IJ.showMessage("Your Track would be longer than the movie; " +
                    "please reduce 'Track length'.");
            return;
        }

        log("adding new track start, id="+nTracks+"; tStart="+t+"; nt="+ntTracking);
        Tracks[nTracks] = new Track(ntTracking);
        Tracks[nTracks].addLocation(new Point3D(x, y, imp.getZ()-1), t, imp.getC()-1);

        int rx = (int) gui_pCenterOfMassRadii.getX();
        int ry = (int) gui_pCenterOfMassRadii.getY();

        rTrackStarts[nTracks] = new Roi(x-rx,y-ry,2*rx+1,2*ry+1);
        rTrackStarts[nTracks].setPosition(imp.getC(),imp.getZ(),imp.getT());

        Overlay o = imp.getOverlay();
        if(o==null) {
            o = new Overlay();
        } else {
            o.clear();
        }
        for(int i=0; i<=nTracks; i++) {
            o.add(rTrackStarts[i]);
        }
        imp.setOverlay(o);
        nTracks++;


    }

    public void showCroppedTracks() {
        ImagePlus[] impA = new ImagePlus[nTracks];
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileInfoSer[][][] infos = vss.getFileInfosSer();

        for(int i=0; i<nTracks; i++) {
            if (Tracks[i].completed) {
                log("# showCroppedTracks: id=" + i);
                impA[i] = OpenStacksAsVirtualStack.openCroppedCenterRadiusFromInfos(imp, infos, Tracks[i].getPoints3D(), gui_pCropRadii, Tracks[i].getTmin(), Tracks[i].getTmax());
                if (impA[i] == null) {
                    log("..cropping failed.");
                } else {
                    impA[i].setTitle("Track" + i);
                    impA[i].show();
                    impA[i].setPosition(0, (int) (impA[i].getNSlices() / 2 + 0.5), 0);
                    impA[i].resetDisplayRange();
                }
            }
        }
    }

    /*
    public void showTrackReview(ImagePlus imp, Point3D[] pTracked) {
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileInfoSer[][][] infos = vss.getFileInfosSer();
        //Point3D[] pos = new Point3D[tMaxTrack-tMinTrack];
        //System.arraycopy(pTracked, tMinTrack, pos, 0, tMaxTrack-tMinTrack);
        Point3D[] pTrackCenters = new Point3D[imp.getNFrames()];
        Point3D pTrackMin = new Point3D(0,0,0);
        Point3D pTrackMax = new Point3D(99999,99999,99999);
        for (int i = 0; i < pTrackCenters.length; i++) {
            pTrackCenters[i] = pTracked[(int) (tMinTrack + (tMaxTrack - tMinTrack) / 2)];
            // make a Point3D min function
            if(pTracked[i].getX() < pTrackMin.getX()) {
                pTrackMin = new Point3D(pTracked[i].getX(), pTrackMin.getY(), pTrackMin.getZ());
            }
            log("" + pTrackCenters[i].toString());
        }
        Point3D reviewTrackRadii = gui_pCropRadii.multiply(2);
        impTR = OpenStacksAsVirtualStack.openCroppedCenterRadiusFromInfos(imp, infos, pTrackCenters, reviewTrackRadii, tMinTrack, tMaxTrack);
        impTR.show();
        impTR.setPosition(0, (int) (impTR.getNSlices() / 2 + 0.5), 0);
        impTR.resetDisplayRange();
        impTR.setTitle("Review");
        tTR = -1; showTrackOnFrame(impTR, pTracked); // tTR=-1 forces update

    }*/


    public void imageClosed(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageOpened(ImagePlus imp) {
        // currently we are not interested in this event
    }

    public void imageUpdated(ImagePlus imp) {
        // has the slice been changed?
        if(imp == this.impTR) {
            //showTracksOnFrame(impTR, Tracks);
        } else {
            //
        }
    }

    public boolean updateGuiVariables() {
        int x, y, z;
        String[] sA;
        TextField tf;

        int iTxt = 0, iChoice = 0;

        tf = (TextField) gd.getStringFields().get(iTxt++);
        sA = tf.getText().split(",");
        gui_pCenterOfMassRadii = new Point3D(new Integer(sA[0]), new Integer(sA[0]), new Integer(sA[1]));

        tf = (TextField) gd.getStringFields().get(iTxt++);
        sA = tf.getText().split(",");
        gui_pCropRadii = new Point3D(new Integer(sA[0]), new Integer(sA[0]), new Integer(sA[1]));

        tf = (TextField) gd.getStringFields().get(iTxt++);
        gui_ntTracking = new Integer(tf.getText());

        tf = (TextField) gd.getStringFields().get(iTxt++);
        gui_bg = new Integer(tf.getText());


        return true;

    }

    // todo: maybe one could set all tracks as overlays using r.setPosition?!
    // this would avoid the updating

    /*public void showTracksOnFrame(ImagePlus imp, Track[] Tracks) {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) return;
        if((imp.getT()-1) == tTR) return; // no new frame selected
        tTR = imp.getT()-1;

        Overlay o = new Overlay();

        for(int i=0; i<nCompletedTracks; i++) {
            Point3D pCenter = Tracks[i][tTR];
            if (pCenter != null) {
                if (vss.isCropped()) {
                    pCenter = pCenter.subtract(vss.getCropOffset());
                }
                if (Globals.verbose) {
                    log("Registration.showTrackOnFrame pTracked: " + pCenter.toString());
                }
                //log("showTrackOnFrame: pCenter: "+pCenter.toString());
                int rx = (int) gui_pStackRadii.getX();
                int ry = (int) gui_pStackRadii.getY();
                Roi r = new PointRoi(pCenter.getX(), pCenter.getY());
                r.setPosition(gui_c, (int) pCenter.getZ(), tTR);

                Roi cropBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);
                //o.add(cropBounds);
                rx = (int) gui_pCenterOfMassRadii.getX();
                ry = (int) gui_pCenterOfMassRadii.getY();
                Roi comBounds = new Roi(pCenter.getX() - rx, pCenter.getY() - ry, 2 * rx + 1, 2 * ry + 1);

                o.add(comBounds);
                o.add(r);
                //imp.setPosition(imp.getC(), ((int) pCenter.getZ() + 1), imp.getT());
                //imp.deleteRoi();
                //imp.setRoi(r);
                o.setLabelColor(Color.blue);
            } else {
                if (Globals.verbose) {
                    log("Registration.showTrackOnFrame: No track available for this time point");
                }
                //Overlay o = new Overlay();
                //imp.setOverlay(o);
            }
        } // iTrack

    }*/


    class track3D implements Runnable {
        int iTrack, c, nt, dt, dz, bg, iterations;
        Point3D pStackRadii, pCenterOfMassRadii;

        ImageStack stack;
        Point3D pOffset, pLocalCenter;
        long startTime, stopTime, elapsedTime;

        track3D(int iTrack, int dt, int dz, Point3D pStackRadii, Point3D pCenterOfMassRadii, int bg, int iterations) {
            this.iTrack = iTrack;
            this.dt = dt;
            this.dz = dz;
            this.iterations = iterations;
            this.pStackRadii = pStackRadii;
            this.pCenterOfMassRadii = pCenterOfMassRadii;
            this.bg = bg;
        }

        public void run() {

            // todo: is there some nicer way than the Polygons?

            int tStart = Tracks[iTrack].getTmin();
            int channel = Tracks[iTrack].getChannelbyIndex(0);
            int nt = Tracks[iTrack].getLength();
            Point3D pStackCenter = Tracks[iTrack].getXYZbyIndex(0);
            Tracks[iTrack].reset();


            if (Globals.verbose) {
                log("# Registration.track3D:");
                log("iTrack: "+iTrack);
                log("tStart, tMax, dt, dz " + tStart + "," + (tStart+nt-1) + "," + dt + "," + dz);
                log("pTrackStart "+pStackCenter.toString());
            }

            for (int it = tStart; it < tStart+nt; it = it + dt) {

                // get stack, ensuring that extracted stack is still within bounds
                pStackCenter = OpenStacksAsVirtualStack.curatePosition(imp, pStackCenter, pStackRadii);
                startTime = System.currentTimeMillis();
                stack = getImageStack(it, channel, dz, pStackCenter, pStackRadii);
                stopTime = System.currentTimeMillis();
                elapsedTime = stopTime - startTime;

                // compute center of mass (in zero-based local stack coordinates)
                startTime = System.currentTimeMillis();
                pLocalCenter = iterativeCenterOfMass16bit(stack, bg, pCenterOfMassRadii, iterations);
                // correct for the sub-sampling in z
                pLocalCenter = new Point3D(pLocalCenter.getX(), pLocalCenter.getY(), dz * pLocalCenter.getZ());
                stopTime = System.currentTimeMillis();
                elapsedTime = stopTime - startTime;
                //log("Computed center of mass [ms]: " + elapsedTime);

                // compute offset to zero-based center of stack
                pOffset = pLocalCenter.subtract(pStackRadii);
                //pOffset = pOffset.subtract(pOnes);

                // update time-points, using linear interpolation
                for (int j = 0; j < dt; j++) {
                    if ((it + j) < imp.getNFrames()) {
                        Point3D p = pStackCenter.add(pOffset.multiply((j + 1.0) / dt));
                        Tracks[iTrack].addLocation(p, it + j, channel);
                    }
                }

                // update next center using Brownian motion model (position of next is same as this)
                // todo:
                // - also have a linear motion model for the update, i.e. add pOffset*2
                pStackCenter = pStackCenter.add(pOffset);

                log("track id=" + iTrack + "; tracking t=" + it); // + "," + pStackCenter.toString());
                if(Globals.verbose) {
                    log("Read data [ms]: " + elapsedTime);
                    log("Reading speed [MB/s]: " + stack.getHeight() * stack.getWidth() * stack.getSize() * 2 / ((elapsedTime + 0.001) * 1000));
                    log("Detected shift [pixel]:" +
                            " x:" + String.format("%.2g", pOffset.getX()) +
                            " y:" + String.format("%.2g", pOffset.getY()) +
                            " z:" + String.format("%.2g", pOffset.getZ()));
                }

                //IJ.showStatus("" + it + "/" + tMax);
                //IJ.showProgress((double) (it - t) / (tMax - t));

            }
            //IJ.showStatus("" + tMax + "/" + tMax);
            //IJ.showProgress(1.0);

            log("track id="+iTrack+"; tracking of "+nt+" frames completed!");
            Tracks[iTrack].completed = true;
            return;
        }
    }

    private ImageStack getImageStack(int t, int c, int dz, Point3D p, Point3D pr) {
        //log("Registration.getImageStack p[0]: "+p[0]+" z:"+p[3]);
        if(Globals.verbose) {
            log("# Registration.getImageStack");
            log("c: "+c);
            log("t: "+t);
            log(""+imp.getStackIndex(c+1,1,t+1));
        }

        long startTime = System.currentTimeMillis();
        ImageStack stack = vss.getCroppedFrameCenterRadii(t, c, dz, p, pr).getStack();
        long stopTime = System.currentTimeMillis(); long elapsedTime = stopTime - startTime;
        //log("loaded stack in [ms]: " + elapsedTime);
        //imp.show();
        return(stack);
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