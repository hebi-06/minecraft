package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * 초월 무기 스킬 사진첩 (화면 시험): 평평한 세계에서 과녁 허스크 여섯 앞에서 14 스킬을 실제 입력 (우클릭 · 웅크리고 우클릭 · 길게 우클릭) 으로 쓰고
 * 스킬마다 정한 순간에 사진 (sk_&lt;스킬&gt;_&lt;틱&gt;[_3p]). 스킬 2 는 3인칭으로 한 번 더. 스킬마다 써졌는지 (재사용 대기 시작) · 들어간 피해를 로그 (skill-gallery) 로,
 * 써지지 않았거나 피해가 0 이면 마지막에 실패.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgSkillGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-skill-gallery");
    /** 과녁: 앞쪽 무리 넷 (7~10칸) + 옆 둘 (4칸) — 평평한 세계 땅 y = -60 */
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 7.5), new Vec3(-1.0, -60, 8.5), new Vec3(2.0, -60, 8.0), new Vec3(0.5, -60, 10.0), new Vec3(-3.5, -60, 4.5),
        new Vec3(4.0, -60, 5.0)
    };

    /** 스킬 하나: 무기 종류 · 스킬 번호 · 사진 찍을 틱 (쓴 뒤) · 끝까지 기다릴 틱 */
    private record Case(String type, int index, int[] shots, int total) {}

    private static final Case[] CASES = {
        new Case("sword", 0, new int[] {2, 11, 16}, 30),
        new Case("sword", 1, new int[] {6, 14, 21, 31}, 50),
        new Case("dagger", 0, new int[] {3, 9, 24}, 40),
        new Case("dagger", 1, new int[] {10, 24, 36, 48}, 64),
        new Case("axe", 0, new int[] {4, 12, 22}, 40),
        new Case("axe", 1, new int[] {12, 26, 32, 46}, 70),
        new Case("shield", 0, new int[] {3, 10, 24}, 40),
        new Case("shield", 1, new int[] {12, 50, 86, 96}, 110),
        new Case("bow", 0, new int[] {5, 11, 22}, 36),
        new Case("bow", 1, new int[] {12, 28, 46, 60}, 76),
        new Case("wand", 0, new int[] {8, 22, 38}, 60),
        new Case("wand", 1, new int[] {14, 32, 52, 62}, 78),
        new Case("greatsword", 0, new int[] {6, 14, 24}, 40),
        new Case("greatsword", 1, new int[] {12, 26, 54, 66}, 84),
    };

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active()) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("xp set @a 40 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(10);
            for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
            int slot = 0;
            String lastType = "";
            for (Case c : CASES) {
                if (!c.type().equals(lastType)) {
                    // 무기마다 칸 하나 (같은 무기 스킬 2 는 같은 칸)
                    slot = slot % 9;
                    server.runCommand("item replace entity @a hotbar." + slot + " with rpg:" + c.type() + "_l40_transcend");
                    int sel = slot;
                    ctx.getInput().pressKey(options -> options.keyHotbarSlots[sel]);
                    slot++;
                    lastType = c.type();
                }
                run(ctx, server, c, false);
                if (c.index() == 1) run(ctx, server, c, true);
            }
        }
        LOG.info("[skill-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("skill gallery: " + String.join(" / ", failures));
    }

    /** 스킬 하나를 실제 입력으로 쓰고 사진 · 피해 기록 */
    private void run(ClientGameTestContext ctx, TestServerContext server, Case c, boolean third) {
        Weapon w = Weapons.parseWeapon("rpg:" + c.type() + "_l40_transcend");
        Obj k = w.skills().get(c.index());
        String id = k.str("id");
        // 자리 · 과녁 · 체력 · 재사용 대기 처음으로
        server.runCommand("time set 6000");
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (Husk h : sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(48), h -> true)) h.discard();
            Transcend.resetCooldowns(sp);
            RpgHealth.healFull(sp);
            spawnHusks(sp.level());
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(12);
        boolean ranged = c.type().equals("bow") || c.type().equals("wand");
        // 입력: 근접 = 우클릭 (스킬 1) · 웅크리고 우클릭 (스킬 2), 활 · 완드 = 웅크리고 우클릭 (스킬 1) · 길게 우클릭 (스킬 2)
        boolean sneak = ranged ? c.index() == 0 : c.index() == 1;
        boolean hold = ranged && c.index() == 1;
        if (sneak) {
            ctx.getInput().holdShift();
            ctx.waitTicks(3);
        }
        int t = 0;
        if (hold) {
            ctx.getInput().holdKey(options -> options.keyUse);
            ctx.waitTicks(Transcend.HOLD_TICKS + 2);
            ctx.getInput().releaseKey(options -> options.keyUse);
            t = 2; // 스킬 2 는 누른 지 HOLD_TICKS 틱에 써짐 — 지금은 그 뒤 2틱
        } else {
            ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        }
        if (sneak) ctx.getInput().releaseShift();
        for (int shot : c.shots()) {
            if (third && c.shots().length > 2 && shot == c.shots()[0]) continue; // 3인칭은 뒤쪽 순간만
            if (shot > t) {
                ctx.waitTicks(shot - t);
                t = shot;
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "sk_%s_%02d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        if (c.total() > t) ctx.waitTicks(c.total() - t);
        boolean cast = server.computeOnServer(s -> Transcend.cooldownLeft(player(s), k) > 0);
        double dealt = server.computeOnServer(s -> {
            double sum = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 7, 0.5, -60, 7).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        LOG.info("[skill-gallery] {} {} cast={} dealt={}", id, third ? "3p" : "1p", cast, String.format(Locale.ROOT, "%.1f", dealt));
        if (!cast) failures.add(id + (third ? " (3p)" : "") + " not cast");
        else if (!(dealt > 0)) failures.add(id + (third ? " (3p)" : "") + " dealt no damage");
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt) */
    private static void spawnHusks(ServerLevel level) {
        TestDummies.reset();
        for (Vec3 at : TARGETS) {
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
