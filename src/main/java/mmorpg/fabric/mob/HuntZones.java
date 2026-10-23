package mmorpg.fabric.mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Js;
import mmorpg.core.Json;
import mmorpg.core.Mobs;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Props;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.nav.Nav;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 사냥터 (huntzone.js v2.6.0): 운영자가 정한 자리에 몬스터를 늘 정해 둔 수만큼 채움 (몬스터는 자연 스폰이 없어 잡으면 사라짐 → 메인 퀘스트의 처치 단계가 막히지 않게).
 * <ul>
 * <li>/mobzone 종류 [수]: 내 발밑을 가운데로 사냥터를 만들고 바로 그 수만큼 부름 (수 1~maxCount, 기본 defaultCount, 넓이를 초록 반짝이 고리로).
 * /mobzone: 목록 · /mobzone clear: 16칸 안의 가장 가까운 사냥터와 그 몬스터를 치움 (명령어는 {@link MonsterCommands})</li>
 * <li>1초마다: 가운데에서 active 칸 안에 플레이어가 있고, 몬스터가 돌아다니는 넓이 (radius + 몬스터 homeRadius) 가 다 불러와진 사냥터만 셈
 * (불러오지 않은 곳의 몬스터를 못 세서 더 부르지 않게). 모자라면 respawn 틱 뒤에 한 마리 (가운데에서 radius 칸 안의 설 수 있는 자리, 연기 + 소리).
 * 쫓아 나간 몬스터도 그 사냥터의 수로 셈 (태그 rpg_zone_&lt;번호&gt;)</li>
 * <li>그래도 넘치면 (멀리 끌려가 남았던 몬스터가 다시 불러와짐 등) 싸우지 않는 것 중 가운데에서 먼 것부터 치움. 지운 사냥터의 몬스터가 나중에 불러와지면 치움.
 * /mob clear 는 사냥터 몬스터를 남김</li>
 * <li>기록: 세계 동적 속성 rpg:hunt_zones [{id, kind, count, dim, x, y, z}] (애드온과 같은 JSON) · 길 안내 (Nav.zones)</li>
 * </ul>
 * 자바에서 달라진 점: "불러와짐" 은 그 칸의 엔티티가 불러와졌는지 (자바는 엔티티를 따로 불러옴 — 길 안내와 같은 확인), 자리 찾기는 불러온 칸의 블록만 봄.
 */
public final class HuntZones {
    private HuntZones() {}

    private static final Obj HUNT_ZONE = Rules.get().obj("HUNT_ZONE");
    private static final String W_ZONES = "rpg:hunt_zones";
    private static final String W_SEQ = "rpg:hunt_zone_seq";
    static final double RADIUS = HUNT_ZONE.num("radius");
    static final double ACTIVE = HUNT_ZONE.num("active");
    static final int MAX_COUNT = (int) HUNT_ZONE.num("maxCount");
    static final int DEFAULT_COUNT = (int) HUNT_ZONE.num("defaultCount");
    static final int MAX_ZONES = (int) HUNT_ZONE.num("maxZones");
    /** 모자란 것을 안 뒤 한 마리를 부르기까지 (틱 — 시험이 줄일 수 있음) */
    public static int respawn = (int) HUNT_ZONE.num("respawn");

    /** 사냥터 하나 (huntzone.js Zone): 번호 (z1 …), 몬스터 키, 수, 차원, 가운데 블록 자리 */
    public record Zone(String id, String kind, int count, String dim, int x, int y, int z) {
        public String tag() {
            return Monsters.ZONE_TAG + id;
        }

        /** 가운데 (블록 가운데) */
        public Vec3 center() {
            return new Vec3(x + 0.5, y, z + 0.5);
        }
    }

    private static MinecraftServer server;
    private static List<Zone> zones;
    /** 사냥터 번호 → 다음 몬스터를 부를 틱 (모자란 것을 안 때부터 respawn 틱 뒤) */
    private static final Map<String, Long> DUE_AT = new HashMap<>();

    // ------------------------------------------------------------ 기록

