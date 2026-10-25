package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mmorpg.core.Economy;
import mmorpg.core.Relics;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.client.ClientSmith;
import mmorpg.fabric.client.SmithScreen;
import mmorpg.fabric.client.motion.ClientMotions;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.smith.EnhanceWindow;
import mmorpg.fabric.smith.SmithUi;
import mmorpg.fabric.ui.Icon;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.44.0 대장장이 전용 창 사진첩 (화면 시험, only=smith — 실제 키 · 마우스 입력): 대장장이 NPC (모루 · 용광로 옆) 를 우클릭 → 새 창 (강화 탭),
 * 칸 고르기 · 풀이, +6 영웅 칼 (하락 위험 · 수호 주문서) 강화 → 망치질 (창 패널: 세 번 내려침 — 닿는 순간마다 사진 · 불꽃 · 모루 소리, 세상의 NPC 도 같은 동작)
 * → 성공 연출 (+7 · 서버 알림), 탭 (레벨 올리기 · 제작 쪽 ◀ ▶ — 공허의 갑옷 부위 · 장신구 · 장신구 · 아티팩트), 모자람, 창을 닫고 세상에서 본 망치질 (북쪽 · 남쪽 —
 * 모루 쪽을 봄 · 윗면을 침 · 불꽃), 모루가 없을 때 옆으로 돌아서서 놓는 잠깐 모루, 좁은 화면 (창을 줄여 그림). 사진 이름 sm_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgSmithUiGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-smith-gallery");
    private static final Vec3 NPC_AT = new Vec3(0.5, -60, 3.5);
    private static final BlockPos ANVIL = new BlockPos(2, -60, 3);
    private final List<String> failures = new ArrayList<>();
    private int npcId = -1;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("smith")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 13000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runCommand("xp set @a 45 levels");
            for (String st : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + st + " 150");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            // 대장간: 대장장이 NPC (남쪽 3칸 — 북쪽 = 사람을 봄) · 동쪽에 모루, 서쪽에 용광로 · 모닥불
            npcId = server.computeOnServer(s -> {
                ServerPlayer sp = player(s);
                var level = sp.level();
                level.setBlockAndUpdate(ANVIL, Blocks.ANVIL.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-2, -60, 4), Blocks.BLAST_FURNACE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-2, -59, 4), Blocks.LANTERN.defaultBlockState());
                Mannequin m = NpcData.spawnAt(level, NPC_AT, new Vec3(0.5, -58.4, 0.5));
                NpcData.setName(m, "대장장이 브록");
                NpcData.setSmith(m, true);
                NpcData.refresh(m, 0);
                stock(sp);
                return m.getId();
            });
            conn.waitForClientboundEntityUpdates(EntityTypes.MANNEQUIN);
            // 빈 손 (단축 칸 6) — 세상 사진에서 든 칼이 가리지 않게
            ctx.getInput().pressKey(InputConstants.KEY_6);
            ctx.waitTicks(20);
            part(ctx, "open", () -> open(ctx, conn));
            part(ctx, "enhance", () -> enhance(ctx, server, conn));
            part(ctx, "tabs", () -> tabs(ctx, server, conn));
            part(ctx, "world", () -> worldView(ctx, server, conn));
            part(ctx, "temp", () -> tempAnvil(ctx, server, conn));
            part(ctx, "compact", () -> compact(ctx, conn));
            ctx.setScreen(() -> null);
        } finally {
            EnhanceWindow.rand = Math::random;
        }
        LOG.info("[smith-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("smith gallery: " + String.join(" / ", failures));
    }

    /** 가방: 0 = +6 영웅 칼 (35렙) · 1 = +2 초월 활 (40렙) · 2 = 영웅 30렙 도끼 (올리기) · 3 · 4 = 희귀 단검 · 일반 칼 · 강화석 · 빛나는 강화석 · 수호 주문서 · 별철 광석 · 아티팩트 · 장신구 · 실버 · 골드 */
    private static void stock(ServerPlayer sp) {
        sp.getInventory().clearContent();
        ItemStack sword = new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 35, "hero").id()));
        WeaponEnhance.write(sword, 6);
        sp.getInventory().setItem(0, sword);
        ItemStack bow = new ItemStack(RpgItems.byId(Weapons.weaponFrom("bow", 40, "transcend").id()));
        WeaponEnhance.write(bow, 2);
        sp.getInventory().setItem(1, bow);
        sp.getInventory().setItem(2, new ItemStack(RpgItems.byId(Weapons.weaponFrom("axe", 30, "hero").id())));
        sp.getInventory().setItem(3, new ItemStack(RpgItems.byId(Weapons.weaponFrom("dagger", 10, "rare").id())));
        sp.getInventory().setItem(4, new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 10, "common").id())));
        sp.getInventory().setItem(9, new ItemStack(GearItems.byId("rpg:enhance_stone"), 64));
        sp.getInventory().setItem(10, new ItemStack(GearItems.byId("rpg:bright_stone"), 12));
        sp.getInventory().setItem(11, new ItemStack(GearItems.byId("rpg:guard_scroll"), 3));
        sp.getInventory().setItem(12, new ItemStack(GearItems.byId("rpg:star_ore"), 6));
        sp.getInventory().setItem(13, new ItemStack(GearItems.byId(Relics.id("megingjord", "hero"))));
        sp.getInventory().setItem(14, new ItemStack(Icon.itemById(mmorpg.core.Artifacts.id("ring", "noble"))));
        sp.getInventory().setItem(15, new ItemStack(Icon.itemById("rpg:loot_void_leaper"), 20));
        Wallet.set(sp, Economy.Kind.SILVER, 40);
        Wallet.set(sp, Economy.Kind.GOLD, 12);
        sp.getInventory().setSelectedSlot(5);
    }

    // ------------------------------------------------------------ 1. NPC 우클릭 → 새 창

    private void open(ClientGameTestContext ctx, TestServerConnection conn) {
        lookAtNpc(ctx);
        ctx.waitTicks(10);
        shot(ctx, "sm_npc");
        rightClickNpc(ctx, conn);
        if (!waitSmith(ctx, "smith window from the npc")) return;
        check(ctx.computeOnClient(mc -> ClientSmith.lastView() != null && ClientSmith.lastView().tab().equals(SmithUi.ENHANCE) && ClientSmith.lastView().npc() == npcId),
                "enhance tab · npc id in the view");
        away(ctx);
        ctx.waitTicks(10);
        shot(ctx, "sm_open");
        hover(ctx, s -> s.rowCenter("w:1"));
        shot(ctx, "sm_row_tip");
        hover(ctx, s -> s.actionCenter());
        shot(ctx, "sm_action_hover");
    }

    // ------------------------------------------------------------ 2. +6 영웅 칼 강화 (수호 주문서 · 망치질 · 성공)

    private void enhance(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ensureOpen(ctx, conn);
        server.runOnServer(s -> EnhanceWindow.rand = () -> 0.0);
        click(ctx, conn, s -> s.rowCenter("w:0"));
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof SmithScreen s && s.selected().equals("w:0")), "+6 sword selected");
        away(ctx);
        shot(ctx, "sm_detail_down_risk");
        hover(ctx, s -> s.scrollCenter());
        shot(ctx, "sm_scroll_tip");
        int stones = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId("rpg:enhance_stone")));
        click(ctx, conn, s -> s.actionCenter());
        away(ctx);
        check(ctx.computeOnClient(mc -> ClientSmith.work() != null && ClientMotions.current(npcId) != null), "work started (panel · world npc motion)");
        // 망치가 닿는 순간마다 (클라이언트가 센 수) · 그 사이 든 망치
        hitShot(ctx, 1, "sm_hammer_hit1");
        ctx.waitTicks(5);
        shot(ctx, "sm_hammer_raise");
        hitShot(ctx, 2, "sm_hammer_hit2");
        ctx.waitTicks(6);
        shot(ctx, "sm_hammer_raise3");
        hitShot(ctx, 3, "sm_hammer_hit3");
        ctx.waitTicks(SmithUi.TICKS - SmithUi.IMPACTS.get(2) + 2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(3);
        shot(ctx, "sm_result_success");
        ctx.waitTicks(16);
        shot(ctx, "sm_result_late");
        int hits = ctx.computeOnClient(mc -> ClientSmith.hits());
        check(hits == SmithUi.IMPACTS.size(), "three hammer hits (" + hits + ")");
        int lv = server.computeOnServer(s -> WeaponEnhance.of(player(s).getInventory().getItem(0)));
        int left = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId("rpg:enhance_stone")));
        check(lv == 7, "+6 → +7 (" + lv + ")");
        check(left < stones, "stones used " + stones + " → " + left);
        String kind = ctx.computeOnClient(mc -> ClientSmith.lastResult() == null ? "" : ClientSmith.lastResult().kind());
        check(kind.equals("success"), "client result success (" + kind + ")");
        server.runOnServer(s -> EnhanceWindow.rand = Math::random);
    }

    // ------------------------------------------------------------ 3. 탭 · 쪽 · 모자람

    private void tabs(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ensureOpen(ctx, conn);
        click(ctx, conn, s -> s.tabCenter(1));
        check(tab(ctx).equals(SmithUi.RAISE), "raise tab (" + tab(ctx) + ")");
        away(ctx);
        shot(ctx, "sm_tab_raise");
        click(ctx, conn, s -> s.tabCenter(2));
        check(tab(ctx).equals(SmithUi.CRAFT), "craft tab (" + tab(ctx) + ")");
        away(ctx);
        shot(ctx, "sm_tab_craft");
        // ◀ ▶ 로 공허의 갑옷 · 장신구 쪽
        for (int k = 0; k < 6 && ctx.computeOnClient(mc -> ClientSmith.lastView().page()) != 3; k++) click(ctx, conn, s -> s.pageCenter(true));
        check(ctx.computeOnClient(mc -> ClientSmith.lastView().page()) == 3, "void armor page");
        click(ctx, conn, s -> s.rowCenter("v:str"));
        click(ctx, conn, s -> s.variantCenter(4));
        away(ctx);
        shot(ctx, "sm_craft_void");
        click(ctx, conn, s -> s.pageCenter(true));
        away(ctx);
        shot(ctx, "sm_craft_artifact");
        if (ctx.computeOnClient(mc -> ClientSmith.lastView().tabs().size()) > 3) {
            click(ctx, conn, s -> s.tabCenter(3));
            check(tab(ctx).equals(SmithUi.GEAR), "gear tab (" + tab(ctx) + ")");
            away(ctx);
            shot(ctx, "sm_tab_gear");
        }
        // 모자람: 강화 탭의 +2 초월 활 → 레벨 올리기 탭의 도끼 (실버 모자람) — 누르면 거절 알림
        click(ctx, conn, s -> s.tabCenter(1));
        click(ctx, conn, s -> s.rowCenter("r:2"));
        click(ctx, conn, s -> s.actionCenter());
        away(ctx);
        shot(ctx, "sm_lack");
        check(server.computeOnServer(s -> RpgItems.byId(Weapons.weaponFrom("axe", 30, "hero").id()) == player(s).getInventory().getItem(2).getItem()), "axe not raised");
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
    }

    // ------------------------------------------------------------ 4. 창을 닫고 세상에서 본 망치질 (모루 쪽 · 불꽃 · 끝나면 화면 제목)

    private void worldView(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        // 북쪽 (NPC 와 모루 사이 앞 — 2.7칸: 누를 수 있는 거리) 에서 옆모습: 왼쪽 모루 · 오른쪽 대장장이
        server.runCommand("tp @a 1.5 -60 0.8 0 8");
        ctx.waitTicks(5);
        ensureOpen(ctx, conn);
        click(ctx, conn, s -> s.rowCenter("w:3"));
        click(ctx, conn, s -> s.actionCenter());
        ctx.waitTicks(2);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "ESC closes during the hammering");
        boolean busy = server.computeOnServer(s -> SmithUi.busy(player(s)));
        check(busy, "server keeps hammering after closing");
        // 모루와 NPC 가운데를 봄
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(1.5, -59.0, 3.4)));
        worldHit(ctx, 1, "sm_world_hit1");
        ctx.waitTicks(6);
        ctx.takeScreenshot(TestScreenshotOptions.of("sm_world_raise").withSize(960, 540));
        worldHit(ctx, 3, "sm_world_hit3");
        ctx.waitTicks(SmithUi.TICKS - SmithUi.IMPACTS.get(2) + 6);
        ctx.takeScreenshot(TestScreenshotOptions.of("sm_world_title").withSize(960, 540));
        boolean open = server.computeOnServer(s -> SmithUi.isOpen(player(s)) || SmithUi.busy(player(s)));
        check(!open, "session ended after the result");
        // 남쪽 (반대편) 에서: 다시 망치질 (창을 닫고) — 망치를 든 오른팔이 이쪽
        server.runCommand("tp @a 1.5 -60 6.0 180 8");
        ctx.waitTicks(5);
        ensureOpen(ctx, conn);
        click(ctx, conn, s -> s.rowCenter("w:4"));
        click(ctx, conn, s -> s.actionCenter());
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(1.5, -59.0, 3.4)));
        ctx.waitTicks(SmithUi.IMPACTS.get(1) - 6);
        ctx.takeScreenshot(TestScreenshotOptions.of("sm_world_south_raise").withSize(960, 540));
        worldHit(ctx, 2, "sm_world_south_hit2");
        ctx.waitTicks(SmithUi.TICKS);
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 5. 모루가 없을 때 (잠깐 놓는 모루 — 끝나면 사라짐)

    private void tempAnvil(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> player(s).level().setBlockAndUpdate(ANVIL, Blocks.AIR.defaultBlockState()));
        server.runCommand("tp @a 2.5 -60 1.5 50 15");
        ctx.waitTicks(5);
        ensureOpen(ctx, conn);
        click(ctx, conn, s -> s.rowCenter("w:4"));
        click(ctx, conn, s -> s.actionCenter());
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        worldHit(ctx, 1, "sm_temp_anvil");
        boolean placed = server.computeOnServer(s -> SmithUi.tempAnvil(npc(s)) != null);
        check(placed, "temporary anvil while there is no anvil");
        ctx.waitTicks(SmithUi.TICKS + 30);
        boolean gone = server.computeOnServer(s -> SmithUi.tempAnvil(npc(s)) == null);
        check(gone, "temporary anvil removed after the hammering");
        server.runOnServer(s -> player(s).level().setBlockAndUpdate(ANVIL, Blocks.ANVIL.defaultBlockState()));
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 6. 좁은 화면 (창을 줄여 그림)

    private void compact(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.getInput().resizeWindow(1024, 768);
        ctx.waitTicks(5);
        ensureOpen(ctx, conn);
        away(ctx);
        ctx.waitTicks(5);
        shot(ctx, "sm_compact");
        click(ctx, conn, s -> s.tabCenter(2));
        check(tab(ctx).equals(SmithUi.CRAFT), "compact: tab click maps the mouse (" + tab(ctx) + ")");
        ctx.setScreen(() -> null);
        ctx.getInput().resizeWindow(1280, 720);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private Entity npc(MinecraftServer s) {
        for (var level : s.getAllLevels()) {
            Entity e = level.getEntity(npcId);
            if (e != null) return e;
        }
        return null;
    }

    private void lookAtNpc(ClientGameTestContext ctx) {
        int id = npcId;
        ctx.runOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, e.getEyePosition().add(0, -0.3, 0));
        });
    }

    private void rightClickNpc(ClientGameTestContext ctx, TestServerConnection conn) {
        lookAtNpc(ctx);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    /** 창이 열려 있지 않으면 NPC 를 다시 우클릭 */
    private void ensureOpen(ClientGameTestContext ctx, TestServerConnection conn) {
        if (ctx.computeOnClient(mc -> mc.gui.screen() instanceof SmithScreen)) return;
        rightClickNpc(ctx, conn);
        waitSmith(ctx, "smith window (again)");
    }

    private String tab(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ClientSmith.lastView() == null ? "" : ClientSmith.lastView().tab());
    }

    private boolean waitSmith(ClientGameTestContext ctx, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof SmithScreen, 80);
            ctx.waitTicks(3);
            return true;
        } catch (AssertionError | RuntimeException e) {
            String sc = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
            check(false, what + " did not open (screen " + sc + ")");
            return false;
        }
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[smith-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[smith-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[smith-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[smith-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    /** 망치가 n 번째로 닿은 바로 뒤 (창의 패널) */
    private void hitShot(ClientGameTestContext ctx, int n, String name) {
        waitHits(ctx, n);
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(name);
    }

    /** 망치가 n 번째로 닿은 바로 뒤 (세상 — 960 × 540) */
    private void worldHit(ClientGameTestContext ctx, int n, String name) {
        waitHits(ctx, n);
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(960, 540));
    }

    private void waitHits(ClientGameTestContext ctx, int n) {
        try {
            ctx.waitFor(mc -> ClientSmith.hits() >= n, 60);
        } catch (AssertionError | RuntimeException e) {
            check(false, "hammer hit " + n + " not seen (" + ctx.computeOnClient(mc -> ClientSmith.hits()) + ")");
        }
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

    /** 창의 GUI 좌표 → 창 픽셀 좌표 (없으면 null) */
    private static double[] at(ClientGameTestContext ctx, Function<SmithScreen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            if (!(sc instanceof SmithScreen s)) return null;
            int[] p = where.apply(s);
            if (p == null) return null;
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private void hover(ClientGameTestContext ctx, Function<SmithScreen, int[]> where) {
        double[] p = at(ctx, where);
        if (p == null) {
            check(false, "nothing to hover");
            return;
        }
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private void click(ClientGameTestContext ctx, TestServerConnection conn, Function<SmithScreen, int[]> where) {
        double[] p = at(ctx, where);
        if (p == null) {
            // 목록에서 안 보이는 칸이면 먼저 고르고 (보이게) 다시
            check(false, "nothing to click");
            return;
        }
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }
}
