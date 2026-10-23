package mmorpg.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.12.5 밸런스 시험 (JUnit 없이 main — check-core.sh): 두 스탯 무기용 갑옷 5종 · 방패 상시 감소 상한 · 가벼운 갑옷 방어 (자바 전용 덧붙임).
 * <ul>
 * <li>종류 10개 (애드온 5 + 마력+힘 · 힘+민첩 · 민첩+마력 · 체력+힘 · 마력+체력) × 레벨 · 등급 6 × 부위 4 = 240, ID 겹침 없음 · 모르는 종류는 갑옷 아님</li>
 * <li>두 스탯 무기 (도끼 · 마검 · 창 · 낫 · 전투 망치 · 성스러운 홀) 마다 요구 비율이 같은 갑옷이 하나 — 무기 요구 = 같은 레벨 · 등급 갑옷 요구,
 *     무기 + 갑옷 요구가 그 레벨 포인트 안 (40렙 125: 초월 74 + 36)</li>
 * <li>세트 보너스 두 스탯 0.6씩 (초월 19 · 19), 방어 = 두 스탯 갑옷 방어 평균 (황제 마력+힘 22 · 민첩+마력 20 …), 민첩 0.9 · 마력 0.8</li>
 * <li>방패 상시 감소 상한 25% (체력이 아무리 높아도), 갑옷 값 (종류와 상관없음)</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class ArmorMoreTest {
    private ArmorMoreTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static final List<String> MORE = List.of("intstr", "strdex", "dexint", "vitstr", "intvit");
    /** 두 스탯 무기 → 갑옷 */
    private static final Map<String, String> WEAPON_ARMOR = Map.of("axe", "strvit", "greatsword", "intstr", "lance", "strdex", "scythe", "dexint",
            "hammer", "vitstr", "scepter", "intvit");

    public static void main(String[] argv) {
        Rules R = Rules.get();
        check(Rules.hasMore(), "overlay present");
        List<String> order = R.strings("ARMOR_TYPE_ORDER");
        List<String> all = new ArrayList<>(List.of("str", "dex", "vit", "strvit", "int"));
        all.addAll(MORE);
        check(order.equals(all), "order " + order);

        // 240 개 · ID 겹침 없음
        Set<String> ids = new HashSet<>();
        int n = 0;
        Obj gl = R.obj("ARMOR_GRADE_LEVELS");
        for (String t : order) {
            for (String g : List.of("common", "transcend")) {
                for (double lv : gl.numbersOrNull(g)) {
                    for (String p : Armor.PIECE_ORDER) {
                        Armor.Piece a = Armor.armorFrom(t, lv, g, p);
                        check(a != null, "armor " + t + " " + lv + " " + g + " " + p);
                        if (a == null) continue;
                        n++;
                        ids.add(a.id());
                        check(a.type().equals(t) && a.icon().equals("textures/items/rpg/armor/" + t + "_l" + Js.str(lv) + "_" + g + "_" + p), "icon " + a.id());
                        check(Armor.armorPrice(a) >= 1, "price " + a.id());
                    }
                }
            }
        }
        check(n == 280 && ids.size() == 280, "armor count " + n + " ids " + ids.size() + " (0.36.0 공허 초월 50렙 40 포함)");
        check(Armor.parseArmor("rpg:armor_strint_l40_common_chest") == null, "unknown type is not armor");
        check(Armor.parseArmor("rpg:armor_intstr_l35_common_chest") == null, "no 35 armor");
        check(Armor.parseArmor("rpg:armor_intstr_l20_transcend_chest") == null, "transcend only 40 · 50");
        check(Armor.parseArmor("rpg:armor_intstr_l50_transcend_chest") != null && Armor.parseArmor("rpg:armor_intstr_l50_common_chest") == null, "void 50 is transcend only");

        // 두 스탯 무기 ↔ 갑옷: 요구 같음 · 포인트 안
        Obj cfg = R.obj("CONFIG");
        for (Map.Entry<String, String> e : WEAPON_ARMOR.entrySet()) {
            String wt = e.getKey();
            String at = e.getValue();
            check(Armor.weaponsFor(at).equals(List.of(wt)), "weaponsFor " + at + " " + Armor.weaponsFor(at));
            for (String g : List.of("common", "transcend")) {
                for (double lv : gl.numbersOrNull(g)) {
                    Armor.Piece a = Armor.armorFrom(at, lv, g, "chest");
                    if (a == null) continue;
                    int gi = R.grade(g).index();
                    // 0렙은 요구 0 (갑옷은 0 인 요구를 뺌 — 무기는 0 으로 둠)
                    List<Weapons.Req> wr = Weapons.requiredStats(lv, gi, wt).stream().filter(q -> q.value() > 0).toList();
                    check(a.reqs().equals(wr), "reqs " + at + " " + lv + " " + g + " " + a.reqs() + " vs " + wr);
                    double points = cfg.num("START_POINTS") + cfg.num("POINTS_PER_LEVEL") * lv;
                    double need = 0;
                    for (Weapons.Req q : wr) need = need + q.value();
                    check(need <= points, "fits " + at + " " + lv + " " + g + " need " + need + " points " + points);
                }
            }
        }
        // 한 스탯 갑옷은 그대로
        check(Armor.weaponsFor("str").equals(List.of("sword")), "str → sword " + Armor.weaponsFor("str"));
        check(Armor.weaponsFor("dex").equals(List.of("dagger", "bow")), "dex → dagger · bow " + Armor.weaponsFor("dex"));
        check(Armor.weaponsFor("vit").equals(List.of("shield")), "vit → shield " + Armor.weaponsFor("vit"));
        check(Armor.weaponsFor("int").equals(List.of("wand")), "int → wand " + Armor.weaponsFor("int"));
        Armor.Piece int40 = Armor.armorFrom("intstr", 40, "transcend", "chest");
        check(int40.reqs().equals(List.of(new Weapons.Req("int", 74), new Weapons.Req("str", 36))), "intstr transcend reqs " + int40.reqs());

        // 보너스 · 방어
        Obj types = R.obj("ARMOR_TYPES");
        Map<String, Double> single = Map.of("str", 1.0, "dex", 0.9, "vit", 1.2, "int", 0.8);
        for (String s : single.keySet()) check(types.obj(s).num("prot") == single.get(s), "prot " + s + " " + types.obj(s).num("prot"));
        for (String t : MORE) {
            Obj d = types.obj(t);
            List<String> stats = new ArrayList<>(d.obj("stats").keys());
            check(stats.size() == 2 && d.obj("stats").num(stats.get(0)) == 0.6 && d.obj("stats").num(stats.get(1)) == 0.6, "stats " + t);
            check(new ArrayList<>(d.obj("req").keys()).equals(stats), "req keys = stats keys " + t);
            double avg = (single.get(stats.get(0)) + single.get(stats.get(1))) / 2;
            check(Math.abs(d.num("prot") - avg) < 1e-9, "prot " + t + " " + d.num("prot") + " vs " + avg);
            check(d.has("badge") && d.has("badge2") && d.str("color").startsWith("§"), "look " + t);
            Armor.Piece c = Armor.armorFrom(t, 40, "transcend", "chest");
            check(c.setBonus().equals(Map.of(stats.get(0), 19.0, stats.get(1), 19.0)), "transcend bonus " + t + " " + c.setBonus());
        }
        check(Armor.armorFrom("intstr", 40, "transcend", "chest").setProt() == 22, "emperor intstr prot");
        check(Armor.armorFrom("dexint", 40, "transcend", "chest").setProt() == 20, "emperor dexint prot");
        check(Armor.armorFrom("vitstr", 40, "transcend", "chest").setProt() == 26 && Armor.armorFrom("strvit", 40, "transcend", "chest").setProt() == 26, "emperor vitstr prot = strvit");
        check(Armor.armorFrom("dex", 40, "transcend", "chest").setProt() == 22, "emperor dex prot (0.9)");
        check(Armor.armorFrom("int", 40, "transcend", "chest").setProt() == 19, "emperor int prot (0.8)");
        check(Armor.setPrice("intstr", 40) == Armor.setPrice("str", 40), "same price as other types");

        // 0.36.0 공허의 갑옷 (초월 50렙 — 세트 void · 방어 [4, 10, 8, 4] · 세트 보너스 40 · 요구 (50 + 4) × 2.5 = 135)
        for (String t : order) {
            Armor.Piece v = Armor.armorFrom(t, 50, "transcend", "chest");
            Armor.Piece e = Armor.armorFrom(t, 40, "transcend", "chest");
            check(v != null && v.set().equals("void") && v.setName().equals("공허") && v.title().equals("공허 갑옷"), "void " + t);
            if (v == null) continue;
            check(v.name().contains("[초월 50렙]") && e.name().contains("[초월]"), "void name " + v.name());
            check(v.setProt() > e.setProt(), "void prot " + t + " " + v.setProt() + " vs " + e.setProt());
            double sum = 0;
            for (double b : v.setBonus().values()) sum += b;
            check(sum == (v.setBonus().size() == 1 ? 40 : 48), "void bonus " + t + " " + v.setBonus());
            double need = 0;
            for (Weapons.Req q : v.reqs()) need += q.value();
            check(Math.abs(need - 135) <= 1 && v.reqLevel() == 50, "void reqs " + t + " " + v.reqs());
        }
        check(Armor.armorFrom("str", 50, "transcend", "chest").setProt() == 26 && Armor.armorFrom("int", 50, "transcend", "chest").setProt() == 20,
                "void prot str 26 · int 20 (" + Armor.armorFrom("int", 50, "transcend", "chest").setProt() + ")");
        Obj va = R.obj("VOID_ARMOR");
        int gold = 0;
        for (String p : Armor.PIECE_ORDER) {
            Obj c = va.obj("cost").obj(p);
            gold += (int) c.num("gold");
            check(c.obj("items").keys().size() == 3, "void cost items " + p);
        }
        check(gold == 48 && va.num("level") == 50 && va.str("grade").equals("transcend"), "void set gold " + gold);

        // 방패 상시 감소 상한 25%
        Obj shield = R.obj("SHIELD");
        check(shield.num("passiveCap") == 0.25, "shield cap " + shield.num("passiveCap"));
        check(shield.num("blockCap") == 0.95 && shield.num("passiveBase") == 0.05, "rest of SHIELD kept");
        Weapon s40 = Weapons.weaponFrom("shield", 40, "transcend");
        check(Math.abs(s40.passive() - 0.23) < 1e-9, "40 transcend shield base passive " + s40.passive());
        check(Math.min(shield.num("passiveCap"), s40.passive() + 1000 * shield.num("passivePerVit")) == 0.25, "capped at 25%");
        check(String.join("\n", Weapons.weaponInfo(s40)).contains("(최대 25%)"), "shield lore shows the cap");

        System.out.println("ArmorMoreTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String b : BAD) System.out.println("  BAD " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
