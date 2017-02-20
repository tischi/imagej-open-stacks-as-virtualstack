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

