package mmorpg.fabric.vanilla;

import mmorpg.core.Runes;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.SkillBar;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Slot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 4b: 모드 없는 클라이언트의 스킬 칸 관리 창 (모드는 SkillBarScreen — J). 상자 메뉴 9 × 6:
 * <pre>
 * [안내] · · · · · · · [스탯 창으로]
 * [룬 1] … [룬 8] [빼기]
 * [직업 1] … [직업 5] · · · ·
 * [가방의 룬 …] (3줄 — 27개까지)
 * </pre>
 * 고르고 놓기: 룬 칸 · 가방의 룬을 누르면 고름 (빛남), 다음에 누른 칸으로 — 룬 칸 → 룬 칸 = 옮기기 (서로 바꿈), 가방 → 룬 칸 = 올리기 (있던 룬은 그 가방 칸으로),
 * 고른 룬 칸 + 빼기 = 가방으로. 같은 것을 다시 누르면 고르기 풂. 서버 동작은 SkillBar.equip · unequip · move 그대로 (실패 문구는 그쪽이 알림).
 * 직업 스킬 칸은 보기만 (잠김 사유 · 재사용 대기).
 */
public final class VanillaSkillBar {
    private VanillaSkillBar() {}

    private static final int INFO = 0, BACK = 8;
    private static final int BAR = 9;
    private static final int REMOVE = 17;
    private static final int JOB = 18;
    private static final int BAG = 27;
    private static final int BAG_MAX = 27;

    /** 고른 것: 룬 칸 (bar = true, 칸 번호) 또는 가방 칸 (bar = false, 가방 칸 번호) */
    private record Pick(boolean bar, int index) {}

    public static void open(ServerPlayer sp) {
        open(sp, null);
    }

    private static void open(ServerPlayer sp, Pick pick) {
        if (sp == null || sp.isRemoved()) return;
        String hint = pick == null ? "" : pick.bar() ? " §8· §e옮길 칸 · 빼기" : " §8· §e올릴 칸을 누르세요";
        ChestForm f = new ChestForm("§b§l스킬 칸" + hint, ChestForm.BIG_ROWS);
        f.set(INFO, Slot.of(Icon.sprite("q_ready"), "§b스킬 칸 쓰는 법").lore(
                "§fF §7한 번: 룬 스킬 — 숫자 1 ~ 8",
                "§fF §7두 번: 직업 스킬 — 숫자 1 ~ 5",
                "§fF §7세 번: 일반 (숫자 = 핫바)",
                "§7룬을 들고 우클릭해도 빈 칸에 올라감",
                "§8룬 칸 · 가방의 룬을 누르면 고르고, 다음에 누른 칸으로 옮김").deco());
        f.set(BACK, Slot.of(Icon.sprite("icon_prev"), "§7스탯 창으로"));

        for (int i = 0; i < SkillBar.SIZE; i++) {
            ItemStack st = SkillBar.get(sp, i);
            boolean picked = pick != null && pick.bar() && pick.index() == i;
            Runes.Rune r = RuneItems.runeOf(st);
            if (r == null) {
                f.set(BAR + i, Slot.of(Icon.sprite("slot"), "§8" + (i + 1) + "번 칸 §7(비어 있음)")
                        .lore(pick != null ? "§e누르면 여기로" : "§7가방의 룬을 고른 뒤 누르면 올림"));
                continue;
            }
            ItemStack icon = st.copy();
            if (picked) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            long left = RuneCast.cooldownLeft(sp, r);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + (i + 1) + "번 칸 §8· §7룬 스킬 모드에서 숫자 " + (i + 1));
            if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
            lore.add(picked ? "§e골랐음 §7— 다른 칸을 누르면 옮김 · 빼기 단추" : pick != null ? "§e누르면 여기로 (서로 바꿈)" : "§a누르면 고름");
            f.set(BAR + i, Slot.of(icon, (picked ? "§e▶ " : "") + r.gradeColor() + r.def().str("name")).lore(lore));
        }
        boolean canRemove = pick != null && pick.bar() && !SkillBar.get(sp, pick.index()).isEmpty();
        Slot remove = Slot.of(Icon.sprite("icon_cross"), canRemove ? "§c빼기 §7(가방으로)" : "§8빼기").lore(canRemove ? "§7고른 룬을 가방으로" : "§8룬 칸을 먼저 고르세요");
        f.set(REMOVE, canRemove ? remove : remove.dim());

