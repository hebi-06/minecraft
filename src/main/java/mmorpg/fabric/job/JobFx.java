package mmorpg.fabric.job;

import mmorpg.core.Jobs;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.27.0 직업 스킬 연출 (메이플식 2D 그림 — tools/job_fx_art.py 의 rpg:rfx_j*, 룬 연출의 바닥 문양 · 룬 글자 · 영역 문양 대신):
 * 그림마다 빛 (넓고 흐린 겉 — 직업의 짙은 빛) + 심지 (가늘고 또렷한 속 — 옅은 빛 · 흰색) 두 장을 같은 자리에 겹쳐 frames 장을 수명 동안 차례로 넘김.
 * <ul>
 * <li>slash — 눈앞에서 휘두르는 큰 초승달 베기 (카메라를 봄 — 1인칭 화면 가운데 · 3인칭은 몸 앞)</li>
 * <li>hit — 맞은 적마다 번쩍 · 베인 줄 · 고리 · 조각</li>
 * <li>ring — 땅 (또는 허리 높이) 에 눕힌 한 바퀴 베기</li>
 * <li>dash · line — 돌진 줄기 · 일섬 (방향으로 누움)</li>
 * <li>burst — 터짐</li>
 * <li>aura — 몸에서 솟는 빛 (시전자 1인칭에서는 숨김 — 몸 가운데의 그림은 카메라가 그림 속이라 픽셀이 화면을 덮는 큰 네모가 됨)</li>
 * </ul>
 * 큰 그림은 1인칭 눈 가까이 (반 크기 안 · 앞뒤 1칸 안) 에 두지 않음 — 카메라 바로 옆의 빌보드는 몇 픽셀이 화면 가득 늘어남.
 */
final class JobFx {
    private JobFx() {}

    static final double[] WHITE = RuneKit.WHITE;
    static final int SLASH = 8;
    static final int HIT = 6;
    static final int RING = 8;
    static final int DASH = 6;
    static final int LINE = 6;
    static final int BURST = 8;
    static final int AURA = 8;
    static final int SPEED = 4;
    /** 그림 칸의 고리 반지름 = 사각형 반 크기 × 0.88 (tools/job_fx_art.py ring_frame R = 칸의 0.44) */
    private static final double RING_R = 0.88;

    // ------------------------------------------------------------ 색

    /** 겉빛: 직업 두 빛 중 더 짙은 쪽 (검사 하늘색 · 마검사 보라) */
    static double[] outer(Jobs.Job j) {
        double[] a = JobKit.glow(j);
        double[] b = JobKit.core(j);
        return sat(a) >= sat(b) ? a : b;
    }

    /** 속빛: 다른 쪽을 흰색 쪽으로 (검사 흰빛 · 마검사 옅은 청록) */
    static double[] inner(Jobs.Job j) {
        double[] a = JobKit.glow(j);
        double[] b = JobKit.core(j);
        double[] o = sat(a) >= sat(b) ? b : a;
        return new double[] {o[0] + (1 - o[0]) * 0.55, o[1] + (1 - o[1]) * 0.55, o[2] + (1 - o[2]) * 0.55};
    }

    private static double sat(double[] c) {
        double max = Math.max(c[0], Math.max(c[1], c[2]));
        double min = Math.min(c[0], Math.min(c[1], c[2]));
        return max <= 0 ? 0 : (max - min) / max;
    }

    // ------------------------------------------------------------ 보는 사람

    /** 시전자가 1인칭이면 숨김 (몸 둘레 · 발밑 · 머리 위 그림) */
    static Opt hide1p(ServerPlayer sp) {
        return Opt.of("view", 1.0, "owner", (double) sp.getId());
    }

    /** 시전자의 1인칭에서만 (눈앞에 따로 그리는 그림) */
    static Opt only1p(ServerPlayer sp) {
        return Opt.of("view", 2.0, "owner", (double) sp.getId());
    }

    // ------------------------------------------------------------ 그리기

    /** 그림 한 벌 (빛 칸 0 · 심지 칸 frames): life 초 동안 frames 장. o: dir · size · spin · spinRate · speed · grow · fade · alpha · coreAlpha · vel · view · owner */
    static void two(ServerLevel level, String name, int frames, Vec3 at, double life, Opt o, double[] glow, double[] core) {
        double fps = frames / Math.max(0.05, life);
        Opt base = o.with("life", life, "frames", (double) frames, "fps", fps, "fade", o.num("fade", 0.2));
        RuneKit.rfx(level, name, at, base.with("color", glow, "cell", 0.0));
        RuneKit.rfx(level, name, at, base.with("color", core, "cell", (double) frames, "alpha", o.num("coreAlpha", o.num("alpha", 1))));
    }

    /** 화면 위쪽 (보는 쪽에 수직 — 고개를 들고 숙여도 화면에서 위) */
    static Vec3 screenUp(Vec3 look) {
        Vec3 up = V.sub(V.UP, V.scale(look, V.dot(V.UP, look)));
        double l = V.len(up);
        return l < 1e-4 ? V.UP : V.scale(up, 1 / l);
    }

