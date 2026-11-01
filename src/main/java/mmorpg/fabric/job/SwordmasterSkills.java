package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Obj;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 검사 각성 시구르드(소드마스터) (jobs.json advance.swordmaster — 금빛 · 은빛 칼빛, 특성 「투지」 최대 8 · 투지 8 이면 치명): 금빛 연격 · 그라니의
 * 질주 · 만월 강하 · 용의 심장 · 신들의 황혼 (사용자 "각성도 기존에 컨셉을 유지한채로 스킬을 바꿔도 됨 더 역동적인 모션을 사용한다던가 더 쎈 스킬을 사용하던가" —
 * 검사의 다섯 스킬과 같은 칸 · 같은 컨셉을 더 큰 몸 동작으로). 몸 쓰는 직업 기준 (사용자 — 제 몸 · 제 칼 동작만, 저절로 나는 환영 무기 · 하늘로 솟아 쏘기 없음).
 * 피해 = 위력 P (직업 맞춤 — 검사의 초당 피해 × 1.2) × 배율 (JobKit.strike · strikeSplit — 같은 적은 10틱 넘게 띄움, 빠른 두 번 베기는 한 번에 넣고 숫자만
 * 두 줄). 그림은 검사의 은빛 초승달 · 맞은 자리를 소드마스터 색 (금빛 겉 · 은빛 속) 으로 (JobFx.fid).
 */
final class SwordmasterSkills {
    private SwordmasterSkills() {}

    /** 잔상의 그림자 몸 (짙은 금빛 — 풀밭 위에서도 보이게) */
    private static final double[] SHADE = {0.45, 0.32, 0.08};
    private static final double[] WHITE = RuneKit.WHITE;
    /** 흙먼지 · 튀는 흙 */
    private static final double[] DUST = {0.62, 0.56, 0.48};
    /** 갈라진 땅의 속 (어두운 틈) */
    private static final double[] RIFT = {0.05, 0.04, 0.02};
    /** 갈라짐이 앞으로 뻗는 빠르기 (칸/초) */
    private static final double SPLIT_SPEED = 32;

    static void register() {
        JobSystem.register("swordmaster_flurry", SwordmasterSkills::flurry);
        JobSystem.register("swordmaster_grani", SwordmasterSkills::grani);
        JobSystem.register("swordmaster_moon", SwordmasterSkills::moon);
        JobSystem.register("swordmaster_heart", SwordmasterSkills::heart);
        JobSystem.register("swordmaster_ult", SwordmasterSkills::ult);
    }

    static List<Double> numbers(Jobs.Skill k, String key, List<Double> fallback) {
        List<Double> l = k.def().numbersOrNull(key);
        return l == null || l.isEmpty() ? fallback : l;
    }

    // ------------------------------------------------------------ 금빛 연격

    /** 금빛 연격 네 박자의 눈앞 베기 기울기 (도 — 두 번 베기는 둘째가 반대쪽) */
    private static final double[][] FLURRY_TILT = {{-12, 168}, {14, 192}, {95}, {35, 145}};

