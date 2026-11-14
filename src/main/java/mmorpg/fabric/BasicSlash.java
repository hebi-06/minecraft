package mmorpg.fabric;

import java.util.List;
import mmorpg.core.Motions;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.fabric.motion.MotionPoses;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-14 기본 평타 참격 판 (사용자 "근접 애들은 참격 나가게" · "참격 좀 큼직막하게 만들어줘" · "광역 애들은 광역 답게 넓게 참격 좀 두껍게 잘 보이게" ·
 * "날가롭게 얅은데 참격 앞을 보고 있으면 뒤에 참격 잔상이 두껍게 사진 같은 요런 느낌, 그렇다고 도트로 하지말고"): 궤적 평타 동작 (rules_more TRAIL.basic) 마다
 * 무기 색 참격 판 (fxmodel slash_basic — tools/basic_slash_art.py: 가늘고 날카로운 머리 · 뒤로 두꺼워지며 짙어지는 잔상) 을 Fx3d 로 띄움 — 모드 클라이언트는
 * 모델, 모드 없는 클라이언트는 글자 표시 (vfx.PBridge · 글자 256픽셀). 전에는 궤적 평타가 모드 클라이언트의 칼 궤적 (BladeTrails) 뿐이라 모드 없는 클라이언트에서
 * 참격이 보이지 않았음.
 * <ul>
 *   <li>up (가로 베기 · 반대 가로 · 창 휘두르기): 몸 둘레에 누운 판 — 반지름 = 무기 종류 · 등급의 궤적 거리 (MOTION.area.reach) × radius (모든 사람에게 위에서 보임)</li>
 *   <li>front (세로 베기 · 내려찍기 · 올려 베기 · X자): 쓴 사람 쪽을 보는 선 판 — 휘두름 시작 쪽 start (화면 도: 0 오른쪽 · 90 위), 볼록한 쪽은 그 90° 반시계
 *       (mirror 면 시계), squash = 볼록한 쪽 납작하게. 쓴 사람의 1인칭에는 조금 멀리 · 옅게 따로</li>
 *   <li>때: 동작의 궤적 구간 (window) 이 시작할 때 나타나 휘두름 동안 머리가 나아가고 (판의 움직임 0 ~ sweepShare) 남은 동안 잔상이 거둬짐 —
 *       공속만큼 빠르게 (Motions.playSpeed — 몸 동작과 같음)</li>
 *   <li>광역 무기 (thick — 마검 · 낫 · 전투 망치 · 창): 두꺼운 띠 (slash_basic_w)</li>
 * </ul>
 * 규칙은 rules_more BASIC_SLASH.
 */
public final class BasicSlash {
    private BasicSlash() {}

    /** 시험: 띄운 판 수 · 마지막 모델 · 반지름 (칸) · 삶 (초) · 늦춤 (초) */
    public static int spawned;
    public static String lastModel = "";
    public static double lastRadius;
    public static double lastLife;
    public static double lastDelay;

    private static Obj cfg() {
        return Rules.get().root().objOrNull("BASIC_SLASH");
    }

    /** 이 평타 동작에 참격 판이 있는지 */
    public static boolean has(String motion) {
        Obj c = cfg();
        if (c == null || !c.truthy("enabled") || motion == null) return false;
        Obj ms = c.objOrNull("motions");
        return ms != null && ms.has(motion);
    }

