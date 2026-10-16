package mmorpg.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 12단계 시험 (JUnit 없이 main — check-core.sh): 새 무기 종류 4개 (자바 전용 덧붙임 — 창 · 낫 · 전투 망치 · 성스러운 홀).
 * <ul>
 * <li>있는 조합: 일반 · 희귀 0 ~ 40렙 (다른 무기와 같은 레벨 — 무기 상점), 고귀 10 · 20렙, 영웅 30 · 35 · 40렙, 초월 40렙 (그 밖의 조합 · 구버전 없음),
 *     이름 · 종류 글자, 일반 · 희귀는 스킬 없음 · 같은 레벨 칼과 같은 값 · 요구 스탯 2:1</li>
 * <li>요구 스탯 (두 스탯 2:1 — 40렙 초월 74 + 36), 피해 배율 (1포인트당 그 스탯의 다른 무기와 같은 비율), 근접 · 마법 피해</li>
 * <li>세기: 40렙 초월 · 스탯 125 (주 89 · 다른 36) 의 평균 평타가 칼 · 단검 쪽과 비슷 (망치 · 홀은 체력 몫만큼 낮게), 초월 스킬1 실효 피해가
 *     그람 화룡 돌진과 ±8%, 스킬2 = 스킬1 × 1.55~1.65, 영웅 = 초월 스킬1 의 0.7배 (HeroSkillsTest)</li>
 * <li>설명 줄 (기본 공격 특징 · 스킬 조작), 제작 (고귀는 전리품 + 에메랄드만, 영웅 · 초월은 보스 재료, 쪽마다 9칸 안), 스탯 창 글자, 정령왕 강림</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class WeaponsMoreTest {
    private WeaponsMoreTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, double eps, String what) {
        check(Math.abs(a - b) <= eps, what + " (" + a + " vs " + b + ")");
    }

    private static final List<String> MORE = List.of("lance", "scythe", "hammer", "scepter");

    /** 40렙 초월 · 스탯 125 의 찍은 스탯 (주 스탯 89 · 다른 스탯 36, 한 스탯 무기는 그 스탯 125) */
    private static Map<String, Double> build(Weapon w) {
        Map<String, Double> s = new HashMap<>();
        for (String k : List.of("str", "dex", "int", "vit")) s.put(k, 0.0);
        if (w.reqs().size() == 1) s.put(w.stat(), 125.0);
        else {
            s.put(w.reqs().get(0).stat(), 89.0);
            s.put(w.reqs().get(1).stat(), 36.0);
        }
        return s;
    }

    /** 평균 평타 (치명타 평균 포함) */
    private static double avgHit(Weapon w) {
        double crit = 1 + w.critChance() * (w.critMult() - 1);
        return w.damage() * Combat.statMultiplier(build(w), w) * crit;
    }

    public static void main(String[] argv) {
        check(Rules.hasMore(), "overlay present");
        check(Weapons.moreTypes().equals(MORE), "more types " + Weapons.moreTypes());
        List<String> all = new ArrayList<>(Rules.get().strings("TYPE_ORDER"));
        all.add("greatsword");
        all.addAll(MORE);
        check(Weapons.allTypes().equals(all), "all types " + Weapons.allTypes());

        Map<String, String[]> names = Map.of("lance", new String[] {"황금 기사창", "게이 볼그", "궁니르", "창"}, "scythe",
                new String[] {"황금 초승달낫", "하르페", "크로노스의 낫", "낫"}, "hammer", new String[] {"황금 전투망치", "다그다의 곤봉", "묠니르", "전투 망치"}, "scepter",
                new String[] {"황금 성광홀", "카두케우스", "아폴론의 태양홀", "성스러운 홀"});
        Map<String, String[]> basics = Map.of("lance", new String[] {"철 장창", "푸른 강철창"}, "scythe", new String[] {"철 전투낫", "청강 전투낫"}, "hammer",
                new String[] {"철 전투망치", "푸른 강철 망치"}, "scepter", new String[] {"견습 사제의 홀", "청수정 성홀"});
        Map<String, String[]> stats = Map.of("lance", new String[] {"str", "dex"}, "scythe", new String[] {"dex", "int"}, "hammer", new String[] {"vit", "str"},
                "scepter", new String[] {"int", "vit"});
        Map<String, String[]> skillIds = Map.of("lance", new String[] {"gungnir_return", "gungnir_fall"}, "scythe", new String[] {"kronos_rend", "kronos_clock"},
                "hammer", new String[] {"mjolnir_throw", "mjolnir_storm"}, "scepter", new String[] {"apollo_sun", "apollo_sanctuary"});
        Weapon gram = Weapons.weaponFrom("sword", 40, "transcend");
        double gramSkill = avgHit(gram) * Skills.skillTotalMult(gram.skills().get(0));
        for (String type : MORE) {
            // 조합
            for (Grade g : Rules.get().grades()) {
                for (double lv : Rules.get().numbers("ALL_LEVELS")) {
                    boolean basic = (g.id().equals("common") || g.id().equals("rare")) && Rules.get().numbers("LEVELS").contains(lv);
                    boolean want = basic || (g.id().equals("noble") && (lv == 10 || lv == 20)) || (g.id().equals("hero") && lv >= 30)
                            || (g.id().equals("transcend") && lv == 40);
                    Weapon w = Weapons.weaponFrom(type, lv, g.id());
                    check(Weapons.isValidWeapon(lv, g.id(), type) == want, type + " " + g.id() + " " + lv + " valid " + want);
                    check((w != null) == want, type + " " + g.id() + " " + lv + " parses " + want);
                    if (w != null) check(!w.legacy(), type + " " + g.id() + " " + lv + " not legacy");
                }
            }
            check(Weapons.parseWeapon("rpg:" + type + "_l5_noble") == null && Weapons.parseWeapon("rpg:" + type + "_l20_hero") == null
                    && Weapons.parseWeapon("rpg:" + type + "_l35_common") == null && Weapons.parseWeapon("rpg:" + type + "_l35_rare") == null, type + " no other combos");
            // 일반 · 희귀 (0 ~ 40렙 — 다른 무기와 같은 레벨 · 무기 상점): 이름 · 스킬 없음 · 같은 레벨 칼과 같은 값 · 요구 스탯 2:1
            String[] basicNames = basics.get(type);
            for (double lv : Rules.get().numbers("LEVELS")) {
                Weapon c = Weapons.weaponFrom(type, lv, "common");
                Weapon r = Weapons.weaponFrom(type, lv, "rare");
                if (c == null || r == null) continue;
                check(c.title().equals(basicNames[0]) && r.title().equals(basicNames[1]), type + " common · rare names " + c.title() + " " + r.title());
                check(c.skills() == null && r.skills() == null, type + " " + lv + " common · rare no skills");
                check(Weapons.shopPrice(c) == Weapons.shopPrice(Weapons.weaponFrom("sword", lv, "common"))
                        && Weapons.shopPrice(r) == Weapons.shopPrice(Weapons.weaponFrom("sword", lv, "rare")), type + " " + lv + " shop price = sword");
                double unit = (lv + 1) * 2.5; // 희귀 (등급 번호 1)
                check(r.reqs().size() == 2 && Math.abs(r.reqs().get(0).value() + r.reqs().get(1).value() - Math.round(unit)) <= 1
                        && r.reqs().get(0).value() >= r.reqs().get(1).value() * 1.8, type + " " + lv + " rare reqs 2:1 " + r.reqs());
                check(c.damage() < r.damage(), type + " " + lv + " common < rare damage");
            }
            Weapon n10 = Weapons.weaponFrom(type, 10, "noble");
            Weapon h30 = Weapons.weaponFrom(type, 30, "hero");
            Weapon t40 = Weapons.weaponFrom(type, 40, "transcend");
            if (n10 == null || h30 == null || t40 == null) continue;
            String[] nm = names.get(type);
            check(n10.title().equals(nm[0]) && h30.title().equals(nm[1]) && t40.title().equals(nm[2]), type + " names " + n10.title() + " " + h30.title() + " " + t40.title());
            check(t40.typeLabel().equals(nm[3]), type + " label " + t40.typeLabel());
            check(t40.id().equals("rpg:" + type + "_l40_transcend"), type + " id " + t40.id());
            // 요구 스탯 · 피해 배율
            String[] st = stats.get(type);
            check(t40.reqs().size() == 2 && t40.reqs().get(0).stat().equals(st[0]) && t40.reqs().get(1).stat().equals(st[1]), type + " req stats " + t40.reqs());
            check(t40.reqs().get(0).value() == 74 && t40.reqs().get(1).value() == 36, type + " 40 transcend reqs 74 + 36 " + t40.reqs());
            check(t40.scaling().size() == 2 && t40.scaling().get(0).stat().equals(st[0]) && t40.scaling().get(1).stat().equals(st[1]), type + " scaling stats");
            for (Weapons.Scaling sc : t40.scaling()) {
                double want = sc.stat().equals("vit") ? 0.005 : Stats.damagePerPoint(sc.stat());
                near(sc.per(), want, 1e-12, type + " " + sc.stat() + " per point");
            }
            check(Combat.isMeleeWeapon(t40) && Combat.isMeleeWeapon(n10), type + " melee");
            check(!Weapons.isRangedType(type), type + " not ranged");
            check(Weapons.isMagicType(type) == type.equals("scepter"), type + " magic skills only for the scepter");
            // 스킬
            check(n10.skills() == null && Weapons.weaponFrom(type, 20, "noble").skills() == null, type + " noble no skill");
            check(h30.skills() != null && h30.skills().size() == 1, type + " hero one skill");
            check(t40.skills() != null && t40.skills().size() == 2, type + " transcend two skills");
            if (t40.skills() == null || t40.skills().size() < 2) continue;
            Obj s1 = t40.skills().get(0);
            Obj s2 = t40.skills().get(1);
            check(s1.str("id").equals(skillIds.get(type)[0]) && s2.str("id").equals(skillIds.get(type)[1]), type + " skill ids " + s1.str("id") + " " + s2.str("id"));
            check(s1.num("cd") == 12 && s2.num("cd") == 30, type + " cooldowns 12 / 30");
            double eff = avgHit(t40) * Skills.skillTotalMult(s1);
            check(Math.abs(eff / gramSkill - 1) <= 0.08, type + " skill 1 power vs Gram " + eff / gramSkill);
            double r2 = Skills.skillTotalMult(s2) / Skills.skillTotalMult(s1);
            check(r2 >= 1.55 && r2 <= 1.65, type + " skill 2 / skill 1 = " + r2);
            for (Obj k : t40.skills()) {
                check(!k.str("name").isEmpty() && !k.str("desc").isEmpty() && !k.str("desc2").isEmpty(), type + " " + k.str("id") + " texts");
                List<Object> color = k.listOrNull("color");
                check(color != null && color.size() == 2, type + " " + k.str("id") + " color [glow, core]");
            }
            // 설명 줄
            List<String> lore = Weapons.weaponInfo(t40);
            check(lore.stream().anyMatch(l -> l.endsWith("버튼: 스킬1, 웅크리면: 스킬2")), type + " melee controls " + lore);
            String trait = switch (type) {
                case "lance" -> "찌르기: 사거리";
                case "scythe" -> "넓게 베기";
                case "hammer" -> "내려찍기";
                default -> "성스러운 일격";
            };
            check(lore.stream().anyMatch(l -> l.contains(trait)), type + " trait line " + trait);
            check(lore.stream().anyMatch(l -> l.contains("마법 피해")) == type.equals("scepter"), type + " magic line");
        }
        // 평균 평타: 창 · 낫은 칼 · 단검과 ±8%, 망치는 방패와 도끼 사이, 홀은 완드의 0.7배 이상
        double sword = avgHit(gram);
        double dagger = avgHit(Weapons.weaponFrom("dagger", 40, "transcend"));
        double shield = avgHit(Weapons.weaponFrom("shield", 40, "transcend"));
        double axe = avgHit(Weapons.weaponFrom("axe", 40, "transcend"));
        double wand = avgHit(Weapons.weaponFrom("wand", 40, "transcend"));
        double lance = avgHit(Weapons.weaponFrom("lance", 40, "transcend"));
        double scythe = avgHit(Weapons.weaponFrom("scythe", 40, "transcend"));
        double hammer = avgHit(Weapons.weaponFrom("hammer", 40, "transcend"));
        double scepter = avgHit(Weapons.weaponFrom("scepter", 40, "transcend"));
        check(Math.abs(lance / sword - 1) <= 0.08, "lance avg vs sword " + lance / sword);
        check(Math.abs(scythe / dagger - 1) <= 0.08, "scythe avg vs dagger " + scythe / dagger);
        check(hammer > shield && hammer < axe, "hammer between shield and axe " + shield + " < " + hammer + " < " + axe);
        check(scepter >= 0.7 * wand && scepter < wand, "scepter vs wand " + scepter / wand);

        // 제작: 새 무기마다 고귀 10 · 20, 영웅 30, 초월 40 한 줄씩
        int more = 0;
        Map<String, Integer> perGrade = new HashMap<>();
        Obj bossMats = Rules.get().obj("BOSS_MATERIALS");
        Obj drops = Rules.get().obj("MONSTER_DROPS");
        for (Obj r : Crafting.RECIPES) {
            perGrade.merge(r.str("grade"), 1, Integer::sum);
            if (!MORE.contains(r.str("type"))) continue;
            more++;
            Weapon w = Weapons.weaponFrom(r.str("type"), r.num("level"), r.str("grade"));
            check(w != null && !w.legacy(), "recipe weapon " + r.str("type") + " " + r.str("grade"));
            for (Crafting.Material m : Crafting.craftMaterials(r)) {
                check(m.kind().equals("boss") ? bossMats.objOrNull(m.key()) != null : drops.objOrNull(m.key()) != null, "material exists " + m.key());
                check(m.count() > 0, "material count " + m.key());
            }
            if (r.str("grade").equals("noble")) {
                check(!r.has("mat") && r.num("ore") == 0 && Crafting.craftMaterials(r).stream().allMatch(m -> m.kind().equals("drop")), "noble: drops only");
            } else {
                check(Crafting.craftMaterials(r).get(0).kind().equals("boss") && r.num("ore") > 0, "hero / transcend: boss material first + ore");
            }
        }
        check(more == 16, "16 new recipes, got " + more);
        check(perGrade.get("noble") == 8 && perGrade.get("hero") == 8 && perGrade.get("transcend") == 9, "per page ≤ 9: " + perGrade);
        check(Crafting.craftUseLines("boss", "worm_fang").contains("§r§f게이 볼그 §7영웅 8"), "worm fang lore " + Crafting.craftUseLines("boss", "worm_fang"));
        check(Crafting.craftUseLines("boss", "worm_fang").contains("§r§f궁니르 §7초월 4"), "worm fang transcend lore " + Crafting.craftUseLines("boss", "worm_fang"));
        check(Crafting.craftUseLines("drop", "boar").contains("§r§f황금 기사창 §7고귀 6"), "boar noble lore " + Crafting.craftUseLines("drop", "boar"));

        // 스탯 창 글자
        check(Stats.info("str").str("weapons").contains("창") && Stats.info("str").str("weapons").contains("망치"), "str weapons text");
        check(Stats.info("dex").str("weapons").contains("낫") && Stats.info("int").str("weapons").contains("홀"), "dex · int weapons text");

        // 정령왕 강림
        Runes.Rune king = Runes.parseRune(Runes.runeId("spirit", "transcend"));
        check(king != null && king.def().str("fn").equals("spiritKing"), "spirit transcend fn");
        if (king != null) near(Runes.runeTotalMult(king.def()), 32, 1e-9, "spirit king total = other transcend runes");
        for (String g : List.of("common", "rare", "noble", "hero")) {
            Runes.Rune r = Runes.parseRune(Runes.runeId("spirit", g));
            check(r != null && r.def().str("fn").equals("summonSpirits"), "spirit " + g + " unchanged");
        }

        // 도끼 초월: 궁니르 (창 모양) → 반고의 도끼 (0.12.2) → 세계수의 도끼 (0.12.4 — 사용자가 고른 새 컨셉, 동작을 새로). 종류 글자 · 평타는 도끼 (잎빛),
        // 배율 합 · 재사용 대기 · 회복은 예전과 같음 (9.9 · 15.9 · 12 · 30초 · 15%)
        Weapon axeT = Weapons.parseWeapon("rpg:axe_l40_transcend");
        check(axeT != null && axeT.title().equals("세계수의 도끼"), "axe transcend name " + (axeT == null ? null : axeT.title()));
        if (axeT != null) {
            check(axeT.typeLabel().equals("도끼") && "ygg_axe".equals(axeT.look()), "axe transcend label · look " + axeT.typeLabel() + " " + axeT.look());
            Obj yggSlash = Weapons.slashStyle("ygg_axe");
            check(yggSlash != null && "heavy".equals(yggSlash.strOrNull("shape")) && yggSlash.has("aura") && yggSlash.num("size") == Weapons.slashStyle("axe").num("size"),
                    "axe transcend slash = axe heavy (base) · jade glow · sprout aura");
            check(Weapons.weaponInfo(axeT).get(0).contains("도끼 · 레벨 40"), "axe transcend info " + Weapons.weaponInfo(axeT).get(0));
            List<Obj> sk = axeT.skills();
            check(sk != null && sk.size() == 2 && sk.get(0).str("id").equals("ygg_seed") && sk.get(1).str("id").equals("ygg_tree"), "axe transcend skills");
            if (sk != null && sk.size() == 2) {
                near(Skills.skillTotalMult(sk.get(0)), 4.4 + 5.5, 1e-9, "ygg_seed total = old axe skill 1");
                near(Skills.skillTotalMult(sk.get(1)), 9.5 + 3.2 * 2, 1e-9, "ygg_tree total = old axe skill 2");
                check(sk.get(0).num("cd") == 12 && sk.get(1).num("cd") == 30, "axe transcend cooldowns");
                check(sk.get(1).num("heal") == 0.15 && sk.get(1).num("healCap") == 0.35, "axe transcend heal (딜탱)");
            }
        }
        // 방패 초월: 아이기스의 방패 → 스발린 (0.12.4 — 동작 · 수치는 그대로, 이름 · 연출만)
        Weapon shieldT = Weapons.parseWeapon("rpg:shield_l40_transcend");
        check(shieldT != null && shieldT.title().equals("스발린"), "shield transcend name " + (shieldT == null ? null : shieldT.title()));
        if (shieldT != null) {
            check(shieldT.typeLabel().equals("방패"), "shield transcend label " + shieldT.typeLabel());
            List<Obj> sk = shieldT.skills();
            check(sk != null && sk.size() == 2 && sk.get(0).str("id").equals("svalinn_charge") && sk.get(1).str("id").equals("svalinn_dome"), "shield transcend skills");
            if (sk != null && sk.size() == 2) {
                near(Skills.skillTotalMult(sk.get(0)), 9.5 + 20, 1e-9, "svalinn_charge total = aegis_charge");
                near(Skills.skillTotalMult(sk.get(1)), 7 + 10 + 30, 1e-9, "svalinn_dome total = aegis_dome");
                check(sk.get(0).num("cd") == 12 && sk.get(1).num("cd") == 30 && sk.get(1).num("reduce") == 0.6, "shield transcend cooldowns · reduce");
            }
        }
        for (String g : List.of("common", "rare", "noble", "hero")) {
            Weapon sh = Weapons.weaponFrom("shield", g.equals("hero") ? 30 : 10, g);
            check(sh != null && !sh.title().equals("스발린"), "shield " + g + " name unchanged " + (sh == null ? null : sh.title()));
        }
        check(Weapons.weaponFrom("shield", 30, "hero").title().equals("듀란드의 방패"), "shield hero name unchanged");
        check(Weapons.weaponFrom("axe", 40, "hero").title().equals("대지의 심판"), "axe hero name unchanged");

        // 창 초월: 브류나크 → 궁니르 (0.12.3 — 예전 도끼 초월의 궁니르를 옮김). 종류 글자 '창', 평타는 애드온 궁니르의 찌르기 (SLASH.spear) 를 바탕으로 디자인 색 (0.12.8 — SLASH.gungnir 청록빛),
        // 스킬 = 신창 귀환 · 신창 강림 (재사용 대기 그대로, 배율 합은 창 세기 8.5 · 13.6 — 예전 궁니르 9.9 · 15.9 와 같은 비율), 도끼 규칙의 회복은 없음
        Weapon lanceT = Weapons.parseWeapon("rpg:lance_l40_transcend");
        check(lanceT != null && lanceT.title().equals("궁니르"), "lance transcend name " + (lanceT == null ? null : lanceT.title()));
        if (lanceT != null) {
            check(lanceT.typeLabel().equals("창") && "gungnir".equals(lanceT.look()), "lance transcend label · look " + lanceT.typeLabel() + " " + lanceT.look());
            Obj gn = Weapons.slashStyle("gungnir");
            check(gn != null && gn.truthy("thrust") && gn.num("length") == Weapons.slashStyle("spear").num("length"), "gungnir look = add-on spear thrust (base) · cyan");
            check(Weapons.weaponInfo(lanceT).get(0).contains("창 · 레벨 40"), "lance transcend info " + Weapons.weaponInfo(lanceT).get(0));
            List<Obj> sk = lanceT.skills();
            if (sk != null && sk.size() == 2) {
                near(Skills.skillTotalMult(sk.get(0)), 8.5, 1e-9, "gungnir_return total = lance skill 1");
                near(Skills.skillTotalMult(sk.get(1)), 13.6, 1e-9, "gungnir_fall total = lance skill 2");
                near(sk.get(0).num("mult") / Skills.skillTotalMult(sk.get(0)), 4.4 / 9.9, 0.01, "gungnir_return out : back like the add-on");
                near(sk.get(1).num("mult") / Skills.skillTotalMult(sk.get(1)), 9.5 / 15.9, 0.01, "gungnir_fall strike : bolts like the add-on");
                check(sk.get(0).num("cd") == 12 && sk.get(1).num("cd") == 30, "lance transcend cooldowns");
                check(!sk.get(1).has("heal") && !(sk.get(1).str("desc") + sk.get(1).str("desc2")).contains("회복"), "lance gungnir: no axe heal");
            }
        }
        check(Weapons.weaponFrom("lance", 40, "hero").title().equals("게이 볼그"), "lance hero name unchanged");
        check(Crafting.craftUseLines("boss", "ygg_branch").stream().anyMatch(l -> l.contains("세계수의 도끼")), "boss material lore uses the new name");

        // 0.12.8: 영웅 · 초월 무기 21종 — 무기마다 디자인 색의 평타 모양 (LOOK → SLASH, base 를 따라 바탕 모양의 값을 받음)
        for (String type : List.of("sword", "dagger", "axe", "shield", "bow", "wand", "greatsword", "lance", "scythe", "hammer", "scepter")) {
            for (String g : List.of("hero", "transcend")) {
                Weapon w = Weapons.weaponFrom(type, 40, g);
                if (w == null) continue; // 마검 영웅은 없음
                Obj st = Weapons.slashStyle(w.look());
                check(w.look() != null && st != null && st.has("glow") && !st.has("base"), type + " " + g + " look " + w.look());
                if (st == null) continue;
                boolean ranged = type.equals("bow") || type.equals("wand");
                Obj plain = Weapons.slashStyle(type.equals("lance") && g.equals("transcend") ? "spear" : type);
                if (ranged) check(st.has("core"), type + " " + g + " look has core (arrow · trail)");
                else check(plain != null && st.truthy("thrust") == plain.truthy("thrust") && st.truthy("bash") == plain.truthy("bash")
                        && java.util.Objects.equals(st.strOrNull("shape"), plain.strOrNull("shape")) && st.numOr("size", 0) == plain.numOr("size", 0),
                        type + " " + g + " look keeps the type's shape · size");
                if (st.has("crit")) check(st.list("crit").size() == 2, type + " " + g + " crit [glow, core]");
            }
        }
        check(Weapons.weaponFrom("sword", 40, "rare").look() == null, "below hero: type look");
        check(Weapons.slashStyle("nope") == null && Weapons.slashStyle(null) == null, "unknown look");

        System.out.println("WeaponsMoreTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
