package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 0.47.0 직업 스킬의 입체 효과 물체 (사용자 "효과 물체를 입체 모델로"): Fx3d 모델 (tools/fx_models.py — 환영 검 · 창 · 비수 · 빛의 화살 · 둥근 방패 · 마탄 ·
 * 비전 구체 · 금서 · 책장 · 번개 대망치 · 큰 종 · 명계의 문 · 망자의 손 · 환영 도끼 · 연 방패 · 빛의 활) 을 직업 색 (몸 = 짙은 빛, 속빛 · 빛 무리 = 옅은 빛) 으로 띄움.
 * 2D 그림 (JobFx) 은 빛 · 베기 · 터짐을 맡고, 물체 (칼 · 창 · 책 · 문 …) 는 이 입체 모델이 맡음.
 * <p>
 * 모델 크기 (모델 칸): 아래 상수 — 2D 그림의 크기 (size) 에서 모델 크기 (scale) 로 바꿀 때 씀.
 */
final class JobFx3d {
    private JobFx3d() {}

    /** phantom_sword: 자루 끝 −0.35 ~ 칼끝 1.32 (가운데 = 코등이) */
    static final double SWORD_LEN = 1.67;
    static final double SWORD_TIP = 1.32;
    /** spear: −1.36 ~ 창끝 1.47 */
    static final double SPEAR_LEN = 2.83;
    static final double SPEAR_TIP = 1.47;
    /** light_arrow: −0.56 ~ 촉 0.64 */
    static final double ARROW_LEN = 1.2;
    static final double ARROW_TIP = 0.64;
    /** dagger: −0.21 ~ 날 끝 0.5 */
    static final double DAGGER_LEN = 0.71;
    /** mana_bolt: −0.36 ~ 끝 0.34 */
    static final double BOLT_LEN = 0.7;
    static final double BOLT_TIP = 0.34;
    /** buckler 반지름 · aegis 높이 (−0.98 ~ 0.78) · maul 길이 (머리 가운데 0 · 자루 끝 −2.2) · bell 높이 (고리 0 ~ 입구 −1.3) · gate 높이 · grimoire 너비 · arcane_orb 구 반지름 */
    static final double BUCKLER_R = 0.48;
    static final double AEGIS_H = 1.76;
    static final double MAUL_LEN = 2.5;
    static final double BELL_H = 1.45;
    static final double GATE_H = 3.68;
    static final double GRIMOIRE_W = 1.16;
    static final double ORB_R = 0.32;
    /** phantom_axe: 자루 −0.75 ~ 0.77, 도끼날 가운데 (0.3, 0.45) */
    static final double AXE_LEN = 1.52;

    // ------------------------------------------------------------ 바탕

    /** 직업 색 (몸 = 짙은 빛 · 속빛과 빛 무리 = 옅은 빛) */
    static Opt look(Jobs.Job j) {
        return Opt.of("color", JobFx.outer(j), "glow", JobFx.inner(j));
    }

    /** a 에 b 를 덮어씀 */
    static Opt merge(Opt a, Opt b) {
        Opt r = a;
        for (String k : b.keys()) r = r.with(k, b.get(k));
        return r;
    }

    /** 2D 그림 선택값의 보는 사람 (view · owner — 1인칭 숨김 · 1인칭에만) 만 */
    static Opt viewOf(Opt o) {
        Opt r = Opt.empty();
        if (o.has("view")) r = r.with("view", o.num("view", 0));
        if (o.has("owner")) r = r.with("owner", o.num("owner", 0));
        return r;
    }

    /** 2D 그림 선택값에서 입체로 넘길 것 (보는 사람 · 투명) */
    static Opt carry(Opt o) {
        Opt r = viewOf(o);
        if (o.has("alpha")) r = r.with("alpha", Math.min(1, o.num("alpha", 1)));
        return r;
    }

    /** 모델 하나 (직업 색): 번호 */
    static int spawn(ServerLevel level, String model, Vec3 at, Jobs.Job j, Opt o) {
        return Fx3d.spawn(level, model, at, merge(look(j), o));
    }

    /** 모델 하나 (색을 정함) */
    static int spawn(ServerLevel level, String model, Vec3 at, double[] color, double[] glow, Opt o) {
        return Fx3d.spawn(level, model, at, merge(Opt.of("color", color, "glow", glow), o));
    }

