package bigDataTools;

import ij.ImagePlus;

import fiji.plugin.trackmate.*;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;

/**
 * Created by tischi on 25/03/17.
 */
public class OverlaySegmentationResults {

    public static void run(ImagePlus imp, SegmentationResults segmentationResults, SegmentationSettings segmentationSettings)
    {

        SelectionModel sm = new SelectionModel(segmentationResults.trackMateModel);
        HyperStackDisplayer displayer =  new HyperStackDisplayer(segmentationResults.trackMateModel, sm, imp);
        displayer.render();

    }


}
