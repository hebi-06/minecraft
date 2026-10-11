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
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.mob.BedrockMob;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 걷고 나는 · 뛰는 보스 (drive — 니드호그 · 스콜과 하티) 사진첩의 바탕: 평평한 세계에 그 보스의 진짜 신전을 짓고 (제단 + Arena.buildArena),
 * 제단을 진짜로 눌러 도전 창 → 도전 (진짜 클릭) → 소환진. 장면은 하위 클래스 (scenes). 사진첩 본보기 RpgBossGallery 의 도우미 (카메라 · 클릭 · 사진) 그대로.
 * 확인 항목은 [&lt;키&gt;-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
abstract class RpgDriveGallery implements FabricClientGameTest {
    /** 제단 높이 (= 신전 안에서 서는 높이 — 평평한 세계의 땅 -60 에서 메워 올림) */
    static final int ALTAR_Y = -55;
    static final int GROUND = -60;
    static final long FAR_FUTURE = Long.MAX_VALUE / 4;

    final List<String> failures = new ArrayList<>();
    Arena.Altar altar;
    double cx;
    double cz;

    /** 보스 키 (BOSSES) */
    abstract String key();

    /** 사진첩 키 (RpgProdSmoke.only) */
    abstract String only();

    abstract Logger log();

    /** 도전 창 글에 있어야 하는 것 */
    abstract List<String> formWords();

    /** 사진 이름 앞 (nh · sk) */
    abstract String prefix();

    /** 소환진 뒤의 장면들 (보스는 소환 중) */
    abstract void scenes(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only(only())) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(20);
            if (!build(ctx, server, world)) return;
            prepare(ctx, server);
            if (!challenge(ctx, server, conn)) return;
            scenes(ctx, server, conn);
        } catch (RuntimeException | AssertionError e) {
            fail("gallery stopped: " + e);
            log().warn("[{}-gallery] stopped", only(), e);
        } finally {
            log().info("[{}-gallery] done, {} failed checks{}", only(), failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError(only() + " gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 확인 · 사진

    void ok(String what) {
        log().info("[{}-gallery] OK   {}", only(), what);
    }

    void fail(String what) {
        log().warn("[{}-gallery] FAIL {}", only(), what);
        failures.add(what);
    }

    void check(boolean good, String what) {
        if (good) ok(what);
        else fail(what);
    }

    /** 사진 — 창 크기 그대로 (큰 글자 · 창 · 가방이 잘리지 않게), 알림 토스트는 치움. 이름 = 앞 + "_" + 장면 */
    void shot(ClientGameTestContext ctx, String scene) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(prefix() + "_" + scene);
    }

    // ------------------------------------------------------------ 서버 · 클라이언트

    static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    static ServerLevel level(MinecraftServer s) {
        return player(s).level();
    }

    Bosses.Enc enc() {
        return altar == null ? null : Bosses.encounterAt(altar);
    }

    static long now(TestServerContext server) {
        return server.computeOnServer(s -> Tasks.now());
    }

    /** 서버 틱이 at 이 될 때까지 (패턴의 판정 순간을 찍으려고) */
    static void waitUntil(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 600 && server.computeOnServer(s -> Tasks.now()) < at; i++) ctx.waitTick();
    }

    /** 보스 엔티티 번호 (없으면 -1) */
    int bossId(TestServerContext server) {
        return server.computeOnServer(s -> enc() != null && enc().boss != null ? enc().boss.getId() : -1);
    }

    /** 클라이언트의 보스 (없으면 null) */
    static BedrockMob clientBoss(ClientGameTestContext ctx, int id) {
        return ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof BedrockMob m ? m : null);
    }

    /** 클라이언트에서 본 보스의 몸 방향 (자바 각도) */
    static float clientBodyYaw(ClientGameTestContext ctx, int id) {
        return ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof LivingEntity le ? le.yBodyRot : Float.NaN);
    }

    /** 클라이언트에서 본 보스 자리 */
    static Vec3 clientPos(ClientGameTestContext ctx, int id) {
        return ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof Entity e ? e.position() : null);
    }

    /** 클라이언트 속성 (서버가 보낸 rpg:… — 그리기가 읽는 값) */
    static double clientProp(ClientGameTestContext ctx, int id, String prop) {
        return ctx.computeOnClient(mc -> mc.level != null && mc.level.getEntity(id) instanceof BedrockMob m ? m.prop(prop) : Double.NaN);
    }

    /** 각도 차이 (도, 0 ~ 180) */
    static double degDiff(double a, double b) {
        double d = ((a - b) % 360 + 540) % 360 - 180;
        return Math.abs(d);
    }

    // ------------------------------------------------------------ 카메라

    /** 카메라 (플레이어) 를 (px, py, pz) 에 두고 (tx, ty, tz) 를 봄 — py = 발 높이 */
    static void look(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz) {
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

    static void camera(ClientGameTestContext ctx, CameraType type) {
        ctx.runOnClient(mc -> mc.options.setCameraType(type));
    }

    /** 3인칭 카메라 거리 (속성 CAMERA_DISTANCE, 기본 4 — 서버 · 클라이언트 둘 다) */
    static void camDist(ClientGameTestContext ctx, TestServerContext server, double d) {
        server.runOnServer(s -> {
            AttributeInstance a = player(s).getAttribute(Attributes.CAMERA_DISTANCE);
            if (a != null) a.setBaseValue(d);
        });
        ctx.runOnClient(mc -> {
            AttributeInstance a = mc.player == null ? null : mc.player.getAttribute(Attributes.CAMERA_DISTANCE);
            if (a != null) a.setBaseValue(d);
        });
    }

    /** 뒤 3인칭 사진 자리: 플레이어를 (px, py, pz) 에 세우고 (tx, ty, tz) 를 보게 — 카메라는 시선 뒤로 dist 칸 */
    void chase(ClientGameTestContext ctx, TestServerContext server, double px, double py, double pz, double tx, double ty, double tz, double dist) {
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, dist);
        look(ctx, server, px, py, pz, tx, ty, tz);
        ctx.waitTick();
        double got = ctx.computeOnClient(mc -> mc.player == null ? -1.0 : mc.gameRenderer.mainCamera().position().distanceTo(mc.player.getEyePosition()));
        if (got < dist - 0.5) log().warn("[{}-gallery] camera pulled in: {} of {} blocks", only(), String.format(Locale.ROOT, "%.2f", got), dist);
    }

    void chase(ClientGameTestContext ctx, TestServerContext server, double px, double pz, double tx, double ty, double tz, double dist) {
        chase(ctx, server, px, ALTAR_Y, pz, tx, ty, tz, dist);
    }

    /** 패턴의 무작위 (BossKit.rand) 를 정한 수열로 돌려 run 을 부르고 되돌림 (서버 스레드에서) */
    static void withRand(double[] seq, Runnable run) {
        DoubleSupplier prev = BossKit.rand;
        int[] i = {0};
        BossKit.rand = () -> seq[i[0]++ % seq.length];
        try {
            run.run();
        } finally {
            BossKit.rand = prev;
        }
    }

    static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
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
    static void attack(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(options -> options.keyAttack);
    }

    static void waitChunks(TestSingleplayerContext world, int timeout) {
        try {
            world.getConnection().waitForChunksRender(true, timeout);
        } catch (AssertionError e) {
            // 다 그려지지 않아도 계속
        }
    }

    /** 보스 둘레의 피해 숫자 글 (서버) */
    List<String> numbersNear(TestServerContext server, double r) {
        return server.computeOnServer(s -> {
            List<String> out = new ArrayList<>();
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return out;
            BedrockMob b = e.boss;
            for (Display.TextDisplay td : level(s).getEntitiesOfClass(Display.TextDisplay.class, new AABB(b.position(), b.position()).inflate(r),
                    t -> t.entityTags().contains(DamageNumbers.TAG))) {
                out.add(td.getText().getString() + String.format(Locale.ROOT, " @%.2f", BossKit.hdist(td.position(), b.position())));
            }
            return out;
        });
    }

    // ------------------------------------------------------------ 신전 짓기 · 준비 · 도전

    boolean build(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world) {
        int R = (int) Arena.BOSSES.obj(key()).obj("arena").num("radius");
        int x = 0;
        int z = 0;
        cx = x + 0.5;
        cz = z + 0.5;
        server.runCommand("gamemode spectator @a");
        look(ctx, server, cx, ALTAR_Y + 34, cz + 0.01, cx, ALTAR_Y, cz);
        waitChunks(world, 1200);
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
            level.setBlock(pos, AltarBlocks.block(key()).defaultBlockState(), Block.UPDATE_ALL);
            altar = Arena.addAltar(key(), NpcData.dimId(level), pos);
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
                ok(key() + " temple built");
                waitChunks(world, 400);
                shot(ctx, "temple");
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /** 싸울 플레이어: 서바이벌 · 저항 V (사진을 찍는 동안 쓰러지지 않게) · 레벨 · 스탯 · 칼 */
    void prepare(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 4 true");
        server.runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
        server.runCommand("xp set @a 40 levels");
        ctx.waitTicks(5);
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l10_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
    }

    /** 제단 누르기 (진짜 오른쪽 클릭) → 도전 창 → 도전 (진짜 클릭) → 소환진 (사진 summon) */
    boolean challenge(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
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
        boolean all = true;
        for (String w : formWords()) all &= body.contains(w);
        check(all, "challenge text " + formWords());
        shot(ctx, "form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state));
        check(summoning, "도전 → summoning");
        if (!summoning) return false;
        look(ctx, server, cx, ALTAR_Y, cz + 12, cx, ALTAR_Y + 1.2, cz);
        ctx.waitTicks(22);
        shot(ctx, "summon");
        return true;
    }

    /** 보스가 BedrockRenderer 로 그려지는지 */
    void checkDrawn(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        check("ok".equals(rend), "boss drawn by BedrockRenderer (" + rend + ")");
    }

    /** 쓰러뜨림 (진짜 클릭) → 처치 글자 · 보상 · 재사용 대기 → 가방. (px, pz) 에서 보스 (bx, by, bz) 를 봄 */
    void kill(ClientGameTestContext ctx, TestServerContext server, double px, double pz, double bx, double by, double bz, int minEmerald) {
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, px, ALTAR_Y, pz, bx, by, bz);
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
            if (altar.cooldownUntil <= System.currentTimeMillis() + 890_000) return "no 15 min cooldown";
            int em = RpgGearTests.countIn(player(s), Items.EMERALD);
            return em >= minEmerald ? "ok " + em : "emeralds " + em;
        });
        check(r.startsWith("ok"), "boss killed → rewards + cooldown (" + r + ")");
        shot(ctx, "kill");
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
        shot(ctx, "loot");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }
}
