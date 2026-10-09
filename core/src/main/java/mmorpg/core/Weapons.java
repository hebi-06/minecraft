package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weapons: the weapon rules of config.js (valid combinations, requirements, damage scaling), the
 * item id scheme and lore helpers of weapons.js, and smith.js {@code nextLevelOf}.
 */
public final class Weapons {
    private Weapons() {}

    private static final Rules R = Rules.get();
    private static final Obj CONFIG = R.obj("CONFIG");
    private static final List<Double> LEVELS = R.numbers("LEVELS");
    private static final Obj GRADE_LEVELS = R.obj("GRADE_LEVELS");
    private static final Obj TYPE_ONLY = R.obj("TYPE_ONLY");
    private static final Obj TYPES = R.obj("TYPES");
    private static final Obj WEAPON_NAMES = R.obj("WEAPON_NAMES");
    private static final Obj LOOK = R.obj("LOOK");
    private static final Obj LEVEL_DAMAGE = R.obj("LEVEL_DAMAGE");
    private static final Obj REQ = R.obj("REQ");
    private static final Obj SHIELD = R.obj("SHIELD");
    private static final Obj SHOP = R.obj("SHOP");
    private static final Obj SMITH = R.obj("SMITH");
    private static final Obj WAND = R.obj("WAND");
    private static final Obj BOW = R.obj("BOW");
    private static final Obj SKILLS = R.obj("SKILLS");
    private static final double CRIT_CAP = R.num("CRIT_CAP");

    /** weapons.js ID_RE ({@code matches()} = JS {@code ^…$}). */
    private static final Pattern ID_RE = Pattern.compile("rpg:(sword|dagger|axe|shield|bow|wand|greatsword)_l([0-9]+)_([a-z]+)");
    /** parseWeapon cache, as weapons.js (empty = not a weapon). */
    private static final Map<String, Optional<Weapon>> CACHE = new ConcurrentHashMap<>();

    /** One stat requirement. */
    public record Req(String stat, double value) {}

    /** One stat that raises weapon damage by {@code per} per point. */
    public record Scaling(String stat, double per) {}

    /** Something missing to use a weapon: a level ({@code stat == null}, {@code value} = level) or a stat. */
    public record Lack(String stat, double value) {
        public static Lack level(double level) {
            return new Lack(null, level);
        }

        public boolean isLevel() {
            return stat == null;
        }
    }

    /** smith.js nextLevelOf: the raised hero weapon and its cost. */
    public record SmithStep(Weapon to, double emerald, double ore) {}

    // ------------------------------------------------------------------ config.js

    /** config.js isValidWeapon without a type (grade table only). */
    public static boolean isValidWeapon(double level, String gradeId) {
        return isValidWeapon(level, gradeId, null);
    }

    /** config.js isValidWeapon: a combination of the current rules (TYPE_ONLY types: only their listed ones). */
    public static boolean isValidWeapon(double level, String gradeId, String type) {
        Obj only = type != null && !type.isEmpty() ? TYPE_ONLY.objOrNull(type) : null;
        List<Double> list = only != null ? only.numbersOrNull(gradeId) : GRADE_LEVELS.numbersOrNull(gradeId);
        return list != null && includes(list, level);
    }

    /** config.js nearestGradeLevel: the grade's level closest to {@code level} (first one on ties). */
    public static double nearestGradeLevel(double level, String gradeId) {
        List<Double> list = gradeLevels(gradeId);
        double best = list.get(0);
        for (double l : list) if (Math.abs(l - level) < Math.abs(best - level)) best = l;
        return best;
    }

    /** config.js gradeLevelsText: "30·35·40렙". */
    public static String gradeLevelsText(String gradeId) {
        StringJoiner j = new StringJoiner("·");
        for (double l : gradeLevels(gradeId)) j.add(Js.str(l));
        return j + "렙";
    }

    /** config.js reqUnit (weapons and armor): (level + grade index × gradeStep) × perLevel. */
    public static double reqUnit(double level, double gradeIndex) {
        return (level + gradeIndex * REQ.num("gradeStep")) * REQ.num("perLevel");
    }

