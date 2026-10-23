package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossLabel;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.faceAll;
import static mmorpg.fabric.boss.BossKit.fanMarks;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.lineMarks;
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.showFaceText;
import static mmorpg.fabric.boss.BossKit.shuffle;
import static mmorpg.fabric.boss.BossKit.snd;
import static mmorpg.fabric.boss.BossKit.sound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

/**
 * 거대 웜 (boss_worm.js — v2.1.0, Lv.25, 대지의 포식자): 수치는 config BOSSES.great_worm.
 * 심층의 신전의 굴 (가운데 + 대각선 넷) 에 박혀 있음 (움직이지 않는 보스 — 엔진 stationary · seat). 땅속으로 굴을 옮겨 다님 (땅속에 있는 동안 무적).
 * <ul>
 * <li>평타 머리 내려찍기 (앞 110°, 엔진)</li>
 * <li>몸통 휘두르기: 한 사람 쪽으로 몸을 돌려 예고 → 앞 270° 를 머리로 쓸어 버림 (머리가 지나가는 자리부터 맞음). 등 뒤 90° 는 안전</li>
 * <li>굴 이동: 땅속으로 → 사람이 가장 많은 다른 굴 둘레에 예고 → 튀어나옴 (피해 + 띄움)</li>
 * <li>대지 분출 (체력 90% 아래): 바닥 체크무늬의 한쪽 칸이 솟고 곧바로 반대쪽 칸이 솟음 (방금 솟은 칸으로 옮겨야 함)</li>
 * <li>삼키기 (체력 80% 아래): 노란 원이 한 사람을 따라다니다 멈춤 → 원 안의 한 명을 삼킴 (속박 + 침묵 + 실명 + 계속 피해).
 * 나머지가 웜에게 (다른 사람 수 × perHelper) 피해를 주면 뱉고 구역질 (잠깐 패턴 없음). 못 하면 더 다친 채로 뱉음</li>
 * <li>진동 감지 (체력 70% · 45% · 20%): 땅속에서 발소리를 들음 → 웅크리지 않고 걷거나 뛰면 발밑에서 튀어나와 삼킴 (즉사).
 * 듣는 동안 머리 위로 돌이 떨어짐 (웅크려 걸어 나와야 함). 끝나면 아무 굴에서 튀어나옴</li>
 * <li>대지 가르기: 멀리 (제 굴에서 far 칸 넘게) 있는 사람 쪽으로 땅에 금 → 바위가 금을 따라 솟음 (옆으로 비켜서면 피함)</li>
 * <li>높은 곳 (성문 · 담장 탑 위): 땅을 울려 떨어뜨림 (진동 감지 중에는 없음: 떨어지는 소리로 들키지 않게)</li>
 * </ul>
 * 큰 패턴 (굴 이동 · 대지 분출 · 삼키기 · 진동 감지) 은 서로 겹치지 않음 (mem.bigUntil). 삼킨 사람이 있는 동안에는 평타 · 몸통 휘두르기만.
 * 밀어내는 공격은 담장 가까이에서 바깥쪽으로 밀지 않음 (keepIn: 필드 밖 · 탑 위로 날아가지 않게).
 * <p>
 * 자바에서 달라진 점: 구출 진행은 이름표와 함께 보스 막대 이름에도 (베드락은 이름표가 보스 막대에 보임 — 자바 막대는 이름이 따로),
 * 틀 줄이기 (애드온 u.cast.until = …) 는 같은 이름의 짧은 틀로 바꿈 (자바 Cast.until 은 final), 웅크림 = isShiftKeyDown, 타고 있음 = isPassenger.
 * 패턴 함수는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossWorm implements BossModule {
    static final String KEY = "great_worm";
    static final double[] EARTH = {0.6, 0.44, 0.32};
    static final double[] DUST = {0.62, 0.58, 0.54};
    static final double[] ORANGE = {1.0, 0.48, 0.12};
    static final double[] RED = {1.0, 0.22, 0.14};
    static final double[] YELLOW = {1.0, 0.86, 0.2};
    static final double[] SOUND = {0.3, 0.88, 0.96};
    static final double[] BILE = {0.78, 0.86, 0.22};
    static final double[] ROCK = {0.5, 0.47, 0.5};
    static final double[] CRACK = {0.12, 0.08, 0.06};

    /** 몸통 휘두르기 방향: 머리가 (앞 + angle/2) 에서 시작해 (앞 - angle/2) 로 감 (가로 각도: +x 에서 +z 쪽이 +). build.py 의 sweep 동작과 같아야 함 */
    public static final int SWEEP_DIR = 1;
    static final double TAU = Math.PI * 2;
    /** 굴을 옮기거나 땅속에 있는 동안의 동작 틀 (다음 동작이 같은 틀에서 바꿀 때까지 유지) */
    static final String DIG = "rpg_dig";
    /** 삼킨 사람이 든 동안 (꿀꺽 불룩한 모습 유지 → 뱉기가 바꿈) */
    static final String GUT = "rpg_gut";

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (sweep · tunnel · quake · devour · tremor · rend — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    // ------------------------------------------------------------ 기억

    /**
     * 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 큰 패턴이 끝나는 틱 · 알린 규칙 · 지금 굴 번호 · 땅속 (굴 이동) · 대지 분출 · 삼키기 · 진동 감지 ·
     * 진동 감지를 쓴 체력 · 높은 곳 (사람 → 마지막 틱)
     */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new HashMap<>();
        public long bigUntil;
        public final Set<String> told = new HashSet<>();
        public int hole;
        public Under under;
        public Quake quake;
        public Devour devour;
        public Tremor tremor;
        public final Set<Double> tremorDone = new HashSet<>();
        public final Map<UUID, Long> high = new HashMap<>();
    }

    /** 굴 이동 (땅속): 그 마리 · 갈 굴 번호 · 옮기는 틱 · 튀어나오는 틱 · 옮겼는지 · 튀어나올 때의 반지름 · 피해 */
    public static final class Under {
        public final Bosses.Unit u;
        public final int dest;
        public final long moveAt;
        public final long riseAt;
        public boolean moved;
        public final double radius;
        public final double damage;

        Under(Bosses.Unit u, int dest, long moveAt, long riseAt, double radius, double damage) {
            this.u = u;
            this.dest = dest;
            this.moveAt = moveAt;
            this.riseAt = riseAt;
            this.radius = radius;
            this.damage = damage;
        }
    }

    /** 체크무늬 칸: 칸 번호 · 칸 가운데 월드 좌표 · 어두운 칸인지 */
    public record Tile(int i, int j, double x, double z, boolean dark) {}

    /** 한 번의 분출: 어두운 칸인지 · 예고 시작 틱 · 솟는 틱 · 솟았는지 */
    public static final class Wave {
        public final boolean dark;
        public final long from;
        public final long at;
        public boolean done;

        Wave(boolean dark, long from, long at) {
            this.dark = dark;
            this.from = from;
            this.at = at;
        }
    }

    /** 대지 분출: 그 마리 · 칸들 · 두 번의 분출 */
    public static final class Quake {
        public final Bosses.Unit u;
        public final List<Tile> tiles;
        public final List<Wave> waves;

        Quake(Bosses.Unit u, List<Tile> tiles, List<Wave> waves) {
            this.u = u;
            this.tiles = tiles;
            this.waves = waves;
        }
    }

    /**
     * 삼키기: phase "aim" (노림 — target · 시작 틱 · 멈추는 틱 · 덮치는 틱 · 멈춘 자리 spot · 따라다니는 자리 last) →
     * "inside" (삼킴 — victim · 삼킨 틱 t1 · 뱉는 틱 until · 구출에 필요한 피해 need · 준 피해 dealt · 몸 안 자리 inside · 이름표에 보인 수)
     */
    public static final class Devour {
        public final Bosses.Unit u;
        public String phase = "aim";
        public final ServerPlayer target;
        public final long t0;
        public final long lockAt;
        public final long biteAt;
        public Vec3 spot;
        public Vec3 last;
        public ServerPlayer victim;
        public long t1;
        public long until;
        public double need;
        public double dealt;
        public Vec3 inside;
        public long lastShown = -1;

        Devour(Bosses.Unit u, ServerPlayer target, long t0, long lockAt, long biteAt, Vec3 last) {
            this.u = u;
            this.target = target;
            this.t0 = t0;
            this.lockAt = lockAt;
            this.biteAt = biteAt;
            this.last = last;
        }
    }

    /** 진동 감지의 발소리 기록: 지난 자리 · 쌓인 걸음 · 경고했는지 · 셈하지 않는 틱 (돌에 맞아 휘청임) */
    public static final class Track {
        public Vec3 last;
        public double acc;
        public boolean warned;
        public long calm;

        Track(Vec3 last) {
            this.last = last;
        }
    }

    /** 들킨 사람: 발밑에서 튀어나오는 틱 */
    public static final class Strike {
        public final ServerPlayer p;
        public final long at;
        public boolean done;

        Strike(ServerPlayer p, long at) {
            this.p = p;
            this.at = at;
        }
    }

    /** 떨어지는 돌: 자리 · 닿는 틱 · 떨어뜨렸는지 · 닿았는지 */
    public static final class Rock {
        public final Vec3 spot;
        public final long at;
        public boolean fell;
        public boolean done;

        Rock(Vec3 spot, long at) {
            this.spot = spot;
            this.at = at;
        }
    }

    /** 진동 감지 끝: 튀어나올 굴 번호 · 자리 · 틱 */
    public record Rise(int i, Vec3 at, long when) {}

    /** 진동 감지: 시작 · 듣기 시작 · 끝 틱, 발소리 기록 · 들킨 사람 차례 · 떨어지는 돌 · 들킨 사람 · 듣는 사람 (예고를 본 사람) · 끝의 튀어나오기 */
    public static final class Tremor {
        public final Bosses.Unit u;
        public final long t0;
        public final long listenAt;
        public final long endAt;
        public final Map<UUID, Track> track = new LinkedHashMap<>();
        public final List<Strike> queue = new ArrayList<>();
        public final List<Rock> rocks = new ArrayList<>();
        public final Set<UUID> heard = new HashSet<>();
        public Set<UUID> listeners;
        public Rise rise;

        Tremor(Bosses.Unit u, long t0, long listenAt, long endAt) {
            this.u = u;
            this.t0 = t0;
            this.listenAt = listenAt;
            this.endAt = endAt;
        }
    }

    /** 대지 가르기의 금 한 줄: 시작 (웜 몸 가장자리) · 방향 · 길이 */
    public record Line(Vec3 from, Vec3 dir, double L) {}

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    // ------------------------------------------------------------ 도우미

    static long every(Bosses.Unit u, double[] range) {
        return (long) Js.round(rnd((int) range[0], (int) range[1]) * u.cdMult);
    }

    static Vec3 floorAt(Bosses.Unit u, double x, double z) {
        return new Vec3(x, u.center.y, z);
    }

    /** 가로 단위 방향 a → b */
    static Vec3 dirTo(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double L = Math.hypot(dx, dz);
        return L < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(dx / L, 0, dz / L);
    }

    static double angOf(Vec3 d) {
        return Math.atan2(d.z, d.x);
    }

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번 — 0.34.0 BOSS_HINTS.chat 일 때만 보냄, 알렸다는 표시 told 는 늘 남김) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        BossKit.hintSay(u, text);
    }

    /** 몸 가운데 (연출용) */
    static Vec3 mid(Bosses.Unit u) {
        Vec3 l = u.boss.position();
        return new Vec3(l.x, l.y + bossSize(u.def).height() * 0.5, l.z);
    }

    /** 흙먼지 (rpg:dust: 부풀며 사라지는 먼지 구름) */
    static void dust(ServerLevel dim, Vec3 at, double size, double[] color, double life) {
        SkillFx.fx(dim, "rpg:dust", new Vec3(at.x, at.y + size * 0.35, at.z), Opt.of("size", size, "life", life, "color", color, "alpha", 0.9));
    }

    /** 튀는 돌 조각 (rpg:boulder 작게: 위로 던지면 떨어짐) */
    static void chips(ServerLevel dim, Vec3 at, int n, double size, double up) {
        for (int i = 0; i < n; i++) {
            double a = random() * TAU;
            double out = 0.25 + random() * 0.35;
            SkillFx.fx(dim, "rpg:boulder", new Vec3(at.x, at.y + 0.3, at.z), Opt.of("dir", new Vec3(Math.cos(a) * out, 1, Math.sin(a) * out), "speed",
                    up * (0.7 + random() * 0.4), "spin", random() * 360, "size", size * (0.7 + random() * 0.6), "life", 1.2, "color", ROCK, "alpha", 1.0));
        }
    }

    /** 땅이 갈라지는 금 (바닥에 누움, 수명 동안 퍼짐) */
    static void crack(ServerLevel dim, Vec3 at, double size, double life) {
        SkillFx.fx(dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.06, at.z), Opt.of("dir", Vec3.ZERO, "size", size, "life", life, "spin", random() * 360, "color",
                CRACK, "alpha", 0.9));
    }

    static void shakeNear(Bosses.Unit u, Vec3 at, double range, double intensity, double seconds) {
        for (ServerPlayer p : fighters(u)) if (hdist(p.position(), at) <= range) SkillFx.shake(p, intensity, seconds);
    }

    /** 밀어내는 힘 (kx, kz): 담장에서 7칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖 · 담장 탑 위로 날아가지 않게) → {x, z} */
    static double[] keepIn(Bosses.Unit u, ServerPlayer p, double kx, double kz) {
        Vec3 c = u.center;
        double rx = p.getX() - c.x;
        double rz = p.getZ() - c.z;
        double r = Math.hypot(rx, rz);
        if (r < u.enc.def.obj("arena").num("radius") - 7 || r < 1e-6) return new double[] {kx, kz};
        double ux = rx / r;
        double uz = rz / r;
        double out = kx * ux + kz * uz;
        if (out <= 0) return new double[] {kx, kz};
        return new double[] {kx - ux * (out + 0.15), kz - uz * (out + 0.15)};
    }

    /** 무언가를 타고 있는지 (말 · 보트: 땅에 닿지 않아도 뛴 것이 아님) */
    static boolean riding(ServerPlayer p) {
        return p.isPassenger();
    }

    /** 체크무늬 칸 크기 (필드 특징 tile — 없으면 설정 quake.tile) */
    static int tileSize(Bosses.Unit u) {
        return u.feat != null && u.feat.tile() > 0 ? u.feat.tile() : (int) u.def.obj("quake").num("tile");
    }

    static List<Vec3> holes(Bosses.Unit u) {
        return u.feat == null ? List.of() : u.feat.holes();
    }

    /** 플레이어를 그 자리로 옮김 (베드락 teleport — 블록과 겹쳐도, 보는 쪽 그대로) */
    static void movePlayer(ServerPlayer p, Vec3 at) {
        if (p.isRemoved()) return;
        p.teleportTo(p.level(), at.x, at.y, at.z, Relative.ROTATION, 0F, 0F, false);
    }

    /** 이름표 (베드락은 보스 막대에도 이름표가 보임 — 자바 막대는 이름이 따로라 함께 바꿈) */
    static void label(Bosses.Unit u, String text) {
        if (u.boss == null || u.boss.isRemoved()) return;
        u.boss.setNameTag(text);
        ServerBossEvent bar = u.boss.bossBar();
        if (bar != null) bar.setName(Component.literal(BedrockText.toJava(text)));
    }

    // ------------------------------------------------------------ 대상

    /** 삼켜진 사람인지 */
    public static boolean inside(Bosses.Unit u, ServerPlayer p) {
        M m = mem(u);
        Devour D = m == null ? null : m.devour;
        return D != null && "inside".equals(D.phase) && D.victim != null && p != null && D.victim.getUUID().equals(p.getUUID());
    }

    /** 공격이 닿는 사람 (삼켜진 사람은 뺌) */
    public static List<ServerPlayer> targets(Bosses.Unit u) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : fighters(u)) if (!inside(u, p)) out.add(p);
        return out;
    }

    /** 가장 가까운 사람 (없으면 null) */
    static ServerPlayer nearest(Bosses.Unit u, Vec3 at) {
        ServerPlayer best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (ServerPlayer p : targets(u)) {
            double d = hdist(p.position(), at);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ 몸통 휘두르기

    /** 머리가 쓸고 간 각도 (시작 방향에서 휘두르는 쪽으로, 0 ~ 2π) */
    static double sweptTo(double start, double ang) {
        return (((SWEEP_DIR * (start - ang)) % TAU) + TAU) % TAU;
    }

    public static boolean sweep(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("sweep");
        Vec3 home = u.home;
        double radius = d.num("radius");
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), home) <= radius + 1 && Math.abs(p.getY() - home.y) < 4) near.add(p);
        if (near.isEmpty()) return false;
        Bosses.Api api = u.api;
        ServerPlayer t = near.get((int) Math.floor(random() * near.size()));
        Vec3 dir = dirTo(home, t.position());
        Vec3 apex = floorAt(u, home.x, home.z);
        double angle = d.num("angle");
        int windup = (int) d.num("windup");
        int time = (int) d.num("time");
        api.face(u, dir);
        fanMarks(u.dim, apex, dir, radius, angle, windup, ORANGE, 1.5);
        api.anim(u, "sweep");
        sound(u, "mob.warden.sniff", mid(u), 0.7, 1);
        explain(u, "sweep", "§6[보스] 몸통 휘두르기: §f거대 웜이 머리로 앞 " + Js.str(angle) + "°를 쓸어 버립니다. §e웜의 등 뒤(" + Js.str(360 - angle) + "°)나 " + Js.str(radius)
                + "칸 밖으로 피하세요");
        double start = angOf(dir) + (SWEEP_DIR * angle * Math.PI) / 360;
        Set<UUID> hit = new HashSet<>();
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 0; k <= time; k++) {
            int kk = k;
            steps.add(step(windup + k, (x, n) -> sweepStep(x, apex, start, kk, hit)));
        }
        api.cast(u, "sweep", windup + time + 14, steps);
        return true;
    }

    /** 휘두르기 k 번째 틱: 머리가 이번 틱까지 쓸고 간 자리의 사람이 맞음 (한 번만) */
    static void sweepStep(Bosses.Unit u, Vec3 apex, double start, int k, Set<UUID> hit) {
        Obj d = u.def.obj("sweep");
        double radius = d.num("radius");
        double time = d.num("time");
        double arc = (d.num("angle") * Math.PI) / 180;
        double done = (arc * k) / time;
        double prev = k == 0 ? -1 : (arc * (k - 1)) / time;
        double head = start - SWEEP_DIR * done;
        // 머리가 지나가는 자리: 흙먼지 + 돌 조각
        Vec3 hx = new Vec3(apex.x + Math.cos(head) * radius * 0.72, apex.y, apex.z + Math.sin(head) * radius * 0.72);
        dust(u.dim, hx, 1.8, EARTH, 0.6);
        if (k % 3 == 0) chips(u.dim, hx, 2, 0.3, 5);
        if (k % 4 == 0) play(u.dim, "dig.deepslate", hx, 0.7, 1);
        for (ServerPlayer p : targets(u)) {
            if (hit.contains(p.getUUID())) continue;
            double r = hdist(p.position(), apex);
            if (r > radius + 0.3 || Math.abs(p.getY() - apex.y) > 3) continue;
            // 이번 틱에 머리가 지나간 자리의 사람만 (이미 지나간 자리로 들어온 사람은 맞지 않음). 몸 안쪽 (굴 위) 은 휘두르기 한가운데에
            double sw = sweptTo(start, Math.atan2(p.getZ() - apex.z, p.getX() - apex.x));
            boolean reached = r < 1.2 ? k == (int) Math.ceil(time / 2) : sw > prev + 1e-9 && sw <= Math.min(done, arc) + 1e-6;
            if (!reached) continue;
            hit.add(p.getUUID());
            double a = Math.atan2(p.getZ() - apex.z, p.getX() - apex.x);
            // 머리가 가는 쪽 (접선) + 바깥으로 밀림 (담장 가까이는 안쪽으로)
            double tx = SWEEP_DIR * Math.sin(a);
            double tz = -SWEEP_DIR * Math.cos(a);
            double[] kk = keepIn(u, p, tx * 0.9 + Math.cos(a) * 0.5, tz * 0.9 + Math.sin(a) * 0.5);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.4)));
            dust(u.dim, p.position(), 1.4, EARTH, 0.5);
        }
    }

    // ------------------------------------------------------------ 굴 이동 · 튀어나오기

    /** 땅속으로 (무적은 틀이 6틱 뒤에 켬): 흙먼지 + 소리 */
    static void dive(Bosses.Unit u) {
        Vec3 l = u.home;
        u.api.anim(u, "dive", null, DIG, "0");
        sound(u, "mob.warden.dig", l, 0.9, 1);
        for (int i = 0; i < 4; i++) dust(u.dim, new Vec3(l.x + (random() - 0.5) * 3, l.y, l.z + (random() - 0.5) * 3), 2.2, EARTH, 0.9);
        chips(u.dim, l, 5, 0.4, 6);
    }

    /** 굴 이동: 둘레 near 칸 안에 사람이 가장 많은 다른 굴 (같으면 무작위). 없으면 -1 */
    static int pickHole(Bosses.Unit u, List<ServerPlayer> list) {
        Obj d = u.def.obj("tunnel");
        List<Vec3> hs = holes(u);
        M m = mem(u);
        int cur = m == null ? -1 : m.hole;
        double near = d.num("near");
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < hs.size(); i++) if (i != cur) others.add(i);
        if (others.isEmpty()) return -1;
        int[] score = new int[hs.size()];
        int best = -1;
        for (int i : others) {
            int n = 0;
            for (ServerPlayer p : list) if (hdist(p.position(), hs.get(i)) <= near) n++;
            score[i] = n;
            best = Math.max(best, n);
        }
        List<Integer> pool = new ArrayList<>();
        for (int i : others) if (score[i] == best) pool.add(i);
        return pool.get((int) Math.floor(random() * pool.size()));
    }

    public static boolean tunnel(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("tunnel");
        M m = mem(u);
        if (m == null) return false;
        int dest = pickHole(u, list);
        if (dest < 0) return false;
        int sink = (int) d.num("sink");
        int windup = (int) d.num("windup");
        m.bigUntil = now + sink + windup + 40;
        dive(u);
        explain(u, "tunnel", "§6[보스] 굴 이동: §f거대 웜이 땅속(무적)으로 다른 굴로 옮겨 갑니다. §e굴 둘레가 갈라지면 " + Js.str(d.num("radius")) + "칸 밖으로 — 튀어나오며 띄웁니다");
        m.under = new Under(u, dest, now + sink, now + sink + windup, d.num("radius"), d.num("damage"));
        // 튀어나오기는 underTick 이 (그때까지 다른 행동 없음)
        u.api.cast(u, "tunnel", sink + windup + 16, step(6, (x, t) -> x.immune = true));
        return true;
    }

    /** 굴 둘레 예고 (주황 원 + 갈라지는 금 + 흙먼지) */
    static void riseMarks(Bosses.Unit u, Vec3 at, double radius, int ticks) {
        disc(u.dim, at, radius, ticks, ORANGE);
        crack(u.dim, at, radius * 0.9, ticks / 20.0 + 0.3);
        play(u.dim, "mob.warden.dig", at, 0.7, 1);
    }

    static void underTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Under U = m.under;
        if (U == null) return;
        Bosses.Unit u = U.u;
        if (!u.alive || !Bosses.valid(u.boss)) {
            m.under = null;
            return;
        }
        Vec3 h = holes(u).get(U.dest);
        if (!U.moved && now >= U.moveAt) {
            U.moved = true;
            m.hole = U.dest;
            u.api.seat(u, floorAt(u, h.x, h.z), null);
            riseMarks(u, floorAt(u, h.x, h.z), U.radius, (int) (U.riseAt - now));
        }
        if (U.moved && now < U.riseAt) {
            if ((U.riseAt - now) % 6 == 0) {
                double a = random() * TAU;
                double r = random() * U.radius * 0.8;
                dust(u.dim, new Vec3(h.x + Math.cos(a) * r, h.y, h.z + Math.sin(a) * r), 1.3, EARTH, 0.5);
            }
            if ((U.riseAt - now) % 10 == 0) shakeNear(u, h, U.radius + 5, 0.12, 0.3);
            return;
        }
        if (now >= U.riseAt) {
            m.under = null;
            emerge(u, floorAt(u, h.x, h.z), U.radius, U.damage);
        }
    }

    /** 굴에서 튀어나옴: 둘레 radius 칸 안은 피해 + 띄움, 가장 가까운 사람 쪽을 봄 */
    static void emerge(Bosses.Unit u, Vec3 at, double radius, double damage) {
        u.immune = false;
        ServerPlayer t = nearest(u, at);
        u.api.seat(u, at, t != null ? angOf(dirTo(at, t.position())) : null);
        u.api.anim(u, "emerge", null, DIG, null);
        dust(u.dim, at, radius * 0.9, EARTH, 0.9);
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * TAU;
            dust(u.dim, new Vec3(at.x + Math.cos(a) * radius * 0.75, at.y, at.z + Math.sin(a) * radius * 0.75), 1.6, EARTH, 0.7);
        }
        chips(u.dim, at, 10, 0.45, 9);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius / 2.1, "life", 0.5, "color", EARTH, "alpha", 0.9));
        sound(u, "mob.warden.emerge", at, 1.1, 1);
        play(u.dim, "random.explode", at, 0.8, 0.7);
        shakeNear(u, at, radius + 8, 0.3, 0.5);
        for (ServerPlayer p : targets(u)) {
            double r = hdist(p.position(), at);
            if (r > radius + 0.3 || Math.abs(p.getY() - at.y) > 3) continue;
            Vec3 dir = dirTo(at, p.position());
            double[] kk = keepIn(u, p, dir.x * 0.5, dir.z * 0.5);
            bossHit(u, p, damage, Opt.of("knock", knock(kk[0], kk[1], 0.6)));
        }
    }

    // ------------------------------------------------------------ 대지 분출 (체크무늬)

    /** 체크무늬 칸 (필드 + 둘레 2.5칸 안): 칸 가운데 월드 좌표 + 어두운 칸인지 */
    public static List<Tile> quakeTiles(Bosses.Unit u) {
        int size = tileSize(u);
        double R = u.enc.def.obj("arena").num("radius") + 2.5;
        Vec3 c = u.center;
        int n = (int) Math.ceil(R / size) + 1;
        List<Tile> out = new ArrayList<>();
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                double x = c.x + i * size;
                double z = c.z + j * size;
                if (Math.hypot(x - c.x, z - c.z) > R) continue;
                out.add(new Tile(i, j, x, z, (((i + j) % 2) + 2) % 2 == 0));
            }
        }
        return out;
    }

    /** 플레이어가 선 칸이 어두운 칸인지 (바닥 칠하기와 같은 식: 발밑 블록) */
    public static boolean onDark(Bosses.Unit u, ServerPlayer p) {
        return ArenaThemes.deepTile((int) Math.floor(p.getX()) - u.a.x, (int) Math.floor(p.getZ()) - u.a.z, tileSize(u)).dark();
    }

    public static void quake(Bosses.Unit u, long now) {
        Obj d = u.def.obj("quake");
        M m = mem(u);
        if (m == null) return;
        boolean first = random() < 0.5;
        int windup = (int) d.num("windup");
        int gap = (int) d.num("gap");
        m.bigUntil = now + windup + gap + 60;
        m.quake = new Quake(u, quakeTiles(u), List.of(new Wave(first, now, now + windup), new Wave(!first, now + windup, now + windup + gap)));
        u.api.anim(u, "quake");
        sound(u, snd(u, "roar"), mid(u), 0.8, 1);
        BossKit.hintTitle(u, "§6§l대지 분출", first ? "§f어두운 칸이 먼저 솟습니다 §7→ 다음은 밝은 칸" : "§f밝은 칸이 먼저 솟습니다 §7→ 다음은 어두운 칸", fighters(u));
        explain(u, "quake", "§6[보스] 대지 분출: §f바닥 체크무늬의 한쪽 칸이 솟고 곧바로 반대쪽 칸이 솟습니다. §e빛나는 칸에서 벗어나 방금 솟은 칸으로 옮기세요");
        u.api.cast(u, "quake", windup + gap + 14);
        quakeMarks(u, m.quake, 0);
    }

    /** 한 번의 분출 예고: 칸마다 네모 (테두리 + 수명 동안 가운데서 차오름) */
    static void quakeMarks(Bosses.Unit u, Quake Q, int wi) {
        Wave w = Q.waves.get(wi);
        int size = tileSize(u);
        double life = Math.max(0.1, (w.at - w.from) / 20.0);
        double[] col = w.dark ? ORANGE : RED;
        for (Tile t : Q.tiles) {
            if (t.dark() != w.dark) continue;
            SkillFx.fx(u.dim, "rpg:tele_square", new Vec3(t.x(), u.center.y + 0.06, t.z()), Opt.of("dir", Vec3.ZERO, "size", size * 0.47, "life", life, "color", col,
                    "alpha", 0.85, "grow", 1.0));
            SkillFx.fx(u.dim, "rpg:tele_square", new Vec3(t.x(), u.center.y + 0.08, t.z()), Opt.of("dir", Vec3.ZERO, "size", size * 0.47, "life", life, "color", col,
                    "alpha", 0.5, "grow", 0.0));
        }
    }

    static void quakeTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Quake Q = m.quake;
        if (Q == null) return;
        Bosses.Unit u = Q.u;
        if (!u.alive) {
            m.quake = null;
            return;
        }
        Obj d = u.def.obj("quake");
        for (int wi = 0; wi < Q.waves.size(); wi++) {
            Wave w = Q.waves.get(wi);
            if (wi > 0 && now == w.from) quakeMarks(u, Q, wi);
            if (w.done || now < w.at) continue;
            w.done = true;
            double y = u.center.y;
            // 흙먼지는 칸의 절반쯤만 (한 틱에 파티클이 너무 몰리지 않게: 모바일)
            for (Tile t : Q.tiles) {
                if (t.dark() != w.dark) continue;
                if ((t.i() * 7 + t.j() * 3) % 2 == 0) dust(u.dim, new Vec3(t.x(), y, t.z()), 1.9, EARTH, 0.6);
                if (random() < 0.15) chips(u.dim, new Vec3(t.x(), y, t.z()), 1, 0.35, 6);
            }
            sound(u, "random.explode", u.center, 0.6, 0.8);
            play(u.dim, "dig.deepslate", u.center, 0.6, 1);
            for (ServerPlayer p : targets(u)) {
                if (Math.abs(p.getY() - y) > 3 || onDark(u, p) != w.dark) continue;
                if (bossHit(u, p, d.num("damage"), Opt.of("knock", knock(0, 0, d.num("lift"))))) SkillFx.shake(p, 0.25, 0.4);
            }
            if (wi == Q.waves.size() - 1) m.quake = null;
        }
    }

    // ------------------------------------------------------------ 삼키기

    /** 머리가 닿는 곳까지만 (reach 칸 밖이면 그 방향으로 reach 칸) */
    static Vec3 inReach(Bosses.Unit u, Vec3 at) {
        double reach = u.def.obj("devour").num("reach");
        Vec3 h = u.home;
        double dx = at.x - h.x;
        double dz = at.z - h.z;
        double L = Math.hypot(dx, dz);
        return L <= reach ? floorAt(u, at.x, at.z) : floorAt(u, h.x + (dx * reach) / L, h.z + (dz * reach) / L);
    }

    public static boolean devour(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("devour");
        M m = mem(u);
        if (m == null) return false;
        Vec3 home = u.home;
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), home) <= d.num("range") && Math.abs(p.getY() - home.y) < 4) cand.add(p);
        if (cand.isEmpty()) return false;
        ServerPlayer t = cand.get((int) Math.floor(random() * cand.size()));
        int windup = (int) d.num("windup");
        m.bigUntil = now + windup + (long) d.num("hold") + 80;
        m.devour = new Devour(u, t, now, now + windup - (long) d.num("lock"), now + windup, inReach(u, t.position()));
        u.api.face(u, dirTo(home, t.position()));
        u.api.anim(u, "rear");
        sound(u, "mob.warden.sniff", mid(u), 1.1, 1);
        BossKit.hintFace(t, "§e§l노림! §f원 밖으로 피하세요", Opt.of("dist", 2.2, "up", 0.35));
        explain(u, "devour", "§6[보스] 삼키기: §f노란 원이 한 사람을 따라다니다 붉게 멈추면 거대 웜이 덮칩니다. 원 안의 한 명은 삼켜져 §c속박·침묵·실명 + 계속 피해§f. §e나머지가 웜을 때려 뱉게 하세요");
        u.api.cast(u, "devour", windup + 12);
        return true;
    }

    /** 삼킨 사람을 둘 자리 (웜 몸 안: 굴 가운데, 가운데 굴은 제단 위) */
    static Vec3 gutSpot(Bosses.Unit u) {
        Vec3 h = u.home;
        Vec3 c = u.center;
        boolean atAltar = Math.hypot(h.x - c.x, h.z - c.z) < 0.5;
        return new Vec3(h.x, h.y + (atAltar ? 1 : 0), h.z);
    }

    static void bite(Bosses.Unit u, Devour D, long now) {
        Obj d = u.def.obj("devour");
        M m = mem(u);
        Vec3 spot = D.spot != null ? D.spot : D.last;
        // 머리가 닿는 거리에 맞는 덮치기 (크기 3에서 약 4.6 · 7.2 · 9.2칸, build.py WORM_LUNGES)
        double k = hdist(spot, u.home);
        u.api.anim(u, k <= 5.5 ? "lunge_near" : k <= 8.5 ? "lunge" : "lunge_far");
        dust(u.dim, spot, 2.2, EARTH, 0.6);
        play(u.dim, "mob.warden.attack", spot, 0.8, 1);
        double radius = d.num("radius");
        List<ServerPlayer> caught = new ArrayList<>();
        for (ServerPlayer p : targets(u)) if (hdist(p.position(), spot) <= radius + 0.3 && Math.abs(p.getY() - spot.y) < 3) caught.add(p);
        caught.sort((a, b) -> Double.compare(hdist(a.position(), spot), hdist(b.position(), spot)));
        if (caught.isEmpty()) {
            // 빗나감
            m.devour = null;
            m.bigUntil = now + 40;
            chips(u.dim, spot, 4, 0.3, 5);
            return;
        }
        ServerPlayer v = caught.get(0);
        for (ServerPlayer p : caught.subList(1, caught.size())) {
            Vec3 dir = dirTo(spot, p.position());
            double[] kk = keepIn(u, p, dir.x * 0.8, dir.z * 0.8);
            bossHit(u, p, d.num("splash"), Opt.of("knock", knock(kk[0], kk[1], 0.4)));
        }
        int helpers = Math.max(1, fighters(u).size() - 1);
        int hold = (int) d.num("hold");
        D.phase = "inside";
        D.victim = v;
        D.t1 = now;
        D.until = now + hold;
        D.need = helpers * d.num("perHelper");
        D.dealt = 0;
        D.inside = gutSpot(u);
        D.lastShown = -1;
        m.bigUntil = now + hold + 80;
        Status.root(v, hold + 40, "삼켜짐");
        Status.silence(v, hold + 40);
        BossKit.addEffect(v, "blindness", 40, 0, false);
        movePlayer(v, D.inside);
        u.api.anim(u, "bulge", null, GUT, "0");
        play(u.dim, "random.eat", spot, 0.6, 1);
        String name = v.getName().getString();
        BossKit.hintTitle(u, "§4§l삼켜졌습니다", "§7동료가 웜을 때리면 뱉어 냅니다 — 버티세요", List.of(v));
        BossKit.hintTitle(u, "§e§l" + name + "님이 삼켜졌습니다!", "§f웜에게 피해 " + Js.str(D.need) + "를 주면 뱉어 냅니다", targets(u));
        BossKit.hintSay(u, "§6[보스] §e" + name + "§f님이 삼켜졌습니다! §7(웜에게 피해 " + Js.str(D.need) + "를 주면 뱉어 냄, " + Js.str(Js.round(hold / 20.0)) + "초)");
        nameTag(u, D);
    }

    /** 이름표 (· 보스 막대) 에 구출 진행 (평소에는 보스 막대와 같은 이름 — 0.34.0 진행 막대 · 숫자만, 이름 · "구출" 글자는 뺌) */
    static void nameTag(Bosses.Unit u, Devour D) {
        String base = bossLabel(u.def.num("level"), u.def.strOr("title", ""), u.def.str("name"));
        if (D == null || D.victim == null) {
            label(u, base);
            return;
        }
        int n = (int) Math.max(0, Math.min(10, Math.floor((D.dealt / D.need) * 10)));
        label(u, base + " §a" + "|".repeat(n) + "§8" + "|".repeat(10 - n) + " §f"
                + Js.str(Math.min(D.need, Js.round(D.dealt))) + "/" + Js.str(D.need));
    }

    /** 뱉음: ok = 구출 (웜 구역질), 아니면 시간이 다 됨 (더 다친 채로) */
    static void spit(Bosses.Unit u, Devour D, boolean ok, long now) {
        Obj d = u.def.obj("devour");
        M m = mem(u);
        ServerPlayer v = D.victim;
        int gag = (int) d.num("gag");
        m.devour = null;
        m.bigUntil = now + 60 + (ok ? gag : 0);
        nameTag(u, null);
        u.api.anim(u, "spit", null, GUT, null);
        // 뱉는 쪽: 대각선 굴은 필드 가운데 쪽 (바깥쪽 장식 · 담장 속에 떨어지지 않게), 가운데 굴은 보는 쪽
        Vec3 dir = new Vec3(0, 0, 1);
        if (hdist(u.home, u.center) > 0.5) dir = dirTo(u.home, u.center);
        else if (u.boss != null) {
            Vec3 vd = u.boss.getViewVector(1F);
            double L = Math.hypot(vd.x, vd.z);
            if (L > 1e-3) dir = new Vec3(vd.x / L, 0, vd.z / L);
        }
        u.api.face(u, dir);
        Vec3 out = floorAt(u, u.home.x + dir.x * 4, u.home.z + dir.z * 4);
        SkillFx.fx(u.dim, "rpg:splash", new Vec3(out.x, out.y + 1.4, out.z), Opt.of("size", 2.0, "life", 0.6, "color", BILE, "alpha", 0.95));
        play(u.dim, "random.burp", out, 0.6, 1);
        if (v != null && !v.isRemoved()) {
            Status.unroot(v);
            Status.unsilence(v);
            v.removeEffect(MobEffects.BLINDNESS);
            movePlayer(v, new Vec3(out.x, out.y + 0.6, out.z));
            Kit.knockback(v, dir.x * 0.8, dir.z * 0.8, 0.45);
            if (!ok) bossHit(u, v, d.num("fail"), Opt.of("cause", "magic"));
            BossKit.hintFace(v, ok ? "§a§l뱉어 냈습니다!" : "§c§l뱉어 냈습니다 §7(소화되다 나옴)", Opt.of("dist", 2.2, "up", 0.3));
        }
        if (ok) {
            u.staggerUntil = now + gag;
            u.cast = null;
            u.api.anim(u, "gag");
            BossKit.hintTitle(u, "§a§l구출!", "§f거대 웜이 구역질합니다 §7(" + Js.str(Js.round(gag / 20.0)) + "초)", targets(u));
        } else BossKit.hintSay(u, "§6[보스] §f거대 웜이 §e" + (v != null ? v.getName().getString() : "누군가") + "§f님을 뱉었습니다 §7(구하지 못함)");
    }

    /** 삼킨 사람을 놓아 줌 (죽음 · 나감 · 싸움 끝): 뱉는 연출 없이 상태만 풂 */
    static void release(Bosses.Unit u, Devour D) {
        ServerPlayer v = D.victim;
        M m = mem(u);
        if (m != null && m.devour == D) m.devour = null;
        if (Bosses.valid(u.boss)) {
            nameTag(u, null);
            u.api.anim(u, "spit", null, GUT, null); // 불룩한 모습 (유지 동작) 을 끝냄
        }
        if (v != null && !v.isRemoved()) {
            Status.unroot(v);
            Status.unsilence(v);
            v.removeEffect(MobEffects.BLINDNESS);
        }
    }

    static void devourTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Devour D = m.devour;
        if (D == null) return;
        Bosses.Unit u = D.u;
        Obj d = u.def.obj("devour");
        if (!u.alive || !Bosses.valid(u.boss)) {
            release(u, D);
            return;
        }
        double radius = d.num("radius");
        if ("aim".equals(D.phase)) {
            ServerPlayer t = D.target;
            if (now < D.lockAt) {
                if (okTarget(t) && fighters(u).contains(t)) {
                    D.last = inReach(u, t.position()); // 머리가 닿는 곳 밖으로는 따라가지 않음
                    u.api.face(u, dirTo(u.home, t.position()));
                }
                if ((now - D.t0) % 3 == 0) disc(u.dim, D.last, radius, 4, YELLOW, Opt.of("fill", false));
                return;
            }
            if (D.spot == null) {
                D.spot = D.last;
                disc(u.dim, D.spot, radius, (int) (D.biteAt - now), RED);
                play(u.dim, "mob.warden.clicking", D.spot, 1, 1);
            }
            if (now >= D.biteAt) bite(u, D, now);
            return;
        }
        // 삼킨 뒤
        ServerPlayer v = D.victim;
        if (v == null || v.isRemoved() || !okTarget(v)) {
            release(u, D);
            return;
        }
        if (hdist(v.position(), D.inside) > 0.6 || Math.abs(v.getY() - D.inside.y) > 1.2) movePlayer(v, D.inside);
        // 소화: 1초마다 (마지막은 뱉기 11틱 전: 못 구했을 때의 추가 피해가 플레이어 무적 시간에 줄지 않게)
        if ((now - D.t1) % 20 == 9) {
            bossHit(u, v, d.num("dot"), Opt.of("cause", "magic"));
            BossKit.addEffect(v, "blindness", 40, 0, false);
            long left = (long) Math.ceil((D.until - now) / 20.0);
            showFaceText(v, "§4§l" + left, Opt.of("dist", 2.2, "up", 0.3));
            play(u.dim, "mob.warden.heartbeat", D.inside, 0.8, 1);
        }
        if (now % 5 == 0 && D.lastShown != (long) Js.round(D.dealt)) {
            D.lastShown = (long) Js.round(D.dealt);
            nameTag(u, D);
        }
        if (D.dealt >= D.need) spit(u, D, true, now);
        else if (now >= D.until) spit(u, D, false, now);
    }

    // ------------------------------------------------------------ 진동 감지

    public static void tremor(Bosses.Unit u, long now) {
        Obj d = u.def.obj("tremor");
        M m = mem(u);
        if (m == null) return;
        int warn = (int) d.num("warn");
        int listen = (int) d.num("listen");
        m.bigUntil = Long.MAX_VALUE / 4; // 끝 (튀어나오기) 에서 풂
        m.tremor = new Tremor(u, now, now + warn, now + warn + listen);
        dive(u);
        sound(u, "shriek.sculk_shrieker", mid(u), 0.8, 1);
        BossKit.hintTitle(u, "§3§l진동 감지", "§f움직이지 마세요! §7웅크려 걸으면 들키지 않습니다", fighters(u));
        explain(u, "tremor", "§3[보스] 진동 감지: §f거대 웜이 땅속(무적)에서 " + Js.str(Js.round(listen / 20.0))
                + "초 동안 발소리를 듣습니다. §c걷거나 뛰면 발밑에서 튀어나와 삼킵니다 (즉사). §e멈추거나 웅크려 걸으세요 §7— 떨어지는 돌은 웅크려 걸어서 피하세요");
        countdown(u, warn);
        // 끝은 tremorTick 이 (길게 잡고 튀어나온 뒤 줄임)
        u.api.cast(u, "tremor", warn + listen + 400, step(6, (x, t) -> x.immune = true));
    }

    /** 듣기 전 3 · 2 · 1 (듣기까지 남은 틱이 20의 배수이고 3초 이하일 때) */
    static void countdown(Bosses.Unit u, long left) {
        if (left % 20 == 0 && left <= 60) faceAll(u, "§3§l" + (left / 20), Opt.of("dist", 2.4, "up", 0.45), fighters(u));
    }

    /** 들킴: strike 틱 뒤 (앞의 것과 gap 틱 간격) 발밑에서 튀어나와 삼킴 */
    static void heard(Bosses.Unit u, Tremor T, ServerPlayer p, long now) {
        Obj d = u.def.obj("tremor");
        T.heard.add(p.getUUID());
        long lastAt = T.queue.isEmpty() ? Long.MIN_VALUE / 4 : T.queue.get(T.queue.size() - 1).at;
        T.queue.add(new Strike(p, Math.max(now + (long) d.num("strike"), lastAt + (long) d.num("gap"))));
        BossKit.hintFace(p, "§4§l들켰습니다!", Opt.of("dist", 2.2, "up", 0.35));
        play(u.dim, "mob.warden.listening_angry", p.position(), 1, 1);
        disc(u.dim, floorAt(u, p.getX(), p.getZ()), 2.2, (int) d.num("strike"), RED);
    }

    /** 발밑에서 튀어나와 삼킴 (즉사): 웜이 그 자리로 옮겨 튀어나왔다 다시 들어감 */
    static void strike(Bosses.Unit u, Strike q) {
        ServerPlayer p = q.p;
        q.done = true;
        if (!okTarget(p) || !fighters(u).contains(p)) return;
        Vec3 at = floorAt(u, p.getX(), p.getZ());
        u.api.seat(u, at, null);
        u.api.anim(u, "strike", null, DIG, "0");
        dust(u.dim, at, 3.2, EARTH, 0.8);
        chips(u.dim, at, 8, 0.4, 9);
        crack(u.dim, at, 2.6, 1.2);
        sound(u, "mob.warden.emerge", at, 1.3, 1);
        play(u.dim, "random.eat", at, 0.5, 1);
        shakeNear(u, at, 10, 0.3, 0.5);
        BossKit.hintSay(u, "§3[보스] §e" + p.getName().getString() + "§f님이 발소리를 들켜 삼켜졌습니다");
        Status.kill(p, u.boss);
    }

    static void tremorTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Tremor T = m.tremor;
        if (T == null) return;
        Bosses.Unit u = T.u;
        Obj d = u.def.obj("tremor");
        if (!u.alive || !Bosses.valid(u.boss)) {
            m.tremor = null;
            return;
        }
        double floorY = u.center.y;
        List<Vec3> hs = holes(u);
        // 듣는 소리: 굴마다 퍼지는 물결 (예고 · 듣는 동안)
        if (now < T.endAt && (now - T.t0) % 10 == 0) {
            for (Vec3 h : hs) {
                SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(h.x, floorY + 0.07, h.z), Opt.of("dir", Vec3.ZERO, "speed", 9.0, "size", 1.0, "life", 1.0, "color", SOUND,
                        "alpha", 0.75));
            }
        }
        if (now < T.listenAt) {
            countdown(u, T.listenAt - now);
            return;
        }
        if (now < T.endAt) {
            List<ServerPlayer> cur = fighters(u);
            if (now == T.listenAt) {
                sound(u, "mob.warden.listening", u.center, 0.8, 1);
                BossKit.hintFaceAll(u, "§b§l듣는 중… §7움직이면 들킵니다", Opt.of("dist", 2.4, "up", 0.45), cur);
                // 예고를 본 사람만 들음 (듣는 중에 필드에 들어온 사람은 듣지 않음)
                T.listeners = new HashSet<>();
                for (ServerPlayer p : cur) T.listeners.add(p.getUUID());
            }
            if ((now - T.listenAt) % 20 == 0) play(u.dim, "mob.warden.heartbeat", u.center, 0.7, 1);
            // 필드를 나간 사람의 기록은 지움 (돌아오면 그 자리부터 새로 셈)
            Set<UUID> ids = new HashSet<>();
            for (ServerPlayer p : cur) ids.add(p.getUUID());
            T.track.keySet().removeIf(id -> !ids.contains(id));
            // 발소리 듣기: 웅크리지 않고 grace 칸 넘게 움직였거나 뛰면 들킴 (웅크리고 뛰어도 들킴)
            double grace = d.num("grace");
            for (ServerPlayer p : cur) {
                if (T.heard.contains(p.getUUID()) || T.listeners == null || !T.listeners.contains(p.getUUID())) continue;
                Track rec = T.track.get(p.getUUID());
                if (rec == null) {
                    T.track.put(p.getUUID(), new Track(p.position()));
                    continue;
                }
                double moved = hdist(p.position(), rec.last);
                rec.last = p.position();
                if (now < rec.calm) continue; // 돌에 맞아 휘청이는 동안은 셈하지 않음
                boolean sneaking = p.isShiftKeyDown();
                if (!sneaking && moved > 0.02) rec.acc += moved;
                boolean jumped = !riding(p) && !p.onGround() && p.getY() - floorY > 0.2;
                if (jumped || rec.acc > grace) heard(u, T, p, now);
                else if (!rec.warned && rec.acc > grace * 0.4) {
                    rec.warned = true;
                    BossKit.hintFace(p, "§e§l쉿! §7움직이면 들킵니다 — 웅크리세요", Opt.of("dist", 2.2, "up", 0.35));
                }
            }
            // 떨어지는 돌: 한 사람씩 머리 위 원 → 떨어짐 (웅크려 걸어 나와야 함)
            List<Double> rockAt = d.numbersOrNull("rockAt");
            if (rockAt != null) {
                for (double k : rockAt) {
                    if (now != T.listenAt + (long) k) continue;
                    List<ServerPlayer> pool = new ArrayList<>();
                    for (ServerPlayer p : fighters(u)) if (!T.heard.contains(p.getUUID())) pool.add(p);
                    pool = shuffle(pool);
                    int rocks = (int) d.num("rocks");
                    int rockWindup = (int) d.num("rockWindup");
                    for (ServerPlayer p : pool.subList(0, Math.min(rocks, pool.size()))) {
                        Vec3 spot = floorAt(u, p.getX(), p.getZ());
                        disc(u.dim, spot, d.num("rockRadius"), rockWindup, ORANGE);
                        T.rocks.add(new Rock(spot, now + rockWindup));
                    }
                }
            }
        }
        // 돌: 떨어지기 18틱 전에 위에서 떨어뜨림 (중력으로 원 가운데에), 닿으면 원 안 피해 (밀지 않음: 밀리면 발소리가 남)
        for (Rock rk : T.rocks) {
            if (!rk.fell && now >= rk.at - 18) {
                rk.fell = true;
                SkillFx.fx(u.dim, "rpg:boulder", new Vec3(rk.spot.x, rk.spot.y + 8.1, rk.spot.z), Opt.of("dir", new Vec3(0, -1, 0), "speed", 0.0, "spin", random() * 360,
                        "size", 1.1, "life", 0.95, "color", ROCK, "alpha", 1.0));
            }
            if (rk.done || now < rk.at) continue;
            rk.done = true;
            dust(u.dim, rk.spot, 1.8, DUST, 0.6);
            chips(u.dim, rk.spot, 3, 0.3, 4);
            play(u.dim, "dig.deepslate", rk.spot, 0.6, 1);
            for (ServerPlayer p : targets(u)) {
                if (hdist(p.position(), rk.spot) > d.num("rockRadius") + 0.3 || Math.abs(p.getY() - floorY) > 3) continue;
                // 떨어지는 돌: 때린 엔티티 없이 (게임이 웜 쪽에서 밀어내지 않게), 맞은 사람은 calm 틱 동안 발소리를 셈하지 않음
                if (bossHit(u, p, d.num("rockDamage"), Opt.of("cause", "fallingBlock", "noSource", true))) {
                    Track rec = T.track.get(p.getUUID());
                    if (rec != null) rec.calm = now + (long) d.num("calm");
                }
            }
        }
        // 들킨 사람: 차례로 발밑에서
        for (Strike q : List.copyOf(T.queue)) if (!q.done && now >= q.at) strike(u, q);
        if (now < T.endAt) return;
        for (Strike q : T.queue) if (!q.done) return;
        for (Rock r : T.rocks) if (!r.done) return;
        // 끝: 아무 굴에 예고 → 튀어나옴
        if (T.rise == null) {
            int i = (int) Math.floor(random() * Math.max(1, hs.size()));
            Vec3 h = i < hs.size() ? hs.get(i) : u.center;
            T.rise = new Rise(i, floorAt(u, h.x, h.z), now + (long) d.num("emerge"));
            u.api.seat(u, T.rise.at(), null);
            riseMarks(u, T.rise.at(), d.num("radius"), (int) d.num("emerge"));
            BossKit.hintFaceAll(u, "§a§l소리를 내도 됩니다", Opt.of("dist", 2.4, "up", 0.45), fighters(u));
            return;
        }
        if (now < T.rise.when()) return;
        m.tremor = null;
        m.hole = T.rise.i();
        m.bigUntil = now + 60;
        emerge(u, T.rise.at(), d.num("radius"), d.num("damage"));
        // 애드온 u.cast.until = now + 16 (자바 Cast.until 은 final → 같은 이름의 짧은 틀로)
        if (u.cast != null && "tremor".equals(u.cast.id)) u.api.cast(u, "tremor", 16);
    }

    // ------------------------------------------------------------ 대지 가르기 (멀리 있는 사람)

    /** 금 한 줄: 웜 몸 가장자리 (굴에서 2칸) 에서 대상 쪽으로 대상 너머 over 칸까지, 필드 가장자리 (반지름 + 2) 에서 멈춤 */
    static Line rendLine(Bosses.Unit u, ServerPlayer p) {
        Obj d = u.def.obj("rend");
        Vec3 home = u.home;
        Vec3 dir = dirTo(home, p.position());
        Vec3 from = floorAt(u, home.x + dir.x * 2, home.z + dir.z * 2);
        double L = Math.max(2, hdist(p.position(), from) + d.num("over"));
        double edge = u.enc.def.obj("arena").num("radius") + 2;
        double ox = from.x - u.center.x;
        double oz = from.z - u.center.z;
        double b = ox * dir.x + oz * dir.z;
        double q = b * b - (ox * ox + oz * oz - edge * edge);
        if (q >= 0) L = Math.max(2, Math.min(L, -b + Math.sqrt(q)));
        return new Line(from, dir, L);
    }

    public static boolean rend(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("rend");
        Vec3 home = u.home;
        List<ServerPlayer> far = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), home) > d.num("far") && Math.abs(p.getY() - home.y) < 4) far.add(p);
        if (far.isEmpty()) return false;
        List<ServerPlayer> pool = shuffle(far);
        List<Line> lines = new ArrayList<>();
        for (ServerPlayer p : pool.subList(0, Math.min((int) d.num("count"), pool.size()))) lines.add(rendLine(u, p));
        int windup = (int) d.num("windup");
        u.api.face(u, lines.get(0).dir());
        u.api.anim(u, "rend");
        sound(u, snd(u, "roar"), mid(u), 0.7, 1.2);
        for (Line ln : lines) {
            Vec3 to = new Vec3(ln.from().x + ln.dir().x * ln.L(), ln.from().y, ln.from().z + ln.dir().z * ln.L());
            lineMarks(u.dim, ln.from(), to, d.num("width") / 2, windup, ORANGE);
            crack(u.dim, ln.from(), 1.2, windup / 20.0);
        }
        explain(u, "rend", "§6[보스] 대지 가르기: §f거대 웜이 멀리 있는 사람 쪽으로 땅을 가릅니다. §e발밑으로 이어진 주황 줄에서 옆으로 비켜서세요 §7— 멀리 있어도 안전하지 않습니다");
        Set<UUID> hit = new HashSet<>();
        double longest = 0;
        for (Line ln : lines) longest = Math.max(longest, ln.L());
        int ticks = (int) Math.ceil(longest / d.num("speed"));
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 0; k <= ticks; k++) {
            int kk = k;
            steps.add(step(windup + k, (x, n) -> rendStep(x, lines, kk, hit)));
        }
        u.api.cast(u, "rend", windup + ticks + 10, steps);
        return true;
    }

    /** 바위가 솟는 k 번째 틱: 금을 따라 이번 틱에 솟은 구간 위의 사람이 맞음 (한 번만, 이미 솟은 곳으로 들어온 사람은 맞지 않음) */
    static void rendStep(Bosses.Unit u, List<Line> lines, int k, Set<UUID> hit) {
        Obj d = u.def.obj("rend");
        double speed = d.num("speed");
        double s0 = (k - 1) * speed;
        double s1 = k * speed;
        double half = d.num("width") / 2;
        for (Line ln : lines) {
            if (s0 > ln.L()) continue;
            double s = Math.min(s1, ln.L());
            Vec3 at = new Vec3(ln.from().x + ln.dir().x * s, ln.from().y, ln.from().z + ln.dir().z * s);
            // 솟는 바위: 흙먼지 + 금 + 위로 튀는 큰 돌 조각 (금을 따라 달려가는 모습)
            dust(u.dim, at, 2.0, EARTH, 0.55);
            crack(u.dim, at, 1.4, 1.0);
            chips(u.dim, at, 2, 0.6, 8);
            if (k % 3 == 0) play(u.dim, "dig.deepslate", at, 0.8, 0.9);
            for (ServerPlayer p : targets(u)) {
                if (hit.contains(p.getUUID()) || Math.abs(p.getY() - ln.from().y) > 3) continue;
                double rx = p.getX() - ln.from().x;
                double rz = p.getZ() - ln.from().z;
                double t = rx * ln.dir().x + rz * ln.dir().z;
                if (t <= s0 - 0.5 || t > s + 0.5) continue;
                if (Math.abs(rx * ln.dir().z - rz * ln.dir().x) > half + 0.3) continue;
                hit.add(p.getUUID());
                if (bossHit(u, p, d.num("damage"), Opt.of("knock", knock(0, 0, d.num("lift"))))) SkillFx.shake(p, 0.2, 0.3);
            }
        }
    }

    // ------------------------------------------------------------ 높은 곳

    /** 성문 · 담장 탑 위처럼 높은 곳 (바닥보다 high.above 칸 넘게) 에 서 있는 사람: 땅을 울려 떨어뜨림 (사람마다 cd 틱에 한 번) */
    static void highTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (now % 10 != 0 || m.tremor != null) return; // 진동 감지 중에는 없음 (밀려 떨어지는 소리로 들키지 않게)
        Bosses.Unit u = enc.units.get(0);
        if (!u.alive || !Bosses.valid(u.boss)) return;
        Obj d = u.def.obj("high");
        Vec3 c = enc.center;
        for (ServerPlayer p : targets(u)) {
            if (!p.onGround() || p.getY() - c.y < d.num("above")) continue;
            Long last = m.high.get(p.getUUID());
            if (now - (last != null ? last : -1000) < d.num("cd")) continue;
            m.high.put(p.getUUID(), now);
            Vec3 dir = dirTo(p.position(), c);
            dust(enc.dim, p.position(), 1.8, EARTH, 0.6);
            chips(enc.dim, p.position(), 3, 0.3, 5);
            play(enc.dim, "dig.deepslate", p.position(), 0.6, 1);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 1.1, dir.z * 1.1, 0.35)));
            BossKit.hintFace(p, "§6§l땅이 울려 떨어집니다", Opt.of("dist", 2.2, "up", 0.35));
            explain(u, "high", "§6[보스] §f성문·담장 탑 위처럼 높은 곳에 올라서면 거대 웜이 땅을 울려 떨어뜨립니다");
        }
    }

    // ------------------------------------------------------------ 모듈

    /** 원거리 견제 (v2.3.0): 땅속 · 삼키는 중 · 진동 감지 (움직이면 안 됨) 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m == null || (m.under == null && m.devour == null && m.tremor == null);
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        M m = new M();
        for (String k : List.of("sweep", "tunnel", "quake", "devour", "rend")) m.next.put(k, now + (long) d.obj(k).num("first"));
        m.bigUntil = now + 100;
        m.hole = 0;
        enc.mem = m;
        // 굴에서 튀어나오며 등장
        enc.api.anim(enc, "emerge", null, DIG, null);
        dust(enc.dim, enc.center, 4, EARTH, 0.9);
        chips(enc.dim, enc.center, 10, 0.45, 9);
    }

    /** 삼켜진 사람은 평타 · 패턴의 대상이 아님 */
    @Override
    public boolean targetable(Bosses.Enc enc, ServerPlayer p) {
        return !inside(enc, p);
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        if (m == null) return false;
        Obj d = u.def;
        String forced = forceNext;
        if (forced != null) {
            forceNext = null;
            return switch (forced) {
                case "sweep" -> sweep(u, now, list);
                case "tunnel" -> tunnel(u, now, list);
                case "quake" -> {
                    quake(u, now);
                    yield true;
                }
                case "devour" -> devour(u, now, list);
                case "tremor" -> {
                    tremor(u, now);
                    yield true;
                }
                case "rend" -> rend(u, now, list);
                default -> false;
            };
        }
        double r = u.api.ratio(u);
        if (m.under != null || m.tremor != null) return false;
        // 큰 패턴: 진동 감지 → 삼키기 → 대지 분출 → 굴 이동 (삼킨 사람이 있거나 다른 큰 패턴이 끝나지 않았으면 없음)
        if (m.devour == null && m.quake == null && now >= m.bigUntil) {
            List<Double> tremorAt = d.obj("tremor").numbersOrNull("at");
            if (tremorAt != null) {
                for (Double at : tremorAt) {
                    if (r <= at && !m.tremorDone.contains(at)) {
                        m.tremorDone.add(at);
                        tremor(u, now);
                        return true;
                    }
                }
            }
            Obj dv = d.obj("devour");
            if (r <= dv.num("from") && now >= m.next.get("devour")) {
                if (devour(u, now, list)) {
                    m.next.put("devour", now + every(u, BossKit.pair(dv, "every")));
                    return true;
                }
                m.next.put("devour", now + 40); // 닿는 곳에 아무도 없음: 조금 뒤 다시
            }
            Obj qk = d.obj("quake");
            if (r <= qk.num("from") && now >= m.next.get("quake")) {
                m.next.put("quake", now + every(u, BossKit.pair(qk, "every")));
                quake(u, now);
                return true;
            }
            if (now >= m.next.get("tunnel")) {
                m.next.put("tunnel", now + every(u, BossKit.pair(d.obj("tunnel"), "every")));
                if (tunnel(u, now, list)) return true;
            }
        }
        if (now >= m.next.get("sweep")) {
            if (sweep(u, now, list)) {
                m.next.put("sweep", now + every(u, BossKit.pair(d.obj("sweep"), "every")));
                return true;
            }
            m.next.put("sweep", now + 20);
        }
        // 멀리 있는 사람: 대지 가르기 (삼킨 사람이 있으면 없음: 입이 막힘)
        if (m.devour == null && now >= m.next.get("rend")) {
            if (rend(u, now, list)) {
                m.next.put("rend", now + every(u, BossKit.pair(d.obj("rend"), "every")));
                return true;
            }
            m.next.put("rend", now + 40);
        }
        return false;
    }

    /** 받는 피해: 삼킨 사람이 있으면 구출에 셈 (삼켜진 사람 자신은 빼고) — 피해는 그대로 */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        M m = mem(u);
        Devour D = m == null ? null : m.devour;
        if (D != null && "inside".equals(D.phase) && amount > 0 && (p == null || D.victim == null || !p.getUUID().equals(D.victim.getUUID()))) D.dealt += amount;
        return null;
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        M m = mem(enc);
        if (m == null) return;
        underTick(enc, now);
        tremorTick(enc, now);
        quakeTick(enc, now);
        devourTick(enc, now);
        highTick(enc, now);
        // 굴 둘레 흙먼지 (가끔, 땅 위에 있을 때)
        if (now % 40 == 0 && enc.alive && Bosses.valid(enc.boss) && m.under == null && m.tremor == null) {
            double a = random() * TAU;
            dust(enc.dim, new Vec3(enc.home.x + Math.cos(a) * 2, enc.center.y, enc.home.z + Math.sin(a) * 2), 1, EARTH, 0.5);
        }
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        if (m.devour != null) release(enc, m.devour);
        m.devour = null;
        m.under = null;
        m.quake = null;
        m.tremor = null;
    }
}
