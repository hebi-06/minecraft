package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossWorm;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.npc.NpcData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=worm): 평평한 세계에 거대 웜의 심층의 신전 (체크무늬 바닥 · 굴 다섯) 을 짓고 진짜로 싸움 —
 * 제단 누르기 → 도전 창 → 도전 (진짜 클릭) → 소환진 → 가운데 굴에서 튀어나옴, 머리 내려찍기 (붉은 부채꼴 110°), 몸통 휘두르기 (주황 270° → 머리가 쓸고 감),
 * 굴 이동 (땅속으로 → 사람이 있는 굴에 주황 원 · 금 → 튀어나옴 — 엔진 seat), 대지 분출 (위에서: 어두운 칸 네모 → 솟음 → 밝은 칸 네모 → 솟음, 땅에서: 방금 솟은 칸으로 옮김),
 * 삼키기 (노란 원이 따라다님 → 붉게 멈춤 → 삼킴: 실명 · 보스 막대에 구출 진행 → 뱉음 + 구역질), 대지 가르기 (주황 줄 → 바위가 솟아 달려옴),
 * 진동 감지 (땅속 · 굴마다 소리 물결 · 3 2 1 · 듣는 중 · 머리 위 돌 → 아무 굴에서 튀어나옴 — 그동안 플레이어는 움직이지 않음),
 * 원거리 견제 (바위 뱉기), 쓰러뜨림 (보상 · 보스 재료). 패턴은 모듈 함수를 바로 부르고 그 사이 웜이 스스로 고르지 않게 멈춤 (BossWorm.M 의 다음 틱을 멀리).
 * 사진 이름 wm_* → ci-logs 의 shots/. 확인 항목은 [worm-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgWormGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-worm-gallery");
    private static final String KEY = "great_worm";
    /** 제단 높이 (= 신전 안에서 서는 높이 — 평평한 세계의 땅 -60 에서 높이 규칙 때문에 메워 올림) */
    private static final int ALTAR_Y = -55;
    private static final int GROUND = -60;
    private static final long FAR_FUTURE = Long.MAX_VALUE / 4;

    private final List<String> failures = new ArrayList<>();
    private Arena.Altar altar;
    private double cx;
    private double cz;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("worm")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("gamerule doMobSpawning false");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(20);
            if (!build(ctx, server, world)) return;
            prepare(ctx, server);
            if (!challenge(ctx, server, conn)) return;
            melee(ctx, server);
            sweep(ctx, server);
            tunnel(ctx, server);
            quake(ctx, server);
            devour(ctx, server);
            rend(ctx, server);
            tremor(ctx, server);
            far(ctx, server);
            kill(ctx, server);
        } finally {
            LOG.info("[worm-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError("worm gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도우미

    private void ok(String what) {
        LOG.info("[worm-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[worm-gallery] FAIL {}", what);
        failures.add(what);
    }

    private void check(boolean good, String what) {
        if (good) ok(what);
        else fail(what);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static ServerLevel level(MinecraftServer s) {
        return player(s).level();
    }

    private Bosses.Enc enc() {
        return altar == null ? null : Bosses.encounterAt(altar);
    }

    /** 카메라 (플레이어) 를 (px, py, pz) 에 두고 (tx, ty, tz) 를 봄 — py = 발 높이 */
    private static void look(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        double dx = tx - px;
        double dz = tz - pz;
        double dy = ty - (py + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", px, py, pz, yaw, pitch));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(yaw, pitch);
        ctx.waitTicks(1);
    }

    private static void camera(ClientGameTestContext ctx, CameraType type) {
        ctx.runOnClient(mc -> mc.options.setCameraType(type));
    }

    /** 3인칭 카메라 거리 (속성 CAMERA_DISTANCE, 기본 4 — 서버 · 클라이언트 둘 다) */
    private static void camDist(ClientGameTestContext ctx, TestServerContext server, double d) {
        server.runOnServer(s -> {
            AttributeInstance a = player(s).getAttribute(Attributes.CAMERA_DISTANCE);
            if (a != null) a.setBaseValue(d);
        });
        ctx.runOnClient(mc -> {
            AttributeInstance a = mc.player == null ? null : mc.player.getAttribute(Attributes.CAMERA_DISTANCE);
            if (a != null) a.setBaseValue(d);
        });
    }

    /** 뒤 3인칭 사진 자리: 플레이어를 (px, pz) 에 세우고 (tx, ty, tz) 를 보게 — 카메라는 시선 뒤로 dist 칸 */
    private static void chase(ClientGameTestContext ctx, TestServerContext server, double px, double pz, double tx, double ty, double tz, double dist) {
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, dist);
        look(ctx, server, px, ALTAR_Y, pz, tx, ty, tz);
        ctx.waitTick();
        double got = ctx.computeOnClient(mc -> mc.player == null ? -1.0 : mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
        if (got < dist - 0.5) LOG.warn("[worm-gallery] camera pulled in: {} of {} blocks", String.format(Locale.ROOT, "%.2f", got), dist);
    }

    /** 관전 모드로 높은 곳에서 (tx, ty, tz) 를 봄 */
    private static void top(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        server.runCommand("gamemode spectator @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, px, py, pz, tx, ty, tz);
    }

    /** 땅으로 내려와 서바이벌 (떨어지지 않게 먼저 옮김) */
    private void ground(ClientGameTestContext ctx, TestServerContext server, double px, double pz) {
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f", px, ALTAR_Y, pz));
        ctx.waitTicks(2);
        server.runCommand("gamemode survival @a");
        ctx.waitTicks(3);
    }

    /** 패턴의 무작위 (BossKit.rand) 를 정한 수열로 돌려 run 을 부르고 되돌림 (서버 스레드에서) */
    private static void withRand(double[] seq, Runnable run) {
        DoubleSupplier prev = BossKit.rand;
        int[] i = {0};
        BossKit.rand = () -> seq[i[0]++ % seq.length];
        try {
            run.run();
        } finally {
            BossKit.rand = prev;
        }
    }

    /** 사진 — 창 크기 그대로 (큰 글자 · 보스 막대가 잘리지 않게), 알림 토스트는 치움 */
    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(name);
    }

    private static void waitChunks(ClientGameTestContext ctx, TestSingleplayerContext world, int timeout) {
        try {
            world.getConnection().waitForChunksRender(true, timeout);
        } catch (AssertionError e) {
            LOG.warn("[worm-gallery] chunks not all rendered in {} ticks — going on", timeout);
        }
    }

    /** 서버 틱이 at 이 될 때까지 (패턴의 판정 순간을 찍으려고) */
    private static void waitUntil(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 600 && server.computeOnServer(s -> Tasks.now()) < at; i++) ctx.waitTick();
    }

    private static long now(TestServerContext server) {
        return server.computeOnServer(s -> Tasks.now());
    }

    /**
     * 웜을 가운데 굴에 다시 앉히고 (엔진 seat — 바라볼 각도 ang) 하던 것을 비움: 무적 · 틀 · 비틀거림 · 땅속 · 분출 · 진동 감지.
     * 굴 동작 (rpg_dig) 은 튀어나온 모습 그대로
     */
    private void settle(TestServerContext server, double ang) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return;
            BossWorm.M m = BossWorm.mem(e);
            if (m != null) {
                m.under = null;
                m.quake = null;
                m.tremor = null;
                m.hole = 0;
            }
            e.immune = false;
            e.cast = null;
            e.staggerUntil = 0;
            Bosses.seat(e, new Vec3(cx, ALTAR_Y, cz), ang);
        });
    }

    /** 웜이 스스로 패턴 · 평타 · 원거리 견제를 고르지 않게 (사진첩이 차례로 부름) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossWorm.M m = BossWorm.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.tremorDone.addAll(List.of(0.7, 0.45, 0.2));
            m.bigUntil = FAR_FUTURE;
            e.meleeAt = FAR_FUTURE;
            e.farAt = FAR_FUTURE;
        });
    }

    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
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

    /** 진짜 왼쪽 클릭 공격 (조준한 것) */
    private static void attack(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(options -> options.keyAttack);
    }

    // ------------------------------------------------------------ 신전 짓기

    private boolean build(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world) {
        int R = (int) Arena.BOSSES.obj(KEY).obj("arena").num("radius");
        int x = 0;
        int z = 0;
        cx = x + 0.5;
        cz = z + 0.5;
        server.runCommand("gamemode spectator @a");
        look(ctx, server, cx, ALTAR_Y + 34, cz + 0.01, cx, ALTAR_Y, cz);
        waitChunks(ctx, world, 1200);
        String started = server.computeOnServer(s -> {
            ServerLevel level = level(s);
            BlockPos pos = new BlockPos(x, ALTAR_Y, z);
            Ticking.holdArea(level, Vec3.atCenterOf(pos), R + 12, "g");
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
            level.setBlock(pos, AltarBlocks.block(KEY).defaultBlockState(), Block.UPDATE_ALL);
            altar = Arena.addAltar(KEY, NpcData.dimId(level), pos);
            String why = Arena.buildProblem(altar);
            if (why != null) return "build problem: " + why;
            return Arena.buildArena(altar, player(s)) ? "ok" : "build did not start";
        });
        if (!"ok".equals(started)) {
            fail("temple " + started);
            return false;
        }
        for (int t = 0; t < 3600 / 10; t++) {
            ctx.waitTicks(10);
            if (server.computeOnServer(s -> altar.built && !Arena.isBuilding(altar))) {
                ok("deep temple built");
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /** 싸울 플레이어: 서바이벌 · 저항 V (사진을 찍는 동안 쓰러지지 않게) · 레벨 · 스탯 · 칼 */
    private void prepare(ClientGameTestContext ctx, TestServerContext server) {
        ground(ctx, server, cx, cz + 3);
        server.runCommand("effect give @a minecraft:resistance infinite 4 true");
        server.runCommand("xp set @a 40 levels");
        ctx.waitTicks(5);
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l10_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
    }

    // ------------------------------------------------------------ 장면

    /** 제단 누르기 (진짜 오른쪽 클릭) → 도전 창 → 도전 (진짜 클릭) → 소환진 → 가운데 굴에서 튀어나옴 (키 약 13칸) */
    private boolean challenge(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        look(ctx, server, cx, ALTAR_Y, cz + 3, cx, ALTAR_Y + 0.5, cz);
        ctx.getInput().pressKey(options -> options.keyUse);
        boolean opened;
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof MenuFormScreen m && m.form().title().contains("도전"), 60);
            opened = true;
        } catch (AssertionError | RuntimeException e) {
            opened = false;
        }
        if (!opened) {
            fail("pressing the altar did not open the challenge form — opening it from the server");
            server.runOnServer(s -> Bosses.onAltarTouch(player(s), altar));
            try {
                ctx.waitFor(mc -> mc.gui.screen() instanceof MenuFormScreen, 60);
            } catch (AssertionError | RuntimeException e) {
                fail("no challenge form at all");
                return false;
            }
        } else ok("pressing the altar opens the challenge form");
        ctx.waitTicks(3);
        String body = ctx.computeOnClient(mc -> mc.gui.screen() instanceof MenuFormScreen m ? m.form().text() : "");
        check(body.contains("Lv.25") && body.contains("바위 뱉기") && body.contains("15분") && body.contains("파쇄의 송곳니"),
                "challenge text (level · far shot · cooldown · boss materials)");
        shot(ctx, "wm_form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state));
        check(summoning, "도전 → summoning");
        if (!summoning) return false;
        look(ctx, server, cx, ALTAR_Y, cz + 16, cx, ALTAR_Y + 1.2, cz);
        ctx.waitTicks(22);
        shot(ctx, "wm_summon");
        look(ctx, server, cx, ALTAR_Y, cz + 17, cx, ALTAR_Y + 5.5, cz);
        for (int t = 0; t < 40 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        ctx.waitTicks(5);
        shot(ctx, "wm_emerge0");
        ctx.waitTicks(20);
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return "no boss";
            if (e.boss.getHealth() != 15000F || e.boss.getMaxHealth() != 15000F) return "hp " + e.boss.getHealth();
            return BossKit.hdist(e.boss.position(), new Vec3(cx, ALTAR_Y, cz)) < 0.1 ? "ok" : "not at the center hole " + e.boss.position();
        });
        check("ok".equals(r), "worm appeared at the center hole with 15000 hp (" + r + ")");
        int id = server.computeOnServer(s -> enc() != null && enc().boss != null ? enc().boss.getId() : -1);
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        check("ok".equals(rend), "worm drawn by BedrockRenderer (" + rend + ")");
        shot(ctx, "wm_appear");
        freeze(server);
        return true;
    }

    /** 머리 내려찍기 (엔진 평타): 붉은 부채꼴 110° (1초) → 내려찍음 */
    private void melee(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("title @a clear");
        settle(server, Math.PI / 2);
        chase(ctx, server, cx, cz + 4.5, cx, ALTAR_Y + 0.5, cz + 1.5, 10);
        boolean started = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            e.meleeAt = 0;
            boolean r = Bosses.melee(e, Tasks.now(), BossKit.fighters(e));
            e.meleeAt = FAR_FUTURE;
            return r && e.cast != null && "melee".equals(e.cast.id);
        });
        check(started, "head slam (melee) cast on the player in reach");
        ctx.waitTicks(12);
        shot(ctx, "wm_melee");
        ctx.waitTicks(10);
        shot(ctx, "wm_melee_hit");
        ctx.waitTicks(10);
    }

    /** 몸통 휘두르기: 대상 쪽으로 몸을 돌리고 앞 270° 주황 부채꼴 (반지름 8, 1.5초) → 머리가 한쪽 뒤에서 반대쪽 뒤까지 쓸어 버림 */
    private void sweep(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        chase(ctx, server, cx + 1, cz + 6.5, cx, ALTAR_Y, cz + 1, 13);
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            boolean r = BossWorm.sweep(e, Tasks.now(), List.of(player(s)));
            return r ? Tasks.now() : -1L;
        });
        check(t0 > 0 && server.computeOnServer(s -> enc().cast != null && "sweep".equals(enc().cast.id)), "sweep cast");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("sweep").num("windup");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "wm_sweep_tele");
        waitUntil(ctx, server, t0 + windup + 5);
        shot(ctx, "wm_sweep");
        waitUntil(ctx, server, t0 + windup + 12);
        shot(ctx, "wm_sweep_end");
        ctx.waitTicks(20);
    }

    /** 굴 이동 (엔진 seat): 가운데에서 땅속으로 (무적) → 플레이어 곁 굴 (+9, +9) 둘레에 주황 원 · 금 · 흙먼지 → 튀어나옴 (그 굴이 새 제 자리) */
    private void tunnel(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        chase(ctx, server, cx + 4, cz + 9.5, cx + 1, ALTAR_Y + 2, cz + 2, 9);
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            return BossWorm.tunnel(e, Tasks.now(), BossKit.fighters(e)) ? Tasks.now() : -1L;
        });
        check(t0 > 0 && server.computeOnServer(s -> BossWorm.mem(enc()).under != null && BossWorm.mem(enc()).under.dest == 1), "tunnel → the hole by the player");
        ctx.waitTicks(10);
        shot(ctx, "wm_dive");
        check(server.computeOnServer(s -> enc().immune), "underground = immune");
        Vec3 hole = server.computeOnServer(s -> enc().feat.holes().get(1));
        chase(ctx, server, cx + 4, cz + 9.5, hole.x, ALTAR_Y + 1, hole.z, 7);
        int sink = (int) Arena.BOSSES.obj(KEY).obj("tunnel").num("sink");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("tunnel").num("windup");
        waitUntil(ctx, server, t0 + sink + 14);
        String seat = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            return BossKit.hdist(e.home, hole) < 1e-6 && BossKit.hdist(e.boss.position(), hole) < 0.1 ? "ok" : "home " + e.home + " body " + e.boss.position();
        });
        check("ok".equals(seat), "seat moved home + body under the new hole (" + seat + ")");
        shot(ctx, "wm_tunnel_tele");
        waitUntil(ctx, server, t0 + sink + windup + 4);
        shot(ctx, "wm_emerge");
        check(server.computeOnServer(s -> !enc().immune && BossWorm.mem(enc()).hole == 1), "emerged at hole 1, no longer immune");
        ctx.waitTicks(20);
    }

    /** 대지 분출: 위에서 — 어두운 칸 (주황) 네모 2초 → 솟음 → 밝은 칸 (붉은) 네모 1.7초 → 솟음. 땅에서 — 밝은 칸에 서 있다가 방금 솟은 어두운 칸으로 옮김 */
    private void quake(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        int windup = (int) Arena.BOSSES.obj(KEY).obj("quake").num("windup");
        int gap = (int) Arena.BOSSES.obj(KEY).obj("quake").num("gap");
        top(ctx, server, cx + 0.01, ALTAR_Y + 34, cz + 9, cx, ALTAR_Y, cz + 1);
        ctx.waitTicks(5);
        long t0 = server.computeOnServer(s -> {
            withRand(new double[] {0.1}, () -> BossWorm.quake(enc(), Tasks.now()));
            return Tasks.now();
        });
        check(server.computeOnServer(s -> BossWorm.mem(enc()).quake != null && BossWorm.mem(enc()).quake.waves.get(0).dark), "quake: dark tiles first");
        waitUntil(ctx, server, t0 + 22);
        shot(ctx, "wm_quake_top");
        waitUntil(ctx, server, t0 + windup + 2);
        shot(ctx, "wm_quake_top_burst");
        waitUntil(ctx, server, t0 + windup + gap - 10);
        shot(ctx, "wm_quake_top2");
        waitUntil(ctx, server, t0 + windup + gap + 2);
        shot(ctx, "wm_quake_top_burst2");
        ctx.waitTicks(20);
        // 땅에서: 밝은 칸 (1, 2) 에 섬 → 어두운 칸이 솟은 뒤 방금 솟은 어두운 칸 (2, 2) 으로
        settle(server, Math.PI / 2);
        ground(ctx, server, cx + 3, cz + 6);
        chase(ctx, server, cx + 3, cz + 6, cx + 1, ALTAR_Y, cz + 1, 8);
        boolean colors = server.computeOnServer(s -> !BossWorm.onDark(enc(), player(s)));
        check(colors, "player on a light tile");
        long t1 = server.computeOnServer(s -> {
            withRand(new double[] {0.1}, () -> BossWorm.quake(enc(), Tasks.now()));
            return Tasks.now();
        });
        waitUntil(ctx, server, t1 + 24);
        shot(ctx, "wm_quake_ground");
        waitUntil(ctx, server, t1 + windup + 2);
        shot(ctx, "wm_quake_ground_burst");
        chase(ctx, server, cx + 6, cz + 6, cx + 3, ALTAR_Y, cz + 1, 8);
        check(server.computeOnServer(s -> BossWorm.onDark(enc(), player(s))), "moved onto the dark tile that just erupted");
        waitUntil(ctx, server, t1 + windup + gap + 2);
        shot(ctx, "wm_quake_ground_burst2");
        ctx.waitTicks(20);
    }

    /**
     * 삼키기: 웜이 몸을 젖히고 노란 원이 플레이어를 따라다님 (노림!) → 붉게 멈춤 → 덮쳐 삼킴 (실명 · 보스 막대 · 이름표에 구출 진행) →
     * 구출 피해가 차면 뱉음 + 구역질 (사진첩은 한 사람이라 피해를 서버에서 채움)
     */
    private void devour(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        chase(ctx, server, cx + 0.5, cz + 6, cx, ALTAR_Y + 3, cz + 1, 9);
        long t0 = server.computeOnServer(s -> BossWorm.devour(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L);
        check(t0 > 0, "devour cast");
        ctx.waitTicks(8);
        shot(ctx, "wm_devour_aim");
        // 옆으로 비켜도 원이 따라옴
        chase(ctx, server, cx + 2, cz + 6.5, cx, ALTAR_Y + 3, cz + 1, 9);
        int windup = (int) Arena.BOSSES.obj(KEY).obj("devour").num("windup");
        int lock = (int) Arena.BOSSES.obj(KEY).obj("devour").num("lock");
        waitUntil(ctx, server, t0 + windup - lock + 6);
        shot(ctx, "wm_devour_lock");
        waitUntil(ctx, server, t0 + windup + 5);
        String in = server.computeOnServer(s -> {
            BossWorm.M m = BossWorm.mem(enc());
            if (m.devour == null || !"inside".equals(m.devour.phase)) return "not swallowed " + (m.devour == null ? null : m.devour.phase);
            if (m.devour.victim != player(s)) return "victim " + m.devour.victim;
            if (enc().mod.targetable(enc(), player(s))) return "still a target";
            String bar = enc().boss.bossBar() == null ? "" : enc().boss.bossBar().getName().getString();
            return bar.contains("구출") ? "ok" : "bar " + bar;
        });
        check("ok".equals(in), "swallowed: untargetable, rescue progress on the boss bar (" + in + ")");
        shot(ctx, "wm_devour_inside");
        // 실명은 삼킨 뒤 9틱 · 29틱 … 에 다시 걸림 → 그 사이에 걷고 찍음 (웜 몸 · 이름표가 보이게)
        waitUntil(ctx, server, t0 + windup + 11);
        server.runCommand("effect clear @a minecraft:blindness");
        ctx.waitTicks(2);
        shot(ctx, "wm_devour_tag");
        server.runOnServer(s -> {
            BossWorm.Devour D = BossWorm.mem(enc()).devour;
            if (D != null) D.dealt = D.need * 0.6;
        });
        waitUntil(ctx, server, t0 + windup + 22);
        shot(ctx, "wm_devour_rescue");
        server.runOnServer(s -> {
            BossWorm.Devour D = BossWorm.mem(enc()).devour;
            if (D != null) D.dealt = D.need;
        });
        ctx.waitTicks(4);
        check(server.computeOnServer(s -> BossWorm.mem(enc()).devour == null && enc().staggerUntil > Tasks.now() && enc().mod.targetable(enc(), player(s))),
                "rescued → spat out, worm gags");
        shot(ctx, "wm_devour_spit");
        ctx.waitTicks(30);
    }

    /** 대지 가르기: 멀리 (굴에서 9칸 넘게) 있는 사람 쪽으로 주황 줄 (1.5초) → 웜이 머리로 내려침 → 금을 따라 바위가 솟아 달려옴 */
    private void rend(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        chase(ctx, server, cx, cz + 14, cx, ALTAR_Y + 2, cz + 3, 8);
        long t0 = server.computeOnServer(s -> BossWorm.rend(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L);
        check(t0 > 0 && server.computeOnServer(s -> enc().cast != null && "rend".equals(enc().cast.id)), "rend cast toward the far player");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("rend").num("windup");
        waitUntil(ctx, server, t0 + 15);
        shot(ctx, "wm_rend_tele");
        waitUntil(ctx, server, t0 + windup + 4);
        shot(ctx, "wm_rend");
        waitUntil(ctx, server, t0 + windup + 8);
        shot(ctx, "wm_rend_hit");
        ctx.waitTicks(25);
    }

    /**
     * 진동 감지: 땅속 (무적) · 굴마다 하늘색 소리 물결 · 큰 글자 · 3 2 1 → 듣는 중 (심장 소리) · 머리 위 주황 원 → 떨어지는 돌 → 아무 굴에 주황 원 → 튀어나옴.
     * 플레이어는 처음 자리에서 움직이지 않음 (옮기면 들켜 즉사 — 사진도 그 자리에서)
     */
    private void tremor(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        chase(ctx, server, cx, cz + 12, cx, ALTAR_Y, cz + 1, 12);
        ctx.waitTicks(5);
        long t0 = server.computeOnServer(s -> {
            BossWorm.tremor(enc(), Tasks.now());
            return Tasks.now();
        });
        int warn = (int) Arena.BOSSES.obj(KEY).obj("tremor").num("warn");
        int listen = (int) Arena.BOSSES.obj(KEY).obj("tremor").num("listen");
        int emerge = (int) Arena.BOSSES.obj(KEY).obj("tremor").num("emerge");
        waitUntil(ctx, server, t0 + 22);
        shot(ctx, "wm_tremor");
        check(server.computeOnServer(s -> enc().immune), "underground (immune) while listening");
        server.runCommand("title @a clear");
        waitUntil(ctx, server, t0 + warn - 18);
        shot(ctx, "wm_tremor_count");
        waitUntil(ctx, server, t0 + warn + 14);
        shot(ctx, "wm_tremor_listen");
        waitUntil(ctx, server, t0 + warn + 10 + 36 - 6);
        shot(ctx, "wm_tremor_rock");
        waitUntil(ctx, server, t0 + warn + listen + 8);
        shot(ctx, "wm_tremor_rise");
        boolean heard = server.computeOnServer(s -> {
            BossWorm.Tremor T = BossWorm.mem(enc()).tremor;
            return T != null && T.heard.contains(player(s).getUUID());
        });
        check(!heard, "standing still is not heard");
        waitUntil(ctx, server, t0 + warn + listen + emerge + 4);
        shot(ctx, "wm_tremor_emerge");
        check(server.computeOnServer(s -> BossWorm.mem(enc()).tremor == null && !enc().immune), "tremor over → emerged");
        ctx.waitTicks(20);
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑 갈색 원 + 바위 뱉기가 포물선으로 → 느림 */
    private void far(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        chase(ctx, server, cx, cz + 14.5, cx, ALTAR_Y + 4, cz, 4);
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            Bosses.farThrow(e, e, Tasks.now(), List.of(player(s)));
        });
        ctx.waitTicks(16);
        shot(ctx, "wm_far");
        ctx.waitTicks(15);
        shot(ctx, "wm_far_hit");
        check(server.computeOnServer(s -> enc().farShots.isEmpty()), "far shot (rock spit) landed");
        ctx.waitTicks(10);
    }

    /** 쓰러뜨림 (진짜 클릭): 처치 글자 · 보상 (보스 재료) · 큰 폭발 → 가방 */
    private void kill(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, Math.PI / 2);
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx, ALTAR_Y, cz + 3.4, cx, ALTAR_Y + 1.9, cz);
        server.runOnServer(s -> enc().boss.setHealth(5F));
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 8);
        ctx.waitTicks(6);
        String r = server.computeOnServer(s -> {
            if (Bosses.encounterAt(altar) != null) return "still fighting";
            if (altar.cooldownUntil <= System.currentTimeMillis()) return "no cooldown";
            int fang = RpgGearTests.countIn(player(s), GearItems.byId("rpg:worm_fang"));
            int heart = RpgGearTests.countIn(player(s), GearItems.byId("rpg:worm_heart"));
            int em = RpgGearTests.countIn(player(s), Items.EMERALD);
            return em >= 16 && fang >= 2 && heart >= 2 ? "ok " + em + " " + fang + " " + heart : "loot " + em + " " + fang + " " + heart;
        });
        check(r.startsWith("ok"), "worm killed → rewards + boss materials + cooldown (" + r + ")");
        shot(ctx, "wm_kill");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks(30);
        ctx.getInput().pressKey(options -> options.keyInventory);
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen, 40);
        } catch (AssertionError | RuntimeException e) {
            fail("inventory did not open");
        }
        ctx.waitTicks(5);
        shot(ctx, "wm_loot");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }

}
