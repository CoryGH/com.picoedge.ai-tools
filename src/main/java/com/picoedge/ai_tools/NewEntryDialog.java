package com.picoedge.ai_tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Logger; // Logger for diagnostics

public class NewEntryDialog {
    private static final Logger LOGGER = Logger.getLogger(NewEntryDialog.class.getName()); // Logger for diagnostics
    private final Project project;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketManager webSocketManager;
    private final LogProcessor logProcessor;
    private final LogPane logPane; // Reference to LogPane for local event handling
    private Dimension screenSize;
    private int maxWidth;

    // Modified constructor to accept LogPane instance
    public NewEntryDialog(Project project, WebSocketManager webSocketManager, LogProcessor logProcessor, LogPane logPane) {
        this.project = project;
        this.webSocketManager = webSocketManager;
        this.logProcessor = logProcessor;
        this.logPane = logPane; // Initialize LogPane reference
        this.screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.maxWidth = (int) (screenSize.width * 0.8);
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            try {
                LOGGER.info("[NewEntryDialog] Initializing dialog creation on EDT");
                JDialog dialog = new JDialog((Frame) null, "New Log Entry", true);
                dialog.setBackground(new Color(30, 30, 30));
                JPanel panel = new JPanel();
                panel.setBackground(new Color(30, 30, 30));
                GridBagLayout layout = new GridBagLayout();
                panel.setLayout(layout);
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.insets = new Insets(10, 10, 10, 10);
                gbc.fill = GridBagConstraints.HORIZONTAL;

                JLabel levelLabel = new JLabel("Log Level:");
                levelLabel.setForeground(Color.WHITE);
                JComboBox<String> levelCombo = new JComboBox<>(new String[]{"Trace", "Debug", "Info", "Notice", "Warning", "Error", "Critical", "Fatal"});
                levelCombo.setSelectedItem("Info");
                levelCombo.setBackground(new Color(42, 42, 42));
                levelCombo.setForeground(Color.WHITE);

                JLabel categoryLabel = new JLabel("Category:");
                categoryLabel.setForeground(Color.WHITE);
                JTextField categoryField = new JTextField("test.websocket", 20);
                categoryField.setBackground(new Color(42, 42, 42));
                categoryField.setForeground(Color.WHITE);

                JLabel codeLabel = new JLabel("Code:");
                codeLabel.setForeground(Color.WHITE);
                JTextField codeField = new JTextField("0", 5);
                codeField.setBackground(new Color(42, 42, 42));
                codeField.setForeground(Color.WHITE);

                JLabel titleLabel = new JLabel("Title:");
                titleLabel.setForeground(Color.WHITE);
                JTextField titleField = new JTextField(20);
                titleField.setBackground(new Color(42, 42, 42));
                titleField.setForeground(Color.WHITE);

                JLabel descLabel = new JLabel("Description:");
                descLabel.setForeground(Color.WHITE);
                JTextArea descArea = new JTextArea(3, 20);
                descArea.setLineWrap(true);
                descArea.setWrapStyleWord(true);
                descArea.setBackground(new Color(42, 42, 42));
                descArea.setForeground(Color.WHITE);
                JScrollPane descScroll = new JScrollPane(descArea);

                JLabel dataLabel = new JLabel("Data (JSON):");
                dataLabel.setForeground(Color.WHITE);
                JTextArea dataArea = new JTextArea(3, 20);
                dataArea.setLineWrap(true);
                dataArea.setWrapStyleWord(true);
                dataArea.setBackground(new Color(42, 42, 42));
                dataArea.setForeground(Color.WHITE);
                JScrollPane dataScroll = new JScrollPane(dataArea);

                JLabel sourceLabel = new JLabel("Source:");
                sourceLabel.setForeground(Color.WHITE);
                JTextField sourceField = new JTextField("@ai-tools-logger/test", 15);
                sourceField.setBackground(new Color(42, 42, 42));
                sourceField.setForeground(Color.WHITE);

                JLabel deviceIdLabel = new JLabel("Device ID:");
                deviceIdLabel.setForeground(Color.WHITE);
                JTextField deviceIdField = new JTextField(UUID.randomUUID().toString(), 15);
                deviceIdField.setBackground(new Color(42, 42, 42));
                deviceIdField.setForeground(Color.WHITE);

                JLabel correlationIdLabel = new JLabel("Correlation ID:");
                correlationIdLabel.setForeground(Color.WHITE);
                JTextField correlationIdField = new JTextField(UUID.randomUUID().toString(), 15);
                correlationIdField.setBackground(new Color(42, 42, 42));
                correlationIdField.setForeground(Color.WHITE);

                JLabel stacktraceLabel = new JLabel("Include Stacktrace:");
                stacktraceLabel.setForeground(Color.WHITE);
                JCheckBox stacktraceCheck = new JCheckBox();
                stacktraceCheck.setBackground(new Color(30, 30, 30));
                stacktraceCheck.setForeground(Color.WHITE);

                JPanel buttonPanel = new JPanel(new FlowLayout());
                buttonPanel.setBackground(new Color(30, 30, 30));
                JButton submitButton = new JButton("Submit");
                submitButton.setBackground(new Color(76, 175, 80));
                submitButton.setForeground(Color.WHITE);
                JButton cancelButton = new JButton("Cancel");
                cancelButton.setBackground(new Color(255, 87, 34));
                cancelButton.setForeground(Color.WHITE);

                int baseWidth = 400;
                updateLayout(panel, layout, gbc, levelLabel, levelCombo, categoryLabel, categoryField,
                        codeLabel, codeField, titleLabel, titleField,
                        descLabel, descScroll, dataLabel, dataScroll,
                        sourceLabel, sourceField, deviceIdLabel, deviceIdField, correlationIdLabel, correlationIdField,
                        stacktraceLabel, stacktraceCheck, buttonPanel, submitButton, cancelButton, baseWidth, maxWidth);

                dialog.add(panel);
                dialog.pack();
                dialog.setMinimumSize(new Dimension(600, 400));
                dialog.setSize(800, 600);
                dialog.setLocationRelativeTo(null);

                dialog.addComponentListener(new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        int width = Math.min(dialog.getWidth(), maxWidth);
                        panel.removeAll();
                        updateLayout(panel, layout, gbc, levelLabel, levelCombo, categoryLabel, categoryField,
                                codeLabel, codeField, titleLabel, titleField,
                                descLabel, descScroll, dataLabel, dataScroll,
                                sourceLabel, sourceField, deviceIdLabel, deviceIdField, correlationIdLabel, correlationIdField,
                                stacktraceLabel, stacktraceCheck, buttonPanel, submitButton, cancelButton, width, maxWidth);
                        dialog.setSize(width, dialog.getHeight());
                        panel.revalidate();
                        panel.repaint();
                    }
                });

