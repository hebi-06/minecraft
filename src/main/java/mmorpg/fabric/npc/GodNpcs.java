package mmorpg.fabric.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.mixin.MannequinAccessor;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 원소신 NPC 다섯 (2026-11-28 — 사용자 "그냥 이런 느낌에 npc 넣기만 할래" · 고른 방식 ① 사람 모양 NPC + 스킨, 기준 그림은 사용자가 AI 로 만든 다섯 장:
 * claude/god-concept-art.md). 라에타 · 니바리아 · 마레아 · 포르티아 · 그라비스.
 * <ul>
 * <li>몸 = RPG NPC 마네킹 (NpcData — 이름 · 인사말 · 퀘스트 · 설정 창 그대로) + 태그 rpg_god · rpg_god_&lt;신&gt;</li>
 * <li>스킨 · 망토 = 마네킹 프로필의 스킨 패치 (texture · cape · model) — 그림은 리소스 팩 (assets/rpg/textures/entity/god) 이라 모장 서버를 거치지 않고,
 * 모드 없는 클라이언트도 팩만 받으면 보임</li>
 * <li>머리 장식 · 두 손 = 투구 칸 · 손에 든 종이 (item_model rpg:god/&lt;소품&gt; — 바닐라가 머리 · 팔 움직임 그대로 그림, 갑옷이 아니라 숨기지 않음)</li>
 * <li>둘레 소품 (북 고리 · 얼음 보관품 · 물 고리 · 떠오르는 돌 …) = 마네킹에 붙인 표시 엔티티 (Polymer 홀더 — 모드 · 바닐라 클라이언트 모두), 2틱마다 돎</li>
 * <li>파티클 = 서버가 뿌림 (둘레 32칸 안의 사람에게)</li>
 * </ul>
 * 배치 · 그림은 tools/god_npcs.py 가 만든 /mmorpg/gods.json. 세우기: /godnpc &lt;신&gt; (/원소신) · NPC 관리 창 「원소신 NPC 세우기」 — 내 앞 2칸, 나를 봄.
 */
public final class GodNpcs {
    private GodNpcs() {}

    public static final String RESOURCE = "/mmorpg/gods.json";
    public static final String TAG = "rpg_god";
    public static final String TAG_PREFIX = "rpg_god_";
    /** 사람 모델 크기 (AvatarRenderer — 0.9375) */
    static final float SCALE = 0.9375F;
    /** 아이템 표시는 모델을 Y 둘레로 180° 돌려 그림 — 되돌림 (VanillaModels.ITEM_FLIP 과 같음) */
    static final float ITEM_FLIP = (float) Math.PI;
    /** 둘레 소품을 다시 보내는 틱 (보간도 같은 틱) */
    static final int EVERY = 2;

    /** 둘레 소품 변환 하나: t 옮김 · r 돌림 · spin 계속 돎 · bob 오르내림 · s 크기 (j 공간 px — x+ 오른쪽 · y+ 위 · z+ 뒤, 발 = 원점) */
    public record Op(String kind, String axis, float a, float b, float c) {}

    public record Prop(Identifier model, float k, List<Op> ops) {}

    /** 파티클: kind (dust · spark · snow · end_rod · drip · glow · flame · falling), every 틱마다 count 개, at · spread (j 공간 px) */
    public record Spark(String kind, int rgb, float size, int every, int count, float[] at, float[] spread) {}

    public record God(String id, String name, String title, String greeting, Identifier skin, boolean slim, Identifier cape, Identifier head,
            Identifier main, Identifier off, List<Prop> props, List<Spark> particles) {}

    private static Map<String, God> gods;
    private static final Map<UUID, GodHolder> HOLDERS = new HashMap<>();

    // ------------------------------------------------------------ 목록

    public static synchronized Map<String, God> all() {
        if (gods == null) gods = load();
        return Collections.unmodifiableMap(gods);
    }

    /** id (laeta …) 또는 이름 (라에타 …) */
    public static God get(String key) {
        if (key == null) return null;
        String k = key.strip();
        God g = all().get(k.toLowerCase(Locale.ROOT));
        if (g != null) return g;
        for (God x : all().values()) if (x.name().equals(k)) return x;
        return null;
    }

