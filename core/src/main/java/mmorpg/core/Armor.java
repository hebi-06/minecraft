package mmorpg.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Armor: config.js {@code armorId} and the pure parts of armor.js (parse an item id into protection,
 * stat bonus and requirements; lore; shop prices; what is missing to wear it).
 */
public final class Armor {
    private Armor() {}

    private static final Rules R = Rules.get();
    private static final Obj TYPES = R.obj("ARMOR_TYPES");
    private static final Obj PIECES = R.obj("ARMOR_PIECES");
    private static final Obj TIERS = R.obj("ARMOR_TIERS");
    private static final Obj GRADE_LEVELS = R.obj("ARMOR_GRADE_LEVELS");
    private static final Obj SHOP_BASE = R.obj("SHOP").obj("basePrice");
    private static final double SET_DISCOUNT = R.obj("ARMOR_SHOP").num("setDiscount");
    private static final List<Double> LEVELS = R.numbers("ARMOR_LEVELS");

    /** ARMOR_PIECE_ORDER: helmet, chest, legs, boots. */
    public static final List<String> PIECE_ORDER = R.strings("ARMOR_PIECE_ORDER");

    /** armor.js ID_RE. */
    private static final Pattern ID_RE = Pattern.compile("rpg:armor_(str|dex|vit|strvit|int)_l([0-9]+)_(common|transcend)_(helmet|chest|legs|boots)");
    /** parseArmor cache, as armor.js (empty = not armor). */
    private static final Map<String, Optional<Piece>> CACHE = new ConcurrentHashMap<>();

    /**
     * One armor item (armor.js makeArmor).
     *
     * @param prot     armor points of this piece, {@code setProt}: of all four pieces
     * @param bonus    stat bonus of this piece, {@code setBonus}: of the full set (type's stat split)
     * @param reqLevel highest XP level needed, {@code reqs}: spent stats needed (as weapons)
     */
    public record Piece(
            String id,
            String type,
            String typeName,
            int level,
            Grade grade,
            int gradeIndex,
            String piece,
            String pieceName,
            String slot,
            String set,
            String setName,
            double prot,
            double setProt,
            Map<String, Double> bonus,
            Map<String, Double> setBonus,
            int reqLevel,
            List<Weapons.Req> reqs,
            String icon,
            String title,
            String name,
            String darkName) {}

    /** config.js armorId: {@code rpg:armor_<type>_l<level>_<grade>_<piece>}. */
    public static String armorId(String type, double level, String grade, String piece) {
        return "rpg:armor_" + type + "_l" + Js.str(level) + "_" + grade + "_" + piece;
    }

    /** armor.js parseArmor: the armor of an item id, or null. */
    public static Piece parseArmor(String typeId) {
        if (typeId == null || !typeId.startsWith("rpg:armor_")) return null;
        return CACHE.computeIfAbsent(typeId, Armor::make).orElse(null);
    }

    /** armor.js armorFrom. */
    public static Piece armorFrom(String type, double level, String grade, String piece) {
        return parseArmor(armorId(type, level, grade, piece));
    }

    private static Optional<Piece> make(String typeId) {
        Matcher m = ID_RE.matcher(typeId);
        if (!m.matches()) return Optional.empty();
        return Optional.ofNullable(makeArmor(m.group(1), Double.parseDouble(m.group(2)), m.group(3), m.group(4)));
    }

    private static Piece makeArmor(String type, double level, String grade, String piece) {
        Obj t = TYPES.objOrNull(type);
        Obj tier = TIERS.objOrNull(grade + "_" + Js.str(level));
        Obj p = PIECES.objOrNull(piece);
        Grade g = R.grade(grade);
        List<Double> levels = GRADE_LEVELS.numbersOrNull(grade);
        if (t == null || tier == null || p == null || g == null || levels == null || !Weapons.includes(levels, level)) return null;
        int pi = PIECE_ORDER.indexOf(piece);
        // 요구 스탯: 무기와 같은 요구 단위 × 종류 배율 (40렙 일반 100 · 초월 110)
        List<Map.Entry<String, Double>> mults = new ArrayList<>();
        Obj req = t.obj("req");
        for (String s : req.keys()) mults.add(Map.entry(s, req.num(s)));
        List<Weapons.Req> reqs = Weapons.splitReq(Weapons.reqUnit(level, g.index()), mults).stream().filter(r -> r.value() > 0).toList();
        String levelText = grade.equals("transcend") ? "초월" : Js.str(level) + "렙";
        List<Double> tierProt = tier.numbersOrNull("prot");
        double typeProt = t.num("prot");
        double setProt = 0;
        for (double v : tierProt) setProt = setProt + Math.max(1, Js.round(v * typeProt));
        Obj stats = t.obj("stats");
        Map<String, Double> setBonus = new LinkedHashMap<>();
        for (String s : stats.keys()) setBonus.put(s, Js.round(tier.num("bonus") * stats.num(s)));
        Map<String, Double> bonus = splitBonus(stats, tier.num("bonus")).get(piece);
        String title = tier.str("name") + " " + p.str("name");
        String id = armorId(type, level, grade, piece);
        return new Piece(id, type, t.str("name"), (int) level, g, g.index(), piece, p.str("name"), p.str("slot"),
                tier.str("set"), tier.str("name"),
                Math.max(1, Js.round(tierProt.get(pi) * typeProt)), setProt,
                Collections.unmodifiableMap(bonus), Collections.unmodifiableMap(setBonus), (int) level, reqs,
                "textures/items/rpg/armor/" + type + "_l" + Js.str(level) + "_" + grade + "_" + piece, title,
                g.color() + title + "§r§7 · " + t.str("name") + " [" + levelText + "]",
                g.dark() + title + "§r§8 · " + t.str("name") + " [" + levelText + "]");
    }

