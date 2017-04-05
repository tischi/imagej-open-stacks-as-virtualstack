package bigDataTools;

import fiji.plugin.trackmate.*;
import fiji.plugin.trackmate.features.ModelFeatureUpdater;
import fiji.plugin.trackmate.features.track.TrackIndexAnalyzer;
import fiji.plugin.trackmate.visualization.DummySpotColorGenerator;
import fiji.plugin.trackmate.visualization.DummyTrackColorGenerator;
import fiji.plugin.trackmate.visualization.SpotColorGenerator;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.CompositeImage;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.process.LUT;
import ij.gui.Overlay;
import org.jfree.chart.renderer.InterpolatePaintScale;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;


// TODO:
// - only highlight the points of the active channels

public class SegmentationOverlay implements ImageListener {

    ImagePlus imp;
    SegmentationResults segmentationResults;
    SegmentationSettings segmentationSettings;
    boolean[] activeChannels;

    // TrackMate specific
    public SelectionModel selectionModel;
    public Model modelSelectedChannels;
    public Model modelAllChannels;
    HyperStackDisplayer hyperStackDisplayer;

    public SegmentationOverlay(ImagePlus imp,
                                    SegmentationResults segmentationResults,
                                    SegmentationSettings segmentationSettings)

    {
        this.imp = imp;
        this.segmentationResults = segmentationResults;
        this.segmentationSettings = segmentationSettings;

        activeChannels = new boolean[imp.getNChannels()];
        for ( int i = 0; i < activeChannels.length; i++ )
        {
            activeChannels[i] = true;
        }
        ImagePlus.addImageListener(this);

    }


    public void highlightNClosestSpotsForAllVisibleChannels(Spot location, int n, int frame)
    {

        selectionModel.clearSpotSelection();

        for (int iChannel = 0; iChannel < segmentationSettings.channels.length; iChannel++)
        {
            // add spots to overlay only if this channel is active
            //
            if (activeChannels[segmentationSettings.channels[iChannel] - 1])
            {
                Model model = segmentationResults.models[iChannel];
                selectionModel.addSpotToSelection(model.getSpots().getNClosestSpots(location, frame, n, false));
            }
        }

        //Utils.logSpotCoordinates("Highlighting the " + n + " spots that are closests to:", location);
        /*
        SpotCollection spots = modelAllChannels.getSpots();
        selectionModel.addSpotToSelection(spots.getNClosestSpots(location, frame, n, false));
        */

        location.putFeature("FRAME", (double) frame); // otherwise the "center view on method" crashes
        hyperStackDisplayer.centerViewOn(location);
        hyperStackDisplayer.refresh();
    }

    public void trackMateClearSpotSelection()
    {
        selectionModel.clearSpotSelection();
    }

    public void setTrackMateOverlayFromTable()
    {

        modelSelectedChannels = new Model();
        modelSelectedChannels.setLogger(Logger.IJ_LOGGER);
        Settings settings = new Settings();
        settings.addTrackAnalyzer(new TrackIndexAnalyzer());
        ModelFeatureUpdater modelFeatureUpdater = new ModelFeatureUpdater(modelSelectedChannels, settings);

        int frame = 0; // zero-based !!
        int channelColumn = 5;

        segmentationSettings.channelIDs = segmentationResults.jTableSpots.table.getModel().getValueAt(0, channelColumn).toString();
        segmentationSettings.channels =  Utils.delimitedStringToIntegerArray(segmentationSettings.channelIDs, ";");

        modelSelectedChannels.beginUpdate();
        for (int iChannel = 0; iChannel < segmentationSettings.channels.length; iChannel++)
        {
            // add spots to overlay only if this channel is active
            //
            if (activeChannels[segmentationSettings.channels[iChannel] - 1])
            {
                /*
                Spot spot = new Spot();
                spot.putFeature("COLOR", (double) segmentationSettings.channels[iChannel]); // one-based
                modelSelectedChannels.addSpotTo(spot, frame);
                */
            }
        }
        modelSelectedChannels.endUpdate();
    }

    public void setTrackMateModelOfAllChannelsFromSegmentationResults()
    {
        // get the multi-channel TrackMate results
        Model[] models = segmentationResults.models;

        modelAllChannels = new Model();
        modelAllChannels.setLogger(Logger.IJ_LOGGER);

        Settings settings = new Settings();
        settings.addTrackAnalyzer(new TrackIndexAnalyzer());
        ModelFeatureUpdater modelFeatureUpdater = new ModelFeatureUpdater(modelAllChannels, settings);

        int frame = 0; // zero-based !!

        modelAllChannels.beginUpdate();
        for (int iChannel = 0; iChannel < segmentationSettings.channels.length; iChannel++)
        {
            Model model = models[iChannel];
            SpotCollection spotCollection = model.getSpots();
            Utils.threadlog("Channel: " + segmentationSettings.channels[iChannel] + "; Number of spots: " +
                    spotCollection.getNSpots(false));
            for (Spot spot : spotCollection.iterable(false))
            {
                spot.putFeature("COLOR", (double) segmentationSettings.channels[iChannel]); // one-based
                modelAllChannels.addSpotTo(spot, frame);
            }
        }
        modelAllChannels.endUpdate();
    }

