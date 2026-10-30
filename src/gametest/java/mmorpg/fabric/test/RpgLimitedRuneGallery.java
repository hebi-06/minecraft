package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import mmorpg.core.Runes;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.rune.Breakthrough;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneSystem;
import mmorpg.fabric.rune.SkillBar;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Status;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.16.0 한정 초월 룬 사진첩 (화면 시험 — 키 limited): 평평한 세계에서 과녁 허스크 여섯 (12 ~ 15칸 앞 — 하늘의 별자리 · 눈이 화면에 들어오게) 앞에서
 * 룬마다 실제 입력 (스킬 칸 1번의 키) 으로 쓰고 단계마다 사진 (lr_&lt;계열&gt;_&lt;돌&gt;_&lt;틱&gt;[_3p]): 0돌 (1인칭) · 6돌 (3인칭 — 전용 연출 finale).
 * 천공의 눈은 광선 동안 몸을 천천히 돌려 광선이 따라가는지 (lr_skyeye_0_*). 칠성 강림 6돌은 밤에도 한 번 (lr_dipper_6n_*).
 * 0.17.0 무간참역 (쓴 자리 영역 — 과녁 가까이 z = 1.5 에 섬): 0돌 1인칭 · 6돌 3인칭 (하늘 가르기) · 6돌 밤 1인칭 (lr_endless_…).
 * 0.18.0 대지 가르기 (앞쪽 균열 — z = -3 에 서서 과녁이 균열 안 · 대검은 머리 위에서 앞으로 휘둘러 과녁 앞에 박힘): 0돌 1인칭 · 6돌 3인칭 (지맥 폭발) ·
 * 6돌 밤 1인칭 (lr_terra_…),
 * 표시 엔티티 (대검 · 들린 땅 · 기둥) 가 떴는지 (FxDisplays.spawned) 도 기록.
 * 0.19.0 세계수의 가호 (버프 — 앞 12칸 · 왼쪽 6칸에 세계수 환영이 자람 · 금빛 빛줄기가 나에게): 0돌 1인칭 (가호 뒤 과녁을 치고 (금빛 잎) · 맞음 (비췻빛 고리)
 * → 가호가 끝난 뒤까지) · 6돌 3인칭 (세계수 개화 — 큰 나무 · 꽃잎 비) · 6돌 밤 1인칭 (lr_ygg_…).
 * 0.20.0 심연의 낙인 (적 약화 — 조준한 과녁 무리에 심연이 열리고 사슬 · 머리 위 낙인): 0돌 1인칭 (낙인 뒤 과녁을 치고 (◆ 숫자) · 맞음 (어두운 금)
 * → 낙인이 끝난 뒤까지) · 6돌 3인칭 (심연의 문) · 6돌 밤 1인칭 (lr_abyss_…).
 * 써졌는지 (재사용 대기) · 들어간 피해를 로그 (limited-gallery) 로, 아니면 마지막에 실패. 0.20.1 부터 끝에 띄우던 총 피해 · 요약 글자는 없음
 * (사용자 "체감으로만, 거추장스럽다") — 룬마다 마지막 사진은 글자가 뜨지 않는지 보는 자리.
 * 스탯은 40렙 실제 분배 (31 · 31 · 31 · 32 = 총 125). 피해 숫자는 실제로 깎인 체력이라 과녁 체력 상한 (바닐라 1024) 에서 잘리므로,
 * 이 사진첩의 과녁은 흡수 체력 2048 을 매 틱 다시 채움 (6돌 큰 한 방 약 1,800 도 그대로 보이게 — 공용 TestDummies 는 그대로).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgLimitedRuneGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-limited-gallery");
    /** 과녁: 앞쪽 무리 (평평한 세계 땅 y = -60) — 나는 z = -7 에서 북쪽 (z +) 을 봄 */
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 6.5), new Vec3(-1.5, -60, 7.5), new Vec3(2.5, -60, 7.0), new Vec3(0.5, -60, 9.0), new Vec3(-3.0, -60, 5.0),
        new Vec3(3.5, -60, 5.0)
    };
    private static final double ME_Z = -7.0;
    /** 과녁의 흡수 체력 (바닐라 상한 2048 — 체력 1024 와 합쳐 한 방 3072 까지 숫자가 잘리지 않음) */
    private static final float BUFFER = 2048F;
    private static final List<Husk> BUFFERED = new CopyOnWriteArrayList<>();
    private static boolean tickInstalled;
    private int lives;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active()) return;
        boolean film = RpgFilm.on() && RpgFilm.listed("filmlimited"); // 0.48.2 룬 영상 (only=film,filmlimited) — 사진 대신 틱마다
        if (!film && (RpgFilm.on() || !RpgProdSmoke.only("limited"))) return;
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
            // 40렙 실제 분배 (총 125 — 한정 룬의 적용 스탯 = 네 스탯의 합)
            for (String s : List.of("str 31", "dex 31", "vit 31", "int 32")) server.runCommand("execute as @a run rpg set " + s);
            server.runOnServer(s -> installBuffer());
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            if (film) {
                RpgFilm f = new RpgFilm(ctx).view(9.0, 4.2, 2.6).angle(105).follow(0.6);
                for (String family : List.of("arcana", "dipper", "skyeye", "endless", "terra", "yggdrasil", "abyss", "glacier")) film(ctx, server, family, f);
                LOG.info("[limited-gallery] film done, {} failed", failures.size());
                if (!failures.isEmpty()) throw new AssertionError("limited film: " + String.join(" / ", failures));
                return;
            }
            // 만상귀일: 구슬이 몸 둘레 → 조준한 곳 위 → 빛줄기 넷 (16 · 26 · 36 · 46틱 쯤 — 스탯 고르게) → 모임 (64) → 귀일 폭발 (70) → 끝 (글자 없음)
            run(ctx, server, "arcana", 0, false, new int[] {8, 26, 36, 56, 66, 72, 82, 98}, null, false);
            run(ctx, server, "arcana", 6, true, new int[] {30, 72, 80, 90, 100, 114}, null, false);
            // 칠성 강림: 별이 켜짐 (0 ~ 18틱) → 별빛 기둥 7 (24 ~ 84틱) → 접힘 (92) → 큰 별 (98) → 떨어짐 (106) → 끝 (글자 없음)
            run(ctx, server, "dipper", 0, false, new int[] {12, 26, 50, 94, 100, 108, 116, 132}, null, false);
            run(ctx, server, "dipper", 6, true, new int[] {30, 104, 118, 126, 136, 150, 162}, null, false);
            // 천공의 눈: 눈을 뜸 (0 ~ 20틱) → 광선 6번 (20 ~ 70틱 — 30틱부터 몸을 천천히 오른쪽으로 돌림) → 마무리 폭발 (80) → 끝 (글자 없음)
            run(ctx, server, "skyeye", 0, false, new int[] {10, 24, 44, 66, 82, 90, 106}, "turn", false);
            run(ctx, server, "skyeye", 6, true, new int[] {12, 40, 70, 92, 100, 116}, null, false);
            // 밤: 칠성 강림 6돌 (별 · 별똥별 비)
            server.runCommand("time set 18000");
            run(ctx, server, "dipper", 6, false, new int[] {40, 118, 130, 145, 160}, null, true);
            server.runCommand("time set 6000");
            ctx.waitTicks(100); // 앞 시전의 6돌 하늘빛이 다 걷힌 뒤 (무간참역 0돌 사진에 남지 않게)
            // 무간참역 (0.17.0): 영역 전개 (0 ~ 16틱 — 검이 떨어져 꽂힘) → 베기 0.5초마다 (16 ~ 106틱) → 검이 모임 (110) → 일섬 (118 — 0.15초에 그어짐)
            //   → 끝 (글자 없음). 사진 틱은 클라이언트 기준 (사진 찍는 동안 서버가 앞서 — 뒤 사진일수록 서버가 몇 틱 앞)
            run(ctx, server, "endless", 0, false, new int[] {12, 30, 62, 100, 106, 110, 114, 124, 132, 142}, null, false, 1.5, 175);
            run(ctx, server, "endless", 6, true, new int[] {24, 80, 140, 148, 152, 157, 164, 172, 180, 196}, null, false, 1.5, 215);
            server.runCommand("time set 18000");
            run(ctx, server, "endless", 6, false, new int[] {12, 70, 150, 155, 162, 176, 190}, null, true, 1.5, 215);
            server.runCommand("time set 6000");
            ctx.waitTicks(100);
            // 대지 가르기 (0.18.0): 머리 위에 대검이 생김 (2 ~ 8틱) → 뒤로 젖힘 (9 ~ 19) → 앞으로 휘둘러 내려옴 (19 ~ 24 — 화면에는 마지막 두 틱)
            //   → 박힘 · 칼 아래 줄이 갈라지고 앞으로 끝까지 (24 ~ 29) → 대검이 부서짐 (36 ~ 41) → 여진 0.5초마다 (40 ~ 94) → 끝 (104 — 글자 없음).
            //   6돌: 여진 8번 (~114) · 지맥 폭발 (116 ~ 150). 서버가 사진보다 2 ~ 3틱 앞서므로 휘두르는 사진은 19 ~ 22
            int fx0 = server.computeOnServer(s -> FxDisplays.spawned);
            run(ctx, server, "terra", 0, false, new int[] {8, 16, 19, 20, 21, 22, 26, 32, 44, 70, 104}, null, false, -3.0, 150);
            run(ctx, server, "terra", 6, true, new int[] {14, 19, 20, 21, 22, 27, 34, 60, 112, 130, 156}, null, false, -3.0, 185);
            server.runCommand("time set 18000");
            run(ctx, server, "terra", 6, false, new int[] {16, 20, 21, 23, 50, 130, 156}, null, true, -3.0, 185);
            server.runCommand("time set 6000");
            int fx1 = server.computeOnServer(s -> FxDisplays.spawned);
            LOG.info("[limited-gallery] terra displays spawned={}", fx1 - fx0);
            if (fx1 - fx0 < 3 * 20) failures.add("terra displays " + (fx1 - fx0));
            ctx.waitTicks(100);
            // 세계수의 가호 (0.19.0): 씨앗 → 돌며 자람 (2 ~ 18틱) → 가호 (18 — 잎 지붕이 피고 금빛 빛줄기가 나에게 · 몸에 가호) → 서 있음 (~88) →
            //   오그라들며 흩어짐 (88 ~ 102) → 과녁을 침 (110 ~ 140 — 가호를 받은 맞힘: 금빛 잎) · 맞음 (126 · 142 — 막아 줌: 비췻빛 고리) →
            //   가호 끝 (258 — 잎이 흩어짐, 요약 글자 없음 — 0.20.1). 6돌: 큰 나무 · 꽃잎 비 · 땅의 빛의 꽃
            runGrace(ctx, server, 0, false, new int[] {4, 10, 15, 19, 23, 30, 60, 92, 100, 114, 128, 144, 268}, false, true);
            runGrace(ctx, server, 6, true, new int[] {6, 14, 19, 24, 32, 60, 100, 110}, false, false);
            server.runCommand("time set 18000");
            runGrace(ctx, server, 6, false, new int[] {12, 20, 28, 60, 100}, true, false);
            server.runCommand("time set 6000");
            ctx.waitTicks(60);
            // 심연의 낙인 (0.20.0): 손에서 심연의 빛 → 조준한 곳에 심연이 열림 (0 ~ 14틱 — 검은 바닥 · 문양 · 검은 손) → 낙인 (14 — 사슬 · 머리 위 낙인 · 낙인 ×N)
            //   → 과녁을 침 (60 ~ 96 — ◆ 숫자 · 1.2배) · 맞음 (104 · 118 — 0.8배 · 어두운 금) → 낙인 끝 (214 — 분홍 조각, 요약 글자 없음 — 0.20.1)
            runBrand(ctx, server, 0, false, new int[] {4, 10, 14, 17, 22, 40, 66, 90, 106, 120, 222}, false, true);
            runBrand(ctx, server, 6, true, new int[] {6, 12, 17, 24, 40, 80, 120}, false, false);
            server.runCommand("time set 18000");
            runBrand(ctx, server, 6, false, new int[] {10, 17, 30, 70}, true, false);
            server.runCommand("time set 6000");
        }
        LOG.info("[limited-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("limited rune gallery: " + String.join(" / ", failures));
    }

    /** 0.48.2 룬 영상: 한정 초월 룬 (0돌) 하나를 같은 입력으로 쓰고 틱마다 한 장 — 새 피해가 30틱 없으면 끝 (RpgFilm) */
    private void film(ClientGameTestContext ctx, TestServerContext server, String family, RpgFilm f) {
        Runes.Rune r = Runes.parseRune(Runes.runeId(family, "transcend"));
        if (r == null) {
            failures.add(family + " no rune (film)");
            return;
        }
        double meZ = family.equals("endless") ? 1.5 : family.equals("terra") ? -3.0 : ME_Z;
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand("time set 6000");
        server.runCommand(String.format(Locale.ROOT, "tp @a 0.5 -60 %.1f 0 4", meZ));
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
            SkillBar.set(sp, 0, Breakthrough.stack(family, "transcend", 0));
        });
        ctx.waitTicks(14);
        // 보는 곳: 몸과 과녁 무리 (z 5 ~ 9) 사이 — 멀리 선 룬 (z -7) 도 과녁 쪽 효과가 화면 가운데에
        f.begin(server, family + "_transcend", r.def().str("name"), "한정 초월 룬 · 0돌", Math.max(3.0, Math.min(8.0, (7.0 - meZ) * 0.55)));
        f.frames(4);
        f.press(MmorpgClient.skillKeys[0]);
        java.util.function.DoubleSupplier dealt = () -> server.computeOnServer(s -> {
            double sum = 0;
            for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 6, 0.5, -60, 6).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        f.untilQuiet(dealt, 110, 30, 240);
        f.end(dealt.getAsDouble());
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        if (!cast) failures.add(family + " (film) not cast");
        ctx.waitTicks(60); // 앞 룬의 하늘빛이 걷히게
    }

    /** 룬 하나를 실제 입력으로 쓰고 사진 · 피해 기록. move = "turn" 이면 30틱부터 2틱마다 몸을 1° 씩 오른쪽으로 */
    private void run(ClientGameTestContext ctx, TestServerContext server, String family, int level, boolean third, int[] shots, String move, boolean night) {
        run(ctx, server, family, level, third, shots, move, night, ME_Z, 170);
    }

    /** meZ = 서는 자리 (z), end = 기다리는 틱 (쓴 뒤) */
    private void run(ClientGameTestContext ctx, TestServerContext server, String family, int level, boolean third, int[] shots, String move, boolean night,
            double meZ, int end) {
        Runes.Rune r = Runes.parseRune(Runes.runeId(family, "transcend"));
        if (r == null) {
            failures.add(family + " no rune");
            return;
        }
        String id = family + "_" + level + (night ? "n" : "");
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand(String.format(Locale.ROOT, "tp @a 0.5 -60 %.1f 0 4", meZ));
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
            SkillBar.set(sp, 0, Breakthrough.stack(family, "transcend", level)); // 스킬 칸 1번 (키 R) — 돌파 단계 그대로
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(14);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        int t = 0;
        float yaw = 0;
        for (int shot : shots) {
            while (t < shot) {
                ctx.waitTick();
                t++;
                if ("turn".equals(move) && t >= 30 && t <= 70 && t % 2 == 0) {
                    yaw += 1F;
                    server.runCommand(String.format(Locale.ROOT, "tp @a 0.5 -60 %.1f %.1f 4", meZ, yaw));
                }
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "lr_%s_%03d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        if (end > t) ctx.waitTicks(end - t);
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        double dealt = server.computeOnServer(s -> {
            double sum = 0;
            for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 6, 0.5, -60, 6).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        LOG.info("[limited-gallery] {} {} cast={} dealt={}", id, third ? "3p" : "1p", cast, String.format(Locale.ROOT, "%.1f", dealt));
        if (!cast) failures.add(id + " not cast");
        else if (!(dealt > 0)) failures.add(id + " dealt no damage");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /**
     * 세계수의 가호 하나: 실제 입력으로 쓰고 사진 (lr_ygg_&lt;돌&gt;[n]_&lt;틱&gt;[_3p]). full = 가호 뒤 과녁을 치고 (110틱부터 6틱마다 하나 — 룬 피해) 맞고 (126 · 142틱)
     * 가호가 끝난 뒤까지 (끝 사진 — 요약 글자가 없는지). 써졌는지 · 세계수가 떴는지 · 가호가 걸렸는지 (가하는 피해 배율) 를 로그로
     */
    private void runGrace(ClientGameTestContext ctx, TestServerContext server, int level, boolean third, int[] shots, boolean night, boolean full) {
        Runes.Rune r = Runes.parseRune(Runes.runeId("yggdrasil", "transcend"));
        if (r == null) {
            failures.add("yggdrasil no rune");
            return;
        }
        String id = "ygg_" + level + (night ? "n" : "");
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand(String.format(Locale.ROOT, "tp @a 0.5 -60 %.1f 0 4", ME_Z));
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
            SkillBar.set(sp, 0, Breakthrough.stack("yggdrasil", "transcend", level));
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(14);
        int fx0 = server.computeOnServer(s -> FxDisplays.spawned);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        int t = 0;
        double mult = 1;
        int hit = 0;
        for (int shot : shots) {
            while (t < shot) {
                ctx.waitTick();
                t++;
                if (t == 30) mult = server.computeOnServer(s -> RpgCombat.dealtMult.applyAsDouble(player(s)));
                if (full && t >= 110 && t <= 140 && (t - 110) % 6 == 0) {
                    final int k = hit++;
                    server.runOnServer(s -> {
                        ServerPlayer sp = player(s);
                        List<Husk> hs = sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(40), h -> true);
                        if (!hs.isEmpty()) RuneKit.runeHit(sp, hs.get(k % hs.size()), RuneCast.power(sp, "yggdrasil"), 3, "int");
                    });
                }
                if (full && (t == 126 || t == 142)) {
                    server.runOnServer(s -> {
                        ServerPlayer sp = player(s);
                        List<Husk> hs = sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(40), h -> true);
                        sp.setInvulnerableTime(0);
                        sp.hurtServer(sp.level(), hs.isEmpty() ? sp.damageSources().generic() : sp.damageSources().mobAttack(hs.get(0)), 8F);
                    });
                }
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "lr_%s_%03d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        int displays = server.computeOnServer(s -> FxDisplays.spawned) - fx0;
        LOG.info("[limited-gallery] {} {} cast={} displays={} dealtMult={}", id, third ? "3p" : "1p", cast, displays, String.format(Locale.ROOT, "%.3f", mult));
        if (!cast) failures.add(id + " not cast");
        if (displays < 1) failures.add(id + " no world tree");
        if (!(mult > 1.1)) failures.add(id + " no grace (dealt ×" + mult + ")");
        if (!full) ctx.waitTicks(Math.max(0, 140 - t)); // 나무가 다 흩어진 뒤 (다음 시전의 사진에 남지 않게)
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /**
     * 심연의 낙인 하나: 실제 입력으로 쓰고 사진 (lr_abyss_&lt;돌&gt;[n]_&lt;틱&gt;[_3p]). 과녁 무리 가운데 (0.5, 6.5) 를 조준. full = 낙인 뒤 과녁을 치고
     * (60틱부터 6틱마다 하나 — 룬 피해) 맞고 (104 · 118틱) 낙인이 끝난 뒤까지 (끝 사진 — 요약 글자가 없는지). 써졌는지 · 낙인 찍힌 과녁 수 · 맞는 쪽 배율을 로그로
     */
    private void runBrand(ClientGameTestContext ctx, TestServerContext server, int level, boolean third, int[] shots, boolean night, boolean full) {
        Runes.Rune r = Runes.parseRune(Runes.runeId("abyss", "transcend"));
        if (r == null) {
            failures.add("abyss no rune");
            return;
        }
        String id = "abyss_" + level + (night ? "n" : "");
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand(String.format(Locale.ROOT, "tp @a 0.5 -60 %.1f 0 4", ME_Z));
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
            SkillBar.set(sp, 0, Breakthrough.stack("abyss", "transcend", level));
            sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, -59.0, 6.5));
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(14);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        int t = 0;
        double mult = 1;
        int marked = 0;
        int hit = 0;
        for (int shot : shots) {
            while (t < shot) {
                ctx.waitTick();
                t++;
                if (t == 30) {
                    mult = server.computeOnServer(s -> {
                        double m = 1;
                        for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, player(s).getBoundingBox().inflate(40), h -> true)) m = Math.max(m, RpgCombat.takenMult.applyAsDouble(h));
                        return m;
                    });
                    marked = server.computeOnServer(s -> {
                        int n = 0;
                        for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, player(s).getBoundingBox().inflate(40), h -> true)) if (RpgCombat.takenMult.applyAsDouble(h) > 1) n++;
                        return n;
                    });
                }
                if (full && t >= 60 && t <= 96 && (t - 60) % 6 == 0) {
                    final int k = hit++;
                    server.runOnServer(s -> {
                        ServerPlayer sp = player(s);
                        List<Husk> hs = sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(40), h -> true);
                        if (!hs.isEmpty()) RuneKit.runeHit(sp, hs.get(k % hs.size()), RuneCast.power(sp, "abyss"), 3, "int");
                    });
                }
                if (full && (t == 104 || t == 118)) {
                    server.runOnServer(s -> {
                        ServerPlayer sp = player(s);
                        List<Husk> hs = sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(40), h -> true);
                        sp.setInvulnerableTime(0);
                        if (!hs.isEmpty()) sp.hurtServer(sp.level(), sp.damageSources().mobAttack(hs.get(0)), 8F);
                    });
                }
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "lr_%s_%03d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        LOG.info("[limited-gallery] {} {} cast={} marked={} takenMult={}", id, third ? "3p" : "1p", cast, marked, String.format(Locale.ROOT, "%.3f", mult));
        if (!cast) failures.add(id + " not cast");
        if (marked < 4) failures.add(id + " marked " + marked);
        if (!(mult > 1.1)) failures.add(id + " no brand (taken ×" + mult + ")");
        if (!full) ctx.waitTicks(Math.max(0, 140 - t));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 서버 틱마다 과녁의 흡수 체력을 다시 채움 (서버 스레드에서 한 번) */
    private static synchronized void installBuffer() {
        if (tickInstalled) return;
        tickInstalled = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Husk h : BUFFERED) {
                if (h.isRemoved()) BUFFERED.remove(h);
                else if (h.getAbsorptionAmount() < BUFFER) h.setAbsorptionAmount(BUFFER);
            }
        });
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt) · 흡수 체력 2048 (매 틱 채움 — 큰 피해 숫자가 잘리지 않게) */
    private static void spawnHusks(ServerLevel level) {
        TestDummies.reset();
        BUFFERED.clear();
        for (Vec3 at : TARGETS) {
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (h == null) continue;
            TestDummies.make(h);
            if (h.getAttribute(Attributes.MAX_ABSORPTION) != null) h.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(BUFFER);
            h.setAbsorptionAmount(BUFFER);
            BUFFERED.add(h);
            h.snapTo(at.x, at.y, at.z, 180F, 0F);
            h.setYHeadRot(180F);
            h.setYBodyRot(180F);
            level.addFreshEntity(h);
        }
    }
}
