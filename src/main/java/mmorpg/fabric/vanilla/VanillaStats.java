package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Stats;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.StatSheet;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴의 스탯 쪽 (VanillaMenu). 2판 — 띄운 기둥 넷 (칸 1 · 3 · 5 · 7, 바탕에 이름 띠 · 그림 받침 · 값 받침):
 * <pre>
 * 스탯                                  남은 포인트 15
 * [ 힘 ] [민첩] [체력] [마력]       줄 1  이름 (바탕)
 * ( ⚔ ) ( ➶ ) ( ♥ ) ( ★ )       줄 2  그림 (아이템)
 *  126    85    60   240         줄 3  값 (버프로 오른 값은 초록) — 효과는 설명 상자 (힘 · 민첩 · 마력 = 공격력 +n, 체력 = 최대 체력)
 *   +     +     +     +          줄 4
 *        [ ⚘ 스탯 트리 ]            줄 5  (가운데 점선 화살표가 가리킴)
 * </pre>
 * 기둥의 칸 (줄 1 ~ 4) 아무 데나 좌클릭 +1 · Shift + 좌클릭 +5. 스탯 트리 단추 = 스탯 트리 쪽 (VanillaTree).
 */
public final class VanillaStats {
    private VanillaStats() {}

    /** 기둥 차례 (바탕 그림과 같음 — 빨강 · 초록 · 분홍 · 파랑) */
    static final String[] ORDER = {"str", "dex", "vit", "int"};

    /** 스탯 쪽으로 메뉴 열기 (RpgNet.openStats — 스탯 책) */
    public static void open(ServerPlayer sp) {
        VanillaMenu.open(sp, VanillaMenu.Page.STATS);
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        StatSheet s = StatSheet.of(sp);
        int point = s.point();
        // 제목 줄: 남은 포인트만 (마름모 + 숫자 — 사용자 "글자를 최대한 줄여서")
        String pt = String.valueOf(point);
        t.right(166, 0, pt, point > 0 ? VanillaGui.GREEN : VanillaGui.DIM);
        t.text(166 - VanillaGui.width(pt) - VanillaGui.width(VanillaGui.GEM) - 2, 0, VanillaGui.GEM, VanillaGui.WHITE);

        // 힘 · 민첩 · 마력의 공격력은 지금 든 무기로 (장비 쪽 총 스펙 · 전투력과 같은 무기)
        mmorpg.fabric.CombatPowers.Sheet basis = mmorpg.fabric.CombatPowers.of(sp);
        for (int i = 0; i < ORDER.length; i++) {
            if (!Stats.STAT_KEYS.contains(ORDER[i])) continue;
            pillar(g, t, s, ORDER[i], 1 + 2 * i, point, basis);
        }
        // 스탯 트리 단추 (줄 5 · 칸 4 — 그림만) + 화살표 둘레 (줄 4 · 칸 4)
        List<String> tip = List.of("§7남은 포인트 §f" + point, "§7스탯마다 열리는 칸 · 고를 갈래", "", "§e눌러서 열기");
        GuiElement.ClickCallback tree = VanillaMenu.MenuGui.left(() -> {
            g.treeSel = -1;
            g.treeTop = -1;
            g.show(VanillaMenu.Page.TREE);
        });
        g.set(5, 4, VanillaGui.blank("§a스탯 트리", tip), tree);
        g.set(4, 4, VanillaGui.blank("§a스탯 트리", tip), tree);
    }

