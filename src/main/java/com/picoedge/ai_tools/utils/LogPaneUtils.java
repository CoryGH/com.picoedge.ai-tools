package com.picoedge.ai_tools.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LogPaneUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static int stringToHash(String value) {
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            hash = ((hash << 5) - hash + value.charAt(i)) & 0x1FFFFF;
        }
        return hash;
    }

    public static String getFieldColor(String value) {
        if (value == null || value.isEmpty()) {
            return "#FFFFFF";
        }
        int base = 128;
        int hash = stringToHash(value);
        int red = (hash & 0x7F) + base;
        int green = ((hash >> 7) & 0x7F) + base;
        int blue = ((hash >> 14) & 0x7F) + base;
        String color = String.format("#%02X%02X%02X", red, green, blue);
        System.out.println("[LogPaneUtils] Generated color for value \"" + value + "\": " + color);
        return color;
    }

    public static String formatData(Object data) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data).replace("<", "&lt;").replace(">", "&gt;");
        } catch (Exception e) {
            return "Error: Data is not JSON-serializable";
        }
    }
}