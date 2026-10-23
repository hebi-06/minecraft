package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import mmorpg.core.Rules;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.HuntZones;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.mob.RpgMonster;
import mmorpg.fabric.quest.Loot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=monster): 일반 몬스터 6종이 서바이벌 플레이어를 공격하는 장면 — 플레이어가 실제 공격 입력 (gameMode.attack · useItem) 으로
 * 먼저 때림 → 피해 숫자 (몸통에서 떠오름) · 이름표 체력 막대 · 공격 동작 + 붉은 고리 → 할퀴기 참격 (1인칭 화면 아래쪽) · 불꽃.
 * 달려들기 (굴론 — 움직이는 몬스터, 활로 쏨), 원거리 반격 (기둥 위에서 쏨 → 발밑 붉은 고리 → 날아오는 기운), 죽음 (+경험치 글자 · 전리품 빛기둥),
 * 사냥터 (/mobzone — 연기 · 초록 고리) · /mob. 확인 항목은 [monster-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 * 사진 이름 mon_&lt;몬스터&gt;_1hit · _2windup · _3claw, mon_troll_3p, mon_label, mon_lunge_*, mon_counter_ring · _fp · _strike, mon_death_*, mon_zone, mon_mobcmd.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgMonsterGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-monster-gallery");
    private static final double FLOOR = -60;
    /** 플레이어가 서는 자리 (북쪽 -z 를 봄) */
    private static final double PX = 0.5;
    private static final double PZ = 0.5;

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("monster")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("difficulty normal");
            server.runCommand("xp set @a 40 levels");
            ctx.waitTicks(10);
            // 체력 넉넉히 (트롤 한 방 38), 힘 · 민첩은 낮게 (0렙 칼 한 방에 허수아비가 쓰러지지 않게)
            server.runCommand("execute as @a run rpg set vit 300");
            server.runCommand("execute as @a run rpg set str 20");
            server.runCommand("execute as @a run rpg set dex 20");
            server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l0_common");
            server.runCommand("item replace entity @a hotbar.1 with rpg:bow_l0_common");
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
            ctx.waitTicks(70); // 접속 직후 무적 (3초) 이 지나게
            for (String key : Rules.get().strings("MONSTER_ORDER")) melee(ctx, server, key);
            lunge(ctx, server);
            counter(ctx, server);
            death(ctx, server);
            zone(ctx, server);
        }
        LOG.info("[monster-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("monster gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도움

    private void check(boolean ok, String what) {
        LOG.info("[monster-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
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

    /** 실제 공격 입력 (마우스 왼쪽과 같은 공격 패킷) */
    private static void attack(ClientGameTestContext ctx, int id) {
        ctx.runOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e != null) mc.gameMode.attack(mc.player, e);
            else LOG.warn("[monster-gallery] target {} missing on client", id);
        });
    }

    /** 손에 든 무기 쓰기 (마우스 오른쪽 — 활은 바라보는 대상에 명중) */
    private static void use(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
    }

    private static float playerHp(TestServerContext server) {
        return server.computeOnServer(s -> player(s).getHealth());
    }

    private static void heal(TestServerContext server) {
        server.runOnServer(s -> RpgHealth.healFull(player(s)));
    }

    /** 클라이언트의 이름표: 그 몬스터에 탄 글자 표시 엔티티의 글 (없으면 null) */
    private static String clientLabel(ClientGameTestContext ctx, int id) {
        return ctx.computeOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e == null) return null;
            for (Entity p : e.getPassengers()) if (p instanceof Display.TextDisplay t) return t.getText().getString();
            return null;
        });
    }

    // ------------------------------------------------------------ 장면

    /** 몬스터 하나: 사거리 바로 안 (몸 가장자리 사이 reach - 0.1칸 — 공격 동작이 카메라를 뚫지 않게) → 칼로 때림 → 반격 동작 · 붉은 고리 → 할퀴기 */
    private void melee(ClientGameTestContext ctx, TestServerContext server, String key) {
        Monsters.Kind k = Monsters.kind(key);
        double dist = k.half() + 0.3 + k.reach() - 0.1;
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, key, PX, FLOOR, PZ - dist, 0F, false);
        ctx.waitTicks(k.traits().isVoid() ? 26 : 6); // 0.32.0 공허 몬스터: 균열에서 나오는 동작 (0.8초) · 균열이 닫힌 뒤
        lookAt(ctx, id, Math.max(0.9, Math.min(1.7, k.height() * 0.55)));
        ctx.waitTicks(3);
        float hp0 = playerHp(server);
        attack(ctx, id);
        boolean started = waitFor(ctx, server, 8, s -> {
            Monsters.Fight f = fight(s, id);
            return f != null && f.windupKind() != null;
        });
        check(started, key + ": fight + attack windup after the real hit");
        ctx.waitTicks(1);
        shot(ctx, "mon_" + key + "_1hit");
        String label = clientLabel(ctx, id);
        check(label != null && label.contains("Lv." + k.level()) && label.contains(k.name()) && label.contains("|"), key + ": label with hp bar on client " + label);
        ctx.waitTicks(Math.max(1, k.windup() / 2 - 2));
        shot(ctx, "mon_" + key + "_2windup");
        boolean hit = waitFor(ctx, server, 14, s -> {
            Monsters.Fight f = fight(s, id);
            return f != null && f.hits() >= 1;
        });
        ctx.waitTicks(1);
        shot(ctx, "mon_" + key + "_3claw");
        float hp1 = playerHp(server);
        check(hit && hp1 < hp0, key + ": claw hit the player (" + hp0 + " → " + hp1 + ")");
        if (key.equals("troll")) {
            // 3인칭 (뒤): 플레이어 · 트롤 · 고리 · 참격을 한 장에 (첫 대에 밀려난 자리를 되돌림)
            teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
            lookAt(ctx, id, 1.7);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            waitFor(ctx, server, 60, s -> {
                Monsters.Fight f = fight(s, id);
                return f != null && f.windupKind() != null;
            });
            ctx.waitTicks(Math.max(1, k.windup() - 2));
            waitFor(ctx, server, 6, s -> {
                Monsters.Fight f = fight(s, id);
                return f != null && f.hits() >= 2;
            });
            ctx.waitTicks(1);
            shot(ctx, "mon_troll_3p");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            // 이름표 · 체력 막대 가까이 (트롤 앞 3칸에서 올려다봄)
            teleport(ctx, server, PX, FLOOR, PZ + 1.5, 180F, -20F);
            ctx.getInput().lookAt(180F, -20F);
            ctx.waitTicks(4);
            shot(ctx, "mon_label");
        }
        discard(server, id);
        heal(server);
        ctx.waitTicks(4);
    }

    /** 달려들기: 굴론 (움직임) 을 5칸 밖에서 활로 쏨 → 사거리 밖이라 뛰어들며 공격 */
    private void lunge(ClientGameTestContext ctx, TestServerContext server) {
        Monsters.Kind k = Monsters.kind("gulon");
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        double dist = k.half() + 0.3 + 5.4;
        int id = spawn(server, "gulon", PX, FLOOR, PZ - dist, 0F, true);
        ctx.waitTicks(8);
        lookAt(ctx, id, 0.7);
        ctx.waitTicks(10);
        double d0 = server.computeOnServer(s -> monster(s, id).position().distanceTo(player(s).position()));
        use(ctx);
        boolean lunged = waitFor(ctx, server, 40, s -> {
            Monsters.Fight f = fight(s, id);
            return f != null && "lunge".equals(f.windupKind());
        });
        check(lunged, "gulon lunged from outside the reach");
        ctx.waitTicks(1);
        shot(ctx, "mon_lunge_t1");
        ctx.waitTicks(3);
        shot(ctx, "mon_lunge_t4");
        double d1 = server.computeOnServer(s -> monster(s, id).position().distanceTo(player(s).position()));
        check(d1 < d0 - 1, "lunge closed the distance " + String.format(Locale.ROOT, "%.2f → %.2f", d0, d1));
        waitFor(ctx, server, 14, s -> {
            Monsters.Fight f = fight(s, id);
            return f != null && "lunge".equals(f.lastKind());
        });
        ctx.waitTicks(1);
        shot(ctx, "mon_lunge_land");
        discard(server, id);
        heal(server);
        ctx.waitTicks(4);
    }

    /** 원거리 반격: 4칸 기둥 위에서 굴론을 쏨 → 닿지도 달려들지도 못함 → 발밑 붉은 고리 → 날아온 기운이 그 자리를 침 */
    private void counter(ClientGameTestContext ctx, TestServerContext server) {
        int bx = 4;
        server.runCommand("fill " + bx + " -60 0 " + bx + " -57 0 minecraft:stone_bricks");
        teleport(ctx, server, bx + 0.5, FLOOR + 4, 0.5, 90F, 40F);
        int id = spawn(server, "gulon", bx + 0.5 - 2.0, FLOOR, 0.5, -90F, false);
        ctx.waitTicks(8);
        lookAt(ctx, id, 0.7);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(10);
        float hp0 = playerHp(server);
        use(ctx);
        boolean ring = waitFor(ctx, server, 40, s -> {
            Monsters.Fight f = fight(s, id);
            return f != null && "counter".equals(f.windupKind());
        });
        Vec3 spot = server.computeOnServer(s -> {
            Monsters.Fight f = fight(s, id);
            return f == null ? null : f.counterSpot();
        });
        Vec3 feet = server.computeOnServer(s -> player(s).position());
        check(ring && spot != null && spot.distanceTo(feet) < 0.5, "counter ring under the player on the pillar " + spot);
        ctx.waitTicks(3);
        shot(ctx, "mon_counter_ring");
        // 1인칭: 아래의 굴론 · 발밑 고리 · 날아오는 기운 (동작 시간 + 0.3초 — 마지막 0.2초에 날아옴)
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        lookAt(ctx, id, 0.7);
        ctx.waitTicks(7);
        shot(ctx, "mon_counter_fp");
        boolean hit = waitFor(ctx, server, 20, s -> {
            Monsters.Fight f = fight(s, id);
            return f != null && f.hits() >= 1;
        });
        shot(ctx, "mon_counter_strike");
        check(hit && playerHp(server) < hp0, "counter strike hit");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        discard(server, id);
        server.runCommand("fill " + bx + " -60 0 " + bx + " -57 0 minecraft:air");
        heal(server);
        ctx.waitTicks(4);
    }

    /**
     * 죽음: 쓰러지는 몸에서 "+N 경험치" (연두) · 플레이어 쪽으로 빛 · 전리품 빛기둥 (확률은 정해 둠). 플레이어가 40렙이라 경험치를 주는 트롤 (xpUntil 43) 로
     */
    private void death(ClientGameTestContext ctx, TestServerContext server) {
        Monsters.Kind k = Monsters.kind("troll");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        double dist = k.half() + 0.3 + 2.4;
        int id = spawn(server, "troll", PX, FLOOR, PZ - dist, 0F, false);
        ctx.waitTicks(6);
        server.runOnServer(s -> {
            monster(s, id).setHealth(1F);
            Loot.rand = () -> 0.0;
        });
        lookAt(ctx, id, 2.2);
        ctx.waitTicks(20);
        int xp0 = server.computeOnServer(s -> player(s).totalExperience);
        attack(ctx, id);
        ctx.waitTicks(3);
        shot(ctx, "mon_death_t3");
        ctx.waitTicks(6);
        shot(ctx, "mon_death_t9");
        int xp1 = server.computeOnServer(s -> player(s).totalExperience);
        boolean loot = server.computeOnServer(s -> !level(s).getEntitiesOfClass(ItemEntity.class, new AABB(PX, FLOOR, PZ - dist, PX, FLOOR, PZ - dist).inflate(3),
                e -> e.getItem().is(GearItems.byId("rpg:loot_troll"))).isEmpty());
        check(xp1 - xp0 == k.xp(), "xp +" + k.xp() + " to the killer (" + xp0 + " → " + xp1 + ")");
        check(loot, "loot dropped at the body");
        DoubleSupplier rnd = Math::random;
        server.runOnServer(s -> Loot.rand = rnd);
        server.runCommand("kill @e[type=minecraft:item]");
        ctx.waitTicks(4);
    }

    /** 사냥터: /mobzone gulon 3 → 연기 · 초록 고리, /mob troll 2 → 눈앞 4칸, 치우기 */
    private void zone(ClientGameTestContext ctx, TestServerContext server) {
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.getInput().lookAt(180F, 35F);
        ctx.waitTicks(8);
        server.runCommand("execute as @a at @a run mobzone gulon 3");
        ctx.waitTicks(2);
        shot(ctx, "mon_zone");
        int n = server.computeOnServer(s -> {
            int c = 0;
            for (HuntZones.Zone z : HuntZones.zoneList()) c += HuntZones.aliveIn(level(s), z);
            return c;
        });
        check(n == 3, "hunt zone spawned 3 (" + n + ")");
        server.runCommand("execute as @a at @a run mob troll 2");
        ctx.waitTicks(6);
        shot(ctx, "mon_mobcmd");
        int trolls = server.computeOnServer(s -> level(s).getEntitiesOfClass(RpgMonster.class, player(s).getBoundingBox().inflate(10),
                m -> m.def.id().equals("rpg:troll")).size());
        check(trolls == 2, "/mob troll 2 (" + trolls + ")");
        server.runCommand("execute as @a at @a run mob clear");
        ctx.waitTicks(2);
        int left = server.computeOnServer(s -> level(s).getEntitiesOfClass(RpgMonster.class, player(s).getBoundingBox().inflate(48)).size());
        check(left == 3, "/mob clear keeps the 3 hunt-zone monsters (" + left + " left)");
        server.runCommand("execute as @a at @a run mobzone clear");
        ctx.waitTicks(2);
        int none = server.computeOnServer(s -> level(s).getEntitiesOfClass(RpgMonster.class, player(s).getBoundingBox().inflate(48)).size());
        check(none == 0, "/mobzone clear removed the zone (" + none + " left)");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }
}
