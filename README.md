# imagej-open-stacks-as-virtualstack

## Functionality

This ImageJ plugin suite constructs a virtual stack (data stream) from a folder of image stacks. This is useful for large (~1TB) data sets as typically acquired with light-sheet microscopy. In addition to viewing the data it also allows you to track objects in the data set in an efficient and fast manner; it is efficient, because it only loads the data around the tracked objects and it is fast, because the code is optimised for speed and uses multi-threading. 

## Plugin help

Below pages summarise the information that you get by hovering with the mouse over the respective button in each plugin.

[Data Streaming Tools](http://htmlpreview.github.com/?https://github.com/tischi/imagej-open-stacks-as-virtualstack/blob/master/open_stacks_as_virtual_stack_maven/src/main/resources/DataStreamingHelp.html)

[Track and Crop](http://htmlpreview.github.com/?https://github.com/tischi/imagej-open-stacks-as-virtualstack/blob/master/open_stacks_as_virtual_stack_maven/src/main/resources/TrackAndCropHelp.html)

## Usage notes

The folder structure of your input data currently must consist of one main folder containing subfolders for each channel, where each of the channel subfolders contains the image stacks representing the time-points. The names of the folders and files are arbitrary, they are only used to sort the channels and time-points (the sorted file lists will be printed in the IJ log window; please check that the order is correct!).

- main_folder **<- this is the folder you have to select**
  - channel_0_folder **<- you need one folder like this, even if you only have one channel!**
    - xyz_stack_timepoint01
    - xyz_stack_timepoint02
    - ... 
  - channel_1_folder
    - xyz_stack_timepoint01
    - xyz_stack_timepoint02
    - ... 
  - ...

When you stream from the main folder the first time, the plugin will analyse all files. This can take some time, especially when the image data is saved as tif stacks.

## Supported file formats

This plugin currently only supports a few file formats; it was tested for

- Luxendo h5 files (opening "/Data111")
- Uncompressed tif files (saved with MATLAB)
- LZW compressed tif files (saved with MATLAB)
- ZIP compressed tif files (EMBL iSPIM data)

## Installation in Fiji

1. Download the jar: https://github.com/tischi/imagej-open-stacks-as-virtualstack/raw/master/OpenStacksAsVirtualStack_.jar
2. Move **OpenStacksAsVirtualStack\_.jar** into your **Fiji.app/plugins**.

## Installation in ImageJ

1. Download https://github.com/tischi/imagej-open-stacks-as-virtualstack/raw/master/OpenStacksAsVirtualStack_.jar
2. Download https://github.com/tischi/imagej-open-stacks-as-virtualstack/raw/master/jhdf5-14.12.0.jar
3. Move both jars into your **ImageJ/plugins/jars** folder

## Usage instructions

### Data streaming

1. Run: **[Plugins > ALMF SPIM > Data Streaming Tools]**
2. **[Stream from folder]**: Streams the image data from the folder into IJ's hyperstack viewer  
3. [Crop as new stream]: ...

### Tracking

1. Run: **[Plugins > ALMF SPIM > Big Data Tracker]**
2. ... 
