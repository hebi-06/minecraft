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
 * 주황 홈) 을 들고 파고들어 벰. 트윈 슬래시 · 블레이드 부메랑 · 블레이드 스톰 · 포지 브레이커 · 아이언 템페스트. 원작의 이름 · 주문 · 쌍검 디자인은 쓰지 않음. 같은 적을 여러 번
 * 치는 것은 10틱 넘게 띄우고, 빠른 연타는 피해 한 번 · 숫자 여러 줄. 화면 흔들림 없음.
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
        RuneSwords.Blade b = RuneSwords.phantom(fx, hilt, d, PhantomArcher.faceOf(d), len, 0.05, p.blade(), p.gem());
        fx.on(Math.max(1, appear), () -> b.grow(1, 2));
        return b;
    }

    /** 베기 호: 가슴 c · 앞 f (가로) · 기울기 tilt (오른쪽에 위를 섞음) · dirSign +1 왼 → 오른 · 반지름 r */
    static void slashArc(ServerLevel level, Vec3 c, Vec3 f, double tilt, int dirSign, double r, double[] glow, int sweep, int life) {
        Vec3 ff = V.unit(V.flat(f));
        if (V.len(ff) < 1e-3) ff = new Vec3(1, 0, 0);
        Vec3 v = V.unit(V.add(V.rightOf(ff), V.UP, tilt));
        double a = dirSign > 0 ? -1.3 : 1.3;
        BlockFx.arc(level, c, ff, v, r, a, -a, 11, 0.42, glow, sweep, life);
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
        RuneSwords.Blade[] bl = new RuneSwords.Blade[2];
        for (int s = 0; s < 2; s++) {
            Vec3 h = V.add(V.up(sp.position(), 1.1), right, (s == 0 ? -1 : 1) * 0.5);
            PhantomArcher.traceLines(level0, h, V.add(f, V.UP, 0.3), V.UP, 1.4, 0.2);
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
                if (qq == n - 1) BlockFx.star(level, V.add(c, ff, 1.6), 1.4, core);
                if (qq != 0) return;
                for (LivingEntity e : front(sp, sp.position(), ff, reach))
                    JobKit.strikeSplit(sp, e, j, P, mult, n, every, wts, false, false, life, 0, d -> JobFx.hit(e, j, 0.9));
            });
        }
        RuneKit.later(hitAt + n * every + 3, () -> {
            for (RuneSwords.Blade b : bl) {
                BlockFx.star(sp.level(), b.hilt(), 0.5, core);
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
        RuneSwords.Blade[] bl = new RuneSwords.Blade[2];
        Vec3[] start = new Vec3[2];
        Vec3[] prev = new Vec3[2];
        for (int s = 0; s < 2; s++) {
            start[s] = V.add(V.up(c0, 1.15), right, (s == 0 ? -1 : 1) * 0.5);
            PhantomArcher.traceLines(level0, start[s], f, V.UP, len, 0.25);
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
            PhantomArcher.cross2d(level, c, V.sub(c, sp.position()), 2.2, glow, core);
            BlockFx.star(level, c, 2.0, core);
            SkillFx.sparks(level, c, core, 10, 5);
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
            Vec3 dv = V.flat(V.sub(c, from));
            double L = V.len(dv);
            Vec3 land = L > 1.4 ? V.add(from, V.unit(dv), L - 1.3) : from;
            JobMove.run(sp, JobMove.to(0, 5, JobMove.at(ground(sp.level(), land)), 1));
            BlockFx.beam(sp.level(), V.up(from, 1.0), V.up(land, 1.0), 0.2, glow, 7);
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
            BlockFx.arc(level, c, ff, rgt, sr * 0.9, -Math.PI, 0, 12, 0.45, glow, 2, 7);
            BlockFx.arc(level, c, ff, rgt, sr * 0.9, 0, Math.PI, 12, 0.45, glow, 2, 7);
            BlockFx.ring(level, V.up(sp.position(), 0.05), 0.4, sr * 1.2, 18, 0.25, glow, 6);
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
            for (RuneSwords.Blade b : bl) {
                BlockFx.star(sp.level(), b.hilt(), 0.5, core);
                b.remove();
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 블레이드 스톰

    /**
     * 블레이드 스톰 (bladeranger_storm): 투영한 칼 swords 자루가 몸 둘레 (orbit 칸) 를 돌며 따라다님 → first 틱부터 gap 틱마다 (hits 번) 둘레 radius 칸의 적을 벰 (적마다
     * hit — 숫자 두 줄) · 둥근 베기 호 · 불티
     */
    static boolean storm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int n = Math.max(3, (int) k.num("swords", 6));
        int hits = Math.max(1, (int) k.num("hits", 6));
        int gap = Math.max(10, (int) k.num("gap", 10));
        int first = Math.max(6, (int) k.num("first", 6));
        int end = first + (hits - 1) * gap + 4;
        double orbit = k.num("orbit", 2.0);
        double r = k.num("radius", 3.2);
        double len = k.num("len", 1.6);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.2, 0.9);
        SkillFx.play(level0, "item.trident.riptide_2", sp.position(), 1.4, 0.6);
        BlockFx.ring(level0, V.up(sp.position(), 0.05), 0.4, orbit * 1.4, 18, 0.25, glow, 6);
        RuneSwords.Blade[] bl = new RuneSwords.Blade[n];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 h = V.add(V.up(sp.position(), 0.9), radial, orbit * 0.5);
            Vec3 tan = new Vec3(-Math.sin(a), 0.15, Math.cos(a));
            bl[i] = blade(level0, h, tan, len, 1 + i / 2, end + 10);
        }
        // 돎 (2틱마다 자리 · 2틱 보간)
        for (int t = 2; t <= end; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 c = V.up(sp.position(), 0.9 + 0.15 * Math.sin(tt * 0.3));
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n + tt * 0.32;
                    Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 tan = V.unit(new Vec3(-Math.sin(a), 0.2, Math.cos(a)));
                    bl[i].pose(V.add(V.add(c, radial, orbit), tan, -len * 0.4), tan, radial, 2);
                }
            });
        }
        for (int h = 0; h < hits; h++) {
            int hh = h;
            RuneKit.later(first + h * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = V.up(sp.position(), 0.9);
                Vec3 ff = Kit.facingOf(sp);
                Vec3 u = V.add(V.scale(ff, Math.cos(hh * 1.1)), V.rightOf(ff), Math.sin(hh * 1.1));
                Vec3 v = V.rightOf(V.unit(u));
                BlockFx.arc(level, c, V.unit(u), v, r * 0.85, -Math.PI, 0, 12, 0.38, glow, 2, 6);
                BlockFx.arc(level, c, V.unit(u), v, r * 0.85, 0, Math.PI, 12, 0.38, glow, 2, 6);
                if (hh == hits - 1) BlockFx.ring(level, V.up(sp.position(), 0.05), 0.5, r * 1.3, 24, 0.3, glow, 7);
                SkillFx.play(level, "entity.player.attack.sweep", c, 0.9 + hh * 0.06, 0.9);
                for (LivingEntity e : Kit.around(sp, sp.position(), r, 2.2)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("hit", 2.1), 2, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("bladeStormHit", 8)) JobFx.hit(e, j, 0.8);
                        if (RuneKit.fxQuota("bladeStormSpark", 8)) SkillFx.sparks(level, Kit.bodyPoint(e), core, 4, 4);
                    });
                }
            });
        }
        RuneKit.later(end + 2, () -> {
            for (RuneSwords.Blade b : bl) PhantomArcher.shatter(sp.level(), b, 0.9);
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
        PhantomArcher.traceLines(level0, h0, d0, V.rightOf(f0), len, 0.5);
        RuneSwords.Blade b = blade(level0, h0, d0, len, 5, waveAt + 20);
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
                if (tt % 3 == 0 && tt >= 6) BlockFx.star(sp.level(), b.tip(), 0.4 + tt * 0.03, glow);
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
            PhantomArcher.streak2d(sp.level(), V.up(sp.position(), 3.0), V.up(c, 0.3), 0.3, glow, core);
        });
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life) || spot[0] == null) return;
            ServerLevel level = sp.level();
            Vec3 c = spot[0];
            BlockFx.star(level, V.up(c, 0.6), 2.4, core);
            BlockFx.burst(level, V.up(c, 0.4), 1.5, glow, core, 10);
            BlockFx.ring(level, V.up(c, 0.05), 0.4, 3.2, 24, 0.35, glow, 7);
            PhantomArcher.shards(level, V.up(c, 0.5), 10, 7.0, 0.4);
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
            PhantomArcher.shatter(level, b, 2.0);
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
                    if (RuneKit.fxQuota("forgeFlame", 10)) BlockFx.flame(sp.level(), g, 1.6, glow, core, 9);
                    RuneKit.later(14, () -> PhantomArcher.shatter(sp.level(), s, 0.8));
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
            PhantomArcher.blast2d(level, V.up(mid, 0.8), rad * 0.7, PhantomArcher.FORGE_PAL);
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

    // ------------------------------------------------------------ 아이언 템페스트

    /**
     * 아이언 템페스트 (bladeranger_ult): 붉은 노을 · 둘레 (field 칸) 땅에 칼 swords 자루가 투영되어 꽂힘 → first 틱부터 every 틱마다 (waves 번) 둘레 radius 칸에서 적을
     * 골라 (차례로 · 없으면 앞) 그 앞으로 몸이 날아들며 땅의 칼을 뽑아 X 로 벰 (그 둘레 cut 칸의 적마다 wave — 숫자 두 줄) → finalAt − 12 에 뛰어올라 머리 위에 거대한 칼을
     * 투영 → finalAt 에 내려침 (둘레 finalRadius 칸 final · 큰 숫자)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double R = k.num("radius", 10);
        int waves = (int) k.num("waves", 6);
        int every = Math.max(10, (int) k.num("every", 10));
        int first = Math.max(8, (int) k.num("first", 16));
        int finalAt = Math.max(first + (waves - 1) * every + 12, (int) k.num("finalAt", 80));
        double cut = k.num("cut", 2.6);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, c0, "rpg:ult_sunset", finalAt + 30);
        Ult.flash(level0, c0, glow, Opt.of("radius", 10.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level0, "block.anvil.use", c0, 0.6, 1.2);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 0.6, 1.4);
        BlockFx.ring(level0, V.up(c0, 0.1), 0.5, k.num("field", 7), 40, 0.3, glow, 12);
        BlockFx.halo(level0, V.up(c0, 0.6), k.num("field", 7), 36, 0.5, glow, 8, finalAt);
        // 둘레 땅에 칼이 꽂힘 (뽑히면 그 자리 칼이 사라짐)
        int m = Math.max(8, (int) k.num("swords", 20));
        List<RuneSwords.Blade> field = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            double a = Math.PI * 2 * i / m + Kit.rand() * 0.2;
            double rr = k.num("field", 7) * (0.8 + Kit.rand() * 0.25);
            Vec3 g = ground(level0, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), rr));
            Vec3 d = V.unit(new Vec3((Kit.rand() - 0.5) * 0.5, -1, (Kit.rand() - 0.5) * 0.5));
            double len = 2.0 + Kit.rand() * 0.8;
            int at = 2 + i * 10 / m;
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneSwords.Blade b = blade(sp.level(), V.add(V.up(g, 2.5), d, -len * 0.62), d, len, 1, finalAt + 20 - at);
                RuneKit.later(2, () -> b.pose(V.add(g, d, -len * 0.62), d, PhantomArcher.faceOf(d), 3));
                field.add(b);
                if (RuneKit.fxQuota("tempestPlant", 8)) BlockFx.star(sp.level(), V.up(g, 0.3), 0.7, glow);
            });
        }
        // 물결: 적 앞으로 날아들어 X 베기
        LivingEntity[] last = {null};
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int hitAt = first + w * every;
            RuneKit.later(hitAt - 5, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                List<LivingEntity> in = Kit.around(sp, c0, R, 5);
                LivingEntity e = in.isEmpty() ? null : in.get(ww % in.size());
                last[0] = e;
                Vec3 from = sp.position();
                Vec3 to;
                if (e != null) {
                    Vec3 dv = V.flat(V.sub(e.position(), from));
                    double L = V.len(dv);
                    to = L > 1.6 ? V.add(from, V.unit(dv), L - Kit.bodyPad(e) - 1.2) : from;
                } else to = V.add(from, V.rightOf(Kit.facingOf(sp)), (ww % 2 == 0 ? 1 : -1) * 2.0);
                Vec3 land = ground(level, to);
                JobMove.run(sp, JobMove.to(0, 4, JobMove.at(land), 1));
                BlockFx.beam(level, V.up(from, 1.0), V.up(land, 1.0), 0.22, glow, 7);
                // 땅의 칼 하나가 손으로 날아듦
                if (!field.isEmpty()) {
                    RuneSwords.Blade b = field.remove(0);
                    Vec3 hand = V.up(land, 1.2);
                    Vec3 d = V.unit(V.sub(hand, b.hilt()));
                    b.pose(V.add(hand, d, -0.3), d, PhantomArcher.faceOf(d), 4);
                    RuneKit.later(5, () -> PhantomArcher.shatter(sp.level(), b, 0.8));
                }
                SkillFx.play(level, "entity.enderman.teleport", from, 1.6, 0.4);
            });
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                LivingEntity e = last[0];
                Vec3 c = e != null && !e.isRemoved() ? Kit.bodyPoint(e) : V.up(V.add(sp.position(), Kit.facingOf(sp), 1.5), 1.0);
                PhantomArcher.cross2d(level, c, V.sub(c, sp.getEyePosition()), 1.9, glow, core);
                JobTraits9.twinBlades(level, sp, c, j, 1.5, 6);
                SkillFx.play(level, "entity.player.attack.sweep", c, 0.9 + ww * 0.06, 1.0);
                for (LivingEntity x : Kit.around(sp, c, cut, 2.5)) {
                    JobKit.strikeSplit(sp, x, j, P, k.num("wave", 7), 2, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("tempestHit", 8)) JobFx.hit(x, j, 1.0);
                    });
                }
            });
        }
        // 마지막: 뛰어올라 거대한 칼을 내려침
        Vec3[] spot = {null};
        RuneSwords.Blade[] giant = {null};
        JobKit.motionLater(sp, "bladeranger_ult:final", finalAt - 12, life);
        RuneKit.later(finalAt - 12, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            LivingEntity e = last[0] != null && !last[0].isRemoved() && last[0].isAlive() ? last[0] : Kit.pickTarget(sp, 12, 30);
            spot[0] = e != null ? ground(level, e.position()) : ground(level, V.add(sp.position(), Kit.facingOf(sp), 3));
            JobMove.run(sp, JobMove.hop(0, 12, JobMove.at(ground(level, V.add(spot[0], V.flat(V.sub(sp.position(), spot[0])).length() > 0.1
                    ? V.unit(V.flat(V.sub(sp.position(), spot[0]))) : Kit.facingOf(sp), 1.6))), 2.6));
            Vec3 top = V.up(spot[0], 6.5);
            PhantomArcher.traceLines(level, top, V.DOWN, V.rightOf(Kit.facingOf(sp)), k.num("giantLen", 6.0), 0.5);
            giant[0] = blade(level, V.up(top, 2.0), V.DOWN, k.num("giantLen", 6.0), 3, 40);
            RuneKit.later(4, () -> {
                if (giant[0] != null) giant[0].pose(top, V.DOWN, PhantomArcher.faceOf(V.DOWN), 6);
            });
            BlockFx.halo(level, V.up(spot[0], 6.0), 3.8, 26, 0.5, glow, 4, 10);
            SkillFx.play(level, "block.anvil.use", spot[0], 0.5, 1.4);
        });
        RuneKit.later(finalAt - 2, () -> {
            if (spot[0] == null || giant[0] == null) return;
            RuneSwords.Blade b = giant[0];
            b.pose(V.up(spot[0], b.reach() * 0.25), V.DOWN, PhantomArcher.faceOf(V.DOWN), 2);
            PhantomArcher.streak2d(sp.level(), V.up(spot[0], 7.0), spot[0], 0.3, glow, core);
        });
        RuneKit.later(finalAt, () -> {
            if (spot[0] == null) return;
            ServerLevel level = sp.level();
            double fr = k.num("finalRadius", 4);
            PhantomArcher.blast(level, j, V.up(spot[0], 1.0), fr);
            BlockFx.ring(level, V.up(spot[0], 0.05), fr * 0.3, fr * 1.8, 32, 0.45, PhantomArcher.FORGE_PAL.fire(), 10);
            Ult.flash(level, spot[0], glow, Opt.of("radius", 8.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(level, "entity.generic.explode", spot[0], 0.7, 1.4);
            SkillFx.play(level, "block.anvil.land", spot[0], 0.5, 1.2);
            if (giant[0] != null) RuneKit.later(10, () -> PhantomArcher.shatter(sp.level(), giant[0], 2.5));
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : Kit.around(sp, spot[0], fr, 4)) JobKit.strike(sp, e, j, P, k.num("final", 14), false, true, life, 0, d -> JobFx.hit(e, j, 1.4));
        });
        RuneKit.later(finalAt + 16, () -> {
            for (RuneSwords.Blade b : field) b.remove();
            field.clear();
        });
        return true;
    }
}