    public void setTrackMateModelOfSelectedChannelsFromSegmentationResults()
    {
        // get the multi-channel TrackMate results
        Model[] models = segmentationResults.models;

        modelSelectedChannels = new Model();
        modelSelectedChannels.setLogger(Logger.IJ_LOGGER);

        Settings settings = new Settings();
        settings.addTrackAnalyzer(new TrackIndexAnalyzer());
        ModelFeatureUpdater modelFeatureUpdater = new ModelFeatureUpdater(modelSelectedChannels, settings);

        int frame = 0; // zero-based !!

        modelSelectedChannels.beginUpdate();
        for (int iChannel = 0; iChannel < segmentationSettings.channels.length; iChannel++)
        {
            // add spots to overlay only if this channel is active
            //
            if (activeChannels[segmentationSettings.channels[iChannel] - 1])
            {
                Model model = models[iChannel];
                SpotCollection spotCollection = model.getSpots();
                for (Spot spot : spotCollection.iterable(false))
                {
                    spot.putFeature("COLOR", (double) segmentationSettings.channels[iChannel]); // one-based
                    modelSelectedChannels.addSpotTo(spot, frame);
                }
            }
        }
        modelSelectedChannels.endUpdate();
    }

    public void displayTrackMateOverlay()
    {

        SpotCollection spotCollection = modelSelectedChannels.getSpots();

        // Color the spots for each channel according to the channel LUT
        //
        SpotColorGenerator spotColorGenerator = new SpotColorGenerator(modelSelectedChannels);
        spotColorGenerator.setFeature("COLOR");
        spotColorGenerator.setAutoMinMaxMode(false);
        spotColorGenerator.setMinMax(1.0, imp.getNChannels()); // one-based
        spotColorGenerator.activate();

        // currently my changes in the LUT for the spots are ignored by TrackMate...
        InterpolatePaintScale interpolatePaintScale = createInterpolatePaintScaleFromImpLUTs(imp);
        for (int iChannel = 0; iChannel < imp.getNChannels(); iChannel++)
        {

            Color color = interpolatePaintScale.getPaint((double) iChannel + 1);
            //Utils.threadlog(" "+ (iChannel+1) + ": " +color.toString());
        }

        // ...thus we change the LUTs of the image to fit the color of the spots
        double lower = spotColorGenerator.getMin();
        double upper = spotColorGenerator.getMax();
        for (int iChannel = 0; iChannel < imp.getNChannels(); iChannel++) {
            Color color = interpolatePaintScale.Jet.getPaint( ((iChannel+1.0)-lower) / (upper-lower));
            //Utils.threadlog(" " + (iChannel + 1) + ": " + color.toString());
            ((CompositeImage)imp).setChannelLut(createLUTFromColor(color), iChannel+1);
        }

        // Configure trackMate's visualization scheme
        //
        selectionModel = new SelectionModel(modelSelectedChannels);
        //selectionModel.addSpotToSelection(spotCollection);
        hyperStackDisplayer = new HyperStackDisplayer(modelSelectedChannels, selectionModel, imp);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_COLORMAP, interpolatePaintScale);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOT_COLORING, spotColorGenerator);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_TRACKS_VISIBLE, false);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOTS_VISIBLE, true);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_SPOT_RADIUS_RATIO, 2.0);
        hyperStackDisplayer.setDisplaySettings(hyperStackDisplayer.KEY_HIGHLIGHT_COLOR, Color.blue);
        hyperStackDisplayer.render();
        hyperStackDisplayer.refresh();

    }

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
            Utils.threadlog("CHANNEL: " + (iChannel + 1));
            Utils.threadlog("Red: " + r);
            Utils.threadlog("Green: " + g);
            Utils.threadlog("Blue: " + b);
            */

            interpolatePaintScale.add(iChannel + 1, color); // one-based


        }


        return interpolatePaintScale;

    }

    public static void clearOverlay(ImagePlus imp)
    {
        Overlay overlay = imp.getOverlay();
        if(overlay != null) {
            overlay.clear();
        }

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


    @Override
    public void imageOpened(ImagePlus imagePlus)
    {

    }

    @Override
    public void imageClosed(ImagePlus imagePlus)
    {
        Utils.threadlog("closed");
        ImagePlus.removeImageListener(this);

    }

    @Override
    public void imageUpdated(ImagePlus imagePlus)
    {
        //Utils.threadlog("updated");

        if( imp == IJ.getImage() )
        {
            boolean updateView = false;
            boolean[] activeChannelsImp = ((CompositeImage) imp).getActiveChannels();
            for (int i = 0; i < activeChannels.length; i++)
            {
                //Utils.threadlog("Channel " + (i+1) + ":" + activeChannels[i]);
                if (activeChannelsImp[i] != activeChannels[i])
                {
                    updateView = true;
                    activeChannels[i] = activeChannelsImp[i];
                }
            }

            // update the view
            if( updateView )
            {
                this.setTrackMateModelOfSelectedChannelsFromSegmentationResults();
                this.displayTrackMateOverlay();
            }

        }

    }
}
