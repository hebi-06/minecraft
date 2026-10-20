package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mmorpg.core.Runes;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.rune.RuneItems;
import net.minecraft.client.KeyMapping;
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
 * 스킬 칸 창 (11단계 — 기본 J 키, 메이플 스킬 창처럼): 위에 스킬 칸 8 (키 글자 · 재사용 대기), 아래에 가방의 룬 (인벤토리 · 왼손).
 * 룬을 끌어 칸에 놓기 · 룬을 누르면 빈 칸에 · 룬 위에서 스킬 키를 누르면 그 칸에, 칸을 오른쪽 클릭 · 창 밖 (칸 줄 밖) 으로 끌면 빼기,
 * 칸끼리 끌면 바꾸기 · 칸 위에서 다른 스킬 키 = 그 칸으로 옮기기. 실제로 옮기는 것은 서버 (SkillBar) — 받은 칸으로 다시 그림. 게임은 멈추지 않음.
 * 0.26.0: 룬 칸 아래에 직업 스킬 칸 5 (Shift + 룬 칸 1 ~ 5 의 키 — 직업이 추가되면 직업 스킬이 들어감, 지금은 보기만).
 */
public final class SkillBarScreen extends Screen {
    public static final int W = 300;
    public static final int H = 238;
    /** 스킬 칸 (큰 칸) */
    static final int BIG = 26;
    static final int BIG_GAP = 6;
    static final int BAR_Y = 36;
    /** 가방의 룬 칸 */
    static final int CELL = 20;
    static final int COLS = 9;
    static final int ROWS = 4;
    static final int GRID_Y = 122;
    /** 0.26.0 직업 스킬 칸 줄 (작은 칸) */
    static final int JOB_Y = 84;
    static final int JOB_X = 64;
    static final int JOB_STEP = 26;
    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");

    private int left;
    private int top;
    /** 끌기: 시작한 곳 (-1 없음 · 0 ~ 7 스킬 칸 · 1000 + 가방 칸) */
    private int dragFrom = -1;
    private double pressX;
    private double pressY;
    private boolean dragging;
    /** 방금 올린 가방 칸 (서버가 칸을 보낼 때까지 그리드에서 감춤 — 잠깐 두 군데 보이지 않게) */
    private int hiddenInv = -1;
    private long hiddenUntil;
    /** 마지막으로 그린 때의 마우스 (키를 누를 때 — 그 아래의 룬 · 칸) */
    private int lastMx = -1;
    private int lastMy = -1;
    /** 창 아래 알림 (클라이언트 — 빈 칸 없음 등) */
    private String note = "";
    private long noteAt;

    public SkillBarScreen() {
        super(Component.literal("스킬 칸"));
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

    // ------------------------------------------------------------ 자리

    private int barX(int i) {
        int total = ClientSkillBar.SIZE * BIG + (ClientSkillBar.SIZE - 1) * BIG_GAP;
        return left + (W - total) / 2 + i * (BIG + BIG_GAP);
    }

    private int barY() {
        return top + BAR_Y;
    }

    private int gridX() {
        return left + (W - COLS * CELL) / 2;
    }

    private int gridY() {
        return top + GRID_Y;
    }

    /** 스킬 칸 i 가운데 (시험용 — GUI 좌표 {x, y}) */
    public int[] barCenter(int i) {
        return new int[] {barX(i) + BIG / 2, barY() + BIG / 2};
    }

    /** 가방의 룬 칸 (그리드 순서 n) 가운데 (시험용) */
    public int[] gridCenter(int n) {
        return new int[] {gridX() + (n % COLS) * CELL + CELL / 2, gridY() + (n / COLS) * CELL + CELL / 2};
    }

    private int jobX(int i) {
        return left + JOB_X + i * JOB_STEP;
    }

    private int jobY() {
        return top + JOB_Y;
    }

    /** 직업 스킬 칸 i 가운데 (시험용) */
    public int[] jobCenter(int i) {
        return new int[] {jobX(i) + SkillBarHud.S / 2, jobY() + SkillBarHud.S / 2};
    }

    /** 마우스 아래 직업 스킬 칸 (-1 없음) */
    private int jobAt(double mx, double my) {
        for (int i = 0; i < ClientJobBar.SIZE; i++) {
            int x = jobX(i);
            int y = jobY();
            if (mx >= x - 1 && mx < x + SkillBarHud.S + 1 && my >= y - 1 && my < y + SkillBarHud.S + 1) return i;
        }
        return -1;
    }

    /** 마우스 아래 스킬 칸 (-1 없음) */
    private int barAt(double mx, double my) {
        for (int i = 0; i < ClientSkillBar.SIZE; i++) {
            int x = barX(i);
            int y = barY();
            if (mx >= x - 2 && mx < x + BIG + 2 && my >= y - 2 && my < y + BIG + 2) return i;
        }
        return -1;
    }

    /** 스킬 칸 줄 근처인지 (끌어 놓기 — 줄 밖이면 빼기) */
    private boolean nearBar(double mx, double my) {
        return my >= barY() - 8 && my < barY() + BIG + 16 && mx >= barX(0) - 10 && mx < barX(ClientSkillBar.SIZE - 1) + BIG + 10;
    }

    /** 가방의 룬 (그리드 순서): 가방 칸 번호 (0 ~ 35 · 왼손 40) */
    public List<Integer> runeSlots() {
        List<Integer> out = new ArrayList<>();
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        if (p == null) return out;
        long now = Util.getMillis();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (i == hiddenInv && now < hiddenUntil) continue;
            if (RuneItems.runeOf(p.getInventory().getItem(i)) != null) out.add(i);
        }
        if (RuneItems.runeOf(p.getInventory().getItem(Inventory.SLOT_OFFHAND)) != null && !(hiddenInv == Inventory.SLOT_OFFHAND && now < hiddenUntil)) {
            out.add(Inventory.SLOT_OFFHAND);
        }
        return out;
    }

