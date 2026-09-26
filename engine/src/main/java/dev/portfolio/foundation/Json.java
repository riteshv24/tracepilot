package dev.portfolio.foundation;

import java.math.BigDecimal;
import java.util.*;

/** Small, strict JSON codec used by the dependency-free engine and file snapshots.
 * Rejects duplicate keys, non-finite numbers, trailing data and excessive nesting.
 * No reflection-based object binding and no polymorphic deserialization.
 */
public final class Json {
    private static final int MAX_DEPTH = 64;
    private Json() {
    }
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("JSON must not be null");
        Parser parser = new Parser(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.pos != text.length()) throw parser.error("Trailing JSON data");
        return value;
    }
    @SuppressWarnings("unchecked") public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        for (Object key : map.keySet()) if (!(key instanceof String)) throw new IllegalArgumentException("Expected string key");
        return (Map<String, Object>) map;
    }
    @SuppressWarnings("unchecked") public static List<Object> array(Object value) {
        if (!(value instanceof List<?>)) throw new IllegalArgumentException("Expected JSON array");
        return (List<Object>) value;
    }
    public static Map<String, Object> map(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Expected key-value pairs");
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    public static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String)) throw new IllegalArgumentException(key + " must be text");
        return (String) value;
    }
    public static long integer(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number n)) throw new IllegalArgumentException(key + " must be numeric");
        try {
            return new BigDecimal(n.toString()).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
    }
    public static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IllegalArgumentException(key + " must be finite");
        return n.doubleValue();
    }
    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + " must be boolean");
        return (Boolean) value;
    }
    public static List<Object> list(Map<String, Object> map, String key) {
        return map.containsKey(key) ? array(map.get(key)) : new ArrayList<>();
    }
    public static List<String> strings(Map<String, Object> map, String key) {
        return list(map, key).stream().map(item -> {
            if (!(item instanceof String)) throw new IllegalArgumentException(key + " must contain strings");
            return (String) item;
        }).toList();
    }
    public static Object copy(Object value) {
        return parse(write(value));
    }
    public static Map<String, Object> copyMap(Map<String, Object> value) {
        return object(copy(value));
    }
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value, 0);
        return out.toString();
    }
    private static void append(StringBuilder out, Object value, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("JSON nesting limit exceeded");
        if (value == null) out.append("null");
        else if (value instanceof String s) quote(out, s);
        else if (value instanceof Boolean) out.append(value);
        else if (value instanceof Number n) {
            if (!Double.isFinite(n.doubleValue())) throw new IllegalArgumentException("JSON number is not finite");
            out.append(n);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("JSON key is not text");
                if (!first) out.append(',');
                first = false;
                quote(out, key);
                out.append(':');
                append(out, entry.getValue(), depth + 1);
            }
            out.append('}');
        } else if (value instanceof Collection<?> list) {
            out.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) out.append(',');
                first = false;
                append(out, item, depth + 1);
            }
            out.append(']');
        } else throw new IllegalArgumentException("Unsupported JSON type: " + value.getClass().getSimpleName());
    }
    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32 || Character.isSurrogate(c)) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }
    private static final class Parser {
        private final String text;
        private int pos;
        Parser(String text) {
            this.text = text;
        }
        void space() {
            while (pos < text.length() && " \t\n\r".indexOf(text.charAt(pos)) >= 0) pos++;
        }
        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + pos);
        }
        char take() {
            if (pos >= text.length()) throw error("Unexpected end of JSON");
            return text.charAt(pos++);
        }
        boolean consume(char c) {
            space();
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }
        Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("JSON nesting limit exceeded");
            space();
            if (pos >= text.length()) throw error("Expected a value");
            char c = text.charAt(pos);
            if (c == '"') return string();
            if (c == '{') {
                pos++;
                Map<String, Object> map = new LinkedHashMap<>();
                if (consume('}')) return map;
                do {
                    space();
                    if (pos >= text.length() || text.charAt(pos) != '"') throw error("Expected object key");
                    String key = string();
                    if (!consume(':')) throw error("Expected colon");
                    if (map.containsKey(key)) throw error("Duplicate key");
                    map.put(key, value(depth + 1));
                } while (consume(','));
                if (!consume('}')) throw error("Expected closing object brace");
                return map;
            }
            if (c == '[') {
                pos++;
                List<Object> list = new ArrayList<>();
                if (consume(']')) return list;
                do {
                    list.add(value(depth + 1));
                } while (consume(','));
                if (!consume(']')) throw error("Expected closing array bracket");
                return list;
            }
            for (String literal : List.of("true", "false", "null")) {
                if (text.startsWith(literal, pos)) {
                    pos += literal.length();
                    return switch (literal) {
                        case "true" -> true;
                        case "false" -> false;
                        default -> null;
                    };
                }
            }
            int start = pos;
            if (c == '-') pos++;
            if (pos >= text.length()) throw error("Invalid number");
            if (text.charAt(pos) == '0') pos++;
            else {
                if (text.charAt(pos) < '1' || text.charAt(pos) > '9') throw error("Expected value");
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (pos < text.length() && text.charAt(pos) == '.') {
                pos++;
                int decimalStart = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
                if (decimalStart == pos) throw error("Invalid fractional number");
            }
            if (pos < text.length() && "eE".indexOf(text.charAt(pos)) >= 0) {
                pos++;
                if (pos < text.length() && "+-".indexOf(text.charAt(pos)) >= 0) pos++;
                int exponentStart = pos;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
                if (exponentStart == pos) throw error("Invalid exponent");
            }
            try {
                String token = text.substring(start, pos);
                if (token.length() > 128) throw error("Number too long");
                BigDecimal number = new BigDecimal(token);
                if (!Double.isFinite(number.doubleValue())) throw error("Number outside supported range");
                return number;
            } catch (NumberFormatException e) {
                throw error("Invalid number");
            }
        }
        String string() {
            if (take() != '"') throw error("Expected string");
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = take();
                if (c == '"') return out.toString();
                if (c < 32) throw error("Unescaped control character");
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = take();
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw error("Incomplete Unicode escape");
                        try {
                            out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException e) {
                            throw error("Invalid Unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("Unknown escape");
                }
            }
        }
    }
}
