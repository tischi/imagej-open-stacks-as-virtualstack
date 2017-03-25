package bigDataTools;

import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.DogDetectorFactory;
import fiji.plugin.trackmate.detection.LogDetectorFactory;
import fiji.plugin.trackmate.tracking.LAPUtils;
import fiji.plugin.trackmate.tracking.TrackerKeys;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPTrackerFactory;
import fiji.plugin.trackmate.Model;

import ij.IJ;
import ij.ImagePlus;
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
                                          int[] frames,
                                          Point3D regionOffset,
                                          Point3D regionSize)
    {

        if( segmentationSettings.method.equals(Globals.TRACKMATEDOG) )
        {

            segmentationResults = segmentUsingTrackMate(imp,
                                                        segmentationResults,
                                                        segmentationSettings,
                                                        channels,
                                                        frames,
                                                        regionOffset,
                                                        regionSize);
        }
        else if ( segmentationSettings.method.equals(Globals.IMAGESUITE3D))
        {
            IJ.showMessage( segmentationSettings.method + " is not yet implemented." );
        }

        return segmentationResults;
    }


    private static SegmentationResults segmentUsingTrackMate(ImagePlus imp,
                                                             SegmentationResults segmentationResults,
                                                             SegmentationSettings segmentationSettings,
                                                             int[] channels,
                                                             int[] frames,
                                                             Point3D regionOffset,
                                                             Point3D regionSize)
    {

        Model model = new Model();

        Settings settings = new Settings();

        settings.setFrom(imp);

        if( segmentationSettings.method.equals(Globals.TRACKMATEDOG) )
        {
            settings.detectorFactory = new LogDetectorFactory<>();
        }
        else if (segmentationSettings.method.equals(Globals.IMAGESUITE3D) )
        {
            settings.detectorFactory = new DogDetectorFactory<>();
        }

        settings.detectorSettings = settings.detectorFactory.getDefaultSettings();
        settings.detectorSettings.put(DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION, true);
        settings.detectorSettings.put(DetectorKeys.KEY_RADIUS, segmentationSettings.trackMateSpotSize);
        settings.detectorSettings.put(DetectorKeys.KEY_THRESHOLD, segmentationSettings.trackMateSpotThreshold);

        // TODO: Are the tracking settings necessary?
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
            return null;
        }
        if (!trackmate.process()) {
            log("Processing error: " + trackmate.getErrorMessage());
            return null;
        }

        //
        // Store results in segmentationResults
        //
        log("Number of spots: " + model.getSpots().getNSpots(false));
        segmentationResults.trackMateModel = model;

        return segmentationResults;
    }


}
