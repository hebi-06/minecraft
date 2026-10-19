package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossJormungandr;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
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
 * 5단계 사진첩 (화면 시험, only=jorm): 평평한 세계에 요르문간드의 뱀의 섬 (가운데 섬 · 독의 해자 · 흰 석영 고리) 을 짓고 진짜로 싸움 —
 * 제단 누르기 → 도전 창 → 도전 (진짜 클릭) → 해자에서 떠오름, 헤엄 (엔진 pose — 위에서 · 옆에서, 꼬리가 해자를 따라 굽고 물에 잠김),
 * 물어뜯기 (목을 돌려 붉은 부채꼴), 독니 돌진 (보라 길 → 섬을 가로질러 돌진 → 해자로 휘어 들어감), 꼬리 휘감기 (꼬리 쪽 주황 부채꼴),
 * 독해 범람 (흰 고리 밖 초록 판 · 3 2 1 → 독 물결 → 독이 빠지면 모인 곳으로 돌진), 아홉 걸음의 독 (초록 줄기 → 발밑 초록 원 · 남은 걸음),
 * 세계를 감는 고리 (고리 소품이 필드 둘레에 솟음 · 머리 앞 초록 틈 · 3 2 1 → 조여 옴 — 플레이어는 틈 안에서 버팀 → 해자로 돌아감),
 * 원거리 견제 (독 뱉기), 쓰러뜨림. 패턴은 모듈 함수를 바로 부르고, 그 사이 뱀이 스스로 고르지 않게 멈춤 (BossJormungandr.M 의 다음 틱을 멀리 — 헤엄은 그대로).
 * 사진 이름 jm_* → ci-logs 의 shots/. 확인 항목은 [jorm-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgJormungandrGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-jorm-gallery");
    private static final String KEY = "jormungandr";
    private static final int ALTAR_Y = -55;
    private static final int GROUND = -60;
    private static final long FAR_FUTURE = Long.MAX_VALUE / 4;

    private final List<String> failures = new ArrayList<>();
    private Arena.Altar altar;
    private double cx;
    private double cz;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("jorm")) return;
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
            swim(ctx, server);
            bite(ctx, server);
            dash(ctx, server);
            tail(ctx, server);
            flood(ctx, server);
            venom(ctx, server);
            ring(ctx, server);
            far(ctx, server);
            kill(ctx, server);
        } finally {
            LOG.info("[jorm-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError("jorm gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도우미

    private void ok(String what) {
        LOG.info("[jorm-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[jorm-gallery] FAIL {}", what);
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
        if (got < dist - 0.5) LOG.warn("[jorm-gallery] camera pulled in: {} of {} blocks", String.format(Locale.ROOT, "%.2f", got), dist);
    }

    /** 관전 모드로 높은 곳에서 (tx, ty, tz) 를 봄 */
    private static void top(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        server.runCommand("gamemode spectator @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, px, py, pz, tx, ty, tz);
    }

    /** 땅으로 내려와 서바이벌 (떨어지지 않게 먼저 옮김) */
    private static void ground(ClientGameTestContext ctx, TestServerContext server, double px, double pz) {
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f", px, ALTAR_Y, pz));
        ctx.waitTicks(2);
        server.runCommand("gamemode survival @a");
        ctx.waitTicks(3);
    }

    /** 사진 — 창 크기 그대로, 알림 토스트는 치움 */
    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(name);
    }

    private static void waitChunks(ClientGameTestContext ctx, TestSingleplayerContext world, int timeout) {
        try {
            world.getConnection().waitForChunksRender(true, timeout);
        } catch (AssertionError e) {
            LOG.warn("[jorm-gallery] chunks not all rendered in {} ticks — going on", timeout);
        }
    }

    /** 서버 틱이 at 이 될 때까지 */
    private static void waitUntil(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 600 && server.computeOnServer(s -> Tasks.now()) < at; i++) ctx.waitTick();
    }

    /** 가운데에서 각도 a, 거리 r 의 자리 {x, z} */
    private double[] polar(double r, double a) {
        return new double[] {cx + Math.cos(a) * r, cz + Math.sin(a) * r};
    }

    /** 뱀이 스스로 패턴 · 물어뜯기 · 원거리 견제를 고르지 않게 (헤엄은 그대로) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossJormungandr.M m = BossJormungandr.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.ringDone.addAll(List.of(0.5, 0.2));
            m.bigUntil = FAR_FUTURE;
            m.floodDash = false;
            e.meleeAt = FAR_FUTURE;
            e.farAt = FAR_FUTURE;
        });
    }

    /** 목뿌리를 해자 가운데 원의 각도 ang 에 (반시계 방향 — 몸은 도는 쪽, 꼬리는 원을 따라), 하던 틀을 비움 — 서버 스레드에서 */
    private void placeOnCircle(double ang) {
        Bosses.Enc e = enc();
        BossJormungandr.M m = BossJormungandr.mem(e);
        if (m == null) return;
        e.cast = null;
        m.swim.charging = false;
        m.swim.dir = 1;
        BossJormungandr.setOnCircle(e, ang);
        e.faceTo = m.swim.byaw;
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
        look(ctx, server, cx, ALTAR_Y + 36, cz + 0.01, cx, ALTAR_Y, cz);
        waitChunks(ctx, world, 1200);
        String started = server.computeOnServer(s -> {
            ServerLevel level = level(s);
            BlockPos pos = new BlockPos(x, ALTAR_Y, z);
            Ticking.holdArea(level, Vec3.atCenterOf(pos), R + 12, "g");
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
                ok("serpent isle built");
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /** 싸울 플레이어: 서바이벌 (땅으로 먼저) · 저항 V · 레벨 · 스탯 · 칼 */
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

    /** 제단 누르기 → 도전 창 → 도전 → 소환진 → 깨운 사람 쪽 (남쪽) 해자에서 떠오름 */
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
        check(body.contains("Lv.32") && body.contains("독 뱉기") && body.contains("15분") && !body.contains("보스 재료"), "challenge text (level · far shot · cooldown · no boss materials)");
        shot(ctx, "jm_form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state));
        check(summoning, "도전 → summoning");
        if (!summoning) return false;
        look(ctx, server, cx, ALTAR_Y, cz + 4, cx, ALTAR_Y + 0.6, cz);
        ctx.waitTicks(22);
        shot(ctx, "jm_summon");
        // 해자에서 떠오름: 섬 남쪽 끝에서 해자를 봄
        look(ctx, server, cx - 3, ALTAR_Y, cz + 7, cx - 1, ALTAR_Y + 1.5, cz + 15.5);
        for (int t = 0; t < 40 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        ctx.waitTicks(6);
        shot(ctx, "jm_emerge");
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return "no boss";
            if (e.boss.getHealth() != 22000F || e.boss.getMaxHealth() != 22000F) return "hp " + e.boss.getHealth();
            double rp = BossKit.hdist(BossJormungandr.pivotOf(e), new Vec3(cx, ALTAR_Y, cz));
            return Math.abs(rp - 15.75) < 0.01 ? "ok" : "pivot r " + rp;
        });
        check("ok".equals(r), "jormungandr rose in the moat with 22000 hp (" + r + ")");
        int id = server.computeOnServer(s -> enc() != null && enc().boss != null ? enc().boss.getId() : -1);
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        check("ok".equals(rend), "jormungandr drawn by BedrockRenderer (" + rend + ")");
        freeze(server);
        ctx.waitTicks(14);
        shot(ctx, "jm_appear");
        return true;
    }

    /**
     * 헤엄 (엔진 pose): 위에서 — 목뿌리가 해자 가운데 원을 돌고 꼬리가 원을 따라 굽음 · 물에 잠김, 옆에서 — 섬 가장자리에서 본 뱀.
     * 클라이언트에서 머리 (엔티티) 가 움직이고 속성 rpg:sink 1 · rpg:curve 가 보이는지
     */
    private void swim(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("title @a clear");
        top(ctx, server, cx + 0.01, ALTAR_Y + 40, cz + 8, cx, ALTAR_Y, cz + 1);
        ctx.waitTicks(6);
        shot(ctx, "jm_swim_top");
        int id = server.computeOnServer(s -> enc().boss.getId());
        Vec3 a0 = ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof BedrockMob m ? m.position() : Vec3.ZERO);
        ctx.waitTicks(10);
        String c = ctx.computeOnClient(mc -> {
            if (!(mc.level != null && mc.level.getEntity(id) instanceof BedrockMob m)) return "no entity";
            double moved = BossKit.hdist(m.position(), a0);
            return String.format(Locale.ROOT, "moved %.2f sink %.2f curve %.2f", moved, m.prop("rpg:sink"), m.prop("rpg:curve"));
        });
        boolean swimming = c.startsWith("moved") && Double.parseDouble(c.split(" ")[1]) > 1.0 && c.contains("sink 1.00") && !c.contains("curve 0.00");
        check(swimming, "client: head swims along the moat, sunk, tail curved (" + c + ")");
        // 옆에서: 머리 쪽 섬 가장자리 (반지름 9) 에서 머리를 봄
        Vec3 head = server.computeOnServer(s -> enc().boss.position());
        double ha = Math.atan2(head.z - cz, head.x - cx);
        double[] p = polar(9, ha - 0.25);
        look(ctx, server, p[0], ALTAR_Y + 3, p[1], head.x, ALTAR_Y + 1, head.z);
        ctx.waitTicks(4);
        shot(ctx, "jm_swim");
        ground(ctx, server, cx, cz);
    }

    /** 물어뜯기 (모듈 평타): 목이 닿는 사람 쪽으로 목을 돌리며 목뿌리에서 앞 70° 붉은 부채꼴 (0.9초) → 물어뜯음 */
    private void bite(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> placeOnCircle(Math.PI / 2));
        chase(ctx, server, cx - 2, cz + 9.5, cx - 2, ALTAR_Y + 1, cz + 15, 7);
        long t0 = server.computeOnServer(s -> {
            placeOnCircle(Math.PI / 2);
            return BossJormungandr.bite(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L;
        });
        check(t0 > 0, "bite cast (neck turns toward the player)");
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "jm_bite");
        waitUntil(ctx, server, t0 + 20);
        shot(ctx, "jm_bite_hit");
        ctx.waitTicks(15);
    }

    /** 독니 돌진: 보라 길 (폭 3.2, 1.6초) → 섬을 가로질러 반대편 해자까지 돌진 (몸 · 꼬리가 따라옴) → 해자로 휘어 들어가 다시 돎 */
    private void dash(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> placeOnCircle(Math.PI / 2));
        chase(ctx, server, cx - 3, cz + 2, cx - 1, ALTAR_Y + 1, cz + 13, 11);
        long t0 = server.computeOnServer(s -> {
            placeOnCircle(Math.PI / 2);
            return BossJormungandr.dash(enc(), Tasks.now(), List.of(player(s)), false) ? Tasks.now() : -1L;
        });
        check(t0 > 0, "dash cast toward the player on the island");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("dash").num("windup");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "jm_dash_tele");
        // 위에서 돌진을 봄 (관전 — 맞지 않음)
        top(ctx, server, cx + 0.01, ALTAR_Y + 38, cz + 9, cx, ALTAR_Y, cz + 1);
        waitUntil(ctx, server, t0 + windup + 12);
        shot(ctx, "jm_dash");
        boolean charging = server.computeOnServer(s -> BossJormungandr.mem(enc()).swim.charging && !BossJormungandr.mem(enc()).swim.onCircle);
        check(charging, "charging across the island (head moved off the circle)");
        waitUntil(ctx, server, t0 + windup + 45);
        shot(ctx, "jm_dash_end");
        ground(ctx, server, cx, cz);
        freeze(server);
    }

    /** 꼬리 휘감기: 목뿌리 뒤 꼬리 쪽 부채꼴 (해자를 따라 12칸, 가운데에서 8칸 밖 ~ 담장 너머) 주황 1.5초 → 꼬리로 휘감음 (속박) */
    private void tail(ClientGameTestContext ctx, TestServerContext server) {
        double a = Math.toRadians(68);
        double[] p = polar(10.5, a);
        server.runOnServer(s -> placeOnCircle(Math.PI / 2));
        chase(ctx, server, p[0], p[1], cx + 2, ALTAR_Y + 1, cz + 16, 11);
        long t0 = server.computeOnServer(s -> {
            placeOnCircle(Math.PI / 2);
            return BossJormungandr.tailLash(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L;
        });
        check(t0 > 0, "tail lash cast (player in the tail zone)");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("tail").num("windup");
        waitUntil(ctx, server, t0 + 15);
        shot(ctx, "jm_tail_tele");
        waitUntil(ctx, server, t0 + windup + 3);
        shot(ctx, "jm_tail");
        check(server.computeOnServer(s -> Status.isRooted(player(s))), "tail hit → rooted");
        ctx.waitTicks(50);
        server.runOnServer(s -> Status.unroot(player(s)));
    }

    /** 독해 범람: 위에서 — 흰 고리 테두리 + 고리 밖 초록 판 · 해자의 물보라, 땅에서 (고리 안) — 3 2 1 → 독 물결 → 독이 빠지면 모인 곳으로 돌진 */
    private void flood(ClientGameTestContext ctx, TestServerContext server) {
        freeze(server);
        server.runOnServer(s -> placeOnCircle(Math.PI / 2));
        top(ctx, server, cx + 0.01, ALTAR_Y + 40, cz + 8, cx, ALTAR_Y, cz + 1);
        long t0 = server.computeOnServer(s -> {
            placeOnCircle(Math.PI / 2);
            BossJormungandr.flood(enc(), Tasks.now());
            return Tasks.now();
        });
        int windup = (int) Arena.BOSSES.obj(KEY).obj("flood").num("windup");
        int hold = (int) Arena.BOSSES.obj(KEY).obj("flood").num("hold");
        waitUntil(ctx, server, t0 + 22);
        shot(ctx, "jm_flood_top");
        ground(ctx, server, cx + 2, cz + 2);
        chase(ctx, server, cx + 2, cz + 2, cx + 1, ALTAR_Y + 1, cz + 12, 12);
        server.runCommand("title @a clear");
        waitUntil(ctx, server, t0 + windup - 17);
        shot(ctx, "jm_flood_count");
        waitUntil(ctx, server, t0 + windup + 4);
        shot(ctx, "jm_flood_wave");
        boolean safe = server.computeOnServer(s -> !player(s).hasEffect(net.minecraft.world.effect.MobEffects.POISON));
        check(safe, "inside the white ring: no poison");
        // 독이 빠지면 곧바로 모인 곳 (가운데에 가장 가까운 사람) 으로 돌진
        waitUntil(ctx, server, t0 + windup + hold + 10 + 16);
        boolean dashing = server.computeOnServer(s -> enc().cast != null && "dash".equals(enc().cast.id));
        check(dashing, "poison drained → dash across the gathered ring");
        shot(ctx, "jm_flood_dash");
        ctx.waitTicks(40);
        freeze(server);
    }

    /** 아홉 걸음의 독: 쉿 + 입에서 초록 줄기 → 발밑 초록 원 (독을 빼 주는 곳) · 액션바 남은 걸음 (그동안 플레이어는 움직이지 않음) */
    private void venom(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> placeOnCircle(Math.PI / 2));
        chase(ctx, server, cx + 3, cz + 3, cx - 1, ALTAR_Y + 2, cz + 14, 7);
        ctx.waitTicks(4);
        long t0 = server.computeOnServer(s -> {
            Bosses.cast(enc(), "pause", 200); // 목만 돌림 (헤엄치지 않게)
            return BossJormungandr.venomMark(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L;
        });
        check(t0 > 0, "nine-step venom marked on the player");
        waitUntil(ctx, server, t0 + 7);
        shot(ctx, "jm_venom");
        server.runCommand("title @a clear");
        waitUntil(ctx, server, t0 + 24);
        shot(ctx, "jm_venom_mark");
        String used = server.computeOnServer(s -> {
            BossJormungandr.Venom V = BossJormungandr.mem(enc()).venom;
            return V == null ? "gone" : String.format(Locale.ROOT, "%.2f", V.marks().get(0).used);
        });
        check("0.00".equals(used), "standing still uses no steps (" + used + ")");
        server.runOnServer(s -> {
            BossJormungandr.M m = BossJormungandr.mem(enc());
            m.venom = null;
            enc().cast = null;
        });
        ctx.waitTicks(10);
    }

    /**
     * 세계를 감는 고리: 몸이 필드 둘레에 떠오름 (고리 소품) · 머리 앞 60° 초록 틈 · 3 2 1 → 조여 옴. 플레이어는 틈 안 (반지름 12) 에서 몸이 지나갈 때까지 버팀 →
     * 끝나면 틈 쪽 해자로 떠올라 다시 돎
     */
    private void ring(ClientGameTestContext ctx, TestServerContext server) {
        freeze(server);
        ground(ctx, server, cx + 1, cz + 1);
        long t0 = server.computeOnServer(s -> {
            placeOnCircle(Math.PI / 2);
            BossJormungandr.ring(enc(), Tasks.now(), 0.5);
            return Tasks.now();
        });
        double g = server.computeOnServer(s -> BossJormungandr.mem(enc()).ring == null ? 0.0 : BossJormungandr.mem(enc()).ring.g);
        check(server.computeOnServer(s -> {
            BossJormungandr.Ring rg = BossJormungandr.mem(enc()).ring;
            return rg != null && rg.prop != null && !rg.prop.isRemoved() && rg.roster.containsKey(player(s).getUUID());
        }), "ring prop spawned, player on the roster");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("ring").num("windup");
        top(ctx, server, cx + 0.01, ALTAR_Y + 44, cz + 10, cx, ALTAR_Y, cz + 1);
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "jm_ring_rise");
        int pid = server.computeOnServer(s -> {
            BossJormungandr.Ring rg = BossJormungandr.mem(enc()).ring;
            return rg == null || rg.prop == null ? -1 : rg.prop.getId();
        });
        // 틈 안 (반지름 12) 에서 가운데를 봄
        double[] p = polar(12, g);
        ground(ctx, server, p[0], p[1]);
        chase(ctx, server, p[0], p[1], cx, ALTAR_Y + 1, cz, 11);
        server.runCommand("title @a clear");
        waitUntil(ctx, server, t0 + windup - 16);
        shot(ctx, "jm_ring_count");
        waitUntil(ctx, server, t0 + windup + 60);
        shot(ctx, "jm_ring_squeeze");
        String rad = ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(pid) instanceof BedrockMob m ? String.format(Locale.ROOT, "%.2f", m.prop("rpg:radius"))
                : "none");
        check(!"none".equals(rad) && Double.parseDouble(rad) < 17, "client ring radius shrinks (" + rad + ")");
        top(ctx, server, cx + 0.01, ALTAR_Y + 34, cz + 8, cx, ALTAR_Y, cz + 1);
        waitUntil(ctx, server, t0 + windup + 120);
        shot(ctx, "jm_ring_tight");
        double speed = Arena.BOSSES.obj(KEY).obj("ring").num("speed");
        double span = Arena.BOSSES.obj(KEY).obj("ring").num("start") - Arena.BOSSES.obj(KEY).obj("ring").num("end");
        waitUntil(ctx, server, t0 + windup + (long) Math.ceil(span / speed * 20) + 16);
        shot(ctx, "jm_ring_end");
        check(server.computeOnServer(s -> BossJormungandr.mem(enc()).ring == null && player(s).isAlive() && !player(s).isDeadOrDying()),
                "ring over, player who stayed in the gap survived");
        ground(ctx, server, cx, cz);
        freeze(server);
        ctx.waitTicks(10);
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑 초록 원 + 독 뱉기가 포물선으로 → 독 */
    private void far(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> placeOnCircle(Math.PI / 2));
        chase(ctx, server, cx, cz - 5, cx - 2, ALTAR_Y + 3, cz + 14, 4);
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            Bosses.cast(e, "pause", 60);
            Bosses.farThrow(e, e, Tasks.now(), List.of(player(s)));
        });
        ctx.waitTicks(16);
        shot(ctx, "jm_far");
        ctx.waitTicks(15);
        shot(ctx, "jm_far_hit");
        check(server.computeOnServer(s -> enc().farShots.isEmpty()), "far shot (venom spit) landed");
        server.runCommand("effect clear @a minecraft:poison");
        ctx.waitTicks(10);
    }

    /** 쓰러뜨림 (진짜 클릭): 섬 가장자리에서 해자의 머리를 때림 → 처치 글자 · 보상 → 가방 */
    private void kill(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            placeOnCircle(Math.PI / 2);
            Bosses.cast(enc(), "pause", 400);
        });
        Vec3 head = server.computeOnServer(s -> enc().boss.position());
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, head.x, ALTAR_Y, head.z - 3.6, head.x, ALTAR_Y + 1.2, head.z);
        server.runOnServer(s -> enc().boss.setHealth(5F));
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        boolean dead = server.computeOnServer(s -> Bosses.encounterAt(altar) == null);
        if (!dead) {
            fail("clicking the head did not kill — hitting it from the server");
            server.runOnServer(s -> enc().boss.hurtServer(level(s), player(s).damageSources().playerAttack(player(s)), 50F));
            ctx.waitTicks(2);
        }
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 8);
        ctx.waitTicks(6);
        String r = server.computeOnServer(s -> {
            if (Bosses.encounterAt(altar) != null) return "still fighting";
            if (altar.cooldownUntil <= System.currentTimeMillis()) return "no cooldown";
            int em = RpgGearTests.silver(player(s));
            return em >= 22 ? "ok " + em : "emeralds " + em;
        });
        check(r.startsWith("ok"), "jormungandr killed → rewards + cooldown (" + r + ")");
        shot(ctx, "jm_kill");
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
        shot(ctx, "jm_loot");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }
}
