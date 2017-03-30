package bigDataTools;

import fiji.plugin.trackmate.Spot;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;



public class SpotsTable extends JPanel implements MouseListener, KeyListener {
    private boolean DEBUG = false;
    JTable table;
    JFrame frame;
    JScrollPane scrollPane;

    SegmentationOverlay segmentationOverlay = null;


    public SpotsTable()
    {
        super(new GridLayout(1, 0));
    }

    public void initializeTable(int[] channels)
    {

        List<String> columns = new ArrayList<>();
        columns.add("Region_X");
        columns.add("Region_Y");
        columns.add("Region_Z");

        for (int i=0; i<channels.length; i++ )
        {
            columns.add("Ch"+String.valueOf(channels[i])+"_X");
            columns.add("Ch"+String.valueOf(channels[i])+"_Y");
            columns.add("Ch"+String.valueOf(channels[i])+"_Z");
        }


        for (int i=0; i<channels.length-1; i++ )
        {
            for (int j=i+1; j<channels.length; j++ )
            {
                columns.add("Dist_Ch" + String.valueOf(channels[i]) + "_Ch" + String.valueOf(channels[i]));
            }
        }

        DefaultTableModel model = new DefaultTableModel(columns.toArray(new String[columns.size()]),0);
        table = new JTable(model);
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setPreferredScrollableViewportSize(new Dimension(500, 200));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(true);
        table.addMouseListener(this);
        table.addKeyListener(this);

        //Create the scroll pane and add the table to it.
        scrollPane = new JScrollPane(table);

        //Add the scroll pane to this panel.
        add(scrollPane);
    }

    public void setSegmentationOverlay(SegmentationOverlay segmentationOverlay)
    {
        this.segmentationOverlay =  segmentationOverlay;
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

    public void updateSegmentationOverlayBasedOnSelectedRow()
    {
        Globals.threadlog("SELECTED");

        if ( segmentationOverlay != null )
        {

            int selectedRow = table.getSelectedRow();
            int indexToModel = table.convertRowIndexToModel(selectedRow);
            float x = new Float(table.getModel().getValueAt(indexToModel, 0).toString());
            float y = new Float(table.getModel().getValueAt(indexToModel, 1).toString());
            float z = new Float(table.getModel().getValueAt(indexToModel, 2).toString());

            double radius = 1.0; // not used
            double quality = 1.0; // not used
            Spot location = new Spot(x,y,z,radius,quality);

            int frame = 0;
            segmentationOverlay.highlightClosestSpots(location, 3, frame);


        }

    }

    /*
    public void highlightSelectedTrack() {
        int rs = table.getSelectedRow();
        int r = table.convertRowIndexToModel(rs);
        float x = new Float(table.getModel().getValueAt(r, 1).toString());
        float y = new Float(table.getModel().getValueAt(r, 2).toString());
        float z = new Float(table.getModel().getValueAt(r, 3).toString());
        int t = new Integer(table.getModel().getValueAt(r, 4).toString());
        int id = new Integer(table.getModel().getValueAt(r, 5).toString());
        ImagePlus imp = tracks.get(id).getImp();
        imp.setPosition(0,(int)z+1,t+1);
        Roi pr = new PointRoi(x,y);
        pr.setPosition(0,(int)z+1,t+1);
        imp.setRoi(pr);
        //log(" rs="+rs+" r ="+r+" x="+x+" y="+y+" z="+z+" t="+t);
        //log("t="+table.getModel().getValueAt(r, 5));
    }*/

    public double computeJTableColumnAverage(JTable table, int columnIndex)
    {
        TableModel tableModel = table.getModel();
        int rowCount = tableModel.getRowCount();

        double sum = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++)
        {
            sum = sum + Double.parseDouble((String)tableModel.getValueAt(rowIndex, columnIndex));
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
        Globals.threadlog("# Average value of all columns");
        for ( int columnIndex = 0; columnIndex < columnNames.length; columnIndex++)
        {
            Globals.threadlog(""+columnNames[columnIndex]+": "+ columnAverages[columnIndex]);
        }
    }


    @Override
    public void mouseClicked(MouseEvent e) {
        updateSegmentationOverlayBasedOnSelectedRow();
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
        updateSegmentationOverlayBasedOnSelectedRow();
    }
}



