package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleUnaryOperator;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 간반테인 (완드) — 후긴과 무닌 · 오딘의 눈 (skills.js v2.22.0 "오딘의 까마귀 · 눈"). 수치는 SKILLS.wand.
 * 세기 "영웅 룬~초월 룬" (Lv.40 마력 125 의 마력 룬 위력 기준) · 오딘의 눈 테두리는 모델 머리 고리 (rpg:odin_eye).
 */
final class Odin {
    private Odin() {}

    static final double[] OD_GOLD = {1.0, 0.8, 0.36}; // 금빛 (지팡이 · 까마귀 빛)
    static final double[] OD_WHITE = {1.0, 0.97, 0.86}; // 흰 금빛
    /** 빛의 까마귀 반 크기 (칸) — 그림 192점 = 2 × 반 크기 (날개 폭 약 160점 → 약 1.7칸) */
    static final double OD_RAVEN = 1.0;
    /** 까마귀를 그리지 않는 눈 둘레 (칸) — 1인칭 화면을 덮지 않게 */
    static final double OD_RAVEN_NEAR = 2.5;
    /** 까마귀가 돌아오는 가장 빠른 빠르기 (틱당 칸) — 멀리서 쪼고 돌아올 때 순간이동처럼 보이지 않게 돌아오는 틱을 늦춤 */
    static final double OD_BACK_SPEED = 1.6;
    /** 돌아오는 길의 가장 긴 거리 (칸) — 내가 멀리 순간이동하면 그만큼 떨어진 곳에서 돌아옴 (쫓는 적은 나에게서 range + jump 칸 안이라 보통은 닿지 않음) */
    static final double OD_HOME_FAR = 40;
    /** 오딘의 눈 그림의 위아래 끝 (반 크기 = 1 — 256점 그림에서 고리 위 뾰족 · 아래 받침이 가운데에서 104점) · 고리 바깥 반지름 (88점) */
    static final double OD_EYE_TOP = 104 / 128.0;
    static final double OD_RING = 88 / 128.0;
    /** 오딘의 눈은 그곳 위에서 내 왼쪽으로 이만큼 (칸) — 1인칭에서 오른손에 든 긴 지팡이의 머리가 화면 가운데 위를 가리므로 */
    static final double OD_EYE_SIDE = 2.5;
    /** rpg:shock_ring 이 수명 동안 커지는 배수 (크기 × (1 + 1.6 × 진행) — 그림의 고리는 반 크기의 약 0.8) */
    static final double RING_GROW = 2.6;

    private static final Obj WAND = Rules.get().obj("WAND");
    /** 후긴과 무닌이 이어서 고르는 적의 무리 (적대 몹 · 이 애드온 몬스터 · 보스 · 허수아비). 조준한 적은 무엇이든 */
    private static final List<String> RUSH_FAMILIES = List.of("monster", "rpg_monster", "rpg_boss", "rpg_boss_prop", "rpg_dummy");
    /** 이어서 고르지 않는 중립 몹 (때리면 무리가 화를 냄 — 조준하면 됨). 자바 이름으로 */
    private static final Set<String> RUSH_NEUTRAL = new HashSet<>();

    static {
        for (String id : new String[] {"minecraft:zombie_pigman", "minecraft:piglin", "minecraft:enderman", "minecraft:wolf", "minecraft:bee",
            "minecraft:polar_bear", "minecraft:llama", "minecraft:trader_llama", "minecraft:panda", "minecraft:dolphin", "minecraft:goat"}) {
            RUSH_NEUTRAL.add(Kit.javaType(id));
        }
    }

    static void register() {
        Transcend.register("odin_ravens", Odin::ravens);
        Transcend.register("odin_eye", Odin::eye);
    }

    // ------------------------------------------------------------ 그림 도구

    /**
     * 빛의 까마귀 한 장 (rpg:odin_raven — 머리 방향 dir 을 따라 눕고 카메라를 봄): vel = 초당 움직임 (다음 틱 자리로 — 틱마다 다시 그려도 매끄럽게),
     * phase = 날갯짓 장의 시작
     */
    static void ravenFx(ServerLevel level, Vec3 at, Vec3 dir, Vec3 vel, double phase, double size, double life) {
        double speed = V.len(vel);
        SkillFx.fx(level, "rpg:odin_raven", at, Opt.of("dir", dir, "size", size, "life", life, "speed", speed, "spin", phase, "color", SkillFx.WHITE,
                "alpha", 1.0, "move", speed > 1e-6 ? V.unit(vel) : dir));
    }

    /** 안수즈 룬 표식 (rpg:odin_mark — 보는 사람을 향함): size 반 크기, life 초. o: grow (커지는 몫) · color · alpha */
    static void odinMarkFx(ServerLevel level, Vec3 at, double size, double life, Opt o) {
        SkillFx.fx(level, "rpg:odin_mark", at, Opt.of("size", size, "life", life, "grow", o.num("grow", 0), "color", o.color("color", SkillFx.WHITE),
                "alpha", o.num("alpha", 1)));
    }

    /** 눈에서 d 칸 떨어진 곳에 그릴 반 크기: 가까우면 작게 (반 크기 ≤ 거리 × k — 1인칭 화면을 덮지 않게), near 칸 안이면 그리지 않음 (0) */
    static double nearSize(double size, double d, double k, double near) {
        return d < near ? 0 : Math.min(size, k * d);
    }

    static double nearSize(double size, double d) {
        return nearSize(size, d, 0.2, 1.2);
    }

    /** 오딘의 눈 (rpg:odin_eye — 보는 사람을 향함): 처음 wax 초 동안 뜸, close 초부터 fade 초 동안 감음, 감은 뒤 번쩍이며 남은 수명 동안 사라짐 */
    static void odinEyeFx(ServerLevel level, Vec3 at, double size, double life, double wax, double close, double fade) {
        SkillFx.fx(level, "rpg:odin_eye", at, Opt.of("size", size, "life", life, "color", SkillFx.WHITE, "alpha", 1.0, "wax", wax, "close", close,
                "fade", fade));
    }

    /** 2차 베지에 곡선 위의 점 (p0 → 조절점 c → p1, v 0~1) */
    static Vec3 bezAt(Vec3 p0, Vec3 c, Vec3 p1, double v) {
        double a = (1 - v) * (1 - v);
        double b = 2 * (1 - v) * v;
        double d = v * v;
        return new Vec3(a * p0.x + b * c.x + d * p1.x, a * p0.y + b * c.y + d * p1.y, a * p0.z + b * c.z + d * p1.z);
    }

    /** 2차 베지에 곡선의 기울기 */
    static Vec3 bezTan(Vec3 p0, Vec3 c, Vec3 p1, double v) {
        return new Vec3(2 * (1 - v) * (c.x - p0.x) + 2 * v * (p1.x - c.x), 2 * (1 - v) * (c.y - p0.y) + 2 * v * (p1.y - c.y),
                2 * (1 - v) * (c.z - p0.z) + 2 * v * (p1.z - c.z));
    }

