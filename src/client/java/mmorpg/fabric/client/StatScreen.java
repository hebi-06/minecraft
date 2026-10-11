package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.StatSheet;
import mmorpg.fabric.mount.MountDefs;
import mmorpg.fabric.mount.MountItem;
import mmorpg.fabric.mount.MountItems;
import mmorpg.fabric.mount.Riding;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 스탯 창 (statui.js 의 자바 판): 남은 포인트, 힘 · 민첩 · 마력 · 체력 (줄을 누르면 +1, Shift 는 +5), 스탯마다 효과,
 * 가진 무기 기준 실제 피해 (기준 무기). 애드온과 같은 그림 (금테 창 · 스탯 아이콘). 게임은 멈추지 않음.
 * 아랫줄 (6단계 — 애드온 상자 창의 아랫줄): 퀘스트 목록 · 엔더 상자 (내 엔더 상자 27칸) · 탈것 (누르면 고른 탈것을 불러 탐 · 타고 있으면 돌려보냄,
 * Shift + 누르면 탈것 고르기 창).
 * 스탯 줄마다 나무 칸 (7단계 스탯 트리 — "나무 3/10", 고를 갈래가 있으면 깜빡임): 누르면 그 스탯의 스탯 트리 창.
 */
public final class StatScreen extends Screen {
    public static final int W = 300;
    public static final int H = 234;
    public static final int ROW_Y = 32;
    public static final int ROW_H = 27;
    /** 기준 무기 줄 간격 · 최대 줄 수 */
    static final int LINE_H = 9;
    static final int MAX_LINES = 6;
    /** 아랫줄 단추 (위치 = 창 위에서 · 높이 · 사이 간격) */
    public static final int BTN_Y = 204;
    public static final int BTN_H = 23;
    static final int BTN_GAP = 6;
    /** 아랫줄 단추 이름 (서버에 보내는 할 일) */
    public static final List<String> BUTTONS = List.of("quest", "ender", "mount");
    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier BTN = Mmorpg.id("stats/dialog_btn");
    private static final Identifier BTN_HOVER = Mmorpg.id("stats/dialog_btn_hover");
    private static final Identifier POINT = Mmorpg.id("hud/icon_point");
    private static final Identifier ENDER = Mmorpg.id("ui/ender_chest");
    private static final Identifier MOUNT = Mmorpg.id("ui/icon_mount");
    /** 스탯 줄의 나무 칸 (+1 단추 왼쪽 · 첫 줄) */
    public static final int TREE_W = 66;
    public static final int TREE_H = 11;

    private int left;
    private int top;
    /** 값이 오른 줄을 잠깐 빛나게 (스탯 → 오른 시각) */
    private final Map<String, Long> flashAt = new HashMap<>();
    private final Map<String, Integer> lastValue = new HashMap<>();

