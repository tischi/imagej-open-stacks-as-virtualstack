/*
 * #%L
 * Data streaming, tracking and cropping tools
 * %%
 * Copyright (C) 2017 Christian Tischer
 *
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
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
