package bigDataTools;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.DogDetectorFactory;
import fiji.plugin.trackmate.tracking.LAPUtils;
import fiji.plugin.trackmate.tracking.TrackerKeys;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPTrackerFactory;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import javafx.geometry.Point3D;

import static ij.IJ.log;

public class SegmentObjects {


    public static SegmentationResults run(ImagePlus imp,
                                          SegmentationResults segmentationResults,
                                          SegmentationSettings segmentationSettings)
    {

        if( segmentationSettings.method.equals(Globals.TRACKMATEDOG) )
        {

            segmentationResults = segmentUsingTrackMateModel(imp,
                                                        segmentationResults,
                                                        segmentationSettings);
        }
        else if ( segmentationSettings.method.equals(Globals.IMAGESUITE3D))
        {
            IJ.showMessage( segmentationSettings.method + " is not yet implemented." );
        }

        return segmentationResults;
    }



    private static SegmentationResults segmentUsingTrackMateDogDetector(ImagePlus imp,
                                                                  SegmentationResults segmentationResults,
                                                                  SegmentationSettings segmentationSettings)
    {
        return segmentationResults;
    }

    private static SegmentationResults segmentUsingTrackMateModel(ImagePlus imp,
                                                             SegmentationResults segmentationResults,
                                                             SegmentationSettings segmentationSettings)
    {

        segmentationResults.channels = segmentationSettings.channels;
        segmentationResults.models = new Model[segmentationResults.channels.length];

        for( int iChannel=0; iChannel < segmentationResults.channels.length; iChannel++ )
        {
            // TrackMate model to hold the results
            Model model = new Model();
            model.setLogger(Logger.VOID_LOGGER);

            // TrackMate settings
            Settings settings = new Settings();
            settings.detectorFactory = new DogDetectorFactory<>();

            Roi roi = imp.getRoi();

            if (roi != null && roi.isArea())
            {
                Point3D regionOffset = new Point3D(roi.getBounds().getX(), roi.getBounds().getY(), 0);
                Point3D regionSize = new Point3D(roi.getBounds().getWidth(), roi.getBounds().getHeight(), imp.getNSlices());
                settings.xstart = (int) regionOffset.getX();
                settings.xend = settings.xstart + (int) regionSize.getX() - 1;
                settings.ystart = (int) regionOffset.getX();
                settings.yend = settings.ystart + (int) regionSize.getY() - 1;
                settings.zstart = (int) regionOffset.getX();
                settings.zend = settings.zstart + (int) regionSize.getZ() - 1;
            }
            else
            {
                Point3D regionOffset = null;
                Point3D regionSize = null;
            }

            settings.setFrom(imp);
            settings.detectorSettings = settings.detectorFactory.getDefaultSettings();
            settings.detectorSettings.put(DetectorKeys.KEY_TARGET_CHANNEL, segmentationResults.channels[iChannel]); //one-based
            settings.detectorSettings.put(DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION, false);
            settings.detectorSettings.put(DetectorKeys.KEY_RADIUS, segmentationSettings.spotSizes[iChannel]);
            settings.detectorSettings.put(DetectorKeys.KEY_THRESHOLD, segmentationSettings.thresholds[iChannel]);

            // Configure spot filters - Classical filter on quality
            // TODO: Shall I filter via threshold or via QUALITY
            /*
            filter1 = FeatureFilter('QUALITY', 30, True)
            settings.addSpotFilter(filter1)
            */

            // TODO: can one get rid of the tracker?
            settings.trackerFactory = new SparseLAPTrackerFactory();
            settings.trackerSettings = LAPUtils.getDefaultLAPSettingsMap();
            settings.trackerSettings.put(TrackerKeys.KEY_LINKING_MAX_DISTANCE, TrackerKeys.DEFAULT_LINKING_MAX_DISTANCE);
            settings.trackerSettings.put(TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE, TrackerKeys.DEFAULT_GAP_CLOSING_MAX_DISTANCE);
            settings.trackerSettings.put(TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP, TrackerKeys.DEFAULT_GAP_CLOSING_MAX_FRAME_GAP);

            TrackMate trackmate = new TrackMate(model, settings);

            // Process (spot detection and tracking)
            if (!trackmate.checkInput()) {
                log("Configuration error: " + trackmate.getErrorMessage());
                //return segmentationResults;
            }

            if (!trackmate.process()) {
                log("Processing error: " + trackmate.getErrorMessage());
                //return segmentationResults;
            }

            //
            // Store results
            //
            log("##### Channel: "+segmentationResults.channels[iChannel]+"; Number of spots: " + model.getSpots().getNSpots(false) );
            segmentationResults.models[iChannel] = model;
            segmentationResults.segmentationMethod = "segmentUsingTrackMateModel";

        }

        return segmentationResults;

    }


}
