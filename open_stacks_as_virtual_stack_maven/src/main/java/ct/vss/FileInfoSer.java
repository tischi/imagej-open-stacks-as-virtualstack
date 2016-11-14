package ct.vss;

import ij.io.FileInfo;

import java.io.Serializable;

/**
 * Created by tischi on 13/11/16.
 */
public class FileInfoSer implements Cloneable, Serializable {

    // File formats
    public static final int UNKNOWN = 0;
    public static final int RAW = 1;
    public static final int TIFF = 2;
    public static final int GIF_OR_JPG = 3;
    public static final int FITS = 4;
    public static final int BMP = 5;
    public static final int DICOM = 6;
    public static final int ZIP_ARCHIVE = 7;
    public static final int PGM = 8;
    public static final int IMAGEIO = 9;

    // Compression modes
    public static final int COMPRESSION_UNKNOWN = 0;
    public static final int COMPRESSION_NONE= 1;
    public static final int LZW = 2;
    public static final int LZW_WITH_DIFFERENCING = 3;
    public static final int JPEG = 4;
    public static final int PACK_BITS = 5;
    public static final int ZIP = 6;

    /* File format (TIFF, GIF_OR_JPG, BMP, etc.). Used by the File/Revert command */
    public int fileFormat;

    /* File type (GRAY8, GRAY_16_UNSIGNED, RGB, etc.) */
    public int fileType;

    public String fileName;
    public String directory;
    public String url;
    public int width;
    public int height;
    public long offset;
    public boolean intelByteOrder;
    public int compression;
    public int[] stripOffsets;
    public int[] stripLengths;
    public int rowsPerStrip;

    public double pixelWidth=1.0;
    public double pixelHeight=1.0;
    public double pixelDepth=1.0;
    public double frameInterval;


    public FileInfoSer(FileInfo info) {
        this.fileName = info.fileName;
        this.directory = info.directory;
        this.url = info.url;
        this.width = info.width;
        this.height = info.height;
        this.offset= info.getOffset();
        this.intelByteOrder = info.intelByteOrder;
        this.compression = info.compression;
        this.stripOffsets = info.stripOffsets;
        this.stripLengths = info.stripLengths;
        this.rowsPerStrip = info.rowsPerStrip;
        this.pixelWidth = info.pixelWidth;
        this.pixelHeight = info.pixelHeight;
        this.pixelDepth = info.pixelDepth;
        this.frameInterval = info.frameInterval;

    }

}
