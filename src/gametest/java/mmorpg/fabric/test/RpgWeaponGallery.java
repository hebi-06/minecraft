package mmorpg.fabric.test;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 무기 3D 모델 사진첩 (화면 시험): 디자인 31종을 하나씩 손에 들고 1인칭 · 3인칭 (앞에서) 사진, 밤에 빛나는 칸 사진.
 * 사진 이름 w_<디자인>_fp / _3p → ci-logs 의 shots/. 확인 항목은 없음 (사진으로 봄).
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
            new String[] {"spear_transcend_lv0", "rpg:axe_l40_transcend"},
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
            // 밤: 빛나는 칸 (light_emission)
            server.runCommand("time set 18000");
            ctx.waitTicks(10);
            for (String id : List.of("sword_transcend_lv0|rpg:sword_l40_transcend", "bow_transcend_lv0|rpg:bow_l40_transcend", "staff_hero_lv0|rpg:wand_l40_hero")) {
                String[] d = id.split("\\|");
                shoot(ctx, server, d[0], d[1], "_night");
            }
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
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
