# imagej-open-stacks-as-virtualstack

## Functionality

This plugin constructs a virtual stack (data stream) from a folder of image stacks. The folder structure currently must consist of one main folder containing subfolders for each channel, where each of the channel subfolders contains the image stacks representing the time-points. The names of the folders and files are arbitrary, they are only used the sort the channels and time-points (the sorted file lists will be printed in the IJ log window; please check that the order is correct!).

- main_folder _**<- this is the folder you have to select!**_
  - folder_with_channel_0_stacks _**<- you need one folder like this, even if you only have one channel!**_
    - xyz_stack_timepoint01
    - xyz_stack_timepoint02
    - ... 
  - folder_with_channel_1_stacks
    - xyz_stack_timepoint01
    - xyz_stack_timepoint02
    - ... 
  - ...

When you open the main folder the first time, the plugin will analyse all files. This can take a lot of time, especially when the folder contains tif stacks. Thus the plugin will save the results of this file analysis in a file, called ovs.ser:

- main_folder
  - folder_with_channel_0_stacks
  - folder_with_channel_1_stacks
  - ...
  - **ovs.ser**
  

The second time you select the main folder it will only read the ovs.ser file, which is much faster than analyzing all the files again.

**IMPORTANT NOTE**: If you changed something in your main folder, e.g. added or renamed files or folders you must remove the **ovs.ser** file such that the plugin will reanalyse your folder. 

## Tested file formats

This plugin only supports a few file formats. Currenty it should work for

- Luxendo h5 files (opening "/Data111")
- Uncompressed tif files saved with MATLAB
- LZW compressed tif files saved with MATLAB

## Installation in Fiji

1. Download the jar: https://github.com/tischi/imagej-open-stacks-as-virtualstack/raw/master/OpenStacksAsVirtualStack_.jar

2. Move **OpenStacksAsVirtualStack\_.jar** into your Fiji **plugins folder**.

## Installation in ImageJ

You also need the hdf5 reader jar....

## Usage instructions

1. In the Fiji GUI run: [Plugins > ALMF_EMBL > Open Stacks as Virtual Stack]
2. Select the main_folder containing your data

Now you can browse your data.

## Additional functionality

### [Crop]

The crop button will crop you data in xy; you need to place a rectangular ROI on your image for this to work. The cropping gives you only a different "view" on the data; no data will be duplicated as it is still streamed from the orginal data; the main benefits of the cropping are:
- it will load less data and thus be faster  
