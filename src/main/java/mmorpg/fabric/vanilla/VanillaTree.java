package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.elements.GuiElement;
import mmorpg.core.StatTree;
import mmorpg.core.Stats;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.Inv;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 메뉴의 스탯 트리 쪽 (스탯 쪽 가운데 아래 단추 · 홈 빠른 단추 · StatTrees.open). 2판 (사용자 "Claude 가 새로 디자인") — 위로 자라는 나무:
 * <pre>
 * 스탯 트리                                  힘 126 · 7/10
 * [힘]        (9)  ┃  (10)    ┃ 3단계 갈래          줄 1
 * [민첩]      (7)  ┃  (8)     ┃ [A] 광전사          줄 2
 * [체력]      (5)  ┃  (6)     ┃ [B] 강철 피부       줄 3
 * [마력]      (3)  ┃  (4)     ┃ 하나를 고르세요      줄 4
 * [초기화]    (1)  ┃  (2)     ┃ 공격력 +4%    [i]   줄 5
 * </pre>
 * 칸 (줄기 왼쪽 칸 2 · 오른쪽 칸 4, 아래 1 → 위 10): 열림 = 보너스 종류 그림 (직접 그린 그림 — rpg:vui/kind_*), 갈래는 고른 쪽 그림 · 아직 안 골랐으면
 * 빛나는 고르기 그림, 닫힘 = 자물쇠. 칸을 누르면 오른쪽 판에 그 칸 (갈래면 둘 — 눌러서 고름 StatTrees.pick). 초기화 = Shift + 좌클릭 (StatTrees.reset).
 */
public final class VanillaTree {
    private VanillaTree() {}

    /** 칸 i 의 자리 (줄, 칸) — 바탕 그림 (tools/vanilla_gui.py TREE_NODES) 과 같음 */
    static final int[][] NODES = {{5, 2}, {5, 4}, {4, 2}, {4, 4}, {3, 2}, {3, 4}, {2, 2}, {2, 4}, {1, 2}, {1, 4}};
    /** 오른쪽 판 글자 자리 */
    private static final int PX = 102, NAME_X = VanillaGui.cellX(6) + 3;

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

        VanillaMenu.title(t, "스탯 트리");
        t.right(166, 0, statName + " " + (int) spent + " · " + tier + "/" + tree.nodes().size(), VanillaGui.MUTED);

