package com.picoedge.ai_tools;

import com.picoedge.ai_tools.utils.LogPaneUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LogFilter {
    private String deviceIdFilter = "";
    private String correlationIdFilter = "";

    public Collection<LogProcessor.LogEvent> filterLogs(Collection<LogProcessor.LogEvent> logs, Set<Integer> selectedLevels, Set<String> selectedSources,
                                                        boolean allSourcesSelected, String categoryFilter, String searchQuery,
                                                        String deviceIdFilter, String correlationIdFilter, long[] timeRange) {
        this.deviceIdFilter = deviceIdFilter != null ? deviceIdFilter : "";
        this.correlationIdFilter = correlationIdFilter != null ? correlationIdFilter : "";
        long currentTime = System.currentTimeMillis();
        long timeRangeMillis = timeRange[1] - timeRange[0];
        List<LogProcessor.LogEvent> filteredLogs = new ArrayList<>(); // Use List to maintain order

        for (LogProcessor.LogEvent event : logs) {
            if (!"All Time".equals(timeRange) && (currentTime - event.getTimestamp()) > timeRangeMillis) {
                System.out.println("[LogFilter] Filtered out event by timestamp: id=" + event.getId() + ", timestamp=" + event.getTimestamp() + ", timeRange=" + timeRange);
                continue;
            }

            String dataStr = event.getData() != null ? LogPaneUtils.formatData(event.getData()).toLowerCase() : "";
            String stacktraceStr = event.getStacktrace() != null ? event.getStacktrace().stream()
                    .map(frame -> frame.toString().toLowerCase())
                    .collect(Collectors.joining(" ")) : "";
            String fullText = (dataStr + stacktraceStr + (event.getCategory() != null ? event.getCategory().toLowerCase() : "") +
                    (event.getSource() != null ? event.getSource().toLowerCase() : "") +
                    (event.getDeviceId() != null ? event.getDeviceId().toLowerCase() : "") +
                    (event.getCorrelationId() != null ? event.getCorrelationId().toLowerCase() : "") +
                    (event.getDescription() != null ? event.getDescription().toLowerCase() : "") +
                    (event.getCode() != null ? event.getCode().toString().toLowerCase() : "") +
                    LogProcessor.LogLevel.toString(event.getLevel()).toLowerCase()).toLowerCase();
            if (!searchQuery.isEmpty() && !fullText.contains(searchQuery.toLowerCase())) {
                System.out.println("[LogFilter] Filtered out event by search query: id=" + event.getId() + ", query=" + searchQuery);
                continue;
            }

            int selectedLevelsInt = selectedLevels.stream().mapToInt(Integer::intValue).reduce(0, (a, b) -> a | b);
            if (selectedLevelsInt != 0 && (event.getLevel() & selectedLevelsInt) == 0) {
                System.out.println("[LogFilter] Filtered out event by level: id=" + event.getId() + ", level=" + event.getLevel() + ", selectedLevels=" + selectedLevelsInt);
                continue;
            }

            if (!selectedSources.isEmpty() && !allSourcesSelected && !selectedSources.contains(event.getSource())) {
                System.out.println("[LogFilter] Filtered out event by source: id=" + event.getId() + ", source=" + event.getSource() + ", selectedSources=" + selectedSources);
                continue;
            }

            if (!categoryFilter.isEmpty() && !event.getCategory().startsWith(categoryFilter)) {
                System.out.println("[LogFilter] Filtered out event by category: id=" + event.getId() + ", category=" + event.getCategory() + ", filterCategory=" + categoryFilter);
                continue;
            }

            // Only apply deviceIdFilter if it is non-empty
            if (!this.deviceIdFilter.isEmpty() && event.getDeviceId() != null && !this.deviceIdFilter.equals(event.getDeviceId())) {
                System.out.println("[LogFilter] Filtered out event by deviceId: id=" + event.getId() + ", deviceId=" + event.getDeviceId() + ", filterDeviceId=" + this.deviceIdFilter);
                continue;
            }

            if (!this.correlationIdFilter.isEmpty() && !this.correlationIdFilter.equals(event.getCorrelationId())) {
                System.out.println("[LogFilter] Filtered out event by correlationId: id=" + event.getId() + ", correlationId=" + event.getCorrelationId() + ", filterCorrelationId=" + this.correlationIdFilter);
                continue;
            }

            filteredLogs.add(event);
        }

        // Sort filtered logs by timestamp to ensure chronological order
        filteredLogs.sort(Comparator.comparingLong(LogProcessor.LogEvent::getTimestamp));
        System.out.println("[LogFilter] Sorted filtered logs by timestamp, count=" + filteredLogs.size());
        return filteredLogs;
    }

    public void setDeviceIdFilter(String deviceIdFilter) {
        this.deviceIdFilter = deviceIdFilter != null ? deviceIdFilter : "";
    }

    public String getDeviceIdFilter() {
        return deviceIdFilter;
    }

    public void setCorrelationIdFilter(String correlationIdFilter) {
        this.correlationIdFilter = correlationIdFilter != null ? correlationIdFilter : "";
    }

    public String getCorrelationIdFilter() {
        return correlationIdFilter;
    }
}