    /**
     * 금빛 연격: gap 틱마다 네 박자 (beats — 1.0 · 1.0 · 1.2 · 1.8, splits — 두 번 베기는 숫자 두 줄): 한 걸음씩 내딛으며 좌우로 두 번씩 두 차례 → 작게
     * 뛰어 돌며 내려 벰 (hop 칸) → X자 베기. 쓸 때 투지가 wave.need 이상이면 wave.use 개를 쓰고 X자가 검기 두 줄로 날아감 (앞 wave.range 칸 · 폭 wave.width
     * 관통 — 부채꼴 안의 적도, 적마다 한 번)
     */
    static boolean flurry(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> beats = numbers(k, "beats", List.of(1.0, 1.0, 1.2, 1.8));
        List<Double> splits = numbers(k, "splits", List.of(2.0, 2.0, 1.0, 2.0));
        int gap = (int) k.num("gap", 10);
        double reach = k.num("reach", 3.6);
        double arc = k.num("arc", 140);
        double step = k.num("step", 0.55);
        Obj wave = k.def().objOrNull("wave");
        boolean waveOn = wave != null && JobTraits.stacks(sp) >= wave.numOr("need", 3);
        if (waveOn) {
            JobTraits.useStacks(sp, (int) wave.numOr("use", 3));
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.burst(sp.level(), V.up(V.add(h.hand(), h.f(), 0.6), 0.3), 0.36, j, 0.3, Opt.empty());
            SkillFx.play(sp.level(), "block.bell.hit", sp.position(), 1.9, 0.35);
        }
        String life = RuneKit.lifeToken(sp);
        int n = beats.size();
        // 셋째 박자: 작게 뛰어올라 돌며 내려 벰 (몸 — 뜀은 다음 박자 전에 내려앉음)
        JobMove.run(sp, JobMove.hop(2 * gap - 4, 2 * gap + 3, from -> from, k.num("hop", 0.6)));
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            boolean last = i == n - 1;
            double mult = beats.get(i);
            int split = (int) Math.max(1, Math.round(splits.get(Math.min(i, splits.size() - 1))));
            // 한 걸음 내딛음 (땅 위에서만 · 셋째는 뜀이 대신)
            if (step > 0 && i != 2 && sp.onGround() && !Status.isRooted(sp)) Kit.launch(sp, f.x * step * 0.5, sp.getDeltaMovement().y, f.z * step * 0.5);
            if (last && waveOn) {
                crossWave(sp, j, P, mult, split, wave, reach, arc, life);
                return;
            }
            double[] tilts = FLURRY_TILT[Math.min(i, FLURRY_TILT.length - 1)];
            // 두 번 베기: 첫 베기 지금 · 둘째는 3틱 뒤 반대쪽 (숫자는 한 번에 두 줄)
            for (int s = 0; s < tilts.length; s++) {
                double tilt = tilts[s];
                double size = last ? 2.2 : i == 2 ? 2.3 : 1.75;
                RuneKit.later(s * 3, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    JobFx.slash(sp, j, 2.2, i == 2 ? 0.1 : 0.35, size, tilt, 0.26);
                    SkillFx.play(sp.level(), "item.spear.attack_miss", sp.getEyePosition(), 1.25 + Kit.rand() * 0.2, 0.85);
                });
            }
            if (i == 2) {
                // 내려 베기: 발밑 금빛 둥근 베기 · 흙먼지
                JobFx.ring(level, V.up(sp.position(), 0.06), reach * 0.8, j, false, 0.32, Opt.of("alpha", 0.8));
                SkillFx.sparks(level, V.up(sp.position(), 0.15), DUST, 6, 2.6);
            }
            if (last) SkillFx.play(level, "entity.player.attack.sweep", eye, 1.15, 0.85);
            int[] cnt = {0};
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, i == 2 ? 360 : arc, 10)) {
                JobKit.strikeSplit(sp, e, j, P, mult, split, 3, false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, last ? 1.3 : 1.05);
                });
            }
        });
        return true;
    }

    /** 금빛 연격 마지막: X자 검기 두 줄 (기울기 35 · 145 — 눈앞 큰 베기가 그대로 날아감) — 선 가까이 · 부채꼴 안의 적마다 한 번 (숫자 두 줄) */
    private static void crossWave(ServerPlayer sp, Jobs.Job j, double P, double mult, int split, Obj wave, double reach, double arc, String life) {
        ServerLevel level = sp.level();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        Vec3 dir = V.unit(new Vec3(f.x, view.y * 0.3, f.z));
        Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.1);
        double half = wave.numOr("width", 1.6) / 2;
        double len = Math.max(1, RuneKit.rayLength(level, from, dir, wave.numOr("range", 8)));
        double speed = 32;
        double s0 = Math.min(1.8, len);
        double fl = Math.max(0.12, (len - s0) / speed) + 0.1;
        for (double tilt : new double[] {35, 145}) {
            JobFx.slash(sp, j, 2.0, 0.3, 2.4, tilt, 0.24);
            JobFx.wave(sp, j, V.add(from, dir, s0), dir, speed, fl, Opt.of("size", 1.9, "spin", tilt, "fade", 0.2));
        }
        JobFx.glint(level, V.add(from, dir, 1.4), 0.6, j, 0.35, Opt.empty());
        SkillFx.play(level, "item.trident.throw", from, 1.25, 0.9);
        SkillFx.play(level, "item.spear.attack_miss", from, 1.55, 0.8);
        SkillFx.play(level, "block.amethyst_block.hit", from, 1.8, 0.6);
        Set<UUID> done = new HashSet<>();
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, dir, len, half)) {
            LivingEntity e = h.e();
            if (!done.add(e.getUUID())) continue;
            JobKit.strikeSplit(sp, e, j, P, mult, split, 3, false, false, life, (int) Math.round(h.t() / speed * 20), d -> {
                if (cnt[0]++ < 6) JobFx.hit(e, j, 1.35);
            });
        }
        for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
            if (!done.add(e.getUUID())) continue;
            JobKit.strikeSplit(sp, e, j, P, mult, split, 3, false, false, life, 0, d -> {
                if (cnt[0]++ < 6) JobFx.hit(e, j, 1.35);
            });
        }
    }

    // ------------------------------------------------------------ 그라니의 질주

    /**
     * 그라니의 질주 (시구르드의 말 그라니처럼 내달림): 앞 오른쪽 (angle 도) dist 칸 → gap 틱 뒤 앞 왼쪽 dist 칸 — Z자 두 번 (한 틱에 한 마디씩 · 벽 · 낭떠러지
     * 앞까지), 지나는 길 가까이 (폭 width) 의 적을 벰 (cuts — 1.5 · 1.5, 2틱 늦게 그어지는 일섬). 끝나면 critFor 틱 안의 다음 칼 평타 한 번은 치명타. 묶였거나
     * 두 길이 모두 막혔으면 쓰지 않음
     */
    static boolean grani(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        double a = Math.toRadians(k.num("angle", 18));
        Vec3 d1 = V.unit(V.add(V.scale(f, Math.cos(a)), r, Math.sin(a)));
        Vec3 d2 = V.unit(V.add(V.scale(f, Math.cos(a)), r, -Math.sin(a)));
        double dist = k.num("dist", 4.5);
        List<Double> cuts = numbers(k, "cuts", List.of(1.5, 1.5));
        int gap = Math.max(10, (int) k.num("gap", 10));
        Vec3 start = sp.position();
        List<Kit.Spot> leg1 = legSpots(level, start, d1, dist);
        Vec3 mid = leg1.isEmpty() ? start : leg1.get(leg1.size() - 1).p();
        List<Kit.Spot> leg2 = legSpots(level, mid, d2, dist);
        if (leg1.isEmpty() && leg2.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        double half = k.num("width", 2.2) / 2;
        Kit.noFall(sp, gap + 40);
        SkillFx.play(level, "entity.horse.gallop", start, 1.2, 0.8);
        SkillFx.play(level, "item.trident.riptide_1", start, 1.5, 0.7);
        int t0 = 0;
        if (!leg1.isEmpty()) {
            leg(sp, j, P, cuts.get(0), start, d1, leg1, half, t0, life, false);
            t0 = gap;
        }
        if (!leg2.isEmpty()) leg(sp, j, P, cuts.get(Math.min(1, cuts.size() - 1)), mid, d2, leg2, half, t0, life, true);
        int end = t0 + 4;
        int critFor = (int) k.num("critFor", 60);
        RuneKit.later(end, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobTraits.sureCritFor(sp, critFor);
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.glint(sp.level(), V.up(V.add(h.hand(), h.f(), 0.7), 0.5), 0.55, j, 0.6, Opt.of("vel", RuneKit.bodyVel(sp), "spin", Kit.rand() * 20 - 10));
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.8, 0.7);
        });
        return true;
    }

    /** 한 마디마다의 자리 (dist 칸 — 벽 · 낭떠러지 앞까지, 0.4칸 못 가면 빈 목록) */
    private static List<Kit.Spot> legSpots(ServerLevel level, Vec3 from, Vec3 d, double dist) {
        double free = Kit.reachFlat(level, from, d, dist);
        return free > 0.4 ? Kit.dashSpots(level, from, d, free) : List.of();
    }

    /** 질주 한 번 (start 틱부터 3틱 — 마디마다 옮김 · 잔상 · 줄기): 길 가까이 적을 지나는 틱에 벰 (그림은 2틱 늦게), 다 지나간 뒤 길에 일섬 */
    private static void leg(ServerPlayer sp, Jobs.Job j, double P, double mult, Vec3 from, Vec3 d, List<Kit.Spot> spots, double half, int start, String life,
            boolean second) {
        double len = spots.get(spots.size() - 1).s();
        int steps = 3;
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        double[] glow = JobKit.glow(j);
        RuneKit.later(start, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            // 출발: 그 자리의 잔상 · 발밑 작은 둥근 베기 · 말발굽 소리 (둘째 질주)
            RuneKit.shadeClone(sp, level, sp.position(), d, 1, SHADE, glow, Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
            JobFx.ring(level, V.up(sp.position(), 0.06), 0.9, j, second, 0.3, Opt.of("alpha", 0.85));
            SkillFx.play(level, "item.spear.attack_miss", sp.position(), second ? 1.65 : 1.45, 0.9);
            if (second) SkillFx.play(level, "entity.horse.gallop", sp.position(), 1.35, 0.7);
        });
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(start + 1 + s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) return;
                ServerLevel lv = sp.level();
                if (ss < marks.size() - 1) RuneKit.shadeClone(sp, lv, prev, d, 1, SHADE, glow, Opt.of("life", 0.32, "fade", 0.6, "alpha", 0.7));
                JobFx.dash(lv, V.up(prev, 0.9), V.up(to, 0.9), j, 0.3, JobFx.hide1p(sp));
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.85);
            });
        }
        // 길 가까이의 적은 그 질주가 시작하는 틱에 고름 (둘째 질주는 10틱 뒤 — 그동안 움직인 적도 맞게)
        RuneKit.later(start, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            int[] cnt = {0};
            for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(from, 1.0), d, len, half)) {
                LivingEntity e = h.e();
                int at = 1 + (int) Math.min(steps - 1, Math.floor(h.t() / Math.max(0.1, len) * steps));
                JobKit.strike(sp, e, j, P, mult, false, false, life, at, dd -> RuneKit.later(2, () -> {
                    if (e.isRemoved() || cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.line(lv, V.add(b, d, -1.3), V.add(b, d, 1.3), j, 0.3, Opt.empty());
                    JobFx.hit(e, j, 1.15);
                    SkillFx.play(lv, "game.player.attack.strong", b, 1.45, 0.7);
                }));
            }
        });
        RuneKit.later(start + steps + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 end = sp.position();
            JobFx.line(sp.level(), V.up(from, 1.0), V.up(V.add(end, d, 0.6), 1.0), j, 0.34, JobFx.hide1p(sp));
            Vec3 look = sp.getViewVector(1F);
            Vec3 c = V.add(V.add(sp.getEyePosition(), look, 2.6), JobFx.screenUp(look), -0.25);
            Vec3 rr = V.rightOf(V.flat(look));
            JobFx.line(sp.level(), V.add(c, rr, -2.6), V.add(c, rr, 2.6), j, 0.3, JobFx.only1p(sp));
        });
    }

    // ------------------------------------------------------------ 만월 강하

    /**
     * 만월 강하: 투지를 모두 써서 (하나당 + perStack) hop 칸 솟아올라 (first 틱) 공중에서 gap 틱마다 세 바퀴 돌며 벰 (hits — 둘레 radius 칸, 땅의 적) → 칼을
     * 꽂으며 내려앉음 (land — 둘레 landRadius 칸 충격). 하나라도 베면 buffTicks 동안 발키리의 가호 (가하는 피해 + buff). 그림: 허리 높이 둥근 베기 · 땅의 옅은
     * 고리 · 눈앞 큰 베기 (도는 쪽) · 내려앉을 때 땅 터짐 · 충격파 · 튀는 돌
     */
    static boolean moon(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> hits = numbers(k, "hits", List.of(2.2, 2.2, 2.2));
        int first = Math.max(2, (int) k.num("first", 4));
        int gap = Math.max(10, (int) k.num("gap", 10));
        double radius = k.num("radius", 4.5);
        double per = k.num("perStack", 0.08);
        int used = JobTraits.useStacks(sp, 99);
        double bonus = 1 + per * used;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        int n = hits.size();
        int landAt = first + n * gap;
        double ground = sp.getY();
        Double gy0 = RuneKit.solidGround(level0, V.up(sp.position(), 0.1), 6);
        double g0 = gy0 != null ? gy0 : ground;
        if (used > 0) JobFx.aura(sp, j, 2.6, (first + 10) / 20.0, Math.min(0.95, 0.5 + 0.06 * used));
        SkillFx.play(level0, "entity.player.attack.sweep", sp.position(), 0.75, 1.0);
        SkillFx.play(level0, "item.trident.riptide_2", sp.position(), 1.4, 0.7);
        // 몸: 솟아오름 (first 틱) → 공중에서 버팀 (도는 동안) → 칼을 꽂으며 내려앉음 (4틱 — 마지막 틱에 땅)
        JobMove.run(sp, JobMove.to(0, first, JobMove.up(sp, k.num("hop", 2.0)), JobMove.OUT), JobMove.hold(first, landAt - 4),
                JobMove.to(landAt - 4, landAt, JobMove.down(sp, g0), JobMove.IN));
        boolean[] cut = {false};
        for (int i = 0; i < n; i++) {
            int ii = i;
            double mult = hits.get(i) * bonus;
            RuneKit.later(first + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 cg = new Vec3(c.x, g0, c.z);
                double q = n <= 1 ? 1 : (double) ii / (n - 1);
                double r0 = radius * (0.75 + 0.25 * q);
                JobFx.ring(level, V.up(c, 1.0), r0, j, ii % 2 == 1, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
                JobFx.ring(level, V.up(cg, 0.06), r0 * 0.92, j, ii % 2 == 1, 0.42, Opt.of("alpha", 0.55, "coreAlpha", 0.45));
                JobFx.slash(sp, j, 2.5, 0.55, 2.0 + 0.3 * q, ii % 2 == 0 ? 0.0 : 180.0, 0.26);
                SkillFx.play(level, "entity.player.attack.sweep", c, 0.85 + ii * 0.12, 1.0);
                SkillFx.play(level, "item.spear.attack_miss", c, 1.0 + ii * 0.18, 0.8);
                int[] cnt = {0};
                for (LivingEntity e : Kit.around(sp, V.up(cg, 1.0), radius, 3.5)) {
                    cut[0] = true;
                    JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                        if (cnt[0]++ < 8) JobFx.hit(e, j, 1.1);
                    });
                }
            });
        }
        // 내려앉음: 칼을 땅에 꽂음 — 둘레 landRadius 칸 충격 · 땅 터짐 · 튀는 돌 · 흙먼지 → 가호
        RuneKit.later(landAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 g = new Vec3(c.x, RuneKit.groundAt(level, c, g0), c.z);
            Vec3 f = Kit.facingOf(sp);
            double lr = k.num("landRadius", 3.5);
            JobFx.slash(sp, j, 2.2, -0.1, 2.6, 95.0, 0.3);
            JobFx.slashCard(level, V.up(V.add(c, f, 0.9), 1.3), f, 95.0, 3.2, 0.3, V.ZERO, j, JobFx.hide1p(sp));
            JobFx.smash(level, V.up(V.add(g, f, 0.8), 0.05), lr, j, 0.5, Opt.empty());
            RuneKit.rockBurst(level, V.up(g, 0.2), 6, 3.8, 0.17);
            SkillFx.sparks(level, V.up(g, 0.2), DUST, 10, 4.0);
            JobKit.shake(sp, 10, 0.25, 0.25);
            SkillFx.play(level, "item.mace.smash_ground", c, 1.0, 1.0);
            SkillFx.play(level, "entity.player.attack.crit", c, 0.9, 0.8);
            int[] cnt = {0};
            List<LivingEntity> targets = Kit.around(sp, V.up(g, 0.5), lr, 2.5);
            if (!targets.isEmpty()) cut[0] = true;
            double buff = k.num("buff", 0);
            boolean[] given = {false};
            Runnable bless = () -> {
                if (given[0] || !cut[0] || buff <= 0 || !RuneKit.sameLife(sp, life)) return;
                given[0] = true;
                mmorpg.fabric.rune.RuneMarks.empower(sp, buff, (int) k.num("buffTicks", 100), "발키리의 가호", JobFx.outer(j), JobFx.inner(j));
                JobFx.valWings(sp, j, 1.0, (int) k.num("buffTicks", 100), 0.75);
                JobFx.feathers(sp.level(), V.up(sp.position(), 2.2), 7, 1.0, 0.17, j, 1.5, JobFx.hide1p(sp));
            };
            int[] pending = {targets.size()};
            for (LivingEntity e : targets) {
                JobKit.strike(sp, e, j, P, k.num("land", 2.4) * bonus, false, false, life, 0, d -> {
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), c)), 0.25, 0.25);
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.25);
                    if (--pending[0] <= 0) bless.run();
                });
            }
            if (targets.isEmpty()) bless.run();
            else RuneKit.later(12, bless);
        });
        return true;
    }

    // ------------------------------------------------------------ 용의 심장

    /**
     * 용의 심장 (시구르드가 먹은 용의 심장): dur 틱 태세 — 칼 평타 두 번째 베기 (echo · JobTraits5), 맞으면 받아치는 베기 (counter — JobTraits5), 투지가 사라지지
     * 않음 (JobTraits), 받는 피해 −guard. 그림: 칼을 땅에 꽂고 기합 — 땅에서 솟는 금빛 기운 · 발밑 칼날 고리 · 차례로 퍼지는 고리 셋 · 1인칭 눈앞 고리 · 용의 울음
     */
    static boolean heart(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        Guards.set(sp, dur, k.num("guard", 0.2), null, p -> JobTraits.stanceOn(p, k.id()));
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        String life = RuneKit.lifeToken(sp);
        // 칼을 땅에 꽂음 (3틱 — 동작과 같은 틱): 땅 터짐 · 튀는 흙 · 솟는 기운
        RuneKit.later(3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 tip = V.add(sp.position(), f, 0.8);
            JobFx.smash(lv, V.up(tip, 0.05), 1.8, j, 0.4, Opt.of("alpha", 0.85));
            SkillFx.sparks(lv, V.up(tip, 0.2), DUST, 8, 3.0);
            RuneKit.godRays(sp, lv, sp.position(), 1.6, 5, JobKit.glow(j), JobKit.core(j), Opt.of("h", 6.0, "life", 0.9, "alpha", 0.7));
            JobFx.aura(sp, j, 2.8, 1.0, 0.95);
            SkillFx.play(lv, "item.mace.smash_ground", sp.position(), 1.3, 0.7);
        });
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(4 + i * 5, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.ring(sp.level(), V.up(sp.position(), 0.06), 1.5 + ii * 0.9, j, ii % 2 == 1, 0.5, Opt.of("alpha", 0.85 - ii * 0.15));
            });
        }
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        JobFx.bladeRing(level, V.up(c, 0.07), 1.7, j, 1.0, 10, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.35, "alpha", 0.95));
        SkillFx.play(level, "entity.ender_dragon.growl", c, 1.6, 0.45);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.2, 1.0);
        SkillFx.play(level, "block.bell.resonate", c, 1.4, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 신들의 황혼

    /** 공중 네 베기의 눈앞 베기 기울기 (오른쪽 → 왼쪽 · 왼쪽 → 오른쪽 · 한 바퀴 · 내려 벰) */
    private static final double[] AIR_TILT = {172, -8, 0, 95};

    /**
     * 신들의 황혼 (라그나로크의 각성 — 사용자 "더 역동적인 모션"): 앞 (100°) dash 칸 안의 가장 가까운 보이는 적 1.5칸 앞까지 rush 틱에 돌진 → 올려 베어 띄움
     * (launch — 둘레 launchRadius 칸, 밀 수 있는 적은 떠오름) → 따라 뛰어올라 (jump 칸) 공중에서 every 틱마다 네 번 벰 (beats — 둘레 radius 칸 · 땅과 공중의
     * 적) → 두 손으로 칼을 내리꽂으며 내려앉음 (final — 둘레 finalRadius 칸 + 앞 finalRange 칸 세 갈래 (가운데 · ± fan 도, 폭 finalWidth) 가 갈라짐 — 갈라짐이
     * 닿는 때, 적마다 한 번 · 큰 숫자). 같은 적은 10틱 넘게 띄움
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        List<Double> beats = numbers(k, "beats", List.of(3.5, 3.5, 3.5, 3.5));
        int n = beats.size();
        int rush = Math.max(2, (int) k.num("rush", 6));
        int first = Math.max(rush + 10, (int) k.num("first", 16));
        int every = Math.max(10, (int) k.num("every", 10));
        int finalDelay = Math.max(10, (int) k.num("finalDelay", 14));
        int lastBeat = first + (n - 1) * every;
        int finalAt = lastBeat + finalDelay;
        double radius = k.num("radius", 4.5);
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        List<LivingEntity> cone = RuneKit.coneTargets(sp, k.num("dash", 7) + 1.5, 100, 4);
        LivingEntity target = cone.isEmpty() ? null : cone.get(0);
        Vec3 f = Kit.facingOf(sp);
        double want = k.num("step", 3);
        if (target != null) {
            f = V.flat(V.sub(target.position(), c0));
            want = Math.max(0, Math.hypot(target.getX() - c0.x, target.getZ() - c0.z) - 1.5 - Kit.bodyPad(target));
            RuneKit.moveTo(sp, c0, V.up(target.position(), target.getBbHeight() * 0.6));
        }
        boolean held = Status.isRooted(sp) || Kit.ridingAny(sp);
        Double gy0 = RuneKit.solidGround(level0, V.up(c0, 0.1), 6);
        Vec3 base = gy0 != null && c0.y - gy0 > 0.05 ? new Vec3(c0.x, gy0, c0.z) : c0;
        double ground = base.y;
        double free = held || want < 0.3 ? 0 : Kit.reachFlat(level0, base, f, want);
        List<Kit.Spot> spots = free > 0.3 ? Kit.dashSpots(level0, base, f, free) : List.of();
        Vec3 stop = spots.isEmpty() ? base : spots.get(spots.size() - 1).p();
        Ult.sky(level0, c0, "rpg:ult_sword", finalAt + 25);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Kit.noFall(sp, finalAt + 30);
        // 몸: 돌진 (rush 틱) → 올려 벰 → 뛰어오름 (rush + 2 → first − 2) → 공중에서 버팀 → 내리꽂으며 내려앉음 (finalAt − 5 → finalAt)
        int rise0 = rush + 2;
        int rise1 = Math.max(rise0 + 3, first - 2);
        if (held) JobMove.run(sp, JobMove.hold(0, rise0), JobMove.to(rise0, rise1, JobMove.up(sp, k.num("jump", 3.0)), JobMove.OUT), JobMove.hold(rise1, finalAt - 5),
                JobMove.to(finalAt - 5, finalAt, JobMove.down(sp, ground), JobMove.IN));
        else JobMove.run(sp, JobMove.to(0, rush, JobMove.at(stop), JobMove.OUT), JobMove.hold(rush, rise0), JobMove.to(rise0, rise1, JobMove.up(sp, k.num("jump",
                3.0)), JobMove.OUT), JobMove.hold(rise1, finalAt - 5), JobMove.to(finalAt - 5, finalAt, JobMove.down(sp, ground), JobMove.IN));
        // 발동: 발을 굴러 땅이 울림 · 금빛 기운 · 1인칭 눈앞 고리 · 소리
        JobFx.smash(level0, V.up(c0, 0.05), 1.6, j, 0.35, Opt.of("alpha", 0.8));
        SkillFx.sparks(level0, V.up(c0, 0.2), DUST, 10, 3.5);
        JobFx.aura(sp, j, 2.6, 0.45, 0.7);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.4);
        SkillFx.play(level0, "mob.ravager.roar", c0, 1.3, 0.5);
        SkillFx.play(level0, "entity.ender_dragon.growl", c0, 1.9, 0.35);
        SkillFx.play(level0, "item.trident.riptide_1", c0, 1.1, 0.8);
        // 돌진: 잔상 · 줄기 · 흙먼지 · 1인칭 빠른 줄
        Vec3[] last = {c0};
        Vec3 face = f;
        for (int t = 1; t <= rush; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 now = sp.position();
                if (now.distanceToSqr(last[0]) > 0.04) {
                    if (tt % 2 == 1) RuneKit.shadeClone(sp, d, last[0], face, 1, SHADE, glow, Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.75));
                    JobFx.dash(d, V.up(last[0], 0.9), V.up(now, 0.9), j, 0.3, JobFx.hide1p(sp));
                    SkillFx.sparks(d, V.up(now, 0.12), DUST, 3, 2.2);
                    JobFx.speed(sp, j, tt == rush ? 0.22 : 0.1, 0.8);
                }
                last[0] = now;
            });
        }
        // 올려 베기 (rush 틱): 아래에서 위로 · 앞 땅에서 솟는 흙 · 둘레 launchRadius 칸 — 밀 수 있는 적은 떠오름
        RuneKit.later(rush, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 ff = Kit.facingOf(sp);
            JobFx.slash(sp, j, 2.3, 0.05, 2.5, 275.0, 0.3);
            JobFx.slashCard(level, V.up(V.add(c, ff, 1.1), 1.3), ff, 275.0, 3.4, 0.3, V.ZERO, j, JobFx.hide1p(sp));
            Vec3 g = V.add(c, ff, 1.5);
            RuneKit.rockBurst(level, V.up(g, 0.2), 6, 3.6, 0.17);
            SkillFx.sparks(level, V.up(g, 0.2), DUST, 9, 3.8);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.7, 1.0);
            SkillFx.play(level, "item.trident.riptide_2", c, 1.3, 0.7);
            JobKit.shake(sp, 8, 0.2, 0.2);
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, k.num("launchRadius", 4), 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("launch", 4), false, false, life, 0, d -> {
                    if (JobKit.pushable(e)) RuneKit.knock(e, ff, 0.05, 0.95); // 띄움 (유틸 lift)
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.2);
                });
            }
        });
        // 공중 네 베기 (first 틱부터 every 틱마다): 둘레 radius 칸 — 땅과 공중의 적 (가운데 = 땅과 몸 사이)
        for (int i = 0; i < n; i++) {
            int ii = i;
            double mult = beats.get(i);
            RuneKit.later(first + i * every, () -> {
                if (RuneKit.sameLife(sp, life)) airBeat(sp, j, P, mult, ii, radius, ground, life);
            });
        }
        // 내리꽂기 준비: 칼을 두 손으로 머리 위로 (칼끝 반짝임 · 기운 · 바람 소리)
        RuneKit.later(finalAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.aura(sp, j, 2.6, 0.45, 0.7);
            RuneKit.rfx(d, "prop", V.up(sp.position(), 3.1), Opt.of("size", 0.36, "life", 0.4, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 20.0, "spinRate",
                    240.0, "color", WHITE, "grow", 0.6, "fade", 0.5, "view", 1.0, "owner", (double) sp.getId()));
            SkillFx.play(d, "item.trident.riptide_3", sp.position(), 1.2, 0.7);
            SkillFx.play(d, "block.beacon.power_select", sp.position(), 1.7, 0.5);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) threeRifts(sp, j, k, P, life);
        });
        return true;
    }

    /** 공중 베기 하나 (i — 0 오른쪽 → 왼쪽 · 1 왼쪽 → 오른쪽 · 2 한 바퀴 · 3 내려 벰): 눈앞 큰 베기 (3인칭 · 다른 사람은 참격 판) · 둘레 radius 칸 */
    private static void airBeat(ServerPlayer sp, Jobs.Job j, double P, double mult, int i, double radius, double ground, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        int kind = i % 4;
        double tilt = AIR_TILT[kind];
        Vec3 cg = new Vec3(c.x, ground, c.z);
        if (kind == 2) {
            JobFx.ring(level, V.up(c, 1.0), radius, j, false, 0.32, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.ring(level, V.up(cg, 0.06), radius * 0.92, j, false, 0.42, Opt.of("alpha", 0.55, "coreAlpha", 0.45));
            JobFx.slash(sp, j, 2.5, 0.45, 2.4, tilt, 0.26);
        } else {
            JobFx.slash(sp, j, 2.4, kind == 3 ? -0.05 : 0.3, kind == 3 ? 2.5 : 2.6, tilt, 0.3);
            JobFx.slashCard(level, V.up(V.add(c, f, 1.1), 1.05), f, tilt, 3.4, 0.3, V.ZERO, j, JobFx.hide1p(sp));
        }
        // 땅에 내리는 베인 그림자 (공중 베기가 땅의 적에 닿음 — 옅은 고리)
        JobFx.ring(level, V.up(cg, 0.07), radius * 0.6, j, kind % 2 == 1, 0.3, Opt.of("alpha", 0.45, "coreAlpha", 0.35));
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.8 + i * 0.08, 1.0);
        SkillFx.play(level, "item.spear.attack_miss", c, 1.0 + i * 0.12, 0.85);
        JobKit.shake(sp, 8, 0.12 + 0.03 * i, 0.15);
        double mid = (c.y + ground) / 2;
        double vertical = Math.max(2.5, (c.y - ground) / 2 + 2.2);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, new Vec3(c.x, mid, c.z), radius, vertical)) {
            JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.15);
            });
        }
    }

    /**
     * 신들의 황혼 마지막: 두 손으로 칼을 내리꽂음 — 둘레 finalRadius 칸 (지금) + 앞 finalRange 칸 세 갈래 (가운데 · ± fan 도 — 폭 finalWidth, 갈라짐이
     * SPLIT_SPEED 로 뻗어 닿는 때) 의 적마다 한 번 final (큰 숫자 · 밀려남). 그림: 눈앞 세로 베기 · 땅 터짐 · 충격파 · 튀는 돌 · 흙먼지 · 번쩍임 · 화면 흔들림 ·
     * 세 갈래 균열 (rfx_fissure — 어두운 틈 · 금빛 · 흰 금) 과 그 위로 튀는 돌
     */
    private static void threeRifts(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double[] glow = JobKit.glow(j);
        double R = k.num("finalRadius", 5);
        double range = k.num("finalRange", 12);
        double half = k.num("finalWidth", 2.4) / 2;
        double fan = Math.toRadians(k.num("fan", 25));
        Vec3 g = new Vec3(c.x, RuneKit.groundAt(level, c, c.y), c.z);
        Vec3 tip = V.add(g, f, 1.2);
        JobFx.slash(sp, j, 2.3, -0.1, 3.1, 95.0, 0.34);
        JobFx.slashCard(level, V.up(V.add(c, f, 1.0), 1.4), f, 95.0, 4.0, 0.32, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.smash(level, V.up(tip, 0.05), R, j, 0.6, Opt.empty());
        JobFx.burst(level, V.up(tip, 0.4), 1.7, j, 0.35, Opt.empty());
        RuneKit.rockBurst(level, V.up(tip, 0.2), 12, 5.2, 0.2);
        SkillFx.sparks(level, V.up(tip, 0.2), DUST, 16, 5);
        Ult.flash(level, V.up(c, 1.0), glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, c, 18, 0.5, 0.6);
        SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.75, 1.2);
        SkillFx.play(level, "random.explode", c, 0.7, 0.9);
        SkillFx.play(level, "entity.player.attack.crit", c, 0.6, 1.0);
        Vec3 r = V.rightOf(f);
        Vec3[] dirs = {f, V.unit(V.add(V.scale(f, Math.cos(fan)), r, Math.sin(fan))), V.unit(V.add(V.scale(f, Math.cos(fan)), r, -Math.sin(fan)))};
        double mult = k.num("final", 22);
        Set<UUID> done = new HashSet<>();
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, R, 3)) {
            done.add(e.getUUID());
            JobKit.strike(sp, e, j, P, mult, false, true, life, 0, d -> finalHitFx(e, j, c, cnt));
        }
        for (Vec3 d : dirs) {
            double len = Math.max(2, RuneKit.rayLength(level, V.up(g, 0.6), d, range));
            rift(level, g, d, len, glow);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(g, 0.6), d, len, half)) {
                LivingEntity e = h.e();
                if (!done.add(e.getUUID())) continue;
                JobKit.strike(sp, e, j, P, mult, false, true, life, (int) Math.round(h.t() / SPLIT_SPEED * 20), dd -> finalHitFx(e, j, c, cnt));
            }
        }
    }

    /** 앞으로 뻗는 균열 한 갈래 (조각마다 그 자리에 닿는 때 — 어두운 틈 · 금빛 · 흰 금, 땅에 눕힘) · 튀는 돌 · 흙먼지 */
    private static void rift(ServerLevel level, Vec3 g, Vec3 f, double len, double[] glow) {
        double piece = 2.5;
        double spin = Math.toDegrees(Math.atan2(f.z, -f.x));
        for (double s = 0.6; s < len; s += piece) {
            double a0 = s;
            double a1 = Math.min(len, s + piece * 1.3);
            int at = (int) Math.round(a0 / SPLIT_SPEED * 20);
            RuneKit.later(at, () -> {
                Vec3 mid = V.add(g, f, (a0 + a1) / 2);
                Vec3 m = new Vec3(mid.x, RuneKit.groundAt(level, mid, g.y), mid.z);
                double size = (a1 - a0) / 2 * 1.25;
                Opt base = Opt.of("dir", V.UP, "size", size, "life", 2.0, "fade", 0.35, "spin", spin + (Kit.rand() < 0.5 ? 180 : 0));
                RuneKit.rfx(level, "fissure", V.up(m, 0.035), base.with("cell", 0.0, "color", RIFT, "alpha", 0.92));
                RuneKit.rfx(level, "fissure", V.up(m, 0.055), base.with("cell", 1.0, "color", glow, "alpha", 0.95));
                RuneKit.rfx(level, "fissure", V.up(m, 0.075), base.with("cell", 2.0, "color", WHITE, "alpha", 1.0));
                RuneKit.glowCrack(level, m, 1.5, glow, WHITE, Opt.of("life", 1.6, "v", a0 % 5 < 2.5 ? 1.0 : 0.0));
                RuneKit.rockBurst(level, V.up(m, 0.15), 3, 3.0, 0.15);
                SkillFx.sparks(level, V.up(m, 0.15), DUST, 4, 2.5);
            });
        }
        RuneKit.later((int) Math.round(len / SPLIT_SPEED * 20), () -> SkillFx.play(level, "item.mace.smash_ground", V.add(g, f, len), 0.95, 0.7));
    }

    /** 내리꽂음에 맞은 적: 타격 그림 · 터짐 (8명까지) · 밀려남 (가운데에서 바깥으로 — 연출) */
    private static void finalHitFx(LivingEntity e, Jobs.Job j, Vec3 c, int[] cnt) {
        if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), c)), 0.35, 0.3);
        if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
        JobFx.hit(e, j, 1.7);
        JobFx.burst(lv, Kit.bodyPoint(e), 1.0 + Kit.bodyPad(e) * 0.5, j, 0.32, Opt.empty());
    }
}
