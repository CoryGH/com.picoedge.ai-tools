package com.picoedge.ai_tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Consumer;

public class LogProcessor {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<LogProcessor.LogEvent> allLogs = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Set<String>> processedEventIdsBySubId = Collections.synchronizedMap(new HashMap<>());
    private final List<String> availableCategories = Collections.synchronizedList(new ArrayList<>());
    private final List<String> availableSources = Collections.synchronizedList(new ArrayList<>());
    private int maxLogs = 10000;
    private boolean defaultStackExpanded = false;
    private boolean defaultDataExpanded = false;
    private String currentSubId = null;
    private final Runnable updateUICallback; // Callback to trigger UI updates

    // Constructor with UI update callback
    public LogProcessor(Runnable updateUICallback) {
        this.updateUICallback = updateUICallback;
    }

    public static class LogEvent {
        String id;
        int level;
        String category;
        String description;
        long timestamp;
        String source;
        String correlationId;
        List<Map<String, Object>> stacktrace;
        boolean stacktraceExpanded;
        boolean dataExpanded;
        String title;
        Integer code;
        Object data;
        String deviceId;
        Boolean includeStacktrace;

        LogEvent(String id, int level, String category, String description, long timestamp, String source,
                 String correlationId, List<Map<String, Object>> stacktrace, String title, Integer code,
                 Object data, String deviceId, Boolean includeStacktrace) {
            this.id = id;
            this.level = level;
            this.category = category != null ? category : "general";
            this.description = description != null ? description : "";
            this.timestamp = timestamp;
            this.source = source != null ? source : "unknown";
            this.correlationId = correlationId;
            this.stacktrace = stacktrace;
            this.stacktraceExpanded = stacktrace != null && !stacktrace.isEmpty();
            this.dataExpanded = data != null;
            this.title = title;
            this.code = code;
            this.data = data;
            this.deviceId = deviceId;
            this.includeStacktrace = includeStacktrace;
        }

        public String getId() { return id; }
        public int getLevel() { return level; }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
        public String getCorrelationId() { return correlationId; }
        public List<Map<String, Object>> getStacktrace() { return stacktrace; }
        public boolean isStacktraceExpanded() { return stacktraceExpanded; }
        public boolean isDataExpanded() { return dataExpanded; }
        public String getTitle() { return title; }
        public Integer getCode() { return code; }
        public Object getData() { return data; }
        public String getDeviceId() { return deviceId; }
        public Boolean getIncludeStacktrace() { return includeStacktrace; }
    }

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

