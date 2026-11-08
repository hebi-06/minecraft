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
 * 메뉴의 스탯 트리 쪽 (스탯 쪽 가운데 아래 단추 · 홈 빠른 단추 · StatTrees.open). 3판 (2026-11-08 사용자 그림 "스탯 트리는 이런 느낌으로 하고 싶음
 * 아래에 화살표는 저걸로 아래로 내릴수 있게 만들어 둔거임"):
 * <pre>
 * 스탯 트리                                  힘 126 · 7/10
 *            [1]                                        위 판 (줄 1 ~ 5) = 나무 판: 1단계가 맨 위, 갈래 (3 · 6 · 9단계) 는
 *             │                                         둘로 갈라졌다 다시 모임. 칸 · 선은 글자 (rpg:gt&lt;줄&gt; — 열린 길 = 스탯 색,
 *            [2]                                        고를 수 있는 갈래 = 금, 닫힘 = 어둡게, 고른 칸 = 흰 테)
 *        ┌────┴────┐
 *      [3A]       [3B]                                  ← 보이는 곳은 5줄 (나무는 19줄) — 방향 키 ↑↓ 로 2줄 (한 단계) 씩
 * [그림] 3단계 갈래 · 광전사   │   [↑]                     아래 판 (가방 자리): 설명 카드 (칸 0 ~ 5 · 줄 6 ~ 8 — 고른 칸) · 방향 키
 * 공격력 +4%                   │ [←][↓][→]                 (↑↓ 나무 내리기 · ←→ 다른 나무), 탭 (줄 9 — 힘 · 민첩 · 체력 · 마력 · 합계 Σ,
 * 한 번 더 누르면 고름          │                            고른 탭 = 주황 빛 + 흰 세모), 도움말 (칸 0) · 초기화 (칸 8 — 두 번 눌러)
 * [?]   [힘][민첩][체력][마력][Σ]   [↺]
 *              [ 힘 스탯 트리 ]                         ← 창 밑 글
 * </pre>
 * 칸을 누르면 고름 (설명 카드), 고른 갈래를 한 번 더 누르면 그 갈래를 고름 (StatTrees.pick — 모바일은 마우스를 올려 설명을 볼 수 없어 두 번 누름).
 */
public final class VanillaTree {
    private VanillaTree() {}

    /** 나무 칸 (가운데 · 갈래 왼쪽 · 오른쪽) · 보이는 줄 수 */
    static final int CENTER = 4, LEFT = 2, RIGHT = 6, VIEW = 5;
    /** 합계 탭 (나무 대신) */
    static final String SUM = "sum";
    /** 선 · 칸 테 색 (tools/vanilla_gui.py 미리보기와 같음) */
    static final int AVAIL = 0xFFD678, LDIM = 0x363A60, SEL = 0xFFF4D6, TAB = 0xE8963C;
    /** 초기화 두 번 누름 사이 (틱) */
    private static final int ARM_TICKS = 60;
    /** 설명 카드 글자 자리 */
    private static final int CARD_X = 9, NAME_X = 28, NAME_W = 82, CARD_W = 100;

    /** 스탯마다 나무 색 (바탕 그림 STATS 와 같은 계열) */
    static int statColor(String stat) {
        return switch (stat) {
            case "str" -> 0xE66056;
            case "dex" -> 0x68D678;
            case "vit" -> 0xF080B0;
            case "int" -> 0x689EF6;
            default -> VanillaGui.legacyColor(Stats.info(stat).str("color"), 0xFFFFFF);
        };
    }

    /** 탭 차례: 스탯 나무들 + 합계 */
    static List<String> tabs() {
        List<String> out = new ArrayList<>(StatTree.stats());
        if (out.size() > 4) out = new ArrayList<>(out.subList(0, 4));
        out.add(SUM);
        return out;
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        List<String> stats = StatTree.stats();
        if (stats.isEmpty()) return;
        if (g.treeStat == null || (!SUM.equals(g.treeStat) && StatTree.tree(g.treeStat) == null)) g.treeStat = stats.get(0);
        tabBar(g, t);
        controls(g);
        if (SUM.equals(g.treeStat)) {
            sum(g, t);
            return;
        }
        String stat = g.treeStat;
        StatTree.Tree tree = StatTree.tree(stat);
        double spent = Stats.get(StatTrees.spent(sp), stat);
        int tier = StatTree.tier(stat, spent);
        StatTree.State state = StatTrees.state(sp);
        Map<Integer, Integer> picks = state.picksOf(stat);
        String statName = Stats.info(stat).str("name");
        int n = tree.nodes().size();

        VanillaMenu.title(t, "스탯 트리");
        t.right(166, 0, statName + " " + (int) spent + " · " + tier + "/" + n, VanillaGui.MUTED);
        t.center(88, 223, "[ " + statName + " 스탯 트리 ]", VanillaGui.MUTED);

        int sel = selected(g, tree, tier, picks);
        int maxTop = Math.max(0, rows(n) - VIEW);
        if (g.treeTop < 0) g.treeTop = Math.max(0, Math.min(maxTop, 2 * (sel / 2) - 2)) & ~1;
        g.treeTop = Math.max(0, Math.min(maxTop, g.treeTop));
        int top = g.treeTop;

        // 나무 (선 테두리 → 선 속 → 칸 테 → 칸 아이템)
        int lit = statColor(stat);
        List<Seg> segs = new ArrayList<>();
        for (int i = 0; i + 1 < n; i++) links(tree, i, tier, picks, lit, segs);
        for (Seg s : segs) {
            int r = s.v - top + 1;
            if (r >= 1 && r <= VIEW) t.glyph(VanillaGui.cellX(s.col), r, s.part + "_o", 0xFFFFFF);
        }
        for (Seg s : segs) {
            int r = s.v - top + 1;
            if (r >= 1 && r <= VIEW) t.glyph(VanillaGui.cellX(s.col), r, s.part, s.color);
        }
        for (int i = 0; i < n; i++) {
            int r = 2 * i - top + 1;
            if (r < 1 || r > VIEW) continue;
            StatTree.Node node = tree.nodes().get(i);
            int opts = node.fork() ? Math.min(2, node.pick().size()) : 1;
            for (int o = 0; o < opts; o++) {
                int col = node.fork() ? (o == 0 ? LEFT : RIGHT) : CENTER;
                Look look = look(node, i, o, tier, picks, lit);
                boolean isSel = sel == code(i, o);
                t.glyph(VanillaGui.cellX(col) - 2, r, isSel ? "frame_sel" : "frame", isSel ? SEL : look.color);
                g.set(r, col, nodeIcon(stat, node, i, o, look, tier, picks), nodeClick(g, stat, i, o));
            }
        }
        t.glyph(VanillaGui.THUMB_X, -1, "thumb" + Math.min(VanillaGui.THUMB_STEPS - 1, maxTop == 0 ? 0
                : Math.round((float) top / maxTop * (VanillaGui.THUMB_STEPS - 1))), VanillaGui.GOLD);

        card(g, t, stat, tree, sel, tier, picks);
    }

    /** 나무의 세로 줄 수 (단계 i = 줄 2i, 사이 = 선) */
    static int rows(int nodes) {
        return Math.max(1, 2 * nodes - 1);
    }

    /** 고른 칸 번호 (단계 × 2 + 갈래) */
    static int code(int i, int o) {
        return i * 2 + o;
    }

    /** 설명 카드에 보일 칸: 누른 칸 → 아직 안 고른 열린 갈래 → 다음에 열릴 칸 → 마지막 칸 */
    private static int selected(VanillaMenu.MenuGui g, StatTree.Tree tree, int tier, Map<Integer, Integer> picks) {
        int n = tree.nodes().size();
        if (g.treeSel >= 0 && g.treeSel / 2 < n) {
            StatTree.Node node = tree.nodes().get(g.treeSel / 2);
            if (node.fork() || g.treeSel % 2 == 0) return g.treeSel;
        }
        for (int i = 0; i < n; i++) {
            if (tree.nodes().get(i).fork() && i < tier && !picks.containsKey(i)) return code(i, 0);
        }
        return code(Math.min(tier, n - 1), 0);
    }

    /** 선 반쪽 하나 (세로 줄 v · 칸 · 반쪽 up · down · left · right · 색) */
    private record Seg(int v, int col, String part, int color) {}

    /** 칸 하나의 모습: 색 (테 · 길) · 열림 · 고를 수 있음 · 고른 갈래인지 · 안 고른 쪽 */
    private record Look(int color, boolean open, boolean avail, boolean mine, boolean other) {}

    private static Look look(StatTree.Node node, int i, int o, int tier, Map<Integer, Integer> picks, int lit) {
        boolean open = i < tier;
        if (!node.fork()) return new Look(open ? lit : LDIM, open, false, false, false);
        Integer chosen = picks.get(i);
        boolean mine = chosen != null && chosen == o;
        boolean avail = open && chosen == null;
        boolean other = chosen != null && !mine;
        return new Look(mine ? lit : avail ? AVAIL : LDIM, open, avail, mine, other);
    }

    /** 단계 i → i + 1 사이 선 (줄 2i + 1): 곧은 줄 · 갈라짐 (┌─┴─┐) · 모임 (└─┬─┘) */
    private static void links(StatTree.Tree tree, int i, int tier, Map<Integer, Integer> picks, int lit, List<Seg> out) {
        int v = 2 * i + 1;
        boolean a = tree.nodes().get(i).fork(), b = tree.nodes().get(i + 1).fork();
        boolean openNext = i + 1 < tier;
        if (!a && !b) {
            int c = openNext ? lit : LDIM;
            out.add(new Seg(v, CENTER, "up", c));
            out.add(new Seg(v, CENTER, "down", c));
            return;
        }
        if (b && !a) { // 갈라짐: 위 가운데 → 갈래 둘
            Integer ch = picks.get(i + 1);
            int stem = openNext && ch != null ? lit : openNext ? AVAIL : LDIM;
            int ca = ch != null && ch == 0 ? lit : openNext && ch == null ? AVAIL : LDIM;
            int cb = ch != null && ch == 1 ? lit : openNext && ch == null ? AVAIL : LDIM;
            out.add(new Seg(v, CENTER, "up", stem));
            bar(out, v, LEFT, ca, "down");
            bar(out, v, RIGHT, cb, "down");
            return;
        }
        if (a && !b) { // 모임: 갈래 둘 → 아래 가운데
            Integer ch = picks.get(i);
            int ca = ch != null && ch == 0 ? lit : LDIM;
            int cb = ch != null && ch == 1 ? lit : LDIM;
            bar(out, v, LEFT, ca, "up");
            bar(out, v, RIGHT, cb, "up");
            out.add(new Seg(v, CENTER, "down", openNext ? lit : LDIM));
            return;
        }
        // 갈래 → 갈래 (데이터에는 없음): 같은 쪽끼리 이음
        Integer ch = picks.get(i);
        for (int o = 0; o < 2; o++) {
            int col = o == 0 ? LEFT : RIGHT;
            int c = ch != null && ch == o && openNext ? lit : LDIM;
            out.add(new Seg(v, col, "up", c));
            out.add(new Seg(v, col, "down", c));
        }
    }

    /** 가운데 (칸 4) 에서 갈래 칸 (2 · 6) 까지 가로 막대 + 끝에서 위 · 아래로 꺾음 */
    private static void bar(List<Seg> out, int v, int end, int color, String turn) {
        int dir = end < CENTER ? -1 : 1;
        out.add(new Seg(v, CENTER, dir < 0 ? "left" : "right", color));
        for (int c = CENTER + dir; c != end; c += dir) {
            out.add(new Seg(v, c, "left", color));
            out.add(new Seg(v, c, "right", color));
        }
        out.add(new Seg(v, end, dir < 0 ? "right" : "left", color));
        out.add(new Seg(v, end, turn, color));
    }

    private static String nodeName(StatTree.Node node, int i, int o) {
        return node.fork() ? (i + 1) + "단계 갈래 · " + node.pick().get(o).name() : (i + 1) + "단계 " + node.name();
    }

    private static Map<String, Double> bonusOf(StatTree.Node node, int o) {
        return node.fork() ? node.pick().get(o).bonus() : node.bonus();
    }

    private static ItemStack nodeIcon(String stat, StatTree.Node node, int i, int o, Look look, int tier, Map<Integer, Integer> picks) {
        String statName = Stats.info(stat).str("name");
        List<String> lore = new ArrayList<>();
        lore.add("§f" + StatTree.bonusText(bonusOf(node, o)));
        lore.add("§7" + statName + " " + node.at() + " 이상 " + (look.open ? "§a열림" : "§8닫힘"));
        if (!node.fork() && !node.reward().isEmpty()) lore.add("§6처음 열면 " + StatTree.rewardText(stat, node.reward()));
        if (node.fork()) {
            lore.add(look.mine ? "§a골랐음" : look.other ? "§8다른 갈래를 골랐음 (초기화하면 다시)" : look.avail ? "§e눌러서 보고 한 번 더 눌러 고름" : "§8열리면 둘 중 하나를 고름");
        }
        boolean dim = !look.open || look.other;
        String name = (look.mine || (!node.fork() && look.open) ? "§a" : look.avail ? "§e" : "§8") + nodeName(node, i, o);
        ItemStack icon = VanillaGui.ui(kindIcon(bonusOf(node, o)) + (dim ? "_locked" : ""), name, lore);
        if (look.avail) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return icon;
    }

    private static GuiElement.ClickCallback nodeClick(VanillaMenu.MenuGui g, String stat, int i, int o) {
        return VanillaMenu.MenuGui.left(() -> {
            ServerPlayer sp = g.sp;
            StatTree.Tree tree = StatTree.tree(stat);
            if (tree == null) return;
            StatTree.Node node = tree.nodes().get(i);
            int c = code(i, o);
            double spent = Stats.get(StatTrees.spent(sp), stat);
            boolean avail = node.fork() && i < StatTree.tier(stat, spent) && !StatTrees.state(sp).picksOf(stat).containsKey(i);
            if (g.treeSel == c && avail) { // 고른 갈래를 한 번 더: 고름
                String err = StatTrees.pick(sp, stat, i, o);
                if (err != null) warn(sp, err);
                else SkillFx.playFor(sp, "block.amethyst_block.chime", 1.2, 0.8);
            } else {
                g.treeSel = c;
                SkillFx.playFor(sp, "ui.button.click", 1.9, 0.25);
            }
            g.treeArm = 0;
            g.refresh();
        });
    }

    /** 설명 카드 (칸 0 ~ 5 · 줄 6 ~ 8): 그림 (6, 0) · 이름 · 보너스 · 상태 */
    private static void card(VanillaMenu.MenuGui g, VanillaGui.Title t, String stat, StatTree.Tree tree, int sel, int tier, Map<Integer, Integer> picks) {
        int i = Math.min(sel / 2, tree.nodes().size() - 1);
        StatTree.Node node = tree.nodes().get(i);
        int o = node.fork() ? Math.min(sel % 2, node.pick().size() - 1) : 0;
        Look look = look(node, i, o, tier, picks, statColor(stat));
        g.set(6, 0, nodeIcon(stat, node, i, o, look, tier, picks), null);
        String statName = Stats.info(stat).str("name");
        t.text(NAME_X, 138, VanillaGui.fit(nodeName(node, i, o), NAME_W), look.open ? VanillaGui.GOLD : VanillaGui.MUTED);
        t.text(CARD_X, 156, VanillaGui.fit(StatTree.bonusText(bonusOf(node, o)), CARD_W), look.open && !look.other ? VanillaGui.WHITE : VanillaGui.MUTED);
        String status;
        int color;
        if (armed(g)) {
            status = "한 번 더 누르면 갈래 초기화";
            color = VanillaGui.RED;
        } else if (!look.open) {
            status = statName + " " + node.at() + " 이상에서 " + (node.fork() ? "고름" : "열림");
            color = VanillaGui.MUTED;
        } else if (look.avail) {
            status = sel == g.treeSel ? "한 번 더 누르면 고름" : "눌러서 고르기";
            color = VanillaGui.GOLD;
        } else if (look.mine) {
            status = "골랐음";
            color = VanillaGui.GREEN;
        } else if (look.other) {
            status = "다른 갈래를 골랐음";
            color = VanillaGui.DIM;
        } else if (!node.reward().isEmpty()) {
            status = "열림 · " + StatTree.rewardText(stat, node.reward());
            color = VanillaGui.GREEN;
        } else {
            status = "열림";
            color = VanillaGui.GREEN;
        }
        t.text(CARD_X, 174, VanillaGui.fit(status, CARD_W), color);
    }

    /** 탭 막대 (줄 9 · 칸 2 ~ 6): 스탯 넷 + 합계 — 고른 탭 = 주황 빛 + 흰 세모 */
    private static void tabBar(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        List<String> tabs = tabs();
        StatTree.State state = StatTrees.state(sp);
        for (int k = 0; k < tabs.size() && k < 5; k++) {
            String s = tabs.get(k);
            int col = 2 + k;
            boolean on = s.equals(g.treeStat);
            if (on) {
                t.glyph(VanillaGui.cellX(col), 9, "tab_sel", TAB);
                t.glyph(VanillaGui.cellMid(col) - 2, -1, "tri", 0xFFFFFF);
            }
            List<String> lore = new ArrayList<>();
            ItemStack icon;
            if (SUM.equals(s)) {
                lore.add("§7모든 나무의 보너스 합계");
                lore.add("");
                lore.add(on ? "§e보는 중" : "§a눌러서 보기");
                icon = VanillaGui.ui("sum", "§6스탯 트리 합계", lore);
            } else {
                StatTree.Tree tr = StatTree.tree(s);
                double spent = Stats.get(StatTrees.spent(sp), s);
                int tier = StatTree.tier(s, spent);
                int pend = StatTree.pending(s, spent, state.picksOf(s)).size();
                lore.add("§7" + Stats.info(s).str("name") + " " + (int) spent + " · 열린 칸 " + tier + "/" + tr.nodes().size());
                if (pend > 0) lore.add("§e고를 갈래 " + pend + "개");
                lore.add("");
                lore.add(on ? "§e보는 중" : "§a눌러서 보기");
                icon = VanillaGui.ui("icon_" + s, VanillaStats.colorCode(Stats.info(s).str("color")) + tr.title(), lore);
                if (pend > 0) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            g.set(9, col, icon, VanillaMenu.MenuGui.left(() -> select(g, s)));
        }
    }

    /** 다른 나무 (탭 · ←→) */
    private static void select(VanillaMenu.MenuGui g, String s) {
        if (s.equals(g.treeStat)) return;
        g.treeStat = s;
        g.treeSel = -1;
        g.treeTop = -1;
        g.treeArm = 0;
        SkillFx.playFor(g.sp, "ui.button.click", 1.6, 0.3);
        g.refresh();
    }

    /** 방향 키 (↑↓ 나무 내리기 · ←→ 다른 나무) · 도움말 · 초기화 */
    private static void controls(VanillaMenu.MenuGui g) {
        ServerPlayer sp = g.sp;
        boolean tree = !SUM.equals(g.treeStat);
        g.set(6, 7, VanillaGui.blank("§f▲ 위로", List.of("§7나무를 위로 (한 단계)")), VanillaMenu.MenuGui.left(() -> scroll(g, -2)));
        g.set(7, 7, VanillaGui.blank("§f▼ 아래로", List.of("§7나무를 아래로 (한 단계)")), VanillaMenu.MenuGui.left(() -> scroll(g, 2)));
        g.set(7, 6, VanillaGui.blank("§f◀ 앞 나무", List.of("§7앞 탭의 나무")), VanillaMenu.MenuGui.left(() -> step(g, -1)));
        g.set(7, 8, VanillaGui.blank("§f▶ 다음 나무", List.of("§7다음 탭의 나무")), VanillaMenu.MenuGui.left(() -> step(g, 1)));
        g.set(9, 0, VanillaGui.blank("§b스탯 트리", List.of(
                "§7스탯을 찍으면 그 나무의 칸이 열림 §8(장비 · 버프 제외)",
                "§7칸을 누르면 아래 카드에 설명",
                "§7갈래 (3 · 6 · 9단계): 둘 중 하나를 눌러 보고 §f한 번 더 눌러 고름",
                "§7▲▼ 나무 내리기 · ◀▶ 다른 나무",
                "§7초기화: 고른 갈래를 지움 §8(두 번 누름)")), null);
        if (!tree) {
            g.set(9, 8, VanillaGui.blank("§8갈래 초기화", List.of("§7나무 탭에서")), null);
            return;
        }
        String stat = g.treeStat;
        int picked = StatTrees.state(sp).pickCount(stat);
        List<String> lore = new ArrayList<>();
        lore.add(picked > 0 ? "§7" + Stats.info(stat).str("name") + " 나무의 고른 갈래 " + picked + "개를 지움" : "§8고른 갈래가 없습니다");
        if (picked > 0) lore.add("§7비용 §f" + Inv.CURRENCY_NAME + " " + StatTrees.resetCost(sp, stat));
        lore.add("");
        lore.add("§e두 번 눌러 초기화 §8(실수로 지우지 않게)");
        g.set(9, 8, VanillaGui.blank(picked > 0 ? "§c갈래 초기화" : "§8갈래 초기화", lore), VanillaMenu.MenuGui.left(() -> {
            if (StatTrees.state(sp).pickCount(stat) <= 0) {
                warn(sp, "§7고른 갈래가 없습니다");
                return;
            }
            if (!armed(g)) {
                g.treeArm = sp.level().getServer().getTickCount();
                SkillFx.playFor(sp, "block.note_block.bass", 1.4, 0.6);
                g.refresh();
                return;
            }
            String err = StatTrees.reset(sp, stat, false);
            if (err != null) warn(sp, err);
            g.treeArm = 0;
            g.treeSel = -1;
            g.refresh();
        }));
    }

    private static boolean armed(VanillaMenu.MenuGui g) {
        return g.treeArm > 0 && g.sp.level().getServer().getTickCount() - g.treeArm <= ARM_TICKS;
    }

    private static void scroll(VanillaMenu.MenuGui g, int dv) {
        if (SUM.equals(g.treeStat)) return;
        StatTree.Tree tree = StatTree.tree(g.treeStat);
        if (tree == null) return;
        int maxTop = Math.max(0, rows(tree.nodes().size()) - VIEW);
        int top = Math.max(0, g.treeTop);
        int next = Math.max(0, Math.min(maxTop, top + dv));
        if (next == top) {
            SkillFx.playFor(g.sp, "block.note_block.bass", 0.8, 0.3);
            return;
        }
        g.treeTop = next;
        SkillFx.playFor(g.sp, "ui.button.click", dv < 0 ? 1.9 : 1.6, 0.25);
        g.refresh();
    }

    private static void step(VanillaMenu.MenuGui g, int d) {
        List<String> tabs = tabs();
        int at = Math.max(0, tabs.indexOf(g.treeStat));
        select(g, tabs.get(Math.floorMod(at + d, tabs.size())));
    }

    /** 합계 탭: 나무 판에 보너스 종류 여덟 (두 칸 줄 × 넷) · 설명 카드에 열린 칸 수 */
    private static void sum(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        VanillaMenu.title(t, "스탯 트리 합계");
        t.center(88, 223, "[ 스탯 트리 합계 ]", VanillaGui.MUTED);
        Map<String, Double> all = StatTrees.totals(sp);
        int k = 0;
        for (Map.Entry<String, StatTree.Kind> e : StatTree.kinds().entrySet()) {
            if (k >= 8) break;
            int r = 1 + k % 4, col = k < 4 ? 0 : 5;
            double v = all.getOrDefault(e.getKey(), 0.0);
            boolean has = v != 0;
            String line = e.getValue().name() + " " + (has ? StatTree.bonusText(e.getKey(), v) : "0");
            g.set(r, col, VanillaGui.ui("kind_" + e.getKey() + (has ? "" : "_locked"), (has ? "§f" : "§8") + e.getValue().name(),
                    List.of("§7" + e.getValue().desc())), null);
            t.text(VanillaGui.cellX(col + 1) + 2, VanillaGui.rowLine(r), VanillaGui.fit(line, col == 0 ? 66 : 50), has ? VanillaGui.CREAM : VanillaGui.DIM);
            k++;
        }
        t.text(10, VanillaGui.rowLine(5), "스탯을 찍으면 그 나무의 칸이 열림", VanillaGui.MUTED);
        // 설명 카드: 열린 칸 · 고를 갈래
        int open = 0, total = 0, pend = 0;
        StatTree.State state = StatTrees.state(sp);
        for (String s : StatTree.stats()) {
            double spent = Stats.get(StatTrees.spent(sp), s);
            open += StatTree.tier(s, spent);
            total += StatTree.tree(s).nodes().size();
            pend += StatTree.pending(s, spent, state.picksOf(s)).size();
        }
        g.set(6, 0, VanillaGui.ui("sum", "§6합계", List.of()), null);
        t.text(NAME_X, 138, "스탯 트리 합계", VanillaGui.GOLD);
        t.text(CARD_X, 156, "열린 칸 " + open + "/" + total, VanillaGui.WHITE);
        t.text(CARD_X, 174, pend > 0 ? "고를 갈래 " + pend + "개" : "고를 갈래 없음", pend > 0 ? VanillaGui.GOLD : VanillaGui.MUTED);
    }

    /** 보너스의 첫 종류 그림 (직접 그린 rpg:vui/kind_&lt;종류&gt; — stat_tree.json kinds 의 열쇠) */
    static String kindIcon(Map<String, Double> bonus) {
        for (String k : bonus.keySet()) {
            if (StatTree.kind(k) != null) return "kind_" + k;
        }
        return "fork";
    }

    private static void warn(ServerPlayer sp, String text) {
        Notify.notify(sp, text, 50);
        SkillFx.playFor(sp, "block.note_block.bass", 0.7, 0.8);
    }
}
