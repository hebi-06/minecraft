package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mmorpg.core.Js;
import mmorpg.core.StatTree;
import mmorpg.core.Stats;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 스탯 트리 창 (7단계): 위 = 나무 4개 탭 (힘 · 민첩 · 마력 · 체력 — 그 스탯 아이콘 · 단계), 가운데 = 고른 나무 (왼쪽 뿌리에서 오른쪽으로 자람: 줄기 ·
 * 3 · 6 · 9단계는 위아래 두 갈래 · 칸마다 보너스 그림 · 뽑기권 보상 표시 · 스탯 값까지 차오르는 줄기 · 흐르는 빛), 아래 = 마우스를 올린 칸의 설명
 * (아니면 나무 요약), 단추 = 스탯 창으로 · 갈래 초기화 (에메랄드). 갈래는 눌러서 고름 (확인 창). 서버가 보낸 기록 · 보너스 합은 ClientRpgState.
 */
public final class StatTreeScreen extends Screen {
    public static final int W = 330;
    public static final int H = 234;
    static final int TAB_Y = 6;
    static final int TAB_W = 60;
    static final int TAB_H = 22;
    static final int TAB_GAP = 3;
    /** 줄기 높이 · 갈래 위아래 거리 · 칸 크기 · 칸 사이 (창 위에서 · 픽셀) */
    static final int CY = 85;
    static final int FORK_DY = 22;
    static final int NODE = 20;
    static final int X0 = 50;
    static final int DX = 28;
    static final int ROOT_X = 22;
    static final int PANEL_Y = 131;
    static final int BTN_Y = 207;
    static final int BTN_H = 20;
    static final int BACK_W = 74;
    static final int RESET_W = 130;
    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier BTN = Mmorpg.id("stats/dialog_btn");
    private static final Identifier BTN_HOVER = Mmorpg.id("stats/dialog_btn_hover");
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private int left;
    private int top;
    private String tab;
    /** 확인 창 (갈래 고르기 · 초기화 — 없으면 null) */
    private Confirm confirm;
    /** 반짝임 (새로 열린 칸 · 고른 갈래): "스탯|칸|갈래" → 시각 */
    private final Map<String, Long> flash = new HashMap<>();
    private final Map<String, Integer> lastTier = new HashMap<>();
    private StatTree.State lastState;

    private record Confirm(String action, String stat, int index, int option, List<String> lines, String yes) {}

