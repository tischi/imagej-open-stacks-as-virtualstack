package bigDataTools;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.DogDetectorFactory;
import fiji.plugin.trackmate.detection.LogDetectorFactory;
import fiji.plugin.trackmate.tracking.LAPUtils;
import fiji.plugin.trackmate.tracking.TrackerKeys;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPTrackerFactory;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import javafx.geometry.Point3D;

import static ij.IJ.log;

/**
 * Created by tischi on 25/03/17.
 */
public class SegmentObjects {


    public static SegmentationResults run(ImagePlus imp,
                                          SegmentationResults segmentationResults,
                                          SegmentationSettings segmentationSettings,
                                          int[] channels,
                                          int[] frames)
    {

        if( segmentationSettings.method.equals(Globals.TRACKMATEDOG) )
        {

            segmentationResults = segmentUsingTrackMateModel(imp,
                                                        segmentationResults,
                                                        segmentationSettings,
                                                        channels,
                                                        frames);
        }
        else if ( segmentationSettings.method.equals(Globals.IMAGESUITE3D))
        {
            IJ.showMessage( segmentationSettings.method + " is not yet implemented." );
        }

        return segmentationResults;
    }

    private static SegmentationResults segmentUsingTrackMateDogDetector(ImagePlus imp,
                                                                  SegmentationResults segmentationResults,
                                                                  SegmentationSettings segmentationSettings,
                                                                  int[] channels,
                                                                  int[] frames)
    {

    }

    private static SegmentationResults segmentUsingTrackMateModel(ImagePlus imp,
                                                             SegmentationResults segmentationResults,
                                                             SegmentationSettings segmentationSettings,
                                                             int[] channels,
                                                             int[] frames)
    {

        // TODO: Loop over channels

        segmentationResults.models = new Model[channels.length];
        segmentationResults.channels = channels;


        for( int channel : channels) {

            Model model = new Model();
            segmentationResults.models[channel] = model;
            segmentationResults.segmentationMethod = "segmentUsingTrackMateModel";

            Settings settings = new Settings();

            if (segmentationSettings.method.equals(Globals.TRACKMATEDOG)) {
                settings.detectorFactory = new LogDetectorFactory<>();
            } else if (segmentationSettings.method.equals(Globals.IMAGESUITE3D)) {
                settings.detectorFactory = new DogDetectorFactory<>();
            }

            Roi roi = imp.getRoi();
            if (roi != null && roi.isArea()) {
                Point3D regionOffset = new Point3D(roi.getBounds().getX(), roi.getBounds().getY(), 0);
                Point3D regionSize = new Point3D(roi.getBounds().getWidth(), roi.getBounds().getHeight(), imp.getNSlices());
                settings.xstart = (int) regionOffset.getX();
                settings.xend = settings.xstart + (int) regionSize.getX() - 1;
                settings.ystart = (int) regionOffset.getX();
                settings.yend = settings.ystart + (int) regionSize.getY() - 1;
                settings.zstart = (int) regionOffset.getX();
                settings.zend = settings.zstart + (int) regionSize.getZ() - 1;
            } else {
                Point3D regionOffset = null;
                Point3D regionSize = null;
            }


            int channel = imp.getChannel();

            settings.setFrom(imp);
            settings.detectorSettings = settings.detectorFactory.getDefaultSettings();
            settings.detectorSettings.put(DetectorKeys.KEY_TARGET_CHANNEL, channel);
            settings.detectorSettings.put(DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION, true);
            settings.detectorSettings.put(DetectorKeys.KEY_RADIUS, segmentationSettings.trackMateSpotSize);
            settings.detectorSettings.put(DetectorKeys.KEY_THRESHOLD, segmentationSettings.trackMateSpotThreshold);

            // TODO: can one get rid of the tracker?
            settings.trackerFactory = new SparseLAPTrackerFactory();
            settings.trackerSettings = LAPUtils.getDefaultLAPSettingsMap();
            settings.trackerSettings.put(TrackerKeys.KEY_LINKING_MAX_DISTANCE, TrackerKeys.DEFAULT_LINKING_MAX_DISTANCE);
            settings.trackerSettings.put(TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE, TrackerKeys.DEFAULT_GAP_CLOSING_MAX_DISTANCE);
            settings.trackerSettings.put(TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP, TrackerKeys.DEFAULT_GAP_CLOSING_MAX_FRAME_GAP);


            // settings.detectorFactory.

            TrackMate trackmate = new TrackMate(model, settings);

            // Process (spot detection and tracking)
            if (!trackmate.checkInput()) {
                log("Configuration error: " + trackmate.getErrorMessage());
                //return segmentationResults;
            }
            if (!trackmate.execDetection()) {
                log("Detection error: " + trackmate.getErrorMessage());
                //return segmentationResults;
            }
            if (!trackmate.process()) {
                log("Processing error: " + trackmate.getErrorMessage());
                //return segmentationResults;
            }
            //
            // Store results in segmentationResults
            //
            log("Number of spots: " + model.getSpots().getNSpots(false));

        }

        return segmentationResults;

    }


}
