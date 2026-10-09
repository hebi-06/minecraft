package mmorpg.fabric.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mmorpg.core.Hp;
import mmorpg.core.Js;
import mmorpg.fabric.Mmorpg;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;

/**
 * 체력 바 HUD (애드온 hud_screen.json 의 rpg_hp_panel 과 같은 그림): 하트 아이콘 + 틀 + 빨간 채움 + "12.5 / 132",
 * 25% 아래면 틀이 깜박임. 오른쪽에 방어 수치 · 레벨 (최고 레벨) · 남은 포인트, 물속이면 공기.
 * 자바 판 추가: 피해를 받으면 깎인 부분이 잠깐 밝게 남았다가 줄어듦.
 */
public final class RpgHud {
    private RpgHud() {}

    private static final ResourceLocation FRAME = Mmorpg.id("hud/hp_frame");
    private static final ResourceLocation FILL = Mmorpg.id("hud/hp_fill");
    private static final ResourceLocation ICON = Mmorpg.id("hud/hp_icon");
    private static final ResourceLocation WARN = Mmorpg.id("hud/hp_warn");
    private static final ResourceLocation ARMOR_BADGE = Mmorpg.id("hud/armor_badge");
    private static final ResourceLocation ARMOR_ICON = Mmorpg.id("hud/armor_icon");
    private static final ResourceLocation LV_BADGE = Mmorpg.id("hud/lv_badge");
    private static final ResourceLocation POINT = Mmorpg.id("hud/icon_point");
    private static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("hud/air");
    private static final ResourceLocation AIR_BURSTING = ResourceLocation.withDefaultNamespace("hud/air_bursting");

    /** 깎인 부분 (비율): 피해 직후 그대로 있다가 10틱 뒤 줄어듦 */
    private static float trail = -1;
    private static float lastRatio = -1;
    private static int trailHold;

    /** 이 HUD 를 그릴지 (모드가 있는 서버 · 생존 · 모험 · F1 아님) */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return ClientRpgState.synced() && mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.options.hideGui;
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            trail = lastRatio = -1;
            return;
        }
        float max = p.getMaxHealth();
        float ratio = max > 0 ? Mth.clamp(p.getHealth() / max, 0, 1) : 0;
        if (lastRatio < 0 || trail < ratio) trail = ratio;
        if (ratio < lastRatio - 1e-4) trailHold = 10;
        lastRatio = ratio;
        if (trailHold > 0) trailHold--;
        else if (trail > ratio) trail = Math.max(ratio, trail - 0.02F);
    }

    /**
     * 그리기 (GuiMixin: 바닐라 renderPlayerHealth 자리에서 — 하트와 같은 층이라 채팅창 등은 그 위에 그려짐).
     * 그림에 반투명 부분이 있어 블렌딩을 켬.
     */
    public static void render(GuiGraphics g) {
        if (!active()) return;
        draw(g);
        RenderSystem.disableBlend();
    }

    /** 반투명 그림: 글자 · 사각형을 그리면 (바로 그려지며) 블렌딩이 꺼지므로 그림마다 다시 켬 */
    static void sprite(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blitSprite(id, x, y, w, h);
    }

    private static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        int h = g.guiHeight();

        // ---------------------------------------------------------- 체력 (왼쪽, 하트 줄 자리)
        int px = cx - 124;
        int py = h - 48;
        sprite(g, ICON, px + 1, py + 4, 11, 11);
        int bx = px + 15;
        int by = py + 2;
        sprite(g, FRAME, bx, by, 96, 14);
        float hp = p.getHealth();
        float max = p.getMaxHealth();
        boolean dead = p.isDeadOrDying();
        float ratio = max > 0 ? Mth.clamp(hp / max, 0, 1) : 0;
        int pct = Mth.clamp((int) Math.floor(ratio * 100), 0, 100);
        if (hp > 0 && pct == 0) pct = 1;
        int fill = Math.round(94 * pct / 100F);
        int trailW = trail > ratio ? Math.round(94 * trail) : 0;
        if (trailW > fill) g.fill(bx + 1 + fill, by + 1, bx + 1 + trailW, by + 13, 0xD0FFE6A8);
        if (fill > 0) {
            RenderSystem.enableBlend();
            g.blitSprite(FILL, 94, 12, 0, 0, bx + 1, by + 1, fill, 12);
        }
        float absorb = p.getAbsorptionAmount();
        if (absorb > 0 && max > 0) {
            int aw = Math.min(94, Math.round(94 * absorb / max));
            g.fill(bx + 1, by + 10, bx + 1 + aw, by + 13, 0xE0FFD24A);
        }
        if (pct > 0 && pct < 26) {
            float a = (float) (0.575 + 0.425 * Math.cos(Util.getMillis() / 900.0 * 2 * Math.PI));
            g.setColor(1F, 1F, 1F, a);
            sprite(g, WARN, bx - 2, by - 2, 100, 18);
            g.setColor(1F, 1F, 1F, 1F);
        }
        String text = "§l" + Hp.hpNumber(dead, hp) + "§r§7 / " + Js.str(Js.round(max * 10) / 10.0) + (absorb > 0 ? " §e+" + Js.str(Js.round(absorb * 10) / 10.0) : "");
        g.drawString(font, text, bx + 48 - font.width(text) / 2, by + 3, 0xFFFFFF, true);

        // ---------------------------------------------------------- 방어 · 레벨 · 포인트 (오른쪽, 배고픔 줄 자리)
        int ry = h - 45;
        int ax = cx + 12;
        sprite(g, ARMOR_BADGE, ax, ry, 29, 13);
        sprite(g, ARMOR_ICON, ax + 3, ry + 2, 9, 9);
        g.drawString(font, String.valueOf(Math.min(99, p.getArmorValue())), ax + 14, ry + 3, 0xFFFFFF, true);
        int lx = ax + 32;
        int level = Math.max(ClientRpgState.lvmax(), p.experienceLevel);
        String lv = String.valueOf(Math.min(999, level));
        int lw = Math.max(34, 8 + font.width("Lv") + 2 + font.width(lv));
        sprite(g, LV_BADGE, lx, ry, lw, 13);
        g.drawString(font, "Lv", lx + 4, ry + 3, 0xFFCC52, true);
        g.drawString(font, lv, lx + 6 + font.width("Lv"), ry + 3, 0xFFFFFF, true);
        int points = ClientRpgState.point();
        if (points > 0) {
            int qx = lx + lw + 3;
            sprite(g, POINT, qx, ry - 1, 14, 14);
            String pt = "+" + points;
            g.drawString(font, pt, qx + 16, ry + 3, 0xFFE066, true);
            // 깜박이는 "K" 안내 (포인트가 남아 있으면)
            if ((Util.getMillis() / 600) % 2 == 0) g.drawString(font, "§8[K]", qx + 18 + font.width(pt), ry + 3, 0xFFFFFF, true);
        }

        // ---------------------------------------------------------- 공기 (물속 · 줄었을 때)
        int maxAir = p.getMaxAirSupply();
        int air = Math.min(p.getAirSupply(), maxAir);
        if (p.isEyeInFluid(FluidTags.WATER) || air < maxAir) {
            int full = Mth.ceil((double) (air - 2) * 10.0 / maxAir);
            int popping = Mth.ceil((double) air * 10.0 / maxAir) - full;
            int right = cx + 91;
            int y = ry - 11;
            for (int i = 0; i < full + popping; i++) {
                sprite(g, i < full ? AIR : AIR_BURSTING, right - i * 8 - 9, y, 9, 9);
            }
        }
    }
}
