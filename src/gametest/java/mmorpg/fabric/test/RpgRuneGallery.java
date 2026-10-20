package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneSystem;
import mmorpg.fabric.rune.SkillBar;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Status;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 룬 85종 사진첩 (화면 시험): 평평한 세계에서 과녁 허스크 여섯 앞에서 룬마다 실제 입력 (스킬 칸 1번에 올리고 그 칸의 키 — 11단계부터 룬은 스킬 칸에서만)
 * 으로 쓰고 사진 (rn_&lt;계열&gt;_&lt;등급&gt;_&lt;틱&gt;[_3p]). 0.27.0: 사신 룬의 낫 조건을 뺌 — 먼저 맨손으로 눌러 쓰이는지 (재사용 대기 시작 ·
 * 스킬 칸이 어둡지 않음 — rn_reaper_bare).
 * 초월 룬은 3인칭으로 한 번 더. 룬마다 써졌는지 (재사용 대기 시작) · 들어간 피해를 로그 (rune-gallery) 로, 써지지 않았거나
 * 피해 룬인데 피해가 0 이면 마지막에 실패. 룬 사이에는 룬 기록 · 버프 · 보호막 · 쿨을 지움.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgRuneGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-rune-gallery");
    /** 과녁: 앞쪽 무리 넷 (5~8칸) + 앞 양옆 둘 (2.9칸 — 둘레 3.5칸 룬도 닿게) — 평평한 세계 땅 y = -60 */
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 5.5), new Vec3(-1.0, -60, 6.5), new Vec3(2.0, -60, 6.0), new Vec3(0.5, -60, 8.0), new Vec3(-1.8, -60, 2.2),
        new Vec3(2.8, -60, 2.2)
    };
    /** 룬 사이에 삶 표시를 넘긴 횟수 (앞 룬의 남은 연출 · 타격이 다음 룬에 섞이지 않게 — 늘 새 표시) */
    private int lives;
    /** 쓰면 피해가 바로 들지 않는 룬 (버프 · 회복 · 보호막 · 정령 — 피해 0 이어도 됨) */
    private static final Set<String> SUPPORT = Set.of("guardShout", "lifeSpring", "ironSkin", "lightBarrier", "purify", "fightingSpirit", "steelBody",
            "regenBody", "warGod", "awakening", "edgeHone", "flurry", "cleave", "bloodBlade", "swordGod", "summonSpirits", "counterStance", "backstep",
            "blink", "smokeBomb", "undyingPact", "berserker", "archangel");

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("rune")) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만 · 고른 시험만 (MMORPG_ONLY)
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
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            lockedCheck(ctx, server);
            for (String family : Runes.FAMILY_ORDER) {
                if (Runes.PAIR_FAMILIES.contains(family)) continue; // 0.25.0 두 스탯 계열은 RpgPairRuneGallery (사진첩 키 pairs)
                for (String grade : Runes.GRADE_ORDER) {
                    Runes.Rune r = Runes.parseRune(Runes.runeId(family, grade));
                    if (r == null) continue;
                    run(ctx, server, r, false);
                    if (grade.equals("transcend")) run(ctx, server, r, true);
                }
            }
        }
        LOG.info("[rune-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("rune gallery: " + String.join(" / ", failures));
    }

    /** 사진 찍을 틱 (쓴 뒤): 예고 (delay) 가 있으면 터지는 순간 쯤, 초월은 셋 */
    private static int[] shots(Runes.Rune r, boolean third) {
        Obj k = r.def();
        // 정령왕 강림 (12단계): 정령들 → 합체 (34틱) → 원소 넷 → 원소 붕괴 (97틱 쯤)
        if ("spiritKing".equals(k.str("fn"))) return third ? new int[] {62} : new int[] {8, 46, 98};
        // 적월 강림 (0.12.6): 달이 떠오름 (16틱) → 칼날 여섯 (16 ~ 70틱) → 달 추락 (86틱)
        if ("crimsonMoon".equals(k.str("fn"))) return third ? new int[] {44} : new int[] {12, 40, 88};
        int delay = (int) Math.round(k.numOr("delay", 0));
        boolean ult = r.grade().equals("transcend");
        int hit = delay > 0 ? delay + 3 : 6;
        if (third) return new int[] {hit + 6};
        if (ult) return new int[] {Math.max(4, delay / 2), hit, hit + 16};
        return new int[] {hit};
    }

    /** 끝까지 기다릴 틱 */
    private static int total(Runes.Rune r) {
        return switch (r.grade()) {
            case "transcend" -> 110;
            case "hero" -> 80;
            default -> 50;
        };
    }

    /** 룬 하나를 실제 입력으로 쓰고 사진 · 피해 기록 */
    private void run(ClientGameTestContext ctx, TestServerContext server, Runes.Rune r, boolean third) {
        String id = r.family() + "_" + r.grade();
        String fn = r.def().str("fn");
        // 무기 전용 계열이 있으면 그 무기를 듦 (40렙 일반 — 0.27.0 부터 없음: 맨손)
        String only = Runes.weaponOnly(r.family());
        server.runCommand("item replace entity @a hotbar.0 with " + (only != null ? Weapons.weaponId(only, 40, "common") : "air"));
        server.runCommand("time set 6000");
        server.runCommand("tp @a 0.5 -60 0.5 0 8");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (Husk h : sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(48), h -> true)) h.discard();
            // 앞 룬의 재사용 대기 (남아 있으면 키가 막힘) · 기록 · 버프 · 보호막 · 막기 · 상태를 지우고,
            // 삶 표시를 앞 룬들이 본 적 없는 값으로 (forget 이 0 으로 되돌리므로 그 뒤에 지금까지 넘긴 횟수 + 1 만큼)
            RuneCast.resetCooldowns(sp);
            RuneSystem.forget(sp.getUUID());
            lives++;
            for (int i = 0; i < lives; i++) RuneKit.lifeEnded(sp.getUUID());
            RpgStats.setBuff(sp, 0);
            Guards.clear(sp.getUUID());
            Status.clear(sp);
            sp.removeAllEffects();
            RpgHealth.healFull(sp);
            spawnHusks(sp.level());
            SkillBar.set(sp, 0, RuneItems.stack(r.family(), r.grade())); // 스킬 칸 1번 (키 R)
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(12);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        int t = 0;
        for (int shot : shots(r, third)) {
            if (shot > t) {
                ctx.waitTicks(shot - t);
                t = shot;
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "rn_%s_%02d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        int total = total(r);
        if (total > t) ctx.waitTicks(total - t);
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        double dealt = server.computeOnServer(s -> {
            double sum = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        LOG.info("[rune-gallery] {} {} {} cast={} dealt={}", id, fn, third ? "3p" : "1p", cast, String.format(Locale.ROOT, "%.1f", dealt));
        if (!cast) failures.add(id + (third ? " (3p)" : "") + " not cast");
        else if (!(dealt > 0) && !SUPPORT.contains(fn)) failures.add(id + (third ? " (3p)" : "") + " dealt no damage");
    }

    /** 0.27.0 사신 룬을 맨손으로 누름 (낫 조건을 뺌 — 사용자 "사신의 룬 조건인 낫을 없애고"): 쓰임 (재사용 대기 시작) · 스킬 칸이 잠김 표시가 아님 (사진 rn_reaper_bare) */
    private void lockedCheck(ClientGameTestContext ctx, TestServerContext server) {
        Runes.Rune r = Runes.parseRune(Runes.runeId("reaper", "common"));
        if (r == null) return;
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand("tp @a 0.5 -60 0.5 0 8");
        server.runOnServer(s -> {
            RuneCast.resetCooldowns(player(s));
            spawnHusks(player(s).level());
            SkillBar.set(player(s), 0, RuneItems.stack(r.family(), r.grade()));
        });
        ctx.waitTicks(12);
        boolean locked = ctx.computeOnClient(mc -> mmorpg.fabric.client.ClientSkillBar.locked(0));
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of("rn_reaper_bare").withSize(1280, 720));
        ctx.waitTicks(6);
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        LOG.info("[rune-gallery] reaper bare-handed (0.27.0 no scythe condition): hud locked={} cast={}", locked, cast);
        if (locked) failures.add("reaper rune shown locked bare-handed (0.27.0 no scythe condition)");
        if (!cast) failures.add("reaper rune not cast bare-handed (0.27.0 no scythe condition)");
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
