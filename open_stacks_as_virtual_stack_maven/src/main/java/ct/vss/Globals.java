package ct.vss;

import ij.IJ;
import ij.ImagePlus;

import javax.swing.*;

import static ij.IJ.log;

/**
 * Created by tischi on 06/11/16.
 */
public class Globals {
    public static boolean verbose = false;
    public static String version = "2016-Nov-21a";

    public static void threadlog(final String log) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                log(log);
            }
        });
    }


    public static VirtualStackOfStacks getVirtualStackOfStacks(ImagePlus imp) {
        VirtualStackOfStacks vss = null;
        try {
            vss = (VirtualStackOfStacks) imp.getStack();
            return (vss);
        } catch (Exception e) {
            IJ.showMessage("This is only implemented for images opened with the Data Streaming Tools plugin.");
            return (null);
        }
    }

}
