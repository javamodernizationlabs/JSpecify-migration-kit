package io.github.javamodernizationlabs.jspecify.report;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny zero-dependency JSON encoder good enough for our report shapes.
 * Reports are written by the tool itself, so we don't need to parse JSON back.
 */
final class Json {

    private Json() {}

    static String string(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String number(long n) { return Long.toString(n); }

    static String bool(boolean b) { return Boolean.toString(b); }

    static String arrayOfStrings(Collection<String> values) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String v : values) {
            if (!first) sb.append(',');
            sb.append(string(v));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    static String object(Map<String, String> rawFields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : rawFields.entrySet()) {
            if (!first) sb.append(',');
            sb.append(string(entry.getKey())).append(':').append(entry.getValue());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }
}
