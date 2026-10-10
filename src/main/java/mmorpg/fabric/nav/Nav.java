package mmorpg.fabric.nav;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.npc.Dummy;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.npc.TrainingDummy;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 길 안내 (nav.js — v2.6.1): 퀘스트를 받으면 다음에 갈 곳을 그 플레이어에게만 보여 줌.
 * - 갈 곳: Quests.navGoal (고른 퀘스트 → 보고할 것 → 메인 이야기 → 진행 중인 다른 퀘스트 …).
 *   NPC = 마지막으로 적어 둔 자리 (NpcData.pos — 불러오지 않은 곳도, 불러와져 있으면 지금 자리. 적어 둔 자리 가까이 왔는데 그 NPC 가 없으면 "NPC가 없음"),
 *   몬스터 처치 · 레벨 = 그 몬스터의 가장 가까운 사냥터 (/mobzone), 보스 처치 = 그 보스의 가장 가까운 제단,
 *   무기 시험 = 48칸 안의 훈련용 허수아비 (/dummy — 브란 곁에 두기를 권함), 없으면 허수아비 사냥터
 * - 화면 위 가운데 화살표 칸: 서버는 목적지 · 도착 · 보이기 · 글자가 바뀔 때만 NavNet 패킷, 화살표 방향 · 거리 · 높이 글자는 클라이언트 (NavHud) 가 매 프레임.
 *   큰 글자 (제목) 가 떠 있거나 창이 열려 있으면 클라이언트가 칸을 숨김
 * - 빛 (그 플레이어에게만 — SkillFx.fxFor): 바닥 빛 화살표 (목적지 쪽으로 차례로 켜짐, 2초마다), 목적지 빛기둥 (96칸 안, 1초마다),
 *   목적지 발밑 고리 (32칸 안), 도착 반짝임 (사냥터 · 제단 · 허수아비는 소리도 — NPC 는 Npcs 가 다가갈 때 소리를 냄)
 * - 싸우는 보스 필드 근처 (보스 막대가 보이는 거리) 에서는 화면 칸을 숨김. /nav · /길안내 · 퀘스트 목록에서 끄고 켬
 * 자바에서 달라진 점: 제목 채널에 숨긴 글자 대신 패킷 + 클라이언트 HUD (화살표 · 거리는 서버 왕복 없이 바로 바뀜 — 애드온의 hudGap 은 목적지 자리만 바뀔 때),
 * 사냥터 · 보스 제단 · 보스 필드는 5단계가 채울 훅 (zones · altars · bossFights — 그 전에는 빈 목록이라 "사냥터 없음" · "제단 없음"),
 * 네더 차원 이름 minecraft:the_nether, 2틱마다 (애드온 runInterval 2) 서버 틱 끝에서.
 */
public final class Nav {
    private Nav() {}

    private static final Rules R = Rules.get();
    /** 규칙 (config.js NAV) */
    static final Obj NAV = R.obj("NAV");
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj BOSSES = R.obj("BOSSES");
    private static final double HUNT_RADIUS = R.obj("HUNT_ZONE").num("radius");

    private static final long HUD_GAP = (long) NAV.num("hudGap");
    private static final long GOAL_EVERY = (long) NAV.num("goalEvery");
    private static final long WAVE_EVERY = (long) NAV.num("waveEvery");
    private static final long BEAM_EVERY = (long) NAV.num("beamEvery");
    private static final double BEAM_RANGE = NAV.num("beamRange");
    private static final double PING_RANGE = NAV.num("pingRange");
    private static final double ARRIVE = NAV.num("arrive");
    private static final double BOSS_HIDE = NAV.num("bossHide");
    private static final Obj WAVE = NAV.obj("wave");
    private static final double WAVE_FROM = WAVE.num("from");
    private static final double WAVE_STEP = WAVE.num("step");
    private static final int WAVE_COUNT = (int) WAVE.num("count");
    private static final double WAVE_DELAY = WAVE.num("delay");
    /** 메인 금빛 · 일반 하늘빛 (NAV.colors m · c) */
    public static final double[] COLOR_MAIN = color("m");
    public static final double[] COLOR_COMMON = color("c");

