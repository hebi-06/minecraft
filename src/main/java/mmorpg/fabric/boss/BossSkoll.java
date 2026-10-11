package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.effect;
import static mmorpg.fabric.boss.BossKit.faceAll;
import static mmorpg.fabric.boss.BossKit.fanMarks;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.groundUnder;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.inFan;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.meleeTarget;
import static mmorpg.fabric.boss.BossKit.nearSegment;
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.pair;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.say;
import static mmorpg.fabric.boss.BossKit.showFaceText;
import static mmorpg.fabric.boss.BossKit.shuffle;
import static mmorpg.fabric.boss.BossKit.snd;
import static mmorpg.fabric.boss.BossKit.sound;
import static mmorpg.fabric.boss.BossKit.title;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 스콜과 하티 (boss_skoll.js — v2.4.0, Lv.38): 수치는 config BOSSES.skoll_hati.
 * 해를 쫓는 검은 늑대 스콜 (몸 왼쪽 머리) 과 달을 쫓는 흰 늑대 하티 (몸 오른쪽 머리) 가 한 몸인 머리 둘 늑대. 해와 달의 돌 고리에서 싸움.
 * 움직이지 않는 보스 (중력 · 충돌 없음) 를 모듈이 매 틱 옮김 (엔진 pose — 설정 drive): 가장 가까운 사람 쪽으로 몸을 돌려 걷고, 뛰어들 때는 포물선으로 뜀.
 * 몸 (엔티티 자리) = 몸통 가운데, 머리 = 몸 앞 drive.head 칸에서 옆으로 drive.side 칸 (왼쪽 스콜, 오른쪽 하티).
 * 엔티티 속성: rpg:gait (0~1 걷는 빠르기 → stride 동작), rpg:day (1 = 낮: 스콜이 깨어 머리를 듦 · 0 = 밤: 하티), rpg:dusk (1 = 황혼: 둘 다),
 * rpg:phase (0 낮 · 1 밤 · 2 황혼 → 텍스처: 깨어난 머리의 불씨 · 눈이 빛남)
 * <ul>
 * <li>낮과 밤 (stance): 낮에는 스콜, 밤에는 하티가 깨어 싸움 (every 틱마다 울부짖으며 바뀜). 잠든 머리 쪽 옆구리에서 준 피해 +weak (숫자 앞 "약점", 옆구리 바닥에 표시)</li>
 * <li>물어뜯기 (평타): 깨어난 머리가 몸 앞 부채꼴 (낮 = 불, 밤 = 느림, 황혼 = 대상 쪽 머리)</li>
 * <li>도약: 멀리 (min 칸 넘게) 있는 사람 중 가장 먼 사람 발밑 원 → 뛰어들어 앞발로 내려찍음 (낮 = 불, 밤 = 느림, 황혼 = 둘 다)</li>
 * <li>태양 바퀴 (낮 · 황혼): 몸 둘레 inner 칸 밖으로 햇살 줄기가 뻗어 sweep° 돎 (쓸고 갈 곳을 먼저 보여 줌, 밝은 쪽에서 시작). 몸 곁 · 틈은 안전 (맞으면 불)</li>
 * <li>달빛 사냥 (밤 · 황혼): 한 사람을 사냥감으로 pounces 번 잇따라 덮침 (서리 자국: 밟으면 느림 + 피해) → 끝나면 지침 (받는 피해 +bonus, 숫자 앞 "지침")</li>
 * <li>해와 달의 심판 (체력 70% · 25%, 즉사): 가장자리로 물러나 무적 → 사람마다 해 · 달 표식 → 가운데 원판의 제 색 반원 (해 = 동쪽 금빛, 달 = 서쪽 푸른) 밖이면 즉사.
 * 두 번째는 도중에 표식이 뒤바뀜. 판결 뒤 가장 많이 모인 곳으로 도약</li>
 * <li>황혼 (체력 45% 아래): 두 머리가 모두 깨어남 — 약점이 사라지고 낮 · 밤 패턴을 모두 씀, 재사용 대기 × dusk.cd</li>
 * <li>높은 곳 (선돌 · 성문 위): 뛰어올라 끌어내림. 원거리 견제: 낮 = 태양 불씨 (불), 밤 = 달 서리 (느림), 황혼 = 번갈아 (엔진 farStyle)</li>
 * </ul>
 * 큰 패턴 (심판) 과 낮밤 바뀜은 틀이 없을 때 (pick). 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기를 모듈이).
 * 자바에서 달라진 점: 선돌 (enc.feat.stones) 은 Arena.Stone (at · sun), Infinity 틱은 Long.MAX_VALUE, 불은 igniteForSeconds (애드온 setOnFire).
 * 패턴 함수는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossSkoll implements BossModule {
    static final String KEY = "skoll_hati";
    static final double[] SUN = {1.0, 0.8, 0.3};
    static final double[] EMBER = {1.0, 0.5, 0.12};
    /** 해 예고 (눈밭 · 금빛 반원에서 보이게 짙은 주황) */
    static final double[] SUN_MARK = {1.0, 0.42, 0.08};
    static final double[] MOON = {0.62, 0.86, 1.0};
    /** 달 예고 (눈밭에서 보이게 짙은 파랑) */
    static final double[] NIGHT = {0.28, 0.5, 1.0};
    static final double[] FROST = {0.8, 0.94, 1.0};
    static final double[] DUSK = {0.78, 0.4, 0.95};
    static final double[] RED = {1.0, 0.22, 0.14};
    static final double[] SAFE = {0.4, 1.0, 0.5};
    static final double[] SNOW = {0.93, 0.95, 1.0};
    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;
    /** 애드온의 Infinity (틱) */
    static final long NEVER = Long.MAX_VALUE;

    /** 피해 숫자 앞 표시: 약점 옆구리 · 지친 동안 (근접 보너스 » 앞) */
    public static final String WEAK_MARK = "§e약점 ";
    public static final String WINDED_MARK = "§b지침 ";
    /** 동작 틀: 뛰어오르기 (pounce · hunt · leap) 는 내려앉기 (land) 가 이어받을 때까지 마지막 모습 유지 */
    public static final String LEAP = "rpg_leap";
    /** 동작 틀: 심판의 울부짖음은 판결까지 유지 */
    public static final String HOWL = "rpg_howl";
    /** 등장: 선돌 위에서 howl 틱 울부짖고 → time 틱 동안 가운데로 뛰어내림 (높이 height) */
    public static final int ARRIVE_HOWL = 20;
    public static final int ARRIVE_TIME = 20;
    public static final double ARRIVE_HEIGHT = 3;

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (bite · pounce · wheel · hunt · judge · swap · dusk — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    // ------------------------------------------------------------ 기억

    /**
     * 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 큰 패턴 쉼 · 알린 규칙 · 몸 · 모습 (낮밤 · 황혼) · 낮밤 · 황혼인지 · 뛰기 · 사냥 · 태양 바퀴 · 심판 · 심판을 쓴 체력 ·
     * 서리 자국 (사람마다 맞은 틱) · 지친 끝 · 높은 곳 · 황혼의 원거리 견제 번갈아
     */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new HashMap<>();
        public long bigUntil;
        public final Set<String> told = new HashSet<>();
        public final Body body = new Body();
        public final Look look = new Look();
        public final Stance stance = new Stance();
        public boolean dusk;
        public Leap leap;
        public Hunt hunt;
        public Wheel wheel;
        public Judge judge;
        public final Set<Double> judgeDone = new HashSet<>();
        public final List<Frost> frost = new ArrayList<>();
        public final Map<UUID, Long> frostHit = new HashMap<>();
        public long windedUntil;
        public final Map<UUID, Long> high = new HashMap<>();
        public final Map<UUID, Long> highSince = new HashMap<>();
        public boolean farFlip;
    }

    /** 몸: 자리 (x, z — 바닥 높이 + 뛰는 높이 h), 몸 방향 yaw (라디안), 패턴 중 돌아볼 쪽 want, 걸음 (지금 · 목표 · 보낸 값), 마지막으로 옮긴 모습 */
    public static final class Body {
        public double x;
        public double z;
        public double h;
        public double yaw;
        public Double want;
        public double gait;
        public double gaitTo;
        public Double gaitSent;
        double[] placed;
    }

    /** 모습 (낮 1 → 밤 0, 황혼 0 → 1 — 지금 · 목표 · 보낸 값), 보낸 텍스처 번호 */
    public static final class Look {
        public double day = 1;
        public double dayTo = 1;
        public double dusk;
        public double duskTo;
        public Double daySent;
        public Double duskSent;
        public Integer phaseSent;
    }

    /** 낮과 밤: "day" · "night", 다음에 바뀌는 틱 */
    public static final class Stance {
        public String mode = "day";
        public long next;
    }

    /** 뛰기: 시작 자리 · 높이, 닿을 자리, 시작 틱 · 걸리는 틱 · 높이 · 바라볼 쪽 */
    public static final class Leap {
        public double x0;
        public double z0;
        public double h0;
        public double x1;
        public double z1;
        public long t0;
        public long time;
        public double height;
        public double yaw;
    }

    /** 뛰어들 곳 (애드온 leapPlan): 몸이 내려앉을 곳 to (x, z), 앞발 자리 spot (원 · 피해), 바라볼 쪽 yaw */
    public record LeapPlan(Vec3 to, Vec3 spot, double yaw) {}

    /** 달빛 사냥: 사냥감 · 덮친 수 · 이번 뛰기 */
    public static final class Hunt {
        public ServerPlayer prey;
        public int n;
        public LeapPlan plan;

        Hunt(ServerPlayer prey) {
            this.prey = prey;
        }
    }

    /** 태양 바퀴: 몸 밑 바닥 · 첫 줄기 각도 · 줄기 수 · 도는 각 (라디안) · 도는 쪽 · 안전한 몸 곁 반지름 · 맞은 (줄기 | 사람) */
    public static final class Wheel {
        public final Vec3 at;
        public final double a0;
        public final int n;
        public final double sweep;
        public final int sgn;
        public final double r0;
        public final Set<String> hit = new HashSet<>();

        Wheel(Vec3 at, double a0, int n, double sweep, int sgn, double r0) {
            this.at = at;
            this.a0 = a0;
            this.n = n;
            this.sweep = sweep;
            this.sgn = sgn;
            this.r0 = r0;
        }
    }

    /** 햇살 줄기 하나 (방향 · 시작 · 끝) */
    public record Ray(Vec3 v, Vec3 from, Vec3 to) {}

    /** 해와 달의 심판: 단계 (retreat · mark) · 두 번째인지 · 표식 시작 · 판결 틱 · 뒤바뀌는 틱 · 뒤바뀌었는지 · 표식 · 물러날 자리 E */
    public static final class Judge {
        public String stage;
        public final boolean second;
        public long t0;
        public long until;
        public Long swapAt;
        public boolean swapped;
        public List<JudgeMark> marks = new ArrayList<>();
        public final Vec3 E;

        Judge(boolean second, long t0, Vec3 E) {
            this.stage = "retreat";
            this.second = second;
            this.t0 = t0;
            this.until = NEVER;
            this.E = E;
        }
    }

    /** 표식 하나 (사람 · 해/달 · 사라졌는지) */
    public static final class JudgeMark {
        public final ServerPlayer p;
        public final UUID id;
        public String side;
        public boolean done;

        JudgeMark(ServerPlayer p, String side) {
            this.p = p;
            this.id = p.getUUID();
            this.side = side;
        }
    }

    /** 서리 자국: 자리 · 생긴 틱 · 사라지는 틱 */
    public record Frost(Vec3 at, long t0, long until) {}

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    /** 시험용: 지금 심판 · 사냥 · 태양 바퀴 · 서리 자국 · 뛰는 중 (애드온 judgeOf · huntOf · wheelOf · frostOf · leapOf) */
    public static Judge judgeOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.judge;
    }

    public static Hunt huntOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.hunt;
    }

    public static Wheel wheelOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.wheel;
    }

    public static List<Frost> frostOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.frost;
    }

    public static Leap leapOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.leap;
    }

    // ------------------------------------------------------------ 도우미

    /** 애드온 isValid (치워지지 않음) */
    static boolean ok(Entity e) {
        return e != null && !e.isRemoved();
    }

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

    static Vec3 dirOf(double a) {
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    /** 각도 → 0 ~ 2π */
    static double wrap(double a) {
        return ((a % TAU) + TAU) % TAU;
    }

    /** 각도 차이 → -π ~ π */
    static double wrapPi(double a) {
        double w = wrap(a);
        return w > Math.PI ? w - TAU : w;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** cur 에서 want 쪽으로 step 까지 돎 */
    static double turnToward(double cur, double want, double step) {
        double d = wrapPi(want - cur);
        return Math.abs(d) <= step ? want : cur + Math.signum(d) * step;
    }

    /** 몸 오른쪽 단위 방향 (몸 방향 yaw 에서 오른쪽 90°: 하티 쪽). +x 동쪽 · +z 남쪽에서 북쪽 (-z) 을 보면 오른쪽은 동쪽 */
    public static Vec3 rightOf(double yaw) {
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        say(u, text);
    }

    static void shakeNear(Bosses.Unit u, Vec3 at, double range, double intensity, double seconds) {
        for (ServerPlayer p : fighters(u)) if (hdist(p.position(), at) <= range) SkillFx.shake(p, intensity, seconds);
    }

    /** 눈보라 먼지 (rpg:dust) */
    static void dust(ServerLevel dim, Vec3 at, double size, double[] color, double life) {
        SkillFx.fx(dim, "rpg:dust", new Vec3(at.x, at.y + size * 0.35, at.z), Opt.of("size", size, "life", life, "color", color, "alpha", 0.8));
    }

    /** 불 (애드온 setOnFire(초, true)) */
    static void burn(ServerPlayer p, double seconds) {
        if (!(seconds > 0) || p.isRemoved()) return;
        p.igniteForSeconds((float) seconds);
    }

    /** 밀어내는 힘 (kx, kz): 담장에서 5칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖 · 선돌 위로 날아가지 않게) → {x, z} */
    static double[] keepIn(Bosses.Unit u, ServerPlayer p, double kx, double kz) {
        Vec3 c = u.center;
        double rx = p.getX() - c.x;
        double rz = p.getZ() - c.z;
        double r = Math.hypot(rx, rz);
        if (r < arenaRadius(u) - 5 || r < 1e-6) return new double[] {kx, kz};
        double ux = rx / r;
        double uz = rz / r;
        double out = kx * ux + kz * uz;
        if (out <= 0) return new double[] {kx, kz};
        return new double[] {kx - ux * (out + 0.15), kz - uz * (out + 0.15)};
    }

    static double arenaRadius(Bosses.Unit u) {
        return u.enc.def.obj("arena").num("radius");
    }

    /** 공격이 닿는 사람 */
    static List<ServerPlayer> targets(Bosses.Unit u) {
        return fighters(u);
    }

    /** 바닥 높이 둘레 (위아래 4칸 안) 의 사람 */
    static List<ServerPlayer> level(Bosses.Unit u, List<ServerPlayer> list) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : list) if (Math.abs(p.getY() - u.center.y) < 4) out.add(p);
        return out;
    }

    // ------------------------------------------------------------ 낮과 밤

    /** 깨어 있는 머리: "sun" (낮: 스콜, 몸 왼쪽) | "moon" (밤: 하티, 몸 오른쪽) | "both" (황혼) */
    public static String awake(Bosses.Unit u) {
        M m = mem(u);
        return m.dusk ? "both" : "day".equals(m.stance.mode) ? "sun" : "moon";
    }

    /** 약점 옆구리 쪽: +1 = 몸 오른쪽 (낮: 하티가 잠듦), -1 = 왼쪽 (밤: 스콜이 잠듦), 0 = 없음 (황혼) */
    public static int weakSide(Bosses.Unit u) {
        M m = mem(u);
        if (m.dusk) return 0;
        return "day".equals(m.stance.mode) ? 1 : -1;
    }

    /** l 이 약점 옆구리 쪽인지: 잠든 머리 쪽이고 몸 방향에서 stance.flank[0]° ~ [1]° */
    public static boolean onFlank(Bosses.Unit u, Vec3 l) {
        int side = weakSide(u);
        if (side == 0) return false;
        Body b = mem(u).body;
        double dx = l.x - b.x;
        double dz = l.z - b.z;
        double d = Math.hypot(dx, dz);
        if (d < 0.5) return false;
        Vec3 f = dirOf(b.yaw);
        Vec3 r = rightOf(b.yaw);
        double ang = Math.acos(clamp((dx * f.x + dz * f.z) / d, -1, 1)) / DEG;
        double lat = (dx * r.x + dz * r.z) * side;
        double[] fl = pair(u.def.obj("stance"), "flank");
        return lat > 0 && ang >= fl[0] && ang <= fl[1];
    }

    /** 지금 색 (예고 · 연출): 낮 = 주황, 밤 = 파랑, 황혼 = 보라 */
    static double[] stanceColor(Bosses.Unit u) {
        String h = awake(u);
        return h.equals("sun") ? SUN_MARK : h.equals("moon") ? NIGHT : DUSK;
    }

    // ------------------------------------------------------------ 몸

    /** 몸 (엔티티 자리): 바닥 높이 + 뛰는 높이 h */
    public static Vec3 bodyOf(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y + b.h, b.z);
    }

    /** 머리 (연출 · 던지는 곳, 입 높이): which = "sun" 왼쪽 스콜 | "moon" 오른쪽 하티 (그 밖 · null = 두 머리 사이) */
    public static Vec3 headAt(Bosses.Unit u, String which) {
        Body b = mem(u).body;
        Obj D = u.def.obj("drive");
        double s = "moon".equals(which) ? 1 : "sun".equals(which) ? -1 : 0;
        Vec3 r = rightOf(b.yaw);
        double head = D.num("head");
        double side = D.num("side");
        return new Vec3(b.x + Math.cos(b.yaw) * head + r.x * side * s, u.center.y + b.h + 3.6, b.z + Math.sin(b.yaw) * head + r.z * side * s);
    }

    public static Vec3 headAt(Bosses.Unit u) {
        return headAt(u, null);
    }

    /** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음: 서 있을 때 매 틱 옮기지 않게. 밀려난 것은 엔진이 10틱마다 되돌림) */
    static void place(Bosses.Unit u) {
        Body b = mem(u).body;
        double[] k = {b.x, b.z, b.h, b.yaw};
        if (b.placed != null && java.util.Arrays.equals(b.placed, k) && ok(u.boss)) return;
        b.placed = k;
        u.api.pose(u, new Vec3(b.x, u.center.y + b.h, b.z), b.yaw);
    }

    /** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게, 땅 위. 시험 · 미리보기도 씀 */
    public static void setBody(Bosses.Unit u, double x, double z, double yaw) {
        Body b = mem(u).body;
        Vec3 c = u.center;
        double K = u.def.obj("drive").num("keep");
        double r = Math.hypot(x - c.x, z - c.z);
        b.x = r > K ? c.x + ((x - c.x) / r) * K : x;
        b.z = r > K ? c.z + ((z - c.z) / r) * K : z;
        b.yaw = yaw;
        b.h = 0;
        place(u);
    }

    /** 속성 보내기: step 넘게 바뀌었거나 끝값 (0 · 1) 에 닿았을 때만 — 보낸 값 */
    static Double setProp(Bosses.Unit u, String id, double v, Double last) {
        double step = 0.05;
        if (last != null && (v == last || (Math.abs(v - last) < step - 1e-9 && v != 0 && v != 1))) return last;
        if (!ok(u.boss)) return last;
        u.boss.setProp(id, v);
        return v;
    }

    static double toward(double v, double to, double s) {
        return Math.abs(to - v) <= s ? to : v + Math.signum(to - v) * s;
    }

    static double r2(double v) {
        return Js.round(clamp(v, 0, 1) * 100) / 100;
    }

    /** 매 틱: 걷는 빠르기 · 낮밤 · 황혼을 목표 쪽으로 (낮밤은 1초, 황혼은 2초에 걸쳐 머리를 들고 내림), 속성 보내기 */
    static void bodyTick(Bosses.Unit u) {
        M m = mem(u);
        Body b = m.body;
        Look L = m.look;
        b.gait = toward(b.gait, b.gaitTo, 0.15);
        L.day = toward(L.day, L.dayTo, 0.05);
        L.dusk = toward(L.dusk, L.duskTo, 0.025);
        b.gaitSent = setProp(u, "rpg:gait", r2(b.gait), b.gaitSent);
        L.daySent = setProp(u, "rpg:day", r2(L.day), L.daySent);
        L.duskSent = setProp(u, "rpg:dusk", r2(L.dusk), L.duskSent);
        // 텍스처: 깨어난 머리의 불씨 (스콜) · 눈 (하티) 이 빛남 — 머리를 반쯤 들었을 때 바뀜
        int phase = L.dusk >= 0.5 ? 2 : L.day >= 0.5 ? 0 : 1;
        if (L.phaseSent == null || phase != L.phaseSent) {
            if (ok(u.boss)) {
                u.boss.setProp("rpg:phase", phase);
                L.phaseSent = phase;
            }
        }
    }

    /** 걷기: 가장 가까운 사람 쪽으로 몸을 돌리고, 거의 그쪽을 보면 걸어감 (near 칸 안이면 멈춤, 가운데에서 keep 칸 밖으로는 나가지 않음 — 뛰어들어 밖에 있으면 안쪽으로만) */
    static void walkTick(Bosses.Unit u, List<ServerPlayer> list) {
        Obj D = u.def.obj("drive");
        Body b = mem(u).body;
        Vec3 bp = new Vec3(b.x, 0, b.z);
        ServerPlayer tgt = null;
        double best = Double.POSITIVE_INFINITY;
        for (ServerPlayer p : list) {
            double d = hdist(p.position(), bp);
            if (d < best) {
                best = d;
                tgt = p;
            }
        }
        b.gaitTo = 0;
        if (tgt != null) {
            double want = angOf(dirTo(bp, tgt.position()));
            b.yaw = turnToward(b.yaw, want, D.num("turn") * DEG);
            if (best > D.num("near") && Math.abs(wrapPi(want - b.yaw)) < 50 * DEG) {
                Vec3 c = u.center;
                double nx = b.x + Math.cos(b.yaw) * D.num("walk");
                double nz = b.z + Math.sin(b.yaw) * D.num("walk");
                double r = Math.hypot(nx - c.x, nz - c.z);
                double lim = Math.max(D.num("keep"), Math.hypot(b.x - c.x, b.z - c.z));
                if (r > lim) {
                    nx = c.x + ((nx - c.x) / r) * lim;
                    nz = c.z + ((nz - c.z) / r) * lim;
                }
                if (Math.hypot(nx - b.x, nz - b.z) > 0.02) {
                    b.x = nx;
                    b.z = nz;
                    b.gaitTo = 1;
                }
            }
        }
        place(u);
        // 발소리
        if (b.gaitTo > 0 && Tasks.now() % 10 == 0) play(u.dim, snd(u, "step"), bodyOf(u), 0.5, 1);
    }

    /** 패턴 동안: 원하는 쪽 (body.want) 이 있으면 그쪽으로 몸을 돌림 (제자리) */
    static void standTick(Bosses.Unit u) {
        Body b = mem(u).body;
        b.gaitTo = 0;
        if (b.want != null) b.yaw = turnToward(b.yaw, b.want, u.def.obj("drive").num("turn") * DEG);
        place(u);
    }

    /** l 이 몸 방향에서 몇 도 떨어져 있는지 (라디안, 0 ~ π) */
    static double offAngle(Bosses.Unit u, Vec3 l) {
        Body b = mem(u).body;
        return Math.abs(wrapPi(angOf(dirTo(new Vec3(b.x, 0, b.z), l)) - b.yaw));
    }

    // ------------------------------------------------------------ 뛰기 (도약 · 사냥 · 등장 · 물러남)

    /**
     * 뛰어들 곳: 대상 발밑 (spot) 에 앞발 (몸 앞 drive.paw 칸) 이 닿게 몸은 그만큼 뒤 (가운데에서 drive.leap 칸 안).
     * 몸을 안쪽으로 당기면 앞발 자리 (원 · 피해) 도 그만큼 안쪽. 시험도 씀
     */
    public static LeapPlan leapPlan(Bosses.Unit u, Vec3 spot) {
        Obj D = u.def.obj("drive");
        Body b = mem(u).body;
        Vec3 c = u.center;
        double paw = D.num("paw");
        double leap = D.num("leap");
        Vec3 dir = dirTo(new Vec3(b.x, 0, b.z), spot);
        double bx = spot.x - dir.x * paw;
        double bz = spot.z - dir.z * paw;
        double r = Math.hypot(bx - c.x, bz - c.z);
        boolean pulled = r > leap;
        if (pulled) {
            bx = c.x + ((bx - c.x) / r) * leap;
            bz = c.z + ((bz - c.z) / r) * leap;
        }
        Vec3 at = pulled ? new Vec3(bx + dir.x * paw, spot.y, bz + dir.z * paw) : spot;
        return new LeapPlan(new Vec3(bx, 0, bz), at, angOf(dir));
    }

    /** 뛰기 시작: time 틱 동안 포물선 (높이 height) 으로 P.to 까지 (몸은 leapTick 이 옮김) */
    static void jump(Bosses.Unit u, LeapPlan P, long time, double height) {
        M m = mem(u);
        Body b = m.body;
        Leap L = new Leap();
        L.x0 = b.x;
        L.z0 = b.z;
        L.h0 = b.h;
        L.x1 = P.to().x;
        L.z1 = P.to().z;
        L.t0 = Tasks.now();
        L.time = Math.max(1, time);
        L.height = height;
        L.yaw = P.yaw();
        m.leap = L;
        b.want = null;
        play(u.dim, snd(u, "swing"), bodyOf(u), 0.7, 1);
        if (b.h <= 0.01) dust(u.dim, floorAt(u, b.x, b.z), 2.6, SNOW, 0.8);
    }

    /** 뛰는 중: 몸을 포물선으로 옮김 (닿으면 땅 위) */
    static void leapTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Leap L = m.leap;
        if (L == null) return;
        Body b = m.body;
        double k = clamp((double) (now - L.t0) / L.time, 0, 1);
        b.x = L.x0 + (L.x1 - L.x0) * k;
        b.z = L.z0 + (L.z1 - L.z0) * k;
        b.h = L.h0 * (1 - k) + L.height * 4 * k * (1 - k);
        b.yaw = turnToward(b.yaw, L.yaw, 20 * DEG);
        b.gaitTo = 0;
        if (k >= 1) {
            m.leap = null;
            b.h = 0;
        }
        place(u);
    }

    /** 내려앉은 자리 연출 */
    static void landFx(Bosses.Unit u, Vec3 at, double radius, double[] color) {
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", 0.6, "color", color, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2, "life", 1.2, "color",
                new double[] {0.34, 0.33, 0.38}, "alpha", 0.85));
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * TAU;
            dust(u.dim, new Vec3(at.x + Math.cos(a) * radius * 0.7, at.y, at.z + Math.sin(a) * radius * 0.7), 2, SNOW, 0.9);
        }
        play(u.dim, "mob.ravager.step", at, 0.6, 1);
        play(u.dim, "dig.snow", at, 0.7, 1);
        shakeNear(u, at, 16, 0.3, 0.6);
    }

    // ------------------------------------------------------------ 물어뜯기 (평타)

    /** 무는 머리: 깨어난 머리 (황혼이면 대상 쪽 머리) */
    static String biter(Bosses.Unit u, Vec3 l) {
        String h = awake(u);
        if (!h.equals("both")) return h;
        Body b = mem(u).body;
        Vec3 r = rightOf(b.yaw);
        return (l.x - b.x) * r.x + (l.z - b.z) * r.z >= 0 ? "moon" : "sun";
    }

    public static boolean bite(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj m = u.def.obj("melee");
        Vec3 b = bodyOf(u);
        double reach = bossSize(u.def).half() + m.num("reach");
        double cone = m.num("cone");
        int windup = (int) m.num("windup");
        // 물 수 있는 사람 가운데 가장 가까운 사람 (v2.20.0 도발한 사람이 닿으면 그 사람)
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : list) {
            if (hdist(p.position(), b) <= reach && Math.abs(p.getY() - u.center.y) <= 4 && offAngle(u, p.position()) <= (cone / 2 + 25) * DEG) pool.add(p);
        }
        ServerPlayer t = meleeTarget(u, pool, b);
        if (t == null) return false;
        Vec3 dir = dirTo(b, t.position());
        Vec3 apex = floorAt(u, b.x, b.z);
        String head = biter(u, t.position());
        mem(u).body.want = angOf(dir);
        fanMarks(u.dim, apex, dir, Js.round(reach), cone, windup, head.equals("sun") ? SUN_MARK : NIGHT);
        u.api.anim(u, "bite_" + head);
        sound(u, snd(u, "swing"), headAt(u, head), 0.8, 1);
        u.api.cast(u, "bite", windup + 6, step(windup, (x, n) -> biteHit(x, apex, dir, reach, head)));
        return true;
    }

    static void biteHit(Bosses.Unit u, Vec3 apex, Vec3 dir, double radius, String head) {
        Obj m = u.def.obj("melee");
        for (ServerPlayer p : targets(u)) {
            if (Math.abs(p.getY() - apex.y) > 4 || !inFan(p.position(), apex, dir, radius, m.num("cone"), 0.5)) continue;
            double[] kk = keepIn(u, p, dir.x * 1.2, dir.z * 1.2);
            boolean landed = bossHit(u, p, m.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.35), "directional", true, "effect",
                    head.equals("moon") ? effect("slowness", (int) m.num("slow"), 0) : null));
            if (landed && head.equals("sun")) burn(p, m.num("burn"));
        }
        Vec3 h = headAt(u, head);
        sound(u, snd(u, "hit"), h, 0.9, 1);
        SkillFx.fx(u.dim, "rpg:slash_heavy_r_glow", new Vec3(h.x, h.y - 1.4, h.z), Opt.of("dir", dir, "speed", 0.0, "spin", 0.0, "size", 3.0, "life", 0.25, "color",
                head.equals("sun") ? EMBER : MOON, "alpha", 0.95));
    }

    // ------------------------------------------------------------ 도약

    public static boolean pounce(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("pounce");
        Vec3 b = bodyOf(u);
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) {
            double r = hdist(p.position(), b);
            if (r > d.num("min") && r <= d.num("range") && Math.abs(p.getY() - u.center.y) < 4) cand.add(p);
        }
        if (cand.isEmpty()) return false;
        cand.sort(Comparator.comparingDouble((ServerPlayer p) -> -hdist(p.position(), b)));
        ServerPlayer t = cand.get(0);
        LeapPlan P = leapPlan(u, new Vec3(t.getX(), groundUnder(t, u.center.y), t.getZ()));
        startPounce(u, P);
        explain(u, "pounce", "§6[보스] 도약: §f멀리 떨어진 사람 발밑에 원이 뜨면 스콜과 하티가 뛰어들어 앞발로 내려찍습니다 — 원 밖으로 §7(낮 = 불, 밤 = 느림)");
        return true;
    }

    /** 도약 한 번 (틀): 앞발 자리 원 windup 틱 → time 틱 동안 뛰어듦 → 원 안 피해 (심판 뒤의 도약도) */
    static void startPounce(Bosses.Unit u, LeapPlan P) {
        Obj d = u.def.obj("pounce");
        int windup = (int) d.num("windup");
        int time = (int) d.num("time");
        double height = d.num("height");
        disc(u.dim, P.spot(), d.num("radius"), windup, stanceColor(u));
        mem(u).body.want = P.yaw();
        u.api.anim(u, "pounce", null, LEAP, "0");
        sound(u, snd(u, "roar"), headAt(u, awake(u).equals("moon") ? "moon" : "sun"), 1.0, 0.8);
        u.api.cast(u, "pounce", windup + time + 8, step(windup, (x, n) -> jump(x, P, time, height)), step(windup + time, (x, n) -> pounceHit(x, P.spot())));
    }

    static void pounceHit(Bosses.Unit u, Vec3 spot) {
        Obj d = u.def.obj("pounce");
        String head = awake(u);
        double radius = d.num("radius");
        for (ServerPlayer p : targets(u)) {
            if (hdist(p.position(), spot) > radius + 0.3 || Math.abs(p.getY() - spot.y) > 3) continue;
            Vec3 dir = dirTo(spot, p.position());
            double[] kk = keepIn(u, p, dir.x * 0.8, dir.z * 0.8);
            boolean landed = bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], d.num("lift")), "effect",
                    !head.equals("sun") ? effect("slowness", (int) d.num("slow"), 0) : null));
            if (landed && !head.equals("moon")) burn(p, d.num("burn"));
        }
        landFx(u, spot, radius, stanceColor(u));
        u.api.anim(u, "land", null, LEAP, null);
    }

    // ------------------------------------------------------------ 태양 바퀴 (낮 · 황혼)

    /** 몸 (at) 에서 방향 v 로 뻗은 햇살이 필드 가장자리 (반지름 + 0.5) 까지 닿는 길이 (range 까지) */
    public static double rayEnd(Bosses.Unit u, Vec3 at, Vec3 v, double range) {
        double R = arenaRadius(u) + 0.5;
        double wx = at.x - u.center.x;
        double wz = at.z - u.center.z;
        double bd = wx * v.x + wz * v.z;
        double q = bd * bd - (wx * wx + wz * wz - R * R);
        double t = q > 0 ? -bd + Math.sqrt(q) : 0;
        return Math.max(0, Math.min(range, t));
    }

    /** 줄기 j 의 k 틱째 각도 */
    static double rayAngle(Bosses.Unit u, Wheel W, int j, double k) {
        return W.a0 + (j * TAU) / W.n + W.sgn * W.sweep * clamp(k / u.def.obj("wheel").num("time"), 0, 1);
    }

    /** 줄기 하나 (몸 둘레 r0 칸 → 가장자리): 시작 · 끝 */
    public static Ray raySegment(Bosses.Unit u, Wheel W, double a) {
        Vec3 v = dirOf(a);
        double r1 = Math.max(W.r0, rayEnd(u, W.at, v, u.def.obj("wheel").num("range")));
        return new Ray(v, new Vec3(W.at.x + v.x * W.r0, W.at.y, W.at.z + v.z * W.r0), new Vec3(W.at.x + v.x * r1, W.at.y, W.at.z + v.z * r1));
    }

    /** 햇살 줄기 그림 (가슴 높이의 빛 띠) */
    static void drawRay(Bosses.Unit u, Ray seg, double life, double alpha, double thick) {
        double L = hdist(seg.from(), seg.to());
        if (L < 0.5) return;
        int n = Math.max(1, (int) Js.round(L / 3.6));
        double half = L / n / 2;
        for (int i = 0; i < n; i++) {
            double s = (i + 0.5) * (L / n);
            SkillFx.fx(u.dim, "rpg:arrow_trail", new Vec3(seg.from().x + seg.v().x * s, seg.from().y + 1.0, seg.from().z + seg.v().z * s), Opt.of("dir", seg.v(), "size",
                    half * 1.05, "thick", thick, "life", life, "color", SUN, "alpha", alpha));
        }
    }

    /** 예고: 줄기마다 쓸고 갈 부채꼴 (시작하는 줄은 밝게) + 몸 곁 안전 원 (초록) */
    static void wheelMarks(Bosses.Unit u, Wheel W, int ticks) {
        Obj d = u.def.obj("wheel");
        double life = Math.max(0.1, ticks / 20.0);
        // 격자 간격 2.4칸 (판도 그만큼 크게): 한 틱에 뿌리는 판을 200개 안으로
        double step = 2.4;
        double R = arenaRadius(u) + 0.5;
        double time = d.num("time");
        for (int j = 0; j < W.n; j++) {
            double a0 = rayAngle(u, W, j, 0);
            double a1 = rayAngle(u, W, j, time);
            double lo = Math.min(a0, a1);
            double hi = Math.max(a0, a1);
            for (double r = W.r0 + step / 2; r <= d.num("range"); r += step) {
                int n = Math.max(1, (int) Math.ceil(((hi - lo) * r) / step));
                for (int i = 0; i <= n; i++) {
                    double a = lo + ((hi - lo) * i) / n;
                    Vec3 at = new Vec3(W.at.x + Math.cos(a) * r, W.at.y, W.at.z + Math.sin(a) * r);
                    if (hdist(at, u.center) > R) continue;
                    boolean start = W.sgn > 0 ? i == 0 : i == n;
                    SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(at.x, at.y + 0.07, at.z), Opt.of("dir", Vec3.ZERO, "size", step * 0.8, "life", life, "color", SUN_MARK, "alpha",
                            start ? 0.85 : 0.4, "grow", 1.0));
                }
            }
            drawRay(u, raySegment(u, W, a0), Math.max(0.1, d.num("windup") / 20), 0.45, 0.35);
        }
        disc(u.dim, W.at, W.r0, ticks, SAFE, Opt.of("fill", false, "alpha", 0.95));
    }

    public static boolean wheel(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("wheel");
        M m = mem(u);
        Vec3 b = bodyOf(u);
        boolean any = false;
        for (ServerPlayer p : list) if (hdist(p.position(), b) <= d.num("range") + 2) any = true;
        if (!any) return false;
        int n = (int) (m.dusk ? d.num("duskBeams") : d.num("beams"));
        double a0 = random() * TAU;
        double sweep = (m.dusk ? d.num("duskSweep") : d.num("sweep")) * DEG;
        int sgn = random() < 0.5 ? 1 : -1;
        Wheel W = new Wheel(floorAt(u, b.x, b.z), a0, n, sweep, sgn, bossSize(u.def).half() + d.num("inner"));
        m.wheel = W;
        m.body.want = null;
        int windup = (int) d.num("windup");
        int time = (int) d.num("time");
        wheelMarks(u, W, windup + time);
        u.api.anim(u, "wheel");
        // 몸 위에 떠오르는 해
        SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(b.x, u.center.y + 7, b.z), Opt.of("dir", new Vec3(0, 1, 0), "size", 2.6, "life", (windup + time) / 20.0, "spin", 0.0,
                "speed", sgn * 60.0, "grow", 0.0, "progress", 1.0, "color", SUN, "alpha", 0.9));
        sound(u, "beacon.activate", b, 0.7, 1.2);
        sound(u, snd(u, "howl"), headAt(u, "sun"), 0.7, 1);
        faceAll(u, "§6§l태양 바퀴! §f몸 곁이나 주황 부채꼴 사이로", Opt.of("dist", 2.4, "up", 0.45), list);
        explain(u, "wheel", "§6[보스] 태양 바퀴: §f스콜이 해를 불러 몸에서 햇살 줄기 " + n
                + "개를 뻗어 돌립니다 (주황 부채꼴 = 쓸고 갈 곳, 밝은 줄에서 시작). §e몸 바로 곁(초록 원)이나 부채꼴 사이는 안전 §7(맞으면 불)");
        List<Bosses.Step> steps = new ArrayList<>();
        steps.add(step(windup, (x, t) -> wheelStart(x)));
        for (int k = 0; k <= time; k++) {
            int kk = k;
            steps.add(step(windup + k, (x, t) -> wheelStep(x, W, kk)));
        }
        steps.add(step(windup + time + 1, (x, t) -> {
            M xm = mem(x);
            if (xm != null) xm.wheel = null;
        }));
        u.api.cast(u, "wheel", windup + time + 8, steps);
        // 바로 이어서 달빛 사냥이 나오지 않게
        m.next.put("hunt", Math.max(m.next.get("hunt"), now + windup + time + 60));
        return true;
    }

    static void wheelStart(Bosses.Unit u) {
        Vec3 b = bodyOf(u);
        M m = mem(u);
        double r0 = m.wheel != null ? m.wheel.r0 : 4;
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(b.x, u.center.y + 0.1, b.z), Opt.of("dir", Vec3.ZERO, "size", r0, "life", 0.5, "color", SUN, "alpha", 0.95));
        play(u.dim, "mob.blaze.shoot", b, 0.5, 1);
        play(u.dim, "fire.ignite", b, 0.7, 1);
        shakeNear(u, b, 22, 0.15, 0.6);
    }

    static void wheelStep(Bosses.Unit u, Wheel W, int k) {
        Obj d = u.def.obj("wheel");
        for (int j = 0; j < W.n; j++) {
            Ray seg = raySegment(u, W, rayAngle(u, W, j, k));
            if (k % 2 == 0) drawRay(u, seg, 0.15, 0.95, 0.9);
            if (k % 4 == 0) SkillFx.fx(u.dim, "rpg:dust", new Vec3(seg.to().x, seg.to().y + 0.6, seg.to().z), Opt.of("size", 1.2, "life", 0.5, "color", EMBER, "alpha", 0.85));
            for (ServerPlayer p : targets(u)) {
                String key = j + "|" + p.getUUID();
                // 몸 곁 (안전한 초록 원 안) 은 줄기 끝에 닿아도 안전
                if (W.hit.contains(key) || Math.abs(p.getY() - W.at.y) > 3 || hdist(p.position(), W.at) < W.r0 || !nearSegment(p.position(), seg.from(), seg.to(),
                        d.num("width") / 2)) continue;
                W.hit.add(key);
                // 때린 엔티티 없이 (보스 쪽에서 밀어내지 않음), 방어구로 줄어드는 피해 (마법 피해가 아님)
                boolean landed = bossHit(u, p, d.num("damage"), Opt.of("noSource", true));
                if (landed) {
                    burn(p, d.num("burn"));
                    showFaceText(p, "§6§l햇살에 데었습니다", Opt.of("dist", 2.2, "up", 0.35));
                }
            }
        }
        if (k % 8 == 0) play(u.dim, "mob.blaze.breathe", bodyOf(u), 0.6, 0.8);
    }

    // ------------------------------------------------------------ 달빛 사냥 (밤 · 황혼)

    public static boolean hunt(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("hunt");
        M m = mem(u);
        Vec3 b = bodyOf(u);
        List<ServerPlayer> lv = level(u, list);
        List<ServerPlayer> far = new ArrayList<>();
        for (ServerPlayer p : lv) if (hdist(p.position(), b) > d.num("min")) far.add(p);
        List<ServerPlayer> pool = !far.isEmpty() ? far : lv;
        if (pool.isEmpty()) return false;
        ServerPlayer prey = shuffle(pool).get(0);
        Hunt H = new Hunt(prey);
        m.hunt = H;
        m.body.want = angOf(dirTo(b, prey.position()));
        u.api.anim(u, "howl");
        sound(u, snd(u, "howl"), headAt(u, "moon"), 0.9, 1.2);
        int pounces = (int) d.num("pounces");
        title(u, "§b§l달빛 사냥감!", "§f하티가 " + pounces + "번 잇따라 덮칩니다 §7— 푸른 원 밖으로 계속 움직이세요", List.of(prey));
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer p : list) if (!p.getUUID().equals(prey.getUUID())) others.add(p);
        faceAll(u, "§b" + prey.getName().getString() + "§f님이 사냥감입니다", Opt.of("dist", 2.4, "up", 0.45), others);
        explain(u, "hunt", "§b[보스] 달빛 사냥: §f하티가 한 사람을 사냥감으로 정해 " + pounces + "번 잇따라 덮칩니다 (푸른 원). 내려앉은 자리에는 서리가 남아 밟으면 느려지고 아픕니다. §e사냥이 끝나면 "
                + Js.str(Js.round(d.num("winded") / 20)) + "초 동안 지쳐 받는 피해 +" + Js.str(Js.round(d.num("bonus") * 100)) + "%");
        int lead = 16;
        int windup = (int) d.num("windup");
        int time = (int) d.num("time");
        double height = d.num("height");
        int cycle = windup + time + 6;
        List<Bosses.Step> steps = new ArrayList<>();
        for (int i = 0; i < pounces; i++) {
            int t0 = lead + i * cycle;
            steps.add(step(t0, (x, t) -> huntAim(x, H)));
            steps.add(step(t0 + windup, (x, t) -> {
                if (H.plan != null) jump(x, H.plan, time, height);
            }));
            steps.add(step(t0 + windup + time, (x, t) -> huntHit(x, H)));
        }
        int end = lead + pounces * cycle;
        steps.add(step(end, (x, t) -> winded(x)));
        int windedT = (int) d.num("winded");
        u.api.cast(u, "hunt", end + windedT, steps);
        // 바로 이어서 태양 바퀴가 나오지 않게
        m.next.put("wheel", Math.max(m.next.get("wheel"), now + end + windedT + 60));
        return true;
    }

    /** 사냥감이 쓰러지거나 나가면 남은 사람 중 가장 가까운 사람으로 */
    static ServerPlayer preyOf(Bosses.Unit u, Hunt H) {
        List<ServerPlayer> list = level(u, targets(u));
        if (H.prey != null && ok(H.prey) && okTarget(H.prey)) {
            for (ServerPlayer p : list) if (p.getUUID().equals(H.prey.getUUID())) return H.prey;
        }
        Vec3 b = bodyOf(u);
        ServerPlayer best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (ServerPlayer p : list) {
            double r = hdist(p.position(), b);
            if (r < bd) {
                bd = r;
                best = p;
            }
        }
        H.prey = best;
        return best;
    }

    static void huntAim(Bosses.Unit u, Hunt H) {
        Obj d = u.def.obj("hunt");
        ServerPlayer p = preyOf(u, H);
        if (p == null) {
            H.plan = null;
            return;
        }
        int windup = (int) d.num("windup");
        H.plan = leapPlan(u, new Vec3(p.getX(), groundUnder(p, u.center.y), p.getZ()));
        disc(u.dim, H.plan.spot(), d.num("radius"), windup, NIGHT);
        SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(p.getX(), p.getY() + 2.6, p.getZ()), Opt.of("dir", new Vec3(0, 1, 0), "size", 1.1, "life", windup / 20.0, "color", MOON,
                "alpha", 0.9));
        mem(u).body.want = H.plan.yaw();
        u.api.anim(u, "hunt", null, LEAP, "0");
        play(u.dim, snd(u, "roar"), headAt(u, "moon"), 1.2, 0.7);
        showFaceText(p, "§b§l덮칩니다! §f(" + (H.n + 1) + "/" + (int) d.num("pounces") + ")", Opt.of("dist", 2.4, "up", 0.45));
    }

    static void huntHit(Bosses.Unit u, Hunt H) {
        Obj d = u.def.obj("hunt");
        LeapPlan P = H.plan;
        H.plan = null;
        H.n += 1;
        if (P == null) return;
        Vec3 spot = P.spot();
        double radius = d.num("radius");
        for (ServerPlayer p : targets(u)) {
            if (hdist(p.position(), spot) > radius + 0.3 || Math.abs(p.getY() - spot.y) > 3) continue;
            Vec3 dir = dirTo(spot, p.position());
            double[] kk = keepIn(u, p, dir.x * 0.7, dir.z * 0.7);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.4), "effect", effect("slowness", (int) d.num("slow"), 0)));
        }
        landFx(u, spot, radius, NIGHT);
        frostPatch(u, spot);
        u.api.anim(u, "land", null, LEAP, null);
    }

    /** 사냥이 끝남: 지침 (받는 피해 +bonus, 틀이 끝날 때까지 공격 없음) */
    static void winded(Bosses.Unit u) {
        Obj d = u.def.obj("hunt");
        M m = mem(u);
        long now = Tasks.now();
        m.hunt = null;
        m.windedUntil = now + (long) d.num("winded");
        m.body.want = null;
        u.api.anim(u, "winded");
        play(u.dim, snd(u, "pant"), headAt(u), 0.7, 1.2);
        for (String w : new String[] {"sun", "moon"}) dust(u.dim, headAt(u, w), 1.1, FROST, 0.9);
        faceAll(u, "§b§l지쳤습니다! §f받는 피해 +" + Js.str(Js.round(d.num("bonus") * 100)) + "%", Opt.of("dist", 2.4, "up", 0.45), targets(u));
    }

    /** 서리 자국: life 틱 동안 남음, 1초마다 안의 사람: 느림 + 약한 피해 (자국이 겹쳐도 사람마다 1초에 한 번) */
    static void frostPatch(Bosses.Unit u, Vec3 at) {
        Obj F = u.def.obj("hunt").obj("frost");
        long now = Tasks.now();
        long lifeT = (long) F.num("life");
        mem(u).frost.add(new Frost(at, now, now + lifeT));
        double life = lifeT / 20.0;
        double radius = F.num("radius");
        SkillFx.fx(u.dim, "rpg:tele_disc", new Vec3(at.x, at.y + 0.06, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", life, "color", NIGHT, "alpha", 0.85));
        SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(at.x, at.y + 0.07, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", life, "color", FROST, "alpha", 0.45, "grow",
                1.0));
        play(u.dim, "random.glass", at, 0.8, 0.6);
    }

    static void frostTick(Bosses.Unit u, long now) {
        Obj F = u.def.obj("hunt").obj("frost");
        M m = mem(u);
        List<Frost> list = m.frost;
        double radius = F.num("radius");
        for (int i = list.size() - 1; i >= 0; i--) {
            Frost f = list.get(i);
            if (now >= f.until()) {
                list.remove(i);
                continue;
            }
            long k = now - f.t0();
            if (k % 10 == 5) {
                double a = random() * TAU;
                double r = random() * radius;
                SkillFx.fx(u.dim, "rpg:dust", new Vec3(f.at().x + Math.cos(a) * r, f.at().y + 0.4, f.at().z + Math.sin(a) * r), Opt.of("size", 0.7, "life", 0.8, "color",
                        FROST, "alpha", 0.85));
            }
            if (k == 0 || k % 20 != 0) continue;
            for (ServerPlayer p : targets(u)) {
                if (hdist(p.position(), f.at()) > radius + 0.2 || Math.abs(p.getY() - f.at().y) > 2) continue;
                // 겹친 자국 (같은 자리에 두 번 덮침 등) 도 사람마다 1초에 한 번
                if (now - m.frostHit.getOrDefault(p.getUUID(), -100L) < 20) continue;
                m.frostHit.put(p.getUUID(), now);
                bossHit(u, p, F.num("dmg"), Opt.of("noSource", true, "effect", effect("slowness", (int) F.num("slow"), 0)));
            }
        }
    }

    // ------------------------------------------------------------ 해와 달의 심판 (즉사: 원판의 제 색 반원)

    static String sideName(String side) {
        return side.equals("sun") ? "해" : "달";
    }

    static String sideText(String side) {
        return side.equals("sun") ? "§6" : "§b";
    }

    /** p 가 서 있는 원판 쪽 ("sun" · "moon" · "line" · null) */
    static String sideOf(Bosses.Unit u, ServerPlayer p) {
        return Arena.discSide(p.position(), u.center, u.enc.def.obj("arena").num("disc"));
    }

    public static void judge(Bosses.Unit u, long now, List<ServerPlayer> list, boolean second) {
        Obj J = u.def.obj("judge");
        M m = mem(u);
        Vec3 c = u.center;
        Body b = m.body;
        double K = u.def.obj("drive").num("keep");
        // 필드 북쪽 가장자리 (원판 경계선을 늘인 곳이라 해 · 달 반원에서 같은 거리). 늘 북쪽: 가운데 (남쪽) 를 보면 왼쪽 스콜 (해) 이 동쪽 해 반원 위,
        // 오른쪽 하티 (달) 가 서쪽 달 반원 위 — 머리 색이 반원 색과 맞음
        Vec3 E = new Vec3(c.x, c.y, c.z - K);
        m.judge = new Judge(second, now, E);
        u.immune = true;
        b.want = null;
        int leapT = (int) J.num("leap");
        int windup = (int) J.num("windup");
        u.api.anim(u, "leap", null, LEAP, "0");
        jump(u, new LeapPlan(new Vec3(E.x, 0, E.z), E, angOf(dirTo(new Vec3(b.x, 0, b.z), E))), leapT, 5);
        sound(u, snd(u, "roar"), headAt(u), 0.7, 1.2);
        title(u, "§e§l해와 달의 심판", "§f스콜과 하티가 물러나 울부짖습니다", list);
        int markAt = leapT + 6;
        List<Bosses.Step> steps = new ArrayList<>();
        steps.add(step(leapT, (x, t) -> judgeLand(x)));
        steps.add(step(markAt, (x, t) -> judgeMark(x)));
        steps.add(step(markAt + windup, (x, t) -> judgeVerdict(x)));
        if (second) steps.add(step(markAt + (int) J.num("swapAt"), (x, t) -> judgeSwap(x)));
        u.api.cast(u, "judge", markAt + windup + 2, steps);
    }

    static void judgeLand(Bosses.Unit u) {
        Judge J = mem(u).judge;
        if (J == null) return;
        landFx(u, floorAt(u, J.E.x, J.E.z), 3, SUN);
        u.api.anim(u, "land", null, LEAP, null);
        mem(u).body.want = angOf(dirTo(J.E, u.center));
    }

    static void judgeMark(Bosses.Unit u) {
        Obj J = u.def.obj("judge");
        M m = mem(u);
        Judge G = m.judge;
        if (G == null) return;
        long now = Tasks.now();
        String first = random() < 0.5 ? "sun" : "moon";
        String second = first.equals("sun") ? "moon" : "sun";
        List<ServerPlayer> list = targets(u);
        pullIn(u, list);
        List<ServerPlayer> mixed = shuffle(list);
        List<JudgeMark> marks = new ArrayList<>();
        for (int i = 0; i < mixed.size(); i++) marks.add(new JudgeMark(mixed.get(i), i % 2 == 0 ? first : second));
        G.marks = marks;
        G.stage = "mark";
        G.t0 = now;
        G.until = now + (long) J.num("windup");
        G.swapAt = G.second ? now + (long) J.num("swapAt") : null;
        u.api.anim(u, "judge", null, HOWL, "0");
        sound(u, snd(u, "howl"), headAt(u), 0.6, 1.4);
        play(u.dim, "mob.wither.spawn", u.center, 1.4, 0.4);
        Vec3 h = headAt(u);
        for (JudgeMark mk : G.marks) {
            Vec3 l = mk.p.position();
            boolean sun = mk.side.equals("sun");
            SkillFx.beam(u.dim, h, new Vec3(l.x, l.y + 1.4, l.z), sun ? SUN : MOON, Opt.of("half", 0.4, "thick", 0.14, "life", 0.6, "gap", 0.6));
            title(u, sun ? "§6§l해의 표식" : "§b§l달의 표식", sun ? "§f가운데 원판의 §6금빛 반원(동쪽)§f에 서세요" : "§f가운데 원판의 §b푸른 반원(서쪽)§f에 서세요", List.of(mk.p));
        }
        explain(u, "judge", "§e[보스] 해와 달의 심판: §f사람마다 해(금빛) 또는 달(푸른) 표식이 붙습니다. " + Js.str(Js.round(J.num("windup") / 20))
                + "초 뒤 §c가운데 원판에서 제 표식 색 반원 위에 서 있지 않으면 즉사 §7(가운데 흰 줄·원판 밖도 즉사, 발밑 고리가 초록이면 안전). 스콜과 하티는 그동안 무적");
        if (G.second) say(u, "§d[보스] §f두 번째 심판은 " + Js.toFixed(J.num("swapAt") / 20, 1) + "초 뒤 §d표식이 뒤바뀝니다!");
    }

    /**
     * 담장 밖 (성문 · 둑 · 필드 바깥 띠) 에서 싸우던 사람은 표식을 받기 전에 담장 안으로 끌려 들어옴 (선돌 안쪽, 제 방향 그대로):
     * 담장을 돌아 성문으로 들어오면 제 반원까지 제때 가지 못하므로
     */
    static void pullIn(Bosses.Unit u, List<ServerPlayer> list) {
        double R = arenaRadius(u);
        Vec3 c = u.center;
        double to = R - 4.5;
        for (ServerPlayer p : list) {
            Vec3 l = p.position();
            double r = hdist(l, c);
            if (r <= R + 0.3) continue;
            Vec3 at = new Vec3(c.x + ((l.x - c.x) / r) * to, c.y, c.z + ((l.z - c.z) / r) * to);
            BossKit.teleport(p, at, new Vec3(c.x, c.y + 1.6, c.z));
            dust(u.dim, l, 1.6, MOON, 0.7);
            dust(u.dim, at, 1.6, SUN, 0.7);
            showFaceText(p, "§e§l심판에 끌려 들어왔습니다", Opt.of("dist", 2.4, "up", 0.45));
        }
    }

    static void judgeSwap(Bosses.Unit u) {
        Judge G = mem(u).judge;
        if (G == null || !"mark".equals(G.stage)) return;
        G.swapped = true;
        Vec3 h = headAt(u);
        for (JudgeMark mk : G.marks) {
            if (mk.done) continue;
            mk.side = mk.side.equals("sun") ? "moon" : "sun";
            if (!ok(mk.p)) continue;
            Vec3 l = mk.p.position();
            SkillFx.beam(u.dim, h, new Vec3(l.x, l.y + 1.4, l.z), DUSK, Opt.of("half", 0.4, "thick", 0.14, "life", 0.6, "gap", 0.6));
            title(u, "§d§l표식이 뒤바뀝니다!", sideText(mk.side) + sideName(mk.side) + "의 표식 §f→ " + (mk.side.equals("sun") ? "§6금빛 반원(동쪽)" : "§b푸른 반원(서쪽)"),
                    List.of(mk.p));
        }
        play(u.dim, "mob.evocation_illager.prepare_wololo", u.center, 0.8, 1.4);
        sound(u, snd(u, "howl"), h, 1.1, 1.2);
    }

    /** 매 틱: 표식 (발밑 고리: 제 반원 위면 초록, 아니면 붉게 · 머리 위 해/달 문양 · 원판 가장자리 반원 · 남은 초) */
    static void judgeTick(Bosses.Unit u, long now) {
        Judge G = mem(u).judge;
        if (G == null || !"mark".equals(G.stage)) return;
        for (JudgeMark mk : G.marks) {
            if (!mk.done && (!ok(mk.p) || !okTarget(mk.p) || mk.p.level() != u.dim || hdist(mk.p.position(), u.center) > 64)) mk.done = true;
        }
        List<JudgeMark> live = new ArrayList<>();
        for (JudgeMark mk : G.marks) if (!mk.done) live.add(mk);
        long k = now - G.t0;
        long left = G.until - now;
        if (k % 5 == 0) {
            for (JudgeMark mk : live) {
                boolean safe = mk.side.equals(sideOf(u, mk.p));
                disc(u.dim, mk.p.position(), 1.3, 5, safe ? SAFE : RED, Opt.of("fill", false, "alpha", 0.95));
            }
        }
        if (k % 20 == 0) {
            for (JudgeMark mk : live) {
                SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(mk.p.getX(), mk.p.getY() + 2.6, mk.p.getZ()), Opt.of("dir", new Vec3(0, 1, 0), "size", 1.2, "life", 1.0, "color",
                        mk.side.equals("sun") ? SUN : MOON, "alpha", 0.9));
            }
            // 원판 가장자리: 동쪽 반원 금빛, 서쪽 반원 푸른 빛
            double R = u.enc.def.obj("arena").num("disc");
            Vec3 c = u.center;
            for (int i = 0; i <= 16; i++) {
                double a = -Math.PI / 2 + (i / 16.0) * Math.PI;
                for (int s : new int[] {1, -1}) {
                    SkillFx.fx(u.dim, "rpg:tele_disc", new Vec3(c.x + s * Math.cos(a) * R, c.y + 0.07, c.z + Math.sin(a) * R), Opt.of("dir", Vec3.ZERO, "size", 0.8, "life", 1.0,
                            "color", s > 0 ? SUN : MOON, "alpha", 0.9));
                }
            }
        }
        if (left > 0 && left % 20 == 0 && left <= 60) {
            for (JudgeMark mk : live) showFaceText(mk.p, sideText(mk.side) + "§l" + sideName(mk.side) + "의 심판 " + (left / 20), Opt.of("dist", 2.4, "up", 0.45));
        }
        // 두 번째 심판: 표식이 뒤바뀌기 3 · 2 · 1초 전
        long toSwap = G.swapAt != null && !G.swapped ? G.swapAt - now : -1;
        if (toSwap > 0 && toSwap % 20 == 0 && toSwap <= 60) for (JudgeMark mk : live) showFaceText(mk.p, "§d§l표식이 뒤바뀜 " + (toSwap / 20), Opt.of("dist", 2.4, "up", 0.25));
        if (k % 10 == 0) {
            for (JudgeMark mk : live) {
                boolean safe = mk.side.equals(sideOf(u, mk.p));
                String where = mk.side.equals("sun") ? "§6금빛 반원(동쪽)" : "§b푸른 반원(서쪽)";
                BossKit.notify(mk.p, safe ? sideText(mk.side) + sideName(mk.side) + "의 표식 §a— 제자리, 안전 §7(" + (long) Math.ceil(left / 20.0) + "초)"
                        : sideText(mk.side) + sideName(mk.side) + "의 표식 §c— " + where + "§c으로!", 15);
            }
        }
    }

    /** 판결: 표식이 남은 사람 중 제 반원 밖이면 즉사 → 무적 풀림 → 가장 많이 모인 곳으로 도약 */
    static void judgeVerdict(Bosses.Unit u) {
        M m = mem(u);
        Judge G = m.judge;
        if (G == null) return;
        long now = Tasks.now();
        Vec3 c = u.center;
        double R = u.enc.def.obj("arena").num("disc");
        m.judge = null;
        u.immune = false;
        m.bigUntil = now + 100;
        // 원판 두 반원에서 빛 (해 = 금빛 폭발, 달 = 푸른 물보라)
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(c.x + R * 0.5, c.y + 1.5, c.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 4.0, "life", 0.6, "color", SUN, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:splash", new Vec3(c.x - R * 0.5, c.y + 2, c.z), Opt.of("size", 4.0, "life", 0.7, "color", MOON, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(c.x, c.y + 0.1, c.z), Opt.of("dir", Vec3.ZERO, "size", R + 1, "life", 0.8, "color", new double[] {1, 1, 1}, "alpha", 0.95));
        play(u.dim, "mob.warden.sonic_boom", c, 0.8, 1);
        for (JudgeMark mk : G.marks) {
            if (mk.done || !ok(mk.p) || !okTarget(mk.p)) continue;
            if (mk.side.equals(sideOf(u, mk.p))) {
                showFaceText(mk.p, "§a§l심판을 견뎠습니다", Opt.of("dist", 2.4, "up", 0.45));
                continue;
            }
            say(u, "§4[보스] §e" + mk.p.getName().getString() + "§f님이 " + sideText(mk.side) + sideName(mk.side) + "의 심판§f을 받아 쓰러졌습니다");
            Status.kill(mk.p, u.boss);
        }
        // 울부짖는 모습을 풂 (빈 동작 rest 가 같은 틀을 이어받음)
        u.api.anim(u, "rest", null, HOWL, null);
        // 판결 뒤: 가장 많이 모인 곳으로 도약 (원판에 모인 사람들이 흩어지게)
        List<ServerPlayer> list = level(u, targets(u));
        Vec3 spot = crowdSpot(u, list);
        if (spot != null) {
            startPounce(u, leapPlan(u, spot));
            List<ServerPlayer> near = new ArrayList<>();
            for (ServerPlayer p : list) if (hdist(p.position(), spot) <= u.def.obj("pounce").num("radius") + 3) near.add(p);
            faceAll(u, "§6§l도약! §f원 밖으로 흩어지세요", Opt.of("dist", 2.4, "up", 0.45), near);
        }
    }

    /** 사람이 가장 많이 모인 곳 (그 사람 발밑): pounce.radius 칸 안에 다른 사람이 가장 많은 사람 */
    public static Vec3 crowdSpot(Bosses.Unit u, List<ServerPlayer> list) {
        double R = u.def.obj("pounce").num("radius");
        ServerPlayer best = null;
        int n = -1;
        for (ServerPlayer p : shuffle(list)) {
            int k = 0;
            for (ServerPlayer q : list) if (hdist(q.position(), p.position()) <= R) k++;
            if (k > n) {
                n = k;
                best = p;
            }
        }
        return best != null ? new Vec3(best.getX(), groundUnder(best, u.center.y), best.getZ()) : null;
    }

    // ------------------------------------------------------------ 낮밤 바뀜 · 황혼

    public static void swapStance(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj S = u.def.obj("stance");
        boolean night = "day".equals(m.stance.mode);
        m.stance.mode = night ? "night" : "day";
        m.stance.next = now + (long) S.num("every");
        m.look.dayTo = night ? 0 : 1;
        m.body.want = null;
        u.api.anim(u, "howl");
        Vec3 h = headAt(u, night ? "moon" : "sun");
        sound(u, snd(u, "howl"), h, night ? 0.9 : 0.6, 1.2);
        Vec3 b = bodyOf(u);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(b.x, u.center.y + 0.1, b.z), Opt.of("dir", Vec3.ZERO, "size", 6.0, "life", 0.8, "color", night ? MOON : SUN, "alpha", 0.9));
        SkillFx.fx(u.dim, "rpg:bash_burst", h, Opt.of("dir", new Vec3(0, 0, 1), "size", 2.5, "life", 0.5, "color", night ? MOON : EMBER, "alpha", 0.95));
        title(u, night ? "§b§l밤 — 하티가 깨어납니다" : "§6§l낮 — 스콜이 깨어납니다", night ? "§f약점: §e스콜(검은 머리) 쪽 옆구리 §7· 달빛 사냥" : "§f약점: §e하티(흰 머리) 쪽 옆구리 §7· 태양 바퀴",
                targets(u));
        int howl = (int) S.num("howl");
        u.api.cast(u, "howl", howl);
        // 깨어난 머리의 큰 패턴은 울부짖은 뒤 조금 쉬고
        String k = night ? "hunt" : "wheel";
        m.next.put(k, Math.max(m.next.get(k), now + howl + 60));
    }

    public static void startDusk(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj D = u.def.obj("dusk");
        m.dusk = true;
        u.cdMult = D.num("cd");
        m.look.duskTo = 1;
        m.body.want = null;
        u.api.anim(u, "howl");
        sound(u, snd(u, "howl"), headAt(u, "sun"), 0.6, 1.4);
        sound(u, snd(u, "howl"), headAt(u, "moon"), 0.9, 1.4);
        Vec3 b = bodyOf(u);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(b.x, u.center.y + 0.1, b.z), Opt.of("dir", Vec3.ZERO, "size", 8.0, "life", 1.0, "color", DUSK, "alpha", 0.95));
        for (String w : new String[] {"sun", "moon"}) {
            SkillFx.fx(u.dim, "rpg:bash_burst", headAt(u, w), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.5, "life", 0.6, "color", w.equals("sun") ? EMBER : MOON, "alpha", 0.95));
        }
        title(u, "§5§l황혼", "§f스콜과 하티가 함께 깨어났습니다 §7— 약점이 사라집니다", targets(u));
        explain(u, "dusk", "§5[보스] 황혼: §f두 머리가 모두 깨어나 태양 바퀴와 달빛 사냥을 모두 씁니다. §7약점 옆구리는 사라지고 패턴이 더 자주 나옵니다");
        int howl = (int) D.num("howl");
        u.api.cast(u, "dusk", howl);
        m.next.put("wheel", Math.max(m.next.get("wheel"), now + howl + 40));
        m.next.put("hunt", Math.max(m.next.get("hunt"), now + howl + 160));
    }

    // ------------------------------------------------------------ 높은 곳 · 둘레 연출

    /** 선돌 · 성문 위처럼 높은 곳 (바닥보다 high.above 칸 넘게) 에 grace 틱 넘게 머문 사람: 뛰어올라 끌어내림 (사람마다 cd 틱에 한 번) */
    static void highTick(Bosses.Unit u, long now) {
        if (now % 10 != 0 || !u.alive || !ok(u.boss)) return;
        Obj d = u.def.obj("high");
        M m = mem(u);
        Vec3 c = u.center;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : targets(u)) {
            // 뛰는 중이어도 셈 (높은 곳에서 계속 뛰며 버티는 것을 막음): 내려오면 처음부터 다시
            if (p.getY() - c.y < d.num("above")) continue;
            seen.add(p.getUUID());
            if (!m.highSince.containsKey(p.getUUID())) {
                m.highSince.put(p.getUUID(), now);
                showFaceText(p, "§6§l내려오세요! §f머물면 끌어내립니다", Opt.of("dist", 2.2, "up", 0.35));
            }
            if (now - m.highSince.get(p.getUUID()) < d.num("grace")) continue;
            if (now - m.high.getOrDefault(p.getUUID(), -1000L) < d.num("cd")) continue;
            m.high.put(p.getUUID(), now);
            Vec3 dir = dirTo(p.position(), c);
            SkillFx.fx(u.dim, "rpg:slash_x", new Vec3(p.getX(), p.getY() + 1.2, p.getZ()), Opt.of("dir", new Vec3(-dir.x, 0, -dir.z), "speed", 0.0, "spin", 0.0, "size", 1.6,
                    "life", 0.3, "color", stanceColor(u), "alpha", 0.95));
            dust(u.dim, p.position(), 1.8, SNOW, 0.6);
            play(u.dim, snd(u, "swing"), p.position(), 0.8, 1);
            // 때린 엔티티 없이 (게임의 밀어내기는 보스에게서 먼 쪽 = 담장 밖일 수 있음): 가운데 쪽으로만 끌어내림
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 1.1, dir.z * 1.1, 0.35), "noSource", true));
            showFaceText(p, "§6§l끌려 내려갑니다", Opt.of("dist", 2.2, "up", 0.35));
            explain(u, "high", "§6[보스] §f선돌·성문 위처럼 높은 곳에 " + Js.toFixed(d.num("grace") / 20, 1) + "초 넘게 머물면 스콜과 하티가 뛰어올라 끌어내립니다");
        }
        m.highSince.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 둘레 연출: 약점 옆구리 바닥 표시 (잠든 머리 색), 깨어난 쪽 선돌의 눈이 빛남 */
    static void ambientTick(Bosses.Unit u, long now) {
        M m = mem(u);
        if (now % 10 == 0 && m.judge == null && m.leap == null && m.body.h <= 0.01) {
            int side = weakSide(u);
            if (side != 0) {
                Vec3 r = rightOf(m.body.yaw);
                double off = bossSize(u.def).half() + 1.3;
                Vec3 at = new Vec3(m.body.x + r.x * side * off, u.center.y, m.body.z + r.z * side * off);
                SkillFx.fx(u.dim, "rpg:tele_disc", new Vec3(at.x, at.y + 0.06, at.z), Opt.of("dir", Vec3.ZERO, "size", 1.3, "life", 0.55, "color", side > 0 ? MOON : SUN,
                        "alpha", 0.8));
            }
        }
        if (now % 40 != 0) return;
        String head = awake(u);
        Vec3 c = u.center;
        if (u.feat == null) return;
        for (Arena.Stone s : u.feat.stones()) {
            if ((head.equals("sun") && !s.sun()) || (head.equals("moon") && s.sun())) continue;
            Vec3 d = dirTo(s.at(), c);
            SkillFx.fx(u.dim, "rpg:dust", new Vec3(s.at().x + d.x * 0.9, s.at().y + 2.4, s.at().z + d.z * 0.9), Opt.of("size", 1.1, "life", 1.6, "color", s.sun() ? SUN : MOON,
                    "alpha", 0.75));
        }
    }

    /** 땅에 내려놓음 (뛰다가 쓰러지거나 싸움이 끝날 때): 높이 0, 걸음 0, 뛰는 모습 대신 내려앉기 */
    static void ground(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null || !ok(enc.boss)) return;
        Body b = m.body;
        // 뛰는 중이면 그 자리에서 땅으로 (앞으로 순간이동하지 않음)
        boolean airborne = b.h > 0.01 || m.leap != null;
        m.leap = null;
        b.h = 0;
        b.gaitTo = 0;
        b.gait = 0;
        try {
            place(enc);
        } catch (RuntimeException e) {
            // 무시
        }
        b.gaitSent = setProp(enc, "rpg:gait", 0, null);
        // 들고 있던 모습 (웅크림 · 뛰는 모습) 을 풂: 떠 있었으면 내려앉기, 아니면 빈 동작
        enc.api.anim(enc, airborne ? "land" : "rest", null, LEAP, null);
    }

    // ------------------------------------------------------------ 모듈

    /** 약점 옆구리 (+weak, "약점") · 지친 동안 (+hunt.bonus, "지침") — 둘 다면 곱함 */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        M m = mem(u);
        if (m == null) return null;
        double mult = 1;
        String mark = "";
        double weak = u.def.obj("stance").num("weak");
        if (weak > 0 && m.judge == null && onFlank(u, p.position())) {
            mult *= 1 + weak;
            mark += WEAK_MARK;
        }
        double bonus = u.def.obj("hunt").num("bonus");
        if (bonus > 0 && Tasks.now() < m.windedUntil) {
            mult *= 1 + bonus;
            mark += WINDED_MARK;
        }
        return Bosses.Hurt.of(amount * mult, mark);
    }

    /** 원거리 견제: 등장 · 심판 · 달빛 사냥 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m != null && m.judge == null && m.hunt == null && (u.cast == null || !"arrive".equals(u.cast.id));
    }

    /** 낮 = 태양 불씨 (불), 밤 = 달 서리 (느림 II), 황혼 = 번갈아 */
    @Override
    public Obj farStyle(Bosses.Unit u) {
        Obj F = u.def.obj("far");
        M m = mem(u);
        if (m == null) return null;
        boolean night;
        if (m.dusk) {
            m.farFlip = !m.farFlip;
            night = m.farFlip;
        } else night = "night".equals(m.stance.mode);
        if (!night) return BossKit.obj("name", F.get("name"), "look", F.get("look"), "effect", F.get("effect"), "burn", F.get("burn"));
        Obj N = F.obj("night");
        explain(u, "farNight", "§b[보스] " + N.str("name") + ": §f밤에는 하티가 " + N.str("name") + "를 던집니다 — 원 안에 있으면 피해 + 느림");
        return BossKit.obj("name", N.get("name"), "look", N.get("look"), "effect", N.get("effect"), "burn", 0);
    }

    /** 던지는 곳: 그 모양의 머리 입 (불씨 = 스콜, 서리 = 하티) */
    @Override
    public Vec3 farFrom(Bosses.Unit u, Obj style) {
        if (mem(u) == null) return null;
        return headAt(u, style != null && "frost".equals(style.strOrNull("look")) ? "moon" : "sun");
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        Vec3 c = enc.center;
        // 등장: 깨운 사람에게서 가장 먼 선돌 위에서 울부짖고 가운데로 뛰어내림
        Vec3 p = enc.starter != null && !enc.starter.isRemoved() ? enc.starter.position() : new Vec3(c.x, c.y, c.z + 8);
        List<Arena.Stone> stones = enc.feat != null ? enc.feat.stones() : List.of();
        Vec3 st = new Vec3(c.x, c.y, c.z);
        if (!stones.isEmpty()) {
            st = stones.get(0).at();
            for (Arena.Stone s : stones) if (hdist(s.at(), p) > hdist(st, p)) st = s.at();
        }
        double yaw = !stones.isEmpty() ? angOf(dirTo(st, c)) : angOf(dirTo(c, p));
        M m = new M();
        m.next.put("bite", now + 40);
        m.next.put("pounce", now + (long) d.obj("pounce").num("first"));
        m.next.put("wheel", now + (long) d.obj("wheel").num("first"));
        m.next.put("hunt", now + (long) d.obj("hunt").num("first"));
        m.bigUntil = now + 100;
        Body b = m.body;
        b.x = st.x;
        b.z = st.z;
        b.h = !stones.isEmpty() ? 5 : 0;
        b.yaw = yaw;
        m.stance.mode = "day";
        m.stance.next = now + (long) d.obj("stance").num("first");
        enc.mem = m;
        for (Bosses.Unit u : enc.units) u.mem = m;
        // 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기는 모듈이)
        enc.meleeAt = NEVER;
        place(enc);
        bodyTick(enc);
        enc.api.anim(enc, "howl");
        sound(enc, snd(enc, "howl"), headAt(enc, "sun"), 0.6, 1.4);
        double every = d.obj("stance").num("every");
        double weak = d.obj("stance").num("weak");
        enc.api.cast(enc, "arrive", ARRIVE_HOWL + ARRIVE_TIME + 16, step(ARRIVE_HOWL, (x, t) -> {
            x.api.anim(x, "leap", null, LEAP, "0");
            jump(x, new LeapPlan(new Vec3(c.x, 0, c.z), floorAt(x, c.x, c.z), yaw), ARRIVE_TIME, ARRIVE_HEIGHT);
        }), step(ARRIVE_HOWL + ARRIVE_TIME, (x, t) -> {
            landFx(x, floorAt(x, c.x, c.z), 4, SUN);
            x.api.anim(x, "land", null, LEAP, null);
            explain(x, "stance", "§e[보스] 낮과 밤: §f" + Js.str(Js.round(every / 20)) + "초마다 낮(스콜 — 검은 머리, 몸 왼쪽)과 밤(하티 — 흰 머리, 몸 오른쪽)이 바뀝니다. §e잠든 머리 쪽 옆구리를 때리면 피해 +"
                    + Js.str(Js.round(weak * 100)) + "%§f (옆구리 바닥에 표시, 피해 숫자 앞 \"약점\")");
        }));
    }

    /** forceNext 의 패턴을 바로 (시작했으면 true) */
    static boolean forced(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        M m = mem(u);
        switch (id) {
            case "bite":
                return bite(u, now, list);
            case "pounce":
                return pounce(u, now, list);
            case "wheel":
                return wheel(u, now, list);
            case "hunt":
                return hunt(u, now, list);
            case "judge":
                judge(u, now, list, !m.judgeDone.isEmpty());
                return true;
            case "swap":
                swapStance(u, now);
                return true;
            case "dusk":
                startDusk(u, now);
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        if (m == null || m.leap != null) return false;
        Obj d = u.def;
        String f = forceNext;
        if (f != null) {
            forceNext = null;
            return forced(u, f, now, list);
        }
        double r = u.api.ratio(u);
        // 1) 해와 달의 심판 (큰 패턴)
        if (now >= m.bigUntil) {
            List<Double> at = d.obj("judge").numbersOrNull("at");
            for (int i = 0; i < at.size(); i++) {
                double x = at.get(i);
                if (r <= x && !m.judgeDone.contains(x)) {
                    m.judgeDone.add(x);
                    judge(u, now, list, i > 0);
                    return true;
                }
            }
        }
        // 2) 황혼
        if (!m.dusk && r <= d.obj("dusk").num("from")) {
            startDusk(u, now);
            return true;
        }
        // 3) 낮과 밤 바뀜
        if (!m.dusk && now >= m.stance.next) {
            swapStance(u, now);
            return true;
        }
        String head = awake(u);
        // 4) 태양 바퀴 (낮 · 황혼) · 달빛 사냥 (밤 · 황혼)
        if (!head.equals("moon") && now >= m.next.get("wheel")) {
            if (wheel(u, now, list)) {
                m.next.put("wheel", now + every(u, pair(d.obj("wheel"), "every")));
                return true;
            }
            m.next.put("wheel", now + 20);
        }
        if (!head.equals("sun") && now >= m.next.get("hunt")) {
            if (hunt(u, now, list)) {
                m.next.put("hunt", now + every(u, pair(d.obj("hunt"), "every")));
                return true;
            }
            m.next.put("hunt", now + 20);
        }
        // 5) 도약
        if (now >= m.next.get("pounce")) {
            if (pounce(u, now, list)) {
                m.next.put("pounce", now + every(u, pair(d.obj("pounce"), "every")));
                return true;
            }
            m.next.put("pounce", now + 20);
        }
        // 6) 평타: 몸 앞의 가장 가까운 사람을 물어뜯음
        if (now >= m.next.get("bite") && bite(u, now, list)) {
            m.next.put("bite", now + (long) Js.round(d.obj("melee").num("every") * u.cdMult));
            return true;
        }
        return false;
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        Bosses.Unit u = enc;
        M m = mem(enc);
        if (!u.alive || !ok(u.boss) || m == null) return;
        leapTick(u, now);
        judgeTick(u, now);
        frostTick(u, now);
        highTick(u, now);
        ambientTick(u, now);
        if (!enc.alive) return;
        // 몸: 뛰는 중에는 leapTick 이, 패턴 중에는 제자리 (원하는 쪽으로 돎), 패턴이 없으면 걸음
        if (m.leap == null) {
            if (u.cast != null) standTick(u);
            else {
                m.body.want = null;
                walkTick(u, list != null ? list : targets(u));
            }
        }
        bodyTick(u);
    }

    /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 뛰다가 쓰러지면 땅에 내려놓음 */
    @Override
    public void dying(Bosses.Enc enc) {
        ground(enc);
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        ground(enc);
        if (m.judge != null) enc.api.anim(enc, "rest", null, HOWL, null);
        m.frost.clear();
        m.hunt = null;
        m.wheel = null;
        m.judge = null;
        enc.immune = false;
    }
}
