package com.aleph.graymatter.jtyposquatting.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Panel for displaying log files with tailing functionality
 */
public class LogPanel extends JPanel {
    private final JTextArea textArea;
    private final JCheckBox followCheck;
    private final LogTailer logTailer;

    public LogPanel(String logFileName, String logDisplayName) {
        setLayout(new BorderLayout());

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setRows(30);
        JScrollPane scroll = new JScrollPane(textArea);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        followCheck = new JCheckBox("Follow", true);
        top.add(new JLabel(logDisplayName));
        top.add(followCheck);

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        logTailer = new LogTailer(logFileName, textArea, followCheck);
    }

    public void start() {
        logTailer.start();
    }

    public void stop() {
        logTailer.stop();
    }

    public void clearLogs() {
        logTailer.stop();
        logTailer.clear();
        SwingUtilities.invokeLater(() -> textArea.setText(""));
    }
    
    public void restartLogs() {
        logTailer.stop();
        logTailer.clear();
        logTailer.start();
    }
}