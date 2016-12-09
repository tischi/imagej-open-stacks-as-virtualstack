package ct.vss;

import ij.*;
import ij.gui.*;
import ij.io.Opener;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Point3D;
import oracle.jrockit.jfr.JFR;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.lang.String;
import java.util.concurrent.atomic.AtomicInteger;

import static ij.IJ.log;


/**
 * Created by tischi on 31/10/16.
 */

// todo: how to behave when the track is leaving the image bounds?

public class Registration implements PlugIn, ImageListener {

    VirtualStackOfStacks vss;
    ImagePlus imp;
    ImagePlus impTR; // track review
    private static NonBlockingGenericDialog gd;
    private final static Point3D pOnes = new Point3D(1,1,1);
    // gui variables
    int gui_ntTracking, gui_bg;
    int gui_iterations = 6;
    int gui_dz = 1;
    int gui_dt = 1;
    Point3D gui_pCenterOfMassRadii;
    Point3D gui_pCropRadii;
    ArrayList<Track> Tracks = new ArrayList<Track>();
    ArrayList<Roi> rTrackStarts = new ArrayList<Roi>();
    private String gui_centeringMethod = "center of mass";
    TrackTable trackTable;
    long trackStatsStartTime;
    long trackStatsReportDelay = 200;
    long trackStatsLastReport = System.currentTimeMillis();
    int totalTimePointsToBeTracked = 0;
    AtomicInteger totalTimePointsTracked = new AtomicInteger(0);
    AtomicInteger totalTimeSpentTracking = new AtomicInteger(0);
    long trackStatsLastTrackStarted;
    int trackStatsTotalPointsTrackedAtLastStart;
    TrackingGUI trackingGUI;

    private JFileChooser myJFileChooser = new JFileChooser(new File("."));

    // todo: deal with the 100 as max number of tracks
    public Registration(ImagePlus imp) {
        this.imp = imp;
        initialize();
    }

    public void run(String arg) {
        this.imp = IJ.getImage();
        initialize();
        showDialog();
    }

    public void showDialog(){

        SwingUtilities.invokeLater(new Runnable() {
                                       public void run() {
                                           trackingGUI = new TrackingGUI();
                                           trackingGUI.showDialog();
                                       }
                                   });
    }

    private void initialize() {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        this.vss = vss;
        gui_pCenterOfMassRadii = new Point3D(30,30,5);
        gui_pCropRadii = new Point3D(60,60,10);
        gui_ntTracking = imp.getNFrames();
        gui_bg = (int) imp.getProcessor().getMin();
        trackTable = new TrackTable();
        ImagePlus.addImageListener(this);
    }

    public void logStatus() {
        log("# Current track status:");
        if(Tracks.size()==0) {
            log("no tracks yet");
            return;
        }
        for(int iTrack=0; iTrack<Tracks.size(); iTrack++) {
            if(!Tracks.get(iTrack).completed) {
                log("track "+iTrack+": not completed");
            } else {
                log("track "+iTrack+": completed; length="+Tracks.get(iTrack).getLength());
            }
        }
    }

    class TrackTable  {
        JTable table;

        public TrackTable() {
            String[] columnNames = {"ID_T",
                    "X",
                    "Y",
                    "Z",
                    "T",
                    "ID"
                    //,
                    //"t_TotalSum",
                    //"t_ReadThis",
                    //"t_ProcessThis"
            };

            DefaultTableModel model = new DefaultTableModel(columnNames,0);
            table = new JTable(model);
            table.setPreferredScrollableViewportSize(new Dimension(500, 200));
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
        }

