package com.aleph.graymatter.jtyposquatting.ui;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.JTypoSquatting;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JTypoFrame extends JFrame {

    private final JTextField jTextFieldInput;
    private final JTable jTableOutput;
    private final DefaultTableModel tableModel;
    private final JTextField jTextFieldConsole;
    private final JButton jGenerateButton;
    private final JButton jCopyButton;
    private final ExecutorService executorService;

    public JTypoFrame() {
        executorService = Executors.newFixedThreadPool(20); // Thread pool for parallel checks

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            setIconImage(ImageIO.read(new File("aleph_sg.jpg")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        setTitle("Aleph TypoSquatting Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1200, 800));
        setLayout(new BorderLayout(10, 10));

        // ===== North Panel =====
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        northPanel.add(new JLabel("Domain Name:"));
        jTextFieldInput = new JTextField("www.example.com", 40);
        northPanel.add(jTextFieldInput);
        jGenerateButton = new JButton("Generate");
        northPanel.add(jGenerateButton);
        add(northPanel, BorderLayout.NORTH);

        // ===== Center Panel (Output Table) =====
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        String[] columnNames = {"Domain URL", "Status", "Title"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make cells non-editable
            }
        };
        jTableOutput = new JTable(tableModel);
        jTableOutput.setFillsViewportHeight(true);
        jTableOutput.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Enable Sorting
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        jTableOutput.setRowSorter(sorter);

        // Custom Renderer for Status Colors
        jTableOutput.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    // Get status from the model, not the view, to ensure correct color mapping
                    int modelRow = table.convertRowIndexToModel(row);
                    String status = (String) tableModel.getValueAt(modelRow, 1);
                    
                    if ("Testing...".equals(status)) {
                        c.setBackground(Color.ORANGE);
                    } else if ("Alive".equals(status)) {
                        c.setBackground(Color.RED);
                    } else if ("Dead".equals(status)) {
                        c.setBackground(Color.GREEN);
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(240, 240, 240));
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(jTableOutput);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // ===== East Panel (Copy Button) =====
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.Y_AXIS));
        eastPanel.setBorder(new EmptyBorder(0, 10, 0, 10));
        jCopyButton = new JButton();
        try {
            ImageIcon copyIcon = new ImageIcon(ImageIO.read(new File("copy-icon.png")));
            jCopyButton.setIcon(copyIcon);
        } catch (IOException e) {
            jCopyButton.setText("Copy");
            e.printStackTrace();
        }
        jCopyButton.setToolTipText("Copy selected rows to clipboard");
        eastPanel.add(jCopyButton);
        centerPanel.add(eastPanel, BorderLayout.EAST);
        add(centerPanel, BorderLayout.CENTER);

        // ===== South Panel (Console) =====
        JPanel southPanel = new JPanel(new BorderLayout(10, 10));
        southPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        jTextFieldConsole = new JTextField();
        jTextFieldConsole.setEditable(false);
        jTextFieldConsole.setHorizontalAlignment(JTextField.CENTER);
        southPanel.add(jTextFieldConsole, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        // ===== Action Listeners =====
        jGenerateButton.addActionListener(e -> validateAction());
        jCopyButton.addActionListener(e -> copy());

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void validateAction() {
        jGenerateButton.setEnabled(false);
        jTextFieldConsole.setText("Generating domains... please wait.");
        tableModel.setRowCount(0); // Clear existing data

        SwingWorker<JTypoSquatting, Void> worker = new SwingWorker<>() {
            @Override
            protected JTypoSquatting doInBackground() throws Exception {
                return new JTypoSquatting(jTextFieldInput.getText());
            }

            @Override
            protected void done() {
                try {
                    JTypoSquatting jTypoSquatting = get();
                    jTextFieldConsole.setForeground(Color.BLACK);
                    jTextFieldConsole.setText("Number of generated squatable domains: " + jTypoSquatting.getNumberOfDomains());
                    
                    ArrayList<String> domains = jTypoSquatting.getListOfDomains();
                    for (String domain : domains) {
                        tableModel.addRow(new Object[]{domain, "Testing...", ""});
                        int row = tableModel.getRowCount() - 1;
                        executorService.submit(() -> checkDomain(domain, row));
                    }
                    
                } catch (InterruptedException | ExecutionException e) {
                    Throwable cause = e.getCause();
                    jTextFieldConsole.setForeground(Color.RED);
                    if (cause instanceof FileNotFoundException) {
                        jTextFieldConsole.setText("Error: One or more required files are missing.");
                    } else if (cause instanceof InvalidDomainException) {
                        jTextFieldConsole.setText("Error: The entered domain name is invalid.");
                    } else {
                        jTextFieldConsole.setText("An unexpected error occurred during generation.");
                        e.printStackTrace();
                    }
                } finally {
                    jGenerateButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void checkDomain(String domainUrl, int row) {
        String status = "Dead";
        String title = "";
        
        // Try HTTPS first
        String[] result = fetchTitle(domainUrl);
        if (result != null) {
            status = "Alive";
            title = result[1];
        } else {
            // Fallback to HTTP
            String httpUrl = domainUrl.replace("https://", "http://");
            result = fetchTitle(httpUrl);
            if (result != null) {
                status = "Alive";
                title = result[1];
            }
        }

        String finalStatus = status;
        String finalTitle = title;
        SwingUtilities.invokeLater(() -> {
            if (row < tableModel.getRowCount()) {
                tableModel.setValueAt(finalStatus, row, 1);
                tableModel.setValueAt(finalTitle, row, 2);
                // Force repaint to update color
                tableModel.fireTableCellUpdated(row, 1);
                tableModel.fireTableCellUpdated(row, 2);
            }
        });
    }

    private String[] fetchTitle(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000); // 3 seconds timeout
            connection.setReadTimeout(3000);
            connection.setRequestMethod("GET"); // Use GET to fetch content
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            if (200 <= responseCode && responseCode <= 399) {
                // Read content to find title
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                        if (content.toString().toLowerCase().contains("</title>")) {
                            break; // Stop reading once title is found
                        }
                    }
                    
                    Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(content.toString());
                    String title = "";
                    if (matcher.find()) {
                        title = matcher.group(1).trim();
                    }
                    return new String[]{String.valueOf(responseCode), title};
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return null;
    }

    private void copy() {
        int[] selectedRows = jTableOutput.getSelectedRows();
        if (selectedRows.length == 0) {
            // If no rows selected, copy all
            selectedRows = new int[jTableOutput.getRowCount()];
            for (int i = 0; i < jTableOutput.getRowCount(); i++) {
                selectedRows[i] = i;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int row : selectedRows) {
            // Convert view index to model index in case of sorting
            int modelRow = jTableOutput.convertRowIndexToModel(row);
            sb.append(tableModel.getValueAt(modelRow, 0)).append("\t")
              .append(tableModel.getValueAt(modelRow, 1)).append("\t")
              .append(tableModel.getValueAt(modelRow, 2)).append("\n");
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection data = new StringSelection(sb.toString());
        clipboard.setContents(data, data);
        jTextFieldConsole.setForeground(Color.BLACK);
        jTextFieldConsole.setText("Copied " + selectedRows.length + " domains to clipboard.");
    }
}
