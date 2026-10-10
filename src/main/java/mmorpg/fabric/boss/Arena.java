package mmorpg.fabric.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import mmorpg.core.Json;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Props;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.nav.Nav;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 보스 제단과 보스 필드 (arena.js) — 제단 목록 (세계 동적 속성 rpg:altars, 애드온과 같은 JSON) · 필드 크기 · 안에 있는지 · 관리 권한.
 * 좌표: 제단 블록 자리 (x, y, z) = 필드 가운데. 바닥은 y - 1 층, 플레이어는 y 층에 섬.
 * <p>
 * 신전 설계 9종 = {@link ArenaThemes} (마인크래프트 없이 — 애드온과 같은 칸 · 같은 블록 · 같은 씨앗, 테마 도우미 deepTile · isleSize · rootAt · discSide …),
 * 베드락 블록 → 자바 = {@link ArenaBlocks}, 단계별 짓기 · 확인 창 = {@link ArenaBuild}, 제단 블록 9종 = {@link AltarBlocks},
 * 필드 보호 · 제단 놓기 · 누르기 · 초기화 = {@link ArenaGuard}. 보스 엔진 (Bosses) 은 이 클래스의 함수를 부름 (테마 도우미는 ArenaThemes 를 바로).
 */
public final class Arena {
    private Arena() {}

    /** 세계 동적 속성 이름 (애드온과 같음) */
    public static final String KEY = "rpg:altars";
    private static final Rules R = Rules.get();
    public static final Obj BOSSES = R.obj("BOSSES");
    public static final Obj BOSS = R.obj("BOSS");

    /** 제단 (arena.js Altar): 보스 키 · 차원 · 블록 자리, 필드를 지었는지 · 재사용 대기가 끝나는 때 (실제 시간 ms) */
    public static final class Altar {
        public final String key;
        public final String dim;
        public final int x;
        public final int y;
        public final int z;
        public boolean built;
        public long cooldownUntil;

        public Altar(String key, String dim, int x, int y, int z, boolean built, long cooldownUntil) {
            this.key = key;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.built = built;
            this.cooldownUntil = cooldownUntil;
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        /** 길 안내 쪽 모양 */
        public Nav.Altar nav() {
            return new Nav.Altar(key, dim, x, y, z);
        }

        @Override
        public String toString() {
            return key + "@" + dim + " " + x + " " + y + " " + z;
        }
    }

    /** 필드 크기 (보스 설정 arena.radius · height) */
    public record Size(int radius, int height) {}

    private static MinecraftServer server;
    private static List<Altar> cache;
    private static Consumer<Altar> onRemoved;

