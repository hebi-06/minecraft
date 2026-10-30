package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.skill.Transcend;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 10단계 영웅 무기 스킬 사진첩 (화면 시험, only=hero): 평평한 세계에서 과녁 허스크 앞에서 6 스킬을 실제 입력 (근접 = 우클릭, 활 · 완드 = 웅크리고 우클릭) 으로
 * 쓰고 스킬마다 정한 순간에 사진 (hs_&lt;스킬&gt;_&lt;틱&gt;[_3p]) — 1인칭 · 3인칭 한 번씩. 스킬마다 써졌는지 (재사용 대기 시작) · 들어간 피해 · 자리 (그림자 습격 —
 * 클라이언트의 몸이 옮겨졌는지) · 회복 (영혼 수확) 을 로그 (hero-gallery) 로, 써지지 않았거나 피해가 0 이면 마지막에 실패.
 * 마지막에 가방에서 영웅 칼 · 완드 설명 (스킬 줄) 사진.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgHeroSkillGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-hero-gallery");
    /** 과녁: 앞쪽 무리 넷 (7~10칸) + 옆 둘 (4~5칸) — 평평한 세계 땅 y = -60 */
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 7.5), new Vec3(-1.0, -60, 8.5), new Vec3(2.0, -60, 8.0), new Vec3(0.5, -60, 10.0), new Vec3(-3.5, -60, 4.5),
        new Vec3(4.0, -60, 5.0)
    };
    /** 가까운 과녁 (성광 강타 — 앞 4.5칸): 앞 · 양옆 앞 3~4칸 + 양옆 (부채꼴 밖 — 3인칭 카메라를 가리지 않게 뒤에는 두지 않음) */
    private static final Vec3[] NEAR = {
        new Vec3(0.5, -60, 4.0), new Vec3(-2.3, -60, 3.0), new Vec3(3.3, -60, 3.0), new Vec3(-3.5, -60, 0.5), new Vec3(4.5, -60, 0.5)
    };

    /** 스킬 하나: 무기 종류 · 사진 찍을 틱 (쓴 뒤) · 끝까지 기다릴 틱 · 가까운 과녁인지 */
    private record Case(String type, int[] shots, int total, boolean near) {}

    private static final Case[] CASES = {
        new Case("sword", new int[] {5, 8, 19}, 34, false),
        new Case("dagger", new int[] {2, 11, 21}, 36, false),
        new Case("axe", new int[] {4, 9, 23}, 40, false),
        new Case("shield", new int[] {4, 13, 30}, 66, true),
        new Case("bow", new int[] {4, 8, 14}, 34, false),
        new Case("wand", new int[] {4, 9, 18, 23}, 40, false),
    };

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active()) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만
        boolean film = RpgFilm.on() && RpgFilm.listed("filmhero"); // 0.48.2 스킬 영상 (only=film,filmhero) — 사진 대신 틱마다
        if (!film && (RpgFilm.on() || !RpgProdSmoke.only("hero"))) return; // 고른 시험만 (MMORPG_ONLY)
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("xp set @a 40 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(10);
            for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
            for (int i = 0; i < CASES.length; i++) {
                server.runCommand("item replace entity @a hotbar." + i + " with rpg:" + CASES[i].type() + "_l40_hero");
            }
            RpgFilm f = film ? new RpgFilm(ctx) : null;
            for (int i = 0; i < CASES.length; i++) {
                int sel = i;
                ctx.getInput().pressKey(options -> options.keyHotbarSlots[sel]);
                if (f != null) {
                    film(ctx, server, CASES[i], f);
                    continue;
                }
                run(ctx, server, CASES[i], false);
                run(ctx, server, CASES[i], true);
            }
            if (f == null) tooltips(ctx, server);
        }
        LOG.info("[hero-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("hero skill gallery: " + String.join(" / ", failures));
    }

    /** 스킬 하나를 실제 입력으로 쓰고 사진 · 피해 기록 */
    private void run(ClientGameTestContext ctx, TestServerContext server, Case c, boolean third) {
        Weapon w = Weapons.parseWeapon("rpg:" + c.type() + "_l40_hero");
        Obj k = w.skills().get(0);
        String id = k.str("id");
        server.runCommand("time set 6000");
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (Husk h : sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(48), h -> true)) h.discard();
            Transcend.resetCooldowns(sp);
            RpgHealth.healFull(sp);
            if (c.type().equals("wand")) sp.setHealth(sp.getMaxHealth() * 0.6F); // 회복이 보이게
            spawnHusks(sp.level(), c.near() ? NEAR : TARGETS);
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(12);
        boolean ranged = c.type().equals("bow") || c.type().equals("wand");
        double z0 = ctx.computeOnClient(mc -> mc.player.getZ());
        float hp0 = server.computeOnServer(s -> player(s).getHealth());
        // 입력: 근접 = 우클릭, 활 · 완드 = 웅크리고 우클릭
        if (ranged) {
            ctx.getInput().holdShift();
            ctx.waitTicks(3);
        }
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        if (ranged) ctx.getInput().releaseShift();
        int t = 0;
        double zMax = z0;
        for (int shot : c.shots()) {
            if (shot > t) {
                ctx.waitTicks(shot - t);
                t = shot;
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "hs_%s_%02d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
            zMax = Math.max(zMax, ctx.computeOnClient(mc -> mc.player.getZ()));
        }
        if (c.total() > t) ctx.waitTicks(c.total() - t);
        boolean cast = server.computeOnServer(s -> Transcend.cooldownLeft(player(s), k) > 0);
        double dealt = server.computeOnServer(s -> {
            double sum = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        float hp1 = server.computeOnServer(s -> player(s).getHealth());
        LOG.info("[hero-gallery] {} {} cast={} dealt={} moved={} healed={}", id, third ? "3p" : "1p", cast, String.format(Locale.ROOT, "%.1f", dealt),
                String.format(Locale.ROOT, "%.2f", zMax - z0), String.format(Locale.ROOT, "%.2f", hp1 - hp0));
        String tag = id + (third ? " (3p)" : "");
        if (!cast) failures.add(tag + " not cast");
        else if (!(dealt > 0)) failures.add(tag + " dealt no damage");
        if (cast && id.equals("shadow_ambush") && !(zMax - z0 > 4)) failures.add(tag + " did not move the player on the client (" + (zMax - z0) + ")");
        if (cast && id.equals("lich_harvest") && !(hp1 > hp0)) failures.add(tag + " did not heal (" + hp0 + " → " + hp1 + ")");
    }

    /** 0.48.2 스킬 영상: 같은 입력 · 과녁으로 쓰고 틱마다 한 장 (RpgFilm) */
    private void film(ClientGameTestContext ctx, TestServerContext server, Case c, RpgFilm f) {
        Weapon w = Weapons.parseWeapon("rpg:" + c.type() + "_l40_hero");
        Obj k = w.skills().get(0);
        String id = k.str("id");
        server.runCommand("time set 6000");
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (Husk h : sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(48), h -> true)) h.discard();
            Transcend.resetCooldowns(sp);
            RpgHealth.healFull(sp);
            if (c.type().equals("wand")) sp.setHealth(sp.getMaxHealth() * 0.6F);
            spawnHusks(sp.level(), c.near() ? NEAR : TARGETS);
        });
        ctx.waitTicks(12);
        boolean ranged = c.type().equals("bow") || c.type().equals("wand");
        f.begin(id, k.str("name"), w.name() + " · 영웅 스킬 (" + (ranged ? "웅크리고 우클릭" : "우클릭") + ")", c.near() ? 2.0 : 4.0);
        f.frames(4);
        if (ranged) {
            ctx.getInput().holdShift();
            f.frames(3);
        }
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        f.cast();
        if (ranged) ctx.getInput().releaseShift();
        f.until(c.total());
        double dealt = server.computeOnServer(s -> {
            double sum = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        f.end(dealt);
        if (!(dealt > 0)) failures.add(id + " (film) dealt no damage");
    }

    /** 가방을 열고 영웅 칼 (칸 0) · 완드 (칸 5) 의 설명 사진 (스킬 줄) */
    private void tooltips(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(10);
        ctx.getInput().pressKey(options -> options.keyInventory);
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen, 40);
        } catch (AssertionError | RuntimeException e) {
            failures.add("inventory did not open");
            return;
        }
        ctx.waitTicks(5);
        for (int slot : new int[] {0, 5}) {
            hover(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 18 * slot + 8, (sc.height - 166) / 2 + 142 + 8});
            ctx.takeScreenshot("hs_tooltip_" + CASES[slot].type());
        }
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] q = sc == null ? null : where.apply(sc);
            if (q == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt) — 나를 봄 */
    private static void spawnHusks(ServerLevel level, Vec3[] spots) {
        TestDummies.reset();
        for (Vec3 at : spots) {
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (h == null) continue;
            TestDummies.make(h);
            h.snapTo(at.x, at.y, at.z, 180F, 0F);
            h.setYHeadRot(180F);
            h.setYBodyRot(180F);
            level.addFreshEntity(h);
        }
    }
}
