/* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*/


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
