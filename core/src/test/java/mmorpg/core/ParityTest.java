package mmorpg.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parity test (plain {@code main}, no JUnit). Reads parity.json — cases {fn, args, expected | error}
 * written by tools/parity_dump.mjs from the add-on's real JavaScript — runs each through the Java
 * port and compares: strings, booleans, lists, maps (keys in order) and integral numbers exactly,
 * other numbers within 1e-9 × max(1, |expected|); an "error" case must throw. Prints pass counts per
 * function and the first mismatches; exits with status 1 on any mismatch, on a function the test
 * does not know, or on a known function without cases.
 *
 * <p>Usage: {@code java -cp <classes> mmorpg.core.ParityTest [parity.json]} (default: classpath
 * resource {@code /parity.json}).
 */
public final class ParityTest {
    private static final int SHOW_PER_FN = 3;
    private static final int SHOW_TOTAL = 30;

    @FunctionalInterface
    private interface Fn {
        Object call(Args a);
    }

    private static final Map<String, Fn> FNS = new LinkedHashMap<>();

    public static void main(String[] argv) throws IOException {
        String text = argv.length > 0 ? Files.readString(Path.of(argv[0]), StandardCharsets.UTF_8) : resource("/parity.json");
        List<?> cases = (List<?>) Json.parse(text);
        register();

        Map<String, int[]> counts = new TreeMap<>();
        List<String> shown = new ArrayList<>();
        Map<String, Integer> shownPerFn = new LinkedHashMap<>();
        int failures = 0;
        for (Object raw : cases) {
            Map<?, ?> c = (Map<?, ?>) raw;
            String fn = (String) c.get("fn");
            List<?> args = (List<?>) decode(c.get("args"));
            int[] count = counts.computeIfAbsent(fn, k -> new int[2]);
            String problem = check(fn, args, c);
            if (problem == null) {
                count[0]++;
                continue;
            }
            count[1]++;
            failures++;
            int n = shownPerFn.merge(fn, 1, Integer::sum);
            if (n <= SHOW_PER_FN && shown.size() < SHOW_TOTAL) shown.add(fn + " " + clip(Json.stringify(args), 160) + "\n      " + problem);
        }
        for (String fn : FNS.keySet()) {
            if (!counts.containsKey(fn)) {
                failures++;
                shown.add(fn + ": no cases in parity.json (the dump no longer covers it)");
            }
        }

        System.out.printf("parity.json: %d cases, %d functions%n", cases.size(), counts.size());
        System.out.printf("  %-28s %7s %6s%n", "function", "pass", "fail");
        for (Map.Entry<String, int[]> e : counts.entrySet()) System.out.printf("  %-28s %7d %6d%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
        if (!shown.isEmpty()) {
            System.out.println("First mismatches:");
            for (String s : shown) System.out.println("  " + s);
        }
        int passed = counts.values().stream().mapToInt(v -> v[0]).sum();
        System.out.printf("RESULT: %s (%d/%d cases passed, %d problems)%n", failures == 0 ? "PASS" : "FAIL", passed, cases.size(), failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    /** null when the case passes, else what went wrong. */
    private static String check(String fn, List<?> args, Map<?, ?> c) {
        Fn f = FNS.get(fn);
        if (f == null) return "unknown function (add it to ParityTest.register)";
        Object actual;
        try {
            actual = f.call(new Args(args));
        } catch (RuntimeException e) {
            if (c.containsKey("error")) return null;
            return "threw " + e + " (JS: " + clip(Json.stringify(decode(c.get("expected"))), 200) + ")";
        }
        if (c.containsKey("error")) return "expected an error (JS: " + c.get("error") + "), got " + clip(Json.stringify(actual), 200);
        return diff(decode(c.get("expected")), actual, "");
    }

    // ------------------------------------------------------------------ comparison

    private static String diff(Object exp, Object act, String path) {
        String at = path.isEmpty() ? "" : path + ": ";
        if (exp == null || act == null) {
            return exp == act ? null : at + "expected " + clip(Json.stringify(exp), 200) + ", got " + clip(Json.stringify(act), 200);
        }
        if (exp instanceof Number e) {
            if (!(act instanceof Number a)) return at + "expected number " + Js.str(e.doubleValue()) + ", got " + clip(Json.stringify(act), 120);
            return sameNumber(e.doubleValue(), a.doubleValue()) ? null : at + "expected " + Js.str(e.doubleValue()) + ", got " + Js.str(a.doubleValue());
        }
        if (exp instanceof String || exp instanceof Boolean) {
            return exp.equals(act) ? null : at + "expected " + Json.stringify(exp) + ", got " + clip(Json.stringify(act), 300);
        }
        if (exp instanceof List<?> el) {
            if (!(act instanceof List<?> al)) return at + "expected a list, got " + clip(Json.stringify(act), 200);
            for (int i = 0; i < Math.min(el.size(), al.size()); i++) {
                String d = diff(el.get(i), al.get(i), path + "[" + i + "]");
                if (d != null) return d;
            }
            return el.size() == al.size() ? null : at + "expected " + el.size() + " elements, got " + al.size();
        }
        if (exp instanceof Map<?, ?> em) {
            if (!(act instanceof Map<?, ?> am)) return at + "expected an object, got " + clip(Json.stringify(act), 200);
            List<?> ek = new ArrayList<>(em.keySet());
            List<?> ak = new ArrayList<>(am.keySet());
            if (!ek.equals(ak)) return at + "expected keys " + ek + ", got " + ak;
            for (Object k : ek) {
                String d = diff(em.get(k), am.get(k), path + "." + k);
                if (d != null) return d;
            }
            return null;
        }
        return at + "cannot compare " + exp.getClass().getSimpleName();
    }

    /** Integral expected values (and ±Infinity) exactly, others within 1e-9 × max(1, |expected|); NaN equals NaN. */
    private static boolean sameNumber(double expected, double actual) {
        if (Double.isNaN(expected) || Double.isNaN(actual)) return Double.isNaN(expected) && Double.isNaN(actual);
        if (expected == actual) return true;
        boolean integral = Double.isInfinite(expected) || (expected == Math.rint(expected) && Math.abs(expected) <= 0x1p53);
        return !integral && Math.abs(expected - actual) <= 1e-9 * Math.max(1, Math.abs(expected));
    }

    /** {"$num": "NaN" | "Infinity" | "-Infinity" | "-0"} → the number; recursively. */
    private static Object decode(Object v) {
        if (v instanceof Map<?, ?> m) {
            if (m.size() == 1 && m.get("$num") instanceof String s) {
                return switch (s) {
                    case "NaN" -> Double.NaN;
                    case "Infinity" -> Double.POSITIVE_INFINITY;
                    case "-Infinity" -> Double.NEGATIVE_INFINITY;
                    case "-0" -> -0.0;
                    default -> throw new IllegalArgumentException("bad $num " + s);
                };
            }
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) out.put((String) e.getKey(), decode(e.getValue()));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) out.add(decode(o));
            return out;
        }
        return v;
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ParityTest.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException(name + " is not on the classpath (run tools/parity_dump.mjs)");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ arguments

    /** Decoded arguments of one case. */
    private record Args(List<?> list) {
        boolean has(int i) {
            return i < list.size();
        }

        Object get(int i) {
            return has(i) ? list.get(i) : null;
        }

        double d(int i) {
            return ((Number) list.get(i)).doubleValue();
        }

        Double numOrNull(int i) {
            return get(i) instanceof Number n ? n.doubleValue() : null;
        }

        String s(int i) {
            return (String) get(i);
        }

        boolean b(int i) {
            return (Boolean) list.get(i);
        }

        @SuppressWarnings("unchecked")
        Obj obj(int i) {
            return get(i) == null ? null : new Obj((Map<String, Object>) get(i), "arg" + i);
        }

        Map<String, Double> stats(int i) {
            Map<String, Double> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) list.get(i)).entrySet()) out.put((String) e.getKey(), ((Number) e.getValue()).doubleValue());
            return out;
        }

