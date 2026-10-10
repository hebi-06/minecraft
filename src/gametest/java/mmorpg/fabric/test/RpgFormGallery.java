package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.client.form.DialogFormScreen;
import mmorpg.fabric.client.form.FormClient;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.client.form.ModalFormScreen;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.shop.Store;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.DialogForm;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.MenuForm;
import mmorpg.fabric.ui.ModalForm;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 4단계 창 사진첩 (화면 시험 — 실제 마우스 · 키 입력): 서버 창 (상자 창 · 대화창 · 버튼 목록 · 입력창) 과 NPC.
 * 상점 (무기 · 툴팁 · 구매 확인 → 사기 → 결과 문구 · 갑옷 · 부위 · 전리품 팔기 · ESC 로 닫으면 서버 흐름 끝), 상자 창 숫자 (큰 수) · 긴 본문 (휠),
 * NPC 마네킹 (이름표 · 머리 위 표시 · 화면 하단 안내 · 우클릭 → 대화 (다음 장 · 선택지) → 상점 · 닫기 단추 · 옆으로 걸을 때 고개 돌리기),
 * 긴 대화 (선택지 많음 · 휠 · 숫자 키), 버튼 목록 (많아서 휠 스크롤 · 닫기 단추),
 * 입력창 (글자 치기 · 고르기 목록 · 켜기 · 밀대 끌기 · 스크롤 → 확인 → 서버가 받은 값 · ESC 면 null),
 * NPC 설정 창 (설정 도구로 NPC 우클릭 → 설정 창 → 퀘스트 만들기 입력창 · ESC 면 설정 창으로).
 * 사진 이름 f_… → ci-logs 의 shots/. 확인 항목은 로그 [form-gallery] OK / FAIL (사진첩이라 시험을 실패시키지는 않음).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgFormGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-form-gallery");
    /** NPC 자리 (플레이어는 북쪽 3칸에서 남쪽을 봄 — NPC 는 북쪽을 봄) */
    private static final Vec3 NPC_AT = new Vec3(0.5, -60, 3.5);

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("form")) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만 · 고른 시험만 (MMORPG_ONLY)
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            String gui = ctx.computeOnClient(mc -> mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " → gui "
                    + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight() + " scale " + mc.getWindow().getGuiScale());
            LOG.info("[form-gallery] window {}", gui);
            part(ctx, "shop", () -> shop(ctx, server, conn));
            part(ctx, "chest", () -> chestExtras(ctx, server, conn));
            part(ctx, "npc", () -> npc(ctx, server, conn));
            part(ctx, "dialog", () -> dialogExtras(ctx, server, conn));
            part(ctx, "menu", () -> menu(ctx, server, conn));
            part(ctx, "modal", () -> modal(ctx, server, conn));
            part(ctx, "editor", () -> editor(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[form-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 상점 (상자 창)

    private void shop(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
            sp.getInventory().setItem(10, new ItemStack(Items.EMERALD, 64));
            sp.getInventory().setItem(11, new ItemStack(GearItems.byId("rpg:loot_troll"), 12));
            sp.getInventory().setItem(12, new ItemStack(GearItems.byId("rpg:loot_nix"), 7));
            sp.getInventory().setItem(13, new ItemStack(GearItems.byId("rpg:loot_scarecrow"), 30));
        });
        ctx.waitTicks(5);
        server.runOnServer(s -> Store.openStore(player(s), 10, "weapons"));
        if (!waitChest(ctx, c -> c.title().contains("무기 상점"), "weapon shop")) return;
        away(ctx);
        shot(ctx, "f_shop_weapons");
        // 칸 위에 마우스 → 이름 · 설명 (10렙 일반 칼)
        hover(ctx, chestSlot(11));
        shot(ctx, "f_shop_tooltip");
        // 맨 오른쪽 아래 칸 (툴팁이 화면 밖으로 나가지 않는지)
        hover(ctx, chestSlot(25));
        shot(ctx, "f_shop_tooltip_edge");

        Weapon w = Weapons.weaponFrom("sword", 10, "common");
        int price = (int) Weapons.shopPrice(w);
        int money0 = server.computeOnServer(s -> Inv.money(player(s)));
        click(ctx, conn, chestSlot(11));
        if (!waitChest(ctx, c -> c.title().contains("구매 확인"), "purchase confirm")) return;
        away(ctx);
        shot(ctx, "f_shop_confirm");
        hover(ctx, chestSlot(11));
        shot(ctx, "f_shop_confirm_hover");
        click(ctx, conn, chestSlot(11));
        check(waitChest(ctx, c -> c.title().contains("무기 상점") && c.body().contains("구매 완료"), "shop after buying"), "bought: back to the shop, result in the body");
        int got = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RpgItems.byId(w.id())));
        int money1 = server.computeOnServer(s -> Inv.money(player(s)));
        check(got == 1 && money1 == money0 - price, "bought sword x" + got + ", emeralds " + money0 + " -> " + money1 + " (price " + price + ")");
        away(ctx);
        shot(ctx, "f_shop_bought");

        // 갑옷 상점 탭 (윗줄 오른쪽 끝) → 힘 갑옷 부위 창
        click(ctx, conn, chestSlot(8));
        if (!waitChest(ctx, c -> c.title().contains("갑옷 상점"), "armor shop")) return;
        away(ctx);
        shot(ctx, "f_shop_armor");
        hover(ctx, chestSlot(11));
        shot(ctx, "f_shop_armor_tooltip");
        click(ctx, conn, chestSlot(11));
        if (!waitChest(ctx, c -> c.title().contains("갑옷") && !c.title().contains("상점"), "armor pieces")) return;
        away(ctx);
        shot(ctx, "f_shop_armor_pieces");
        hover(ctx, chestSlot(10));
        shot(ctx, "f_shop_armor_pieces_tooltip");
        click(ctx, conn, chestSlot(22)); // 돌아가기
        if (!waitChest(ctx, c -> c.title().contains("갑옷 상점"), "armor shop again")) return;

        // 전리품 팔기 탭 → 트롤 엄니 → 모두 팔기
        click(ctx, conn, chestSlot(6));
        if (!waitChest(ctx, c -> c.title().contains("전리품 팔기"), "sell window")) return;
        away(ctx);
        shot(ctx, "f_shop_sell");
        hover(ctx, chestSlot(15));
        shot(ctx, "f_shop_sell_tooltip");
        int emeraldsBefore = server.computeOnServer(s -> Inv.money(player(s)));
        click(ctx, conn, chestSlot(15));
        if (!waitChest(ctx, c -> c.title().contains("트롤 엄니"), "sell amount")) return;
        away(ctx);
        shot(ctx, "f_shop_sell_amount");
        click(ctx, conn, chestSlot(13)); // 모두
        check(waitChest(ctx, c -> c.title().contains("전리품 팔기") && c.body().contains("판매"), "sold"), "sold: back to the sell window, result in the body");
        int trolls = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId("rpg:loot_troll")));
        int emeraldsAfter = server.computeOnServer(s -> Inv.money(player(s)));
        check(trolls == 0 && emeraldsAfter == emeraldsBefore + 12 * 5, "sold 12 troll tusks: left " + trolls + ", emeralds " + emeraldsBefore + " -> " + emeraldsAfter);
        away(ctx);
        shot(ctx, "f_shop_sold");

        // ESC → 창이 닫히고 서버 흐름도 끝남
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        check(waitClosed(ctx), "ESC closes the chest window");
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        check(!server.computeOnServer(s -> Forms.pending(player(s))), "ESC ends the shop flow on the server (Forms.pending false)");
    }

    /** 상자 창 숫자 (1 · 64 · 999 · 12345 · 가격) · 장식 · 빈 칸 · 화면보다 긴 본문 (휠) · 닫기 단추 */
    private void chestExtras(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        AtomicInteger picked = new AtomicInteger(-99);
        server.runOnServer(s -> {
            ChestForm f = new ChestForm("숫자 · 긴 본문 §8· §7시험용 상자 창 (제목이 아주 길면 닫기 단추 앞에서 잘립니다)");
            int[] counts = {1, 9, 64, 99, 128, 999, 1000, 12345, 99999};
            for (int i = 0; i < counts.length; i++) f.set(i, Slot.of(Items.EMERALD, "§a보유 " + counts[i]).count(counts[i]));
            int[] prices = {1, 8, 24, 120, 999, 2500};
            for (int i = 0; i < prices.length; i++) f.set(9 + i, Slot.of(Items.IRON_SWORD, "§f칼 · 가격 " + prices[i]).price(prices[i]).lore("§7설명 줄", "§a가격: " + prices[i]));
            f.set(16, Slot.of(Icon.sprite("q_new"), "§e장식 칸 (누를 수 없음)").deco());
            f.set(18, Slot.of(Icon.sprite("icon_check"), "§a확인"));
            f.set(20, Slot.of(Icon.sprite("icon_next"), "§b다음"));
            f.set(22, Slot.of(Icon.sprite("item_unknown"), "§7모르는 그림"));
            f.set(26, Slot.of(Icon.sprite("icon_cross"), "§c닫기"));
            List<String> lines = new ArrayList<>();
            for (int i = 1; i <= 26; i++) lines.add(i % 5 == 0 ? "§2" + i + "번째 줄: 초록 글자 · §4빨간 글자 §0검은 글자§r" : "§8" + i + "번째 줄 — 본문이 화면보다 길면 휠로 넘겨 봅니다.§r");
            f.body(String.join("\n", lines));
            f.show(player(s), sel -> picked.set(sel));
        });
        if (!waitChest(ctx, c -> c.title().contains("숫자"), "numbers chest")) return;
        away(ctx);
        shot(ctx, "f_chest_numbers");
        int[] b = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChestFormScreen c ? c.bounds() : null);
        int sh = ctx.computeOnClient(mc -> mc.gui.screen().height);
        check(b != null && b[1] >= 0 && b[1] + b[3] <= sh, "long chest fits the screen: " + (b == null ? "?" : b[1] + "+" + b[3] + " <= " + sh));
        hover(ctx, chestSlot(7));
        shot(ctx, "f_chest_numbers_hover");
        hover(ctx, sc -> {
            int[] r = ((ChestFormScreen) sc).bounds();
            return new int[] {r[0] + r[2] / 2, r[1] + r[3] - 20};
        });
        ctx.getInput().scroll(-30);
        ctx.waitTicks(2);
        shot(ctx, "f_chest_body_scrolled");
        // 장식 칸은 눌러도 응답 없음
        click(ctx, conn, chestSlot(16));
        check(picked.get() == -99 && ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChestFormScreen), "deco slot is not clickable");
        // 닫기 단추
        hover(ctx, sc -> ((ChestFormScreen) sc).closeCenter());
        shot(ctx, "f_chest_close_hover");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).closeCenter());
        ctx.waitTicks(2);
        check(picked.get() == -1 && waitClosed(ctx), "chest close button → -1 (got " + picked.get() + ")");
    }

    // ------------------------------------------------------------ NPC (대화창)

    private void npc(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("tp @a 0.5 -60 -6.5 0 0"); // 안내 범위 (5칸) 밖
        ctx.waitTicks(5);
        Mannequin npc = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            Mannequin m = NpcData.spawnAt(sp.level(), NPC_AT, new Vec3(NPC_AT.x, NPC_AT.y + 1.6, NPC_AT.z - 3));
            NpcData.setName(m, "상인 한나");
            NpcData.setGreeting(m, "어서 오세요, 모험가님! 오늘은 좋은 무기가 많이 들어왔어요.||칼 · 단검 · 도끼 · 방패 · 활 · 완드, 무엇이든 말씀하세요.|몬스터 전리품도 사 드립니다.");
            NpcData.setShop(m, 10);
            NpcData.refresh(m, 0);
            return m;
        });
        conn.waitForClientboundEntityUpdates(EntityTypes.MANNEQUIN);
        int id = npc.getId();
        try {
            ctx.waitTicks(5);
            lookAtNpc(ctx, id);
            ctx.waitTicks(20);
            shot(ctx, "f_npc_far");
            String tag = ctx.computeOnClient(mc -> {
                Entity e = mc.level.getEntity(id);
                return e == null || e.getCustomName() == null ? "(none)" : e.getCustomName().getString() + (e.isCustomNameVisible() ? "" : " (hidden)");
            });
            check(tag.contains("[상점]") && tag.contains("상인 한나") && !tag.contains("hidden"), "name tag on client: " + tag);

            // 다가감 (3칸 — 안내 범위 안): 화면 하단 안내 · 머리 위 표시
            server.runCommand("tp @a 0.5 -60 0.5");
            ctx.waitTicks(3);
            lookAtNpc(ctx, id);
            ctx.waitTicks(12);
            shot(ctx, "f_npc_near");

            // 실제 우클릭 → 대화 (첫 장 · 다음)
            lookAtNpc(ctx, id);
            ctx.waitTick();
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            conn.waitForServerboundPackets();
            ctx.waitTicks(3);
            conn.waitForClientboundPackets();
            if (!waitDialog(ctx, d -> d.text().contains("어서 오세요"), "first page")) return;
            away(ctx);
            check(ctx.computeOnClient(mc -> FormClient.dialogOpen()), "dialog open (bottom HUD hidden)");
            shot(ctx, "f_npc_dialog");
            hover(ctx, dialogChoice(0));
            shot(ctx, "f_npc_dialog_hover");
            click(ctx, conn, dialogChoice(0)); // 다음
            if (!waitDialog(ctx, d -> d.text().contains("전리품") && d.choices().size() >= 2, "last page")) return;
            hover(ctx, dialogChoice(0));
            shot(ctx, "f_npc_dialog2_hover");
            click(ctx, conn, dialogChoice(0)); // 상점
            check(waitChest(ctx, c -> c.title().contains("무기 상점"), "shop from npc"), "npc dialog → weapon shop");
            away(ctx);
            shot(ctx, "f_npc_shop");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            check(waitClosed(ctx), "ESC closes the npc shop");
            conn.waitForServerboundPackets();
            ctx.waitTicks(3);
            check(!server.computeOnServer(s -> Forms.pending(player(s))), "npc flow ended on the server");

            // 대화창 닫기 단추: 다시 눌러 첫 장에서 X
            lookAtNpc(ctx, id);
            ctx.waitTicks(2);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            conn.waitForServerboundPackets();
            ctx.waitTicks(3);
            conn.waitForClientboundPackets();
            if (waitDialog(ctx, d -> d.text().contains("어서 오세요"), "first page again")) {
                hover(ctx, sc -> ((DialogFormScreen) sc).closeCenter());
                shot(ctx, "f_npc_dialog_close_hover");
                click(ctx, conn, sc -> ((DialogFormScreen) sc).closeCenter());
                check(waitClosed(ctx), "dialog close button");
                ctx.waitTicks(2);
                check(!server.computeOnServer(s -> Forms.pending(player(s))), "dialog close ends the flow");
                check(!ctx.computeOnClient(mc -> FormClient.dialogOpen()), "HUD back after the dialog");
            }

            server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f", NPC_AT.x - 2.6, NPC_AT.y, NPC_AT.z - 1.5));
            headTurn(ctx, id);
        } finally {
            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(s -> npc.discard());
        }
    }

    /**
     * 고개 돌리기: NPC 정면에서 서쪽으로 60° 자리에 서서 → NPC 를 보며 왼쪽 걸음 (NPC 앞을 지나 동쪽으로 돌아감). NPC 머리가 플레이어를 따라오는지
     * (머리 방향과 플레이어 쪽 방향의 차이 — 몸에서 75° 까지만)
     */
    private void headTurn(ClientGameTestContext ctx, int id) {
        ctx.waitTicks(2);
        lookAtNpc(ctx, id);
        ctx.waitTicks(25);
        float[] h0 = head(ctx, id);
        check(h0 != null && Math.abs(h0[3]) < 10, "head turned to the player standing west-front: " + fmt(h0));
        shot(ctx, "f_npc_head_west");
        ctx.getInput().holdKey(o -> o.keyLeft);
        float worst = 0;
        for (int t = 1; t <= 32; t++) {
            lookAtNpc(ctx, id);
            ctx.waitTick();
            if (t % 4 == 0) {
                float[] h = head(ctx, id);
                LOG.info("[form-gallery] walking t={} {}", t, fmt(h));
                if (h != null && t >= 8) worst = Math.max(worst, Math.abs(h[3]));
            }
            if (t == 16) shot(ctx, "f_npc_head_walk");
        }
        ctx.getInput().releaseKey(o -> o.keyLeft);
        // 서버가 돌린 머리 → 패킷 → 클라이언트 보간이라 앞을 보지 않으면 걷는 동안 20° 넘게 늦음 (예전 2틱마다 35%: 32°, 틱마다 50%: 22°)
        check(worst < 15, "head follows while walking (worst lag " + worst + "°)");
        lookAtNpc(ctx, id);
        ctx.waitTicks(20);
        float[] h1 = head(ctx, id);
        check(h1 != null && Math.abs(h1[3]) < 10, "head turned after walking past: " + fmt(h1));
        shot(ctx, "f_npc_head_east");
    }

    /** [머리 yaw, 바라볼 yaw (몸에서 ±75° 안), 몸 yaw, 차이] — 클라이언트 */
    private static float[] head(ClientGameTestContext ctx, int id) {
        return ctx.computeOnClient(mc -> {
            if (!(mc.level.getEntity(id) instanceof Mannequin m)) return null;
            Vec3 a = m.getEyePosition();
            Vec3 b = mc.player.getEyePosition();
            float yaw = (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
            float body = m.yBodyRot;
            float want = body + Math.max(-75F, Math.min(75F, wrap(yaw - body)));
            return new float[] {m.getYHeadRot(), want, body, wrap(m.getYHeadRot() - want)};
        });
    }

    private static String fmt(float[] h) {
        return h == null ? "(npc missing)" : String.format(Locale.ROOT, "head %.1f want %.1f body %.1f diff %.1f", h[0], h[1], h[2], h[3]);
    }

    private static float wrap(float a) {
        a %= 360F;
        if (a >= 180F) a -= 360F;
        if (a < -180F) a += 360F;
        return a;
    }

    private static void lookAtNpc(ClientGameTestContext ctx, int id) {
        ctx.runOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, e.getEyePosition().add(0, -0.15, 0));
        });
    }

    /** 긴 대사 · 긴 이름 · 선택지 9개 (휠) · 숫자 키로 고르기, 채팅이 보이는 채로 (대화 상자 뒤로 비치지 않는지) */
    private void dialogExtras(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        AtomicInteger picked = new AtomicInteger(-99);
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
        for (int i = 1; i <= 6; i++) server.runCommand("tellraw @a \"채팅 " + i + ": 대화 상자 뒤로 채팅 글자가 비쳐 보이는지 확인하는 줄\"");
        server.runOnServer(s -> {
            DialogForm d = new DialogForm("§e여관 주인 잉그리드 §7(마을 광장)", "오늘도 일거리가 산더미예요. 몬스터 전리품을 가져다주면 에메랄드와 경험치를 넉넉히 드릴게요.\n"
                    + "§7(반복 의뢰는 하루에 세 번까지 받을 수 있습니다. 아주 긴 대사는 상자 폭에 맞춰 줄이 바뀝니다.)");
            d.choice("§e[의뢰] 트롤 엄니 10개 모아 오기", DialogForm.NEW);
            d.choice("§f[진행 중] 굴론 털가죽 5개", DialogForm.ACTIVE);
            d.choice("§a[보고] 닉스 비늘 8개 — 다 모았어요!", DialogForm.READY);
            d.choice("§b다음 이야기", DialogForm.NEXT);
            d.choice("§a무기 · 갑옷 상점 §7(20렙)", DialogForm.SHOP);
            d.choice("§6무기 강화 · 레벨 올리기", Icon.item(Items.ANVIL));
            d.choice("§d룬 갈기 · 뽑기권", Icon.item("rpg:rune_shard"));
            d.choice("§f아주 긴 선택지 글자는 상자 끝에서 잘리고 점 세 개로 끝납니다 — 끝까지 보이지 않아요");
            d.choice("§7안녕히 계세요", DialogForm.BYE);
            d.show(player(s), sel -> picked.set(sel));
        });
        if (!waitDialog(ctx, d -> d.choices().size() == 9, "long dialog")) return;
        away(ctx);
        shot(ctx, "f_dialog_long_chat");
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
        ctx.waitTick();
        hover(ctx, dialogChoice(0));
        ctx.getInput().scroll(-10);
        ctx.waitTicks(2);
        away(ctx);
        shot(ctx, "f_dialog_long_scrolled");
        ctx.getInput().pressKey(InputConstants.KEY_1 + 1); // 숫자 키 2 → 두 번째 선택지
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        check(picked.get() == 1 && waitClosed(ctx), "dialog number key 2 → choice 1 (got " + picked.get() + ")");
    }

    // ------------------------------------------------------------ 버튼 목록 창

    private void menu(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        AtomicInteger picked = new AtomicInteger(-99);
        int[] count = {0};
        server.runOnServer(s -> {
            MenuForm m = new MenuForm("§l관리자 메뉴 §r§7· NPC · 퀘스트", "NPC 와 퀘스트를 설정합니다.\n버튼이 많으면 휠로 넘깁니다.");
            m.button("§aNPC 세우기", Icon.item(Items.ARMOR_STAND));
            m.button("§eNPC 목록 §7(12)", Icon.item(Items.PLAYER_HEAD));
            m.button("§b퀘스트 만들기", DialogForm.NEW);
            m.button("§f진행 중인 퀘스트", DialogForm.ACTIVE);
            m.button("§a보고할 퀘스트", DialogForm.READY);
            m.button("§6상점 설정 §7(무기 · 갑옷 · 전리품)", Icon.item(Items.EMERALD));
            m.button("§6대장장이 · 강화 확률", Icon.item(Items.ANVIL));
            m.button("§d룬술사 · 뽑기권", Icon.item("rpg:rune_shard"));
            m.button("§f허수아비 세우기", Icon.item(Items.HAY_BLOCK));
            m.button("§7그림 없는 버튼");
            m.button("§c모두 지우기 §8(되돌릴 수 없음 — 아주 긴 설명이 붙은 버튼은 끝이 잘려 보입니다)", DialogForm.BYE);
            for (int i = 1; i <= 6; i++) m.button("§f메인 퀘스트 " + i + "장 §7· 마을의 부탁", Icon.sprite("q_new"));
            m.button("§a닫기", DialogForm.OK);
            count[0] = m.size();
            m.show(player(s), sel -> {
                picked.set(sel);
                LOG.info("[form-gallery] menu answer {}", sel);
            });
        });
        if (!waitScreen(ctx, sc -> sc instanceof MenuFormScreen, "menu")) return;
        away(ctx);
        shot(ctx, "f_menu");
        hover(ctx, sc -> ((MenuFormScreen) sc).buttonCenter(2));
        shot(ctx, "f_menu_hover");
        // 휠로 끝까지 내려 마지막 앞 버튼 (메인 퀘스트 6장) 을 누름
        hover(ctx, sc -> ((MenuFormScreen) sc).buttonCenter(3));
        ctx.getInput().scroll(-30);
        ctx.waitTicks(2);
        shot(ctx, "f_menu_scrolled");
        int want = count[0] - 2;
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(want));
        ctx.waitTicks(2);
        check(picked.get() == want, "menu: clicked button " + want + " after scrolling → server got " + picked.get());
        check(waitClosed(ctx), "menu closes when the flow ends");

        // 닫기 단추 → -1
        picked.set(-99);
        server.runOnServer(s -> new MenuForm("§l짧은 메뉴", "").button("§a하나", DialogForm.OK).button("§c둘", DialogForm.BYE)
                .show(player(s), sel -> picked.set(sel)));
        if (!waitScreen(ctx, sc -> sc instanceof MenuFormScreen, "short menu")) return;
        hover(ctx, sc -> ((MenuFormScreen) sc).closeCenter());
        shot(ctx, "f_menu_short");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).closeCenter());
        ctx.waitTicks(2);
        check(picked.get() == -1 && waitClosed(ctx), "menu close button → -1 (got " + picked.get() + ")");
    }

    // ------------------------------------------------------------ 입력창

    private void modal(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        AtomicReference<Map<String, Object>> got = new AtomicReference<>();
        AtomicInteger answers = new AtomicInteger();
        server.runOnServer(s -> new ModalForm("§lNPC 설정 §r§8· §7n1 상인 한나")
                .header("기본 정보")
                .label("§7이름과 인사말을 정합니다. 인사말에서 | 는 줄바꿈, || 는 다음 장입니다.")
                .text("name", "이름", "예: 상인 한나", "상인 한나")
                .text("greet", "인사말", "비우면 누르자마자 상점", "")
                .divider()
                .header("역할")
                .dropdown("shop", "상점 레벨", List.of("상점 아님", "0렙", "5렙", "10렙", "15렙", "20렙", "30렙", "40렙"), 3)
                .toggle("smith", "대장장이 (무기 강화)", false)
                .toggle("rune", "룬술사 (룬 갈기 · 뽑기권)", true)
                .slider("range", "안내 거리 (칸)", 1, 16, 5)
                .divider()
                .label("§8아래는 스크롤 확인용 설명 줄입니다. 창이 화면보다 길면 휠로 내려 확인 단추를 누릅니다.")
                .text("memo", "메모", "운영자 메모", "")
                .submit("§a저장")
                .show(player(s), v -> {
                    got.set(v);
                    answers.incrementAndGet();
                    LOG.info("[form-gallery] modal answer {}", v);
                }));
        if (!waitScreen(ctx, sc -> sc instanceof ModalFormScreen, "modal")) return;
        away(ctx);
        shot(ctx, "f_modal");
        // 인사말 칸 (입력 1) 을 눌러 글자 치기
        String greet = "안녕하세요! 좋은 하루 되세요";
        clickInput(ctx, 1, 0.5);
        ctx.getInput().typeChars(greet);
        ctx.waitTicks(2);
        shot(ctx, "f_modal_typed");
        // 고르기 (입력 2): 누르면 목록이 펼쳐짐 → 15렙 (4) 을 고름
        clickInput(ctx, 2, 0.5);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ModalFormScreen m && m.listOpen()), "dropdown list opens on click");
        hover(ctx, sc -> ((ModalFormScreen) sc).listItemCenter(4));
        shot(ctx, "f_modal_dropdown");
        click(ctx, conn, sc -> ((ModalFormScreen) sc).listItemCenter(4));
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ModalFormScreen m && !m.listOpen() && "4".equals(m.values().get(2))),
                "dropdown picked 15렙 (index 4) and folded");
        // 목록을 다시 펼쳐 ESC → 목록만 접힘 (창은 그대로)
        clickInput(ctx, 2, 0.5);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ModalFormScreen m && !m.listOpen() && "4".equals(m.values().get(2))),
                "ESC folds only the dropdown list");
        // 대장장이 켜기 (입력 3)
        clickInput(ctx, 3, 0.5);
        // 밀대 (입력 5) 끌기: 왼쪽 끝 → 오른쪽 3/4
        dragSlider(ctx, 5, 0.05, 0.75);
        away(ctx);
        shot(ctx, "f_modal_filled");
        hover(ctx, sc -> center(((ModalFormScreen) sc).input(5)));
        ctx.getInput().scroll(-20);
        ctx.waitTicks(2);
        hover(ctx, sc -> center(((ModalFormScreen) sc).submitButton()));
        shot(ctx, "f_modal_scrolled");
        List<String> shown = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ModalFormScreen m ? m.values() : List.of());
        LOG.info("[form-gallery] modal values on client {}", shown);
        click(ctx, conn, sc -> center(((ModalFormScreen) sc).submitButton()));
        ctx.waitTicks(3);
        Map<String, Object> v = got.get();
        check(answers.get() == 1 && v != null, "modal submitted (" + answers.get() + " answers)");
        if (v != null) {
            check("상인 한나".equals(v.get("name")), "modal name kept: " + v.get("name"));
            check(greet.equals(v.get("greet")), "modal typed text: " + v.get("greet"));
            check(Integer.valueOf(4).equals(v.get("shop")), "modal dropdown 10렙 → 15렙 (4): " + v.get("shop"));
            check(Boolean.TRUE.equals(v.get("smith")) && Boolean.TRUE.equals(v.get("rune")), "modal toggles smith on / rune on: " + v.get("smith") + " / " + v.get("rune"));
            Object range = v.get("range");
            check(range instanceof Integer r && r >= 10 && r <= 14 && shown.size() > 5 && String.valueOf(r).equals(shown.get(5)),
                    "modal slider dragged to ~3/4 of 1..16: " + range + " (client " + (shown.size() > 5 ? shown.get(5) : "?") + ")");
        }
        check(waitClosed(ctx), "modal closes after submit");

        // 짧은 입력창: ESC 로 닫으면 null
        got.set(Map.of("unset", true));
        server.runOnServer(s -> new ModalForm("§l짧은 입력창").text("t", "글자", "", "").toggle("b", "켜기", true)
                .dropdown("d", "고르기 (길면 목록이 휠로 넘어감)", List.of("하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉", "열", "열하나", "열둘"), 0)
                .show(player(s), r -> {
                    got.set(r);
                    answers.incrementAndGet();
                }));
        if (!waitScreen(ctx, sc -> sc instanceof ModalFormScreen, "short modal")) return;
        away(ctx);
        shot(ctx, "f_modal_short");
        clickInput(ctx, 2, 0.5);
        hover(ctx, sc -> ((ModalFormScreen) sc).listItemCenter(2));
        ctx.getInput().scroll(-3);
        ctx.waitTicks(2);
        shot(ctx, "f_modal_short_list");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE); // 목록 접기
        ctx.waitTicks(2);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE); // 창 닫기
        check(waitClosed(ctx), "ESC closes the modal");
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        check(answers.get() == 2 && got.get() == null, "modal ESC → server got null (" + got.get() + ")");
    }

    // ------------------------------------------------------------ NPC 설정 창 (editor.js)

    /**
     * NPC 설정 도구를 들고 NPC 를 실제 우클릭 (크리에이티브) → 그 NPC 설정 창 → 퀘스트 추가 → 퀘스트 만들기 입력창 (위 · 휠로 가운데 · 아래),
     * ESC → 입력창만 닫히고 설정 창으로 (저장 안 함), ESC → 흐름 끝
     */
    private void editor(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("gamemode creative @a"); // 운영자가 아니어도 크리에이티브면 설정 가능
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        ctx.waitTicks(5);
        Mannequin npc = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GearItems.NPC_TOOL));
            Mannequin m = NpcData.spawnAt(sp.level(), NPC_AT, new Vec3(NPC_AT.x, NPC_AT.y + 1.6, NPC_AT.z - 3));
            NpcData.setName(m, "촌장 올라프");
            NpcData.setGreeting(m, "어서 오게, 모험가여.||요즘 마을 밖이 흉흉하다네.|허수아비가 밭을 망치고 있어.");
            NpcData.setShop(m, 10);
            NpcData.refresh(m, 0);
            return m;
        });
        conn.waitForClientboundEntityUpdates(EntityTypes.MANNEQUIN);
        int id = npc.getId();
        try {
            ctx.waitTicks(5);
            lookAtNpc(ctx, id);
            ctx.waitTick();
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            conn.waitForServerboundPackets();
            ctx.waitTicks(3);
            conn.waitForClientboundPackets();
            if (!waitScreen(ctx, sc -> sc instanceof MenuFormScreen m && m.form().title().contains("NPC 설정"), "npc settings by the tool")) return;
            away(ctx);
            shot(ctx, "f_editor_settings");
            hover(ctx, sc -> ((MenuFormScreen) sc).buttonCenter(3));
            shot(ctx, "f_editor_settings_hover");
            click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(3)); // 퀘스트 추가
            if (!waitScreen(ctx, sc -> sc instanceof ModalFormScreen m && m.values().size() == 23, "quest editor")) return;
            away(ctx);
            shot(ctx, "f_editor_quest");
            int[] steps = {-8, -40};
            String[] names = {"f_editor_quest_middle", "f_editor_quest_bottom"};
            for (int k = 0; k < steps.length; k++) {
                double[] mid = ctx.computeOnClient(mc -> new double[] {mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
                ctx.getInput().setCursorPos(mid[0], mid[1]);
                ctx.getInput().scroll(steps[k]);
                ctx.waitTicks(2);
                away(ctx);
                shot(ctx, names[k]);
            }
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            conn.waitForServerboundPackets();
            ctx.waitTicks(3);
            conn.waitForClientboundPackets();
            check(waitScreen(ctx, sc -> sc instanceof MenuFormScreen m && m.form().title().contains("NPC 설정"), "settings after the quest editor"),
                    "ESC on the quest editor → back to the npc settings window");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            check(waitClosed(ctx), "ESC closes the npc settings window");
            conn.waitForServerboundPackets();
            ctx.waitTicks(3);
            check(!server.computeOnServer(s -> Forms.pending(player(s))), "editor flow ended on the server (Forms.pending false)");
        } finally {
            ctx.setScreen(() -> null);
            server.runOnServer(s -> {
                NpcData.forget(s, NpcData.key(npc));
                npc.discard();
                player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            server.runCommand("gamemode survival @a");
        }
    }

    private void clickInput(ClientGameTestContext ctx, int i, double fx) {
        if (!scrollIntoView(ctx, i)) {
            check(false, "modal input " + i + " not visible");
            return;
        }
        double[] p = at(ctx, sc -> {
            AbstractWidget w = ((ModalFormScreen) sc).input(i);
            return new int[] {w.getX() + (int) (w.getWidth() * fx), w.getY() + w.getHeight() / 2};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    /** 밀대를 from (폭의 비율) 에서 눌러 to 까지 끌어 놓음 */
    private void dragSlider(ClientGameTestContext ctx, int i, double from, double to) {
        if (!scrollIntoView(ctx, i)) {
            check(false, "modal slider " + i + " not visible");
            return;
        }
        double[] a = at(ctx, sc -> {
            AbstractWidget w = ((ModalFormScreen) sc).input(i);
            return new int[] {w.getX() + (int) (w.getWidth() * from), w.getY() + w.getHeight() / 2};
        });
        double[] b = at(ctx, sc -> {
            AbstractWidget w = ((ModalFormScreen) sc).input(i);
            return new int[] {w.getX() + (int) (w.getWidth() * to), w.getY() + w.getHeight() / 2};
        });
        ctx.getInput().setCursorPos(a[0], a[1]);
        ctx.waitTick();
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        for (int k = 1; k <= 6; k++) {
            ctx.getInput().setCursorPos(a[0] + (b[0] - a[0]) * k / 6, a[1]);
            ctx.waitTick();
        }
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
    }

    /** 입력 칸 i 가 스크롤 영역 안에 다 보일 때까지 휠 (창 가운데에서) */
    private boolean scrollIntoView(ClientGameTestContext ctx, int i) {
        for (int k = 0; k < 30; k++) {
            int dir = ctx.computeOnClient(mc -> {
                if (!(mc.gui.screen() instanceof ModalFormScreen m) || m.input(i) == null) return 0;
                AbstractWidget w = m.input(i);
                int[] a = m.area();
                if (w.getY() >= a[0] && w.getY() + w.getHeight() <= a[0] + a[1]) return 0;
                return w.getY() < a[0] ? 1 : -1;
            });
            if (dir == 0) break;
            double[] mid = ctx.computeOnClient(mc -> new double[] {mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
            ctx.getInput().setCursorPos(mid[0], mid[1]);
            ctx.getInput().scroll(dir);
            ctx.waitTick();
        }
        return ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof ModalFormScreen m) || m.input(i) == null) return false;
            AbstractWidget w = m.input(i);
            int[] a = m.area();
            return w.visible && w.getY() >= a[0] && w.getY() + w.getHeight() <= a[0] + a[1];
        });
    }

    private static int[] center(AbstractWidget w) {
        return w == null ? null : new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
    }

    // ------------------------------------------------------------ 도움

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[form-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[form-gallery] part " + name, e);
        }
        try {
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[form-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[form-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 사진 (알림 토스트는 치움) */
    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    /** 마우스를 창 왼쪽 위 구석으로 (마우스를 올리지 않은 모습) */
    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
    }

    private static String screenName(Minecraft mc) {
        Screen sc = mc.gui.screen();
        if (sc == null) return "none";
        if (sc instanceof ChestFormScreen c) return "chest '" + c.form().title() + "'";
        if (sc instanceof DialogFormScreen d) return "dialog '" + d.form().text() + "'";
        return sc.getClass().getSimpleName();
    }

    private boolean waitScreen(ClientGameTestContext ctx, Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 100);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open (now " + ctx.computeOnClient(RpgFormGallery::screenName) + ")");
            return false;
        }
    }

    private boolean waitChest(ClientGameTestContext ctx, Predicate<FormNet.ChestPayload> test, String what) {
        return waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && test.test(c.form()), what);
    }

    private boolean waitDialog(ClientGameTestContext ctx, Predicate<FormNet.DialogPayload> test, String what) {
        return waitScreen(ctx, sc -> sc instanceof DialogFormScreen d && test.test(d.form()), what);
    }

    private static boolean waitClosed(ClientGameTestContext ctx) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() == null, 60);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    private static Function<Screen, int[]> chestSlot(int i) {
        return sc -> ((ChestFormScreen) sc).slotCenter(i);
    }

    private static Function<Screen, int[]> dialogChoice(int i) {
        return sc -> ((DialogFormScreen) sc).choiceCenter(i);
    }

    /** 지금 화면의 GUI 좌표 → 창 픽셀 좌표 (마우스) */
    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen " + screenName(mc));
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    /** 실제 마우스 왼쪽 누르기 → 응답이 서버에 가고 다음 창이 오기까지 */
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
