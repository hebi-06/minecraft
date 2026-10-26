package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Js;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
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
 * 0.29.0 암살자 직업 스킬 (jobs.json assassin — 자홍 · 짙은 보라, 특성 「표식」 — 단검 평타가 찍는 머리 위 꽃): 낙화 · 잔영 · 귀환 비수 · 암영 · 흑야.
 * 피해 = 위력 P × 배율 (JobKit.strike — 같은 적을 다시 칠 때는 몹 무적 시간 뒤로, 빠른 연타는 쌓이는 숫자 JobKit.strikeSplit).
 * 연출 = 메이플식 2D 그림 (JobFx — 꽃 · 꽃잎 · 엇갈린 베기 · 도는 비수 · 그림자 연기 · 분신, 룬 문양 · 룬 글자 · 마법진은 쓰지 않음).
 */
final class AssassinSkills {
    private AssassinSkills() {}

    static void register() {
        JobSystem.register("assassin_bloom", AssassinSkills::bloom);
        JobSystem.register("assassin_shade", AssassinSkills::shade);
        JobSystem.register("assassin_daggers", AssassinSkills::daggers);
        JobSystem.register("assassin_veil", AssassinSkills::veil);
        JobSystem.register("assassin_ult", AssassinSkills::ult);
    }

    // ------------------------------------------------------------ 낙화