    /** 몸통 가로 · 세로 방향에 수직인 수평 방향 (날아가는 쪽 u 기준 — 비수가 앞뒤로 구르는 축) */
    static Vec3 sideOf(Vec3 u) {
        Vec3 s = V.cross(u, V.UP);
        double l = V.len(s);
        return l < 1e-3 ? new Vec3(1, 0, 0) : V.scale(s, 1 / l);
    }

    // ------------------------------------------------------------ 날아가는 것

    /**
     * 곧게 날아감: 모델 끝 (tip 모델 칸 × scale) 이 secs 초에 to 에 닿고 hold 초 머무름 (가는 쪽을 봄 — align, 넓은 면이 보는 사람 쪽 — faceCam). 번호
     */
    static int fly(ServerLevel level, String model, Vec3 from, Vec3 to, double tip, double scale, double secs, double hold, Jobs.Job j, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        Vec3 u = len < 1e-3 ? V.UP : V.scale(d, 1 / len);
        Vec3 end = V.add(to, u, -tip * scale);
        Vec3 start = V.add(from, u, -Math.min(tip * scale, len * 0.5));
        double travel = Math.max(0.05, secs);
        Opt b = carry(o).with("to", end, "ctrl", o.vec("ctrl", V.lerp(start, end, 0.5)), "travel", travel, "life", travel + Math.max(0, hold), "align", 1.0, "dir", u,
                "scale", scale, "fadeIn", 0.04, "fadeOut", o.num("fadeOut", 0.1), "faceCam", o.num("faceCam", 1), "glowAlpha", o.num("glowAlpha", 0.45), "shine",
                o.num("shine", 0.15), "spin", o.num("roll", 0), "spinAxis", "y", "delay", o.numOrNull("delay"), "ease", o.str("ease", null));
        return spawn(level, model, start, j, b);
    }

    /** 환영 검이 날아가 꽂힘 (칼끝 → to): speed 칸/초 · hold 초 꽂혀 있음 — 닿는 틱 */
    static int swordFly(ServerLevel level, Vec3 from, Vec3 to, double speed, double scale, double hold, Jobs.Job j, Opt o) {
        double len = V.len(V.sub(to, from));
        double secs = Math.max(0.05, len / Math.max(1, speed));
        fly(level, "phantom_sword", from, to, SWORD_TIP, scale, secs, hold, j, o);
        return Math.max(1, (int) Math.round(secs * 20));
    }

    /** 빛의 창이 날아가 꽂힘 (창끝 → to — 축 둘레로 돎): 닿는 틱 */
    static int spearFly(ServerLevel level, Vec3 from, Vec3 to, double speed, double scale, double hold, Jobs.Job j, Opt o) {
        double len = V.len(V.sub(to, from));
        double secs = Math.max(0.05, len / Math.max(1, speed));
        fly(level, "spear", from, to, SPEAR_TIP, scale, secs, hold, j, o.with("roll", o.num("roll", 540), "faceCam", 0.0));
        return Math.max(1, (int) Math.round(secs * 20));
    }

    /** 빛의 화살이 날아가 꽂힘 (촉 → to): 닿는 틱 */
    static int arrowFly(ServerLevel level, Vec3 from, Vec3 to, double speed, double scale, double hold, Jobs.Job j, Opt o) {
        double len = V.len(V.sub(to, from));
        double secs = Math.max(0.05, len / Math.max(1, speed));
        fly(level, "light_arrow", from, to, ARROW_TIP, scale, secs, hold, j, o.with("roll", o.num("roll", 720), "faceCam", 0.0));
        return Math.max(1, (int) Math.round(secs * 20));
    }

    /** 마탄 (결정 — 끝 → to, 축 둘레로 빠르게 돎): 닿는 틱 */
    static int boltFly(ServerLevel level, Vec3 from, Vec3 to, double speed, double scale, double[] color, double[] glow, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        double secs = Math.max(0.05, len / Math.max(1, speed));
        Vec3 u = len < 1e-3 ? V.UP : V.scale(d, 1 / len);
        Vec3 end = V.add(to, u, -BOLT_TIP * scale);
        Vec3 start = V.add(from, u, -Math.min(BOLT_TIP * scale, len * 0.5));
        spawn(level, "mana_bolt", start, color, glow, carry(o).with("to", end, "ctrl", V.lerp(start, end, 0.5), "travel", secs, "life", secs + 0.03, "align", 1.0,
                "dir", u, "scale", scale, "spin", 1080.0, "spinAxis", "y", "glowAlpha", 0.6, "shine", 0.35, "fadeOut", 0.05));
        return Math.max(1, (int) Math.round(secs * 20));
    }

