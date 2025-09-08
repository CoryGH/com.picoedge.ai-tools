package com.picoedge.ai_tools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ServerHandshake;
import javax.swing.Timer;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WebSocketManager {
    private final Project project;
    private final WebSocketServerImpl server;
    private WebSocketClient client;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private String wsUrl = "ws://localhost:1065";
    private boolean useLocalServer = false;
    private final String deviceId;
    private String deviceIdFilter = "";
    private String correlationIdFilter = "";
    private int reconnectAttempts = 0;
    private String wsSubId;
    private final int maxReconnectAttempts = 5;
    private final int baseReconnectDelay = 1000;
    private final int maxReconnectDelay = 10000;
    private Timer messageTimeoutTimer;
    private final int messageTimeoutMs = 30000; // 30 seconds timeout for no messages
    private final LogProcessor logProcessor; // Reference to LogProcessor for clearing processed IDs
    private final ConcurrentHashMap<String, WebSocketClient> activeClients = new ConcurrentHashMap<>(); // Track active clients by deviceId
    private final Object connectionLock = new Object(); // Lock for connection initialization

    public static class WebSocketServerImpl extends WebSocketServer {
        private final ConcurrentHashMap<String, WebSocket> activeConnections = new ConcurrentHashMap<>();
        private final Consumer<String> messageHandler;

        public WebSocketServerImpl(int port, Consumer<String> messageHandler) {
            super(new InetSocketAddress("localhost", port));
            this.messageHandler = messageHandler;
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String subId = UUID.randomUUID().toString();
            activeConnections.put(subId, conn);
            System.out.println("[WebSocketManager] WebSocket client connected: subId=" + subId + ", remoteAddress=" + conn.getRemoteSocketAddress());
            try {
                conn.send("{\"action\":\"subscribe\",\"filter\":{\"level\":255,\"category\":[],\"excludeCategory\":[]},\"subId\":\"" + subId + "\"}");
                System.out.println("[WebSocketManager] Sent subscription message for subId: " + subId);
            } catch (Exception e) {
                System.out.println("[WebSocketManager] Failed to send subscription message for subId: " + subId + ", error=" + e.getMessage());
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            activeConnections.values().remove(conn);
            System.out.println("[WebSocketManager] WebSocket client disconnected, code: " + code + ", reason: " + reason + ", remoteAddress=" + conn.getRemoteSocketAddress());
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            System.out.println("[WebSocketManager] Received WebSocket message: " + message + ", from: " + conn.getRemoteSocketAddress());
            messageHandler.accept(message);
            activeConnections.values().forEach(client -> {
                if (client.isOpen() && client != conn) {
                    try {
                        client.send(message);
                        System.out.println("[WebSocketManager] Forwarded message to client: " + client.getRemoteSocketAddress());
                    } catch (Exception e) {
                        System.out.println("[WebSocketManager] Failed to forward message to client: " + client.getRemoteSocketAddress() + ", error: " + e.getMessage());
                    }
                }
            });
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            System.out.println("[WebSocketManager] WebSocket server error: " + ex.getMessage() + (conn != null ? ", client: " + conn.getRemoteSocketAddress() : ""));
            if (conn != null) {
                activeConnections.values().remove(conn);
            }
        }

        @Override
        public void onStart() {
            System.out.println("[WebSocketManager] WebSocket server started on port 1065");
        }

        public void stopServer() {
            try {
                stop();
                System.out.println("[WebSocketManager] WebSocket server stopped");
            } catch (InterruptedException e) {
                System.out.println("[WebSocketManager] Failed to stop server: error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
            }
        }

        public ConcurrentHashMap<String, WebSocket> getActiveConnections() {
            return activeConnections;
        }
    }

    private class WebSocketClientImpl extends WebSocketClient {
        private final String deviceId;
        private final Consumer<String> messageHandler;
        private Timer pingTimer;
        private boolean isSubscribed = false;

        public WebSocketClientImpl(URI serverUri, String deviceId, Consumer<String> messageHandler) {
            super(serverUri);
            this.deviceId = deviceId;
            this.messageHandler = messageHandler;
            System.out.println("[WebSocketManager] Initializing WebSocket client: deviceId=" + deviceId + ", uri=" + serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("[WebSocketManager] WebSocket client connected: deviceId=" + deviceId + ", uri=" + getURI() + ", status=" + handshakedata.getHttpStatus() + ", extensions=" + handshakedata.getFieldValue("Sec-WebSocket-Extensions"));
            synchronized (connectionLock) {
                isConnected.set(true);
                reconnectAttempts = 0;
                wsSubId = UUID.randomUUID().toString();
                logProcessor.clearProcessedEventIds(wsSubId); // Clear deduplication for new subscription
                logProcessor.setCurrentSubId(wsSubId); // Set current subscription ID
                activeClients.put(deviceId, this); // Track this client
                System.out.println("[WebSocketManager] Active clients: " + activeClients.size() + ", deviceId=" + deviceId);
                sendSubscriptionMessage();
            }
            startPingTimer();
            startMessageTimeoutTimer();
        }

        private void sendSubscriptionMessage() {
            if (isSubscribed) {
                System.out.println("[WebSocketManager] Already subscribed, skipping: subId=" + wsSubId + ", deviceId=" + deviceId);
                return;
            }
            try {
                String subscriptionMessage = "{\"action\":\"subscribe\",\"filter\":{\"level\":255,\"category\":[\"test.websocket\"],\"excludeCategory\":[\"logger.server\",\"logger.server.forward\"]},\"subId\":\"" + wsSubId + "\",\"deviceId\":\"" + deviceId + "\"}";
                send(subscriptionMessage);
                isSubscribed = true;
                System.out.println("[WebSocketManager] Sent WebSocket subscription: subId=" + wsSubId + ", deviceId=" + deviceId + ", message=" + subscriptionMessage);
            } catch (Exception e) {
                isSubscribed = false;
                System.out.println("[WebSocketManager] Failed to send subscription message: subId=" + wsSubId + ", deviceId=" + deviceId + ", error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
            }
        }

        private void startPingTimer() {
            pingTimer = new Timer(10000, ee -> {
                try {
                    sendPing();
                    System.out.println("[WebSocketManager] Sent ping to server: deviceId=" + deviceId);
                } catch (Exception ex) {
                    System.out.println("[WebSocketManager] Failed to send ping: deviceId=" + deviceId + ", error=" + ex.getMessage());
                }
            });
            pingTimer.start();
        }

        private void startMessageTimeoutTimer() {
            if (messageTimeoutTimer != null) {
                messageTimeoutTimer.stop();
            }
            messageTimeoutTimer = new Timer(messageTimeoutMs, e -> {
                System.out.println("[WebSocketManager] No messages received for " + messageTimeoutMs + "ms, forcing reconnection: deviceId=" + deviceId);
                reconnect();
            });
            messageTimeoutTimer.setRepeats(false);
            messageTimeoutTimer.start();
        }

        @Override
        public void onMessage(String message) {
            System.out.println("[WebSocketManager] Received WebSocket message: deviceId=" + deviceId + ", subId=" + wsSubId + ", message=" + message);
            startMessageTimeoutTimer(); // Reset timeout on message receipt
            try {
                messageHandler.accept(message);
            } catch (Exception e) {
                System.out.println("[WebSocketManager] Error processing message: deviceId=" + deviceId + ", subId=" + wsSubId + ", error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("[WebSocketManager] WebSocket client disconnected: deviceId=" + deviceId + ", subId=" + wsSubId + ", code=" + code + ", reason=" + reason + ", remote=" + remote + ", uri=" + getURI());
            synchronized (connectionLock) {
                if (pingTimer != null) {
                    pingTimer.stop();
                    pingTimer = null;
                }
                if (messageTimeoutTimer != null) {
                    messageTimeoutTimer.stop();
                    messageTimeoutTimer = null;
                }
                isConnected.set(false);
                isSubscribed = false;
                activeClients.remove(deviceId, this); // Remove from active clients
                System.out.println("[WebSocketManager] Removed client from active clients: deviceId=" + deviceId + ", remaining=" + activeClients.size());
                if (reconnectAttempts < maxReconnectAttempts && code != 1001) {
                    retryWebSocket();
                } else {
                    System.out.println("[WebSocketManager] Max reconnect attempts reached or intentional close: deviceId=" + deviceId);
                }
            }
        }

        @Override
        public void onError(Exception ex) {
            System.out.println("[WebSocketManager] WebSocket client error: deviceId=" + deviceId + ", subId=" + wsSubId + ", error=" + ex.getMessage() + ", stack=" + Arrays.toString(ex.getStackTrace()) + ", uri=" + getURI());
            Messages.showErrorDialog(project, "WebSocket client error: " + ex.getMessage(), "Error");
            synchronized (connectionLock) {
                isConnected.set(false);
                isSubscribed = false;
                if (pingTimer != null) {
                    pingTimer.stop();
                    pingTimer = null;
                }
                if (messageTimeoutTimer != null) {
                    messageTimeoutTimer.stop();
                    messageTimeoutTimer = null;
                }
                activeClients.remove(deviceId, this); // Remove from active clients
                System.out.println("[WebSocketManager] Removed client from active clients due to error: deviceId=" + deviceId + ", remaining=" + activeClients.size());
                if (reconnectAttempts < maxReconnectAttempts) {
                    retryWebSocket();
                } else {
                    System.out.println("[WebSocketManager] Max reconnect attempts reached: deviceId=" + deviceId);
                }
            }
        }

        // Resubscribe after reconnection to ensure filter is applied
        public void resubscribe() {
            isSubscribed = false;
            sendSubscriptionMessage();
        }
    }

    public WebSocketManager(Project project, Consumer<String> messageHandler, Runnable connectionStatusChanged, LogProcessor logProcessor) {
        this.project = project;
        this.deviceId = UUID.randomUUID().toString();
        this.server = new WebSocketServerImpl(1065, messageHandler);
        this.logProcessor = logProcessor;
        initializeConnection();
    }

    private void initializeConnection() {
        synchronized (connectionLock) {
            System.out.println("[WebSocketManager] Initializing connection: useLocalServer=" + useLocalServer + ", deviceId=" + deviceId + ", wsUrl=" + wsUrl);
            if (useLocalServer) {
                if (isPortAvailable(1065)) {
                    try {
                        server.start();
                        isConnected.set(true);
                        System.out.println("[WebSocketManager] Started local WebSocket server on port 1065");
                    } catch (Exception e) {
                        System.out.println("[WebSocketManager] Failed to start local WebSocket server: error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
                    }
                } else {
                    Messages.showErrorDialog(project, "Port 1065 is already in use. Please free the port or connect to an existing server.", "Error");
                    System.out.println("[WebSocketManager] Failed to start local server: Port 1065 in use");
                }
            } else {
                if (checkServerAvailability()) {
                    try {
                        // Close all existing clients for this deviceId
                        closeExistingClient();
                        String fullWsUrl = wsUrl + "/ws?deviceId=" + deviceId;
                        System.out.println("[WebSocketManager] Creating WebSocket client: fullWsUrl=" + fullWsUrl + ", deviceId=" + deviceId);
                        client = new WebSocketClientImpl(new URI(fullWsUrl), deviceId, server.messageHandler);
                        activeClients.put(deviceId, client); // Track new client
                        System.out.println("[WebSocketManager] Active clients after creation: " + activeClients.size() + ", deviceId=" + deviceId);
                        client.connect();
                        System.out.println("[WebSocketManager] Initiated WebSocket client connection to " + fullWsUrl + ", deviceId=" + deviceId);
                    } catch (Exception e) {
                        System.out.println("[WebSocketManager] Failed to create or connect WebSocket client: deviceId=" + deviceId + ", fullWsUrl=" + wsUrl + "/ws?deviceId=" + deviceId + ", error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
                        retryWebSocket();
                    }
                } else {
                    System.out.println("[WebSocketManager] Server not available at " + wsUrl + ", scheduling retry: deviceId=" + deviceId);
                    retryWebSocket();
                }
            }
        }
    }

    private void closeExistingClient() {
        synchronized (connectionLock) {
            WebSocketClient existingClient = activeClients.remove(deviceId);
            if (existingClient != null && !existingClient.isClosed()) {
                try {
                    existingClient.close();
                    System.out.println("[WebSocketManager] Closed existing WebSocket client: deviceId=" + deviceId);
                } catch (Exception e) {
                    System.out.println("[WebSocketManager] Failed to close existing WebSocket client: deviceId=" + deviceId + ", error=" + e.getMessage());
                }
            }
            if (client != null && !client.isClosed()) {
                try {
                    client.close();
                    System.out.println("[WebSocketManager] Closed current WebSocket client: deviceId=" + deviceId);
                } catch (Exception e) {
                    System.out.println("[WebSocketManager] Failed to close current WebSocket client: deviceId=" + deviceId + ", error=" + e.getMessage());
                }
                client = null;
            }
            System.out.println("[WebSocketManager] Active clients after close: " + activeClients.size() + ", deviceId=" + deviceId);
        }
    }

    public void setWsUrl(String wsUrl) {
        synchronized (connectionLock) {
            this.wsUrl = wsUrl;
        }
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setUseLocalServer(boolean useLocalServer) {
        synchronized (connectionLock) {
            if (this.useLocalServer != useLocalServer) {
                this.useLocalServer = useLocalServer;
                if (isConnected.get()) {
                    toggleConnection();
                    initializeConnection();
                }
            }
        }
    }

    public boolean isUseLocalServer() {
        return useLocalServer;
    }

    public void setDeviceIdFilter(String deviceIdFilter) {
        this.deviceIdFilter = deviceIdFilter;
    }

    public String getDeviceIdFilter() {
        return deviceIdFilter;
    }

    public void setCorrelationIdFilter(String correlationIdFilter) {
        this.correlationIdFilter = correlationIdFilter;
    }

    public String getCorrelationIdFilter() {
        return correlationIdFilter;
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public WebSocketServerImpl getServer() {
        return server;
    }

    public WebSocketClient getClient() {
        return client;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void toggleConnection() {
        synchronized (connectionLock) {
            System.out.println("[WebSocketManager] Attempting to toggle connection: useLocalServer=" + useLocalServer + ", deviceId=" + deviceId + ", wsUrl=" + wsUrl);
            if (isConnected.get()) {
                if (useLocalServer) {
                    try {
                        server.stopServer();
                        System.out.println("[WebSocketManager] Stopped local WebSocket server");
                    } catch (Exception e) {
                        System.out.println("[WebSocketManager] Failed to stop local WebSocket server: error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
                    }
                } else {
                    closeExistingClient();
                }
                isConnected.set(false);
            } else {
                if (!useLocalServer && !checkServerAvailability()) {
                    Messages.showErrorDialog(project, "WebSocket server is not available at " + wsUrl, "Error");
                    System.out.println("[WebSocketManager] Connection aborted: server not available at " + wsUrl);
                    return;
                }
                if (useLocalServer) {
                    if (!isPortAvailable(1065)) {
                        Messages.showErrorDialog(project,
                                "Port 1065 is already in use. Please free the port or connect to an existing server.",
                                "Error");
                        System.out.println("[WebSocketManager] Failed to start local server: Port 1065 in use");
                        return;
                    }
                    try {
                        server.start();
                        System.out.println("[WebSocketManager] Started local WebSocket server on port 1065");
                    } catch (Exception e) {
                        Messages.showErrorDialog(project, "Failed to start WebSocket server: " + e.getMessage(), "Error");
                        System.out.println("[WebSocketManager] Failed to start local WebSocket server: error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
                    }
                } else {
                    try {
                        closeExistingClient();
                        String fullWsUrl = wsUrl + "/ws?deviceId=" + deviceId;
                        System.out.println("[WebSocketManager] Creating WebSocket client: fullWsUrl=" + fullWsUrl + ", deviceId=" + deviceId);
                        client = new WebSocketClientImpl(new URI(fullWsUrl), deviceId, server.messageHandler);
                        activeClients.put(deviceId, client); // Track new client
                        System.out.println("[WebSocketManager] Active clients after creation: " + activeClients.size() + ", deviceId=" + deviceId);
                        client.connect();
                        System.out.println("[WebSocketManager] Initiated WebSocket client connection to " + fullWsUrl + ", deviceId=" + deviceId);
                    } catch (Exception e) {
                        Messages.showErrorDialog(project, "Failed to connect to WebSocket server: " + e.getMessage(), "Error");
                        System.out.println("[WebSocketManager] Failed to create or connect WebSocket client: deviceId=" + deviceId + ", fullWsUrl=" + wsUrl + "/ws?deviceId=" + deviceId + ", error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
                    }
                }
            }
        }
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            System.out.println("[WebSocketManager] Port " + port + " is available");
            return true;
        } catch (IOException e) {
            System.out.println("[WebSocketManager] Port " + port + " is not available: error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
            return false;
        }
    }

    private boolean checkServerAvailability() {
        try {
            String httpUrl = wsUrl.replace("ws://", "http://").replace("wss://", "https://") + "/health";
            URI uri = URI.create(httpUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            int responseCode = conn.getResponseCode();
            System.out.println("[WebSocketManager] Server health check: url=" + httpUrl + ", responseCode=" + responseCode);
            return responseCode == 200;
        } catch (IOException e) {
            System.out.println("[WebSocketManager] Server health check failed: url=" + wsUrl + ", error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
            return false;
        }
    }

    private void retryWebSocket() {
        synchronized (connectionLock) {
            reconnectAttempts++;
            if (reconnectAttempts >= maxReconnectAttempts) {
                System.out.println("[WebSocketManager] Max reconnect attempts (" + maxReconnectAttempts + ") reached: deviceId=" + deviceId);
                return;
            }
            long delay = Math.min(baseReconnectDelay * (1L << (reconnectAttempts - 1)) + (long) (Math.random() * 100), maxReconnectDelay);
            System.out.println("[WebSocketManager] Scheduling reconnection in " + delay + "ms (attempt " + reconnectAttempts + "/" + maxReconnectAttempts + "): deviceId=" + deviceId);
            Timer timer = new Timer((int) delay, e -> {
                synchronized (connectionLock) {
                    if (!isConnected.get() && !useLocalServer) {
                        if (!checkServerAvailability()) {
                            System.out.println("[WebSocketManager] Reconnection aborted: server not available at " + wsUrl + ", deviceId=" + deviceId);
                            return;
                        }
                        try {
                            // Ensure only one client is active
                            closeExistingClient();
                            String fullWsUrl = wsUrl + "/ws?deviceId=" + deviceId;
                            System.out.println("[WebSocketManager] Attempting reconnection: deviceId=" + deviceId + ", fullWsUrl=" + fullWsUrl);
                            client = new WebSocketClientImpl(new URI(fullWsUrl), deviceId, server.messageHandler);
                            activeClients.put(deviceId, client); // Track new client
                            System.out.println("[WebSocketManager] Active clients after reconnection: " + activeClients.size() + ", deviceId=" + deviceId);
                            client.connect();
                            System.out.println("[WebSocketManager] Reconnection attempt " + reconnectAttempts + " initiated to " + fullWsUrl + ", deviceId=" + deviceId);
                            // Resubscribe after reconnection
                            if (client instanceof WebSocketClientImpl) {
                                ((WebSocketClientImpl) client).resubscribe();
                            }
                        } catch (Exception ex) {
                            System.out.println("[WebSocketManager] Reconnection attempt failed: deviceId=" + deviceId + ", fullWsUrl=" + wsUrl + "/ws?deviceId=" + deviceId + ", error=" + ex.getMessage() + ", stack=" + Arrays.toString(ex.getStackTrace()));
                        }
                    } else {
                        System.out.println("[WebSocketManager] Reconnection skipped: isConnected=" + isConnected.get() + ", useLocalServer=" + useLocalServer + ", deviceId=" + deviceId);
                    }
                }
            });
            timer.setRepeats(false);
            timer.start();
            System.out.println("[WebSocketManager] Reconnection timer started: delay=" + delay + ", deviceId=" + deviceId);
        }
    }
}