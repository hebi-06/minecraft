package mmorpg.fabric.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import mmorpg.core.Armor;
import mmorpg.core.Economy;
import mmorpg.core.Js;
import mmorpg.core.Leveling;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.shop.ArmorShop;
import mmorpg.fabric.shop.Shop;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 퀘스트 보상 (quest.js rewardFor · rewardStacks · materialStack · giveRewards · missingSlots · rewardLevelLine · rewardArmorLine)
 * 과 무기 시험용 무기 (trialItem · giveTrialWeapons · takeBackTrials — 애드온의 아이템 동적 속성 "rpg:trial" 은 자바에서 사용자 데이터 표시).
 */
public final class QuestRewards {
    private QuestRewards() {}

    private static final Rules R = Rules.get();
    private static final Obj RUNE = R.obj("RUNE");
    /** 시험용 무기 표시 (사용자 데이터 — 애드온의 아이템 동적 속성 이름 그대로) */
    static final String TRIAL = "rpg:trial";

    /**
     * 이 플레이어가 실제로 받는 보상: 반복 의뢰의 경험치는 최고 레벨이 그 몬스터의 xpUntil 이상이면 0 (capped),
     * 왕실 퀘스트의 갑옷은 무기의 길에 맞는 종류 (armorType)
     */
    public static Quest.Reward rewardFor(ServerPlayer sp, Quest q) {
        return rewardFor(sp, q, q.reward);
    }

    public static Quest.Reward rewardFor(ServerPlayer sp, Quest q, Quest.Reward base) {
        Quest.Reward r = base.copy();
        if (!q.xpMob.isEmpty() && Loot.xpCapped(sp, q.xpMob)) {
            r.xpPoints = 0;
            r.xp = 0;
            r.capped = q.xpMob;
        }
        // 9단계 (레벨업 3배): 레벨 보상은 지금 레벨부터 그 레벨 수만큼의 바닐라 경험치로 — 필요 경험치가 3배라 실제로는 1/3 레벨
        if (r.xp > 0) {
            r.xpPoints += Leveling.pointsForLevels(sp.experienceLevel, r.xp);
            r.xp = 0;
        }
        if (r.armor.equals("path")) r.armorType = SideQuests.PATH_ARMOR.getOrDefault(Quests.pathOf(sp), "str");
        return r;
    }

