package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.SimpleGuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import mmorpg.core.Runes;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.SkillBar;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 4b: 모드 없는 클라이언트의 스킬 칸 관리 창 (모드는 SkillBarScreen — J). 6줄 상자 메뉴 (sgui) + 그림 글꼴 바탕 (tools/vanilla_gui.py skills.png),
 * 아래는 진짜 가방 (룬을 누르면 고름 — 가방 아이템은 움직이지 않음):
 * <pre>
 * 스킬 칸                            (고른 것 안내)
 * 룬 스킬  F 한 번 · 숫자 1 ~ 8
 * [룬 1] … [룬 8] [빼기]
 * 직업 스킬  F 두 번 · 숫자 1 ~ 5
 * [직업 1] … [직업 5]          [스탯 창]
 * 도움말 두 줄
 * [가방 — 진짜 칸]
 * </pre>
 * 고르고 놓기: 룬 칸 · 가방의 룬을 누르면 고름, 다음에 누른 칸으로 — 룬 칸 → 룬 칸 옮기기 (서로 바꿈), 가방 → 룬 칸 올리기 (있던 룬은 그 가방 칸으로),
 * 고른 룬 칸 + 빼기 = 가방으로. 같은 것을 다시 누르면 풂. 서버 동작은 SkillBar.equip · unequip · move (실패 문구는 그쪽이 알림).
 */
public final class VanillaSkillBar {
    private VanillaSkillBar() {}

    /** 고른 것: 룬 칸 (bar = true, 칸 번호) 또는 가방 칸 (bar = false, 가방 칸 번호) */
    private record Pick(boolean bar, int index) {}

    public static void open(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return;
        SkillsGui gui = new SkillsGui(sp);
        gui.refresh();
        gui.open();
    }

    private static final class SkillsGui extends SimpleGui {
        private final ServerPlayer sp;
        private Pick pick;

        SkillsGui(ServerPlayer sp) {
            super(MenuType.GENERIC_9x6, sp, false);
            this.sp = sp;
            setLockPlayerInventory(true);
        }

        void refresh() {
            for (int i = 0; i < 54; i++) clearSlot(i);
            if (pick != null && pick.bar() && SkillBar.get(sp, pick.index()).isEmpty()) pick = null;
            if (pick != null && !pick.bar() && RuneItems.runeOf(sp.getInventory().getItem(pick.index())) == null) pick = null;
            VanillaGui.Title t = new VanillaGui.Title().background(VanillaGui.BG_SKILLS);
            t.text(10, 0, "스킬 칸", VanillaGui.AQUA);
            if (pick != null) {
                Runes.Rune r = RuneItems.runeOf(pick.bar() ? SkillBar.get(sp, pick.index()) : sp.getInventory().getItem(pick.index()));
                String what = r == null ? "" : r.def().str("name");
                t.right(168, 0, VanillaGui.fit("▶ " + what + (pick.bar() ? " — 옮길 칸 · 빼기" : " — 올릴 칸"), 110), VanillaGui.GOLD);
            }
            t.text(10, VanillaGui.rowLine(0), "룬 스킬", VanillaGui.AQUA);
            t.text(10 + VanillaGui.width("룬 스킬") + 6, VanillaGui.rowLine(0), "F 한 번 · 숫자 1 ~ 8", VanillaGui.GRAY);
            t.text(10, VanillaGui.rowLine(2), "직업 스킬", VanillaGui.PINK);
            t.text(10 + VanillaGui.width("직업 스킬") + 6, VanillaGui.rowLine(2), "F 두 번 · 숫자 1 ~ 5", VanillaGui.GRAY);
            t.text(10, VanillaGui.rowLine(4), "룬 칸 → 다른 칸: 옮기기", VanillaGui.GRAY);
            t.text(10, VanillaGui.rowLine(5), "가방의 룬 → 룬 칸: 올리기", VanillaGui.GRAY);
            t.right(168, 124, "가방의 룬을 눌러 고르기", VanillaGui.GOLD);

            for (int i = 0; i < SkillBar.SIZE; i++) runeCell(i);
            boolean canRemove = pick != null && pick.bar();
            setSlot(VanillaStats.idx(1, 8), new SimpleGuiElement(VanillaGui.ui("icon_cross", canRemove ? "§c빼기 §7(가방으로)" : "§8빼기",
                    List.of(canRemove ? "§7고른 룬을 가방으로" : "§8룬 칸을 먼저 고르세요")), (ix, type, a, g) -> {
                if (!type.isLeft || pick == null || !pick.bar()) return;
                SkillBar.unequip(sp, pick.index());
                pick = null;
                refresh();
            }));
            for (int i = 0; i < JobSkills.SIZE; i++) jobCell(i);
            setSlot(VanillaStats.idx(3, 8), new SimpleGuiElement(VanillaGui.ui("icon_prev", "§7스탯 창으로", List.of()), (ix, type, a, g) -> {
                if (type.isLeft) VanillaStats.open(sp);
            }));
            setTitle(t.build());
        }

