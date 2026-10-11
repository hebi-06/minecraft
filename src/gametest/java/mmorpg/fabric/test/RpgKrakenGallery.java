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
import mmorpg.fabric.boss.BossKraken;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.gear.GearItems;
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
import net.minecraft.util.Mth;
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
 * 5단계 사진첩 (화면 시험, only=kraken): 평평한 세계에 가라앉은 신전을 짓고 (제단 + Arena.buildArena) 실제로 싸움 —
 * 제단을 눌러 도전 창 → 도전 (진짜 클릭) → 소환진 → 등장 (가운데에 박혀 깨운 사람 쪽을 봄), 물어뜯기 (돌아서며 붉은 부채꼴 → 판정),
 * 촉수 내려치기 (주황 부채꼴 둘 → 첫째 내려침 · 휘감김 → 돌아서 둘째), 먹물 분사 (보라 부채꼴 → 검은 물보라 · 실명 · 침묵 → 먹물 웅덩이),
 * 소용돌이 (물살 무늬 · 가운데 붉은 원 · 끌려감 → 짓누름), 촉수 소환 (물구덩이 넷에서 솟음 · 이름표 체력 막대 · 회복 끈 → 감아올림 · 휘두르기 →
 * 진짜 클릭 · 쓰러짐 → 넷 다 베면 그로기 → 진짜 클릭 +40%), 해일 (거품 벽 · 기둥 뒤 노란 자리 → 3 2 1 → 물결이 가로지름 → 기둥 뒤에서 버팀),
 * 높은 곳 (기둥 위 → 촉수가 끌어내림), 원거리 견제 (먹물 포탄), 처치 (진짜 클릭 → 보상 · 보스 재료 · 가방). 크라켄은 끝까지 제 자리.
 * 패턴은 모듈 함수를 바로 부르고 그 사이 크라켄이 스스로 고르지 않게 멈춤. 사진 이름 kr_* → ci-logs 의 shots/.
 * 확인 항목은 [kraken-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgKrakenGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-kraken-gallery");
    private static final String KEY = "kraken";
    private static final int ALTAR_Y = -55;
    private static final int GROUND = -60;
    private static final long FAR_FUTURE = Long.MAX_VALUE / 4;

    private final List<String> failures = new ArrayList<>();
    private Arena.Altar altar;
    private double cx;
    private double cz;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("kraken")) return;
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
            bite(ctx, server);
            slam(ctx, server);
            ink(ctx, server);
            whirl(ctx, server);
            tentacles(ctx, server);
            tide(ctx, server);
            high(ctx, server);
            far(ctx, server);
            kill(ctx, server);
        } finally {
            LOG.info("[kraken-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError("kraken gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도우미

    private void ok(String what) {
        LOG.info("[kraken-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[kraken-gallery] FAIL {}", what);
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

    /** 뒤 3인칭: 플레이어를 가운데에서 (px, pz) 에 세우고 (tx, ty, tz) (가운데 기준, ty = 바닥 위 높이) 를 보게 — 카메라는 시선 뒤로 dist 칸 */
    private void chase(ClientGameTestContext ctx, TestServerContext server, double px, double pz, double tx, double ty, double tz, double dist) {
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, dist);
        look(ctx, server, cx + px, ALTAR_Y, cz + pz, cx + tx, ALTAR_Y + ty, cz + tz);
        ctx.waitTick();
        double got = ctx.computeOnClient(mc -> mc.player == null ? -1.0 : mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
        if (got < dist - 0.5) LOG.warn("[kraken-gallery] camera pulled in: {} of {} blocks", String.format(Locale.ROOT, "%.2f", got), dist);
    }

    /** 1인칭: 가운데에서 (px, py, pz) 에 서서 (tx, ty, tz) 를 봄 (py · ty = 바닥 위 높이) */
    private void eye(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx + px, ALTAR_Y + py, cz + pz, cx + tx, ALTAR_Y + ty, cz + tz);
    }

    /** 관전자로 하늘에서 (1인칭) — 큰 바닥 예고를 내려다봄. 끝나면 survival() 로 되돌림 */
    private void sky(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
        server.runCommand("gamemode spectator @a");
        eye(ctx, server, px, py, pz, tx, ty, tz);
    }

    private static void survival(TestServerContext server) {
        server.runCommand("gamemode survival @a");
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(name);
    }

    private static void waitChunks(ClientGameTestContext ctx, TestSingleplayerContext world, int timeout) {
        try {
            world.getConnection().waitForChunksRender(true, timeout);
        } catch (AssertionError e) {
            LOG.warn("[kraken-gallery] chunks not all rendered in {} ticks — going on", timeout);
        }
    }

    private static void waitUntil(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 400 && server.computeOnServer(s -> Tasks.now()) < at; i++) ctx.waitTick();
    }

    /** 크라켄이 스스로 패턴 · 평타 · 원거리 견제를 고르지 않게 (사진첩이 차례로 부름) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossKraken.M m = BossKraken.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.bigUntil = FAR_FUTURE;
            m.addsDone.addAll(e.def.obj("tentacles").numbersOrNull("at"));
            m.tideDone.addAll(e.def.obj("tide").numbersOrNull("at"));
            e.meleeAt = FAR_FUTURE;
            e.farAt = FAR_FUTURE;
        });
    }

    /** 플레이어 체력을 채우고 상태 · 효과 (실명 · 느림) 를 풂, 크라켄의 틀을 비움 */
    private void refresh(TestServerContext server) {
        server.runCommand("effect clear @a minecraft:blindness");
        server.runCommand("effect clear @a minecraft:slowness");
        server.runCommand("title @a clear");
        server.runOnServer(s -> {
            ServerPlayer p = player(s);
            Status.clear(p);
            p.setHealth(p.getMaxHealth());
            p.setDeltaMovement(Vec3.ZERO);
        });
    }

    /** 크라켄이 가운데 (제단 칸, 발 = 바닥) 에 그대로인지 */
    private void inPlace(TestServerContext server, String when) {
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return "no kraken";
            double d = BossKit.hdist(e.boss.position(), e.center);
            return d < 0.05 && Math.abs(e.boss.getY() - ALTAR_Y) < 0.05 ? "ok" : String.format(Locale.ROOT, "moved %.2f / y %.2f", d, e.boss.getY());
        });
        check("ok".equals(r), "kraken still in its place " + when + " (" + r + ")");
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
        look(ctx, server, cx, ALTAR_Y + 32, cz + 0.01, cx, ALTAR_Y, cz);
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
                ok("sunken temple built");
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /** 싸울 플레이어: 서바이벌 · 저항 IV (받는 피해 -80%) · 레벨 · 스탯 · 칼 */
    private void prepare(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 3 true");
        server.runCommand("xp set @a 40 levels");
        ctx.waitTicks(5);
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l10_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
        server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
    }

    // ------------------------------------------------------------ 장면

    /** 제단 누르기 → 도전 창 → 도전 → 소환진 → 등장 (가운데에 박혀 깨운 사람 쪽을 봄) */
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
        check(body.contains("Lv.25") && body.contains("먹물 포탄") && body.contains("칠흑 먹물") && body.contains("심해의 눈") && body.contains("15분"),
                "challenge text (level · far shot · boss materials · cooldown)");
        shot(ctx, "kr_form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state));
        check(summoning, "도전 → summoning");
        if (!summoning) return false;
        // 남쪽 14칸 (깨운 사람이 선 쪽) 에서
        look(ctx, server, cx, ALTAR_Y, cz + 14, cx, ALTAR_Y + 2.5, cz);
        ctx.waitTicks(22);
        shot(ctx, "kr_summon");
        for (int t = 0; t < 40 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        freeze(server);
        ctx.waitTicks(6);
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return "no boss";
            if (e.boss.getHealth() != 15000F) return "hp " + e.boss.getHealth();
            return Math.abs(Mth.wrapDegrees(e.boss.getYRot())) < 2 ? "ok" : "yaw " + e.boss.getYRot();
        });
        check("ok".equals(r), "kraken (15000) appeared facing the challenger (south) (" + r + ")");
        inPlace(server, "after appearing");
        int id = server.computeOnServer(s -> enc().boss.getId());
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        check("ok".equals(rend), "kraken drawn by BedrockRenderer (" + rend + ")");
        shot(ctx, "kr_appear");
        // 둘레를 돌아도 (동쪽에서) 스스로 몸을 돌리지 않음
        float yaw0 = server.computeOnServer(s -> enc().boss.getYRot());
        look(ctx, server, cx + 10, ALTAR_Y, cz + 2, cx, ALTAR_Y + 2.5, cz);
        ctx.waitTicks(30);
        float yaw1 = server.computeOnServer(s -> enc().boss.getYRot());
        check(Math.abs(Mth.wrapDegrees(yaw1 - yaw0)) < 0.5, "does not turn by itself (vanilla look AI off): " + yaw0 + " → " + yaw1);
        return true;
    }

    /** 물어뜯기: 동쪽을 보던 크라켄이 남쪽 사람 쪽으로 돌며 앞 100° 붉은 부채꼴 (0.9초) → 물어뜯음 */
    private void bite(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("title @a clear");
        server.runOnServer(s -> Bosses.face(enc(), new Vec3(1, 0, 0)));
        chase(ctx, server, 0, 5.2, 0, 1.5, 0, 7);
        ctx.waitTicks(4);
        boolean started = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            e.meleeAt = 0;
            boolean r = Bosses.melee(e, Tasks.now(), BossKit.fighters(e));
            e.meleeAt = FAR_FUTURE;
            return r && "melee".equals(e.cast.id) && Bosses.turnLeft(e) > 1.0;
        });
        check(started, "bite: turning toward the player in reach");
        ctx.waitTicks(4);
        shot(ctx, "kr_bite_turn");
        ctx.waitTicks(8);
        shot(ctx, "kr_bite");
        ctx.waitTicks(7);
        shot(ctx, "kr_bite_hit");
        String r = server.computeOnServer(s -> Bosses.turnLeft(enc()) < 1e-3 && Math.abs(Mth.wrapDegrees(enc().boss.getYRot())) < 1 ? "ok" : "yaw " + enc().boss.getYRot());
        check("ok".equals(r), "turned to face the bitten player (16°/tick) (" + r + ")");
        inPlace(server, "after the bite");
        ctx.waitTicks(10);
    }

    /** 촉수 내려치기: 주황 부채꼴 둘 (남쪽 · 동쪽) → 남쪽을 내려침 (휘감김) → 동쪽으로 돌아 내려침 */
    private void slam(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        long t0 = server.computeOnServer(s -> {
            BossKraken.slamDirs(enc(), Tasks.now(), List.of(new Vec3(0, 0, 1), new Vec3(1, 0, 0)));
            return Tasks.now();
        });
        var d = Arena.BOSSES.obj(KEY).obj("slam");
        int windup = (int) d.num("windup");
        int gap = (int) d.num("gap");
        sky(ctx, server, 13, 15, 15, 4, 0, 4);
        waitUntil(ctx, server, t0 + 20);
        shot(ctx, "kr_slam");
        survival(server);
        chase(ctx, server, 1.5, 8, 5, 1, 2, 9);
        waitUntil(ctx, server, t0 + windup + 1);
        shot(ctx, "kr_slam_hit");
        check(server.computeOnServer(s -> Status.isRooted(player(s))), "slam hits the player in the south fan → wrapped (rooted)");
        waitUntil(ctx, server, t0 + windup + gap + 1);
        shot(ctx, "kr_slam_2");
        String r = server.computeOnServer(s -> Math.abs(Mth.wrapDegrees(enc().boss.getYRot() + 90)) < 1 ? "ok" : "yaw " + enc().boss.getYRot());
        check("ok".equals(r), "turned east for the second slam (" + r + ")");
        inPlace(server, "after the slams");
        ctx.waitTicks(15);
    }

    /** 먹물 분사: 보라 부채꼴 (먼 사람 — 좁고 길게) → 검은 물보라 · 실명 · 침묵 → 먹물 웅덩이 셋 */
    private void ink(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        chase(ctx, server, 0, 14, 0, 2, 0, 6);
        long t0 = server.computeOnServer(s -> BossKraken.ink(enc(), Tasks.now(), List.of(player(s))) ? Tasks.now() : -1L);
        check(t0 > 0, "ink at the player");
        int windup = (int) Arena.BOSSES.obj(KEY).obj("ink").num("windup");
        waitUntil(ctx, server, t0 + windup - 6);
        shot(ctx, "kr_ink");
        waitUntil(ctx, server, t0 + windup + 1);
        boolean hit = server.computeOnServer(s -> player(s).hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && Status.isSilenced(player(s)));
        check(hit, "ink hit: blinded + silenced");
        server.runCommand("effect clear @a minecraft:blindness");
        ctx.waitTicks(1);
        shot(ctx, "kr_ink_hit");
        int pools = server.computeOnServer(s -> BossKraken.mem(enc()).puddles.size());
        check(pools == 3, "three ink pools (" + pools + ")");
        chase(ctx, server, 0, 17, 0, 0, 9, 9);
        ctx.waitTicks(12);
        server.runCommand("effect clear @a minecraft:blindness");
        shot(ctx, "kr_ink_pool");
        ctx.waitTicks(5);
    }

    /** 소용돌이: 물살 무늬 · 가운데 7칸 붉은 원 · 0.5초마다 끌려감 → 끝에 짓누름 (바깥으로 달아난 뒤) */
    private void whirl(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        // 앞 장면의 먹물 웅덩이를 치움 — 웅덩이 피해 (1초마다, 보스가 때린 것) 는 보스 반대쪽으로 밀어 (애드온 applyDamage 와 같음) 끌려가는 것을 가림
        server.runOnServer(s -> BossKraken.mem(enc()).puddles.clear());
        // 길 (+z 축) 의 12칸은 촉수 물구덩이 — 구덩이에 빠진 채로는 끌려 나오지 못함 (가장자리가 한 칸 높음). 길과 대각선 기둥 사이 (22.5°, 12칸) 에서 가운데까지 막힌 것 없음
        double sx = 12 * Math.sin(Math.toRadians(22.5));
        double sz = 12 * Math.cos(Math.toRadians(22.5));
        chase(ctx, server, sx, sz, 0, 0, 3, 10);
        double r0 = server.computeOnServer(s -> Math.hypot(player(s).getX() - cx, player(s).getZ() - cz));
        long t0 = server.computeOnServer(s -> {
            BossKraken.whirl(enc(), Tasks.now());
            return Tasks.now();
        });
        int time = (int) Arena.BOSSES.obj(KEY).obj("whirl").num("time");
        waitUntil(ctx, server, t0 + 40);
        shot(ctx, "kr_whirl");
        double r1 = server.computeOnServer(s -> Math.hypot(player(s).getX() - cx, player(s).getZ() - cz));
        check(r1 < r0 - 1, "pulled toward the center (" + String.format(Locale.ROOT, "%.1f → %.1f blocks", r0, r1) + ")");
        // 바깥으로 달아남 (가운데 짓누름 원 밖)
        chase(ctx, server, 0, 14, 0, 0, 3, 10);
        waitUntil(ctx, server, t0 + time + 1);
        shot(ctx, "kr_whirl_crush");
        check(server.computeOnServer(s -> BossKraken.mem(enc()).whirl == null), "whirl ended with the crush");
        inPlace(server, "after the whirl");
        ctx.waitTicks(20);
    }

    /**
     * 촉수 소환: 물구덩이 넷에서 솟음 (이름표 체력 막대 · 초록 회복 끈) → 남쪽 촉수가 감아올림 (붉은 원) → 휘두름 → 진짜 클릭 (체력 막대 줄어듦) →
     * 쓰러짐 (몸부림치며 가라앉음) → 넷 다 베면 그로기 → 진짜 클릭 (+40%)
     */
    private void tentacles(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        long t0 = server.computeOnServer(s -> BossKraken.summonAdds(enc(), Tasks.now()) ? Tasks.now() : -1L);
        check(t0 > 0, "tentacle summon");
        sky(ctx, server, 0, 21, 19, 0, 0, 1);
        waitUntil(ctx, server, t0 + 14 + 12);
        String r = server.computeOnServer(s -> {
            BossKraken.M m = BossKraken.mem(enc());
            if (m.adds == null || m.adds.list.size() != 4) return "adds " + (m.adds == null ? null : m.adds.list.size());
            for (BossKraken.Add a : m.adds.list) {
                if (!(a.e instanceof BedrockMob b) || b.getHealth() != 380F || BossKraken.labelOf(enc(), a.e) == null) return "tentacle not ready";
                if (BossKit.hdist(a.e.position(), a.hole) > 0.05) return "not in its pool";
            }
            return "ok";
        });
        check("ok".equals(r), "four tentacles rose from the pools (hp 380 · label) (" + r + ")");
        shot(ctx, "kr_adds");
        survival(server);
        // 남쪽 물구덩이 (가운데에서 남쪽 12칸) 의 촉수: 이름표 체력 막대
        eye(ctx, server, 0, 0, 16.5, 0, 4.2, 12);
        ctx.waitTicks(6);
        shot(ctx, "kr_adds_label");
        // 남쪽 촉수 (둘째 — 나온 뒤 3초) 감아올림 → 휘두르기
        chase(ctx, server, 0, 16.6, 0, 1.5, 12, 6);
        waitUntil(ctx, server, t0 + 14 + 60 + 12);
        shot(ctx, "kr_adds_coil");
        waitUntil(ctx, server, t0 + 14 + 60 + 24 + 2);
        shot(ctx, "kr_adds_whip");
        // 진짜 클릭: 물구덩이 둘레 (산호) 에서 촉수 몸을 조준
        eye(ctx, server, 0, 0, 14.6, 0, 1.6, 12);
        Entity south = server.computeOnServer(s -> {
            for (BossKraken.Add a : BossKraken.mem(enc()).adds.list) if (Math.abs(a.hole.z - cz - 12) < 0.1) return a.e;
            return null;
        });
        float hp0 = server.computeOnServer(s -> south instanceof BedrockMob b ? b.getHealth() : -1F);
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(3);
        String hit = server.computeOnServer(s -> {
            if (!(south instanceof BedrockMob b)) return "no tentacle";
            Display.TextDisplay label = BossKraken.labelOf(enc(), south);
            String bar = label == null ? "" : label.getText().getString();
            return b.getHealth() < hp0 && bar.contains(String.valueOf((int) Math.ceil(b.getHealth()))) ? "ok" : "hp " + b.getHealth() + " label " + bar;
        });
        check("ok".equals(hit), "player hits a tentacle → hp bar on its label goes down (" + hit + ")");
        shot(ctx, "kr_adds_hit");
        // 쓰러뜨림 (서버에서 큰 피해) → 몸부림치며 가라앉음
        ctx.waitTicks(12);
        server.runOnServer(s -> south.hurtServer(level(s), player(s).damageSources().playerAttack(player(s)), 1000F));
        ctx.waitTicks(8);
        shot(ctx, "kr_adds_die");
        check(server.computeOnServer(s -> south.entityTags().contains("rpg_gone")), "fallen tentacle (rpg_gone)");
        // 나머지 셋 → 그로기
        server.runOnServer(s -> {
            for (BossKraken.Add a : BossKraken.mem(enc()).adds.list) {
                if (a.dead) continue;
                a.e.hurtServer(level(s), player(s).damageSources().playerAttack(player(s)), 1000F);
            }
        });
        chase(ctx, server, 0, 6.5, 0, 2, 0, 6);
        ctx.waitTicks(4);
        boolean staggered = server.computeOnServer(s -> enc().staggerUntil > Tasks.now() && BossKraken.mem(enc()).adds == null);
        check(staggered, "all four cut → kraken staggered");
        shot(ctx, "kr_stagger");
        // 그로기 중 진짜 클릭: +40% (눈앞 글자)
        server.runCommand("title @a clear");
        eye(ctx, server, 0, 0, 4.4, 0, 1.8, 0);
        float k0 = server.computeOnServer(s -> enc().boss.getHealth());
        ctx.waitTicks(20);
        attack(ctx);
        ctx.waitTicks(2);
        float k1 = server.computeOnServer(s -> enc().boss.getHealth());
        check(k1 < k0, "hit the staggered kraken (" + k0 + " → " + k1 + ")");
        shot(ctx, "kr_stagger_hit");
        ctx.waitTicks(40);
        check(server.computeOnServer(s -> enc().props.isEmpty()), "fallen tentacles cleared");
        inPlace(server, "after the tentacles");
    }

    /** 해일: 서쪽 거품 벽 · 기둥 뒤 노란 자리 (하늘에서) → 기둥 뒤로 → 3 2 1 → 물결이 가로지름 → 기둥 뒤에서 버팀 */
    private void tide(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        long t0 = server.computeOnServer(s -> {
            DoubleSupplier prev = BossKit.rand;
            BossKit.rand = () -> 0.0; // 8방향 중 0 = 동쪽으로 가는 물결 (서쪽에서 옴)
            try {
                BossKraken.tide(enc(), Tasks.now());
            } finally {
                BossKit.rand = prev;
            }
            return Tasks.now();
        });
        int windup = (int) Arena.BOSSES.obj(KEY).obj("tide").num("windup");
        sky(ctx, server, 12, 19, 13, -6, 0, 0);
        waitUntil(ctx, server, t0 + 80);
        shot(ctx, "kr_tide_warn");
        survival(server);
        // 북서쪽이 아닌 남서쪽 기둥 (가운데에서 서쪽 7 · 남쪽 7) 의 동쪽 3칸 = 그림자
        chase(ctx, server, -4, 7, -14, 2, 7, 6);
        server.runCommand("title @a clear");
        waitUntil(ctx, server, t0 + windup - 38);
        shot(ctx, "kr_tide_count");
        waitUntil(ctx, server, t0 + windup + 26);
        shot(ctx, "kr_tide_wave");
        waitUntil(ctx, server, t0 + windup + 40);
        shot(ctx, "kr_tide_safe");
        String r = server.computeOnServer(s -> {
            ServerPlayer p = player(s);
            BossKraken.Tide T = BossKraken.mem(enc()).tide;
            boolean passed = T == null || T.hit.contains(p.getUUID());
            return p.isAlive() && !p.isDeadOrDying() && passed ? "ok" : "alive " + p.isAlive() + " passed " + passed;
        });
        check("ok".equals(r), "behind the pillar: the wave passes, the player survives (" + r + ")");
        waitUntil(ctx, server, t0 + windup + 95);
        inPlace(server, "after the tide");
    }

    /** 높은 곳: 기둥 위에 서면 촉수가 끌어내림 (가운데 쪽으로 밀어 떨어뜨림) */
    private void high(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 6);
        // 남동쪽 기둥 (가운데에서 동쪽 7 · 남쪽 7, 3×3 높이 6) 의 바깥 모서리 위
        look(ctx, server, cx + 8, ALTAR_Y + 6, cz + 8, cx, ALTAR_Y + 2, cz);
        long[] at = {-1};
        for (int i = 0; i < 40 && at[0] < 0; i++) {
            ctx.waitTick();
            at[0] = server.computeOnServer(s -> {
                Long t = BossKraken.mem(enc()).high.get(player(s).getUUID());
                return t == null ? -1L : t;
            });
        }
        check(at[0] > 0, "standing on a pillar → a tentacle pulls the player down");
        ctx.waitTicks(3);
        shot(ctx, "kr_high");
        ctx.waitTicks(20);
        check(server.computeOnServer(s -> player(s).getY() < ALTAR_Y + 2), "pulled off the pillar");
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑 보라 원 + 먹물 포탄 → 실명 */
    private void far(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        chase(ctx, server, -3, 15, 0, 3, 0, 5);
        server.runOnServer(s -> Bosses.farThrow(enc(), enc(), Tasks.now(), List.of(player(s))));
        ctx.waitTicks(16);
        shot(ctx, "kr_far");
        ctx.waitTicks(14);
        check(server.computeOnServer(s -> enc().farShots.isEmpty() && player(s).hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)), "ink bomb landed (blindness)");
        server.runCommand("effect clear @a minecraft:blindness");
        ctx.waitTicks(1);
        shot(ctx, "kr_far_hit");
        ctx.waitTicks(10);
    }

    /** 처치 (진짜 클릭): 처치 글자 · 보상 채팅 (보스 재료) → 가방 */
    private void kill(ClientGameTestContext ctx, TestServerContext server) {
        refresh(server);
        inPlace(server, "before the kill");
        eye(ctx, server, 0, 0, 4.4, 0, 1.8, 0);
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
            if (altar.cooldownUntil <= System.currentTimeMillis() + 890_000) return "no 15 min cooldown";
            int em = RpgGearTests.countIn(player(s), Items.EMERALD);
            int ink = RpgGearTests.countIn(player(s), GearItems.byId("rpg:kraken_ink"));
            int eye = RpgGearTests.countIn(player(s), GearItems.byId("rpg:kraken_eye"));
            return em >= 16 && ink >= 2 && eye >= 2 ? "ok " + em + " · ink " + ink + " · eye " + eye : "emeralds " + em + " ink " + ink + " eye " + eye;
        });
        check(r.startsWith("ok"), "kraken killed → rewards + boss materials + 15 min cooldown (" + r + ")");
        shot(ctx, "kr_kill");
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
        shot(ctx, "kr_loot");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }
}
