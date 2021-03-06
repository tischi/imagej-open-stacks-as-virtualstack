package bigDataTools;

import automic.parameters.ParameterCollection;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.frame.RoiManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.util.ArrayList;


// Notes:
// - See: https://imagej.net/TrackMate_Algorithms#Spot_features_generated_by_the_spot_detectors
// -
// - The Quality feature of the DoG is the actual maximal DoG signal
// - We hope that TrackMate will be used in experiments requiring Sub-pixel localization, such as following motor proteins in biophysical experiments, so we added schemes to achieve this. The one currently implemented uses a quadratic fitting scheme (made by Stephan Saalfeld and Stephan Preibisch) based on David Lowe SIFT work[1]. It has the advantage of being very quick, compared to the segmentation time itself.
//     - See: http://www.cs.ubc.ca/~lowe/keypoints/

// Ideas:
// - maybe bin the data in z to have it isotropic in terms sigmas?

public class AnalyzeFISHSpotsGUI implements ActionListener, FocusListener {

    // GUI
    JFrame frame;

    final String buttonSegmentSpotsText = "Find spots";
    JButton buttonSegmentSpots =  new JButton();

    final String buttonShowSpotsText = "Show spots";
    JButton buttonShowSpots =  new JButton();

    final String buttonAnalyzeSelectedRegionsText = "Analyze selected regions";
    JButton buttonAnalyzeSelectedRegions =  new JButton();

    final String buttonSaveTableText = "Save table";
    JButton buttonSaveTable =  new JButton();

    final String buttonLoadTableText = "Load table";
    JButton buttonLoadTable =  new JButton();

    final String buttonLogColumnAverageText = "Log column averages";
    JButton buttonLogColumnAverage =  new JButton();

    final String textFieldChannelsLabel = "Channel IDs [one-based]";
    JTextField textFieldChannels = new JTextField(12);

    final String textFieldSpotRadiiLabel = "Spot radii [pixels]";
    JTextField textFieldSpotRadii = new JTextField(20);

    final String textFieldSpotThresholdsLabel = "Spot channel thresholds [a.u.]";
    JTextField textFieldSpotThresholds = new JTextField(12);

    final String textFieldSpotBackgroundValuesLabel = "Spot background values [gray value]";
    JTextField textFieldSpotBackgroundValues = new JTextField(12);

    final String textFieldExperimentalBatchLabel = "Experimental batch";
    JTextField textFieldExperimentalBatch = new JTextField(15);

    final String textFieldTreatmentLabel = "Treatment";
    JTextField textFieldTreatment = new JTextField(15);

    final String textFieldExperimentIDLabel = "Experiment ID";
    JTextField textFieldExperimentID = new JTextField(15);

    final String textFieldPathNameLabel = "Path to image";
    JTextField textFieldPathName = new JTextField(15);

    final String textFieldFileNameLabel = "Filename of image";
    JTextField textFieldFileName = new JTextField(15);

    final String comboBoxSegmentationMethodLabel = "Segmentation method";
    JComboBox comboBoxSegmentationMethod = new JComboBox(new String[] {Utils.TRACKMATEDOG}); //, Utils.TRACKMATEDOGSUBPIXEL, Utils.IMAGESUITE3D

    SegmentationResults segmentationResults = new SegmentationResults();
    SegmentationSettings segmentationSettings = new SegmentationSettings();
    SegmentationOverlay segmentationOverlay;
    ParameterCollection parameterCollection;


    // Other
    ImagePlus imp;

    public AnalyzeFISHSpotsGUI(ParameterCollection parameterCollection)
    {
        this.parameterCollection = parameterCollection;
    }

