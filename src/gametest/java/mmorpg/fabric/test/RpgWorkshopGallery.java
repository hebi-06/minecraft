package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mmorpg.core.Economy;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.client.ClientSmith;
import mmorpg.fabric.client.SmithScreen;
import mmorpg.fabric.client.motion.ClientMotions;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.rune.Breakthrough;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.rune.RuneSmithUi;
import mmorpg.fabric.shop.ShopUi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.45.0 룬술사 · 상인 전용 창 사진첩 (화면 시험, only=workshop — 실제 키 · 마우스 입력). 사진 이름 rw_* (룬술사) · sw_* (상인) → ci-logs 의 shots/.
 * <ul>
 *   <li>룬술사 NPC (바로 동쪽에 마법 부여대) 우클릭 → 새 창 (룬 공방 패널) · 하나뿐인 룬 갈기 (두 번 누름 — 경고) → 동작 (기운 세 번 · 깨짐) · 결과,
 *       돌파 탭 (두 룬이 부딪혀 합침) · 뽑기권 탭 (5장 고르기 · 조각이 모여 뽑기권), 창을 닫고 세상에서 본 룬 다루기 (떠오르는 룬 · 마법진)</li>
 *   <li>상인 NPC 우클릭 → 새 창 (가게 패널 — 계산대 뒤 상인) · 쪽 (새 무기) · 무기 사기 (계산대에 올림 · 건넴) · 갑옷 (부위 · 한 벌) · 전리품 팔기
 *       (10개 · 은화를 셈) · 모두 팔기, 세상에서 본 건넴 (물건이 사는 사람 쪽으로)</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgWorkshopGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-workshop-gallery");
    private static final Vec3 RUNE_AT = new Vec3(0.5, -60, 3.5);
    private static final BlockPos TABLE = new BlockPos(1, -60, 3);
    private static final Vec3 SHOP_AT = new Vec3(8.5, -60, 3.5);
    private final List<String> failures = new ArrayList<>();
    private int runeId = -1;
    private int shopId = -1;
    /** 큰 단추를 누르기 전의 일 (닿은 수는 새 일의 것만 셈) */
    private ClientSmith.Work before;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("workshop")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 13000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runCommand("xp set @a 30 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            int[] ids = server.computeOnServer(s -> {
                ServerPlayer sp = player(s);
                var level = sp.level();
                // 룬 공방: 룬술사 (남쪽 3칸 — 사람을 봄) · 바로 동쪽에 마법 부여대 · 책장
                level.setBlockAndUpdate(TABLE, Blocks.ENCHANTING_TABLE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-1, -60, 5), Blocks.BOOKSHELF.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-1, -59, 5), Blocks.BOOKSHELF.defaultBlockState());
                Mannequin r = NpcData.spawnAt(level, RUNE_AT, new Vec3(0.5, -58.4, 0.5));
                NpcData.setName(r, "룬술사 미르");
                NpcData.setRunesmith(r, true);
                NpcData.refresh(r, 0);
                // 가게: 상인 (동쪽 8칸 · 남쪽 3칸) · 뒤에 통 · 등불
                level.setBlockAndUpdate(new BlockPos(9, -60, 5), Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(7, -60, 5), Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(7, -59, 5), Blocks.LANTERN.defaultBlockState());
                Mannequin m = NpcData.spawnAt(level, SHOP_AT, new Vec3(8.5, -58.4, 0.5));
                NpcData.setName(m, "상인 한스");
                NpcData.setShop(m, 10);
                NpcData.refresh(m, 0);
                stock(sp);
                return new int[] {r.getId(), m.getId()};
            });
            runeId = ids[0];
            shopId = ids[1];
            conn.waitForClientboundEntityUpdates(EntityTypes.MANNEQUIN);
            ctx.getInput().pressKey(InputConstants.KEY_6);
            ctx.waitTicks(20);
            part(ctx, "rune", () -> rune(ctx, server, conn));
            part(ctx, "runeWorld", () -> runeWorld(ctx, server, conn));
            part(ctx, "shop", () -> shop(ctx, server, conn));
            part(ctx, "shopWorld", () -> shopWorld(ctx, server, conn));
            part(ctx, "compact", () -> compact(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[workshop-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("workshop gallery: " + String.join(" / ", failures));
    }

    /**
     * 가방: 20 · 21 = 같은 룬 (민첩 고귀) · 22 = 하나뿐인 룬 (지능 희귀) · 23 · 24 = 초월 메테오 (2돌 · 0돌 — 돌파) · 25 · 26 = 체력 일반 둘 · 룬 조각 120 ·
     * 전리품 (트롤 14 · 닉스 6 · 허수아비 30) · 실버 5000
     */
    private static void stock(ServerPlayer sp) {
        sp.getInventory().clearContent();
        sp.getInventory().setItem(20, RuneItems.stack("dex", "noble"));
        sp.getInventory().setItem(21, RuneItems.stack("dex", "noble"));
        sp.getInventory().setItem(22, RuneItems.stack("int", "rare"));
        sp.getInventory().setItem(23, Breakthrough.stack("int", "transcend", 2));
        sp.getInventory().setItem(24, Breakthrough.stack("int", "transcend", 0));
        sp.getInventory().setItem(25, RuneItems.stack("vit", "common"));
        sp.getInventory().setItem(26, RuneItems.stack("vit", "common"));
        sp.getInventory().setItem(9, RuneSmith.createShardItem(64));
        sp.getInventory().setItem(10, RuneSmith.createShardItem(56));
        sp.getInventory().setItem(11, new ItemStack(GearItems.byId("rpg:loot_troll"), 14));
        sp.getInventory().setItem(12, new ItemStack(GearItems.byId("rpg:loot_nix"), 6));
        sp.getInventory().setItem(13, new ItemStack(GearItems.byId("rpg:loot_scarecrow"), 30));
        Wallet.set(sp, Economy.Kind.SILVER, 5000);
        Wallet.set(sp, Economy.Kind.GOLD, 12);
        sp.getInventory().setSelectedSlot(5);
    }

    // ------------------------------------------------------------ 1. 룬술사 창

    private void rune(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        lookAt(ctx, runeId);
        ctx.waitTicks(10);
        shot(ctx, "rw_npc");
        rightClick(ctx, conn, runeId);
        if (!waitScreen(ctx, "rune smith window from the npc")) return;
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof SmithScreen s && s.sceneKind().equals("runesmith") && ClientSmith.lastView().npc() == runeId
                && ClientSmith.lastView().tab().equals(RuneSmithUi.GRIND)), "rune scene · grind tab · npc id");
        away(ctx);
        ctx.waitTicks(10);
        shot(ctx, "rw_open");
        hover(ctx, s -> s.rowCenter("dup"));
        shot(ctx, "rw_dup_tip");
        // 하나뿐인 룬 갈기: 처음 누르면 경고 (단추 "한 번 더") → 다시 누르면 일
        String only = "r:22:rpg:rune_int_rare:0";
        row(ctx, conn, only);
        int shards = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RuneSmith.shardItem()));
        click(ctx, conn, s -> s.actionCenter());
        away(ctx);
        check(ctx.computeOnClient(mc -> ClientSmith.work() == null), "first press only warns (unique rune)");
        shot(ctx, "rw_confirm");
        act(ctx, conn);
        away(ctx);
        check(ctx.computeOnClient(mc -> ClientSmith.work() != null && ClientSmith.work().fx().equals("grind") && ClientMotions.current(runeId) != null),
                "grind started (panel · world npc motion)");
        hitShot(ctx, 1, "rw_grind_hit1");
        hitShot(ctx, 2, "rw_grind_hit2");
        hitShot(ctx, 3, "rw_grind_hit3");
        waitResult(ctx, conn);
        shot(ctx, "rw_grind_result");
        int after = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RuneSmith.shardItem()));
        boolean gone = server.computeOnServer(s -> player(s).getInventory().getItem(22).isEmpty());
        check(gone && after > shards, "unique rune ground: shards " + shards + " → " + after);
        // 돌파 탭: 메테오 2돌 + 0돌 → 3돌
        click(ctx, conn, s -> s.tabCenter(1));
        check(tab(ctx).equals(RuneSmithUi.BREAK), "break tab (" + tab(ctx) + ")");
        away(ctx);
        shot(ctx, "rw_tab_break");
        String meteor = ctx.computeOnClient(mc -> ClientSmith.lastView().entries().stream().filter(e -> e.key().startsWith("b:") && e.key().contains("int_transcend"))
                .map(e -> e.key()).findFirst().orElse(""));
        row(ctx, conn, meteor);
        away(ctx);
        shot(ctx, "rw_break_detail");
        act(ctx, conn);
        away(ctx);
        hitShot(ctx, 0, "rw_merge_start");
        ctx.waitTicks(6);
        shot(ctx, "rw_merge_close");
        hitShot(ctx, 1, "rw_merge_hit1");
        hitShot(ctx, 2, "rw_merge_hit2");
        waitResult(ctx, conn);
        shot(ctx, "rw_merge_result");
        int level = server.computeOnServer(s -> {
            for (int i = 0; i < 36; i++) {
                ItemStack it = player(s).getInventory().getItem(i);
                if (RuneItems.runeOf(it) != null && RuneItems.runeOf(it).id().equals("rpg:rune_int_transcend")) return Breakthrough.of(it);
            }
            return -1;
        });
        check(level == 3, "meteor 2 + 0 → 3 (" + level + ")");
        // 뽑기권 탭: 룬 뽑기권 5장
        click(ctx, conn, s -> s.tabCenter(2));
        check(tab(ctx).equals(RuneSmithUi.TICKETS), "tickets tab (" + tab(ctx) + ")");
        row(ctx, conn, "t:n");
        click(ctx, conn, s -> s.variantCenter(1));
        away(ctx);
        shot(ctx, "rw_tab_tickets");
        int tickets = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RuneItems.TICKET));
        act(ctx, conn);
        away(ctx);
        hitShot(ctx, 2, "rw_ticket_hit2");
        hitShot(ctx, 3, "rw_ticket_hit3");
        waitResult(ctx, conn);
        shot(ctx, "rw_ticket_result");
        int got = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RuneItems.TICKET));
        check(got == tickets + 5, "5 tickets bought (" + tickets + " → " + got + ")");
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
    }

    // ------------------------------------------------------------ 2. 세상에서 본 룬술사 (마법 부여대 위의 룬 · 마법진)

    private void runeWorld(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("title @a clear");
        server.runCommand("tp @a 2.6 -60 1.0 40 12");
        ctx.waitTicks(5);
        ensureOpen(ctx, conn, runeId);
        click(ctx, conn, s -> s.tabCenter(0));
        row(ctx, conn, "r:21:rpg:rune_dex_noble:0");
        act(ctx, conn);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(2);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "ESC closes while working");
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(1.0, -59.0, 3.2)));
        worldHit(ctx, 1, "rw_world_hit1");
        worldHit(ctx, 2, "rw_world_hit2");
        worldHit(ctx, 3, "rw_world_hit3");
        ctx.waitTicks(RuneSmithUi.GRIND_WORK.ticks() - RuneSmithUi.GRIND_WORK.impacts().get(2) + 4);
        ctx.takeScreenshot(TestScreenshotOptions.of("rw_world_title").withSize(960, 540));
        boolean open = server.computeOnServer(s -> mmorpg.fabric.smith.Workshop.isOpen(player(s)) || mmorpg.fabric.smith.Workshop.busy(player(s)));
        check(!open, "session ended after the result");
        // 돌파도 세상에서 (두 룬이 다가와 합침)
        server.runCommand("title @a clear");
        ensureOpen(ctx, conn, runeId);
        click(ctx, conn, s -> s.tabCenter(1));
        String vit = ctx.computeOnClient(mc -> ClientSmith.lastView().entries().stream().filter(e -> e.key().startsWith("b:") && e.key().contains("vit_common"))
                .map(e -> e.key()).findFirst().orElse(""));
        row(ctx, conn, vit);
        act(ctx, conn);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(1.0, -59.0, 3.2)));
        ctx.waitTicks(8);
        ctx.takeScreenshot(TestScreenshotOptions.of("rw_world_merge_close").withSize(960, 540));
        worldHit(ctx, 2, "rw_world_merge_hit2");
        ctx.waitTicks(RuneSmithUi.MERGE_WORK.ticks());
        server.runCommand("title @a clear");
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 3. 상인 창

    private void shop(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("tp @a 8.5 -60 0.6 0 0");
        ctx.waitTicks(5);
        lookAt(ctx, shopId);
        ctx.waitTicks(5);
        rightClick(ctx, conn, shopId);
        if (!waitScreen(ctx, "shop window from the npc")) return;
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof SmithScreen s && s.sceneKind().equals("shop") && ClientSmith.lastView().tab().equals(ShopUi.WEAPONS)
                && ClientSmith.lastView().npc() == shopId), "shop scene · weapons tab · npc id");
        away(ctx);
        ctx.waitTicks(10);
        shot(ctx, "sw_open");
        if (!Weapons.moreTypes().isEmpty()) {
            click(ctx, conn, s -> s.pageCenter(true));
            away(ctx);
            shot(ctx, "sw_page2");
            click(ctx, conn, s -> s.pageCenter(false));
        }
        String sword = "w:" + Weapons.weaponFrom("sword", 10, "rare").id();
        row(ctx, conn, sword);
        away(ctx);
        shot(ctx, "sw_weapon_detail");
        long silver = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.SILVER));
        act(ctx, conn);
        away(ctx);
        ctx.waitTicks(5);
        shot(ctx, "sw_buy_reach");
        hitShot(ctx, 1, "sw_buy_hit1");
        hitShot(ctx, 2, "sw_buy_hit2");
        ctx.waitTicks(2);
        shot(ctx, "sw_buy_hand");
        waitResult(ctx, conn);
        shot(ctx, "sw_buy_result");
        long silver2 = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.SILVER));
        int swords = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RpgItems.byId(Weapons.weaponFrom("sword", 10, "rare").id())));
        check(swords == 1 && silver2 < silver, "sword bought: silver " + silver + " → " + silver2);
        // 갑옷: 힘 갑옷 한 벌 고르기 · 투구 사기
        click(ctx, conn, s -> s.tabCenter(1));
        check(tab(ctx).equals(ShopUi.ARMOR), "armor tab (" + tab(ctx) + ")");
        row(ctx, conn, "a:str");
        click(ctx, conn, s -> s.variantCenter(4));
        away(ctx);
        shot(ctx, "sw_armor_set");
        click(ctx, conn, s -> s.variantCenter(0));
        act(ctx, conn);
        away(ctx);
        hitShot(ctx, 1, "sw_armor_hit1");
        waitResult(ctx, conn);
        shot(ctx, "sw_armor_result");
        // 전리품 팔기: 트롤 10개 → 모두 팔기
        click(ctx, conn, s -> s.tabCenter(2));
        check(tab(ctx).equals(ShopUi.SELL), "sell tab (" + tab(ctx) + ")");
        row(ctx, conn, "l:troll");
        click(ctx, conn, s -> s.variantCenter(1));
        away(ctx);
        shot(ctx, "sw_tab_sell");
        act(ctx, conn);
        away(ctx);
        hitShot(ctx, 1, "sw_sell_hit1");
        hitShot(ctx, 3, "sw_sell_hit3");
        waitResult(ctx, conn);
        shot(ctx, "sw_sell_result");
        int trolls = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId("rpg:loot_troll")));
        check(trolls == 4, "10 trolls sold (" + trolls + " left)");
        row(ctx, conn, "all");
        act(ctx, conn);
        away(ctx);
        waitResult(ctx, conn);
        shot(ctx, "sw_sell_all");
        int loot = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId("rpg:loot_troll")) + RpgGearTests.countIn(player(s),
                GearItems.byId("rpg:loot_nix")) + RpgGearTests.countIn(player(s), GearItems.byId("rpg:loot_scarecrow")));
        check(loot == 0, "all loot sold (" + loot + " left)");
        ctx.setScreen(() -> null);
        ctx.waitTicks(4);
    }

    // ------------------------------------------------------------ 4. 세상에서 본 상인 (물건이 사는 사람 쪽으로)

    private void shopWorld(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("title @a clear");
        server.runCommand("tp @a 7.3 -60 1.2 -25 10");
        ctx.waitTicks(5);
        ensureOpen(ctx, conn, shopId);
        click(ctx, conn, s -> s.tabCenter(0));
        String axe = "w:" + Weapons.weaponFrom("axe", 10, "common").id();
        row(ctx, conn, axe);
        act(ctx, conn);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, SHOP_AT.add(0, 1.2, -0.6)));
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of("sw_world_reach").withSize(960, 540));
        worldHit(ctx, 1, "sw_world_hit1");
        worldHit(ctx, 2, "sw_world_hit2");
        ctx.waitTicks(3);
        ctx.takeScreenshot(TestScreenshotOptions.of("sw_world_hand").withSize(960, 540));
        ctx.waitTicks(ShopUi.BUY_WORK.ticks());
        server.runCommand("title @a clear");
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 5. 좁은 화면

    private void compact(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().resizeWindow(1024, 768);
        ctx.waitTicks(5);
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        ctx.waitTicks(3);
        ensureOpen(ctx, conn, runeId);
        away(ctx);
        ctx.waitTicks(5);
        shot(ctx, "rw_compact");
        click(ctx, conn, s -> s.tabCenter(2));
        check(tab(ctx).equals(RuneSmithUi.TICKETS), "compact: tab click maps the mouse (" + tab(ctx) + ")");
        ctx.setScreen(() -> null);
        ctx.getInput().resizeWindow(1280, 720);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private void lookAt(ClientGameTestContext ctx, int id) {
        ctx.runOnClient(mc -> {
            Entity e = mc.level.getEntity(id);
            if (e != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, e.getEyePosition().add(0, -0.3, 0));
        });
    }

    private void rightClick(ClientGameTestContext ctx, TestServerConnection conn, int id) {
        lookAt(ctx, id);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    /** 창이 열려 있지 않으면 그 NPC 를 다시 우클릭 */
    private void ensureOpen(ClientGameTestContext ctx, TestServerConnection conn, int id) {
        if (ctx.computeOnClient(mc -> mc.gui.screen() instanceof SmithScreen)) return;
        rightClick(ctx, conn, id);
        waitScreen(ctx, "window (again)");
    }

    private String tab(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ClientSmith.lastView() == null ? "" : ClientSmith.lastView().tab());
    }

    private boolean waitScreen(ClientGameTestContext ctx, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof SmithScreen, 80);
            ctx.waitTicks(3);
            return true;
        } catch (AssertionError | RuntimeException e) {
            String sc = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
            check(false, what + " did not open (screen " + sc + ")");
            return false;
        }
    }

    /** 결과가 올 때까지 (일이 끝나고 서버가 보낸 결과 — 받은 뒤 3틱) */
    private void waitResult(ClientGameTestContext ctx, TestServerConnection conn) {
        ClientSmith.Work w = ctx.computeOnClient(mc -> ClientSmith.work());
        int left = w == null ? 4 : (int) Math.max(4, w.ticks() + 4);
        try {
            ctx.waitFor(mc -> ClientSmith.lastResult() != null && ClientSmith.work() != null && ClientSmith.progress(0) >= 1, left + 40);
        } catch (AssertionError | RuntimeException e) {
            check(false, "result not seen");
        }
        conn.waitForClientboundPackets();
        ctx.waitTicks(3);
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[workshop-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[workshop-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[workshop-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[workshop-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    /** n 번째로 닿은 바로 뒤 (창의 패널 — n = 0 이면 시작 바로 뒤) */
    private void hitShot(ClientGameTestContext ctx, int n, String name) {
        waitHits(ctx, n);
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.takeScreenshot(name);
    }

    /** n 번째로 닿은 바로 뒤 (세상 — 960 × 540) */
    private void worldHit(ClientGameTestContext ctx, int n, String name) {
        waitHits(ctx, n);
        ctx.takeScreenshot(TestScreenshotOptions.of(name).withSize(960, 540));
    }

    /** 큰 단추 (누르기 전 일을 기억 — 닿은 수는 새 일부터) */
    private void act(ClientGameTestContext ctx, TestServerConnection conn) {
        before = ctx.computeOnClient(mc -> ClientSmith.work());
        click(ctx, conn, s -> s.actionCenter());
    }

    private void waitHits(ClientGameTestContext ctx, int n) {
        ClientSmith.Work old = before;
        try {
            ctx.waitFor(mc -> ClientSmith.work() != null && ClientSmith.work() != old && ClientSmith.hits() >= n, 80);
        } catch (AssertionError | RuntimeException e) {
            check(false, "hit " + n + " not seen (" + ctx.computeOnClient(mc -> ClientSmith.hits()) + ")");
        }
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

    /** 창의 GUI 좌표 → 창 픽셀 좌표 (없으면 null) */
    private static double[] at(ClientGameTestContext ctx, Function<SmithScreen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            if (!(sc instanceof SmithScreen s)) return null;
            int[] p = where.apply(s);
            if (p == null) return null;
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private void hover(ClientGameTestContext ctx, Function<SmithScreen, int[]> where) {
        double[] p = at(ctx, where);
        if (p == null) {
            check(false, "nothing to hover");
            return;
        }
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    /** 목록의 칸을 마우스로 고름 (안 보이는 칸이면 목록을 그 칸까지 내린 뒤) */
    private void row(ClientGameTestContext ctx, TestServerConnection conn, String key) {
        if (at(ctx, s -> s.rowCenter(key)) == null) {
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof SmithScreen sc) sc.select(key);
            });
            ctx.waitTicks(2);
        }
        click(ctx, conn, s -> s.rowCenter(key));
    }

    private void click(ClientGameTestContext ctx, TestServerConnection conn, Function<SmithScreen, int[]> where) {
        double[] p = at(ctx, where);
        if (p == null) {
            check(false, "nothing to click");
            return;
        }
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }
}
