package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mmorpg.core.field.FieldPlan;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.field.FieldWorld;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2026-11-07 섬 맵 사진첩 (화면 시험, only=field): 새 세계를 "월드 유형 = 섬 맵 (mmorpg:field)" 으로 만들어 섬마다 하늘에서 · 다리 위에서 찍음.
 * 확인 항목: 지상 세계가 섬 맵 생성기 · 세계 경계 (field.json border) · 시작 자리 (마을 광장). 사진 이름 fd_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgFieldGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-field-gallery");
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Mmorpg.id("field"));

    private final List<String> failures = new ArrayList<>();

    /** 사진 자리: 이름 · 눈 자리 (x, y, z) · 보는 곳 (x, y, z) */
    private record Shot(String name, double px, double py, double pz, double tx, double ty, double tz) {}

    private static final List<Shot> SHOTS = List.of(
            new Shot("fd_01_village_island", 160, 175, 300, 0, 72, 20),
            new Shot("fd_02_spawn", 0, 74, 100, 0, 80, -120),
            new Shot("fd_03_bridge_east", 470, 73, 3, 900, 75, 0),
            new Shot("fd_04_bridge_side", 520, 82, 60, 520, 66, -10),
            new Shot("fd_05_farm", 760, 150, 260, 1150, 70, -40),
            new Shot("fd_06_desert", -120, 150, 760, 60, 80, 1250),
            new Shot("fd_07_desert_mesa", 420, 130, 1050, 0, 85, 1400),
            new Shot("fd_08_sea_fjords", -760, 160, 260, -1350, 70, -60),
            new Shot("fd_09_mire", -1180, 110, 560, -1450, 64, 250),
            new Shot("fd_10_north", 120, 140, -700, -60, 110, -1250),
            new Shot("fd_11_glacier", 200, 170, -1450, -80, 110, -1750),
            new Shot("fd_12_void", 160, 130, -2120, -60, 80, -2600),
            new Shot("fd_13_helheim", 150, 120, -2700, 0, 75, -2960),
            new Shot("fd_14_muspel", 180, 140, 2120, -40, 90, 2560),
            new Shot("fd_15_volcano", 60, 150, 2450, -250, 120, 2460));

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("field")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(c -> {
                    c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    for (WorldCreationUiState.WorldTypeEntry e : c.getNormalPresetList()) if (e.preset().is(PRESET)) c.setWorldType(e);
                })
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runOnServer(s -> checkWorld(s));
            server.runCommand("time set 5000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("gamemode spectator @a");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(12);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); // 손 · 화면 칸 없이 (F1)
            });
            ctx.waitTicks(20);
            for (Shot s : SHOTS) shot(ctx, server, world, s);
        }
        LOG.info("[field-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("field gallery failed: " + String.join(" / ", failures));
    }

    private void check(boolean ok, String what) {
        if (ok) LOG.info("[field-gallery] OK   {}", what);
        else {
            LOG.warn("[field-gallery] FAIL {}", what);
            failures.add(what);
        }
    }

    private void checkWorld(MinecraftServer s) {
        ServerLevel level = s.overworld();
        FieldPlan plan = FieldWorld.terrain().plan();
        check(FieldWorld.isField(level), "overworld uses the island map generator");
        check(Math.abs(level.getWorldBorder().getSize() - plan.borderSize) < 1, "world border " + level.getWorldBorder().getSize());
        var spawn = level.getRespawnData().pos();
        check(spawn.getX() == plan.spawnX && spawn.getZ() == plan.spawnZ, "spawn at the village square " + spawn.toShortString());
    }

    private static void shot(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world, Shot s) {
        double dx = s.tx() - s.px();
        double dz = s.tz() - s.pz();
        double dy = s.ty() - (s.py() + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", s.px(), s.py(), s.pz(), yaw, pitch));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(yaw, pitch);
        ctx.waitTicks(60);
        try {
            world.getConnection().waitForChunksRender(true, 900);
        } catch (AssertionError e) {
            LOG.info("[field-gallery] chunks not all rendered — taking {} anyway", s.name());
        }
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of(s.name()).withSize(1280, 720));
    }
}