    /**
     * 눈앞 큰 베기 (rfx_jslash — 카메라를 봄): 눈에서 보는 쪽 ahead 칸 · 화면 아래 down 칸, size = 가로 반 칸 (세로 = 반), tilt = 화면에서 기운 각
     * (도 — 0 = 왼쪽에서 아래를 지나 오른쪽으로, 180 = 뒤집혀 오른쪽 위에서 왼쪽으로). 1인칭 화면 가운데 · 3인칭은 몸 앞
     */
    static void slash(ServerPlayer sp, Jobs.Job j, double ahead, double down, double size, double tilt, double life) {
        Vec3 look = sp.getViewVector(1F);
        Vec3 at = V.add(V.add(sp.getEyePosition(), look, ahead), screenUp(look), -down);
        two(sp.level(), "jslash", SLASH, at, life, Opt.of("size", size, "spin", tilt, "vel", RuneKit.bodyVel(sp), "fade", 0.15), outer(j), inner(j));
    }

    /** 맞은 자리 (rfx_jhit): 몸 가운데 · 몸 크기에 맞춤 · 아무렇게나 기욺 */
    static void hit(LivingEntity e, Jobs.Job j, double scale) {
        if (!(e.level() instanceof ServerLevel level)) return;
        double size = (0.75 + Kit.bodyPad(e) * 0.6) * scale;
        two(level, "jhit", HIT, Kit.bodyPoint(e), 0.3, Opt.of("size", size, "spin", Kit.rand() * 360, "fade", 0.2), outer(j), inner(j));
    }

    /** 땅 (또는 그 높이) 에 눕힌 한 바퀴 베기 (rfx_jring): 반지름 radius 칸, reverse = 반시계. o: alpha · fade · view · owner · grow */
    static void ring(ServerLevel level, Vec3 center, double radius, Jobs.Job j, boolean reverse, double life, Opt o) {
        two(level, "jring", RING, center, life, o.with("dir", reverse ? V.DOWN : V.UP, "size", radius / RING_R, "spin", o.num("spin", 0)), outer(j), inner(j));
    }

    /** 세운 둥근 베기 (rfx_jring 을 normal 쪽을 보게 — 눈앞 고리 · 적 둘레 고리) */
    static void ringFacing(ServerLevel level, Vec3 center, double radius, Jobs.Job j, Vec3 normal, double life, Opt o) {
        two(level, "jring", RING, center, life, o.with("dir", normal, "size", radius / RING_R, "spin", o.num("spin", 0)), outer(j), inner(j));
    }

    /** 1인칭 눈앞 고리 (시전자의 1인칭에만 — 태세 · 각성 신호): 눈앞 ahead 칸에 반지름 radius 칸, 화면을 보고 시계 방향으로 한 바퀴 */
    static void eyeRing(ServerPlayer sp, Jobs.Job j, double ahead, double radius, double life) {
        Vec3 look = sp.getViewVector(1F);
        ringFacing(sp.level(), V.add(sp.getEyePosition(), look, ahead), radius, j, V.scale(look, -1), life, only1p(sp).with("vel", RuneKit.bodyVel(sp)));
    }

    /** 빠른 줄 (rfx_jspeed — 시전자의 1인칭에만, 화면 가장자리): 눈앞 1.4칸 · 화면을 덮는 크기, life 초 동안 깜빡이며 흐름 */
    static void speed(ServerPlayer sp, Jobs.Job j, double life, double alpha) {
        Vec3 look = sp.getViewVector(1F);
        Opt o = only1p(sp).with("size", 1.9, "loop", 1.0, "alpha", alpha, "vel", RuneKit.bodyVel(sp), "fade", 0.4, "spin", Kit.rand() * 360);
        Opt base = o.with("life", life, "frames", (double) SPEED, "fps", 24.0);
        Vec3 at = V.add(sp.getEyePosition(), look, 1.4);
        RuneKit.rfx(sp.level(), "jspeed", at, base.with("color", outer(j), "cell", 0.0));
        RuneKit.rfx(sp.level(), "jspeed", at, base.with("color", inner(j), "cell", (double) SPEED, "alpha", alpha * 0.9));
    }

    /** 돌진 줄기 (rfx_jdash — 오른쪽이 앞): from → to. 굵기 = 길이의 1/4 (줄 여럿이 그 안에) */
    static void dash(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double life, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2) return;
        two(level, "jdash", DASH, V.lerp(from, to, 0.5), life, o.with("dir", V.scale(d, 1 / len), "size", len / 2), outer(j), inner(j));
    }

    /** 일섬 (rfx_jline): from → to — 가는 선이 그어지고 가운데가 부풀었다 사라짐 */
    static void line(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double life, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2) return;
        two(level, "jline", LINE, V.lerp(from, to, 0.5), life, o.with("dir", V.scale(d, 1 / len), "size", len / 2), outer(j), inner(j));
    }

    /** 터짐 (rfx_jburst — 카메라를 봄) */
    static void burst(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jburst", BURST, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }

    /** 몸에서 솟는 빛 (rfx_jaura — 세워 카메라 쪽으로 돎): 높이 height 칸, life 초 동안 이어 돌림. 시전자 1인칭에서는 숨김 */
    static void aura(ServerPlayer sp, Jobs.Job j, double height, double life, double alpha) {
        Vec3 c = V.up(sp.position(), height / 2 - 0.1);
        Opt o = hide1p(sp).with("size", height / 4, "loop", 1.0, "alpha", alpha, "coreAlpha", alpha * 0.8, "vel", RuneKit.bodyVel(sp), "fade", 0.3);
        double fps = AURA / 0.45; // 한 바퀴 0.45초
        Opt base = o.with("life", life, "frames", (double) AURA, "fps", fps);
        RuneKit.rfx(sp.level(), "jaura", c, base.with("color", outer(j), "cell", 0.0));
        RuneKit.rfx(sp.level(), "jaura", c, base.with("color", inner(j), "cell", (double) AURA, "alpha", alpha * 0.8));
    }
}
