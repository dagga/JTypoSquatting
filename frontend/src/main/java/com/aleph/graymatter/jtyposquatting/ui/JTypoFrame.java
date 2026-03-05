package com.aleph.graymatter.jtyposquatting.ui;

import com.aleph.graymatter.jtyposquatting.client.JTypoSquattingRestClient;
import com.aleph.graymatter.jtyposquatting.config.ClientConfig;
import com.aleph.graymatter.jtyposquatting.config.ConfigManager;
import com.aleph.graymatter.jtyposquatting.dto.DomainResultDTO;

import java.awt.datatransfer.DataFlavor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.ImageIcon;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.KeyStroke;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main application frame for JTypoSquatting tool that communicates via REST API using SSE
 */
public class JTypoFrame extends JFrame {
    private final JTextField jTextFieldInput;
    private final JTable jTableOutput;
    private final DefaultTableModel tableModel;
    private final JTextField jTextFieldConsole;
    private final JButton jGenerateButton;
    private final JButton jCopyButton;
    private final JButton jClearButton;
    private final JTypoSquattingRestClient restClient;
    private final ExecutorService executorService;
    private final DomainStreamingService streamingService;
    private final Map<String, Integer> domainRowMap = new HashMap<>();
    private final Map<String, DomainResultDTO> domainDataMap = new HashMap<>();
    private final AtomicInteger openDialogCount = new AtomicInteger(0);
    private final int maxOpenDialogs;
    private final PreviewPanel previewPanel;
    private final LogPanel backendLogPanel;
    private final LogPanel frontendLogPanel;
    private volatile boolean isGenerating = false;
    private final ConfigManager config;

    public JTypoFrame() {
        config = ConfigManager.getInstance();
        
        // Load configuration values
        maxOpenDialogs = config.getIntConfig("ui.max.open.dialogs", 5);
        int windowWidth = config.getIntConfig("ui.window.width", 1200);
        int windowHeight = config.getIntConfig("ui.window.height", 800);
        int tableRowHeight = config.getIntConfig("ui.table.row.height", 24);
        int previewWidth = config.getIntConfig("ui.preview.width", 320);
        int previewHeight = config.getIntConfig("ui.preview.height", 240);
        
        restClient = new JTypoSquattingRestClient(ClientConfig.getApiUrl());
        streamingService = new DomainStreamingService(restClient);
        executorService = Executors.newSingleThreadExecutor();

        Runtime.getRuntime().addShutdownHook(new Thread(streamingService::shutdown));

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            setIconImage(ImageIO.read(getClass().getResourceAsStream("/aleph_sg.jpg")));
        } catch (Exception e) {
            e.printStackTrace();
        }

