package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.faceAll;
import static mmorpg.fabric.boss.BossKit.fanMarks;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.inFan;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.lineMarks;
import static mmorpg.fabric.boss.BossKit.meleeTarget;
import static mmorpg.fabric.boss.BossKit.nearSegment;
import static mmorpg.fabric.boss.BossKit.pair;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.say;
import static mmorpg.fabric.boss.BossKit.showFaceText;
import static mmorpg.fabric.boss.BossKit.snd;
import static mmorpg.fabric.boss.BossKit.sound;
import static mmorpg.fabric.boss.BossKit.title;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.33.0 공허 기사 (Lv.55 — 균열을 걷는 자, 수치는 rules_more BOSSES.void_knight): 한 방이 매우 아픈 대신 칠 때마다 자세를 잡고 (선딜레이 — 보라 예고),
 * 가만히 있지 않고 대상 둘레를 돌며 (옆걸음) 멀면 달려들고 너무 붙으면 뒤로 뛰는 날쌘 기사. 공허의 결투장 (신전 테마 void) 에서 싸움.
 * 움직이지 않는 보스 (중력 · 충돌 없음) 를 모듈이 매 틱 옮김 (엔진 pose — 스콜과 하티와 같은 방식, 설정 drive). 엔진 평타는 쓰지 않음.
 * 엔티티 속성: rpg:gait (앞뒤 걸음 → stride 동작 무게) · rpg:strafe (-1 ~ 1 옆걸음 → strafe) · rpg:phase (1 = 각성 — 빛이 센 텍스처).
 * <ul>
 * <li>내려베기: 머리 위로 들어 (windup) → 앞 부채꼴. 가장 셈. 뒤에 빈틈</li>
 * <li>돌진 찌르기: 긴 줄 예고 → 줄을 따라 순식간에 찔러 나감</li>
 * <li>도약 내려찍기: 떨어질 원 예고 → 뛰어올라 내려찍음. 뒤에 빈틈 (각성 뒤: 바깥 고리가 한 번 더 터짐 — 안쪽은 안전)</li>
 * <li>회전 베기: 몸 둘레 원 → 한 바퀴 넘게 돌며 벰 (원 밖으로). 뒤에 빈틈 (각성 뒤: 공허 검기 여섯 줄이 퍼짐 — 줄 사이로)</li>
 * <li>균열 걸음: 균열로 사라짐 (무적 · 안 보임) → 대상 등 뒤에 균열이 열림 (경고 원) → 나와서 겨누고 (부채꼴) 가로 베기 + 어둠</li>
 * <li>3연격: 한 걸음씩 나아가며 세 번 (부채꼴마다 예고) — 셋째가 가장 셈. 뒤에 빈틈</li>
 * <li>빈틈 (큰 공격 뒤 open 틱): 받는 피해 +opening.bonus (피해 숫자 앞 분홍 "빈틈"), 바닥에 보라 고리가 맥박침</li>
 * <li>각성 (체력 phase2.at): 포효 (무적) → 더 빨라짐 (선딜레이 × phase2.windup · 재사용 × phase2.cd · 움직임 × drive.fast), 베기마다 공허 검기 (날아가는 초승달 — 옆으로 비킴),
 * 보스 안개 (보랏빛), 공허 참: 가운데로 뛰어 칼을 세우고 (무적) → 빛나는 원 count 개 밖의 필드 전체를 벰 (원 안은 안전)</li>
 * <li>제한 시간 (enrage.limit 틱 — 보스 막대 아래 "공허 붕괴까지" 막대 · 남은 시간 알림): 넘기면 공허 붕괴 — 필드 안 모두 즉사 (every 틱마다 되풀이)</li>
 * </ul>
 * 패턴 함수 · 몸 도우미는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossVoidKnight implements BossModule {
    static final String KEY = "void_knight";
    public static final double[] VIOLET = {0.66, 0.34, 1.0};
    public static final double[] PINK = {1.0, 0.31, 0.85};
    public static final double[] LIGHT = {1.0, 0.86, 0.98};
    public static final double[] DEEP = {0.22, 0.1, 0.36};
    /** 예고 (어두운 바닥에서 보이게 밝은 보라) */
    public static final double[] MARK = {0.78, 0.3, 1.0};
    /** 공허 참 안전 원 (금빛) */
    public static final double[] SAFE = {1.0, 0.84, 0.36};
    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;
    static final long NEVER = Long.MAX_VALUE;

    /** 피해 숫자 앞 표시 (빈틈 — 근접 보너스 » 앞) */
    public static final String OPEN_MARK = "§d빈틈 ";
    /** 동작 틀: 공격 자세 (_ready → 휘두름 → _rec) · 움직임 (dash · backstep) */
    public static final String ATK = "rpg_atk";
    public static final String MOVE = "rpg_move";
    /** 보스 안개: 각성 · 공허 붕괴 */
    public static final String FOG_AWAKE = "rpg:void_awake";
    public static final String FOG_COLLAPSE = "rpg:void_collapse";
    /** 등장 (균열에서 비집고 나와 칼을 세움 — 동작 spawn 1.85초) */
    public static final int ARRIVE = 40;

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (cleave · thrust · leap · spin · blink · combo · awaken · cross · collapse — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    // ------------------------------------------------------------ 기억

    /** 싸움의 기억: 몸 · 패턴마다 다음 틱 · 다음 공격 틱 · 빈틈 끝 · 각성 · 공허 참 · 제한 시간 · 움직임 · 공허 검기 · 알린 규칙 */
    public static final class M extends Bosses.Mem {
        public final Body body = new Body();
        public final Map<String, Long> next = new HashMap<>();
        public long nextAttack;
        public long openUntil;
        public boolean phase2;
        public boolean awakenPending;
        public long crossAt = NEVER;
        /** 싸움 시작 틱 · 공허 붕괴 틱 · 다음 붕괴 (넘긴 뒤 되풀이) */
        public long t0;
        public long limit;
        public long collapseAt;
        public final Set<Long> warned = new HashSet<>();
        public ServerBossEvent timer;
        String timerText;
        public Move move;
        public final List<Wave> waves = new ArrayList<>();
        public int orbitDir = 1;
        public long flipAt;
        public long backAt;
        public boolean dashing;
        public boolean vanished;
        public Cross cross;
        public final Set<String> told = new HashSet<>();
        public long stepAt;
    }

    /** 몸: 자리 (x, z — 바닥 높이 + 뛰는 높이 h), 몸 방향 yaw (라디안, +x 에서 +z 쪽으로), 패턴 중 돌아볼 쪽 want, 걸음 · 옆걸음 (지금 · 목표 · 보낸 값) */
    public static final class Body {
        public double x;
        public double z;
        public double h;
        public double yaw;
        public Double want;
        public double gait;
        public double gaitTo;
        public double strafe;
        public double strafeTo;
        Double gaitSent;
        Double strafeSent;
        Integer phaseSent;
        double[] placed;
    }

    /** 움직임 (달려듦 · 뒤로 뜀 · 찌르기 · 도약 · 걸음): from → to (바닥), 시작 틱 · 걸리는 틱 · 높이 (포물선), 종류 */
    public record Move(Vec3 from, Vec3 to, long t0, long time, double height, String kind) {}

    /** 공허 검기 (각성 뒤): 바닥의 출발점 · 방향 · 시작 틱 · 칸/틱 · 거리 · 폭 · 피해 · 맞은 사람 */
    public static final class Wave {
        public final Vec3 from;
        public final Vec3 dir;
        public final long t0;
        public final double speed;
        public final double range;
        public final double width;
        public final double damage;
        final Set<UUID> hit = new HashSet<>();

        Wave(Vec3 from, Vec3 dir, long t0, double speed, double range, double width, double damage) {
            this.from = from;
            this.dir = dir;
            this.t0 = t0;
            this.speed = speed;
            this.range = range;
            this.width = width;
            this.damage = damage;
        }
    }

    /** 공허 참: 안전 원 자리 (바닥) · 벨 각도 (라디안 — 십자 줄의 첫 줄) */
    public record Cross(List<Vec3> safe, double angle) {}

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    // ------------------------------------------------------------ 도우미

    static boolean ok(Entity e) {
        return e != null && !e.isRemoved();
    }

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

    static double wrapPi(double a) {
        double w = ((a % TAU) + TAU) % TAU;
        return w > Math.PI ? w - TAU : w;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double turnToward(double cur, double want, double step) {
        double d = wrapPi(want - cur);
        return Math.abs(d) <= step ? want : cur + Math.signum(d) * step;
    }

    static double toward(double v, double to, double s) {
        return Math.abs(to - v) <= s ? to : v + Math.signum(to - v) * s;
    }

    /** 몸 오른쪽 단위 방향 (몸 방향 yaw 에서 오른쪽 90° — 스콜과 하티와 같은 식) */
    public static Vec3 rightOf(double yaw) {
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    static Vec3 floorAt(Bosses.Unit u, double x, double z) {
        return new Vec3(x, u.center.y, z);
    }

    static Obj D(Bosses.Unit u) {
        return u.def.obj("drive");
    }

    /** 선딜레이 (각성 뒤 × phase2.windup) */
    public static int windup(Bosses.Unit u, double ticks) {
        M m = mem(u);
        double k = m != null && m.phase2 ? u.def.obj("phase2").num("windup") : 1;
        return Math.max(4, (int) Js.round(ticks * k));
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

    static double arenaRadius(Bosses.Unit u) {
        return u.enc.def.obj("arena").num("radius");
    }

    /** 가운데에서 keep 칸 안으로 */
    static Vec3 keepIn(Bosses.Unit u, double x, double z) {
        Vec3 c = u.center;
        double K = D(u).num("keep");
        double r = Math.hypot(x - c.x, z - c.z);
        if (r <= K) return new Vec3(x, c.y, z);
        return new Vec3(c.x + ((x - c.x) / r) * K, c.y, c.z + ((z - c.z) / r) * K);
    }

    /** 밀어내는 힘 (kx, kz): 담장 5칸 안에서는 바깥 성분을 빼고 조금 안쪽으로 (필드 밖으로 날아가지 않게) */
    static double[] knockIn(Bosses.Unit u, ServerPlayer p, double kx, double kz) {
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

    /** 공격이 닿는 사람 (싸우는 사람 — 바닥 둘레 위아래 4칸) */
    static List<ServerPlayer> targets(Bosses.Unit u) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : fighters(u)) if (Math.abs(p.getY() - u.center.y) < 4) out.add(p);
        return out;
    }

    /** list 중 몸에서 가장 가까운 사람 (도발한 사람이 maxDist 안이면 그 사람 — 없으면 null) */
    static ServerPlayer nearest(Bosses.Unit u, List<ServerPlayer> list, double maxDist) {
        Vec3 b = bodyOf(u);
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), b) <= maxDist && Math.abs(p.getY() - u.center.y) < 4) pool.add(p);
        return meleeTarget(u, pool, b);
    }

    // ------------------------------------------------------------ 몸

    /** 몸 (엔티티 자리): 바닥 높이 + 뛰는 높이 h */
    public static Vec3 bodyOf(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y + b.h, b.z);
    }

    /** 몸 발밑 바닥 */
    public static Vec3 feetOf(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y, b.z);
    }

    /** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음) */
    static void place(Bosses.Unit u) {
        Body b = mem(u).body;
        double[] k = {b.x, b.z, b.h, b.yaw};
        if (b.placed != null && java.util.Arrays.equals(b.placed, k) && ok(u.boss)) return;
        b.placed = k;
        u.api.pose(u, new Vec3(b.x, u.center.y + b.h, b.z), b.yaw);
    }

    /** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게, 땅 위. 시험 · 사진첩도 씀 */
    public static void setBody(Bosses.Unit u, double x, double z, double yaw) {
        M m = mem(u);
        Body b = m.body;
        Vec3 at = keepIn(u, x, z);
        b.x = at.x;
        b.z = at.z;
        b.yaw = yaw;
        b.h = 0;
        b.want = null;
        m.move = null;
        place(u);
    }

    /** 속성 보내기 (0 ~ 1 또는 -1 ~ 1): step 넘게 바뀌었거나 끝값에 닿았을 때만 — 보낸 값 */
    static Double setProp(Bosses.Unit u, String id, double v, Double last) {
        double r = Js.round(v * 100) / 100;
        if (last != null && (r == last || (Math.abs(r - last) < 0.05 - 1e-9 && Math.abs(r) != 1 && r != 0))) return last;
        if (!ok(u.boss)) return last;
        u.boss.setProp(id, r);
        return r;
    }

    /** 매 틱: 걸음 · 옆걸음을 목표 쪽으로, 속성 보내기, 각성 텍스처 */
    static void bodyTick(Bosses.Unit u) {
        M m = mem(u);
        Body b = m.body;
        b.gait = toward(b.gait, b.gaitTo, 0.15);
        b.strafe = toward(b.strafe, b.strafeTo, 0.2);
        b.gaitSent = setProp(u, "rpg:gait", clamp(b.gait, 0, 1), b.gaitSent);
        b.strafeSent = setProp(u, "rpg:strafe", clamp(b.strafe, -1, 1), b.strafeSent);
        int phase = m.phase2 ? 1 : 0;
        if ((b.phaseSent == null || b.phaseSent != phase) && ok(u.boss)) {
            u.boss.setProp("rpg:phase", phase);
            b.phaseSent = phase;
        }
    }

    /** 패턴 동안: 원하는 쪽 (body.want) 이 있으면 그쪽으로 몸을 돌림 (제자리) */
    static void standTick(Bosses.Unit u) {
        Body b = mem(u).body;
        b.gaitTo = 0;
        b.strafeTo = 0;
        if (b.want != null) b.yaw = turnToward(b.yaw, b.want, D(u).num("turn") * 1.5 * DEG);
        place(u);
    }

    /** 움직임 한 번 (from = 지금 몸, to 는 keep 안으로): time 틱 동안 곧게 (height &gt; 0 이면 포물선) */
    static void startMove(Bosses.Unit u, Vec3 to, long time, double height, String kind) {
        M m = mem(u);
        Vec3 dest = keepIn(u, to.x, to.z);
        m.move = new Move(feetOf(u), dest, Tasks.now(), Math.max(1, time), height, kind);
    }

    static void moveTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Move mv = m.move;
        if (mv == null) return;
        Body b = m.body;
        double k = clamp((double) (now - mv.t0()) / mv.time(), 0, 1);
        b.x = mv.from().x + (mv.to().x - mv.from().x) * k;
        b.z = mv.from().z + (mv.to().z - mv.from().z) * k;
        b.h = mv.height() * 4 * k * (1 - k);
        if (b.want != null) b.yaw = turnToward(b.yaw, b.want, D(u).num("turn") * 2 * DEG);
        if (k >= 1) {
            m.move = null;
            b.h = 0;
        }
        place(u);
    }

    /**
     * 공격 사이 움직임 (틀이 없을 때): 가장 가까운 사람 쪽을 보며 — 멀면 (far 칸 넘게) 달려듦, 너무 붙으면 (near 칸 안) 뒤로 뜀, 그 사이면 orbit 칸 거리를 두고 둘레를 돎 (옆걸음 —
     * 도는 쪽은 flip 틱마다 바뀔 수 있음). 각성 뒤 빠르기 × fast. 가운데에서 keep 칸 밖으로는 나가지 않음
     */
    static void driveTick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        Body b = m.body;
        Obj D = D(u);
        double fast = m.phase2 ? D.num("fast") : 1;
        Vec3 bp = feetOf(u);
        ServerPlayer t = nearest(u, list, 999);
        if (t == null) {
            b.gaitTo = 0;
            b.strafeTo = 0;
            stopDash(u);
            place(u);
            return;
        }
        double d = hdist(t.position(), bp);
        double want = angOf(dirTo(bp, t.position()));
        b.yaw = turnToward(b.yaw, want, D.num("turn") * DEG);
        if (d > D.num("far")) {
            // 달려듦: 대상 쪽으로 (orbit 칸 앞에서 멈춤)
            if (!m.dashing) {
                m.dashing = true;
                u.api.anim(u, "dash", null, MOVE, "0");
                play(u.dim, snd(u, "dash"), bp, 0.8, 0.8);
            }
            double step = Math.min(D.num("dash") * fast, d - D.num("orbit"));
            Vec3 dir = dirTo(bp, t.position());
            Vec3 at = keepIn(u, b.x + dir.x * step, b.z + dir.z * step);
            b.x = at.x;
            b.z = at.z;
            b.gaitTo = 1;
            b.strafeTo = 0;
            if (now % 6 == 0) SkillFx.fx(u.dim, "rpg:dust", new Vec3(b.x, u.center.y + 0.3, b.z), Opt.of("size", 1.0, "life", 0.4, "color", DEEP, "alpha", 0.7));
        } else {
            stopDash(u);
            if (d < D.num("near") && now >= m.backAt) {
                backstep(u, t);
                return;
            }
            if (now >= m.flipAt) {
                if (random() < 0.6) m.orbitDir = -m.orbitDir;
                double[] fl = pair(D, "flip");
                m.flipAt = now + rnd((int) fl[0], (int) fl[1]);
            }
            Vec3 radial = dirTo(t.position(), bp);
            Vec3 tan = new Vec3(-radial.z * m.orbitDir, 0, radial.x * m.orbitDir);
            double corr = clamp((D.num("orbit") - d) * 0.1, -0.1, 0.1);
            double sp = D.num("strafe") * fast;
            Vec3 v = new Vec3(tan.x * sp + radial.x * corr, 0, tan.z * sp + radial.z * corr);
            Vec3 at = keepIn(u, b.x + v.x, b.z + v.z);
            // 담장에 막혀 돌 수 없으면 반대로
            if (Math.hypot(at.x - (b.x + v.x), at.z - (b.z + v.z)) > 0.05) m.orbitDir = -m.orbitDir;
            double moved = Math.hypot(at.x - b.x, at.z - b.z);
            Vec3 f = dirOf(b.yaw);
            Vec3 r = rightOf(b.yaw);
            if (moved > 1e-4) {
                double ux = (at.x - b.x) / moved;
                double uz = (at.z - b.z) / moved;
                double k = clamp(moved / Math.max(1e-6, sp), 0, 1);
                b.strafeTo = clamp((ux * r.x + uz * r.z) * k, -1, 1);
                b.gaitTo = clamp(Math.abs(ux * f.x + uz * f.z) * k, 0, 1);
            } else {
                b.strafeTo = 0;
                b.gaitTo = 0;
            }
            b.x = at.x;
            b.z = at.z;
        }
        place(u);
        if (now >= m.stepAt && (b.gaitTo > 0.2 || Math.abs(b.strafeTo) > 0.2)) {
            m.stepAt = now + (m.dashing ? 6 : 10);
            play(u.dim, snd(u, "step"), bp, m.dashing ? 0.9 : 0.7, 0.6);
        }
    }

    static void stopDash(Bosses.Unit u) {
        M m = mem(u);
        if (!m.dashing) return;
        m.dashing = false;
        u.api.anim(u, "dash_stop", null, MOVE, null);
    }

    /** 뒤로 뜀: 대상 반대쪽으로 back 칸 (backTicks 틱) */
    public static void backstep(Bosses.Unit u, ServerPlayer from) {
        M m = mem(u);
        Obj D = D(u);
        Vec3 bp = feetOf(u);
        Vec3 away = from != null ? dirTo(from.position(), bp) : dirOf(m.body.yaw + Math.PI);
        if (from != null) m.body.want = angOf(dirTo(bp, from.position()));
        startMove(u, new Vec3(bp.x + away.x * D.num("back"), bp.y, bp.z + away.z * D.num("back")), (long) D.num("backTicks"), 0.6, "back");
        m.backAt = Tasks.now() + (long) D.num("backEvery");
        u.api.anim(u, "backstep", null, MOVE, null);
        play(u.dim, "item.trident.riptide_1", bp, 1.3, 0.5);
        SkillFx.fx(u.dim, "rpg:dust", new Vec3(bp.x, bp.y + 0.3, bp.z), Opt.of("size", 1.4, "life", 0.4, "color", DEEP, "alpha", 0.7));
    }

    // ------------------------------------------------------------ 연출

    /** 세로 균열 (열림 · life 초): 가운데 at, 키 size × 2 */
    static void rift(ServerLevel level, Vec3 at, double size, double life) {
        RuneKit.rfx2(level, "rift", at, Opt.of("dir", new Vec3(0, 1, 0), "size", size, "life", life, "grow", 0.5, "alpha", 0.95, "fade", 0.35), PINK, LIGHT);
    }

    /** 칼이 땅을 친 자리: 보라 고리 · 빛나는 금 · 균열 · 알갱이 · 흔들림 */
    static void impact(Bosses.Unit u, Vec3 at, double r) {
        RuneKit.novaRing(u.dim, at, 0.5, r, PINK, LIGHT, Opt.of("life", 0.5));
        RuneKit.glowCrack(u.dim, at, r * 0.9, VIOLET, PINK, Opt.of("life", 1.6, "fade", 0.5));
        SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", r * 1.6, "life", 1.4, "color", DEEP,
                "alpha", 0.9));
        u.dim.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.3, at.z, 30, r * 0.35, 0.2, r * 0.35, 0.06);
        shakeNear(u, at, 14, 0.35, 0.45);
    }

    /** 칼 궤적 (초승달 참격 — spin = 기울기 도: 0 가로 · 90 세로) */
    static void slash(Bosses.Unit u, Vec3 at, Vec3 dir, double size, double spin, boolean flip) {
        SkillFx.arc(u.dim, at, dir, Opt.of("shape", "heavy", "flip", flip, "size", size, "life", 0.3, "speed", 0.0, "spin", spin, "color", VIOLET, "aura", PINK));
    }

    /** 빈틈 시작: open 틱 동안 받는 피해 +bonus — 바닥에 보라 고리 (맥박), 가까운 사람에게 "빈틈!" */
    static void open(Bosses.Unit u, int ticks) {
        M m = mem(u);
        long now = Tasks.now();
        m.openUntil = now + ticks;
        Vec3 f = feetOf(u);
        play(u.dim, snd(u, "open"), f, 0.55, 0.7);
        double bonus = u.def.obj("opening").num("bonus");
        for (ServerPlayer p : fighters(u)) {
            if (hdist(p.position(), f) <= 16) showFaceText(p, "§d§l빈틈! §f지금 공격 §7(받는 피해 +" + Js.str(Js.round(bonus * 100)) + "%)", Opt.of("dist", 2.4, "up", 0.45));
        }
        explain(u, "open", "§d[보스] 빈틈: §f공허 기사는 큰 공격 (내려베기 · 도약 · 회전 · 3연격 · 공허 참) 뒤 잠깐 칼이 박혀 움직이지 못합니다 — 그동안 받는 피해 +"
                + Js.str(Js.round(bonus * 100)) + "% §7(피해 숫자 앞 분홍 \"빈틈\", 바닥의 보라 고리)");
    }

    /** 빈틈인지 (시험) */
    public static boolean opened(Bosses.Unit u, long now) {
        M m = mem(u);
        return m != null && now < m.openUntil;
    }

    // ------------------------------------------------------------ 공허 검기 (각성 뒤)

    /** 검기 예고: 날아갈 줄 (선딜레이 동안 — 공격 모양 끝에서 range 칸) */
    static void waveMark(Bosses.Unit u, Vec3 from, Vec3 dir, int ticks) {
        Obj W = u.def.obj("wave");
        lineMarks(u.dim, from, ahead(from, dir, W.num("range")), W.num("width") * 0.7, ticks, PINK);
    }

    /** p 에서 dir 쪽으로 d 칸 (바닥) */
    static Vec3 ahead(Vec3 p, Vec3 dir, double d) {
        return new Vec3(p.x + dir.x * d, p.y, p.z + dir.z * d);
    }

    /** 검기 하나를 날림 (바닥 from 에서 dir 쪽으로 — 맞는 판정은 waveTick) */
    public static void wave(Bosses.Unit u, Vec3 from, Vec3 dir) {
        M m = mem(u);
        Obj W = u.def.obj("wave");
        Wave w = new Wave(from, dir, Tasks.now(), W.num("speed"), W.num("range"), W.num("width"), W.num("damage"));
        m.waves.add(w);
        double life = w.range / (w.speed * 20);
        SkillFx.arc(u.dim, new Vec3(from.x, from.y + 1.1, from.z), dir, Opt.of("shape", "heavy", "flip", random() < 0.5, "size", 1.9, "life", life, "speed", w.speed * 20,
                "spin", 90.0, "color", VIOLET, "aura", PINK));
        play(u.dim, "entity.breeze.wind_burst", from, 0.6, 0.7);
    }

    static void waveTick(Bosses.Unit u, long now) {
        M m = mem(u);
        if (m.waves.isEmpty()) return;
        List<ServerPlayer> list = targets(u);
        for (Wave w : new ArrayList<>(m.waves)) {
            double dist = (now - w.t0) * w.speed;
            if (dist > w.range + 0.5) {
                m.waves.remove(w);
                continue;
            }
            Vec3 at = new Vec3(w.from.x + w.dir.x * dist, w.from.y, w.from.z + w.dir.z * dist);
            if (now % 2 == 0) u.dim.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.4, at.z, 3, 0.4, 0.2, 0.4, 0.02);
            for (ServerPlayer p : list) {
                if (w.hit.contains(p.getUUID())) continue;
                double rx = p.getX() - w.from.x;
                double rz = p.getZ() - w.from.z;
                double along = rx * w.dir.x + rz * w.dir.z;
                double side = Math.abs(-rx * w.dir.z + rz * w.dir.x);
                if (along < dist - w.speed - 0.6 || along > dist + 0.6 || side > w.width / 2 + 0.3) continue;
                w.hit.add(p.getUUID());
                double[] kk = knockIn(u, p, w.dir.x * 0.8, w.dir.z * 0.8);
                if (bossHit(u, p, w.damage, Opt.of("noSource", true, "knock", knock(kk[0], kk[1], 0.3)))) {
                    showFaceText(p, "§d§l공허 검기에 베였습니다", Opt.of("dist", 2.2, "up", 0.35));
                }
            }
        }
    }

    /** 지금 날아가는 검기 수 (시험) */
    public static int waveCount(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? 0 : m.waves.size();
    }

    // ------------------------------------------------------------ 패턴: 내려베기

    public static boolean cleave(Bosses.Unit u, long now, List<ServerPlayer> list) {
        ServerPlayer t = nearest(u, list, u.def.obj("cleave").num("reach") + 3);
        if (t == null) return false;
        return cleaveAt(u, dirTo(feetOf(u), t.position()));
    }

    /** 내려베기 한 번 (dir = 가로 방향 — 사진첩 · 시험도 씀) */
    public static boolean cleaveAt(Bosses.Unit u, Vec3 dir) {
        Obj d = u.def.obj("cleave");
        M m = mem(u);
        Vec3 apex = feetOf(u);
        int wu = windup(u, d.num("windup"));
        int open = (int) d.num("open");
        double radius = d.num("reach");
        double cone = d.num("cone");
        m.body.want = angOf(dir);
        fanMarks(u.dim, apex, dir, radius, cone, wu, MARK);
        // 각성 뒤 검기는 부채꼴 끝에서 출발 (부채꼴 안의 사람이 두 번 맞지 않게)
        Vec3 waveFrom = ahead(apex, dir, radius - 0.5);
        if (m.phase2) waveMark(u, waveFrom, dir, wu);
        u.api.anim(u, "cleave_ready", null, ATK, "0");
        sound(u, snd(u, "swing"), bodyOf(u), 0.5, 0.8);
        play(u.dim, "block.respawn_anchor.charge", apex, 1.4, 0.5);
        explain(u, "cleave", "§5[보스] 내려베기: §f칼을 머리 위로 들면 앞 부채꼴 (보라) 을 내려칩니다 — 가장 아픈 공격, 부채꼴 밖으로");
        u.api.cast(u, "cleave", wu + open + 12, step(wu, (x, n) -> {
            x.api.anim(x, "cleave", null, ATK, "0");
            hitFan(x, apex, dir, radius, cone, d.num("damage"), 1.0, 0.45);
            Vec3 tip = new Vec3(apex.x + dir.x * radius * 0.6, apex.y, apex.z + dir.z * radius * 0.6);
            slash(x, new Vec3(apex.x + dir.x * radius * 0.45, apex.y + 2.4, apex.z + dir.z * radius * 0.45), dir, radius * 0.7, 90, false);
            impact(x, tip, 2.6);
            rift(x.dim, new Vec3(tip.x, tip.y + 1.2, tip.z), 1.2, 0.7);
            sound(x, snd(x, "heavy"), tip, 0.8, 1);
            sound(x, snd(x, "hit"), tip, 0.7, 0.8);
            if (mem(x).phase2) wave(x, waveFrom, dir);
            open(x, open);
        }), step(wu + open, (x, n) -> x.api.anim(x, "cleave_rec", null, ATK, null)));
        return true;
    }

    /** 부채꼴 안의 사람에게 피해 (보스 쪽에서 오는 공격 — 방패로 막을 수 있음) · 밀기 */
    static void hitFan(Bosses.Unit u, Vec3 apex, Vec3 dir, double radius, double cone, double damage, double push, double lift) {
        for (ServerPlayer p : targets(u)) {
            if (Math.abs(p.getY() - apex.y) > 3.5 || !inFan(p.position(), apex, dir, radius, cone, 0.5)) continue;
            Vec3 away = dirTo(apex, p.position());
            double[] kk = knockIn(u, p, (dir.x + away.x) * 0.5 * push, (dir.z + away.z) * 0.5 * push);
            bossHit(u, p, damage, Opt.of("knock", knock(kk[0], kk[1], lift), "directional", true));
        }
    }

    // ------------------------------------------------------------ 패턴: 돌진 찌르기

    public static boolean thrust(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("thrust");
        Vec3 bp = feetOf(u);
        ServerPlayer t = null;
        double best = Double.POSITIVE_INFINITY;
        for (ServerPlayer p : list) {
            double r = hdist(p.position(), bp);
            if (r >= d.num("min") && r <= d.num("max") && Math.abs(p.getY() - u.center.y) < 4 && r < best) {
                best = r;
                t = p;
            }
        }
        if (t == null) return false;
        return thrustAt(u, dirTo(bp, t.position()), Math.min(d.num("length"), best + 3));
    }

    /** 돌진 찌르기 한 번 (dir 쪽으로 L 칸 — 사진첩 · 시험도 씀) */
    public static boolean thrustAt(Bosses.Unit u, Vec3 dir, double L) {
        Obj d = u.def.obj("thrust");
        Vec3 bp = feetOf(u);
        M m = mem(u);
        Vec3 end = keepIn(u, bp.x + dir.x * L, bp.z + dir.z * L);
        int wu = windup(u, d.num("windup"));
        int time = (int) d.num("time");
        double width = d.num("width");
        m.body.want = angOf(dir);
        lineMarks(u.dim, bp, end, width, wu + time, MARK);
        if (m.phase2) waveMark(u, end, dir, wu + time);
        u.api.anim(u, "thrust_ready", null, ATK, "0");
        play(u.dim, "block.respawn_anchor.charge", bp, 1.6, 0.5);
        explain(u, "thrust", "§5[보스] 돌진 찌르기: §f칼을 뒤로 당기면 보라 줄을 따라 순식간에 찔러 나갑니다 — 줄 밖으로 비키세요");
        Set<UUID> hit = new HashSet<>();
        List<Bosses.Step> steps = new ArrayList<>();
        steps.add(step(wu, (x, n) -> {
            x.api.anim(x, "thrust", null, ATK, "0");
            startMove(x, end, time, 0, "thrust");
            sound(x, snd(x, "dash"), bp, 0.9, 1);
            SkillFx.beam(x.dim, new Vec3(bp.x, bp.y + 1.6, bp.z), new Vec3(end.x, end.y + 1.6, end.z), PINK, Opt.of("half", 0.6, "thick", 0.22, "life", 0.35, "alpha", 0.9,
                    "sweep", time / 20.0));
        }));
        for (int k = 1; k <= time; k++) {
            int kk = k;
            steps.add(step(wu + k, (x, n) -> {
                Vec3 now2 = feetOf(x);
                for (ServerPlayer p : targets(x)) {
                    if (hit.contains(p.getUUID()) || Math.abs(p.getY() - bp.y) > 3.5 || !nearSegment(p.position(), bp, now2, width / 2 + 0.4)) continue;
                    hit.add(p.getUUID());
                    Vec3 side = rightOf(angOf(dir));
                    double s = ((p.getX() - bp.x) * side.x + (p.getZ() - bp.z) * side.z) >= 0 ? 1 : -1;
                    double[] kn = knockIn(x, p, side.x * s * 0.9 + dir.x * 0.4, side.z * s * 0.9 + dir.z * 0.4);
                    bossHit(x, p, d.num("damage"), Opt.of("knock", knock(kn[0], kn[1], 0.35), "directional", true));
                }
                if (kk == time) {
                    SkillFx.burst(x.dim, new Vec3(now2.x + dir.x, now2.y + 1.6, now2.z + dir.z), VIOLET, 2.0, 0.35);
                    sound(x, snd(x, "hit"), now2, 1.0, 0.8);
                    if (mem(x).phase2) wave(x, now2, dir);
                }
            }));
        }
        steps.add(step(wu + time + 6, (x, n) -> x.api.anim(x, "thrust_rec", null, ATK, null)));
        u.api.cast(u, "thrust", wu + time + 14, steps);
        return true;
    }

    // ------------------------------------------------------------ 패턴: 도약 내려찍기

    public static boolean leap(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("leap");
        Vec3 bp = feetOf(u);
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) {
            double r = hdist(p.position(), bp);
            if (r >= d.num("min") && r <= d.num("max") && Math.abs(p.getY() - u.center.y) < 4) cand.add(p);
        }
        if (cand.isEmpty()) return false;
        ServerPlayer t = cand.get((int) Math.floor(random() * cand.size()) % cand.size());
        Vec3 spot = keepIn(u, t.getX(), t.getZ());
        return leapTo(u, spot);
    }

    /** 도약 한 번 (spot = 떨어질 바닥): 원 예고 windup 틱 — 끝 jump 틱 동안 뛰어 내려찍음 → 빈틈 (각성 뒤 바깥 고리가 한 번 더 터짐) */
    public static boolean leapTo(Bosses.Unit u, Vec3 spot) {
        Obj d = u.def.obj("leap");
        M m = mem(u);
        Vec3 bp = feetOf(u);
        int wu = windup(u, d.num("windup"));
        int jump = Math.min(wu - 2, (int) d.num("jump"));
        int open = (int) d.num("open");
        double radius = d.num("radius");
        Obj R = u.def.obj("ring");
        int ringDelay = (int) R.num("delay");
        m.body.want = angOf(dirTo(bp, spot));
        disc(u.dim, spot, radius, wu, MARK);
        u.api.anim(u, "leap_ready", null, ATK, "0");
        play(u.dim, "block.respawn_anchor.charge", bp, 1.2, 0.6);
        explain(u, "leap", "§5[보스] 도약 내려찍기: §f웅크리면 보라 원으로 뛰어올라 내려찍습니다 — 원 밖으로"
                + (m.phase2 ? " §7(각성 뒤: 떨어진 뒤 바깥 고리가 한 번 더 터짐 — 고리 안쪽이 안전)" : ""));
        List<Bosses.Step> steps = new ArrayList<>();
        steps.add(step(wu - jump, (x, n) -> {
            x.api.anim(x, "leap_air", null, ATK, "0");
            startMove(x, spot, jump, d.num("height"), "leap");
            sound(x, snd(x, "dash"), bp, 0.7, 1);
            SkillFx.fx(x.dim, "rpg:dust", new Vec3(bp.x, bp.y + 0.4, bp.z), Opt.of("size", 2.2, "life", 0.6, "color", DEEP, "alpha", 0.8));
        }));
        steps.add(step(wu, (x, n) -> {
            x.api.anim(x, "slam", null, ATK, "0");
            for (ServerPlayer p : targets(x)) {
                if (hdist(p.position(), spot) > radius + 0.3 || Math.abs(p.getY() - spot.y) > 3.5) continue;
                Vec3 away = dirTo(spot, p.position());
                double[] kk = knockIn(x, p, away.x * 0.9, away.z * 0.9);
                bossHit(x, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.5)));
            }
            impact(x, spot, radius);
            SkillFx.shock(x.dim, spot, VIOLET, radius, 0.5);
            rift(x.dim, new Vec3(spot.x, spot.y + 1.4, spot.z), 1.5, 0.8);
            sound(x, snd(x, "heavy"), spot, 0.7, 1);
            if (mem(x).phase2) {
                // 바깥 고리: inner ~ outer 칸 (안쪽은 안전) — ringDelay 틱 뒤 터짐
                disc(x.dim, spot, R.num("outer"), ringDelay, PINK, Opt.of("fill", false, "alpha", 0.95));
                disc(x.dim, spot, R.num("inner"), ringDelay, SAFE, Opt.of("fill", false, "alpha", 0.95));
                SkillFx.fx(x.dim, "rpg:tele_fill", new Vec3(spot.x, spot.y + 0.08, spot.z), Opt.of("dir", Vec3.ZERO, "size", R.num("outer"), "life", ringDelay / 20.0, "color", PINK,
                        "alpha", 0.35, "grow", 1.0));
            }
            open(x, open);
        }));
        if (m.phase2) steps.add(step(wu + ringDelay, (x, n) -> ringHit(x, spot)));
        steps.add(step(wu + open, (x, n) -> x.api.anim(x, "slam_rec", null, ATK, null)));
        u.api.cast(u, "leap", wu + open + 14, steps);
        return true;
    }

    /** 바깥 고리 (각성 뒤 도약): inner ~ outer 칸 안의 사람에게 피해 + 띄움 */
    static void ringHit(Bosses.Unit u, Vec3 spot) {
        Obj R = u.def.obj("ring");
        for (ServerPlayer p : targets(u)) {
            double r = hdist(p.position(), spot);
            if (r < R.num("inner") - 0.2 || r > R.num("outer") + 0.3 || Math.abs(p.getY() - spot.y) > 3) continue;
            Vec3 away = dirTo(spot, p.position());
            double[] kk = knockIn(u, p, away.x * 0.6, away.z * 0.6);
            bossHit(u, p, R.num("damage"), Opt.of("noSource", true, "knock", knock(kk[0], kk[1], 0.6)));
        }
        RuneKit.novaRing(u.dim, spot, R.num("inner"), (R.num("outer") - R.num("inner")) / R.num("inner"), PINK, LIGHT, Opt.of("life", 0.4));
        for (int i = 0; i < 8; i++) {
            double a = i * TAU / 8 + random() * 0.4;
            double r = (R.num("inner") + R.num("outer")) / 2;
            rift(u.dim, new Vec3(spot.x + Math.cos(a) * r, spot.y + 0.9, spot.z + Math.sin(a) * r), 0.8, 0.5);
        }
        play(u.dim, "block.respawn_anchor.deplete", spot, 0.8, 1);
    }

    // ------------------------------------------------------------ 패턴: 회전 베기

    public static boolean spin(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("spin");
        Vec3 bp = feetOf(u);
        double radius = d.num("radius");
        boolean any = false;
        for (ServerPlayer p : list) if (hdist(p.position(), bp) <= radius + 3) any = true;
        return any && spinAt(u);
    }

    /** 회전 베기 한 번 (둘레에 사람이 없어도 — 사진첩 · 시험도 씀) */
    public static boolean spinAt(Bosses.Unit u) {
        Obj d = u.def.obj("spin");
        Vec3 bp = feetOf(u);
        double radius = d.num("radius");
        M m = mem(u);
        int wu = windup(u, d.num("windup"));
        int open = (int) d.num("open");
        disc(u.dim, bp, radius, wu, MARK);
        double a0 = random() * TAU;
        int n = (int) u.def.obj("wave").num("spin");
        if (m.phase2) for (int i = 0; i < n; i++) waveMark(u, ahead(bp, dirOf(a0 + i * TAU / n), radius - 0.5), dirOf(a0 + i * TAU / n), wu);
        m.body.want = null;
        u.api.anim(u, "spin_ready", null, ATK, "0");
        play(u.dim, "block.respawn_anchor.charge", bp, 1.0, 0.6);
        explain(u, "spin", "§5[보스] 회전 베기: §f몸을 비틀어 칼을 옆으로 빼면 몸 둘레 원 (보라) 을 한 바퀴 벱니다 — 원 밖으로 달아나세요"
                + (m.phase2 ? " §7(각성 뒤: 공허 검기가 사방으로 — 분홍 줄 사이로)" : ""));
        u.api.cast(u, "spin", wu + open + 14, step(wu, (x, k) -> {
            x.api.anim(x, "spin", null, ATK, "0");
            for (ServerPlayer p : targets(x)) {
                if (hdist(p.position(), bp) > radius + 0.3 || Math.abs(p.getY() - bp.y) > 3.5) continue;
                Vec3 away = dirTo(bp, p.position());
                double[] kk = knockIn(x, p, away.x * 1.1, away.z * 1.1);
                bossHit(x, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.4), "directional", true));
            }
            for (int i = 0; i < 4; i++) {
                Vec3 dd = dirOf(i * TAU / 4 + random() * 0.3);
                slash(x, new Vec3(bp.x + dd.x * radius * 0.5, bp.y + 1.8, bp.z + dd.z * radius * 0.5), dd, radius * 0.6, 0, i % 2 == 0);
            }
            RuneKit.novaRing(x.dim, bp, 1.0, radius - 1, PINK, LIGHT, Opt.of("life", 0.45));
            SkillFx.shock(x.dim, bp, VIOLET, radius, 0.5);
            sound(x, snd(x, "swing"), bp, 0.6, 1);
            sound(x, snd(x, "hit"), bp, 0.8, 0.8);
            shakeNear(x, bp, 12, 0.25, 0.35);
            if (mem(x).phase2) for (int i = 0; i < n; i++) wave(x, ahead(bp, dirOf(a0 + i * TAU / n), radius - 0.5), dirOf(a0 + i * TAU / n));
            open(x, open);
        }), step(wu + open, (x, k) -> x.api.anim(x, "spin_rec", null, ATK, null)));
        return true;
    }

    // ------------------------------------------------------------ 패턴: 균열 걸음

    /** 대상 등 뒤 dist 칸 (보는 쪽의 반대 — 필드 안으로) */
    static Vec3 behind(Bosses.Unit u, ServerPlayer p, double dist) {
        Vec3 look = Kit.facingOf(p);
        return keepIn(u, p.getX() - look.x * dist, p.getZ() - look.z * dist);
    }

    public static boolean blink(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("blink");
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : list) if (Math.abs(p.getY() - u.center.y) < 4) pool.add(p);
        if (pool.isEmpty()) return false;
        // 가장 가까운 사람이 아닌 사람을 먼저 (여럿이면) — 뒤에 있던 사람도 노림
        ServerPlayer near = nearest(u, pool, 999);
        if (pool.size() > 1) pool.remove(near);
        ServerPlayer t = pool.get((int) Math.floor(random() * pool.size()) % pool.size());
        M m = mem(u);
        int vanish = (int) d.num("vanish");
        int hidden = (int) d.num("hidden");
        int wu = windup(u, d.num("windup"));
        double reach = d.num("reach");
        double cone = d.num("cone");
        Vec3 from = feetOf(u);
        m.body.want = null;
        u.api.anim(u, "vanish", null, ATK, "0");
        rift(u.dim, new Vec3(from.x, from.y + 2.2, from.z), 2.0, (vanish + 6) / 20.0);
        sound(u, snd(u, "vanish"), from, 0.6, 1);
        explain(u, "blink", "§5[보스] 균열 걸음: §f공허 기사가 균열로 사라지면 누군가의 등 뒤에 균열이 열립니다 (보라 원) — 나와서 겨눈 부채꼴을 가로로 벱니다 (맞으면 어둠)");
        Vec3[] spot = new Vec3[1];
        Vec3[] aim = new Vec3[1];
        u.api.cast(u, "blink", vanish + hidden + wu + 16, step(vanish, (x, n) -> {
            M xm = mem(x);
            xm.vanished = true;
            x.immune = true;
            if (ok(x.boss)) x.boss.setInvisible(true);
            spot[0] = behind(x, t, d.num("behind"));
            Vec3 s = spot[0];
            rift(x.dim, new Vec3(s.x, s.y + 2.2, s.z), 2.0, (hidden + wu + 8) / 20.0);
            disc(x.dim, s, reach, hidden + wu, MARK, Opt.of("fill", false, "alpha", 0.9));
            SkillFx.fx(x.dim, "rpg:tele_fill", new Vec3(s.x, s.y + 0.08, s.z), Opt.of("dir", Vec3.ZERO, "size", 1.6, "life", (hidden + wu) / 20.0, "color", PINK, "alpha", 0.6, "grow",
                    1.0));
            x.dim.sendParticles(ParticleTypes.REVERSE_PORTAL, s.x, s.y + 2, s.z, 30, 0.4, 1.2, 0.4, 0.05);
            sound(x, snd(x, "rift"), s, 0.8, 1);
            if (!t.isRemoved()) showFaceText(t, "§d§l등 뒤에 균열! §f돌아서 비키세요", Opt.of("dist", 2.4, "up", 0.45));
        }), step(vanish + hidden, (x, n) -> {
            M xm = mem(x);
            Vec3 s = spot[0];
            Vec3 tp = t.isRemoved() ? feetOf(x) : t.position();
            Vec3 dir = dirTo(s, tp);
            aim[0] = dir;
            xm.body.x = s.x;
            xm.body.z = s.z;
            xm.body.h = 0;
            xm.body.yaw = angOf(dir);
            xm.body.want = angOf(dir);
            place(x);
            xm.vanished = false;
            x.immune = false;
            if (ok(x.boss)) x.boss.setInvisible(false);
            x.api.anim(x, "appear", null, ATK, "0");
            fanMarks(x.dim, s, dir, reach, cone, wu, MARK);
            if (xm.phase2) waveMark(x, ahead(s, dir, reach - 0.5), dir, wu);
            SkillFx.burst(x.dim, new Vec3(s.x, s.y + 2.4, s.z), VIOLET, 2.4, 0.35);
            sound(x, "entity.warden.attack_impact", s, 1.3, 0.7);
        }), step(vanish + hidden + wu, (x, n) -> {
            Vec3 s = spot[0];
            Vec3 dir = aim[0];
            x.api.anim(x, "blink_slash", null, ATK, "0");
            for (ServerPlayer p : targets(x)) {
                if (Math.abs(p.getY() - s.y) > 3.5 || !inFan(p.position(), s, dir, reach, cone, 0.5)) continue;
                double[] kk = knockIn(x, p, dir.x * 0.9, dir.z * 0.9);
                if (bossHit(x, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.35), "directional", true))) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, (int) d.num("dark"), 0, false, true));
                }
            }
            Vec3 at = new Vec3(s.x + dir.x * reach * 0.5, s.y + 2.0, s.z + dir.z * reach * 0.5);
            slash(x, at, dir, reach * 0.65, 45, false);
            slash(x, at, dir, reach * 0.65, -45, true);
            sound(x, snd(x, "swing"), s, 0.8, 1);
            sound(x, snd(x, "hit"), s, 1.1, 0.7);
            if (mem(x).phase2) wave(x, ahead(s, dir, reach - 0.5), dir);
        }), step(vanish + hidden + wu + 8, (x, n) -> x.api.anim(x, "blink_rec", null, ATK, null)));
        return true;
    }

    // ------------------------------------------------------------ 패턴: 3연격

    public static boolean combo(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("combo");
        List<Double> reach = d.numbersOrNull("reach");
        List<Double> cone = d.numbersOrNull("cone");
        List<Double> dmg = d.numbersOrNull("damage");
        List<Double> wus = d.numbersOrNull("windup");
        ServerPlayer t = nearest(u, list, reach.get(0) + 3);
        if (t == null) return false;
        M m = mem(u);
        int w0 = windup(u, wus.get(0));
        int w1 = windup(u, wus.get(1));
        int w2 = windup(u, wus.get(2));
        int open = (int) d.num("open");
        double stepLen = d.num("step");
        Vec3 p0 = feetOf(u);
        Vec3 d0 = dirTo(p0, t.position());
        m.body.want = angOf(d0);
        fanMarks(u.dim, p0, d0, reach.get(0), cone.get(0), w0, MARK);
        u.api.anim(u, "combo1_ready", null, ATK, "0");
        sound(u, snd(u, "swing"), p0, 0.7, 0.6);
        explain(u, "combo", "§5[보스] 3연격: §f한 걸음씩 나아가며 세 번 벱니다 (부채꼴이 차례로) — 셋째 (머리 위에서 내려침) 가 가장 아픔");
        // 걸음마다 다음 자리 · 방향 (휘두르는 순간 정함 — 예고는 그때 뜸)
        Vec3[] pos = {p0, null, null};
        Vec3[] dir = {d0, null, null};
        u.api.cast(u, "combo", w0 + w1 + w2 + open + 14, step(w0, (x, n) -> {
            x.api.anim(x, "combo1", null, ATK, "0");
            hitFan(x, pos[0], dir[0], reach.get(0), cone.get(0), dmg.get(0), 0.6, 0.3);
            slash(x, new Vec3(pos[0].x + dir[0].x * 2.4, pos[0].y + 2.2, pos[0].z + dir[0].z * 2.4), dir[0], 3.2, 35, false);
            sound(x, snd(x, "swing"), pos[0], 0.9, 1);
            double s1 = stepOf(pos[0], t, stepLen);
            pos[1] = keepIn(x, pos[0].x + dir[0].x * s1, pos[0].z + dir[0].z * s1);
            startMove(x, pos[1], 3, 0, "step");
            dir[1] = dirTo(pos[1], t.isRemoved() ? pos[1].add(dir[0]) : t.position());
            mem(x).body.want = angOf(dir[1]);
            fanMarks(x.dim, pos[1], dir[1], reach.get(1), cone.get(1), w1, MARK);
        }), step(w0 + w1, (x, n) -> {
            x.api.anim(x, "combo2", null, ATK, "0");
            hitFan(x, pos[1], dir[1], reach.get(1), cone.get(1), dmg.get(1), 0.6, 0.3);
            slash(x, new Vec3(pos[1].x + dir[1].x * 2.4, pos[1].y + 2.2, pos[1].z + dir[1].z * 2.4), dir[1], 3.2, -35, true);
            sound(x, snd(x, "swing"), pos[1], 1.0, 1);
            double s2 = stepOf(pos[1], t, stepLen);
            pos[2] = keepIn(x, pos[1].x + dir[1].x * s2, pos[1].z + dir[1].z * s2);
            startMove(x, pos[2], 3, 0, "step");
            dir[2] = dirTo(pos[2], t.isRemoved() ? pos[2].add(dir[1]) : t.position());
            mem(x).body.want = angOf(dir[2]);
            x.api.anim(x, "combo3_ready", null, ATK, "0");
            fanMarks(x.dim, pos[2], dir[2], reach.get(2), cone.get(2), w2, MARK);
            if (mem(x).phase2) waveMark(x, ahead(pos[2], dir[2], reach.get(2) - 0.5), dir[2], w2);
            play(x.dim, "block.respawn_anchor.charge", pos[2], 1.4, 0.5);
        }), step(w0 + w1 + w2, (x, n) -> {
            x.api.anim(x, "cleave", null, ATK, "0");
            hitFan(x, pos[2], dir[2], reach.get(2), cone.get(2), dmg.get(2), 1.0, 0.45);
            Vec3 tip = new Vec3(pos[2].x + dir[2].x * reach.get(2) * 0.6, pos[2].y, pos[2].z + dir[2].z * reach.get(2) * 0.6);
            slash(x, new Vec3(pos[2].x + dir[2].x * 2.8, pos[2].y + 2.4, pos[2].z + dir[2].z * 2.8), dir[2], reach.get(2) * 0.7, 90, false);
            impact(x, tip, 2.4);
            sound(x, snd(x, "heavy"), tip, 0.9, 1);
            if (mem(x).phase2) wave(x, ahead(pos[2], dir[2], reach.get(2) - 0.5), dir[2]);
            open(x, open);
        }), step(w0 + w1 + w2 + open, (x, n) -> x.api.anim(x, "cleave_rec", null, ATK, null)));
        return true;
    }

    /** 3연격 한 걸음: max 칸까지 — 대상 1.8칸 앞에서는 멈춤 (몸을 뚫고 지나가지 않게) */
    static double stepOf(Vec3 from, ServerPlayer t, double max) {
        if (t == null || t.isRemoved()) return max;
        return clamp(hdist(from, t.position()) - 1.8, 0, max);
    }

    // ------------------------------------------------------------ 각성 (체력 phase2.at)

    public static void awaken(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj P = u.def.obj("phase2");
        int roar = (int) P.num("roar");
        m.awakenPending = false;
        m.body.want = null;
        u.immune = true;
        stopDash(u);
        Vec3 f = feetOf(u);
        u.api.anim(u, "roar", null, ATK, null);
        sound(u, snd(u, "awaken"), f, 0.7, 1.2);
        sound(u, snd(u, "roar"), f, 0.8, 1);
        title(u, "§5§l공허 각성", "§f공허 기사가 더 빨라집니다 — 베기마다 §d공허 검기");
        say(u, "§5[보스] 공허 각성: §f공허 기사가 더 빨라지고 (선딜레이 짧아짐), 칼을 휘두를 때마다 §d공허 검기§f가 분홍 줄을 따라 날아갑니다 — 줄 옆으로 비키세요. "
                + "§5공허 참§f: 가운데에서 칼을 세우면 빛나는 원 안으로!");
        u.api.cast(u, "awaken", roar + 4, step(10, (x, n) -> {
            Vec3 at = feetOf(x);
            RuneKit.novaRing(x.dim, at, 1.0, 14, PINK, LIGHT, Opt.of("life", 0.8));
            RuneKit.domainSigil(x.dim, at, 9, "blade", VIOLET, PINK, Opt.of("life", 2.2, "spinRate", 40.0, "fade", 0.4));
            for (int i = 0; i < 6; i++) {
                double a = i * TAU / 6;
                rift(x.dim, new Vec3(at.x + Math.cos(a) * 7, at.y + 2, at.z + Math.sin(a) * 7), 1.6, 1.6);
            }
            shakeNear(x, at, 30, 0.5, 1.0);
            x.dim.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 3, at.z, 120, 3, 2, 3, 0.15);
        }), step(roar, (x, n) -> {
            M xm = mem(x);
            xm.phase2 = true;
            x.immune = false;
            x.cdMult = x.def.obj("phase2").num("cd");
            xm.crossAt = Tasks.now() + (long) x.def.obj("cross").num("first");
            bodyTick(x);
        }));
    }

    // ------------------------------------------------------------ 공허 참 (각성 뒤)

    /** 안전 원 자리: 가운데에서 dist 칸, 각도 a0 + 45° + k × 360°/count */
    public static List<Vec3> crossSafe(Bosses.Unit u, double a0) {
        Obj C = u.def.obj("cross");
        int n = (int) C.num("count");
        List<Vec3> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = a0 + Math.PI / n + i * TAU / n;
            out.add(new Vec3(u.center.x + Math.cos(a) * C.num("dist"), u.center.y, u.center.z + Math.sin(a) * C.num("dist")));
        }
        return out;
    }

    public static boolean cross(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj C = u.def.obj("cross");
        M m = mem(u);
        int jump = (int) C.num("jump");
        int wu = (int) C.num("windup");
        int open = (int) C.num("open");
        double a0 = random() * TAU;
        List<Vec3> safe = crossSafe(u, a0);
        m.cross = new Cross(safe, a0);
        m.crossAt = now + every(u, pair(C, "every"));
        m.body.want = null;
        u.immune = true;
        stopDash(u);
        Vec3 c = floorAt(u, u.center.x, u.center.z);
        u.api.anim(u, "leap_ready", null, ATK, "0");
        sound(u, snd(u, "dash"), feetOf(u), 0.6, 1);
        double R = arenaRadius(u);
        double sr = C.num("safe");
        int total = jump + wu;
        // 예고: 필드 전체 (안전 원 밖) 가 보랏빛으로 차오름 · 안전 원 (금빛 테두리 + 빛기둥)
        double gs = 3.0;
        for (double x = -R; x <= R; x += gs) {
            for (double z = -R; z <= R; z += gs) {
                if (Math.hypot(x, z) > R) continue;
                Vec3 p = new Vec3(c.x + x, c.y, c.z + z);
                boolean inSafe = false;
                for (Vec3 s : safe) if (hdist(p, s) <= sr + 0.5) inSafe = true;
                if (inSafe) continue;
                SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(p.x, p.y + 0.07, p.z), Opt.of("dir", Vec3.ZERO, "size", gs * 0.85, "life", total / 20.0, "color", MARK, "alpha",
                        0.38, "grow", 1.0));
            }
        }
        for (Vec3 s : safe) {
            disc(u.dim, s, sr, total, SAFE, Opt.of("fill", false, "alpha", 1.0));
            RuneKit.rfx2(u.dim, "rays", s, Opt.of("size", 5.0, "life", total / 20.0, "alpha", 0.85, "fade", 0.3), SAFE, LIGHT, new int[] {0, 1}, 0.9);
        }
        faceAll(u, "§5§l공허 참! §f빛나는 원 안으로", Opt.of("dist", 2.4, "up", 0.45), list);
        explain(u, "cross", "§5[보스] 공허 참: §f공허 기사가 가운데로 뛰어 칼을 하늘로 세우면 (무적) 3초 뒤 필드 전체를 벱니다 — §e금빛 원 " + safe.size()
                + "곳§f 안만 안전 §7(밖이면 아주 큰 피해)");
        List<Bosses.Step> steps = new ArrayList<>();
        steps.add(step(1, (x, n) -> {
            x.api.anim(x, "leap_air", null, ATK, "0");
            startMove(x, c, jump, 4, "leap");
        }));
        steps.add(step(jump, (x, n) -> {
            x.api.anim(x, "cross_ready", null, ATK, "0");
            SkillFx.shock(x.dim, c, VIOLET, 3, 0.4);
            RuneKit.domainSigil(x.dim, c, R * 0.9, "blade", VIOLET, PINK, Opt.of("life", wu / 20.0 + 0.6, "spinRate", 25.0, "fade", 0.3, "alpha", 0.75));
            sound(x, "block.respawn_anchor.charge", c, 0.6, 1.2);
            mem(x).body.yaw = angOf(dirOf(a0));
        }));
        for (int k = 3; k >= 1; k--) {
            int kk = k;
            steps.add(step(jump + wu - kk * 20, (x, n) -> {
                for (ServerPlayer p : fighters(x)) showFaceText(p, "§5§l공허 참 §f" + kk, Opt.of("dist", 2.4, "up", 0.45));
                play(x.dim, snd(x, "tick"), c, 0.6 + (3 - kk) * 0.2, 1);
            }));
        }
        steps.add(step(jump + wu, (x, n) -> crossHit(x, c, safe, a0)));
        steps.add(step(jump + wu + open, (x, n) -> x.api.anim(x, "cross_rec", null, ATK, null)));
        u.api.cast(u, "cross", jump + wu + open + 16, steps);
        return true;
    }

    /** 공허 참 판정: 안전 원 밖 모두 피해 + 띄움, 십자 참격 · 필드 전체 폭발. 그 뒤 빈틈 */
    static void crossHit(Bosses.Unit u, Vec3 c, List<Vec3> safe, double a0) {
        Obj C = u.def.obj("cross");
        double sr = C.num("safe");
        double R = arenaRadius(u);
        u.immune = false;
        u.api.anim(u, "cross", null, ATK, "0");
        for (ServerPlayer p : targets(u)) {
            boolean in = false;
            for (Vec3 s : safe) if (hdist(p.position(), s) <= sr + 0.3) in = true;
            if (in) {
                showFaceText(p, "§e§l안전!", Opt.of("dist", 2.2, "up", 0.35));
                continue;
            }
            Vec3 away = dirTo(c, p.position());
            double[] kk = knockIn(u, p, away.x * 0.5, away.z * 0.5);
            if (bossHit(u, p, C.num("damage"), Opt.of("noSource", true, "knock", knock(kk[0], kk[1], 0.7)))) {
                showFaceText(p, "§5§l공허 참에 베였습니다", Opt.of("dist", 2.2, "up", 0.35));
            }
        }
        // 십자 참격: 가운데를 지나는 두 줄 (안전 원 사이) + 줄을 따라 균열
        for (int i = 0; i < 2; i++) {
            Vec3 d = dirOf(a0 + i * Math.PI / 2);
            for (double s = -R + 2; s <= R - 2; s += 4) {
                Vec3 p = new Vec3(c.x + d.x * s, c.y, c.z + d.z * s);
                SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(p.x, p.y + 0.05, p.z), Opt.of("dir", Vec3.ZERO, "spin", Math.toDegrees(a0) + i * 90, "size", 4.4, "life", 2.0,
                        "color", DEEP, "alpha", 0.9));
                if (Math.abs(s) > 3 && ((int) s) % 8 == 0) rift(u.dim, new Vec3(p.x, p.y + 1.5, p.z), 1.4, 0.9);
            }
            SkillFx.beam(u.dim, new Vec3(c.x - d.x * R, c.y + 1.2, c.z - d.z * R), new Vec3(c.x + d.x * R, c.y + 1.2, c.z + d.z * R), PINK, Opt.of("half", 1.2, "thick", 0.5,
                    "life", 0.5, "alpha", 0.95, "gap", 1.6));
        }
        RuneKit.novaRing(u.dim, c, 2.0, R / 2, PINK, LIGHT, Opt.of("life", 0.6));
        u.dim.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 200, R * 0.4, 1.0, R * 0.4, 0.1);
        sound(u, snd(u, "cross"), c, 0.8, 1.5);
        sound(u, snd(u, "heavy"), c, 0.6, 1);
        shakeNear(u, c, 40, 0.6, 1.0);
        open(u, (int) C.num("open"));
    }

    // ------------------------------------------------------------ 제한 시간 · 공허 붕괴

    static String clock(long ticks) {
        long s = Math.max(0, (ticks + 19) / 20);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** 남은 틱 (공허 붕괴까지 — 넘겼으면 0) */
    public static long timeLeft(Bosses.Unit u, long now) {
        M m = mem(u);
        return m == null ? 0 : Math.max(0, m.limit - now);
    }

    /** 공허 붕괴까지 막대: 필드 둘레 사람에게, 남은 시간 · 비율 (1분 아래면 붉게) */
    static void timerTick(Bosses.Unit u, long now) {
        M m = mem(u);
        long limit = (long) u.def.obj("enrage").num("limit");
        long left = Math.max(0, m.limit - now);
        if (m.timer == null) {
            m.timer = new ServerBossEvent(UUID.randomUUID(), Component.literal(""), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        ServerBossEvent bar = m.timer;
        String text = left > 0 ? "§5공허 붕괴까지 §f" + clock(left) : "§4§l공허 붕괴";
        if (!text.equals(m.timerText)) {
            m.timerText = text;
            bar.setName(Component.literal(BedrockText.toJava(text)));
        }
        bar.setProgress((float) clamp((double) left / limit, 0, 1));
        bar.setColor(left <= 1200 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
        if (now % 10 != 0) return;
        List<ServerPlayer> here = BossKit.audience(u);
        for (ServerPlayer p : here) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (!here.contains(p) || p.isRemoved()) bar.removePlayer(p);
    }

    /** 공허 붕괴까지 남은 시간 알림 (warn 틱마다 한 번) · 마지막 10초 세기 · 넘기면 붕괴 */
    static void enrageTick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        Obj E = u.def.obj("enrage");
        timerTick(u, now);
        long left = m.limit - now;
        for (Double w : E.numbersOrNull("warn")) {
            long wt = Math.round(w);
            if (left <= wt && left > wt - 20 && m.warned.add(wt)) {
                say(u, "§5[보스] §f공허 붕괴까지 §d" + clock(wt) + "§f — 넘기면 필드 안 모두 즉사합니다");
                play(u.dim, "block.bell.resonate", feetOf(u), 0.6, 1);
            }
        }
        if (left > 0 && left <= 200 && left % 20 == 0) {
            title(u, "§4§l" + (left / 20), "§5공허 붕괴까지");
            play(u.dim, snd(u, "tick"), feetOf(u), 0.5, 1.2);
        }
        if (left <= 0 && now >= m.collapseAt) {
            m.collapseAt = now + (long) E.num("every");
            collapse(u, now);
        }
    }

    /** 공허 붕괴: 틀을 끊고 가운데로 — 하늘이 검게 (안개 · 어둠) → windup 틱 뒤 필드 안 모두 즉사 (즉사 — 보호막 · 불사의 맹약 무시) */
    public static void collapse(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj E = u.def.obj("enrage");
        int wu = (int) E.num("windup");
        u.cast = null;
        m.move = null;
        m.waves.clear();
        m.openUntil = 0;
        stopDash(u);
        if (m.vanished) {
            m.vanished = false;
            if (ok(u.boss)) u.boss.setInvisible(false);
        }
        u.immune = true;
        Vec3 c = floorAt(u, u.center.x, u.center.z);
        setBody(u, c.x, c.z, m.body.yaw);
        u.api.anim(u, "cross_ready", null, ATK, "0");
        title(u, "§4§l공허 붕괴", "§f제한 시간이 지났습니다");
        say(u, "§4[보스] 공허 붕괴: §f10분 안에 쓰러뜨리지 못했습니다 — 공허가 필드를 삼킵니다");
        sound(u, snd(u, "collapse"), c, 0.6, 1.5);
        for (ServerPlayer p : fighters(u)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, wu + 60, 0, false, false));
        RuneKit.rfx(u.dim, "hole", new Vec3(c.x, c.y + 6, c.z), Opt.of("size", 3.0, "life", wu / 20.0 + 0.6, "cell", 0.0, "spinRate", 180.0, "grow", 4.0, "alpha", 0.95));
        RuneKit.rfx(u.dim, "hole", new Vec3(c.x, c.y + 6, c.z), Opt.of("size", 3.0, "life", wu / 20.0 + 0.6, "cell", (double) RuneKit.HOLE_CORE, "grow", 4.0));
        RuneKit.domainSigil(u.dim, c, arenaRadius(u), "blade", DEEP, PINK, Opt.of("life", wu / 20.0 + 1.0, "spinRate", 60.0, "fade", 0.3));
        u.api.cast(u, "collapse", wu + 20, step(wu, (x, n) -> {
            Vec3 at = feetOf(x);
            x.dim.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 2, at.z, 300, 8, 3, 8, 0.4);
            RuneKit.novaRing(x.dim, at, 2.0, 20, PINK, LIGHT, Opt.of("life", 0.8));
            sound(x, snd(x, "cross"), at, 0.5, 1.5);
            for (ServerPlayer p : fighters(x)) Status.kill(p, x.boss);
        }), step(wu + 16, (x, n) -> {
            x.immune = false;
            x.api.anim(x, "cross_rec", null, ATK, null);
        }));
    }

    // ------------------------------------------------------------ 패턴 고르기

    static long every(Bosses.Unit u, double[] range) {
        return (long) Js.round(rnd((int) range[0], (int) range[1]) * u.cdMult);
    }

    /** 패턴 시작 뒤: 그 패턴의 다음 틱 (every × 재사용 배율) · 다음 공격 (틀 끝 + gap) */
    static void after(Bosses.Unit u, String id, long now) {
        M m = mem(u);
        Obj d = u.def.obj(id);
        m.next.put(id, now + (long) Js.round(d.num("every") * u.cdMult));
        double[] gap = pair(m.phase2 ? u.def.obj("phase2") : u.def, "gap");
        long end = u.cast != null ? u.cast.until : now;
        m.nextAttack = end + rnd((int) gap[0], (int) gap[1]);
    }

    /** 보통 패턴 (every 틱마다 — 거리에 맞춰 무게대로 고름) */
    public static final List<String> PATTERNS = List.of("cleave", "thrust", "leap", "spin", "blink", "combo");

    static boolean ready(M m, String id, long now) {
        return now >= m.next.getOrDefault(id, 0L);
    }

    /** forceNext 의 패턴을 바로 (시작했으면 true) */
    static boolean forced(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        boolean r = switch (id) {
            case "cleave" -> cleave(u, now, list);
            case "thrust" -> thrust(u, now, list);
            case "leap" -> leap(u, now, list);
            case "spin" -> spin(u, now, list);
            case "blink" -> blink(u, now, list);
            case "combo" -> combo(u, now, list);
            case "cross" -> cross(u, now, list);
            case "awaken" -> {
                awaken(u, now);
                yield true;
            }
            case "collapse" -> {
                collapse(u, now);
                yield true;
            }
            default -> false;
        };
        if (r && PATTERNS.contains(id)) after(u, id, now);
        return r;
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        if (m == null || m.move != null) return false;
        String f = forceNext;
        if (f != null) {
            forceNext = null;
            return forced(u, f, now, list);
        }
        if (m.awakenPending && !m.phase2) {
            awaken(u, now);
            return true;
        }
        if (m.phase2 && now >= m.crossAt && cross(u, now, list)) return true;
        if (now < m.nextAttack) return false;
        Vec3 bp = feetOf(u);
        ServerPlayer t = nearest(u, list, 999);
        if (t == null) return false;
        double d = hdist(t.position(), bp);
        int close = 0;
        for (ServerPlayer p : list) if (hdist(p.position(), bp) <= u.def.obj("spin").num("radius") + 1.5) close++;
        // 거리에 맞는 패턴 가운데 무게대로 하나 (쓸 수 있는 것만)
        List<String> ids = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        java.util.function.BiConsumer<String, Double> add = (id, w) -> {
            if (ready(m, id, now) && w > 0) {
                ids.add(id);
                ws.add(w);
            }
        };
        if (d <= u.def.obj("cleave").num("reach") + 1) {
            add.accept("cleave", 3.0);
            add.accept("combo", 3.0);
            add.accept("spin", close >= 2 ? 4.0 : 1.5);
        }
        if (d >= u.def.obj("thrust").num("min") && d <= u.def.obj("thrust").num("max")) add.accept("thrust", 3.0);
        if (d >= u.def.obj("leap").num("min")) add.accept("leap", 2.5);
        add.accept("blink", 1.5);
        while (!ids.isEmpty()) {
            double sum = 0;
            for (double w : ws) sum += w;
            double r = random() * sum;
            int i = 0;
            while (i < ids.size() - 1 && r >= ws.get(i)) r -= ws.get(i++);
            String id = ids.get(i);
            if (forcedPattern(u, id, now, list)) {
                after(u, id, now);
                return true;
            }
            ids.remove(i);
            ws.remove(i);
        }
        m.nextAttack = now + 6;
        return false;
    }

    static boolean forcedPattern(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        return switch (id) {
            case "cleave" -> cleave(u, now, list);
            case "thrust" -> thrust(u, now, list);
            case "leap" -> leap(u, now, list);
            case "spin" -> spin(u, now, list);
            case "blink" -> blink(u, now, list);
            case "combo" -> combo(u, now, list);
            default -> false;
        };
    }

    // ------------------------------------------------------------ 모듈

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        Vec3 c = enc.center;
        Vec3 p = enc.starter != null && !enc.starter.isRemoved() ? enc.starter.position() : new Vec3(c.x, c.y, c.z + 8);
        M m = new M();
        m.body.x = c.x;
        m.body.z = c.z;
        m.body.yaw = angOf(dirTo(c, p));
        m.t0 = now;
        m.limit = now + (long) d.obj("enrage").num("limit");
        m.collapseAt = m.limit;
        for (String id : List.of("cleave", "thrust", "leap", "spin", "combo")) m.next.put(id, now + ARRIVE + 20);
        m.next.put("blink", now + (long) d.obj("blink").num("first"));
        m.nextAttack = now + ARRIVE + 10;
        m.flipAt = now + 40;
        enc.mem = m;
        for (Bosses.Unit u : enc.units) u.mem = m;
        // 엔진 평타는 쓰지 않음 (패턴 · 움직임은 모듈이)
        enc.meleeAt = NEVER;
        place(enc);
        bodyTick(enc);
        // 등장: 가운데 균열에서 비집고 나와 칼을 세워 예를 갖춤 (무적)
        enc.immune = true;
        enc.api.anim(enc, "spawn", null, ATK, null);
        rift(enc.dim, new Vec3(c.x, c.y + 2.6, c.z), 2.6, 1.4);
        enc.dim.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 2.5, c.z, 80, 1.2, 1.8, 1.2, 0.8);
        sound(enc, snd(enc, "rift"), c, 0.6, 1.2);
        enc.api.cast(enc, "arrive", ARRIVE, step(8, (x, n) -> {
            SkillFx.burst(x.dim, new Vec3(c.x, c.y + 2.6, c.z), VIOLET, 3.0, 0.5);
            sound(x, snd(x, "vanish"), c, 0.5, 1);
        }), step(ARRIVE - 4, (x, n) -> {
            x.immune = false;
            say(x, "§5[보스] 공허 기사: §f한 방이 매우 아픈 대신 칠 때마다 자세를 잡습니다 — 바닥의 §d보라 예고§f를 보고 피하세요. 큰 공격 뒤에는 §d빈틈§f (받는 피해 +"
                    + Js.str(Js.round(x.def.obj("opening").num("bonus") * 100)) + "%)");
            say(x, "§4[보스] 제한 시간 " + clock((long) x.def.obj("enrage").num("limit")) + " §f— 넘기면 §4공허 붕괴§f로 필드 안 모두 즉사합니다 (위 막대)");
        }));
        timerTick(enc, now);
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        Bosses.Unit u = enc;
        M m = mem(enc);
        if (!u.alive || !ok(u.boss) || m == null) return;
        enrageTick(u, now, list);
        if (!enc.alive || Bosses.encounterAt(enc.a) != enc) return;
        moveTick(u, now);
        waveTick(u, now);
        if (m.move == null) {
            if (u.cast != null || m.vanished) standTick(u);
            else {
                m.body.want = null;
                driveTick(u, now, list != null ? list : targets(u));
            }
        }
        bodyTick(u);
        ambientTick(u, now);
        BossKit.syncBossFog(u, now >= m.limit ? FOG_COLLAPSE : m.phase2 ? FOG_AWAKE : null, now);
    }

    /** 몸 둘레 공허 알갱이 (각성 뒤 더 많이) · 빈틈 동안 발밑 보라 고리 */
    static void ambientTick(Bosses.Unit u, long now) {
        M m = mem(u);
        if (m.vanished) return;
        Vec3 b = bodyOf(u);
        double h = bossSize(u.def).height();
        if (now % 5 == 0) u.dim.sendParticles(ParticleTypes.REVERSE_PORTAL, b.x, b.y + h * 0.55, b.z, m.phase2 ? 6 : 3, 0.6, h * 0.3, 0.6, 0.02);
        if (now < m.openUntil && now % 8 == 0) {
            SkillFx.fx(u.dim, "rpg:tele_disc", new Vec3(b.x, u.center.y + 0.07, b.z), Opt.of("dir", Vec3.ZERO, "size", 2.2, "life", 0.4, "color", PINK, "alpha", 0.9));
        }
        if (m.phase2 && now % 40 == 0) {
            double a = random() * TAU;
            rift(u.dim, new Vec3(b.x + Math.cos(a) * 1.4, u.center.y + 2.6, b.z + Math.sin(a) * 1.4), 0.9, 0.6);
        }
    }

    /** 빈틈 동안 받는 피해 +bonus (분홍 "빈틈") */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        M m = mem(u);
        if (m == null) return null;
        if (Tasks.now() < m.openUntil) return Bosses.Hurt.of(amount * (1 + u.def.obj("opening").num("bonus")), OPEN_MARK);
        return null;
    }

    /** 체력이 phase2.at 아래로 → 다음 pick 에서 각성 */
    @Override
    public void afterHurt(Bosses.Unit u, ServerPlayer p, double amount, double left) {
        M m = mem(u);
        if (m == null || m.phase2 || m.awakenPending) return;
        if (u.api.ratio(u) <= u.def.obj("phase2").num("at")) m.awakenPending = true;
    }

    /** 원거리 견제: 패턴 · 균열 속 · 각성 · 공허 참 · 붕괴 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m != null && u.cast == null && !m.vanished && now < m.limit;
    }

    /** 원거리 견제 (균열 파편) 를 던지는 곳: 칼끝 높이 — 사용자 동작 attack (칼을 들어 내리침) 을 틂 */
    @Override
    public Vec3 farFrom(Bosses.Unit u, Obj style) {
        if (mem(u) == null || !ok(u.boss)) return null;
        u.api.anim(u, "attack", null, "rpg_far", null);
        Vec3 b = bodyOf(u);
        return new Vec3(b.x, b.y + bossSize(u.def).height() * 0.8, b.z);
    }

    @Override
    public void fogCleared(Bosses.Enc enc, ServerPlayer p) {
        if (enc.mem != null && enc.mem.fog != null) enc.mem.fog.remove(p.getUUID());
    }

    /** 쓰러질 때: 땅에 내려놓고 보이게, 무릎 꿇고 균열로 빨려 드는 동작 (die) · 공허 구멍 */
    @Override
    public void dying(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        m.move = null;
        m.body.h = 0;
        m.waves.clear();
        if (ok(enc.boss)) {
            enc.boss.setInvisible(false);
            try {
                place(enc);
            } catch (RuntimeException e) {
                // 무시
            }
            // 다른 틀 (공격 자세 · 달려듦) 을 멈추고 죽음 동작만 (더해져 꺾이지 않게)
            enc.boss.stopAnimation(ATK);
            enc.boss.stopAnimation(MOVE);
            enc.api.anim(enc, "die", null, "die", "0");
        }
        Vec3 at = feetOf(enc);
        RuneKit.rfx(enc.dim, "hole", new Vec3(at.x, at.y + 1.2, at.z), Opt.of("size", 3.2, "life", 1.2, "cell", 0.0, "spinRate", 360.0, "grow", -0.8, "alpha", 0.95));
        RuneKit.rfx(enc.dim, "hole", new Vec3(at.x, at.y + 1.2, at.z), Opt.of("size", 3.2, "life", 1.2, "cell", (double) RuneKit.HOLE_CORE, "grow", -0.8));
        enc.dim.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 2, at.z, 80, 1.4, 2, 1.4, 1.0);
        play(enc.dim, "block.respawn_anchor.deplete", at, 0.8, 1);
    }

    /** 처치 뒤 딜 순위 아래: 걸린 시간 (제한 시간 대비) */
    @Override
    public List<String> killNote(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return null;
        long took = Tasks.now() - m.t0;
        return List.of("§d처치 시간 §f" + clock(took) + " §7/ 제한 " + clock((long) enc.def.obj("enrage").num("limit")));
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        if (m.timer != null) m.timer.removeAllPlayers();
        m.waves.clear();
        m.move = null;
        m.vanished = false;
        if (ok(enc.boss)) enc.boss.setInvisible(false);
        enc.immune = false;
        BossKit.clearBossFog(enc);
    }

    /** 공허 붕괴까지 막대 (시험) */
    public static ServerBossEvent timerOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.timer;
    }

    /** 걸린 시간 · 남은 시간을 시험이 당김 (t0 · limit 을 앞당김 — 붕괴를 미리 보려고) */
    public static void shiftClock(Bosses.Unit u, long ticks) {
        M m = mem(u);
        if (m == null) return;
        m.t0 -= ticks;
        m.limit -= ticks;
        m.collapseAt = m.limit;
    }

    /** 몸 높이 (시험 — 뛰는 중이면 0 보다 큼) */
    public static double heightOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? 0 : m.body.h;
    }

    /** 보스 엔티티 (시험) */
    public static BedrockMob bossOf(Bosses.Unit u) {
        return u.boss;
    }
}