    /** 멈춘 물체 (끝 tip 모델 칸이 at, 방향 dir — 꽂힌 화살 · 창): 번호 */
    static int stuck(ServerLevel level, String model, Vec3 at, Vec3 dir, double tip, double scale, double life, Jobs.Job j, Opt o) {
        Vec3 u = V.len(dir) < 1e-3 ? V.DOWN : V.unit(dir);
        return spawn(level, model, V.add(at, u, -tip * scale), j, carry(o).with("dir", u, "scale", scale, "life", life, "fadeOut", Math.min(life, o.num("fade", 0.25)),
                "faceCam", 1.0, "glowAlpha", 0.4, "shine", 0.1, "vel", o.vec("vel", null)));
    }

    /**
     * 앞뒤로 구르며 날아가는 비수 (from → to 를 ticks 틱 — 가는 쪽에 수직인 수평 축 둘레로 rate 도/초): 번호 (돌아올 때 Fx3d.move)
     */
    static int daggerFly(ServerLevel level, Vec3 from, Vec3 to, int ticks, double scale, double life, Jobs.Job j, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        Vec3 u = len < 1e-3 ? V.flat(new Vec3(1, 0, 0)) : V.scale(d, 1 / len);
        double secs = Math.max(0.05, ticks / 20.0);
        Opt b = carry(o).with("dir", u, "face", sideOf(u), "spin", o.num("spinRate", -1260), "spinAxis", "z", "scale", scale, "life", Math.max(life, secs), "fadeOut",
                0.08, "glowAlpha", 0.5, "shine", 0.2);
        if (len > 1e-3) b = b.with("to", to, "ctrl", V.lerp(from, to, 0.5), "travel", secs);
        return spawn(level, "dagger", from, j, b);
    }

    // ------------------------------------------------------------ 휘두름

    /**
     * 휘두름: pivot (손잡이) 에서 up 쪽으로 선 모델 (+Y) 을 up → sweep 평면에서 a0 → a1 도 (0 = up, 90 = sweep) 로 secs 초에 돌리고 (sweep 쪽으로 — 날이 앞서게),
     * 끝 각에서 hold 초 머무름 (흐려지며). up · sweep 은 서로 수직인 단위. 번호 {휘두르는 것, 머무는 것}
     */
    static int[] swing(ServerLevel level, String model, Vec3 pivot, Vec3 up, Vec3 sweep, double a0, double a1, double secs, double hold, double scale, Jobs.Job j,
            Opt o) {
        Vec3 axis = V.unit(V.cross(sweep, up)); // 모델 +Z (넓은 면) — 이 축 둘레로 돌면 +Y 가 up → sweep 쪽으로 감
        double r0 = Math.toRadians(a0);
        double r1 = Math.toRadians(a1);
        Vec3 d0 = V.add(V.scale(up, Math.cos(r0)), sweep, Math.sin(r0));
        Vec3 d1 = V.add(V.scale(up, Math.cos(r1)), sweep, Math.sin(r1));
        double t = Math.max(0.05, secs);
        Opt b = carry(o).with("scale", scale, "glowAlpha", o.num("glowAlpha", 0.5), "shine", o.num("shine", 0.25));
        int a = spawn(level, model, pivot, j, b.with("dir", d0, "face", axis, "spin", -(a1 - a0) / t, "spinAxis", "z", "life", t, "fadeOut", 0.0, "fadeIn", 0.03));
        int h = hold > 0 ? spawn(level, model, pivot, j, b.with("dir", d1, "face", axis, "life", hold, "fadeOut", hold * 0.8, "delay", t)) : 0;
        return new int[] {a, h};
    }

    // ------------------------------------------------------------ 둘레를 도는 것