    private static List<Zone> load() {
        if (zones != null) return zones;
        List<Zone> list = new ArrayList<>();
        if (server != null) {
            try {
                String raw = Props.world(server, W_ZONES);
                if (raw != null && !raw.isEmpty() && Json.parse(raw) instanceof List<?> arr) {
                    for (Object o : arr) {
                        if (!(o instanceof Map<?, ?> m) || !(m.get("kind") instanceof String kind) || Monsters.kind(kind) == null) continue;
                        if (!(m.get("x") instanceof Number x) || !(m.get("y") instanceof Number y) || !(m.get("z") instanceof Number z)) continue;
                        if (!Double.isFinite(x.doubleValue()) || !Double.isFinite(y.doubleValue()) || !Double.isFinite(z.doubleValue())) continue;
                        int count = m.get("count") instanceof Number c ? c.intValue() : DEFAULT_COUNT;
                        list.add(new Zone(String.valueOf(m.get("id")), kind, count, String.valueOf(m.get("dim")), (int) Math.floor(x.doubleValue()),
                                (int) Math.floor(y.doubleValue()), (int) Math.floor(z.doubleValue())));
                    }
                }
            } catch (RuntimeException e) {
                list = new ArrayList<>();
            }
        }
        if (server != null) zones = list;
        return list;
    }

    private static void save() {
        if (server == null) return;
        List<Object> out = new ArrayList<>();
        for (Zone z : load()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", z.id());
            m.put("kind", z.kind());
            m.put("count", z.count());
            m.put("dim", z.dim());
            m.put("x", z.x());
            m.put("y", z.y());
            m.put("z", z.z());
            out.add(m);
        }
        Props.setWorld(server, W_ZONES, Json.stringify(out));
    }

    /** 다시 읽게 (시험용) */
    public static void forgetZones() {
        zones = null;
        DUE_AT.clear();
    }

    /** 사냥터 목록 (복사본) */
    public static List<Zone> zoneList() {
        return new ArrayList<>(load());
    }

    // ------------------------------------------------------------ 몬스터 · 넓이

    /** 그 사냥터의 (불러와진) 몬스터 — 쫓아 나간 것도 */
    static List<Entity> membersOf(ServerLevel level, Zone z) {
        EntityType<? extends BedrockMob> type = RpgEntities.type(Mobs.monsterId(z.kind()));
        if (type == null) return List.of();
        String tag = z.tag();
        return new ArrayList<>(level.getEntities(type, e -> e.entityTags().contains(tag) && !e.isRemoved()));
    }

    /** 그 사냥터의 지금 몬스터 수 (불러와진 것) */
    public static int aliveIn(ServerLevel level, Zone z) {
        return membersOf(level, z).size();
    }

    /** 그 자리 칸의 엔티티가 불러와져 있는지 (애드온 isChunkLoaded — 자바는 엔티티를 따로 불러옴) */
    private static boolean entitiesLoaded(ServerLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        return level.areEntitiesLoaded(ChunkPos.pack(pos)) || level.isPositionEntityTicking(pos);
    }

    /** 몬스터가 돌아다니는 넓이 (가운데 + 네 모서리) 가 다 불러와졌는지 (시험도 씀 — 다 불러와질 때까지 채우지 않음) */
    public static boolean areaLoaded(ServerLevel level, Zone z) {
        double r = RADIUS + Monsters.MONSTER.num("homeRadius");
        Vec3 c = z.center();
        for (double[] d : new double[][] {{0, 0}, {-r, -r}, {r, -r}, {-r, r}, {r, r}}) {
            if (!entitiesLoaded(level, c.x + d[0], c.y, c.z + d[1])) return false;
        }
        return true;
    }

    /** 넘치는 몬스터를 치움: 싸우지 않는 것 중 가운데에서 먼 것부터. 치운 수 */
    static int trimExtra(Zone z, List<Entity> members) {
        int extra = members.size() - z.count();
        if (extra <= 0) return 0;
        Vec3 c = z.center();
        List<Entity> idle = new ArrayList<>();
        for (Entity e : members) if (Monsters.fightOf(e) == null) idle.add(e);
        idle.sort(Comparator.comparingDouble((Entity e) -> Math.hypot(e.getX() - c.x, e.getZ() - c.z)).reversed());
        int n = 0;
        for (Entity e : idle.subList(0, Math.min(extra, idle.size()))) {
            e.discard();
            n++;
        }
        return n;
    }

    /** 설 수 있는 자리 (발 · 머리가 비고 밑이 단단함 — 불러온 칸만). 못 찾으면 가운데 */
    static Vec3 spotIn(ServerLevel level, Zone z) {
        for (int tries = 0; tries < 10; tries++) {
            double a = Kit.rand() * Math.PI * 2;
            double r = Math.sqrt(Kit.rand()) * RADIUS;
            // 사냥터 가운데 (블록 가운데) 에서 r 안 → 고른 블록의 가운데는 반지름 + 0.71 안
            int x = (int) Math.floor(z.x() + 0.5 + Math.cos(a) * r);
            int zz = (int) Math.floor(z.z() + 0.5 + Math.sin(a) * r);
            if (!level.hasChunkAt(new BlockPos(x, z.y(), zz))) continue; // 불러오지 않은 곳: 다른 자리
            for (int y = z.y() + 3; y >= z.y() - 4; y--) {
                BlockState feet = level.getBlockState(new BlockPos(x, y, zz));
                BlockState head = level.getBlockState(new BlockPos(x, y + 1, zz));
                BlockState below = level.getBlockState(new BlockPos(x, y - 1, zz));
                if (feet.isAir() && head.isAir() && !below.isAir() && !(below.getBlock() instanceof LiquidBlock)) return new Vec3(x + 0.5, y, zz + 0.5);
            }
        }
        return z.center();
    }

