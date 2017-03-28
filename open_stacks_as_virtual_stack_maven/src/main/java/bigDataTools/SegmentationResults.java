package bigDataTools;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.visualization.DummySpotColorGenerator;
import fiji.plugin.trackmate.visualization.DummyTrackColorGenerator;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.ImagePlus;
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


}
