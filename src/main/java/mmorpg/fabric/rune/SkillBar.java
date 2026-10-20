package mmorpg.fabric.rune;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mmorpg.core.Runes;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 스킬 칸 (11단계 — 자바 판, 사용자 요청 "룬을 인벤에 두고 바꿔 들지 않고 메이플 스킬처럼 올려 두고 키로"): 룬 SIZE 개를 올려 두는 칸.
 * <ul>
 * <li>플레이어에 저장 (아이템 그대로 — 돌파 단계도) · 죽어도 남음. 올리면 가방 (인벤토리) 에서 빠지고, 빼면 가방으로 (가방 → 핫바 순으로 빈 칸)</li>
 * <li>같은 룬 (룬 ID) 은 한 칸에만 (재사용 대기가 룬마다 하나라 두 칸이면 헷갈림)</li>
 * <li>클라이언트가 칸의 키를 누르면 그 룬을 씀 ({@link #cast} → RuneCast.castRune — 재사용 대기 · 돌파 단계 · 침묵 · 탈것은 룬을 들고 쓸 때와 같음,
 * 손에 든 무기는 그대로)</li>
 * <li>칸이 바뀌면 클라이언트에 보냄 (화면 오른쪽 아래 스킬 칸 · 스킬 칸 창 — SkillBarNet). 룬 재사용 대기도 보냄 (칸의 덮개 · 남은 초)</li>
 * <li>돌파 (Breakthrough) 는 스킬 칸의 룬도 합침 (칸 번호 SLOT_BASE + i — 결과는 칸에 그대로), 갈기 (RuneSmith) 는 가방의 룬만</li>
 * </ul>
 */
public final class SkillBar {
    private SkillBar() {}

    /** 칸 수 (암살자 콤보 5단 + 다른 룬 3) */
    public static final int SIZE = 8;
    /** 돌파 (Breakthrough.Held) 에서 스킬 칸을 나타내는 칸 번호의 시작 (가방 0 ~ 35 · 왼손 40 과 겹치지 않게) */
    public static final int SLOT_BASE = 100;

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

    /** RuneSystem.init */
    static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("skill_bar"), builder -> builder
                .persistent(Data.CODEC)
                .copyOnDeath()
                .initializer(Data::empty));
        SkillBarNet.register();
        mmorpg.fabric.job.JobSkills.init(); // 0.26.0 직업 스킬 칸 5 (Shift + 룬 칸 1 ~ 5 의 키)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SkillBarNet.sendBar(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> SkillBarNet.sendBar(newPlayer));
    }

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

    /** 칸 i 에 놓음 (복사본) + 클라이언트에 보냄 */
    public static void set(ServerPlayer sp, int i, ItemStack stack) {
        if (i < 0 || i >= SIZE) return;
        List<ItemStack> list = new ArrayList<>(data(sp).slots());
        list.set(i, stack == null ? ItemStack.EMPTY : stack);
        sp.setAttached(TYPE, Data.of(list));
        SkillBarNet.sendBar(sp);
    }

    /** 이 룬 (ID) 이 올라간 칸 (없으면 -1) */
    public static int indexOf(ServerPlayer sp, String runeId) {
        List<ItemStack> list = slots(sp);
        for (int i = 0; i < list.size(); i++) {
            Runes.Rune r = RuneItems.runeOf(list.get(i));
            if (r != null && r.id().equals(runeId)) return i;
        }
        return -1;
    }

    /** 돌파의 칸 번호가 스킬 칸인지 (SLOT_BASE ~ SLOT_BASE + SIZE - 1) */
    public static boolean isBarSlot(int heldSlot) {
        return heldSlot >= SLOT_BASE && heldSlot < SLOT_BASE + SIZE;
    }

    /** 가방 칸 번호가 올바른지 (핫바 + 가방 0 ~ 35 · 왼손 40) */
    public static boolean invSlot(int i) {
        return (i >= 0 && i < Inventory.INVENTORY_SIZE) || i == Inventory.SLOT_OFFHAND;
    }

    // ------------------------------------------------------------ 올리기 · 빼기 · 옮기기

    /**
     * 가방 칸 inv 의 룬을 스킬 칸 slot 에 올림 — 그 칸에 룬이 있으면 그 룬은 가방 칸 inv 로 (서로 바꿈). 같은 룬이 다른 칸에 있으면 안 됨.
     * 실패 문구 (되면 null)
     */
    public static String equip(ServerPlayer sp, int inv, int slot) {
        if (slot < 0 || slot >= SIZE || !invSlot(inv)) return fail(sp, "§c잘못된 칸입니다");
        Inventory pinv = sp.getInventory();
        ItemStack it = pinv.getItem(inv);
        Runes.Rune r = RuneItems.runeOf(it);
        if (r == null) return fail(sp, "§c룬만 스킬 칸에 올릴 수 있습니다");
        int other = indexOf(sp, r.id());
        if (other >= 0 && other != slot) return fail(sp, "§c" + r.def().str("name") + " §7— 이미 " + (other + 1) + "번 스킬 칸에 있습니다");
        ItemStack old = get(sp, slot).copy();
        ItemStack one = it.copyWithCount(1);
        if (it.getCount() > 1) {
            // 룬은 한 칸에 하나지만 명령어로 겹쳤으면: 하나만 올리고 원래 칸의 룬은 다른 빈 칸으로
            if (!old.isEmpty() && !addToBag(sp, old)) return fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
            it.shrink(1);
        } else {
            pinv.setItem(inv, old);
        }
        pinv.setChanged();
        set(sp, slot, one);
        return null;
    }

    /** 스킬 칸 slot 의 룬을 가방으로 (가방 → 핫바 순으로 빈 칸). 빈 칸이면 아무 일 없음. 실패 문구 (되면 null) */
    public static String unequip(ServerPlayer sp, int slot) {
        if (slot < 0 || slot >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        ItemStack old = get(sp, slot);
        if (old.isEmpty()) return null;
        if (!addToBag(sp, old.copy())) return fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
        set(sp, slot, ItemStack.EMPTY);
        return null;
    }

    /** 두 스킬 칸을 서로 바꿈 (한쪽이 비었으면 옮김). 실패 문구 (되면 null) */
    public static String move(ServerPlayer sp, int from, int to) {
        if (from < 0 || from >= SIZE || to < 0 || to >= SIZE) return fail(sp, "§c잘못된 칸입니다");
        if (from == to) return null;
        List<ItemStack> list = new ArrayList<>(data(sp).slots());
        Collections.swap(list, from, to);
        sp.setAttached(TYPE, Data.of(list));
        SkillBarNet.sendBar(sp);
        return null;
    }

    /** 첫 빈 스킬 칸 (없으면 -1) */
    public static int firstEmpty(ServerPlayer sp) {
        List<ItemStack> list = slots(sp);
        for (int i = 0; i < list.size(); i++) if (list.get(i).isEmpty()) return i;
        return -1;
    }

    /**
     * 든 룬을 우클릭 (RuneItem.use — 룬은 스킬 칸에서만 씀): 같은 룬이 칸에 있으면 그 칸과 바꿔 끼움 (돌파 단계가 다를 때 — 같으면 안내만),
     * 없으면 첫 빈 칸에 올림. 빈 칸이 없으면 안내. 실패 문구 (되면 null)
     */
    public static String equipHeld(ServerPlayer sp, InteractionHand hand) {
        int inv = hand == InteractionHand.MAIN_HAND ? sp.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        ItemStack it = sp.getInventory().getItem(inv);
        Runes.Rune r = RuneItems.runeOf(it);
        if (r == null) return null;
        String name = r.gradeColor() + r.def().str("name") + Breakthrough.nameTag(Breakthrough.of(it));
        int same = indexOf(sp, r.id());
        int slot = same >= 0 ? same : firstEmpty(sp);
        if (same >= 0 && Breakthrough.of(get(sp, same)) == Breakthrough.of(it)) {
            return fail(sp, name + " §7— 이미 " + (same + 1) + "번 스킬 칸에 있습니다 §8(룬은 스킬 칸에서 키로 씀)");
        }
        if (slot < 0) return fail(sp, "§c빈 스킬 칸이 없습니다 §7(스킬 칸 창에서 바꾸기)");
        String err = equip(sp, inv, slot);
        if (err == null) Notify.notify(sp, name + " §7→ §b" + (slot + 1) + "번 스킬 칸" + (same >= 0 ? " §7(바꿔 끼움)" : ""), 40); // 소리 · 칸 반짝임은 클라이언트
        return err;
    }

    /** 가방의 빈 칸에 넣음 (가방 9 ~ 35 먼저, 그다음 핫바 — 룬을 다시 핫바에 두지 않게). 넣었으면 true */
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
        Notify.notify(sp, text, 40);
        SkillBarNet.sendBar(sp); // 창이 먼저 바꿔 그렸으면 되돌림
        return text;
    }

    // ------------------------------------------------------------ 쓰기

    /** 스킬 칸 slot 의 룬을 씀 (키). 재사용 대기 중이면 소리 없이 알림만 (칸을 골라 쓴 것과 같음). 나갔으면 true */
    public static boolean cast(ServerPlayer sp, int slot) {
        if (slot < 0 || slot >= SIZE || sp.isRemoved() || sp.isSpectator() || sp.isDeadOrDying()) return false;
        ItemStack st = get(sp, slot);
        Runes.Rune r = RuneItems.runeOf(st);
        if (r == null) {
            Notify.notify(sp, "§7" + (slot + 1) + "번 스킬 칸이 비어 있습니다 §8(스킬 칸 창에서 룬을 올림)", 30);
            return false;
        }
        return RuneCast.castRune(sp, r, true, Breakthrough.of(st));
    }

    /** 클라이언트가 보낸 일 (cast · job · equip · unequip · move) */
    static void action(ServerPlayer sp, String action, int a, int b) {
        if (sp == null || sp.isRemoved()) return;
        switch (action) {
            case "cast" -> cast(sp, a);
            case "job" -> mmorpg.fabric.job.JobSkills.press(sp, a); // 0.26.0 직업 스킬 칸 (Shift + 키)
            case "equip" -> equip(sp, a, b);
            case "unequip" -> unequip(sp, a);
            case "move" -> move(sp, a, b);
            case "sync" -> SkillBarNet.sendBar(sp);
            default -> {
            }
        }
    }
}
