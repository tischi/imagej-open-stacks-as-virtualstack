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
        segmentationResults.table = new SpotsTable(segmentationSettings);
        segmentationResults.table.initializeTable();

        // Get spot locations and compute pair-wise distances for each selection region
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

                // Init a new row in the table
                //
                List<String> tableRow = new ArrayList<>();

                // Add experimental metadata to the table
                //
                tableRow.add(segmentationSettings.experimentalBatch);
                tableRow.add(segmentationSettings.experimentID);
                tableRow.add(segmentationSettings.treatment);
                tableRow.add(segmentationSettings.pathName);
                tableRow.add(segmentationSettings.fileName);


                // Add selected region center to the table
                //
                for (int d = 0; d < 3; d++)
                {
                    tableRow.add(String.valueOf(spotRoi.getDoublePosition(d)));
                }

                // Find the closest spot in each channel
                //
                Spot[] closestSpotsTrackMateDoGMax = new Spot[segmentationResults.channels.length];
                Spot[] closestSpotsCenterOfMass = new Spot[segmentationResults.channels.length];

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

                        // Compute center of mass
                        //
                        double backgroundValue = 0;
                        Spot spotCenterOfMass = computeCenterOfMass(spot, iChannel, backgroundValue);


                        // Add position to table
                        //
                        for (int d = 0; d < 3; d++)
                        {
                            tableRow.add(String.valueOf(spot.getDoublePosition(d)));
                        }

                        // Add center of mass to table
                        //
                        for (int d = 0; d < 3; d++)
                        {
                            tableRow.add(String.valueOf(spotCenterOfMass.getDoublePosition(d)));
                        }

                        // Remember positions for distance computations
                        //
                        closestSpotsTrackMateDoGMax[iChannel] = spot;
                        closestSpotsCenterOfMass[iChannel] = spotCenterOfMass;

                    }
                    else
                    {
                        tableRow.add("No spot found");
                        tableRow.add("No spot found");
                        tableRow.add("No spot found");
                    }

                } // channel loop


                // Compute pair-wise distances and add to table
                //

                computePairWiseDistancesAndAddToTable(tableRow, closestSpotsTrackMateDoGMax);
                computePairWiseDistancesAndAddToTable(tableRow, closestSpotsCenterOfMass);

                // Add the whole row to actual table
                //
                segmentationResults.table.addRow(tableRow.toArray(new Object[tableRow.size()]));

            }

        }
    }


    public void computePairWiseDistancesAndAddToTable(List<String> tableRow, Spot[] spots)
    {

        for ( int i = 0; i < spots.length - 1; i++ )
        {
            for ( int j = i+1; j < spots.length; j++ )
            {
                Double distance = Math.sqrt(spots[i].squareDistanceTo(spots[j]));
                tableRow.add(String.valueOf(distance));
            }
        }

    }


    public Spot computeCenterOfMass(Spot spot, int iChannel, double backgroundValue)
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
                Math.round(segmentationSettings.spotRadii[iChannel] + 0.5),
                Math.round(segmentationSettings.spotRadii[iChannel] + 0.5),
                0,  // 0 size in  channel dimension, because we only want to evaluate one channel
                Math.round(segmentationSettings.spotRadii[iChannel] + 0.5)};

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

            double x = cursor.getDoublePosition(0) * imp.getCalibration().pixelWidth;
            double y = cursor.getDoublePosition(1) * imp.getCalibration().pixelHeight;
            double c = cursor.getDoublePosition(2);  // this is the channel; should be the same always
            double z = cursor.getDoublePosition(3) * imp.getCalibration().pixelDepth;
            double v = cursor.get().getRealDouble() -  backgroundValue;

            sumDim[0] += x * v;
            sumDim[1] += y * v;
            sumDim[2] += z * v;

            sumVal += v;

        }


        int radius = 1;
        int quality = 1;
        Spot spotCenterOfMass = new Spot(
                sumDim[0]/sumVal,
                sumDim[1]/sumVal,
                sumDim[2]/sumVal,
                radius,
                quality);


        return spotCenterOfMass;

    }

}
