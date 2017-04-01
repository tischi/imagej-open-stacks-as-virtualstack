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

    public void measureSpotLocationsAndDistancesInSelectedRegions(ImagePlus imp,
                                                                         SegmentationResults segmentationResults)
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
                for (int ic = 0; ic < segmentationResults.channels.length; ic++) {
                    log("CHANNEL: " + segmentationResults.channels[ic]);
                    SpotCollection spotCollection = segmentationResults.models[ic].getSpots();

                    /*
                    // print all spots
                    for (Spot spot : spotCollection.iterable(false)) {
                        Point3D pSpot = new Point3D(spot.getDoublePosition(0),
                                spot.getDoublePosition(1),
                                spot.getDoublePosition(2));
                        log("SPOT: "+pSpot.toString());
                    }*/

                    int frame = 0; // 0-based
                    closestSpots[ic] = spotCollection.getClosestSpot(spotRoi, frame, false);
                    if ( closestSpots[ic] != null ) {
                        //Globals.logSpotCoordinates("CLOSEST SPOT", closestSpots[ic]);
                        //log("DISTANCE: " + Math.sqrt(closestsSpots[ic].squareDistanceTo(spotRoi)));
                        tableRow.add(closestSpots[ic].getDoublePosition(0));
                        tableRow.add(closestSpots[ic].getDoublePosition(1));
                        tableRow.add(closestSpots[ic].getDoublePosition(2));

                        // compute center of mass
                        //
                        // construct the img of respective channel
                        Img<T> img = ImageJFunctions.wrapReal(imp);
                        int numDim = img.numDimensions();
                        for ( int d = 0; d < img.numDimensions(); d++ )
                        {
                            long nd = img.dimension(d);
                            nd += 2;
                        }

                        // https://javadoc.imagej.net/ImgLib2/net/imglib2/view/Views.html
                        // extended_img = Views.extendMirrorSingle(img)
                        // interval = Views.interval(extended_img, [x - rX, y - rY, z - rZ], [x + rX, y + rY, z + rZ])
                        // https://github.com/imglib/imglib2-introductory-workshop/blob/master/completed/ImgLib2_CenterOfMass2.java
                        // http://javadoc.imagej.net/ImgLib2/index.html?net/imglib2/algorithm/neighborhood/Neighborhood.html

                        double[] cali = new double[3];
                        cali[0] = imp.getCalibration().pixelWidth;
                        cali[1] = imp.getCalibration().pixelHeight;
                        cali[2] = imp.getCalibration().pixelDepth;
                        long[] center = new long[numDim];
                        for(int span = 0; span < 3; ++span)
                        {
                            center[span] = Math.round(closestSpots[ic].getFeature(Spot.POSITION_FEATURES[span]).doubleValue() / cali[span]);
                        }
                        center[4]=0; //channel

                        long[] size = new long[]{3,3,3,0};
                        RectangleNeighborhoodGPL rectangleNeighborhood = new RectangleNeighborhoodGPL(img, new OutOfBoundsMirrorExpWindowingFactory() );
                        rectangleNeighborhood.setPosition(center);
                        rectangleNeighborhood.setSpan(size);

                        Cursor<T> cursor = rectangleNeighborhood.localizingCursor();

                        while ( cursor.hasNext() )
                        {
                            // move the cursor to the next pixel
                            cursor.fwd();

                            // intensity of the pixel
                            //double i = cursor.get().getRealDouble();
                            double x = cursor.getDoublePosition(0);
                            double y = cursor.getDoublePosition(1);
                            double z = cursor.getDoublePosition(2);
                            double v = cursor.get().getRealDouble();

                            //// sum up the location weighted by the intensity for each dimension
                            //for ( int d = 0; d < img.numDimensions(); ++d )
                            //    sumDim[ d ] += cursor.getLongPosition( d ) * i;

                            // sum up the intensities
                            //sumI += i;
                        }

                    }
                    else
                    {

                        // TODO: handle the case of not spots better
                        tableRow.add(-1.0);
                        tableRow.add(-1.0);
                        tableRow.add(-1.0);

                    }

                }

                // Compute pair-wise distance
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


    public static <T extends RealType<T>> void getAsImg(final ImagePlus imp) {
        final Img<T> wrapImg = ImageJFunctions.wrapReal(imp);
        System.out.println("ImgLib2 image type is " +
                wrapImg.firstElement().getClass().getName());

    }


}
