package mmorpg.fabric.smith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mmorpg.core.Artifacts;
import mmorpg.core.Enhance;
import mmorpg.core.Grade;
import mmorpg.core.Relics;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.gear.ArtifactItem;
import mmorpg.fabric.gear.RelicItem;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.relic.RelicSystem;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 0.43.0 장신구 · 아티팩트 강화 (대장장이 강화 창의 탭 — {@link EnhanceWindow} 윗줄 둘째 칸): +1 ~ +10.
 * <ul>
 *   <li>성공 확률 · 강화석 · 빛나는 강화석 · 등급 배수 · +6 부터 실패하면 한 단계 하락 · 수호 주문서 · +7 이상 성공 서버 알림은 무기와 같음
 *   (Enhance.gearCost — 재료 × ENHANCE_GEAR.costScale)</li>
 *   <li>효과: 장신구 옵션 · 아티팩트 스탯 줄 × (1 + ENHANCE_GEAR.bonus[n]) (+10 = × 1.5). 아티팩트 패시브는 그대로</li>
 *   <li>고르는 곳: 판의 아티팩트 · 낀 장신구 (둘 다 빼지 않고 그 자리에서) · 가방의 장신구 · 아티팩트</li>
 *   <li>강화 단계는 무기와 같은 아이템 구성 요소 (WeaponEnhance — 이름 "+N" · 설명은 아이템 쪽이 그림)</li>
 * </ul>
 * 창 (큰 상자 9 × 6): 윗줄 [레벨 올리기로] · [무기 강화로] · 강화석 · 빛나는 강화석 · 안내 · 수호 주문서 · [보스 무기 제작으로],
 * 둘째 줄 판 아홉 칸, 셋째 줄 낀 장신구 다섯 칸, 아래 세 줄 가방의 것 (최대 27개). 성공 판정의 난수는 {@link #rand} (무기 강화 시험과 섞이지 않게 따로).
 */
public final class GearEnhance {
    private GearEnhance() {}

    static final int TAB_SLOT = 0;
    static final int WEAPON_TAB_SLOT = 1;
    static final int STONE_SLOT = 2;
    static final int BRIGHT_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int SCROLL_SLOT = 6;
    static final int CRAFT_TAB_SLOT = 8;
    static final int ROWS = 6;
    static final int FIRST = 9;

    /** 자리 번호: 가방 0 ~ 35 · 왼손 40, 낀 장신구 ACC + 칸, 판 BOARD + 칸 */
    static final int ACC = 1000;
    static final int BOARD = 2000;

    private static final int MAX = EnhanceWindow.MAX;

    /** 강화 성공 판정의 난수 (0 이상 1 미만 — 이 값이 성공 확률보다 작으면 성공. 시험이 정할 수 있게 — 무기 강화와 따로) */
    public static java.util.function.DoubleSupplier rand = Math::random;

    /** 강화할 것 하나: 자리 · 아이템 (그 때의 복사본) · 장신구 또는 아티팩트 (하나만) · 강화 단계 · 판 칸이면 고른 스탯 줄 (아니면 null) */
    public record Entry(int loc, ItemStack item, Artifacts.Art art, Relics.Relic relic, int n, String stat) {
        String grade() {
            return art != null ? art.grade() : relic.grade();
        }

        int gradeIndex() {
            return art != null ? art.gradeIndex() : relic.gradeIndex();
        }

        String gradeColor() {
            return art != null ? art.gradeColor() : relic.gradeColor();
        }

        String gradeName() {
            return art != null ? art.gradeName() : relic.gradeName();
        }

        /** 이름 (색 없음) */
        String title() {
            return art != null ? art.name() : relic.name();
        }

        /** 등급 색 + (강화 k &gt; 0 이면 "+k ") + 이름 */
        String name(int k) {
            return gradeColor() + (k > 0 ? "+" + k + " " : "") + title();
        }

        /** 밝은 바탕 (창 본문) 용 */
        String darkName(int k) {
            if (relic != null) return relic.darkName(k);
            Grade g = Rules.get().grade(art.grade());
            return (g == null ? "§0" : g.dark()) + (k > 0 ? "+" + k + " " : "") + art.name();
        }

        /** 어디에 있는지 ("판 칸 3" · "낀 장신구 · 반지 1" · "가방") */
        String where() {
            if (loc >= BOARD) return "판 칸 " + (loc - BOARD + 1);
            if (loc >= ACC) return "낀 장신구 · " + Artifacts.slotName(loc - ACC);
            return "가방";
        }

        /** 효과 한 줄 (단계 k): 장신구 = 옵션, 판의 아티팩트 = 고른 스탯 줄, 가방의 아티팩트 = 스탯 줄 배율 */
        String effect(int k) {
            if (art != null) return Artifacts.optionText(art, k);
            if (stat != null) return Relics.lineText(stat, Relics.lineValue(stat, relic.gradeIndex(), k));
            return "스탯 줄 +" + EnhanceWindow.pct(Enhance.gearBonus(k));
        }
    }

    /** 무기 강화 창의 탭 그림 (초월 드라우프니르 — 없으면 금 조각) */
    static Item icon() {
        Item it = Icon.itemById(Relics.id("draupnir", "transcend"));
        if (it == null) it = Icon.itemById("rpg:artifact_ring_transcend");
        return it != null ? it : Icon.itemById("minecraft:gold_nugget");
    }

    // ------------------------------------------------------------ 자리

    /** 자리의 아이템 (고치지 말 것 — 강화는 복사본에 적고 {@link #put}) */
    static ItemStack at(ServerPlayer sp, int loc) {
        if (loc >= BOARD) return RelicSystem.get(sp, loc - BOARD);
        if (loc >= ACC) return ArtifactSystem.get(sp, loc - ACC);
        return Inv.get(sp, loc);
    }

    /** 자리에 놓음 (판 · 장신구 칸이면 보너스 다시 · 클라이언트에 보냄 — 판의 고른 스탯 줄은 그대로) */
    static void put(ServerPlayer sp, int loc, ItemStack stack) {
        if (loc >= BOARD) RelicSystem.set(sp, loc - BOARD, stack, null);
        else if (loc >= ACC) ArtifactSystem.set(sp, loc - ACC, stack);
        else Inv.set(sp, loc, stack);
    }

    /** 자리의 것이 강화할 것이면 Entry (아니면 null) */
    static Entry entryAt(ServerPlayer sp, int loc) {
        ItemStack s = at(sp, loc);
        Artifacts.Art a = ArtifactItem.of(s);
        Relics.Relic r = a == null ? RelicItem.of(s) : null;
        if (a == null && r == null) return null;
        String stat = r != null && loc >= BOARD ? RelicSystem.stat(sp, loc - BOARD) : null;
        return new Entry(loc, s.copy(), a, r, WeaponEnhance.of(s), stat);
    }

    /** 그 자리의 것이 고른 것 그대로인지 (같은 아이템 · 같은 강화 단계) */
    private static boolean same(ItemStack now, Entry e) {
        if (now == null || now.isEmpty() || WeaponEnhance.of(now) != e.n()) return false;
        if (e.art() != null) {
            Artifacts.Art a = ArtifactItem.of(now);
            return a != null && a.id().equals(e.art().id());
        }
        Relics.Relic r = RelicItem.of(now);
        return r != null && r.id().equals(e.relic().id());
    }

    /** 창의 칸 배치: 칸 번호 → 강화할 것 (없으면 null) · 판 줄 · 장신구 줄 · 가방 줄의 첫 칸 (없는 줄은 -1) · 가방에서 못 보인 수 */
    record Layout(Entry[] at, int boardFirst, int accFirst, int bagFirst, int hidden) {}

    /** 창의 칸 배치 (판 → 낀 장신구 → 가방 (핫바부터 · 왼손)) */
    static Layout layout(ServerPlayer sp) {
        int size = ROWS * 9;
        Entry[] at = new Entry[size];
        int row = FIRST;
        int boardFirst = -1;
        if (Relics.enabled() && RelicSystem.TYPE != null && row + RelicSystem.SIZE <= size - 9) {
            boardFirst = row;
            for (int i = 0; i < RelicSystem.SIZE; i++) at[row + i] = entryAt(sp, BOARD + i);
            row += (RelicSystem.SIZE + 8) / 9 * 9;
        }
        int accFirst = -1;
        if (Artifacts.enabled() && ArtifactSystem.TYPE != null && row + ArtifactSystem.SIZE <= size - 9) {
            accFirst = row;
            for (int i = 0; i < ArtifactSystem.SIZE; i++) at[row + i] = entryAt(sp, ACC + i);
            row += (ArtifactSystem.SIZE + 8) / 9 * 9;
        }
        int bagFirst = row;
        int k = 0;
        int hidden = 0;
        for (int i = 0; i <= Inventory.SLOT_OFFHAND; i++) {
            if (i >= Inv.SIZE && i != Inventory.SLOT_OFFHAND) continue;
            Entry e = entryAt(sp, i);
            if (e == null) continue;
            if (bagFirst + k < size) at[bagFirst + k++] = e;
            else hidden++;
        }
        return new Layout(at, boardFirst, accFirst, bagFirst, hidden);
    }

    // ------------------------------------------------------------ 창

    /** 칸 설명 */
    private static List<String> slotLore(Entry e) {
        List<String> out = new ArrayList<>();
        String head = e.where();
        if (e.stat() != null) head += " §8· §7스탯 줄 §f" + Relics.lineName(e.stat());
        else if (e.relic() != null) head += " §8· §7아티팩트 (스탯 줄은 판에서)";
        out.add("§r§7" + head);
        if (e.n() >= MAX) {
            out.add("§r§6최대 강화 +" + MAX);
            out.add("§r§a" + e.effect(e.n()));
            return out;
        }
        Enhance.Cost c = Enhance.gearCost(e.n(), e.grade());
        if (c == null) return out;
        out.add("§r§7강화 §f+" + e.n() + " → +" + (e.n() + 1) + "§7 · 성공 §a" + EnhanceWindow.pct(c.rate()));
        out.add("§r§a" + e.effect(e.n()));
        out.add("§r§7→ §f" + e.effect(e.n() + 1));
        out.add("§r§7재료: " + EnhanceWindow.costText(c, false));
        out.add("§r" + (c.down() ? "§c" : "§8") + EnhanceWindow.failText(c));
        return out;
    }

    private static ChestForm buildForm(ServerPlayer sp, Layout lay, String message) {
        int st = Inv.count(sp, EnhanceWindow.itemOf(EnhanceWindow.STONE));
        int br = Inv.count(sp, EnhanceWindow.itemOf(EnhanceWindow.BRIGHT));
        int sc = Inv.count(sp, EnhanceWindow.itemOf(EnhanceWindow.SCROLL));
        ChestForm form = new ChestForm("대장장이 §8· §7장신구 · 아티팩트 강화", ROWS);
        form.set(TAB_SLOT, Slot.of("textures/items/iron_ingot", "§6레벨 올리기로").lore("§7영웅 · 초월 무기 레벨 제한 올리기 창"));
        form.set(WEAPON_TAB_SLOT, Slot.of("textures/items/iron_sword", "§6무기 강화로").lore("§7가진 RPG 무기 강화 창"));
        form.set(STONE_SLOT, Slot.of(EnhanceWindow.STONE.str("icon"), EnhanceWindow.STONE.str("color") + "보유 " + EnhanceWindow.STONE.str("name") + " §f" + st).count(st));
        form.set(BRIGHT_SLOT, Slot.of(EnhanceWindow.BRIGHT.str("icon"), EnhanceWindow.BRIGHT.str("color") + "보유 " + EnhanceWindow.BRIGHT.str("name") + " §f" + br).count(br));
        form.set(INFO_SLOT, Slot.of(Icon.item(icon()), "§d장신구 · 아티팩트 강화 §f(+1 ~ +" + MAX + ")")
                .lore("§7장신구 옵션 · 아티팩트 스탯 줄이 오릅니다", "§7+" + MAX + "이면 × " + mmorpg.core.Js.str(1 + Enhance.gearBonus(MAX)) + " (아티팩트 패시브는 그대로)",
                        "§7+" + EnhanceWindow.DOWN_FROM + "부터 실패하면 한 단계 하락", "§7" + EnhanceWindow.SCROLL.str("name") + "가 있으면 하락 방지",
                        "§7+" + EnhanceWindow.ANNOUNCE_FROM + " 이상 성공은 서버 전체 알림")
                .deco());
        form.set(SCROLL_SLOT, Slot.of(EnhanceWindow.SCROLL.str("icon"), EnhanceWindow.SCROLL.str("color") + "보유 " + EnhanceWindow.SCROLL.str("name") + " §f" + sc).count(sc));
        form.set(CRAFT_TAB_SLOT, Slot.of("textures/items/iron_sword", "§6보스 무기 제작으로").lore("§7보스 재료로 영웅·초월 무기 만들기", "§7공허 전리품으로 공허의 갑옷 만들기",
                "§7전리품 · 보스 재료로 장신구 만들기"));
        int count = 0;
        for (int i = FIRST; i < lay.at().length; i++) {
            Entry e = lay.at()[i];
            if (e != null) {
                form.set(i, Slot.of(e.item(), "§r" + e.name(e.n())).lore(slotLore(e)));
                count++;
            }
        }
        // 빈 판 칸 · 빈 장신구 칸 (어두운 칸 — 누를 수 없음)
        if (lay.boardFirst() >= 0) {
            for (int i = 0; i < RelicSystem.SIZE; i++) {
                if (form.get(lay.boardFirst() + i) == null) form.set(lay.boardFirst() + i, Slot.of(Icon.sprite("pane_off"), "§8판 칸 " + (i + 1) + " · 비어 있음").deco());
            }
        }
        if (lay.accFirst() >= 0) {
            for (int i = 0; i < ArtifactSystem.SIZE; i++) {
                if (form.get(lay.accFirst() + i) == null) {
                    form.set(lay.accFirst() + i, Slot.of(Icon.sprite("pane_off"), "§8" + Artifacts.slotName(i) + " · 비어 있음").deco());
                }
            }
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        if (lay.boardFirst() >= 0) rows.add("판 " + RelicSystem.SIZE + "칸");
        if (lay.accFirst() >= 0) rows.add("낀 장신구");
        rows.add("가방");
        lines.add("§8줄 순서: " + String.join(" → ", rows) + " §0(빼지 않고 그 자리에서 강화)§r");
        if (count == 0) lines.add("§8강화할 장신구 · 아티팩트가 없습니다.§r");
        else lines.add("§8칸을 누르면 확인 창이 열립니다.§r");
        lines.add("§8성공 확률 · 재료 · 하락은 무기와 같음 · 효과 +" + MAX + " §0× " + mmorpg.core.Js.str(1 + Enhance.gearBonus(MAX)) + "§r");
        if (lay.hidden() > 0) lines.add("§8가방에 " + lay.hidden() + "개 더 있음 (낀 것 · 앞의 것부터 보임)§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 확인 창. 누른 것을 pick 으로: "go" · "guard" (수호 주문서 쓰고) · "" (취소) */
    private static void confirm(ServerPlayer sp, Entry e, Consumer<String> pick) {
        Enhance.Cost c = Enhance.gearCost(e.n(), e.grade());
        if (c == null) {
            pick.accept("");
            return;
        }
        int st = Inv.count(sp, EnhanceWindow.itemOf(EnhanceWindow.STONE));
        int br = Inv.count(sp, EnhanceWindow.itemOf(EnhanceWindow.BRIGHT));
        int sc = Inv.count(sp, EnhanceWindow.itemOf(EnhanceWindow.SCROLL));
        boolean scroll = c.down() && sc > 0;
        String kind = e.art() != null ? "장신구" : "아티팩트";
        ChestForm form = new ChestForm(kind + " 강화 §8· §7+" + e.n() + " → +" + (e.n() + 1));
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a강화하기 §7(성공 " + EnhanceWindow.pct(c.rate()) + ")").lore("§r§7" + EnhanceWindow.failText(c)));
        if (scroll) {
            form.set(12, Slot.of(EnhanceWindow.SCROLL.str("icon"), "§6" + EnhanceWindow.SCROLL.str("name") + " 쓰고 강화").count(sc)
                    .lore("§r§7실패해도 단계가 떨어지지 않음", "§r§7(실패했을 때만 사라짐)"));
        }
        double lv = RpgCombat.reqLevelOf(sp);
        List<String> lore = new ArrayList<>(e.art() != null ? Artifacts.lore(e.art(), e.n(), lv) : Relics.lore(e.relic(), e.n(), lv));
        lore.add("§r§7" + e.where());
        form.set(13, Slot.of(e.item(), "§r" + e.name(e.n())).lore(lore));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> lines = List.of(
                e.darkName(e.n()) + " §8→ §0§l+" + (e.n() + 1) + "§r §8(" + e.where() + ")§r",
                "§8성공 확률 §2§l" + EnhanceWindow.pct(c.rate()) + "§r§8 · 효과 +" + EnhanceWindow.pct(Enhance.gearBonus(e.n())) + " → §0+"
                        + EnhanceWindow.pct(Enhance.gearBonus(e.n() + 1)) + "§r",
                "§8" + e.effect(e.n()) + " → §0" + e.effect(e.n() + 1) + "§r",
                "§8재료 " + EnhanceWindow.costText(c, true) + " §8(보유 §0" + st + "§8" + (c.bright() > 0 ? " / §0" + br : "") + "§8)§r",
                (c.down() ? "§4" : "§8") + EnhanceWindow.failText(c) + "§r",
                "§8초록 칸: 강화" + (scroll ? " · 주문서 칸: 하락 방지" : "") + " · 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> pick.accept(sel == 11 ? "go" : sel == 12 && scroll ? "guard" : ""));
    }

    // ------------------------------------------------------------ 강화

    /**
     * 강화 한 번 (확인 창에서 누른 뒤). 결과 문구 (창 본문용) 를 done 으로. 무기와 같은 흐름: 재료 확인 → 망치질 연출 (창 없이 — Forms.busy) →
     * 그 자리의 것 · 재료가 그대로면 그 틱에 재료를 받고 판정 (옮겼으면 아무것도 쓰지 않고 취소). 도중에 나가거나 죽으면 done 을 부르지 않음.
     */
    public static void enhanceOnce(ServerPlayer sp, Entry e, boolean useScroll, Consumer<String> done) {
        if (!same(at(sp, e.loc()), e)) {
            done.accept("§4옮겨져서 취소했습니다. 다시 골라 주세요.§r");
            return;
        }
        Enhance.Cost c = Enhance.gearCost(e.n(), e.grade());
        if (c == null) {
            done.accept("§8이미 최대 강화 +" + MAX + "입니다.§r");
            return;
        }
        boolean guard = useScroll && c.down();
        String short0 = EnhanceWindow.shortage(sp, c, guard);
        if (short0 != null) {
            done.accept(short0);
            return;
        }
        EnhanceWindow.hammerFx(sp, e.gradeIndex());
        Forms.busy(sp, EnhanceWindow.ANIM_TICKS + 2);
        Tasks.later(EnhanceWindow.ANIM_TICKS, () -> {
            if (sp.isRemoved() || sp.isDeadOrDying()) return;
            done.accept(judge(sp, e, c, guard));
        });
    }

    /** 망치질이 끝난 순간: 그 자리의 것 · 재료를 다시 보고 재료를 받고 판정 → 복사본에 단계를 적어 그 자리에 다시 놓음. 결과 문구 */
    static String judge(ServerPlayer sp, Entry e, Enhance.Cost c, boolean guard) {
        ItemStack cur = at(sp, e.loc());
        if (!same(cur, e)) return "§4강화 중에 옮겨서 취소했습니다. 재료는 쓰지 않았습니다.§r";
        String short1 = EnhanceWindow.shortage(sp, c, guard);
        if (short1 != null) return short1;
        if (!Inv.remove(sp, EnhanceWindow.itemOf(EnhanceWindow.STONE), (int) c.stone())
                || (c.bright() > 0 && !Inv.remove(sp, EnhanceWindow.itemOf(EnhanceWindow.BRIGHT), (int) c.bright()))) {
            return "§4재료를 쓰지 못했습니다§r";
        }
        boolean ok = rand.getAsDouble() < c.rate();
        int n = e.n();
        if (ok) n = e.n() + 1;
        else if (c.down() && !guard) n = e.n() - 1;
        if (!ok && guard && !Inv.remove(sp, EnhanceWindow.itemOf(EnhanceWindow.SCROLL), 1)) n = e.n() - 1; // (위에서 확인했으므로 없는 일)
        ItemStack now = cur.copy(); // (판 · 장신구 칸의 것은 고치지 않음 — 복사본에 적어 다시 놓음)
        WeaponEnhance.write(now, n);
        put(sp, e.loc(), now);
        String name = e.gradeColor() + e.title();
        String bonus = "효과 +" + EnhanceWindow.pct(Enhance.gearBonus(n));
        if (ok) {
            EnhanceWindow.successFx(sp, n);
            Notify.title(sp, "§6§l+" + n + " 강화 성공!", "§r" + name + "§r §7" + bonus);
            if (n >= EnhanceWindow.ANNOUNCE_FROM) EnhanceWindow.announce(sp, e.gradeColor(), e.gradeName(), e.title(), n);
            return "§2§l성공! §r" + e.darkName(n) + "§8 (" + e.effect(n) + ")" + (guard ? " · 주문서는 쓰지 않았습니다" : "") + "§r";
        }
        EnhanceWindow.failFx(sp, n < e.n(), guard);
        if (n < e.n()) {
            Notify.title(sp, "§c강화 실패", "§c+" + e.n() + " → +" + n);
            return "§4실패: 한 단계 떨어졌습니다 (+" + e.n() + " → +" + n + ")§r";
        }
        if (guard) {
            Notify.title(sp, "§e강화 실패", "§e" + EnhanceWindow.SCROLL.str("name") + "가 지켜 주었습니다 (+" + n + ")");
            return "§6실패: " + EnhanceWindow.SCROLL.str("name") + "가 지켜 주어 +" + n + " 그대로입니다§r";
        }
        Notify.title(sp, "§7강화 실패", "§7+" + n + " 그대로");
        return "§8실패: +" + n + " 그대로입니다§r";
    }

    /** 장신구 · 아티팩트 강화 창 반복. next: "smith" = 레벨 올리기 창으로, "craft" = 보스 무기 제작 창으로, null = 닫음 (무기 강화 탭은 이 안에서 오감) */
    static void step(ServerPlayer sp, String message, int guard, Consumer<String> next) {
        if (guard >= 200) {
            next.accept(null);
            return;
        }
        Layout lay = layout(sp);
        buildForm(sp, lay, message).show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == TAB_SLOT) next.accept("smith");
            else if (sel == CRAFT_TAB_SLOT) next.accept("craft");
            else if (sel == WEAPON_TAB_SLOT) EnhanceWindow.step(sp, "", guard + 1, next);
            else {
                Entry e = sel >= FIRST && sel < lay.at().length ? lay.at()[sel] : null;
                if (e == null) step(sp, "", guard + 1, next);
                else if (e.n() >= MAX) step(sp, "§8이미 최대 강화 +" + MAX + "입니다.§r", guard + 1, next);
                else confirm(sp, e, pick -> {
                    if (pick.isEmpty()) step(sp, "", guard + 1, next);
                    else enhanceOnce(sp, e, pick.equals("guard"), msg -> step(sp, msg, guard + 1, next));
                });
            }
        });
    }

    /** 시험 · 명령어: 장신구 · 아티팩트 강화 창을 바로 엶 */
    public static void open(ServerPlayer sp, Consumer<String> next) {
        step(sp, "", 0, next);
    }
}