    /**
     * 주인 둘레를 도는 모델 n 개 (follow + orbit — 주인 기준 [오른쪽, 위, 앞] 칸의 center 둘레 반지름 radius, rate 도/초): radial 이면 모델 +Y 가 바깥 (도끼 ·
     * 칼끝이 바깥으로 — 주인 기준 둘레는 세계에서 거울이라 돌림 = +rate), 아니면 가는 쪽 (align). 번호들
     */
    static int[] orbitOwner(ServerPlayer sp, String model, int n, Vec3 center, double radius, double rate, boolean radial, double life, double scale, Jobs.Job j,
            Opt o) {
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            double ang = i * 360.0 / n + o.num("angle", 0);
            double rad = Math.toRadians(ang);
            Opt b = carry(o).with("follow", (double) sp.getId(), "orbit", radius, "orbitSpeed", rate, "orbitAngle", ang, "life", life, "scale", scale, "fadeIn",
                    o.num("fadeIn", 0.15), "fadeOut", o.num("fadeOut", 0.2), "glowAlpha", o.num("glowAlpha", 0.45), "shine", o.num("shine", 0.2), "bob", o.num("bob", 0),
                    "bobHz", o.num("bobHz", 1));
            if (radial) b = b.with("dir", new Vec3(Math.cos(rad), 0, Math.sin(rad)), "face", V.UP, "spin", rate, "spinAxis", "up");
            else b = b.with("align", 1.0, "faceCam", o.num("faceCam", 1));
            ids[i] = spawn(sp.level(), model, center, j, b);
        }
        return ids;
    }

    /** 세계의 한 점 둘레를 도는 모델 n 개 (orbit — 가는 쪽을 봄): 번호들 */
    static int[] orbitAt(ServerLevel level, String model, int n, Vec3 center, double radius, double rate, double life, double scale, double[] color, double[] glow,
            Opt o) {
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            double ang = i * 360.0 / n + o.num("angle", 0);
            ids[i] = spawn(level, model, center, color, glow, carry(o).with("orbit", radius, "orbitSpeed", rate, "orbitAngle", ang, "life", life, "scale", scale, "align",
                    1.0, "faceCam", o.num("faceCam", 1), "fadeIn", o.num("fadeIn", 0.2), "fadeOut", o.num("fadeOut", 0.2), "glowAlpha", o.num("glowAlpha", 0.45), "shine",
                    o.num("shine", 0.2), "bob", o.num("bob", 0), "bobHz", o.num("bobHz", 0.8)));
        }
        return ids;
    }

    // ------------------------------------------------------------ 지우기 · 붙들어 둔 것 (태세 동안)

    static void kill(ServerLevel level, int id, Vec3 near, double fade) {
        if (id != 0) Fx3d.kill(level, id, near, fade);
    }

    static void killAll(ServerLevel level, int[] ids, Vec3 near, double fade) {
        if (ids == null) return;
        for (int id : ids) kill(level, id, near, fade);
    }

    /** 주인마다 이름 붙여 둔 모델 (태세의 도는 검 · 앞의 방패): 끝나면 drop */
    private static final Map<UUID, Map<String, int[]>> HELD = new HashMap<>();

    static void hold(ServerPlayer sp, String key, int[] ids) {
        drop(sp, key, 0.1);
        HELD.computeIfAbsent(sp.getUUID(), u -> new HashMap<>()).put(key, ids);
    }

    static void drop(ServerPlayer sp, String key, double fade) {
        Map<String, int[]> m = HELD.get(sp.getUUID());
        if (m == null) return;
        int[] ids = m.remove(key);
        if (ids != null) killAll(sp.level(), ids, sp.position(), fade);
        if (m.isEmpty()) HELD.remove(sp.getUUID());
    }

    /** 수명 동안 주인이 죽거나 나가면 지움 (life 토큰이 바뀜) — every 틱마다 살핌 */
    static void guard(ServerPlayer sp, String life, int[] ids, int ticks) {
        for (int t = 10; t < ticks; t += 10) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) killAll(sp.level(), ids, sp.position(), 0.1);
            });
        }
    }

    /** 시험: 이 직업 스킬이 띄운 입체 물체 수 (Fx3d.sent 와 같이 셈) */
    static int sent() {
        return Fx3d.sent;
    }
}
