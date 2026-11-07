package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import mmorpg.core.Runes;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.SkillBar;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 메뉴의 스킬 쪽 (VanillaMenu — 왼쪽 탭 "스킬", 사용자 "스킬 세팅도 왼쪽에 박고"):
 * <pre>
 * 스킬 세팅                                   ▶ 고른 룬
 * 룬 스킬  F 한 번 · 숫자 1 ~ 8
 * [룬 1] … [룬 8]                    (줄 1 · 칸 1 ~ 8)
 * 직업 스킬  F 두 번 · 숫자 1 ~ 5
 * [직업 1] … [직업 5]      [빼기] [도움말]   (줄 3)
 * </pre>
 * 고르고 놓기: 룬 칸 · 가방의 룬을 누르면 고름, 다음에 누른 칸으로 — 룬 칸 → 룬 칸 옮기기 (서로 바꿈), 가방 → 룬 칸 올리기 (있던 룬은 그 가방 칸으로),
 * 고른 룬 칸 + 빼기 = 가방으로. 같은 것을 다시 누르면 풂. 서버 동작은 SkillBar.equip · unequip · move (실패 문구는 그쪽이 알림).
 */
public final class VanillaSkillBar {
    private VanillaSkillBar() {}

    /** 고른 것: 룬 칸 (bar = true, 칸 번호) 또는 가방 칸 (bar = false, 가방 칸 번호) */
    record Pick(boolean bar, int index) {}

    /** 스킬 쪽으로 메뉴 열기 */
    public static void open(ServerPlayer sp) {
        VanillaMenu.open(sp, VanillaMenu.Page.SKILLS);
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        Pick pick = g.skillPick;
        if (pick != null && pick.bar() && SkillBar.get(sp, pick.index()).isEmpty()) pick = null;
        if (pick != null && !pick.bar() && RuneItems.runeOf(sp.getInventory().getItem(pick.index())) == null) pick = null;
        g.skillPick = pick;

        t.text(31, 0, "스킬 세팅", VanillaGui.INK);
        if (pick != null) {
            Runes.Rune r = RuneItems.runeOf(pick.bar() ? SkillBar.get(sp, pick.index()) : sp.getInventory().getItem(pick.index()));
            String what = r == null ? "" : r.def().str("name");
            t.right(168, 0, VanillaGui.fit("▶ " + what + (pick.bar() ? " → 칸 · 빼기" : " → 칸"), 84), VanillaGui.GOLD_INK);
        } else {
            t.right(168, 0, "룬을 눌러 고르기", VanillaGui.SUB); // 제목 "스킬 세팅" (x 31 ~ 77) 과 겹치지 않게 (CI 42387417421)
        }
        t.text(30, 15, "룬 스킬", VanillaGui.TEAL_INK);
        t.text(30 + VanillaGui.width("룬 스킬") + 6, 15, "F 한 번 · 숫자 1 ~ 8", VanillaGui.SUB);
        t.text(30, 51, "직업 스킬", VanillaGui.PURPLE_INK);
        t.text(30 + VanillaGui.width("직업 스킬") + 6, 51, "F 두 번 · 숫자 1 ~ 5", VanillaGui.SUB);

        for (int i = 0; i < SkillBar.SIZE && i < 8; i++) runeCell(g, i);
        for (int i = 0; i < JobSkills.SIZE && i < 5; i++) jobCell(g, i);
        boolean canRemove = pick != null && pick.bar();
        g.set(3, 7, VanillaGui.ui("icon_cross", canRemove ? "§c빼기 §7(가방으로)" : "§8빼기",
                List.of(canRemove ? "§7고른 룬을 가방으로" : "§8룬 칸을 먼저 고르세요")), VanillaMenu.MenuGui.left(() -> {
            Pick p = g.skillPick;
            if (p == null || !p.bar()) return;
            SkillBar.unequip(sp, p.index());
            g.skillPick = null;
            g.refresh();
        }));
        g.set(3, 8, VanillaGui.ui("info", "§b도움말", List.of(
                "§7룬 칸을 누르고 → 다른 룬 칸: §f옮기기",
                "§7가방의 룬을 누르고 → 룬 칸: §f올리기",
                "§7룬 칸을 누르고 → §c빼기§7: §f가방으로",
                "§7같은 것을 다시 누르면 고르기 풂",
                "",
                "§7스킬 쓰기: §fF§7 (룬) · §fF F§7 (직업) → 숫자 키",
                "§7메뉴: §fShift + F")), GuiElement.EMPTY_CALLBACK);
    }

