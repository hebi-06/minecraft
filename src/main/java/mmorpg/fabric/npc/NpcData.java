package mmorpg.fabric.npc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Props;
import mmorpg.fabric.mixin.MannequinAccessor;
import mmorpg.fabric.skill.Kit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * NPC 데이터 (npcdata.js): RPG NPC 의 몸은 마네킹 (사람 모양 · 스킨 — 베드락 minecraft:npc 대신). 엔티티 속성 (Props) 과 태그에 저장:
 * rpg:npc_key (퀘스트와 잇는 번호, 예 n3 · main_1) · rpg:npc_name (이름 — 이름표 앞의 [!] · [상점] 표시는 자동) · rpg:greet (인사말),
 * 태그 rpg_npc / rpg_shop / rpg_shop_lv_&lt;레벨&gt; / rpg_quest / rpg_smith / rpg_runesmith.
 * 세계 속성: rpg:npcs (번호 → 이름 — 퀘스트 편집 창의 NPC 고르기), rpg:npc_pos (번호 → 마지막 자리 — 길 안내), rpg:main_npc_ids (메인 NPC 번호 → 세운 엔티티).
 */
public final class NpcData {
    private NpcData() {}

    public static final String TAG = "rpg_npc";
    private static final String P_KEY = "rpg:npc_key";
    private static final String P_NAME = "rpg:npc_name";
    private static final String P_GREET = "rpg:greet";
    private static final String W_SEQ = "rpg:npc_seq";
    private static final String W_REG = "rpg:npcs";
    private static final String W_POS = "rpg:npc_pos";
    private static final String W_MAIN = "rpg:main_npc_ids";
    private static final List<Double> LEVELS = Rules.get().numbers("LEVELS");

    /** 마지막으로 본 NPC 자리 (id = 엔티티 UUID, dim = 차원 "minecraft:overworld") */
    public record Spot(String id, String dim, double x, double y, double z) {
        public Vec3 vec() {
            return new Vec3(x, y, z);
        }

        JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("dim", dim);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("z", z);
            return o;
        }

