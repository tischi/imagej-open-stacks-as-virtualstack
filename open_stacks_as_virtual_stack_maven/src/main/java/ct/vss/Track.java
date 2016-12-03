package ct.vss;

import javafx.geometry.Point3D;

/**
 * Created by tischi on 03/12/16.
 */
public class Track {
    int[] t;
    int[] c;
    Point3D[] p;
    int n;

    Track(int n) {
        this.t = new int[n];
        this.c = new int[n];
        this.p = new Point3D[n];
        this.n = 0;
    }

    public void addLocation(Point3D p, int t, int c) {
        this.p[n] = p;
        this.t[n] = t;
        this.c[n] = c;
        n++;
    }

    public Point3D[] getPoints3D() {
        return(p);
    }

    public Point3D getXYZbyIndex(int i) {
        return(p[i]);
    }
    public int getTmin() {
        return(t[0]);
    }

    public int getTmax() {
        return(t[n-1]);
    }

    public int getLength() {
        return(n);
    }


}
