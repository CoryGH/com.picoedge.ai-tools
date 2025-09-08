package com.picoedge.ai_tools;

import com.picoedge.ai_tools.utils.LogPaneUtils;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class LogDisplay {
    public String generateLogHtml(List<LogProcessor.LogEvent> logs) {
        StringBuilder html = new StringBuilder("<html><body style='font-family:monospace;font-size:12px;background-color:#1e1e1e;color:#ffffff;padding:10px;'>");

        for (LogProcessor.LogEvent event : logs) {
            String escapedSource = event.source != null ? event.source.replace("<", "&lt;").replace(">", "&gt;") : "";
            String escapedDeviceId = event.deviceId != null ? event.deviceId.replace("<", "&lt;").replace(">", "&gt;") : "";
            String escapedCorrelationId = event.correlationId != null ? event.correlationId.replace("<", "&lt;").replace(">", "&gt;") : "";
            String escapedCategory = event.category.replace("<", "&lt;").replace(">", "&gt;");
            String escapedTitle = event.title != null ? event.title.replace("<", "&lt;").replace(">", "&gt;") : "";
            String escapedDescription = event.description.replace("<", "&lt;").replace(">", "&gt;");
            String escapedCode = event.code != null ? event.code.toString() : "N/A";
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(event.timestamp));
            String levelStr = LogProcessor.LogLevel.toString(event.level);
            String color;
            switch (event.level) {
                case 1: color = "#999999"; break;
                case 2: color = "#00FF00"; break;
                case 4: color = "#1E90FF"; break;
                case 8: color = "#FFD700"; break;
                case 16: color = "#FFA500"; break;
                case 32: color = "#FF0000"; break;
                case 64: color = "#FF00FF"; break;
                case 128: color = "#DC143C"; break;
                default: color = "#FFFFFF"; break;
            }
            String categoryColor = LogPaneUtils.getFieldColor(event.category);
            String sourceColor = LogPaneUtils.getFieldColor(event.source);
            String deviceIdColor = LogPaneUtils.getFieldColor(event.deviceId);
            String correlationIdColor = LogPaneUtils.getFieldColor(event.correlationId);
            html.append("<div style='margin-bottom:10px;padding:10px;background-color:#2a2a2a;border-radius:4px;'>");
            html.append(String.format("<span style='color:%s'>%s [<a href='category:%s' title='Category' style='color:%s !important;text-decoration:underline'>%s</a>] [<a href='source:%s' title='Source' style='color:%s !important;text-decoration:underline'>%s</a>] [<a href='deviceId:%s' title='Device' style='color:%s !important;text-decoration:underline'>%s</a>] [<a href='correlationId:%s' title='Correlation' style='color:%s !important;text-decoration:underline'>%s</a>] [<a href='level:%s' title='Log Level' style='color:%s !important;text-decoration:underline'>%s</a>] %s: %s</span><br>",
                    color, timestamp, escapedCategory, categoryColor, escapedCategory,
                    escapedSource, sourceColor, escapedSource,
                    escapedDeviceId, deviceIdColor, escapedDeviceId,
                    escapedCorrelationId, correlationIdColor, escapedCorrelationId,
                    levelStr, color, levelStr, escapedCode, escapedTitle));
            html.append(escapedDescription).append("<br>");
            if (event.stacktrace != null && !event.stacktrace.isEmpty()) {
                html.append(String.format("<div style='margin-top:5px;'><a href='stack:%s' style='color:#1e90ff;font-family:monospace;font-size:12px;text-decoration:underline;user-select:none;'>%s</a></div>",
                        event.id.replace("<", "&lt;").replace(">", "&gt;"), event.stacktraceExpanded ? "Hide Stack" : "Show Stack"));
            }
            if (event.data != null) {
                html.append(String.format("<div style='margin-top:5px;'><a href='data:%s' style='color:#1e90ff;font-family:monospace;font-size:12px;text-decoration:underline;user-select:none;'>%s</a></div>",
                        event.id.replace("<", "&lt;").replace(">", "&gt;"), event.dataExpanded ? "Hide Data" : "Show Data"));
            }
            if (event.stacktraceExpanded && event.stacktrace != null && !event.stacktrace.isEmpty()) {
                html.append("<div style='margin-top:5px;padding-left:10px;border-left:2px solid #555;color:#cccccc;font-family:monospace;font-size:10px;'>");
                html.append("Stack Trace:<br>");
                for (Map<String, Object> frame : event.stacktrace) {
                    Object lineObj = frame.get("line");
                    String line = lineObj instanceof String ? (String) lineObj : (lineObj != null ? lineObj.toString() : "");
                    html.append(line.replace("<", "&lt;").replace(">", "&gt;")).append("<br>");
                }
                html.append("</div>");
            }
            if (event.dataExpanded && event.data != null) {
                html.append("<div style='margin-top:5px;padding-left:10px;border-left:2px solid #555;color:#cccccc;font-family:monospace;font-size:10px;'>");
                html.append("Data:<br>");
                html.append(LogPaneUtils.formatData(event.data).replace("\n", "<br>"));
                html.append("</div>");
            }
            html.append("</div>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    public void copyLogs(List<LogProcessor.LogEvent> logs) {
        StringBuilder logText = new StringBuilder();
        for (LogProcessor.LogEvent event : logs) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(event.timestamp));
            String source = event.source != null ? event.source : "";
            String deviceId = event.deviceId != null ? event.deviceId : "";
            String correlationId = event.correlationId != null ? event.correlationId : "";
            String categoryStr = event.category != null ? event.category : "";
            String levelStr = LogProcessor.LogLevel.toString(event.level);
            String codeStr = event.code != null ? event.code.toString() : "N/A";
            String title = event.title != null ? event.title : "";
            String description = event.description != null ? event.description : "";
            logText.append(String.format("### %s [%s] [%s] [%s] [%s] [%s] %s: %s\n%s",
                    timestamp, categoryStr, source, deviceId, correlationId, levelStr, codeStr, title, description));
            if (event.dataExpanded && event.data != null) {
                logText.append("\n#### Data\n").append(LogPaneUtils.formatData(event.data));
            }
            if (event.stacktraceExpanded && event.stacktrace != null && !event.stacktrace.isEmpty()) {
                logText.append("\n#### Stack Trace\n");
                for (Map<String, Object> frame : event.stacktrace) {
                    Object lineObj = frame.get("line");
                    String line = lineObj instanceof String ? (String) lineObj : (lineObj != null ? lineObj.toString() : "");
                    logText.append(line).append("\n");
                }
            }
            logText.append("\n");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(logText.toString()), null);
        System.out.println("[LogDisplay] Copied logs to clipboard: length=" + logText.length());
    }
}