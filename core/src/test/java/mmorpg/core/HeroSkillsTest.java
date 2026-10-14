package mmorpg.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 10단계 시험 (JUnit 없이 main — check-core.sh): 영웅 무기 스킬 (자바 전용 덧붙임 HERO_SKILLS).
 * <ul>
 * <li>영웅 무기 6종 + 12단계 새 무기 4종 (30 · 35 · 40렙) 은 스킬 하나 — 종류마다 정한 id, 레벨이 달라도 같은 스킬</li>
 * <li>초월 무기는 그대로 두 스킬, 일반 · 희귀 · 고귀 · 구버전 영웅 (20렙 등) 은 스킬 없음</li>
 * <li>세기: 피해 합 (skillTotalMult) = 같은 종류 초월 스킬 1 의 0.7배 (±3%), 재사용 대기 10초, 이름 · 설명 두 줄 · 색 [빛, 심지]</li>
 * <li>스킬 id 가 초월 스킬과 겹치지 않음, 설명 줄 (스킬 한 개: "버튼: 스킬" · "웅크리면: 스킬" · "스킬 이름 10초")</li>
 * </ul>
 * 대조 시험 (ParityTest — 덧붙임 없는 규칙) 이 영웅 무기 설명까지 애드온과 같은지 보므로, 덧붙임이 없으면 영웅 스킬이 없다는 것도 그쪽에서 확인됨.
 * 하나라도 틀리면 종료 코드 1.
 */
public final class HeroSkillsTest {
    private HeroSkillsTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static final Map<String, String> IDS = new LinkedHashMap<>();

    static {
        IDS.put("sword", "balmung_wave");
        IDS.put("dagger", "shadow_ambush");
        IDS.put("axe", "earth_fissure");
        IDS.put("shield", "durand_smite");
        IDS.put("bow", "elf_volley");
        IDS.put("wand", "lich_harvest");
        // 12단계 새 무기 (TYPE_ONLY — 고귀 10 · 20렙, 영웅 30 · 35 · 40렙, 초월 40렙만)
        IDS.put("lance", "gaebolg_pierce");
        IDS.put("scythe", "harpe_spin");
        IDS.put("hammer", "dagda_smash");
        IDS.put("scepter", "caduceus_serpents");
    }

    public static void main(String[] argv) {
        check(Rules.hasMore(), "overlay present");
        check(Skills.heroTypes().equals(new ArrayList<>(IDS.keySet())), "hero types " + Skills.heroTypes());
        Set<String> ids = new HashSet<>();
        for (String type : Weapons.allTypes()) {
            List<Obj> tk = Skills.weaponSkills(type);
            if (tk != null) for (Obj k : tk) ids.add(k.str("id"));
        }
        int transcendIds = ids.size();
        for (Map.Entry<String, String> e : IDS.entrySet()) {
            String type = e.getKey();
            Obj hk = null;
            for (int lv : new int[] {30, 35, 40}) {
                Weapon w = Weapons.weaponFrom(type, lv, "hero");
                check(w != null && !w.legacy(), type + " hero " + lv + " exists");
                if (w == null) continue;
                check(w.skills() != null && w.skills().size() == 1, type + " " + lv + " one skill");
                if (w.skills() == null || w.skills().isEmpty()) continue;
                Obj k = w.skills().get(0);
                check(k.str("id").equals(e.getValue()), type + " id " + k.str("id"));
                if (hk == null) hk = k;
                else check(hk.str("id").equals(k.str("id")), type + " same skill at " + lv);
            }
            if (hk == null) continue;
            final Obj k0 = hk;
            ids.add(k0.str("id"));
            // 세기: 같은 종류 초월 스킬 1 의 0.7배
            Weapon t = Weapons.weaponFrom(type, 40, "transcend");
            check(t != null && t.skills() != null && t.skills().size() == 2, type + " transcend keeps two skills");
            if (t != null && t.skills() != null) {
                double ratio = Skills.skillTotalMult(hk) / Skills.skillTotalMult(t.skills().get(0));
                check(Math.abs(ratio - 0.7) <= 0.03, type + " power 0.7 of transcend skill 1: " + ratio);
            }
            check(hk.num("cd") == 10, type + " cooldown 10");
            check(!hk.str("name").isEmpty() && !hk.str("desc").isEmpty() && !hk.str("desc2").isEmpty(), type + " name and two desc lines");
            List<Object> color = hk.listOrNull("color");
            check(color != null && color.size() == 2 && color.get(0) instanceof List<?> c0 && c0.size() == 3 && color.get(1) instanceof List<?> c1 && c1.size() == 3,
                    type + " color [glow, core]");
            // 다른 등급 · 구버전은 스킬 없음 (12단계 새 무기: 구버전 조합이 없고 고귀는 10 · 20렙, 일반 · 희귀는 다른 무기와 같은 레벨)
            boolean more = Weapons.moreTypes().contains(type);
            for (String g : new String[] {"common", "rare", "noble"}) {
                Weapon o = Weapons.weaponFrom(type, more && g.equals("noble") ? 20 : 30, g);
                check(o != null && o.skills() == null, type + " " + g + " has no skill");
            }
            Weapon old = Weapons.parseWeapon("rpg:" + type + "_l20_hero");
            if (more) check(old == null, type + " no legacy hero 20");
            else check(old != null && old.legacy() && old.skills() == null, type + " legacy hero 20 has no skill");
            // 설명 줄
            Weapon w40 = Weapons.weaponFrom(type, 40, "hero");
            List<String> lore = Weapons.weaponInfo(w40);
            boolean ranged = Weapons.isRangedType(type);
            String controls = ranged ? "웅크리면: 스킬" : "버튼: 스킬";
            check(lore.stream().anyMatch(l -> l.endsWith(controls)), type + " controls line " + controls + " in " + lore);
            check(lore.stream().anyMatch(l -> l.contains("스킬 §f" + k0.str("name") + " §710초")), type + " skill line in " + lore);
            check(lore.stream().noneMatch(l -> l.contains("스킬1") || l.contains("스킬2")), type + " no numbered skills");
            check(lore.stream().anyMatch(l -> l.endsWith(k0.str("desc"))) && lore.stream().anyMatch(l -> l.endsWith(k0.str("desc2"))), type + " desc lines");
        }
        check(ids.size() == transcendIds + IDS.size(), "skill ids unique (" + ids.size() + ")");
        // 초월 설명은 그대로 (두 스킬)
        List<String> gram = Weapons.weaponInfo(Weapons.weaponFrom("sword", 40, "transcend"));
        check(gram.stream().anyMatch(l -> l.endsWith("버튼: 스킬1, 웅크리면: 스킬2")), "transcend controls line unchanged");
        // 스킬 줄을 뺀 영웅 무기 설명은 다른 등급과 같은 모양 (스킬 줄은 맨 끝 — 요구 스탯 뒤)
        List<String> hero = Weapons.weaponInfo(Weapons.weaponFrom("axe", 35, "hero"));
        int req = -1;
        for (int i = 0; i < hero.size(); i++) if (hero.get(i).contains("요구 스탯")) req = i;
        check(req >= 0 && hero.size() == req + 1 + 4, "axe 35 lore: requirement then 4 skill lines " + hero);

        System.out.println("HeroSkillsTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
