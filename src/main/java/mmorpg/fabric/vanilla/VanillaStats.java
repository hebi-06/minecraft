package mmorpg.fabric.vanilla;

import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.StatSheet;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Slot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 4b: 모드 없는 클라이언트의 스탯 창 (모드는 StatScreen — 스탯 책 · K). 상자 메뉴 9 × 6 (Forms 상자 창 → VanillaForms):
 * <pre>
 * [정보] · · · · · · [장터][쓰레기통]
 *  · [힘][+1][+5] · [민첩][+1][+5] ·
 *  · [마력][+1][+5] · [체력][+1][+5] ·
 *  · · · · [기준 무기] · · · ·
 *  · · · · · · · · ·
 * [퀘스트][창고][탈것][탈것 고르기][스킬 칸][스탯 트리][아티팩트][유물][닫기]
 * </pre>
 * 스탯 그림을 누르면 그 스탯의 스탯 트리, +1 · +5 는 투자 후 같은 메뉴를 다시 그림 (VanillaForms 가 열린 메뉴를 그대로 씀). 포인트가 없으면 +1 · +5 는 어둡게.
 * 아랫줄 단추는 모드 창의 아랫줄과 같은 할 일 (RpgNet.statAction) — 스킬 칸은 VanillaSkillBar.
 */
public final class VanillaStats {
    private VanillaStats() {}

    /** 스탯 그림 칸 (그 오른쪽 두 칸이 +1 · +5) — Stats.STAT_KEYS 순서 */
    private static final int[] STAT_AT = {10, 14, 19, 23};
    private static final int WEAPON_AT = 31;
    private static final int MARKET = 7, TRASH = 8;
    private static final int QUEST = 45, ENDER = 46, MOUNT = 47, MOUNT_SELECT = 48, SKILLS = 49, TREE = 50, ARTIFACT = 51, RELIC = 52, CLOSE = 53;

    /** 스탯 창 (RpgNet.openStats — 모드 없는 클라이언트) */
    public static void open(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return;
        StatSheet s = StatSheet.of(sp);
        int point = s.point();
        ChestForm f = new ChestForm("§6§l스탯 §r§8· §7남은 포인트 §f" + point, ChestForm.BIG_ROWS);
        List<String> info = new ArrayList<>();
        info.add("§7레벨 §f" + sp.experienceLevel + " §8(최고 " + (int) s.lvmax() + ")");
        info.add("§7체력 §f" + Math.round(sp.getHealth()) + " §7/ §f" + Math.round(sp.getMaxHealth()));
        info.add("§7남은 포인트 §f" + point);
        String job = mmorpg.fabric.job.JobSystem.jobOf(sp);
        if (job != null && !job.isEmpty()) {
            mmorpg.fabric.job.JobSkills.Header h = mmorpg.fabric.job.JobSkills.header.apply(sp);
            info.add("§7직업 §d" + (h != null ? h.name() : job));
        }
        f.set(0, Slot.of(new ItemStack(RpgItems.STAT_BOOK), "§e" + sp.getName().getString()).lore(info));
        f.set(MARKET, Slot.of(new ItemStack(mmorpg.fabric.economy.EconomyItems.GOLD_COIN), "§6유저 장터").lore("§7다른 사람이 올린 물건을 사고 팖"));
        f.set(TRASH, Slot.of(new ItemStack(Items.LAVA_BUCKET), "§c쓰레기통").lore("§7넣고 닫으면 지워집니다"));

        List<String> keys = Stats.STAT_KEYS;
        for (int i = 0; i < STAT_AT.length && i < keys.size(); i++) {
            String k = keys.get(i);
            int at = STAT_AT[i];
            String name = Stats.info(k).str("name");
            List<String> lore = new ArrayList<>(s.tooltip(k));
            if (!lore.isEmpty()) lore.remove(lore.size() - 1); // 모드 창의 "눌러서 +1 (Shift: +5)" 줄
            lore.add("§b누르면 " + name + " 스탯 트리");
            f.set(at, Slot.of(Icon.sprite("icon_" + k), s.title(k)).lore(lore));
            Slot one = Slot.of(Icon.sprite("icon_point"), (point > 0 ? "§a" : "§8") + name + " +1").lore(point > 0 ? "§7포인트 1 을 씀" : "§8포인트 없음");
            Slot five = Slot.of(Icon.sprite("icon_point"), (point > 0 ? "§a" : "§8") + name + " +5").lore(point > 0 ? "§7포인트 " + Math.min(5, point) + " 을 씀" : "§8포인트 없음").count(5);
            if (point <= 0) {
                one.dim();
                five.dim();
            }
            f.set(at + 1, one).set(at + 2, five);
        }

        ItemStack[] held = {sp.getMainHandItem(), sp.getOffhandItem()};
        Weapon w = StatSheet.referenceWeapon(held[0], held[1], sp.getInventory().getNonEquipmentItems(), s::usable);
        if (w != null) {
            int enh = mmorpg.fabric.gear.WeaponEnhance.reference(held[0], sp.getInventory().getNonEquipmentItems(), w);
            List<String> lines = new ArrayList<>(s.weaponLines(w, enh, false));
            f.set(WEAPON_AT, Slot.of(weaponStack(sp, w), "§f기준 무기 §8· §e" + w.name()).lore(lines).deco());
        }

        f.set(QUEST, Slot.of(Icon.sprite("q_active"), "§e퀘스트").lore("§7받은 퀘스트 · 진행"));
        f.set(ENDER, Slot.of(Icon.sprite("ender_chest"), "§d창고").lore("§7엔더 창고 (큰 상자 5개)"));
        f.set(MOUNT, Slot.of(Icon.sprite("icon_mount"), "§b탈것").lore("§7고른 탈것을 불러 탐 · 타고 있으면 돌려보냄"));
        f.set(MOUNT_SELECT, Slot.of(new ItemStack(Items.SADDLE), "§b탈것 고르기"));
        f.set(SKILLS, Slot.of(skillIcon(sp), "§b스킬 칸").lore("§7룬 올리기 · 빼기 · 옮기기, 직업 스킬 보기",
                "§7F 로 룬 스킬 → 직업 스킬 → 일반"));
        f.set(TREE, Slot.of(new ItemStack(Items.OAK_SAPLING), "§a스탯 트리").lore("§7스탯마다 받은 칸 · 고를 갈래 (채팅)"));
        f.set(ARTIFACT, Slot.of(new ItemStack(Items.AMETHYST_SHARD), "§d아티팩트").lore("§7낀 아티팩트 요약 (채팅)"));
        f.set(RELIC, Slot.of(new ItemStack(Items.ECHO_SHARD), "§6유물").lore("§7꽂은 유물 요약 (채팅)"));
        f.set(CLOSE, Slot.of(Icon.sprite("icon_cross"), "§c닫기"));
        f.fillEmpty();
        f.show(sp, sel -> click(sp, sel));
    }

