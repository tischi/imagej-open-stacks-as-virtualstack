/* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as
* published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*/


package ct.vss;

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
