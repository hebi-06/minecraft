package mmorpg.fabric.vanilla;

import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseCooldown;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-11-18 1인칭 모션 시범 (축복의 길 — 사용자 "1인칭 공격 · 스킬 모션", 모드 없이). 모드 클라이언트의 1인칭 동작 (poses.json fp 열쇠) 을
 * tools/fp_frames.py 가 무기 디자인마다 프레임 모델로 구워 두고 (assets/rpg/models/item/fp/&lt;디자인&gt;/&lt;동작&gt;_&lt;k&gt;), 아이템 정의
 * ({@link VanillaPack} fpWrap) 가 1인칭 오른손에서 custom_model_data 글자 1번 (동작 이름) → range_dispatch (minecraft:cooldown) 로 고름.
 * 동작이 나갈 때 (MotionSystem.broadcast) 그 사람에게만 무기의 쿨타임 묶음으로 쿨타임 패킷을 보내 재생 (서버의 쿨타임은 그대로 — 막는 것 없음).
 *
 * <p>시범에서 확인할 것 (CI 영상 fppilot:): 핫바 쿨타임 덮개 · 동작 이름 (아이템 구성 요소) 을 바꿀 때 다시 들기 · Q 의 손 비움 · 쿨타임 중
 * 우클릭 패킷. 시범 동안만 켬 ({@link #enabled} — -Dmmorpg.fp=true 또는 사진기 영상).
 */
public final class VanillaFirstPerson {
    private VanillaFirstPerson() {}

    /** 시범 스위치 (기본 끔 — 켜면 프레임이 있는 무기의 1인칭 모션을 재생) */
    public static volatile boolean enabled = Boolean.getBoolean("mmorpg.fp");
    /** true = 동작마다 동작 이름 (custom_model_data 글자 1번) 을 바꿔 보냄 (다시 들기 확인), false = 이름 없이 늘 첫 연타 동작 프레임 */
    public static volatile boolean switchMotion = true;
    /** 시범: Q (버리기) 를 막고 손 칸을 되돌려 보냄 (스킬 키 자리 — 버리지 않음) */
    public static volatile boolean blockDrop = true;

    /** 프레임을 감싼 아이템 (VanillaPack) → 그 디자인의 동작 이름 → 프레임 수 */
    static final Map<Identifier, Map<String, Integer>> WRAPPED = new ConcurrentHashMap<>();
    /** 사람마다 지금 보낸 동작 이름 (convert 가 custom_model_data 글자 1번으로) */
    private static final Map<UUID, String> MOTION = new ConcurrentHashMap<>();

    /** 시험 · 영상 기록 */
    public static int played;
    public static String lastMotion = "";
    public static int lastTicks;
    public static int resent;
    public static int drops;
    public static int uses;

    /** VanillaSupport.init: 나가면 동작 이름을 지움 */
    static void init() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
    }

    /** 프레임을 감쌀 아이템 (VanillaPack.cosmeticItems — 리소스 팩을 만들 때) */
    static void wrapped(Identifier item, Map<String, Integer> motions) {
        WRAPPED.put(item, Map.copyOf(motions));
    }

    /** 감싼 아이템 수 (시험) */
    public static int wrappedCount() {
        return WRAPPED.size();
    }

    /**
     * 동작이 나감 (MotionSystem.broadcast — 평타 · 스킬 · 룬): 모드 없는 클라이언트가 프레임이 있는 무기를 들었으면, 동작 이름을 바꿔 보내고 (switchMotion)
     * 쿨타임 패킷 (길이 = 동작 틱 ÷ 빠르기) 으로 1인칭 프레임을 재생
     */
    public static void play(ServerPlayer sp, String motion, float speed) {
        if (!enabled || sp == null || sp.isRemoved() || motion == null || !VanillaClients.isVanilla(sp)) return;
        ItemStack st = sp.getMainHandItem();
        if (!(st.getItem() instanceof RpgWeaponItem)) return;
        Map<String, Integer> motions = WRAPPED.get(BuiltInRegistries.ITEM.getKey(st.getItem()));
        if (motions == null) return;
        Integer frames = motions.get(motion);
        if (frames == null) return; // 이 동작의 프레임이 없음 (시범은 칼 평타 둘)
        if (switchMotion) {
            String old = MOTION.put(sp.getUUID(), motion);
            if (!motion.equals(old)) resendHeld(sp);
        } else if (MOTION.remove(sp.getUUID()) != null) {
            resendHeld(sp);
        }
        int ticks = Math.max(4, Math.round(frames / Math.max(0.25F, speed)));
        sp.connection.send(new ClientboundCooldownPacket(group(st), ticks));
        lastPlayTick = mmorpg.fabric.Tasks.now();
        played++;
        lastMotion = motion;
        lastTicks = ticks;
    }

    /** 모드 없는 클라이언트에 가는 무기의 custom_model_data 글자 1번 (없으면 null — 이름을 보내지 않음) */
    static String motionFor(ServerPlayer receiver, ItemStack stack) {
        if (!enabled || receiver == null || !switchMotion) return null;
        if (!WRAPPED.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return null;
        return MOTION.get(receiver.getUUID());
    }

    /**
     * 시범: Q · Ctrl + Q (버리기 패킷) — 프레임이 있는 무기를 든 모드 없는 클라이언트면 버리지 않고 (스킬 키 자리) 손 칸을 되돌려 보냄
     * (클라이언트는 패킷을 보내기 전에 손 칸을 먼저 비움). 막았으면 true
     */
    public static boolean onDrop(ServerPlayer sp) {
        if (!enabled || !blockDrop || sp == null || !VanillaClients.isVanilla(sp)) return false;
        ItemStack st = sp.getMainHandItem();
        if (!(st.getItem() instanceof RpgWeaponItem) || !WRAPPED.containsKey(BuiltInRegistries.ITEM.getKey(st.getItem()))) return false;
        drops++;
        resendHeld(sp);
        Mmorpg.LOG.info("[vanilla] fp drop key {} (blocked, slot resent)", drops);
        return true;
    }

    /** 쿨타임 창 안 (마지막 재생 뒤 그 길이 안) 에 닿은 우클릭 수 (시험) */
    public static int usesInCooldown;
    private static long lastPlayTick = -1000;

    /** 시범: 무기의 우클릭 (아이템 사용 패킷이 서버에 닿음 — RpgWeaponItem.use) — 1인칭 쿨타임 중에도 닿는지 */
    public static void onUse(ServerPlayer sp) {
        if (!enabled || sp == null || !VanillaClients.isVanilla(sp)) return;
        uses++;
        boolean inCooldown = mmorpg.fabric.Tasks.now() - lastPlayTick < lastTicks;
        if (inCooldown) usesInCooldown++;
        Mmorpg.LOG.info("[vanilla] fp use packet {} (in client cooldown window {})", uses, inCooldown);
    }

    /** 나감 · 죽음 */
    static void forget(UUID id) {
        MOTION.remove(id);
    }

    /** 손 칸을 다시 보냄 (Polymer 가 받는 사람마다 바꿈 — convert 의 동작 이름) */
    private static void resendHeld(ServerPlayer sp) {
        int slot = sp.getInventory().getSelectedSlot();
        sp.connection.send(new ClientboundSetPlayerInventoryPacket(slot, sp.getInventory().getItem(slot).copy()));
        resent++;
    }

    /** 26.3 swing_animation 구성 요소 · "none" 값 (처음 쓸 때 코덱으로 — 이름 · 형식이 다르면 null 이라 아무것도 하지 않음) */
    private static net.minecraft.core.component.DataComponentType<?> swingType;
    private static Object swingNone;
    private static boolean swingTried;

    /**
     * 시범: 감싼 무기를 받는 모드 없는 클라이언트에는 바닐라 팔 휘두르기를 끔 (swing_animation none) — 1인칭 프레임과 겹치지 않게
     * (좌클릭 · Q 의 바닐라 휘두르기). VanillaCosmetics.convert (무기 · 받는 사람 자신)
     */
    static void noSwing(ItemStack out, ItemStack stack) {
        if (!enabled || !WRAPPED.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return;
        if (!swingTried) {
            swingTried = true;
            try {
                swingType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse("minecraft:swing_animation"));
                com.mojang.serialization.Codec<?> codec = swingType == null ? null : swingType.codec();
                swingNone = codec == null ? null
                        : codec.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"type\":\"none\"}")).result().orElse(null);
            } catch (RuntimeException e) {
                swingNone = null;
            }
            Mmorpg.LOG.info("[vanilla] fp swing_animation component {} none {}", swingType != null, swingNone);
        }
        if (swingType != null && swingNone != null) setUnchecked(out, swingType, swingNone);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setUnchecked(ItemStack out, net.minecraft.core.component.DataComponentType<T> type, Object value) {
        out.set(type, (T) value);
    }

    /** 무기의 쿨타임 묶음 (VanillaItems 가 바닐라 아이템에 넣는 것과 같음 — 진짜 아이템의 묶음, 없으면 아이템 ID) */
    static Identifier group(ItemStack st) {
        UseCooldown real = st.get(DataComponents.USE_COOLDOWN);
        Identifier id = BuiltInRegistries.ITEM.getKey(st.getItem());
        return real != null ? real.cooldownGroup().orElse(id) : id;
    }

    /** 시험 · 영상: 기록 지움 */
    public static void resetCounters() {
        played = 0;
        resent = 0;
        drops = 0;
        uses = 0;
        usesInCooldown = 0;
        lastMotion = "";
        lastTicks = 0;
    }

    /** 감싼 아이템 목록 (시험) */
    public static Set<Identifier> wrappedItems() {
        return WRAPPED.keySet();
    }
}
