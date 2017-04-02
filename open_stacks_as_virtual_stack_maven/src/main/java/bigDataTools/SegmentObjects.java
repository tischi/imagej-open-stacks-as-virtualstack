package bigDataTools;

import fiji.plugin.trackmate.*;
import fiji.plugin.trackmate.detection.DetectorKeys;
import fiji.plugin.trackmate.detection.DogDetectorFactory;
import fiji.plugin.trackmate.tracking.LAPUtils;
import fiji.plugin.trackmate.tracking.TrackerKeys;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPTrackerFactory;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;

import static ij.IJ.log;

public class SegmentObjects {


    public static SegmentationResults run(ImagePlus imp,
                                          SegmentationResults segmentationResults,
                                          SegmentationSettings segmentationSettings)
    {

        if( segmentationSettings.method.equals(Utils.TRACKMATEDOG)
                || segmentationSettings.method.equals(Utils.TRACKMATEDOGSUBPIXEL) )
        {

            segmentationResults = segmentUsingTrackMate(imp,
                    segmentationResults,
                    segmentationSettings);
        }
        else if ( segmentationSettings.method.equals(Utils.IMAGESUITE3D))
        {
            IJ.showMessage( segmentationSettings.method + " is not yet implemented." );
        }

        return segmentationResults;
    }


    private static SegmentationResults segmentUsingTrackMate(ImagePlus imp,
                                                             SegmentationResults segmentationResults,
                                                             SegmentationSettings segmentationSettings)
    {

        // Remember the actual calibration, because we'll temporarily remove/change it
        //
        Calibration calibrationOrig = imp.getCalibration();

        // Prepare results storage
        //
        segmentationResults.channels = segmentationSettings.channels;
        segmentationResults.models = new Model[segmentationResults.channels.length];

        for( int iChannel=0; iChannel < segmentationResults.channels.length; iChannel++ )
        {

            // Change image calibration to trick TrackMate into anisotropic DoG filtering
            //
            Calibration calibrationTemp = new Calibration(imp);
            calibrationTemp.pixelWidth = segmentationSettings.spotRadii[iChannel][0];
            calibrationTemp.pixelHeight = segmentationSettings.spotRadii[iChannel][1];
            calibrationTemp.pixelDepth = segmentationSettings.spotRadii[iChannel][2];
            imp.setCalibration(calibrationTemp);

            // TrackMate model to hold the results
            Model model = new Model();
            model.setLogger(Logger.VOID_LOGGER);

            // TrackMate settings
            Settings settings = new Settings();
            settings.detectorFactory = new DogDetectorFactory<>();


            // Check if there was a ROI
            // Not necessary, seems to be done automatically by TrackMate
            /*
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
                imp.deleteRoi();
            }
            else
            {
                Point3D regionOffset = null;
                Point3D regionSize = null;
            }
            */


            // Go on with TrackMate settings
            //
            settings.setFrom(imp);
            settings.detectorSettings = settings.detectorFactory.getDefaultSettings();
            settings.detectorSettings.put(DetectorKeys.KEY_TARGET_CHANNEL, segmentationResults.channels[iChannel]); //one-based

            if( segmentationSettings.method.equals(Utils.TRACKMATEDOGSUBPIXEL))
            {
                settings.detectorSettings.put(DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION, true);
            }
            else if( segmentationSettings.method.equals(Utils.TRACKMATEDOG))
            {
                settings.detectorSettings.put(DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION, true);
            }

            settings.detectorSettings.put(DetectorKeys.KEY_RADIUS, 1.0); // because we rather calibrate the image accordingly
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


            // Convert spot features back to the original scaling of the image
            //
            SpotCollection spots = model.getSpots();
            for ( Spot spot : spots.iterable(false) )
            {
                spot.putFeature(spot.POSITION_X, (spot.getDoublePosition(0) / calibrationTemp.pixelWidth) * calibrationOrig.pixelWidth);
                spot.putFeature(spot.POSITION_Y, (spot.getDoublePosition(1) / calibrationTemp.pixelHeight) * calibrationOrig.pixelHeight);
                spot.putFeature(spot.POSITION_Z, (spot.getDoublePosition(2) / calibrationTemp.pixelDepth) * calibrationOrig.pixelDepth);
                spot.putFeature(spot.RADIUS, segmentationSettings.spotRadii[iChannel][0] * calibrationOrig.pixelWidth);
            }

            // Store results
            //
            segmentationResults.models[iChannel] = model;
        }

        // Put back the original calibration on the image
        //
        imp.setCalibration(calibrationOrig);

        return segmentationResults;

    }


}
