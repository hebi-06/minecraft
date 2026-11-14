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
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-15 팬텀 아처 직업 스킬 (궁수 리메이크 — 사용자 "아처 1차는 … 에미야 시로 같은 느낌으로", claude/archer-remake.md): 소드 볼트 · 크로스 팽 · 블레이드
 * 레인 · 팬텀 버스트 · 소드 생추어리. 칼은 모두 투영 (빛의 선 → 3D 칼 — {@link PhantomArcher#project}) 이고 맞은 적에게 꽂힘 (특성 환영 무기). 같은 적을 여러 번
 * 치는 것은 10틱 넘게 띄우고, 빠른 연타는 피해 한 번 · 숫자 여러 줄 (JobKit.strikeSplit). 화면 흔들림 없음.
 */
final class PhantomArcherSkills {
    private PhantomArcherSkills() {}

    static void register() {
        JobSystem.register("archer_volley", PhantomArcherSkills::volley);
        JobSystem.register("archer_cross", PhantomArcherSkills::cross);
        JobSystem.register("archer_blades", PhantomArcherSkills::blades);
        JobSystem.register("archer_burst", PhantomArcherSkills::burst);
        JobSystem.register("archer_ult", PhantomArcherSkills::ult);
    }

    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double u) {
        double v = 1 - u;
        return new Vec3(a.x * v * v + c.x * 2 * u * v + b.x * u * u, a.y * v * v + c.y * 2 * u * v + b.y * u * u, a.z * v * v + c.z * 2 * u * v + b.z * u * u);
    }

    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, p, p.y), p.z);
    }

    // ------------------------------------------------------------ 소드 볼트

    /**
     * 소드 볼트 (archer_volley): 겨눈 적 (range 칸 — 없으면 쓰지 않음) — 활 위에 칼 swords 자루가 부채꼴로 투영됨 (빛의 선 → 3D 칼, 마지막 칼이 big 배 크게) → lead
     * 틱부터 every 틱마다 한 자루씩 날아가 꽂힘 (꽂힘 +1씩). 피해는 첫 칼이 닿을 때 한 번 (swords 합 — 숫자 자루 수 줄, 줄마다 그 칼의 몫)
     */
    static boolean volley(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 24), 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        List<Double> sw = SwordmasterSkills.numbers(k, "swords", List.of(2.0, 2.0, 3.0));
        int n = sw.size();
        int every = Math.max(1, (int) k.num("every", 3));
        int lead = Math.max(2, (int) k.num("lead", 3));
        double speed = k.num("speed", 60);
        double mult = 0;
        double[] wts = new double[n];
        for (int i = 0; i < n; i++) {
            mult += sw.get(i);
            wts[i] = sw.get(i);
        }
        double total = mult;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        // 동작: 쏘며 뒤로 짧게 뜀
        JobMove.run(sp, JobMove.hop(0, lead + (n - 1) * every + 3, JobMove.ahead(sp, -k.num("back", 1.0), 0), 0.4));
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.7, 0.7);
        RuneSwords.Blade[] blades = new RuneSwords.Blade[n];
        for (int i = 0; i < n; i++) {
            int ii = i;
            double len = k.num("len", 1.3) * (i == n - 1 ? k.num("big", 1.6) : 1.0);
            int fireAt = lead + i * every;
            Vec3 h0 = hover(sp, i, n);
            blades[i] = PhantomArcher.project(level0, h0, V.sub(Kit.bodyPoint(target), h0), len, 1 + i, fireAt + 40, true);
            // 쏘기 전까지 활 위를 따라다니며 적을 겨눔
            for (int t = 1; t < fireAt; t++) {
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life) || target.isRemoved()) return;
                    Vec3 h = hover(sp, ii, n);
                    Vec3 d = V.unit(V.sub(Kit.bodyPoint(target), h));
                    blades[ii].pose(h, d, PhantomArcher.faceOf(d), 1);
                });
            }
            RuneKit.later(fireAt, () -> {
                RuneSwords.Blade b = blades[ii];
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) {
                    b.remove();
                    return;
                }
                ServerLevel level = sp.level();
                Vec3 to = V.up(Kit.bodyPoint(target), (ii - (n - 1) / 2.0) * 0.18);
                Vec3 from = b.hilt();
                Vec3 d = V.unit(V.sub(to, from));
                int t = Math.max(1, ArrowFx.flyTicks(from, to, speed));
                b.pose(V.add(to, d, -b.reach() * 0.75), d, PhantomArcher.faceOf(d), t);
                RuneKit.flyStreak(level, from, to, speed, glow, core, 0.6 + len * 0.2, 0.18);
                SkillFx.play(level, "item.trident.throw", from, 1.25 + ii * 0.1, 0.8);
                if (ii == 0) JobKit.strikeSplit(sp, target, j, P, total, n, every, wts, false, false, life, t, dd -> {});
                RuneKit.later(t, () -> {
                    b.remove();
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    Vec3 at = Kit.bodyPoint(target);
                    PhantomArcher.lodge(sp, j, target, 1, from);
                    JobFx.hit(target, j, ii == n - 1 ? 1.1 : 0.8);
                    JobFx.glint(lv, at, ii == n - 1 ? 0.9 : 0.5, glow, core, 0.22, Opt.empty());
                    SkillFx.sparks(lv, at, core, ii == n - 1 ? 10 : 5, 4);
                    if (ii == n - 1) {
                        SkillFx.burst(lv, at, glow, 1.1, 0.22);
                        SkillFx.play(lv, "item.trident.hit", at, 0.8, 1.0);
                    }
                });
            });
        }
        return true;
    }

    /** 소드 볼트의 i 번째 칼이 떠 있는 자리 (활 위 — 부채꼴) */
    private static Vec3 hover(ServerPlayer sp, int i, int n) {
        Vec3 f = sp.getViewVector(1F);
        Vec3 right = V.rightOf(V.flat(f));
        Vec3 hand = RuneKit.bowHand(sp);
        double u = i - (n - 1) / 2.0;
        // 2026-11-15 1인칭 영상: 활 손 뒤 0.2칸은 눈에 너무 가까워 칼자루가 화면 위를 덮었음 → 활 앞 0.7칸 · 조금 더 위
        return V.add(V.up(V.add(hand, f, 0.7), 0.6 + 0.12 * Math.abs(u) + (i == n - 1 ? 0.15 : 0)), right, u * 0.55);
    }

    // ------------------------------------------------------------ 크로스 팽

    /**
     * 크로스 팽 (archer_cross): 뒤로 leap 칸 공중제비로 물러나며 투영한 쌍검을 양옆으로 던짐 → 바깥으로 휘어 (curve 칸) 날아가 겨눈 곳 (range 칸의 적 — 없으면 앞
     * fallback 칸) 에서 crossAt 틱에 X 자로 교차하며 벰 (cross · 둘레 radius 칸 · 꽂힘 +1) → 돌아오며 backAt 틱에 지나는 길 (폭 width) 을 다시 벰 (back) → 손에 돌아와
     * 사라짐. 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean cross(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        LivingEntity t0 = Kit.pickTarget(sp, k.num("range", 12), 20);
        Vec3[] spot = {t0 != null ? Kit.bodyPoint(t0) : V.up(ground(level0, V.add(c0, f, k.num("fallback", 8))), 1.0)};
        int crossAt = Math.max(4, (int) k.num("crossAt", 8));
        int backAt = Math.max(crossAt + 10, (int) k.num("backAt", 18));
        int home = backAt + 4;
        double curve = k.num("curve", 3.5);
        double len = k.num("len", 1.3);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        // 몸: 뒤로 공중제비 (순간 이동 몫)
        JobMove.run(sp, JobMove.hop(0, 8, JobMove.ahead(sp, -k.num("leap", 5), 0), k.num("lift", 1.3)));
        SkillFx.play(level0, "entity.player.attack.sweep", c0, 1.4, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 1.8, 0.6);
        Vfx.Fx fx = RuneProps.fx(level0, c0, home + 8);
        Vec3 right = V.rightOf(f);
        RuneSwords.Blade[] bl = new RuneSwords.Blade[2];
        Vec3[] start = new Vec3[2];
        Vec3[] prev = new Vec3[2]; // 2026-11-15 컨셉아트: 휘어 나는 쌍검 뒤의 이어진 빛 띠 (틱마다 앞 자리 → 지금 자리)
        for (int s = 0; s < 2; s++) {
            double side = s == 0 ? -1 : 1;
            start[s] = V.add(V.up(c0, 1.15), right, side * 0.45);
            PhantomArcher.traceLines(level0, start[s], f, V.UP, len, 0.25);
            bl[s] = RuneSwords.phantom(fx, start[s], f, V.UP, len, 0.05, PhantomArcher.BLADE, PhantomArcher.GEM);
            int ss = s;
            fx.on(1, () -> bl[ss].grow(1, 2));
        }
        // 날아감 (2 → crossAt): 바깥으로 휘는 두 길 · 부메랑처럼 돎
        for (int t = 2; t <= crossAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (t0 != null && !t0.isRemoved() && t0.isAlive()) spot[0] = Kit.bodyPoint(t0);
                double u = (tt - 2) / (double) Math.max(1, crossAt - 2);
                for (int s = 0; s < 2; s++) {
                    double side = s == 0 ? -1 : 1;
                    Vec3 mid = V.lerp(start[s], spot[0], 0.5);
                    Vec3 ctl = V.add(V.up(mid, 0.6), V.rightOf(V.flat(V.sub(spot[0], start[s]))), side * curve);
                    Vec3 p = bez(start[s], ctl, spot[0], u);
                    Vec3 d = RuneSwords.rot(f, V.UP, side * tt * 62);
                    bl[s].pose(V.add(p, d, -len * 0.45), d, V.UP, 1);
                    if (prev[s] != null) RuneKit.flyStreak(sp.level(), prev[s], p, 20 * V.len(V.sub(p, prev[s])) + 1, glow, core, 0.7, 0.22);
                    prev[s] = p;
                }
            });
        }
        // 교차 (crossAt): X 자 섬광 · 둘레 적 베기 · 꽂힘
        RuneKit.later(crossAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = spot[0];
            Vec3 r = V.rightOf(V.flat(V.sub(c, sp.position())));
            Vec3 a = V.unit(V.add(r, V.UP, 1));
            Vec3 b = V.unit(V.add(r, V.UP, -1));
            JobFx.line(level, V.add(c, a, 1.6), V.add(c, a, -1.6), glow, core, 0.32, Opt.of("alpha", 1.0));
            JobFx.line(level, V.add(c, b, 1.6), V.add(c, b, -1.6), glow, core, 0.32, Opt.of("alpha", 1.0));
            JobFx.glint(level, c, 1.0, glow, core, 0.25, Opt.empty());
            SkillFx.sparks(level, c, core, 10, 5);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.9, 1.0);
            SkillFx.play(level, "item.trident.hit", c, 1.1, 0.8);
            for (LivingEntity e : Kit.around(sp, c, k.num("radius", 2), 2.0)) {
                JobKit.strike(sp, e, j, P, k.num("cross", 2.8), false, false, life, 0, d -> {
                    PhantomArcher.lodge(sp, j, e, 1, c);
                    JobFx.hit(e, j, 0.9);
                });
            }
        });
        // 돌아옴 (crossAt + 1 → home): 쓴 사람 쪽으로 · backAt 에 지나는 길을 벰
        for (int t = crossAt + 1; t <= home; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double u = (tt - crossAt) / (double) (home - crossAt);
                Vec3 hand = V.up(sp.position(), 1.15);
                for (int s = 0; s < 2; s++) {
                    double side = s == 0 ? -1 : 1;
                    Vec3 mid = V.lerp(spot[0], hand, 0.5);
                    Vec3 ctl = V.add(V.up(mid, 0.4), V.rightOf(V.flat(V.sub(hand, spot[0]))), -side * curve * 0.7);
                    Vec3 p = bez(spot[0], ctl, V.add(hand, V.rightOf(f), side * 0.45), u);
                    Vec3 d = RuneSwords.rot(f, V.UP, side * tt * 62);
                    bl[s].pose(V.add(p, d, -len * 0.45), d, V.UP, 1);
                    if (prev[s] != null) RuneKit.flyStreak(sp.level(), prev[s], p, 20 * V.len(V.sub(p, prev[s])) + 1, glow, core, 0.5, 0.16);
                    prev[s] = p;
                }
            });
        }
        RuneKit.later(backAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 hand = V.up(sp.position(), 1.0);
            Vec3 dv = V.sub(hand, spot[0]);
            double L = V.len(dv);
            if (L < 0.5) return;
            for (Kit.LineHit h : Kit.lineHits(sp, spot[0], V.scale(dv, 1 / L), L, k.num("width", 1.5))) {
                LivingEntity e = h.e();
                JobKit.strike(sp, e, j, P, k.num("back", 1.4), false, false, life, 0, d -> JobFx.hit(e, j, 0.7));
            }
        });
        RuneKit.later(home, () -> {
            for (RuneSwords.Blade b : bl) {
                JobFx.glint(sp.level(), b.hilt(), 0.4, PhantomArcher.TRACE, PhantomArcher.TRACE_CORE, 0.15, Opt.empty());
                b.remove();
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 블레이드 레인

    /**
     * 블레이드 레인 (archer_blades): 조준한 곳 (range 칸) 하늘 (sky 칸 위) 에 칼이 투영됨 → first 틱부터 gap 틱마다 (waves 번) 칼 물결이 쏟아져 땅에 꽂힘 (반지름
     * radius 칸 · 안의 적마다 wave — 숫자 두 줄 · 꽂힘 +1, 처음 맞은 적은 root 틱 묶임 · 보스는 둔화) → burstAt 틱에 꽂힌 칼이 한꺼번에 터짐 (burst)
     */
    static boolean blades(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 center = ground(level0, Kit.aimPoint(sp, k.num("range", 24)));
        double r = k.num("radius", 4.5);
        int waves = (int) k.num("waves", 5);
        int gap = Math.max(10, (int) k.num("gap", 10));
        int first = Math.max(4, (int) k.num("first", 6));
        int burstAt = Math.max(first + (waves - 1) * gap + 10, (int) k.num("burstAt", 56));
        int per = Math.max(2, (int) k.num("per", 6));
        double sky = k.num("sky", 7.0);
        double len = k.num("len", 1.6);
        int fall = 4;
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        Set<UUID> rooted = new HashSet<>();
        List<RuneSwords.Blade> stuck = new ArrayList<>();
        Vfx.Fx fx = RuneProps.fx(level0, center, burstAt + 20);
        // 동작: 제자리에서 뛰어올라 하늘로 쏨 — 칼 셋이 하늘로 솟음
        JobMove.run(sp, JobMove.hop(0, 10, from -> from, 0.8));
        for (int i = 0; i < 3; i++) {
            Vec3 from = RuneKit.bowHand(sp);
            Vec3 up = V.add(V.up(from, 8 + i), V.flat(sp.getViewVector(1F)), 2 + i * 0.6);
            RuneSwords.Blade b = PhantomArcher.project(level0, from, V.sub(up, from), 1.2, 1, 12, false);
            b.pose(V.add(up, V.unit(V.sub(up, from)), -1.0), V.sub(up, from), V.UP, 6);
            RuneKit.later(7, b::remove);
            RuneKit.flyStreak(level0, from, up, 40, glow, JobKit.core(j), 0.7, 0.18);
        }
        SkillFx.play(level0, "item.trident.riptide_1", sp.position(), 1.4, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", center, 1.2, 1.0);
        // 땅: 과녁 문양 · 하늘: 투영 고리 (칼이 나오는 곳)
        SkillFx.rune(level0, V.up(center, 0.05), Opt.of("size", r, "life", (burstAt + 8) / 20.0, "color", glow, "alpha", 0.7, "rate", 30.0, "grow", 0.0));
        RuneKit.zonePulse(level0, center, glow, r, 0.45);
        JobFx.ring(level0, V.up(center, sky), r * 0.8, j, false, (burstAt - 4) / 20.0, Opt.of("alpha", 0.75));
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int hitAt = first + w * gap;
            int spawnAt = Math.max(0, hitAt - fall - 3);
            RuneKit.later(spawnAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                List<Vec3> spots = new ArrayList<>();
                for (LivingEntity e : Kit.around(sp, center, r, 4)) if (spots.size() < per / 2) spots.add(e.position());
                while (spots.size() < per) {
                    double a = Kit.rand() * Math.PI * 2;
                    spots.add(V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * Math.sqrt(Kit.rand())));
                }
                for (Vec3 s0 : spots) {
                    Vec3 g = ground(level, s0);
                    Vec3 tilt = V.unit(V.add(V.DOWN, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.35));
                    Vec3 top = V.add(V.up(g, sky), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 1.2);
                    RuneSwords.Blade b = RuneSwords.phantom(fx, top, tilt, PhantomArcher.faceOf(tilt), len, 0.05, PhantomArcher.BLADE, PhantomArcher.GEM);
                    b.grow(1, 2);
                    if (RuneKit.fxQuota("bladeRainTrace", 8)) JobFx.glint(level, V.add(top, tilt, len * 0.5), 0.35, PhantomArcher.TRACE, PhantomArcher.TRACE_CORE, 0.18,
                            Opt.empty());
                    stuck.add(b);
                    RuneKit.later(3, () -> b.pose(V.add(g, tilt, -len * 0.62), tilt, PhantomArcher.faceOf(tilt), fall));
                    RuneKit.later(3 + fall, () -> {
                        if (RuneKit.fxQuota("bladeRainLand", 8)) {
                            SkillFx.sparks(level, V.up(g, 0.1), PhantomArcher.BLADE, 3, 3);
                            JobFx.glint(level, V.up(g, 0.2), 0.3, glow, JobKit.core(j), 0.15, Opt.empty());
                        }
                    });
                }
                SkillFx.play(level, "item.trident.throw", V.up(center, sky), 0.8 + ww * 0.08, 0.9);
            });
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                SkillFx.play(level, "item.trident.hit_ground", center, 1.0 + ww * 0.06, 1.0);
                RuneKit.zonePulse(level, center, glow, r, 0.25);
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    boolean pin = rooted.add(e.getUUID());
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 2.0), 2, 2, false, false, life, 0, d -> {
                        PhantomArcher.lodge(sp, j, e, 1, V.up(e.position(), sky));
                        if (RuneKit.fxQuota("bladeRainHit", 6)) JobFx.hit(e, j, 0.9);
                        if (pin && k.num("root", 0) > 0) {
                            boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
                            RuneKit.effect(e, "slowness", (int) k.num("root", 20), boss ? 1 : 10);
                        }
                    });
                }
            });
        }
        // 꽂힌 칼이 한꺼번에 터짐
        RuneKit.later(burstAt, () -> {
            ServerLevel level = sp.level();
            for (RuneSwords.Blade b : stuck) {
                if (RuneKit.fxQuota("bladeRainBurst", 14)) PhantomArcher.blast(level, j, b.tip(), 0.8);
                PhantomArcher.shatter(level, b, 1.0);
            }
            stuck.clear();
            if (!RuneKit.sameLife(sp, life)) return;
            PhantomArcher.blast(level, j, V.up(center, 0.8), r * 0.7);
            RuneKit.zonePulse(level, center, PhantomArcher.FIRE, r + 0.5, 0.45);
            for (LivingEntity e : Kit.around(sp, center, r, 4)) JobKit.strike(sp, e, j, P, k.num("burst", 2.6), false, false, life, 0, d -> JobFx.hit(e, j, 1.0));
        });
        return true;
    }

    // ------------------------------------------------------------ 팬텀 버스트

    /**
     * 팬텀 버스트 (archer_burst): 활에 거대한 칼 (len 칸) 이 투영되어 draw 틱 동안 시위에 걸려 당겨짐 (느려짐) → 겨눈 쪽 range 칸을 날아가 처음 맞은 적 (폭 width) 에게
     * 꽂힘 (hit) → blastDelay 틱 뒤 거대하게 터짐 (둘레 radius 칸 blast — 그 적들에게 꽂혀 있던 칼도 함께 터짐: 하나당 + perLodged). 아무도 없으면 벽 · 끝에 꽂혀 터짐
     */
    static boolean burst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int draw = Math.max(6, (int) k.num("draw", 14));
        double len = k.num("len", 4.0);
        double speed = k.num("speed", 90);
        int blastDelay = Math.max(10, (int) k.num("blastDelay", 10));
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        RuneKit.effect(sp, "slowness", draw + 4, 2);
        Vec3 f0 = sp.getViewVector(1F);
        Vec3 h0 = V.add(V.add(RuneKit.bowHand(sp), V.rightOf(V.flat(f0)), 0.3), f0, 0.4);
        RuneSwords.Blade b = PhantomArcher.project(level0, h0, f0, len, 6, draw + blastDelay + 60, true);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 0.9, 1.0);
        SkillFx.play(level0, "block.beacon.power_select", sp.position(), 1.6, 0.6);
        // 당김: 활을 따라 겨눔 · 조금씩 뒤로 당겨짐 · 칼끝에 빛이 모임 · 끝무렵 떨림
        for (int t = 1; t < draw; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 f = sp.getViewVector(1F);
                // 2026-11-15 1인칭 영상: 활 손 뒤 (−0.7 ~ −1.05칸) 는 칼자루 · 코등이가 화면 오른쪽을 크게 덮었음 → 활 앞 0.4 → 0.1칸 · 오른쪽 0.3칸
                double pull = 0.4 - 0.3 * tt / (double) draw;
                Vec3 h = V.add(V.add(RuneKit.bowHand(sp), V.rightOf(V.flat(f)), 0.3), f, pull);
                if (tt > draw - 5) h = V.add(h, new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.5, Kit.rand() - 0.5), 0.04);
                b.pose(h, f, PhantomArcher.faceOf(f), 1);
                if (tt % 3 == 0) JobFx.glint(sp.level(), b.tip(), 0.3 + tt * 0.03, glow, core, 0.16, Opt.empty());
            });
        }
        RuneKit.later(draw, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                b.remove();
                return;
            }
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = sp.getViewVector(1F);
            double range = k.num("range", 32);
            List<Kit.LineHit> hits = Kit.lineHits(sp, eye, f, range, k.num("width", 1.0));
            LivingEntity hitE = hits.isEmpty() ? null : hits.get(0).e();
            double dist = hitE != null ? hits.get(0).t() : Kit.rayDist(level, eye, f, range);
            Vec3 to = hitE != null ? Kit.bodyPoint(hitE) : V.add(eye, f, Math.max(2, dist));
            Vec3 from = b.hilt();
            Vec3 d = V.unit(V.sub(to, from));
            int t = Math.max(1, ArrowFx.flyTicks(from, to, speed));
            double reach = b.reach();
            b.pose(V.add(to, d, -reach * 0.7), d, PhantomArcher.faceOf(d), t);
            RuneKit.flyStreak(level, from, to, speed, glow, core, 1.6, 0.34);
            SkillFx.play(level, "item.crossbow.shoot", from, 0.6, 1.0);
            SkillFx.play(level, "item.trident.riptide_2", from, 1.2, 0.8);
            Vec3 off = hitE != null ? V.sub(V.add(to, d, -reach * 0.7), Kit.bodyPoint(hitE)) : null;
            RuneKit.later(t, () -> {
                ServerLevel lv = sp.level();
                JobFx.glint(lv, to, 1.2, glow, core, 0.25, Opt.empty());
                SkillFx.sparks(lv, to, core, 12, 5);
                SkillFx.play(lv, "item.trident.hit", to, 0.6, 1.0);
                if (hitE != null && RuneKit.sameLife(sp, life)) JobKit.strike(sp, hitE, j, P, k.num("hit", 5.6), false, false, life, 0, dd -> {
                    PhantomArcher.lodge(sp, j, hitE, 1, from);
                    JobFx.hit(hitE, j, 1.1);
                });
                // 꽂힌 채 빛이 부풂 (적을 따라감)
                for (int q = 1; q < blastDelay; q++) {
                    int qq = q;
                    RuneKit.later(q, () -> {
                        if (hitE != null && !hitE.isRemoved()) b.pose(V.add(Kit.bodyPoint(hitE), off), d, PhantomArcher.faceOf(d), 1);
                        if (qq % 3 == 0) JobFx.glint(sp.level(), V.lerp(b.hilt(), b.tip(), 0.6), 0.5 + qq * 0.08, PhantomArcher.FIRE, PhantomArcher.FIRE_CORE, 0.15,
                                Opt.empty());
                    });
                }
                RuneKit.later(blastDelay, () -> {
                    ServerLevel l2 = sp.level();
                    Vec3 c = hitE != null && !hitE.isRemoved() ? Kit.bodyPoint(hitE) : to;
                    PhantomArcher.shatter(l2, b, 2.5);
                    double rad = k.num("radius", 4);
                    PhantomArcher.blast(l2, j, c, rad * 0.9);
                    JobFx.burst(l2, c, rad * 1.1, j, 0.5, Opt.of("alpha", 0.8));
                    RuneKit.zonePulse(l2, ground(l2, c), PhantomArcher.FIRE, rad, 0.5);
                    PhantomArcher.shards(l2, c, 14, 9.0, 0.5);
                    Ult.flash(l2, c, PhantomArcher.FIRE, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.04, 0.25}), sp);
                    SkillFx.play(l2, "entity.generic.explode", c, 0.8, 1.2);
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (LivingEntity e : Kit.around(sp, c, rad, 3)) {
                        int n = PhantomArcher.detonate(sp, e);
                        double m = k.num("blast", 11.2) * (1 + k.num("perLodged", 0.1) * n);
                        JobKit.strike(sp, e, j, P, m, false, true, life, 0, dd -> JobFx.hit(e, j, 1.3));
                    }
                });
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 소드 생추어리

    /** 땅에 꽂힌 칼 하나 (소드 생추어리) */
    private static final class Planted {
        final RuneSwords.Blade b;
        final Vec3 ground;
        boolean used;

        Planted(RuneSwords.Blade b, Vec3 ground) {
            this.b = b;
            this.ground = ground;
        }
    }

    /**
     * 소드 생추어리 (archer_ult): 붉은 노을 (rpg:ult_sunset) — 발밑에서 불꽃 고리가 ringTicks 틱 동안 radius 칸까지 번지고 고리 안 땅에 칼 swords 자루가 솟아 꽂힘 →
     * first 틱부터 every 틱마다 (waves 번) 칼이 뽑혀 고리 안 적들에게 날아가 꽂힘 (적마다 wave — 숫자 두 줄 · 꽂힘 +1, 뽑힌 자리에는 새 칼이 솟음) → finalAt − 14 에
     * 남은 칼이 겨눈 곳 (range 칸) 하늘로 솟았다가 finalAt 에 한꺼번에 내리꽂힘 (둘레 finalRadius 칸 final · 큰 숫자)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double R = k.num("radius", 12);
        int ringTicks = Math.max(4, (int) k.num("ringTicks", 12));
        int nSw = Math.max(8, (int) k.num("swords", 36));
        int waves = (int) k.num("waves", 6);
        int every = Math.max(10, (int) k.num("every", 10));
        int first = Math.max(ringTicks + 4, (int) k.num("first", 24));
        int finalAt = Math.max(first + (waves - 1) * every + 10, (int) k.num("finalAt", 86));
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, c0, "rpg:ult_sunset", finalAt + 30);
        Ult.flash(level0, c0, glow, Opt.of("radius", 10.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.2, 0.9);
        SkillFx.play(level0, "item.firecharge.use", c0, 0.6, 1.0);
        JobFx.aura(sp, j, 3.0, 0.6, 0.9);
        Vfx.Fx fx = RuneProps.fx(level0, c0, finalAt + 40);
        List<Planted> field = new ArrayList<>();
        // 불꽃 고리가 번짐
        for (int i = 0; i <= ringTicks; i += 2) {
            double ri = Math.max(1.5, R * i / (double) ringTicks);
            RuneKit.later(i, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                JobFx.ring(level, V.up(c0, 0.08), ri, j, false, 0.35, Opt.of("alpha", 0.95));
                for (int q = 0; q < 12; q++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), ri));
                    SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 0.8, "life", 0.45, "color", PhantomArcher.FIRE, "alpha", 0.85, "spin", Kit.rand() * 360));
                }
            });
        }
        // 2026-11-15 컨셉아트: 고리 테두리의 불꽃이 끝까지 타오름 (6틱마다 열여섯)
        for (int t = ringTicks; t < finalAt; t += 6) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                for (int q = 0; q < 16; q++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), R + (Kit.rand() - 0.5) * 0.8));
                    SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 1.0 + Kit.rand() * 0.5, "life", 0.5, "color", PhantomArcher.FIRE, "alpha", 0.9, "spin", Kit.rand() * 360));
                }
            });
        }
        RuneKit.later(ringTicks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            SkillFx.rune(level, V.up(c0, 0.05), Opt.of("size", R, "life", (finalAt - ringTicks + 10) / 20.0, "color", glow, "alpha", 0.5, "rate", 8.0, "grow", 0.0));
            SkillFx.play(level, "block.fire.ambient", c0, 0.8, 1.0);
        });
        // 칼이 솟음 (가까운 데부터 — 번지는 고리를 따라)
        for (int i = 0; i < nSw; i++) {
            double rad = R * Math.sqrt((i + 0.5) / nSw) * 0.95;
            int at = 3 + (int) (rad / R * ringTicks) + (i % 3);
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                plant(sp.level(), fx, field, c0, rad);
            });
        }
        // 물결: 칼이 뽑혀 적에게 날아가 꽂힘
        int fly = 4;
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int hitAt = first + w * every;
            RuneKit.later(hitAt - fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                List<LivingEntity> in = Kit.around(sp, c0, R, 5);
                int launch = in.isEmpty() ? 4 : Math.max(4, Math.min(8, in.size() * 2));
                for (int q = 0; q < launch; q++) {
                    LivingEntity e = in.isEmpty() ? null : in.get(q % in.size());
                    Vec3 to = e != null ? V.up(Kit.bodyPoint(e), (Kit.rand() - 0.5) * 0.4) : V.up(randomIn(level, c0, R), 1.0);
                    Planted p = nearestFree(field, to);
                    if (p == null) break;
                    p.used = true;
                    RuneSwords.Blade b = p.b;
                    Vec3 lift = V.up(b.hilt(), 0.9);
                    b.pose(lift, b.dir(), PhantomArcher.faceOf(b.dir()), 1);
                    Vec3 d = V.unit(V.sub(to, lift));
                    RuneKit.later(1, () -> b.pose(V.add(to, d, -b.reach() * 0.75), d, PhantomArcher.faceOf(d), fly - 1));
                    if (RuneKit.fxQuota("sanctuaryStreak", 10)) RuneKit.flyStreak(level, lift, to, V.len(V.sub(to, lift)) / (fly / 20.0), glow, core, 0.7, 0.16);
                    RuneKit.later(fly, () -> {
                        b.remove();
                        if (e == null && RuneKit.fxQuota("sanctuaryMiss", 6)) JobFx.glint(sp.level(), to, 0.4, glow, core, 0.15, Opt.empty());
                    });
                    RuneKit.later(fly + 2, () -> {
                        if (RuneKit.sameLife(sp, life)) plant(sp.level(), fx, field, c0, R * Math.sqrt(Kit.rand()) * 0.95);
                    });
                }
                SkillFx.play(level, "item.trident.throw", c0, 0.75 + ww * 0.06, 1.0);
            });
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                JobFx.ring(level, V.up(c0, 0.08), R, j, false, 0.3, Opt.of("alpha", 0.6));
                SkillFx.play(level, "item.trident.hit", c0, 0.9 + ww * 0.06, 0.9);
                for (LivingEntity e : Kit.around(sp, c0, R, 5)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 7), 2, 2, false, false, life, 0, d -> {
                        PhantomArcher.lodge(sp, j, e, 1, V.up(c0, 1.0));
                        if (RuneKit.fxQuota("sanctuaryHit", 8)) JobFx.hit(e, j, 1.0);
                    });
                }
            });
        }
        // 마지막: 남은 칼이 겨눈 곳 하늘로 솟았다가 한꺼번에 내리꽂힘
        Vec3[] spot = {null};
        List<RuneSwords.Blade> rain = new ArrayList<>();
        JobKit.motionLater(sp, "archer_ult:final", finalAt - 14, life);
        RuneKit.later(finalAt - 14, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            LivingEntity t = Kit.pickTarget(sp, k.num("range", 24), 20);
            spot[0] = t != null ? ground(level, t.position()) : ground(level, Kit.aimPoint(sp, k.num("range", 24)));
            int q = 0;
            for (Planted p : field) {
                if (p.used) continue;
                p.used = true;
                if (q++ >= 24) {
                    p.b.grow(0.1, 6);
                    continue;
                }
                double a = q * 2.399;
                double rr = 1.2 + 2.8 * Math.sqrt(q / 24.0);
                Vec3 top = V.add(V.up(spot[0], 7 + (q % 4) * 0.6), new Vec3(Math.cos(a), 0, Math.sin(a)), rr);
                Vec3 d = V.unit(V.sub(V.add(spot[0], new Vec3(Math.cos(a), 0, Math.sin(a)), rr * 0.25), top));
                p.b.pose(top, d, PhantomArcher.faceOf(d), 6 + (q % 4));
                rain.add(p.b);
            }
            JobFx.ring(level, V.up(spot[0], 7.5), 4.5, j, true, 0.7, Opt.of("alpha", 0.9));
            SkillFx.play(level, "block.amethyst_block.resonate", spot[0], 0.7, 1.2);
            SkillFx.play(level, "item.trident.riptide_3", spot[0], 1.2, 0.8);
        });
        RuneKit.later(finalAt - 4, () -> {
            if (spot[0] == null) return;
            for (RuneSwords.Blade b : rain) {
                Vec3 d = b.dir();
                Vec3 hit = V.add(spot[0], new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 2.4);
                b.pose(V.add(V.up(hit, 0.1), d, -b.reach() * 0.6), d, PhantomArcher.faceOf(d), 4);
            }
        });
        RuneKit.later(finalAt, () -> {
            if (spot[0] == null) return;
            ServerLevel level = sp.level();
            double fr = k.num("finalRadius", 4);
            PhantomArcher.blast(level, j, V.up(spot[0], 1.0), fr * 0.9);
            JobFx.burst(level, V.up(spot[0], 1.2), fr * 1.2, j, 0.55, Opt.of("alpha", 0.85));
            RuneKit.zonePulse(level, spot[0], PhantomArcher.FIRE, fr + 0.5, 0.5);
            PhantomArcher.shards(level, V.up(spot[0], 0.8), 16, 9.0, 0.5);
            SkillFx.play(level, "entity.generic.explode", spot[0], 0.7, 1.4);
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : Kit.around(sp, spot[0], fr, 4)) JobKit.strike(sp, e, j, P, k.num("final", 14), false, true, life, 0, d -> JobFx.hit(e, j, 1.4));
        });
        RuneKit.later(finalAt + 12, () -> {
            ServerLevel level = sp.level();
            for (RuneSwords.Blade b : rain) PhantomArcher.shatter(level, b, 1.2);
            for (Planted p : field) {
                if (!p.used) p.b.grow(0.1, 6);
            }
        });
        RuneKit.later(finalAt + 19, () -> {
            for (Planted p : field) p.b.remove();
        });
        return true;
    }

    /** 고리 안 rad 칸 (각도는 아무렇게나) 땅에 칼 하나가 솟아 꽂힘 */
    private static void plant(ServerLevel level, Vfx.Fx fx, List<Planted> field, Vec3 c0, double rad) {
        double a = Kit.rand() * Math.PI * 2;
        Vec3 g = ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), rad));
        Vec3 d = V.unit(V.add(V.DOWN, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.5));
        double len = 1.8 + Kit.rand() * 0.8; // 2026-11-15 컨셉아트: 들판 칼 크게
        Vec3 face = PhantomArcher.faceOf(d);
        RuneSwords.Blade b = RuneSwords.phantom(fx, V.add(g, d, -len * 0.15), d, face, len, 0.7, PhantomArcher.BLADE, PhantomArcher.GEM);
        b.pose(V.add(g, d, -len * 0.62), d, face, 3);
        b.grow(1, 3);
        field.add(new Planted(b, g));
        if (RuneKit.fxQuota("sanctuaryRise", 6)) {
            SkillFx.sparks(level, V.up(g, 0.1), PhantomArcher.FIRE, 3, 2);
            JobFx.glint(level, V.up(g, 0.3), 0.3, PhantomArcher.TRACE, PhantomArcher.TRACE_CORE, 0.15, Opt.empty());
        }
    }

    private static Planted nearestFree(List<Planted> field, Vec3 to) {
        Planted best = null;
        double bd = Double.MAX_VALUE;
        for (Planted p : field) {
            if (p.used) continue;
            double d = V.len(V.sub(p.ground, to));
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static Vec3 randomIn(ServerLevel level, Vec3 c0, double R) {
        double a = Kit.rand() * Math.PI * 2;
        return ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), R * Math.sqrt(Kit.rand()) * 0.9));
    }
}
