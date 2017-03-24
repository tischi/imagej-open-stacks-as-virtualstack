package bigDataTools;


import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;

/**
 * Created by tischi on 24/03/17.
 */
public class AnalyzeFishSpots implements PlugIn {

    ImagePlus imp;
    PluginGUI pluginGUI;

    public AnalyzeFishSpots() {
    }

    public AnalyzeFishSpots(String path) {
        IJ.open(path);
        this.imp = IJ.getImage();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                showDialog();
            }
        });
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
        pluginGUI = new PluginGUI();
        pluginGUI.showDialog();
    }

    private void initialize() {
    }

    class PluginGUI implements ActionListener, FocusListener {

        JFrame frame;
        final String findSpotsButtonName = "Find Spots";
        JButton findSpotsButton =  new JButton(findSpotsButtonName);


        public void PluginGUI() {
        }

        public void showDialog() {

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
            // actions
            panels.add(new JPanel(new FlowLayout(FlowLayout.RIGHT)));
            findSpotsButton.setActionCommand(findSpotsButtonName);
            findSpotsButton.addActionListener(this);
            panels.get(iPanel).add(findSpotsButton);
            c.add(panels.get(iPanel++));
            //
            // Show the GUI
            //
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

            // update current imp object
            imp = IJ.getImage();

            if (e.getActionCommand().equals(findSpotsButtonName)) {

                IJ.showMessage("Hello");

            }
        }

    }


}
