package mmorpg.fabric.client.rpgui;

import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.container.ContainerTools;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 0.23.0 RPG 창 그리기 (어둠의 금테 — 사용자가 고른 시안 A, 스탯 창 · 대화창과 같은 남색 + 금테): 창 (남색 세로 그라데이션 + 금테 · 모서리 붉은 보석),
 * 어두운 칸, 등급 색 칸 (무기 · 갑옷 · 룬 — 애드온 등급 보석과 같은 색: 일반 회색 · 희귀 파랑 · 고귀 보라 · 영웅 주황 · 초월 빨강, 영웅 · 초월은 테두리가
 * 숨 쉬듯 밝아지고 빛 한 점이 테두리를 돎), 금빛 마우스 강조, 금빛 글자. 그림은 tools/rpg_ui_art.py (sprites/rpgui).
 */
public final class RpgUi {
    private RpgUi() {}

    public static final Identifier FRAME = Mmorpg.id("rpgui/frame");
    public static final Identifier FRAME_SMALL = Mmorpg.id("rpgui/frame_small");
    public static final Identifier SLOT = Mmorpg.id("rpgui/slot");
    public static final Identifier MODEL_BG = Mmorpg.id("rpgui/model_bg");
    public static final Identifier ARROW = Mmorpg.id("rpgui/arrow");
    public static final Identifier BUTTON = Mmorpg.id("rpgui/button");
    public static final Identifier BUTTON_HOVER = Mmorpg.id("rpgui/button_hover");
    public static final Identifier BUTTON_PRESS = Mmorpg.id("rpgui/button_press");

    /** 금빛 글자 (제목 · 이름) */
    public static final int GOLD_TEXT = 0xFFECC870;
    /** 창 바탕 (위 → 아래) */
    public static final int BODY_TOP = 0xFF22253C;
    public static final int BODY_BOTTOM = 0xFF141624;
    /** 금빛 가는 줄 (나눔) */
    public static final int SEP = 0x96D6AA50;
    /** 마우스 강조 금빛 */
    public static final int HOVER = 0xFFD678;
    /** 어두운 칸 바탕 (흐린 칸 덮개 · 큰 칸) */
    public static final int SLOT_BODY = 0xFF12131F;

    /** 등급 색 (0 일반 ~ 4 초월 — SkillBarHud.gradeColor 와 같음) */
    static final int[] GRADE_RGB = {0xC8C8C8, 0x4C8BFF, 0xB66CFF, 0xFF9A2E, 0xFF4A3D};

    public static int gradeRgb(int grade) {
        return grade < 0 || grade >= GRADE_RGB.length ? 0xFFFFFF : GRADE_RGB[grade];
    }

