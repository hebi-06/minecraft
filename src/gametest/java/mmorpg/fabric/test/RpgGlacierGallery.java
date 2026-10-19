package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import mmorpg.core.Runes;
import mmorpg.fabric.FxDisplays;
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
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
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
 * 0.24.0 만년빙하 사진첩 (화면 시험 — 키 glacier): 평평한 세계에서 과녁 허스크 여섯 (5 ~ 9칸 북쪽 무리) 의 가운데 과녁을 조준하고 실제 입력 (스킬 칸 1번의 키) 으로 씀.
 * 0돌 1인칭 (얼어붙음 → 거대한 얼음 → 빙결 → 빙하 붕괴 → 부서짐) · 6돌 3인칭 (빙하의 왕관 · 오로라) · 6돌 밤 1인칭 (lr_glacier_&lt;돌&gt;[n]_&lt;틱&gt;[_3p]).
 * 써졌는지 (재사용 대기) · 들어간 피해 · 얼린 과녁 수 · 띄운 얼음 (표시 엔티티) 수를 로그 (glacier-gallery) 로, 모자라면 마지막에 실패.
 * 과녁은 흡수 체력 2048 을 매 틱 다시 채움 (붕괴 숫자가 잘리지 않게 — RpgLimitedRuneGallery 와 같음).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgGlacierGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-glacier-gallery");
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 6.5), new Vec3(-1.5, -60, 7.5), new Vec3(2.5, -60, 7.0), new Vec3(0.5, -60, 9.0), new Vec3(-3.0, -60, 5.0),
        new Vec3(3.5, -60, 5.0)
    };
    private static final double ME_Z = -7.0;
    private static final float BUFFER = 2048F;
    private static final List<Husk> BUFFERED = new CopyOnWriteArrayList<>();
    private static boolean tickInstalled;
    private int lives;
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("glacier")) return;
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
            for (String s : List.of("str 31", "dex 31", "vit 31", "int 32")) server.runCommand("execute as @a run rpg set " + s);
            server.runOnServer(s -> installBuffer());
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            // 0돌 (사진 틱 = 서버에서 쓴 뒤): 서리 빛 · 마법진 (0 ~ 16틱 — 얼음판이 가운데부터 퍼짐) → 경계 결정 · 첫 거대한 얼음 (14 땅속 → 16 솟음 · 17 피해)
            //   → 0.7초마다 (31 · 45 — 세 번째에 빙결) → 얼어 있는 과녁 (45 ~ 85) → 다시 쌓여 또 빙결 (115) → 서리가 모임 (118) → 빙하 첨탑 · 붕괴 (128 ~ 131)
            //   → 부서짐 (138) → 끝
            run(ctx, server, 0, false, new int[] {4, 9, 14, 17, 19, 25, 33, 47, 52, 80, 119, 124, 129, 131, 134, 139, 144, 160}, false, 175);
            // 6돌 (3인칭): 필드 8칸 · 얼음 12번 (17 ~ 171) · 붕괴 184 → 부서짐 194 → 빙하의 왕관 (196 ~ 210 차례로 솟음) · 오로라 · 눈 (196 ~ 264)
            run(ctx, server, 6, true, new int[] {16, 19, 47, 103, 176, 185, 187, 193, 199, 205, 212, 230, 255}, false, 275);
            server.runCommand("time set 18000");
            run(ctx, server, 6, false, new int[] {12, 19, 50, 122, 185, 188, 196, 206, 216, 240}, true, 275);
            server.runCommand("time set 6000");
        }
        LOG.info("[glacier-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("glacier gallery: " + String.join(" / ", failures));
    }

    /** 만년빙하 하나를 실제 입력으로 쓰고 사진 · 기록 (level = 돌파 단계, end = 쓴 뒤 기다리는 틱) */
    private void run(ClientGameTestContext ctx, TestServerContext server, int level, boolean third, int[] shots, boolean night, int end) {
        Runes.Rune r = Runes.parseRune(Runes.runeId("glacier", "transcend"));
        if (r == null) {
            failures.add("glacier no rune");
            return;
        }
        String id = "glacier_" + level + (night ? "n" : "");
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
            SkillBar.set(sp, 0, Breakthrough.stack("glacier", "transcend", level));
            sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, -59.0, 6.5));
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(14);
        int fx0 = server.computeOnServer(s -> FxDisplays.spawned);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        // 사진 틱 = 서버에서 룬을 쓴 틱 (시전 기록) 부터 — 사진을 찍는 동안 서버가 앞서 나가므로 클라이언트 틱이 아니라 서버 틱으로 맞춤
        long c0 = -1;
        for (int i = 0; i < 40 && c0 < 0; i++) {
            ctx.waitTick();
            double[] l = server.computeOnServer(s -> glacierOf(player(s)));
            if (l != null && l.length > 3) c0 = (long) l[3];
        }
        if (c0 < 0) {
            failures.add(id + " not cast (no record)");
            return;
        }
        final long cast0 = c0;
        int frozenSeen = 0;
        int loops = 0;
        long t = 0;
        for (int shot : shots) {
            while ((t = server.computeOnServer(s -> mmorpg.fabric.Tasks.now()) - cast0) < shot) {
                ctx.waitTick();
                if (++loops % 8 == 0) {
                    int n = server.computeOnServer(s -> {
                        int k = 0;
                        for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, player(s).getBoundingBox().inflate(40), h -> true)) {
                            if (h.hasEffect(MobEffects.SLOWNESS)) k++;
                        }
                        return k;
                    });
                    frozenSeen = Math.max(frozenSeen, n);
                }
            }
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "lr_%s_%03d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        while (server.computeOnServer(s -> mmorpg.fabric.Tasks.now()) - cast0 < end) ctx.waitTick();
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        double dealt = server.computeOnServer(s -> {
            double sum = 0;
            for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 6, 0.5, -60, 6).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
        int displays = server.computeOnServer(s -> FxDisplays.spawned) - fx0;
        double[] last = server.computeOnServer(s -> glacierOf(player(s)));
        LOG.info("[glacier-gallery] {} {} cast={} dealt={} displays={} frozenSeen={} last={}", id, third ? "3p" : "1p", cast, String.format(Locale.ROOT, "%.1f", dealt),
                displays, frozenSeen, last == null ? "none" : java.util.Arrays.toString(last));
        if (!cast) failures.add(id + " not cast");
        else if (!(dealt > 0)) failures.add(id + " dealt no damage");
        if (displays < 100) failures.add(id + " displays " + displays);
        if (last == null || last[0] < 6) failures.add(id + " frozen " + (last == null ? "none" : (int) last[0]));
        if (frozenSeen < 4) failures.add(id + " frozen seen " + frozenSeen);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    /** RuneGlacier 의 마지막 시전 {얼린 수, 거대한 얼음 수, 반지름} (패키지 밖이라 리플렉션) */
    private static double[] glacierOf(ServerPlayer sp) {
        try {
            Class<?> c = Class.forName("mmorpg.fabric.rune.RuneGlacier");
            Method m = c.getDeclaredMethod("last", java.util.UUID.class);
            m.setAccessible(true);
            return (double[]) m.invoke(null, sp.getUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

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
