package ct.vss;

import javafx.geometry.Point3D;

/**
 * Created by tischi on 03/12/16.
 */
public class Point5D {
    int x,y,z,c,t;
    Point5D(int x, int y, int z, int c, int t) {
        this.x=x;
        this.y=y;
        this.z=z;
        this.c=c;
        this.t=t;
    }

    public Point3D getXYZ(int i) {
        return (new Point3D(x,y,z));
    }

    public int getC() {
        return c;
    }

    public int getT() {
        return t;
    }

}

