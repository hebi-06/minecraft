package mmorpg.fabric.vanilla;

import mmorpg.core.StatTree;
import mmorpg.core.Stats;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.Inv;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 메뉴의 스탯 트리 쪽 (스탯 쪽 가운데 아래 단추 — 사용자 "가운데 아래 보면 화살표 있는데 저게 스탯 트리로 하고 싶음"). 모드 클라이언트의 스탯 트리 창 대신:
 * <pre>
 * 스탯 트리                                   힘 126 · 7/10
 * [힘][민첩][마력][체력]              [초기화] [합계]   줄 0
 * (1)─(2)─(3)─(4)─(5)        [갈래 A] 이름          줄 1   칸 열 개는 뱀 모양 (1 → 5, 6 ← 10)
 * (10)─(9)─(8)─(7)─(6)       [갈래 B] 이름          줄 2
 * 고른 칸 설명                                         줄 3
 * </pre>
 * 칸: 열림 = 보너스 종류 그림 (갈래는 고른 쪽 그림 · 아직 안 골랐으면 빛나는 고르기 그림), 닫힘 = 자물쇠. 갈래 칸을 누르면 오른쪽에 그 갈래 둘 —
 * 눌러서 고름 (StatTrees.pick). 초기화 = Shift + 좌클릭 (실수로 지우지 않게 — StatTrees.reset, 실버).
 */
public final class VanillaTree {
    private VanillaTree() {}

    /** 칸 i 의 자리 (뱀 모양 — 0 ~ 4 줄 1 왼쪽부터, 5 ~ 9 줄 2 오른쪽부터) */
    static int row(int i) {
        return i < 5 ? 1 : 2;
    }

    static int col(int i) {
        return i < 5 ? 1 + i : 5 - (i - 5);
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        List<String> stats = StatTree.stats();
        if (stats.isEmpty()) return;
        if (g.treeStat == null || StatTree.tree(g.treeStat) == null) g.treeStat = stats.get(0);
        String stat = g.treeStat;
        StatTree.Tree tree = StatTree.tree(stat);
        double spent = Stats.get(StatTrees.spent(sp), stat);
        int tier = StatTree.tier(stat, spent);
        StatTree.State state = StatTrees.state(sp);
        Map<Integer, Integer> picks = state.picksOf(stat);
        String statName = Stats.info(stat).str("name");

        t.text(31, 0, "스탯 트리", VanillaGui.INK);
        t.right(168, 0, statName + " " + (int) spent + " · " + tier + "/" + tree.nodes().size(), VanillaGui.SUB);

        // 나무 탭 (줄 0 · 칸 1 ~ 4)
        for (int i = 0; i < stats.size() && i < 4; i++) {
            String s = stats.get(i);
            StatTree.Tree tr = StatTree.tree(s);
            double sp2 = Stats.get(StatTrees.spent(sp), s);
            int tr2 = StatTree.tier(s, sp2);
            int pend = StatTree.pending(s, sp2, state.picksOf(s)).size();
            boolean on = s.equals(stat);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + Stats.info(s).str("name") + " " + (int) sp2 + " · 열린 칸 " + tr2 + "/" + tr.nodes().size());
            if (pend > 0) lore.add("§e고를 갈래 " + pend + "개");
            lore.add("");
            lore.add(on ? "§e보는 중" : "§a눌러서 보기");
            ItemStack icon = VanillaGui.ui("icon_" + s, VanillaStats.colorCode(Stats.info(s).str("color")) + tr.title(), lore);
            if (on || pend > 0) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            g.set(0, 1 + i, icon, VanillaMenu.MenuGui.left(() -> {
                if (s.equals(g.treeStat)) return;
                g.treeStat = s;
                g.treeSel = -1;
                g.refresh();
            }));
        }
        // 초기화 (Shift + 좌클릭) · 합계
        int cost = StatTrees.resetCost(sp, stat);
        int picked = state.pickCount(stat);
        g.set(0, 7, VanillaGui.ui("icon_cross", picked > 0 ? "§c갈래 초기화" : "§8갈래 초기화", List.of(
                picked > 0 ? "§7" + statName + " 나무의 고른 갈래 " + picked + "개를 지움" : "§8고른 갈래가 없습니다",
                picked > 0 ? "§7비용 §f" + Inv.CURRENCY_NAME + " " + cost : "",
                "",
                "§eShift + 좌클릭으로 초기화 §8(실수로 지우지 않게)")), (ix, type, a, gg) -> {
            if (!type.isLeft || !type.shift) return;
            String err = StatTrees.reset(sp, stat, false);
            if (err != null) warn(sp, err);
            g.treeSel = -1;
            g.refresh();
        });
        g.set(0, 8, VanillaGui.ui("info", "§a스탯 트리 합계", totals(sp)), null);

