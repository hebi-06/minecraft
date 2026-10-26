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
 * 떨어지는 창 · 도는 비수 · 몸 뒤를 따라가는 검 반원 — 3인칭), 장면 (금서 둘레를 도는 책장 · 쫓아가는 구체 · 열리고 닫히는 명계의 문 · 솟는 손 · 몸 둘레를 도는 도끼)
 * — 사진 fx_* → ci-logs 의 shots/. 확인: 받은 수 · 그린 수 · 사각형 수 · 지우기 · 옮기기 · 움직임 바꾸기.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgFxModelGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-fx3d-gallery");
    private final List<String> failures = new ArrayList<>();

    /** {모델, 크기, 가운데까지 (모델 +Y 칸 — 눈높이에 가운데가 오게), 움직임 (되풀이 — 없으면 "")}: 앞 줄 (작은 것) */
    static final Object[][] LINEUP = {
        {"phantom_sword", 1.0, 0.48, ""},
        {"spear", 0.62, 0.05, ""},
        {"dagger", 1.4, 0.14, ""},
        {"light_arrow", 1.3, 0.03, ""},
        {"buckler", 1.0, 0.0, ""},
        {"mana_bolt", 1.6, 0.0, ""},
        {"arcane_orb", 1.0, 0.0, "spin"},
        {"page", 2.2, 0.0, ""},
    };
    /** 뒤 줄 (큰 것 — 금서 · 대망치 · 종 · 문 · 손 · 도끼 · 연 방패) */
    static final Object[][] LINEUP_B = {
        {"grimoire", 0.9, 0.0, "flip"},
        {"maul", 0.55, -0.95, ""},
        {"bell", 0.62, -0.62, "swing"},
        {"gate", 0.42, 1.72, "swirl"},
        {"bone_hand", 1.1, -0.35, "grasp"},
        {"phantom_axe", 1.0, 0.0, ""},
        {"aegis", 0.75, -0.1, ""},
        {"light_bow", 0.85, 0.0, "draw"},
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
            part(ctx, "scenes", () -> scenes(ctx, server));
        }
        LOG.info("[fx3d-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("fx3d gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 1. 한 줄 (낮 · 가까이 · 밤 · 직업 색)

    private void spawnLine(TestServerContext server, Object[][] line, double dist, double gap, double spin, double[][][] colors) {
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            double eye = -60 + 1.62;
            int n = line.length;
            for (int i = 0; i < n; i++) {
                String name = (String) line[i][0];
                double scale = ((Number) line[i][1]).doubleValue();
                double mid = ((Number) line[i][2]).doubleValue();
                String anim = (String) line[i][3];
                double x = 0.5 + (i - (n - 1) / 2.0) * gap * Math.min(1, dist / 3.5);
                double[] glow = colors != null ? colors[i % colors.length][0] : SKY;
                double[] core = colors != null ? colors[i % colors.length][1] : SKY_CORE;
                Fx3d.spawn(level, name, new Vec3(x, eye - mid * scale, 0.5 - dist), Opt.of("dir", new Vec3(0, 1, 0), "face", new Vec3(0, 0, 1), "spin", spin, "spinAxis",
                        "up", "scale", scale, "life", 6.0, "color", glow, "glow", core, "pop", 0.3, "shine", colors != null ? 0.35 : 0.0, "anim", anim, "loop",
                        anim.isEmpty() ? 0.0 : 1.0, "animSpeed", 0.6));
            }
        });
    }

    private void lineShots(ClientGameTestContext ctx, TestServerContext server, Object[][] line, String tag, double dist, double gap, boolean close, boolean night) {
        int before = ctx.computeOnClient(mc -> FxModels.received);
        spawnLine(server, line, dist, gap, 30, null);
        ctx.waitTicks(16);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_day" + tag).withSize(1280, 720));
        int got = ctx.computeOnClient(mc -> FxModels.received) - before;
        check(got == line.length, tag + " received " + got + " / " + line.length);
        check(ctx.computeOnClient(mc -> FxModels.lastDrawn) == line.length, tag + " drawn " + ctx.computeOnClient(mc -> FxModels.lastDrawn));
        check(ctx.computeOnClient(mc -> FxModels.lastQuads) > 300, tag + " quads " + ctx.computeOnClient(mc -> FxModels.lastQuads));
        ctx.waitTicks(30);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_day_turned" + tag).withSize(1280, 720));
        ctx.runOnClient(mc -> FxModels.clear());
        if (close) {
            // 가까이 (2칸 — 조금 내려다봄)
            spawnLine(server, line, 2.0, gap, 0, null);
            server.runCommand("tp @a 0.5 -60 0.5 200 12");
            ctx.waitTicks(10);
            ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_close" + tag).withSize(1280, 720));
            ctx.runOnClient(mc -> FxModels.clear());
            server.runCommand("tp @a 0.5 -60 0.5 180 0");
        }
        if (night) {
            server.runCommand("time set 18000");
            spawnLine(server, line, dist, gap, 30, null);
            ctx.waitTicks(16);
            ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_night" + tag).withSize(1280, 720));
            ctx.runOnClient(mc -> FxModels.clear());
            server.runCommand("time set 6000");
        }
        // 직업 색 (더한 빛 0.35)
        spawnLine(server, line, dist, gap, 30, JOB_COLORS);
        ctx.waitTicks(16);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_lineup_colors" + tag).withSize(1280, 720));
        int live = ctx.computeOnClient(mc -> FxModels.live(null));
        check(live == line.length, tag + " live " + live);
        ctx.runOnClient(mc -> FxModels.clear());
    }

    private void lineup(ClientGameTestContext ctx, TestServerContext server) {
        lineShots(ctx, server, LINEUP, "", 4.2, 1.05, true, false);
        lineShots(ctx, server, LINEUP_B, "_b", 5.4, 1.2, false, true);
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
                Fx3d.spawn(level, "dagger", new Vec3(-1.0 + k * 3, eye, -3.0), Opt.of("dir", new Vec3(1, 0, 0), "face", new Vec3(0, 0.5, 1), "spin", 900.0, "spinAxis", "z",
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

    // ------------------------------------------------------------ 3. 장면 (도는 길 · 옮기기 · 움직임 바꾸기)

    private void scenes(ClientGameTestContext ctx, TestServerContext server) {
        double eye = -60 + 1.62;
        // 금서 둘레를 도는 책장 여덟 (orbit) — 금서는 책장을 넘김 (flip 되풀이)
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            Vec3 c = new Vec3(0.5, eye, -3.0);
            Fx3d.spawn(level, "grimoire", c, Opt.of("dir", new Vec3(0, 1, 0), "face", new Vec3(0, 0.35, 1), "life", 3.0, "color", JOB_COLORS[1][0], "glow",
                    JOB_COLORS[1][1], "anim", "flip", "loop", 1.0, "scale", 0.8, "pop", 0.25, "bob", 0.08));
            for (int k = 0; k < 8; k++) {
                Fx3d.spawn(level, "page", c, Opt.of("orbit", 1.3, "orbitSpeed", 80.0, "orbitAngle", k * 45.0, "bob", 0.12, "bobHz", 0.7, "faceCam", 1.0, "life", 3.0,
                        "color", JOB_COLORS[1][0], "glow", JOB_COLORS[1][1], "scale", 1.3, "fadeIn", 0.3, "spin", 40.0, "spinAxis", "y"));
            }
        });
        ctx.waitTicks(14);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_orbit").withSize(1280, 720));
        check(ctx.computeOnClient(mc -> FxModels.live("page")) == 8, "orbit pages " + ctx.computeOnClient(mc -> FxModels.live("page")));
        ctx.waitTicks(16);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_orbit_later").withSize(1280, 720));
        ctx.runOnClient(mc -> FxModels.clear());
        // 쫓아가는 구체 (move): 왼쪽에서 오른쪽 앞으로 옮김 → 옮기는 중에 다시 옮김 (0.47.0 사진첩: 잇따라 옮기면 처음 자리에서 다시 떠났음 — 지금 자리에서 떠나야 함)
        int moved0 = ctx.computeOnClient(mc -> FxModels.moved);
        int[] orb = new int[1];
        server.runOnServer(s -> orb[0] = Fx3d.spawn(s.overworld(), "arcane_orb", new Vec3(-2.0, eye, -3.5), Opt.of("life", 3.0, "color", SKY, "glow", SKY_CORE,
                "anim", "spin", "loop", 1.0, "scale", 0.6, "faceCam", 1.0)));
        ctx.waitTicks(8);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_move_0").withSize(1280, 720));
        server.runOnServer(s -> Fx3d.move(s.overworld(), orb[0], new Vec3(2.5, eye + 0.4, -4.5), 0.5));
        ctx.waitTicks(14);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_move_1").withSize(1280, 720));
        server.runOnServer(s -> Fx3d.move(s.overworld(), orb[0], new Vec3(0.5, eye + 1.2, -5.5), 0.3, null));
        ctx.waitTicks(3);
        double[] mid = ctx.computeOnClient(mc -> FxModels.probe(orb[0]));
        check(mid != null && mid[0] > 0.0, "chained move starts from where it is " + (mid == null ? "null" : String.format(java.util.Locale.ROOT, "x %.2f", mid[0])));
        ctx.waitTicks(10);
        double[] end = ctx.computeOnClient(mc -> FxModels.probe(orb[0]));
        check(end != null && Math.abs(end[0] - 0.5) < 0.08 && Math.abs(end[1] - (eye + 1.2)) < 0.08 && Math.abs(end[2] + 5.5) < 0.08, "chained move ends at the new place "
                + (end == null ? "null" : String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", end[0], end[1], end[2])));
        check(ctx.computeOnClient(mc -> FxModels.moved) - moved0 == 2, "moved " + (ctx.computeOnClient(mc -> FxModels.moved) - moved0));
        ctx.runOnClient(mc -> FxModels.clear());
        // 휘두름 (arc · grip): 머리 위 왼쪽에서 자루 끝을 쥐고 뒤 (카메라 쪽) 로 젖혔다가 앞 아래로 150° 내리치는 도끼 (넓은 면이 카메라 쪽) · 왼쪽 위에서 오른쪽으로
        // 휘두르는 검 (끝 보석 위를 쥠). 사람은 북쪽 (−z) 을 봄
        int[] swung = new int[2];
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            swung[0] = Fx3d.spawn(level, "phantom_axe", new Vec3(-1.2, eye + 1.4, -2.0), Opt.of("dir", new Vec3(0, 0.82, 0.57), "arcAxis", new Vec3(1, 0, 0), "arc",
                    -150.0, "arcTime", 0.35, "arcEase", "in", "grip", new Vec3(0, -0.66, 0), "faceCam", 1.0, "life", 1.6, "color", JOB_COLORS[0][0], "glow",
                    JOB_COLORS[0][1], "scale", 1.6, "shine", 0.3));
            swung[1] = Fx3d.spawn(level, "phantom_sword", new Vec3(1.8, eye - 0.8, -3.5), Opt.of("dir", new Vec3(-0.42, 0.9, 0), "arcAxis", new Vec3(0, 0, 1), "arc",
                    -130.0, "arcTime", 0.3, "arcEase", "out", "grip", new Vec3(0, -0.3, 0), "faceCam", 1.0, "life", 1.6, "color", JOB_COLORS[1][0], "glow",
                    JOB_COLORS[1][1], "scale", 1.5, "shine", 0.3));
        });
        ctx.waitTicks(6);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_swing_0").withSize(1280, 720));
        ctx.waitTicks(10);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_swing_1").withSize(1280, 720));
        double[] axe = ctx.computeOnClient(mc -> FxModels.probe(swung[0]));
        // (0, 0.82, 0.57) — +y 에서 +z 쪽 34.8° — 를 +X 축 둘레로 −150° → −115.2°: +Y = (0, cos, sin) ≈ (0, −0.43, −0.90) (앞 = 북쪽 아래), 쥔 곳은 그대로
        check(axe != null && axe[4] < -0.3 && axe[4] > -0.55 && axe[5] < -0.8 && Math.abs(axe[0] + 1.2) < 0.02 && Math.abs(axe[1] - (eye + 1.4)) < 0.02,
                "axe swung down around the grip " + (axe == null ? "null" : String.format(java.util.Locale.ROOT, "at %.2f %.2f %.2f · +Y %.2f %.2f %.2f", axe[0], axe[1],
                        axe[2], axe[3], axe[4], axe[5])));
        double[] sword = ctx.computeOnClient(mc -> FxModels.probe(swung[1]));
        // (−0.42, 0.9) — +x 에서 115° — 를 +Z 축 둘레로 −130° → −15°: +Y ≈ (0.97, −0.26, 0) (오른쪽 = 동쪽, 조금 아래)
        check(sword != null && sword[3] > 0.9 && sword[4] < 0 && sword[4] > -0.45, "sword swung right " + (sword == null ? "null" : String.format(java.util.Locale.ROOT,
                "+Y %.2f %.2f %.2f", sword[3], sword[4], sword[5])));
        ctx.runOnClient(mc -> FxModels.clear());
        // 명계의 문 (열림 → 소용돌이 되풀이 → 닫힘) · 땅에서 솟는 손 다섯 (움켜쥠) · 떨어지는 대망치 · 흔들리는 종
        int sw0 = ctx.computeOnClient(mc -> FxModels.animSwitched);
        int[] gate = new int[1];
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            gate[0] = Fx3d.spawn(level, "gate", new Vec3(0.5, -60, -6.0), Opt.of("dir", new Vec3(0, 1, 0), "face", new Vec3(0, 0, 1), "life", 4.0, "color",
                    JOB_COLORS[4][0], "glow", JOB_COLORS[4][1], "anim", "open", "scale", 0.9, "pop", 0.3, "glowAlpha", 0.7));
            for (int k = 0; k < 5; k++) {
                double x = -2.5 + k * 1.25;
                Fx3d.spawn(level, "bone_hand", new Vec3(x, -61.4, -3.2), Opt.of("to", new Vec3(x, -60 + 0.2, -3.2), "travel", 0.45, "ease", "out3", "dir",
                        new Vec3(0, 1, 0), "face", new Vec3(0, 0, 1), "life", 2.5, "color", JOB_COLORS[4][0], "glow", JOB_COLORS[4][1], "anim", "grasp", "animSpeed",
                        0.8, "delay", k * 0.06, "scale", 1.1));
            }
            Fx3d.spawn(level, "maul", new Vec3(-3.2, eye + 5, -5.0), Opt.of("to", new Vec3(-3.2, -60 + 0.4, -5.0), "travel", 0.5, "ease", "in", "dir", new Vec3(0, -1, 0),
                    "face", new Vec3(0, 0, 1), "life", 2.0, "color", JOB_COLORS[3][0], "glow", JOB_COLORS[3][1], "scale", 0.7));
            Fx3d.spawn(level, "bell", new Vec3(3.6, eye + 2.2, -5.0), Opt.of("dir", new Vec3(0, 1, 0), "face", new Vec3(0, 0, 1), "life", 3.0, "color",
                    JOB_COLORS[3][0], "glow", JOB_COLORS[3][1], "anim", "swing", "loop", 1.0, "scale", 0.8, "shine", 0.2));
        });
        ctx.waitTicks(12);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_gate_open").withSize(1280, 720));
        server.runOnServer(s -> Fx3d.anim(s.overworld(), gate[0], "swirl", true, new Vec3(0.5, -60, -6.0)));
        ctx.waitTicks(10);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_gate_swirl").withSize(1280, 720));
        server.runOnServer(s -> Fx3d.anim(s.overworld(), gate[0], "close", false, new Vec3(0.5, -60, -6.0)));
        ctx.waitTicks(8);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_gate_close").withSize(1280, 720));
        check(ctx.computeOnClient(mc -> FxModels.animSwitched) - sw0 == 2, "anim switched " + (ctx.computeOnClient(mc -> FxModels.animSwitched) - sw0));
        check(ctx.computeOnClient(mc -> FxModels.live("bone_hand")) == 5, "hands " + ctx.computeOnClient(mc -> FxModels.live("bone_hand")));
        ctx.runOnClient(mc -> FxModels.clear());
        // 3인칭: 몸 둘레를 도는 환영 도끼 넷 (follow + orbit) · 앞의 연 방패
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            ServerLevel level = sp.level();
            for (int k = 0; k < 4; k++) {
                Fx3d.spawn(level, "phantom_axe", new Vec3(0, 1.0, 0), Opt.of("follow", (double) sp.getId(), "orbit", 1.6, "orbitSpeed", 300.0, "orbitAngle", k * 90.0,
                        "align", 1.0, "face", new Vec3(0, 1, 0), "life", 2.0, "color", JOB_COLORS[0][0], "glow", JOB_COLORS[0][1], "scale", 1.1, "fadeIn", 0.15));
            }
            Fx3d.spawn(level, "aegis", new Vec3(0, 1.1, 1.3), Opt.of("follow", (double) sp.getId(), "dir", new Vec3(0, 1, 0), "face", new Vec3(0, 0, 1), "life", 2.0,
                    "color", JOB_COLORS[3][0], "glow", JOB_COLORS[3][1], "scale", 1.3, "pop", 0.25, "shine", 0.2));
        });
        ctx.waitTicks(10);
        ctx.takeScreenshot(TestScreenshotOptions.of("fx_scene_axes").withSize(1280, 720));
        check(ctx.computeOnClient(mc -> FxModels.live("phantom_axe")) == 4, "axes " + ctx.computeOnClient(mc -> FxModels.live("phantom_axe")));
        ctx.runOnClient(mc -> {
            FxModels.clear();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
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
