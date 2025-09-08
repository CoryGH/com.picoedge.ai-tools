package com.picoedge.ai_tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SettingsDialog {
    private final Project project;
    private final VirtualFile envFile;
    private final Properties envProps;
    private final LogProcessor logProcessor;
    private final WebSocketManager webSocketManager;

    public SettingsDialog(Project project, VirtualFile envFile, Properties envProps, LogProcessor logProcessor, WebSocketManager webSocketManager) {
        this.project = project;
        this.envFile = envFile;
        this.envProps = envProps;
        this.logProcessor = logProcessor;
        this.webSocketManager = webSocketManager;
    }

    public void show() {
        JDialog settingsDialog = new JDialog((Frame) null, "Log Settings", true);
        settingsDialog.setLayout(new GridLayout(4, 2));
        JTextField maxLogsField = new JTextField(String.valueOf(logProcessor.getMaxLogs()));
        JTextField wsUrlField = new JTextField(webSocketManager.getWsUrl());
        JCheckBox localServerCheckBox = new JCheckBox("Use Local Server", webSocketManager.isUseLocalServer());
        settingsDialog.add(new JLabel("Maximum Logs:"));
        settingsDialog.add(maxLogsField);
        settingsDialog.add(new JLabel("WebSocket URL:"));
        settingsDialog.add(wsUrlField);
        settingsDialog.add(new JLabel("Server Mode:"));
        settingsDialog.add(localServerCheckBox);
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            try {
                int newMaxLogs = Integer.parseInt(maxLogsField.getText().trim());
                if (newMaxLogs > 0) {
                    logProcessor.setMaxLogs(newMaxLogs);
                    System.out.println("[SettingsDialog] Updated maxLogs to " + newMaxLogs);
                }
                String newWsUrl = wsUrlField.getText().trim();
                if (!newWsUrl.isEmpty()) {
                    webSocketManager.setWsUrl(newWsUrl);
                    System.out.println("[SettingsDialog] Updated wsUrl to " + newWsUrl);
                }
                boolean newUseLocalServer = localServerCheckBox.isSelected();
                if (newUseLocalServer != webSocketManager.isUseLocalServer()) {
                    webSocketManager.setUseLocalServer(newUseLocalServer);
                    webSocketManager.toggleConnection();
                    webSocketManager.toggleConnection();
                    System.out.println("[SettingsDialog] Updated useLocalServer to " + newUseLocalServer);
                }
                saveSettings();
                settingsDialog.dispose();
            } catch (NumberFormatException ex) {
                Messages.showErrorDialog(project, "Invalid number for max logs", "Error");
                System.out.println("[SettingsDialog] Failed to save settings: Invalid max logs, error=" + ex.getMessage());
            }
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> settingsDialog.dispose());
        settingsDialog.add(cancelButton);
        settingsDialog.add(saveButton);
        settingsDialog.setSize(300, 200);
        settingsDialog.setLocationRelativeTo(null);
        settingsDialog.setVisible(true);
    }

    private void saveSettings() {
        envProps.setProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_MAX_LOGS", String.valueOf(logProcessor.getMaxLogs()));
        envProps.setProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_WS_URL", webSocketManager.getWsUrl());
        envProps.setProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_USE_LOCAL_SERVER", String.valueOf(webSocketManager.isUseLocalServer()));
        StringBuilder envContent = new StringBuilder();
        for (String key : envProps.stringPropertyNames()) {
            envContent.append(key).append("=").append(envProps.getProperty(key)).append("\n");
        }
        if (envFile != null) {
            SwingUtilities.invokeLater(() -> {
                try {
                    envFile.setBinaryContent(envContent.toString().getBytes(StandardCharsets.UTF_8));
                    System.out.println("[SettingsDialog] Saved .env settings");
                } catch (IOException e) {
                    System.out.println("[SettingsDialog] Failed to save .env settings: error=" + e.getMessage());
                }
            });
        }
    }
}