    /** 이 엔티티가 원소신 NPC 면 그 신 */
    public static God godOf(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof Mannequin) || !e.entityTags().contains(TAG)) return null;
        for (String t : e.entityTags()) if (t.startsWith(TAG_PREFIX)) return all().get(t.substring(TAG_PREFIX.length()));
        return null;
    }

    private static Map<String, God> load() {
        JsonObject root;
        try (InputStream in = GodNpcs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " 이 없음");
            root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, God> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("gods").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<Prop> props = new ArrayList<>();
            for (JsonElement pe : o.getAsJsonArray("props")) {
                JsonObject p = pe.getAsJsonObject();
                List<Op> ops = new ArrayList<>();
                for (JsonElement oe : p.getAsJsonArray("ops")) {
                    JsonArray a = oe.getAsJsonArray();
                    String kind = a.get(0).getAsString();
                    switch (kind) {
                        case "t" -> ops.add(new Op(kind, null, a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()));
                        case "r" -> ops.add(new Op(kind, a.get(1).getAsString(), a.get(2).getAsFloat(), 0, 0));
                        case "spin" -> ops.add(new Op(kind, a.get(1).getAsString(), a.get(2).getAsFloat(), a.get(3).getAsFloat(), 0));
                        case "bob" -> ops.add(new Op(kind, null, a.get(1).getAsFloat(), a.get(2).getAsFloat(), a.get(3).getAsFloat()));
                        case "s" -> ops.add(new Op(kind, null, a.get(1).getAsFloat(), 0, 0));
                        default -> throw new IllegalStateException("gods.json op " + kind);
                    }
                }
                props.add(new Prop(id(p.get("model").getAsString()), p.has("k") ? p.get("k").getAsFloat() : 1F, List.copyOf(ops)));
            }
            List<Spark> sparks = new ArrayList<>();
            for (JsonElement se : o.getAsJsonArray("particles")) {
                JsonObject s = se.getAsJsonObject();
                int rgb = s.has("color") ? Integer.parseInt(s.get("color").getAsString().substring(1), 16) : 0xFFFFFF;
                sparks.add(new Spark(s.get("kind").getAsString(), rgb, s.has("size") ? s.get("size").getAsFloat() : 1F, Math.max(1, s.get("every").getAsInt()),
                        Math.max(1, s.get("count").getAsInt()), floats(s.getAsJsonArray("at")), floats(s.getAsJsonArray("spread"))));
            }
            out.put(e.getKey(), new God(e.getKey(), o.get("name").getAsString(), o.get("title").getAsString(), o.get("greeting").getAsString(),
                    id(o.get("skin").getAsString()), o.get("slim").getAsBoolean(), optId(o, "cape"), id(o.get("head").getAsString()), optId(o, "main"),
                    optId(o, "off"), List.copyOf(props), List.copyOf(sparks)));
        }
        return out;
    }

    private static Identifier id(String s) {
        return Identifier.parse(s);
    }

    private static Identifier optId(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? id(o.get(k).getAsString()) : null;
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[3];
        for (int i = 0; i < 3 && a != null && i < a.size(); i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /** 아이템 모델만 바꾼 종이 (머리 · 손 · 둘레 소품 — 모드 없는 클라이언트도 리소스 팩의 rpg:god/&lt;소품&gt; 을 그림) */
    public static ItemStack stack(Identifier model) {
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.ITEM_MODEL, model);
        return s;
    }

    /** 스킨 패치 프로필 (이름 · UUID 없이 그림만 — 모장 서버에 묻지 않음): {"texture": …, "cape": …, "model": "slim" | "wide"} */
    public static ResolvableProfile profile(God g) {
        JsonObject o = new JsonObject();
        o.addProperty("texture", g.skin().toString());
        if (g.cape() != null) o.addProperty("cape", g.cape().toString());
        o.addProperty("model", g.slim() ? "slim" : "wide");
        return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, o).getOrThrow();
    }

    // ------------------------------------------------------------ 세우기

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(GodNpcs::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (GodHolder h : HOLDERS.values()) h.destroy();
            HOLDERS.clear();
        });
    }

    /** sp 앞 2칸에 신을 세워 sp 를 보게 (NPC 설정 창 · 퀘스트는 다른 NPC 와 같음) */
    public static Mannequin spawn(ServerPlayer sp, God g) {
        Mannequin m = NpcData.spawnAt(sp.level(), NpcData.spotNear(sp), sp.getEyePosition());
        dress(m, g);
        Vec3 head = m.getEyePosition();
        SkillFx.vanilla(sp.level(), "minecraft:totem_particle", new Vec3(head.x, head.y + 0.6, head.z));
        SkillFx.playFor(sp, "random.orb", 1.2, 0.8);
        return m;
    }

    /** 마네킹을 그 신으로 (이름 · 이명 · 인사말 · 스킨 · 망토 · 머리 장식 · 두 손) — 다시 불러도 같음 */
    public static void dress(Mannequin m, God g) {
        for (String t : new ArrayList<>(m.entityTags())) if (t.startsWith(TAG_PREFIX)) m.removeTag(t);
        m.addTag(TAG);
        m.addTag(TAG_PREFIX + g.id());
        NpcData.setKey(m, "god_" + g.id());
        NpcData.setName(m, g.name());
        NpcData.setGreeting(m, g.greeting());
        try {
            MannequinAccessor a = (MannequinAccessor) m;
            a.mmorpg$setProfile(profile(g));
            a.mmorpg$setDescription(Component.literal("§6" + g.title()));
            a.mmorpg$setHideDescription(false);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[gods] dress {}: {}", g.id(), e.toString());
        }
        m.setItemSlot(EquipmentSlot.HEAD, stack(g.head()));
        m.setItemSlot(EquipmentSlot.MAINHAND, g.main() == null ? ItemStack.EMPTY : stack(g.main()));
        m.setItemSlot(EquipmentSlot.OFFHAND, g.off() == null ? ItemStack.EMPTY : stack(g.off()));
        NpcData.refresh(m, Npcs.badgeCount.applyAsInt(NpcData.key(m)));
        GodHolder old = HOLDERS.remove(m.getUUID());
        if (old != null) old.destroy();
    }

    // ------------------------------------------------------------ 명령: /godnpc · /원소신

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("godnpc", "원소신")) {
            d.register(Commands.literal(name)
                    .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(GodNpcs::list)
                    .then(Commands.argument("god", StringArgumentType.greedyString())
                            .suggests((c, b) -> {
                                List<String> k = new ArrayList<>();
                                for (God g : all().values()) {
                                    k.add(g.id());
                                    k.add(g.name());
                                }
                                return SharedSuggestionProvider.suggest(k, b);
                            })
                            .executes(GodNpcs::spawnCmd)));
        }
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        StringBuilder sb = new StringBuilder("§6[원소신 NPC] §f/godnpc 이름§7 → 내 앞 2칸에 세움 (설정 · 퀘스트 · 지우기는 NPC 설정 도구)");
        for (God g : all().values()) sb.append("\n§e").append(g.name()).append(" §7(").append(g.id()).append(") — ").append(g.title());
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int spawnCmd(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        God g = get(StringArgumentType.getString(c, "god"));
        if (g == null) {
            c.getSource().sendFailure(Component.literal("모르는 신입니다. /godnpc 로 목록을 보세요."));
            return 0;
        }
        spawn(sp, g);
        c.getSource().sendSuccess(() -> Component.literal("§6[원소신 NPC] §e" + g.name() + " §7(" + g.title() + ")§f을(를) 세웠습니다."), true);
        return 1;
    }

    // ------------------------------------------------------------ 둘레 소품 · 파티클

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 20 == 0) {
            HOLDERS.entrySet().removeIf(e -> {
                if (e.getValue().entity.isRemoved()) {
                    e.getValue().destroy();
                    return true;
                }
                return false;
            });
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                for (Mannequin m : sp.level().getEntitiesOfClass(Mannequin.class, sp.getBoundingBox().inflate(72), e -> e.entityTags().contains(TAG))) {
                    if (HOLDERS.containsKey(m.getUUID())) continue;
                    God g = godOf(m);
                    if (g != null) attach(m, g);
                }
            }
        }
        for (GodHolder h : HOLDERS.values()) h.particles(now);
    }

    /** 표시 엔티티 홀더를 붙임 (둘레 소품이 없는 신도 파티클을 위해 붙임) */
    static GodHolder attach(Mannequin m, God g) {
        GodHolder h = new GodHolder(m, g);
        HOLDERS.put(m.getUUID(), h);
        EntityAttachment.ofTicking(h, m);
        return h;
    }

    /** 시험용: 붙은 둘레 소품 수 (없으면 −1) */
    public static int propCount(Mannequin m) {
        GodHolder h = HOLDERS.get(m.getUUID());
        return h == null ? -1 : h.els.size();
    }

    /** 시험용: 지금 붙일 수 있는 신 NPC 에 바로 붙임 (사람이 다가오기를 기다리지 않고) */
    public static void attachNow(Mannequin m) {
        God g = godOf(m);
        if (g != null && !HOLDERS.containsKey(m.getUUID())) attach(m, g);
    }

    /** 신 둘레 변환 (j 공간 px → 엔티티 기준 블록): Ry(180° − 몸 방향) · 크기 0.9375 / 16 */
    static Matrix4f root(Mannequin m) {
        return new Matrix4f().rotateY((float) Math.toRadians(180F - m.yBodyRot)).scale(SCALE / 16F);
    }

    static Matrix4f ops(Matrix4f m, List<Op> ops, float t) {
        for (Op o : ops) {
            switch (o.kind()) {
                case "t" -> m.translate(o.a(), o.b(), o.c());
                case "r" -> rot(m, o.axis(), o.a());
                case "spin" -> rot(m, o.axis(), o.b() + o.a() * t);
                case "bob" -> m.translate(0F, (float) (o.a() * Math.sin(o.c() + o.b() * t)), 0F);
                case "s" -> m.scale(o.a());
                default -> {}
            }
        }
        return m;
    }

    private static void rot(Matrix4f m, String axis, float deg) {
        float r = (float) Math.toRadians(deg % 360F);
        switch (axis) {
            case "x" -> m.rotateX(r);
            case "y" -> m.rotateY(r);
            default -> m.rotateZ(r);
        }
    }

    static final class GodHolder extends ElementHolder {
        final Mannequin entity;
        final God god;
        final List<ItemDisplayElement> els = new ArrayList<>();
        private int age;

        GodHolder(Mannequin entity, God god) {
            this.entity = entity;
            this.god = god;
            for (Prop p : god.props()) {
                ItemDisplayElement e = new ItemDisplayElement(stack(p.model()));
                e.setInterpolationDuration(EVERY);
                e.setDisplaySize(3F, 3F);
                addElement(e);
                els.add(e);
            }
            pose(0);
        }

        @Override
        protected void onTick() {
            age++;
            if (getWatchingPlayers().isEmpty() || age % EVERY != 0) return;
            pose(age + EVERY);
        }

        void pose(float t) {
            for (int i = 0; i < els.size(); i++) {
                Prop p = god.props().get(i);
                Matrix4f m = ops(root(entity), p.ops(), t).scale(16F * p.k()).rotateY(ITEM_FLIP);
                els.get(i).setTransformation(m);
                els.get(i).startInterpolationIfDirty();
            }
        }

        void particles(long now) {
            if (entity.isRemoved() || entity.isInvisible() || !(entity.level() instanceof ServerLevel level)) return;
            Matrix4f r = null;
            for (Spark s : god.particles()) {
                if (now % s.every() != 0) continue;
                if (r == null) {
                    if (level.getNearestPlayer(entity, 32) == null) return;
                    r = root(entity);
                }
                Vector3f at = r.transformPosition(new Vector3f(s.at()[0], s.at()[1], s.at()[2]));
                float k = SCALE / 16F;
                level.sendParticles(particle(s), entity.getX() + at.x, entity.getY() + at.y, entity.getZ() + at.z, s.count(),
                        s.spread()[0] * k, s.spread()[1] * k, s.spread()[2] * k, s.kind().equals("spark") ? 0.02 : 0.0);
            }
        }
    }

    static ParticleOptions particle(Spark s) {
        return switch (s.kind()) {
            case "spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "snow" -> ParticleTypes.SNOWFLAKE;
            case "end_rod" -> ParticleTypes.END_ROD;
            case "drip" -> ParticleTypes.FALLING_WATER;
            case "glow" -> ParticleTypes.GLOW;
            case "flame" -> ParticleTypes.SMALL_FLAME;
            case "falling" -> new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
            default -> new DustParticleOptions(s.rgb(), s.size());
        };
    }
}
