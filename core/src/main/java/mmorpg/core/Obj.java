package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only view of one JSON object from rules.json (or a test case), with typed getters.
 *
 * <ul>
 *   <li>{@code num(k)}, {@code str(k)}, {@code obj(k)}, {@code list(k)}: the value must be there
 *       (like {@code t.damage} in JS, which would be NaN/throw otherwise) — a missing or mistyped
 *       value throws with its path, e.g. "TYPES.axe.damage".</li>
 *   <li>{@code numOr(k, x)}, {@code strOr(k, x)}: JS {@code obj.k ?? x}.</li>
 *   <li>{@code numOrNull}, {@code objOrNull}, ...: JS {@code obj?.k} (missing → null).</li>
 * </ul>
 * A JSON {@code null} counts as missing, as {@code ??} treats null and undefined alike.
 * The view does not copy: rules data is frozen once by {@link Rules}.
 */
public final class Obj {
    private final Map<String, Object> map;
    private final String path;

    public Obj(Map<String, Object> map, String path) {
        this.map = map;
        this.path = path;
    }

    /** View of a parsed JSON value that must be an object ({@code null} stays {@code null}). */
    @SuppressWarnings("unchecked")
    public static Obj of(Object value, String path) {
        if (value == null) return null;
        if (!(value instanceof Map)) throw new IllegalStateException(path + " is not an object");
        return new Obj((Map<String, Object>) value, path);
    }

    /** Raw value (null if missing). */
    public Object get(String key) {
        return map.get(key);
    }

    /** JS {@code key in obj} for own keys (true even when the value is null). */
    public boolean has(String key) {
        return map.containsKey(key);
    }

    /** JS truthiness of {@code obj[key]}. */
    public boolean truthy(String key) {
        return Js.truthy(map.get(key));
    }

    /** {@code `${obj[key]}`} — a missing value prints "undefined" as in JS. */
    public String text(String key) {
        return Js.str(map.get(key));
    }

    public Set<String> keys() {
        return map.keySet();
    }

    // ------------------------------------------------------------------ numbers

    public double num(String key) {
        Double v = numOrNull(key);
        if (v == null) throw missing(key, "number");
        return v;
    }

    public double numOr(String key, double fallback) {
        Double v = numOrNull(key);
        return v == null ? fallback : v;
    }

    public Double numOrNull(String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        throw wrongType(key, "number", v);
    }

    // ------------------------------------------------------------------ strings

    public String str(String key) {
        String v = strOrNull(key);
        if (v == null) throw missing(key, "string");
        return v;
    }

    public String strOr(String key, String fallback) {
        String v = strOrNull(key);
        return v == null ? fallback : v;
    }

    public String strOrNull(String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof String s) return s;
        throw wrongType(key, "string", v);
    }

    // ------------------------------------------------------------------ objects and lists

    public Obj obj(String key) {
        Obj v = objOrNull(key);
        if (v == null) throw missing(key, "object");
        return v;
    }

    public Obj objOrNull(String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Map) return of(v, path + "." + key);
        throw wrongType(key, "object", v);
    }

    public List<Object> list(String key) {
        List<Object> v = listOrNull(key);
        if (v == null) throw missing(key, "array");
        return v;
    }

    @SuppressWarnings("unchecked")
    public List<Object> listOrNull(String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof List) return (List<Object>) v;
        throw wrongType(key, "array", v);
    }

    /** Array of numbers ({@code null} if missing). */
    public List<Double> numbersOrNull(String key) {
        List<Object> raw = listOrNull(key);
        if (raw == null) return null;
        List<Double> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (!(o instanceof Number n)) throw new IllegalStateException(path + "." + key + " has a non-number element");
            out.add(n.doubleValue());
        }
        return out;
    }

    /** Array of objects ({@code null} if missing). */
    public List<Obj> objsOrNull(String key) {
        List<Object> raw = listOrNull(key);
        if (raw == null) return null;
        List<Obj> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) out.add(of(raw.get(i), path + "." + key + "[" + i + "]"));
        return out;
    }

    private IllegalStateException missing(String key, String type) {
        return new IllegalStateException("missing " + type + ": " + path + "." + key);
    }

    private IllegalStateException wrongType(String key, String type, Object v) {
        return new IllegalStateException(path + "." + key + " is not a " + type + " (" + v.getClass().getSimpleName() + ")");
    }

    @Override
    public String toString() {
        return path + Json.stringify(map);
    }
}
