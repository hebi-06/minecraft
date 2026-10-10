package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaBuild;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossSurtr;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
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
 * 5단계 사진첩 (화면 시험, only=surtr): 평평한 세계에 무스펠헤임의 신전 (반지름 44 — Arena.buildArena 그대로) 을 짓고 실제로 싸움 —
 * 제단을 눌러 도전 창 ([초월 보스] · 패턴을 깨는 법) → 도전 (진짜 클릭) → 화구 밑에서 솟아오름 → 포효 (큰 글자 · 보스 막대),
 * 레바테인 내려찍기 (붉은 줄 예고 → 박힌 칼 · 불길 · 레바테인의 핵) → 핵을 부숨 (부위 파괴 큰 글자 · 식어 금 간 칼 텍스처),
 * 횡베기 (붉은 띠 → 금빛 띠) → 스킬로 반격 (반격 큰 글자 · 노란 "반격" 숫자), 불의 주먹 (붉은 원 → 내려침 · 건틀릿의 핵 → 부숨),
 * 봉인의 사슬 기둥 (기둥 앞에서 끊긴 줄 · 사슬빛 고리 → 사슬이 칼을 붙잡음 · 하늘색 "속박" → 기둥이 무너짐),
 * 무스펠의 업화 (달아오름 rpg:heat · 주황 안개 · 업화 막대 → 피해로 깎임 → 쓰러짐 · 금색 "빈틈" · 불꽃 왕관의 핵 → 부숨 = 머리 불꽃이 꺼짐),
 * 라그나로크 (뛰어감 → 붉은 안개 · 얼음 결정 뒤 파란 그림자 → 불길 · 결정이 막아 줌 → 결정이 녹음), 쓰러뜨림 (딜 순위 · 부위 파괴 채팅).
 * 패턴은 플레이어 (서바이벌 · 저항 V) 를 대상으로 모듈 함수를 바로 부르고, 넓은 사진은 관전 모드로 (그동안 싸움이 초기화되지 않게 emptySince 를 멀리).
 * 사진 이름 su_* → ci-logs 의 shots/. 확인 항목은 [surtr-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgSurtrGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-surtr-gallery");
    private static final String KEY = "surtr";
    /** 제단 높이 (= 신전 안에서 서는 높이 — 평평한 세계의 땅 -60 에서 높이 규칙 때문에 메워 올림) */
    private static final int ALTAR_Y = -55;
    private static final int GROUND = -60;
    private static final long FAR = Long.MAX_VALUE / 4;

    private final List<String> failures = new ArrayList<>();
    private Arena.Altar altar;
    private Bosses.Enc fight;
    private double cx;
    private double cz;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("surtr")) return;
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
            slam(ctx, server);
            sweep(ctx, server);
            fist(ctx, server);
            bind(ctx, server);
            inferno(ctx, server);
            ragnarok(ctx, server);
            kill(ctx, server);
        } finally {
            LOG.info("[surtr-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError("surtr gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도우미

    private void ok(String what) {
        LOG.info("[surtr-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[surtr-gallery] FAIL {}", what);
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

    private BossSurtr.M mem() {
        return BossSurtr.mem(enc());
    }

    private static long now(TestServerContext server) {
        return server.computeOnServer(s -> Tasks.now());
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

    /** 관전 모드 카메라 (넓은 사진 — 그동안 싸움이 초기화되지 않게 emptySince 를 멀리) */
    private void spec(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            if (e != null) e.emptySince = FAR;
        });
        server.runCommand("gamemode spectator @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, px, py, pz, tx, ty, tz);
    }

    /** 서바이벌로 바닥 (px, pz) 에 서서 (tx, ty, tz) 를 봄 (1인칭) */
    private void stand(ClientGameTestContext ctx, TestServerContext server, double px, double pz, double tx, double ty, double tz) {
        server.runCommand("gamemode survival @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, px, ALTAR_Y, pz, tx, ty, tz);
    }

    /** 서바이벌로 바닥에 서서 뒤 3인칭 (카메라는 시선 뒤로 dist 칸) */
    private void chase(ClientGameTestContext ctx, TestServerContext server, double px, double pz, double tx, double ty, double tz, double dist) {
        server.runCommand("gamemode survival @a");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, dist);
        look(ctx, server, px, ALTAR_Y, pz, tx, ty, tz);
        ctx.waitTick();
        double got = ctx.computeOnClient(mc -> mc.player == null ? -1.0 : mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
        if (got < dist - 0.5) LOG.warn("[surtr-gallery] camera pulled in: {} of {} blocks", String.format(Locale.ROOT, "%.2f", got), dist);
    }

    /** 패턴의 무작위 (BossKit.rand) 를 v 로 정해 run 을 부르고 되돌림 (서버 스레드에서) */
    private static void withRand(double v, Runnable run) {
        DoubleSupplier prev = BossKit.rand;
        BossKit.rand = () -> v;
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
            LOG.warn("[surtr-gallery] chunks not all rendered in {} ticks — going on", timeout);
        }
    }

    /** 서버 틱이 at 이 될 때까지 (패턴의 예고 · 판정 순간을 찍으려고) */
    private static void waitUntil(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 600 && now(server) < at; i++) ctx.waitTick();
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

    /** 보스가 스스로 패턴 · 원거리 견제 · 큰 패턴을 고르지 않게 */
    private void calm(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossSurtr.M m = BossSurtr.mem(e);
            if (e == null || m == null) return;
            m.next.replaceAll((k, v) -> FAR);
            m.bigUntil = FAR;
            e.farAt = FAR;
        });
    }

    /** 보스를 가운데에 yaw (도, +x 에서 +z 쪽으로 — 90 = 남쪽) 를 보게 세우고 멈춤 (틀 pause), 드러난 핵은 거둠 */
    private void settle(TestServerContext server, double yawDeg) {
        calm(server);
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || BossSurtr.mem(e) == null) return;
            BossSurtr.cover(e);
            BossSurtr.setBody(e, cx, cz, Math.toRadians(yawDeg));
            e.staggerUntil = 0;
            Bosses.cast(e, "pause", 1_000_000);
        });
    }

    private int bossId(TestServerContext server) {
        return server.computeOnServer(s -> enc() != null && enc().boss != null ? enc().boss.getId() : -1);
    }

    /** 클라이언트가 받은 보스 속성 (rpg:heat · rpg:sword_broken …) */
    private double clientProp(ClientGameTestContext ctx, int id, String prop) {
        return ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof BedrockMob m ? m.prop(prop) : -1.0);
    }

    /** 서버: 플레이어가 e 를 amount 로 때림 (평타 피해 — 엔진의 무적 시간 · 보너스를 거침) */
    private static boolean serverHit(MinecraftServer s, Entity e, float amount) {
        return e.hurtServer((ServerLevel) e.level(), player(s).damageSources().playerAttack(player(s)), amount);
    }

    /** 보스 둘레에 떠 있는 피해 숫자 가운데 mark 가 든 것 */
    private static String number(MinecraftServer s, Entity boss, String mark) {
        for (Display.TextDisplay td : level(s).getEntitiesOfClass(Display.TextDisplay.class, boss.getBoundingBox().inflate(8), t -> t.entityTags().contains(DamageNumbers.TAG))) {
            String t = td.getText().getString();
            if (t.contains(mark)) return t;
        }
        return null;
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
        waitChunks(ctx, world, 300);
        String started = server.computeOnServer(s -> {
            ServerLevel level = level(s);
            BlockPos pos = new BlockPos(x, ALTAR_Y, z);
            Ticking.holdArea(level, Vec3.atCenterOf(pos), Math.min(R + 12, 60), "g");
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
                int errors = server.computeOnServer(s -> ArenaBuild.lastErrors(altar));
                check(errors == 0, "muspelheim temple built (failed batches " + errors + ")");
                waitChunks(ctx, world, 200);
                ctx.waitTicks(10);
                shot(ctx, "su_temple");
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /** 싸울 플레이어: 서바이벌 · 저항 V (수르트의 한 방을 맞아도 쓰러지지 않게 — 라그나로크는 결정 그림자 안에서) · 레벨 · 스탯 · 칼 */
    private void prepare(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 4 true");
        server.runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
        server.runCommand("xp set @a 50 levels");
        ctx.waitTicks(5);
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l10_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
    }

    // ------------------------------------------------------------ 장면

    /** 제단 누르기 (진짜 오른쪽 클릭) → 도전 창 ([초월 보스] · 패턴을 깨는 법) → 도전 (진짜 클릭) → 화구 밑에서 솟아오름 → 포효 */
    private boolean challenge(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        stand(ctx, server, cx, cz + 3, cx, ALTAR_Y + 0.5, cz);
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
        check(body.contains("[초월 보스]") && body.contains("패턴을 깨는 법") && body.contains("고급 룬 뽑기권") && body.contains("초월 장비 재료") && body.contains("30분"),
                "challenge text ([초월 보스] · notes · advanced ticket · transcend materials · 30 min)");
        shot(ctx, "su_form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state));
        check(summoning, "도전 → summoning");
        if (!summoning) return false;
        fight = server.computeOnServer(s -> enc());
        // 소환진 (3초) 동안 남쪽으로 물러남, 나오면 바로 패턴을 멈춤 (등장 틀은 그대로)
        stand(ctx, server, cx, cz + 30, cx, ALTAR_Y + 8, cz);
        for (int t = 0; t < 80 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        calm(server);
        long spawn = now(server);
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return "no boss";
            if (e.boss.getHealth() != 90000F || e.boss.getMaxHealth() != 90000F) return "hp " + e.boss.getHealth();
            return e.immune && e.cast != null && "arrive".equals(e.cast.id) ? "ok" : "not rising";
        });
        check("ok".equals(r), "surtr spawned with 90000 hp, rising (immune) (" + r + ")");
        // 솟아오르는 중: 남동쪽 위에서 화구를 (관전 — 등장 동안은 아무도 노리지 않음)
        spec(ctx, server, cx + 30, ALTAR_Y + 14, cz + 36, cx, ALTAR_Y + 9, cz);
        waitUntil(ctx, server, spawn + 45);
        int id = bossId(server);
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        check("ok".equals(rend), "surtr drawn by BedrockRenderer (" + rend + ")");
        double under = server.computeOnServer(s -> enc().boss.getY() - ALTAR_Y);
        check(under < -3 && under > -30, "rising from the pit (feet " + String.format(Locale.ROOT, "%.1f", under) + ")");
        shot(ctx, "su_rise");
        // 다 솟아오름 → 포효 (큰 글자 · 보스 막대): 남쪽 46칸에서 몸 전체
        spec(ctx, server, cx, ALTAR_Y + 10, cz + 47, cx, ALTAR_Y + 16, cz);
        long rise = (long) Arena.BOSSES.obj(KEY).obj("arrive").num("rise");
        long roar = (long) Arena.BOSSES.obj(KEY).obj("arrive").num("roar");
        waitUntil(ctx, server, spawn + rise + 8);
        shot(ctx, "su_appear");
        waitUntil(ctx, server, spawn + rise + roar + 4);
        check(server.computeOnServer(s -> enc() != null && !enc().immune), "fight begins after the roar (not immune)");
        settle(server, 90);
        return true;
    }

    /** 레바테인 내려찍기: 붉은 긴 줄 (예고) → 내리침 (줄을 따라 불 · 박힌 칼 · 레바테인의 핵) → 핵을 부숨 (부위 파괴 · 식어 금 간 칼) */
    private void slam(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 90);
        stand(ctx, server, cx, cz + 24, cx, ALTAR_Y + 10, cz);
        long t0 = server.computeOnServer(s -> BossSurtr.slam(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L);
        check(t0 > 0, "slam started on the player");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("melee").num("windup");
        // 동쪽 위에서 비스듬히: 칼을 든 거인과 오른 어깨에서 뻗은 붉은 줄
        spec(ctx, server, cx + 30, ALTAR_Y + 18, cz + 30, cx - 1, ALTAR_Y + 10, cz + 12);
        waitUntil(ctx, server, t0 + 24);
        shot(ctx, "su_slam_tele");
        waitUntil(ctx, server, t0 + windup + 3);
        shot(ctx, "su_slam_hit");
        Vec3 core = server.computeOnServer(s -> {
            BossSurtr.M m = mem();
            return m != null && m.core != null && "sword".equals(m.core.kind) ? m.core.e.position() : null;
        });
        check(core != null, "sword core exposed at the blade tip");
        if (core == null) return;
        String label = server.computeOnServer(s -> mem().core != null ? mem().core.labelText() : null);
        check(label != null && label.contains("레바테인의 핵") && label.contains("100%"), "core label (" + label + ")");
        // 핵 가까이 (금빛 결정 · 이름표)
        spec(ctx, server, core.x + 6, ALTAR_Y + 2.5, core.z + 4, core.x, ALTAR_Y + 2.2, core.z);
        ctx.waitTicks(2);
        shot(ctx, "su_core");
        // 부숨: 핵 곁에 서서 (서바이벌) 큰 피해 → 부위 파괴 큰 글자
        stand(ctx, server, core.x + 5, core.z + 1, core.x, ALTAR_Y + 1.8, core.z);
        boolean broke = server.computeOnServer(s -> {
            BossSurtr.M m = mem();
            if (m.core == null) return false;
            serverHit(s, m.core.e, 6000F);
            return m.parts.get("sword").broken;
        });
        check(broke, "sword core broken → part break");
        ctx.waitTicks(4);
        shot(ctx, "su_break");
        int id = bossId(server);
        check(clientProp(ctx, id, "rpg:sword_broken") == 1, "client sees rpg:sword_broken (cooled blade texture)");
        // 칼이 박힌 채 식은 모습 (부위 파괴 후) — 서쪽 앞에서 오른팔 · 칼
        spec(ctx, server, cx - 16, ALTAR_Y + 6, cz + 30, cx - 2, ALTAR_Y + 4, cz + 14);
        waitUntil(ctx, server, t0 + windup + 30);
        shot(ctx, "su_sword_broken");
    }

    /** 횡베기: 붉은 부채꼴 띠 → 마지막 1초 금빛 → 스킬로 반격 (패턴이 끊김 · 반격 큰 글자 · 노란 "반격" 숫자) */
    private void sweep(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 90);
        stand(ctx, server, cx, cz + 28, cx, ALTAR_Y + 10, cz);
        long t0 = server.computeOnServer(s -> BossSurtr.sweep(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L);
        check(t0 > 0, "sweep started (player in the 20~38 band)");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("sweep").num("windup");
        int window = (int) Arena.BOSSES.obj(KEY).obj("sweep").num("window");
        // 남쪽 높이에서 띠 전체
        spec(ctx, server, cx, ALTAR_Y + 40, cz + 58, cx, ALTAR_Y, cz + 18);
        waitUntil(ctx, server, t0 + 22);
        shot(ctx, "su_sweep_tele");
        waitUntil(ctx, server, t0 + windup - window + 3);
        boolean open = server.computeOnServer(s -> mem().counter != null);
        check(open, "counter window open (gold band)");
        shot(ctx, "su_sweep_gold");
        // 반격: 띠 안에 서서 스킬 피해 (금빛 동안)
        stand(ctx, server, cx, cz + 28, cx, ALTAR_Y + 14, cz);
        boolean countered = server.computeOnServer(s -> {
            BossSurtr.M m = mem();
            if (m.counter == null) return false;
            Kit.applySkillDamage(player(s), enc().boss, 300, false, Kit.Cause.ATTACK, null);
            return m.counters == 1 && enc().cast == null;
        });
        check(countered, "skill in the gold window → counter (pattern cut)");
        ctx.waitTicks(3);
        shot(ctx, "su_counter");
        // 반격 뒤 3초 동안 노란 "반격" 숫자: 몸 가까이에서 때림
        stand(ctx, server, cx, cz + 10, cx, ALTAR_Y + 1.8, cz);
        String num = server.computeOnServer(s -> {
            serverHit(s, enc().boss, 120F);
            return number(s, enc().boss, "반격");
        });
        check(num != null, "damage number with the yellow 반격 mark (" + num + ")");
        ctx.waitTicks(2);
        shot(ctx, "su_counter_num");
        waitUntil(ctx, server, t0 + windup + 40);
    }

    /** 불의 주먹: 붉은 원 (반지름 7) → 내려침 · 건틀릿의 핵 → 부숨 */
    private void fist(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 90);
        double[] fa = BossKit.pair(Arena.BOSSES.obj(KEY).obj("fist"), "at");
        double d0 = Math.hypot(fa[0], fa[1]);
        stand(ctx, server, cx, cz + d0, cx, ALTAR_Y + 10, cz);
        long t0 = server.computeOnServer(s -> BossSurtr.fist(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L);
        check(t0 > 0, "fist started");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("fist").num("windup");
        spec(ctx, server, cx + 28, ALTAR_Y + 16, cz + 32, cx + 2, ALTAR_Y + 6, cz + 12);
        waitUntil(ctx, server, t0 + 30);
        shot(ctx, "su_fist_tele");
        waitUntil(ctx, server, t0 + windup + 3);
        shot(ctx, "su_fist_hit");
        Vec3 core = server.computeOnServer(s -> mem().core != null && "fist".equals(mem().core.kind) ? mem().core.e.position() : null);
        check(core != null, "gauntlet core exposed where the fist landed");
        if (core == null) return;
        stand(ctx, server, core.x + 5, core.z + 2, core.x, ALTAR_Y + 1.8, core.z);
        boolean broke = server.computeOnServer(s -> {
            BossSurtr.M m = mem();
            if (m.core == null) return false;
            serverHit(s, m.core.e, 6000F);
            return m.parts.get("fist").broken;
        });
        check(broke, "gauntlet core broken → part break");
        ctx.waitTicks(3);
        check(clientProp(ctx, bossId(server), "rpg:fist_broken") == 1, "client sees rpg:fist_broken");
        spec(ctx, server, cx + 18, ALTAR_Y + 8, cz + 24, cx + 2, ALTAR_Y + 6, cz + 6);
        ctx.waitTicks(2);
        shot(ctx, "su_fist_break");
        waitUntil(ctx, server, t0 + windup + 70);
    }

    /** 봉인의 사슬 기둥: 기둥 앞에서 끊긴 붉은 줄 · 사슬빛 고리 → 사슬이 칼을 붙잡음 (하늘색 "속박") → 풀리면 기둥이 무너짐 */
    private void bind(ClientGameTestContext ctx, TestServerContext server) {
        Vec3 c0 = server.computeOnServer(s -> mem().chains.get(0).at);
        Vec3 v = new Vec3(c0.x - cx, 0, c0.z - cz).normalize();
        Vec3 spot = new Vec3(c0.x + v.x * 8, ALTAR_Y, c0.z + v.z * 8);
        settle(server, Math.toDegrees(Math.atan2(v.z, v.x)));
        stand(ctx, server, spot.x, spot.z, cx, ALTAR_Y + 10, cz);
        long t0 = server.computeOnServer(s -> BossSurtr.slam(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L);
        boolean pinned = server.computeOnServer(s -> {
            BossSurtr.M m = mem();
            BossSurtr.Pin pin = m.body.want == null ? null : BossSurtr.bindPillar(enc(), BossSurtr.slamRect(enc(), m.body.want));
            return pin != null && pin.c() == m.chains.get(0);
        });
        check(t0 > 0 && pinned, "slam toward the player behind a chain pillar (the pillar is on the line)");
        if (t0 < 0) return;
        int windup = (int) Arena.BOSSES.obj(KEY).obj("melee").num("windup");
        int time = (int) Arena.BOSSES.obj(KEY).obj("bind").num("time");
        // 동쪽에서: 기둥 앞에서 끊긴 줄 · 기둥의 사슬빛 고리
        spec(ctx, server, c0.x + 22, ALTAR_Y + 12, c0.z - 12, c0.x - 4, ALTAR_Y + 7, c0.z - 2);
        waitUntil(ctx, server, t0 + 22);
        shot(ctx, "su_bind_tele");
        // 기둥 뒤 (서바이벌) 에서 붙잡히는 순간 (큰 글자)
        stand(ctx, server, spot.x, spot.z, cx, ALTAR_Y + 10, cz);
        waitUntil(ctx, server, t0 + windup + 4);
        boolean bound = server.computeOnServer(s -> mem().binds == 1 && mem().bindUntil > Tasks.now() && player(s).isAlive());
        check(bound, "chains bind the sword (player behind the pillar unhurt)");
        shot(ctx, "su_bind");
        // 속박 동안 하늘색 "속박" 숫자: 몸 가까이에서
        Vec3 near = new Vec3(cx + v.x * 10, ALTAR_Y, cz + v.z * 10);
        stand(ctx, server, near.x, near.z, cx, ALTAR_Y + 1.8, cz);
        String num = server.computeOnServer(s -> {
            serverHit(s, enc().boss, 120F);
            return number(s, enc().boss, "속박");
        });
        check(num != null, "damage number with the sky-blue 속박 mark (" + num + ")");
        ctx.waitTicks(2);
        shot(ctx, "su_bind_num");
        // 풀리면 기둥이 무너짐 (매단 랜턴 먼저 · 금 간 밑동)
        spec(ctx, server, c0.x + 14, ALTAR_Y + 9, c0.z + 12, c0.x, ALTAR_Y + 5, c0.z);
        waitUntil(ctx, server, t0 + windup + time + 6);
        boolean crumbled = server.computeOnServer(s -> {
            BossSurtr.Struct c = mem().chains.get(0);
            return c.broken && level(s).getBlockState(BlockPos.containing(c.at.x, c.at.y + 8, c.at.z)).isAir();
        });
        check(crumbled, "the pillar crumbled after the bind");
        shot(ctx, "su_crumble");
    }

    /** 무스펠의 업화: 달아오름 · 주황 안개 · 업화 막대 → 피해로 깎임 → 쓰러짐 (금색 "빈틈") · 불꽃 왕관의 핵 → 부숨 (머리 불꽃이 꺼짐) */
    private void inferno(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 90);
        chase(ctx, server, cx + 6, cz + 22, cx, ALTAR_Y + 18, cz, 8);
        long t0 = server.computeOnServer(s -> {
            BossSurtr.inferno(enc(), Tasks.now(), List.of(player(s)));
            return Tasks.now();
        });
        boolean on = server.computeOnServer(s -> mem().inferno != null && mem().inferno.gauge != null && !mem().inferno.gauge.isRemoved());
        check(on, "inferno: gauge prop (boss bar) spawned");
        waitUntil(ctx, server, t0 + 44);
        int id = bossId(server);
        double heat = clientProp(ctx, id, "rpg:heat");
        check(heat > 0.2, "client sees rpg:heat " + heat);
        shot(ctx, "su_inferno");
        // 피해로 막대를 반쯤 깎음
        double half = server.computeOnServer(s -> {
            BossSurtr.Inferno I = mem().inferno;
            serverHit(s, enc().boss, (float) (I.need / 2));
            return I.gauge.getHealth();
        });
        check(Math.abs(half - 500) < 1, "gauge 1000 → 500 by damage (" + half + ")");
        ctx.waitTicks(3);
        shot(ctx, "su_gauge");
        // 다 깎음 → 쓰러짐 (왕관의 핵은 몸 앞 15칸 — 비켜 섬)
        chase(ctx, server, cx + 12, cz + 28, cx, ALTAR_Y + 4, cz + 8, 8);
        ctx.waitTicks(11);
        boolean groggy = server.computeOnServer(s -> {
            BossSurtr.Inferno I = mem().inferno;
            if (I == null) return false;
            serverHit(s, enc().boss, (float) (I.left + 50));
            return mem().inferno == null && mem().groggyUntil > Tasks.now() && mem().core != null && "crown".equals(mem().core.kind);
        });
        check(groggy, "gauge emptied → collapse (groggy) · crown core");
        ctx.waitTicks(12);
        shot(ctx, "su_groggy");
        // 빈틈: 금색 숫자
        ctx.waitTicks(10);
        String num = server.computeOnServer(s -> {
            serverHit(s, enc().boss, 120F);
            return number(s, enc().boss, "빈틈");
        });
        check(num != null, "damage number with the gold 빈틈 mark (" + num + ")");
        ctx.waitTicks(2);
        shot(ctx, "su_groggy_num");
        // 불꽃 왕관의 핵을 부숨 → 머리 불꽃이 꺼짐
        long tb = now(server);
        Vec3 crown = server.computeOnServer(s -> mem().core != null ? mem().core.e.position() : null);
        if (crown != null) {
            stand(ctx, server, crown.x + 5, crown.z + 2, crown.x, ALTAR_Y + 1.8, crown.z);
            boolean broke = server.computeOnServer(s -> {
                BossSurtr.M m = mem();
                if (m.core == null) return false;
                serverHit(s, m.core.e, 6000F);
                return m.parts.get("crown").broken;
            });
            check(broke, "crown core broken → part break");
            ctx.waitTicks(3);
            check(clientProp(ctx, id, "rpg:crown_broken") == 1, "client sees rpg:crown_broken (head flames hidden)");
        } else fail("no crown core");
        // 일어난 뒤 (머리 불꽃이 꺼진 모습)
        int groggyTicks = (int) Arena.BOSSES.obj(KEY).obj("inferno").num("groggy");
        spec(ctx, server, cx + 22, ALTAR_Y + 14, cz + 34, cx, ALTAR_Y + 18, cz);
        waitUntil(ctx, server, tb + groggyTicks + 30);
        shot(ctx, "su_crown_broken");
    }

    /** 라그나로크: 가운데 7칸 동쪽으로 뛰어감 → 칼을 하늘로 (붉은 안개 · 결정 뒤 파란 그림자) → 불길 → 그림자 안은 삶 (결정이 녹음) */
    private void ragnarok(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 90);
        stand(ctx, server, cx + 22, cz + 18, cx, ALTAR_Y + 12, cz);
        long t0 = server.computeOnServer(s -> {
            withRand(0.0, () -> BossSurtr.ragnarok(enc(), Tasks.now(), List.of(player(s)), false));
            return Tasks.now();
        });
        check(server.computeOnServer(s -> mem().ragnarok != null), "ragnarok: leap (immune)");
        int leap = (int) Arena.BOSSES.obj(KEY).obj("ragnarok").num("leap");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("ragnarok").num("windup");
        waitUntil(ctx, server, t0 + leap + 8);
        // 첫 결정 (22.5°) 의 그림자 한가운데
        Vec3 safe = server.computeOnServer(s -> BossSurtr.shadowMid(enc(), mem().crystals.get(0), BossSurtr.bodyOf(enc())));
        Vec3 crystal = server.computeOnServer(s -> mem().crystals.get(0).at);
        // 그림자 뒤 높이에서: 결정 · 파란 그림자 · 칼을 든 거인
        spec(ctx, server, safe.x + 14, ALTAR_Y + 14, safe.z + 10, cx + 7, ALTAR_Y + 6, cz);
        waitUntil(ctx, server, t0 + leap + 30);
        shot(ctx, "su_rag_shadow");
        // 그림자 안 (서바이벌): 액션바 "얼음 결정 그림자 안 — 안전"
        chase(ctx, server, safe.x, safe.z, cx + 7, ALTAR_Y + 16, cz, 7);
        boolean inShadow = server.computeOnServer(s -> BossSurtr.shadowOf(enc(), player(s).position(), BossSurtr.bodyOf(enc())) != null);
        check(inShadow, "player in the blue shadow behind the crystal");
        if (!inShadow) {
            spec(ctx, server, safe.x + 14, ALTAR_Y + 14, safe.z + 10, cx + 7, ALTAR_Y + 6, cz);
        }
        waitUntil(ctx, server, t0 + leap + 70);
        shot(ctx, "su_rag_safe");
        // 불길 (초당 18칸) 이 지나감 → 결정이 막아 줌
        long release = t0 + leap + 6 + windup;
        waitUntil(ctx, server, release + 22);
        shot(ctx, "su_rag_wave");
        waitUntil(ctx, server, release + 32);
        boolean saved = server.computeOnServer(s -> player(s).isAlive() && mem().ragnarok != null && mem().ragnarok.saved == 1);
        check(saved, "the crystal shadow saved the player");
        shot(ctx, "su_rag_saved");
        // 다 지나가면 숨겨 준 결정이 녹음
        spec(ctx, server, crystal.x + 10, ALTAR_Y + 7, crystal.z + 9, crystal.x, ALTAR_Y + 2, crystal.z);
        for (int i = 0; i < 120 && server.computeOnServer(s -> mem().ragnarok != null); i++) ctx.waitTick();
        boolean melted = server.computeOnServer(s -> mem().crystals.get(0).melted && level(s).getBlockState(BlockPos.containing(crystal.x, crystal.y + 1, crystal.z)).isAir());
        check(melted, "the sheltering crystal melted");
        String clientAir = ctx.computeOnClient(mc -> mc.level.getBlockState(BlockPos.containing(crystal.x, crystal.y + 1, crystal.z)).toString());
        check(clientAir.contains("air"), "client sees the melted crystal (" + clientAir + ")");
        ctx.waitTicks(6);
        shot(ctx, "su_rag_melt");
    }

    /** 쓰러뜨림 (진짜 클릭): 처치 큰 글자 · 보상 · 딜 순위 · 부위 파괴 채팅 */
    private void kill(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, 90);
        stand(ctx, server, cx, cz + 7.2, cx, ALTAR_Y + 2.5, cz + 4);
        server.runOnServer(s -> enc().boss.setHealth(5F));
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        boolean dead = server.computeOnServer(s -> Bosses.encounterAt(altar) == null);
        if (!dead) {
            fail("clicking did not kill surtr — hitting from the server");
            server.runOnServer(s -> {
                if (enc() != null) serverHit(s, enc().boss, 50F);
            });
            ctx.waitTicks(2);
        } else ok("real click kills surtr");
        List<String> board = server.computeOnServer(s -> fight == null ? List.<String>of() : Bosses.damageBoard(fight));
        check(!board.isEmpty() && board.get(0).contains("[딜 순위]") && board.get(0).contains("수르트"), "damage board " + board);
        List<String> note = server.computeOnServer(s -> fight == null ? List.<String>of() : Bosses.module(KEY).killNote(fight));
        check(note.size() == 2 && note.get(0).contains("3/3"), "part break note " + note);
        String r = server.computeOnServer(s -> {
            if (altar.cooldownUntil <= System.currentTimeMillis() + 1_700_000) return "no 30 min cooldown";
            int em = RpgGearTests.countIn(player(s), Items.EMERALD);
            int adv = RpgGearTests.countIn(player(s), mmorpg.fabric.rune.RuneItems.ADV_TICKET);
            int shard = RpgGearTests.countIn(player(s), mmorpg.fabric.gear.GearItems.byId("rpg:laevateinn_shard"));
            int heart = RpgGearTests.countIn(player(s), mmorpg.fabric.gear.GearItems.byId("rpg:muspel_heart"));
            return em >= 48 && adv >= 1 && shard >= 5 && heart >= 2 ? "ok em " + em + " adv " + adv + " shard " + shard + " heart " + heart
                    : "em " + em + " adv " + adv + " shard " + shard + " heart " + heart;
        });
        check(r.startsWith("ok"), "rewards + part bonus + cooldown (" + r + ")");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 9);
        ctx.waitTicks(6);
        shot(ctx, "su_kill");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }
}
