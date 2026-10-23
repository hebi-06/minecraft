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
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rgb;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.shuffle;
import static mmorpg.fabric.boss.BossKit.snd;
import static mmorpg.fabric.boss.BossKit.sound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 요르문간드 (boss_jormungandr.js — v2.2.0, Lv.32, 세계를 휘감은 뱀): 수치는 config BOSSES.jormungandr.
 * 뱀의 섬 (가운데 섬 + 독의 해자 + 둑) 의 해자를 헤엄쳐 돎. 엔티티 (맞는 곳) = 머리, 모듈이 매 틱 옮김 (엔진 pose — 설정 swim).
 * 몸의 움직임: 목뿌리 (머리에서 목 길이 swim.neck 뒤, 모델의 몸 뿌리) 가 해자 가운데 원을 따라가고 머리는 목뿌리 앞.
 * 패턴 동안에는 목뿌리가 그대로 있고 목만 돌려 대상 쪽을 봄 (몸통 · 꼬리가 해자에 남음: 필드 담장을 뚫고 나가지 않게).
 * 엔티티 속성: rpg:tail = 꼬리가 목뿌리에서 나가는 방향 (머리 바로 뒤 기준, 도), rpg:curve = 꼬리 마디마다 굽는 각도 (원을 따라), rpg:sink = 물에 잠김 (0~1).
 * <ul>
 * <li>물어뜯기 (평타): 목이 닿는 사람 쪽으로 목을 돌려 부채꼴 예고 → 피해 (목뿌리에서 앞으로)</li>
 * <li>독니 돌진: 섬 위의 한 명 쪽으로 목을 돌려 길을 예고 → 반대편 해자까지 돌진 (머리가 지나가는 자리: 피해 + 침묵 + 옆으로 밀림),
 * 꼬리는 돌진하며 길 뒤로 따라 들어오고, 끝나면 다시 해자로 휘어 들어가 돎</li>
 * <li>꼬리 휘감기: 목뿌리 뒤 꼬리 쪽 (해자 ~ 섬 가장자리) 을 예고 → 피해 + 속박. 그 자리에 아무도 없으면 하지 않음</li>
 * <li>독해 범람 (체력 85% 아래): 흰 고리 밖이 독에 잠김 (피해 + 독, 잠시 남음) → 끝나면 고리 안에 모인 곳을 가로질러 독니 돌진</li>
 * <li>아홉 걸음의 독 (체력 70% 아래): 표식이 붙은 사람이 스스로 아홉 칸을 걸으면 즉사 (보스에게 맞아 밀린 것은 셈하지 않음).
 * 표식이 없는 동료가 곁 (초록 원) 에 서 있으면 독을 빼 줌 (도운 사람은 조금 다침). 시간이 다 되면 사라짐</li>
 * <li>세계를 감는 고리 (체력 50% · 20%): 몸이 필드 둘레에 떠올라 조여 옴 → 머리 앞 틈 (초록 길) 으로만 빠져나감.
 * 틈 밖에서 몸에 닿거나 몸을 넘어가면 (순간이동 스킬로 건너뛰어도) 즉사, 끝까지 고리 안에 남으면 즉사.
 * 고리가 시작될 때 싸우던 사람은 틈을 지나지 않고 고리 밖에 있으면 (성문으로 나가는 등) 즉사. 머리는 틈 가장자리를 따라 들어옴 (때릴 수 있음)</li>
 * <li>독의 해자: 해자 물에 들어가 있으면 1초마다 마법 피해. 높은 곳 (성문 · 탑 위): 꼬리로 쳐서 떨어뜨림</li>
 * </ul>
 * 큰 패턴 (고리 · 아홉 걸음 · 범람) 은 서로 겹치지 않음 (mem.bigUntil). 엔진 평타는 쓰지 않음 (목을 돌리는 물어뜯기를 모듈이).
 * <p>
 * 자바에서 달라진 점: 쓰러짐 (애드온 hpState.awaitRespawn) = isDeadOrDying (자바는 불사의 토템이 피해 안에서 바로 막음), 나갔다 들어온 사람 = 같은 UUID 의 새 ServerPlayer,
 * 틀 줄이기 (u.cast.until = …) 는 같은 이름의 짧은 틀로 (자바 Cast.until 은 final), 고리 소품은 처음부터 틈 쪽을 보고 나옴 (소환 뒤 돌리지 않음).
 * 패턴 함수는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossJormungandr implements BossModule {
    static final String KEY = "jormungandr";
    static final double[] VENOM = {0.46, 0.95, 0.32};
    static final double[] TEAL = {0.3, 0.86, 0.62};
    static final double[] PURPLE = {0.72, 0.36, 0.98};
    static final double[] ORANGE = {1.0, 0.5, 0.14};
    static final double[] RED = {1.0, 0.22, 0.14};
    static final double[] SAFE = {0.86, 1.0, 0.9};
    static final double[] DUST = {0.6, 0.62, 0.56};
    static final double[] FOAM = {0.7, 0.95, 0.8};
    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;

    /** 목을 돌릴 수 있는 가장 큰 각도 (몸 방향에서, 라디안) */
    public static final double NECK_MAX = 110 * DEG;
    /** 꼬리가 새 방향으로 휘어 들어가는 빠르기 (한 틱에, 라디안) */
    public static final double TAIL_TURN = 8 * DEG;
    /** rpg:curve 속성 범위 (도, build.py 와 같음) */
    public static final double CURVE_MAX = 45;
    /** rpg:tail 속성 범위 (도, build.py 와 같음) */
    public static final double TAIL_MAX = 170;
    /** 동작 틀 (고리 동작은 떠오르기가 같은 틀에서 바꿀 때까지 유지) */
    static final String COIL = "rpg_coil";
    /** 세계를 감는 고리 (몸통 모형 소품) */
    public static final String RING_ID = "rpg:prop_jorm_ring";

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (bite · dash · tail · flood · venom · ring — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> propIds() {
        return List.of(RING_ID);
    }

    // ------------------------------------------------------------ 기억

    /**
     * 몸 (애드온 mem.swim): 목뿌리의 해자 원 각도 ang · 도는 쪽 dir (±1) · 목뿌리 자리 (px, pz) · 몸 방향 byaw · 목 각도 nyaw (몸에서) ·
     * 꼬리 방향 tail (→ tailTo) · 굽힘 curve (→ curveTo) · 잠김 sink (→ sinkTo) · 원을 따라 도는지 · 돌진 중인지 · 보낸 속성 값
     */
    public static final class Swim {
        public double ang;
        public int dir = 1;
        public double px;
        public double pz;
        public double byaw;
        public double nyaw;
        public double tail;
        public double tailTo;
        public double curve;
        public double curveTo;
        public double sink = 1;
        public double sinkTo = 1;
        public boolean onCircle = true;
        public boolean charging;
        Double tailSent;
        Double curveSent;
        Double sinkSent;
    }

    /** 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 큰 패턴이 끝나는 틱 · 알린 규칙 · 몸 · 범람 · 범람 뒤 돌진 · 아홉 걸음 · 고리 · 고리를 쓴 체력 · 높은 곳 */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new HashMap<>();
        public long bigUntil;
        public final Set<String> told = new HashSet<>();
        public final Swim swim = new Swim();
        public Flood flood;
        public boolean floodDash;
        public Venom venom;
        public Ring ring;
        public final Set<Double> ringDone = new HashSet<>();
        public final Map<UUID, Long> high = new HashMap<>();
    }

    /** 독해 범람: 그 마리 · 시작 · 물결 · 끝 틱 */
    public record Flood(Bosses.Unit u, long t0, long waveAt, long endAt) {}

    /** 아홉 걸음의 독 표식: 그 사람 · 쓴 걸음 · 지난 자리 · 셈하지 않는 틱 · 독 빼기 (틱 수) · 끝 틱 · 보인 걸음 · 끝났는지 */
    public static final class Mark {
        public final ServerPlayer p;
        public double used;
        public Vec3 last;
        public long calm;
        public int help;
        public final long until;
        public long shown = -1;
        public boolean done;

        Mark(ServerPlayer p, Vec3 last, long calm, long until) {
            this.p = p;
            this.last = last;
            this.calm = calm;
            this.until = until;
        }
    }

    /** 아홉 걸음의 독: 그 마리 · 표식들 · 시작 틱 */
    public record Venom(Bosses.Unit u, List<Mark> marks, long t0) {}

    /** 고리 명단 한 사람: 지난번에 본 자리가 몸 안쪽인지 · 그 자리 (ax, az) · 틈으로 빠져나갔는지 */
    public static final class Entry {
        public ServerPlayer p;
        public boolean in;
        public double ax;
        public double az;
        public boolean passed;

        Entry(ServerPlayer p, boolean in, double ax, double az) {
            this.p = p;
            this.in = in;
            this.ax = ax;
            this.az = az;
        }
    }

    /**
     * 세계를 감는 고리: 그 마리 · 쓴 체력 at · 틈 방향 g · 틈 반각 half · 머리 각도 head · 도는 쪽 dir · 머리의 처음 거리 r0 · 시작 · 조이기 시작 틱 ·
     * 지금 반지름 R · 소품 · 명단 · 짓눌린 틱
     */
    public static final class Ring {
        public final Bosses.Unit u;
        public final double at;
        public final double g;
        public final double half;
        public final double head;
        public final int dir;
        public final double r0;
        public final long t0;
        public final long startAt;
        public double R;
        public final BedrockMob prop;
        public final Map<UUID, Entry> roster;
        public final Map<UUID, Long> crushed = new HashMap<>();

        Ring(Bosses.Unit u, double at, double g, double half, double head, int dir, double r0, long t0, long startAt, double R, BedrockMob prop,
                Map<UUID, Entry> roster) {
            this.u = u;
            this.at = at;
            this.g = g;
            this.half = half;
            this.head = head;
            this.dir = dir;
            this.r0 = r0;
            this.t0 = t0;
            this.startAt = startAt;
            this.R = R;
            this.prop = prop;
            this.roster = roster;
        }
    }

    /** 돌진 길: 시작 · 방향 · 길이 */
    public record Lane(Vec3 from, Vec3 dir, double L) {}

    /** 꼬리 쪽 자리: 목뿌리 각도 base · 도는 쪽 · 뒤로 a0 ~ a1 (라디안) · 가운데에서 r0 ~ r1 칸 */
    public record Zone(double base, int dir, double a0, double a1, double r0, double r1) {}

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

    /** 각도 → 0 ~ 2π */
    static double wrap(double a) {
        return ((a % TAU) + TAU) % TAU;
    }

    /** 각도 차이 → -π ~ π */
    static double wrapPi(double a) {
        double w = wrap(a);
        return w > Math.PI ? w - TAU : w;
    }

    /** 가운데 기준 각도 */
    static double polarOf(Bosses.Unit u, Vec3 l) {
        return Math.atan2(l.z - u.center.z, l.x - u.center.x);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번 — 0.34.0 BOSS_HINTS.chat 일 때만 보냄, 알렸다는 표시 told 는 늘 남김) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        BossKit.hintSay(u, text);
    }

    /** 머리 (연출용: 엔티티 자리 위) */
    static Vec3 headAt(Bosses.Unit u) {
        Vec3 l = u.boss.position();
        return new Vec3(l.x, l.y + 3.6, l.z);
    }

    /** 독 물보라 (rpg:splash: 튀어 오르는 물방울 왕관). 밑동이 바닥에 닿게 올림 */
    static void splash(ServerLevel dim, Vec3 at, double size, double[] color, double life) {
        SkillFx.fx(dim, "rpg:splash", new Vec3(at.x, at.y + size * 0.75, at.z), Opt.of("size", size, "life", life, "color", color, "alpha", 0.95));
    }

    /** 독 안개 (rpg:dust 초록) */
    static void mist(ServerLevel dim, Vec3 at, double size, double[] color, double life) {
        SkillFx.fx(dim, "rpg:dust", new Vec3(at.x, at.y + size * 0.35, at.z), Opt.of("size", size, "life", life, "color", color, "alpha", 0.8));
    }

    static void shakeNear(Bosses.Unit u, Vec3 at, double range, double intensity, double seconds) {
        for (ServerPlayer p : fighters(u)) if (hdist(p.position(), at) <= range) SkillFx.shake(p, intensity, seconds);
    }

    /** 밀어내는 힘 (kx, kz): 담장에서 5칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖 · 담장 탑 위로 날아가지 않게) → {x, z} */
    static double[] keepIn(Bosses.Unit u, ServerPlayer p, double kx, double kz) {
        Vec3 c = u.center;
        double rx = p.getX() - c.x;
        double rz = p.getZ() - c.z;
        double r = Math.hypot(rx, rz);
        if (r < u.enc.def.obj("arena").num("radius") - 5 || r < 1e-6) return new double[] {kx, kz};
        double ux = rx / r;
        double uz = rz / r;
        double out = kx * ux + kz * uz;
        if (out <= 0) return new double[] {kx, kz};
        return new double[] {kx - ux * (out + 0.15), kz - uz * (out + 0.15)};
    }

    /** 공격이 닿는 사람 */
    static List<ServerPlayer> targets(Bosses.Unit u) {
        return fighters(u);
    }

    static Obj swimDef(Bosses.Unit u) {
        return u.def.obj("swim");
    }

    static double arenaRadius(Bosses.Unit u) {
        return u.enc.def.obj("arena").num("radius");
    }

    // ------------------------------------------------------------ 몸 (목뿌리 · 목 · 꼬리)

    /** 꼬리가 반지름 r 원을 따라 굽는 마디 각도 (도, 부호: 도는 쪽 dir = +1 이면 음수). build.py 의 slither 동작과 같은 약속 */
    public static double curveFor(Bosses.Unit u, double r, int dir) {
        double deg = ((swimDef(u).num("seg") / Math.max(0.5, r)) * 180) / Math.PI;
        return -dir * Math.min(CURVE_MAX, deg);
    }

    /** 목뿌리 (몸통이 시작되는 곳) */
    public static Vec3 pivotOf(Bosses.Unit u) {
        Swim sw = mem(u).swim;
        return new Vec3(sw.px, u.center.y, sw.pz);
    }

    /** 머리 자리 · 방향: 목뿌리에서 (몸 방향 + 목 각도) 로 목 길이만큼 → {x, z, yaw} */
    static double[] headOf(Bosses.Unit u) {
        Swim sw = mem(u).swim;
        double y = sw.byaw + sw.nyaw;
        double N = swimDef(u).num("neck");
        return new double[] {sw.px + Math.cos(y) * N, sw.pz + Math.sin(y) * N, y};
    }

    /** 몸 상태대로 엔티티 (머리) 를 옮김 (엔진 pose). 목뿌리가 해자 물 위면 몸이 잠김 */
    static void place(Bosses.Unit u) {
        Swim sw = mem(u).swim;
        double[] h = headOf(u);
        u.api.pose(u, floorAt(u, h[0], h[1]), h[2]);
        sw.sinkTo = overMoat(u, pivotOf(u)) ? 1 : 0;
    }

    /** 머리를 at 에 두고 yaw 쪽을 보게 (목은 곧게, 목뿌리는 그 뒤로) */
    static void setHead(Bosses.Unit u, Vec3 at, double yaw) {
        Swim sw = mem(u).swim;
        double N = swimDef(u).num("neck");
        sw.px = at.x - Math.cos(yaw) * N;
        sw.pz = at.z - Math.sin(yaw) * N;
        sw.byaw = yaw;
        sw.nyaw = 0;
        place(u);
    }

    /** 목뿌리를 해자 가운데 원의 ang 자리에 (몸은 도는 쪽을 봄, 목 곧게, 꼬리는 원을 따라 바로). 시험 · 미리보기도 씀 */
    public static void setOnCircle(Bosses.Unit u, double ang) {
        Obj S = swimDef(u);
        Swim sw = mem(u).swim;
        Vec3 c = u.center;
        double radius = S.num("radius");
        sw.ang = wrap(ang);
        sw.px = c.x + Math.cos(sw.ang) * radius;
        sw.pz = c.z + Math.sin(sw.ang) * radius;
        sw.byaw = sw.ang + (sw.dir * Math.PI) / 2;
        sw.nyaw = 0;
        sw.onCircle = true;
        sw.tailTo = sw.byaw + Math.PI;
        sw.tail = sw.tailTo;
        sw.curveTo = curveFor(u, radius, sw.dir);
        sw.curve = sw.curveTo;
        place(u);
    }

    /** 속성 보내기 (바뀐 만큼이 작으면 보내지 않음) → 보낸 값 */
    static Double sendProp(Bosses.Unit u, String id, double v, Double last) {
        if (last != null && Math.abs(v - last) < (id.equals("rpg:sink") ? 0.02 : 0.05)) return last;
        if (u.boss != null && !u.boss.isRemoved()) u.boss.setProp(id, v);
        return v;
    }

    /** 매 틱: 꼬리 방향 · 굽힘 · 물에 잠김을 목표 쪽으로 조금씩 (한 번에 휙 바뀌지 않게) → 모델 속성 */
    static void bodyTick(Bosses.Unit u) {
        Swim sw = mem(u).swim;
        sw.tail = turnToward(sw.tail, sw.tailTo, TAIL_TURN);
        double dc = sw.curveTo - sw.curve;
        sw.curve = Math.abs(dc) <= 1.2 ? sw.curveTo : sw.curve + Math.signum(dc) * 1.2;
        double ds = sw.sinkTo - sw.sink;
        sw.sink = Math.abs(ds) <= 0.08 ? sw.sinkTo : sw.sink + Math.signum(ds) * 0.08;
        // 꼬리 방향: 머리 바로 뒤 (머리 방향 + 180°) 에서 얼마나 돌았는지
        double rel = wrapPi(sw.tail - (sw.byaw + sw.nyaw + Math.PI)) / DEG;
        sw.tailSent = sendProp(u, "rpg:tail", Js.round(clamp(rel, -TAIL_MAX, TAIL_MAX) * 100) / 100, sw.tailSent);
        sw.curveSent = sendProp(u, "rpg:curve", Js.round(clamp(sw.curve, -CURVE_MAX, CURVE_MAX) * 100) / 100, sw.curveSent);
        sw.sinkSent = sendProp(u, "rpg:sink", clamp(sw.sink, 0, 1), sw.sinkSent);
    }

    /** 해자 물 위인지 */
    static boolean overMoat(Bosses.Unit u, Vec3 l) {
        ArenaThemes.Isle s = ArenaThemes.isleSize(u.enc.key);
        double r = hdist(l, u.center);
        return r > s.island() - 0.3 && r < s.moat() + 0.3;
    }

    static double turnToward(double from, double to, double step) {
        double d = wrapPi(to - from);
        return Math.abs(d) <= step ? to : from + Math.signum(d) * step;
    }

    // ------------------------------------------------------------ 헤엄 · 목 돌리기

    /**
     * 패턴이 없을 때: 목을 곧게 펴며 목뿌리가 해자 가운데 원을 따라 돎.
     * 원에서 벗어나 있으면 (돌진 뒤) 앞으로 나아가며 원의 조금 앞 자리 쪽으로 몸을 돌려 휘어 들어감
     */
    public static void swimTick(Bosses.Unit u, long now) {
        Obj S = swimDef(u);
        Swim sw = mem(u).swim;
        Vec3 c = u.center;
        double radius = S.num("radius");
        double speed = S.num("speed");
        double step = S.num("turn") * DEG;
        sw.nyaw = turnToward(sw.nyaw, 0, step);
        if (sw.onCircle) {
            sw.ang = wrap(sw.ang + (sw.dir * speed) / radius);
            sw.px = c.x + Math.cos(sw.ang) * radius;
            sw.pz = c.z + Math.sin(sw.ang) * radius;
            sw.byaw = sw.ang + (sw.dir * Math.PI) / 2;
            sw.tailTo = sw.byaw + Math.PI;
        } else {
            // 원 밖이면 안쪽으로, 원 안이면 바깥쪽으로 틀어 (1칸마다 20°, 60° 까지) 도는 쪽 접선을 따라감 → 원 위에서는 접선 그대로
            double pa = polarOf(u, pivotOf(u));
            double err = hdist(pivotOf(u), c) - radius;
            double want = pa + (sw.dir * Math.PI) / 2 + sw.dir * clamp(err * 20 * DEG, -60 * DEG, 60 * DEG);
            sw.byaw = turnToward(sw.byaw, want, step);
            sw.px += Math.cos(sw.byaw) * speed * 1.4;
            sw.pz += Math.sin(sw.byaw) * speed * 1.4;
            double pa2 = polarOf(u, pivotOf(u));
            sw.ang = wrap(pa2);
            sw.tailTo = pa2 + (sw.dir * Math.PI) / 2 + Math.PI;
            // 원에 닿고 도는 쪽을 보면 원을 따라 돎
            if (Math.abs(hdist(pivotOf(u), c) - radius) < 0.3 && Math.abs(wrapPi(sw.byaw - (pa2 + (sw.dir * Math.PI) / 2))) < 5 * DEG) {
                sw.onCircle = true;
                sw.px = c.x + Math.cos(sw.ang) * radius;
                sw.pz = c.z + Math.sin(sw.ang) * radius;
                sw.byaw = sw.ang + (sw.dir * Math.PI) / 2;
            }
        }
        sw.curveTo = curveFor(u, radius, sw.dir);
        u.faceTo = sw.byaw + sw.nyaw;
        place(u);
        // 물살 (머리 옆 작은 물보라)
        if (now % 6 == 0) {
            Vec3 h = u.boss.position();
            if (overMoat(u, h)) splash(u.dim, floorAt(u, h.x, h.z), 1.1, FOAM, 0.45);
        }
        if (now % 60 == 0) play(u.dim, "mob.dolphin.swim", u.boss.position(), 0.5, 0.7);
    }

    /** 패턴 동안: 목뿌리는 그대로, 목만 돌려 바라보려는 쪽 (u.faceTo: 엔진 face 또는 모듈이 정함) 을 봄 (몸 방향에서 NECK_MAX 까지) */
    static void neckTick(Bosses.Unit u) {
        Swim sw = mem(u).swim;
        double look = u.faceTo != null ? u.faceTo : sw.byaw + sw.nyaw;
        double want = clamp(wrapPi(look - sw.byaw), -NECK_MAX, NECK_MAX);
        double d = want - sw.nyaw;
        double step = swimDef(u).num("turn") * DEG;
        sw.nyaw = Math.abs(d) <= step ? want : sw.nyaw + Math.signum(d) * step;
        place(u);
    }

    /** 목이 그쪽을 볼 수 있는지 (몸 방향에서 NECK_MAX 안) */
    static boolean neckCanFace(Bosses.Unit u, Vec3 from, Vec3 to) {
        return Math.abs(wrapPi(angOf(dirTo(from, to)) - mem(u).swim.byaw)) <= NECK_MAX;
    }

    // ------------------------------------------------------------ 물어뜯기 (평타)

    public static boolean bite(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj m = u.def.obj("melee");
        Vec3 piv = pivotOf(u);
        double half = bossSize(u.def).half();
        double reach = swimDef(u).num("neck") + half + m.num("reach");
        // 목이 닿는 사람 가운데 가장 가까운 사람 (v2.20.0 도발한 사람이 닿으면 그 사람)
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : list) {
            if (hdist(p.position(), piv) <= reach && Math.abs(p.getY() - u.center.y) <= 4 && neckCanFace(u, piv, p.position())) pool.add(p);
        }
        ServerPlayer t = meleeTarget(u, pool, piv);
        if (t == null) return false;
        Vec3 dir = dirTo(piv, t.position());
        int windup = (int) m.num("windup");
        u.api.face(u, dir);
        fanMarks(u.dim, piv, dir, Js.round(reach), m.num("cone"), windup, RED);
        u.api.anim(u, m.str("anim"));
        sound(u, snd(u, "swing"), headAt(u), 0.8, 1);
        u.api.cast(u, "bite", windup + 6, step(windup, (x, n) -> biteHit(x, piv, dir, reach)));
        return true;
    }

    static void biteHit(Bosses.Unit u, Vec3 apex, Vec3 dir, double radius) {
        Obj m = u.def.obj("melee");
        for (ServerPlayer p : targets(u)) {
            if (Math.abs(p.getY() - apex.y) > 4 || !inFan(p.position(), apex, dir, radius, m.num("cone"), 0.5)) continue;
            double[] kk = keepIn(u, p, dir.x * 1.2, dir.z * 1.2);
            bossHit(u, p, m.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.35), "directional", true));
            calm(u, p); // 밀림은 피해가 막혀도 들어감
        }
        sound(u, snd(u, "hit"), headAt(u), 0.9, 1);
        Vec3 h = u.boss.position();
        SkillFx.fx(u.dim, "rpg:slash_heavy_r_glow", new Vec3(h.x + dir.x * 1.5, h.y + 1.4, h.z + dir.z * 1.5), Opt.of("dir", dir, "speed", 0.0, "spin", 0.0, "size",
                2.6, "life", 0.25, "color", rgb(u.def, "color"), "alpha", 0.95));
    }

    // ------------------------------------------------------------ 독니 돌진

    /** 돌진 길: from 에서 dir 쪽으로, 반대편 해자 (가운데 원보다 endIn 칸 안쪽 원) 까지 (길이 L) */
    static Lane lane(Bosses.Unit u, Vec3 from, Vec3 dir) {
        double rm = swimDef(u).num("radius") - u.def.obj("dash").num("endIn");
        Vec3 c = u.center;
        double ox = from.x - c.x;
        double oz = from.z - c.z;
        double b = ox * dir.x + oz * dir.z;
        double q = b * b - (ox * ox + oz * oz - rm * rm);
        double L = q >= 0 ? -b + Math.sqrt(q) : 0;
        return new Lane(floorAt(u, from.x, from.z), dir, L);
    }

    /** 독니 돌진 (center = 범람 뒤: 흰 고리 안에 모인 곳 — 가운데에 가장 가까운 사람 — 을 가로지름) */
    public static boolean dash(Bosses.Unit u, long now, List<ServerPlayer> list, boolean center) {
        Obj d = u.def.obj("dash");
        Obj S = swimDef(u);
        Vec3 c = u.center;
        Vec3 piv = pivotOf(u);
        double neck = S.num("neck");
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) {
            if (hdist(p.position(), c) <= S.num("radius") - 1 && hdist(p.position(), piv) >= neck + d.num("near") && Math.abs(p.getY() - c.y) < 4
                    && neckCanFace(u, piv, p.position())) cand.add(p);
        }
        if (cand.isEmpty()) return false;
        ServerPlayer t;
        if (center) {
            List<ServerPlayer> sorted = new ArrayList<>(cand);
            sorted.sort((a, b) -> Double.compare(hdist(a.position(), c), hdist(b.position(), c)));
            t = sorted.get(0);
        } else t = cand.get((int) Math.floor(random() * cand.size()));
        Vec3 dir = dirTo(piv, t.position());
        // 목을 그쪽으로 돌린 머리 자리에서 출발
        Lane ln = lane(u, new Vec3(piv.x + dir.x * neck, c.y, piv.z + dir.z * neck), dir);
        if (ln.L() < 6) return false;
        Bosses.Api api = u.api;
        int windup = (int) d.num("windup");
        Vec3 to = new Vec3(ln.from().x + dir.x * ln.L(), ln.from().y, ln.from().z + dir.z * ln.L());
        api.face(u, dir);
        lineMarks(u.dim, ln.from(), to, d.num("width") / 2, windup, PURPLE);
        api.anim(u, "dash");
        sound(u, snd(u, "swing"), headAt(u), 0.6, 1);
        play(u.dim, "mob.breeze.charge", ln.from(), 0.6, 1);
        BossKit.hintFace(t, "§5§l노림! §f보라 길에서 옆으로 비키세요", Opt.of("dist", 2.2, "up", 0.35));
        explain(u, "dash", "§5[보스] 독니 돌진: §f요르문간드가 보라 길을 따라 섬을 가로질러 돌진합니다. §e길에서 옆으로 비키세요 §7— 맞으면 침묵 (스킬·룬을 못 씀)");
        Set<UUID> hit = new HashSet<>();
        int n = (int) Math.ceil(ln.L() / d.num("speed"));
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 0; k <= n; k++) {
            int kk = k;
            steps.add(step(windup + k, (x, w) -> dashStep(x, ln, kk, n, hit)));
        }
        steps.add(step(windup + n + 1, (x, w) -> dashEnd(x)));
        api.cast(u, "dash", windup + n + 8, steps);
        return true;
    }

    /** 돌진 k 번째 틱 (0 = 출발): 머리가 이번 틱에 지나간 자리의 사람이 맞음 (한 번만). 꼬리는 길 뒤로 따라 들어옴 */
    static void dashStep(Bosses.Unit u, Lane ln, int k, int n, Set<UUID> hit) {
        Obj d = u.def.obj("dash");
        Swim sw = mem(u).swim;
        double speed = d.num("speed");
        double s0 = (k - 1) * speed;
        double s = Math.min(ln.L(), k * speed);
        Vec3 at = new Vec3(ln.from().x + ln.dir().x * s, ln.from().y, ln.from().z + ln.dir().z * s);
        sw.charging = k < n;
        sw.onCircle = false;
        setHead(u, at, angOf(ln.dir()));
        sw.tailTo = angOf(ln.dir()) + Math.PI;
        sw.curveTo = 0;
        if (k == 0) {
            play(u.dim, "item.trident.riptide_1", at, 0.7, 1);
            return;
        }
        if (k % 2 == 0) {
            if (overMoat(u, at)) splash(u.dim, at, 1.6, FOAM, 0.5);
            else mist(u.dim, at, 1.6, DUST, 0.5);
        }
        double sx = -ln.dir().z;
        double sz = ln.dir().x;
        double width = d.num("width");
        for (ServerPlayer p : targets(u)) {
            if (hit.contains(p.getUUID()) || Math.abs(p.getY() - ln.from().y) > 3) continue;
            double rx = p.getX() - ln.from().x;
            double rz = p.getZ() - ln.from().z;
            double t = rx * ln.dir().x + rz * ln.dir().z;
            if (t <= s0 - 0.5 || t > s + 0.5) continue;
            double lat = rx * sx + rz * sz;
            if (Math.abs(lat) > width / 2 + 0.3) continue;
            hit.add(p.getUUID());
            double sg = lat >= 0 ? 1 : -1;
            double[] kk = keepIn(u, p, sx * sg * d.num("knock"), sz * sg * d.num("knock"));
            boolean landed = bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.4)));
            calm(u, p);
            if (landed) {
                Status.silence(p, (int) d.num("silence"));
                BossKit.hintFace(p, "§5§l침묵! §7독에 혀가 굳었습니다", Opt.of("dist", 2.2, "up", 0.35));
                SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.6, "life", 0.35, "color", PURPLE,
                        "alpha", 0.9));
            }
        }
        if (k == n) shakeNear(u, at, 10, 0.2, 0.3);
    }

    /** 돌진 끝 (반대편 해자): 앞으로 나아가며 해자로 휘어 들어가 다시 돎 (가끔 도는 방향을 바꿈) */
    static void dashEnd(Bosses.Unit u) {
        Swim sw = mem(u).swim;
        sw.charging = false;
        if (random() < swimDef(u).num("flip")) sw.dir = -sw.dir;
        Vec3 at = u.boss.position();
        splash(u.dim, floorAt(u, at.x, at.z), 2.4, FOAM, 0.7);
        play(u.dim, "random.splash", at, 0.7, 1);
    }

    // ------------------------------------------------------------ 꼬리 휘감기

    /** 꼬리 쪽 자리: 목뿌리 뒤 (해자를 따라) from ~ to 칸, 가운데에서 inner 칸 밖 → {각도 범위, 반지름 범위} */
    public static Zone tailZone(Bosses.Unit u) {
        Obj d = u.def.obj("tail");
        Swim sw = mem(u).swim;
        double rm = swimDef(u).num("radius");
        return new Zone(sw.ang, sw.dir, d.num("from") / rm, d.num("to") / rm, d.num("inner"), arenaRadius(u) + 1.5);
    }

    public static boolean inTail(Bosses.Unit u, Zone Z, Vec3 l) {
        double r = hdist(l, u.center);
        if (r < Z.r0() - 0.3 || r > Z.r1() + 0.3) return false;
        double behind = wrap((Z.base() - polarOf(u, l)) * Z.dir());
        double pad = 0.3 / Math.max(1, r);
        return behind >= Z.a0() - pad && behind <= Z.a1() + pad;
    }

    /** 고리 부채꼴 예고 (가운데 기준 반지름 r0 ~ r1, 각도 th0 ~ th1): 극좌표 격자에 작은 판, 가장자리는 밝게 */
    static void sectorMarks(Bosses.Unit u, double r0, double r1, double th0, double th1, int ticks, double[] color) {
        double life = Math.max(0.1, ticks / 20.0);
        Vec3 c = u.center;
        double step = 1.5;
        for (double r = r0 + step / 2; r <= r1 + 0.01; r += step) {
            int n = Math.max(2, (int) Math.ceil(((th1 - th0) * r) / step));
            for (int i = 0; i <= n; i++) {
                double a = th0 + ((th1 - th0) * i) / n;
                boolean edge = r + step > r1 + 0.01 || r - step < r0 || i == 0 || i == n;
                SkillFx.fx(u.dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", new Vec3(c.x + Math.cos(a) * r, c.y + 0.07, c.z + Math.sin(a) * r), Opt.of("dir", Vec3.ZERO,
                        "size", (edge ? 0.55 : 0.75) * step, "life", life, "color", color, "alpha", edge ? 0.9 : 0.45, "grow", edge ? 0.0 : 1.0));
            }
        }
    }

    /** 꼬리가 해자를 따라 놓여 있는지 (원을 따라 돌고, 꼬리 방향 · 굽힘이 다 맞춰짐) */
    public static boolean tailSettled(Bosses.Unit u) {
        Swim sw = mem(u).swim;
        return sw.onCircle && Math.abs(wrapPi(sw.tail - sw.tailTo)) < 5 * DEG && Math.abs(sw.curve - sw.curveTo) <= 1 && Math.abs(sw.nyaw) < 30 * DEG;
    }

    public static boolean tailLash(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("tail");
        if (!tailSettled(u)) return false;
        Zone Z = tailZone(u);
        boolean any = false;
        for (ServerPlayer p : list) if (inTail(u, Z, p.position()) && Math.abs(p.getY() - u.center.y) < 4) any = true;
        if (!any) return false;
        int windup = (int) d.num("windup");
        double thA = Z.base() - Z.dir() * Z.a0();
        double thB = Z.base() - Z.dir() * Z.a1();
        sectorMarks(u, Z.r0(), Z.r1(), Math.min(thA, thB), Math.max(thA, thB), windup, ORANGE);
        u.api.anim(u, "coil");
        sound(u, "mob.cat.hiss", headAt(u), 0.5, 1);
        play(u.dim, "mob.enderdragon.flap", pivotOf(u), 0.6, 1);
        explain(u, "tail", "§6[보스] 꼬리 휘감기: §f요르문간드가 꼬리 쪽(목 뒤 해자와 섬 가장자리)을 휘감습니다. §e주황 자리에서 벗어나세요 §7— 맞으면 속박");
        u.api.cast(u, "tail", windup + 16, step(windup, (x, t) -> tailHit(x, Z)));
        return true;
    }

    static void tailHit(Bosses.Unit u, Zone Z) {
        Obj d = u.def.obj("tail");
        Vec3 c = u.center;
        double rm = swimDef(u).num("radius");
        // 꼬리가 지나간 자리: 물보라 · 흙먼지
        for (int i = 0; i <= 6; i++) {
            double a = Z.base() - Z.dir() * (Z.a0() + ((Z.a1() - Z.a0()) * i) / 6);
            splash(u.dim, floorAt(u, c.x + Math.cos(a) * rm, c.z + Math.sin(a) * rm), 1.8, FOAM, 0.6);
            mist(u.dim, floorAt(u, c.x + Math.cos(a) * (Z.r0() + 1.5), c.z + Math.sin(a) * (Z.r0() + 1.5)), 1.6, DUST, 0.6);
        }
        play(u.dim, "random.splash", pivotOf(u), 0.6, 1);
        play(u.dim, "mob.enderdragon.hit", pivotOf(u), 0.6, 1);
        for (ServerPlayer p : targets(u)) {
            if (!inTail(u, Z, p.position()) || Math.abs(p.getY() - c.y) > 3) continue;
            boolean landed = bossHit(u, p, d.num("damage"), Opt.of("knock", knock(0, 0, 0.2)));
            calm(u, p);
            if (landed) Status.root(p, (int) d.num("root"), BossKit.rootLabel("꼬리에 휘감김"));
        }
    }

    // ------------------------------------------------------------ 독해 범람

    public static void flood(Bosses.Unit u, long now) {
        Obj d = u.def.obj("flood");
        M m = mem(u);
        if (m == null) return;
        double safe = ArenaThemes.isleSize(u.enc.key).safe();
        int windup = (int) d.num("windup");
        int hold = (int) d.num("hold");
        m.bigUntil = now + windup + hold + 80;
        m.flood = new Flood(u, now, now + windup, now + windup + hold);
        u.api.face(u, dirTo(pivotOf(u), u.center));
        u.api.anim(u, "rear");
        sound(u, snd(u, "roar"), headAt(u), 0.7, 1);
        BossKit.hintTitle(u, "§2§l독해 범람", "§f흰 고리 안으로! §7고리 밖은 독에 잠깁니다", fighters(u));
        explain(u, "flood", "§2[보스] 독해 범람: §f해자의 독이 차올라 섬의 흰 고리(반지름 " + Js.str(safe) + "칸) 밖을 모두 덮습니다. §e흰 고리 안으로 모이세요 §7— 독이 빠지면 모인 곳으로 돌진합니다");
        u.api.cast(u, "flood", windup + hold + 10);
        floodMarks(u, windup);
    }

    /** 범람 때 안전한 곳의 바깥 끝: 흰 석영 고리 (칸 가운데가 safe - 1 ~ safe 인 칸) 의 바깥 가장자리 */
    public static double safeEdge(double safe) {
        return safe + 0.7;
    }

    /** 범람 예고: 흰 고리 (안전) 테두리 + 고리 밖 초록 판 */
    static void floodMarks(Bosses.Unit u, int ticks) {
        double safe = ArenaThemes.isleSize(u.enc.key).safe();
        double R = arenaRadius(u);
        Vec3 c = u.center;
        double life = Math.max(0.1, ticks / 20.0);
        double edge = safeEdge(safe);
        disc(u.dim, c, edge, ticks, SAFE, Opt.of("fill", false, "alpha", 0.95));
        for (double r = edge + 1.4; r <= R + 1.5; r += 2.8) {
            int n = (int) Math.ceil((TAU * r) / 2.8);
            for (int i = 0; i < n; i++) {
                double a = ((double) i / n) * TAU + r;
                SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(c.x + Math.cos(a) * r, c.y + 0.07, c.z + Math.sin(a) * r), Opt.of("dir", Vec3.ZERO, "size", 1.5, "life", life,
                        "color", VENOM, "alpha", 0.5));
            }
        }
    }

    static void floodTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Flood F = m.flood;
        if (F == null) return;
        Bosses.Unit u = F.u();
        Obj d = u.def.obj("flood");
        if (!u.alive || !Bosses.valid(u.boss)) {
            m.flood = null;
            return;
        }
        ArenaThemes.Isle size = ArenaThemes.isleSize(enc.key);
        Vec3 c = u.center;
        double edge = safeEdge(size.safe());
        if (now < F.waveAt()) {
            // 차오르는 독: 해자를 따라 물보라, 남은 시간
            if ((now - F.t0()) % 6 == 0) {
                for (int i = 0; i < 4; i++) {
                    double a = random() * TAU;
                    double r = size.island() + random() * (size.moat() - size.island());
                    splash(u.dim, floorAt(u, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r), 1.4 + ((double) (now - F.t0()) / d.num("windup")) * 1.2, VENOM, 0.6);
                }
                play(u.dim, "bubble.pop", c, 0.6, 0.6);
            }
            long left = F.waveAt() - now;
            if (left % 20 == 0 && left <= 60) faceAll(u, "§2§l" + (left / 20), Opt.of("dist", 2.4, "up", 0.45), fighters(u));
            return;
        }
        if (now == F.waveAt()) {
            // 물결: 둘레에서 흰 고리까지 안쪽으로 밀려듦
            double R = enc.def.obj("arena").num("radius");
            for (int i = 0; i < 20; i++) {
                double a = (i / 20.0) * TAU;
                Vec3 dir = new Vec3(-Math.cos(a), 0, -Math.sin(a));
                SkillFx.fx(u.dim, "rpg:tide_wave", new Vec3(c.x + Math.cos(a) * (R + 1), c.y + 1.2, c.z + Math.sin(a) * (R + 1)), Opt.of("dir", dir, "speed", 14.0, "size", 2.6,
                        "thick", 1.6, "life", (R + 1 - size.safe()) / 14, "color", VENOM, "alpha", 0.95));
            }
            play(u.dim, "random.splash", c, 0.5, 1);
            play(u.dim, "ambient.weather.thunder", c, 0.7, 0.5);
            shakeNear(u, c, R + 8, 0.3, 0.8);
            for (ServerPlayer p : targets(u)) {
                if (hdist(p.position(), c) <= edge) continue;
                // 독 물결: 보스가 때린 것이 아님 (게임이 보스 쪽에서 밀어내지 않게: 아홉 걸음이 밀린 걸음을 셈하지 않도록)
                if (bossHit(u, p, d.num("damage"), Opt.of("cause", "entityAttack", "noSource", true, "effect", BossKit.effect("poison", (int) d.num("poison"), 0)))) {
                    splash(u.dim, p.position(), 1.8, VENOM, 0.6);
                }
            }
        }
        if (now < F.endAt()) {
            // 남은 독: 고리 밖이면 1초마다 마법 피해, 초록 안개
            if ((now - F.waveAt()) % 5 == 0) {
                for (int i = 0; i < 5; i++) {
                    double a = random() * TAU;
                    double r = size.safe() + 1 + random() * (enc.def.obj("arena").num("radius") - size.safe());
                    mist(u.dim, floorAt(u, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r), 2.2, VENOM, 0.8);
                }
            }
            if ((now - F.waveAt()) % 20 == 19) {
                for (ServerPlayer p : targets(u)) {
                    if (hdist(p.position(), c) <= edge) continue;
                    bossHit(u, p, d.num("tick"), Opt.of("cause", "magic", "noSource", true));
                    BossKit.hintFace(p, "§2§l독에 잠겼습니다 §7— 흰 고리 안으로", Opt.of("dist", 2.2, "up", 0.35));
                }
            }
            return;
        }
        m.flood = null;
        // 독이 빠짐 → 곧바로 모인 곳을 가로질러 돌진
        m.next.put("dash", now + 4);
        m.floodDash = true;
        BossKit.hintFaceAll(u, "§a§l독이 빠졌습니다 §7— 돌진에 조심", Opt.of("dist", 2.4, "up", 0.45), fighters(u));
    }

    // ------------------------------------------------------------ 아홉 걸음의 독

    /** 보스에게 맞아 밀린 사람: calm 틱 동안 걸음으로 셈하지 않음 */
    static void calm(Bosses.Unit u, ServerPlayer p) {
        M m = mem(u);
        Venom V = m == null ? null : m.venom;
        if (V == null) return;
        long until = Tasks.now() + (long) u.def.obj("venom").num("calm");
        for (Mark mk : V.marks()) if (mk.p.getUUID().equals(p.getUUID())) mk.calm = until;
    }

    public static boolean venomMark(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("venom");
        M m = mem(u);
        if (m == null || list.isEmpty()) return false;
        int n = list.size() > 3 ? 2 : 1;
        List<ServerPlayer> picked = shuffle(list);
        List<Mark> marks = new ArrayList<>();
        long time = (long) d.num("time");
        for (ServerPlayer p : picked.subList(0, Math.min(n, picked.size()))) marks.add(new Mark(p, p.position(), now + (long) d.num("grace"), now + time));
        m.venom = new Venom(u, marks, now);
        m.bigUntil = now + time + 40;
        u.api.face(u, dirTo(pivotOf(u), marks.get(0).p.position()));
        u.api.anim(u, "hiss");
        sound(u, "mob.cat.hiss", headAt(u), 0.45, 1);
        String steps = Js.str(d.num("steps"));
        for (Mark mk : marks) {
            SkillFx.beam(u.dim, headAt(u), new Vec3(mk.p.getX(), mk.p.getY() + 1.2, mk.p.getZ()), VENOM, Opt.of("half", 0.4, "thick", 0.14, "life", 0.6, "gap", 0.6));
            BossKit.hintTitle(u, "§2§l아홉 걸음의 독", "§f스스로 §c" + steps + "칸§f을 걸으면 쓰러집니다 §7— 동료가 곁에 서면 독을 빼 줍니다", List.of(mk.p));
            BossKit.notify(mk.p, "§a§l" + steps, 25);
            play(u.dim, "mob.zombie.unfect", mk.p.position(), 0.8, 1);
        }
        List<String> names = new ArrayList<>();
        for (Mark mk : marks) names.add(mk.p.getName().getString());
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer p : fighters(u)) {
            boolean marked = false;
            for (Mark mk : marks) if (mk.p.getUUID().equals(p.getUUID())) marked = true;
            if (!marked) others.add(p);
        }
        BossKit.hintTitle(u, "§2§l" + String.join(", ", names) + "님에게 아홉 걸음의 독!", "§f곁(초록 원)에 서서 독을 빼 주세요", others);
        explain(u, "venom", "§2[보스] 아홉 걸음의 독: §f표식이 붙은 사람이 스스로 " + steps + "칸을 움직이면 쓰러집니다 (즉사, 순간이동 스킬도 셈). §e표식이 붙은 사람은 멈추고, 동료가 곁(초록 원)에 "
                + Js.toFixed(d.num("need") / 20, 1) + "초 서 있으면 독이 빠집니다 §7(" + Js.str(Js.round(time / 20.0)) + "초 버티면 사라짐)");
        u.api.cast(u, "venom", 30);
        return true;
    }

    static void venomTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Venom V = m.venom;
        if (V == null) return;
        Bosses.Unit u = V.u();
        Obj d = u.def.obj("venom");
        if (!u.alive || !Bosses.valid(u.boss)) {
            m.venom = null;
            return;
        }
        double leave = d.num("leave");
        double radius = d.num("radius");
        double stepsMax = d.num("steps");
        double need = d.num("need");
        // 독을 빼 줄 수 있는 사람: 필드 둘레 (표식이 붙은 사람이 필드 밖으로 밀려나도 동료가 따라가 빼 줄 수 있게)
        List<ServerPlayer> all = new ArrayList<>();
        for (ServerPlayer p : Arena.playersIn(u.a, leave)) if (okTarget(p)) all.add(p);
        for (Mark mk : V.marks()) {
            if (mk.done) continue;
            ServerPlayer p = mk.p;
            if (p == null || p.isRemoved() || !okTarget(p)) {
                mk.done = true; // 쓰러짐 · 나감
                continue;
            }
            // 싸움에서 멀리 떠남 (순간이동 · 다시 태어남 등, 걸어서는 그 전에 쓰러짐): 표식을 거둠
            if (hdist(p.position(), u.center) > arenaRadius(u) + leave) {
                mk.done = true;
                continue;
            }
            // 걸음: 스스로 움직인 가로 거리 (보스에게 맞아 밀린 뒤 calm 틱은 셈하지 않음)
            double moved = hdist(p.position(), mk.last);
            mk.last = p.position();
            if (now > mk.calm && moved > 0.01) mk.used += moved;
            double left = Math.max(0, stepsMax - mk.used);
            if (mk.used >= stepsMax) {
                mk.done = true;
                Vec3 at = p.position();
                splash(u.dim, at, 2.4, VENOM, 0.7);
                SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.4, "life", 0.5, "color", VENOM, "alpha", 0.95));
                play(u.dim, "mob.zombie.unfect", at, 0.5, 1);
                BossKit.hintSay(u, "§2[보스] §e" + p.getName().getString() + "§f님이 아홉 걸음 끝에 쓰러졌습니다");
                Status.kill(p, u.boss);
                continue;
            }
            // 독 빼기: 표식이 없는 동료가 radius 안
            List<ServerPlayer> helpers = new ArrayList<>();
            for (ServerPlayer q : all) {
                if (q.getUUID().equals(p.getUUID())) continue;
                boolean marked = false;
                for (Mark x : V.marks()) if (!x.done && x.p.getUUID().equals(q.getUUID())) marked = true;
                if (marked) continue;
                if (hdist(q.position(), p.position()) <= radius && Math.abs(q.getY() - p.getY()) < 2.5) helpers.add(q);
            }
            if (!helpers.isEmpty()) {
                mk.help += 1;
                if (mk.help % 4 == 1) {
                    for (ServerPlayer q : helpers) {
                        SkillFx.beam(u.dim, new Vec3(p.getX(), p.getY() + 1, p.getZ()), new Vec3(q.getX(), q.getY() + 1, q.getZ()), VENOM, Opt.of("half", 0.25, "thick", 0.08,
                                "life", 0.25, "gap", 0.4));
                        BossKit.notify(q, "§a§l" + Js.str(Math.min(99, Js.round((mk.help / need) * 100))) + "%", 10);
                    }
                }
                if (mk.help >= need) {
                    mk.done = true;
                    helpers.sort((a, b) -> Double.compare(hdist(a.position(), p.position()), hdist(b.position(), p.position())));
                    ServerPlayer q = helpers.get(0);
                    bossHit(u, q, d.num("helper"), Opt.of("cause", "magic", "noSource", true));
                    BossKit.hintFace(p, "§a§l독이 빠졌습니다!", Opt.of("dist", 2.2, "up", 0.35));
                    BossKit.hintFace(q, "§a§l독을 빼 주었습니다 §7(조금 다침)", Opt.of("dist", 2.2, "up", 0.35));
                    SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.8, "life", 0.4, "color", SAFE,
                            "alpha", 0.9));
                    play(u.dim, "mob.zombie.remedy", p.position(), 1, 1);
                    BossKit.hintSay(u, "§a[보스] §e" + q.getName().getString() + "§f님이 §e" + p.getName().getString() + "§f님의 독을 빼 주었습니다");
                    continue;
                }
            }
            if (now >= mk.until) {
                mk.done = true;
                BossKit.hintFace(p, "§a§l독이 사라졌습니다", Opt.of("dist", 2.2, "up", 0.35));
                continue;
            }
            // 표시: 발밑 초록 원 (독을 빼 주는 곳), 남은 걸음 (액션바 — 0.34.0 숫자만)
            if ((now - V.t0()) % 10 == 0) disc(u.dim, floorAt(u, p.getX(), p.getZ()), radius, 12, VENOM, Opt.of("fill", false, "alpha", 0.9));
            long shown = (long) Math.ceil(left);
            if (shown != mk.shown || (now - V.t0()) % 10 == 0) {
                mk.shown = shown;
                String col = shown <= 3 ? "§c" : shown <= 6 ? "§e" : "§a";
                BossKit.notify(p, col + "§l" + shown, 25);
            }
        }
        boolean allDone = true;
        for (Mark mk : V.marks()) if (!mk.done) allDone = false;
        if (allDone) {
            m.venom = null;
            m.bigUntil = Math.min(m.bigUntil, now + 60);
        }
    }

    // ------------------------------------------------------------ 세계를 감는 고리

    /** 고리 (몸통 모형): 가운데에 세우고 틈이 g 쪽을 보게 (처음부터 그쪽을 보고 나옴). 반지름은 속성 rpg:radius */
    static BedrockMob spawnRing(Bosses.Unit u, double g) {
        Vec3 c = u.center;
        BedrockMob e = BossKit.spawn(u.dim, RING_ID, c, BossKit.yawOfAng(g));
        if (e == null) return null;
        u.api.addProp(u.enc, e);
        e.setProp("rpg:radius", u.def.obj("ring").num("start"));
        return e;
    }

    public static void ring(Bosses.Unit u, long now, double at) {
        Obj d = u.def.obj("ring");
        M m = mem(u);
        if (m == null) return;
        Swim sw = m.swim;
        double half = (d.num("gap") * Math.PI) / 360;
        Vec3 h = u.boss.position();
        double head = polarOf(u, h);
        // 틈: 머리 바로 앞 (머리가 틈의 한쪽 끝, 꼬리 끝이 반대쪽 끝)
        double g = wrap(head + sw.dir * half);
        double start = d.num("start");
        int windup = (int) d.num("windup");
        m.bigUntil = Long.MAX_VALUE / 4; // 끝에서 풂
        // 고리가 시작될 때 싸우던 사람: 틈을 지나야만 고리 밖으로 나갈 수 있음 (in = 지난번에 본 자리가 몸 안쪽인지, at = 그 자리)
        Map<UUID, Entry> roster = new LinkedHashMap<>();
        for (ServerPlayer p : fighters(u)) roster.put(p.getUUID(), new Entry(p, hdist(p.position(), u.center) < start, p.getX(), p.getZ()));
        m.ring = new Ring(u, at, g, half, head, sw.dir, hdist(h, u.center), now, now + windup, start, spawnRing(u, g), roster);
        u.api.anim(u, "ring", null, COIL, "0");
        sound(u, snd(u, "roar"), headAt(u), 0.5, 1);
        play(u.dim, "mob.elderguardian.curse", u.center, 0.7, 1);
        BossKit.hintTitle(u, "§4§l세계를 감는 고리", "§f머리 앞 초록 틈으로 빠져나가세요! §7몸에 닿으면 즉사", fighters(u));
        explain(u, "ring", "§4[보스] 세계를 감는 고리: §f요르문간드가 몸으로 필드를 감싸고 조여 옵니다. §e머리 앞 초록 틈으로만 빠져나갈 수 있습니다 §7— 틈 밖에서 몸에 닿거나 끝까지 고리 안에 남으면 즉사");
        // 틈 (안전한 길): 가운데에서 틈 쪽 부채꼴
        fanMarks(u.dim, u.center, new Vec3(Math.cos(g), 0, Math.sin(g)), arenaRadius(u) + 1, d.num("gap") - 6,
                windup + (int) Js.round(((start - d.num("end")) / d.num("speed")) * 20), SAFE, 2);
        u.api.cast(u, "ring", 100000);
    }

    /** 고리의 지금 반지름 */
    public static double ringRadius(Bosses.Unit u, Ring Rg, long now) {
        Obj d = u.def.obj("ring");
        if (now < Rg.startAt) return d.num("start");
        return Math.max(d.num("end"), d.num("start") - ((now - Rg.startAt) * d.num("speed")) / 20);
    }

    /** 틈 안인지 (가운데 기준 각도, 가장자리 0.3칸 너그럽게) */
    public static boolean inGap(Bosses.Unit u, Ring Rg, double x, double z) {
        return Math.abs(wrapPi(Math.atan2(z - u.center.z, x - u.center.x) - Rg.g)) <= Rg.half + 0.3 / Math.max(1, Math.hypot(x - u.center.x, z - u.center.z));
    }

    /** a → b 로 옮긴 사람이 가운데 c, 반지름 R 원 (고리 몸) 을 건넌 자리. 선분이 원과 만나지 않으면 (몸이 지나간 것) b → {x, z} */
    static double[] crossAt(Vec3 c, double ax0, double az0, double bx, double bz, double R) {
        double ax = ax0 - c.x;
        double az = az0 - c.z;
        double dx = bx - ax0;
        double dz = bz - az0;
        double A = dx * dx + dz * dz;
        if (A < 1e-9) return new double[] {bx, bz};
        double B = 2 * (ax * dx + az * dz);
        double disc = B * B - 4 * A * (ax * ax + az * az - R * R);
        if (disc < 0) return new double[] {bx, bz};
        double q = Math.sqrt(disc);
        for (double t : new double[] {(-B - q) / (2 * A), (-B + q) / (2 * A)}) {
            if (t >= 0 && t <= 1) return new double[] {ax0 + dx * t, az0 + dz * t};
        }
        return new double[] {bx, bz};
    }

    /** 짓눌림 (즉사): 불사의 토템으로 살아나면 잠시 뒤 다시 셈 (영영 빠지지 않게) */
    static void crush(Bosses.Unit u, Ring Rg, ServerPlayer p, String why) {
        long now = Tasks.now();
        Long last = Rg.crushed.get(p.getUUID());
        if (now - (last != null ? last : -1000) < 20) return;
        Rg.crushed.put(p.getUUID(), now);
        Vec3 at = p.position();
        splash(u.dim, at, 2.4, VENOM, 0.6);
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.6, "life", 0.5, "color", TEAL, "alpha", 0.95));
        play(u.dim, "random.anvil_land", at, 0.5, 0.8);
        BossKit.hintSay(u, "§4[보스] §e" + p.getName().getString() + "§f님이 " + why);
        Status.kill(p, u.boss);
    }

    static void ringTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Ring Rg = m.ring;
        if (Rg == null) return;
        Bosses.Unit u = Rg.u;
        Obj d = u.def.obj("ring");
        Vec3 c = u.center;
        if (!u.alive || !Bosses.valid(u.boss)) {
            endRing(enc, Rg);
            return;
        }
        double R = ringRadius(u, Rg, now);
        Rg.R = R;
        double start = d.num("start");
        double end = d.num("end");
        double thick = d.num("thick");
        int windup = (int) d.num("windup");
        // 머리: 틈의 한쪽 끝을 따라 (예고 동안 제자리에서 고리 자리로 나감), 틈 쪽 (꼬리 끝) 을 봄. 몸 · 꼬리는 고리를 따라 굽음
        double hr = now < Rg.startAt ? Rg.r0 + (start - Rg.r0) * Math.min(1, (double) (now - Rg.t0) / Math.max(1, windup - 10)) : R;
        Swim sw = m.swim;
        sw.onCircle = false;
        setHead(u, new Vec3(c.x + Math.cos(Rg.head) * hr, c.y, c.z + Math.sin(Rg.head) * hr), Rg.head + (Rg.dir * Math.PI) / 2);
        sw.tailTo = polarOf(u, pivotOf(u)) + (Rg.dir * Math.PI) / 2 + Math.PI;
        sw.curveTo = curveFor(u, hr, Rg.dir);
        if (Rg.prop != null && !Rg.prop.isRemoved()) Rg.prop.setProp("rpg:radius", Js.round(R * 100) / 100);
        if (now < Rg.startAt) {
            rosterTick(u, Rg, R, true);
            long left = Rg.startAt - now;
            if (left % 20 == 0 && left <= 60) faceAll(u, "§4§l" + (left / 20), Opt.of("dist", 2.4, "up", 0.45), fighters(u));
            return;
        }
        if (now == Rg.startAt) {
            play(u.dim, "mob.enderdragon.growl", c, 0.5, 1);
            shakeNear(u, c, enc.def.obj("arena").num("radius") + 8, 0.25, 0.8);
        }
        // 조이는 몸: 흙먼지 · 물보라 (틈 밖 몇 곳)
        if ((now - Rg.startAt) % 4 == 0) {
            for (int i = 0; i < 5; i++) {
                double a = Rg.g + Rg.half + random() * (TAU - 2 * Rg.half);
                Vec3 at = floorAt(u, c.x + Math.cos(a) * R, c.z + Math.sin(a) * R);
                if (overMoat(u, at)) splash(u.dim, at, 1.6, FOAM, 0.5);
                else mist(u.dim, at, 1.6, DUST, 0.5);
            }
        }
        if ((now - Rg.startAt) % 20 == 0) play(u.dim, "block.grindstone.use", c, 0.4, 0.8);
        rosterTick(u, Rg, R, false);
        // 나중에 들어온 사람 (명단 밖): 틈 밖에서 몸에 닿으면 즉사 (명단처럼 높이 상관없이)
        for (ServerPlayer p : targets(u)) {
            if (Rg.roster.containsKey(p.getUUID())) continue;
            double r = hdist(p.position(), c);
            if (Math.abs(r - R) <= thick && !inGap(u, Rg, p.getX(), p.getZ())) crush(u, Rg, p, "세계를 감는 고리에 짓눌렸습니다");
        }
        if (R > end) return;
        // 다 조임: 고리 안에 남은 사람은 즉사
        for (ServerPlayer p : targets(u)) {
            if (hdist(p.position(), c) < end + thick) crush(u, Rg, p, "고리 안에 갇혀 짓눌렸습니다");
        }
        splash(u.dim, c, 3.2, VENOM, 0.8);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(c.x, c.y + 0.1, c.z), Opt.of("dir", Vec3.ZERO, "size", 4.0, "life", 0.7, "color", TEAL, "alpha", 0.9));
        play(u.dim, "random.explode", c, 0.6, 0.8);
        endRing(enc, Rg);
        // 해자로 돌아가 다시 돎 (틈 쪽에서)
        sw.dir = Rg.dir;
        setOnCircle(u, Rg.g);
        u.faceTo = sw.byaw;
        u.api.anim(u, "emerge", null, COIL, null);
        splash(u.dim, pivotOf(u), 3, FOAM, 0.8);
        m.bigUntil = now + 100;
        // 애드온 u.cast.until = now + 20 (자바 Cast.until 은 final → 같은 이름의 짧은 틀로)
        if (u.cast != null && "ring".equals(u.cast.id)) u.api.cast(u, "ring", 20);
    }

    /**
     * 고리가 시작될 때 싸우던 사람 (명단): 틈 밖에서 몸 (두께 ±thick) 에 닿거나 몸을 건너면 (한 틱에 건너뛰어도, 건넌 자리로 셈) 즉사.
     * 틈에서 몸을 건너면 빠져나간 것, 틈을 지나지 않고 고리 밖에 있으면 (성문으로 나가는 등) 즉사. 높이는 보지 않음 (탑 위도).
     * 예고 동안 (warm) 에는 죽이지 않음: 틈으로 나간 것 (틈에서 몸을 건넜거나 틈 쪽 몸 바깥에 있음) 은 받아 두고, 몸 안쪽에 있으면 지금 자리를 기준으로 삼음
     * — 틈 밖으로 건너간 것은 조이기 시작할 때 셈 (그 전에 돌아오면 없던 일).
     * 쓰러진 사람은 뺌, 나갔다 다시 들어오면 (같은 UUID 의 새 플레이어) 이어서 셈
     */
    static void rosterTick(Bosses.Unit u, Ring Rg, double R, boolean warm) {
        Obj d = u.def.obj("ring");
        double thick = d.num("thick");
        Vec3 c = u.center;
        for (Iterator<Map.Entry<UUID, Entry>> it = Rg.roster.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Entry> en = it.next();
            Entry e = en.getValue();
            if (e.p.isRemoved()) {
                ServerPlayer back = null;
                for (ServerPlayer q : targets(u)) if (q.getUUID().equals(en.getKey())) back = q;
                if (back == null) continue;
                e.p = back;
            }
            ServerPlayer p = e.p;
            if (p.isDeadOrDying()) {
                it.remove(); // 죽은 사람은 다시 태어난 곳까지 쫓지 않음
                continue;
            }
            if (!okTarget(p)) continue;
            double x = p.getX();
            double z = p.getZ();
            double r = Math.hypot(x - c.x, z - c.z);
            boolean cross = e.in != (r < R);
            boolean gap;
            if (cross) {
                double[] k = crossAt(c, e.ax, e.az, x, z, R);
                gap = inGap(u, Rg, k[0], k[1]);
            } else gap = inGap(u, Rg, x, z);
            boolean out = r > R + thick;
            if (warm) {
                boolean passed = gap && (cross || out);
                if (passed) e.passed = true;
                if (passed || r < R) {
                    e.in = r < R;
                    e.ax = x;
                    e.az = z;
                }
                continue;
            }
            boolean touch = cross || Math.abs(r - R) <= thick;
            e.in = r < R;
            e.ax = x;
            e.az = z;
            if (gap) {
                if (touch || out) e.passed = true;
            } else if (touch || (out && !e.passed)) {
                crush(u, Rg, p, out ? "틈을 지나지 않고 고리 밖으로 나가려다 짓눌렸습니다" : "세계를 감는 고리에 짓눌렸습니다");
            }
        }
    }

    static void endRing(Bosses.Enc enc, Ring Rg) {
        M m = mem(enc);
        if (m != null) m.ring = null;
        if (Rg.prop != null) enc.api.dropProp(enc, Rg.prop);
    }

    // ------------------------------------------------------------ 독의 해자 · 높은 곳

    /** 해자 물에 들어가 있는 사람 (발이 바닥보다 낮음): 1초마다 마법 피해 */
    static void moatTick(Bosses.Enc enc, long now) {
        if (now % 20 != 7) return;
        Bosses.Unit u = enc.units.get(0);
        if (!u.alive || !Bosses.valid(u.boss)) return;
        M m = mem(enc);
        ArenaThemes.Isle s = ArenaThemes.isleSize(enc.key);
        Vec3 c = enc.center;
        for (ServerPlayer p : targets(u)) {
            double r = hdist(p.position(), c);
            if (r <= s.island() + 0.2 || r >= s.moat() + 0.3 || p.getY() > c.y - 0.4) continue;
            bossHit(u, p, u.def.obj("moat").num("damage"), Opt.of("cause", "magic", "noSource", true));
            mist(enc.dim, p.position(), 1.2, VENOM, 0.5);
            boolean marked = false;
            if (m.venom != null) for (Mark mk : m.venom.marks()) if (!mk.done && mk.p.getUUID().equals(p.getUUID())) marked = true;
            if (!marked) BossKit.hintNotify(p, "§2독의 해자 §7— 물에 있으면 1초마다 독 피해", 30);
            explain(u, "moat", "§2[보스] 독의 해자: §f해자 물에 들어가 있으면 1초마다 독 피해를 받습니다");
        }
    }

    /** 성문 · 담장 탑 위처럼 높은 곳 (바닥보다 high.above 칸 넘게) 에 서 있는 사람: 꼬리로 쳐서 떨어뜨림 (사람마다 cd 틱에 한 번) */
    static void highTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (now % 10 != 0 || m.ring != null) return;
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
            splash(enc.dim, p.position(), 1.8, FOAM, 0.6);
            play(enc.dim, "mob.enderdragon.flap", p.position(), 0.7, 1);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 1.1, dir.z * 1.1, 0.35)));
            calm(u, p);
            BossKit.hintFace(p, "§6§l꼬리에 맞아 떨어집니다", Opt.of("dist", 2.2, "up", 0.35));
            explain(u, "high", "§6[보스] §f성문·담장 탑 위처럼 높은 곳에 올라서면 요르문간드가 꼬리로 쳐서 떨어뜨립니다");
        }
    }

    // ------------------------------------------------------------ 모듈

    /** 원거리 견제 (v2.3.0): 세계를 감는 고리 · 아홉 걸음의 독 (표식이 붙은 사람은 움직이면 안 됨) · 독해 범람 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m == null || (m.ring == null && m.venom == null && m.flood == null);
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        Vec3 c = enc.center;
        // 해자에서 떠오르며 등장: 깨운 사람 쪽 (없으면 남쪽)
        Vec3 p = enc.starter != null && !enc.starter.isRemoved() ? enc.starter.position() : new Vec3(c.x, c.y, c.z + 8);
        double ang = Math.atan2(p.z - c.z, p.x - c.x);
        M m = new M();
        m.swim.dir = random() < 0.5 ? 1 : -1;
        m.swim.px = c.x;
        m.swim.pz = c.z;
        m.swim.ang = ang;
        m.next.put("bite", now + 30);
        for (String k : List.of("dash", "tail", "flood", "venom")) m.next.put(k, now + (long) d.obj(k).num("first"));
        m.bigUntil = now + 100;
        enc.mem = m;
        for (Bosses.Unit u : enc.units) u.mem = m;
        // 엔진 평타는 쓰지 않음 (목을 돌리는 물어뜯기는 모듈이)
        enc.meleeAt = Long.MAX_VALUE / 4;
        setOnCircle(enc, ang);
        enc.faceTo = m.swim.byaw;
        bodyTick(enc);
        enc.api.anim(enc, "emerge", null, COIL, null);
        splash(enc.dim, pivotOf(enc), 3.2, FOAM, 0.9);
        play(enc.dim, "random.splash", pivotOf(enc), 0.6, 1);
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
                case "bite" -> bite(u, now, list);
                case "dash" -> dash(u, now, list, false);
                case "tail" -> tailLash(u, now, list);
                case "flood" -> {
                    flood(u, now);
                    yield true;
                }
                case "venom" -> venomMark(u, now, list);
                case "ring" -> {
                    ring(u, now, 0);
                    yield true;
                }
                default -> false;
            };
        }
        double r = u.api.ratio(u);
        if (m.ring != null || m.flood != null) return false;
        // 큰 패턴: 세계를 감는 고리 → 아홉 걸음의 독 → 독해 범람 (서로 겹치지 않음)
        if (m.venom == null && now >= m.bigUntil) {
            List<Double> ringAt = d.obj("ring").numbersOrNull("at");
            if (ringAt != null) {
                for (Double at : ringAt) {
                    if (r <= at && !m.ringDone.contains(at)) {
                        m.ringDone.add(at);
                        ring(u, now, at);
                        return true;
                    }
                }
            }
            Obj vn = d.obj("venom");
            if (r <= vn.num("from") && now >= m.next.get("venom")) {
                if (venomMark(u, now, list)) {
                    m.next.put("venom", now + every(u, BossKit.pair(vn, "every")));
                    return true;
                }
            }
            Obj fl = d.obj("flood");
            if (r <= fl.num("from") && now >= m.next.get("flood")) {
                m.next.put("flood", now + every(u, BossKit.pair(fl, "every")));
                flood(u, now);
                return true;
            }
        }
        if (now >= m.next.get("dash")) {
            boolean center = m.floodDash;
            m.floodDash = false;
            if (dash(u, now, list, center)) {
                m.next.put("dash", now + every(u, BossKit.pair(d.obj("dash"), "every")));
                return true;
            }
            m.next.put("dash", now + 20);
        }
        if (now >= m.next.get("tail")) {
            if (tailLash(u, now, list)) {
                m.next.put("tail", now + every(u, BossKit.pair(d.obj("tail"), "every")));
                return true;
            }
            m.next.put("tail", now + 30);
        }
        // 평타: 목이 닿는 사람을 물어뜯음
        if (now >= m.next.get("bite") && bite(u, now, list)) {
            m.next.put("bite", now + (long) Js.round(d.obj("melee").num("every") * u.cdMult));
            return true;
        }
        return false;
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        M m = mem(enc);
        if (!enc.alive || !Bosses.valid(enc.boss) || m == null) return;
        ringTick(enc, now);
        floodTick(enc, now);
        venomTick(enc, now);
        moatTick(enc, now);
        highTick(enc, now);
        if (!enc.alive) return;
        // 몸: 고리 중에는 ringTick 이, 돌진 중에는 돌진 단계가 옮김. 다른 패턴 중에는 목만 돌리고, 패턴이 없으면 해자를 돎
        if (m.ring == null && !m.swim.charging) {
            if (enc.cast != null) neckTick(enc);
            else swimTick(enc, now);
        }
        bodyTick(enc);
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        if (m.ring != null) endRing(enc, m.ring);
        m.ring = null;
        m.flood = null;
        m.venom = null;
    }
}
