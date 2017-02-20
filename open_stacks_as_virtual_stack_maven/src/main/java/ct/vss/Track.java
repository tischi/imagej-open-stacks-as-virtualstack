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
import javafx.geometry.Point3D;

/**
 * Created by tischi on 03/12/16.
 */
public class Track {
    int[] t;
    int[] c; // todo: why c? change from array to index
    Point3D[] p;
    Point3D objectSize;
    int i;
    int n;
    boolean completed = false;
    int id;
    private ImagePlus imp;

    Track(int n) {
        this.t = new int[n];
        this.c = new int[n];
        this.p = new Point3D[n];
        this.n = n;
        this.i = 0;
    }

    public void addLocation(Point3D p, int t, int c) {
        if(i>n-1) {
            IJ.showMessage("Error: track got longer than initialised.");
            return;
        }
        this.p[i] = p;
        this.t[i] = t;
        this.c[i] = c;
        i++;
    }

    public void setObjectSize(Point3D p) {
        this.objectSize = p;
    }

    public void setID(int id) {
        this.id = id;
    }

    public int getID() {
        return(this.id);
    }

    public Point3D getObjectSize() {
        return(this.objectSize);
    }

    public void reset() {
        this.i = 0;
    }

    public Point3D[] getPoints3D() {
        return(p);
    }

    public Point3D getXYZ(int i) {
        return(p[i]);
    }

    public double getX(int i) {
        return(p[i].getX());
    }

    public double getY(int i) {
        return(p[i].getY());
    }

    public double getZ(int i) {
        return(p[i].getZ());
    }

    public int getT(int i) {
        return(t[i]);
    }

    public int getC(int i) {
        return(c[i]);
    }

    public int getTmin() {
        return(t[0]);
    }

    public int getTmax() {
        return(t[n-1]); // todo replace with i?!
    }

    public int getLength() {
        return(n);
    }

    public void setImp(ImagePlus imp) {
        this.imp = imp;
    }

    public ImagePlus getImp() {
        return imp;
    }
}
