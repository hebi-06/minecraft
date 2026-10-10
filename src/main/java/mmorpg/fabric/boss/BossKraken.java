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
import static mmorpg.fabric.boss.BossKit.okTarget;
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
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 크라켄 (boss_kraken.js — v2.0.0, Lv.25, 심해의 군주): 수치는 config BOSSES.kraken.
 * 움직이지 않음 (가라앉은 신전 가운데에 박힘 — 엔진 stationary). 패턴마다 대상 쪽으로 몸을 돌림 (엔진 face, 모델 앞 = 부리).
 * <ul>
 * <li>평타 물어뜯기 (앞 100°, 엔진)</li>
 * <li>촉수 내려치기: 2~3명 쪽으로 차례로 몸을 돌려 앞의 두 촉수를 내려침 (부채꼴) → 피해 + 휘감김 (속박)</li>
 * <li>먹물 분사: 한 명 쪽 부채꼴 → 피해 + 실명 + 침묵, 먹물 웅덩이 (밟고 있으면 느림 + 침묵 + 마법 피해)</li>
 * <li>소용돌이 (체력 85% 아래): 가운데로 끌어당김 → 끝날 때 가운데 crush 칸 안은 큰 피해 (바깥으로 달려야 함)</li>
 * <li>촉수 소환 (체력 80% · 45%): 네 물구덩이에서 촉수 (맞는 소품). 살아 있는 동안 크라켄 회복 + 둘레 휘두르기 (속박).
 * 모두 베면 크라켄 그로기 (받는 피해 증가), life 틱 안에 못 베면 물러남</li>
 * <li>해일 (체력 60% · 25%, 촉수가 없을 때): 한쪽에서 큰 물결 → 물결이 지나갈 때 기둥 그림자에 없으면 즉사 (먹물 웅덩이도 씻겨 나감)</li>
 * <li>높은 곳 (기둥 · 성문 위): 촉수가 끌어내림 (패턴이 닿지 않는 곳에서 버티지 못하게)</li>
 * </ul>
 * 큰 패턴 (소용돌이 · 촉수 소환 · 해일) 은 서로 겹치지 않음 (mem.bigUntil). 촉수가 살아 있는 동안에는 물어뜯기 · 먹물만.
 * 패턴 함수 (slam · ink · whirl · summonAdds · tide) 는 시험 · 사진첩이 바로 부를 수 있게 공개, {@link #forceNext} = 다음 pick 에서 그 패턴.
 * 자바에서 달라진 점: 촉수의 두 줄 이름표 (이름 + 체력 막대) 는 자바 이름표가 한 줄이라 촉수 머리 위 글자 표시 엔티티로 (저장하지 않음, 촉수와 함께 치움).
 */
public final class BossKraken implements BossModule {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-boss");
    static final String KEY = "kraken";
    public static final String TENTACLE = "rpg:prop_kraken_tentacle";
    static final String TENT_ANIM = "animation.rpg.prop.kraken_tentacle";
    /** 촉수 동작의 틀 (솟아오름 · 흔들림 · 감아올림 · 휘두르기 · 물러남 · 쓰러짐을 서로 바꿈) */
    public static final String TENT_CONTROLLER = "rpg_tent";
    /** 촉수 이름표 (글자 표시 엔티티) 의 표시 */
    public static final String LABEL_TAG = "rpg_kraken_tent_label";
    static final double[] SEA = {0.3, 0.78, 0.9};
    static final double[] FOAM = {0.86, 0.96, 1.0};
    static final double[] DEEP = {0.16, 0.42, 0.85};
    static final double[] INK = {0.12, 0.05, 0.2};
    static final double[] INK_MARK = {0.62, 0.3, 0.95};
    static final double[] HEAL = {0.35, 1.0, 0.55};
    /** 해일 그림자 자리: 청록 바닥 위에서 잘 보이게 금빛 */
    static final double[] SAFE = {1.0, 0.85, 0.2};
    static final double[] RED = {1.0, 0.25, 0.15};
    /** 내려치기 예고: 청록 바닥 위에서 잘 보이게 주황빛 붉은색 */
    static final double[] SLAM_MARK = {1.0, 0.42, 0.12};
    static final double[] GOLD = {1.0, 0.85, 0.35};
    static final double[] WHITE = {1, 1, 1};

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (slam · ink · whirl · adds · tide — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> propIds() {
        return List.of(TENTACLE);
    }

    // ------------------------------------------------------------ 기억

    /**
     * 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 큰 패턴이 끝나는 틱 · 알린 규칙 · 먹물 · 그로기 알림 (사람 → 틱) · 먹물 웅덩이 · 소용돌이 · 촉수 ·
     * 쓴 체력 문턱 · 해일 · 치울 촉수 · 그로기 끝 · 높은 곳 끌어내림 (사람 → 틱) · 자바: 촉수 이름표
     */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new LinkedHashMap<>();
        public long bigUntil;
        public final Set<String> told = new HashSet<>();
        public final Map<UUID, Long> inkTold = new HashMap<>();
        public final Map<UUID, Long> staggerTold = new HashMap<>();
        public List<Puddle> puddles = new ArrayList<>();
        public Whirl whirl;
        public Adds adds;
        public final Set<Double> addsDone = new HashSet<>();
        public Tide tide;
        public final Set<Double> tideDone = new HashSet<>();
        public List<Corpse> corpses = new ArrayList<>();
        public long staggerUntil;
        public final Map<UUID, Long> high = new HashMap<>();
        /** 촉수 → 머리 위 이름표 (두 줄: 이름 + 체력 막대) */
        public final Map<UUID, Display.TextDisplay> labels = new HashMap<>();
    }

    /** 먹물 웅덩이: 자리 (바닥) · 반지름 · 끝나는 틱 */
    public record Puddle(Vec3 at, double r, long until) {}

    /** 소용돌이: 시작 · 끝 틱 */
    public record Whirl(long t0, long until) {}

    /** 촉수 하나: 엔티티 · 물구덩이 · 다음 휘두르기 (예고 시작) · 휘두르는 틱 (0 = 예고 없음) · 쓰러졌는지 */
    public static final class Add {
        public final Entity e;
        public final Vec3 hole;
        public long next;
        public long hitAt;
        public boolean dead;

        Add(Entity e, Vec3 hole, long next) {
            this.e = e;
            this.hole = hole;
            this.next = next;
        }
    }

    /** 촉수 소환: 촉수들 · 시작 · 물러나는 틱 (시험이 당길 수 있음) · 회복한 양 */
    public static final class Adds {
        public final List<Add> list = new ArrayList<>();
        public final long t0;
        public long until;
        public double healed;

        Adds(long t0, long until) {
            this.t0 = t0;
            this.until = until;
        }
    }

    /** 해일: 마리 · 물결이 가는 방향 · 시작 · 부딪히는 틱 · 물결 시작 · 끝 (가운데에서 방향으로 잰 거리) · 사람마다 지난 틱의 거리 · 맞은 사람 */
    public static final class Tide {
        public final Bosses.Unit u;
        public final Vec3 dir;
        public final long t0;
        public final long crashAt;
        public final double s0;
        public final double s1;
        public final Map<UUID, Double> last = new HashMap<>();
        public final Set<UUID> hit = new HashSet<>();

        Tide(Bosses.Unit u, Vec3 dir, long t0, long crashAt, double s0, double s1) {
            this.u = u;
            this.dir = dir;
            this.t0 = t0;
            this.crashAt = crashAt;
            this.s0 = s0;
            this.s1 = s1;
        }
    }

    /** 쓰러지거나 물러난 촉수: 치우는 틱 */
    public record Corpse(Entity e, long at) {}

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    static long every(Bosses.Unit u, Obj d) {
        double[] r = BossKit.pair(d, "every");
        return (long) Js.round(rnd((int) r[0], (int) r[1]) * u.cdMult);
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

    /** 방향을 가로로 deg 도 돌림 (+x 에서 +z 쪽으로) */
    static Vec3 turned(Vec3 d, double deg) {
        double a = Math.atan2(d.z, d.x) + (deg * Math.PI) / 180;
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        say(u, text);
    }

    /** 몸 가운데 (끈 · 연출용) */
    static Vec3 mid(Bosses.Unit u) {
        Vec3 l = u.boss.position();
        return new Vec3(l.x, l.y + bossSize(u.def).height() * 0.55, l.z);
    }

    /** 물보라 (rpg:splash: 튀어 오르는 물방울 왕관, 먹물이면 검게). 그림의 밑동 (아래 1/8) 이 바닥에 닿게 올림 */
    static void splash(ServerLevel dim, Vec3 at, double size, double[] color, double life) {
        SkillFx.fx(dim, "rpg:splash", new Vec3(at.x, at.y + size * 0.75, at.z), Opt.of("size", size, "life", life, "color", color, "alpha", 0.95));
    }

    static void splash(ServerLevel dim, Vec3 at, double size) {
        splash(dim, at, size, FOAM, 0.55);
    }

    /** 마지막 모습을 유지하는 촉수 동작 (다음 동작이 같은 틀에서 바꿀 때까지): 감아올림 · 물러남 · 쓰러짐 */
    static final Set<String> TENT_HOLD = Set.of("coil", "sink", "die");

    /** 촉수 소품 동작: 모두 한 틀 (rpg_tent) 에서 → 휘두르기가 감아올림을 바꾸고, 유지하는 동작은 기본 멈춤식 (끝나면 멈춤) 대신 "0" */
    public static void tentAnim(Entity e, String name) {
        if (e instanceof BedrockMob m && !m.isRemoved()) m.playAnimation(TENT_ANIM + "." + name, TENT_CONTROLLER, TENT_HOLD.contains(name) ? "0" : null);
    }

    /** 쓰러지거나 물러나는 소품: 스킬 · 원거리 조준에서 빠짐 (Kit 이 rpg_gone 을 건너뜀) */
    static void gone(Entity e) {
        if (e != null) e.addTag("rpg_gone");
    }

    /** 둘레의 사람들 화면 흔들기 */
    static void shakeNear(Bosses.Unit u, Vec3 at, double range, double intensity, double seconds) {
        for (ServerPlayer p : fighters(u)) if (hdist(p.position(), at) <= range) SkillFx.shake(p, intensity, seconds);
    }

    // ------------------------------------------------------------ 촉수 내려치기

    /** 부채꼴 안인지 (촉수 내려치기 · 먹물): 가로 + 높이 3칸 안 */
    static boolean inCone(ServerPlayer p, Vec3 apex, Vec3 dir, double radius, double cone) {
        return inFan(p.position(), apex, dir, radius, cone, 0.5) && Math.abs(p.getY() - apex.y) < 3;
    }

    public static boolean slam(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("slam");
        Bosses.Api api = u.api;
        Vec3 c = u.center;
        double radius = d.num("radius");
        double cone = d.num("cone");
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), c) <= radius + 1) cand.add(p);
        List<ServerPlayer> near = shuffle(cand);
        if (near.isEmpty()) return false;
        int n = (int) (api.ratio(u) <= d.num("low") ? d.num("countLow") : d.num("count"));
        List<Vec3> dirs = new ArrayList<>();
        for (int i = 0; i < Math.min(n, near.size()); i++) dirs.add(dirTo(c, near.get(i).position()));
        Vec3 apex = floorAt(u, c.x, c.z);
        int windup = (int) d.num("windup");
        int gap = (int) d.num("gap");
        long[] at = new long[dirs.size()];
        for (int i = 0; i < dirs.size(); i++) at[i] = windup + (long) i * gap;
        for (int i = 0; i < dirs.size(); i++) fanMarks(u.dim, apex, dirs.get(i), radius, cone, (int) at[i], SLAM_MARK, 1.5);
        // 이번 내려치기에서 맞은 사람 (한 번만)
        Set<UUID> struck = new HashSet<>();
        api.face(u, dirs.get(0));
        sound(u, "mob.elderguardian.idle", mid(u), 0.7, 1);
        explain(u, "slam", "§b[보스] 촉수 내려치기: §f크라켄이 몸을 돌려 앞의 두 촉수로 부채꼴을 내려칩니다. §e맞으면 " + Js.toFixed(d.num("root") / 20, 1)
                + "초 휘감겨 움직일 수 없습니다");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int i = 0; i < dirs.size(); i++) {
            Vec3 dir = dirs.get(i);
            // 앞 내려치기가 끝나면 다음 쪽으로 몸을 돌림 (동작은 부딪히기 0.9초 전부터: 촉수를 들어 올리는 동안 마저 돎)
            if (i > 0) steps.add(step(at[i - 1] + 2, (x, t) -> api.face(x, dir)));
            steps.add(step(at[i] - 18, (x, t) -> api.anim(x, "slam")));
            steps.add(step(at[i], (x, t) -> slamHit(x, apex, dir, struck)));
        }
        long last = at[at.length - 1];
        // 끝나면 1초 동안 물어뜯기 없음 (휘감김이 풀린 뒤 피할 틈)
        steps.add(step(last + 12, (x, t) -> x.meleeAt = Math.max(x.meleeAt, Tasks.now() + 20)));
        api.cast(u, "slam", (int) (last + 14), steps);
        return true;
    }

    static void slamHit(Bosses.Unit u, Vec3 apex, Vec3 dir, Set<UUID> struck) {
        Obj d = u.def.obj("slam");
        double radius = d.num("radius");
        // 앞의 두 촉수 (±22.5°) 가 떨어지는 줄을 따라 물보라, 끝에 고리
        for (double side : new double[] {-22.5, 0, 22.5}) {
            Vec3 g = turned(dir, side);
            for (double s = 3; s <= radius - 0.5; s += 2.5) splash(u.dim, new Vec3(apex.x + g.x * s, apex.y, apex.z + g.z * s), side == 0 ? 1.0 : 1.5);
            if (side != 0) {
                SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(apex.x + g.x * (radius - 1.5), apex.y + 0.1, apex.z + g.z * (radius - 1.5)), Opt.of("dir", Vec3.ZERO, "size", 1.6,
                        "life", 0.45, "color", SEA, "alpha", 0.9));
            }
        }
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(apex.x + dir.x * 6, apex.y + 0.8, apex.z + dir.z * 6), Opt.of("dir", new Vec3(0, 0, 1), "size", 3.2, "life", 0.4,
                "color", SEA, "alpha", 0.9));
        play(u.dim, "random.explode", new Vec3(apex.x + dir.x * 6, apex.y, apex.z + dir.z * 6), 0.7, 0.9);
        play(u.dim, "entity.generic.splash", new Vec3(apex.x + dir.x * 6, apex.y, apex.z + dir.z * 6), 0.6, 1);
        shakeNear(u, apex, radius + 4, 0.2, 0.3);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (ServerPlayer p : fighters(u)) {
            if (struck.contains(p.getUUID()) || !inCone(p, apex, dir, radius, d.num("cone"))) continue;
            // 옆으로 밀림 (촉수가 떨어진 쪽 바깥으로)
            double s = (p.getX() - apex.x) * side.x + (p.getZ() - apex.z) * side.z >= 0 ? 1 : -1;
            if (!bossHit(u, p, d.num("damage"), Opt.of("knock", knock(side.x * s * 0.5, side.z * s * 0.5, 0.3)))) continue;
            struck.add(p.getUUID());
            Status.root(p, (int) d.num("root"), "휘감김");
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.4, "life", 0.4, "color", SEA,
                    "alpha", 0.9));
        }
    }

    // ------------------------------------------------------------ 먹물 분사

    public static boolean ink(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("ink");
        Bosses.Api api = u.api;
        M m = mem(u);
        Vec3 c = u.center;
        List<ServerPlayer> cand = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), c) <= d.num("range")) cand.add(p);
        if (cand.isEmpty()) return false;
        ServerPlayer t = cand.get((int) Math.floor(random() * cand.size()));
        Vec3 dir = dirTo(c, t.position());
        double radius = Math.max(8, Math.min(d.num("range"), hdist(t.position(), c) + 2.5));
        // 먼 사람 (가장자리까지) 에게는 좁은 줄기
        double cone = radius > 13 ? d.num("coneFar") : d.num("cone");
        Vec3 apex = floorAt(u, c.x, c.z);
        int windup = (int) d.num("windup");
        api.face(u, dir);
        api.anim(u, "ink");
        fanMarks(u.dim, apex, dir, radius, cone, windup, INK_MARK, 1.5);
        // 실명이 끝날 때까지 내려치기 없음 (예고를 볼 수 있게)
        if (m != null) m.next.put("slam", Math.max(m.next.getOrDefault("slam", 0L), now + windup + (long) d.num("blind")));
        sound(u, "mob.squid.ambient", mid(u), 0.5, 1);
        explain(u, "ink", "§5[보스] 먹물 분사: §f맞으면 실명 + 스킬 봉인(침묵). §d바닥의 먹물 웅덩이§f를 밟고 있으면 느려지고 계속 봉인됩니다");
        // 웅덩이 자리: 대상이 있던 곳 + 부채꼴 안 아무 데나 (가운데 4칸 밖)
        List<Vec3> spots = new ArrayList<>();
        spots.add(floorAt(u, t.getX(), t.getZ()));
        for (int i = 1; i < d.num("puddles"); i++) {
            Vec3 g = turned(dir, (random() - 0.5) * cone * 0.8);
            double s = 4 + random() * (radius - 5);
            spots.add(floorAt(u, c.x + g.x * s, c.z + g.z * s));
        }
        api.cast(u, "ink", windup + 12, step(windup - 3, (x, n) -> sound(x, "mob.squid.ink_squirt", mid(x), 0.6, 1)),
                step(windup, (x, n) -> inkHit(x, apex, dir, radius, cone, spots)));
        return true;
    }

    static void inkHit(Bosses.Unit u, Vec3 apex, Vec3 dir, double radius, double cone, List<Vec3> spots) {
        Obj d = u.def.obj("ink");
        M m = mem(u);
        long now = Tasks.now();
        // 먹물 줄기: 부채꼴을 따라 검은 물보라
        for (double side : new double[] {-cone * 0.35, 0, cone * 0.35}) {
            Vec3 g = turned(dir, side);
            for (double s = 2.5; s <= radius; s += 2.2) {
                Vec3 at = new Vec3(apex.x + g.x * s, apex.y, apex.z + g.z * s);
                splash(u.dim, at, 1.2, INK, 0.6);
                if (side == 0) {
                    SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1.1, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.6, "life", 0.45, "color", INK,
                            "alpha", 0.9));
                }
            }
        }
        play(u.dim, "mob.glow_squid.ink_squirt", apex, 0.5, 1);
        for (ServerPlayer p : fighters(u)) {
            if (!inCone(p, apex, dir, radius, cone)) continue;
            if (!bossHit(u, p, d.num("damage"), Opt.of("effect", BossKit.effect("blindness", (int) d.num("blind"))))) continue;
            Status.silence(p, (int) d.num("silence"));
            showFaceText(p, "§5§l먹물! §7스킬 봉인", Opt.of("dist", 2.2, "up", 0.35));
        }
        for (Vec3 s : spots) {
            if (m != null) m.puddles.add(new Puddle(s, d.num("puddleRadius"), now + (long) d.num("puddleLife")));
            splash(u.dim, s, 1.8, INK, 0.6);
        }
    }

    /** 먹물 웅덩이: 보이기 (10틱마다 새로), 1초마다 밟은 사람은 느림 + 침묵 + 마법 피해 */
    static void puddleTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (m == null || m.puddles.isEmpty()) return;
        m.puddles.removeIf(pd -> now >= pd.until());
        Bosses.Unit u = enc.units.get(0);
        Obj d = u.def.obj("ink");
        if (now % 10 == 0) {
            for (Puddle pd : m.puddles) {
                Vec3 a = pd.at();
                SkillFx.fx(enc.dim, "rpg:tele_fill", new Vec3(a.x, a.y + 0.07, a.z), Opt.of("dir", Vec3.ZERO, "size", pd.r(), "life", 0.7, "color", INK, "alpha", 0.8, "grow",
                        1.0));
                SkillFx.fx(enc.dim, "rpg:tele_disc", new Vec3(a.x, a.y + 0.09, a.z), Opt.of("dir", Vec3.ZERO, "size", pd.r(), "life", 0.7, "color", INK_MARK, "alpha", 0.7));
            }
        }
        if (now % 16 == 0) {
            for (Puddle pd : m.puddles) {
                Vec3 a = pd.at();
                splash(enc.dim, new Vec3(a.x + (random() - 0.5) * pd.r(), a.y, a.z + (random() - 0.5) * pd.r()), 0.6, INK, 0.5);
            }
        }
        if (now % 20 != 0 || !u.alive) return;
        for (ServerPlayer p : fighters(enc)) {
            boolean in = false;
            for (Puddle pd : m.puddles) if (hdist(p.position(), pd.at()) <= pd.r() + 0.3 && Math.abs(p.getY() - pd.at().y) < 2) in = true;
            if (!in) continue;
            BossKit.addEffect(p, "slowness", 40, 1, true);
            Status.silence(p, 30);
            bossHit(u, p, d.num("puddleDamage"), Opt.of("cause", "magic"));
            if (now - m.inkTold.getOrDefault(p.getUUID(), -100L) >= 60) {
                m.inkTold.put(p.getUUID(), now);
                showFaceText(p, "§5먹물 웅덩이 §7— 빠져나오세요", Opt.of("dist", 2.4, "up", 0.3));
            }
        }
    }

    // ------------------------------------------------------------ 소용돌이

    public static void whirl(Bosses.Unit u, long now) {
        Obj d = u.def.obj("whirl");
        Bosses.Api api = u.api;
        M m = mem(u);
        if (m == null) return;
        Vec3 c = u.center;
        int time = (int) d.num("time");
        String crush = Js.str(d.num("crush"));
        m.bigUntil = now + time + 60;
        m.whirl = new Whirl(now, now + time);
        api.anim(u, "spin"); // 동작 길이 = time (build.py): 다섯 바퀴 돌고 끝에 모든 촉수로 내려침
        title(u, "§3§l소용돌이", "§b바깥으로 달리세요! §7(가운데 " + crush + "칸은 짓눌립니다)", fighters(u));
        explain(u, "whirl", "§3[보스] 소용돌이: §f" + Js.str(Js.round(time / 20.0)) + "초 동안 가운데로 끌려갑니다. §c끝날 때 가운데 " + crush + "칸 안에 있으면 크게 다칩니다 §f— 바깥으로 달리세요");
        sound(u, "ambient.underwater.enter", c, 0.6, 1);
        sound(u, "mob.elderguardian.curse", c, 0.7, 0.8);
        disc(u.dim, floorAt(u, c.x, c.z), d.num("crush"), time, RED);
        api.cast(u, "whirl", time + 26, step(time, (x, n) -> whirlCrush(x)));
    }

    /** 소용돌이 도는 동안: 바닥 물살 무늬, tug 틱마다 가운데로 끌어당김 */
    static void whirlTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Whirl w = m == null ? null : m.whirl;
        if (w == null) return;
        Bosses.Unit u = enc.units.get(0);
        Obj d = u.def.obj("whirl");
        Vec3 c = enc.center;
        long age = now - w.t0();
        if (now >= w.until() || !u.alive) {
            m.whirl = null;
            return;
        }
        if (age % 8 == 0) {
            SkillFx.fx(enc.dim, "rpg:whirlpool", new Vec3(c.x, c.y + 0.12, c.z), Opt.of("dir", Vec3.ZERO, "size", d.num("radius"), "life", 0.6, "spin", age * 9.0, "speed",
                    -220.0, "color", DEEP, "alpha", 0.75));
        }
        if (age % 4 == 0) {
            // 네 갈래 나선 물보라 (안으로 말려 들어감)
            for (int k = 0; k < 4; k++) {
                for (double r : new double[] {4, 7, 10, 13, 16}) {
                    double a = (k * Math.PI) / 2 - age * 0.12 + r * 0.22;
                    splash(enc.dim, new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r), 0.7, FOAM, 0.4);
                }
            }
        }
        if (age % 30 == 0) play(enc.dim, "bubble.down", c, 0.7, 1);
        if (age % (long) d.num("tug") != 0) return;
        double strength = d.num("strength");
        for (ServerPlayer p : fighters(enc)) {
            double r = hdist(p.position(), c);
            if (r > d.num("radius") || r < 2.2 || RpgCombat.isSteadfast(p)) continue; // v2.24.0 강철 근육: 끌려가지 않음
            Vec3 dir = dirTo(p.position(), c);
            Kit.knockback(p, dir.x * strength, dir.z * strength, 0);
        }
    }

    static void whirlCrush(Bosses.Unit u) {
        Obj d = u.def.obj("whirl");
        M m = mem(u);
        Vec3 c = u.center;
        double crush = d.num("crush");
        if (m != null) m.whirl = null;
        for (int i = 0; i < 12; i++) {
            double a = (i / 12.0) * Math.PI * 2;
            splash(u.dim, new Vec3(c.x + Math.cos(a) * crush, c.y, c.z + Math.sin(a) * crush), 1.8);
        }
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(c.x, c.y + 0.1, c.z), Opt.of("dir", Vec3.ZERO, "size", crush / 2.1, "life", 0.5, "color", SEA, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(c.x, c.y + 1.5, c.z), Opt.of("dir", new Vec3(0, 0, 1), "size", crush, "life", 0.5, "color", DEEP, "alpha", 0.9));
        sound(u, "random.explode", c, 0.6, 1);
        sound(u, "entity.generic.splash", c, 0.5, 1);
        shakeNear(u, c, d.num("radius"), 0.3, 0.4);
        for (ServerPlayer p : fighters(u)) {
            double r = hdist(p.position(), c);
            if (r > crush + 0.3 || Math.abs(p.getY() - c.y) > 3) continue;
            Vec3 dir = dirTo(c, p.position());
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 0.9, dir.z * 0.9, 0.6)));
        }
    }

    // ------------------------------------------------------------ 촉수 소환

    /** 촉수 이름표 (두 줄: 이름 + 체력 막대 10칸 + 남은 체력) */
    public static String hpTag(double cur, double max) {
        int n = (int) Math.max(0, Math.min(10, Math.ceil((cur / max) * 10)));
        return "§3크라켄의 촉수\n§a" + "|".repeat(n) + "§8" + "|".repeat(10 - n) + " §f" + Js.str(Math.ceil(cur));
    }

    /** 이름표 자리: 촉수 몸 (충돌 상자) 위 */
    static Vec3 labelPos(Entity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.25, e.getZ());
    }

    /** 촉수 머리 위 이름표를 바꿈 (없으면 만듦 — 글자 표시 엔티티, 늘 카메라를 봄 · 밤에도 밝게 · 저장하지 않음) */
    public static void setLabel(Bosses.Unit u, Entity e, String text) {
        M m = mem(u);
        if (m == null || e == null || e.isRemoved() || !(e.level() instanceof ServerLevel level)) return;
        Display.TextDisplay td = m.labels.get(e.getUUID());
        Component c = Component.literal(BedrockText.toJava(text));
        if (td != null && !td.isRemoved()) {
            td.setText(c);
            return;
        }
        td = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level) {
            @Override
            public boolean shouldBeSaved() {
                return false;
            }
        };
        td.setText(c);
        td.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        td.setFlags(Display.TextDisplay.FLAG_SHADOW);
        td.setBrightnessOverride(Brightness.FULL_BRIGHT);
        Vec3 at = labelPos(e);
        td.snapTo(at.x, at.y, at.z, 0F, 0F);
        td.addTag(LABEL_TAG);
        if (level.addFreshEntity(td)) m.labels.put(e.getUUID(), td);
    }

    /** 촉수의 이름표 (없으면 null — 시험용) */
    public static Display.TextDisplay labelOf(Bosses.Unit u, Entity e) {
        M m = mem(u);
        Display.TextDisplay td = m == null || e == null ? null : m.labels.get(e.getUUID());
        return td != null && !td.isRemoved() ? td : null;
    }

    static void dropLabel(M m, Entity e) {
        Display.TextDisplay td = m == null || e == null ? null : m.labels.remove(e.getUUID());
        if (td != null && !td.isRemoved()) td.discard();
    }

    /** 촉수를 치움 (이름표와 함께) */
    static void dropTentacle(Bosses.Enc enc, Entity e) {
        dropLabel(mem(enc), e);
        enc.api.dropProp(enc, e);
    }

    public static boolean summonAdds(Bosses.Unit u, long now) {
        Obj T = u.def.obj("tentacles");
        Bosses.Api api = u.api;
        M m = mem(u);
        List<Vec3> holes = u.feat.holes();
        if (m == null || holes.isEmpty()) return false;
        m.bigUntil = now + 60;
        m.adds = new Adds(now, now + 14 + (long) T.num("life"));
        api.anim(u, "summon");
        title(u, "§3§l촉수 소환", "§b촉수를 모두 베세요! §7(살아 있는 동안 크라켄이 회복)", fighters(u));
        explain(u, "adds", "§3[보스] 촉수 소환: §f네 물구덩이의 촉수가 살아 있는 동안 §a크라켄이 회복§f합니다. §e" + Js.str(Js.round(T.num("life") / 20)) + "초 안에 모두 베면 크라켄이 그로기 (받는 피해 +"
                + Js.str(Js.round(T.num("bonus") * 100)) + "%)");
        sound(u, snd(u, "roar"), mid(u), 0.6, 1);
        for (Vec3 h : holes) disc(u.dim, h, T.num("radius"), 14, SEA, Opt.of("fill", false));
        api.cast(u, "summon", 40, step(14, (x, n) -> spawnAdds(x, holes)));
        return true;
    }

    static void spawnAdds(Bosses.Unit u, List<Vec3> holes) {
        Obj T = u.def.obj("tentacles");
        M m = mem(u);
        long now = Tasks.now();
        if (m == null || m.adds == null) return;
        double hp = T.num("hp");
        for (int i = 0; i < holes.size(); i++) {
            Vec3 h = holes.get(i);
            Vec3 at = new Vec3(h.x, h.y - 1, h.z);
            BedrockMob e = BossKit.spawn(u.dim, TENTACLE, at, BossKit.yawTo(at, new Vec3(u.center.x, h.y + 2, u.center.z)));
            if (e == null) {
                LOG.warn("[mmorpg] kraken tentacle: spawn failed at {}", at);
                continue;
            }
            u.api.addProp(u.enc, e, hp, 4);
            setLabel(u, e, hpTag(hp, hp));
            tentAnim(e, "rise");
            splash(u.dim, h, 2.2);
            SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(h.x, h.y + 0.1, h.z), Opt.of("dir", Vec3.ZERO, "size", 1.5, "life", 0.5, "color", SEA, "alpha", 0.9));
            play(u.dim, "mob.guardian.flop", h, 0.6, 1);
            // 휘두르기는 나온 뒤 2초부터, 촉수마다 1초씩 어긋나게
            m.adds.list.add(new Add(e, h, now + 40 + i * 20L));
        }
        play(u.dim, "entity.generic.splash", u.center, 0.5, 1);
    }

    /** 촉수: 휘두르기 (예고 → 맞음), 10틱마다 크라켄 회복 (초록 끈), 모두 베면 그로기, 시간이 다 되면 물러남 */
    static void addsTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Adds A = m == null ? null : m.adds;
        if (A == null) return;
        Bosses.Unit u = enc.units.get(0);
        Obj T = u.def.obj("tentacles");
        if (A.list.isEmpty()) {
            if (now > A.t0 + 20) m.adds = null; // 하나도 나오지 않음 (소환 실패)
            return;
        }
        List<Add> alive = new ArrayList<>();
        for (Add a : A.list) if (!a.dead && !a.e.isRemoved()) alive.add(a);
        if (alive.isEmpty()) {
            m.adds = null;
            if (u.alive) stagger(u, now);
            return;
        }
        if (now >= A.until) {
            retreat(u, alive, now);
            m.adds = null;
            return;
        }
        for (Add a : alive) {
            if (a.hitAt == 0 && now >= a.next) {
                a.hitAt = now + (long) T.num("windup");
                a.next = now + (long) T.num("every"); // 주기 = every (예고 windup 포함)
                disc(enc.dim, a.hole, T.num("radius"), (int) T.num("windup"), RED);
                tentAnim(a.e, "coil");
            } else if (a.hitAt != 0 && now >= a.hitAt) {
                a.hitAt = 0;
                whip(u, a);
            }
        }
        if (now % 10 == 0 && u.alive && Bosses.valid(u.boss)) {
            float max = u.boss.getMaxHealth();
            double add = max * T.num("heal") * 0.5 * alive.size();
            u.boss.setHealth((float) Math.min(max, u.boss.getHealth() + add));
            A.healed += add;
            Vec3 to = mid(u);
            for (Add a : alive) {
                Vec3 top = new Vec3(a.hole.x, a.hole.y + 3.6, a.hole.z);
                SkillFx.beam(enc.dim, top, to, HEAL, Opt.of("life", 0.55, "half", 0.28, "thick", 0.14, "gap", 0.6));
            }
            if (now % 20 == 0) SkillFx.fx(enc.dim, "rpg:bash_burst", to, Opt.of("dir", new Vec3(0, 0, 1), "size", 1.8, "life", 0.4, "color", HEAL, "alpha", 0.8));
        }
    }

    /** 촉수 휘두르기: 물구덩이 둘레 radius 칸 → 피해 + 속박 */
    static void whip(Bosses.Unit u, Add a) {
        Obj T = u.def.obj("tentacles");
        Vec3 h = a.hole;
        double radius = T.num("radius");
        tentAnim(a.e, "whip");
        for (int i = 0; i < 8; i++) {
            double ang = (i / 8.0) * Math.PI * 2;
            splash(u.dim, new Vec3(h.x + Math.cos(ang) * radius * 0.8, h.y, h.z + Math.sin(ang) * radius * 0.8), 1.1);
        }
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(h.x, h.y + 0.1, h.z), Opt.of("dir", Vec3.ZERO, "size", radius / 2.1, "life", 0.4, "color", SEA, "alpha", 0.9));
        play(u.dim, "entity.generic.splash", h, 0.8, 1);
        play(u.dim, "mob.guardian.hit", h, 0.6, 0.8);
        for (ServerPlayer p : fighters(u)) {
            if (hdist(p.position(), h) > radius + 0.3 || Math.abs(p.getY() - h.y) > 3) continue;
            Vec3 dir = dirTo(h, p.position());
            if (!bossHit(u, p, T.num("damage"), Opt.of("from", a.e, "knock", knock(dir.x * 0.6, dir.z * 0.6, 0.3)))) continue;
            Status.root(p, (int) T.num("root"), "휘감김");
        }
    }

    /** 촉수를 모두 벰: 크라켄 그로기 (패턴 없음, 받는 피해 증가) */
    static void stagger(Bosses.Unit u, long now) {
        Obj T = u.def.obj("tentacles");
        M m = mem(u);
        long until = now + (long) T.num("stagger");
        if (m != null) m.staggerUntil = until;
        u.staggerUntil = until;
        // 하던 패턴 (물어뜯기 · 먹물) 은 끊음: 비틀거리는 동작 뒤에서 예고 없이 맞지 않게
        u.cast = null;
        u.api.anim(u, "stagger");
        title(u, "§e§l그로기!", "§f크라켄이 받는 피해 +" + Js.str(Js.round(T.num("bonus") * 100)) + "% §7(" + Js.str(Js.round(T.num("stagger") / 20)) + "초)");
        say(u, "§e[보스] 촉수를 모두 베었습니다! §f크라켄이 비틀거립니다");
        sound(u, "mob.elderguardian.hit", mid(u), 0.6, 1);
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(u.center.x, u.center.y + 0.1, u.center.z), Opt.of("dir", Vec3.ZERO, "size", 3.0, "life", 0.6, "color", GOLD,
                "alpha", 0.9));
    }

    /** 시간이 다 됨: 남은 촉수는 물속으로 (크라켄은 그만큼 회복한 채로) */
    static void retreat(Bosses.Unit u, List<Add> alive, long now) {
        M m = mem(u);
        for (Add a : alive) {
            a.dead = true;
            u.api.retireProp(a.e);
            gone(a.e);
            tentAnim(a.e, "sink");
            splash(u.dim, a.hole, 2);
            if (m != null) m.corpses.add(new Corpse(a.e, now + 18));
        }
        play(u.dim, "entity.generic.splash", u.center, 0.6, 1);
        say(u, "§3[보스] 남은 촉수 " + alive.size() + "개가 물속으로 물러났습니다 §7(크라켄이 회복했습니다)");
    }

    /** 쓰러지거나 물러난 촉수: 동작이 끝나면 치움 */
    static void corpseTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (m == null || m.corpses.isEmpty()) return;
        List<Corpse> due = new ArrayList<>();
        List<Corpse> keep = new ArrayList<>();
        for (Corpse c : m.corpses) (now >= c.at() ? due : keep).add(c);
        if (due.isEmpty()) return;
        m.corpses = keep;
        for (Corpse c : due) dropTentacle(enc, c.e());
    }

    // ------------------------------------------------------------ 해일

    /**
     * 기둥 그림자인지: p 에서 물결이 오는 쪽 (-dir) 으로 depth 칸 안에 기둥 (가로 반폭 half + pad 상자) 이 있으면 가려짐
     * @param dir 물결이 가는 방향 (단위)
     */
    public static boolean sheltered(List<Arena.Pillar> pillars, Vec3 dir, Vec3 p, double depth, double pad) {
        double rx = -dir.x;
        double rz = -dir.z;
        for (Arena.Pillar c : pillars) {
            double h = c.half() + pad;
            // 가로 선분 p → p + r·depth 와 상자 [c-h, c+h] 가 만나는지 (slab)
            double t0 = 0;
            double t1 = depth;
            boolean hit = true;
            double[][] axes = {{p.x, rx, c.at().x - h, c.at().x + h}, {p.z, rz, c.at().z - h, c.at().z + h}};
            for (double[] ax : axes) {
                double o = ax[0];
                double r = ax[1];
                if (Math.abs(r) < 1e-9) {
                    if (o < ax[2] || o > ax[3]) hit = false;
                    continue;
                }
                double a = (ax[2] - o) / r;
                double b = (ax[3] - o) / r;
                if (a > b) {
                    double t = a;
                    a = b;
                    b = t;
                }
                t0 = Math.max(t0, a);
                t1 = Math.min(t1, b);
            }
            if (hit && t0 <= t1) return true;
        }
        return false;
    }

    /** 물결이 오는 쪽 이름 (예: 북동쪽) */
    public static String sideName(Vec3 dir) {
        double ox = -dir.x;
        double oz = -dir.z;
        String ns = oz < -0.4 ? "북" : oz > 0.4 ? "남" : "";
        String ew = ox > 0.4 ? "동" : ox < -0.4 ? "서" : "";
        return ns + ew + "쪽";
    }

    /** 해일: 8방향 중 k 번째 쪽으로 가는 물결 (k = 0 → +x 동쪽으로 가는 물결, 서쪽에서 옴) */
    public static Vec3 tideDir(int k) {
        return new Vec3(Math.round(Math.cos((k * Math.PI) / 4) * 1e6) / 1e6, 0, Math.round(Math.sin((k * Math.PI) / 4) * 1e6) / 1e6);
    }

    public static void tide(Bosses.Unit u, long now) {
        Obj d = u.def.obj("tide");
        Bosses.Api api = u.api;
        M m = mem(u);
        if (m == null) return;
        double R = u.enc.def.obj("arena").num("radius");
        int k = (int) Math.floor(random() * 8);
        Vec3 dir = tideDir(k);
        // 필드 밖 성문 자리 (+6) 까지 휩씀
        double s0 = -(R + 7);
        double s1 = R + 7;
        int windup = (int) d.num("windup");
        long travel = (long) Math.ceil(((s1 - s0) / d.num("speed")) * 20);
        m.bigUntil = now + windup + travel + 60;
        m.tide = new Tide(u, dir, now, now + windup, s0, s1);
        api.face(u, new Vec3(-dir.x, 0, -dir.z));
        api.anim(u, "call");
        title(u, "§9§l해일", "§b" + sideName(dir) + "에서 큰 물결! §e기둥 뒤 노란 자리§f로 숨으세요", Arena.playersIn(u.a, 8));
        explain(u, "tide", "§9[보스] 해일: §f큰 물결이 필드와 둘레 6칸(성문 밖 가까이)까지 휩쓸고 지나갑니다. §e물결 반대쪽 기둥 뒤(노란 자리)§f에 숨지 않으면 §4즉사§f합니다");
        sound(u, snd(u, "roar"), mid(u), 0.5, 1);
        sound(u, "ambient.underwater.enter", u.center, 0.5, 1);
        api.cast(u, "tide", (int) (windup + travel + 12), step(windup + travel + 10, (x, n) -> {}));
    }

    /** 물결이 오는 쪽 가장자리 거품 벽 (예고, 점점 높아짐) + 기둥 그림자 노란 자리 */
    static void tideWarn(Bosses.Unit u, Tide T, double k) {
        Obj d = u.def.obj("tide");
        Vec3 c = u.center;
        double R = u.enc.def.obj("arena").num("radius");
        Vec3 side = new Vec3(-T.dir.z, 0, T.dir.x);
        double s = -(R - 1.5);
        for (double l = -R; l <= R; l += 3) {
            if (Math.hypot(s, l) > R) continue;
            Vec3 at = new Vec3(c.x + T.dir.x * s + side.x * l, c.y, c.z + T.dir.z * s + side.z * l);
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 0.6 + k * 2.2, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.3 + k, "life", 0.55, "color", FOAM,
                    "alpha", 0.85));
            if (k > 0.3) splash(u.dim, at, 1 + k, DEEP, 0.5);
        }
        // 그림자 자리: 기둥 뒤 판 (실제 판정과 같은 식으로 고름)
        List<Arena.Pillar> pillars = u.feat.pillars();
        for (Arena.Pillar pl : pillars) {
            for (double a = 0.8; a <= d.num("depth") + 1.5; a += 1.2) {
                for (double l = -1.6; l <= 1.61; l += 1.6) {
                    double back = pl.half() * (Math.abs(T.dir.x) + Math.abs(T.dir.z));
                    Vec3 at = new Vec3(pl.at().x + T.dir.x * (back + a) + side.x * l, c.y, pl.at().z + T.dir.z * (back + a) + side.z * l);
                    if (!sheltered(pillars, T.dir, at, d.num("depth"), d.num("pad"))) continue;
                    SkillFx.fx(u.dim, "rpg:tele_fill", new Vec3(at.x, at.y + 0.07, at.z), Opt.of("dir", Vec3.ZERO, "size", 0.8, "life", 0.6, "color", SAFE, "alpha", 0.55,
                            "grow", 1.0));
                }
            }
        }
    }

    /** 해일: 예고 → 물결이 speed 칸/초로 지나감. 이번 틱에 물결이 지나간 사람은 그림자에 없으면 즉사 */
    static void tideTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Tide T = m == null ? null : m.tide;
        if (T == null) return;
        Bosses.Unit u = T.u;
        Obj d = u.def.obj("tide");
        Vec3 c = enc.center;
        if (!u.alive) {
            m.tide = null;
            return;
        }
        double speed = d.num("speed");
        if (now < T.crashAt) {
            double k = (now - T.t0) / d.num("windup");
            if ((now - T.t0) % 10 == 0) tideWarn(u, T, k);
            long left = T.crashAt - now;
            if (left % 20 == 0 && left <= 60) faceAll(u, "§9§l해일 " + (left / 20), Opt.of("dist", 2.4, "up", 0.5), Arena.playersIn(u.a, 8));
            return;
        }
        long age = now - T.crashAt;
        double f0 = T.s0 + (speed * Math.max(0, age - 1)) / 20;
        double f1 = T.s0 + (speed * age) / 20;
        Vec3 side = new Vec3(-T.dir.z, 0, T.dir.x);
        double R = u.enc.def.obj("arena").num("radius");
        if (age == 0) {
            // 물결: 옆으로 4칸마다 한 조각, 물결 방향으로 날아감 (조각은 옆으로 길쭉하게 서서 보는 쪽을 향함)
            double life = (T.s1 - T.s0) / speed;
            for (double l = -(R + 6); l <= R + 6.01; l += 4) {
                Vec3 at = new Vec3(c.x + T.dir.x * T.s0 + side.x * l, c.y + 1.7, c.z + T.dir.z * T.s0 + side.z * l);
                SkillFx.fx(u.dim, "rpg:tide_wave", at, Opt.of("dir", T.dir, "speed", speed, "size", 2.4, "thick", 1.9, "life", life, "color", WHITE, "alpha", 0.95));
            }
            play(u.dim, "ambient.weather.thunder", c, 0.6, 0.6);
            play(u.dim, "random.splash", c, 0.5, 1);
            shakeNear(u, c, R + 8, 0.35, 1.2);
        }
        if (age % 3 == 0) {
            // 물결 앞 거품
            for (double l = -R; l <= R; l += 3) {
                if (Math.hypot(f1, l) > R + 1) continue;
                splash(u.dim, new Vec3(c.x + T.dir.x * f1 + side.x * l, c.y, c.z + T.dir.z * f1 + side.z * l), 1.3);
            }
        }
        if (age % 10 == 0) play(u.dim, "entity.generic.splash", new Vec3(c.x + T.dir.x * f1, c.y, c.z + T.dir.z * f1), 0.6, 1);
        // 먹물 웅덩이는 씻겨 나감
        m.puddles.removeIf(pd -> (pd.at().x - c.x) * T.dir.x + (pd.at().z - c.z) * T.dir.z <= f1);
        for (ServerPlayer p : Arena.playersIn(u.a, 6)) {
            if (!okTarget(p)) continue;
            double s = (p.getX() - c.x) * T.dir.x + (p.getZ() - c.z) * T.dir.z;
            double was = T.last.getOrDefault(p.getUUID(), s);
            T.last.put(p.getUUID(), s);
            // 지난 틱에 물결 앞에 있었고 이번 틱에 물결이 닿음 (거슬러 달려도 빠지지 않게)
            if (T.hit.contains(p.getUUID()) || !(was > f0 && s <= f1)) continue;
            T.hit.add(p.getUUID());
            if (sheltered(u.feat.pillars(), T.dir, p.position(), d.num("depth"), d.num("pad"))) {
                splash(u.dim, p.position(), 1.2);
                showFaceText(p, "§b기둥 뒤에서 버텼습니다", Opt.of("dist", 2.4, "up", 0.3));
                continue;
            }
            splash(u.dim, p.position(), 2.4, DEEP, 0.55);
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.4, "life", 0.5, "color", DEEP,
                    "alpha", 0.95));
            showFaceText(p, "§1§l해일에 휩쓸렸습니다", Opt.of("dist", 2.2, "up", 0.3));
            say(u, "§9[보스] " + p.getName().getString() + "님이 해일에 휩쓸렸습니다");
            Status.kill(p, u.boss);
        }
        if (f1 >= T.s1) m.tide = null;
    }

    // ------------------------------------------------------------ 높은 곳

    /** 기둥 · 성문 위처럼 높은 곳 (바닥보다 high.above 칸 넘게) 에 서 있는 사람: 촉수가 끌어내림 (가운데 쪽으로 밀어 떨어뜨림 + 피해, 사람마다 cd 틱에 한 번) */
    static void highTick(Bosses.Enc enc, long now) {
        if (now % 10 != 0) return;
        M m = mem(enc);
        Bosses.Unit u = enc.units.get(0);
        if (m == null || !u.alive || !Bosses.valid(u.boss)) return;
        Obj d = u.def.obj("high");
        Vec3 c = enc.center;
        for (ServerPlayer p : fighters(enc)) {
            if (!p.onGround() || p.getY() - c.y < d.num("above")) continue;
            if (now - m.high.getOrDefault(p.getUUID(), -1000L) < d.num("cd")) continue;
            m.high.put(p.getUUID(), now);
            Vec3 dir = dirTo(p.position(), c);
            SkillFx.beam(enc.dim, mid(u), new Vec3(p.getX(), p.getY() + 1, p.getZ()), SEA, Opt.of("life", 0.4, "half", 0.35, "thick", 0.3, "gap", 0.6));
            splash(enc.dim, p.position(), 1.6);
            play(enc.dim, "entity.generic.splash", p.position(), 0.7, 1);
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock(dir.x * 1.1, dir.z * 1.1, 0.35)));
            showFaceText(p, "§b§l촉수가 끌어내립니다", Opt.of("dist", 2.2, "up", 0.35));
            explain(u, "high", "§b[보스] §f기둥·성문 위처럼 높은 곳에 올라서면 크라켄의 촉수가 끌어내립니다");
        }
    }

    // ------------------------------------------------------------ 모듈

    /** 원거리 견제 (v2.3.0): 소용돌이 · 해일 (필드 전체 패턴) · 촉수가 나와 있는 동안 (물구덩이 곁에서 촉수와 싸우는 사람은 크라켄에게서 멂) 은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m == null || (m.whirl == null && m.tide == null && m.adds == null);
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj d = enc.def;
        M m = new M();
        m.next.put("slam", now + (long) d.obj("slam").num("first"));
        m.next.put("ink", now + (long) d.obj("ink").num("first"));
        m.next.put("whirl", now + (long) d.obj("whirl").num("first"));
        m.bigUntil = now + 100;
        enc.mem = m;
    }

    /** 체력 문턱 at 목록 중 지금 넘은 것 (쓰지 않은 것 — 없으면 null) */
    private static Double crossed(List<Double> at, double r, Set<Double> done) {
        if (at == null) return null;
        for (Double x : at) if (r <= x && !done.contains(x)) return x;
        return null;
    }

    /** 시험 · 사진첩이 정한 패턴 (시작했으면 true) */
    private static boolean forced(Bosses.Unit u, long now, List<ServerPlayer> list) {
        String f = forceNext;
        if (f == null) return false;
        forceNext = null;
        return switch (f) {
            case "slam" -> slam(u, now, list);
            case "ink" -> ink(u, now, list);
            case "whirl" -> {
                whirl(u, now);
                yield true;
            }
            case "adds" -> summonAdds(u, now);
            case "tide" -> {
                tide(u, now);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        if (m == null) return false;
        if (forced(u, now, list)) return true;
        Obj d = u.def;
        double r = u.api.ratio(u);
        // 체력 문턱 패턴: 촉수 소환 → 해일 (촉수가 없고 다른 큰 패턴이 끝났을 때)
        if (m.adds == null && m.tide == null && m.whirl == null && now >= m.bigUntil) {
            Double at = crossed(d.obj("tentacles").numbersOrNull("at"), r, m.addsDone);
            if (at != null) {
                m.addsDone.add(at);
                if (summonAdds(u, now)) return true;
            }
            Double tt = crossed(d.obj("tide").numbersOrNull("at"), r, m.tideDone);
            if (tt != null) {
                m.tideDone.add(tt);
                tide(u, now);
                return true;
            }
            Obj w = d.obj("whirl");
            if (r <= w.num("from") && now >= m.next.get("whirl")) {
                m.next.put("whirl", now + every(u, w));
                whirl(u, now);
                return true;
            }
        }
        if (m.adds == null && now >= m.next.get("slam")) {
            if (slam(u, now, list)) {
                m.next.put("slam", now + every(u, d.obj("slam")));
                return true;
            }
            m.next.put("slam", now + 20); // 닿는 곳에 아무도 없음: 조금 뒤 다시
        }
        if (now >= m.next.get("ink")) {
            if (ink(u, now, list)) {
                m.next.put("ink", now + every(u, d.obj("ink")));
                return true;
            }
            m.next.put("ink", now + 20);
        }
        return false;
    }

    /** 받는 피해: 그로기 동안 증가 (때린 사람에게 가끔 알림) */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        M m = mem(u);
        long now = Tasks.now();
        if (m == null || now >= m.staggerUntil) return null;
        double bonus = u.def.obj("tentacles").num("bonus");
        if (now - m.staggerTold.getOrDefault(p.getUUID(), -100L) >= 30) {
            m.staggerTold.put(p.getUUID(), now);
            showFaceText(p, "§e그로기 §f+" + Js.str(Js.round(bonus * 100)) + "%", Opt.of("dist", 2.4, "side", -0.5, "up", 0.25));
        }
        return Bosses.Hurt.of(amount * (1 + bonus));
    }

    /** 촉수가 맞음: 이름표의 체력 막대 */
    @Override
    public void propHit(Bosses.Enc enc, Entity prop, ServerPlayer p, double amount, double left) {
        if (!Kit.isType(prop, TENTACLE) || Double.isNaN(left)) return;
        setLabel(enc, prop, hpTag(left, enc.def.obj("tentacles").num("hp")));
    }

    /** 촉수가 쓰러짐: 무너지는 동작 뒤 치움 */
    @Override
    public void propDied(Bosses.Enc enc, Entity prop, ServerPlayer p) {
        M m = mem(enc);
        if (m == null) return;
        Add a = null;
        if (m.adds != null) for (Add x : m.adds.list) if (x.e == prop) a = x;
        if (a != null) a.dead = true;
        gone(prop);
        tentAnim(prop, "die");
        Vec3 at = a != null ? a.hole : prop.position();
        splash(enc.dim, at, 2.4);
        SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 2, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.6, "life", 0.45, "color", SEA, "alpha", 0.9));
        play(enc.dim, "mob.guardian.death", at, 0.6, 1);
        int left = 0;
        if (m.adds != null) for (Add x : m.adds.list) if (!x.dead) left++;
        say(enc, "§b[보스] §e" + (p != null ? p.getName().getString() : "누군가") + "§f님이 촉수를 베었습니다 §7(남은 촉수 " + left + ")");
        m.corpses.add(new Corpse(prop, Tasks.now() + 20));
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        if (mem(enc) == null) return;
        puddleTick(enc, now);
        whirlTick(enc, now);
        addsTick(enc, now);
        tideTick(enc, now);
        corpseTick(enc, now);
        highTick(enc, now);
        // 몸 둘레 거품 (가끔)
        if (now % 40 == 0 && enc.alive && Bosses.valid(enc.boss)) {
            double a = random() * Math.PI * 2;
            splash(enc.dim, new Vec3(enc.center.x + Math.cos(a) * 3, enc.center.y, enc.center.z + Math.sin(a) * 3), 0.8, FOAM, 0.5);
        }
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        m.puddles = new ArrayList<>();
        m.whirl = null;
        m.adds = null;
        m.tide = null;
        m.corpses = new ArrayList<>();
        // 자바: 촉수 이름표 (촉수는 엔진이 치움)
        for (Display.TextDisplay td : new ArrayList<>(m.labels.values())) if (!td.isRemoved()) td.discard();
        m.labels.clear();
    }
}
