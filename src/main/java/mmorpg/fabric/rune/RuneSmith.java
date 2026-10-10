package mmorpg.fabric.rune;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 룬술사 (runesmith.js — v2.7.0): 겹치거나 쓰지 않는 룬을 갈아 룬 조각으로 → 룬 조각으로 룬 뽑기권 · 고급 룬 뽑기권을 삼 (RUNE_SMITH).
 * - NPC: 태그 rpg_runesmith (Npcs 대화의 "룬 갈기 · 뽑기권"), 명령어 /runesmith (운영자) · /runeshard [수] (룬 조각 받기 — 운영자, 시험용)
 * - 창 둘 (상자 모양, 윗줄 왼쪽 칸으로 오감)
 *   · 룬 갈기: 가방의 룬 (최대 18개, 겹친 룬 먼저) → 누르면 확인 창 → 갈면 조각. 윗줄 오른쪽 = 겹친 룬 모두 갈기 (룬마다 하나는 남김, 핫바의 것을 남김)
 *   · 뽑기권: 룬 뽑기권 (조각 10) · 고급 룬 뽑기권 (조각 40, 일반 룬 없음) → 1장 · 5장
 * - 연출: 갈면 눈앞에 등급 색 마법진 섬광 + 깨지는 빛 조각 + 숫돌 · 자수정 소리 + "+8 룬 조각", 사면 보랏빛 마법진 + 반짝이
 * 애드온의 창 고리 (for (;;) { await form.show }) 는 받을 일 (콜백) 되부름으로 (shop.Shop 과 같은 방식).
 */
public final class RuneSmith {
    private RuneSmith() {}

    private static final Obj SMITH = Rules.get().obj("RUNE_SMITH");
    private static final Obj RUNE = RuneKit.RUNE;
    private static final List<String> GRADE_ORDER = Runes.GRADE_ORDER;
    private static final Map<String, String> GRADE_NAME = Map.of("common", "일반", "rare", "희귀", "noble", "고귀", "hero", "영웅", "transcend", "초월");

    /** 다른 창으로 (두 창 모두 윗줄 왼쪽) */
    public static final int TAB = 0;
    /** 보유 조각 */
    public static final int SHARD_SLOT = 4;
    /** 겹친 룬 모두 갈기 */
    public static final int DUP_SLOT = 8;
    /** 아래 두 줄: 룬 (최대 18) */
    public static final int LIST_FIRST = 9;
    public static final int LIST_MAX = 18;
    /** 뽑기권 창: 룬 뽑기권 · 정보 · 고급 룬 뽑기권 */
    public static final int NORMAL_SLOT = 11;
    public static final int INFO_SLOT = 13;
    public static final int ADV_SLOT = 15;
    private static final double[] PURPLE = {0.72, 0.42, 1.0};
    private static final double[] GOLD = {1.0, 0.75, 0.3};

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    /** 룬 조각 이름 (RUNE_SMITH.shardName) */
    public static String shardName() {
        return SMITH.str("shardName");
    }

    /** 룬 조각 아이템 (rpg:rune_shard — 설명은 아이템이 그림) */
    public static Item shardItem() {
        return GearItems.byId(SMITH.str("shardItem"));
    }

    /** 룬 조각 amount 개 (한 묶음 — createShardItem) */
    public static ItemStack createShardItem(int amount) {
        Item it = shardItem();
        return it == null ? ItemStack.EMPTY : new ItemStack(it, Math.max(1, Math.min(64, amount)));
    }

    /** 룬 한 개를 갈면 나오는 조각 (grindValue) */
    public static int grindValue(Runes.Rune r) {
        return (int) Runes.grindValue(r);
    }

    // ------------------------------------------------------------ 가방의 룬

    /** 가방 칸의 룬 하나 (slot = 칸 0 ~ 35, dup = 가방에 같은 룬이 몇 개인지) */
    public record RuneSlot(int slot, String id, Runes.Rune r, int dup) {}

    /** 고른 칸 (그 칸에 그 룬이 그대로 있을 때만 갊) */
    public record Pick(int slot, String id) {}