    /**
     * 까마귀가 한 구간을 나는 길의 조절점 (o = 구간): 가운데에서 위로 · 옆으로 (후긴 sgn +1 · 무닌 -1 — 서로 엇갈림). 같은 적을 다시 쪼면 (1칸 안)
     * 높이 솟았다가 내리꽂힘 — 그 옆은 구간을 시작할 때의 내 오른쪽 (o.loopSide — 화면에서 옆으로 도는 고리). 돌아오는 구간 (o.home) 은 낮고 완만하게.
     * 곡선의 가장 높은 곳이 o.top (천장 아래 높이) 을 넘지 않게 조절점을 낮춤 — 2차 곡선 (끝 높이 a · b, 조절점 c) 의 꼭대기가 M 이 되는 c = M + √((M − a)(M − b))
     */
    private static Vec3 ravenCtrl(Vec3 p0, Vec3 p1, int sgn, Leg o) {
        Vec3 d = V.sub(p1, p0);
        double len = V.len(d);
        double hz = Math.hypot(d.x, d.z);
        boolean home = o != null && o.home;
        Vec3 loopSide = o != null && o.loopSide != null ? o.loopSide : new Vec3(1, 0, 0);
        Vec3 c;
        if (len < 1.0 && !home) c = V.add(V.up(p1, 4.0), loopSide, 3.0 * sgn);
        else {
            Vec3 side = hz > 0.5 ? new Vec3(-d.z / hz, 0, d.x / hz) : loopSide;
            double rise = home ? 0.4 + 0.06 * len : 1.0 + 0.18 * len;
            double lat = home ? 0.3 + 0.08 * len : 0.6 + 0.22 * len;
            c = V.add(V.up(V.add(p0, d, 0.5), rise), side, lat * sgn);
        }
        double m = o != null ? o.top : Double.POSITIVE_INFINITY;
        if (m == Double.POSITIVE_INFINITY) return c;
        double hiEnd = Math.max(p0.y, p1.y);
        double cMax = m >= hiEnd ? m + Math.sqrt((m - p0.y) * (m - p1.y)) : hiEnd;
        return c.y > cMax ? new Vec3(c.x, cMax, c.z) : c;
    }

    /** 까마귀가 쪼은 자리: 금빛 X 번쩍임 + 깃털 (눈 가까이는 작게 — 1.5칸 안이면 깃털만) */
    static void peckFx(ServerLevel level, Vec3 c, Vec3 eye, double[] glow) {
        double de = V.len(V.sub(c, eye));
        if (de < 1.5) {
            SkillFx.sparks(level, c, OD_GOLD, 3, 4);
            return;
        }
        double near = Math.min(1, Math.max(0.45, (de - 0.8) / 2.5));
        Vec3 dir = V.unit(V.sub(c, eye));
        SkillFx.fx(level, "rpg:slash_x", c, Opt.of("dir", dir, "size", 0.9 * near, "life", 0.22, "color", glow, "alpha", 1.0));
        SkillFx.fx(level, "rpg:slash_x", V.add(c, dir, -0.04), Opt.of("dir", dir, "size", 0.6 * near, "life", 0.18, "color", OD_WHITE, "alpha", 1.0));
        SkillFx.sparks(level, c, OD_GOLD, 4, 4);
    }

