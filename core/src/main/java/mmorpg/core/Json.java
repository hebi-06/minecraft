package mmorpg.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small strict JSON (RFC 8259) parser and writer without dependencies.
 *
 * <p>Parsed values: object → {@link LinkedHashMap} in JavaScript property order (integer keys
 * "0", "1", ... ascending first, then the others as written; a repeated key keeps its first position
 * and takes the last value — all as JS {@code JSON.parse}), array → {@link ArrayList},
 * number → {@link Double}, string → {@link String}, true/false → {@link Boolean}, null → {@code null}.
 * Anything outside the grammar (trailing commas, comments, single quotes, leading zeros, control
 * characters inside strings, trailing text, ...) is rejected with a {@link ParseException} that names
 * the line and column.
 */
public final class Json {
    /** Nesting deeper than this is rejected (keeps the recursive parser off the stack limit). */
    private static final int MAX_DEPTH = 512;

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    /** Malformed JSON input. */
    public static final class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ParseException(String message) {
            super(message);
        }
    }

    /** Parses one JSON value (surrounding whitespace allowed). */
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("JSON text is null");
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.readValue(0);
        p.skipWhitespace();
        if (p.pos < text.length()) throw p.error("unexpected text after the JSON value");
        return value;
    }

    /** Parses a JSON document whose top level must be an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new ParseException("top-level JSON value is not an object");
        return (Map<String, Object>) value;
    }

    // ------------------------------------------------------------------ reading

    private Object readValue(int depth) {
        if (pos >= text.length()) throw error("unexpected end of input, expected a value");
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return readObject(depth + 1);
            case '[':
                return readArray(depth + 1);
            case '"':
                return readString();
            case 't':
                return readWord("true", Boolean.TRUE);
            case 'f':
                return readWord("false", Boolean.FALSE);
            case 'n':
                return readWord("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
                throw error("unexpected character " + describe(c) + ", expected a value");
        }
    }

    private Map<String, Object> readObject(int depth) {
        if (depth > MAX_DEPTH) throw error("nesting deeper than " + MAX_DEPTH);
        pos++; // '{'
        Map<String, Object> out = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return out;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("expected a string key");
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            out.put(key, readValue(depth));
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == '}') return jsOrder(out);
            if (c != ',') {
                pos--;
                throw error("expected ',' or '}' in object");
            }
        }
    }

    /**
     * JavaScript property order (what Object.keys / Object.entries / JSON.stringify see): array-index
     * keys ascending first, then the other keys in document order.
     */
    private static Map<String, Object> jsOrder(Map<String, Object> map) {
        List<String> indices = new ArrayList<>();
        for (String k : map.keySet()) if (arrayIndex(k) >= 0) indices.add(k);
        if (indices.isEmpty()) return map;
        indices.sort((a, b) -> Long.compare(arrayIndex(a), arrayIndex(b)));
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : indices) out.put(k, map.get(k));
        for (Map.Entry<String, Object> e : map.entrySet()) if (arrayIndex(e.getKey()) < 0) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** The key as a JS array index (canonical integer string 0 … 2³² − 2), or -1. */
    private static long arrayIndex(String k) {
        int n = k.length();
        if (n == 0 || n > 10) return -1;
        if (k.charAt(0) == '0') return n == 1 ? 0 : -1;
        long v = 0;
        for (int i = 0; i < n; i++) {
            char c = k.charAt(i);
            if (c < '0' || c > '9') return -1;
            v = v * 10 + (c - '0');
        }
        return v <= 4294967294L ? v : -1;
    }

    private List<Object> readArray(int depth) {
        if (depth > MAX_DEPTH) throw error("nesting deeper than " + MAX_DEPTH);
        pos++; // '['
        List<Object> out = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return out;
        }
        while (true) {
            skipWhitespace();
            out.add(readValue(depth));
            skipWhitespace();
            char c = peek();
            pos++;
            if (c == ']') return out;
            if (c != ',') {
                pos--;
                throw error("expected ',' or ']' in array");
            }
        }
    }

    private String readString() {
        int start = ++pos; // after the opening quote
        StringBuilder sb = null;
        while (true) {
            if (pos >= text.length()) throw error("unterminated string");
            char c = text.charAt(pos);
            if (c == '"') {
                String s = sb == null ? text.substring(start, pos) : sb.append(text, start, pos).toString();
                pos++;
                return s;
            }
            if (c < 0x20) throw error("control character " + describe(c) + " in string (must be escaped)");
            if (c != '\\') {
                pos++;
                continue;
            }
            if (sb == null) sb = new StringBuilder();
            sb.append(text, start, pos);
            pos++; // backslash
            if (pos >= text.length()) throw error("unterminated escape sequence");
            char e = text.charAt(pos++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> sb.append(readHex4());
                default -> {
                    pos--;
                    throw error("invalid escape \\" + e);
                }
            }
            start = pos;
        }
    }

    private char readHex4() {
        if (pos + 4 > text.length()) throw error("incomplete \\u escape");
        int v = 0;
        for (int i = 0; i < 4; i++) {
            char h = text.charAt(pos);
            int d;
            if (h >= '0' && h <= '9') d = h - '0';
            else if (h >= 'a' && h <= 'f') d = h - 'a' + 10;
            else if (h >= 'A' && h <= 'F') d = h - 'A' + 10;
            else throw error("invalid hex digit " + describe(h) + " in \\u escape");
            v = v * 16 + d;
            pos++;
        }
        return (char) v;
    }

    private Double readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        if (peek() == '0') {
            pos++;
            if (isDigit(peek())) throw error("leading zero in number");
        } else if (isDigit(peek())) {
            while (isDigit(peek())) pos++;
        } else {
            throw error("expected a digit");
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) throw error("expected a digit after the decimal point");
            while (isDigit(peek())) pos++;
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') pos++;
            if (!isDigit(peek())) throw error("expected a digit in the exponent");
            while (isDigit(peek())) pos++;
        }
        // Double.parseDouble rounds correctly, like JS JSON.parse (1e400 → Infinity in both)
        return Double.parseDouble(text.substring(start, pos));
    }

    private Object readWord(String word, Object value) {
        if (!text.startsWith(word, pos)) throw error("invalid literal, expected '" + word + "'");
        pos += word.length();
        return value;
    }

    private void expect(char c) {
        if (peek() != c) throw error("expected '" + c + "'");
        pos++;
    }

    /** Current character, or '\0' at the end (never valid where it is checked). */
    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
            pos++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static String describe(char c) {
        return c < 0x20 || c > 0x7e ? String.format("U+%04X", (int) c) : "'" + c + "'";
    }

    private ParseException error(String message) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < Math.min(pos, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        String where = pos >= text.length() ? "end of input" : "line " + line + ", column " + col;
        return new ParseException("Malformed JSON at " + where + ": " + message);
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes a value as compact JSON (like JS {@code JSON.stringify}): numbers in JS form, NaN and
     * infinities as {@code null}; maps, lists, strings, booleans and null as usual.
     */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            sb.append(Double.isFinite(d) ? Js.str(d) : "null");
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                write(sb, list.get(i));
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("cannot write " + value.getClass().getName() + " as JSON");
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    boolean lone = Character.isSurrogate(c)
                            && !(Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1)))
                            && !(Character.isLowSurrogate(c) && i > 0 && Character.isHighSurrogate(s.charAt(i - 1)));
                    if (c < 0x20 || lone) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
