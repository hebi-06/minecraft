package mmorpg.fabric.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mmorpg.core.Armor;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 갑옷 상점 (armor.js 상점): 상점 레벨 이하에서 가장 높은 갑옷 레벨의 일반 세트 — 종류 10개 (모양 같고 스탯이 다름): 가운데 줄 애드온의 5개
 * (힘 · 민첩 · 체력 · 힘+체력 · 마력), 아랫줄 두 스탯 무기용 5개 (0.12.5 — 마력+힘 · 힘+민첩 · 민첩+마력 · 체력+힘 · 마력+체력). 칸 설명에 어울리는 무기.
 * 종류 칸 → 부위 창 (부위마다 구매 · 세트 구매 10% 할인). 초월 갑옷은 왕실 퀘스트 보상.
 */
public final class ArmorShop {
    private ArmorShop() {}

    private static final Rules R = Rules.get();
    private static final Obj TYPES = R.obj("ARMOR_TYPES");
    private static final Obj TIERS = R.obj("ARMOR_TIERS");
    private static final double SET_DISCOUNT = R.obj("ARMOR_SHOP").num("setDiscount");

    private static final int WEAPON_TAB = 0;
    private static final int BALANCE_SLOT = 4;
    private static final int INFO_SLOT = 8;
    /** 종류 칸: 한 줄 5칸씩 (가운데 줄 11 ~ 15, 아랫줄 20 ~ 24) */
    private static final int SET_FIRST = 11;
    private static final int PER_ROW = 5;

    /** ARMOR_TYPE_ORDER 의 i 번째 종류의 칸 */
    public static int typeSlot(int i) {
        return SET_FIRST + (i / PER_ROW) * 9 + i % PER_ROW;
    }

    /** 칸 → ARMOR_TYPE_ORDER 번호 (종류 칸이 아니면 -1) */
    static int typeAt(int slot, int count) {
        int d = slot - SET_FIRST;
        if (d < 0 || d % 9 >= PER_ROW) return -1;
        int i = (d / 9) * PER_ROW + d % 9;
        return i < count ? i : -1;
    }

    /** 어울리는 무기 이름 ("창", "단검 · 활") — 갑옷과 요구 스탯 비율이 같은 무기 (Armor.weaponsFor) */
    static String weaponNames(String armorType) {
        List<String> names = new ArrayList<>();
        for (String w : Armor.weaponsFor(armorType)) names.add(R.obj("TYPES").obj(w).str("name"));
        return String.join(" · ", names);
    }

    /** 상점 레벨 → 파는 갑옷 레벨 (그 레벨 이하에서 가장 높은 것 — 15렙 상점 → 10렙 갑옷) */
    public static int armorLevelFor(int level) {
        List<Double> levels = R.numbers("ARMOR_LEVELS");
        double best = levels.get(0);
        for (double l : levels) if (l <= level) best = l;
        return (int) best;
    }

