package com.aleph.graymatter.jtyposquatting;

import com.aleph.graymatter.jtyposquatting.ui.JTypoFrame;
import javax.swing.*;

/**
 * Main entry point for the JTypoSquatting application.
 */
public class Main {
    
    /**
     * Main method to launch the application.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Launch the Swing application
        SwingUtilities.invokeLater(() -> {
            try {
                // Set system look and feel for better integration
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            JTypoFrame frame = new JTypoFrame();
            frame.setVisible(true);
        });
    }
}
