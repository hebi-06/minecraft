package mmorpg.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.DoubleSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rune skills: config.js {@code runeId} and the pure parts of runes.js (item ids, power P = level
 * base × (1 + applied stat × RUNE.perPoint), damage totals, the draw, lore) and runesmith.js
 * {@code grindValue}. Stats are passed in as a map (runes.js reads them from the player:
 * getStatsCached, i.e. {@link Stats#effectiveStats}).
 */
public final class Runes {
    private Runes() {}

    private static final Rules R = Rules.get();
    private static final Obj RUNE = R.obj("RUNE");
    private static final Obj RUNES = R.obj("RUNES");
    private static final Obj FAMILIES = R.obj("RUNE_FAMILIES");
    private static final Obj RUNE_GRADES = R.obj("RUNE_GRADES");
    private static final Obj RUNE_STRIKE = R.obj("RUNE_STRIKE");
    private static final Obj RUNE_BODY = R.obj("RUNE_BODY");
    private static final Obj RUNE_COMBO = R.obj("RUNE_COMBO");
    private static final Obj RUNE_SPELLBLADE = R.obj("RUNE_SPELLBLADE");
    private static final Obj RUNE_SPIRITS = R.obj("RUNE_SPIRITS");
    private static final Obj RUNE_SMITH = R.obj("RUNE_SMITH");
    private static final Obj LEVEL_DAMAGE = R.obj("LEVEL_DAMAGE");
    private static final List<String> STAT_ORDER = R.strings("RUNE_STAT_ORDER");
    /** LEVEL_DAMAGE levels, ascending ({@code Object.keys(LEVEL_DAMAGE).map(Number).sort((a, b) => a - b)}). */
    private static final double[] LEVEL_KEYS = LEVEL_DAMAGE.keys().stream().mapToDouble(Double::parseDouble).sorted().toArray();

    /** RUNE_FAMILY_ORDER (the draw picks one of these with equal chance). */
    public static final List<String> FAMILY_ORDER = R.strings("RUNE_FAMILY_ORDER");
    /** runes.js GRADE_ORDER. */
    public static final List<String> GRADE_ORDER = List.of("common", "rare", "noble", "hero", "transcend");

    /** runes.js RUNE_RE ({@code matches()} = JS {@code ^…$}). */
    private static final Pattern RUNE_RE = Pattern.compile("rpg:rune_("
            + FAMILY_ORDER.stream().map(Pattern::quote).collect(Collectors.joining("|"))
            + ")_(common|rare|noble|hero|transcend)");
    /** runes.js STRIKE_BONUS_FNS: strike runes that add power × bonus to each of {@code hits} basic attacks. */
    private static final Set<String> STRIKE_BONUS_FNS = Set.of("edgeHone", "cleave", "bloodBlade", "swordGod");
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /**
     * A rune item (runes.js parseRune).
     *
     * @param stat    the family's representative stat (icon group), {@code famName}: family name
     * @param def     RUNES[family][grade] (fn = effect, mult/hits/... = its numbers)
     * @param cd      cooldown in seconds (RUNE_GRADES)
     */
    public record Rune(String id, String family, String stat, String famName, String grade, Obj def, double cd, String gradeName, String gradeColor) {}

    /** One stat term of the applied stat: raw × coef. */
    public record Part(String stat, double raw, double coef) {}

    /**
     * runes.js runeStat result: the applied stat {@code value} and how it came about.
     *
     * @param stat   stat shown as representative, {@code raw}/{@code coef}: its value and coefficient
     * @param parts  terms for display
     * @param off    true when another stat × RUNE.offStat won
     * @param mix    spellblade mix bonus (0 … mix), {@code hasMix}: the family has one
     * @param best   body/strike families: the highest stat × 1.0
     */
    public record Applied(String stat, double raw, double value, double coef, List<Part> parts, boolean off, double mix, boolean hasMix, boolean best) {}

    /** Result of a draw. */
    public record Roll(String family, String stat, String grade) {}

    // ------------------------------------------------------------------ ids

    /** config.js runeId: {@code rpg:rune_<family>_<grade>}. */
    public static String runeId(String family, String grade) {
        return "rpg:rune_" + family + "_" + grade;
    }

    /** runes.js parseRune: the rune of an item id, or null. */
    public static Rune parseRune(String typeId) {
        Matcher m = RUNE_RE.matcher(typeId == null ? "" : typeId);
        if (!m.matches()) return null;
        String family = m.group(1);
        String grade = m.group(2);
        Obj byGrade = RUNES.objOrNull(family);
        Obj def = byGrade == null ? null : byGrade.objOrNull(grade);
        Obj f = FAMILIES.objOrNull(family);
        if (def == null || f == null) return null;
        Grade g = R.grade(grade);
        return new Rune(typeId, family, f.str("stat"), f.str("name"), grade, def, RUNE_GRADES.obj(grade).num("cd"),
                g == null ? grade : g.name(), g == null ? "§f" : g.color());
    }

    // ------------------------------------------------------------------ power

    /** runes.js runeLevelBase: LEVEL_DAMAGE at the highest XP level, linear between table levels. */
    public static double runeLevelBase(double level) {
        double[] keys = LEVEL_KEYS;
        double lv = Math.max(0, Double.isNaN(level) ? 0 : level);
        if (lv <= keys[0]) return levelDamage(keys[0]);
        for (int i = 1; i < keys.length; i++) {
            if (lv <= keys[i]) {
                double a = keys[i - 1];
                double b = keys[i];
                return levelDamage(a) + ((levelDamage(b) - levelDamage(a)) * (lv - a)) / (b - a);
            }
        }
        return levelDamage(keys[keys.length - 1]);
    }

    private static double levelDamage(double level) {
        return LEVEL_DAMAGE.num(Js.str(level));
    }

    /**
     * runes.js mixBonus (spellblade families): 0 … max, larger the closer int ÷ ratio is to the higher
     * of str/dex. {@code max}/{@code ratio} null = absent (no bonus / ratio 1).
     */
    public static double mixBonus(Map<String, Double> s, Double max, Double ratio) {
        double m = max == null ? 0 : max;
        if (!(m > 0)) return 0;
        double r = ratio == null ? 1 : ratio;
        double phys = Math.max(Stats.get(s, "str"), Stats.get(s, "dex"));
        double mag = Stats.get(s, "int") / (r > 0 ? r : 1);
        double hi = Math.max(phys, mag);
        return hi > 0 ? (m * Math.min(phys, mag)) / hi : 0;
    }

    /**
     * runes.js runeStat: applied stat of a family = max(Σ stat × family coefficient × (1 + mix),
     * any stat × RUNE.offStat); body/strike ("best") families take the highest stat × 1.0.
     */
    public static Applied runeStat(Map<String, Double> s, String family) {
        Obj f = FAMILIES.objOrNull(family);
        if (f != null && f.truthy("best")) {
            String k0 = STAT_ORDER.get(0);
            for (String k : STAT_ORDER) if (Stats.get(s, k) > Stats.get(s, k0)) k0 = k;
            double raw = Stats.get(s, k0);
            return new Applied(k0, raw, raw, 1, List.of(new Part(k0, raw, 1)), false, 0, false, true);
        }
        // unknown family: { stat: family, coef: { [family]: 1 } }
        String fStat = f == null ? family : f.str("stat");
        Map<String, Double> coef = new LinkedHashMap<>();
        if (f == null) coef.put(family, 1.0);
        else for (String k : f.obj("coef").keys()) coef.put(k, f.obj("coef").num(k));
        List<Part> parts = new ArrayList<>();
        for (Map.Entry<String, Double> e : coef.entrySet()) parts.add(new Part(e.getKey(), Stats.get(s, e.getKey()), e.getValue()));
        Double fMix = f == null ? null : f.numOrNull("mix");
        double mix = mixBonus(s, fMix, f == null ? null : f.numOrNull("ratio"));
        double sum = 0;
        for (Part x : parts) sum = sum + x.raw() * x.coef();
        Double ownCoef = coef.get(fStat);
        Applied best = new Applied(fStat, Stats.get(s, fStat), sum * (1 + mix), ownCoef != null ? ownCoef : 1, List.copyOf(parts),
                false, mix, (fMix == null ? 0 : fMix) > 0, false);
        double off = RUNE.num("offStat");
        for (String k : STAT_ORDER) {
            double v = Stats.get(s, k) * off;
            if (v > best.value()) best = new Applied(k, Stats.get(s, k), v, off, List.of(new Part(k, Stats.get(s, k), off)), true, 0, false, false);
        }
        return best;
    }

    /** runes.js runeStatLabel: "힘 100 × 0.85", "힘 100 + 체력 40 × 0.5 · 섞음 +12%". */
    public static String runeStatLabel(Applied e) {
        return runeStatLabel(e, " × ");
    }

    public static String runeStatLabel(Applied e, String sep) {
        if (e.parts().size() == 1) {
            Part p = e.parts().get(0);
            return Stats.info(p.stat()).str("name") + " " + Js.str(p.raw()) + sep + Js.str(p.coef());
        }
        StringJoiner j = new StringJoiner(" + ");
        for (int i = 0; i < e.parts().size(); i++) {
            Part x = e.parts().get(i);
            if (e.hasMix() && !(x.raw() > 0 || i == 0)) continue; // 섞음 계열: 0 인 스탯은 뺌
            j.add(Stats.info(x.stat()).str("name") + " " + Js.str(x.raw()) + (x.coef() == 1 ? "" : sep + Js.str(x.coef())));
        }
        String sum = j.toString();
        return e.mix() > 0 ? sum + " · 섞음 +" + Js.str(Js.round(e.mix() * 100)) + "%" : sum;
    }

    /** runes.js runePower: P = runeLevelBase(s.lvmax) × (1 + applied stat × RUNE.perPoint). */
    public static double runePower(Map<String, Double> s, String family) {
        return runeLevelBase(Stats.get(s, "lvmax")) * (1 + runeStat(s, family).value() * RUNE.num("perPoint"));
    }

    /** runes.js supportScale: heal/shield multiplier = 1 + applied stat × perPoint × 0.5. */
    public static double supportScale(Map<String, Double> s, String family) {
        return 1 + runeStat(s, family).value() * RUNE.num("perPoint") * 0.5;
    }

    /**
     * runes.js runeTotalMult: one use's damage on one target in units of P (conditional extras, undead
     * and combo multipliers excluded; poison included). Strike runes: their basic-attack bonus total.
     */
    public static double runeTotalMult(Obj def) {
        String fn = def.strOrNull("fn");
        if ("flurry".equals(fn)) return def.numOr("echo", 0) * RUNE_STRIKE.num("echoHits");
        if (fn != null && STRIKE_BONUS_FNS.contains(fn)) return def.numOr("bonus", 0) * def.numOr("hits", 1);
        if (!def.truthy("mult")) return 0;
        double mult = def.num("mult");
        if ("bulwark".equals(fn) || "undyingPact".equals(fn)) return mult * Math.floor(def.numOr("ticks", Double.NaN) / def.numOr("pulse", Double.NaN));
        // 정령왕 강림 (12단계 — 자바 전용): 정령 공격 mult × hits + 원소 마무리 넷 elemMult × 4 + 원소 붕괴 burstMult
        if ("spiritKing".equals(fn)) return mult * def.numOr("hits", 1) + def.numOr("elemMult", 0) * 4 + def.numOr("burstMult", 0);
        return mult * def.numOr("hits", 1) + def.numOr("dot", 0) * def.numOr("dots", 0) + def.numOr("crush", 0);
    }

    // ------------------------------------------------------------------ draw and grinding

    /** runes.js gradeWeights: grade → weight of a ticket (adv = high ticket, RUNE.advWeights). */
    public static Map<String, Double> gradeWeights(boolean adv) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String g : GRADE_ORDER) out.put(g, adv ? RUNE.obj("advWeights").numOr(g, 0) : RUNE_GRADES.obj(g).num("weight"));
        return out;
    }

    /**
     * runes.js rollRune: grade by weight, family uniformly. {@code rand} is called twice (grade, then
     * family) and should return values in [0, 1) like Math.random.
     */
    public static Roll rollRune(DoubleSupplier rand, boolean adv) {
        Map<String, Double> w = gradeWeights(adv);
        double total = 0;
        for (String g : GRADE_ORDER) total = total + w.get(g);
        double x = rand.getAsDouble() * total;
        String grade = GRADE_ORDER.get(GRADE_ORDER.size() - 1);
        for (String g : GRADE_ORDER) {
            if (w.get(g) <= 0) continue; // 나오지 않는 등급 (고급 뽑기권의 일반)
            if (x < w.get(g)) {
                grade = g;
                break;
            }
            x -= w.get(g);
        }
        int n = FAMILY_ORDER.size();
        double i = Math.min(n - 1, Math.floor(rand.getAsDouble() * n));
        if (!(i >= 0)) throw new IllegalArgumentException("random value out of [0, 1)");
        String family = FAMILY_ORDER.get((int) i);
        return new Roll(family, FAMILIES.obj(family).str("stat"), grade);
    }

    /**
     * 스탯 룬 뽑기권 (7단계 스탯 트리 — 애드온에는 없음): 등급은 {@link #rollRune} 과 같은 무게로, 계열은 family 하나로 정함.
     * {@code rand} 는 한 번 (등급) 부름.
     */
    public static Roll rollRuneOf(DoubleSupplier rand, boolean adv, String family) {
        if (!FAMILY_ORDER.contains(family)) throw new IllegalArgumentException("unknown rune family: " + family);
        Map<String, Double> w = gradeWeights(adv);
        double total = 0;
        for (String g : GRADE_ORDER) total = total + w.get(g);
        double x = rand.getAsDouble() * total;
        String grade = GRADE_ORDER.get(GRADE_ORDER.size() - 1);
        for (String g : GRADE_ORDER) {
            if (w.get(g) <= 0) continue;
            if (x < w.get(g)) {
                grade = g;
                break;
            }
            x -= w.get(g);
        }
        return new Roll(family, FAMILIES.obj(family).str("stat"), grade);
    }

    /** runesmith.js grindValue: rune shards from grinding one rune. */
    public static double grindValue(Rune r) {
        return r == null ? 0 : RUNE_SMITH.obj("grind").numOr(r.grade(), 0);
    }

    // ------------------------------------------------------------------ lore (runes.js runeLore)

    /** runes.js runeLore: item lore lines ({@code dark} = colors for light backgrounds). */
    public static List<String> runeLore(Rune r, boolean dark) {
        String L = dark ? "§8" : "§7";
        String N = dark ? "§0" : "§f";
        Obj d = r.def();
        String head = "§r" + L + r.famName() + " 룬 · " + r.gradeName() + " · 재사용 " + Js.str(r.cd()) + "초";
        if (Js.utf8Length(head) > 50) head = "§r" + L + r.famName() + " · " + r.gradeName() + " · 재사용 " + Js.str(r.cd()) + "초";
        List<String> lines = new ArrayList<>();
        lines.add(head);
        lines.add("§r" + L + d.text("desc"));
        if (d.truthy("use")) lines.add("§r" + L + "용도 " + N + d.text("use"));
        double total = r.family().equals(RUNE_STRIKE.str("family")) ? 0 : runeTotalMult(d);
        if (total > 0 && d.truthy("crush")) {
            String each = d.numOr("hits", 1) > 1 ? d.text("mult") + "×" + d.text("hits") : d.text("mult");
            lines.add("§r" + L + "피해 " + N + "위력×" + Js.str(round1(total)) + L + " (" + each + " + " + d.text("crush") + ")");
        } else if (total > 0) {
            lines.add("§r" + L + "피해 " + N + "위력×" + Js.str(round1(total)) + L + (d.truthy("hits") ? " (" + d.text("hits") + "번)" : ""));
        }
        // 0.12.6 사신 계열의 지속 피해는 출혈 (암살자 독 표창은 애드온 그대로 중독)
        String dotName = REAPER.equals(r.family()) ? "출혈 " : "중독 ";
        if (d.truthy("dot")) lines.add("§r" + L + dotName + N + "위력×" + d.text("dot") + L + ", " + sec20(d, "dotGap") + "초마다 " + d.text("dots") + "번");
        if (d.truthy("splashMult")) lines.add("§r" + L + "주위 " + d.text("splash") + "칸 " + N + "위력×" + d.text("splashMult"));
        if (total > 0 && r.family().equals("holy")) lines.add("§r" + L + "신성 피해 · 언데드 " + N + RUNE.text("undeadBonus") + "배");
        if (d.truthy("reduce")) lines.add("§r" + L + "받는 피해 " + N + "-" + Js.str(Js.round(d.num("reduce") * 100)) + "%");
        boolean buff = r.family().equals(RUNE_BODY.str("family")) || r.family().equals(RUNE_STRIKE.str("family"));
        if (!buff && d.truthy("heal") && d.truthy("healMax")) {
            lines.add("§r" + L + "흡혈 " + N + "적마다 최대 체력 " + Js.str(Js.round(d.num("heal") * 100)) + "%+");
        } else if (!buff && d.truthy("heal")) {
            double times = "undyingPact".equals(d.strOrNull("fn")) ? d.numOr("ticks", Double.NaN) / d.numOr("pulse", Double.NaN) : d.numOr("hits", 1);
            lines.add("§r" + L + "회복 " + N + "최대 체력 " + Js.str(Js.round(d.num("heal") * 100 * times)) + "%+");
        }
        if (d.truthy("shield") && d.truthy("cap")) {
            lines.add("§r" + L + "보호막 " + N + Js.str(Js.round(d.num("shield") * 100)) + "%씩" + L + " (최대 " + Js.str(Js.round(d.num("cap") * 100)) + "%)");
        } else if (d.truthy("shield")) {
            lines.add("§r" + L + "보호막 " + N + "최대 체력 " + Js.str(Js.round(d.num("shield") * 100)) + "%+");
        }
        Obj f = FAMILIES.objOrNull(r.family());
        if (f != null && f.truthy("mix")) {
            // 마검사 · 원소 검사: 섞음 보너스, 피해 종류, 마검 부여
            lines.add("§r" + L + "적용 = 마력 + 힘·민첩 × " + Js.str(f.obj("coef").numOr("str", 1)));
            lines.add("§r" + L + "마력 " + Js.str(f.numOr("ratio", 1)) + " : 힘·민첩 1 → 최대 " + N + "+" + Js.str(Js.round(f.num("mix") * 100)) + "%");
            String kind = d.strOrNull("kind");
            lines.add("§r" + L + "피해 종류: " + ("magic".equals(kind) ? "마력(마법)" : "sword".equals(kind) ? "검(공격)" : "검(공격) + 마력(마법)"));
            if (d.truthy("imbue")) {
                Obj imbue = RUNE_SPELLBLADE.obj("imbue");
                lines.add("§r" + (dark ? "§1" : "§9") + "마검 부여" + L + ": 평타 " + imbue.text("hits") + "번 (룬 뒤 " + Js.str(imbue.num("ticks") / 20) + "초)");
                lines.add("§r" + L + "평타 " + N + "+위력×" + d.text("imbue") + L + " · 관통 " + imbue.text("pierce") + "칸");
            }
        } else if (f != null && f.truthy("best")) {
            lines.add("§r" + L + "적용 = 가장 높은 스탯 × 1.0");
        } else {
            lines.add("§r" + L + coefText(r.family()) + " · 다른 스탯 " + RUNE.text("offStat"));
        }
        // 0.12.6 (자바 전용): 무기 전용 계열 (사신 = 낫) — 그 무기를 든 동안만 씀
        String only = weaponOnly(r.family());
        if (only != null) lines.add("§r" + (dark ? "§4" : "§c") + weaponName(only) + " 전용" + L + " · 다른 무기로는 쓰지 못함");
        if (REAPER.equals(r.family())) lines.addAll(reaperLore(d, L, dark));
        if (buff) lines.addAll(buffLore(r, L, dark));
        if (d.truthy("el")) lines.addAll(elementLore(d, L, dark));
        if (d.truthy("spirits")) lines.addAll(spiritLore(d, L, dark));
        if (r.family().equals("gravity")) lines.addAll(gravityLore(d, L, dark));
        lines.add("§r" + L + "위력 = 레벨 공격력×(1+스탯×" + Js.str(Js.round(RUNE.num("perPoint") * 100)) + "%)");
        if (r.family().equals(RUNE_COMBO.str("family"))) {
            lines.add("§r" + (dark ? "§5" : "§d") + "콤보" + L + ": " + Js.str(Js.round(RUNE_COMBO.num("window") / 20)) + "초 안에 다른 암살자 룬");
            lines.add("§r" + L + "이을 때마다 피해 +" + Js.str(Js.round(RUNE_COMBO.num("step") * 100)) + "% (" + RUNE_COMBO.text("minOwned") + "종부터)");
            lines.add("§r" + L + "5종을 모두 이으면 §5" + RUNE_COMBO.obj("finisher").text("name"));
        }
        lines.add("§r§8터치: 칸을 고르면 바로 사용");
        lines.add("§r§8누르면 사용 · 요구 스탯 없음");
        return lines;
    }

    /** 0.12.6 사신 계열 (낫 전용 — 피의 초승달): 낙인 · 적월 강림의 흐름 */
    private static List<String> reaperLore(Obj d, String L, boolean dark) {
        String N = dark ? "§0" : "§f";
        String fn = d.strOrNull("fn");
        List<String> out = new ArrayList<>();
        if ("bloodHarvest".equals(fn)) out.add("§r" + L + "두 번 휘두름 (" + sec(d.numOr("gap", Double.NaN)) + " 사이)");
        if ("bloodBrand".equals(fn)) out.add("§r" + L + "낙인 " + d.text("targets") + "명 → " + sec(d.numOr("delay", Double.NaN)) + " 뒤 폭발");
        if ("crimsonMoon".equals(fn)) {
            out.add("§r" + L + "칼날 " + sec(d.numOr("gap", Double.NaN)) + "마다 " + d.text("hits") + "번 (반지름 " + d.text("radius") + "칸)");
            out.add("§r" + L + "달 추락 " + N + "위력×" + d.text("crush"));
        }
        return out;
    }

    /** 0.12.6 사신 계열 */
    public static final String REAPER = "reaper";

    /**
     * 0.12.6 (자바 전용): 이 계열의 룬을 쓰려면 들어야 하는 무기 종류 (RUNE_FAMILIES[family].weapon — 사신 = scythe, 없으면 null — 아무 무기나)
     */
    public static String weaponOnly(String family) {
        Obj f = FAMILIES.objOrNull(family);
        return f == null ? null : f.strOrNull("weapon");
    }

    /** 이 룬을 이 무기 종류를 든 채 쓸 수 있는지 (weaponType = 손에 든 RPG 무기의 종류, 없으면 null) */
    public static boolean usableWith(Rune r, String weaponType) {
        String only = r == null ? null : weaponOnly(r.family());
        return only == null || only.equals(weaponType);
    }

    /** 무기 종류 이름 ("낫") */
    public static String weaponName(String type) {
        Obj t = R.obj("TYPES").objOrNull(type);
        return t == null ? type : t.str("name");
    }

    /** runes.js coefText: "민첩 계수 1.0", 도끼 "힘 1.0 + 체력 0.5". */
    private static String coefText(String family) {
        Obj f = FAMILIES.objOrNull(family);
        Obj coef = f == null ? null : f.objOrNull("coef");
        List<String> keys = coef == null ? List.of() : List.copyOf(coef.keys());
        if (keys.size() == 1) return Stats.info(keys.get(0)).str("name") + " 계수 " + Js.toFixed(coef.num(keys.get(0)), 1);
        StringJoiner j = new StringJoiner(" + ");
        for (String k : keys) j.add(Stats.info(k).str("name") + " " + Js.toFixed(coef.num(k), 1));
        return j.toString();
    }

    /** runes.js elementLore (원소 검사): fire / ice / thunder lines. */
    private static List<String> elementLore(Obj d, String L, boolean dark) {
        String fire = "§r" + (dark ? "§4" : "§6") + "불" + L + ": 불붙음 " + d.text("fire") + "초";
        String ice = "§r" + (dark ? "§3" : "§b") + "얼음" + L + ": 구속 " + roman(d.numOr("slowAmp", 0)) + " " + Js.str(d.numOr("slowTicks", 0) / 20) + "초";
        String thunder = "§r" + (dark ? "§6" : "§e") + "번개" + L + ": " + d.text("chain") + "칸 안 한 명에 위력×" + d.text("chainMult");
        String el = d.strOrNull("el");
        if ("fire".equals(el)) return List.of(fire);
        if ("ice".equals(el)) return List.of(ice);
        if ("thunder".equals(el)) return List.of(thunder);
        List<String> out = new ArrayList<>();
        if ("elementRelease".equals(d.strOrNull("fn"))) {
            out.add("§r" + L + "검기 셋: 불 · 얼음 · 번개");
            out.add("§r" + L + "적마다 한 번 (먼저 닿은 검기)");
        } else {
            out.add("§r" + L + "불 → 얼음 → 번개 (" + Js.str(d.numOr("gap", 10) / 20) + "초마다)");
        }
        out.add(fire);
        out.add(ice);
        out.add(thunder);
        return out;
    }

    /** runes.js gravityLore (중력): pull, lift, crush and slow lines. */
    private static List<String> gravityLore(Obj d, String L, boolean dark) {
        String N = dark ? "§0" : "§f";
        String fn = d.strOrNull("fn");
        List<String> out = new ArrayList<>();
        if ("gravityOrb".equals(fn)) {
            out.add("§r" + L + "끌어당김: 반지름 " + d.text("pull") + "칸 · " + sec(d.numOr("pullTicks", Double.NaN)));
            out.add("§r" + L + "→ 반지름 " + d.text("radius") + "칸 폭발");
        }
        if ("gravityWell".equals(fn)) {
            out.add("§r" + L + "끌어당김: 반지름 " + d.text("pull") + "칸 · " + sec(d.numOr("hits", Double.NaN) * d.numOr("gap", Double.NaN)));
            out.add("§r" + L + sec(d.numOr("gap", Double.NaN)) + "마다 " + N + "위력×" + d.text("mult") + L + " · 구속 " + roman(d.numOr("slowAmp", 0)));
        }
        if ("blackHole".equals(fn)) {
            out.add("§r" + L + "빨아들여 띄움: 반지름 " + d.text("pull") + "칸 · " + sec(d.numOr("hits", Double.NaN) * d.numOr("gap", Double.NaN)));
            out.add("§r" + L + sec(d.numOr("gap", Double.NaN)) + "마다 " + N + "위력×" + d.text("mult"));
        }
        if (d.truthy("lift")) out.add("§r" + L + "띄움 " + sec(d.numOr("gap", Double.NaN)) + " (그 동안 공격 못 함)");
        if ("liftSlam".equals(fn)) out.add("§r" + L + "내리꽂기: " + N + "위력×" + d.text("mult"));
        if (d.truthy("crush")) {
            String crushName = d.text("crushName");
            out.add("§r" + L + crushName + ": " + N + "위력×" + d.text("crush") + ("붕괴".equals(crushName) ? L + " (반지름 " + d.text("radius") + "칸)" : ""));
        }
        if (d.truthy("slowTicks")) out.add("§r" + L + "짓눌림: 구속 " + roman(d.numOr("slowAmp", 0)) + " " + sec(d.num("slowTicks")));
        out.add("§r" + L + "보스는 끌리거나 뜨지 않음");
        return out;
    }

    /** runes.js spiritLore (정령사): which spirits, how they attack. */
    private static List<String> spiritLore(Obj d, String L, boolean dark) {
        String G = dark ? "§2" : "§a";
        String secs = Js.str(d.numOr("ticks", Double.NaN) / 20);
        List<Object> spirits = d.list("spirits");
        if (spirits.size() > 1) {
            StringJoiner names = new StringJoiner("·");
            for (Object k : spirits) names.add(RUNE_SPIRITS.obj((String) k).text("name"));
            return List.of(
                    "§r" + G + "정령" + L + ": 넷 " + secs + "초 (정령마다 " + Js.str(Js.round(d.numOr("hits", Double.NaN) / spirits.size())) + "번)",
                    "§r" + L + names,
                    "§r" + L + "보는 적부터 노림");
        }
        String key = (String) spirits.get(0);
        Obj s = RUNE_SPIRITS.obj(key);
        String how = switch (key) {
            case "salamander" -> "불덩이 폭발 " + s.text("radius") + "칸 · 불붙음 " + s.text("fire") + "초";
            case "undine" -> "물줄기 " + s.text("radius") + "칸 · 밀침 · 구속 " + Js.str(s.numOr("slowTicks", 0) / 20) + "초";
            case "sylph" -> "꿰뚫고 " + s.text("pierce") + "칸 너머까지 돌진 · 띄움";
            case "gnome" -> Js.str(s.numOr("warn", 0) / 20) + "초 뒤 가시 " + s.text("radius") + "칸 · 구속 II";
            default -> "undefined";
        };
        return List.of("§r" + G + "정령" + L + ": " + s.text("name") + " " + secs + "초", "§r" + L + how, "§r" + L + "보는 적부터 노림");
    }

    /** runes.js buffLore (신체 강화 · 평타 강화): duration and effects. */
    private static List<String> buffLore(Rune r, String L, boolean dark) {
        String N = dark ? "§0" : "§f";
        Obj d = r.def();
        List<String> out = new ArrayList<>();
        if (r.family().equals(RUNE_BODY.str("family"))) {
            out.add("§r" + L + "지속 " + N + sec(d.numOr("ticks", Double.NaN)));
            if (d.has("speed")) {
                String roman = romanOrNull(d.numOr("speed", Double.NaN));
                out.add("§r" + L + "이동 속도 " + N + (roman != null ? roman : Js.str(d.numOr("speed", Double.NaN) + 1)));
            }
            if (d.truthy("statUp")) out.add("§r" + L + "모든 스탯 " + N + "+" + Js.str(Js.round(d.num("statUp") * 100)) + "%");
            if (d.truthy("steadfast")) out.add("§r" + L + "보스 기술에 밀려나지 않음");
            if (d.truthy("heal")) out.add("§r" + L + sec(d.numOr("pulse", Double.NaN)) + "마다 " + N + "최대 체력 " + Js.str(round1(d.num("heal") * 100)) + "%+");
            if (d.truthy("mult")) out.add("§r" + L + "쓸 때 충격파 반지름 " + d.text("radius") + "칸");
        } else {
            out.add("§r" + L + (d.truthy("hits") ? "평타 " + d.text("hits") + "번 · " : "") + sec(d.numOr("ticks", Double.NaN)) + " 동안");
            if (d.truthy("bonus")) out.add("§r" + L + "평타마다 " + N + "+위력×" + d.text("bonus"));
            if (d.truthy("rangedCd")) out.add("§r" + L + "활·완드 재사용 " + N + "-" + Js.str(Js.round((1 - d.num("rangedCd")) * 100)) + "%");
            if (d.truthy("echo")) out.add("§r" + L + "근접 " + N + "+위력×" + d.text("echo") + L + " · 옆의 적도");
            if (d.truthy("cleave")) {
                out.add("§r" + L + "근접: 앞 부채꼴 " + d.text("cleave") + "칸");
                out.add("§r" + L + "활·완드: 맞은 적 뒤 " + d.text("splash") + "칸");
            }
            if (d.truthy("heal")) out.add("§r" + L + "맞히면 " + N + "최대 체력 " + Js.str(round1(d.num("heal") * 100)) + "%+");
            if (d.truthy("wave")) out.add("§r" + L + "평타마다 검기 " + d.text("wave") + "칸 관통");
            if (d.truthy("crit")) out.add("§r" + L + "평타 치명타 " + N + "+" + Js.str(Js.round(d.num("crit") * 100)) + "%p");
        }
        return out;
    }

    /** runes.js round1. */
    private static double round1(double n) {
        return Js.round(n * 10) / 10;
    }

    /** "1.5초" from ticks (runes.js sec). */
    private static String sec(double ticks) {
        return Js.str(round1(ticks / 20)) + "초";
    }

    /** {@code ${d[key] / 20}}. */
    private static String sec20(Obj d, String key) {
        return Js.str(d.numOr(key, Double.NaN) / 20);
    }

    /** {@code ${ROMAN[i]}} ("undefined" outside I … V). */
    private static String roman(double i) {
        String r = romanOrNull(i);
        return r != null ? r : "undefined";
    }

    private static String romanOrNull(double i) {
        return i >= 0 && i < ROMAN.length && i == Math.rint(i) ? ROMAN[(int) i] : null;
    }
}
