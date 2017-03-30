package bigDataTools;

import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;


// Notes:
// - See: https://imagej.net/TrackMate_Algorithms#Spot_features_generated_by_the_spot_detectors
// - The Quality feature of the DoG is the actual maximal DoG signal
// - We hope that TrackMate will be used in experiments requiring Sub-pixel localization, such as following motor proteins in biophysical experiments, so we added schemes to achieve this. The one currently implemented uses a quadratic fitting scheme (made by Stephan Saalfeld and Stephan Preibisch) based on David Lowe SIFT work[1]. It has the advantage of being very quick, compared to the segmentation time itself.
//     - See: http://www.cs.ubc.ca/~lowe/keypoints/

// Ideas:
// - maybe bin the data in z to have it isotropic in terms sigmas?

public class AnalyzeFishSpotsGUI implements ActionListener, FocusListener
{

    // GUI
    JFrame frame;

    final String buttonSegmentSpotsText = "Find Spots";
    JButton buttonSegmentSpots =  new JButton();

    final String buttonShowSpotsText = "Show Spots";
    JButton buttonShowSpots =  new JButton();

    final String buttonAnalyzeSelectedRegionsText = "Analyze Selected Regions";
    JButton buttonAnalyzeSelectedRegions =  new JButton();

    final String buttonLogColumnAverageText = "Column averages";
    JButton buttonLogColumnAverage =  new JButton();

    final String textFieldRegionSizeLabel = "Region size [pixels]";
    JTextField textFieldRegionSize = new JTextField(12);

    final String textFieldChannelsLabel = "Channels [one-based]";
    JTextField textFieldChannels = new JTextField(12);

    final String textFieldSpotSizesLabel = "Spot sizes";
    JTextField textFieldSpotSizes = new JTextField(12);

    final String textFieldSpotThresholdsLabel = "Spot thresholds";
    JTextField textFieldSpotThresholds = new JTextField(12);

    final String comboBoxSegmentationMethodLabel = "Segmentation method";
    JComboBox comboBoxSegmentationMethod = new JComboBox(new String[] {Globals.TRACKMATEDOG,Globals.IMAGESUITE3D});

    SegmentationResults segmentationResults = new SegmentationResults();
    SegmentationSettings segmentationSettings = new SegmentationSettings();
    SegmentationOverlay segmentationOverlay;


    // Other
    ImagePlus imp;

    public void AnalyzeFishSpotsGUI()
    {
    }


    public void showDialog() {

        // this ensures that selection points are added to the overlay
        IJ.run("Point Tool...", "type=Hybrid color=Green size=Small add_to label");

        imp = IJ.getImage();

        frame = new JFrame("Spot Segmentation");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Container c = frame.getContentPane();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));

        //
        // Panels
        //
        ArrayList<JPanel> panels = new ArrayList<JPanel>();
        int iPanel = 0;

        //
        // Spot detection
        //

        // header
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel("SPOT DETECTION"));
        c.add(panels.get(iPanel++));

        // action
        addComboBox(panels, iPanel++, c, comboBoxSegmentationMethod, comboBoxSegmentationMethodLabel);
        addTextField(panels, iPanel++, c, textFieldChannels, textFieldChannelsLabel, "2,3,4");
        addTextField(panels, iPanel++, c, textFieldSpotSizes, textFieldSpotSizesLabel, "0.5,0.5,0.5");
        addTextField(panels, iPanel++, c, textFieldSpotThresholds, textFieldSpotThresholdsLabel, "100.0,100.0,100.0");
        addButton(panels, iPanel++, c, buttonSegmentSpots, buttonSegmentSpotsText);

        //
        // Spot analysis
        //

        // header
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel("SPOT ANALYSIS"));
        c.add(panels.get(iPanel++));

        // show spots button
        //addTextField(panels, iPanel++, c, textFieldRegionSize, textFieldRegionSizeLabel, "10,10,10");
        addButton(panels, iPanel++, c, buttonAnalyzeSelectedRegions, buttonAnalyzeSelectedRegionsText);


        //
        // Table
        //

        // header
        panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
        panels.get(iPanel).add(new JLabel("TABLE"));
        c.add(panels.get(iPanel++));

        // show spots button
        //addTextField(panels, iPanel++, c, textFieldRegionSize, textFieldRegionSizeLabel, "10,10,10");
        addButton(panels, iPanel++, c, buttonLogColumnAverage, buttonLogColumnAverageText);

        //
        // Show the GUI
        //
        frame.pack();
        frame.setLocation(imp.getWindow().getX() + imp.getWindow().getWidth(), imp.getWindow().getY());
        frame.setVisible(true);

    }

    public void actionPerformed(ActionEvent e) {

        // update current imp object
        imp = IJ.getImage();

        // update segmentationSettings
        segmentationSettings.frames = null;
        segmentationSettings.channels = Globals.commaSeparatedStringToIntegerArray(textFieldChannels.getText());
        segmentationSettings.spotSizes = Globals.commaSeparatedStringToDoubleArray(textFieldSpotSizes.getText());
        segmentationSettings.thresholds = Globals.commaSeparatedStringToDoubleArray(textFieldSpotThresholds.getText());
        segmentationSettings.method = (String) comboBoxSegmentationMethod.getSelectedItem();

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

            segmentationOverlay.createHyperStackDisplayer();

        }


        if ( e.getActionCommand().equals(buttonAnalyzeSelectedRegionsText) )
        {
            // Measure spots around selected points
            //
            AnalyzeObjects.measureSpotLocationsAndDistancesInSelectedRegions(imp, segmentationResults);

            // Show results table
            //
            segmentationResults.table.showTable();

            // Notify table about overlay (such that it can change it, upon selection of a specific row)
            //
            segmentationResults.table.setSegmentationOverlay(segmentationOverlay);


        }

        if ( e.getActionCommand().equals(buttonLogColumnAverageText) )
        {
            segmentationResults.table.logColumnAverages();
        }


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
