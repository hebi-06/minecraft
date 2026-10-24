package mmorpg.fabric.artifact;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mmorpg.core.Artifacts;
import mmorpg.core.Js;
import mmorpg.core.StatTree;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.gear.ArtifactItem;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 0.37.0 아티팩트 (사용자: "아티팩트 추가 — 반지1 반지2 목걸이 귀걸이 팔찌, 등급 나눠서, 각 아티팩트마다 올려주는게 다름" — 규칙은 core {@link Artifacts}):
 * <ul>
 * <li>칸 다섯 (반지 1 · 반지 2 · 목걸이 · 귀걸이 · 팔찌) 을 플레이어에 저장 (아이템 그대로) · 죽어도 남음. 끼면 가방에서 빠지고, 빼면 가방으로.</li>
 * <li>칸마다 그 종류만, 착용 레벨 (일반 10 ~ 초월 50 — 렙제 레벨 RpgCombat.reqLevelOf) 이 되어야 낌.</li>
 * <li>보너스: 칸들의 옵션 합 ({@link #totals} — 플레이어마다 기억, 칸이 바뀌거나 접속 · 레벨이 바뀌면 다시) 을 스탯 트리 보너스에 더함
 * ({@link StatTrees#extra} — 공격력 · 치명타 · 최대 체력 · 받는 피해 · 룬 피해 · 재사용 대기 · 치명타 피해 · 이동 속도, 상한은 스탯 트리와 같음).
 * 스탯 트리를 쓰지 않는 플레이어 (게임 시험) 도 아티팩트 보너스는 받음.</li>
 * <li>창: 클라이언트의 아티팩트 창 (I 키 · 인벤토리 패널 단추 · /아티팩트) — 끌어 놓기 · 누르기 · 칸 오른쪽 클릭 (ArtifactNet). 들고 우클릭해도 낌.</li>
 * </ul>
 */
public final class ArtifactSystem {
    private ArtifactSystem() {}

    /** 칸 수 (반지 1 · 반지 2 · 목걸이 · 귀걸이 · 팔찌) */
    public static final int SIZE = 5;

    /** 저장하는 칸들 (늘 SIZE 개 — 빈 칸은 EMPTY) */
    public record Data(List<ItemStack> slots) {
        public static final Codec<Data> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(Data::of, Data::slots);

        /** SIZE 칸으로 맞춤 (모자라면 빈 칸, 넘치면 버림) — 복사본 */
        static Data of(List<ItemStack> list) {
            List<ItemStack> out = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) {
                ItemStack s = i < list.size() ? list.get(i) : ItemStack.EMPTY;
                out.add(s == null || s.isEmpty() ? ItemStack.EMPTY : s.copy());
            }
            return new Data(Collections.unmodifiableList(out));
        }

        static Data empty() {
            return of(List.of());
        }
    }

    public static AttachmentType<Data> TYPE;
    /** 플레이어 → 아티팩트 보너스 합 (상한 전 — 처음 물을 때 칸들로 셈, 칸 · 레벨이 바뀌면 지움) */
    private static final Map<UUID, Map<String, Double>> TOTALS = new ConcurrentHashMap<>();

    /** Mmorpg.onInitialize (StatTrees.init 다음) */
    public static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("artifacts"), builder -> builder
                .persistent(Data.CODEC)
                .copyOnDeath()
                .initializer(Data::empty));
        ArtifactNet.register();
        StatTrees.extra = ArtifactSystem::totals;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            invalidate(handler.player);
            StatTrees.extraChanged(handler.player);
            ArtifactNet.send(handler.player, false);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            invalidate(newPlayer);
            StatTrees.extraChanged(newPlayer); // 새 몸에 이동 속도 수정자 · 최대 체력
            ArtifactNet.send(newPlayer, false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOTALS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOTALS.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    // ------------------------------------------------------------ 칸

    private static Data data(ServerPlayer sp) {
        return sp.getAttachedOrCreate(TYPE);
    }

    /** 칸들 (늘 SIZE 개 — 고치지 말 것) */
    public static List<ItemStack> slots(ServerPlayer sp) {
        return data(sp).slots();
    }

    /** 칸 i 의 아이템 (빈 칸 · 없는 칸이면 EMPTY — 고치지 말 것) */
    public static ItemStack get(ServerPlayer sp, int i) {
        return i < 0 || i >= SIZE ? ItemStack.EMPTY : data(sp).slots().get(i);
    }

    /** 칸 i 의 아티팩트 (없으면 null) */
    public static Artifacts.Art art(ServerPlayer sp, int i) {
        return ArtifactItem.of(get(sp, i));
    }

    /** 칸 i 에 놓음 (복사본 · 검사 없음 — 시험 · 명령어용) → 보너스 다시 · 클라이언트에 보냄 */
    public static void set(ServerPlayer sp, int i, ItemStack stack) {
        if (i < 0 || i >= SIZE) return;
        List<ItemStack> list = new ArrayList<>(data(sp).slots());
        list.set(i, stack == null ? ItemStack.EMPTY : stack);
        sp.setAttached(TYPE, Data.of(list));
        changed(sp);
    }

    /** 칸이 바뀐 뒤: 보너스 다시 (이동 속도 · 최대 체력) · 클라이언트에 칸 · 스탯 */
    private static void changed(ServerPlayer sp) {
        invalidate(sp);
        StatTrees.extraChanged(sp);
        ArtifactNet.send(sp, false);
        mmorpg.fabric.RpgNet.sync(sp, false);
    }

    // ------------------------------------------------------------ 보너스

    /** 보너스 합을 잊음 (다음에 물을 때 칸들로 다시 셈 — 레벨 · 칸이 바뀐 뒤) */
    public static void invalidate(ServerPlayer sp) {
        if (sp != null) TOTALS.remove(sp.getUUID());
    }

    /** 아티팩트 보너스 합 (상한 전 — 칸 종류가 맞고 착용 레벨이 되는 것만) */
    public static Map<String, Double> totals(ServerPlayer sp) {
        if (sp == null || TYPE == null) return Map.of();
        Map<String, Double> t = TOTALS.get(sp.getUUID());
        if (t != null) return t;
        t = Map.copyOf(Artifacts.totals(ids(sp), RpgCombat.reqLevelOf(sp)));
        TOTALS.put(sp.getUUID(), t);
        return t;
    }

    /** 칸 순서의 아이템 ID (빈 칸 · 아티팩트가 아니면 null) */
    static List<String> ids(ServerPlayer sp) {
        List<String> out = new ArrayList<>(SIZE);
        for (ItemStack s : slots(sp)) {
            Artifacts.Art a = ArtifactItem.of(s);
            out.add(a == null ? null : a.id());
        }
        return out;
    }

    /** 낀 칸 수 */
    public static int worn(ServerPlayer sp) {
        int n = 0;
        for (ItemStack s : slots(sp)) if (ArtifactItem.of(s) != null) n++;
        return n;
    }

    // ------------------------------------------------------------ 끼기 · 빼기 · 옮기기

    /** 가방 칸 번호가 올바른지 (핫바 + 가방 0 ~ 35 · 왼손 40) */
    static boolean invSlot(int i) {
        return (i >= 0 && i < Inventory.INVENTORY_SIZE) || i == Inventory.SLOT_OFFHAND;
    }

    /**
     * 이 아티팩트가 저절로 들어갈 칸: 그 종류의 빈 칸 (앞부터), 다 차 있으면 등급이 가장 낮은 칸 (같으면 앞 — 반지 1). 종류가 없으면 -1
     */
    public static int autoSlot(ServerPlayer sp, Artifacts.Art a) {
        List<Integer> where = Artifacts.slotsFor(a.type());
        if (where.isEmpty()) return -1;
        for (int i : where) if (get(sp, i).isEmpty()) return i;
        int best = where.get(0);
        int bestGrade = Integer.MAX_VALUE;
        for (int i : where) {
            Artifacts.Art o = art(sp, i);
            int g = o == null ? -1 : o.gradeIndex();
            if (g < bestGrade) {
                best = i;
                bestGrade = g;
            }
        }
        return best;
    }

    /**
     * 가방 칸 inv 의 아티팩트를 칸 slot (-1 = 저절로) 에 낌 — 그 칸에 있던 것은 가방 칸 inv 로 (서로 바꿈). 실패 문구 (되면 null)
     */
    public static String equip(ServerPlayer sp, int inv, int slot) {
        if (!invSlot(inv)) return fail(sp, "§c잘못된 칸입니다");
        Inventory pinv = sp.getInventory();
        ItemStack it = pinv.getItem(inv);
        Artifacts.Art a = ArtifactItem.of(it);
        if (a == null) return fail(sp, "§c아티팩트만 낄 수 있습니다");
        int to = slot < 0 ? autoSlot(sp, a) : slot;
        if (to < 0 || to >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        if (!Artifacts.fits(to, a)) {
            return fail(sp, "§c" + Artifacts.slotName(to) + " 칸에는 " + Artifacts.type(Artifacts.slotType(to)).name() + "만 낄 수 있습니다 §7(" + a.typeName() + ")");
        }
        double lv = RpgCombat.reqLevelOf(sp);
        if (!Artifacts.usable(a, lv)) return fail(sp, "§c" + a.coloredName() + " §c— " + a.level() + "렙부터 낄 수 있습니다 §7(지금 " + Js.str(Math.floor(lv)) + ")");
        ItemStack old = get(sp, to).copy();
        ItemStack one = it.copyWithCount(1);
        if (it.getCount() > 1) {
            // 아티팩트는 하나씩이지만 명령어로 겹쳤으면: 하나만 끼고 있던 것은 다른 빈 칸으로
            if (!old.isEmpty() && !addToBag(sp, old)) return fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
            it.shrink(1);
        } else {
            pinv.setItem(inv, old);
        }
        pinv.setChanged();
        List<ItemStack> list = new ArrayList<>(data(sp).slots());
        list.set(to, one);
        sp.setAttached(TYPE, Data.of(list));
        changed(sp);
        equipFx(sp, a);
        Notify.notify(sp, a.coloredName() + " §7→ §d" + Artifacts.slotName(to) + " §a" + Artifacts.optionText(a), 50);
        return null;
    }

    /** 칸 slot 의 아티팩트를 가방으로 (가방 → 핫바 순으로 빈 칸). 빈 칸이면 아무 일 없음. 실패 문구 (되면 null) */
    public static String unequip(ServerPlayer sp, int slot) {
        if (slot < 0 || slot >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        ItemStack old = get(sp, slot);
        if (old.isEmpty()) return null;
        Artifacts.Art a = ArtifactItem.of(old);
        if (!addToBag(sp, old.copy())) return fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
        List<ItemStack> list = new ArrayList<>(data(sp).slots());
        list.set(slot, ItemStack.EMPTY);
        sp.setAttached(TYPE, Data.of(list));
        changed(sp);
        SkillFx.playFor(sp, "item.armor.equip_generic", 0.8, 0.7);
        if (a != null) Notify.notify(sp, "§7" + Artifacts.slotName(slot) + " 빼기: " + a.coloredName() + " §7→ 가방", 40);
        return null;
    }

    /** 두 칸을 서로 바꿈 (같은 종류 칸끼리만 — 반지 1 ↔ 반지 2, 한쪽이 비었으면 옮김). 실패 문구 (되면 null) */
    public static String move(ServerPlayer sp, int from, int to) {
        if (from < 0 || from >= SIZE || to < 0 || to >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        if (from == to) return null;
        if (!Artifacts.slotType(from).equals(Artifacts.slotType(to))) {
            return fail(sp, "§c" + Artifacts.slotName(to) + " 칸에는 " + Artifacts.type(Artifacts.slotType(to)).name() + "만 낄 수 있습니다");
        }
        List<ItemStack> list = new ArrayList<>(data(sp).slots());
        Collections.swap(list, from, to);
        sp.setAttached(TYPE, Data.of(list));
        changed(sp);
        SkillFx.playFor(sp, "item.armor.equip_chain", 1.2, 0.6);
        return null;
    }

    /** 든 아티팩트를 우클릭 (ArtifactItem.use): 맞는 칸에 낌 (autoSlot — 있던 것은 그 손으로). 실패 문구 (되면 null) */
    public static String equipHeld(ServerPlayer sp, InteractionHand hand) {
        int inv = hand == InteractionHand.MAIN_HAND ? sp.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        Artifacts.Art a = ArtifactItem.of(sp.getInventory().getItem(inv));
        if (a == null) return null;
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
        ArtifactNet.send(sp, false); // 창이 먼저 바꿔 그렸으면 되돌림
        return text;
    }

    /** 끼기 연출: 등급 색 땅 고리 · 불꽃 · 소리 (영웅 · 초월은 더 크게 — 초월은 업화 불티 · 마법진) */
    private static void equipFx(ServerPlayer sp, Artifacts.Art a) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] rgb = gradeRgb(a.gradeIndex());
        int g = a.gradeIndex();
        SkillFx.shock(level, c, rgb, 1.3 + g * 0.25, 0.5);
        SkillFx.sparks(level, V.up(c, 1.1), rgb, 6 + g * 4, 2 + g * 0.5);
        SkillFx.playFor(sp, g >= 3 ? "item.armor.equip_gold" : "item.armor.equip_chain", 1.0, 0.9);
        SkillFx.play(level, "block.amethyst_block.chime", c, 0.8 + g * 0.12, 0.9);
        if (g >= 3) SkillFx.play(level, "block.beacon.power_select", c, 1.4 + (g - 3) * 0.2, 0.5);
        if (g >= 4) {
            SkillFx.rune(level, c, mmorpg.fabric.skill.Opt.of("size", 1.6, "life", 0.9, "color", rgb, "alpha", 0.85, "progress", 1.0, "rate", 60.0));
            SkillFx.particle(level, net.minecraft.core.particles.ParticleTypes.FLAME, V.up(c, 0.9), 18, 0.45, 0.04);
            SkillFx.particle(level, net.minecraft.core.particles.ParticleTypes.LAVA, V.up(c, 0.6), 4, 0.35, 0);
        }
    }

    /** 등급 색 (0 ~ 1 — RpgUi.GRADE_RGB 와 같음) */
    public static double[] gradeRgb(int g) {
        int[] rgb = {0xC8C8C8, 0x4C8BFF, 0xB66CFF, 0xFF9A2E, 0xFF4A3D};
        int v = rgb[Math.max(0, Math.min(rgb.length - 1, g))];
        return new double[] {((v >> 16) & 255) / 255.0, ((v >> 8) & 255) / 255.0, (v & 255) / 255.0};
    }

    // ------------------------------------------------------------ 클라이언트 · 명령어

    /** 클라이언트가 보낸 일 (equip · unequip · move · sync) */
    static void action(ServerPlayer sp, String action, int a, int b) {
        if (sp == null || sp.isRemoved()) return;
        switch (action) {
            case "equip" -> equip(sp, a, b);
            case "unequip" -> unequip(sp, a);
            case "move" -> move(sp, a, b);
            case "sync" -> ArtifactNet.send(sp, false);
            default -> {
            }
        }
    }

    /** 아티팩트 창 열기 (모드 클라이언트 — 아니면 채팅 요약) */
    public static void open(ServerPlayer sp) {
        if (ArtifactNet.canSend(sp)) {
            mmorpg.fabric.RpgNet.sync(sp, false);
            ArtifactNet.send(sp, true);
            return;
        }
        chatSummary(sp);
    }

    /** 채팅 요약: 칸마다 낀 것 · 합계 */
    public static void chatSummary(ServerPlayer sp) {
        Notify.say(sp, "§d§l아티팩트 §r§7(낀 칸 " + worn(sp) + "/" + SIZE + ")");
        double lv = RpgCombat.reqLevelOf(sp);
        for (int i = 0; i < SIZE; i++) {
            Artifacts.Art a = art(sp, i);
            String line = a == null ? "§8비어 있음"
                    : a.coloredName() + " §7— " + (Artifacts.usable(a, lv) ? "§a" + Artifacts.optionText(a) : "§c" + a.level() + "렙부터 (보너스 없음)");
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(mmorpg.fabric.BedrockText.toJava("§7" + Artifacts.slotName(i) + ": " + line)));
        }
        String all = StatTree.bonusText(totals(sp));
        sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(mmorpg.fabric.BedrockText.toJava("§e합계: " + (all.isEmpty() ? "§8없음" : "§a" + all))));
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
