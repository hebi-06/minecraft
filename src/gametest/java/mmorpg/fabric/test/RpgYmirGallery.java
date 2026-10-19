package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossYmir;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.UltFx;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Status;
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
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=ymir): 평평한 세계에 태초의 빙원을 짓고 (제단 + Arena.buildArena) 진짜로 싸움 —
 * 제단을 눌러 도전 창 → 도전 (진짜 클릭) → 소환진 → 얼어붙은 호수 밑에서 솟아오름 → 포효 → 걸음 (stride · rpg:gait),
 * 몽둥이 내려치기 (짙은 파란 직사각형 → 내려침), 발 구르기 (파란 원 → 쿵), 얼음 가시 (파란 줄 → 가시 → 빙결 얼음 덩어리 → 부서짐),
 * 고드름 비 (발밑 원 · 하늘의 서리 문양 → 떨어짐), 원거리 견제 (얼음 창 → 얼음 가시), 균열 1 · 2 · 3 (텍스처) → 무릎 꿇음 (진짜 클릭) → 빈틈 숫자,
 * 니플헤임의 숨결 (뛰어옴 → 들이쉼: 보스 안개 · 모닥불 곁 주황 원 · 불 위 글자 → 내쉼: 모닥불 곁은 안전 → 안개 걷힘),
 * 두 번째 숨결 (얼음 창이 마주 보는 모닥불 둘을 끔), 핌불베트르 (옅은 눈보라 안개), 높은 곳 (첨탑 위 → 경고 → 끌어내림), 쓰러뜨림 (보상 · 가방 · 안개 걷힘).
 * 보스 안개는 서버 (Bosses.hasBossFog) 와 클라이언트 (UltFx 의 하늘빛 짙기 · 거리 — 리소스 팩 fogs 의 3~26 · 10~48) 둘 다 확인.
 * 패턴은 모듈 함수를 바로 부르고 그 사이 보스가 스스로 고르지 않게 멈춤 (BossYmir.M 의 다음 틱을 멀리 + 틀 "pause").
 * 사진 이름 ym_* → ci-logs 의 shots/. 확인 항목은 [ymir-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgYmirGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-ymir-gallery");
    private static final String KEY = "ymir";
    /** 제단 높이 (= 신전 안에서 서는 높이 — 평평한 세계의 땅 -60 에서 메워 올림) */
    private static final int ALTAR_Y = -55;
    private static final int GROUND = -60;
    private static final long FAR_FUTURE = Long.MAX_VALUE / 4;
    private static final double SOUTH = Math.PI / 2;

    private final List<String> failures = new ArrayList<>();
    private Arena.Altar altar;
    private double cx;
    private double cz;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("ymir")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("difficulty normal");
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
            walk(ctx, server);
            slam(ctx, server);
            stomp(ctx, server);
            spikes(ctx, server);
            icicles(ctx, server);
            far(ctx, server);
            crack(ctx, server);
            if (!breath(ctx, server)) return;
            breath2(ctx, server);
            winter(ctx, server);
            high(ctx, server);
            kill(ctx, server);
        } finally {
            BossYmir.forceNext = null;
            LOG.info("[ymir-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError("ymir gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도우미

    private void ok(String what) {
        LOG.info("[ymir-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[ymir-gallery] FAIL {}", what);
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

    private BossYmir.M mem() {
        return BossYmir.mem(enc());
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
        if (got < dist - 0.5) LOG.warn("[ymir-gallery] camera pulled in: {} of {} blocks", String.format(Locale.ROOT, "%.2f", got), dist);
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

    /** 사진 — 창 크기 그대로 (큰 글자 · 창 · 가방이 잘리지 않게), 알림 토스트는 치움 */
    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(name);
    }

    private static void waitChunks(ClientGameTestContext ctx, TestSingleplayerContext world, int timeout) {
        try {
            world.getConnection().waitForChunksRender(true, timeout);
        } catch (AssertionError e) {
            LOG.warn("[ymir-gallery] chunks not all rendered in {} ticks — going on", timeout);
        }
    }

    /** 서버 틱이 at 이 될 때까지 (패턴의 판정 순간을 찍으려고) */
    private static void waitUntil(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 600 && server.computeOnServer(s -> Tasks.now()) < at; i++) ctx.waitTick();
    }

    /** 보스가 스스로 패턴 · 평타 · 원거리 견제 · 큰 패턴을 고르지 않게 (사진첩이 차례로 부름) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossYmir.M m = BossYmir.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.bigUntil = FAR_FUTURE;
            m.breathDone.addAll(e.def.obj("breath").numbersOrNull("at"));
            e.meleeAt = FAR_FUTURE;
            e.farAt = FAR_FUTURE;
        });
    }

    /** 보스 몸을 가운데에서 (dx, dz) 에 yaw 쪽을 보게 세우고 틀 "pause" 로 멈춤 (걷지 않게 — 패턴은 틀을 바꿈) */
    private void settle(TestServerContext server, double dx, double dz, double yaw) {
        freeze(server);
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || BossYmir.mem(e) == null) return;
            Bosses.cast(e, "pause", 6000);
            BossYmir.setBody(e, cx + dx, cz + dz, yaw);
            player(s).setHealth(player(s).getMaxHealth());
            player(s).removeEffect(MobEffects.SLOWNESS);
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

    /** 클라이언트의 하늘빛 (보스 안개): [짙기 0~1, 시작 칸, 끝 칸] — UltFx 의 상태 (FogRendererMixin 이 프레임마다 씀) */
    private static float[] clientFog(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            try {
                Method m = UltFx.class.getDeclaredMethod("skyStrength");
                m.setAccessible(true);
                Field s = UltFx.class.getDeclaredField("skyStartDist");
                s.setAccessible(true);
                Field e = UltFx.class.getDeclaredField("skyEndDist");
                e.setAccessible(true);
                return new float[] {(float) m.invoke(null), s.getFloat(null), e.getFloat(null)};
            } catch (ReflectiveOperationException ex) {
                return new float[] {-1, 0, 0};
            }
        });
    }

    private static String fogText(float[] f) {
        return String.format(Locale.ROOT, "strength %.2f, %.0f~%.0f blocks", f[0], f[1], f[2]);
    }

    /** 보스 엔티티 (클라이언트) 의 몸 방향과 보는 방향의 차이 (도) */
    private static float bodyLag(ClientGameTestContext ctx, int id) {
        return ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof LivingEntity le ? Math.abs(Mth.wrapDegrees(le.yBodyRot - le.getYRot())) : -1F);
    }

    private int bossId(TestServerContext server) {
        return server.computeOnServer(s -> enc() != null && enc().boss != null ? enc().boss.getId() : -1);
    }

    // ------------------------------------------------------------ 신전 짓기

    private boolean build(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world) {
        int R = (int) Arena.BOSSES.obj(KEY).obj("arena").num("radius");
        int x = 0;
        int z = 0;
        cx = x + 0.5;
        cz = z + 0.5;
        server.runCommand("gamemode spectator @a");
        look(ctx, server, cx, ALTAR_Y + 36, cz + 0.01, cx, ALTAR_Y, cz);
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
                ok("glacier temple built");
                waitChunks(ctx, world, 300);
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /** 싸울 플레이어: 서바이벌 · 저항 III (사진을 찍는 동안 쓰러지지 않게 — 숨결은 모닥불 곁에서) · 레벨 · 스탯 · 칼 */
    private void prepare(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 2 true");
        server.runCommand("xp set @a 40 levels");
        ctx.waitTicks(5);
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l10_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
    }

    // ------------------------------------------------------------ 장면

    /** 제단 누르기 (진짜 오른쪽 클릭) → 도전 창 → 도전 (진짜 클릭) → 소환진 → 호수 밑에서 솟아오름 → 포효 */
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
        check(body.contains("Lv.40") && body.contains("얼음 창") && body.contains("20분") && body.contains("이미르의 서리 심장") && body.contains("태초의 거인 뼈"),
                "challenge text (level · ice lance · cooldown · boss materials)");
        shot(ctx, "ym_form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state));
        check(summoning, "도전 → summoning");
        if (!summoning) return false;
        long spawn = server.computeOnServer(s -> enc().spawnAt);
        // 소환진 (3초 동안 차오름) — 남쪽 16칸에서 호수를 봄 (깨운 사람 쪽을 보고 솟아오름)
        look(ctx, server, cx, ALTAR_Y, cz + 16, cx, ALTAR_Y + 3.5, cz);
        waitUntil(ctx, server, spawn - 20);
        shot(ctx, "ym_summon");
        waitUntil(ctx, server, spawn + 2);
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return "no boss";
            BossYmir.M m = BossYmir.mem(e);
            if (m == null) return "no module memory";
            if (e.boss.getHealth() != 38000F || e.boss.getMaxHealth() != 38000F) return "hp " + e.boss.getHealth();
            if (!e.immune || e.cast == null || !"arrive".equals(e.cast.id)) return "not arriving";
            return m.body.h < -8 ? "ok" : "h " + m.body.h;
        });
        check("ok".equals(r), "Ymir appears under the frozen lake with 38000 hp, untouchable while it rises (" + r + ")");
        // 솟아오르는 중 (반쯤)
        waitUntil(ctx, server, spawn + 28);
        shot(ctx, "ym_rise");
        int id = bossId(server);
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        check("ok".equals(rend), "Ymir drawn by BedrockRenderer (" + rend + ")");
        // 꼭대기: 서리 충격파 + 포효 (두 팔을 벌린 채 유지)
        int rise = (int) Arena.BOSSES.obj(KEY).obj("arrive").num("rise");
        int roar = (int) Arena.BOSSES.obj(KEY).obj("arrive").num("roar");
        waitUntil(ctx, server, spawn + rise + 6);
        shot(ctx, "ym_roar");
        boolean fires = ctx.computeOnClient(mc -> {
            for (Vec3 f : Arena.arenaFeatures(altar).fires()) {
                BlockState s = mc.level.getBlockState(BlockPos.containing(f.x, f.y + 1, f.z));
                if (!s.is(Blocks.CAMPFIRE) || !s.getValue(CampfireBlock.LIT)) return false;
            }
            return true;
        });
        check(fires, "the four Muspelheim fires are lit (client blocks)");
        waitUntil(ctx, server, spawn + rise + roar + 4);
        String after = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            BossYmir.M m = BossYmir.mem(e);
            return !e.immune && m.body.h == 0 && Math.abs(e.boss.getY() - ALTAR_Y) < 1e-6 ? "ok" : "immune " + e.immune + " h " + m.body.h;
        });
        check("ok".equals(after), "risen: standing on the ice, the fight is on (" + after + ")");
        freeze(server);
        return true;
    }

    /** 걸음: 가장 가까운 사람 쪽으로 걸어옴 (stride — rpg:gait 1, 쿵 소리 · 눈 먼지) */
    private void walk(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("title @a clear");
        settle(server, 0, 0, SOUTH);
        chase(ctx, server, cx + 5, cz + 15, cx, ALTAR_Y + 4, cz + 2, 6);
        server.runOnServer(s -> enc().cast = null); // 틀을 풀면 가장 가까운 사람 쪽으로 걸음
        ctx.waitTicks(14);
        int id = bossId(server);
        double gait = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof BedrockMob m ? m.prop("rpg:gait") : -1.0);
        check(gait > 0.9, "walking: rpg:gait " + gait + " on the client (stride)");
        shot(ctx, "ym_walk");
        double moved = server.computeOnServer(s -> BossKit.hdist(BossYmir.bodyOf(enc()), new Vec3(cx, ALTAR_Y, cz)));
        check(moved > 1.0, "it walks toward the nearest player (" + String.format(Locale.ROOT, "%.2f", moved) + " blocks)");
    }

    /** 몽둥이 내려치기: 대상이 몽둥이 줄 위에 오게 몸을 돌림 · 오른 어깨에서 짙은 파란 직사각형 (0.9초) → 내려침 (얼음 깨짐 · 금) */
    private void slam(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        double px = cx + 2;
        double pz = cz + 7.5;
        chase(ctx, server, px, pz, cx - 0.5, ALTAR_Y, cz + 3, 11);
        long t0 = server.computeOnServer(s -> BossYmir.slam(enc(), Tasks.now(), BossKit.fighters(enc())) ? Tasks.now() : -1L);
        check(t0 > 0, "club slam on the player in front");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("melee").num("windup");
        waitUntil(ctx, server, t0 + 11);
        shot(ctx, "ym_slam_tele");
        float lag = bodyLag(ctx, bossId(server));
        check(lag >= 0 && lag < 6, "its body (not just its head) has turned to bring the club over the target — client body lag " + lag + "°");
        float hp = server.computeOnServer(s -> player(s).getHealth());
        waitUntil(ctx, server, t0 + windup + 1);
        shot(ctx, "ym_slam");
        boolean hit = server.computeOnServer(s -> player(s).getHealth() < hp && player(s).hasEffect(MobEffects.SLOWNESS));
        check(hit, "slammed: damage + slowness");
        ctx.waitTicks(10);
    }

    /** 발 구르기: 옆에 붙은 사람 → 몸 둘레 파란 원 (반지름 6, 1.3초) → 쿵 (충격파 · 띄움) */
    private void stomp(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        // 동쪽 옆 4칸 (몸 앞 100° 밖) — 뒤 위에서 몸 옆과 원 전체를 봄
        chase(ctx, server, cx + 4, cz - 1, cx - 1, ALTAR_Y, cz, 11);
        long t0 = server.computeOnServer(s -> BossYmir.stomp(enc(), Tasks.now(), BossKit.fighters(enc())) ? Tasks.now() : -1L);
        check(t0 > 0, "stomp at someone by its side");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("stomp").num("windup");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "ym_stomp_tele");
        float hp = server.computeOnServer(s -> player(s).getHealth());
        waitUntil(ctx, server, t0 + windup + 2);
        shot(ctx, "ym_stomp");
        check(server.computeOnServer(s -> player(s).getHealth() < hp), "stomped (inside the circle)");
        ctx.waitTicks(20);
    }

    /** 얼음 가시: 몸에서 파란 줄 (폭 1.8칸, 필드 끝까지) → 가시가 차례로 솟음 → 빙결 (얼음 덩어리) → 풀리며 부서짐 */
    private void spikes(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        double px = cx + 4;
        double pz = cz + 12;
        chase(ctx, server, px, pz, cx + 1.5, ALTAR_Y, cz + 4, 9);
        long t0 = server.computeOnServer(s -> BossYmir.spikes(enc(), Tasks.now(), BossKit.fighters(enc())) ? Tasks.now() : -1L);
        check(t0 > 0, "ice spikes at the farthest player");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("spikes").num("windup");
        waitUntil(ctx, server, t0 + 14);
        shot(ctx, "ym_spikes_tele");
        waitUntil(ctx, server, t0 + windup + 6);
        shot(ctx, "ym_spikes");
        // 맞음 → 빙결: 얼음 덩어리 (가까이에서)
        for (int i = 0; i < 40 && !server.computeOnServer(s -> !BossYmir.iceOf(enc()).isEmpty()); i++) ctx.waitTick();
        String ice = server.computeOnServer(s -> {
            List<BossYmir.Ice> list = BossYmir.iceOf(enc());
            if (list.isEmpty()) return "no ice";
            if (!Status.isRooted(player(s))) return "not rooted";
            return list.get(0).pid.equals(player(s).getUUID()) && Kit.isType(list.get(0).e, BossYmir.ICE_ID) ? "ok" : "ice on someone else";
        });
        check("ok".equals(ice), "frozen in a block of ice (rooted, rpg:prop_ymir_ice) — " + ice);
        camDist(ctx, server, 4.5);
        ctx.waitTicks(4);
        shot(ctx, "ym_frozen");
        int iceId = server.computeOnServer(s -> BossYmir.iceOf(enc()).isEmpty() ? -1 : BossYmir.iceOf(enc()).get(0).e.getId());
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(iceId);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        check("ok".equals(rend), "the ice block is drawn (" + rend + ")");
        long breakAt = server.computeOnServer(s -> BossYmir.iceOf(enc()).isEmpty() ? 0L : BossYmir.iceOf(enc()).get(0).breakAt);
        waitUntil(ctx, server, breakAt + 3);
        shot(ctx, "ym_ice_shatter");
        check(server.computeOnServer(s -> !Status.isRooted(player(s)) && (BossYmir.iceOf(enc()).isEmpty() || BossYmir.iceOf(enc()).get(0).broken)),
                "thawed: the ice shatters");
        ctx.waitTicks(16);
        check(server.computeOnServer(s -> BossYmir.iceOf(enc()).isEmpty()), "…and is gone");
    }

    /** 고드름 비: 두 팔을 하늘로 · 발밑 + 곳곳 셋 파란 원 · 하늘에 서리 문양 (1.4초) → 고드름이 떨어짐 */
    private void icicles(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        double px = cx + 1;
        double pz = cz + 9;
        chase(ctx, server, px, pz, cx, ALTAR_Y + 2.5, cz + 2, 10);
        // 무작위 자리 셋: 플레이어 둘레 화면 안 (각 · 거리 수열 — randomSpot: 각 = v × 360°, 거리 = √v × 21)
        long t0 = server.computeOnServer(s -> {
            boolean[] r = {false};
            withRand(new double[] {0.17, 0.27, 0.21, 0.12, 0.31, 0.22, 0.5}, () -> r[0] = BossYmir.icicles(enc(), Tasks.now(), BossKit.fighters(enc())));
            return r[0] ? Tasks.now() : -1L;
        });
        check(t0 > 0, "icicle rain");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("icicles").num("windup");
        waitUntil(ctx, server, t0 + 14);
        shot(ctx, "ym_icicles_tele");
        waitUntil(ctx, server, t0 + windup - 5);
        shot(ctx, "ym_icicles_fall");
        float hp = server.computeOnServer(s -> player(s).getHealth());
        waitUntil(ctx, server, t0 + windup + 1);
        shot(ctx, "ym_icicles");
        check(server.computeOnServer(s -> player(s).getHealth() < hp && player(s).hasEffect(MobEffects.SLOWNESS)), "hit under the circle: damage + slowness");
        ctx.waitTicks(10);
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑 짙은 파란 원 + 왼손에서 얼음 창 → 떨어진 자리에 얼음 가시 (느림 II) */
    private void far(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        chase(ctx, server, cx - 4, cz + 16, cx - 1, ALTAR_Y + 4, cz + 3, 7);
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            List<ServerPlayer> far = Bosses.farTargets(e, BossKit.fighters(e), p -> true);
            if (far.isEmpty()) return -1L;
            Bosses.farThrow(e, e, Tasks.now(), far);
            return Tasks.now();
        });
        check(t0 > 0, "the ice lance is thrown at the far player");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("far").num("windup");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "ym_far");
        waitUntil(ctx, server, t0 + windup + 2);
        shot(ctx, "ym_far_hit");
        String r = server.computeOnServer(s -> {
            var sl = player(s).getEffect(MobEffects.SLOWNESS);
            return enc().farShots.isEmpty() && sl != null && sl.getAmplifier() == 1 ? "ok" : "slowness " + sl;
        });
        check("ok".equals(r), "the lance lands: slowness II (" + r + ")");
        ctx.waitTicks(10);
    }

    /** 균열 1 · 2 (가까이에서 때린 피해만큼 — 얼음 다리의 금 텍스처) → 다 차면 무릎 꿇음 (진짜 클릭) → 빈틈 (+50%) 숫자 */
    private void crack(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        double need = Arena.BOSSES.obj(KEY).obj("crack").num("need");
        int id = bossId(server);
        // 다리 가까이 (몸 겉면 2.7칸) 에서 뒤 3인칭 — 다리 · 몸통이 화면 가운데
        double pz = cz + 4.5;
        chase(ctx, server, cx, pz, cx, ALTAR_Y + 3.0, cz, 5);
        for (int stage = 1; stage <= 2; stage++) {
            int st = stage;
            server.runOnServer(s -> BossYmir.addCrack(enc(), need / 3 + 1, true, Tasks.now()));
            ctx.waitTicks(4);
            int got = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof BedrockMob m ? m.propInt("rpg:crack") : -1);
            check(got == st, "crack " + st + "/3: rpg:crack " + got + " on the client");
            shot(ctx, "ym_crack" + st);
        }
        // 금을 거의 채우고 진짜 클릭 (근접) → 무릎
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx, ALTAR_Y, pz, cx, ALTAR_Y + 1.9, cz);
        server.runOnServer(s -> BossYmir.crackOf(enc()).g = need - 1);
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        boolean knelt = server.computeOnServer(s -> enc().cast != null && "kneel".equals(enc().cast.id));
        if (!knelt) {
            fail("the real hit did not fill the cracks — kneeling from the server");
            server.runOnServer(s -> BossYmir.kneel(enc(), Tasks.now()));
        } else ok("a close hit fills the cracks: Ymir kneels (cast kneel)");
        ctx.waitTicks(3);
        int got = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof BedrockMob m ? m.propInt("rpg:crack") : -1);
        check(got == 3, "kneeling: rpg:crack 3 (" + got + ")");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 7);
        ctx.waitTicks(8);
        shot(ctx, "ym_kneel");
        // 무릎 꿇은 동안 진짜 클릭: 금색 "빈틈" + 근접 보너스 » 숫자
        camera(ctx, CameraType.FIRST_PERSON);
        server.runCommand("title @a clear");
        ctx.waitTicks(22);
        float hp0 = server.computeOnServer(s -> enc().boss.getHealth());
        attack(ctx);
        ctx.waitTicks(2);
        String num = server.computeOnServer(s -> {
            BedrockMob b = enc().boss;
            for (Display.TextDisplay td : level(s).getEntitiesOfClass(Display.TextDisplay.class, new AABB(b.position(), b.position()).inflate(7),
                    t -> t.entityTags().contains(DamageNumbers.TAG))) {
                String t = td.getText().getString();
                if (t.contains("빈틈")) return t;
            }
            return null;
        });
        float hp1 = server.computeOnServer(s -> enc().boss.getHealth());
        check(hp1 < hp0, "a hit while it kneels lands (" + hp0 + " → " + hp1 + ")");
        check(num != null, "the number reads 빈틈 (" + num + ")");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 4.5);
        ctx.waitTicks(1);
        shot(ctx, "ym_open");
        // 일어날 때까지
        long until = server.computeOnServer(s -> mem().kneelUntil);
        waitUntil(ctx, server, until + 14);
        check(server.computeOnServer(s -> BossYmir.crackOf(enc()).stage == 0 && enc().boss.propInt("rpg:crack") == 0), "it rises, legs whole again");
    }

    /**
     * 니플헤임의 숨결 (65%): 가운데로 뛰어옴 → 들이쉼 (짙은 눈보라 안개 — 서버 · 클라이언트 · 모닥불 곁 주황 원 · 불 위 글자 · 눈보라) → 3 · 2 · 1 →
     * 내쉼 (모닥불 곁은 안전 — "불씨가 지켜 주었습니다") → 안개 걷힘. 플레이어는 남동 모닥불 곁
     */
    private boolean breath(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 4, -4, SOUTH);
        Vec3 f0 = server.computeOnServer(s -> BossYmir.firesOf(enc()).get(0).at);
        Vec3 in = new Vec3(cx - f0.x, 0, cz - f0.z).normalize();
        double px = f0.x + in.x * 2.3;
        double pz = f0.z + in.z * 2.3;
        chase(ctx, server, px, pz, cx, ALTAR_Y + 0.5, cz, 7);
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            e.boss.setHealth(e.boss.getMaxHealth() * 0.64F);
            BossYmir.breath(e, Tasks.now(), BossKit.fighters(e), false);
            return Tasks.now();
        });
        check(server.computeOnServer(s -> BossYmir.breathOf(enc()) != null && enc().immune), "Niflheim's breath: it leaps to the middle (untouchable)");
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "ym_breath_leap");
        int leap = (int) Arena.BOSSES.obj(KEY).obj("breath").num("leap");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("breath").num("windup");
        long inhale = t0 + leap + 6;
        waitUntil(ctx, server, inhale + 30);
        server.runCommand("title @a clear");
        String srv = server.computeOnServer(s -> {
            BossYmir.Breath G = BossYmir.breathOf(enc());
            if (G == null || !"inhale".equals(G.stage)) return "not inhaling";
            BossKit.Fog f = mem().fog == null ? null : mem().fog.get(player(s).getUUID());
            if (f == null || !BossYmir.FOG_BREATH.equals(f.id()) || !Bosses.hasBossFog(player(s))) return "no fog on the server " + f;
            String lab = null;
            for (BossYmir.Label L : G.labels) if (L.f == BossYmir.firesOf(enc()).get(0)) lab = BossYmir.labelText(L);
            return "§6불씨 §f1§7/§f1".equals(lab) ? "ok" : "label " + lab;
        });
        check("ok".equals(srv), "inhaling: blizzard fog for the player (server), the fire's label 불씨 1/1 (" + srv + ")");
        float[] fog = clientFog(ctx);
        check(fog[0] > 0.9 && Math.abs(fog[1] - 3) < 1e-3 && Math.abs(fog[2] - 26) < 1e-3, "blizzard fog on the client: " + fogText(fog));
        shot(ctx, "ym_breath_fog");
        // 모닥불 곁 위에서 (주황 원 · 글자 · 들이쉬는 이미르)
        camDist(ctx, server, 4);
        look(ctx, server, px, ALTAR_Y, pz, f0.x, ALTAR_Y + 0.5, f0.z);
        ctx.waitTicks(4);
        shot(ctx, "ym_breath_fire");
        chase(ctx, server, px, pz, cx, ALTAR_Y + 0.5, cz, 7);
        long until = server.computeOnServer(s -> BossYmir.breathOf(enc()) == null ? 0L : BossYmir.breathOf(enc()).until);
        waitUntil(ctx, server, until - 39);
        shot(ctx, "ym_breath_count");
        waitUntil(ctx, server, until + 2);
        boolean alive = server.computeOnServer(s -> player(s).isAlive() && !player(s).isDeadOrDying());
        check(alive, "exhale: by a lit fire — safe");
        shot(ctx, "ym_breath_exhale");
        if (!alive) return false;
        ctx.waitTicks(24);
        float[] gone = clientFog(ctx);
        check(gone[0] < 0.01 && server.computeOnServer(s -> !Bosses.hasBossFog(player(s))), "the blizzard lifts (" + fogText(gone) + ")");
        shot(ctx, "ym_breath_after");
        return true;
    }

    /** 두 번째 숨결 (30%): 왼손 얼음 창이 마주 보는 모닥불 둘을 꺼 버림 ("꺼진 불" · 흐린 고리) → 남은 불 곁은 안전 */
    private void breath2(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        // 남서 모닥불 (1) 곁 — 쌍 0 (남동 · 북서) 이 꺼짐 (rand 0.1)
        Vec3 f1 = server.computeOnServer(s -> BossYmir.firesOf(enc()).get(1).at);
        Vec3 in = new Vec3(cx - f1.x, 0, cz - f1.z).normalize();
        double px = f1.x + in.x * 2.3;
        double pz = f1.z + in.z * 2.3;
        // 남쪽을 보며 (남동 모닥불이 오른쪽 앞) — 꺼지는 불이 화면에
        Vec3 f0 = server.computeOnServer(s -> BossYmir.firesOf(enc()).get(0).at);
        chase(ctx, server, px, pz, (f0.x + cx) / 2, ALTAR_Y + 2, (f0.z + cz) / 2 + 2, 7);
        // 쌍 고르기 (들이쉴 때 random() < 0.5 → 쌍 0): 뛰기부터 들이쉴 때까지 무작위를 0.1 로 (연출의 돌림만 바뀜)
        DoubleSupplier[] prev = new DoubleSupplier[1];
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            prev[0] = BossKit.rand;
            BossKit.rand = () -> 0.1;
            e.boss.setHealth(e.boss.getMaxHealth() * 0.29F);
            BossYmir.breath(e, Tasks.now(), BossKit.fighters(e), true);
            return Tasks.now();
        });
        int leap = (int) Arena.BOSSES.obj(KEY).obj("breath").num("leap");
        long inhale = t0 + leap + 6;
        waitUntil(ctx, server, inhale + 1);
        String pair = server.computeOnServer(s -> {
            BossKit.rand = prev[0];
            BossYmir.Breath G = BossYmir.breathOf(enc());
            return G == null ? "none" : G.snuffed.toString();
        });
        check("[0, 2]".equals(pair), "the lances fly at the south-east and north-west fires (" + pair + ")");
        ctx.waitTicks(3);
        shot(ctx, "ym_breath2_lance");
        waitUntil(ctx, server, inhale + 22);
        String r = server.computeOnServer(s -> {
            List<BossYmir.Fire> fs = BossYmir.firesOf(enc());
            boolean outs = !fs.get(0).lit && !fs.get(2).lit && fs.get(1).lit && fs.get(3).lit;
            boolean blocks = !BossYmir.fireBlockLit(enc(), fs.get(0)) && !BossYmir.fireBlockLit(enc(), fs.get(2));
            String lab = null;
            for (BossYmir.Label L : BossYmir.breathOf(enc()).labels) if (L.f == fs.get(0)) lab = BossYmir.labelText(L);
            return outs && blocks && "§8꺼진 불".equals(lab) ? "ok" : "fires " + fs.get(0).lit + fs.get(1).lit + fs.get(2).lit + fs.get(3).lit + " label " + lab;
        });
        check("ok".equals(r), "second breath: two facing fires are put out by ice lances (꺼진 불) — " + r);
        server.runCommand("title @a clear");
        shot(ctx, "ym_breath2_out");
        long until = server.computeOnServer(s -> BossYmir.breathOf(enc()) == null ? 0L : BossYmir.breathOf(enc()).until);
        waitUntil(ctx, server, until + 2);
        check(server.computeOnServer(s -> player(s).isAlive() && !player(s).isDeadOrDying()), "by the fire that still burns: safe");
        ctx.waitTicks(10);
    }

    /** 핌불베트르 (20%): 두 팔을 벌리고 포효 → 끝날 때까지 옅은 눈보라 안개 (클라이언트 10~48칸) · 눈보라 */
    private void winter(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        chase(ctx, server, cx + 1, cz + 13, cx, ALTAR_Y + 5, cz, 6);
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            e.boss.setHealth(e.boss.getMaxHealth() * 0.19F);
            BossYmir.startWinter(e, Tasks.now());
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "ym_winter");
        waitUntil(ctx, server, t0 + 40);
        server.runCommand("title @a clear");
        float[] fog = clientFog(ctx);
        check(server.computeOnServer(s -> mem().winter && Math.abs(enc().cdMult - 0.8) < 1e-9 && Bosses.hasBossFog(player(s))), "Fimbulvetr: winter, patterns x0.8, fog (server)");
        check(fog[0] > 0.9 && Math.abs(fog[1] - 10) < 1e-3 && Math.abs(fog[2] - 48) < 1e-3, "light blizzard fog on the client: " + fogText(fog));
        shot(ctx, "ym_winter_fog");
    }

    /** 높은 곳: 얼음 첨탑 꼭대기 → "내려오세요!" → 1.5초 넘게 머물면 몽둥이로 쳐서 가운데 쪽으로 떨어뜨림 */
    private void high(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        Vec3 top = server.computeOnServer(s -> {
            var sp = enc().feat.spires().get(1);
            return new Vec3(sp.at().x, sp.at().y + sp.h(), sp.at().z);
        });
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 5);
        look(ctx, server, top.x, top.y, top.z, cx, ALTAR_Y + 2, cz);
        ctx.waitTicks(12);
        shot(ctx, "ym_high_warn");
        float hp = server.computeOnServer(s -> player(s).getHealth());
        int grace = (int) Arena.BOSSES.obj(KEY).obj("high").num("grace");
        boolean hit = false;
        for (int i = 0; i < grace + 30 && !hit; i++) {
            ctx.waitTick();
            hit = server.computeOnServer(s -> player(s).getHealth() < hp);
        }
        check(hit, "staying on a spire: clubbed down");
        ctx.waitTicks(2);
        shot(ctx, "ym_high_hit");
        ctx.waitTicks(30);
    }

    /** 쓰러뜨림 (진짜 클릭): 처치 글자 · 보상 채팅 · 안개 걷힘 · 꺼진 불도 켜짐 → 가방 (서리 심장 · 거인 뼈) */
    private void kill(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 0, 0, SOUTH);
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx, ALTAR_Y, cz + 4.5, cx, ALTAR_Y + 1.9, cz);
        server.runOnServer(s -> enc().boss.setHealth(5F));
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 7);
        ctx.waitTicks(6);
        String r = server.computeOnServer(s -> {
            if (Bosses.encounterAt(altar) != null) return "still fighting";
            if (altar.cooldownUntil <= System.currentTimeMillis() + 1_190_000) return "no 20 min cooldown";
            int heart = RpgGearTests.countIn(player(s), GearItems.byId("rpg:ymir_heart"));
            int bone = RpgGearTests.countIn(player(s), GearItems.byId("rpg:ymir_bone"));
            int em = RpgGearTests.silver(player(s));
            return heart >= 2 && bone >= 2 && em >= 34 ? "ok " + em + " emeralds, " + heart + " hearts, " + bone + " bones" : "loot " + em + "/" + heart + "/" + bone;
        });
        check(r.startsWith("ok"), "Ymir killed → rewards + cooldown (" + r + ")");
        shot(ctx, "ym_kill");
        ctx.waitTicks(16);
        float[] fog = clientFog(ctx);
        check(fog[0] < 0.01, "the winter fog lifts after the fight (" + fogText(fog) + ")");
        boolean fires = ctx.computeOnClient(mc -> {
            for (Vec3 f : Arena.arenaFeatures(altar).fires()) {
                BlockState s = mc.level.getBlockState(BlockPos.containing(f.x, f.y + 1, f.z));
                if (!s.is(Blocks.CAMPFIRE) || !s.getValue(CampfireBlock.LIT)) return false;
            }
            return true;
        });
        check(fires, "every Muspelheim fire burns again (client)");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks(14);
        ctx.getInput().pressKey(options -> options.keyInventory);
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen, 40);
        } catch (AssertionError | RuntimeException e) {
            fail("inventory did not open");
        }
        ctx.waitTicks(5);
        shot(ctx, "ym_loot");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }
}
