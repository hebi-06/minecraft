package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mmorpg.core.Economy;
import mmorpg.core.Jobs;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.client.ClientJobBar;
import mmorpg.fabric.client.JobUpgradeHud;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobUpgrades;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.31.0 직업 스킬 강화 사진첩 (화면 시험, only=jobup — 실제 마우스): 56렙 검사 (연참 +3 · 섬보 +1 · 명경지수 +5 · 천검난무 +4) 로
 * <ul>
 * <li>강화 창 — 스킬 다섯 (단계 숫자) · 칸 설명 (다음 단계 · ✔ ✘ — 연참은 이미르 뼈 모자람, 천검난무는 60렙 모자람, 명경지수는 최대)</li>
 * <li>확인 창 (섬보 +1 → +2 — 필요한 것 칸 · 스킬) → 강화 연출 1인칭 (재료 빛이 모임 → 터짐 · 화면 가운데 아이콘 · 별) → 창이 다시 열림</li>
 * <li>3인칭 연출 (연참 +3 → +4), +5 (천검난무 — MAX · 금빛 빛살), HUD 칸 배지 · 스킬 칸 창 설명</li>
 * </ul>
 * 확인 항목은 로그 [jobup-gallery] OK / FAIL (단계 · 값 · 클라이언트 배지 · 화면 연출).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgJobUpGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-jobup-gallery");
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("jobup")) return;
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
            server.runCommand("xp set @a 56 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(10);
            for (String s : List.of("str", "int", "vit", "dex")) server.runCommand("execute as @a run rpg set " + s + " 150");
            server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l40_hero");
            String[][] bag = {{"rpg:loot_frost_giant", "64"}, {"rpg:loot_frost_giant", "64"}, {"rpg:loot_frost_giant", "64"}, {"rpg:loot_troll", "64"},
                {"rpg:loot_frost_wolf", "64"}, {"rpg:loot_draugr", "64"}, {"rpg:ymir_heart", "8"}, {"rpg:ymir_bone", "2"}, {"rpg:muspel_heart", "3"},
                {"rpg:laevateinn_shard", "6"}};
            for (int i = 0; i < bag.length; i++) server.runCommand("item replace entity @a inventory." + i + " with " + bag[i][0] + " " + bag[i][1]);
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            server.runCommand("tp @a 0.5 -60 0.5 0 10");
            server.runOnServer(s -> {
                ServerPlayer sp = player(s);
                RpgPlayers.update(sp, RpgData.of(sp).withLvmax(56));
                JobSystem.setJob(sp, "swordsman");
                JobUpgrades.set(sp, "swordsman_flurry", 3);
                JobUpgrades.set(sp, "swordsman_flash", 1);
                JobUpgrades.set(sp, "swordsman_mirror", 5);
                JobUpgrades.set(sp, "swordsman_ult", 4);
                Wallet.set(sp, Economy.Kind.GOLD, 120);
            });
            ctx.waitTicks(10);
            part(ctx, "window", () -> window(ctx, server, conn));
            part(ctx, "third", () -> third(ctx, server, conn));
            part(ctx, "max", () -> max(ctx, server, conn));
            part(ctx, "hud", () -> hud(ctx, server, conn));
            server.runOnServer(s -> {
                ServerPlayer sp = player(s);
                for (Jobs.Skill k : Jobs.job("swordsman").skills()) JobUpgrades.set(sp, k.id(), 0);
                JobSystem.setJob(sp, null);
            });
            ctx.setScreen(() -> null);
        }
        LOG.info("[jobup-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("jobup gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 강화 창 → 확인 → 1인칭 연출 → 창이 다시 열림

    private void window(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> JobUpgrades.open(player(s), null));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("직업 스킬 강화"), "upgrade window")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(11));
        shot(ctx, "jobup_window_flurry");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(15));
        shot(ctx, "jobup_window_ult");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(14));
        shot(ctx, "jobup_window_max");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(12));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("확인"), "confirm window")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(13));
        shot(ctx, "jobup_confirm");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(5));
        shot(ctx, "jobup_confirm_item");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(10));
        ctx.waitTicks(2);
        shot(ctx, "jobup_gather_a");
        ctx.waitTicks(8);
        shot(ctx, "jobup_gather_b");
        if (!waitFor(ctx, () -> ctx.computeOnClient(mc -> JobUpgradeHud.active()), 40)) check(false, "screen effect did not start");
        ctx.waitTicks(1);
        shot(ctx, "jobup_done_01");
        ctx.waitTicks(7);
        shot(ctx, "jobup_done_08");
        ctx.waitTicks(12);
        shot(ctx, "jobup_done_20");
        check(server.computeOnServer(s -> JobUpgrades.level(player(s), "swordsman_flash") == 2 && Wallet.get(player(s), Economy.Kind.GOLD) == 110),
                "flash +2, paid 10 gold (" + server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.GOLD)) + ")");
        if (waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("직업 스킬 강화"), "window back")) {
            hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(12));
            shot(ctx, "jobup_back");
        }
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
    }

    // ------------------------------------------------------------ 3인칭 (연참 +3 → +4 — 이미르 뼈를 채움)

    private void third(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("item replace entity @a inventory.7 with rpg:ymir_bone 4");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(6);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            JobUpgrades.upgrade(sp, Jobs.job("swordsman"), Jobs.job("swordsman").skill(0), msg -> {});
        });
        ctx.waitTicks(12);
        shot(ctx, "jobup_gather_3p");
        if (!waitFor(ctx, () -> ctx.computeOnClient(mc -> JobUpgradeHud.active()), 40)) check(false, "3p screen effect did not start");
        ctx.waitTicks(4);
        shot(ctx, "jobup_done_3p_04");
        ctx.waitTicks(10);
        shot(ctx, "jobup_done_3p_14");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(30);
        check(server.computeOnServer(s -> JobUpgrades.level(player(s), "swordsman_flurry") == 4), "flurry +4");
    }

    // ------------------------------------------------------------ +5 (천검난무 — 60렙)

    private void max(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("xp set @a 60 levels");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            RpgPlayers.update(sp, RpgData.of(sp).withLvmax(60));
            JobUpgrades.upgrade(sp, Jobs.job("swordsman"), Jobs.job("swordsman").skill(4), msg -> {});
        });
        if (!waitFor(ctx, () -> ctx.computeOnClient(mc -> JobUpgradeHud.active()), 60)) check(false, "max screen effect did not start");
        ctx.waitTicks(3);
        shot(ctx, "jobup_max_03");
        ctx.waitTicks(9);
        shot(ctx, "jobup_max_12");
        ctx.waitTicks(12);
        shot(ctx, "jobup_max_24");
        ctx.waitTicks(30);
        check(server.computeOnServer(s -> JobUpgrades.level(player(s), "swordsman_ult") == 5), "ult +5");
    }

    // ------------------------------------------------------------ HUD 배지 · 스킬 칸 창

    private void hud(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        settle(ctx, conn);
        ctx.waitTicks(10);
        shot(ctx, "jobup_hud_badges");
        check(ctx.computeOnClient(mc -> ClientJobBar.up(0) == 4 && ClientJobBar.up(1) == 2 && ClientJobBar.up(2) == 0 && ClientJobBar.up(3) == 5
                && ClientJobBar.up(4) == 5), "client badges 4 · 2 · 0 · 5 · 5: " + ctx.computeOnClient(mc -> ClientJobBar.up(0) + " " + ClientJobBar.up(1) + " "
                        + ClientJobBar.up(2) + " " + ClientJobBar.up(3) + " " + ClientJobBar.up(4)));
        ctx.getInput().pressKey(MmorpgClient.skillBarKey);
        if (waitScreen(ctx, sc -> sc instanceof mmorpg.fabric.client.SkillBarScreen, "skill bar window")) {
            hover(ctx, sc -> ((mmorpg.fabric.client.SkillBarScreen) sc).jobCenter(0));
            shot(ctx, "jobup_window_tooltip");
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    // ------------------------------------------------------------ 도구

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[jobup-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[jobup-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[jobup-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[jobup-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private boolean waitScreen(ClientGameTestContext ctx, java.util.function.Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 120);
            ctx.waitTicks(3);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open");
            return false;
        }
    }

    /** 조건이 맞을 때까지 한 틱씩 (ticks 틱까지) */
    private static boolean waitFor(ClientGameTestContext ctx, java.util.function.BooleanSupplier test, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (test.getAsBoolean()) return true;
            ctx.waitTick();
        }
        return test.getAsBoolean();
    }

    private static void settle(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] q = sc == null ? null : where.apply(sc);
            if (q == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        hover(ctx, where);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        settle(ctx, conn);
    }
}
