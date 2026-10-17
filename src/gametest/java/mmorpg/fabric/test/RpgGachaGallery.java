package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import mmorpg.core.Gacha;
import mmorpg.fabric.Props;
import mmorpg.fabric.client.ClientGacha;
import mmorpg.fabric.client.GachaAdminScreen;
import mmorpg.fabric.client.GachaScreen;
import mmorpg.fabric.client.GachaShowScreen;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.gacha.GachaSystem;
import mmorpg.fabric.rune.RuneItem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.14.0 룬 뽑기 사진첩 (화면 시험 — MMORPG_ONLY=gacha, 실제 마우스 입력):
 * 스탯 창 아랫줄 뽑기 단추 → 뽑기 창 (일반 — 초월 룬이 차례로 · 확률 · 천장), 관리자 설정 창 (이름 · 픽업 초월 · 영웅 그림 누르기 → 저장 — 한정 뽑기가 열림),
 * 한정 탭 (픽업 초월 큰 그림 · 기간 · 픽업 영웅), 고급 · 스탯 탭, 한정 1회 (확천장 — 소환 · 초월 흔들림 · 섬광 · 카드), 일반 10회 (천장 85 — 펼쳐짐 · 뒤집기 전 뒷면 선 색 (0.14.1 — 영웅 미만 흰 선 · 영웅 이상 노란 선, 사진 픽셀로 확인) · 뒤집기 · 결과 · 풀이),
 * 확인 → 뽑기 창 (천장 막대), 기록, 룬술사 뽑기권 창 (한정 룬 뽑기권), 뽑기권 우클릭 → 뽑기 창, 설정 창에서 지금 끝내기.
 * 연출은 시험이 정한 때에 멈춰 사진 (GachaShowScreen.freezeAt). 사진 이름 g_… → ci-logs 의 shots/. 확인 항목은 로그 [gacha-gallery] OK / FAIL.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgGachaGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-gacha-gallery");
    private static final Vec3 HOME = new Vec3(0.5, -60, 0.5);

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("gacha")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 0", HOME.x, HOME.y, HOME.z));
            server.runOnServer(s -> {
                Props.setWorld(s, GachaSystem.LIMITED_DATA, null);
                ServerPlayer me = me(s);
                GachaSystem.reset(me);
                me.getInventory().clearContent();
                me.getInventory().setItem(0, new ItemStack(RuneItems.TICKET, 40));
                me.getInventory().setItem(1, new ItemStack(RuneItems.ADV_TICKET, 10));
                me.getInventory().setItem(2, new ItemStack(RuneItems.statTicket("str"), 5));
                me.getInventory().setItem(3, new ItemStack(RuneItems.LIMITED_TICKET, 12));
                me.getInventory().setSelectedSlot(0);
                if (!s.getPlayerList().isOp(me.nameAndId())) s.getPlayerList().op(me.nameAndId());
            });
            ctx.waitTicks(20);
            part(ctx, "open", () -> open(ctx, conn));
            part(ctx, "admin", () -> admin(ctx, server, conn));
            part(ctx, "tabs", () -> tabs(ctx, conn));
            part(ctx, "single", () -> single(ctx, server, conn));
            part(ctx, "ten", () -> ten(ctx, server, conn));
            part(ctx, "history", () -> history(ctx, conn));
            part(ctx, "runesmith", () -> runesmith(ctx, server));
            part(ctx, "ticket", () -> ticket(ctx, server, conn));
            part(ctx, "close", () -> closeLimited(ctx, server, conn));
            server.runOnServer(s -> Props.setWorld(s, GachaSystem.LIMITED_DATA, null));
            ctx.setScreen(() -> null);
        }
        LOG.info("[gacha-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 스탯 창 → 뽑기 창

    private void open(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, s -> s instanceof StatScreen, "stats")) return;
        int idx = StatScreen.BUTTONS.indexOf("gacha");
        check(idx >= 0, "stat window has the gacha button");
        hover(ctx, sc -> ((StatScreen) sc).bottomCenter(idx));
        shot(ctx, "g_stats_button");
        click(ctx, conn, sc -> ((StatScreen) sc).bottomCenter(idx));
        if (!waitScreen(ctx, s -> s instanceof GachaScreen, "gacha")) return;
        waitFor(ctx, mc -> ClientGacha.received(), 40);
        away(ctx);
        ctx.waitTicks(5);
        shot(ctx, "g_screen_normal");
        String tab = ctx.computeOnClient(mc -> ((GachaScreen) mc.gui.screen()).tab());
        List<String> keys = ctx.computeOnClient(mc -> ((GachaScreen) mc.gui.screen()).tabKeys());
        check(tab.equals("normal"), "opens on the normal tab without a limited banner (" + tab + ")");
        check(keys.equals(List.of("normal", "advanced", "stat_str")), "tabs: normal · advanced · owned stat ticket " + keys);
        check(ctx.computeOnClient(mc -> ClientGacha.admin()), "operator sees the settings button");
        hover(ctx, sc -> ((GachaScreen) sc).pullCenter(10));
        shot(ctx, "g_pull10_tip");
    }

    // ------------------------------------------------------------ 관리자: 한정 뽑기 열기

    private void admin(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        click(ctx, conn, sc -> ((GachaScreen) sc).adminCenter());
        if (!waitScreen(ctx, s -> s instanceof GachaAdminScreen, "admin")) return;
        ctx.runOnClient(mc -> ((GachaAdminScreen) mc.gui.screen()).nameBox().setValue("불꽃 정령의 축제"));
        click(ctx, conn, sc -> ((GachaAdminScreen) sc).iconCenter("transcend", "spirit"));
        for (String f : List.of("str", "axe", "holy")) click(ctx, conn, sc -> ((GachaAdminScreen) sc).iconCenter("hero", f));
        List<String> f5 = ctx.computeOnClient(mc -> new ArrayList<>(((GachaAdminScreen) mc.gui.screen()).featured("transcend")));
        List<String> f4 = ctx.computeOnClient(mc -> new ArrayList<>(((GachaAdminScreen) mc.gui.screen()).featured("hero")));
        check(f5.equals(List.of("spirit")) && f4.equals(List.of("str", "axe", "holy")), "picked by clicking icons " + f5 + " " + f4);
        hover(ctx, sc -> ((GachaAdminScreen) sc).iconCenter("transcend", "spirit"));
        shot(ctx, "g_admin");
        click(ctx, conn, sc -> ((GachaAdminScreen) sc).saveCenter());
        boolean opened = waitServer(ctx, server, s -> GachaSystem.activeLimited(s) != null, 60);
        check(opened, "saving opens the limited banner");
        Gacha.Spec spec = server.computeOnServer(GachaSystem::activeLimited);
        check(spec != null && spec.name().equals("불꽃 정령의 축제") && spec.featured5().equals(List.of("spirit")) && spec.featured4().equals(List.of("str", "axe", "holy"))
                && spec.hard() == 90 && spec.price() == 15, "limited spec " + spec);
        boolean back = waitScreen(ctx, s -> s instanceof GachaScreen g && "limited".equals(g.tab()), "gacha (limited tab) after saving");
        if (!back) return;
        away(ctx);
        ctx.waitTicks(5);
        shot(ctx, "g_screen_limited");
        hover(ctx, sc -> new int[] {sc.width / 2 - GachaScreen.W / 2 + 176, sc.height / 2 - GachaScreen.H / 2 + 47 + 66});
        shot(ctx, "g_limited_tip");
    }

    private void tabs(ClientGameTestContext ctx, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        for (String k : List.of("advanced", "stat_str")) {
            click(ctx, conn, sc -> ((GachaScreen) sc).tabCenter(k));
            away(ctx);
            ctx.waitTicks(3);
            check(k.equals(ctx.computeOnClient(mc -> ((GachaScreen) mc.gui.screen()).tab())), "tab " + k);
            shot(ctx, "g_screen_" + k);
        }
        click(ctx, conn, sc -> ((GachaScreen) sc).tabCenter("limited"));
    }

    // ------------------------------------------------------------ 한정 1회 (확천장 — 픽업 초월)

    private void single(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        server.runOnServer(s -> GachaSystem.setCounter(me(s), "limited", new Gacha.Counter(89, 0, true, false)));
        ctx.runOnClient(mc -> ((GachaScreen) mc.gui.screen()).setTab("limited"));
        ctx.waitTicks(2);
        int results = ctx.computeOnClient(mc -> ClientGacha.results);
        int tickets = server.computeOnServer(s -> GachaSystem.tickets(me(s), RuneItems.LIMITED_TICKET));
        click(ctx, conn, sc -> ((GachaScreen) sc).pullCenter(1));
        if (!waitScreen(ctx, s -> s instanceof GachaShowScreen, "show (single)")) return;
        check(ctx.computeOnClient(mc -> ClientGacha.results) == results + 1, "result arrived");
        String top = ctx.computeOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).topGrade());
        check("transcend".equals(top), "guaranteed transcend (" + top + ")");
        check(server.computeOnServer(s -> GachaSystem.tickets(me(s), RuneItems.LIMITED_TICKET)) == tickets - 1, "one limited ticket paid");
        String rune = server.computeOnServer(s -> GachaSystem.state(me(s)).history().get(GachaSystem.state(me(s)).history().size() - 1).rune());
        check("rpg:rune_spirit_transcend".equals(rune), "featured spirit-king rune " + rune);
        away(ctx);
        for (long[] at : new long[][] {{450}, {1250}, {1600}, {2700}}) {
            ctx.runOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).freezeAt(at[0]));
            ctx.waitTicks(3);
            shot(ctx, "g_single_" + at[0]);
        }
        ctx.runOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).unfreeze());
        ctx.waitTicks(3);
        click(ctx, conn, sc -> ((GachaShowScreen) sc).okCenter());
        if (!waitScreen(ctx, s -> s instanceof GachaScreen, "back to gacha")) return;
        boolean revealed = waitServer(ctx, server, s -> !GachaSystem.revealPending(me(s)), 40);
        check(revealed, "server told the result was shown (announce)");
        away(ctx);
        ctx.waitTicks(3);
        shot(ctx, "g_after_single");
    }

    // ------------------------------------------------------------ 일반 10회 (천장 85 → 5회째 초월)

    private void ten(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        server.runOnServer(s -> GachaSystem.setCounter(me(s), "normal", new Gacha.Counter(85, 2, false, false)));
        ctx.runOnClient(mc -> ((GachaScreen) mc.gui.screen()).setTab("normal"));
        ctx.waitTicks(3);
        int runes0 = server.computeOnServer(s -> runes(me(s)));
        click(ctx, conn, sc -> ((GachaScreen) sc).pullCenter(10));
        if (!waitScreen(ctx, s -> s instanceof GachaShowScreen, "show (ten)")) return;
        List<String> grades = ctx.computeOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).grades());
        check(grades.size() == 10 && grades.contains("transcend"), "10 results with the pity transcend " + grades);
        check(server.computeOnServer(s -> runes(me(s))) == runes0 + 10, "10 runes given");
        away(ctx);
        // 뒷면 선 색 (0.14.1 — 영웅 미만 흰 선 · 영웅 이상 노란 선): 규칙, 그리고 뒤집기 전 화면 사진의 픽셀 (카드 자리 안의 흰 · 노란 선 픽셀 수)
        List<String> tones = ctx.computeOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).cardTones());
        boolean rule = tones.size() == grades.size();
        for (int i = 0; rule && i < grades.size(); i++) rule = tones.get(i).equals(List.of("hero", "transcend").contains(grades.get(i)) ? "yellow" : "white");
        check(rule && tones.contains("yellow"), "card back lines: hero and above yellow, below white " + tones + " " + grades);
        long backs = ctx.computeOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).flipAt(0)) - 5;
        ctx.runOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).freezeAt(backs));
        ctx.waitTicks(3);
        Path backsShot = shot(ctx, "g_ten_backs");
        List<int[]> rects = ctx.computeOnClient(mc -> {
            List<int[]> out = new ArrayList<>();
            for (int i = 0; i < grades.size(); i++) out.add(((GachaShowScreen) mc.gui.screen()).cardRect(i));
            return out;
        });
        int guiW = ctx.computeOnClient(mc -> mc.gui.screen().width);
        List<String> seen = screenTones(backsShot, rects, guiW);
        check(seen.equals(tones), "card backs on screen " + seen + " = " + tones);
        long end = ctx.computeOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).endAt());
        long mid = ctx.computeOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).flipAt(4)) + 60;
        for (long at : new long[] {900, 2150, mid, end + 50}) {
            ctx.runOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).freezeAt(at));
            ctx.waitTicks(3);
            shot(ctx, "g_ten_" + (at == end + 50 ? "result" : at == mid ? "flipping" : String.valueOf(at)));
        }
        int topIdx = grades.indexOf("transcend");
        hover(ctx, sc -> ((GachaShowScreen) sc).cardCenter(topIdx));
        shot(ctx, "g_ten_tip");
        click(ctx, conn, sc -> ((GachaShowScreen) sc).okCenter());
        if (!waitScreen(ctx, s -> s instanceof GachaScreen, "back to gacha (ten)")) return;
        Gacha.Counter c = server.computeOnServer(s -> GachaSystem.counter(me(s), "normal"));
        check(c.n5() < 10, "normal pity reset by the transcend " + c);
        away(ctx);
        ctx.waitTicks(3);
        shot(ctx, "g_after_ten");
    }

    private void history(ClientGameTestContext ctx, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        click(ctx, conn, sc -> ((GachaScreen) sc).historyCenter());
        check(ctx.computeOnClient(mc -> ((GachaScreen) mc.gui.screen()).showingHistory()), "history shown");
        check(ctx.computeOnClient(mc -> ClientGacha.history().size()) == 11, "11 entries " + ctx.computeOnClient(mc -> ClientGacha.history().size()));
        away(ctx);
        shot(ctx, "g_history");
        click(ctx, conn, sc -> ((GachaScreen) sc).historyCenter());
    }

    // ------------------------------------------------------------ 룬술사 (한정 룬 뽑기권)

    private void runesmith(ClientGameTestContext ctx, TestServerContext server) {
        ctx.setScreen(() -> null);
        ctx.waitTicks(2);
        server.runOnServer(s -> RuneSmith.openRunesmith(me(s), "tickets"));
        boolean open = waitFor(ctx, mc -> mc.gui.screen() instanceof ChestFormScreen, 60);
        check(open, "runesmith ticket window");
        if (!open) return;
        boolean slot = ctx.computeOnClient(mc -> ((ChestFormScreen) mc.gui.screen()).form().slots().get(RuneSmith.LIMITED_SLOT).name().contains("한정"));
        check(slot, "limited ticket on sale (bottom middle)");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(RuneSmith.LIMITED_SLOT));
        shot(ctx, "g_runesmith");
        ctx.setScreen(() -> null);
        server.runOnServer(s -> Forms.close(me(s)));
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 뽑기권 우클릭 → 뽑기 창

    private void ticket(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.setScreen(() -> null);
        server.runOnServer(s -> me(s).getInventory().setSelectedSlot(1));
        ctx.waitTicks(4);
        ctx.getInput().pressKey(o -> o.keyUse);
        conn.waitForServerboundPackets();
        boolean opened = waitScreen(ctx, s -> s instanceof GachaScreen g && "advanced".equals(g.tab()), "gacha from the advanced ticket");
        check(opened, "right-click advanced ticket → gacha window on the advanced tab");
        check(server.computeOnServer(s -> GachaSystem.tickets(me(s), RuneItems.ADV_TICKET)) == 10, "right-click does not draw by itself");
    }

    private void closeLimited(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        click(ctx, conn, sc -> ((GachaScreen) sc).adminCenter());
        if (!waitScreen(ctx, s -> s instanceof GachaAdminScreen, "admin (close)")) return;
        click(ctx, conn, sc -> ((GachaAdminScreen) sc).closeCenter());
        hover(ctx, sc -> ((GachaAdminScreen) sc).closeCenter());
        shot(ctx, "g_admin_close_confirm");
        click(ctx, conn, sc -> ((GachaAdminScreen) sc).closeCenter());
        boolean closed = waitServer(ctx, server, s -> GachaSystem.activeLimited(s) == null, 60);
        check(closed, "closing ends the limited banner (two presses)");
        boolean back = waitScreen(ctx, s -> s instanceof GachaScreen g && "normal".equals(g.tab()), "gacha (normal) after closing");
        check(back && !ctx.computeOnClient(mc -> ((GachaScreen) mc.gui.screen()).tabKeys().contains("limited")), "limited tab gone");
    }

    // ------------------------------------------------------------ 도움

    private static int runes(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) if (sp.getInventory().getItem(i).getItem() instanceof RuneItem) n++;
        return n;
    }

    private boolean ensureScreen(ClientGameTestContext ctx) {
        if (ctx.computeOnClient(mc -> mc.gui.screen() instanceof GachaScreen)) return true;
        ctx.setScreen(() -> new GachaScreen(""));
        return waitScreen(ctx, s -> s instanceof GachaScreen, "gacha");
    }

    private ServerPlayer me(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private boolean waitServer(ClientGameTestContext ctx, TestServerContext server, Predicate<MinecraftServer> test, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (server.computeOnServer(test::test)) return true;
            ctx.waitTick();
        }
        return server.computeOnServer(test::test);
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[gacha-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[gacha-gallery] part " + name, e);
        }
        ctx.waitTicks(2);
    }

    private void check(boolean ok, String what) {
        LOG.info("[gacha-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static Path shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        return ctx.takeScreenshot(name);
    }

    /**
     * 사진에서 GUI 네모들 (x0, y0, x1, y1) 안의 선 색 → yellow (노란 선이 흰 선의 3배 넘게) · white (그 반대) · 그 밖은 수.
     * 뒷면 몸통은 어두운 남보라라 밝은 픽셀은 거의 선 (테두리 · 마법진) — 펼쳐지는 중이라 몇 픽셀 어긋나도 셈이 같음.
     */
    private static List<String> screenTones(Path file, List<int[]> guiRects, int guiW) {
        List<String> out = new ArrayList<>();
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            double k = img.getWidth() / (double) guiW;
            for (int[] r : guiRects) {
                int white = 0;
                int yellow = 0;
                for (int y = (int) (r[1] * k); y < Math.min(img.getHeight(), (int) (r[3] * k)); y++) {
                    for (int x = (int) (r[0] * k); x < Math.min(img.getWidth(), (int) (r[2] * k)); x++) {
                        int c = img.getRGB(Math.max(0, x), Math.max(0, y));
                        int cr = (c >> 16) & 0xFF;
                        int cg = (c >> 8) & 0xFF;
                        int cb = c & 0xFF;
                        if (Math.min(cr, Math.min(cg, cb)) > 190 && Math.max(cr, Math.max(cg, cb)) - Math.min(cr, Math.min(cg, cb)) < 45) white++;
                        else if (cr > 190 && cg > 140 && cb < 130 && cr - cb > 90) yellow++;
                    }
                }
                if (yellow > 300 && yellow > white * 3) out.add("yellow");
                else if (white > 300 && white > yellow * 3) out.add("white");
                else out.add("white " + white + " / yellow " + yellow);
            }
        } catch (IOException | RuntimeException e) {
            out.add("unreadable " + e);
        }
        return out;
    }

    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
    }

    private static boolean waitFor(ClientGameTestContext ctx, Predicate<Minecraft> test, int ticks) {
        try {
            ctx.waitFor(mc -> mc.player != null && test.test(mc), ticks);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    private boolean waitScreen(ClientGameTestContext ctx, Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 100);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open (now " + ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()) + ")");
            return false;
        }
    }

    /** 지금 화면의 GUI 좌표 → 창 픽셀 좌표 */
    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen " + (sc == null ? "none" : sc.getClass().getSimpleName()));
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }
}