    private static final double[] WHITE = {1, 1, 1};
    private static final double BEAM_HEIGHT = 14;
    /** 무기 시험: 이 안의 훈련용 허수아비 */
    public static final double DUMMY_RANGE = 48;
    /** 적어 둔 NPC 자리에서 이 칸 안인데 NPC 가 없으면 "NPC가 없음" */
    public static final double MISSING_NEAR = 16;
    /** 차원 이름 (베드락 nether = 자바 the_nether) */
    private static final Map<String, String> DIM_NAMES = Map.of("minecraft:overworld", "오버월드", "minecraft:the_nether", "네더", "minecraft:nether", "네더",
            "minecraft:the_end", "디 엔드");
    /** 도착했을 때 이름 뒤의 글자 ("터치" → 자바 "누르기") */
    private static final Map<String, String> ARRIVED = Map.of("npc", " §a· 여기!", "zone", " §a· 사냥터", "altar", " §a· 제단을 누르기", "dummy", " §a· 훈련용 허수아비");

    private static double[] color(String k) {
        List<Double> c = NAV.obj("colors").numbersOrNull(k);
        return new double[] {c.get(0), c.get(1), c.get(2)};
    }

    // ------------------------------------------------------------ 5단계가 채울 훅 (사냥터 · 보스 제단 · 싸우는 보스 필드)

    /** 사냥터 (huntzone.js Zone — /mobzone): id, 몬스터 키 (MONSTERS), 차원 ("minecraft:overworld"), 가운데 블록 자리 (가운데 = x + 0.5, y, z + 0.5) */
    public record Zone(String id, String kind, String dim, int x, int y, int z) {}

    /** 보스 제단 (arena.js altars() 의 한 줄 — /boss altar): 보스 키 (BOSSES), 차원, 제단 블록 자리 (필드 가운데 = x + 0.5, y, z + 0.5 · 크기 = BOSSES.키.arena) */
    public record Altar(String key, String dim, int x, int y, int z) {}

    /** 싸우는 보스 필드 (bosses.js allEncounters() 의 한 줄): 그 제단, 필드 가운데 (null 이면 제단 가운데) */
    public record BossFight(Altar altar, Vec3 center) {}

    /** 모든 사냥터 (5단계 huntzone 이 채움 — 그 전에는 빈 목록: 처치 · 레벨 안내는 "사냥터 없음") */
    public static Supplier<List<Zone>> zones = List::of;
    /** 모든 보스 제단 (5단계 arena 가 채움 — 그 전에는 빈 목록: 보스 안내는 "제단 없음") */
    public static Supplier<List<Altar>> altars = List::of;
    /** 지금 싸우는 보스 필드 (5단계 bosses 가 채움 — 그 근처에서는 화면 칸을 숨김) */
    public static Supplier<List<BossFight>> bossFights = List::of;

    // ------------------------------------------------------------ 목적지

    /**
     * 목적지 (nav.js Target): kind = npc · zone · altar · dummy, id = NPC 번호 · 사냥터 id · 제단 "차원|x,y,z" · 허수아비 UUID, name = NPC 이름 (그 밖은 ""),
     * dim · at = 차원 · 자리 (NPC 를 적어 둔 적이 없으면 null), radius = 도착 거리, altar = 제단 (제단일 때), missing = 적어 둔 자리 가까이 왔는데 NPC 가 없음
     */
    public record Target(String kind, String id, String name, String dim, Vec3 at, double radius, Altar altar, boolean missing) {}

    /** 한 플레이어의 길 안내 상태 (nav.js NavState) */
    static final class State {
        Quests.NavGoal goal;
        Target target;
        String key = "";
        long goalAt;
        long waveAt;
        long beamAt;
        long pingAt;
        boolean arrived;
        boolean hide;
        boolean dirty;
        /** 목적지 · 도착 · 보이기가 바뀜 → 화면 칸을 바로 (아니면 HUD_GAP 틱에 한 번) */
        boolean urgent = true;
        /** 마지막으로 정한 화면 칸 · 그 틱 · 클라이언트에 갔는지 */
        NavNet.NavPayload hud;
        long hudAt = -100000;
        boolean delivered;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static String lastError = "";

