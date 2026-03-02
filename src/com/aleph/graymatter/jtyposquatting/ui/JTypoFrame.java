package com.aleph.graymatter.jtyposquatting.ui;

import com.aleph.graymatter.jtyposquatting.InvalidDomainException;
import com.aleph.graymatter.jtyposquatting.JTypoSquatting;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class JTypoFrame extends JFrame {

    private final JTextField jTextFieldInput;
    private final JTextArea jTextAreaOutput;
    private final JTextField jTextFieldConsole;
    private final JButton jGenerateButton;
    private final JButton jCopyButton;

    public JTypoFrame() {
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

        // ===== Center Panel (Output) =====
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new EmptyBorder(0, 10, 0, 0));
        jTextAreaOutput = new JTextArea();
        jTextAreaOutput.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(jTextAreaOutput);
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
        jCopyButton.setToolTipText("Copy results to clipboard");
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
        jTextAreaOutput.setText("");

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
                    jTextAreaOutput.setText(jTypoSquatting.getListOfDomainsAsURL());
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

    private void copy() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection data = new StringSelection(jTextAreaOutput.getText());
        clipboard.setContents(data, data);
        jTextFieldConsole.setForeground(Color.BLACK);
        jTextFieldConsole.setText("Results copied to clipboard.");
    }
}
