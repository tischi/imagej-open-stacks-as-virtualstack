package ct.vss;

import static ij.IJ.log;
import javafx.geometry.Point3D;

/**
 * Created by tischi on 31/10/16.
 */

public class Positions3D {
    public int nt, t;
    private int[][] txyz;
    int zmax, xmax, ymax;
    int nz, nx, ny;


    public Positions3D(int nt, int t, int xmax, int ymax, int zmax, int nx, int ny, int nz) {
        this.t = t; //starting time-point
        this.nt = nt;
        this.xmax = xmax;
        this.ymax = ymax;
        this.zmax = zmax;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.txyz = new int[nt][4];
    }

    public void setPosition(int it, double xd, double yd, double zd) {
        if (it < t || it > t+nt-1) {
            throw new IllegalArgumentException("t="+it+" is out of range,"+(nt+t-1));
        }

        // round the values
        int x = (int) (xd+0.5);
        int y = (int) (yd+0.5);
        int z = (int) (zd+0.5);

        // make sure that the ROI stays within the image bounds
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (z < 0) z = 0;

        if (x+nx > xmax) x = xmax-nx;
        if (y+ny > ymax) y = ymax-ny;
        if (z+nz > zmax) z = zmax-nz;

        txyz[it-t][0] = it;
        txyz[it-t][1] = x;
        txyz[it-t][2] = y;
        txyz[it-t][3] = z;

        //log("Set Position: "+t+" "+x+" "+y+" "+z);
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
        int x = (int) p.getX();
        int y = (int) p.getY();
        int z = (int) p.getZ();
        setPosition(it,x,y,z);
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
        for(int it=t; it<nt+t; it++) {
            log("Position: "+txyz[it-t][0]+" "+txyz[it-t][1]+" "+txyz[it-t][2]+" "+txyz[it-t][3]);
        }

    }

}
