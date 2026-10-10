package mmorpg.fabric.client;

import java.lang.reflect.Field;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.nav.NavMath;
import mmorpg.fabric.nav.NavNet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 길 안내 화면 칸 (애드온 RPG_RP/ui/hud_screen.json 의 rpg_nav_* — 화면 위 가운데 18px 줄): 테두리 (bg1 메인 금빛 · bg2 일반 하늘빛, nine_slice 4) +
 * 왼쪽 16×16 화살표 (내가 보는 쪽에서 시계 방향으로 22.5° 씩 돈 그림 a_m00 ~ a_m15 · a_c00 ~ a_c15, 도착 a_mhr · a_chr) + 글자 (이름 · 거리 · 높이).
 * 서버 (Nav) 는 목적지 자리 · 이름 · 도착 · 보이기만 보내고, 화살표 방향 · 거리 · 높이 글자는 여기서 매 프레임 내 위치 · 보는 방향으로 계산 (NavMath —
 * 경계 머무름 NAV.sectorHold, 거리 20칸부터 5칸 · 100칸부터 10칸 단위). 큰 글자 (제목) 가 떠 있거나 창 (채팅 말고) 이 열려 있으면 숨김,
 * 보스 막대가 보이면 그 아래로 (애드온 rpg_nav_pad 20px).
 */
public final class NavHud {
    private NavHud() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-nav-hud");
    private static final Obj NAV = Rules.get().obj("NAV");
    private static final double HOLD = NAV.num("sectorHold");
    private static final double HEIGHT = NAV.num("height");
    private static final Identifier BG_MAIN = Mmorpg.id("nav/bg1");
    private static final Identifier BG_COMMON = Mmorpg.id("nav/bg2");
    /** 줄 높이 · 화살표 크기 · 위쪽 틈 (애드온 rpg_nav_row 18 · rpg_nav_icon 16 · rpg_nav_gap 3) */
    static final int ROW_H = 18;
    static final int ICON = 16;
    static final int GAP = 3;

    /** 서버가 보낸 지금 칸 (없으면 null) */
    private static NavNet.NavPayload state;
    /** 지금 화살표 칸 (-1 = 아직 — 목적지가 바뀌면 새로) */
    private static int sector = -1;
    private static String sectorKey = "";
    /** 지난 프레임에 그린 글자 · 그림 (시험용 — 안 그렸으면 "") */
    private static String lastText = "";
    private static String lastIcon = "";
    private static int[] lastBox;

