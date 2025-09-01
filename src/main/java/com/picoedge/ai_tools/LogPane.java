package com.picoedge.ai_tools;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ServerHandshake;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays; // Added for Arrays.stream
import java.util.Collections;
import java.util.Date; // Added for timestamp formatting
import java.util.HashSet; // Added for processedEventIds
import java.util.List; // Explicitly import java.util.List
import java.util.Map;
import java.util.Properties; // Added for envProps
import java.util.Set; // Added for processedEventIds
import java.util.UUID; // Added for WebSocketServerImpl
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
// Swing-based LogPane UI, ported from LogPane.tsx with local/remote server support
public class LogPane {
    private final JPanel content;
    private final JTextArea logArea;
    private final JComboBox<String> levelFilter;
    private final JComboBox<String> sourceFilter;
    private final JTextField categoryFilter;
    private final JCheckBox[] levelCheckBoxes;
    private final JList<String> sourceList;
    private final DefaultListModel<String> sourceModel;
    private final JButton connectButton;
    private final JButton settingsButton;
    private final JButton toggleStackButton;
    private final JButton clearButton;
    private final JButton copyButton;
    private final Project project;
    private final WebSocketServerImpl server;
    private WebSocketClient client;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final List<LogEvent> allLogs = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> processedEventIds = Collections.synchronizedSet(new HashSet<>());
    private final List<String> availableCategories = Collections.synchronizedList(new ArrayList<>(Collections.singletonList("general")));
    private final List<String> availableSources = Collections.synchronizedList(new ArrayList<>());
    private int maxLogs = 10000; // Default max logs
    private boolean defaultStackExpanded = false; // Start collapsed
    private int selectedLevels = 255; // All levels by default
    private String wsUrl = "ws://localhost:1065/ws"; // Default WebSocket URL
    private boolean useLocalServer = true; // Default to local server mode
    private final ObjectMapper objectMapper = new ObjectMapper(); // For JSON parsing
    private final Properties envProps = new Properties(); // For .env persistence
    private VirtualFile envFile; // Project .env file
    // Ported LogLevel enum
    public enum LogLevel {
        Null(0), Trace(1), Debug(2), Info(4), Notice(8), Warning(16), Error(32), Critical(64), Fatal(128);
        private final int value;
        LogLevel(int value) { this.value = value; }
        public int getValue() { return value; }
        public static String toString(int level) {
            switch (level) {
                case 1: return "Trace";
                case 2: return "Debug";
                case 4: return "Info";
                case 8: return "Notice";
                case 16: return "Warning";
                case 32: return "Error";
                case 64: return "Critical";
                case 128: return "Fatal";
                default: return "Unknown";
            }
        }
    }
    // Ported LogEvent structure, matching TypeScript LogEvent
    public static class LogEvent {
        String id;
        int level;
        String category;
        String description;
        long timestamp;
        String source;
        String correlationId;
        List<Map<String, Object>> stacktrace; // Updated to allow null values in map
        boolean stacktraceExpanded;
        String title;
        Integer code;
        Object data;
        String deviceId;
        Boolean includeStacktrace;
        LogEvent(String id, int level, String category, String description, long timestamp, String source, String correlationId, List<Map<String, Object>> stacktrace,
                 String title, Integer code, Object data, String deviceId, Boolean includeStacktrace) {
            this.id = id;
            this.level = level;
            this.category = category != null ? category : "general";
            this.description = description != null ? description : "";
            this.timestamp = timestamp;
            this.source = source != null ? source : "unknown";
            this.correlationId = correlationId;
            this.stacktrace = stacktrace;
            this.stacktraceExpanded = stacktrace != null && !stacktrace.isEmpty() ? false : false;
            this.title = title;
            this.code = code;
            this.data = data;
            this.deviceId = deviceId;
            this.includeStacktrace = includeStacktrace;
        }
    }
    // WebSocket server implementation
    private class WebSocketServerImpl extends WebSocketServer {
        private final ConcurrentHashMap<String, WebSocket> activeConnections = new ConcurrentHashMap<>();
        public WebSocketServerImpl(int port) {
            super(new InetSocketAddress("localhost", port));
        }
        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String subId = UUID.randomUUID().toString();
            activeConnections.put(subId, conn);
            System.out.println("[LogPane] WebSocket client connected: " + subId);
        }
        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            activeConnections.values().remove(conn);
            System.out.println("[LogPane] WebSocket client disconnected, code: " + code + ", reason: " + reason);
        }
        @Override
        public void onMessage(WebSocket conn, String message) {
            processMessage(message);
            activeConnections.values().forEach(client -> {
                if (client.isOpen()) client.send(message);
            });
        }
        @Override
        public void onError(WebSocket conn, Exception ex) {
            System.out.println("[LogPane] WebSocket server error: " + ex.getMessage());
        }
        @Override
        public void onStart() {
            System.out.println("[LogPane] WebSocket server started on port 1065");
            isConnected.set(true);
            updateConnectButton();
        }
        public void stopServer() {
            try {
                stop();
                isConnected.set(false);
                updateConnectButton();
            } catch (InterruptedException e) {
                System.out.println("[LogPane] Failed to stop server: " + e.getMessage());
            }
        }
    }
    // WebSocket client implementation
    private class WebSocketClientImpl extends WebSocketClient {
        public WebSocketClientImpl(URI serverUri) {
            super(serverUri);
        }
        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("[LogPane] WebSocket client connected to " + wsUrl);
            isConnected.set(true);
            updateConnectButton();
        }
        @Override
        public void onMessage(String message) {
            processMessage(message);
        }
        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("[LogPane] WebSocket client disconnected, code: " + code + ", reason: " + reason);
            isConnected.set(false);
            updateConnectButton();
        }
        @Override
        public void onError(Exception ex) {
            System.out.println("[LogPane] WebSocket client error: " + ex.getMessage());
            Messages.showMessageDialog(project, "WebSocket client error: " + ex.getMessage(), "Error", Messages.getErrorIcon());
        }
    }
    // Load settings from .env file
    private void loadSettings() {
        String basePath = project.getBasePath();
        if (basePath == null) return;
        VirtualFile projectDir = VfsUtil.findFileByIoFile(new java.io.File(basePath), true);
        if (projectDir == null) return;
        envFile = projectDir.findChild(".env");
        if (envFile != null && envFile.exists()) {
            try {
                envProps.load(new java.io.StringReader(new String(envFile.contentsToByteArray(), StandardCharsets.UTF_8)));
                String maxLogsStr = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_MAX_LOGS", "10000");
                String wsUrlStr = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_WS_URL", "ws://localhost:1065/ws");
                String useLocalServerStr = envProps.getProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_USE_LOCAL_SERVER", "true");
                maxLogs = Integer.parseInt(maxLogsStr);
                wsUrl = wsUrlStr;
                useLocalServer = Boolean.parseBoolean(useLocalServerStr);
            } catch (IOException | NumberFormatException e) {
                System.out.println("[LogPane] Failed to load .env settings: " + e.getMessage());
            }
        }
    }
    // Save settings to .env file
    private void saveSettings() {
        if (envFile == null || !envFile.exists()) {
            String basePath = project.getBasePath();
            if (basePath == null) return;
            VirtualFile projectDir = VfsUtil.findFileByIoFile(new java.io.File(basePath), true);
            if (projectDir == null) return;
            try {
                envFile = projectDir.createChildData(this, ".env");
                envFile.setBinaryContent(String.join("\n", FileProcessor.DEFAULT_ENV).getBytes(StandardCharsets.UTF_8));
                envProps.load(new java.io.StringReader(new String(envFile.contentsToByteArray(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                System.out.println("[LogPane] Failed to create .env file: " + e.getMessage());
                return;
            }
        }
        envProps.setProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_MAX_LOGS", String.valueOf(maxLogs));
        envProps.setProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_WS_URL", wsUrl);
        envProps.setProperty("COM_PICOEDGE_AI_TOOLS_LOGGER_USE_LOCAL_SERVER", String.valueOf(useLocalServer));
        StringBuilder envContent = new StringBuilder();
        for (String key : envProps.stringPropertyNames()) {
            envContent.append(key).append("=").append(envProps.getProperty(key)).append("\n");
        }
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                envFile.setBinaryContent(envContent.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.out.println("[LogPane] Failed to save .env settings: " + e.getMessage());
            }
        });
    }
    // Process incoming WebSocket message
    private void processMessage(String message) {
        try {
            // Parse JSON log event using Jackson
            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(message, Map.class);
            String id = (String) json.get("id");
            if (id == null || processedEventIds.contains(id)) {
                System.out.println("[LogPane] Skipping duplicate or invalid event: " + id);
                return;
            }
            processedEventIds.add(id);
            Integer level = (Integer) json.getOrDefault("level", LogLevel.Info.getValue());
            String category = (String) json.getOrDefault("category", "general");
            String description = (String) json.getOrDefault("description", "");
            String source = (String) json.getOrDefault("source", "unknown");
            String correlationId = (String) json.get("correlationId");
            Long timestamp = json.get("timestamp") instanceof Number ? ((Number) json.get("timestamp")).longValue() : System.currentTimeMillis();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stacktrace = json.get("stacktrace") instanceof List ? (List<Map<String, Object>>) json.get("stacktrace") : null;
            String title = (String) json.get("title");
            Integer code = json.get("code") instanceof Number ? ((Number) json.get("code")).intValue() : null;
            Object data = json.get("data");
            String deviceId = (String) json.get("deviceId");
            Boolean includeStacktrace = json.get("includeStacktrace") instanceof Boolean ? (Boolean) json.get("includeStacktrace") : null;
            LogEvent event = new LogEvent(id, level, category, description, timestamp, source, correlationId, stacktrace,
                    title, code, data, deviceId, includeStacktrace);
            synchronized (allLogs) {
                event.stacktraceExpanded = defaultStackExpanded;
                allLogs.add(event);
                if (allLogs.size() > maxLogs) allLogs.remove(0);
            }
            synchronized (availableCategories) {
                if (!availableCategories.contains(category)) {
                    availableCategories.add(category);
                    availableCategories.sort(String::compareTo);
                }
            }
            synchronized (availableSources) {
                if (!availableSources.contains(source)) {
                    availableSources.add(source);
                    sourceModel.addElement(source);
                    // Select new source automatically
                    sourceList.setSelectedIndex(sourceModel.size() - 1);
                    updateSourceFilterDisplay();
                }
            }
            updateLogDisplay();
        } catch (Exception e) {
            System.out.println("[LogPane] Failed to parse WebSocket message: " + e.getMessage());
        }
    }
    public LogPane(Project project) {
        this.project = project;
        this.server = new WebSocketServerImpl(1065);
        // Load settings from .env
        loadSettings();
        // Initialize UI components
        content = new JPanel(new BorderLayout());
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // Log level filter
        levelFilter = new JComboBox<>();
        levelFilter.setPreferredSize(new Dimension(150, 30));
        levelCheckBoxes = new JCheckBox[]{
                new JCheckBox("Trace"), new JCheckBox("Debug"), new JCheckBox("Info"),
                new JCheckBox("Notice"), new JCheckBox("Warning"), new JCheckBox("Error"),
                new JCheckBox("Critical"), new JCheckBox("Fatal")
        };
        Arrays.stream(levelCheckBoxes).forEach(cb -> cb.setSelected(true)); // All levels selected by default
        JPopupMenu levelPopup = new JPopupMenu();
        Arrays.stream(levelCheckBoxes).forEach(levelPopup::add);
        levelFilter.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                levelPopup.show(levelFilter, 0, levelFilter.getHeight());
            }
        });
        Arrays.stream(levelCheckBoxes).forEach(cb -> cb.addActionListener(e -> {
            updateLevelFilter();
            updateLogDisplay();
        }));
        // Source filter
        sourceModel = new DefaultListModel<>();
        sourceList = new JList<>(sourceModel);
        sourceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        sourceList.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                super.setSelectionInterval(index0, index1);
                updateLogDisplay();
            }
            @Override
            public void addSelectionInterval(int index0, int index1) {
                super.addSelectionInterval(index0, index1);
                updateLogDisplay();
            }
            @Override
            public void removeSelectionInterval(int index0, int index1) {
                super.removeSelectionInterval(index0, index1);
                updateLogDisplay();
            }
        });
        sourceFilter = new JComboBox<>();
        sourceFilter.setPreferredSize(new Dimension(150, 30));
        JPopupMenu sourcePopup = new JPopupMenu();
        JScrollPane sourceScroll = new JScrollPane(sourceList);
        sourceScroll.setPreferredSize(new Dimension(150, 150));
        sourcePopup.add(sourceScroll);
        sourceFilter.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                sourcePopup.show(sourceFilter, 0, sourceFilter.getHeight());
            }
        });
        // Initialize source list with all sources selected
        synchronized (availableSources) {
            if (!availableSources.isEmpty()) {
                availableSources.forEach(sourceModel::addElement);
                sourceList.setSelectionInterval(0, sourceModel.size() - 1);
            }
        }
        // Category filter
        categoryFilter = new JTextField(15);
        categoryFilter.setPreferredSize(new Dimension(150, 30));
        categoryFilter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                updateLogDisplay();
            }
        });
        // Buttons
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> toggleConnection());
        settingsButton = new JButton("⚙️");
        settingsButton.addActionListener(e -> showSettingsDialog());
        toggleStackButton = new JButton("📚");
        toggleStackButton.addActionListener(e -> toggleAllStacks());
        clearButton = new JButton("🗑️");
        clearButton.addActionListener(e -> clearLogs());
        copyButton = new JButton("📋");
        copyButton.addActionListener(e -> copyLogs());
        // Add components to filter panel
        filterPanel.add(new JLabel("Level:"));
        filterPanel.add(levelFilter);
        filterPanel.add(new JLabel("Source:"));
        filterPanel.add(sourceFilter);
        filterPanel.add(new JLabel("Category:"));
        filterPanel.add(categoryFilter);
        filterPanel.add(settingsButton);
        filterPanel.add(toggleStackButton);
        filterPanel.add(clearButton);
        filterPanel.add(copyButton);
        filterPanel.add(connectButton);
        // Log display area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(Color.WHITE);
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // Auto-scroll
        JScrollPane scrollPane = new JScrollPane(logArea);
        // Add components to main panel
        content.add(filterPanel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        // Update initial UI
        updateLevelFilter();
        updateSourceFilterDisplay();
        updateConnectButton();
    }
    public JComponent getContent() {
        return content;
    }
    // Check if a port is available
    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    private void toggleConnection() {
        if (isConnected.get()) {
            if (useLocalServer) {
                server.stopServer();
            } else if (client != null) {
                client.close();
                client = null;
            }
            isConnected.set(false);
            updateConnectButton();
        } else {
            if (useLocalServer) {
                if (!isPortAvailable(1065)) {
                    Messages.showMessageDialog(project,
                            "Port 1065 is already in use. Please free the port or connect to an existing server.",
                            "Error", Messages.getErrorIcon());
                    return;
                }
                try {
                    server.start();
                } catch (Exception e) {
                    Messages.showMessageDialog(project, "Failed to start WebSocket server: " + e.getMessage(), "Error", Messages.getErrorIcon());
                }
            } else {
                try {
                    client = new WebSocketClientImpl(new URI(wsUrl));
                    client.connect();
                } catch (Exception e) {
                    Messages.showMessageDialog(project, "Failed to connect to WebSocket server: " + e.getMessage(), "Error", Messages.getErrorIcon());
                }
            }
        }
    }
    private void updateConnectButton() {
        connectButton.setText(isConnected.get() ? "Disconnect" : "Connect");
        connectButton.setBackground(isConnected.get() ? new Color(0, 128, 0) : new Color(128, 128, 128));
    }
    private void updateLevelFilter() {
        selectedLevels = 0;
        for (int i = 0; i < levelCheckBoxes.length; i++) {
            if (levelCheckBoxes[i].isSelected()) {
                selectedLevels |= LogLevel.values()[i + 1].getValue();
            }
        }
        levelFilter.removeAllItems();
        levelFilter.addItem(selectedLevels == 255 ? "All Log Levels" : getLevelDisplay());
    }
    private void updateSourceFilterDisplay() {
        sourceFilter.removeAllItems();
        int selectedCount = sourceList.getSelectedIndices().length;
        if (selectedCount == sourceModel.size() && !sourceModel.isEmpty()) {
            sourceFilter.addItem("All Sources");
        } else if (selectedCount == 0) {
            sourceFilter.addItem("None");
        } else {
            sourceFilter.addItem(String.join(",", sourceList.getSelectedValuesList()));
        }
    }
    private String getLevelDisplay() {
        if (selectedLevels == 0) return "None";
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < levelCheckBoxes.length; i++) {
            if (levelCheckBoxes[i].isSelected()) {
                selected.add(levelCheckBoxes[i].getText());
            }
        }
        return selected.isEmpty() ? "None" : String.join(",", selected);
    }
    private void updateLogDisplay() {
        StringBuilder display = new StringBuilder();
        String category = categoryFilter.getText().trim();
        synchronized (allLogs) {
            for (LogEvent event : allLogs) {
                // Apply level filter: return no logs if no levels selected
                if (selectedLevels != 0 && (event.level & selectedLevels) == 0) continue;
                // Apply source filter: return no logs if no sources selected
                if (!sourceList.getSelectedValuesList().isEmpty() && !sourceList.getSelectedValuesList().contains(event.source)) continue;
                // Apply category filter: only filter if category is non-empty, allowing all logs when blank
                if (!category.isEmpty() && !event.category.startsWith(category)) continue;
                display.append(String.format("[%s] [%s] [%s] [%s] [%s] : %s %s\n",
                        event.id, event.category, event.correlationId,
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(event.timestamp)),
                        LogLevel.toString(event.level), event.title != null ? event.title : "", event.description));
                if (event.stacktraceExpanded && event.stacktrace != null && !event.stacktrace.isEmpty()) {
                    for (Map<String, Object> frame : event.stacktrace) {
                        Object lineObj = frame.get("line");
                        String line = lineObj instanceof String ? (String) lineObj : (lineObj != null ? lineObj.toString() : "");
                        display.append(" ").append(line).append("\n");
                    }
                }
            }
        }
        logArea.setText(display.toString());
    }
    private void toggleAllStacks() {
        defaultStackExpanded = !defaultStackExpanded;
        synchronized (allLogs) {
            for (LogEvent event : allLogs) {
                if (event.stacktrace != null && !event.stacktrace.isEmpty()) {
                    event.stacktraceExpanded = defaultStackExpanded;
                }
            }
        }
        updateLogDisplay();
        toggleStackButton.setBackground(defaultStackExpanded ? new Color(76, 175, 80) : new Color(42, 42, 42));
    }
    private void clearLogs() {
        synchronized (allLogs) {
            allLogs.clear();
        }
        processedEventIds.clear();
        sourceModel.clear();
        availableSources.clear();
        sourceList.clearSelection();
        updateSourceFilterDisplay();
        updateLogDisplay();
    }
    private void copyLogs() {
        StringBuilder logText = new StringBuilder();
        String category = categoryFilter.getText().trim();
        synchronized (allLogs) {
            for (LogEvent event : allLogs) {
                // Apply level filter: return no logs if no levels selected
                if (selectedLevels != 0 && (event.level & selectedLevels) == 0) continue;
                // Apply source filter: return no logs if no sources selected
                if (!sourceList.getSelectedValuesList().isEmpty() && !sourceList.getSelectedValuesList().contains(event.source)) continue;
                // Apply category filter: only filter if category is non-empty, allowing all logs when blank
                if (!category.isEmpty() && !event.category.startsWith(category)) continue;
                logText.append(String.format("[%s] [%s] [%s] [%s] [%s] : %s %s\n",
                        event.id, event.category, event.correlationId,
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(event.timestamp)),
                        LogLevel.toString(event.level), event.title != null ? event.title : "", event.description));
                if (event.stacktraceExpanded && event.stacktrace != null && !event.stacktrace.isEmpty()) {
                    for (Map<String, Object> frame : event.stacktrace) {
                        Object lineObj = frame.get("line");
                        String line = lineObj instanceof String ? (String) lineObj : (lineObj != null ? lineObj.toString() : "");
                        logText.append(" ").append(line).append("\n");
                    }
                }
            }
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(logText.toString()), null);
    }
    private void showSettingsDialog() {
        JDialog settingsDialog = new JDialog((Frame) null, "Log Settings", true);
        settingsDialog.setLayout(new GridLayout(4, 2));
        JTextField maxLogsField = new JTextField(String.valueOf(maxLogs));
        JTextField wsUrlField = new JTextField(wsUrl);
        JCheckBox localServerCheckBox = new JCheckBox("Use Local Server", useLocalServer);
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
                    maxLogs = newMaxLogs;
                    synchronized (allLogs) {
                        while (allLogs.size() > maxLogs) {
                            allLogs.remove(0);
                        }
                    }
                    updateLogDisplay();
                }
                String newWsUrl = wsUrlField.getText().trim();
                if (!newWsUrl.isEmpty()) {
                    wsUrl = newWsUrl;
                }
                useLocalServer = localServerCheckBox.isSelected();
                saveSettings(); // Persist settings to .env
                settingsDialog.dispose();
            } catch (NumberFormatException ex) {
                Messages.showMessageDialog(project, "Invalid number for max logs", "Error", Messages.getErrorIcon());
            }
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> settingsDialog.dispose());
        settingsDialog.add(cancelButton);
        settingsDialog.add(saveButton);
        settingsDialog.setSize(300, 200);
        settingsDialog.setLocationRelativeTo(content);
        settingsDialog.setVisible(true);
    }
}