        // 나무 탭 (칸 0 · 줄 1 ~ 4)
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
            g.set(1 + i, 0, icon, VanillaMenu.MenuGui.left(() -> {
                if (s.equals(g.treeStat)) return;
                g.treeStat = s;
                g.treeSel = -1;
                g.refresh();
            }));
        }
        // 초기화 (칸 0 · 줄 5 — Shift + 좌클릭)
        int picked = state.pickCount(stat);
        List<String> resetLore = new ArrayList<>();
        resetLore.add(picked > 0 ? "§7" + statName + " 나무의 고른 갈래 " + picked + "개를 지움" : "§8고른 갈래가 없습니다");
        if (picked > 0) resetLore.add("§7비용 §f" + Inv.CURRENCY_NAME + " " + StatTrees.resetCost(sp, stat));
        resetLore.add("");
        resetLore.add("§eShift + 좌클릭으로 초기화 §8(실수로 지우지 않게)");
        g.set(5, 0, VanillaGui.blank(picked > 0 ? "§c갈래 초기화" : "§8갈래 초기화", resetLore), (ix, type, a, gg) -> {
            if (!type.isLeft || !type.shift) return;
            String err = StatTrees.reset(sp, stat, false);
            if (err != null) warn(sp, err);
            g.treeSel = -1;
            g.refresh();
        });
        g.set(5, 8, VanillaGui.blank("§a스탯 트리 합계", totals(sp)), null);

        // 칸 열 개 (줄기 양쪽)
        int sel = selected(g, tree, tier, picks);
        for (int i = 0; i < tree.nodes().size() && i < NODES.length; i++) node(g, stat, tree.nodes().get(i), i, tier, picks, i == sel);
        if (sel >= 0) panel(g, t, stat, tree.nodes().get(sel), sel, tier, picks);
    }

    /** 오른쪽 판에 보일 칸: 누른 칸 → 아직 안 고른 열린 갈래 → 다음에 열릴 칸 → 마지막 칸 */
    private static int selected(VanillaMenu.MenuGui g, StatTree.Tree tree, int tier, Map<Integer, Integer> picks) {
        int n = tree.nodes().size();
        if (g.treeSel >= 0 && g.treeSel < n) return g.treeSel;
        for (int i = 0; i < n; i++) {
            if (tree.nodes().get(i).fork() && i < tier && !picks.containsKey(i)) return i;
        }
        return Math.min(tier, n - 1);
    }

    private static void node(VanillaMenu.MenuGui g, String stat, StatTree.Node n, int i, int tier, Map<Integer, Integer> picks, boolean sel) {
        boolean open = i < tier;
        String statName = Stats.info(stat).str("name");
        List<String> lore = new ArrayList<>();
        lore.add("§7" + statName + " " + n.at() + " 이상 " + (open ? "§a열림" : "§8닫힘"));
        ItemStack icon;
        if (n.fork()) {
            Integer chosen = picks.get(i);
            String name = (i + 1) + "단계 갈래";
            for (int o = 0; o < n.pick().size(); o++) {
                StatTree.Option op = n.pick().get(o);
                lore.add((chosen != null && chosen == o ? "§a▶ " : "§7· ") + op.name() + " §f" + StatTree.bonusText(op.bonus()));
            }
            if (!open) {
                icon = VanillaGui.ui("lock", "§8" + name, lore);
            } else if (chosen == null) {
                lore.add("");
                lore.add("§e오른쪽에서 고르기");
                icon = VanillaGui.ui("fork", "§e" + name + " §7(고르기)", lore);
                icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            } else {
                StatTree.Option op = n.pick().get(chosen);
                icon = VanillaGui.ui(kindIcon(op.bonus()), "§a" + name + " §f「" + op.name() + "」", lore);
            }
        } else {
            String name = (i + 1) + "단계 " + n.name();
            lore.add("§f" + StatTree.bonusText(n.bonus()));
            if (!n.reward().isEmpty()) lore.add("§6처음 열면 " + StatTree.rewardText(stat, n.reward()));
            icon = open ? VanillaGui.ui(kindIcon(n.bonus()), "§a" + name, lore) : VanillaGui.ui("lock", "§8" + name, lore);
        }
        if (sel) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        g.set(NODES[i][0], NODES[i][1], icon, VanillaMenu.MenuGui.left(() -> {
            g.treeSel = i;
            g.refresh();
        }));
    }

    /** 오른쪽 판 (칸 5 ~ 8 · 줄 1 ~ 5): 고른 칸의 이름 · 갈래 둘 (또는 보너스 · 조건) · 상태 */
    private static void panel(VanillaMenu.MenuGui g, VanillaGui.Title t, String stat, StatTree.Node n, int index, int tier, Map<Integer, Integer> picks) {
        boolean open = index < tier;
        String statName = Stats.info(stat).str("name");
        if (n.fork()) {
            Integer chosen = picks.get(index);
            t.text(PX, VanillaGui.rowLine(1), VanillaGui.fit((index + 1) + "단계 갈래", 64), VanillaGui.GOLD);
            for (int o = 0; o < n.pick().size() && o < 2; o++) option(g, t, stat, n, index, o, open, chosen);
            String status = !open ? statName + " " + n.at() + "부터" : chosen == null ? "하나를 고르세요" : "「" + n.pick().get(chosen).name() + "」";
            t.text(PX, VanillaGui.rowLine(4), VanillaGui.fit(status, 64), chosen == null && open ? VanillaGui.GOLD : open ? VanillaGui.GREEN : VanillaGui.DIM);
            if (chosen != null) t.text(PX, VanillaGui.rowLine(5), VanillaGui.fit(StatTree.bonusText(n.pick().get(chosen).bonus()), 44), VanillaGui.MUTED);
            return;
        }
        t.text(PX, VanillaGui.rowLine(1), VanillaGui.fit((index + 1) + "단계 " + n.name(), 64), open ? VanillaGui.GOLD : VanillaGui.MUTED);
        g.set(2, 5, VanillaGui.ui(kindIcon(n.bonus()), "§f" + StatTree.bonusText(n.bonus()), List.of()), null);
        t.text(NAME_X, VanillaGui.rowLine(2), VanillaGui.fit(StatTree.bonusText(n.bonus()), 46), VanillaGui.WHITE);
        g.set(3, 5, open ? VanillaGui.blank("§a열림", List.of()) : VanillaGui.ui("lock", "§8닫힘", List.of("§7" + statName + " " + n.at() + " 이상")), null);
        t.text(NAME_X, VanillaGui.rowLine(3), VanillaGui.fit(statName + " " + n.at() + " 이상", 46), open ? VanillaGui.GREEN : VanillaGui.MUTED);
        if (!n.reward().isEmpty()) t.text(PX, VanillaGui.rowLine(4), VanillaGui.fit("보상 " + StatTree.rewardText(stat, n.reward()), 64), VanillaGui.GOLD);
        t.text(PX, VanillaGui.rowLine(5), open ? "열림" : "닫힘", open ? VanillaGui.GREEN : VanillaGui.DIM);
    }

    /** 갈래 하나 (줄 2 · 3 — 칸 5 = 그림, 칸 6 ~ 8 = 이름) */
    private static void option(VanillaMenu.MenuGui g, VanillaGui.Title t, String stat, StatTree.Node n, int index, int o, boolean open, Integer chosen) {
        ServerPlayer sp = g.sp;
        StatTree.Option op = n.pick().get(o);
        boolean mine = chosen != null && chosen == o;
        boolean can = open && chosen == null;
        int r = 2 + o;
        List<String> lore = new ArrayList<>();
        lore.add("§f" + StatTree.bonusText(op.bonus()));
        lore.add("");
        lore.add(mine ? "§a골랐음" : chosen != null ? "§8다른 쪽을 골랐음 (초기화하면 다시)" : open ? "§e눌러서 고르기" : "§8" + Stats.info(stat).str("name") + " " + n.at() + "부터");
        ItemStack icon = VanillaGui.ui(kindIcon(op.bonus()), (mine ? "§a▶ " : can ? "§e" : "§7") + op.name(), lore);
        if (mine) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        GuiElement.ClickCallback cb = VanillaMenu.MenuGui.left(() -> {
            if (!can) return;
            String err = StatTrees.pick(sp, stat, index, o);
            if (err != null) warn(sp, err);
            g.refresh();
        });
        g.set(r, 5, icon, cb);
        for (int c = 6; c <= 8; c++) g.set(r, c, VanillaGui.blank((mine ? "§a" : "§f") + op.name(), lore), cb);
        t.text(NAME_X, VanillaGui.rowLine(r), VanillaGui.fit(op.name(), 46), mine ? VanillaGui.GREEN : can ? VanillaGui.WHITE : VanillaGui.MUTED);
    }

    /** 보너스의 첫 종류 그림 (직접 그린 rpg:vui/kind_&lt;종류&gt; — stat_tree.json kinds 의 열쇠) */
    static String kindIcon(Map<String, Double> bonus) {
        for (String k : bonus.keySet()) {
            if (StatTree.kind(k) != null) return "kind_" + k;
        }
        return "fork";
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
