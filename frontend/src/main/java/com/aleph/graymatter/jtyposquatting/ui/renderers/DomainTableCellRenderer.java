package com.aleph.graymatter.jtyposquatting.ui.renderers;

import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Custom cell renderer for domain table cells.
 */
public class DomainTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        
        if (c instanceof JLabel) {
            JLabel label = (JLabel) c;
            label.setHorizontalAlignment(JLabel.CENTER);
            label.setOpaque(true);
        }
        
        // Get domain data to determine cell styling
        DomainResultDTO domainData = null;
        if (row < table.getModel().getRowCount()) {
            // Try to get domain data from the model
            Object domainObj = table.getModel().getValueAt(row, 0);
            if (domainObj != null) {
                // This is a simplified approach - in a real implementation, you would
                // have a reference to the domain data for each row
            }
        }
        
        if (isSelected) {
            c.setBackground(table.getSelectionBackground());
            c.setForeground(table.getSelectionForeground());
        } else {
            c.setBackground(Color.WHITE);
            c.setForeground(Color.BLACK);
        }
        
        return c;
    }
}
