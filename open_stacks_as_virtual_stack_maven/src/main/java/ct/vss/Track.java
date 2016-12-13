package ct.vss;

import ij.IJ;
import javafx.geometry.Point3D;

/**
 * Created by tischi on 03/12/16.
 */
public class Track {
    int[] t;
    int[] c; // todo: why c? change from array to index
    Point3D[] p;
    int i;
    int n;
    boolean completed = false;

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


}
