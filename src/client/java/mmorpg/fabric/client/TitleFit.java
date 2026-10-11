package mmorpg.fabric.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 긴 제목 · 부제목이 화면 밖으로 잘리지 않게 줄임. 애드온의 보스 · 패턴 제목 (예: "사막 전갈 · 바위 전갈 처치", "사슬이 레바테인을 붙잡았습니다") 은 그대로 보내는데,
 * 자바 제목은 GUI 의 4배 (부제목 2배) 라 흔한 화면 (1920×1080, GUI 크기 자동 = 너비 480) 에서 양옆이 잘렸음 (베드락은 제목 글자가 작아 한 화면에 들어감).
 * 제목이나 부제목이 화면 너비 - 여백을 넘으면 둘을 화면 가운데를 중심으로 같은 비율로 줄여 그림 (바닐라 그리기를 감쌈 — 위치 · 색 · 차오름은 바닐라 그대로).
 * 지금 떠 있는 글은 제목 패킷에서 (ClientTitleMixin — 바닐라 Hud 와 같은 규칙: 제목이 오면 시간 새로, 다 지나면 제목 · 부제목을 지움).
 */
public final class TitleFit {
    private TitleFit() {}

    /** 양옆 여백 (GUI 칸) */
    static final int MARGIN = 8;

    private static Component title;
    private static Component subtitle;
    private static int titleTime;
    private static int fadeIn = 10;
    private static int stay = 70;
    private static int fadeOut = 20;
    /** 지난 프레임에 쓴 줄임 비율 (시험용 — 1 = 그대로) */
    private static float lastFactor = 1F;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(TitleFit::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> onClear(true));
        // 게임이 다 뜬 뒤에 감쌈 (다른 곳이 먼저 바꾼 제목 그리기도 이 안에서 줄여지게)
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> HudElementRegistry.replaceElement(VanillaHudElements.TITLE_AND_SUBTITLE,
                old -> (graphics, delta) -> {
                    float f = factor(Minecraft.getInstance().font, graphics.guiWidth());
                    lastFactor = f;
                    if (f >= 0.999F) {
                        old.extractRenderState(graphics, delta);
                        return;
                    }
                    float cx = graphics.guiWidth() / 2F;
                    float cy = graphics.guiHeight() / 2F;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(cx, cy);
                    graphics.pose().scale(f, f);
                    graphics.pose().translate(-cx, -cy);
                    old.extractRenderState(graphics, delta);
                    graphics.pose().popMatrix();
                }));
    }

    private static void tick(Minecraft mc) {
        if (mc.isPaused() || titleTime <= 0) return;
        if (--titleTime <= 0) {
            title = null;
            subtitle = null;
        }
    }

    /**
     * 줄임 비율: 제목 (4배) · 부제목 (2배) 의 너비가 화면 너비 - 2 × 여백 안에 들게 (넘지 않으면 1).
     * 둘을 같은 비율로 줄여 제목 · 부제목 사이 간격도 같이 줄어듦
     */
    public static float factor(Font font, int guiWidth) {
        if (titleTime <= 0 || title == null) return 1F;
        float room = Math.max(40, guiWidth - 2 * MARGIN);
        float f = 1F;
        int tw = font.width(title);
        if (tw * 4F > room) f = Math.min(f, room / (tw * 4F));
        if (subtitle != null) {
            int sw = font.width(subtitle);
            if (sw * 2F > room) f = Math.min(f, room / (sw * 2F));
        }
        return f;
    }

    // ------------------------------------------------------------ 제목 패킷 (ClientTitleMixin)

    /** 제목 (바닐라 setTitle: 시간 새로) */
    public static void onTitle(Component text) {
        title = text;
        titleTime = fadeIn + stay + fadeOut;
    }

    /** 부제목 (바닐라 setSubtitle: 시간은 그대로) */
    public static void onSubtitle(Component text) {
        subtitle = text;
    }

    /** 시간 (바닐라 setTimes: 음수는 그대로, 떠 있는 제목은 새 시간으로) */
    public static void onTimes(int in, int st, int out) {
        if (in >= 0) fadeIn = in;
        if (st >= 0) stay = st;
        if (out >= 0) fadeOut = out;
        if (titleTime > 0) titleTime = fadeIn + stay + fadeOut;
    }

    /** /title clear · reset (바닐라 clearTitles: 제목 · 부제목 지움, reset 이면 시간도 처음 값) */
    public static void onClear(boolean resetTimes) {
        title = null;
        subtitle = null;
        titleTime = 0;
        if (resetTimes) {
            fadeIn = 10;
            stay = 70;
            fadeOut = 20;
        }
    }

    // ------------------------------------------------------------ 시험용

    /** 지난 프레임의 줄임 비율 (1 = 그대로) */
    public static float lastFactor() {
        return lastFactor;
    }

    /** 지금 떠 있는 제목 글 (없으면 "") */
    public static String titleText() {
        return titleTime > 0 && title != null ? title.getString() : "";
    }
}