    /**
     * 휘두름 (Slash.swing — 요구 조건을 맞춘 근접 무기의 궤적 평타): 동작의 판들. style = 무기 참격 모습 (SLASH · 평타 강화 모습 — glow 빛깔 · core 날 빛)
     */
    public static void swing(ServerPlayer sp, Weapon w, Obj style, String motion) {
        if (sp == null || w == null || !has(motion)) return;
        MotionPoses.Motion mo = MotionPoses.get(motion);
        if (mo == null) return;
        Obj c = cfg();
        double speed = Motions.playSpeed(RpgCombat.attackSpeed.applyAsDouble(sp));
        int ticks = Motions.playTicks(mo.ticks, speed);
        Double reach = Motions.reach(w.type(), w.gradeIndex());
        double base = reach != null ? reach : c.numOr("defaultReach", 2.4);
        double[] glow = color(style, "glow", new double[] {0.8, 0.9, 1});
        double[] core = style != null && style.has("core") ? color(style, "core", glow) : whiten(glow, c.numOr("coreWhite", 0.72));
        List<Object> thickTypes = c.listOrNull("thick");
        boolean thick = thickTypes != null && thickTypes.contains(w.type());
        List<Obj> specs = c.obj("motions").objsOrNull(motion);
        if (specs == null) return;
        for (Obj s : specs) {
            // 휘두름 구간: 궤적 구간 (window) — 2026-11-14 궤적이 없는 동작 (찌르기 · 밀치기) 은 판의 time [시작, 끝] (동작 길이의 몫)
            float[] tw;
            List<Object> tl = s.listOrNull("time");
            if (tl != null && tl.size() >= 2) tw = new float[] {(float) num(tl, 0), (float) num(tl, 1)};
            else if (mo.trail != null && mo.trail.length > 0) tw = mo.trail[Math.max(0, Math.min(mo.trail.length - 1, (int) s.numOr("window", 0)))];
            else continue;
            // 동작은 다음 틱에 시작 (ClientMotions · VanillaRig 의 start = 지금 + 1)
            double delay = (1 + tw[0] * ticks) / 20.0;
            double sweep = Math.max(0.05, (tw[1] - tw[0]) * ticks / 20.0);
            double life = Math.max(c.numOr("minLife", 0.3), sweep / c.numOr("sweepShare", 0.4));
            // 2026-11-14 reach = 판이 정한 거리 (창 찌르기 줄 4칸 · 방패 밀치기 2.6칸 — 궤적 거리 표가 아님)
            double radius = Math.max(c.numOr("minRadius", 1.4), (s.has("reach") ? s.num("reach") : base) * s.numOr("radius", 1));
            spawn(sp, s, thick, radius, delay, life, glow, core);
            if (s.has("impact")) impact(sp, s, radius, delay + sweep, glow, core);
        }
    }

