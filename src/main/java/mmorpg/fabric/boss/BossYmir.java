package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.effect;
import static mmorpg.fabric.boss.BossKit.faceAll;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.groundUnder;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.lineMarks;
import static mmorpg.fabric.boss.BossKit.lobArc;
import static mmorpg.fabric.boss.BossKit.meleeTarget;
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.say;
import static mmorpg.fabric.boss.BossKit.snd;
import static mmorpg.fabric.boss.BossKit.sound;
import static mmorpg.fabric.boss.BossKit.title;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 이미르 (boss_ymir.js — v2.5.0, Lv.40, 마지막 보스): 수치는 config BOSSES.ymir.
 * 얼음 몽둥이를 오른손에 든 태초의 서리 거인 (크기 ×4, 키 약 9칸). 태초의 빙원에서 싸움.
 * 움직이지 않는 보스 (중력 · 충돌 없음) 를 모듈이 매 틱 옮김 (엔진 pose): 가장 가까운 사람 쪽으로 몸을 돌려 걷고, 숨결 때는 가운데로 뛰어옴.
 * 몸 (엔티티 자리) = 발 사이, 오른 어깨 = 몸 오른쪽 drive.arm 칸 (몽둥이가 떨어지는 줄).
 * 엔티티 속성: rpg:gait (0~1 걷는 빠르기 → stride 동작), rpg:crack (0~3 → 텍스처: 얼음 다리의 금, 3 = 무릎 꿇음).
 * <ul>
 * <li>등장: 가운데 얼어붙은 호수 밑에서 얼음을 깨고 솟아오름 → 포효 (그동안 무적)</li>
 * <li>몽둥이 내려치기 (평타): 몸 앞 가장 가까운 사람 — 오른 어깨에서 그 사람 쪽 직사각형 (대상이 몽둥이 줄 위에 오게 몸을 돎). 피해 + 느림</li>
 * <li>발 구르기: 옆 · 뒤 (앞 front° 밖) 에 붙은 사람이 있으면 몸 둘레 원 → 피해 + 띄움</li>
 * <li>얼음 가시: 가장 먼 사람부터 lines 명 쪽으로 몸에서 가시 줄 → 가시가 차례로 솟음 → 피해 + 빙결 (얼음 덩어리에 갇혀 못 움직임)</li>
 * <li>고드름 비: 모두의 발밑 + 무작위 자리 원 → 피해 + 느림</li>
 * <li>빙결된 사람 (얼음에 갇힘) 은 이미르의 다른 공격을 받지 않음 (속박된 사람 = 원거리 견제도 노리지 않음)</li>
 * <li>균열 → 무릎 꿇기: 몸 가까이에서 준 피해만큼 금 (멀리서는 × far). 다 차면 무릎을 꿇어 공격 없이 받는 피해 +bonus ("빈틈") → 일어난 뒤 lock 틱은 금이 가지 않음</li>
 * <li>니플헤임의 숨결 (체력 65% · 30%, 즉사): 가운데로 뛰어옴 → 숨을 들이쉼 (눈보라 안개) → 불이 켜진 모닥불 곁 (warm 칸) 이 아니면 얼어붙음.
 * 불 하나에 싸우는 사람의 절반 (올림) 까지 — 넘치면 그 불이 꺼지고 곁의 사람도 모두 얼어붙음. 두 번째는 마주 보는 모닥불 한 쌍을 먼저 꺼 버림</li>
 * <li>핌불베트르 (체력 20% 아래): 끝날 때까지 눈보라 안개, 재사용 대기 × winter.cd, 가시 줄 · 고드름이 늘어남</li>
 * <li>모닥불 곁에서는 느림이 풀림 · 높은 곳 (첨탑 · 성문 기둥): 몽둥이로 쳐서 끌어내림 · 원거리 견제: 얼음 창 (왼손에서, 느림 II — 엔진의 ice 모양)</li>
 * </ul>
 * 큰 패턴 (숨결) 과 핌불베트르는 틀이 없을 때 (pick). 엔진 평타는 쓰지 않음 (몸을 돌리는 내려치기를 모듈이).
 * 자바에서 달라진 점: /fog 대신 엔진의 보스 안개 (BossKit.syncBossFog — 하늘빛 패킷, 리소스 팩 fogs 의 거리 · 색 그대로), 모닥불은 자바 블록 상태 lit
 * (베드락 extinguished 의 반대 — ArenaBlocks), 모닥불 위 글자 (rpg:prop_label) 는 한 줄 이름표 그대로. 패턴 함수 · 몸 도우미는 시험 · 사진첩이 바로 부를 수 있게 공개,
 * {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossYmir implements BossModule {
    static final String KEY = "ymir";

    static final double[] ICE = {0.62, 0.9, 1.0};
    /** 눈밭에서 보이는 예고 색 (짙은 파랑) */
    static final double[] DEEP = {0.2, 0.5, 1.0};
    static final double[] FROST = {0.85, 0.95, 1.0};
    static final double[] SNOW = {0.95, 0.97, 1.0};
    static final double[] EMBER = {1.0, 0.55, 0.15};
    /** 모닥불 곁 고리 */
    static final double[] FIRE = {1.0, 0.45, 0.08};
    static final double[] RED = {1.0, 0.18, 0.12};
    /** 꺼진 불 */
    static final double[] DEAD = {0.3, 0.38, 0.55};
    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;

    /** 피해 숫자 앞 표시: 무릎 꿇은 동안 (근접 보너스 » 앞) */
    public static final String OPEN_MARK = "§6빈틈 ";
    /** 동작 틀: 솟아오르기 · 무릎 꿇기 · 뛰기 · 숨결은 다음 동작이 이어받을 때까지 마지막 모습 유지 (쉬기 rest 가 풂) */
    public static final String HOLD = "rpg_hold";
    /** 빙결 얼음 덩어리 (소품) · 모닥불 위 글자 (소품) */
    public static final String ICE_ID = "rpg:prop_ymir_ice";
    public static final String LABEL_ID = "rpg:prop_label";
    static final String ICE_ANIM = "animation.rpg.prop.ymir_ice";
    /** 안개 (리소스 팩 fogs/ — 자바는 BossKit.FOGS): 숨결 = 짙은 눈보라, 핌불베트르 = 옅은 눈보라 */
    public static final String FOG_BREATH = "rpg:ymir_blizzard";
    public static final String FOG_WINTER = "rpg:ymir_winter";
    /** 고드름: fall 틱 동안 height 칸에서 떨어짐 (rpg:icicle 중력 = 2 × height / (fall/20)², build.py ICICLE_FALL 과 같음) */
    public static final int ICICLE_FALL_TICKS = 12;
    public static final double ICICLE_FALL_HEIGHT = 7.2;
    /** 모닥불 네 개 중 마주 보는 쌍 (ArenaThemes.FIRE_DEG 순서: 45 · 135 · 225 · 315°) */
    public static final int[][] FIRE_PAIRS = {{0, 2}, {1, 3}};

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (slam · stomp · spikes · icicles · breath · breath2 · winter — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> propIds() {
        return List.of(ICE_ID, LABEL_ID);
    }

    // ------------------------------------------------------------ 기억

    /** 몸: 자리 (x, z) · 뛰는 높이 h (등장 때는 호수 밑 = 음수) · 몸 방향 yaw (라디안, +x 에서 +z 쪽으로) · 돌리려는 쪽 want · 걷는 빠르기 gait (→ gaitTo) */
    public static final class Body {
        public double x;
        public double z;
        public double h;
        public double yaw;
        public Double want;
        public double gait;
        public double gaitTo;
        Double gaitSent;
        /** 마지막으로 보낸 자리 (그대로면 다시 보내지 않음) */
        boolean placed;
        double px;
        double pz;
        double ph;
        double pyaw;

        Body(double x, double z, double h, double yaw) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.yaw = yaw;
        }
    }

    /** 균열: 쌓인 금 g · 단계 (0~3, 3 = 무릎 꿇음) · 금이 가지 않는 끝 틱 */
    public static final class Crack {
        public double g;
        public int stage;
        Integer sent;
        public long lockUntil;
    }

    /** 뛰기: 시작 자리 · 높이 → 끝 자리, 시작 틱 · 걸리는 틱 · 포물선 높이 */
    public record Leap(double x0, double z0, double h0, double x1, double z1, long t0, int time, double height) {}

    /** 무스펠헤임의 모닥불: 자리 (화덕 가운데 칸, 바닥 높이 — 모닥불 블록은 한 칸 위) · 켜졌는지 · 다시 켜지는 틱 */
    public static final class Fire {
        public final Vec3 at;
        public boolean lit = true;
        public Long relightAt;

        Fire(Vec3 at) {
            this.at = at;
        }
    }

    /** 빙결 얼음 덩어리: 엔티티 · 갇힌 사람 · 부서지는 틱 · 사라지는 틱 */
    public static final class Ice {
        public final Entity e;
        public final UUID pid;
        public final long breakAt;
        public final long dropAt;
        public boolean broken;

        Ice(Entity e, UUID pid, long breakAt, long dropAt) {
            this.e = e;
            this.pid = pid;
            this.breakAt = breakAt;
            this.dropAt = dropAt;
        }
    }

    /** 모닥불 위 글자 (소품) 와 그 불, 마지막 글자 */
    public static final class Label {
        public final Entity e;
        public final Fire f;
        String text;

        Label(Entity e, Fire f) {
            this.e = e;
            this.f = f;
        }
    }

    /** 니플헤임의 숨결: 단계 (leap · inhale) · 두 번째인지 · 시작 · 내쉬는 틱 · 받는 사람 (id) · 빠진 사람 · 불 하나의 자리 · 글자 · 꺼 버린 불 */
    public static final class Breath {
        public String stage;
        public final boolean second;
        public long t0;
        public long until;
        public final Set<UUID> who;
        public final Set<UUID> gone = new HashSet<>();
        public int cap;
        public final List<Label> labels = new ArrayList<>();
        public final List<Integer> snuffed = new ArrayList<>();

        Breath(boolean second, long t0, Set<UUID> who) {
            this.stage = "leap";
            this.second = second;
            this.t0 = t0;
            this.until = Long.MAX_VALUE;
            this.who = who;
            this.cap = Math.max(1, (int) Math.ceil(who.size() / 2.0));
        }
    }

    /**
     * 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 큰 패턴을 쉬는 끝 · 알린 규칙 · 몸 · 균열 · 무릎 꿇은 끝 · 뛰기 · 숨결 · 쓴 숨결 · 겨울 · 모닥불 · 얼음 덩어리 ·
     * 높은 곳 (친 틱 · 올라선 틱) · 따뜻함 알림. 보스 안개는 엔진의 칸 (Bosses.Mem fog · fogWant)
     */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new HashMap<>();
        public long bigUntil;
        public final Set<String> told = new HashSet<>();
        public Body body;
        public final Crack crack = new Crack();
        public long kneelUntil;
        public Leap leap;
        public Breath breath;
        public final Set<Double> breathDone = new HashSet<>();
        public boolean winter;
        public final List<Fire> fires = new ArrayList<>();
        public final List<Ice> ice = new ArrayList<>();
        public final Map<UUID, Long> high = new HashMap<>();
        public final Map<UUID, Long> highSince = new HashMap<>();
        public final Map<UUID, Long> warmTold = new HashMap<>();
    }

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    // ------------------------------------------------------------ 도우미

    static long every(Bosses.Unit u, double[] range) {
        return Math.round(rnd((int) range[0], (int) range[1]) * u.cdMult);
    }

    static Vec3 floorAt(Bosses.Unit u, double x, double z) {
        return new Vec3(x, u.center.y, z);
    }

    /** 가로 단위 방향 a → b (같은 자리면 남쪽) */
    static Vec3 dirTo(double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double L = Math.hypot(dx, dz);
        return L < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(dx / L, 0, dz / L);
    }

    static Vec3 dirTo(Vec3 a, Vec3 b) {
        return dirTo(a.x, a.z, b.x, b.z);
    }

    static double angOf(Vec3 d) {
        return Math.atan2(d.z, d.x);
    }

    static Vec3 dirOf(double a) {
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    static double wrap(double a) {
        return ((a % TAU) + TAU) % TAU;
    }

    static double wrapPi(double a) {
        double w = wrap(a);
        return w > Math.PI ? w - TAU : w;
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double turnToward(double cur, double want, double step) {
        double d = wrapPi(want - cur);
        return Math.abs(d) <= step ? want : cur + Math.signum(d) * step;
    }

    /** 몸 오른쪽 단위 방향 (+x 동쪽 · +z 남쪽에서 북쪽 (-z) 을 보면 오른쪽은 동쪽): 몽둥이를 든 손 쪽 */
    public static Vec3 rightOf(double yaw) {
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번 — 0.34.0 BOSS_HINTS.chat 일 때만 보냄, 알렸다는 표시 told 는 늘 남김) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        BossKit.hintSay(u, text);
    }

    /** 0.34.0 2페이즈 알림 (패턴 안내를 숨겨도 늘 보임 — 사용자 "공허 각성 같은 2페이지는 냅두고"): explain 과 같이 싸움마다 한 번 */
    static void announce(Bosses.Unit u, String id, String text) {
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

    /** 얼음 부서짐 (물보라 그림 + 먼지) */
    static void shatter(ServerLevel dim, Vec3 at, double size, double[] color) {
        SkillFx.fx(dim, "rpg:splash", new Vec3(at.x, at.y + size * 0.7, at.z), Opt.of("size", size, "life", 0.6, "color", color, "alpha", 0.95));
        dust(dim, at, size * 0.9, FROST, 0.7);
    }

    static double arenaRadius(Bosses.Unit u) {
        return u.enc.def.obj("arena").num("radius");
    }

    /** 밀어내는 힘 (kx, kz): 담장에서 5칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖 · 벽 위로 날아가지 않게) */
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

    /** 공격이 닿는 사람 */
    static List<ServerPlayer> targets(Bosses.Unit u) {
        return fighters(u);
    }

    /** 바닥 높이의 사람 (위아래 4칸 안) */
    static boolean atFloor(Bosses.Unit u, ServerPlayer p) {
        return Math.abs(p.getY() - u.center.y) < 4;
    }

    /** 이미르가 공격할 수 있는 때: 등장 · 숨결 (뛰는 동안 포함) · 뛰는 동안 · 무릎 꿇은 동안이 아님 (원거리 견제 · 높은 곳) */
    static boolean canAttack(Bosses.Unit u, long now) {
        M m = mem(u);
        return m != null && m.body != null && m.breath == null && m.leap == null && now >= m.kneelUntil && !(u.cast != null && "arrive".equals(u.cast.id));
    }

    /** 이미르가 때릴 수 있는 사람: 빙결 (속박) 된 사람은 얼음이 막아 줌 */
    static boolean open(ServerPlayer p) {
        return !Status.isRooted(p);
    }

    // ------------------------------------------------------------ 몸

    /** 몸 (엔티티 자리): 바닥 높이 + 뛰는 높이 h (등장 때는 호수 밑 = 음수) */
    public static Vec3 bodyOf(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y + b.h, b.z);
    }

    /** 오른 어깨 밑 (몽둥이 줄의 시작, 바닥 높이) — yaw 가 null 이면 지금 몸 방향 */
    public static Vec3 shoulderAt(Bosses.Unit u, Double yaw) {
        Body b = mem(u).body;
        Vec3 r = rightOf(yaw != null ? yaw : b.yaw);
        double arm = u.def.obj("drive").num("arm");
        return new Vec3(b.x + r.x * arm, u.center.y, b.z + r.z * arm);
    }

    /** 왼손 (원거리 견제를 던지는 곳: 몽둥이는 오른손) */
    public static Vec3 leftHandAt(Bosses.Unit u) {
        Body b = mem(u).body;
        Vec3 r = rightOf(b.yaw);
        double arm = u.def.obj("drive").num("arm");
        return new Vec3(b.x - r.x * arm, u.center.y + b.h + 6, b.z - r.z * arm);
    }

    /** 머리 (입 높이) */
    public static Vec3 headAt(Bosses.Unit u) {
        Body b = mem(u).body;
        Vec3 f = dirOf(b.yaw);
        return new Vec3(b.x + f.x * 0.6, u.center.y + b.h + 7.6, b.z + f.z * 0.6);
    }

    /** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음: 서 있을 때 매 틱 옮기지 않게) */
    static void place(Bosses.Unit u) {
        Body b = mem(u).body;
        if (b.placed && b.px == b.x && b.pz == b.z && b.ph == b.h && b.pyaw == b.yaw && Bosses.valid(u.boss)) return;
        b.placed = true;
        b.px = b.x;
        b.pz = b.z;
        b.ph = b.h;
        b.pyaw = b.yaw;
        u.api.pose(u, new Vec3(b.x, u.center.y + b.h, b.z), b.yaw);
    }

    /** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게, 땅 위. 시험 · 사진첩도 씀 */
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

    /** 속성 보내기: step 넘게 바뀌었거나 끝값 (0 · 1) 에 닿았을 때만 */
    static Double sendProp(Bosses.Unit u, String id, double v, Double last, double step) {
        if (last != null && (v == last || (Math.abs(v - last) < step - 1e-9 && v != 0 && v != 1))) return last;
        if (u.boss == null || u.boss.isRemoved()) return last;
        u.boss.setProp(id, v);
        return v;
    }

    /** 매 틱: 걷는 빠르기를 목표 쪽으로, 속성 보내기 (걷기 rpg:gait, 금 rpg:crack) */
    static void bodyTick(Bosses.Unit u) {
        M m = mem(u);
        Body b = m.body;
        Crack C = m.crack;
        b.gait = Math.abs(b.gaitTo - b.gait) <= 0.15 ? b.gaitTo : b.gait + Math.signum(b.gaitTo - b.gait) * 0.15;
        b.gaitSent = sendProp(u, "rpg:gait", Math.round(clamp(b.gait, 0, 1) * 100) / 100.0, b.gaitSent, 0.05);
        if ((C.sent == null || C.stage != C.sent) && u.boss != null && !u.boss.isRemoved()) {
            u.boss.setProp("rpg:crack", C.stage);
            C.sent = C.stage;
        }
    }

    /** 걷기: 가장 가까운 사람 쪽으로 몸을 돌리고, 거의 그쪽을 보면 걸어감 (near 칸 안이면 멈춤, 가운데에서 keep 칸 밖으로는 나가지 않음) */
    static void walkTick(Bosses.Unit u, List<ServerPlayer> list, long now) {
        Obj D = u.def.obj("drive");
        Body b = mem(u).body;
        ServerPlayer tgt = null;
        double best = Double.POSITIVE_INFINITY;
        for (ServerPlayer p : list) {
            double d = Math.hypot(p.getX() - b.x, p.getZ() - b.z);
            if (d < best) {
                best = d;
                tgt = p;
            }
        }
        b.gaitTo = 0;
        if (tgt != null) {
            double want = angOf(dirTo(b.x, b.z, tgt.getX(), tgt.getZ()));
            b.yaw = turnToward(b.yaw, want, D.num("turn") * DEG);
            if (best > D.num("near") && Math.abs(wrapPi(want - b.yaw)) < 50 * DEG) {
                Vec3 c = u.center;
                double walk = D.num("walk");
                double nx = b.x + Math.cos(b.yaw) * walk;
                double nz = b.z + Math.sin(b.yaw) * walk;
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
        // 발걸음: 쿵 (가까운 사람은 조금 흔들림)
        if (b.gaitTo > 0 && now % 12 == 0) {
            Vec3 at = bodyOf(u);
            play(u.dim, snd(u, "step"), at, 0.7, 1);
            dust(u.dim, floorAt(u, at.x, at.z), 1.8, SNOW, 0.6);
            shakeNear(u, at, 7, 0.06, 0.25);
        }
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
        return Math.abs(wrapPi(angOf(dirTo(b.x, b.z, l.x, l.z)) - b.yaw));
    }

    /** 지금 틀에 ticks 뒤 단계를 끼워 넣음 (틀이 없으면 무시) */
    static void later(Bosses.Unit u, int ticks, Bosses.StepFn fn) {
        Bosses.Cast c = u.cast;
        if (c == null) return;
        List<Bosses.Step> all = new ArrayList<>(c.steps);
        all.add(new Bosses.Step(Tasks.now() + ticks, fn));
        all.sort(Comparator.comparingLong(Bosses.Step::at));
        c.steps.clear();
        c.steps.addAll(all);
    }

    /** 동작 틀기: 유지하는 동작은 틀 HOLD (다음에 같은 틀에 튼 동작이 이어받음) */
    static void hold(Bosses.Unit u, String name) {
        u.api.anim(u, name, null, HOLD, "0");
    }

    static void release(Bosses.Unit u, String name) {
        u.api.anim(u, name, null, HOLD, null);
    }

    // ------------------------------------------------------------ 뛰기 (숨결 때 가운데로)

    /** 뛰기 시작: time 틱 동안 포물선 (높이 height) 으로 to 까지 (몸은 leapTick 이 옮김) */
    static void jump(Bosses.Unit u, Vec3 to, int time, double height) {
        M m = mem(u);
        Body b = m.body;
        m.leap = new Leap(b.x, b.z, b.h, to.x, to.z, Tasks.now(), Math.max(1, time), height);
        b.want = null;
        play(u.dim, snd(u, "swing"), bodyOf(u), 0.5, 1);
        if (b.h <= 0.01) dust(u.dim, floorAt(u, b.x, b.z), 3.2, SNOW, 0.9);
    }

    /** 뛰는 중: 몸을 포물선으로 옮김 (닿으면 땅 위) */
    static void leapTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Leap L = m.leap;
        if (L == null) return;
        Body b = m.body;
        double k = clamp((now - L.t0()) / (double) L.time(), 0, 1);
        b.x = L.x0() + (L.x1() - L.x0()) * k;
        b.z = L.z0() + (L.z1() - L.z0()) * k;
        b.h = L.h0() * (1 - k) + L.height() * 4 * k * (1 - k);
        b.gaitTo = 0;
        if (k >= 1) {
            m.leap = null;
            b.h = 0;
        }
        place(u);
    }

    /** 내려앉은 자리 · 쿵 연출 */
    static void landFx(Bosses.Unit u, Vec3 at, double radius, double[] color) {
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", 0.7, "color", color, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2, "life", 1.4, "color",
                new double[] {0.3, 0.4, 0.55}, "alpha", 0.9));
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * TAU;
            dust(u.dim, new Vec3(at.x + Math.cos(a) * radius * 0.7, at.y, at.z + Math.sin(a) * radius * 0.7), 2.4, SNOW, 0.9);
        }
        play(u.dim, snd(u, "hit"), at, 0.6, 1);
        play(u.dim, "dig.snow", at, 0.7, 1);
        shakeNear(u, at, 20, 0.35, 0.7);
    }

    // ------------------------------------------------------------ 빙결 (얼음 덩어리)

    /** 얼음 덩어리 소품 동작 (부서짐은 마지막 모습 유지 — 틀 rpg_ice, 멈춤 "0") */
    static void iceAnim(Entity e, String name) {
        if (e instanceof BedrockMob m && !m.isRemoved()) m.playAnimation(ICE_ANIM + "." + name, "rpg_ice", "0");
    }

    /** 얼음 덩어리를 p 발밑에 (ticks 뒤 부서지고 0.6초 뒤 사라짐) */
    static void encase(Bosses.Unit u, ServerPlayer p, int ticks) {
        M m = mem(u);
        long now = Tasks.now();
        Vec3 at = new Vec3(p.getX(), groundUnder(p, u.center.y), p.getZ());
        BedrockMob e = BossKit.spawn(u.dim, ICE_ID, at);
        if (e != null) {
            u.api.addProp(u.enc, e);
            m.ice.add(new Ice(e, p.getUUID(), now + ticks, now + ticks + 12));
        }
        shatter(u.dim, at, 1.4, FROST);
        play(u.dim, snd(u, "ice"), at, 1.3, 1);
    }

    /** 빙결: 속박 ticks (얼음에 갇힘, 그동안 이미르의 다른 공격은 막아 줌) + 얼음 덩어리 */
    static void freeze(Bosses.Unit u, ServerPlayer p, int ticks) {
        Status.root(p, ticks, BossKit.rootLabel("빙결"));
        encase(u, p, ticks);
    }

    /** 얼음 덩어리: 때가 되면 부서지고 사라짐 */
    static void iceTick(Bosses.Unit u, long now) {
        List<Ice> I = mem(u).ice;
        for (int i = I.size() - 1; i >= 0; i--) {
            Ice c = I.get(i);
            if (c.e == null || c.e.isRemoved()) {
                I.remove(i);
                continue;
            }
            if (now >= c.breakAt && !c.broken) {
                c.broken = true;
                iceAnim(c.e, "shatter");
                Vec3 l = c.e.position();
                shatter(u.dim, l, 1.6, ICE);
                play(u.dim, snd(u, "ice"), l, 0.9, 1);
            }
            if (now >= c.dropAt) {
                u.api.dropProp(u.enc, c.e);
                I.remove(i);
            }
        }
    }

    // ------------------------------------------------------------ 몽둥이 내려치기 (평타)

    /** 내려치는 직사각형: 어깨 밑에서 앞으로 from ~ reach 칸 (시작 · 끝 · 방향) */
    public record Rect(Vec3 a, Vec3 b, Vec3 dir) {}

    /**
     * 대상이 몽둥이 줄 (오른 어깨에서 앞) 위에 오게 하는 몸 방향. 몸에 바짝 붙은 사람 (몸 가운데에서 √(arm² + (from+0.5)²) 칸 안) 은
     * 줄 가운데에 올 수 없으므로 직사각형 시작에서 0.5칸 안쪽 (줄에서 조금 왼쪽이지만 폭 안) 에 오게 함
     */
    public static double slamYaw(Bosses.Unit u, Vec3 l) {
        Body b = mem(u).body;
        double D = Math.hypot(l.x - b.x, l.z - b.z);
        double th = Math.atan2(l.z - b.z, l.x - b.x);
        double arm = u.def.obj("drive").num("arm");
        // 대상 자리 (몸 기준: 앞으로 along, 오른쪽으로 side)
        double along = Math.max(Math.sqrt(Math.max(0, D * D - arm * arm)), Math.min(D, u.def.obj("melee").num("from") + 0.5));
        double side = Math.sqrt(Math.max(0, D * D - along * along));
        return th - Math.atan2(side, along);
    }

    public static Rect slamRect(Bosses.Unit u, double yaw) {
        Obj m = u.def.obj("melee");
        Vec3 s = shoulderAt(u, yaw);
        Vec3 f = dirOf(yaw);
        return new Rect(new Vec3(s.x + f.x * m.num("from"), s.y, s.z + f.z * m.num("from")), new Vec3(s.x + f.x * m.num("reach"), s.y, s.z + f.z * m.num("reach")), f);
    }

    /** 직사각형 예고 (가운데 줄을 따라 판, 테두리는 진하게) */
    static void rectMarks(Bosses.Unit u, Rect R, double width, int ticks, double[] color) {
        double L = hdist(R.a(), R.b());
        int n = Math.max(2, (int) Math.ceil(L / 1.2));
        Vec3 r = rightOf(angOf(R.dir()));
        double life = Math.max(0.1, ticks / 20.0);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            Vec3 c = new Vec3(R.a().x + (R.b().x - R.a().x) * t, R.a().y, R.a().z + (R.b().z - R.a().z) * t);
            for (int s = -1; s <= 1; s++) {
                boolean edge = s != 0 || i == 0 || i == n;
                Vec3 at = new Vec3(c.x + r.x * s * (width / 2 - 0.4), c.y + 0.07, c.z + r.z * s * (width / 2 - 0.4));
                SkillFx.fx(u.dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", at, Opt.of("dir", Vec3.ZERO, "size", edge ? 0.6 : 1.1, "life", life, "color", color, "alpha",
                        edge ? 0.9 : 0.45, "grow", 1.0));
            }
        }
    }

    /** 몽둥이 내려치기: 몽둥이 줄이 닿는 사람 (어깨에서 reach 칸: 몸 가운데에서 √(reach² + arm²)) 중 가장 가까운 사람 (v2.20.0 도발한 사람이 닿으면 그 사람) */
    public static boolean slam(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj m = u.def.obj("melee");
        Body b = mem(u).body;
        double reachC = Math.hypot(m.num("reach"), u.def.obj("drive").num("arm"));
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : list) {
            if (open(p) && atFloor(u, p) && Math.hypot(p.getX() - b.x, p.getZ() - b.z) <= reachC && offAngle(u, p.position()) <= m.num("aim") * DEG) pool.add(p);
        }
        ServerPlayer t = meleeTarget(u, pool, new Vec3(b.x, u.center.y, b.z));
        if (t == null) return false;
        double yaw = slamYaw(u, t.position());
        Rect R = slamRect(u, yaw);
        b.want = yaw;
        int windup = (int) m.num("windup");
        rectMarks(u, R, m.num("width"), windup, DEEP);
        u.api.anim(u, "slam");
        sound(u, snd(u, "swing"), shoulderAt(u, yaw), 0.7, 1);
        u.api.cast(u, "slam", windup + 8, step(windup, (x, n) -> slamHit(x, R)));
        return true;
    }

    /** l 이 직사각형 R (a → b, 반폭 half) 안인지: 앞뒤 · 옆으로 pad 칸 여유 (끝이 둥글지 않음 — 그린 예고 그대로) */
    public static boolean inRect(Vec3 l, Rect R, double half, double pad) {
        double ax = l.x - R.a().x;
        double az = l.z - R.a().z;
        double along = ax * R.dir().x + az * R.dir().z;
        double side = Math.abs(ax * R.dir().z - az * R.dir().x);
        return along >= -pad && along <= hdist(R.a(), R.b()) + pad && side <= half + pad;
    }

    static void slamHit(Bosses.Unit u, Rect R) {
        Obj m = u.def.obj("melee");
        for (ServerPlayer p : targets(u)) {
            if (!open(p) || Math.abs(p.getY() - R.a().y) > 4 || !inRect(p.position(), R, m.num("width") / 2, 0.3)) continue;
            double[] kk = keepIn(u, p, R.dir().x * 1.0, R.dir().z * 1.0);
            bossHit(u, p, m.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.35), "directional", true, "effect", effect("slowness", (int) m.num("slow"), 0)));
        }
        // 몽둥이가 떨어진 곳: 얼음 깨짐 + 줄을 따라 금
        Vec3 head = new Vec3(R.a().x + (R.b().x - R.a().x) * 0.8, R.a().y, R.a().z + (R.b().z - R.a().z) * 0.8);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(head.x, head.y + 0.1, head.z), Opt.of("dir", Vec3.ZERO, "size", 2.6, "life", 0.5, "color", ICE, "alpha", 0.95));
        for (int i = 1; i <= 3; i++) {
            double t = i / 4.0;
            SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(R.a().x + (R.b().x - R.a().x) * t, R.a().y + 0.05, R.a().z + (R.b().z - R.a().z) * t), Opt.of("dir", Vec3.ZERO,
                    "spin", random() * 360, "size", 3.2, "life", 1.2, "color", new double[] {0.3, 0.42, 0.6}, "alpha", 0.85));
        }
        shatter(u.dim, head, 2.2, ICE);
        sound(u, snd(u, "hit"), head, 0.8, 1);
        shakeNear(u, head, 10, 0.25, 0.5);
    }

    // ------------------------------------------------------------ 발 구르기

    public static boolean stomp(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("stomp");
        Body b = mem(u).body;
        boolean flank = false;
        for (ServerPlayer p : list) {
            if (open(p) && atFloor(u, p) && Math.hypot(p.getX() - b.x, p.getZ() - b.z) <= d.num("radius") && offAngle(u, p.position()) > (d.num("front") / 2) * DEG) {
                flank = true;
                break;
            }
        }
        if (!flank) return false;
        Vec3 at = floorAt(u, b.x, b.z);
        b.want = null;
        int windup = (int) d.num("windup");
        disc(u.dim, at, d.num("radius"), windup, DEEP);
        u.api.anim(u, "stomp");
        sound(u, snd(u, "roar"), headAt(u), 1.4, 0.5);
        explain(u, "stomp", "§b[보스] 발 구르기: §f이미르의 옆이나 뒤에 붙어 있으면 발을 굴러 몸 둘레(파란 원)를 칩니다 — 원 밖으로 잠깐 물러나세요");
        u.api.cast(u, "stomp", windup + 10, step(windup, (x, n) -> stompHit(x, at)));
        return true;
    }

    static void stompHit(Bosses.Unit u, Vec3 at) {
        Obj d = u.def.obj("stomp");
        for (ServerPlayer p : targets(u)) {
            if (!open(p) || hdist(p.position(), at) > d.num("radius") + 0.3 || Math.abs(p.getY() - at.y) > 3) continue;
            Vec3 dir = dirTo(at, p.position());
            double[] kk = keepIn(u, p, dir.x * 0.9, dir.z * 0.9);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], d.num("lift"))));
        }
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(at.x, at.y + 0.2, at.z), Opt.of("dir", Vec3.ZERO, "speed", 26.0, "size", 1.0, "life", d.num("radius") / 26, "color", ICE,
                "alpha", 0.9));
        landFx(u, at, 3.2, ICE);
    }

    // ------------------------------------------------------------ 얼음 가시 (빙결)

    /** 몸 (at) 에서 방향 v 로 뻗은 줄이 필드 가장자리 안쪽 (반지름 - 0.5) 까지 닿는 길이 (range 까지) */
    public static double rayEnd(Bosses.Unit u, Vec3 at, Vec3 v, double range) {
        double R = arenaRadius(u) - 0.5;
        double wx = at.x - u.center.x;
        double wz = at.z - u.center.z;
        double bd = wx * v.x + wz * v.z;
        double q = bd * bd - (wx * wx + wz * wz - R * R);
        double t = q > 0 ? -bd + Math.sqrt(q) : 0;
        return Math.max(0, Math.min(range, t));
    }

    /** 가시 자리 (s = 몸에서 거리) */
    public record Pt(double x, double y, double z, double s) {}

    /** 가시 줄: 방향 · 시작 · 끝 · 가시 자리, 맞은 사람 (줄마다 한 번) · 솟은 가시 수 */
    public static final class Line {
        public final Vec3 dir;
        public final Vec3 from;
        public final Vec3 to;
        public final List<Pt> pts;
        final Set<UUID> hit = new HashSet<>();
        int done;

        Line(Vec3 dir, Vec3 from, Vec3 to, List<Pt> pts) {
            this.dir = dir;
            this.from = from;
            this.to = to;
            this.pts = pts;
        }
    }

    /** 가시 줄: 몸 겉면 바로 밖에서 대상 쪽으로 (필드 끝까지), 가시 자리 목록 */
    public static List<Line> spikeLines(Bosses.Unit u, List<ServerPlayer> list) {
        Obj d = u.def.obj("spikes");
        Vec3 b = bodyOf(u);
        double start = bossSize(u.def).half() + 0.5;
        List<Line> out = new ArrayList<>();
        for (ServerPlayer p : list) {
            Vec3 dir = dirTo(b, p.position());
            double len = Math.max(start, rayEnd(u, b, dir, d.num("range")));
            List<Pt> pts = new ArrayList<>();
            for (double s = start; s <= len + 1e-6; s += d.num("step")) pts.add(new Pt(b.x + dir.x * s, u.center.y, b.z + dir.z * s, s));
            out.add(new Line(dir, new Vec3(b.x + dir.x * start, u.center.y, b.z + dir.z * start), new Vec3(b.x + dir.x * len, u.center.y, b.z + dir.z * len), pts));
        }
        return out;
    }

    public static boolean spikes(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("spikes");
        M m = mem(u);
        Vec3 b = bodyOf(u);
        int n = (int) d.num("lines") + (m.winter ? (int) u.def.obj("winter").num("lines") : 0);
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (open(p) && atFloor(u, p)) cand.add(p);
        cand.sort(Comparator.comparingDouble((ServerPlayer p) -> hdist(p.position(), b)).reversed());
        if (cand.size() > n) cand = new ArrayList<>(cand.subList(0, n));
        if (cand.isEmpty()) return false;
        List<Line> lines = spikeLines(u, cand);
        double start = bossSize(u.def).half() + 0.5;
        double longest = 0;
        for (Line L : lines) longest = Math.max(longest, hdist(L.from, L.to));
        double speed = d.num("speed");
        int travel = (int) Math.ceil((longest / speed) * 20);
        int windup = (int) d.num("windup");
        // 줄 예고: 판의 크기는 반폭 (가시가 닿는 곳 = 줄 가운데에서 width/2 + 0.3)
        for (Line L : lines) lineMarks(u.dim, L.from, L.to, d.num("width") / 2, windup + travel, DEEP);
        m.body.want = angOf(lines.get(0).dir);
        u.api.anim(u, "spike");
        sound(u, snd(u, "swing"), bodyOf(u), 0.6, 1);
        BossKit.hintFaceAll(u, "§b§l얼음 가시! §f파란 줄 밖으로", Opt.of("dist", 2.4, "up", 0.45), cand);
        explain(u, "spikes", "§b[보스] 얼음 가시: §f이미르가 몽둥이를 땅에 박으면 먼 사람들 쪽으로 파란 줄이 뻗고 가시가 차례로 솟습니다 — 줄 밖으로. §7맞으면 빙결 (얼음에 갇혀 못 움직이지만, 그동안 이미르의 다른 공격은 막아 줌)");
        List<Bosses.Step> steps = new ArrayList<>();
        steps.add(step(windup, (x, t) -> play(x.dim, "mace.smash_ground", bodyOf(x), 0.8, 1)));
        for (int k = 0; k <= travel; k++) {
            double reach = start + (k * speed) / 20;
            steps.add(step(windup + k, (x, t) -> spikeStep(x, lines, reach)));
        }
        u.api.cast(u, "spikes", windup + travel + 10, steps);
        return true;
    }

    /** 가시가 솟음: 줄마다 reach 칸까지 아직 솟지 않은 가시 → 곁의 사람 (줄마다 한 번) 피해 + 빙결 */
    static void spikeStep(Bosses.Unit u, List<Line> lines, double reach) {
        Obj d = u.def.obj("spikes");
        List<ServerPlayer> list = targets(u);
        for (Line L : lines) {
            while (L.done < L.pts.size() && L.pts.get(L.done).s() <= reach + 1e-6) {
                Pt pt = L.pts.get(L.done++);
                Vec3 at = new Vec3(pt.x(), pt.y(), pt.z());
                SkillFx.fx(u.dim, "rpg:ice_spike", at, Opt.of("size", 1.9 + (L.done % 3) * 0.25, "life", 1.3, "spin", random() * 30 - 15, "color", ICE, "alpha", 1.0));
                if (L.done % 3 == 0) play(u.dim, "break.amethyst_block", at, 0.8 + random() * 0.3, 0.8);
                for (ServerPlayer p : list) {
                    if (L.hit.contains(p.getUUID()) || !open(p) || Math.abs(p.getY() - pt.y()) > 3 || hdist(p.position(), at) > d.num("width") / 2 + 0.3) continue;
                    L.hit.add(p.getUUID());
                    // 때린 엔티티 없이 (땅에서 솟은 가시: 보스 쪽에서 밀어내지 않음)
                    if (bossHit(u, p, d.num("damage"), Opt.of("noSource", true))) freeze(u, p, (int) d.num("root"));
                }
            }
        }
    }

    // ------------------------------------------------------------ 고드름 비

    /** 필드 안 무작위 자리 (가운데에서 R-3 칸 안) */
    static Vec3 randomSpot(Bosses.Unit u) {
        double R = arenaRadius(u) - 3;
        double a = random() * TAU;
        double r = Math.sqrt(random()) * R;
        return floorAt(u, u.center.x + Math.cos(a) * r, u.center.z + Math.sin(a) * r);
    }

    public static boolean icicles(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("icicles");
        M m = mem(u);
        List<ServerPlayer> people = new ArrayList<>();
        for (ServerPlayer p : list) if (open(p) && atFloor(u, p)) people.add(p);
        if (people.isEmpty()) return false;
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : people) spots.add(new Vec3(p.getX(), groundUnder(p, u.center.y), p.getZ()));
        int extra = (int) d.num("extra") + (m.winter ? (int) u.def.obj("winter").num("extra") : 0);
        for (int i = 0; i < extra; i++) spots.add(randomSpot(u));
        double radius = d.num("radius");
        int windup = (int) d.num("windup");
        for (Vec3 s : spots) {
            disc(u.dim, s, radius, windup, DEEP);
            SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(s.x, s.y + ICICLE_FALL_HEIGHT + 0.5, s.z), Opt.of("dir", new Vec3(0, 1, 0), "size", radius * 0.9, "life", windup / 20.0,
                    "spin", 0.0, "speed", 40.0, "grow", 0.0, "progress", 1.0, "color", FROST, "alpha", 0.75));
        }
        m.body.want = null;
        u.api.anim(u, "summon");
        sound(u, snd(u, "roar"), headAt(u), 1.2, 0.6);
        BossKit.hintFaceAll(u, "§b§l고드름 비! §f발밑 원 밖으로", Opt.of("dist", 2.4, "up", 0.45), people);
        explain(u, "icicles", "§b[보스] 고드름 비: §f이미르가 두 팔을 들면 모두의 발밑과 곳곳에 파란 원이 뜨고 고드름이 떨어집니다 — 원 밖으로 §7(맞으면 느림)");
        int fall = windup - ICICLE_FALL_TICKS;
        u.api.cast(u, "icicles", windup + 8, step(fall, (x, t) -> icicleDrop(x, spots)), step(windup, (x, t) -> icicleHit(x, spots)));
        return true;
    }

    /** 고드름이 떨어지기 시작 (ICICLE_FALL_TICKS 뒤에 바닥에 닿음) */
    static void icicleDrop(Bosses.Unit u, List<Vec3> spots) {
        double radius = u.def.obj("icicles").num("radius");
        for (Vec3 s : spots) {
            for (int i = 0; i < 3; i++) {
                double a = (i / 3.0) * TAU + random();
                double r = i == 0 ? 0 : radius * 0.5;
                SkillFx.fx(u.dim, "rpg:icicle", new Vec3(s.x + Math.cos(a) * r, s.y + ICICLE_FALL_HEIGHT, s.z + Math.sin(a) * r), Opt.of("size", 1.4, "life",
                        ICICLE_FALL_TICKS / 20.0 + 0.05, "color", ICE, "alpha", 1.0));
            }
        }
    }

    static void icicleHit(Bosses.Unit u, List<Vec3> spots) {
        Obj d = u.def.obj("icicles");
        double radius = d.num("radius");
        Set<UUID> hit = new HashSet<>();
        for (Vec3 s : spots) {
            shatter(u.dim, s, radius * 0.8, ICE);
            SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(s.x, s.y + 0.05, s.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2, "life", 1.0, "color",
                    new double[] {0.35, 0.45, 0.62}, "alpha", 0.85));
            play(u.dim, snd(u, "ice"), s, 1.1, 0.9);
            for (ServerPlayer p : targets(u)) {
                if (hit.contains(p.getUUID()) || !open(p) || hdist(p.position(), s) > radius + 0.3 || Math.abs(p.getY() - s.y) > 3) continue;
                hit.add(p.getUUID());
                bossHit(u, p, d.num("damage"), Opt.of("noSource", true, "effect", effect("slowness", (int) d.num("slow"), 0)));
            }
        }
    }

    // ------------------------------------------------------------ 균열 → 무릎 꿇기

    /** 금이 가는 중인지 (등장 · 숨결 · 뛰기 · 무적 · 무릎 꿇은 뒤 쉬는 동안은 아님) */
    static boolean canCrack(Bosses.Unit u, long now) {
        M m = mem(u);
        return !u.immune && m.breath == null && m.leap == null && now >= m.kneelUntil && now >= m.crack.lockUntil && !(u.cast != null && "arrive".equals(u.cast.id));
    }

    /** 맞을 때: 몸 가까이 (근접 보너스 거리) 에서 준 피해는 그대로, 멀리서 준 피해는 × far 만큼 금. 다 차면 무릎 꿇기 */
    public static void addCrack(Bosses.Unit u, double amount, boolean close, long now) {
        Obj C = u.def.obj("crack");
        M m = mem(u);
        Crack K = m.crack;
        if (!(amount > 0) || !canCrack(u, now)) return;
        K.g += amount * (close ? 1 : C.num("far"));
        if (K.g >= C.num("need")) {
            kneel(u, now);
            return;
        }
        int stage = (int) Math.min(2, Math.floor((3 * K.g) / C.num("need")));
        if (stage > K.stage) {
            K.stage = stage;
            Vec3 b = bodyOf(u);
            Vec3 r = rightOf(m.body.yaw);
            for (int s : new int[] {1, -1}) shatter(u.dim, new Vec3(b.x + r.x * s * 0.75, u.center.y + 1.2, b.z + r.z * s * 0.75), 1.2, ICE);
            play(u.dim, snd(u, "ice"), b, 0.7, 1);
            faceAll(u, "§b§l" + stage + "/3", Opt.of("dist", 2.4, "up", 0.6), targets(u));
        }
    }

    /** 무릎 꿇기: 하던 패턴을 끊고 time 틱 동안 공격 없음, 받는 피해 +bonus → 일어남 */
    public static void kneel(Bosses.Unit u, long now) {
        Obj C = u.def.obj("crack");
        M m = mem(u);
        int time = (int) C.num("time");
        u.cast = null;
        m.body.want = null;
        m.crack.g = 0;
        m.crack.stage = 3;
        m.kneelUntil = now + time;
        m.crack.lockUntil = now + time + (long) C.num("lock");
        // 하던 공격 동작 (기본 틀: 내려치기 · 가시 박기 등) 을 끊고 무릎 꿇는 모습 (유지 틀)
        u.api.anim(u, "rest");
        hold(u, "kneel");
        Vec3 b = bodyOf(u);
        Vec3 r = rightOf(m.body.yaw);
        for (int s : new int[] {1, -1}) {
            Vec3 k = new Vec3(b.x + r.x * s * 0.75, u.center.y + 1.2, b.z + r.z * s * 0.75);
            shatter(u.dim, k, 2.4, ICE);
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(k.x, k.y + 0.8, k.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.6, "life", 0.5, "color", FROST, "alpha", 0.95));
        }
        landFx(u, floorAt(u, b.x, b.z), 4.5, FROST);
        play(u.dim, "random.anvil_land", b, 0.5, 1);
        play(u.dim, snd(u, "ice"), b, 0.6, 1);
        String secs = Js.str(Js.round(time / 20.0));
        String bonus = Js.str(Js.round(C.num("bonus") * 100));
        BossKit.hintTitle(u, "§b§l이미르가 무릎을 꿇었습니다!", "§f" + secs + "초 동안 받는 피해 §e+" + bonus + "% §7(빈틈)", targets(u));
        explain(u, "kneel", "§b[보스] 균열: §f이미르의 얼음 다리를 가까이에서 때리면 금이 갑니다 (멀리서 때린 것은 " + Js.str(Js.round(C.num("far") * 100)) + "%만). §e금이 가득 차면 무릎을 꿇고 "
                + secs + "초 동안 공격 없이 받는 피해 +" + bonus + "% §7(피해 숫자 앞 \"빈틈\")");
        u.api.cast(u, "kneel", time + 20, step(time, (x, t) -> rise(x)), step(time + 12, (x, t) -> mem(x).crack.stage = 0));
    }

    static void rise(Bosses.Unit u) {
        release(u, "rise");
        play(u.dim, snd(u, "roar"), headAt(u), 1.1, 0.7);
        Body b = mem(u).body;
        dust(u.dim, floorAt(u, b.x, b.z), 3, SNOW, 0.8);
    }

    // ------------------------------------------------------------ 모닥불

    private static BlockState campfire(boolean lit) {
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("extinguished", !lit);
        st.put("minecraft:cardinal_direction", "south");
        return ArenaBlocks.state("minecraft:campfire", st);
    }

    /** 모닥불 블록 자리 (화덕 가운데 칸 한 칸 위) */
    static BlockPos firePos(Fire f) {
        return new BlockPos((int) Math.floor(f.at.x), (int) Math.floor(f.at.y) + 1, (int) Math.floor(f.at.z));
    }

    /** 모닥불 블록 켜기 · 끄기 (화덕 가운데 칸 한 칸 위 — 불러오지 않은 곳이면 기억만) */
    public static void setFire(Bosses.Unit u, Fire f, boolean lit) {
        f.lit = lit;
        BlockPos pos = firePos(f);
        if (!u.dim.isLoaded(pos)) return;
        u.dim.setBlock(pos, campfire(lit), Block.UPDATE_CLIENTS);
    }

    /** 모닥불 블록이 켜져 있는지 (시험용 — 모닥불이 아니면 false) */
    public static boolean fireBlockLit(Bosses.Unit u, Fire f) {
        BlockState s = u.dim.getBlockState(firePos(f));
        return s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT);
    }

    /** 모닥불 곁의 사람 (가로 warm 칸 안, 위아래 3칸) */
    public static List<ServerPlayer> nearFire(Bosses.Unit u, Fire f, List<ServerPlayer> list) {
        double W = u.enc.def.obj("arena").num("warm");
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), f.at) <= W && Math.abs(p.getY() - f.at.y) < 3) out.add(p);
        return out;
    }

    /** 켜진 모닥불 중 p 가 곁에 있는 불 */
    static Fire fireOf(Bosses.Unit u, ServerPlayer p) {
        double W = u.enc.def.obj("arena").num("warm");
        for (Fire f : mem(u).fires) if (f.lit && hdist(p.position(), f.at) <= W && Math.abs(p.getY() - f.at.y) < 3) return f;
        return null;
    }

    /** 매 틱: 꺼진 불 다시 켜기 · 불씨 연출 · 곁에서는 느림이 풀림 */
    static void fireTick(Bosses.Unit u, long now) {
        M m = mem(u);
        for (Fire f : m.fires) {
            if (!f.lit && f.relightAt != null && now >= f.relightAt && m.breath == null) {
                f.relightAt = null;
                setFire(u, f, true);
                SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(f.at.x, f.at.y + 1.6, f.at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.2, "life", 0.5, "color", EMBER, "alpha",
                        0.95));
                play(u.dim, snd(u, "fire"), f.at, 1, 1);
            }
            if (f.lit && now % 20 == 7) SkillFx.fx(u.dim, "rpg:dust", new Vec3(f.at.x, f.at.y + 2.4, f.at.z), Opt.of("size", 1.2, "life", 1.2, "color", EMBER, "alpha", 0.7));
            // 2초마다: 다른 방법 (물병 던지기 · 명령 등) 으로 꺼지거나 켜진 모닥불은 규칙대로 (스크립트가 기억하는 상태로) 되돌림
            if (now % 40 == 13) {
                BlockPos pos = firePos(f);
                if (u.dim.isLoaded(pos)) {
                    BlockState s = u.dim.getBlockState(pos);
                    if (!s.is(Blocks.CAMPFIRE) || s.getValue(CampfireBlock.LIT) != f.lit) setFire(u, f, f.lit);
                }
            }
        }
        long every = (long) u.def.obj("warmth").num("every");
        if (now % every != 0) return;
        for (ServerPlayer p : targets(u)) {
            if (fireOf(u, p) == null || !p.hasEffect(MobEffects.SLOWNESS)) continue;
            if (!p.removeEffect(MobEffects.SLOWNESS)) continue;
            SkillFx.fx(u.dim, "rpg:dust", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("size", 1.1, "life", 0.6, "color", EMBER, "alpha", 0.8));
            if (now - m.warmTold.getOrDefault(p.getUUID(), -1000L) >= 100) {
                m.warmTold.put(p.getUUID(), now);
                BossKit.hintFace(p, "§6따뜻함 §f— 느림이 풀렸습니다", Opt.of("dist", 2.2, "up", 0.1));
            }
        }
    }

    // ------------------------------------------------------------ 니플헤임의 숨결 (즉사: 불이 켜진 모닥불 곁)

    /**
     * 담장 밖 (성문 · 둑 · 필드 바깥 띠) 에서 싸우던 사람은 숨결을 들이쉬기 시작할 때 담장 안으로 끌려 들어옴 (제 방향 그대로, 가운데에서 R-4.5 칸):
     * 벽을 돌아 성문으로 들어오면 모닥불까지 제때 가지 못하므로
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
            dust(u.dim, l, 1.6, FROST, 0.7);
            dust(u.dim, at, 1.6, FROST, 0.7);
            BossKit.hintFace(p, "§b§l눈보라에 끌려 들어왔습니다", Opt.of("dist", 2.4, "up", 0.45));
        }
    }

    /** 니플헤임의 숨결 시작 (second = 두 번째 — 마주 보는 모닥불 한 쌍을 꺼 버림) */
    public static void breath(Bosses.Unit u, long now, List<ServerPlayer> list, boolean second) {
        Obj B = u.def.obj("breath");
        M m = mem(u);
        Vec3 c = u.center;
        // 숨결을 받는 사람 (id): 뛰기 시작할 때 싸우던 사람 + 그 뒤 들어온 사람 (breathRoll). 자리 = 절반 (올림, 사람이 늘면 늘어남)
        Set<UUID> who = new LinkedHashSet<>();
        for (ServerPlayer p : targets(u)) who.add(p.getUUID());
        m.breath = new Breath(second, now, who);
        u.immune = true;
        m.body.want = null;
        hold(u, "leap");
        int leap = (int) B.num("leap");
        jump(u, c, leap, 4);
        sound(u, snd(u, "roar"), headAt(u), 0.8, 1);
        BossKit.hintTitle(u, "§b§l니플헤임의 숨결", "§f이미르가 숨을 들이쉽니다 §7— §6불이 켜진 모닥불 곁으로!", list);
        int inhale = leap + 6;
        int windup = (int) B.num("windup");
        u.api.cast(u, "breath", inhale + windup + 30, step(leap, (x, t) -> breathLand(x)), step(inhale, (x, t) -> breathInhale(x)),
                step(inhale + windup, (x, t) -> breathExhale(x)), step(inhale + windup + 26, (x, t) -> release(x, "rest")));
    }

    static void breathLand(Bosses.Unit u) {
        M m = mem(u);
        if (m.breath == null) return;
        u.immune = false;
        Vec3 b = bodyOf(u);
        landFx(u, floorAt(u, b.x, b.z), 6, FROST);
        // 내려앉자마자 가슴을 펴고 숨을 들이쉬는 모습 (내쉴 때까지 유지)
        hold(u, "inhale");
        // 호수 얼음에 금
        for (int i = 0; i < 6; i++) {
            double a = (i / 6.0) * TAU + random() * 0.4;
            SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(b.x + Math.cos(a) * 3, u.center.y + 0.05, b.z + Math.sin(a) * 3), Opt.of("dir", Vec3.ZERO, "spin", random() * 360,
                    "size", 4.0, "life", 3.0, "color", new double[] {0.85, 0.95, 1.0}, "alpha", 0.9));
        }
    }

    static void breathInhale(Bosses.Unit u) {
        Obj B = u.def.obj("breath");
        M m = mem(u);
        Breath G = m.breath;
        if (G == null) return;
        long now = Tasks.now();
        // 숨결을 받는 사람 (뛰는 동안 필드를 벗어난 사람도: 64칸 안) 중 담장 밖에 있는 사람은 끌려 들어옴
        breathRoll(u, G);
        pullIn(u, breathVictims(u, G));
        G.stage = "inhale";
        G.t0 = now;
        G.until = now + (long) B.num("windup");
        sound(u, snd(u, "inhale"), headAt(u), 0.5, 1);
        play(u.dim, "mob.warden.sonic_charge", headAt(u), 0.6, 1);
        // 두 번째 숨결: 마주 보는 모닥불 한 쌍에 얼음 창을 던져 끔 (남은 한 쌍도 마주 봄: 어디서든 가까운 불이 있음)
        if (G.second && m.fires.size() == 4) {
            int[] pair = FIRE_PAIRS[(int) Math.floor(random() * FIRE_PAIRS.length)];
            Vec3 from = leftHandAt(u);
            for (int i : pair) {
                Fire f = m.fires.get(i);
                BossKit.Arc arc = lobArc(from, new Vec3(f.at.x, f.at.y + 1.5, f.at.z), 0.8);
                SkillFx.fx(u.dim, "rpg:ice_lance", from, Opt.of("dir", arc.dir(), "speed", arc.speed(), "size", 1.6, "life", 0.8, "color", ICE, "alpha", 1.0));
                G.snuffed.add(i);
            }
            play(u.dim, "mob.irongolem.throw", from, 0.7, 1);
            later(u, 16, (x, t) -> snuff(x, pair));
        }
        // 모닥불 위 글자 (곁의 사람 수 / 한 불의 자리)
        for (Fire f : m.fires) {
            BedrockMob e = BossKit.spawn(u.dim, LABEL_ID, new Vec3(f.at.x, f.at.y + 3.1, f.at.z));
            if (e == null) continue;
            u.api.addProp(u.enc, e);
            G.labels.add(new Label(e, f));
        }
        labelTick(u);
        explain(u, "breath", "§b[보스] 니플헤임의 숨결: §f이미르가 숨을 들이쉬는 " + Js.str(Js.round(B.num("windup") / 20)) + "초 안에 §6불이 켜진 무스펠헤임의 모닥불 곁(주황 원)§f으로 — 원 밖은 얼어붙어 즉사. §c불 하나에 싸우는 사람의 절반까지 §7(넘치면 불이 꺼져 곁의 사람도 모두 얼어붙음)");
        BossKit.hintSay(u, "§b[보스] §f이번 숨결: 불 하나에 §e" + G.cap + "명§f까지" + (G.second ? " §7· §c이미르가 모닥불 둘을 꺼 버립니다!" : ""));
    }

    /** 두 번째 숨결: 얼음 창이 닿은 모닥불이 꺼짐 (숨결이 끝나고 relight 틱 뒤 다시 켜짐) */
    static void snuff(Bosses.Unit u, int[] pair) {
        M m = mem(u);
        for (int i : pair) {
            if (i < 0 || i >= m.fires.size()) continue;
            Fire f = m.fires.get(i);
            setFire(u, f, false);
            shatter(u.dim, new Vec3(f.at.x, f.at.y + 1, f.at.z), 2.4, ICE);
            SkillFx.fx(u.dim, "rpg:dust", new Vec3(f.at.x, f.at.y + 2, f.at.z), Opt.of("size", 2.2, "life", 1.2, "color", new double[] {0.5, 0.5, 0.55}, "alpha", 0.8));
            play(u.dim, snd(u, "snuff"), f.at, 0.6, 1);
            play(u.dim, "random.fizz", f.at, 0.8, 1);
        }
        labelTick(u);
    }

    /** 모닥불 위 글자: 켜진 불 = 곁의 사람 수 / 자리, 넘치면 붉게, 꺼진 불 = 글자 없음 */
    static void labelTick(Bosses.Unit u) {
        Breath G = mem(u).breath;
        if (G == null) return;
        List<ServerPlayer> list = targets(u);
        for (Label L : G.labels) {
            if (L.e == null || L.e.isRemoved()) continue;
            int n = nearFire(u, L.f, list).size();
            String text;
            // 0.34.0 숫자만 (꺼진 불은 글자 없음 — "불씨" · "넘침!" · "꺼진 불" 글자는 패턴 안내라 뺌)
            if (!L.f.lit) text = "";
            else if (n > G.cap) text = "§c§l" + n + "§7/§c" + G.cap;
            else text = "§6§l" + n + "§7/§6" + G.cap;
            if (text.equals(L.text)) continue;
            L.text = text;
            if (L.e instanceof BedrockMob b) b.setNameTag(text);
        }
    }

    /** 모닥불 위 글자 (시험용: 지금 글자 — 없으면 null) */
    public static String labelText(Label L) {
        return L == null ? null : L.text;
    }

    /** 세계의 플레이어 모두 (나갔다 다시 들어온 사람은 새 객체라 id 로 찾음) */
    static List<ServerPlayer> allPlayers(Bosses.Unit u) {
        return u.dim.getServer() == null ? List.of() : u.dim.getServer().getPlayerList().getPlayers();
    }

    /**
     * 숨결 명단 맞추기 (뛰는 동안 · 들이쉬는 동안 매 틱): 지금 싸우는 사람은 넣고 (쓰러졌다가 필드로 돌아온 사람도 다시), 자리 수는 사람이 늘면 늘림 (줄이지 않음).
     * 쓰러졌거나 싸울 수 없게 된 사람 (크리에이티브 등) 은 뺌 — 다시 살아나 필드 밖에 있으면 판정하지 않음
     */
    static void breathRoll(Bosses.Unit u, Breath G) {
        for (ServerPlayer p : targets(u)) {
            G.gone.remove(p.getUUID());
            G.who.add(p.getUUID());
        }
        G.cap = Math.max(G.cap, (int) Math.ceil(G.who.size() / 2.0));
        for (ServerPlayer p : allPlayers(u)) if (G.who.contains(p.getUUID()) && !G.gone.contains(p.getUUID()) && !okTarget(p)) G.gone.add(p.getUUID());
    }

    /** 숨결을 받는 사람: 지금 싸우는 사람 + 명단에 있는 사람 중 아직 싸울 수 있고 같은 세계 · 필드 가운데에서 64칸 안인 사람 (필드를 떠나도 피하지 못함) */
    public static List<ServerPlayer> breathVictims(Bosses.Unit u, Breath G) {
        Map<UUID, ServerPlayer> out = new LinkedHashMap<>();
        for (ServerPlayer p : targets(u)) out.put(p.getUUID(), p);
        for (ServerPlayer p : allPlayers(u)) {
            if (out.containsKey(p.getUUID()) || !G.who.contains(p.getUUID()) || G.gone.contains(p.getUUID())) continue;
            if (okTarget(p) && p.level() == u.dim && hdist(p.position(), u.center) <= 64) out.put(p.getUUID(), p);
        }
        return new ArrayList<>(out.values());
    }

    /** 숨결 동안 매 틱: 명단 맞추기 (뛰는 동안부터) · 들이쉬는 동안 모닥불 곁 고리 (주황 = 자리 있음, 붉음 = 넘침, 꺼진 불 = 흐린 고리) · 글자 · 알림 · 3 · 2 · 1 · 눈보라 */
    static void breathTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Breath G = m.breath;
        if (G == null) return;
        // 뛰는 동안부터 명단을 맞춤 (그 사이 들어온 사람도 받고, 쓰러진 사람은 빠짐)
        breathRoll(u, G);
        if (!"inhale".equals(G.stage)) return;
        double W = u.enc.def.obj("arena").num("warm");
        List<ServerPlayer> list = breathVictims(u, G);
        long left = G.until - now;
        if (now % 5 == 0) {
            for (Fire f : m.fires) {
                int n = nearFire(u, f, list).size();
                double[] color = !f.lit ? DEAD : n > G.cap ? RED : FIRE;
                disc(u.dim, f.at, f.lit ? W : 1.5, 6, color, Opt.of("fill", false, "alpha", 0.95));
            }
            labelTick(u);
        }
        if (now % 10 == 0) {
            for (ServerPlayer p : list) {
                Fire f = fireOf(u, p);
                int n = f != null ? nearFire(u, f, list).size() : 0;
                long s = (long) Math.ceil(left / 20.0);
                BossKit.hintNotify(p, f == null ? "§b얼어붙습니다! §f불이 켜진 모닥불 곁(주황 원)으로 §7(" + s + "초)"
                        : n > G.cap ? "§c불 하나에 " + G.cap + "명까지 §f— 다른 불로! §7(" + n + "명, " + s + "초)" : "§6모닥불 곁 §a— 안전 §7(" + n + "/" + G.cap + ", " + s + "초)", 15);
                // 눈보라: 이미르 쪽으로 몰아치는 눈 (1초마다, 모바일 파티클 수)
                if (now % 20 != 0) continue;
                Vec3 toward = dirTo(p.position(), bodyOf(u));
                SkillFx.fx(u.dim, "rpg:snowfall", new Vec3(p.getX() - toward.x * 3, p.getY() + 3, p.getZ() - toward.z * 3), Opt.of("dir", new Vec3(toward.x, -0.3, toward.z),
                        "speed", 9.0, "size", 7.0, "life", 0.8, "color", SNOW, "alpha", 0.9));
            }
            // 가장자리에서 가운데로 빨려 드는 서리
            Vec3 c = u.center;
            double R = arenaRadius(u) - 2;
            for (int i = 0; i < 8; i++) {
                double a = (i / 8.0) * TAU + now * 0.05;
                Vec3 at = new Vec3(c.x + Math.cos(a) * R, c.y + 1.5, c.z + Math.sin(a) * R);
                Vec3 v = dirTo(at, bodyOf(u));
                SkillFx.fx(u.dim, "rpg:breath", at, Opt.of("dir", new Vec3(v.x, 0.1, v.z), "speed", 14.0, "size", 3.0, "life", 1.2, "spin", random() * 360, "color", FROST,
                        "alpha", 0.75));
            }
        }
        if (left > 0 && left % 20 == 0 && left <= 60) faceAll(u, "§b§l" + (left / 20), Opt.of("dist", 2.4, "up", 0.45), list);
    }

    /** 내쉼: 켜진 모닥불 곁이 아니면 얼어붙음. 곁의 사람이 자리보다 많으면 그 불이 꺼지고 곁의 사람도 모두 얼어붙음 */
    static void breathExhale(Bosses.Unit u) {
        Obj B = u.def.obj("breath");
        M m = mem(u);
        Breath G = m.breath;
        if (G == null) return;
        long now = Tasks.now();
        List<ServerPlayer> list = breathVictims(u, G);
        Set<UUID> safe = new HashSet<>();
        Set<UUID> doomed = new HashSet<>();
        for (Fire f : m.fires) {
            if (!f.lit) continue;
            List<ServerPlayer> near = nearFire(u, f, list);
            if (near.size() > G.cap) {
                // 넘침: 불이 꺼지고 곁의 사람도 얼어붙음
                setFire(u, f, false);
                SkillFx.fx(u.dim, "rpg:dust", new Vec3(f.at.x, f.at.y + 2, f.at.z), Opt.of("size", 2.4, "life", 1.2, "color", new double[] {0.5, 0.5, 0.55}, "alpha", 0.85));
                play(u.dim, snd(u, "snuff"), f.at, 0.5, 1);
                for (ServerPlayer p : near) doomed.add(p.getUUID());
            } else for (ServerPlayer p : near) safe.add(p.getUUID());
        }
        hold(u, "exhale");
        Vec3 b = bodyOf(u);
        sound(u, snd(u, "exhale"), headAt(u), 0.8, 1);
        play(u.dim, "wind_charge.burst", b, 0.6, 1);
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(b.x, u.center.y + 0.3, b.z), Opt.of("dir", Vec3.ZERO, "speed", 30.0, "size", 1.0, "life", arenaRadius(u) / 30, "color", FROST,
                "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(b.x, u.center.y + 0.1, b.z), Opt.of("dir", Vec3.ZERO, "size", 8.0, "life", 0.8, "color", FROST, "alpha", 0.95));
        for (int i = 0; i < 12; i++) {
            Vec3 v = dirOf((i / 12.0) * TAU);
            SkillFx.fx(u.dim, "rpg:breath", headAt(u), Opt.of("dir", new Vec3(v.x, -0.35, v.z), "speed", 18.0, "size", 4.0, "life", 1.2, "spin", random() * 360, "color", FROST,
                    "alpha", 0.85));
        }
        for (ServerPlayer p : fighters(u)) SkillFx.shake(p, 0.4, 0.8);
        for (ServerPlayer p : list) {
            if (safe.contains(p.getUUID()) && !doomed.contains(p.getUUID())) {
                BossKit.hintFace(p, "§6§l불씨가 지켜 주었습니다", Opt.of("dist", 2.4, "up", 0.45));
                continue;
            }
            encase(u, p, 20);
            BossKit.hintSay(u, "§b[보스] §e" + p.getName().getString() + "§f님이 니플헤임의 숨결에 얼어붙었습니다" + (doomed.contains(p.getUUID()) ? " §7(한 불에 너무 많이 모임)" : ""));
            Status.kill(p, u.boss);
        }
        // 글자 치우기, 꺼진 불은 relight 틱 뒤 다시 켜짐
        for (Label L : G.labels) u.api.dropProp(u.enc, L.e);
        for (Fire f : m.fires) if (!f.lit) f.relightAt = now + (long) B.num("relight");
        m.breath = null;
        m.bigUntil = now + 100;
        // 숨결 바로 뒤에 큰 공격이 겹치지 않게
        m.next.put("spikes", Math.max(m.next.get("spikes"), now + 80));
        m.next.put("icicles", Math.max(m.next.get("icicles"), now + 120));
    }

    // ------------------------------------------------------------ 핌불베트르 (체력 20% 아래)

    public static void startWinter(Bosses.Unit u, long now) {
        Obj W = u.def.obj("winter");
        M m = mem(u);
        m.winter = true;
        u.cdMult = W.num("cd");
        m.body.want = null;
        u.api.anim(u, "roar");
        sound(u, snd(u, "roar"), headAt(u), 0.7, 1);
        play(u.dim, "ambient.weather.thunder", u.center, 0.6, 0.8);
        Vec3 b = bodyOf(u);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(b.x, u.center.y + 0.1, b.z), Opt.of("dir", Vec3.ZERO, "size", 10.0, "life", 1.0, "color", FROST, "alpha", 0.95));
        title(u, "§b§l핌불베트르", "§f끝나지 않는 겨울 — 눈보라가 몰아칩니다", targets(u));
        announce(u, "winter", "§b[보스] 핌불베트르: §f이미르가 끝나지 않는 겨울을 부릅니다. 눈보라 속에서 패턴이 빨라지고 얼음 가시 줄·고드름이 늘어납니다");
        u.api.cast(u, "winter", 40);
        m.next.put("spikes", Math.max(m.next.get("spikes"), now + 80));
        m.next.put("icicles", Math.max(m.next.get("icicles"), now + 140));
    }

    /** 핌불베트르 동안: 사람마다 눈보라 (1초마다) */
    static void winterTick(Bosses.Unit u, long now) {
        M m = mem(u);
        if (!m.winter || m.breath != null || now % 20 != 3) return;
        for (ServerPlayer p : targets(u)) {
            SkillFx.fx(u.dim, "rpg:snowfall", new Vec3(p.getX() - 2, p.getY() + 4, p.getZ() - 1), Opt.of("dir", new Vec3(0.6, -0.8, 0.2), "speed", 6.0, "size", 9.0, "life", 1.0,
                    "color", SNOW, "alpha", 0.8));
        }
    }

    // ------------------------------------------------------------ 안개 (숨결 = 짙은 눈보라, 핌불베트르 = 옅은 눈보라 — 엔진의 보스 안개)

    /** 지금 필드 둘레 사람에게 씌울 안개 (없으면 null) */
    public static String fogWant(Bosses.Unit u) {
        M m = mem(u);
        if (m == null) return null;
        if (m.breath != null && "inhale".equals(m.breath.stage)) return FOG_BREATH;
        return m.winter ? FOG_WINTER : null;
    }

    // ------------------------------------------------------------ 높은 곳 · 둘레 연출

    /**
     * 첨탑 · 성문 기둥 위처럼 높은 곳 (선 자리의 바닥이 필드 바닥보다 high.above 칸 넘게) 에 grace 틱 넘게 머문 사람: 몽둥이로 쳐서 끌어내림
     * (사람마다 cd 틱에 한 번, 얼음에 갇힌 사람은 아님). 이미르가 공격하지 못하는 동안 (등장 · 무릎 꿇기 · 뛰기 · 숨결) 은 새로 세거나 치지 않고 내려온 사람의 기록만 지움
     * (그 전부터 머문 사람은 쉬는 게 끝나면 바로 맞을 수 있음, 그동안 올라간 사람은 끝난 뒤 경고부터)
     */
    static void highTick(Bosses.Unit u, long now) {
        if (now % 10 != 0 || !u.alive || !Bosses.valid(u.boss)) return;
        Obj d = u.def.obj("high");
        M m = mem(u);
        boolean paused = !canAttack(u, now);
        Vec3 c = u.center;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : targets(u)) {
            UUID id = p.getUUID();
            // 선 자리의 바닥 높이 (뛰는 중이면 발밑 블록 위). 올라가 있던 사람이 뛰는 중에 발밑이 낮게 잡히면 (가장자리에서 뛰기) 기록을 그대로 둠 — 내려와 땅을 디뎌야 지움
            boolean up = groundUnder(p, p.getY()) - c.y >= d.num("above");
            if (!up && !(!p.onGround() && m.highSince.containsKey(id))) continue;
            seen.add(id);
            if (paused) continue;
            if (!m.highSince.containsKey(id)) {
                m.highSince.put(id, now);
                BossKit.hintFace(p, "§b§l내려오세요! §f머물면 몽둥이로 칩니다", Opt.of("dist", 2.2, "up", 0.35));
            }
            // 치는 것은 높은 곳을 디디고 있거나 그 위에서 뛰는 동안만 (내려가는 중은 아님)
            if (!up || now - m.highSince.get(id) < d.num("grace")) continue;
            // 얼음에 갇힌 사람은 얼음이 막아 줌 (셈은 이어짐)
            if (!open(p)) continue;
            if (now - m.high.getOrDefault(id, -1000L) < d.num("cd")) continue;
            m.high.put(id, now);
            Vec3 dir = dirTo(p.position(), c);
            SkillFx.fx(u.dim, "rpg:slash_x", new Vec3(p.getX(), p.getY() + 1.2, p.getZ()), Opt.of("dir", new Vec3(-dir.x, 0, -dir.z), "speed", 0.0, "spin", 0.0, "size", 1.8,
                    "life", 0.3, "color", ICE, "alpha", 0.95));
            shatter(u.dim, p.position(), 1.6, ICE);
            play(u.dim, snd(u, "hit"), p.position(), 1.2, 0.8);
            // 때린 엔티티 없이 (게임의 밀어내기는 보스에게서 먼 쪽 = 벽 밖일 수 있음): 가운데 쪽으로만 끌어내림
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 1.1, dir.z * 1.1, 0.35), "noSource", true));
            BossKit.hintFace(p, "§b§l얻어맞고 떨어집니다", Opt.of("dist", 2.2, "up", 0.35));
            explain(u, "high", "§b[보스] §f첨탑·성문 기둥 위처럼 높은 곳에 " + Js.toFixed(d.num("grace") / 20, 1) + "초 넘게 머물면 이미르가 몽둥이로 쳐서 떨어뜨립니다");
        }
        m.highSince.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 둘레 연출: 입김 (1.5초마다) */
    static void ambientTick(Bosses.Unit u, long now) {
        M m = mem(u);
        if (now % 30 != 0 || m.leap != null || m.body.h < -0.5) return;
        Vec3 h = headAt(u);
        Vec3 f = dirOf(m.body.yaw);
        SkillFx.fx(u.dim, "rpg:breath", new Vec3(h.x + f.x * 0.6, h.y - 0.6, h.z + f.z * 0.6), Opt.of("dir", new Vec3(f.x, -0.2, f.z), "speed", 2.5, "size", 1.6, "life", 1.0,
                "spin", random() * 360, "color", FROST, "alpha", 0.6));
    }

    // ------------------------------------------------------------ 등장 (호수 밑에서 솟아오름)

    static void arrive(Bosses.Enc enc) {
        Obj A = enc.def.obj("arrive");
        Vec3 c = enc.center;
        int rise = (int) A.num("rise");
        int roar = (int) A.num("roar");
        double depth = A.num("depth");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 1; k <= rise; k++) {
            int kk = k;
            steps.add(step(k, (x, n) -> {
                double t = (double) kk / rise;
                // 처음엔 빨리, 끝에서 느리게 (얼음을 밀고 올라옴)
                mem(x).body.h = -depth * (1 - t) * (1 - t);
                if (kk % 6 == 0) {
                    double a = random() * TAU;
                    double r = 1 + random() * 3.5;
                    shatter(x.dim, floorAt(x, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r), 1.8, ICE);
                    play(x.dim, snd(x, "ice"), c, 0.6 + t * 0.4, 1);
                }
                if (kk % 10 == 0) shakeNear(x, c, 24, 0.12 + t * 0.2, 0.5);
            }));
        }
        steps.add(step(rise, (x, n) -> {
            mem(x).body.h = 0;
            landFx(x, floorAt(x, c.x, c.z), 6, FROST);
            hold(x, "roar_hold");
            sound(x, snd(x, "roar"), headAt(x), 0.7, 1);
        }));
        steps.add(step(rise + roar, (x, n) -> {
            x.immune = false;
            release(x, "rest");
            Obj C = x.def.obj("crack");
            explain(x, "crack", "§b[보스] 균열: §f이미르의 얼음 다리를 가까이에서 때리면 금이 갑니다 §7(멀리서 때린 것은 " + Js.str(Js.round(C.num("far") * 100)) + "%만)§f. §e금이 가득 차면 무릎을 꿇고 "
                    + Js.str(Js.round(C.num("time") / 20)) + "초 동안 받는 피해 +" + Js.str(Js.round(C.num("bonus") * 100)) + "%");
        }));
        enc.api.cast(enc, "arrive", rise + roar + 2, steps);
    }

    // ------------------------------------------------------------ 모듈

    /** 땅에 내려놓음 (뛰다가 쓰러지거나 싸움이 끝날 때): 높이 0, 걸음 0, 들고 있던 모습 (뛰기 · 숨결 · 무릎) 을 풂 */
    static void ground(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null || m.body == null || !Bosses.valid(enc.boss)) return;
        Body b = m.body;
        m.leap = null;
        b.h = 0;
        b.gaitTo = 0;
        b.gait = 0;
        place(enc);
        b.gaitSent = sendProp(enc, "rpg:gait", 0, null, 0.05);
        release(enc, "rest");
    }

    /** 무릎 꿇은 동안 받는 피해 +bonus ("빈틈") · 균열 (몸 가까이 = h.near: 근접 보너스 거리, 보너스를 꺼도 같음) */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        M m = mem(u);
        if (m == null || m.body == null) return null;
        long now = Tasks.now();
        if (now < m.kneelUntil) return Bosses.Hurt.of(amount * (1 + u.def.obj("crack").num("bonus")), OPEN_MARK);
        addCrack(u, amount, h != null && h.near, now);
        return null;
    }

    /** 원거리 견제: 등장 · 숨결 · 뛰는 동안 · 무릎 꿇은 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        return canAttack(u, now);
    }

    /** 던지는 곳: 왼손 (몽둥이는 오른손) */
    @Override
    public Vec3 farFrom(Bosses.Unit u, Obj style) {
        return mem(u) != null && mem(u).body != null ? leftHandAt(u) : null;
    }

    /** 엔진이 이 사람의 보스 안개를 걷었음 (세계에 들어올 때): 기억도 지움 → 필드 둘레면 0.5초 안에 다시 씌움 */
    @Override
    public void fogCleared(Bosses.Enc enc, ServerPlayer p) {
        if (enc.mem != null && enc.mem.fog != null) enc.mem.fog.remove(p.getUUID());
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        Vec3 c = enc.center;
        Vec3 p = enc.starter != null && !enc.starter.isRemoved() ? enc.starter.position() : new Vec3(c.x, c.y, c.z + 8);
        double yaw = angOf(dirTo(c, p));
        Obj A = d.obj("arrive");
        long arrive = (long) (A.num("rise") + A.num("roar"));
        M m = new M();
        m.next.put("slam", now + arrive + 20);
        m.next.put("stomp", now + (long) d.obj("stomp").num("first"));
        m.next.put("spikes", now + (long) d.obj("spikes").num("first"));
        m.next.put("icicles", now + (long) d.obj("icicles").num("first"));
        m.bigUntil = now + arrive + 100;
        m.body = new Body(c.x, c.z, -A.num("depth"), yaw);
        for (Vec3 f : enc.feat.fires()) m.fires.add(new Fire(new Vec3(f.x, f.y, f.z)));
        enc.mem = m;
        for (Bosses.Unit u : enc.units) u.mem = m;
        // 모닥불은 모두 켜고 시작 (지난 싸움이 꺼진 채 끝났어도)
        for (Fire f : m.fires) setFire(enc, f, true);
        // 엔진 평타는 쓰지 않음 (몸을 돌리는 내려치기는 모듈이)
        enc.meleeAt = Long.MAX_VALUE;
        enc.immune = true;
        place(enc);
        bodyTick(enc);
        hold(enc, "emerge");
        sound(enc, snd(enc, "summon"), c, 0.6, 1);
        arrive(enc);
    }

    /** 시험 · 사진첩의 forceNext: 그 패턴 (시작했으면 true) */
    private static boolean forced(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        return switch (id) {
            case "slam" -> slam(u, now, list);
            case "stomp" -> stomp(u, now, list);
            case "spikes" -> spikes(u, now, list);
            case "icicles" -> icicles(u, now, list);
            case "breath", "breath2" -> {
                breath(u, now, list, id.equals("breath2"));
                yield true;
            }
            case "winter" -> {
                startWinter(u, now);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        Obj d = u.def;
        if (m == null || m.body == null || m.leap != null) return false;
        String f = forceNext;
        if (f != null) {
            forceNext = null;
            return forced(u, f, now, list);
        }
        double r = u.api.ratio(u);
        // 1) 니플헤임의 숨결 (큰 패턴)
        if (now >= m.bigUntil) {
            List<Double> at = d.obj("breath").numbersOrNull("at");
            for (int i = 0; at != null && i < at.size(); i++) {
                double x = at.get(i);
                if (r <= x && !m.breathDone.contains(x)) {
                    m.breathDone.add(x);
                    breath(u, now, list, i > 0);
                    return true;
                }
            }
        }
        // 2) 핌불베트르
        if (!m.winter && r <= d.obj("winter").num("from")) {
            startWinter(u, now);
            return true;
        }
        // 3) 얼음 가시 · 고드름 비 (서로 바로 이어지지 않게)
        Obj sp = d.obj("spikes");
        Obj ic = d.obj("icicles");
        if (now >= m.next.get("spikes")) {
            if (spikes(u, now, list)) {
                m.next.put("spikes", now + every(u, BossKit.pair(sp, "every")));
                m.next.put("icicles", Math.max(m.next.get("icicles"), now + (long) sp.num("windup") + 60));
                return true;
            }
            m.next.put("spikes", now + 20);
        }
        if (now >= m.next.get("icicles")) {
            if (icicles(u, now, list)) {
                m.next.put("icicles", now + every(u, BossKit.pair(ic, "every")));
                m.next.put("spikes", Math.max(m.next.get("spikes"), now + (long) ic.num("windup") + 60));
                return true;
            }
            m.next.put("icicles", now + 20);
        }
        // 4) 발 구르기 (옆 · 뒤에 붙은 사람)
        Obj st = d.obj("stomp");
        if (now >= m.next.get("stomp")) {
            if (stomp(u, now, list)) {
                m.next.put("stomp", now + every(u, BossKit.pair(st, "every")));
                m.next.put("slam", Math.max(m.next.get("slam"), now + (long) st.num("windup") + 16));
                return true;
            }
            m.next.put("stomp", now + 20);
        }
        // 5) 평타: 몽둥이 내려치기
        if (now >= m.next.get("slam") && slam(u, now, list)) {
            m.next.put("slam", now + Math.round(d.obj("melee").num("every") * u.cdMult));
            return true;
        }
        return false;
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        Bosses.Unit u = enc;
        M m = mem(enc);
        if (!u.alive || !Bosses.valid(u.boss) || m == null || m.body == null) return;
        leapTick(u, now);
        iceTick(u, now);
        fireTick(u, now);
        breathTick(u, now);
        winterTick(u, now);
        highTick(u, now);
        ambientTick(u, now);
        BossKit.syncBossFog(u, fogWant(u), now);
        if (!enc.alive) return;
        // 몸: 뛰는 중에는 leapTick 이, 등장 · 패턴 중에는 제자리 (원하는 쪽으로 돎), 패턴이 없으면 걸음
        if (m.leap == null) {
            if (u.cast != null) standTick(u);
            else {
                m.body.want = null;
                walkTick(u, list != null ? list : targets(u), now);
            }
        }
        bodyTick(u);
    }

    /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 뛰다가 쓰러지면 땅에 내려놓음, 몸 둘레 얼음이 부서짐 */
    @Override
    public void dying(Bosses.Enc enc) {
        ground(enc);
        M m = mem(enc);
        if (m == null || m.body == null) return;
        Vec3 b = bodyOf(enc);
        for (int i = 0; i < 3; i++) shatter(enc.dim, new Vec3(b.x, enc.center.y + i * 2.5, b.z), 3.2, ICE);
        play(enc.dim, snd(enc, "ice"), b, 0.5, 1);
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null || m.body == null) return;
        ground(enc);
        BossKit.clearBossFog(enc);
        // 꺼진 모닥불은 모두 다시 켬 (필드를 처음 모습으로)
        for (Fire f : m.fires) {
            f.relightAt = null;
            if (!f.lit) setFire(enc, f, true);
        }
        m.ice.clear();
        m.breath = null;
        m.kneelUntil = 0;
        enc.immune = false;
    }

    // ------------------------------------------------------------ 시험용: 지금 숨결 · 균열 · 모닥불 · 얼음 덩어리 · 뛰는 중

    public static Breath breathOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.breath;
    }

    public static Crack crackOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.crack;
    }

    public static List<Fire> firesOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? List.of() : m.fires;
    }

    public static List<Ice> iceOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? List.of() : m.ice;
    }

    public static Leap leapOf(Bosses.Unit u) {
        M m = mem(u);
        return m == null ? null : m.leap;
    }

    /** 태초의 빙원 치수 (arena.js glacierSize 를 다시 내보낸 것과 같음) */
    public static ArenaThemes.Glacier glacierSize() {
        return ArenaThemes.glacierSize(KEY);
    }
}