    public void showDialog()
    {

        imp = IJ.getImage();

        frame = new JFrame("FISH Spots");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Container c = frame.getContentPane();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));


        // Panels
        //
        ArrayList<JPanel> panels = new ArrayList<JPanel>();
        int iPanel = 0;

        // Spot detection
        //
        addHeader(panels, iPanel++, c, "SPOT DETECTION");
        addComboBox(panels, iPanel++, c, comboBoxSegmentationMethod, comboBoxSegmentationMethodLabel);
        addTextField(panels, iPanel++, c, textFieldChannels, textFieldChannelsLabel, "2;3;4");
        addTextField(panels, iPanel++, c, textFieldSpotRadii, textFieldSpotRadiiLabel, "1.5,1.5,3;1.5,1.5,3;1.5,1.5,3");
        addTextField(panels, iPanel++, c, textFieldSpotThresholds, textFieldSpotThresholdsLabel, "200;200;200");
        addButton(panels, iPanel++, c, buttonSegmentSpots, buttonSegmentSpotsText);

        // Spot analysis
        //
        addHeader(panels, iPanel++, c, "SPOT ANALYSIS");
        addTextField(panels, iPanel++, c, textFieldSpotBackgroundValues, textFieldSpotBackgroundValuesLabel, "100;300;300");
        addButton(panels, iPanel++, c, buttonAnalyzeSelectedRegions, buttonAnalyzeSelectedRegionsText);

        // Table
        //
        addHeader(panels, iPanel++, c, "TABLE");
        addTextField(panels, iPanel++, c, textFieldExperimentalBatch, textFieldExperimentalBatchLabel, "Today");
        addTextField(panels, iPanel++, c, textFieldExperimentID, textFieldExperimentIDLabel, "001");
        addTextField(panels, iPanel++, c, textFieldTreatment, textFieldTreatmentLabel, "Negative_Control");
        //addTextField(panels, iPanel++, c, textFieldPathName, textFieldPathNameLabel, imp.getOriginalFileInfo().directory);
        //addTextField(panels, iPanel++, c, textFieldFileName, textFieldFileNameLabel, imp.getOriginalFileInfo().fileName);
        addButton(panels, iPanel++, c, buttonLogColumnAverage, buttonLogColumnAverageText);
        addButton(panels, iPanel++, c, buttonSaveTable, buttonSaveTableText);
        //addButton(panels, iPanel++, c, buttonLoadTable, buttonLoadTableText);

        // Show GUI
        //
        frame.pack();
        frame.setLocation(imp.getWindow().getX() + imp.getWindow().getWidth(), imp.getWindow().getY());
        frame.setVisible(true);

    }

    public void actionPerformed(ActionEvent e)
    {

        // update current imp object
        imp = IJ.getImage();

        // update segmentationSettings
        segmentationSettings.frames = null;
        segmentationSettings.channels = Utils.delimitedStringToIntegerArray(textFieldChannels.getText(), ";");
        segmentationSettings.channelIDs = textFieldChannels.getText();
        segmentationSettings.spotRadii = new double[segmentationSettings.channels.length][];
        String[] spotRadii = textFieldSpotRadii.getText().split(";");
        for (int iChannel = 0; iChannel < segmentationSettings.channels.length; iChannel++)
        {
            segmentationSettings.spotRadii[iChannel] = Utils.delimitedStringToDoubleArray(spotRadii[iChannel], ",");
        }
        segmentationSettings.backgrounds = Utils.delimitedStringToDoubleArray(textFieldSpotBackgroundValues.getText()
                , ";");
        segmentationSettings.thresholds = Utils.delimitedStringToDoubleArray(textFieldSpotThresholds.getText(), ";");
        segmentationSettings.experimentalBatch = textFieldExperimentalBatch.getText();
        segmentationSettings.experimentID = textFieldExperimentID.getText();
        segmentationSettings.treatment = textFieldTreatment.getText();
        segmentationSettings.method = (String) comboBoxSegmentationMethod.getSelectedItem();
        segmentationSettings.pathName = imp.getOriginalFileInfo().directory; // textFieldPathName.getText();
        segmentationSettings.fileName = imp.getOriginalFileInfo().fileName; // textFieldFileName.getText();


        if ( e.getActionCommand().equals(buttonSegmentSpotsText) )
        {
            // Segment
            //
            segmentationResults = SegmentObjects.run(imp,
                    segmentationResults,
                    segmentationSettings);

            // Construct and show overlay
            //
            segmentationOverlay = new SegmentationOverlay(imp,
                    segmentationResults,
                    segmentationSettings);

            segmentationOverlay.setTrackMateModelForVisualisationOfSelectedChannels();
            segmentationOverlay.displayTrackMateModelAsOverlay();

            // Prepare for marking regions
            //
            RoiManager roiManager = RoiManager.getInstance();
            if (roiManager == null)
                roiManager = new RoiManager();
            IJ.run("Point Tool...", "type=Hybrid color=Blue size=[Extra Large] add label");

            IJ.run(imp, "Make Composite", "");
            IJ.run("Channels Tool...");
            //imp.setActiveChannels("0111");

        }



        if ( e.getActionCommand().equals(buttonAnalyzeSelectedRegionsText) )
        {

            // Check the ROI Manager for selected points
            //
            // ....

            // Measure spots around selected points
            //
            SegmentationAnalysis segmentationAnalysis = new SegmentationAnalysis(imp, segmentationSettings, segmentationResults);

            SpotCollection selectedPoints = getSpotCollectionFromRoiManager();

            segmentationAnalysis.analyzeSpotsAroundSelectedPoints(selectedPoints);



            // Show results jTableSpots
            //
            segmentationResults.jTableSpots.showTable();

            // Notify jTableSpots about overlay (such that it can change it, upon selection of a specific row)
            //
            segmentationResults.jTableSpots.segmentationOverlay = segmentationOverlay;

        }

        if ( e.getActionCommand().equals(buttonLogColumnAverageText) )
        {
            // Print average values of all columns to log window
            //
            segmentationResults.jTableSpots.logColumnAverages();
        }

        if ( e.getActionCommand().equals(buttonSaveTableText))
        {

            // Save Table
            //
            JFileChooser jFileChooser = new JFileChooser(segmentationSettings.pathName);
            if (jFileChooser.showSaveDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
                File file = jFileChooser.getSelectedFile();
                segmentationResults.jTableSpots.saveTable(file);
            }

        }


        if ( e.getActionCommand().equals(buttonLoadTableText))
        {

            // Load Table
            //
            JFileChooser jFileChooser = new JFileChooser(segmentationSettings.pathName);
            if (jFileChooser.showOpenDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
                File file = jFileChooser.getSelectedFile();
                if ( file != null )
                {
                    // load and show jTableSpots
                    //
                    segmentationResults.jTableSpots = new jTableSpots();
                    segmentationResults.jTableSpots.loadTable(file);
                    segmentationResults.jTableSpots.showTable();

                    // construct overlay from jTableSpots
                    //
                    //segmentationResults.jTableSpots.segmentationOverlay = segmentationOverlay;
                }
                else
                {
                    Utils.threadlog("No file selected.");
                }

            }

        }

    }


    public SpotCollection getSpotCollectionFromRoiManager()
    {
        SpotCollection spotCollection = new SpotCollection();

        RoiManager roiManager = RoiManager.getInstance();
        if (roiManager != null)
        {
            Roi[] rois = roiManager.getRoisAsArray();
            for (Roi roi : rois)
            {
                if (roi.getTypeAsString().equals("Point"))
                {

                    Calibration calibration = imp.getCalibration();

                    int radius = 1;
                    int quality = 1;

                    Spot spot = new Spot(
                            calibration.getX(roi.getXBase()),
                            calibration.getY(roi.getYBase()),
                            calibration.getZ(roi.getZPosition() - 1), // roi z-position is the slice and thus one-based
                            radius,
                            quality);

                    int frame = 0;
                    spotCollection.add(spot, frame);
                }
            }
        }

        return spotCollection;

    }

    private void addHeader(ArrayList<JPanel> panels, int iPanel, Container c, String label)
    {
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel(label));
        c.add(panels.get(iPanel++));
    }

    private void addTextField(ArrayList<JPanel> panels, int iPanel, Container c, JTextField textField, String textFieldLabel, String textFieldDefault)
    {
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        textField.setActionCommand(textFieldLabel);
        textField.addActionListener(this);
        textField.addFocusListener(this);
        textField.setText(textFieldDefault);
        panels.get(iPanel).add(new JLabel(textFieldLabel));
        panels.get(iPanel).add(textField);
        c.add(panels.get(iPanel));
    }

    private void addButton(ArrayList<JPanel> panels, int iPanel, Container c, JButton button, String buttonLabel)
    {
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        button.setActionCommand(buttonLabel);
        button.addActionListener(this);
        button.setText(buttonLabel);
        panels.get(iPanel).add(button);
        c.add(panels.get(iPanel));
    }

    private void addComboBox(ArrayList<JPanel> panels, int iPanel, Container c, JComboBox comboBox, String comboBoxLabel)
    {
        panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
        panels.get(iPanel).add(new JLabel(comboBoxLabel));
        panels.get(iPanel).add(comboBox);
        c.add(panels.get(iPanel));
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

}