        private void runeCell(int i) {
            ItemStack st = SkillBar.get(sp, i);
            Runes.Rune r = RuneItems.runeOf(st);
            boolean picked = pick != null && pick.bar() && pick.index() == i;
            GuiElement.ClickCallback cb = (ix, type, a, g) -> clickBar(i, type);
            if (r == null) {
                setSlot(VanillaStats.idx(1, i), new SimpleGuiElement(VanillaGui.blank("§8" + (i + 1) + "번 칸 §7(비어 있음)",
                        List.of(pick != null ? "§e누르면 여기로" : "§7가방의 룬을 고른 뒤 누르면 올림")), cb));
                return;
            }
            ItemStack icon = st.copy();
            if (picked) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            long left = RuneCast.cooldownLeft(sp, r);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + (i + 1) + "번 칸 §8· §7룬 스킬 모드에서 숫자 " + (i + 1));
            if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
            lore.add(picked ? "§e골랐음 §7— 다른 칸을 누르면 옮김 · 빼기 단추" : pick != null ? "§e누르면 여기로 (서로 바꿈)" : "§a누르면 고름");
            setSlot(VanillaStats.idx(1, i), new SimpleGuiElement(VanillaGui.named(icon, (picked ? "§e▶ " : "") + r.gradeColor() + r.def().str("name"), lore), cb));
        }

        private void clickBar(int i, ClickType type) {
            if (!type.isLeft) return;
            if (pick == null) {
                pick = SkillBar.get(sp, i).isEmpty() ? null : new Pick(true, i);
            } else if (pick.bar()) {
                if (pick.index() != i) SkillBar.move(sp, pick.index(), i);
                pick = null;
            } else {
                SkillBar.equip(sp, pick.index(), i);
                pick = null;
            }
            refresh();
        }

        private void jobCell(int i) {
            JobSkills.Skill s = JobSkills.skill(sp, i);
            if (s == null) {
                setSlot(VanillaStats.idx(3, i), new SimpleGuiElement(VanillaGui.blank("§8직업 스킬 " + (i + 1) + " §7(없음)",
                        List.of("§7마을의 직업 교관에게서 직업을 고르면 들어감")), GuiElement.EMPTY_CALLBACK));
                return;
            }
            String lock = s.lock(sp);
            boolean locked = lock != null && !lock.isEmpty();
            List<String> lore = new ArrayList<>();
            lore.add("§7직업 스킬 모드에서 숫자 " + (i + 1));
            if (locked) lore.add("§c" + JobSkills.lockText(lock));
            long left = JobSkills.cooldownLeft(sp, s);
            if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
            ItemStack icon = VanillaGui.icon(mmorpg.fabric.Mmorpg.rpg("vjob/" + VanillaInput.iconId(s.id()) + (locked ? "_locked" : "")),
                    (locked ? "§8" : "§d") + s.name(), lore);
            setSlot(VanillaStats.idx(3, i), new SimpleGuiElement(icon, GuiElement.EMPTY_CALLBACK));
        }

        /** 가방 칸을 누름 (잠근 가방 — 아이템은 움직이지 않음): 룬이면 고르거나 고른 룬 칸에 올림 */
        @Override
        public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
            if (index < 54) return true;
            if (!type.isLeft || index >= 90) return false;
            int slot = index < 81 ? index - 54 + 9 : index - 81;
            if (RuneItems.runeOf(sp.getInventory().getItem(slot)) == null) return false;
            if (pick != null && pick.bar()) {
                SkillBar.equip(sp, slot, pick.index()); // 고른 룬 칸에 이 룬을 (있던 룬은 이 가방 칸으로)
                pick = null;
            } else {
                pick = pick != null && pick.index() == slot ? null : new Pick(false, slot);
            }
            refresh();
            return false;
        }
    }
}
