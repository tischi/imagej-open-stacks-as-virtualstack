package bigDataTools;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.visualization.DummySpotColorGenerator;
import fiji.plugin.trackmate.visualization.DummyTrackColorGenerator;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import fiji.plugin.trackmate.visualization.hyperstack.SpotOverlay;
import ij.ImagePlus;
import ij.gui.Overlay;
import org.jfree.chart.renderer.InterpolatePaintScale;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tischi on 25/03/17.
 */
public class SegmentationResults {
    public String segmentationMethod;
    public Model[] models; // the array is channel-wise
    public int[] channels; 
    public SpotCollection[] spotCollections; // the array is channel-wise

    // TODO: Loop over the models to find spots in multiple channels;
    // TODO: Overlay the spots
    // TODO: check how to convert a TrackMate Spot List to a SpotCollection: simply add spots from the list to the collection
    // TODO: find out how to create a cropped view with also a channel in imglib2
    // TODO: implement DoGDetector directly;

    // ArrayList<Individual>[] group = (ArrayList<Individual>[])new ArrayList[4];

    public void showOverlay(ImagePlus imp) {

        // depending on the specific segmentationMethod, call different methods for creating the overlay
        if( segmentationMethod.equals("segmentUsingTrackMateModel") )
        {
            showOverlayFromTrackMateModel(imp);
        }

    }


    public void showOverlayFromTrackMateModel(ImagePlus imp) {

    }


    public void showOverlayUsingTrackMateHyperStackDisplayer(ImagePlus imp)
    {
        SelectionModel selectionModel = new SelectionModel(model);
        HyperStackDisplayer hyperStackDisplayer =  new HyperStackDisplayer(model, selectionModel, imp);
        hyperStackDisplayer.render();
        hyperStackDisplayer.refresh();
    }

    public void showOverlayUsingTrackMateSpotOverlay(ImagePlus imp)
    {

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
