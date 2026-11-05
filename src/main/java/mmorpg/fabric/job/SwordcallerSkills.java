package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 마검사 반전 소드 콜러 — 재설계안 2판 (헥스 블레이드 대신 · 사용자 "반전 컨셉도 대부분이 맘에 안듬" · "스킬 컨셉은 롤같은거 많이 참고하면 좋음"):
 * 1차 마검사 (내가 베는 광역 단타) 를 거꾸로 — 칼이 손을 떠나 스스로 싸우는 단일 연타. 특성 「스스로 싸우는 칼」 (칼 평타가 날아가 겨눈 적을 벰 · 이어 벨수록 빨라짐) 은
 * JobTraits7. 직업 스킬은 칼에게 내리는 명령: 베어라 (롤 아지르 Q) · 데려가라 (롤 아지르 E) · 꿰뚫고 맴돌아라 (롤 아펠리오스 원월륜 — 주력) · 둘로 나뉘어라 (롤 아지르 W) ·
 * 칼날 감옥 (롤 이렐리아 R). 칼은 마력 검 (JobFx3d phantom_sword — 룬이 새겨진 2D 판 · 청록). 몸 쓰는 직업이 아님 — 칼과 칼빛이 스스로 날고 벰 (내 동작은 명령하는 손짓).
 * 맞은 자리는 JobImpact (화면은 흔들지 않음).
 */
final class SwordcallerSkills {
    private SwordcallerSkills() {}

    private static final double[] WHITE = RuneKit.WHITE;
    /** 칼날 감옥 · 베어라 칼질 기울기 (가로 · 비스듬 · 되돌림 · 세로가 섞임) */
    private static final double[] CUT_TILT = {-8, 160, 35, 205, 95, -30, 140, 15, 185, 60};

    static void register() {
        JobSystem.register("swordcaller_strike", SwordcallerSkills::strike);
        JobSystem.register("swordcaller_shift", SwordcallerSkills::shift);
        JobSystem.register("swordcaller_orbit", SwordcallerSkills::orbit);
        JobSystem.register(JobTraits7.TWIN, SwordcallerSkills::twin);
        JobSystem.register("swordcaller_ult", SwordcallerSkills::ult);
    }

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 칼을 든 손 앞 (칼이 떠나는 자리 — 1인칭 눈앞 오른쪽 아래) */
    private static Vec3 handTip(ServerPlayer sp) {
        RuneKit.Hand h = RuneKit.handOf(sp);
        return V.up(V.add(h.hand(), h.f(), 0.35), 0.25);
    }

    /** 숫자 몫 (cuts 의 from ~ to) · 합 */
    private static double[] parts(List<Double> l, int from, int to) {
        double[] p = new double[Math.max(0, to - from)];
        for (int i = from; i < to; i++) p[i - from] = l.get(i);
        return p;
    }

    private static double sum(double[] p) {
        double s = 0;
        for (double x : p) s += x;
        return s;
    }

    /** 칼이 지나간 결 (참격 판 — 1 · 3인칭 모두 세상에 놓임) · 직업의 맞은 자리 · 불티 */
    private static void cutAt(ServerLevel level, Vec3 at, Vec3 view, double tilt, double size, Jobs.Job j) {
        JobFx.slashCard(level, at, view, tilt, size, 0.24, Vec3.ZERO, j, Opt.empty());
        SkillFx.sparks(level, at, JobFx.inner(j), 6, 4.0);
    }

    // ------------------------------------------------------------ 베어라