        Weapon weapon(int i) {
            return Weapons.parseWeapon(s(i));
        }

        Armor.Piece armor(int i) {
            return Armor.parseArmor(s(i));
        }

        Runes.Rune rune(int i) {
            return Runes.parseRune(s(i));
        }
    }

    // ------------------------------------------------------------------ views (same shapes as parity_dump.mjs)

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static List<Object> reqsV(List<Weapons.Req> reqs) {
        List<Object> out = new ArrayList<>();
        for (Weapons.Req r : reqs) out.add(map("stat", r.stat(), "value", r.value()));
        return out;
    }

    private static List<Object> scalingV(List<Weapons.Scaling> list) {
        List<Object> out = new ArrayList<>();
        for (Weapons.Scaling b : list) out.add(map("stat", b.stat(), "per", b.per()));
        return out;
    }

    private static Object weaponV(Weapon w) {
        if (w == null) return null;
        List<Object> skills = null;
        if (w.skills() != null) {
            skills = new ArrayList<>();
            for (Obj k : w.skills()) skills.add(k.strOrNull("id"));
        }
        return map("id", w.id(), "type", w.type(), "level", w.level(), "gradeIndex", w.gradeIndex(), "grade", w.grade().id(),
                "stat", w.stat(), "req", w.req(), "reqs", reqsV(w.reqs()), "scaling", scalingV(w.scaling()), "icon", w.icon(),
                "title", w.title(), "name", w.name(), "darkName", w.darkName(), "typeLabel", w.typeLabel(), "look", w.look(),
                "legacy", w.legacy(), "skills", skills, "damage", w.damage(), "critChance", w.critChance(), "critMult", w.critMult(),
                "passive", w.passive(), "block", w.block());
    }

    private static Object armorV(Armor.Piece a) {
        if (a == null) return null;
        return map("id", a.id(), "type", a.type(), "typeName", a.typeName(), "level", a.level(), "grade", a.grade().id(),
                "gradeIndex", a.gradeIndex(), "piece", a.piece(), "pieceName", a.pieceName(), "slot", a.slot(), "set", a.set(),
                "setName", a.setName(), "prot", a.prot(), "setProt", a.setProt(), "bonus", a.bonus(), "setBonus", a.setBonus(),
                "reqLevel", a.reqLevel(), "reqs", reqsV(a.reqs()), "icon", a.icon(), "title", a.title(), "name", a.name(),
                "darkName", a.darkName());
    }

    private static Object runeV(Runes.Rune r) {
        if (r == null) return null;
        return map("id", r.id(), "family", r.family(), "stat", r.stat(), "famName", r.famName(), "grade", r.grade(),
                "fn", r.def().strOrNull("fn"), "defName", r.def().strOrNull("name"), "cd", r.cd(), "gradeName", r.gradeName(),
                "gradeColor", r.gradeColor());
    }

    private static Object appliedV(Runes.Applied e) {
        List<Object> parts = new ArrayList<>();
        for (Runes.Part p : e.parts()) parts.add(map("stat", p.stat(), "raw", p.raw(), "coef", p.coef()));
        return map("stat", e.stat(), "raw", e.raw(), "value", e.value(), "coef", e.coef(), "parts", parts, "off", e.off(),
                "mix", e.mix(), "hasMix", e.hasMix(), "best", e.best());
    }

    private static List<Object> lackV(List<Weapons.Lack> list) {
        List<Object> out = new ArrayList<>();
        for (Weapons.Lack x : list) out.add(x.isLevel() ? map("level", x.value(), "value", x.value()) : map("stat", x.stat(), "value", x.value()));
        return out;
    }

    private static Object changeV(Hp.Change c) {
        return map("hp", c.hp(), "amount", c.amount());
    }

    // ------------------------------------------------------------------ dispatch (fn names as in parity_dump.mjs)

    private static void fn(String name, Fn f) {
        FNS.put(name, f);
    }

    private static void register() {
        // JS engine behavior the port relies on
        fn("js.String", a -> Js.str(a.d(0)));
        fn("js.Math.round", a -> Js.round(a.d(0)));
        fn("js.toFixed", a -> Js.toFixed(a.d(0), (int) a.d(1)));
        fn("runes.utf8Length", a -> Js.utf8Length(a.s(0)));
        fn("JSON.parse", a -> Json.parse(a.s(0)));

        // config.js
        fn("config.damagePerPoint", a -> Stats.damagePerPoint(a.s(0)));
        fn("config.isValidWeapon", a -> a.has(2) ? Weapons.isValidWeapon(a.d(0), a.s(1), a.s(2)) : Weapons.isValidWeapon(a.d(0), a.s(1)));
        fn("config.nearestGradeLevel", a -> Weapons.nearestGradeLevel(a.d(0), a.s(1)));
        fn("config.gradeLevelsText", a -> Weapons.gradeLevelsText(a.s(0)));
        fn("config.reqUnit", a -> Weapons.reqUnit(a.d(0), a.d(1)));
        fn("config.splitReq", a -> {
            List<Map.Entry<String, Double>> mults = new ArrayList<>();
            for (Object o : (List<?>) a.get(1)) {
                List<?> pair = (List<?>) o;
                mults.add(Map.entry((String) pair.get(0), ((Number) pair.get(1)).doubleValue()));
            }
            return reqsV(Weapons.splitReq(a.d(0), mults));
        });
        fn("config.requiredStat", a -> Weapons.requiredStat(a.d(0), a.d(1), a.s(2)));
        fn("config.requiredStats", a -> reqsV(Weapons.requiredStats(a.d(0), a.d(1), a.s(2))));
        fn("config.damageScaling", a -> scalingV(Weapons.damageScaling(a.s(0))));
        fn("config.skillTotalMult", a -> Skills.skillTotalMult(a.obj(0)));
        fn("config.tyrfingHpFactor", a -> Skills.tyrfingHpFactor(a.d(0)));
        fn("config.runeId", a -> Runes.runeId(a.s(0), a.s(1)));
        fn("config.craftMaterials", a -> {
            List<Object> out = new ArrayList<>();
            for (Crafting.Material m : Crafting.craftMaterials(a.obj(0))) out.add(map("key", m.key(), "kind", m.kind(), "count", m.count()));
            return out;
        });
        fn("config.craftUseLines", a -> Crafting.craftUseLines(a.s(0), a.s(1)));
        fn("config.enhanceBonus", a -> Enhance.enhanceBonus(a.d(0)));
        fn("config.enhanceCost", a -> {
            Enhance.Cost c = Enhance.enhanceCost(a.d(0), a.s(1));
            return c == null ? null : map("rate", c.rate(), "stone", c.stone(), "bright", c.bright(), "down", c.down());
        });
        fn("config.armorId", a -> Armor.armorId(a.s(0), a.d(1), a.s(2), a.s(3)));
        fn("config.monsterId", a -> Mobs.monsterId(a.s(0)));
        fn("config.bossId", a -> Mobs.bossId(a.s(0)));
        fn("config.altarId", a -> Mobs.altarId(a.s(0)));
        fn("config.bossUnitKeys", a -> Mobs.bossUnitKeys(a.s(0)));
        fn("config.unitHomeRadius", a -> Mobs.unitHomeRadius(a.s(0), a.obj(1)));

        // weapons.js · smith.js
        fn("weapons.round1", a -> Weapons.round1(a.d(0)));
        fn("weapons.pct", a -> Weapons.pct(a.d(0)));
        fn("weapons.parseWeapon", a -> weaponV(Weapons.parseWeapon(a.s(0))));
        fn("weapons.weaponFrom", a -> weaponV(Weapons.weaponFrom(a.s(0), a.d(1), a.s(2))));
        fn("weapons.currentWeapon", a -> weaponV(Weapons.currentWeapon(a.weapon(0))));
        fn("weapons.shopPrice", a -> Weapons.shopPrice(a.weapon(0)));
        fn("weapons.needsLevel", a -> Weapons.needsLevel(a.weapon(0)));
        fn("weapons.weaponLack", a -> lackV(Weapons.weaponLack(a.weapon(0), a.d(1), a.stats(2))));
        fn("weapons.levelNote", a -> Weapons.levelNote(a.weapon(0), a.d(1)));
        fn("weapons.weaponInfo", a -> Weapons.weaponInfo(a.weapon(0), a.has(1) && a.b(1), a.has(2) ? a.d(2) : 0));
        fn("weapons.skillLines", a -> Weapons.skillLines(a.weapon(0), a.b(1)));
        fn("weapons.enhancedName", a -> Weapons.enhancedName(a.weapon(0), a.d(1), a.s(2)));
        fn("weapons.reqsText", a -> {
            List<Weapons.Req> reqs = new ArrayList<>();
            for (Object o : (List<?>) a.get(0)) {
                Map<?, ?> r = (Map<?, ?>) o;
                reqs.add(new Weapons.Req((String) r.get("stat"), ((Number) r.get("value")).doubleValue()));
            }
            if (a.has(2)) return Weapons.reqsText(reqs, a.b(1), a.s(2));
            return a.has(1) ? Weapons.reqsText(reqs, a.b(1)) : Weapons.reqsText(reqs);
        });
        fn("weapons.enhanceOf", a -> Enhance.enhanceOf(a.get(0)));
        fn("smith.nextLevelOf", a -> {
            Weapons.SmithStep n = Weapons.nextLevelOf(a.weapon(0));
            return n == null ? null : map("to", n.to().id(), "emerald", n.emerald(), "ore", n.ore());
        });

        // combat.js
        fn("combat.isMeleeWeapon", a -> Combat.isMeleeWeapon(a.weapon(0)));
        fn("combat.statMultiplier", a -> Combat.statMultiplier(a.stats(0), a.weapon(1)));
        fn("combat.powerMultiplier", a -> Combat.powerMultiplier(a.stats(0), a.weapon(1), a.d(2)));
        fn("combat.critChanceOf", a -> a.has(2) ? Combat.critChanceOf(a.weapon(0), a.d(1), a.d(2)) : Combat.critChanceOf(a.weapon(0), a.d(1)));
        fn("combat.reqText", a -> a.has(1) ? Combat.reqText(a.weapon(0), a.d(1), a.stats(2)) : Combat.reqText(a.weapon(0)));

        // stats.js · hp.js · duel.js
        fn("stats.getStatsCached", a -> Stats.effectiveStats(a.stats(0), a.stats(1), a.d(2)));
        fn("stats.reqLevelOf", a -> Stats.reqLevelOf(a.d(0), a.d(1)));
        fn("stats.checkLevelUp", a -> Stats.checkLevelUp(a.d(0), a.d(1)));
        fn("hp.maxHpOf", a -> Hp.maxHpOf(a.d(0), a.d(1)));
        fn("hp.levelHpOf", a -> Hp.levelHpOf(a.d(0)));
        fn("hp.hpNumber", a -> Hp.hpNumber(a.b(0), a.d(1)));
        fn("hp.refreshMaxHp", a -> Hp.refreshMaxHp(a.d(0), a.d(1), a.d(2), a.b(3), a.b(4)));
        fn("hp.payHp", a -> changeV(Hp.payHp(a.d(0), a.d(1), a.b(2))));
        fn("hp.healBy", a -> changeV(Hp.healBy(a.d(0), a.d(1), a.d(2), a.b(3))));
        fn("hp.onPlayerHealBefore", a -> Hp.vanillaHeal(a.d(0), a.d(1), a.d(2), a.b(3)));
        fn("hp.revive", a -> Hp.reviveHp(a.d(0)));
        fn("hp.saveHp", a -> Hp.saveRatio(a.b(0), a.d(1), a.d(2)));
        fn("duel.duelDamage", a -> a.has(2) ? Pvp.duelDamage(a.d(0), a.d(1), a.d(2), a.d(3)) : Pvp.duelDamage(a.d(0), a.d(1)));

        // armor.js
        fn("armor.parseArmor", a -> armorV(Armor.parseArmor(a.s(0))));
        fn("armor.armorFrom", a -> armorV(Armor.armorFrom(a.s(0), a.d(1), a.s(2), a.s(3))));
        fn("armor.bonusText", a -> Armor.bonusText(a.stats(0), a.has(1) && a.b(1)));
        fn("armor.armorReqText", a -> Armor.armorReqText(a.armor(0)));
        fn("armor.armorInfo", a -> Armor.armorInfo(a.armor(0), a.has(1) && a.b(1)));
        fn("armor.armorLevelFor", a -> Armor.armorLevelFor(a.d(0)));
        fn("armor.armorPrice", a -> Armor.armorPrice(a.armor(0)));
        fn("armor.setPrice", a -> Armor.setPrice(a.s(0), a.d(1)));
        fn("armor.armorLack", a -> Armor.armorLack(a.armor(0), a.d(1), a.stats(2)));

        // runes.js · runesmith.js
        fn("runes.parseRune", a -> runeV(Runes.parseRune(a.s(0))));
        fn("runesmith.grindValue", a -> Runes.grindValue(a.rune(0)));
        fn("runes.runeLevelBase", a -> Runes.runeLevelBase(a.d(0)));
        fn("runes.mixBonus", a -> Runes.mixBonus(a.stats(0), a.numOrNull(1), a.numOrNull(2)));
        fn("runes.runeStat", a -> appliedV(Runes.runeStat(a.stats(0), a.s(1))));
        fn("runes.runeStatLabel", a -> {
            Runes.Applied e = Runes.runeStat(a.stats(0), a.s(1));
            return a.has(2) ? Runes.runeStatLabel(e, a.s(2)) : Runes.runeStatLabel(e);
        });
        fn("runes.runePower", a -> Runes.runePower(a.stats(0), a.s(1)));
        fn("runes.supportScale", a -> Runes.supportScale(a.stats(0), a.s(1)));
        fn("runes.runeTotalMult", a -> Runes.runeTotalMult(a.obj(0)));
        fn("runes.gradeWeights", a -> Runes.gradeWeights(a.b(0)));
        fn("runes.rollRune", a -> {
            Deque<Double> seq = new ArrayDeque<>(List.of(a.d(0), a.d(1)));
            Runes.Roll r = Runes.rollRune(seq::removeFirst, a.b(2));
            return map("family", r.family(), "stat", r.stat(), "grade", r.grade());
        });
        fn("runes.runeLore", a -> Runes.runeLore(a.rune(0), a.has(1) && a.b(1)));
    }
}
