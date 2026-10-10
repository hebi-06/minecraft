package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 아르테미스의 은활 (활) — 은월 관통 · 만월의 사냥 (skills.js v2.21.0 "달빛 · 사냥": 달빛을 모은 거대한 은빛 화살 · 만월을 품은 빛의 은활의 화살비).
 * 수치는 SKILLS.bow. 입력 (웅크리고 = 스킬 1, 길게 = 스킬 2) 은 Transcend. 빛의 은활 그림은 모델 옆모습 (rpg:artemis_bow)
 */
final class Artemis {
    private Artemis() {}

    /** 달빛 (은빛 푸른) */
    static final double[] AR_MOON = {0.78, 0.88, 1.0};
    static final double[] AR_WHITE = {1, 1, 1};
    /** 만월 그림 색: 조금 푸르게 (앞의 흰 활과 구별) */
    static final double[] AR_MOON_TINT = {0.86, 0.9, 1.0};
    /** 빛의 은활 그림에서 시위 · 줌통의 높이 (가운데 기준, 반 크기 = 1 — build.py artemis_bow_base: 320점 그림의 시위 줄 · 줌통) */
    static final double AR_STRING_Y = 0.053;
    static final double AR_GRIP_Y = -0.546;
    /** 활 반폭 · 위아래 끝 (반 크기 = 1) */
    static final double AR_BOW_HALF = 0.8;
    static final double AR_BOW_TOP = 0.83;
    /** 달 그림에서 원판 반지름 (반 크기 = 1 — build.py artemis_moon_frame: 256점 그림의 반지름 88) */
    static final double AR_MOON_DISK = 88 / 128.0;
    /** rpg:shock_ring 이 수명 동안 커지는 배수 (skills.js RING_GROW — build.py 의 grow 1.6, 그림의 고리는 반 크기의 약 0.8) */
    static final double RING_GROW = 2.6;

    static void register() {
        Transcend.register("artemis_pierce", Artemis::pierce);
        Transcend.register("artemis_moon", Artemis::moon);
    }

    // ------------------------------------------------------------ 그림 도구

    /**
     * 거대한 은빛 화살 하나 (rpg:artemis_arrow — 궁니르 창 · 빛의 검과 같은 변수, skills.js spearFx): at = 화살 가운데, dir = 화살촉 방향 (단위),
     * half = 화살 반 길이 (칸), life = 초. o: move · speed = 움직이는 방향 · 초당 칸 (기본 dir · 0), progress = -1 되풀이 (기본) · 2 수명 동안 흩어짐, alpha
     */
    static void arrowFx(ServerLevel level, Vec3 at, Vec3 dir, double half, double life, Opt o) {
        SkillFx.fx(level, "rpg:artemis_arrow", at, Opt.of("dir", dir, "size", half, "life", life, "speed", o.num("speed", 0), "progress", o.num("progress", -1),
                "color", SkillFx.WHITE, "alpha", o.num("alpha", 1), "move", o.vec("move", dir), "seg", 0.0));
    }

    static void arrowFx(ServerLevel level, Vec3 at, Vec3 dir, double half, double life) {
        arrowFx(level, at, dir, half, life, Opt.empty());
    }

    /**
     * 초승달 참격 (rpg:artemis_crescent — 화면을 향해 돎): size = 반 크기, life = 나타난 뒤의 초.
     * o: spin 처음 각 · rate 초당 각 · grow 커지는 몫 (기본 0.3) · delay 초 뒤에 나타남 · alpha
     */
    static void crescentFx(ServerLevel level, Vec3 at, double size, double life, double[] color, Opt o) {
        double delay = o.num("delay", 0);
        SkillFx.fx(level, "rpg:artemis_crescent", at, Opt.of("size", size, "life", life + delay, "spin", o.num("spin", 0), "speed", o.num("rate", 0),
                "grow", o.num("grow", 0.3), "delay", delay, "color", color, "alpha", o.num("alpha", 1)));
    }

    /** 빛의 은활 (rpg:artemis_bow — 보는 사람을 향해 섬, 아래를 겨눔): at = 가운데, size = 반 크기 (칸), life 초 — 마지막 fade 초 동안 흩어짐 */
    static void bowFx(ServerLevel level, Vec3 at, double size, double life, double fade) {
        SkillFx.fx(level, "rpg:artemis_bow", at, Opt.of("size", size, "life", life, "spin", Math.floor(Kit.rand() * 4), "color", SkillFx.WHITE, "alpha", 1.0,
                "fade", fade));
    }

    /** 만월 (rpg:artemis_moon — 보는 사람을 향함): at = 가운데, size = 반 크기 (칸), 처음 wax 초 동안 차오름, 마지막 fade 초 동안 번쩍인 뒤 흩어짐 */
    static void moonFx(ServerLevel level, Vec3 at, double size, double life, double wax, double fade) {
        SkillFx.fx(level, "rpg:artemis_moon", at, Opt.of("size", size, "life", life, "color", AR_MOON_TINT, "alpha", 1.0, "wax", wax, "fade", fade));
    }

