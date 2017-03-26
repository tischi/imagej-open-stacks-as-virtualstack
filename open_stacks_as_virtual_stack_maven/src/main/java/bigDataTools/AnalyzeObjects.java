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

        //
        // Make and show the Results Table
        //
        AnalyzeFishSpotsTable analyzeFishSpotsTable = new AnalyzeFishSpotsTable();
        analyzeFishSpotsTable.initializeTable(segmentationResults.channels);
        analyzeFishSpotsTable.showTable();

        for (int i=0; i<overlay.size(); i++)
        {

            Roi roi = overlay.get(i);
            Calibration calibration = imp.getCalibration();
            int radius = 1;
            int quality = 1;
            Spot spotRoi = new Spot(calibration.getX(roi.getXBase()),
                            calibration.getY(roi.getYBase()),
                            calibration.getZ(roi.getZPosition()),
                                    radius,
                    quality);
            Globals.logSpotCoordinates("ROI", spotRoi);

            List<Double> tableRow = new ArrayList<>();
            tableRow.add(spotRoi.getDoublePosition(0));
            tableRow.add(spotRoi.getDoublePosition(1));
            tableRow.add(spotRoi.getDoublePosition(2));

            Spot[] closestSpots = new Spot[segmentationResults.channels.length];

            for (int ic=0; ic<segmentationResults.channels.length; ic++)
            {
                log("CHANNEL: "+segmentationResults.channels[ic]);
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
                Globals.logSpotCoordinates("CLOSEST SPOT", closestSpots[ic]);
                //log("DISTANCE: " + Math.sqrt(closestsSpots[ic].squareDistanceTo(spotRoi)));

                tableRow.add(closestSpots[ic].getDoublePosition(0));
                tableRow.add(closestSpots[ic].getDoublePosition(1));
                tableRow.add(closestSpots[ic].getDoublePosition(2));

            }

            analyzeFishSpotsTable.addRow(tableRow.toArray(new Object[tableRow.size()]));

        }

    }



}
