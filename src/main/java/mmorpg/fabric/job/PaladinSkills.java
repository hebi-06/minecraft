package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
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
 * 0.30.0 성기사 직업 스킬 (jobs.json paladin — 금빛 · 하늘빛 벼락, 특성 「심판」 · 수호 오라): 뇌격 강타 · 신념의 돌진 · 뇌진 · 수호의 맹세 · 천뢰의 대망치.
 * 피해 = 위력 P × 배율 (JobKit.strike — 같은 적을 다시 칠 때는 몹 무적 시간이 끝난 뒤로, 같은 적을 여러 번 치는 것은 10틱 넘게 띄움).
 * 연출 = 메이플식 2D 그림 (JobFx — 내리치는 벼락 · 번개 고리 · 번개 대망치 · 빛의 날개 · 기절 별 · 빛의 방패, 룬 문양 · 마법진은 쓰지 않음).
 */
final class PaladinSkills {
    private PaladinSkills() {}

    /** 흙먼지 (누런 회색) */
    private static final double[] DUST = {0.62, 0.52, 0.4};
    /** 먹구름 (짙은 보라 회색 · 그보다 옅은 쪽) */
    private static final double[] CLOUD = {0.16, 0.14, 0.24};
    private static final double[] CLOUD_CORE = {0.34, 0.31, 0.46};

    static void register() {
        JobSystem.register("paladin_smite", PaladinSkills::smite);
        JobSystem.register("paladin_charge", PaladinSkills::charge);
        JobSystem.register("paladin_quake", PaladinSkills::quake);
        JobSystem.register("paladin_oath", PaladinSkills::oath);
        JobSystem.register("paladin_ult", PaladinSkills::ult);
    }

    /** 발 앞 ahead 칸의 땅 */
    static Vec3 groundAhead(ServerPlayer sp, Vec3 from, Vec3 f, double ahead) {
        Vec3 a = V.add(from, f, ahead);
        return new Vec3(a.x, RuneKit.groundAt(sp.level(), a, from.y), a.z);
    }

    /** 벼락 한 줄기: 내리치는 벼락 · 발밑 번개 고리 · 불티 · 소리 */
    static void boltFx(ServerLevel level, Vec3 ground, Jobs.Job j, double height, double scale) {
        JobFx.thunder(level, ground, height, j, 0.42, Opt.empty());
        JobFx.volt(level, V.up(ground, 0.08), 1.1 * scale, j, 0.4, Opt.empty());
        SkillFx.sparks(level, V.up(ground, 0.3), JobKit.glow(j), (int) Math.round(6 * scale), 4);
        SkillFx.play(level, "entity.lightning_bolt.impact", ground, 1.3, 0.55);
    }

    // ------------------------------------------------------------ 뇌격 강타

    /**
     * 뇌격 강타: 망치로 앞 땅을 내려쳐 앞 reach 칸 · arc° 부채꼴 충격파 (wave — 맞힌 적마다 심판 + judge) → delay 틱 뒤 맞은 적마다 벼락 (bolt — 마법). 그림: 눈앞 위 →
     * 아래 큰 베기 · 땅의 금 · 부채꼴로 뻗는 금빛 줄기 다섯 · 벼락 (적이 없으면 부채꼴 가운데에 하나 — 피해 없음)
     */
    static boolean smite(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        String life = RuneKit.lifeToken(sp);
        // 0.50.0 동작: 제자리에서 뛰어올라 (hop 칸) 내려앉으며 망치로 내려침 (hopT 틱 — 충격파 · 벼락은 그 뒤)
        int hopT = (int) k.num("hopT", 6);
        JobMove.run(sp, JobMove.hop(0, hopT, from -> from, k.num("hop", 0.9)));
        RuneKit.later(hopT, () -> {
            if (RuneKit.sameLife(sp, life)) smiteLand(sp, j, k, P, life);
        });
        return true;
    }