    private static void spawn(ServerPlayer sp, Obj s, boolean thick, double radius, double delay, double life, double[] glow, double[] core) {
        Obj c = cfg();
        ServerLevel level = sp.level();
        boolean mirror = s.truthy("mirror");
        String model = s.has("model") ? s.str("model") : "slash_basic" + (thick ? "_w" : "") + (mirror ? "_l" : "");
        double yaw = Math.toRadians(sp.getYRot());
        Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 r = V.cross(f, V.UP); // 오른쪽
        List<Object> at = s.listOrNull("at");
        Vec3 feet = sp.position();
        Vec3 p = V.add(V.add(V.add(feet, r, num(at, 0)), V.UP, num(at, 1)), f, num(at, 2));
        Vec3 vel = mmorpg.fabric.rune.RuneKit.bodyVel(sp);
        // 2026-11-14 둘 (둘레 사람 · 3인칭 판 + 쓴 사람 1인칭 판) — spawned 는 판 수
        Opt card = Opt.of("scale", radius, "life", life, "delay", delay, "grow", c.numOr("grow", 0.05), "vel", vel, "anim", "play", "animSpeed", 1.0 / life,
                "fadeIn", 0.0, "fadeOut", life * 0.08, "glowAlpha", 0.0, "color", glow, "glow", core, "alpha", c.numOr("alpha", 0.95), "shine", 0.1);
        int owner = sp.getId();
        String kind = s.strOr("face", "up");
        if ("thrust".equals(kind)) {
            // 2026-11-14 창 찌르기 판 (slash_thrust — +Y = 찌르는 쪽 · 원점 = 손): 보는 쪽 (위아래 ±25° 까지) 으로 뻗고, 판은 그 축 둘레로 보는 사람을 봄 (faceCam).
            // 둘레 사람 · 3인칭 = 손 자리, 쓴 사람 1인칭 = 조금 앞 · 옅게
            Vec3 look = sp.getViewVector(1F);
            double pitch = Math.max(-0.42, Math.min(0.42, look.y));
            Vec3 dir = V.add(V.scale(f, Math.sqrt(1 - pitch * pitch)), V.UP, pitch);
            mmorpg.fabric.Fx3d.spawn(level, model, p, card.with("dir", dir, "faceCam", 1.0, "roll", 0.0, "view", 1.0, "owner", (double) owner));
            mmorpg.fabric.Fx3d.spawn(level, model, V.add(p, dir, c.numOr("fpAhead", 0.6)), card.with("dir", dir, "faceCam", 1.0, "roll", 0.0, "view", 2.0, "owner",
                    (double) owner, "alpha", c.numOr("fpAlpha", 0.7)));
        } else if ("front".equals(kind)) {
            // 쓴 사람 쪽을 보는 선 판: 시작 쪽 start · 볼록한 쪽 (반시계 90° · mirror 면 시계 90°) · 휘두름 쪽 길이 length · 볼록한 쪽 납작하게 squash.
            // 둘레 사람 · 3인칭: 면을 turn 도 왼쪽으로 돌림 (뒤 · 옆 카메라 모두에서 넓은 면) / 쓴 사람 1인칭: 눈을 봄 · 조금 멀리 · 옅게 (화면 가운데를 덜 가림)
            Vec3 stretch = new Vec3(s.numOr("length", 1), s.numOr("squash", 0.45), 1);
            double turn = Math.toRadians(s.numOr("turn", c.numOr("turn", 40)));
            Vec3 f2 = V.add(V.scale(f, Math.cos(turn)), r, Math.sin(turn));
            mmorpg.fabric.Fx3d.spawn(level, model, p, front(card, f2, s, mirror).with("stretch", stretch, "view", 1.0, "owner", (double) owner));
            Vec3 fp = V.add(p, f, c.numOr("fpAhead", 0.6));
            mmorpg.fabric.Fx3d.spawn(level, model, fp, front(card, f, s, mirror).with("stretch", stretch, "view", 2.0, "owner", (double) owner, "alpha",
                    c.numOr("fpAlpha", 0.7)));
        } else {
            // 몸 둘레에 누운 판 (그림 위 = 앞 · +X = 오른쪽). 둘레 사람 · 3인칭: 앞쪽을 tilt 도 듦 (몸 높이의 카메라에서도 넓은 면 — 누우면 옆선) /
            // 쓴 사람 1인칭: 누운 그대로 (화면 아래로 띠가 지나감 — 눈높이를 가리지 않게)
            double tilt = Math.toRadians(s.numOr("tilt", c.numOr("tilt", 28)));
            Vec3 dir = V.add(V.scale(f, Math.cos(tilt)), V.UP, Math.sin(tilt));
            Vec3 face = V.add(V.scale(V.UP, Math.cos(tilt)), f, -Math.sin(tilt));
            mmorpg.fabric.Fx3d.spawn(level, model, p, card.with("dir", dir, "face", face, "roll", 0.0, "view", 1.0, "owner", (double) owner));
            // 2026-11-14 2판 1인칭: 눈에서 더 낮게 (fpHeight — 발 위 칸) · 앞쪽을 fpTilt 도 듦 — 가슴 높이에 누우면 화면 아래 가는 줄로만 보였음 (미리보기 —
            // 눈 1.62 · 판 1.2 면 앞 2.3칸이 아래 10° 라 띠가 얇음). 화면 가운데 (수평선 위) 는 가리지 않음
            double ft = Math.toRadians(c.numOr("fpTilt", 15));
            Vec3 pf = V.add(V.add(V.add(feet, r, num(at, 0)), V.UP, s.numOr("fpHeight", c.numOr("fpHeight", 0.95))), f, num(at, 2));
            mmorpg.fabric.Fx3d.spawn(level, model, pf, card.with("dir", V.add(V.scale(f, Math.cos(ft)), V.UP, Math.sin(ft)), "face",
                    V.add(V.scale(V.UP, Math.cos(ft)), f, -Math.sin(ft)), "roll", 0.0, "view", 2.0, "owner", (double) owner));
        }
        spawned += 2;
        lastModel = model;
        lastRadius = radius;
        lastLife = life;
        lastDelay = delay;
    }

    /** 선 판의 방향: 앞 fwd 의 반대를 봄 · 볼록한 쪽 = 시작 쪽 start 에서 반시계 90° (mirror 면 시계 90° — 그림이 거울) */
    private static Opt front(Opt card, Vec3 fwd, Obj s, boolean mirror) {
        Vec3 r = V.cross(fwd, V.UP);
        double a = Math.toRadians(s.numOr("start", 90));
        double b = a + (mirror ? -Math.PI / 2 : Math.PI / 2);
        Vec3 bulge = V.add(V.scale(r, Math.cos(b)), V.UP, Math.sin(b));
        return card.with("dir", bulge, "face", V.scale(fwd, -1), "roll", 0.0);
    }