    /** 가방의 룬 (칸마다): 겹친 룬 먼저, 그다음 낮은 등급 · 계열 순서 · 칸 순서 (runeSlots) */
    public static List<RuneSlot> runeSlots(ServerPlayer sp) {
        Map<Integer, Runes.Rune> found = new LinkedHashMap<>();
        Map<String, Integer> n = new LinkedHashMap<>();
        for (int i = 0; i < Inv.SIZE; i++) {
            Runes.Rune r = RuneItems.runeOf(Inv.get(sp, i));
            if (r == null) continue;
            found.put(i, r);
            n.merge(r.id(), 1, Integer::sum);
        }
        List<RuneSlot> out = new ArrayList<>();
        for (Map.Entry<Integer, Runes.Rune> e : found.entrySet()) {
            Runes.Rune r = e.getValue();
            out.add(new RuneSlot(e.getKey(), r.id(), r, n.getOrDefault(r.id(), 1)));
        }
        out.sort(Comparator.<RuneSlot>comparingInt(e -> e.dup() > 1 ? 0 : 1)
                .thenComparingInt(e -> GRADE_ORDER.indexOf(e.r().grade()))
                .thenComparingInt(e -> Runes.FAMILY_ORDER.indexOf(e.r().family()))
                .thenComparingInt(RuneSlot::slot));
        return out;
    }

    /** 겹친 룬 중 갈 것 (룬마다 하나는 남김 — 핫바에 있는 것을 먼저 남김 — duplicateSlots) */
    public static List<RuneSlot> duplicateSlots(ServerPlayer sp) {
        Map<String, List<RuneSlot>> byId = new LinkedHashMap<>();
        for (RuneSlot e : runeSlots(sp)) byId.computeIfAbsent(e.id(), k -> new ArrayList<>()).add(e);
        List<RuneSlot> out = new ArrayList<>();
        for (List<RuneSlot> list : byId.values()) {
            if (list.size() < 2) continue;
            RuneSlot keep = list.stream().min(Comparator.<RuneSlot>comparingInt(e -> e.slot() < 9 ? 0 : 1).thenComparingInt(RuneSlot::slot)).orElseThrow();
            for (RuneSlot e : list) if (e != keep) out.add(e);
        }
        return out;
    }

    private static int sumShards(List<RuneSlot> list) {
        int s = 0;
        for (RuneSlot e : list) s += grindValue(e.r());
        return s;
    }

    // ------------------------------------------------------------ 갈기

    /** 눈앞 연출 자리 (보는 쪽 수평으로 2.2칸, 발에서 1.4칸 위) */
    private static Vec3 faceAt(ServerPlayer sp) {
        Vec3 f = V.flat(sp.getViewVector(1F));
        return V.up(V.add(sp.position(), f, 2.2), 1.4);
    }

