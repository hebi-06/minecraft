package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.client.form.DialogFormScreen;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.mob.RpgMonster;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.QuestTalk;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.ui.FormNet;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 9단계 사진첩 (화면 시험, only=mob9): 새 몬스터 8종 — 한 줄로 선 모습 · 지역마다 둘, 특성 장면 (실제 공격 입력으로 먼저 때림):
 * 니세 깡충 (m9_nisse_*), 광폭 멧돼지 돌진 (앞발 긁기 · 붉은 줄 → 달림 → 들이받아 밀쳐냄 — m9_boar_*), 웜 유충 매복 (모래 속 → 다가가면 튀어나옴 — m9_larva_*),
 * 맞으면 붙는 효과 (모래 전갈 독 · 집게 게 붙잡기 · 그렌델 시듦 — m9_&lt;키&gt;_hit), 서리 늑대 무리 (울부짖음 → 무리 — m9_wolf_*),
 * 서리 거인 얼음 충격파 (m9_giant_*), 지역 이야기의 새 단계 대화 (농부 올라프 → 장난꾸러기 니세 — m9_q_*).
 * 확인 항목은 [mob9-gallery] OK 줄, 실패가 있으면 끝에 AssertionError.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgMoreMonsterGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-mob9-gallery");
    private static final double FLOOR = -60;
    /** 플레이어가 서는 자리 (북쪽 -z 를 봄) */
    private static final double PX = 0.5;
    private static final double PZ = 0.5;
    private static final List<String> REGIONS = List.of("farm", "desert", "sea", "north");

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("mob9")) return;
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
            server.runCommand("difficulty normal");
            server.runCommand("xp set @a 40 levels");
            ctx.waitTicks(10);
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
            part(ctx, server, "lineup", () -> lineup(ctx, server));
            part(ctx, server, "nisse", () -> nisse(ctx, server));
            part(ctx, server, "boar", () -> boar(ctx, server));
            part(ctx, server, "larva", () -> larva(ctx, server));
            for (String key : List.of("sand_scorpion", "crab", "grendel")) part(ctx, server, key, () -> onHit(ctx, server, key));
            part(ctx, server, "wolves", () -> wolves(ctx, server));
            part(ctx, server, "giant", () -> giant(ctx, server));
            part(ctx, server, "quest", () -> quest(ctx, server, conn));
        }
        LOG.info("[mob9-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("mob9 gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 도움

    private void check(boolean ok, String what) {
        LOG.info("[mob9-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    /** 장면 하나 (실패해도 다음 장면으로): 끝나면 몬스터 · 효과 · 카메라 · 창 · 바닥을 되돌림 */
    private void part(ClientGameTestContext ctx, TestServerContext server, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[mob9-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[mob9-gallery] part " + name, e);
        }
        try {
            ctx.setScreen(() -> null);
            server.runOnServer(s -> {
                for (RpgMonster m : level(s).getEntitiesOfClass(RpgMonster.class, new AABB(PX, FLOOR, PZ, PX, FLOOR, PZ).inflate(48))) m.discard();
                RpgHealth.healFull(player(s));
            });
            server.runCommand("effect clear @a");
            server.runCommand("fill -12 -61 -16 12 -61 6 minecraft:grass_block");
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
                mc.options.fov().set(70);
            });
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            ctx.waitTicks(6);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[mob9-gallery] reset after " + name, e);
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

    /**
     * 창이 열린 장면은 창 크기 그대로 (작은 크기로 찍으면 창의 잘라 그리기 영역이 사진 밖으로 나가 0 이 되어 그리기가 멈춤 — 사진 찍기에서만 생기는 일)
     */
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

    /** 몸을 움직이게 (맞아서 밀린 속도는 지움) */
    private static void wake(TestServerContext server, int id) {
        server.runOnServer(s -> {
            RpgMonster m = monster(s, id);
            if (m == null) return;
            m.setDeltaMovement(Vec3.ZERO);
            m.setNoAi(false);
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
            else LOG.warn("[mob9-gallery] target {} missing on client", id);
        });
    }

    /** 손에 든 무기 쓰기 (마우스 오른쪽 — 활은 바라보는 대상에 명중) */
    private static void use(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
    }

    private static void camera(ClientGameTestContext ctx, CameraType type) {
        ctx.runOnClient(mc -> mc.options.setCameraType(type));
    }

    /**
     * 3인칭 뒤 카메라를 비스듬히: 바라보던 쪽에서 yawOff 도 돌리고 pitch 도로 내려다봄 — 앞의 몬스터가 플레이어 몸에 가리지 않게
     * (보는 쪽만 바꿈 — 몬스터는 이미 싸우는 중이라 상관없음)
     */
    private static void sideView(ClientGameTestContext ctx, float yawOff, float pitch) {
        ctx.runOnClient(mc -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(mc.player.getYRot() + yawOff);
            mc.player.setXRot(pitch);
        });
        ctx.waitTicks(2);
    }

    /** 손 · HUD 숨기기 (F1 — 모습 사진) */
    private static void hideGui(ClientGameTestContext ctx, boolean hide) {
        ctx.runOnClient(mc -> mc.options.hideGui = hide);
    }

    /** 시야각 (멀리 있는 작은 것을 당겨 찍기 — 기본 70) */
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

    // ------------------------------------------------------------ 장면

    /** 한 줄로 선 새 몬스터 8종 (지역 순서 — 이름표 · 크기 비교) → 지역마다 둘씩 가까이 (비스듬히 서서 카메라를 봄) — 손 · HUD 없이 */
    private void lineup(ClientGameTestContext ctx, TestServerContext server) {
        hideGui(ctx, true);
        teleport(ctx, server, PX, FLOOR, PZ, 180F, -6F);
        List<Integer> ids = new ArrayList<>();
        double z = PZ - 9.5;
        for (int i = 0; i < RpgMoreMonsterTests.NEW.size(); i++) ids.add(spawn(server, RpgMoreMonsterTests.NEW.get(i), PX - 9.8 + i * 2.8, FLOOR, z, 0F, false));
        check(ids.stream().allMatch(i -> i >= 0), "8 new monsters spawned " + ids);
        ctx.waitTicks(30);
        shot(ctx, "m9_lineup");
        for (int i = 0; i < ids.size(); i++) {
            String label = clientLabel(ctx, ids.get(i));
            Monsters.Kind k = Monsters.kind(RpgMoreMonsterTests.NEW.get(i));
            check(label != null && label.contains("Lv." + k.level()) && label.contains(k.name()), k.key() + ": label on client " + label);
        }
        server.runOnServer(s -> {
            for (RpgMonster m : level(s).getEntitiesOfClass(RpgMonster.class, new AABB(PX, FLOOR, PZ, PX, FLOOR, PZ).inflate(24))) m.discard();
        });
        // 지역마다 둘 (왼쪽 · 오른쪽에서 카메라 쪽으로 비스듬히)
        for (int r = 0; r < REGIONS.size(); r++) {
            Monsters.Kind a = Monsters.kind(RpgMoreMonsterTests.NEW.get(2 * r));
            Monsters.Kind b = Monsters.kind(RpgMoreMonsterTests.NEW.get(2 * r + 1));
            double big = Math.max(a.height(), b.height());
            double d = big > 2.5 ? 5.4 : 3.9;
            teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
            int ia = spawn(server, a.key(), PX - 0.7 - a.half(), FLOOR, PZ - d, -30F, false);
            int ib = spawn(server, b.key(), PX + 0.7 + b.half(), FLOOR, PZ - d, 30F, false);
            ctx.waitTicks(12);
            lookAtPoint(ctx, new Vec3(PX, FLOOR + Math.max(0.5, big * 0.42), PZ - d));
            ctx.waitTicks(4);
            shot(ctx, "m9_pair_" + REGIONS.get(r));
            discard(server, ia);
            discard(server, ib);
        }
        hideGui(ctx, false);
    }

    private static void discard(TestServerContext server, int id) {
        server.runOnServer(s -> {
            Entity e = level(s).getEntity(id);
            if (e != null) e.discard();
        });
    }

    /** 니세: 칼로 때림 → 쇠스랑 찌르기 → 뒤로 깡충 (3인칭 — 뛰어 물러나는 모습) */
    private void nisse(ClientGameTestContext ctx, TestServerContext server) {
        Monsters.Kind k = Monsters.kind("nisse");
        double dist = k.half() + 0.3 + k.reach() - 0.1;
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "nisse", PX, FLOOR, PZ - dist, 0F, false);
        ctx.waitTicks(6);
        lookAt(ctx, id, 0.5);
        ctx.waitTicks(4);
        attack(ctx, id);
        boolean fought = waitFor(ctx, server, 8, s -> fight(s, id) != null);
        wake(server, id);
        check(fought, "nisse: fight after the real hit");
        sideView(ctx, -42F, 24F);
        boolean wind = waitFor(ctx, server, 10, s -> fight(s, id) != null && fight(s, id).windupKind() != null);
        ctx.waitTicks(3);
        shot(ctx, "m9_nisse_jab");
        double d0 = server.computeOnServer(s -> monster(s, id).position().distanceTo(player(s).position()));
        boolean hopped = waitFor(ctx, server, 20, s -> fight(s, id) != null && fight(s, id).hops() >= 1);
        ctx.waitTicks(3);
        shot(ctx, "m9_nisse_hop");
        ctx.waitTicks(4);
        shot(ctx, "m9_nisse_hop2");
        double d1 = server.computeOnServer(s -> monster(s, id).position().distanceTo(player(s).position()));
        check(wind && hopped && d1 > d0 + 0.8, "nisse: jab then hop back " + String.format(Locale.ROOT, "%.2f → %.2f", d0, d1));
    }

    /** 광폭 멧돼지: 활로 쏨 → 앞발 긁기 (붉은 줄 · 먼지) → 달림 → 들이받아 플레이어를 밀쳐냄 (3인칭) */
    private void boar(ClientGameTestContext ctx, TestServerContext server) {
        Monsters.Kind k = Monsters.kind("boar");
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        int id = spawn(server, "boar", PX, FLOOR, PZ - (k.half() + 0.3 + 6.0), 0F, false);
        ctx.waitTicks(8);
        lookAt(ctx, id, 0.6);
        ctx.waitTicks(6);
        Vec3 p0 = server.computeOnServer(s -> player(s).position());
        float hp0 = server.computeOnServer(s -> player(s).getHealth());
        use(ctx);
        boolean fought = waitFor(ctx, server, 30, s -> fight(s, id) != null);
        wake(server, id);
        check(fought, "boar: fight after the arrow");
        sideView(ctx, -32F, 18F);
        boolean prep = waitFor(ctx, server, 40, s -> fight(s, id) != null && "prep".equals(fight(s, id).chargePhase()));
        ctx.waitTicks(7);
        shot(ctx, "m9_boar_prep");
        boolean dash = waitFor(ctx, server, 20, s -> fight(s, id) != null && "dash".equals(fight(s, id).chargePhase()));
        ctx.waitTicks(2);
        shot(ctx, "m9_boar_dash");
        boolean hit = waitFor(ctx, server, 24, s -> fight(s, id) != null && fight(s, id).hits() >= 1);
        ctx.waitTicks(2);
        shot(ctx, "m9_boar_hit");
        ctx.waitTicks(5);
        shot(ctx, "m9_boar_hit2");
        ctx.waitTicks(10);
        Vec3 p1 = server.computeOnServer(s -> player(s).position());
        float hp1 = server.computeOnServer(s -> player(s).getHealth());
        String kind = server.computeOnServer(s -> fight(s, id) == null ? null : fight(s, id).lastKind());
        check(prep && dash, "boar: prep then dash");
        check(hit && "charge".equals(kind) && hp1 < hp0, "boar: charge hit (" + kind + ", hp " + hp0 + " → " + hp1 + ")");
        check(Math.hypot(p1.x - p0.x, p1.z - p0.z) > 1.5, "boar: player knocked back " + String.format(Locale.ROOT, "%.2f", Math.hypot(p1.x - p0.x, p1.z - p0.z)));
    }

    /** 웜 유충: 모래밭에 숨음 (모래 둔덕만 · 이름표 없음) → 다가가면 튀어나옴 (모래 폭발) */
    private void larva(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("fill -4 -61 -11 5 -61 -1 minecraft:sand");
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        double lz = PZ - 7.5;
        int id = spawn(server, "worm_larva", PX, FLOOR, lz, 0F, false);
        ctx.waitTicks(10);
        server.runOnServer(s -> Monsters.burrow(monster(s, id)));
        ctx.waitTicks(16);
        lookAt(ctx, id, 0.1);
        fov(ctx, 30); // 7칸 밖의 모래 둔덕을 당겨 찍음
        ctx.waitTicks(3);
        shot(ctx, "m9_larva_buried");
        fov(ctx, 70);
        boolean buriedClient = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof RpgMonster m && m.buried() && m.getBbHeight() < 0.5);
        check(buriedClient && clientLabel(ctx, id) == null, "larva: buried on the client (low box, no tag)");
        // 다가감 (3칸 — detect 5 안)
        teleport(ctx, server, PX, FLOOR, lz + 3.2, 180F, 0F);
        lookAt(ctx, id, 0.1);
        boolean out = waitFor(ctx, server, 12, s -> !Monsters.buried(monster(s, id)));
        ctx.waitTicks(1);
        shot(ctx, "m9_larva_emerge");
        ctx.waitTicks(6);
        lookAt(ctx, id, 1.0);
        shot(ctx, "m9_larva_up");
        boolean fighting = server.computeOnServer(s -> fight(s, id) != null && fight(s, id).player() == player(s));
        check(out && fighting, "larva: emerged and attacks the player who came near");
        check(clientLabel(ctx, id) != null, "larva: name tag back after emerging");
    }

    /** 맞으면 붙는 효과: 모래 전갈 (독 — 초록 방울) · 집게 게 (붙잡기 — 구속 IV) · 그렌델 (시듦 — 검은 연기) */
    private void onHit(ClientGameTestContext ctx, TestServerContext server, String key) {
        Monsters.Kind k = Monsters.kind(key);
        double dist = k.half() + 0.3 + k.reach() - 0.1;
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, key, PX, FLOOR, PZ - dist, 0F, false);
        ctx.waitTicks(6);
        lookAt(ctx, id, Math.max(0.6, Math.min(1.6, k.height() * 0.55)));
        ctx.waitTicks(3);
        attack(ctx, id);
        boolean hit = waitFor(ctx, server, 40, s -> fight(s, id) != null && fight(s, id).hits() >= 1);
        ctx.waitTicks(1);
        shot(ctx, "m9_" + key + "_hit");
        ctx.waitTicks(6);
        shot(ctx, "m9_" + key + "_after");
        boolean effect = server.computeOnServer(s -> switch (key) {
            case "sand_scorpion" -> player(s).hasEffect(MobEffects.POISON);
            case "grendel" -> player(s).hasEffect(MobEffects.WITHER);
            default -> player(s).getEffect(MobEffects.SLOWNESS) != null && player(s).getEffect(MobEffects.SLOWNESS).getAmplifier() == 3;
        });
        check(hit && effect, key + ": hit with its effect");
    }

    /** 서리 늑대 무리 (눈밭): 하나를 쏘면 울부짖음 (하늘빛 고리) → 둘레 늑대가 차례로 울부짖고 덤빔 (3인칭) */
    private void wolves(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("fill -8 -61 -14 9 -61 -1 minecraft:snow_block");
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        int a = spawn(server, "frost_wolf", PX, FLOOR, PZ - 6.5, 0F, false);
        int b = spawn(server, "frost_wolf", PX - 3.5, FLOOR, PZ - 9.0, 0F, false);
        int c = spawn(server, "frost_wolf", PX + 3.5, FLOOR, PZ - 10.0, 0F, false);
        ctx.waitTicks(8);
        lookAt(ctx, a, 0.7);
        ctx.waitTicks(6);
        use(ctx);
        boolean fought = waitFor(ctx, server, 30, s -> fight(s, a) != null);
        for (int id : List.of(a, b, c)) wake(server, id);
        sideView(ctx, -28F, 20F);
        ctx.waitTicks(3);
        shot(ctx, "m9_wolf_howl");
        ctx.waitTicks(9);
        shot(ctx, "m9_wolf_pack");
        ctx.waitTicks(14);
        shot(ctx, "m9_wolf_chase");
        int called = server.computeOnServer(s -> fight(s, a) == null ? -1 : fight(s, a).packCalled());
        boolean joined = server.computeOnServer(s -> fight(s, b) != null && fight(s, c) != null);
        check(fought && called >= 2 && joined, "wolves: howl called the pack (" + called + ")");
    }

    /** 서리 거인 (눈밭): 칼로 때림 → 내려치기 → 얼음 고리 · 고드름 · 서리 금 (3인칭 — 플레이어 둘레) */
    private void giant(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("fill -8 -61 -12 9 -61 4 minecraft:snow_block");
        Monsters.Kind k = Monsters.kind("frost_giant");
        double dist = k.half() + 0.3 + k.reach() - 0.1;
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        int id = spawn(server, "frost_giant", PX, FLOOR, PZ - dist, 0F, false);
        ctx.waitTicks(6);
        lookAt(ctx, id, 2.0);
        ctx.waitTicks(3);
        attack(ctx, id);
        boolean wind = waitFor(ctx, server, 10, s -> fight(s, id) != null && fight(s, id).windupKind() != null);
        lookAt(ctx, id, 1.4);
        sideView(ctx, -36F, 16F);
        ctx.waitTicks(Math.max(1, k.windup() / 2));
        shot(ctx, "m9_giant_windup");
        boolean hit = waitFor(ctx, server, 20, s -> fight(s, id) != null && fight(s, id).hits() >= 1);
        ctx.waitTicks(1);
        shot(ctx, "m9_giant_shock");
        ctx.waitTicks(5);
        shot(ctx, "m9_giant_shock2");
        boolean slow = server.computeOnServer(s -> player(s).getEffect(MobEffects.SLOWNESS) != null);
        check(wind && hit && slow, "giant: slam hit with ice shock (slowness on the player)");
    }

    // ------------------------------------------------------------ 대화 (지역 이야기의 새 단계)

    private boolean waitDialog(ClientGameTestContext ctx, Predicate<FormNet.DialogPayload> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof DialogFormScreen d && test.test(d.form()), 100);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "dialog " + what + " did not open");
            return false;
        }
    }

    /** 지금 대화창에서 글자가 들어간 선택지 번호 (없으면 -1) */
    private static int choiceWith(ClientGameTestContext ctx, String part) {
        return ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof DialogFormScreen d)) return -1;
            List<FormNet.ChoiceData> cs = d.form().choices();
            for (int i = 0; i < cs.size(); i++) if (cs.get(i).text().contains(part)) return i;
            return -1;
        });
    }

    /** 지금 화면의 GUI 좌표 → 창 픽셀 좌표 (마우스) */
    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, int choice) {
        double[] p = at(ctx, sc -> ((DialogFormScreen) sc).choiceCenter(choice));
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    /** 농부 올라프 (허수아비 처치를 마침): 새 단계 "장난꾸러기 니세" 를 고름 → 대사 세 장 (마지막 장: 목표 · 보상) → 수락 */
    private void quest(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        teleport(ctx, server, PX, FLOOR, PZ, 180F, 0F);
        Vec3 at = new Vec3(PX, FLOOR, PZ - 3);
        int olaf = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            StringBuilder qs = new StringBuilder("{");
            int ks = MainQuests.mainKillStep("nisse");
            for (int i = 1; i < ks; i++) qs.append(i > 1 ? "," : "").append("\"").append(MainQuests.mainId("sword", i)).append("\":{\"s\":\"d\",\"n\":0,\"c\":1}");
            Props.set(sp, "rpg:main_path", "sword");
            Props.set(sp, "rpg:qs", qs.append("}").toString());
            Quests.forgetQuestPlayer(sp.getUUID());
            Mannequin m = NpcData.spawnAt(level(s), at, new Vec3(PX, FLOOR + 1.6, PZ));
            NpcData.setKey(m, "main_13");
            NpcData.setName(m, MainQuests.mainNpcByKey("main_13").name());
            NpcData.refresh(m, 0);
            NpcData.setMainRecord("main_13", m, null, null);
            return m.getId();
        });
        try {
            ctx.waitTicks(10);
            lookAtPoint(ctx, new Vec3(at.x, at.y + 1.5, at.z));
            ctx.waitTicks(10);
            shot(ctx, "m9_q_olaf");
            server.runOnServer(s -> QuestTalk.talkLoop(player(s), level(s).getEntity(olaf), null));
            if (!waitDialog(ctx, d -> d.choices().stream().anyMatch(c -> c.text().contains("장난꾸러기 니세")), "olaf greeting with the nisse step")) return;
            ctx.getInput().setCursorPos(6, 6);
            ctx.waitTicks(2);
            shotFull(ctx, "m9_q_greet");
            click(ctx, conn, choiceWith(ctx, "장난꾸러기 니세"));
            if (!waitDialog(ctx, d -> d.text().contains("니세들이 말썽"), "offer page 1")) return;
            shotFull(ctx, "m9_q_offer1");
            for (int i = 0; i < 4 && choiceWith(ctx, "수락하기") < 0; i++) click(ctx, conn, 0); // 다음 장
            if (!waitDialog(ctx, d -> d.text().contains("니세 열만 혼내 주게") && d.choices().size() >= 2, "offer last page")) return;
            ctx.getInput().setCursorPos(6, 6);
            ctx.waitTicks(2);
            shotFull(ctx, "m9_q_offer");
            click(ctx, conn, choiceWith(ctx, "수락하기"));
            ctx.waitTicks(6);
            String st = server.computeOnServer(s -> Quests.statusOf(player(s), Quests.getQuest(MainQuests.mainId("sword", MainQuests.mainKillStep("nisse")))));
            check("active".equals(st), "quest: nisse step accepted (" + st + ")");
            shot(ctx, "m9_q_accepted");
        } finally {
            ctx.setScreen(() -> null);
            server.runOnServer(s -> {
                Entity e = level(s).getEntity(olaf);
                if (e != null) e.discard();
                Quests.resetQuests(player(s));
            });
        }
    }
}
