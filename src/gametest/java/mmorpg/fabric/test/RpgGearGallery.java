package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Armor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 4단계 사진첩 (화면 시험): 갑옷 세트 5종 (순찰대 · 강철 기사 · 흑요석 수호자 · 적룡 · 황제) 을 입은 모습 — 3인칭 앞 · 뒤, 마네킹 둘 (옆 · 앞),
 * 황제 망토가 걸을 때 날리는 모습 (마네킹을 옆으로 옮기며), 가방 화면의 갑옷 칸 · 설명 · 재료 그림.
 * 사진 이름 a_&lt;세트&gt;_front / _back · am_&lt;세트&gt; · a_cape_t&lt;틱&gt; · a_inventory → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgGearGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-gear-gallery");
    /** 세트 → 입힐 갑옷 (종류 · 레벨 · 등급) */
    private static final String[][] SETS = {
        {"ranger", "dex", "10", "common"},
        {"knight", "str", "20", "common"},
        {"warden", "vit", "30", "common"},
        {"dragon", "strvit", "40", "common"},
        {"emperor", "int", "40", "transcend"},
    };
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SLOT_ARGS = {"armor.head", "armor.chest", "armor.legs", "armor.feet"};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("gear")) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만 · 고른 시험만 (MMORPG_ONLY)
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runCommand("xp set @a 40 levels");
            for (String st : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + st + " 200");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            armor(ctx, server);
            cape(ctx, server);
            inventory(ctx, server);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static String id(String[] set, int piece) {
        return Armor.armorId(set[1], Double.parseDouble(set[2]), set[3], Armor.PIECE_ORDER.get(piece));
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    /** 세트마다: 플레이어가 입고 3인칭 앞 · 뒤, 마네킹 둘 (옆 · 앞 — 플레이어는 1인칭으로 봄) */
    private static void armor(ClientGameTestContext ctx, TestServerContext server) {
        List<Mannequin> ms = mannequins(server, new float[][] {{-1.0F, -3.0F, -90F}, {1.6F, -3.0F, 0F}});
        for (String[] set : SETS) {
            for (int i = 0; i < 4; i++) server.runCommand("item replace entity @a " + SLOT_ARGS[i] + " with " + id(set, i));
            server.runOnServer(s -> {
                for (Mannequin m : ms) for (int i = 0; i < 4; i++) m.setItemSlot(SLOTS[i], stack(id(set, i)));
            });
            ctx.getInput().lookAt(180F, 12F);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(6);
            ctx.takeScreenshot(TestScreenshotOptions.of("am_" + set[0]).withSize(960, 540));
            ctx.getInput().lookAt(0F, 0F);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(4);
            ctx.takeScreenshot(TestScreenshotOptions.of("a_" + set[0] + "_front").withSize(960, 540));
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(4);
            ctx.takeScreenshot(TestScreenshotOptions.of("a_" + set[0] + "_back").withSize(960, 540));
            boolean worn = server.computeOnServer(s -> {
                var sp = s.getPlayerList().getPlayers().get(0);
                for (EquipmentSlot slot : SLOTS) if (sp.getItemBySlot(slot).isEmpty()) return false;
                return true;
            });
            LOG.info("[gear-gallery] armor {} worn={}", set[0], worn);
        }
        server.runOnServer(s -> ms.forEach(Mannequin::discard));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(0F, 0F);
    }

    private static List<Mannequin> mannequins(TestServerContext server, float[][] at) {
        return server.computeOnServer(s -> {
            ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
            List<Mannequin> list = new ArrayList<>();
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
    }

    /** 황제 망토: 동쪽을 보는 마네킹을 틱마다 0.25칸씩 동쪽으로 옮김 (걷는 빠르기 → 망토가 뒤로 날림), 플레이어는 옆에서 봄 */
    private static void cape(ClientGameTestContext ctx, TestServerContext server) {
        String[] set = SETS[4];
        List<Mannequin> ms = mannequins(server, new float[][] {{-3.5F, -4.0F, -90F}});
        Mannequin m = ms.get(0);
        server.runOnServer(s -> {
            for (int i = 0; i < 4; i++) m.setItemSlot(SLOTS[i], stack(id(set, i)));
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(180F, 10F);
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("a_cape_t0").withSize(960, 540));
        for (int t = 1; t <= 16; t++) {
            server.runOnServer(s -> m.snapTo(m.getX() + 0.25, m.getY(), m.getZ(), -90F, 0F));
            ctx.waitTicks(1);
            if (t == 6 || t == 10 || t == 14) ctx.takeScreenshot(TestScreenshotOptions.of("a_cape_t" + t).withSize(960, 540));
        }
        ctx.waitTicks(20);
        ctx.takeScreenshot(TestScreenshotOptions.of("a_cape_stop").withSize(960, 540));
        server.runOnServer(s -> m.discard());
        ctx.getInput().lookAt(0F, 0F);
        LOG.info("[gear-gallery] cape");
    }

    /** 가방 화면: 황제 세트를 입고 핫바에 재료 몇 개, 흉갑 칸 위에 마우스 (설명) */
    private static void inventory(ClientGameTestContext ctx, TestServerContext server) {
        String[] set = SETS[4];
        for (int i = 0; i < 4; i++) server.runCommand("item replace entity @a " + SLOT_ARGS[i] + " with " + id(set, i));
        String[] mats = {"rpg:star_ore", "rpg:enhance_stone", "rpg:kraken_eye", "rpg:muspel_heart", "rpg:loot_troll", "rpg:rune_shard", "rpg:npc_tool"};
        int slot = 0;
        for (String m : mats) {
            if (BuiltInRegistries.ITEM.containsKey(Identifier.parse(m))) server.runCommand("item replace entity @a hotbar." + slot++ + " with " + m + (m.equals("rpg:npc_tool") ? "" : " 5"));
            else LOG.warn("[gear-gallery] no material {}", m);
        }
        ctx.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
        ctx.waitTicks(5);
        double[] at = ctx.computeOnClient(mc -> {
            var sc = mc.gui.screen();
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            double left = (sc.width - 176) / 2.0;
            double top = (sc.height - 166) / 2.0;
            return new double[] {(left + 8 + 8) * k, (top + 8 + 18 + 8) * k};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("a_inventory").withSize(1280, 720));
        ctx.setScreen(() -> null);
        LOG.info("[gear-gallery] inventory");
    }
}