    /** 갑옷 아이템 (armor.js createArmorItem — 이름 · 설명은 아이템이 그림) */
    public static ItemStack armorItem(Armor.Piece a) {
        Item it = GearItems.byId(a.id());
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    /** 요구 조건 중 모자란 것 (armor.js armorLack — 최고 레벨 · 찍은 스탯) */
    public static List<String> lack(ServerPlayer sp, Armor.Piece a) {
        return Armor.armorLack(a, RpgCombat.reqLevelOf(sp), RpgData.of(sp).base());
    }

    private static ChestForm buildShop(ServerPlayer sp, int lv, String message) {
        Obj tier = TIERS.obj("common_" + lv);
        ChestForm form = new ChestForm("갑옷 상점 §8· §7" + lv + "렙 " + tier.str("name"));
        form.set(WEAPON_TAB, Slot.of(Items.IRON_SWORD, "§b무기 상점으로"));
        form.set(BALANCE_SLOT, Shop.balanceSlot(sp));
        form.set(INFO_SLOT, Slot.of(Items.IRON_CHESTPLATE, "§f" + tier.str("name") + " 세트 §7(" + lv + "렙)")
                .lore("§7같은 모양, 종류마다 스탯이 다름", "§7요구 조건이 모자라면 벗겨짐", "§7초월 갑옷은 왕실 퀘스트 보상").deco());
        form.set(Shop.SELL_TAB, Shop.sellTabSlot(sp));
        List<String> order = R.strings("ARMOR_TYPE_ORDER");
        for (int i = 0; i < order.size(); i++) {
            String type = order.get(i);
            Armor.Piece chest = Armor.armorFrom(type, lv, "common", "chest");
            int price = (int) Armor.setPrice(type, lv);
            Obj t = TYPES.obj(type);
            List<String> lore = new ArrayList<>();
            lore.add("§r§7세트 방어 §f" + Js.str(chest.setProt()) + "§7 · " + Armor.bonusText(chest.setBonus(), false));
            lore.add("§r§7요구: " + Armor.armorReqText(chest));
            String weapons = weaponNames(type);
            if (!weapons.isEmpty()) lore.add("§r§7어울리는 무기: §f" + weapons);
            lore.add("§r§a세트 " + price + " " + Inv.CURRENCY_NAME + " §7(낱개도 팜)");
            form.set(typeSlot(i), Slot.of(armorItem(chest), t.str("color") + t.str("name") + " 갑옷 §f" + tier.str("name") + " 세트").price(price).lore(lore));
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§8종류별 " + tier.str("name") + " 세트 · 칸을 누르면 부위별로 살 수 있습니다.§r");
        lines.add("§8아랫줄은 두 스탯 무기용 (마검 · 창 · 낫 · 전투 망치 · 성스러운 홀).§r");
        lines.add("§8모양은 같고 스탯이 다릅니다 · 어울리는 무기는 칸 설명에.§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 한 종류의 부위 창 (typeWindow): 10 ~ 13 부위 (누르면 구매), 15 세트 구매, 22 돌아가기 */
    private static void typeWindow(ServerPlayer sp, String type, int lv, String message, int guard, Runnable back) {
        if (guard >= 50) {
            back.run();
            return;
        }
        Obj t = TYPES.obj(type);
        List<Armor.Piece> pieces = new ArrayList<>();
        for (String p : Armor.PIECE_ORDER) pieces.add(Armor.armorFrom(type, lv, "common", p));
        int setPrice = (int) Armor.setPrice(type, lv);
        ChestForm form = new ChestForm(t.str("name") + " 갑옷 §8· §7" + TIERS.obj("common_" + lv).str("name") + " (" + lv + "렙)");
        for (int i = 0; i < pieces.size(); i++) {
            Armor.Piece a = pieces.get(i);
            int price = (int) Armor.armorPrice(a);
            List<String> lore = new ArrayList<>(Armor.armorInfo(a, false));
            lore.add("§r§a가격: " + price + " " + Inv.CURRENCY_NAME);
            form.set(10 + i, Slot.of(armorItem(a), a.name()).price(price).lore(lore));
        }
        form.set(15, Slot.of(Icon.sprite("icon_check"), "§a세트 구매 §7(4부위, " + Math.round((1 - SET_DISCOUNT) * 100) + "% 할인)").price(setPrice)
                .lore("§r§a" + setPrice + " " + Inv.CURRENCY_NAME));
        form.set(4, Shop.balanceSlot(sp));
        form.set(22, Slot.of(Icon.sprite("icon_cross"), "§7돌아가기"));
        form.fillEmpty();
        List<String> lack = lack(sp, pieces.get(0));
        List<String> lines = new ArrayList<>();
        lines.add("§8부위 칸: 그 부위 구매 · 초록 칸: 세트 구매 (§0" + setPrice + "§8)§r");
        lines.add("§8요구: " + Armor.armorReqText(pieces.get(0)) + (lack.isEmpty() ? "" : " §4(지금 " + String.join(" · ", lack) + " 부족)") + "§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel < 0) {
                back.run();
                return;
            }
            if (sel == 22) {
                back.run();
                return;
            }
            String msg = "";
            if (sel >= 10 && sel <= 13) msg = buy(sp, List.of(pieces.get(sel - 10)), (int) Armor.armorPrice(pieces.get(sel - 10)));
            else if (sel == 15) msg = buy(sp, pieces, setPrice);
            typeWindow(sp, type, lv, msg, guard + 1, back);
        });
    }

    /** 구매 처리 (armor.js buy). 결과 문구 */
    public static String buy(ServerPlayer sp, List<Armor.Piece> list, int price) {
        int bal = Inv.money(sp);
        if (bal < price) {
            Shop.angry(sp);
            Notify.notify(sp, "§c" + Inv.CURRENCY_NAME + " 부족 §7(필요 " + price + ", 보유 " + bal + ")", 40);
            return "§4" + Inv.CURRENCY_NAME + " 부족: 필요 " + price + ", 보유 " + bal + "§r";
        }
        List<ItemStack> items = new ArrayList<>();
        for (Armor.Piece a : list) {
            ItemStack it = armorItem(a);
            if (it.isEmpty()) return "§4아이템을 만들 수 없습니다.§r";
            items.add(it);
        }
        if (!Inv.fitsAfter(sp, List.of(new Inv.Take(Inv.CURRENCY, price)), items)) return Inv.bagFull(sp);
        if (!Inv.remove(sp, Inv.CURRENCY, price)) return "§4결제 오류§r";
        for (ItemStack it : items) Inv.give(sp, it);
        SkillFx.playFor(sp, "armor.equip_iron", 1.1, 1);
        SkillFx.playFor(sp, "random.orb", 1.2, 0.6);
        Shop.happy(sp);
        boolean one = list.size() == 1;
        Armor.Piece a = list.get(0);
        Notify.notify(sp, "§a구매 완료: " + (one ? a.name() : a.setName() + " 세트 · " + a.typeName()), 40);
        return "§2구매 완료: " + (one ? a.darkName() : "§0" + a.setName() + " 세트§8 · " + a.typeName()) + "§r";
    }

    /** 갑옷 상점 반복 (armorShopLoop). next: "weapons" · "sell" · null */
    public static void armorShopLoop(ServerPlayer sp, int level, Consumer<String> next) {
        step(sp, level, "", 0, next);
    }

    private static void step(ServerPlayer sp, int level, String message, int guard, Consumer<String> next) {
        if (guard >= 100) {
            next.accept(null);
            return;
        }
        int lv = armorLevelFor(level);
        buildShop(sp, lv, message).show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == WEAPON_TAB) next.accept("weapons");
            else if (sel == Shop.SELL_TAB) next.accept("sell");
            else {
                List<String> order = R.strings("ARMOR_TYPE_ORDER");
                int i = typeAt(sel, order.size());
                if (i >= 0) typeWindow(sp, order.get(i), lv, "", 0, () -> step(sp, level, "", guard + 1, next));
                else step(sp, level, "", guard + 1, next);
            }
        });
    }
}
