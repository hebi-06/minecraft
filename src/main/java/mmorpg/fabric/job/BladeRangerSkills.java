package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.BlockFx;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-15 블레이드 레인저 (궁수 2차 반전 — 사용자 "에미아 같은 근접으로", claude/archer-remake.md): 1차를 뒤집음 — 멀리서 쏘는 대신 투영한 쌍검 (단조 불빛 날 ·
 * 주황 홈) 을 들고 파고들어 벰. 트윈 슬래시 · 블레이드 부메랑 · 라이징 엣지 · 포지 브레이커 · 블레이드 이클립스 (2026-11-16 — 셋째 · 다섯째 새 컨셉).
 * 원작의 이름 · 주문 · 쌍검 디자인은 쓰지 않음. 같은 적을 여러 번
 * 치는 것은 10틱 넘게 띄우고, 빠른 연타는 피해 한 번 · 숫자 여러 줄. 화면 흔들림 없음.
 * <p>
 * 2026-11-15 직업 전용 2D 그림 (사용자 "직업마다 전용으로 그릴래" — tools/jobvfx/forge.py · 그림판 br_*): 베기 = 달아오른 초승달 (휘두른 면에 고정 · 앞뒤 두 장 —
 * 예전 단색 참격 판 대신) · 쌍검 X 베기 · 쇳물 폭발 · 열 충격파 · 녹은 금 · 단조 불꽃 · 불티, 칼은 3D 그대로 + 주황 발광 (빛 번짐 후처리).
 */
final class BladeRangerSkills {
    private BladeRangerSkills() {}