    /**
     * config.js splitReq: each stat needs ceil(unit × multiplier); with several stats the last one is
     * lowered so the sum does not exceed ceil(unit × Σ multipliers).
     */
    public static List<Req> splitReq(double unit, List<Map.Entry<String, Double>> mults) {
        List<Req> out = new ArrayList<>(mults.size());
        for (Map.Entry<String, Double> e : mults) out.add(new Req(e.getKey(), Math.ceil(unit * e.getValue() - 1e-9)));
        if (out.size() > 1) {
            double m = 0;
            for (Map.Entry<String, Double> e : mults) m = m + e.getValue();
            double total = Math.ceil(unit * m - 1e-9);
            double sum = 0;
            for (Req r : out) sum = sum + r.value();
            Req last = out.get(out.size() - 1);
            out.set(out.size() - 1, new Req(last.stat(), Math.max(0, last.value() - Math.max(0, sum - total))));
        }
        return out;
    }

    /** config.js requiredStat: the main stat requirement. */
    public static double requiredStat(double level, double gradeIndex, String type) {
        return requiredStats(level, gradeIndex, type).get(0).value();
    }

    /** config.js requiredStats: all requirements of a weapon type, main stat first (unknown type: 힘 × 1). */
    public static List<Req> requiredStats(double level, double gradeIndex, String type) {
        Obj t = TYPES.objOrNull(type);
        List<Map.Entry<String, Double>> mults = new ArrayList<>();
        mults.add(Map.entry(t == null ? "str" : t.strOr("stat", "str"), t == null ? 1 : t.numOr("req", 1)));
        Obj extra = t == null ? null : t.objOrNull("reqExtra");
        if (extra != null) for (String s : extra.keys()) mults.add(Map.entry(s, extra.num(s)));
        return splitReq(reqUnit(level, gradeIndex), mults);
    }

    /** config.js damageScaling: damage multiplier = 1 + Σ stat × per (main stat first). */
    public static List<Scaling> damageScaling(String type) {
        Obj t = TYPES.objOrNull(type);
        String stat = t == null ? "str" : t.strOr("stat", "str");
        Double own = t == null ? null : t.numOrNull("perPoint");
        List<Scaling> out = new ArrayList<>();
        out.add(new Scaling(stat, own != null ? own : Stats.damagePerPoint(stat)));
        Obj bonus = t == null ? null : t.objOrNull("bonus");
        if (bonus != null) for (String s : bonus.keys()) out.add(new Scaling(s, bonus.num(s)));
        return out;
    }

    // ------------------------------------------------------------------ item ids (weapons.js)

    /** The item id weaponFrom builds: {@code rpg:<type>_l<level>_<gradeId>}. */
    public static String weaponId(String type, double level, String gradeId) {
        return "rpg:" + type + "_l" + Js.str(level) + "_" + gradeId;
    }

    /**
     * weapons.js parseWeapon: the weapon of an item id, or null. Accepts the current combinations and
     * legacy ones (any LEVEL_DAMAGE level of the six table types); TYPE_ONLY types only their own.
     */
    public static Weapon parseWeapon(String typeId) {
        if (typeId == null || !typeId.startsWith("rpg:")) return null;
        return CACHE.computeIfAbsent(typeId, Weapons::make).orElse(null);
    }

    /** weapons.js weaponFrom. */
    public static Weapon weaponFrom(String type, double level, String gradeId) {
        return parseWeapon(weaponId(type, level, gradeId));
    }

    /** weapons.js currentWeapon: a legacy weapon becomes the same grade's nearest current level. */
    public static Weapon currentWeapon(Weapon w) {
        if (w == null || !w.legacy()) return w;
        Weapon cur = weaponFrom(w.type(), nearestGradeLevel(w.level(), w.grade().id()), w.grade().id());
        return cur != null ? cur : w;
    }

