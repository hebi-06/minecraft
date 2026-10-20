package mmorpg.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 자바 판 룬 툴팁 (0.16.1 — 사용자 요청 "위력×32 대신 총 피해량 1000 처럼 구체적으로, 가독성 · 쓸모없는 줄 · 연출 줄 빼기, 돌파 · 주스탯 / 다른 스탯 계수는 남기기").
 * 대조 시험의 {@link Runes#runeLore} (애드온 runes.js 그대로) 는 바꾸지 않고, 가방 · 스킬 칸 창의 룬 툴팁만 이것을 씀.
 * <ul>
 * <li>숫자는 보는 사람의 지금 스탯 ({@link Viewer}): 피해 = 위력 P (레벨 기본값 × (1 + 적용 스탯 × 2%)) × 스탯 트리 (공격력 % + 룬 피해 %) × 돌파 계수 × 배율,
 * 회복 · 보호막 = 최대 체력 × 비율 (돌파 계수 포함) × (1 + 적용 스탯 × 1%). 적 하나 기준 (방어 · 언데드 · 콤보 보너스 전)</li>
 * <li>줄 순서: 재사용 (+ 한정 · 무기 전용) · 설명 · ⚔ 총 피해 (나눔) · 다른 피해 · ✚ 회복 / ◈ 보호막 · 룬별 흐름 · 효과 (불붙음 · 기절 …) · 계수 줄 · 돌파 줄
 * (초월은 3 · 6돌마다 그 단계의 총 피해 + 바뀌는 것) · 기준 · 조작</li>
 * <li>뺀 것: 용도 줄, 위력 공식 줄, 화면 색 · 각성 연출 줄, 6돌 보기 전용 연출 (finale), 돌파 안내 중복 줄, 총 피해 글자 안내, 한정 뽑기 안내</li>
 * </ul>
 * {@code viewer} 가 null 이면 (스탯을 모름) 피해는 "위력×N", 회복은 "최대 체력 N%+" 로.
 */
public final class RuneTip {
    private RuneTip() {}

    /**
     * 툴팁을 보는 사람.
     *
     * @param stats    실제 스탯 (찍은 스탯 + 장비 · 버프, lvmax 포함 — Stats.effectiveStats)
     * @param runeMult 스탯 트리 배수 (1 + (공격력 % + 룬 피해 %) / 100 — 서버 StatTrees.runeMult 와 같음)
     * @param maxHp    최대 체력 (회복 · 보호막 숫자 — 0 이하 · NaN 이면 비율로)
     */
    public record Viewer(Map<String, Double> stats, double runeMult, double maxHp) {}

    private static final Rules R = Rules.get();
    private static final Obj RUNE = R.obj("RUNE");
    private static final Obj FAMILIES = R.obj("RUNE_FAMILIES");
    private static final Obj STRIKE = R.obj("RUNE_STRIKE");
    private static final Obj BODY = R.obj("RUNE_BODY");
    private static final Obj COMBO = R.obj("RUNE_COMBO");
    private static final Obj SPELLBLADE = R.obj("RUNE_SPELLBLADE");
    private static final Obj SPIRITS = R.obj("RUNE_SPIRITS");
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};
    /** 한 줄 최대 글자 수 (색 코드 빼고 — 넘으면 " · " 에서 나눔) */
    static final int LINE_MAX = 30;
    /** 총 피해 (Runes.runeTotalMult) 에 든 값: 돌파 줄의 "위력 ×a → ×b" 는 그 단계의 총 피해로 대신함 */
    private static final Set<String> TOTAL_KEYS = Set.of("mult", "crush", "dot", "dots", "elemMult", "burstMult");

    private static final String L = "§7";
    private static final String N = "§f";
    private static final String D = "§8";
    /** 조작 줄 (스킬 칸 창은 이 줄을 지우고 키 안내를 붙임) */
    public static final String CONTROL = "우클릭: 빈 스킬 칸에 올리기 · 칸의 키로 사용";
    /** 숫자 기준 줄 (피해가 있는 룬 · 회복 / 강화만 있는 룬) */
    public static final String BASIS = "숫자: 지금 내 스탯 · 적 하나 기준";
    public static final String BASIS_SELF = "숫자: 지금 내 스탯 기준";

    /** 계산에 쓰는 값: 룬 · 단계 · 단계의 정의 · 돌파 계수 전 위력 (스탯 트리 포함 — 모르면 NaN) · 최대 체력 · 회복 배율 · 적용 스탯 (가호 룬 — 모르면 NaN) */
    private record Ctx(Runes.Rune r, int lv, Obj d, String fn, double pBase, double maxHp, double scale, double applied) {
        boolean body() {
            return r.family().equals(BODY.str("family"));
        }

        boolean strike() {
            return r.family().equals(STRIKE.str("family"));
        }
    }

    /** 툴팁 줄 (§ 색 코드 — 화면에 쓸 때 BedrockText.toJava). level = 돌파 단계 */
    public static List<String> lines(Runes.Rune r, int level, Viewer viewer) {
        int lv = RuneBreak.clamp(level);
        Obj d = RuneBreak.def(r, lv);
        double pBase = Double.NaN;
        double maxHp = Double.NaN;
        double scale = Double.NaN;
        double applied = Double.NaN;
        if (viewer != null && viewer.stats() != null) {
            // 0.26.0 지속 보정 (시간이 걸리거나 장판인 룬 — 쓸 때 위력에 곱하는 것과 같음)
            pBase = Runes.runePower(viewer.stats(), r.family()) * (viewer.runeMult() > 0 ? viewer.runeMult() : 1) * Runes.spreadMult(r);
            scale = Runes.supportScale(viewer.stats(), r.family());
            applied = Runes.runeStat(viewer.stats(), r.family()).value();
            if (viewer.maxHp() > 0) maxHp = viewer.maxHp();
        }
        Ctx c = new Ctx(r, lv, d, d.strOrNull("fn"), pBase, maxHp, scale, applied);
        List<String> out = new ArrayList<>();
        out.add(head(r));
        out.add("§r" + L + d.text("desc"));
        out.addAll(damage(c));
        String spread = spreadLine(c);
        if (spread != null) out.add(spread);
        out.addAll(support(c));
        out.addAll(flow(c));
        for (String l : wrap(effects(c), LINE_MAX)) out.add("§r" + L + l);
        out.addAll(coefLines(r));
        String summary = RuneBreak.summaryLine(lv, false);
        out.add(lv == 0 ? "§r" + L + plain(summary) : summary);
        out.addAll(tiers(c));
        if (viewer != null) out.add("§r" + D + (dealsDamage(c) ? BASIS : BASIS_SELF));
        out.add("§r" + D + CONTROL);
        return out;
    }

    /** 피해 숫자가 있는 룬인지 (총 피해 · 평타 강화 · 충격파) */
    private static boolean dealsDamage(Ctx c) {
        return c.strike() || Runes.runeTotalMult(c.d()) > 0;
    }

    // ------------------------------------------------------------------ 머리 · 숫자

    private static String head(Runes.Rune r) {
        StringBuilder sb = new StringBuilder("§r" + L + "재사용 " + N + Js.str(r.cd()) + "초");
        if (Runes.limited(r.family())) sb.append(D + " · §6한정");
        String only = Runes.weaponOnly(r.family());
        if (only != null) sb.append(D + " · §c" + Runes.weaponName(only) + " 전용");
        return sb.toString();
    }

    /** 값 → 글자: 100 이상은 쉼표 · 정수 ("1,344"), 아래는 소수 한 자리까지 ("8.4") */
    public static String fmt(double v) {
        double x = v > 0 ? v : 0;
        if (x >= 100) return String.format(Locale.ROOT, "%,d", Math.round(x));
        return Js.str(Math.round(x * 10) / 10.0);
    }

    /** 피해: 단계 lv 의 위력 × mult (스탯을 모르면 "위력×mult") */
    private static String dmg(Ctx c, double mult, int lv) {
        if (Double.isNaN(c.pBase())) return "위력×" + Js.str(Math.round(mult * Runes.spreadMult(c.r()) * 100) / 100.0);
        return fmt(c.pBase() * RuneBreak.coef(lv) * mult);
    }

    private static String dmg(Ctx c, double mult) {
        return dmg(c, mult, c.lv());
    }

    /** 회복 · 보호막: 최대 체력 × frac × 회복 배율 (모르면 "최대 체력 N%+") */
    private static String hp(Ctx c, double frac) {
        if (Double.isNaN(c.maxHp()) || Double.isNaN(c.scale())) return "최대 체력 " + Js.str(Math.round(frac * 1000) / 10.0) + "%+";
        return fmt(c.maxHp() * frac * c.scale());
    }

    /** 최대 체력 × frac (배율 없음 — 보호막 상한) */
    private static String hpRaw(Ctx c, double frac) {
        if (Double.isNaN(c.maxHp())) return "최대 체력 " + Js.str(Math.round(frac * 100)) + "%";
        return fmt(c.maxHp() * frac);
    }

    private static String n(double v) {
        return Js.str(Math.round(v * 100) / 100.0);
    }

    /** 틱 → "1.5초" */
    private static String sec(double ticks) {
        return Js.str(Math.round(ticks / 20 * 10) / 10.0) + "초";
    }

    private static String pct(double frac) {
        return Js.str(Math.round(frac * 100));
    }

    private static String roman(double i) {
        return i >= 0 && i < ROMAN.length && i == Math.rint(i) ? ROMAN[(int) i] : n(i + 1);
    }

    // ------------------------------------------------------------------ 피해

    private static List<String> damage(Ctx c) {
        Obj d = c.d();
        List<String> out = new ArrayList<>();
        double total = c.strike() ? 0 : Runes.runeTotalMult(d);
        if (c.body()) {
            // 신체 강화 초월: 쓸 때 충격파
            if (total > 0) out.add("§r§c⚔ " + L + "쓸 때 충격파 " + N + "§l" + dmg(c, total) + "§r" + L + " (반지름 " + d.text("radius") + "칸)");
        } else if (total > 0) {
            List<String> parts = breakdown(c);
            String line = "§r§c⚔ " + L + "총 피해 " + N + "§l" + dmg(c, total);
            String inline = line + "§r" + L + " (" + String.join(" + ", parts) + ")";
            if (parts.isEmpty()) {
                out.add(line);
            } else if (plain(inline).length() <= LINE_MAX + 4) {
                out.add(inline);
            } else {
                // 길면 나눔을 다음 줄로 (" + " 에서 나눔 — 이어지는 줄은 "+ " 로 시작)
                out.add(line);
                for (String l : wrapSum(parts, LINE_MAX - 2)) out.add("§r" + L + "  " + l);
            }
        }
        if (d.truthy("splashMult")) out.add("§r" + L + "주위 " + d.text("splash") + "칸 " + N + dmg(c, d.num("splashMult")));
        if ("thunderSpear".equals(c.fn()) && d.truthy("chainMult")) {
            out.add("§r" + L + "연쇄 번개 " + N + dmg(c, d.num("chainMult")) + L + " (맞은 적마다 " + d.text("chainRange") + "칸 안 하나)");
        }
        if (d.truthy("reflect")) out.add("§r" + L + "때린 몹에게 반사 " + N + dmg(c, d.num("reflect")));
        if (total > 0 && c.r().family().equals("holy")) out.add("§r" + L + "신성 피해 · 언데드 " + N + "×" + RUNE.text("undeadBonus"));
        return out;
    }

    /** 0.26.0 지속 보정 줄 ("지속 보정 +10% 포함 (장판)" — 위의 총 피해에 든 것) — 한방 · 피해 없는 룬은 null */
    private static String spreadLine(Ctx c) {
        String k = Runes.spreadKind(c.r());
        if (k == null || !dealsDamage(c) || c.strike()) return null;
        String why = switch (k) {
            case "short" -> "다 맞는 데 2초 안";
            case "long" -> "다 맞는 데 2초 넘게";
            case "zone" -> "장판";
            default -> "장판 · 4초 넘게";
        };
        return "§r" + L + "지속 보정 " + N + "+" + pct(Runes.spreadMult(c.r()) - 1) + "%" + L + " 포함" + D + " (" + why + ")";
    }

    /** 총 피해 나눔 조각 ("칼날 126 × 6번", "달 추락 588") — 한 번에 다 들어가면 빈 목록 */
    private static List<String> breakdown(Ctx c) {
        Obj d = c.d();
        String fn = c.fn();
        double m = d.numOr("mult", 0);
        double h = d.numOr("hits", 1);
        if ("spiritKing".equals(fn)) {
            return List.of("정령 " + dmg(c, m) + " × " + n(h) + "번", "원소 " + dmg(c, d.numOr("elemMult", 0)) + " × 4", "붕괴 " + dmg(c, d.numOr("burstMult", 0)));
        }
        if ("bulwark".equals(fn) || "undyingPact".equals(fn)) {
            double times = Math.floor(d.numOr("ticks", 0) / d.numOr("pulse", 20));
            return List.of(sec(d.numOr("pulse", 20)) + "마다 " + dmg(c, m) + " × " + n(times) + "번");
        }
        boolean dot = d.truthy("dot") && d.numOr("dots", 0) > 0;
        boolean crush = d.truthy("crush");
        if (!(h > 1) && !dot && !crush) return List.of();
        List<String> out = new ArrayList<>();
        // 0.18.0 대지 가르기: 대검 내려치기 (crush) 가 먼저, 그 뒤 갈라진 땅 여진 (mult × hits)
        boolean crushFirst = "earthSplitter".equals(fn);
        if (crush && crushFirst) out.add(crushLabel(fn, d) + " " + dmg(c, d.num("crush")));
        // 0.25.0: 조각 이름을 룬 정의에서 (multName · dotName — 없으면 함수마다 · 출혈 / 중독)
        if (m > 0) out.add((d.truthy("multName") ? d.text("multName") : multLabel(fn)) + dmg(c, m) + (h > 1 ? " × " + n(h) + "번" : ""));
        if (dot) out.add(Runes.dotName(c.r().family(), d) + " " + dmg(c, d.num("dot")) + " × " + n(d.num("dots")) + "번");
        if (crush && !crushFirst) out.add(crushLabel(fn, d) + " " + dmg(c, d.num("crush")));
        return out;
    }

    private static String multLabel(String fn) {
        if (fn == null) return "";
        return switch (fn) {
            case "crimsonMoon" -> "칼날 ";
            case "arcanaUnity" -> "구슬 ";
            case "sevenStars" -> "별빛 ";
            case "heavenEye" -> "광선 ";
            case "gravityReverse" -> "띄우기 ";
            case "blackHole" -> "띄워 치기 ";
            case "endlessSlash" -> "베기 ";
            case "earthSplitter" -> "여진 ";
            case "glacierField" -> "얼음 ";
            default -> "";
        };
    }

    private static String crushLabel(String fn, Obj d) {
        if (d.truthy("crushName")) return d.text("crushName");
        if (fn == null) return "마무리";
        return switch (fn) {
            case "crimsonMoon" -> "달 추락";
            case "arcanaUnity" -> "귀일 폭발";
            case "sevenStars" -> "별 낙하";
            case "heavenEye" -> "마무리 폭발";
            case "endlessSlash" -> "마무리 일섬";
            case "earthSplitter" -> "대검";
            case "glacierField" -> "빙하 붕괴";
            default -> "마무리";
        };
    }

    // ------------------------------------------------------------------ 회복 · 보호막

    private static List<String> support(Ctx c) {
        Obj d = c.d();
        List<String> out = new ArrayList<>();
        if (c.body() || c.strike()) return out; // 강화 룬은 flow 에서
        if (Runes.isGrace(d)) {
            // 0.19.0 세계수의 가호: 가하는 피해 + · 받는 피해 − (보는 사람의 스탯 합 · 돌파 계수 — 모르면 기본 값 + 스탯 비례)
            if (Double.isNaN(c.applied())) {
                out.add("§r§6✦ " + L + "가하는 피해 " + N + "+" + pct(d.num("graceUp")) + "%" + L + " + 스탯 비례");
                out.add("§r§b◈ " + L + "받는 피해 " + N + "-" + pct(d.num("graceCut")) + "%" + L + " + 스탯 비례");
            } else {
                out.add("§r§6✦ " + L + "가하는 피해 " + N + "§l+" + pct(Runes.graceUp(d, c.applied())) + "%");
                out.add("§r§b◈ " + L + "받는 피해 " + N + "§l-" + pct(Runes.graceCut(d, c.applied())) + "%");
            }
            return out;
        }
        if (Runes.isBrand(d)) {
            // 0.20.0 심연의 낙인: 적이 받는 피해 + · 적이 주는 피해 − (보는 사람의 스탯 합 · 돌파 계수)
            if (Double.isNaN(c.applied())) {
                out.add("§r§d✦ " + L + "적이 받는 피해 " + N + "+" + pct(d.num("brandUp")) + "%" + L + " + 스탯 비례");
                out.add("§r§5◈ " + L + "적이 주는 피해 " + N + "-" + pct(d.num("brandCut")) + "%" + L + " + 스탯 비례");
            } else {
                out.add("§r§d✦ " + L + "적이 받는 피해 " + N + "§l+" + pct(Runes.brandUp(d, c.applied())) + "%");
                out.add("§r§5◈ " + L + "적이 주는 피해 " + N + "§l-" + pct(Runes.brandCut(d, c.applied())) + "%");
            }
            return out;
        }
        if (d.truthy("heal") && d.truthy("healMax")) {
            out.add("§r§a✚ " + L + "흡혈 " + N + "적마다 " + hp(c, d.num("heal")) + L + " (" + d.text("healMax") + "명까지)");
        } else if (d.truthy("heal")) {
            boolean pact = "undyingPact".equals(c.fn());
            double times = pact ? Math.floor(d.numOr("ticks", 0) / d.numOr("pulse", 20)) : d.numOr("hits", 1);
            String detail = "";
            if (pact) detail = sec(d.numOr("pulse", 20)) + "마다 " + hp(c, d.num("heal")) + " × " + n(times) + "번";
            else if (d.truthy("pulses")) detail = sec(d.numOr("gap", 10)) + "마다 " + d.text("pulses") + "번 나눠";
            else if (times > 1) detail = hp(c, d.num("heal")) + " × " + n(times) + "번";
            out.add("§r§a✚ " + L + "회복 " + N + "§l" + hp(c, d.num("heal") * times) + (detail.isEmpty() ? "" : "§r" + L + " (" + detail + ")"));
        }
        if (d.truthy("shield") && d.truthy("cap")) {
            out.add("§r§b◈ " + L + "보호막 " + N + hp(c, d.num("shield")) + "씩" + L + " (최대 " + hpRaw(c, d.num("cap")) + ")");
        } else if (d.truthy("shield")) {
            out.add("§r§b◈ " + L + "보호막 " + N + "§l" + hp(c, d.num("shield")));
        }
        return out;
    }

    // ------------------------------------------------------------------ 룬별 흐름 (피해 숫자는 구체적으로)

    private static List<String> flow(Ctx c) {
        Obj d = c.d();
        String fn = c.fn();
        String fam = c.r().family();
        List<String> out = new ArrayList<>();
        if (c.body()) {
            // 지속 · 이동 속도 · 모든 스탯 · 받는 피해는 효과 줄 (effects)
            if (d.truthy("heal")) out.add("§r§a✚ " + L + "회복 " + sec(d.numOr("pulse", 20)) + "마다 " + N + "§l" + hp(c, d.num("heal")));
            return out;
        }
        if (c.strike()) {
            out.add("§r" + L + (d.truthy("hits") ? "평타 " + d.text("hits") + "번 · " : "") + sec(d.numOr("ticks", 0)) + " 동안");
            if (d.truthy("bonus")) {
                double hits = d.numOr("hits", 1);
                out.add("§r§c⚔ " + L + "평타마다 " + N + "§l+" + dmg(c, d.num("bonus")) + (hits > 1 ? "§r" + L + " (" + n(hits) + "번 합 +" + dmg(c, d.num("bonus") * hits) + ")" : ""));
            }
            if (d.truthy("rangedCd")) out.add("§r" + L + "활·완드 재사용 " + N + "-" + pct(1 - d.num("rangedCd")) + "%");
            if (d.truthy("echo")) out.add("§r§c⚔ " + L + "근접 " + N + "+" + dmg(c, d.num("echo")) + L + " · 옆의 적도");
            if (d.truthy("cleave")) out.add("§r" + L + "근접: 앞 부채꼴 " + d.text("cleave") + "칸 · 활·완드: 맞은 적 뒤 " + d.text("splash") + "칸");
            if (d.truthy("heal")) out.add("§r§a✚ " + L + "맞히면 " + N + hp(c, d.num("heal")));
            if (d.truthy("wave")) out.add("§r" + L + "평타마다 검기 " + d.text("wave") + "칸 관통");
            if (d.truthy("crit")) out.add("§r" + L + "평타 치명타 " + N + "+" + pct(d.num("crit")) + "%p");
            return out;
        }
        Obj f = FAMILIES.objOrNull(fam);
        if (f != null && f.truthy("mix") && d.truthy("imbue")) {
            Obj imbue = SPELLBLADE.obj("imbue");
            out.add("§r§9마검 부여" + L + ": 평타 " + imbue.text("hits") + "번 (룬 뒤 " + Js.str(imbue.num("ticks") / 20) + "초)");
            out.add("§r" + L + "평타 " + N + "+" + dmg(c, d.num("imbue")) + L + " · 관통 " + imbue.text("pierce") + "칸");
        }
        if (d.truthy("el")) out.addAll(element(c));
        if (d.truthy("spirits")) out.addAll(spirits(c));
        if ("gravity".equals(fam)) out.addAll(gravity(c));
        if (Runes.REAPER.equals(fam)) {
            if ("bloodHarvest".equals(fn)) out.add("§r" + L + "두 번 휘두름 (" + sec(d.numOr("gap", 0)) + " 사이)");
            if ("bloodBrand".equals(fn)) out.add("§r" + L + "낙인 " + d.text("targets") + "명 → " + sec(d.numOr("delay", 0)) + " 뒤 폭발");
            if ("crimsonMoon".equals(fn)) out.add("§r" + L + "칼날 " + sec(d.numOr("gap", 0)) + "마다 (반지름 " + d.text("radius") + "칸)");
        }
        // 0.25.0 두 스탯 계열: 흐름 줄 (def.flow — {key} · {t:key})
        for (String l : Runes.flowLines(d)) out.add("§r" + L + l);
        if ("arcanaUnity".equals(fn)) out.add("§r" + L + "구슬 몫은 스탯 비율로 · 폭발 반지름 " + d.text("radius") + "칸");
        if ("sevenStars".equals(fn)) out.add("§r" + L + "별빛 기둥 " + d.text("beam") + "칸 · 별 낙하 반지름 " + d.text("radius") + "칸");
        if ("endlessSlash".equals(fn)) out.add("§r" + L + "영역 반지름 " + d.text("radius") + "칸 · " + sec(d.numOr("gap", 10)) + "마다 영역 안 모든 적");
        if ("earthSplitter".equals(fn)) {
            out.add("§r" + L + "앞으로 " + d.text("length") + "칸 균열 (폭 " + n(d.numOr("width", 0) * 2) + "칸) 안 모든 적");
            out.add("§r" + L + "대검이 내려친 뒤 " + sec(d.numOr("gap", 10)) + "마다 여진");
        }
        if ("glacierField".equals(fn)) {
            // 0.24.0 만년빙하: 얼음 필드 · 거대한 얼음 · 빙결 (같은 적이 chill 번 맞으면 frost 틱)
            out.add("§r" + L + "조준한 곳 반지름 " + d.text("radius") + "칸 얼음 필드 · " + sec(d.numOr("gap", 14)) + "마다");
            out.add("§r" + L + "필드 안 적마다 발밑에서 거대한 얼음이 솟음");
            out.add("§r§b빙결" + L + ": 같은 적 " + d.text("chill") + "번 → " + sec(d.numOr("frost", 0)) + " (보스는 얼지 않음)");
        }
        if (Runes.isGrace(d)) {
            out.add("§r" + L + "쓴 자리 반지름 " + d.text("radius") + "칸 안 아군 (나 포함) · " + sec(d.numOr("ticks", 0)));
            out.add("§r" + L + Runes.graceFormula(d));
            out.add("§r" + L + "받는 피해 감소는 다른 감소와 겹치지 않음");
        }
        if (Runes.isBrand(d)) {
            out.add("§r" + L + "조준한 곳 반지름 " + d.text("radius") + "칸 안 적 · " + sec(d.numOr("ticks", 0)) + " (보스 포함)");
            out.add("§r" + L + Runes.brandFormula(d));
            out.add("§r" + L + "모든 플레이어의 피해에 · 가호와 곱");
        }
        if ("heavenEye".equals(fn)) {
            out.add("§r" + L + "광선이 바라보는 곳을 따라감 (굵기 " + d.text("width") + "칸)");
            out.add("§r" + L + "폭발 반지름 " + d.text("radius") + "칸 · 쓰는 동안 천천히 걸음");
        }
        if (fam.equals(COMBO.str("family"))) {
            out.add("§r§d콤보" + L + ": " + Js.str(Math.round(COMBO.num("window") / 20)) + "초 안에 다른 암살자 룬 → +" + pct(COMBO.num("step")) + "%씩");
            out.add("§r" + L + "(" + COMBO.text("minOwned") + "종부터) · 5종을 모두 이으면 §5" + COMBO.obj("finisher").text("name"));
        }
        return out;
    }

    private static List<String> element(Ctx c) {
        Obj d = c.d();
        String fire = "§r§6불" + L + ": 불붙음 " + d.text("fire") + "초";
        String ice = "§r§b얼음" + L + ": 구속 " + roman(d.numOr("slowAmp", 0)) + " " + sec(d.numOr("slowTicks", 0));
        String thunder = "§r§e번개" + L + ": " + d.text("chain") + "칸 안 한 명에 " + N + dmg(c, d.numOr("chainMult", 0));
        String el = d.strOrNull("el");
        if ("fire".equals(el)) return List.of(fire);
        if ("ice".equals(el)) return List.of(ice);
        if ("thunder".equals(el)) return List.of(thunder);
        List<String> out = new ArrayList<>();
        out.add("§r" + L + ("elementRelease".equals(c.fn()) ? "검기 셋 (적마다 먼저 닿은 하나)" : "불 → 얼음 → 번개 (" + sec(d.numOr("gap", 10)) + "마다)"));
        out.add(fire);
        out.add(ice);
        out.add(thunder);
        return out;
    }

    private static List<String> spirits(Ctx c) {
        Obj d = c.d();
        String secs = Js.str(d.numOr("ticks", 0) / 20);
        List<Object> list = d.list("spirits");
        if (list.size() > 1) {
            StringJoiner names = new StringJoiner("·");
            for (Object k : list) names.add(SPIRITS.obj((String) k).text("name"));
            return List.of("§r§a정령" + L + " 넷 " + secs + "초 · 보는 적부터 노림", "§r" + L + names);
        }
        String key = (String) list.get(0);
        Obj s = SPIRITS.obj(key);
        String how = switch (key) {
            case "salamander" -> "불덩이 폭발 " + s.text("radius") + "칸 · 불붙음 " + s.text("fire") + "초";
            case "undine" -> "물줄기 " + s.text("radius") + "칸 · 밀침 · 구속 " + Js.str(s.numOr("slowTicks", 0) / 20) + "초";
            case "sylph" -> "꿰뚫고 " + s.text("pierce") + "칸 너머까지 돌진 · 띄움";
            case "gnome" -> Js.str(s.numOr("warn", 0) / 20) + "초 뒤 가시 " + s.text("radius") + "칸 · 구속 II";
            default -> "";
        };
        return List.of("§r§a정령" + L + ": " + s.text("name") + " " + secs + "초 · 보는 적부터 노림", "§r" + L + how);
    }

    private static List<String> gravity(Ctx c) {
        Obj d = c.d();
        String fn = c.fn();
        List<String> out = new ArrayList<>();
        if ("gravityOrb".equals(fn)) out.add("§r" + L + "끌어당김 " + d.text("pull") + "칸 (" + sec(d.numOr("pullTicks", 0)) + ") → 폭발 " + d.text("radius") + "칸");
        if ("gravityWell".equals(fn)) out.add("§r" + L + "끌어당김 " + d.text("pull") + "칸 (" + sec(d.numOr("hits", 0) * d.numOr("gap", 0)) + ") · 구속 " + roman(d.numOr("slowAmp", 0)));
        if ("blackHole".equals(fn)) out.add("§r" + L + "빨아들여 띄움 " + d.text("pull") + "칸 (" + sec(d.numOr("hits", 0) * d.numOr("gap", 0)) + ")");
        if (d.truthy("lift")) out.add("§r" + L + "띄움 " + sec(d.numOr("gap", 0)) + " (그 동안 공격 못 함)");
        if (d.truthy("slowTicks")) out.add("§r" + L + "짓눌림: 구속 " + roman(d.numOr("slowAmp", 0)) + " " + sec(d.num("slowTicks")));
        out.add("§r" + L + "보스는 끌리거나 뜨지 않음");
        return out;
    }

    // ------------------------------------------------------------------ 효과 (불붙음 · 기절 · 구속 …)

    private static List<String> effects(Ctx c) {
        Obj d = c.d();
        String fam = c.r().family();
        boolean element = d.truthy("el");
        boolean gravity = "gravity".equals(fam);
        boolean spirit = d.truthy("spirits") && d.list("spirits").size() == 1; // 정령 하나는 정령 줄에
        boolean buff = c.body() || c.strike();
        List<String> e = new ArrayList<>();
        if (c.body()) {
            e.add("지속 " + sec(d.numOr("ticks", 0)));
            if (d.has("speed")) e.add("이동 속도 " + roman(d.numOr("speed", 0)));
            if (d.truthy("statUp")) e.add("모든 스탯 +" + pct(d.num("statUp")) + "%");
            if (d.truthy("steadfast")) e.add("보스 기술에 밀려나지 않음");
        }
        if (!element && !spirit && d.truthy("fire")) e.add("불붙음 " + d.text("fire") + "초");
        if (d.truthy("stun")) e.add("기절 " + sec(d.num("stun")));
        if (!element && !gravity && !spirit && d.truthy("slowTicks")) e.add("구속 " + roman(d.numOr("slowAmp", 0)) + " " + sec(d.num("slowTicks")));
        if (d.truthy("root")) e.add("묶음 " + sec(d.num("root")));
        if (d.truthy("freeze")) e.add("멈춤 " + sec(d.num("freeze")));
        if (!gravity && !spirit && d.truthy("lift")) e.add("띄움");
        if (!gravity && !spirit && d.truthy("pull")) e.add("끌어당김");
        if (d.truthy("knock") || d.truthy("push")) e.add("밀쳐냄");
        if (d.truthy("hide")) e.add("은신 " + sec(d.num("hide")));
        if (d.truthy("speedTicks")) e.add("빨라짐 " + sec(d.num("speedTicks")));
        if (!buff && d.truthy("below") && d.truthy("bonus")) e.add("체력 " + pct(d.num("below")) + "% 이하 적 ×" + d.text("bonus"));
        double targets = d.numOr("maxTargets", d.numOr("targets", 0));
        if (targets > 0 && !"bloodBrand".equals(c.fn())) e.add("최대 " + n(targets) + "명");
        if (d.truthy("reduce")) e.add("받는 피해 -" + pct(d.num("reduce")) + "%" + (!buff && d.truthy("ticks") ? " (" + sec(d.num("ticks")) + ")" : ""));
        return e;
    }

    // ------------------------------------------------------------------ 계수 (그대로)

    private static List<String> coefLines(Runes.Rune r) {
        Obj f = FAMILIES.objOrNull(r.family());
        List<String> out = new ArrayList<>();
        if (f != null && f.truthy("mix")) {
            out.add("§r" + L + "적용 = 마력 + 힘·민첩 × " + Js.str(f.obj("coef").numOr("str", 1)));
            out.add("§r" + L + "마력 " + Js.str(f.numOr("ratio", 1)) + " : 힘·민첩 1 → 최대 " + N + "+" + pct(f.num("mix")) + "%");
            String kind = r.def().strOrNull("kind");
            out.add("§r" + L + "피해 종류: " + ("magic".equals(kind) ? "마력(마법)" : "sword".equals(kind) ? "검(공격)" : "검(공격) + 마력(마법)"));
        } else if (f != null && f.truthy("best")) {
            out.add("§r" + L + "적용 = 가장 높은 스탯 × 1.0");
        } else if (f != null && f.truthy("sum")) {
            out.add("§r" + L + "적용 = 네 스탯의 합 × " + Js.toFixed(f.obj("coef").numOr("int", 1), 1));
        } else {
            out.add("§r" + L + Runes.coefText(r.family()) + " · 다른 스탯 " + RUNE.text("offStat"));
        }
        return out;
    }

    // ------------------------------------------------------------------ 초월 돌파 (3 · 6돌)

    private static List<String> tiers(Ctx c) {
        Runes.Rune r = c.r();
        if (!RuneBreak.tiered(r)) return List.of();
        String fn = r.def().str("fn");
        List<String> out = new ArrayList<>();
        StringJoiner ts = new StringJoiner(" · ");
        for (int t : RuneBreak.tiers()) ts.add(t + "돌");
        out.add("§r§6초월 돌파 " + D + "(" + ts + ")");
        Map<String, Double> cur = new LinkedHashMap<>();
        for (int t : RuneBreak.tiers()) {
            boolean on = c.lv() >= t;
            String body = on ? N : L;
            String num = on ? "§e" : N;
            Obj dt = RuneBreak.def(r, t);
            double tot = c.strike() || c.body() ? 0 : Runes.runeTotalMult(dt);
            List<String> parts = new ArrayList<>();
            if (tot > 0) parts.add("총 피해 " + num + dmg(c, tot, t) + body);
            if (Runes.isGrace(dt)) parts.add("가호 " + num + graceText(dt, c.applied(), t) + body);
            if (Runes.isBrand(dt)) parts.add("낙인 " + num + brandText(dt, c.applied(), t) + body);
            for (RuneBreak.Change ch : RuneBreak.changes(fn, t)) {
                String txt = ch.text();
                if (txt == null || txt.isEmpty()) continue;
                Double from = cur.containsKey(ch.key()) ? cur.get(ch.key()) : r.def().numOrNull(ch.key());
                if (from == null) from = ch.base();
                boolean upgrade = txt.contains("위력 ×{from} → ×{to}");
                if (upgrade && TOTAL_KEYS.contains(ch.key()) && tot > 0) continue; // 그 단계의 총 피해로
                if (upgrade) txt = txt.replace("위력 ×{from} → ×{to}", dmg(c, from == null ? 0 : from, t) + " → " + dmg(c, ch.to(), t));
                txt = txt.replace("위력 ×{to}", dmg(c, ch.to(), t));
                txt = txt.replace("{from}", from == null ? "?" : n(from / ch.div())).replace("{to}", n(ch.to() / ch.div())).replace("위력", "피해");
                parts.add(txt);
            }
            for (RuneBreak.Change ch : RuneBreak.changes(fn, t)) cur.put(ch.key(), ch.to());
            RuneBreak.Extra ex = RuneBreak.extra(fn);
            if (ex != null && ex.at() == t && "echo".equals(ex.type())) {
                parts.add("◆ " + ex.text().replace("위력 {mult}%", num + dmg(c, tot * ex.mult(), t) + body));
            }
            List<String> lines = wrap(parts, LINE_MAX);
            for (int i = 0; i < lines.size(); i++) {
                String head = i == 0 ? (on ? "§6" + RuneBreak.STAR : L + RuneBreak.STAR_EMPTY) + t + "돌 " : "     ";
                out.add("§r" + (on ? "§6" : L) + head + body + lines.get(i));
            }
        }
        return out;
    }

    /** 단계의 가호 세기 ("26%" — 가하는 피해 · 받는 피해가 다르면 "+26% · -24%", 스탯을 모르면 돌파 계수 "×1.3") */
    private static String graceText(Obj dt, double applied, int t) {
        if (Double.isNaN(applied)) return RuneBreak.coefText(t);
        String up = pct(Runes.graceUp(dt, applied));
        String cut = pct(Runes.graceCut(dt, applied));
        return up.equals(cut) ? up + "%" : "+" + up + "% · -" + cut + "%";
    }

    /** 단계의 낙인 세기 (가호와 같은 모양) */
    private static String brandText(Obj dt, double applied, int t) {
        if (Double.isNaN(applied)) return RuneBreak.coefText(t);
        String up = pct(Runes.brandUp(dt, applied));
        String cut = pct(Runes.brandCut(dt, applied));
        return up.equals(cut) ? up + "%" : "+" + up + "% · -" + cut + "%";
    }

    // ------------------------------------------------------------------ 줄 나누기

    /** 조각들을 " · " 로 이어 줄로 (색 코드를 뺀 글자 수 max 까지) */
    static List<String> wrap(List<String> parts, int max) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int len = 0;
        for (String p : parts) {
            int pl = plain(p).length();
            if (len > 0 && len + 3 + pl > max) {
                lines.add(cur.toString());
                cur.setLength(0);
                len = 0;
            }
            if (len > 0) {
                cur.append(" · ");
                len += 3;
            }
            cur.append(p);
            len += pl;
        }
        if (len > 0) lines.add(cur.toString());
        return lines;
    }

    /** 합 조각들을 " + " 로 이어 줄로 (이어지는 줄은 "+ " 로 시작) */
    static List<String> wrapSum(List<String> parts, int max) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (cur.length() == 0) {
                cur.append(lines.isEmpty() ? p : "+ " + p);
            } else if (plain(cur.toString()).length() + 3 + plain(p).length() > max) {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append("+ ").append(p);
            } else {
                cur.append(" + ").append(p);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    /** 색 코드 (§x) 를 뺀 글자 */
    public static String plain(String s) {
        return s.replaceAll("§.", "");
    }
}
