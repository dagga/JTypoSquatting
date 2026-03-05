package com.aleph.graymatter.jtyposquatting.ui;

import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.List;

/**
 * Dialog for displaying domain details with HTML preview and metadata
 */
public class DomainDetailsDialog extends JDialog {
    private final JEditorPane editorPane;
    private final JTextArea metaArea;
    private final java.util.concurrent.atomic.AtomicBoolean disposed = new java.util.concurrent.atomic.AtomicBoolean(false);

    public DomainDetailsDialog(Frame parent, DomainResultDTO data) {
        super(parent, "Domain Details: " + data.getDomain(), false);
        setLayout(new BorderLayout(8, 8));
        setSize(800, 600);
        setLocationRelativeTo(parent);

        // Header
        JPanel header = getJPanel(data);
        add(header, BorderLayout.NORTH);

        // Editor pane for preview (plain text only to avoid heavy HTML parsing)
        editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/plain");
        JScrollPane editorScroll = new JScrollPane(editorPane);

        // Meta information area
        metaArea = new JTextArea();
        metaArea.setEditable(false);
        metaArea.setLineWrap(true);
        metaArea.setWrapStyleWord(true);
        JScrollPane metaScroll = new JScrollPane(metaArea);
        metaScroll.setPreferredSize(new Dimension(320, 200));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorScroll, metaScroll);
        split.setResizeWeight(0.7);
        add(split, BorderLayout.CENTER);

        // Buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton openBtn = new JButton("Open in Browser");
        JButton copyHtmlBtn = new JButton("Copy Text");
        JButton closeBtn = new JButton("Close");

        btns.add(openBtn);
        btns.add(copyHtmlBtn);
        btns.add(closeBtn);
        add(btns, BorderLayout.SOUTH);

        // Load initial data if present
        if (data != null) {
            populateFromData(data);
        } else {
            editorPane.setText("No details available.");
            metaArea.setText("No metadata available.");
        }

        openBtn.addActionListener(e -> {
            try {
                String urlStr = data.getDomain().startsWith("http") ? data.getDomain() : "https://" + data.getDomain();
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(urlStr));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to open browser: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        copyHtmlBtn.addActionListener(e -> {
            // Now copies the homepageText
            String textToCopy = editorPane.getText();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textToCopy), null);
            JOptionPane.showMessageDialog(this, "Homepage text copied to clipboard", "Success", JOptionPane.INFORMATION_MESSAGE);
        });

        closeBtn.addActionListener(e -> dispose());

        // Add window listener to properly decrement count when closed via X button
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                cleanup();
            }
        });
    }

    private static @NonNull JPanel getJPanel(DomainResultDTO data) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // Status LED
        Color ledColor = switch (data.getStatus()) {
            case "Suspicious" -> new Color(255, 80, 80); // Red
            case "Safe" -> new Color(100, 200, 100); // Green
            case "Checking", "Testing..." -> new Color(255, 165, 0); // Orange
            case null, default -> new Color(200, 200, 200); // Grey
        };

        JLabel ledLabel = new JLabel(new LedIcon(ledColor, 20));
        header.add(ledLabel);

        JLabel lbl = new JLabel("Details for " + data.getDomain() + "    [" + data.getStatus() + "]");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        header.add(lbl);
        return header;
    }

    @Override
    public void dispose() {
        cleanup();
        super.dispose();
    }

    private void cleanup() {
        // Ensure cleanup only happens once
        if (disposed.compareAndSet(false, true)) {
            // decrement open dialog count
            if (getParent() instanceof JTypoFrame) {
                ((JTypoFrame) getParent()).decrementOpenDialogCount();
            }
        }
    }

    private void populateFromData(DomainResultDTO data) {
        if (data == null) return;
        
        StringBuilder previewText = new StringBuilder();
        previewText.append("Domain: ").append(defaultString(data.getDomain())).append('\n');
        previewText.append("Title: ").append(defaultString(data.getTitle())).append('\n').append('\n');
        
        // Display homepageText (first 200 chars)
        if (data.getHomepageText() != null && !data.getHomepageText().trim().isEmpty()) {
            previewText.append(data.getHomepageText());
        } else {
            previewText.append("No homepage text preview available.");
        }
        editorPane.setText(previewText.toString());

        StringBuilder meta = new StringBuilder();
        meta.append("=== Domain Information ===\n\n");
        meta.append("Domain: ").append(data.getDomain()).append('\n');

        if (data.getTitle() != null && !data.getTitle().trim().isEmpty()) {
            meta.append("Title: ").append(data.getTitle()).append('\n');
        }

        if (data.getLanguage() != null && !data.getLanguage().trim().isEmpty()) {
            meta.append("Language: ").append(data.getLanguage()).append('\n');
        }

        meta.append('\n');

        // Meta tags section
        if (hasValue(data.getDescription())) {
            meta.append("=== Meta Tags ===\n\n");
            meta.append("Description: ").append(data.getDescription()).append('\n');
            meta.append('\n');
        }

        // HTTP Response section
        if (data.getHttpCode() > 0 || (data.getHttpHeaders() != null && !data.getHttpHeaders().isEmpty())) {
            meta.append("=== HTTP Response ===\n\n");

            if (data.getHttpCode() > 0) {
                meta.append("Status Code: ").append(data.getHttpCode()).append('\n');
            }

            if (data.getHttpHeaders() != null && !data.getHttpHeaders().isEmpty()) {
                meta.append('\n');
                meta.append("Headers:\n");
                // Sort headers for better readability
                List<String> sortedKeys = new java.util.ArrayList<>(data.getHttpHeaders().keySet());
                sortedKeys.sort(String.CASE_INSENSITIVE_ORDER);

                for (String key : sortedKeys) {
                    String value = data.getHttpHeaders().get(key);
                    if (value != null && !value.trim().isEmpty()) {
                        meta.append("  ").append(key).append(": ").append(value).append('\n');
                    }
                }
            }
        }

        metaArea.setText(meta.toString());
    }

    private boolean hasValue(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String defaultString(String s) {
        return s == null ? "N/A" : s;
    }

    private record LedIcon(Color color, int size) implements Icon {
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
}
