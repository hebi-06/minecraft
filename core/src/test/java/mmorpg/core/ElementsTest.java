package mmorpg.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-07 원소 반응 시험 (JUnit 없이 main — check-core.sh): rules_more.json ELEMENTS · 룬 RUNE_SPREAD.runes 의 elem · 직업 스킬 jobs.json 의 elem.
 * <ul>
 * <li>원소 다섯 (불 · 물 · 전기 · 얼음 · 바위) · 원소 스킬 −5% · 묻는 시간 6초 · 같은 적 반응 2초에 한 번</li>
 * <li>반응 열: 서로 다른 두 원소마다 하나 (순서 없음) — 사용자가 정한 여섯 (증발 · 과부하 · 열충격 · 감전 · 빙결 · 쇄빙) · 제안 넷 (용암 · 진흙 · 초전도 · 자력)</li>
 * <li>무속성이 더 많음 (룬 · 직업 스킬 모두) · 원소 스킬의 피해 배율 = 무속성 × 0.95 · 툴팁 줄</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class ElementsTest {
    private ElementsTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    public static void main(String[] argv) {
        check(Elements.on(), "ELEMENTS present");
        check(near(Elements.tax(), 0.05) && Elements.auraTicks() == 120 && Elements.icdTicks() == 40, "tax 5% · aura 6s · icd 2s");
        check(Elements.order().equals(List.of("fire", "water", "thunder", "ice", "rock")), "five elements " + Elements.order());
        String[] names = {"불", "물", "전기", "얼음", "바위"};
        Set<Integer> cells = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Elements.Element e = Elements.get(Elements.order().get(i));
            check(e.name().equals(names[i]), e.id() + " name " + e.name());
            check(e.label().startsWith("§") && e.label().endsWith(names[i]), e.id() + " label " + e.label());
            check(e.glow().length == 3 && e.core().length == 3, e.id() + " colors");
            cells.add(e.cell());
        }
        check(cells.equals(Set.of(0, 1, 2, 3, 4)), "glyph cells 0..4 " + cells);

        // 반응: 서로 다른 두 원소마다 하나 · 순서 없음 · 같은 원소 · 모르는 원소는 없음
        int pairs = 0;
        Set<String> ids = new HashSet<>();
        for (String a : Elements.order()) {
            check(Elements.reaction(a, a) == null, a + " + " + a + " no reaction");
            for (String b : Elements.order()) {
                if (a.compareTo(b) >= 0) continue;
                Elements.Reaction r = Elements.reaction(a, b);
                check(r != null, a + " + " + b + " has a reaction");
                if (r == null) continue;
                pairs++;
                check(r == Elements.reaction(b, a), a + " + " + b + " symmetric");
                check(ids.add(r.id()), r.id() + " unique");
                check(Set.of("hit", "blast", "stun", "root", "vuln", "field", "pull").contains(r.kind()), r.id() + " kind " + r.kind());
                check(!r.desc().isEmpty(), r.id() + " desc");
                check(Elements.byId(r.id()) == r, r.id() + " by id");
            }
        }
        check(pairs == 10 && Elements.reactions().size() == 10, "10 reactions " + pairs);
        check(Elements.reaction("fire", null) == null && Elements.reaction("fire", "wind") == null, "unknown → none");
        Map<String, String[]> want = Map.of( // 열쇠 → {이름, 종류}
                "fire+water", new String[] {"증발", "hit"}, "fire+thunder", new String[] {"과부하", "blast"}, "fire+ice", new String[] {"열충격", "blast"},
                "thunder+water", new String[] {"감전", "stun"}, "ice+water", new String[] {"빙결", "stun"}, "ice+rock", new String[] {"쇄빙", "hit"},
                "fire+rock", new String[] {"용암", "field"}, "rock+water", new String[] {"진흙", "root"}, "ice+thunder", new String[] {"초전도", "vuln"},
                "rock+thunder", new String[] {"자력", "pull"});
        for (var en : want.entrySet()) {
            String[] ab = en.getKey().split("\\+");
            Elements.Reaction r = Elements.reaction(ab[0], ab[1]);
            check(r != null && r.key().equals(en.getKey()) && r.name().equals(en.getValue()[0]) && r.kind().equals(en.getValue()[1]),
                    en.getKey() + " = " + en.getValue()[0] + " (" + en.getValue()[1] + ")");
        }
        // 숫자: 즉시 피해 2.5 · 광역 1.5 (3칸) · 기절 1초 (보스 구속 2초) · 묶음 2초 · 받는 피해 +10% 5초 · 불타는 땅 3초 0.5초마다 0.3 · 끌어모음 5칸
        Elements.Reaction vap = Elements.byId("vaporize");
        Elements.Reaction sh = Elements.byId("shatter");
        check(near(vap.num("power", 0), 2.5) && near(sh.num("power", 0), 2.5), "vaporize · shatter 2.5");
        check(near(Elements.byId("overload").num("power", 0), 1.5) && near(Elements.byId("thermal").num("power", 0), 1.5)
                && near(Elements.byId("overload").num("radius", 0), 3), "overload · thermal 1.5 (3 blocks)");
        check(Elements.byId("shock").num("ticks", 0) == 20 && Elements.byId("freeze").num("ticks", 0) == 20 && Elements.byId("shock").num("bossSlow", 0) == 40,
                "stuns 1s (boss slow 2s)");
        check(Elements.byId("mud").num("ticks", 0) == 40, "mud root 2s");
        check(near(Elements.byId("superconduct").num("add", 0), 0.1) && Elements.byId("superconduct").num("ticks", 0) == 100, "superconduct +10% 5s");
        check(Elements.byId("lava").num("ticks", 0) == 60 && Elements.byId("lava").num("every", 0) == 10 && near(Elements.byId("lava").num("power", 0), 0.3),
                "lava 3s · 0.5s · 0.3");
        check(near(Elements.byId("magnet").num("radius", 0), 5), "magnet 5 blocks");
        check(Elements.mult(null) == 1 && near(Elements.mult("fire"), 0.95) && near(Elements.mult(Elements.MULTI), 0.95), "mult");
        check("원소 불 −5%".equals(Elements.notePlain("fire")) && "원소 여럿 −5%".equals(Elements.notePlain(Elements.MULTI)) && Elements.notePlain(null) == null,
                "plain note");
        check(Elements.tableLines("§7").size() == 10, "table lines");

        // 룬: 원소 룬 36 · 무속성이 더 많음 · 피해 배율 × 0.95 · 툴팁 머리 줄
        int runes = 0, elemental = 0;
        for (String fam : Runes.ALL_FAMILIES) {
            for (String g : Runes.GRADE_ORDER) {
                Runes.Rune r = Runes.parseRune(Runes.runeId(fam, g));
                if (r == null) continue;
                runes++;
                String el = Runes.element(r);
                if (el != null) {
                    elemental++;
                    check(Elements.known(el) || el.equals(Elements.MULTI), r.id() + " element " + el);
                }
                String head = RuneTip.lines(r, 0, null).get(0);
                check(el == null ? !head.contains("원소") : head.contains("원소") && head.contains("−5%"), r.id() + " head line " + head);
            }
        }
        check(elemental == 36, "36 elemental runes " + elemental);
        check(elemental * 2 < runes, "무속성 룬이 더 많음 " + elemental + " / " + runes);
        check("fire".equals(Runes.element(Runes.parseRune(Runes.runeId("int", "common")))) && "ice".equals(Runes.element(Runes.parseRune(Runes.runeId("int", "rare"))))
                && "water".equals(Runes.element(Runes.parseRune(Runes.runeId("tide", "hero")))) && "rock".equals(Runes.element(Runes.parseRune(Runes.runeId("golem",
                        "transcend")))) && "thunder".equals(Runes.element(Runes.parseRune(Runes.runeId("storm", "common")))) && Runes.element(Runes.parseRune(Runes.runeId(
                                "spirit", "noble"))) == null && Elements.MULTI.equals(Runes.element(Runes.parseRune(Runes.runeId("element", "hero")))),
                "rune elements (화염구 불 · 서리 폭발 얼음 · 해일 물 · 거신 강림 바위 · 연쇄 번개 전기 · 바람의 정령 무속성 · 삼원소 연격 여럿)");
        check(Runes.element(Runes.parseRune(Runes.runeId("str", "common"))) == null && Runes.element(Runes.parseRune(Runes.runeId("dex", "common"))) == null,
                "대지 강타 · 질풍 베기 무속성");

        // 직업 스킬: 무속성이 더 많음 · 원소 직업 (컨셉에 맞으면 모두) · 툴팁
        int skills = 0, elemSkills = 0;
        List<Jobs.Job> all = new ArrayList<>(Jobs.all());
        all.addAll(Jobs.allAdvanced());
        for (Jobs.Job j : all) {
            for (Jobs.Skill k : j.skills()) {
                skills++;
                String el = Jobs.element(k);
                if (el == null) continue;
                elemSkills++;
                if (k.total() > 0) {
                    String note = Jobs.scaleNote(k);
                    check(note != null && note.contains(Elements.notePlain(el)), k.id() + " note " + note);
                }
            }
        }
        check(elemSkills * 2 < skills, "무속성 직업 스킬이 더 많음 " + elemSkills + " / " + skills);
        for (String id : List.of("frostraider", "phoenix", "thunderlord")) {
            Jobs.Job j = Jobs.job(id);
            String el0 = Jobs.element(j.skill(0));
            for (Jobs.Skill k : j.skills()) check(el0 != null && el0.equals(Jobs.element(k)), id + " all " + el0 + ": " + k.id());
        }
        check("ice".equals(Jobs.element(Jobs.job("frostraider").skill(0))) && "fire".equals(Jobs.element(Jobs.job("phoenix").skill(0)))
                && "thunder".equals(Jobs.element(Jobs.job("thunderlord").skill(0))), "frost raider ice · phoenix fire · thunder lord thunder");
        Jobs.Job pa = Jobs.job("paladin");
        check("thunder".equals(Jobs.element(pa.skill(0))) && Jobs.element(pa.skill(1)) == null, "paladin smite thunder · holy charge 무속성");
        check(Jobs.element(Jobs.job("swordsman").skill(0)) == null && Jobs.element(Jobs.job("warrior").skill(4)) == null, "검사 · 전사 무속성");

        System.out.println("ElementsTest: " + ok + " ok, " + BAD.size() + " failed (runes " + elemental + "/" + runes + " · job skills " + elemSkills + "/" + skills + ")");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
