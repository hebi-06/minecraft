package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaBlocks;
import mmorpg.fabric.boss.ArenaBuild;
import mmorpg.fabric.boss.ArenaThemes;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.npc.NpcData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=arena): 보스 필드 신전 9종을 평평한 세계에 서로 떨어뜨려 지음 (제단을 놓고 Arena.buildArena — 단계별 짓기 그대로)
 * → 위 · 비스듬히 · 땅 높이에서 사진 (수르트는 더 넓게 · 사슬 기둥 가까이). 제단 블록 9종 줄 사진 (텍스처).
 * 평평한 세계의 땅 (서는 높이 -60) 에서는 필드 높이가 맞지 않으므로 (애드온과 같은 높이 규칙: 제단 y ≥ 바닥 + 9) 제단을 -55 에 놓고
 * 둘레 땅을 흙 · 풀로 메워 올림 — 신전 밖 땅이 제단 높이와 같게.
 * 확인 항목: 다 지음 · 못 놓은 묶음 0 · 세계의 블록이 설계 (ArenaThemes.planOps 를 차례대로 놓은 것) 와 칸마다 같음 · 클라이언트에 제단 블록이 보임.
 * 사진 이름 a_&lt;보스&gt;_top · _34 · _ground (+ a_surtr_wide · a_surtr_chain), a_altars → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgArenaGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-arena-gallery");
    /** 제단 높이 (= 신전 안에서 서는 높이) */
    private static final int ALTAR_Y = -55;
    /** 평평한 세계에서 서는 높이 */
    private static final int GROUND = -60;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("arena")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("gamemode spectator @a");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(20);
            altarRow(ctx, server, world);
            int x = 0;
            String prev = null;
            for (String key : Arena.BOSSES.keys()) {
                int R = radius(key);
                if (prev != null) x += radius(prev) + R + 34;
                temple(ctx, server, world, key, x, 0);
                prev = key;
            }
        }
        LOG.info("[arena-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("arena gallery failed: " + String.join(" / ", failures));
    }

    private static int radius(String key) {
        return (int) Arena.BOSSES.obj(key).obj("arena").num("radius");
    }

    private static int height(String key) {
        return (int) Arena.BOSSES.obj(key).obj("arena").num("height");
    }

    private void ok(String what) {
        LOG.info("[arena-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[arena-gallery] FAIL {}", what);
        failures.add(what);
    }

    private static ServerLevel level(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0).level();
    }

    // ------------------------------------------------------------ 카메라

    /** (px, py, pz) 에서 (tx, ty, tz) 를 봄 (관전 모드 — 발 높이 py) */
    private static void look(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        double dy = ty - (py + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
        camera(ctx, server, px, py, pz, yaw, pitch);
    }

    private static void camera(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, float yaw, float pitch) {
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(yaw, pitch);
    }

    private static void shot(ClientGameTestContext ctx, TestSingleplayerContext world, String name) {
        waitChunks(ctx, world, 1200);
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(960, 540));
    }

    /** 둘레 청크가 다 그려질 때까지 (CI 는 소프트웨어 그리기라 느림 — 시간이 다 되면 그대로 찍음) */
    private static void waitChunks(ClientGameTestContext ctx, TestSingleplayerContext world, int timeout) {
        try {
            world.getConnection().waitForChunksRender(true, timeout);
        } catch (AssertionError e) {
            LOG.warn("[arena-gallery] chunks not all rendered in {} ticks — taking the shot anyway", timeout);
            ctx.waitTicks(20);
        }
    }

    // ------------------------------------------------------------ 제단 블록 줄

    /** 제단 블록 9종을 한 줄로 놓고 비스듬히 위에서 (텍스처: 위 · 옆) */
    private void altarRow(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world) {
        List<String> keys = new ArrayList<>(Arena.BOSSES.keys());
        int z = -70;
        server.runOnServer(s -> {
            ServerLevel level = level(s);
            for (int i = 0; i < keys.size(); i++) level.setBlock(new BlockPos(-8 + 2 * i, GROUND, z), AltarBlocks.block(keys.get(i)).defaultBlockState(), Block.UPDATE_ALL);
        });
        look(ctx, server, 0.5, GROUND + 3.5, z + 8.5, 0.5, GROUND + 0.5, z + 0.5);
        String r = ctx.computeOnClient(mc -> {
            for (int i = 0; i < keys.size(); i++) {
                BlockState s = mc.level.getBlockState(new BlockPos(-8 + 2 * i, GROUND, z));
                if (!(s.getBlock() instanceof AltarBlocks.AltarBlock a) || !a.key.equals(keys.get(i))) return "client sees " + s + " for " + keys.get(i);
            }
            return "ok";
        });
        if ("ok".equals(r)) ok("altar blocks on the client");
        else fail(r);
        shot(ctx, world, "a_altars");
    }

    // ------------------------------------------------------------ 신전 하나

    private void temple(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world, String key, int x, int z) {
        int R = radius(key);
        int H = height(key);
        double cx = x + 0.5;
        double cz = z + 0.5;
        // 먼저 위로 가서 둘레를 불러오게 함 (수르트는 넓어서 그리는 거리를 8 청크로)
        if (key.equals("surtr")) ctx.runOnClient(mc -> mc.options.renderDistance().set(8));
        camera(ctx, server, cx, ALTAR_Y + 30, cz, 180F, 90F);
        waitChunks(ctx, world, key.equals("surtr") ? 3600 : 1200);
        String[] hold = new String[1];
        Arena.Altar[] altar = new Arena.Altar[1];
        String started = server.computeOnServer(s -> {
            ServerLevel level = level(s);
            BlockPos pos = new BlockPos(x, ALTAR_Y, z);
            hold[0] = Ticking.holdArea(level, Vec3.atCenterOf(pos), Math.min(R + 12, 60), "g");
            // 둘레 땅을 제단 높이까지 메움 (흙 + 풀, 신전보다 12칸 넓게)
            int half = R + 12;
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    for (int y = GROUND; y < ALTAR_Y; y++) {
                        m.set(x + dx, y, z + dz);
                        level.setBlock(m, y == ALTAR_Y - 1 ? grass : dirt, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
            level.setBlock(pos, AltarBlocks.block(key).defaultBlockState(), Block.UPDATE_ALL);
            Arena.Altar a = Arena.addAltar(key, NpcData.dimId(level), pos);
            altar[0] = a;
            String why = Arena.buildProblem(a);
            if (why != null) return "build problem: " + why;
            ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
            return Arena.buildArena(a, sp) ? "ok" : "build did not start";
        });
        if (!"ok".equals(started)) {
            fail(key + " " + started);
            return;
        }
        // 다 지을 때까지 (최대 3분 — CI 는 느림)
        boolean done = false;
        for (int t = 0; t < 3600 / 10; t++) {
            ctx.waitTicks(10);
            if (server.computeOnServer(s -> altar[0].built && !Arena.isBuilding(altar[0]))) {
                done = true;
                break;
            }
        }
        if (!done) {
            fail(key + " not built in 3 min");
            return;
        }
        int errors = server.computeOnServer(s -> ArenaBuild.lastErrors(altar[0]));
        if (errors == 0) ok(key + " built (H " + H + ", R " + R + ")");
        else fail(key + " built with " + errors + " failed batches");
        // 세계 = 설계
        String same = server.computeOnServer(s -> compare(level(s), altar[0]));
        if (same.startsWith("ok")) ok(key + " world matches the design (" + same + ")");
        else fail(key + " " + same);
        ctx.waitTicks(10);
        // 위에서 (북쪽이 위, 동쪽이 오른쪽)
        double top = (R + 5) * 1.45;
        camera(ctx, server, cx, ALTAR_Y + top, cz, 180F, 90F);
        String seen = ctx.computeOnClient(mc -> mc.level.getBlockState(new BlockPos(x, ALTAR_Y, z)).getBlock() instanceof AltarBlocks.AltarBlock ? "ok" : "client altar " + mc.level.getBlockState(new BlockPos(x, ALTAR_Y, z)));
        if ("ok".equals(seen)) ok(key + " altar on the client");
        else fail(key + " " + seen);
        shot(ctx, world, "a_" + key + "_top");
        // 비스듬히 (남동쪽 위에서 가운데를)
        double d = R * 1.25 + 8;
        look(ctx, server, cx + d * 0.72, ALTAR_Y + R * 0.75 + 6, cz + d * 0.72, cx, ALTAR_Y + 1, cz);
        shot(ctx, world, "a_" + key + "_34");
        // 땅 높이 (남쪽 성문 안에서 북쪽 가운데를)
        look(ctx, server, cx, ALTAR_Y, cz + R - 2.5, cx, ALTAR_Y + 2.2, cz);
        shot(ctx, world, "a_" + key + "_ground");
        if (key.equals("surtr")) {
            look(ctx, server, cx + 82, ALTAR_Y + 58, cz + 70, cx, ALTAR_Y + 4, cz);
            shot(ctx, world, "a_surtr_wide");
            ArenaThemes.Spot chain = ArenaThemes.polar(ArenaThemes.muspelSize("surtr").chains(), 45);
            look(ctx, server, cx + chain.dx() - 9, ALTAR_Y + 2, cz + chain.dz() - 4, cx + chain.dx(), ALTAR_Y + 7, cz + chain.dz());
            shot(ctx, world, "a_surtr_chain");
        }
        server.runOnServer(s -> Ticking.releaseArea(hold[0]));
    }

    /** 설계를 차례대로 놓았을 때의 블록과 세계를 칸마다 비교 → "ok N칸" 또는 다른 칸 */
    private static String compare(ServerLevel level, Arena.Altar a) {
        ArenaThemes.Plan plan = Arena.arenaPlan(a);
        Map<BlockPos, BlockState> want = new HashMap<>();
        for (ArenaThemes.StageOps st : ArenaThemes.planOps(a.x, a.y, a.z, Arena.arenaSize(a).radius(), plan)) {
            for (ArenaThemes.Op op : st.ops()) {
                BlockState s = ArenaBlocks.state(op.block(), op.states());
                for (int x = Math.min(op.x(), op.x2()); x <= Math.max(op.x(), op.x2()); x++) {
                    for (int y = Math.min(op.y(), op.y2()); y <= Math.max(op.y(), op.y2()); y++) {
                        for (int z = Math.min(op.z(), op.z2()); z <= Math.max(op.z(), op.z2()); z++) want.put(new BlockPos(x, y, z), s);
                    }
                }
            }
        }
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (Map.Entry<BlockPos, BlockState> e : want.entrySet()) {
            BlockState got = level.getBlockState(e.getKey());
            if (got != e.getValue()) {
                n++;
                if (bad.size() < 4) bad.add(e.getKey().toShortString() + " " + got + " != " + e.getValue());
            }
        }
        if (!ArenaBlocks.problems().isEmpty()) return "unmapped blocks " + ArenaBlocks.problems();
        return n == 0 ? "ok " + want.size() + " cells" : n + " cells differ: " + String.join(" | ", bad);
    }
}
