package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.client.FxModels;
import mmorpg.fabric.skill.Opt;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.47.0 입체 효과 물체 사진첩 (화면 시험, only=fx3d): 모델을 눈높이에 한 줄로 띄워 천천히 돌리며 찍음 (낮 · 가까이 · 밤 · 직업 색), 움직임 (곡선 길을 날아가는 검 ·
 * 떨어지는 창 · 도는 비수 · 몸 뒤를 따라가는 검 반원 — 3인칭) — 사진 fx_* → ci-logs 의 shots/. 확인: 받은 수 · 그린 수 · 사각형 수 · 지우기.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgFxModelGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-fx3d-gallery");
    private final List<String> failures = new ArrayList<>();

    /** {모델, 크기, 가운데까지 (모델 +Y 칸 — 눈높이에 가운데가 오게)} */
    static final Object[][] LINEUP = {
        {"phantom_sword", 1.0, 0.48},
        {"spear", 0.62, 0.05},
        {"dagger", 1.4, 0.14},
        {"light_arrow", 1.3, 0.03},
        {"buckler", 1.0, 0.0},
    };
    private static final double[] SKY = {0.42, 0.78, 1.0};
    private static final double[] SKY_CORE = {0.84, 0.95, 1.0};
    /** 직업 색 몇 (전사 · 마검사 · 암살자 · 성기사 · 사신) {빛깔, 속빛} */
    private static final double[][][] JOB_COLORS = {
        {{1.0, 0.36, 0.2}, {1.0, 0.82, 0.55}}, {{0.62, 0.38, 1.0}, {0.78, 1.0, 0.98}}, {{0.78, 0.3, 1.0}, {1.0, 0.75, 0.95}},
        {{1.0, 0.82, 0.3}, {1.0, 0.97, 0.78}}, {{0.35, 0.95, 0.62}, {0.85, 1.0, 0.9}},
    };

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("fx3d")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });
            server.runOnServer(s -> player(s).getInventory().clearContent());
            ctx.waitTicks(20);
            part(ctx, "lineup", () -> lineup(ctx, server));
            part(ctx, "motion", () -> motion(ctx, server));
        }
        LOG.info("[fx3d-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("fx3d gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 1. 한 줄 (낮 · 가까이 · 밤 · 직업 색)

    private void spawnLine(TestServerContext server, double dist, double spin, double[][][] colors) {
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            double eye = -60 + 1.62;
            int n = LINEUP.length;
            for (int i = 0; i < n; i++) {
                String name = (String) LINEUP[i][0];
                double scale = ((Number) LINEUP[i][1]).doubleValue();
                double mid = ((Number) LINEUP[i][2]).doubleValue();
                double x = 0.5 + (i - (n - 1) / 2.0) * 1.15 * Math.min(1, dist / 3.5);
                double[] glow = colors != null ? colors[i % colors.length][0] : SKY;
                double[] core = colors != null ? colors[i % colors.length][1] : SKY_CORE;
                Fx3d.spawn(level, name, new Vec3(x, eye - mid * scale, 0.5 - dist), Opt.of("dir", new Vec3(0, 1, 0), "face", new Vec3(0, 0, 1), "spin", spin, "spinAxis",
                        "up", "scale", scale, "life", 6.0, "color", glow, "glow", core, "pop", 0.3));
            }
        });
    }

    private void lineup(ClientGameTestContext ctx, TestServerContext server) {
        int before = ctx.computeOnClient(mc -> FxModels.received);
        spawnLine(server, 3.5, 30, null);
        ctx.waitTicks(16);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_day").withSize(1280, 720));
        int got = ctx.computeOnClient(mc -> FxModels.received) - before;
        check(got == LINEUP.length, "received " + got + " / " + LINEUP.length);
        check(ctx.computeOnClient(mc -> FxModels.lastDrawn) == LINEUP.length, "drawn " + ctx.computeOnClient(mc -> FxModels.lastDrawn));
        check(ctx.computeOnClient(mc -> FxModels.lastQuads) > 300, "quads " + ctx.computeOnClient(mc -> FxModels.lastQuads));
        ctx.waitTicks(30);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_day_turned").withSize(1280, 720));
        ctx.runOnClient(mc -> FxModels.clear());
        // 가까이 (2칸 — 조금 내려다봄)
        spawnLine(server, 2.0, 0, null);
        server.runCommand("tp @a 0.5 -60 0.5 200 12");
        ctx.waitTicks(10);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_close").withSize(1280, 720));
        ctx.runOnClient(mc -> FxModels.clear());
        server.runCommand("tp @a 0.5 -60 0.5 180 0");
        // 밤
        server.runCommand("time set 18000");
        spawnLine(server, 3.5, 30, null);
        ctx.waitTicks(16);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_night").withSize(1280, 720));
        ctx.runOnClient(mc -> FxModels.clear());
        server.runCommand("time set 6000");
        // 직업 색
        spawnLine(server, 3.5, 30, JOB_COLORS);
        ctx.waitTicks(16);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_colors").withSize(1280, 720));
        // 지우기 (0.2초에 흐려짐)
        int live = ctx.computeOnClient(mc -> FxModels.live(null));
        check(live == LINEUP.length, "live " + live);
        ctx.runOnClient(mc -> FxModels.clear());
    }

    // ------------------------------------------------------------ 2. 움직임

    private void motion(ClientGameTestContext ctx, TestServerContext server) {
        // 곡선 길을 날아가는 검 셋 (왼쪽 → 오른쪽 앞 — 가는 쪽을 봄) · 떨어지는 창 (가는 쪽) · 제자리에서 도는 비수 둘 (세계 위 축) · 방패 (앞면 축으로 돎)
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            double eye = -60 + 1.62;
            for (int k = 0; k < 3; k++) {
                Vec3 from = new Vec3(-3.5, eye - 0.4 + k * 0.5, -1.5);
                Vec3 to = new Vec3(4.5, eye - 0.6 + k * 0.4, -5.5);
                Fx3d.spawn(level, "phantom_sword", from, Opt.of("to", to, "ctrl", new Vec3(0.5, eye + 1.4, -6.5), "travel", 1.2, "ease", "inout", "align", 1.0, "faceCam",
                        1.0, "life", 1.4, "color", SKY, "glow", SKY_CORE, "delay", k * 0.12));
            }
            Fx3d.spawn(level, "spear", new Vec3(-1.5, eye + 6, -7), Opt.of("vel", new Vec3(0.4, -9, 0.6), "align", 1.0, "faceCam", 1.0, "life", 0.75, "color",
                    JOB_COLORS[3][0], "glow", JOB_COLORS[3][1]));
            for (int k = 0; k < 2; k++) {
                Fx3d.spawn(level, "dagger", new Vec3(-1.0 + k * 3, eye, -3.0), Opt.of("dir", new Vec3(1, 0, 0), "face", new Vec3(0, 1, 0), "spin", 900.0, "spinAxis", "z",
                        "life", 1.5, "color", JOB_COLORS[2][0], "glow", JOB_COLORS[2][1], "scale", 1.6));
            }
            Fx3d.spawn(level, "buckler", new Vec3(0.5, eye - 0.9, -3.0), Opt.of("dir", new Vec3(0, 0, 1), "face", new Vec3(0, 1, 0.25), "spin", 720.0, "spinAxis", "z",
                    "life", 1.5, "color", JOB_COLORS[3][0], "glow", JOB_COLORS[3][1], "scale", 0.8));
        });
        for (int i = 0; i < 3; i++) {
            ctx.waitTicks(6);
            ctx.takeScreenshot(TestScreenshotOptions.of("fx_motion_" + i).withSize(1280, 720));
        }
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> FxModels.clear());
        // 몸 뒤 검 반원 (주인을 따라감 — 3인칭 뒤에서): 열두 자루가 솟아오름
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        int[] ids = new int[12];
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            ServerLevel level = sp.level();
            for (int i = 0; i < 12; i++) {
                double th = Math.toRadians(-80 + 160.0 * i / 11);
                Vec3 radial = new Vec3(Math.sin(th), Math.cos(th), 0);
                Vec3 at = new Vec3(radial.x * 2.2, 1.9 + radial.y * 2.2, -0.8);
                ids[i] = Fx3d.spawn(level, "phantom_sword", at.add(0, -1.6, 0), Opt.of("follow", (double) sp.getId(), "to", at, "ctrl", at.add(0, -0.4, 0), "travel", 0.6,
                        "ease", "out3", "dir", radial, "face", new Vec3(0, 0, 1), "life", 3.0, "color", SKY, "glow", SKY_CORE, "scale", 0.85, "fadeIn", 0.2));
            }
        });
        ctx.waitTicks(16);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_follow_halo").withSize(1280, 720));
        // 몸을 돌려도 따라 돎
        server.runCommand("tp @a 0.5 -60 0.5 135 0");
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_follow_turned").withSize(1280, 720));
        check(ctx.computeOnClient(mc -> FxModels.live("phantom_sword")) == 12, "halo live " + ctx.computeOnClient(mc -> FxModels.live("phantom_sword")));
        // 여섯 자루를 지움 (흐려지며)
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (int i = 0; i < 6; i++) Fx3d.kill(level, ids[i], new Vec3(0.5, -60, 0.5), 0.2);
        });
        ctx.waitTicks(10);
        check(ctx.computeOnClient(mc -> FxModels.live("phantom_sword")) == 6, "after kill " + ctx.computeOnClient(mc -> FxModels.live("phantom_sword")));
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_follow_killed").withSize(1280, 720));
        ctx.runOnClient(mc -> {
            FxModels.clear();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        server.runCommand("tp @a 0.5 -60 0.5 180 0");
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[fx3d-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[fx3d-gallery] part " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[fx3d-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }
}
