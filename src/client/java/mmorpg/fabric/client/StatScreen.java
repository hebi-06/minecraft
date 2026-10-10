package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.StatSheet;
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

/**
 * 스탯 창 (statui.js 의 자바 판): 남은 포인트, 힘 · 민첩 · 마력 · 체력 (줄을 누르면 +1, Shift 는 +5), 스탯마다 효과,
 * 가진 무기 기준 실제 피해 (기준 무기). 애드온과 같은 그림 (금테 창 · 스탯 아이콘). 게임은 멈추지 않음.
 */
public final class StatScreen extends Screen {
    public static final int W = 300;
    public static final int H = 222;
    public static final int ROW_Y = 34;
    public static final int ROW_H = 29;
    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier BTN = Mmorpg.id("stats/dialog_btn");
    private static final Identifier BTN_HOVER = Mmorpg.id("stats/dialog_btn_hover");
    private static final Identifier POINT = Mmorpg.id("hud/icon_point");

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
        top = (height - H) / 2;
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
        return top + ROW_Y + i * ROW_H + 13;
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
        g.fill(left + 10, top + 27, left + W - 10, top + 28, 0x90C8A050);

        // 스탯 줄
        List<String> keys = Stats.STAT_KEYS;
        long now = Util.getMillis();
        int hover = -1;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int y = top + ROW_Y + i * ROW_H;
            int value = s.stat(key);
            Integer before = lastValue.put(key, value);
            if (before != null && value > before) flashAt.put(key, now);
            boolean over = inRow(i, mx, my);
            if (over) hover = i;
            g.fill(left + 10, y, left + W - 10, y + ROW_H - 2, over ? 0x34FFFFFF : 0x22000000);
            Long f = flashAt.get(key);
            if (f != null && now - f < 600) {
                int alpha = (int) (0x70 * (1 - (now - f) / 600.0));
                g.fill(left + 10, y, left + W - 10, y + ROW_H - 2, (alpha << 24) | 0x7CFF8A);
            }
            RpgHud.sprite(g, icon(key), left + 14, y + 5, 16, 16);
            g.text(font, BedrockText.toJava(s.title(key)), left + 36, y + 4, 0xFFFFFFFF, true);
            g.text(font, BedrockText.toJava(s.effect(key)), left + 36, y + 15, 0xFFFFFFFF, true);
            // +1 단추
            int bx = left + W - 40;
            int by = y + 5;
            boolean can = s.point() > 0;
            RpgHud.sprite(g, over && can ? BTN_HOVER : BTN, bx, by, 26, 16);
            String plus = (can ? "§a§l" : "§8") + "+1";
            g.text(font, plus, bx + 13 - font.width(plus) / 2, by + 4, 0xFFFFFFFF, true);
        }

        // 기준 무기
        int wy = top + ROW_Y + keys.size() * ROW_H + 4;
        g.fill(left + 10, wy - 3, left + W - 10, wy - 2, 0x60C8A050);
        Weapon w = StatSheet.referenceWeapon(player.getMainHandItem(), player.getOffhandItem(), player.getInventory().getNonEquipmentItems(), s::usable);
        List<String> lines = s.weaponLines(w, mmorpg.fabric.gear.WeaponEnhance.reference(player.getMainHandItem(), player.getInventory().getNonEquipmentItems(), w), false);
        for (int i = 0; i < Math.min(6, lines.size()); i++) {
            g.text(font, BedrockText.toJava(lines.get(i)), left + 14, wy + 2 + i * 10, 0xFFFFFFFF, true);
        }

        if (hover >= 0) {
            List<Component> tip = new ArrayList<>();
            for (String l : s.tooltip(keys.get(hover))) tip.add(Component.literal(BedrockText.toJava(l)));
            g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) { // 26.3 은 SDL 번호 (왼쪽 = 1)
            for (int i = 0; i < Stats.STAT_KEYS.size(); i++) {
                if (!inRow(i, event.x(), event.y())) continue;
                if (ClientRpgState.point() <= 0) return true;
                ClientPlayNetworking.send(new RpgNet.SpendPayload(Stats.STAT_KEYS.get(i), event.hasShiftDown() ? 5 : 1));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
