package bigDataTools;

import fiji.plugin.trackmate.*;
import fiji.plugin.trackmate.features.ModelFeatureUpdater;
import fiji.plugin.trackmate.features.track.TrackIndexAnalyzer;
import fiji.plugin.trackmate.visualization.DummySpotColorGenerator;
import fiji.plugin.trackmate.visualization.DummyTrackColorGenerator;
import fiji.plugin.trackmate.visualization.SpotColorGenerator;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.ImagePlus;
import org.jfree.chart.renderer.InterpolatePaintScale;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static ij.IJ.log;


public class SegmentationOverlay {

    ImagePlus imp;
    SegmentationResults segmentationResults;
    SegmentationSettings segmentationSettings;

    public SegmentationOverlay(ImagePlus imp,
                                    SegmentationResults segmentationResults,
                                    SegmentationSettings segmentationSettings)

    {
        this.imp = imp;
        this.segmentationResults = segmentationResults;
        this.segmentationSettings = segmentationSettings;

    }

    public void showOverlay()
    {
    }


    public void showOverlayUsingTrackMateHyperStackDisplayer()
    {
        // get the multi-channel TrackMate results
        Model[] models = segmentationResults.models;

        Model modelOverlay = new Model();
        modelOverlay.setLogger(Logger.IJ_LOGGER);
        Settings settings = new Settings();
        settings.addTrackAnalyzer(new TrackIndexAnalyzer());
        ModelFeatureUpdater modelFeatureUpdater = new ModelFeatureUpdater( modelOverlay, settings );

        int frame = 0; // zero-based !!

        modelOverlay.beginUpdate();
        for ( int iChannel = 0; iChannel < segmentationResults.channels.length; iChannel++)
        {
            Model model = models[iChannel];
            SpotCollection spotCollection = model.getSpots();
            //log("Channel: "+segmentationResults.channels[iChannel]+"; Number of spots: "+spotCollection.getNSpots(false));
            for ( Spot spot : spotCollection.iterable(false) )
            {

                spot.putFeature("COLOR", (double) iChannel+1);
                modelOverlay.addSpotTo(spot, frame);

            }
        }
        modelOverlay.endUpdate();


        SpotCollection spotCollection = modelOverlay.getSpots();
        log("Total number of spots: " + spotCollection.getNSpots(false));


        SpotColorGenerator spotColorGenerator = new SpotColorGenerator(modelOverlay);
        spotColorGenerator.setFeature("COLOR");
        spotColorGenerator.autoMinMax();

        log("min: " + spotColorGenerator.getMin());
        log("max: " + spotColorGenerator.getMax());
        spotColorGenerator.activate();


        SelectionModel selectionModel = new SelectionModel(modelOverlay);
        //selectionModel.addSpotToSelection(spotCollection);
        HyperStackDisplayer hyperStackDisplayer = new HyperStackDisplayer(modelOverlay, selectionModel, imp);

        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOT_COLORING, spotColorGenerator);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_TRACKS_VISIBLE, false);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOTS_VISIBLE, true);
        hyperStackDisplayer.render();
        hyperStackDisplayer.refresh();


        // check all the spot features
        /*
        for ( Spot spot : spotCollection.iterable(false) )
        {
            Globals.printMap(spot.getFeatures());
        }*/

        //	centerViewOn(Spot spot)

        //}

    }

    public void showOverlayUsingTrackMateSpotOverlay(ImagePlus imp)
    {
        /*
        Overlay overlay = imp.getOverlay();
        if(overlay == null) {
            overlay = new Overlay();
            imp.setOverlay(overlay);
        }
        overlay.clear();

        Map<String, Object> displaySettings = initDisplaySettings(model);
        displaySettings.put("TracksVisible", Boolean.valueOf(false));


        displaySettings.put("SpotRadiusRatio", Double.valueOf(5.0D));
        SpotOverlay spotOverlay = new SpotOverlay(model, imp, displaySettings);
        imp.getOverlay().add(spotOverlay);
        imp.updateAndDraw();

        displaySettings.put("SpotRadiusRatio", Double.valueOf(1.0D));
        SpotOverlay spotOverlay2 = new SpotOverlay(model, imp, displaySettings);
        imp.getOverlay().add(spotOverlay2);
        imp.updateAndDraw();
        */

        /*
        displaySettings.put("Color", DEFAULT_SPOT_COLOR);
        displaySettings.put("HighlightColor", DEFAULT_HIGHLIGHT_COLOR);
        displaySettings.put("SpotsVisible", Boolean.valueOf(true));
        displaySettings.put("DisplaySpotNames", Boolean.valueOf(false));
        displaySettings.put("SpotColoring", new DummySpotColorGenerator());
        displaySettings.put("SpotRadiusRatio", Double.valueOf(1.0D));
        displaySettings.put("TracksVisible", Boolean.valueOf(true));
        displaySettings.put("TrackDisplaymode", Integer.valueOf(0));
        displaySettings.put("TrackDisplayDepth", Integer.valueOf(10));
        displaySettings.put("TrackColoring", new DummyTrackColorGenerator());
        displaySettings.put("ColorMap", DEFAULT_COLOR_MAP);
        displaySettings.put("LimitDrawingDepth", Boolean.valueOf(false));
        displaySettings.put("DrawingDepth", Double.valueOf(10.0D));
        */

    }

    protected Map<String, Object> initDisplaySettings(Model model)
    {
        HashMap displaySettings = new HashMap(11);
        displaySettings.put("Color", Color.green);
        displaySettings.put("HighlightColor", Color.cyan);
        displaySettings.put("SpotsVisible", Boolean.valueOf(true));
        displaySettings.put("DisplaySpotNames", Boolean.valueOf(false));
        displaySettings.put("SpotColoring", new DummySpotColorGenerator());
        displaySettings.put("SpotRadiusRatio", Double.valueOf(1.0D));
        displaySettings.put("TracksVisible", Boolean.valueOf(true));
        displaySettings.put("TrackDisplaymode", Integer.valueOf(0));
        displaySettings.put("TrackDisplayDepth", Integer.valueOf(10));
        displaySettings.put("TrackColoring", new DummyTrackColorGenerator());
        displaySettings.put("ColorMap", InterpolatePaintScale.Jet);
        displaySettings.put("LimitDrawingDepth", Boolean.valueOf(false));
        displaySettings.put("DrawingDepth", Double.valueOf(10.0D));
        return displaySettings;
    }


}
