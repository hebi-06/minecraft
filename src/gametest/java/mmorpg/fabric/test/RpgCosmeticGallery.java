package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.client.ClientCosmetics;
import mmorpg.fabric.client.CosmeticAura;
import mmorpg.fabric.client.CosmeticLayer;
import mmorpg.fabric.client.CosmeticModels;
import mmorpg.fabric.client.CosmeticRender;
import mmorpg.fabric.client.CosmeticScreen;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.cosmetic.CosmeticDefs;
import mmorpg.fabric.cosmetic.Cosmetics;
import mmorpg.fabric.cosmetic.Look;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.13.0 장식 사진첩 (화면 시험 — MMORPG_ONLY=cosmetic, 실제 마우스 · 키 입력):
 * 스탯 창 아랫줄 장식 단추 (풀이) → 장식 창 (무기 탭 — 칼을 든 미리보기), 벚꽃 검 줄 → 미리보기 → 구매 (두 번 눌러 확인 — 골드 줄어듦 · 바로 입음),
 * 갑옷 탭 (투구 숨기기 · 성기사 세트 장착), 머리 (왕관 장착 · 고리 · 모자 입어 보기), 등 (뒤로 돌아간 미리보기 — 날개 장착 · 망토 · 악마 날개 입어 보기),
 * 오라 (별빛 장착), 칭호 (용사 장착), U 키로 열기 · 닫기, 세계에서 3인칭 앞 · 뒤 · 1인칭 (벚꽃 검 스킨), 두 번째 플레이어 (가짜 연결 — 장식을 입히면
 * 내 클라이언트에 모습이 옴): 앞 (칭호가 이름 위) · 뒤 · 옆, 진짜 갑옷 (다이아몬드) 을 입은 채 숨기기 · 빙룡 갑옷 스킨, 오라 셋 (불씨 · 별빛 · 서리 —
 * 1인칭이므로 내 오라는 뿌리지 않음).
 * 사진 이름 c_… → ci-logs 의 shots/. 확인 항목은 로그 [cosmetic-gallery] OK / FAIL.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgCosmeticGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-cosmetic-gallery");
    private static final Vec3 HOME = new Vec3(0.5, -60, 0.5);
    private static final Vec3 RIVAL_AT = new Vec3(0.5, -60, 3.5);

    private final List<String> failures = new ArrayList<>();
    private UUID rivalId;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("cosmetic")) return;
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
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            home(server, 0F, 0F);
            server.runOnServer(s -> {
                ServerPlayer me = me(s);
                Cosmetics.reset(me);
                me.getInventory().clearContent();
                // 영웅 칼을 쓸 수 있게 (요구 레벨 · 스탯 — 화면 하단 "필요" 안내가 사진을 가리지 않게)
                me.setExperienceLevels(30);
                RpgPlayers.checkLevelUp(me);
                Weapon w = Weapons.weaponFrom("sword", 30, "hero");
                for (Weapons.Req r : w.reqs()) RpgPlayers.setStat(me, r.stat(), (int) Math.ceil(r.value()) + 10);
                me.getInventory().setItem(0, new ItemStack(RpgItems.byId(w.id())));
                me.getInventory().setSelectedSlot(0);
                // 0.21.0 장식은 골드: 지갑 500 + 가방의 금화 40
                mmorpg.fabric.economy.Wallet.set(me, mmorpg.core.Economy.Kind.GOLD, 500);
                me.getInventory().setItem(9, new ItemStack(mmorpg.fabric.economy.EconomyItems.GOLD_COIN, 40));
            });
            ctx.waitTicks(20);
            String gui = ctx.computeOnClient(mc -> mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " → gui "
                    + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
            LOG.info("[cosmetic-gallery] window {}", gui);
            int models = ctx.computeOnClient(mc -> CosmeticModels.count());
            check(models == countModels(), "cosmetic models loaded " + models + " / " + countModels());
            check(ctx.computeOnClient(mc -> CosmeticModels.animCount("angel_wings")) == 2, "wing bones animate (2)");
            check(ctx.computeOnClient(mc -> CosmeticModels.animCount("halo")) == 2, "halo bob + spin bones");
            check(ctx.computeOnClient(mc -> CosmeticModels.animCount("crimson_cape")) == 1, "cape bone");
            check(ctx.computeOnClient(mc -> CosmeticModels.animCount("fairy_wings")) == 4, "fairy wing bones flutter (4 — upper · lower × 2)");
            check(ctx.computeOnClient(mc -> CosmeticModels.animCount("cat_ears")) == 2, "cat ear bones twitch (2)");
            check(ctx.computeOnClient(mc -> CosmeticModels.translucent("ice_crown") && CosmeticModels.translucent("frost_wings")
                    && !CosmeticModels.translucent("phoenix_wings")), "ice crown · frost wings translucent (phoenix not)");
            check(ctx.computeOnClient(mc -> CosmeticModels.translucent("fairy_wings") && !CosmeticModels.translucent("angel_wings")),
                    "fairy wings drawn translucent (angel wings not)");
            // 등 장식은 날갯짓 · 휘날림 중에도 몸을 뚫지 않음 (0.14.3 — 날개 끝이 앞으로 꺾여 몸을 뚫던 것): 가장 앞 꼭짓점이 몸통 등 면 (z 2 픽셀) 뒤
            for (CosmeticDefs.Def d : CosmeticDefs.of("back", null)) {
                if (d.model() == null) continue;
                float z = ctx.computeOnClient(mc -> CosmeticModels.frontmostZ(d.model()));
                check(z >= 1.95F, d.id() + " stays behind the back while moving (frontmost z " + z + " px, back = 2)");
            }
            part(ctx, "stats", () -> stats(ctx, conn));
            part(ctx, "buy", () -> buy(ctx, server, conn));
            server.runOnServer(s -> {
                for (String id : CosmeticDefs.all().keySet()) Cosmetics.give(me(s), id);
            });
            ctx.waitTicks(3);
            part(ctx, "armor", () -> armor(ctx, server, conn));
            part(ctx, "head", () -> head(ctx, server, conn));
            part(ctx, "back", () -> back(ctx, server, conn));
            part(ctx, "aura_title", () -> auraTitle(ctx, server, conn));
            part(ctx, "key", () -> key(ctx));
            part(ctx, "world", () -> world(ctx, server));
            part(ctx, "rival", () -> rival(ctx, server));
            part(ctx, "rival_armor", () -> rivalArmor(ctx, server));
            part(ctx, "auras", () -> auras(ctx, server));
            part(ctx, "fairy", () -> fairy(ctx, server));
            part(ctx, "looks", () -> looks(ctx, server));
            server.runOnServer(s -> {
                ServerPlayer r = rivalId == null ? null : s.getPlayerList().getPlayer(rivalId);
                if (r != null) s.getPlayerList().remove(r);
                if (rivalId != null) Forms.HEADLESS.remove(rivalId);
            });
            ctx.setScreen(() -> null);
        }
        LOG.info("[cosmetic-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    private static int countModels() {
        java.util.Set<String> m = new java.util.HashSet<>();
        for (CosmeticDefs.Def d : CosmeticDefs.all().values()) if (d.model() != null) m.add(d.model());
        return m.size();
    }

    // ------------------------------------------------------------ 스탯 창 → 장식 창

    private void stats(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, s -> s instanceof StatScreen, "stats")) return;
        hover(ctx, sc -> ((StatScreen) sc).bottomCenter(3));
        shot(ctx, "c_stats_button");
        click(ctx, conn, sc -> ((StatScreen) sc).bottomCenter(3));
        if (!waitScreen(ctx, s -> s instanceof CosmeticScreen, "cosmetic")) return;
        away(ctx);
        ctx.waitTicks(5);
        shot(ctx, "c_screen_weapon");
        String tab = ctx.computeOnClient(mc -> ((CosmeticScreen) mc.gui.screen()).tab());
        check(tab.equals("weapon"), "opens on the weapon tab (" + tab + ")");
        List<String> rows = ctx.computeOnClient(mc -> ((CosmeticScreen) mc.gui.screen()).rowIds());
        check(rows.size() >= 2 && rows.get(0).equals("default") && rows.contains("sakura_sword") && !rows.contains("hide"),
                "weapon rows (sword — held type, no hide row) " + rows);
    }

    private void buy(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        int before = server.computeOnServer(s -> (int) mmorpg.fabric.economy.Wallet.total(me(s), mmorpg.core.Economy.Kind.GOLD));
        int idx = rowIndex(ctx, "sakura_sword");
        click(ctx, conn, sc -> ((CosmeticScreen) sc).rowCenter(idx));
        away(ctx);
        ctx.waitTicks(3);
        shot(ctx, "c_weapon_preview");
        check(ctx.computeOnClient(mc -> CosmeticRender.skinnedThirdPerson) > 0, "preview holds the skinned sword (third person in the window)");
        click(ctx, conn, sc -> ((CosmeticScreen) sc).actionCenter());
        hover(ctx, sc -> ((CosmeticScreen) sc).actionCenter());
        shot(ctx, "c_buy_confirm");
        check(server.computeOnServer(s -> !Cosmetics.owns(me(s), "sakura_sword")), "first press only asks to confirm");
        click(ctx, conn, sc -> ((CosmeticScreen) sc).actionCenter());
        boolean owned = waitServer(ctx, server, s -> Cosmetics.owns(me(s), "sakura_sword"), 40);
        int after = server.computeOnServer(s -> (int) mmorpg.fabric.economy.Wallet.total(me(s), mmorpg.core.Economy.Kind.GOLD));
        int price = CosmeticDefs.get("sakura_sword").price();
        check(owned && after == before - price, "bought sakura sword (" + before + " → " + after + ", price " + price + ")");
        check(server.computeOnServer(s -> "sakura_sword".equals(Cosmetics.look(me(s)).weaponSkin("sword"))), "worn right after buying");
        ctx.waitTicks(4);
        away(ctx);
        shot(ctx, "c_bought");
        boolean synced = waitFor(ctx, mc -> ClientCosmetics.owns("sakura_sword"), 40);
        check(synced, "client knows it is owned");
    }

    // ------------------------------------------------------------ 갑옷 · 머리 · 등 · 오라 · 칭호

    private void armor(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        click(ctx, conn, sc -> ((CosmeticScreen) sc).tabCenter("armor"));
        click(ctx, conn, sc -> ((CosmeticScreen) sc).subCenter(0));
        List<String> rows = ctx.computeOnClient(mc -> ((CosmeticScreen) mc.gui.screen()).rowIds());
        check(rows.size() >= 3 && rows.get(1).equals("hide") && rows.contains("paladin_helmet"), "helmet rows " + rows);
        click(ctx, conn, sc -> ((CosmeticScreen) sc).rowCenter(1));
        click(ctx, conn, sc -> ((CosmeticScreen) sc).actionCenter());
        check(waitServer(ctx, server, s -> Cosmetics.look(me(s)).hidden("helmet"), 40), "helmet hidden by clicking (free)");
        // 성기사 세트: 부위마다 줄 → 장착
        for (int i = 0; i < 4; i++) {
            int piece = i;
            String id = "paladin_" + CosmeticDefs.ARMOR.get(i);
            click(ctx, conn, sc -> ((CosmeticScreen) sc).subCenter(piece));
            int row = rowIndex(ctx, id);
            click(ctx, conn, sc -> ((CosmeticScreen) sc).rowCenter(row));
            click(ctx, conn, sc -> ((CosmeticScreen) sc).actionCenter());
        }
        check(waitServer(ctx, server, s -> {
            Look l = Cosmetics.look(me(s));
            return "paladin_helmet".equals(l.get("helmet")) && "paladin_chest".equals(l.get("chest")) && "paladin_legs".equals(l.get("legs"))
                    && "paladin_boots".equals(l.get("boots"));
        }, 40), "paladin set worn");
        away(ctx);
        shot(ctx, "c_armor_paladin");
        // 모두 숨기기 단추 → 미리보기에서 갑옷이 사라짐 → 모두 원래대로
        click(ctx, conn, sc -> ((CosmeticScreen) sc).subCenter(4));
        check(waitServer(ctx, server, s -> Cosmetics.look(me(s)).hidden("chest") && Cosmetics.look(me(s)).hidden("boots"), 40), "hide all");
        away(ctx);
        shot(ctx, "c_armor_hidden");
        click(ctx, conn, sc -> ((CosmeticScreen) sc).subCenter(5));
        check(waitServer(ctx, server, s -> Cosmetics.look(me(s)).get("chest") == null, 40), "show all");
        // 다시 성기사 갑옷 (세계 사진용)
        for (String slot : CosmeticDefs.ARMOR) server.runOnServer(s -> Cosmetics.equip(me(s), "paladin_" + slot));
    }

    private void head(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        click(ctx, conn, sc -> ((CosmeticScreen) sc).tabCenter("head"));
        equipRow(ctx, conn, "crown");
        check(waitServer(ctx, server, s -> "crown".equals(Cosmetics.look(me(s)).get("head")), 40), "crown worn");
        away(ctx);
        shot(ctx, "c_head_crown");
        for (String id : List.of("halo", "wizard_hat", "cat_ears", "devil_horns", "flower_crown", "ice_crown")) {
            int row = rowIndex(ctx, id);
            click(ctx, conn, sc -> ((CosmeticScreen) sc).rowCenter(row));
            away(ctx);
            ctx.waitTicks(10);
            shot(ctx, "c_head_" + id + "_try");
        }
        check(server.computeOnServer(s -> "crown".equals(Cosmetics.look(me(s)).get("head"))), "trying on does not change the worn look");
    }

    private void back(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        click(ctx, conn, sc -> ((CosmeticScreen) sc).tabCenter("back"));
        equipRow(ctx, conn, "angel_wings");
        check(waitServer(ctx, server, s -> "angel_wings".equals(Cosmetics.look(me(s)).get("back")), 40), "wings worn");
        away(ctx);
        ctx.waitTicks(6);
        shot(ctx, "c_back_wings");
        for (String id : List.of("crimson_cape", "demon_wings", "fairy_wings", "phoenix_wings", "frost_wings", "royal_cape")) {
            int row = rowIndex(ctx, id);
            click(ctx, conn, sc -> ((CosmeticScreen) sc).rowCenter(row));
            away(ctx);
            ctx.waitTicks(10);
            shot(ctx, "c_back_" + id + "_try");
        }
        // 끌어서 돌리기 (앞으로)
        double[] p = at(ctx, sc -> ((CosmeticScreen) sc).previewCenter());
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        for (int i = 1; i <= 8; i++) {
            ctx.getInput().setCursorPos(p[0] - i * 12, p[1]);
            ctx.waitTick();
        }
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
        shot(ctx, "c_back_dragged");
    }

    private void auraTitle(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!ensureScreen(ctx)) return;
        click(ctx, conn, sc -> ((CosmeticScreen) sc).tabCenter("aura"));
        equipRow(ctx, conn, "star_aura");
        check(waitServer(ctx, server, s -> "star_aura".equals(Cosmetics.look(me(s)).get("aura")), 40), "star aura worn");
        away(ctx);
        shot(ctx, "c_aura_star");
        // 0.15.0 새 오라: 미리보기 (입어 보기) 그림
        for (String id : List.of("sakura_aura", "storm_aura", "heart_aura", "shadow_aura")) {
            int row = rowIndex(ctx, id);
            click(ctx, conn, sc -> ((CosmeticScreen) sc).rowCenter(row));
            away(ctx);
            ctx.waitTicks(5);
            shot(ctx, "c_aura_" + id + "_try");
        }
        click(ctx, conn, sc -> ((CosmeticScreen) sc).tabCenter("title"));
        equipRow(ctx, conn, "title_hero");
        check(waitServer(ctx, server, s -> "title_hero".equals(Cosmetics.look(me(s)).get("title")), 40), "hero title worn");
        int row = rowIndex(ctx, "title_slayer");
        hover(ctx, sc -> ((CosmeticScreen) sc).rowCenter(row));
        shot(ctx, "c_title_tip");
        // 무기 탭: 활 종류 → 서리 활 장착 (칼 스킨은 그대로)
        click(ctx, conn, sc -> ((CosmeticScreen) sc).tabCenter("weapon"));
        int bow = Weapons.allTypes().indexOf("bow");
        click(ctx, conn, sc -> ((CosmeticScreen) sc).subCenter(bow));
        equipRow(ctx, conn, "frost_bow");
        check(waitServer(ctx, server, s -> "frost_bow".equals(Cosmetics.look(me(s)).weaponSkin("bow"))
                && "sakura_sword".equals(Cosmetics.look(me(s)).weaponSkin("sword")), 40), "bow skin added, sword skin kept");
        away(ctx);
        shot(ctx, "c_weapon_bow");
    }

    private void key(ClientGameTestContext ctx) {
        closeScreen(ctx);
        ctx.getInput().pressKey(MmorpgClient.cosmeticKey);
        boolean open = waitScreen(ctx, s -> s instanceof CosmeticScreen, "cosmetic (U key)");
        check(open, "U key opens the cosmetics window");
        if (open) {
            ctx.getInput().pressKey(MmorpgClient.cosmeticKey);
            ctx.waitTicks(3);
            check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "U key closes it");
        }
        check(ctx.computeOnClient(mc -> ClientCosmetics.preview() == null), "closing drops the try-on look");
    }

    // ------------------------------------------------------------ 세계에서

    private void world(ClientGameTestContext ctx, TestServerContext server) {
        ctx.setScreen(() -> null);
        home(server, 0F, 0F);
        ctx.waitTicks(10);
        int drawn0 = ctx.computeOnClient(mc -> CosmeticLayer.drawn);
        int aura0 = ctx.computeOnClient(mc -> CosmeticAura.spawned);
        camera(ctx, CameraType.THIRD_PERSON_FRONT);
        ctx.waitTicks(30);
        shot(ctx, "c_world_front");
        // 왕관 (hideHelmet) → 성기사 투구는 그리지 않음 · 마법사 모자로 바꿔 봄 (사람 모델의 hat 뼈와 이름이 겹치지 않는지 — 머리 위에 앉음)
        server.runOnServer(s -> Cosmetics.equip(me(s), "wizard_hat"));
        ctx.waitTicks(15);
        shot(ctx, "c_world_wizard_hat");
        server.runOnServer(s -> Cosmetics.equip(me(s), "crown"));
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        ctx.waitTicks(10);
        shot(ctx, "c_world_back");
        check(ctx.computeOnClient(mc -> CosmeticLayer.drawn) > drawn0, "decorations drawn in the world");
        check(ctx.computeOnClient(mc -> CosmeticAura.spawned) > aura0, "aura particles spawned");
        // 달리기 → 망토 · 날개가 더 크게
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(12);
        shot(ctx, "c_world_run");
        ctx.getInput().releaseKey(o -> o.keyUp);
        camera(ctx, CameraType.FIRST_PERSON);
        int fp0 = ctx.computeOnClient(mc -> CosmeticRender.skinnedFirstPerson);
        ctx.waitTicks(8);
        shot(ctx, "c_fp_sword");
        check(ctx.computeOnClient(mc -> CosmeticRender.skinnedFirstPerson) > fp0, "first-person hand uses the skin");
        // 핫바 아이콘은 그대로 (가방 아이콘 · 수치 그대로): 손의 아이템 자체는 바뀌지 않음
        boolean plain = ctx.computeOnClient(mc -> !mc.player.getMainHandItem().has(net.minecraft.core.component.DataComponents.ITEM_MODEL)
                || !mc.player.getMainHandItem().get(net.minecraft.core.component.DataComponents.ITEM_MODEL).getPath().startsWith("cosmetic_"));
        check(plain, "the real item is untouched (icon stays)");
    }

    private void rival(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer r = RpgDuelGallery.rival(s, "Rival", RIVAL_AT, 180F);
            rivalId = r.getUUID();
            Forms.HEADLESS.add(rivalId);
            r.getInventory().setItem(0, new ItemStack(RpgItems.byId(Weapons.weaponFrom("axe", 30, "hero").id())));
            r.getInventory().setSelectedSlot(0);
            // 다이아몬드 갑옷 (바닐라 — 입을 조건 없음): 가짜 연결의 플레이어는 몸 틱이 돌지 않아 바꾼 장비를 보는 사람에게 보내는 일
            // (LivingEntity.detectEquipmentUpdates — 진짜 플레이어는 틱마다) 이 없으므로 직접 한 번 부름 (들고 있는 도끼도 함께 감)
            r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            r.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
            r.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
            r.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            sendEquipment(r);
            for (String id : CosmeticDefs.all().keySet()) Cosmetics.give(r, id);
            for (String id : List.of("lava_axe", "wizard_hat", "demon_wings", "ember_aura", "title_slayer", "frost_dragon_chest")) Cosmetics.equip(r, id);
        });
        ctx.waitTicks(30);
        boolean seen = ctx.computeOnClient(mc -> mc.level.players().stream().anyMatch(p -> p.getUUID().equals(rivalId)));
        check(seen, "rival visible to the client");
        boolean got = waitFor(ctx, mc -> "title_slayer".equals(ClientCosmetics.lookOf(rivalId).get("title")), 40);
        check(got, "rival's look reached my client (broadcast)");
        camera(ctx, CameraType.FIRST_PERSON);
        // 두 번째 플레이어는 그대로 두고 내가 둘레를 돌며 봄 (남의 몸 방향은 머리를 따라 늦게 돌아서)
        int third0 = ctx.computeOnClient(mc -> CosmeticRender.skinnedThirdPerson);
        viewRival(ctx, server, "front");
        shot(ctx, "c_rival_front");
        check(ctx.computeOnClient(mc -> CosmeticRender.skinnedThirdPerson) > third0, "rival's axe drawn with the lava skin");
        viewRival(ctx, server, "back");
        shot(ctx, "c_rival_back");
        viewRival(ctx, server, "side");
        shot(ctx, "c_rival_side");
    }

    private void rivalArmor(ClientGameTestContext ctx, TestServerContext server) {
        if (rivalId == null) return;
        // 입은 다이아몬드 갑옷 (처음부터) — 장식을 벗기면 진짜 갑옷이 보임 → 모두 숨기기 → 빙룡 갑옷 스킨
        server.runOnServer(s -> {
            ServerPlayer r = rival(s);
            Cosmetics.clear(r, "chest", null);
            Cosmetics.clear(r, "back", null);
            Cosmetics.clear(r, "head", null);
        });
        boolean worn = waitFor(ctx, mc -> mc.level.getPlayerByUUID(rivalId) != null
                && mc.level.getPlayerByUUID(rivalId).getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), 40);
        check(worn, "rival's real diamond armor reached my client");
        viewRival(ctx, server, "front");
        shot(ctx, "c_rival_diamond");
        server.runOnServer(s -> Cosmetics.action(rival(s), "hide_all", "", ""));
        boolean hidden = waitFor(ctx, mc -> ClientCosmetics.lookOf(rivalId).hidden("chest"), 40);
        check(hidden, "rival armor hidden on my client");
        ctx.waitTicks(5);
        shot(ctx, "c_rival_diamond_hidden");
        boolean stillWorn = server.computeOnServer(s -> rival(s).getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE));
        check(stillWorn, "hiding keeps the real chestplate on (looks only)");
        server.runOnServer(s -> {
            ServerPlayer r = rival(s);
            for (String id : List.of("frost_dragon_helmet", "frost_dragon_chest", "frost_dragon_legs", "frost_dragon_boots", "halo", "crimson_cape")) {
                Cosmetics.equip(r, id);
            }
        });
        ctx.waitTicks(15);
        shot(ctx, "c_rival_frost_dragon");
        viewRival(ctx, server, "back");
        shot(ctx, "c_rival_frost_dragon_back");
        viewRival(ctx, server, "front");
    }

    private void auras(ClientGameTestContext ctx, TestServerContext server) {
        if (rivalId == null) return;
        viewRival(ctx, server, "near");
        hideGui(ctx, true); // 손 · 화면 칸 없이 (발밑 오라가 가리지 않게 — F1)
        try {
            for (String id : List.of("ember_aura", "star_aura", "frost_aura", "sakura_aura", "storm_aura", "heart_aura", "shadow_aura")) {
                server.runOnServer(s -> Cosmetics.equip(rival(s), id));
                ctx.waitTicks(40);
                shot(ctx, "c_aura_" + id);
            }
        } finally {
            hideGui(ctx, false);
        }
    }

    /** 요정의 날개 (0.14.2): 두 번째 플레이어가 입음 (갑옷 숨김 · 오라 · 머리 장식 없이) — 뒤 · 뒤 가까이 · 옆 · 밤 (날개맥 빛) · 가루 */
    private void fairy(ClientGameTestContext ctx, TestServerContext server) {
        if (rivalId == null) return;
        int dust0 = ctx.computeOnClient(mc -> CosmeticAura.dustSpawned);
        server.runOnServer(s -> {
            ServerPlayer r = rival(s);
            Cosmetics.clear(r, "aura", null);
            Cosmetics.clear(r, "head", null);
            Cosmetics.action(r, "hide_all", "", "");
            Cosmetics.equip(r, "fairy_wings");
        });
        boolean worn = waitFor(ctx, mc -> "fairy_wings".equals(ClientCosmetics.lookOf(rivalId).get("back")), 40);
        check(worn, "rival's fairy wings reached my client");
        hideGui(ctx, true);
        try {
            viewRival(ctx, server, "back");
            shot(ctx, "c_fairy_back");
            viewRival(ctx, server, "back_near");
            shot(ctx, "c_fairy_back_near");
            viewRival(ctx, server, "side");
            shot(ctx, "c_fairy_side");
            // 날갯짓 다른 때 (약 6.6틱에 한 번 — 3틱 뒤) · 위에서 (날개가 등 뒤로 V 자인지 — 0.14.3)
            ctx.waitTicks(3);
            shot(ctx, "c_fairy_side_2");
            fly(server, true); // 위에서 볼 때 떨어지지 않게
            viewRival(ctx, server, "top");
            shot(ctx, "c_fairy_top");
            ctx.waitTicks(3);
            shot(ctx, "c_fairy_top_2");
            check(ctx.computeOnClient(mc -> CosmeticAura.dustSpawned) > dust0, "fairy dust spawned " + ctx.computeOnClient(mc -> CosmeticAura.dustSpawned - dust0));
            server.runCommand("time set 18000");
            viewRival(ctx, server, "back_near");
            ctx.waitTicks(20);
            shot(ctx, "c_fairy_night");
        } finally {
            fly(server, false);
            server.runCommand("time set 6000");
            hideGui(ctx, false);
        }
    }

    /** 0.15.0 새 장식 묶음 셋을 두 번째 플레이어가 입음 (갑옷 숨김) — 앞 · 뒤 사진, 새 칭호 (글자 ☾ ❄ ♥), 불사조 날개의 불티 */
    private void looks(ClientGameTestContext ctx, TestServerContext server) {
        if (rivalId == null) return;
        String[][] sets = {
                {"devil_horns", "phoenix_wings", "title_dark_lord", "shadow_aura"},
                {"cat_ears", "royal_cape", "title_fairy_friend", "heart_aura"},
                {"ice_crown", "frost_wings", "title_frost_lord", "storm_aura"},
                {"flower_crown", "fairy_wings", "title_light_guardian", "sakura_aura"}};
        hideGui(ctx, true);
        try {
            for (String[] set : sets) {
                int dust0 = ctx.computeOnClient(mc -> CosmeticAura.dustSpawned);
                server.runOnServer(s -> {
                    ServerPlayer r = rival(s);
                    Cosmetics.action(r, "hide_all", "", "");
                    for (String id : set) Cosmetics.equip(r, id);
                });
                boolean got = waitFor(ctx, mc -> set[1].equals(ClientCosmetics.lookOf(rivalId).get("back")) && set[0].equals(ClientCosmetics.lookOf(rivalId).get("head")), 40);
                check(got, "rival wears " + String.join(" · ", set));
                hideGui(ctx, false); // 앞: 이름표 위 칭호가 보이게 (F1 이면 이름표를 그리지 않음)
                viewRival(ctx, server, "front");
                shot(ctx, "c_look_" + set[0] + "_front");
                hideGui(ctx, true);
                viewRival(ctx, server, "back_near");
                shot(ctx, "c_look_" + set[0] + "_back");
                if (set[1].equals("phoenix_wings")) check(ctx.computeOnClient(mc -> CosmeticAura.dustSpawned) > dust0, "phoenix embers spawned");
            }
        } finally {
            hideGui(ctx, false);
        }
    }

    /** 나 (사진 찍는 플레이어) 날기 켜기 · 끄기 (위에서 보는 사진 — 서바이벌 세계) */
    private void fly(TestServerContext server, boolean on) {
        server.runOnServer(s -> {
            ServerPlayer p = me(s);
            p.getAbilities().mayfly = on;
            p.getAbilities().flying = on;
            p.onUpdateAbilities();
        });
    }

    /** 두 번째 플레이어를 보는 자리로 (앞 · 뒤 · 옆 · 가까이 — 두 번째 플레이어는 북쪽을 봄) */
    private void viewRival(ClientGameTestContext ctx, TestServerContext server, String where) {
        double[] p = switch (where) {
            case "back" -> new double[] {RIVAL_AT.x, RIVAL_AT.z + 3.0, 180};
            case "back_near" -> new double[] {RIVAL_AT.x, RIVAL_AT.z + 2.2, 180, 6};
            case "top" -> new double[] {RIVAL_AT.x, RIVAL_AT.z + 0.6, 180, 80, 2.4};
            case "side" -> new double[] {RIVAL_AT.x + 3.0, RIVAL_AT.z, 90};
            case "near" -> new double[] {RIVAL_AT.x, RIVAL_AT.z - 2.6, 0, 16};
            default -> new double[] {RIVAL_AT.x, RIVAL_AT.z - 3.0, 0};
        };
        // 수평으로 봄 (높은 모자로 올라간 이름표 위 칭호까지 들어오게 — 발은 핫바 위)
        double pitch = p.length > 3 ? p[3] : 0;
        double up = p.length > 4 ? p[4] : 0; // 위에서 볼 때 (블록)
        server.runCommand(String.format(Locale.ROOT, "tp @a[name=!Rival] %.2f %.2f %.2f %.1f %.1f", p[0], HOME.y + up, p[1], p[2], pitch));
        ctx.waitTicks(20);
        // 두 번째 플레이어의 몸통을 머리 쪽으로 (0.15.0 — 가만히 선 다른 플레이어는 몸통이 머리에서 50° 까지 비껴 있어 등 장식이 비스듬히 찍힘:
        // 한쪽 날개가 옆면만 보이는 등. 몸통은 각 클라이언트가 정하고, 서 있으면 머리와 같은 쪽에서 움직이지 않음)
        ctx.runOnClient(mc -> {
            var r = mc.level == null || rivalId == null ? null : mc.level.getPlayerByUUID(rivalId);
            if (r != null) {
                r.yBodyRot = r.getYHeadRot();
                r.yBodyRotO = r.yBodyRot;
            }
        });
        ctx.waitTicks(2);
    }

    // ------------------------------------------------------------ 도움

    private void equipRow(ClientGameTestContext ctx, TestServerConnection conn, String id) {
        int row = rowIndex(ctx, id);
        click(ctx, conn, sc -> ((CosmeticScreen) sc).rowCenter(row));
        click(ctx, conn, sc -> ((CosmeticScreen) sc).actionCenter());
    }

    /** 그 장식의 목록 줄 번호 — 6줄 밖이면 실제 마우스 휠로 굴려 보이게 함 (0.15.0 — 칸마다 8줄) */
    private int rowIndex(ClientGameTestContext ctx, String id) {
        int i = ctx.computeOnClient(mc -> mc.gui.screen() instanceof CosmeticScreen sc ? sc.rowIds().indexOf(id) : -1);
        if (i < 0) throw new AssertionError("no row " + id);
        for (int k = 0; k < 20 && !ctx.computeOnClient(mc -> mc.gui.screen() instanceof CosmeticScreen sc && sc.rowVisible(i)); k++) {
            double[] p = at(ctx, sc -> ((CosmeticScreen) sc).listCenter());
            ctx.getInput().setCursorPos(p[0], p[1]);
            ctx.waitTick();
            boolean below = ctx.computeOnClient(mc -> mc.gui.screen() instanceof CosmeticScreen sc && sc.rowCenter(i)[1] > sc.listCenter()[1]);
            ctx.getInput().scroll(below ? -1 : 1);
            ctx.waitTick();
        }
        return i;
    }

    private boolean ensureScreen(ClientGameTestContext ctx) {
        if (ctx.computeOnClient(mc -> mc.gui.screen() instanceof CosmeticScreen)) return true;
        ctx.setScreen(CosmeticScreen::new);
        return waitScreen(ctx, s -> s instanceof CosmeticScreen, "cosmetic");
    }

    private static void home(TestServerContext server, float yaw, float pitch) {
        server.runCommand(String.format(Locale.ROOT, "tp @a[name=!Rival] %.2f %.2f %.2f %.1f %.1f", HOME.x, HOME.y, HOME.z, yaw, pitch));
    }

    private ServerPlayer me(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!p.getUUID().equals(rivalId)) return p;
        throw new IllegalStateException("no client player");
    }

    private ServerPlayer rival(MinecraftServer s) {
        return s.getPlayerList().getPlayer(rivalId);
    }

    /** 바꾼 장비를 보는 사람에게 (진짜 플레이어는 몸 틱마다 바닐라가 함 — 가짜 연결 플레이어는 틱이 돌지 않아 직접) */
    private static void sendEquipment(ServerPlayer p) {
        try {
            java.lang.reflect.Method m = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("detectEquipmentUpdates");
            m.setAccessible(true);
            m.invoke(p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot send the rival's equipment", e);
        }
    }

    private boolean waitServer(ClientGameTestContext ctx, TestServerContext server, Predicate<MinecraftServer> test, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (server.computeOnServer(test::test)) return true;
            ctx.waitTick();
        }
        return server.computeOnServer(test::test);
    }

    /** 손 · HUD 숨기기 (F1 — 모습 사진) */
    private static void hideGui(ClientGameTestContext ctx, boolean hide) {
        ctx.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() != hide) mc.gui.hud.toggle();
        });
        ctx.waitTick();
    }

    private static void camera(ClientGameTestContext ctx, CameraType t) {
        ctx.runOnClient(mc -> mc.options.setCameraType(t));
        ctx.waitTick();
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[cosmetic-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[cosmetic-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.waitTicks(2);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[cosmetic-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[cosmetic-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
    }

    private static boolean waitFor(ClientGameTestContext ctx, Predicate<Minecraft> test, int ticks) {
        try {
            ctx.waitFor(mc -> mc.player != null && test.test(mc), ticks);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    private boolean waitScreen(ClientGameTestContext ctx, Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 100);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open (now " + ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()) + ")");
            return false;
        }
    }

    private static void closeScreen(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) ctx.setScreen(() -> null);
    }

    /** 지금 화면의 GUI 좌표 → 창 픽셀 좌표 */
    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen " + (sc == null ? "none" : sc.getClass().getSimpleName()));
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

}
