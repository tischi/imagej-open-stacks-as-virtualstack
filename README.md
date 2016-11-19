# imagej-open-stacks-as-virtualstack

## Functionality

Upon running the plugin constructs a virtual stack (data stream) from a folder with image stacks.
The folder structure needs to look like show below, the names of the folders do not matter:

- main folder _**<- this is the folder you have to select!**_
  - folder with channel 0 stacks _**<- you need one folder like this, even if you only have one channel!**_
    - xyz_stack_Timepoint01
    - xyz_stack_Timepoint02
    - ... 
  - folder with channel 1 stacks
    - xyz_stack_Timepoint01
    - xyz_stack_Timepoint02
    - ... 
  - ...

When you open the main folder the first time, the plugin will analyse all files. This can take a lot of time, especially when the folder contains tif stacks. Thus the plugin will save the results of this file analysis in a file, called ovs.ser:

- main folder
  - folder with channel 0 stacks
  - folder with channel 1 stacks
  - ...
  - **ovs.ser**
  

The second time you select the main folder it will only read the ovs.ser file, which is much faster than analyzing all the files again.
NOTE: If you changed something in your main folder you should remove the **ovs.ser** file such that the plugin will reanalyse your folder. 

## Tested file formats

This plugin only supports a few file formats. Currenty it should work for

- Luxendo h5 files
- Uncompressed tif saved with MATLAB
- LZW compressed tif saved with MATLAB

## Installation in Fiji

1. Download and unzip this repository:
https://github.com/tischi/imagej-open-stacks-as-virtualstack/archive/master.zip

2. Move **OpenStacksAsVirtualStack\_.jar** into your Fiji **plugins folder**.

## Usage instructions

1. In the Fiji GUI run: [Plugins > ALMF_EMBL > Open Stacks as Virtual Stack]
2. Select a folder having