    public StatScreen() {
        super(Component.literal("스탯"));
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

    private static Identifier icon(String key) {
        return Mmorpg.id("stats/icon_" + key);
    }

    /** 줄 i 의 영역 안인지 */
    private boolean inRow(int i, double mx, double my) {
        int y = top + ROW_Y + i * ROW_H;
        return mx >= left + 10 && mx < left + W - 10 && my >= y && my < y + ROW_H - 2;
    }

    /** 줄 i 의 +1 단추 가운데 (시험용 — 화면 좌표) */
    public int buttonX() {
        return left + W - 40 + 13;
    }

    public int rowY(int i) {
        return top + ROW_Y + i * ROW_H + 12;
    }

    // ------------------------------------------------------------ 나무 칸 (스탯 트리)

    private int treeX() {
        return left + W - 40 - 6 - TREE_W;
    }

    private int treeY(int i) {
        return top + ROW_Y + i * ROW_H + 2;
    }

    /** 줄 i 의 나무 칸 안인지 (스탯 트리를 쓰는 서버만) */
    private boolean inTree(int i, double mx, double my) {
        if (!ClientRpgState.treeOn()) return false;
        int x = treeX();
        int y = treeY(i);
        return mx >= x && mx < x + TREE_W && my >= y && my < y + TREE_H;
    }

    /** 줄 i 의 나무 칸 가운데 (시험용 — 화면 좌표 {x, y}) */
    public int[] treeCenter(int i) {
        return new int[] {treeX() + TREE_W / 2, treeY(i) + TREE_H / 2};
    }

    private static final ItemStack SAPLING = new ItemStack(Items.OAK_SAPLING);

    /** 나무 칸 그리기: 묘목 + "나무 3/10" (고를 갈래가 있으면 "갈래 고르기!" 깜빡임) */
    private void drawTree(GuiGraphicsExtractor g, String key, int i, boolean over, long now) {
        int x = treeX();
        int y = treeY(i);
        int v = ClientRpgState.spent(key);
        int tier = mmorpg.core.StatTree.tier(key, v);
        int size = mmorpg.core.StatTree.tree(key) == null ? 0 : mmorpg.core.StatTree.tree(key).nodes().size();
        boolean pending = !mmorpg.core.StatTree.pending(key, v, ClientRpgState.treeState().picksOf(key)).isEmpty();
        g.fill(x, y, x + TREE_W, y + TREE_H, over ? 0x50FFFFFF : pending && (now / 400) % 2 == 0 ? 0x50FFD24A : 0x30000000);
        g.pose().pushMatrix();
        g.pose().translate(x + 1, y + 1);
        g.pose().scale(0.5625F, 0.5625F);
        g.item(SAPLING, 0, 0);
        g.pose().popMatrix();
        String label = pending ? ((now / 400) % 2 == 0 ? "§e§l갈래 고르기!" : "§6§l갈래 고르기!") : "§7나무 " + Stats.info(key).str("color") + tier + "§7/" + size;
        g.text(font, BedrockText.toJava(label), x + 12, y + 2, 0xFFFFFFFF, true);
    }

    private List<String> treeTip(String key) {
        int v = ClientRpgState.spent(key);
        mmorpg.core.StatTree.Tree t = mmorpg.core.StatTree.tree(key);
        List<String> tip = new ArrayList<>();
        if (t == null) return tip;
        String name = Stats.info(key).str("name");
        int nx = mmorpg.core.StatTree.nextAt(key, v);
        tip.add(Stats.info(key).str("color") + "§l" + name + " 나무 §r§7(" + t.title() + ")");
        tip.add("§7" + mmorpg.core.StatTree.tier(key, v) + "/" + t.nodes().size() + " 단계 · " + (nx > 0 ? "다음 단계 " + name + " " + nx : "다 자람"));
        String b = mmorpg.core.StatTree.bonusText(mmorpg.core.StatTree.treeTotals(key, v, ClientRpgState.treeState().picksOf(key)));
        tip.add(b.isEmpty() ? "§8보너스 없음" : "§a" + b);
        if (!mmorpg.core.StatTree.pending(key, v, ClientRpgState.treeState().picksOf(key)).isEmpty()) tip.add("§e고를 갈래가 있습니다");
        tip.add("§e누르면 스탯 트리");
        return tip;
    }

    // ------------------------------------------------------------ 아랫줄 단추

    private int bottomW() {
        return (W - 20 - BTN_GAP * (BUTTONS.size() - 1)) / BUTTONS.size();
    }

    private int bottomLeft(int i) {
        return left + 10 + i * (bottomW() + BTN_GAP);
    }

    /** 아랫줄 단추 i 안인지 */
    private boolean inBottom(int i, double mx, double my) {
        int x = bottomLeft(i);
        int y = top + BTN_Y;
        return mx >= x && mx < x + bottomW() && my >= y && my < y + BTN_H;
    }

    /** 아랫줄 단추 i 의 가운데 (시험용 — 화면 좌표 {x, y}) */
    public int[] bottomCenter(int i) {
        return new int[] {bottomLeft(i) + bottomW() / 2, top + BTN_Y + BTN_H / 2};
    }

    /** 지금 고른 탈것 (없으면 null) */
    private static MountDefs.Def currentMount() {
        return MountDefs.get(ClientRpgState.mountCurrent());
    }

    private static boolean riding() {
        LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
        return Riding.isMounted(p);
    }

    /** 단추의 그림 · 두 줄 글 · 풀이 (마우스를 올리면) */
    private record Bottom(ItemStack item, Identifier sprite, String line1, String line2, List<String> tip) {}

    private Bottom bottom(String name) {
        switch (name) {
            case "quest":
                return new Bottom(new ItemStack(Items.WRITABLE_BOOK), null, "§e§l퀘스트", "§7목록 보기", List.of("§e퀘스트 목록", "§7진행 중인 퀘스트 보기"));
            case "ender":
                return new Bottom(ItemStack.EMPTY, ENDER, "§d§l엔더 상자", "§7내 보관함",
                        List.of("§d§l엔더 상자", "§7누르면 내 엔더 상자가 열립니다", "§7어디서나 — 놓인 엔더 상자와 같은 27칸"));
            default: {
                MountDefs.Def d = currentMount();
                String mname = d != null ? d.name() : "없음";
                MountItem item = d != null ? MountItems.of(d.key()) : null;
                ItemStack stack = item != null ? new ItemStack(item) : ItemStack.EMPTY;
                int owned = ClientRpgState.mountOwned().size();
                String shiftLine = "§eShift + 누르기: 탈것 고르기 §7(가진 탈것 " + owned + "종)";
                if (riding()) {
                    return new Bottom(stack, item == null ? MOUNT : null, "§c§l돌려보내기", "§7" + mname,
                            List.of("§b" + mname + " 돌려보내기", "§7지금 타고 있습니다", "§7누르면 내려서 돌려보냄 (웅크려도 됨)", shiftLine));
                }
                double cd = ClientRpgState.mountCooldown();
                List<String> tip = new ArrayList<>(List.of("§b§l탈것 소환 §r§7(" + mname + ")", "§7누르면 불러서 바로 탑니다", "§7웅크려 내리면 사라집니다",
                        "§c탄 동안: 공격 · 스킬 · 룬 불가"));
                if (cd > 0) tip.add(String.format(Locale.ROOT, "§8%.1f초 뒤에 다시 부를 수 있음", cd));
                tip.add(shiftLine);
                String l2 = cd > 0 ? String.format(Locale.ROOT, "§8%.1f초 뒤", cd) : "§7" + mname;
                return new Bottom(stack, item == null ? MOUNT : null, "§b§l탈것 소환", l2, tip);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        StatSheet s = ClientRpgState.sheet();
        RpgHud.sprite(g, BG, left, top, W, H);

        // 제목 · 남은 포인트
        g.text(font, "§6§l스탯", left + 12, top + 11, 0xFFFFFFFF, true);
        String pts = "남은 포인트 " + (s.point() > 0 ? "§e§l" : "§7§l") + s.point();
        int pw = font.width(pts);
        g.text(font, pts, left + W - 12 - pw, top + 11, 0xFFFFFFFF, true);
        RpgHud.sprite(g, POINT, left + W - 12 - pw - 17, top + 7, 15, 15);
        g.fill(left + 10, top + 26, left + W - 10, top + 27, 0x90C8A050);

        // 스탯 줄
        List<String> keys = Stats.STAT_KEYS;
        long now = Util.getMillis();
        int hover = -1;
        int treeHover = -1;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int y = top + ROW_Y + i * ROW_H;
            int value = s.stat(key);
            Integer before = lastValue.put(key, value);
            if (before != null && value > before) flashAt.put(key, now);
            boolean overTree = inTree(i, mx, my);
            if (overTree) treeHover = i;
            boolean over = inRow(i, mx, my) && !overTree;
            if (over) hover = i;
            g.fill(left + 10, y, left + W - 10, y + ROW_H - 2, over ? 0x34FFFFFF : 0x22000000);
            Long f = flashAt.get(key);
            if (f != null && now - f < 600) {
                int alpha = (int) (0x70 * (1 - (now - f) / 600.0));
                g.fill(left + 10, y, left + W - 10, y + ROW_H - 2, (alpha << 24) | 0x7CFF8A);
            }
            RpgHud.sprite(g, icon(key), left + 14, y + 4, 16, 16);
            g.text(font, BedrockText.toJava(s.title(key)), left + 36, y + 3, 0xFFFFFFFF, true);
            g.text(font, BedrockText.toJava(s.effect(key)), left + 36, y + 14, 0xFFFFFFFF, true);
            // +1 단추
            int bx = left + W - 40;
            int by = y + 4;
            boolean can = s.point() > 0;
            RpgHud.sprite(g, over && can ? BTN_HOVER : BTN, bx, by, 26, 16);
            String plus = (can ? "§a§l" : "§8") + "+1";
            g.text(font, plus, bx + 13 - font.width(plus) / 2, by + 4, 0xFFFFFFFF, true);
            if (ClientRpgState.treeOn()) drawTree(g, key, i, overTree, now);
        }

        // 기준 무기
        int wy = top + ROW_Y + keys.size() * ROW_H + 3;
        g.fill(left + 10, wy - 3, left + W - 10, wy - 2, 0x60C8A050);
        Weapon w = StatSheet.referenceWeapon(player.getMainHandItem(), player.getOffhandItem(), player.getInventory().getNonEquipmentItems(), s::usable);
        List<String> lines = s.weaponLines(w, mmorpg.fabric.gear.WeaponEnhance.reference(player.getMainHandItem(), player.getInventory().getNonEquipmentItems(), w), false);
        for (int i = 0; i < Math.min(MAX_LINES, lines.size()); i++) {
            g.text(font, BedrockText.toJava(lines.get(i)), left + 14, wy + 1 + i * LINE_H, 0xFFFFFFFF, true);
        }

        // 아랫줄: 퀘스트 목록 · 엔더 상자 · 탈것
        g.fill(left + 10, top + BTN_Y - 4, left + W - 10, top + BTN_Y - 3, 0x60C8A050);
        int bottomHover = -1;
        for (int i = 0; i < BUTTONS.size(); i++) {
            Bottom b = bottom(BUTTONS.get(i));
            int x = bottomLeft(i);
            int y = top + BTN_Y;
            boolean over = inBottom(i, mx, my);
            if (over) bottomHover = i;
            RpgHud.sprite(g, over ? BTN_HOVER : BTN, x, y, bottomW(), BTN_H);
            int ix = x + 4;
            int iy = y + (BTN_H - 16) / 2;
            if (!b.item().isEmpty()) g.item(b.item(), ix, iy);
            else if (b.sprite() != null) RpgHud.sprite(g, b.sprite(), ix, iy, 16, 16);
            g.text(font, BedrockText.toJava(b.line1()), ix + 19, y + 3, 0xFFFFFFFF, true);
            g.text(font, BedrockText.toJava(b.line2()), ix + 19, y + 12, 0xFFFFFFFF, true);
        }

        if (treeHover >= 0) {
            List<Component> tip = new ArrayList<>();
            for (String l : treeTip(keys.get(treeHover))) tip.add(Component.literal(BedrockText.toJava(l)));
            g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
        } else if (hover >= 0) {
            List<Component> tip = new ArrayList<>();
            for (String l : s.tooltip(keys.get(hover))) tip.add(Component.literal(BedrockText.toJava(l)));
            g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
        } else if (bottomHover >= 0) {
            List<Component> tip = new ArrayList<>();
            for (String l : bottom(BUTTONS.get(bottomHover)).tip()) tip.add(Component.literal(BedrockText.toJava(l)));
            g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) { // 26.3 은 SDL 번호 (왼쪽 = 1)
            for (int i = 0; i < Stats.STAT_KEYS.size(); i++) {
                if (!inTree(i, event.x(), event.y())) continue;
                click();
                minecraft.gui.setScreen(new StatTreeScreen(Stats.STAT_KEYS.get(i)));
                return true;
            }
            for (int i = 0; i < Stats.STAT_KEYS.size(); i++) {
                if (!inRow(i, event.x(), event.y())) continue;
                if (ClientRpgState.point() <= 0) return true;
                ClientPlayNetworking.send(new RpgNet.SpendPayload(Stats.STAT_KEYS.get(i), event.hasShiftDown() || minecraft.hasShiftDown() ? 5 : 1));
                click();
                return true;
            }
            for (int i = 0; i < BUTTONS.size(); i++) {
                if (!inBottom(i, event.x(), event.y())) continue;
                press(BUTTONS.get(i), event.hasShiftDown() || minecraft.hasShiftDown());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * 아랫줄 단추 누르기: 퀘스트 목록 · 엔더 상자 (서버가 창을 엶 — 이 창 대신), 탈것 (창을 닫고 부름 · 돌려보냄 — 창이 열린 동안에는 탈것을 몰 수 없음),
     * Shift + 탈것 = 탈것 고르기 창
     */
    void press(String name, boolean shift) {
        click();
        if (name.equals("mount")) {
            if (shift) {
                ClientPlayNetworking.send(new RpgNet.StatActionPayload("mount_select"));
                return;
            }
            onClose();
        }
        ClientPlayNetworking.send(new RpgNet.StatActionPayload(name));
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (MmorpgClient.statsKey != null && MmorpgClient.statsKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
