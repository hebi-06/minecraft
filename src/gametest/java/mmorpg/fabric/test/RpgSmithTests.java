package mmorpg.fabric.test;

import java.util.List;
import mmorpg.core.Enhance;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.smith.EnhanceWindow;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.smith.VoidArmorCraft;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 대장장이 게임 시험 (서버, 클라이언트 없이 창 흐름 — Forms.HEADLESS · Forms.answer): 무기 강화 (성공 · 실패 — +1~+5 그대로, +6 부터 한 단계 하락,
 * 수호 주문서는 실패했을 때만 사라지고 단계 유지 · 재료는 판정 때 소모), 재료가 모자라면 쓰지 않음, 망치질 도중 무기를 옮기면 취소 (재료 그대로),
 * 영웅 무기 레벨 올리기 (30 → 35 · 비용 · 강화 단계 · 모루 이름 유지), 보스 무기 제작 (재료 · 실버 · 별철 광석 소모, 모자라면 아무것도 쓰지 않음),
 * 0.36.0 초월 무기 레벨 올리기 (40 → 45 — 골드 + 공허 재료) · 공허의 갑옷 제작 (제작 창 넷째 쪽 — 부위 · 네 부위 한 번에).
 * 강화 성공 여부는 EnhanceWindow.rand 로 정함 — 난수를 바꾸는 시험은 하나 (enhanceOutcomes) 에 모아 차례로 (함께 도는 시험끼리 섞이지 않게).
 */
public final class RpgSmithTests {
    private static final int ANIM = EnhanceWindow.ANIM_TICKS;
    /** 강화 창: 강화석 칸 (누르면 새로 그림) · 첫 무기 칸 */
    private static final int STONE_SLOT = 2;
    private static final int FIRST = 9;

    private static Item stone() {
        return GearItems.byId("rpg:enhance_stone");
    }

    private static Item bright() {
        return GearItems.byId("rpg:bright_stone");
    }

    private static Item scroll() {
        return GearItems.byId("rpg:guard_scroll");
    }

    private static Item ore() {
        return GearItems.byId("rpg:star_ore");
    }

    private static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    private static String body(ServerPlayer sp) {
        FormNet.ChestPayload c = chest(sp);
        return c == null ? "(창 없음)" : c.body();
    }

    private static void finish(GameTestHelper h, ServerPlayer sp) {
        Forms.answer(sp, -1, null);
        h.assertFalse(Forms.pending(sp), "closed");
        Forms.HEADLESS.remove(sp.getUUID());
        RpgGearTests.done(h, sp);
    }

    /**
     * 강화 한 번 (창으로): 칸 0 의 무기를 start 강으로 적고 창을 새로 그림 (강화석 칸) → 무기 칸 → 확인 창 → pick (11 강화 · 12 주문서 쓰고) →
     * 망치질 (창 없음 · 묶임 · 재료 그대로) → 끝난 뒤 then
     */
    private static void attempt(GameTestHelper h, ServerPlayer sp, int start, double roll, int pick, Runnable then) {
        WeaponEnhance.write(sp.getInventory().getItem(0), start);
        h.assertTrue(Forms.answer(sp, STONE_SLOT, null), "refresh +" + start);
        FormNet.ChestPayload c = chest(sp);
        h.assertTrue(c != null && c.title().contains("무기 강화") && c.slots().get(FIRST).name().contains("+" + start) == (start > 0), "list +" + start);
        h.assertTrue(Forms.answer(sp, FIRST, null), "pick weapon +" + start);
        c = chest(sp);
        h.assertTrue(c != null && c.title().contains("+" + start + " → +" + (start + 1)), "confirm window " + (c == null ? null : c.title()));
        Enhance.Cost cost = Enhance.enhanceCost(start, "common");
        h.assertTrue(c.slots().get(12).kind() == (cost.down() ? Slot.COUNT : Slot.DECO), "scroll choice only when a failure drops (+" + start + ")");
        int stones = RpgGearTests.countIn(sp, stone());
        EnhanceWindow.rand = () -> roll;
        h.assertTrue(Forms.answer(sp, pick, null), "enhance +" + start);
        h.assertTrue(Forms.last(sp) == null && !Forms.pending(sp) && Forms.isOpen(sp), "hammering: no window, busy (+" + start + ")");
        h.assertTrue(RpgGearTests.countIn(sp, stone()) == stones, "materials are taken only at the end (+" + start + ")");
        h.runAfterDelay(ANIM + 3, () -> {
            h.assertTrue(Forms.pending(sp) && chest(sp) != null && chest(sp).title().contains("무기 강화"), "window back after hammering (+" + start + ")");
            then.run();
        });
    }

