package mmorpg.fabric.rune;

import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.core.StatTree;
import mmorpg.fabric.Notify;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * 2026-11-17 룬 재개발 (계열 없이 등급만 — rules_runes.json RUNE_SET): 접속 때 옛 룬 아이템을 바꿈 (컨셉 문서 「뽑기 · 아이템」).
 * <ul>
 *   <li>옛 계열 아홉 (RUNE_SET.migrateFamilies — 힘 · 민첩 · 체력 · 마력 · 도끼 · 궁수 · 암살자 · 검사 · 정령사) 의 룬: 이어 온 스물둘은 새 룬으로
 *   (RUNE_SET.migrate — 등급이 바뀐 것은 새 등급, 돌파 단계 그대로), 나머지는 룬 조각 (옛 등급 · 돌파의 갈기 값)</li>
 *   <li>스탯 룬 뽑기권 (계열을 고르던 것) → 같은 수의 룬 뽑기권</li>
 * </ul>
 * 가방 · 엔더 상자 · 스킬 칸 · 엔더 창고 (StorageData). 2026-11-14 숨긴 열넷 (성기사 · 번개 …) 은 그대로 둠 (컨셉: 퀄을 올린 뒤 다시).
 */
public final class RuneMigrate {
    private RuneMigrate() {}

    private static final Obj SET = Rules.get().root().has("RUNE_SET") ? Rules.get().obj("RUNE_SET") : null;
    private static final List<String> FAMILIES = SET == null || !SET.has("migrateFamilies") ? List.of() : stringsOf(SET.list("migrateFamilies"));

    private static List<String> stringsOf(List<Object> l) {
        return l.stream().map(String::valueOf).toList();
    }

    /** 바꾼 수 (룬 · 조각 · 뽑기권) */
    private static final class Count {
        int runes;
        int shards;
        int tickets;

        boolean any() {
            return runes + shards + tickets > 0;
        }
    }

    /** 접속 (RpgPlayers.onJoin — 엔더 상자 → 창고 다음) */
    public static void onJoin(ServerPlayer sp) {
        if (SET == null) return;
        Count c = new Count();
        convert(sp, sp.getInventory(), c);
        convert(sp, sp.getEnderChestInventory(), c);
        for (int i = 0; i < SkillBar.SIZE; i++) {
            ItemStack s = SkillBar.get(sp, i);
            ItemStack n = convert(sp, s, c);
            if (n != s) SkillBar.set(sp, i, n);
        }
        mmorpg.fabric.storage.StorageData d = mmorpg.fabric.storage.StorageData.of(sp);
        for (int p = 0; p < d.unlocked(); p++) {
            NonNullList<ItemStack> page = d.page(p);
            for (int i = 0; i < page.size(); i++) {
                ItemStack s = page.get(i);
                ItemStack n = convert(sp, s, c);
                if (n != s) page.set(i, n);
            }
        }
        if (c.any()) {
            sp.getInventory().setChanged();
            StringBuilder b = new StringBuilder("§d룬이 새로 바뀌었습니다 §7— 계열 없이 등급만 (등급마다 6개)");
            if (c.runes > 0) b.append(" §8· §f새 룬 ").append(c.runes).append("개");
            if (c.shards > 0) b.append(" §8· §f").append(RuneSmith.shardName()).append(" ").append(c.shards).append("개");
            if (c.tickets > 0) b.append(" §8· §f룬 뽑기권 ").append(c.tickets).append("장");
            String msg = b.toString();
            mmorpg.fabric.Tasks.later(60, () -> {
                if (!sp.isRemoved()) Notify.say(sp, msg);
            });
        }
    }

    private static void convert(ServerPlayer sp, Container box, Count c) {
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.getItem(i);
            ItemStack n = convert(sp, s, c);
            if (n != s) box.setItem(i, n);
        }
    }

    /** 한 칸: 바꿀 것이 없으면 같은 객체 */
    static ItemStack convert(ServerPlayer sp, ItemStack s, Count c) {
        if (s == null || s.isEmpty()) return s;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        for (StatTree.Ticket t : StatTree.tickets().values()) {
            if (t.item().equals(id) && !Runes.FAMILY_ORDER.contains(t.family())) {
                c.tickets += s.getCount();
                return RuneItems.ticket(s.getCount(), false);
            }
        }
        Runes.Rune r = RuneItems.runeOf(s);
        if (r == null || !FAMILIES.contains(r.family())) return s;
        int lv = Breakthrough.of(s);
        String to = SET.obj("migrate").strOrNull(r.family() + "." + r.grade());
        if (to != null) {
            int dot = to.indexOf('.');
            ItemStack n = Breakthrough.stack(to.substring(0, dot), to.substring(dot + 1), lv);
            if (!n.isEmpty()) {
                c.runes++;
                return n;
            }
        }
        int shards = Math.max(1, RuneSmith.grindValue(r, lv)) * Math.max(1, s.getCount());
        c.shards += shards;
        ItemStack first = RuneSmith.createShardItem(shards);
        for (int left = shards - first.getCount(); left > 0; left -= 64) Notify.give(sp, RuneSmith.createShardItem(Math.min(64, left)));
        return first;
    }

    /** 시험 · 명령어: 옛 계열.등급 → 새 룬 (없으면 null — 조각) */
    public static String target(String family, String grade) {
        return SET == null || !SET.has("migrate") ? null : SET.obj("migrate").strOrNull(family + "." + grade);
    }

    /** 바꾸는 옛 계열 (아홉) */
    public static List<String> families() {
        return FAMILIES;
    }

    static Map<String, Object> debug() {
        return Map.of("families", FAMILIES);
    }
}
