package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.client.WeaponAura;
import mmorpg.fabric.client.WeaponFxData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 무기 3D 모델 사진첩 (화면 시험): 디자인 31종을 하나씩 손에 들고 1인칭 · 3인칭 (앞에서) 사진, 밤에 빛나는 칸 사진,
 * 마네킹 둘 (옆 · 앞) 이 든 모습 (3인칭 자세를 옆에서 확인), 영웅 · 초월 13종의 빛 알갱이 (밤: 1인칭 · 3인칭 · 마네킹).
 * 사진 이름 w_<디자인>_fp / _3p / m_<디자인> / g_<디자인>_fp / _3p / mg_<디자인> → ci-logs 의 shots/. 확인 항목은 없음 (사진으로 봄).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgWeaponGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-weapon-gallery");
    /** 디자인 → 그 디자인을 쓰는 무기 하나 (tools/weapon_art/designs.json) */
    private static final List<String[]> DESIGNS = List.of(
            new String[] {"sword_lv0", "rpg:sword_l40_common"},
            new String[] {"sword_rare_lv0", "rpg:sword_l40_rare"},
            new String[] {"sword_noble_lv0", "rpg:sword_l40_noble"},
            new String[] {"sword_hero_lv0", "rpg:sword_l40_hero"},
            new String[] {"sword_transcend_lv0", "rpg:sword_l40_transcend"},
            new String[] {"dagger_lv0", "rpg:dagger_l40_common"},
            new String[] {"dagger_rare_lv0", "rpg:dagger_l40_rare"},
            new String[] {"dagger_noble_lv0", "rpg:dagger_l40_noble"},
            new String[] {"dagger_hero_lv0", "rpg:dagger_l40_hero"},
            new String[] {"dagger_transcend_lv0", "rpg:dagger_l40_transcend"},
            new String[] {"axe_lv0", "rpg:axe_l40_common"},
            new String[] {"axe_rare_lv0", "rpg:axe_l40_rare"},
            new String[] {"axe_noble_lv0", "rpg:axe_l40_noble"},
            new String[] {"axe_hero_lv0", "rpg:axe_l40_hero"},
            new String[] {"axe_transcend_lv0", "rpg:axe_l40_transcend"},
            new String[] {"greatsword_transcend_lv0", "rpg:greatsword_l40_transcend"},
            new String[] {"shield_lv0", "rpg:shield_l40_common"},
            new String[] {"shield_rare_lv0", "rpg:shield_l40_rare"},
            new String[] {"shield_noble_lv0", "rpg:shield_l40_noble"},
            new String[] {"shield_hero_lv0", "rpg:shield_l40_hero"},
            new String[] {"shield_transcend_lv0", "rpg:shield_l40_transcend"},
            new String[] {"bow_lv0", "rpg:bow_l40_common"},
            new String[] {"bow_rare_lv0", "rpg:bow_l40_rare"},
            new String[] {"bow_noble_lv0", "rpg:bow_l40_noble"},
            new String[] {"bow_hero_lv0", "rpg:bow_l40_hero"},
            new String[] {"bow_transcend_lv0", "rpg:bow_l40_transcend"},
            new String[] {"staff_lv0", "rpg:wand_l40_common"},
            new String[] {"staff_rare_lv0", "rpg:wand_l40_rare"},
            new String[] {"staff_noble_lv0", "rpg:wand_l40_noble"},
            new String[] {"staff_hero_lv0", "rpg:wand_l40_hero"},
            new String[] {"staff_transcend_lv0", "rpg:wand_l40_transcend"});

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("weapon")) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만 · 고른 시험만 (MMORPG_ONLY)
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            for (String[] d : DESIGNS) shoot(ctx, server, d[0], d[1], "");
            mannequins(ctx, server, false);
            effects(ctx, server);
            slashes(ctx, server);
            // 밤: 빛나는 칸 (light_emission)
            server.runCommand("time set 18000");
            ctx.waitTicks(10);
            for (String id : List.of("sword_transcend_lv0|rpg:sword_l40_transcend", "bow_transcend_lv0|rpg:bow_l40_transcend", "staff_hero_lv0|rpg:wand_l40_hero")) {
                String[] d = id.split("\\|");
                shoot(ctx, server, d[0], d[1], "_night");
            }
            glints(ctx, server);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    /**
     * 이펙트 그림 (애드온 그림 파티클): 북쪽 12칸 허스크에게 활 · 완드 (일반 · 초월 색) — 쏜 뒤 여러 틱의 사진 (fx_<무기>_t<틱>),
     * 3인칭 (뒤) 한 번씩.
     */
    private static void effects(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("time set 6000");
        server.runCommand("xp set @a 40 levels");
        for (String st : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + st + " 200");
        server.runOnServer(s -> {
            ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            h.setNoAi(true);
            h.setSilent(true);
            h.setPersistenceRequired();
            h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000);
            h.setHealth(5000F);
            h.snapTo(0.5, -60, -11.5, 0F, 0F);
            h.setYHeadRot(0F);
            h.setYBodyRot(0F);
            level.addFreshEntity(h);
        });
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, -60 + 1.5, -11.5)));
        String[][] shots = {
            {"rpg:bow_l40_common", "bow_common", "1,2,3,4,6,9"},
            {"rpg:bow_l40_transcend", "bow_transcend", "1,2,3,4,6,9"},
            {"rpg:wand_l40_common", "wand_common", "2,5,8,11,13,16"},
            {"rpg:wand_l40_transcend", "wand_transcend", "2,5,8,11,13,16"},
        };
        for (String[] sh : shots) fire(ctx, server, sh[0], sh[1], sh[2], CameraType.FIRST_PERSON, "");
        fire(ctx, server, "rpg:bow_l40_hero", "bow_hero", "2,4", CameraType.THIRD_PERSON_BACK, "_3p");
        fire(ctx, server, "rpg:wand_l40_hero", "wand_hero", "5,11,13", CameraType.THIRD_PERSON_BACK, "_3p");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // 과녁은 치움 (떨어뜨리는 것 없이)
        server.runOnServer(s -> {
            ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
            level.getEntities(EntityTypes.HUSK, h -> true).forEach(h -> h.discard());
        });
    }

    /**
     * 근접 참격 (애드온 그림): 가까운 허스크를 침 (보통 · 치명타), 허공 휘두르기 — 친 뒤 1 · 3틱 사진 (s_<무기>_<보통|치명>_t<틱>).
     */
    private static void slashes(ClientGameTestContext ctx, TestServerContext server) {
        Husk target = server.computeOnServer(s -> {
            ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            h.setNoAi(true);
            h.setSilent(true);
            h.setPersistenceRequired();
            h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024); // 한 방에 쓰러지지 않게 (치기 전마다 다시 채움)
            h.setHealth(1024F);
            h.snapTo(0.5, -60, -2.2, 0F, 0F);
            h.setYHeadRot(0F);
            h.setYBodyRot(0F);
            level.addFreshEntity(h);
            return h;
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(5);
        String[][] weapons = {
            {"rpg:sword_l40_common", "sword_common"},
            {"rpg:sword_l40_hero", "sword_hero"},
            {"rpg:dagger_l40_hero", "dagger_hero"},
            {"rpg:axe_l40_hero", "axe_hero"},
            {"rpg:shield_l40_hero", "shield_hero"},
            {"rpg:axe_l40_transcend", "axe_transcend"},
            {"rpg:lance_l40_transcend", "lance_transcend"}, // 궁니르 (0.12.3 — 하늘빛 찌르기 SLASH.spear)
            {"rpg:greatsword_l40_transcend", "greatsword_transcend"},
        };
        for (String[] w : weapons) {
            server.runCommand("item replace entity @a weapon.mainhand with " + w[0]);
            ctx.waitTicks(12);
            for (boolean crit : new boolean[] {false, true}) {
                server.runOnServer(s -> {
                    target.setHealth(target.getMaxHealth());
                    target.damageCooldownTime = 0;
                    mmorpg.fabric.RpgCombat.forceCrit = crit;
                });
                ctx.runOnClient(mc -> {
                    mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(target.getX(), target.getEyeY() - 0.3, target.getZ()));
                    var e = mc.level.getEntity(target.getId());
                    if (e != null) mc.gameMode.attack(mc.player, e);
                    else LOG.warn("[gallery] slash target missing on client");
                });
                ctx.waitTicks(1);
                ctx.takeScreenshot(TestScreenshotOptions.of("s_" + w[1] + (crit ? "_crit" : "") + "_t1").withSize(960, 540));
                ctx.waitTicks(2);
                ctx.takeScreenshot(TestScreenshotOptions.of("s_" + w[1] + (crit ? "_crit" : "") + "_t3").withSize(960, 540));
                server.runOnServer(s -> mmorpg.fabric.RpgCombat.forceCrit = null);
                ctx.waitTicks(15);
            }
        }
        // 허공 휘두르기 (실제 마우스 왼쪽 — 휘두르기 패킷): 하늘을 보고
        server.runCommand("item replace entity @a weapon.mainhand with rpg:sword_l40_transcend");
        ctx.waitTicks(12);
        ctx.getInput().lookAt(180F, -35F);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.takeScreenshot(TestScreenshotOptions.of("s_air_sword_transcend_t1").withSize(960, 540));
        ctx.waitTicks(2);
        ctx.takeScreenshot(TestScreenshotOptions.of("s_air_sword_transcend_t3").withSize(960, 540));
        ctx.waitTicks(10);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(1);
        ctx.takeScreenshot(TestScreenshotOptions.of("s_air_sword_transcend_2_t2").withSize(960, 540));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(10);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(1);
        ctx.takeScreenshot(TestScreenshotOptions.of("s_air_sword_transcend_3p_t2").withSize(960, 540));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // 과녁은 치움 (다음 사진들에 가리지 않게)
        server.runOnServer(s -> target.discard());
        ctx.getInput().lookAt(0F, 0F);
        LOG.info("[gallery] slashes");
    }

    private static void fire(ClientGameTestContext ctx, TestServerContext server, String id, String name, String ticks, CameraType cam, String suffix) {
        server.runCommand("item replace entity @a weapon.mainhand with " + id);
        ctx.runOnClient(mc -> mc.options.setCameraType(cam));
        ctx.waitTicks(12);
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        int done = 0;
        for (String t : ticks.split(",")) {
            int at = Integer.parseInt(t);
            ctx.waitTicks(at - done);
            done = at;
            ctx.takeScreenshot(TestScreenshotOptions.of("fx_" + name + suffix + "_t" + at).withSize(960, 540));
        }
        ctx.waitTicks(Math.max(0, 26 - done)); // 재사용 대기 (20틱)
        LOG.info("[gallery] fx {}", name);
    }

    /** 빛 알갱이가 있는 무기인지 (WeaponFxData) */
    private static boolean hasGlints(String id) {
        return WeaponFxData.BY_ITEM.containsKey(id.substring(id.indexOf(':') + 1));
    }

    /**
     * 무기 빛 알갱이 (밤 — 지금 밤): 영웅 · 초월 13종을 들고 1인칭 · 3인칭 (앞) 각 20틱 뒤 (알갱이가 수명만큼 쌓인 뒤) — 실제 크기,
     * 마네킹 (mg_) 은 알갱이를 4배로 키워 무기의 어느 자리에서 나오는지 봄
     */
    private static void glints(ClientGameTestContext ctx, TestServerContext server) {
        for (String[] d : DESIGNS) {
            if (!hasGlints(d[1])) continue;
            server.runCommand("item replace entity @a weapon.mainhand with " + d[1]);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(20);
            ctx.takeScreenshot(TestScreenshotOptions.of("g_" + d[0] + "_fp").withSize(640, 360));
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(20);
            ctx.takeScreenshot(TestScreenshotOptions.of("g_" + d[0] + "_3p").withSize(640, 360));
            LOG.info("[gallery] glints {}", d[0]);
        }
        ctx.runOnClient(mc -> WeaponAura.testScale = 4F);
        mannequins(ctx, server, true);
        ctx.runOnClient(mc -> WeaponAura.testScale = 1F);
    }

    /**
     * 마네킹 둘 (옆을 보임 · 앞을 보임) 에게 무기를 들려 3인칭 자세를 봄. 플레이어는 빈손으로 북쪽을 봄.
     * glints: 빛 알갱이가 있는 무기만, 알갱이가 쌓이도록 20틱 뒤 (mg_<디자인>)
     */
    private static void mannequins(ClientGameTestContext ctx, TestServerContext server, boolean glints) {
        List<Mannequin> ms = server.computeOnServer(s -> {
            ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
            List<Mannequin> list = new ArrayList<>();
            float[][] at = {{-1.0F, -3.0F, -90F}, {1.6F, -3.0F, 0F}}; // 동쪽을 봄 (오른쪽이 플레이어 쪽) · 남쪽을 봄 (플레이어를 마주봄)
            for (float[] a : at) {
                Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
                m.snapTo(a[0], -60, a[1], a[2], 0F);
                m.setYHeadRot(a[2]);
                m.setYBodyRot(a[2]);
                level.addFreshEntity(m);
                list.add(m);
            }
            return list;
        });
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(180F, 12F);
        for (String[] d : DESIGNS) {
            if (glints ? !hasGlints(d[1]) : !d[0].endsWith("_lv0") || d[0].contains("rare") || d[0].contains("noble")) continue;
            server.runOnServer(s -> {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(d[1])));
                for (Mannequin m : ms) m.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
            });
            ctx.waitTicks(glints ? 20 : 4);
            ctx.takeScreenshot(TestScreenshotOptions.of((glints ? "mg_" : "m_") + d[0]).withSize(960, 540));
        }
        server.runOnServer(s -> ms.forEach(m -> m.discard()));
        ctx.getInput().lookAt(0F, 0F);
    }

    private static void shoot(ClientGameTestContext ctx, TestServerContext server, String design, String id, String suffix) {
        server.runCommand("item replace entity @a weapon.mainhand with " + id);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(12); // 바꿔 드는 동작이 끝날 때까지
        ctx.takeScreenshot(TestScreenshotOptions.of("w_" + design + suffix + "_fp").withSize(640, 360));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(3);
        ctx.takeScreenshot(TestScreenshotOptions.of("w_" + design + suffix + "_3p").withSize(640, 360));
        LOG.info("[gallery] {} ({})", design, id);
    }
}