    /** 벼락 강타의 내려침 (착지 틱): 앞 부채꼴 충격파 → delay 틱 뒤 맞은 자리에 벼락 */
    private static void smiteLand(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double reach = k.num("reach", 4);
        double arc = k.num("arc", 120);
        int delay = (int) k.num("delay", 10);
        int judge = (int) k.num("judge", 2);
        JobFx.slash(sp, j, 2.3, 0.1, 2.0, 95.0, 0.22);
        Vec3 g = groundAhead(sp, c, f, 1.6);
        // 0.47.0 입체: 번개 대망치가 위에서 앞 땅으로 내려꽂힘 (머리가 아래 · 0.1초) → 잠깐 박혀 있다 흐려짐
        double ms = 0.85;
        JobFx3d.spawn(level, "maul", V.up(g, 3.5), j, Opt.of("to", V.up(g, 0.3 * ms), "ctrl", V.up(g, 1.8), "travel", 0.1, "ease", "in", "dir", V.DOWN, "face",
                V.scale(f, -1), "scale", ms, "life", 0.5, "fadeOut", 0.3, "glowAlpha", 0.55, "shine", 0.4));
        JobFx.smash(level, V.up(g, 0.05), 1.8, j, 0.5, Opt.empty());
        for (int i = 0; i < 5; i++) {
            Vec3 d = V.turnY(f, (i / 4.0 - 0.5) * arc * 0.8);
            JobFx.dash(level, V.up(V.add(c, d, 0.9), 0.3), V.up(V.add(c, d, reach + 0.3), 0.3), j, 0.3, Opt.of("alpha", 0.9));
        }
        SkillFx.sparks(level, V.up(g, 0.3), JobKit.glow(j), 10, 5);
        SkillFx.sparks(level, V.up(g, 0.2), DUST, 6, 3);
        JobKit.shake(sp, 8, 0.2, 0.2);
        SkillFx.play(level, "mace.heavy_smash_ground", g, 1.0, 0.9);
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.7, 0.8);
        List<LivingEntity> hits = RuneKit.coneTargets(sp, reach, arc, 10);
        int[] cnt = {0};
        for (LivingEntity e : hits) {
            JobKit.strike(sp, e, j, P, k.num("wave", 3), false, false, life, 0, d -> {
                JobTraits4.judge(sp, e, j, judge);
                if (cnt[0]++ < 6) JobFx.hit(e, j, 1.1);
            });
        }
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            SkillFx.play(lv, "item.trident.thunder", c, 1.5, 0.35);
            if (hits.isEmpty()) {
                boltFx(lv, groundAhead(sp, c, f, reach * 0.7), j, 9, 1.0);
                return;
            }
            int n = 0;
            for (LivingEntity e : hits) {
                if (e.isRemoved() || !e.isAlive()) continue;
                if (n++ < 6) boltFx(lv, e.position(), j, 9 + e.getBbHeight(), 1.0 + Kit.bodyPad(e) * 0.5);
                JobKit.strike(sp, e, j, P, k.num("bolt", 2.5), true, false, life, 0, null);
            }
        });
    }

    // ------------------------------------------------------------ 신념의 돌진

    /**
     * 신념의 돌진: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 steps 틱에 내달림 (그동안 밀려나지 않음) — 길 앞에 처음 닿은 적들 앞에서 멈춰 부딪힘 (ram · 보스 제외
     * stun 틱 기절 · 조금 밀림) → 멈춘 자리 둘레 allyRadius 칸 아군 (나 포함) 에게 shieldFor 틱 보호막 (최대 체력 × shield). 묶였거나 앞이 막혀 한 칸도 못 가면
     * 쓰지 않음
     */
    static boolean charge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
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
        RpgCombat.setSteadfast(sp, steps + 10);
        Kit.noFall(sp, steps + 30);
        // 출발: 몸의 금빛 · 앞의 빛의 방패 · 소리
        JobFx.aegis(level, V.up(V.add(start, f, 1.0), 1.1), 0.5, j, 0, 0.3, JobFx.hide1p(sp).with("vel", V.scale(f, len / Math.max(1, steps) * 20)));
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.35);
        SkillFx.play(level, "item.trident.riptide_1", start, 1.0, 0.8);
        SkillFx.play(level, "block.beacon.power_select", start, 1.4, 0.5);
        boolean[] done = {false};
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (done[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                List<LivingEntity> hit = Kit.around(sp, V.add(to, f, 0.6), 1.5, 2.0);
                if (!hit.isEmpty()) {
                    done[0] = true;
                    double sPrev = V.dot(V.sub(prev, start), f);
                    double near = 1e9;
                    for (LivingEntity e : hit) near = Math.min(near, V.dot(V.sub(e.position(), start), f) - 1.5 - Kit.bodyPad(e));
                    double want = Math.max(sPrev, Math.min(len, near));
                    Kit.Spot best = null;
                    for (Kit.Spot x : spots) if (x.s() <= want + 0.05 && (best == null || x.s() > best.s())) best = x;
                    Vec3 stop = prev;
                    if (best != null && best.s() > sPrev + 0.05 && Kit.moveTo(sp, best.p())) stop = best.p();
                    JobFx.dash(d, V.up(prev, 0.9), V.up(stop, 0.9), j, 0.3, JobFx.hide1p(sp));
                    ram(sp, j, k, P, hit, f, life);
                    chargeEnd(sp, j, k);
                    return;
                }
                if (!Kit.moveTo(sp, to)) {
                    done[0] = true;
                    chargeEnd(sp, j, k);
                    return;
                }
                JobFx.dash(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.3, JobFx.hide1p(sp));
                SkillFx.sparks(d, V.up(to, 0.15), DUST, 3, 2);
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.8);
                if (ss == marks.size() - 1) {
                    done[0] = true;
                    chargeEnd(sp, j, k);
                }
            });
        }
        return true;
    }

    /** 부딪힘: 닿은 적마다 ram · 보스 제외 stun 틱 기절 · 조금 밀림 · 맞은 자리 번개 고리 */
    private static void ram(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, List<LivingEntity> hit, Vec3 f, String life) {
        ServerLevel d = sp.level();
        int stun = (int) k.num("stun", 10);
        int[] cnt = {0};
        for (LivingEntity e : hit) {
            JobKit.strike(sp, e, j, P, k.num("ram", 3.3), false, false, life, 0, x -> {
                JobTraits4.stun(e, j, stun);
                if (JobKit.pushable(e)) RuneKit.knock(e, f, 0.3, 0.15);
                if (cnt[0]++ >= 6) return;
                JobFx.hit(e, j, 1.2);
                JobFx.ringFacing(e.level() instanceof ServerLevel lv ? lv : d, Kit.bodyPoint(e), 0.8 + Kit.bodyPad(e) * 0.4, j, V.scale(f, -1), 0.3, Opt.empty());
            });
        }
        JobKit.shake(sp, 6, 0.3, 0.2);
        SkillFx.sparks(d, V.up(V.add(sp.position(), f, 0.9), 1.1), JobKit.glow(j), 8, 4);
        SkillFx.play(d, "entity.goat.ram_impact", sp.position(), 0.9, 1.0);
        SkillFx.play(d, "item.shield.block", sp.position(), 0.8, 0.8);
    }

    /** 멈춤: 둘레 allyRadius 칸 아군 (나 포함) 보호막 — 아군마다 빛의 방패가 피어남 · 발밑 금빛 고리 */
    private static void chargeEnd(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double frac = JobUpgrades.num(sp, k, "shield", 0.08); // 0.31.0 강화
        int ticks = (int) k.num("shieldFor", 60);
        for (ServerPlayer o : JobTraits4.allies(sp, c, k.num("allyRadius", 5))) {
            double max = RpgHealth.maxOf(o);
            RpgHealth.addShield(o, max * frac, ticks, max * frac);
            JobFx.aegis(level, V.up(o.position(), 1.1), 0.45, j, 0, 0.4, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
        }
        JobFx.ring(level, V.up(c, 0.06), k.num("allyRadius", 5), j, false, 0.45, Opt.of("alpha", 0.75));
        SkillFx.play(level, "block.beacon.power_select", c, 1.7, 0.5);
    }

    // ------------------------------------------------------------ 뇌진

    /**
     * 뇌진: 망치로 gap 틱마다 3번 내려쳐 둘레 radii 칸 (3 → 5 → 7) 의 적에게 번개 고리 (ring — 맞을 때마다 심판 + judge · 밀리는 적은 띄움 lift). 그림: 눈앞
     * 큰 베기 (번갈아) · 발밑 땅의 금 · 번개 고리 · 고리 위의 작은 벼락들
     */
    static boolean quake(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<?> radii = k.def().list("radii");
        int gap = Math.max(1, (int) k.num("gap", 10));
        int judge = (int) k.num("judge", 1);
        double lift = k.num("lift", 0.35);
        String life = RuneKit.lifeToken(sp);
        // 0.50.0 동작: 내려칠 때마다 작게 뛰어 (hop 칸) 다음 내려침에 착지
        JobMove.Seg[] hops = new JobMove.Seg[Math.max(0, radii.size() - 1)];
        for (int i = 0; i < hops.length; i++) hops[i] = JobMove.hop(i * gap, (i + 1) * gap, from -> from, k.num("hop", 0.8));
        JobMove.run(sp, hops);
        for (int i = 0; i < radii.size(); i++) {
            int ii = i;
            double r = ((Number) radii.get(i)).doubleValue();
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                JobFx.slash(sp, j, 2.2, 0.15, 1.8, ii % 2 == 0 ? 95.0 : 265.0, 0.2);
                JobFx.smash(level, V.up(c, 0.05), Math.min(r, 3.2), j, 0.45, Opt.empty());
                JobFx.volt(level, V.up(c, 0.1), r, j, 0.5, Opt.empty());
                for (int b = 0; b < 3 + ii; b++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), r * (0.82 + Kit.rand() * 0.18));
                    p = new Vec3(p.x, RuneKit.groundAt(level, p, c.y), p.z);
                    JobFx.thunder(level, p, 5.5 + Kit.rand() * 2, j, 0.35, Opt.of("alpha", 0.85));
                }
                SkillFx.sparks(level, V.up(c, 0.3), JobKit.glow(j), 8 + ii * 3, 5);
                JobKit.shake(sp, 10, 0.18 + ii * 0.06, 0.22);
                SkillFx.play(level, "mace.heavy_smash_ground", c, 0.9 + ii * 0.08, 0.9);
                SkillFx.play(level, "item.trident.thunder", c, 1.25 + ii * 0.1, 0.35);
                int[] cnt = {0};
                for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                    JobKit.strike(sp, e, j, P, k.num("ring", 3.3), false, false, life, 0, d -> {
                        JobTraits4.judge(sp, e, j, judge);
                        if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), c), 0.12, lift);
                        if (cnt[0]++ < 6) JobFx.hit(e, j, 1.0);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 수호의 맹세

    /**
     * 수호의 맹세: dur 틱 태세 — 수호 오라 −5% → − aura, 둘레 radius 칸 아군 (나 포함) 1초마다 최대 체력 regen 회복 (JobTraits4), 밀려나지 않음. 그림: 등 뒤로
     * 펼쳐지는 빛의 날개 · 몸의 금빛 · 발밑 오라 범위 고리 · 아군마다 빛의 방패 · 1인칭 눈앞 고리 · 글자
     */
    static boolean oath(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 200); // 0.31.0 강화: 지속
        JobTraits.startStance(sp, k, dur);
        RpgCombat.setSteadfast(sp, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.wing(level, JobTraits4.wingAt(sp), Kit.facingOf(sp), 1.5, j, 0.7, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "grow", 0.3, "alpha", 0.95, "solo",
                3.0));
        JobFx.aura(sp, j, 3.0, 1.0, 1.0);
        JobFx.ring(level, V.up(c, 0.06), k.num("radius", 6), j, false, 0.6, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        RuneKit.selfCue(sp, JobKit.glow(j), JobKit.core(j), false);
        for (ServerPlayer o : JobTraits4.allies(sp, c, k.num("radius", 6))) {
            if (o == sp) continue;
            JobFx.aegis(level, V.up(o.position(), 1.1), 0.42, j, 0, 0.45, Opt.of("vel", RuneKit.bodyVel(o)));
        }
        Ult.flash(level, c, JobKit.glow(j), Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        DamageNumbers.showFace(sp, "§6§l" + k.name(), 2.4, 0, 0.2);
        SkillFx.play(level, "block.beacon.activate", c, 1.2, 0.8);
        SkillFx.play(level, "block.bell.use", c, 0.9, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 천뢰의 대망치

    /**
     * 천뢰의 대망치: 먹구름이 모이고 (보라 하늘 · 구름 속 번개) 앞 3칸 땅 위로 번개로 된 거대한 망치가 내려와 나와 함께 내리침 — first 틱부터 gap 틱마다 3번 둘레
     * radius 칸 (hits — 마법, 마지막은 큰 숫자 · 보스 제외 stun 틱 기절) → sanctuary 틱 빛의 성역 (둘레 radius 칸 아군 1초마다 최대 체력 heal 회복)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<?> hitList = k.def().list("hits");
        int first = (int) k.num("first", 20);
        int gap = Math.max(10, (int) k.num("gap", 14));
        double radius = k.num("radius", 8);
        int sanct = (int) k.num("sanctuary", 80);
        int last = hitList.size() - 1;
        int endAt = first + gap * last;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        // 0.50.0 동작 (사용자 "역동적으로"): 토르의 힘을 받아 높이 뛰어올라 (lift 칸) 첫 내려침에 착지 → 다시 뛰어 두 번 더 (내려치는 자리는 앞 3칸 그대로)
        JobMove.run(sp, JobMove.hop(0, first, from -> from, k.num("lift", 3.0)), JobMove.hop(first, first + gap, from -> from, k.num("hop", 1.6)),
                JobMove.hop(first + gap, first + 2 * gap, from -> from, k.num("hop", 1.6)));
        Vec3 f = Kit.facingOf(sp);
        Vec3 center = groundAhead(sp, c0, f, 3.0);
        Ult.sky(level0, c0, "rpg:ult_storm", endAt + sanct + 20);
        RpgCombat.setSteadfast(sp, endAt + 10);
        JobFx.aura(sp, j, 3.0, 0.9, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        DamageNumbers.showFace(sp, "§6§l" + k.name(), 2.6, 0, 0.35);
        SkillFx.play(level0, "entity.lightning_bolt.thunder", center, 0.6, 0.6);
        SkillFx.play(level0, "block.beacon.activate", c0, 0.8, 0.8);
        // 먹구름: 가운데 위 10 ~ 12칸에 짙은 구름이 모이고 그 속에서 번개가 번쩍임
        for (int t = 0; t < endAt; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double spread = 7 - Math.min(1, tt / (double) first) * 3.5;
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.up(V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), spread * Math.sqrt(Kit.rand())), 10.5 + Kit.rand() * 1.5);
                    JobFx.smoke(lv, p, 2.4 + Kit.rand(), CLOUD, CLOUD_CORE, 1.0, Opt.of("alpha", 0.85, "fade", 0.5));
                }
                if (tt % 8 == 4) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.up(V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), Kit.rand() * spread), 7.0);
                    JobFx.thunder(lv, p, 4.5, j, 0.2, Opt.of("alpha", 0.8));
                }
                if (tt == 8) SkillFx.play(lv, "entity.lightning_bolt.thunder", center, 0.75, 0.5);
            });
        }
        for (int i = 0; i <= last; i++) {
            int ii = i;
            double mult = ((Number) hitList.get(i)).doubleValue();
            int at = first + gap * i;
            // 망치가 떨어짐 (6틱 — 머리가 땅에 닿을 때 내리침)
            RuneKit.later(at - 6, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double size = ii == last ? 2.2 : 1.9;
                double half = size * 2.67 / 2;
                JobFx.maul(sp.level(), V.up(center, half + 10), size, j, 0.3, Opt.of("dir", V.DOWN, "speed", 10 / 0.3, "alpha", 0.95, "fade", 0.05, "lean", maulLean(
                        sp)));
                SkillFx.play(sp.level(), "item.trident.thunder", center, 0.9 + ii * 0.1, 0.5);
            });
            RuneKit.later(at, () -> {
                if (RuneKit.sameLife(sp, life)) slam(sp, j, k, P, center, radius, mult, ii, ii == last, life);
            });
            JobKit.motionLater(sp, "paladin_ult:smash", at - 3, life); // 0.47.0 대망치와 함께 내리치는 동작 (3틱 뒤 닿음)
        }
        RuneKit.later(endAt + 4, () -> {
            if (RuneKit.sameLife(sp, life)) sanctuary(sp, j, k, center, radius, sanct, life);
        });
        return true;
    }

    /**
     * 0.47.0 천뢰의 대망치의 기울기 (망치 머리 쪽 — 자루가 오른쪽 위로 22° 비스듬히 솟음): 사진첩에서 앞 3칸에 곧게 선 자루가 1인칭 화면 가운데를 세로로 갈랐음
     */
    private static Vec3 maulLean(ServerPlayer sp) {
        double a = Math.toRadians(22);
        return V.add(V.scale(V.DOWN, Math.cos(a)), V.rightOf(Kit.facingOf(sp)), -Math.sin(a));
    }

    /** 대망치가 내리침: 망치 · 큰 벼락 · 둘레 벼락 · 번개 고리 · 땅의 금 · 1인칭 큰 베기 — 둘레 radius 칸 (mult — 마법) */
    private static void slam(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 center, double radius, double mult, int i, boolean fin, String life) {
        ServerLevel level = sp.level();
        double size = fin ? 2.2 : 1.9;
        JobFx.maul(level, V.up(center, size * 2.67 / 2), size, j, 0.3, Opt.of("alpha", 0.9, "fade", 0.6, "grow", 0.1, "lean", maulLean(sp)));
        JobFx.thunder(level, center, fin ? 16 : 13, j, 0.5, Opt.empty());
        Vec3 me = sp.position();
        for (int b = 0; b < (fin ? 6 : 4); b++) {
            // 둘레 벼락은 시전자에게서 3.5칸 넘게 (0.30.0 사진첩: 카메라 바로 옆에 떨어진 벼락이 화면을 덮으며 그림 칸이 계단처럼 보였음)
            Vec3 p = center;
            for (int tries = 0; tries < 8; tries++) {
                double a = Kit.rand() * Math.PI * 2;
                p = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * (0.35 + Kit.rand() * 0.55));
                if (Math.hypot(p.x - me.x, p.z - me.z) > 3.5) break;
            }
            if (Math.hypot(p.x - me.x, p.z - me.z) <= 3.5) continue;
            p = new Vec3(p.x, RuneKit.groundAt(level, p, center.y), p.z);
            Vec3 pp = p;
            RuneKit.later(b % 3, () -> JobFx.thunder(level, pp, 9 + Kit.rand() * 3, j, 0.4, Opt.of("alpha", 0.9)));
        }
        JobFx.volt(level, V.up(center, 0.1), radius, j, 0.55, Opt.empty());
        JobFx.smash(level, V.up(center, 0.05), radius * 0.6, j, 0.6, Opt.empty());
        JobFx.burst(level, V.up(center, 0.8), fin ? 2.4 : 1.8, j, 0.4, Opt.empty());
        JobFx.slash(sp, j, 2.3, 0.1, 2.0, 95.0, 0.22);
        RuneKit.rockBurst(level, V.up(center, 0.3), fin ? 12 : 8, 5.0, 0.2);
        SkillFx.sparks(level, V.up(center, 0.5), JobKit.glow(j), fin ? 20 : 12, 7);
        RuneKit.shakeAround(level, center, 18, fin ? 0.5 : 0.35, 0.35);
        if (fin) {
            Ult.flash(level, center, JobKit.glow(j), Opt.empty(), sp);
            RuneKit.godRays(sp, level, center, radius * 0.6, 6, JobKit.glow(j), JobKit.core(j), Opt.of("h", 12.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
        }
        SkillFx.play(level, "entity.lightning_bolt.thunder", center, 0.9 + i * 0.1, 0.9);
        SkillFx.play(level, "entity.lightning_bolt.impact", center, 0.8, 1.0);
        SkillFx.play(level, "mace.heavy_smash_ground", center, 0.6, 1.0);
        int stun = (int) k.num("stun", 30);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, center, radius, 3.5)) {
            JobKit.strike(sp, e, j, P, mult, true, fin, life, 0, d -> {
                if (fin) JobTraits4.stun(e, j, stun);
                if (fin && JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), center), 0.4, 0.45);
                if (cnt[0]++ < 6) JobFx.hit(e, j, fin ? 1.5 : 1.2);
            });
        }
    }

    /** 빛의 성역: ticks 동안 둘레 radius 칸 — 발밑 금빛 고리 · 솟는 빛 알갱이, 1초마다 아군 (나 포함) 최대 체력 heal 회복 (금빛 십자 · 숫자) */
    private static void sanctuary(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, Vec3 center, double radius, int ticks, String life) {
        double heal = JobUpgrades.num(sp, k, "heal", 0.02); // 0.31.0 강화
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        SkillFx.play(sp.level(), "block.beacon.power_select", center, 1.2, 0.8);
        for (int t = 0; t < ticks; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                if (tt % 20 == 0) {
                    JobFx.ring(level, V.up(center, 0.06), radius, j, tt % 40 == 0, 0.8, Opt.of("alpha", 0.6));
                    for (ServerPlayer o : JobTraits4.allies(sp, center, radius)) {
                        if (JobTraits4.heal(o, RpgHealth.maxOf(o) * heal) <= 0) continue;
                        JobFx.healCross(level, V.up(o.position(), 2.25), 0.24, glow, core, 0.6, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel",
                                V.add(RuneKit.bodyVel(o), new Vec3(0, 0.6, 0))));
                    }
                }
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.up(V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * Math.sqrt(Kit.rand())), 0.2);
                    SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 1.6, "size", 0.14, "grow", 0.5, "life", 0.8, "color", i % 2 == 0 ? glow : core,
                            "alpha", 0.9));
                }
            });
        }
    }
}
