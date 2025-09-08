package com.picoedge.ai_tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

public class LogPane {
    private final Project project;
    private final WebSocketManager webSocketManager;
    private final LogProcessor logProcessor;
    private final LogFilter logFilter;
    private final LogDisplay logDisplay;
    private final Properties envProps = new Properties();
    private VirtualFile envFile;
    private LogPaneUI ui = null;

    public LogPane(Project project) {
        this.project = project;
        this.logProcessor = new LogProcessor(this::updateUI);
        this.logFilter = new LogFilter();
        this.logDisplay = new LogDisplay();
        this.webSocketManager = new WebSocketManager(project, this::handleMessage, this::updateUI, logProcessor);
        this.ui = new LogPaneUI(
                this::handleHyperlink,
                this::updateUI,
                this::updateUI,
                this::updateUI,
                this::updateUI,
                this::updateUI,
                webSocketManager::toggleConnection,
                () -> new NewEntryDialog(project, webSocketManager, logProcessor, this).show(),
                () -> new SettingsDialog(project, envFile, envProps, logProcessor, webSocketManager).show(),
                () -> logProcessor.setDefaultStackExpanded(!logProcessor.isDefaultStackExpanded()),
                () -> logProcessor.setDefaultDataExpanded(!logProcessor.isDefaultDataExpanded()),
                () -> { logProcessor.clearLogs(); updateUI(); },
                () -> logDisplay.copyLogs(new ArrayList<>(logFilter.filterLogs(
                        logProcessor.getAllLogs(),
                        convertToSet(ui.getSelectedLevels()),
                        new HashSet<>(ui.getSelectedSources()),
                        ui.isAllSourcesSelected(),
                        ui.getCategoryFilter(),
                        ui.getSearchQuery(),
                        logFilter.getDeviceIdFilter(),
                        logFilter.getCorrelationIdFilter(),
                        convertTimeRange(ui.getTimeRange())))),
                deviceId -> {
                    logFilter.setDeviceIdFilter(deviceId);
                    updateUI();
                },
                correlationId -> {
                    logFilter.setCorrelationIdFilter(correlationId);
                    updateUI();
                },
                logFilter // Pass LogFilter instance to LogPaneUI
        );
        loadSettings();
        updateUI();
    }

    public JComponent getContent() {
        return ui != null ? ui.getContent() : new JPanel();
    }