    private static State stateOf(ServerPlayer sp, long now) {
        State st = STATES.get(sp.getUUID());
        if (st == null) {
            // 목적지 다시 정하기를 플레이어마다 다른 틱에 (여럿이 한 틱에 몰리지 않게 — 애드온과 같은 문자열 해시)
            int h = sp.getUUID().toString().hashCode();
            long phase = 2 * (Math.abs((long) h) % Math.max(1, GOAL_EVERY / 2));
            st = new State();
            st.goalAt = now - GOAL_EVERY + phase;
            STATES.put(sp.getUUID(), st);
        }
        return st;
    }

    /** 나간 플레이어 (forgetNav) */
    public static void forgetNav(UUID id) {
        STATES.remove(id);
    }

    /** 다음 틱에 갈 곳을 다시 정함 (/nav · 퀘스트를 받음 · 목표를 채움 · 보고 · 끄고 켬 — Quests.onNavChange) */
    public static void refreshNav(ServerPlayer sp) {
        State st = stateOf(sp, Tasks.now());
        st.dirty = true;
        st.urgent = true;
    }

    /** 시험용: 지금 화면 칸 (아직 정하지 않았으면 null) */
    public static NavNet.NavPayload hud(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? null : st.hud;
    }

    /** 시험용: 지금 목적지 (없으면 null) */
    public static Target target(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? null : st.target;
    }

    /** 시험용 (사진 시각): 다음 바닥 빛 화살표 틱 — 지금보다 앞이면 다음 2틱 안에 (상태가 없으면 -1) */
    public static long nextWave(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? -1 : st.waveAt;
    }

    // ------------------------------------------------------------ 갈 곳 → 자리

    private static String dimOf(ServerPlayer sp) {
        return NpcData.dimId(sp.level());
    }

    /** NPC 자리 (npcSpot): 차원 · 자리 · 없음 */
    private record Spot(String dim, Vec3 at, boolean missing) {}

    /**
     * NPC 자리: 불러와진 NPC 면 지금 자리 (옮겼으면 기록도 고침), 아니면 마지막으로 적어 둔 자리. 적어 둔 자리 가까이 (MISSING_NEAR) 왔고 그곳 엔티티가
     * 불러와져 있는데 (자바: 엔티티가 도는 칸) 그 NPC 가 없으면 missing (같은 번호의 NPC 가 근처에 다시 생겼으면 Npcs 의 NPC 연출이 기록을 고침)
     */
    private static Spot npcSpot(String key, ServerPlayer sp) {
        MinecraftServer server = sp.level().getServer();
        NpcData.Spot rec = server == null ? null : NpcData.pos(server, key);
        if (rec == null) return null;
        Entity e;
        try {
            e = NpcData.entityOf(server, rec);
        } catch (RuntimeException ex) {
            e = null;
        }
        try {
            if (e != null && e.isAlive() && NpcData.isNpcEntity(e) && key.equals(NpcData.key(e))) {
                NpcData.notePos(e, key, null, null);
                return new Spot(NpcData.dimId(e.level()), e.position(), false);
            }
        } catch (RuntimeException ex) {
            // 무시
        }
        Vec3 at = new Vec3(rec.x(), rec.y(), rec.z());
        try {
            Vec3 l = sp.position();
            if (rec.dim().equals(dimOf(sp)) && Math.hypot(at.x - l.x, at.z - l.z) <= MISSING_NEAR && entitiesLoaded(sp.level(), at)) {
                return new Spot(rec.dim(), at, true);
            }
        } catch (RuntimeException ex) {
            // 무시
        }
        return new Spot(rec.dim(), at, false);
    }

    /** 그 자리의 엔티티가 불러와져 있는지 (애드온 isChunkLoaded — 자바는 엔티티를 따로 불러옴: 칸의 엔티티를 다 읽었거나 엔티티가 도는 칸) */
    private static boolean entitiesLoaded(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        long chunk = ((long) (pos.getX() >> 4) & 0xFFFFFFFFL) | (((long) (pos.getZ() >> 4) & 0xFFFFFFFFL) << 32); // ChunkPos.asLong
        return level.areEntitiesLoaded(chunk) || level.isPositionEntityTicking(pos);
    }

    /** 사냥터 가운데 (huntzone.js centerOf) */
    public static Vec3 zoneCenter(Zone z) {
        return new Vec3(z.x() + 0.5, z.y(), z.z() + 0.5);
    }

