package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mmorpg.core.Js;
import mmorpg.core.Relics;
import mmorpg.core.StatTree;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.rpgui.RpgUi;
import mmorpg.fabric.gear.RelicItem;
import mmorpg.fabric.gear.WeaponEnhance;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 0.43.0 아티팩트 판 창 (I 키 창의 "아티팩트" 탭 · /아티팩트 · 인벤토리 패널의 아티팩트 단추 — 사용자: "본인이 어떻게 설계 할지에 따라서 스탯이 달라지게,
 * 꽂기만 하면 스탯을 본인이 고를 수 있음, 잘 연결되면 패시브"):
 * <ul>
 * <li>왼쪽 판 3 × 3: 칸마다 꽂은 아티팩트 (등급 빛 · 왼쪽 위 계열 점 · 오른쪽 위 패시브 단계 점 · 오른쪽 아래 고른 스탯 줄 그림 · 착용 레벨이 모자라면 빨갛게).
 * 이웃 칸이 같은 계열이면 그 계열 색 선 (묶음이 단계가 되면 밝게 · 빛이 흐르고 칸 둘레에 계열 빛), 아니면 흐린 선.</li>
 * <li>오른쪽: 패시브 (단계 점 · 이름 · 묶음 칸 수 / 다음 단계 — 꺼진 것은 흐리게) · 스탯 줄 합계 여덟 (바뀐 줄은 잠깐 초록 빛).
 * 칸을 누르면 그 칸의 스탯 줄 고르기 (여덟 단추 — 그 아티팩트의 등급 · 강화 값) · 빼기 단추.</li>
 * <li>아래: 가방의 아티팩트 — 누르면 고른 칸 (없으면 첫 빈 칸) 에 (그 칸의 줄을 씀), 칸에 끌어 놓기, 칸끼리 끌기 = 자리 바꾸기 (고른 줄도 함께 옮김),
 * 칸 오른쪽 클릭 · 판 밖으로 끌기 · [빼기] = 빼기 (줄은 칸에 남음).</li>
 * </ul>
 * 실제로 옮기는 것은 서버 (RelicSystem) — 받은 판으로 다시 그림. 게임은 멈추지 않음.
 */
public final class RelicScreen extends Screen {
    public static final int W = ArtifactScreen.W;
    public static final int H = ArtifactScreen.H;
    /** 판 칸 크기 · 칸 사이 (선이 지나감) · 첫 칸 자리 (창 기준) */
    static final int S = 24;
    static final int G = 10;
    static final int BX = 12;
    static final int BY = 31;
    /** 오른쪽 칸 (패시브 · 합계 / 스탯 줄 고르기) */
    static final int PX = 112;
    static final int PY = 29;
    static final int PW = 178;
    static final int PH = 96;
    static final int ROW_H = 10;
    static final int PASSIVE_ROWS = 4;
    static final int SUM_Y = 60;
    static final int SUM_H = 9;
    /** 스탯 줄 단추 */
    static final int BTN_Y = 26;
    static final int BTN_H = 15;
    static final int BTN_STEP = 17;
    /** 가방의 아티팩트 칸 */
    static final int CELL = 20;
    static final int COLS = 9;
    static final int ROWS = 3;
    static final int GRID_Y = 142;
    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier BOARD = Mmorpg.id("relic/board");
    private static final Identifier SOCKET = Mmorpg.id("relic/socket");
    private static final int GOLD = 0xFFD24A;

    private int left;
    private int top;
    /** 고른 칸 (-1 없음) — 스탯 줄 고르기 */
    private int sel = -1;
    /** 끌기: 시작한 곳 (-1 없음 · 0 ~ 8 칸 · 1000 + 가방 칸) */
    private int dragFrom = -1;
    private double pressX;
    private double pressY;
    private boolean dragging;
    /** 방금 꽂은 가방 칸 (서버가 판을 보낼 때까지 감춤) */
    private int hiddenInv = -1;
    private long hiddenUntil;
    /** 가방 줄 내림 */
    private int bagScroll;
    private String note = "";
    private long noteAt;
    private final long openedAt = Util.getMillis();

    public RelicScreen() {
        super(Component.literal("아티팩트"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(0, (height - H) / 2);
        GearTabs.last = GearTabs.RELICS;
        ClientRelics.send("sync", 0, 0, "");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------ 자리 (시험용 — GUI 좌표)

    private static int n() {
        return Math.max(1, Relics.size());
    }

    private static int boardW() {
        return n() * S + (n() - 1) * G;
    }

    private int cellX(int i) {
        return left + BX + (i % n()) * (S + G);
    }

    private int cellY(int i) {
        return top + BY + (i / n()) * (S + G);
    }

    /** 칸 i 가운데 (시험용) */
    public int[] cellCenter(int i) {
        return new int[] {cellX(i) + S / 2, cellY(i) + S / 2};
    }

    /** 0.43.0 탭 i (0 장신구 · 1 아티팩트) 가운데 (시험용) */
    public int[] tabCenter(int i) {
        return GearTabs.center(font, left, top, i);
    }

    /** 고른 칸 (시험용) */
    public int selected() {
        return sel;
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < ClientRelics.SIZE; i++) {
            int x = cellX(i);
            int y = cellY(i);
            if (mx >= x - 2 && mx < x + S + 2 && my >= y - 2 && my < y + S + 2) return i;
        }
        return -1;
    }

    private boolean inBoard(double mx, double my) {
        return mx >= left + BX - 4 && mx < left + BX + boardW() + 4 && my >= top + BY - 4 && my < top + BY + boardW() + 4;
    }

    private boolean inPanel(double mx, double my) {
        return mx >= left + PX && mx < left + PX + PW && my >= top + PY && my < top + PY + PH;
    }

    private int gridX() {
        return left + (W - COLS * CELL) / 2;
    }

    private int gridY() {
        return top + GRID_Y;
    }

    /** 가방의 아티팩트 (그리드 순서 n — 내림 반영) 가운데 (시험용) */
    public int[] gridCenter(int k) {
        int v = k - bagScroll * COLS;
        return new int[] {gridX() + (v % COLS) * CELL + CELL / 2, gridY() + (v / COLS) * CELL + CELL / 2};
    }

    /** 스탯 줄 단추 k (Relics.lineKeys 순서) 자리 {x, y, w, h} */
    private int[] statButton(int k) {
        int bw = (PW - 12) / 2;
        return new int[] {left + PX + 4 + (k % 2) * (bw + 4), top + PY + BTN_Y + (k / 2) * BTN_STEP, bw, BTN_H};
    }

    /** 스탯 줄 단추 k 가운데 (시험용) */
    public int[] statCenter(int k) {
        int[] b = statButton(k);
        return new int[] {b[0] + b[2] / 2, b[1] + b[3] / 2};
    }

    private int statAt(double mx, double my) {
        if (sel < 0) return -1;
        List<String> keys = Relics.lineKeys();
        for (int k = 0; k < keys.size(); k++) {
            int[] b = statButton(k);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return k;
        }
        return -1;
    }

    /** 고른 칸 창의 [×] · [빼기] 자리 {x, y, w, h} */
    private int[] closeButton() {
        return new int[] {left + PX + PW - 15, top + PY + 3, 11, 11};
    }

    private int[] removeButton() {
        return new int[] {left + PX + PW - 15 - 4 - 30, top + PY + 3, 30, 11};
    }

    /** [빼기] 가운데 (시험용) */
    public int[] removeCenter() {
        int[] b = removeButton();
        return new int[] {b[0] + b[2] / 2, b[1] + b[3] / 2};
    }

    private static boolean in(int[] b, double mx, double my) {
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    /** 패시브 줄 r 가운데 (시험용) */
    public int[] passiveCenter(int r) {
        return new int[] {left + PX + PW / 2, top + PY + 15 + r * ROW_H + 4};
    }

    /** 가방의 아티팩트 (그리드 순서): 가방 칸 번호 (0 ~ 35 · 왼손 40) — 높은 등급 · 종류 순 */
    public List<Integer> bagSlots() {
        List<Integer> out = new ArrayList<>();
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        if (p == null) return out;
        long now = Util.getMillis();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (i == hiddenInv && now < hiddenUntil) continue;
            if (RelicItem.of(p.getInventory().getItem(i)) != null) out.add(i);
        }
        if (RelicItem.of(p.getInventory().getItem(Inventory.SLOT_OFFHAND)) != null && !(hiddenInv == Inventory.SLOT_OFFHAND && now < hiddenUntil)) {
            out.add(Inventory.SLOT_OFFHAND);
        }
        List<String> order = Relics.types();
        out.sort((a, b) -> {
            Relics.Relic x = RelicItem.of(invItem(a));
            Relics.Relic y = RelicItem.of(invItem(b));
            if (x.gradeIndex() != y.gradeIndex()) return y.gradeIndex() - x.gradeIndex();
            int t = order.indexOf(x.type()) - order.indexOf(y.type());
            return t != 0 ? t : WeaponEnhance.of(invItem(b)) - WeaponEnhance.of(invItem(a));
        });
        return out;
    }

    private int invAt(double mx, double my, List<Integer> list) {
        int from = bagScroll * COLS;
        for (int k = from; k < Math.min(list.size(), from + COLS * ROWS); k++) {
            int v = k - from;
            int x = gridX() + (v % COLS) * CELL;
            int y = gridY() + (v / COLS) * CELL;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return list.get(k);
        }
        return -1;
    }

    private boolean inGrid(double mx, double my) {
        return mx >= gridX() && mx < gridX() + COLS * CELL && my >= gridY() && my < gridY() + ROWS * CELL;
    }

    private ItemStack invItem(int slot) {
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        return p == null ? ItemStack.EMPTY : p.getInventory().getItem(slot);
    }

    // ------------------------------------------------------------ 패시브 줄

    /** 패시브 한 줄: 종류 · 단계 · 그 종류가 든 가장 큰 묶음 칸 수 */
    record PassiveRow(String type, int tier, int best) {}

    /** 판에 있는 (쓸 수 있는) 종류의 패시브 — 켜진 것 (높은 단계) 먼저, 그다음 묶음이 큰 것 */
    static List<PassiveRow> passiveRows(Relics.Board b) {
        List<PassiveRow> out = new ArrayList<>();
        for (String k : Relics.types()) {
            if (!b.tiers().containsKey(k)) continue;
            int best = 0;
            for (Relics.Group gr : b.groups()) {
                for (int c : gr.cells()) {
                    Relics.Relic r = Relics.byId(b.cells().get(c).relicId());
                    if (r != null && r.type().equals(k)) {
                        best = Math.max(best, gr.size());
                        break;
                    }
                }
            }
            out.add(new PassiveRow(k, b.tier(k), best));
        }
        List<String> order = Relics.types();
        out.sort((x, y) -> x.tier() != y.tier() ? y.tier() - x.tier() : x.best() != y.best() ? y.best() - x.best() : order.indexOf(x.type()) - order.indexOf(y.type()));
        return out;
    }

    /** 이 칸이 든 계열 묶음들 (계열 → 칸 수) */
    private static Map<String, Integer> groupsOf(Relics.Board b, int cell) {
        Map<String, Integer> out = new HashMap<>();
        for (Relics.Group gr : b.groups()) if (gr.cells().contains(cell)) out.merge(gr.stat(), gr.size(), Math::max);
        return out;
    }

    /** 이 계열 · 칸이 든 묶음의 단계 (없으면 0) */
    private static int linkTier(Relics.Board b, int cell, String stat) {
        for (Relics.Group gr : b.groups()) if (gr.stat().equals(stat) && gr.cells().contains(cell)) return Relics.tierOf(gr.size());
        return 0;
    }

    // ------------------------------------------------------------ 그리기

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        if (dragFrom >= 0 && !dragging && Math.hypot(mx - pressX, my - pressY) > 3) dragging = true;
        long now = Util.getMillis();
        double level = ClientArtifacts.level();
        Relics.Board b = ClientRelics.board();
        if (sel >= ClientRelics.SIZE) sel = -1;
        RpgHud.sprite(g, BG, left, top, W, H);
        GearTabs.draw(g, font, left, top, GearTabs.RELICS, mx, my);
        String head = "§7칸 §f" + b.filled() + "§7/" + ClientRelics.SIZE + " §8· §7패시브 §f" + b.active() + "  §8[" + keyName() + "]";
        g.text(font, head, left + W - 12 - font.width(head), top + 11, 0xFFFFFFFF, true);
        g.fill(left + 10, top + 26, left + W - 10, top + 27, 0x90C8A050);

        // 판
        int hover = dragging ? -1 : cellAt(mx, my);
        int drop = dragging ? cellAt(mx, my) : -1;
        drawBoard(g, b, now);
        for (int i = 0; i < ClientRelics.SIZE; i++) {
            boolean lifted = dragging && dragFrom == i;
            boolean target = dragging && dragFrom != i && (dragFrom >= 1000 || dragFrom >= 0);
            drawCell(g, b, i, now, level, hover == i, drop == i, target, lifted);
        }

        // 오른쪽 칸
        int px = left + PX;
        int py = top + PY;
        g.fill(px, py, px + PW, py + PH, 0x70000000);
        g.outline(px, py, PW, PH, 0x90C8A050);
        PanelHover ph = sel >= 0 ? drawPicker(g, b, mx, my, now) : drawSummary(g, b, mx, my, now);

        // 가방의 아티팩트
        List<Integer> bag = bagSlots();
        int maxScroll = Math.max(0, (bag.size() + COLS - 1) / COLS - ROWS);
        bagScroll = Math.max(0, Math.min(bagScroll, maxScroll));
        g.fill(left + 10, top + 128, left + W - 10, top + 129, 0x60C8A050);
        g.text(font, "§6가진 아티팩트 §7(" + bag.size() + "개 · 가방)", left + 12, top + 131, 0xFFFFFFFF, true);
        String how = sel >= 0 ? "§e누르면 " + (sel + 1) + "번 칸에" : "§8누르면 빈 칸 · 끌어 놓기";
        g.text(font, how, left + W - 12 - font.width(how), top + 131, 0xFFFFFFFF, true);
        int hoverInv = dragging ? -1 : invAt(mx, my, bag);
        int from = bagScroll * COLS;
        for (int v = 0; v < COLS * ROWS; v++) {
            int x = gridX() + (v % COLS) * CELL;
            int y = gridY() + (v / COLS) * CELL;
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40000000);
            int k = from + v;
            if (k >= bag.size()) continue;
            int slot = bag.get(k);
            ItemStack st = invItem(slot);
            Relics.Relic r = RelicItem.of(st);
            int c = RpgUi.gradeRgb(r == null ? 0 : r.gradeIndex());
            g.fill(x + 1, y + CELL - 3, x + CELL - 1, y + CELL - 1, (0x90 << 24) | c);
            if (hoverInv == slot) g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x50FFFFFF);
            if (!(dragging && dragFrom == 1000 + slot)) g.item(st, x + 2, y + 2);
            if (r != null) {
                statDots(g, r, x + 2, y + 2);
                int en = WeaponEnhance.of(st);
                if (en > 0) small(g, "§e+" + en, x + 2, y + CELL - 8, false);
                if (!Relics.usable(r, level)) {
                    g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x50FF2020);
                    small(g, "§c" + r.level(), x + CELL - 2, y + 1, true);
                }
            }
        }
        if (bag.isEmpty()) g.text(font, "§8가방에 아티팩트가 없습니다 — 보스 · 몬스터에게서 얻음", gridX() + 4, gridY() + 6, 0xFFFFFFFF, true);
        if (maxScroll > 0) {
            int bx = gridX() + COLS * CELL + 2;
            int h = ROWS * CELL;
            g.fill(bx, gridY(), bx + 2, gridY() + h, 0x40FFFFFF);
            int bh = Math.max(6, h * ROWS / (ROWS + maxScroll));
            int by = gridY() + (h - bh) * bagScroll / maxScroll;
            g.fill(bx, by, bx + 2, by + bh, 0xC0FFD24A);
        }

        // 도움말 · 알림
        int hy = top + H - 28;
        g.fill(left + 10, hy - 3, left + W - 10, hy - 2, 0x60C8A050);
        g.text(font, "§7칸 누르기 = 스탯 줄 고르기 §8· §7칸끼리 끌기 = 자리 바꾸기", left + 12, hy, 0xFFFFFFFF, true);
        boolean noteOn = !note.isEmpty() && now - noteAt < 2600;
        g.text(font, noteOn ? note : "§7같은 계열 이웃 " + Relics.tiersText() + " = 패시브 1 · 2 · 3단계 §8(밖으로 끌기 = 빼기)", left + 12, hy + 11, 0xFFFFFFFF, true);

        // 끄는 그림
        if (dragging && dragFrom >= 0) {
            ItemStack st = dragFrom >= 1000 ? invItem(dragFrom - 1000) : ClientRelics.get(dragFrom);
            if (!st.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(mx, my);
                g.pose().scale(1.25F, 1.25F);
                g.item(st, -8, -8);
                g.pose().popMatrix();
            }
            return;
        }

        // 풀이
        List<Component> tip = null;
        if (hover >= 0) tip = cellTip(b, hover, level);
        else if (hoverInv >= 0) tip = bagTip(hoverInv, level);
        else if (ph != null) tip = ph.tip();
        if (tip != null && !tip.isEmpty()) g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
    }

    /** 오른쪽 칸 위의 풀이 (없으면 null) */
    private record PanelHover(List<Component> tip) {}

    /** 판 바탕 · 단계가 된 묶음의 계열 빛 · 이웃 선 */
    private void drawBoard(GuiGraphicsExtractor g, Relics.Board b, long now) {
        int bw = boardW();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BOARD, left + BX - 4, top + BY - 4, bw + 8, bw + 8);
        // 단계가 된 묶음: 칸 둘레에 계열 빛 (숨 쉼)
        for (Relics.Group gr : b.groups()) {
            int t = Relics.tierOf(gr.size());
            if (t <= 0) continue;
            int rgb = Relics.statColor(gr.stat());
            int a = (int) (0x26 + 0x12 * t + 0x14 * Math.sin(now / 420.0 + gr.cells().get(0)));
            for (int c : gr.cells()) {
                int x = cellX(c);
                int y = cellY(c);
                g.fill(x - 3, y - 3, x + S + 3, y + S + 3, (Math.max(0, Math.min(255, a)) << 24) | rgb);
            }
        }
        // 이웃 선 (한 쌍에 계열이 둘이면 두 줄)
        Map<Long, List<Relics.Link>> pairs = new HashMap<>();
        for (Relics.Link l : b.links()) pairs.computeIfAbsent((long) l.a() * 1000 + l.b(), k -> new ArrayList<>()).add(l);
        for (List<Relics.Link> ls : pairs.values()) {
            int k = ls.size();
            for (int j = 0; j < k; j++) {
                Relics.Link l = ls.get(j);
                int tier = linkTier(b, l.a(), l.stat());
                int th = k == 1 ? 4 : 2;
                int off = k == 1 ? -2 : (j == 0 ? -2 : 0);
                drawLink(g, l, tier, th, off, now);
            }
        }
    }

    /** 이웃 선 하나: 단계가 된 묶음이면 계열 색 밝게 + 흐르는 빛, 아니면 어둡게 */
    private void drawLink(GuiGraphicsExtractor g, Relics.Link l, int tier, int th, int off, long now) {
        int rgb = Relics.statColor(l.stat());
        boolean horizontal = l.b() == l.a() + 1;
        int col = tier > 0 ? 0xFF000000 | rgb : 0xFF000000 | shade(rgb, 0.5);
        int x0;
        int y0;
        int x1;
        int y1;
        if (horizontal) {
            x0 = cellX(l.a()) + S - 1;
            x1 = cellX(l.b()) + 1;
            int cy = cellY(l.a()) + S / 2;
            y0 = cy + off;
            y1 = y0 + th;
        } else {
            y0 = cellY(l.a()) + S - 1;
            y1 = cellY(l.b()) + 1;
            int cx = cellX(l.a()) + S / 2;
            x0 = cx + off;
            x1 = x0 + th;
        }
        g.fill(x0, y0, x1, y1, col);
        if (tier <= 0) return;
        // 흐르는 빛 (단계가 높을수록 빠르게)
        int len = horizontal ? x1 - x0 : y1 - y0;
        int p = (int) ((now / Math.max(12, 40 - tier * 8) + l.a() * 5L + l.b() * 3L) % (len + 4)) - 2;
        int lo = Math.max(0, p);
        int hi = Math.min(len, p + 3);
        if (hi <= lo) return;
        int glow = 0xFF000000 | mixRgb(rgb, 0xFFFFFF, 0.7);
        if (horizontal) g.fill(x0 + lo, y0, x0 + hi, y1, glow);
        else g.fill(x0, y0 + lo, x1, y0 + hi, glow);
    }

    /** 칸 하나: 받침 · 등급 빛 · 그림 · 계열 점 · 단계 점 · 고른 줄 그림 · 강화 · 연출 */
    private void drawCell(GuiGraphicsExtractor g, Relics.Board b, int i, long now, double level, boolean hover, boolean drop, boolean target, boolean lifted) {
        int x = cellX(i);
        int y = cellY(i);
        int cx = x + S / 2;
        int cy = y + S / 2;
        ItemStack st = ClientRelics.get(i);
        Relics.Relic r = ClientRelics.relic(i);
        if (drop) g.fill(x - 3, y - 3, x + S + 3, y + S + 3, 0x70FFD24A);
        else if (target) g.fill(x - 2, y - 2, x + S + 2, y + S + 2, (int) (0x30 + 0x28 * Math.sin(now / 160.0)) << 24 | GOLD);
        else if (hover) g.fill(x - 2, y - 2, x + S + 2, y + S + 2, 0x40FFFFFF);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SOCKET, x, y, S, S);
        float eq = SkillBarHud.phase(ClientRelics.EQUIP_AT[i], now, 520);
        if (r != null && !lifted) {
            int rgb = RpgUi.gradeRgb(r.gradeIndex());
            boolean high = r.gradeIndex() >= 3;
            g.fillGradient(x + 3, y + S / 2, x + S - 3, y + S - 3, rgb & 0xFFFFFF, (high ? 0x60 : 0x40) << 24 | rgb);
            int alpha = high ? (int) (0xB0 + 0x4F * Math.sin(now / (r.gradeIndex() == 4 ? 240.0 : 360.0))) : 0xD0;
            g.outline(x + 2, y + 2, S - 4, S - 4, (Math.max(0, Math.min(255, alpha)) << 24) | rgb);
            float k = eq >= 0 ? 1.0F + 0.3F * (1 - eq) * (1 - eq) : 1.0F;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(k, k);
            g.item(st, -8, -8);
            g.pose().popMatrix();
            statDots(g, r, x + 3, y + 3);
            int t = b.cellTier()[i];
            for (int j = 0; j < t; j++) {
                int dx = x + S - 5 - j * 3;
                g.fill(dx, y + 3, dx + 2, y + 5, 0xFF000000 | GOLD);
            }
            int en = ClientRelics.enhance(i);
            if (en > 0) small(g, "§e+" + en, x + 3, y + S - 7, false);
            if (!b.usable()[i]) {
                g.fill(x + 3, y + 3, x + S - 3, y + S - 3, 0x55FF2020);
                small(g, "§c" + r.level() + "렙", x + S - 2, y + S - 13, true);
            }
        }
        // 고른 스탯 줄 그림 (오른쪽 아래 — 줄을 바꾸면 잠깐 커짐)
        ItemStack icon = ArtifactScreen.kindIcon(ClientRelics.stat(i));
        if (!icon.isEmpty() && !lifted) {
            float sp = SkillBarHud.phase(ClientRelics.STAT_AT[i], now, 600);
            float k = 0.5F * (sp >= 0 ? 1.0F + 0.6F * (1 - sp) : 1.0F);
            g.pose().pushMatrix();
            g.pose().translate(x + S - 5, y + S - 5);
            g.pose().scale(k, k);
            g.item(icon, -8, -8);
            g.pose().popMatrix();
            if (sp >= 0) SkillBarHud.ring(g, x + S - 5, y + S - 5, 4 + 8 * sp, 0x7CFF8A, 1 - sp);
        }
        if (sel == i) {
            int a = (int) (0xB0 + 0x4F * Math.sin(now / 200.0));
            int c = (Math.max(0, Math.min(255, a)) << 24) | GOLD;
            g.outline(x - 2, y - 2, S + 4, S + 4, c);
            g.outline(x - 1, y - 1, S + 2, S + 2, c);
        }
        if (eq >= 0) SkillBarHud.ring(g, cx, cy, S * (0.5F + 0.6F * eq), r == null ? GOLD : RpgUi.gradeRgb(r.gradeIndex()), 1 - eq);
        float rm = SkillBarHud.phase(ClientRelics.REMOVE_AT[i], now, 380);
        if (rm >= 0) SkillBarHud.ring(g, cx, cy, S * (0.6F - 0.25F * rm), 0xB0B0B0, 0.7F * (1 - rm));
        // 패시브 단계가 오름: 금빛 번쩍 + 퍼지는 고리
        if (r != null) {
            Long up = ClientRelics.TIER_AT.get(r.type());
            float tp = up == null ? -1 : SkillBarHud.phase(up, now, 900);
            if (tp >= 0) {
                g.fill(x, y, x + S, y + S, ((int) (0x90 * (1 - tp)) << 24) | 0xFFF2B0);
                SkillBarHud.ring(g, cx, cy, S * (0.6F + 0.9F * tp), GOLD, 1 - tp);
            }
        }
    }

    /** 계열 점 (왼쪽 위 — 계열 색 작은 네모 하나 또는 둘) */
    private static void statDots(GuiGraphicsExtractor g, Relics.Relic r, int x, int y) {
        Relics.Type t = r.def();
        if (t == null) return;
        for (int j = 0; j < t.stats().size(); j++) {
            int dx = x + j * 4;
            g.fill(dx - 1, y - 1, dx + 3, y + 3, 0xC0000000);
            g.fill(dx, y, dx + 2, y + 2, 0xFF000000 | Relics.statColor(t.stats().get(j)));
        }
    }

    /** 오른쪽 칸 (고른 칸 없음): 패시브 · 스탯 줄 합계 */
    private PanelHover drawSummary(GuiGraphicsExtractor g, Relics.Board b, int mx, int my, long now) {
        int px = left + PX;
        int py = top + PY;
        g.text(font, "§e§l패시브", px + 5, py + 4, 0xFFFFFFFF, true);
        String th = "§8" + Relics.tiersText() + " → 1 · 2 · 3단계";
        g.text(font, th, px + PW - 5 - font.width(th), py + 4, 0xFFFFFFFF, true);
        List<PassiveRow> rows = passiveRows(b);
        PanelHover hov = null;
        if (rows.isEmpty()) {
            g.text(font, "§8아티팩트를 꽂고 같은 계열을", px + 6, py + 17, 0xFFFFFFFF, true);
            g.text(font, "§8이웃으로 " + Relics.tierThresholds().get(0) + "칸 이으면 패시브가 켜집니다", px + 6, py + 27, 0xFFFFFFFF, true);
        }
        int shown = rows.size() > PASSIVE_ROWS ? PASSIVE_ROWS - 1 : rows.size();
        for (int r = 0; r < shown; r++) {
            PassiveRow row = rows.get(r);
            Relics.Type t = Relics.type(row.type());
            int ry = py + 15 + r * ROW_H;
            Long up = ClientRelics.TIER_AT.get(row.type());
            if (up != null && now - up < 1400) {
                int a = (int) (0x70 * (1 - (now - up) / 1400.0));
                g.fill(px + 2, ry - 1, px + PW - 2, ry + ROW_H - 1, (a << 24) | GOLD);
            }
            boolean over = mx >= px && mx < px + PW && my >= ry - 1 && my < ry + ROW_H - 1;
            if (over) g.fill(px + 2, ry - 1, px + PW - 2, ry + ROW_H - 1, 0x22FFFFFF);
            for (int j = 1; j <= Relics.maxTier(); j++) {
                int dx = px + 5 + (j - 1) * 4;
                g.fill(dx, ry + 2, dx + 3, ry + 5, j <= row.tier() ? 0xFF000000 | GOLD : 0xFF3A3A48);
            }
            int need = Relics.nextNeed(row.best());
            String prog = row.tier() >= Relics.maxTier() ? "§6최고" : "§8" + row.best() + "/" + need + "칸";
            int pw = font.width(prog);
            g.text(font, prog, px + PW - 5 - pw, ry, 0xFFFFFFFF, true);
            String name = (row.tier() > 0 ? "§e" : "§8") + "「" + t.passive().name() + "」 " + (row.tier() > 0 ? "§7" : "§8") + t.name();
            fitText(g, name, px + 19, ry, PW - 19 - 5 - pw - 4);
            if (over) hov = new PanelHover(passiveTip(row));
        }
        if (rows.size() > PASSIVE_ROWS) {
            int ry = py + 15 + (PASSIVE_ROWS - 1) * ROW_H;
            g.text(font, "§8+" + (rows.size() - shown) + "개 더 §7(마우스를 올리면 모두)", px + 19, ry, 0xFFFFFFFF, true);
            if (mx >= px && mx < px + PW && my >= ry - 1 && my < ry + ROW_H - 1) {
                List<Component> tip = new ArrayList<>();
                tip.add(lit("§e§l패시브 모두"));
                for (PassiveRow row : rows) {
                    Relics.Type t = Relics.type(row.type());
                    tip.add(lit((row.tier() > 0 ? "§6" + row.tier() + "단계 §e" : "§8꺼짐 ") + "「" + t.passive().name() + "」 §7" + t.name()));
                }
                hov = new PanelHover(tip);
            }
        }
        g.fill(px + 4, py + SUM_Y - 3, px + PW - 4, py + SUM_Y - 2, 0x60C8A050);
        Map<String, Double> sum = b.totals();
        List<String> keys = Relics.lineKeys();
        int colW = (PW - 14) / 2;
        int rowsPerCol = (keys.size() + 1) / 2;
        for (int k = 0; k < keys.size(); k++) {
            String key = keys.get(k);
            int col = k / rowsPerCol;
            int row = k % rowsPerCol;
            int x = px + 5 + col * (colW + 4);
            int y = py + SUM_Y + row * SUM_H;
            double v = sum.getOrDefault(key, 0.0);
            Long ch = ClientRelics.CHANGED_AT.get(key);
            if (ch != null && now - ch < 900) {
                int a = (int) (0x80 * (1 - (now - ch) / 900.0));
                g.fill(x - 2, y - 1, x + colW + 1, y + SUM_H - 1, (a << 24) | 0x7CFF8A);
            }
            boolean over = mx >= x - 2 && mx < x + colW + 1 && my >= y - 1 && my < y + SUM_H - 1;
            String nm = Relics.lineName(key);
            String val = v != 0 ? "§a" + StatTree.bonusText(key, v).substring(nm.length() + 1) : "§8—";
            int vw = font.width(val);
            g.text(font, val, x + colW - vw, y, 0xFFFFFFFF, true);
            fitText(g, (v != 0 ? "§f" : "§8") + nm, x, y, colW - vw - 3);
            if (over) {
                StatTree.Kind kind = StatTree.kind(key);
                List<Component> tip = new ArrayList<>();
                tip.add(lit("§e" + nm + (v != 0 ? " §a" + StatTree.bonusText(key, v).substring(nm.length() + 1) : "")));
                if (kind != null && !kind.desc().isEmpty()) tip.add(lit("§7" + kind.desc()));
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < ClientRelics.SIZE; i++) if (b.usable()[i] && key.equals(ClientRelics.stat(i))) cells.add(String.valueOf(i + 1));
                tip.add(lit(cells.isEmpty() ? "§8이 줄을 고른 칸 없음" : "§7고른 칸: §f" + String.join(" · ", cells) + "번"));
                tip.add(lit("§8스탯 트리 · 장신구 보너스에 더함 (상한 같음)"));
                hov = new PanelHover(tip);
            }
        }
        return hov;
    }

    /** 오른쪽 칸 (고른 칸 있음): 그 칸의 아티팩트 · 계열 · 패시브, 스탯 줄 여덟 단추, [빼기] · [×] */
    private PanelHover drawPicker(GuiGraphicsExtractor g, Relics.Board b, int mx, int my, long now) {
        int px = left + PX;
        int py = top + PY;
        Relics.Relic r = ClientRelics.relic(sel);
        int en = ClientRelics.enhance(sel);
        PanelHover hov = null;
        int[] cb = closeButton();
        boolean overClose = in(cb, mx, my);
        g.fill(cb[0], cb[1], cb[0] + cb[2], cb[1] + cb[3], overClose ? 0x60FF6060 : 0x40000000);
        g.outline(cb[0], cb[1], cb[2], cb[3], 0x90C8A050);
        g.text(font, "§c×", cb[0] + 3, cb[1] + 2, 0xFFFFFFFF, true);
        int nameMax = cb[0] - px - 9;
        if (r != null) {
            int[] rb = removeButton();
            boolean overRemove = in(rb, mx, my);
            g.fill(rb[0], rb[1], rb[0] + rb[2], rb[1] + rb[3], overRemove ? 0x50FFFFFF : 0x40000000);
            g.outline(rb[0], rb[1], rb[2], rb[3], 0x90C8A050);
            String rt = "§7빼기";
            g.text(font, rt, rb[0] + (rb[2] - font.width(rt)) / 2, rb[1] + 2, 0xFFFFFFFF, true);
            nameMax = rb[0] - px - 9;
            if (overRemove) hov = new PanelHover(List.of(lit("§7" + (sel + 1) + "번 칸의 아티팩트를 가방으로"), lit("§8고른 스탯 줄은 칸에 남음")));
        }
        if (overClose) hov = new PanelHover(List.of(lit("§7닫기 §8(패시브 · 합계 보기)")));
        String head = "§6§l" + (sel + 1) + "번 칸 §r" + (r == null ? "§8비어 있음" : r.coloredName(en));
        fitText(g, head, px + 5, py + 4, nameMax);
        String sub;
        if (r == null) sub = "§8고른 줄은 다음에 꽂는 아티팩트가 씁니다";
        else {
            Relics.Type t = r.def();
            int tier = b.tier(r.type());
            sub = "§7계열 " + Relics.statsText(t) + "  " + (!b.usable()[sel] ? "§c" + r.level() + "렙부터"
                    : tier > 0 ? "§e「" + t.passive().name() + "」 §6" + tier + "단계" : "§8「" + t.passive().name() + "」 꺼짐");
        }
        fitText(g, sub, px + 5, py + 15, PW - 10);
        List<String> keys = Relics.lineKeys();
        String cur = ClientRelics.stat(sel);
        int hk = statAt(mx, my);
        for (int k = 0; k < keys.size(); k++) {
            String key = keys.get(k);
            int[] bt = statButton(k);
            boolean on = key.equals(cur);
            boolean over = hk == k;
            g.fill(bt[0], bt[1], bt[0] + bt[2], bt[1] + bt[3], on ? 0x50FFD24A : over ? 0x40FFFFFF : 0x50000000);
            g.outline(bt[0], bt[1], bt[2], bt[3], on ? 0xFF000000 | GOLD : 0x70C8A050);
            if (on) g.fill(bt[0] + 1, bt[1] + 1, bt[0] + 3, bt[1] + bt[3] - 1, 0xFF000000 | GOLD);
            ItemStack icon = ArtifactScreen.kindIcon(key);
            if (!icon.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(bt[0] + 5, bt[1] + 3.5F);
                g.pose().scale(0.5F, 0.5F);
                g.item(icon, 0, 0);
                g.pose().popMatrix();
            }
            String label = r == null ? Relics.lineName(key) : Relics.lineText(key, Relics.lineValue(key, r.gradeIndex(), en));
            fitText(g, (on ? "§e" : over ? "§f" : "§7") + label, bt[0] + 15, bt[1] + 4, bt[2] - 17);
            if (over) {
                StatTree.Kind kind = StatTree.kind(key);
                List<Component> tip = new ArrayList<>();
                tip.add(lit("§e" + Relics.lineName(key) + (on ? " §7(고른 줄)" : "")));
                if (kind != null && !kind.desc().isEmpty()) tip.add(lit("§7" + kind.desc()));
                if (r != null) {
                    List<String> per = new ArrayList<>();
                    for (int gi = 0; gi < 5; gi++) per.add(Js.str(Js.round(Relics.lineValue(key, gi, en) * 100) / 100.0));
                    tip.add(lit("§8등급마다 (일반 → 초월): " + String.join(" · ", per)));
                }
                tip.add(lit(on ? "§8지금 고른 줄" : "§e누르면 이 줄로 바꿈 §8(언제든 다시 바꿀 수 있음)"));
                hov = new PanelHover(tip);
            }
        }
        return hov;
    }

    // ------------------------------------------------------------ 풀이

    private List<Component> cellTip(Relics.Board b, int i, double level) {
        List<Component> tip = new ArrayList<>();
        ItemStack st = ClientRelics.get(i);
        Relics.Relic r = ClientRelics.relic(i);
        String stat = ClientRelics.stat(i);
        if (r == null) {
            tip.add(lit("§6" + (i + 1) + "번 칸 §7(빈 칸)"));
            tip.add(lit("§7고른 스탯 줄: §f" + Relics.lineName(stat)));
            tip.add(lit("§8가방의 아티팩트를 끌어 놓거나, 칸을 고르고 가방의 것을 누르세요"));
            return tip;
        }
        tip.addAll(Screen.getTooltipFromItem(minecraft, st));
        tip.removeIf(c -> c.getString().contains("우클릭: 판의 빈 칸에"));
        int en = ClientRelics.enhance(i);
        if (b.usable()[i]) tip.add(lit("§b이 칸의 스탯 줄: §a" + Relics.lineText(stat, Relics.lineValue(stat, r.gradeIndex(), en))));
        else tip.add(lit("§c착용 레벨이 모자라 스탯 · 연결 · 패시브 없음 §7(" + r.level() + "렙)"));
        Map<String, Integer> gs = groupsOf(b, i);
        Relics.Type t = r.def();
        if (t != null && b.usable()[i]) {
            for (String s : t.stats()) {
                int size = gs.getOrDefault(s, 1);
                int tier = Relics.tierOf(size);
                int need = Relics.nextNeed(size);
                tip.add(lit(Relics.statCode(s) + Relics.statName(s) + " §7묶음 §f" + size + "칸" + (tier > 0 ? " §6" + tier + "단계" : "")
                        + (need > 0 ? " §8(다음 " + need + "칸)" : " §6(최고)")));
            }
        }
        tip.add(lit("§8누르기: 스탯 줄 고르기 · 끌기: 자리 바꾸기 · 오른쪽 클릭: 빼기"));
        return tip;
    }

    private List<Component> bagTip(int inv, double level) {
        ItemStack st = invItem(inv);
        List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, st));
        tip.removeIf(c -> c.getString().contains("우클릭: 판의 빈 칸에"));
        Relics.Relic r = RelicItem.of(st);
        if (r != null) {
            int to = sel >= 0 ? sel : ClientRelics.firstEmpty();
            if (!Relics.usable(r, level)) tip.add(lit("§c" + r.level() + "렙부터 꽂을 수 있습니다"));
            else if (to >= 0) {
                String stat = ClientRelics.stat(to);
                tip.add(lit("§e누르면 " + (to + 1) + "번 칸에 §a" + Relics.lineText(stat, Relics.lineValue(stat, r.gradeIndex(), WeaponEnhance.of(st)))
                        + (ClientRelics.get(to).isEmpty() ? "" : " §7(꽂힌 것과 바꿈)")));
            } else tip.add(lit("§7판이 가득 찼습니다 — 바꿀 칸을 먼저 누르거나 끌어 놓으세요"));
        }
        return tip;
    }

    private static List<Component> passiveTip(PassiveRow row) {
        Relics.Type t = Relics.type(row.type());
        List<Component> tip = new ArrayList<>();
        tip.add(lit("§e§l「" + t.passive().name() + "」 §r§7" + t.name() + " · " + t.title()));
        tip.add(lit("§7계열 " + Relics.statsText(t)));
        if (row.tier() > 0) tip.add(lit("§6" + row.tier() + "단계 §f" + t.passive().text(row.tier())));
        else tip.add(lit("§8꺼짐 §7— " + t.passive().text(0)));
        int need = Relics.nextNeed(row.best());
        tip.add(lit(need > 0 ? "§7가장 큰 묶음 §f" + Math.max(1, row.best()) + "칸 §8→ 다음 단계 " + need + "칸" : "§6최고 단계"));
        tip.add(lit("§8같은 종류가 여러 칸이면 가장 높은 단계 하나만"));
        return tip;
    }

    // ------------------------------------------------------------ 글자 · 색

    private static Component lit(String s) {
        return Component.literal(BedrockText.toJava(s));
    }

    /** 반 크기 글자 (right = 오른쪽 끝을 x 에) */
    private void small(GuiGraphicsExtractor g, String s, int x, int y, boolean right) {
        g.pose().pushMatrix();
        g.pose().translate(right ? x - font.width(s) * 0.5F : x, y);
        g.pose().scale(0.5F, 0.5F);
        g.text(font, s, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    /** 글자를 maxW 안에 (넓으면 가로로 줄임) */
    private void fitText(GuiGraphicsExtractor g, String s, int x, int y, int maxW) {
        String j = BedrockText.toJava(s);
        int w = font.width(j);
        if (w <= maxW || maxW <= 0) {
            g.text(font, j, x, y, 0xFFFFFFFF, true);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(maxW / (float) w, 1F);
        g.text(font, j, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    private static int shade(int rgb, double k) {
        int r = (int) (((rgb >> 16) & 255) * k);
        int gg = (int) (((rgb >> 8) & 255) * k);
        int b = (int) ((rgb & 255) * k);
        return (r << 16) | (gg << 8) | b;
    }

    private static int mixRgb(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gg = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bb = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (gg << 8) | bb;
    }

    private static String keyName() {
        return MmorpgClient.artifactKey == null ? "I" : MmorpgClient.artifactKey.getTranslatedKeyMessage().getString();
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int tab = GearTabs.at(font, left, top, mx, my);
        if (tab >= 0) {
            GearTabs.select(tab, GearTabs.RELICS);
            return true;
        }
        List<Integer> bag = bagSlots();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (sel >= 0) {
                if (in(closeButton(), mx, my)) {
                    sel = -1;
                    click();
                    return true;
                }
                if (ClientRelics.relic(sel) != null && in(removeButton(), mx, my)) {
                    ClientRelics.send("unequip", sel, 0, "");
                    click();
                    return true;
                }
                int k = statAt(mx, my);
                if (k >= 0) {
                    String key = Relics.lineKeys().get(k);
                    if (!key.equals(ClientRelics.stat(sel))) ClientRelics.send("stat", sel, 0, key);
                    click();
                    return true;
                }
            }
            int c = cellAt(mx, my);
            int inv = invAt(mx, my, bag);
            if (c >= 0) dragFrom = c;
            else if (inv >= 0) dragFrom = 1000 + inv;
            else {
                if (inBoard(mx, my)) sel = -1;
                return super.mouseClicked(event, doubleClick);
            }
            pressX = mx;
            pressY = my;
            dragging = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            int c = cellAt(mx, my);
            if (c >= 0 && !ClientRelics.get(c).isEmpty()) {
                ClientRelics.send("unequip", c, 0, "");
                return true;
            }
            int inv = invAt(mx, my, bag);
            if (inv >= 0) {
                equipAuto(inv);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragFrom >= 0 && !dragging && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) dragging = true;
        return dragFrom >= 0 || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || dragFrom < 0) return super.mouseReleased(event);
        double mx = event.x();
        double my = event.y();
        int from = dragFrom;
        boolean moved = dragging || Math.hypot(mx - pressX, my - pressY) > 3;
        dragFrom = -1;
        dragging = false;
        int to = cellAt(mx, my);
        if (moved) {
            if (from >= 1000) {
                if (to >= 0) equipTo(from - 1000, to);
                else if (inBoard(mx, my)) equipAuto(from - 1000);
            } else if (to >= 0) {
                if (to != from && !(ClientRelics.get(from).isEmpty() && ClientRelics.get(to).isEmpty())) {
                    ClientRelics.send("move", from, to, "");
                    if (sel == from) sel = to;
                    else if (sel == to) sel = from;
                    click();
                }
            } else if (!inBoard(mx, my) && !inPanel(mx, my) && !ClientRelics.get(from).isEmpty()) {
                ClientRelics.send("unequip", from, 0, "");
            }
        } else if (from >= 1000) {
            equipAuto(from - 1000);
        } else {
            sel = sel == from ? -1 : from;
            click();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inGrid(mx, my) && sy != 0) {
            bagScroll = Math.max(0, bagScroll - (int) Math.signum(sy));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (MmorpgClient.artifactKey != null && MmorpgClient.artifactKey.matches(event) && Util.getMillis() - openedAt > 150) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** 가방 칸 inv 의 아티팩트를 판 칸 cell 에 (레벨을 먼저 봄 — 모자라면 알림만). 꽂혀 있던 것은 그 가방 칸으로 */
    private void equipTo(int inv, int cell) {
        Relics.Relic r = RelicItem.of(invItem(inv));
        if (r == null) return;
        if (!Relics.usable(r, ClientArtifacts.level())) {
            note("§c" + r.level() + "렙부터 꽂을 수 있습니다 §7(지금 " + Js.str(Math.floor(ClientArtifacts.level())) + ")");
            return;
        }
        ClientRelics.send("equip", inv, cell, "");
        hiddenInv = inv;
        hiddenUntil = Util.getMillis() + 500;
    }

    /** 고른 칸 (없으면 첫 빈 칸) 에 */
    private void equipAuto(int inv) {
        int to = sel >= 0 ? sel : ClientRelics.firstEmpty();
        if (to < 0) {
            note("§c판이 가득 찼습니다 §7— 바꿀 칸을 먼저 누르거나 끌어 놓으세요");
            return;
        }
        equipTo(inv, to);
    }

    private void note(String text) {
        note = text;
        noteAt = Util.getMillis();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.7F));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
