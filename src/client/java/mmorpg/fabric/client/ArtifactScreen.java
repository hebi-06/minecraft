package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mmorpg.core.Artifacts;
import mmorpg.core.Js;
import mmorpg.core.StatTree;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.rpgui.RpgUi;
import mmorpg.fabric.gear.ArtifactItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
 * 0.37.0 아티팩트 창 (기본 I 키 · 인벤토리 패널의 "아티팩트" 단추 · /아티팩트 — 사용자가 고른 "따로 아티팩트 창: 키 하나로 여는 전용 창에 칸 5개 · 합계 옵션"):
 * <ul>
 * <li>왼쪽 무대: 내 모습 (마우스를 따라 봄) · 마법진 · 둘레에 칸 다섯 — 왼쪽 귀걸이 · 목걸이 · 팔찌, 오른쪽 반지 1 · 반지 2. 빈 칸은 그 종류의 흐린 모양,
 * 낀 칸은 등급 색 테 (영웅 · 초월은 숨 쉬는 빛 · 도는 빛 한 점), 착용 레벨이 모자라면 빨갛게.</li>
 * <li>오른쪽: 합계 옵션 여덟 줄 (공격력 · 치명타 확률 · 치명타 피해 · 룬 피해 · 재사용 대기 · 최대 체력 · 받는 피해 · 이동 속도 — 바뀐 줄은 잠깐 초록 빛).</li>
 * <li>아래: 가방의 아티팩트 (인벤토리 · 왼손) — 누르면 맞는 칸에 (반지는 빈 반지 칸, 둘 다 차 있으면 낮은 등급과 바꿈), 칸에 끌어 놓기,
 * 칸끼리 끌기 = 반지 1 ↔ 반지 2, 칸 오른쪽 클릭 · 무대 밖으로 끌기 = 빼기. 실제로 옮기는 것은 서버 (ArtifactSystem) — 받은 칸으로 다시 그림.</li>
 * </ul>
 * 게임은 멈추지 않음.
 */
public final class ArtifactScreen extends Screen {
    public static final int W = 300;
    public static final int H = 236;
    /** 무대 (사람 그림 · 칸 다섯) */
    static final int STAGE_X = 10;
    static final int STAGE_Y = 30;
    static final int STAGE_W = 146;
    static final int STAGE_H = 88;
    /** 칸 크기 · 칸 자리 (창 기준 — 칸 순서: 반지 1 · 반지 2 · 목걸이 · 귀걸이 · 팔찌) */
    static final int S = 24;
    static final int[][] SLOT_XY = {{124, 48}, {124, 76}, {18, 62}, {18, 34}, {18, 90}};
    /** 합계 옵션 칸 */
    static final int SUM_X = 162;
    static final int SUM_W = 128;
    static final int LINE_H = 9;
    /** 합계 옵션 줄 순서 (공격 → 방어 · 이동) */
    static final List<String> KINDS = List.of("atk", "crit", "critDmg", "rune", "cdr", "hp", "def", "speed");
    /** 가방의 아티팩트 칸 */
    static final int CELL = 20;
    static final int COLS = 9;
    static final int ROWS = 3;
    static final int GRID_Y = 138;
    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier STAGE = Mmorpg.id("artifact/stage");
    private static final Identifier SLOT = Mmorpg.id("artifact/slot");

    private int left;
    private int top;
    /** 끌기: 시작한 곳 (-1 없음 · 0 ~ 4 칸 · 1000 + 가방 칸) */
    private int dragFrom = -1;
    private double pressX;
    private double pressY;
    private boolean dragging;
    /** 방금 낀 가방 칸 (서버가 칸을 보낼 때까지 감춤) */
    private int hiddenInv = -1;
    private long hiddenUntil;
    /** 창 아래 알림 (클라이언트 — 레벨 · 종류가 맞지 않음 등) */
    private String note = "";
    private long noteAt;
    private final long openedAt = Util.getMillis();

