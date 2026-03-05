package com.aleph.graymatter.jtyposquatting.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;

/**
 * Panel for displaying page preview thumbnail
 */
public class PreviewPanel extends JPanel {
    private final JLabel thumbnailLabel;

    public PreviewPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Page Preview"));

        thumbnailLabel = new JLabel("No preview available", SwingConstants.CENTER);
        thumbnailLabel.setPreferredSize(new Dimension(320, 240));
        thumbnailLabel.setBackground(Color.BLACK);
        thumbnailLabel.setOpaque(true);
        add(thumbnailLabel, BorderLayout.CENTER);
    }

    public void updatePreview(String domain, byte[] screenshot) {
        if (screenshot != null && screenshot.length > 0) {
            try {
                // Try to load image from byte array using ImageIO
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(screenshot));
                
                if (img != null) {
                    // Scale the image
                    Image scaledImg = img.getScaledInstance(320, 240, Image.SCALE_SMOOTH);
                    ImageIcon scaledIcon = new ImageIcon(scaledImg);

                    SwingUtilities.invokeLater(() -> {
                        thumbnailLabel.setIcon(scaledIcon);
                        thumbnailLabel.setText("");
                        thumbnailLabel.setBackground(null);
                        thumbnailLabel.repaint();
                    });
                    return;
                }
            } catch (Exception e) {
                System.err.println("[PreviewPanel] Error loading screenshot: " + e.getMessage());
            }
        }

        SwingUtilities.invokeLater(() -> {
            thumbnailLabel.setText("No preview available");
            thumbnailLabel.setIcon(null);
            thumbnailLabel.setBackground(Color.BLACK);
        });
    }

    public void clearPreview() {
        SwingUtilities.invokeLater(() -> {
            thumbnailLabel.setText("No preview available");
            thumbnailLabel.setIcon(null);
            thumbnailLabel.setBackground(Color.BLACK);
        });
    }
}