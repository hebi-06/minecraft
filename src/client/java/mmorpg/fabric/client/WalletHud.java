package mmorpg.fabric.client;

import java.util.Locale;
import mmorpg.core.Economy;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.form.FormClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 0.21.0 화면 왼쪽 아래 지갑 (사용자 요청 "화면 왼쪽 아래에 표시"): 금화 · 은화 그림 + 골드 · 실버 숫자 (반투명 바탕). 바뀌면 숫자가 굴러가듯 따라가고,
 * 동전 그림이 한 번 튀며 빛나고, 바뀐 양 (+12 초록 · −40 빨강) 이 숫자 옆에서 떠오르며 사라짐. 핫바 왼쪽 (왼손 칸) 에 닿을 만큼 길면 "12만" 처럼 줄임.
 * RPG HUD 일 때 (생존 · 모험) 만, 채팅 입력 · NPC 대화창이 열린 동안은 숨김.
 */
public final class WalletHud {
    private WalletHud() {}

    private static final Identifier GOLD = Mmorpg.id("hud/coin_gold");
    private static final Identifier SILVER = Mmorpg.id("hud/coin_silver");
    static final int ICON = 11;
    static final int ROW = 12;
    static final int GOLD_TEXT = 0xFFFFD34D;
    static final int SILVER_TEXT = 0xFFE6EAF2;

    /** 그릴지: 지갑을 받았고 RPG HUD 이고, 채팅 입력 · NPC 대화창이 열려 있지 않을 때 */
    public static boolean visible() {
        if (!ClientWallet.received() || !RpgHud.active()) return false;
        Minecraft mc = Minecraft.getInstance();
        return !(mc.gui.screen() instanceof ChatScreen) && !FormClient.dialogOpen();
    }

    /** 줄인 수: 10만 이상 "12만" · 1억 이상 "1.2억" */
    static String compact(long v) {
        if (v < 100_000) return Economy.fmt(v);
        if (v < 100_000_000) return v / 10_000 + "만";
        return String.format(Locale.ROOT, "%.1f억", v / 1e8);
    }

    /** 지갑 판의 왼쪽 위 (시험용 — {x, y, 너비, 높이}) */
    public static int[] box(int guiWidth, int guiHeight) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String[] t = texts(guiWidth);
        int w = ICON + 4 + Math.max(font.width(t[0]), font.width(t[1])) + 4;
        return new int[] {2, guiHeight - 6 - ROW * 2, w + 2, ROW * 2 + 2};
    }

    /** 두 줄의 글자 (핫바 · 왼손 칸에 닿으면 줄임) */
    private static String[] texts(int guiWidth) {
        Font font = Minecraft.getInstance().font;
        String g = Economy.fmt(ClientWallet.shown(Economy.Kind.GOLD));
        String s = Economy.fmt(ClientWallet.shown(Economy.Kind.SILVER));
        LocalPlayer p = Minecraft.getInstance().player;
        boolean offhandLeft = p != null && !p.getOffhandItem().isEmpty() && p.getMainArm() == HumanoidArm.RIGHT;
        int right = guiWidth / 2 - 91 - (offhandLeft ? 29 : 0) - 4;
        int room = right - 4 - ICON - 4;
        if (Math.max(font.width(g), font.width(s)) > room) {
            g = compact(ClientWallet.shown(Economy.Kind.GOLD));
            s = compact(ClientWallet.shown(Economy.Kind.SILVER));
        }
        return new String[] {g, s};
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (!visible()) return;
        Font font = Minecraft.getInstance().font;
        long now = Util.getMillis();
        String[] t = texts(g.guiWidth());
        int x = 4;
        int y = g.guiHeight() - 4 - ROW * 2;
        int w = ICON + 4 + Math.max(font.width(t[0]), font.width(t[1])) + 4;
        g.fill(x - 2, y - 2, x + w, y + ROW * 2, 0x58000000);
        g.fill(x - 2, y - 2, x + w, y - 1, 0x30FFFFFF);
        row(g, font, x, y, Economy.Kind.GOLD, GOLD, t[0], GOLD_TEXT, now);
        row(g, font, x, y + ROW, Economy.Kind.SILVER, SILVER, t[1], SILVER_TEXT, now);
    }

    private static void row(GuiGraphicsExtractor g, Font font, int x, int y, Economy.Kind k, Identifier icon, String text, int color, long now) {
        ClientWallet.Pop pop = ClientWallet.pop(k);
        long age = pop == null ? Long.MAX_VALUE : now - pop.at();
        float cx = x + ICON / 2F;
        float cy = y + ICON / 2F;
        // 바뀐 직후: 동전 뒤로 퍼지는 빛 (같은 그림을 크게 · 옅게) + 동전이 한 번 튐
        if (age < 600) {
            float t = age / 600F;
            int a = Math.round(0x90 * (1 - t));
            float s = 1.2F + 0.9F * t;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(s, s);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, -ICON / 2, -ICON / 2, ICON, ICON, (a << 24) | 0xFFFFFF);
            g.pose().popMatrix();
        }
        float s = age < 350 ? 1 + 0.35F * (float) Math.sin(Math.PI * age / 350.0) : 1F;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(s, s);
        RpgHud.sprite(g, icon, -ICON / 2, -ICON / 2, ICON, ICON);
        g.pose().popMatrix();
        int tx = x + ICON + 4;
        g.text(font, text, tx, y + 2, color, true);
        if (pop == null) return;
        float t = Math.min(1F, age / (float) ClientWallet.POP_MS);
        int alpha = Math.round(255 * (1 - t * t));
        if (alpha < 16) return; // 글자는 알파가 아주 낮으면 불투명으로 그려지므로 그리지 않음
        String d = (pop.delta() > 0 ? "+" : "-") + Economy.fmt(Math.abs(pop.delta()));
        int pc = pop.delta() > 0 ? 0x7CFF8A : 0xFF7070;
        g.text(font, d, tx + font.width(text) + 4, y + 2 - Math.round(t * 6), (alpha << 24) | pc, true);
    }
}