    /**
     * 베어라 (롤 아지르 Q): 칼끝으로 겨눈 곳 (range 칸 — 땅) 을 가리키면 launch 틱에 칼이 손을 떠나 날아가 (speed 칸/틱) 그 자리 허리 높이에서 맴돌며 둘레 radius 칸을 벰 —
     * 닿는 틱에 두 번 (cuts 0 · 1 — 한 번에 넣고 숫자 두 줄, cutGap 틱) · groupGap 틱 뒤 두 번 더 (cuts 2 · 3 — 마지막이 큼) → 땅에 꽂혀 stay 틱 기다림 (데려가라가 날아감)
     */
    static boolean strike(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 12);
        int launch = Math.max(0, (int) k.num("launch", 2));
        double speed = Math.max(0.5, k.num("speed", 4));
        double R = k.num("radius", 1.8);
        int group = Math.max(1, (int) k.num("group", 2));
        int cutGap = Math.max(1, (int) k.num("cutGap", 3));
        int groupGap = Math.max(10, (int) k.num("groupGap", 10));
        int stay = (int) k.num("stay", 80);
        List<Double> cuts = SwordmasterSkills.numbers(k, "cuts", List.of(2.0, 2.0, 2.0, 2.5));
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        Vec3 aim = Kit.aimPoint(sp, range);
        Vec3 flat = V.sub(new Vec3(aim.x, c0.y, aim.z), c0);
        if (V.len(flat) > range) aim = V.add(c0, V.unit(flat), range);
        Vec3 g = ground(level0, aim);
        Vec3 center = V.up(g, 1.05);
        // 겨눈 자리의 표식 (청록 고리 — 칼이 닿을 둘레)
        JobFx.ring(level0, V.up(g, 0.08), R, j, false, (launch + 4) / 20.0 + 0.1, Opt.of("alpha", 0.75));
        SkillFx.play(level0, "item.trident.return", c0, 1.6, 0.6);
        RuneKit.later(launch, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 from = handTip(sp);
            int ticks = Math.max(1, (int) Math.ceil(V.len(V.sub(center, from)) / speed));
            int id = JobFx3d.fly(lv, "phantom_sword", from, center, JobFx3d.SWORD_TIP, 1.3, ticks / 20.0, 0.05, j, Opt.of("shine", 0.4, "fadeOut", 0.05));
            JobFx.line(lv, from, center, j, ticks / 20.0 + 0.1, Opt.of("alpha", 0.6));
            SkillFx.play(lv, "item.trident.throw", from, 1.3, 0.8);
            RuneKit.later(ticks, () -> {
                JobFx3d.kill(lv, id, center, 0.03);
                if (RuneKit.sameLife(sp, life)) strikeGroup(sp, j, P, cuts, 0, group, center, g, R, cutGap, groupGap, life, false);
            });
            RuneKit.later(ticks + groupGap, () -> {
                if (RuneKit.sameLife(sp, life)) strikeGroup(sp, j, P, cuts, group, cuts.size(), center, g, R, cutGap, (cuts.size() - group) * cutGap + 2, life, true);
            });
            // 끝: 칼끝을 아래로 땅에 내리꽂혀 (2틱) 기다림 (stay 틱 — 발밑 룬 고리 · 칼 빛)
            int endT = ticks + groupGap + (cuts.size() - group) * cutGap + 2;
            RuneKit.later(endT - 2, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx3d.fly(sp.level(), "phantom_sword", V.up(center, 0.8), V.up(g, -0.3), JobFx3d.SWORD_TIP, 1.25, 0.1, 0.0, j, Opt.of(
                        "fadeOut", 0.02));
            });
            RuneKit.later(endT, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel l2 = sp.level();
                JobTraits7.setStuck(sp, g, stay, JobTraits7.stuckSword(l2, g, stay, j));
                JobFx.burst(l2, V.up(g, 0.3), 1.0, j, 0.3, Opt.empty());
                RuneKit.rockBurst(l2, V.up(g, 0.2), 4, 3.5, 0.13);
                SkillFx.play(l2, "item.trident.hit_ground", g, 1.0, 0.9);
            });
        });
        return true;
    }

    /**
     * 베어라 한 무리 (from ~ to 번째 칼질): 칼이 그 자리를 맴돌며 벰 (입체 칼이 둘레를 돎 — spinTicks 틱 · 칼날 고리 rfx_jbladering · 참격 판 칼질마다) — 둘레 radius 칸
     * 적에 한 번에 넣고 숫자 칼질마다 (cutGap 틱). last 면 반대로 돌며 크게 (멈칫)
     */
    private static void strikeGroup(ServerPlayer sp, Jobs.Job j, double P, List<Double> cuts, int from, int to, Vec3 center, Vec3 g, double R, int cutGap, int spinTicks,
            String life, boolean last) {
        ServerLevel level = sp.level();
        double[] w = parts(cuts, from, to);
        int n = w.length;
        if (n == 0) return;
        double spin = (last ? -1 : 1) * 1260;
        JobFx3d.orbitAt(level, "phantom_sword", 1, center, R * 0.75, spin, spinTicks / 20.0, 1.25, JobFx.outer(j), JobFx.inner(j), Opt.of("angle", Kit.rand() * 360,
                "fadeIn", 0.0, "fadeOut", 0.06, "shine", 0.4));
        JobFx.bladeRing(level, center, R, j, (n * cutGap + 2) / 20.0, 18, Opt.of("alpha", 0.9));
        Vec3 view = V.unit(V.sub(center, sp.getEyePosition()));
        for (int i = 0; i < n; i++) {
            int ii = i;
            RuneKit.later(i * cutGap, () -> {
                double tilt = CUT_TILT[(from + ii) % CUT_TILT.length];
                cutAt(level, center, view, tilt, R * (last && ii == n - 1 ? 1.4 : 1.15), j);
                SkillFx.play(level, "entity.player.attack.sweep", center, 1.4 + 0.1 * ii, 0.6);
                if (last && ii == n - 1) {
                    SkillFx.shock(level, V.up(g, 0.1), JobFx.inner(j), R, 0.3);
                    SkillFx.play(level, "item.trident.riptide_1", center, 1.4, 0.6);
                }
            });
        }
        for (LivingEntity e : Kit.around(sp, g, R, 2.5)) {
            JobKit.strikeSplit(sp, e, j, P, sum(w), n, cutGap, w, true, false, life, 0, x -> JobImpact.hit(sp, e, j, last ? 1.4 : 1.0));
        }
    }

    // ------------------------------------------------------------ 데려가라

    /**
     * 데려가라 (롤 아지르 E): 꽂혀 기다리는 칼 (seek 칸 안) 이 있으면 칼이 나를 끌어당겨 그 곁으로 날아감, 없으면 보는 쪽 dist 칸 (막히면 그 앞) — start 틱부터 speed 칸/틱
     * (끌어당기는 청록 줄 · 몸의 잔상 · 바람) · 지나간 적 (폭 width) 을 벰 (total · 지나가는 틱에). 칼에 닿으면 칼을 뽑아 둘레를 벰 (그림)
     */
    static boolean shift(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 stuck = JobTraits7.stuckAt(sp);
        boolean toSword = stuck != null && V.hdist(stuck, c0) <= k.num("seek", 14) && Math.abs(stuck.y - c0.y) <= 4 && V.hdist(stuck, c0) > 1.0;
        Vec3 f = toSword ? V.flat(V.sub(stuck, c0)) : Kit.facingOf(sp);
        double D = toSword ? Math.max(0, V.hdist(stuck, c0) - 0.7) : RuneKit.dashDistance(sp, f, k.num("dist", 8));
        Vec3 dest = toSword ? V.add(new Vec3(c0.x, stuck.y, c0.z), f, D) : V.add(c0, f, D);
        int start = Math.max(0, (int) k.num("start", 1));
        double speed = Math.max(0.3, k.num("speed", 1.6));
        int ticks = Math.max(1, (int) Math.ceil(D / speed));
        double half = k.num("width", 1.2) / 2;
        String life = RuneKit.lifeToken(sp);
        JobMove.run(sp, JobMove.to(start, start + ticks, JobMove.at(dest), JobMove.LINEAR));
        RpgCombat.setSteadfast(sp, start + ticks + 4);
        // 끌어당김: 칼에서 내게로 청록 줄 · 칼 번쩍 / 칼이 없으면 앞으로 날아간 칼빛이 길을 엶
        if (toSword) {
            JobFx.line(level0, V.up(stuck, 0.9), V.up(c0, 1.2), j, (start + ticks) / 20.0 + 0.05, Opt.of("alpha", 0.85));
            JobFx.glint(level0, V.up(stuck, 1.2), 0.7, j, 0.3, Opt.empty());
            SkillFx.play(level0, "item.trident.return", stuck, 1.2, 0.9);
        } else {
            JobFx.bigWave(level0, sp, V.up(V.add(c0, f, 1.0), 1.2), f, 30.0, 1.6, 174.0, D / 30.0 + 0.12, j, Opt.empty()); // 0.51.0 길을 여는 큰 검기
            SkillFx.play(level0, "item.trident.throw", c0, 1.1, 0.8);
        }
        SkillFx.play(level0, "item.trident.riptide_2", c0, 1.3, 0.6);
        // 날아가는 동안: 몸의 잔상 · 줄기 · 바람 (2틱마다)
        for (int t = start; t <= start + ticks; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.speed(sp, j, 0.25, 0.7);
                SkillFx.fx(sp.level(), "rpg:arrow_spark", V.up(sp.position(), 1.0), Opt.of("dir", V.scale(f, -1), "speed", 4.0, "size", 0.12, "grow", 0.6, "life", 0.3,
                        "color", JobFx.inner(j), "alpha", 1.0));
            });
        }
        RuneKit.later(start, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.dash(sp.level(), V.up(c0, 1.0), V.up(dest, 1.0), j, ticks / 20.0 + 0.25, Opt.of("alpha", 0.9));
        });
        // 지나간 적: 길 위 (폭 half) — 지나가는 틱에
        for (LivingEntity e : Kit.around(sp, V.lerp(c0, dest, 0.5), D / 2 + 1.5, 2.5)) {
            Vec3 d = V.sub(e.position(), c0);
            double along = d.x * f.x + d.z * f.z;
            double side = Math.abs(d.x * -f.z + d.z * f.x);
            double pad = Kit.bodyPad(e);
            if (along < -0.3 || along > D + 0.8 + pad || side > half + pad) continue;
            int at = start + (int) Math.ceil(Math.max(0, along) / speed);
            JobKit.strike(sp, e, j, P, k.total(), true, false, life, at, x -> {
                JobImpact.hit(sp, e, j, 1.1);
                if (e.level() instanceof ServerLevel lv) {
                    cutAt(lv, Kit.bodyPoint(e), f, Kit.rand() < 0.5 ? -12 : 192, 1.1 + Kit.bodyPad(e), j);
                    JobFx.bigWave(lv, null, Kit.bodyPoint(e), f, 0, 1.3 + Kit.bodyPad(e) * 0.5, Kit.rand() < 0.5 ? 10 : 170, 0.26, j, Opt.empty()); // 0.51.0 지나가며 벤 큰 검기
                }
            });
        }
        // 닿음: 칼을 뽑아 쥠 (꽂힌 칼이 사라지며 둘레를 한 바퀴 벤 빛) · 착지
        RuneKit.later(start + ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            if (toSword) {
                JobTraits7.dropStuck(sp, 0.1);
                JobFx.ring(lv, V.up(sp.position(), 0.9), 1.8, j, false, 0.3, Opt.of("alpha", 0.95));
                JobFx.glint(lv, V.up(sp.position(), 1.4), 0.9, j, 0.35, Opt.empty());
                SkillFx.play(lv, "item.trident.hit_ground", sp.position(), 1.4, 0.7);
            }
            JobFx.burst(lv, V.up(sp.position(), 0.3), 0.8, j, 0.25, Opt.empty());
            SkillFx.play(lv, "entity.player.attack.sweep", sp.position(), 1.2, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 꿰뚫고 맴돌아라

    /**
     * 꿰뚫고 맴돌아라 (롤 아펠리오스 원월륜 — 주력): 겨눈 적 (range 칸) 에게 launch 틱에 칼이 날아가 (speed 칸/틱) 꿰뚫고 지나감 (pierce) → 그 적 둘레를 돌며 every 틱마다
     * 벰 (spins — 한 번에 넣고 숫자 lines 줄, lineGap 틱 · 칼질마다 참격 판) → 마지막 칼질 뒤 손으로 돌아옴. 적이 없으면 쓰지 않음
     */
    static boolean orbit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 12);
        LivingEntity t = Kit.pickTarget(sp, range, 15);
        if (t == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다 (" + Math.round(range) + "칸)", 30);
            return false;
        }
        int launch = Math.max(0, (int) k.num("launch", 2));
        double speed = Math.max(0.5, k.num("speed", 4));
        int every = Math.max(10, (int) k.num("every", 10));
        int lines = Math.max(1, (int) k.num("lines", 3));
        int lineGap = Math.max(1, (int) k.num("lineGap", 3));
        List<Double> spins = SwordmasterSkills.numbers(k, "spins", List.of(3.0, 3.0, 3.0, 3.0));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        SkillFx.play(level0, "item.trident.return", sp.position(), 1.5, 0.6);
        JobFx.target(level0, Kit.bodyPoint(t), 0.6 + Kit.bodyPad(t), j, V.unit(V.sub(sp.getEyePosition(), Kit.bodyPoint(t))), 0, 0.3, Opt.of("vel", RuneKit.entVel(t)));
        RuneKit.later(launch, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 from = handTip(sp);
            Vec3 to = Kit.bodyPoint(t);
            int ticks = Math.max(1, (int) Math.ceil(V.len(V.sub(to, from)) / speed));
            int id = JobFx3d.fly(lv, "phantom_sword", from, to, JobFx3d.SWORD_TIP, 1.35, ticks / 20.0, 0.15, j, Opt.of("shine", 0.45, "fadeOut", 0.05));
            JobFx.line(lv, from, to, j, ticks / 20.0 + 0.08, Opt.of("alpha", 0.7));
            SkillFx.play(lv, "item.trident.throw", from, 1.2, 0.9);
            // 꿰뚫음 (닿는 틱): 칼이 몸을 뚫고 1.6칸 지나감 · 찌른 자리 · 타격 섬광
            RuneKit.later(ticks, () -> {
                Vec3 d = V.unit(V.sub(to, from));
                Fx3d.move(lv, id, V.add(to, d, 1.6), 0.1, "out");
                RuneKit.later(3, () -> JobFx3d.kill(lv, id, to, 0.06));
                if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
                JobKit.strike(sp, t, j, P, k.num("pierce", 3.3), true, false, life, 0, x -> {
                    JobImpact.hit(sp, t, j, 1.3);
                    if (t.level() instanceof ServerLevel l2) {
                        JobFx.stab(l2, Kit.bodyPoint(t), 0.9 + Kit.bodyPad(t), j, 0.25, Opt.of("dir", d));
                        SkillFx.play(l2, "item.trident.hit", Kit.bodyPoint(t), 1.0, 0.9);
                    }
                });
                // 맴돎: 꿰뚫고 지나간 칼이 돌아와 그 적 둘레를 돎 (적을 따라감) — 칼질 넷이 끝날 때까지
                int dur = spins.size() * every + 3;
                int oid = JobFx3d.spawn(lv, "phantom_sword", new Vec3(0, t.getBbHeight() * 0.55, 0), j, Opt.of("follow", (double) t.getId(), "orbit", 1.3 + Kit.bodyPad(t),
                        "orbitSpeed", 620.0, "orbitAngle", Kit.rand() * 360, "life", dur / 20.0, "scale", 1.25, "align", 1.0, "faceCam", 1.0, "fadeIn", 0.1, "fadeOut", 0.1,
                        "glowAlpha", 0.55, "shine", 0.35, "delay", 0.12));
                JobFx3d.guard(sp, life, new int[] {oid}, dur);
                for (int i = 0; i < spins.size(); i++) {
                    int ii = i;
                    double mult = spins.get(i);
                    boolean last = i == spins.size() - 1;
                    RuneKit.later((i + 1) * every, () -> {
                        if (!RuneKit.sameLife(sp, life) || t.isRemoved() || !t.isAlive()) {
                            JobFx3d.kill(sp.level(), oid, sp.position(), 0.1);
                            return;
                        }
                        double[] w = new double[lines];
                        java.util.Arrays.fill(w, 1.0);
                        if (last) w[lines - 1] = 1.6;
                        JobKit.strikeSplit(sp, t, j, P, mult, lines, lineGap, w, true, false, life, 0, x -> JobImpact.hit(sp, t, j, last ? 1.5 : 1.0));
                        for (int q = 0; q < lines; q++) {
                            int qq = q;
                            RuneKit.later(q * lineGap, () -> {
                                if (t.isRemoved() || !(t.level() instanceof ServerLevel l3)) return;
                                Vec3 b = Kit.bodyPoint(t);
                                Vec3 view = V.unit(V.sub(b, sp.getEyePosition()));
                                cutAt(l3, b, view, CUT_TILT[(ii * lines + qq) % CUT_TILT.length], 1.3 + Kit.bodyPad(t) + (last && qq == lines - 1 ? 0.5 : 0), j);
                                JobImpact.fx(sp, t, j, 0.8);
                                SkillFx.play(l3, "entity.player.attack.sweep", b, 1.5 + 0.08 * qq, 0.45);
                            });
                        }
                        if (last) RuneKit.later(lines * lineGap, () -> recall(sp, j, oid, t, life));
                    });
                }
            });
        });
        return true;
    }

    /** 맴돌던 칼이 손으로 돌아옴: 그 자리의 칼을 지우고 적 → 손으로 날아오는 칼 · 손에서 받음 (번쩍) */
    private static void recall(ServerPlayer sp, Jobs.Job j, int oid, LivingEntity t, String life) {
        ServerLevel level = sp.level();
        Vec3 from = t.isRemoved() ? sp.position() : Kit.bodyPoint(t);
        JobFx3d.kill(level, oid, from, 0.02);
        if (!RuneKit.sameLife(sp, life)) return;
        Vec3 hand = handTip(sp);
        int ticks = Math.max(1, (int) Math.ceil(V.len(V.sub(hand, from)) / 3.0));
        int id = JobFx3d.fly(level, "phantom_sword", from, hand, JobFx3d.SWORD_TIP, 1.1, ticks / 20.0, 0.0, j, Opt.of("fadeOut", 0.06));
        RuneKit.later(ticks, () -> {
            JobFx3d.kill(level, id, hand, 0.05);
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.glint(sp.level(), handTip(sp), 0.6, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(sp.level(), "item.trident.return", sp.position(), 1.7, 0.6);
        });
    }

    // ------------------------------------------------------------ 둘로 나뉘어라

    /**
     * 둘로 나뉘어라 (롤 아지르 W — 태세 dur 틱): 칼을 얼굴 앞에 세우면 칼이 번쩍이며 둘로 갈라짐 (손 앞의 세로 빛 · 양옆으로 벌어지는 칼빛 둘) → 칼 둘이 몸 둘레를 돎 (태세 동안 —
     * 1인칭에서는 숨김). 그동안 칼 평타마다 둘째 칼이 함께 X 자로 벰 (JobTraits7.echoBonus · flyCut) · 칼이 날아가는 거리 + range
     */
    static boolean twin(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 200);
        JobTraits.startStance(sp, k, dur);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        Vec3 hand = handTip(sp);
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        JobFx.glint(level, V.up(hand, 0.3), 0.85, j, 0.35, Opt.of("vel", RuneKit.bodyVel(sp)));
        RuneKit.later(3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = V.up(V.add(sp.position(), Kit.facingOf(sp), 0.9), 1.4);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 to = V.add(c, right, s * 1.3);
                JobFx3d.fly(lv, "phantom_sword", c, to, 0, 1.0, 0.18, 0.1, j, Opt.of("ctrl", V.up(V.lerp(c, to, 0.5), 0.5), "fadeOut", 0.1));
            }
            JobFx.ringFacing(lv, c, 1.0, j, Kit.facingOf(sp), 0.3, Opt.of("alpha", 0.9));
            SkillFx.play(lv, "block.amethyst_block.resonate", c, 1.4, 0.8);
            SkillFx.play(lv, "item.trident.return", c, 1.9, 0.6);
        });
        RuneKit.later(6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            int[] ids = JobFx3d.orbitOwner(sp, "phantom_sword", 2, new Vec3(0, 1.35, 0), 1.05, 150, false, (dur - 6) / 20.0, 0.85, j, Opt.of("view", 1.0, "owner",
                    (double) sp.getId(), "bob", 0.2, "bobHz", 0.6, "fadeIn", 0.2, "fadeOut", 0.4, "alpha", 0.92));
            JobFx3d.hold(sp, JobTraits7.TWIN, ids);
            JobFx3d.guard(sp, life, ids, dur - 6);
        });
        JobFx.aura(sp, j, 2.4, 0.6, 0.85);
        return true;
    }

    // ------------------------------------------------------------ 칼날 감옥

    /**
     * 칼날 감옥 (롤 이렐리아 R): 청록 하늘 · 칼을 하늘로 치켜들면 등 뒤에 칼 swords 자루가 솟음 → 겨눈 적 (range 칸) 을 향해 날아가 surroundAt 틱에 둘레를 둘러쌈 (칼끝이
     * 적을 봄 · 천천히 돎 — 적을 따라감) · 묶음 (finalAt 까지 — 보스는 느려짐) → surroundAt 부터 every 틱마다 사방에서 벰 (slashes — 한 번에 넣고 숫자 lines 줄 lineGap
     * 틱: 칼들이 차례로 적을 가로질러 지나감) → finalAt 틱에 칼들이 동시에 적을 꿰뚫으며 손으로 돌아옴 — 큰 한 방 (final). 적이 없으면 쓰지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 14);
        LivingEntity t = Kit.pickTarget(sp, range, 15);
        if (t == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다 (" + Math.round(range) + "칸)", 30);
            return false;
        }
        int n = Math.max(3, (int) k.num("swords", 6));
        int surroundAt = Math.max(4, (int) k.num("surroundAt", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int finalAt = (int) k.num("finalAt", 70);
        int lines = Math.max(1, (int) k.num("lines", 5));
        int lineGap = Math.max(1, (int) k.num("lineGap", 2));
        double R = k.num("radius", 1.8) + Kit.bodyPad(t) * 0.6;
        List<Double> slashes = SwordmasterSkills.numbers(k, "slashes", List.of(6.0, 6.0, 6.0, 6.0, 6.0, 6.0));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Ult.sky(level0, c0, "rpg:ult_prison", finalAt + 24);
        Ult.flash(level0, c0, JobKit.glow(j), Opt.of("radius", 10.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.5, 0.8);
        SkillFx.play(level0, "item.trident.riptide_3", c0, 1.2, 0.7);
        // 0 ~ 6: 등 뒤에 칼 n 자루가 부채꼴로 솟음 (칼끝 위 — 1인칭에서는 숨김)
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 r0 = V.rightOf(f0);
        List<Vec3> backs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(-70 + 140.0 * i / (n - 1));
            Vec3 p = V.up(V.add(V.add(c0, f0, -0.6), r0, Math.sin(a) * 1.6), 2.2 + Math.cos(a) * 0.9);
            backs.add(p);
            Vec3 tip = V.add(p, V.unit(V.add(V.UP, r0, Math.sin(a) * 0.6)), 0.3);
            JobFx3d.fly(level0, "phantom_sword", V.up(p, -1.2), tip, JobFx3d.SWORD_TIP, 1.05, 0.2, 0.12, j, Opt.of("view", 1.0, "owner", (double) sp.getId(), "fadeOut", 0.05,
                    "delay", i * 0.03));
        }
        JobFx.aura(sp, j, 2.8, 0.5, 0.9);
        // 6 ~ surroundAt: 칼들이 적에게 날아감 (부채꼴에서 → 적 둘레 자리로 — 칼끝이 가는 쪽)
        RuneKit.later(6, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(t);
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(i * 360.0 / n);
                Vec3 ring = V.add(b, new Vec3(Math.cos(a), 0.25, Math.sin(a)), R);
                JobFx3d.fly(lv, "phantom_sword", backs.get(i), ring, JobFx3d.SWORD_TIP, 1.1, (surroundAt - 6) / 20.0, 0.03, j, Opt.of("ctrl", V.up(V.lerp(backs.get(i), ring,
                        0.5), 2.0), "fadeOut", 0.04));
            }
            SkillFx.play(lv, "item.trident.throw", sp.position(), 1.0, 1.0);
        });
        // surroundAt: 둘러쌈 — 칼 n 자루가 적 둘레를 천천히 돎 (칼끝이 적 — 적을 따라감) · 감옥 고리 · 묶음
        int[] cage = new int[n];
        int hold = finalAt - surroundAt;
        RuneKit.later(surroundAt, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel lv = sp.level();
            double rate = 40;
            for (int i = 0; i < n; i++) {
                double ang = i * 360.0 / n;
                double rad = Math.toRadians(ang);
                Vec3 in = V.unit(new Vec3(-Math.cos(rad), -0.35, -Math.sin(rad)));
                cage[i] = JobFx3d.spawn(lv, "phantom_sword", new Vec3(0, t.getBbHeight() * 0.6, 0), j, Opt.of("follow", (double) t.getId(), "orbit", R, "orbitSpeed", rate,
                        "orbitAngle", ang, "dir", in, "arcAxis", V.UP, "arc", -rate * hold / 20.0, "arcTime", hold / 20.0, "arcEase", "linear", "faceCam", 1.0, "life", hold
                                / 20.0 + 0.1, "scale", 1.1, "fadeIn", 0.0, "fadeOut", 0.05, "glowAlpha", 0.55, "shine", 0.3, "bob", 0.12, "bobHz", 1.2));
            }
            JobFx3d.guard(sp, life, cage, hold);
            JobFx.bladeRing(lv, V.up(t.position(), 0.1), R + 0.3, j, hold / 20.0, 6, Opt.of("alpha", 0.75));
            boolean boss = JobKit.isBoss(t);
            RuneKit.effect(t, "slowness", hold, boss ? 1 : 10);
            SkillFx.play(lv, "block.chain.place", t.position(), 0.8, 1.0);
            SkillFx.play(lv, "item.trident.hit_ground", t.position(), 0.9, 0.8);
        });
        // 사방에서 벰: every 틱마다 한 무리 (숫자 lines 줄) — 줄마다 칼 한 자루가 적을 가로질러 지나감 · 참격 판 · 불티 · 칼 소리
        for (int i = 0; i < slashes.size(); i++) {
            int ii = i;
            double mult = slashes.get(i);
            RuneKit.later(surroundAt + i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || t.isRemoved() || !t.isAlive()) return;
                JobKit.strikeSplit(sp, t, j, P, mult, lines, lineGap, true, false, life, 0, x -> JobImpact.hit(sp, t, j, 1.1));
                for (int q = 0; q < lines; q++) {
                    int qq = q;
                    RuneKit.later(q * lineGap, () -> {
                        if (t.isRemoved() || !(t.level() instanceof ServerLevel l2)) return;
                        dartThrough(sp, j, t, R, ii * lines + qq);
                        if (qq % 2 == 0) SkillFx.play(l2, "entity.player.attack.sweep", Kit.bodyPoint(t), 1.3 + 0.05 * qq, 0.5);
                    });
                }
                if (ii % 2 == 1 && t.level() instanceof ServerLevel l2) SkillFx.play(l2, "item.trident.riptide_1", t.position(), 1.6, 0.4);
            });
        }
        // finalAt: 칼들이 동시에 적을 꿰뚫으며 손으로 돌아옴 — 큰 한 방
        RuneKit.later(finalAt - 3, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel lv = sp.level();
            JobKit.motion(sp, "swordcaller_ult:catch");
            Vec3 b = Kit.bodyPoint(t);
            for (int i = 0; i < n; i++) {
                JobFx3d.kill(lv, cage[i], b, 0.02);
                double ang = Math.toRadians(i * 360.0 / n + 40 * 3);
                Vec3 from = V.add(b, new Vec3(Math.cos(ang), 0.3, Math.sin(ang)), R);
                Vec3 hand = handTip(sp);
                JobFx3d.fly(lv, "phantom_sword", from, hand, JobFx3d.SWORD_TIP, 1.1, 0.3, 0.0, j, Opt.of("ctrl", V.add(b, V.unit(V.sub(b, from)), 0.6), "fadeOut", 0.06,
                        "shine", 0.5));
            }
            SkillFx.play(lv, "item.trident.riptide_3", b, 1.0, 0.9);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(t);
            JobKit.strike(sp, t, j, P, k.num("final", 32), true, true, life, 0, x -> {
                JobImpact.hit(sp, t, j, 2.5);
                if (t.level() instanceof ServerLevel l2) {
                    Vec3 bb = Kit.bodyPoint(t);
                    JobFx.two(l2, "jcuts", SwordmasterSkills.CUTS, bb, 0.45, Opt.of("size", 2.2 + Kit.bodyPad(t), "spin", Kit.rand() * 360, "fade", 0.3), JobFx.outer(j),
                            JobFx.inner(j));
                    RuneKit.godRays(sp, l2, ground(l2, t.position()), 3.5, 8, JobKit.glow(j), JobKit.core(j), Opt.of("h", 6.0, "life", 0.6, "alpha", 0.75, "skipNear",
                            true));
                }
            });
            JobFx.burst(lv, b, 1.8, j, 0.35, Opt.empty());
            // 0.51.0 큰 한 방: 갇힌 적 위의 거대한 X 검기 (사용자 "커다란 검기가 나오면서 때리면")
            Vec3 ff = V.flat(V.sub(t.position(), sp.position()));
            ff = V.len(ff) < 1e-3 ? Kit.facingOf(sp) : V.unit(ff);
            JobFx.bigWave(lv, null, b, ff, 0, 2.4 + Kit.bodyPad(t) * 0.6, 45, 0.38, j, Opt.empty());
            JobFx.bigWave(lv, null, b, ff, 0, 2.4 + Kit.bodyPad(t) * 0.6, -45, 0.38, j, Opt.empty());
            JobFx.bladeRing(lv, b, R + 0.6, j, 0.35, 24, Opt.of("alpha", 1.0));
            Ult.flash(lv, b, JobKit.core(j), Opt.empty(), sp);
            SkillFx.play(lv, "entity.player.attack.crit", b, 0.7, 1.0);
            SkillFx.play(lv, "item.trident.thunder", b, 1.6, 0.5);
        });
        RuneKit.later(finalAt + 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.glint(sp.level(), handTip(sp), 0.9, j, 0.35, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(sp.level(), "item.trident.return", sp.position(), 1.5, 0.8);
        });
        return true;
    }

    /** 감옥의 칼 한 자루가 적을 가로질러 지나감 (둘레 한 점 → 몸 → 반대쪽 — 3틱) · 지나간 결의 참격 판 · 불티 */
    private static void dartThrough(ServerPlayer sp, Jobs.Job j, LivingEntity t, double R, int i) {
        ServerLevel level = (ServerLevel) t.level();
        Vec3 b = Kit.bodyPoint(t);
        double a = Math.toRadians(i * 137.5 + Kit.rand() * 20);
        double h = (Kit.rand() - 0.5) * 0.8;
        Vec3 from = V.add(b, new Vec3(Math.cos(a), h, Math.sin(a)), R + 0.4);
        Vec3 to = V.add(b, new Vec3(-Math.cos(a), -h, -Math.sin(a)), R + 0.4);
        JobFx3d.fly(level, "phantom_sword", from, to, JobFx3d.SWORD_TIP, 1.05, 0.15, 0.02, j, Opt.of("fadeOut", 0.05, "shine", 0.35));
        Vec3 view = V.unit(V.sub(b, sp.getEyePosition()));
        cutAt(level, b, view, CUT_TILT[i % CUT_TILT.length], 1.25 + Kit.bodyPad(t), j);
        JobImpact.fx(sp, t, j, 0.75);
    }
}
