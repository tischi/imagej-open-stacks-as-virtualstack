package ct.vss;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import javafx.geometry.Point3D;
import mpicbg.imglib.algorithm.fft.PhaseCorrelation;
import mpicbg.imglib.image.ImagePlusAdapter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static ij.IJ.log;


/**
 * Created by tischi on 31/10/16.
 */

// todo: how to behave when the track is leaving the image bounds?
    // - show only the minimal region that stays within the image bounds
// todo: add unique track id to tracktable, track, and imageName of cropped track

public class Registration implements PlugIn {

    VirtualStackOfStacks vss;
    ImagePlus imp;
    private static NonBlockingGenericDialog gd;
    private final static Point3D pOnes = new Point3D(1,1,1);
    // gui variables
    int gui_ntTracking, gui_bg;
    int gui_iterations = 6;
    Point3D gui_pTrackingSize;
    Point3D gui_pSubSample = new Point3D(1,1,1);
    int gui_tSubSample = 1;
    ArrayList<Track> Tracks = new ArrayList<Track>();
    ArrayList<Roi> rTrackStarts = new ArrayList<Roi>();
    String gui_trackingMethod = "center of mass";
    String gui_centeringMethod = "center of mass";
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

    // todo: put actual tracking into different class

    public Registration() {
    }

    public Registration(ImagePlus imp) {
        this.imp = imp;
        initialize();
    }

    class TrackingGUI implements ActionListener, FocusListener {

        JFrame frame;

        String[] texts = {
                "Tracking window: nx, ny, nz [pixels]",
                "Sub-sampling: dx, dy, dz, dt [pixels, frames]",
                "Track length [frames]"
        };

        String[] actions = {
                "Set nx,ny",
                "Set nz",
                "Track selected object",
                "Show tracked objects",
                "Show",
                "Save",
                "Clear",
                "Report issue"
        };


        String[] defaults = {
                String.valueOf((int) gui_pTrackingSize.getX()) + "," + (int) gui_pTrackingSize.getY() + "," +String.valueOf((int) gui_pTrackingSize.getZ()),
                String.valueOf((int) gui_pSubSample.getX() + "," + (int) gui_pSubSample.getY() + "," + (int) gui_pSubSample.getZ() + "," + gui_tSubSample),
                String.valueOf(imp.getNFrames())
        };

        String[] comboNames = {
                "Tracking method"
        };

        String[][] combos = {
                {"center of mass","correlation"}
        };

        JTextField[] textFields = new JTextField[texts.length];
        JLabel[] labels = new JLabel[texts.length];

        int previouslySelectedZ = -1;

        ExecutorService es = Executors.newCachedThreadPool();

        public void TrackingGUI() {
        }

        public void changeTextField(int i, String text) {
            textFields[i].setText(text);
        }

