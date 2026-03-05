package com.aleph.graymatter.jtyposquatting.ui.renderers;

import javax.swing.*;
import java.awt.*;

/**
 * Custom renderer for displaying status as LED indicators.
 */
public class StatusLedRenderer extends JLabel implements javax.swing.table.TableCellRenderer {
    
    private static final int LED_SIZE = 20;
    
    public StatusLedRenderer() {
        setHorizontalAlignment(JLabel.CENTER);
        setOpaque(true);
    }
    
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        String status = (String) value;
        Color ledColor;
        Color bgColor;

        switch (status) {
            case "Suspicious" -> {
                ledColor = new Color(255, 80, 80);
                bgColor = new Color(255, 220, 220);
            }
            case "Safe" -> {
                ledColor = new Color(100, 200, 100);
                bgColor = new Color(220, 255, 220);
            }
            case "Checking", "Testing..." -> {
                ledColor = new Color(255, 165, 0);
                bgColor = new Color(255, 240, 200);
            }
            case null, default -> {
                ledColor = new Color(200, 200, 200);
                bgColor = Color.WHITE;
            }
        }
        
        setOpaque(true);
        setBackground(isSelected ? table.getSelectionBackground() : bgColor);
        setIcon(new LedIcon(ledColor, LED_SIZE));
        
        // Get HTTP code from model (column 6)
        int modelRow = table.convertRowIndexToModel(row);
        Object httpCodeObj = table.getModel().getValueAt(modelRow, 6);
        String httpCodeStr = "";
        if (httpCodeObj != null) {
            int httpCode = Integer.parseInt(httpCodeObj.toString());
            // Show HTTP code only for positive values
            // Keep empty for httpCode == -1 (testing) and httpCode == 0 (unreachable/dead)
            if (httpCode > 0) {
                httpCodeStr = String.valueOf(httpCode);
            }
        }
        setText(httpCodeStr);
        
        return this;
    }
}
