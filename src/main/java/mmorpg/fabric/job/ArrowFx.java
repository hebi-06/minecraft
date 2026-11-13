package mmorpg.fabric.job;

import mmorpg.fabric.rune.BowFx;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-14 궁수 계열 다시 만들기 (사용자 컨셉 — 폭풍 궁수 · 천벌의 사수 · 그림자 사수): 세 직업이 함께 쓰는 그림. 모드 없는 클라이언트에도 보이는 것만 — 3D 빛 화살 ·
 * 번개 창 (RuneSwords) · 구름 · 날개 · 빛 고리 (RuneProps) · 룬 그림 (번개 · 번개 구슬 · 빛줄 · 반짝임 · 빛기둥). 직업 2D 그림 (rfx_j*) 은 모드 없는 클라이언트에
 * 거의 보이지 않으므로 이 셋의 새 연출에는 쓰지 않음 (맞은 자리 JobFx.hit · 터짐 JobFx.burst 만 — 룬 묶음에 들어 있음)
 */
final class ArrowFx {
    private ArrowFx() {}

    /** 번개 그림 (rpg:rfx_bolt) 칸 {빛, 심지} */
    static final int[] BOLT = {0, RuneKit.BOLT_CORE};

    /** 날아가는 틱 (JobFx.arrow 와 같은 셈 — 스킬의 피해 틱이 예전과 같게): from → to 를 speed 칸/초 */
    static int flyTicks(Vec3 from, Vec3 to, double speed) {
        double len = V.len(V.sub(to, from));
        if (len < 0.1) return 0;
        return Math.max(1, (int) Math.round(Math.max(0.05, len / speed) * 20));
    }

    /** 3D 화살의 오늬에서 촉까지 (칸 — rune_arrow 길이 len) */
    static double reach(double len) {
        return len * (31 - RuneProps.ARROW_NOCK[1]) / RuneProps.ARROW_LEN;
    }

    private static Vec3 faceOf(Vec3 d) {
        return Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : V.rightOf(V.flat(d));
    }

    /**
     * 3D 빛 화살 (rune_arrow · 길이 len) 이 from (오늬) 에서 촉이 to 에 닿게 ticks 틱에 날아감 (1틱에 나타나 ticks − 1 틱 동안 움직임 — 촉이 닿는 틱 = ticks, 스킬의 피해 틱과
     * 같음) → stick 틱 꽂혀 있다가 작아지며 사라짐. follow 가 있으면 꽂힌 동안 그 몸을 따라감
     */
    static void arrow3d(ServerLevel level, Vec3 from, Vec3 to, int ticks, double len, int stick, LivingEntity follow, double[] glow, double[] core) {
        Vec3 d0 = V.sub(to, from);
        if (V.len(d0) < 0.2) return;
        Vec3 d = V.unit(d0);
        Vec3 face = faceOf(d);
        double r = reach(len);
        int t = Math.max(1, ticks);
        Vfx.Fx fx = RuneProps.fx(level, from, t + stick + 10);
        RuneSwords.Blade a = RuneSwords.arrow(fx, from, d, face, len, 0.7, glow, core);
        Vec3 nockEnd = V.add(to, d, -r);
        fx.on(1, () -> {
            a.grow(1, 1);
            a.pose(nockEnd, d, face, Math.max(1, t - 1));
        });
        if (follow != null && stick > 0) {
            Vec3 off = V.sub(nockEnd, Kit.bodyPoint(follow));
            fx.each(t + 1, t + stick, 1, tt -> {
                if (follow.isRemoved()) return;
                a.pose(V.add(Kit.bodyPoint(follow), off), d, face, 1);
            });
        }
        fx.on(t + stick, () -> a.grow(0.1, 4));
        fx.on(t + stick + 5, a::remove);
    }

    /** 화살 한 발 (3D 빛 화살 + 날아가는 빛줄): 촉이 닿는 틱 (JobFx.arrow 와 같은 셈) */
    static int shot(ServerLevel level, Vec3 from, Vec3 to, double speed, double len, int stick, LivingEntity follow, double[] glow, double[] core) {
        int t = flyTicks(from, to, speed);
        if (t <= 0) return 1;
        arrow3d(level, from, to, t, len, stick, follow, glow, core);
        RuneKit.flyStreak(level, from, to, speed, glow, core, 0.55 + len * 0.15, 0.16);
        return t;
    }

    /** 하늘에서 땅으로 벼락 (번개 그림 한 장 — 키 height · 지그재그 줄기 · 땅의 번쩍임 · 불티): ground = 떨어지는 땅 */
    static void skyBolt(ServerLevel level, Vec3 ground, double height, double[] glow, double[] core) {
        RuneKit.rfx2(level, "bolt", V.up(ground, height / 2), Opt.of("size", height / 2, "life", 0.26, "cell", Math.floor(Kit.rand() * 4)), glow, RuneKit.WHITE, BOLT, null);
        SkillFx.bolt(level, new Vec3(ground.x + (Kit.rand() - 0.5) * 0.8, ground.y + height, ground.z + (Kit.rand() - 0.5) * 0.8), V.up(ground, 0.1), core,
                Opt.of("jag", 0.45, "thick", 0.12, "life", 0.22));
        SkillFx.burst(level, V.up(ground, 0.5), core, 1.2, 0.22);
        SkillFx.sparks(level, V.up(ground, 0.3), core, 6, 5);
    }