    private static void runeCell(VanillaMenu.MenuGui g, int i) {
        ServerPlayer sp = g.sp;
        ItemStack st = SkillBar.get(sp, i);
        Runes.Rune r = RuneItems.runeOf(st);
        Pick pick = g.skillPick;
        boolean picked = pick != null && pick.bar() && pick.index() == i;
        GuiElement.ClickCallback cb = (ix, type, a, gg) -> clickBar(g, i, type);
        if (r == null) {
            g.set(1, 1 + i, VanillaGui.blank("§8" + (i + 1) + "번 칸 §7(비어 있음)",
                    List.of(pick != null ? "§e누르면 여기로" : "§7가방의 룬을 고른 뒤 누르면 올림")), cb);
            return;
        }
        net.minecraft.resources.Identifier model = st.get(DataComponents.ITEM_MODEL);
        ItemStack icon = VanillaGui.cooldown(VanillaGui.icon(model != null ? model : mmorpg.fabric.Mmorpg.rpg("vui/blank"), "", List.of()),
                VanillaGui.cooldownGroup(st));
        if (picked) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        long left = RuneCast.cooldownLeft(sp, r);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + (i + 1) + "번 칸 §8· §7룬 스킬 모드에서 숫자 " + (i + 1));
        if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
        lore.add(picked ? "§e골랐음 §7— 다른 칸을 누르면 옮김 · 빼기 단추" : pick != null ? "§e누르면 여기로 (서로 바꿈)" : "§a누르면 고름");
        g.set(1, 1 + i, VanillaGui.named(icon, (picked ? "§e▶ " : "") + r.gradeColor() + r.def().str("name"), lore), cb);
    }

    private static void clickBar(VanillaMenu.MenuGui g, int i, ClickType type) {
        if (!type.isLeft) return;
        ServerPlayer sp = g.sp;
        Pick pick = g.skillPick;
        if (pick == null) {
            g.skillPick = SkillBar.get(sp, i).isEmpty() ? null : new Pick(true, i);
        } else if (pick.bar()) {
            if (pick.index() != i) SkillBar.move(sp, pick.index(), i);
            g.skillPick = null;
        } else {
            SkillBar.equip(sp, pick.index(), i);
            g.skillPick = null;
        }
        g.refresh();
    }

    private static void jobCell(VanillaMenu.MenuGui g, int i) {
        ServerPlayer sp = g.sp;
        JobSkills.Skill s = JobSkills.skill(sp, i);
        if (s == null) {
            g.set(3, 1 + i, VanillaGui.blank("§8직업 스킬 " + (i + 1) + " §7(없음)", List.of("§7마을의 직업 교관에게서 직업을 고르면 들어감")), null);
            return;
        }
        String lock = s.lock(sp);
        boolean locked = lock != null && !lock.isEmpty();
        List<String> lore = new ArrayList<>();
        lore.add("§7직업 스킬 모드에서 숫자 " + (i + 1));
        if (locked) lore.add("§c" + JobSkills.lockText(lock));
        long left = JobSkills.cooldownLeft(sp, s);
        if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
        ItemStack icon = VanillaGui.cooldown(VanillaGui.icon(mmorpg.fabric.Mmorpg.rpg("vjob/" + VanillaInput.iconId(s.id()) + (locked ? "_locked" : "")),
                (locked ? "§8" : "§d") + s.name(), lore), VanillaInput.jobGroup(s.id()));
        g.set(3, 1 + i, icon, null);
    }

    /** 가방 칸을 누름 (잠근 가방 — 아이템은 움직이지 않음): 룬이면 고르거나 고른 룬 칸에 올림 */
    static void inventoryClick(VanillaMenu.MenuGui g, int slot, ClickType type) {
        if (!type.isLeft) return;
        ServerPlayer sp = g.sp;
        if (RuneItems.runeOf(sp.getInventory().getItem(slot)) == null) return;
        Pick pick = g.skillPick;
        if (pick != null && pick.bar()) {
            SkillBar.equip(sp, slot, pick.index()); // 고른 룬 칸에 이 룬을 (있던 룬은 이 가방 칸으로)
            g.skillPick = null;
        } else {
            g.skillPick = pick != null && pick.index() == slot ? null : new Pick(false, slot);
        }
        g.refresh();
    }
}
