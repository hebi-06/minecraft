package mmorpg.fabric.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.cosmetic.CosmeticDefs;
import mmorpg.fabric.cosmetic.Cosmetics;
import mmorpg.fabric.cosmetic.Look;
import mmorpg.fabric.gear.RpgArmorItem;
import mmorpg.fabric.mixin.MannequinAccessor;
import mmorpg.fabric.npc.NpcData;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 모드 없는 클라이언트가 보는 장식 (2026-11-08 로드맵 3번 꾸미기 — 사용자 "다음 작업"). 모드 클라이언트는 그대로 (CosmeticNet 모습 표 → 모드가 그림).
 * 그림은 tools/vanilla_cosmetics.py (mmorpg/vanilla_cosmetics.json).
 * <ul>
 * <li>무기 스킨: 손에 든 RPG 무기에 custom_model_data 글자 = 스킨 키 → 무기 아이템 정의 (VanillaPack 이 감쌈) 가 손 (1 · 3인칭) 에서만 스킨 모델
 * — 가방 · 핫바 아이콘은 그대로 (모드와 같음)</li>
 * <li>갑옷: 숨기기 = 갑옷 그림 (equippable asset) 을 뺌, 갑옷 스킨 = 그림 rpg:&lt;세트&gt; (갑옷 · 바지 · 신발 — 평면 그림), 투구 스킨 · 머리에 얹는
 * 장식 (hideHelmet) = 투구 칸 아이템 모델 (custom_model_data → 투구 아이템 정의의 head 자리 · 빈 칸이면 다른 사람에게는 그 그림만 보냄) —
 * 바닐라 CustomHeadLayer 가 머리 움직임 그대로 그림</li>
 * <li>칭호 · 천사의 고리 · 등 장식: 그 사람에게 태운 표시 엔티티 (글자 · 아이템 — Polymer). 등 장식 · 고리는 서버가 몸 방향 · 웅크림 · 날갯짓을
 * 2 ~ 8틱마다 (그 사이는 보간 — VanillaModels 와 같은 변환 차례)</li>
 * <li>오라 · 날개 가루: 바닐라 파티클 (보는 사람마다 — 48칸)</li>
 * </ul>
 * 내가 보는 내 모습: 무기 스킨 (1인칭에서도) · 갑옷 숨기기 · 스킨 · 머리 장식은 입은 칸만 (3인칭 F5 — 투구를 쓴 때만 머리 장식). 칭호 · 고리 · 등 장식 ·
 * 오라는 다른 사람에게만 (서버는 1 · 3인칭을 모름 — 모드도 1인칭에서는 내 오라를 뿌리지 않음) — 꾸미기 창의 미리보기 (마네킹) 로 봄.
 * 투명 (투명화 물약) 이면 모드처럼: 숨기기 · 머리 · 등 장식 · 오라 · 칭호 없음, 갑옷 스킨은 그대로.
 */
public final class VanillaCosmetics {
    private VanillaCosmetics() {}

    /** 미리보기 마네킹이 서 있는 틱 */
    static final int PREVIEW_TICKS = 300;
    /** 미리보기 마네킹이 도는 빠르기 (도 / 틱 — 한 바퀴 7.5초) */
    static final float PREVIEW_SPIN = 2.4F;
    /** 칭호 글자 아래 끝 — 이름표 글자 위에 한 줄 (태운 자리 = 몸 높이 기준: 이름표 기준점 +0.5, 글자 9 × 0.025 · 줄 간격 1.15) */
    static final float TITLE_Y = 0.5F - 9 * 0.025F + 9 * 1.15F * 0.025F;
    static final float LINE = 9 * 1.15F * 0.025F;

    // ------------------------------------------------------------ 그림 목록 (vanilla_cosmetics.json)

    record Bone(String name, String parent, String root, float[] off, float[] rot, String anim, ItemStack item, ItemStack glow, float k, float[] c0) {}

    record Rig(String root, List<Bone> bones) {}

    /** 투구 칸 아이템 (모델 → rpg:vcos/&lt;모델&gt;) */
    private static final Map<String, Identifier> HEAD = new HashMap<>();
    /** 투구 칸 아이템의 뼈 모델들 (모델 → rpg:item/vcos/&lt;모델&gt;/&lt;뼈&gt; …) — VanillaPack 이 투구 정의를 감쌀 때 */
    private static final Map<String, List<String>> HEAD_MODELS = new LinkedHashMap<>();
    private static final Map<String, Rig> RIGS = new HashMap<>();
    private static final Set<String> SETS = new HashSet<>();
    /** 무기 스킨 (손에 든 모델이 있는 것) */
    private static final Set<String> SKINS = new HashSet<>();

