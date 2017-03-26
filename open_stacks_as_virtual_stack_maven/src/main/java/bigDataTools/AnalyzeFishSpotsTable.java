package bigDataTools;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;


public class AnalyzeFishSpotsTable extends JPanel implements MouseListener, KeyListener {
    private boolean DEBUG = false;
    JTable table;
    JFrame frame;
    JScrollPane scrollPane;

    public AnalyzeFishSpotsTable()
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




    @Override
    public void mouseClicked(MouseEvent e) {
        //highlightSelectedTrack();
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
        //highlightSelectedTrack();
    }
}



