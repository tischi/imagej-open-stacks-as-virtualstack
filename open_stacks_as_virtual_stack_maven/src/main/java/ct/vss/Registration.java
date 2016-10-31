package ct.vss;
import ij.ImagePlus;
import javafx.geometry.Point3D;



/**
 * Created by tischi on 31/10/16.
 */

public class Registration {
    VirtualStackOfStacks vss;
    int nx,ny,nz;

    public void Registration(VirtualStackOfStacks vss) {
        this.vss = vss;
    }

    public Positions3D computeDrifts3D(int t, int nt, int z, int nz, int x, int nx, int y, int ny, String method, int bg) {
        Positions3D positions = new Positions3D(nt);
        ImagePlus imp0, imp1;
        Point3D comPrev, comNext, comDiff;
        Point3D global = new Point3D(x,y,z);

        this.nx = nx;
        this.ny = ny;
        this.nz = nz;

        positions.setPosition(t,global);
        positions.setPosition(t+1,global);

        // get first image
        impPrev @ position 0
        // compute center of mass
        comPrev =
        for(int it = t+1; it<nt; it++ ){

            // get next image at predicted position
            impNext = getImageAtPosition(positions.getPosition(it));
            comNext = centerOfMass(impNext, bg);
            // compute center of mass of Next image

            comDiff = comNext.subtract(comPrev);
            global = global.add(comDiff);

            // update
            positions.setPosition(it, global); // update position of this image
            positions.setPosition(it+1, global); // use also as prediction for next image
            comPrev = comNext;


        }



        return(positions);
    }

    private ImagePlus getImageAtPosition(int[] p) {
        ImagePlus imp = vss.getCroppedFrameAsImagePlus(p[0], 0, p[3], nz, p[1], nx, p[2], ny);
    }



}