    /** a → b 빛줄기에서 눈 near 칸 안의 부분은 빼고 그림 (1인칭 화면을 덮지 않게 — skills.js beamAway) */
    static void beamAway(ServerLevel level, Vec3 eye, Vec3 a, Vec3 b, double[] color, Opt o, double near) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        Vec3 u = V.unit(d);
        double t0 = 0;
        double t1 = len;
        // 선에서 눈까지 near 칸 안인 구간을 잘라냄 (앞쪽이나 뒤쪽 한쪽만 남김)
        Vec3 we = V.sub(eye, a);
        double tc = V.dot(we, u);
        double off2 = V.dot(we, we) - tc * tc;
        if (off2 < near * near) {
            double hw = Math.sqrt(near * near - off2);
            if (tc - hw <= 0) t0 = Math.max(t0, tc + hw);
            else t1 = Math.min(t1, tc - hw);
        }
        if (t1 - t0 > 0.3) SkillFx.beam(level, V.add(a, u, t0), V.add(a, u, t1), color, o);
    }

    static void beamAway(ServerLevel level, Vec3 eye, Vec3 a, Vec3 b, double[] color, Opt o) {
        beamAway(level, eye, a, b, color, o, 1.6);
    }

    /** 달빛 화살이 지나가며 꿰뚫은 자리: 은빛 X 번쩍임 + 불꽃 (눈 가까이는 작게 — 1.5칸 안이면 불꽃만). light = 번쩍임 하나만 (한 틱에 여럿 맞을 때) */
    static void moonHitFx(ServerLevel level, LivingEntity e, Vec3 eye, Vec3 dir, double[] glow, boolean light) {
        Vec3 c = Kit.bodyAt0(e, eye);
        double de = V.len(V.sub(c, eye));
        if (de < 1.5) {
            // 몸에 붙은 적: X 가 1인칭 화면을 덮지 않게 불꽃만
            if (!light) SkillFx.sparks(level, c, AR_MOON, 3, 4);
            return;
        }
        double near = Math.min(1, Math.max(0.45, (de - 0.8) / 2.5));
        SkillFx.fx(level, "rpg:slash_x", c, Opt.of("dir", dir, "size", 1.0 * near, "life", 0.25, "color", glow, "alpha", 1.0));
        if (light) return;
        SkillFx.fx(level, "rpg:slash_x", V.add(c, dir, -0.04), Opt.of("dir", dir, "size", 0.7 * near, "life", 0.2, "color", AR_WHITE, "alpha", 1.0));
        SkillFx.sparks(level, c, AR_MOON, 4, 5);
    }

    // ------------------------------------------------------------ 조준 · 대상

    /** 활을 든 손 (화살이 나오는 곳 — 평타 화살과 같은 arrowPath 의 시작) */
    static Vec3 handFor(ServerPlayer sp, Vec3 dir) {
        return RpgCombat.arrowPath(sp.getEyePosition(), dir, 3).from();
    }

    /**
     * 조준 보정으로 고른 적 쪽 (은월 관통): 가로는 눈에서 그 몸 쪽, 세로는 시선의 기울기 그대로 — 그 몸 높이에서 0.6칸보다 벗어나면 0.6칸까지만 기울임.
     * 몸 가운데로 곧장 겨누면 눈보다 낮은 몸 쪽으로 내려가 거대한 화살이 뒤의 땅에 일찍 박힘 (평지에서 앞을 보고 쏘면 수평으로 24칸 — 줄 위의 적은 폭 안)
     */
    static Vec3 assistDir(Vec3 eye, Vec3 view, Vec3 body) {
        Vec3 q = V.sub(body, eye);
        double h = Math.hypot(q.x, q.z);
        if (h < 0.3) return V.unit(q);
        double vh = Math.hypot(view.x, view.z);
        double yView = vh > 1e-3 ? (view.y / vh) * h : q.y; // 시선 기울기로 그 몸까지 갔을 때의 높이 (눈 기준)
        return V.unit(new Vec3(q.x, Math.max(q.y - 0.6, Math.min(q.y + 0.6, yView)), q.z));
    }

    /** 눈에서 길 (dir 로 reach 칸, 폭 width) 위의 적 — 벽에 닿았으면 벽 · 판 너머의 적은 빼기 (판에 붙어 선 얇은 몹은 시선 검사의 여유 0.35칸으로 보일 수 있음) */
    private static List<Kit.LineHit> pathHits(ServerPlayer sp, Vec3 eye, Vec3 dir, double reach, double width, boolean wall, double wallD) {
        List<Kit.LineHit> out = new ArrayList<>();
        for (Kit.LineHit h : Kit.lineHits(sp, eye, dir, reach, width)) if (!wall || h.t() <= wallD) out.add(h);
        return out;
    }

    /** 몸에서 길 (eye 에서 dir 로 0 ~ reach 칸) 에 가장 가까운 점까지의 거리 (키 큰 보스는 몸 가운데가 길보다 한참 위) */
    private static double toPath(LivingEntity e, Vec3 eye, Vec3 dir, double reach, Vec3 end) {
        Vec3 b = !e.isRemoved() ? Kit.bodyToward(e, eye, dir) : end;
        Vec3 q = V.sub(b, eye);
        double t = Math.max(0, Math.min(reach, V.dot(q, dir)));
        return V.len(V.sub(q, V.scale(dir, t)));
    }

    /** 보스 · 보스 소품 (밀기가 듣지 않음 — 보스 기믹을 깨지 않게, skills.js bossLike) */
    private static boolean bossLike(LivingEntity e) {
        return Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop");
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
        if (r.target() != null && !r.target().isRemoved() && Kit.canHit(sp, r.target())) q = r.target().position();
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
        double fy = hit.point().y - hit.pos().getY(); // 블록 안에서 맞은 높이 (반 블록 0.5)
        return new Vec3(q.x, hit.pos().getY() + (fy > 0.05 && fy <= 1 ? fy : 1), q.z);
    }

    // ------------------------------------------------------------ 스킬 1 은월 관통

    /**
     * 은월 관통: charge 틱 동안 활에 달빛이 모임 (손 앞에서 차오르는 초승달 · 둘레에서 모여드는 빛 알갱이) → 그때 조준한 쪽 (시선 — 시선에 몹이 겹치지 않으면
     * 조준 보정으로 고른 3칸 밖의 몹 쪽: 가로는 그 몸 쪽, 기울기는 시선 (assistDir). 고른 줄이 그 몸에 닿기 전에 막히면 — 낮은 담 너머 — 그 몸 쪽으로 곧게) 으로
     * 거대한 은빛 화살: 눈에서 range 칸 (벽 앞까지) 을 틱당 speed 칸으로 날며 폭 width 칸의 적을 꿰뚫음 (mult — 화살촉이 지나가는 틱, 눈에서 가려진 적 · 벽 너머의
     * 적은 아님). 판정 (벽 · 꿰뚫는 줄 · 가려짐) 은 모두 눈에서 — 문 · 유리판에 붙어 서도 그 너머를 맞히지 않음, 그림은 손에서 같은 끝으로.
     * 지나간 길에 달빛 줄기가 남음 → 화살이 끝에 닿고 delay 틱 뒤 길이 다시 빛나며 초승달 참격으로 갈라짐 (afterMult — 그때 길 위의 적 + 꿰뚫린 적은 밀려났어도
     * 길에서 width + 2.5칸 안이면 (몸에서 길에 가장 가까운 점으로 — 키 큰 보스), 다섯씩 한 틱). 벽에 닿으면 화살이 벽에 박혔다가 갈라질 때 흩어짐.
     * 모으는 동안 활을 바꾸거나 침묵이면 쏘지 않음 (달빛이 흩어짐 — 재사용 대기는 그대로). 쓰러지거나 차원을 옮기면 남은 것은 없음. 적이 없어도 쏨.
     * 화살 그림은 손 앞 2.4칸부터 (1인칭 화면을 덮지 않게 — 벽이 가까우면 그림 없이 빛만, 눈 가까이의 연출은 작게 · 빼고: 몸에 붙은 적은 X · 초승달 없이 불꽃만)
     */
    static boolean pierce(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[] glow = Kit.colors(w)[0];
        int charge = (int) k.num("charge");
        // 모음: 손 앞에서 초승달이 차오름 (커지며 돎) + 둘레에서 빛 알갱이가 손으로 모여듦
        SkillFx.play(level, "crossbow.loading.start", sp.position(), 1.0, 0.8);
        SkillFx.play(level, "resonate.amethyst_block", sp.position(), 0.9, 1.2);
        for (int t = 0; t < charge; t++) {
            final int tt = t;
            Runnable draw = () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 view = sp.getViewVector(1F);
                Vec3 hand = handFor(sp, view);
                double g = (tt + 1) / (double) charge;
                crescentFx(level, V.add(hand, view, 0.9), 0.14 + 0.22 * g, 0.1, tt % 2 == 1 ? AR_WHITE : AR_MOON,
                        Opt.of("spin", -tt * 25.0, "alpha", 0.55 + 0.45 * g, "grow", 0.0));
                Vec3 side = V.rightOf(Kit.facingOf(sp));
                Vec3 eyeNow = sp.getEyePosition();
                for (int j = 0; j < 3; j++) {
                    double a = (j / 3.0) * Math.PI * 2 + tt * 0.9;
                    Vec3 off = V.add(V.add(V.up(hand, Math.sin(a) * 1.1), side, Math.cos(a) * 1.1), view, 0.4);
                    if (V.len(V.sub(off, eyeNow)) < 1.0) continue; // 바로 아래 · 위를 볼 때 눈앞에서 나오는 알갱이는 빼기
                    Vec3 d = V.sub(hand, off);
                    SkillFx.fx(level, "rpg:arrow_spark", off, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.2, "size", 0.07, "life", 0.2, "color", AR_MOON,
                            "alpha", 0.9));
                }
                if (tt == charge - 3) SkillFx.play(level, "crossbow.loading.middle", hand, 1.0, 1.1);
            };
            if (t == 0) draw.run();
            else Tasks.later(t, draw);
        }
        Tasks.later(charge, () -> {
            if (!ok.getAsBoolean()) return;
            if (!Kit.holding(sp, w) || Status.isSilenced(sp)) {
                // 활을 바꿨거나 침묵: 모은 달빛이 흩어짐 (쏘지 않음)
                Vec3 h = handFor(sp, sp.getViewVector(1F));
                SkillFx.sparks(level, h, AR_MOON, 6, 3);
                SkillFx.play(level, "random.fizz", h, 0.5, 1.6);
                return;
            }
            shoot(sp, w, k, level, ok, glow);
        });
        return true;
    }

    /** 은월 관통의 쏨 (다 모은 순간): 거대한 화살 · 꿰뚫음 → 끝에서 박힘 · 흩어짐 → delay 틱 뒤 갈라짐 */
    private static void shoot(ServerPlayer sp, Weapon w, Obj k, ServerLevel level, BooleanSupplier ok, double[] glow) {
        double range = k.num("range");
        double speed = k.num("speed");
        double width = k.num("width");
        double half = k.num("half");
        int delay = (int) k.num("delay");
        double mult = k.num("mult");
        double afterMult = k.num("afterMult");
        Vec3 eye = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        Vec3 hand = handFor(sp, view);
        // 조준: 시선에 겹친 적은 시선 그대로 (가까운 적의 몸 쪽으로 꺾여 땅에 박히거나 옆으로 새지 않게), 조준 보정으로 고른 적 (3칸 밖) 은 그 몸 쪽으로
        // (assistDir — 기울기는 시선). 자바 Aim 은 dir 이 늘 있어서 (JS 는 조준 보정으로 고른 적에만) 보정 없이 겨눈 적이 없을 때 고른 적을 조준 보정으로 봄
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, Kit.BOW.numOr("aimAssist", 0));
        boolean assisted = r.target() != null && RpgCombat.aimTarget(sp, range, 0).target() == null;
        Vec3 dir0 = view;
        Vec3 aimBody = null; // 조준 보정으로 고른 몸 (눈에서 보이는 점 — assist 가 확인)
        if (assisted && !r.target().isRemoved() && Kit.canHit(sp, r.target())) {
            aimBody = Kit.bodyToward(r.target(), eye, view);
            if (r.dist() >= 3) dir0 = assistDir(eye, view, aimBody);
        }
        // 고른 줄이 그 몸에 닿기 전에 막히면 (낮은 담 · 턱 너머의 적 — 시선이 담에 닿아 조준 보정이 고름) 그 몸 쪽으로 곧게
        if (aimBody != null) {
            Vec3 toBody = V.sub(aimBody, eye);
            if (Kit.rayDist(level, eye, dir0, range) < V.len(toBody) - 0.5) dir0 = V.unit(toBody);
        }
        final Vec3 dir = dir0;
        // 판정 (벽까지의 거리 · 꿰뚫는 줄 · 가려짐) 은 눈에서 — 손은 눈 앞 0.6칸이라 문 · 유리판 · 울타리에 붙어 서면 그 너머에 있음. 그림은 손에서 같은 끝으로
        double wallD = Kit.rayDist(level, eye, dir, range); // 눈에서 막는 블록까지
        double reach = Math.max(0.5, wallD - 0.3); // 화살촉이 닿는 거리 (벽 앞)
        boolean wall = reach < range - 0.3 - 1e-6;
        int outT = (int) Math.max(1, Math.ceil(reach / speed));
        Vec3 end = V.add(eye, dir, reach);
        Vec3 vd = V.sub(end, hand);
        double vreach = V.len(vd); // 그림의 길이 (손 → 끝)
        Vec3 vdir = vreach > 1e-3 ? V.unit(vd) : dir;
        long now = Tasks.now();
        double tip0 = 2.4 + 2 * half; // 화살 그림의 처음 촉 자리 (손에서 — 깃 끝이 손 앞 2.4칸)
        boolean shown = vreach > tip0 + 0.5; // 화살 그림을 그릴 만큼 날아가는지
        // 쏨: 손 앞의 작은 번쩍임 (눈 1.6칸 안이면 없음 — 문 · 벽에 붙어 쏠 때) + 거대한 화살 (끝에 닿는 틱에 촉이 끝에) + 촉의 빛 + 지나가며 켜지는 달빛 줄기
        // (갈라질 때까지 옅어지며 남음, 눈 1.6칸 안은 빼고)
        Vec3 flash = V.add(hand, vdir, Math.min(1.4, vreach));
        if (V.len(V.sub(flash, eye)) >= 1.6) SkillFx.burst(level, flash, glow, 0.3, 0.18);
        if (shown) {
            double vSpeed = (vreach - tip0) / (outT / 20.0);
            Vec3 tip = V.add(hand, vdir, tip0);
            arrowFx(level, V.add(hand, vdir, tip0 - half), vdir, half, outT / 20.0, Opt.of("move", vdir, "speed", vSpeed));
            SkillFx.fx(level, "rpg:arrow_spark", tip, Opt.of("dir", vdir, "speed", vSpeed, "size", 1.0, "grow", 1.0, "life", outT / 20.0 + 0.05, "color", glow,
                    "alpha", 0.7));
            SkillFx.fx(level, "rpg:arrow_spark", tip, Opt.of("dir", vdir, "speed", vSpeed, "size", 0.45, "grow", 1.0, "life", outT / 20.0 + 0.05, "color", AR_WHITE,
                    "alpha", 1.0));
        }
        // 화살촉이 지나가는 자리마다 달빛 고리 (화면을 향함) + 두 틱마다 도는 작은 초승달 — 화살 그림은 화살 방향을 따라 누워서 쏜 사람 (1인칭 · 뒤) 에게는
        // 거의 보이지 않으므로, 뒤에서는 멀어지는 고리로 보임 (눈 2.5칸 안은 빼고)
        for (int i = 0; i < outT; i++) {
            final int ii = i;
            final double s = Math.min(vreach, (i + 1) * speed);
            Runnable passFx = () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 at = V.add(hand, vdir, s);
                if (V.len(V.sub(at, sp.getEyePosition())) < 2.5) return;
                SkillFx.ring(level, at, vdir, glow, 0.55, 0.3, 0);
                if (ii % 2 == 0) crescentFx(level, at, 0.5, 0.12, AR_WHITE, Opt.of("spin", ii * 70.0, "rate", 900.0, "grow", 0.0));
            };
            if (i == 0) passFx.run();
            else Tasks.later(i, passFx);
        }
        double hold = (outT + delay) / 20.0 + 0.1;
        Vec3 from = V.add(hand, vdir, 1.2);
        beamAway(level, eye, from, end, glow, Opt.of("half", 0.7, "thick", 0.3, "life", hold, "sweep", outT / 20.0, "gap", 1.4, "alpha", 0.8));
        beamAway(level, eye, from, end, AR_WHITE, Opt.of("half", 0.6, "thick", 0.09, "life", hold * 0.6, "sweep", outT / 20.0, "gap", 3.0, "alpha", 1.0));
        SkillFx.play(level, "crossbow.shoot", hand, 1.2, 0.7);
        SkillFx.play(level, "random.bow", hand, 1.4, 0.5);
        // 꿰뚫음: 화살촉이 지나가는 틱 (가까운 적부터). 한 틱에 넷째부터는 번쩍임만 (쏜 틱에 손 앞 적이 여럿일 때)
        Map<UUID, LivingEntity> pierced = new LinkedHashMap<>(); // 꿰뚫린 적 (밀려나도 갈라질 때 맞음)
        Map<Long, Integer> perTick = new HashMap<>(); // 틱 → 그 틱에 그린 꿰뚫음 연출 수
        for (Kit.LineHit h : pathHits(sp, eye, dir, reach, width, wall, wallD)) {
            LivingEntity e = h.e();
            pierced.put(e.getUUID(), e);
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now + (long) Math.max(0, Math.ceil(h.t() / speed) - 1)), ok, () -> {
                if (Kit.skillHit(sp, e, w, mult) <= 0) return;
                int n = perTick.merge(Tasks.now(), 1, Integer::sum);
                moonHitFx(level, e, sp.getEyePosition(), dir, glow, n > 3);
                SkillFx.play(level, "random.bowhit", Kit.bodyAt0(e, end), 1.0, 0.7);
            });
        }
        // 끝: 벽이면 화살이 박힘 (갈라질 때까지) + 번쩍임 · 불꽃 (눈 가까이는 작게 — 1.6칸 안이면 없음), 사거리 끝이면 은빛 알갱이로 흩어짐
        Vec3 stuck = V.add(end, vdir, 0.35 - half); // 촉이 벽에 0.35칸 박힌 화살의 가운데
        Tasks.later(outT, () -> {
            if (!ok.getAsBoolean()) return;
            if (wall) {
                if (shown) arrowFx(level, stuck, vdir, half, delay / 20.0 + 0.05);
                double de = V.len(V.sub(end, sp.getEyePosition()));
                if (de >= 1.6) {
                    double sz = Math.min(1, Math.max(0.3, (de - 0.8) / 2.5));
                    SkillFx.burst(level, end, AR_WHITE, 0.9 * sz, 0.22);
                    SkillFx.sparks(level, end, glow, de >= 2.5 ? 8 : 4, 5 * sz);
                }
                SkillFx.play(level, "random.bowhit", end, 1.4, 0.5);
            } else if (shown) arrowFx(level, V.add(end, vdir, -half), vdir, half, 0.35, Opt.of("progress", 2.0));
        });
        // 갈라짐: 길이 다시 빛나고 2칸마다 초승달 참격이 앞쪽으로 차례로 (0.15초 — 눈 2.2칸 안은 빼고, 가까우면 작게) + 맞는 적마다 엇갈린 초승달
        Tasks.later(outT + delay, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 eyeNow = sp.getEyePosition();
            beamAway(level, eyeNow, from, end, AR_WHITE, Opt.of("half", 0.6, "thick", 0.16, "life", 0.25, "gap", 2.0, "alpha", 1.0));
            int j = 0;
            for (double s = 1.5; s <= vreach + 1e-6; s += 2.0, j++) {
                Vec3 at = V.add(hand, vdir, s);
                double de = V.len(V.sub(at, eyeNow));
                if (de < 2.2) continue;
                boolean odd = j % 2 == 1;
                crescentFx(level, at, Math.min(1.3, 0.35 + de * 0.12), 0.35, odd ? AR_WHITE : AR_MOON, Opt.of("spin", odd ? 40.0 : -40.0, "rate",
                        odd ? 300.0 : -300.0, "grow", 0.5, "delay", (s / vreach) * 0.15));
            }
            if (wall && shown) arrowFx(level, stuck, vdir, half, 0.3, Opt.of("progress", 2.0));
            List<LivingEntity> onPath = new ArrayList<>();
            for (Kit.LineHit h : pathHits(sp, eye, dir, reach, width, wall, wallD)) onPath.add(h.e());
            List<LivingEntity> pushed = new ArrayList<>();
            for (LivingEntity e : pierced.values()) {
                if (Kit.canHit(sp, e) && toPath(e, eye, dir, reach, end) <= width + 2.5 + Kit.bodyPad(e)) pushed.add(e);
            }
            List<LivingEntity> list = Kit.union(onPath, pushed);
            long t0 = Tasks.now();
            for (int idx = 0; idx < list.size(); idx++) {
                LivingEntity e = list.get(idx);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0 + idx / 5), ok, () -> {
                    if (Kit.skillHit(sp, e, w, afterMult) <= 0) return;
                    Vec3 c = Kit.bodyAt0(e, end);
                    double dc = V.len(V.sub(c, sp.getEyePosition()));
                    if (dc >= 1.5) {
                        // 몸에 붙은 적은 초승달 없이 (1인칭 화면을 덮지 않게)
                        double sz = Math.min(1, Math.max(0.45, (dc - 0.8) / 2.5));
                        crescentFx(level, c, 0.95 * sz, 0.3, AR_WHITE, Opt.of("spin", 35.0, "rate", 520.0, "grow", 0.4));
                        crescentFx(level, c, 0.85 * sz, 0.3, glow, Opt.of("spin", 215.0, "rate", 520.0, "grow", 0.4));
                    }
                    SkillFx.sparks(level, c, AR_MOON, list.size() > 5 ? 2 : 4, 4);
                });
            }
            SkillFx.play(level, "chime.amethyst_block", end, 1.6, 1.3);
            SkillFx.play(level, "break.amethyst_cluster", V.add(hand, vdir, Math.min(vreach, 5)), 1.3, 0.9);
            SkillFx.play(level, "firework.twinkle", V.add(hand, vdir, Math.min(vreach, 6)), 1.4, 1.1);
            if (!list.isEmpty()) SkillFx.shake(sp, 0.15, 0.2);
        });
    }

    // ------------------------------------------------------------ 스킬 2 만월의 사냥

    /**
     * 만월의 사냥: 조준한 곳 (range 칸 — 가로 min 칸보다 가까우면 min 칸 앞, 그 아래 땅) 하늘에 만월을 품은 빛의 은활 (모델 모양 — 아래를 겨눔, 처음부터 시위에
     * 은빛 화살, 만월은 활 뒤에서 활 몸에 안긴 듯 조금 위 — 그쪽이 벽이면 벽 앞까지) + 땅의 은빛 룬 + 달빛 기둥. 활의 크기 · 높이는 그곳까지의 가로 거리에 맞춤
     * (위끝이 눈높이에서 26° 위까지), 굴 · 지붕 · 나뭇잎 아래면 트인 높이에 맞게 작고 낮게 → rise 틱부터 gap 틱마다 화살비 waves 번 (활에서 반지름 radius 칸 안의
     * 적마다 한 발 — 3틱 뒤 꽂힘 mult (한 틱에 열둘씩), 활에서 보이는 적만, 빈 땅에도 넷) → final 8틱 전에 시위에 거대한 화살을 메겨 당겼다가 4틱 전에 쏨 →
     * final 틱에 가운데에 내리꽂혀 달빛 폭발 (afterMult, 밀쳐 띄움 — 보스는 밀지 않음, 가운데에 선 적은 나에게서 먼 쪽으로, 화살비에 맞은 적은 밀려났어도
     * radius + 2.5칸 안이면) · 만월이 번쩍인 뒤 흩어지고 활도 흩어짐. 땅을 조준하지 않았거나 (허공) · 그 땅이 눈에서 보이지 않거나 (천장 · 지붕 위) ·
     * min 칸 앞으로 밀 수 없으면 (벽 · 나무) 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 화살 · 폭발은 없음 (이미 그린 달 · 활은 그대로 흩어짐)
     */
    static boolean moon(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 spot = fallSpot(sp, k);
        Vec3 eye = sp.getEyePosition();
        String name = k.str("name");
        // 땅이 없거나 (허공) · 그 땅이 눈에서 보이지 않으면 (천장 · 지붕 위를 조준 — 광선이 블록 안에서 시작) 쓰지 않음
        if (spot == null || (!Kit.lineOfSight(sp, eye, V.up(spot, 1.2)) && !Kit.lineOfSight(sp, eye, V.up(spot, 2.0)))) {
            Notify.warn(sp, "§c" + name + ": §7땅을 조준해야 합니다 (" + k.text("range") + "칸 안)");
            return false;
        }
        Vec3 pl = sp.position();
        double hd = Math.hypot(spot.x - pl.x, spot.z - pl.z);
        // 가로 min 칸보다 가까운데 밀어낼 수 없었으면 (그 너머가 벽 · 나무) 쓰지 않음 — 거대한 활 · 폭발이 1인칭 화면을 덮지 않게
        if (hd < k.num("min") - 0.5) {
            Notify.warn(sp, "§c" + name + ": §7" + k.text("min") + "칸 밖의 땅을 조준해야 합니다");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = Kit.colors(w);
        double[] glow = cs[0];
        double[] core = cs[1];
        int fin = (int) k.num("final");
        int rise = (int) k.num("rise");
        int gap = (int) k.num("gap");
        int waves = (int) k.num("waves");
        double radius = k.num("radius");
        double mult = k.num("mult");
        double afterMult = k.num("afterMult");
        Vec3 away = hd > 0.5 ? V.flat(V.sub(spot, pl)) : Kit.facingOf(sp);
        // 크기 · 높이: 트인 높이 (나뭇잎도 막힘 — 나무 밑이면 잎 아래에) 안에서 (아래 끝 땅 위 0.6칸 ~ 만월 위끝 천장 아래 0.2칸 = 1.68 × 반 크기 + 0.8 —
        // 낮은 굴이면 작게, 그래도 모자라면 천장 쪽을 지킴). 가까우면 작고 낮게: 활 위끝이 눈높이에서 26° 위 (tan 26° = 0.49) 를 넘지 않게
        double room = Kit.skyRoom(level, V.up(spot, 0.2), 12, false) + 0.2;
        double s = Math.max(0.6, Math.min(2.6, Math.min((room - 0.8) / (AR_BOW_TOP * 2 + 0.02), Math.max(1.6, hd * 0.3)))); // 활 반 크기
        double bowH = Math.min(room - (AR_BOW_TOP + 0.02) * s - 0.2, Math.max(AR_BOW_TOP * s + 0.6, Math.min(6, 1.62 + hd * 0.49 - AR_BOW_TOP * s)));
        Vec3 bowC = V.up(spot, bowH);
        Vec3 nock = V.up(bowC, AR_STRING_Y * s); // 시위
        Vec3 rest = V.up(bowC, AR_GRIP_Y * s); // 줌통 (화살이 나가는 곳)
        double moonSize = ((0.75 * AR_BOW_HALF) / AR_MOON_DISK) * s; // 달 원판이 활 반폭의 0.75
        // 만월은 활 뒤 (나에게서 먼 쪽) 0.8칸 · 조금 위 — 활 몸에 안긴 듯. 그쪽이 벽이면 (벽 면 · 벽에 붙은 몹을 조준) 벽 앞 0.3칸까지 (벽 속에 그려지면 보이지 않음)
        double back = Math.max(-0.3, Math.min(0.8, Kit.rayDist(level, V.up(bowC, 0.25 * s), away, 1.1) - 0.3));
        Vec3 moonC = V.add(V.up(bowC, 0.25 * s), away, back);
        // 나타남: 만월 (rise 틱 동안 차오름 → final 에 번쩍 · 흩어짐) + 빛의 은활 (final 2틱 전부터 흩어짐) + 땅의 은빛 룬 + 달빛 기둥 + 소리
        moonFx(level, moonC, moonSize, (fin + 12) / 20.0, rise / 20.0, 0.6);
        bowFx(level, bowC, s, (fin + 8) / 20.0, 0.5);
        // 처음부터 시위에 메긴 은빛 화살 (꼬리가 시위, 촉이 아래 — 아래를 겨눈 활로 보이게) — 거대한 화살을 메길 때 사라짐
        arrowFx(level, V.up(nock, -0.45 * s), V.DOWN, 0.45 * s, (fin - 8) / 20.0);
        SkillFx.rune(level, spot, Opt.of("size", radius, "life", (fin + 6) / 20.0, "color", glow, "alpha", 0.75, "rate", 25.0));
        SkillFx.beam(level, V.up(moonC, -AR_MOON_DISK * moonSize), new Vec3(moonC.x, spot.y + 0.15, moonC.z), AR_MOON, Opt.of("half", 0.9, "thick", 0.9,
                "life", fin / 20.0, "gap", 1.6, "alpha", 0.18)); // 달 바로 아래 땅까지 곧게
        SkillFx.shock(level, spot, glow, radius / RING_GROW, 0.5);
        SkillFx.play(level, "beacon.activate", spot, 1.4, 1.3);
        SkillFx.play(level, "chime.amethyst_block", bowC, 1.2, 0.6);
        SkillFx.play(level, "random.bow", sp.getEyePosition(), 0.7, 0.6);
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>(); // 화살비에 맞은 적 (밀려나도 폭발에 맞음)
        for (int i = 0; i < waves; i++) {
            final int wave = i;
            Tasks.later(rise + i * gap, () -> {
                if (!ok.getAsBoolean()) return;
                List<LivingEntity> targets = Kit.visibleFrom(sp, rest, Kit.around(sp, spot, radius, 3));
                boolean many = targets.size() > 6;
                SkillFx.burst(level, rest, AR_WHITE, 0.3 * s, 0.2);
                SkillFx.play(level, "random.bow", bowC, 1.2, 1.2 + 0.15 * wave);
                // 적마다 한 발: 줌통에서 몸으로 날아가는 빛줄기 → 3틱 뒤 꽂힘 (꽂힌 화살 · 불꽃 — 한 틱에 열둘씩)
                long t0 = Tasks.now();
                for (int j = 0; j < targets.size(); j++) {
                    LivingEntity e = targets.get(j);
                    Vec3 b = Kit.bodyAt0(e, spot);
                    Vec3 d = V.sub(b, rest);
                    double dl = V.len(d);
                    Vec3 u = dl > 1e-3 ? V.unit(d) : V.DOWN;
                    if (dl >= 0.6) {
                        double fly = Math.max(0, dl - 1.2) / 0.15;
                        SkillFx.fx(level, "rpg:arrow_shot", V.add(rest, u, 0.6), Opt.of("dir", u, "speed", fly, "size", 0.6, "thick", 0.22, "life", 0.15,
                                "color", glow, "alpha", 1.0));
                        if (!many) {
                            SkillFx.fx(level, "rpg:arrow_shot", V.add(rest, u, 0.6), Opt.of("dir", u, "speed", fly, "size", 0.55, "thick", 0.08, "life", 0.15,
                                    "color", core, "alpha", 1.0));
                        }
                    }
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0 + 3 + j / 12), ok, () -> {
                        if (Kit.skillHit(sp, e, w, mult) <= 0) return;
                        struck.put(e.getUUID(), e);
                        Vec3 c = Kit.bodyAt0(e, b);
                        arrowFx(level, V.add(c, u, -0.25), u, 0.45, 0.6);
                        SkillFx.sparks(level, c, AR_MOON, many ? 2 : 4, 3);
                        SkillFx.play(level, "random.bowhit", c, 0.8, 1.0);
                    });
                }
                // 빈 땅에도 넷 (화살비) — 꽂힌 화살
                for (int j = 0; j < 4; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = Math.sqrt(Kit.rand()) * radius * 0.9;
                    double gx = spot.x + Math.cos(a) * rr;
                    double gz = spot.z + Math.sin(a) * rr;
                    Double gy = Kit.solidBelow(level, new Vec3(gx, spot.y + 1.5, gz), 4);
                    Vec3 tgt = new Vec3(gx, gy != null ? gy : spot.y, gz);
                    Vec3 d = V.sub(tgt, rest);
                    Vec3 u = V.unit(d);
                    SkillFx.fx(level, "rpg:arrow_shot", V.add(rest, u, 0.6), Opt.of("dir", u, "speed", Math.max(0, V.len(d) - 1.2) / 0.15, "size", 0.5,
                            "thick", 0.18, "life", 0.15, "color", j % 2 == 1 ? glow : core, "alpha", 0.9));
                    Tasks.later(3, () -> {
                        if (ok.getAsBoolean()) arrowFx(level, V.add(tgt, u, -0.3), u, 0.4, 0.8);
                    });
                }
            });
        }
        // 거대한 화살: 시위에 메김 (꼬리가 시위, 촉이 아래) → 4틱 동안 당김 (위로) → 쏨: 4틱에 땅으로 (굵은 달빛 줄기)
        double gHalf = 0.62 * s;
        double pull = 0.25 * s;
        Tasks.later(fin - 8, () -> {
            if (!ok.getAsBoolean()) return;
            arrowFx(level, V.up(nock, -gHalf), V.DOWN, gHalf, 0.2, Opt.of("move", V.UP, "speed", pull / 0.2));
            crescentFx(level, rest, 0.35 * s, 0.4, AR_MOON, Opt.of("spin", 0.0, "rate", 400.0, "grow", 0.6));
            SkillFx.play(level, "crossbow.loading.middle", bowC, 1.4, 0.8);
            SkillFx.play(level, "beacon.power", bowC, 1.2, 1.2);
        });
        Tasks.later(fin - 4, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 c = V.up(nock, -gHalf + pull);
            double drop = c.y - (spot.y + gHalf * 0.7); // 촉의 30% 가 땅에 박힘
            arrowFx(level, c, V.DOWN, gHalf, 0.2, Opt.of("move", V.DOWN, "speed", Math.max(0, drop) / 0.2));
            SkillFx.beam(level, rest, V.up(spot, 0.2), AR_WHITE, Opt.of("half", 0.6, "thick", 0.35, "life", 0.3, "sweep", 0.2, "gap", 1.2, "alpha", 0.9));
            SkillFx.beam(level, rest, V.up(spot, 0.2), glow, Opt.of("half", 0.7, "thick", 0.8, "life", 0.35, "sweep", 0.2, "gap", 1.6, "alpha", 0.6));
            SkillFx.play(level, "crossbow.shoot", bowC, 1.6, 0.7);
            SkillFx.play(level, "random.bow", bowC, 1.6, 0.5);
        });
        // 꽂힘: 박힌 거대한 화살 (0.6초 → 흩어짐) + 흰 · 은빛 폭발 + 바깥으로 도는 초승달 여덟 + 고리 둘 + 룬 번쩍 + 솟는 달빛 + 흔들림 → 폭발 (적마다, 여섯씩 한 틱)
        Tasks.later(fin, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 center = V.up(spot, gHalf * 0.7);
            arrowFx(level, center, V.DOWN, gHalf, 0.6);
            Tasks.later(12, () -> arrowFx(level, center, V.DOWN, gHalf, 0.4, Opt.of("progress", 2.0)));
            SkillFx.burst(level, V.up(spot, 1.0), AR_WHITE, 3.0, 0.35);
            SkillFx.burst(level, V.up(spot, 1.6), glow, 4.2, 0.45);
            SkillFx.shock(level, spot, glow, radius / 1.8, 0.5);
            SkillFx.shock(level, spot, AR_WHITE, radius / 3.6, 0.4);
            SkillFx.rune(level, spot, Opt.of("size", radius, "life", 0.6, "grow", 0.25, "rate", -120.0, "color", AR_WHITE, "alpha", 0.9));
            for (int j = 0; j < 8; j++) {
                double a = (j / 8.0) * Math.PI * 2;
                Vec3 at = new Vec3(spot.x + Math.cos(a) * radius * 0.55, spot.y + 1.0, spot.z + Math.sin(a) * radius * 0.55);
                boolean odd = j % 2 == 1;
                crescentFx(level, at, 1.2, 0.4, odd ? AR_WHITE : AR_MOON, Opt.of("spin", j * 45.0, "rate", odd ? 420.0 : -420.0, "grow", 0.8, "delay",
                        0.03 * (j % 4)));
            }
            for (int j = 0; j < 8; j++) {
                SkillFx.fx(level, "rpg:arrow_spark", V.up(spot, 0.4 + Kit.rand()), Opt.of("dir", V.UP, "speed", 2 + Kit.rand() * 3, "size", 0.16, "life", 0.8,
                        "color", AR_MOON, "alpha", 1.0));
            }
            SkillFx.play(level, "random.explode", spot, 0.9, 1.1);
            SkillFx.play(level, "firework.large_blast", spot, 1.4, 0.8);
            SkillFx.play(level, "chime.amethyst_block", spot, 1.6, 0.6);
            SkillFx.play(level, "firework.twinkle", spot, 1.4, 0.9);
            Kit.shakeNear(level, spot, 14, 0.35, 0.4);
            List<LivingEntity> pushed = new ArrayList<>();
            for (LivingEntity e : struck.values()) {
                double far = Math.hypot(e.getX() - spot.x, e.getZ() - spot.z);
                if (Kit.canHit(sp, e) && far <= radius + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - spot.y) <= 5) pushed.add(e);
            }
            List<LivingEntity> list = Kit.union(Kit.visibleFrom(sp, V.up(spot, 1.2), Kit.around(sp, spot, radius, 3)), pushed);
            long t0 = Tasks.now();
            for (int j = 0; j < list.size(); j++) {
                LivingEntity e = list.get(j);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0 + j / 6), ok, () -> {
                    if (Kit.skillHit(sp, e, w, afterMult) <= 0) return;
                    SkillFx.burst(level, Kit.bodyAt0(e, spot), AR_WHITE, 0.8, 0.22);
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