    /** 마우스 아래 가방의 룬 → 가방 칸 번호 (-1 없음) */
    private int invAt(double mx, double my, List<Integer> runes) {
        for (int n = 0; n < Math.min(runes.size(), COLS * ROWS); n++) {
            int x = gridX() + (n % COLS) * CELL;
            int y = gridY() + (n / COLS) * CELL;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return runes.get(n);
        }
        return -1;
    }

    private ItemStack invItem(int slot) {
        LocalPlayer p = minecraft.player;
        return p == null ? ItemStack.EMPTY : p.getInventory().getItem(slot);
    }

    // ------------------------------------------------------------ 그리기

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        lastMx = mx;
        lastMy = my;
        // 누른 채 3픽셀 넘게 움직였으면 끄는 중 (mouseDragged 가 오지 않는 입력에서도)
        if (dragFrom >= 0 && !dragging && Math.hypot(mx - pressX, my - pressY) > 3) dragging = true;
        long now = Util.getMillis();
        RpgHud.sprite(g, BG, left, top, W, H);
        g.text(font, "§6§l스킬 칸", left + 12, top + 11, 0xFFFFFFFF, true);
        String tip = "§7칸의 키를 누르면 시전 §8· §7키는 조작 설정";
        g.text(font, tip, left + W - 12 - font.width(tip), top + 11, 0xFFFFFFFF, true);
        g.fill(left + 10, top + 26, left + W - 10, top + 27, 0x90C8A050);