    /** 내려찍기의 땅 충격 (휘두름이 끝나는 때): 땅 고리 · 튀는 불티 */
    private static void impact(ServerPlayer sp, Obj s, double radius, double at, double[] glow, double[] core) {
        List<Object> ip = s.listOrNull("impact");
        int delay = Math.max(0, (int) Math.round(at * 20));
        Tasks.later(delay, () -> {
            if (sp.isRemoved()) return;
            double yaw = Math.toRadians(sp.getYRot());
            Vec3 f = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 r = V.cross(f, V.UP);
            Vec3 p = V.add(V.add(V.add(sp.position(), r, num(ip, 0)), V.UP, num(ip, 1)), f, num(ip, 2));
            double size = radius * cfg().numOr("impactSize", 0.7);
            mmorpg.fabric.skill.SkillFx.shock(sp.level(), p, glow, size, 0.42);
            mmorpg.fabric.skill.SkillFx.shock(sp.level(), p, core, size * 0.6, 0.3);
            mmorpg.fabric.skill.SkillFx.sparks(sp.level(), V.up(p, 0.2), core, 10, 4.5);
            // 2026-11-14 그림체 "섞음" (3D 는 블록 픽셀): 내려찍은 자리에서 3D 바위 조각이 솟아 앞으로 튐 (RuneSwing.erupt — 틈의 빛 = 무기 색)
            int debris = (int) s.numOr("debris", 0);
            if (debris > 0) mmorpg.fabric.rune.RuneSwing.erupt(sp.level(), p, f, debris, s.numOr("debrisSpread", 150), 0.3, size * 0.5, 0.3, 0.55, size * 0.6, glow);
        });
    }

    /**
     * 2026-11-14 맞은 자리 섬광 (hit_spark — 빛살 여덟 · 가운데 섬광 · 퍼지는 고리, 판이 보는 사람을 봄): 판이 나간 휘두르기가 맞힌 적마다 (Slash.hit) —
     * 예전 흰 고리 (bashHit · thrustHit) 대신. 적 몸 가운데에서 쓴 사람 쪽으로 조금 앞, 치명타는 크게 · 치명타 색
     */
    public static void hitSpark(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity victim, Obj style, boolean crit) {
        Obj c = cfg();
        if (c == null || !c.truthy("enabled") || victim == null || !(victim.level() instanceof ServerLevel level)) return;
        Obj k = c.objOrNull("spark");
        if (k == null) return;
        Vec3 head = victim.getEyePosition();
        Vec3 mid = new Vec3(head.x, head.y - 0.45, head.z);
        Vec3 to = V.sub(attacker.getEyePosition(), mid);
        double len = Math.max(1e-6, to.length());
        Vec3 at = V.add(mid, V.scale(to, 1 / len), Math.min(k.numOr("ahead", 0.45), len * 0.5));
        double[] glow = color(style, "glow", new double[] {0.8, 0.9, 1});
        double[] col = crit ? color(style, "critColor", new double[] {1.0, 0.85, 0.3}) : glow;
        double[] core = whiten(col, 0.8);
        double life = k.numOr("life", 0.25);
        double size = k.numOr("size", 0.8) * (crit ? k.numOr("crit", 1.4) : 1);
        mmorpg.fabric.Fx3d.spawn(level, "hit_spark", at, Opt.of("scale", size, "life", life, "anim", "play", "animSpeed", 1.0 / life, "faceCam", 1.0,
                "fadeIn", 0.0, "fadeOut", life * 0.1, "glowAlpha", 0.0, "color", col, "glow", core, "alpha", 1.0, "roll", attacker.getRandom().nextDouble() * 90));
        sparks++;
    }

    /** 시험: 띄운 맞은 자리 섬광 수 */
    public static int sparks;

    // ------------------------------------------------------------ 도움

    private static double num(List<Object> l, int i) {
        return l != null && i < l.size() && l.get(i) instanceof Number n ? n.doubleValue() : 0;
    }

    private static double[] color(Obj style, String key, double[] def) {
        if (style == null || !style.has(key)) return def;
        List<Object> l = style.list(key);
        if (l.size() < 3) return def;
        return new double[] {((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue()};
    }

    private static double[] whiten(double[] c, double t) {
        return new double[] {c[0] + (1 - c[0]) * t, c[1] + (1 - c[1]) * t, c[2] + (1 - c[2]) * t};
    }
}