        // 칸 열 (줄 1 ~ 2 · 칸 1 ~ 5)
        int sel = selected(g, tree, tier, picks);
        for (int i = 0; i < tree.nodes().size() && i < 10; i++) node(g, stat, tree.nodes().get(i), i, tier, picks, i == sel);

        // 갈래 (칸 6 ~ 8) · 아래 설명
        if (sel >= 0) {
            StatTree.Node n = tree.nodes().get(sel);
            boolean open = sel < tier;
            Integer chosen = picks.get(sel);
            for (int o = 0; o < n.pick().size() && o < 2; o++) option(g, t, stat, n, sel, o, open, chosen);
            String head = (sel + 1) + "단계 갈래 · ";
            String tail = !open ? statName + " " + n.at() + "부터" : chosen == null ? "하나를 고르세요" : "「" + n.pick().get(chosen).name() + "」 골랐음";
            t.text(30, 71, VanillaGui.fit(head + tail, 138), chosen == null && open ? VanillaGui.GOLD_INK : VanillaGui.INK);
        } else {
            int next = StatTree.nextAt(stat, spent);
            t.text(30, 71, next > 0 ? "다음 칸: " + statName + " " + next : "모든 칸이 열렸습니다", VanillaGui.INK);
        }
    }

    /** 오른쪽에 보일 갈래 칸: 누른 갈래 → 아직 안 고른 열린 갈래 → 첫 갈래 */
    private static int selected(VanillaMenu.MenuGui g, StatTree.Tree tree, int tier, Map<Integer, Integer> picks) {
        if (g.treeSel >= 0 && g.treeSel < tree.nodes().size() && tree.nodes().get(g.treeSel).fork()) return g.treeSel;
        int first = -1;
        for (int i = 0; i < tree.nodes().size(); i++) {
            if (!tree.nodes().get(i).fork()) continue;
            if (first < 0) first = i;
            if (i < tier && !picks.containsKey(i)) return i;
        }
        return first;
    }

    private static void node(VanillaMenu.MenuGui g, String stat, StatTree.Node n, int i, int tier, Map<Integer, Integer> picks, boolean sel) {
        boolean open = i < tier;
        String statName = Stats.info(stat).str("name");
        List<String> lore = new ArrayList<>();
        lore.add("§7" + statName + " " + n.at() + " 이상 " + (open ? "§a열림" : "§8닫힘"));
        ItemStack icon;
        String name;
        if (n.fork()) {
            Integer chosen = picks.get(i);
            name = (i + 1) + "단계 갈래";
            for (int o = 0; o < n.pick().size(); o++) {
                StatTree.Option op = n.pick().get(o);
                lore.add((chosen != null && chosen == o ? "§a▶ " : "§7· ") + op.name() + " §f" + StatTree.bonusText(op.bonus()));
            }
            if (!open) {
                icon = VanillaGui.ui("lock", "§8" + name, lore);
            } else if (chosen == null) {
                lore.add("");
                lore.add("§e눌러서 오른쪽에서 고르기");
                icon = VanillaGui.ui("icon_point", "§e" + name + " §7(고르기)", lore);
                icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            } else {
                StatTree.Option op = n.pick().get(chosen);
                icon = VanillaGui.icon(kindModel(op.bonus()), "§a" + name + " §f「" + op.name() + "」", lore);
            }
            if (sel) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else {
            name = (i + 1) + "단계 " + n.name();
            lore.add("§f" + StatTree.bonusText(n.bonus()));
            if (!n.reward().isEmpty()) lore.add("§6처음 열면 " + StatTree.rewardText(stat, n.reward()));
            icon = open ? VanillaGui.icon(kindModel(n.bonus()), "§a" + name, lore) : VanillaGui.ui("lock", "§8" + name, lore);
        }
        g.set(row(i), col(i), icon, n.fork() ? VanillaMenu.MenuGui.left(() -> {
            g.treeSel = i;
            g.refresh();
        }) : null);
    }

    /** 갈래 하나 (줄 1 · 2 의 칸 6 = 그림, 칸 7 ~ 8 = 이름) */
    private static void option(VanillaMenu.MenuGui g, VanillaGui.Title t, String stat, StatTree.Node n, int index, int o, boolean open, Integer chosen) {
        ServerPlayer sp = g.sp;
        StatTree.Option op = n.pick().get(o);
        boolean mine = chosen != null && chosen == o;
        boolean can = open && chosen == null;
        int r = 1 + o;
        List<String> lore = new ArrayList<>();
        lore.add("§f" + StatTree.bonusText(op.bonus()));
        lore.add("");
        lore.add(mine ? "§a골랐음" : chosen != null ? "§8다른 쪽을 골랐음 (초기화하면 다시)" : open ? "§e눌러서 고르기" : "§8" + Stats.info(stat).str("name") + " " + n.at() + "부터");
        ItemStack icon = VanillaGui.icon(kindModel(op.bonus()), (mine ? "§a▶ " : can ? "§e" : "§7") + op.name(), lore);
        if (mine) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var cb = VanillaMenu.MenuGui.left(() -> {
            if (!can) return;
            String err = StatTrees.pick(sp, stat, index, o);
            if (err != null) warn(sp, err);
            g.refresh();
        });
        g.set(r, 6, icon, cb);
        for (int c = 7; c <= 8; c++) g.set(r, c, VanillaGui.blank((mine ? "§a" : "§f") + op.name(), lore), cb);
        t.text(VanillaGui.cellX(7) + 1, r == 1 ? 35 : 53, VanillaGui.fit(op.name(), 34), mine ? VanillaGui.GREEN : can ? VanillaGui.WHITE : 0xC8B496);
    }

    /** 보너스의 첫 종류 그림 (stat_tree.json kinds.icon — 바닐라 · rpg 아이템 모델) */
    private static Identifier kindModel(Map<String, Double> bonus) {
        for (String k : bonus.keySet()) {
            StatTree.Kind kind = StatTree.kind(k);
            Identifier id = kind == null ? null : Identifier.tryParse(kind.icon());
            if (id != null) return id;
        }
        return Mmorpg.rpg("vui/icon_point");
    }

    private static List<String> totals(ServerPlayer sp) {
        List<String> out = new ArrayList<>();
        Map<String, Double> all = StatTrees.totals(sp);
        for (Map.Entry<String, StatTree.Kind> e : StatTree.kinds().entrySet()) {
            double v = all.getOrDefault(e.getKey(), 0.0);
            if (v != 0) out.add("§7" + e.getValue().name() + " §f" + StatTree.bonusText(e.getKey(), v));
        }
        if (out.isEmpty()) out.add("§8아직 열린 보너스가 없습니다");
        out.add("");
        out.add("§7스탯을 찍으면 그 나무의 칸이 열림 (장비 · 버프 제외)");
        return out;
    }

    private static void warn(ServerPlayer sp, String text) {
        Notify.notify(sp, text, 50);
        SkillFx.playFor(sp, "block.note_block.bass", 0.7, 0.8);
    }
}