    public void processMessage(String message, Consumer<String> onNewCategory, Consumer<String> onNewSource) {
        System.out.println("[LogProcessor] Processing message: " + message);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(message, Map.class);
            String id = (String) json.get("id");
            if (id == null) {
                System.out.println("[LogProcessor] Skipping event with null ID: message=" + message);
                return;
            }
            synchronized (processedEventIdsBySubId) {
                Set<String> processedEventIds = processedEventIdsBySubId.computeIfAbsent(currentSubId, k -> new HashSet<>());
                if (processedEventIds.contains(id)) {
                    System.out.println("[LogProcessor] Skipping duplicate event: id=" + id + ", subId=" + currentSubId);
                    return;
                }
                processedEventIds.add(id);
                if (processedEventIds.size() > maxLogs) {
                    Iterator<String> iterator = processedEventIds.iterator();
                    if (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();
                    }
                }
            }
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
            String eventDeviceId = (String) json.get("deviceId");
            Boolean includeStacktrace = json.get("includeStacktrace") instanceof Boolean ? (Boolean) json.get("includeStacktrace") : null;
            System.out.println("[LogProcessor] Parsed event: id=" + id + ", category=" + category + ", level=" + level + ", description=" + description + ", source=" + source + ", correlationId=" + correlationId + ", deviceId=" + eventDeviceId);
            LogEvent event = new LogEvent(id, level, category, description, timestamp, source, correlationId, stacktrace,
                    title, code, data, eventDeviceId, includeStacktrace);
            synchronized (allLogs) {
                event.stacktraceExpanded = defaultStackExpanded;
                event.dataExpanded = data != null ? defaultDataExpanded : false;
                allLogs.add(event);
                if (allLogs.size() > maxLogs) allLogs.remove(0);
            }
            synchronized (availableCategories) {
                if (!availableCategories.contains(category)) {
                    availableCategories.add(category);
                    availableCategories.sort(String::compareTo);
                    System.out.println("[LogProcessor] Added category: " + category);
                    onNewCategory.accept(category);
                }
            }
            synchronized (availableSources) {
                if (!availableSources.contains(source)) {
                    availableSources.add(source);
                    System.out.println("[LogProcessor] Added source: " + source);
                    onNewSource.accept(source);
                }
            }
            System.out.println("[LogProcessor] Successfully processed event: id=" + id);
        } catch (Exception e) {
            System.out.println("[LogProcessor] Failed to process message: " + message + ", error=" + e.getMessage() + ", stack=" + Arrays.toString(e.getStackTrace()));
        }
    }

    public void setCurrentSubId(String subId) {
        synchronized (processedEventIdsBySubId) {
            this.currentSubId = subId;
            processedEventIdsBySubId.computeIfAbsent(subId, k -> new HashSet<>());
            System.out.println("[LogProcessor] Set current subscription ID: " + subId);
        }
    }

    public void clearProcessedEventIds(String subId) {
        synchronized (processedEventIdsBySubId) {
            processedEventIdsBySubId.remove(subId);
            System.out.println("[LogProcessor] Cleared processed event IDs for subscription: " + subId);
        }
    }

    public void clearProcessedEventIds() {
        synchronized (processedEventIdsBySubId) {
            processedEventIdsBySubId.clear();
            System.out.println("[LogProcessor] Cleared all processed event IDs for new WebSocket connection");
        }
    }

    public void setMaxLogs(int maxLogs) {
        this.maxLogs = maxLogs;
        synchronized (allLogs) {
            while (allLogs.size() > maxLogs) {
                allLogs.remove(0);
            }
        }
    }

    public int getMaxLogs() {
        return maxLogs;
    }

    public List<LogEvent> getAllLogs() {
        synchronized (allLogs) {
            List<LogEvent> sortedLogs = new ArrayList<>(allLogs);
            sortedLogs.sort(Comparator.comparingLong(LogEvent::getTimestamp));
            return sortedLogs;
        }
    }

    public void clearLogs() {
        synchronized (allLogs) {
            allLogs.clear();
        }
        synchronized (processedEventIdsBySubId) {
            processedEventIdsBySubId.clear();
        }
        synchronized (availableSources) {
            availableSources.clear();
        }
    }

    public void toggleStack(String eventId) {
        synchronized (allLogs) {
            for (LogEvent event : allLogs) {
                if (event.id.equals(eventId) && event.stacktrace != null && !event.stacktrace.isEmpty()) {
                    event.stacktraceExpanded = !event.stacktraceExpanded;
                    System.out.println("[LogProcessor] Toggled stack trace for event: id=" + eventId + ", expanded=" + event.stacktraceExpanded);
                    break;
                }
            }
        }
        if (updateUICallback != null) {
            updateUICallback.run(); // Trigger UI update after toggling
        }
    }

    public void toggleData(String eventId) {
        synchronized (allLogs) {
            for (LogEvent event : allLogs) {
                if (event.id.equals(eventId) && event.data != null) {
                    event.dataExpanded = !event.dataExpanded;
                    System.out.println("[LogProcessor] Toggled data for event: id=" + eventId + ", expanded=" + event.dataExpanded);
                    break;
                }
            }
        }
        if (updateUICallback != null) {
            updateUICallback.run(); // Trigger UI update after toggling
        }
    }

    public void setDefaultStackExpanded(boolean expanded) {
        this.defaultStackExpanded = expanded;
        synchronized (allLogs) {
            for (LogEvent event : allLogs) {
                if (event.stacktrace != null && !event.stacktrace.isEmpty()) {
                    event.stacktraceExpanded = expanded;
                }
            }
        }
        System.out.println("[LogProcessor] Set default stack expanded: " + expanded);
        if (updateUICallback != null) {
            updateUICallback.run(); // Trigger UI update after state change
        }
    }

    public void setDefaultDataExpanded(boolean expanded) {
        this.defaultDataExpanded = expanded;
        synchronized (allLogs) {
            for (LogEvent event : allLogs) {
                if (event.data != null) {
                    event.dataExpanded = expanded;
                }
            }
        }
        System.out.println("[LogProcessor] Set default data expanded: " + expanded);
        if (updateUICallback != null) {
            updateUICallback.run(); // Trigger UI update after state change
        }
    }

    public boolean isDefaultStackExpanded() {
        return defaultStackExpanded;
    }

    public boolean isDefaultDataExpanded() {
        return defaultDataExpanded;
    }
}