        static Spot of(JsonElement e) {
            if (e == null || !e.isJsonObject()) return null;
            JsonObject o = e.getAsJsonObject();
            try {
                return new Spot(o.get("id").getAsString(), o.get("dim").getAsString(), o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------ 엔티티

    /** NPC 몸 (마네킹 — 애드온 NPC_TYPE) 인지 */
    public static boolean isNpcEntity(Entity e) {
        return e instanceof Mannequin;
    }

    /** RPG NPC (태그 rpg_npc) 인지 */
    public static boolean isRpgNpc(Entity e) {
        return isNpcEntity(e) && e.entityTags().contains(TAG);
    }

    public static String stripFormat(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    /** 예전 이름표 ("[상점] 무기상 (10렙)") 에서 이름만 */
    private static String legacyName(Entity npc) {
        Component c = npc.getCustomName();
        String s = c == null ? "" : stripFormat(c.getString());
        return s.replaceAll("\\[(상점|!|대장장이|룬술사)\\]", "").replaceAll("\\(\\d+렙\\)", "").trim();
    }

    private static MinecraftServer server(Entity e) {
        return e.level().getServer();
    }

    /** NPC 번호 (create 면 없을 때 새로 — n1, n2 …) */
    public static String key(Entity npc, boolean create) {
        String k = Props.get(npc, P_KEY);
        if (k != null && !k.isEmpty()) return k;
        if (!create) return "";
        MinecraftServer s = server(npc);
        int n = (int) Props.worldNum(s, W_SEQ, 0) + 1;
        Props.setWorld(s, W_SEQ, String.valueOf(n));
        String key = "n" + n;
        Props.set(npc, P_KEY, key);
        return key;
    }

    public static String key(Entity npc) {
        return key(npc, false);
    }

    /** 정해 둔 번호 (메인 퀘스트 NPC: main_1 …) */
    public static void setKey(Entity npc, String key) {
        Props.set(npc, P_KEY, key);
    }

    public static String name(Entity npc) {
        String n = Props.get(npc, P_NAME);
        if (n != null && !n.isEmpty()) return n;
        String l = legacyName(npc);
        return l.isEmpty() ? "NPC" : l;
    }

    public static void setName(Entity npc, String name) {
        String t = name == null ? "" : name.trim();
        Props.set(npc, P_NAME, t.isEmpty() ? "NPC" : t);
    }

    public static String greeting(Entity npc) {
        String g = Props.get(npc, P_GREET);
        return g == null ? "" : g;
    }

    public static void setGreeting(Entity npc, String text) {
        String t = text == null ? "" : text.trim();
        Props.set(npc, P_GREET, t.isEmpty() ? null : t);
    }

    /** 상점 레벨 (상점이 아니면 -1) */
    public static int shopLevel(Entity npc) {
        if (!npc.entityTags().contains("rpg_shop")) return -1;
        for (String tag : npc.entityTags()) {
            if (!tag.startsWith("rpg_shop_lv_")) continue;
            try {
                double lv = Double.parseDouble(tag.substring("rpg_shop_lv_".length()));
                if (LEVELS.contains(lv)) return (int) lv;
            } catch (NumberFormatException e) {
                // 넘어감
            }
        }
        return LEVELS.get(0).intValue();
    }

    /** 상점으로 (level -1 이면 상점 해제) */
    public static void setShop(Entity npc, int level) {
        for (String tag : List.copyOf(npc.entityTags())) if (tag.equals("rpg_shop") || tag.startsWith("rpg_shop_lv_")) npc.removeTag(tag);
        if (LEVELS.contains((double) level)) {
            npc.addTag("rpg_shop");
            npc.addTag("rpg_shop_lv_" + level);
        }
    }

    public static boolean isSmith(Entity npc) {
        return npc.entityTags().contains("rpg_smith");
    }

    public static void setSmith(Entity npc, boolean on) {
        if (on) npc.addTag("rpg_smith");
        else npc.removeTag("rpg_smith");
    }

    public static boolean isRunesmith(Entity npc) {
        return npc.entityTags().contains("rpg_runesmith");
    }

    public static void setRunesmith(Entity npc, boolean on) {
        if (on) npc.addTag("rpg_runesmith");
        else npc.removeTag("rpg_runesmith");
    }

    /** 이름표: [!] · [상점] · [대장장이] · [룬술사] + 이름 (+ 상점 레벨) */
    public static String nameTagFor(String name, int shopLv, boolean hasQuest, boolean smith, boolean runesmith) {
        StringBuilder p = new StringBuilder();
        if (hasQuest) p.append("§e§l[!] ");
        if (shopLv >= 0) p.append("§a§l[상점] ");
        if (smith) p.append("§6§l[대장장이] ");
        if (runesmith) p.append("§d§l[룬술사] ");
        String suffix = shopLv >= 0 ? " §7(" + shopLv + "렙)" : "";
        return p + "§r§f" + name + suffix;
    }

    // ------------------------------------------------------------ 이름 목록 (세계)

    private static JsonObject readObj(MinecraftServer s, String key) {
        String v = Props.world(s, key);
        if (v == null) return new JsonObject();
        try {
            JsonElement e = JsonParser.parseString(v);
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ex) {
            return new JsonObject();
        }
    }

    private static void writeObj(MinecraftServer s, String key, JsonObject o) {
        Props.setWorld(s, key, o.toString());
    }

    public static String nameByKey(MinecraftServer s, String key) {
        JsonObject reg = readObj(s, W_REG);
        return reg.has(key) ? reg.get(key).getAsString() : "";
    }

    /** [번호, 이름] 이름순 */
    public static List<String[]> list(MinecraftServer s) {
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : readObj(s, W_REG).entrySet()) out.add(new String[] {e.getKey(), e.getValue().getAsString()});
        out.sort((a, b) -> a[1].compareTo(b[1]));
        return out;
    }

    public static void forget(MinecraftServer s, String key) {
        JsonObject reg = readObj(s, W_REG);
        reg.remove(key);
        writeObj(s, W_REG, reg);
        forgetPos(s, key);
    }

    // ------------------------------------------------------------ 자리 (길 안내)

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    public static String dimId(Level level) {
        return level.dimension().identifier().toString();
    }

    /** 차원 이름의 세계 (없으면 null) */
    public static ServerLevel levelOf(MinecraftServer s, String dim) {
        Identifier id = Identifier.tryParse(dim);
        return id == null ? null : s.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** 불러와진 엔티티 (없으면 null) */
    public static Entity entityOf(MinecraftServer s, Spot spot) {
        if (spot == null) return null;
        ServerLevel level = levelOf(s, spot.dim());
        if (level == null) return null;
        try {
            return level.getEntity(UUID.fromString(spot.id()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * NPC 자리를 적어 둠 (반 칸 넘게 옮겼거나 차원 · 엔티티가 바뀌었을 때만). key 가 null 이면 NPC 의 번호, at 이 null 이면 지금 자리
     */
    public static void notePos(Entity npc, String key, Vec3 at, String dim) {
        String k = key != null ? key : key(npc);
        if (k.isEmpty()) return;
        MinecraftServer s = server(npc);
        Spot main = Spot.of(readObj(s, W_MAIN).get(k));
        String id = npc.getUUID().toString();
        if (main != null && !main.id().equals(id)) return; // 예전에 세운 중복 메인 NPC
        Vec3 l = at != null ? at : npc.position();
        String d = dim != null ? dim : dimId(npc.level());
        JsonObject all = readObj(s, W_POS);
        Spot cur = Spot.of(all.get(k));
        if (cur != null && cur.id().equals(id) && cur.dim().equals(d) && Math.hypot(Math.hypot(cur.x() - l.x, cur.y() - l.y), cur.z() - l.z) < 0.5) return;
        if (cur != null && !cur.id().equals(id) && at == null) {
            // 같은 번호의 다른 NPC 가 아직 있으면 먼저 적은 것을 지킴
            Entity old = entityOf(s, cur);
            if (old != null && old.isAlive() && isNpcEntity(old) && key(old).equals(k)) return;
        }
        all.add(k, new Spot(id, d, round1(l.x), round1(l.y), round1(l.z)).json());
        writeObj(s, W_POS, all);
    }

    /** 마지막으로 적어 둔 NPC 자리 (메인 NPC 는 세운 기록도) */
    public static Spot pos(MinecraftServer s, String key) {
        if (key == null || key.isEmpty()) return null;
        Spot p = Spot.of(readObj(s, W_POS).get(key));
        if (p != null) return p;
        Spot m = Spot.of(readObj(s, W_MAIN).get(key));
        return m == null ? null : new Spot(m.id(), m.dim(), m.x() + 0.5, m.y(), m.z() + 0.5);
    }

    public static void forgetPos(MinecraftServer s, String key) {
        JsonObject all = readObj(s, W_POS);
        if (!all.has(key)) return;
        all.remove(key);
        writeObj(s, W_POS, all);
    }

    /** 이름표 · 태그 · 이름 목록을 지금 데이터에 맞춤 (questCount = 이 NPC 가 주는 퀘스트 수). 번호를 돌려줌 */
    public static String refresh(Entity npc, int questCount) {
        String key = key(npc, true);
        String name = name(npc);
        int lv = shopLevel(npc);
        npc.addTag(TAG);
        if (questCount > 0) npc.addTag("rpg_quest");
        else npc.removeTag("rpg_quest");
        npc.setCustomName(Component.literal(BedrockText.toJava(nameTagFor(name, lv, questCount > 0, isSmith(npc), isRunesmith(npc)))));
        npc.setCustomNameVisible(true);
        MinecraftServer s = server(npc);
        JsonObject reg = readObj(s, W_REG);
        if (!reg.has(key) || !reg.get(key).getAsString().equals(name)) {
            reg.addProperty(key, name);
            writeObj(s, W_REG, reg);
        }
        if (!readObj(s, W_POS).has(key)) notePos(npc, key, null, null);
        return key;
    }

    // ------------------------------------------------------------ 메인 퀘스트 NPC 기록

    public static Spot mainRecord(MinecraftServer s, String key) {
        return Spot.of(readObj(s, W_MAIN).get(key));
    }

    /** 이 NPC 가 그 번호의 메인 NPC (같은 번호의 다른 엔티티는 예전 것 — 보이면 치움) */
    public static void setMainRecord(String key, Entity npc, Vec3 at, String dim) {
        MinecraftServer s = server(npc);
        JsonObject all = readObj(s, W_MAIN);
        Vec3 l = at != null ? at : npc.position();
        all.add(key, new Spot(npc.getUUID().toString(), dim != null ? dim : dimId(npc.level()), Math.floor(l.x), Math.floor(l.y), Math.floor(l.z)).json());
        writeObj(s, W_MAIN, all);
    }

    public static void forgetMainRecord(MinecraftServer s, String key) {
        JsonObject all = readObj(s, W_MAIN);
        if (!all.has(key)) return;
        all.remove(key);
        writeObj(s, W_MAIN, all);
    }

    // ------------------------------------------------------------ 세우기

    /** NPC 를 세울 자리: 플레이어 앞 2칸 (막혀 있으면 플레이어 자리) */
    public static Vec3 spotNear(ServerPlayer sp) {
        Vec3 dir = sp.getViewVector(1F);
        double len = Math.hypot(dir.x, dir.z);
        if (len < 1e-6) len = 1;
        Vec3 base = sp.position();
        Vec3 loc = new Vec3(base.x + dir.x / len * 2, base.y, base.z + dir.z / len * 2);
        BlockPos feet = BlockPos.containing(loc);
        Level level = sp.level();
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) loc = base;
        return loc;
    }

    /** 마네킹 NPC 를 at 에 세워 face 쪽을 보게 (밀리지 않음 · 다치지 않음 · 아래 설명 줄 없음) */
    public static Mannequin spawnAt(ServerLevel level, Vec3 at, Vec3 face) {
        Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (m == null) throw new IllegalStateException("mannequin");
        float yaw = 0;
        if (face != null) yaw = Kit.lookRotation(at, face)[0];
        m.snapTo(at.x, at.y, at.z, yaw, 0F);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);
        setup(m);
        level.addFreshEntity(m);
        return m;
    }

    /** NPC 몸 설정 (밀리지 않음 · 다치지 않음 · 설명 줄 숨김) */
    public static void setup(Mannequin m) {
        m.setPermanentlyInvulnerable(true);
        try {
            MannequinAccessor a = (MannequinAccessor) m;
            a.mmorpg$setImmovable(true);
            a.mmorpg$setHideDescription(true);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] npc setup", e);
        }
    }

    /** 플레이어 앞 2칸에 NPC 를 세워 플레이어를 보게 */
    public static Mannequin spawnNear(ServerPlayer sp) {
        return spawnAt(sp.level(), spotNear(sp), sp.getEyePosition());
    }

    /** 가장 가까운 NPC 몸 (range 칸 안, 없으면 null) */
    public static Mannequin nearest(ServerPlayer sp, double range) {
        AABB box = sp.getBoundingBox().inflate(range);
        Mannequin best = null;
        double bd = range * range;
        for (Mannequin m : sp.level().getEntitiesOfClass(Mannequin.class, box, e -> true)) {
            double d = m.distanceToSqr(sp);
            if (d <= bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** 이 세계 (불러와진 곳) 의 RPG NPC 들 (번호 → 엔티티) */
    public static Map<String, List<Mannequin>> loaded(MinecraftServer s) {
        Map<String, List<Mannequin>> out = new HashMap<>();
        for (ServerLevel level : s.getAllLevels()) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Mannequin m && isRpgNpc(m)) out.computeIfAbsent(key(m), k -> new ArrayList<>()).add(m);
            }
        }
        return out;
    }
}
