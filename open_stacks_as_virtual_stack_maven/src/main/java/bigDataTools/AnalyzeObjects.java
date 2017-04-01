package bigDataTools;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.util.SpotNeighborhood;

import fiji.plugin.trackmate.util.SpotNeighborhoodCursor;

import net.imagej.ImgPlus;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.RectangleNeighborhood;
import net.imglib2.algorithm.region.localneighborhood.RectangleNeighborhoodGPL;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.outofbounds.OutOfBoundsMirrorExpWindowingFactory;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.RealType;

import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

import static ij.IJ.currentMemory;
import static ij.IJ.log;

// TODO: replace Point3D by import net.imglib2.RealLocalizable;

public class AnalyzeObjects< T extends RealType< T >> {

    ImagePlus imp;
    SegmentationSettings segmentationSettings;
    SegmentationResults segmentationResults;

    public AnalyzeObjects(ImagePlus imp, SegmentationSettings segmentationSettings,
                          SegmentationResults segmentationResults)
    {
        this.imp = imp;
        this.segmentationResults = segmentationResults;
        this.segmentationSettings = segmentationSettings;
    }


    public void measureSpotLocationsAndDistancesInSelectedRegions()
    {
        ij.gui.Overlay overlay = imp.getOverlay();

        if(overlay != null) {
            log("Number of selected points:" + overlay.size());
        }
        else
        {
            log("Please use the Point Selection tool to mark the center of regions of interest.");
        }


        // Initialise Results Table
        //
        segmentationResults.table = new SpotsTable();
        segmentationResults.table.initializeTable(segmentationResults.channels);


        // Get spot locations and compute pari-wise distances for each selection region
        //
        for ( int i=0; i<overlay.size(); i++ ) {

            Roi roi = overlay.get(i);

            //Globals.threadlog(roi.toString());
            //Globals.threadlog(roi.getTypeAsString());

            if (roi.getTypeAsString().equals("Point")) {

                Calibration calibration = imp.getCalibration();
                int radius = 1;
                int quality = 1;
                Spot spotRoi = new Spot(calibration.getX(roi.getXBase()),
                        calibration.getY(roi.getYBase()),
                        calibration.getZ(roi.getZPosition()),
                        radius,
                        quality);

                //Globals.logSpotCoordinates("ROI", spotRoi);

                // Add the selection region to the table
                //
                List<Double> tableRow = new ArrayList<>();
                tableRow.add(spotRoi.getDoublePosition(0));
                tableRow.add(spotRoi.getDoublePosition(1));
                tableRow.add(spotRoi.getDoublePosition(2));

                // Find the closest spot in each channel
                //

                Spot[] closestSpots = new Spot[segmentationResults.channels.length];
                for (int iChannel = 0; iChannel < segmentationResults.channels.length; iChannel++) {

                    SpotCollection spotCollection = segmentationResults.models[iChannel].getSpots();

                    /*
                    // print all spots
                    for (Spot spot : spotCollection.iterable(false)) {
                        Point3D pSpot = new Point3D(spot.getDoublePosition(0),
                                spot.getDoublePosition(1),
                                spot.getDoublePosition(2));
                        log("SPOT: "+pSpot.toString());
                    }*/

                    int frame = 0; // 0-based
                    Spot spot = spotCollection.getClosestSpot(spotRoi, frame, false);

                    if ( spot != null ) {

                        // Add trackmate position to the table
                        //
                        tableRow.add(spot.getDoublePosition(0));
                        tableRow.add(spot.getDoublePosition(1));
                        tableRow.add(spot.getDoublePosition(2));

                        // Compute center of mass
                        //
                        double backgroundValue = 0;
                        double[] centerOfMass = computeCenterOfMass(spot, iChannel, backgroundValue);


                    }
                    else
                    {

                        // TODO: handle the case of not spots better
                        tableRow.add(-1.0);
                        tableRow.add(-1.0);
                        tableRow.add(-1.0);

                    }

                    closestSpots[iChannel] = spot;

                }

                // Compute pair-wise distances
                //
                for ( int ic = 0; ic < segmentationResults.channels.length - 1; ic++ )
                {
                    for ( int jc = ic+1; jc < segmentationResults.channels.length; jc++ )
                    {

                        Double distance = Math.sqrt(closestSpots[ic].squareDistanceTo(closestSpots[jc]));
                        tableRow.add(distance);
                    }

                }

                // Add row to table
                //
                segmentationResults.table.addRow(tableRow.toArray(new Object[tableRow.size()]));

            }



        }
    }



    public double[] computeCenterOfMass(Spot spot, int iChannel, double backgroundValue)
    {

        // https://javadoc.imagej.net/ImgLib2/net/imglib2/view/Views.html
        // https://github.com/imglib/imglib2-introductory-workshop/blob/master/completed/ImgLib2_CenterOfMass2.java
        // http://javadoc.imagej.net/ImgLib2/index.html?net/imglib2/algorithm/neighborhood/Neighborhood.html



        // wrap to img
        //
        Img<T> img = ImageJFunctions.wrapReal(imp);

        /*for ( int d = 0; d < img.numDimensions(); d++ )
        {
            long nd = img.dimension(d);
            nd += 2;
        }*/


        // Compute spot center in pixel coordinates
        //
        // ! Dimensions in img are: x,y,c,z,t
        long[] center = new long[img.numDimensions()];
        center[0] = Math.round(spot.getFeature(Spot.POSITION_FEATURES[0]).doubleValue() / imp.getCalibration().pixelWidth);
        center[1] = Math.round(spot.getFeature(Spot.POSITION_FEATURES[1]).doubleValue() / imp.getCalibration().pixelHeight);
        center[2] = segmentationSettings.channels[iChannel] - 1;  // zero-based channels for img
        center[3] = Math.round(spot.getFeature(Spot.POSITION_FEATURES[2]).doubleValue() / imp.getCalibration().pixelDepth);

        // Set radii of the region in which the center of mass should be computed
        //

        // TODO: consider multiplication of region size with a constant factor gere
        long[] size = new long[]{
                segmentationSettings.spotRadii[iChannel],
                segmentationSettings.spotRadii[iChannel],
                0,  // 0 size in  channel dimension, because we only want to evaluate one channel
                segmentationSettings.spotRadii[iChannel]};

        // Create a local neighborhood around this spot
        //
        RectangleNeighborhoodGPL rectangleNeighborhood = new RectangleNeighborhoodGPL(img, new OutOfBoundsMirrorExpWindowingFactory() );
        rectangleNeighborhood.setPosition(center);
        rectangleNeighborhood.setSpan(size);
        Cursor<T> cursor = rectangleNeighborhood.localizingCursor();

        // Loop through the region and compute the center of mass
        //
        double[] sumDim = new double[]{0,0,0};
        double sumVal = 0;
        while ( cursor.hasNext() )
        {
            // move the cursor to the next pixel
            cursor.fwd();


            double x = cursor.getDoublePosition(0);
            double y = cursor.getDoublePosition(1);
            double c = cursor.getDoublePosition(2);  // this is the channel; should be the same always
            double z = cursor.getDoublePosition(3);
            double v = cursor.get().getRealDouble() -  backgroundValue;

            sumDim[0] += x * v;
            sumDim[1] += y * v;
            sumDim[2] += z * v;

            sumVal += v;

        }


        double[] centerOfMass =  new double[]
                {
                        sumDim[0]/sumVal,
                        sumDim[1]/sumVal,
                        sumDim[2]/sumVal,
                };


        return centerOfMass;

    }

}
