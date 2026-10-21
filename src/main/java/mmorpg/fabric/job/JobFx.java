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
    /** 0.28.0 전사 · 매지션 · 궁수 그림 (tools/job_fx_art_more.py) 의 장 수 */
    static final int SMASH = 8;
    static final int BREAK = 6;
    static final int WHIRL = 8;
    static final int TWISTER = 8;
    static final int COMET = 4;
    static final int BOLT = 4;
    static final int SHATTER = 8;
    static final int ORB = 6;
    static final int BEAM = 6;
    static final int BOOK = 8;
    static final int PAGE = 4;
    static final int ARROW = 4;
    static final int BOW = 6;
    static final int TARGET = 6;
    static final int WIND = 8;
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
        two(level, name, frames, 0, at, life, o, glow, core);
    }

    /** 같은 것 — 빛 칸 base · 심지 칸 base + frames (rfx_jring 의 뒤집은 장면은 base = 2 × frames) */
    static void two(ServerLevel level, String name, int frames, int base, Vec3 at, double life, Opt o, double[] glow, double[] core) {
        double fps = frames / Math.max(0.05, life);
        Opt b = o.with("life", life, "frames", (double) frames, "fps", fps, "fade", o.num("fade", 0.2));
        RuneKit.rfx(level, name, at, b.with("color", glow, "cell", (double) base));
        RuneKit.rfx(level, name, at, b.with("color", core, "cell", (double) (base + frames), "alpha", o.num("coreAlpha", o.num("alpha", 1))));
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

    /**
     * 땅 (또는 그 높이) 에 눕힌 한 바퀴 베기 (rfx_jring — 면은 늘 위를 봄): 반지름 radius 칸, reverse = 반시계 (좌우를 뒤집은 장면 — 면을 아래로 눕히면 위에서
     * 보이지 않음). o: alpha · coreAlpha · fade · view · owner · grow · vel
     */
    static void ring(ServerLevel level, Vec3 center, double radius, Jobs.Job j, boolean reverse, double life, Opt o) {
        two(level, "jring", RING, reverse ? 2 * RING : 0, center, life, o.with("dir", V.UP, "size", radius / RING_R, "spin", o.num("spin", 0)), outer(j), inner(j));
    }

    /** 세운 둥근 베기 (rfx_jring 을 normal 쪽을 보게 — 보는 사람 쪽을 보게 두면 눈앞 고리 · 적 둘레 고리) */
    static void ringFacing(ServerLevel level, Vec3 center, double radius, Jobs.Job j, Vec3 normal, double life, Opt o) {
        two(level, "jring", RING, center, life, o.with("dir", normal, "size", radius / RING_R, "spin", o.num("spin", 0)), outer(j), inner(j));
    }

    /** 1인칭 눈앞 고리 (시전자의 1인칭에만 — 태세 · 각성 신호): 눈앞 ahead 칸에 반지름 radius 칸, 화면을 보고 시계 방향으로 한 바퀴 */
    static void eyeRing(ServerPlayer sp, Jobs.Job j, double ahead, double radius, double life) {
        Vec3 look = sp.getViewVector(1F);
        ringFacing(sp.level(), V.add(sp.getEyePosition(), look, ahead), radius, j, V.scale(look, -1), life, only1p(sp).with("vel", RuneKit.bodyVel(sp)));
    }

    /** 빠른 줄 (rfx_jspeed — 시전자의 1인칭에만, 화면 가장자리): 눈앞 1.4칸, life 초 동안 깜빡이며 흐름 (순간 이동 뒤에 부름 — 자리를 따라가지 않음) */
    static void speed(ServerPlayer sp, Jobs.Job j, double life, double alpha) {
        Vec3 look = sp.getViewVector(1F);
        // 반 크기 1.35 (1.4칸 앞): 줄 안쪽 끝이 화면 가운데에서 약 30° — 위아래 시야 (반 35°) 안에도 줄이 보임
        Opt o = only1p(sp).with("size", 1.35, "loop", 1.0, "alpha", alpha, "fade", 0.4, "spin", Kit.rand() * 360);
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

    /** 몸에서 솟는 빛 (rfx_jaura — 세워 카메라 쪽으로 돎): 높이 약 height 칸, life 초 동안 이어 돌림. 시전자 1인칭에서는 숨김 */
    static void aura(ServerPlayer sp, Jobs.Job j, double height, double life, double alpha) {
        // 그림 칸 128 × 256: 반 너비 = 0.3 × 높이 (몸 뒤에 다 가려지지 않게 넓게), 반 높이 = 0.6 × 높이 — 아래 끝이 발밑 조금 아래
        Vec3 c = V.up(sp.position(), height * 0.6 - 0.15);
        Opt o = hide1p(sp).with("size", height * 0.3, "loop", 1.0, "alpha", alpha, "coreAlpha", alpha * 0.8, "vel", RuneKit.bodyVel(sp), "fade", 0.3);
        double fps = AURA / 0.45; // 한 바퀴 0.45초
        Opt base = o.with("life", life, "frames", (double) AURA, "fps", fps);
        RuneKit.rfx(sp.level(), "jaura", c, base.with("color", outer(j), "cell", 0.0));
        RuneKit.rfx(sp.level(), "jaura", c, base.with("color", inner(j), "cell", (double) AURA, "alpha", alpha * 0.8));
    }

    // ------------------------------------------------------------ 0.28.0 전사 · 매지션 · 궁수 그림

    /** 칸 범위 하나 (빛 칸 start ~ · 심지 칸 total + start ~, count 장 — 책 · 활 · 과녁처럼 장면 일부만): fps (0 = 수명에 맞춤) · loop */
    static void cells(ServerLevel level, String name, int total, int start, int count, Vec3 at, double life, double fps, boolean loop, Opt o, double[] glow,
            double[] core) {
        double f = fps > 0 ? fps : count / Math.max(0.05, life);
        Opt b = o.with("life", life, "frames", (double) count, "fps", f, "fade", o.num("fade", 0.2), "loop", loop ? 1.0 : null, "phase",
                loop ? (o.has("phase") ? o.num("phase", 0) : Kit.rand() * count) : null);
        RuneKit.rfx(level, name, at, b.with("color", glow, "cell", (double) start));
        RuneKit.rfx(level, name, at, b.with("color", core, "cell", (double) (total + start), "alpha", o.num("coreAlpha", o.num("alpha", 1))));
    }

    /** 이어 돌리는 그림 (회전 칼날 · 회오리 · 구체 · 책장 …): 장 frames 개를 fps 로 되풀이 */
    static void loop(ServerLevel level, String name, int frames, Vec3 at, double life, double fps, Opt o, double[] glow, double[] core) {
        cells(level, name, frames, 0, frames, at, life, fps, true, o, glow, core);
    }

    /** 땅에 눕는 내려찍기 (rfx_jsmash): 충격 고리 반지름 radius 칸 (그림 고리 끝 = 반 크기 × 0.92) · 금 · 튀는 돌조각 */
    static void smash(ServerLevel level, Vec3 at, double radius, Jobs.Job j, double life, Opt o) {
        two(level, "jsmash", SMASH, at, life, o.with("dir", V.UP, "size", radius / 0.92, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }

    /** 방어 깨짐 (rfx_jbreak — 카메라를 봄): 머리 위에 방패가 갈라짐 */
    static void breakMark(LivingEntity e, Jobs.Job j, double size, double life, double alpha) {
        if (!(e.level() instanceof ServerLevel level)) return;
        two(level, "jbreak", BREAK, RuneKit.overHead(e, 0.55 + size * 0.4), life, Opt.of("size", size, "alpha", alpha, "vel", RuneKit.entVel(e), "fade", 0.3), outer(j),
                inner(j));
    }

    /** 땅 (또는 허리 높이) 에 눕는 회전 칼날 (rfx_jwhirl — 이어 돌림): 칼날 반지름 radius 칸 (= 반 크기 × 0.8) */
    static void whirl(ServerLevel level, Vec3 center, double radius, Jobs.Job j, double life, Opt o) {
        loop(level, "jwhirl", WHIRL, center, life, o.num("fps", 30), o.with("dir", V.UP, "size", radius / 0.8), outer(j), inner(j));
    }

    /** 몸에서 솟는 회오리 (rfx_jtwister — 세워 카메라 쪽으로 돎, 이어 돌림): 높이 약 height 칸 — 시전자 1인칭에서는 숨김 */
    static void twister(ServerPlayer sp, Jobs.Job j, double height, double life, double alpha) {
        Vec3 c = V.up(sp.position(), height * 0.6 - 0.2);
        loop(sp.level(), "jtwister", TWISTER, c, life, 22, hide1p(sp).with("size", height * 0.34, "alpha", alpha, "coreAlpha", alpha * 0.85, "vel", RuneKit.bodyVel(sp),
                "fade", 0.3), outer(j), inner(j));
    }

    /** 불꽃 꼬리 (rfx_jcomet — 머리가 앞): 머리 head 가 dir 쪽 (단위) 으로 speed 칸/초. 길이 = size × 2 */
    static void comet(ServerLevel level, Vec3 head, Vec3 dir, double size, double speed, double life, Jobs.Job j, Opt o) {
        Vec3 c = V.add(head, dir, -size * 0.64);
        loop(level, "jcomet", COMET, c, life, 16, o.with("dir", dir, "size", size, "speed", speed), outer(j), inner(j));
    }

    /** 같은 것 — 색을 정함 (금서의 불 · 얼음 주문) */
    static void comet(ServerLevel level, Vec3 head, Vec3 dir, double size, double speed, double life, double[] glow, double[] core, Opt o) {
        Vec3 c = V.add(head, dir, -size * 0.64);
        loop(level, "jcomet", COMET, c, life, 16, o.with("dir", dir, "size", size, "speed", speed), glow, core);
    }

    /** 날아가는 그림 (머리가 앞 — 마탄 · 빛의 화살): 머리가 from 근처에서 나와 to 에 닿음. headAt = 머리 자리 (그림 길이의 비율 — 가운데 기준 −1 ~ 1). 닿는 틱 */
    private static int fly(ServerLevel level, String name, int frames, Vec3 from, Vec3 to, double speed, double size, double headAt, Opt o, double[] glow,
            double[] core) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.1) return 0;
        Vec3 u = V.scale(d, 1 / len);
        double life = Math.max(0.05, len / speed);
        // 처음: 꼬리 끝이 from (머리는 from + 길이) — 1인칭 화면 뒤에서 튀어나오지 않게, 길이보다 짧은 거리면 그만큼만
        double lead = Math.min(size * (1 + headAt), len * 0.6);
        Vec3 c0 = V.add(from, u, lead - size * headAt);
        double travel = Math.max(0, len - lead);
        loop(level, name, frames, c0, life, 14, o.with("dir", u, "size", size, "speed", travel / life, "fade", o.num("fade", 0.1)), glow, core);
        return Math.max(1, (int) Math.round(life * 20));
    }

    /** 마탄 (rfx_jbolt) 이 from → to: 닿는 틱 */
    static int bolt(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double speed, double size, Opt o) {
        return fly(level, "jbolt", BOLT, from, to, speed, size, 0.56, o, outer(j), inner(j));
    }

    /** 빛의 화살 (rfx_jarrow) 이 from → to: 닿는 틱 */
    static int arrow(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double speed, double size, Opt o) {
        return fly(level, "jarrow", ARROW, from, to, speed, size, 0.97, o, outer(j), inner(j));
    }

    /** 멈춰 있는 빛의 화살 (잔상 — 촉이 tip, 방향 dir) */
    static void arrowAt(ServerLevel level, Vec3 tip, Vec3 dir, double size, double life, Jobs.Job j, Opt o) {
        loop(level, "jarrow", ARROW, V.add(tip, dir, -size * 0.97), life, 10, o.with("dir", dir, "size", size), outer(j), inner(j));
    }

    /** 깨지는 거울 (rfx_jshatter — 카메라를 봄): 금이 퍼지고 조각이 흩어짐 (반지름 약 size 칸) */
    static void shatter(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jshatter", SHATTER, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }

    /** 마력 구체 (rfx_jorb — 카메라를 봄, 이어 돌림): 구 반지름 radius 칸 (= 반 크기 × 0.6) */
    static void orb(ServerLevel level, Vec3 at, double radius, Jobs.Job j, double life, Opt o) {
        loop(level, "jorb", ORB, at, life, 12, o.with("size", radius / 0.6), outer(j), inner(j));
    }

    /**
     * 굵은 광선 (rfx_jbeam — 방향으로 누움): from → to 를 길이 seg 칸 조각으로 (그림은 길이에 따라 굵기가 늘어서 — 조각마다 굵기 = 조각 길이 × 0.25),
     * 조각끼리 조금 겹침
     */
    static void beam(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double seg, double life, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.3) return;
        Vec3 u = V.scale(d, 1 / len);
        int n = Math.max(1, (int) Math.ceil(len / (seg * 0.82)));
        double step = len / n;
        for (int i = 0; i < n; i++) {
            Vec3 c = V.add(from, u, step * (i + 0.5));
            two(level, "jbeam", BEAM, c, life, o.with("dir", u, "size", Math.max(step * 0.62, seg / 2)), outer(j), inner(j));
        }
    }

    /** 펼쳐진 책 (rfx_jbook — 카메라를 봄): 책장이 넘어감 (0 ~ 5 이어 돌림) · closing 이면 닫힘 (6 ~ 7). 책 너비 약 size × 1.44 칸 */
    static void book(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, boolean closing, Opt o) {
        if (closing) cells(level, "jbook", BOOK, 6, 2, at, life, 0, false, o.with("size", size), outer(j), inner(j));
        else cells(level, "jbook", BOOK, 0, 6, at, life, 9, true, o.with("size", size), outer(j), inner(j));
    }

    /** 날리는 책장 (rfx_jpage — 이어 돌림) */
    static void page(ServerLevel level, Vec3 at, double size, double[] glow, double[] core, double life, Opt o) {
        loop(level, "jpage", PAGE, at, life, 10, o.with("size", size), glow, core);
    }

    /** 빛의 활 (rfx_jbow — 카메라를 봄, 뒤에서 본 활): cell 0 ~ 3 = 당긴 정도, 4 ~ 5 = 놓음. 활 높이 약 size × 1.72 칸 */
    static void bow(ServerLevel level, Vec3 at, double size, Jobs.Job j, int cell, int count, double life, Opt o) {
        cells(level, "jbow", BOW, Math.max(0, Math.min(BOW - 1, cell)), Math.max(1, Math.min(BOW - cell, count)), at, life, 0, false, o.with("size", size), outer(j),
                inner(j));
    }

    /** 과녁 (rfx_jtarget — 면이 normal 쪽): 바깥 고리 반지름 radius 칸 (= 반 크기 × 0.84). stage 0 = 조여 들어옴 (0 ~ 3) · 1 = 맞춰진 채 깜빡임 (3 ~ 5 이어 돌림) */
    static void target(ServerLevel level, Vec3 at, double radius, Jobs.Job j, Vec3 normal, int stage, double life, Opt o) {
        Opt b = o.with("dir", normal, "size", radius / 0.84);
        if (stage == 0) cells(level, "jtarget", TARGET, 0, 4, at, life, 0, false, b, outer(j), inner(j));
        else cells(level, "jtarget", TARGET, 3, 3, at, life, 8, true, b, outer(j), inner(j));
    }

    /** 땅에 눕는 바람 고리 (rfx_jwind): 바깥 반지름 약 radius 칸 */
    static void wind(ServerLevel level, Vec3 at, double radius, Jobs.Job j, double life, Opt o) {
        two(level, "jwind", WIND, at, life, o.with("dir", V.UP, "size", radius, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }
}
