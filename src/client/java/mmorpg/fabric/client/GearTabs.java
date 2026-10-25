package mmorpg.fabric.client;

import mmorpg.core.Relics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * 0.43.0 장신구 · 아티팩트 창의 탭 (I 키 창 윗줄 왼쪽): [장신구] [아티팩트]. 누르면 그 창으로 바꿈, I 키는 마지막에 본 탭을 엶.
 * 규칙에 아티팩트가 없으면 (Relics.enabled false) 탭 없이 "장신구" 제목만.
 */
public final class GearTabs {
    private GearTabs() {}

    public static final int ACCESSORIES = 0;
    public static final int RELICS = 1;
    /** I 키가 여는 탭 (마지막에 본 것) */
    public static int last = ACCESSORIES;

    static final int Y = 7;
    static final int H = 15;
    static final int PAD = 7;
    private static final String[] LABEL = {"장신구", "아티팩트"};
    private static final int[] COLOR = {0xFF77FF, 0xFFB43C};

    /** 탭이 둘인지 (아티팩트 규칙이 있을 때) */
    public static boolean shown() {
        return Relics.enabled();
    }

    private static int tabW(Font font, int i) {
        return font.width(LABEL[i]) + PAD * 2;
    }

    /** 탭 i 의 왼쪽 x (창 기준이 아닌 화면 좌표) */
    static int tabX(Font font, int left, int i) {
        int x = left + 8;
        for (int k = 0; k < i; k++) x += tabW(font, k) + 3;
        return x;
    }

    /** 시험용: 탭 i 가운데 (화면 좌표) */
    public static int[] center(Font font, int left, int top, int i) {
        return new int[] {tabX(font, left, i) + tabW(font, i) / 2, top + Y + H / 2};
    }

    /** 마우스 아래 탭 (-1 없음) */
    static int at(Font font, int left, int top, double mx, double my) {
        if (!shown() || my < top + Y || my >= top + Y + H) return -1;
        for (int i = 0; i < LABEL.length; i++) {
            int x = tabX(font, left, i);
            if (mx >= x && mx < x + tabW(font, i)) return i;
        }
        return -1;
    }

    /** 탭 줄 그리기 (active = 지금 창). 탭이 없으면 제목만 */
    static void draw(GuiGraphicsExtractor g, Font font, int left, int top, int active, int mx, int my) {
        if (!shown()) {
            g.text(font, "§d§l" + LABEL[ACCESSORIES], left + 12, top + 11, 0xFFFFFFFF, true);
            return;
        }
        long now = Util.getMillis();
        int hover = at(font, left, top, mx, my);
        for (int i = 0; i < LABEL.length; i++) {
            int x = tabX(font, left, i);
            int w = tabW(font, i);
            int y = top + Y;
            int rgb = COLOR[i];
            if (i == active) {
                g.fillGradient(x, y, x + w, y + H, 0x48000000 | rgb, 0xD0141420);
                g.outline(x, y, w, H, 0xE0000000 | rgb);
                int a = (int) (0xA0 + 0x50 * Math.sin(now / 300.0));
                g.fill(x + 2, y + H - 2, x + w - 2, y + H - 1, (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF));
                g.text(font, "§l" + LABEL[i], x + PAD, y + 4, 0xFF000000 | rgb, true);
            } else {
                g.fill(x, y, x + w, y + H, hover == i ? 0x50FFFFFF : 0x40000000);
                g.outline(x, y, w, H, 0x60C8A050);
                g.text(font, LABEL[i], x + PAD, y + 4, hover == i ? 0xFFFFFFFF : 0xFF9A9AA8, true);
            }
        }
    }

    /** 탭 i 의 창 */
    static Screen screen(int i) {
        return i == RELICS && shown() ? new RelicScreen() : new ArtifactScreen();
    }

    /** 탭을 누름: 다른 탭이면 그 창으로 (소리) */
    static void select(int i, int active) {
        if (i < 0 || i == active) return;
        last = i;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        mc.gui.setScreen(screen(i));
    }

    /** I 키 · 패널 단추: 마지막 탭 */
    public static Screen lastScreen() {
        return screen(last);
    }
}
