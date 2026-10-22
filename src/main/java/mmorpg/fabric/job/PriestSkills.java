package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
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
 * 0.30.0 사제 직업 스킬 (jobs.json priest — 초록 · 옅은 금빛, 특성 「신앙」): 치유의 손길 · 빛의 걸음 · 성가 · 성흔 · 새벽의 종.
 * 회복 · 보호막은 받는 사람의 최대 체력 비율 (위력과 무관), 피해 = 위력 P × 배율 × 신앙 (JobSystem — 공격 스킬인 성가 · 새벽의 종만).
 * 연출 = 메이플식 2D 그림 (JobFx — 회복 십자 · 음표 · 성흔 · 큰 종 · 빛의 방패, 룬 문양 · 마법진은 쓰지 않음).
 */
final class PriestSkills {
    private PriestSkills() {}

    /** 종 · 새벽빛 (금빛 · 흰빛) */
    private static final double[] GOLD = {1, 0.84, 0.42};
    private static final double[] DAWN = {1, 0.97, 0.86};

    static void register() {
        JobSystem.register("priest_heal", PriestSkills::heal);
        JobSystem.register("priest_step", PriestSkills::step);
        JobSystem.register("priest_hymn", PriestSkills::hymn);
        JobSystem.register("priest_stigma", PriestSkills::stigma);
        JobSystem.register("priest_ult", PriestSkills::ult);
    }

    /** 홀 끝 (손 앞) */
    private static Vec3 tip(ServerPlayer sp) {
        RuneKit.Hand h = RuneKit.handOf(sp);
        return V.up(V.add(h.hand(), h.f(), 0.45), 0.25);
    }

    // ------------------------------------------------------------ 치유의 손길

