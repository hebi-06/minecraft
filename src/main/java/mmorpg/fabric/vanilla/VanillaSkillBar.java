package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import mmorpg.core.Runes;
import mmorpg.core.StatTree;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.job.CommonSkills;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.SkillKeys;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.SkillBar;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.stattree.StatTrees;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 메뉴의 스킬 장착 쪽 (VanillaMenu — 위 탭 "스킬"). 3판 (2026-11-18 축복의 길 — 스킬 키 여섯 SkillKeys):
 * <pre>
 * [1][2][3][4][5][6][7][8]  [빼기 ↓]          줄 1 룬 칸 (아래 숫자는 바탕)
 *  1  2  3  4  5  6  7  8
 * [우][⇧우][⇧좌][⇧⇧][Q][⇧Q]       [도움말]    줄 3 스킬 키 여섯 (아래 키 이름 — Shift 와 함께는 금빛)
 *  우 ⇧우 ⇧좌 ⇧⇧  Q  ⇧Q
 * ─────────────────────────────
 * [직1][직2][직3][직4][직5][공][공][공][공]    줄 5 가진 스킬 (직업 스킬 다섯 + 스탯 트리에서 연 공용 스킬)
 * </pre>
 * 룬: 룬 칸 · 가방의 룬을 누르면 고름, 다음에 누른 칸으로 — 룬 칸 → 룬 칸 옮기기 (서로 바꿈), 가방 → 룬 칸 올리기 (있던 룬은 그 가방 칸으로),
 * 고른 룬 칸 + 빼기 = 가방으로. 같은 것을 다시 누르면 풂. 서버 동작은 SkillBar.equip · unequip · move (실패 문구는 그쪽이 알림).
 * <p>스킬 키 (컨셉 문서 "끼우는 창"): 가진 스킬을 누르고 → 키 칸을 누르면 끼움 (한 스킬은 한 키에만 — 다른 키에 있던 것은 옮겨 옴, 공용 스킬은 하나만:
 * 다른 공용 스킬을 끼우면 바뀜). 키 칸을 누르면 그 스킬을 고름 (다른 키 칸을 누르면 옮김) · 고른 키 칸을 다시 누르면 뺌 · 우클릭 = 바로 뺌.
 */
public final class VanillaSkillBar {
    private VanillaSkillBar() {}

    /** 줄: 룬 칸 · 스킬 키 · 가진 스킬 (tools/vanilla_gui.py menu_skills 와 같음) */
    static final int RUNE_ROW = 1, KEY_ROW = 3, OWN_ROW = 5;

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
        if (g.keyPick != null && !owned(sp).contains(g.keyPick)) g.keyPick = null;

