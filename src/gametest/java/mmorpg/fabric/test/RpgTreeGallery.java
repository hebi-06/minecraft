package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.tree.TreeBlocks;
import mmorpg.fabric.tree.TreeLog;
import mmorpg.fabric.tree.TreePlan;
import mmorpg.fabric.tree.WorldTree;
import mmorpg.fabric.ui.Forms;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=tree): 평평한 세계에 세계수 (높이 64) 를 심음 — /worldtree 와 같은 흐름 (WorldTree.plantAt: 설계 → 살핌 → 자리 표시 · 확인 창
 * → 심기) 으로, 단계 앞에서 잠시 멈추며 (WorldTree.holdBeforeStage) 자라는 중 (뿌리 · 줄기 · 가지 · 잎) 을 찍고, 다 자란 모습 (멀리서 · 밑동 · 잎 아래 빛나는 열매 ·
 * 밤) 과 되돌린 뒤.
 * 확인 항목: 확인 창 (제목 · 본문) 이 클라이언트에 뜸, 멈춘 단계, 다 자람 (모든 칸 · 건너뛴 곳 · 못 놓은 곳 0), 세계 = 설계, 클라이언트의 줄기 · 떨어지지 않는 잎 · 열매 덩굴,
 * 되돌린 뒤 처음과 칸마다 같음 · 기록 지움. 사진 이름 t_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgTreeGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-tree-gallery");
    /** 평평한 세계에서 서는 높이 (= 밑동) */
    private static final int GROUND = -60;
    private static final BlockPos BASE = new BlockPos(8, GROUND, 56);
    private static final int H = 64;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("tree")) return;
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
                mc.options.renderDistance().set(6);
            });
            ctx.waitTicks(20);
            try {
                tree(ctx, server, world);
            } finally {
                WorldTree.holdBeforeStage = null;
            }
        }
        LOG.info("[tree-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("tree gallery failed: " + String.join(" / ", failures));
    }

    private void ok(String what) {
        LOG.info("[tree-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[tree-gallery] FAIL {}", what);
        failures.add(what);
    }

    private void check(boolean ok, String what) {
        if (ok) ok(what);
        else fail(what);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    // ------------------------------------------------------------ 카메라

    /** (px, py, pz) 에서 (tx, ty, tz) 를 봄 (관전 모드 — 발 높이 py) */
    private static void look(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        double dy = ty - (py + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", px, py, pz, yaw, pitch));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(yaw, pitch);
    }

    /** 밑동 기준 (dx, dy, dz) 에서 밑동 기준 (tx, ty, tz) 를 봄 */
    private static void lookRel(ClientGameTestContext ctx, TestServerContext server, double dx, double dy, double dz, double tx, double ty, double tz) {
        double bx = BASE.getX() + 0.5;
        double bz = BASE.getZ() + 0.5;
        look(ctx, server, bx + dx, BASE.getY() + dy, bz + dz, bx + tx, BASE.getY() + ty, bz + tz);
    }

    private static void shot(ClientGameTestContext ctx, TestSingleplayerContext world, String name, int chunkWait) {
        waitChunks(ctx, world, name, chunkWait);
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(960, 540));
    }

    /** 창 사진: 창은 화면 크기에 맞춰 놓이므로 줄이지 않고 화면 그대로 */
    private static void formShot(ClientGameTestContext ctx, TestSingleplayerContext world, String name, int chunkWait) {
        waitChunks(ctx, world, name, chunkWait);
        ctx.takeScreenshot(name);
    }

    /** 둘레 청크가 다 그려질 때까지 (CI 는 소프트웨어 그리기라 다 그려지지 않을 때가 많음 — 시간이 다 되면 그대로 찍음) */
    private static void waitChunks(ClientGameTestContext ctx, TestSingleplayerContext world, String name, int chunkWait) {
        try {
            world.getConnection().waitForChunksRender(true, chunkWait);
        } catch (AssertionError e) {
            LOG.info("[tree-gallery] chunks not all rendered in {} ticks — taking {} anyway", chunkWait, name);
        }
        ctx.waitTicks(4);
    }

    // ------------------------------------------------------------ 세계수

    private void tree(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world) {
        TreePlan plan = TreePlan.plan(H, TreePlan.seedOf(BASE.getX(), BASE.getY(), BASE.getZ()));
        int trunk1 = -1;
        int trunkMid = -1;
        int leaf1 = -1;
        int glow = -1;
        int n = 0;
        for (int i = 0; i < plan.stages.size(); i++) {
            String k = plan.stages.get(i).kind();
            if (k.equals("trunk") && trunk1 < 0) trunk1 = i;
            if (k.equals("trunk") && ++n == 4) trunkMid = i;
            if (k.equals("leaf") && leaf1 < 0) leaf1 = i;
            if (k.equals("glow")) glow = i;
        }
        LOG.info("[tree-gallery] plan H {} seed {} total {} reach {} canopy {} top {} stages {} (trunk {} · mid {} · leaf {} · glow {})", plan.H, plan.seed, plan.total, plan.reach,
                plan.canopy, plan.top, plan.stages.size(), trunk1, trunkMid, leaf1, glow);
        // 처음 모습 (되돌린 뒤와 비교)
        Map<BlockPos, BlockState> before = server.computeOnServer(s -> snapshot(player(s).level(), plan));
        // 심기: /worldtree 와 같은 흐름 → 자리 표시 · 확인 창
        int[] holds = {trunk1, trunkMid, leaf1, glow};
        int[] at = {0};
        WorldTree.holdBeforeStage = i -> at[0] < holds.length && i == holds[at[0]];
        lookRel(ctx, server, 34, 22, 34, 0, 2, 0);
        server.runOnServer(s -> WorldTree.plantAt(player(s), player(s).level(), BASE, H));
        boolean asked = false;
        for (int t = 0; t < 600 && !asked; t += 5) {
            ctx.waitTicks(5);
            asked = ctx.computeOnClient(mc -> mc.gui.screen() instanceof MenuFormScreen);
        }
        check(asked, "plant window on the client");
        if (!asked) return;
        String body = server.computeOnServer(s -> Forms.pending(player(s)) ? "pending" : "none");
        check("pending".equals(body), "server waits for the answer");
        ctx.waitTicks(10);
        formShot(ctx, world, "t_ask", 60);
        server.runOnServer(s -> Forms.answer(player(s), 0, null));
        // 자라는 중: 단계 앞에서 멈춤 → 사진
        String[] names = {"t_grow1_roots", "t_grow2_trunk", "t_grow3_branches", "t_grow4_leaves"};
        double[][] cams = {{30, 26, 30, 0, 0, 0}, {30, 12, 30, 0, 10, 0}, {40, 22, 36, 0, 28, 0}, {48, 16, 40, 0, 30, 0}};
        for (int k = 0; k < holds.length; k++) {
            int stage = holds[k];
            boolean held = false;
            for (int t = 0; t < 1200 && !held; t += 5) {
                ctx.waitTicks(5);
                held = server.computeOnServer(s -> WorldTree.heldAt() == stage);
            }
            check(held, "growing: held before stage " + stage + " (" + plan.stages.get(stage).kind() + ")");
            if (!held) return;
            if (k == 0) ctx.waitTicks(60); // 시작 큰 글자가 걷힌 뒤
            double[] c = cams[k];
            lookRel(ctx, server, c[0], c[1], c[2], c[3], c[4], c[5]);
            shot(ctx, world, names[k], k == 0 ? 120 : 60);
            at[0]++;
        }
        WorldTree.holdBeforeStage = null;
        // 다 자람
        boolean done = false;
        for (int t = 0; t < 2400 && !done; t += 5) {
            ctx.waitTicks(5);
            done = server.computeOnServer(s -> WorldTree.treeJob() == null && WorldTree.lastResult() != null);
        }
        check(done, "grown");
        if (!done) return;
        WorldTree.Result r = server.computeOnServer(s -> WorldTree.lastResult());
        check(r.placed() == plan.total && r.skipped() == 0 && r.errors() == 0, "every block placed: " + r.placed() + " / " + plan.total + " (skipped " + r.skipped() + ", errors "
                + r.errors() + ")");
        String same = server.computeOnServer(s -> compare(player(s).level(), plan));
        check(same.startsWith("ok"), "world = plan (" + same + ")");
        String client = ctx.computeOnClient(mc -> {
            BlockState trunk = mc.level.getBlockState(BASE.above(6));
            if (trunk.getBlock() != Blocks.PALE_OAK_WOOD && trunk.getBlock() != Blocks.MOSS_BLOCK && trunk.getBlock() != Blocks.RESIN_BLOCK) return "trunk " + trunk;
            int leaves = 0;
            int persistent = 0;
            int berries = 0;
            for (TreePlan.Op o : plan.allOps()) {
                BlockState st = mc.level.getBlockState(BASE.offset(o.dx(), o.dy(), o.dz()));
                if (st.getBlock() == Blocks.AZALEA_LEAVES || st.getBlock() == Blocks.FLOWERING_AZALEA_LEAVES) {
                    leaves++;
                    if (st.getValue(BlockStateProperties.PERSISTENT)) persistent++;
                }
                if ((st.getBlock() == Blocks.CAVE_VINES || st.getBlock() == Blocks.CAVE_VINES_PLANT) && st.getValue(BlockStateProperties.BERRIES)) berries++;
            }
            return leaves > 0 && persistent == leaves && berries > 0 ? "ok " + leaves + " leaves, " + berries + " glow berries" : "leaves " + leaves + " persistent " + persistent
                    + " berries " + berries;
        });
        check(client.startsWith("ok"), "client sees the tree (" + client + ")");
        // 다 자란 순간: 큰 글자 · 빛 고리 · 토템 빛 (같은 자리에서 — 청크는 이미 그려짐)
        shot(ctx, world, "t_done", 10);
        ctx.waitTicks(80); // 끝 큰 글자가 걷힌 뒤
        lookRel(ctx, server, 48, 16, 40, 0, 30, 0);
        shot(ctx, world, "t_full", 60);
        lookRel(ctx, server, -46, 24, -36, 0, 30, 0);
        shot(ctx, world, "t_full_back", 100);
        // 밑동: 버팀 뿌리 · 이끼 · 이끼 깔개 · 반딧불이 덤불 · 꽃 핀 진달래
        lookRel(ctx, server, 15, 2.5, 19, 0, 5, 0);
        shot(ctx, world, "t_base", 60);
        // 잎 아래: 발광 열매 덩굴 · 포자 꽃 · 버섯불
        lookRel(ctx, server, 13, 12, 9, -4, 34, -3);
        shot(ctx, world, "t_under", 60);
        // 밤
        server.runCommand("time set 18000");
        ctx.waitTicks(20);
        lookRel(ctx, server, 40, 12, 34, 0, 28, 0);
        shot(ctx, world, "t_night", 60);
        lookRel(ctx, server, 13, 12, 9, -4, 34, -3);
        shot(ctx, world, "t_night_under", 40);
        server.runCommand("time set 6000");
        // 되돌리기 (확인 창 → 되돌림)
        server.runOnServer(s -> WorldTree.undoTree(player(s)));
        boolean askedUndo = false;
        for (int t = 0; t < 200 && !askedUndo; t += 5) {
            ctx.waitTicks(5);
            askedUndo = ctx.computeOnClient(mc -> mc.gui.screen() instanceof MenuFormScreen);
        }
        check(askedUndo, "undo window on the client");
        if (!askedUndo) return;
        lookRel(ctx, server, 48, 16, 40, 0, 30, 0);
        formShot(ctx, world, "t_undo_ask", 40);
        server.runOnServer(s -> Forms.answer(player(s), 0, null));
        boolean undone = false;
        for (int t = 0; t < 2400 && !undone; t += 5) {
            ctx.waitTicks(5);
            undone = server.computeOnServer(s -> WorldTree.treeJob() == null && WorldTree.lastResult() != null && WorldTree.lastResult().undo());
        }
        check(undone, "undone");
        if (!undone) return;
        WorldTree.Result u = server.computeOnServer(s -> WorldTree.lastResult());
        check(u.restored() == plan.total && u.changed() == 0 && u.failed() == 0, "every block restored: " + u.restored() + " / " + plan.total);
        // 처음과 같음 — 나무 칸이 아닌 곳에서 풀이 흙이 된 것 (나무 밑 그늘: 바닐라 풀은 빛이 없으면 흙이 됨, 애드온도 같음) 은 따로 셈
        Set<BlockPos> cells = new HashSet<>();
        for (TreePlan.Op o : plan.allOps()) if (o.mode() != TreePlan.Mode.CARPET && o.mode() != TreePlan.Mode.BUSH) cells.add(BASE.offset(o.dx(), o.dy(), o.dz()));
        String back = server.computeOnServer(s -> {
            ServerLevel level = player(s).level();
            int bad = 0;
            int decayed = 0;
            String first = "";
            for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
                BlockState now = level.getBlockState(e.getKey());
                if (now == e.getValue()) continue;
                if (!cells.contains(e.getKey()) && e.getValue().getBlock() == Blocks.GRASS_BLOCK && now.getBlock() == Blocks.DIRT) {
                    decayed++;
                    continue;
                }
                if (bad++ == 0) first = e.getKey().toShortString() + " " + e.getValue() + " → " + now;
            }
            TreeLog.Rec rec = TreeLog.last(s);
            return bad == 0 && rec == null ? "ok " + before.size() + " cells (grass gone to dirt in the shade: " + decayed + ")" : bad + " cells differ (" + first + "), record " + rec;
        });
        check(back.startsWith("ok"), "back as it was, record dropped (" + back + ")");
        ctx.waitTicks(20);
        String clientBack = ctx.computeOnClient(mc -> {
            int left = 0;
            for (BlockPos p : cells) if (p.getY() >= GROUND && !mc.level.getBlockState(p).isAir()) left++;
            return left == 0 ? "ok" : left + " tree blocks still on the client";
        });
        check(clientBack.equals("ok"), "client: no tree blocks left above ground (" + clientBack + ")");
        lookRel(ctx, server, 48, 16, 40, 0, 30, 0);
        shot(ctx, world, "t_undone", 200);
    }

    /** 나무 자리의 지금 블록 (설계 칸, 깔개 · 덤불은 그 둘레 기둥) */
    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, TreePlan plan) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        for (TreePlan.Op o : plan.allOps()) {
            boolean ground = o.mode() == TreePlan.Mode.CARPET || o.mode() == TreePlan.Mode.BUSH;
            for (int dy = -5; dy <= 2; dy++) {
                if (!ground && dy != 0) continue;
                BlockPos p = BASE.offset(o.dx(), o.dy() + dy, o.dz());
                if (level.isOutsideBuildHeight(p)) continue;
                m.put(p, level.getBlockState(p));
            }
        }
        return m;
    }

    /** 세계 = 설계 (깔개 · 덤불은 그 기둥 어딘가) → "ok N칸" 또는 다른 칸 */
    private static String compare(ServerLevel level, TreePlan plan) {
        int bad = 0;
        String first = "";
        for (TreePlan.Op o : plan.allOps()) {
            boolean ground = o.mode() == TreePlan.Mode.CARPET || o.mode() == TreePlan.Mode.BUSH;
            boolean found = false;
            for (int dy = ground ? 2 : 0; dy >= (ground ? -4 : 0); dy--) {
                if (level.getBlockState(BASE.offset(o.dx(), o.dy() + dy, o.dz())).getBlock() == TreeBlocks.block(o.block())) found = true;
            }
            if (!found && bad++ == 0) first = o.toString();
        }
        return bad == 0 ? "ok " + plan.total + " cells" : bad + " cells differ, e.g. " + first;
    }
}
