package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.Runes;
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
 * 0.25.0 두 스탯 룬 계열 여섯 (해류 · 골렘 · 무투 · 사냥꾼 · 화산 · 투사 — 30종) 사진첩 (화면 시험, 사진첩 키 pairs): RpgRuneGallery 와 같은 길
 * (평평한 세계 · 과녁 허스크 여섯 · 스킬 칸 1번에 올리고 그 칸의 키로 씀) 으로 룬마다 그 룬의 볼거리 틱에 사진 (pr_&lt;계열&gt;_&lt;등급&gt;_&lt;틱&gt;[_3p]) —
 * 초월 룬은 3인칭으로 한 번 더. 룬마다 써졌는지 · 들어간 피해 · 띄운 블록 표시 수를 로그 (pair-gallery) 로, 써지지 않았거나 피해가 0 이거나
 * 가장 많이 맞은 과녁 하나의 피해가 P × 총 배율 (±1%) 이 아니면 마지막에 실패.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgPairRuneGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-pair-gallery");
    /** 과녁: 앞쪽 무리 넷 (5~8칸) + 앞 양옆 둘 (2.9칸) — 평평한 세계 땅 y = -60 (RpgRuneGallery 와 같음) */
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 5.5), new Vec3(-1.0, -60, 6.5), new Vec3(2.0, -60, 6.0), new Vec3(0.5, -60, 8.0), new Vec3(-1.8, -60, 2.2),
        new Vec3(2.8, -60, 2.2)
    };
    /** 룬마다 사진 틱 (1인칭) · 초월 룬의 3인칭 사진 틱 — 그 룬의 볼거리 (솟음 · 타격 · 마무리) */
    private static final Map<String, int[]> SHOTS = Map.ofEntries(
            Map.entry("tideLance", new int[] {4}), Map.entry("healingRain", new int[] {14, 36}), Map.entry("maelstrom", new int[] {12, 62}),
            Map.entry("tidalWave", new int[] {6, 14, 27}), Map.entry("leviathan", new int[] {10, 30, 60, 84, 90}),
            Map.entry("stoneFist", new int[] {8}), Map.entry("stoneWard", new int[] {5, 24}), Map.entry("petrify", new int[] {10, 28, 38}),
            Map.entry("golemSummon", new int[] {10, 18, 34, 72}), Map.entry("colossus", new int[] {16, 30, 74, 94}),
            Map.entry("chainFist", new int[] {2, 31}), Map.entry("flyingKick", new int[] {3, 13}), Map.entry("palmBlast", new int[] {6, 12}),
            Map.entry("hundredFists", new int[] {10, 52}), Map.entry("heavenFist", new int[] {12, 40, 84, 88}),
            Map.entry("steelTrap", new int[] {5, 9}), Map.entry("falconStrike", new int[] {4, 14}), Map.entry("huntNet", new int[] {4, 8, 31}),
            Map.entry("wolfPack", new int[] {4, 23, 54}), Map.entry("beastKing", new int[] {12, 41, 85}),
            Map.entry("magmaSlam", new int[] {3}), Map.entry("lavaBombs", new int[] {6, 9, 16, 20, 29}), Map.entry("magmaGeyser", new int[] {8, 28}),
            Map.entry("magmaField", new int[] {8, 30, 42}), Map.entry("grandEruption", new int[] {14, 40, 92, 100}),
            Map.entry("chainHook", new int[] {3, 6}), Map.entry("shieldRush", new int[] {2, 13}), Map.entry("chainWhirl", new int[] {12, 41}),
            Map.entry("arenaDomain", new int[] {12, 50, 86}), Map.entry("warChains", new int[] {10, 30, 80, 84}));
    private static final Map<String, int[]> SHOTS_3P = Map.of("leviathan", new int[] {26, 88}, "colossus", new int[] {44, 92}, "heavenFist", new int[] {30, 88},
            "beastKing", new int[] {30, 86}, "grandEruption", new int[] {50, 93}, "warChains", new int[] {40, 84});
    /** 룬 사이에 삶 표시를 넘긴 횟수 (앞 룬의 남은 연출 · 타격이 다음 룬에 섞이지 않게) */
    private int lives;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active()) return;
        boolean film = RpgFilm.on() && (RpgFilm.listed("filmpair") || RpgFilm.listed("filmtr")); // 0.48.2 룬 영상 (only=film,filmpair · 초월만 filmtr) — 사진 대신 틱마다
        boolean trOnly = film && !RpgFilm.listed("filmpair");
        if (!film && (RpgFilm.on() || !RpgProdSmoke.only("pairs"))) return;
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
            RpgFilm f = film ? new RpgFilm(ctx).angle(105).follow(0.6) : null;
            for (String family : Runes.PAIR_FAMILIES) {
                for (String grade : trOnly ? List.of("transcend") : Runes.GRADE_ORDER) {
                    Runes.Rune r = Runes.parseRune(Runes.runeId(family, grade));
                    if (r == null) {
                        failures.add(family + "_" + grade + " missing");
                        continue;
                    }
                    if (f != null) {
                        film(ctx, server, r, f);
                        continue;
                    }
                    run(ctx, server, r, false);
                    if (grade.equals("transcend")) run(ctx, server, r, true);
                }
            }
        }
        LOG.info("[pair-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("pair rune gallery: " + String.join(" / ", failures));
    }

    /** 화상이 마지막 타격 뒤에 이어지는 룬 (화산) — 끝까지 기다릴 틱 (로그의 피해가 P × 총 배율이 되게) */
    private static final Map<String, Integer> WAIT = Map.of("magmaSlam", 72, "lavaBombs", 80, "magmaGeyser", 125);

    /** 끝까지 기다릴 틱 (마무리 · 화상 · 무너짐까지) */
    private static int total(Runes.Rune r) {
        Integer w = WAIT.get(r.def().str("fn"));
        if (w != null) return w;
        return switch (r.grade()) {
            case "transcend" -> 130;
            case "hero" -> 100;
            case "noble" -> 80;
            case "rare" -> 60;
            default -> 50;
        };
    }

    /** 0.48.2 룬 영상: 같은 입력 · 과녁으로 쓰고 틱마다 한 장 — 새 피해가 22틱 없으면 끝 (화상까지, RpgFilm) */
    private void film(ClientGameTestContext ctx, TestServerContext server, Runes.Rune r, RpgFilm f) {
        String id = r.family() + "_" + r.grade();
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand("time set 6000");
        server.runCommand("tp @a 0.5 -60 0.5 0 8");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (Husk h : sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(48), h -> true)) h.discard();
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
            SkillBar.set(sp, 0, RuneItems.stack(r.family(), r.grade()));
        });
        ctx.waitTicks(12);
        boolean big = r.grade().equals("hero") || r.grade().equals("transcend");
        f.view(big ? 7.6 : 7.0, big ? 4.2 : 3.6, big ? 1.9 : 1.1); // 0.48.2 룬 영상: 옆 뒤 (105°) 에서 높게 — 과녁을 뚫고 지나간 몸이 과녁 무리에 가리지 않게
        f.begin(server, id, r.def().str("name"), r.famName() + " 계열 · " + r.gradeName() + " 룬", 3.0);
        f.frames(4);
        f.press(MmorpgClient.skillKeys[0]);
        java.util.function.DoubleSupplier dealt = () -> server.computeOnServer(s -> {
            double sum = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        f.untilQuiet(dealt, big ? 50 : 34, 22, 160);
        f.end(dealt.getAsDouble());
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        if (!cast) failures.add(id + " (film) not cast");
    }

    /** 룬 하나를 실제 입력으로 쓰고 사진 · 피해 기록 */
    private void run(ClientGameTestContext ctx, TestServerContext server, Runes.Rune r, boolean third) {
        String id = r.family() + "_" + r.grade();
        String fn = r.def().str("fn");
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand("time set 6000");
        server.runCommand("tp @a 0.5 -60 0.5 0 8");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (Husk h : sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(48), h -> true)) h.discard();
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
            SkillBar.set(sp, 0, RuneItems.stack(r.family(), r.grade()));
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(12);
        int displays0 = server.computeOnServer(s -> mmorpg.fabric.FxDisplays.spawned);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        int t = 0;
        int[] shots = third ? SHOTS_3P.getOrDefault(fn, new int[] {8}) : SHOTS.getOrDefault(fn, new int[] {6});
        for (int shot : shots) {
            if (shot > t) {
                ctx.waitTicks(shot - t);
                t = shot;
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "pr_%s_%02d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        int total = total(r);
        if (total > t) ctx.waitTicks(total - t);
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        double[] dealt = server.computeOnServer(s -> {
            double sum = 0;
            double first = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(40), h -> true)) {
                double d = TestDummies.dealt(h);
                sum += d;
                first = Math.max(first, d);
            }
            return new double[] {sum, first};
        });
        double P = server.computeOnServer(s -> RuneCast.power(player(s), r.family())) * Runes.spreadMult(r); // 0.26.0 지속 보정
        int displays = server.computeOnServer(s -> mmorpg.fabric.FxDisplays.spawned) - displays0;
        LOG.info("[pair-gallery] {} {} {} cast={} dealt={} maxOne={} (P×total {}) displays={}", id, fn, third ? "3p" : "1p", cast,
                String.format(Locale.ROOT, "%.1f", dealt[0]), String.format(Locale.ROOT, "%.1f", dealt[1]),
                String.format(Locale.ROOT, "%.1f", P * Runes.runeTotalMult(r.def())), displays);
        double want = P * Runes.runeTotalMult(r.def());
        if (!cast) failures.add(id + (third ? " (3p)" : "") + " not cast");
        else if (!(dealt[0] > 0)) failures.add(id + (third ? " (3p)" : "") + " dealt no damage");
        // 가장 많이 맞은 과녁 하나 = P × 총 배율 (같은 적을 여러 번 · 화상 · 마무리가 모두 들어감 — 실제 입력 · 클라이언트에서도)
        else if (Math.abs(dealt[1] - want) > want * 0.01) failures.add(id + (third ? " (3p)" : "") + " one target " + Math.round(dealt[1]) + " vs " + Math.round(want));
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