    /** 왕실 퀘스트 갑옷 (초월 40렙 한 벌, 부위 순서) */
    static List<Armor.Piece> armorSet(String type) {
        List<Armor.Piece> out = new ArrayList<>();
        for (String piece : Armor.PIECE_ORDER) {
            Armor.Piece a = Armor.armorFrom(type, 40, "transcend", piece);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** 붙인 이름 (기울이지 않음 — 애드온 nameTag) */
    static Component nameTag(String text) {
        return Component.literal(BedrockText.toJava(text)).withStyle(s -> s.withItalic(false));
    }

    /** 한 칸 최대 개수로 나눠 담음 (모르는 아이템이면 없음) */
    private static void split(List<ItemStack> out, String id, int amount, IntFunction<ItemStack> make) {
        Item item = Icon.itemById(id);
        if (item == null) {
            Mmorpg.LOG.warn("[mmorpg] reward item {}: unknown", id);
            return;
        }
        int max = Math.max(1, new ItemStack(item).getMaxStackSize());
        for (int left = amount; left > 0; ) {
            int n = Math.min(max, left);
            ItemStack s = make.apply(n);
            if (s != null && !s.isEmpty()) out.add(s);
            left -= n;
        }
    }

    /** 보상으로 줄 아이템 묶음 (giveRewards 가 이대로 줌 — 가방 계산 missingSlots 도 같은 묶음으로). 실버 · 골드는 지갑으로 (0.21.0 — 아이템 아님) */
    public static List<ItemStack> rewardStacks(Quest.Reward r) {
        List<ItemStack> out = new ArrayList<>();
        Weapon w = QuestText.rewardWeapon(r);
        if (w != null) {
            ItemStack s = Shop.weaponItem(w);
            if (!s.isEmpty()) out.add(s);
        }
        if (r.item != null) {
            Quest.RewardItem it = r.item;
            Weapon iw = QuestText.rewardItemWeapon(it);
            String tag = it.nameTag != null && !it.nameTag.isEmpty() ? it.nameTag : null;
            if (iw != null) {
                for (int i = 0; i < Math.min(64, it.amount); i++) {
                    ItemStack s = Shop.weaponItem(iw);
                    if (s.isEmpty()) continue;
                    if (tag != null) s.set(DataComponents.CUSTOM_NAME, nameTag(tag));
                    out.add(s);
                }
            } else {
                split(out, it.id, it.amount, n -> {
                    // 전리품 · 룬 조각 · 뽑기권 · 레이드 재료를 주는 만든 퀘스트도 게임에서 얻은 것과 같게 (겹쳐 쌓이게)
                    ItemStack s = materialStack(it.id, n);
                    if (tag != null && !s.isEmpty()) s.set(DataComponents.CUSTOM_NAME, nameTag(tag));
                    return s;
                });
            }
        }
        for (Quest.RewardItem it : r.items) split(out, it.id, it.amount, n -> materialStack(it.id, n));
        // 왕실 퀘스트: 무기의 길에 맞는 초월 갑옷 한 벌 (rewardFor 가 종류를 정한 보상만)
        if (!r.armorType.isEmpty()) {
            for (Armor.Piece a : armorSet(r.armorType)) {
                ItemStack s = ArmorShop.armorItem(a);
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    /** 재료 보상: 레이드 · 보스 재료 · 룬 뽑기권 · 고급 룬 뽑기권 · 룬 조각 · 전리품은 게임에서 얻은 것과 같은 아이템으로 (설명은 아이템이 그림) */
    public static ItemStack materialStack(String id, int n) {
        if (id.equals(RUNE.str("ticketItem"))) return RuneItems.ticket(n, false);
        if (id.equals(RUNE.str("advTicketItem"))) return RuneItems.ticket(n, true);
        Item gear = GearItems.byId(id);
        if (gear != null) return new ItemStack(gear, n);
        Item item = Icon.itemById(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, n);
    }

    /**
     * 받은 무기를 아직 쓸 수 없으면 알림 한 줄 (렙제). 보상 무기 (무기 보상 · 아이템 보상 — 붙인 이름이 있으면 그 이름) 중 레벨이 가장 높은 것.
     * 보상 경험치를 받은 뒤의 레벨로 봄. 없으면 ""
     */
    public static String rewardLevelLine(ServerPlayer sp, Quest.Reward r) {
        Weapon top = null;
        String topName = "";
        Weapon w = QuestText.rewardWeapon(r);
        if (w != null && Weapons.needsLevel(w)) {
            top = w;
            topName = w.name();
        }
        Weapon iw = QuestText.rewardItemWeapon(r.item);
        if (iw != null && Weapons.needsLevel(iw) && (top == null || iw.level() > top.level())) {
            top = iw;
            topName = r.item.nameTag != null && !r.item.nameTag.isEmpty() ? r.item.nameTag : iw.name();
        }
        double lv = RpgCombat.reqLevelOf(sp);
        return top != null && lv < top.level() ? topName + "§r§c: 레벨 " + top.level() + "부터 쓸 수 있음 §7(지금 최고 레벨 " + Js.str(lv) + ")" : "";
    }

    /** 왕실 갑옷: 아직 입을 수 없으면 알림 한 줄 (모자란 레벨 · 스탯 — 입어도 벗겨짐). 없으면 "" */
    public static String rewardArmorLine(ServerPlayer sp, Quest.Reward r) {
        if (r.armorType.isEmpty()) return "";
        List<Armor.Piece> set = armorSet(r.armorType);
        List<String> lack = set.isEmpty() ? List.of() : ArmorShop.lack(sp, set.get(0));
        return lack.isEmpty() ? "" : "§c" + QuestText.EMPEROR + " 갑옷: " + String.join(" · ", lack) + " 필요 §7(모자라면 입어도 벗겨짐)";
    }

    /** 완료 창 아래에 붙이는 알림 (줄바꿈 + rewardLevelLine · rewardArmorLine) */
    public static String rewardLevelNote(ServerPlayer sp, Quest.Reward r) {
        List<String> lines = new ArrayList<>();
        String a = rewardLevelLine(sp, r);
        if (!a.isEmpty()) lines.add(a);
        String b = rewardArmorLine(sp, r);
        if (!b.isEmpty()) lines.add(b);
        return lines.isEmpty() ? "" : "\n" + String.join("\n", lines);
    }

    /**
     * 보상 주기: 경험치 (바닐라 경험치 — 레벨 보상은 9단계부터 그 레벨 수만큼의 바닐라 경험치로, rewardFor 가 미리 바꿔 둠), 스탯 포인트,
     * 실버 · 골드 (0.21.0 — 지갑으로), 아이템 (가방이 차면 발밑)
     */
    public static void giveRewards(ServerPlayer sp, Quest.Reward r) {
        if (r.xp > 0) sp.giveExperiencePoints(Leveling.pointsForLevels(sp.experienceLevel, r.xp));
        if (r.xpPoints > 0) sp.giveExperiencePoints(r.xpPoints);
        if (r.points > 0) RpgPlayers.addPoints(sp, r.points);
        if (r.emerald > 0) Inv.earn(sp, r.emerald);
        if (r.gold > 0) Wallet.give(sp, Economy.Kind.GOLD, r.gold);
        for (ItemStack stack : rewardStacks(r)) {
            try {
                Inv.give(sp, stack);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] reward", e);
            }
        }
    }

    /**
     * 보상을 넣을 자리가 모자란 칸 수 (가방이 차서 보상이 땅에 떨어지면 사라질 수 있음 — 특히 전설 무기). 실제로 줄 묶음 (rewardStacks) 을 가방 36칸에
     * 넣어 보는 셈: 이미 있는 묶음에는 겹칠 수 있을 때만. 가져오기 퀘스트는 가져갈 아이템을 뺀 뒤로, trials = 시험용 무기를 돌려받은 뒤로
     */
    public static int missingSlots(ServerPlayer sp, Quest q, Quest.Reward r, boolean trials) {
        ItemStack[] items = new ItemStack[Inv.SIZE];
        int[] n = new int[Inv.SIZE];
        for (int i = 0; i < Inv.SIZE; i++) {
            ItemStack s = Inv.get(sp, i);
            if (s.isEmpty() || (trials && isTrial(s))) continue;
            items[i] = s;
            n[i] = s.getCount();
        }
        if (q.type.equals("collect")) {
            Item target = Icon.itemById(q.target);
            int left = q.count;
            for (int i = 0; i < Inv.SIZE && left > 0 && target != null; i++) {
                if (items[i] == null || !items[i].is(target)) continue;
                int take = Math.min(left, n[i]);
                left -= take;
                n[i] -= take;
                if (n[i] <= 0) items[i] = null;
            }
        }
        int missing = 0;
        for (ItemStack stack : rewardStacks(r)) {
            int left = stack.getCount();
            int max = stack.getMaxStackSize();
            for (int i = 0; i < Inv.SIZE && left > 0; i++) {
                if (items[i] == null || n[i] >= Math.min(max, items[i].getMaxStackSize())) continue;
                if (!ItemStack.isSameItemSameComponents(items[i], stack)) continue;
                int add = Math.min(left, max - n[i]);
                n[i] += add;
                left -= add;
            }
            if (left <= 0) continue;
            int free = -1;
            for (int i = 0; i < Inv.SIZE; i++) {
                if (items[i] == null) {
                    free = i;
                    break;
                }
            }
            if (free < 0) {
                missing++;
                continue;
            }
            items[free] = stack;
            n[free] = left;
        }
        return missing;
    }

    // ------------------------------------------------------------ 무기 시험용 무기

    /** 시험용 무기인지 */
    public static boolean isTrial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBooleanOr(TRIAL, false);
    }

    /** 시험용 무기 (0렙 일반, 이름 앞 [시험용], 무기를 고르면 돌려받음) */
    public static ItemStack trialItem(String type) {
        Weapon w = Weapons.currentWeapon(Weapons.parseWeapon("rpg:" + type + "_l0_common"));
        if (w == null) return ItemStack.EMPTY;
        ItemStack item = Shop.weaponItem(w);
        if (item.isEmpty()) return item;
        item.set(DataComponents.CUSTOM_NAME, nameTag("§7[시험용] §f" + w.title() + "§r§7 [0렙]"));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TRIAL, true);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return item;
    }

    /** 아직 가진 시험용 무기가 없는 종류만 줌 (포기했다가 다시 받아도 겹치지 않게) */
    static void giveTrialWeapons(ServerPlayer sp, List<String> types) {
        Set<String> have = new HashSet<>();
        for (int i = 0; i < Inv.SIZE; i++) {
            ItemStack it = Inv.get(sp, i);
            Weapon w = isTrial(it) ? RpgCombat.weaponOf(it) : null;
            if (w != null) have.add(w.type());
        }
        for (String t : types) {
            if (have.contains(t)) continue;
            ItemStack s = trialItem(t);
            if (!s.isEmpty()) Inv.give(sp, s);
        }
    }

    /** 시험용 무기를 모두 돌려받음 (가방 · 왼손). 돌려받은 수 */
    public static int takeBackTrials(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < Inv.SIZE; i++) {
            if (!isTrial(Inv.get(sp, i))) continue;
            Inv.set(sp, i, ItemStack.EMPTY);
            n++;
        }
        if (isTrial(sp.getOffhandItem())) {
            sp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            n++;
        }
        return n;
    }
}
