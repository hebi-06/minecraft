package mmorpg.fabric.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Grade;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 무기 상점 (shop.js, 상자 모양 9 × 3 — 구매에 스탯 · 레벨 제한 없음): 윗줄 가운데 보유 에메랄드, 둘째 줄 첫 판매 등급 (일반) 6종, 셋째 줄 둘째 판매 등급 (희귀) 6종.
 * 칸을 누르면 구매 확인 창. 윗줄 오른쪽 셋째 칸 = 전리품 팔기 (갑옷 상점에도 같은 자리), 오른쪽 끝 = 갑옷 상점.
 * 흐름 (애드온 async 고리) 은 받을 일 (콜백) 로: 다음 창을 열거나 next.accept(다음 창 이름 · null = 끝).
 */
public final class Shop {
    private Shop() {}

    private static final Rules R = Rules.get();
    private static final Obj SHOP = R.obj("SHOP");
    private static final Obj DROPS = R.obj("MONSTER_DROPS");
    private static final Obj MONSTERS = R.obj("MONSTERS");

    static final int BALANCE_SLOT = 4;
    /** 전리품 팔기 창으로 (무기 · 갑옷 상점 같은 자리) */
    public static final int SELL_TAB = 6;
    static final int ARMOR_TAB = 8;
    private static final int[][] ROWS = {{9, 11}, {18, 20}};

    // ------------------------------------------------------------ 공통

    /** 보유 화폐 칸 (상점 · 대장장이 윗줄 가운데) */
    public static Slot balanceSlot(ServerPlayer sp) {
        int bal = Inv.money(sp);
        return Slot.of(Icon.item(Inv.CURRENCY), "§a보유 " + Inv.CURRENCY_NAME + " §f" + bal).count(bal);
    }

    /** 머리 위 자리 (연출) */
    static Vec3 head(ServerPlayer sp) {
        return sp.getEyePosition();
    }

    /** 돈이 모자람 · 재료 부족: 낮은 소리 + 머리 위 화난 얼굴 (대장장이도 씀) */
    public static void angry(ServerPlayer sp) {
        SkillFx.playFor(sp, "note.bass", 0.6, 1);
        Vec3 h = head(sp);
        SkillFx.vanilla(sp.level(), "minecraft:villager_angry", new Vec3(h.x, h.y + 0.5, h.z));
    }

    /** 구매 · 판매 성공: 머리 아래 초록 반짝이 */
    static void happy(ServerPlayer sp) {
        Vec3 h = head(sp);
        SkillFx.vanilla(sp.level(), "minecraft:villager_happy", new Vec3(h.x, h.y - 0.4, h.z));
    }

    /** RPG 무기 아이템 (weapons.js createWeaponItem — 이름 · 설명은 아이템이 그림) */
    public static ItemStack weaponItem(Weapon w) {
        Item it = RpgItems.byId(w.id());
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static List<List<Weapon>> levelWeapons(int level) {
        List<List<Weapon>> out = new ArrayList<>();
        List<Object> grades = SHOP.list("grades");
        for (int r = 0; r < Math.min(ROWS.length, grades.size()); r++) {
            String g = String.valueOf(grades.get(r));
            List<Weapon> row = new ArrayList<>();
            for (String t : R.strings("TYPE_ORDER")) row.add(Weapons.isValidWeapon(level, g) ? Weapons.weaponFrom(t, level, g) : null);
            out.add(row);
        }
        return out;
    }

    // ------------------------------------------------------------ 무기 상점

    private record Built(ChestForm form, Map<Integer, Weapon> bySlot) {}

    private static Built buildShop(ServerPlayer sp, int level, String message) {
        ChestForm form = new ChestForm("무기 상점 §8· §7" + level + "렙");
        form.set(BALANCE_SLOT, balanceSlot(sp));
        Map<Integer, Weapon> bySlot = new HashMap<>();
        List<List<Weapon>> rows = levelWeapons(level);
        List<Object> grades = SHOP.list("grades");
        for (int r = 0; r < rows.size(); r++) {
            Grade grade = R.grade(String.valueOf(grades.get(r)));
            if (grade != null) form.set(ROWS[r][0], Slot.of(Icon.of(grade.icon()), grade.color() + grade.name() + " 등급").deco());
            List<Weapon> row = rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                Weapon w = row.get(i);
                if (w == null) continue;
                int price = (int) Weapons.shopPrice(w);
                List<String> lore = new ArrayList<>(Weapons.weaponInfo(w));
                lore.add("§r§a가격: " + price + " " + Inv.CURRENCY_NAME);
                form.set(ROWS[r][1] + i, Slot.of(weaponItem(w), w.name()).price(price).lore(lore));
                bySlot.put(ROWS[r][1] + i, w);
            }
        }
        form.set(ARMOR_TAB, Slot.of(Items.IRON_CHESTPLATE, "§b갑옷 상점으로").lore("§7힘·민첩·체력·힘+체력·마력 갑옷"));
        form.set(SELL_TAB, sellTabSlot(sp));
        form.fillEmpty();
        List<String> lines = new ArrayList<>(List.of("§8칸을 누르면 구매 창이 열립니다. 칸의 초록 숫자는 가격입니다.§r"));
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return new Built(form, bySlot);
    }