    /** 보스 필드 가운데 (arena.js arenaCenter — 제단 블록 위 가운데) */
    public static Vec3 arenaCenter(Altar a) {
        return new Vec3(a.x() + 0.5, a.y(), a.z() + 0.5);
    }

    private static Obj arenaOf(Altar a) {
        Obj b = a == null || a.key() == null ? null : BOSSES.objOrNull(a.key());
        return b == null ? null : b.objOrNull("arena");
    }

    /** 보스 필드 반지름 (arena.js arenaSize — BOSSES.키.arena.radius, 모르는 보스는 0) */
    public static double arenaRadius(Altar a) {
        Obj ar = arenaOf(a);
        return ar == null ? 0 : ar.numOr("radius", 0);
    }

    /** loc 이 필드 안인지 (arena.js inArena — 가로 반지름 + margin, 높이는 바닥 3칸 아래 ~ 천장) */
    public static boolean inArena(Altar a, String dim, Vec3 loc, double margin) {
        if (a == null || dim == null || !dim.equals(a.dim())) return false;
        Obj ar = arenaOf(a);
        double radius = ar == null ? 0 : ar.numOr("radius", 0);
        double height = ar == null ? 0 : ar.numOr("height", 0);
        Vec3 c = arenaCenter(a);
        if (loc.y < a.y() - 3 || loc.y > a.y() + height + 1) return false;
        return Math.hypot(loc.x - c.x, loc.z - c.z) <= radius + margin;
    }

    /** 같은 차원에서 가장 가까운 것 (없으면 다른 차원의 것 하나 — nearest) */
    private static <T> T nearest(List<T> list, ServerPlayer sp, Function<T, String> dimFn, Function<T, Vec3> posOf) {
        Vec3 l = sp.position();
        String dim = dimOf(sp);
        T best = null;
        T other = null;
        double bd = Double.POSITIVE_INFINITY;
        for (T x : list) {
            if (x == null) continue;
            if (!dim.equals(dimFn.apply(x))) {
                if (other == null) other = x;
                continue;
            }
            Vec3 p = posOf.apply(x);
            double d = Math.sqrt((p.x - l.x) * (p.x - l.x) + (p.y - l.y) * (p.y - l.y) + (p.z - l.z) * (p.z - l.z));
            if (d < bd) {
                bd = d;
                best = x;
            }
        }
        return best != null ? best : other;
    }

