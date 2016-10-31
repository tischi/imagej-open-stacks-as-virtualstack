package ct.vss;

import static ij.IJ.log;
import javafx.geometry.Point3D;

/**
 * Created by tischi on 31/10/16.
 */

public class Positions3D {
    public int nt, t;
    private int[][] txyz;

    public Positions3D(int nt, int t) {
        this.t = t; //starting time-point
        this.nt = nt;
        this.txyz = new int[nt][3];
    }

    public void setPosition(int it, int x, int y, int z) {
        if (it < t || it > t+nt-1) {
            throw new IllegalArgumentException("t="+it+" is out of range,"+(nt+t-1));
        }
        txyz[it-t][0] = t;
        txyz[it-t][1] = x;
        txyz[it-t][2] = y;
        txyz[it-t][2] = z;

        log("Set Position: "+t+" "+x+" "+y+" "+z);
    }

    public int[] getPosition(int it) {
        if (it < t || it > t+nt-1) {
            throw new IllegalArgumentException("t="+it+" is out of range,"+(nt+t-1));
        }
        return(txyz[it-t]);
    }

    public void setPosition(int it, Point3D p) {
        if (it < t || it > t+nt-1) {
            throw new IllegalArgumentException("t="+it+" is out of range,"+(nt+t-1));
        }
        txyz[it-t][0] = t;
        txyz[it-t][1] = (int) p.getX();
        txyz[it-t][2] = (int) p.getY();
        txyz[it-t][2] = (int) p.getZ();

        log("Set Position: "+t+" "+x+" "+y+" "+z);
    }


    /**
    public void addToPosition(int it, int t, int x, int y, int z) {
        if (it > nt-1) {
            throw new IllegalArgumentException("t="+t+" is out of range, nt="+nt);
        }
        txyz[it][0] += t;
        txyz[it][1] += x;
        txyz[it][2] += y;
        txyz[it][2] += z;

        log("Added To Position: "+t+" "+x+" "+y+" "+z);
    }*/

    public void printPositions() {
        for(int it=0; it<nt; it++) {
            log("Position: "+xyz[0]+" "+xyz[1]+" "+xyz[2]+" "+xyz[3]);
        }

    }

}
