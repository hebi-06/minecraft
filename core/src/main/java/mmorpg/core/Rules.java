package mmorpg.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Balance data exported from the add-on's config.js ({@code /mmorpg/rules.json} on the classpath,
 * written by tools/export_rules.mjs). Loaded once on first use and frozen (read-only).
 *
 * <p>Top-level keys are the config.js constant names ({@code TYPES}, {@code GRADES}, {@code RUNES}, ...).
 * Object key order is the JS order, so iterating a table here visits entries in the same order as
 * {@code Object.entries} in the add-on.
 */
public final class Rules {
    public static final String RESOURCE = "/mmorpg/rules.json";

    private final Obj root;
    private final List<Grade> grades;

    private Rules(Map<String, Object> json) {
        this.root = new Obj(freezeMap(json), "rules");
        List<Grade> gs = new ArrayList<>();
        List<Obj> raw = root.objsOrNull("GRADES");
        if (raw == null) throw new IllegalStateException("missing array: rules.GRADES");
        for (int i = 0; i < raw.size(); i++) {
            Obj g = raw.get(i);
            gs.add(new Grade(i, g.str("id"), g.str("name"), g.str("color"), g.str("dark"), g.num("mult"), g.str("icon")));
        }
        this.grades = List.copyOf(gs);
    }

    /** The rules (loaded from the classpath on first call). */
    public static Rules get() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final Rules INSTANCE = load();
    }

    private static Rules load() {
        try (InputStream in = Rules.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is not on the classpath (generate it with tools/export_rules.mjs)");
            return new Rules(Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
    }

    // ------------------------------------------------------------------ typed accessors

    /** The whole document (one key per config.js constant). */
    public Obj root() {
        return root;
    }

    /** An object constant, e.g. {@code obj("TYPES")}. */
    public Obj obj(String name) {
        return root.obj(name);
    }

    /** A number constant, e.g. {@code num("CRIT_CAP")}. */
    public double num(String name) {
        return root.num(name);
    }

    /** An array constant of numbers, e.g. {@code numbers("LEVELS")}. */
    public List<Double> numbers(String name) {
        List<Double> v = root.numbersOrNull(name);
        if (v == null) throw new IllegalStateException("missing array: rules." + name);
        return v;
    }

    /** An array constant of strings, e.g. {@code strings("TYPE_ORDER")}. */
    public List<String> strings(String name) {
        List<Object> raw = root.list(name);
        List<String> out = new ArrayList<>(raw.size());
        for (Object o : raw) {
            if (!(o instanceof String s)) throw new IllegalStateException("rules." + name + " has a non-string element");
            out.add(s);
        }
        return List.copyOf(out);
    }

    /** GRADES in order. */
    public List<Grade> grades() {
        return grades;
    }

    /** The grade with this id ({@code GRADES.find(g => g.id === id)}), or null. */
    public Grade grade(String id) {
        for (Grade g : grades) if (g.id().equals(id)) return g;
        return null;
    }

    // ------------------------------------------------------------------ freezing

    private static Map<String, Object> freezeMap(Map<String, Object> map) {
        map.replaceAll((k, v) -> freeze(v));
        return Collections.unmodifiableMap(map);
    }

    @SuppressWarnings("unchecked")
    private static Object freeze(Object v) {
        if (v instanceof Map) return freezeMap((Map<String, Object>) v);
        if (v instanceof List) {
            List<Object> list = (List<Object>) v;
            list.replaceAll(Rules::freeze);
            return Collections.unmodifiableList(list);
        }
        return v;
    }
}