        setTitle(config.getMessage("app.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(windowWidth, windowHeight));
        setLayout(new BorderLayout(10, 10));

        ((JComponent) getContentPane()).setDoubleBuffered(true);

        // ===== North Panel =====
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        northPanel.add(new JLabel(config.getMessage("label.domain.name")));
        jTextFieldInput = new JTextField("www.aleph-networks.eu", 40);
        northPanel.add(jTextFieldInput);
        jGenerateButton = new JButton(config.getMessage("button.generate"));
        jGenerateButton.setIcon(createTextIcon("⟳"));
        northPanel.add(jGenerateButton);
        add(northPanel, BorderLayout.NORTH);

        // ===== Center Panel (Output Table) =====
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        String[] columnNames = {
            config.getMessage("column.domain.url"),
            config.getMessage("column.status"),
            config.getMessage("column.title"),
            config.getMessage("column.language"),
            config.getMessage("column.flag"),
            config.getMessage("column.description"),
            config.getMessage("column.http.code"),
            config.getMessage("column.screenshot")
        };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        jTableOutput = new JTable(tableModel);
        jTableOutput.setFillsViewportHeight(true);
        jTableOutput.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        jTableOutput.setDoubleBuffered(true);
        jTableOutput.setAutoscrolls(true);
        jTableOutput.setShowGrid(true);
        jTableOutput.setGridColor(Color.LIGHT_GRAY);
        jTableOutput.setSelectionForeground(Color.BLACK);
        jTableOutput.setRowHeight(tableRowHeight);

        jTableOutput.getColumnModel().getColumn(0).setPreferredWidth(200);
        jTableOutput.getColumnModel().getColumn(1).setPreferredWidth(100);
        jTableOutput.getColumnModel().getColumn(2).setPreferredWidth(250);
        jTableOutput.getColumnModel().getColumn(3).setPreferredWidth(80);
        jTableOutput.getColumnModel().getColumn(4).setMinWidth(40);
        jTableOutput.getColumnModel().getColumn(4).setMaxWidth(40);
        jTableOutput.getColumnModel().getColumn(4).setPreferredWidth(40);
        jTableOutput.getColumnModel().getColumn(5).setPreferredWidth(300);
        jTableOutput.getColumnModel().getColumn(6).setMinWidth(0);
        jTableOutput.getColumnModel().getColumn(6).setMaxWidth(0);
        jTableOutput.getColumnModel().getColumn(6).setWidth(0);
        jTableOutput.getColumnModel().getColumn(7).setMinWidth(0);
        jTableOutput.getColumnModel().getColumn(7).setMaxWidth(0);
        jTableOutput.getColumnModel().getColumn(7).setWidth(0);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(1, (o1, o2) -> {
            int priority1 = getStatusPriority((String) o1);
            int priority2 = getStatusPriority((String) o2);
            return Integer.compare(priority1, priority2);
        });
        sorter.setComparator(2, (o1, o2) -> {
            String t1 = (o1 == null || o1.toString().trim().isEmpty()) ? "\uFFFF" : o1.toString().toLowerCase().trim();
            String t2 = (o2 == null || o2.toString().trim().isEmpty()) ? "\uFFFF" : o2.toString().toLowerCase().trim();
            return t1.compareTo(t2);
        });
        sorter.setSortKeys(java.util.List.of(
                new RowSorter.SortKey(1, SortOrder.ASCENDING),
                new RowSorter.SortKey(2, SortOrder.ASCENDING)
        ));
        jTableOutput.setRowSorter(sorter);

        jTableOutput.setDefaultRenderer(Object.class, new CustomTableCellRenderer());
        jTableOutput.setDefaultRenderer(String.class, new CustomTableCellRenderer());
        jTableOutput.getColumnModel().getColumn(1).setCellRenderer(new CustomStatusLedRenderer());
        jTableOutput.getColumnModel().getColumn(4).setCellRenderer(new FlagCellRenderer());

        jTableOutput.setComponentPopupMenu(createTablePopupMenu());

        jTableOutput.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl C"), "copyDomains");
        jTableOutput.getActionMap().put("copyDomains", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                copySelectedDomains();
            }
        });