        // 글자 없음 (사용자 "글자를 최대한 줄여서" — 고른 것은 칸이 빛남, 쓰는 법은 도움말 단추 · 칸의 설명 상자)
        if (pick != null && !pick.bar()) g.markBag(pick.index());
        for (int i = 0; i < SkillBar.SIZE && i < 8; i++) runeCell(g, i);
        boolean canRemove = pick != null && pick.bar();
        g.set(RUNE_ROW, 8, VanillaGui.blank(canRemove ? "§c빼기 §7(가방으로)" : "§8빼기",
                List.of(canRemove ? "§7고른 룬을 가방으로" : "§8룬 칸을 먼저 고르세요")), VanillaMenu.MenuGui.left(() -> {
            Pick p = g.skillPick;
            if (p == null || !p.bar()) return;
            SkillBar.unequip(sp, p.index());
            g.skillPick = null;
            g.refresh();
        }));
        SkillKeys.Key[] keys = SkillKeys.Key.values();
        for (int i = 0; i < keys.length; i++) keyCell(g, i, keys[i]);
        List<String> own = owned(sp);
        for (int i = 0; i < own.size() && i < 9; i++) ownCell(g, i, own.get(i));
        g.set(KEY_ROW, 8, VanillaGui.blank("§b도움말", List.of(
                "§f룬§7: 룬 칸을 누르고 → 다른 룬 칸 §f옮기기",
                "§7가방의 룬을 누르고 → 룬 칸 §f올리기 §8· §7룬 칸 + §c↓ §f가방으로",
                "",
                "§f스킬 키§7: 아래 가진 스킬을 누르고 → 키 칸 §f끼우기",
                "§7키 칸을 누르고 → 다른 키 칸 §f옮기기 §8· §7다시 누르거나 우클릭 §f빼기",
                "§7공용 스킬은 하나만 (다른 것을 끼우면 바뀜)",
                "",
                "§7쓰기: §f우 · ⇧우 · ⇧좌 · ⇧⇧ · Q · ⇧Q §8(⇧ = Shift, 금빛)",
                "§7룬: §fF§7 → 숫자 1 ~ 8 §8· §7메뉴: §fShift + F")), null);
    }

    // ------------------------------------------------------------ 룬

    private static void runeCell(VanillaMenu.MenuGui g, int i) {
        ServerPlayer sp = g.sp;
        ItemStack st = SkillBar.get(sp, i);
        Runes.Rune r = RuneItems.runeOf(st);
        Pick pick = g.skillPick;
        boolean picked = pick != null && pick.bar() && pick.index() == i;
        GuiElement.ClickCallback cb = (ix, type, a, gg) -> clickBar(g, i, type);
        if (r == null) {
            g.set(RUNE_ROW, i, VanillaGui.blank("§8" + (i + 1) + "번 칸 §7(비어 있음)",
                    List.of(pick != null ? "§e누르면 여기로" : "§7가방의 룬을 고른 뒤 누르면 올림")), cb);
            return;
        }
        net.minecraft.resources.Identifier model = st.get(DataComponents.ITEM_MODEL);
        ItemStack icon = VanillaGui.cooldown(VanillaGui.icon(model != null ? model : Mmorpg.rpg("vui/blank"), "", List.of()),
                VanillaGui.cooldownGroup(st));
        if (picked) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        long left = RuneCast.cooldownLeft(sp, r);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + (i + 1) + "번 칸 §8· §7룬 모드 (F) 에서 숫자 " + (i + 1));
        if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
        lore.add(picked ? "§e골랐음 §7— 다른 칸을 누르면 옮김 · 빼기 단추" : pick != null ? "§e누르면 여기로 (서로 바꿈)" : "§a누르면 고름");
        g.set(RUNE_ROW, i, VanillaGui.named(icon, (picked ? "§e▶ " : "") + r.gradeColor() + r.def().str("name"), lore), cb);
    }

    private static void clickBar(VanillaMenu.MenuGui g, int i, ClickType type) {
        if (!type.isLeft) return;
        ServerPlayer sp = g.sp;
        Pick pick = g.skillPick;
        g.keyPick = null;
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

    /** 가방 칸을 누름 (잠근 가방 — 아이템은 움직이지 않음): 룬이면 고르거나 고른 룬 칸에 올림 */
    static void inventoryClick(VanillaMenu.MenuGui g, int slot, ClickType type) {
        if (!type.isLeft) return;
        ServerPlayer sp = g.sp;
        if (RuneItems.runeOf(sp.getInventory().getItem(slot)) == null) return;
        Pick pick = g.skillPick;
        g.keyPick = null;
        if (pick != null && pick.bar()) {
            SkillBar.equip(sp, slot, pick.index()); // 고른 룬 칸에 이 룬을 (있던 룬은 이 가방 칸으로)
            g.skillPick = null;
        } else {
            g.skillPick = pick != null && pick.index() == slot ? null : new Pick(false, slot);
        }
        g.refresh();
    }

    // ------------------------------------------------------------ 스킬 키

    /** 가진 스킬 (끼울 수 있는 것): 직업 스킬 칸 "j1" ~ "j5" (칸에 스킬이 있으면) + 연 공용 스킬 "c:&lt;id&gt;" */
    static List<String> owned(ServerPlayer sp) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < JobSkills.SIZE; i++) if (JobSkills.skill(sp, i) != null) out.add("j" + (i + 1));
        for (String id : StatTrees.openSkills(sp)) out.add("c:" + id);
        return out;
    }

    /** 고른 것 (가진 스킬 "j1" · "c:&lt;id&gt;") → 키에 끼우는 값 */
    private static String bindValue(String pick) {
        return pick.startsWith("c:") ? SkillKeys.COMMON : pick;
    }

    /** 키 칸 하나 (줄 3 · 칸 i) */
    private static void keyCell(VanillaMenu.MenuGui g, int i, SkillKeys.Key k) {
        ServerPlayer sp = g.sp;
        String what = SkillKeys.bound(sp, k);
        String pick = g.keyPick;
        boolean picked = pick != null && !what.isEmpty() && keyPickOf(sp, what).equals(pick) && g.keyPickFromKey;
        GuiElement.ClickCallback cb = (ix, type, a, gg) -> clickKey(g, k, type);
        String head = "§e" + k.label + " §8(" + k.shortLabel + ")";
        ItemStack icon = skillIcon(sp, what.isEmpty() ? null : keyPickOf(sp, what));
        if (icon == null) {
            g.set(KEY_ROW, i, VanillaGui.blank(head + " §7— 비어 있음",
                    List.of(pick != null ? "§e누르면 여기에 끼움" : "§7아래 가진 스킬을 누른 뒤 여기를 누르면 끼움")), cb);
            return;
        }
        List<String> lore = new ArrayList<>(skillLore(sp, keyPickOf(sp, what)));
        lore.add(0, head);
        lore.add(picked ? "§e골랐음 §7— 다른 키 칸을 누르면 옮김 · 다시 누르면 §c빼기" : pick != null ? "§e누르면 여기에 끼움 (있던 것은 뺌)" : "§a누르면 고름 §8· §7우클릭 §c빼기");
        if (picked) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        g.set(KEY_ROW, i, VanillaGui.named(icon, (picked ? "§e▶ " : "") + SkillKeys.name(sp, what), lore), cb);
    }

    /** 키에 끼운 값 → 가진 스킬 이름 (공용은 지금 끼운 공용 스킬 "c:&lt;id&gt;") */
    private static String keyPickOf(ServerPlayer sp, String what) {
        if (what.equals(SkillKeys.COMMON)) {
            String id = CommonSkills.equipped(sp);
            return id == null ? "c:" : "c:" + id;
        }
        return what;
    }

    private static void clickKey(VanillaMenu.MenuGui g, SkillKeys.Key k, ClickType type) {
        ServerPlayer sp = g.sp;
        g.skillPick = null;
        String what = SkillKeys.bound(sp, k);
        String pick = g.keyPick;
        if (!type.isLeft) { // 우클릭: 빼기
            if (!what.isEmpty()) {
                SkillKeys.bind(sp, k, "");
                SkillFx.playFor(sp, "ui.button.click", 0.8, 0.35);
            }
            g.keyPick = null;
            g.refresh();
            return;
        }
        if (pick == null) {
            if (!what.isEmpty()) {
                g.keyPick = keyPickOf(sp, what);
                g.keyPickFromKey = true;
            }
        } else if (g.keyPickFromKey && !what.isEmpty() && keyPickOf(sp, what).equals(pick)) {
            SkillKeys.bind(sp, k, ""); // 고른 키 칸을 다시 누름: 빼기
            g.keyPick = null;
            SkillFx.playFor(sp, "ui.button.click", 0.8, 0.35);
        } else {
            bind(g, k, pick);
            g.keyPick = null;
        }
        g.refresh();
    }

    /** 끼우기 (공용 스킬은 그것을 끼운 공용 스킬로 정하고 공용 자리를 이 키로) */
    private static void bind(VanillaMenu.MenuGui g, SkillKeys.Key k, String pick) {
        ServerPlayer sp = g.sp;
        if (pick.startsWith("c:")) {
            String err = CommonSkills.equip(sp, pick.substring(2));
            if (err != null) {
                mmorpg.fabric.Notify.warn(sp, err);
                return;
            }
        }
        String err = SkillKeys.bind(sp, k, bindValue(pick));
        if (err != null) mmorpg.fabric.Notify.warn(sp, err);
        else SkillFx.playFor(sp, "block.amethyst_block.chime", 1.4, 0.5);
    }

    /** 가진 스킬 칸 하나 (줄 5 · 칸 i) */
    private static void ownCell(VanillaMenu.MenuGui g, int i, String own) {
        ServerPlayer sp = g.sp;
        ItemStack icon = skillIcon(sp, own);
        if (icon == null) return;
        boolean picked = own.equals(g.keyPick) && !g.keyPickFromKey;
        SkillKeys.Key at = keyOfOwned(sp, own);
        List<String> lore = new ArrayList<>(skillLore(sp, own));
        lore.add(at != null ? "§7끼운 키: §e" + at.label : "§8키에 없음");
        lore.add(picked ? "§e골랐음 §7— 위 키 칸을 누르면 끼움" : "§a누르면 고름 §7(그다음 위 키 칸)");
        if (picked) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        g.set(OWN_ROW, i, VanillaGui.named(icon, (picked ? "§e▶ " : "") + ownName(sp, own), lore), (ix, type, a, gg) -> {
            if (!type.isLeft) return;
            g.skillPick = null;
            g.keyPick = picked ? null : own;
            g.keyPickFromKey = false;
            g.refresh();
        });
    }

    /** 가진 스킬이 끼워진 키 (공용은 그 공용 스킬이 끼운 것일 때만) */
    private static SkillKeys.Key keyOfOwned(ServerPlayer sp, String own) {
        if (own.startsWith("c:")) {
            return own.substring(2).equals(CommonSkills.equipped(sp)) ? SkillKeys.keyOf(sp, SkillKeys.COMMON) : null;
        }
        return SkillKeys.keyOf(sp, own);
    }

    private static String ownName(ServerPlayer sp, String own) {
        if (own.startsWith("c:")) {
            StatTree.Skill k = StatTree.skill(own.substring(2));
            return k == null ? "§8?" : "§b" + k.name();
        }
        JobSkills.Skill s = JobSkills.skill(sp, own.charAt(1) - '1');
        return s == null ? "§8?" : "§d" + s.name();
    }

    /** 그림 (직업 스킬 rpg:vjob/&lt;id&gt; · 공용 스킬 rpg:vjob/common_&lt;id&gt; — 잠기면 어두운 그림) · 쿨타임 묶음. 없으면 null */
    private static ItemStack skillIcon(ServerPlayer sp, String own) {
        if (own == null) return null;
        if (own.startsWith("c:")) {
            String id = own.substring(2);
            if (id.isEmpty() || StatTree.skill(id) == null) return null;
            return VanillaGui.cooldown(VanillaGui.icon(Mmorpg.rpg("vjob/" + VanillaHud.commonIcon(id)), "", List.of()), VanillaInput.commonGroup(id));
        }
        JobSkills.Skill s = JobSkills.skill(sp, own.charAt(1) - '1');
        if (s == null) return null;
        String lock = s.lock(sp);
        boolean locked = lock != null && !lock.isEmpty();
        return VanillaGui.cooldown(VanillaGui.icon(Mmorpg.rpg("vjob/" + VanillaInput.iconId(s.id()) + (locked ? "_locked" : "")), "", List.of()),
                VanillaInput.jobGroup(s.id()));
    }

    /** 설명 줄: 종류 · 잠김 · 재사용 대기 */
    private static List<String> skillLore(ServerPlayer sp, String own) {
        List<String> lore = new ArrayList<>();
        if (own.startsWith("c:")) {
            StatTree.Skill k = StatTree.skill(own.substring(2));
            if (k == null) return lore;
            lore.add("§b공용 스킬 §7(" + ("move".equals(k.kind()) ? "이동기" : "작은 공격") + " · 재사용 " + (int) k.cd() + "초)");
            lore.add("§f" + k.text());
            if (own.substring(2).equals(CommonSkills.equipped(sp))) {
                long left = CommonSkills.cooldownLeft(sp);
                if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
            }
            return lore;
        }
        int i = own.charAt(1) - '1';
        JobSkills.Skill s = JobSkills.skill(sp, i);
        if (s == null) return lore;
        lore.add("§d직업 스킬 " + (i + 1) + "번");
        String lock = s.lock(sp);
        if (lock != null && !lock.isEmpty()) lore.add("§c" + JobSkills.lockText(lock));
        long left = JobSkills.cooldownLeft(sp, s);
        if (left > 0) lore.add("§c재사용 대기 " + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초");
        return lore;
    }
}
