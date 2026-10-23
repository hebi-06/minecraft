package mmorpg.fabric.smith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Armor;
import mmorpg.core.Economy;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobUpgrades;
import mmorpg.fabric.shop.ArmorShop;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 0.36.0 공허의 갑옷 제작 (rules_more.json VOID_ARMOR — 사용자: "공허의 갑옷을 추가", 50렙 한 벌, 골드 + 공허 재료, 디자인 견본 ② 공허 갑각):
 * 대장장이 제작 창의 넷째 쪽 (영웅 → 초월 → 고귀 → 공허의 갑옷 — {@link Smith#CRAFT_PAGES}).
 * <ul>
 *   <li>쪽: 갑옷 종류 10 (갑옷 상점과 같은 칸 — 가운데 줄 힘 · 민첩 · 체력 · 힘+체력 · 마력, 아랫줄 두 스탯 무기용 5) · 양옆 칸에 가진 골드 · 재료.</li>
 *   <li>종류 창: 부위 넷 (누르면 확인 창) · 네 부위 한 번에 · 돌아가기. 확인 창에서 골드 + 공허 몬스터 전리품을 내고 갑옷 (초월 50렙 — 요구 50렙 · 스탯 합 135).</li>
 * </ul>
 * 비용은 부위마다 (VOID_ARMOR.cost — 한 벌 골드 48 · 공허 독주머니 70 · 균열 칼날 70 · 공허 핵 55). 다 있는지 확인한 뒤 내고, 가방이 차면 아무것도 쓰지 않음.
 */
public final class VoidArmorCraft {
    private VoidArmorCraft() {}

    private static final Rules R = Rules.get();
    /** 공허의 갑옷 설정 (순수 규칙 — rules.json 만 — 이면 null) */
    private static final Obj VOID = R.root().objOrNull("VOID_ARMOR");
    private static final Obj TYPES = R.obj("ARMOR_TYPES");

    /** 쪽: 양옆 칸 (왼쪽 9 · 18, 오른쪽 17 · 26) = 골드 · 재료 셋 (가진 수) */
    static final int[] HAVE_SLOTS = {9, 18, 17, 26};
    /** 종류 창: 윗줄 골드 · 재료 셋 (가진 수) */
    static final int[] TYPE_HAVE_SLOTS = {1, 3, 5, 7};
    /** 종류 창: 부위 칸 (10 ~ 13) · 네 부위 한 번에 · 돌아가기 */
    public static final int PIECE_FIRST = 10;
    public static final int SET_SLOT = 15;
    public static final int BACK_SLOT = 22;

    /** 공허의 갑옷을 만들 수 있는지 (규칙에 VOID_ARMOR 가 있음) */
    public static boolean enabled() {
        return VOID != null;
    }

    public static int level() {
        return (int) VOID.num("level");
    }

    public static String grade() {
        return VOID.str("grade");
    }

    /** 비용 (골드 + 아이템 ID → 수, 적힌 순서) */
    public record Cost(int gold, Map<String, Integer> items) {
        public Cost plus(Cost o) {
            Map<String, Integer> m = new LinkedHashMap<>(items);
            for (Map.Entry<String, Integer> e : o.items.entrySet()) m.merge(e.getKey(), e.getValue(), Integer::sum);
            return new Cost(gold + o.gold, Collections.unmodifiableMap(m));
        }
    }

    private static final Cost NONE = new Cost(0, Map.of());

    /** 한 부위 (helmet · chest · legs · boots) 의 비용 */
    public static Cost pieceCost(String piece) {
        Obj c = VOID.obj("cost").obj(piece);
        Map<String, Integer> items = new LinkedHashMap<>();
        Obj it = c.objOrNull("items");
        if (it != null) for (String k : it.keys()) if (!k.startsWith("_")) items.put(k, (int) it.num(k));
        return new Cost((int) c.numOr("gold", 0), Collections.unmodifiableMap(items));
    }

    /** 여러 부위의 비용 (합침) */
    public static Cost cost(List<Armor.Piece> pieces) {
        Cost c = NONE;
        for (Armor.Piece a : pieces) c = c.plus(pieceCost(a.piece()));
        return c;
    }

    /** 한 벌 (네 부위) 의 비용 */
    public static Cost setCost() {
        Cost c = NONE;
        for (String p : Armor.PIECE_ORDER) c = c.plus(pieceCost(p));
        return c;
    }

    /** 한 부위 (공허 — 초월 50렙) */
    public static Armor.Piece piece(String type, String piece) {
        return Armor.armorFrom(type, level(), grade(), piece);
    }

    /** 한 종류의 네 부위 (투구 · 갑옷 · 바지 · 신발) */
    public static List<Armor.Piece> set(String type) {
        List<Armor.Piece> out = new ArrayList<>();
        for (String p : Armor.PIECE_ORDER) out.add(piece(type, p));
        return out;
    }

    /** 모자란 것 ["골드 5", "공허 핵 12"] (없으면 빈 목록) */
    public static List<String> lack(ServerPlayer sp, Cost c) {
        List<String> out = new ArrayList<>();
        long gold = Wallet.total(sp, Economy.Kind.GOLD);
        if (gold < c.gold()) out.add("골드 " + (c.gold() - gold));
        for (Map.Entry<String, Integer> x : c.items().entrySet()) {
            JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
            int have = m.item() == null ? 0 : Inv.count(sp, m.item());
            if (have < x.getValue()) out.add(m.name() + " " + (x.getValue() - have));
        }
        return out;
    }

    /** 비용 줄 ["골드 12/48", "공허 독주머니 70/70", …] — 채웠으면 초록 (dark: 창 본문용 어두운 글자) */
    static List<String> costParts(ServerPlayer sp, Cost c, boolean dark) {
        List<String> out = new ArrayList<>();
        long gold = Wallet.total(sp, Economy.Kind.GOLD);
        String ok = dark ? (gold >= c.gold() ? "§2" : "§4") : (gold >= c.gold() ? "§a" : "§7");
        out.add(ok + "골드 " + (dark ? Economy.fmt(gold) : "§f" + Math.min(gold, c.gold())) + "/" + c.gold());
        for (Map.Entry<String, Integer> x : c.items().entrySet()) out.add(Smith.itemLine(sp, x.getKey(), x.getValue(), dark));
        return out;
    }

    /** 칸 설명에 붙는 비용 줄 (§r 로 시작) + 만들 수 있는지 */
    private static List<String> costLore(ServerPlayer sp, Cost c) {
        List<String> out = new ArrayList<>();
        for (String s : costParts(sp, c, false)) out.add("§r" + s);
        out.add(lack(sp, c).isEmpty() ? "§r§a만들 수 있음" : "§r§c재료 부족");
        return out;
    }

    /** 가진 골드 · 재료 칸 (보유 수는 그림 위 숫자 — 장식 칸은 숫자가 안 보여서 일반 칸, 누르면 새로 그림) */
    private static void haveSlots(ServerPlayer sp, ChestForm form, int[] slots) {
        long gold = Wallet.total(sp, Economy.Kind.GOLD);
        form.set(slots[0], JobUpgrades.goldSlot(sp).deco(false).count(gold));
        int i = 1;
        for (String id : setCost().items().keySet()) {
            if (i >= slots.length) break;
            JobUpgrades.Mat m = JobUpgrades.mat(id);
            int n = m.item() == null ? 0 : Inv.count(sp, m.item());
            Slot s = m.item() == null ? Slot.of(Icon.sprite("pane"), m.name()) : Slot.of(Icon.item(m.item()), m.code() + "보유 " + m.name() + " §f" + n);
            form.set(slots[i++], s.count(n).lore("§7" + m.from(), "§7한 벌에 " + setCost().items().get(id) + "개"));
        }
    }

    /** 한 벌 비용을 한 줄에 둘씩: "골드 48 · 공허 독주머니 70" */
    private static List<String> setCostText() {
        Cost c = setCost();
        List<String> parts = new ArrayList<>();
        parts.add("골드 " + c.gold());
        for (Map.Entry<String, Integer> x : c.items().entrySet()) parts.add(JobUpgrades.mat(x.getKey()).name() + " " + x.getValue());
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += 2) lines.add(String.join(" · ", parts.subList(i, Math.min(parts.size(), i + 2))));
        return lines;
    }

    // ------------------------------------------------------------ 쪽 (대장장이 제작 창의 넷째 쪽)

    /** 쪽 이름 · 칸 이름에 쓰는 갑옷 이름: "공허 (초월 50렙)" */
    static String setLabel() {
        Obj tier = R.obj("ARMOR_TIERS").objOrNull(grade() + "_" + level());
        return (tier == null ? "공허" : tier.str("name")) + " (초월 " + level() + "렙)";
    }

    /** 칸 → 갑옷 종류 (ARMOR_TYPE_ORDER — 종류 칸이 아니면 null) */
    static String typeAt(int slot) {
        List<String> order = R.strings("ARMOR_TYPE_ORDER");
        int i = ArmorShop.typeAt(slot, order.size());
        return i < 0 ? null : order.get(i);
    }

    /** 공허의 갑옷 쪽 (Smith.craftStep 이 보여 줌 — 윗줄은 다른 쪽과 같음: 0 강화로 · 4 다음 쪽 · 8 레벨 올리기로) */
    static ChestForm pageForm(ServerPlayer sp, String message, String title, String nextLabel) {
        ChestForm form = new ChestForm(title);
        form.set(Smith.TAB_SLOT, Slot.of("textures/items/nether_star", "§6무기 강화로").lore("§7+1 ~ +10 무기 강화 창"));
        List<String> order = R.strings("ARMOR_TYPE_ORDER");
        Armor.Piece first = piece(order.get(0), "chest");
        form.set(Smith.CRAFT_INFO_SLOT, Slot.of(first == null ? Icon.sprite("pane") : Icon.item(ArmorShop.armorItem(first)), "§5공허의 갑옷 §7· §f초월 " + level() + "렙")
                .lore("§e누르면 " + nextLabel + " 보기", "§7공허 몬스터 전리품과 골드로", "§7공허의 갑옷 (종류 10) 을", "§7부위마다 만듭니다",
                        "§7세트 보너스 " + Js.str(R.obj("ARMOR_TIERS").obj(grade() + "_" + level()).num("bonus")) + " · 요구 " + level() + "렙"));
        form.set(Smith.CRAFT_TAB_SLOT, Slot.of("textures/items/iron_ingot", "§6레벨 올리기로").lore("§7영웅 · 초월 무기 레벨 제한 올리기 창"));
        haveSlots(sp, form, HAVE_SLOTS);
        Cost set = setCost();
        boolean ok = lack(sp, set).isEmpty();
        for (int i = 0; i < order.size(); i++) {
            String type = order.get(i);
            Armor.Piece chest = piece(type, "chest");
            if (chest == null) continue;
            Obj t = TYPES.obj(type);
            List<String> lore = new ArrayList<>();
            lore.add("§r§7세트 방어 §f" + Js.str(chest.setProt()) + "§7 · " + Armor.bonusText(chest.setBonus(), false));
            lore.add("§r§7요구: " + Armor.armorReqText(chest));
            List<String> weapons = new ArrayList<>();
            for (String w : Armor.weaponsFor(type)) weapons.add(R.obj("TYPES").obj(w).str("name"));
            if (!weapons.isEmpty()) lore.add("§r§7어울리는 무기: §f" + String.join(" · ", weapons));
            lore.add("§r§7한 벌:");
            for (String s : costParts(sp, set, false)) lore.add("§r  " + s);
            lore.add(ok ? "§r§a한 벌을 만들 수 있음" : "§r§e누르면 부위별로 만들기");
            form.set(ArmorShop.typeSlot(i), Slot.of(ArmorShop.armorItem(chest), t.str("color") + t.str("name") + " 갑옷 §5공허 세트").lore(lore));
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§8종류 칸을 누르면 부위 창이 열립니다. 윗줄 가운데 칸: " + nextLabel + " 쪽.§r");
        lines.add("§8재료: 골드 + 공허 몬스터 전리품 (양옆 칸 — 가진 수).§r");
        List<String> cost = setCostText();
        lines.add("§8한 벌 §0" + cost.get(0) + "§r");
        for (int i = 1; i < cost.size(); i++) lines.add("§0" + cost.get(i) + "§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    // ------------------------------------------------------------ 종류 창 · 확인 창

    /** 한 종류의 부위 창 (typeWindow): 10 ~ 13 부위 (누르면 확인 창), 15 네 부위 한 번에, 22 돌아가기. 윗줄 = 가진 골드 · 재료 */
    public static void typeWindow(ServerPlayer sp, String type, String message, int guard, Runnable back) {
        if (guard >= 50) {
            back.run();
            return;
        }
        Obj t = TYPES.obj(type);
        List<Armor.Piece> pieces = set(type);
        if (pieces.stream().anyMatch(java.util.Objects::isNull)) {
            back.run();
            return;
        }
        ChestForm form = new ChestForm(t.str("name") + " 갑옷 §8· §5" + setLabel());
        haveSlots(sp, form, TYPE_HAVE_SLOTS);
        for (int i = 0; i < pieces.size(); i++) {
            Armor.Piece a = pieces.get(i);
            List<String> lore = new ArrayList<>(Armor.armorInfo(a, false));
            lore.addAll(costLore(sp, pieceCost(a.piece())));
            form.set(PIECE_FIRST + i, Slot.of(ArmorShop.armorItem(a), a.name()).lore(lore));
        }
        Cost set = setCost();
        List<String> setLore = new ArrayList<>();
        setLore.add("§r§7세트 방어 §f" + Js.str(pieces.get(0).setProt()) + "§7 · " + Armor.bonusText(pieces.get(0).setBonus(), false));
        setLore.addAll(costLore(sp, set));
        form.set(SET_SLOT, Slot.of(Icon.sprite("icon_check"), "§a네 부위 한 번에 만들기").lore(setLore));
        form.set(BACK_SLOT, Slot.of(Icon.sprite("icon_cross"), "§7돌아가기"));
        form.fillEmpty();
        List<String> wear = ArmorShop.lack(sp, pieces.get(0));
        List<String> lines = new ArrayList<>();
        lines.add("§8부위 칸: 그 부위 만들기 · 초록 칸: 네 부위 한 번에§r");
        lines.add("§8요구: " + Armor.armorReqText(pieces.get(0)) + "§r");
        if (!wear.isEmpty()) lines.add("§4지금 입을 수 없음: " + String.join(" · ", wear) + "§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel < 0 || sel == BACK_SLOT) {
                back.run();
                return;
            }
            Runnable again = () -> typeWindow(sp, type, "", guard + 1, back);
            if (sel >= PIECE_FIRST && sel < PIECE_FIRST + pieces.size()) {
                confirm(sp, List.of(pieces.get(sel - PIECE_FIRST)), msg -> typeWindow(sp, type, msg, guard + 1, back));
            } else if (sel == SET_SLOT) {
                confirm(sp, pieces, msg -> typeWindow(sp, type, msg, guard + 1, back));
            } else again.run();
        });
    }

    /** 확인 창 (confirm): 11 만들기 · 13 만들 갑옷 · 15 취소. 결과 문구를 done 으로 (취소면 "") */
    private static void confirm(ServerPlayer sp, List<Armor.Piece> pieces, Consumer<String> done) {
        Armor.Piece a = pieces.get(0);
        boolean one = pieces.size() == 1;
        Cost c = cost(pieces);
        ChestForm form = new ChestForm("공허의 갑옷 제작");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a만들기"));
        Armor.Piece shown = one ? a : pieces.get(Math.min(1, pieces.size() - 1)); // 한 벌이면 갑옷 (몸통) 그림
        List<String> info = new ArrayList<>(Armor.armorInfo(shown, false));
        if (!one) info.add(0, "§r§f네 부위: 투구 · 갑옷 · 바지 · 신발");
        form.set(13, Slot.of(ArmorShop.armorItem(shown), one ? a.name() : a.setName() + " 세트 §7· " + a.typeName() + " (네 부위)").lore(info));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> wear = ArmorShop.lack(sp, a);
        List<String> lines = new ArrayList<>();
        lines.add((one ? a.darkName() : "§0" + a.setName() + " 세트§8 · " + a.typeName() + " §8(네 부위)") + "§r");
        double prot = 0;
        for (Armor.Piece p : pieces) prot += p.prot();
        String bonus = Armor.bonusText(one ? a.bonus() : a.setBonus(), true);
        lines.add("§8방어 §0+" + Js.str(prot) + (bonus.isEmpty() ? "" : "§8 · " + bonus) + "§r");
        lines.add("§8요구: " + Armor.armorReqText(a) + "§r");
        if (!wear.isEmpty()) lines.add("§4지금 입을 수 없음: " + String.join(" · ", wear) + "§r");
        // 비용은 "이름 가진 수/필요" 로 한 줄에 둘씩 (휴대폰 창 한 줄 180px)
        List<String> parts = costParts(sp, c, true);
        for (int i = 0; i < parts.size(); i += 2) lines.add("§8" + String.join(" §8· ", parts.subList(i, Math.min(parts.size(), i + 2))) + "§r");
        lines.add("§8왼쪽 초록 칸: 만들기 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> done.accept(sel == 11 ? craft(sp, pieces) : ""));
    }

    /**
     * 공허의 갑옷 만들기 (craft). 결과 문구 (창 본문용). 비용이 모두 있는지 · 낸 뒤 가방에 갑옷이 들어가는지 확인한 뒤, 재료 → 골드 순으로 내고 갑옷을 줌.
     * 모자라거나 가방이 차면 아무것도 쓰지 않음
     */
    public static String craft(ServerPlayer sp, List<Armor.Piece> pieces) {
        // List.of 의 contains(null) 은 NullPointerException — 하나씩 봄
        if (!enabled() || pieces.isEmpty() || pieces.stream().anyMatch(java.util.Objects::isNull)) return "§4갑옷을 만들 수 없습니다.§r";
        Cost c = cost(pieces);
        List<String> lack = lack(sp, c);
        if (!lack.isEmpty()) return Smith.lackMessage(sp, lack);
        List<ItemStack> items = new ArrayList<>();
        for (Armor.Piece a : pieces) {
            ItemStack it = ArmorShop.armorItem(a);
            if (it.isEmpty()) return "§4갑옷을 만들 수 없습니다.§r";
            items.add(it);
        }
        // 낼 재료 · 가방의 금화를 뺀 뒤 갑옷이 모두 들어가는지 (땅에 떨어지지 않게 — 갑옷 상점과 같음)
        List<Inv.Take> takes = new ArrayList<>();
        for (Map.Entry<String, Integer> x : c.items().entrySet()) {
            JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
            if (m.item() != null) takes.add(new Inv.Take(m.item(), x.getValue()));
        }
        takes.addAll(Wallet.takes(sp, Economy.Kind.GOLD, c.gold()));
        if (!Inv.fitsAfter(sp, takes, items)) return Inv.bagFull(sp);
        // 모두 있는 것을 위에서 확인했으므로 차례로 냄 (하나라도 실패하면 그 뒤는 내지 않음)
        for (Map.Entry<String, Integer> x : c.items().entrySet()) {
            JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
            if (m.item() == null || !Inv.remove(sp, m.item(), x.getValue())) return "§4결제 오류§r";
        }
        if (c.gold() > 0 && !Wallet.pay(sp, Economy.Kind.GOLD, c.gold())) return "§4결제 오류§r";
        for (ItemStack it : items) Inv.give(sp, it);
        craftFx(sp);
        Armor.Piece a = pieces.get(0);
        boolean one = pieces.size() == 1;
        List<String> wear = ArmorShop.lack(sp, a);
        String what = one ? a.name() : a.setName() + " 세트 §7· " + a.typeName();
        Notify.notify(sp, "§5공허의 갑옷 제작: " + what + (wear.isEmpty() ? "" : " §7(입으려면 " + String.join(" · ", wear) + ")"), 60);
        return "§2완료: " + (one ? a.darkName() : "§0" + a.setName() + " 세트§8 · " + a.typeName() + " (네 부위)") + "§r";
    }

    /** 만들기 연출: 모루 · 레벨 업 · 자수정 울림 소리, 머리 아래 공허 빛 (역포탈 · 포탈 알갱이) */
    private static void craftFx(ServerPlayer sp) {
        Smith.forgeFx(sp, 0.8, 0.9, 1.1, 0.6, 4);
        ServerLevel level = sp.level();
        Vec3 at = sp.position().add(0, 1.0, 0);
        SkillFx.play(level, "resonate.amethyst_block", at, 0.7, 1.0);
        SkillFx.play(level, "respawn_anchor.charge", at, 1.2, 0.6);
        SkillFx.playFor(sp, "armor.equip_iron", 0.8, 1);
        SkillFx.particle(level, ParticleTypes.REVERSE_PORTAL, at, 40, 0.45, 0.04);
        SkillFx.particle(level, ParticleTypes.PORTAL, at, 30, 0.4, 0.6);
        SkillFx.particle(level, ParticleTypes.WITCH, at.add(0, 0.6, 0), 12, 0.35, 0.02);
    }

    /** 한 종류의 쪽 칸 번호 (시험용 — ArmorShop.typeSlot 과 같음) */
    public static int typeSlot(String type) {
        return ArmorShop.typeSlot(R.strings("ARMOR_TYPE_ORDER").indexOf(type));
    }

    /** 쪽 처음 여는 점수: 가진 재료 (한 벌에 드는 만큼까지 — Smith.firstCraftPage) */
    static int score(ServerPlayer sp) {
        if (!enabled()) return 0;
        int s = 0;
        for (Map.Entry<String, Integer> x : setCost().items().entrySet()) {
            JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
            s += Math.min(m.item() == null ? 0 : Inv.count(sp, m.item()), x.getValue());
        }
        return s;
    }
}