    /** 금테 창: 남색 그라데이션 바탕 + 금테 (모서리 보석) */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fillGradient(x + 3, y + 3, x + w - 3, y + h - 3, BODY_TOP, BODY_BOTTOM);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, w, h);
    }

    /** 작은 금테 (탭 · 작은 칸 — 모서리 장식 없음), 바탕은 top → bottom */
    public static void smallPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, top, bottom);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_SMALL, x, y, w, h);
    }

    /** 칸 (x, y = 아이템 자리 — 칸 그림은 1칸 바깥 18 × 18) */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x - 1, y - 1, 18, 18);
    }

    /** 칸 크기를 정해서 (모드 상자 모양 창 — 20 × 20 칸) */
    public static void slot(GuiGraphicsExtractor g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, SLOT_BODY);
        g.fill(x, y, x + size, y + 1, 0xFF06060A);
        g.fill(x, y, x + 1, y + size, 0xFF06060A);
        g.fill(x + 1, y + size - 1, x + size, y + size, 0xFF46425C);
        g.fill(x + size - 1, y + 1, x + size, y + size, 0xFF46425C);
        g.fill(x + 1, y + 1, x + size - 1, y + 2, 0xFF0C0C14);
    }

    /** 금빛 가는 줄 */
    public static void sep(GuiGraphicsExtractor g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, SEP);
    }

    /** 금빛 글자 (그림자) */
    public static void label(GuiGraphicsExtractor g, Font font, Component text, int x, int y) {
        g.text(font, text, x, y, GOLD_TEXT, true);
    }

    /** 단추 (남색 · 금테 — 마우스를 올리면 밝게, 누르는 중이면 어둡게) */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean pressed) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, pressed ? BUTTON_PRESS : hover ? BUTTON_HOVER : BUTTON, x, y, w, h);
    }

    /** 막대 (경험치 · 체력): 검은 테 · 어두운 바탕 · 채운 몫 (위 1줄 밝게) */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int rgb) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF0A0A10);
        int fw = Math.round(w * Math.max(0F, Math.min(1F, frac)));
        if (fw <= 0) return;
        g.fill(x, y, x + fw, y + h, 0xFF000000 | rgb);
        g.fill(x, y, x + fw, y + 1, 0xFF000000 | mix(rgb, 0xFFFFFF, 0.45F));
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gg = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    /** 마우스 아래 칸 (x, y = 아이템 자리 · size 그림 크기 16): 바깥 빛 · 금 테 · 안쪽 옅은 금 */
    public static void hover(GuiGraphicsExtractor g, int x, int y, int size) {
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0x30000000 | HOVER);
        g.outline(x - 1, y - 1, size + 2, size + 2, 0xFF000000 | HOVER);
        g.fill(x, y, x + size, y + size, 0x38000000 | HOVER);
    }

    /** 이 아이템의 등급 (무기 · 갑옷 · 룬 — 없으면 -1) */
    public static int grade(ItemStack s) {
        return s == null || s.isEmpty() ? -1 : ContainerTools.grade(s);
    }

    /**
     * 칸 아래 등급 색 (x, y = 아이템 자리 · size 16): 일반은 옅은 회색 테만, 희귀 ~ 초월은 옅은 색 · 아래쪽이 짙은 빛 · 색 테, 영웅 · 초월은 테가 숨 쉬듯
     * 밝아지고 흰 빛 한 점이 테두리를 돎 (초월이 더 빠름)
     */
    public static void gradeUnder(GuiGraphicsExtractor g, ItemStack s, int x, int y, int size, long ms) {
        int gi = grade(s);
        if (gi < 0) return;
        int rgb = gradeRgb(gi);
        if (gi == 0) {
            g.outline(x, y, size, size, 0x48000000 | rgb);
            return;
        }
        boolean high = gi >= 3;
        g.fill(x, y, x + size, y + size, (high ? 0x34 : 0x26) << 24 | rgb);
        g.fillGradient(x, y + size / 2, x + size, y + size, rgb, (high ? 0x50 : 0x34) << 24 | rgb);
        int a = high ? (int) (0xB4 + 0x4B * Math.sin(ms / (gi == 4 ? 260.0 : 380.0))) : 0xC8;
        g.outline(x, y, size, size, (Math.max(0, Math.min(255, a)) << 24) | rgb);
        if (high) spark(g, x, y, size, ms, gi == 4 ? 1600 : 2600);
    }

    /** 테두리를 도는 빛 한 점 (period ms 에 한 바퀴) */
    static void spark(GuiGraphicsExtractor g, int x, int y, int size, long ms, int period) {
        int perim = (size - 1) * 4;
        int p = (int) ((ms % period) * perim / period);
        for (int k = 0; k < 3; k++) {
            int q = Math.floorMod(p - k, perim);
            int px;
            int py;
            int side = q / (size - 1);
            int o = q % (size - 1);
            switch (side) {
                case 0 -> { px = x + o; py = y; }
                case 1 -> { px = x + size - 1; py = y + o; }
                case 2 -> { px = x + size - 1 - o; py = y + size - 1; }
                default -> { px = x; py = y + size - 1 - o; }
            }
            int alpha = k == 0 ? 0xFF : k == 1 ? 0xA0 : 0x50;
            g.fill(px, py, px + 1, py + 1, alpha << 24 | 0xFFFFFF);
        }
    }
}