    public StatTreeScreen(String stat) {
        super(Component.literal("스탯 트리"));
        this.tab = StatTree.tree(stat) != null ? stat : StatTree.stats().get(0);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(0, (height - H) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 탭 바꾸기 (서버가 연 탭 · 시험) */
    public void select(String stat) {
        if (StatTree.tree(stat) != null) {
            tab = stat;
            confirm = null;
        }
    }

    public String tab() {
        return tab;
    }

    // ------------------------------------------------------------ 자리 (시험용 — 화면 좌표)

    int tabX(int i) {
        return left + W - 10 - StatTree.stats().size() * TAB_W - (StatTree.stats().size() - 1) * TAB_GAP + i * (TAB_W + TAB_GAP);
    }

    /** 탭 i 의 가운데 */
    public int[] tabCenter(int i) {
        return new int[] {tabX(i) + TAB_W / 2, top + TAB_Y + TAB_H / 2};
    }

    int nodeX(int index) {
        return left + X0 + index * DX;
    }

    /** 칸의 가운데 (option: -1 = 줄기 칸, 0 = 위 갈래, 1 = 아래 갈래) */
    public int[] nodeCenter(int index, int option) {
        int y = top + CY + (option < 0 ? 0 : option == 0 ? -FORK_DY : FORK_DY);
        return new int[] {nodeX(index), y};
    }

    public int[] backCenter() {
        return new int[] {left + 12 + BACK_W / 2, top + BTN_Y + BTN_H / 2};
    }

    public int[] resetCenter() {
        return new int[] {left + W - 12 - RESET_W / 2, top + BTN_Y + BTN_H / 2};
    }

    /** 확인 창 (가운데 상자 — x, y, 너비, 높이) */
    private int[] confirmBox() {
        int bw = 250;
        int bh = 84;
        return new int[] {left + (W - bw) / 2, top + (H - bh) / 2 - 6, bw, bh};
    }

    static final int CONFIRM_BTN_W = 80;
    static final int CONFIRM_BTN_H = 18;

    /** 확인 창 단추 (yes = 고르기 · 초기화, 아니면 취소) */
    public int[] confirmCenter(boolean yes) {
        int[] b = confirmBox();
        int cx = b[0] + b[2] / 2;
        return new int[] {yes ? cx - 48 : cx + 48, b[1] + b[3] - 16};
    }

    private boolean inConfirmBtn(double mx, double my, boolean yes) {
        int[] c = confirmCenter(yes);
        return Math.abs(mx - c[0]) < CONFIRM_BTN_W / 2.0 && Math.abs(my - c[1]) < CONFIRM_BTN_H / 2.0;
    }

    public boolean confirming() {
        return confirm != null;
    }

    // ------------------------------------------------------------ 그리기 도움

    private static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id, k -> {
            try {
                Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(k));
                return it == null || it == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(it);
            } catch (RuntimeException e) {
                return new ItemStack(Items.BARRIER);
            }
        });
    }

    /** 보너스 종류의 그림 (첫 보너스) */
    private static ItemStack kindIcon(Map<String, Double> bonus) {
        for (String k : bonus.keySet()) {
            StatTree.Kind kind = StatTree.kind(k);
            if (kind != null) return icon(kind.icon());
        }
        return new ItemStack(Items.OAK_SAPLING);
    }

    private static int rgb(double[] c, int alpha) {
        int r = (int) Math.round(Math.max(0, Math.min(1, c[0])) * 255);
        int gg = (int) Math.round(Math.max(0, Math.min(1, c[1])) * 255);
        int b = (int) Math.round(Math.max(0, Math.min(1, c[2])) * 255);
        return (Math.max(0, Math.min(255, alpha)) << 24) | (r << 16) | (gg << 8) | b;
    }

    /** 굵기 t 의 선 (작은 네모를 이어 그림 — 비스듬한 가지) */
    private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int t, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        int h = t / 2;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (int) Math.round((double) (x2 - x1) * i / Math.max(1, steps));
            int y = y1 + (int) Math.round((double) (y2 - y1) * i / Math.max(1, steps));
            g.fill(x - h, y - h, x - h + t, y - h + t, color);
        }
    }

    /** 선 위의 비율 f 자리 */
    private static int[] lerp(int x1, int y1, int x2, int y2, double f) {
        return new int[] {(int) Math.round(x1 + (x2 - x1) * f), (int) Math.round(y1 + (y2 - y1) * f)};
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y) {
        g.text(font, BedrockText.toJava(s), x, y, 0xFFFFFFFF, true);
    }

    /** 너비 max 에 맞게 자름 (넘치면 …) */
    private String fit(String s, int max) {
        String j = BedrockText.toJava(s);
        if (font.width(j) <= max) return s;
        String cut = font.plainSubstrByWidth(j, max - font.width("…"));
        return cut + "…";
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** 받침이 있으면 "을", 없으면 "를" (힘을 · 민첩을 …) */
    static String eul(String word) {
        if (word == null || word.isEmpty()) return "을";
        char c = word.charAt(word.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return "을(를)";
        return (c - 0xAC00) % 28 == 0 ? "를" : "을";
    }

    /** 긴 줄은 " · " 에서 나눠 여러 줄로 (너비 max) */
    private List<String> wrap(String line, int max) {
        List<String> out = new ArrayList<>();
        if (font.width(BedrockText.toJava(line)) <= max) {
            out.add(line);
            return out;
        }
        String[] parts = line.split(" · ");
        StringBuilder cur = new StringBuilder();
        String color = "";
        for (String part : parts) {
            String next = cur.length() == 0 ? part : cur + " · " + part;
            if (cur.length() > 0 && font.width(BedrockText.toJava(next)) > max) {
                out.add(cur.toString());
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(§[0-9a-fk-or])+").matcher(cur);
                while (m.find()) color = m.group();
                cur = new StringBuilder("   " + color + part);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private void deny() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.7F));
    }

    // ------------------------------------------------------------ 상태

    private static int spent(String stat) {
        return ClientRpgState.spent(stat);
    }

    private static StatTree.State state() {
        return ClientRpgState.treeState();
    }

    /** 바뀐 것 (새로 열린 칸 · 새로 고른 갈래) 을 찾아 반짝임 */
    private void watch(long now) {
        StatTree.State s = state();
        for (String stat : StatTree.stats()) {
            int t = StatTree.tier(stat, spent(stat));
            Integer before = lastTier.put(stat, t);
            if (before != null && t > before) for (int i = before; i < t; i++) flash.put(stat + "|" + i + "|-1", now);
            if (lastState != null) {
                for (Map.Entry<Integer, Integer> e : s.picksOf(stat).entrySet()) {
                    Integer was = lastState.picksOf(stat).get(e.getKey());
                    if (was == null || !was.equals(e.getValue())) flash.put(stat + "|" + e.getKey() + "|" + e.getValue(), now);
                }
            }
        }
        lastState = s;
    }

    // ------------------------------------------------------------ 그리기

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        if (minecraft.player == null) return;
        long now = Util.getMillis();
        watch(now);
        RpgHud.sprite(g, BG, left, top, W, H);
        text(g, "§6§l스탯 트리", left + 12, top + 11);
        List<String> stats = StatTree.stats();
        // 탭
        int tabHover = -1;
        for (int i = 0; i < stats.size(); i++) {
            String stat = stats.get(i);
            StatTree.Tree t = StatTree.tree(stat);
            int x = tabX(i);
            int y = top + TAB_Y;
            boolean sel = stat.equals(tab);
            boolean over = confirm == null && mx >= x && mx < x + TAB_W && my >= y && my < y + TAB_H;
            if (over) tabHover = i;
            g.fill(x, y, x + TAB_W, y + TAB_H, sel ? 0x58FFFFFF : over ? 0x40FFFFFF : 0x30000000);
            if (sel) g.fill(x, y + TAB_H - 2, x + TAB_W, y + TAB_H, rgb(t.color(), 255));
            RpgHud.sprite(g, Mmorpg.id("stats/icon_" + stat), x + 3, y + 3, 16, 16);
            text(g, Stats.info(stat).str("color") + (sel ? "§l" : "") + Stats.info(stat).str("name"), x + 22, y + 3);
            int tier = StatTree.tier(stat, spent(stat));
            text(g, "§f" + tier + "§7/" + t.nodes().size(), x + 22, y + 12);
            if (!StatTree.pending(stat, spent(stat), state().picksOf(stat)).isEmpty() && (now / 400) % 2 == 0) {
                g.fill(x + TAB_W - 9, y + 2, x + TAB_W - 3, y + 10, 0xFFFFD24A);
                text(g, "§0§l!", x + TAB_W - 8, y + 2);
            }
        }
        g.fill(left + 10, top + 30, left + W - 10, top + 31, 0x90C8A050);

        StatTree.Tree tree = StatTree.tree(tab);
        int value = spent(tab);
        int tier = StatTree.tier(tab, value);
        int next = StatTree.nextAt(tab, value);
        Map<Integer, Integer> picks = state().picksOf(tab);
        String sname = Stats.info(tab).str("name");
        String scol = Stats.info(tab).str("color");
        text(g, scol + "§l" + tree.title() + " §r§7" + sname + " §f" + value + (next > 0 ? "§7 · 다음 단계 " + sname + " " + next : "§6 · 다 자람"), left + 12, top + 35);
        String steps = "§f§l" + tier + "§r§7 / " + tree.nodes().size() + " 단계";
        text(g, steps, left + W - 12 - font.width(BedrockText.toJava(steps)), top + 35);

        // 나무 자리 (위는 그 스탯 색이 옅게 비치는 하늘, 아래는 땅)
        int ax0 = left + 10;
        int ax1 = left + W - 10;
        int ay0 = top + 46;
        int ay1 = top + 128;
        g.fillGradient(ax0, ay0, ax1, ay1, rgb(tree.color(), 0x26), 0x40000000);
        g.fill(ax0, ay1 - 1, ax1, ay1, rgb(tree.color(), 0x50));

        int cy = top + CY;
        int rootX = left + ROOT_X;
        double[] col = tree.color();
        int grown = rgb(col, 255);
        int glow = rgb(col, 70);
        int dead = 0xFF3B3326;
        // 줄기 · 가지: 칸 i 로 가는 선은 이전 칸 (또는 뿌리) 에서
        for (int i = 0; i < tree.nodes().size(); i++) {
            StatTree.Node n = tree.nodes().get(i);
            int prevAt = i == 0 ? 0 : tree.nodes().get(i - 1).at();
            double fill = value >= n.at() ? 1 : value <= prevAt ? 0 : (double) (value - prevAt) / (n.at() - prevAt);
            int x2 = nodeX(i);
            List<int[]> froms = new ArrayList<>();
            if (i == 0) froms.add(new int[] {rootX + 9, cy, -1});
            else {
                StatTree.Node p = tree.nodes().get(i - 1);
                if (p.fork()) {
                    froms.add(new int[] {nodeX(i - 1) + NODE / 2, cy - FORK_DY, 0});
                    froms.add(new int[] {nodeX(i - 1) + NODE / 2, cy + FORK_DY, 1});
                } else froms.add(new int[] {nodeX(i - 1) + NODE / 2, cy, -1});
            }
            List<int[]> tos = new ArrayList<>();
            if (n.fork()) {
                tos.add(new int[] {x2 - NODE / 2, cy - FORK_DY, 0});
                tos.add(new int[] {x2 - NODE / 2, cy + FORK_DY, 1});
            } else tos.add(new int[] {x2 - NODE / 2, cy, -1});
            for (int[] a : froms) {
                for (int[] b : tos) {
                    if (froms.size() > 1 && tos.size() > 1) continue; // 갈래 다음 갈래는 없음
                    line(g, a[0], a[1], b[0], b[1], 2, dead);
                    // 자란 줄기: 고른 갈래가 있으면 그 갈래 쪽만, 아직 안 골랐으면 두 쪽 다 밝게
                    boolean fromOk = a[2] < 0 || !picks.containsKey(i - 1) || picks.get(i - 1) == a[2];
                    boolean toOk = b[2] < 0 || !picks.containsKey(i) || picks.get(i) == b[2];
                    if (fill <= 0 || !fromOk || !toOk) continue;
                    int[] e = lerp(a[0], a[1], b[0], b[1], fill);
                    line(g, a[0], a[1], e[0], e[1], 5, glow);
                    line(g, a[0], a[1], e[0], e[1], 3, grown);
                    // 흐르는 빛 (자란 줄기 위)
                    double f = ((now / 900.0) + i * 0.37 + (b[2] < 0 ? 0 : b[2] * 0.5)) % 1.0;
                    if (f <= fill) {
                        int[] d = lerp(a[0], a[1], b[0], b[1], f);
                        g.fill(d[0] - 1, d[1] - 1, d[0] + 2, d[1] + 2, 0xFFFFFFE0);
                    }
                }
            }
        }
        // 뿌리 (스탯 아이콘 + 빛)
        int pulse = (int) (90 + 60 * Math.sin(now / 300.0));
        g.fill(rootX - 11, cy - 11, rootX + 11, cy + 11, rgb(col, pulse / 2));
        RpgHud.sprite(g, Mmorpg.id("stats/icon_" + tab), rootX - 8, cy - 8, 16, 16);

        // 칸
        int hoverIndex = -1;
        int hoverOption = -2;
        for (int i = 0; i < tree.nodes().size(); i++) {
            StatTree.Node n = tree.nodes().get(i);
            boolean open = value >= n.at();
            if (n.fork()) {
                for (int o = 0; o < n.pick().size(); o++) {
                    int[] c = nodeCenter(i, o);
                    Integer chosen = picks.get(i);
                    int state = !open ? 0 : chosen == null ? 2 : chosen == o ? 3 : 4; // 0 잠김 · 2 고를 수 있음 · 3 고름 · 4 안 고름
                    drawNode(g, c[0], c[1], kindIcon(n.pick().get(o).bonus()), state, col, now, false, Map.of(), false, tab + "|" + i + "|" + o);
                    if (confirm == null && Math.abs(mx - c[0]) <= NODE / 2 && Math.abs(my - c[1]) <= NODE / 2) {
                        hoverIndex = i;
                        hoverOption = o;
                    }
                }
            } else {
                int[] c = nodeCenter(i, -1);
                boolean last = i == tree.nodes().size() - 1;
                boolean claimed = state().claimedOf(tab) > i;
                drawNode(g, c[0], c[1], kindIcon(n.bonus()), open ? 1 : 0, col, now, last, n.reward(), claimed, tab + "|" + i + "|-1");
                if (confirm == null && Math.abs(mx - c[0]) <= NODE / 2 && Math.abs(my - c[1]) <= NODE / 2) {
                    hoverIndex = i;
                    hoverOption = -1;
                }
            }
            // 단계 값 (아래)
            String lab = (open ? "§7" : "§8") + n.at();
            text(g, lab, nodeX(i) - font.width(BedrockText.toJava(lab)) / 2, top + CY + FORK_DY + 11);
        }
        if (hoverIndex >= 0) {
            int[] c = nodeCenter(hoverIndex, hoverOption);
            g.outline(c[0] - NODE / 2 - 2, c[1] - NODE / 2 - 2, NODE + 4, NODE + 4, 0xFFFFFFFF);
        }

        // 설명 (마우스를 올린 칸, 아니면 나무 요약)
        g.fill(left + 10, top + PANEL_Y - 2, left + W - 10, top + BTN_Y - 4, 0x48000000);
        List<String> raw = hoverIndex >= 0 ? nodeLines(tree, hoverIndex, hoverOption, value, picks) : summaryLines(tree, value, picks);
        List<String> lines = new ArrayList<>();
        for (String l : raw) lines.addAll(wrap(l, W - 28));
        for (int k = 0; k < Math.min(7, lines.size()); k++) text(g, fit(lines.get(k), W - 28), left + 14, top + PANEL_Y + 2 + k * 10);

        // 단추: 스탯 창으로 · 갈래 초기화
        boolean overBack = confirm == null && inBtn(mx, my, left + 12, BACK_W);
        RpgHud.sprite(g, overBack ? BTN_HOVER : BTN, left + 12, top + BTN_Y, BACK_W, BTN_H);
        String back = "§f◀ 스탯 창";
        text(g, back, left + 12 + (BACK_W - font.width(BedrockText.toJava(back))) / 2, top + BTN_Y + 6);
        int nPicks = picks.size();
        int cost = StatTree.resetCost(nPicks);
        boolean canReset = nPicks > 0;
        int rx = left + W - 12 - RESET_W;
        boolean overReset = confirm == null && inBtn(mx, my, rx, RESET_W);
        RpgHud.sprite(g, overReset && canReset ? BTN_HOVER : BTN, rx, top + BTN_Y, RESET_W, BTN_H);
        String rs = canReset ? "§c갈래 초기화 §7· §a에메랄드 " + cost : "§8갈래 초기화";
        g.item(new ItemStack(Items.EMERALD), rx + 4, top + BTN_Y + 2);
        text(g, rs, rx + 22, top + BTN_Y + 6);

        if (confirm != null) drawConfirm(g, mx, my);
        else if (tabHover >= 0) {
            String stat = stats.get(tabHover);
            StatTree.Tree t = StatTree.tree(stat);
            int v = spent(stat);
            int nx = StatTree.nextAt(stat, v);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(BedrockText.toJava(Stats.info(stat).str("color") + "§l" + Stats.info(stat).str("name") + " 나무 §r§7(" + t.title() + ")")));
            tip.add(Component.literal(BedrockText.toJava("§7" + StatTree.tier(stat, v) + "/" + t.nodes().size() + " 단계 · " + (nx > 0 ? "다음 단계 " + Stats.info(stat).str("name") + " " + nx : "다 자람"))));
            String b = StatTree.bonusText(StatTree.treeTotals(stat, v, state().picksOf(stat)));
            tip.add(Component.literal(BedrockText.toJava(b.isEmpty() ? "§8보너스 없음" : "§a" + b)));
            g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
        } else if (overReset) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(BedrockText.toJava("§c§l갈래 초기화 §r§7(" + tree.title() + ")")));
            tip.add(Component.literal(BedrockText.toJava("§7이 나무에서 고른 갈래를 모두 지웁니다 (다시 고를 수 있음)")));
            tip.add(Component.literal(BedrockText.toJava("§7갈래 1개당 에메랄드 " + StatTree.perPick() + " · 지금 " + nPicks + "개 → §a" + cost)));
            tip.add(Component.literal(BedrockText.toJava("§7가진 에메랄드 " + ClientRpgState.money() + " · 받은 뽑기권은 그대로")));
            g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
        }
    }

    private boolean inBtn(double mx, double my, int x, int w) {
        return mx >= x && mx < x + w && my >= top + BTN_Y && my < top + BTN_Y + BTN_H;
    }

    /**
     * 칸 하나: state 0 = 잠김 (어둡게), 1 = 열림 (그 스탯 색 테두리 · 숨쉬는 빛), 2 = 고를 수 있는 갈래 (노랑 깜빡임), 3 = 고른 갈래 (금빛), 4 = 고르지 않은 갈래.
     * 보상이 있으면 오른쪽 위에 뽑기권 (받았으면 초록 점), 마지막 칸은 금테 한 겹 더, 새로 열리거나 고른 칸은 퍼지는 고리.
     */
    private void drawNode(GuiGraphicsExtractor g, int cx, int cy, ItemStack icon, int state, double[] col, long now, boolean capstone, Map<String, Integer> reward,
            boolean claimed, String key) {
        int x0 = cx - NODE / 2;
        int y0 = cy - NODE / 2;
        int breathe = (int) (70 + 50 * Math.sin(now / 320.0 + cx * 0.05));
        if (state == 1 || state == 3) g.fill(x0 - 2, y0 - 2, x0 + NODE + 2, y0 + NODE + 2, state == 3 ? (breathe << 24) | 0xFFD24A : rgb(col, breathe));
        if (state == 2) {
            int a = (now / 300) % 2 == 0 ? 0xFF : 0x70;
            g.fill(x0 - 2, y0 - 2, x0 + NODE + 2, y0 + NODE + 2, (a << 24) | 0xFFE066);
        }
        g.fill(x0, y0, x0 + NODE, y0 + NODE, state == 0 || state == 4 ? 0xE0121216 : 0xF0241C14);
        int border = switch (state) {
            case 1 -> rgb(col, 255);
            case 2 -> 0xFFFFE066;
            case 3 -> 0xFFFFD24A;
            default -> 0xFF4A4A4A;
        };
        g.outline(x0, y0, NODE, NODE, border);
        if (capstone) g.outline(x0 - 3, y0 - 3, NODE + 6, NODE + 6, state == 1 ? 0xFFFFD24A : 0xFF5A5040);
        g.item(icon, cx - 8, cy - 8);
        if (state == 0 || state == 4) g.fill(x0 + 1, y0 + 1, x0 + NODE - 1, y0 + NODE - 1, 0xA0101014);
        if (state == 2) text(g, "§e§l?", cx - 2, y0 - 10);
        if (!reward.isEmpty()) {
            // 보상 뽑기권 (작게) — 받았으면 초록 점
            String first = reward.keySet().iterator().next();
            String item = StatTree.rewardItem(tab, first);
            if (item != null) {
                g.pose().pushMatrix();
                g.pose().translate(x0 + NODE - 6, y0 - 4);
                g.pose().scale(0.6F, 0.6F);
                g.item(icon(item), 0, 0);
                g.pose().popMatrix();
            }
            if (claimed) g.fill(x0 + NODE - 1, y0 + NODE - 4, x0 + NODE + 2, y0 + NODE - 1, 0xFF55FF55);
        }
        Long f = flash.get(key);
        if (f != null && now - f < 900) {
            double k = (now - f) / 900.0;
            int r = (int) (NODE / 2 + 4 + 14 * k);
            int a = (int) (255 * (1 - k));
            int c = state == 3 ? (a << 24) | 0xFFD24A : rgb(col, a);
            g.outline(cx - r, cy - r, r * 2, r * 2, c);
            g.outline(cx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, c);
        }
    }

    /** 마우스를 올린 칸의 설명 */
    private List<String> nodeLines(StatTree.Tree tree, int index, int option, int value, Map<Integer, Integer> picks) {
        List<String> out = new ArrayList<>();
        StatTree.Node n = tree.nodes().get(index);
        String sname = Stats.info(tree.stat()).str("name");
        String scol = Stats.info(tree.stat()).str("color");
        boolean open = value >= n.at();
        if (n.fork()) {
            StatTree.Option o = n.pick().get(option);
            StatTree.Option other = n.pick().get(1 - option);
            out.add(scol + "§l" + (index + 1) + "단계 갈래 §r§7(" + sname + " " + n.at() + ") — 둘 중 하나");
            out.add("§e「" + o.name() + "」 §a" + StatTree.bonusText(o.bonus()));
            out.add("§7다른 갈래: 「" + other.name() + "」 " + StatTree.bonusText(other.bonus()));
            for (String k : o.bonus().keySet()) {
                StatTree.Kind kind = StatTree.kind(k);
                if (kind != null && !kind.desc().isEmpty()) out.add("§8" + kind.name() + ": " + kind.desc());
            }
            Integer chosen = picks.get(index);
            if (!open) out.add("§c잠김 §7— " + sname + " " + n.at() + " 필요 (지금 " + value + ")");
            else if (chosen == null) out.add("§e누르면 고름 §7(바꾸려면 갈래 초기화 — 에메랄드 " + StatTree.perPick() + ")");
            else if (chosen == option) out.add("§6고른 갈래 §7(보너스가 붙어 있음)");
            else out.add("§8고르지 않은 갈래 §7(갈래 초기화로 바꿀 수 있음)");
        } else {
            out.add(scol + "§l" + (index + 1) + "단계 「" + n.name() + "」 §r§7(" + sname + " " + n.at() + ")");
            out.add("§a" + StatTree.bonusText(n.bonus()));
            for (String k : n.bonus().keySet()) {
                StatTree.Kind kind = StatTree.kind(k);
                if (kind != null && !kind.desc().isEmpty()) out.add("§8" + kind.name() + ": " + kind.desc());
            }
            if (!n.reward().isEmpty()) {
                boolean got = state().claimedOf(tree.stat()) > index;
                out.add("§d보상: " + StatTree.rewardText(tree.stat(), n.reward()) + (got ? " §a(받음)" : " §7(처음 열릴 때)"));
            }
            out.add(open ? "§a열림 §7(보너스가 붙어 있음)" : "§c잠김 §7— " + sname + " " + n.at() + " 필요 (지금 " + value + ")");
        }
        return out;
    }

    /** 나무 요약 (칸에 마우스가 없을 때) */
    private List<String> summaryLines(StatTree.Tree tree, int value, Map<Integer, Integer> picks) {
        List<String> out = new ArrayList<>();
        String sname = Stats.info(tree.stat()).str("name");
        out.add("§7" + sname + eul(sname) + " 찍으면 나무가 자랍니다 (" + sname + " 10마다 한 칸 · 장비 · 버프 제외)");
        String b = StatTree.bonusText(StatTree.treeTotals(tree.stat(), value, picks));
        out.add("§7이 나무: " + (b.isEmpty() ? "§8아직 없음" : "§a" + b));
        String all = StatTree.bonusText(ClientRpgState.treeTotals());
        out.add("§7나무 넷 합계: " + (all.isEmpty() ? "§8없음" : "§b" + all));
        Map<String, Integer> got = StatTree.rewards(tree.stat(), 0, state().claimedOf(tree.stat()));
        List<String> parts = new ArrayList<>();
        got.forEach((item, n) -> parts.add(StatTree.itemName(item) + " ×" + n));
        out.add("§7받은 보상: " + (parts.isEmpty() ? "§8없음" : "§d" + String.join(" · ", parts)));
        List<Integer> pend = StatTree.pending(tree.stat(), value, picks);
        if (!pend.isEmpty()) out.add("§e" + (pend.get(0) + 1) + "단계 갈래를 고르세요 §7(노랗게 깜빡이는 칸을 누름)");
        out.add("§8칸에 마우스를 올리면 설명 · 갈래 초기화 = 고른 갈래 1개당 에메랄드 " + StatTree.perPick());
        return out;
    }

    private void drawConfirm(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(left, top, left + W, top + H, 0xB0000000);
        int[] b = confirmBox();
        g.fill(b[0] + 2, b[1] + 2, b[0] + b[2] - 2, b[1] + b[3] - 2, 0xF0141420);
        RpgHud.sprite(g, BG, b[0], b[1], b[2], b[3]);
        for (int k = 0; k < confirm.lines().size(); k++) text(g, fit(confirm.lines().get(k), b[2] - 22), b[0] + 11, b[1] + 11 + k * 11);
        for (boolean yes : new boolean[] {true, false}) {
            int[] c = confirmCenter(yes);
            boolean over = inConfirmBtn(mx, my, yes);
            g.fill(c[0] - CONFIRM_BTN_W / 2, c[1] - CONFIRM_BTN_H / 2, c[0] + CONFIRM_BTN_W / 2, c[1] + CONFIRM_BTN_H / 2, over ? 0xC0303040 : 0xC0181822);
            RpgHud.sprite(g, over ? BTN_HOVER : BTN, c[0] - CONFIRM_BTN_W / 2, c[1] - CONFIRM_BTN_H / 2, CONFIRM_BTN_W, CONFIRM_BTN_H);
            String label = yes ? confirm.yes() : "§f취소";
            text(g, label, c[0] - font.width(BedrockText.toJava(label)) / 2, c[1] - 4);
        }
    }

    // ------------------------------------------------------------ 누르기

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        if (confirm != null) {
            if (inConfirmBtn(mx, my, true)) {
                click();
                ClientPlayNetworking.send(new RpgNet.StatTreeActionPayload(confirm.action(), confirm.stat(), confirm.index(), confirm.option()));
                confirm = null;
            } else if (inConfirmBtn(mx, my, false)) {
                click();
                confirm = null;
            }
            return true;
        }
        List<String> stats = StatTree.stats();
        for (int i = 0; i < stats.size(); i++) {
            int x = tabX(i);
            int y = top + TAB_Y;
            if (mx >= x && mx < x + TAB_W && my >= y && my < y + TAB_H) {
                if (!stats.get(i).equals(tab)) click();
                tab = stats.get(i);
                return true;
            }
        }
        if (inBtn(mx, my, left + 12, BACK_W)) {
            click();
            minecraft.gui.setScreen(new StatScreen());
            return true;
        }
        Map<Integer, Integer> picks = state().picksOf(tab);
        if (inBtn(mx, my, left + W - 12 - RESET_W, RESET_W)) {
            if (picks.isEmpty()) {
                deny();
                return true;
            }
            click();
            StatTree.Tree t = StatTree.tree(tab);
            int cost = StatTree.resetCost(picks.size());
            confirm = new Confirm("reset", tab, 0, 0, List.of("§c§l" + t.title() + " 갈래 초기화", "§7고른 갈래 " + picks.size() + "개를 모두 지울까요?",
                    "§a에메랄드 " + cost + "개 §7(가진 것 " + ClientRpgState.money() + ") · 받은 뽑기권은 그대로"), "§c초기화");
            return true;
        }
        StatTree.Tree tree = StatTree.tree(tab);
        int value = spent(tab);
        for (int i = 0; i < tree.nodes().size(); i++) {
            StatTree.Node n = tree.nodes().get(i);
            if (!n.fork()) continue;
            for (int o = 0; o < n.pick().size(); o++) {
                int[] c = nodeCenter(i, o);
                if (Math.abs(mx - c[0]) > NODE / 2 || Math.abs(my - c[1]) > NODE / 2) continue;
                if (value < n.at() || picks.containsKey(i)) {
                    deny();
                    return true;
                }
                click();
                StatTree.Option opt = n.pick().get(o);
                confirm = new Confirm("pick", tab, i, o, List.of(Stats.info(tab).str("color") + "§l" + (i + 1) + "단계 갈래: 「" + opt.name() + "」",
                        "§a" + StatTree.bonusText(opt.bonus()), "§7바꾸려면 갈래 초기화 (에메랄드 " + StatTree.perPick() + ")"), "§e고르기");
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (confirm != null && event.key() == InputConstants.KEY_ESCAPE) {
            confirm = null;
            return true;
        }
        if (MmorpgClient.statsKey != null && MmorpgClient.statsKey.matches(event)) {
            onClose();
            return true;
        }
        int k = event.key();
        if (k >= InputConstants.KEY_1 && k < InputConstants.KEY_1 + StatTree.stats().size() && confirm == null) {
            select(StatTree.stats().get(k - InputConstants.KEY_1));
            click();
            return true;
        }
        return super.keyPressed(event);
    }
}