    /** 스킬 칸 단추 그림: 첫 룬의 그림만 (룬 아이템 그대로면 룬 설명이 단추 설명에 섞임) — 룬이 없으면 마법 책 */
    private static ItemStack skillIcon(ServerPlayer sp) {
        for (ItemStack st : mmorpg.fabric.rune.SkillBar.slots(sp)) {
            net.minecraft.resources.Identifier model = st.isEmpty() ? null : st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
            if (model == null) continue;
            ItemStack icon = new ItemStack(Items.PAPER);
            icon.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, model);
            return icon;
        }
        return new ItemStack(Items.ENCHANTED_BOOK);
    }

    /** 기준 무기의 아이템 (가진 것 — 강화 · 꾸밈 그대로, 없으면 그 무기 아이템) */
    private static ItemStack weaponStack(ServerPlayer sp, Weapon w) {
        List<ItemStack> all = new ArrayList<>();
        all.add(sp.getMainHandItem());
        all.add(sp.getOffhandItem());
        for (ItemStack st : sp.getInventory().getNonEquipmentItems()) all.add(st);
        for (ItemStack st : all) {
            Weapon x = RpgCombat.weaponOf(st);
            if (x != null && x.id().equals(w.id())) return st.copy();
        }
        for (RpgWeaponItem it : RpgItems.WEAPONS) if (it.weapon().id().equals(w.id())) return new ItemStack(it);
        return new ItemStack(Items.IRON_SWORD);
    }

    private static void click(ServerPlayer sp, int sel) {
        if (sel < 0 || sp.isRemoved()) return; // 닫음
        List<String> keys = Stats.STAT_KEYS;
        for (int i = 0; i < STAT_AT.length && i < keys.size(); i++) {
            int at = STAT_AT[i];
            if (sel == at) {
                mmorpg.fabric.stattree.StatTrees.open(sp, keys.get(i));
                return;
            }
            if (sel == at + 1 || sel == at + 2) {
                RpgPlayers.spend(sp, keys.get(i), sel == at + 1 ? 1 : 5);
                open(sp);
                return;
            }
        }
        switch (sel) {
            case MARKET -> RpgNet.statAction(sp, "market");
            case TRASH -> RpgNet.statAction(sp, "trash");
            case QUEST -> RpgNet.statAction(sp, "quest");
            case ENDER -> RpgNet.statAction(sp, "ender");
            case MOUNT -> RpgNet.statAction(sp, "mount");
            case MOUNT_SELECT -> RpgNet.statAction(sp, "mount_select");
            case SKILLS -> VanillaSkillBar.open(sp);
            case TREE -> mmorpg.fabric.stattree.StatTrees.open(sp, null);
            case ARTIFACT -> mmorpg.fabric.artifact.ArtifactSystem.open(sp);
            case RELIC -> mmorpg.fabric.relic.RelicSystem.open(sp);
            case CLOSE -> {
            }
            default -> open(sp);
        }
    }
}
