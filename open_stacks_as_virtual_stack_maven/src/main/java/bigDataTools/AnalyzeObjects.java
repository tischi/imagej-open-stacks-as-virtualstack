package bigDataTools;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.measure.Calibration;

import java.util.ArrayList;
import java.util.List;

import static ij.IJ.log;

// TODO: replace Point3D by import net.imglib2.RealLocalizable;

public class AnalyzeObjects {

    public static void measureSpotLocationsAndDistancesInSelectedRegions(ImagePlus imp,
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
        for (int i=0; i<overlay.size(); i++) {

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
                    } else {
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



}