        public void showDialog() {

            frame = new JFrame("Big Data Tracker");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            Container c = frame.getContentPane();
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
            ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

            String[] toolTipTexts = getToolTipFile("TrackAndCropHelp.html");

            //
            // TextFields
            //

            int iToolTipText = 0;

            for (int i = 0; i < textFields.length; i++, iToolTipText++) {
                textFields[i] = new JTextField(12);
                textFields[i].setActionCommand(texts[i]);
                textFields[i].addActionListener(this);
                textFields[i].addFocusListener(this);
                textFields[i].setText(defaults[i]);
                textFields[i].setToolTipText(toolTipTexts[iToolTipText]);
                labels[i] = new JLabel(texts[i] + ": ");
                labels[i].setLabelFor(textFields[i]);
            }

            //
            // Buttons
            //
            JButton[] buttons = new JButton[actions.length];

            for (int i = 0; i < buttons.length; i++, iToolTipText++) {
                buttons[i] = new JButton(actions[i]);
                buttons[i].setActionCommand(actions[i]);
                buttons[i].addActionListener(this);
                buttons[i].setToolTipText(toolTipTexts[iToolTipText]);
            }

            //
            // ComboBoxes
            //
            JComboBox[] comboBoxes = new JComboBox[comboNames.length];
            JLabel[] comboLabels = new JLabel[comboNames.length];

            for (int i = 0; i < combos.length; i++, iToolTipText++) {
                comboBoxes[i] = new JComboBox(combos[i]);
                comboBoxes[i].setActionCommand(comboNames[i]);
                comboBoxes[i].addActionListener(this);
                comboBoxes[i].setToolTipText(toolTipTexts[iToolTipText]);
                comboLabels[i] = new JLabel(comboNames[i] + ": ");
                comboLabels[i].setLabelFor(comboBoxes[i]);
            }

            //
            // Panels
            //

            int i = 0;
            ArrayList<JPanel> panels = new ArrayList<JPanel>();
            int iPanel = 0;

            int k = 0;
            panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
            panels.get(iPanel).add(labels[k]);
            panels.get(iPanel).add(textFields[k]);
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            for (k = 1; k < textFields.length; k++) {
                panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
                panels.get(iPanel).add(labels[k]);
                panels.get(iPanel).add(textFields[k]);
                c.add(panels.get(iPanel++));
            }

            for (k = 0; k < comboNames.length; k++) {
                panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
                panels.get(iPanel).add(comboLabels[k]);
                panels.get(iPanel).add(comboBoxes[k]);
                c.add(panels.get(iPanel++));
            }

            panels.add(new JPanel());
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            panels.add(new JPanel());
            JLabel labelTrackTable = new JLabel("  Track table: ");
            labelTrackTable.setLabelFor(buttons[i]);
            panels.get(iPanel).add(labelTrackTable);
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            panels.get(iPanel).add(buttons[i++]);
            c.add(panels.get(iPanel++));

            panels.add(new JPanel());
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

            int i = 0, j = 0, k = 0;
            JFileChooser fc;

            // update current imp object
            imp = IJ.getImage();

            if (e.getActionCommand().equals(actions[i++])) {

                //
                // Set nx, ny
                //

                Roi r = imp.getRoi();

                if(r==null || !r.getTypeAsString().equals("Rectangle")) {
                    IJ.showMessage("Please put a rectangular selection on the image");
                    return;
                }

                gui_pTrackingSize = new Point3D((int)r.getFloatWidth(), (int)r.getFloatHeight(), gui_pTrackingSize.getZ() );
                trackingGUI.changeTextField(0, "" + (int) gui_pTrackingSize.getX() + "," + (int) gui_pTrackingSize.getY() + "," + (int) gui_pTrackingSize.getZ());


            } else if (e.getActionCommand().equals(actions[i++])) {
                //
                //  Set nz
                //

                int z = imp.getZ()-1;
                if (previouslySelectedZ==-1) {
                    // first time do nothing
                } else {
                    int nz = Math.abs(z - previouslySelectedZ);
                    gui_pTrackingSize = new Point3D(gui_pTrackingSize.getX(), gui_pTrackingSize.getY(), nz);
                    trackingGUI.changeTextField(0, "" + (int) gui_pTrackingSize.getX() + "," + (int) gui_pTrackingSize.getY() + "," + (int) gui_pTrackingSize.getZ());
                }
                previouslySelectedZ = z;

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Track selected object
                //

                Roi r = imp.getRoi();
                if (r == null || ! (r.getTypeAsString().equals("Point") || r.getTypeAsString().equals("Rectangle")) ) {
                    IJ.showMessage("Please use ImageJ's 'Point' or 'Rectangular' selection tool on image: '" + imp.getTitle()+"'");
                    return;
                }

                // Add track start ...
                int iNewTrack = addTrackStart(imp);

                // ... and start tracking immediately
                if( iNewTrack >= 0 ) {
                    trackStatsLastTrackStarted = System.currentTimeMillis();
                    trackStatsTotalPointsTrackedAtLastStart = totalTimePointsTracked.get();
                    es.execute(new Registration.Tracking(iNewTrack, gui_pSubSample, gui_tSubSample, gui_bg, gui_iterations));
                }

                Thread t = new Thread(new Runnable() {
                    public void run() {
                        logIfTrackingFinished();
                    }
                });
                t.start();


            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // View Object Tracks
                //

                showCroppedTracks();

            }  else if (e.getActionCommand().equals(actions[i++])) {

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

            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Clear Table and all tracks
                //

                trackTable.clear();
                Tracks = new ArrayList<Track>();
                rTrackStarts = new ArrayList<Roi>();

                // remove overlay
                imp.setOverlay(new Overlay());

                totalTimePointsToBeTracked = 0;


            } else if (e.getActionCommand().equals(actions[i++])) {

                //
                // Report issue
                //

                if (Desktop.isDesktopSupported()) {
                    try {
                        final URI uri = new URI("https://github.com/tischi/imagej-open-stacks-as-virtualstack/issues");
                        Desktop.getDesktop().browse(uri);
                    } catch (URISyntaxException uriEx) {
                        IJ.showMessage(uriEx.toString());
                    } catch (IOException ioEx) {
                        IJ.showMessage(ioEx.toString());
                    }
                } else { /* TODO: error handling */ }

            }  else if (e.getActionCommand().equals(texts[k++])) {

                //
                // Tracking object size
                //

                JTextField source = (JTextField) e.getSource();
                String[] sA = source.getText().split(",");
                gui_pTrackingSize = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));

            } else if (e.getActionCommand().equals(texts[k++])) {

                //
                // Tracking sub-sampling
                //

                JTextField source = (JTextField) e.getSource();
                String[] sA = source.getText().split(",");
                gui_pSubSample = new Point3D(new Integer(sA[0]), new Integer(sA[1]), new Integer(sA[2]));
                gui_tSubSample = new Integer(sA[3]);

            } else if (e.getActionCommand().equals(texts[k++])) {

                //
                // Track length
                //

                JTextField source = (JTextField) e.getSource();
                gui_ntTracking = new Integer(source.getText());

            } else if (e.getActionCommand().equals(comboNames[j++])) {

                //
                // Tracking method
                //

                JComboBox cb = (JComboBox)e.getSource();
                gui_trackingMethod = (String)cb.getSelectedItem();

            }
        }

        public JFrame getFrame() {
            return frame;
        }

        private String[] getToolTipFile(String fileName) {
            ArrayList<String> toolTipTexts = new ArrayList<String>();

            //Get file from resources folder
            InputStream in = getClass().getResourceAsStream("/"+fileName);
            BufferedReader input = new BufferedReader(new InputStreamReader(in));
            Scanner scanner = new Scanner(input);
            StringBuilder sb = new StringBuilder("");

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if(line.equals("###")) {
                    toolTipTexts.add(sb.toString());
                    sb = new StringBuilder("");
                } else {
                    sb.append(line);
                }

            }

            scanner.close();


            return(toolTipTexts.toArray(new String[0]));
        }

    }

    public boolean logIfTrackingFinished() {

        while(totalTimePointsTracked.get()<totalTimePointsToBeTracked) {
            IJ.wait(500);
            //log(""+totalTimePointsTracked.get()+" "+totalTimePointsToBeTracked);
        }
        Globals.threadlog(
                "Tracking completed!"
        );
        return true;
    }

    public void run(String arg) {
        this.imp = IJ.getImage();
        initialize();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                showDialog();
            }
        });
    }

    public void showDialog(){
       trackingGUI = new TrackingGUI();
       trackingGUI.showDialog();
    }

    private void initialize() {

        VirtualStackOfStacks vss = (VirtualStackOfStacks) imp.getStack();
        if(vss==null) {
            throw new IllegalArgumentException("Registration only works with VirtualStackOfStacks");
        }
        FileInfoSer[][][] infos = vss.getFileInfosSer();
        if(infos[0][0][0].compression==6) {
            IJ.showMessage(
                    "This is a ZIP compressed data set." +
                            "This will probably NOT WORK PROPERLY with this plugin!"
            );
        }
        this.vss = vss;
        gui_pTrackingSize = new Point3D(20,20,30);
        gui_ntTracking = imp.getNFrames();
        gui_bg = (int) imp.getProcessor().getMin();
        trackTable = new TrackTable();
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


        public void clear() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0);
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

        public void highlightSelectedTrack() {
            int rs = table.getSelectedRow();
            int r = table.convertRowIndexToModel( rs );
            float x = new Float(table.getModel().getValueAt(r, 1).toString());
            float y = new Float(table.getModel().getValueAt(r, 2).toString());
            float z = new Float(table.getModel().getValueAt(r, 3).toString());
            int t = new Integer(table.getModel().getValueAt(r, 4).toString());
            imp.setPosition(0,(int)z+1,t+1);
            Roi pr = new PointRoi(x,y);
            pr.setPosition(0,(int)z+1,t+1);
            imp.setRoi(pr);
            //log(" rs="+rs+" r ="+r+" x="+x+" y="+y+" z="+z+" t="+t);
            //log("t="+table.getModel().getValueAt(r, 5));
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            highlightSelectedTrack();
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
            highlightSelectedTrack();
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
        Point3D pTrackCenter = null;

        int t;
        Roi r = imp.getRoi();

        if(r.getTypeAsString().equals("Point")) {
            pTrackCenter = new Point3D(r.getPolygon().xpoints[0],
                             r.getPolygon().ypoints[0],
                             imp.getZ()-1);
        } else {
            IJ.showMessage("Please use the point selection tool to mark an object.");
            return(-1);
        }

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;
        if(t+gui_ntTracking > imp.getNFrames()) {
            IJ.showMessage("Your Track would be longer than the movie; " +
                    "please\n - reduce 'Track length', or\n- move the time slide to an earlier time point.");
            return(-1);
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = Tracks.size();

        Tracks.add(new Track(ntTracking));
        Track track = Tracks.get(newTrackID);
        track.setID(newTrackID);
        track.addLocation(pTrackCenter, t, imp.getC() - 1);
        track.setObjectSize(gui_pTrackingSize);

        return(newTrackID);

    }

    public void showCroppedTracks() {

        ImagePlus[] impA = new ImagePlus[Tracks.size()];
        OpenStacksAsVirtualStack osv = new OpenStacksAsVirtualStack();

        for(int i=0; i<Tracks.size(); i++) {

            Track track = Tracks.get(i);

            if (track.completed) {

                //
                // convert track center coordinates to curated bounding box offsets
                //

                boolean[] shifted = new boolean[1];
                Point3D[] trackOffsets = new Point3D[track.getLength()];
                for(int iPosition=0; iPosition<track.getLength(); iPosition++) {
                    Point3D offset = computeOffset(track.getXYZ(iPosition), track.getObjectSize());
                    Point3D offsetCurated = osv.curatePositionOffsetSize(imp, offset, track.getObjectSize(), shifted);
                    if(shifted[0]) {
                        log("Track_"+track.getID()+" was out of image bounds at frame "+iPosition+
                                " (frame "+track.getT(iPosition)+" in original image)");
                    };
                    trackOffsets[iPosition] = offsetCurated;
                }

                impA[i] = OpenStacksAsVirtualStack.makeCroppedVirtualStack(imp, trackOffsets, track.getObjectSize(), track.getTmin(), track.getTmax());
                if (impA[i] == null) {
                    log("..cropping failed.");
                } else {
                    impA[i].setTitle(imp.getTitle()+"Track_" + track.getID());
                    impA[i].show();
                    impA[i].setPosition(0, (int) (impA[i].getNSlices() / 2 + 0.5), 0);
                    impA[i].resetDisplayRange();
                }
            }
        }
    }

    public void addTrackToOverlay(final Track track, final int i) {
        // using invokeLater to avoid that two different tracking threads change the imp overlay
        // concurrently; which could lead to disruption of the imp overlay
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int rx = (int) track.getObjectSize().getX()/2;
                int ry = (int) track.getObjectSize().getY()/2;
                int rz = (int) track.getObjectSize().getZ()/2;

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
        });
    }

    class Tracking implements Runnable {
        int iTrack, dt, bg, iterations;
        Point3D pSubSample;
        double centerOfMassFractionOfImage;

        Tracking(int iTrack, Point3D pSubSample, int gui_tSubSample, int bg, int iterations) {
            this.iTrack = iTrack;
            this.dt = gui_tSubSample;
            this.pSubSample = pSubSample;
            this.iterations = iterations;
            this.bg = bg;
            this.centerOfMassFractionOfImage = 0.5; // todo: what to do here??
        }

        public void run() {
            long startTime, stopTime, elapsedReadingTime, elapsedProcessingTime;
            ImagePlus imp0, imp1;
            Point3D p0offset;
            Point3D p1offset;
            Point3D pShift;
            Point3D pLocalShift;
            Point3D pSize;

            Track track = Tracks.get(iTrack);
            int tStart = track.getTmin();
            int channel = track.getC(0);
            int nt = track.getLength();
            track.reset();

            //
            // track first time-point by center of mass
            //

            // get selected track coordinates
            pSize = track.getObjectSize();
            p0offset = computeOffset(track.getXYZ(0), pSize);

            //
            // read data
            //

            p0offset = OpenStacksAsVirtualStack.curatePositionOffsetSize(imp, p0offset, pSize);
            imp0 = vss.getCubeByTimeOffsetAndSize(tStart, channel, p0offset, pSize, pSubSample);
            IJ.run(imp0, "Subtract...", "value="+computeMean16bit(imp0.getStack()));

            // iteratively compute the shift of the center of mass relative to the center of the image stack
            // using only half the image size for iteration

            if(Globals.verbose) log("measuring position of first time-point using center of mass...");

            startTime = System.currentTimeMillis();
            pShift = computeIterativeCenterOfMassShift16bit(imp0.getStack(), centerOfMassFractionOfImage, iterations);
            elapsedProcessingTime = System.currentTimeMillis() - startTime;

            // correct for sub-sampling
            pShift = multiplyPoint3dComponents(pShift, pSubSample);

            if(Globals.verbose) log("shift relative to where user clicked is "+pShift.toString());

            //
            // Add track location for first image
            //
            Point3D pUpdate = computeCenter(p0offset.add(pShift), pSize);
            track.addLocation(pUpdate, tStart, channel);

            // Update global track table and imp.overlay
            // - thread safe, because both internally use SwingUtilities.invokeLater
            //
            trackTable.addRow(new Object[]{
                    String.format("%1$04d", iTrack) + "_" + String.format("%1$05d", tStart ),
                    (float) pUpdate.getX(), (float) pUpdate.getY(), (float) pUpdate.getZ(), tStart, iTrack
            });

            addTrackToOverlay(track, tStart - tStart);

            totalTimePointsTracked.addAndGet(1);

            //
            // compute shifts for following time-points
            //

            boolean finish = false;
            int itMax = tStart + nt - 1;
            int itPrevious = tStart;
            int itNow;
            int itMaxUpdate;

            //
            //  Important notes for the logic:
            //  - p0offset has to be the position where the previous images was loaded
            //  - p1offset has to be the position where the current image was loaded

            for (int it = tStart + dt; it < tStart + nt + dt; it = it + dt) {

                itNow = it;

                if(itNow >= itMax) {
                    // due to the sub-sampling in t the addition of dt
                    // can cause the frame to be outside of
                    // the tracking range => load the last frame
                    itNow = itMax;
                    finish = true;
                }

                // load next image at the same position where the previous image has been loaded (p0offset)
                // plus the computed shift (pShift), basically a linear motion model
                // but curate this position according to the image bounds
                //log("Position where previous image was loaded: " + p0offset);
                //log("Position where previous image was loaded plus shift: " + p0offset.add(pShift));
                p1offset = OpenStacksAsVirtualStack.curatePositionOffsetSize(imp, p0offset.add(pShift), pSize);
                //log("Curated position where this image is loaded: " + p1offset);

                // load image
                startTime = System.currentTimeMillis();
                imp1 = vss.getCubeByTimeOffsetAndSize(itNow, channel, p1offset, pSize, pSubSample);
                elapsedReadingTime = System.currentTimeMillis() - startTime;

                // subtract mean intensity
                IJ.run(imp1, "Subtract...", "value="+computeMean16bit(imp1.getStack()));

                if (gui_trackingMethod == "correlation") {

                    if(Globals.verbose) log("measuring drift using correlation...");

                    // compute shift relative to previous time-point
                    startTime = System.currentTimeMillis();
                    pShift = computeShift16bitUsingPhaseCorrelation(imp1, imp0);
                    stopTime = System.currentTimeMillis();
                    elapsedProcessingTime = stopTime - startTime;

                    // correct for sub-sampling
                    pShift = multiplyPoint3dComponents(pShift, pSubSample);
                    //log("Correlation Tracking Shift: "+pShift);

                    if(Globals.verbose) log("shift after correction for sub-sampling is "+pShift.toString());

                    // take into account the different loading positions of this and the previous image
                    pShift = pShift.add(p1offset.subtract(p0offset));
                    //log("Correlation Tracking Shift including image shift: "+pShift);

                    if(Globals.verbose) log("actual final shift is "+pShift.toString());

                } else if (gui_trackingMethod == "center of mass") {

                    if(Globals.verbose) log("measuring drift using center of mass...");

                    // compute the different of the center of mass
                    // to the geometric center of imp1
                    startTime = System.currentTimeMillis();
                    pLocalShift = computeIterativeCenterOfMassShift16bit(imp1.getStack(), centerOfMassFractionOfImage, iterations);
                    stopTime = System.currentTimeMillis();
                    elapsedProcessingTime = stopTime - startTime;

                    // correct for sub-sampling
                    pLocalShift = multiplyPoint3dComponents(pLocalShift, pSubSample);
                    //log("Center of Mass Local Shift: "+pLocalShift);

                    if(Globals.verbose) log("local shift after correction for sub-sampling is "+pLocalShift.toString());

                    // the drift corrected position in the global coordinate system is: p1offset.add(pLocalShift)
                    // in center coordinates this is: computeCenter(p1offset.add(pShift),pSize)
                    // relative to previous tracking position:
                    pShift = computeCenter(p1offset.add(pLocalShift),pSize).subtract(track.getXYZ(itPrevious));
                    //log("Center of Mass Tracking Shift relative to previous position: "+pShift);

                    if(Globals.verbose) log("actual shift is "+pShift.toString());



                }


                // compute time-points between this and the previous one (inclusive)
                // using linear interpolation

                itMaxUpdate = itNow;
                if(finish) itMaxUpdate = itNow; // include last data point

                for (int itUpdate = itPrevious + 1; itUpdate <= itMaxUpdate; itUpdate++) {

                    Point3D pPrevious = track.getXYZ(itPrevious);
                    double interpolation = (double) (itUpdate - itPrevious) / (double) (itNow - itPrevious);
                    pUpdate = pPrevious.add(pShift.multiply(interpolation));

                    //
                    // Add to track
                    // - thread safe, because only this thread is accessing this particular track
                    //
                    track.addLocation(pUpdate, itUpdate, channel);


                    // Update global track table and imp.overlay
                    // - thread safe, because both internally use SwingUtilities.invokeLater
                    //
                    trackTable.addRow(new Object[]{
                            String.format("%1$04d", iTrack) + "_" + String.format("%1$05d", itUpdate ),
                            (float) pUpdate.getX(), (float) pUpdate.getY(), (float) pUpdate.getZ(), itUpdate, iTrack
                    });

                    addTrackToOverlay(track, itUpdate - tStart);

                }

                itPrevious = itNow;
                imp0 = imp1;
                p0offset = p1offset; // store the position where this image was loaded


                //
                // show progress
                //
                int n = totalTimePointsTracked.addAndGet(dt);

                if( (System.currentTimeMillis() > trackStatsReportDelay+trackStatsLastReport)
                        || (n==totalTimePointsToBeTracked))
                    {
                    trackStatsLastReport = System.currentTimeMillis();

                    long dtt = System.currentTimeMillis()-trackStatsLastTrackStarted;
                    int dn = n - trackStatsTotalPointsTrackedAtLastStart;
                    int nToGo = totalTimePointsToBeTracked - n;
                    float speed = (float)1.0*dn/dtt*1000;
                    float remainingTime = (float)1.0*nToGo/speed;

                    Globals.threadlog(
                            "progress = " + n + "/" + totalTimePointsToBeTracked +
                                    "; speed [n/s] = " + String.format("%.2g", speed) +
                                    "; remaining [s] = " + String.format("%.2g", remainingTime) +
                                    "; reading [ms] = " + elapsedReadingTime +
                                    "; processing [ms] = " + elapsedProcessingTime
                    );
                    }

                if(finish) break;

            }

            track.completed = true;

            return;

        }

    }

    public Point3D computeOffset(Point3D pCenter, Point3D pSize) {
        return(pCenter.subtract(pSize.subtract(1,1,1).multiply(0.5)));
    }

    public Point3D computeCenter(Point3D pOffset, Point3D pSize) {
        // center of width 7 is 0,1,2,*3*,4,5,6
        // center of width 6 is 0,1,2,*2.5*,3,4,5
        return(pOffset.add(pSize.subtract(1,1,1).multiply(0.5)));
    }

    public Point3D multiplyPoint3dComponents(Point3D p0, Point3D p1) {

        double x = p0.getX() * p1.getX();
        double y = p0.getY() * p1.getY();
        double z = p0.getZ() * p1.getZ();

        return (new Point3D(x,y,z));

    }

    public Point3D computeIterativeCenterOfMassShift16bit(ImageStack stack, double trackingFraction, int iterations) {
        Point3D pMin, pMax;

        // compute stack center and tracking radii
        // at each iteration, the center of mass is only computed for a subset of the data cube
        // this subset iteratively shifts every iteration according to the results of the center of mass computation
        Point3D pStackSize = new Point3D(stack.getWidth(), stack.getHeight(), stack.getSize() );
        Point3D pStackCenter = computeCenter(new Point3D(0,0,0), pStackSize);
        Point3D pCenter = pStackCenter;
        for(int i=0; i<iterations; i++) {
            pMin = pCenter.subtract(pStackSize.multiply(trackingFraction/2));
            pMax = pCenter.add(pStackSize.multiply(trackingFraction/2));
            pCenter = computeCenter16bit(stack, pMin, pMax);
        }
        return(pCenter.subtract(pStackCenter));
    }

    public Point3D computeCenter16bit(ImageStack stack, Point3D pMin, Point3D pMax) {

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

        // compute one-based, otherwise the numbers at x=0,y=0,z=0 are lost for the center of mass

        if (gui_centeringMethod == "center of mass") {
            for (int z = zmin + 1; z <= zmax + 1; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                short[] pixels = (short[]) ip.getPixels();
                for (int y = ymin + 1; y <= ymax + 1; y++) {
                    i = (y - 1) * width + xmin; // zero-based location in pixel array
                    for (int x = xmin + 1; x <= xmax + 1; x++) {
                        v = pixels[i] & 0xffff;
                        // v=0 is ignored automatially
                        sum += v;
                        xsum += x * v;
                        ysum += y * v;
                        zsum += z * v;
                        i++;
                    }
                }
            }
        }

        if (gui_centeringMethod == "centroid") {
            for (int z = zmin + 1; z <= zmax + 1; z++) {
                ImageProcessor ip = stack.getProcessor(z);
                short[] pixels = (short[]) ip.getPixels();
                for (int y = ymin + 1; y <= ymax + 1; y++) {
                    i = (y - 1) * width + xmin; // zero-based location in pixel array
                    for (int x = xmin + 1; x <= xmax + 1; x++) {
                        v = pixels[i] & 0xffff;
                        if (v > 0) {
                            sum += 1;
                            xsum += x;
                            ysum += y;
                            zsum += z;
                        }
                        i++;
                    }
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

    public Point3D computeShift16bitUsingPhaseCorrelation(ImagePlus imp1, ImagePlus imp0) {
        PhaseCorrelation phc = new PhaseCorrelation(ImagePlusAdapter.wrap(imp1), ImagePlusAdapter.wrap(imp0), 5, true);
        if(Globals.verbose) log("phc.process()... ");
        phc.process();
        int[] shift = phc.getShift().getPosition();
        return(new Point3D(shift[0],shift[1],shift[2]));
    }

    public int computeMean16bit(ImageStack stack) {

        //long startTime = System.currentTimeMillis();
        double sum = 0.0;
        int i;
        int width = stack.getWidth();
        int height = stack.getHeight();
        int depth = stack.getSize();
        int xMin = 0;
        int xMax = (width-1);
        int yMin = 0;
        int yMax = (height-1);
        int zMin = 0;
        int zMax = (depth-1);

        for(int z=zMin; z<=zMax; z++) {
            short[] pixels = (short[]) stack.getProcessor(z+1).getPixels();
            for (int y = yMin; y<=yMax; y++) {
                i = y * width + xMin;
                for (int x = xMin; x <= xMax; x++) {
                    sum += (pixels[i] & 0xffff);
                    i++;
                }
            }
        }

        return((int) sum/(width*height*depth));

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
    public int addTrackStartWholeDataSet(ImagePlus imp) {
        int t;

        int ntTracking = gui_ntTracking;
        t = imp.getT()-1;

        if(t+gui_ntTracking > imp.getNFrames()) {
            IJ.showMessage("Your track would be longer than the movie!\n" +
                    "Please\n- reduce the 'Track length', or\n- move the time slider to an earlier time point.");
            return(-1);
        }

        totalTimePointsToBeTracked += ntTracking;
        int newTrackID = Tracks.size();
        //log("added new track start; id = "+newTrackID+"; starting [frame] = "+t+"; length [frames] = "+ntTracking);
        Tracks.add(new Track(ntTracking));
        Tracks.get(newTrackID).addLocation(new Point3D(0, 0, imp.getZ()-1), t, imp.getC()-1);

        return(newTrackID);

    }
*/


/*

    public int getNumberOfUncompletedTracks() {
        int uncomplete = 0;
        for(Track t:Tracks) {
            if(!t.completed)
                uncomplete++;
        }
        return uncomplete;
    }


 */