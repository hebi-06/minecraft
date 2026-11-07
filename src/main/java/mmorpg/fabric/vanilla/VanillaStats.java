package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import mmorpg.core.Obj;
import mmorpg.core.Stats;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.StatSheet;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴의 스탯 쪽 (VanillaMenu — 사용자 "스탯에 들어가면 5번째 사진처럼 깔끔하게 가운데 아래 보면 화살표 있는데 저게 스탯 트리로"):
 * <pre>
 * 스탯                                   남은 포인트 15     (제목 줄)
 * ┌ 진열장 ─────────────────────────────────────┐
 * │        [힘] [민첩] [체력] [마력]   ← 선반 위   │  줄 0 · 칸 3 ~ 6 (좌클릭 +1 · Shift + 좌클릭 +5)
 * │        126   85    60   240      ← 값 받침  │  줄 1 (같은 단추 — 버프로 오른 값은 초록)
 * │                  ▼                         │  줄 2
 * └───────[   스탯 트리   ]────────────────────┘  줄 3 · 칸 3 ~ 6
 * </pre>
 * 스탯마다 설명 (마우스): 효과 한 줄 · 1포인트 효과 · 누름 안내. 스탯 트리 단추 = 메뉴를 닫고 스탯 트리 (모드 없는 접속은 채팅 요약).
 */
public final class VanillaStats {
    private VanillaStats() {}

    /** 스탯 쪽으로 메뉴 열기 (RpgNet.openStats — 스탯 책) */
    public static void open(ServerPlayer sp) {
        VanillaMenu.open(sp, VanillaMenu.Page.STATS);
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        StatSheet s = StatSheet.of(sp);
        int point = s.point();
        t.text(31, 0, "스탯", VanillaGui.INK);
        String pt = String.valueOf(point);
        t.right(168, 0, pt, point > 0 ? VanillaGui.OK : VanillaGui.SUB);
        t.right(168 - VanillaGui.width(pt) - 4, 0, "남은 포인트", VanillaGui.SUB);

        List<String> keys = Stats.STAT_KEYS;
        String[] order = {"str", "dex", "vit", "int"}; // 사진처럼 빨강 · 초록 · 분홍 · 파랑
        int col = 3;
        for (String k : order) {
            if (!keys.contains(k)) continue;
            stat(g, t, s, k, col++, point);
        }
        // 스탯 트리 단추 (줄 3 · 칸 3 ~ 6) + 화살표 둘레 (줄 2 · 칸 4 ~ 5)
        List<String> tip = List.of("§7스탯마다 받은 칸 · 고를 갈래", "§8(모드 없는 접속은 채팅 요약)", "", "§e눌러서 열기");
        GuiElement.ClickCallback tree = VanillaMenu.MenuGui.left(() -> g.closeThen(p -> mmorpg.fabric.stattree.StatTrees.open(p, null)));
        for (int c = 3; c <= 6; c++) g.set(3, c, VanillaGui.blank("§a스탯 트리", tip), tree);
        for (int c = 4; c <= 5; c++) g.set(2, c, VanillaGui.blank("§a스탯 트리", tip), tree);
    }

    /** 스탯 하나: 선반 위 그림 (줄 0) · 값 (줄 1) — 둘 다 좌클릭 +1 · Shift + 좌클릭 +5 */
    private static void stat(VanillaMenu.MenuGui g, VanillaGui.Title t, StatSheet s, String k, int c, int point) {
        Obj info = Stats.info(k);
        String name = info.str("name");
        int value = s.stat(k);
        double eff = Stats.get(s.eff(), k);
        int shown = (int) Math.round(eff);
        boolean buffed = shown > value;
        t.numCenter(VanillaGui.cellMid(c), 38, VanillaGui.compact(shown), buffed ? VanillaGui.GREEN : VanillaGui.WHITE);

        List<String> tip = new ArrayList<>(s.tooltip(k));
        if (!tip.isEmpty()) tip.remove(tip.size() - 1); // 모드 창의 "눌러서 +1 (Shift: +5)" 줄
        if (buffed) tip.add("§7찍은 값 §f" + value + " §7+ 장비 · 버프 §a" + (shown - value));
        tip.add("");
        tip.add(point > 0 ? "§a좌클릭 +1 §7· §aShift + 좌클릭 +5" : "§8남은 포인트가 없습니다");
        String title = colorCode(info.str("color")) + "§l" + name + " §r§f" + value + (buffed ? " §a(" + shown + ")" : "");
        GuiElement.ClickCallback plus = (ix, type, a, gg) -> plus(g, k, type);
        g.set(0, c, VanillaGui.ui("icon_" + k, title, tip), plus);
        g.set(1, c, VanillaGui.blank(title, tip), plus);
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