                submitButton.addActionListener(e -> {
                    try {
                        int level = LogProcessor.LogLevel.valueOf((String) levelCombo.getSelectedItem()).getValue();
                        String category = categoryField.getText().trim();
                        java.util.List<String> codeParts = Arrays.asList(codeField.getText().trim().split("\\s+"));
                        Integer code = codeParts.isEmpty() ? null : Integer.parseInt(codeParts.get(0));
                        String title = titleField.getText().trim();
                        String description = descArea.getText().trim();
                        Object data = null;
                        if (!dataArea.getText().trim().isEmpty()) {
                            try {
                                data = objectMapper.readValue(dataArea.getText().trim(), Object.class);
                            } catch (Exception ex) {
                                data = Map.of("error", "Invalid JSON data");
                                LOGGER.warning("[NewEntryDialog] Failed to parse JSON data: " + ex.getMessage());
                            }
                        }
                        String source = sourceField.getText().trim();
                        String eventDeviceId = deviceIdField.getText().trim();
                        String correlationId = correlationIdField.getText().trim();
                        boolean includeStacktrace = stacktraceCheck.isSelected();
                        Map<String, Object> event = new HashMap<>();
                        event.put("id", UUID.randomUUID().toString());
                        event.put("level", level);
                        event.put("category", category.isEmpty() ? "general" : category);
                        event.put("description", description.isEmpty() ? "" : description);
                        event.put("timestamp", System.currentTimeMillis());
                        event.put("source", source.isEmpty() ? "unknown" : source);
                        event.put("correlationId", correlationId.isEmpty() ? null : correlationId);
                        event.put("title", title.isEmpty() ? null : title);
                        event.put("code", code);
                        event.put("data", data);
                        event.put("deviceId", eventDeviceId.isEmpty() ? null : eventDeviceId);
                        event.put("includeStacktrace", includeStacktrace);
                        // Include destinationFilter to ensure event is sent to WebSocket and subscriptions
                        event.put("destinationFilter", Map.of("include", Arrays.asList(4, 7))); // LogDestination.Socket, LogDestination.Subscription
                        if (includeStacktrace) {
                            java.util.List<Map<String, String>> stackTrace = Arrays.stream(new Exception().getStackTrace())
                                    .map(ste -> Map.of("line", ste.toString()))
                                    .collect(Collectors.toList());
                            event.put("stacktrace", stackTrace);
                        }
                        String message = objectMapper.writeValueAsString(event);
                        boolean sentSuccessfully = false;
                        if (webSocketManager.isUseLocalServer()) {
                            ConcurrentHashMap<String, WebSocket> connections = webSocketManager.getServer().getActiveConnections();
                            if (connections != null) {
                                for (WebSocket conn : connections.values()) {
                                    if (conn.isOpen()) {
                                        try {
                                            conn.send(message);
                                            LOGGER.info("[NewEntryDialog] Sent new log event to client: " + conn.getRemoteSocketAddress());
                                            sentSuccessfully = true;
                                        } catch (Exception ex) {
                                            LOGGER.warning("[NewEntryDialog] Failed to send new log event to client: " + conn.getRemoteSocketAddress() + ", error: " + ex.getMessage());
                                        }
                                    }
                                }
                            }
                        } else if (webSocketManager.getClient() != null && webSocketManager.getClient().isOpen()) {
                            try {
                                webSocketManager.getClient().send(message);
                                LOGGER.info("[NewEntryDialog] Sent new log event via WebSocket client: deviceId=" + webSocketManager.getDeviceId());
                                sentSuccessfully = true;
                            } catch (Exception ex) {
                                LOGGER.warning("[NewEntryDialog] Failed to send new log event: deviceId=" + webSocketManager.getDeviceId() + ", error: " + ex.getMessage());
                            }
                        }
                        // Process the event locally to update the LogPane UI immediately
                        logPane.handleMessage(message); // Direct call to LogPane's handleMessage to update UI
                        if (sentSuccessfully || webSocketManager.isConnected()) {
                            dialog.dispose();
                            LOGGER.info("[NewEntryDialog] Submitted new log event: " + message);
                        } else {
                            Messages.showWarningDialog(project, "Could not send log event: WebSocket not connected.", "Warning");
                            LOGGER.warning("[NewEntryDialog] Failed to submit new log event: WebSocket not connected");
                        }
                    } catch (NumberFormatException ex) {
                        Messages.showErrorDialog(project, "Invalid code value: must be a number", "Error");
                        LOGGER.warning("[NewEntryDialog] Failed to submit new log event: invalid code, error=" + ex.getMessage());
                    } catch (Exception ex) {
                        Messages.showErrorDialog(project, "Failed to submit log event: " + ex.getMessage(), "Error");
                        LOGGER.severe("[NewEntryDialog] Failed to submit new log event: error=" + ex.getMessage());
                    }
                });

