package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mmorpg.core.Rules;
import mmorpg.fabric.client.bedrock.BedrockRenderer;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import mmorpg.fabric.mob.RpgEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험): 애드온 엔티티 26종의 모델 · 동작 · 렌더 컨트롤러 (클라이언트 BedrockRenderer).
 * 몬스터 6 (앞 · 옆 · 공격 동작), 보스 10 (앞 · 3/4 · 패턴 동작 셋 · 번쩍임 · 분노, 스콜과 하티 낮 · 밤 · 황혼, 이미르 균열 1~3, 수르트 부위 파괴 · 열기),
 * 분신 · 소품 9 (동작). 관전 모드로 몸 크기에 맞춰 떨어져서 봄. 확인 항목: 그리는 것이 BedrockRenderer 인지 · 속성 · 튼 동작이 클라이언트에 왔는지.
 * 사진 이름 m_&lt;몬스터&gt;_* · b_&lt;보스&gt;_* · p_&lt;소품&gt;_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgMobGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-mob-gallery");
    private static final double FLOOR = -60;

    /** 보스 → 사진 찍을 동작 (짧은 이름, 찍을 때 (초)) */
    private static final Map<String, Object[][]> BOSS_ANIMS = Map.ofEntries(
            Map.entry("desert_scorpion", new Object[][] {{"sting", 0.7}, {"burrow", 0.8}, {"rage", 0.6}}),
            Map.entry("rock_scorpion", new Object[][] {{"slam", 0.75}, {"roar", 1.6}, {"throw", 0.45}}),
            Map.entry("scarecrow_king", new Object[][] {{"slash", 0.65}, {"raise", 2.7}, {"stagger", 2.5}}),
            Map.entry("kraken", new Object[][] {{"slam", 0.6}, {"ink", 0.8}, {"spin", 3.0}}),
            Map.entry("great_worm", new Object[][] {{"slam", 0.75}, {"rear", 1.0}, {"quake", 2.2}}),
            Map.entry("jormungandr", new Object[][] {{"bite", 0.7}, {"rear", 3.5}, {"coil", 1.15}}),
            Map.entry("nidhogg", new Object[][] {{"breath", 1.8}, {"roar", 0.75}, {"tail", 0.8}}),
            Map.entry("skoll_hati", new Object[][] {{"howl", 0.75}, {"wheel", 2.1}, {"pounce", 1.3}}),
            Map.entry("ymir", new Object[][] {{"slam", 0.9}, {"spike", 1.5}, {"kneel", 0.4}}),
            Map.entry("surtr", new Object[][] {{"slam", 2.45}, {"fist", 3.0}, {"roar", 1.15}}));

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("mob")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamemode spectator @a");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(20);
            for (String key : Rules.get().strings("MONSTER_ORDER")) monster(ctx, server, key);
            for (String key : BOSS_ANIMS.keySet().stream().sorted().toList()) boss(ctx, server, key);
            props(ctx, server);
        }
        LOG.info("[mob-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("mob gallery failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 엔티티 세우기 · 보기

    /** 엔티티를 (x, 바닥, z) 에 yaw 쪽을 보게 세움 (목표 없음 — 제자리) */
    private static int spawn(TestServerContext server, String id, double x, double z, float yaw) {
        return server.computeOnServer(s -> {
            ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
            BedrockMob e = RpgEntities.type(id).create(level, EntitySpawnReason.COMMAND);
            e.snapTo(x, FLOOR, z, yaw, 0F);
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
            e.setNoAi(true);
            level.addFreshEntity(e);
            return e.getId();
        });
    }

    private static void turn(TestServerContext server, int id, float yaw) {
        server.runOnServer(s -> {
            Entity e = s.getPlayerList().getPlayers().get(0).level().getEntity(id);
            if (e instanceof BedrockMob m) {
                m.snapTo(m.getX(), m.getY(), m.getZ(), yaw, 0F);
                m.setYHeadRot(yaw);
                m.setYBodyRot(yaw);
            }
        });
    }

    private static void discard(TestServerContext server, int id) {
        server.runOnServer(s -> {
            Entity e = s.getPlayerList().getPlayers().get(0).level().getEntity(id);
            if (e != null) e.discard();
        });
    }

    private static void anim(TestServerContext server, int id, String full) {
        server.runOnServer(s -> {
            Entity e = s.getPlayerList().getPlayers().get(0).level().getEntity(id);
            if (e instanceof BedrockMob m) m.playAnimation(full);
        });
    }

    private static void prop(TestServerContext server, int id, String prop, double v) {
        server.runOnServer(s -> {
            Entity e = s.getPlayerList().getPlayers().get(0).level().getEntity(id);
            if (e instanceof BedrockMob m) m.setProp(prop, v);
        });
    }

    /** 카메라: (x, y, z) 에서 yaw · pitch 로 봄 (관전 모드) */
    private static void camera(ClientGameTestContext ctx, TestServerContext server, double x, double y, double z, float yaw, float pitch) {
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(yaw, pitch);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(960, 540));
    }

    /** 확인: 클라이언트에 그 엔티티가 있고 BedrockRenderer 로 그림 */
    private void checkRenderer(ClientGameTestContext ctx, int id, String label) {
        String r = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (e == null) return "no entity";
            Object r0 = mc.getEntityRenderDispatcher().getRenderer(e);
            return r0 instanceof BedrockRenderer ? "ok" : "renderer " + r0;
        });
        if (!"ok".equals(r)) failures.add(label + ": " + r);
        else LOG.info("[mob-gallery] OK   {} renderer", label);
    }

    /** 확인: 속성 · 튼 동작이 클라이언트에 옴 */
    private void checkSynced(ClientGameTestContext ctx, int id, String label, String prop, double want, String anim) {
        String r = ctx.computeOnClient(mc -> {
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            if (!(e instanceof BedrockMob m)) return "no entity";
            if (prop != null && Math.abs(m.prop(prop) - want) > 1e-6) return prop + "=" + m.prop(prop);
            if (anim != null && m.syncedAnims().stream().noneMatch(p -> p.anim().equals(anim))) return "anim " + anim + " not synced " + m.syncedAnims();
            return "ok";
        });
        if (!"ok".equals(r)) failures.add(label + ": " + r);
        else LOG.info("[mob-gallery] OK   {} synced", label);
    }

    // ------------------------------------------------------------ 몬스터

    private void monster(ClientGameTestContext ctx, TestServerContext server, String key) {
        String id = "rpg:" + key;
        MobData.Def d = MobData.get(id);
        double h = d.height() * d.scale();
        double dist = Math.max(4.5, h * 1.6 + 1.5);
        int e = spawn(server, id, 0.5, 0.5 - dist, 0F);
        camera(ctx, server, 0.5, FLOOR + h * 0.45, 0.5, 180F, 4F);
        ctx.waitTicks(8);
        checkRenderer(ctx, e, key);
        shot(ctx, "m_" + key + "_front");
        turn(server, e, 90F);
        ctx.waitTicks(4);
        shot(ctx, "m_" + key + "_side");
        turn(server, e, 0F);
        String full = "animation.rpg.monster." + key + ".attack";
        anim(server, e, full);
        ctx.waitTicks(2);
        checkSynced(ctx, e, key + " attack", null, 0, full);
        ctx.waitTicks(5);
        shot(ctx, "m_" + key + "_attack");
        discard(server, e);
        ctx.waitTicks(2);
    }

    // ------------------------------------------------------------ 보스

    private void boss(ClientGameTestContext ctx, TestServerContext server, String key) {
        String id = "rpg:boss_" + key;
        MobData.Def d = MobData.get(id);
        double h = d.height() * d.scale();
        double w = d.width() * d.scale();
        double dist = Math.max(7, h * 1.25 + w * 1.6 + 2);
        int e = spawn(server, id, 0.5, 0.5 - dist, 0F);
        double eye = FLOOR + Math.max(1.6, h * 0.55);
        camera(ctx, server, 0.5, eye, 0.5, 180F, 6F);
        ctx.waitTicks(12);
        checkRenderer(ctx, e, key);
        shot(ctx, "b_" + key + "_front");
        // 3/4: 보스 둘레 45° 쪽에서
        double a = Math.toRadians(45);
        camera(ctx, server, 0.5 + Math.sin(a) * dist, eye, 0.5 - dist + Math.cos(a) * dist, 135F, 6F);
        ctx.waitTicks(4);
        shot(ctx, "b_" + key + "_34");
        camera(ctx, server, 0.5, eye, 0.5, 180F, 6F);
        for (Object[] an : BOSS_ANIMS.get(key)) {
            String full = "animation.rpg.boss." + key + "." + an[0];
            anim(server, e, full);
            ctx.waitTicks(Math.max(1, (int) Math.round((Double) an[1] * 20)));
            shot(ctx, "b_" + key + "_" + an[0]);
            ctx.waitTicks(2);
        }
        server.runOnServer(s -> {
            Entity x = s.getPlayerList().getPlayers().get(0).level().getEntity(e);
            if (x instanceof BedrockMob m) m.stopAnimation(null);
        });
        prop(server, e, "rpg:flash", 1);
        ctx.waitTicks(3);
        checkSynced(ctx, e, key + " flash", "rpg:flash", 1, null);
        shot(ctx, "b_" + key + "_flash");
        prop(server, e, "rpg:flash", 0);
        prop(server, e, "rpg:rage", 1);
        ctx.waitTicks(3);
        shot(ctx, "b_" + key + "_rage");
        prop(server, e, "rpg:rage", 0);
        switch (key) {
            case "skoll_hati" -> {
                for (int ph = 0; ph < 3; ph++) {
                    prop(server, e, "rpg:phase", ph);
                    prop(server, e, "rpg:day", ph == 0 ? 1 : 0);
                    prop(server, e, "rpg:dusk", ph == 2 ? 1 : 0);
                    ctx.waitTicks(6);
                    shot(ctx, "b_" + key + "_phase" + ph);
                }
            }
            case "ymir" -> {
                for (int c = 1; c <= 3; c++) {
                    prop(server, e, "rpg:crack", c);
                    ctx.waitTicks(3);
                    shot(ctx, "b_" + key + "_crack" + c);
                }
            }
            case "surtr" -> {
                prop(server, e, "rpg:heat", 1);
                prop(server, e, "rpg:gait", 1);
                ctx.waitTicks(6);
                shot(ctx, "b_" + key + "_heat");
                prop(server, e, "rpg:heat", 0);
                prop(server, e, "rpg:gait", 0);
                prop(server, e, "rpg:sword_broken", 1);
                prop(server, e, "rpg:fist_broken", 1);
                prop(server, e, "rpg:crown_broken", 1);
                ctx.waitTicks(4);
                checkSynced(ctx, e, key + " broken", "rpg:crown_broken", 1, null);
                shot(ctx, "b_" + key + "_broken");
            }
            case "nidhogg" -> {
                prop(server, e, "rpg:fly", 1);
                ctx.waitTicks(10);
                shot(ctx, "b_" + key + "_fly");
            }
            default -> {
            }
        }
        discard(server, e);
        ctx.waitTicks(2);
    }

    // ------------------------------------------------------------ 분신 · 소품

    private void props(ClientGameTestContext ctx, TestServerContext server) {
        String[][] list = {
            {"rpg:decoy_scarecrow_king", "appear", "0.25"},
            {"rpg:prop_kraken_tentacle", "whip", "0.4"},
            {"rpg:prop_kraken_tentacle", "coil", "1.2"},
            {"rpg:prop_nid_roots", "rise", "0.45"},
            {"rpg:prop_straw_cage", "rise", "0.4"},
            {"rpg:prop_pumpkin_bomb", "", "0"},
            {"rpg:prop_surtr_core", "appear", "0.3"},
            {"rpg:prop_ymir_ice", "form", "0.2"},
            {"rpg:prop_ymir_ice", "shatter", "0.25"},
            {"rpg:prop_jorm_ring", "", "0"},
        };
        for (String[] p : list) {
            String id = p[0];
            String key = id.substring(4);
            MobData.Def d = MobData.get(id);
            double h = Math.max(1.5, d.height() * d.scale());
            boolean ring = id.endsWith("jorm_ring");
            double dist = ring ? 30 : Math.max(5, h * 1.6 + 2);
            int e = spawn(server, id, 0.5, 0.5 - dist, 0F);
            camera(ctx, server, 0.5, FLOOR + (ring ? 14 : Math.max(1.5, h * 0.55)), 0.5, 180F, ring ? 25F : 8F);
            ctx.waitTicks(8);
            checkRenderer(ctx, e, key);
            String name = "p_" + key + (p[1].isEmpty() ? "" : "_" + p[1]);
            if (!p[1].isEmpty()) {
                String prefix = id.startsWith("rpg:decoy_") ? "animation.rpg.boss.scarecrow_king." : "animation.rpg.prop." + key.substring(5) + ".";
                anim(server, e, prefix + p[1]);
                ctx.waitTicks(Math.max(1, (int) Math.round(Double.parseDouble(p[2]) * 20)));
            }
            shot(ctx, name);
            if (id.endsWith("surtr_core")) {
                for (int part = 1; part <= 2; part++) {
                    prop(server, e, "rpg:part", part);
                    ctx.waitTicks(3);
                    shot(ctx, "p_" + key + "_part" + part);
                }
            }
            discard(server, e);
            ctx.waitTicks(2);
        }
    }
}
