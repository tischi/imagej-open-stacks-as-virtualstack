package bigDataTools;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import ij.ImagePlus;
import ij.gui.Roi;
import javafx.geometry.Point3D;

import static ij.IJ.log;

public class AnalyzeObjects {

    public static void measureSpotLocationsAndDistancesInSelectedRegions(ImagePlus imp, SegmentationResults segmentationResults)
    {

        ij.gui.Overlay overlay = imp.getOverlay();

        if(overlay != null) {
            log("Number of selected points:" + overlay.size());
        }
        else
        {
            log("Please use the Point Selection tool to mark the center of regions of interest.");
        }

        // TODO: how to store the z-positions?

        for (int i=0; i<overlay.size(); i++)
        {

            Roi roi = overlay.get(i);
            Point3D pRoi =  new Point3D(roi.getXBase(), roi.getYBase(), roi.getZPosition());
            log("ROI: "+pRoi.toString());

            for (int ic=0; ic<segmentationResults.channels.length; ic++)
            {
                log("CHANNEL: "+segmentationResults.channels[ic]);
                SpotCollection spotCollection = segmentationResults.models[ic].getSpots();
                // print all spots
                for (Spot spot : spotCollection.iterable(false)) {
                    Point3D pSpot = new Point3D(spot.getDoublePosition(0),
                            spot.getDoublePosition(1),
                            spot.getDoublePosition(2));
                    log("SPOT: "+pSpot.toString());
                }
                // TODO: Spot in scaled units?
                //fiji.plugin.trackmate.Spot spot = new Spot(x,y,z,1,1);
                //spots.getClosestSpot(location, frame, false);
            }


        }

    }
}
