package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
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
 * 0.35.0 헬 (Lv.58 — 헬헤임의 주인, 수치는 rules_more BOSSES.hel): 선딜레이가 긴 대신 모든 공격이 즉사급 (0.36.0 한 방 1,300 · 체력 2,200,000 — 공허 갑옷 ·
 * 55렙 초월 무기 기준 58렙 방패 · 전투 망치 2방, 그 밖 1방).
 * 사용자 모델 (반은 검고 반은 창백한 망자의 여신 — 몸의 오른쪽 · 오른손이 검은 쪽). 발 없이 떠서 느리게 다가오고, 가끔 안개로 사라졌다가 누군가의 뒤에서 나타남.
 * 헬헤임 (신전 테마 helheim — 얼어붙은 굘 강 · 걀라르 다리) 에서 싸움. 움직이지 않는 보스 (중력 · 충돌 없음) 를 모듈이 매 틱 옮김 (공허 기사와 같은 방식). 엔진 평타는 쓰지 않음.
 * 모든 공격은 막기 없음 (방패를 들고 웅크려도 그대로 — 사용자 "탱커 같은 애들은 2방").
 * 엔티티 속성: rpg:gait (0 ~ 1 떠서 나아감 → stride 동작 무게) · rpg:phase (1 = 각성 — 검은 금이 번진 텍스처).
 * <ul>
 * <li>죽음의 손톱: 검은 오른손을 높이 들어 (windup) → 앞 부채꼴을 내리긁음</li>
 * <li>망자의 손: 싸우는 사람 모두의 발밑에 원 → 땅에서 망자의 손이 솟음 (원 밖으로)</li>
 * <li>삶과 죽음의 경계: 헬을 지나는 선으로 필드를 가름 — 헬의 검은 오른손 쪽이 청록 안개로 차오름 → 그쪽을 침 (방향은 죽음 쪽 사람이 모두 선딜레이 안에 비킬 수 있는 것 중
 * 사람이 가장 많은 쪽)</li>
 * <li>굘 강의 물결: 필드를 가로지르는 넓은 띠 (사람이 가장 많이 걸치는 자리) → 띠를 따라 물결이 쓸고 지나감 (띠 밖으로)</li>
 * <li>헬의 시선: 가장 먼 사람을 검은 눈으로 따라가며 노림 (줄 예고) → 멈춘 뒤 줄을 따라 광선</li>
 * <li>안개 걸음: 안개로 흩어짐 (무적 · 안 보임) → 누군가의 등 뒤에 안개가 모임 (경고 원) → 나타나 그 사람에게 죽음의 손톱</li>
 * <li>각성 「망자의 여왕」 (체력 phase2.at): 울부짖음 (무적) → 선딜레이 × phase2.windup · 재사용 × phase2.cd, 검은 금이 번진 텍스처 · 차가운 안개,
 * 걀라르 다리: 가운데로 떠올라 두 팔을 하늘로 → 필드를 가로지르는 금빛 다리 (폭 bridge.width — 모두가 셈 안에 닿을 수 있는 방향) 만 남기고 필드 전체를 침</li>
 * <li>제한 시간 (enrage.limit 틱 — 보스 막대 아래 "헬헤임의 문까지" 막대): 넘기면 헬헤임의 문 — 필드 안 모두 즉사 (every 틱마다 되풀이)</li>
 * </ul>
 * 0.34.0 방침: 패턴 안내 글자는 BossKit.hint* 로만 (설정 BOSS_HINTS — 기본 숨김), 카운트는 숫자만, 2페이즈 알림 (망자의 여왕) 은 늘 보임.
 * 패턴 함수 · 몸 도우미는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossHel implements BossModule {
    static final String KEY = "hel";
    /** 영혼 빛 (예고 · 연출) */
    public static final double[] SOUL = {0.36, 0.9, 1.0};
    public static final double[] PALE = {0.88, 0.95, 1.0};
    public static final double[] INK = {0.06, 0.05, 0.1};
    public static final double[] BONE = {0.93, 0.9, 0.8};
    /** 예고 (어두운 바닥에서 보이게 밝은 영혼 빛) */
    public static final double[] MARK = {0.45, 0.95, 1.0};
    /** 죽음의 쪽 (삶과 죽음의 경계 · 걀라르 다리 밖): 짙은 청록 */
    public static final double[] DEATH = {0.2, 0.62, 0.86};
    /** 걀라르 다리 (금빛) */
    public static final double[] GOLD = {1.0, 0.84, 0.36};
    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;
    static final long NEVER = Long.MAX_VALUE;

    /** 동작 틀: 공격 자세 (_ready → 침 → _rec) · 원거리 견제 (사용자 attack) */
    public static final String ATK = "rpg_atk";
    public static final String FAR = "rpg_far";
    /** 보스 안개: 각성 (차가운 청록) · 헬헤임의 문 (검게 닫힘) */
    public static final String FOG_QUEEN = "rpg:hel_queen";
    public static final String FOG_GATE = "rpg:hel_gate";
    /** 등장 (안개에서 솟아올라 손짓 — 동작 spawn 1.9초) */
    public static final int ARRIVE = 40;
    /** 치는 동작을 피해보다 이만큼 (틱) 먼저 */
    static final int LEAD = 2;
    /** 가운데 제단 블록 옆: 가운데에 서는 때 (등장 · 걀라르 다리 · 헬헤임의 문) 는 가운데에서 이만큼 비켜 섬 */
    public static final double ALTAR_OFF = 2.0;
    /** 바닥 예고 격자 칸 (반쪽 · 다리 밖) */
    static final double GRID = 3.0;

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (claw · hands · divide · river · gaze · blink · awaken · bridge · gate — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    // ------------------------------------------------------------ 기억

    /** 싸움의 기억: 몸 · 패턴마다 다음 틱 · 다음 공격 틱 · 각성 · 걀라르 다리 · 안개 걸음 · 제한 시간 · 알린 규칙 · 마지막 패턴 모양 (시험) */
    public static final class M extends Bosses.Mem {
        public final Body body = new Body();
        public final Map<String, Long> next = new HashMap<>();
        public long nextAttack;
        public boolean phase2;
        public boolean awakenPending;
        public long bridgeAt = NEVER;
        public long blinkAt = NEVER;
        /** 싸움 시작 틱 · 헬헤임의 문 틱 · 다음 문 (넘긴 뒤 되풀이) */
        public long t0;
        public long limit;
        public long gateAt;
        public final Set<Long> warned = new HashSet<>();
        public ServerBossEvent timer;
        String timerText;
        public Move move;
        public boolean vanished;
        public final Set<String> told = new HashSet<>();
        /** 마지막 경계 (죽음 쪽 방향 — 가운데에서) · 강 (띠 방향 · 가운데에서 옆으로 떨어진 거리) · 시선 (방향) · 다리 (방향) */
        public Vec3 lastDivide;
        public Vec3 lastDivideFrom;
        public double[] lastRiver;
        public Vec3 lastGaze;
        public Vec3 lastBridge;
        public long soulAt;
    }

    /** 몸: 자리 (x, z — 바닥), 몸 방향 yaw (라디안, +x 에서 +z 쪽으로), 패턴 중 돌아볼 쪽 want, 나아감 (지금 · 목표 · 보낸 값) */
    public static final class Body {
        public double x;
        public double z;
        public double yaw;
        public Double want;
        public double gait;
        public double gaitTo;
        Double gaitSent;
        Integer phaseSent;
        double[] placed;
    }

    /** 움직임 (안개 걸음 뒤 등): from → to (바닥), 시작 틱 · 걸리는 틱 */
    public record Move(Vec3 from, Vec3 to, long t0, long time) {}

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

    /** 몸 오른쪽 단위 방향 (몸 방향 yaw 에서 오른쪽 90° — 공허 기사와 같은 식) */
    public static Vec3 rightOf(double yaw) {
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** 오른쪽 (검은 손) 이 n 쪽을 보게 하는 몸 방향 */
    public static double yawRightTo(Vec3 n) {
        return Math.atan2(-n.x, n.z);
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

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번 — 0.34.0 BOSS_HINTS.chat 일 때만 보냄, 알렸다는 표시 told 는 늘 남김) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        BossKit.hintSay(u, text);
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
    public static List<ServerPlayer> targets(Bosses.Unit u) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : fighters(u)) if (Math.abs(p.getY() - u.center.y) < 4) out.add(p);
        return out;
    }

    /** list 중 몸에서 가장 가까운 사람 (도발한 사람이 maxDist 안이면 그 사람 — 없으면 null) */
    static ServerPlayer nearest(Bosses.Unit u, List<ServerPlayer> list, double maxDist) {
        Vec3 b = feetOf(u);
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), b) <= maxDist && Math.abs(p.getY() - u.center.y) < 4) pool.add(p);
        return meleeTarget(u, pool, b);
    }

    /** 맞히기 (모든 공격 — 막기 없음: 방패를 들고 웅크려도 그대로) · 밀기 */
    static boolean hit(Bosses.Unit u, ServerPlayer p, double damage, double kx, double kz, double lift) {
        double[] kk = knockIn(u, p, kx, kz);
        return bossHit(u, p, damage, Opt.of("knock", knock(kk[0], kk[1], lift)));
    }

    // ------------------------------------------------------------ 몸

    /** 몸 (엔티티 자리 = 바닥 — 떠 있는 모습은 동작 hover) */
    public static Vec3 bodyOf(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y, b.z);
    }

    /** 몸 발밑 바닥 */
    public static Vec3 feetOf(Bosses.Unit u) {
        return bodyOf(u);
    }

    /** 머리 (시선 광선이 나오는 곳) */
    public static Vec3 headOf(Bosses.Unit u) {
        Vec3 b = bodyOf(u);
        return new Vec3(b.x, b.y + bossSize(u.def).height() * 0.92, b.z);
    }

    /** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음) */
    static void place(Bosses.Unit u) {
        Body b = mem(u).body;
        double[] k = {b.x, b.z, b.yaw};
        if (b.placed != null && java.util.Arrays.equals(b.placed, k) && ok(u.boss)) return;
        b.placed = k;
        u.api.pose(u, new Vec3(b.x, u.center.y, b.z), b.yaw);
    }

    /** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게. 시험 · 사진첩도 씀 */
    public static void setBody(Bosses.Unit u, double x, double z, double yaw) {
        M m = mem(u);
        Body b = m.body;
        Vec3 at = keepIn(u, x, z);
        b.x = at.x;
        b.z = at.z;
        b.yaw = yaw;
        b.want = null;
        m.move = null;
        place(u);
    }

    /** 속성 보내기 (0 ~ 1): 0.05 넘게 바뀌었거나 끝값에 닿았을 때만 — 보낸 값 */
    static Double setProp(Bosses.Unit u, String id, double v, Double last) {
        double r = Js.round(v * 100) / 100;
        if (last != null && (r == last || (Math.abs(r - last) < 0.05 - 1e-9 && r != 1 && r != 0))) return last;
        if (!ok(u.boss)) return last;
        u.boss.setProp(id, r);
        return r;
    }

    /** 매 틱: 나아감을 목표 쪽으로, 속성 보내기, 각성 텍스처 */
    static void bodyTick(Bosses.Unit u) {
        M m = mem(u);
        Body b = m.body;
        b.gait = toward(b.gait, b.gaitTo, 0.1);
        b.gaitSent = setProp(u, "rpg:gait", clamp(b.gait, 0, 1), b.gaitSent);
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
        if (b.want != null) b.yaw = turnToward(b.yaw, b.want, D(u).num("turn") * 1.5 * DEG);
        place(u);
    }

    /** 움직임 한 번 (from = 지금 몸, to 는 keep 안으로): time 틱 동안 곧게 */
    static void startMove(Bosses.Unit u, Vec3 to, long time) {
        M m = mem(u);
        Vec3 dest = keepIn(u, to.x, to.z);
        m.move = new Move(feetOf(u), dest, Tasks.now(), Math.max(1, time));
    }

    static void moveTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Move mv = m.move;
        if (mv == null) return;
        Body b = m.body;
        double k = clamp((double) (now - mv.t0()) / mv.time(), 0, 1);
        double e = k * k * (3 - 2 * k);
        b.x = mv.from().x + (mv.to().x - mv.from().x) * e;
        b.z = mv.from().z + (mv.to().z - mv.from().z) * e;
        if (b.want != null) b.yaw = turnToward(b.yaw, b.want, D(u).num("turn") * 2 * DEG);
        if (k >= 1) m.move = null;
        place(u);
    }

    /**
     * 공격 사이 움직임 (틀이 없을 때): 가장 가까운 사람 쪽을 보며 떠서 느리게 다가옴 (far 칸 앞에서 멈춤), 너무 붙으면 (near 칸 안) 조금 물러남.
     * 각성 뒤 빠르기 × fast. 가운데에서 keep 칸 밖으로는 나가지 않음
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
            place(u);
            return;
        }
        double d = hdist(t.position(), bp);
        Vec3 dir = dirTo(bp, t.position());
        b.yaw = turnToward(b.yaw, angOf(dir), D.num("turn") * DEG);
        double step = 0;
        if (d > D.num("far")) step = Math.min(D.num("glide") * fast, d - D.num("far"));
        else if (d < D.num("near")) step = -D.num("back") * fast;
        if (step != 0) {
            Vec3 at = keepIn(u, b.x + dir.x * step, b.z + dir.z * step);
            double moved = Math.hypot(at.x - b.x, at.z - b.z);
            b.x = at.x;
            b.z = at.z;
            b.gaitTo = clamp(moved / Math.max(1e-6, D.num("glide")), 0, 1);
        } else b.gaitTo = 0;
        place(u);
    }

    // ------------------------------------------------------------ 연출

    /** 영혼이 솟는 자리: 영혼 · 영혼 불꽃 알갱이 · 차가운 안개 */
    static void souls(ServerLevel level, Vec3 at, double spread, int n) {
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.6, at.z, n, spread, 0.4, spread, 0.04);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.4, at.z, n / 2 + 1, spread, 0.2, spread, 0.02);
        level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y + 1.2, at.z, n * 2, spread * 1.4, 0.8, spread * 1.4, 0.01);
    }

    /** 손톱 자국: 바닥에 나란한 금 셋 (dir 쪽으로) */
    static void clawMarks(Bosses.Unit u, Vec3 apex, Vec3 dir, double reach) {
        Vec3 side = rightOf(angOf(dir));
        for (int i = -1; i <= 1; i++) {
            Vec3 a = new Vec3(apex.x + dir.x * 1.2 + side.x * i * 0.9, apex.y + 0.06, apex.z + dir.z * 1.2 + side.z * i * 0.9);
            Vec3 b = new Vec3(a.x + dir.x * (reach - 1.6), a.y, a.z + dir.z * (reach - 1.6));
            SkillFx.beam(u.dim, a, b, SOUL, Opt.of("half", 0.18, "thick", 0.12, "life", 1.2, "alpha", 0.95, "fade", 0.6));
        }
    }

    /** 허공의 손톱 자국 셋: 몸 오른쪽 (검은 손) 위에서 왼쪽 아래 앞으로 긁어 내림 — 영혼 빛 (가운데 창백한 심) */
    static void clawStrokes(Bosses.Unit u, Vec3 apex, Vec3 dir, double reach) {
        Vec3 side = rightOf(angOf(dir));
        for (int i = -1; i <= 1; i++) {
            double o = i * 0.7;
            Vec3 a = new Vec3(apex.x + dir.x * 1.4 + side.x * (1.6 + o), apex.y + 4.4 + i * 0.2, apex.z + dir.z * 1.4 + side.z * (1.6 + o));
            Vec3 b = new Vec3(apex.x + dir.x * (reach - 1.0) + side.x * (-1.4 + o), apex.y + 0.3, apex.z + dir.z * (reach - 1.0) + side.z * (-1.4 + o));
            SkillFx.beam(u.dim, a, b, SOUL, Opt.of("half", 0.32, "thick", 0.16, "life", 0.45, "alpha", 0.95, "fade", 0.6));
            SkillFx.beam(u.dim, a, b, PALE, Opt.of("half", 0.12, "thick", 0.08, "life", 0.35, "alpha", 1.0, "fade", 0.6));
        }
    }

    /** 땅에서 솟는 망자의 손: 손바닥 (영혼 빛 · 뼈 심지) 이 솟아오름 · 둘레 뼈 가시 */
    static void deadHand(Bosses.Unit u, Vec3 at, double radius) {
        RuneKit.rfx2(u.dim, "fist", new Vec3(at.x, at.y + 0.6, at.z), Opt.of("dir", new Vec3(0, 1, 0), "speed", 2.4, "size", 2.0, "life", 0.6, "grow", 0.3,
                "fade", 0.5), SOUL, BONE, new int[] {2, 3}, null);
        for (int i = 0; i < 6; i++) {
            double a = i * TAU / 6 + random() * 0.4;
            double r = radius * (0.45 + random() * 0.5);
            SkillFx.fx(u.dim, "rpg:ice_spike", new Vec3(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r), Opt.of("size", 1.3 + random() * 0.5, "life", 1.0,
                    "spin", random() * 30 - 15, "color", BONE, "alpha", 1.0));
        }
        souls(u.dim, at, radius * 0.4, 8);
    }

    /** 반쪽 · 다리 밖 같은 넓은 자리의 바닥 예고: 가운데 c 에서 반지름 R 안의 격자 칸 중 keep(칸 가운데) 인 곳 */
    static void areaMarks(Bosses.Unit u, Vec3 c, double R, int ticks, double[] color, java.util.function.BiPredicate<Double, Double> keep) {
        for (double x = -R; x <= R; x += GRID) {
            for (double z = -R; z <= R; z += GRID) {
                if (Math.hypot(x, z) > R || !keep.test(x, z)) continue;
                SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(c.x + x, c.y + 0.07, c.z + z), Opt.of("dir", Vec3.ZERO, "size", GRID * 0.85, "life", ticks / 20.0, "color", color,
                        "alpha", 0.4, "grow", 1.0));
            }
        }
    }

    // ------------------------------------------------------------ 패턴: 죽음의 손톱

    public static boolean claw(Bosses.Unit u, long now, List<ServerPlayer> list) {
        ServerPlayer t = nearest(u, list, u.def.obj("claw").num("reach") + 2);
        if (t == null) return false;
        return clawAt(u, dirTo(feetOf(u), t.position()));
    }

    /** 죽음의 손톱 한 번 (dir = 가로 방향 — 사진첩 · 시험도 씀) */
    public static boolean clawAt(Bosses.Unit u, Vec3 dir) {
        Obj d = u.def.obj("claw");
        M m = mem(u);
        Vec3 apex = feetOf(u);
        int wu = windup(u, d.num("windup"));
        double reach = d.num("reach");
        double cone = d.num("cone");
        m.body.want = angOf(dir);
        fanMarks(u.dim, apex, dir, reach, cone, wu, MARK);
        u.api.anim(u, "claw_ready", null, ATK, "0");
        sound(u, snd(u, "charge"), apex, 1.3, 0.6);
        explain(u, "claw", "§b[보스] 죽음의 손톱: §f헬이 검은 오른손을 높이 들면 앞 부채꼴 (영혼 빛) 을 내리긁습니다 — 부채꼴 밖으로");
        u.api.cast(u, "claw", wu + 18, step(wu - LEAD, (x, n) -> x.api.anim(x, "claw", null, ATK, "0")), step(wu, (x, n) -> {
            for (ServerPlayer p : targets(x)) {
                if (Math.abs(p.getY() - apex.y) > 3.5 || !inFan(p.position(), apex, dir, reach, cone, 0.5)) continue;
                Vec3 away = dirTo(apex, p.position());
                hit(x, p, d.num("damage"), (dir.x + away.x) * 0.4, (dir.z + away.z) * 0.4, 0.35);
            }
            clawStrokes(x, apex, dir, reach);
            clawMarks(x, apex, dir, reach);
            souls(x.dim, new Vec3(apex.x + dir.x * reach * 0.6, apex.y, apex.z + dir.z * reach * 0.6), 1.6, 10);
            sound(x, snd(x, "swing"), apex, 0.6, 1);
            sound(x, snd(x, "hit"), apex, 0.8, 0.9);
            shakeNear(x, apex, 12, 0.3, 0.35);
        }), step(wu + 8, (x, n) -> x.api.anim(x, "claw_rec", null, ATK, null)));
        return true;
    }

    // ------------------------------------------------------------ 패턴: 망자의 손

    public static boolean hands(Bosses.Unit u, long now, List<ServerPlayer> list) {
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : targets(u)) spots.add(new Vec3(p.getX(), u.center.y, p.getZ()));
        if (spots.isEmpty()) return false;
        return handsAt(u, spots);
    }

    /** 망자의 손 한 번 (spots = 원 가운데 — 바닥) */
    public static boolean handsAt(Bosses.Unit u, List<Vec3> spots) {
        Obj d = u.def.obj("hands");
        M m = mem(u);
        int wu = windup(u, d.num("windup"));
        double radius = d.num("radius");
        List<Vec3> at = List.copyOf(spots);
        m.body.want = null;
        for (Vec3 s : at) {
            disc(u.dim, s, radius, wu, MARK);
            u.dim.sendParticles(ParticleTypes.SCULK_SOUL, s.x, s.y + 0.3, s.z, 4, radius * 0.4, 0.1, radius * 0.4, 0.01);
        }
        u.api.anim(u, "hands_ready", null, ATK, "0");
        sound(u, snd(u, "hands"), feetOf(u), 0.7, 1);
        explain(u, "hands", "§b[보스] 망자의 손: §f헬이 손바닥을 땅으로 내리면 모두의 발밑에 원이 뜨고 망자의 손이 솟습니다 — 원 밖으로");
        u.api.cast(u, "hands", wu + 20, step(wu - LEAD, (x, n) -> x.api.anim(x, "hands", null, ATK, "0")), step(wu, (x, n) -> {
            for (ServerPlayer p : targets(x)) {
                boolean in = false;
                Vec3 from = null;
                for (Vec3 s : at) {
                    if (hdist(p.position(), s) <= radius + 0.3 && Math.abs(p.getY() - s.y) < 3) {
                        in = true;
                        from = s;
                    }
                }
                if (!in) continue;
                Vec3 away = dirTo(from, p.position());
                hit(x, p, d.num("damage"), away.x * 0.3, away.z * 0.3, 0.7);
            }
            for (Vec3 s : at) deadHand(x, s, radius);
            sound(x, snd(x, "rise"), feetOf(x), 0.6, 1.2);
            sound(x, snd(x, "soul"), feetOf(x), 0.7, 1);
        }), step(wu + 10, (x, n) -> x.api.anim(x, "hands_rec", null, ATK, null)));
        return true;
    }

    // ------------------------------------------------------------ 패턴: 삶과 죽음의 경계

    /** 경계에서 이만큼 (선딜레이 틱당 칸) 안이면 달려서 비킬 수 있다고 봄 (전력 질주 0.28칸/틱 — 알아채는 시간을 빼고) */
    static final double ESCAPE_PER_TICK = 0.22;

    /**
     * 경계 방향 고르기: 헬을 지나는 선 (16 방향) 중 죽음 쪽에 선 사람이 모두 선딜레이 안에 비킬 수 있는 (가장 먼 사람이 escape 칸 안) 방향에서
     * 죽음 쪽 사람이 가장 많은 쪽 (같으면 아무거나). 그런 방향이 없으면 죽음 쪽 가장 먼 사람이 가장 가까운 쪽
     */
    public static boolean divide(Bosses.Unit u, long now, List<ServerPlayer> list) {
        List<ServerPlayer> ps = targets(u);
        if (ps.isEmpty()) return false;
        Vec3 b = feetOf(u);
        double escape = ESCAPE_PER_TICK * windup(u, u.def.obj("divide").num("windup"));
        int best = -1;
        List<Double> picks = new ArrayList<>();
        double fallback = 0;
        double fallbackFar = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 16; i++) {
            double a = i * TAU / 16;
            Vec3 n = dirOf(a);
            int k = 0;
            double far = 0;
            for (ServerPlayer p : ps) {
                double side = (p.getX() - b.x) * n.x + (p.getZ() - b.z) * n.z;
                if (side < -0.3) continue;
                k++;
                far = Math.max(far, side + 0.3);
            }
            if (k > 0 && far < fallbackFar) {
                fallbackFar = far;
                fallback = a;
            }
            if (far > escape) continue;
            if (k > best) {
                best = k;
                picks.clear();
            }
            if (k == best) picks.add(a);
        }
        double a = best > 0 ? picks.get((int) Math.floor(random() * picks.size()) % picks.size()) : fallback;
        return divideAt(u, a);
    }

    /** 삶과 죽음의 경계 한 번 (a = 죽음 쪽 방향): 헬을 지나며 그 방향에 수직인 선 — 헬의 검은 오른손 쪽 (a 쪽) 반쪽을 침 */
    public static boolean divideAt(Bosses.Unit u, double a) {
        Obj d = u.def.obj("divide");
        M m = mem(u);
        Vec3 c = floorAt(u, u.center.x, u.center.z);
        Vec3 b = feetOf(u);
        Vec3 n = dirOf(a);
        Vec3 along = new Vec3(-n.z, 0, n.x);
        double R = arenaRadius(u);
        int wu = windup(u, d.num("windup"));
        m.lastDivide = n;
        m.lastDivideFrom = b;
        m.body.want = yawRightTo(n);
        // 선이 필드 원과 만나는 두 끝 (헬 자리에서 along 쪽으로 s0 ~ s1)
        double dx = b.x - c.x;
        double dz = b.z - c.z;
        double proj = dx * along.x + dz * along.z;
        double disc = Math.sqrt(Math.max(0, proj * proj - (dx * dx + dz * dz - R * R)));
        double s0 = -proj - disc;
        double s1 = -proj + disc;
        Vec3 e0 = new Vec3(b.x + along.x * s0, c.y, b.z + along.z * s0);
        Vec3 e1 = new Vec3(b.x + along.x * s1, c.y, b.z + along.z * s1);
        // 예고: 죽음 쪽 (헬의 검은 오른손 쪽) 이 청록 안개로 차오름 · 가르는 선 (창백한 빛)
        areaMarks(u, c, R, wu, DEATH, (x, z) -> (c.x + x - b.x) * n.x + (c.z + z - b.z) * n.z > GRID * 0.3);
        lineMarks(u.dim, e0, e1, 0.7, wu, PALE);
        u.api.anim(u, "divide_ready", null, ATK, "0");
        sound(u, snd(u, "charge"), b, 0.8, 0.8);
        explain(u, "divide", "§b[보스] 삶과 죽음의 경계: §f헬이 두 팔을 벌리면 헬을 지나는 선으로 필드가 갈리고 검은 오른손 쪽이 청록 안개로 차오릅니다 — 창백한 왼손 쪽으로");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 10; k < wu - 4; k += 10) {
            steps.add(step(k, (x, t) -> {
                for (int i = 0; i < 6; i++) {
                    double s = s0 + random() * (s1 - s0);
                    double o = random() * R * 0.8 + 1;
                    double px = b.x + along.x * s + n.x * o;
                    double pz = b.z + along.z * s + n.z * o;
                    if (Math.hypot(px - c.x, pz - c.z) > R) continue;
                    x.dim.sendParticles(ParticleTypes.SQUID_INK, px, c.y + 0.4, pz, 2, 0.4, 0.2, 0.4, 0.01);
                }
            }));
        }
        steps.add(step(wu - LEAD, (x, t) -> x.api.anim(x, "divide", null, ATK, "0")));
        steps.add(step(wu, (x, t) -> {
            for (ServerPlayer p : targets(x)) {
                double side = (p.getX() - b.x) * n.x + (p.getZ() - b.z) * n.z;
                if (side < -0.3) continue;
                hit(x, p, d.num("damage"), n.x * 0.5, n.z * 0.5, 0.45);
            }
            for (double s = s0 + 2; s <= s1 - 2; s += 4) {
                for (double o = 3; o <= 2 * R; o += 6) {
                    Vec3 p = new Vec3(b.x + along.x * s + n.x * o, c.y, b.z + along.z * s + n.z * o);
                    if (Math.hypot(p.x - c.x, p.z - c.z) > R - 1) continue;
                    SkillFx.fx(x.dim, "rpg:ground_crack", new Vec3(p.x, p.y + 0.05, p.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", 3.6, "life", 1.4, "color",
                            INK, "alpha", 0.9));
                    souls(x.dim, p, 1.2, 4);
                }
            }
            SkillFx.beam(x.dim, new Vec3(e0.x, c.y + 0.6, e0.z), new Vec3(e1.x, c.y + 0.6, e1.z), PALE, Opt.of("half", 0.5, "thick", 0.3, "life", 0.6, "alpha", 0.95));
            sound(x, snd(x, "beam"), b, 0.6, 1.2);
            sound(x, snd(x, "heavy"), b, 0.7, 1);
            shakeNear(x, b, R * 2, 0.45, 0.6);
        }));
        steps.add(step(wu + 12, (x, t) -> x.api.anim(x, "divide_rec", null, ATK, null)));
        u.api.cast(u, "divide", wu + 20, steps);
        return true;
    }

    /** 죽음 쪽인지 (마지막 경계 — 시험) */
    public static boolean deathSide(Bosses.Unit u, Vec3 p) {
        M m = mem(u);
        if (m == null || m.lastDivide == null || m.lastDivideFrom == null) return false;
        return (p.x - m.lastDivideFrom.x) * m.lastDivide.x + (p.z - m.lastDivideFrom.z) * m.lastDivide.z >= -0.3;
    }

    // ------------------------------------------------------------ 패턴: 굘 강의 물결

    public static boolean river(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("river");
        Vec3 c = u.center;
        List<ServerPlayer> ps = targets(u);
        if (ps.isEmpty()) return false;
        double half = d.num("width") / 2;
        double R = arenaRadius(u);
        int best = -1;
        List<double[]> picks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 12;
            Vec3 nrm = new Vec3(-Math.sin(a), 0, Math.cos(a));
            for (ServerPlayer q : ps) {
                double off = clamp((q.getX() - c.x) * nrm.x + (q.getZ() - c.z) * nrm.z, -(R - half - 1), R - half - 1);
                int k = 0;
                for (ServerPlayer p : ps) if (Math.abs((p.getX() - c.x) * nrm.x + (p.getZ() - c.z) * nrm.z - off) <= half) k++;
                if (k > best) {
                    best = k;
                    picks.clear();
                }
                if (k == best) picks.add(new double[] {a, off});
            }
        }
        double[] pk = picks.get((int) Math.floor(random() * picks.size()) % picks.size());
        return riverAt(u, pk[0], pk[1], random() < 0.5 ? 1 : -1);
    }

    /** 굘 강의 물결 한 번 (a = 띠 방향, off = 가운데에서 옆으로 떨어진 거리, sign = 물결이 흐르는 쪽 ±1) */
    public static boolean riverAt(Bosses.Unit u, double a, double off, int sign) {
        Obj d = u.def.obj("river");
        M m = mem(u);
        Vec3 c = floorAt(u, u.center.x, u.center.z);
        Vec3 t = dirOf(a).scale(sign >= 0 ? 1 : -1);
        Vec3 nrm = new Vec3(-t.z, 0, t.x);
        double R = arenaRadius(u);
        double half = d.num("width") / 2;
        double L = Math.sqrt(Math.max(4, R * R - off * off)) + 2;
        int wu = windup(u, d.num("windup"));
        int flow = (int) d.num("flow");
        Vec3 mid = new Vec3(c.x + nrm.x * off, c.y, c.z + nrm.z * off);
        m.lastRiver = new double[] {a, off, sign};
        m.body.want = angOf(dirTo(feetOf(u), mid));
        // 예고: 띠 (청록 판 격자) · 양쪽 가장자리 줄 (창백한 빛)
        for (double s = -L + 1; s <= L - 1; s += 2.5) {
            for (double o = -half + 1.2; o <= half - 1.1; o += 2.4) {
                SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(mid.x + t.x * s + nrm.x * o, c.y + 0.07, mid.z + t.z * s + nrm.z * o), Opt.of("dir", Vec3.ZERO, "size", 2.3,
                        "life", (wu + flow) / 20.0, "color", MARK, "alpha", 0.42, "grow", 1.0));
            }
        }
        for (int e : new int[] {-1, 1}) {
            Vec3 a0 = new Vec3(mid.x - t.x * L + nrm.x * half * e, c.y, mid.z - t.z * L + nrm.z * half * e);
            Vec3 a1 = new Vec3(mid.x + t.x * L + nrm.x * half * e, c.y, mid.z + t.z * L + nrm.z * half * e);
            lineMarks(u.dim, a0, a1, 0.6, wu + flow, PALE);
        }
        u.api.anim(u, "river_ready", null, ATK, "0");
        sound(u, snd(u, "river"), mid, 0.7, 1.2);
        explain(u, "river", "§b[보스] 굘 강의 물결: §f필드를 가로지르는 넓은 띠 (영혼 빛) 가 뜨고 헬이 손을 쓸면 띠를 따라 망자의 강물이 쓸고 지나갑니다 — 띠 밖으로");
        Set<UUID> got = new HashSet<>();
        List<Bosses.Step> steps = new ArrayList<>();
        // 물이 흐를 쪽 (띠의 시작 끝) 에서 영혼이 맴돎
        for (int k = 8; k < wu - 4; k += 8) {
            steps.add(step(k, (x, n) -> {
                Vec3 s0 = new Vec3(mid.x - t.x * (L - 2), c.y, mid.z - t.z * (L - 2));
                x.dim.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, s0.x, s0.y + 0.5, s0.z, 10, nrm.x * half * 0.6 + 0.3, 0.3, nrm.z * half * 0.6 + 0.3, 0.02);
            }));
        }
        steps.add(step(wu - LEAD, (x, n) -> x.api.anim(x, "river", null, ATK, "0")));
        for (int k = 0; k <= flow; k++) {
            int kk = k;
            steps.add(step(wu + kk, (x, n) -> {
                double front = -L + 2 * L * kk / flow;
                for (ServerPlayer p : targets(x)) {
                    if (got.contains(p.getUUID())) continue;
                    double s = (p.getX() - mid.x) * t.x + (p.getZ() - mid.z) * t.z;
                    double o = (p.getX() - mid.x) * nrm.x + (p.getZ() - mid.z) * nrm.z;
                    if (s > front + 1 || Math.abs(o) > half + 0.3 || Math.abs(p.getY() - c.y) > 3.5) continue;
                    got.add(p.getUUID());
                    hit(x, p, d.num("damage"), t.x * 0.9, t.z * 0.9, 0.4);
                }
                if (kk == 0) {
                    // 물결: 띠 폭으로 3칸마다 한 조각, 흐르는 쪽으로 날아감
                    double life = (double) flow / 20;
                    double speed = 2 * L / life;
                    for (double o = -half + 0.5; o <= half - 0.4; o += 2.0) {
                        Vec3 at = new Vec3(mid.x - t.x * L + nrm.x * o, c.y + 1.5, mid.z - t.z * L + nrm.z * o);
                        SkillFx.fx(x.dim, "rpg:tide_wave", at, Opt.of("dir", t, "speed", speed, "size", 2.8, "thick", 2.2, "life", life, "color", SOUL, "alpha", 1.0));
                        SkillFx.fx(x.dim, "rpg:tide_wave", new Vec3(at.x, at.y + 1.0, at.z), Opt.of("dir", t, "speed", speed, "size", 1.7, "thick", 1.2, "life", life,
                                "color", PALE, "alpha", 0.95));
                    }
                    sound(x, snd(x, "wave"), mid, 0.6, 1.4);
                }
                if (kk % 2 == 0) {
                    // 물결 앞머리: 띠 폭으로 물보라 셋 · 영혼 · 물방울 (지나가는 자리가 보이게)
                    Vec3 at = new Vec3(mid.x + t.x * front, c.y, mid.z + t.z * front);
                    for (int e = -1; e <= 1; e++) {
                        Vec3 f = new Vec3(at.x + nrm.x * e * (half - 1.2), c.y + 1.0, at.z + nrm.z * e * (half - 1.2));
                        SkillFx.fx(x.dim, "rpg:splash", f, Opt.of("size", 1.7, "life", 0.45, "color", PALE, "alpha", 0.9));
                    }
                    souls(x.dim, at, half * 0.5, 6);
                    x.dim.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.6, at.z, 20, half * 0.5, 0.3, half * 0.5, 0.2);
                }
            }));
        }
        steps.add(step(wu + flow + 4, (x, n) -> x.api.anim(x, "river_rec", null, ATK, null)));
        u.api.cast(u, "river", wu + flow + 14, steps);
        return true;
    }

    // ------------------------------------------------------------ 패턴: 헬의 시선

    public static boolean gaze(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("gaze");
        Vec3 bp = feetOf(u);
        ServerPlayer t = null;
        double best = -1;
        for (ServerPlayer p : targets(u)) {
            double r = hdist(p.position(), bp);
            if (r <= d.num("length") - 1 && r > best) {
                best = r;
                t = p;
            }
        }
        if (t == null) return false;
        return gazeAt(u, t);
    }

    /** 헬의 시선 한 번: target 을 따라가며 노림 (windup − lock 틱) → 멈춤 (lock 틱) → 줄을 따라 광선 */
    public static boolean gazeAt(Bosses.Unit u, ServerPlayer target) {
        Obj d = u.def.obj("gaze");
        M m = mem(u);
        int wu = windup(u, d.num("windup"));
        int lock = Math.min(wu - 4, (int) d.num("lock"));
        double len = d.num("length");
        double width = d.num("width");
        Vec3 from = feetOf(u);
        Vec3[] aim = {dirTo(from, target.position())};
        m.lastGaze = aim[0];
        m.body.want = angOf(aim[0]);
        u.api.anim(u, "gaze_ready", null, ATK, "0");
        sound(u, snd(u, "gaze"), from, 0.8, 1);
        explain(u, "gaze", "§b[보스] 헬의 시선: §f헬이 몸을 숙이고 검은 눈으로 가장 먼 사람을 따라가며 노립니다 (줄) — 줄이 멈추면 곧 광선, 옆으로 비키세요");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 0; k < wu - lock; k += 2) {
            int kk = k;
            steps.add(step(kk, (x, n) -> {
                if (!target.isRemoved()) aim[0] = dirTo(from, target.position());
                mem(x).body.want = angOf(aim[0]);
                lineMarks(x.dim, from, new Vec3(from.x + aim[0].x * len, from.y, from.z + aim[0].z * len), width * 0.6, 3, MARK);
            }));
        }
        steps.add(step(wu - lock, (x, n) -> {
            mem(x).lastGaze = aim[0];
            lineMarks(x.dim, from, new Vec3(from.x + aim[0].x * len, from.y, from.z + aim[0].z * len), width, lock, PALE);
            play(x.dim, "block.beacon.activate", from, 1.6, 0.6);
        }));
        steps.add(step(wu - LEAD, (x, n) -> x.api.anim(x, "gaze", null, ATK, "0")));
        steps.add(step(wu, (x, n) -> {
            Vec3 dir = aim[0];
            Vec3 end = new Vec3(from.x + dir.x * len, from.y, from.z + dir.z * len);
            for (ServerPlayer p : targets(x)) {
                if (Math.abs(p.getY() - from.y) > 3.5 || !nearSegment(p.position(), from, end, width / 2 + 0.3)) continue;
                Vec3 side = rightOf(angOf(dir));
                double s = ((p.getX() - from.x) * side.x + (p.getZ() - from.z) * side.z) >= 0 ? 1 : -1;
                hit(x, p, d.num("damage"), side.x * s * 0.6 + dir.x * 0.3, side.z * s * 0.6 + dir.z * 0.3, 0.3);
            }
            Vec3 head = headOf(x);
            SkillFx.beam(x.dim, head, new Vec3(end.x, end.y + 1.0, end.z), SOUL, Opt.of("half", 0.9, "thick", 0.5, "life", 0.5, "alpha", 0.95));
            SkillFx.beam(x.dim, new Vec3(from.x, from.y + 0.15, from.z), new Vec3(end.x, end.y + 0.15, end.z), PALE, Opt.of("half", 0.35, "thick", 0.2, "life", 1.0,
                    "alpha", 0.9, "fade", 0.5));
            for (double s = 2; s <= len; s += 3) souls(x.dim, new Vec3(from.x + dir.x * s, from.y, from.z + dir.z * s), 0.5, 3);
            sound(x, snd(x, "beam"), from, 0.9, 1.2);
            shakeNear(x, end, 10, 0.3, 0.3);
        }));
        steps.add(step(wu + 10, (x, n) -> x.api.anim(x, "gaze_rec", null, ATK, null)));
        u.api.cast(u, "gaze", wu + 18, steps);
        return true;
    }

    // ------------------------------------------------------------ 안개 걸음 (→ 죽음의 손톱)

    /** 대상 등 뒤 dist 칸 (보는 쪽의 반대 — 필드 안으로) */
    static Vec3 behind(Bosses.Unit u, ServerPlayer p, double dist) {
        Vec3 look = Kit.facingOf(p);
        return keepIn(u, p.getX() - look.x * dist, p.getZ() - look.z * dist);
    }

    public static boolean blink(Bosses.Unit u, long now, List<ServerPlayer> list) {
        List<ServerPlayer> pool = new ArrayList<>(targets(u));
        if (pool.isEmpty()) return false;
        // 가장 가까운 사람이 아닌 사람을 먼저 (여럿이면) — 뒤에 있던 사람도 노림
        ServerPlayer near = nearest(u, pool, 999);
        if (pool.size() > 1) pool.remove(near);
        return blinkTo(u, pool.get((int) Math.floor(random() * pool.size()) % pool.size()));
    }

    /** 안개 걸음 한 번 (target 의 등 뒤로 — 나타나서 죽음의 손톱) */
    public static boolean blinkTo(Bosses.Unit u, ServerPlayer t) {
        Obj d = u.def.obj("blink");
        M m = mem(u);
        int vanish = (int) d.num("vanish");
        int hidden = (int) d.num("hidden");
        Vec3 from = feetOf(u);
        m.body.want = null;
        m.blinkAt = Tasks.now() + every(u, pair(d, "every"));
        u.api.anim(u, "vanish", null, ATK, "0");
        souls(u.dim, from, 1.0, 14);
        sound(u, snd(u, "vanish"), from, 0.7, 1);
        explain(u, "blink", "§b[보스] 안개 걸음: §f헬이 안개로 흩어지면 누군가의 등 뒤에 안개가 모입니다 (원) — 나타나자마자 죽음의 손톱");
        Vec3[] spot = new Vec3[1];
        u.api.cast(u, "blink", vanish + hidden + 12, step(vanish, (x, n) -> {
            M xm = mem(x);
            xm.vanished = true;
            x.immune = true;
            if (ok(x.boss)) x.boss.setInvisible(true);
            spot[0] = behind(x, t, d.num("behind"));
            Vec3 s = spot[0];
            disc(x.dim, s, 1.8, hidden, MARK, Opt.of("fill", true, "alpha", 0.9));
            souls(x.dim, s, 0.8, 10);
            x.dim.sendParticles(ParticleTypes.CLOUD, s.x, s.y + 1.2, s.z, 20, 0.6, 1.0, 0.6, 0.02);
        }), step(vanish + hidden, (x, n) -> {
            M xm = mem(x);
            Vec3 s = spot[0];
            Vec3 tp = t.isRemoved() ? s.add(dirOf(xm.body.yaw)) : t.position();
            Vec3 dir = dirTo(s, tp);
            xm.body.x = s.x;
            xm.body.z = s.z;
            xm.body.yaw = angOf(dir);
            xm.body.want = angOf(dir);
            place(x);
            xm.vanished = false;
            x.immune = false;
            if (ok(x.boss)) x.boss.setInvisible(false);
            x.api.anim(x, "appear", null, ATK, "0");
            souls(x.dim, s, 1.0, 16);
            sound(x, snd(x, "vanish"), s, 1.1, 1);
        }), step(vanish + hidden + 6, (x, n) -> {
            Vec3 s = feetOf(x);
            Vec3 tp = t.isRemoved() ? s.add(dirOf(mem(x).body.yaw)) : t.position();
            clawAt(x, dirTo(s, tp));
        }));
        return true;
    }

    // ------------------------------------------------------------ 각성 「망자의 여왕」 (체력 phase2.at)

    public static void awaken(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj P = u.def.obj("phase2");
        int roar = (int) P.num("roar");
        m.awakenPending = false;
        m.body.want = null;
        u.immune = true;
        Vec3 f = feetOf(u);
        u.api.anim(u, "roar", null, ATK, null);
        sound(u, snd(u, "roar"), f, 0.7, 1.4);
        // 2페이즈 알림은 늘 보임 (사용자 "공허 각성 같은 2페이지는 냅두고")
        // 패턴 안내 (걀라르 다리를 깨는 법) 는 넣지 않음 — 0.34.0 방침: 2페이즈 이름 · 모습만 늘 보이고 깨는 법은 BOSS_HINTS 로만 (bridgeAt 의 explain)
        title(u, "§3§l망자의 여왕", "§f헬의 검은 반쪽이 온몸으로 번집니다");
        say(u, "§3[보스] 망자의 여왕: §f헬의 검은 반쪽이 온몸으로 번집니다 — 헬이 더 빨라집니다");
        u.api.cast(u, "awaken", roar + 4, step(10, (x, n) -> {
            Vec3 at = feetOf(x);
            RuneKit.novaRing(x.dim, at, 1.0, 16, SOUL, PALE, Opt.of("life", 0.9));
            RuneKit.domainSigil(x.dim, at, 10, "spirit", DEATH, SOUL, Opt.of("life", 2.2, "spinRate", 30.0, "fade", 0.4));
            for (int i = 0; i < 10; i++) {
                double a = i * TAU / 10;
                souls(x.dim, new Vec3(at.x + Math.cos(a) * 8, at.y, at.z + Math.sin(a) * 8), 0.8, 8);
            }
            for (ServerPlayer p : fighters(x)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
            shakeNear(x, at, 34, 0.5, 1.2);
        }), step(roar, (x, n) -> {
            M xm = mem(x);
            xm.phase2 = true;
            x.immune = false;
            x.cdMult = x.def.obj("phase2").num("cd");
            xm.bridgeAt = Tasks.now() + (long) x.def.obj("bridge").num("first");
            bodyTick(x);
        }));
    }

    // ------------------------------------------------------------ 걀라르 다리 (각성 뒤)

    /**
     * 다리 방향 고르기 (12 방향): 모두가 셈 (jump + windup 틱) 안에 다리에 닿을 수 있는 (가장 먼 사람이 escape 칸 안) 방향 중 아무거나,
     * 그런 방향이 없으면 가장 먼 사람이 가장 가까운 방향
     */
    public static boolean bridge(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj B = u.def.obj("bridge");
        Vec3 c = u.center;
        double half = B.num("width") / 2;
        double escape = ESCAPE_PER_TICK * (B.num("jump") + B.num("windup"));
        List<Double> ok = new ArrayList<>();
        double best = 0;
        double bestFar = Double.POSITIVE_INFINITY;
        double turn = random() * Math.PI / 12;
        for (int i = 0; i < 12; i++) {
            double a = turn + i * Math.PI / 12;
            Vec3 t = dirOf(a);
            double far = 0;
            for (ServerPlayer p : targets(u)) far = Math.max(far, Math.abs((p.getX() - c.x) * -t.z + (p.getZ() - c.z) * t.x) - half);
            if (far <= escape) ok.add(a);
            if (far < bestFar) {
                bestFar = far;
                best = a;
            }
        }
        return bridgeAt(u, ok.isEmpty() ? best : ok.get((int) Math.floor(random() * ok.size()) % ok.size()));
    }

    /** 걀라르 다리 한 번 (a = 다리 방향 — 가운데를 지남): 가운데 옆으로 떠 가서 두 팔을 하늘로 → 다리 밖 필드 전체를 침 */
    public static boolean bridgeAt(Bosses.Unit u, double a) {
        Obj B = u.def.obj("bridge");
        M m = mem(u);
        int jump = (int) B.num("jump");
        int wu = (int) B.num("windup");
        double half = B.num("width") / 2;
        double R = arenaRadius(u);
        Vec3 c = floorAt(u, u.center.x, u.center.z);
        Vec3 t = dirOf(a);
        Vec3 nrm = new Vec3(-t.z, 0, t.x);
        m.lastBridge = t;
        m.bridgeAt = Tasks.now() + every(u, pair(B, "every"));
        m.body.want = angOf(t);
        // 서는 자리: 다리 위 · 가운데에서 ALTAR_OFF 칸 (제단 블록 옆)
        Vec3 stand = new Vec3(c.x - t.x * ALTAR_OFF, c.y, c.z - t.z * ALTAR_OFF);
        startMove(u, stand, jump);
        u.api.anim(u, "bridge_ready", null, ATK, "0");
        sound(u, snd(u, "bridge"), feetOf(u), 0.6, 1.4);
        int total = jump + wu;
        // 예고: 다리 밖 필드 전체가 청록 안개로 차오름 · 다리 (금빛 판 + 양쪽 가장자리 빛기둥)
        areaMarks(u, c, R, total, DEATH, (x, z) -> Math.abs(x * nrm.x + z * nrm.z) > half + GRID * 0.35);
        lineMarks(u.dim, new Vec3(c.x - t.x * R, c.y, c.z - t.z * R), new Vec3(c.x + t.x * R, c.y, c.z + t.z * R), half * 2, total, GOLD);
        for (int e : new int[] {-1, 1}) {
            for (double s = -R + 2; s <= R - 2; s += 4) {
                Vec3 p = new Vec3(c.x + t.x * s + nrm.x * half * e, c.y, c.z + t.z * s + nrm.z * half * e);
                RuneKit.rfx2(u.dim, "rays", p, Opt.of("size", 1.3, "life", total / 20.0, "alpha", 0.85, "fade", 0.3), GOLD, PALE, new int[] {0, 1}, 0.9);
            }
        }
        explain(u, "bridge", "§e[보스] 걀라르 다리: §f헬이 가운데에서 두 팔을 하늘로 들면 필드를 가로지르는 금빛 다리만 남고 나머지가 모두 망자의 안개에 잠깁니다 — 다리 위로");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 3; k >= 1; k--) {
            int kk = k;
            // 카운트 (숫자만 — 0.34.0)
            steps.add(step(total - kk * 20, (x, n) -> {
                for (ServerPlayer p : fighters(x)) showFaceText(p, "§e§l" + kk, Opt.of("dist", 2.4, "up", 0.45));
                play(x.dim, snd(x, "tick"), c, 0.6 + (3 - kk) * 0.2, 1);
            }));
        }
        steps.add(step(total - LEAD, (x, n) -> x.api.anim(x, "bridge", null, ATK, "0")));
        steps.add(step(total, (x, n) -> bridgeHit(x, c, t, half)));
        steps.add(step(total + 16, (x, n) -> x.api.anim(x, "bridge_rec", null, ATK, null)));
        u.api.cast(u, "bridge", total + 26, steps);
        return true;
    }

    /** 걀라르 다리 판정: 다리 밖 모두 피해 (안쪽은 무사) · 필드 전체 영혼 폭발 */
    static void bridgeHit(Bosses.Unit u, Vec3 c, Vec3 t, double half) {
        Obj B = u.def.obj("bridge");
        double R = arenaRadius(u);
        Vec3 nrm = new Vec3(-t.z, 0, t.x);
        for (ServerPlayer p : targets(u)) {
            double o = (p.getX() - c.x) * nrm.x + (p.getZ() - c.z) * nrm.z;
            if (Math.abs(o) <= half + 0.3) continue;
            double s = o >= 0 ? 1 : -1;
            hit(u, p, B.num("damage"), nrm.x * s * 0.5, nrm.z * s * 0.5, 0.6);
        }
        for (double x = -R + 2; x <= R - 2; x += 5) {
            for (double z = -R + 2; z <= R - 2; z += 5) {
                if (Math.hypot(x, z) > R - 1 || Math.abs(x * nrm.x + z * nrm.z) <= half + 1) continue;
                souls(u.dim, new Vec3(c.x + x, c.y, c.z + z), 1.4, 3);
            }
        }
        RuneKit.novaRing(u.dim, c, 2.0, R / 2, SOUL, PALE, Opt.of("life", 0.7));
        SkillFx.beam(u.dim, new Vec3(c.x - t.x * R, c.y + 0.5, c.z - t.z * R), new Vec3(c.x + t.x * R, c.y + 0.5, c.z + t.z * R), GOLD, Opt.of("half", half, "thick",
                0.4, "life", 0.8, "alpha", 0.9));
        sound(u, snd(u, "beam"), c, 0.6, 1.6);
        sound(u, snd(u, "heavy"), c, 0.6, 1.2);
        shakeNear(u, c, R + 10, 0.6, 1.0);
    }

    // ------------------------------------------------------------ 제한 시간 · 헬헤임의 문

    static String clock(long ticks) {
        long s = Math.max(0, (ticks + 19) / 20);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** 남은 틱 (헬헤임의 문까지 — 넘겼으면 0) */
    public static long timeLeft(Bosses.Unit u, long now) {
        M m = mem(u);
        return m == null ? 0 : Math.max(0, m.limit - now);
    }

    /** 헬헤임의 문까지 막대: 필드 둘레 사람에게, 남은 시간 · 비율 (1분 아래면 붉게) — 체력 막대 아래 */
    static void timerTick(Bosses.Unit u, long now) {
        M m = mem(u);
        long limit = (long) u.def.obj("enrage").num("limit");
        long left = Math.max(0, m.limit - now);
        if (m.timer == null) {
            m.timer = new ServerBossEvent(UUID.randomUUID(), Component.literal(""), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        ServerBossEvent bar = m.timer;
        String text = left > 0 ? "§3헬헤임의 문까지 §f" + clock(left) : "§4§l헬헤임의 문";
        if (!text.equals(m.timerText)) {
            m.timerText = text;
            bar.setName(Component.literal(BedrockText.toJava(text)));
        }
        bar.setProgress((float) clamp((double) left / limit, 0, 1));
        bar.setColor(left <= 1200 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.BLUE);
        if (now % 10 != 0) return;
        List<ServerPlayer> here = BossKit.audience(u);
        // 보스 체력 막대 아래에 오게: 체력 막대를 아직 못 받은 사람에게는 체력 막대를 먼저 보냄 (공허 기사와 같음)
        ServerBossEvent hp = ok(u.boss) ? u.boss.bossBar() : null;
        for (ServerPlayer p : here) {
            if (bar.getPlayers().contains(p)) continue;
            if (hp != null && !hp.getPlayers().contains(p)) hp.addPlayer(p);
            bar.addPlayer(p);
        }
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (!here.contains(p) || p.isRemoved()) bar.removePlayer(p);
    }

    /** 헬헤임의 문까지 남은 시간 알림 (안내 — 숨김) · 마지막 10초 숫자 · 넘기면 문 */
    static void enrageTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj E = u.def.obj("enrage");
        timerTick(u, now);
        long left = m.limit - now;
        for (Double w : E.numbersOrNull("warn")) {
            long wt = Math.round(w);
            if (left <= wt && left > wt - 20 && m.warned.add(wt)) {
                BossKit.hintSay(u, "§3[보스] §f헬헤임의 문까지 §b" + clock(wt) + "§f — 열리면 필드 안 모두 즉사합니다");
                play(u.dim, "block.bell.resonate", feetOf(u), 0.5, 1);
            }
        }
        if (left > 0 && left <= 200 && left % 20 == 0) {
            // 숫자마다 1초 남짓 (기본 3.5초면 마지막 "1" 이 헬헤임의 문 위에 남음)
            for (ServerPlayer p : BossKit.audience(u)) if (!p.isRemoved()) mmorpg.fabric.Notify.title(p, "§4§l" + (left / 20), "", 0, 18, 4);
            play(u.dim, snd(u, "tick"), feetOf(u), 0.5, 1.2);
        }
        if (left <= 0 && now >= m.gateAt) {
            m.gateAt = now + (long) E.num("every");
            gate(u, now);
        }
    }

    /** 헬헤임의 문: 틀을 끊고 가운데로 — 두 팔을 벌려 문을 엶 (안개 · 어둠) → windup 틱 뒤 필드 안 모두 즉사 (즉사 — 보호막 · 불사의 맹약 무시) */
    public static void gate(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj E = u.def.obj("enrage");
        int wu = (int) E.num("windup");
        u.cast = null;
        m.move = null;
        if (m.vanished) {
            m.vanished = false;
            if (ok(u.boss)) u.boss.setInvisible(false);
        }
        u.immune = true;
        Vec3 c = floorAt(u, u.center.x, u.center.z);
        double yaw = m.body.yaw;
        setBody(u, c.x - Math.cos(yaw) * ALTAR_OFF, c.z - Math.sin(yaw) * ALTAR_OFF, yaw);
        u.api.anim(u, "gate", null, ATK, "0");
        BossKit.hintTitle(u, "§4§l헬헤임의 문", "§f제한 시간이 지났습니다");
        BossKit.hintSay(u, "§4[보스] 헬헤임의 문: §f10분 안에 쓰러뜨리지 못했습니다 — 망자의 문이 열려 필드를 삼킵니다");
        sound(u, snd(u, "gate"), c, 0.6, 1.5);
        for (ServerPlayer p : fighters(u)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, wu + 60, 0, false, false));
        RuneKit.domainSigil(u.dim, c, arenaRadius(u), "spirit", INK, SOUL, Opt.of("life", wu / 20.0 + 1.0, "spinRate", 60.0, "fade", 0.3));
        RuneKit.rfx(u.dim, "hole", new Vec3(c.x, c.y + 6, c.z), Opt.of("size", 3.0, "life", wu / 20.0 + 0.6, "cell", 0.0, "spinRate", 120.0, "grow", 4.0, "alpha", 0.95,
                "color", DEATH));
        u.api.cast(u, "gate", wu + 20, step(wu, (x, n) -> {
            Vec3 at = feetOf(x);
            souls(x.dim, at, 10, 60);
            RuneKit.novaRing(x.dim, at, 2.0, 22, SOUL, PALE, Opt.of("life", 0.8));
            sound(x, snd(x, "beam"), at, 0.5, 1.5);
            for (ServerPlayer p : fighters(x)) Status.kill(p, x.boss);
        }), step(wu + 16, (x, n) -> {
            x.immune = false;
            x.api.anim(x, "gate_rec", null, ATK, null);
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

    /** 보통 패턴 (every 틱마다 — 무게대로 고름) */
    public static final List<String> PATTERNS = List.of("claw", "hands", "divide", "river", "gaze");

    static boolean ready(M m, String id, long now) {
        return now >= m.next.getOrDefault(id, 0L);
    }

    /** forceNext 의 패턴을 바로 (시작했으면 true) */
    static boolean forced(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        boolean r = switch (id) {
            case "claw" -> claw(u, now, list);
            case "hands" -> hands(u, now, list);
            case "divide" -> divide(u, now, list);
            case "river" -> river(u, now, list);
            case "gaze" -> gaze(u, now, list);
            case "blink" -> blink(u, now, list);
            case "bridge" -> bridge(u, now, list);
            case "awaken" -> {
                awaken(u, now);
                yield true;
            }
            case "gate" -> {
                gate(u, now);
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
        if (m.phase2 && now >= m.bridgeAt && bridge(u, now, list)) {
            double[] gap = pair(u.def.obj("phase2"), "gap");
            m.nextAttack = (u.cast != null ? u.cast.until : now) + rnd((int) gap[0], (int) gap[1]);
            return true;
        }
        if (now < m.nextAttack) return false;
        if (now >= m.blinkAt && blink(u, now, list)) {
            double[] gap = pair(m.phase2 ? u.def.obj("phase2") : u.def, "gap");
            m.nextAttack = (u.cast != null ? u.cast.until : now) + rnd((int) gap[0], (int) gap[1]);
            return true;
        }
        Vec3 bp = feetOf(u);
        ServerPlayer t = nearest(u, list, 999);
        if (t == null) return false;
        double d = hdist(t.position(), bp);
        List<String> ids = new ArrayList<>();
        List<Double> ws = new ArrayList<>();
        java.util.function.BiConsumer<String, Double> add = (id, w) -> {
            if (ready(m, id, now) && w > 0) {
                ids.add(id);
                ws.add(w);
            }
        };
        if (d <= u.def.obj("claw").num("reach") + 1) add.accept("claw", 4.0);
        add.accept("hands", 2.0);
        add.accept("divide", 1.6);
        add.accept("river", 2.0);
        add.accept("gaze", 2.0);
        while (!ids.isEmpty()) {
            double sum = 0;
            for (double w : ws) sum += w;
            double r = random() * sum;
            int i = 0;
            while (i < ids.size() - 1 && r >= ws.get(i)) r -= ws.get(i++);
            String id = ids.get(i);
            if (start(u, id, now, list)) {
                after(u, id, now);
                return true;
            }
            ids.remove(i);
            ws.remove(i);
        }
        m.nextAttack = now + 6;
        return false;
    }

    static boolean start(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        return switch (id) {
            case "claw" -> claw(u, now, list);
            case "hands" -> hands(u, now, list);
            case "divide" -> divide(u, now, list);
            case "river" -> river(u, now, list);
            case "gaze" -> gaze(u, now, list);
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
        // 제단 앞 (부른 사람 쪽으로 ALTAR_OFF 칸) 에서 솟아 부른 사람을 봄. 부른 사람이 제단 곁에 서 있으면 제단 건너편에서
        Vec3 toward = dirTo(c, p);
        double side = hdist(p, c) < ALTAR_OFF + 1.5 ? -1 : 1;
        m.body.x = c.x + toward.x * ALTAR_OFF * side;
        m.body.z = c.z + toward.z * ALTAR_OFF * side;
        m.body.yaw = angOf(dirTo(new Vec3(m.body.x, c.y, m.body.z), p));
        Vec3 at = new Vec3(m.body.x, c.y, m.body.z);
        m.t0 = now;
        m.limit = now + (long) d.obj("enrage").num("limit");
        m.gateAt = m.limit;
        m.next.put("claw", now + ARRIVE + 20);
        for (String id : List.of("hands", "divide", "river", "gaze")) m.next.put(id, now + (long) d.obj(id).num("first"));
        m.blinkAt = now + (long) d.obj("blink").num("first");
        m.nextAttack = now + ARRIVE + 10;
        enc.mem = m;
        for (Bosses.Unit u : enc.units) u.mem = m;
        // 엔진 평타는 쓰지 않음 (패턴 · 움직임은 모듈이)
        enc.meleeAt = NEVER;
        place(enc);
        bodyTick(enc);
        // 등장: 제단 앞 안개에서 솟아올라 부른 사람에게 손짓 (무적)
        enc.immune = true;
        enc.api.anim(enc, "spawn", null, ATK, null);
        souls(enc.dim, at, 1.4, 30);
        enc.dim.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 40, 1.6, 0.3, 1.6, 0.02);
        sound(enc, snd(enc, "summon"), at, 0.7, 1.2);
        enc.api.cast(enc, "arrive", ARRIVE, step(10, (x, n) -> {
            RuneKit.novaRing(x.dim, at, 1.0, 6, SOUL, PALE, Opt.of("life", 0.7));
            sound(x, snd(x, "soul"), at, 0.6, 1.2);
        }), step(ARRIVE - 4, (x, n) -> {
            x.immune = false;
            BossKit.hintSay(x, "§3[보스] 헬: §f모든 공격이 매우 아픈 대신 선딜레이가 깁니다 — 바닥의 §b영혼 빛 예고§f를 보고 피하세요 (방패로 막을 수 없음)");
            BossKit.hintSay(x, "§4[보스] 제한 시간 " + clock((long) x.def.obj("enrage").num("limit")) + " §f— 넘기면 §4헬헤임의 문§f이 열려 필드 안 모두 즉사합니다 (위 막대)");
        }));
        timerTick(enc, now);
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        Bosses.Unit u = enc;
        M m = mem(enc);
        if (!u.alive || !ok(u.boss) || m == null) return;
        enrageTick(u, now);
        if (!enc.alive || Bosses.encounterAt(enc.a) != enc) return;
        moveTick(u, now);
        if (m.move == null) {
            if (u.cast != null || m.vanished) standTick(u);
            else {
                m.body.want = null;
                driveTick(u, now, list != null ? list : targets(u));
            }
        }
        bodyTick(u);
        ambientTick(u, now);
        BossKit.syncBossFog(u, now >= m.limit ? FOG_GATE : m.phase2 ? FOG_QUEEN : null, now);
    }

    /** 몸 둘레 영혼 · 발밑 안개 (각성 뒤 더 많이 · 검은 알갱이) */
    static void ambientTick(Bosses.Unit u, long now) {
        M m = mem(u);
        if (m.vanished) return;
        Vec3 b = bodyOf(u);
        double h = bossSize(u.def).height();
        if (now % 5 == 0) {
            u.dim.sendParticles(ParticleTypes.SCULK_SOUL, b.x, b.y + h * 0.5, b.z, m.phase2 ? 3 : 1, 0.6, h * 0.3, 0.6, 0.01);
            u.dim.sendParticles(ParticleTypes.WHITE_ASH, b.x, b.y + 0.4, b.z, 6, 1.0, 0.2, 1.0, 0.01);
        }
        if (m.phase2 && now % 3 == 0) u.dim.sendParticles(ParticleTypes.SQUID_INK, b.x, b.y + h * 0.6, b.z, 1, 0.5, h * 0.25, 0.5, 0.01);
        if (now >= m.soulAt) {
            m.soulAt = now + 60 + (long) (random() * 60);
            play(u.dim, snd(u, "soul"), b, 0.6 + random() * 0.2, 0.6);
        }
    }

    /** 받는 피해는 그대로 (빈틈 없음) */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        return null;
    }

    /** 체력이 phase2.at 아래로 → 다음 pick 에서 각성 */
    @Override
    public void afterHurt(Bosses.Unit u, ServerPlayer p, double amount, double left) {
        M m = mem(u);
        if (m == null || m.phase2 || m.awakenPending) return;
        if (u.api.ratio(u) <= u.def.obj("phase2").num("at")) m.awakenPending = true;
    }

    /** 원거리 견제: 패턴 · 안개 속 · 각성 · 다리 · 문 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m != null && u.cast == null && !m.vanished && now < m.limit;
    }

    /** 원거리 견제 (망자의 창) 를 던지는 곳: 오른손 높이 — 사용자 동작 attack (팔을 들어 내리침) 을 틂 */
    @Override
    public Vec3 farFrom(Bosses.Unit u, Obj style) {
        if (mem(u) == null || !ok(u.boss)) return null;
        u.api.anim(u, "attack", null, FAR, null);
        Vec3 b = bodyOf(u);
        Vec3 r = rightOf(mem(u).body.yaw);
        return new Vec3(b.x + r.x * 0.8, b.y + bossSize(u.def).height() * 0.75, b.z + r.z * 0.8);
    }

    @Override
    public void fogCleared(Bosses.Enc enc, ServerPlayer p) {
        if (enc.mem != null && enc.mem.fog != null) enc.mem.fog.remove(p.getUUID());
    }

    /** 쓰러질 때: 보이게, 두 팔을 들고 안개 속으로 가라앉음 (die) · 영혼이 흩어짐 */
    @Override
    public void dying(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        m.move = null;
        if (ok(enc.boss)) {
            enc.boss.setInvisible(false);
            try {
                place(enc);
            } catch (RuntimeException e) {
                // 무시
            }
            enc.boss.stopAnimation(ATK);
            enc.boss.stopAnimation(FAR);
            enc.api.anim(enc, "die", null, "die", "0");
        }
        Vec3 at = feetOf(enc);
        souls(enc.dim, at, 2.0, 50);
        enc.dim.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1, at.z, 60, 1.6, 1.2, 1.6, 0.04);
        RuneKit.novaRing(enc.dim, at, 1.0, 8, SOUL, PALE, Opt.of("life", 0.8));
        play(enc.dim, snd(enc, "death"), at, 0.5, 1.2);
    }

    /** 처치 뒤 딜 순위 아래: 걸린 시간 (제한 시간 대비) */
    @Override
    public List<String> killNote(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return null;
        long took = Tasks.now() - m.t0;
        return List.of("§b처치 시간 §f" + clock(took) + " §7/ 제한 " + clock((long) enc.def.obj("enrage").num("limit")));
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        if (m.timer != null) m.timer.removeAllPlayers();
        m.move = null;
        m.vanished = false;
        if (ok(enc.boss)) enc.boss.setInvisible(false);
        enc.immune = false;
        BossKit.clearBossFog(enc);
    }

    /** 헬헤임의 문까지 막대 (시험) */
    public static ServerBossEvent timerOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.timer;
    }

    /** 걸린 시간 · 남은 시간을 시험이 당김 (t0 · limit 을 앞당김 — 문을 미리 보려고) */
    public static void shiftClock(Bosses.Unit u, long ticks) {
        M m = mem(u);
        if (m == null) return;
        m.t0 -= ticks;
        m.limit -= ticks;
        m.gateAt = m.limit;
    }

    /** 보스 엔티티 (시험) */
    public static BedrockMob bossOf(Bosses.Unit u) {
        return u.boss;
    }
}