    private static Optional<Weapon> make(String typeId) {
        Matcher m = ID_RE.matcher(typeId);
        if (!m.matches()) return Optional.empty();
        String type = m.group(1);
        double level = Double.parseDouble(m.group(2)); // Number("040") = 40
        Grade g = R.grade(m.group(3));
        Obj only = TYPE_ONLY.objOrNull(type);
        if (g == null || !LEVEL_DAMAGE.has(Js.str(level)) || (only != null && !isValidWeapon(level, g.id(), type))) return Optional.empty();
        return Optional.of(makeWeapon(type, (int) level, g));
    }

    private static Weapon makeWeapon(String type, int level, Grade g) {
        Obj t = TYPES.obj(type);
        int gi = g.index();
        List<Req> reqs = requiredStats(level, gi, type);
        Obj look = LOOK.objOrNull(type + "_" + g.id());
        Obj names = WEAPON_NAMES.objOrNull(type);
        String designName = names == null ? null : names.strOrNull(g.id());
        String title = designName != null ? designName : g.name() + " " + t.str("name");
        boolean legacy = !isValidWeapon(level, g.id(), type);
        List<Obj> skills = g.id().equals("transcend") && !legacy ? Skills.weaponSkills(type) : null;
        if (skills != null) skills = List.copyOf(skills);
        double damage = round1(LEVEL_DAMAGE.num(Js.str(level)) * t.num("damage") * g.mult());
        // 치명타는 무기 종류 · 레벨 · 등급으로만 (스탯은 치명타를 올리지 않음)
        double critChance = Math.min(CRIT_CAP, t.num("crit") + level * t.num("critPerLevel") + gi * t.num("critPerGrade"));
        double critMult = Js.round((t.num("critMult") + level * t.num("critMultPerLevel") + gi * t.num("critMultPerGrade")) * 100) / 100;
        double passive = 0;
        double block = 0;
        if (type.equals("shield")) {
            passive = Math.min(SHIELD.num("passiveCap"), SHIELD.num("passiveBase") + level * SHIELD.num("passivePerLevel") + gi * SHIELD.num("passivePerGrade"));
            block = Math.min(SHIELD.num("blockCap"), SHIELD.num("blockBase") + level * SHIELD.num("blockPerLevel") + gi * SHIELD.num("blockPerGrade"));
        }
        String key = type + "_l" + level + "_" + g.id();
        String label = look == null ? null : look.strOrNull("label");
        return new Weapon("rpg:" + key, type, level, gi, g, t, t.str("stat"), reqs.get(0).value(), List.copyOf(reqs), List.copyOf(damageScaling(type)),
                "textures/items/rpg/" + type + "/" + key, title,
                g.color() + title + "§r§7 [" + level + "렙]", g.dark() + title + "§r§8 [" + level + "렙]",
                label != null ? label : t.str("name"), look == null ? null : look.strOrNull("slash"),
                legacy, skills, damage, critChance, critMult, passive, block);
    }

    // ------------------------------------------------------------------ prices and requirements (weapons.js)

    /** weapons.js round1: one decimal ({@code Math.round(x * 10) / 10}). */
    public static double round1(double x) {
        return Js.round(x * 10) / 10;
    }

    /** weapons.js pct: "12.5%". */
    public static String pct(double x) {
        return Js.str(round1(x * 100)) + "%";
    }

    /** weapons.js shopPrice (에메랄드). */
    public static double shopPrice(Weapon w) {
        double base = SHOP.obj("basePrice").numOr(Js.str(w.level()), 1);
        return Math.max(1, Math.ceil(base * SHOP.obj("gradePriceMult").numOr(w.grade().id(), 1) * SHOP.obj("typePriceMult").numOr(w.type(), 1)));
    }

    /** weapons.js reqsText, light-background colors off: "§c힘 22§7 · §d체력 11". */
    public static String reqsText(List<Req> reqs) {
        return reqsText(reqs, false);
    }

    public static String reqsText(List<Req> reqs, boolean dark) {
        return reqsText(reqs, dark, dark ? "§8 · " : "§7 · ");
    }

    public static String reqsText(List<Req> reqs, boolean dark, String sep) {
        StringJoiner j = new StringJoiner(sep);
        for (Req r : reqs) {
            Obj info = Stats.info(r.stat());
            j.add((dark ? info.str("dark") : info.str("color")) + info.str("name") + " " + Js.str(r.value()));
        }
        return j.toString();
    }

