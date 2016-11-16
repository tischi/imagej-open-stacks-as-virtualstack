package ct.vss;

/**
 * Created by tischi on 16/11/16.
 */

// hdf5:  http://svnsis.ethz.ch/doc/hdf5/hdf5-14.12/

import ch.systemsx.cisd.hdf5.HDF5DataSetInformation;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.HDF5LinkInformation;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.base.mdarray.MDShortArray;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.IJ;

import java.util.List;

import static ij.IJ.log;

public class OpenHDF5test {

    public OpenHDF5test() {

    }

    public ImagePlus openOneFileAsImp(String path) {
        ImagePlus imp = null;
        String dataSet = "Data444";
        IHDF5Reader reader = HDF5Factory.openForReading(path);
        browse(reader, reader.object().getGroupMemberInformation("/", true), "");

        HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation("/"+dataSet);
        log("" + dsInfo.getRank());
        int nZ = (int)dsInfo.getDimensions()[0];
        int nY = (int)dsInfo.getDimensions()[1];
        int nX = (int)dsInfo.getDimensions()[2];
        log("nx,ny,nz :"+nX+","+nY+","+nZ+",");

        final MDShortArray block = reader.uint16().readMDArrayBlockWithOffset(dataSet, new int[] {1, 150, 150}, new long[] {(int)nZ/2,70,70} );
        final short[] asFlatArray = block.getAsFlatArray();

        imp = IJ.createHyperStack(dataSet, 150, 150, 1, 1, 1, 16);
        ImageProcessor ip = imp.getStack().getProcessor(imp.getStackIndex(1,1,1));
        System.arraycopy( asFlatArray, 0, (short[])ip.getPixels(),  0, asFlatArray.length);


        reader.close();
        imp.show();

        return(imp);
    }


    static void browse(IHDF5Reader reader, List<HDF5LinkInformation> members, String prefix)
    {
        for (HDF5LinkInformation info : members) {
            log(prefix + info.getPath() + ":" + info.getType());
            switch (info.getType())
            {
                case DATASET:
                    HDF5DataSetInformation dsInfo = reader.object().getDataSetInformation(info.getPath());
                    log(prefix + "     " + dsInfo);
                    break;
                case SOFT_LINK:
                    log(prefix + "     -> " + info.tryGetSymbolicLinkTarget());
                    break;
                case GROUP:
                    browse(reader, reader.object().getGroupMemberInformation(info.getPath(), true),
                            prefix + "  ");
                    break;
                default:
                    break;
            }
        }
    }

}