    private static void confirmPurchase(ServerPlayer sp, Weapon w, Consumer<String> done) {
        int price = (int) Weapons.shopPrice(w);
        int bal = Inv.money(sp);
        ChestForm form = new ChestForm("구매 확인");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a구매하기"));
        form.set(13, Slot.of(weaponItem(w), w.name()).price(price).lore(Weapons.weaponInfo(w)));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        double lv = RpgCombat.reqLevelOf(sp);
        // 렙제: 아직 쓸 수 없으면 설명의 "요구 레벨" 줄을 빨간 줄로
        List<String> lines = new ArrayList<>();
        lines.add(w.darkName() + "§r");
        boolean note = !Weapons.levelNote(w, lv).isEmpty();
        for (String l : Weapons.weaponInfo(w, true, 0)) {
            lines.add(note && l.startsWith("§r§8요구 레벨:") ? "§r§4요구 레벨: " + w.level() + " §8(지금 최고 레벨 " + Js.str(lv) + ")" : l);
        }
        lines.add("§r§8가격: §2§l" + price + "§r§8 " + Inv.CURRENCY_NAME + " · 보유 §0" + bal + "§r");
        lines.add("§8왼쪽 초록 칸: 구매 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> done.accept(sel == 11 ? purchase(sp, w) : ""));
    }

    /** 구매 처리 (shop.js purchase). 결과 문구 */
    static String purchase(ServerPlayer sp, Weapon w) {
        int price = (int) Weapons.shopPrice(w);
        int bal = Inv.money(sp);
        if (bal < price) {
            angry(sp);
            Notify.notify(sp, "§c" + Inv.CURRENCY_NAME + " 부족 §7(필요 " + price + ", 보유 " + bal + ")", 40);
            return "§4" + Inv.CURRENCY_NAME + " 부족: 필요 " + price + ", 보유 " + bal + "§r";
        }
        ItemStack item = weaponItem(w);
        if (item.isEmpty()) return "§4아이템을 만들 수 없습니다.§r";
        if (!Inv.fitsAfter(sp, List.of(new Inv.Take(Inv.CURRENCY, price)), List.of(item))) return Inv.bagFull(sp);
        if (!Inv.remove(sp, Inv.CURRENCY, price)) return "§4결제 오류§r";
        Inv.give(sp, item);
        SkillFx.playFor(sp, "random.orb", 1.2, 1);
        SkillFx.playFor(sp, "mob.villager.yes", 1, 0.7);
        happy(sp);
        Notify.notify(sp, "§a구매 완료: " + w.name() + Weapons.levelNote(w, RpgCombat.reqLevelOf(sp)), 40);
        return "§2구매 완료: " + w.darkName() + "§r";
    }

    /**
     * 무기 상점 창 반복 (shopLoop). next: "armor" = 갑옷 상점으로, "sell" = 전리품 팔기로, null = 닫음
     */
    public static void shopLoop(ServerPlayer sp, int level, Consumer<String> next) {
        shopStep(sp, level, "", 0, next);
    }

    private static void shopStep(ServerPlayer sp, int level, String message, int guard, Consumer<String> next) {
        if (guard >= 100) {
            next.accept(null);
            return;
        }
        Built b = buildShop(sp, level, message);
        b.form().show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == ARMOR_TAB) next.accept("armor");
            else if (sel == SELL_TAB) next.accept("sell");
            else {
                Weapon w = b.bySlot().get(sel);
                if (w == null) shopStep(sp, level, "", guard + 1, next);
                else confirmPurchase(sp, w, msg -> shopStep(sp, level, msg, guard + 1, next));
            }
        });
    }

    // ------------------------------------------------------------ 전리품 팔기

    private static final int SELL_BACK = 0;
    private static final int SELL_ALL = 8;
    private static final int SELL_FIRST = 10;
    /** 전리품 칸 (9단계 — 몬스터 14종): 가운데 · 아랫줄의 1 ~ 7 칸 (10 ~ 16 · 19 ~ 25), 풀이 칸은 윗줄 */
    private static final int SELL_COLS = 7;
    private static final int SELL_INFO = 2;

    /** i 번째 전리품의 칸 */
    public static int sellSlot(int i) {
        return SELL_FIRST + (i / SELL_COLS) * 9 + i % SELL_COLS;
    }

    /** 칸 → 전리품 번호 (전리품 칸이 아니면 -1) */
    static int sellIndex(int slot) {
        int rel = slot - SELL_FIRST;
        if (rel < 0) return -1;
        int row = rel / 9;
        int col = rel % 9;
        return col < SELL_COLS ? row * SELL_COLS + col : -1;
    }

    /** 팔 수 있는 전리품 하나 (몬스터 키 · 정의 · 가진 수) */
    public record Loot(String key, Obj d, int n) {
        public Item item() {
            return Icon.itemById(d.str("item"));
        }

        public int price() {
            return (int) d.num("price");
        }
    }

    /** 팔 수 있는 전리품 (몬스터 순서, 없는 것도 — sellList) */
    public static List<Loot> sellList(ServerPlayer sp) {
        List<Loot> out = new ArrayList<>();
        for (String k : R.strings("MONSTER_ORDER")) {
            Obj d = DROPS.objOrNull(k);
            if (d == null) continue;
            out.add(new Loot(k, d, Inv.count(sp, Icon.itemById(d.str("item")))));
        }
        return out;
    }

    /** 가진 전리품을 모두 팔면 받는 에메랄드 */
    public static int sellTotal(ServerPlayer sp) {
        int t = 0;
        for (Loot e : sellList(sp)) t += e.n() * e.price();
        return t;
    }

    /** 무기 · 갑옷 상점 윗줄의 "전리품 팔기" 칸 */
    public static Slot sellTabSlot(ServerPlayer sp) {
        int total = sellTotal(sp);
        return Slot.of(Items.BUNDLE, "§e전리품 팔기").lore("§7몬스터 전리품을 에메랄드로",
                total > 0 ? "§a지금 모두 팔면 " + total + " " + Inv.CURRENCY_NAME : "§8가진 전리품 없음");
    }

    private record SellBuilt(ChestForm form, List<Loot> list) {}

    private static SellBuilt sellForm(ServerPlayer sp, String message) {
        List<Loot> list = sellList(sp);
        int total = 0;
        for (Loot e : list) total += e.n() * e.price();
        ChestForm form = new ChestForm("전리품 팔기 §8· §7상점");
        form.set(SELL_BACK, Slot.of(Items.IRON_SWORD, "§b무기 상점으로"));
        form.set(BALANCE_SLOT, balanceSlot(sp));
        form.set(SELL_ALL, total > 0
                ? Slot.of(Icon.sprite("icon_check"), "§a모두 팔기 §7(+" + total + " " + Inv.CURRENCY_NAME + ")").price(total).lore("§7가진 전리품을 한 번에 팝니다", "§7(확인 창이 열림)")
                : Slot.of(Icon.sprite("icon_check"), "§8모두 팔기 §7(가진 전리품 없음)").deco());
        for (int i = 0; i < list.size(); i++) {
            Loot e = list.get(i);
            Obj m = MONSTERS.obj(e.key());
            if (sellSlot(i) >= ChestForm.SIZE) break;
            form.set(sellSlot(i), Slot.of(Icon.item(e.item()), e.d().str("color") + e.d().str("name") + " §7· 1개 " + e.price() + " " + Inv.CURRENCY_NAME).count(e.n())
                    .lore(e.n() > 0 ? "§r§a보유 " + e.n() + "개 → " + e.n() * e.price() + " " + Inv.CURRENCY_NAME : "§r§8가진 것이 없음",
                            "§r§7" + m.str("name") + " (Lv." + Js.str(m.num("level")) + ") 전리품"));
        }
        form.set(SELL_INFO, Slot.of(Items.BUNDLE, "§f전리품").lore("§7몬스터를 잡으면 떨어짐 (50%)", "§7잉그리드의 반복 의뢰에 쓰면", "§7에메랄드·경험치를 더 받음").deco());
        form.fillEmpty();
        List<String> lines = new ArrayList<>(List.of("§8가운데 줄: 전리품 · 칸을 누르면 팔 개수를 고릅니다.§r", "§8반복 의뢰 재료로 쓰면 더 많이 받습니다 (여관 주인 잉그리드).§r"));
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return new SellBuilt(form, list);
    }

    /**
     * 판매 처리 (sellLoot): 전리품 n 개를 가져가고 에메랄드를 줌. 같은 전리품이 여러 번 오면 합쳐서 가진 만큼만. 결과 문구
     */
    public static String sellLoot(ServerPlayer sp, Map<String, Integer> what) {
        Map<String, Integer> want = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> w : what.entrySet()) if (DROPS.objOrNull(w.getKey()) != null) want.merge(w.getKey(), Math.max(0, w.getValue()), Integer::sum);
        List<Loot> plan = new ArrayList<>();
        for (Map.Entry<String, Integer> e : want.entrySet()) {
            Obj d = DROPS.obj(e.getKey());
            int n = Math.min(e.getValue(), Inv.count(sp, Icon.itemById(d.str("item"))));
            if (n > 0) plan.add(new Loot(e.getKey(), d, n));
        }
        int total = 0;
        for (Loot x : plan) total += x.n() * x.price();
        if (total <= 0) {
            SkillFx.playFor(sp, "note.bass", 0.6, 1);
            return "§4팔 전리품이 없습니다§r";
        }
        List<Inv.Take> takes = new ArrayList<>();
        for (Loot x : plan) takes.add(new Inv.Take(x.item(), x.n()));
        if (!Inv.fitsAfter(sp, takes, Inv.currency(total))) return Inv.bagFull(sp);
        List<String> lines = new ArrayList<>();
        int got = 0;
        for (Loot x : plan) {
            if (!Inv.remove(sp, x.item(), x.n())) continue;
            got += x.n() * x.price();
            lines.add(x.d().str("name") + " " + x.n());
        }
        if (got <= 0) {
            SkillFx.playFor(sp, "note.bass", 0.6, 1);
            return "§4팔 전리품이 없습니다§r";
        }
        for (ItemStack st : Inv.currency(got)) Inv.give(sp, st);
        SkillFx.playFor(sp, "random.orb", 1.3, 1);
        SkillFx.playFor(sp, "mob.villager.yes", 1, 0.7);
        happy(sp);
        DamageNumbers.showFace(sp, "§a§l+" + got + " " + Inv.CURRENCY_NAME, 2.0, 0.5, -0.3);
        Notify.notify(sp, "§a판매: " + String.join(", ", lines) + " §7→ §a" + Inv.CURRENCY_NAME + " +" + got, 50);
        return "§2판매: §0" + String.join(", ", lines) + "§2 → " + Inv.CURRENCY_NAME + " +" + got + "§r";
    }

    /** 한 가지 팔기: 1개 · 10개 · 모두 (sellOne). 결과 문구를 done 으로 */
    private static void sellOne(ServerPlayer sp, Loot e, Consumer<String> done) {
        Obj d = e.d();
        int n = Inv.count(sp, e.item());
        if (n <= 0) {
            SkillFx.playFor(sp, "note.bass", 0.7, 1);
            done.accept("§4" + d.str("name") + ": 가진 것이 없습니다§r");
            return;
        }
        int price = e.price();
        ChestForm form = new ChestForm("팔기 §8· §7" + d.str("name"));
        Icon icon = Icon.item(e.item());
        form.set(4, Slot.of(icon, d.str("color") + d.str("name")).count(n).lore("§r§7보유 " + n + "개 · 1개 " + price + " " + Inv.CURRENCY_NAME).deco());
        form.set(11, Slot.of(icon, "§a1개 팔기 §7(+" + price + ")").price(price));
        if (n >= 10) form.set(12, Slot.of(icon, "§a10개 팔기 §7(+" + price * 10 + ")").price(price * 10));
        form.set(13, Slot.of(Icon.sprite("icon_check"), "§a모두 팔기 §7(" + n + "개, +" + price * n + ")").price(price * n));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        form.body("§0" + d.str("name") + "§8 보유 §0" + n + "개§8 · 1개 §0" + price + " " + Inv.CURRENCY_NAME + "§r\n§8초록 숫자 칸: 그만큼 팔기 · 빨간 칸: 취소§r");
        form.show(sp, sel -> {
            int count = sel == 11 ? 1 : sel == 12 && n >= 10 ? 10 : sel == 13 ? n : 0;
            done.accept(count > 0 ? sellLoot(sp, Map.of(e.key(), count)) : "");
        });
    }

    /** 모두 팔기 확인 (sellEverything) */
    private static void sellEverything(ServerPlayer sp, Consumer<String> done) {
        List<Loot> list = new ArrayList<>();
        int total = 0;
        for (Loot e : sellList(sp)) {
            if (e.n() <= 0) continue;
            list.add(e);
            total += e.n() * e.price();
        }
        if (total <= 0) {
            done.accept("§4팔 전리품이 없습니다§r");
            return;
        }
        ChestForm form = new ChestForm("모두 팔기");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a모두 팔기 §7(+" + total + " " + Inv.CURRENCY_NAME + ")").price(total));
        form.set(13, Slot.of(Icon.item(Inv.CURRENCY), "§a" + Inv.CURRENCY_NAME + " +" + total).count(total).deco());
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> parts = new ArrayList<>();
        for (Loot e : list) parts.add(e.d().str("name") + " " + e.n());
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 3) rows.add("§0" + String.join(" · ", parts.subList(i, Math.min(parts.size(), i + 3))) + "§r");
        rows.add("§8합계 §2" + total + " " + Inv.CURRENCY_NAME + "§8 · 왼쪽 초록 칸: 팔기 · 빨간 칸: 취소§r");
        form.body(String.join("\n", rows));
        form.show(sp, sel -> {
            if (sel != 11) {
                done.accept("");
                return;
            }
            Map<String, Integer> what = new LinkedHashMap<>();
            for (Loot e : list) what.put(e.key(), e.n());
            done.accept(sellLoot(sp, what));
        });
    }

    /** 전리품 팔기 창 반복 (sellLoop). next: "weapons" = 무기 상점으로, null = 닫음 */
    public static void sellLoop(ServerPlayer sp, Consumer<String> next) {
        sellStep(sp, "", 0, next);
    }

    private static void sellStep(ServerPlayer sp, String message, int guard, Consumer<String> next) {
        if (guard >= 100) {
            next.accept(null);
            return;
        }
        SellBuilt b = sellForm(sp, message);
        b.form().show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == SELL_BACK) next.accept("weapons");
            else if (sel == SELL_ALL) sellEverything(sp, msg -> sellStep(sp, msg, guard + 1, next));
            else {
                int i = sellIndex(sel);
                Loot e = i >= 0 && i < b.list().size() ? b.list().get(i) : null;
                if (e == null) sellStep(sp, "", guard + 1, next);
                else sellOne(sp, e, msg -> sellStep(sp, msg, guard + 1, next));
            }
        });
    }
}