    /**
     * 치유의 손길: 겨눈 아군 (range 칸 — 없으면 나) 최대 체력 heal 회복 · guardFor 틱 받는 피해 − guard. 그림: 홀 끝에서 그 아군까지 빛줄기 · 머리 위 큰 회복 십자 ·
     * 솟는 빛 알갱이 (나면 1인칭 눈앞 십자 · 몸의 빛)
     */
    static boolean heal(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        ServerPlayer aimed = JobTraits4.aimAlly(sp, k.num("range", 16));
        ServerPlayer to = aimed != null ? aimed : sp;
        JobTraits4.heal(to, RpgHealth.maxOf(to) * k.num("heal", 0.12));
        Guards.set(to, (int) k.num("guardFor", 60), k.num("guard", 0.1), null, null);
        Vec3 head = V.up(to.position(), to.getBbHeight() + 0.55);
        if (aimed != null) {
            JobFx.beam(level, tip(sp), V.up(to.position(), 1.1), j, 2.0, 0.3, Opt.of("alpha", 0.75));
            JobFx.healCross(level, head, 0.55, j, 0.7, Opt.of("vel", V.add(RuneKit.bodyVel(to), new Vec3(0, 0.5, 0))));
            JobFx.burst(level, V.up(to.position(), 1.0), 0.8, j, 0.3, Opt.of("vel", RuneKit.bodyVel(to)));
        } else {
            Vec3 look = sp.getViewVector(1F);
            JobFx.healCross(level, V.add(V.add(sp.getEyePosition(), look, 2.3), JobFx.screenUp(look), -0.15), 0.5, j, 0.6, JobFx.only1p(sp).with("vel",
                    RuneKit.bodyVel(sp)));
            JobFx.healCross(level, head, 0.55, j, 0.7, JobFx.hide1p(sp).with("vel", V.add(RuneKit.bodyVel(sp), new Vec3(0, 0.5, 0))));
            JobFx.aura(sp, j, 2.6, 0.6, 0.8);
        }
        for (int i = 0; i < 6; i++) {
            double a = Kit.rand() * Math.PI * 2;
            Vec3 p = V.up(V.add(to.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 0.5 + Kit.rand() * 0.4), 0.2 + Kit.rand() * 1.4);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 1.4, "size", 0.13, "grow", 0.5, "life", 0.6, "color", i % 2 == 0 ? JobKit.glow(j)
                    : JobKit.core(j), "alpha", 0.95));
        }
        SkillFx.play(level, "block.amethyst_block.chime", to.position(), 1.5, 0.9);
        SkillFx.play(level, "block.beacon.power_select", to.position(), 1.8, 0.4);
        if (aimed != null) Notify.notify(aimed, "§a" + sp.getName().getString() + " §7님의 §a치유의 손길", 30);
        return true;
    }

    // ------------------------------------------------------------ 빛의 걸음

    /**
     * 빛의 걸음: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 steps 틱에 미끄러짐 (적은 지나침) → 지나간 길 width 칸 안의 아군 (나 포함) 에게 shieldFor 틱 보호막 (최대
     * 체력 × shield). 그림: 발자국마다 빛 십자 · 빛 줄기 · 1인칭 빠른 줄 · 아군마다 빛의 방패
     */
    static boolean step(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
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
        int steps = Math.max(1, (int) k.num("steps", 4));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        String life = RuneKit.lifeToken(sp);
        Kit.noFall(sp, steps + 20);
        double width = k.num("width", 2.5);
        Vec3 vel = V.scale(f, len / steps * 20);
        JobFx.aura(sp, j, 2.4, 0.4, 0.8);
        SkillFx.play(level, "block.amethyst_block.resonate", start, 1.6, 0.7);
        SkillFx.play(level, "entity.player.attack.sweep", start, 1.8, 0.4);
        Set<UUID> shielded = new HashSet<>();
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) return;
                JobFx.dash(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.35, JobFx.hide1p(sp).with("alpha", 0.8));
                JobFx.healCross(d, V.up(V.lerp(prev, to, 0.5), 0.15), 0.22, j, 0.9, Opt.of("alpha", 0.85, "fade", 0.5));
                JobFx.speed(sp, j, 0.1, 0.7, vel);
                // 지나간 자리 width 칸 안의 아군: 보호막 (한 번씩)
                for (ServerPlayer o : JobTraits4.allies(sp, V.lerp(prev, to, 0.5), width + V.len(V.sub(to, prev)) / 2)) {
                    if (!shielded.add(o.getUUID())) continue;
                    double max = RpgHealth.maxOf(o);
                    RpgHealth.addShield(o, max * k.num("shield", 0.1), (int) k.num("shieldFor", 100), max * k.num("shield", 0.1));
                    JobFx.aegis(d, V.up(o.position(), 1.1), 0.45, j, 0, 0.45, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
                    SkillFx.play(d, "item.armor.equip_gold", o.position(), 1.4, 0.7);
                }
                if (ss == marks.size() - 1) JobFx.eyeRing(sp, j, 2.4, 0.8, 0.35);
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 성가

    /**
     * 성가: gap 틱마다 (pulses 번) 둘레 radius 칸 — 아군 (나 포함) 최대 체력 heal 회복, 적은 둔화 (slow) · 신성 피해 (pulse — 마법). 사제를 따라감 (노래하며
     * 움직임). 그림: 둘레를 떠오르는 음표 · 발밑 빛의 고리 · 아군의 작은 십자 · 맞은 적의 빛
     */
    static boolean hymn(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int pulses = Math.max(1, (int) k.num("pulses", 8));
        int gap = Math.max(1, (int) k.num("gap", 10));
        double r = k.num("radius", 7);
        double heal = k.num("heal", 0.015);
        int slow = (int) k.num("slow", 1);
        String life = RuneKit.lifeToken(sp);
        // 화음 (하프 · 차임) — 펄스마다 한 음씩 올라감
        double[] pitch = {0.7, 0.84, 1.0, 0.94, 1.12, 1.26, 1.06, 1.5};
        JobFx.aura(sp, j, 2.6, (pulses * gap) / 20.0, 0.6);
        for (int i = 0; i < pulses; i++) {
            int ii = i;
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                JobFx.ring(level, V.up(c, 0.06), r, j, ii % 2 == 1, 0.5, Opt.of("alpha", 0.55, "vel", RuneKit.bodyVel(sp)));
                // 음표: 둘레 1.5 ~ r 칸에서 떠오름 (하나는 앞 — 1인칭에도)
                Vec3 f = Kit.facingOf(sp);
                for (int n = 0; n < 4; n++) {
                    Vec3 p;
                    if (n == 0) p = V.up(V.add(V.add(c, f, 2.2), V.rightOf(f), (Kit.rand() - 0.5) * 2.4), 1.3 + Kit.rand() * 0.6);
                    else {
                        double a = Kit.rand() * Math.PI * 2;
                        p = V.up(V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), 1.5 + Kit.rand() * (r - 1.5)), 0.6 + Kit.rand() * 1.4);
                    }
                    JobFx.note(level, p, (ii + n) % JobFx.NOTE, 0.24, j, 1.1, Opt.of("dir", V.unit(new Vec3((Kit.rand() - 0.5) * 0.4, 1, (Kit.rand() - 0.5) * 0.4)), "speed",
                            0.9, "spin", (Kit.rand() - 0.5) * 30, "fade", 0.4));
                }
                SkillFx.play(level, "block.note_block.harp", c, pitch[ii % pitch.length], 0.8);
                SkillFx.play(level, "block.note_block.chime", c, pitch[ii % pitch.length] * 1.5, 0.35);
                for (ServerPlayer o : JobTraits4.allies(sp, c, r)) {
                    if (JobTraits4.heal(o, RpgHealth.maxOf(o) * heal) > 0 && RuneKit.fxQuota("jobHymnHeal", 6)) {
                        JobFx.healCross(level, V.up(o.position(), 2.2), 0.18, j, 0.5, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", V.add(RuneKit.bodyVel(o),
                                new Vec3(0, 0.7, 0))));
                    }
                }
                int[] cnt = {0};
                for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                    RuneKit.effect(e, "slowness", gap + 12, slow);
                    JobKit.strike(sp, e, j, P, k.num("pulse", 0.7), true, false, life, 0, d -> {
                        if (cnt[0]++ < 4) JobFx.burst(e.level() instanceof ServerLevel lv ? lv : level, Kit.bodyPoint(e), 0.55 + Kit.bodyPad(e) * 0.3, j, 0.25,
                                Opt.empty());
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 성흔

    /**
     * 성흔: 겨눈 적 (range 칸 — 보스 포함) 에 dur 틱 성흔 — 그 적을 때린 아군 (나 포함) 은 준 피해 × rate 회복 (JobTraits4). 겨눈 적이 없으면 쓰지 않음.
     * 그림: 홀 끝에서 적까지 빛줄기 · 머리 위 빛나는 십자가 (16틱마다 다시) · 글자
     */
    static boolean stigma(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 16), 12);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        int dur = (int) k.num("dur", 200);
        JobTraits4.stigmaOn(sp, e, dur, k.num("rate", 0.04));
        ServerLevel level = sp.level();
        JobFx.beam(level, tip(sp), Kit.bodyPoint(e), j, 2.0, 0.3, Opt.of("alpha", 0.8));
        JobFx.stigma(level, RuneKit.overHead(e, 0.75), 0.62 + Kit.bodyPad(e) * 0.25, j, 0.8, Opt.of("vel", RuneKit.entVel(e), "grow", -0.3, "fade", 0.3));
        JobFx.burst(level, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.4, j, 0.35, Opt.empty());
        DamageNumbers.showAt(level, V.up(e.getEyePosition(), 1.25), "§e§l성흔");
        SkillFx.play(level, "block.respawn_anchor.charge", Kit.bodyPoint(e), 1.4, 0.7);
        SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.2, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 새벽의 종

    /**
     * 새벽의 종: 새벽빛 하늘 · 앞 3칸 위 4.6칸에 큰 종이 내려와 first 틱부터 gap 틱마다 rings 번 울림 — 종 아래 둘레 radius 칸 아군 (나 포함) 최대 체력 heal 회복 (첫
     * 울림에 나쁜 효과 제거 · undying 틱 쓰러지지 않음), 적에게 신성 피해 (ring — 마법, 마지막은 큰 숫자). 그림: 흔들리며 울리는 종 · 땅의 빛 고리 · 빛살 · 아군마다 큰
     * 회복 십자
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int rings = Math.max(1, (int) k.num("rings", 3));
        int first = (int) k.num("first", 16);
        int gap = Math.max(10, (int) k.num("gap", 16));
        double r = k.num("radius", 10);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 ground = PaladinSkills.groundAhead(sp, c0, f, 3.0);
        Vec3 bellAt = V.up(ground, 4.6);
        double bellSize = 1.5;
        // 0.30.0 사진첩: 머리 위 종은 시전자 1인칭 시야 밖 (위로 45°) → 시전자 1인칭에서는 눈앞 2.8칸 · 화면 위쪽 (시선에서 22° 위) 의 작은 종 (only1p — 6.5 ·
        // 3.5칸 앞에 두었더니 3.5칸 앞 과녁 머리 뒤에 가려졌음), 머리 위 종은 숨김
        Vec3 look = sp.getViewVector(1F);
        Vec3 bell1p = V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), 1.13);
        double size1p = 0.5;
        int endAt = first + gap * (rings - 1);
        Ult.sky(level0, c0, "rpg:ult_heaven", endAt + 30);
        JobFx.aura(sp, j, 3.0, 0.9, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        DamageNumbers.showFace(sp, "§e§l새벽의 종", 2.6, 0, 0.35);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.0, 0.8);
        // 종이 내려옴 (위에서 first − 4 틱 동안 — 흔들리기 전의 가만한 장면)
        int down = Math.max(4, first - 4);
        JobFx.bellStill(level0, V.up(bellAt, 4), bellSize, GOLD, DAWN, down / 20.0, JobFx.hide1p(sp).with("dir", V.DOWN, "speed", 4 / (down / 20.0), "fade", 0.02));
        JobFx.bellStill(level0, V.up(bell1p, 1.0), size1p, GOLD, DAWN, down / 20.0, JobFx.only1p(sp).with("dir", V.DOWN, "speed", 1.0 / (down / 20.0), "fade",
                0.02));
        RuneKit.godRays(sp, level0, ground, 2.5, 4, GOLD, DAWN, Opt.of("h", 10.0, "life", 1.0, "alpha", 0.6, "skipNear", true));
        for (int i = 0; i < rings; i++) {
            int ii = i;
            int at = first + gap * i;
            // 흔들림 (8장 — 4장째에 울림): 울리기 4틱 전부터
            RuneKit.later(at - 4, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double swing = ii == rings - 1 ? 0.9 : gap / 20.0 + 0.05;
                JobFx.bell(sp.level(), bellAt, bellSize, GOLD, DAWN, swing, JobFx.hide1p(sp).with("fade", 0.15));
                JobFx.bell(sp.level(), bell1p, size1p, GOLD, DAWN, swing, JobFx.only1p(sp).with("fade", 0.15));
            });
            RuneKit.later(at, () -> {
                if (RuneKit.sameLife(sp, life)) toll(sp, j, k, P, ground, bellAt, bell1p, r, ii, ii == rings - 1, life);
            });
        }
        return true;
    }

    /** 종이 울림: 땅의 빛 고리 둘 · 퍼지는 충격 고리 · 빛살 — 아군 회복 (첫 울림에 정화 · 쓰러지지 않음), 적에게 ring (마법) */
    private static void toll(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 ground, Vec3 bellAt, Vec3 bell1p, double r, int i, boolean fin,
            String life) {
        ServerLevel level = sp.level();
        JobFx.ring(level, V.up(ground, 0.06), r, j, i % 2 == 1, 0.6, Opt.empty());
        RuneKit.zonePulse(level, V.up(ground, 0.1), GOLD, r, 0.55);
        JobFx.burst(level, bellAt, 1.6, j, 0.35, JobFx.hide1p(sp).with("alpha", 0.8));
        JobFx.burst(level, bell1p, 0.5, j, 0.35, JobFx.only1p(sp).with("alpha", 0.8));
        RuneKit.godRays(sp, level, ground, r * 0.5, 5, GOLD, DAWN, Opt.of("h", 10.0, "life", 0.8, "alpha", 0.7, "skipNear", true));
        if (fin) Ult.flash(level, ground, GOLD, Opt.empty(), sp);
        RuneKit.shakeAround(level, ground, 16, fin ? 0.3 : 0.18, 0.3);
        SkillFx.play(level, "block.bell.use", bellAt, 0.7 - i * 0.08, 1.0);
        SkillFx.play(level, "block.bell.resonate", bellAt, 1.0, i == 0 ? 0.8 : 0.4);
        double heal = k.num("heal", 0.15);
        int undying = (int) k.num("undying", 120);
        for (ServerPlayer o : JobTraits4.allies(sp, ground, r)) {
            JobTraits4.heal(o, RpgHealth.maxOf(o) * heal);
            if (i == 0) {
                RuneKit.cleanse(o);
                JobTraits4.dawnFor(o, undying);
            }
            JobFx.healCross(level, V.up(o.position(), 2.3), 0.5, j, 0.7, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", V.add(RuneKit.bodyVel(o), new Vec3(0, 0.5,
                    0))));
            if (o == sp) {
                Vec3 look = sp.getViewVector(1F);
                JobFx.healCross(level, V.add(V.add(sp.getEyePosition(), look, 2.4), JobFx.screenUp(look), -0.2), 0.42, j, 0.6, JobFx.only1p(sp).with("vel",
                        RuneKit.bodyVel(sp)));
            }
        }
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, ground, r, 4)) {
            JobKit.strike(sp, e, j, P, k.num("ring", 8), true, fin, life, 0, d -> {
                if (cnt[0]++ < 6) JobFx.burst(e.level() instanceof ServerLevel lv ? lv : level, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.4, j, 0.3, Opt.empty());
            });
        }
    }
}