        public void addRow(final Object[] row) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.addRow(row);
                }
            });
        }

        public JTable getTable() {
            return table;
        }

    }

    class TrackTablePanel extends JPanel implements MouseListener, KeyListener {
        private boolean DEBUG = false;
        JTable table;
        JFrame frame;
        JScrollPane scrollPane;

        public TrackTablePanel() {
            super(new GridLayout(1, 0));

            //DefaultTableModel model = new DefaultTableModel(columnNames,);
            table = trackTable.getTable();
            table.setPreferredScrollableViewportSize(new Dimension(500, 200));
            table.setFillsViewportHeight(true);
            table.setAutoCreateRowSorter(true);
            table.setRowSelectionAllowed(true);
            table.addMouseListener(this);
            table.addKeyListener(this);

            //Create the scroll pane and add the table to it.
            scrollPane = new JScrollPane(table);

            //Add the scroll pane to this panel.
            add(scrollPane);
        }

        public void showTable() {
            //Create and set up the window.
            frame = new JFrame("Tracks");
            //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            //Create and set up the content pane.
            this.setOpaque(true); //content panes must be opaque
            frame.setContentPane(this);

            //Display the window.
            frame.pack();
            frame.setLocation(trackingGUI.getFrame().getX() + trackingGUI.getFrame().getWidth(), trackingGUI.getFrame().getY());
            frame.setVisible(true);
        }

        public void setImpPosition() {
            int r = table.getSelectedRow();
            float x = new Float(table.getModel().getValueAt(r, 1).toString());
            float y = new Float(table.getModel().getValueAt(r, 2).toString());
            float z = new Float(table.getModel().getValueAt(r, 3).toString());
            int t = new Integer(table.getModel().getValueAt(r, 4).toString());
            imp.setPosition(0,(int)z+1,t+1);
            Roi pr = new PointRoi(x,y);
            pr.setPosition(0,(int)z+1,t+1);
            imp.setRoi(pr);
            //log("t="+table.getModel().getValueAt(r, 5));
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            setImpPosition();
        }

        @Override
        public void mousePressed(MouseEvent e) {

        }

        @Override
        public void mouseReleased(MouseEvent e) {

        }

        @Override
        public void mouseEntered(MouseEvent e) {

        }

        @Override
        public void mouseExited(MouseEvent e) {

        }

        @Override
        public void keyTyped(KeyEvent e) {

        }

        @Override
        public void keyPressed(KeyEvent e) {

        }

        @Override
        public void keyReleased(KeyEvent e) {
            setImpPosition();
        }
    }

    class TrackingGUI implements ActionListener, FocusListener {

        JFrame frame;

        String[] actions = {
                //"Add Track Start",
                "Track selected Object",
                "Crop Image along Tracks",
                "Show Track Table", "Save Track Table"
        };

        String[] texts = {
                "Tracking center of mass radii; xy,z [pix]",
                "Cropping radii; xy,z [pix]",
                "Track length [frames]",
                "Image background"
        };

        String[] defaults = {
                String.valueOf((int) gui_pCenterOfMassRadii.getX()) + "," + String.valueOf((int) gui_pCenterOfMassRadii.getZ()),
                String.valueOf((int) gui_pCropRadii.getX()) + "," + String.valueOf((int) gui_pCropRadii.getZ()),
                String.valueOf(imp.getNFrames()),
                String.valueOf(gui_bg)
        };

        ExecutorService es = Executors.newCachedThreadPool();

        public void TrackingGUI() {
        }

        public void showDialog() {

            frame = new JFrame("Big Data Tracker");
            //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            Container c = frame.getContentPane();
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));

            JButton[] buttons = new JButton[actions.length];

            for (int i = 0; i < buttons.length; i++) {
                buttons[i] = new JButton(actions[i]);
                buttons[i].setActionCommand(actions[i]);
                buttons[i].addActionListener(this);
            }

            JTextField[] textFields = new JTextField[texts.length];
            JLabel[] labels = new JLabel[texts.length];

            for (int i = 0; i < textFields.length; i++) {
                textFields[i] = new JTextField(6);
                textFields[i].setActionCommand(texts[i]);
                textFields[i].addActionListener(this);
                textFields[i].addFocusListener(this);
                textFields[i].setText(defaults[i]);
                labels[i] = new JLabel(texts[i] + ": ");
                labels[i].setLabelFor(textFields[i]);
            }

            int i = 0, j = 0;

            // todo; replace by arraylist
            ArrayList<JPanel> panels = new ArrayList<JPanel>();
            int iPanel = 0;

            for (int k = 0; k < textFields.length; k++) {
                panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
                panels.get(iPanel).add(labels[k]);
                panels.get(iPanel).add(textFields[k]);
                c.add(panels.get(iPanel++));
            }

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            frame.pack();
            frame.setLocation(imp.getWindow().getX() + imp.getWindow().getWidth(), imp.getWindow().getY());
            frame.setVisible(true);

        }

        public void focusGained(FocusEvent e) {
            //
        }

        public void focusLost(FocusEvent e) {
            JTextField tf = (JTextField) e.getSource();
            if (!(tf == null)) {
                tf.postActionEvent();
            }
        }

        public void actionPerformed(ActionEvent e) {
            int i = 0;
            int k = 0;
            String[] sA;
            JFileChooser fc;
            final OpenStacksAsVirtualStack osv = new OpenStacksAsVirtualStack();

            if (e.getActionCommand().equals(actions[i++])) {
                //
                // Add track
                //
                Roi r = imp.getRoi();
                if (r == null || !r.getTypeAsString().equals("Point")) {
                    IJ.showMessage("Please use IJ's 'Point selection tool' on image: '" + imp.getTitle()+"'");
                    return;
                }
                // add track start ...
                int iNewTrack = addTrackStart(imp);

                // ... and start tracking immediately
                if( iNewTrack >= 0 ) {
                    trackStatsLastTrackStarted = System.currentTimeMillis();
                    trackStatsTotalPointsTrackedAtLastStart = totalTimePointsTracked.get();
                    es.execute(new Registration.track3D(iNewTrack, 1, gui_dz, gui_pCenterOfMassRadii, gui_bg, gui_iterations));
                }

            } else if (e.getActionCommand().equals(actions[i++])) {
                //
                // View Tracks
                //
                showCroppedTracks();
            } else if (e.getActionCommand().equals(actions[i++])) {
                //
                // Show Table
                //
                showTrackTable();
            } else if (e.getActionCommand().equals(actions[i++])) {
                //
                // Save Table
                //
                TableModel model = trackTable.getTable().getModel();
                if(model == null) {
                    IJ.showMessage("There are no tracks yet.");
                    return;
                }
                fc = new JFileChooser(vss.getDirectory());
                if (fc.showSaveDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    saveTrackTable(file);
                }
            } else if (e.getActionCommand().equals(texts[k++])) {
                //
                // Tracking radii
                //
                JTextField source = (JTextField) e.getSource();
                sA = source.getText().split(",");
                log(texts[k-1] + ": " + source.getText());
                gui_pCenterOfMassRadii = new Point3D(new Integer(sA[0]), new Integer(sA[0]), new Integer(sA[1]));
            } else if (e.getActionCommand().equals(texts[k++])) {
                //
                // Cropping radii
                //
                JTextField source = (JTextField) e.getSource();
                sA = source.getText().split(",");
                log(texts[k-1] + ": " + source.getText());
                gui_pCropRadii = new Point3D(new Integer(sA[0]), new Integer(sA[0]), new Integer(sA[1]));
            } else if (e.getActionCommand().equals(texts[k++])) {
                //
                // nt
                //
                JTextField source = (JTextField) e.getSource();
                log(texts[k-1] + ": " + source.getText());
                gui_ntTracking = new Integer(source.getText());
            } else if (e.getActionCommand().equals(texts[k++])) {
                //
                // bg
                //
                JTextField source = (JTextField) e.getSource();
                log(texts[k-1] + ": " + source.getText());
                gui_bg = new Integer(source.getText());
            }
        }

        public JFrame getFrame() {
            return frame;
        }
    }

    public void showTrackTable(){
        if(trackTable==null) {
            IJ.showMessage("There are no tracks to show yet.");
            return;
        }
        TrackTablePanel ttp = new TrackTablePanel();
        ttp.showTable();
    }

    public void saveTrackTable(File file) {
        try{
            TableModel model = trackTable.getTable().getModel();
            FileWriter excel = new FileWriter(file);

            for(int i = 0; i < model.getColumnCount(); i++){
                excel.write(model.getColumnName(i) + "\t");
            }
            excel.write("\n");

            for(int i=0; i< model.getRowCount(); i++) {
                for(int j=0; j < model.getColumnCount(); j++) {
                    excel.write(model.getValueAt(i,j).toString()+"\t");
                }
                excel.write("\n");
            }
            excel.close();

        } catch(IOException e) { IJ.showMessage(e.toString()); }
    }


    public int addTrackStart(ImagePlus imp) {
        int x,y,t;
        Roi r = imp.getRoi();

        if(!r.getTypeAsString().equals("Point")) {
            IJ.showMessage("Please use IJ's 'Point selection tool' to mark objects.");
            return(-1);
        }

        x = r.getPolygon().xpoints[0];
        y = r.getPolygon().ypoints[0];

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;
        if(t+gui_ntTracking > imp.getNFrames()) {
            IJ.showMessage("Your Track would be longer than the movie; " +
                    "please reduce 'Track length' or start tracking from an earlier time point.");
            return(-1);
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = Tracks.size();
        //log("added new track start; id = "+newTrackID+"; starting [frame] = "+t+"; length [frames] = "+ntTracking);
        Tracks.add(new Track(ntTracking));
        Tracks.get(newTrackID).addLocation(new Point3D(x, y, imp.getZ()-1), t, imp.getC()-1);

        return(newTrackID);

    }

    public void showCroppedTracks() {
        ImagePlus[] impA = new ImagePlus[Tracks.size()];
        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        FileInfoSer[][][] infos = vss.getFileInfosSer();

        for(int i=0; i<Tracks.size(); i++) {
            Track track = Tracks.get(i);
            if (track.completed) {
                log("# showCroppedTracks: id=" + i);
                impA[i] = OpenStacksAsVirtualStack.openCroppedCenterRadiusFromInfos(imp, infos, track.getPoints3D(), gui_pCropRadii, track.getTmin(), track.getTmax());
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

    public int getNumberOfUncompletedTracks() {
        int uncomplete = 0;
        for(Track t:Tracks) {
            if(!t.completed)
                uncomplete++;
        }
        return uncomplete;
    }


    public void addTrackToOverlay(Track track, int i) {

        int rx = (int) gui_pCenterOfMassRadii.getX();
        int ry = (int) gui_pCenterOfMassRadii.getY();
        int rz = (int) gui_pCenterOfMassRadii.getZ();

        Roi roi;
        Overlay o = imp.getOverlay();
        if(o==null) {
            o = new Overlay();
            imp.setOverlay(o);
        }

        int x = (int) track.getX(i);
        int y = (int) track.getY(i);
        int z = (int) track.getZ(i);
        int c = (int) track.getC(i);
        int t = (int) track.getT(i);

        int rrx, rry;
        for(int iz=0; iz<imp.getNSlices(); iz++) {
            rrx = Math.max(rx/(Math.abs(iz-z)+1),1);
            rry = Math.max(ry/(Math.abs(iz-z)+1),1);
            roi = new Roi(x - rrx, y - rry, 2 * rrx + 1, 2 * rry + 1);
            roi.setPosition(c+1, iz+1, t+1);
            o.add(roi);
        }

    }


    class track3D implements Runnable {
        int iTrack, c, nt, dt, dz, bg, iterations;
        Point3D pStackRadii, pCenterOfMassRadii;
        String log;

        ImageStack stack;
        Point3D pOffset, pLocalCenter;

        track3D(int iTrack, int dt, int dz, Point3D pCenterOfMassRadii, int bg, int iterations) {
            this.iTrack = iTrack;
            this.dt = dt;
            this.dz = dz;
            this.iterations = iterations;
            this.pCenterOfMassRadii = pCenterOfMassRadii;
            this.bg = bg;
        }

        public void run() {
            long startTime = 0, stopTime, elapsedReadingTime, elapsedProcessingTime;
            long startTimePerTimePoint, totalElapsedTime = 0;

            Track track = Tracks.get(iTrack);

            int tStart = track.getTmin();
            int channel = track.getC(0);
            int nt = track.getLength();
            Point3D pStackCenter = track.getXYZ(0);
            track.reset();
            Point3D pStackRadii = pCenterOfMassRadii.multiply(2.0);

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
                elapsedReadingTime = stopTime - startTime;

                // compute center of mass (in zero-based local stack coordinates)
                startTime = System.currentTimeMillis();
                pLocalCenter = iterativeCenterOfMass16bit(stack, bg, pCenterOfMassRadii, iterations);
                // correct for the sub-sampling in z
                pLocalCenter = new Point3D(pLocalCenter.getX(), pLocalCenter.getY(), dz * pLocalCenter.getZ());
                stopTime = System.currentTimeMillis();
                elapsedProcessingTime = stopTime - startTime;
                //log("Computed center of mass [ms]: " + elapsedTime);

                // compute offset to zero-based center of stack
                pOffset = pLocalCenter.subtract(pStackRadii);
                //pOffset = pOffset.subtract(pOnes);

                // update time-points, using linear interpolation
                for (int j = 0; j < dt; j++) {
                    if ((it + j) < imp.getNFrames()) {
                        Point3D p = pStackCenter.add(pOffset.multiply((j + 1.0) / dt));
                        // store as track
                        track.addLocation(p, it + j, channel);
                        // update also in table
                        trackTable.addRow(new Object[]{
                                String.format("%1$04d",iTrack)+"_"+String.format("%1$05d",it),
                                (float)p.getX(), (float)p.getY(), (float)p.getZ(), it, iTrack
                                //totalElapsedTime, elapsedReadingTime, elapsedProcessingTime
                        });
                        // add as overlay on the image
                        addTrackToOverlay(track, it + j - tStart);
                    }
                }

                // update next center using Brownian motion model (position of next is same as this)
                // todo:
                // - also have a linear motion model for the update, i.e. add pOffset*2
                pStackCenter = pStackCenter.add(pOffset);


                // show progress
                int n = totalTimePointsTracked.addAndGet(1);
                if( (System.currentTimeMillis() > trackStatsReportDelay+trackStatsLastReport)
                        || (n==totalTimePointsToBeTracked))
                    {
                    trackStatsLastReport = System.currentTimeMillis();

                    long dt = System.currentTimeMillis()-trackStatsLastTrackStarted;
                    int dn = n - trackStatsTotalPointsTrackedAtLastStart;
                    int nToGo = totalTimePointsToBeTracked - n;
                    float speed = (float)1.0*dn/dt*1000;
                    float remainingTime = (float)1.0*nToGo/speed;

                    Globals.threadlog(
                            "progress = "+n+"/"+totalTimePointsToBeTracked+
                            "; speed [n/s] = "+String.format("%.2g",speed)+
                            "; remaining [s] = "+String.format("%.2g",remainingTime)+
                            "; reading [ms] = "+elapsedReadingTime+
                            "; processing [ms] = "+elapsedProcessingTime
                    );
                }

                if(Globals.verbose) {
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Read data [ms]: " + elapsedReadingTime);
                    log("Reading speed [MB/s]: " + stack.getHeight() * stack.getWidth() * stack.getSize() * 2 / ((elapsedReadingTime + 0.001) * 1000));
                    log("Detected shift [pixel]:" +
                            " x:" + String.format("%.2g", pOffset.getX()) +
                            " y:" + String.format("%.2g", pOffset.getY()) +
                            " z:" + String.format("%.2g", pOffset.getZ()));
                }

            }

            //Globals.threadlog("track id = "+iTrack+"; nt [frames] = "+nt+"; completed.");
            track.completed = true;
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