    private static void expect(GameTestHelper h, ServerPlayer sp, int level, int stones, int brights, int scrolls, String text) {
        ItemStack w = sp.getInventory().getItem(0);
        h.assertTrue(WeaponEnhance.of(w) == level, "level " + WeaponEnhance.of(w) + " vs " + level + " — " + body(sp));
        h.assertTrue(RpgGearTests.countIn(sp, stone()) == stones, "stones " + RpgGearTests.countIn(sp, stone()) + " vs " + stones);
        h.assertTrue(RpgGearTests.countIn(sp, bright()) == brights, "bright " + RpgGearTests.countIn(sp, bright()) + " vs " + brights);
        h.assertTrue(RpgGearTests.countIn(sp, scroll()) == scrolls, "scrolls " + RpgGearTests.countIn(sp, scroll()) + " vs " + scrolls);
        h.assertTrue(body(sp).contains(text), "message '" + text + "' in " + body(sp));
    }

    /** 강화 결과 (일반 10렙 칼 — 등급 배수 1): 성공 · 실패 그대로 (+4) · 실패 하락 (+6, +7) · 주문서 (실패면 사라지고 단계 유지, 성공이면 그대로) · 재료 소모 */
    @GameTest(maxTicks = 300)
    public void enhanceOutcomes(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        Weapon w = Weapons.weaponFrom("sword", 10, "common");
        sp.getInventory().setItem(0, new ItemStack(RpgItems.byId(w.id())));
        sp.getInventory().setItem(20, new ItemStack(stone(), 64));
        sp.getInventory().setItem(21, new ItemStack(bright(), 10));
        sp.getInventory().setItem(22, new ItemStack(scroll(), 2));
        Smith.smithLoop(sp, "enhance", null);
        FormNet.ChestPayload c = chest(sp);
        h.assertTrue(c != null && c.title().contains("무기 강화"), "enhance window");
        h.assertTrue(c.slots().get(STONE_SLOT).num() == 64 && c.slots().get(FIRST).icon().item().is(RpgItems.byId(w.id())), "stones · weapon shown");
        // +0 → +1 성공 (강화석 1)
        attempt(h, sp, 0, 0.0, 11, () -> {
            expect(h, sp, 1, 63, 10, 2, "성공");
            // +3 → +4 실패: 그대로 (강화석 2)
            attempt(h, sp, 3, 0.99, 11, () -> {
                expect(h, sp, 3, 61, 10, 2, "그대로");
                // +5 → +6 실패: 한 단계 하락 (강화석 3 · 빛나는 강화석 1, 주문서 안 씀)
                attempt(h, sp, 5, 0.99, 11, () -> {
                    expect(h, sp, 4, 58, 9, 2, "떨어졌습니다");
                    // +6 → +7 실패 + 주문서: 단계 유지, 주문서 사라짐 (강화석 4 · 빛나는 1)
                    attempt(h, sp, 6, 0.99, 12, () -> {
                        expect(h, sp, 6, 54, 8, 1, "지켜");
                        // +7 → +8 성공 + 주문서: 주문서는 그대로 (강화석 4 · 빛나는 2), 서버 알림 (7강 이상)
                        attempt(h, sp, 7, 0.0, 12, () -> {
                            expect(h, sp, 8, 50, 6, 1, "주문서는 쓰지 않았습니다");
                            h.assertTrue(sp.getInventory().getItem(0).getHoverName().getString().contains("+8"), "name +8");
                            EnhanceWindow.rand = Math::random;
                            finish(h, sp);
                        });
                    });
                });
            });
        });
    }

