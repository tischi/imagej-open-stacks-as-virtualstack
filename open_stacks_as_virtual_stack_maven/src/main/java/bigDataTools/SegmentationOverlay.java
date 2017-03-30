package bigDataTools;

import fiji.plugin.trackmate.*;
import fiji.plugin.trackmate.features.ModelFeatureUpdater;
import fiji.plugin.trackmate.features.track.TrackIndexAnalyzer;
import fiji.plugin.trackmate.visualization.DummySpotColorGenerator;
import fiji.plugin.trackmate.visualization.DummyTrackColorGenerator;
import fiji.plugin.trackmate.visualization.SpotColorGenerator;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.LUT;
import org.jfree.chart.renderer.InterpolatePaintScale;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;


public class SegmentationOverlay {

    ImagePlus imp;
    SegmentationResults segmentationResults;
    SegmentationSettings segmentationSettings;

    // TrackMate specific
    public SelectionModel selectionModel;
    public Model modelOverlay;
    HyperStackDisplayer hyperStackDisplayer;

    public SegmentationOverlay(ImagePlus imp,
                                    SegmentationResults segmentationResults,
                                    SegmentationSettings segmentationSettings)

    {
        this.imp = imp;
        this.segmentationResults = segmentationResults;
        this.segmentationSettings = segmentationSettings;

    }


    public void highlightClosestSpots(Spot location, int n, int frame)
    {
        //Globals.logSpotCoordinates("Highlighting the " + n + " spots that are closests to:", location);
        SpotCollection spots = modelOverlay.getSpots();
        selectionModel.clearSpotSelection();
        java.util.List<Spot> closestSpots = spots.getNClosestSpots(location, frame, n, false);
        /*
        for ( Spot spot : closestSpots)
        {
            Globals.logSpotCoordinates("Spot:", spot);

        }
        */
        selectionModel.addSpotToSelection(spots.getNClosestSpots(location, frame, n, false));
        hyperStackDisplayer.centerViewOn(location);
        hyperStackDisplayer.refresh();

    }

    public void trackMateClearSpotSelection()
    {
        selectionModel.clearSpotSelection();
    }


    public void createHyperStackDisplayer()
    {
        // get the multi-channel TrackMate results
        Model[] models = segmentationResults.models;

        modelOverlay = new Model();
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
            Globals.threadlog("Channel: " + segmentationResults.channels[iChannel] + "; Number of spots: " +
                    spotCollection
                    .getNSpots(false));
            for ( Spot spot : spotCollection.iterable(false) )
            {
                spot.putFeature("COLOR", (double)segmentationResults.channels[iChannel]); // one-based
                modelOverlay.addSpotTo(spot, frame);
            }
        }
        modelOverlay.endUpdate();

        SpotCollection spotCollection = modelOverlay.getSpots();
        Globals.threadlog("Total number of spots: " + spotCollection.getNSpots(false));

        // Color the spots for each channel according to the channel LUT
        //
        SpotColorGenerator spotColorGenerator = new SpotColorGenerator(modelOverlay);
        spotColorGenerator.setFeature("COLOR");
        spotColorGenerator.setAutoMinMaxMode(false);
        spotColorGenerator.setMinMax(1.0, imp.getNChannels()); // one-based
        spotColorGenerator.activate();

        // currently my changes in the LUT for the spots are ignored by TrackMate...
        InterpolatePaintScale interpolatePaintScale = createInterpolatePaintScaleFromImpLUTs(imp);
        for (int iChannel = 0; iChannel < imp.getNChannels(); iChannel++) {
            Color color = interpolatePaintScale.getPaint( (double)iChannel+1);
            //Globals.threadlog(" "+ (iChannel+1) + ": " +color.toString());
        }

        // ...thus we change the LUTs of the image to fit the color of the spots
        double lower = spotColorGenerator.getMin();
        double upper = spotColorGenerator.getMax();
        for (int iChannel = 0; iChannel < imp.getNChannels(); iChannel++) {
            Color color = interpolatePaintScale.Jet.getPaint( ((iChannel+1.0)-lower) / (upper-lower));
            //Globals.threadlog(" " + (iChannel + 1) + ": " + color.toString());
            ((CompositeImage)imp).setChannelLut(createLUTFromColor(color), iChannel+1);
        }

        // Configure trackMate's visualization scheme
        //
        selectionModel = new SelectionModel(modelOverlay);
        //selectionModel.addSpotToSelection(spotCollection);
        hyperStackDisplayer = new HyperStackDisplayer(modelOverlay, selectionModel, imp);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_COLORMAP, interpolatePaintScale);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOT_COLORING, spotColorGenerator);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_TRACKS_VISIBLE, true);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOTS_VISIBLE, true);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOT_RADIUS_RATIO, 1.0);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_HIGHLIGHT_COLOR, Color.blue);
        hyperStackDisplayer.render();
        hyperStackDisplayer.refresh();

    }

    //
    public LUT createLUTFromColor(Color color)
    {
        byte[] red = new byte[256];
        byte[] green = new byte[256];
        byte[] blue = new byte[256];

        for (int i=0; i<256; i++)
        {
            red[i] = (byte)(color.getRed()*i/255.0);
            green[i] = (byte)(color.getGreen()*i/255.0);
            blue[i] = (byte)(color.getBlue()*i/255.0);
        }

        return new LUT(red, green, blue);

    }

    // Not useful yet, because TrackMate ignores the LUT for the SpotColoring
    //
    public InterpolatePaintScale createInterpolatePaintScaleFromImpLUTs(ImagePlus imp)
    {

        InterpolatePaintScale interpolatePaintScale = new InterpolatePaintScale(1.0, imp.getNChannels());

        // create overlay colors from the imp LUTs
        //
        for (int iChannel = 0; iChannel < imp.getNChannels(); iChannel++)
        {
            int r = imp.getLuts()[iChannel].getRed(255);
            int g = imp.getLuts()[iChannel].getGreen(255);
            int b = imp.getLuts()[iChannel].getBlue(255);

            Color color = new Color(r,g,b);
            /*
            Globals.threadlog("CHANNEL: " + (iChannel + 1));
            Globals.threadlog("Red: " + r);
            Globals.threadlog("Green: " + g);
            Globals.threadlog("Blue: " + b);
            */

            interpolatePaintScale.add(iChannel + 1, color); // one-based


        }


        return interpolatePaintScale;

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