    // ---- 큰 글자 (제목): 바닐라처럼 차오름 + 머묾 + 걷힘 틱 동안 (ClientTitleMixin 이 알려 줌)
    private static int titleTime;
    private static int fadeIn = 10;
    private static int stay = 70;
    private static int fadeOut = 20;
    private static boolean titleText;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(NavNet.NavPayload.TYPE, (payload, context) -> update(payload));
        // 보스 막대 다음 (같은 위쪽 가운데 — 막대가 있으면 그 아래로 내려 그림)
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, Mmorpg.id("nav"), NavHud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(NavHud::tick);
    }

    static void update(NavNet.NavPayload p) {
        state = p;
        if (!p.key().equals(sectorKey)) {
            sectorKey = p.key();
            sector = -1;
        }
    }

    static void clear() {
        state = null;
        sector = -1;
        sectorKey = "";
        lastText = "";
        lastIcon = "";
        lastBox = null;
        titleTime = 0;
        titleText = false;
    }

    private static void tick(Minecraft mc) {
        if (!mc.isPaused() && titleTime > 0) titleTime--;
    }

    // ------------------------------------------------------------ 큰 글자 (ClientTitleMixin)

    /** 큰 글자가 옴 (바닐라 setTitle: 차오름 + 머묾 + 걷힘 틱 동안 보임) */
    public static void onTitle(Component text) {
        titleText = text != null && !text.getString().isEmpty();
        titleTime = fadeIn + stay + fadeOut;
    }

    /** 큰 글자 시간 (바닐라 setTimes: 음수는 그대로, 떠 있는 큰 글자는 새 시간으로) */
    public static void onTitleTimes(int in, int st, int out) {
        if (in >= 0) fadeIn = in;
        if (st >= 0) stay = st;
        if (out >= 0) fadeOut = out;
        if (titleTime > 0) titleTime = fadeIn + stay + fadeOut;
    }

    /** 큰 글자를 지움 (/title clear · reset) */
    public static void onTitleClear() {
        titleTime = 0;
        titleText = false;
    }

    /** 큰 글자 (빈 글자가 아닌 제목) 가 떠 있는지 — 그동안 칸을 숨김 (애드온: 일반 제목이 떠 있으면 UI 가 칸을 숨김) */
    public static boolean titleShowing() {
        return titleTime > 0 && titleText;
    }

    // ------------------------------------------------------------ 시험용

    /** 서버가 보낸 지금 칸 (없으면 null) */
    public static NavNet.NavPayload state() {
        return state;
    }

    /** 지금 화살표 칸 (0 앞 · 4 오른쪽 · 8 뒤 · 12 왼쪽, 아직이면 -1) */
    public static int sector() {
        return sector;
    }

    /** 지난 프레임에 그린 글자 (안 그렸으면 "") */
    public static String lastText() {
        return lastText;
    }

    /** 지난 프레임에 그린 그림 이름 ("a_m03" · "a_chr" · 그림 없음 "" ) */
    public static String lastIcon() {
        return lastIcon;
    }

    /** 지난 프레임의 칸 [x, y, 너비, 높이] (GUI 좌표, 안 그렸으면 null) */
    public static int[] lastBox() {
        return lastBox;
    }

    // ------------------------------------------------------------ 보스 막대 (바닐라 BossHealthOverlay — 막대 수만 읽음)

    private static Field overlayField;
    private static Field eventsField;
    private static boolean reflectFailed;

    /** 지금 보이는 보스 막대 수 (읽지 못하면 0) */
    static int bossBars(Minecraft mc) {
        if (reflectFailed) return 0;
        try {
            Object hud = mc.gui.hud;
            if (overlayField == null) overlayField = fieldOfType(hud.getClass(), BossHealthOverlay.class);
            Object overlay = overlayField.get(hud);
            if (overlay == null) return 0;
            if (eventsField == null) eventsField = fieldOfType(overlay.getClass(), Map.class);
            Object events = eventsField.get(overlay);
            return events instanceof Map<?, ?> m ? m.size() : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectFailed = true;
            LOG.warn("[mmorpg] nav hud: cannot read boss bars ({}) — the arrow row stays at the top", e.toString());
            return 0;
        }
    }

    private static Field fieldOfType(Class<?> c, Class<?> type) throws NoSuchFieldException {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        throw new NoSuchFieldException(type.getSimpleName() + " in " + c.getName());
    }

    /** 보스 막대 아래로 내릴 높이: 바닐라는 막대마다 19 (y 12 부터, 화면 높이 1/3 까지) — 막대가 있으면 그 아래 + 1 (애드온 패드 20) */
    static int bossPad(Minecraft mc, int guiHeight) {
        int n = bossBars(mc);
        if (n <= 0) return 0;
        int shown = 0;
        for (int j = 12; shown < n; j += 19) {
            shown++;
            if (j + 19 >= guiHeight / 3) break;
        }
        return 19 * shown + 1;
    }

    // ------------------------------------------------------------ 그리기

    /** 숨길 때인지: 칸 없음 · 숨김 · 창 (채팅 말고) · 큰 글자 */
    static boolean hidden(Minecraft mc) {
        NavNet.NavPayload p = state;
        if (p == null || !p.visible() || mc.player == null || mc.level == null) return true;
        Screen sc = mc.gui.screen();
        if (sc != null && !(sc instanceof ChatScreen)) return true;
        return titleShowing();
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc)) {
            lastText = "";
            lastIcon = "";
            lastBox = null;
            return;
        }
        NavNet.NavPayload p = state;
        LocalPlayer pl = mc.player;
        String icon = "";
        String tail = p.tail();
        if (p.mode() == NavNet.NavPayload.ARROW) {
            // 화살표 · 거리 · 높이: 내 위치 · 보는 방향으로 매 프레임 (다른 차원으로 막 넘어갔으면 서버가 고칠 때까지 그림 없이)
            if (mc.level.dimension().identifier().toString().equals(p.dim())) {
                float pt = delta.getGameTimeDeltaPartialTick(true);
                Vec3 pos = pl.getPosition(pt);
                Vec3 to = new Vec3(p.x(), p.y(), p.z());
                Vec3 view = NavMath.facing(pl.getViewVector(pt), pl.getYRot());
                sector = NavMath.sectorFor(NavMath.angleOf(view, pos, to), sector, HOLD);
                icon = NavMath.arrowSprite(p.frame(), sector);
                tail = NavMath.arrowTail(Math.hypot(to.x - pos.x, to.z - pos.z), to.y - pos.y, HEIGHT);
            }
        } else if (p.mode() == NavNet.NavPayload.HERE) {
            icon = NavMath.hereSprite(p.frame());
        }
        String text = p.label() + tail;
        Font font = mc.font;
        int w = font.width(text) + 29; // 애드온 "100%cm + 29px": 왼쪽 4 + 화살표 16 + 3 + 글자 + 6
        int x = g.guiWidth() / 2 - w / 2;
        int y = bossPad(mc, g.guiHeight()) + GAP;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, p.frame() == 1 ? BG_MAIN : BG_COMMON, x, y, w, ROW_H);
        if (!icon.isEmpty()) g.blitSprite(RenderPipelines.GUI_TEXTURED, Mmorpg.id("nav/" + icon), x + 4, y + (ROW_H - ICON) / 2, ICON, ICON);
        g.text(font, text, x + 23, y + (ROW_H - 8) / 2, 0xFFFFFFFF, true);
        lastText = text;
        lastIcon = icon;
        lastBox = new int[] {x, y, w, ROW_H};
    }
}