    // ------------------------------------------------------------ 상태

    /** 엔티티 UUID → 모습 (플레이어 · 미리보기 마네킹) — 서버 스레드에서만 고침 (패킷을 만드는 스레드는 읽기만) */
    private static final Map<UUID, Look> LOOKS = new ConcurrentHashMap<>();
    private static final Map<UUID, CosmeticHolder> HOLDERS = new HashMap<>();
    /** 미리보기 마네킹 → (주인, 끝나는 틱) */
    private static final Map<UUID, Preview> PREVIEWS = new LinkedHashMap<>();
    /** 다른 창을 닫으면 가방을 다시 보낼 사람 (투구 · 무기 그림이 바뀜) */
    private static final Set<UUID> PENDING = new HashSet<>();
    /** 지금 만드는 아이템이 누구의 모습을 따를지 (장비 패킷 — 그 엔티티의 모습). 없으면 받는 사람의 모습 (내 가방) */
    private static final ThreadLocal<View> OVERRIDE = new ThreadLocal<>();
    private static final Map<Item, String> TYPES = new ConcurrentHashMap<>();
    private static final Map<Equippable, Map<String, Equippable>> EQUIP = new ConcurrentHashMap<>();

    record View(Look look, boolean invisible) {}

    record Preview(UUID owner, Mannequin body, long end) {}

    /** 시험용 (VanillaProbe): 장비 패킷을 고친 수 · 마지막으로 보낸 머리 그림 */
    static int rewrites;
    static String lastHead = "";

