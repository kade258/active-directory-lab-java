package lab;

import java.util.*;

/** Small output-only encoder; incoming requests use standard URL-encoded forms. */
final class Json {
    private Json() {}
    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            StringJoiner items = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> items.add(quote(String.valueOf(key)) + ":" + encode(item)));
            return items.toString();
        }
        if (value instanceof Iterable<?> list) {
            StringJoiner items = new StringJoiner(",", "[", "]");
            list.forEach(item -> items.add(encode(item)));
            return items.toString();
        }
        throw new IllegalArgumentException("Unsupported JSON type.");
    }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