    /** 갈기 연출: 눈앞에 등급 색 마법진 섬광 + 깨져 흩어지는 빛 조각 + 숫돌 · 자수정 소리 (grindFx) */
    private static void grindFx(ServerPlayer sp, String grade, int shards) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 at = faceAt(sp);
        double[] col = RuneKit.RUNE_GRADE_FX.getOrDefault(grade, PURPLE);
        int gi = Math.max(0, GRADE_ORDER.indexOf(grade));
        SkillFx.fx(level, "rpg:magic_circle_flash", at, Opt.of("size", 0.6 + gi * 0.08, "life", 0.45, "spin", Kit.rand() * 360, "progress", 1.0, "color", col,
                "alpha", 1.0));
        SkillFx.burst(level, at, col, 0.8 + gi * 0.15, 0.3);
        Vec3 side = V.rightOf(f);
        int n = 8 + gi * 2;
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2;
            Vec3 dir = V.unit(new Vec3(side.x * Math.cos(a), Math.sin(a) + 0.3, side.z * Math.cos(a)));
            SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", dir, "speed", 3 + Kit.rand() * 2, "size", 0.11, "grow", 0.4, "life", 0.45, "color",
                    i % 2 == 1 ? col : PURPLE, "alpha", 1.0));
        }
        SkillFx.play(level, "block.grindstone.use", sp.position(), 1.1, 0.8);
        SkillFx.play(level, "break.amethyst_cluster", sp.position(), 1.0 + gi * 0.1, 0.8);
        DamageNumbers.showFace(sp, "§d§l+" + shards + " " + shardName(), 2.0, 0.5, -0.3);
    }

    /**
     * 룬 갈기: 고른 칸의 룬을 가져가고 조각을 줌 (칸에 그 룬이 그대로 있을 때만). 결과 문구 (창 본문용 — grindRunes)
     */
    public static String grindRunes(ServerPlayer sp, List<Pick> picks) {
        int shards = 0;
        int count = 0;
        String top = "common";
        for (Pick p : picks) {
            if (p.slot() < 0 || p.slot() >= Inv.SIZE) continue;
            ItemStack it = Inv.get(sp, p.slot());
            Runes.Rune r = RuneItems.runeOf(it);
            if (r == null || !r.id().equals(p.id())) continue;
            int v = grindValue(r);
            if (v <= 0) continue;
            int k = Math.max(1, it.getCount()); // 룬은 한 칸에 하나 (명령어로 겹쳐도 칸째로 갊)
            Inv.set(sp, p.slot(), ItemStack.EMPTY);
            shards += v * k;
            count += k;
            if (GRADE_ORDER.indexOf(r.grade()) > GRADE_ORDER.indexOf(top)) top = r.grade();
        }
        if (count == 0) {
            SkillFx.playFor(sp, "note.bass", 0.6, 1);
            return "§4룬이 옮겨져서 취소했습니다. 다시 골라 주세요.§r";
        }
        sp.getInventory().setChanged();
        for (int left = shards; left > 0; left -= 64) Inv.give(sp, createShardItem(Math.min(64, left)));
        grindFx(sp, top, shards);
        Notify.notify(sp, "§d룬 " + count + "개를 갈았습니다 §7→ §d" + shardName() + " +" + shards, 50);
        return "§2룬 " + count + "개 → §5" + shardName() + " +" + shards + "§r";
    }

    // ------------------------------------------------------------ 뽑기권

    /** 뽑기권 정보 (adv = 고급 — ticketInfo): 이름 · 그림 · 값 (조각) · 확률 글자 · 색 */
    public record TicketInfo(boolean adv, String id, String name, String icon, int price, List<String> odds, String color) {}

    public static TicketInfo ticketInfo(boolean adv) {
        Map<String, Double> w = Runes.gradeWeights(adv);
        List<String> odds = new ArrayList<>();
        for (String g : GRADE_ORDER) if (w.get(g) > 0) odds.add(GRADE_NAME.get(g) + " " + Js.str(w.get(g)) + "%");
        return new TicketInfo(adv, RUNE.str(adv ? "advTicketItem" : "ticketItem"), RUNE.str(adv ? "advTicketName" : "ticketName"),
                RUNE.str(adv ? "advTicketIcon" : "ticketIcon"), (int) SMITH.obj("price").num(adv ? "adv" : "ticket"), List.copyOf(odds), adv ? "§6" : "§d");
    }

    /** 뽑기권 사기: 조각을 가져가고 뽑기권 n 장. 결과 문구 (buyTicket) */
    public static String buyTicket(ServerPlayer sp, boolean adv, int n) {
        TicketInfo t = ticketInfo(adv);
        int cost = t.price() * n;
        int have = Inv.count(sp, shardItem());
        if (n <= 0 || have < cost) {
            SkillFx.playFor(sp, "note.bass", 0.6, 1);
            Notify.notify(sp, "§c" + shardName() + " 부족 §7(필요 " + cost + ", 보유 " + have + ")", 40);
            return "§4" + shardName() + " 부족: 필요 " + cost + ", 보유 " + have + "§r";
        }
        ItemStack item = RuneItems.ticket(n, adv);
        if (item.isEmpty()) return "§4뽑기권을 만들 수 없습니다.§r";
        if (!Inv.fitsAfter(sp, List.of(new Inv.Take(shardItem(), cost)), List.of(item))) return Inv.bagFull(sp); // 땅에 떨어지지 않게
        if (!Inv.remove(sp, shardItem(), cost)) return "§4결제 오류§r";
        Inv.give(sp, item);
        ServerLevel level = sp.level();
        Vec3 at = faceAt(sp);
        double[] col = adv ? GOLD : PURPLE;
        SkillFx.fx(level, "rpg:magic_circle_flash", at, Opt.of("size", 0.7, "life", 0.5, "spin", Kit.rand() * 360, "progress", 1.0, "color", col, "alpha", 1.0));
        SkillFx.sparks(level, at, col, 10, 3);
        SkillFx.play(level, "vault.eject_item", sp.position(), 1.2, 0.9);
        SkillFx.play(level, "chime.amethyst_block", sp.position(), adv ? 1.4 : 1.1, 1);
        SkillFx.vanilla(level, "minecraft:totem_particle", V.up(sp.position(), 1.2));
        DamageNumbers.showFace(sp, t.color() + "§l" + t.name() + " ×" + n, 2.0, 0.5, -0.3);
        Notify.notify(sp, t.color() + t.name() + " " + n + "장 §7(" + shardName() + " -" + cost + ")", 50);
        return "§2구매: " + t.name() + " " + n + "장 §8(" + shardName() + " -" + cost + ")§r";
    }

    /** 보유 조각 칸 (shardSlot) */
    private static Slot shardSlot(ServerPlayer sp) {
        int n = Inv.count(sp, shardItem());
        List<String> grind = new ArrayList<>();
        for (String g : GRADE_ORDER) grind.add(Js.str(SMITH.obj("grind").num(g)));
        return Slot.of(SMITH.str("shardIcon"), "§d보유 " + shardName() + " §f" + n).count(n).lore("§7갈기: " + String.join(" · ", grind) + " (일반~초월)");
    }

    private static String runeIcon(Runes.Rune r) {
        return "textures/items/rpg/runes/rune_" + r.family() + "_" + r.grade();
    }

    // ------------------------------------------------------------ 창: 룬 갈기

    private record GrindBuilt(ChestForm form, List<RuneSlot> list, List<RuneSlot> dups) {}

    private static GrindBuilt grindForm(ServerPlayer sp, String message) {
        List<RuneSlot> all = runeSlots(sp);
        List<RuneSlot> list = all.subList(0, Math.min(LIST_MAX, all.size()));
        List<RuneSlot> dups = duplicateSlots(sp);
        int dupShards = sumShards(dups);
        ChestForm form = new ChestForm("룬술사 §8· §7룬 갈기");
        form.set(TAB, Slot.of(RUNE.str("ticketIcon"), "§d뽑기권 사기로").lore("§7" + RUNE.str("ticketName") + " · " + RUNE.str("advTicketName")));
        form.set(SHARD_SLOT, shardSlot(sp));
        form.set(DUP_SLOT, !dups.isEmpty()
                ? Slot.of(Icon.sprite("icon_check"), "§a겹친 룬 모두 갈기 §7(" + dups.size() + "개 · +" + dupShards + ")").price(dupShards)
                        .lore("§7같은 룬은 하나씩 남김", "§7(핫바의 것을 남김, 확인 창)")
                : Slot.of(Icon.sprite("icon_check"), "§8겹친 룬 없음").deco());
        for (int i = 0; i < list.size(); i++) {
            RuneSlot e = list.get(i);
            int v = grindValue(e.r());
            form.set(LIST_FIRST + i, Slot.of(runeIcon(e.r()), e.r().gradeColor() + e.r().def().str("name") + "§r§7 · " + e.r().famName() + " 룬").price(v)
                    .lore("§r§7" + e.r().gradeName() + " · 갈면 §d+" + v + " " + shardName(), e.dup() > 1 ? "§r§e같은 룬 " + e.dup() + "개" : "§r§8하나뿐인 룬",
                            e.slot() < 9 ? "§r§8핫바 " + (e.slot() + 1) + "번 칸" : "§r§8가방"));
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(!list.isEmpty() ? "§8룬 칸을 누르면 확인 창 · 초록 숫자 = 받는 룬 조각§r" : "§8갈 룬이 없습니다. 룬을 가방에 넣고 오세요.§r");
        if (all.size() > LIST_MAX) lines.add("§8룬은 " + LIST_MAX + "개까지 보입니다 (겹친 룬 먼저).§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return new GrindBuilt(form, List.copyOf(list), dups);
    }

    /** 룬 하나 갈기 확인 (confirmGrind) — 결과 문구를 done 으로 ("" = 취소) */
    private static void confirmGrind(ServerPlayer sp, RuneSlot e, Consumer<String> done) {
        int v = grindValue(e.r());
        ChestForm form = new ChestForm("룬 갈기");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a갈기 §7(+" + v + " " + shardName() + ")").price(v));
        form.set(13, Slot.of(runeIcon(e.r()), e.r().gradeColor() + e.r().def().str("name")).lore("§r§7" + e.r().famName() + " 룬 · " + e.r().gradeName()).deco());
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§0" + e.r().def().str("name") + "§8 (" + e.r().famName() + " 룬 · " + e.r().gradeName() + ") → " + shardName() + " §0" + v + "§8개§r");
        if (e.dup() <= 1) lines.add("§4하나뿐인 룬입니다. 갈면 되돌릴 수 없습니다.§r");
        lines.add("§8왼쪽 초록 칸: 갈기 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> done.accept(sel == 11 ? grindRunes(sp, List.of(new Pick(e.slot(), e.id()))) : ""));
    }

    /** 겹친 룬 모두 갈기 확인 (confirmDups) */
    private static void confirmDups(ServerPlayer sp, List<RuneSlot> dups, Consumer<String> done) {
        int total = sumShards(dups);
        ChestForm form = new ChestForm("겹친 룬 모두 갈기");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a모두 갈기 §7(" + dups.size() + "개 · +" + total + ")").price(total));
        form.set(13, Slot.of(SMITH.str("shardIcon"), "§d" + shardName() + " +" + total).count(total).deco());
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> names = new ArrayList<>(new LinkedHashSet<>(dups.stream().map(e -> e.r().def().str("name")).toList()));
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < names.size() && rows.size() < 3; i += 3) rows.add("§0" + String.join(" · ", names.subList(i, Math.min(names.size(), i + 3))) + "§r");
        List<String> lines = new ArrayList<>();
        lines.add("§8같은 룬은 하나씩 남기고 §0" + dups.size() + "개§8를 갑니다 → §5+" + total + "§r");
        lines.addAll(rows);
        lines.add("§8왼쪽 초록 칸: 갈기 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel != 11) {
                done.accept("");
                return;
            }
            List<Pick> picks = new ArrayList<>();
            for (RuneSlot e : dups) picks.add(new Pick(e.slot(), e.id()));
            done.accept(grindRunes(sp, picks));
        });
    }

    /** 룬 갈기 창 반복 (grindLoop). next: "tickets" = 뽑기권 창으로, null = 닫음 */
    public static void grindLoop(ServerPlayer sp, Consumer<String> next) {
        grindStep(sp, "", 0, next);
    }

    private static void grindStep(ServerPlayer sp, String message, int guard, Consumer<String> next) {
        if (guard >= 100) {
            next.accept(null);
            return;
        }
        GrindBuilt b = grindForm(sp, message);
        b.form().show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == TAB) next.accept("tickets");
            else if (sel == DUP_SLOT) {
                if (b.dups().isEmpty()) grindStep(sp, "", guard + 1, next);
                else confirmDups(sp, b.dups(), msg -> grindStep(sp, msg, guard + 1, next));
            } else {
                int i = sel - LIST_FIRST;
                RuneSlot e = sel >= LIST_FIRST && i < b.list().size() ? b.list().get(i) : null;
                if (e == null) grindStep(sp, "", guard + 1, next);
                else confirmGrind(sp, e, msg -> grindStep(sp, msg, guard + 1, next));
            }
        });
    }

    // ------------------------------------------------------------ 창: 뽑기권

    private static ChestForm ticketForm(ServerPlayer sp, String message) {
        ChestForm form = new ChestForm("룬술사 §8· §7뽑기권");
        form.set(TAB, Slot.of("textures/items/rpg/runes/rune_int_common", "§d룬 갈기로").lore("§7겹치거나 쓰지 않는 룬 → 룬 조각"));
        form.set(SHARD_SLOT, shardSlot(sp));
        for (boolean adv : new boolean[] {false, true}) {
            TicketInfo t = ticketInfo(adv);
            List<String> lore = new ArrayList<>();
            lore.add("§r§7" + String.join(" · ", t.odds().subList(0, Math.min(3, t.odds().size()))));
            lore.add("§r§7" + String.join(" · ", t.odds().subList(Math.min(3, t.odds().size()), t.odds().size())));
            lore.add("§r§7누르면 1장 · 5장");
            lore.removeIf(l -> l.equals("§r§7"));
            form.set(adv ? ADV_SLOT : NORMAL_SLOT, Slot.of(t.icon(), t.color() + t.name() + " §7· " + shardName() + " " + t.price()).price(t.price()).lore(lore));
        }
        Obj grind = SMITH.obj("grind");
        form.set(INFO_SLOT, Slot.of(SMITH.str("shardIcon"), "§d" + shardName()).lore("§7룬을 갈면 나옴",
                "§7일반 " + Js.str(grind.num("common")) + " · 희귀 " + Js.str(grind.num("rare")) + " · 고귀 " + Js.str(grind.num("noble")),
                "§7영웅 " + Js.str(grind.num("hero")) + " · 초월 " + Js.str(grind.num("transcend"))).deco());
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§8왼쪽: §0" + RUNE.str("ticketName") + "§8 · 오른쪽: §0" + RUNE.str("advTicketName") + "§8 (일반 룬 없음)§r");
        lines.add("§8초록 숫자 = 필요한 룬 조각 · 누르면 확인 창§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 뽑기권 사기 확인: 1장 · (모자라지 않으면) 5장 (confirmBuy) */
    private static void confirmBuy(ServerPlayer sp, boolean adv, Consumer<String> done) {
        TicketInfo t = ticketInfo(adv);
        int have = Inv.count(sp, shardItem());
        ChestForm form = new ChestForm(t.name() + " 사기");
        form.set(11, Slot.of(t.icon(), "§a1장 사기 §7(" + shardName() + " " + t.price() + ")").price(t.price()));
        if (have >= t.price() * 5) form.set(12, Slot.of(t.icon(), "§a5장 사기 §7(" + shardName() + " " + t.price() * 5 + ")").price(t.price() * 5));
        form.set(13, Slot.of(SMITH.str("shardIcon"), "§d보유 " + shardName() + " " + have).count(have).deco());
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§0" + t.name() + "§8 1장 = " + shardName() + " §0" + t.price() + "§8 · 보유 §0" + have + "§r");
        lines.add("§8" + String.join(" · ", t.odds()) + "§r");
        if (have < t.price()) lines.add("§4" + shardName() + "이 " + (t.price() - have) + "개 모자랍니다.§r");
        lines.add("§8초록 숫자 칸: 그만큼 사기 · 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            int n = sel == 11 ? 1 : sel == 12 && have >= t.price() * 5 ? 5 : 0;
            done.accept(n > 0 ? buyTicket(sp, adv, n) : "");
        });
    }

    /** 뽑기권 창 반복 (ticketLoop). next: "grind" = 룬 갈기 창으로, null = 닫음 */
    public static void ticketLoop(ServerPlayer sp, Consumer<String> next) {
        ticketStep(sp, "", 0, next);
    }

    private static void ticketStep(ServerPlayer sp, String message, int guard, Consumer<String> next) {
        if (guard >= 100) {
            next.accept(null);
            return;
        }
        ticketForm(sp, message).show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == TAB) next.accept("grind");
            else if (sel == NORMAL_SLOT || sel == ADV_SLOT) confirmBuy(sp, sel == ADV_SLOT, msg -> ticketStep(sp, msg, guard + 1, next));
            else ticketStep(sp, "", guard + 1, next);
        });
    }

    // ------------------------------------------------------------ 흐름

    /** 룬술사 창 (룬 갈기 ↔ 뽑기권 — 흐름 안에서, 예: NPC 대화). start: "grind" · "tickets", 끝나면 done (null 이면 없음) */
    public static void runesmithLoop(ServerPlayer sp, String start, Runnable done) {
        go(sp, start == null ? "grind" : start, 0, done);
    }

    private static void go(ServerPlayer sp, String next, int i, Runnable done) {
        if (next == null || i >= 20) {
            if (done != null) done.run();
            return;
        }
        if (next.equals("tickets")) ticketLoop(sp, n -> go(sp, n, i + 1, done));
        else grindLoop(sp, n -> go(sp, n, i + 1, done));
    }

    /** 룬술사 창 열기 (명령어용 — 창이 열려 있으면 무시, openRunesmith) */
    public static void openRunesmith(ServerPlayer sp, String start) {
        if (Forms.isOpen(sp)) return;
        runesmithLoop(sp, start, null);
    }

    // ------------------------------------------------------------ 명령어 (commands.js — 운영자)

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("runesmith").requires(RuneSmith::admin).executes(c -> {
            openRunesmith(c.getSource().getPlayerOrException(), "grind");
            return 1;
        }));
        d.register(Commands.literal("runeshard").requires(RuneSmith::admin)
                .executes(c -> shards(c, 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 640)).executes(c -> shards(c, IntegerArgumentType.getInteger(c, "amount")))));
    }

    private static int shards(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        for (int left = amount; left > 0; left -= 64) Inv.give(sp, createShardItem(Math.min(64, left)));
        Notify.say(sp, shardName() + " " + amount + "개 지급");
        return amount;
    }
}