    /** a → b 곧은 빛 한 줄 (조각 하나) 에서 눈 near 칸 안의 부분은 빼고 (skills.js streakAway — 파티클 하나, 한 틱의 파티클 수를 줄임) */
    static void streakAway(ServerLevel level, Vec3 eye, Vec3 a, Vec3 b, double[] color, double thick, double life, double alpha, double near) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        Vec3 u = V.unit(d);
        double t0 = 0;
        double t1 = len;
        Vec3 w = V.sub(eye, a);
        double tc = V.dot(w, u);
        double lw = V.len(w);
        double off2 = lw * lw - tc * tc;
        if (off2 < near * near) {
            double hw = Math.sqrt(near * near - off2);
            if (tc - hw <= 0) t0 = Math.max(t0, tc + hw);
            else t1 = Math.min(t1, tc - hw);
        }
        if (t1 - t0 > 0.3) Gram.streakLine(level, V.add(a, u, t0), V.add(a, u, t1), color, thick, life, alpha, 0);
    }

    /**
     * 떨어질 땅 (skills.js fallSpot — 신창 강림과 같음): 조준한 몹의 발밑 → 시선이 닿은 곳 (사거리 끝) 에서 가로로 0.4칸 앞 (벽 면에 붙지 않게) 의 아래 땅
     * (물 · 용암 위도 됨). 가로 min 칸보다 가까우면 min 칸 앞 — 그 자리가 눈에서 보이지 않으면 (벽 너머) 밀지 않음. 없으면 null
     */
    static Vec3 fallSpot(ServerPlayer sp, Obj k) {
        ServerLevel level = sp.level();
        Vec3 p = sp.position();
        Vec3 eye = sp.getEyePosition();
        double min = k.num("min");
        Vec3 q;
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, k.num("range"), Kit.BOW.numOr("aimAssist", 0));
        if (r.target() != null && Kit.canHit(sp, r.target())) q = r.target().position();
        else {
            Vec3 d = r.dir();
            q = V.add(eye, d, r.dist());
            if (Math.hypot(d.x, d.z) > 0.2) q = V.add(q, V.flat(d), -0.4);
        }
        Vec3 f = Kit.facingOf(sp);
        double dx = q.x - p.x;
        double dz = q.z - p.z;
        double h = Math.hypot(dx, dz);
        if (h < min) {
            Vec3 pushed = new Vec3(p.x + (h > 0.5 ? dx / h : f.x) * min, q.y, p.z + (h > 0.5 ? dz / h : f.z) * min);
            if (Kit.lineOfSight(sp, eye, new Vec3(pushed.x, Math.max(q.y, p.y) + 1.0, pushed.z))) q = pushed;
        }
        Kit.Hit hit = Kit.ray(level, new Vec3(q.x, q.y + 1.5, q.z), V.DOWN, 24, true);
        if (hit == null) return null;
        double fy = hit.point().y - hit.pos().getY();
        return new Vec3(q.x, hit.pos().getY() + (fy > 0.05 && fy <= 1 ? fy : 1), q.z);
    }

    /** 후긴과 무닌이 이어서 고를 수 있는 적 (적대 무리 — 중립 몹은 아님) */
    private static boolean rushable(Entity e) {
        if (RUSH_NEUTRAL.contains(Kit.typeId(e))) return false;
        for (String f : RUSH_FAMILIES) if (Kit.hasFamily(e, f)) return true;
        return false;
    }

    /** 보스 · 보스 소품 (밀기가 듣지 않음 — 보스 기믹을 깨지 않게) */
    private static boolean bossLike(Entity e) {
        return Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop");
    }

    // ------------------------------------------------------------ 스킬 1 후긴과 무닌

    /**
     * 후긴과 무닌: 조준한 적 (range 칸 — 시선에 겹친 몹, 없으면 조준 보정) + 그 적에서 jump 칸 안의 적 (적대 몹 · 이 애드온 몬스터 · 보스 · 허수아비 —
     * 그 적의 몸에서 보이는, 가까운 순) targets 명까지. 지팡이 앞 마법진에서 빛의 까마귀 둘 (후긴 · 무닌) 이 날아올라 번갈아 hits 번 쪼음 (mult — 적을 돌아가며).
     * 까마귀는 쪼고 나면 다음 쪼음을 잡음: 덜 쪼인 적 (같으면 처음 고른 차례) — 그 틱은 날아갈 시간 (첫 쪼음은 날아오름 launch 틱 + 거리, 그 뒤는 6틱 이상) ·
     * 다른 쪼음에서 gap 틱 · 같은 적은 0.5초 (몹 무적 시간 — 피해가 미뤄지지 않게) 뒤. 곡선으로 날아 쪼는 틱에 그 몸에 닿음 (적이 움직이면 따라감 ·
     * 한 틱에 튀면 지금 자리에서 다시 · 천장이 낮으면 낮게) — 나는 동안 그 적이 쓰러지거나 나에게서 range + jump 칸 밖으로 달아나면 덜 쪼인 다른 적에게로 꺾음
     * (틱을 다시 잡음). 쪼인 적에게 안수즈 룬 표식 → 쪼음을 다 하면 (남은 적이 없으면 바로) 까마귀가 내 앞으로 돌아와 (마지막 쪼음 back 틱 뒤 이상 ·
     * 틱당 OD_BACK_SPEED 칸 이하로) 빛으로 흩어지며 표식이 터짐 (afterMult — 쪼인 적마다 한 번, 넷씩 한 틱 — 놓친 적은 아님).
     * 대상이 없으면 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 것은 없음. 까마귀 그림은 눈 2.5칸 안이면 그리지 않음, 표식 · 터짐은 가까우면 작게
     */
    static boolean ravens(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        RpgCombat.Aim aim = RpgCombat.aimTarget(sp, k.num("range"), WAND.numOr("aimAssist", 0));
        LivingEntity first = aim.target() != null && Kit.canHit(sp, aim.target()) ? aim.target() : null;
        if (first == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7" + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[] glow = Kit.colors(w)[0];
        // 이어서 쪼을 적: 첫 적에서 jump 칸 안 (그 몸에서 보이는 적대 무리, 첫 적에서 가까운 순)
        Vec3 fl = first.position();
        Vec3 fb = Kit.bodyAt0(first, fl);
        List<LivingEntity> others = new ArrayList<>();
        for (LivingEntity e : Kit.visibleFrom(sp, fb, Kit.around(sp, fl, k.num("jump"), 4))) {
            if (e != first && rushable(e)) others.add(e);
        }
        others.sort(Comparator.comparingDouble(e -> V.len(V.sub(e.position(), fl))));
        List<LivingEntity> targets = new ArrayList<>();
        targets.add(first);
        targets.addAll(others.subList(0, Math.min(others.size(), (int) Math.max(0, k.num("targets") - 1))));
        Vec3 launch = RpgCombat.circleAt(sp)[0];
        Hunt h = new Hunt(sp, w, k, targets, glow, ok);
        double rise = k.num("launch");
        for (Raven r : h.ravens) h.issue(r, 0, launch, fly -> rise + fly);
        // 나타남: 지팡이 앞 마법진 번쩍임 (평타 시전 마법진 (반 크기 0.45) 보다 작게) · 금빛 불꽃 · 날갯짓 소리
        SkillFx.fx(level, "rpg:magic_circle_flash", launch, Opt.of("size", 0.3, "life", 0.25, "spin", Kit.rand() * 360, "color", glow, "alpha", 1.0));
        SkillFx.sparks(level, launch, OD_GOLD, 6, 3);
        SkillFx.play(level, "mob.phantom.flap", launch, 1.4, 1.2);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", launch, 1.0, 1.3);
        Kit.ticks(h::step);
        return true;
    }

    /** 쪼인 적: i (적 번호), n (쪼인 수) */
    private static final class Marked {
        final int i;
        int n;

        Marked(int i) {
            this.i = i;
        }
    }

    /**
     * 까마귀가 나는 구간: t0 → t1 틱, p0 에서 idx 적의 몸으로 (-1 은 내 앞으로 돌아옴), top · loopSide (ravenCtrl), steady (고른 빠르기),
     * end (지난 틱에 잰 끝 자리 — 한 틱에 튀었는지 보려고)
     */
    private static final class Leg {
        double t0;
        double t1;
        Vec3 p0;
        int idx;
        boolean home;
        double top;
        Vec3 loopSide;
        boolean steady;
        Vec3 end;
    }

    /** 까마귀 하나: rv 0 후긴 · 1 무닌, sgn 곡선이 엇갈리는 쪽, leg 지금 나는 구간, dir 마지막으로 그린 그림 방향 */
    private static final class Raven {
        final int rv;
        final int sgn;
        Leg leg;
        Vec3 dir;

        Raven(int rv) {
            this.rv = rv;
            this.sgn = rv == 0 ? 1 : -1;
        }
    }

    /** 나는 길 위의 자리 · 다음 틱 자리 · 나는 방향 */
    private record Fly(Vec3 at, Vec3 next, Vec3 dir) {}

    /**
     * 후긴과 무닌 한 번 (skills.js odinRavens 의 지역 상태 · 함수): 쫓는 적, 적마다 쪼은 수 · 마지막으로 쪼은 틱, 쪼인 적의 표식, 까마귀 둘.
     * 틱 (t, t0, t1, T …) 은 스킬을 쓴 때부터 센 틱 (step 의 t)
     */
    private static final class Hunt {
        final ServerPlayer sp;
        final Weapon w;
        final ServerLevel level;
        final BooleanSupplier ok;
        final double[] glow;
        final List<LivingEntity> targets;
        final int n;
        /** 쫓을 수 있는 거리 (나에게서 range + jump 칸) */
        final double leash;
        final double mult;
        final double afterMult;
        final double hits;
        final double speed;
        final double gap;
        final double back;
        /** 적마다 마지막으로 본 몸 */
        final Vec3[] seen;
        /** 적마다 쪼은 수 (쪼러 가는 중 포함) · 마지막으로 쪼은 틱 */
        final int[] pecked;
        final double[] doneOn;
        /** 잡은 쪼음 수 (hits 면 더 잡지 않음 — 꺾은 쪼음은 그대로 하나) */
        double issued;
        /** 마지막으로 쪼은 틱 */
        double lastPeck = -100;
        /** 돌아와 흩어지는 틱 (쪼음을 다 잡으면 정함) */
        double T = Double.POSITIVE_INFINITY;
        /** 쪼인 적: id → 적 번호 · 쪼인 수 (처음 쪼인 순) */
        final Map<UUID, Marked> marked = new LinkedHashMap<>();
        /** 돌아올 자리의 높이: 눈높이에서 1칸 위 (낮은 굴이면 쓸 때 앞 천장 아래 0.6칸까지) */
        final double lift;
        final Raven[] ravens = {new Raven(0), new Raven(1)};

        Hunt(ServerPlayer sp, Weapon w, Obj k, List<LivingEntity> targets, double[] glow, BooleanSupplier ok) {
            this.sp = sp;
            this.w = w;
            this.level = sp.level();
            this.ok = ok;
            this.glow = glow;
            this.targets = targets;
            this.n = targets.size();
            this.leash = k.num("range") + k.num("jump");
            this.mult = k.num("mult");
            this.afterMult = k.num("afterMult");
            this.hits = k.num("hits");
            this.speed = k.num("speed");
            this.gap = k.num("gap");
            this.back = k.num("back");
            this.seen = new Vec3[n];
            for (int i = 0; i < n; i++) seen[i] = Kit.bodyAt0(targets.get(i), targets.get(i).position());
            this.pecked = new int[n];
            this.doneOn = new double[n];
            Arrays.fill(doneOn, -100);
            this.lift = Math.max(0, Math.min(1.0, Kit.skyRoom(level, V.add(sp.getEyePosition(), Kit.facingOf(sp), 3.2), 3, true) - 0.6));
        }

        /** 그 적의 지금 몸 (사라졌으면 마지막으로 본 자리) */
        Vec3 body(int i) {
            seen[i] = Kit.bodyAt0(targets.get(i), seen[i]);
            return seen[i];
        }

        /** 쫓을 수 있는 적: 맞을 수 있고 · 같은 차원 · 나에게서 leash 칸 안 (멀리 달아나거나 날아가면 놓침) */
        boolean alive(int i) {
            if (i < 0) return false;
            LivingEntity e = targets.get(i);
            return Kit.canHit(sp, e) && e.level() == level && V.len(V.sub(e.position(), sp.position())) <= leash;
        }

        /** 돌아올 자리: 내 앞 3.2칸 · lift 위, 후긴은 왼쪽 · 무닌은 오른쪽 (1인칭 화면 위쪽 양옆 — 가까이 오지 않게) */
        Vec3 home(int rv) {
            Vec3 eye = sp.getEyePosition();
            Vec3 f = Kit.facingOf(sp);
            return V.up(V.add(V.add(eye, f, 3.2), V.rightOf(f), rv == 0 ? -1.3 : 1.3), lift);
        }

        /** 구간의 끝 자리: 그 적의 지금 몸 (따라감), 돌아오는 구간은 내 앞 */
        Vec3 endOf(Raven r) {
            return r.leg.idx >= 0 ? body(r.leg.idx) : home(r.rv);
        }

        Fly flight(Raven r, double t) {
            return flight(r, t, endOf(r));
        }

        /** t 틱에 그 구간 위의 자리 · 다음 틱 자리 · 나는 방향 (끝으로 갈수록 빨라짐 — 돌아오는 중에 다시 잡은 구간 (steady) 은 고른 빠르기) */
        Fly flight(Raven r, double t, Vec3 p1) {
            Leg g = r.leg;
            Vec3 c = ravenCtrl(g.p0, p1, r.sgn, g);
            double span = Math.max(1, g.t1 - g.t0);
            double v = ease(g, Math.min(1, Math.max(0, (t - g.t0) / span)));
            double v2 = ease(g, Math.min(1, Math.max(0, (t + 1 - g.t0) / span)));
            Vec3 tan = bezTan(g.p0, c, p1, Math.min(0.999, v));
            Vec3 chord = V.sub(p1, g.p0);
            Vec3 dir = V.len(tan) > 1e-4 ? V.unit(tan) : V.len(chord) > 1e-4 ? V.unit(chord) : V.UP;
            return new Fly(bezAt(g.p0, c, p1, v), bezAt(g.p0, c, p1, v2), dir);
        }

        double ease(Leg g, double u) {
            return g.steady ? u : Math.pow(u, 1.35);
        }

        /**
         * r 이 t 틱에 p0 에서 t1 틱까지 idx 적 (-1 은 내 앞) 으로 나는 구간 — 곡선은 그 가운데 위 천장의 0.6칸 아래까지 (천장에 박히지 않게 · 트였으면 끝없음).
         * 돌아오는 길은 OD_HOME_FAR 칸까지 (내가 멀리 순간이동했으면 그만큼 떨어진 곳에서)
         */
        void setLeg(Raven r, double t, Vec3 p0, double t1, int idx) {
            Vec3 p1 = idx >= 0 ? body(idx) : home(r.rv);
            Vec3 far = V.sub(p0, p1);
            if (idx < 0 && V.len(far) > OD_HOME_FAR) p0 = V.add(p1, V.unit(far), OD_HOME_FAR);
            Vec3 mid = V.len(V.sub(p1, p0)) < 1.0 ? p1 : V.add(p0, V.sub(p1, p0), 0.5);
            double room = Kit.skyRoom(level, mid, 8, true);
            Leg g = new Leg();
            g.t0 = t;
            g.t1 = Math.max(t + 1, t1);
            g.p0 = p0;
            g.idx = idx;
            g.home = idx < 0;
            g.top = room >= 8 ? Double.POSITIVE_INFINITY : mid.y + room - 0.6;
            g.loopSide = V.rightOf(Kit.facingOf(sp));
            g.end = p1;
            r.leg = g;
        }

        /** 다른 까마귀 (but 말고) 가 쪼러 가는 틱 중 가장 늦은 것 (i 적 — i < 0 이면 어느 적이든). 꺾어서 버린 쪼음은 셈하지 않음 */
        double pendingMax(Raven but, int i) {
            double m = -100;
            for (Raven r : ravens) {
                if (r != but && r.leg != null && r.leg.idx >= 0 && (i < 0 || r.leg.idx == i)) m = Math.max(m, r.leg.t1);
            }
            return m;
        }

        /** 덜 쪼인 적 먼저 (같으면 from 에서 가까운 적) — 쫓을 적이 없으면 -1 */
        int pick(Vec3 from) {
            int best = -1;
            double key = Double.POSITIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                if (!alive(j)) continue;
                double kj = pecked[j] * 1000 + V.len(V.sub(body(j), from));
                if (kj < key) {
                    best = j;
                    key = kj;
                }
            }
            return best;
        }

        /**
         * 돌아오는 틱 T (쪼음을 다 잡았을 때): 마지막 쪼음 back 틱 뒤 이상, 까마귀마다 (쪼는 틱 또는 지금) + 내 앞까지 거리 / OD_BACK_SPEED 이상.
         * 돌아오는 중인 까마귀는 지금 자리에서 T 에 닿게 다시 (T 가 바뀌어도 이어지게)
         */
        void planT(double t) {
            if (issued < hits) return;
            double want = Math.max(t + 1, lastPeck + back);
            for (Raven r : ravens) {
                Leg g = r.leg;
                if (g == null) continue;
                Vec3 from = g.idx >= 0 ? body(g.idx) : flight(r, t).at();
                want = Math.max(want, (g.idx >= 0 ? g.t1 : t) + Math.ceil(Math.min(OD_HOME_FAR, V.len(V.sub(from, home(r.rv)))) / OD_BACK_SPEED));
                if (g.idx >= 0) want = Math.max(want, g.t1 + back);
            }
            T = want;
            for (Raven r : ravens) {
                if (r.leg == null || r.leg.idx >= 0 || r.leg.t1 == T) continue;
                boolean moving = r.leg.t0 < t; // 이미 돌아오는 중: 멈칫하지 않게 (처음부터 다시 빨라지지 않고) 고른 빠르기로 이어서
                setLeg(r, t, flight(r, t).at(), T, -1);
                r.leg.steady = moving;
            }
        }

        /** 쫓을 적이 없음: 남은 쪼음은 없이 돌아옴 */
        void goHome(Raven r, double t, Vec3 from) {
            issued = hits;
            setLeg(r, t, from, t + 1, -1);
            planT(t);
        }

        /** j 적을 쪼는 구간을 잡음 (t1 틱 — 정수 틱으로: 설정이 소수여도 쪼는 틱을 지나치지 않게) */
        void aimAt(Raven r, double t, Vec3 from, int j, double t1) {
            pecked[j]++;
            setLeg(r, t, from, Math.ceil(t1), j);
        }

        /**
         * r 이 t 틱에 from 에서 다음 쪼음을 잡음: 덜 쪼인 적 (같으면 처음 고른 차례 — 조준한 적부터 가까운 순, 꺾은 쪼음도 셈).
         * 틱 = ready(나는 틱 수) · 다른 쪼음 (한 것 · 다른 까마귀가 쪼러 가는 것) 에서 gap 틱 · 같은 적은 IFRAMES 틱 뒤. 쪼음을 다 잡았거나 쫓을 적이 없으면 돌아옴
         */
        void issue(Raven r, double t, Vec3 from, DoubleUnaryOperator ready) {
            int i = -1;
            if (issued < hits) {
                for (int j = 0; j < n; j++) if (alive(j) && (i < 0 || pecked[j] < pecked[i])) i = j;
            }
            if (i < 0) {
                goHome(r, t, from);
                return;
            }
            issued++;
            double fly = Math.ceil(V.len(V.sub(body(i), from)) / speed);
            aimAt(r, t, from, i, Math.max(ready.applyAsDouble(fly),
                    Math.max(Math.max(lastPeck, pendingMax(r, -1)) + gap, Math.max(doneOn[i], pendingMax(r, i)) + Kit.IFRAMES)));
            planT(t);
        }

        /** 나는 동안 그 적이 쓰러지거나 달아남: 지금 자리에서 덜 쪼인 다른 적에게로 꺾음 (틱을 다시 잡음 — 버린 쪼음은 셈하지 않음) · 쫓을 적이 없으면 돌아옴 */
        void retarget(Raven r, double t) {
            Vec3 from = flight(r, t).at();
            pecked[r.leg.idx] = Math.max(0, pecked[r.leg.idx] - 1);
            int j = pick(from);
            if (j < 0) {
                goHome(r, t, from);
                return;
            }
            double fly = Math.ceil(V.len(V.sub(body(j), from)) / speed);
            aimAt(r, t, from, j, Math.max(t + Math.max(4, fly), Math.max(doneOn[j], pendingMax(r, j)) + Kit.IFRAMES));
            planT(t);
        }

        /** 쪼음: 그 몸에 닿음 — 금빛 X · 표식 · 피해 → 다음 쪼음 */
        void strike(Raven r, double t, Vec3 eye) {
            int i = r.leg.idx;
            if (!alive(i)) {
                retarget(r, t); // 이 틱에 먼저 쪼인 피해 등으로 쓰러짐
                return;
            }
            LivingEntity e = targets.get(i);
            Vec3 at = body(i);
            lastPeck = t;
            doneOn[i] = t;
            peckFx(level, at, eye, glow);
            SkillFx.play(level, "mob.phantom.bite", at, 1.0, 1.3);
            Marked m = marked.computeIfAbsent(e.getUUID(), id -> new Marked(i));
            m.n++;
            double ms = nearSize(0.32 + 0.06 * m.n, V.len(V.sub(at, eye)));
            if (ms > 0) odinMarkFx(level, at, ms, 0.3, Opt.of("alpha", 0.95));
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> Kit.skillHit(sp, e, w, mult));
            issue(r, t, at, fly -> t + Math.max(6, fly));
        }

        /** 까마귀 그리기 (눈 2.5칸 안은 빼고) + 지나간 자리의 금빛 알갱이 */
        void draw(Raven r, double t, Vec3 eye, Vec3 view) {
            Fly fl = flight(r, t);
            Vec3 at = fl.at();
            Vec3 dir = fl.dir();
            double de = V.len(V.sub(at, eye));
            if (de < OD_RAVEN_NEAR) return;
            // 그림 방향: 나는 쪽을 내 화면 면에 눕힘 (나는 쪽 그대로면 멀어지거나 다가올 때 날개 끝만 보이는 얇은 줄이 됨)
            double dv = V.dot(dir, view);
            Vec3 sd = V.sub(dir, V.scale(view, dv));
            Vec3 sdir = V.len(sd) > 0.2 ? V.unit(sd) : r.dir != null ? r.dir : V.screenDir(view, 0);
            r.dir = sdir;
            // 가까이 오면 작게 (3.6칸 안 — 돌아올 때 1인칭 화면 위쪽을 크게 덮지 않게)
            Vec3 next = fl.next();
            Vec3 vel = new Vec3((next.x - at.x) * 20, (next.y - at.y) * 20, (next.z - at.z) * 20);
            ravenFx(level, at, sdir, vel, t * 0.8 + r.rv * 3, OD_RAVEN * Math.min(1, Math.max(0.55, (de - 1.5) / 4)), 0.075);
            SkillFx.fx(level, "rpg:arrow_spark", V.add(at, dir, -0.6), Opt.of("dir", dir, "speed", 0.3, "size", 0.1, "life", 0.35, "color", OD_GOLD,
                    "alpha", 0.9));
        }

        /** 돌아옴: 까마귀가 내 앞에서 빛으로 흩어짐 → 표식이 터짐 (넷씩 한 틱 — 가까우면 작게) */
        void scatter(Vec3 eye) {
            for (Raven r : ravens) {
                Vec3 h = home(r.rv);
                SkillFx.burst(level, h, glow, 0.35, 0.22);
                SkillFx.sparks(level, h, OD_GOLD, 4, 3);
            }
            SkillFx.play(level, "mob.phantom.flap", eye, 1.2, 0.9);
            SkillFx.play(level, "beacon.power", eye, 0.8, 1.5);
            int j = 0;
            for (Marked m : marked.values()) {
                if (!alive(m.i)) continue;
                LivingEntity e = targets.get(m.i);
                long at = Kit.reserveHit(sp, e, Tasks.now() + j / 4);
                j++;
                Kit.hitAt(sp, e, at, ok, () -> {
                    if (Kit.skillHit(sp, e, w, afterMult) <= 0) return;
                    Vec3 c = Kit.bodyAt0(e, seen[m.i]);
                    double dc = V.len(V.sub(c, sp.getEyePosition()));
                    double ms = nearSize(0.7, dc, 0.16, 1.5); // 1.9배로 커짐 — 다 커져도 거리 × 0.3 안
                    if (ms > 0) odinMarkFx(level, c, ms, 0.3, Opt.of("grow", 0.9));
                    double bs = nearSize(1.0, dc, 0.3, 1.0);
                    if (bs > 0) SkillFx.burst(level, c, glow, bs, 0.25);
                    double th = Math.min(1, Math.max(0.3, (dc - 0.8) / 3));
                    Gram.streakLine(level, V.up(c, 3.2), c, OD_WHITE, 0.14 * th, 0.2, 1, 0);
                    Gram.streakLine(level, V.up(c, 3.2), c, glow, 0.38 * th, 0.25, 0.85, 0);
                    SkillFx.sparks(level, c, OD_GOLD, 4, 5);
                    SkillFx.play(level, "item.trident.thunder", c, 0.8, 1.5);
                    SkillFx.play(level, "firework.blast", c, 1.0, 1.2);
                });
            }
        }

        /** 매 틱 (t = 0, 1, …): 계속하면 true (돌아와 흩어지거나 · 쓰러지거나 · 30초가 지나면 멈춤) */
        boolean step(int t) {
            if (!ok.getAsBoolean() || t > 600) return false;
            Vec3 eye = sp.getEyePosition();
            Vec3 view = sp.getViewVector(1F);
            // 끝 자리가 한 틱에 2칸 넘게 옮겨짐 (그 적이 순간이동 · 날려 감, 내가 순간이동 · 확 돌아섬): 옮겨지기 전 끝으로 잰 지금 자리에서 다시 날아감 (까마귀가 튀지 않게)
            for (Raven r : ravens) {
                Leg g = r.leg;
                Vec3 e1 = endOf(r);
                if (V.len(V.sub(e1, g.end)) > 2) {
                    setLeg(r, t, flight(r, t, g.end).at(), g.t1, g.idx);
                    r.leg.steady = g.steady || g.idx < 0;
                }
                r.leg.end = e1;
            }
            // 쫓던 적을 놓침 (쓰러짐 · 달아남 · 다른 차원) → 다른 적에게로 꺾거나 돌아옴
            for (Raven r : ravens) if (r.leg.idx >= 0 && !alive(r.leg.idx)) retarget(r, t);
            // 쪼음: 이 틱에 끝나는 구간 (그 몸에 닿음) → 다음 쪼음을 잡음
            for (Raven r : ravens) if (r.leg.idx >= 0 && r.leg.t1 <= t) strike(r, t, eye);
            for (Raven r : ravens) draw(r, t, eye, view);
            // 쪼인 적의 표식 (3틱마다 지금 몸에 다시 — 걸어가도 따라감, 겹쳐 그려 깜빡이지 않게)
            if (t % 3 == 0 && t < T) {
                for (Marked m : marked.values()) {
                    if (!alive(m.i)) continue;
                    Vec3 at = body(m.i);
                    double ms = nearSize(0.32 + 0.06 * m.n, V.len(V.sub(at, eye)));
                    if (ms > 0) odinMarkFx(level, at, ms, 0.3, Opt.of("alpha", 0.9));
                }
            }
            if (t < T) return true;
            scatter(eye);
            return false;
        }
    }

    // ------------------------------------------------------------ 스킬 2 오딘의 눈

    /** 눈 반 크기: 트인 높이 room 안 (아래 끝 땅 위 1.2칸 ~ 위끝 천장 아래 0.2칸 — 낮은 굴이면 작게), 가로 거리 hd 에 맞춤 */
    private static double sizeIn(double room, double hd) {
        return Math.max(0.6, Math.min(Math.min(2.8, (room - 1.4) / (OD_EYE_TOP * 2)), Math.max(1.8, hd * 0.32)));
    }

    /** 눈 가운데 높이 (그곳에서): 위끝이 눈높이에서 26° 위 (tan 26° = 0.49) 를 넘지 않게 — 가까우면 낮게, 그래도 모자라면 천장 쪽을 지킴 */
    private static double highIn(double s, double room, double hd) {
        return Math.min(room - OD_EYE_TOP * s - 0.2, Math.max(OD_EYE_TOP * s + 1.2, Math.min(7, 1.62 + hd * 0.49 - OD_EYE_TOP * s)));
    }

    /** 그곳 위 높이들 hs 에서 수평 방향 d 로 막힘 없이 가는 거리 (가장 짧은 것) */
    private static double openTo(ServerLevel level, Vec3 spot, double[] hs, Vec3 d) {
        double m = Double.POSITIVE_INFINITY;
        for (double h : hs) m = Math.min(m, Kit.rayDist(level, V.up(spot, h), d, OD_EYE_SIDE + 3.2));
        return m;
    }

    /**
     * 오딘의 눈의 광선 (skills.js odinEye 의 물결 상태 · 함수): 이번 물결의 적 (맞는 순) · 그 몸 자리 (사라지면 마지막으로 본 자리) ·
     * 맞는 틱 (물결 시작에서 — 잡아 둔 틱), 적마다 훑는 차례, 한 틱의 그림 수
     */
    private static final class Gaze {
        final ServerPlayer sp;
        final ServerLevel level;
        final Vec3 spot;
        final Vec3 eyeC;
        final double[] glow;
        final double gap;
        final double radius;
        List<LivingEntity> wave = List.of();
        Vec3[] pos = new Vec3[0];
        long[] at = new long[0];
        /** 적마다 훑는 차례: 처음 본 물결의 둘레 차례 (그 뒤에 들어온 적은 뒤에) — 물결마다 다시 재지 않아 0° 줄을 넘어간 적이 차례를 바꿔 무적 시간에 밀리지 않게 */
        final Map<UUID, Integer> rank = new HashMap<>();
        long fxTick = -1;
        int fxN;

        Gaze(ServerPlayer sp, Vec3 spot, Vec3 eyeC, double[] glow, double gap, double radius) {
            this.sp = sp;
            this.level = sp.level();
            this.spot = spot;
            this.eyeC = eyeC;
            this.glow = glow;
            this.gap = gap;
            this.radius = radius;
        }

        Vec3 bodyOf(int j) {
            pos[j] = Kit.bodyAt0(wave.get(j), pos[j]);
            return pos[j];
        }

        /** 그곳에서 본 둘레 각도 (0 ~ 2π) */
        double key(Entity e) {
            double a = Math.atan2(e.getZ() - spot.z, e.getX() - spot.x);
            return (a + Math.PI * 2) % (Math.PI * 2);
        }

        /** 훑는 차례로 줄 세움 (list 를 그대로 정렬해 돌려줌) */
        List<LivingEntity> order(List<LivingEntity> list) {
            List<LivingEntity> fresh = new ArrayList<>();
            for (LivingEntity e : list) if (!rank.containsKey(e.getUUID())) fresh.add(e);
            fresh.sort(Comparator.comparingDouble(e -> key(e)));
            for (LivingEntity e : fresh) rank.put(e.getUUID(), rank.size());
            list.sort(Comparator.comparingInt(e -> rank.get(e.getUUID())));
            return list;
        }

        /** 한 틱에 맞는 적이 많으면 (몰려 있음 · 미뤄진 타격) 그림은 앞의 여섯까지 · 소리는 둘까지 — 이 틱의 몇 번째인지 */
        int fxSlot() {
            long now = Tasks.now();
            if (now != fxTick) {
                fxTick = now;
                fxN = 0;
            }
            return ++fxN;
        }

        /** 이번 물결에서 j 번째 적을 맞히는 틱 (물결 시작에서) */
        long hitOff(int j, int n) {
            return (long) Math.floor(j * gap / Math.max(1, n));
        }

        /**
         * 광선 발: 맞는 틱 (잡아 둔 틱 — 무적 시간에 밀린 적은 그만큼 뒤) 에 그 적의 몸에 있고 그 사이는 다음 적으로 미끄러짐 (마지막 적 뒤에는 이 물결의 첫 적 쪽으로) —
         * 적이 없으면 둘레의 땅을 돎 (비탈이면 그 높이), 하나면 그 몸 둘레를 작게
         */
        Vec3 footAt(int i, int t) {
            int n = wave.size();
            if (n == 0) {
                double a = (i + t / gap) * Math.PI * 2;
                double gx = spot.x + Math.cos(a) * radius * 0.55;
                double gz = spot.z + Math.sin(a) * radius * 0.55;
                Double gy = Kit.solidBelow(level, new Vec3(gx, spot.y + 2, gz), 5);
                return new Vec3(gx, (gy != null ? gy : spot.y) + 0.2, gz);
            }
            if (n == 1) {
                Vec3 c = bodyOf(0);
                double a = (t / gap) * Math.PI * 2;
                double r = 0.35 * Math.abs(Math.sin(((t - at[0]) / gap) * Math.PI)); // 맞는 틱에는 그 몸에
                return new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
            }
            if (t <= at[0]) return bodyOf(0);
            int j = 0;
            while (j + 1 < n && at[j + 1] <= t) j++;
            double t0 = at[j];
            double t1 = j + 1 < n ? at[j + 1] : Math.max(gap, t0 + 1);
            Vec3 a = bodyOf(j);
            Vec3 b = bodyOf((j + 1) % n);
            return V.add(a, V.sub(b, a), Math.min(1, (t - t0) / (t1 - t0)));
        }

        /** 눈동자에서 to 까지 빛줄기 (금빛 · 흰 심지): 내 눈 2칸 안을 지나는 부분은 빼고, 가까이 지나면 가늘게 — 둘레 안의 가까운 적을 비출 때 1인칭 화면을 덮지 않게 */
        Vec3 ray(Vec3 to, double glowT, double coreT, double life, double coreLife, double glowAlpha) {
            Vec3 me = sp.getEyePosition();
            Vec3 d = V.sub(to, eyeC);
            double len = V.len(d);
            Vec3 u = len > 1e-6 ? V.unit(d) : V.DOWN;
            double tc = Math.max(0, Math.min(len, V.dot(V.sub(me, eyeC), u)));
            double th = Math.min(1, Math.max(0.3, (V.len(V.sub(me, V.add(eyeC, u, tc))) - 0.8) / 3));
            streakAway(level, me, eyeC, to, glow, glowT * th, life, glowAlpha, 2.0);
            streakAway(level, me, eyeC, to, OD_WHITE, coreT * th, coreLife, 1, 2.0);
            return me;
        }
    }

    /**
     * 오딘의 눈: 조준한 곳 (range 칸 — 가로 min 칸보다 가까우면 min 칸 앞, 그 아래 땅) 하늘 (그곳 위에서 내 왼쪽으로 2.5칸 — 1인칭에서 오른손의 지팡이 머리에
     * 가리지 않게) 에 지팡이 고리 테두리의 거대한 금빛 눈 (모델 머리 고리 — 보는 사람을 향함) + 땅의 금빛 룬 + 빛 기둥. 눈의 크기 · 높이는 가로 거리에 맞춤
     * (위끝이 눈높이에서 26° 위까지), 굴 · 지붕 · 나뭇잎 아래면 트인 높이에 맞게 작고 낮게, 옆에 벽이 있으면 고리가 벽에 묻히지 않게 덜 옮기거나 작게
     * (옮긴 자리 위가 막혔으면 그곳 위 그대로) → open 틱 동안 뜸 → gap 틱마다 waves 번 광선이 반지름 radius 칸의 적 (눈에서 보이는) 을 둘레를 따라 차례로 훑음
     * (mult — 한 번에 적마다 한 번, 같은 적은 0.5초 간격) → final 6틱 전부터 감기며 final 틱에 대폭발 (afterMult, 밀쳐 띄움 — 보스는 밀지 않음,
     * 가운데에 선 적은 나에게서 먼 쪽으로, 광선에 맞은 적은 밀려났어도 radius + 2.5칸 안이면). 땅을 조준하지 않았거나 (허공) · 그 땅이 눈에서 보이지 않거나
     * (천장 · 지붕 위) · min 칸 앞으로 밀 수 없으면 (벽 · 나무) 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 광선 · 폭발은 없음 (이미 그린 눈은 그대로 감김)
     */
    static boolean eye(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 spot = fallSpot(sp, k);
        Vec3 eye = sp.getEyePosition();
        if (spot == null || (!Kit.lineOfSight(sp, eye, V.up(spot, 1.2)) && !Kit.lineOfSight(sp, eye, V.up(spot, 2.0)))) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7땅을 조준해야 합니다 (" + k.text("range") + "칸 안)");
            return false;
        }
        Vec3 pl = sp.position();
        double hd = Math.hypot(spot.x - pl.x, spot.z - pl.z);
        if (hd < k.num("min") - 0.5) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7" + k.text("min") + "칸 밖의 땅을 조준해야 합니다");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[] glow = Kit.colors(w)[0];
        Vec3 away = hd > 0.5 ? V.flat(V.sub(spot, pl)) : Kit.facingOf(sp);
        Vec3 left = new Vec3(away.z, 0, -away.x);
        // 크기 · 높이: 트인 높이 room (나뭇잎도 막힘) 안에서 (아래 끝 땅 위 1.2칸 ~ 위끝 천장 아래 0.2칸 — 낮은 굴이면 작게, 그래도 모자라면 천장 쪽을 지킴)
        double room0 = Kit.skyRoom(level, V.up(spot, 0.2), 12, false) + 0.2;
        double s = sizeIn(room0, hd); // 눈 반 크기
        // 옆: 내 왼쪽으로 OD_EYE_SIDE 칸. 눈 높이 (가운데 · 고리 반지름의 절반 위아래) 에서 양옆 벽까지 재어 고리 (반지름 OD_RING × s, 0.2칸 여유) 가
        // 벽에 묻히지 않게 — 왼쪽 벽이 가까우면 덜 옮기고, 그래도 들어가지 않으면 (그곳 바로 옆 벽 · 좁은 굴) 눈을 줄임 (가장 작아도 안 되면 두 벽 가운데)
        double h0 = highIn(s, room0, hd);
        double[] hs = {h0 - OD_RING * s * 0.5, h0, h0 + OD_RING * s * 0.5};
        for (int j = 0; j < hs.length; j++) hs[j] = Math.max(0.6, Math.min(room0 - 0.3, hs[j]));
        double dL = openTo(level, spot, hs, left);
        double dR = openTo(level, spot, hs, new Vec3(-left.x, 0, -left.z));
        s = Math.max(0.6, Math.min(s, (Math.min(dL, Math.min((dL + dR) / 2, dR + OD_EYE_SIDE)) - 0.2) / OD_RING));
        double ring = OD_RING * s + 0.2;
        double hi = Math.min(OD_EYE_SIDE, dL - ring);
        double side = hi >= ring - dR ? hi : (dL - dR) / 2;
        // 옮긴 자리 위가 막혔으면 (처마 · 나뭇가지 — 그곳 위보다 낮음) 그곳 위 그대로 (눈이 처마 밑에 작아지지 않게)
        double room = room0;
        if (side > 0.05) {
            double roomCol = Kit.skyRoom(level, V.up(V.add(spot, left, side), 0.2), 12, false) + 0.2;
            if (roomCol < room0 - 0.5) {
                side = 0;
                s = Math.max(0.6, Math.min(s, (Math.min(dL, dR) - 0.2) / OD_RING));
            } else room = Math.min(room0, roomCol);
        }
        double size = Math.min(s, sizeIn(room, hd));
        Vec3 col = V.add(spot, left, side);
        Vec3 eyeC = V.up(col, highIn(size, room, hd));
        double fin = k.num("final");
        double close = fin - 6;
        double open = k.num("open");
        double gap = k.num("gap");
        double radius = k.num("radius");
        double mult = k.num("mult");
        double afterMult = k.num("afterMult");
        odinEyeFx(level, eyeC, size, (fin + 10) / 20, open / 20, close / 20, 6 / 20.0);
        SkillFx.rune(level, spot, Opt.of("size", radius, "life", (fin + 6) / 20, "color", glow, "alpha", 0.75, "rate", 20.0));
        // 눈 아래 땅까지 곧게 내리는 옅은 빛
        SkillFx.beam(level, V.up(eyeC, -OD_RING * size), V.up(col, 0.15), OD_GOLD, Opt.of("half", 0.9, "thick", 0.9, "life", fin / 20, "gap", 1.6,
                "alpha", 0.16));
        SkillFx.shock(level, spot, glow, radius / RING_GROW, 0.5);
        SkillFx.play(level, "beacon.activate", spot, 1.4, 0.9);
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", eyeC, 1.2, 0.8);
        // 광선에 맞은 적 (밀려나도 폭발에 맞음)
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        Gaze g = new Gaze(sp, spot, eyeC, glow, gap, radius);
        double waves = k.num("waves");
        for (int i = 0; i < waves; i++) {
            final int wi = i;
            int t0 = (int) (open + i * gap);
            Tasks.later(t0, () -> {
                if (!ok.getAsBoolean()) return;
                List<LivingEntity> list = g.order(Kit.visibleFrom(sp, eyeC, Kit.around(sp, spot, radius, 3)));
                int n = list.size();
                long base = Tasks.now();
                // 맞는 틱을 먼저 잡고 (무적 시간에 밀린 적은 그만큼 뒤로) 그 순서로 — 광선 발이 맞는 틱에 그 몸에 있게
                long[] at = new long[n];
                for (int j = 0; j < n; j++) at[j] = Kit.reserveHit(sp, list.get(j), base + g.hitOff(j, n)) - base;
                List<Integer> idx = new ArrayList<>();
                for (int j = 0; j < n; j++) idx.add(j);
                idx.sort(Comparator.<Integer>comparingLong(x -> at[x]).thenComparingInt(x -> x));
                List<LivingEntity> wave = new ArrayList<>();
                long[] waveAt = new long[n];
                Vec3[] wavePos = new Vec3[n];
                for (int j = 0; j < n; j++) {
                    LivingEntity e = list.get(idx.get(j));
                    wave.add(e);
                    waveAt[j] = at[idx.get(j)];
                    wavePos[j] = Kit.bodyAt0(e, spot);
                }
                g.wave = wave;
                g.at = waveAt;
                g.pos = wavePos;
                SkillFx.play(level, "beacon.power", eyeC, 1.2, 1.4 + 0.1 * wi);
                for (int j = 0; j < n; j++) {
                    LivingEntity e = wave.get(j);
                    Kit.hitAt(sp, e, base + waveAt[j], ok, () -> {
                        if (Kit.skillHit(sp, e, w, mult) <= 0) return;
                        struck.put(e.getUUID(), e);
                        int slot = g.fxSlot();
                        if (slot > 6) return;
                        Vec3 c = Kit.bodyAt0(e, spot);
                        double dc = V.len(V.sub(c, sp.getEyePosition()));
                        double bs = nearSize(0.75, dc, 0.3, 1.0);
                        if (bs > 0) SkillFx.burst(level, c, glow, bs, 0.2);
                        double ms = nearSize(0.42, dc, 0.14, 1.5); // 1.4배로 커짐
                        if (ms > 0) odinMarkFx(level, c, ms, 0.35, Opt.of("grow", 0.4));
                        SkillFx.sparks(level, c, OD_GOLD, n > 6 ? 2 : 3, 4);
                        if (slot <= 2) SkillFx.play(level, "mob.shulker.bullet.hit", c, 0.8, 1.4);
                    });
                }
            });
            // 광선 그림: 이 물결의 gap 틱 동안 틱마다 (눈동자에서 발까지 — 금빛 + 흰 심지, 발의 번쩍임)
            for (int t = 0; t < gap; t++) {
                final int tt = t;
                Tasks.later(t0 + t, () -> {
                    if (!ok.getAsBoolean()) return;
                    Vec3 f = g.footAt(wi, tt);
                    Vec3 me = g.ray(f, 0.6, 0.18, 0.1, 0.1, 0.85);
                    double fs = nearSize(0.45, V.len(V.sub(f, me)), 0.13, 1.5); // 1.5배로 커짐 — 다 커져도 거리 × 0.2 안
                    if (fs > 0) {
                        SkillFx.fx(level, "rpg:arrow_spark", f, Opt.of("dir", V.UP, "size", fs, "grow", 1.5, "life", 0.12, "color", OD_GOLD, "alpha", 0.9));
                    }
                });
            }
        }
        // 감음 → 대폭발: 흰 · 금빛 폭발 + 고리 둘 + 흰 룬 번쩍 + 눈에서 땅으로 내리꽂히는 빛줄기 여섯 + 솟는 금빛 알갱이 + 흔들림 → 적마다 (여섯씩 한 틱)
        Tasks.later((int) close, () -> {
            if (ok.getAsBoolean()) SkillFx.play(level, "beacon.deactivate", eyeC, 1.2, 0.8);
        });
        Tasks.later((int) fin, () -> {
            if (!ok.getAsBoolean()) return;
            SkillFx.burst(level, V.up(spot, 1.0), OD_WHITE, 3.0, 0.35);
            SkillFx.burst(level, V.up(spot, 1.6), glow, 4.2, 0.45);
            SkillFx.shock(level, spot, glow, radius / 1.8, 0.5);
            SkillFx.shock(level, spot, OD_WHITE, radius / 3.6, 0.4);
            SkillFx.rune(level, spot, Opt.of("size", radius, "life", 0.6, "grow", 0.25, "rate", -120.0, "color", OD_WHITE, "alpha", 0.9));
            for (int j = 0; j < 6; j++) {
                double a = (j / 5.0) * Math.PI * 2;
                double r = j == 0 ? 0 : radius * 0.6;
                double gx = spot.x + Math.cos(a) * r;
                double gz = spot.z + Math.sin(a) * r;
                Double gy = Kit.solidBelow(level, new Vec3(gx, spot.y + 1.5, gz), 4);
                g.ray(new Vec3(gx, (gy != null ? gy : spot.y) + 0.1, gz), 0.45, 0.14, 0.35, 0.3, 0.9);
            }
            for (int j = 0; j < 8; j++) {
                SkillFx.fx(level, "rpg:arrow_spark", V.up(spot, 0.4 + Kit.rand()), Opt.of("dir", V.UP, "speed", 2 + Kit.rand() * 3, "size", 0.16, "life", 0.8,
                        "color", OD_GOLD, "alpha", 1.0));
            }
            SkillFx.play(level, "random.explode", spot, 0.9, 1.1);
            SkillFx.play(level, "firework.large_blast", spot, 1.4, 0.8);
            SkillFx.play(level, "item.trident.thunder", spot, 1.6, 0.8);
            Kit.shakeNear(level, spot, 14, 0.35, 0.4);
            List<LivingEntity> pushed = new ArrayList<>();
            for (LivingEntity e : struck.values()) {
                double hd2 = Math.hypot(e.getX() - spot.x, e.getZ() - spot.z);
                if (Kit.canHit(sp, e) && hd2 <= radius + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - spot.y) <= 5) pushed.add(e);
            }
            List<LivingEntity> list = Kit.union(Kit.visibleFrom(sp, V.up(spot, 1.2), Kit.around(sp, spot, radius, 3)), pushed);
            long now = Tasks.now();
            for (int j = 0; j < list.size(); j++) {
                LivingEntity e = list.get(j);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now + j / 6), ok, () -> {
                    if (Kit.skillHit(sp, e, w, afterMult) <= 0) return;
                    Vec3 c = Kit.bodyAt0(e, spot);
                    double bs = nearSize(0.8, V.len(V.sub(c, sp.getEyePosition())), 0.3, 1.0);
                    if (bs > 0) SkillFx.burst(level, c, OD_WHITE, bs, 0.22);
                    if (bossLike(e) || e.isRemoved()) return;
                    // 가운데에 선 적 (조준한 적) 은 나에게서 먼 쪽으로
                    Vec3 d = Math.hypot(e.getX() - spot.x, e.getZ() - spot.z) > 0.3 ? V.flat(V.sub(e.position(), spot)) : away;
                    Kit.knockback(e, d.x * 0.5, d.z * 0.5, 0.45);
                });
            }
        });
        return true;
    }
}