    /**
     * armor.js splitBonus: the set bonus of each stat (round(total × share of the type)) split over the
     * pieces by ARMOR_PIECES share, flooring and then handing the remainder out one by one to the
     * largest fractions (earlier piece first on ties) so the pieces add up exactly.
     */
    private static Map<String, Map<String, Double>> splitBonus(Obj stats, double total) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (String p : PIECE_ORDER) out.put(p, new LinkedHashMap<>());
        int n = PIECE_ORDER.size();
        for (String stat : stats.keys()) {
            double sum = Js.round(total * stats.num(stat));
            double[] raw = new double[n];
            double[] got = new double[n];
            double have = 0;
            for (int i = 0; i < n; i++) {
                raw[i] = sum * PIECES.obj(PIECE_ORDER.get(i)).num("share");
                got[i] = Math.floor(raw[i]);
                have = have + got[i];
            }
            double left = sum - have;
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> {
                double d = (raw[b] - Math.floor(raw[b])) - (raw[a] - Math.floor(raw[a]));
                if (d > 0) return 1;
                if (d < 0) return -1;
                return Integer.compare(a, b);
            });
            for (int j = 0; left > 0; j++, left--) got[order[j % n]]++;
            for (int i = 0; i < n; i++) if (got[i] > 0) out.get(PIECE_ORDER.get(i)).put(stat, got[i]);
        }
        return out;
    }

    // ------------------------------------------------------------------ text

    /** armor.js bonusText: "§c힘 §f+4§7 · §d체력 §f+4" (positive entries only). */
    public static String bonusText(Map<String, Double> b, boolean dark) {
        String v = dark ? "§0" : "§f";
        StringJoiner j = new StringJoiner(dark ? "§8 · " : "§7 · ");
        for (Map.Entry<String, Double> e : b.entrySet()) {
            if (!(e.getValue() > 0)) continue;
            Obj info = Stats.info(e.getKey());
            j.add((dark ? info.str("dark") : info.str("color")) + info.str("name") + " " + v + "+" + Js.str(e.getValue()));
        }
        return j.toString();
    }

    /** armor.js armorReqText: "레벨 30 · 힘 30" (no colors). */
    public static String armorReqText(Piece a) {
        StringJoiner j = new StringJoiner(" · ");
        j.add("레벨 " + a.reqLevel());
        for (Weapons.Req r : a.reqs()) j.add(Stats.info(r.stat()).str("name") + " " + Js.str(r.value()));
        return j.toString();
    }

    /** armor.js bonusPlain: "힘 +4 · 체력 +4" (no colors). */
    private static String bonusPlain(Map<String, Double> b) {
        StringJoiner j = new StringJoiner(" · ");
        for (Map.Entry<String, Double> e : b.entrySet()) {
            if (e.getValue() > 0) j.add(Stats.info(e.getKey()).str("name") + " +" + Js.str(e.getValue()));
        }
        return j.toString();
    }

    /** armor.js armorInfo: item lore (this piece, the full set, requirements). */
    public static List<String> armorInfo(Piece a, boolean dark) {
        String l = dark ? "§8" : "§7";
        String v = dark ? "§0" : "§f";
        String piece = bonusPlain(a.bonus());
        String set = bonusPlain(a.setBonus());
        return List.of(
                "§r" + l + a.typeName() + " 갑옷 · " + a.pieceName() + " · " + (dark ? a.grade().dark() : a.grade().color()) + a.grade().name(),
                "§r" + l + "방어 " + v + "+" + Js.str(a.prot()) + l + (piece.isEmpty() ? "" : " · " + piece),
                "§r" + l + "세트 방어 " + Js.str(a.setProt()) + (set.isEmpty() ? "" : " · " + set),
                "§r" + l + "요구: " + armorReqText(a),
                "§r§8요구 조건이 모자라면 벗겨짐");
    }

    /**
     * armor.js armorLack: what is missing to wear {@code a}: "레벨 30", "힘 30", ... ({@code reqLevel} =
     * stats.js reqLevelOf, {@code baseStats} = spent stats, armor bonus not counted).
     */
    public static List<String> armorLack(Piece a, double reqLevel, Map<String, Double> baseStats) {
        List<String> out = new ArrayList<>();
        if (reqLevel < a.reqLevel()) out.add("레벨 " + a.reqLevel());
        for (Weapons.Req r : a.reqs()) {
            if (Stats.get(baseStats, r.stat()) < r.value()) out.add(Stats.info(r.stat()).str("name") + " " + Js.str(r.value()));
        }
        return out;
    }

    // ------------------------------------------------------------------ shop

    /** armor.js armorLevelFor: the armor level a shop of this level sells (highest ARMOR_LEVELS entry ≤ level). */
    public static double armorLevelFor(double level) {
        double best = LEVELS.get(0);
        for (double l : LEVELS) if (l <= level) best = l;
        return best;
    }

    /** armor.js armorPrice: SHOP.basePrice of the level × the piece's price share, rounded up, at least 1. */
    public static double armorPrice(Piece a) {
        double base = SHOP_BASE.numOr(Js.str(a.level()), 1);
        return Math.max(1, Math.ceil(base * PIECES.obj(a.piece()).num("price") - 1e-9));
    }

    /** armor.js setPrice: the four common pieces with the set discount (floored). */
    public static double setPrice(String type, double level) {
        double sum = 0;
        for (String p : PIECE_ORDER) {
            Piece a = armorFrom(type, level, "common", p);
            if (a == null) throw new IllegalArgumentException("no common armor " + type + " at level " + Js.str(level));
            sum = sum + armorPrice(a);
        }
        return Math.max(1, Math.floor(sum * SET_DISCOUNT));
    }
}
