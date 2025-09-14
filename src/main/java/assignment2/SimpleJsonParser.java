package assignment2;

import java.util.*;

/**
 * Minimal functional JSON parser for flat JSON objects with string key-value pairs.
 */
public class SimpleJsonParser {

    /**
     * Parses a JSON object string into a Map<String, String>.
     * Example input: {"id":"ABC","temp":"20"}
     */
    public static Map<String, String> parse(String json) throws IllegalArgumentException {
        Map<String, String> map = new LinkedHashMap<>();

        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON object");
        }
        // Strip starting and ending braces
        json = json.substring(1, json.length() - 1).trim();

        if (json.isEmpty()) return map;

        // Split entries by commas not within quotes
        List<String> entries = splitIgnoringQuotes(json, ',');

        for (String entry : entries) {
            List<String> pair = splitIgnoringQuotes(entry, ':');
            if (pair.size() != 2) throw new IllegalArgumentException("Invalid JSON entry: " + entry);

            String key = removeQuotes(pair.get(0).trim());
            String value = removeQuotes(pair.get(1).trim());

            map.put(key, value);
        }

        return map;
    }

    /**
     * Converts a Map<String, String> to a JSON object string.
     * Example output: {"id":"ABC","temp":"20"}
     */
    public static String toJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            sb.append("\"").append(e.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    // Helper method: removes surrounding quotes if present
    private static String removeQuotes(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    // Helper method: splits string by delimiter ignoring delimiters inside quotes
    private static List<String> splitIgnoringQuotes(String str, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '"') inQuotes = !inQuotes;
            if (c == delimiter && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result;
    }
}
