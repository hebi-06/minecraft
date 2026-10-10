package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossScarecrow;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Status;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
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
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=boss): 평평한 세계에 허수아비 킹 신전을 짓고 (제단 + Arena.buildArena) 실제로 싸움 —
 * 제단을 눌러 도전 창 → 도전 (진짜 클릭) → 소환진 · 큰 글자 → 등장 (보스 막대 · 이름표), 평타 (붉은 부채꼴 예고 → 베기), 플레이어 평타 (진짜 클릭: 번쩍임 ·
 * 피해 숫자 · 근접 보너스 »), 까마귀 떼 (보라 선 → 까마귀), 호박 폭탄 (주황 원 → 폭발), 짚단 감옥 (금빛 원 → 감옥 · 속박 → 안에서 클릭으로 부숨),
 * 분신 (넷 · 진짜 어깨의 까마귀 → 진짜를 때려 비틀거림), 수확의 낫 (붉은 테 · 토템 금빛 원 · 카운트다운 → 낫 한 바퀴), 원거리 견제 (발밑 원 · 불붙은 짚단 → 불),
 * 쓰러뜨림 (처치 글자 · 보상 채팅 · 가방). 패턴은 모듈 함수를 바로 부르고 그 사이 보스가 스스로 고르지 않게 멈춤 (BossScarecrow.M 의 다음 틱을 멀리).
 * 사진 이름 bs_* → ci-logs 의 shots/. 확인 항목은 [boss-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgBossGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-boss-gallery");
    private static final String KEY = "scarecrow_king";
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
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("boss")) return;
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
            playerHit(ctx, server);
            crows(ctx, server);
            bombs(ctx, server);
            prison(ctx, server);
            decoys(ctx, server);
            reaping(ctx, server);
            far(ctx, server);
            kill(ctx, server);
        } finally {
            LOG.info("[boss-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        }
        if (!failures.isEmpty()) throw new AssertionError("boss gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도우미

    private void ok(String what) {
        LOG.info("[boss-gallery] OK   {}", what);
    }

    private void fail(String what) {
        LOG.warn("[boss-gallery] FAIL {}", what);
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

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(960, 540));
    }

    private static void waitChunks(ClientGameTestContext ctx, TestSingleplayerContext world, int timeout) {
        try {
            world.getConnection().waitForChunksRender(true, timeout);
        } catch (AssertionError e) {
            LOG.warn("[boss-gallery] chunks not all rendered in {} ticks — going on", timeout);
        }
    }

    /** 보스 자리 (서버) */
    private Vec3 bossAt(TestServerContext server) {
        return server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            return e != null && e.boss != null ? e.boss.position() : new Vec3(cx, ALTAR_Y + 1, cz);
        });
    }

    /** 보스를 가운데 (제단 위) 로 돌려 세우고 멈춤 (패턴이 풀어 둔 것을 다시) — 바라볼 곳 face */
    private void settle(TestServerContext server, Vec3 face) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return;
            Bosses.hold(e, true);
            Vec3 home = new Vec3(cx, ALTAR_Y + 1, cz);
            if (BossKit.hdist(e.boss.position(), home) > 0.6 || face != null) BossKit.teleport(e.boss, home, face);
        });
    }

    /** 보스가 스스로 패턴 · 평타 · 원거리 견제를 고르지 않게 (사진첩이 차례로 부름) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossScarecrow.M m = BossScarecrow.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.decoyDone.add(0.6);
            m.decoyDone.add(0.25);
            m.reapAt = FAR_FUTURE;
            e.meleeAt = FAR_FUTURE;
            e.farAt = FAR_FUTURE;
            Bosses.hold(e, true);
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

    /** 보스 몸 가운데 (가슴) 를 보는 눈높이 자리: 보스에게서 (dx, dz) */
    private void faceBoss(ClientGameTestContext ctx, TestServerContext server, double dx, double dz, double chest) {
        Vec3 b = bossAt(server);
        look(ctx, server, b.x + dx, ALTAR_Y, b.z + dz, b.x, b.y + chest, b.z);
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
                ok("scarecrow temple built");
                return true;
            }
        }
        fail("temple not built in 3 min");
        return false;
    }

    /** 싸울 플레이어: 서바이벌 · 저항 V (사진을 찍는 동안 쓰러지지 않게 — 즉사 기믹은 토템 원 안에서) · 레벨 · 스탯 · 칼 */
    private void prepare(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("effect give @a minecraft:resistance infinite 4 true");
        server.runCommand("xp set @a 40 levels");
        ctx.waitTicks(5);
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l10_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
    }

    // ------------------------------------------------------------ 장면

    /** 제단 누르기 (진짜 오른쪽 클릭) → 도전 창 → 도전 (진짜 클릭) → 소환진 · 큰 글자 → 등장 */
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
        check(body.contains("Lv.10") && body.contains("근접 보너스") && body.contains("불붙은 짚단") && body.contains("10분"), "challenge text (level · close bonus · far shot · cooldown)");
        shot(ctx, "bs_form");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(0));
        boolean summoning = server.computeOnServer(s -> enc() != null && "summon".equals(enc().state));
        check(summoning, "도전 → summoning");
        if (!summoning) return false;
        // 소환진 (3초 동안 차오름) + 큰 글자
        look(ctx, server, cx, ALTAR_Y, cz + 10, cx, ALTAR_Y + 1.2, cz);
        ctx.waitTicks(22);
        shot(ctx, "bs_summon");
        for (int t = 0; t < 40 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        ctx.waitTicks(8);
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (e == null || e.boss == null) return "no boss";
            return e.boss.getHealth() == 7500F && e.boss.getMaxHealth() == 7500F ? "ok" : "hp " + e.boss.getHealth();
        });
        check("ok".equals(r), "boss appeared with 7500 hp (" + r + ")");
        int id = server.computeOnServer(s -> enc() != null && enc().boss != null ? enc().boss.getId() : -1);
        String rend = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            return mc.getEntityRenderDispatcher().getRenderer(e) instanceof BedrockRenderer ? "ok" : "renderer " + mc.getEntityRenderDispatcher().getRenderer(e);
        });
        check("ok".equals(rend), "boss drawn by BedrockRenderer (" + rend + ")");
        shot(ctx, "bs_appear");
        freeze(server);
        return true;
    }

    /** 평타: 붉은 부채꼴 예고 (0.8초) → 낫 베기 */
    private void melee(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, new Vec3(cx, ALTAR_Y + 1, cz + 5));
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        faceBoss(ctx, server, 0, 5, 2.5);
        boolean started = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            e.meleeAt = 0;
            boolean r = Bosses.melee(e, Tasks.now(), BossKit.fighters(e));
            e.meleeAt = FAR_FUTURE;
            return r && e.cast != null && "melee".equals(e.cast.id);
        });
        check(started, "melee (scythe) cast on the player in reach");
        ctx.waitTicks(9);
        shot(ctx, "bs_melee");
        ctx.waitTicks(8);
        shot(ctx, "bs_melee_hit");
        ctx.waitTicks(10);
    }

    /** 플레이어 평타 (진짜 클릭): 번쩍임 · 피해 숫자 (가슴 높이) · 근접 보너스 » */
    private void playerHit(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, new Vec3(cx, ALTAR_Y + 1, cz + 3));
        camera(ctx, CameraType.FIRST_PERSON);
        faceBoss(ctx, server, 0, 2.8, 3.0);
        float hp0 = server.computeOnServer(s -> enc().boss.getHealth());
        ctx.waitTicks(25); // 공격 충전
        attack(ctx);
        int id = server.computeOnServer(s -> enc().boss.getId());
        boolean flash = false;
        for (int i = 0; i < 6 && !flash; i++) {
            ctx.waitTick();
            flash = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof BedrockMob m && m.propBool("rpg:flash"));
        }
        float hp1 = server.computeOnServer(s -> enc().boss.getHealth());
        check(hp1 < hp0, "player melee hits the boss (" + hp0 + " → " + hp1 + ")");
        check(flash, "boss flashes red on the client (rpg:flash)");
        String num = server.computeOnServer(s -> {
            BedrockMob b = enc().boss;
            for (Display.TextDisplay td : level(s).getEntitiesOfClass(Display.TextDisplay.class, new AABB(b.position(), b.position()).inflate(5),
                    t -> t.entityTags().contains(DamageNumbers.TAG))) {
                String t = td.getText().getString();
                if (t.contains("»")) return t;
            }
            return null;
        });
        check(num != null, "damage number with the close bonus mark » (" + num + ")");
        ctx.waitTicks(1);
        shot(ctx, "bs_hit");
        ctx.waitTicks(15);
    }

    /** 까마귀 떼: 보라 선 예고 (1.3초) → 선을 따라 까마귀 */
    private void crows(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, new Vec3(cx + 5, ALTAR_Y + 1, cz + 7));
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        faceBoss(ctx, server, 5, 7, 2.0);
        server.runOnServer(s -> BossScarecrow.crows(enc(), Tasks.now(), BossKit.fighters(enc())));
        check(server.computeOnServer(s -> enc().cast != null && "crows".equals(enc().cast.id)), "crows cast");
        ctx.waitTicks(14);
        shot(ctx, "bs_crows_tele");
        ctx.waitTicks(13);
        server.runCommand("effect clear @a minecraft:blindness");
        ctx.waitTicks(2);
        shot(ctx, "bs_crows");
        ctx.waitTicks(12);
        server.runCommand("effect clear @a minecraft:blindness");
    }

    /** 호박 폭탄: 호박 + 주황 원 (2.5초) → 폭발 */
    private void bombs(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, new Vec3(cx - 5, ALTAR_Y + 1, cz + 6));
        faceBoss(ctx, server, -5, 6, 2.0);
        server.runOnServer(s -> BossScarecrow.bombs(enc(), Tasks.now(), BossKit.fighters(enc())));
        long n = server.computeOnServer(s -> enc().props.stream().filter(e -> Kit.isType(e, BossScarecrow.BOMB)).count());
        check(n == 5, "5 pumpkin bombs (" + n + ")");
        ctx.waitTicks(25);
        shot(ctx, "bs_bombs");
        // 원 밖으로 비켜서 터지는 것을 봄
        Vec3 b = bossAt(server);
        look(ctx, server, b.x - 9, ALTAR_Y, b.z + 9, b.x - 4, ALTAR_Y + 0.5, b.z + 4);
        ctx.waitTicks(23);
        shot(ctx, "bs_bombs_boom");
        long left = server.computeOnServer(s -> enc().props.stream().filter(e -> Kit.isType(e, BossScarecrow.BOMB)).count());
        check(left == 0, "bombs exploded (" + left + " left)");
        ctx.waitTicks(10);
    }

    /** 짚단 감옥: 금빛 원이 따라다님 → 감옥 + 속박 → 안에서 클릭해 부숨 (맞는 상자 1.6×2.6) */
    private void prison(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, new Vec3(cx, ALTAR_Y + 1, cz + 11));
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        look(ctx, server, cx, ALTAR_Y, cz + 11, cx, ALTAR_Y + 1, cz);
        server.runOnServer(s -> BossScarecrow.prison(enc(), Tasks.now(), BossKit.fighters(enc())));
        ctx.waitTicks(28);
        boolean caged = server.computeOnServer(s -> BossScarecrow.mem(enc()).cage != null && Status.isRooted(player(s)));
        check(caged, "straw cage + rooted");
        shot(ctx, "bs_prison");
        // 1인칭으로 감옥 안에서 앞을 보고 때림 (조준 광선이 감옥 상자 안에서 시작)
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.getInput().lookAt(180F, 10F);
        boolean freed = false;
        float[] hp = {60F};
        for (int i = 0; i < 4 && !freed; i++) {
            ctx.waitTicks(14);
            attack(ctx);
            ctx.waitTicks(3);
            freed = server.computeOnServer(s -> {
                BossScarecrow.M m = BossScarecrow.mem(enc());
                if (m.cage != null && m.cage.e instanceof BedrockMob c) hp[0] = c.getHealth();
                return m.cage == null && !Status.isRooted(player(s));
            });
        }
        check(freed, "clicking inside breaks the cage → freed (cage hp " + hp[0] + ")");
        if (!freed) {
            server.runOnServer(s -> {
                BossScarecrow.M m = BossScarecrow.mem(enc());
                if (m.cage != null) m.cage.e.discard();
            });
            ctx.waitTicks(3);
        }
        ctx.waitTicks(10);
    }

    /** 분신: 허수아비 넷 (진짜는 어깨에 까마귀 · 날갯짓) → 진짜를 때려 비틀거림 */
    private void decoys(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, null);
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            e.boss.setHealth(7500 * 0.59F);
            BossScarecrow.decoys(e, Tasks.now());
        });
        server.runCommand("gamemode spectator @a");
        look(ctx, server, cx + 13, ALTAR_Y + 11, cz + 13, cx, ALTAR_Y + 1, cz);
        ctx.waitTicks(20);
        int n = server.computeOnServer(s -> {
            BossScarecrow.Decoys d = BossScarecrow.mem(enc()).decoys;
            return d == null ? -1 : d.props.size();
        });
        check(n == 3, "3 decoys + the real one (" + n + ")");
        shot(ctx, "bs_decoys");
        // 진짜의 어깨 까마귀 날갯짓 (나타나고 1초 뒤)
        Vec3 real = server.computeOnServer(s -> enc().boss.position());
        Vec3 toC = new Vec3(cx - real.x, 0, cz - real.z).normalize();
        look(ctx, server, real.x + toC.x * 6, real.y + 2.5, real.z + toC.z * 6, real.x, real.y + 5, real.z);
        ctx.waitTicks(10);
        shot(ctx, "bs_decoys_tell");
        // 진짜를 때림 (진짜 클릭)
        server.runCommand("gamemode survival @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, real.x + toC.x * 2.8, ALTAR_Y, real.z + toC.z * 2.8, real.x, ALTAR_Y + 3, real.z);
        ctx.waitTicks(20);
        attack(ctx);
        ctx.waitTicks(2);
        boolean found = server.computeOnServer(s -> BossScarecrow.mem(enc()).decoys == null && enc().staggerUntil > Tasks.now());
        if (!found) {
            fail("clicking the real one did not find it — hitting it from the server");
            server.runOnServer(s -> enc().boss.hurtServer(level(s), player(s).damageSources().playerAttack(player(s)), 50F));
        } else ok("hitting the real scarecrow king ends the decoys → stagger");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        look(ctx, server, real.x + toC.x * 7, ALTAR_Y, real.z + toC.z * 7, real.x, real.y + 3, real.z);
        ctx.waitTicks(6);
        shot(ctx, "bs_stagger");
        // 비틀거림이 끝날 때까지
        ctx.waitTicks(100);
    }

    /** 수확의 낫: 가운데서 낫을 들어 올림 · 붉은 테 · 토템 금빛 원 · 5 4 3 2 1 → 낫 한 바퀴 (토템 원 안이라 살아남음) */
    private void reaping(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, null);
        Vec3 t = server.computeOnServer(s -> enc().feat.totems().get(1));
        Vec3 in = new Vec3(cx - t.x, 0, cz - t.z).normalize();
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        look(ctx, server, t.x + in.x * 1.4, ALTAR_Y, t.z + in.z * 1.4, cx, ALTAR_Y + 2, cz);
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            e.boss.setHealth(7500 * 0.39F);
            BossScarecrow.reaping(e, Tasks.now());
        });
        check(server.computeOnServer(s -> enc().cast != null && "reaping".equals(enc().cast.id)), "reaping cast");
        ctx.waitTicks(42);
        shot(ctx, "bs_reaping");
        // 높이에서 본 토템 넷의 금빛 원
        server.runCommand("gamemode spectator @a");
        look(ctx, server, cx + 0.01, ALTAR_Y + 26, cz + 6, cx, ALTAR_Y, cz);
        ctx.waitTicks(8);
        shot(ctx, "bs_reaping_top");
        server.runCommand("gamemode survival @a");
        look(ctx, server, t.x + in.x * 1.4, ALTAR_Y, t.z + in.z * 1.4, cx, ALTAR_Y + 2, cz);
        ctx.waitTicks(52);
        shot(ctx, "bs_reaping_judge");
        boolean alive = server.computeOnServer(s -> player(s).isAlive() && !player(s).isDeadOrDying());
        check(alive, "player inside a totem circle survives the reaping");
        ctx.waitTicks(25);
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑 주황 원 + 불붙은 짚단이 포물선으로 → 불 */
    private void far(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, new Vec3(cx, ALTAR_Y + 1, cz + 13));
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        look(ctx, server, cx, ALTAR_Y, cz + 13.5, cx, ALTAR_Y + 3, cz);
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            Bosses.farThrow(e, e, Tasks.now(), List.of(player(s)));
        });
        ctx.waitTicks(16);
        shot(ctx, "bs_far");
        ctx.waitTicks(15);
        shot(ctx, "bs_far_hit");
        boolean landed = server.computeOnServer(s -> enc().farShots.isEmpty() && player(s).getRemainingFireTicks() > 0);
        check(landed, "far shot (burning straw) landed — player burning");
        server.runCommand("effect give @a minecraft:fire_resistance 30 0 true");
        ctx.waitTicks(10);
    }

    /** 쓰러뜨림 (진짜 클릭): 처치 글자 · 보상 채팅 · 서버 알림 → 가방 */
    private void kill(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, new Vec3(cx, ALTAR_Y + 1, cz + 3));
        camera(ctx, CameraType.FIRST_PERSON);
        faceBoss(ctx, server, 0, 2.8, 3.0);
        server.runOnServer(s -> enc().boss.setHealth(5F));
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(10);
        String r = server.computeOnServer(s -> {
            if (Bosses.encounterAt(altar) != null) return "still fighting";
            if (altar.cooldownUntil <= System.currentTimeMillis()) return "no cooldown";
            int em = RpgGearTests.countIn(player(s), Items.EMERALD);
            return em >= 8 ? "ok " + em : "emeralds " + em;
        });
        check(r.startsWith("ok"), "boss killed → rewards + cooldown (" + r + ")");
        shot(ctx, "bs_kill");
        ctx.waitTicks(30);
        ctx.getInput().pressKey(options -> options.keyInventory);
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen, 40);
        } catch (AssertionError | RuntimeException e) {
            fail("inventory did not open");
        }
        ctx.waitTicks(5);
        shot(ctx, "bs_loot");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
    }
}
