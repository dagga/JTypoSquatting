package com.aleph.graymatter.jtyposquatting.ui.renderers;

import javax.swing.*;
import java.awt.*;

/**
 * Custom icon for displaying status as LED indicators.
 */
public class LedIcon implements Icon {
    
    private final Color color;
    private final int size;
    
    public LedIcon(Color color, int size) {
        this.color = color;
        this.size = size;
    }
    
    @Override
    public int getIconWidth() {
        return size;
    }
    
    @Override
    public int getIconHeight() {
        return size;
    }
    
    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(0, 0, 0, 50));
        g2d.fillOval(x + 2, y + 2, size, size);
        g2d.setColor(color);
        g2d.fillOval(x, y, size, size);
        g2d.setColor(new Color(255, 255, 255, 100));
        g2d.fillOval(x + 2, y + 2, size / 2, size / 2);
        g2d.setColor(color.darker());
        g2d.drawOval(x, y, size, size);
        g2d.dispose();
    }
}