    /** weapons.js needsLevel: level-gated weapon (렙제, not for level 0). */
    public static boolean needsLevel(Weapon w) {
        return CONFIG.truthy("WEAPON_LEVEL_REQUIRED") && w != null && w.level() > 0;
    }

    /**
     * weapons.js weaponLack: what is missing to use {@code w} at highest XP level {@code lv} with
     * spent stats {@code s} (armor bonus not counted). Empty when usable.
     */
    public static List<Lack> weaponLack(Weapon w, double lv, Map<String, Double> s) {
        List<Lack> out = new ArrayList<>();
        if (needsLevel(w) && lv < w.level()) out.add(Lack.level(w.level()));
        if (CONFIG.truthy("REQUIREMENT_ENABLED")) {
            for (Req r : w.reqs()) if (Stats.get(s, r.stat()) < r.value()) out.add(new Lack(r.stat(), r.value()));
        }
        return out;
    }

    /** weapons.js levelNote: " §7(레벨 40부터 사용 · 지금 최고 레벨 32)" while the level is too low, else "". */
    public static String levelNote(Weapon w, double lv) {
        return needsLevel(w) && lv < w.level() ? " §7(레벨 " + w.level() + "부터 사용 · 지금 최고 레벨 " + Js.str(lv) + ")" : "";
    }

    // ------------------------------------------------------------------ lore (weapons.js)

    /** weapons.js weaponInfo (item lore, no enhancement). */
    public static List<String> weaponInfo(Weapon w) {
        return weaponInfo(w, false, 0);
    }

    /**
     * weapons.js weaponInfo: item lore lines. {@code dark} = colors for light backgrounds (chest UI);
     * {@code enh} = enhancement level (adds the enhanced damage and an enhancement line).
     */
    public static List<String> weaponInfo(Weapon w, boolean dark, double enh) {
        String L = dark ? "§8" : "§7";
        String V = dark ? "§0" : "§f";
        List<String> lines = new ArrayList<>();
        lines.add("§r" + L + w.typeLabel() + " · 레벨 " + w.level() + " · " + (dark ? w.grade().dark() : w.grade().color()) + w.grade().name());
        if (w.type().equals("shield")) lines.add("§r" + L + "오른손에 들고 때리는 무기");
        String label = w.type().equals("wand") ? "마법 피해" : w.type().equals("bow") ? "화살 피해" : "공격력";
        if (enh > 0) {
            double bonus = Enhance.enhanceBonus(enh);
            lines.add("§r" + L + label + " " + V + Js.str(round1(w.damage() * (1 + bonus))) + L + " (기본 " + Js.str(w.damage()) + ")");
            lines.add("§r§6강화 +" + Js.str(enh) + L + " · 피해 +" + Js.str(Js.round(bonus * 100)) + "%");
        } else {
            lines.add("§r" + L + label + " " + V + Js.str(w.damage()));
        }
        if (w.type().equals("wand")) {
            lines.add("§r" + L + "사거리 " + V + WAND.text("range") + "칸" + L + " · 재사용 " + V + Js.str(WAND.num("cooldownTicks") / 20) + "초");
            lines.add("§r" + L + "시전 " + V + Js.str(WAND.num("castTicks") / 20) + "초" + L + " (선딜레이)");
        }
        if (w.type().equals("bow")) lines.add("§r" + L + "사거리 " + V + BOW.text("range") + "칸" + L + " · 재사용 " + V + Js.str(BOW.num("cooldownTicks") / 20) + "초");
        lines.add("§r" + L + "치명타 " + V + pct(w.critChance()) + L + " · 배율 " + V + "×" + Js.str(w.critMult()));
        for (Scaling b : w.scaling()) {
            if (b.per() > 0) lines.add("§r" + L + Stats.info(b.stat()).str("name") + " 1포인트당 피해 " + V + "+" + Js.str(round1(b.per() * 100)) + "%");
        }
        if (w.type().equals("greatsword")) {
            Obj tyrfing = SKILLS.objOrNull("tyrfing");
            lines.add("§r" + L + "스킬: 검(공격) + " + (dark ? "§1" : "§9") + "마력(마법)");
            lines.add("§r" + L + "스킬 피해 × 최대 체력 ÷ " + V + Js.str(tyrfing == null ? 100 : tyrfing.numOr("hpBase", 100)));
        }
        if (w.type().equals("shield")) {
            lines.add("§r" + L + "피해 감소 " + V + pct(w.passive()));
            lines.add("§r" + L + "체력 1포인트당 감소 " + V + "+" + Js.str(round1(SHIELD.num("passivePerVit") * 100)) + "%p");
            lines.add("§r" + L + "웅크려 막기 " + V + pct(w.block()));
            lines.add("§r" + L + "때리면 " + V + Js.str(SHIELD.num("bashBlockLockTicks") / 20) + "초" + L + " 동안 막기 풀림");
        }
        if (needsLevel(w)) lines.add("§r" + L + "요구 레벨: " + V + w.level());
        if (CONFIG.truthy("REQUIREMENT_ENABLED")) lines.add("§r" + L + "요구 스탯: " + reqsText(w.reqs(), dark));
        if (w.skills() != null) lines.addAll(skillLines(w, dark));
        if (w.legacy()) lines.add("§r§8구버전 무기 (새로 얻을 수 없음)");
        return lines;
    }