        for (int i = 0; i < JobSkills.SIZE; i++) {
            JobSkills.Skill s = JobSkills.skill(sp, i);
            if (s == null) {
                f.set(JOB + i, Slot.of(Icon.sprite("slot"), "§8직업 스킬 " + (i + 1) + " §7(없음)").lore("§7마을의 직업 교관에게서 직업을 고르면 들어감").deco());
                continue;
            }
            String lock = s.lock(sp);
            boolean locked = lock != null && !lock.isEmpty();
            ItemStack icon = new ItemStack(net.minecraft.world.item.Items.PAPER);
            icon.set(DataComponents.ITEM_MODEL, mmorpg.fabric.Mmorpg.rpg("vjob/" + VanillaInput.iconId(s.id()) + (locked ? "_locked" : "")));
            List<String> lore = new ArrayList<>();
            lore.add("§7직업 스킬 모드에서 숫자 " + (i + 1));
            if (locked) lore.add("§c" + JobSkills.lockText(lock));
            long left = JobSkills.cooldownLeft(sp, s);
            if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
            f.set(JOB + i, Slot.of(icon, (locked ? "§8" : "§d") + s.name()).lore(lore).deco());
        }

        Inventory inv = sp.getInventory();
        int[] slots = new int[BAG_MAX]; // 가방 줄 칸 → 가방 칸 번호
        int n = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE && n < BAG_MAX; slot++) {
            ItemStack st = inv.getItem(slot);
            Runes.Rune r = RuneItems.runeOf(st);
            if (r == null) continue;
            boolean picked = pick != null && !pick.bar() && pick.index() == slot;
            ItemStack icon = st.copy();
            if (picked) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            int at = SkillBar.indexOf(sp, r.id());
            List<String> lore = new ArrayList<>();
            lore.add("§7가방 " + (slot < 9 ? "핫바 " + (slot + 1) : (slot + 1) + "번") + " 칸");
            if (at >= 0) lore.add("§8같은 룬이 " + (at + 1) + "번 스킬 칸에 있음");
            lore.add(picked ? "§e골랐음 §7— 올릴 룬 칸을 누르세요" : "§a누르면 고름");
            f.set(BAG + n, Slot.of(icon, (picked ? "§e▶ " : "") + r.gradeColor() + r.def().str("name")).lore(lore));
            slots[n] = slot;
            n++;
        }
        int[] bagSlots = java.util.Arrays.copyOf(slots, n);
        if (n == 0) f.set(BAG + 4, Slot.of(Icon.sprite("item_unknown"), "§7가방에 룬이 없습니다").lore("§7룬은 뽑기 · 상점 · 보상으로 얻음").deco());
        f.fillRange(0, BAG, Slot.PANE);
        f.show(sp, sel -> click(sp, pick, bagSlots, sel));
    }

    private static void click(ServerPlayer sp, Pick pick, int[] bagSlots, int sel) {
        if (sel < 0 || sp.isRemoved()) return; // 닫음
        if (sel == BACK) {
            VanillaStats.open(sp);
            return;
        }
        if (sel == REMOVE) {
            if (pick != null && pick.bar()) SkillBar.unequip(sp, pick.index());
            open(sp, null);
            return;
        }
        if (sel >= BAR && sel < BAR + SkillBar.SIZE) {
            int i = sel - BAR;
            if (pick == null) {
                open(sp, SkillBar.get(sp, i).isEmpty() ? null : new Pick(true, i));
            } else if (pick.bar()) {
                if (pick.index() != i) SkillBar.move(sp, pick.index(), i);
                open(sp, null);
            } else {
                SkillBar.equip(sp, pick.index(), i);
                open(sp, null);
            }
            return;
        }
        if (sel >= BAG && sel < BAG + bagSlots.length) {
            int slot = bagSlots[sel - BAG];
            if (pick != null && pick.bar()) {
                SkillBar.equip(sp, slot, pick.index()); // 고른 룬 칸에 이 룬을 (있던 룬은 이 가방 칸으로)
                open(sp, null);
            } else {
                open(sp, pick != null && pick.index() == slot ? null : new Pick(false, slot));
            }
            return;
        }
        open(sp, pick);
    }
}