    private static <T> List<T> listOf(Supplier<List<T>> s) {
        try {
            List<T> l = s.get();
            return l == null ? List.of() : l;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 가장 가까운 훈련용 허수아비 (DUMMY_RANGE 칸 안, 없으면 null) */
    private static TrainingDummy nearestDummy(ServerPlayer sp) {
        Vec3 p = sp.position();
        TrainingDummy best = null;
        double bd = DUMMY_RANGE;
        for (TrainingDummy d : sp.level().getEntitiesOfClass(TrainingDummy.class, new AABB(p, p).inflate(DUMMY_RANGE), Dummy::isDummy)) {
            if (!d.isAlive()) continue;
            double dd = d.position().distanceTo(p);
            if (dd <= bd) {
                bd = dd;
                best = d;
            }
        }
        return best;
    }

    /** 갈 곳 → 목적지 (resolve — 없으면 null) */
    public static Target resolve(ServerPlayer sp, Quests.NavGoal g) {
        if (g == null) return null;
        if (g.npc() != null) {
            Spot spot = npcSpot(g.npc(), sp);
            String name = Quests.npcNameByKey(g.npc());
            if (name == null || name.isEmpty()) name = MainQuests.mainNpcCall(g.npc());
            if (name == null || name.isEmpty()) name = "NPC";
            return new Target("npc", g.npc(), name, spot == null ? null : spot.dim(), spot == null ? null : spot.at(), ARRIVE, null, spot != null && spot.missing());
        }
        if ("boss".equals(g.why()) && g.boss() != null) {
            List<Altar> list = new ArrayList<>();
            for (Altar a : listOf(altars)) if (a != null && g.boss().equals(a.key()) && arenaOf(a) != null) list.add(a);
            Altar a = nearest(list, sp, Altar::dim, Nav::arenaCenter);
            return a == null ? null : new Target("altar", a.dim() + "|" + a.x() + "," + a.y() + "," + a.z(), "", a.dim(), arenaCenter(a), arenaRadius(a), a, false);
        }
        if ("try".equals(g.why())) {
            // 무기 시험: 가까운 훈련용 허수아비 (때려도 죽지 않음), 없으면 아래의 허수아비 사냥터
            try {
                TrainingDummy d = nearestDummy(sp);
                if (d != null) return new Target("dummy", d.getUUID().toString(), "", NpcData.dimId(d.level()), d.position(), ARRIVE, null, false);
            } catch (RuntimeException e) {
                // 무시
            }
        }
        List<String> kinds = g.mobs() != null && !g.mobs().isEmpty() ? g.mobs() : g.mob() != null ? List.of(g.mob()) : List.of();
        if (!kinds.isEmpty()) {
            // 전설의 시련: 몬스터 여럿 중 어느 것이든 → 그 중 가장 가까운 사냥터
            List<Zone> list = new ArrayList<>();
            for (Zone z : listOf(zones)) if (z != null && kinds.contains(z.kind())) list.add(z);
            Zone z = nearest(list, sp, Zone::dim, Nav::zoneCenter);
            return z == null ? null : new Target("zone", z.id(), "", z.dim(), zoneCenter(z), HUNT_RADIUS + 2, null, false);
        }
        return null;
    }

    /** 싸우는 보스 필드 근처인지 (보스 막대가 화면 위 가운데에 보이는 거리 — nearBossFight) */
    static boolean nearBossFight(ServerPlayer sp) {
        Vec3 l = sp.position();
        String dim = dimOf(sp);
        for (BossFight f : listOf(bossFights)) {
            try {
                if (f == null || f.altar() == null || !dim.equals(f.altar().dim())) continue;
                Vec3 c = f.center() != null ? f.center() : arenaCenter(f.altar());
                if (Math.hypot(l.x - c.x, l.z - c.z) <= arenaRadius(f.altar()) + BOSS_HIDE) return true;
            } catch (RuntimeException e) {
                // 무시
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 글자

    private static String str(String s) {
        return s == null ? "" : s;
    }

    /** 화면 칸의 이름 글자 (labelOf — 베드락 서식) */
    public static String labelOf(Quests.NavGoal g, Target t) {
        String prog = g.count() > 0 ? " §7" + Math.min(g.n(), g.count()) + "/" + g.count() : "";
        String name = t != null && t.name() != null && !t.name().isEmpty() ? t.name() : "NPC";
        switch (str(g.why())) {
            case "turnin" -> {
                return "§e" + name + "§f에게 보고";
            }
            case "talk" -> {
                return "§e" + name + "§f에게";
            }
            case "offer" -> {
                return "§e" + name + "§f에게 새 이야기";
            }
            case "hunt" -> {
                Obj m = g.mob() == null ? null : MONSTERS.objOrNull(g.mob());
                String mn = m != null ? m.strOr("name", "") : "";
                return "§c" + (!mn.isEmpty() ? mn : str(g.what())) + " §f처치" + prog;
            }
            case "boss" -> {
                return "§c" + str(g.what()) + " §f제단";
            }
            case "try" -> {
                return "§f무기 시험" + prog;
            }
            case "collect" -> {
                return "§f" + str(g.what()) + " 모으기" + prog;
            }
            case "level" -> {
                Obj m = g.mob() == null ? null : MONSTERS.objOrNull(g.mob());
                return "§7레벨 " + g.lv() + " 필요" + (m != null ? " §7· §f" + m.strOr("name", "") + " 사냥 §7(Lv." + Js.str(m.numOr("level", 0)) + ")" : "");
            }
            default -> {
                return "§f" + (g.q() == null ? "" : g.q().title);
            }
        }
    }

    // ------------------------------------------------------------ 도착

    private static boolean isHere(ServerPlayer sp, Target t, double d, double dy) {
        if (t.kind().equals("altar")) return inArena(t.altar(), dimOf(sp), sp.position(), 0);
        return d <= t.radius() && Math.abs(dy) <= (t.kind().equals("zone") ? 6 : 3);
    }

    private static boolean leftHere(ServerPlayer sp, Target t, double d) {
        if (t.kind().equals("altar")) return !inArena(t.altar(), dimOf(sp), sp.position(), 3);
        return d > t.radius() + 3;
    }

    // ------------------------------------------------------------ 빛 (그 플레이어에게만)

    private static void pfx(ServerPlayer sp, String id, Vec3 at, Opt o) {
        try {
            SkillFx.fxFor(sp, id, at, o);
        } catch (RuntimeException e) {
            // 불러오지 않은 곳 등
        }
    }

    /**
     * 바닥 높이: 발보다 2.5칸 위에서 아래로 8칸 (언덕 오르막도) — 닿은 윗면의 높이 (반 블록 · 물 위도). 시작점이 블록 안이거나 윗면이 아니면
     * (낮은 굴 천장 등) 발 높이 (groundY — 베드락 getBlockFromRay)
     */
    static double groundY(ServerLevel level, Vec3 at, double y0) {
        try {
            Vec3 from = new Vec3(at.x, y0 + 2.5, at.z);
            Vec3 to = new Vec3(at.x, y0 + 2.5 - 8, at.z);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
                double y = hit.getLocation().y;
                if (y <= y0 + 2.4) return y;
            }
        } catch (RuntimeException e) {
            // 불러오지 않은 곳
        }
        return y0;
    }

    /** 바닥 빛 화살표: 발밑에서 목적지 쪽으로 &gt; &gt; &gt; 가 차례로 켜졌다 사라짐 (wave) */
    private static void wave(ServerPlayer sp, Target t, double[] color) {
        Vec3 p = sp.position();
        Vec3 dir = NavMath.flat(V.sub(t.at(), p));
        Vec3 side = V.rightOf(dir);
        double dist = Math.hypot(t.at().x - p.x, t.at().z - p.z);
        for (int i = 0; i < WAVE_COUNT; i++) {
            double d = WAVE_FROM + i * WAVE_STEP;
            if (d > dist - 1.5) break;
            Vec3 c = V.add(p, dir, d);
            double y = groundY(sp.level(), c, p.y) + 0.12;
            Vec3 tip = new Vec3(c.x + dir.x * 0.26, y, c.z + dir.z * 0.26);
            double delay = i * WAVE_DELAY;
            for (int s : new int[] {-1, 1}) {
                Vec3 end = new Vec3(c.x - dir.x * 0.22 + side.x * 0.32 * s, y, c.z - dir.z * 0.22 + side.z * 0.32 * s);
                Vec3 mid = new Vec3((tip.x + end.x) / 2, y, (tip.z + end.z) / 2);
                pfx(sp, "rpg:arrow_trail", mid, Opt.of("dir", V.unit(V.sub(end, tip)), "size", 0.29, "thick", 0.065, "life", delay + 0.6, "delay", delay, "color", color,
                        "alpha", 0.9));
            }
        }
    }

    private static boolean standsOnTop(Target t) {
        return t.kind().equals("npc") || t.kind().equals("dummy");
    }

    /** 목적지 빛기둥 (NPC · 허수아비는 머리 위부터 — beam) */
    private static void beam(ServerPlayer sp, Target t, double[] color) {
        double base = standsOnTop(t) ? 2.3 : 0.3;
        Vec3 mid = new Vec3(t.at().x, t.at().y + base + BEAM_HEIGHT / 2, t.at().z);
        pfx(sp, "rpg:arrow_shot", mid, Opt.of("dir", V.UP, "speed", 0.0, "size", BEAM_HEIGHT / 2, "thick", 0.3, "life", 1.2, "color", color, "alpha", 0.5));
        pfx(sp, "rpg:arrow_shot", mid, Opt.of("dir", V.UP, "speed", 0.0, "size", BEAM_HEIGHT / 2, "thick", 0.08, "life", 1.1, "color", WHITE, "alpha", 0.8));
    }

    /** 목적지 발밑 고리 (ping) */
    private static void ping(ServerPlayer sp, Target t, double[] color) {
        double size = standsOnTop(t) ? 1.4 : Math.min(8, t.radius());
        pfx(sp, "rpg:shock_ring", new Vec3(t.at().x, t.at().y + 0.08, t.at().z), Opt.of("dir", V.ZERO, "size", size, "life", 0.9, "color", color, "alpha", 0.75));
    }

    /** 도착: 고리 + 위로 튀는 빛 (NPC 가 아니면 소리도 — 사냥터 · 제단 · 허수아비, arrivalFx) */
    private static void arrivalFx(ServerPlayer sp, Target t, double[] color) {
        Vec3 at = standsOnTop(t) ? t.at() : sp.position();
        Vec3 base = new Vec3(at.x, at.y + 0.08, at.z);
        pfx(sp, "rpg:shock_ring", base, Opt.of("dir", V.ZERO, "size", 2.2, "life", 0.7, "color", color, "alpha", 0.95));
        for (int i = 0; i < 10; i++) {
            double a = (i / 10.0) * Math.PI * 2;
            pfx(sp, "rpg:arrow_spark", new Vec3(base.x, base.y + 0.6, base.z), Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.2, Math.sin(a))), "speed", 3.0, "size", 0.09,
                    "life", 0.55, "color", color, "alpha", 0.95));
        }
        if (!t.kind().equals("npc")) SkillFx.playFor(sp, "random.orb", 1.7, 0.6);
    }

    // ------------------------------------------------------------ 틱

    static String targetKey(Quests.NavGoal g, Target t) {
        if (g == null) return "";
        return (g.q() == null ? "" : g.q().id) + "|" + g.why() + "|" + (t != null ? t.kind() + ":" + t.id() : "");
    }

    private static double r1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** 화면 칸 (보임) */
    private static NavNet.NavPayload hudOf(State st, Quests.NavGoal g, Target t, byte mode, String tail) {
        Vec3 at = t != null && t.at() != null ? t.at() : Vec3.ZERO;
        return new NavNet.NavPayload(true, (byte) (g.main() ? 1 : 2), mode, st.key, t != null && t.dim() != null ? t.dim() : "", r1(at.x), r1(at.y), r1(at.z),
                BedrockText.toJava(labelOf(g, t)), str(BedrockText.toJava(tail)));
    }

    /** 화면 칸을 맡김: 목적지 · 도착 · 보이기가 바뀌었으면 바로, 아니면 (목적지 자리만 조금 바뀜) HUD_GAP 틱에 한 번 (show) */
    private static void show(ServerPlayer sp, State st, long now, NavNet.NavPayload hud) {
        boolean same = hud.equals(st.hud);
        if (same && st.delivered) return;
        if (!same && !st.urgent && now - st.hudAt < HUD_GAP) return;
        st.hud = hud;
        st.hudAt = now;
        st.urgent = false;
        st.delivered = NavNet.send(sp, hud);
    }

    /** 한 플레이어 (2틱마다 — tickPlayer) */
    public static void tickPlayer(ServerPlayer sp, long now) {
        State st = stateOf(sp, now);
        if (st.dirty || now - st.goalAt >= GOAL_EVERY) {
            st.dirty = false;
            st.goalAt = now;
            st.goal = Quests.navGoal(sp);
            st.target = resolve(sp, st.goal);
            boolean hide = st.goal != null && nearBossFight(sp);
            if (hide != st.hide) st.urgent = true;
            st.hide = hide;
            String key = targetKey(st.goal, st.target);
            if (!key.equals(st.key)) {
                // 갈 곳이 바뀜 (퀘스트를 받음 · 목표를 채움 · 보고함): 바로 빛 화살표와 빛기둥, 화면 칸도 바로
                st.key = key;
                st.arrived = false;
                st.waveAt = now;
                st.beamAt = now;
                st.pingAt = now;
                st.urgent = true;
            }
        }
        Quests.NavGoal g = st.goal;
        if (g == null || st.hide) {
            show(sp, st, now, NavNet.NavPayload.HIDDEN);
            return;
        }
        Target t = st.target;
        double[] color = g.main() ? COLOR_MAIN : COLOR_COMMON;
        byte mode = NavNet.NavPayload.NONE;
        String tail = "";
        if (t == null) {
            // 갈 곳이 없음: 모으기는 글자만, 이 애드온 몬스터 · 보스인데 사냥터 · 제단이 없으면 알려 줌 (운영자가 /mobzone · /boss altar 로 둠)
            if ("boss".equals(g.why())) tail = " §7· 제단 없음";
            else if (g.mob() != null || (g.mobs() != null && !g.mobs().isEmpty())) tail = " §7· 사냥터 없음";
        } else if (t.at() == null) tail = " §7· 위치 모름";
        else if (!dimOf(sp).equals(t.dim())) tail = " §7· " + DIM_NAMES.getOrDefault(str(t.dim()), "다른 차원") + "에 있음";
        else if (t.missing()) tail = " §7· NPC가 없음";
        else {
            Vec3 l = sp.position();
            double d = Math.hypot(t.at().x - l.x, t.at().z - l.z);
            double dy = t.at().y - l.y;
            if (!st.arrived && isHere(sp, t, d, dy)) {
                st.arrived = true;
                st.urgent = true;
                arrivalFx(sp, t, color);
            } else if (st.arrived && leftHere(sp, t, d)) {
                st.arrived = false;
                st.urgent = true;
            }
            if (st.arrived) {
                mode = NavNet.NavPayload.HERE;
                tail = ARRIVED.getOrDefault(t.kind(), "");
            } else {
                mode = NavNet.NavPayload.ARROW; // 방향 · 거리 · 높이 글자는 클라이언트가 매 프레임 (NavMath)
                if (now >= st.waveAt) {
                    st.waveAt = now + WAVE_EVERY;
                    wave(sp, t, color);
                }
                if (d <= BEAM_RANGE && now >= st.beamAt) {
                    st.beamAt = now + BEAM_EVERY;
                    beam(sp, t, color);
                }
                if (d <= PING_RANGE && now >= st.pingAt) {
                    st.pingAt = now + WAVE_EVERY;
                    ping(sp, t, color);
                }
            }
        }
        show(sp, st, now, hudOf(st, g, t, mode, tail));
    }

    private static void navTick(MinecraftServer server) {
        long now = Tasks.now();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(sp, now);
            } catch (RuntimeException e) {
                String msg = e.toString();
                if (!msg.equals(lastError)) {
                    lastError = msg;
                    Mmorpg.LOG.warn("[mmorpg] nav", e);
                }
            }
        }
    }