    /** 한 마리 부름 (자리 · 태그 · 이름표, 연기 + 소리). 못 불렀으면 null */
    static RpgMonster spawnOne(ServerLevel level, Zone z) {
        Vec3 at = spotIn(level, z);
        RpgMonster m = Monsters.spawnAt(level, z.kind(), at, (float) (Kit.rand() * 360 - 180));
        if (m == null) return null;
        m.addTag(z.tag());
        Monsters.Kind k = Monsters.kindOf(m);
        if (k != null && k.traits().isVoid()) return m; // 0.32.0 공허 몬스터는 균열이 열리며 나타남 (Monsters.spawnAt)
        SkillFx.vanilla(level, "minecraft:death_explosion_emitter", new Vec3(at.x, at.y + 0.6, at.z));
        SkillFx.play(level, "random.pop", at, 0.6, 0.8);
        return m;
    }

    // ------------------------------------------------------------ 명령어 (/mobzone)

    /** /mobzone 종류 [수]: 내 발밑에 사냥터 — 알림 글 (베드락 서식) */
    public static String addZone(ServerPlayer sp, String kind, Integer count) {
        server(sp);
        Monsters.Kind k = Monsters.kind(kind);
        if (k == null || kind.indexOf(':') >= 0) return "§c종류: " + String.join(" · ", Monsters.keys());
        List<Zone> list = load();
        if (list.size() >= MAX_ZONES) return "§c사냥터는 " + MAX_ZONES + "곳까지입니다. §7/mobzone clear 로 하나를 지우세요.";
        int raw = count == null ? DEFAULT_COUNT : count;
        int n = Math.max(1, Math.min(MAX_COUNT, raw == 0 ? DEFAULT_COUNT : raw)); // JS: Math.floor(Number(count)) || defaultCount
        int seq = (int) Props.worldNum(server, W_SEQ, 0) + 1;
        Props.setWorld(server, W_SEQ, String.valueOf(seq));
        ServerLevel level = sp.level();
        Zone z = new Zone("z" + seq, k.key(), n, NpcData.dimId(level), (int) Math.floor(sp.getX()), (int) Math.floor(sp.getY()), (int) Math.floor(sp.getZ()));
        list.add(z);
        save();
        for (int i = 0; i < n; i++) {
            if (spawnOne(level, z) == null) break;
        }
        // 넓이를 보여 주는 고리
        Vec3 c = z.center();
        for (int i = 0; i < 24; i++) {
            double a = (i / 24.0) * Math.PI * 2;
            SkillFx.vanilla(level, "minecraft:villager_happy", new Vec3(c.x + Math.cos(a) * RADIUS, c.y + 0.3, c.z + Math.sin(a) * RADIUS));
        }
        SkillFx.playFor(sp, "random.orb", 1.1, 1);
        return "§6[사냥터] §f" + z.id() + ": Lv." + k.level() + " " + k.name() + " " + n + "마리 §7(잡으면 " + Math.round(respawn / 20.0) + "초 뒤 다시 나옴, "
                + Js.str(ACTIVE) + "칸 안에 사람이 있을 때)";
    }