    private void loadSettings() {
        String basePath = project.getBasePath();
        if (basePath == null) {
            System.out.println("[LogPane] Project base path is null, skipping .env load");
            return;
        }
        VirtualFile projectDir = VfsUtil.findFileByIoFile(new java.io.File(basePath), true);
        if (projectDir == null) {
            System.out.println("[LogPane] Project directory not found, skipping .env load");
            return;
        }
        envFile = projectDir.findChild(".env");
        if (envFile != null && envFile.exists()) {
            try {
                envProps.load(new java.io.StringReader(new String(envFile.contentsToByteArray(), StandardCharsets.UTF_8)));
                String maxLogsStr = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_MAX_LOGS", "10000");
                String wsUrlStr = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_WS_URL", "ws://localhost:1065");
                String useLocalServerStr = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_USE_LOCAL_SERVER", "false");
                logProcessor.setMaxLogs(Integer.parseInt(maxLogsStr));
                webSocketManager.setWsUrl(wsUrlStr);
                webSocketManager.setUseLocalServer(Boolean.parseBoolean(useLocalServerStr));
                System.out.println("[LogPane] Loaded .env settings: maxLogs=" + maxLogsStr + ", wsUrl=" + wsUrlStr + ", useLocalServer=" + useLocalServerStr);
            } catch (IOException | NumberFormatException e) {
                System.out.println("[LogPane] Failed to load .env settings: error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
            }
        } else {
            System.out.println("[LogPane] .env file not found, using defaults: maxLogs=10000, wsUrl=ws://localhost:1065, useLocalServer=false");
            webSocketManager.setWsUrl("ws://localhost:1065");
            webSocketManager.setUseLocalServer(false);
        }
    }

    public void handleMessage(String message) {
        System.out.println("[LogPane] Received message for processing: " + message);
        try {
            synchronized (logProcessor) {
                logProcessor.processMessage(message, category -> {
                    System.out.println("[LogPane] New category detected: " + category);
                    if (ui != null) ApplicationManager.getApplication().invokeLater(() -> {
                        ui.addSource(category);
                        updateUI();
                    });
                }, source -> {
                    System.out.println("[LogPane] New source detected: " + source);
                    if (ui != null) ApplicationManager.getApplication().invokeLater(() -> {
                        ui.addSource(source);
                        updateUI();
                    });
                });
            }
            System.out.println("[LogPane] Message processed, triggering UI update");
            ApplicationManager.getApplication().invokeLater(this::updateUI);
        } catch (Exception e) {
            System.out.println("[LogPane] Error processing message: " + message + ", error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void handleHyperlink(String url) {
        if (ui != null) {
            if (url.startsWith("stack:")) {
                String eventId = url.substring("stack:".length());
                logProcessor.toggleStack(eventId);
                updateUI();
            } else if (url.startsWith("data:")) {
                String eventId = url.substring("data:".length());
                logProcessor.toggleData(eventId);
                updateUI();
            } else if (url.startsWith("category:")) {
                String category = url.substring("category:".length());
                ui.setCategoryFilter(category);
                updateUI();
            } else if (url.startsWith("source:")) {
                String source = url.substring("source:".length());
                ui.setSourceFilter(source);
                updateUI();
            } else if (url.startsWith("deviceId:")) {
                String deviceId = url.substring("deviceId:".length());
                logFilter.setDeviceIdFilter(deviceId);
                updateUI();
            } else if (url.startsWith("correlationId:")) {
                String correlationId = url.substring("correlationId:".length());
                logFilter.setCorrelationIdFilter(correlationId);
                updateUI();
            } else if (url.startsWith("level:")) {
                String levelStr = url.substring("level:".length());
                int level = LogProcessor.LogLevel.valueOf(levelStr).getValue();
                ui.setLevelFilter(level);
                updateUI();
            }
        }
    }

    private void updateUI() {
        if (ui != null) {
            System.out.println("[LogPane] Updating UI with logs");
            ApplicationManager.getApplication().invokeLater(() -> {
                ui.setLogContent(logDisplay.generateLogHtml(new ArrayList<>(logFilter.filterLogs(
                        logProcessor.getAllLogs(),
                        convertToSet(ui.getSelectedLevels()),
                        new HashSet<>(ui.getSelectedSources()),
                        ui.isAllSourcesSelected(),
                        ui.getCategoryFilter(),
                        ui.getSearchQuery(),
                        logFilter.getDeviceIdFilter(),
                        logFilter.getCorrelationIdFilter(),
                        convertTimeRange(ui.getTimeRange())))));
                ui.updateConnectionStatus(webSocketManager.isConnected());
                ui.updateStackButton(logProcessor.isDefaultStackExpanded());
                ui.updateDataButton(logProcessor.isDefaultDataExpanded());
                ui.updateClearDeviceIdButton(logFilter.getDeviceIdFilter());
                ui.updateClearCorrelationIdButton(logFilter.getCorrelationIdFilter());
            });
        }
    }

    private Set<Integer> convertToSet(int levelMask) {
        Set<Integer> levels = new HashSet<>();
        if (levelMask == 255) {
            for (LogProcessor.LogLevel level : LogProcessor.LogLevel.values()) {
                levels.add(level.getValue());
            }
        } else {
            for (LogProcessor.LogLevel level : LogProcessor.LogLevel.values()) {
                if ((levelMask & level.getValue()) != 0) {
                    levels.add(level.getValue());
                }
            }
        }
        return levels;
    }

    private long[] convertTimeRange(String timeRange) {
        long currentTime = System.currentTimeMillis();
        long[] range = new long[2];
        range[1] = currentTime;
        switch (timeRange) {
            case "Last 5 Minutes":
                range[0] = currentTime - (5 * 60 * 1000);
                break;
            case "Last 1 Hour":
                range[0] = currentTime - (1 * 60 * 60 * 1000);
                break;
            case "Last 24 Hours":
                range[0] = currentTime - (24 * 60 * 60 * 1000);
                break;
            case "Last 7 Days":
                range[0] = currentTime - (7 * 24 * 60 * 1000);
                break;
            case "All Time":
            default:
                range[0] = 0;
                break;
        }
        System.out.println("[LogPane] Converted time range: " + timeRange + " to [" + range[0] + ", " + range[1] + "]");
        return range;
    }
}