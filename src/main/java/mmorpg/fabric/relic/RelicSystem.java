package mmorpg.fabric.relic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import mmorpg.core.Js;
import mmorpg.core.Relics;
import mmorpg.core.StatTree;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.RelicItem;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 0.43.0 아티팩트 판 (규칙은 core {@link Relics} — 사용자: "아티팩트는 본인이 어떻게 설계 할지에 따라서 해당 스탯들이 달라지게, 총 10종류, 꽂기만 하면 스탯을
 * 본인이 고를 수 있음, 잘 연결되면 패시브"):
 * <ul>
 * <li>판 9칸을 플레이어에 저장 (칸마다 아이템 그대로 · 고른 스탯 줄 — 빈 칸도 마지막에 고른 줄을 기억) · 죽어도 남음. 꽂으면 가방에서 빠지고 빼면 가방으로.
 * 옮기기는 아이템과 고른 줄이 함께.</li>
 * <li>판 평가 ({@link #board} — 플레이어마다 기억, 판 · 레벨이 바뀌거나 접속하면 다시): 선 · 묶음 · 패시브 단계 · 스탯 줄 합. 합은 스탯 트리 보너스에 더함
 * ({@link StatTrees#extra} — 장신구와 함께, 같은 상한).</li>
 * <li>패시브: {@link RelicPassives} (단계는 {@link #tier}).</li>
 * <li>얻는 법: 몬스터 ({@link #dropForMonster}) · 보스 보상 ({@link #dropForBoss}) — 종류는 열 중 무작위 ({@link #rand} — 시험이 정함).</li>
 * <li>창: 클라이언트의 아티팩트 판 (I 키 창의 아티팩트 탭 · /아티팩트) — 끌어 놓기 · 누르기 · 칸 오른쪽 클릭 · 스탯 줄 고르기 (RelicNet). 들고 우클릭해도 첫 빈 칸에.</li>
 * </ul>
 */
public final class RelicSystem {
    private RelicSystem() {}

    /** 판 칸 수 (3 × 3) */
    public static final int SIZE = Math.max(1, Relics.cells());

    /** 칸 하나: 아이템 (빈 칸 EMPTY) · 고른 스탯 줄 (빈 칸도 기억) */
    public record Cell(ItemStack item, String stat) {
        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(Cell::item),
                Codec.STRING.optionalFieldOf("stat", "atk").forGetter(Cell::stat)).apply(i, Cell::new));

        static Cell empty() {
            return new Cell(ItemStack.EMPTY, Relics.defaultLine());
        }

        Cell copy() {
            return new Cell(item.isEmpty() ? ItemStack.EMPTY : item.copy(), Relics.isLine(stat) ? stat : Relics.defaultLine());
        }
    }

    /** 저장하는 판 (늘 SIZE 칸) */
    public record Data(List<Cell> cells) {
        public static final Codec<Data> CODEC = Cell.CODEC.listOf().xmap(Data::of, Data::cells);

        static Data of(List<Cell> list) {
            List<Cell> out = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) out.add(i < list.size() && list.get(i) != null ? list.get(i).copy() : Cell.empty());
            return new Data(Collections.unmodifiableList(out));
        }

        static Data empty() {
            return of(List.of());
        }
    }

    public static AttachmentType<Data> TYPE;
    /** 드랍 난수 (0 이상 1 미만 — 확률 · 종류. 시험이 정할 수 있게) */
    public static DoubleSupplier rand = Math::random;
    private static final Map<UUID, Relics.Board> BOARDS = new ConcurrentHashMap<>();

    /** Mmorpg.onInitialize (ArtifactSystem.init 다음 — StatTrees.extra 를 이어 붙임) */
    public static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("relics"), builder -> builder.persistent(Data.CODEC).copyOnDeath().initializer(Data::empty));
        RelicNet.register();
        java.util.function.Function<ServerPlayer, Map<String, Double>> prev = StatTrees.extra;
        StatTrees.extra = sp -> {
            Map<String, Double> a = prev.apply(sp);
            Map<String, Double> b = totals(sp);
            if (b.isEmpty()) return a;
            if (a.isEmpty()) return b;
            Map<String, Double> out = new LinkedHashMap<>(a);
            for (Map.Entry<String, Double> e : b.entrySet()) out.merge(e.getKey(), e.getValue(), Double::sum);
            return out;
        };
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            invalidate(handler.player);
            StatTrees.extraChanged(handler.player);
            RelicNet.send(handler.player, false);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            invalidate(newPlayer);
            StatTrees.extraChanged(newPlayer);
            RelicNet.send(newPlayer, false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BOARDS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BOARDS.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        RelicPassives.init();
    }

    // ------------------------------------------------------------ 판

    private static Data data(ServerPlayer sp) {
        return sp.getAttachedOrCreate(TYPE);
    }

    /** 칸들 (늘 SIZE 개 — 고치지 말 것) */
    public static List<Cell> cells(ServerPlayer sp) {
        return data(sp).cells();
    }

    /** 칸 i 의 아이템 (없으면 EMPTY — 고치지 말 것) */
    public static ItemStack get(ServerPlayer sp, int i) {
        return i < 0 || i >= SIZE ? ItemStack.EMPTY : cells(sp).get(i).item();
    }

    /** 칸 i 의 아티팩트 (없으면 null) */
    public static Relics.Relic relic(ServerPlayer sp, int i) {
        return RelicItem.of(get(sp, i));
    }

    /** 칸 i 의 고른 스탯 줄 */
    public static String stat(ServerPlayer sp, int i) {
        return i < 0 || i >= SIZE ? Relics.defaultLine() : cells(sp).get(i).stat();
    }

    /** 칸 i 에 놓음 (복사본 · 검사 없음 — 시험 · 명령어 · 강화 창). stat null 이면 그 칸의 줄 그대로 */
    public static void set(ServerPlayer sp, int i, ItemStack stack, String stat) {
        if (i < 0 || i >= SIZE) return;
        List<Cell> list = new ArrayList<>(cells(sp));
        Cell old = list.get(i);
        list.set(i, new Cell(stack == null ? ItemStack.EMPTY : stack, stat != null && Relics.isLine(stat) ? stat : old.stat()));
        put(sp, list);
        changed(sp, null);
    }

    private static void put(ServerPlayer sp, List<Cell> list) {
        sp.setAttached(TYPE, Data.of(list));
    }

    /** 평가에 넣는 칸들 (아이템 ID · 줄 · 강화) */
    static List<Relics.Cell> evalCells(ServerPlayer sp) {
        List<Relics.Cell> out = new ArrayList<>(SIZE);
        for (Cell c : cells(sp)) {
            Relics.Relic r = RelicItem.of(c.item());
            out.add(r == null ? new Relics.Cell(null, c.stat(), 0) : new Relics.Cell(r.id(), c.stat(), WeaponEnhance.of(c.item())));
        }
        return out;
    }

    /** 판 평가 (기억 — 판 · 레벨이 바뀌면 {@link #invalidate}) */
    public static Relics.Board board(ServerPlayer sp) {
        if (sp == null || TYPE == null) return Relics.evaluate(List.of(), 0);
        Relics.Board b = BOARDS.get(sp.getUUID());
        if (b != null) return b;
        b = Relics.evaluate(evalCells(sp), RpgCombat.reqLevelOf(sp));
        BOARDS.put(sp.getUUID(), b);
        return b;
    }

    /** 이 종류의 패시브 단계 (0 = 꺼짐) */
    public static int tier(ServerPlayer sp, String type) {
        return sp == null ? 0 : board(sp).tier(type);
    }

    /** 스탯 줄 합 + 켜진 패시브의 스탯 몫 (상한 전) */
    public static Map<String, Double> totals(ServerPlayer sp) {
        if (sp == null || TYPE == null) return Map.of();
        return board(sp).totals();
    }

    /** 판 평가를 잊음 (레벨 · 판이 바뀐 뒤) */
    public static void invalidate(ServerPlayer sp) {
        if (sp != null) BOARDS.remove(sp.getUUID());
    }

    /** 판이 바뀐 뒤: 평가 다시 · 이동 속도 · 최대 체력 · 클라이언트에 판 · 스탯, 켜진 패시브가 바뀌었으면 알림 (before = 바뀌기 전 평가 — null 이면 알림 없음) */
    private static void changed(ServerPlayer sp, Relics.Board before) {
        invalidate(sp);
        StatTrees.extraChanged(sp);
        RelicNet.send(sp, false);
        mmorpg.fabric.RpgNet.sync(sp, false);
        if (before != null) announceTiers(sp, before, board(sp));
    }

    /** 켜진 · 오른 · 꺼진 패시브 알림 (오르면 소리 · 빛) */
    private static void announceTiers(ServerPlayer sp, Relics.Board a, Relics.Board b) {
        List<String> up = new ArrayList<>();
        List<String> down = new ArrayList<>();
        int top = 0;
        for (String k : Relics.types()) {
            int x = a.tier(k);
            int y = b.tier(k);
            if (y > x) {
                up.add("§e「" + Relics.type(k).passive().name() + "」 §6" + y + "단계");
                top = Math.max(top, y);
            } else if (y < x) down.add("§7「" + Relics.type(k).passive().name() + "」 " + (y == 0 ? "꺼짐" : y + "단계"));
        }
        if (!up.isEmpty()) {
            Notify.notify(sp, "§6패시브 " + String.join(" §7· ", up), 60);
            SkillFx.playFor(sp, "block.beacon.power_select", 1.2 + top * 0.15, 0.6);
            SkillFx.playFor(sp, "block.amethyst_block.chime", 1.4 + top * 0.1, 0.9);
            ServerLevel level = sp.level();
            SkillFx.sparks(level, V.up(sp.position(), 1.1), GOLD, 8 + top * 4, 3);
        } else if (!down.isEmpty()) {
            Notify.notify(sp, "§7패시브 " + String.join(" §8· ", down), 40);
        }
    }

    private static final double[] GOLD = {1.0, 0.82, 0.3};

    // ------------------------------------------------------------ 꽂기 · 빼기 · 옮기기 · 스탯 줄

    static boolean invSlot(int i) {
        return (i >= 0 && i < Inventory.INVENTORY_SIZE) || i == Inventory.SLOT_OFFHAND;
    }

    /** 첫 빈 칸 (없으면 -1) */
    public static int firstEmpty(ServerPlayer sp) {
        for (int i = 0; i < SIZE; i++) if (get(sp, i).isEmpty()) return i;
        return -1;
    }

    /**
     * 가방 칸 inv 의 아티팩트를 판 칸 cell (-1 = 첫 빈 칸) 에 꽂음 — 그 칸에 있던 것은 가방 칸 inv 로 (서로 바꿈). 고른 줄은 그 칸의 것 그대로. 실패 문구 (되면 null)
     */
    public static String equip(ServerPlayer sp, int inv, int cell) {
        if (!invSlot(inv)) return fail(sp, "§c잘못된 칸입니다");
        Inventory pinv = sp.getInventory();
        ItemStack it = pinv.getItem(inv);
        Relics.Relic a = RelicItem.of(it);
        if (a == null) return fail(sp, "§c아티팩트만 꽂을 수 있습니다");
        int to = cell < 0 ? firstEmpty(sp) : cell;
        if (to < 0) return fail(sp, "§c판이 가득 찼습니다 §7(창에서 바꿀 칸에 끌어 놓으세요)");
        if (to >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        double lv = RpgCombat.reqLevelOf(sp);
        if (!Relics.usable(a, lv)) return fail(sp, "§c" + a.coloredName() + " §c— " + a.level() + "렙부터 꽂을 수 있습니다 §7(지금 " + Js.str(Math.floor(lv)) + ")");
        Relics.Board before = board(sp);
        ItemStack old = get(sp, to).copy();
        ItemStack one = it.copyWithCount(1);
        if (it.getCount() > 1) {
            if (!old.isEmpty() && !addToBag(sp, old)) return fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
            it.shrink(1);
        } else {
            pinv.setItem(inv, old);
        }
        pinv.setChanged();
        List<Cell> list = new ArrayList<>(cells(sp));
        list.set(to, new Cell(one, list.get(to).stat()));
        put(sp, list);
        changed(sp, before);
        equipFx(sp, a);
        String line = Relics.lineText(stat(sp, to), Relics.lineValue(stat(sp, to), a.gradeIndex(), WeaponEnhance.of(one)));
        Notify.notify(sp, a.coloredName(WeaponEnhance.of(one)) + " §7→ §6" + (to + 1) + "번 칸 §a" + line, 50);
        return null;
    }

    /** 칸 cell 의 아티팩트를 가방으로. 빈 칸이면 아무 일 없음. 실패 문구 (되면 null) */
    public static String unequip(ServerPlayer sp, int cell) {
        if (cell < 0 || cell >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        ItemStack old = get(sp, cell);
        if (old.isEmpty()) return null;
        Relics.Relic a = RelicItem.of(old);
        if (!addToBag(sp, old.copy())) return fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
        Relics.Board before = board(sp);
        List<Cell> list = new ArrayList<>(cells(sp));
        list.set(cell, new Cell(ItemStack.EMPTY, list.get(cell).stat()));
        put(sp, list);
        changed(sp, before);
        SkillFx.playFor(sp, "item.armor.equip_generic", 0.8, 0.7);
        if (a != null) Notify.notify(sp, "§7" + (cell + 1) + "번 칸 빼기: " + a.coloredName(WeaponEnhance.of(old)) + " §7→ 가방", 40);
        return null;
    }

    /** 두 칸을 서로 바꿈 (아이템과 고른 줄이 함께 — 한쪽이 비었으면 옮김). 실패 문구 (되면 null) */
    public static String move(ServerPlayer sp, int from, int to) {
        if (from < 0 || from >= SIZE || to < 0 || to >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        if (from == to) return null;
        Relics.Board before = board(sp);
        List<Cell> list = new ArrayList<>(cells(sp));
        Collections.swap(list, from, to);
        put(sp, list);
        changed(sp, before);
        SkillFx.playFor(sp, "item.armor.equip_chain", 1.2, 0.6);
        return null;
    }

    /** 칸 cell 의 스탯 줄을 고름 (빈 칸도 — 다음에 꽂는 아티팩트가 씀). 실패 문구 (되면 null) */
    public static String setStat(ServerPlayer sp, int cell, String stat) {
        if (cell < 0 || cell >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        if (!Relics.isLine(stat)) return fail(sp, "§c고를 수 없는 스탯입니다");
        if (stat.equals(stat(sp, cell))) return null;
        List<Cell> list = new ArrayList<>(cells(sp));
        list.set(cell, new Cell(list.get(cell).item(), stat));
        put(sp, list);
        changed(sp, null);
        Relics.Relic a = relic(sp, cell);
        SkillFx.playFor(sp, "block.amethyst_block.hit", 1.4, 0.7);
        if (a != null) {
            Notify.notify(sp, "§7" + (cell + 1) + "번 칸 " + a.coloredName(WeaponEnhance.of(get(sp, cell))) + " §7→ §a"
                    + Relics.lineText(stat, Relics.lineValue(stat, a.gradeIndex(), WeaponEnhance.of(get(sp, cell)))), 40);
        }
        return null;
    }

    /** 든 아티팩트를 우클릭: 첫 빈 칸에. 실패 문구 (되면 null) */
    public static String equipHeld(ServerPlayer sp, InteractionHand hand) {
        int inv = hand == InteractionHand.MAIN_HAND ? sp.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        if (RelicItem.of(sp.getInventory().getItem(inv)) == null) return null;
        return equip(sp, inv, -1);
    }

    /** 가방의 빈 칸에 넣음 (가방 9 ~ 35 먼저, 그다음 핫바). 넣었으면 true */
    static boolean addToBag(ServerPlayer sp, ItemStack stack) {
        Inventory inv = sp.getInventory();
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? Inventory.SELECTION_SIZE : 0;
            int to = pass == 0 ? Inventory.INVENTORY_SIZE : Inventory.SELECTION_SIZE;
            for (int i = from; i < to; i++) {
                if (!inv.getItem(i).isEmpty()) continue;
                inv.setItem(i, stack);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    private static String fail(ServerPlayer sp, String text) {
        Notify.notify(sp, text, 50);
        SkillFx.playFor(sp, "note.bass", 0.7, 0.8);
        RelicNet.send(sp, false); // 창이 먼저 바꿔 그렸으면 되돌림
        return text;
    }

    /** 꽂기 연출: 계열 색 땅 고리 · 등급 색 불꽃 · 소리 (영웅 · 초월은 더 크게) */
    private static void equipFx(ServerPlayer sp, Relics.Relic a) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        int g = a.gradeIndex();
        double[] grade = mmorpg.fabric.artifact.ArtifactSystem.gradeRgb(g);
        Relics.Type t = a.def();
        for (int i = 0; t != null && i < t.stats().size(); i++) {
            int rgb = Relics.statColor(t.stats().get(i));
            double[] col = {((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0};
            SkillFx.shock(level, c, col, 1.2 + g * 0.2 + i * 0.5, 0.55);
        }
        SkillFx.sparks(level, V.up(c, 1.1), grade, 6 + g * 4, 2 + g * 0.5);
        SkillFx.playFor(sp, g >= 3 ? "item.armor.equip_gold" : "item.armor.equip_chain", 1.0, 0.9);
        SkillFx.play(level, "block.amethyst_block.chime", c, 0.8 + g * 0.12, 0.9);
        if (g >= 4) SkillFx.rune(level, c, Opt.of("size", 1.5, "life", 0.9, "color", GOLD, "alpha", 0.85, "progress", 1.0, "rate", 60.0));
    }

    // ------------------------------------------------------------ 얻는 법

    /** 아티팩트 하나 (등급 · 종류 — 종류가 null 이면 무작위) 를 줌: 가방 (가득 차면 발밑) · 알림 · 빛. 준 아이템 */
    public static ItemStack grant(ServerPlayer sp, String grade, String type, String why) {
        String t = type != null ? type : Relics.typeFor(rand.getAsDouble());
        Relics.Relic r = Relics.relic(t, grade);
        if (r == null) return ItemStack.EMPTY;
        Item item = GearItems.byId(r.id());
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        Inv.give(sp, stack.copy());
        int g = r.gradeIndex();
        Notify.say(sp, "§6아티팩트 " + r.coloredName() + " §7(" + r.gradeName() + (why == null || why.isEmpty() ? "" : " · " + why) + ")");
        ServerLevel level = sp.level();
        double[] col = mmorpg.fabric.artifact.ArtifactSystem.gradeRgb(g);
        SkillFx.fx(level, "rpg:arrow_shot", V.up(sp.position(), 0.1), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.6 + g * 0.3, "thick", 0.12, "life", 0.9, "color", col,
                "alpha", 0.85)); // 빛기둥
        SkillFx.sparks(level, V.up(sp.position(), 1.2), col, 6 + g * 3, 3);
        SkillFx.playFor(sp, g >= 3 ? "random.levelup" : "random.orb", g >= 3 ? 1.4 : 1.2, 0.7);
        if (g >= 3) SkillFx.playFor(sp, "block.beacon.power_select", 1.5, 0.5);
        return stack;
    }

    /** 몬스터를 잡음 (잡은 사람 하나 — 몬스터 레벨 구간의 굴림) */
    public static void dropForMonster(ServerPlayer killer, double monsterLevel, String monsterName) {
        if (killer == null || killer.isRemoved() || !Relics.enabled()) return;
        for (Relics.Roll r : Relics.monsterRolls(monsterLevel)) {
            if (rand.getAsDouble() < r.chance()) grant(killer, r.grade(), null, monsterName);
        }
    }

    /** 보스 보상 (보상을 받는 사람마다 — 확률 1 이상은 반드시). 받은 것 글자 (보상 목록 — 없으면 빈 목록) */
    public static List<String> dropForBoss(ServerPlayer p, String boss) {
        List<String> got = new ArrayList<>();
        if (p == null || p.isRemoved() || !Relics.enabled()) return got;
        for (Relics.Roll r : Relics.bossRolls(boss)) {
            if (r.chance() >= 1 || rand.getAsDouble() < r.chance()) {
                ItemStack s = grant(p, r.grade(), null, null);
                Relics.Relic a = RelicItem.of(s);
                if (a != null) got.add("§6아티팩트 " + a.coloredName() + "§r§f");
            }
        }
        return got;
    }

    // ------------------------------------------------------------ 클라이언트 · 명령어

    /** 클라이언트가 보낸 일 */
    static void action(ServerPlayer sp, String action, int a, int b, String s) {
        if (sp == null || sp.isRemoved()) return;
        switch (action) {
            case "equip" -> equip(sp, a, b);
            case "unequip" -> unequip(sp, a);
            case "move" -> move(sp, a, b);
            case "stat" -> setStat(sp, a, s);
            case "sync" -> RelicNet.send(sp, false);
            default -> {
            }
        }
    }

    /** 판 창 열기 (모드 클라이언트 — 아니면 채팅 요약) */
    public static void open(ServerPlayer sp) {
        if (RelicNet.canSend(sp)) {
            mmorpg.fabric.RpgNet.sync(sp, false);
            RelicNet.send(sp, true);
            return;
        }
        chatSummary(sp);
    }

    /** 채팅 요약: 칸마다 꽂은 것 · 줄, 켜진 패시브, 합계 */
    public static void chatSummary(ServerPlayer sp) {
        Relics.Board b = board(sp);
        Notify.say(sp, "§6§l아티팩트 판 §r§7(꽂은 칸 " + b.filled() + "/" + SIZE + " · 패시브 " + b.active() + ")");
        for (int i = 0; i < SIZE; i++) {
            Relics.Relic a = relic(sp, i);
            int n = WeaponEnhance.of(get(sp, i));
            String line = a == null ? "§8비어 있음 §7(" + Relics.lineName(stat(sp, i)) + ")"
                    : a.coloredName(n) + " §7— " + (b.usable()[i] ? "§a" + Relics.lineText(stat(sp, i), Relics.lineValue(stat(sp, i), a.gradeIndex(), n))
                            : "§c" + a.level() + "렙부터");
            sp.sendSystemMessage(Component.literal(mmorpg.fabric.BedrockText.toJava("§7" + (i + 1) + "번: " + line)));
        }
        for (String k : Relics.types()) {
            int t = b.tier(k);
            if (t > 0) sp.sendSystemMessage(Component.literal(mmorpg.fabric.BedrockText.toJava("§e" + t + "단계 §f" + Relics.passiveText(Relics.type(k), t))));
        }
        String all = StatTree.bonusText(b.totals());
        sp.sendSystemMessage(Component.literal(mmorpg.fabric.BedrockText.toJava("§e합계: " + (all.isEmpty() ? "§8없음" : "§a" + all))));
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("artifact", "아티팩트")) {
            d.register(Commands.literal(name)
                    .executes(c -> {
                        open(c.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("info").executes(c -> {
                        chatSummary(c.getSource().getPlayerOrException());
                        return 1;
                    })));
        }
    }
}