    /** /mobzone clear: 가장 가까운 사냥터 (range 칸 안) 와 그 몬스터 (불러와진 것) 를 치움 — 알림 글 */
    public static String removeNearestZone(ServerPlayer sp, double range) {
        server(sp);
        List<Zone> list = load();
        String dim = NpcData.dimId(sp.level());
        Zone best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Zone z : list) {
            if (!z.dim().equals(dim)) continue;
            double d = Math.sqrt(Math.pow(z.x() + 0.5 - sp.getX(), 2) + Math.pow(z.y() - sp.getY(), 2) + Math.pow(z.z() + 0.5 - sp.getZ(), 2));
            if (d < bestD) {
                bestD = d;
                best = z;
            }
        }
        if (best == null || bestD > range) return "§c" + Js.str(range) + "칸 안에 사냥터가 없습니다. §7/mobzone 으로 목록을 보세요.";
        int n = 0;
        for (Entity e : membersOf(sp.level(), best)) {
            e.discard();
            n++;
        }
        list.remove(best);
        DUE_AT.remove(best.id());
        save();
        Monsters.Kind k = Monsters.kind(best.kind());
        return "§6[사냥터] §f" + best.id() + " (" + (k == null ? best.kind() : k.name()) + ") 를 지우고 몬스터 " + n + "마리를 치웠습니다.";
    }

    /** /mobzone: 목록 (여러 줄) */
    public static String zoneListText(MinecraftServer s) {
        if (server == null) server = s;
        List<Zone> list = load();
        if (list.isEmpty()) return "§6[사냥터] §7없음 §8— /mobzone 종류 수 (예: /mobzone gulon 5) 로 내 발밑에 만듭니다";
        List<String> lines = new ArrayList<>();
        lines.add("§6[사냥터] §f" + list.size() + "곳 §7(잡으면 " + Math.round(respawn / 20.0) + "초 뒤 다시 나옴)");
        for (Zone z : list) {
            ServerLevel level = s == null ? null : NpcData.levelOf(s, z.dim());
            Monsters.Kind k = Monsters.kind(z.kind());
            lines.add("§e" + z.id() + " §fLv." + k.level() + " " + k.name() + " ×" + z.count() + " §7" + z.dim().replace("minecraft:", "") + " " + z.x() + " " + z.y() + " "
                    + z.z() + (level != null ? " §8(지금 " + aliveIn(level, z) + "마리)" : ""));
        }
        return String.join("\n", lines);
    }

    /** 사냥터 몬스터 수 (불러와진 것, 주변 radius 칸) — /mob clear 안내용 */
    public static int zoneMonstersNear(ServerPlayer sp, double radius) {
        Vec3 c = sp.position();
        return sp.level().getEntitiesOfClass(RpgMonster.class, new net.minecraft.world.phys.AABB(c, c).inflate(radius),
                e -> e.position().distanceTo(c) <= radius && Monsters.inZone(e)).size();
    }

    private static MinecraftServer server(ServerPlayer sp) {
        if (server == null) server = sp.level().getServer();
        return server;
    }

    // ------------------------------------------------------------ 1초마다 채우기

    /** 사람이 가까운 사냥터를 채움 (huntzone.js zoneTick) */
    public static void zoneTick(MinecraftServer s) {
        if (server == null) server = s;
        long now = Tasks.now();
        for (Zone z : new ArrayList<>(load())) {
            ServerLevel level = NpcData.levelOf(s, z.dim());
            if (level == null) continue;
            Vec3 c = z.center();
            boolean near = false;
            for (ServerPlayer p : level.players()) {
                if (p.position().distanceTo(c) <= ACTIVE) {
                    near = true;
                    break;
                }
            }
            if (!near || !areaLoaded(level, z)) continue;
            List<Entity> members = membersOf(level, z);
            if (members.size() >= z.count()) {
                trimExtra(z, members);
                DUE_AT.remove(z.id());
                continue;
            }
            Long due = DUE_AT.get(z.id());
            if (due == null) {
                DUE_AT.put(z.id(), now + respawn);
                continue;
            }
            if (now < due) continue;
            try {
                spawnOne(level, z);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] hunt zone " + z.id(), e);
            }
            DUE_AT.put(z.id(), now + respawn);
        }
    }

    /** 지운 사냥터의 몬스터 (그때 불러와지지 않아 남은 것) 가 불러와지면 치움 */
    private static void onLoad(Entity e, ServerLevel level) {
        if (!(e instanceof RpgMonster)) return;
        String tag = null;
        for (String t : e.entityTags()) if (t.startsWith(Monsters.ZONE_TAG)) tag = t;
        if (tag == null) return;
        if (server == null) server = level.getServer();
        for (Zone z : load()) if (z.tag().equals(tag)) return;
        Tasks.later(1, () -> {
            if (!e.isRemoved()) e.discard();
        });
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            zones = null;
            DUE_AT.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            zones = null;
            DUE_AT.clear();
        });
        ServerEntityEvents.ENTITY_LOAD.register(HuntZones::onLoad);
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (Tasks.now() % 20 != 0) return;
            try {
                zoneTick(s);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] hunt zones", e);
            }
        });
        // 길 안내: 처치 퀘스트의 다음 갈 곳 (가까운 사냥터)
        Nav.zones = () -> {
            List<Nav.Zone> out = new ArrayList<>();
            for (Zone z : load()) out.add(new Nav.Zone(z.id(), z.kind(), z.dim(), z.x(), z.y(), z.z()));
            return out;
        };
    }
}