    /** Mmorpg.onInitialize: 서버를 기억 (애드온의 world 처럼 어디서나 목록을 읽게), 길 안내 훅 */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            cache = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            cache = null;
        });
        Nav.altars = () -> {
            List<Nav.Altar> out = new ArrayList<>();
            for (Altar a : altars()) out.add(a.nav());
            return out;
        };
    }

    /** 지금 서버 (없으면 null) */
    public static MinecraftServer server() {
        return server;
    }

    // ------------------------------------------------------------ 제단 목록

    /** 제단 목록 (세계에 저장 — 고친 뒤 saveAltars) */
    public static List<Altar> altars() {
        if (cache == null) {
            cache = new ArrayList<>();
            if (server != null) {
                try {
                    String raw = Props.world(server, KEY);
                    if (raw != null && !raw.isEmpty() && Json.parse(raw) instanceof List<?> list) {
                        for (Object o : list) {
                            if (!(o instanceof java.util.Map<?, ?> m) || !(m.get("key") instanceof String key) || BOSSES.objOrNull(key) == null) continue;
                            cache.add(new Altar(key, String.valueOf(m.get("dim")), num(m.get("x")), num(m.get("y")), num(m.get("z")),
                                    Boolean.TRUE.equals(m.get("built")), m.get("cooldownUntil") instanceof Number n ? n.longValue() : 0L));
                        }
                    }
                } catch (RuntimeException e) {
                    cache = new ArrayList<>();
                }
            }
        }
        return cache;
    }

    private static int num(Object o) {
        return o instanceof Number n ? (int) Math.floor(n.doubleValue()) : 0;
    }

    public static void saveAltars() {
        if (server == null) return;
        List<Object> out = new ArrayList<>();
        for (Altar a : altars()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("key", a.key);
            m.put("dim", a.dim);
            m.put("x", a.x);
            m.put("y", a.y);
            m.put("z", a.z);
            m.put("built", a.built);
            m.put("cooldownUntil", a.cooldownUntil);
            out.add(m);
        }
        Props.setWorld(server, KEY, Json.stringify(out));
    }

    /** 제단 블록 ID (rpg:altar_kraken) → 보스 키 (제단이 아니면 null) */
    public static String altarKeyOf(String blockId) {
        if (blockId == null || !blockId.startsWith("rpg:altar_")) return null;
        String k = blockId.substring("rpg:altar_".length());
        return BOSSES.objOrNull(k) != null ? k : null;
    }

    /** 이 블록 자리의 제단 (없으면 null) */
    public static Altar altarAt(String dim, BlockPos pos) {
        for (Altar a : altars()) if (a.dim.equals(dim) && a.x == pos.getX() && a.y == pos.getY() && a.z == pos.getZ()) return a;
        return null;
    }

    public static Altar addAltar(String key, String dim, BlockPos pos) {
        Altar found = altarAt(dim, pos);
        if (found != null) return found;
        Altar a = new Altar(key, dim, pos.getX(), pos.getY(), pos.getZ(), false, 0L);
        altars().add(a);
        saveAltars();
        return a;
    }

    /** 제단이 없어질 때 (보스 엔진이 싸움을 정리) */
    public static void setAltarRemovedHook(Consumer<Altar> fn) {
        onRemoved = fn;
    }

    public static void removeAltar(Altar a) {
        if (!altars().remove(a)) return;
        saveAltars();
        if (onRemoved != null) {
            try {
                onRemoved.accept(a);
            } catch (RuntimeException e) {
                mmorpg.fabric.Mmorpg.LOG.warn("[mmorpg] altar removed hook", e);
            }
        }
    }

    /** 필드 크기 */
    public static Size arenaSize(Altar a) {
        Obj ar = BOSSES.obj(a.key).obj("arena");
        return new Size((int) ar.num("radius"), (int) ar.num("height"));
    }

    /** 가운데 (제단 블록 위 가운데) */
    public static Vec3 arenaCenter(Altar a) {
        return new Vec3(a.x + 0.5, a.y, a.z + 0.5);
    }

    /** loc 이 필드 안인지 (가로 반지름 + margin, 높이는 바닥 3칸 아래 ~ 천장) */
    public static boolean inArena(Altar a, String dim, Vec3 loc, double margin) {
        if (!a.dim.equals(dim)) return false;
        Size s = arenaSize(a);
        Vec3 c = arenaCenter(a);
        if (loc.y < a.y - 3 || loc.y > a.y + s.height() + 1) return false;
        return Math.hypot(loc.x - c.x, loc.z - c.z) <= s.radius() + margin;
    }

    /** 이 블록 자리를 품은 지어진 필드 (보호용: 울타리 고리까지, 칸 가운데로 잼, 위로는 끝까지) — 없으면 null */
    public static Altar builtArenaAt(String dim, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        for (Altar a : altars()) {
            if (!a.built || !a.dim.equals(dim) || pos.getY() < a.y - 4) continue;
            Vec3 c = arenaCenter(a);
            if (Math.hypot(x - c.x, z - c.z) <= arenaSize(a).radius() + 2.1) return a;
        }
        return null;
    }

    /** 관리할 수 있는 사람 (운영자 또는 크리에이티브) */
    public static boolean canManage(ServerPlayer p) {
        if (p == null) return false;
        return p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || p.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    /** 가장 가까운 제단 (같은 차원, maxDist 안 — 없으면 null) */
    public static Altar nearestAltar(ServerPlayer p, double maxDist) {
        String dim = NpcData.dimId(p.level());
        Altar best = null;
        double bd = maxDist;
        for (Altar a : altars()) {
            if (!a.dim.equals(dim)) continue;
            double d = arenaCenter(a).distanceTo(p.position());
            if (d <= bd) {
                bd = d;
                best = a;
            }
        }
        return best;
    }

    /** 필드 안의 플레이어 (살아 있는지는 부르는 쪽이) */
    public static List<ServerPlayer> playersIn(Altar a, double margin) {
        ServerLevel level = levelOf(a);
        if (level == null) return List.of();
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (inArena(a, a.dim, p.position(), margin)) out.add(p);
        return out;
    }

    /** 제단의 세계 (없으면 null) */
    public static ServerLevel levelOf(Altar a) {
        return server == null ? null : NpcData.levelOf(server, a.dim);
    }

    // ------------------------------------------------------------ 짓기 · 설계

    /**
     * 필드의 특징 자리 (arena.js arenaFeatures — 월드 좌표, 칸 가운데): 토템 · 분신 자리 · 성문 · 기둥, 해일 그림자 기둥 (half) · 촉수 물구덩이,
     * 굴 입구 · 체크무늬 칸 크기 (tile), 선돌 (sun), 모닥불 · 얼음 첨탑 (h), 사슬 기둥 · 얼음 결정 (half · h · cells · frost) · 불꽃 탑 (h)
     */
    public record Features(List<Vec3> totems, List<Vec3> decoys, List<Vec3> gates, List<Vec3> posts, List<Pillar> pillars, List<Vec3> holes, int tile,
            List<Stone> stones, List<Vec3> fires, List<Spire> spires, List<Column> chains, List<Column> crystals, List<Spire> towers) {
        public static final Features EMPTY = new Features(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    /** 해일 그림자 기둥 (가로 반폭) */
    public record Pillar(Vec3 at, double half) {}

    /** 선돌 (sun = 동쪽 해 선돌) */
    public record Stone(Vec3 at, boolean sun) {}

    /** 높이가 있는 자리 (얼음 첨탑 · 불꽃 탑) */
    public record Spire(Vec3 at, int h) {}

    /** 블록 한 칸 (월드 칸 좌표, 블록 ID, 상태) */
    public record Cell(int x, int y, int z, String block, java.util.Map<String, String> states) {}

    /** 사슬 기둥 · 얼음 결정 (가운데 3×3 반폭 half, 높이 h, 블록 cells, 결정 둘레 서리 바닥 frost) */
    public record Column(Vec3 at, double half, int h, List<Cell> cells, List<Cell> frost) {}

    /** 필드 설계의 특징 자리 (월드 좌표, 칸 가운데 — 테마가 없으면 EMPTY). 설계는 {@link ArenaThemes} (애드온과 같은 칸 · 같은 블록) */
    public static Features arenaFeatures(Altar a) {
        ArenaThemes.Plan plan = arenaPlan(a);
        if (plan == null) return Features.EMPTY;
        ArenaThemes.Feat f = plan.features();
        Vec3 c = arenaCenter(a);
        java.util.function.BiFunction<Integer, Integer, Vec3> to = (dx, dz) -> new Vec3(c.x + dx, a.y, c.z + dz);
        List<Vec3> totems = new ArrayList<>();
        for (ArenaThemes.Spot p : f.totems()) totems.add(to.apply(p.dx(), p.dz()));
        List<Vec3> decoys = new ArrayList<>();
        for (ArenaThemes.Spot p : f.decoys()) decoys.add(to.apply(p.dx(), p.dz()));
        List<Vec3> gates = new ArrayList<>();
        for (ArenaThemes.Spot p : f.gates()) gates.add(to.apply(p.dx(), p.dz()));
        List<Vec3> posts = new ArrayList<>();
        for (ArenaThemes.Spot p : f.posts()) posts.add(to.apply(p.dx(), p.dz()));
        List<Pillar> pillars = new ArrayList<>();
        for (ArenaThemes.PillarAt p : f.pillars()) pillars.add(new Pillar(to.apply(p.dx(), p.dz()), p.half()));
        List<Vec3> holes = new ArrayList<>();
        for (ArenaThemes.Spot p : f.holes()) holes.add(to.apply(p.dx(), p.dz()));
        List<Stone> stones = new ArrayList<>();
        for (ArenaThemes.StoneAt p : f.stones()) stones.add(new Stone(to.apply(p.dx(), p.dz()), p.sun()));
        List<Vec3> fires = new ArrayList<>();
        for (ArenaThemes.Spot p : f.fires()) fires.add(to.apply(p.dx(), p.dz()));
        List<Spire> spires = new ArrayList<>();
        for (ArenaThemes.SpireAt p : f.spires()) spires.add(new Spire(to.apply(p.dx(), p.dz()), p.h()));
        List<Column> chains = new ArrayList<>();
        for (ArenaThemes.ColumnAt p : f.chains()) chains.add(new Column(to.apply(p.dx(), p.dz()), p.half(), p.h(), cellsOf(a, p.cells()), cellsOf(a, p.frost())));
        List<Column> crystals = new ArrayList<>();
        for (ArenaThemes.ColumnAt p : f.crystals()) crystals.add(new Column(to.apply(p.dx(), p.dz()), p.half(), p.h(), cellsOf(a, p.cells()), cellsOf(a, p.frost())));
        List<Spire> towers = new ArrayList<>();
        for (ArenaThemes.SpireAt p : f.towers()) towers.add(new Spire(to.apply(p.dx(), p.dz()), p.h()));
        return new Features(List.copyOf(totems), List.copyOf(decoys), List.copyOf(gates), List.copyOf(posts), List.copyOf(pillars), List.copyOf(holes),
                f.tile() == null ? 0 : f.tile(), List.copyOf(stones), List.copyOf(fires), List.copyOf(spires), List.copyOf(chains), List.copyOf(crystals), List.copyOf(towers));
    }

    /** 설계의 놓을 블록 (제단 기준 dx, dy, dz) → 월드 칸 좌표 (블록 · 상태는 애드온의 베드락 이름 그대로 — 자바 블록은 ArenaBlocks.state(cell)) */
    private static List<Cell> cellsOf(Altar a, List<ArenaThemes.Put> list) {
        List<Cell> out = new ArrayList<>(list.size());
        for (ArenaThemes.Put q : list) {
            java.util.Map<String, String> st = null;
            if (q.states() != null) {
                st = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<String, Object> e : q.states().entrySet()) st.put(e.getKey(), String.valueOf(e.getValue()));
                st = Collections.unmodifiableMap(st);
            }
            out.add(new Cell(a.x + q.dx(), a.y + q.dy(), a.z + q.dz(), q.block(), st));
        }
        return List.copyOf(out);
    }

    /** 제단의 필드 설계 (테마가 없으면 null — arena.js arenaPlan) */
    public static ArenaThemes.Plan arenaPlan(Altar a) {
        return ArenaThemes.arenaPlan(a.key, a.x, a.y, a.z);
    }

    /**
     * 원판의 어느 쪽인지 (스콜과 하티 — arena.js discSide): at = 서 있는 자리, center = 필드 가운데 (제단 칸 가운데), disc = 원판 반지름 →
     * "sun" (해, 동쪽 dx ≥ 1) · "moon" (달, 서쪽 dx ≤ -1) · "line" (제단 줄) · null (원판 밖)
     */
    public static String discSide(Vec3 at, Vec3 center, double disc) {
        return ArenaThemes.discSide(at.x, at.z, center.x, center.z, disc);
    }

    /** 짓는 중인지 */
    public static boolean isBuilding(Altar a) {
        return ArenaBuild.isBuilding(a);
    }

    /** "보스 필드 만들기" 확인 창 (arena.js askBuild — 운영자 · 크리에이티브만, 지을 수 없으면 까닭을 채팅으로) */
    public static void askBuild(ServerPlayer p, Altar a) {
        ArenaBuild.askBuild(p, a);
    }

    /** 필드 짓기 시작 (arena.js buildArena — /boss build · 관리 창 "다시 짓기"). 시작했으면 true */
    public static boolean buildArena(Altar a, ServerPlayer player) {
        return ArenaBuild.buildArena(a, player);
    }

    /** 지을 수 없는 까닭 (없으면 null — arena.js buildProblem) */
    public static String buildProblem(Altar a) {
        return ArenaBuild.buildProblem(a);
    }

    /** 목록의 제단 자리에 제단 블록이 없으면 목록에서 뺌 (arena.js checkAltarBlocks — 200틱마다 저절로) */
    public static void checkAltarBlocks() {
        ArenaBuild.checkAltarBlocks();
    }

    /** 제단 블록 아이템 (/boss altar — 없는 키면 빈 것) */
    public static net.minecraft.world.item.ItemStack altarItem(String key, int amount) {
        return AltarBlocks.item(key, amount);
    }

    /** 지금의 제단 없어짐 훅 (시험이 잠시 바꿨다가 되돌릴 때) */
    public static Consumer<Altar> altarRemovedHook() {
        return onRemoved;
    }

    /** 제단 블록을 누름 (arena.js 의 ALTAR_COMPONENT — 보스 엔진이 정함: 도전 창 · 싸우는 중 체력 · 웅크린 관리자는 짓기 창) */
    public static BiConsumer<ServerPlayer, Altar> onAltarUse = (p, a) -> {};

    /** 목록의 모든 제단 (읽기 전용 사본) */
    public static List<Altar> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(altars()));
    }
}