    static void init() {
        load();
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> changed(h.player));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> forget(h.player, server));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> changed(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(VanillaCosmetics::tick);
    }

    private static void load() {
        try (InputStream in = VanillaCosmetics.class.getResourceAsStream("/mmorpg/vanilla_cosmetics.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("head").entrySet()) {
                JsonObject h = e.getValue().getAsJsonObject();
                HEAD.put(e.getKey(), Identifier.parse(h.get("item").getAsString()));
                List<String> models = new ArrayList<>();
                for (JsonElement m : h.getAsJsonArray("models")) models.add(m.getAsString());
                HEAD_MODELS.put(e.getKey(), models);
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("rigs").entrySet()) {
                JsonObject r = e.getValue().getAsJsonObject();
                List<Bone> bones = new ArrayList<>();
                for (JsonElement be : r.getAsJsonArray("bones")) {
                    JsonObject b = be.getAsJsonObject();
                    bones.add(new Bone(b.get("name").getAsString(), str(b, "parent"), b.get("root").getAsString(), floats(b.getAsJsonArray("off")),
                            floats(b.getAsJsonArray("rot")), str(b, "anim"), stack(str(b, "item")), stack(str(b, "glow")),
                            b.has("k") ? b.get("k").getAsFloat() : 1F, b.has("c0") ? floats(b.getAsJsonArray("c0")) : new float[]{8, 8, 8}));
                }
                RIGS.put(e.getKey(), new Rig(r.get("root").getAsString(), bones));
            }
            SETS.addAll(o.getAsJsonObject("equipment").keySet());
        } catch (Exception e) {
            Mmorpg.LOG.warn("[vanilla] cosmetics: {}", e.toString());
        }
        for (CosmeticDefs.Def d : CosmeticDefs.all().values()) {
            if (!"weapon".equals(d.slot()) || d.design() == null) continue;
            if (VanillaCosmetics.class.getResource("/assets/rpg/models/item/model/" + d.design() + ".json") != null) SKINS.add(d.id());
        }
        Mmorpg.LOG.info("[vanilla] cosmetics: head {}, rigs {}, armor sets {}, weapon skins {}", HEAD.size(), RIGS.size(), SETS.size(), SKINS.size());
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static ItemStack stack(String model) {
        if (model == null) return null;
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
        return s;
    }

    /** 머리에 얹는 그림 (모델 → 뼈 모델들 — VanillaPack 이 투구 정의를 감쌀 때) */
    static Map<String, List<String>> headModels() {
        return HEAD_MODELS;
    }

    /** 무기 스킨 키 → 손에 든 모델 (rpg:item/model/&lt;디자인&gt;) — VanillaPack */
    static Map<String, String> skinDesigns() {
        Map<String, String> m = new LinkedHashMap<>();
        for (CosmeticDefs.Def d : CosmeticDefs.all().values()) if (SKINS.contains(d.id())) m.put(d.id(), d.design());
        return m;
    }

    // ------------------------------------------------------------ 모습

    /** 이 엔티티의 장식 모습 (모르면 EMPTY — 어느 스레드에서나) */
    static Look lookOf(Entity e) {
        if (e == null) return Look.EMPTY;
        Look l = LOOKS.get(e.getUUID());
        return l == null ? Look.EMPTY : l;
    }

    /** 장식이 바뀜 (Cosmetics.broadcast · 들어옴 · 다시 살아남): 모습을 새로 읽고 보이는 것을 다시 보냄 — 서버 스레드 */
    public static void changed(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return;
        LOOKS.put(sp.getUUID(), Cosmetics.look(sp));
        refresh(sp);
    }

    private static void forget(ServerPlayer sp, MinecraftServer server) {
        LOOKS.remove(sp.getUUID());
        CosmeticHolder h = HOLDERS.remove(sp.getUUID());
        if (h != null) h.destroy();
        PENDING.remove(sp.getUUID());
        for (Preview p : new ArrayList<>(PREVIEWS.values())) if (p.owner().equals(sp.getUUID())) endPreview(p, server);
    }

    /** 보이는 것을 다시: 태운 표시 엔티티 · 다른 사람의 장비 그림 · 내 가방 (다른 창이 열려 있으면 닫은 뒤) */
    static void refresh(LivingEntity e) {
        Look look = lookOf(e);
        CosmeticHolder old = HOLDERS.remove(e.getUUID());
        if (old != null) old.destroy();
        if (CosmeticHolder.wanted(look)) {
            CosmeticHolder h = new CosmeticHolder(e, look);
            HOLDERS.put(e.getUUID(), h);
            EntityAttachment.ofTicking(h, e);
        }
        if (e.level() instanceof ServerLevel level) {
            List<Pair<EquipmentSlot, ItemStack>> all = new ArrayList<>();
            for (EquipmentSlot s : EquipmentSlot.values()) {
                if (s.getType() == EquipmentSlot.Type.HAND || s.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) all.add(Pair.of(s, e.getItemBySlot(s).copy()));
            }
            ClientboundSetEquipmentPacket p = new ClientboundSetEquipmentPacket(e.getId(), all);
            for (ServerPlayer viewer : level.players()) {
                if (viewer != e && VanillaClients.isVanilla(viewer) && viewer.distanceToSqr(e) < 160 * 160) viewer.connection.send(p);
            }
        }
        if (e instanceof ServerPlayer sp && VanillaClients.isVanilla(sp)) PENDING.add(sp.getUUID());
    }

    // ------------------------------------------------------------ 아이템 (VanillaItems.modifyBasePolymerItemStack 끝)

    /** 모드 없는 클라이언트에 가는 RPG 아이템 하나: 그 모습의 무기 스킨 · 갑옷 그림 (out = 바닐라 것) */
    static void convert(ItemStack out, ItemStack stack, PacketContext ctx, HolderLookup.Provider lookup) {
        View view = OVERRIDE.get();
        if (view == null) {
            ServerPlayer receiver = receiver(ctx);
            if (receiver == null) return;
            Look own = LOOKS.get(receiver.getUUID());
            if (own == null || own.isEmpty()) return;
            view = new View(own, receiver.isInvisible());
        }
        if (view.look().isEmpty()) return;
        if (lookup == null) lookup = SERVER_LOOKUP;
        if (lookup == null) return;
        Item item = stack.getItem();
        if (item instanceof RpgWeaponItem) {
            String skin = view.look().weaponSkin(weaponType(item));
            if (skin != null && SKINS.contains(skin)) out.set(DataComponents.CUSTOM_MODEL_DATA, cmd(skin));
            return;
        }
        if (!(item instanceof RpgArmorItem)) return;
        Equippable eq = out.get(DataComponents.EQUIPPABLE);
        if (eq == null) return;
        String slot = slotKey(eq.slot());
        if (slot == null) return;
        Visual v = visual(view.look(), slot, view.invisible());
        switch (v.kind()) {
            case HEAD_ITEM -> {
                out.set(DataComponents.CUSTOM_MODEL_DATA, cmd(v.value()));
                out.set(DataComponents.EQUIPPABLE, withAsset(eq, null, lookup));
            }
            case HIDE -> {
                if ("helmet".equals(slot)) out.set(DataComponents.CUSTOM_MODEL_DATA, cmd("hide"));
                out.set(DataComponents.EQUIPPABLE, withAsset(eq, null, lookup));
            }
            case SET -> out.set(DataComponents.EQUIPPABLE, withAsset(eq, Mmorpg.NS + ":" + v.value(), lookup));
            default -> {
            }
        }
    }

    /** 받는 사람 (연결이 없는 문맥 — 설명 만들기 등 — 이면 null) */
    private static ServerPlayer receiver(PacketContext ctx) {
        if (ctx == null) return null;
        try {
            return PolymerCommonUtils.getPlayer(ctx);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 레지스트리 (아이템을 만드는 곳이 넘겨주지 않을 때) — 서버 틱에서 */
    private static volatile HolderLookup.Provider SERVER_LOOKUP;

    static final int NONE = 0, HIDE = 1, SET = 2, HEAD_ITEM = 3;

    record Visual(int kind, String value) {}

    private static final Visual NOTHING = new Visual(NONE, null);

    /** 갑옷 칸 하나의 모습 (모드 CosmeticRender.extract 와 같은 차례): 머리에 얹는 장식 → 투구 스킨 · 숨김 → 갑옷 스킨 */
    static Visual visual(Look look, String slot, boolean invisible) {
        if ("helmet".equals(slot) && !invisible) {
            CosmeticDefs.Def hat = look.def("head");
            if (hat != null && hat.hideHelmet() && hat.model() != null && HEAD.containsKey(hat.model())) return new Visual(HEAD_ITEM, hat.model());
        }
        String v = look.get(slot);
        if (v == null) return NOTHING;
        if (Look.HIDE.equals(v)) return invisible ? NOTHING : new Visual(HIDE, null);
        CosmeticDefs.Def d = CosmeticDefs.get(v);
        if (d == null) return NOTHING;
        if ("helmet".equals(slot)) return d.model() != null && HEAD.containsKey(d.model()) ? new Visual(HEAD_ITEM, d.model()) : NOTHING;
        return d.set() != null && SETS.contains(d.set()) ? new Visual(SET, d.set()) : NOTHING;
    }

    static String slotKey(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> "helmet";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case FEET -> "boots";
            default -> null;
        };
    }

    static String weaponType(Item item) {
        return TYPES.computeIfAbsent(item, it -> {
            Weapon w = Weapons.parseWeapon(BuiltInRegistries.ITEM.getKey(it).toString());
            return w == null ? "" : w.type();
        });
    }

    private static CustomModelData cmd(String s) {
        return new CustomModelData(List.of(), List.of(), List.of(s), List.of());
    }

    /** 갑옷 그림 (asset_id) 만 바꾼 equippable (null = 그림 없음) — 코덱으로 (판마다 필드가 바뀌어도 그대로) */
    static Equippable withAsset(Equippable eq, String asset, HolderLookup.Provider lookup) {
        return EQUIP.computeIfAbsent(eq, k -> new ConcurrentHashMap<>()).computeIfAbsent(asset == null ? "" : asset, a -> {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
            JsonObject o = Equippable.CODEC.encodeStart(ops, eq).getOrThrow().getAsJsonObject();
            if (a.isEmpty()) o.remove("asset_id");
            else o.addProperty("asset_id", a);
            return Equippable.CODEC.parse(ops, o).getOrThrow();
        });
    }

    // ------------------------------------------------------------ 다른 사람의 장비 (VanillaPackets.rewrite)

    /** 장비 패킷 하나 (모드 없는 클라이언트에게): 그 엔티티의 모습으로 바닐라 아이템을 만들어 넣음 · 빈 칸에 장식 그림 */
    static Packet<?> equipment(ServerPlayer receiver, ClientboundSetEquipmentPacket p) {
        if (!VanillaClients.isVanilla(receiver)) return p;
        Entity e = receiver.level().getEntity(p.getEntity());
        Look look = lookOf(e);
        boolean rpg = false;
        for (Pair<EquipmentSlot, ItemStack> s : p.getSlots()) rpg |= VanillaSupport.ours(BuiltInRegistries.ITEM.getKey(s.getSecond().getItem()));
        if (!rpg && look.isEmpty()) return p;
        boolean invisible = e != null && e.isInvisible();
        HolderLookup.Provider lookup = receiver.registryAccess();
        List<Pair<EquipmentSlot, ItemStack>> out = new ArrayList<>();
        OVERRIDE.set(new View(look, invisible));
        try {
            for (Pair<EquipmentSlot, ItemStack> s : p.getSlots()) out.add(Pair.of(s.getFirst(), slotStack(receiver, s.getFirst(), s.getSecond(), look, invisible, lookup)));
        } finally {
            OVERRIDE.remove();
        }
        rewrites++;
        return new ClientboundSetEquipmentPacket(p.getEntity(), out);
    }

    private static ItemStack slotStack(ServerPlayer receiver, EquipmentSlot slot, ItemStack st, Look look, boolean invisible, HolderLookup.Provider lookup) {
        String key = slotKey(slot);
        Visual v = key == null || look.isEmpty() ? NOTHING : visual(look, key, invisible);
        boolean ours = !st.isEmpty() && VanillaSupport.ours(BuiltInRegistries.ITEM.getKey(st.getItem()));
        if (ours) {
            ItemStack c = PolymerItemUtils.getPolymerItemStack(st, receiver.getPacketContext(), lookup);
            if (slot == EquipmentSlot.HEAD) lastHead = v.kind() == HEAD_ITEM ? v.value() : "";
            return c;
        }
        // 빈 칸 · 바닐라 아이템: 머리에 얹는 그림 · 갑옷 스킨은 그 그림만, 숨김은 빈 칸
        switch (v.kind()) {
            case HEAD_ITEM -> {
                lastHead = v.value();
                ItemStack hat = new ItemStack(Items.PAPER);
                hat.set(DataComponents.ITEM_MODEL, HEAD.get(v.value()));
                return hat;
            }
            case HIDE -> {
                return ItemStack.EMPTY;
            }
            case SET -> {
                ItemStack base = st.isEmpty() ? new ItemStack(Items.PAPER) : st.copy();
                Equippable eq = base.get(DataComponents.EQUIPPABLE);
                base.set(DataComponents.EQUIPPABLE, eq != null ? withAsset(eq, Mmorpg.NS + ":" + v.value(), lookup) : fakeEquippable(slot, v.value(), lookup));
                return base;
            }
            default -> {
                return st;
            }
        }
    }

    private static Equippable fakeEquippable(EquipmentSlot slot, String set, HolderLookup.Provider lookup) {
        JsonObject o = new JsonObject();
        o.addProperty("slot", slot.getSerializedName());
        o.addProperty("asset_id", Mmorpg.NS + ":" + set);
        return Equippable.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, lookup), o).getOrThrow();
    }

    // ------------------------------------------------------------ 틱: 가방 다시 보냄 · 오라 · 미리보기

    private static void tick(MinecraftServer server) {
        if (SERVER_LOOKUP == null) SERVER_LOOKUP = server.registryAccess();
        if (!PENDING.isEmpty()) {
            for (UUID id : new ArrayList<>(PENDING)) {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp == null) {
                    PENDING.remove(id);
                    continue;
                }
                if (sp.containerMenu != sp.inventoryMenu) continue;
                sp.inventoryMenu.sendAllDataToRemote();
                PENDING.remove(id);
            }
        }
        long now = server.getTickCount();
        for (Preview p : new ArrayList<>(PREVIEWS.values())) {
            Mannequin m = p.body();
            ServerPlayer owner = server.getPlayerList().getPlayer(p.owner());
            if (now >= p.end() || m.isRemoved() || owner == null || owner.level() != m.level() || owner.distanceToSqr(m) > 24 * 24) {
                endPreview(p, server);
                continue;
            }
            float yaw = Mth.wrapDegrees(m.getYRot() + PREVIEW_SPIN);
            m.setYRot(yaw);
            m.setYHeadRot(yaw);
            m.setYBodyRot(yaw);
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer sp : level.players()) particles(level, sp, now);
            for (Preview p : PREVIEWS.values()) if (p.body().level() == level) particles(level, p.body(), now);
        }
    }

    /** 오라 · 날개 가루 (모습에 있을 때) — 모드 없는 클라이언트에게만 (그 사람 자신은 빼고 — 모드도 1인칭에서는 내 오라를 뿌리지 않음) */
    private static void particles(ServerLevel level, LivingEntity e, long now) {
        Look look = lookOf(e);
        if (look.isEmpty() || e.isInvisible() || e.isSpectator() || e.isRemoved()) return;
        CosmeticDefs.Def aura = look.def("aura");
        CosmeticDefs.Def back = look.def("back");
        if ((aura == null || aura.aura() == null) && (back == null || back.dust() == null)) return;
        List<ServerPlayer> viewers = new ArrayList<>(2);
        for (ServerPlayer v : level.players()) {
            if (v != e && VanillaClients.isVanilla(v) && v.distanceToSqr(e) < 48 * 48) viewers.add(v);
        }
        if (viewers.isEmpty()) return;
        Emit emit = (type, x, y, z, n, dx, dy, dz, sp) -> {
            for (ServerPlayer v : viewers) level.sendParticles(v, type, false, false, x, y, z, n, dx, dy, dz, sp);
        };
        int tick = (int) (now % 1_000_000);
        if (aura != null && aura.aura() != null) VanillaAuras.aura(emit, e, aura.aura(), tick, level.getRandom());
        if (back != null && back.dust() != null) VanillaAuras.dust(emit, e, back.dust(), tick, level.getRandom());
    }

    /** 파티클 하나를 보는 사람들에게 (sendParticles 와 같은 인자) */
    @FunctionalInterface
    interface Emit {
        void send(ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed);
    }

    // ------------------------------------------------------------ 미리보기 (꾸미기 창)

    /** 내 앞에 내 모습 (스킨 · 갑옷 · 손의 무기 · 장식) 의 마네킹을 15초 — 천천히 돌며 앞 · 옆 · 뒤를 보여 줌 */
    public static Mannequin preview(ServerPlayer sp) {
        MinecraftServer server = sp.level().getServer();
        for (Preview p : new ArrayList<>(PREVIEWS.values())) if (p.owner().equals(sp.getUUID())) endPreview(p, server);
        ServerLevel level = sp.level();
        Vec3 dir = sp.getViewVector(1F);
        double len = Math.hypot(dir.x, dir.z);
        if (len < 1e-6) {
            dir = new Vec3(0, 0, 1);
            len = 1;
        }
        Vec3 at = new Vec3(sp.getX() + dir.x / len * 2.6, sp.getY(), sp.getZ() + dir.z / len * 2.6);
        Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (m == null) return null;
        float yaw = (float) Math.toDegrees(Math.atan2(-(sp.getX() - at.x), sp.getZ() - at.z));
        m.snapTo(at.x, at.y, at.z, yaw, 0F);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);
        NpcData.setup(m);
        try {
            ((MannequinAccessor) m).mmorpg$setProfile(ResolvableProfile.createUnresolved(sp.getGameProfile().name()));
        } catch (RuntimeException ex) {
            Mmorpg.LOG.warn("[vanilla] preview profile: {}", ex.toString());
        }
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (s.getType() == EquipmentSlot.Type.HAND || s.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) m.setItemSlot(s, sp.getItemBySlot(s).copy());
        }
        Look look = Cosmetics.look(sp);
        LOOKS.put(m.getUUID(), look);
        level.addFreshEntity(m);
        PREVIEWS.put(m.getUUID(), new Preview(sp.getUUID(), m, server.getTickCount() + PREVIEW_TICKS));
        refresh(m);
        Cosmetics.showLook(server, m.getUUID(), look); // 모드 클라이언트도 이 마네킹에 장식을 그림
        return m;
    }

    private static void endPreview(Preview p, MinecraftServer server) {
        PREVIEWS.remove(p.body().getUUID());
        CosmeticHolder h = HOLDERS.remove(p.body().getUUID());
        if (h != null) h.destroy();
        LOOKS.remove(p.body().getUUID());
        Cosmetics.showLook(server, p.body().getUUID(), null);
        if (!p.body().isRemoved()) p.body().discard();
    }

    /** 시험용: 이 사람의 미리보기 마네킹 (없으면 null) */
    static Mannequin previewOf(ServerPlayer sp) {
        for (Preview p : PREVIEWS.values()) if (p.owner().equals(sp.getUUID())) return p.body();
        return null;
    }

    /** 시험용: 태운 장식 (칭호 글 · 뼈 수) */
    static String holderInfo(Entity e) {
        CosmeticHolder h = HOLDERS.get(e.getUUID());
        if (h == null) return "";
        return (h.title != null ? "title " : "") + "bones " + h.parts.size();
    }

    // ------------------------------------------------------------ 태운 표시 엔티티 (칭호 · 고리 · 등 장식)

    /** 뼈 하나의 표시 엔티티 (그림 · 빛 겹) */
    record Part(Rig rig, int index, ItemDisplayElement base, ItemDisplayElement glow) {}

    static final class CosmeticHolder extends ElementHolder {
        final LivingEntity entity;
        final Look look;
        TextDisplayElement title;
        final List<Part> parts = new ArrayList<>();
        private boolean shown = true;
        private boolean discrete;
        private int age;
        private int every = 2;

        static boolean wanted(Look look) {
            CosmeticDefs.Def t = look.def("title");
            CosmeticDefs.Def head = look.def("head");
            CosmeticDefs.Def back = look.def("back");
            return (t != null && t.title() != null) || (head != null && !head.hideHelmet() && head.model() != null && RIGS.containsKey(head.model()))
                    || (back != null && back.model() != null && RIGS.containsKey(back.model()));
        }

        CosmeticHolder(LivingEntity entity, Look look) {
            this.entity = entity;
            this.look = look;
            CosmeticDefs.Def t = look.def("title");
            if (t != null && t.title() != null) {
                title = new TextDisplayElement(Component.literal(BedrockText.toJava(t.title())));
                title.setBillboardMode(Display.BillboardConstraints.CENTER);
                title.setBackground(0x40000000);
                title.setSeeThrough(true);
                title.setShadow(false);
                title.setTeleportDuration(0);
                title.ignorePositionUpdates();
                addPassengerElement(title);
            }
            CosmeticDefs.Def head = look.def("head");
            if (head != null && !head.hideHelmet() && head.model() != null) rig(RIGS.get(head.model()));
            CosmeticDefs.Def back = look.def("back");
            if (back != null && back.model() != null) rig(RIGS.get(back.model()));
            pose();
        }

        private void rig(Rig rig) {
            if (rig == null) return;
            for (int i = 0; i < rig.bones().size(); i++) {
                Bone b = rig.bones().get(i);
                if (b.item() == null) continue;
                parts.add(new Part(rig, i, element(b.item()), b.glow() == null ? null : element(b.glow())));
            }
        }

        private ItemDisplayElement element(ItemStack stack) {
            ItemDisplayElement e = new ItemDisplayElement(stack.copy());
            e.setInterpolationDuration(every);
            e.setDisplaySize(2F, 2F);
            e.ignorePositionUpdates();
            addPassengerElement(e);
            return e;
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            if (VanillaClients.isModded(player.getPlayer())) return false;
            return super.startWatching(player);
        }

        /** 이미 보이는 사람이 새로 보기 시작하면 (장식을 바꿈) 태운 것을 알려 줌 (처음 볼 때는 Polymer 가 보냄) */
        @Override
        protected void startWatchingExtraPackets(ServerGamePacketListenerImpl player, Consumer<Packet<ClientGamePacketListener>> packets) {
            packets.accept(new ClientboundSetPassengersPacket(entity));
        }

        @Override
        protected void onTick() {
            if (getWatchingPlayers().isEmpty()) return;
            age++;
            int rate = rate();
            if (rate != every) {
                every = rate;
                for (Part p : parts) {
                    p.base().setInterpolationDuration(rate);
                    if (p.glow() != null) p.glow().setInterpolationDuration(rate);
                }
            }
            if (age % every != 0) return;
            pose();
        }

        private int rate() {
            double best = Double.MAX_VALUE;
            for (ServerGamePacketListenerImpl c : getWatchingPlayers()) best = Math.min(best, c.getPlayer().distanceToSqr(entity));
            return best < 24 * 24 ? 2 : best < 48 * 48 ? 4 : 8;
        }

        /** 보일지: 투명 · 관전 · 누움 · 헤엄 · 겉날개 · 회전 공격이면 숨김 (몸이 누워 그림이 맞지 않음) */
        private boolean visibleNow() {
            if (entity.isInvisible() || entity.isSpectator() || entity.isRemoved()) return false;
            Pose p = entity.getPose();
            return p == Pose.STANDING || p == Pose.CROUCHING;
        }

        void pose() {
            boolean show = visibleNow();
            if (show != shown) {
                shown = show;
                for (Part p : parts) {
                    Bone b = p.rig().bones().get(p.index());
                    p.base().setItem(show ? b.item().copy() : ItemStack.EMPTY);
                    if (p.glow() != null) p.glow().setItem(show ? b.glow().copy() : ItemStack.EMPTY);
                }
                if (title != null) title.setText(show ? Component.literal(BedrockText.toJava(look.def("title").title())) : Component.empty());
            }
            if (!show) return;
            boolean crouch = entity.isCrouching();
            float h = entity.getDimensions(entity.getPose()).height();
            if (title != null) {
                boolean score = entity.level().getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME) != null && entity instanceof ServerPlayer;
                title.setTranslation(new org.joml.Vector3f(0, TITLE_Y + (score ? LINE : 0), 0));
                if (crouch != discrete) {
                    discrete = crouch;
                    title.setSeeThrough(!crouch);
                }
                title.startInterpolationIfDirty();
            }
            if (parts.isEmpty()) return;
            float s = entity.getScale();
            // LivingEntityRenderer 와 같은 차례 (j 공간): 태운 자리 (몸 높이) 에서 발 · 웅크림 내림 · 몸 방향 · 사람 크기 0.9375 · 모델 위 1.501
            Matrix4f root = new Matrix4f().translation(0, -h + (crouch ? -2F / 16F * s : 0F), 0)
                    .rotateY((float) Math.toRadians(180F - entity.yBodyRot))
                    .scale(0.9375F * s)
                    .translate(0, 1.501F, 0);
            Matrix4f body = new Matrix4f(root);
            if (crouch) body.translate(0, -3.2F / 16F, 0).rotateX(-0.5F);
            Matrix4f head = new Matrix4f(root);
            if (crouch) head.translate(0, -4.2F / 16F, 0);
            head.rotateY((float) -Math.toRadians(Mth.wrapDegrees(entity.getYHeadRot() - entity.yBodyRot)))
                    .rotateX((float) -Math.toRadians(entity.getXRot()));
            double speed = Math.max(0, Math.min(1, entity.walkAnimation.speed()));
            float ageTicks = entity.tickCount;
            Map<Rig, Matrix4f[]> worlds = new HashMap<>();
            for (Part p : parts) {
                Matrix4f[] w = worlds.computeIfAbsent(p.rig(), r -> chain(r, "head".equals(r.root()) ? head : body, ageTicks, speed));
                Bone b = p.rig().bones().get(p.index());
                Matrix4f d = new Matrix4f(w[p.index()]).scale(1F / b.k())
                        .translate(-(b.c0()[0] - 8F) / 16F, -(b.c0()[1] - 8F) / 16F, -(b.c0()[2] - 8F) / 16F);
                p.base().setTransformation(d);
                p.base().startInterpolationIfDirty();
                if (p.glow() != null) {
                    p.glow().setTransformation(d);
                    p.glow().startInterpolationIfDirty();
                }
            }
        }

        /** 뼈 사슬 (j 공간): T(자리 / 16) · Rz(z) · Ry(−y) · Rx(−x) — 움직이는 뼈는 모드 PieceModel.setupAnim 과 같은 각을 더함 */
        private static Matrix4f[] chain(Rig rig, Matrix4f base, float age, double speed) {
            List<Bone> bones = rig.bones();
            Matrix4f[] w = new Matrix4f[bones.size()];
            Map<String, Integer> at = new HashMap<>();
            for (int i = 0; i < bones.size(); i++) {
                Bone b = bones.get(i);
                at.put(b.name(), i);
                Integer pi = b.parent() == null ? null : at.get(b.parent());
                Matrix4f m = new Matrix4f(pi == null ? base : w[pi]);
                float[] r = {b.rot()[0], b.rot()[1], b.rot()[2]};
                float oy = b.off()[1];
                if (b.anim() != null) {
                    switch (b.anim()) {
                        case "cape" -> r[0] += (float) (speed * 35 + Math.sin(Math.toRadians(age / 20.0 * 100)) * 2);
                        case "wing_left", "wing_right" -> {
                            double flap = Math.sin(age * (0.14 + speed * 0.22)) * (9 + speed * 14);
                            r[1] += (float) ((b.anim().equals("wing_right") ? 1 : -1) * (flap + speed * 6));
                        }
                        case "flutter_left", "flutter_right", "flutter_low_left", "flutter_low_right" -> {
                            boolean low = b.anim().startsWith("flutter_low");
                            double flap = Math.sin(age * (0.95 + speed * 0.45) - (low ? 0.9 : 0)) * (14 + speed * 10) * (low ? 0.7 : 1);
                            r[1] += (float) ((b.anim().endsWith("right") ? 1 : -1) * (flap + speed * 8));
                        }
                        case "spin" -> r[1] += (float) (age * 2.0 % 360);
                        case "bob" -> oy -= (float) (Math.sin(age * 0.09) * 0.6);
                        default -> {
                        }
                    }
                }
                m.translate(b.off()[0] / 16F, oy / 16F, b.off()[2] / 16F)
                        .rotateZ((float) Math.toRadians(r[2])).rotateY((float) -Math.toRadians(r[1])).rotateX((float) -Math.toRadians(r[0]));
                w[i] = m;
            }
            return w;
        }
    }
}
