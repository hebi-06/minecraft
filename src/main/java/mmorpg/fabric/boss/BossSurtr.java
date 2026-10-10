package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.audience;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.faceAll;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.groundUnder;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.lobArc;
import static mmorpg.fabric.boss.BossKit.meleeTarget;
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.say;
import static mmorpg.fabric.boss.BossKit.showFaceText;
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
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 수르트 (boss_surtr.js — v2.12.0, Lv.50, 첫 초월 보스): 수치는 config BOSSES.surtr.
 * 불꽃 검 레바테인을 오른손에 든 무스펠헤임의 불의 거인 (크기 ×10, 키 약 33칸). 무스펠헤임의 신전 (반지름 44) 에서 싸움.
 * 움직이지 않는 보스 (중력 · 충돌 없음) 를 모듈이 매 틱 옮김 (엔진 pose): 가장 가까운 사람 쪽으로 몸을 돌려 걷고 (가운데에서 drive.keep 칸 안), 라그나로크 때 뛰어감.
 * 몸 (엔티티 자리) = 발 사이, 오른 어깨 = 몸 오른쪽 drive.arm 칸 (레바테인이 떨어지는 줄). 모델에서 잰 자리:
 * 내려찍기 끝 = 칼끝이 어깨 앞 약 37.5칸 바닥 (melee.reach), 불의 주먹 = 몸 앞 13.4 · 왼쪽 7칸 (fist.at), 쓰러짐 = 머리가 몸 앞 약 13~16칸 바닥 ({@link #CROWN_AT}).
 * 엔티티 속성: rpg:gait (걸음) · rpg:heat (0~1 달아오름 → 붉은 주황 덧칠) · rpg:sword_broken · rpg:fist_broken (텍스처) · rpg:crown_broken (머리 불꽃 숨김).
 * <p>
 * 깨는 맛 (사용자 요청: 무력화 · 부위 파괴 · 신전 구조물 활용 · 반격 타이밍)
 * <ul>
 * <li>레바테인 내려찍기 (평타): 오른 어깨에서 대상 쪽 긴 직사각형 → 피해 + 밀림. 칼이 박힌 동안 칼끝 쪽 "레바테인의 핵" (맞는 소품) → 부수면 부위 파괴.
 * 칼이 온전하면 내려찍은 자리가 한동안 불탐. 칼 줄 위의 온전한 봉인의 사슬 기둥 (몸에서 bind.min 칸 넘게) 이 칼을 붙잡음 → 속박 (받는 피해 +bonus, 칼의 핵),
 * 기둥 뒤는 맞지 않음, 기둥은 부서짐 (싸움이 끝나면 다시 섬)</li>
 * <li>횡베기 · 찌르기 (반격): 예고 마지막 window 틱 동안 칼이 금빛 — 그때 스킬 · 룬으로 수르트를 때리면 패턴이 끊기고 비틀거림 (받는 피해 +bonus "반격")</li>
 * <li>불의 주먹 (건틀릿의 핵) · 무스펠의 불비 · 불의 고리 (뛰어넘기) · 발 구르기 · 높은 곳 (불기둥) · 원거리 견제 (불덩이, 왼손)</li>
 * <li>무스펠의 업화 (체력 80 · 50 · 20%, 무력화): 업화 막대 (보스 막대 소품) 를 피해로 다 깎으면 쓰러짐 (빈틈 +bonus, 불꽃 왕관의 핵), 못 깎으면 업화 폭발</li>
 * <li>라그나로크 (체력 65 · 35%): 온전한 니플헤임의 얼음 결정 뒤 (수르트 반대쪽 그림자) 만 안전 — 즉사 (레바테인이 부서졌으면 큰 마법 피해). 쓴 결정은 녹음.
 * 들고 있는 동안 담장 밖으로 나가면 끌려 들어오고, 불길은 가운데에서 RAG_REACH 칸까지 퍼짐 (불길이 지나간 뒤에 들어온 사람은 판정하지 않음)</li>
 * <li>세계를 태우는 불꽃 (체력 50% 아래): 재사용 대기 × cd, 주는 피해 × dmg</li>
 * </ul>
 * 큰 패턴 (업화 · 라그나로크) 은 틀이 없을 때 (pick). 엔진 평타는 쓰지 않음 (몸을 돌리는 내려찍기를 모듈이).
 * <p>
 * 자바에서 달라진 점: 부위의 핵 이름표 (두 줄 — 부위 이름 + 막대) 는 핵 위의 글자 표시 엔티티 (자바 이름표는 한 줄), 신전 블록 (녹이기 · 무너뜨리기 · 되돌리기) 은
 * 짓기와 같은 방식 (ArenaBlocks · ArenaBuild.FLAGS — 이웃을 건드리지 않아 매단 랜턴이 떨어지지 않음), later 는 엔진의 틀 (Bosses.Cast.steps) 에 끼워 넣음,
 * 월드의 플레이어 = 서버 플레이어 목록, 끌어올 때 보는 쪽은 가로만. 패턴 함수는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴,
 * {@link #skipArrive} = 등장을 건너뜀 (시험).
 */
public final class BossSurtr implements BossModule {
    static final String KEY = "surtr";
    /** 동작 틀: 솟아오르기 · 업화 · 쓰러짐 · 속박 · 라그나로크 · 뛰기는 다음 동작이 이어받을 때까지 마지막 모습 유지 (쉬기 rest 가 풂) */
    public static final String HOLD = "rpg_hold";
    /** 드러난 부위의 핵 (맞는 소품) · 업화 막대 (보스 막대만 보이는 소품) */
    public static final String CORE_ID = "rpg:prop_surtr_core";
    public static final String GAUGE_ID = "rpg:prop_surtr_gauge";
    /** 업화 막대 소품의 최대 체력 (build.py 와 같음): 막대 = 남은 업화 비율 × 이 값 */
    public static final int GAUGE_MAX = 1000;
    /** 안개 (리소스 팩 fogs/ — BossKit.FOGS): 라그나로크 = 붉은 하늘, 업화 = 주황 열기 */
    public static final String FOG_RAGNAROK = "rpg:surtr_ragnarok";
    public static final String FOG_INFERNO = "rpg:surtr_inferno";
    /** 피해 숫자 앞 표시 (받는 피해가 늘어난 동안, 근접 보너스 » 앞) */
    public static final String MARK_GROGGY = "§6빈틈 ";
    public static final String MARK_BIND = "§b속박 ";
    public static final String MARK_COUNTER = "§e반격 ";
    /** 부위 이름 · 핵 소품의 속성 rpg:part 값 (텍스처 색) */
    public static final Map<String, String> PART_NAMES = Map.of("sword", "레바테인", "fist", "왼 건틀릿", "crown", "불꽃 왕관");
    public static final Map<String, Integer> PART_INDEX = Map.of("sword", 0, "fist", 1, "crown", 2);
    public static final List<String> PART_ORDER = List.of("sword", "fist", "crown");
    /** 부위를 부수면 바뀌는 것 (알림) */
    static final Map<String, String> PART_EFFECT = Map.of("sword", "불길이 꺼지고 라그나로크가 즉사하지 않음", "fist", "불의 주먹이 사라지고 불비가 약해짐", "crown",
            "업화를 막기 쉬워지고 불의 고리가 줄어듦");
    /** 쓰러졌을 때 머리 (불꽃 왕관) 가 닿는 자리: 몸 앞 칸 (build.py collapse 자세의 머리 앞 끝) */
    public static final double CROWN_AT = 15;
    /** 패턴 사이 쉬는 틱 (틀 길이에 더함: 거인의 다음 동작이 읽히게) */
    public static final int GAP = 14;
    /** 라그나로크를 받는 거리 (필드 가운데에서 칸, 명단에 있는 사람): 불길이 여기까지 퍼짐 (담장 밖으로 달아나도 피하지 못함) */
    public static final double RAG_REACH = 64;
    /** 라그나로크를 들고 있는 동안 (10틱마다) 담장 안으로 끌어오는 거리 (명단에 있는 사람, 같은 차원) */
    public static final double RAG_PULL = 128;
    /** 핵 소품 둘레에 비워 둘 칸 (가운데에서 체비셰프): 서 있는 사슬 기둥 (3×3 기둥 반폭 1.5 + 핵의 맞는 상자 반폭 1.5) · 얼음 결정 (팔 끝 2.5 + 1.5) · 담장 (반지름 - 이 값) */
    public static final double CLEAR_CHAIN = 3;
    public static final double CLEAR_CRYSTAL = 4;
    public static final double CLEAR_WALL = 2.5;
    /** 핵 이름표 (글자 표시 엔티티) 높이 (핵 자리에서 칸 — 떠서 도는 결정 (0.8 ~ 2.2칸) 위) */
    static final double LABEL_Y = 3.2;
    /** 핵 이름표 글자 모양: 그림자 · 앞에 그림 (베드락 이름표처럼 결정 · 블록에 가리지 않음) */
    static final byte LABEL_FLAGS = (byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH);
    /** 핵 이름표 엔티티의 표 */
    public static final String LABEL_TAG = "rpg_surtr_core_label";

    static final double[] RED = {1.0, 0.22, 0.16};
    static final double[] FIRE = {1.0, 0.45, 0.08};
    static final double[] LAVA = {1.0, 0.3, 0.05};
    static final double[] EMBER = {1.0, 0.62, 0.2};
    static final double[] GOLD = {1.0, 0.84, 0.3};
    static final double[] ICE = {0.62, 0.9, 1.0};
    static final double[] SAFE = {0.35, 0.8, 1.0};
    static final double[] ASH = {0.32, 0.28, 0.26};
    static final double[] CHAIN = {0.72, 0.78, 0.9};
    static final double[] STEAM = {0.92, 0.94, 0.96};
    static final double[] WHITE = {1, 1, 1};
    static final double[] CRACK = {0.16, 0.08, 0.06};
    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;
    static final Vec3 UP = new Vec3(0, 1, 0);
    static final Vec3 Z_AXIS = new Vec3(0, 0, 1);

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (slam · sweep · thrust · fist · meteors · rings · stomp · inferno · ragnarok · enrage — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> propIds() {
        return List.of(CORE_ID, GAUGE_ID);
    }

    // ------------------------------------------------------------ 기억

    /** 몸 (엔티티 자리): 가로 자리 · 뛰는 높이 h (등장 때는 화구 밑 = 음수) · 보는 쪽 yaw (라디안, +x 에서 +z 쪽으로) · 패턴이 돌리려는 쪽 · 걸음 */
    public static final class Body {
        public double x;
        public double z;
        public double h;
        public double yaw;
        public Double want;
        public double gait;
        public double gaitTo;
        Double gaitSent;
        double[] placed;

        Body(double x, double z, double h, double yaw) {
            this.x = x;
            this.z = z;
            this.h = h;
            this.yaw = yaw;
        }
    }

    /** 뛰기 (라그나로크 자리로): 처음 자리 · 높이, 내려앉을 자리, 시작 틱, 걸리는 틱, 높이 */
    public record Leap(double x0, double z0, double h0, double x1, double z1, long t0, int time, double height) {}

    /** 부위 하나: 남은 체력 (싸움 동안 이어짐) · 부서졌는지 · 부순 사람 */
    public static final class Limb {
        public double hp;
        public boolean broken;
        public String by = "";

        Limb(double hp) {
            this.hp = hp;
        }
    }

    /** 드러난 부위의 핵: 부위 · 핵 엔티티 · 거두는 틱 · 이름표 (글자 표시 엔티티) */
    public static final class Core {
        public final String kind;
        public final BedrockMob e;
        public final long until;
        Display.TextDisplay label;
        String text;

        Core(String kind, BedrockMob e, long until) {
            this.kind = kind;
            this.e = e;
            this.until = until;
        }

        /** 이름표 글자 (베드락 서식, 없으면 null — 시험용) */
        public String labelText() {
            return text;
        }

        /** 이름표 엔티티 (없으면 null — 시험용) */
        public Display.TextDisplay label() {
            return label != null && !label.isRemoved() ? label : null;
        }
    }

    /** 반격 창: 패턴 (sweep · thrust) · 열린 틱 · 닫히는 틱 */
    public record Counter(String kind, long from, long until) {}

    /** 직사각형 (가운데 줄 a → b, 가로 단위 방향 dir) */
    public record Rect(Vec3 a, Vec3 b, Vec3 dir) {}

    /** 불길 (내려찍은 자리): 직사각형 · 꺼지는 틱 · 다음 피해 틱 */
    public static final class Trail {
        public final Rect r;
        public final long until;
        long next;

        Trail(Rect r, long until, long next) {
            this.r = r;
            this.until = until;
            this.next = next;
        }
    }

    /** 불의 고리 하나: 가운데 · 시작 틱 · 지난 앞머리 · 끝 · 맞은 사람 */
    public static final class FireRing {
        final double x;
        final double z;
        final long t0;
        double last;
        final double max;
        final Set<UUID> hit = new HashSet<>();

        FireRing(double x, double z, long t0, double max) {
            this.x = x;
            this.z = z;
            this.t0 = t0;
            this.max = max;
        }
    }

    /** 용암 분출 하나 (업화 동안): 자리 · 터지는 틱 */
    public record Erupt(Vec3 at, long hitAt) {}

    /** 무스펠의 업화: 깎을 피해 · 남은 것 · 시작 · 끝 · 업화 막대 소품 · 다음 분출 · 분출들 · 막대에 보인 값 */
    public static final class Inferno {
        public final double need;
        public double left;
        public final long t0;
        public final long until;
        public final BedrockMob gauge;
        long nextErupt;
        List<Erupt> erupts = new ArrayList<>();
        int shown = -1;

        Inferno(double need, long t0, long until, BedrockMob gauge, long nextErupt) {
            this.need = need;
            this.left = need;
            this.t0 = t0;
            this.until = until;
            this.gauge = gauge;
            this.nextErupt = nextErupt;
        }
    }

    /** 세계를 태우는 불길: 시작 틱 · 퍼지는 곳 · 끝 거리 · 앞머리가 아직 닿지 않은 사람 */
    public static final class Wave {
        final long t0;
        public final Vec3 from;
        final double max;
        final Set<UUID> ahead;

        Wave(long t0, Vec3 from, double max, Set<UUID> ahead) {
            this.t0 = t0;
            this.from = from;
            this.max = max;
            this.ahead = ahead;
        }
    }

    /** 라그나로크: 단계 (leap · raise · wave) · 두 번째인지 · 명단 · 빠진 사람 · 판정한 사람 · 산 · 죽은 수 · 불길 · 먼저 녹인 결정 · 든 틱 · 내리치는 틱 */
    public static final class Ragnarok {
        public String stage = "leap";
        public final boolean second;
        final Set<UUID> who;
        final Set<UUID> gone = new HashSet<>();
        final Set<UUID> judged = new HashSet<>();
        public int saved;
        public int killed;
        public Wave wave;
        public final List<Struct> melted = new ArrayList<>();
        public long t0;
        public long until;

        Ragnarok(boolean second, Set<UUID> who) {
            this.second = second;
            this.who = who;
        }
    }

    /** 신전 구조물 (봉인의 사슬 기둥 · 니플헤임의 얼음 결정): 가운데 · 반폭 · 높이 · 블록 · 서리 바닥, 온전한지 · 무너졌는지 · 녹았는지 · 이번 불길에 숨겨 줬는지 */
    public static final class Struct {
        public final Vec3 at;
        public final double half;
        public final int h;
        public final List<Arena.Cell> cells;
        public final List<Arena.Cell> frost;
        public boolean intact = true;
        public boolean broken;
        public boolean melted;
        public boolean used;

        Struct(Vec3 at, double half, int h, List<Arena.Cell> cells, List<Arena.Cell> frost) {
            this.at = at;
            this.half = half;
            this.h = h;
            this.cells = cells;
            this.frost = frost;
        }
    }

    /** 칼 줄 위의 사슬 기둥: 기둥 · 줄 시작에서 기둥 가운데까지 */
    public record Pin(Struct c, double along) {}

    /** 받는 피해 보너스 (가장 큰 것 하나) */
    public record Bonus(double bonus, String mark) {}

    /** 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 큰 패턴 쉼 · 알린 규칙 · 몸 · 뛰기 · 부위 · 핵 · 반격 · 빈틈 · 속박 · 불길 · 고리 · 업화 · 라그나로크 · 구조물 · 높은 곳 (안개는 Bosses.Mem) */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new HashMap<>();
        public long bigUntil;
        final Set<String> told = new HashSet<>();
        public Body body;
        public Leap leap;
        public final Map<String, Limb> parts = new LinkedHashMap<>();
        public final List<String> brokenOrder = new ArrayList<>();
        final Map<String, Boolean> flagSent = new HashMap<>();
        Double heatSent;
        public Core core;
        public Counter counter;
        public long groggyUntil;
        public long bindUntil;
        public long counterUntil;
        public List<Trail> trails = new ArrayList<>();
        public List<FireRing> rings = new ArrayList<>();
        public Inferno inferno;
        public final Set<Double> infDone = new HashSet<>();
        public Ragnarok ragnarok;
        public final Set<Double> ragSeen = new HashSet<>();
        public int ragDone;
        public boolean enraged;
        public int counters;
        public int binds;
        public int infernoWins;
        public int infernoFails;
        public final List<Struct> chains = new ArrayList<>();
        public final List<Struct> crystals = new ArrayList<>();
        final Map<UUID, Long> high = new HashMap<>();
        final Map<UUID, Long> highSince = new HashMap<>();
    }

    /** 이 싸움의 기억 (시작 전이면 null — 시험 · 사진첩용, 애드온 stateOf) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    // ------------------------------------------------------------ 작은 도우미

    static long every(Bosses.Unit u, Obj o, String key) {
        double[] range = BossKit.pair(o, key);
        return (long) Js.round(rnd((int) range[0], (int) range[1]) * u.cdMult);
    }

    static Vec3 floorAt(Bosses.Unit u, double x, double z) {
        return new Vec3(x, u.center.y, z);
    }

    /** 가로 단위 방향 a → b (너무 가까우면 남쪽) */
    static Vec3 dirTo(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double L = Math.hypot(dx, dz);
        return L < 1e-6 ? Z_AXIS : new Vec3(dx / L, 0, dz / L);
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

    /** 같은 엔티티인지 (UUID 로) */
    static boolean sameEntity(Entity a, Entity b) {
        if (a == null || b == null) return false;
        return a == b || a.getUUID().equals(b.getUUID());
    }

    static double turnToward(double cur, double want, double step) {
        double d = wrapPi(want - cur);
        return Math.abs(d) <= step ? want : cur + Math.signum(d) * step;
    }

    /** 몸 오른쪽 단위 방향 (+x 동쪽 · +z 남쪽, 북쪽 (-z) 을 보면 오른쪽은 동쪽): 칼을 든 손 쪽 */
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

    /** 바닥 높이의 사람 (위아래 4칸 안) — 애드온 level(u, p) */
    static boolean onFloor(Bosses.Unit u, ServerPlayer p) {
        return Math.abs(p.getY() - u.center.y) < 4;
    }

    static Obj cfg(Bosses.Unit u, String key) {
        return u.def.obj(key);
    }

    static double arenaRadius(Bosses.Unit u) {
        return u.enc.def.obj("arena").num("radius");
    }

    static Limb part(M m, String kind) {
        return m.parts.get(kind);
    }

    static boolean broken(M m, String kind) {
        Limb P = m == null ? null : m.parts.get(kind);
        return P != null && P.broken;
    }

    // ------------------------------------------------------------ 연출 조각

    static void dust(ServerLevel dim, Vec3 at, double size, double[] color, double life) {
        SkillFx.fx(dim, "rpg:dust", new Vec3(at.x, at.y + size * 0.35, at.z), Opt.of("size", size, "life", life, "color", color, "alpha", 0.8));
    }

    /** 불꽃 기둥 한 장 (rpg:fire_wall: 서서 일렁이는 불꽃) */
    static void flame(ServerLevel dim, Vec3 at, double size, double life, double[] color) {
        SkillFx.fx(dim, "rpg:fire_wall", at, Opt.of("size", size, "life", life, "color", color, "alpha", 0.95, "spin", random() * 360));
    }

    /** 터지는 불 (폭발 + 충격파 + 불꽃 + 먼지) */
    static void blast(ServerLevel dim, Vec3 at, double radius, double[] color) {
        SkillFx.fx(dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1, at.z), Opt.of("dir", Z_AXIS, "size", radius * 1.1, "life", 0.45, "color", color, "alpha", 0.95));
        SkillFx.fx(dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", 0.5, "color", color, "alpha", 0.9));
        flame(dim, at, radius * 0.9, 0.6, LAVA);
        dust(dim, at, radius * 0.9, ASH, 0.8);
    }

    /** 땅이 갈라지고 불이 새어 나옴 */
    static void scorch(ServerLevel dim, Vec3 at, double size) {
        SkillFx.fx(dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", size, "life", 1.4, "color", CRACK,
                "alpha", 0.9));
        SkillFx.fx(dim, "rpg:ember", new Vec3(at.x, at.y + 0.3, at.z), Opt.of("size", size * 0.5, "life", 0.8, "speed", 2.0, "color", EMBER, "alpha", 0.9));
    }

    /** 밀어내는 힘 (kx, kz): 담장에서 5칸 안에서는 바깥쪽 성분을 빼고 조금 안쪽으로 (필드 밖 · 벽 위로 날아가지 않게) → {x, z} */
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

    /** 불붙이기 (초) */
    static void burn(ServerPlayer p, double seconds) {
        if (!(seconds > 0) || p == null || p.isRemoved()) return;
        p.igniteForSeconds((float) seconds);
    }

    /** 수르트가 공격할 수 있는 때 (원거리 견제 · 높은 곳): 등장 · 라그나로크 · 업화 · 뛰기 · 쓰러짐 · 속박 · 비틀거림이 아님 */
    static boolean canAttack(Bosses.Unit u, long now) {
        M m = mem(u);
        return m != null && m.body != null && m.ragnarok == null && m.inferno == null && m.leap == null && now >= m.groggyUntil && now >= m.bindUntil
                && now >= u.staggerUntil && !(u.cast != null && "arrive".equals(u.cast.id));
    }

    // ------------------------------------------------------------ 몸

    /** 몸 (엔티티 자리): 바닥 높이 + 뛰는 높이 h (등장 때는 화구 밑 = 음수) */
    public static Vec3 bodyOf(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y + b.h, b.z);
    }

    /** 몸의 바닥 자리 (가로 거리 · 방향용 — 애드온의 body {x, z}) */
    static Vec3 bfloor(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y, b.z);
    }

    /** 오른 어깨 밑 (레바테인 줄의 시작, 바닥 높이) — yaw null 이면 지금 보는 쪽 */
    public static Vec3 shoulderAt(Bosses.Unit u, Double yaw) {
        Body b = mem(u).body;
        Vec3 r = rightOf(yaw != null ? yaw : b.yaw);
        double arm = cfg(u, "drive").num("arm");
        return new Vec3(b.x + r.x * arm, u.center.y, b.z + r.z * arm);
    }

    /** 왼손 높이 (원거리 견제 · 불비를 던지는 곳: 칼은 오른손) */
    public static Vec3 leftHandAt(Bosses.Unit u) {
        Body b = mem(u).body;
        Vec3 r = rightOf(b.yaw);
        double arm = cfg(u, "drive").num("arm");
        return new Vec3(b.x - r.x * arm, u.center.y + b.h + 16, b.z - r.z * arm);
    }

    /** 머리 (입 높이) */
    public static Vec3 headAt(Bosses.Unit u) {
        Body b = mem(u).body;
        return new Vec3(b.x, u.center.y + b.h + 22, b.z);
    }

    /** 몸 상태대로 엔티티를 옮김 (그대로면 보내지 않음) */
    static void place(Bosses.Unit u) {
        Body b = mem(u).body;
        double[] key = {b.x, b.z, b.h, b.yaw};
        if (b.placed != null && java.util.Arrays.equals(b.placed, key) && Bosses.valid(u.boss)) return;
        b.placed = key;
        u.api.pose(u, new Vec3(b.x, u.center.y + b.h, b.z), b.yaw);
    }

    /** 몸을 x, z 에 (가운데에서 keep 칸 안으로), yaw 쪽을 보게, 땅 위. 시험 · 사진첩도 씀 */
    public static void setBody(Bosses.Unit u, double x, double z, double yaw) {
        Body b = mem(u).body;
        Vec3 c = u.center;
        double K = cfg(u, "drive").num("keep");
        double r = Math.hypot(x - c.x, z - c.z);
        b.x = r > K ? c.x + ((x - c.x) / r) * K : x;
        b.z = r > K ? c.z + ((z - c.z) / r) * K : z;
        b.yaw = yaw;
        b.h = 0;
        place(u);
    }

    /** 속성 보내기: step 넘게 바뀌었거나 끝값 (0 · 1) 에 닿았을 때만 → 보낸 값 (못 보냈으면 last) */
    static Double sendProp(Bosses.Unit u, String id, double v, Double last, double step) {
        if (last != null && (v == last || (Math.abs(v - last) < step - 1e-9 && v != 0 && v != 1))) return last;
        if (u.boss == null || u.boss.isRemoved()) return last;
        u.boss.setProp(id, v);
        return v;
    }

    /** 지금 달아오른 정도 (rpg:heat): 업화를 모으는 만큼 · 라그나로크 · 분노 */
    public static double heatOf(Bosses.Unit u, long now) {
        M m = mem(u);
        if (m.inferno != null) return clamp((now - m.inferno.t0) / cfg(u, "inferno").num("time"), 0.15, 1);
        if (m.ragnarok != null && !"leap".equals(m.ragnarok.stage)) return 0.7;
        return m.enraged ? 0.25 : 0;
    }

    /** 매 틱: 걷는 빠르기를 목표 쪽으로, 속성 보내기 (걸음 · 달아오름 · 부서진 부위) */
    static void bodyTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Body b = m.body;
        b.gait = Math.abs(b.gaitTo - b.gait) <= 0.1 ? b.gaitTo : b.gait + Math.signum(b.gaitTo - b.gait) * 0.1;
        b.gaitSent = sendProp(u, "rpg:gait", Js.round(clamp(b.gait, 0, 1) * 100) / 100, b.gaitSent, 0.05);
        m.heatSent = sendProp(u, "rpg:heat", Js.round(heatOf(u, now) * 100) / 100, m.heatSent, 0.05);
        if (u.boss == null || u.boss.isRemoved()) return;
        for (String k : PART_ORDER) {
            boolean want = broken(m, k);
            if (Boolean.valueOf(want).equals(m.flagSent.get(k))) continue;
            u.boss.setProp("rpg:" + k + "_broken", want);
            m.flagSent.put(k, want);
        }
    }

    /** 걷기: 가장 가까운 사람 쪽으로 몸을 돌리고, 거의 그쪽을 보면 걸어감 (near 칸 안이면 멈춤, 가운데에서 keep 칸 밖으로는 나가지 않음) */
    static void walkTick(Bosses.Unit u, List<ServerPlayer> list, long now) {
        Obj D = cfg(u, "drive");
        Body b = mem(u).body;
        Vec3 bp = bfloor(u);
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
            if (best > D.num("near") && Math.abs(wrapPi(want - b.yaw)) < 45 * DEG) {
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
        // 발걸음: 쿵 (반 걸음마다, 둘레 사람은 흔들림)
        if (b.gaitTo > 0 && now % 36 == 0) {
            Vec3 at = bodyOf(u);
            play(u.dim, snd(u, "step"), at, 0.5, 1);
            dust(u.dim, floorAt(u, at.x, at.z), 4, ASH, 0.8);
            shakeNear(u, at, 22, 0.08, 0.3);
        }
    }

    /** 패턴 동안: 원하는 쪽 (body.want) 이 있으면 그쪽으로 몸을 돌림 (제자리) */
    static void standTick(Bosses.Unit u) {
        Body b = mem(u).body;
        b.gaitTo = 0;
        if (b.want != null) b.yaw = turnToward(b.yaw, b.want, cfg(u, "drive").num("turn") * DEG);
        place(u);
    }

    /** l 이 몸 방향에서 몇 도 떨어져 있는지 (라디안, 0 ~ π) */
    static double offAngle(Bosses.Unit u, Vec3 l) {
        Body b = mem(u).body;
        return Math.abs(wrapPi(angOf(dirTo(bfloor(u), l)) - b.yaw));
    }

    /** 지금 틀에 ticks 뒤 단계를 끼워 넣음 (틀이 없으면 무시 — 엔진의 Cast.steps 에 시간 순서대로) */
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

    // ------------------------------------------------------------ 뛰기 (라그나로크 자리로)

    static void jump(Bosses.Unit u, Vec3 to, int time, double height) {
        M m = mem(u);
        Body b = m.body;
        m.leap = new Leap(b.x, b.z, b.h, to.x, to.z, Tasks.now(), Math.max(1, time), height);
        b.want = angOf(dirTo(bfloor(u), to));
        play(u.dim, snd(u, "swing"), bodyOf(u), 0.4, 1);
        if (b.h <= 0.01) dust(u.dim, floorAt(u, b.x, b.z), 7, ASH, 1.0);
    }

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
        if (b.want != null) b.yaw = turnToward(b.yaw, b.want, cfg(u, "drive").num("turn") * 2 * DEG);
        if (k >= 1) {
            m.leap = null;
            b.h = 0;
        }
        place(u);
    }

    /** 내려앉은 자리 · 쿵 연출 */
    static void landFx(Bosses.Unit u, Vec3 at, double radius) {
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", 0.8, "color", FIRE, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2, "life", 1.6, "color",
                CRACK, "alpha", 0.9));
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * TAU;
            Vec3 p = new Vec3(at.x + Math.cos(a) * radius * 0.7, at.y, at.z + Math.sin(a) * radius * 0.7);
            dust(u.dim, p, 4, ASH, 1.0);
            if (i % 2 == 0) flame(u.dim, p, 3, 0.6, LAVA);
        }
        play(u.dim, snd(u, "hit"), at, 0.5, 1);
        play(u.dim, snd(u, "lava"), at, 0.6, 1);
        shakeNear(u, at, 40, 0.45, 0.9);
    }

    // ------------------------------------------------------------ 바닥 예고

    /**
     * 직사각형 예고 (가운데 줄을 따라 몇 줄의 판, 테두리는 진하게): R = {a, b, dir}, 폭 width.
     * 줄 간격 1.6칸 · 줄 수는 폭에 맞게 (모바일 파티클 수)
     */
    static void rectMarks(Bosses.Unit u, Rect R, double width, int ticks, double[] color) {
        double L = hdist(R.a(), R.b());
        int n = Math.max(2, (int) Math.ceil(L / 1.6));
        Vec3 r = rightOf(angOf(R.dir()));
        double life = Math.max(0.1, ticks / 20.0);
        int cols = Math.max(3, (int) Math.ceil(width / 1.7) + 1);
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            Vec3 c = new Vec3(R.a().x + (R.b().x - R.a().x) * t, R.a().y, R.a().z + (R.b().z - R.a().z) * t);
            for (int k = 0; k < cols; k++) {
                double s = ((double) k / (cols - 1)) * 2 - 1;
                boolean edge = k == 0 || k == cols - 1 || i == 0 || i == n;
                Vec3 at = new Vec3(c.x + r.x * s * (width / 2 - 0.4), c.y + 0.07, c.z + r.z * s * (width / 2 - 0.4));
                SkillFx.fx(u.dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", at, Opt.of("dir", Vec3.ZERO, "size", edge ? 0.6 : 1.6, "life", life, "color", color, "alpha",
                        edge ? 0.9 : 0.45, "grow", 1.0));
            }
        }
    }

    /** l 이 직사각형 R (a → b, 반폭 half) 안인지: 앞뒤 · 옆으로 pad 칸 여유 */
    public static boolean inRect(Vec3 l, Rect R, double half, double pad) {
        double ax = l.x - R.a().x;
        double az = l.z - R.a().z;
        double along = ax * R.dir().x + az * R.dir().z;
        double side = Math.abs(ax * R.dir().z - az * R.dir().x);
        return along >= -pad && along <= hdist(R.a(), R.b()) + pad && side <= half + pad;
    }

    public static boolean inRect(Vec3 l, Rect R, double half) {
        return inRect(l, R, half, 0.3);
    }

    /** 부채꼴 띠 예고 (apex 에서 ang 방향 가운데, 벌린 각 angle°, inner ~ outer 칸): 바큇살 (판) + 바깥 · 안쪽 테두리 */
    static void bandMarks(Bosses.Unit u, Vec3 apex, double ang, double inner, double outer, double angle, int ticks, double[] color, double alpha) {
        double half = (angle * DEG) / 2;
        double life = Math.max(0.1, ticks / 20.0);
        int spokes = Math.max(3, (int) Math.ceil(angle / 12));
        for (int i = 0; i <= spokes; i++) {
            double a = ang - half + (2 * half * i) / spokes;
            for (double r = inner + 1.5; r <= outer - 1; r += 3.2) {
                SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(apex.x + Math.cos(a) * r, u.center.y + 0.07, apex.z + Math.sin(a) * r), Opt.of("dir", Vec3.ZERO, "size", 2.4,
                        "life", life, "color", color, "alpha", 0.4 * alpha, "grow", 1.0));
            }
        }
        for (double r : new double[] {outer, inner}) {
            double step = 2.2;
            int n = Math.max(2, (int) Math.ceil((r * 2 * half) / step));
            for (int i = 0; i <= n; i++) {
                double a = ang - half + (2 * half * i) / n;
                SkillFx.fx(u.dim, "rpg:tele_disc", new Vec3(apex.x + Math.cos(a) * r, u.center.y + 0.08, apex.z + Math.sin(a) * r), Opt.of("dir", Vec3.ZERO, "size", 0.8,
                        "life", life, "color", color, "alpha", 0.9 * alpha));
            }
        }
    }

    /** 점 l 이 부채꼴 띠 (apex, 가운데 각 ang, angle°, inner ~ outer) 안인지 (pad 칸 여유) */
    public static boolean inBand(Vec3 l, Vec3 apex, double ang, double inner, double outer, double angle, double pad) {
        double d = hdist(l, apex);
        if (d < inner - pad || d > outer + pad) return false;
        double off = Math.abs(wrapPi(Math.atan2(l.z - apex.z, l.x - apex.x) - ang));
        return off <= (angle * DEG) / 2 + pad / Math.max(1, d);
    }

    public static boolean inBand(Vec3 l, Vec3 apex, double ang, double inner, double outer, double angle) {
        return inBand(l, apex, ang, inner, outer, angle, 0.3);
    }

    // ------------------------------------------------------------ 부위의 핵 (맞는 소품)

    /** 핵 소품의 이름표: 부위 이름 + 남은 체력 막대 (두 줄 — 핵 위의 글자 표시 엔티티) */
    static void coreTag(Bosses.Unit u) {
        M m = mem(u);
        Core c = m == null ? null : m.core;
        if (c == null || c.e == null || c.e.isRemoved()) return;
        Limb P = part(m, c.kind);
        double max = cfg(u, "parts").num(c.kind);
        int n = (int) Math.max(0, Math.min(10, Math.ceil((P.hp / max) * 10)));
        String text = "§6" + PART_NAMES.get(c.kind) + "의 핵\n§c" + "|".repeat(n) + "§8" + "|".repeat(10 - n) + " §f" + Js.str(Js.round((P.hp / max) * 100)) + "%";
        if (text.equals(c.text) && c.label != null && !c.label.isRemoved()) return;
        c.text = text;
        Component comp = Component.literal(BedrockText.toJava(text));
        if (c.label != null && !c.label.isRemoved()) {
            c.label.setText(comp);
            return;
        }
        Vec3 at = new Vec3(c.e.getX(), c.e.getY() + LABEL_Y, c.e.getZ());
        Display.TextDisplay td = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, u.dim) {
            @Override
            public boolean shouldBeSaved() {
                return false;
            }
        };
        td.setText(comp);
        td.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        td.setFlags(LABEL_FLAGS);
        td.setBrightnessOverride(Brightness.FULL_BRIGHT);
        td.addTag(LABEL_TAG);
        td.snapTo(at.x, at.y, at.z, 0F, 0F);
        c.label = u.dim.addFreshEntity(td) ? td : null;
    }

    /**
     * 핵이 놓일 자리: at 이 서 있는 구조물 (사슬 기둥 · 얼음 결정) 이나 담장에 걸리면 toward (몸 · 어깨) 쪽으로 0.5칸씩 당김 — 핵의 맞는 상자가 블록 안에 묻혀 때릴 수 없게 되지 않게.
     * 무너진 기둥의 밑동 (한 칸 높이) · 녹은 결정 (서리 바닥) 은 막지 않음
     */
    public static Vec3 coreSpot(Bosses.Unit u, Vec3 at, Vec3 toward) {
        M m = mem(u);
        Vec3 c = u.center;
        double wall = arenaRadius(u) - CLEAR_WALL;
        java.util.function.BiPredicate<Double, Double> blocked = (x, z) -> {
            if (Math.hypot(x - c.x, z - c.z) > wall) return true;
            for (Struct q : m.chains) if (!q.broken && Math.max(Math.abs(x - q.at.x), Math.abs(z - q.at.z)) < CLEAR_CHAIN) return true;
            for (Struct q : m.crystals) if (!q.melted && Math.max(Math.abs(x - q.at.x), Math.abs(z - q.at.z)) < CLEAR_CRYSTAL) return true;
            return false;
        };
        Vec3 to = toward != null ? toward : bodyOf(u);
        double L = hdist(at, to);
        Vec3 v = dirTo(at, to);
        for (double s = 0; s <= L; s += 0.5) {
            double x = at.x + v.x * s;
            double z = at.z + v.z * s;
            if (!blocked.test(x, z)) return new Vec3(x, at.y, z);
        }
        // 줄 위가 모두 막힘 (거의 없음): at 둘레에서 가장 가까운 빈 자리
        for (int r = 1; r <= 10; r++) {
            for (int k = 0; k < 16; k++) {
                double x = at.x + Math.cos((k / 16.0) * TAU) * r;
                double z = at.z + Math.sin((k / 16.0) * TAU) * r;
                if (!blocked.test(x, z)) return new Vec3(x, at.y, z);
            }
        }
        return at;
    }

    /** 부위의 핵을 드러냄 (at = 바닥 자리 → coreSpot 으로 구조물에서 비킴, ticks 틱 뒤 cover). 이미 부서진 부위는 아님. 다른 핵이 드러나 있으면 먼저 거둠 */
    static BedrockMob expose(Bosses.Unit u, String kind, Vec3 at0, int ticks, Vec3 toward) {
        M m = mem(u);
        Limb P = part(m, kind);
        if (P.broken) return null;
        cover(u);
        Vec3 at = coreSpot(u, at0, toward);
        BedrockMob e = BossKit.spawn(u.dim, CORE_ID, new Vec3(at.x, u.center.y, at.z), 0F);
        if (e == null) return null;
        u.api.addProp(u.enc, e, coreMax(u), 2.4);
        e.setHealth((float) Math.max(1, P.hp));
        e.setProp("rpg:part", PART_INDEX.get(kind).doubleValue());
        m.core = new Core(kind, e, Tasks.now() + ticks);
        coreTag(u);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, u.center.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", 2.6, "life", 0.6, "color", GOLD, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, u.center.y + 1.6, at.z), Opt.of("dir", Z_AXIS, "size", 2.0, "life", 0.4, "color", GOLD, "alpha", 0.95));
        play(u.dim, "block.bell.hit", at, 1.4, 0.7);
        explain(u, "core_" + kind, "§6[보스] " + PART_NAMES.get(kind) + "의 핵: §f" + (kind.equals("sword") ? "칼이 박힌 동안" : kind.equals("fist") ? "주먹이 땅에 있는 동안" : "쓰러진 동안")
                + " 금빛 핵이 드러납니다 — 때려서 부수면 부위 파괴 §7(" + PART_EFFECT.get(kind) + ")");
        return e;
    }

    /** 핵 소품의 최대 체력 (가장 단단한 부위, build.py 의 소품 체력과 같음) */
    public static double coreMax(Bosses.Unit u) {
        Obj P = cfg(u, "parts");
        return Math.max(P.num("sword"), Math.max(P.num("fist"), P.num("crown")));
    }

    /** 드러난 핵을 거둠 (부위 체력은 그대로) */
    static void cover(Bosses.Unit u) {
        M m = mem(u);
        Core c = m == null ? null : m.core;
        if (c == null) return;
        m.core = null;
        if (c.label != null && !c.label.isRemoved()) c.label.discard();
        c.label = null;
        u.api.dropProp(u.enc, c.e);
    }

    /** 부위 파괴: 핵이 부서짐 → 속성 (텍스처 · 불꽃), 패턴이 약해짐, 보상에 재료 더 */
    static void partBreak(Bosses.Unit u, String kind, ServerPlayer p) {
        M m = mem(u);
        Limb P = part(m, kind);
        if (P.broken) return;
        P.broken = true;
        P.hp = 0;
        P.by = p != null ? p.getName().getString() : "";
        m.brokenOrder.add(kind);
        Vec3 at = m.core != null && m.core.e != null && !m.core.e.isRemoved() ? m.core.e.position() : bodyOf(u);
        cover(u);
        for (int i = 0; i < 3; i++) {
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1 + i, at.z), Opt.of("dir", Z_AXIS, "size", 2.5 + i, "life", 0.6, "color", i == 1 ? GOLD : FIRE, "alpha",
                    0.95));
        }
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", 6.0, "life", 0.8, "color", GOLD, "alpha", 0.95));
        for (int i = 0; i < 8; i++) {
            double a = (i / 8.0) * TAU;
            SkillFx.fx(u.dim, "rpg:boulder", new Vec3(at.x, at.y + 1.5, at.z), Opt.of("dir", new Vec3(Math.cos(a) * 0.6, 0.8, Math.sin(a) * 0.6), "speed", 9.0, "size", 0.7,
                    "life", 0.9, "spin", random() * 360, "color", new double[] {0.55, 0.3, 0.18}, "alpha", 1.0));
        }
        play(u.dim, snd(u, "shatter"), at, 0.7, 1);
        play(u.dim, snd(u, "erupt"), at, 0.9, 1);
        shakeNear(u, at, 30, 0.35, 0.6);
        // 레바테인이 부서지면 불길이 꺼짐
        if (kind.equals("sword")) m.trails = new ArrayList<>();
        title(u, "§6§l부위 파괴!", "§f" + PART_NAMES.get(kind) + " §7— " + PART_EFFECT.get(kind), fighters(u));
        say(u, "§6[보스] §e" + (p != null ? p.getName().getString() : "누군가") + "§f님이 " + PART_NAMES.get(kind) + "을(를) 부쉈습니다! §7(" + PART_EFFECT.get(kind) + " · 보상 재료 더)");
    }

    // ------------------------------------------------------------ 받는 피해가 늘어난 때 (빈틈 · 속박 · 반격)

    /** 지금 받는 피해 보너스 {bonus, mark} (겹치면 가장 큰 것 하나 — 더하지 않음), 없으면 null */
    public static Bonus openBonus(Bosses.Unit u, long now) {
        M m = mem(u);
        if (m == null) return null;
        Bonus best = null;
        Object[][] all = {
            {m.groggyUntil, cfg(u, "inferno").num("bonus"), MARK_GROGGY},
            {m.bindUntil, cfg(u, "bind").num("bonus"), MARK_BIND},
            {m.counterUntil, cfg(u, "counter").num("bonus"), MARK_COUNTER},
        };
        for (Object[] x : all) {
            long until = (Long) x[0];
            double bonus = (Double) x[1];
            if (now < until && (best == null || bonus > best.bonus())) best = new Bonus(bonus, (String) x[2]);
        }
        return best;
    }

    // ------------------------------------------------------------ 반격 (횡베기 · 찌르기)

    /** 반격 창 열기: 칼이 금빛으로 번쩍임, 예고 자리가 금빛, 눈앞 "반격!" */
    static void openCounter(Bosses.Unit u, String kind, Runnable show) {
        M m = mem(u);
        long now = Tasks.now();
        Obj d = cfg(u, kind);
        int window = (int) d.num("window");
        m.counter = new Counter(kind, now, now + window);
        Vec3 s = shoulderAt(u, null);
        Vec3 at = new Vec3(s.x, u.center.y + 12, s.z);
        SkillFx.fx(u.dim, "rpg:bash_burst", at, Opt.of("dir", Z_AXIS, "size", 5.0, "life", 0.5, "color", GOLD, "alpha", 1.0));
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(s.x, u.center.y + 0.15, s.z), Opt.of("dir", Vec3.ZERO, "size", 6.0, "life", window / 20.0, "color", GOLD, "alpha", 0.95));
        if (show != null) show.run();
        play(u.dim, snd(u, "parry"), at, 0.8, 1);
        play(u.dim, "random.anvil_use", at, 1.6, 0.6);
        faceAll(u, "§e§l반격! §f스킬·룬으로 수르트를", Opt.of("dist", 2.4, "up", 0.55), fighters(u));
    }

    /** 반격 성공: 하던 패턴을 끊고 비틀거림, time 틱 동안 받는 피해 +bonus */
    static void counterHit(Bosses.Unit u, ServerPlayer p, long now) {
        M m = mem(u);
        Obj C = cfg(u, "counter");
        String kind = m.counter != null ? m.counter.kind() : "sweep";
        m.counter = null;
        u.cast = null;
        u.staggerUntil = now + (long) C.num("stagger");
        m.counterUntil = now + (long) C.num("time");
        m.counters++;
        m.body.want = null;
        u.api.anim(u, "recoil");
        Vec3 s = shoulderAt(u, null);
        Vec3 at = new Vec3(s.x, u.center.y + 10, s.z);
        for (int i = 0; i < 3; i++) {
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + i * 3, at.z), Opt.of("dir", Z_AXIS, "size", 4.0 + i, "life", 0.5, "color", GOLD, "alpha", 1.0));
        }
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(s.x, u.center.y + 0.1, s.z), Opt.of("dir", Vec3.ZERO, "size", 9.0, "life", 0.7, "color", GOLD, "alpha", 0.95));
        play(u.dim, snd(u, "counter"), at, 0.6, 1);
        play(u.dim, snd(u, "parry"), at, 1.2, 1);
        shakeNear(u, at, 30, 0.3, 0.5);
        showFaceText(p, "§6§l반격 성공!", Opt.of("dist", 2.4, "up", 0.6));
        title(u, "§6§l반격!", "§e" + p.getName().getString() + "§f님이 " + (kind.equals("thrust") ? "찌르기" : "횡베기") + "를 받아쳤습니다 §7— " + Js.str(Js.round(C.num("time") / 20))
                + "초 동안 받는 피해 +" + Js.str(Js.round(C.num("bonus") * 100)) + "%", fighters(u));
    }

    // ------------------------------------------------------------ 레바테인 내려찍기 (평타)

    /**
     * 대상이 칼 줄 (오른 어깨에서 앞) 위에 오게 하는 몸 방향. 몸에 바짝 붙은 사람은 줄 가운데에 올 수 없으므로 직사각형 시작에서 0.5칸 안쪽에 오게
     * @param from 줄 시작 (어깨에서 칸)
     */
    public static double slamYaw(Bosses.Unit u, Vec3 l, double from) {
        Body b = mem(u).body;
        double D = Math.hypot(l.x - b.x, l.z - b.z);
        double th = Math.atan2(l.z - b.z, l.x - b.x);
        double arm = cfg(u, "drive").num("arm");
        double along = Math.max(Math.sqrt(Math.max(0, D * D - arm * arm)), Math.min(D, from + 0.5));
        double side = Math.sqrt(Math.max(0, D * D - along * along));
        return th - Math.atan2(side, along);
    }

    public static double slamYaw(Bosses.Unit u, Vec3 l) {
        return slamYaw(u, l, cfg(u, "melee").num("from"));
    }

    /** 어깨 밑에서 앞으로 from ~ reach 칸 직사각형 (시작 · 끝 · 방향) */
    public static Rect lineRect(Bosses.Unit u, double yaw, double from, double reach) {
        Vec3 s = shoulderAt(u, yaw);
        Vec3 f = dirOf(yaw);
        return new Rect(new Vec3(s.x + f.x * from, s.y, s.z + f.z * from), new Vec3(s.x + f.x * reach, s.y, s.z + f.z * reach), f);
    }

    public static Rect slamRect(Bosses.Unit u, double yaw) {
        Obj m = cfg(u, "melee");
        return lineRect(u, yaw, m.num("from"), m.num("reach"));
    }

    /**
     * 칼 줄 위의 온전한 사슬 기둥 (가장 가까운 것): 몸에서 bind.min 칸 넘게, 줄 시작 ~ 끝 사이, 옆으로 반폭 + 기둥 반폭 안 → {c, along} (줄 시작에서 기둥 가운데까지)
     */
    public static Pin bindPillar(Bosses.Unit u, Rect R, double width) {
        Obj B = cfg(u, "bind");
        Vec3 b = bodyOf(u);
        Struct best = null;
        double bestAlong = Double.POSITIVE_INFINITY;
        for (Struct c : mem(u).chains) {
            if (!c.intact) continue;
            double ax = c.at.x - R.a().x;
            double az = c.at.z - R.a().z;
            double along = ax * R.dir().x + az * R.dir().z;
            double side = Math.abs(ax * R.dir().z - az * R.dir().x);
            if (along < -c.half || along > hdist(R.a(), R.b()) + c.half || side > width / 2 + c.half || hdist(c.at, b) < B.num("min")) continue;
            if (along < bestAlong) {
                best = c;
                bestAlong = along;
            }
        }
        return best != null ? new Pin(best, bestAlong) : null;
    }

    public static Pin bindPillar(Bosses.Unit u, Rect R) {
        return bindPillar(u, R, cfg(u, "melee").num("width"));
    }

    /** 직사각형을 기둥 앞에서 자름 (기둥 뒤는 맞지 않음) */
    static Rect cutAt(Rect R, double along, double half) {
        double L = Math.max(0, along - half);
        return new Rect(R.a(), new Vec3(R.a().x + R.dir().x * L, R.a().y, R.a().z + R.dir().z * L), R.dir());
    }

    /** 내려찍기 대상: 몸 앞 ±aim° · 칼이 닿는 사람 중 band 칸 사이의 가장 가까운 사람 (없으면 가장 가까운 사람) */
    static ServerPlayer slamTarget(Bosses.Unit u, List<ServerPlayer> list) {
        Obj m = cfg(u, "melee");
        Vec3 b = bfloor(u);
        double reachC = Math.hypot(m.num("reach"), cfg(u, "drive").num("arm"));
        double[] band = BossKit.pair(m, "band");
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (onFloor(u, p) && hdist(p.position(), b) <= reachC && offAngle(u, p.position()) <= m.num("aim") * DEG) cand.add(p);
        List<ServerPlayer> inBand = new ArrayList<>();
        for (ServerPlayer p : cand) {
            double d = hdist(p.position(), b);
            if (d >= band[0] && d <= band[1]) inBand.add(p);
        }
        // v2.20.0 도발 (아이기스의 방패): 칼이 닿는 사람 가운데 도발한 사람이 있으면 그 사람 (띠 밖이어도)
        ServerPlayer taunted = meleeTarget(u, cand, b);
        if (taunted != null && u.taunt != null && u.taunt.id().equals(taunted.getUUID()) && Tasks.now() < u.taunt.until()) return taunted;
        return meleeTarget(u, inBand.isEmpty() ? cand : inBand, b);
    }

    /** 레바테인 내려찍기 (평타): 시작했으면 true */
    public static boolean slam(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M mm = mem(u);
        Obj m = cfg(u, "melee");
        Body b = mm.body;
        ServerPlayer t = slamTarget(u, list);
        if (t == null) return false;
        double yaw = slamYaw(u, t.position());
        Rect R = slamRect(u, yaw);
        Pin pin = bindPillar(u, R);
        Rect hitR = pin != null ? cutAt(R, pin.along(), pin.c().half) : R;
        b.want = yaw;
        int windup = (int) m.num("windup");
        int embed = (int) m.num("embed");
        if (hdist(hitR.a(), hitR.b()) > 0.2) rectMarks(u, hitR, m.num("width"), windup, RED);
        // 칼을 붙잡을 기둥: 둘레에 사슬빛 고리 (예고 동안)
        if (pin != null) {
            Vec3 pc = pin.c().at;
            disc(u.dim, new Vec3(pc.x, u.center.y, pc.z), pin.c().half + 1.6, windup, CHAIN, Opt.of("fill", false, "alpha", 0.95));
            for (int h : new int[] {4, 9, 14}) {
                SkillFx.fx(u.dim, "rpg:bash_ring", new Vec3(pc.x, u.center.y + h, pc.z), Opt.of("dir", UP, "size", 2.6, "life", windup / 20.0, "color", CHAIN, "alpha", 0.9));
            }
        }
        u.api.anim(u, "slam");
        sound(u, snd(u, "swing"), shoulderAt(u, yaw), 0.5, 1);
        explain(u, "slam", "§6[보스] 레바테인 내려찍기: §f수르트가 칼을 들면 오른 어깨에서 긴 붉은 줄이 뜹니다 — 줄 옆으로 비키세요. §e칼이 박힌 동안 칼끝 쪽 레바테인의 핵을 때리면 칼이 부서집니다");
        Obj B = cfg(u, "bind");
        if (pin != null) {
            explain(u, "bind", "§b[보스] 봉인의 사슬 기둥: §f수르트와 나 사이에 사슬 기둥이 오게 서면 내려찍은 칼을 기둥의 사슬이 붙잡습니다 §7— " + Js.str(Js.round(B.num("time") / 20))
                    + "초 속박, 받는 피해 +" + Js.str(Js.round(B.num("bonus") * 100)) + "%, 레바테인의 핵 (기둥은 한 번 쓰면 부서짐)");
        }
        u.api.cast(u, "slam", windup + embed + 12 + GAP, step(windup, (x, n) -> slamHit(x, R, hitR, pin, yaw)), step(windup + embed, (x, n) -> cover(x)));
        return true;
    }

    static void slamHit(Bosses.Unit u, Rect R, Rect hitR, Pin pin, double yaw) {
        Obj m = cfg(u, "melee");
        M mm = mem(u);
        long now = Tasks.now();
        double L = hdist(hitR.a(), hitR.b());
        if (L > 0.2) {
            for (ServerPlayer p : fighters(u)) {
                if (!onFloor(u, p) || !inRect(p.position(), hitR, m.num("width") / 2)) continue;
                double[] kk = keepIn(u, p, R.dir().x * m.num("knock"), R.dir().z * m.num("knock"));
                bossHit(u, p, m.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.35), "directional", true));
            }
        }
        // 칼이 떨어진 줄: 불 터짐 · 금 · 먼지 (4칸마다), 끝에 큰 불
        boolean swordBroken = broken(mm, "sword");
        for (double s = 0; s <= L; s += 4) {
            Vec3 at = new Vec3(hitR.a().x + R.dir().x * s, u.center.y, hitR.a().z + R.dir().z * s);
            scorch(u.dim, at, 4);
            if (!swordBroken) flame(u.dim, at, 3.2, 0.7, FIRE);
        }
        Vec3 end = new Vec3(hitR.b().x, u.center.y, hitR.b().z);
        blast(u.dim, end, 4.5, swordBroken ? ASH : FIRE);
        sound(u, snd(u, "hit"), end, 0.6, 1);
        shakeNear(u, end, 26, 0.35, 0.6);
        if (pin != null) {
            bind(u, pin, R, now);
            return;
        }
        // 레바테인이 온전하면 지나간 자리가 불탐
        if (!swordBroken) {
            Obj T = cfg(u, "trail");
            mm.trails.add(new Trail(hitR, now + (long) T.num("time"), now + (long) T.num("tick")));
            explain(u, "trail", "§6[보스] §f레바테인이 지나간 자리는 한동안 불탑니다 §7(불길 안에서는 계속 피해 · 칼을 부수면 사라짐)");
        }
        // 레바테인의 핵: 어깨 앞 core 칸 (칼끝 쪽, 바닥에 박힌 칼날 — 구조물에 걸리면 칼날을 따라 어깨 쪽으로)
        Vec3 s = shoulderAt(u, yaw);
        Vec3 f = dirOf(yaw);
        expose(u, "sword", new Vec3(s.x + f.x * m.num("core"), u.center.y, s.z + f.z * m.num("core")), (int) m.num("embed"), s);
    }

    /** 사슬 기둥이 칼을 붙잡음: 속박 (time 틱, 받는 피해 +bonus), 칼의 핵은 기둥 앞, 기둥은 쓰임 (속박이 풀릴 때 무너짐) */
    static void bind(Bosses.Unit u, Pin pin, Rect R, long now) {
        Obj B = cfg(u, "bind");
        M m = mem(u);
        Struct c = pin.c();
        int time = (int) B.num("time");
        c.intact = false;
        m.bindUntil = now + time;
        m.binds++;
        u.cast = null;
        m.body.want = null;
        u.api.anim(u, "rest");
        hold(u, "bound");
        for (int h : new int[] {3, 7, 11, 15}) {
            SkillFx.fx(u.dim, "rpg:bash_ring", new Vec3(c.at.x, u.center.y + h, c.at.z), Opt.of("dir", UP, "size", 3.0, "life", 0.6, "color", CHAIN, "alpha", 0.95));
        }
        dust(u.dim, floorAt(u, c.at.x, c.at.z), 5, new double[] {0.6, 0.6, 0.65}, 1.0);
        play(u.dim, snd(u, "chain"), c.at, 0.6, 1);
        play(u.dim, "random.anvil_land", c.at, 0.5, 0.8);
        title(u, "§b§l사슬이 레바테인을 붙잡았습니다!", "§f" + Js.str(Js.round(time / 20.0)) + "초 동안 받는 피해 §e+" + Js.str(Js.round(B.num("bonus") * 100)) + "% §7· 레바테인의 핵",
                fighters(u));
        // 칼의 핵: 기둥 바로 앞 (수르트 쪽, 기둥에 묻히지 않게 칼날을 따라 어깨 쪽으로 당김)
        double from = cfg(u, "melee").num("from");
        expose(u, "sword", new Vec3(c.at.x - R.dir().x * (c.half + 1.4), u.center.y, c.at.z - R.dir().z * (c.half + 1.4)), time,
                new Vec3(R.a().x - R.dir().x * from, u.center.y, R.a().z - R.dir().z * from));
        u.api.cast(u, "bound", time + GAP, step(time, (x, n) -> unbind(x, c)));
    }

    static void unbind(Bosses.Unit u, Struct c) {
        cover(u);
        crumble(u, c);
        release(u, "unbind");
        play(u.dim, snd(u, "shatter"), c.at, 0.8, 1);
        play(u.dim, snd(u, "roar"), headAt(u), 0.9, 0.8);
    }

    /** 불길 (내려찍은 자리): 8틱마다 불꽃, tick 틱마다 안의 사람 피해 + 불 */
    static void trailTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Obj T = cfg(u, "trail");
        if (m.trails.isEmpty()) return;
        List<Trail> keep = new ArrayList<>();
        for (Trail t : m.trails) if (now < t.until) keep.add(t);
        m.trails = keep;
        double half = cfg(u, "melee").num("width") / 2;
        for (Trail t : m.trails) {
            if (now % 8 == 0) {
                double L = hdist(t.r.a(), t.r.b());
                Vec3 r = rightOf(angOf(t.r.dir()));
                for (double s = 1; s <= L; s += 3.5) {
                    double side = ((Math.floor(s) % 3) - 1) * 1.4;
                    flame(u.dim, new Vec3(t.r.a().x + t.r.dir().x * s + r.x * side, u.center.y, t.r.a().z + t.r.dir().z * s + r.z * side), 2.2, 0.55, s % 2 < 1 ? FIRE : LAVA);
                }
            }
            if (now < t.next) continue;
            t.next = now + (long) T.num("tick");
            for (ServerPlayer p : fighters(u)) {
                if (!onFloor(u, p) || !inRect(p.position(), t.r, half, 0)) continue;
                if (bossHit(u, p, T.num("damage"), Opt.of("noSource", true))) burn(p, T.num("burn"));
            }
        }
    }

    // ------------------------------------------------------------ 횡베기 (반격)

    public static boolean sweep(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = cfg(u, "sweep");
        Body b = mem(u).body;
        Vec3 bp = bfloor(u);
        double inner = d.num("inner");
        double outer = d.num("outer");
        double angle = d.num("angle");
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (onFloor(u, p) && hdist(p.position(), bp) >= inner && hdist(p.position(), bp) <= outer) cand.add(p);
        if (cand.isEmpty()) return false;
        // 지금 보는 쪽에서 가장 덜 돌아도 되는 사람
        cand.sort(Comparator.comparingDouble(p -> offAngle(u, p.position())));
        double center = angOf(dirTo(bp, cand.get(0).position()));
        double yaw = center - d.num("offset") * DEG;
        Vec3 apex = bp;
        int windup = (int) d.num("windup");
        int window = (int) d.num("window");
        b.want = yaw;
        bandMarks(u, apex, center, inner, outer, angle, windup, RED, 1);
        u.api.anim(u, "sweep");
        sound(u, snd(u, "swing"), headAt(u), 0.4, 1);
        explain(u, "sweep", "§e[보스] 횡베기: §f수르트가 칼을 옆으로 당기면 앞쪽 넓은 부채꼴 (몸에서 " + Js.str(inner) + "~" + Js.str(outer)
                + "칸) 을 쓸어 버립니다 — 안쪽이나 바깥으로. §e칼이 금빛으로 번쩍이는 동안 스킬·룬으로 수르트를 때리면 반격!");
        u.api.cast(u, "sweep", windup + 24 + GAP,
                step(windup - window, (x, n) -> openCounter(x, "sweep", () -> bandMarks(x, apex, center, inner, outer, angle, window, GOLD, 0.9))),
                step(windup, (x, n) -> sweepHit(x, apex, center)));
        return true;
    }

    static void sweepHit(Bosses.Unit u, Vec3 apex, double center) {
        Obj d = cfg(u, "sweep");
        mem(u).counter = null;
        double inner = d.num("inner");
        double outer = d.num("outer");
        double angle = d.num("angle");
        for (ServerPlayer p : fighters(u)) {
            if (!onFloor(u, p) || !inBand(p.position(), apex, center, inner, outer, angle)) continue;
            Vec3 out = dirTo(apex, p.position());
            double[] kk = keepIn(u, p, out.x * d.num("knock"), out.z * d.num("knock"));
            if (bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.4), "directional", true))) burn(p, d.num("burn"));
        }
        // 칼이 지나간 띠: 불꽃 줄 (10°마다) + 큰 초승달 참격
        double half = (angle * DEG) / 2;
        double mid = (inner + outer) / 2;
        for (double a = center - half; a <= center + half + 1e-6; a += 10 * DEG) {
            Vec3 at = new Vec3(apex.x + Math.cos(a) * mid, u.center.y, apex.z + Math.sin(a) * mid);
            flame(u.dim, at, 4, 0.6, FIRE);
            dust(u.dim, at, 3, ASH, 0.6);
        }
        SkillFx.fx(u.dim, "rpg:slash_heavy_r_glow", new Vec3(apex.x + Math.cos(center) * mid * 0.5, u.center.y + 3, apex.z + Math.sin(center) * mid * 0.5),
                Opt.of("dir", dirOf(center), "speed", 0.0, "spin", 0.0, "size", outer * 0.7, "life", 0.3, "color", FIRE, "alpha", 0.95));
        sound(u, snd(u, "hit"), apex, 0.7, 1);
        play(u.dim, snd(u, "fire"), apex, 0.6, 1);
        shakeNear(u, apex, outer + 4, 0.3, 0.5);
    }

    // ------------------------------------------------------------ 찌르기 (반격)

    public static boolean thrust(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = cfg(u, "thrust");
        Body b = mem(u).body;
        Vec3 bp = bfloor(u);
        double reachC = Math.hypot(d.num("reach"), cfg(u, "drive").num("arm"));
        double near = BossKit.pair(cfg(u, "melee"), "band")[0];
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (onFloor(u, p) && hdist(p.position(), bp) >= near && hdist(p.position(), bp) <= reachC) cand.add(p);
        if (cand.isEmpty()) return false;
        cand.sort(Comparator.comparingDouble((ServerPlayer p) -> hdist(p.position(), bp)).reversed());
        double yaw = slamYaw(u, cand.get(0).position(), d.num("from"));
        Rect R = lineRect(u, yaw, d.num("from"), d.num("reach"));
        int windup = (int) d.num("windup");
        int window = (int) d.num("window");
        b.want = yaw;
        rectMarks(u, R, d.num("width"), windup, RED);
        u.api.anim(u, "thrust");
        sound(u, snd(u, "swing"), shoulderAt(u, yaw), 0.7, 1);
        explain(u, "thrust", "§e[보스] 찌르기: §f가장 먼 사람 쪽으로 긴 줄 — 옆으로 비키세요. §e칼이 금빛일 때 스킬·룬으로 수르트를 때리면 반격!");
        u.api.cast(u, "thrust", windup + 20 + GAP, step(windup - window, (x, n) -> openCounter(x, "thrust", () -> rectMarks(x, R, d.num("width"), window, GOLD))),
                step(windup, (x, n) -> thrustHit(x, R)));
        return true;
    }

    static void thrustHit(Bosses.Unit u, Rect R) {
        Obj d = cfg(u, "thrust");
        mem(u).counter = null;
        for (ServerPlayer p : fighters(u)) {
            if (!onFloor(u, p) || !inRect(p.position(), R, d.num("width") / 2)) continue;
            double[] kk = keepIn(u, p, R.dir().x * d.num("knock"), R.dir().z * d.num("knock"));
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], 0.3), "directional", true));
        }
        double L = hdist(R.a(), R.b());
        for (double s = 0; s <= L; s += 3) {
            Vec3 at = new Vec3(R.a().x + R.dir().x * s, u.center.y, R.a().z + R.dir().z * s);
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1, at.z), Opt.of("dir", R.dir(), "size", 1.6, "life", 0.3, "delay", s / 60, "color", s % 6 < 3 ? FIRE : GOLD,
                    "alpha", 0.9));
        }
        blast(u.dim, new Vec3(R.b().x, u.center.y, R.b().z), 3.5, FIRE);
        sound(u, snd(u, "hit"), R.b(), 0.9, 1);
    }

    // ------------------------------------------------------------ 불의 주먹 (건틀릿의 핵)

    /** 주먹이 떨어지는 자리 (몸 기준 앞 at[0] · 오른쪽 at[1]) */
    public static Vec3 fistAt(Bosses.Unit u, Double yaw) {
        Body b = mem(u).body;
        double[] at = BossKit.pair(cfg(u, "fist"), "at");
        double y = yaw != null ? yaw : b.yaw;
        Vec3 f = dirOf(y);
        Vec3 r = rightOf(y);
        return new Vec3(b.x + f.x * at[0] + r.x * at[1], u.center.y, b.z + f.z * at[0] + r.z * at[1]);
    }

    public static boolean fist(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = cfg(u, "fist");
        M m = mem(u);
        if (broken(m, "fist")) return false;
        Body b = m.body;
        Vec3 bp = bfloor(u);
        double[] fa = BossKit.pair(d, "at");
        double D0 = Math.hypot(fa[0], fa[1]);
        double off = Math.atan2(fa[1], fa[0]);
        double radius = d.num("radius");
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (onFloor(u, p) && Math.abs(hdist(p.position(), bp) - D0) <= radius - 1) cand.add(p);
        if (cand.isEmpty()) return false;
        cand.sort(Comparator.comparingDouble(p -> Math.abs(wrapPi(angOf(dirTo(bp, p.position())) - off - b.yaw))));
        double yaw = angOf(dirTo(bp, cand.get(0).position())) - off;
        Vec3 at = fistAt(u, yaw);
        int windup = (int) d.num("windup");
        int rest = (int) d.num("rest");
        b.want = yaw;
        disc(u.dim, at, radius, windup, RED);
        u.api.anim(u, "fist");
        sound(u, snd(u, "swing"), at, 0.6, 1);
        explain(u, "fist", "§6[보스] 불의 주먹: §f왼 주먹으로 땅을 내려칩니다 (붉은 원). §e주먹이 땅에 있는 동안 건틀릿의 핵을 때리면 부서집니다 §7(불의 주먹이 사라지고 불비가 약해짐)");
        u.api.cast(u, "fist", windup + rest + 20 + GAP, step(windup, (x, n) -> fistHit(x, at)), step(windup + rest, (x, n) -> cover(x)));
        return true;
    }

    static void fistHit(Bosses.Unit u, Vec3 at) {
        Obj d = cfg(u, "fist");
        double radius = d.num("radius");
        for (ServerPlayer p : fighters(u)) {
            if (!onFloor(u, p) || hdist(p.position(), at) > radius + 0.3) continue;
            Vec3 out = dirTo(at, p.position());
            double[] kk = keepIn(u, p, out.x * 0.8, out.z * 0.8);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], d.num("lift"))));
        }
        blast(u.dim, at, radius * 0.8, LAVA);
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(at.x, at.y + 0.2, at.z), Opt.of("dir", Vec3.ZERO, "speed", 24.0, "size", 1.0, "life", radius / 24, "color", FIRE, "alpha", 0.9));
        scorch(u.dim, at, radius * 1.6);
        sound(u, snd(u, "hit"), at, 0.5, 1);
        play(u.dim, snd(u, "lava"), at, 0.8, 1);
        shakeNear(u, at, 24, 0.35, 0.6);
        expose(u, "fist", at, (int) d.num("rest"), null);
    }

    // ------------------------------------------------------------ 무스펠의 불비 (메테오)

    /** 필드 안 무작위 자리 (가운데에서 R-4 칸 안) */
    static Vec3 randomSpot(Bosses.Unit u) {
        double R = arenaRadius(u) - 4;
        double a = random() * TAU;
        double r = Math.sqrt(random()) * R;
        return floorAt(u, u.center.x + Math.cos(a) * r, u.center.z + Math.sin(a) * r);
    }

    public static boolean meteors(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = cfg(u, "meteors");
        M m = mem(u);
        List<ServerPlayer> people = new ArrayList<>();
        for (ServerPlayer p : list) if (onFloor(u, p)) people.add(p);
        if (people.isEmpty()) return false;
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : people) spots.add(new Vec3(p.getX(), groundUnder(p, u.center.y), p.getZ()));
        int extra = (int) (broken(m, "fist") ? d.num("extraBroken") : d.num("extra"));
        for (int i = 0; i < extra; i++) spots.add(randomSpot(u));
        int windup = (int) d.num("windup");
        double radius = d.num("radius");
        for (Vec3 s : spots) {
            disc(u.dim, s, radius, windup, RED);
            SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(s.x, s.y + 18, s.z), Opt.of("dir", UP, "size", radius * 0.9, "life", windup / 20.0, "spin", 0.0, "speed", 40.0, "grow",
                    0.0, "progress", 1.0, "color", FIRE, "alpha", 0.75));
        }
        m.body.want = null;
        u.api.anim(u, "summon");
        sound(u, snd(u, "charge"), headAt(u), 0.7, 1);
        faceAll(u, "§6§l무스펠의 불비! §f발밑 원 밖으로", Opt.of("dist", 2.4, "up", 0.45), people);
        explain(u, "meteors", "§6[보스] 무스펠의 불비: §f수르트가 왼손을 들면 모두의 발밑과 곳곳에 붉은 원 — 메테오가 떨어지기 전에 원 밖으로 §7(왼 건틀릿을 부수면 곳곳의 원이 없어짐)");
        u.api.cast(u, "meteors", windup + 12 + GAP, step(windup - (long) d.num("fall"), (x, n) -> meteorFly(x, spots)), step(windup, (x, n) -> meteorHit(x, spots)));
        return true;
    }

    /** 메테오가 날아옴 (수르트 쪽 하늘에서 비스듬히, fall 틱 뒤 원에 닿음) */
    static void meteorFly(Bosses.Unit u, List<Vec3> spots) {
        Obj d = cfg(u, "meteors");
        Vec3 b = bodyOf(u);
        double t = d.num("fall") / 20;
        for (Vec3 s : spots) {
            Vec3 away = dirTo(b, s);
            Vec3 from = new Vec3(s.x - away.x * 12, s.y + 30, s.z - away.z * 12);
            Vec3 v = new Vec3(s.x - from.x, s.y + 0.4 - from.y, s.z - from.z);
            double L = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
            SkillFx.fx(u.dim, "rpg:meteor", from, Opt.of("dir", new Vec3(v.x / L, v.y / L, v.z / L), "speed", L / t, "size", 3.2, "life", t, "color", WHITE, "alpha", 1.0));
        }
        play(u.dim, snd(u, "meteor"), b, 0.6, 1);
    }

    static void meteorHit(Bosses.Unit u, List<Vec3> spots) {
        Obj d = cfg(u, "meteors");
        double radius = d.num("radius");
        Set<UUID> hit = new HashSet<>();
        for (Vec3 s : spots) {
            blast(u.dim, s, radius, FIRE);
            scorch(u.dim, s, radius * 2);
            play(u.dim, snd(u, "erupt"), s, 0.9, 0.8);
            for (ServerPlayer p : fighters(u)) {
                if (hit.contains(p.getUUID()) || hdist(p.position(), s) > radius + 0.3 || Math.abs(p.getY() - s.y) > 3) continue;
                hit.add(p.getUUID());
                if (bossHit(u, p, d.num("damage"), Opt.of("noSource", true, "cause", "projectile"))) burn(p, d.num("burn"));
            }
        }
    }

    // ------------------------------------------------------------ 불의 고리 (뛰어넘기)

    public static boolean rings(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = cfg(u, "rings");
        M m = mem(u);
        boolean any = false;
        for (ServerPlayer p : list) if (onFloor(u, p)) any = true;
        if (!any) return false;
        int waves = (int) (broken(m, "crown") ? d.num("wavesBroken") : d.num("waves"));
        m.body.want = null;
        u.api.anim(u, "plunge");
        sound(u, snd(u, "swing"), headAt(u), 0.6, 1);
        explain(u, "rings", "§6[보스] 불의 고리: §f칼을 땅에 꽂으면 수르트에게서 불의 고리가 퍼집니다 — 고리가 지나갈 때 뛰어서 넘으세요 §7(불꽃 왕관을 부수면 고리가 줄어듦)");
        int plunge = (int) d.num("plunge");
        int gap = (int) d.num("gap");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 0; k < waves; k++) steps.add(step(plunge + (long) k * gap, (x, n) -> ringStart(x)));
        u.api.cast(u, "rings", plunge + (waves - 1) * gap + 30 + GAP, steps);
        return true;
    }

    static void ringStart(Bosses.Unit u) {
        Obj d = cfg(u, "rings");
        Vec3 b = bodyOf(u);
        double speed = d.num("speed");
        double R = arenaRadius(u) + 6 + hdist(b, u.center);
        double life = R / speed;
        mem(u).rings.add(new FireRing(b.x, b.z, Tasks.now(), R));
        SkillFx.fx(u.dim, "rpg:fire_wave", new Vec3(b.x, u.center.y, b.z), Opt.of("speed", speed, "size", 1.8, "life", life, "color", FIRE, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(b.x, u.center.y + 0.2, b.z), Opt.of("dir", Vec3.ZERO, "speed", speed, "size", 1.0, "life", life, "color", LAVA, "alpha", 0.95));
        blast(u.dim, floorAt(u, b.x, b.z), 6, FIRE);
        play(u.dim, snd(u, "fire"), b, 0.5, 1);
        play(u.dim, snd(u, "lava"), b, 0.7, 1);
        shakeNear(u, b, 30, 0.2, 0.4);
    }

    /** 고리마다: 앞머리가 지나간 사람 (땅에 있으면) 피해 + 불, 뛰는 중이면 넘음 */
    static void ringTick(Bosses.Unit u, long now) {
        Obj d = cfg(u, "rings");
        M m = mem(u);
        if (m.rings.isEmpty()) return;
        List<ServerPlayer> list = fighters(u);
        for (FireRing g : m.rings) {
            double front = (d.num("speed") * (now - g.t0)) / 20;
            double prev = g.last;
            g.last = front;
            for (ServerPlayer p : list) {
                if (g.hit.contains(p.getUUID()) || !onFloor(u, p)) continue;
                double r = Math.hypot(p.getX() - g.x, p.getZ() - g.z);
                if (r < prev - 0.3 || r > front + 0.3) continue;
                g.hit.add(p.getUUID());
                if (!p.onGround()) {
                    showFaceText(p, "§a뛰어넘음!", Opt.of("dist", 2.2, "up", 0.2));
                    continue;
                }
                if (bossHit(u, p, d.num("damage"), Opt.of("noSource", true))) burn(p, d.num("burn"));
            }
        }
        List<FireRing> keep = new ArrayList<>();
        for (FireRing g : m.rings) if (g.last < g.max) keep.add(g);
        m.rings = keep;
    }

    // ------------------------------------------------------------ 발 구르기

    public static boolean stomp(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = cfg(u, "stomp");
        Body b = mem(u).body;
        Vec3 bp = bfloor(u);
        double radius = d.num("radius");
        boolean flank = false;
        for (ServerPlayer p : list) {
            if (onFloor(u, p) && hdist(p.position(), bp) <= radius && offAngle(u, p.position()) > (d.num("front") / 2) * DEG) flank = true;
        }
        if (!flank) return false;
        Vec3 at = floorAt(u, b.x, b.z);
        int windup = (int) d.num("windup");
        b.want = null;
        disc(u.dim, at, radius, windup, RED);
        u.api.anim(u, "stomp");
        sound(u, snd(u, "roar"), headAt(u), 1.3, 0.5);
        explain(u, "stomp", "§6[보스] 발 구르기: §f수르트의 옆이나 뒤에 붙어 있으면 발을 굴러 몸 둘레 (붉은 원) 를 칩니다 — 잠깐 물러나세요");
        u.api.cast(u, "stomp", windup + 12 + GAP, step(windup, (x, n) -> stompHit(x, at)));
        return true;
    }

    static void stompHit(Bosses.Unit u, Vec3 at) {
        Obj d = cfg(u, "stomp");
        double radius = d.num("radius");
        for (ServerPlayer p : fighters(u)) {
            if (!onFloor(u, p) || hdist(p.position(), at) > radius + 0.3) continue;
            Vec3 dir = dirTo(at, p.position());
            double[] kk = keepIn(u, p, dir.x * 1.0, dir.z * 1.0);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(kk[0], kk[1], d.num("lift"))));
        }
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(at.x, at.y + 0.2, at.z), Opt.of("dir", Vec3.ZERO, "speed", 30.0, "size", 1.0, "life", radius / 30, "color", FIRE, "alpha", 0.9));
        landFx(u, at, 6);
    }

    // ------------------------------------------------------------ 무스펠의 업화 (무력화)

    /** 업화 막대 소품 (보스 막대만 보임): 가운데 바닥 */
    static BedrockMob spawnGauge(Bosses.Unit u) {
        BedrockMob e = BossKit.spawn(u.dim, GAUGE_ID, new Vec3(u.center.x, u.center.y + 0.5, u.center.z), 0F);
        if (e == null) return null;
        u.api.addProp(u.enc, e);
        e.setNameTag("§6무스펠의 업화");
        return e;
    }

    /** 업화 막대 = 남은 업화 비율 */
    static void gaugeTick(Bosses.Unit u) {
        Inferno I = mem(u).inferno;
        if (I == null || I.gauge == null || I.gauge.isRemoved()) return;
        int v = (int) Math.max(1, Math.ceil((GAUGE_MAX * Math.max(0, I.left)) / I.need));
        if (I.shown == v) return;
        I.shown = v;
        I.gauge.setHealth(v);
    }

    /** 무스펠의 업화 (무력화): 업화 막대가 나오고 time 틱 동안 업화를 모음 */
    public static void inferno(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj I = cfg(u, "inferno");
        M m = mem(u);
        int n = (int) Math.max(I.num("min"), list.size());
        double need = Js.round(n * I.num("per") * (broken(m, "crown") ? I.num("crownCut") : 1));
        int time = (int) I.num("time");
        m.inferno = new Inferno(need, now, now + time, spawnGauge(u), now + 30);
        gaugeTick(u);
        m.body.want = null;
        u.api.anim(u, "rest");
        hold(u, "channel");
        sound(u, snd(u, "charge"), headAt(u), 0.5, 1);
        play(u.dim, snd(u, "roar"), headAt(u), 0.6, 1);
        title(u, "§6§l무스펠의 업화", "§f업화가 모이기 전에 §e피해로 업화 막대§f를 깎으세요! §7(" + Js.str(Js.round(time / 20.0)) + "초)", list);
        explain(u, "inferno", "§6[보스] 무스펠의 업화 (무력화): §f수르트가 칼을 하늘로 들고 업화를 모읍니다. §e업화 막대를 " + Js.str(Js.round(time / 20.0)) + "초 안에 피해로 다 깎으면 쓰러져 "
                + Js.str(Js.round(I.num("groggy") / 20)) + "초 동안 받는 피해 +" + Js.str(Js.round(I.num("bonus") * 100))
                + "% (빈틈), 불꽃 왕관의 핵이 드러납니다. §c못 깎으면 업화 폭발 §7(모두 큰 피해, 수르트 회복)");
        say(u, "§6[보스] §f이번 업화: §e" + Js.str(need) + "§f 피해 §7(싸우는 사람 " + n + "명" + (broken(m, "crown") ? " · 불꽃 왕관이 부서져 쉬움" : "") + ")");
        u.api.cast(u, "inferno", time + 10, step(time, (x, t) -> infernoFail(x)));
    }

    /** 업화 동안 매 틱: 용암 분출 (사람마다 곁에 원) · 업화 막대 · 알림 · 3 · 2 · 1 · 불꽃이 모임 */
    static void infernoTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Inferno G = m.inferno;
        if (G == null) return;
        Obj I = cfg(u, "inferno");
        int eruptWindup = (int) I.num("eruptWindup");
        double eruptRadius = I.num("eruptRadius");
        // 새 분출은 업화가 끝나기 전에 터질 수 있을 때만 (예고만 뜨고 터지지 않는 원이 없게)
        if (now >= G.nextErupt && G.until - now >= eruptWindup) {
            G.nextErupt = now + (long) I.num("erupt");
            for (ServerPlayer p : fighters(u)) {
                if (!onFloor(u, p)) continue;
                double a = random() * TAU;
                double r = random() * 2.5;
                Vec3 at = new Vec3(p.getX() + Math.cos(a) * r, groundUnder(p, u.center.y), p.getZ() + Math.sin(a) * r);
                disc(u.dim, at, eruptRadius, eruptWindup, RED);
                G.erupts.add(new Erupt(at, now + eruptWindup));
            }
        }
        List<Erupt> keep = new ArrayList<>();
        for (Erupt e : G.erupts) {
            if (now < e.hitAt()) {
                keep.add(e);
                continue;
            }
            blast(u.dim, e.at(), eruptRadius, LAVA);
            flame(u.dim, e.at(), eruptRadius * 1.6, 0.8, FIRE);
            play(u.dim, snd(u, "lava"), e.at(), 1.0, 0.8);
            for (ServerPlayer p : fighters(u)) {
                if (hdist(p.position(), e.at()) > eruptRadius + 0.3 || Math.abs(p.getY() - e.at().y) > 3) continue;
                bossHit(u, p, I.num("eruptDamage"), Opt.of("noSource", true, "knock", knock(0, 0, 0.6)));
            }
        }
        // 분출 판정 중에 업화가 끝났으면 (쓰러뜨림 · 폭발) 그대로
        if (m.inferno != G) return;
        G.erupts = keep;
        gaugeTick(u);
        long left = G.until - now;
        if (now % 10 == 0) {
            long pct = (long) Math.max(0, Math.ceil((G.left / G.need) * 100));
            for (ServerPlayer p : fighters(u)) {
                BossKit.notify(p, "§6업화 §f" + pct + "% 남음 §7— 수르트를 때려 깎으세요 §8(" + (long) Math.ceil(left / 20.0) + "초)", 15);
            }
        }
        if (left > 0 && left % 20 == 0 && left <= 60) faceAll(u, "§6§l업화 " + (left / 20), Opt.of("dist", 2.4, "up", 0.45), fighters(u));
        // 몸 둘레로 불꽃이 모임
        if (now % 4 == 0) {
            Vec3 b = bodyOf(u);
            double a = random() * TAU;
            double r = 6 + random() * 6;
            flame(u.dim, new Vec3(b.x + Math.cos(a) * r, u.center.y, b.z + Math.sin(a) * r), 2.5 + ((now - G.t0) / I.num("time")) * 3, 0.6, LAVA);
        }
        if (now % 20 == 0) {
            Vec3 b = bodyOf(u);
            SkillFx.fx(u.dim, "rpg:ember", new Vec3(b.x, u.center.y + 18, b.z), Opt.of("size", 8.0, "life", 1.0, "speed", 3.0, "color", EMBER, "alpha", 0.9));
        }
    }

    /** 맞은 피해만큼 업화를 깎음 → 다 깎으면 쓰러짐 */
    static void gaugeHit(Bosses.Unit u, double amount) {
        Inferno G = mem(u).inferno;
        if (G == null || !(amount > 0)) return;
        G.left -= amount;
        gaugeTick(u);
        if (G.left <= 0) infernoBreak(u);
    }

    static void dropGauge(Bosses.Unit u, Inferno G) {
        if (G != null && G.gauge != null) u.api.dropProp(u.enc, G.gauge);
    }

    /** 업화를 막음: 쓰러짐 (그로기) — groggy 틱 동안 받는 피해 +bonus, 불꽃 왕관의 핵 */
    static void infernoBreak(Bosses.Unit u) {
        Obj I = cfg(u, "inferno");
        M m = mem(u);
        long now = Tasks.now();
        Inferno G = m.inferno;
        int groggy = (int) I.num("groggy");
        m.inferno = null;
        dropGauge(u, G);
        // 아직 터지지 않은 용암 분출은 식어 꺼짐 (재 연기)
        if (G != null) {
            for (Erupt e : G.erupts) dust(u.dim, e.at(), I.num("eruptRadius") * 1.2, ASH, 0.7);
            if (!G.erupts.isEmpty()) play(u.dim, snd(u, "melt"), G.erupts.get(0).at(), 0.8, 1);
        }
        m.infernoWins++;
        u.cast = null;
        m.body.want = null;
        m.groggyUntil = now + groggy;
        u.api.anim(u, "rest");
        hold(u, "collapse");
        Vec3 b = bodyOf(u);
        Vec3 f = dirOf(m.body.yaw);
        landFx(u, floorAt(u, b.x + f.x * 8, b.z + f.z * 8), 12);
        play(u.dim, "random.anvil_land", b, 0.4, 1);
        play(u.dim, snd(u, "roar"), headAt(u), 0.5, 1);
        title(u, "§6§l수르트가 쓰러졌습니다!", "§f" + Js.str(Js.round(groggy / 20.0)) + "초 동안 받는 피해 §e+" + Js.str(Js.round(I.num("bonus") * 100)) + "% §7(빈틈)"
                + (broken(m, "crown") ? "" : " · 불꽃 왕관의 핵"), fighters(u));
        say(u, "§6[보스] §f업화를 막았습니다! 수르트가 쓰러졌습니다");
        expose(u, "crown", new Vec3(b.x + f.x * CROWN_AT, u.center.y, b.z + f.z * CROWN_AT), groggy, null);
        u.api.cast(u, "groggy", groggy + 24 + GAP, step(groggy, (x, n) -> rise(x)));
        m.bigUntil = Math.max(m.bigUntil, now + groggy + 100);
    }

    static void rise(Bosses.Unit u) {
        cover(u);
        release(u, "rise");
        play(u.dim, snd(u, "roar"), headAt(u), 0.8, 0.9);
        Body b = mem(u).body;
        dust(u.dim, floorAt(u, b.x, b.z), 8, ASH, 1.0);
    }

    /** 업화를 막지 못함: 업화 폭발 (모두 마법 피해 + 불), 수르트 회복 */
    static void infernoFail(Bosses.Unit u) {
        Obj I = cfg(u, "inferno");
        M m = mem(u);
        Inferno G = m.inferno;
        if (G == null) return;
        m.inferno = null;
        dropGauge(u, G);
        m.infernoFails++;
        release(u, "rest");
        Vec3 b = bodyOf(u);
        SkillFx.fx(u.dim, "rpg:fire_wave", new Vec3(b.x, u.center.y, b.z), Opt.of("speed", 40.0, "size", 2.4, "life", 1.4, "color", FIRE, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(b.x, u.center.y + 0.1, b.z), Opt.of("dir", Vec3.ZERO, "size", 20.0, "life", 1.0, "color", FIRE, "alpha", 0.95));
        for (ServerPlayer p : fighters(u)) {
            blast(u.dim, p.position(), 2.5, LAVA);
            if (bossHit(u, p, I.num("failDamage"), Opt.of("noSource", true, "cause", "magic"))) burn(p, I.num("failBurn"));
            SkillFx.shake(p, 0.5, 1.0);
        }
        if (u.boss != null && !u.boss.isRemoved()) {
            float max = u.boss.getMaxHealth();
            u.boss.setHealth((float) Math.min(max, u.boss.getHealth() + max * I.num("heal")));
        }
        play(u.dim, snd(u, "erupt"), b, 0.5, 1);
        play(u.dim, snd(u, "roar"), headAt(u), 0.7, 1);
        title(u, "§c§l무스펠의 업화 폭발!", "§f업화를 막지 못했습니다 §7— 수르트가 체력을 " + Js.str(Js.round(I.num("heal") * 100)) + "% 되찾았습니다", fighters(u));
        m.bigUntil = Math.max(m.bigUntil, Tasks.now() + 120);
    }

    // ------------------------------------------------------------ 라그나로크 (얼음 결정 뒤)

    /** 세계의 플레이어 모두 (서버 플레이어 목록) */
    static List<ServerPlayer> allPlayers(Bosses.Unit u) {
        return u.dim.getServer() == null ? List.of() : u.dim.getServer().getPlayerList().getPlayers();
    }

    /** 명단 맞추기: 지금 싸우는 사람은 넣고, 쓰러졌거나 싸울 수 없게 된 사람은 뺌 */
    static void ragRoll(Bosses.Unit u, Ragnarok G) {
        for (ServerPlayer p : fighters(u)) {
            G.gone.remove(p.getUUID());
            G.who.add(p.getUUID());
        }
        for (ServerPlayer p : allPlayers(u)) if (G.who.contains(p.getUUID()) && !G.gone.contains(p.getUUID()) && !okTarget(p)) G.gone.add(p.getUUID());
    }

    /** 라그나로크를 받는 사람: 지금 싸우는 사람 + 명단에 있는 사람 중 같은 차원 · 필드 가운데에서 reach 칸 안 (필드를 떠나도 피하지 못함) */
    static List<ServerPlayer> ragVictims(Bosses.Unit u, Ragnarok G, double reach) {
        Map<UUID, ServerPlayer> out = new LinkedHashMap<>();
        for (ServerPlayer p : fighters(u)) out.put(p.getUUID(), p);
        for (ServerPlayer p : allPlayers(u)) {
            if (out.containsKey(p.getUUID()) || !G.who.contains(p.getUUID()) || G.gone.contains(p.getUUID())) continue;
            if (okTarget(p) && p.level() == u.dim && hdist(p.position(), u.center) <= reach) out.put(p.getUUID(), p);
        }
        return new ArrayList<>(out.values());
    }

    /** 담장 밖 (성문 · 둑) 에서 싸우던 사람은 담장 안으로 끌려 들어옴 (제 방향 그대로, 가운데에서 R-5 칸) */
    static void pullIn(Bosses.Unit u, List<ServerPlayer> list) {
        double R = arenaRadius(u);
        Vec3 c = u.center;
        for (ServerPlayer p : list) {
            Vec3 l = p.position();
            double r = hdist(l, c);
            if (r <= R + 0.3) continue;
            Vec3 at = new Vec3(c.x + ((l.x - c.x) / r) * (R - 5), c.y, c.z + ((l.z - c.z) / r) * (R - 5));
            float[] rot = Kit.lookRotation(new Vec3(at.x, at.y + 1.62, at.z), new Vec3(c.x, c.y + 1.6, c.z));
            if (!p.teleportTo(u.dim, at.x, at.y, at.z, Set.of(), rot[0], rot[1], false)) continue;
            p.setYHeadRot(rot[0]);
            showFaceText(p, "§c§l불길에 끌려 들어왔습니다", Opt.of("dist", 2.4, "up", 0.45));
        }
    }

    /**
     * p 를 지켜 주는 온전한 얼음 결정 (불길이 오는 곳 from 에서 결정 너머 그림자: 결정 반폭 ~ 반폭 + depth 칸 뒤, 옆으로 반폭 + pad), 없으면 null
     */
    public static Struct shadowOf(Bosses.Unit u, Vec3 l, Vec3 from) {
        Obj G = cfg(u, "ragnarok");
        for (Struct c : mem(u).crystals) {
            if (!c.intact) continue;
            Vec3 v = dirTo(from, c.at);
            double px = l.x - c.at.x;
            double pz = l.z - c.at.z;
            double along = px * v.x + pz * v.z;
            double side = Math.abs(px * v.z - pz * v.x);
            if (along >= c.half - 0.3 && along <= c.half + G.num("depth") && side <= c.half + G.num("pad") && Math.abs(l.y - c.at.y) < 3) return c;
        }
        return null;
    }

    /** 라그나로크 (즉사): second = 두 번째 (사람들 곁의 결정을 먼저 녹임) */
    public static void ragnarok(Bosses.Unit u, long now, List<ServerPlayer> list, boolean second) {
        Obj G = cfg(u, "ragnarok");
        M m = mem(u);
        Vec3 c = u.center;
        double a = random() * TAU;
        Vec3 spot = new Vec3(c.x + Math.cos(a) * G.num("spot"), c.y, c.z + Math.sin(a) * G.num("spot"));
        Set<UUID> who = new HashSet<>();
        for (ServerPlayer p : list) who.add(p.getUUID());
        m.ragnarok = new Ragnarok(second, who);
        u.immune = true;
        u.api.anim(u, "rest");
        hold(u, "leap");
        int leap = (int) G.num("leap");
        int windup = (int) G.num("windup");
        jump(u, spot, leap, 10);
        sound(u, snd(u, "roar"), headAt(u), 0.6, 1);
        title(u, "§4§l라그나로크", "§f세계를 태우는 불길이 옵니다 §7— §b얼음 결정 뒤로!", list);
        int raise = leap + 6;
        u.api.cast(u, "ragnarok", raise + windup + 90 + GAP, step(leap, (x, n) -> ragLand(x)), step(raise, (x, n) -> ragRaise(x)), step(raise + windup, (x, n) -> ragRelease(x)));
    }

    static void ragLand(Bosses.Unit u) {
        M m = mem(u);
        if (m.ragnarok == null) return;
        u.immune = false;
        landFx(u, floorAt(u, m.body.x, m.body.z), 10);
    }

    static void ragRaise(Bosses.Unit u) {
        Obj G = cfg(u, "ragnarok");
        M m = mem(u);
        Ragnarok R = m.ragnarok;
        if (R == null) return;
        long now = Tasks.now();
        int windup = (int) G.num("windup");
        R.stage = "raise";
        R.t0 = now;
        R.until = now + windup;
        m.body.want = null;
        hold(u, "ragnarok");
        ragRoll(u, R);
        pullIn(u, ragVictims(u, R, RAG_PULL));
        play(u.dim, snd(u, "thunder"), u.center, 0.6, 1);
        sound(u, snd(u, "charge"), headAt(u), 0.4, 1);
        // 두 번째부터: 사람들 곁의 온전한 결정을 melt 개까지 먼저 녹임 (meltPick: 온전한 결정 keepIntact 개 이상 · 모두에게 safe 칸 안의 그림자를 남김)
        if (R.second) {
            List<ServerPlayer> people = ragVictims(u, R, RAG_REACH);
            List<Struct> pick = meltPick(u, people, bodyOf(u));
            Vec3 from = leftHandAt(u);
            for (Struct c : pick) {
                BossKit.Arc arc = lobArc(from, new Vec3(c.at.x, c.at.y + 4, c.at.z), 0.8);
                SkillFx.fx(u.dim, "rpg:glob", from, Opt.of("dir", arc.dir(), "speed", arc.speed(), "size", 2.2, "life", 0.8, "spin", random() * 360, "color", FIRE, "alpha", 1.0));
                R.melted.add(c);
                c.intact = false; // 날아가는 동안에도 이미 쓸 수 없음 (그림자 표시에서 빠짐)
            }
            if (!pick.isEmpty()) {
                play(u.dim, snd(u, "fire"), from, 0.6, 1);
                later(u, 16, (x, n) -> {
                    for (Struct c : pick) meltCrystal(x, c);
                });
            }
        }
        explain(u, "ragnarok", "§4[보스] 라그나로크: §f수르트가 칼을 하늘로 들면 " + Js.str(Js.round(windup / 20.0))
                + "초 뒤 세계를 태우는 불길이 퍼집니다 — §b수르트 반대쪽 얼음 결정 뒤 (파란 그림자)§f에 숨으세요 §c(밖은 즉사)§7. 숨겨 준 결정은 녹아 없어지고, 레바테인을 부수면 즉사 대신 큰 피해");
        if (R.second && !R.melted.isEmpty()) {
            int left = 0;
            for (Struct c : m.crystals) if (c.intact) left++;
            say(u, "§4[보스] §f수르트가 얼음 결정 " + R.melted.size() + "개를 녹여 버립니다! §7(남은 결정 " + left + "개)");
        }
    }

    /** 결정 c 의 그림자 한가운데 (불길이 오는 곳 from 에서 결정 너머 반폭 + depth / 2) */
    public static Vec3 shadowMid(Bosses.Unit u, Struct c, Vec3 from) {
        Vec3 v = dirTo(from, c.at);
        double s = c.half + cfg(u, "ragnarok").num("depth") / 2;
        return new Vec3(c.at.x + v.x * s, c.at.y, c.at.z + v.z * s);
    }

    /**
     * 두 번째 라그나로크에 먼저 녹일 결정: 사람들 (가운데) 에 가까운 온전한 결정부터 melt 개까지.
     * 온전한 결정을 keepIntact 개 이상 남기고, 녹인 뒤에도 모두에게 safe 칸 안에 온전한 결정의 그림자가 있을 때만 녹임 (닿을 수 없는 즉사는 없음)
     */
    public static List<Struct> meltPick(Bosses.Unit u, List<ServerPlayer> people, Vec3 from) {
        Obj G = cfg(u, "ragnarok");
        if (people.isEmpty()) return List.of();
        double cx = 0;
        double cz = 0;
        for (ServerPlayer p : people) {
            cx += p.getX();
            cz += p.getZ();
        }
        cx /= people.size();
        cz /= people.size();
        Set<Struct> left = new LinkedHashSet<>();
        for (Struct c : mem(u).crystals) if (c.intact) left.add(c);
        List<Struct> order = new ArrayList<>(left);
        double fx = cx;
        double fz = cz;
        order.sort(Comparator.comparingDouble(c -> Math.hypot(c.at.x - fx, c.at.z - fz)));
        List<Struct> out = new ArrayList<>();
        for (Struct c : order) {
            if (out.size() >= G.num("melt") || left.size() <= G.num("keepIntact")) break;
            left.remove(c);
            boolean reachable = true;
            for (ServerPlayer p : people) {
                boolean any = false;
                for (Struct q : left) if (hdist(p.position(), shadowMid(u, q, from)) <= G.num("safe")) any = true;
                if (!any) reachable = false;
            }
            if (reachable) out.add(c);
            else left.add(c);
        }
        return out;
    }

    /** 들고 있는 동안: 명단 · 그림자 표시 (온전한 결정마다) · 알림 · 3 · 2 · 1 · 붉은 하늘 */
    static void ragTick(Bosses.Unit u, long now) {
        M m = mem(u);
        Ragnarok R = m.ragnarok;
        if (R == null) return;
        ragRoll(u, R);
        if ("raise".equals(R.stage)) {
            Vec3 from = bodyOf(u);
            if (now % 6 == 0) for (Struct c : m.crystals) if (c.intact) shadowMarks(u, c, from, 8);
            // 들고 있는 동안 담장 밖으로 나간 사람은 다시 끌려 들어옴 (10틱마다)
            if (now % 10 == 0) pullIn(u, ragVictims(u, R, RAG_PULL));
            List<ServerPlayer> list = ragVictims(u, R, RAG_REACH);
            long left = R.until - now;
            if (now % 10 == 0) {
                for (ServerPlayer p : list) {
                    long s = (long) Math.ceil(left / 20.0);
                    BossKit.notify(p, shadowOf(u, p.position(), from) != null ? "§b얼음 결정 그림자 안 §a— 안전 §7(" + s + "초)" : "§c불길이 옵니다! §f수르트 반대쪽 얼음 결정 뒤로 §7(" + s + "초)",
                            15);
                }
            }
            if (left > 0 && left % 20 == 0 && left <= 60) faceAll(u, "§4§l라그나로크 " + (left / 20), Opt.of("dist", 2.4, "up", 0.45), list);
            if (now % 20 == 0) {
                for (ServerPlayer p : list) {
                    SkillFx.fx(u.dim, "rpg:ember", new Vec3(p.getX(), p.getY() + 4, p.getZ()), Opt.of("size", 6.0, "life", 1.0, "speed", 2.0, "color", EMBER, "alpha", 0.8));
                }
            }
            return;
        }
        if ("wave".equals(R.stage)) waveTick(u, now);
    }

    /** 결정의 그림자 (수르트 반대쪽 depth 칸, 폭 = 반폭 + pad) 를 바닥에 파란 판으로 */
    static void shadowMarks(Bosses.Unit u, Struct c, Vec3 from, int ticks) {
        Obj G = cfg(u, "ragnarok");
        Vec3 v = dirTo(from, c.at);
        Vec3 r = rightOf(angOf(v));
        double w = c.half + G.num("pad");
        double life = Math.max(0.1, ticks / 20.0);
        for (double s = c.half + 0.6; s <= c.half + G.num("depth"); s += 1.4) {
            for (int k : new int[] {-1, 0, 1}) {
                Vec3 at = new Vec3(c.at.x + v.x * s + r.x * k * (w - 0.5), u.center.y + 0.08, c.at.z + v.z * s + r.z * k * (w - 0.5));
                SkillFx.fx(u.dim, k == 0 ? "rpg:tele_fill" : "rpg:tele_disc", at, Opt.of("dir", Vec3.ZERO, "size", k == 0 ? 1.6 : 0.7, "life", life, "color", SAFE, "alpha",
                        k == 0 ? 0.55 : 0.9, "grow", 1.0));
            }
        }
    }

    static void ragRelease(Bosses.Unit u) {
        Obj G = cfg(u, "ragnarok");
        M m = mem(u);
        Ragnarok R = m.ragnarok;
        if (R == null) return;
        long now = Tasks.now();
        Vec3 b = bodyOf(u);
        R.stage = "wave";
        // 불길은 라그나로크를 받는 거리 (가운데에서 RAG_REACH) 끝까지 퍼짐. ahead = 앞머리가 아직 닿지 않은 사람 (지금 있는 사람 모두 + 앞머리 앞에서 본 사람)
        Set<UUID> ahead = new HashSet<>();
        for (ServerPlayer p : ragVictims(u, R, RAG_REACH)) ahead.add(p.getUUID());
        R.wave = new Wave(now, new Vec3(b.x, u.center.y, b.z), RAG_REACH + hdist(b, u.center) + 1, ahead);
        hold(u, "release");
        double speed = G.num("speed");
        double life = R.wave.max / speed;
        SkillFx.fx(u.dim, "rpg:fire_wave", new Vec3(b.x, u.center.y, b.z), Opt.of("speed", speed, "size", 3.0, "life", life, "color", FIRE, "alpha", 1.0));
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(b.x, u.center.y + 0.2, b.z), Opt.of("dir", Vec3.ZERO, "speed", speed, "size", 1.0, "life", life, "color", LAVA, "alpha", 0.95));
        blast(u.dim, floorAt(u, b.x, b.z), 10, FIRE);
        play(u.dim, snd(u, "erupt"), b, 0.4, 1);
        play(u.dim, snd(u, "roar"), headAt(u), 0.5, 1);
        for (ServerPlayer p : fighters(u)) SkillFx.shake(p, 0.6, 1.4);
    }

    /**
     * 불길: 앞머리가 지나간 사람을 판정 (온전한 결정의 그림자 안이면 삶, 그 결정은 녹음) → 다 지나가면 끝.
     * 판정은 앞머리가 닿기 전에 있던 사람만 (불길이 지나간 뒤에 들어온 사람은 아님 · 앞머리를 건너뛰어 뒤로 간 사람은 판정). 끝날 때 남은 사람도 판정
     */
    static void waveTick(Bosses.Unit u, long now) {
        Obj G = cfg(u, "ragnarok");
        M m = mem(u);
        Ragnarok R = m.ragnarok;
        Wave W = R.wave;
        double front = (G.num("speed") * (now - W.t0)) / 20;
        boolean done = front > W.max;
        for (ServerPlayer p : ragVictims(u, R, RAG_REACH)) {
            if (R.judged.contains(p.getUUID())) continue;
            if (!done && hdist(p.position(), W.from) > front + 0.5) {
                W.ahead.add(p.getUUID());
                continue;
            }
            if (!W.ahead.contains(p.getUUID())) continue;
            R.judged.add(p.getUUID());
            Struct c = shadowOf(u, p.position(), W.from);
            if (c != null) {
                c.used = true;
                R.saved++;
                showFaceText(p, "§b§l얼음 결정이 막아 주었습니다", Opt.of("dist", 2.4, "up", 0.45));
                SkillFx.fx(u.dim, "rpg:splash", new Vec3(c.at.x, c.at.y + 3, c.at.z), Opt.of("size", 3.0, "life", 0.6, "color", ICE, "alpha", 0.95));
                continue;
            }
            R.killed++;
            flame(u.dim, p.position(), 3.5, 0.8, LAVA);
            if (broken(m, "sword")) {
                if (bossHit(u, p, G.num("brokenDamage"), Opt.of("noSource", true, "cause", "magic"))) burn(p, 4);
                showFaceText(p, "§c§l불길에 휩쓸렸습니다 §7(레바테인이 부서져 버팀)", Opt.of("dist", 2.4, "up", 0.45));
                continue;
            }
            say(u, "§4[보스] §e" + p.getName().getString() + "§f님이 세계를 태우는 불길에 휩쓸렸습니다");
            Status.kill(p, u.boss);
        }
        if (!done) return;
        // 끝: 숨겨 준 결정은 녹음, 붉은 하늘이 걷힘
        for (Struct c : m.crystals) {
            if (!c.used) continue;
            c.used = false;
            if (c.intact) meltCrystal(u, c);
        }
        m.ragnarok = null;
        m.ragDone++;
        release(u, "rest");
        m.bigUntil = now + 100;
        for (String k : List.of("sweep", "thrust", "meteors", "rings")) m.next.put(k, Math.max(m.next.getOrDefault(k, 0L), now + 60));
        int left = 0;
        for (Struct c : m.crystals) if (c.intact) left++;
        say(u, "§4[보스] §f라그나로크가 지나갔습니다 §7(남은 얼음 결정 " + left + "개)");
    }

    // ------------------------------------------------------------ 신전 구조물 (얼음 결정 · 사슬 기둥: 싸움 중 녹고 부서짐 → 끝나면 되돌림)

    static BlockPos posOf(Arena.Cell q) {
        return new BlockPos(q.x(), q.y(), q.z());
    }

    /** 한 칸 놓기 (짓기와 같은 방식 — 이웃을 건드리지 않음). 불러오지 않은 칸이면 false */
    static boolean setCell(ServerLevel dim, Arena.Cell q, BlockState s) {
        if (s == null) return false;
        BlockPos pos = posOf(q);
        if (!dim.isLoaded(pos)) return false;
        dim.setBlock(pos, s, ArenaBuild.FLAGS);
        return true;
    }

    /** 얼음 결정이 녹음: 블록 → 공기, 둘레 서리 → 그을린 현무암 (김 · 물보라) */
    public static void meltCrystal(Bosses.Unit u, Struct c) {
        c.intact = false;
        c.melted = true;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState basalt = ArenaBlocks.state("minecraft:smooth_basalt", null);
        List<Arena.Cell> cells = new ArrayList<>(c.cells);
        cells.sort(Comparator.comparingInt((Arena.Cell q) -> q.y()).reversed());
        for (Arena.Cell q : cells) setCell(u.dim, q, air);
        for (Arena.Cell q : c.frost) setCell(u.dim, q, basalt);
        SkillFx.fx(u.dim, "rpg:splash", new Vec3(c.at.x, c.at.y + 2.5, c.at.z), Opt.of("size", 3.5, "life", 0.7, "color", ICE, "alpha", 0.95));
        for (int i = 0; i < 3; i++) dust(u.dim, new Vec3(c.at.x, c.at.y + i * 2.5, c.at.z), 3.5, STEAM, 1.2);
        play(u.dim, snd(u, "melt"), c.at, 0.7, 1);
        play(u.dim, snd(u, "ice"), c.at, 0.6, 1);
    }

    /** 사슬 기둥이 무너짐 (칼을 놓아준 뒤): 블록 → 공기 (매단 랜턴 먼저: 받침이 먼저 없어지면 랜턴이 아이템으로 떨어짐), 밑동은 금 간 흑암 벽돌 */
    public static void crumble(Bosses.Unit u, Struct c) {
        c.intact = false;
        c.broken = true;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState rubble = ArenaBlocks.state("minecraft:cracked_polished_blackstone_bricks", null);
        int y0 = (int) Math.floor(c.at.y);
        List<Arena.Cell> cells = new ArrayList<>(c.cells);
        cells.sort(Comparator.comparingInt((Arena.Cell q) -> q.block().equals("minecraft:lantern") || q.block().equals("minecraft:soul_lantern") ? 0 : 1)
                .thenComparing(Comparator.comparingInt((Arena.Cell q) -> q.y()).reversed()));
        for (Arena.Cell q : cells) {
            boolean base = q.y() == y0 && Math.abs(q.x() + 0.5 - c.at.x) <= 1 && Math.abs(q.z() + 0.5 - c.at.z) <= 1;
            setCell(u.dim, q, base ? rubble : air);
        }
        for (int i = 0; i < 4; i++) dust(u.dim, new Vec3(c.at.x, c.at.y + i * 4, c.at.z), 4, new double[] {0.4, 0.38, 0.4}, 1.2);
        play(u.dim, "dig.chain", c.at, 0.7, 1);
    }

    /** 구조물이 처음 모습인지 (몇 칸만 봄: 가장 높은 칸 · 가운데 밑동). 불러오지 않은 곳은 모름 (건드리지 않음 — 참) */
    static boolean looksIntact(ServerLevel dim, Struct c) {
        if (c.cells.isEmpty()) return true;
        Arena.Cell top = c.cells.get(0);
        for (Arena.Cell q : c.cells) if (q.y() > top.y()) top = q;
        int y0 = (int) Math.floor(c.at.y);
        Arena.Cell base = c.cells.get(0);
        for (Arena.Cell q : c.cells) {
            if (q.y() == y0 && Math.abs(q.x() + 0.5 - c.at.x) < 0.6 && Math.abs(q.z() + 0.5 - c.at.z) < 0.6) {
                base = q;
                break;
            }
        }
        for (Arena.Cell q : List.of(top, base)) {
            BlockPos pos = posOf(q);
            if (!dim.isLoaded(pos)) return true;
            if (dim.getBlockState(pos).getBlock() != ArenaBlocks.state(q).getBlock()) return false;
        }
        return true;
    }

    /** 구조물 안에 사람이 있는지 (되돌리면 갇힘) */
    static boolean occupied(Struct c, List<ServerPlayer> list) {
        for (ServerPlayer p : list) {
            if (Math.abs(p.getX() - c.at.x) <= c.half + 1.6 && Math.abs(p.getZ() - c.at.z) <= c.half + 1.6 && p.getY() >= c.at.y - 1 && p.getY() <= c.at.y + c.h + 1) return true;
        }
        return false;
    }

    /** 구조물 되돌리기 (블록 + 결정 둘레 서리 바닥). force 면 안에 있는 사람을 바깥으로 밀어 내고 */
    static boolean restore(ServerLevel dim, Struct c, List<ServerPlayer> list, boolean force) {
        if (occupied(c, list)) {
            if (!force) return false;
            for (ServerPlayer p : list) {
                if (!occupied(c, List.of(p))) continue;
                Vec3 v = dirTo(c.at, p.position());
                p.teleportTo(dim, c.at.x + v.x * (c.half + 3), c.at.y, c.at.z + v.z * (c.half + 3), Relative.ROTATION, 0F, 0F, false);
            }
        }
        for (Arena.Cell q : c.cells) ArenaBlocks.place(dim, q);
        for (Arena.Cell q : c.frost) ArenaBlocks.place(dim, q);
        c.intact = true;
        c.melted = false;
        c.broken = false;
        return true;
    }

    /** 필드 둘레 (반지름 + 12칸) 의 플레이어 */
    static List<ServerPlayer> allNear(Bosses.Unit u) {
        double R = arenaRadius(u) + 12;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : u.dim.players()) if (p.position().distanceTo(u.center) <= R) out.add(p);
        return out;
    }

    static List<Struct> structs(M m) {
        List<Struct> all = new ArrayList<>(m.chains);
        all.addAll(m.crystals);
        return all;
    }

    // ------------------------------------------------------------ 세계를 태우는 불꽃 (체력 50% 아래)

    public static void enrage(Bosses.Unit u, long now) {
        Obj E = cfg(u, "enrage");
        M m = mem(u);
        m.enraged = true;
        u.cdMult = E.num("cd");
        u.dmgMult = E.num("dmg");
        m.body.want = null;
        u.api.anim(u, "roar");
        sound(u, snd(u, "roar"), headAt(u), 0.5, 1);
        play(u.dim, snd(u, "thunder"), u.center, 0.7, 0.8);
        Vec3 b = bodyOf(u);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(b.x, u.center.y + 0.1, b.z), Opt.of("dir", Vec3.ZERO, "size", 18.0, "life", 1.0, "color", FIRE, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:fire_wave", new Vec3(b.x, u.center.y, b.z), Opt.of("speed", 24.0, "size", 1.6, "life", 0.9, "color", LAVA, "alpha", 0.9));
        title(u, "§c§l세계를 태우는 불꽃", "§f수르트의 불길이 거세집니다 — 패턴이 빨라지고 아파집니다", fighters(u));
        explain(u, "enrage", "§c[보스] 세계를 태우는 불꽃: §f체력 " + Js.str(Js.round(E.num("from") * 100)) + "% 아래에서 수르트의 재사용 대기가 ×" + Js.str(E.num("cd")) + ", 주는 피해가 ×"
                + Js.str(E.num("dmg")));
        u.api.cast(u, "enrage", 50);
        m.next.put("meteors", Math.max(m.next.getOrDefault("meteors", 0L), now + 80));
    }

    // ------------------------------------------------------------ 높은 곳 · 둘레 연출

    /**
     * 결정 · 기둥 · 담장 · 탑 위처럼 높은 곳 (선 자리의 바닥이 필드 바닥보다 high.above 칸 넘게) 에 grace 틱 넘게 머문 사람: 불기둥 (사람마다 cd 틱에 한 번).
     * 수르트가 공격하지 못하는 동안은 새로 세거나 치지 않음
     */
    static void highTick(Bosses.Unit u, long now) {
        if (now % 10 != 0 || !u.alive || !Bosses.valid(u.boss)) return;
        Obj d = cfg(u, "high");
        M m = mem(u);
        boolean paused = !canAttack(u, now);
        Vec3 c = u.center;
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : fighters(u)) {
            UUID id = p.getUUID();
            boolean up = groundUnder(p, p.getY()) - c.y >= d.num("above");
            if (!up && !(!p.onGround() && m.highSince.containsKey(id))) continue;
            seen.add(id);
            if (paused) continue;
            if (!m.highSince.containsKey(id)) {
                m.highSince.put(id, now);
                showFaceText(p, "§c§l내려오세요! §f머물면 불기둥이 솟습니다", Opt.of("dist", 2.2, "up", 0.35));
            }
            if (!up || now - m.highSince.get(id) < d.num("grace")) continue;
            if (now - m.high.getOrDefault(id, -1000L) < d.num("cd")) continue;
            m.high.put(id, now);
            Vec3 dir = dirTo(p.position(), c);
            for (int i = 0; i < 3; i++) flame(u.dim, new Vec3(p.getX(), p.getY() - 1 + i * 2, p.getZ()), 3, 0.6, i != 0 ? FIRE : LAVA);
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("dir", Z_AXIS, "size", 2.4, "life", 0.4, "color", FIRE, "alpha", 0.95));
            play(u.dim, snd(u, "erupt"), p.position(), 1.2, 0.8);
            if (bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 1.1, dir.z * 1.1, 0.35), "noSource", true))) burn(p, 2);
            showFaceText(p, "§c§l불기둥에 떨어집니다", Opt.of("dist", 2.2, "up", 0.35));
            explain(u, "high", "§6[보스] §f결정·기둥·담장 위처럼 높은 곳에 " + Js.toFixed(d.num("grace") / 20, 1) + "초 넘게 머물면 발밑에서 불기둥이 솟습니다");
        }
        m.highSince.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 둘레 연출: 불씨 (1.5초마다), 핵 이름표 · 시간 */
    static void ambientTick(Bosses.Unit u, long now) {
        M m = mem(u);
        if (m.core != null) {
            if (m.core.e == null || m.core.e.isRemoved() || now >= m.core.until + 40) cover(u);
            else if (now % 10 == 0) {
                SkillFx.fx(u.dim, "rpg:bash_ring", new Vec3(m.core.e.getX(), u.center.y + 1.6, m.core.e.getZ()), Opt.of("dir", UP, "size", 1.6, "life", 0.5, "color", GOLD, "alpha",
                        0.85));
            }
        }
        if (now % 30 != 0 || m.leap != null || m.body.h < -0.5) return;
        Vec3 h = headAt(u);
        if (!broken(m, "crown")) SkillFx.fx(u.dim, "rpg:ember", new Vec3(h.x, h.y + 4, h.z), Opt.of("size", 5.0, "life", 1.2, "speed", 2.5, "color", EMBER, "alpha", 0.85));
        if (m.enraged) SkillFx.fx(u.dim, "rpg:ember", new Vec3(h.x, u.center.y + 8, h.z), Opt.of("size", 10.0, "life", 1.2, "speed", 3.0, "color", LAVA, "alpha", 0.8));
    }

    // ------------------------------------------------------------ 안개

    static String fogWant(Bosses.Unit u) {
        M m = mem(u);
        if (m.ragnarok != null && !"leap".equals(m.ragnarok.stage)) return FOG_RAGNAROK;
        if (m.inferno != null) return FOG_INFERNO;
        return null;
    }

    // ------------------------------------------------------------ 등장 (화구 밑에서 솟아오름)

    static void arrive(Bosses.Enc enc) {
        Obj A = cfg(enc, "arrive");
        Vec3 c = enc.center;
        int rise = (int) A.num("rise");
        int roar = (int) A.num("roar");
        double depth = A.num("depth");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int k = 1; k <= rise; k++) {
            int kk = k;
            steps.add(step(k, (x, n) -> {
                double t = (double) kk / rise;
                mem(x).body.h = -depth * (1 - t) * (1 - t);
                if (kk % 5 == 0) {
                    double a = random() * TAU;
                    double r = 1 + random() * (ArenaThemes.muspelSize(x.key).pit() - 1);
                    Vec3 at = floorAt(x, c.x + Math.cos(a) * r, c.z + Math.sin(a) * r);
                    flame(x.dim, at, 3 + t * 3, 0.7, kk % 10 != 0 ? FIRE : LAVA);
                    if (kk % 10 == 0) play(x.dim, snd(x, "lava"), c, 0.5 + t * 0.4, 1);
                }
                if (kk % 20 == 0) shakeNear(x, c, 48, 0.12 + t * 0.25, 0.6);
            }));
        }
        steps.add(step(rise, (x, n) -> {
            mem(x).body.h = 0;
            landFx(x, floorAt(x, c.x, c.z), 12);
            hold(x, "roar_hold");
            sound(x, snd(x, "roar"), headAt(x), 0.5, 1);
            title(x, "§c§l" + x.def.str("name"), "§6" + x.def.strOr("title", ""), audience(x));
        }));
        steps.add(step(rise + roar, (x, n) -> {
            x.immune = false;
            release(x, "rest");
            List<Object> notes = x.def.listOrNull("notes");
            say(x, "§c[초월 보스] §f" + x.def.str("name") + ": §7" + (notes != null && !notes.isEmpty() ? "패턴을 깨는 법 — 반격 · 무력화 · 신전 구조물 · 부위 파괴 (도전 창에 정리)" : ""));
        }));
        enc.api.cast(enc, "arrive", rise + roar + 2, steps);
    }

    /** 시험 · 사진첩: 등장 (5초 솟아오름 + 포효) 을 건너뛰고 바로 싸움 (무적 풂 · 땅 위) */
    public static void skipArrive(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null || m.body == null) return;
        if (enc.cast != null && "arrive".equals(enc.cast.id)) enc.cast = null;
        m.body.h = 0;
        enc.immune = false;
        place(enc);
        release(enc, "rest");
    }

    // ------------------------------------------------------------ 모듈

    /** 땅에 내려놓음 (뛰다가 쓰러지거나 싸움이 끝날 때): 높이 0, 걸음 0, 들고 있던 모습을 풂 */
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

    /**
     * 맞을 때: 반격 창 (칼이 금빛) 에 스킬 · 룬 피해 (h.skill) → 반격, 업화 동안 업화 막대를 깎음, 받는 피해 보너스 (빈틈 · 속박 · 반격 중 가장 큰 것) + 표시
     */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        M m = mem(u);
        if (m == null || m.body == null) return null;
        long now = Tasks.now();
        if (m.counter != null && h != null && h.skill && now <= m.counter.until() && amount > 0) counterHit(u, p, now);
        Bonus o = openBonus(u, now);
        double out = o != null ? amount * (1 + o.bonus()) : amount;
        if (m.inferno != null) gaugeHit(u, out);
        return o != null ? Bosses.Hurt.of(out, o.mark()) : null;
    }

    /** 맞는 소품 (부위의 핵): 체력을 기억하고 이름표 막대 */
    @Override
    public void propHit(Bosses.Enc enc, Entity prop, ServerPlayer p, double amount, double left) {
        M m = mem(enc);
        if (m == null || m.core == null || !sameEntity(m.core.e, prop) || Double.isNaN(left)) return;
        part(m, m.core.kind).hp = Math.max(0, left);
        coreTag(enc);
    }

    /** 핵의 체력이 0: 부위 파괴 */
    @Override
    public void propDied(Bosses.Enc enc, Entity prop, ServerPlayer p) {
        M m = mem(enc);
        if (m == null || m.core == null || !sameEntity(m.core.e, prop)) return;
        partBreak(enc, m.core.kind, p);
    }

    /** 보상에 더하는 것: 부순 부위마다 (설정 partLoot) */
    @Override
    public Map<String, Double> bonusLoot(Bosses.Enc enc) {
        Map<String, Double> out = new LinkedHashMap<>();
        M m = mem(enc);
        Obj loot = enc.def.objOrNull("partLoot");
        if (m == null || loot == null) return out;
        for (String k : PART_ORDER) {
            if (!broken(m, k)) continue;
            Obj o = loot.objOrNull(k);
            if (o == null) continue;
            for (String item : o.keys()) out.merge(item, o.num(item), Double::sum);
        }
        return out;
    }

    /** 처치 뒤 남기는 줄: 부위 파괴 · 반격 · 업화 · 속박 */
    @Override
    public List<String> killNote(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return List.of();
        List<String> names = new ArrayList<>();
        for (String k : PART_ORDER) {
            Limb P = part(m, k);
            if (P == null || !P.broken) continue;
            names.add(PART_NAMES.get(k) + (P.by.isEmpty() ? "" : " (" + P.by + ")"));
        }
        return List.of("§6[부위 파괴] §f" + names.size() + "/3" + (names.isEmpty() ? "" : " §7— " + String.join(" · ", names)),
                "§7반격 " + m.counters + "번 · 업화 막음 " + m.infernoWins + "/" + (m.infernoWins + m.infernoFails) + " · 사슬 속박 " + m.binds + "번");
    }

    /** 원거리 견제: 큰 패턴 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        return canAttack(u, now);
    }

    /** 던지는 곳: 왼손 (칼은 오른손) */
    @Override
    public Vec3 farFrom(Bosses.Unit u, Obj style) {
        M m = mem(u);
        return m != null && m.body != null ? leftHandAt(u) : null;
    }

    @Override
    public void fogCleared(Bosses.Enc enc, ServerPlayer p) {
        M m = mem(enc);
        if (m != null && m.fog != null) m.fog.remove(p.getUUID());
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        Vec3 c = enc.center;
        Vec3 p = enc.starter != null && !enc.starter.isRemoved() ? enc.starter.position() : new Vec3(c.x, c.y, c.z + 8);
        double yaw = angOf(dirTo(c, p));
        Arena.Features feat = enc.feat != null ? enc.feat : Arena.Features.EMPTY;
        Obj A = d.obj("arrive");
        long after = (long) (A.num("rise") + A.num("roar"));
        M m = new M();
        m.next.put("slam", now + after + 20);
        for (String k : List.of("stomp", "fist", "sweep", "meteors", "thrust", "rings")) m.next.put(k, now + after + (long) d.obj(k).num("first"));
        m.bigUntil = now + after + 160;
        m.body = new Body(c.x, c.z, -A.num("depth"), yaw);
        for (String k : PART_ORDER) m.parts.put(k, new Limb(d.obj("parts").num(k)));
        for (Arena.Column q : feat.chains()) m.chains.add(new Struct(q.at(), q.half(), q.h(), q.cells(), List.of()));
        for (Arena.Column q : feat.crystals()) m.crystals.add(new Struct(q.at(), q.half(), q.h(), q.cells(), q.frost() != null ? q.frost() : List.of()));
        enc.mem = m;
        for (Bosses.Unit u : enc.units) u.mem = m;
        // 지난 싸움에서 되돌리지 못한 구조물 (안에 사람이 있었음 · 세계를 나감) 은 지금 되돌림 (안의 사람은 밀어 냄)
        List<ServerPlayer> near = allNear(enc);
        for (Struct s : structs(m)) if (!looksIntact(enc.dim, s)) restore(enc.dim, s, near, true);
        // 엔진 평타는 쓰지 않음 (몸을 돌리는 내려찍기는 모듈이)
        enc.meleeAt = Long.MAX_VALUE / 4;
        enc.immune = true;
        place(enc);
        bodyTick(enc, now);
        hold(enc, "emerge");
        sound(enc, snd(enc, "summon"), c, 0.6, 1);
        arrive(enc);
    }

    /** 시험 · 사진첩이 정한 패턴 (forceNext) */
    static boolean forced(Bosses.Unit u, String id, long now, List<ServerPlayer> list) {
        switch (id) {
            case "slam" -> {
                return slam(u, now, list);
            }
            case "sweep" -> {
                return sweep(u, now, list);
            }
            case "thrust" -> {
                return thrust(u, now, list);
            }
            case "fist" -> {
                return fist(u, now, list);
            }
            case "meteors" -> {
                return meteors(u, now, list);
            }
            case "rings" -> {
                return rings(u, now, list);
            }
            case "stomp" -> {
                return stomp(u, now, list);
            }
            case "inferno" -> inferno(u, now, list);
            case "ragnarok" -> ragnarok(u, now, list, false);
            case "enrage" -> enrage(u, now);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        Obj d = u.def;
        // 큰 패턴이 아직 이어지는 중이면 (불길이 퍼지는 중 등) 쉼
        if (m == null || m.body == null || m.leap != null || m.ragnarok != null || m.inferno != null) return false;
        String force = forceNext;
        if (force != null) {
            forceNext = null;
            return forced(u, force, now, list);
        }
        double r = u.api.ratio(u);
        // 1) 큰 패턴: 라그나로크 · 무스펠의 업화 (체력 차례대로, 한 번씩)
        if (now >= m.bigUntil) {
            List<Double> ragAt = d.obj("ragnarok").numbersOrNull("at");
            List<Double> infAt = d.obj("inferno").numbersOrNull("at");
            Double rag = null;
            for (Double x : ragAt) {
                if (r <= x && !m.ragSeen.contains(x)) {
                    rag = x;
                    break;
                }
            }
            Double inf = null;
            for (Double x : infAt) {
                if (r <= x && !m.infDone.contains(x)) {
                    inf = x;
                    break;
                }
            }
            // 둘 다 왔으면 체력 문턱이 높은 것 (먼저 온 것) 부터
            if (rag != null && (inf == null || rag >= inf)) {
                m.ragSeen.add(rag);
                ragnarok(u, now, list, ragAt.indexOf(rag) > 0);
                return true;
            }
            if (inf != null) {
                m.infDone.add(inf);
                inferno(u, now, list);
                return true;
            }
        }
        // 2) 세계를 태우는 불꽃
        if (!m.enraged && r <= d.obj("enrage").num("from")) {
            enrage(u, now);
            return true;
        }
        // 3) 불비 · 불의 고리 (넓은 공격, 서로 바로 이어지지 않게)
        if (now >= m.next.get("meteors")) {
            if (meteors(u, now, list)) {
                m.next.put("meteors", now + every(u, d.obj("meteors"), "every"));
                m.next.put("rings", Math.max(m.next.get("rings"), now + (long) d.obj("meteors").num("windup") + 80));
                return true;
            }
            m.next.put("meteors", now + 20);
        }
        if (now >= m.next.get("rings")) {
            if (rings(u, now, list)) {
                m.next.put("rings", now + every(u, d.obj("rings"), "every"));
                m.next.put("meteors", Math.max(m.next.get("meteors"), now + 120));
                return true;
            }
            m.next.put("rings", now + 20);
        }
        // 4) 반격 패턴: 횡베기 · 찌르기 (서로 바로 이어지지 않게)
        if (now >= m.next.get("sweep")) {
            if (sweep(u, now, list)) {
                m.next.put("sweep", now + every(u, d.obj("sweep"), "every"));
                m.next.put("thrust", Math.max(m.next.get("thrust"), now + (long) d.obj("sweep").num("windup") + 60));
                return true;
            }
            m.next.put("sweep", now + 20);
        }
        if (now >= m.next.get("thrust")) {
            if (thrust(u, now, list)) {
                m.next.put("thrust", now + every(u, d.obj("thrust"), "every"));
                m.next.put("sweep", Math.max(m.next.get("sweep"), now + (long) d.obj("thrust").num("windup") + 60));
                return true;
            }
            m.next.put("thrust", now + 20);
        }
        // 5) 불의 주먹 (왼 건틀릿이 온전할 때)
        if (now >= m.next.get("fist")) {
            if (fist(u, now, list)) {
                m.next.put("fist", now + every(u, d.obj("fist"), "every"));
                return true;
            }
            m.next.put("fist", now + 20);
        }
        // 6) 발 구르기 (옆 · 뒤에 붙은 사람)
        if (now >= m.next.get("stomp")) {
            if (stomp(u, now, list)) {
                m.next.put("stomp", now + every(u, d.obj("stomp"), "every"));
                m.next.put("slam", Math.max(m.next.get("slam"), now + (long) d.obj("stomp").num("windup") + 16));
                return true;
            }
            m.next.put("stomp", now + 20);
        }
        // 7) 평타: 레바테인 내려찍기
        if (now >= m.next.get("slam") && slam(u, now, list)) {
            m.next.put("slam", now + (long) Js.round(d.obj("melee").num("every") * u.cdMult));
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
        trailTick(u, now);
        ringTick(u, now);
        infernoTick(u, now);
        ragTick(u, now);
        highTick(u, now);
        ambientTick(u, now);
        BossKit.syncBossFog(u, fogWant(u), now);
        if (!enc.alive) return;
        // 반격 창이 지나면 닫음 (틀이 끊긴 경우 등)
        if (m.counter != null && now > m.counter.until() + 2) m.counter = null;
        // 몸: 뛰는 중에는 leapTick 이, 패턴 · 비틀거림 중에는 제자리 (원하는 쪽으로 돎), 아니면 걸음
        if (m.leap == null) {
            if (u.cast != null || now < u.staggerUntil) standTick(u);
            else {
                m.body.want = null;
                walkTick(u, list != null ? list : fighters(u), now);
            }
        }
        bodyTick(u, now);
    }

    /** 쓰러질 때 (엔진이 쓰러지는 연출 전에): 땅에 내려놓음, 드러난 핵 · 업화 막대를 거둠, 큰 불 */
    @Override
    public void dying(Bosses.Enc enc) {
        ground(enc);
        M m = mem(enc);
        if (m == null || m.body == null) return;
        cover(enc);
        if (m.inferno != null) dropGauge(enc, m.inferno);
        m.inferno = null;
        Vec3 b = bodyOf(enc);
        for (int i = 0; i < 4; i++) blast(enc.dim, new Vec3(b.x, enc.center.y + i * 6, b.z), 6, i % 2 != 0 ? LAVA : FIRE);
        SkillFx.fx(enc.dim, "rpg:fire_wave", new Vec3(b.x, enc.center.y, b.z), Opt.of("speed", 20.0, "size", 2.2, "life", 1.2, "color", FIRE, "alpha", 0.9));
        play(enc.dim, snd(enc, "erupt"), b, 0.5, 1);
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null || m.body == null) return;
        ground(enc);
        cover(enc);
        if (m.inferno != null) dropGauge(enc, m.inferno);
        BossKit.clearBossFog(enc);
        // 녹은 결정 · 무너진 기둥은 처음 모습으로 (안에 사람이 있으면 다음 싸움을 시작할 때)
        List<ServerPlayer> near = allNear(enc);
        for (Struct s : structs(m)) if (!s.intact || s.melted || s.broken) restore(enc.dim, s, near, false);
        m.inferno = null;
        m.ragnarok = null;
        m.trails = new ArrayList<>();
        m.rings = new ArrayList<>();
        m.counter = null;
        m.groggyUntil = 0;
        m.bindUntil = 0;
        enc.immune = false;
    }
}
