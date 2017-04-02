package bigDataTools;


import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

import javax.swing.*;

/**
 * Created by tischi on 24/03/17.
 */
public class AnalyzeFISHSpotsPlugIn implements PlugIn {

    ImagePlus imp;
    AnalyzeFishSpotsGUI gui;

    public AnalyzeFISHSpotsPlugIn() {
    }

    public AnalyzeFISHSpotsPlugIn(String path) {
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
        gui = new AnalyzeFishSpotsGUI();
        gui.showDialog();
    }

    private void initialize() {
    }


}