    // ------------------------------------------------------------ /nav · /길안내

    /** /nav [on|off]: 켜고 끔 (없으면 바꿈). 켜면 지금 갈 곳을 알려 줌 (navCommand — 채팅 글자, 베드락 서식) */
    public static String navCommand(ServerPlayer sp, String mode) {
        boolean off = "off".equals(mode) || (!"on".equals(mode) && !Quests.navOf(sp).off());
        Quests.setNavOff(sp, off);
        refreshNav(sp);
        SkillFx.playFor(sp, "random.click", off ? 0.8 : 1.3, 1);
        if (off) return "§6[RPG] §7길 안내를 껐습니다. §8(/nav 로 다시 켜기)";
        Quests.NavGoal g = Quests.navGoal(sp);
        if (g == null) return "§6[RPG] §b길 안내를 켰습니다. §7지금은 갈 곳이 없습니다 (퀘스트를 받으면 안내합니다).";
        Target t = resolve(sp, g);
        String where = t != null && t.at() != null
                ? " §7(" + DIM_NAMES.getOrDefault(str(t.dim()), "") + " " + (long) Math.floor(t.at().x) + " " + (long) Math.floor(t.at().y) + " " + (long) Math.floor(t.at().z) + ")"
                : t != null ? " §7(위치 모름)" : "";
        return "§6[RPG] §b길 안내를 켰습니다. §f" + (g.q() == null ? "" : g.q().title) + " → " + BedrockText.plain(labelOf(g, t)) + where;
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("nav", "길안내")) {
            d.register(Commands.literal(name)
                    .executes(c -> nav(c, null))
                    .then(Commands.literal("on").executes(c -> nav(c, "on")))
                    .then(Commands.literal("off").executes(c -> nav(c, "off"))));
        }
    }

    private static int nav(CommandContext<CommandSourceStack> c, String mode) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(navCommand(sp, mode))));
        return 1;
    }

    // ------------------------------------------------------------ 등록

    /** Mmorpg.onInitialize: 패킷 · 퀘스트 훅 (받음 · 목표를 채움 · 보고 · 끄고 켬 → 다음 틱에 바로) · 2틱마다 · 나감 · 명령어 (registerNav) */
    public static void init() {
        NavNet.register();
        Quests.onNavChange(Nav::refreshNav);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 2 == 0) navTick(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetNav(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            lastError = "";
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }
}