    /** 명령어 /smith · /enhance 등록 (Smith.init) */
    @GameTest(maxTicks = 20)
    public void smithCommandsRegistered(GameTestHelper h) {
        var root = h.getLevel().getServer().getCommands().getDispatcher().getRoot();
        h.assertTrue(root.getChild("smith") != null && root.getChild("enhance") != null, "commands /smith · /enhance");
        h.succeed();
    }

    /** 재료가 모자라면 망치질 없이 바로 창으로 (아무것도 쓰지 않음) · 창이 열려 있으면 명령어 창은 열리지 않음 */
    @GameTest(maxTicks = 40)
    public void enhanceNeedsMaterials(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        Weapon w = Weapons.weaponFrom("dagger", 10, "rare");
        ItemStack stack = new ItemStack(RpgItems.byId(w.id()));
        WeaponEnhance.write(stack, 5); // +5 → +6: 강화석 3 · 빛나는 강화석 1
        sp.getInventory().setItem(3, stack);
        sp.getInventory().setItem(20, new ItemStack(stone(), 64));
        Smith.smithLoop(sp, "enhance", null);
        Smith.openSmith(sp, "smith"); // 이미 창이 열려 있음 → 무시
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("무기 강화"), "command ignored while a window is open");
        h.assertTrue(Forms.answer(sp, FIRST, null), "pick weapon");
        h.assertTrue(Forms.answer(sp, 11, null), "enhance");
        h.assertTrue(Forms.pending(sp) && body(sp).contains("재료 부족") && body(sp).contains("빛나는 강화석 1"), "lack message: " + body(sp));
        h.assertTrue(RpgGearTests.countIn(sp, stone()) == 64 && WeaponEnhance.of(sp.getInventory().getItem(3)) == 5, "nothing used");
        finish(h, sp);
    }

    /** 망치질 도중 무기를 옮기면 취소 (재료 그대로 · 단계 그대로) */
    @GameTest(maxTicks = 80)
    public void enhanceCancelsWhenWeaponMoved(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        Weapon w = Weapons.weaponFrom("axe", 20, "noble");
        ItemStack stack = new ItemStack(RpgItems.byId(w.id()));
        WeaponEnhance.write(stack, 2);
        sp.getInventory().setItem(0, stack);
        sp.getInventory().setItem(20, new ItemStack(stone(), 64));
        Smith.smithLoop(sp, "enhance", null);
        Forms.answer(sp, FIRST, null);
        h.assertTrue(Forms.answer(sp, 11, null), "enhance");
        h.assertTrue(Forms.last(sp) == null && Forms.isOpen(sp), "hammering");
        h.runAfterDelay(5, () -> {
            ItemStack wpn = sp.getInventory().getItem(0);
            sp.getInventory().setItem(0, ItemStack.EMPTY);
            sp.getInventory().setItem(4, wpn);
            h.runAfterDelay(ANIM, () -> {
                h.assertTrue(body(sp).contains("옮겨서 취소"), "cancel message: " + body(sp));
                h.assertTrue(RpgGearTests.countIn(sp, stone()) == 64, "no materials used");
                h.assertTrue(WeaponEnhance.of(sp.getInventory().getItem(4)) == 2, "level kept");
                finish(h, sp);
            });
        });
    }

    /** 영웅 무기 레벨 올리기 30 → 35: 비용이 모자라면 그대로, 있으면 실버 64 (가방의 은화 + 지갑) · 별철 광석 4, 강화 단계 · 모루 이름은 새 무기로 */
    @GameTest(maxTicks = 40)
    public void raiseHeroWeapon(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        Weapon w30 = Weapons.weaponFrom("sword", 30, "hero");
        Weapons.SmithStep step = Weapons.nextLevelOf(w30);
        h.assertTrue(step != null && step.to().level() == 35 && step.emerald() == 64 && step.ore() == 4, "smith step");
        ItemStack stack = new ItemStack(RpgItems.byId(w30.id()));
        WeaponEnhance.write(stack, 4);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("내 검"));
        sp.getInventory().setItem(2, stack);
        sp.getInventory().setItem(20, new ItemStack(mmorpg.fabric.economy.EconomyItems.SILVER_COIN, 50));
        sp.getInventory().setItem(21, new ItemStack(ore(), 10));
        Smith.smithLoop(sp, "smith", null);
        FormNet.ChestPayload c = chest(sp);
        h.assertTrue(c != null && c.title().contains("레벨 올리기") && c.slots().get(FIRST).icon().item().is(RpgItems.byId(w30.id())), "raise window");
        // 실버 50 < 64: 그대로
        Forms.answer(sp, FIRST, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("레벨 제한 올리기"), "confirm window");
        Forms.answer(sp, 11, null);
        h.assertTrue(body(sp).contains("재료 부족") && body(sp).contains("실버 14"), "lack: " + body(sp));
        h.assertTrue(Inv.money(sp) == 50 && RpgGearTests.countIn(sp, ore()) == 10 && sp.getInventory().getItem(2).is(RpgItems.byId(w30.id())), "nothing changed");
        // 실버를 더 주고 다시 (0.21.0: 지갑에 50 — 지갑 먼저, 모자란 14 는 가방의 은화)
        mmorpg.fabric.economy.Wallet.set(sp, mmorpg.core.Economy.Kind.SILVER, 50);
        Forms.answer(sp, FIRST, null);
        Forms.answer(sp, 11, null);
        ItemStack now = sp.getInventory().getItem(2);
        h.assertTrue(now.is(RpgItems.byId(step.to().id())), "raised to 35: " + now);
        h.assertTrue(WeaponEnhance.of(now) == 4, "enhance kept " + WeaponEnhance.of(now));
        h.assertTrue(now.get(DataComponents.CUSTOM_NAME) != null && now.get(DataComponents.CUSTOM_NAME).getString().equals("내 검"), "anvil name kept");
        h.assertTrue(Inv.money(sp) == 100 - 64 && RpgGearTests.countIn(sp, ore()) == 6, "paid: " + Inv.money(sp) + " / " + RpgGearTests.countIn(sp, ore()));
        h.assertTrue(body(sp).contains("완료"), "done message: " + body(sp));
        finish(h, sp);
    }

    /**
     * 0.36.0 초월 무기 레벨 올리기 40 → 45: 골드 15 + 공허 독주머니 40 · 균열 칼날 20 — 하나라도 모자라면 그대로, 채우면 내고 45렙 무기 (강화 단계 그대로).
     * 60렙 초월 무기는 목록에 없음
     */
    @GameTest(maxTicks = 40)
    public void raiseTranscendWeapon(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        Weapon w40 = Weapons.weaponFrom("sword", 40, "transcend");
        Weapons.SmithStep step = Weapons.nextLevelOf(w40);
        h.assertTrue(step != null && step.to().level() == 45 && step.gold() == 15 && step.items().size() == 2, "smith step " + (step == null ? null : step.items()));
        Item leaper = mmorpg.fabric.ui.Icon.itemById("rpg:loot_void_leaper");
        Item stalker = mmorpg.fabric.ui.Icon.itemById("rpg:loot_void_stalker");
        h.assertTrue(leaper != null && stalker != null, "void loot items");
        ItemStack stack = new ItemStack(RpgItems.byId(w40.id()));
        WeaponEnhance.write(stack, 3);
        sp.getInventory().setItem(2, stack);
        sp.getInventory().setItem(5, new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 60, "transcend").id())));
        sp.getInventory().setItem(20, new ItemStack(leaper, 40));
        sp.getInventory().setItem(21, new ItemStack(stalker, 19));
        mmorpg.fabric.economy.Wallet.set(sp, mmorpg.core.Economy.Kind.GOLD, 20);
        List<Smith.Raise> list = Smith.raisable(sp);
        h.assertTrue(list.size() == 1 && list.get(0).slot() == 2 && list.get(0).goldStep() && list.get(0).gold() == 15, "only the 40렙 sword is raisable: " + list.size());
        Smith.smithLoop(sp, "smith", null);
        FormNet.ChestPayload c = chest(sp);
        h.assertTrue(c != null && c.title().contains("레벨 올리기") && c.slots().get(FIRST).icon().item().is(RpgItems.byId(w40.id())), "raise window");
        // 균열 칼날 19 < 20: 그대로
        Forms.answer(sp, FIRST, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("레벨 제한 올리기"), "confirm window");
        Forms.answer(sp, 11, null);
        h.assertTrue(body(sp).contains("재료 부족") && body(sp).contains("균열 칼날 1"), "lack: " + body(sp));
        h.assertTrue(RpgGearTests.countIn(sp, leaper) == 40 && RpgGearTests.countIn(sp, stalker) == 19
                && mmorpg.fabric.economy.Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD) == 20 && sp.getInventory().getItem(2).is(RpgItems.byId(w40.id())), "nothing changed");
        // 하나를 더 주고 다시
        sp.getInventory().setItem(22, new ItemStack(stalker, 1));
        Forms.answer(sp, FIRST, null);
        Forms.answer(sp, 11, null);
        ItemStack now = sp.getInventory().getItem(2);
        h.assertTrue(now.is(RpgItems.byId(step.to().id())), "raised to 45: " + now);
        h.assertTrue(WeaponEnhance.of(now) == 3, "enhance kept " + WeaponEnhance.of(now));
        h.assertTrue(RpgGearTests.countIn(sp, leaper) == 0 && RpgGearTests.countIn(sp, stalker) == 0
                && mmorpg.fabric.economy.Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD) == 5, "paid gold 15 + materials");
        h.assertTrue(body(sp).contains("완료"), "done message: " + body(sp));
        Weapon w45 = RpgCombat.weaponOf(now);
        h.assertTrue(w45 != null && w45.level() == 45 && w45.damage() > w40.damage() && w45.critChance() == w40.critChance(), "45렙: damage up, crit same");
        finish(h, sp);
    }

    /**
     * 0.36.0 공허의 갑옷 제작 (대장장이 제작 창 넷째 쪽): 공허 전리품을 가지면 그 쪽부터 열림 (힘 칸 = 공허 갑옷 그림) → 힘 종류 창 → 투구 확인 창 →
     * 골드가 1 모자라면 아무것도 쓰지 않음 → 채우면 골드 10 · 독주머니 15 · 칼날 15 · 핵 10 을 내고 투구 → 네 부위 한 번에 (한 벌 비용) → 4부위 →
     * 돌아가기 → 윗줄 가운데 칸: 영웅 쪽
     */
    @GameTest(maxTicks = 40)
    public void craftVoidArmor(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        h.assertTrue(VoidArmorCraft.enabled() && Smith.CRAFT_PAGES.size() == 5 && Smith.voidPage(3) && Smith.artifactPage(4), "void armor page " + Smith.CRAFT_PAGES);
        VoidArmorCraft.Cost helm = VoidArmorCraft.pieceCost("helmet");
        VoidArmorCraft.Cost set = VoidArmorCraft.setCost();
        h.assertTrue(helm.gold() == 10 && set.gold() == 48 && set.items().size() == 3, "costs " + helm + " / " + set);
        Item leaper = mmorpg.fabric.ui.Icon.itemById("rpg:loot_void_leaper");
        Item stalker = mmorpg.fabric.ui.Icon.itemById("rpg:loot_void_stalker");
        Item jelly = mmorpg.fabric.ui.Icon.itemById("rpg:loot_void_jelly");
        int[] need = {set.items().get("rpg:loot_void_leaper"), set.items().get("rpg:loot_void_stalker"), set.items().get("rpg:loot_void_jelly")};
        h.assertTrue(need[0] == 70 && need[1] == 70 && need[2] == 55 && helm.items().get("rpg:loot_void_jelly") == 10, "set items " + set.items());
        // 투구 + 한 벌 몫
        Item[] mats = {leaper, stalker, jelly};
        int slot = 18;
        for (int k = 0; k < 3; k++) {
            int n = need[k] + helm.items().get(BuiltInRegistries.ITEM.getKey(mats[k]).toString());
            for (; n > 0; n -= 64) sp.getInventory().setItem(slot++, new ItemStack(mats[k], Math.min(64, n)));
        }
        mmorpg.fabric.economy.Wallet.set(sp, mmorpg.core.Economy.Kind.GOLD, helm.gold() - 1);
        Item helmet = GearItems.byId("rpg:armor_str_l50_transcend_helmet");
        h.assertTrue(helmet != null, "void helmet item");
        Smith.smithLoop(sp, "craft", null);
        FormNet.ChestPayload c = chest(sp);
        h.assertTrue(c != null && c.title().contains("공허"), "opens on the void armor page: " + (c == null ? null : c.title()));
        int strSlot = VoidArmorCraft.typeSlot("str");
        h.assertTrue(c.slots().get(strSlot).icon().item().is(GearItems.byId("rpg:armor_str_l50_transcend_chest")), "str slot = void chest");
        Forms.answer(sp, strSlot, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("힘 갑옷"), "type window: " + (chest(sp) == null ? null : chest(sp).title()));
        h.assertTrue(chest(sp).slots().get(10).icon().item().is(helmet), "helmet at 10");
        // 골드 9 < 10: 그대로
        Forms.answer(sp, 10, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("공허의 갑옷 제작"), "confirm window");
        Forms.answer(sp, 11, null);
        h.assertTrue(body(sp).contains("재료 부족") && body(sp).contains("골드 1"), "lack: " + body(sp));
        h.assertTrue(RpgGearTests.countIn(sp, helmet) == 0 && RpgGearTests.countIn(sp, leaper) == 85 && RpgGearTests.countIn(sp, jelly) == 65
                && mmorpg.fabric.economy.Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD) == helm.gold() - 1, "nothing used");
        // 골드를 채우고 (투구 + 한 벌) 다시
        mmorpg.fabric.economy.Wallet.set(sp, mmorpg.core.Economy.Kind.GOLD, helm.gold() + set.gold());
        Forms.answer(sp, 10, null);
        Forms.answer(sp, 11, null);
        h.assertTrue(body(sp).contains("완료"), "helmet done: " + body(sp));
        h.assertTrue(RpgGearTests.countIn(sp, helmet) == 1 && RpgGearTests.countIn(sp, leaper) == 70 && RpgGearTests.countIn(sp, stalker) == 70
                && RpgGearTests.countIn(sp, jelly) == 55 && mmorpg.fabric.economy.Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD) == set.gold(), "paid helmet cost");
        // 네 부위 한 번에
        Forms.answer(sp, VoidArmorCraft.SET_SLOT, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("공허의 갑옷 제작") && chest(sp).body().contains("네 부위"), "set confirm: " + body(sp));
        Forms.answer(sp, 11, null);
        h.assertTrue(body(sp).contains("완료"), "set done: " + body(sp));
        for (String p : mmorpg.core.Armor.PIECE_ORDER) {
            int n = RpgGearTests.countIn(sp, GearItems.byId("rpg:armor_str_l50_transcend_" + p));
            h.assertTrue(n == (p.equals("helmet") ? 2 : 1), p + " " + n);
        }
        for (Item m : mats) h.assertTrue(RpgGearTests.countIn(sp, m) == 0, "used " + m);
        h.assertTrue(mmorpg.fabric.economy.Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD) == 0, "gold 0");
        // 돌아가기 → 공허의 갑옷 쪽 → 윗줄 가운데 칸: 아티팩트 쪽 (0.37.0) → 영웅 쪽
        Forms.answer(sp, VoidArmorCraft.BACK_SLOT, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("공허"), "back to the page");
        Forms.answer(sp, 4, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("아티팩트"), "next page: " + (chest(sp) == null ? null : chest(sp).title()));
        Forms.answer(sp, 4, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("영웅"), "then: " + (chest(sp) == null ? null : chest(sp).title()));
        finish(h, sp);
    }

    /** 보스 무기 제작 (영웅 쪽 첫 무기): 별철 광석이 하나 모자라면 아무것도 쓰지 않음, 채우면 재료 · 실버 · 별철 광석을 내고 무기 */
    @GameTest(maxTicks = 40)
    public void craftBossWeapon(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        List<Smith.Recipe> hero = Smith.craftPage(Smith.craftRecipes(), 0);
        h.assertTrue(!hero.isEmpty() && Smith.craftPage(Smith.craftRecipes(), 1).size() > 0, "recipes on both pages");
        Smith.Recipe r = hero.get(0);
        for (Smith.Mat m : r.mats()) sp.getInventory().add(new ItemStack(m.item(), m.count()));
        mmorpg.fabric.economy.Wallet.set(sp, mmorpg.core.Economy.Kind.SILVER, r.emerald() + 5);
        sp.getInventory().setItem(31, new ItemStack(ore(), r.ore() - 1));
        Item weapon = RpgItems.byId(r.w().id());
        Smith.smithLoop(sp, "craft", null);
        FormNet.ChestPayload c = chest(sp);
        h.assertTrue(c != null && c.title().contains("보스 무기 제작") && c.title().contains("영웅"), "craft window (hero page first): " + (c == null ? null : c.title()));
        h.assertTrue(c.slots().get(9).icon().item().is(weapon), "first hero weapon at slot 9");
        Forms.answer(sp, 9, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("보스 무기 제작"), "confirm window");
        Forms.answer(sp, 11, null);
        h.assertTrue(body(sp).contains("재료 부족") && body(sp).contains("별철 광석 1"), "lack: " + body(sp));
        for (Smith.Mat m : r.mats()) h.assertTrue(RpgGearTests.countIn(sp, m.item()) == m.count(), "kept " + m.name());
        h.assertTrue(Inv.money(sp) == r.emerald() + 5 && RpgGearTests.countIn(sp, weapon) == 0, "nothing used");
        // 별철 광석을 채우고 다시
        sp.getInventory().setItem(32, new ItemStack(ore(), 1));
        Forms.answer(sp, 9, null);
        Forms.answer(sp, 11, null);
        h.assertTrue(body(sp).contains("완료"), "done: " + body(sp));
        for (Smith.Mat m : r.mats()) h.assertTrue(RpgGearTests.countIn(sp, m.item()) == 0, "used " + m.name());
        h.assertTrue(Inv.money(sp) == 5 && RpgGearTests.countIn(sp, ore()) == 0, "paid: " + Inv.money(sp));
        h.assertTrue(RpgGearTests.countIn(sp, weapon) == 1, "got " + r.w().id());
        // 윗줄 가운데 칸: 초월 쪽
        Forms.answer(sp, 4, null);
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("초월"), "other page: " + (chest(sp) == null ? null : chest(sp).title()));
        finish(h, sp);
    }
}