        jTableOutput.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl V"), "pasteDomains");
        jTableOutput.getActionMap().put("pasteDomains", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                pasteDomains();
            }
        });

        jTableOutput.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showDomainDetails();
                }
            }
        });

        jTableOutput.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateThumbnail();
            }
        });

        JScrollPane scrollPane = new JScrollPane(jTableOutput);
        scrollPane.setDoubleBuffered(true);
        scrollPane.setMinimumSize(new Dimension(400, 200));
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // ===== Logs Panel =====
        JTabbedPane logsTab = new JTabbedPane();
        logsTab.setDoubleBuffered(true);
        String backendLogPath = config.getConfig("log.backend.path");
        String frontendLogPath = config.getConfig("log.frontend.path");
        backendLogPanel = new LogPanel(backendLogPath, config.getMessage("label.backend.log") + backendLogPath);
        frontendLogPanel = new LogPanel(frontendLogPath, config.getMessage("label.frontend.log") + frontendLogPath);
        logsTab.addTab("Backend", backendLogPanel);
        logsTab.addTab("Frontend", frontendLogPanel);
        logsTab.setPreferredSize(new Dimension(500, 300));

        // ===== Thumbnail Panel (under logs) =====
        previewPanel = new PreviewPanel();
        previewPanel.setPreferredSize(new Dimension(previewWidth, previewHeight + 50));

        // Create right panel with logs on top and thumbnail below
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(logsTab, BorderLayout.CENTER);
        rightPanel.add(previewPanel, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);

        // ===== South Panel =====
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        jTextFieldConsole = new JTextField();
        jTextFieldConsole.setEditable(false);
        jTextFieldConsole.setText(config.getMessage("console.ready"));
        southPanel.add(jTextFieldConsole, BorderLayout.CENTER);

        jCopyButton = new JButton(config.getMessage("button.copy"));
        jCopyButton.setIcon(createTextIcon("C"));
        jCopyButton.addActionListener(e -> copySelectedDomains());

        jClearButton = new JButton(config.getMessage("button.clear"));
        jClearButton.setIcon(createTextIcon("X"));
        jClearButton.addActionListener(e -> clearAll());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(jClearButton);
        buttonPanel.add(jCopyButton);
        southPanel.add(buttonPanel, BorderLayout.EAST);

        add(southPanel, BorderLayout.SOUTH);

        // ===== Button Actions =====
        jGenerateButton.addActionListener(e -> generateAndCheckDomains());

        // Set up streaming service callbacks
        streamingService.setCallbacks(
                this::updateDomainTable,
                this::onStreamComplete,
                this::onStreamError
        );

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        checkApiHealth();

        // Start log tailers
        backendLogPanel.start();
        frontendLogPanel.start();
    }

    private void checkApiHealth() {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return restClient.isHealthy();
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        jTextFieldConsole.setText(config.getMessage("console.api.connected") + restClient.getBaseUrl());
                    } else {
                        jTextFieldConsole.setText(config.getMessage("console.api.error") + restClient.getBaseUrl());
                        JOptionPane.showMessageDialog(JTypoFrame.this, 
                            config.getMessage("dialog.api.connection.error.message"), 
                            config.getMessage("dialog.api.connection.error.title"), 
                            JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception e) {
                    jTextFieldConsole.setText(config.getMessage("console.error") + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void generateAndCheckDomains() {
        String domain = jTextFieldInput.getText().trim();
        if (domain.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                config.getMessage("dialog.input.error.message"), 
                config.getMessage("dialog.input.error.title"), 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // If already generating, stop the current generation first
        if (isGenerating) {
            stopCurrentGeneration();
        }

        // Restart log panels if they were stopped
        if (backendLogPanel != null) {
            backendLogPanel.start();
        }
        if (frontendLogPanel != null) {
            frontendLogPanel.start();
        }

        isGenerating = true;
        jGenerateButton.setEnabled(false);
        jGenerateButton.setText(config.getMessage("button.generate") + "...");
        jTextFieldConsole.setText(config.getMessage("console.connecting"));
        
        // Clear previous data
        tableModel.setRowCount(0);
        domainRowMap.clear();
        domainDataMap.clear();
        previewPanel.clearPreview();
        activeDomainCount.set(0);
        deadDomainCount.set(0);
        totalGeneratedCount.set(0);

        streamingService.startDomainChecks(domain);
    }

    private void stopCurrentGeneration() {
        executorService.submit(() -> {
            try {
                streamingService.cancelActiveStreamIfAny();
                restClient.cancelActiveAnalysis();
                restClient.clearAllCachedData();
            } catch (Exception e) {
                // Ignore stopping errors
            }
        });
    }

    private volatile AtomicInteger activeDomainCount = new AtomicInteger(0);
    private volatile AtomicInteger deadDomainCount = new AtomicInteger(0);;
    private volatile AtomicInteger totalGeneratedCount = new AtomicInteger(0);;

    private void updateDomainTable(DomainResultDTO result) {
        if (SwingUtilities.isEventDispatchThread()) {
            processDomainUpdate(result);
        } else {
            SwingUtilities.invokeLater(() -> processDomainUpdate(result));
        }
    }

    private void processDomainUpdate(DomainResultDTO result) {
        // Handle Unreachable domains - remove them from the list
        if ("Unreachable".equals(result.getStatus())) {
            // Search for the domain in the table by name
            int removeRow = -1;
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String domainName = (String) tableModel.getValueAt(i, 0);
                if (result.getDomain().equals(domainName)) {
                    removeRow = i;
                    break;
                }
            }

            if (removeRow != -1) {
                String removedStatus = (String) tableModel.getValueAt(removeRow, 1);
                if ("Suspicious".equals(removedStatus) || "Safe".equals(removedStatus)) {
                    activeDomainCount.addAndGet(-1);
                } else if ("Testing...".equals(removedStatus)) {
                    // Do not count Testing... domains in active/dead counts
                }

                tableModel.removeRow(removeRow);
                domainRowMap.remove(result.getDomain());
                domainDataMap.remove(result.getDomain());

                // Adjust row mappings for domains after the removed one
                for (Map.Entry<String, Integer> entry : domainRowMap.entrySet()) {
                    if (entry.getValue() > removeRow) {
                        domainRowMap.put(entry.getKey(), entry.getValue() - 1);
                    }
                }
            }
            return; // Stop processing unreachable domains further
        }

        // Store the full DTO for details view
            domainDataMap.put(result.getDomain(), result);

            if ("Timeout".equals(result.getStatus())) {
                // Search for the domain in the table by name
                int removeRow = -1;
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String domainName = (String) tableModel.getValueAt(i, 0);
                    if (result.getDomain().equals(domainName)) {
                        removeRow = i;
                        break;
                    }
                }

                if (removeRow != -1) {
                    String removedStatus = (String) tableModel.getValueAt(removeRow, 1);
                    if ("Suspicious".equals(removedStatus) || "Safe".equals(removedStatus)) {
                        activeDomainCount.addAndGet(-1);
                    } else if ("Testing...".equals(removedStatus)) {
                        // Do not count Testing... domains in active/dead counts
                    }

                    tableModel.removeRow(removeRow);
                    domainRowMap.remove(result.getDomain());
                    domainDataMap.remove(result.getDomain());

                    // Adjust row mappings for domains after the removed one
                    for (Map.Entry<String, Integer> entry : domainRowMap.entrySet()) {
                        if (entry.getValue() > removeRow) {
                            domainRowMap.put(entry.getKey(), entry.getValue() - 1);
                        }
                    }
                }
            } else if (!domainRowMap.containsKey(result.getDomain())) {
                // Add new domain (only if not Unreachable)
                totalGeneratedCount.addAndGet(1);
                String flag = getFlagForLanguage(result.getLanguage());
                tableModel.addRow(new Object[]{
                        result.getDomain(),
                        result.getStatus(),
                        result.getTitle(),
                        result.getLanguage(),
                        flag,
                        result.getDescription(),
                        result.getHttpCode(),
                        result.getScreenshot()
                });
                domainRowMap.put(result.getDomain(), tableModel.getRowCount() - 1);
                jTableOutput.revalidate();
                jTableOutput.repaint();
                if ("Suspicious".equals(result.getStatus()) || "Safe".equals(result.getStatus())) {
                    activeDomainCount.addAndGet(1);
                }
            } else {
                // Update existing domain
                int row = domainRowMap.get(result.getDomain());
                String oldStatus = (String) tableModel.getValueAt(row, 1);
                String newStatus = result.getStatus();

                // Update counters based on status transition
                if ("Active".equals(oldStatus)) {
                    if (!"Active".equals(newStatus)) {
                        activeDomainCount.addAndGet(-1);
                        if ("Dead".equals(newStatus)) {
                            deadDomainCount.addAndGet(1);
                        }
                    }
                } else if ("Dead".equals(oldStatus)) {
                    if (!"Dead".equals(newStatus)) {
                        deadDomainCount.addAndGet(-1);
                        if ("Active".equals(newStatus)) {
                            activeDomainCount.addAndGet(1);
                        }
                    }
                } else if ("Testing...".equals(oldStatus)) {
                    if ("Active".equals(newStatus)) {
                        activeDomainCount.addAndGet(1);
                    } else if ("Dead".equals(newStatus)) {
                        deadDomainCount.addAndGet(1);
                    }
                }

                tableModel.setValueAt(result.getStatus(), row, 1);
                tableModel.setValueAt(result.getTitle(), row, 2);
                tableModel.setValueAt(result.getLanguage(), row, 3);
                tableModel.setValueAt(getFlagForLanguage(result.getLanguage()), row, 4);
                tableModel.setValueAt(result.getDescription(), row, 5);
                tableModel.setValueAt(result.getHttpCode(), row, 6);
                tableModel.setValueAt(result.getScreenshot(), row, 7);
            }

            updateConsole();
            jTableOutput.repaint();
    }

    private void onStreamComplete() {
        SwingUtilities.invokeLater(() -> {
            isGenerating = false;
            String metrics = config.getMessage("metrics.generated") + totalGeneratedCount + 
                           config.getMessage("metrics.separator") +
                           config.getMessage("metrics.http.up") + activeDomainCount + 
                           config.getMessage("metrics.separator") +
                           config.getMessage("metrics.inaccessible") + deadDomainCount;
            jTextFieldConsole.setText(config.getMessage("console.final.metrics") + metrics);
            jGenerateButton.setEnabled(true);
            jGenerateButton.setText(config.getMessage("button.generate"));
        });
    }

    private void onStreamError(String error) {
        SwingUtilities.invokeLater(() -> {
            isGenerating = false;
            jTextFieldConsole.setText(config.getMessage("console.error") + error);
            jGenerateButton.setEnabled(true);
            jGenerateButton.setText(config.getMessage("button.generate"));
        });
    }

    private void sortTableByStatus() {
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) jTableOutput.getRowSorter();
        sorter.setSortKeys(java.util.List.of(new RowSorter.SortKey(1, SortOrder.ASCENDING)));
        sorter.sort();
    }

    private int getStatusPriority(String status) {
        if ("Suspicious".equals(status)) return 0; // Red
        if ("Safe".equals(status)) return 1;       // Green
        if ("Checking".equals(status) || "Testing...".equals(status)) return 2; // Orange
        return 3;
    }

    private void updateConsole() {
        int httpUpCount = activeDomainCount.get();
        int inaccessibleCount = deadDomainCount.get();
        String metrics = config.getMessage("metrics.generated") + totalGeneratedCount + 
                       config.getMessage("metrics.separator") +
                       config.getMessage("metrics.http.up") + httpUpCount + 
                       config.getMessage("metrics.separator") +
                       config.getMessage("metrics.inaccessible") + inaccessibleCount;
        jTextFieldConsole.setText(config.getMessage("console.processing") + metrics);
    }

    private void copySelectedDomains() {
        int[] selectedRows = jTableOutput.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, 
                config.getMessage("dialog.selection.error.message"), 
                config.getMessage("dialog.selection.error.title"), 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int row : selectedRows) {
            int modelRow = jTableOutput.convertRowIndexToModel(row);
            sb.append((String) tableModel.getValueAt(modelRow, 0)).append("\n");
        }
        StringSelection selection = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        jTextFieldConsole.setText(config.getMessage("console.copied") + selectedRows.length + 
                                  config.getMessage("console.domains.to.clipboard"));
    }

    private void updateThumbnail() {
        int selectedRow = jTableOutput.getSelectedRow();
        if (selectedRow < 0) {
            previewPanel.clearPreview();
            return;
        }

        int modelRow = jTableOutput.convertRowIndexToModel(selectedRow);
        String domain = (String) tableModel.getValueAt(modelRow, 0);
        
        // Get domain data from the map instead of directly from table (more reliable)
        DomainResultDTO domainData = domainDataMap.get(domain);
        byte[] screenshot = null;
        if (domainData != null) {
            screenshot = domainData.getScreenshot();
        }
        
        // Fallback to table value if map entry not found
        if (screenshot == null) {
            Object screenshotObj = tableModel.getValueAt(modelRow, 7);
            if (screenshotObj instanceof byte[]) {
                screenshot = (byte[]) screenshotObj;
            }
        }
        
        previewPanel.updatePreview(domain, screenshot);
    }

    private void clearAll() {
        // Stop any active generation/streaming
        executorService.submit(() -> {
            try {
                restClient.cancelActiveAnalysis();
                restClient.clearAllCachedData();
            } catch (Exception e) {
                // Ignore clearing errors
            }
        });

        // Clear UI
        tableModel.setRowCount(0);
        domainRowMap.clear();
        domainDataMap.clear();
        previewPanel.clearPreview();
        activeDomainCount.set(0);
        deadDomainCount.set(0);
        totalGeneratedCount.set(0);

        // Clear log panels and stop tailers
        if (backendLogPanel != null) {
            backendLogPanel.stop();
            backendLogPanel.clearLogs();
        }
        if (frontendLogPanel != null) {
            frontendLogPanel.stop();
            frontendLogPanel.clearLogs();
        }

        jTextFieldConsole.setText(config.getMessage("console.ready"));
        jGenerateButton.setEnabled(true);
        jGenerateButton.setText(config.getMessage("button.generate"));
    }

    private void pasteDomains() {
        try {
            String text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    jTextFieldInput.setText(trimmed);
                    jTextFieldConsole.setText(config.getMessage("console.pasted") + trimmed);
                    break;
                }
            }
        } catch (Exception e) {
            jTextFieldConsole.setText(config.getMessage("console.paste.error"));
        }
    }

    private JPopupMenu createTablePopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem(config.getMessage("menu.copy"));
        copyItem.addActionListener(e -> copySelectedDomains());
        popupMenu.add(copyItem);
        JMenuItem pasteItem = new JMenuItem(config.getMessage("menu.paste"));
        pasteItem.addActionListener(e -> pasteDomains());
        popupMenu.add(pasteItem);
        return popupMenu;
    }

    private void showContextMenu(MouseEvent e) {
        int row = jTableOutput.rowAtPoint(e.getPoint());
        if (row >= 0 && !jTableOutput.isRowSelected(row)) {
            jTableOutput.changeSelection(row, jTableOutput.columnAtPoint(e.getPoint()), false, false);
        }
        jTableOutput.getComponentPopupMenu().show(e.getComponent(), e.getX(), e.getY());
    }

    private void showDomainDetails() {
        int selectedRow = jTableOutput.getSelectedRow();
        if (selectedRow < 0) return;
        if (openDialogCount.get() >= maxOpenDialogs) {
            JOptionPane.showMessageDialog(this, 
                config.getMessage("console.too.many.dialogs"), 
                config.getMessage("console.limit.reached"), 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = jTableOutput.convertRowIndexToModel(selectedRow);
        String domain = (String) tableModel.getValueAt(modelRow, 0);

        // Retrieve full DTO from map
        DomainResultDTO data = domainDataMap.get(domain);

        if (data == null) {
            data = new DomainResultDTO();
            data.setDomain(domain);
            data.setStatus((String) tableModel.getValueAt(modelRow, 1));
            data.setTitle((String) tableModel.getValueAt(modelRow, 2));
            data.setLanguage((String) tableModel.getValueAt(modelRow, 3));
            data.setDescription((String) tableModel.getValueAt(modelRow, 4));
        }

        jTextFieldConsole.setText(config.getMessage("console.opening.details") + domain);
        DomainDetailsDialog dialog = new DomainDetailsDialog(this, data);
        dialog.setModalityType(Dialog.ModalityType.MODELESS);
        openDialogCount.incrementAndGet();
        dialog.setVisible(true);
    }

    public void decrementOpenDialogCount() {
        openDialogCount.decrementAndGet();
        jTextFieldConsole.setText(config.getMessage("console.ready"));
    }

    private class CustomStatusLedRenderer extends JLabel implements TableCellRenderer {
        private static final int LED_SIZE = 20;
        public CustomStatusLedRenderer() { 
            setHorizontalAlignment(JLabel.CENTER); 
            setOpaque(true); 
        }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            String status = (String) value;
            Color ledColor;
            Color bgColor;
            if ("Suspicious".equals(status)) {
                ledColor = new Color(255, 80, 80);
                bgColor = new Color(255, 220, 220);
            } else if ("Safe".equals(status)) {
                ledColor = new Color(100, 200, 100);
                bgColor = new Color(220, 255, 220);
            } else if ("Checking".equals(status) || "Testing...".equals(status)) {
                ledColor = new Color(255, 165, 0);
                bgColor = new Color(255, 240, 200);
            } else {
                ledColor = new Color(200, 200, 200);
                bgColor = Color.WHITE;
            }

            setOpaque(true);
            setBackground(isSelected ? table.getSelectionBackground() : bgColor);
            setIcon(new LedIcon(ledColor, LED_SIZE));

            // Get HTTP code from model (column 6)
            int modelRow = table.convertRowIndexToModel(row);
            Object httpCodeObj = table.getModel().getValueAt(modelRow, 6);
            String httpCodeStr = "";
            if (httpCodeObj != null) {
                try {
                    int httpCode = Integer.parseInt(httpCodeObj.toString());
                    // Show HTTP code only for positive values
                    // Keep empty for httpCode == -1 (testing) and httpCode == 0 (unreachable/dead)
                    if (httpCode > 0) {
                        httpCodeStr = String.valueOf(httpCode);
                    }
                } catch (NumberFormatException e) {
                    // Ignore if value is not a valid integer
                }
            }
            setText(httpCodeStr);

            return this;
        }
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

    private class CustomTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (c instanceof JComponent) {
                ((JComponent) c).setOpaque(true);
            }
            
            if (isSelected) {
                c.setBackground(table.getSelectionBackground());
            } else {
                int modelRow = table.convertRowIndexToModel(row);
                Object statusObj = table.getModel().getValueAt(modelRow, 1);
                String status = (statusObj != null) ? statusObj.toString().trim() : "";
                
                if ("Suspicious".equals(status)) {
                    c.setBackground(new Color(255, 220, 220));
                } else if ("Safe".equals(status)) {
                    c.setBackground(new Color(220, 255, 220));
                } else if ("Checking".equals(status) || "Testing...".equals(status)) {
                    c.setBackground(new Color(255, 240, 200));
                } else {
                    c.setBackground(Color.WHITE);
                }
            }
            
            return c;
        }
    }

    /**
     * Creates a simple text-based icon for buttons
     */
    private Icon createTextIcon(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        int size = 20;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g2d.setColor(Color.BLACK);
        
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = (size - textWidth) / 2;
        int y = (size + fm.getAscent()) / 2 - fm.getDescent();
        
        g2d.drawString(text, x, y);
        g2d.dispose();
        
        return new ImageIcon(image);
    }

    /**
     * Creates an ImageIcon from an emoji character
     */
    private ImageIcon createIconFromEmoji(String emoji) {
        if (emoji == null || emoji.isEmpty()) {
            return null;
        }
        
        // Create a small image with the emoji
        int iconSize = 24;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(iconSize, iconSize, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        
        // Use a larger font size for better visibility
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g2d.setColor(Color.BLACK);
        
        // Get font metrics and center the emoji
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(emoji);
        int textHeight = fm.getAscent() - fm.getDescent();
        int x = (iconSize - textWidth) / 2;
        int y = (iconSize + textHeight) / 2 - fm.getDescent();
        
        // Draw the emoji
        g2d.drawString(emoji, x, y);
        g2d.dispose();
        
        return new ImageIcon(image);
    }

    /**
     * Maps language code to flag emoji
     * Supports all languages from Lingua language detection library
     */
    private String getFlagForLanguage(String language) {
        if (language == null || language.isEmpty()) {
            return "🌐";
        }
        
        String lang = language.toUpperCase().trim();
        
        // Map language codes to country/region codes for flags
        switch (lang) {
            // European Languages
            case "ENGLISH":
            case "EN":
                return "🇬🇧";
            case "FRENCH":
            case "FR":
                return "🇫🇷";
            case "GERMAN":
            case "DE":
                return "🇩🇪";
            case "SPANISH":
            case "ES":
                return "🇪🇸";
            case "ITALIAN":
            case "IT":
                return "🇮🇹";
            case "PORTUGUESE":
            case "PT":
                return "🇵🇹";
            case "DUTCH":
            case "NL":
                return "🇳🇱";
            case "RUSSIAN":
            case "RU":
                return "🇷🇺";
            case "POLISH":
            case "PL":
                return "🇵🇱";
            case "TURKISH":
            case "TR":
                return "🇹🇷";
            case "SWEDISH":
            case "SV":
                return "🇸🇪";
            case "NORWEGIAN":
            case "NO":
            case "NORWEGIAN_N":
            case "NORWEGIAN_B":
                return "🇳🇴";
            case "DANISH":
            case "DA":
                return "🇩🇰";
            case "FINNISH":
            case "FI":
                return "🇫🇮";
            case "CZECH":
            case "CS":
                return "🇨🇿";
            case "GREEK":
            case "EL":
                return "🇬🇷";
            case "ROMANIAN":
            case "RO":
                return "🇷🇴";
            case "HUNGARIAN":
            case "HU":
                return "🇭🇺";
            case "UKRAINIAN":
            case "UK":
                return "🇺🇦";
            case "BULGARIAN":
            case "BG":
                return "🇧🇬";
            case "CROATIAN":
            case "HR":
                return "🇭🇷";
            case "SERBIAN":
            case "SR":
                return "🇷🇸";
            case "SLOVAK":
            case "SK":
                return "🇸🇰";
            case "SLOVENIAN":
            case "SL":
                return "🇸🇮";
            case "LITHUANIAN":
            case "LT":
                return "🇱🇹";
            case "LATVIAN":
            case "LV":
                return "🇱🇻";
            case "ESTONIAN":
            case "ET":
                return "🇪🇪";
            case "ALBANIAN":
            case "SQ":
                return "🇦🇱";
            case "MACEDONIAN":
            case "MK":
                return "🇲🇰";
            case "BOSNIAN":
            case "BS":
                return "🇧🇦";
            case "BELARUSIAN":
            case "BE":
                return "🇧🇾";
            case "ICELANDIC":
            case "IS":
                return "🇮🇸";
            case "IRISH":
            case "GA":
                return "🇮🇪";
            case "WELSH":
            case "CY":
                return "🏴󠁧󠁢󠁷󠁬󠁳󠁿";
            case "BASQUE":
            case "EU":
                return "🇪🇸"; // Basque Country is in Spain
            case "CATALAN":
            case "CA":
                return "🇪🇸"; // Catalonia is in Spain
            case "GALICIAN":
            case "GL":
                return "🇪🇸"; // Galicia is in Spain
            case "MALTESE":
            case "MT":
                return "🇲🇹";
            case "LUXEMBOURGISH":
            case "LB":
                return "🇱🇺";
            case "AFRIKAANS":
            case "AF":
                return "🇿🇦";
            
            // Asian Languages
            case "CHINESE":
            case "ZH":
                return "🇨🇳";
            case "JAPANESE":
            case "JA":
                return "🇯🇵";
            case "KOREAN":
            case "KO":
                return "🇰🇷";
            case "ARABIC":
            case "AR":
                return "🇸🇦";
            case "HINDI":
            case "HI":
                return "🇮🇳";
            case "THAI":
            case "TH":
                return "🇹🇭";
            case "VIETNAMESE":
            case "VI":
                return "🇻🇳";
            case "INDONESIAN":
            case "ID":
                return "🇮🇩";
            case "MALAY":
            case "MS":
                return "🇲🇾";
            case "BENGALI":
            case "BN":
                return "🇧🇩";
            case "URDU":
            case "UR":
                return "🇵🇰";
            case "PERSIAN":
            case "FA":
                return "🇮🇷";
            case "HEBREW":
            case "HE":
                return "🇮🇱";
            case "PUNJABI":
            case "PA":
                return "🇮🇳";
            case "GUJARATI":
            case "GU":
                return "🇮🇳";
            case "MARATHI":
            case "MR":
                return "🇮🇳";
            case "TAMIL":
            case "TA":
                return "🇮🇳";
            case "TELUGU":
            case "TE":
                return "🇮🇳";
            case "KANNADA":
            case "KN":
                return "🇮🇳";
            case "MALAYALAM":
            case "ML":
                return "🇮🇳";
            case "SINHALA":
            case "SI":
                return "🇱🇰";
            case "BURMESE":
            case "MY":
                return "🇲🇲";
            case "KHMER":
            case "KM":
                return "🇰🇭";
            case "LAO":
            case "LO":
                return "🇱🇦";
            case "GEORGIAN":
            case "KA":
                return "🇬🇪";
            case "ARMENIAN":
            case "HY":
                return "🇦🇲";
            case "AZERBAIJANI":
            case "AZ":
                return "🇦🇿";
            case "KAZAKH":
            case "KK":
                return "🇰🇿";
            case "UZBEK":
            case "UZ":
                return "🇺🇿";
            case "TAJIK":
            case "TG":
                return "🇹🇯";
            case "TURKMEN":
            case "TK":
                return "🇹🇲";
            case "KYRGYZ":
            case "KY":
                return "🇰🇬";
            case "MONGOLIAN":
            case "MN":
                return "🇲🇳";
            case "TIBETAN":
            case "BO":
                return "🇨🇳";
            case "DZONGKHA":
            case "DZ":
                return "🇧🇹";
            case "NEPALI":
            case "NE":
                return "🇳🇵";
            
            // African Languages
            case "SWAHILI":
            case "SW":
                return "🇰🇪";
            case "YORUBA":
            case "YO":
                return "🇳🇬";
            case "IGBO":
            case "IG":
                return "🇳🇬";
            case "HAUSA":
            case "HA":
                return "🇳🇬";
            case "AMHARIC":
            case "AM":
                return "🇪🇹";
            case "SOMALI":
            case "SO":
                return "🇸🇴";
            case "ZULU":
            case "ZU":
                return "🇿🇦";
            case "XHOSA":
            case "XH":
                return "🇿🇦";
            case "SHONA":
            case "SN":
                return "🇿🇼";
            
            // Other Languages
            case "ESPERANTO":
            case "EO":
                return "🏳️"; // Esperanto flag (green star)
            case "LATIN":
            case "LA":
                return "🏛️"; // Roman column for Latin
            
            default:
                return "🌐"; // Globe for unknown/other languages
        }
    }

    /**
     * Custom cell renderer for displaying flag icons
     */
    private class FlagCellRenderer extends JLabel implements TableCellRenderer {
        public FlagCellRenderer() {
            setHorizontalAlignment(JLabel.CENTER);
            setOpaque(true);
            // Use a font that supports emojis - try emoji fonts first, fallback to default
            Font emojiFont = getEmojiFont();
            setFont(emojiFont != null ? emojiFont : new Font("SansSerif", Font.PLAIN, 20));
        }

        /**
         * Try to find a font that supports emoji/flag characters
         */
        private Font getEmojiFont() {
            // Try common emoji font names for different platforms
            String[] emojiFontNames = {
                "Segoe UI Emoji",      // Windows
                "Apple Color Emoji",   // macOS
                "Noto Color Emoji",    // Linux with Noto fonts
                "EmojiOne",           // Some Linux systems
                "Twemoji",            // Some systems
                "Symbola"             // Fallback symbol font
            };

            for (String fontName : emojiFontNames) {
                Font font = new Font(fontName, Font.PLAIN, 20);
                if (font.getFamily().equals(fontName)) {
                    return font;
                }
            }

            // Check if system default font can display emoji
            Font defaultFont = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
            return defaultFont;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            String flag = (value != null) ? value.toString() : "";

            int modelRow = table.convertRowIndexToModel(row);
            Object statusObj = table.getModel().getValueAt(modelRow, 1);
            String status = (statusObj != null) ? statusObj.toString().trim() : "";

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                if ("Suspicious".equals(status)) {
                    setBackground(new Color(255, 220, 220));
                } else if ("Safe".equals(status)) {
                    setBackground(new Color(220, 255, 220));
                } else if ("Checking".equals(status) || "Testing...".equals(status)) {
                    setBackground(new Color(255, 240, 200));
                } else {
                    setBackground(Color.WHITE);
                }
                setForeground(Color.BLACK);
            }

            setText(flag);
            setIcon(null);

            return this;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(JTypoFrame::new);
    }
}