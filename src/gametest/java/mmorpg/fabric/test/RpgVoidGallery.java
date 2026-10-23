package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.mob.RpgMonster;
import mmorpg.fabric.mob.VoidMonsters;
import mmorpg.fabric.shop.Shop;
import mmorpg.fabric.ui.FormNet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.32.0 공허 몬스터 사진첩 (화면 시험, only=void — 실제 공격 입력으로 먼저 때림): 다섯이 한 줄로 선 모습 (낮 · 밤 — 빛나는 무늬) · 하나씩 가까이,
 * 균열이 열리며 나타남, 도약충 덮치기 (웅크림 · 분홍 원 → 공중 → 떨어짐) · 무리 울음, 추적자 균열 이동 (사라짐 → 등 뒤 균열 · 경고 원 → 십자 베기 · 어둠),
 * 해파리 공허 구체 (모음 → 날아옴 → 터짐), 굴착자 땅굴 (파고듦 → 금 간 자국 → 발밑 경고 → 솟구침), 거수 수정 가시 (일어섬 · 경고 줄 → 솟는 가시)
 * · 앞발 내려찍기 충격파, 빨려 드는 죽음, 전리품 팔기 창 (19종 — 4줄) · 가방의 공허 전리품.
 * 확인 항목은 [void-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgVoidGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-void-gallery");
    private static final double FLOOR = -60;
    /** 플레이어가 서는 자리 (북쪽 -z 를 봄) */
    private static final double PX = 0.5;
    private static final double PZ = 0.5;
    private static final List<String> VOID = RpgVoidMonsterTests.VOID;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("void")) return;
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
            server.runCommand("difficulty normal");
            server.runCommand("xp set @a 55 levels");
            ctx.waitTicks(10);
            server.runCommand("execute as @a run rpg set vit 900");
            server.runCommand("execute as @a run rpg set str 20");
            server.runCommand("execute as @a run rpg set dex 20");
            server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l0_common");
            server.runCommand("item replace entity @a hotbar.1 with rpg:bow_l0_common");
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            server.runCommand("fill -14 -61 -18 14 -61 8 minecraft:deepslate_tiles");
            teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
            ctx.waitTicks(70); // 접속 직후 무적 (3초) 이 지나게
            part(ctx, server, "lineup", () -> lineup(ctx, server));
            part(ctx, server, "close", () -> closeups(ctx, server));
            part(ctx, server, "spawn", () -> spawnRift(ctx, server));
            part(ctx, server, "leap", () -> leap(ctx, server));
            part(ctx, server, "swarm", () -> swarm(ctx, server));
            part(ctx, server, "blink", () -> blink(ctx, server));
            part(ctx, server, "orb", () -> orb(ctx, server));
            part(ctx, server, "tunnel", () -> tunnel(ctx, server));
            part(ctx, server, "spikes", () -> spikes(ctx, server));
            part(ctx, server, "shock", () -> shock(ctx, server));
            part(ctx, server, "death", () -> death(ctx, server));
            part(ctx, server, "sell", () -> sell(ctx, server, conn));
        }
        LOG.info("[void-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("void gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도움

    private void check(boolean ok, String what) {
        LOG.info("[void-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    /** 장면 하나 (실패해도 다음 장면으로): 끝나면 몬스터 · 효과 · 카메라 · 창 · 바닥을 되돌림 */
    private void part(ClientGameTestContext ctx, TestServerContext server, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[void-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[void-gallery] part " + name, e);
        }
        try {
            ctx.setScreen(() -> null);
            server.runOnServer(s -> {
                for (RpgMonster m : level(s).getEntitiesOfClass(RpgMonster.class, new AABB(PX, FLOOR, PZ, PX, FLOOR, PZ).inflate(48))) m.discard();
                Monsters.clearState();
                RpgHealth.healFull(player(s));
            });
            server.runCommand("effect clear @a");
            server.runCommand("time set 6000");
            server.runCommand("fill -14 -61 -18 14 -61 8 minecraft:deepslate_tiles");
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                mc.options.fov().set(70);
            });
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
            ctx.waitTicks(6);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[void-gallery] reset after " + name, e);
        }
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static ServerLevel level(MinecraftServer s) {
        return player(s).level();
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(960, 540));
    }

    /** 창이 열린 장면은 창 크기 그대로 */
    private static void shotFull(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot(name);
    }

    private static void teleport(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, float yaw, float pitch) {
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        ctx.waitTicks(3);
    }

    /** 몬스터를 세움 (yaw 쪽을 봄, ai = false 면 제자리) — 엔티티 번호 */
    private static int spawn(TestServerContext server, String key, double x, double y, double z, float yaw, boolean ai) {
        return server.computeOnServer(s -> {
            RpgMonster m = Monsters.spawnAt(level(s), key, new Vec3(x, y, z), yaw);
            if (m == null) return -1;
            m.setNoAi(!ai);
            return m.getId();
        });
    }

    private static RpgMonster monster(MinecraftServer s, int id) {
        Entity e = level(s).getEntity(id);
        return e instanceof RpgMonster m ? m : null;
    }

    private static Monsters.Fight fight(MinecraftServer s, int id) {
        return Monsters.fightOf(monster(s, id));
    }

    private static String phase(MinecraftServer s, int id) {
        return VoidMonsters.runPhase(fight(s, id));
    }

    private static void discard(TestServerContext server, int id) {
        server.runOnServer(s -> {
            Entity e = level(s).getEntity(id);
            if (e != null) e.discard();
        });
    }

    /** 조건이 맞을 때까지 한 틱씩 (최대 max 틱). 맞았으면 true */
    private static boolean waitFor(ClientGameTestContext ctx, TestServerContext server, int max, Predicate<MinecraftServer> cond) {
        for (int i = 0; i < max; i++) {
            if (server.computeOnServer(cond::test)) return true;
            ctx.waitTicks(1);
        }
        return server.computeOnServer(cond::test);
    }

    /** 클라이언트에서 그 몬스터를 바라봄 (높이 = 발 + h) */
    private static void lookAt(ClientGameTestContext ctx, int id, double h) {
        ctx.runOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(e.getX(), e.getY() + h, e.getZ()));
        });
    }

    private static void lookAtPoint(ClientGameTestContext ctx, Vec3 at) {
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, at));
    }

    /** 실제 공격 입력 (마우스 왼쪽과 같은 공격 패킷) */
    private static void attack(ClientGameTestContext ctx, int id) {
        ctx.runOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e != null) mc.gameMode.attack(mc.player, e);
            else LOG.warn("[void-gallery] target {} missing on client", id);
        });
    }

    /** 손에 든 무기 쓰기 (마우스 오른쪽 — 활은 바라보는 대상에 명중) */
    private static void use(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
    }

    /** 활로 쏘아 싸움을 시작 (멀리 있는 몬스터) — 싸움이 시작됐으면 true */
    private boolean shoot(ClientGameTestContext ctx, TestServerContext server, int id, double h) {
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        ctx.waitTicks(4);
        lookAt(ctx, id, h);
        ctx.waitTicks(3);
        use(ctx);
        boolean fought = waitFor(ctx, server, 40, s -> fight(s, id) != null);
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        return fought;
    }

    /**
     * 3인칭 뒤 카메라를 비스듬히: 바라보던 쪽에서 yawOff 도 돌리고 pitch 도로 내려다봄 — 앞의 몬스터가 플레이어 몸에 가리지 않게
     */
    private static void sideView(ClientGameTestContext ctx, float yawOff, float pitch) {
        ctx.runOnClient(mc -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(mc.player.getYRot() + yawOff);
            mc.player.setXRot(pitch);
        });
        ctx.waitTicks(2);
    }

    private static void hideGui(ClientGameTestContext ctx, boolean hide) {
        ctx.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() != hide) mc.gui.hud.toggle();
        });
    }

    private static void fov(ClientGameTestContext ctx, int deg) {
        ctx.runOnClient(mc -> mc.options.fov().set(deg));
    }

    /** 클라이언트의 이름표 (그 몬스터에 탄 글자 표시 엔티티의 글, 없으면 null) */
    private static String clientLabel(ClientGameTestContext ctx, int id) {
        return ctx.computeOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e == null) return null;
            for (Entity p : e.getPassengers()) if (p instanceof Display.TextDisplay t) return t.getText().getString();
            return null;
        });
    }

    private static float hp(TestServerContext server) {
        return server.computeOnServer(s -> player(s).getHealth());
    }

    // ------------------------------------------------------------ 모습

    /** 다섯이 한 줄 (레벨 순서 — 이름표 · 크기 비교) 낮 · 밤 (빛나는 무늬) — 손 · HUD 없이 */
    private void lineup(ClientGameTestContext ctx, TestServerContext server) {
        hideGui(ctx, true);
        teleport(ctx, server, PX, FLOOR, PZ, 180F, -4F);
        List<Integer> ids = new ArrayList<>();
        double[] xs = {-7.4, -3.9, 0.0, 3.9, 8.0};
        for (int i = 0; i < VOID.size(); i++) ids.add(spawn(server, VOID.get(i), PX + xs[i], FLOOR, PZ - 11.5, 0F, false));
        check(ids.stream().allMatch(i -> i >= 0), "5 void monsters spawned " + ids);
        ctx.waitTicks(30);
        shot(ctx, "v_lineup");
        for (int i = 0; i < ids.size(); i++) {
            String label = clientLabel(ctx, ids.get(i));
            Monsters.Kind k = Monsters.kind(VOID.get(i));
            check(label != null && label.contains("Lv." + k.level()) && label.contains(k.name()), k.key() + ": label on client " + label);
        }
        server.runCommand("time set 18000");
        ctx.waitTicks(20);
        shot(ctx, "v_lineup_night");
        server.runCommand("time set 6000");
        hideGui(ctx, false);
    }

    /** 하나씩 가까이 (비스듬히 서서 카메라를 봄 · 3/4) — 손 · HUD 없이 */
    private void closeups(ClientGameTestContext ctx, TestServerContext server) {
        hideGui(ctx, true);
        fov(ctx, 45); // 좁은 시야 — 몸이 화면에 크게 (장면이 끝나면 70 으로)
        for (String key : VOID) {
            Monsters.Kind k = Monsters.kind(key);
            double d = Math.max(3.6, k.height() * 1.25 + k.half() + 1.4);
            teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
            int id = spawn(server, key, PX, FLOOR, PZ - d, -28F, false);
            ctx.waitTicks(14);
            lookAtPoint(ctx, new Vec3(PX, FLOOR + Math.max(0.5, k.height() * 0.45), PZ - d));
            ctx.waitTicks(4);
            shot(ctx, "v_" + key);
            server.runCommand("time set 18000");
            ctx.waitTicks(6);
            shot(ctx, "v_" + key + "_night");
            server.runCommand("time set 6000");
            discard(server, id);
            ctx.waitTicks(3);
        }
        fov(ctx, 70);
        hideGui(ctx, false);
    }

    /** 나타남: 몸 뒤에 세로 균열이 열리고 (몸은 아직 없음) → 몸이 균열에서 비집고 나옴 (좁고 작게 → 부풀며 앞으로) → 다 나옴 — 추적자 · 거수 · 도약충 (손 · HUD 없이) */
    private void spawnRift(ClientGameTestContext ctx, TestServerContext server) {
        hideGui(ctx, true);
        teleport(ctx, server, PX, FLOOR, PZ, 180F, -2F);
        int a = spawn(server, "void_stalker", PX - 2.2, FLOOR, PZ - 6.0, 15F, false);
        int b = spawn(server, "void_behemoth", PX + 2.8, FLOOR, PZ - 9.0, -20F, false);
        int c = spawn(server, "void_leaper", PX + 0.2, FLOOR, PZ - 4.2, 0F, false);
        boolean anim = server.computeOnServer(s -> {
            for (int id : List.of(a, b, c)) {
                RpgMonster m = monster(s, id);
                if (m == null || m.syncedAnims().stream().noneMatch(p -> p.anim().endsWith(".spawn"))) return false;
            }
            return true;
        });
        ctx.waitTicks(2);
        shot(ctx, "v_spawn_rift");
        ctx.waitTicks(5);
        shot(ctx, "v_spawn_rift2");
        ctx.waitTicks(4);
        shot(ctx, "v_spawn_rift3");
        ctx.waitTicks(10);
        shot(ctx, "v_spawn_rift4");
        check(a >= 0 && b >= 0 && c >= 0 && anim, "spawned from rifts (spawn animation)");
        hideGui(ctx, false);
    }

    // ------------------------------------------------------------ 특성

    /** 도약충 덮치기: 활로 쏨 → 웅크림 (내 발밑 분홍 원) → 공중 → 떨어짐 (분홍 고리) — 3인칭 비스듬히 · 1인칭 */
    private void leap(ClientGameTestContext ctx, TestServerContext server) {
        Monsters.Kind k = Monsters.kind("void_leaper");
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "void_leaper", PX, FLOOR, PZ - (k.half() + 0.3 + 6.5), 0F, false);
        ctx.waitTicks(8);
        float hp0 = hp(server);
        boolean fought = shoot(ctx, server, id, 0.4);
        check(fought, "leaper: fight after the arrow");
        sideView(ctx, -38F, 20F);
        boolean prep = waitFor(ctx, server, 60, s -> "prep".equals(phase(s, id)));
        ctx.waitTicks(6);
        shot(ctx, "v_leap_prep");
        boolean fly = waitFor(ctx, server, 20, s -> "fly".equals(phase(s, id)));
        ctx.waitTicks(7);
        shot(ctx, "v_leap_air");
        boolean landed = waitFor(ctx, server, 30, s -> fight(s, id) != null && "leap".equals(fight(s, id).lastKind()));
        ctx.waitTicks(1);
        shot(ctx, "v_leap_land");
        ctx.waitTicks(5);
        shot(ctx, "v_leap_land2");
        boolean slow = server.computeOnServer(s -> player(s).hasEffect(MobEffects.SLOWNESS));
        check(prep && fly && landed && hp(server) < hp0 && slow, "leaper: prep → fly → land on the player (slowed)");
        // 1인칭: 다시 덮칠 때 (웅크림 — 발밑 원)
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runOnServer(s -> {
            RpgMonster m = monster(s, id);
            if (m != null) {
                m.snapTo(PX, FLOOR, PZ - 7.5, 0F, 0F);
                VoidMonsters.readyTrait(fight(s, id));
            }
        });
        lookAt(ctx, id, 0.4);
        boolean again = waitFor(ctx, server, 90, s -> "prep".equals(phase(s, id)));
        ctx.waitTicks(4);
        shot(ctx, "v_leap_fp_prep");
        waitFor(ctx, server, 20, s -> "fly".equals(phase(s, id)));
        ctx.waitTicks(8);
        shot(ctx, "v_leap_fp_air");
        check(again, "leaper: leaps again");
    }

    /** 무리: 셋 중 하나를 쏘면 울고 (분홍 고리) 둘레 도약충이 덤빔 */
    private void swarm(ClientGameTestContext ctx, TestServerContext server) {
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int a = spawn(server, "void_leaper", PX, FLOOR, PZ - 6.5, 0F, false);
        int b = spawn(server, "void_leaper", PX - 3.2, FLOOR, PZ - 8.5, 10F, false);
        int c = spawn(server, "void_leaper", PX + 3.2, FLOOR, PZ - 9.0, -10F, false);
        ctx.waitTicks(10);
        boolean fought = shoot(ctx, server, a, 0.4);
        sideView(ctx, -26F, 18F);
        ctx.waitTicks(3);
        shot(ctx, "v_swarm_screech");
        ctx.waitTicks(10);
        shot(ctx, "v_swarm_join");
        int called = server.computeOnServer(s -> fight(s, a) == null ? -1 : fight(s, a).packCalled());
        boolean joined = server.computeOnServer(s -> fight(s, b) != null && fight(s, c) != null);
        check(fought && called >= 2 && joined, "swarm: screech called " + called);
    }

    /** 추적자 균열 이동: 쏨 → 균열 속으로 → 내 등 뒤에 균열 · 경고 원 (3인칭 — 둘 다 보이게) → 나와서 십자 베기 → 1인칭 어둠 */
    private void blink(ClientGameTestContext ctx, TestServerContext server) {
        Monsters.Kind k = Monsters.kind("void_stalker");
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "void_stalker", PX, FLOOR, PZ - 7.5, 0F, false);
        ctx.waitTicks(8);
        float hp0 = hp(server);
        boolean fought = shoot(ctx, server, id, 1.4);
        check(fought, "stalker: fight after the arrow");
        // 등 뒤가 보이게: 옆 (서쪽) 에서 비스듬히 — 플레이어는 북쪽을 봄, 균열은 남쪽 (+z)
        sideView(ctx, -80F, 22F);
        boolean vanish = waitFor(ctx, server, 80, s -> "vanish".equals(phase(s, id)));
        ctx.waitTicks(4);
        shot(ctx, "v_blink_vanish");
        boolean hidden = waitFor(ctx, server, 20, s -> "hidden".equals(phase(s, id)));
        ctx.waitTicks(1);
        boolean invisible = ctx.computeOnClient(mc -> mc.level.getEntity(id) != null && mc.level.getEntity(id).isInvisible());
        ctx.waitTicks(5);
        shot(ctx, "v_blink_rift");
        boolean strike = waitFor(ctx, server, 20, s -> "strike".equals(phase(s, id)));
        ctx.waitTicks(3);
        shot(ctx, "v_blink_strike");
        boolean done = waitFor(ctx, server, 12, s -> fight(s, id) != null && "blink".equals(fight(s, id).lastKind()));
        ctx.waitTicks(1);
        shot(ctx, "v_blink_strike2");
        boolean dark = server.computeOnServer(s -> player(s).hasEffect(MobEffects.DARKNESS));
        check(vanish && hidden && invisible && strike && done && hp(server) < hp0 && dark,
                "stalker: vanish (invisible on client " + invisible + ") → rift behind → cross slash · darkness");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(10);
        shot(ctx, "v_blink_dark");
    }

    /** 해파리 공허 구체: 쏨 → 모음 (핵이 커짐 · 촉수가 펼쳐짐) → 날아옴 → 터짐 — 3인칭 비스듬히 · 1인칭 */
    private void orb(ClientGameTestContext ctx, TestServerContext server) {
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "void_jelly", PX, FLOOR, PZ - 9.5, 0F, false);
        ctx.waitTicks(8);
        float hp0 = hp(server);
        boolean fought = shoot(ctx, server, id, 1.8);
        check(fought, "jelly: fight after the arrow");
        sideView(ctx, -34F, 14F);
        boolean cast = waitFor(ctx, server, 60, s -> "charge".equals(phase(s, id)));
        ctx.waitTicks(8);
        shot(ctx, "v_orb_cast");
        int fired0 = server.computeOnServer(s -> VoidMonsters.orbsFired);
        boolean fly = waitFor(ctx, server, 20, s -> VoidMonsters.orbCount() > 0);
        int fired = server.computeOnServer(s -> VoidMonsters.orbsFired); // 모으는 동안은 아직 쏘지 않음 → 날아간 뒤에 셈
        ctx.waitTicks(6);
        shot(ctx, "v_orb_fly");
        int burst0 = server.computeOnServer(s -> VoidMonsters.orbsBurst);
        boolean burst = waitFor(ctx, server, 40, s -> VoidMonsters.orbsBurst > burst0);
        ctx.waitTicks(1);
        shot(ctx, "v_orb_burst");
        check(cast && fly && burst && hp(server) < hp0 && fired > fired0, "jelly: cast → orb (" + fired0 + " → " + fired + ") → burst on the player");
        // 1인칭: 날아오는 구체
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        lookAt(ctx, id, 1.6);
        boolean again = waitFor(ctx, server, 80, s -> VoidMonsters.orbCount() > 0);
        ctx.waitTicks(8);
        shot(ctx, "v_orb_fp");
        check(again, "jelly: fires again");
    }

    /** 굴착자 땅굴: 쏨 → 파고듦 → 땅속 (금 간 자국 · 흙) → 내 발밑 경고 원 → 솟구침 (띄움) — 3인칭 높이 */
    private void tunnel(ClientGameTestContext ctx, TestServerContext server) {
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "void_borer", PX, FLOOR, PZ - 10.5, 0F, false);
        ctx.waitTicks(8);
        float hp0 = hp(server);
        boolean fought = shoot(ctx, server, id, 0.8);
        check(fought, "borer: fight after the arrow");
        sideView(ctx, -40F, 34F);
        boolean dive = waitFor(ctx, server, 80, s -> "dive".equals(phase(s, id)));
        ctx.waitTicks(6);
        shot(ctx, "v_tunnel_dive");
        boolean under = waitFor(ctx, server, 20, s -> "under".equals(phase(s, id)));
        ctx.waitTicks(10);
        shot(ctx, "v_tunnel_under");
        boolean noTag = clientLabel(ctx, id) == null;
        boolean warn = waitFor(ctx, server, 60, s -> "warn".equals(phase(s, id)));
        ctx.waitTicks(8);
        shot(ctx, "v_tunnel_warn");
        boolean erupt = waitFor(ctx, server, 20, s -> fight(s, id) != null && "tunnel".equals(fight(s, id).lastKind()));
        ctx.waitTicks(2);
        shot(ctx, "v_tunnel_erupt");
        ctx.waitTicks(6);
        shot(ctx, "v_tunnel_erupt2");
        check(dive && under && noTag && warn && erupt && hp(server) < hp0, "borer: dive → under (no tag " + noTag + ") → warn → erupt");
    }

    /** 거수 수정 가시: 쏨 → 일어섬 (경고 원 줄) → 가시가 차례로 솟음 — 3인칭 높이 · 1인칭 */
    private void spikes(ClientGameTestContext ctx, TestServerContext server) {
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "void_behemoth", PX, FLOOR, PZ - 10.5, 0F, false);
        ctx.waitTicks(8);
        float hp0 = hp(server);
        boolean fought = shoot(ctx, server, id, 1.6);
        check(fought, "behemoth: fight after the arrow");
        sideView(ctx, -46F, 30F);
        boolean prep = waitFor(ctx, server, 90, s -> "prep".equals(phase(s, id)));
        ctx.waitTicks(10);
        shot(ctx, "v_spikes_prep");
        boolean rise = waitFor(ctx, server, 20, s -> "spikes".equals(phase(s, id)));
        ctx.waitTicks(6);
        shot(ctx, "v_spikes_rise");
        boolean done = waitFor(ctx, server, 40, s -> fight(s, id) != null && "spikes".equals(fight(s, id).lastKind()) && phase(s, id) == null);
        ctx.waitTicks(2);
        shot(ctx, "v_spikes_all");
        int n = server.computeOnServer(s -> VoidMonsters.lastSpikes.size());
        check(prep && rise && done && n == Monsters.kind("void_behemoth").traits().spikes().count() && hp(server) < hp0, "behemoth: prep → " + n + " spikes → hit");
        // 1인칭
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runOnServer(s -> {
            VoidMonsters.readyTrait(fight(s, id));
            RpgHealth.healFull(player(s));
        });
        lookAt(ctx, id, 1.4);
        boolean again = waitFor(ctx, server, 120, s -> "spikes".equals(phase(s, id)));
        ctx.waitTicks(5);
        lookAt(ctx, id, 0.6);
        shot(ctx, "v_spikes_fp");
        check(again, "behemoth: spikes again");
    }

    /** 거수 앞발 내려찍기 (근접): 공허 충격파 (분홍 고리 · 빛나는 금 · 수정 조각) — 3인칭 */
    private void shock(ClientGameTestContext ctx, TestServerContext server) {
        Monsters.Kind k = Monsters.kind("void_behemoth");
        double dist = k.half() + 0.3 + k.reach() - 0.2;
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "void_behemoth", PX, FLOOR, PZ - dist, 0F, false);
        ctx.waitTicks(8);
        lookAt(ctx, id, 1.8);
        ctx.waitTicks(3);
        attack(ctx, id);
        boolean wind = waitFor(ctx, server, 12, s -> fight(s, id) != null && fight(s, id).windupKind() != null);
        sideView(ctx, -40F, 22F);
        ctx.waitTicks(Math.max(1, k.windup() / 2));
        shot(ctx, "v_behemoth_windup");
        boolean hit = waitFor(ctx, server, 24, s -> fight(s, id) != null && fight(s, id).hits() >= 1);
        ctx.waitTicks(1);
        shot(ctx, "v_behemoth_shock");
        ctx.waitTicks(5);
        shot(ctx, "v_behemoth_shock2");
        check(wind && hit, "behemoth: stomp with void shock");
    }

    /** 죽음: 검은 구멍이 오므라들며 빨려 듦 (도약충 · 추적자) */
    private void death(ClientGameTestContext ctx, TestServerContext server) {
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int a = spawn(server, "void_leaper", PX - 1.2, FLOOR, PZ - 3.4, 0F, false);
        int b = spawn(server, "void_stalker", PX + 1.6, FLOOR, PZ - 4.6, 0F, false);
        ctx.waitTicks(10);
        server.runOnServer(s -> {
            for (int id : List.of(a, b)) {
                RpgMonster m = monster(s, id);
                if (m != null) m.setHealth(1F);
            }
        });
        lookAt(ctx, a, 0.4);
        ctx.waitTicks(2);
        attack(ctx, a);
        ctx.waitTicks(3);
        shot(ctx, "v_death");
        boolean dieAnim = server.computeOnServer(s -> monster(s, a) != null && monster(s, a).syncedAnims().stream().anyMatch(p -> p.anim().endsWith(".die")));
        ctx.waitTicks(6);
        shot(ctx, "v_death_shrink");
        lookAt(ctx, b, 1.3);
        ctx.waitTicks(10); // 공격 쿨다운이 차게
        attack(ctx, b);
        ctx.waitTicks(3);
        shot(ctx, "v_death2");
        ctx.waitTicks(9);
        shot(ctx, "v_death2_shrink");
        boolean dead = server.computeOnServer(s -> monster(s, a) == null || !monster(s, a).isAlive());
        check(dead && dieAnim, "void death: leaper died (die animation " + dieAnim + ")");
    }

    /** 전리품 팔기 창 (19종 — 4줄 · 공허 다섯은 셋째 · 넷째 줄) · 가방의 공허 전리품 */
    private void sell(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            int i = 0;
            for (String key : VOID) sp.getInventory().setItem(9 + i++, new ItemStack(GearItems.byId("rpg:loot_" + key), 8 + i * 3));
            sp.getInventory().setItem(14, new ItemStack(GearItems.byId("rpg:loot_frost_giant"), 12));
            Shop.sellLoop(sp, r -> {
            });
        });
        boolean open = waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("전리품 팔기"), "sell window");
        ctx.waitTicks(4);
        FormNet.ChestPayload form = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChestFormScreen c ? c.form() : null);
        boolean rows = form != null && form.slots().size() == 36 && form.slots().get(Shop.sellSlot(18)).name().contains("공허 수정");
        ctx.getInput().setCursorPos(0, 0);
        shotFull(ctx, "v_sell");
        check(open && rows, "sell window: 4 rows · void drops on row 3 · 4 (" + (form == null ? null : form.slots().size()) + ")");
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
        // 가방 (E)
        ctx.getInput().pressKey(options -> options.keyInventory);
        ctx.waitTicks(8);
        shotFull(ctx, "v_loot_inventory");
        ctx.setScreen(() -> null);
    }

    private boolean waitScreen(ClientGameTestContext ctx, Predicate<net.minecraft.client.gui.screens.Screen> test, String what) {
        try {
            ctx.waitFor(mc -> test.test(mc.gui.screen()), 100);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, what + " did not open");
            return false;
        }
    }
}