                cancelButton.addActionListener(e -> dialog.dispose());

                dialog.setVisible(true);
                LOGGER.info("[NewEntryDialog] Dialog set visible");
            } catch (Exception ex) {
                LOGGER.severe("[NewEntryDialog] Failed to initialize dialog: " + ex.getMessage());
                Messages.showErrorDialog(project, "Failed to open New Entry dialog: " + ex.getMessage(), "Error");
            }
        });
    }

    private void updateLayout(JPanel panel, GridBagLayout layout, GridBagConstraints gbc,
                              JLabel levelLabel, JComboBox<String> levelCombo, JLabel categoryLabel, JTextField categoryField,
                              JLabel codeLabel, JTextField codeField, JLabel titleLabel, JTextField titleField,
                              JLabel descLabel, JScrollPane descScroll, JLabel dataLabel, JScrollPane dataScroll,
                              JLabel sourceLabel, JTextField sourceField, JLabel deviceIdLabel, JTextField deviceIdField,
                              JLabel correlationIdLabel, JTextField correlationIdField,
                              JLabel stacktraceLabel, JCheckBox stacktraceCheck, JPanel buttonPanel,
                              JButton submitButton, JButton cancelButton, int width, int maxWidth) {
        int baseWidth = 400;
        gbc.gridy = 0;
        if (width >= baseWidth) {
            gbc.gridx = 0;
            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
            panel.add(levelLabel, gbc);
            gbc.gridx = 1;
            gbc.weightx = 0.5;
            panel.add(levelCombo, gbc);
            gbc.gridx = 2;
            gbc.weightx = 0.0;
            panel.add(categoryLabel, gbc);
            gbc.gridx = 3;
            gbc.weightx = 0.5;
            panel.add(categoryField, gbc);
            gbc.gridy++;
            gbc.weightx = 0.0;
        } else {
            gbc.gridx = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            panel.add(levelLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(levelCombo, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(categoryLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(categoryField, gbc);
            gbc.gridy++;
            gbc.weightx = 0.0;
        }
        if (width >= baseWidth) {
            gbc.gridx = 0;
            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
            panel.add(codeLabel, gbc);
            gbc.gridx = 1;
            gbc.weightx = 0.2;
            panel.add(codeField, gbc);
            gbc.gridx = 2;
            gbc.gridwidth = 2;
            gbc.weightx = 0.8;
            panel.add(titleLabel, gbc);
            gbc.gridx = 3;
            panel.add(titleField, gbc);
            gbc.gridy++;
            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
        } else {
            gbc.gridx = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            panel.add(codeLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(codeField, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(titleLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(titleField, gbc);
            gbc.gridy++;
            gbc.weightx = 0.0;
        }
        if (width >= baseWidth) {
            gbc.gridx = 0;
            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
            panel.add(descLabel, gbc);
            gbc.gridx = 1;
            gbc.weightx = 0.5;
            panel.add(descScroll, gbc);
            gbc.gridx = 2;
            gbc.weightx = 0.0;
            panel.add(dataLabel, gbc);
            gbc.gridx = 3;
            gbc.weightx = 0.5;
            panel.add(dataScroll, gbc);
            gbc.gridy++;
            gbc.weightx = 0.0;
        } else {
            gbc.gridx = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            panel.add(descLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(descScroll, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(dataLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(dataScroll, gbc);
            gbc.gridy++;
            gbc.weightx = 0.0;
        }
        if (width >= baseWidth) {
            gbc.gridx = 0;
            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
            panel.add(sourceLabel, gbc);
            gbc.gridx = 1;
            gbc.weightx = 0.33;
            panel.add(sourceField, gbc);
            gbc.gridx = 2;
            gbc.weightx = 0.0;
            panel.add(deviceIdLabel, gbc);
            gbc.gridx = 3;
            gbc.weightx = 0.33;
            panel.add(deviceIdField, gbc);
            gbc.gridx = 4;
            gbc.weightx = 0.0;
            panel.add(correlationIdLabel, gbc);
            gbc.gridx = 5;
            gbc.weightx = 0.33;
            panel.add(correlationIdField, gbc);
            gbc.gridy++;
            gbc.weightx = 0.0;
        } else {
            gbc.gridx = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            panel.add(sourceLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(sourceField, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(deviceIdLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(deviceIdField, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(correlationIdLabel, gbc);
            gbc.gridy++;
            gbc.gridx = 0;
            panel.add(correlationIdField, gbc);
            gbc.gridy++;
            gbc.weightx = 0.0;
        }
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        panel.add(stacktraceLabel, gbc);
        gbc.gridx = 1;
        panel.add(stacktraceCheck, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        buttonPanel.removeAll();
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, gbc);
    }
}