package ct.vss;

import ij.io.FileInfo;
import java.io.Serializable;

/**
 * Created by tischi on 13/11/16.
 */
public class FileInfoSer implements Cloneable, Serializable {

    /* File format (TIFF, GIF_OR_JPG, BMP, etc.). Used by the File/Revert command */
    public int fileFormat;

    /* File type (GRAY8, GRAY_16_UNSIGNED, RGB, etc.) */
    public int fileType;

    public String fileName;
    public String directory;
    public String url;
    public int width;
    public int height;
    public long longOffset;
    public boolean intelByteOrder;
    public int compression;
    public int[] stripOffsets;
    public int[] stripLengths;
    public int rowsPerStrip;

    public double pixelWidth=1.0;
    public double pixelHeight=1.0;
    public double pixelDepth=1.0;
    public double frameInterval;

    // own stuff
    public int bytesPerPixel;
    public String h5DataSet;
    public String fileTypeString;

    public FileInfoSer() {

    }

    public FileInfoSer(FileInfo info) {
        this.fileFormat = info.fileFormat;
        this.fileName = info.fileName;
        this.directory = info.directory;
        this.fileType = info.fileType;
        this.url = info.url;
        this.width = info.width;
        this.height = info.height;
        this.longOffset= info.getOffset();
        this.intelByteOrder = info.intelByteOrder;
        this.compression = info.compression;
        this.stripOffsets = info.stripOffsets;
        this.stripLengths = info.stripLengths;
        this.rowsPerStrip = info.rowsPerStrip;
        this.pixelWidth = info.pixelWidth;
        this.pixelHeight = info.pixelHeight;
        this.pixelDepth = info.pixelDepth;
        this.frameInterval = info.frameInterval;
        this.bytesPerPixel = info.getBytesPerPixel();

    }

    public FileInfo getFileInfo() {
        FileInfo fi = new FileInfo();
        fi.fileName = this.fileName;
        fi.fileFormat = this.fileFormat;
        fi.fileType = this.fileType;
        fi.directory = this.directory;
        fi.url = this.url;
        fi.width = this.width;
        fi.height = this.height;
        fi.longOffset = this.longOffset;
        fi.intelByteOrder = this.intelByteOrder;
        fi.compression = this.compression;
        fi.stripOffsets = this.stripOffsets;
        fi.stripLengths = this.stripLengths;
        fi.rowsPerStrip = this.rowsPerStrip;
        fi.pixelWidth = this.pixelWidth;
        fi.pixelHeight = this.pixelHeight;
        fi.pixelDepth = this.pixelDepth;
        fi.frameInterval = this.frameInterval;

        return(fi);
    }

}