    /** weapons.js skillLines: controls line + name/cooldown and description lines per skill. */
    public static List<String> skillLines(Weapon w, boolean dark) {
        String L = dark ? "§8" : "§7";
        String N = dark ? "§0" : "§f";
        String B = dark ? "§3" : "§b";
        boolean ranged = w.type().equals("bow") || w.type().equals("wand");
        List<String> out = new ArrayList<>();
        out.add(ranged ? "§r" + L + "웅크리면: 스킬1, 길게: 스킬2" : "§r" + L + "버튼: 스킬1, 웅크리면: 스킬2");
        for (int i = 0; i < w.skills().size(); i++) {
            Obj k = w.skills().get(i);
            out.add("§r" + B + "스킬" + (i + 1) + " " + N + k.text("name") + " " + L + k.text("cd") + "초");
            out.add("§r" + L + " " + k.text("desc"));
            if (k.truthy("desc2")) out.add("§r" + L + " " + k.text("desc2"));
        }
        return out;
    }

    /** weapons.js enhancedName: "§r<color>+7 그람§r§7 [40렙]" ({@code custom} = anvil name or null). */
    public static String enhancedName(Weapon w, double n, String custom) {
        return "§r" + w.grade().color() + "+" + Js.str(n) + " " + (custom != null ? custom : w.title()) + "§r§7 [" + w.level() + "렙]";
    }

    // ------------------------------------------------------------------ smith (smith.js)

    /** smith.js nextLevelOf: a current hero weapon raised by SMITH.step levels, or null. */
    public static SmithStep nextLevelOf(Weapon w) {
        if (w == null || !w.grade().id().equals("hero") || w.legacy()) return null;
        double next = w.level() + SMITH.num("step");
        if (!isValidWeapon(next, "hero")) return null;
        Obj cost = SMITH.obj("cost").objOrNull(Js.str(w.level()));
        if (cost == null) return null;
        Weapon to = weaponFrom(w.type(), next, "hero");
        return to == null ? null : new SmithStep(to, cost.num("emerald"), cost.num("ore"));
    }

    // ------------------------------------------------------------------ helpers

    /** GRADE_LEVELS[gradeId] ?? LEVELS. */
    private static List<Double> gradeLevels(String gradeId) {
        List<Double> list = GRADE_LEVELS.numbersOrNull(gradeId);
        return list != null ? list : LEVELS;
    }

    /** {@code Array.prototype.includes} (SameValueZero: 0 equals -0). */
    static boolean includes(List<Double> list, double x) {
        for (double v : list) if (v == x || (Double.isNaN(v) && Double.isNaN(x))) return true;
        return false;
    }
}
