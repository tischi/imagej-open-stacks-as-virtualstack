import loci.common.*;
import loci.formats.tiff.*;

import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import java.io.IOException;
import ij.io.Opener;
import ij.io.FileInfo;




public class Cropped_Plane_From_TiffStack implements PlugIn {
	
	public String filename = "/Users/tischi/Desktop/example-data/T88200/T88200_new_S00_T0003.tif";
		
	public void run(String arg) {
		IJ.log("Cropped_Plane_From_TiffStack");
		IJ.register(Cropped_Plane_From_TiffStack.class);
		byte[] data = new byte[10];
		try {
			//RandomAccessInputStream in = new RandomAccessInputStream(filename);
			IJ.log("0.");
			RandomAccessInputStream in = new RandomAccessInputStream(data);
			IJ.log("A.");
			FileInfo[] info = new Opener().getTiffFileInfo(filename);
			if(info==null) 
			IJ.log("File not found");
			IJ.log("B");
			//ImagePlus imp = new OpenerExtensions2().Plane_From_TiffStack();
			//TiffParser tp = new TiffParser(filename);
			IJ.log("C");
			//IFDList idfs = tp.getIFDs();
			IJ.log("Done.");
		} catch(IOException ex) {
			IJ.log("IOException:");
			IJ.log(ex.toString());
		} catch(OutOfMemoryError e) {
			IJ.log("Memory error.");	
		}
		

	}

}

class OpenerExtensions2 extends Opener { 

        public ImagePlus Plane_From_TiffStack( )  {
	
	 	String filename = "/Users/tischi/Desktop/example-data/T88200/T88200_new_S00_T0003.tif";
		
		IJ.log("Cropped_Plane_From_TiffStack");
		IJ.register(Cropped_Plane_From_TiffStack.class);
		try {
			//RandomAccessInputStream in = new RandomAccessInputStream(filename);
			IJ.log("A.");
			FileInfo[] info = new Opener().getTiffFileInfo(filename);
			if(info==null) 
				IJ.log("File not found");
			IJ.log("B");
			TiffParser tp = new TiffParser(filename);
			IJ.log("C");
			//IFDList idfs = tp.getIFDs();
			IJ.log("Done.");
		} catch(IOException ex) {
			IJ.log("IOException:");
		//	IJ.log(ex.toString());
		} catch(OutOfMemoryError e) {
			IJ.log("Memory error.");	
		}
		
	return(null);
	}
}


