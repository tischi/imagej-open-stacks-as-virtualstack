package bigDataTools;

import ij.IJ;
import ij.ImagePlus;
import javafx.geometry.Point3D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;


public class AnalyzeFishSpotsGUI implements ActionListener, FocusListener
{

        // GUI
        JFrame frame;

        final String buttonSegmentSpotsText = "Find Spots";
        JButton buttonSegmentSpots =  new JButton();

        final String buttonShowSpotsText = "Show Spots";
        JButton buttonShowSpots =  new JButton();

        final String textFieldSpotSizeLabel = "Spot size";
        JTextField textFieldSpotSize = new JTextField(3);

        final String textFieldSpotThresholdLabel = "Spot threshold";
        JTextField textFieldSpotThreshold = new JTextField(3);

        final String comboBoxSegmentationMethodLabel = "Segmentation method";
        JComboBox comboBoxSegmentationMethod = new JComboBox(new String[] {Globals.TRACKMATEDOG,Globals.IMAGESUITE3D});

        SegmentationResults segmentationResults = new SegmentationResults();
        SegmentationSettings segmentationSettings = new SegmentationSettings();

        // Other
        ImagePlus imp;

        public void AnalyzeFishSpotsGUI()
        {
        }


        public void showDialog() {

            imp = IJ.getImage();

            frame = new JFrame("Analyze Spots");
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
            addButton(panels, iPanel++, c, buttonSegmentSpots, buttonSegmentSpotsText);
            addComboBox(panels, iPanel++, c, comboBoxSegmentationMethod, comboBoxSegmentationMethodLabel);
            addTextField(panels, iPanel++, c, textFieldSpotSize, textFieldSpotSizeLabel, "5.0");
            addTextField(panels, iPanel++, c, textFieldSpotThreshold, textFieldSpotThresholdLabel, "1.0");

            //
            // Spot visualisation
            //

            // header
            panels.add(new JPanel(new FlowLayout(FlowLayout.LEFT)));
            panels.get(iPanel).add(new JLabel("SPOT VISUALIZATION"));
            c.add(panels.get(iPanel++));

            // show spots button
            addButton(panels, iPanel++, c, buttonShowSpots, buttonShowSpotsText);

            //
            // Show the GUI
            //
            frame.pack();
            frame.setLocation(imp.getWindow().getX() + imp.getWindow().getWidth(), imp.getWindow().getY());
            frame.setVisible(true);

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

        public void actionPerformed(ActionEvent e) {

            // update current imp object
            imp = IJ.getImage();

            if (e.getActionCommand().equals(buttonSegmentSpotsText)) {

                segmentationSettings.trackMateSpotSize = new Double(textFieldSpotSize.getText());
                segmentationSettings.trackMateSpotThreshold = new Double(textFieldSpotThreshold.getText());
                segmentationSettings.method = (String) comboBoxSegmentationMethod.getSelectedItem();

                int[] channels = new int[]{2}; // one-based
                int[] frames = new int[]{1}; // one-based

                Point3D regionOffset = null;
                Point3D regionSize = null;

                segmentationResults = SegmentObjects.run(imp,
                        segmentationResults,
                        segmentationSettings,
                        channels,
                        frames,
                        regionOffset,
                        regionSize);
            }

            if (e.getActionCommand().equals(buttonShowSpotsText)) {
                IJ.showMessage("Not implemented");
            }
        }

}
