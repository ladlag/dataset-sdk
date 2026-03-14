package com.knowledge.sdk.util;

public final class JsonUtil {

    private JsonUtil() {
    }

    /**
     * Escape special characters in a JSON string value.
     *
     * @param value the string to escape
     * @return escaped string safe for use in JSON values
     */
    public static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