    /**
     * 낙화: 겨눈 적 (range 칸) 의 표식을 모두 터뜨림 — 피해 base + per × 표식 (한 번에 넣고 숫자는 1 + 표식 줄, gap 틱마다 한 줄). 머리 위 꽃이 꽃잎으로 흩어지고
     * 줄마다 엇갈린 베기 · 꽃잎 (표식이 가득이면 마지막에 큰 터짐 · 큰 숫자). 겨눈 적이 없으면 쓰지 않음
     */
    static boolean bloom(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 5);
        LivingEntity e = Kit.pickTarget(sp, range, 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다 (" + Js.str(range) + "칸)", 30);
            return false;
        }
        ServerLevel level = sp.level();
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        // 꽃이 터짐: 머리 위의 꽃 n 송이가 꽃잎으로 흩어짐 (떼어 내기 전 자리)
        int n = JobTraits3.marks(sp, e);
        Vec3 over = RuneKit.overHead(e, 0.5);
        Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
        for (int i = 0; i < n; i++) JobFx.petals(level, V.add(over, right, (i - (n - 1) / 2.0) * 0.32), 4, 2.6, 0.13, j, 0.7, Opt.empty());
        n = JobTraits3.takeMarks(sp, e);
        int lines = 1 + n;
        int gap = Math.max(1, (int) k.num("gap", 2));
        double mult = k.num("base", 0.5) + k.num("per", 0.85) * n;
        boolean full = n >= max;
        String life = RuneKit.lifeToken(sp);
        JobKit.strikeSplit(sp, e, j, P, mult, lines, gap, false, full, life, 0, null);
        // 1인칭: 눈앞에서 빠르게 엇갈려 긋는 두 베기
        JobFx.slash(sp, j, 1.8, 0.3, 1.15, 20.0, 0.18);
        RuneKit.later(gap, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.8, 0.3, 1.15, 200.0, 0.18);
        });
        int last = lines - 1;
        for (int i = 0; i < lines; i++) {
            int ii = i;
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                Vec3 at = Kit.bodyPoint(e);
                Vec3 jit = new Vec3((Kit.rand() - 0.5) * 0.4, (Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.5) * 0.4);
                double size = 0.7 + Kit.bodyPad(e) * 0.4 + (ii == last ? 0.3 : 0);
                JobFx.cross(lv, V.add(at, jit), size, j, 0.26, Opt.of("vel", RuneKit.entVel(e)));
                JobFx.petals(lv, at, ii == last ? 8 : 3, 3.0, 0.13, j, 0.65, Opt.empty());
                SkillFx.play(lv, "entity.player.attack.sweep", at, 1.3 + ii * 0.12, 0.7);
                if (ii == last) {
                    SkillFx.play(lv, "block.cherry_leaves.break", at, 1.0, 1.0);
                    if (full) {
                        JobFx.burst(lv, at, 1.3 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
                        JobFx.blossom(lv, at, 0.9, j, 0.45, Opt.of("grow", 0.6, "fade", 0.5));
                        SkillFx.play(lv, "entity.player.attack.crit", at, 0.8, 1.0);
                    }
                }
            });
        }
        JobSystem.note(sp, "§d표식 " + n + " §7→ " + lines + "연격" + (full ? " §d(가득)" : ""));
        return true;
    }

    // ------------------------------------------------------------ 잔영

    /**
     * 잔영: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 steps 틱에 순간 이동 (적은 지나침) — 원래 자리에 그림자 분신 life 틱 (둘레 taunt 칸에서 나와 싸우던 몹이
     * 분신 쪽으로 감 — Monsters.decoy), 적이 touch 칸 안에 닿거나 life 틱이 지나면 분신이 터짐 (둘레 radius 칸 blast)
     */
    static boolean shade(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double free = Kit.reachFlat(level, start, f, k.num("dist", 6));
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        double len = spots.get(spots.size() - 1).s();
        int steps = Math.max(1, (int) k.num("steps", 3));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        String life = RuneKit.lifeToken(sp);
        int clone = Math.max(4, (int) k.num("life", 40));
        Kit.noFall(sp, steps + 20);
        // 분신: 나와 싸우던 몹이 그 자리로 (분신이 터지면 다시 나에게)
        Monsters.decoy(sp, start, k.num("taunt", 6), clone);
        boolean[] blown = {false};
        double[] dark = JobKit.core(j);
        // 출발: 그림자 연기 · 1인칭 눈앞 연기 · 소리
        JobFx.smoke(level, V.up(start, 1.0), 1.0, j, 0.55, JobFx.hide1p(sp));
        SkillFx.play(level, "entity.enderman.teleport", start, 1.3, 0.7);
        SkillFx.play(level, "mob.phantom.flap", start, 1.5, 0.5);
        // 분신 그림 (8틱마다 다시 — 그림자 · 발밑 연기 · 머리 위 꽃 하나)
        for (int t = 0; t < clone; t += 8) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (blown[0]) return;
                RuneKit.shadeClone(sp, level, start, f, 2, dark, JobKit.glow(j), Opt.of("life", 0.5, "fade", 0.3, "alpha", 0.9));
                if (tt % 16 == 0) JobFx.smoke(level, V.up(start, 0.25), 0.75, j, 0.6, Opt.of("alpha", 0.55));
                JobFx.blossom(level, V.up(start, 2.3), 0.16, j, 0.45, Opt.of("alpha", 0.85));
            });
        }
        // 닿으면 터짐 (2틱마다 확인) · life 틱이 지나면 터짐
        for (int t = 2; t < clone; t += 2) {
            RuneKit.later(t, () -> {
                if (blown[0] || !RuneKit.sameLife(sp, life)) return;
                if (!Kit.around(sp, start, k.num("touch", 1.4), 2.0).isEmpty()) {
                    blown[0] = true;
                    shadeBlast(sp, j, k, P, start, life);
                }
            });
        }
        RuneKit.later(clone, () -> {
            if (blown[0]) return;
            blown[0] = true;
            if (RuneKit.sameLife(sp, life)) shadeBlast(sp, j, k, P, start, life);
            else Monsters.endDecoy(sp);
        });
        // 순간 이동: 한 틱에 한 마디 — 지나온 줄기 (3인칭) · 1인칭 빠른 줄 · 도착 연기
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) return;
                JobFx.line(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.25, JobFx.hide1p(sp));
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.7);
                if (ss == marks.size() - 1) {
                    JobFx.smoke(d, V.up(to, 0.8), 0.8, j, 0.5, JobFx.hide1p(sp).with("alpha", 0.8));
                    JobFx.petals(d, V.up(to, 1.2), 5, 2.0, 0.12, j, 0.6, Opt.empty());
                }
            });
        }
        return true;
    }

    /** 분신이 터짐: 미끼가 끝남 · 연기 · 터짐 · 땅의 둥근 베기 · 꽃잎 — 둘레 radius 칸 blast */
    private static void shadeBlast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 at, String life) {
        Monsters.endDecoy(sp);
        ServerLevel level = sp.level();
        double r = k.num("radius", 3);
        Vec3 c = V.up(at, 1.0);
        JobFx.burst(level, c, 1.5, j, 0.35, Opt.empty());
        JobFx.smoke(level, c, 1.7, j, 0.75, Opt.empty());
        JobFx.ring(level, V.up(at, 0.08), r, j, false, 0.4, Opt.empty());
        JobFx.petals(level, c, 10, 4.0, 0.14, j, 0.7, Opt.empty());
        SkillFx.sparks(level, c, JobKit.glow(j), 10, 4);
        SkillFx.play(level, "random.explode", at, 1.5, 0.5);
        SkillFx.play(level, "entity.enderman.teleport", at, 0.6, 0.8);
        for (LivingEntity e : Kit.around(sp, at, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("blast", 2.85), false, false, life, 0, d -> {
                if (RuneKit.fxQuota("jobShadeBlast", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }

    // ------------------------------------------------------------ 귀환 비수

    /**
     * 귀환 비수: 허리 높이에서 둘레 count 방향으로 도는 비수 (range 칸 — 벽 앞까지) 가 flight 틱에 날아가 hold 틱 멈췄다가 지금 자리로 flight 틱에 되돌아옴 —
     * 갈 때 · 올 때 길 (폭 width) 의 적마다 한 번씩 (out · back — 같은 길에서는 지나가는 때에 맞춰)
     */
    static boolean daggers(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        int count = Math.max(1, (int) k.num("count", 8));
        double range = k.num("range", 8);
        int flight = Math.max(2, (int) k.num("flight", 8));
        int hold = Math.max(0, (int) k.num("hold", 4));
        double half = k.num("width", 1.4) / 2;
        double h0 = 1.0;
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        Vec3 o0 = V.up(c0, h0);
        Vec3 f = Kit.facingOf(sp);
        Vec3[] ends = new Vec3[count];
        Set<UUID> outHit = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Vec3 d = V.turnY(f, i * 360.0 / count);
            double len = Math.max(1.0, RuneKit.rayLength(level, o0, d, range));
            ends[i] = V.add(o0, d, len);
            JobFx.dagger(level, V.add(o0, d, 0.5), ends[i], flight, 0.42, j, Opt.empty());
            for (Kit.LineHit lh : Kit.lineHits(sp, o0, d, len, half)) {
                LivingEntity e = lh.e();
                if (!outHit.add(e.getUUID())) continue;
                int at = (int) Math.round(flight * Math.min(1, lh.t() / len));
                JobKit.strike(sp, e, j, P, k.num("out", 4.275), false, false, life, at, x -> {
                    if (RuneKit.fxQuota("jobDaggerOut", 6)) JobFx.cross(e.level() instanceof ServerLevel lv ? lv : level, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.3, j,
                            0.22, Opt.empty());
                });
            }
        }
        // 던짐: 1인칭 눈앞 베기 · 몸 둘레 고리 (3인칭) · 손의 꽃잎 · 소리
        JobFx.slash(sp, j, 1.7, 0.4, 1.3, 15.0, 0.2);
        JobFx.ring(level, o0, 1.4, j, false, 0.3, JobFx.hide1p(sp));
        SkillFx.play(level, "item.trident.throw", c0, 1.6, 0.8);
        SkillFx.play(level, "item.trident.throw", c0, 1.9, 0.6);
        // 멈춤: 날 끝에서 도는 채로 hold 틱
        RuneKit.later(flight, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (Vec3 end : ends) JobFx.dagger(sp.level(), end, end, hold + 1, 0.42, j, Opt.of("fade", 0.05));
        });
        // 되돌아옴: 지금 자리로 (길 위의 적마다 back)
        RuneKit.later(flight + hold, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 me = V.up(sp.position(), h0);
            Set<UUID> backHit = new HashSet<>();
            for (Vec3 from : ends) {
                Vec3 dv = V.sub(me, from);
                double len = V.len(dv);
                if (len < 0.6) continue;
                Vec3 u = V.scale(dv, 1 / len);
                JobFx.dagger(d, from, V.add(me, u, -0.5), flight, 0.42, j, Opt.empty());
                for (Kit.LineHit lh : Kit.lineHits(sp, from, u, len, half)) {
                    LivingEntity e = lh.e();
                    if (!backHit.add(e.getUUID())) continue;
                    int at = (int) Math.round(flight * Math.min(1, lh.t() / len));
                    JobKit.strike(sp, e, j, P, k.num("back", 4.275), false, false, life, at, x -> {
                        if (RuneKit.fxQuota("jobDaggerBack", 6)) JobFx.cross(e.level() instanceof ServerLevel lv ? lv : d, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.3,
                                j, 0.22, Opt.empty());
                    });
                }
            }
            SkillFx.play(d, "item.trident.return", sp.position(), 1.4, 0.8);
        });
        // 잡음: 손 앞 작은 터짐 · 꽃잎
        RuneKit.later(flight * 2 + hold, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.burst(sp.level(), V.up(V.add(h.hand(), h.f(), 0.5), 0.3), 0.32, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.petals(sp.level(), V.up(sp.position(), 1.0), 4, 1.6, 0.11, j, 0.5, JobFx.hide1p(sp));
            SkillFx.play(sp.level(), "item.trident.hit_ground", sp.position(), 1.6, 0.5);
        });
        return true;
    }

    // ------------------------------------------------------------ 암영

    /**
     * 암영: dur 틱 은신 (투명 · 몹이 노리던 것을 놓음 — JobTraits3) — 은신 중 첫 공격 (단검 평타 · 직업 스킬의 첫 타 하나) 치명타 확정 · 피해 × first, 공격하면 풀림 →
     * 풀린 뒤 speedFor 틱 이동 + speed. 그림: 그림자 연기 · 흩어지는 분신 · 꽃잎 · 1인칭 눈앞 연기와 고리, 은신 동안 1인칭 화면 귀퉁이 연기 (JobTraits3.stanceFx)
     */
    static boolean veil(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        JobTraits3.veilStart(sp, j, k);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.smoke(level, V.up(c, 1.0), 1.1, j, 0.7, JobFx.hide1p(sp));
        JobFx.smoke(level, V.up(c, 0.35), 1.4, j, 0.8, JobFx.hide1p(sp).with("alpha", 0.8));
        // 흩어지는 분신은 몸 조금 뒤 (1인칭 카메라가 그림 속에 들지 않게)
        RuneKit.shadeClone(sp, level, V.add(c, Kit.facingOf(sp), -0.7), Kit.facingOf(sp), 0, JobKit.core(j), JobKit.glow(j), Opt.of("life", 0.6, "fade", 0.7, "alpha",
                0.85, "grow", 0.15));
        JobFx.petals(level, V.up(c, 1.2), 8, 2.2, 0.12, j, 0.8, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.45);
        Vec3 look = sp.getViewVector(1F);
        JobFx.smoke(level, V.add(sp.getEyePosition(), look, 1.8), 0.9, j, 0.45, JobFx.only1p(sp).with("alpha", 0.6));
        DamageNumbers.showFace(sp, "§5§l암영 §7— 첫 공격 ×" + Js.str(Math.round(JobUpgrades.num(sp, k, "first", 2.5) * 100) / 100.0) + " 치명타", 2.4, 0, -0.3);
        SkillFx.play(level, "entity.enderman.teleport", c, 0.7, 0.7);
        SkillFx.play(level, "mob.phantom.flap", c, 0.8, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 흑야

    /**
     * 흑야: 둘레 radius 칸이 밤처럼 어두워지고 every 틱마다 (slashes 번) 표식 찍힌 적 (없으면 가까운 targets 명 — 보이는 적) 곁으로 차례로 순간 이동하며 벰 (slash —
     * 같은 적은 10틱 넘게 띄움: 적이 하나면 두 번씩 한꺼번에 · 숫자 두 줄) → 마지막 베기 뒤 처음 자리로 돌아와 finalDelay 틱 뒤 벤 자국이 한꺼번에 터짐 (final — 큰 숫자).
     * 둘레에 적이 없으면 쓰지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        double radius = k.num("radius", 12);
        int maxN = Math.max(1, (int) k.num("targets", 6));
        List<LivingEntity> around = Kit.visibleFrom(sp, sp.getEyePosition(), JobKit.near(sp, radius, 24));
        List<LivingEntity> marked = new ArrayList<>();
        for (LivingEntity e : around) if (JobTraits3.marks(sp, e) > 0) marked.add(e);
        List<LivingEntity> pool = marked.isEmpty() ? around : marked;
        List<LivingEntity> targets = new ArrayList<>(pool.subList(0, Math.min(maxN, pool.size())));
        if (targets.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 둘레에 적이 없습니다", 30);
            return false;
        }
        int slashes = Math.max(1, (int) k.num("slashes", 6));
        int every = Math.max(1, (int) k.num("every", 5));
        int finalDelay = (int) k.num("finalDelay", 15);
        int lastSlash = (slashes - 1) * every;
        int finalAt = lastSlash + finalDelay;
        double slash = k.num("slash", 3);
        String life = RuneKit.lifeToken(sp);
        Vec3 home = sp.position();
        double[] glow = JobKit.glow(j);
        Ult.sky(level0, home, "rpg:ult_night", finalAt + 25);
        Kit.noFall(sp, finalAt + 30);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        // 시작: 그림자 연기 · 꽃잎 소용돌이 · 어두운 번쩍임 · 1인칭 눈앞 고리
        JobFx.smoke(level0, V.up(home, 1.0), 1.6, j, 0.8, JobFx.hide1p(sp));
        JobFx.petals(level0, V.up(home, 1.2), 14, 3.0, 0.14, j, 0.9, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        Ult.flash(level0, home, JobKit.core(j), Opt.of("radius", 6.0, "time", new double[] {0.03, 0.08, 0.4}), sp);
        SkillFx.play(level0, "entity.enderman.teleport", home, 0.5, 1.0);
        SkillFx.play(level0, "mob.wither.shoot", home, 1.6, 0.4);
        int n = targets.size();
        for (int i = 0; i < slashes; i++) {
            int ii = i;
            LivingEntity e = targets.get(i % n);
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                blinkSlash(sp, j, e, ii);
                if (ii > 0) JobKit.motion(sp, "assassin_ult:slash" + (ii % 2)); // 0.47.0 X 자 · 되돌려 긋기 (번갈아 — 첫 베기는 쓸 때의 동작)
            });
        }
        // 피해: 같은 적을 every × n 틱 간격으로 — 적이 하나면 두 베기를 한 번에 (숫자 두 줄, every 틱 간격)
        if (n == 1) {
            LivingEntity e = targets.get(0);
            for (int i = 0; i < slashes; i += 2) {
                int c = Math.min(2, slashes - i);
                JobKit.strikeSplit(sp, e, j, P, slash * c, c, every, false, false, life, i * every, null);
            }
        } else {
            for (int i = 0; i < slashes; i++) JobKit.strike(sp, targets.get(i % n), j, P, slash, false, false, life, i * every, null);
        }
        // 처음 자리로 돌아옴 (벤 적들을 바라봄)
        RuneKit.later(lastSlash + 5, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 sum = Vec3.ZERO;
            int m = 0;
            for (LivingEntity e : targets) {
                if (e.isRemoved()) continue;
                sum = V.add(sum, Kit.bodyPoint(e));
                m++;
            }
            Vec3 prev = sp.position();
            Vec3 focus = m > 0 ? V.scale(sum, 1.0 / m) : V.add(home, Kit.facingOf(sp), 4);
            float[] rot = Kit.lookRotation(V.up(home, sp.getEyeHeight()), focus);
            if (!Kit.moveTo(sp, home, rot[0], Math.max(-20F, Math.min(40F, rot[1])))) Kit.moveTo(sp, home);
            ServerLevel d = sp.level();
            RuneKit.shadeClone(sp, d, prev, Kit.facingOf(sp), 1, JobKit.core(j), glow, Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.8));
            JobFx.smoke(d, V.up(home, 0.9), 0.9, j, 0.5, JobFx.hide1p(sp));
            SkillFx.play(d, "entity.enderman.teleport", home, 1.2, 0.6);
            JobKit.motion(sp, "assassin_ult:home"); // 0.47.0 단검을 털고 거꾸로 쥐어 내리는 동작
        });
        JobKit.motionLater(sp, "assassin_ult:final", finalAt - 3, life); // 0.47.0 단검을 허리로 탁 거두는 동작 (3틱 뒤 터짐)
        // 벤 자국이 한꺼번에 터짐
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Ult.flash(d, sp.position(), glow, Opt.empty(), sp);
            RuneKit.shakeAround(d, sp.position(), 16, 0.35, 0.35);
            SkillFx.play(d, "entity.player.attack.crit", sp.position(), 0.6, 1.0);
            SkillFx.play(d, "random.explode", sp.position(), 1.4, 0.6);
            SkillFx.play(d, "block.cherry_leaves.break", sp.position(), 0.8, 1.0);
            Vec3 look = sp.getViewVector(1F);
            JobFx.burst(d, V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), -0.3), 1.4, j, 0.4, JobFx.only1p(sp));
            for (LivingEntity e : targets) {
                if (e.isRemoved()) continue;
                Vec3 at = Kit.bodyPoint(e);
                double s = 1.2 + Kit.bodyPad(e) * 0.6;
                JobFx.cross(d, at, s, j, 0.35, Opt.empty());
                RuneKit.later(2, () -> JobFx.cross(d, at, s * 0.8, j, 0.3, Opt.of("spin", 45.0)));
                JobFx.burst(d, at, s, j, 0.4, Opt.empty());
                JobFx.petals(d, at, 10, 4.5, 0.15, j, 0.8, Opt.empty());
                JobKit.strike(sp, e, j, P, k.num("final", 20), false, true, life, 0, null);
            }
        });
        return true;
    }

    /** 흑야의 베기 한 번: 적 곁 (지금 쪽에서 돌아간 자리 — 1.8칸 + 몸) 으로 순간 이동해 적을 바라봄 · 엇갈린 베기 · 잔상 · 꽃잎 */
    private static void blinkSlash(ServerPlayer sp, Jobs.Job j, LivingEntity e, int i) {
        ServerLevel level = sp.level();
        Vec3 from = sp.position();
        Vec3 away = Math.hypot(from.x - e.getX(), from.z - e.getZ()) < 0.1 ? V.scale(Kit.facingOf(sp), -1) : V.flat(V.sub(from, e.position()));
        Vec3 spot = null;
        for (double turn : new double[] {i % 2 == 0 ? 140 : -140, i % 2 == 0 ? -140 : 140, 90, -90, 0}) {
            Vec3 dir = V.turnY(away, turn);
            Vec3 want = V.add(e.position(), dir, 1.8 + Kit.bodyPad(e));
            Vec3 s = Kit.standSpot(level, new Vec3(want.x, Math.max(want.y, e.getY()) + 0.5, want.z), 2.5);
            if (s != null && Math.abs(s.y - e.getY()) < 2.5) {
                spot = s;
                break;
            }
        }
        Vec3 focus = V.up(e.position(), e.getBbHeight() * 0.55);
        if (spot != null) {
            float[] rot = Kit.lookRotation(V.up(spot, sp.getEyeHeight()), focus);
            if (!Kit.moveTo(sp, spot, rot[0], Math.max(-25F, Math.min(45F, rot[1])))) spot = null;
        }
        Vec3 now = sp.position();
        // 떠난 자리: 잔상 · 연기, 지나간 줄 (3인칭)
        RuneKit.shadeClone(sp, level, from, V.flat(V.sub(e.position(), from)), 1 + i % 3, JobKit.core(j), JobKit.glow(j), Opt.of("life", 0.4, "fade", 0.6, "alpha",
                0.85));
        if (spot != null) JobFx.line(level, V.up(from, 1.0), V.up(now, 1.0), j, 0.22, JobFx.hide1p(sp));
        // 벰: 적 몸의 엇갈린 베기 · 1인칭 눈앞 베기 · 꽃잎
        Vec3 at = Kit.bodyPoint(e);
        JobFx.cross(level, at, 0.9 + Kit.bodyPad(e) * 0.5, j, 0.28, Opt.of("vel", RuneKit.entVel(e)));
        JobFx.slash(sp, j, 1.9, 0.25, 1.35, i % 2 == 0 ? 25.0 : 205.0, 0.2);
        JobFx.petals(level, at, 4, 3.0, 0.13, j, 0.6, Opt.empty());
        JobFx.speed(sp, j, 0.12, 0.7);
        SkillFx.play(level, "entity.player.attack.sweep", at, 1.4 + i * 0.06, 0.8);
        SkillFx.play(level, "entity.enderman.teleport", now, 1.8, 0.35);
    }
}
