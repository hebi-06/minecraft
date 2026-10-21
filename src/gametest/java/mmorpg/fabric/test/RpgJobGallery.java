package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.core.Economy;
import mmorpg.core.Jobs;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.client.ClientJobBar;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobTrainer;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.job.JobTraitsMore;
import mmorpg.fabric.rune.RuneCast;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
 * 0.27.0 직업 사진첩 (화면 시험, only=job — 실제 키 · 마우스): 직업 교관 창 (카드 열하나 · 카드 설명 · 흐린 준비 중 카드 · 확인 창) → 전직 연출, 직업 칸 HUD
 * (직업 이름 그라데이션 · 잠긴 칸 · 검세 보석 · 태세 남은 시간), 검사 · 마검사 스킬 다섯 (Shift + 룬 칸 키, 1인칭 · 3인칭 — 정한 틱에 사진
 * job_&lt;스킬&gt;_&lt;틱&gt;[_3p]), 명경지수 · 마검 각성 뒤 평타 (왼쪽 클릭), 직업 바꾸기 확인 (300 은화), 스탯 창 「마력 검술」 줄.
 * 확인 항목은 로그 [job-gallery] OK / FAIL (써졌는지 — 재사용 대기 · 피해 · 태세).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgJobGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-job-gallery");
    /** 과녁: 앞 무리 (3 ~ 6칸) + 옆 둘 — 평평한 세계 땅 y = -60 */
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 3.2), new Vec3(-1.2, -60, 4.5), new Vec3(2.2, -60, 4.5), new Vec3(0.5, -60, 6.0), new Vec3(-3.0, -60, 2.5), new Vec3(4.0, -60, 2.5)
    };

    /**
     * 스킬 하나: 칸 · 사진 틱 (1인칭 · 3인칭) · 끝까지 기다릴 틱 · 피해가 없는 태세인지 (태세 · 강화 — 뒤에 평타를 누름) · 0.28.0 태세가 아닌 강화 (바람 타기 — 평타만 확인) ·
     * 평타 뒤 사진 틱 (완드는 마법진을 다 그린 뒤)
     */
    private record Case(int slot, int[] shots, int[] shots3p, int total, boolean stance, boolean buff, int hitAt) {
        Case(int slot, int[] shots, int[] shots3p, int total, boolean stance) {
            this(slot, shots, shots3p, total, stance, false, 3);
        }
    }

    private static final Case[] CASES = {
        new Case(0, new int[] {2, 12, 22}, new int[] {12, 22}, 30, false),
        new Case(1, new int[] {1, 3, 8}, new int[] {2, 6}, 16, false),
        new Case(2, new int[] {2, 12}, new int[] {2, 12}, 22, false),
        new Case(3, new int[] {4, 20}, new int[] {6}, 24, true),
        new Case(4, new int[] {8, 20, 40, 66, 83, 86, 90}, new int[] {20, 60, 83}, 100, false),
    };
    private static final Case[] CASES_SB = {
        new Case(0, new int[] {2, 6, 12}, new int[] {3, 12}, 22, false),
        new Case(1, new int[] {1, 4, 12}, new int[] {2, 12}, 20, false),
        new Case(2, new int[] {4, 18, 34}, new int[] {18}, 42, false),
        new Case(3, new int[] {4, 20}, new int[] {6}, 24, true),
        new Case(4, new int[] {10, 22, 32, 42, 56, 62}, new int[] {22, 56}, 72, false),
    };

    /** 0.28.0 전사 · 매지션 · 궁수 */
    private static final Case[] CASES_WA = {
        new Case(0, new int[] {3, 8, 14}, new int[] {8, 14}, 24, false),
        new Case(1, new int[] {2, 5, 10}, new int[] {3, 10}, 22, false),
        new Case(2, new int[] {6, 20, 40}, new int[] {10, 30}, 54, false),
        new Case(3, new int[] {4, 20}, new int[] {6}, 24, true),
        new Case(4, new int[] {8, 12, 30, 48, 62, 66}, new int[] {6, 12, 36, 62}, 80, false),
    };
    private static final Case[] CASES_MG = {
        new Case(0, new int[] {2, 6, 12}, new int[] {3, 8}, 18, false),
        new Case(1, new int[] {1, 6, 12}, new int[] {2, 12}, 18, false),
        new Case(2, new int[] {6, 25, 50, 71}, new int[] {20, 71}, 76, false),
        new Case(3, new int[] {4, 20}, new int[] {6}, 40, true, false, 3),
        new Case(4, new int[] {10, 22, 34, 46, 58, 70, 74}, new int[] {22, 46, 70}, 84, false),
    };
    private static final Case[] CASES_AR = {
        new Case(0, new int[] {2, 6, 12}, new int[] {3, 9}, 18, false),
        new Case(1, new int[] {1, 3, 8}, new int[] {2, 6}, 30, false, true, 4),
        new Case(2, new int[] {3, 10, 26, 46}, new int[] {10, 30}, 52, false),
        new Case(3, new int[] {4, 16}, new int[] {6}, 30, true, false, 4),
        new Case(4, new int[] {8, 18, 25, 28, 40, 52}, new int[] {14, 26, 44}, 64, false),
    };

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("job")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("xp set @a 40 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(10);
            for (String s : List.of("str", "int", "vit", "dex")) server.runCommand("execute as @a run rpg set " + s + " 150");
            server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l40_hero");
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            home(server);
            ctx.waitTicks(10);
            part(ctx, "trainer", () -> trainer(ctx, server, conn));
            part(ctx, "hud", () -> hud(ctx, server, conn));
            part(ctx, "swordsman", () -> skills(ctx, server, conn, "swordsman", CASES));
            part(ctx, "change", () -> change(ctx, server, conn));
            part(ctx, "spellblade", () -> skills(ctx, server, conn, "spellblade", CASES_SB));
            part(ctx, "stat", () -> stat(ctx));
            // 0.28.0 전사 · 매지션 · 궁수: 직업 · 무기 (핫바 셋째 칸) → 자원 HUD → 스킬 다섯
            part(ctx, "warrior", () -> {
                newJob(ctx, server, conn, "warrior", "rpg:axe_l40_hero", 100, sp -> JobTraitsMore.setRage(sp, 60));
                skills(ctx, server, conn, "warrior", CASES_WA);
            });
            part(ctx, "mage", () -> {
                newJob(ctx, server, conn, "mage", "rpg:wand_l40_hero", 3, sp -> JobTraitsMore.setChain(sp, 2));
                skills(ctx, server, conn, "mage", CASES_MG);
            });
            part(ctx, "archer", () -> {
                newJob(ctx, server, conn, "archer", "rpg:bow_l40_hero", 5, sp -> JobTraitsMore.setBreath(sp, 3));
                skills(ctx, server, conn, "archer", CASES_AR);
            });
            server.runOnServer(s -> JobSystem.setJob(player(s), null));
            ctx.setScreen(() -> null);
        }
        LOG.info("[job-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("job gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 직업 교관 창 → 전직

    private void trainer(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> JobTrainer.open(player(s), null));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("직업 교관"), "trainer window")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("swordsman")));
        shot(ctx, "job_trainer_swordsman");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("spellblade")));
        shot(ctx, "job_trainer_spellblade");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("paladin")));
        shot(ctx, "job_trainer_dim");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("swordsman")));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("확인"), "confirm window")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(13));
        shot(ctx, "job_trainer_confirm");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(11));
        ctx.waitTicks(2);
        shot(ctx, "job_change_02");
        ctx.waitTicks(8);
        shot(ctx, "job_change_10");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(4);
        shot(ctx, "job_change_14_3p");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(20);
        check(server.computeOnServer(s -> "swordsman".equals(JobSystem.jobOf(player(s)))), "trainer: swordsman (first job free)");
        check(ctx.computeOnClient(mc -> ClientJobBar.jobId().equals("swordsman") && ClientJobBar.gaugeMax() == 5 && !ClientJobBar.empty(0)),
                "client job bar: swordsman, 검세 max 5, slots filled");
    }

    // ------------------------------------------------------------ HUD

    private void hud(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        // 20렙 + 20렙 칼 (둘째 칸): 칸 4 · 5 (30 · 40렙) 만 잠김
        server.runCommand("xp set @a 20 levels");
        server.runCommand("item replace entity @a hotbar.1 with rpg:sword_l20_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            mmorpg.fabric.RpgPlayers.update(sp, mmorpg.fabric.RpgData.of(sp).withLvmax(20));
            JobSystem.refresh(sp, true);
        });
        settle(ctx, conn);
        ctx.waitTicks(6);
        shot(ctx, "job_hud_locked");
        check(ctx.computeOnClient(mc -> ClientJobBar.lock(3).equals("30렙") && ClientJobBar.lock(4).equals("40렙") && ClientJobBar.lock(0).isEmpty()),
                "locks at 20: '" + ctx.computeOnClient(mc -> ClientJobBar.lock(0) + "' '" + ClientJobBar.lock(3) + "' '" + ClientJobBar.lock(4)) + "'");
        // 20렙에 40렙 칼 (첫째 칸): 칸 1 ~ 3 은 든 무기의 레벨로 잠김 ("무기 40렙" — HUD 는 "무기")
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
        shot(ctx, "job_hud_weapon_level");
        check(ctx.computeOnClient(mc -> ClientJobBar.lock(0).equals("무기 40렙") && ClientJobBar.lock(3).equals("30렙")),
                "weapon level lock: '" + ctx.computeOnClient(mc -> ClientJobBar.lock(0)) + "'");
        server.runCommand("xp set @a 40 levels");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            mmorpg.fabric.RpgPlayers.update(sp, mmorpg.fabric.RpgData.of(sp).withLvmax(40));
            JobTraits.setStacks(sp, 5);
            JobSystem.refresh(sp, true);
        });
        settle(ctx, conn);
        ctx.waitTicks(6);
        shot(ctx, "job_hud_stacks");
        check(ctx.computeOnClient(mc -> ClientJobBar.gaugeValue() == 5 && ClientJobBar.lock(4).isEmpty()), "HUD: 5 stacks, all unlocked at 40");
        // 맨손: 칸 모두 잠김 (칼)
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        ctx.waitTicks(10);
        shot(ctx, "job_hud_nosword");
        check(ctx.computeOnClient(mc -> ClientJobBar.lock(0).equals("칼")), "bare hand locks the slots ('" + ctx.computeOnClient(mc -> ClientJobBar.lock(0)) + "')");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
        // 스킬 칸 창의 직업 줄 (칸 설명)
        ctx.getInput().pressKey(MmorpgClient.skillBarKey);
        if (waitScreen(ctx, sc -> sc instanceof mmorpg.fabric.client.SkillBarScreen, "skill bar window")) {
            hover(ctx, sc -> ((mmorpg.fabric.client.SkillBarScreen) sc).jobCenter(1));
            shot(ctx, "job_window_slot");
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    // ------------------------------------------------------------ 스킬 다섯

    private void skills(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String job, Case[] cases) {
        Jobs.Job j = Jobs.job(job);
        for (Case c : cases) {
            for (boolean third : new boolean[] {false, true}) {
                Jobs.Skill k = j.skill(c.slot());
                int[] shots = third ? c.shots3p() : c.shots();
                home(server);
                server.runOnServer(s -> {
                    ServerPlayer sp = player(s);
                    JobSkills.resetCooldowns(sp);
                    RuneCast.resetCooldowns(sp);
                    RpgHealth.healFull(sp);
                    if (k.id().equals("swordsman_flurry")) JobTraits.setStacks(sp, 3); // 검기
                    if (k.id().equals("warrior_crush")) JobTraitsMore.setRage(sp, 60); // 분노 30 을 씀
                    if (k.id().equals("archer_rapid")) JobTraitsMore.setBreath(sp, 3); // 4발
                    JobTraitsMore.setChain(sp, 0);
                    spawnHusks(sp.level());
                });
                ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
                ctx.waitTicks(12);
                ctx.getInput().holdKey(o -> o.keyShift);
                ctx.waitTicks(2);
                ctx.getInput().pressKey(MmorpgClient.skillKeys[c.slot()]);
                ctx.getInput().releaseKey(o -> o.keyShift);
                int t = 0;
                for (int shot : shots) {
                    if (shot > t) {
                        ctx.waitTicks(shot - t);
                        t = shot;
                    }
                    ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "job_%s_%02d%s", k.id(), shot, third ? "_3p" : "")).withSize(1280, 720));
                }
                if (c.stance() || c.buff()) {
                    // 태세 · 강화 뒤 평타 (왼쪽 클릭 — 눈앞 과녁): 명경지수 두 번째 베기 · 마검 각성 잔상 · 투혼 · 마나 과부하 광선 · 정조준 관통 · 바람 타기 두 발
                    ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
                    ctx.waitTicks(c.hitAt());
                    t += c.hitAt();
                    ctx.takeScreenshot(TestScreenshotOptions.of("job_" + k.id() + "_hit" + (third ? "_3p" : "")).withSize(1280, 720));
                }
                if (c.total() > t) ctx.waitTicks(c.total() - t);
                boolean cast = server.computeOnServer(s -> JobSkills.cooldownLeft(player(s), k.id()) > 0);
                double dealt = server.computeOnServer(s -> {
                    double sum = 0;
                    for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(30), h -> true)) sum += TestDummies.dealt(h);
                    return sum;
                });
                boolean stance = server.computeOnServer(s -> JobTraits.stanceOn(player(s), k.id()));
                LOG.info("[job-gallery] {} {} cast={} dealt={} stance={}", k.id(), third ? "3p" : "1p", cast, String.format(Locale.ROOT, "%.1f", dealt), stance);
                String tag = k.id() + (third ? " (3p)" : "");
                check(cast, tag + " cast");
                if (c.stance()) check(stance && dealt > 0, tag + " stance on, hit after it");
                else if (c.buff()) check(dealt > 0, tag + " buffed basic hit (" + String.format(Locale.ROOT, "%.1f", dealt) + ")");
                else check(dealt > 0, tag + " dealt damage (" + String.format(Locale.ROOT, "%.1f", dealt) + ")");
            }
        }
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runOnServer(s -> JobSystem.setJob(player(s), job)); // 태세 · 자원 지움
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 0.28.0 새 직업 (운영자 /job set 과 같은 길) · 자원 HUD

    private void newJob(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String job, String item, int gaugeMax,
            java.util.function.Consumer<ServerPlayer> gauge) {
        home(server);
        server.runCommand("item replace entity @a hotbar.2 with " + item);
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[2]);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            JobSystem.setJob(sp, job);
            gauge.accept(sp);
            JobSystem.refresh(sp, true);
        });
        settle(ctx, conn);
        ctx.waitTicks(8);
        shot(ctx, "job_hud_" + job);
        check(ctx.computeOnClient(mc -> ClientJobBar.jobId().equals(job) && ClientJobBar.gaugeMax() == gaugeMax && !ClientJobBar.empty(4) && ClientJobBar.lock(0).isEmpty()),
                job + ": client bar (gauge max " + gaugeMax + ", slots, unlocked) — '" + ctx.computeOnClient(mc -> ClientJobBar.jobId() + "' max "
                        + ClientJobBar.gaugeMax() + " lock '" + ClientJobBar.lock(0)) + "'");
    }

    // ------------------------------------------------------------ 직업 바꾸기 (300 은화)

    private void change(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        home(server);
        server.runOnServer(s -> {
            Wallet.set(player(s), Economy.Kind.SILVER, 500);
            JobTrainer.open(player(s), null);
        });
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("직업 교관"), "trainer window (change)")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("spellblade")));
        shot(ctx, "job_trainer_change_card");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("spellblade")));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("바꾸기"), "change confirm")) return;
        shot(ctx, "job_trainer_change_confirm");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(11));
        ctx.waitTicks(6);
        shot(ctx, "job_change_sb_06");
        ctx.waitTicks(20);
        check(server.computeOnServer(s -> "spellblade".equals(JobSystem.jobOf(player(s))) && Wallet.get(player(s), Economy.Kind.SILVER) == 200),
                "changed to spellblade for 300 silver");
        check(ctx.computeOnClient(mc -> ClientJobBar.jobId().equals("spellblade") && ClientJobBar.gaugeMax() == 3), "client: spellblade, 각인 max 3");
        shot(ctx, "job_hud_spellblade");
    }

    // ------------------------------------------------------------ 스탯 창

    private void stat(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window")) {
            ctx.waitTicks(4);
            shot(ctx, "job_stat_spellblade");
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    // ------------------------------------------------------------ 도구

    private static void home(TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt) — 나를 봄 */
    private static void spawnHusks(ServerLevel level) {
        for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(48), h -> true)) h.discard();
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

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[job-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[job-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.getInput().releaseKey(o -> o.keyShift);
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[job-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[job-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private boolean waitScreen(ClientGameTestContext ctx, java.util.function.Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 60);
            ctx.waitTicks(3);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open");
            return false;
        }
    }

    private static void settle(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
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

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        hover(ctx, where);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        settle(ctx, conn);
    }

    @SuppressWarnings("unused")
    private static boolean waitFor(ClientGameTestContext ctx, java.util.function.Predicate<Minecraft> test, int ticks) {
        try {
            ctx.waitFor(mc -> mc.player != null && test.test(mc), ticks);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }
}