    /** 땅에 비스듬히 꽂힌 3D 화살 (len 칸 — 촉이 p 아래로 박힘): 위에서 2틱에 내리꽂혀 hold 틱 남았다가 작아져 사라짐 (룬 속박 화살의 말뚝과 같음) */
    static void pinStake(ServerLevel level, Vec3 p, Vec3 tilt, int hold, double len, double[] glow, double[] core) {
        Vec3 tl = V.unit(tilt);
        double r = reach(len);
        Vec3 nock = V.sub(V.add(p, tl, 0.25), V.scale(tl, r));
        Vfx.Fx fx = RuneProps.fx(level, p, hold + 10);
        Vec3 face = V.rightOf(V.flat(tl).lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : V.flat(tl));
        RuneSwords.Blade a = RuneSwords.arrow(fx, V.sub(nock, V.scale(tl, 2.5)), tl, face, len, 0.7, glow, core);
        fx.on(1, () -> {
            a.grow(1, 1);
            a.pose(nock, tl, face, 2);
        });
        fx.on(Math.max(4, hold), () -> a.grow(0.1, 4));
        fx.on(Math.max(4, hold) + 5, a::remove);
    }

    /** 하늘에서 떨어져 꽂히는 3D 빛 화살 (촉이 땅 g 위 sky 칸 → 땅속, drop 틱 · stuck 틱 꽂혀 있다가 가라앉음) */
    static void fallArrow(ServerLevel level, Vec3 g, double len, double sky, int drop, int stuck, double[] glow, double[] core) {
        BowFx.fallArrow(level, g, len, sky, drop, stuck, glow, core);
    }

    /** 반짝임 (rfx prop glint — 네 뿔 빛) */
    static void glint(ServerLevel level, Vec3 at, double size, double life, double[] color, Opt o) {
        RuneKit.rfx(level, "prop", at, o.with("size", size, "life", life, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", o.num("spin", Kit.rand() * 90), "color", color,
                "grow", o.num("grow", 0.5), "fade", o.num("fade", 0.5)));
    }

    /** 깃털 한 장 (rfx prop feather — 천천히 떨어짐) */
    static void feather(ServerLevel level, Vec3 at, double size, double life, double[] color) {
        RuneKit.rfx(level, "prop", at, Opt.of("size", size, "life", life, "cell", (double) RuneKit.RFX.get("feather")[0], "spin", Kit.rand() * 360, "spinRate",
                (Kit.rand() - 0.5) * 120, "dir", V.DOWN, "speed", 0.5 + Kit.rand() * 0.4, "color", color, "fade", 0.5));
    }

    /** 몸에서 bowHand 쪽 (1인칭 오른쪽 아래 활) 의 화살 나가는 자리 */
    static Vec3 bow(ServerPlayer sp, double ahead) {
        return V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), ahead);
    }

    // ------------------------------------------------------------ 3D 날개 (천벌의 사수 — 윙 스텝 · 세라핌): 대천사 강림의 날개 (RuneHoly.wings3d) 와 같은 자세 셈

    /** 날개 자세 (뿌리 자리 · 회전) */
    record WingPose(Vec3 at, org.joml.Quaternionf rot) {}

    /**
     * 몸 c (발) · 보는 쪽 f 의 등 뒤 날개 하나: s = +1 오른쪽 · −1 왼쪽, sweep = 뒤로 젖힘 (도), flap = 위로 듦 (도), up = 뿌리 높이 (발에서 칸), lean = 뿌리를
     * 옆으로 (칸) — 대천사 날개 뿌리 (어깨 뒤 1.36칸) 와 같은 셈
     */
    static WingPose wingPose(Vec3 c, Vec3 f, int s, double sweep, double flap, double up, double lean) {
        Vec3 r = V.rightOf(f);
        Vec3 at = V.add(V.add(V.up(c, up), f, -0.24), r, s * lean);
        double ps = Math.toRadians(sweep);
        double th = Math.toRadians(flap);
        Vec3 out0 = V.add(V.scale(r, s * Math.cos(ps)), f, -Math.sin(ps));
        Vec3 out = V.add(V.scale(out0, Math.cos(th)), V.UP, Math.sin(th));
        Vec3 upv = V.sub(V.scale(V.UP, Math.cos(th)), V.scale(out0, Math.sin(th)));
        return new WingPose(at, RuneProps.facing(out, upv));
    }

    /** 머리 위 빛 고리의 회전: 뒤로 tilt 도 기울고 t 틱마다 5° 돎 */
    static org.joml.Quaternionf haloRot(Vec3 f, int t, double tilt) {
        double a = Math.toRadians(tilt);
        Vec3 n = V.sub(V.scale(V.UP, Math.cos(a)), V.scale(f, Math.sin(a)));
        return RuneProps.along(n, RuneKit.turn(V.rightOf(f), t * 5.0));
    }
}
