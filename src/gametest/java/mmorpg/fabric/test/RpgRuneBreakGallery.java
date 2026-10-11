package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.RuneBreak;
import mmorpg.core.Runes;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.client.UltFx;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.rune.Breakthrough;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.ui.FormNet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 8단계 룬 돌파 사진첩 (화면 시험 — MMORPG_ONLY=runebreak, 실제 마우스 입력): 룬술사 돌파 창 (목록 · 풀이 · 확인 창 → 돌파 → 초월 3돌 미리 보기 — 하늘빛 · 제목),
 * 모두 돌파 (확인 → 6돌), 가방의 배지 (1 ~ 6) · 설명 (3돌 · 6돌 강화 줄), 갈기 창 (돌파한 룬의 조각), 메테오 0 · 3 · 6돌을 실제로 써서
 * 화면 색 연출 단계 (0돌 없음 · 3돌 하늘빛 · 6돌 짙은 하늘빛 + 테두리 빛 + 금빛 각성 — 클라이언트의 하늘빛 짙기로도 확인), 3인칭 6돌 각성.
 * 사진 이름 rb_… → ci-logs 의 shots/. 확인 항목은 로그 [runebreak-gallery] OK / FAIL.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgRuneBreakGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-runebreak-gallery");
    private static final Vec3 HOME = new Vec3(0.5, -60, 0.5);

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("runebreak")) return;
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
            server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 0", HOME.x, HOME.y, HOME.z));
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            server.runOnServer(s -> {
                ServerPlayer sp = player(s);
                sp.setExperienceLevels(40);
                RpgPlayers.checkLevelUp(sp);
                for (String st : List.of("str", "dex", "int", "vit")) RpgPlayers.setStat(sp, st, 120);
            });
            ctx.waitTicks(30);
            part(ctx, "window", () -> window(ctx, server, conn));
            part(ctx, "all", () -> all(ctx, server, conn));
            part(ctx, "bag", () -> bag(ctx, server));
            part(ctx, "cast", () -> cast(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[runebreak-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 룬술사 돌파 창 → 메테오 2돌 + 0돌 = 3돌 (미리 보기)

    private void window(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().clearContent();
            sp.getInventory().setItem(0, Breakthrough.stack("int", "transcend", 2));
            sp.getInventory().setItem(20, Breakthrough.stack("int", "transcend", 0));
            sp.getInventory().setItem(21, Breakthrough.stack("holy", "transcend", 0));
            sp.getInventory().setItem(22, Breakthrough.stack("holy", "transcend", 4));
            sp.getInventory().setItem(23, Breakthrough.stack("str", "rare", 1));
            sp.getInventory().setItem(24, Breakthrough.stack("str", "rare", 0));
            sp.getInventory().setSelectedSlot(8);
            RuneSmith.openRunesmith(sp, "break");
        });
        if (!waitChest(ctx, c -> c.title().contains("돌파"), "break window")) return;
        int rows = ctx.computeOnClient(mc -> (int) ((ChestFormScreen) mc.gui.screen()).form().slots().stream().filter(x -> x.kind() == mmorpg.fabric.ui.Slot.COUNT).count());
        check(rows >= 4, "break window lists merges + all (" + rows + " count slots)");
        away(ctx);
        shot(ctx, "rb_window");
        hover(ctx, chestSlot(RuneSmith.LIST_FIRST + listIndex(ctx, "메테오")));
        shot(ctx, "rb_window_tip_meteor");
        hover(ctx, chestSlot(RuneSmith.BREAK_INFO_SLOT));
        shot(ctx, "rb_window_info");
        click(ctx, conn, chestSlot(RuneSmith.LIST_FIRST + listIndex(ctx, "메테오")));
        if (!waitChest(ctx, c -> c.title().equals("룬 돌파"), "confirm")) return;
        away(ctx);
        shot(ctx, "rb_confirm");
        click(ctx, conn, chestSlot(11));
        String got = server.computeOnServer(s -> slotText(player(s), 0) + " " + slotText(player(s), 20));
        check(got.equals(Runes.runeId("int", "transcend") + "@3 -"), "메테오 2 + 0 → 3 in the hotbar (" + got + ")");
        // 창을 닫고 3돌 미리 보기 (그 룬의 하늘빛 · 번쩍임 · 제목)
        closeScreen(ctx);
        ctx.waitTicks(12);
        shot(ctx, "rb_tier3_preview");
        float sky = ctx.computeOnClient(mc -> UltFx.skyStrength());
        check(sky > 0.3F, "3돌 preview: the meteor sky on the client (" + sky + ")");
        ctx.waitTicks(30);
        shot(ctx, "rb_tier3_title");
        ctx.waitTicks(60);
    }

    // ------------------------------------------------------------ 모두 돌파 (대천사 4 + 0 · 대지 균열 1 + 0 · 수호의 함성 0 × 7 → 6)

    private void all(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (int i = 9; i <= 15; i++) sp.getInventory().setItem(i, Breakthrough.stack("vit", "common", 0));
            RuneSmith.openRunesmith(sp, "break");
        });
        if (!waitChest(ctx, c -> c.title().contains("돌파"), "break window (all)")) return;
        hover(ctx, chestSlot(RuneSmith.BREAK_ALL_SLOT));
        shot(ctx, "rb_all_tip");
        click(ctx, conn, chestSlot(RuneSmith.BREAK_ALL_SLOT));
        if (!waitChest(ctx, c -> c.title().equals("모두 돌파"), "all confirm")) return;
        away(ctx);
        shot(ctx, "rb_all_confirm");
        click(ctx, conn, chestSlot(11));
        ctx.waitTicks(4);
        String vit = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 36; i++) {
                ItemStack st = sp.getInventory().getItem(i);
                if (RuneItems.runeOf(st) != null) b.append(i).append('=').append(RuneItems.runeOf(st).family()).append(Breakthrough.of(st)).append(' ');
            }
            return b.toString().trim();
        });
        check(vit.contains("9=vit6") && vit.contains("22=holy5") && vit.contains("23=str2"), "all: vit 0×7 → 6 · holy 4 + 0 → 5 · str 1 + 0 → 2 (" + vit + ")");
        away(ctx);
        shot(ctx, "rb_all_done");
        click(ctx, conn, chestSlot(RuneSmith.TAB));
        if (waitChest(ctx, c -> c.title().contains("룬 갈기"), "grind window")) {
            away(ctx);
            shot(ctx, "rb_grind");
        }
        closeScreen(ctx);
        ctx.waitTicks(20);
    }

    /** 돌파 창 목록에서 이름이 든 칸 (없으면 0) */
    private static int listIndex(ClientGameTestContext ctx, String name) {
        return ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof ChestFormScreen c)) return 0;
            List<FormNet.SlotData> slots = c.form().slots();
            for (int i = RuneSmith.LIST_FIRST; i < slots.size(); i++) if (slots.get(i).name().contains(name)) return i - RuneSmith.LIST_FIRST;
            return 0;
        });
    }

    // ------------------------------------------------------------ 가방: 배지 · 설명

    private void bag(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().clearContent();
            sp.getInventory().setItem(0, Breakthrough.stack("int", "transcend", 3));
            sp.getInventory().setItem(1, Breakthrough.stack("vit", "transcend", 6));
            for (int i = 2; i <= 7; i++) sp.getInventory().setItem(i, Breakthrough.stack(Runes.FAMILY_ORDER.get(i), "rare", i - 1));
            sp.getInventory().setItem(8, Breakthrough.stack("dex", "transcend", 0));
            sp.getInventory().setSelectedSlot(8);
        });
        ctx.waitTicks(6);
        int lv = ctx.computeOnClient(mc -> Breakthrough.of(mc.player.getInventory().getItem(1)));
        check(lv == 6, "client sees the level (" + lv + ")");
        String name = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).getHoverName().getString());
        check(name.contains("[3돌]"), "name with the level (" + name + ")");
        shot(ctx, "rb_hotbar");
        ctx.getInput().pressKey(o -> o.keyInventory);
        if (!waitScreen(ctx, sc -> sc instanceof InventoryScreen, "inventory")) return;
        away(ctx);
        shot(ctx, "rb_bag");
        hover(ctx, sc -> hotbar(sc, 0));
        shot(ctx, "rb_tip_meteor3");
        hover(ctx, sc -> hotbar(sc, 1));
        shot(ctx, "rb_tip_undying6");
        hover(ctx, sc -> hotbar(sc, 8));
        shot(ctx, "rb_tip_shadowless0");
        closeScreen(ctx);
    }

    /** 가방 창의 핫바 칸 가운데 */
    private static int[] hotbar(Screen sc, int i) {
        return new int[] {(sc.width - 176) / 2 + 8 + i * 18 + 8, (sc.height - 166) / 2 + 142 + 8};
    }

    // ------------------------------------------------------------ 실제로 쓰기: 메테오 0 · 3 · 6돌

    private void cast(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, 0F, 14F);
        for (int lv : new int[] {0, 3, 6}) {
            int level = lv;
            server.runOnServer(s -> {
                ServerPlayer sp = player(s);
                RuneCast.resetCooldowns(sp);
                Ult.forgetSky(sp);
                sp.getInventory().setItem(0, Breakthrough.stack("int", "transcend", level));
                sp.getInventory().setSelectedSlot(0);
            });
            ctx.runOnClient(mc -> UltFx.clear());
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
            conn.waitForServerboundPackets();
            ctx.waitTicks(4);
            if (lv == 6) shot(ctx, "rb_meteor6_awaken");
            ctx.waitTicks(10);
            float sky = ctx.computeOnClient(mc -> UltFx.skyStrength());
            float edge = ctx.computeOnClient(mc -> UltFx.edgeStrength());
            shot(ctx, "rb_meteor" + lv + "_sky");
            if (lv == 0) check(sky == 0F && edge == 0F, "0돌: no screen color (sky " + sky + ", edge " + edge + ")");
            if (lv == 3) check(sky > 0.5F && edge == 0F, "3돌: sky, no edge (sky " + sky + ", edge " + edge + ")");
            if (lv == 6) check(sky > 0.5F && edge > 0.3F, "6돌: sky + edge (sky " + sky + ", edge " + edge + ")");
            ctx.waitTicks(20);
            shot(ctx, "rb_meteor" + lv + "_impact");
            int castLv = server.computeOnServer(s -> Breakthrough.castLevel(player(s), "meteor"));
            check(castLv == lv, "cast at " + lv + " (" + castLv + ")");
            ctx.waitTicks(lv == 6 ? 110 : 80);
        }
        // 3인칭: 6돌 각성 (발밑 금빛 문양 · 빛기둥 · 몸의 금빛 기운)
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            RuneCast.resetCooldowns(sp);
            sp.getInventory().setItem(0, Breakthrough.stack("vit", "transcend", 6));
        });
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        conn.waitForServerboundPackets();
        ctx.waitTicks(5);
        shot(ctx, "rb_undying6_3p");
        ctx.waitTicks(15);
        shot(ctx, "rb_undying6_3p_later");
        camera(ctx, CameraType.FIRST_PERSON);
        check(RuneBreak.max() == 6, "max 6");
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 칸의 룬 ID@단계 ("-" = 빈 칸) */
    private static String slotText(ServerPlayer sp, int slot) {
        ItemStack s = sp.getInventory().getItem(slot);
        Runes.Rune r = RuneItems.runeOf(s);
        return r == null ? "-" : r.id() + "@" + Breakthrough.of(s);
    }

    private static void camera(ClientGameTestContext ctx, CameraType t) {
        ctx.runOnClient(mc -> mc.options.setCameraType(t));
        ctx.waitTick();
    }

    private static void look(ClientGameTestContext ctx, float yaw, float pitch) {
        ctx.runOnClient(mc -> {
            LocalPlayer p = mc.player;
            p.setYRot(yaw);
            p.setXRot(pitch);
            p.yRotO = yaw;
            p.xRotO = pitch;
            p.setYHeadRot(yaw);
        });
        ctx.waitTick();
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[runebreak-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[runebreak-gallery] part " + name, e);
        }
        try {
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[runebreak-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[runebreak-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
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

    private boolean waitChest(ClientGameTestContext ctx, Predicate<FormNet.ChestPayload> test, String what) {
        return waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && test.test(c.form()), what);
    }

    private static Function<Screen, int[]> chestSlot(int i) {
        return sc -> ((ChestFormScreen) sc).slotCenter(i);
    }

    private static void closeScreen(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) ctx.setScreen(() -> null);
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
