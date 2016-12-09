package ct.vss;

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
}
