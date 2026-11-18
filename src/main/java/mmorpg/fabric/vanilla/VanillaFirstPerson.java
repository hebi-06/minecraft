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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-11-18 1인칭 모션 (축복의 길 — 사용자 "1인칭 공격 · 스킬 모션", 모드 없이). 모드 클라이언트의 1인칭 동작 (poses.json fp 열쇠) 을
 * tools/fp_frames.py 가 무기 디자인마다 프레임 모델로 구워 두고 (assets/rpg/models/item/fp/&lt;디자인&gt;/&lt;동작&gt;_&lt;k&gt;), 아이템 정의
 * ({@link VanillaPack} fpWrap — 스킨이면 그 스킨 디자인의 프레임) 가 1인칭 오른손에서 custom_model_data 글자 1번 (동작 이름) → range_dispatch
 * (minecraft:cooldown) 로 고름. 동작이 나갈 때 (MotionSystem.broadcast · 활 · 완드 평타 {@link #basic}) 그 사람에게만 무기의 쿨타임 묶음으로
 * 쿨타임 패킷을 보내 재생 (서버의 쿨타임은 그대로 — 막는 것 없음).
 *
 * <p>디자인마다 굽는 동작 = 그 종류의 바탕 (근접 평타 연타 · 활 shoot · 완드 cast) + 스킬 대표 동작 (근접: 큰 베기 · 내려찍기 · 찌르기 · 시전 · 돌진 · 회전,
 * 방패 · 활 · 완드는 따로). 직업 · 룬 스킬 동작 (380 남짓) 은 구운 대표 가운데 1인칭 움직임이 가장 닮은 것 (fp_map.json — 끝 방향 · 자리의 DTW) 으로 보여 줌.
 *
 * <p>시범 결과 (CI 44256605383 · 44286558279, 칼): 쿨타임 프레임은 틱마다 (20장/초) 잘 나오고 평타 뒤 1틱에 시작. 동작 이름 (아이템 구성 요소) 을 바꾸면
 * 바닐라 다시 들기 (손이 3틱 내려갔다 올라옴) — 아이템 정의의 hand_animation_on_swap false 로 끔 ({@link #swapFree}: 이름을 바꾼 그 틱에 재생 ·
 * 연타 단계마다 그 동작). Q 는 스킬 키 (SkillKeys — 손 칸을 되돌림, 1 ~ 2틱 깜박임). 핫바의 그 칸에 쿨타임 덮개 (흰 막) 가 동작 동안 보임.
 * 쿨타임 중 우클릭은 서버에 닿음. 끄기: -Dmmorpg.fp=false.
 */
public final class VanillaFirstPerson {
    private VanillaFirstPerson() {}

    /** 스위치 (2026-11-18 무기 종류 모두 — 기본 켬, -Dmmorpg.fp=false 로 끔): 프레임이 있는 무기의 1인칭 모션을 재생 */
    public static volatile boolean enabled = !"false".equals(System.getProperty("mmorpg.fp"));
    /** true = 스킬 동작은 그 동작 (또는 닮은 대표 동작 — fp_map.json) 의 프레임 (이름을 바꿈), false = 스킬도 바탕 프레임 (이름을 바꾸지 않음) */
    public static volatile boolean skillMotions = true;
    /**
     * 무기 아이템 정의에 hand_animation_on_swap false (VanillaPack fpWrap — 구성 요소가 바뀌어도 손을 내렸다 올리지 않음): 켜면 이름을 바꾼 그 틱에 재생 ·
     * 평타 연타도 그 단계의 동작 (칼 둘째 = 반대 가로). 끄면 시범 2차처럼 스킬만 이름을 바꾸고 lead 틱 뒤 (내려가는 3틱)
     */
    public static volatile boolean swapFree = true;
    /** swapFree 가 아닐 때 스킬 동작 이름을 바꾼 뒤 쿨타임까지 틱 (다시 들기의 내려가는 3틱) */
    public static volatile int lead = 3;
    /** 시범: Q (버리기) 를 막고 손 칸을 되돌려 보냄 (스킬 키 자리 — 버리지 않음) */
    public static volatile boolean blockDrop = true;

    /** 프레임을 감싼 아이템 (VanillaPack) → 그 디자인의 동작 이름 → 프레임 수 */
    static final Map<Identifier, Map<String, Integer>> WRAPPED = new ConcurrentHashMap<>();
    /** 프레임을 감싼 아이템 → 이름이 없을 때의 동작 (VanillaPack fpWrap 의 fallback — 첫 연타 동작) */
    static final Map<Identifier, String> BASE = new ConcurrentHashMap<>();
    /** 사람마다 지금 보낸 동작 이름 (convert 가 custom_model_data 글자 1번으로 — 없으면 첫 연타 동작) */
    private static final Map<UUID, String> MOTION = new ConcurrentHashMap<>();
    /** 사람마다 재생 차례 (늦게 도는 쿨타임 · 이름 지움이 다음 재생을 덮지 않게) */
    private static final Map<UUID, Integer> SEQ = new ConcurrentHashMap<>();

    /** fp_map.json (tools/fp_frames.py): 무기 종류 → 바탕 동작 · 종류 → (동작 → 구운 대표 동작) · 구운 동작의 틱 */
    private static final Map<String, String> TYPE_BASE = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> TYPE_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Integer> TICKS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> TYPE_BAKED = new ConcurrentHashMap<>();

    /** 무기 종류에 구운 동작 (처음 = 바탕 — 없으면 빈 목록) */
    public static List<String> bakedFor(String type) {
        return TYPE_BAKED.getOrDefault(type, List.of());
    }

    static {
        try (java.io.InputStream in = VanillaFirstPerson.class.getResourceAsStream("/assets/mmorpg/motion/fp_map.json")) {
            if (in != null) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.getAsJsonObject("base").entrySet()) TYPE_BASE.put(e.getKey(), e.getValue().getAsString());
                for (var e : o.getAsJsonObject("baked").entrySet()) {
                    List<String> l = new java.util.ArrayList<>();
                    for (var v : e.getValue().getAsJsonArray()) l.add(v.getAsString());
                    TYPE_BAKED.put(e.getKey(), List.copyOf(l));
                }
                for (var e : o.getAsJsonObject("ticks").entrySet()) TICKS.put(e.getKey(), e.getValue().getAsInt());
                for (var t : o.getAsJsonObject("types").entrySet()) {
                    Map<String, String> m = new java.util.HashMap<>();
                    for (var e : t.getValue().getAsJsonObject().entrySet()) m.put(e.getKey(), e.getValue().getAsString());
                    TYPE_MAP.put(t.getKey(), Map.copyOf(m));
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vanilla] fp_map.json: {}", e.toString());
        }
    }

    /** 무기 종류의 바탕 동작 (이름이 없을 때 · 평타 — 근접은 첫 연타 동작, 활 shoot · 완드 cast). 없으면 null */
    public static String baseOf(String type) {
        return TYPE_BASE.get(type);
    }

    /** 이 종류 무기로 동작 motion 을 쓸 때 보여 줄 구운 동작 (구웠으면 그것, 아니면 닮은 대표 — 없으면 null) */
    public static String shownFor(String type, String motion, Map<String, Integer> baked) {
        if (motion == null) return null;
        if (baked.containsKey(motion)) return motion;
        Map<String, String> m = TYPE_MAP.get(type);
        String r = m == null ? null : m.get(motion);
        return r != null && baked.containsKey(r) ? r : null;
    }

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

    /** 프레임을 감쌀 아이템 (VanillaPack.cosmeticItems — 리소스 팩을 만들 때) · base = 이름이 없을 때의 동작 */
    static void wrapped(Identifier item, Map<String, Integer> motions, String base) {
        WRAPPED.put(item, Map.copyOf(motions));
        BASE.put(item, base);
    }

    /** 감싼 아이템 수 (시험) */
    public static int wrappedCount() {
        return WRAPPED.size();
    }

    /**
     * 동작이 나감 (MotionSystem.broadcast — 평타 · 스킬 · 룬): 모드 없는 클라이언트가 프레임이 있는 무기를 들었으면 쿨타임 패킷 (길이 = 보여 줄 동작의 틱 ÷ 빠르기)
     * 으로 1인칭 프레임을 재생. 구운 동작이면 그것, 아니면 닮은 대표 동작 (fp_map.json), 둘 다 없으면 바탕 동작 (이름 그대로)
     */
    public static void play(ServerPlayer sp, String motion, float speed) {
        play(sp, motion, speed, false);
    }

    /** asSkill = 연타 동작이어도 스킬처럼 (이름을 바꿈 — 시범 영상) */
    public static void play(ServerPlayer sp, String motion, float speed, boolean asSkill) {
        if (!enabled || sp == null || sp.isRemoved() || motion == null || !VanillaClients.isVanilla(sp)) return;
        ItemStack st = sp.getMainHandItem();
        if (!(st.getItem() instanceof RpgWeaponItem)) return;
        Identifier item = BuiltInRegistries.ITEM.getKey(st.getItem());
        Map<String, Integer> motions = WRAPPED.get(item);
        String base = BASE.get(item);
        if (motions == null || base == null || !motions.containsKey(base)) return;
        String type = VanillaCosmetics.weaponType(st.getItem());
        boolean comboStep = mmorpg.core.Motions.combo(type).contains(motion);
        // 2026-11-18 무기 종류 모두: 구운 동작이 아니면 (직업 · 룬 스킬 동작) 1인칭 움직임이 가장 닮은 대표 동작 (fp_map.json)
        String mapped = shownFor(type, motion, motions);
        boolean named = skillMotions && mapped != null && !mapped.equals(base) && (asSkill || !comboStep || swapFree);
        String shown = named ? mapped : base;
        String want = named ? mapped : null;
        UUID id = sp.getUUID();
        int seq = SEQ.merge(id, 1, Integer::sum);
        boolean change = !java.util.Objects.equals(MOTION.get(id), want);
        if (change) {
            if (want == null) MOTION.remove(id);
            else MOTION.put(id, want);
            resendHeld(sp);
        }
        int ticks = Math.max(4, Math.round(TICKS.getOrDefault(shown, motions.get(shown)) / Math.max(0.25F, speed)));
        Identifier group = group(st);
        int delay = change && !swapFree ? Math.max(0, lead) : 0;
        if (delay == 0) {
            sp.connection.send(new ClientboundCooldownPacket(group, ticks));
        } else {
            mmorpg.fabric.Tasks.later(delay, () -> {
                if (!sp.isRemoved() && SEQ.getOrDefault(id, 0) == seq) sp.connection.send(new ClientboundCooldownPacket(group, ticks));
            });
        }
        if (want != null) {
            // 끝나면 이름을 지움 — 다음 평타가 곧바로 첫 연타 동작 프레임 (쉬는 동안의 다시 들기)
            mmorpg.fabric.Tasks.later(delay + ticks + 1, () -> {
                if (sp.isRemoved() || SEQ.getOrDefault(id, 0) != seq || !want.equals(MOTION.get(id))) return;
                MOTION.remove(id);
                resendHeld(sp);
            });
        }
        lastPlayTick = mmorpg.fabric.Tasks.now() + delay;
        played++;
        if (!shown.equals(motion)) mappedPlays++;
        lastMotion = shown + (change ? " (lead " + delay + ")" : "");
        lastAsked = motion;
        lastTicks = ticks;
    }

    /** 활 · 완드 평타 (좌클릭 — RpgCombat.punchRanged 가 쏨): 바탕 동작 (활 shoot · 완드 cast) 프레임 — 근접 평타는 MotionSystem 이 동작을 보냄 */
    public static void basic(ServerPlayer sp) {
        if (!enabled || sp == null || !VanillaClients.isVanilla(sp)) return;
        String base = BASE.get(BuiltInRegistries.ITEM.getKey(sp.getMainHandItem().getItem()));
        if (base == null) return;
        float speed = (float) mmorpg.core.Motions.playSpeed(mmorpg.fabric.RpgCombat.attackSpeed.applyAsDouble(sp));
        play(sp, base, speed);
    }

    /** 감싼 아이템의 구운 동작 → 프레임 수 (없으면 null — 시험 · 영상) */
    public static Map<String, Integer> bakedOf(Identifier item) {
        return WRAPPED.get(item);
    }

    /** 영상: 이 종류에서 대표 동작 shown 으로 보이는 진짜 스킬 동작 하나 (직업 jb_ · 룬 rn_ — 이름 차례로 첫 것, 없으면 null) */
    public static String exampleFor(String type, String shown) {
        Map<String, String> m = TYPE_MAP.get(type);
        if (m == null) return null;
        String best = null;
        for (Map.Entry<String, String> e : m.entrySet()) {
            String k = e.getKey();
            if (!e.getValue().equals(shown) || !(k.startsWith("jb_") || k.startsWith("rn_"))) continue;
            if (best == null || k.compareTo(best) < 0) best = k;
        }
        return best;
    }

    /** 시험: 대표 동작으로 바꿔 보여 준 수 · 마지막으로 받은 동작 이름 */
    public static int mappedPlays;
    public static String lastAsked = "";

    /** 모드 없는 클라이언트에 가는 무기의 custom_model_data 글자 1번 (없으면 null — 첫 연타 동작) */
    static String motionFor(ServerPlayer receiver, ItemStack stack) {
        if (!enabled || receiver == null) return null;
        if (!WRAPPED.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return null;
        return MOTION.get(receiver.getUUID());
    }

    /** 손 칸을 바꿈 (VanillaInputMixin — 핫바): 새로 든 무기가 지금 이름을 받게 다시 보냄 (스킬 도중 가방이 통째로 간 칸의 옛 이름) */
    public static void onSlot(ServerPlayer sp) {
        if (!enabled || sp == null || !VanillaClients.isVanilla(sp)) return;
        if (!WRAPPED.containsKey(BuiltInRegistries.ITEM.getKey(sp.getMainHandItem().getItem()))) return;
        int slot = sp.getInventory().getSelectedSlot();
        sp.connection.send(new ClientboundSetPlayerInventoryPacket(slot, sp.getInventory().getItem(slot).copy())); // resent 에 세지 않음 (시범 영상의 셈)
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

    /** 2026-11-18 스킬 키 Q · Shift + Q (SkillKeys.onDrop — 버리지 않고 손 칸을 되돌렸음): 시범 셈 (모드 없는 클라이언트) */
    public static void noteDrop(ServerPlayer sp) {
        if (sp == null || !VanillaClients.isVanilla(sp)) return;
        drops++;
        resent++;
        Mmorpg.LOG.info("[vanilla] fp drop key {} (skill key, slot resent)", drops);
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
        SEQ.remove(id);
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
            // 시범 1차: minecraft:swing_animation 이 26.3 등록부에 없었음 (null). 2차 (CI 44259802101) 기록: 비슷한 이름은 attack_animation ·
            // interact_animation · weapon · attack_range · minimum_attack_charge … → 좌클릭 휘두르기는 attack_animation 으로 (값 꼴은 몇 가지를 차례로 시도해 기록)
            StringBuilder near = new StringBuilder();
            String err = "";
            try {
                Identifier found = null;
                for (String name : new String[] {"minecraft:swing_animation", "minecraft:attack_animation"}) {
                    swingType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(name));
                    if (swingType != null) {
                        found = Identifier.parse(name);
                        break;
                    }
                }
                for (Identifier k : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()) {
                    String path = k.getPath();
                    if (path.contains("swing") || path.contains("anim") || path.contains("attack") || path.contains("weapon")) near.append(' ').append(k);
                }
                com.mojang.serialization.Codec<?> codec = swingType == null ? null : swingType.codec();
                if (codec != null) {
                    for (String json : new String[] {"{\"type\":\"none\"}", "\"none\"", "{\"type\":\"minecraft:none\"}", "{\"animation\":\"none\"}"}) {
                        var r = codec.parse(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(json));
                        swingNone = r.result().orElse(null);
                        err += " " + json + " → " + r.error().map(e -> e.message()).orElse("ok");
                        if (swingNone != null) break;
                    }
                }
                near.insert(0, "using " + (swingType == null ? "none" : found) + " |");
            } catch (RuntimeException e) {
                swingNone = null;
                err = e.toString();
            }
            Mmorpg.LOG.info("[vanilla] fp swing_animation component {} none {} {} {}", swingType != null, swingNone, err, near);
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
        mappedPlays = 0;
        lastAsked = "";
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