    /**
     * 기둥 하나 (칸 c · 줄 1 ~ 4): 그림 (줄 2) · 값 (숫자 d 68) — 칸 넷 모두 좌클릭 +1 · Shift + 좌클릭 +5.
     * 설명: 힘 · 민첩 · 마력은 피해 % 대신 기준 무기에 더하는 공격력 (2026-11-08 사용자 "힘이랑 민첩, 마력 공격력 + n으로 바꿔"), 체력은 그대로 (최대 체력).
     */
    private static void pillar(VanillaMenu.MenuGui g, VanillaGui.Title t, StatSheet s, String k, int c, int point, mmorpg.fabric.CombatPowers.Sheet basis) {
        Obj info = Stats.info(k);
        int value = s.stat(k);
        double eff = Stats.get(s.eff(), k);
        int shown = (int) Math.round(eff);
        boolean buffed = shown > value;
        int mid = VanillaGui.cellMid(c);
        t.numCenter(mid, 68, VanillaGui.compact(shown), buffed ? VanillaGui.GREEN : VanillaGui.WHITE);

        List<String> tip = new ArrayList<>();
        if (k.equals("vit")) {
            tip.addAll(s.tooltip(k));
            if (!tip.isEmpty()) tip.remove(tip.size() - 1); // 모드 창의 "눌러서 +1 (Shift: +5)" 줄
        } else {
            tip.addAll(attackLines(s, k, info, basis.weapon(), basis.enh()));
        }
        if (buffed) tip.add("§7찍은 값 §f" + value + " §7+ 장비 · 버프 §a" + (shown - value));
        if (k.equals("vit")) tip.add("§7최대 체력 " + VanillaGui.compact(Math.round(s.maxHp())));
        tip.add("§7남은 포인트 §f" + point);
        tip.add("");
        tip.add(point > 0 ? "§a좌클릭 +1 §7· §aShift + 좌클릭 +5" : "§8남은 포인트가 없습니다");
        String title = colorCode(info.str("color")) + "§l" + info.str("name") + " §r§f" + value + (buffed ? " §a(" + shown + ")" : "");
        GuiElement.ClickCallback plus = (ix, type, a, gg) -> plus(g, k, type);
        for (int r = 1; r <= 4; r++) {
            g.set(r, c, r == 2 ? VanillaGui.ui("icon_" + k, title, tip) : VanillaGui.blank(title, tip), plus);
        }
    }

    /**
     * 힘 · 민첩 · 마력의 공격력 줄: "공격력 +79.2 (1당 +0.6)" · "칼·도끼·마검·창·망치 · 든 칼 기준". 공격력 = 이 스탯이 지금 든 무기 (강화 포함) 에 더하는 값 —
     * 무기 설명 · 강화 창의 "공격력" 과 같은 단위 (스탯 트리 공격력 % 는 총 스펙에서 곱함).
     */
    static List<String> attackLines(StatSheet s, String k, Obj info, mmorpg.core.Weapon w, int enh) {
        List<String> out = new ArrayList<>();
        if (w == null) {
            out.add("§7공격력 — 든 RPG 무기가 없음");
            out.add("§7" + info.str("weapons"));
            return out;
        }
        double n = s.statAttack(w, enh, k);
        double per = s.statAttackPer(w, enh, k);
        out.add("§7공격력 §f+" + one(n) + (per > 0 ? " §7(1당 +" + (per >= 1 ? one(per) : two(per)) + ")" : " §7(" + w.typeLabel() + "에는 붙지 않음)"));
        out.add("§7" + info.str("weapons") + " · 든 " + w.typeLabel() + " 기준");
        return out;
    }

    /** 소수 한 자리 (끝의 0 은 뺌) */
    static String one(double v) {
        return Js.str(Math.round(v * 10) / 10.0);
    }

    /** 소수 둘째 자리 */
    static String two(double v) {
        return Js.str(Math.round(v * 100) / 100.0);
    }

    /** 좌클릭 +1, Shift + 좌클릭 +5 */
    private static void plus(VanillaMenu.MenuGui g, String k, ClickType type) {
        if (!type.isLeft) return;
        int before = StatSheet.of(g.sp).point();
        RpgPlayers.spend(g.sp, k, type.shift ? 5 : 1);
        if (StatSheet.of(g.sp).point() < before) SkillFx.playFor(g.sp, "entity.experience_orb.pickup", type.shift ? 1.4 : 1.1, 0.5);
        g.refresh();
    }

    /** "§c" 같은 색 글자만 (Stats.info color 는 "§c" 꼴) */
    static String colorCode(String c) {
        return c == null || c.length() < 2 ? "§f" : c.substring(0, 2);
    }

    /** § 서식 빼기 */
    static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
