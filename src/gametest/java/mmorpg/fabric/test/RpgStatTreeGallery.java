package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.StatTree;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.client.GachaScreen;
import mmorpg.fabric.client.GachaShowScreen;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.StatTreeScreen;
import mmorpg.fabric.rune.RuneData;
import mmorpg.fabric.rune.RuneItem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 7단계 스탯 트리 사진첩 (화면 시험 — MMORPG_ONLY=stattree, 실제 마우스 · 키 입력): 스탯 창 줄마다 나무 칸 (풀이), Shift + 힘 줄 두 번 → 힘 10 →
 * 나무가 자람 (3인칭 — 마법진 · 빛기둥 · 잎 · 큰 글자), 나무 칸 누르기 → 스탯 트리 창 (힘 35 — 3단계 갈래 깜빡임), 칸에 마우스 (줄기 · 갈래 · 보상 칸),
 * 갈래 누르기 → 확인 창 → 고름 (서버 기록), 탭 (민첩 · 마력 · 체력 — 다 자란 나무 · 금테), 갈래 초기화 (에메랄드 — 확인 창 → 지움), 힘 룬 뽑기권
 * (풀이 · 우클릭 → 뽑기 창 (힘 탭) → 1회 → 힘 계열 룬), 다시 스탯 창 (나무 칸 · 트리 보너스가 들어간 피해 줄). 사진 이름 t_… → ci-logs 의 shots/.
 * 확인 항목은 로그 [stattree-gallery] OK / FAIL.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgStatTreeGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-stattree-gallery");
    private static final Vec3 HOME = new Vec3(0.5, -60, 0.5);

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("stattree")) return;
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
                TestSetup.treeOn(sp);
                sp.setExperienceLevels(30);
                RpgPlayers.checkLevelUp(sp);
                Weapon w = Weapons.weaponFrom("sword", 0, "common");
                sp.getInventory().setItem(0, new ItemStack(RpgItems.byId(w.id())));
                sp.getInventory().setSelectedSlot(0);
            });
            ctx.waitTicks(30);
            part(ctx, "stats", () -> stats(ctx));
            part(ctx, "grow", () -> grow(ctx, server, conn));
            part(ctx, "tree", () -> tree(ctx, server, conn));
            part(ctx, "tabs", () -> tabs(ctx, server, conn));
            part(ctx, "reset", () -> reset(ctx, server, conn));
            part(ctx, "ticket", () -> ticket(ctx, server, conn));
            part(ctx, "after", () -> after(ctx, server));
            ctx.setScreen(() -> null);
        }
        LOG.info("[stattree-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 스탯 창의 나무 칸

    private void stats(ClientGameTestContext ctx) {
        openStats(ctx);
        away(ctx);
        shot(ctx, "t_stats");
        boolean on = ctx.computeOnClient(mc -> mmorpg.fabric.client.ClientRpgState.treeOn());
        check(on, "client knows the stat tree is on");
        hover(ctx, sc -> ((StatScreen) sc).treeCenter(0));
        shot(ctx, "t_stats_tree_tip");
        closeScreen(ctx);
    }

    // ------------------------------------------------------------ 자람 (Shift + 힘 줄 두 번 → 힘 10)

    private void grow(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        openStats(ctx);
        shiftClick(ctx, conn, sc -> new int[] {((StatScreen) sc).buttonX(), ((StatScreen) sc).rowY(0)});
        shiftClick(ctx, conn, sc -> new int[] {((StatScreen) sc).buttonX(), ((StatScreen) sc).rowY(0)});
        int str = server.computeOnServer(s -> mmorpg.fabric.RpgData.of(player(s)).str());
        check(str == 10, "Shift + 힘 줄 두 번 → 힘 " + str);
        ctx.waitTicks(4);
        shot(ctx, "t_grow_window");
        closeScreen(ctx);
        // 3인칭 앞에서 자라는 연출 (힘 20 — 2단계 · 힘 룬 뽑기권)
        camera(ctx, CameraType.THIRD_PERSON_FRONT);
        look(ctx, 0F, 10F);
        ctx.waitTicks(5);
        server.runOnServer(s -> RpgPlayers.setStat(player(s), "str", 20));
        ctx.waitTicks(5);
        shot(ctx, "t_grow_fx");
        ctx.waitTicks(10);
        shot(ctx, "t_grow_title");
        int tickets = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RuneItems.statTicket("str")));
        check(tickets == 1, "2단계 → 힘 룬 뽑기권 1 (" + tickets + ")");
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.waitTicks(30);
    }

    // ------------------------------------------------------------ 스탯 트리 창 (힘 35 — 갈래)

    private void tree(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> RpgPlayers.setStat(player(s), "str", 35));
        ctx.waitTicks(10);
        openStats(ctx);
        click(ctx, conn, sc -> ((StatScreen) sc).treeCenter(0));
        if (!waitScreen(ctx, sc -> sc instanceof StatTreeScreen, "stat tree window from the row")) return;
        String tab = ctx.computeOnClient(mc -> ((StatTreeScreen) mc.gui.screen()).tab());
        check("str".equals(tab), "힘 줄 → 힘 나무 탭 (" + tab + ")");
        away(ctx);
        ctx.waitTicks(4);
        shot(ctx, "t_tree_str");
        hover(ctx, sc -> ((StatTreeScreen) sc).nodeCenter(0, -1));
        shot(ctx, "t_node_trunk");
        hover(ctx, sc -> ((StatTreeScreen) sc).nodeCenter(1, -1));
        shot(ctx, "t_node_reward");
        hover(ctx, sc -> ((StatTreeScreen) sc).nodeCenter(2, 0));
        shot(ctx, "t_node_fork");
        hover(ctx, sc -> ((StatTreeScreen) sc).nodeCenter(9, -1));
        shot(ctx, "t_node_capstone");
        // 갈래 누르기 → 확인 창 → 고르기
        click(ctx, conn, sc -> ((StatTreeScreen) sc).nodeCenter(2, 0));
        boolean asking = ctx.computeOnClient(mc -> mc.gui.screen() instanceof StatTreeScreen t && t.confirming());
        check(asking, "fork click → confirm box");
        away(ctx);
        shot(ctx, "t_confirm_pick");
        click(ctx, conn, sc -> ((StatTreeScreen) sc).confirmCenter(true));
        ctx.waitTicks(6);
        Integer picked = server.computeOnServer(s -> StatTrees.state(player(s)).picksOf("str").get(2));
        check(Integer.valueOf(0).equals(picked), "광전사 picked on the server (" + picked + ")");
        double atk = server.computeOnServer(s -> StatTrees.bonus(player(s), "atk"));
        check(Math.abs(atk - 6) < 1e-9, "공격력 +6 (" + atk + ")");
        away(ctx);
        ctx.waitTicks(2);
        shot(ctx, "t_picked");
        ctx.waitTicks(16);
        shot(ctx, "t_picked_settled");
    }

    // ------------------------------------------------------------ 탭 (민첩 · 마력 · 체력)

    private void tabs(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            RpgPlayers.setStat(sp, "dex", 47);
            RpgPlayers.setStat(sp, "int", 64);
            RpgPlayers.setStat(sp, "vit", 100);
            StatTrees.pick(sp, "int", 2, 0);
            StatTrees.pick(sp, "vit", 2, 0);
            StatTrees.pick(sp, "vit", 5, 1);
            StatTrees.pick(sp, "vit", 8, 0);
        });
        ctx.waitTicks(10);
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof StatTreeScreen)) {
            server.runOnServer(s -> StatTrees.open(player(s), "str"));
            if (!waitScreen(ctx, sc -> sc instanceof StatTreeScreen, "stat tree window (command)")) return;
        }
        String[] names = {"str", "dex", "int", "vit"};
        for (int i = 1; i < 4; i++) {
            int k = i;
            click(ctx, conn, sc -> ((StatTreeScreen) sc).tabCenter(k));
            String tab = ctx.computeOnClient(mc -> ((StatTreeScreen) mc.gui.screen()).tab());
            check(names[k].equals(tab), "tab " + k + " → " + tab);
            away(ctx);
            ctx.waitTicks(3);
            shot(ctx, "t_tree_" + names[k]);
        }
        hover(ctx, sc -> ((StatTreeScreen) sc).tabCenter(1));
        shot(ctx, "t_tab_tip");
    }

    // ------------------------------------------------------------ 갈래 초기화 (체력 나무 — 갈래 셋 → 에메랄드 36)

    private void reset(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(8, new ItemStack(Items.EMERALD, 50));
            StatTrees.sync(sp, "");
        });
        ctx.waitTicks(4);
        if (!ctx.computeOnClient(mc -> mc.gui.screen() instanceof StatTreeScreen t && "vit".equals(t.tab()))) {
            server.runOnServer(s -> StatTrees.open(player(s), "vit"));
            if (!waitScreen(ctx, sc -> sc instanceof StatTreeScreen t && "vit".equals(t.tab()), "vit tab")) return;
        }
        hover(ctx, sc -> ((StatTreeScreen) sc).resetCenter());
        shot(ctx, "t_reset_tip");
        click(ctx, conn, sc -> ((StatTreeScreen) sc).resetCenter());
        away(ctx);
        shot(ctx, "t_confirm_reset");
        int cost = server.computeOnServer(s -> StatTrees.resetCost(player(s), "vit"));
        click(ctx, conn, sc -> ((StatTreeScreen) sc).confirmCenter(true));
        ctx.waitTicks(6);
        int left = server.computeOnServer(s -> Inv.count(player(s), Inv.CURRENCY));
        int picks = server.computeOnServer(s -> StatTrees.state(player(s)).pickCount("vit"));
        check(cost == 3 * StatTree.perPick() && left == 50 - cost && picks == 0, "reset: cost " + cost + ", emeralds left " + left + ", picks " + picks);
        away(ctx);
        ctx.waitTicks(4);
        shot(ctx, "t_reset_done");
        click(ctx, conn, sc -> ((StatTreeScreen) sc).backCenter());
        boolean back = waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window from the tree");
        check(back, "◀ 스탯 창 → stat window");
        closeScreen(ctx);
    }

    // ------------------------------------------------------------ 힘 룬 뽑기권

    private void ticket(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(1, new ItemStack(RuneItems.statTicket("str"), 1));
            sp.getInventory().setSelectedSlot(1);
        });
        ctx.waitTicks(4);
        ctx.getInput().pressKey(o -> o.keyInventory);
        if (waitScreen(ctx, sc -> sc instanceof InventoryScreen, "inventory")) {
            hover(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 18 + 8, (sc.height - 166) / 2 + 142 + 8});
            shot(ctx, "t_ticket_tooltip");
            closeScreen(ctx);
        }
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, 0F, 0F);
        int runesBefore = server.computeOnServer(s -> runes(player(s), null));
        // 0.14.0: 뽑기권 우클릭 → 뽑기 창 (그 스탯의 탭) → 1회 뽑기 → 연출
        ctx.getInput().pressKey(o -> o.keyUse);
        conn.waitForServerboundPackets();
        if (!waitScreen(ctx, sc -> sc instanceof GachaScreen g && "stat_str".equals(g.tab()), "gacha window (str ticket)")) return;
        away(ctx);
        ctx.waitTicks(4);
        shot(ctx, "t_ticket_drawing");
        click(ctx, conn, sc -> ((GachaScreen) sc).pullCenter(1));
        if (!waitScreen(ctx, sc -> sc instanceof GachaShowScreen, "gacha show (str ticket)")) return;
        ctx.runOnClient(mc -> ((GachaShowScreen) mc.gui.screen()).skip());
        away(ctx);
        ctx.waitTicks(4);
        shot(ctx, "t_ticket_result");
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
        int strRunes = server.computeOnServer(s -> runes(player(s), "str"));
        int all = server.computeOnServer(s -> runes(player(s), null));
        boolean pending = server.computeOnServer(s -> RuneData.pending(player(s)).isEmpty());
        check(all == runesBefore + 1 && strRunes == 1 && pending, "힘 룬 뽑기권 → 힘 계열 룬 (" + strRunes + " / " + all + ")");
    }

    private static int runes(ServerPlayer sp, String family) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack st = sp.getInventory().getItem(i);
            if (st.getItem() instanceof RuneItem ri && (family == null || ri.rune().family().equals(family))) n += st.getCount();
        }
        return n;
    }

    // ------------------------------------------------------------ 다시 스탯 창 (나무 칸 · 보너스가 들어간 피해)

    private void after(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> RpgPlayers.setStat(player(s), "dex", 30)); // 3단계 갈래 (깜빡임)
        ctx.waitTicks(6);
        openStats(ctx);
        away(ctx);
        shot(ctx, "t_stats_after");
        hover(ctx, sc -> ((StatScreen) sc).treeCenter(1));
        shot(ctx, "t_stats_after_tip");
        closeScreen(ctx);
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private void openStats(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window (K)")) ctx.setScreen(StatScreen::new);
        ctx.waitTicks(3);
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
            LOG.info("[stattree-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[stattree-gallery] part " + name, e);
        }
        try {
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[stattree-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[stattree-gallery] {} {}", ok ? "OK  " : "FAIL", what);
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

    /** Shift 를 누른 채 왼쪽 누르기 */
    private static void shiftClick(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().holdShift();
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.getInput().releaseShift();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }
}
