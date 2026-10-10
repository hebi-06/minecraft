package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.AddonFx;
import mmorpg.fabric.AddonFxTable;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 애드온 파티클 사진첩 (화면 시험): 119종을 20개씩 벽처럼 늘어놓고 (이름표와 함께) 사진 — 그림 · UV · 방향 · 직사각형 나눔 확인.
 * 변수는 보기 쉬운 기본값 (크기 0.9 · 수명 6초 · 흰색 · 첫 칸 — 특별한 변수 (마법진 progress 등) 는 1).
 * 면이 방향을 따르는 것 (direction_z) 은 카메라 쪽, 길쭉한 빛줄기 (lookat_direction) 는 가로. 사진 이름 pfx_<n>.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgParticleGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-particle-gallery");
    private static final int PER_SHOT = 20;
    private static final int COLS = 5;
    private static final double GAP_X = 2.6;
    private static final double GAP_Y = 2.2;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 18000");
            server.runCommand("weather clear");
            // 카메라: 남쪽 (z = 13, 땅 위) 에서 북쪽 벽 (z = 0.5) 을 봄
            server.runCommand("tp @a 0.5 -60 13 180 0");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(20);
            String[] ids = AddonFxTable.PARTICLES;
            for (int shot = 0; shot * PER_SHOT < ids.length; shot++) {
                int from = shot * PER_SHOT;
                int to = Math.min(ids.length, from + PER_SHOT);
                List<String> batch = new ArrayList<>();
                for (int i = from; i < to; i++) batch.add(ids[i]);
                server.runCommand("kill @e[type=minecraft:text_display]");
                for (int i = 0; i < batch.size(); i++) {
                    Vec3 at = spot(i);
                    String name = batch.get(i).replace("rpg:", "");
                    server.runCommand(String.format(java.util.Locale.ROOT,
                            "summon minecraft:text_display %.2f %.2f %.2f {billboard:\"center\",text:\"%s\",background:0,see_through:1b,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]}}",
                            at.x, at.y - 1.05, at.z + 0.2, name));
                }
                ctx.waitTicks(3);
                server.runOnServer(s -> {
                    ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
                    for (int i = 0; i < batch.size(); i++) spawn(level, batch.get(i), spot(i));
                });
                ctx.waitTicks(6);
                ctx.takeScreenshot(TestScreenshotOptions.of(String.format("pfx_%02d", shot)).withSize(1280, 720));
                LOG.info("[particle-gallery] shot {} ({} .. {})", shot, batch.get(0), batch.get(batch.size() - 1));
                ctx.waitTicks(130); // 수명 6초 — 다음 묶음 전에 사라지게
            }
            server.runCommand("kill @e[type=minecraft:text_display]");
        }
    }

    private static Vec3 spot(int i) {
        int c = i % COLS;
        int r = i / COLS;
        // 맨 아랫줄이 땅 (y -60) 위 1칸 — 눈높이 (-58.4) 위로 네 줄
        return new Vec3(0.5 + (c - (COLS - 1) / 2.0) * GAP_X, -59.0 + (3 - r) * GAP_Y, 0.5);
    }

    private static void spawn(ServerLevel level, String id, Vec3 at) {
        // 면이 방향을 따르는 것은 카메라 쪽 (+z), 길쭉한 것은 가로 (+x)
        Vec3 dir = switch (id) {
            case "rpg:rfx_nova", "rpg:rfx_sigil", "rpg:rfx_crack", "rpg:fire_wing", "rpg:rfx_wings", "rpg:rfx_moon", "rpg:rfx_sun", "rpg:rfx_emblem",
                    "rpg:rfx_hole", "rpg:rfx_hex" -> new Vec3(0, 0, 1);
            default -> new Vec3(1, 0, 0);
        };
        AddonFx.Vars v = AddonFx.vars().vec("dir", dir).vec("move", dir).vec("vel", Vec3.ZERO).set("size", 0.9).set("life", 6).set("speed", 0)
                .set("thick", 0.3).set("delay", 0).set("grow", 0).set("progress", 1).set("spin", 0).set("spin_rate", 0).set("cell", 0).set("frames", 1)
                .set("fps", 0).set("loop", 0).set("phase", 0).set("fade", 0.2).set("frame", 0).set("kind", 0).set("seg", 0).set("rate", 20)
                .set("reach", 0).set("fold", 99).set("rise", 0.3).set("sink", 0).set("grav", 0).set("wax", 1).set("close", 0)
                .color("color", new double[] {1, 1, 1}, 1);
        AddonFx.spawn(level, id, at, v);
    }
}
