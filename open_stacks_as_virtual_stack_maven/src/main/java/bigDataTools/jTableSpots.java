package bigDataTools;

import fiji.plugin.trackmate.Spot;
import ij.IJ;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class jTableSpots extends JPanel implements MouseListener, KeyListener {
    private boolean DEBUG = false;
    JTable table;
    JFrame frame;
    JScrollPane scrollPane;
    int REGION_ID;

    SegmentationOverlay segmentationOverlay;
    SegmentationSettings segmentationSettings;

    public jTableSpots()
    {
        super(new GridLayout(1, 0));
    }

    public void initializeTable()
    {

        int[] channels = segmentationSettings.channels;

        List<String> columns = new ArrayList<>();

        columns.add("Experimental_Batch");
        columns.add("Experiment_ID");
        columns.add("Treatment");
        columns.add("PathName_Image");
        columns.add("FileName_Image");
        columns.add("Channel_IDs");
        columns.add("Region_X"); REGION_ID = 6;
        columns.add("Region_Y");
        columns.add("Region_Z");


        for (int i=0; i<channels.length; i++ )
        {
            columns.add("Ch"+String.valueOf(channels[i])+"_DoG_X");
            columns.add("Ch"+String.valueOf(channels[i])+"_DoG_Y");
            columns.add("Ch"+String.valueOf(channels[i])+"_DoG_Z");
        }

        for (int i=0; i<channels.length; i++ )
        {
            columns.add("Ch"+String.valueOf(channels[i])+"_CoM_X");
            columns.add("Ch"+String.valueOf(channels[i])+"_CoM_Y");
            columns.add("Ch"+String.valueOf(channels[i])+"_CoM_Z");
        }

        for (int i=0; i<channels.length-1; i++ )
        {
            for (int j=i+1; j<channels.length; j++ )
            {
                columns.add("Dist_DoG_Ch" + String.valueOf(channels[i]) + "_Ch" + String.valueOf(channels[j]));
            }
        }

        for (int i=0; i<channels.length-1; i++ )
        {
            for (int j=i+1; j<channels.length; j++ )
            {
                columns.add("Dist_CoM_Ch" + String.valueOf(channels[i]) + "_Ch" + String.valueOf(channels[j]));
            }
        }


        DefaultTableModel model = new DefaultTableModel(columns.toArray(new String[columns.size()]),0);
        table = new JTable(model);
        setTableProperties();

    }

    public void setTableProperties()
    {
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(true);
        table.addMouseListener(this);
        table.addKeyListener(this);

        //Create the scroll pane and add the jTableSpots to it.
        scrollPane = new JScrollPane(table);

        //Add the scroll pane to this panel.
        add(scrollPane);
    }

    public void showTable()
    {
        //Create and set up the window.
        frame = new JFrame("Table");
        //frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        this.setOpaque(true); //content panes must be opaque
        frame.setContentPane(this);

        //Display the window.
        frame.pack();
        //frame.setLocation(trackingGUI.getFrame().getX() + trackingGUI.getFrame().getWidth(), trackingGUI.getFrame().getY());
        frame.setVisible(true);
    }

    public void saveTable(File file)
    {

        if(table.getModel() == null) {
            IJ.showMessage("There is not jTableSpots to be saved.");
            return;
        }

        try{
            TableModel model = table.getModel();
            FileWriter fileWriter = new FileWriter(file);

            for(int i = 0; i < model.getColumnCount(); i++){
                fileWriter.write(model.getColumnName(i) + "\t");
            }
            fileWriter.write("\n");

            for(int i=0; i< model.getRowCount(); i++) {
                for(int j=0; j < model.getColumnCount(); j++) {
                    fileWriter.write(model.getValueAt(i, j).toString() + "\t");
                }
                fileWriter.write("\n");
            }
            fileWriter.close();

        } catch(IOException e) { IJ.showMessage(e.toString()); }
    }

    public void loadTable(File file)
    {

        DefaultTableModel model = null;

        try
        {
            FileInputStream is = new FileInputStream(file);
            Scanner scan = new Scanner(is);
            String[] array;
            boolean firstLine = true;
            while (scan.hasNextLine())
            {
                String line = scan.nextLine();
                array = line.split("\t");
                Object[] data = new Object[array.length];
                for (int i = 0; i < array.length; i++)
                {
                    data[i] = array[i];
                }
                if (firstLine)
                {
                    model = new DefaultTableModel(data, 0);
                    table = new JTable(model);
                    setTableProperties();
                    firstLine = false;
                }
                else
                {
                    model.addRow(data);
                }
            }
        }
        catch(IOException ioe)
        {
            JOptionPane.showMessageDialog(this, ioe, "Error reading data", JOptionPane.ERROR_MESSAGE);
        }

        table.setModel(model);

    }

    public void addRow(final Object[] row)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                DefaultTableModel model = (DefaultTableModel) table.getModel();
                model.addRow(row);
            }
        });
    }

    public void clearTable()
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                DefaultTableModel model = (DefaultTableModel) table.getModel();
                model.setRowCount(0);
            }
        });
    }

    public void highlightBasedOnSelectedRow()
    {

        if ( segmentationOverlay != null )
        {

            int indexToModel = table.convertRowIndexToModel(table.getSelectedRow());
            float x = new Float(table.getModel().getValueAt(indexToModel, REGION_ID).toString());
            float y = new Float(table.getModel().getValueAt(indexToModel, REGION_ID+1).toString());
            float z = new Float(table.getModel().getValueAt(indexToModel, REGION_ID+2).toString());

            double radius = 1.0; // not used
            double quality = 1.0; // not used
            int frame = 0;
            Spot location = new Spot(x,y,z,radius,quality);


            segmentationOverlay.highlightClosestSpots(location, 3, frame);
            //ImagePlus imp = segmentationOverlay.imp;
            //imp.setZ( (int) Math.round(z / imp.getCalibration().pixelDepth) );

        }

    }

    /*
    public void highlightSelectedTrack() {
        int rs = jTableSpots.getSelectedRow();
        int r = jTableSpots.convertRowIndexToModel(rs);
        float x = new Float(jTableSpots.getModel().getValueAt(r, 1).toString());
        float y = new Float(jTableSpots.getModel().getValueAt(r, 2).toString());
        float z = new Float(jTableSpots.getModel().getValueAt(r, 3).toString());
        int t = new Integer(jTableSpots.getModel().getValueAt(r, 4).toString());
        int id = new Integer(jTableSpots.getModel().getValueAt(r, 5).toString());
        ImagePlus imp = tracks.get(id).getImp();
        imp.setPosition(0,(int)z+1,t+1);
        Roi pr = new PointRoi(x,y);
        pr.setPosition(0,(int)z+1,t+1);
        imp.setRoi(pr);
        //log(" rs="+rs+" r ="+r+" x="+x+" y="+y+" z="+z+" t="+t);
        //log("t="+jTableSpots.getModel().getValueAt(r, 5));
    }*/

    public double computeJTableColumnAverage(JTable table, int columnIndex)
    {
        TableModel tableModel = table.getModel();
        int rowCount = tableModel.getRowCount();

        double sum = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++)
        {
            try {
                sum = sum + Double.parseDouble((String)tableModel.getValueAt(rowIndex, columnIndex));
            } catch (NumberFormatException e) {
                // do nothing
            }

        }
        return (sum / rowCount);
    }


    public Double[] computeJTableColumnAverages(JTable table)
    {
        int columnCount = table.getModel().getColumnCount();

        Double[] columnAverages = new Double[columnCount];

        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            columnAverages[columnIndex] = computeJTableColumnAverage(table, columnIndex);
        }
        return columnAverages;
    }

    public String[] getColumnNames(JTable table)
    {
        int columnCount = table.getModel().getColumnCount();

        String[] columnNames = new String[columnCount];

        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++)
        {
            columnNames[columnIndex] = table.getColumnName(columnIndex);
        }
        return columnNames;

    }

    public void logColumnAverages()
    {
        Double[] columnAverages = computeJTableColumnAverages(table);
        String[] columnNames = getColumnNames(table);
        Utils.threadlog("# Average value of all columns");
        for ( int columnIndex = 0; columnIndex < columnNames.length; columnIndex++)
        {
            Utils.threadlog("" + columnNames[columnIndex] + ": " + columnAverages[columnIndex]);
        }
    }


    @Override
    public void mouseClicked(MouseEvent e) {
        highlightBasedOnSelectedRow();
    }

    @Override
    public void mousePressed(MouseEvent e) {

    }

    @Override
    public void mouseReleased(MouseEvent e) {

    }

    @Override
    public void mouseEntered(MouseEvent e) {

    }

    @Override
    public void mouseExited(MouseEvent e) {

    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

    }

    @Override
    public void keyReleased(KeyEvent e) {
        highlightBasedOnSelectedRow();
    }
}



