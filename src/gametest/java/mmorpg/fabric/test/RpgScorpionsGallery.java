package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossScorpions;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.TitleFit;
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
 * 5단계 사진첩 (화면 시험, only=scorpions): 평평한 세계에 모래와 바위의 신전을 짓고 (제단 + Arena.buildArena) 실제로 싸움 —
 * 제단을 눌러 도전 창 (함께 나옴: 두 마리) → 도전 (진짜 클릭) → 소환진 (나올 자리마다 모이는 고리) → 두 마리 등장 (서로 마주 봄 · 보스 막대 둘),
 * 평타 (사막 집게 90° · 바위 내려찍기 120° — 예고 → 판정), 공생 (7칸 안 금빛 끈 + 큰 글자 → 진짜 클릭: 피해 -60% 눈앞 글자),
 * 독침 (초록 원 → 꼬리에서 독침), 모래 잠행 (가라앉음 → 흙먼지 줄기가 쫓음 → 멈춘 자리 흙색 원 → 튀어나옴),
 * 맹독 표식 (초록 원 · 머리 위 문양 → 3 2 1 → 혼자라 즉사 — 불사의 토템이 막음), 바위 던지기 (갈색 원 셋 → 포물선 바위 → 떨어짐),
 * 대지의 파동 (퍼지는 고리 — 첫 고리는 땅에서 맞고, 둘째 고리는 진짜 점프로 피함), 석화의 포효 (돌 문양 · 2 1 → 보던 사람 석화 = 속박 + 침묵),
 * 원거리 견제 (바위 조각), 짝 잃은 분노 (사막 전갈을 진짜 클릭으로 쓰러뜨림 → 바위 전갈 붉은 몸 · 큰 글자), 처치 (진짜 클릭 → 보상 · 가방).
 * 패턴은 모듈 함수를 바로 부르고 그 사이 두 마리가 스스로 고르지 않게 멈춤 (M 의 다음 틱을 멀리). 사진 이름 sc_* → ci-logs 의 shots/.
 * 확인 항목은 [scorpions-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgScorpionsGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-scorpions-gallery");
    private static final String KEY = "scorpions";
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
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("scorpions")) return;
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
            bond(ctx, server);
            sting(ctx, server);
            burrow(ctx, server);
            venom(ctx, server);
            boulders(ctx, server);
            tremor(ctx, server);
            roar(ctx, server);
            far(ctx, server);
            rage(ctx, server);
            kill(ctx, server);
        } finally {
            LOG.info("[scorpions-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError("scorpions gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도우미

    private void ok(String what) {
        LOG.info("[scorpions-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[scorpions-gallery] FAIL {}", what);
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

    private Bosses.Unit desert() {
        return BossScorpions.desert(enc());
    }

    private Bosses.Unit rock() {
        return BossScorpions.rock(enc());
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

    /** 뒤 3인칭 사진 자리: 플레이어를 가운데에서 (px, pz) 에 세우고 (tx, ty, tz) (가운데 기준, ty = 바닥 위 높이) 를 보게 — 카메라는 시선 뒤로 dist 칸 */
    private void chase(ClientGameTestContext ctx, TestServerContext server, double px, double pz, double tx, double ty, double tz, double dist) {
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, dist);
        look(ctx, server, cx + px, ALTAR_Y, cz + pz, cx + tx, ALTAR_Y + ty, cz + tz);
        ctx.waitTick();
        double got = ctx.computeOnClient(mc -> mc.player == null ? -1.0 : mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
        if (got < dist - 0.5) LOG.warn("[scorpions-gallery] camera pulled in: {} of {} blocks", String.format(Locale.ROOT, "%.2f", got), dist);
    }

    /** 1인칭: 가운데에서 (px, py, pz) 에 서서 (tx, ty, tz) 를 봄 (py · ty = 바닥 위 높이) */
    private void eye(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx + px, ALTAR_Y + py, cz + pz, cx + tx, ALTAR_Y + ty, cz + tz);
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
            LOG.warn("[scorpions-gallery] chunks not all rendered in {} ticks — going on", timeout);
        }
    }

    /** 서버 틱이 at 이 될 때까지 (패턴의 예고 · 판정 순간을 찍으려고) */
    private static void waitUntil(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 400 && server.computeOnServer(s -> Tasks.now()) < at; i++) ctx.waitTick();
    }

    private static long now(TestServerContext server) {
        return server.computeOnServer(s -> Tasks.now());
    }

    /** 그 마리를 가운데에서 (x, z) 자리에 세우고 멈춤 — face (가운데 기준, null = 그대로) 를 봄 */
    private void settle(TestServerContext server, String key, double x, double z, Vec3 face) {
        server.runOnServer(s -> {
            Bosses.Unit u = BossScorpions.unitOf(enc(), key);
            if (u == null || !u.alive || u.boss == null) return;
            Bosses.hold(u, true);
            u.cast = null;
            Vec3 at = new Vec3(cx + x, ALTAR_Y, cz + z);
            BossKit.teleport(u.boss, at, face == null ? null : new Vec3(cx + face.x, ALTAR_Y, cz + face.z));
        });
    }

    /** 두 마리가 스스로 패턴 · 평타 · 원거리 견제를 고르지 않게 (사진첩이 차례로 부름) — 걷지도 않음 */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossScorpions.M m = BossScorpions.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            for (Bosses.Unit u : e.units) {
                u.meleeAt = FAR_FUTURE;
                u.farAt = FAR_FUTURE;
                Bosses.hold(u, true);
            }
        });
    }

    /** 플레이어 체력을 채우고 상태 · 효과 (실명 · 느림 · 독) 를 풂 — 다음 장면을 위해 */
    private void refresh(TestServerContext server) {
        server.runCommand("effect clear @a minecraft:blindness");
        server.runCommand("effect clear @a minecraft:slowness");
        server.runCommand("effect clear @a minecraft:poison");
        server.runCommand("title @a clear");
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Status.clear(p);
            p.setHealth(p.getMaxHealth());
            p.setDeltaMovement(Vec3.ZERO);
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
        look(ctx, server, cx, ALTAR_Y + 30, cz + 0.01, cx, ALTAR_Y, cz);
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
                ok("temple of sand and stone built");
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /**
     * 싸울 플레이어: 서바이벌 · 저항 IV (받는 피해 -80% — 맹독 표식의 즉사는 왼손의 불사의 토템이 막음) · 레벨 · 스탯 · 칼
     */
    private void prepare(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 3 true");
        server.runCommand("xp set @a 40 levels");
        ctx.waitTicks(5);
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l10_rare");
        server.runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
        server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
    }

    // ------------------------------------------------------------ 장면

    /** 제단 누르기 (진짜 오른쪽 클릭) → 도전 창 (함께 나옴: 두 마리) → 도전 (진짜 클릭) → 나올 자리마다 모이는 고리 → 두 마리 등장 */
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
        check(body.contains("Lv.18") && body.contains("함께 나옴") && body.contains("거대 사막 전갈") && body.contains("초거대 바위 전갈") && body.contains("모두 쓰러뜨려야 보상")
                && body.contains("바위 조각") && body.contains("15분"), "challenge text (level · both units · far shot · cooldown)");
        shot(ctx, "sc_form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state) && enc().units.size() == 2);
        check(summoning, "도전 → summoning two units");
        if (!summoning) return false;
        // 소환진 (가운데) + 나올 자리 (서쪽 · 동쪽 8칸) 마다 모이는 고리 — 남쪽 13칸에서 (등장도 같은 자리에서)
        look(ctx, server, cx, ALTAR_Y, cz + 13, cx, ALTAR_Y + 0.8, cz);
        ctx.waitTicks(22);
        shot(ctx, "sc_summon");
        for (int t = 0; t < 40 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        // 등장 바로 뒤 (서로 마주 봄 — 바닐라 시선이 플레이어 쪽으로 돌리기 전), 멈춤
        freeze(server);
        ctx.waitTicks(3);
        String r = server.computeOnServer(s -> {
            Bosses.Unit d = desert();
            Bosses.Unit k = rock();
            if (d == null || k == null || d.boss == null || k.boss == null) return "no units";
            if (d.boss.getHealth() != 5000F || k.boss.getHealth() != 7000F) return "hp " + d.boss.getHealth() + " · " + k.boss.getHealth();
            return BossKit.hdist(d.boss.position(), d.home) < 3 && BossKit.hdist(k.boss.position(), k.home) < 3 ? "ok" : "not at their homes";
        });
        check("ok".equals(r), "desert (5000) west · rock (7000) east appeared (" + r + ")");
        int[] ids = server.computeOnServer(s -> new int[] {desert().boss.getId(), rock().boss.getId()});
        String rend = ctx.computeOnClient(mc -> {
            for (int id : ids) {
                Entity e = mc.level == null ? null : mc.level.getEntity(id);
                if (e == null) return "no entity " + id;
                Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
                if (!(r0 instanceof BedrockRenderer)) return "renderer " + r0;
            }
            return "ok";
        });
        check("ok".equals(rend), "both drawn by BedrockRenderer (" + rend + ")");
        shot(ctx, "sc_appear");
        ctx.waitTicks(12);
        boolean bars = server.computeOnServer(s -> desert().boss.bossBar().getPlayers().contains(player(s)) && rock().boss.bossBar().getPlayers().contains(player(s))
                && desert().boss.bossBar() != rock().boss.bossBar());
        check(bars, "a boss bar per unit, both shown to the player");
        return true;
    }

    /** 평타: 사막 전갈 집게 (앞 90° 0.6초) · 바위 전갈 내려찍기 (앞 120° 1초) — 예고 → 판정, 뒤 위에서 */
    private void melee(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("title @a clear");
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(-8, 0, 4));
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(8, 0, 4));
        chase(ctx, server, -8, 4.2, -8, 0, 1.2, 8);
        boolean started = server.computeOnServer(s -> {
            Bosses.Unit d = desert();
            d.meleeAt = 0;
            boolean r = Bosses.melee(d, Tasks.now(), BossKit.fighters(enc()));
            d.meleeAt = FAR_FUTURE;
            return r && "melee".equals(d.cast.id) && rock().cast == null;
        });
        check(started, "desert pinch (only the desert casts)");
        ctx.waitTicks(7);
        shot(ctx, "sc_pinch");
        ctx.waitTicks(7);
        shot(ctx, "sc_pinch_hit");
        ctx.waitTicks(8);
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(0, 0, 0));
        refresh(server);
        chase(ctx, server, 8, 5, 8, 0, 1.5, 9);
        started = server.computeOnServer(s -> {
            Bosses.Unit k = rock();
            k.meleeAt = 0;
            boolean r = Bosses.melee(k, Tasks.now(), BossKit.fighters(enc()));
            k.meleeAt = FAR_FUTURE;
            return r && "melee".equals(k.cast.id);
        });
        check(started, "rock claw slam");
        ctx.waitTicks(13);
        shot(ctx, "sc_slam");
        ctx.waitTicks(8);
        shot(ctx, "sc_slam_hit");
        ctx.waitTicks(10);
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(0, 0, 0));
        refresh(server);
    }

    /** 공생: 두 마리가 7칸 안 → 금빛 끈 + 큰 글자 → 진짜 클릭으로 사막 전갈을 때림: 피해 -60% (눈앞 글자) */
    private void bond(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, BossScorpions.DESERT, -3, 0, new Vec3(0, 0, 6));
        settle(server, BossScorpions.ROCK, 3, 0, new Vec3(0, 0, 6));
        chase(ctx, server, 0, 8, 0, 1.0, 0, 7);
        ctx.waitTicks(12);
        boolean bonded = server.computeOnServer(s -> BossScorpions.mem(enc()).bond);
        check(bonded, "symbiosis bond within 7 blocks");
        shot(ctx, "sc_bond");
        // 진짜 클릭: 사막 전갈 몸 (가운데에서 서쪽 3칸, 높이 약 1칸) 을 조준
        server.runCommand("title @a clear");
        eye(ctx, server, -3, 0, 3.0, -3, 0.8, 0);
        float hp0 = server.computeOnServer(s -> desert().boss.getHealth());
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        float hp1 = server.computeOnServer(s -> desert().boss.getHealth());
        check(hp1 < hp0, "player hits the bonded desert (" + hp0 + " → " + hp1 + ")");
        shot(ctx, "sc_bond_hit");
        // 떼어 놓음
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(0, 0, 0));
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(0, 0, 0));
        ctx.waitTicks(3);
        check(!server.computeOnServer(s -> BossScorpions.mem(enc()).bond), "bond broken when apart");
        ctx.waitTicks(10);
    }

    /** 독침: 가장 먼 사람 발밑 초록 원 (1초) → 꼬리에서 독침 (독) */
    private void sting(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(-5, 0, 10));
        chase(ctx, server, -5, 10, -8, 1.0, 0, 7);
        long t0 = server.computeOnServer(s -> {
            Bosses.Unit d = desert();
            ServerPlayer t = BossScorpions.stingTarget(d, BossKit.fighters(enc()));
            if (t == null) return -1L;
            BossScorpions.sting(d, Tasks.now(), t);
            return Tasks.now();
        });
        check(t0 > 0, "sting on the farthest player");
        int windup = (int) Arena.BOSSES.obj(KEY).objsOrNull("units").get(0).obj("sting").num("windup");
        waitUntil(ctx, server, t0 + windup - 4);
        shot(ctx, "sc_sting");
        waitUntil(ctx, server, t0 + windup + 1);
        shot(ctx, "sc_sting_hit");
        boolean poisoned = server.computeOnServer(s -> player(s).hasEffect(net.minecraft.world.effect.MobEffects.POISON));
        check(poisoned, "sting poisons");
        ctx.waitTicks(10);
    }

    /** 모래 잠행: 모래 속으로 (무적) → 흙먼지 줄기가 쫓음 → 멈춘 자리 흙색 원 → 튀어나옴 (원 밖으로 비켜섬) */
    private void burrow(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(-8, 0, 7));
        chase(ctx, server, -8, 7, -8, 0, 1.5, 9);
        long t0 = server.computeOnServer(s -> {
            BossScorpions.burrow(desert(), Tasks.now(), player(s));
            return Tasks.now();
        });
        check(server.computeOnServer(s -> BossScorpions.mem(enc()).burrow != null && "burrow".equals(desert().cast.id)), "burrow cast");
        waitUntil(ctx, server, t0 + 9);
        check(server.computeOnServer(s -> desert().immune), "immune underground");
        shot(ctx, "sc_burrow");
        // 흙먼지 줄기가 쫓아옴 (가라앉은 뒤 0.4초)
        waitUntil(ctx, server, t0 + 16 + 8);
        shot(ctx, "sc_burrow_trail");
        long spotAt = -1;
        for (int i = 0; i < 80 && spotAt < 0; i++) {
            ctx.waitTick();
            spotAt = server.computeOnServer(s -> {
                BossScorpions.Burrow b = BossScorpions.mem(enc()).burrow;
                return b != null && b.spot != null ? b.hitAt : -1L;
            });
        }
        check(spotAt > 0, "trail stopped → dust circle");
        ctx.waitTicks(10);
        shot(ctx, "sc_burrow_disc");
        // 원 밖으로 (남쪽 4칸 더) 비켜서 튀어나오는 것을 봄
        chase(ctx, server, -8, 12, -8, 0, 4.5, 9);
        waitUntil(ctx, server, spotAt + 2);
        shot(ctx, "sc_burrow_emerge");
        check(server.computeOnServer(s -> BossScorpions.mem(enc()).burrow == null && !desert().immune), "burst out, vulnerable again");
        ctx.waitTicks(16);
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(0, 0, 0));
    }

    /** 맹독 표식: 초록 원 + 머리 위 문양이 따라다님 → 3 2 1 → 혼자라 즉사 (왼손 불사의 토템이 막음) */
    private void venom(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        chase(ctx, server, -4, 6, -8, 1.0, 0, 6);
        long t0 = server.computeOnServer(s -> {
            BossScorpions.venom(desert(), Tasks.now(), List.of(player(s)));
            return Tasks.now();
        });
        check(server.computeOnServer(s -> BossScorpions.mem(enc()).venom != null && !enc().mod.farOk(rock(), Tasks.now())), "venom mark · far shots pause");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "sc_venom");
        // 카운트다운 (눈앞 글자 3 2 1): 큰 글자를 지우고 "2" 가 뜬 뒤
        server.runCommand("title @a clear");
        int time = (int) Arena.BOSSES.obj(KEY).objsOrNull("units").get(0).obj("venom").num("time");
        waitUntil(ctx, server, t0 + time - 40 + 2);
        shot(ctx, "sc_venom_count");
        int totems0 = server.computeOnServer(s -> player(s).getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? 1 : 0);
        waitUntil(ctx, server, t0 + time + 2);
        shot(ctx, "sc_venom_judge");
        String r = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            boolean alive = p.isAlive() && !p.isDeadOrDying();
            boolean used = !p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
            return alive && used ? "ok" : "alive " + alive + " totem used " + used;
        });
        check(totems0 == 1 && "ok".equals(r), "alone = instant death — the totem of undying saved the player (" + r + ")");
        ctx.waitTicks(30);
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(0, 0, 0));
    }

    /** 바위 던지기: 갈색 원 셋 (1.6초) → 꼬리로 던진 바위가 포물선으로 → 떨어짐 (느림 II) */
    private void boulders(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        server.runCommand("effect clear @a");
        server.runCommand("effect give @a minecraft:resistance infinite 3 true");
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(4, 0, 9));
        chase(ctx, server, 4, 10, 6, 0, 4, 9);
        long t0 = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            Bosses.Unit k = rock();
            List<Vec3> spots = List.of(new Vec3(p.getX(), ALTAR_Y, p.getZ()), new Vec3(cx + 1, ALTAR_Y, cz + 6.5), new Vec3(cx + 8, ALTAR_Y, cz + 7));
            BossScorpions.bouldersAt(k, Tasks.now(), spots);
            return Tasks.now();
        });
        int windup = (int) Arena.BOSSES.obj(KEY).objsOrNull("units").get(1).obj("boulders").num("windup");
        waitUntil(ctx, server, t0 + 8 + 12);
        shot(ctx, "sc_boulders");
        waitUntil(ctx, server, t0 + windup + 1);
        shot(ctx, "sc_boulders_hit");
        check(server.computeOnServer(s -> BossScorpions.mem(enc()).boulders.isEmpty() && player(s).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)),
                "boulders landed (slowness II on the player in a circle)");
        ctx.waitTicks(12);
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(8, 0, 9));
    }

    /** 대지의 파동: 세 번 내려찍음 → 퍼지는 고리 — 첫 고리는 땅에 서서 맞고, 둘째 고리는 진짜 점프로 피함 */
    private void tremor(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        chase(ctx, server, 8, 7, 8, 0.5, 0, 9);
        long t0 = server.computeOnServer(s -> {
            BossScorpions.tremor(rock(), Tasks.now());
            return Tasks.now();
        });
        var tr = Arena.BOSSES.obj(KEY).objsOrNull("units").get(1).obj("tremor");
        int first = (int) tr.num("first");
        int gap = (int) tr.num("gap");
        double speed = tr.num("speed");
        // 고리가 7칸 (플레이어) 에 닿는 때: 시작 뒤 (7 - 0.35) / (speed / 20) 틱
        int reach = (int) Math.floor((7 - 0.35) / (speed / 20));
        waitUntil(ctx, server, t0 + first + reach - 6);
        shot(ctx, "sc_tremor");
        waitUntil(ctx, server, t0 + first + reach + 4);
        boolean hit1 = server.computeOnServer(s -> {
            BossScorpions.M m = BossScorpions.mem(enc());
            return !m.waves.isEmpty() && m.waves.get(0).hit().contains(player(s).getUUID());
        });
        check(hit1, "first ring hits the player standing on the ground");
        // 첫 고리에 밀려난 자리를 되돌림 → 둘째 고리: 닿기 6틱 전부터 점프 키를 누르고 있음 (진짜 키 — 한 번 누름은 그 틱에 땅에 없으면 (되돌린 뒤 내려앉는 중) 안 뛰어
        // CI 에서 가끔 맞았음. 누르고 있으면 땅에 닿는 대로 뜀, 한 번 뛰면 약 12틱 공중)
        server.runOnServer(s -> Status.clear(player(s)));
        chase(ctx, server, 8, 7, 8, 0.5, 0, 9);
        waitUntil(ctx, server, t0 + first + gap + reach - 6);
        ctx.getInput().holdKey(options -> options.keyJump);
        waitUntil(ctx, server, t0 + first + gap + reach);
        shot(ctx, "sc_tremor_jump");
        waitUntil(ctx, server, t0 + first + gap + reach + 2);
        ctx.getInput().releaseKey(options -> options.keyJump);
        waitUntil(ctx, server, t0 + first + gap + reach + 6);
        String dodged = server.computeOnServer(s -> {
            BossScorpions.M m = BossScorpions.mem(enc());
            if (m.waves.size() < 2) return "waves " + m.waves.size();
            return m.waves.get(1).hit().contains(player(s).getUUID()) ? "hit" : "ok";
        });
        check("ok".equals(dodged), "jumping over the second ring dodges it (" + dodged + ")");
        ctx.waitTicks(40);
    }

    /** 석화의 포효: 돌 문양 · 모이는 고리 · 2 1 → 바위 전갈 쪽을 보던 사람은 석화 (속박 + 침묵 — 몸에 돌 조각) */
    private void roar(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(8, 0, 8));
        chase(ctx, server, 8, 8, 8, 1.2, 0, 6);
        long t0 = server.computeOnServer(s -> {
            BossScorpions.roar(rock(), Tasks.now());
            return Tasks.now();
        });
        int windup = (int) Arena.BOSSES.obj(KEY).objsOrNull("units").get(1).obj("roar").num("windup");
        server.runCommand("title @a clear");
        waitUntil(ctx, server, t0 + windup - 18);
        shot(ctx, "sc_roar");
        waitUntil(ctx, server, t0 + windup + 3);
        shot(ctx, "sc_roar_stone");
        boolean stoned = server.computeOnServer(s -> Status.isRooted(player(s)) && Status.isSilenced(player(s)));
        check(stoned, "looking at the rock scorpion → petrified (rooted + silenced)");
        ctx.waitTicks(20);
        refresh(server);
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑 갈색 원 + 꼬리 끝에서 바위 조각 → 느림 */
    private void far(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(-2, 0, 13));
        chase(ctx, server, -2, 13.5, 8, 2, 0, 5);
        server.runOnServer(s -> Bosses.farThrow(enc(), rock(), Tasks.now(), List.of(player(s))));
        ctx.waitTicks(16);
        shot(ctx, "sc_far");
        ctx.waitTicks(15);
        shot(ctx, "sc_far_hit");
        check(server.computeOnServer(s -> enc().farShots.isEmpty()), "rock fragment landed");
        ctx.waitTicks(10);
    }

    /** 짝 잃은 분노: 사막 전갈을 진짜 클릭으로 쓰러뜨림 → 싸움은 이어지고 바위 전갈이 분노 (붉은 몸 · 큰 글자 · 붉은 기운) */
    private void rage(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        settle(server, BossScorpions.DESERT, -8, 0, new Vec3(-8, 0, 4));
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(-8, 0, 4));
        eye(ctx, server, -8, 0, 3.0, -8, 0.8, 0);
        server.runOnServer(s -> desert().boss.setHealth(5F));
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(3);
        String r = server.computeOnServer(s -> {
            Bosses.Unit d = desert();
            Bosses.Unit k = rock();
            if (d.alive) return "desert still alive " + d.boss.getHealth();
            if (enc() == null || !"fight".equals(enc().state)) return "fight ended";
            return k.boss.propBool("rpg:rage") && k.dmgMult > 1.3 && k.fast ? "ok" : "rock not enraged";
        });
        check("ok".equals(r), "desert falls → the fight goes on, rock enraged (" + r + ")");
        // 남은 바위 전갈: 남쪽 위에서 (분노 동작 · 붉은 몸)
        chase(ctx, server, 2, 9, 8, 1.2, 0, 6);
        int id = server.computeOnServer(s -> rock().boss.getId());
        boolean red = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof BedrockMob m && m.propBool("rpg:rage"));
        check(red, "rock scorpion drawn red on the client (rpg:rage)");
        ctx.waitTicks(10);
        boolean bars = server.computeOnServer(s -> !desert().boss.bossBar().getPlayers().contains(player(s)) && rock().boss.bossBar().getPlayers().contains(player(s)));
        check(bars, "the fallen unit's boss bar is gone, the rock's stays");
        shot(ctx, "sc_rage");
        ctx.waitTicks(10);
    }

    /** 처치 (진짜 클릭): 보상 채팅 · 큰 글자 · 가방 */
    private void kill(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        settle(server, BossScorpions.ROCK, 8, 0, new Vec3(8, 0, 4));
        eye(ctx, server, 8, 0, 3.6, 8, 1.0, 0);
        server.runOnServer(s -> rock().boss.setHealth(5F));
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 7);
        ctx.waitTicks(6);
        String r = server.computeOnServer(s -> {
            if (Bosses.encounterAt(altar) != null) return "still fighting";
            if (altar.cooldownUntil <= System.currentTimeMillis() + 890_000) return "no 15 min cooldown";
            int em = RpgGearTests.countIn(player(s), Items.EMERALD);
            return em >= 12 ? "ok " + em : "emeralds " + em;
        });
        check(r.startsWith("ok"), "both down → rewards + 15 min cooldown (" + r + ")");
        // 긴 처치 제목 ("사막 전갈 · 바위 전갈 처치!" — 4배면 화면보다 넓음) 은 화면에 맞게 줄여 그림 (TitleFit)
        String titleNow = ctx.computeOnClient(mc -> TitleFit.titleText());
        float fit = ctx.computeOnClient(mc -> TitleFit.lastFactor());
        check(titleNow.contains("처치") && fit < 0.999F, "long kill title shrunk to fit the screen (" + titleNow + " x" + fit + ")");
        shot(ctx, "sc_kill");
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
        shot(ctx, "sc_loot");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }
}
