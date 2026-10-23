package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.effect;
import static mmorpg.fabric.boss.BossKit.fanMarks;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.groundUnder;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.inFan;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.meleeTarget;
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.pair;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rgb;
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
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 니드호그 (boss_nidhogg.js — v2.3.0, Lv.38): 수치는 config BOSSES.nidhogg.
 * 세계수의 뿌리 방에서 싸우는 흑룡. 움직이지 않는 보스 (중력 · 충돌 없음) 를 모듈이 매 틱 옮김 (엔진 pose — 설정 drive): 땅에서는 가장 가까운 사람 쪽으로 몸을 돌려 걷고,
 * 비상 때는 날아올라 필드 위를 돌다가 급강하. 몸 (엔티티 자리) = 몸통 가운데, 머리는 몸 앞 drive.head 칸.
 * 엔티티 속성: rpg:gait (0~1 걷는 빠르기 → stride 동작을 섞음), rpg:fly (0~1 날기 → fly 동작을 섞음, 쉬기 동작을 뺌)
 * <ul>
 * <li>물어뜯기 (평타): 몸 앞 부채꼴 (대상 쪽으로 몸을 돌리며) · 꼬리 후려치기: 등 뒤 부채꼴 (옆 · 뒤로 밀림)</li>
 * <li>독의 숨결: 앞쪽 한 사람을 가운데로 sweep° 를 한쪽에서 반대쪽으로 쓸어 가는 독 불길 (머리 앞 부채꼴이 돎, 맞으면 시듦). 쓸어 갈 곳 전체를 먼저 보여 줌</li>
 * <li>날갯짓 폭풍 (침묵): 날개를 펴고 바람 → 날개 밑 (inner 칸 안) 과 먼 곳 (outer 칸 밖) 은 안전, 그 사이는 피해 + 침묵 + 바깥으로 날아감 (도넛)</li>
 * <li>뿌리의 올가미 (속박): 멀리 있는 사람부터 발밑 원 → 세계수 뿌리가 솟아 피해 + 속박 (뿌리는 속박 동안 남음)</li>
 * <li>비상 (체력 75% · 40%): 날아올라 필드 위를 돌며 독의 비 (사람마다 발밑 원) 세 번 → 가장 많이 모인 곳에 급강하 (피해 + 띄움) → 내려앉음</li>
 * <li>독룡의 낙인 (체력 60% 아래, 즉사): 싸우는 사람 모두에게 낙인 (원이 따라다님, 다른 사람이 안에 들면 붉게) → 터질 때 원 둘 이상에 든 사람은 즉사, 하나면 피해.
 * 니드호그는 낙인 동안에도 싸움 (물어뜯기 · 꼬리 · 숨결), 폭풍 · 올가미 · 비상은 없음</li>
 * <li>높은 곳 (뿌리 · 담장 · 탑 위): 날개로 쳐서 떨어뜨림</li>
 * </ul>
 * 큰 패턴 (비상 · 낙인) 은 서로 겹치지 않음 (mem.bigUntil). 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기를 모듈이).
 * 자바에서 달라진 점: 애드온의 {@code u.cast.until = now} (비상 틀을 이 틱에 끝냄) 는 Cast.until 을 바꿀 수 없어 같은 이름의 0틱 틀로 바꿈 ({@link #endCast} — 같은 틱에 끝나고
 * 그 틱에는 패턴을 고르지 않음, 애드온과 같음). Infinity 틱은 Long.MAX_VALUE. 패턴 함수는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossNidhogg implements BossModule {
    static final String KEY = "nidhogg";
    static final double[] VENOM = {0.62, 0.95, 0.3};
    static final double[] PURPLE = {0.74, 0.3, 0.96};
    static final double[] RED = {1.0, 0.22, 0.14};
    static final double[] ORANGE = {1.0, 0.5, 0.14};
    static final double[] SAFE = {0.86, 1.0, 0.9};
    static final double[] BARK = {0.72, 0.52, 0.34};
    static final double[] DUST = {0.52, 0.46, 0.42};
    static final double[] DARK = {0.62, 0.08, 0.2};
    static final double[] WIND = {0.86, 0.84, 1.0};
    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;
    /** 애드온의 Infinity (틱) */
    static final long NEVER = Long.MAX_VALUE;

    /** 세계수 뿌리 소품 (뿌리의 올가미): 솟아오름 (rise) 은 나올 때 저절로 (리소스 팩 animate), 가라앉음 (sink) 은 스크립트가 */
    public static final String ROOTS_ID = "rpg:prop_nid_roots";
    static final String ROOTS_ANIM = "animation.rpg.prop.nid_roots";
    /** 떨어지는 독 방울의 중력 (칸/초², build.py NID_DROP_GRAVITY 와 같음) */
    public static final double DROP_GRAVITY = 30;
    /** 독 방울이 떨어지는 시간 (틱): 닿기 DROP_TICKS 틱 전에 DROP_HEIGHT 칸 위에서 멈춘 채 떨어지기 시작 → 닿을 때 바닥 */
    public static final int DROP_TICKS = 16;
    public static final double DROP_HEIGHT = 0.5 * DROP_GRAVITY * (DROP_TICKS / 20.0) * (DROP_TICKS / 20.0);
    /** 동작 틀: 급강하는 내려앉기가 이어받을 때까지 유지 */
    public static final String AIR = "rpg_air";

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (bite · tail · breath · gust · snare · flight · brand — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> propIds() {
        return List.of(ROOTS_ID);
    }

    // ------------------------------------------------------------ 기억

    /** 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 큰 패턴 쉼 · 알린 규칙 · 몸 · 비상 · 비상을 쓴 체력 · 낙인 · 뿌리 소품 · 높은 곳 (때린 틱 · 올라선 틱) */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new HashMap<>();
        public long bigUntil;
        public final Set<String> told = new HashSet<>();
        public final Body body = new Body();
        public Flight flight;
        public final Set<Double> flightDone = new HashSet<>();
        public Brand brand;
        public final List<Root> roots = new ArrayList<>();
        public final Map<UUID, Long> high = new HashMap<>();
        public final Map<UUID, Long> highSince = new HashMap<>();
    }

    /** 몸: 자리 (x, z — 바닥 높이 + 나는 높이 h), 몸 방향 yaw (라디안), 패턴 중 돌아볼 쪽 want, 걸음 · 날기 (지금 · 목표 · 보낸 값), 마지막으로 옮긴 모습 */
    public static final class Body {
        public double x;
        public double z;
        public double h;
        public double yaw;
        public Double want;
        public double gait;
        public double gaitTo;
        public double fly;
        public double flyTo;
        public Double gaitSent;
        public Double flySent;
        double[] placed;
    }

    /**
     * 비상: 단계 (arrive 등장 · rise 날아오름 · circle 돌기 · dive 급강하 · land 내려앉음) · 시작 틱 · 날아오른 자리 · 도는 각도 · 방향 · 독의 비 (횟수 · 다음 틱 · 떨어지는 것) ·
     * 급강하 자리 · 틱 · 시작 모습 · 내려앉는 끝, 등장 (time · h0)
     */
    public static final class Flight {
        public String stage;
        public long t0;
        public double x0;
        public double z0;
        public double ang;
        public int dir;
        public int volleys;
        public long nextVolley;
        public final List<Rain> rains = new ArrayList<>();
        public Vec3 target;
        public long diveAt;
        public long d0;
        public double fromX;
        public double fromZ;
        public double fromH;
        public long landUntil;
        public long time;
        public double h0;
    }

    /** 떨어지는 독의 비 하나: 자리 (서 있는 바닥) · 닿는 틱 · 방울이 떨어지기 시작하는 틱 */
    public record Rain(Vec3 at, long hitAt, long dropAt) {}

    /** 독룡의 낙인: 시작 · 터지는 틱 · 낙인들 */
    public static final class Brand {
        public final long t0;
        public final long until;
        public final List<BrandMark> marks;

        Brand(long t0, long until, List<BrandMark> marks) {
            this.t0 = t0;
            this.until = until;
            this.marks = marks;
        }
    }

    /** 낙인 하나 (사람 · 사라졌는지) */
    public static final class BrandMark {
        public final ServerPlayer p;
        public final UUID id;
        public boolean done;

        BrandMark(ServerPlayer p) {
            this.p = p;
            this.id = p.getUUID();
        }
    }

    /** 세계수 뿌리 소품: 가라앉는 틱 · 치우는 틱 */
    public static final class Root {
        public final Entity e;
        public final long sinkAt;
        public final long dropAt;
        public boolean sunk;

        Root(Entity e, long sinkAt, long dropAt) {
            this.e = e;
            this.sinkAt = sinkAt;
            this.dropAt = dropAt;
        }
    }

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    /** 시험용: 지금 낙인 (애드온 brandOf) */
    public static Brand brandOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.brand;
    }

    /** 시험용: 비상 상태 (애드온 flightOf) */
    public static Flight flightOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.flight;
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

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번 — 0.34.0 BOSS_HINTS.chat 일 때만 보냄, 알렸다는 표시 told 는 늘 남김) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        BossKit.hintSay(u, text);
    }

    static void shakeNear(Bosses.Unit u, Vec3 at, double range, double intensity, double seconds) {
        for (ServerPlayer p : fighters(u)) if (hdist(p.position(), at) <= range) SkillFx.shake(p, intensity, seconds);
    }

    /** 흙먼지 (rpg:dust) */
    static void dust(ServerLevel dim, Vec3 at, double size, double[] color, double life) {
        SkillFx.fx(dim, "rpg:dust", new Vec3(at.x, at.y + size * 0.35, at.z), Opt.of("size", size, "life", life, "color", color, "alpha", 0.8));
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

    /** 틀을 이 틱에 끝냄 (애드온 u.cast.until = now): 같은 이름의 0틱 틀로 바꿈 → 이 틱의 틀 차례에서 끝나고 그 틱에는 패턴을 고르지 않음 */
    static void endCast(Bosses.Unit u, String id) {
        if (u.cast != null && id.equals(u.cast.id)) u.api.cast(u, id, 0);
    }

    // ------------------------------------------------------------ 몸

    /** 몸 (엔티티 자리): 바닥 높이 + 나는 높이 h */
    public static Vec3 bodyOf(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y + b.h, b.z);
    }

    /** 머리 밑 바닥 (몸 앞 drive.head 칸, yaw = 몸 방향) */
    public static Vec3 headGround(Bosses.Unit u, double yaw) {
        Body b = mem(u).body;
        double H = u.def.obj("drive").num("head");
        return new Vec3(b.x + Math.cos(yaw) * H, u.center.y, b.z + Math.sin(yaw) * H);
    }

    public static Vec3 headGround(Bosses.Unit u) {
        return headGround(u, mem(u).body.yaw);
    }

    /** 머리 (연출 · 던지는 곳) */
    public static Vec3 headAt(Bosses.Unit u) {
        Vec3 g = headGround(u);
        return new Vec3(g.x, u.center.y + mem(u).body.h + 4.4, g.z);
    }

    /** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음: 서 있을 때 매 틱 옮기지 않게. 밀려난 것은 엔진이 10틱마다 되돌림) */
    static void place(Bosses.Unit u) {
        Body b = mem(u).body;
        double[] k = {b.x, b.z, b.h, b.yaw};
        if (b.placed != null && java.util.Arrays.equals(b.placed, k) && ok(u.boss)) return;
        b.placed = k;
        u.api.pose(u, new Vec3(b.x, u.center.y + b.h, b.z), b.yaw);
    }

    /** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게. 시험 · 미리보기도 씀 */
    public static void setBody(Bosses.Unit u, double x, double z, double yaw) {
        Body b = mem(u).body;
        Vec3 c = u.center;
        double K = u.def.obj("drive").num("keep");
        double r = Math.hypot(x - c.x, z - c.z);
        b.x = r > K ? c.x + ((x - c.x) / r) * K : x;
        b.z = r > K ? c.z + ((z - c.z) / r) * K : z;
        b.yaw = yaw;
        place(u);
    }

    /** 속성 보내기 (값이 바뀔 때만: 조금 바뀐 것은 보내지 않음) — 보낸 값 */
    static Double setProp(Bosses.Unit u, String id, double v, Double last) {
        if (last != null && Math.abs(v - last) < (id.equals("rpg:fly") ? 0.02 : 0.05)) return last;
        if (!ok(u.boss)) return last;
        u.boss.setProp(id, v);
        return v;
    }

    /** 매 틱: 걷는 빠르기 · 날기를 목표 쪽으로 (날기 1초에 걸쳐), 속성 보내기 */
    static void bodyTick(Bosses.Unit u) {
        Body b = mem(u).body;
        double gs = 0.15;
        b.gait = Math.abs(b.gaitTo - b.gait) <= gs ? b.gaitTo : b.gait + Math.signum(b.gaitTo - b.gait) * gs;
        double fs = 0.05;
        b.fly = Math.abs(b.flyTo - b.fly) <= fs ? b.flyTo : b.fly + Math.signum(b.flyTo - b.fly) * fs;
        b.gaitSent = setProp(u, "rpg:gait", Js.round(clamp(b.gait, 0, 1) * 100) / 100, b.gaitSent);
        b.flySent = setProp(u, "rpg:fly", Js.round(clamp(b.fly, 0, 1) * 100) / 100, b.flySent);
    }

    /** 걷기: 가장 가까운 사람 쪽으로 몸을 돌리고, 거의 그쪽을 보면 걸어감 (near 칸 안이면 멈춤, 가운데에서 keep 칸 밖으로는 나가지 않음) */
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
                double K = D.num("keep");
                double nx = b.x + Math.cos(b.yaw) * D.num("walk");
                double nz = b.z + Math.sin(b.yaw) * D.num("walk");
                double r = Math.hypot(nx - c.x, nz - c.z);
                if (r > K) {
                    nx = c.x + ((nx - c.x) / r) * K;
                    nz = c.z + ((nz - c.z) / r) * K;
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
        if (b.gaitTo > 0 && Tasks.now() % 12 == 0) play(u.dim, "mob.ravager.step", bodyOf(u), 0.5, 0.8);
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

    // ------------------------------------------------------------ 물어뜯기 (평타)

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
        mem(u).body.want = angOf(dir);
        fanMarks(u.dim, apex, dir, Js.round(reach), cone, windup, RED);
        u.api.anim(u, m.str("anim"));
        sound(u, snd(u, "swing"), headAt(u), 0.8, 1);
        u.api.cast(u, "bite", windup + 6, step(windup, (x, n) -> biteHit(x, apex, dir, reach)));
        return true;
    }

    static void biteHit(Bosses.Unit u, Vec3 apex, Vec3 dir, double radius) {
        Obj m = u.def.obj("melee");
        for (ServerPlayer p : targets(u)) {
            if (Math.abs(p.getY() - apex.y) > 4 || !inFan(p.position(), apex, dir, radius, m.num("cone"), 0.5)) continue;
            double[] kk = keepIn(u, p, dir.x * 1.2, dir.z * 1.2);
            bossHit(u, p, m.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.35), "directional", true));
        }
        sound(u, snd(u, "hit"), headAt(u), 0.9, 1);
        Vec3 h = headAt(u);
        SkillFx.fx(u.dim, "rpg:slash_heavy_r_glow", new Vec3(h.x, h.y - 2.4, h.z), Opt.of("dir", dir, "speed", 0.0, "spin", 0.0, "size", 3.2, "life", 0.25, "color",
                rgb(u.def, "color"), "alpha", 0.95));
    }

    // ------------------------------------------------------------ 꼬리 후려치기 (등 뒤)

    public static boolean tailWhip(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("tail");
        Vec3 b = bodyOf(u);
        double back = mem(u).body.yaw + Math.PI;
        double reach = d.num("reach");
        double cone = d.num("cone");
        int windup = (int) d.num("windup");
        boolean any = false;
        for (ServerPlayer p : list) {
            if (hdist(p.position(), b) <= reach && Math.abs(p.getY() - u.center.y) < 4 && Math.abs(wrapPi(angOf(dirTo(b, p.position())) - back)) <= (cone / 2) * DEG) {
                any = true;
                break;
            }
        }
        if (!any) return false;
        Vec3 dir = dirOf(back);
        Vec3 apex = floorAt(u, b.x, b.z);
        mem(u).body.want = null;
        fanMarks(u.dim, apex, dir, Js.round(reach), cone, windup, ORANGE, 1.5);
        u.api.anim(u, "tail");
        sound(u, "mob.enderdragon.flap", b, 0.7, 1);
        explain(u, "tail", "§6[보스] 꼬리 후려치기: §f니드호그 등 뒤(주황 부채꼴)에 있으면 꼬리에 맞아 옆으로 날아갑니다");
        u.api.cast(u, "tail", windup + 8, step(windup, (x, n) -> tailHit(x, apex, dir)));
        return true;
    }

    static void tailHit(Bosses.Unit u, Vec3 apex, Vec3 dir) {
        Obj d = u.def.obj("tail");
        double K = d.num("knock");
        // 꼬리가 도는 쪽 (tail 동작: 먼저 한쪽으로 감았다가 반대쪽으로 휘두름) → 옆으로 + 조금 뒤로
        double sx = -dir.z;
        double sz = dir.x;
        for (ServerPlayer p : targets(u)) {
            if (Math.abs(p.getY() - apex.y) > 4 || !inFan(p.position(), apex, dir, d.num("reach"), d.num("cone"), 0.5)) continue;
            double lat = (p.getX() - apex.x) * sx + (p.getZ() - apex.z) * sz;
            double sg = lat >= 0 ? 1 : -1;
            double[] kk = keepIn(u, p, (sx * sg + dir.x * 0.4) * K, (sz * sg + dir.z * 0.4) * K);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.4)));
        }
        play(u.dim, "mob.enderdragon.hit", apex, 0.8, 1);
        for (int i = 1; i <= 4; i++) dust(u.dim, floorAt(u, apex.x + dir.x * i * 2, apex.z + dir.z * i * 2), 1.6, DUST, 0.6);
    }

    // ------------------------------------------------------------ 독의 숨결 (쓸어 가는 부채꼴)

    /** 쓸어 가기: 시작 각도 a0 → 끝 a1 (라디안), 도는 쪽 */
    public record Sweep(double a0, double a1, int side) {}

    /** 숨결 부채꼴 (몸 방향 yaw 일 때): 머리 밑에서 yaw 쪽으로 range 칸, 폭 cone° */
    private record Cone(Vec3 apex, Vec3 dir, double range, double cone) {}

    static Cone breathCone(Bosses.Unit u, double yaw) {
        Obj d = u.def.obj("breath");
        return new Cone(headGround(u, yaw), dirOf(yaw), d.num("range"), d.num("cone"));
    }

    /** 쓸어 가는 동안 한 번이라도 불길에 드는 곳인지 (a0 → a1, 4° 간격으로) */
    static boolean inSweep(Bosses.Unit u, Sweep B, Vec3 l) {
        int n = Math.max(2, (int) Math.ceil(Math.abs(B.a1() - B.a0()) / (4 * DEG)));
        for (int i = 0; i <= n; i++) {
            Cone c = breathCone(u, B.a0() + ((B.a1() - B.a0()) * i) / n);
            if (inFan(l, c.apex(), c.dir(), c.range(), c.cone(), 0)) return true;
        }
        return false;
    }

    /** 시험용: 쓸어 가는 동안 불길이 닿는 곳인지 (애드온 sweepCovers) */
    public static boolean sweepCovers(Bosses.Unit u, double a0, double a1, Vec3 l) {
        return inSweep(u, new Sweep(a0, a1, 1), l);
    }

    /** 예고: 쓸어 갈 곳 전체 (몸 둘레 극좌표 격자 중 불길이 닿는 곳), 불길이 시작하는 쪽 가장자리는 밝게 */
    static void breathMarks(Bosses.Unit u, Sweep B, int ticks) {
        Obj d = u.def.obj("breath");
        Body b = mem(u).body;
        Vec3 c = u.center;
        double life = Math.max(0.1, ticks / 20.0);
        double step = 1.8;
        double R = u.def.obj("drive").num("head") + d.num("range");
        double lo = Math.min(B.a0(), B.a1()) - (d.num("cone") / 2 + 8) * DEG;
        double hi = Math.max(B.a0(), B.a1()) + (d.num("cone") / 2 + 8) * DEG;
        for (double r = step; r <= R + 0.01; r += step) {
            int n = Math.max(2, (int) Math.ceil(((hi - lo) * r) / step));
            for (int i = 0; i <= n; i++) {
                double a = lo + ((hi - lo) * i) / n;
                Vec3 at = new Vec3(b.x + Math.cos(a) * r, c.y, b.z + Math.sin(a) * r);
                if (!inSweep(u, B, at)) continue;
                boolean start = Math.abs(wrapPi(a - B.a0())) < 12 * DEG;
                SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(at.x, c.y + 0.07, at.z), Opt.of("dir", Vec3.ZERO, "size", step * 0.8, "life", life, "color", PURPLE, "alpha",
                        start ? 0.8 : 0.42, "grow", 1.0));
            }
        }
    }

    public static boolean breath(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("breath");
        Body b = mem(u).body;
        Vec3 B0 = bodyOf(u);
        double R = u.def.obj("drive").num("head") + d.num("range");
        List<ServerPlayer> front = new ArrayList<>();
        for (ServerPlayer p : list) {
            if (offAngle(u, p.position()) <= 100 * DEG && hdist(p.position(), B0) <= R && Math.abs(p.getY() - u.center.y) < 4) front.add(p);
        }
        if (front.isEmpty()) return false;
        ServerPlayer t = shuffle(front).get(0);
        double aim = angOf(dirTo(B0, t.position()));
        int side = random() < 0.5 ? 1 : -1;
        double half = (d.num("sweep") * DEG) / 2;
        Sweep B = new Sweep(aim - side * half, aim + side * half, side);
        b.want = B.a0();
        int windup = (int) d.num("windup");
        int time = (int) d.num("time");
        breathMarks(u, B, windup + time);
        u.api.anim(u, "breath");
        sound(u, "mob.enderdragon.growl", headAt(u), 1.2, 0.8);
        explain(u, "breath", "§5[보스] 독의 숨결: §f보라 부채꼴을 밝은 쪽 끝에서 반대쪽 끝으로 쓸어 가며 독 불길을 뿜습니다. §e니드호그 뒤나 목 밑, 불길이 지나간 쪽으로 피하세요 §7(맞으면 시듦)");
        List<Bosses.Step> steps = new ArrayList<>();
        steps.add(step(windup, (x, n) -> breathStart(x)));
        for (int k = 0; k <= time; k++) {
            int kk = k;
            steps.add(step(windup + k, (x, n) -> breathStep(x, B, kk)));
        }
        u.api.cast(u, "breath", windup + time + 8, steps);
        return true;
    }

    static void breathStart(Bosses.Unit u) {
        play(u.dim, "mob.enderdragon.growl", headAt(u), 0.7, 1);
        shakeNear(u, bodyOf(u), 20, 0.12, 0.5);
    }

    static void breathStep(Bosses.Unit u, Sweep B, int k) {
        Obj d = u.def.obj("breath");
        Body b = mem(u).body;
        // 몸 방향만 정함 (엔티티는 다음 틱 standTick 이 옮김: 한 틱에 한 번만 옮김)
        b.want = null;
        b.yaw = B.a0() + ((B.a1() - B.a0()) * k) / d.num("time");
        Cone c = breathCone(u, b.yaw);
        Vec3 h = headAt(u);
        // 불길: 머리에서 퍼져 나가는 독 구름 (부채꼴 폭 안에서 흩어짐)
        if (k % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double a = b.yaw + (random() - 0.5) * d.num("cone") * DEG;
                Vec3 dir = new Vec3(Math.cos(a), -0.22, Math.sin(a));
                SkillFx.fx(u.dim, "rpg:breath", new Vec3(h.x - 0.6 + Math.cos(b.yaw) * 1.2, h.y - 1.6, h.z + Math.sin(b.yaw) * 1.2), Opt.of("dir", dir, "speed",
                        11 + random() * 4, "spin", random() * 360, "size", 1.4 + random() * 0.6, "life", 1.3, "color", i == 0 ? VENOM : PURPLE, "alpha", 0.9));
            }
        }
        if (k % 4 == 0) play(u.dim, "mob.blaze.shoot", h, 0.5, 0.6);
        // 불길 판정은 tick 틱마다 (한 사람이 불길 안에 있으면 tick 틱에 한 번 = 바닐라 무적 시간과 같게)
        if (k % (int) d.num("tick") != 0) return;
        for (ServerPlayer p : targets(u)) {
            if (Math.abs(p.getY() - u.center.y) > 4 || !inFan(p.position(), c.apex(), c.dir(), c.range(), c.cone(), 0.4)) continue;
            bossHit(u, p, d.num("damage"), Opt.of("directional", true, "effect", effect("wither", (int) d.num("wither"), 0)));
        }
    }

    // ------------------------------------------------------------ 날갯짓 폭풍 (도넛, 침묵)

    static void gustRingMarks(Bosses.Unit u, Vec3 at, int ticks) {
        Obj d = u.def.obj("gust");
        double life = Math.max(0.1, ticks / 20.0);
        double outer = d.num("outer");
        for (double r = d.num("inner") + 1.2; r <= outer + 0.01; r += 2.4) {
            int n = (int) Math.ceil((TAU * r) / 2.6);
            for (int i = 0; i < n; i++) {
                double a = ((double) i / n) * TAU + r;
                SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(at.x + Math.cos(a) * r, u.center.y + 0.07, at.z + Math.sin(a) * r), Opt.of("dir", Vec3.ZERO, "size", 1.4,
                        "life", life, "color", PURPLE, "alpha", 0.4));
            }
        }
        SkillFx.fx(u.dim, "rpg:tele_disc", new Vec3(at.x, u.center.y + 0.06, at.z), Opt.of("dir", Vec3.ZERO, "size", outer, "life", life, "color", PURPLE, "alpha", 0.9));
    }

    /** 날갯짓 폭풍에 드는 거리인지 (inner 칸 밖 ~ outer 칸 안) */
    static boolean inGust(Obj d, double r) {
        return r > d.num("inner") && r <= d.num("outer");
    }

    public static boolean gust(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("gust");
        Vec3 b = bodyOf(u);
        List<ServerPlayer> mid = new ArrayList<>();
        for (ServerPlayer p : list) if (inGust(d, hdist(p.position(), b))) mid.add(p);
        if (mid.isEmpty()) return false;
        Vec3 at = floorAt(u, b.x, b.z);
        int windup = (int) d.num("windup");
        mem(u).body.want = null;
        disc(u.dim, at, d.num("inner"), windup, SAFE, Opt.of("fill", false, "alpha", 0.95));
        gustRingMarks(u, at, windup);
        u.api.anim(u, "gust");
        sound(u, snd(u, "roar"), headAt(u), 0.9, 1);
        play(u.dim, "mob.enderdragon.flap", b, 0.6, 1);
        BossKit.hintFaceAll(u, "§f§l날개 밑으로! §7또는 멀리", Opt.of("dist", 2.4, "up", 0.45), mid);
        explain(u, "gust", "§f[보스] 날갯짓 폭풍: §f날개를 펴고 바람을 일으킵니다. §e날개 밑(초록 원, " + Js.str(d.num("inner")) + "칸 안)이나 먼 곳(" + Js.str(d.num("outer"))
                + "칸 밖)은 안전§f, 그 사이에 있으면 날아가며 §5침묵§f(스킬·룬 봉인)");
        u.api.cast(u, "gust", windup + 12, step(windup, (x, n) -> gustHit(x, at)));
        return true;
    }

    static void gustHit(Bosses.Unit u, Vec3 at) {
        Obj d = u.def.obj("gust");
        double inner = d.num("inner");
        double outer = d.num("outer");
        for (ServerPlayer p : targets(u)) {
            double r = hdist(p.position(), at);
            if (r <= inner + 0.2 || r > outer + 0.3 || Math.abs(p.getY() - u.center.y) > 4) continue;
            Vec3 dir = dirTo(at, p.position());
            double[] kk = keepIn(u, p, dir.x * d.num("knock"), dir.z * d.num("knock"));
            boolean landed = bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.5)));
            if (landed) {
                Status.silence(p, (int) d.num("silence"));
                BossKit.hintFace(p, "§5§l침묵! §7바람에 숨이 막혔습니다", Opt.of("dist", 2.2, "up", 0.35));
            }
        }
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(at.x, at.y + 0.2, at.z), Opt.of("dir", Vec3.ZERO, "speed", 24.0, "size", 1.0, "life", outer / 24, "color", WIND, "alpha",
                0.9));
        for (int i = 0; i < 12; i++) {
            double a = (i / 12.0) * TAU;
            dust(u.dim, floorAt(u, at.x + Math.cos(a) * (inner + 1.5), at.z + Math.sin(a) * (inner + 1.5)), 2.2, WIND, 0.7);
        }
        play(u.dim, "mob.enderdragon.flap", at, 0.5, 1);
        shakeNear(u, at, outer + 4, 0.25, 0.6);
    }

    // ------------------------------------------------------------ 뿌리의 올가미 (속박)

    public static boolean snare(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("snare");
        Vec3 b = bodyOf(u);
        List<ServerPlayer> farList = new ArrayList<>();
        List<ServerPlayer> nearList = new ArrayList<>();
        for (ServerPlayer p : list) (hdist(p.position(), b) > d.num("far") ? farList : nearList).add(p);
        List<ServerPlayer> chosen = new ArrayList<>(shuffle(farList));
        chosen.addAll(shuffle(nearList));
        int count = (int) d.num("count");
        if (chosen.size() > count) chosen = new ArrayList<>(chosen.subList(0, count));
        if (chosen.isEmpty()) return false;
        // 발밑 = 서 있는 바닥 (뿌리 위에 서 있으면 뿌리 위)
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : chosen) spots.add(new Vec3(p.getX(), groundUnder(p, u.center.y), p.getZ()));
        mem(u).body.want = null;
        double radius = d.num("radius");
        int windup = (int) d.num("windup");
        for (Vec3 s : spots) {
            disc(u.dim, s, radius, windup, BARK);
            SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(s.x, s.y + 0.05, s.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2.2, "life",
                    windup / 20.0 + 0.4, "color", new double[] {0.35, 0.22, 0.14}, "alpha", 0.85));
        }
        u.api.anim(u, "gnaw");
        sound(u, "mob.ravager.bite", headAt(u), 0.6, 1);
        explain(u, "snare", "§6[보스] 뿌리의 올가미: §f니드호그가 뿌리를 갉으면 멀리 있는 사람 발밑(갈색 원)에서 세계수 뿌리가 솟아 §e속박§f합니다 — 원 밖으로");
        u.api.cast(u, "snare", windup + 8, step(windup, (x, n) -> snareHit(x, spots)));
        return true;
    }

    static void snareHit(Bosses.Unit u, List<Vec3> spots) {
        Obj d = u.def.obj("snare");
        long now = Tasks.now();
        int rootT = (int) d.num("root");
        Set<UUID> hit = new HashSet<>();
        M m = mem(u);
        for (Vec3 s : spots) {
            Vec3 facing = new Vec3(s.x + random() - 0.5, s.y, s.z + 1);
            BedrockMob e = BossKit.spawn(u.dim, ROOTS_ID, s, BossKit.yawTo(s, facing));
            if (e != null) {
                u.api.addProp(u.enc, e);
                if (m != null) m.roots.add(new Root(e, now + rootT + 4, now + rootT + 16));
            }
            dust(u.dim, s, 2.4, BARK, 0.7);
            play(u.dim, "dig.wood", s, 0.6, 1);
            for (ServerPlayer p : targets(u)) {
                if (hit.contains(p.getUUID()) || hdist(p.position(), s) > d.num("radius") + 0.3 || Math.abs(p.getY() - s.y) > 3) continue;
                hit.add(p.getUUID());
                bossHit(u, p, d.num("damage"), Opt.of("noSource", true));
                Status.root(p, rootT, "뿌리에 휘감김");
            }
        }
    }

    /** 뿌리 소품 동작: 가라앉음은 마지막 모습 (땅속) 을 사라질 때까지 유지 */
    static void rootsAnim(Entity e, String name) {
        if (e instanceof BedrockMob b && !b.isRemoved()) b.playAnimation(ROOTS_ANIM + "." + name, "rpg_roots", "0");
    }

    /** 뿌리 소품: 속박이 끝나면 가라앉고 사라짐 */
    static void rootsTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        List<Root> R = m.roots;
        for (int i = R.size() - 1; i >= 0; i--) {
            Root r = R.get(i);
            if (!ok(r.e)) {
                R.remove(i);
                continue;
            }
            if (now >= r.sinkAt && !r.sunk) {
                r.sunk = true;
                rootsAnim(r.e, "sink");
            }
            if (now >= r.dropAt) {
                enc.api.dropProp(enc, r.e);
                R.remove(i);
            }
        }
    }

    // ------------------------------------------------------------ 비상 (날아오름 → 독의 비 → 급강하 → 내려앉음)

    public static void takeoff(Bosses.Unit u, long now) {
        Obj F = u.def.obj("flight");
        M m = mem(u);
        Body b = m.body;
        Vec3 c = u.center;
        double r = Math.hypot(b.x - c.x, b.z - c.z);
        Flight fl = new Flight();
        fl.stage = "rise";
        fl.t0 = now;
        fl.x0 = b.x;
        fl.z0 = b.z;
        fl.ang = r > 0.5 ? Math.atan2(b.z - c.z, b.x - c.x) : b.yaw;
        fl.dir = random() < 0.5 ? 1 : -1;
        fl.volleys = 0;
        fl.nextVolley = now + (long) F.num("rise") + (long) F.num("first");
        fl.target = null;
        fl.diveAt = 0;
        m.flight = fl;
        m.bigUntil = NEVER;
        b.flyTo = 1;
        b.gaitTo = 0;
        b.want = null;
        u.api.anim(u, "takeoff");
        sound(u, snd(u, "roar"), headAt(u), 0.8, 1);
        play(u.dim, "mob.enderdragon.flap", bodyOf(u), 0.6, 1);
        Vec3 at = floorAt(u, b.x, b.z);
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(at.x, at.y + 0.2, at.z), Opt.of("dir", Vec3.ZERO, "speed", 14.0, "size", 1.0, "life", 0.8, "color", WIND, "alpha", 0.85));
        for (int i = 0; i < 10; i++) {
            double a = (i / 10.0) * TAU;
            dust(u.dim, floorAt(u, at.x + Math.cos(a) * 4, at.z + Math.sin(a) * 4), 2.6, DUST, 0.9);
        }
        BossKit.hintTitle(u, "§5§l니드호그가 날아오릅니다", "§f독의 비를 피하고 §c한곳에 모여 있지 마세요", fighters(u));
        explain(u, "flight", "§5[보스] 비상: §f니드호그가 날아올라 필드 위를 돌며 독의 비(초록 원)를 세 번 떨어뜨리고, §c가장 많이 모인 곳§f으로 급강하합니다 §7— 날아 있는 동안 근접 공격은 닿지 않음 (활·완드·멀리 쏘는 스킬은 닿음)");
        u.api.cast(u, "flight", 100000);
    }

    /** 독의 비 한 번: 싸우는 사람마다 발밑 원 → windup 틱 뒤 떨어짐 */
    static void rainVolley(Bosses.Unit u, long now) {
        Obj d = u.def.obj("flight").obj("rain");
        Flight fl = mem(u).flight;
        Vec3 h = headAt(u);
        int windup = (int) d.num("windup");
        for (ServerPlayer p : targets(u)) {
            // 서 있는 바닥에 (뿌리 · 담장 위에 올라가 있어도 비를 피하지 못함)
            Vec3 s = new Vec3(p.getX(), groundUnder(p, u.center.y), p.getZ());
            disc(u.dim, s, d.num("radius"), windup, VENOM);
            fl.rains.add(new Rain(s, now + windup, now + Math.max(0, windup - DROP_TICKS)));
            SkillFx.beam(u.dim, h, new Vec3(s.x, s.y + 0.5, s.z), VENOM, Opt.of("half", 0.3, "thick", 0.1, "life", 0.35, "gap", 0.8));
        }
        play(u.dim, "mob.enderdragon.growl", h, 1.4, 0.6);
    }

    /** 떨어지는 독: 떨어지기 시작 (방울) → 닿음 (원 안: 피해 + 독) */
    static void rainTick(Bosses.Unit u, long now) {
        Obj d = u.def.obj("flight").obj("rain");
        Flight fl = mem(u).flight;
        double radius = d.num("radius");
        for (int i = fl.rains.size() - 1; i >= 0; i--) {
            Rain r = fl.rains.get(i);
            if (now == r.dropAt()) {
                // 독 방울 셋: 닿는 틱에 바닥에 닿게 (중력 DROP_GRAVITY 로 DROP_TICKS 틱 동안 DROP_HEIGHT 칸)
                double t = (r.hitAt() - now) / 20.0;
                double h = 0.5 * DROP_GRAVITY * t * t;
                for (int k = 0; k < 3; k++) {
                    SkillFx.fx(u.dim, "rpg:drop", new Vec3(r.at().x + (random() - 0.5) * 1.2, r.at().y + h, r.at().z + (random() - 0.5) * 1.2), Opt.of("dir",
                            new Vec3(0, -1, 0), "speed", 0.0, "size", 0.9, "life", t, "color", VENOM, "alpha", 0.95));
                }
            }
            if (now < r.hitAt()) continue;
            fl.rains.remove(i);
            SkillFx.fx(u.dim, "rpg:splash", new Vec3(r.at().x, r.at().y + radius * 0.75, r.at().z), Opt.of("size", radius, "life", 0.6, "color", VENOM, "alpha", 0.95));
            dust(u.dim, r.at(), 2, VENOM, 0.8);
            play(u.dim, "random.fizz", r.at(), 0.8, 0.8);
            for (ServerPlayer p : targets(u)) {
                if (hdist(p.position(), r.at()) > radius + 0.3 || Math.abs(p.getY() - r.at().y) > 3) continue;
                bossHit(u, p, d.num("damage"), Opt.of("noSource", true, "effect", effect("poison", (int) d.num("poison"), 0)));
            }
        }
    }

    /** 급강하할 곳: 싸우는 사람 중 dive.radius 칸 안에 다른 사람이 가장 많은 사람 자리 (몸이 설 수 있게 keep 칸 안으로) */
    public static Vec3 diveSpot(Bosses.Unit u, List<ServerPlayer> list) {
        double R = u.def.obj("flight").obj("dive").num("radius");
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
        Vec3 c = u.center;
        if (best == null) return floorAt(u, c.x, c.z);
        double K = u.def.obj("drive").num("keep");
        double x = best.getX() - c.x;
        double z = best.getZ() - c.z;
        double r = Math.hypot(x, z);
        return r > K ? floorAt(u, c.x + (x / r) * K, c.z + (z / r) * K) : floorAt(u, best.getX(), best.getZ());
    }

    static void flightTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Flight fl = m.flight;
        if (fl == null) return;
        Bosses.Unit u = enc;
        Obj F = u.def.obj("flight");
        Body b = m.body;
        Vec3 c = u.center;
        if ("arrive".equals(fl.stage)) {
            // 등장: 하늘에서 내려앉음
            double k = clamp((double) (now - fl.t0) / fl.time, 0, 1);
            b.h = fl.h0 * (1 - k) * (1 - k);
            if (k >= 0.75) b.flyTo = 0;
            if (now == fl.t0 + (long) Js.round(fl.time * 0.7)) u.api.anim(u, "land", null, AIR, null);
            place(u);
            if (k >= 1) {
                m.flight = null;
                dust(u.dim, floorAt(u, b.x, b.z), 4, DUST, 1);
                play(u.dim, "mob.ravager.step", bodyOf(u), 0.5, 1);
            }
            return;
        }
        if ("rise".equals(fl.stage)) {
            double k = clamp((now - fl.t0) / F.num("rise"), 0, 1);
            double e = k * k * (3 - 2 * k);
            double tx = c.x + Math.cos(fl.ang) * F.num("circle");
            double tz = c.z + Math.sin(fl.ang) * F.num("circle");
            b.x = fl.x0 + (tx - fl.x0) * e;
            b.z = fl.z0 + (tz - fl.z0) * e;
            b.h = F.num("height") * e;
            double tan = fl.ang + (fl.dir * Math.PI) / 2;
            b.yaw = turnToward(b.yaw, Math.hypot(tx - b.x, tz - b.z) > 1.5 ? Math.atan2(tz - b.z, tx - b.x) : tan, 6 * DEG);
            if (now % 8 == 0) play(u.dim, "mob.enderdragon.flap", bodyOf(u), 0.7, 0.8);
            if (k >= 1) fl.stage = "circle";
            place(u);
            return;
        }
        if ("circle".equals(fl.stage)) {
            double circle = F.num("circle");
            fl.ang = wrap(fl.ang + (fl.dir * F.num("speed")) / circle);
            b.x = c.x + Math.cos(fl.ang) * circle;
            b.z = c.z + Math.sin(fl.ang) * circle;
            b.h = F.num("height");
            b.yaw = turnToward(b.yaw, fl.ang + (fl.dir * Math.PI) / 2, 6 * DEG);
            if (now % 10 == 0) play(u.dim, "mob.enderdragon.flap", bodyOf(u), 0.8, 0.7);
            if (fl.volleys < F.num("volleys") && now >= fl.nextVolley) {
                rainVolley(u, now);
                fl.volleys += 1;
                fl.nextVolley = now + (long) F.num("gap");
            }
            rainTick(u, now);
            Obj D = F.obj("dive");
            if (fl.volleys >= F.num("volleys") && fl.rains.isEmpty() && fl.target == null) {
                int windup = (int) D.num("windup");
                double radius = D.num("radius");
                fl.target = diveSpot(u, targets(u));
                fl.diveAt = now + windup;
                Vec3 t = fl.target;
                disc(u.dim, t, radius, windup, DARK);
                SkillFx.fx(u.dim, "rpg:tele_disc", new Vec3(t.x, t.y + 0.08, t.z), Opt.of("dir", Vec3.ZERO, "size", radius + 0.4, "life", windup / 20.0, "color", RED,
                        "alpha", 0.95));
                sound(u, snd(u, "roar"), headAt(u), 0.7, 1);
                List<ServerPlayer> near = new ArrayList<>();
                for (ServerPlayer p : targets(u)) if (hdist(p.position(), t) <= radius + 3) near.add(p);
                BossKit.hintFaceAll(u, "§4§l급강하! §f검붉은 원 밖으로", Opt.of("dist", 2.4, "up", 0.45), near);
            }
            if (fl.target != null && now >= fl.diveAt - (long) D.num("time")) {
                fl.stage = "dive";
                fl.d0 = now;
                fl.fromX = b.x;
                fl.fromZ = b.z;
                fl.fromH = b.h;
                u.api.anim(u, "dive", null, AIR, "0");
            }
            place(u);
            return;
        }
        if ("dive".equals(fl.stage)) {
            double T = F.obj("dive").num("time");
            double k = clamp((now - fl.d0) / T, 0, 1);
            Vec3 t = fl.target;
            b.x = fl.fromX + (t.x - fl.fromX) * k;
            b.z = fl.fromZ + (t.z - fl.fromZ) * k;
            b.h = fl.fromH * (1 - k);
            if (Math.hypot(t.x - fl.fromX, t.z - fl.fromZ) > 0.5) b.yaw = turnToward(b.yaw, Math.atan2(t.z - fl.fromZ, t.x - fl.fromX), 12 * DEG);
            place(u);
            if (k >= 1) {
                diveHit(u, t);
                fl.stage = "land";
                fl.landUntil = now + (long) F.num("land");
                b.h = 0;
                b.flyTo = 0;
                u.api.anim(u, "land", null, AIR, null);
            }
            return;
        }
        if ("land".equals(fl.stage)) {
            b.h = 0;
            place(u);
            if (now >= fl.landUntil) {
                m.flight = null;
                m.bigUntil = now + 80;
                endCast(u, "flight");
            }
        }
    }

    static void diveHit(Bosses.Unit u, Vec3 at) {
        Obj D = u.def.obj("flight").obj("dive");
        double radius = D.num("radius");
        for (ServerPlayer p : targets(u)) {
            double r = hdist(p.position(), at);
            // 큰 몸이 내려꽂힘: 바닥에서 6칸 위 (뿌리 위) 까지 닿음
            if (r > radius + 0.3 || p.getY() - at.y > 6 || p.getY() - at.y < -3) continue;
            Vec3 dir = dirTo(at, p.position());
            double[] kk = keepIn(u, p, dir.x * 0.9, dir.z * 0.9);
            bossHit(u, p, D.num("damage"), Opt.of("knock", knock(kk[0], kk[1], D.num("lift"))));
        }
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", 0.7, "color", rgb(u.def, "color"), "alpha",
                0.95));
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1.5, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 4.0, "life", 0.5, "color", DARK, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2.2, "life", 1.6, "color",
                new double[] {0.3, 0.12, 0.16}, "alpha", 0.9));
        for (int i = 0; i < 12; i++) {
            double a = (i / 12.0) * TAU;
            dust(u.dim, floorAt(u, at.x + Math.cos(a) * radius * 0.8, at.z + Math.sin(a) * radius * 0.8), 2.8, DUST, 1);
        }
        play(u.dim, "random.explode", at, 0.6, 1);
        play(u.dim, "mob.enderdragon.hit", at, 0.5, 1);
        shakeNear(u, at, 24, 0.45, 0.9);
    }

    // ------------------------------------------------------------ 독룡의 낙인 (즉사: 흩어지기)

    public static boolean brand(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("brand");
        if (list.isEmpty()) return false;
        M m = mem(u);
        List<BrandMark> marks = new ArrayList<>();
        for (ServerPlayer p : list) marks.add(new BrandMark(p));
        long time = (long) d.num("time");
        m.brand = new Brand(now, now + time, marks);
        m.bigUntil = now + time + 60;
        u.api.anim(u, "roar");
        sound(u, snd(u, "roar"), headAt(u), 0.6, 1);
        play(u.dim, "mob.wither.spawn", u.center, 1.2, 0.5);
        for (BrandMark mk : marks) {
            SkillFx.beam(u.dim, headAt(u), new Vec3(mk.p.getX(), mk.p.getY() + 1.4, mk.p.getZ()), PURPLE, Opt.of("half", 0.4, "thick", 0.14, "life", 0.6, "gap", 0.6));
        }
        String radius = Js.str(d.num("radius"));
        BossKit.hintTitle(u, "§4§l독룡의 낙인", "§f서로 §c" + radius + "칸§f 넘게 흩어지세요! §7다른 사람의 원 안 = 즉사", list);
        explain(u, "brand", "§4[보스] 독룡의 낙인: §f모두에게 낙인이 붙고 " + Js.str(Js.round(time / 20.0)) + "초 뒤 터집니다 (반지름 " + radius
                + "칸). §c다른 사람의 낙인 원 안에 있으면 즉사 §7— 서로 흩어지세요 (원이 붉게 보이면 위험). 원 하나만 맞으면 피해");
        return true;
    }

    /** q 가 at 에서 터지는 낙인 원 안인지 (반지름 brand.radius, 위아래 4칸 안) */
    static boolean inBrand(Bosses.Unit u, ServerPlayer q, Vec3 at) {
        return hdist(q.position(), at) <= u.def.obj("brand").num("radius") && Math.abs(q.getY() - at.y) < 4;
    }

    /** 지금 낙인이 남은 사람 */
    static List<BrandMark> liveMarks(Brand B) {
        List<BrandMark> out = new ArrayList<>();
        for (BrandMark mk : B.marks) if (!mk.done && ok(mk.p) && okTarget(mk.p)) out.add(mk);
        return out;
    }

    static void brandTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Brand B = m.brand;
        if (B == null) return;
        Bosses.Unit u = enc;
        Obj d = u.def.obj("brand");
        double radius = d.num("radius");
        if (!u.alive || !ok(u.boss)) {
            m.brand = null;
            return;
        }
        // 쓰러짐 · 나감 · 다른 차원 · 필드에서 64칸 넘게 멀어짐: 낙인도 사라짐
        for (BrandMark mk : B.marks) {
            if (!mk.done && (!ok(mk.p) || !okTarget(mk.p) || mk.p.level() != u.dim || hdist(mk.p.position(), u.center) > 64)) mk.done = true;
        }
        List<BrandMark> live = liveMarks(B);
        if (now < B.until) {
            // 표시: 발밑 원 (다른 사람이 안에 있으면 붉게), 머리 위 문양, 남은 초
            if ((now - B.t0) % 4 == 0) {
                for (BrandMark mk : live) {
                    Vec3 l = mk.p.position();
                    boolean bad = false;
                    for (ServerPlayer q : targets(u)) if (!q.getUUID().equals(mk.id) && inBrand(u, q, l)) bad = true;
                    disc(u.dim, l, radius, 5, bad ? RED : PURPLE, Opt.of("fill", false, "alpha", 0.95));
                }
            }
            if ((now - B.t0) % 20 == 0) {
                for (BrandMark mk : live) {
                    SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(mk.p.getX(), mk.p.getY() + 2.6, mk.p.getZ()), Opt.of("dir", new Vec3(0, 1, 0), "size", 1.2, "life", 1.0,
                            "color", PURPLE, "alpha", 0.9));
                }
            }
            long left = B.until - now;
            if (left % 20 == 0 && left <= 60) for (BrandMark mk : live) showFaceText(mk.p, "§4§l" + (left / 20), Opt.of("dist", 2.4, "up", 0.45));
            if ((now - B.t0) % 10 == 0) {
                for (BrandMark mk : live) {
                    int near = 0;
                    for (ServerPlayer q : targets(u)) if (!q.getUUID().equals(mk.id) && inBrand(u, q, mk.p.position())) near++;
                    BossKit.hintNotify(mk.p, near > 0 ? "§4§l독룡의 낙인 §c— 원 안에 " + near + "명! 흩어지세요"
                            : "§5독룡의 낙인 §7(" + (long) Math.ceil(left / 20.0) + "초) §a원 안에 아무도 없음", 15);
                }
            }
            return;
        }
        // 터짐: 낙인마다 원 → 원 둘 이상에 든 사람 즉사, 하나면 마법 피해
        m.brand = null;
        m.bigUntil = Math.min(m.bigUntil, now + 60);
        List<Vec3> blasts = new ArrayList<>();
        for (BrandMark mk : live) blasts.add(mk.p.position());
        for (Vec3 at : blasts) {
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1.2, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 3.0, "life", 0.5, "color", PURPLE, "alpha", 0.95));
            SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", 0.6, "color", PURPLE, "alpha", 0.9));
            play(u.dim, "random.explode", at, 1.2, 0.6);
        }
        // 필드 안의 사람 + 낙인이 남은 사람 (필드 밖으로 달아나도 제 낙인은 터짐)
        Map<UUID, ServerPlayer> who = new LinkedHashMap<>();
        for (ServerPlayer p : targets(u)) who.put(p.getUUID(), p);
        for (BrandMark mk : live) who.put(mk.id, mk.p);
        for (ServerPlayer p : who.values()) {
            int n = 0;
            for (Vec3 at : blasts) if (inBrand(u, p, at)) n++;
            if (n >= 2) {
                BossKit.hintSay(u, "§4[보스] §e" + p.getName().getString() + "§f님이 독룡의 낙인이 겹쳐 쓰러졌습니다");
                Status.kill(p, u.boss);
            } else if (n == 1) {
                bossHit(u, p, d.num("damage"), Opt.of("cause", "magic", "noSource", true));
            }
        }
    }

    // ------------------------------------------------------------ 높은 곳

    /** 뿌리 · 담장 · 탑 위처럼 높은 곳 (바닥보다 high.above 칸 넘게) 에 서 있는 사람: 날개로 쳐서 떨어뜨림 (사람마다 cd 틱에 한 번). 날아 있는 동안은 없음 */
    static void highTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (now % 10 != 0 || m.flight != null) return;
        Bosses.Unit u = enc;
        if (!u.alive || !ok(u.boss)) return;
        Obj d = u.def.obj("high");
        Vec3 c = enc.center;
        Map<UUID, Long> since = m.highSince;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : targets(u)) {
            // 뛰는 중이어도 셈 (높은 곳에서 계속 뛰며 버티는 것을 막음): 내려오면 (아래로 보이면) 처음부터 다시
            if (p.getY() - c.y < d.num("above")) continue;
            seen.add(p.getUUID());
            // 뿌리를 넘어가는 것은 괜찮음: grace 틱 넘게 높은 곳에 머물면 (처음 올라서면 경고)
            if (!since.containsKey(p.getUUID())) {
                since.put(p.getUUID(), now);
                BossKit.hintFace(p, "§6§l내려오세요! §f머물면 날개로 칩니다", Opt.of("dist", 2.2, "up", 0.35));
            }
            if (now - since.get(p.getUUID()) < d.num("grace")) continue;
            if (now - m.high.getOrDefault(p.getUUID(), -1000L) < d.num("cd")) continue;
            m.high.put(p.getUUID(), now);
            Vec3 dir = dirTo(p.position(), c);
            dust(enc.dim, p.position(), 1.8, WIND, 0.6);
            play(enc.dim, "mob.enderdragon.flap", p.position(), 0.7, 1);
            // 때린 엔티티 없이 (게임의 밀어내기는 니드호그에게서 먼 쪽 = 담장 밖일 수 있음): 가운데 쪽으로만 떨어뜨림
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 1.1, dir.z * 1.1, 0.35), "noSource", true));
            BossKit.hintFace(p, "§6§l날개에 맞아 떨어집니다", Opt.of("dist", 2.2, "up", 0.35));
            explain(u, "high", "§6[보스] §f뿌리·담장·탑 위처럼 높은 곳에 " + Js.toFixed(d.num("grace") / 20, 1)
                    + "초 넘게 머물면 니드호그가 날개로 쳐서 떨어뜨립니다 (뿌리를 넘어가는 것은 괜찮음)");
        }
        since.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 땅에 내려놓음 (날다가 쓰러지거나 싸움이 끝날 때): 높이 0, 날기 · 걸음 속성 0, 급강하 모습 대신 내려앉기 */
    static void ground(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null || !ok(enc.boss)) return;
        Body b = m.body;
        boolean flying = b.h > 0 || b.fly > 0;
        b.h = 0;
        b.flyTo = 0;
        b.fly = 0;
        b.gaitTo = 0;
        b.gait = 0;
        try {
            place(enc);
        } catch (RuntimeException e) {
            // 무시
        }
        b.flySent = setProp(enc, "rpg:fly", 0, null);
        b.gaitSent = setProp(enc, "rpg:gait", 0, null);
        if (flying) enc.api.anim(enc, "land", null, AIR, null);
    }

    // ------------------------------------------------------------ 모듈

    /** 원거리 견제 (v2.3.0): 비상 (독의 비) · 독룡의 낙인 (흩어져야 함) 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m == null || (m.flight == null && m.brand == null);
    }

    /** 독 가시는 입에서 */
    @Override
    public Vec3 farFrom(Bosses.Unit u, Obj style) {
        return mem(u) == null ? null : headAt(u);
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        Vec3 c = enc.center;
        // 하늘에서 내려앉으며 등장: 깨운 사람 쪽을 봄 (없으면 남쪽)
        Vec3 p = enc.starter != null && !enc.starter.isRemoved() ? enc.starter.position() : new Vec3(c.x, c.y, c.z + 8);
        double yaw = Math.atan2(p.z - c.z, p.x - c.x);
        M m = new M();
        m.next.put("bite", now + 40);
        m.next.put("tail", now + (long) d.obj("tail").num("first"));
        m.next.put("breath", now + (long) d.obj("breath").num("first"));
        m.next.put("gust", now + (long) d.obj("gust").num("first"));
        m.next.put("snare", now + (long) d.obj("snare").num("first"));
        m.next.put("brand", now + (long) d.obj("brand").num("first"));
        m.bigUntil = now + 100;
        Body b = m.body;
        b.x = c.x;
        b.z = c.z;
        b.h = 14;
        b.yaw = yaw;
        b.fly = 1;
        b.flyTo = 1;
        Flight fl = new Flight();
        fl.stage = "arrive";
        fl.t0 = now;
        fl.time = 40;
        fl.h0 = 14;
        m.flight = fl;
        enc.mem = m;
        for (Bosses.Unit u : enc.units) u.mem = m;
        // 엔진 평타는 쓰지 않음 (몸을 돌리는 물어뜯기는 모듈이)
        enc.meleeAt = NEVER;
        place(enc);
        bodyTick(enc);
        enc.api.cast(enc, "arrive", 44);
        play(enc.dim, "mob.enderdragon.flap", c, 0.7, 1);
    }

    /** forceNext 의 패턴을 바로 (시작했으면 true) */
    static boolean forced(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        return switch (id) {
            case "bite" -> bite(u, now, list);
            case "tail" -> tailWhip(u, now, list);
            case "breath" -> breath(u, now, list);
            case "gust" -> gust(u, now, list);
            case "snare" -> snare(u, now, list);
            case "flight" -> {
                takeoff(u, now);
                yield true;
            }
            case "brand" -> {
                brand(u, now, list);
                yield false; // 낙인은 틀 없이
            }
            default -> false;
        };
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        if (m == null) return false;
        Obj d = u.def;
        double r = u.api.ratio(u);
        if (m.flight != null) return false;
        String f = forceNext;
        if (f != null) {
            forceNext = null;
            return forced(u, f, now, list);
        }
        // 큰 패턴: 비상 → 독룡의 낙인 (서로 겹치지 않음)
        if (m.brand == null && now >= m.bigUntil) {
            Double at = null;
            for (Double x : d.obj("flight").numbersOrNull("at")) {
                if (r <= x && !m.flightDone.contains(x)) {
                    at = x;
                    break;
                }
            }
            if (at != null) {
                m.flightDone.add(at);
                takeoff(u, now);
                return true;
            }
            Obj br = d.obj("brand");
            if (r <= br.num("from") && now >= m.next.get("brand")) {
                if (brand(u, now, list)) {
                    m.next.put("brand", now + every(u, pair(br, "every")));
                    return false; // 낙인은 틀 없이: 니드호그는 계속 싸움
                }
            }
        }
        if (m.brand == null && now >= m.next.get("gust")) {
            if (gust(u, now, list)) {
                m.next.put("gust", now + every(u, pair(d.obj("gust"), "every")));
                return true;
            }
            m.next.put("gust", now + 20);
        }
        if (now >= m.next.get("breath")) {
            if (breath(u, now, list)) {
                m.next.put("breath", now + every(u, pair(d.obj("breath"), "every")));
                return true;
            }
            m.next.put("breath", now + 20);
        }
        if (m.brand == null && now >= m.next.get("snare")) {
            if (snare(u, now, list)) {
                m.next.put("snare", now + every(u, pair(d.obj("snare"), "every")));
                return true;
            }
            m.next.put("snare", now + 20);
        }
        if (now >= m.next.get("tail")) {
            if (tailWhip(u, now, list)) {
                m.next.put("tail", now + every(u, pair(d.obj("tail"), "every")));
                return true;
            }
            m.next.put("tail", now + 10);
        }
        // 평타: 몸 앞의 가장 가까운 사람을 물어뜯음
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
        flightTick(enc, now);
        brandTick(enc, now);
        rootsTick(enc, now);
        highTick(enc, now);
        if (!enc.alive) return;
        // 몸: 비상 중에는 flightTick 이, 패턴 중에는 제자리 (원하는 쪽으로 돎), 패턴이 없으면 걸음
        if (m.flight == null) {
            if (u.cast != null) standTick(u);
            else {
                m.body.want = null;
                walkTick(u, list != null ? list : targets(u));
            }
        }
        bodyTick(u);
    }

    /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 날다가 쓰러지면 땅에 내려놓고 서 있는 모습으로 */
    @Override
    public void dying(Bosses.Enc enc) {
        ground(enc);
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        ground(enc);
        for (Root r : m.roots) enc.api.dropProp(enc, r.e);
        m.roots.clear();
        m.flight = null;
        m.brand = null;
    }
}