    public ArtifactScreen() {
        super(Component.literal("아티팩트"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(0, (height - H) / 2);
        ClientArtifacts.send("sync", 0, 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------ 자리 (시험용 — GUI 좌표)

    private int slotX(int i) {
        return left + SLOT_XY[i][0];
    }

    private int slotY(int i) {
        return top + SLOT_XY[i][1];
    }

    /** 칸 i 가운데 (시험용) */
    public int[] slotCenter(int i) {
        return new int[] {slotX(i) + S / 2, slotY(i) + S / 2};
    }

    private int gridX() {
        return left + (W - COLS * CELL) / 2;
    }

    private int gridY() {
        return top + GRID_Y;
    }

    /** 가방의 아티팩트 (그리드 순서 n) 가운데 (시험용) */
    public int[] gridCenter(int n) {
        return new int[] {gridX() + (n % COLS) * CELL + CELL / 2, gridY() + (n / COLS) * CELL + CELL / 2};
    }

    /** 무대 가운데 (시험용 — 사람 그림) */
    public int[] stageCenter() {
        return new int[] {left + STAGE_X + STAGE_W / 2, top + STAGE_Y + STAGE_H / 2};
    }

    /** 합계 줄 i 가운데 (시험용) */
    public int[] sumCenter(int i) {
        return new int[] {left + SUM_X + SUM_W / 2, top + 46 + i * LINE_H + 4};
    }

    /** 마우스 아래 칸 (-1 없음) */
    private int slotAt(double mx, double my) {
        for (int i = 0; i < ClientArtifacts.SIZE; i++) {
            int x = slotX(i);
            int y = slotY(i);
            if (mx >= x - 2 && mx < x + S + 2 && my >= y - 2 && my < y + S + 2) return i;
        }
        return -1;
    }

    private boolean inStage(double mx, double my) {
        return mx >= left + STAGE_X && mx < left + STAGE_X + STAGE_W && my >= top + STAGE_Y && my < top + STAGE_Y + STAGE_H;
    }

    /** 가방의 아티팩트 (그리드 순서): 가방 칸 번호 (0 ~ 35 · 왼손 40) — 높은 등급 · 반지 → 팔찌 순 */
    public List<Integer> bagSlots() {
        List<Integer> out = new ArrayList<>();
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        if (p == null) return out;
        long now = Util.getMillis();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (i == hiddenInv && now < hiddenUntil) continue;
            if (ArtifactItem.of(p.getInventory().getItem(i)) != null) out.add(i);
        }
        if (ArtifactItem.of(p.getInventory().getItem(Inventory.SLOT_OFFHAND)) != null && !(hiddenInv == Inventory.SLOT_OFFHAND && now < hiddenUntil)) {
            out.add(Inventory.SLOT_OFFHAND);
        }
        List<String> order = Artifacts.types();
        out.sort((a, b) -> {
            Artifacts.Art x = ArtifactItem.of(invItem(a));
            Artifacts.Art y = ArtifactItem.of(invItem(b));
            if (x.gradeIndex() != y.gradeIndex()) return y.gradeIndex() - x.gradeIndex();
            return order.indexOf(x.type()) - order.indexOf(y.type());
        });
        return out;
    }

    /** 마우스 아래 가방의 아티팩트 → 가방 칸 번호 (-1 없음) */
    private int invAt(double mx, double my, List<Integer> list) {
        for (int n = 0; n < Math.min(list.size(), COLS * ROWS); n++) {
            int x = gridX() + (n % COLS) * CELL;
            int y = gridY() + (n / COLS) * CELL;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return list.get(n);
        }
        return -1;
    }

    private ItemStack invItem(int slot) {
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        return p == null ? ItemStack.EMPTY : p.getInventory().getItem(slot);
    }

    /** 이 아티팩트가 저절로 들어갈 칸 (서버 ArtifactSystem.autoSlot 과 같음 — 빈 칸, 아니면 등급이 가장 낮은 칸) */
    static int autoSlot(Artifacts.Art a) {
        List<Integer> where = Artifacts.slotsFor(a.type());
        if (where.isEmpty()) return -1;
        for (int i : where) if (ClientArtifacts.get(i).isEmpty()) return i;
        int best = where.get(0);
        int bestGrade = Integer.MAX_VALUE;
        for (int i : where) {
            Artifacts.Art o = ClientArtifacts.art(i);
            int g = o == null ? -1 : o.gradeIndex();
            if (g < bestGrade) {
                best = i;
                bestGrade = g;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ 그리기

    private static int gradeRgb(Artifacts.Art a) {
        return RpgUi.gradeRgb(a == null ? 0 : a.gradeIndex());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        if (dragFrom >= 0 && !dragging && Math.hypot(mx - pressX, my - pressY) > 3) dragging = true;
        long now = Util.getMillis();
        double level = ClientArtifacts.level();
        RpgHud.sprite(g, BG, left, top, W, H);
        g.text(font, "§d§l아티팩트", left + 12, top + 11, 0xFFFFFFFF, true);
        String head = "§7낀 칸 §f" + ClientArtifacts.worn() + "§7/" + ClientArtifacts.SIZE + "  §8[" + keyName() + "]";
        g.text(font, head, left + W - 12 - font.width(head), top + 11, 0xFFFFFFFF, true);
        g.fill(left + 10, top + 26, left + W - 10, top + 27, 0x90C8A050);

        // 무대 + 내 모습
        g.blitSprite(RenderPipelines.GUI_TEXTURED, STAGE, left + STAGE_X, top + STAGE_Y, STAGE_W, STAGE_H);
        int hover = dragging ? -1 : slotAt(mx, my);
        int drop = dragging ? slotAt(mx, my) : -1;
        Artifacts.Art lifted = dragging && dragFrom >= 1000 ? ArtifactItem.of(invItem(dragFrom - 1000)) : dragging && dragFrom >= 0 ? ClientArtifacts.art(dragFrom) : null;
        for (int i = 0; i < ClientArtifacts.SIZE; i++) {
            boolean target = lifted != null && Artifacts.fits(i, lifted) && !(dragFrom == i);
            drawSlot(g, i, now, level, hover == i, drop == i, target, dragging && dragFrom == i);
        }
        g.nextStratum();
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, left + 56, top + 33, left + 110, top + 113, 33, 0.0625F, mx, my, player);
        g.nextStratum();

        // 합계 옵션
        int sx = left + SUM_X;
        int sy = top + STAGE_Y;
        g.fill(sx, sy, sx + SUM_W, sy + STAGE_H, 0x70000000);
        g.outline(sx, sy, SUM_W, STAGE_H, 0x90C8A050);
        g.text(font, "§e§l합계 옵션", sx + 5, sy + 4, 0xFFFFFFFF, true);
        Map<String, Double> sum = ClientArtifacts.totals();
        int sumHover = -1;
        for (int i = 0; i < KINDS.size(); i++) {
            String k = KINDS.get(i);
            StatTree.Kind kind = StatTree.kind(k);
            int ly = top + 46 + i * LINE_H;
            double v = sum.getOrDefault(k, 0.0);
            Long ch = ClientArtifacts.CHANGED_AT.get(k);
            if (ch != null && now - ch < 900) {
                int a = (int) (0x80 * (1 - (now - ch) / 900.0));
                g.fill(sx + 2, ly - 1, sx + SUM_W - 2, ly + LINE_H - 1, (a << 24) | 0x7CFF8A);
            }
            if (mx >= sx && mx < sx + SUM_W && my >= ly - 1 && my < ly + LINE_H - 1 && !dragging) {
                sumHover = i;
                g.fill(sx + 2, ly - 1, sx + SUM_W - 2, ly + LINE_H - 1, 0x22FFFFFF);
            }
            ItemStack icon = kindIcon(k);
            if (!icon.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(sx + 5, ly - 0.5F);
                g.pose().scale(0.5F, 0.5F);
                g.item(icon, 0, 0);
                g.pose().popMatrix();
            }
            String name = kind == null ? k : kind.name();
            g.text(font, (v != 0 ? "§f" : "§8") + name, sx + 16, ly, 0xFFFFFFFF, true);
            String val = v != 0 ? "§a" + StatTree.bonusText(k, v).substring(name.length() + 1) : "§8—";
            g.text(font, val, sx + SUM_W - 5 - font.width(val), ly, 0xFFFFFFFF, true);
        }

        // 가방의 아티팩트
        List<Integer> bag = bagSlots();
        int gy = gridY();
        g.fill(left + 10, top + 121, left + W - 10, top + 122, 0x60C8A050);
        g.text(font, "§e가진 아티팩트 §7(" + bag.size() + "개 · 가방)", left + 12, top + 126, 0xFFFFFFFF, true);
        String how = "§8누르면 맞는 칸 · 끌어 놓기";
        g.text(font, how, left + W - 12 - font.width(how), top + 126, 0xFFFFFFFF, true);
        int hoverInv = dragging ? -1 : invAt(mx, my, bag);
        for (int n = 0; n < COLS * ROWS; n++) {
            int x = gridX() + (n % COLS) * CELL;
            int y = gy + (n / COLS) * CELL;
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40000000);
            if (n >= bag.size()) continue;
            int slot = bag.get(n);
            ItemStack st = invItem(slot);
            Artifacts.Art a = ArtifactItem.of(st);
            int c = gradeRgb(a);
            g.fill(x + 1, y + CELL - 3, x + CELL - 1, y + CELL - 1, (0x90 << 24) | c);
            if (hoverInv == slot) g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x50FFFFFF);
            if (!(dragging && dragFrom == 1000 + slot)) g.item(st, x + 2, y + 2);
            if (a != null && !Artifacts.usable(a, level)) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x50FF2020);
                small(g, "§c" + a.level(), x + CELL - 2, y + 1, true);
            }
        }
        if (bag.isEmpty()) g.text(font, "§8가방에 아티팩트가 없습니다 — 대장장이에게서 제작", gridX() + 4, gy + 6, 0xFFFFFFFF, true);
        if (bag.size() > COLS * ROWS) g.text(font, "§8…", gridX() + COLS * CELL + 2, gy + ROWS * CELL - 9, 0xFFFFFFFF, true);

        // 도움말 · 알림
        int hy = top + H - 30;
        g.fill(left + 10, hy - 4, left + W - 10, hy - 3, 0x60C8A050);
        g.text(font, "§7끌어 놓기 §8· §7누르면 맞는 칸 §8· §7칸끼리 끌기 = 반지 바꾸기", left + 12, hy, 0xFFFFFFFF, true);
        boolean noteOn = !note.isEmpty() && now - noteAt < 2600;
        g.text(font, noteOn ? note : "§7칸 오른쪽 클릭 · 무대 밖으로 끌기 = 빼기 §8(제작: 대장장이)", left + 12, hy + 11, 0xFFFFFFFF, true);

        // 끄는 그림
        if (dragging && dragFrom >= 0) {
            ItemStack st = dragFrom >= 1000 ? invItem(dragFrom - 1000) : ClientArtifacts.get(dragFrom);
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
        if (hover >= 0) {
            ItemStack st = ClientArtifacts.get(hover);
            tip = new ArrayList<>();
            if (!st.isEmpty()) {
                tip.addAll(Screen.getTooltipFromItem(minecraft, st));
                tip.removeIf(c -> c.getString().contains("우클릭: 끼기"));
                Artifacts.Art a = ClientArtifacts.art(hover);
                if (a != null && !Artifacts.usable(a, level)) tip.add(lit("§c착용 레벨이 모자라 보너스 없음"));
                tip.add(lit("§8" + Artifacts.slotName(hover) + " 칸 · 오른쪽 클릭: 빼기 · 끌어서 옮기기"));
            } else {
                Artifacts.Type t = Artifacts.type(Artifacts.slotType(hover));
                tip.add(lit("§d" + Artifacts.slotName(hover) + " §7(빈 칸)"));
                if (t != null) {
                    tip.add(lit("§7" + t.name() + " 옵션: §f" + String.join(" · ", optionNames(t))));
                    tip.add(lit("§8가방의 " + t.name() + "을 끌어 놓거나 누르세요"));
                }
            }
        } else if (hoverInv >= 0) {
            ItemStack st = invItem(hoverInv);
            tip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, st));
            tip.removeIf(c -> c.getString().contains("우클릭: 끼기"));
            Artifacts.Art a = ArtifactItem.of(st);
            if (a != null) {
                int to = autoSlot(a);
                if (!Artifacts.usable(a, level)) tip.add(lit("§c" + a.level() + "렙부터 낄 수 있습니다"));
                else if (to >= 0) tip.add(lit("§e누르면 " + Artifacts.slotName(to) + " 칸에" + (ClientArtifacts.get(to).isEmpty() ? "" : " §7(낀 것과 바꿈)")));
            }
        } else if (sumHover >= 0) {
            StatTree.Kind kind = StatTree.kind(KINDS.get(sumHover));
            if (kind != null) {
                tip = new ArrayList<>();
                tip.add(lit("§e" + kind.name()));
                if (!kind.desc().isEmpty()) tip.add(lit("§7" + kind.desc()));
                List<String> from = new ArrayList<>();
                for (String t : Artifacts.types()) if (Artifacts.type(t).bonus().containsKey(kind.key())) from.add(Artifacts.type(t).name());
                if (!from.isEmpty()) tip.add(lit("§8올려 주는 것: " + String.join(" · ", from)));
                tip.add(lit("§8스탯 트리 보너스에 더함 (상한 같음)"));
            }
        }
        if (tip != null && !tip.isEmpty()) g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
    }