    static void register() {
        JobSystem.register("bladeranger_twin", BladeRangerSkills::twin);
        JobSystem.register("bladeranger_boomerang", BladeRangerSkills::boomerang);
        JobSystem.register("bladeranger_storm", BladeRangerSkills::storm);
        JobSystem.register("bladeranger_forge", BladeRangerSkills::forge);
        JobSystem.register("bladeranger_ult", BladeRangerSkills::ult);
    }

    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, p, p.y), p.z);
    }

    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double u) {
        double v = 1 - u;
        return new Vec3(a.x * v * v + c.x * 2 * u * v + b.x * u * u, a.y * v * v + c.y * 2 * u * v + b.y * u * u, a.z * v * v + c.z * 2 * u * v + b.z * u * u);
    }

    /** 투영한 칼 한 자루 (단조 불빛 · 길이 len — appear 틱에 커짐 · fx 수명 life 틱) */
    static RuneSwords.Blade blade(ServerLevel level, Vec3 hilt, Vec3 dir, double len, int appear, int life) {
        Vec3 d = V.unit(dir);
        Vfx.Fx fx = RuneProps.fx(level, hilt, Math.max(appear + 6, life));
        PhantomArcher.Pal p = PhantomArcher.FORGE_PAL;
        RuneSwords.Blade b = RuneSwords.phantom(fx, p.sword(), hilt, d, PhantomArcher.faceOf(d), len, 0.05, p.blade(), p.gem());
        b.prop.outline(PhantomFx.FORGE.glow()); // 2026-11-15 직업 전용: 칼이 주황으로 번짐
        fx.on(Math.max(1, appear), () -> b.grow(1, 2));
        return b;
    }

    /**
     * 베기 호 2026-11-15 2D (사용자 "참격이나 잔상 남는 연출도 넣어서 그런거는 2D 연출로" · "잔상 느낌으로 진짜 조금만 천천히"): 가슴 c · 앞 f (가로) · 기울기 tilt
     * (오른쪽에 위를 섞음) · dirSign +1 왼 → 오른 · 반지름 r — 기운 판의 참격 판 (slash_basic — 날카로운 머리 · 두꺼운 잔상, 0.75초에 천천히 거둬짐)
     */
    static void slashArc(ServerLevel level, Vec3 c, Vec3 f, double tilt, int dirSign, double r, double[] glow, int sweep, int life) {
        Vec3 ff = V.unit(V.flat(f));
        if (V.len(ff) < 1e-3) ff = new Vec3(1, 0, 0);
        Vec3 v = V.unit(V.add(V.rightOf(ff), V.UP, tilt));
        // 2026-11-15 직업 전용: 달아오른 초승달 베기 (br_slash — 휘두른 면에 고정 · 앞뒤 두 장, 예전 단색 참격 판 slash_basic 대신)
        PhantomFx.slash(level, PhantomFx.FORGE, c, ff, V.scale(v, dirSign), r);
    }

    /** 2026-11-15 몸 둘레 휩쓰는 베기 (돌며 베기 · 블레이드 스톰 — 누운 면을 앞이 조금 들리게 lift): 가운데 c · 볼록한 쪽 f · 반지름 r · 휘두르는 쪽 dirSign */
    static void sweepArc(ServerLevel level, Vec3 c, Vec3 f, int dirSign, double r, double lift) {
        Vec3 ff = V.len(V.flat(f)) < 1e-3 ? new Vec3(0, 0, 1) : V.unit(V.flat(f));
        Vec3 side = V.add(V.scale(V.rightOf(ff), dirSign), V.UP, lift * dirSign);
        PhantomFx.slash(level, PhantomFx.FORGE, c, V.add(ff, V.UP, lift * 0.5), side, r);
    }

    /** 칼이 호를 따라 휘두름 (각 a0 → a1 · ticks 틱 — 자루는 가슴 c 에서 hilt 칸 바깥) */
    static void swing(RuneSwords.Blade b, Vec3 c, Vec3 f, double tilt, double a0, double a1, double hilt, int ticks) {
        Vec3 ff = V.unit(V.flat(f));
        Vec3 v = V.unit(V.add(V.rightOf(ff), V.UP, tilt));
        int n = Math.max(1, ticks);
        for (int i = 0; i <= n; i++) {
            double a = a0 + (a1 - a0) * i / n;
            Vec3 radial = V.add(V.scale(ff, Math.cos(a)), v, Math.sin(a));
            int ii = i;
            RuneKit.later(i, () -> b.pose(V.add(c, radial, hilt), radial, V.unit(V.cross(ff, v)), ii == 0 ? 0 : 1));
        }
    }

    /** 몸 앞 반원 (반지름 r · 앞 판 dot ≥ 0.2) 의 적 */
    static List<LivingEntity> front(ServerPlayer sp, Vec3 c, Vec3 f, double r) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 ff = V.unit(V.flat(f));
        for (LivingEntity e : Kit.around(sp, c, r, 2.2)) {
            Vec3 to = V.flat(V.sub(e.position(), c));
            if (V.len(to) < 0.6 || V.dot(V.unit(to), ff) >= 0.2) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------ 트윈 슬래시

    /**
     * 트윈 슬래시 (bladeranger_twin): 쌍검을 투영하며 앞으로 파고듦 (dash 칸 — 겨눈 적이 있으면 그 앞까지) → hitAt 틱부터 every 틱마다 쌍검이 엇갈려 네 번 벰 (호 넷 · 몸 앞
     * 반원 reach 칸). 피해는 첫 베기에 한 번 (cuts 합 — 숫자 줄 넷)
     */
    static boolean twin(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        List<Double> cuts = SwordmasterSkills.numbers(k, "cuts", List.of(1.75, 1.75, 1.75, 1.75));
        int n = cuts.size();
        double[] wts = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            wts[i] = cuts.get(i);
            total += cuts.get(i);
        }
        double mult = total;
        int hitAt = Math.max(3, (int) k.num("hitAt", 4));
        int every = Math.max(1, (int) k.num("every", 2));
        double reach = k.num("reach", 3.2);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 f = Kit.facingOf(sp);
        LivingEntity t0 = Kit.pickTarget(sp, k.num("range", 6), 30);
        double dash = k.num("dash", 2.5);
        if (t0 != null) dash = Math.max(0, Math.min(dash, V.hdist(sp.position(), t0.position()) - Kit.bodyPad(t0) - 1.2));
        if (dash > 0.2) JobMove.run(sp, JobMove.to(0, hitAt - 1, JobMove.ahead(sp, dash, 0), 1));
        SkillFx.play(level0, "entity.player.attack.sweep", sp.position(), 1.5, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.7, 0.6);
        Vec3 right = V.rightOf(f);
        PhantomFx.Lib L = PhantomFx.FORGE;
        RuneSwords.Blade[] bl = new RuneSwords.Blade[2];
        for (int s = 0; s < 2; s++) {
            Vec3 h = V.add(V.up(sp.position(), 1.1), right, (s == 0 ? -1 : 1) * 0.5);
            PhantomArcher.traceLines(level0, h, V.add(f, V.UP, 0.3), V.UP, 1.4, 0.2, L.trace());
            bl[s] = blade(level0, h, V.add(f, V.UP, 0.3), 1.4, 1, hitAt + n * every + 10);
        }
        for (int q = 0; q < n; q++) {
            int qq = q;
            RuneKit.later(hitAt + q * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = V.up(sp.position(), 1.1);
                Vec3 ff = Kit.facingOf(sp);
                double tilt = qq % 2 == 0 ? 0.55 : -0.55;
                int dir = qq % 2 == 0 ? 1 : -1;
                slashArc(level, V.add(c, ff, 0.2), ff, tilt, dir, 2.1, glow, 2, 6);
                swing(bl[qq % 2], c, ff, tilt, dir > 0 ? -1.3 : 1.3, dir > 0 ? 1.3 : -1.3, 0.35, 2);
                SkillFx.play(level, "entity.player.attack.sweep", c, 1.2 + qq * 0.1, 0.8);
                if (qq == n - 1) PhantomFx.flash(level, L, V.add(c, ff, 1.9), 1.6);
                if (qq != 0) return;
                for (LivingEntity e : front(sp, sp.position(), ff, reach))
                    JobKit.strikeSplit(sp, e, j, P, mult, n, every, wts, false, false, life, 0, d -> JobFx.hit(e, j, 0.9));
            });
        }
        RuneKit.later(hitAt + n * every + 3, () -> {
            for (RuneSwords.Blade b : bl) {
                PhantomFx.flash(sp.level(), L, b.hilt(), 0.5);
                b.remove();
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 블레이드 부메랑

    /**
     * 블레이드 부메랑 (bladeranger_boomerang): 투영한 쌍검을 양옆으로 던짐 → 바깥으로 휘어 (curve 칸) 겨눈 곳 (range 칸의 적 — 없으면 앞 fallback 칸) 에서 crossAt 틱에
     * X 자로 교차하며 벰 (cross · 둘레 radius 칸) → 몸이 그 자리로 끌려감 (순간 이동 몫 · pullAt → +5) → spinAt 틱에 칼을 받아 돌며 벰 (둘레 spinRadius 칸 spin). 묶였거나
     * 타고 있으면 쓰지 않음
     */
    static boolean boomerang(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        LivingEntity t0 = Kit.pickTarget(sp, k.num("range", 12), 20);
        Vec3[] spot = {t0 != null ? Kit.bodyPoint(t0) : V.up(ground(level0, V.add(c0, f, k.num("fallback", 7))), 1.0)};
        int crossAt = Math.max(4, (int) k.num("crossAt", 8));
        int pullAt = crossAt + 2;
        int spinAt = Math.max(crossAt + 10, (int) k.num("spinAt", 18));
        double curve = k.num("curve", 3.0);
        double len = k.num("len", 1.8);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        SkillFx.play(level0, "entity.player.attack.sweep", c0, 1.3, 0.8);
        SkillFx.play(level0, "item.trident.throw", c0, 1.1, 0.8);
        Vec3 right = V.rightOf(f);
        PhantomFx.Lib L = PhantomFx.FORGE;
        RuneSwords.Blade[] bl = new RuneSwords.Blade[2];
        Vec3[] start = new Vec3[2];
        Vec3[] prev = new Vec3[2];
        for (int s = 0; s < 2; s++) {
            start[s] = V.add(V.up(c0, 1.15), right, (s == 0 ? -1 : 1) * 0.5);
            PhantomArcher.traceLines(level0, start[s], f, V.UP, len, 0.25, L.trace());
            bl[s] = blade(level0, start[s], f, len, 1, spinAt + 12);
        }
        // 날아감 (2 → crossAt): 바깥으로 휘는 두 길 · 돎
        for (int t = 2; t <= crossAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (t0 != null && !t0.isRemoved() && t0.isAlive()) spot[0] = Kit.bodyPoint(t0);
                double u = (tt - 2) / (double) Math.max(1, crossAt - 2);
                for (int s = 0; s < 2; s++) {
                    double side = s == 0 ? -1 : 1;
                    Vec3 mid = V.lerp(start[s], spot[0], 0.5);
                    Vec3 ctl = V.add(V.up(mid, 0.5), V.rightOf(V.flat(V.sub(spot[0], start[s]))), side * curve);
                    Vec3 p = bez(start[s], ctl, spot[0], u);
                    Vec3 d = RuneSwords.rot(f, V.UP, side * tt * 70);
                    bl[s].pose(V.add(p, d, -len * 0.45), d, V.UP, 1);
                    if (prev[s] != null) PhantomArcher.streak2d(sp.level(), prev[s], p, 0.3, glow, core);
                    prev[s] = p;
                }
            });
        }
        Set<UUID> crossed = new HashSet<>();
        RuneKit.later(crossAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = spot[0];
            // 2026-11-15 직업 전용: 쌍검 X 베기 (br_xslash — 쓴 사람 쪽 1칸 앞) · 불티 별 · 불티
            Vec3 toMe = V.unit(V.sub(sp.getEyePosition(), c));
            PhantomFx.xslash(level, L, V.add(c, toMe, 1.0), 4.4, Kit.rand() * 16 - 8);
            PhantomFx.flash(level, L, c, 2.2);
            PhantomFx.embers(level, L, c, 12, 1.8, 0.4, 16);
            SkillFx.play(level, "item.trident.hit", c, 1.1, 0.8);
            for (LivingEntity e : Kit.around(sp, c, k.num("radius", 2), 2.0)) {
                crossed.add(e.getUUID());
                JobKit.strike(sp, e, j, P, k.num("cross", 2.8), false, false, life, 0, d -> JobFx.hit(e, j, 0.9));
            }
        });
        // 몸이 교차한 자리로 끌려감 (칼은 그 자리에서 돎)
        RuneKit.later(pullAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = spot[0];
            Vec3 from = sp.position();
            // 2026-11-16 V.flat 은 단위 벡터라 (길이 늘 1) 몸이 끌려가지 않았음 → 수평 벡터 그대로
            Vec3 dv = new Vec3(c.x - from.x, 0, c.z - from.z);
            double dl = V.len(dv);
            Vec3 land = dl > 1.4 ? V.add(from, V.unit(dv), dl - 1.3) : from;
            JobMove.run(sp, JobMove.to(0, 5, JobMove.at(ground(sp.level(), land)), 1));
            PhantomArcher.trail2d(sp.level(), V.up(from, 1.0), V.up(land, 1.0), 0.25, 0.9, glow, BlockFx.hot(glow)); // 2026-11-15 2D 잔상 (끌려간 길)
            SkillFx.play(sp.level(), "entity.enderman.teleport", from, 1.5, 0.5);
        });
        for (int t = crossAt + 1; t < spinAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 c = tt < pullAt + 5 ? spot[0] : V.up(sp.position(), 1.15);
                for (int s = 0; s < 2; s++) {
                    double side = s == 0 ? -1 : 1;
                    double a = tt * 0.9 + s * Math.PI;
                    Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 d = RuneSwords.rot(f, V.UP, side * tt * 70);
                    bl[s].pose(V.add(V.add(c, radial, 0.6), d, -len * 0.45), d, V.UP, 1);
                }
            });
        }
        RuneKit.later(spinAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = V.up(sp.position(), 1.1);
            Vec3 ff = Kit.facingOf(sp);
            double sr = k.num("spinRadius", 2.5);
            Vec3 rgt = V.rightOf(ff);
            // 2026-11-15 직업 전용: 돌며 베기 = 달아오른 초승달 둘 (앞 · 뒤 — 같은 쪽으로 돎) · 열 충격파
            sweepArc(level, c, ff, 1, sr * 0.95, 0.22);
            sweepArc(level, c, V.scale(ff, -1), 1, sr * 0.95, 0.22);
            PhantomFx.shock(level, L, PhantomFx.ground(level, sp.position()), sr * 2.6, 1);
            for (int s = 0; s < 2; s++) swing(bl[s], c, ff, 0.05, s * Math.PI, s * Math.PI + Math.PI * 1.6, 0.4, 3);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.9, 1.0);
            SkillFx.play(level, "item.trident.riptide_1", c, 1.3, 0.7);
            // 2026-11-15 시험 (CI 43644702711): 몸이 덜 끌려가면 (벽 · 막힘) 돌며 베기가 빗나갔음 → 칼이 교차한 자리 둘레 (그 자리로 끌려감) 의 적을 벰
            Vec3 at = spot[0] != null ? ground(level, spot[0]) : sp.position();
            for (LivingEntity e : Kit.around(sp, at, sr, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("spin", 1.4), false, false, life, 0, d -> JobFx.hit(e, j, 0.8));
            }
        });
        RuneKit.later(spinAt + 5, () -> {
            for (RuneSwords.Blade b : bl) PhantomArcher.shatter(sp.level(), b, 0.8, L);
        });
        return true;
    }

    // ------------------------------------------------------------ 라이징 엣지 (2026-11-16 — 옛 블레이드 스톰 대신)

    /**
     * 라이징 엣지 (bladeranger_storm — id 는 옛 블레이드 스톰 그대로, 2026-11-16 사용자 "아처 반전 3차 5차는 새로 컨셉 잡자 그리고 바꾸자" · claude/archer-remake.md):
     * 쌍검을 발 앞 땅에 꽂음 → 둘레 radius 칸에 달아오른 톱니 고리가 퍼지고 적 발밑에 칼 윤곽 (투영 선) · 달아오른 땅 → riseAt 틱에 땅을 뚫고 불빛 칼이 솟아 적을 띄움
     * (rise · 밀 수 있는 적만 뜸 — 보스 · 허수아비는 둔화) → 쓴 사람이 뜬 적들 쪽 위 height 칸으로 뛰어올라 공중에서 쌍검으로 엇갈려 네 번 벰 (airAt 에 air 네 줄 —
     * 뜬 적은 떠 있게 받쳐 줌) → 적을 끌고 내리꽂아 slamAt 에 땅을 부숨 (slam · 내린 자리 둘레 radius 칸 · 솟은 칼이 부서짐). 적이 없으면 앞 2.4칸 둘레에서.
     * 같은 적은 12틱 띄움 (6 → 18 → 30). 롤: 땅에서 솟는 칼로 띄우고 따라 올라가 공중 연타 → 내리꽂기
     */
    static boolean storm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 쓸 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 right0 = V.rightOf(f0);
        double R = k.num("radius", 3.5);
        int riseAt = Math.max(4, (int) k.num("riseAt", 6));
        int airAt = Math.max(riseAt + 11, (int) k.num("airAt", 18));
        int slamAt = Math.max(airAt + 11, (int) k.num("slamAt", 30));
        double H = k.num("height", 2.6);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.FORGE;
        // 솟을 자리: 둘레 적 (가까운 순 · 여섯까지) 의 발밑 — 없으면 앞 2.4칸 둘레 셋
        List<LivingEntity> near0 = new ArrayList<>(Kit.around(sp, c0, R, 2.5));
        near0.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(sp)));
        List<Vec3> spots = new ArrayList<>();
        for (int i = 0; i < Math.min(6, near0.size()); i++) spots.add(ground(level0, near0.get(i).position()));
        if (spots.isEmpty()) {
            for (int s = -1; s <= 1; s++) spots.add(ground(level0, V.add(V.add(c0, f0, 2.4 - Math.abs(s) * 0.5), right0, s * 1.4)));
        }
        // 뛰어오를 자리: 솟는 칼 자리 가운데의 쓴 사람 쪽 (가운데 · 1.1칸 앞) — 그 위 height 칸 (머리 위가 막히면 닿는 데까지)
        Vec3 mid = V.ZERO;
        for (Vec3 g : spots) mid = V.add(mid, g);
        mid = V.scale(mid, 1.0 / spots.size());
        Vec3 dm = new Vec3(mid.x - c0.x, 0, mid.z - c0.z);
        double dl = V.len(dm);
        Vec3 hub = ground(level0, dl > 1.2 ? V.add(c0, V.scale(dm, 1 / dl), dl - 1.1) : c0);
        Vec3 air = JobMove.upAt(sp, hub, H);
        Vec3 face0 = dl > 1.2 ? V.scale(dm, 1 / dl) : f0; // 공중에서 보는 쪽 (뜬 적들 쪽)
        // 0: 꽂음 — 쌍검이 손에서 발 앞 땅으로 · 발밑 섬광 · 달아오른 톱니 고리가 둘레로 퍼짐 (예고 — 내리꽂을 때까지) · 작은 열 충격파
        Vec3 g0 = PhantomFx.ground(level0, c0);
        PhantomFx.ring(level0, L, g0, R, riseAt, slamAt - riseAt);
        PhantomFx.shock(level0, L, g0, R * 1.5, 1);
        PhantomFx.flash(level0, L, V.add(V.up(c0, 0.35), f0, 0.6), 1.8);
        SkillFx.play(level0, "block.anvil.place", c0, 0.8, 0.9);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 1.4, 0.7);
        for (int s = 0; s < 2; s++) {
            double side = s == 0 ? -1 : 1;
            Vec3 h = V.add(V.add(V.up(c0, 1.15), right0, side * 0.45), f0, 0.3);
            Vec3 d = V.unit(V.add(V.add(V.DOWN, f0, 0.45), right0, side * 0.12));
            RuneSwords.Blade b = blade(level0, h, d, 1.5, 1, riseAt + 4);
            RuneKit.later(2, () -> b.pose(V.add(V.add(V.up(c0, 0.55), right0, side * 0.35), f0, 0.75), d, PhantomArcher.faceOf(d), 2));
            RuneKit.later(riseAt + 2, () -> PhantomArcher.shatter(sp.level(), b, 0.6, L));
        }
        // 솟을 칼: 자리마다 투영 선 (땅에서 위로 — 칼 윤곽) · 달아오른 땅 · 땅 밑의 칼
        List<RuneSwords.Blade> risers = new ArrayList<>();
        List<Vec3> rdir = new ArrayList<>();
        for (Vec3 g : spots) {
            Vec3 d = V.unit(new Vec3((Kit.rand() - 0.5) * 0.4, 1, (Kit.rand() - 0.5) * 0.4));
            rdir.add(d);
            PhantomArcher.traceLines(level0, V.up(g, 0.05), d, PhantomArcher.faceOf(d), 2.4, riseAt / 20.0 + 0.1, L.trace());
            PhantomFx.crackS(level0, L, g, 1.7);
            risers.add(blade(level0, V.add(g, d, -2.7), d, 2.4, 1, slamAt + 14));
        }
        // riseAt: 솟음 — 칼이 땅을 뚫고 솟음 · 불기둥 · 작은 쇳물 폭발 · 그을음 → 적을 띄움
        Set<UUID> lifted = new HashSet<>();
        List<LivingEntity> airborne = new ArrayList<>();
        RuneKit.later(riseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            for (int i = 0; i < risers.size(); i++) {
                Vec3 g = spots.get(i);
                Vec3 d = rdir.get(i);
                risers.get(i).pose(V.add(g, d, -0.35), d, PhantomArcher.faceOf(d), 2);
                PhantomFx.flame(level, L, g, 2.8, 1);
                if (RuneKit.fxQuota("riseBoom", 4)) PhantomFx.boomSmall(level, L, V.up(g, 0.6), 1.7);
                PhantomFx.dust(level, L, g, 1.7);
            }
            SkillFx.play(level, "item.trident.riptide_2", c0, 1.1, 0.9);
            SkillFx.play(level, "block.anvil.land", c0, 1.4, 0.5);
            SkillFx.play(level, "entity.generic.explode", c0, 1.6, 0.4);
            for (LivingEntity e : Kit.around(sp, c0, R, 2.5)) {
                if (!lifted.add(e.getUUID())) continue;
                airborne.add(e);
                if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.55), 0);
                else RuneKit.effect(e, "slowness", slamAt - riseAt + 6, JobKit.isBoss(e) ? 1 : 10);
                JobKit.strike(sp, e, j, P, k.num("rise", 2.1), false, false, life, 0, d -> JobFx.hit(e, j, 0.9));
            }
        });
        // 뛰어오름 → 공중에 머묾 → 내리꽂음 (몸) · 뜬 적을 받쳐 줌 (2틱마다 — 중력 몫만큼 올림) → 함께 내리꽂음
        JobMove.run(sp, JobMove.to(riseAt + 1, riseAt + 6, JobMove.at(air), JobMove.OUT), JobMove.hold(riseAt + 6, slamAt - 4),
                JobMove.to(slamAt - 4, slamAt, JobMove.down(sp, c0.y), JobMove.IN));
        JobKit.motionLater(sp, "bladeranger_storm:air", riseAt + 1, life);
        JobKit.motionLater(sp, "bladeranger_storm:slam", slamAt - 5, life);
        RuneKit.later(riseAt + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            PhantomArcher.trail2d(sp.level(), V.up(c0, 0.8), V.up(air, 0.8), 0.25, 0.9, glow, core); // 2026-11-16 솟구친 길 잔상
            SkillFx.play(sp.level(), "entity.player.attack.sweep", c0, 0.8, 0.9);
        });
        for (int t = riseAt + 7; t < slamAt - 4; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : airborne) if (e.isAlive() && !e.isRemoved() && JobKit.pushable(e)) Kit.launch(e, 0, 0.16, 0);
            });
        }
        // 공중 연타: 쌍검 둘 (손 — 뛰어오르며 투영) 이 엇갈려 네 번 (2틱마다) · 몸 둘레 달아오른 초승달 · 뜬 적마다 X 베기 → airAt 에 피해 (네 줄 — 적마다 한 번)
        RuneSwords.Blade[] tw = new RuneSwords.Blade[2];
        RuneKit.later(riseAt + 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = V.up(sp.position(), 1.1);
            Vec3 rt = V.rightOf(face0);
            for (int s = 0; s < 2; s++) {
                Vec3 h = V.add(c, rt, (s == 0 ? -1 : 1) * 0.5);
                PhantomArcher.traceLines(sp.level(), h, V.add(face0, V.UP, 0.4), V.UP, 1.5, 0.2, L.trace());
                tw[s] = blade(sp.level(), h, V.add(face0, V.UP, 0.4), 1.5, 1, slamAt - riseAt + 4);
            }
        });
        int cut0 = riseAt + 7;
        for (int q = 0; q < 4; q++) {
            int qq = q;
            RuneKit.later(cut0 + q * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = V.up(sp.position(), 1.1);
                double tilt = qq % 2 == 0 ? 0.65 : -0.65;
                int dir = qq % 2 == 0 ? 1 : -1;
                slashArc(level, V.add(c, face0, 0.2), face0, tilt, dir, 2.3, glow, 2, 6);
                if (tw[qq % 2] != null) swing(tw[qq % 2], c, face0, tilt, dir > 0 ? -1.3 : 1.3, dir > 0 ? 1.3 : -1.3, 0.35, 2);
                SkillFx.play(level, "entity.player.attack.sweep", c, 1.1 + qq * 0.12, 0.85);
                int shown = 0;
                for (LivingEntity e : airborne) {
                    if (!e.isAlive() || e.isRemoved() || shown >= 4) continue;
                    if (!RuneKit.fxQuota("riseCross", 6)) break;
                    Vec3 b = Kit.bodyPoint(e);
                    Vec3 toEye = V.unit(V.sub(sp.getEyePosition(), b));
                    PhantomFx.xslash(level, L, V.add(b, toEye, 0.55), 2.4 + qq * 0.25, (qq % 2 == 0 ? 20 : -20) + Kit.rand() * 16 - 8);
                    shown++;
                }
            });
        }
        RuneKit.later(airAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            List<Double> air4 = SwordmasterSkills.numbers(k, "air", List.of(2.1, 2.1, 2.1, 2.1));
            double[] wts = new double[air4.size()];
            double sum = 0;
            for (int i = 0; i < wts.length; i++) {
                wts[i] = air4.get(i);
                sum += wts[i];
            }
            double mult = sum;
            for (LivingEntity e : airborne) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strikeSplit(sp, e, j, P, mult, wts.length, 2, wts, false, false, life, 0, d -> {
                    if (RuneKit.fxQuota("riseHit", 6)) JobFx.hit(e, j, 0.9);
                    if (RuneKit.fxQuota("riseSpark", 6)) PhantomFx.embers(level, L, Kit.bodyPoint(e), 5, 1.1, 0.3, 12);
                });
            }
            PhantomFx.flash(level, L, V.add(V.up(sp.position(), 1.1), face0, 1.6), 2.0);
        });
        // 내리꽂음: 적도 땅으로 · 내려오는 길 잔상 · 쌍검을 머리 위로
        RuneKit.later(slamAt - 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : airborne) if (e.isAlive() && !e.isRemoved() && JobKit.pushable(e)) Kit.launch(e, 0, -1.1, 0);
            Vec3 c = V.up(sp.position(), 1.6);
            for (int s = 0; s < 2; s++) {
                if (tw[s] == null) continue;
                Vec3 d = V.unit(V.add(V.add(V.DOWN, face0, 0.5), V.rightOf(face0), (s == 0 ? -1 : 1) * 0.25));
                tw[s].pose(V.add(V.add(c, V.rightOf(face0), (s == 0 ? -1 : 1) * 0.35), face0, 0.3), d, PhantomArcher.faceOf(d), 3);
            }
            PhantomArcher.streak2d(sp.level(), V.up(air, 1.0), V.up(hub, 0.6), 0.25, glow, core);
        });
        // slamAt: 땅을 부숨 — 쇳물 폭발 · 넓은 열 충격파 · 그을린 땅 · 솟은 칼이 부서짐 → 내린 자리 둘레 radius 칸 피해
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 g = PhantomFx.ground(level, sp.position());
            PhantomFx.boom(level, L, V.up(g, 0.3), 1.7);
            PhantomFx.shock(level, L, g, R * 2.7, 1);
            PhantomFx.flash(level, L, V.up(g, 0.5), 3.0);
            SkillFx.play(level, "entity.generic.explode", g, 0.9, 1.0);
            SkillFx.play(level, "block.anvil.land", g, 0.6, 1.0);
            SkillFx.play(level, "item.mace.smash_ground_heavy", g, 0.8, 0.9);
            for (RuneSwords.Blade b : risers) PhantomArcher.shatter(level, b, 1.1, L);
            risers.clear();
            for (RuneSwords.Blade b : tw) if (b != null) PhantomArcher.shatter(level, b, 0.8, L);
            Set<UUID> hit = new HashSet<>();
            for (LivingEntity e : Kit.around(sp, sp.position(), R, 3)) {
                hit.add(e.getUUID());
                JobKit.strike(sp, e, j, P, k.num("slam", 2.1), false, false, life, 0, d -> JobFx.hit(e, j, 1.1));
            }
            // 뜬 채 멀리 밀려난 적도 (내린 자리에서 R 밖 — 띄운 적은 같이 떨어짐)
            for (LivingEntity e : airborne) {
                if (!e.isAlive() || e.isRemoved() || hit.contains(e.getUUID()) || e.distanceToSqr(sp) > (R + 2.5) * (R + 2.5)) continue;
                JobKit.strike(sp, e, j, P, k.num("slam", 2.1), false, false, life, 0, d -> JobFx.hit(e, j, 1.1));
            }
        });
        RuneKit.later(slamAt + 6, () -> {
            for (RuneSwords.Blade b : risers) b.remove();
        });
        return true;
    }

    // ------------------------------------------------------------ 포지 브레이커

    /**
     * 포지 브레이커 (bladeranger_forge): 머리 위에 거대한 칼 (len 칸) 을 투영해 들어 올림 (느려짐) → leapAt 틱에 앞으로 뛰어 hitAt 틱에 앞 (ahead 칸) 에 내려침 (둘레
     * hitRadius 칸 hit) → waveAt 틱에 칼이 부서지며 앞 부채꼴 (radius 칸 · cone 도) 땅에서 불빛 칼이 솟음 (wave · 둘레 적마다 한 번)
     */
    static boolean forge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int leapAt = Math.max(6, (int) k.num("leapAt", 12));
        int hitAt = Math.max(leapAt + 2, (int) k.num("hitAt", 16));
        int waveAt = Math.max(hitAt + 10, (int) k.num("waveAt", 26));
        double len = k.num("len", 4.2);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        RuneKit.effect(sp, "slowness", leapAt + 2, 2);
        LivingEntity t0 = Kit.pickTarget(sp, k.num("range", 8), 30);
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 h0 = V.up(V.add(sp.position(), f0, -0.2), 2.3);
        Vec3 d0 = V.unit(V.add(V.UP, f0, -0.35));
        PhantomFx.Lib L = PhantomFx.FORGE;
        PhantomArcher.traceLines(level0, h0, d0, V.rightOf(f0), len, 0.5, L.trace());
        RuneSwords.Blade b = blade(level0, h0, d0, len, 5, waveAt + 20);
        // 2026-11-15 직업 전용: 치켜든 칼끝에 모이는 열 (br_charge — 칼끝을 따라감, 0.6 → 2.2)
        Vfx.Fx chargeFx = Vfx.at(level0, V.add(h0, d0, len), hitAt + 2);
        Vfx.Sprite[] heat = {null};
        SkillFx.play(level0, "block.anvil.use", sp.position(), 0.7, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 0.8, 1.0);
        // 들어 올림 (뒤로 젖힘) · 칼끝에 불빛
        for (int t = 1; t < hitAt - 2; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 f = Kit.facingOf(sp);
                Vec3 h = V.up(V.add(sp.position(), f, -0.2), 2.3);
                double back = Math.min(1, tt / (double) leapAt);
                Vec3 d = V.unit(V.add(V.UP, f, -0.35 - 0.5 * back));
                b.pose(h, d, V.rightOf(f), 1);
                if (tt == 6) heat[0] = PhantomFx.charge(chargeFx, L, b.tip(), 0.6);
                if (heat[0] != null) heat[0].to(b.tip(), 0.6 + (tt - 6) * 0.17, 1);
            });
        }
        RuneKit.later(leapAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double leap = k.num("leap", 2.0);
            if (t0 != null && !t0.isRemoved()) leap = Math.max(0, Math.min(leap, V.hdist(sp.position(), t0.position()) - Kit.bodyPad(t0) - 2.2));
            JobMove.run(sp, JobMove.hop(0, hitAt - leapAt, JobMove.ahead(sp, leap, 0), 0.9));
        });
        Vec3[] spot = {null};
        RuneKit.later(hitAt - 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 f = Kit.facingOf(sp);
            boolean on = t0 != null && !t0.isRemoved() && t0.isAlive() && V.hdist(sp.position(), t0.position()) < k.num("range", 8) + 2;
            Vec3 c = on ? ground(sp.level(), t0.position()) : ground(sp.level(), V.add(sp.position(), f, k.num("ahead", 2.6)));
            spot[0] = c;
            Vec3 d = V.unit(V.add(f, V.UP, -0.9));
            b.pose(V.add(V.up(c, 0.2), d, -b.reach() * 0.75), d, V.rightOf(f), 2);
            if (heat[0] != null) heat[0].remove();
            PhantomArcher.streak2d(sp.level(), V.up(sp.position(), 3.0), V.up(c, 0.3), 0.3, glow, core);
        });
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life) || spot[0] == null) return;
            ServerLevel level = sp.level();
            Vec3 c = spot[0];
            // 2026-11-15 직업 전용: 내려친 자리 작은 쇳물 폭발 · 불티 별 · 열 충격파 (예전 상자 별 · 상자 터짐 · 상자 고리 · 상자 조각 대신)
            PhantomFx.boomSmall(level, L, V.up(c, 0.5), 2.6);
            PhantomFx.flash(level, L, V.up(c, 0.6), 2.4);
            PhantomFx.shock(level, L, PhantomFx.ground(level, c), 6.4, 1);
            SkillFx.play(level, "block.anvil.land", c, 0.6, 1.0);
            SkillFx.play(level, "entity.generic.explode", c, 1.3, 0.7);
            for (LivingEntity e : Kit.around(sp, c, k.num("hitRadius", 2.2), 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("hit", 5.6), false, false, life, 0, d -> JobFx.hit(e, j, 1.1));
            }
        });
        // 부서짐 → 앞 부채꼴에 불빛 칼이 솟음
        int rise = 6;
        RuneKit.later(waveAt - rise, () -> {
            if (!RuneKit.sameLife(sp, life) || spot[0] == null) return;
            ServerLevel level = sp.level();
            PhantomArcher.shatter(level, b, 2.0, L);
            Vec3 f = Kit.facingOf(sp);
            double rad = k.num("radius", 5);
            double cone = Math.toRadians(k.num("cone", 80)) / 2;
            int m = Math.max(6, (int) k.num("spikes", 12));
            for (int q = 0; q < m; q++) {
                double u = (q + 0.5) / m;
                double a = (Kit.rand() * 2 - 1) * cone;
                double dist = 1.2 + rad * Math.sqrt(u);
                Vec3 dir = V.add(V.scale(f, Math.cos(a)), V.rightOf(f), Math.sin(a));
                Vec3 g = ground(level, V.add(spot[0], dir, dist - 1.0));
                Vec3 up = V.unit(V.add(V.UP, dir, 0.35));
                int at = (int) (u * (rise - 1));
                RuneKit.later(at, () -> {
                    RuneSwords.Blade s = blade(sp.level(), V.add(g, up, -1.4), up, 1.6 + Kit.rand() * 0.8, 1, 26);
                    RuneKit.later(2, () -> s.pose(V.add(g, up, -0.2), up, PhantomArcher.faceOf(up), 3));
                    if (RuneKit.fxQuota("forgeFlame", 10)) {
                        // 2026-11-15 직업 전용: 솟는 칼 둘레 단조 불꽃 (br_flame — 세로로 섬) · 그을음
                        PhantomFx.flame(sp.level(), L, PhantomFx.ground(sp.level(), g), 1.8 + Kit.rand() * 0.8, 1);
                        PhantomFx.dust(sp.level(), L, PhantomFx.ground(sp.level(), g), 1.3);
                    }
                    RuneKit.later(14, () -> PhantomArcher.shatter(sp.level(), s, 0.8, L));
                });
            }
            SkillFx.play(level, "item.trident.riptide_3", spot[0], 1.0, 1.0);
        });
        RuneKit.later(waveAt, () -> {
            if (!RuneKit.sameLife(sp, life) || spot[0] == null) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            double rad = k.num("radius", 5);
            Vec3 mid = V.add(spot[0], f, rad * 0.5);
            PhantomFx.boom(level, L, V.up(mid, 0.1), rad * 0.6); // 2026-11-15 직업 전용: 쇳물 대폭발 (예전 상자 폭발 대신)
            Ult.flash(level, mid, glow, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.04, 0.25}), sp);
            SkillFx.play(level, "entity.generic.explode", mid, 0.8, 1.2);
            double cone = Math.cos(Math.toRadians(k.num("cone", 80)) / 2 + 0.2);
            for (LivingEntity e : Kit.around(sp, spot[0], rad + 1.0, 3)) {
                Vec3 to = V.flat(V.sub(e.position(), spot[0]));
                if (V.len(to) > 1.0 && V.dot(V.unit(to), f) < cone) continue;
                JobKit.strike(sp, e, j, P, k.num("wave", 11.2), false, true, life, 0, d -> JobFx.hit(e, j, 1.3));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 블레이드 이클립스 (2026-11-16 — 옛 아이언 템페스트 대신)

    /** 블레이드 이클립스 고리의 한 자세: 칼 i 의 자루 · 칼끝 방향 */
    private record RingPose(Vec3 hilt, Vec3 dir) {}

    /**
     * 고리의 칼 i (n 자루 중) 자세: 가운데 c (발) · 돌림 phase (도) · 반지름 r · 높이 h · 바깥을 보는지 (out — 아니면 칼끝이 안쪽) · 위로 드는 몫 up
     */
    private static RingPose ringPose(Vec3 c, int i, int n, double phase, double r, double h, boolean out, double up, double len) {
        double a = Math.toRadians(phase) + Math.PI * 2 * i / n;
        Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
        Vec3 d = out ? V.unit(V.add(radial, V.UP, up)) : V.unit(V.add(V.scale(radial, -1), V.UP, -up));
        // 자루 = 반지름 자리에서 칼 길이의 반만큼 안쪽 (밖을 볼 때) · 바깥쪽 (안을 볼 때) — 고리의 칼날 가운데가 반지름 r
        Vec3 mid = V.up(V.add(c, radial, r), h);
        return new RingPose(V.add(mid, d, -len * 0.45), d);
    }

    /**
     * 블레이드 이클립스 (bladeranger_ult — id 는 옛 아이언 템페스트 그대로, 2026-11-16 사용자 "아처 반전 3차 5차는 새로 컨셉 잡자"): 머리 위에 칼 swords 자루가 바깥을
     * 향한 빛무리 고리 (일식의 테 — 단조 톱니 고리 · 발밑 단조 마법진) 로 투영됨 → 세 번: 고리가 radius 칸까지 퍼지며 휩쓸고 (wave — 둘레 radius + 0.5칸의 적
     * 모두) 끝에서 칼끝을 안으로 돌려 머리 위로 되돌아오며 다시 벰 (wave · 밀 수 있는 적을 쓴 사람 쪽으로 끌어옴 — 쌍검이 서로 끌리듯). 고리마다 다름: 첫째 가슴 높이 ·
     * 둘째 위아래 두 겹이 거꾸로 돎 · 셋째 빠른 소용돌이. 피해 틱 = first + i × every (i 0 · 2 · 4 퍼짐, 1 · 3 · 5 되돌아옴) → 칼이 손으로 모여 쌍검이
     * 날개처럼 거대해짐 (giantLen 칸) → 겨눈 적 (range 칸 — 없으면 앞 3.5칸) 앞으로 파고들어 finalAt 에 거대한 X 로 벰 (final · 둘레 finalRadius 칸 · 큰 숫자).
     * 롤: 탈론 R (칼날 고리가 퍼졌다 돌아옴) × 3 → 마무리 일격
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double R = k.num("radius", 9);
        int waves = Math.max(2, (int) k.num("waves", 6));
        int every = Math.max(11, (int) k.num("every", 12));
        int first = Math.max(8, (int) k.num("first", 14));
        int lastWave = first + (waves - 1) * every;
        int finalAt = Math.max(lastWave + 12, (int) k.num("finalAt", 88));
        int n = Math.max(8, (int) k.num("swords", 24));
        double len = k.num("len", 1.9);
        double haloR = 1.25;
        double haloH = 2.75;
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.FORGE;
        Ult.sky(level0, c0, "rpg:ult_night", finalAt + 24);
        Ult.flash(level0, c0, glow, Opt.of("radius", 10.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level0, "block.respawn_anchor.charge", c0, 0.8, 1.0);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 0.6, 1.4);
        // 발밑 단조 마법진 (톱니 · 엇갈린 칼 · 푸른 강철 고리 — 마지막 X 전에 닫힘: 큰 폭발 그림과 겹치면 그리는 차례에 마법진이 네모나게 가려졌음) · 퍼지는 열 충격파
        Vec3 g0 = PhantomFx.ground(level0, c0);
        BlockFx.sigil(level0, V.up(c0, 0.04), 2.6, PhantomArcher.SIGIL_FORGE, 8, finalAt - 16, 60);
        PhantomFx.shock(level0, L, g0, 6.0, 1);
        // 머리 위 빛무리: 칼 n 자루가 차례로 투영됨 (투영 선 — 둘에 하나) · 테 고리
        List<RuneSwords.Blade> ring = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RingPose p = ringPose(c0, i, n, 0, haloR, haloH, true, 0.35, len);
            if (i % 2 == 0) PhantomArcher.traceLines(level0, p.hilt(), p.dir(), PhantomArcher.faceOf(p.dir()), len, 0.35, L.trace());
            ring.add(blade(level0, p.hilt(), p.dir(), len, 1 + i * 6 / n, finalAt + 10));
        }
        PhantomFx.ring(level0, L, V.up(c0, haloH - 0.1), haloR + 0.6, 6, first + 2);
        // 고리 한 번: 퍼짐 (s → s + 6 · 2틱마다 한 걸음) → 끝에서 칼끝을 안으로 (s + 8) → 되돌아옴 (s + 10 → s + 16) → 칼끝을 다시 밖으로 (s + 18)
        double[] phase = {0};
        Vec3[][] lastTip = new Vec3[1][n];
        for (int cyc = 0; cyc < (waves + 1) / 2; cyc++) {
            int cc = cyc;
            int s = first - 4 + cyc * every * 2;
            double spin = cc == 2 ? 30 : cc == 1 ? 20 : 15;
            for (int st = 1; st <= 3; st++) {
                int stt = st;
                // 퍼짐
                RuneKit.later(s + st * 2, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    double u = stt / 3.0;
                    double e = 1 - (1 - u) * (1 - u);
                    moveRing(sp, ring, lastTip, phase, cc, spin, haloR + (R - 0.6 - haloR) * e, haloH, 1.2, true, len, stt == 3 ? 0.05 : 0.35 * (1 - e) + 0.05,
                            glow, core);
                });
                // 되돌아옴 (칼끝이 안쪽)
                RuneKit.later(s + 10 + st * 2, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    double u = stt / 3.0;
                    double e = u * u;
                    moveRing(sp, ring, lastTip, phase, cc, -spin, R - 0.6 + (haloR + 0.9 - (R - 0.6)) * e, 1.2, haloH, false, len, 0.15, glow, core);
                });
            }
            // 끝에서 칼끝을 안으로 돌림 · 테두리 톱니 고리 · 칼마다 불티 (몇)
            RuneKit.later(s + 8, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                for (int i = 0; i < ring.size(); i++) {
                    double h = cc == 1 ? (i % 2 == 0 ? 0.7 : 1.9) : 1.2;
                    double ph = cc == 1 && i % 2 == 1 ? -phase[0] : phase[0];
                    RingPose p = ringPose(c, i, ring.size(), ph, R - 0.6, h, false, 0.15, len);
                    ring.get(i).pose(p.hilt(), p.dir(), PhantomArcher.faceOf(p.dir()), 2);
                }
                PhantomFx.ring(level, L, PhantomFx.ground(level, c), R, 2, 6);
                SkillFx.play(level, "item.trident.return", c, 1.0 + cc * 0.1, 0.9);
            });
            // 머리 위로 돌아와 칼끝을 다시 밖으로 (마지막 고리 뒤는 손으로 모임)
            if (cyc < (waves + 1) / 2 - 1) {
                RuneKit.later(s + 18, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 c = sp.position();
                    for (int i = 0; i < ring.size(); i++) {
                        double ph = cc == 1 && i % 2 == 1 ? -phase[0] : phase[0];
                        RingPose p = ringPose(c, i, ring.size(), ph, haloR, haloH, true, 0.35, len);
                        ring.get(i).pose(p.hilt(), p.dir(), PhantomArcher.faceOf(p.dir()), 2);
                    }
                    PhantomFx.ring(sp.level(), L, V.up(c, haloH - 0.1), haloR + 0.6, 2, 4);
                });
            }
            // 퍼지는 열 고리 (가슴 높이 — 칼날 고리를 따라 퍼짐) · 소리
            RuneKit.later(s + 1, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                PhantomFx.shock(level, L, V.up(PhantomFx.ground(level, c), 1.15), R * 2.1, 1);
                PhantomFx.flash(level, L, V.up(c, haloH), 2.2);
                SkillFx.play(level, "item.trident.riptide_3", c, 0.9 + cc * 0.12, 0.8);
                SkillFx.play(level, "entity.player.attack.sweep", c, 0.7 + cc * 0.1, 1.0);
            });
        }
        // 피해: first + i × every — 짝수 = 퍼지는 칼날 · 홀수 = 되돌아오는 칼날 (밀 수 있는 적을 끌어옴)
        for (int w = 0; w < waves; w++) {
            int ww = w;
            RuneKit.later(first + w * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                for (LivingEntity e : Kit.around(sp, c, R + 0.5, 4)) {
                    JobKit.strike(sp, e, j, P, k.num("wave", 7), false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("eclipseHit", 8)) JobFx.hit(e, j, 1.0);
                        if (RuneKit.fxQuota("eclipseSpark", 6)) PhantomFx.embers(level, L, Kit.bodyPoint(e), 5, 1.2, 0.3, 12);
                    });
                    if (ww % 2 == 1) {
                        double dist = V.hdist(c, e.position());
                        if (dist > 2.6) JobKit.reel(sp, e, sp::position, Math.min(k.num("reel", 2.5), dist - 2.4), 6, 2.2, life);
                    }
                }
            });
        }
        // 모임: 칼이 손으로 날아들어 사라짐 → 쌍검이 날개처럼 거대해짐
        int gather = lastWave + 2;
        JobKit.motionLater(sp, "bladeranger_ult:catch", gather, life);
        RuneKit.later(gather, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 f = Kit.facingOf(sp);
            Vec3 rt = V.rightOf(f);
            for (int i = 0; i < ring.size(); i++) {
                RuneSwords.Blade b = ring.get(i);
                Vec3 hand = V.add(V.up(sp.position(), 1.2), rt, (i % 2 == 0 ? -1 : 1) * 0.5);
                Vec3 d = V.unit(V.sub(hand, b.hilt()));
                b.pose(V.add(hand, d, -0.3), d, PhantomArcher.faceOf(d), 3 + i % 3);
                b.grow(0.3, 4);
            }
            SkillFx.play(sp.level(), "block.anvil.use", sp.position(), 0.7, 1.2);
        });
        RuneKit.later(gather + 6, () -> {
            for (int i = 0; i < ring.size(); i++) {
                if (i % 4 == 0) PhantomFx.flash(sp.level(), L, ring.get(i).hilt(), 0.8);
                ring.get(i).remove();
            }
            ring.clear();
        });
        double giant = k.num("giantLen", 4.8);
        RuneSwords.Blade[] wings = new RuneSwords.Blade[2];
        RuneKit.later(gather + 3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 f = Kit.facingOf(sp);
            Vec3 rt = V.rightOf(f);
            Vec3 c = V.up(sp.position(), 1.2);
            for (int s = 0; s < 2; s++) {
                double side = s == 0 ? -1 : 1;
                Vec3 h = V.add(c, rt, side * 0.5);
                Vec3 d = V.unit(V.add(V.add(V.UP, rt, side * 0.85), f, -0.35));
                PhantomArcher.traceLines(sp.level(), h, d, PhantomArcher.faceOf(d), giant, 0.4, L.trace());
                // 날개처럼 천천히 자람 (blade 의 저절로 자람은 40틱 뒤 — 그 전에 부서짐): 0.05 → 0.4 (2틱) → 1 (6틱)
                RuneSwords.Blade b = blade(sp.level(), h, d, giant, 40, finalAt - gather + 14);
                RuneKit.later(1, () -> b.grow(0.4, 2));
                RuneKit.later(3, () -> b.grow(1, 6));
                wings[s] = b;
            }
            PhantomFx.flash(sp.level(), L, V.up(sp.position(), 2.4), 2.6);
            SkillFx.play(sp.level(), "item.trident.thunder", sp.position(), 1.6, 0.35);
        });
        // 마지막: 겨눈 적 앞으로 파고들어 거대한 X
        Vec3[] spot = {null};
        JobKit.motionLater(sp, "bladeranger_ult:final", finalAt - 6, life);
        RuneKit.later(finalAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            LivingEntity e = Kit.pickTarget(sp, k.num("range", 10), 30);
            if (e == null) {
                List<LivingEntity> in = new ArrayList<>(Kit.around(sp, sp.position(), R, 4));
                in.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(sp)));
                e = in.isEmpty() ? null : in.get(0);
            }
            spot[0] = e != null ? ground(level, e.position()) : ground(level, V.add(sp.position(), Kit.facingOf(sp), 3.5));
            Vec3 from = sp.position();
            Vec3 dv = new Vec3(spot[0].x - from.x, 0, spot[0].z - from.z);
            double dl = V.len(dv);
            if (dl > 2.4) {
                Vec3 land = ground(level, V.add(from, V.scale(dv, 1 / dl), dl - 1.7));
                JobMove.run(sp, JobMove.to(0, 4, JobMove.at(land), JobMove.OUT));
                PhantomArcher.trail2d(level, V.up(from, 1.0), V.up(land, 1.0), 0.2, 1.0, glow, core);
            }
            // 날개 쌍검을 머리 위 뒤로 크게 젖힘
            Vec3 f = dl > 1e-3 ? V.scale(dv, 1 / dl) : Kit.facingOf(sp);
            Vec3 rt = V.rightOf(f);
            Vec3 c = V.up(sp.position(), 1.4);
            for (int s = 0; s < 2; s++) {
                if (wings[s] == null) continue;
                double side = s == 0 ? -1 : 1;
                Vec3 d = V.unit(V.add(V.add(V.UP, rt, side * 0.55), f, -0.75));
                wings[s].pose(V.add(c, rt, side * 0.45), d, PhantomArcher.faceOf(d), 3);
            }
            SkillFx.play(level, "entity.enderman.teleport", from, 1.4, 0.4);
        });
        RuneKit.later(finalAt - 1, () -> {
            if (!RuneKit.sameLife(sp, life) || spot[0] == null) return;
            Vec3 f0 = V.flat(V.sub(spot[0], sp.position()));
            Vec3 rt = V.rightOf(f0);
            Vec3 c = V.up(sp.position(), 1.3);
            for (int s = 0; s < 2; s++) {
                if (wings[s] == null) continue;
                double side = s == 0 ? -1 : 1;
                Vec3 d = V.unit(V.add(V.add(f0, rt, -side * 0.7), V.UP, -0.55));
                wings[s].pose(V.add(c, rt, side * 0.4), d, PhantomArcher.faceOf(d), 2);
            }
        });
        RuneKit.later(finalAt, () -> {
            if (spot[0] == null) return;
            ServerLevel level = sp.level();
            Vec3 f0 = V.flat(V.sub(spot[0], sp.position()));
            Vec3 c = V.up(sp.position(), 1.2);
            double fr = k.num("finalRadius", 4.5);
            // 거대한 X: 엇갈린 달아오른 초승달 둘 (몸 앞을 크게) · 겨눈 자리의 X 베기 · 쇳물 대폭발 · 열 충격파
            PhantomFx.slash(level, L, c, V.add(f0, V.UP, 0.1), V.add(V.rightOf(f0), V.UP, 0.85), fr * 1.05);
            RuneKit.later(1, () -> PhantomFx.slash(sp.level(), L, c, V.add(f0, V.UP, 0.1), V.add(V.rightOf(f0), V.UP, -0.85), fr * 1.05));
            Vec3 b = V.up(spot[0], 1.2);
            PhantomFx.xslash(level, L, V.add(b, V.unit(V.sub(sp.getEyePosition(), b)), 0.9), 7.0, Kit.rand() * 10 - 5);
            PhantomFx.boom(level, L, V.up(spot[0], 0.1), fr * 0.62);
            Ult.flash(level, spot[0], glow, Opt.of("radius", 8.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(level, "entity.generic.explode", spot[0], 0.7, 1.4);
            SkillFx.play(level, "block.anvil.land", spot[0], 0.5, 1.2);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.6, 1.0);
            for (RuneSwords.Blade w : wings) if (w != null) RuneKit.later(8, () -> PhantomArcher.shatter(sp.level(), w, 2.2, L));
            if (!RuneKit.sameLife(sp, life)) return;
            Set<UUID> hit = new HashSet<>();
            for (LivingEntity e : Kit.around(sp, spot[0], fr, 4)) {
                hit.add(e.getUUID());
                JobKit.strike(sp, e, j, P, k.num("final", 14), false, true, life, 0, d -> JobFx.hit(e, j, 1.4));
            }
            // 날개가 휩쓴 몸 앞 반원 (쓴 사람 둘레 — 겨눈 자리 밖에 선 적)
            for (LivingEntity e : front(sp, sp.position(), f0, fr)) {
                if (hit.add(e.getUUID())) JobKit.strike(sp, e, j, P, k.num("final", 14), false, true, life, 0, d -> JobFx.hit(e, j, 1.4));
            }
        });
        RuneKit.later(finalAt + 14, () -> {
            for (RuneSwords.Blade b : ring) b.remove();
            ring.clear();
        });
        return true;
    }

    /**
     * 블레이드 이클립스 고리 한 걸음: 칼 모두를 반지름 r · 높이 h0 → h1 (퍼짐이면 머리 위 → 가슴 · 되돌아옴이면 가슴 → 머리 위 — 걸음의 몫은 r 이 정함) 자리로 2틱 보간 ·
     * 돌림 phase 에 spin 도 더함 · 둘째 고리는 위아래 두 겹이 거꾸로 돎. 셋에 하나 칼끝 자리에서 이어지는 잔상 줄기
     */
    private static void moveRing(ServerPlayer sp, List<RuneSwords.Blade> ring, Vec3[][] lastTip, double[] phase, int cyc, double spin, double r, double h0, double h1,
            boolean out, double len, double up, double[] glow, double[] core) {
        if (ring.isEmpty()) return;
        Vec3 c = sp.position();
        phase[0] += spin;
        int n = ring.size();
        double R = Math.max(r, 0.01);
        for (int i = 0; i < n; i++) {
            double ph = cyc == 1 && i % 2 == 1 ? -phase[0] : phase[0];
            double h = cyc == 1 ? (i % 2 == 0 ? 0.7 : 1.9) : 1.2;
            // 높이: 퍼짐은 반지름이 클수록 가슴 높이로 · 되돌아옴은 작을수록 머리 위로
            double k = out ? Math.min(1, R / 6.0) : 1 - Math.min(1, (R - 1.5) / 6.0);
            double hh = out ? h0 + (h - h0) * k : h + (h1 - h) * Math.max(0, k);
            RingPose p = ringPose(c, i, n, ph, R, hh, out, up, len);
            RuneSwords.Blade b = ring.get(i);
            b.pose(p.hilt(), p.dir(), PhantomArcher.faceOf(p.dir()), 2);
            Vec3 tip = V.add(p.hilt(), p.dir(), len * 0.9);
            if (i % 3 == 0 && lastTip[0][i] != null && RuneKit.fxQuota("eclipseTrail", 10)) PhantomArcher.streak2d(sp.level(), lastTip[0][i], tip, 0.2, glow, core);
            lastTip[0][i] = tip;
        }
    }
}