        // 스킬 칸 (큰 칸) + 아래 키 · 이름
        int hoverBar = dragging ? -1 : barAt(mx, my);
        int dropBar = dragging ? barAt(mx, my) : -1;
        for (int i = 0; i < ClientSkillBar.SIZE; i++) {
            int x = barX(i);
            int y = barY();
            boolean lifted = dragging && dragFrom == i;
            if (dropBar == i) g.fill(x - 3, y - 3, x + BIG + 3, y + BIG + 3, 0x70FFD24A);
            else if (hoverBar == i) g.fill(x - 2, y - 2, x + BIG + 2, y + BIG + 2, 0x40FFFFFF);
            if (lifted) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SkillBarHud.FRAME, x, y, BIG, BIG, 0x80FFFFFF);
            } else {
                SkillBarHud.drawSlot(g, font, i, x, y, BIG, partialTick, now, false, false);
            }
            String key = SkillBarHud.label(i < MmorpgClient.skillKeys.length ? MmorpgClient.skillKeys[i] : null);
            int kw = font.width(key);
            int kx = x + BIG / 2 - (kw + 6) / 2;
            int ky = y + BIG + 4;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SkillBarHud.KEY, kx, ky, kw + 6, 12, 0xFFFFFFFF);
            g.text(font, (ClientSkillBar.get(i).isEmpty() ? "§7" : "§e") + key, kx + 3, ky + 2, 0xFFFFFFFF, true);
        }

        // 0.26.0 직업 스킬 칸 (Shift + 룬 칸 1 ~ 5 의 키 — 지금은 보기만)
        int jy = jobY();
        g.text(font, ClientJobBar.any() ? "§b직업 스킬" : "§7직업 스킬", left + 12, jy + 6, 0xFFFFFFFF, true);
        int hoverJob = dragging ? -1 : jobAt(mx, my);
        for (int i = 0; i < ClientJobBar.SIZE; i++) {
            int x = jobX(i);
            if (hoverJob == i) g.fill(x - 2, jy - 2, x + SkillBarHud.S + 2, jy + SkillBarHud.S + 2, 0x40FFFFFF);
            SkillBarHud.drawJobSlot(g, font, i, x, jy, SkillBarHud.S, partialTick, now, false, true);
        }
        String jobNote = ClientJobBar.any() ? "§8Shift + 칸의 키" : "§8직업 추가 후 · Shift + 키";
        g.text(font, jobNote, left + W - 12 - font.width(jobNote), jy + 6, 0xFFFFFFFF, true);

        // 가방의 룬
        List<Integer> runes = runeSlots();
        int gy = gridY();
        g.fill(left + 10, gy - 15, left + W - 10, gy - 14, 0x60C8A050);
        g.text(font, "§e가진 룬 §7(" + runes.size() + "개 · 가방)", left + 12, gy - 11 - 1, 0xFFFFFFFF, true);
        String how = "§8누르면 빈 칸 · 끌어 놓기";
        g.text(font, how, left + W - 12 - font.width(how), gy - 12, 0xFFFFFFFF, true);
        int hoverInv = dragging ? -1 : invAt(mx, my, runes);
        for (int n = 0; n < COLS * ROWS; n++) {
            int x = gridX() + (n % COLS) * CELL;
            int y = gy + (n / COLS) * CELL;
            g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40000000);
            if (n >= runes.size()) continue;
            int slot = runes.get(n);
            ItemStack st = invItem(slot);
            boolean lifted = dragging && dragFrom == 1000 + slot;
            Runes.Rune r = RuneItems.runeOf(st);
            int c = SkillBarHud.gradeColor(r == null ? null : r.grade());
            g.fill(x + 1, y + CELL - 3, x + CELL - 1, y + CELL - 1, (0x90 << 24) | (c & 0xFFFFFF)); // 등급 색 밑줄
            if (hoverInv == slot) g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x50FFFFFF);
            if (!lifted) g.item(st, x + 2, y + 2);
            if (r != null && ClientSkillBar.indexOf(r.id()) >= 0) g.text(font, "§b●", x + CELL - 6, y, 0xFFFFFFFF, true); // 같은 룬이 칸에 있음
        }
        if (runes.isEmpty()) g.text(font, "§8가방에 룬이 없습니다 — 룬 뽑기권 · 룬술사", gridX() + 4, gy + 6, 0xFFFFFFFF, true);
        if (runes.size() > COLS * ROWS) g.text(font, "§8…", gridX() + COLS * CELL + 2, gy + ROWS * CELL - 9, 0xFFFFFFFF, true);

        // 도움말 · 알림
        int hy = top + H - 30;
        g.fill(left + 10, hy - 4, left + W - 10, hy - 3, 0x60C8A050);
        g.text(font, "§7끌어 놓기 §8· §7룬 위에서 칸의 키 = 그 칸에 §8· §7칸끼리 끌기 = 바꾸기", left + 12, hy, 0xFFFFFFFF, true);
        boolean noteOn = !note.isEmpty() && now - noteAt < 2500;
        g.text(font, noteOn ? note : "§7칸 오른쪽 클릭 · 줄 밖으로 끌기 = 빼기 §8(룬은 스킬 칸에서만 씀)", left + 12, hy + 11, 0xFFFFFFFF, true);

        // 끄는 그림 (마우스를 따라감)
        if (dragging && dragFrom >= 0) {
            ItemStack st = dragFrom >= 1000 ? invItem(dragFrom - 1000) : ClientSkillBar.get(dragFrom);
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
        if (hoverBar >= 0 && !ClientSkillBar.get(hoverBar).isEmpty()) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, ClientSkillBar.get(hoverBar)));
            lines.removeIf(c -> c.getString().contains(mmorpg.core.RuneTip.CONTROL)); // 이미 올린 룬 (조작 줄 — 키 안내로 바꿈)
            String key = SkillBarHud.label(MmorpgClient.skillKeys[hoverBar]);
            lines.add(Component.literal(BedrockText.toJava("§e[" + key + "] 키로 사용 §8(" + (hoverBar + 1) + "번 칸)")));
            lines.add(Component.literal(BedrockText.toJava("§8오른쪽 클릭: 빼기 · 끌어서 옮기기")));
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
        } else if (hoverBar >= 0) {
            String key = SkillBarHud.label(MmorpgClient.skillKeys[hoverBar]);
            g.setTooltipForNextFrame(font, List.of(Component.literal(BedrockText.toJava("§7빈 칸 §8(" + (hoverBar + 1) + "번 · [" + key + "] 키)")),
                    Component.literal(BedrockText.toJava("§8룬을 끌어 놓으세요"))), Optional.empty(), mx, my);
        } else if (hoverJob >= 0) {
            String key = ClientJobBar.keyLabel(hoverJob);
            List<Component> lines = new ArrayList<>();
            if (ClientJobBar.empty(hoverJob)) {
                lines.add(Component.literal(BedrockText.toJava("§7직업 스킬 " + (hoverJob + 1) + "번 칸 §8([" + key + "] — Shift + 키)")));
                lines.add(Component.literal(BedrockText.toJava("§8직업이 추가되면 직업 스킬이 들어갑니다")));
            } else {
                lines.add(Component.literal(BedrockText.toJava("§b" + ClientJobBar.name(hoverJob))));
                lines.add(Component.literal(BedrockText.toJava("§e[" + key + "] 키로 사용 §8(직업 스킬 " + (hoverJob + 1) + "번 칸)")));
            }
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
        } else if (hoverInv >= 0) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, invItem(hoverInv)));
            lines.add(Component.literal(BedrockText.toJava("§e누르면 빈 스킬 칸에 · 위에서 칸의 키 = 그 칸에")));
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
        }
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        List<Integer> runes = runeSlots();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int b = barAt(mx, my);
            int inv = invAt(mx, my, runes);
            if (b >= 0 && !ClientSkillBar.get(b).isEmpty()) dragFrom = b;
            else if (inv >= 0) dragFrom = 1000 + inv;
            else return super.mouseClicked(event, doubleClick);
            pressX = mx;
            pressY = my;
            dragging = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            int b = barAt(mx, my);
            if (b >= 0 && !ClientSkillBar.get(b).isEmpty()) {
                ClientSkillBar.send("unequip", b, 0);
                return true;
            }
            int inv = invAt(mx, my, runes);
            if (inv >= 0) {
                equipFirstEmpty(inv);
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
        int to = barAt(mx, my);
        if (moved) {
            if (from >= 1000) {
                if (to >= 0) equip(from - 1000, to);
            } else if (to >= 0) {
                if (to != from) {
                    ClientSkillBar.send("move", from, to);
                    click();
                }
            } else if (!nearBar(mx, my)) {
                ClientSkillBar.send("unequip", from, 0);
            }
        } else if (from >= 1000) {
            equipFirstEmpty(from - 1000);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (MmorpgClient.skillBarKey != null && MmorpgClient.skillBarKey.matches(event)) {
            onClose();
            return true;
        }
        int k = skillKeyOf(event);
        if (k >= 0) {
            int b = barAt(lastMx, lastMy);
            int inv = invAt(lastMx, lastMy, runeSlots());
            if (inv >= 0) {
                equip(inv, k);
                return true;
            }
            if (b >= 0 && b != k && !ClientSkillBar.get(b).isEmpty()) {
                ClientSkillBar.send("move", b, k);
                click();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** 누른 키가 몇 번 스킬 칸의 키인지 (-1 아님) */
    private static int skillKeyOf(KeyEvent event) {
        KeyMapping[] keys = MmorpgClient.skillKeys;
        for (int i = 0; i < keys.length; i++) if (keys[i] != null && keys[i].matches(event)) return i;
        return -1;
    }

    private void equip(int inv, int slot) {
        ClientSkillBar.send("equip", inv, slot);
        hiddenInv = inv;
        hiddenUntil = Util.getMillis() + 500;
    }

    private void equipFirstEmpty(int inv) {
        Runes.Rune r = RuneItems.runeOf(invItem(inv));
        int same = r == null ? -1 : ClientSkillBar.indexOf(r.id());
        int slot = same >= 0 ? same : ClientSkillBar.firstEmpty();
        if (slot < 0) {
            note("§c빈 스킬 칸이 없습니다 §7— 칸에 끌어 놓으면 바꿉니다");
            return;
        }
        equip(inv, slot);
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