    private static final java.util.Map<String, ItemStack> ICONS = new java.util.HashMap<>();

    /** 보너스 종류의 그림 (stat_tree.json kinds.icon — 없으면 EMPTY) */
    static ItemStack kindIcon(String k) {
        return ICONS.computeIfAbsent(k, key -> {
            StatTree.Kind kind = StatTree.kind(key);
            net.minecraft.world.item.Item it = kind == null ? null : mmorpg.fabric.ui.Icon.itemById(kind.icon());
            return it == null ? ItemStack.EMPTY : new ItemStack(it);
        });
    }

    private static List<String> optionNames(Artifacts.Type t) {
        List<String> out = new ArrayList<>();
        for (String k : t.bonus().keySet()) {
            StatTree.Kind kind = StatTree.kind(k);
            out.add(kind == null ? k : kind.name());
        }
        return out;
    }

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

    /** 칸 하나: 받침 · 등급 빛 · 그림 (빈 칸은 흐린 모양) · 낌 · 뺌 고리 · 끌어 놓을 수 있는 칸 표시 */
    private void drawSlot(GuiGraphicsExtractor g, int i, long now, double level, boolean hover, boolean drop, boolean target, boolean liftedFrom) {
        int x = slotX(i);
        int y = slotY(i);
        int cx = x + S / 2;
        int cy = y + S / 2;
        ItemStack st = ClientArtifacts.get(i);
        Artifacts.Art a = ClientArtifacts.art(i);
        if (drop) g.fill(x - 3, y - 3, x + S + 3, y + S + 3, target ? 0x70FFD24A : 0x60FF4040);
        else if (target) g.fill(x - 2, y - 2, x + S + 2, y + S + 2, (int) (0x30 + 0x28 * Math.sin(now / 160.0)) << 24 | 0xFFD24A);
        else if (hover) g.fill(x - 2, y - 2, x + S + 2, y + S + 2, 0x40FFFFFF);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, S, S);
        if (a != null && !liftedFrom) {
            int rgb = gradeRgb(a);
            boolean high = a.gradeIndex() >= 3;
            g.fillGradient(x + 3, y + S / 2, x + S - 3, y + S - 3, rgb & 0xFFFFFF, (high ? 0x60 : 0x40) << 24 | rgb);
            int alpha = high ? (int) (0xB0 + 0x4F * Math.sin(now / (a.gradeIndex() == 4 ? 240.0 : 360.0))) : 0xD0;
            g.outline(x + 2, y + 2, S - 4, S - 4, (Math.max(0, Math.min(255, alpha)) << 24) | rgb);
            if (a.gradeIndex() == 4) spark(g, x + 2, y + 2, S - 4, now, 1500, rgb);
        } else {
            String t = Artifacts.slotType(i);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Mmorpg.id("artifact/ghost_" + t), x + 4, y + 4, 16, 16, 0x50FFFFFF);
        }
        float eq = SkillBarHud.phase(ClientArtifacts.EQUIP_AT[i], now, 520);
        if (eq >= 0) SkillBarHud.ring(g, cx, cy, S * (0.5F + 0.6F * eq), a == null ? 0xFFD24A : gradeRgb(a), 1 - eq);
        float rm = SkillBarHud.phase(ClientArtifacts.REMOVE_AT[i], now, 380);
        if (rm >= 0) SkillBarHud.ring(g, cx, cy, S * (0.6F - 0.25F * rm), 0xB0B0B0, 0.7F * (1 - rm));
        if (!st.isEmpty() && !liftedFrom) {
            float k = eq >= 0 ? 1.0F + 0.3F * (1 - eq) * (1 - eq) : 1.0F;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(k, k);
            g.item(st, -8, -8);
            g.pose().popMatrix();
            if (a != null && !Artifacts.usable(a, level)) {
                g.fill(x + 3, y + 3, x + S - 3, y + S - 3, 0x55FF2020);
                small(g, "§c" + a.level() + "렙", x + S - 2, y + S - 6, true);
            }
        }
    }

    /** 테두리를 도는 빛 (초월 — RpgUi.spark 처럼, 등급 색이 섞인 흰 빛) */
    private static void spark(GuiGraphicsExtractor g, int x, int y, int size, long ms, int period, int rgb) {
        int perim = (size - 1) * 4;
        int p = (int) ((ms % period) * perim / period);
        for (int k = 0; k < 4; k++) {
            int q = Math.floorMod(p - k, perim);
            int side = q / (size - 1);
            int o = q % (size - 1);
            int px;
            int py;
            switch (side) {
                case 0 -> {
                    px = x + o;
                    py = y;
                }
                case 1 -> {
                    px = x + size - 1;
                    py = y + o;
                }
                case 2 -> {
                    px = x + size - 1 - o;
                    py = y + size - 1;
                }
                default -> {
                    px = x;
                    py = y + size - 1 - o;
                }
            }
            int a = 0xFF - k * 0x38;
            g.fill(px, py, px + 1, py + 1, (a << 24) | (k == 0 ? 0xFFFFFF : rgb));
        }
    }

    private static String keyName() {
        return MmorpgClient.artifactKey == null ? "I" : MmorpgClient.artifactKey.getTranslatedKeyMessage().getString();
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        List<Integer> bag = bagSlots();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int s = slotAt(mx, my);
            int inv = invAt(mx, my, bag);
            if (s >= 0 && !ClientArtifacts.get(s).isEmpty()) dragFrom = s;
            else if (inv >= 0) dragFrom = 1000 + inv;
            else return super.mouseClicked(event, doubleClick);
            pressX = mx;
            pressY = my;
            dragging = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            int s = slotAt(mx, my);
            if (s >= 0 && !ClientArtifacts.get(s).isEmpty()) {
                ClientArtifacts.send("unequip", s, 0);
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
        int to = slotAt(mx, my);
        if (moved) {
            if (from >= 1000) {
                if (to >= 0) equipTo(from - 1000, to);
                else if (inStage(mx, my)) equipAuto(from - 1000);
            } else if (to >= 0) {
                if (to != from) {
                    if (Artifacts.slotType(to).equals(Artifacts.slotType(from))) {
                        ClientArtifacts.send("move", from, to);
                        click();
                    } else {
                        note("§c" + Artifacts.slotName(to) + " 칸에는 " + Artifacts.type(Artifacts.slotType(to)).name() + "만 낄 수 있습니다");
                    }
                }
            } else if (!inStage(mx, my)) {
                ClientArtifacts.send("unequip", from, 0);
            }
        } else if (from >= 1000) {
            equipAuto(from - 1000);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (MmorpgClient.artifactKey != null && MmorpgClient.artifactKey.matches(event) && Util.getMillis() - openedAt > 150) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** 가방 칸 inv 의 아티팩트를 칸 slot 에 (종류 · 레벨을 먼저 봄 — 맞지 않으면 알림만) */
    private void equipTo(int inv, int slot) {
        Artifacts.Art a = ArtifactItem.of(invItem(inv));
        if (a == null) return;
        if (!Artifacts.fits(slot, a)) {
            note("§c" + Artifacts.slotName(slot) + " 칸에는 " + Artifacts.type(Artifacts.slotType(slot)).name() + "만 낄 수 있습니다 §7(" + a.typeName() + ")");
            return;
        }
        if (!Artifacts.usable(a, ClientArtifacts.level())) {
            note("§c" + a.level() + "렙부터 낄 수 있습니다 §7(지금 " + Js.str(Math.floor(ClientArtifacts.level())) + ")");
            return;
        }
        ClientArtifacts.send("equip", inv, slot);
        hiddenInv = inv;
        hiddenUntil = Util.getMillis() + 500;
    }

    /** 맞는 칸에 저절로 (반지: 빈 반지 칸 → 낮은 등급과 바꿈) */
    private void equipAuto(int inv) {
        Artifacts.Art a = ArtifactItem.of(invItem(inv));
        if (a == null) return;
        int slot = autoSlot(a);
        if (slot < 0) return;
        equipTo(inv, slot);
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
