package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Aegis;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.30.0 수호기사 직업 스킬 (jobs.json guardian — 강철 파랑 · 흰빛, 특성 「반격」): 방패 치기 · 수호 돌입 · 방패 투척 · 철옹성 · 성채.
 * 피해 = 위력 P × 배율 (JobKit.strike — 첫 타에 반격 게이지를 더함, JobTraits4). 도발 = Aegis.tauntFor (그동안 그 적은 나를 노림 · 머리 위 "도발").
 * 연출 = 메이플식 2D 그림 (JobFx — 빛의 방패 · 둥근 방패 · 빛의 사슬 · 기절 별) + 성채의 돌 성벽 (블록 표시 엔티티 — 솟았다 무너짐), 룬 문양 · 마법진은 쓰지 않음.
 */
final class GuardianSkills {
    private GuardianSkills() {}

    /** 돌먼지 (회색) */
    private static final double[] DUST = {0.6, 0.6, 0.62};

    static void register() {
        JobSystem.register("guardian_bash", GuardianSkills::bash);
        JobSystem.register("guardian_rush", GuardianSkills::rush);
        JobSystem.register("guardian_throw", GuardianSkills::shieldThrow);
        JobSystem.register("guardian_bulwark", GuardianSkills::bulwark);
        JobSystem.register("guardian_ult", GuardianSkills::ult);
    }

    /** 방패 앞 (가슴 높이 · 앞 ahead 칸) */
    private static Vec3 shieldFront(ServerPlayer sp, double ahead) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), ahead), 1.15);
    }

    // ------------------------------------------------------------ 방패 치기

    /**
     * 방패 치기: 앞 reach 칸 (100°) 의 적 (가까운 셋) 을 방패로 침 (mult + 반격 게이지 — 첫 타) · 보스 제외 stun 틱 기절 · taunt 틱 도발. 그림: 앞의 빛의 방패가
     * 번쩍 · 충돌 고리 · 맞은 적의 별 (1인칭 눈앞 방패 번쩍)
     */
    static boolean bash(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        String life = RuneKit.lifeToken(sp);
        JobFx.aegis(level, shieldFront(sp, 1.2), 0.5, j, 0, 0.22, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        Vec3 look = sp.getViewVector(1F);
        JobFx.aegis(level, V.add(V.add(sp.getEyePosition(), look, 2.1), JobFx.screenUp(look), -0.45), 0.42, j, 0, 0.22, JobFx.only1p(sp).with("vel",
                RuneKit.bodyVel(sp), "alpha", 0.85));
        JobFx.ringFacing(level, shieldFront(sp, 1.4), 0.9, j, V.scale(f, -1), 0.25, Opt.empty());
        SkillFx.play(level, "item.shield.block", sp.position(), 0.8, 1.0);
        SkillFx.play(level, "entity.player.attack.knockback", sp.position(), 0.9, 0.9);
        int stun = (int) k.num("stun", 14);
        int taunt = (int) k.num("taunt", 80);
        int[] cnt = {0};
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("reach", 3.5), 100, 3)) {
            Aegis.tauntFor(sp, e, taunt, true);
            JobKit.strike(sp, e, j, P, k.num("mult", 4), false, false, life, 0, d -> {
                JobTraits4.stun(e, j, stun);
                if (JobKit.pushable(e)) RuneKit.knock(e, f, 0.35, 0.12);
                if (cnt[0]++ >= 3) return;
                JobFx.hit(e, j, 1.2);
                SkillFx.play(e.level() instanceof ServerLevel lv ? lv : level, "entity.zombie.attack_iron_door", Kit.bodyPoint(e), 1.3, 0.5);
            });
        }
        JobKit.shake(sp, 6, 0.2, 0.15);
        return true;
    }

    // ------------------------------------------------------------ 수호 돌입

    /**
     * 수호 돌입: 겨눈 아군 (range 칸 — 그 앞 1.2칸까지) 에게, 없으면 앞 dist 칸으로 steps 틱에 돌진 (벽 앞 · 아군이 없으면 길 앞에 처음 닿은 적 앞에서 멈춤)
     * → 닿은 자리 둘레 radius 칸 적에게 hit (반격
     * 게이지 — 첫 타) · 밀침, 아군이면 shareFor 틱 동안 그 아군이 받을 피해의 share 를 내가 × (1 − cut) 로 (JobTraits4 — 둘 사이 빛의 사슬). 묶였거나 아군도 없이
     * 앞이 막혔으면 쓰지 않음
     */
    static boolean rush(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerPlayer ally = JobTraits4.aimAlly(sp, k.num("range", 12));
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double want = k.num("dist", 8);
        if (ally != null) {
            Vec3 d = V.flat(V.sub(ally.position(), start));
            double dist = Math.hypot(ally.getX() - start.x, ally.getZ() - start.z);
            if (dist > 0.1) f = d;
            want = Math.max(0, dist - 1.2);
        }
        double free = want > 0.4 ? Kit.reachFlat(level, start, f, want) : 0;
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty() && ally == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        int steps = Math.max(1, (int) k.num("steps", 4));
        List<Vec3> marks = new ArrayList<>();
        double len = spots.isEmpty() ? 0 : spots.get(spots.size() - 1).s();
        if (!spots.isEmpty()) {
            for (int s = 1; s <= steps; s++) {
                double w = len * s / steps;
                Kit.Spot best = spots.get(0);
                for (Kit.Spot x : spots) if (Math.abs(x.s() - w) < Math.abs(best.s() - w)) best = x;
                marks.add(best.p());
            }
        }
        RpgCombat.setSteadfast(sp, marks.size() + 8);
        Kit.noFall(sp, marks.size() + 30);
        Vec3 dir = f;
        SkillFx.play(level, "item.trident.riptide_1", start, 1.2, 0.7);
        SkillFx.play(level, "item.armor.equip_iron", start, 0.9, 0.8);
        JobFx.eyeRing(sp, j, 2.4, 0.8, 0.3);
        boolean[] done = {false};
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (done[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                if (ally == null) {
                    // 0.30.0 아군 없이 앞으로: 길 앞에 처음 닿은 적들 앞에서 멈춰 닿음 (성기사 신념의 돌진과 같음 — 사진첩: 과녁 무리를 지나쳐 빈 곳에 섰음)
                    List<LivingEntity> hit = Kit.around(sp, V.add(to, dir, 0.6), 1.5, 2.0);
                    if (!hit.isEmpty()) {
                        done[0] = true;
                        double sPrev = V.dot(V.sub(prev, start), dir);
                        double near = 1e9;
                        for (LivingEntity e : hit) near = Math.min(near, V.dot(V.sub(e.position(), start), dir) - 1.5 - Kit.bodyPad(e));
                        double stopAt = Math.max(sPrev, Math.min(len, near));
                        Kit.Spot best = null;
                        for (Kit.Spot x : spots) if (x.s() <= stopAt + 0.05 && (best == null || x.s() > best.s())) best = x;
                        Vec3 stop = prev;
                        if (best != null && best.s() > sPrev + 0.05 && Kit.moveTo(sp, best.p())) stop = best.p();
                        JobFx.dash(d, V.up(prev, 0.9), V.up(stop, 0.9), j, 0.3, JobFx.hide1p(sp));
                        JobFx.speed(sp, j, 0.25, 0.8);
                        arrive(sp, j, k, P, null, dir, life);
                        return;
                    }
                }
                if (!Kit.moveTo(sp, to)) return;
                JobFx.dash(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.3, JobFx.hide1p(sp));
                SkillFx.sparks(d, V.up(to, 0.15), DUST, 3, 2);
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.8);
            });
        }
        RuneKit.later(marks.size(), () -> {
            if (!done[0] && RuneKit.sameLife(sp, life)) arrive(sp, j, k, P, ally, dir, life);
        });
        return true;
    }

    /** 닿음: 둘레 radius 칸 적 hit · 밀침, 아군과 사슬로 이어짐 */
    private static void arrive(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, ServerPlayer ally, Vec3 f, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 2.5);
        JobFx.ring(level, V.up(c, 0.08), r, j, false, 0.35, Opt.empty());
        JobFx.aegis(level, shieldFront(sp, 1.0), 0.5, j, 0, 0.3, JobFx.hide1p(sp)); // 방패를 앞세워 부딪힘 (피어나는 장 — 금 간 장은 성채가 밀칠 때)
        JobKit.shake(sp, 6, 0.2, 0.15);
        SkillFx.play(level, "item.shield.block", c, 0.9, 1.0);
        SkillFx.play(level, "entity.iron_golem.attack", c, 1.1, 0.7);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("hit", 2.4), false, false, life, 0, d -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), c), 0.45, 0.2);
                if (cnt[0]++ < 6) JobFx.hit(e, j, 1.1);
            });
        }
        if (ally == null || ally.isRemoved() || !ally.isAlive() || ally.level() != level) return;
        JobTraits4.shareStart(sp, ally, k);
        JobFx.chain(level, V.up(c, 1.1), V.up(ally.position(), 1.1), j, 2.0, 0.4, Opt.of("alpha", 1.0));
        JobFx.aegis(level, V.up(ally.position(), 1.1), 0.5, j, 0, 0.45, Opt.of("vel", RuneKit.bodyVel(ally)));
        DamageNumbers.showFace(sp, "§b§l수호", 2.2, 0, 0.1);
        SkillFx.play(level, "block.chain.place", ally.position(), 0.8, 0.9);
        Notify.notify(ally, "§b" + sp.getName().getString() + " §7님이 지켜 줍니다 (" + Math.round(k.num("share", 0.4) * 100) + "% · " + Math.round(k.num("shareFor", 80)
                / 20.0) + "초)", 40);
    }

    // ------------------------------------------------------------ 방패 투척

    /**
     * 방패 투척: 겨눈 적 (range 칸 — 없으면 앞으로 날아갔다 돌아옴) 에게 방패를 던져 bounceRange 칸 안의 다음 적으로 튕김 (bounces 명 — 적마다 한 번 hit · taunt 틱
     * 도발, 첫 타에 반격 게이지) → 손으로 돌아옴. 날아가는 빠르기 speed 칸/틱. 그림: 팽이처럼 도는 둥근 방패 · 맞은 자리 터짐 · 머리 위 "도발"
     */
    static boolean shieldThrow(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        double range = k.num("range", 10);
        int bounces = Math.max(1, (int) k.num("bounces", 5));
        double speed = k.num("speed", 1.4) * 20;
        String life = RuneKit.lifeToken(sp);
        RuneKit.Hand h = RuneKit.handOf(sp);
        Vec3 from = V.up(V.add(h.hand(), h.f(), 0.4), 0.15);
        LivingEntity first = Kit.pickTarget(sp, range, 15);
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.8, 1.0);
        SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 1.4, 0.5);
        if (first == null) {
            Vec3 to = V.add(sp.getEyePosition(), sp.getViewVector(1F), range * 0.8);
            int t = JobFx.bucklerFly(level, from, to, speed, 0.42, j, Opt.empty());
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) comeBack(sp, j, to, speed);
            });
            return true;
        }
        List<LivingEntity> chain = new ArrayList<>();
        chain.add(first);
        Set<UUID> seen = new HashSet<>();
        seen.add(first.getUUID());
        LivingEntity cur = first;
        while (chain.size() < bounces) {
            LivingEntity next = null;
            double best = 1e9;
            for (LivingEntity e : Kit.around(sp, cur.position(), k.num("bounceRange", 6), 3)) {
                if (seen.contains(e.getUUID())) continue;
                double d = e.distanceToSqr(cur);
                if (d < best) {
                    best = d;
                    next = e;
                }
            }
            if (next == null) break;
            chain.add(next);
            seen.add(next.getUUID());
            cur = next;
        }
        hop(sp, j, k, P, chain, 0, from, speed, life);
        return true;
    }

    /** 다음 적으로 날아감 (i 번째) — 닿으면 hit · 도발 · 다음, 다 맞히면 돌아옴 */
    private static void hop(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, List<LivingEntity> chain, int i, Vec3 from, double speed, String life) {
        if (!RuneKit.sameLife(sp, life)) return;
        ServerLevel level = sp.level();
        if (i >= chain.size()) {
            comeBack(sp, j, from, speed);
            return;
        }
        LivingEntity e = chain.get(i);
        if (e.isRemoved() || !e.isAlive() || e.level() != level) {
            hop(sp, j, k, P, chain, i + 1, from, speed, life);
            return;
        }
        Vec3 to = Kit.bodyPoint(e);
        int t = JobFx.bucklerFly(level, from, to, speed, 0.42, j, Opt.empty());
        RuneKit.later(t, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 at = e.isRemoved() ? to : Kit.bodyPoint(e);
            if (!e.isRemoved() && e.isAlive()) {
                Aegis.tauntFor(sp, e, (int) k.num("taunt", 60), true);
                JobKit.strike(sp, e, j, P, k.num("hit", 7.2), false, false, life, 0, d -> {
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), from), 0.25, 0.1);
                });
            }
            JobFx.burst(lv, at, 0.75, j, 0.25, Opt.empty());
            JobFx.ringFacing(lv, at, 0.7, j, V.unit(V.sub(from, at)), 0.25, Opt.empty());
            SkillFx.sparks(lv, at, JobKit.glow(j), 6, 4);
            SkillFx.play(lv, "item.shield.block", at, 1.1 + i * 0.06, 0.8);
            SkillFx.play(lv, "entity.zombie.attack_iron_door", at, 1.6, 0.35);
            hop(sp, j, k, P, chain, i + 1, at, speed, life);
        });
    }

    /** 손으로 돌아옴 */
    private static void comeBack(ServerPlayer sp, Jobs.Job j, Vec3 from, double speed) {
        if (sp.isRemoved()) return;
        RuneKit.Hand h = RuneKit.handOf(sp);
        Vec3 hand = V.up(V.add(h.hand(), h.f(), 0.3), 0.1);
        int t = JobFx.bucklerFly(sp.level(), from, hand, speed, 0.42, j, Opt.of("fade", 0.3));
        RuneKit.later(t, () -> {
            if (!sp.isRemoved()) SkillFx.play(sp.level(), "item.trident.return", sp.position(), 1.2, 0.8);
        });
    }

    // ------------------------------------------------------------ 철옹성

    /**
     * 철옹성: dur 틱 태세 — 웅크려 막기가 정면 front · 옆 side (JobTraits4 — RpgCombat.blockMod), 밀려나지 않음, 이동 − slow. 그림: 앞의 큰 빛의 방패가 솟음
     * (태세 동안 8틱마다 — 3인칭 · 다른 사람) · 1인칭 화면 아래 방패 테 · 발밑 고리 · 글자
     */
    static boolean bulwark(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 120); // 0.31.0 강화: 지속
        JobTraits4.bulwarkStart(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        JobFx.aegis(level, V.up(V.add(sp.position(), f, 0.95), 1.05), 0.62, j, 0, 0.3, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "solo", 3.0));
        JobFx.ring(level, V.up(sp.position(), 0.06), 2.2, j, false, 0.4, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.45);
        JobFx.aura(sp, j, 2.6, 0.8, 0.8);
        DamageNumbers.showFace(sp, "§b§l철옹성", 2.4, 0, 0.2);
        SkillFx.play(level, "item.shield.block", sp.position(), 0.6, 1.0);
        SkillFx.play(level, "block.iron_door.close", sp.position(), 0.7, 0.8);
        SkillFx.play(level, "entity.iron_golem.repair", sp.position(), 0.8, 0.8);
        return true;
    }

    // ------------------------------------------------------------ 성채

    /** 성벽 한 조각 (블록 표시 — 아래 모서리를 땅 g 에, 회전 q · 크기) */
    /**
     * 성벽 조각: base = 이 조각의 기준점이 땅에서 높은 만큼 (성벽 0 · 성가퀴 · 깃대 = 성벽 높이 · 깃발 = 망루 + 1.05 — 무너질 때 그만큼 더 가라앉음), seg = 둘레의 몇
     * 번째 성벽인지 (위 조각도 그 성벽과 같은 틱에 솟고 무너짐 — 0.30.0 사진첩: 조각마다 엇갈린 틱이라 성가퀴가 먼저 솟아 공중에 떠 있었음)
     */
    private record Brick(Display.BlockDisplay d, Quaternionf q, Vec3 off, double w, double h, double t, Vec3 out, double base, int seg) {}

    /**
     * 성채: 둘레 radius 칸에 돌 성벽이 솟아 (블록 표시 — 성가퀴 · 네 망루의 파란 깃발) dur 틱 요새 — 안의 아군 (나 포함) 받는 피해 − guard (JobTraits4), 성벽에 닿은
     * 밀리는 적은 바깥으로 밀려남 (push) → 무너지며 둘레 collapse 칸 (final — 큰 숫자 · 밀침). 그림: 성벽 · 깃발 · 1초마다 성벽 밑 빛의 고리 · 무너질 때 돌조각 · 먼지
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 7);
        int dur = (int) k.num("dur", 160);
        String life = RuneKit.lifeToken(sp);
        JobTraits4.fortStart(sp, c, r, dur, k.num("guard", 0.35));
        Ult.sky(level, c, "rpg:ult_still", dur + 20);
        JobFx.aura(sp, j, 3.0, 0.9, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        DamageNumbers.showFace(sp, "§b§l성채", 2.6, 0, 0.35);
        SkillFx.play(level, "entity.iron_golem.repair", c, 0.6, 1.0);
        SkillFx.play(level, "block.deepslate_bricks.place", c, 0.6, 1.0);
        List<Brick> bricks = new ArrayList<>();
        int n = 28;
        int life2 = dur + 26;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            boolean tower = i % 7 == 0;
            double w = tower ? 1.5 : 1.7;
            double h = tower ? 3.6 : 2.5;
            double t = tower ? 1.5 : 0.8;
            Vec3 p = V.add(c, out, r);
            Vec3 g = new Vec3(p.x, RuneKit.groundAt(level, p, c.y), p.z);
            Brick b = wallBlock(level, g, out, w, h, t, tower ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState(), life2, 0, i);
            if (b != null) bricks.add(b);
            if (!tower && i % 2 == 0) {
                // 성가퀴 (성벽 위 작은 돌)
                Brick top = wallBlock(level, V.up(g, h), out, 0.75, 0.55, 0.85, Blocks.STONE_BRICKS.defaultBlockState(), life2, h, i);
                if (top != null) bricks.add(top);
            }
            if (tower) {
                // 망루 위 깃대 (울타리 기둥) · 파란 깃발 (얇은 양털 판 — 바깥 옆으로)
                Vec3 top = V.up(g, h);
                Vec3 side = V.unit(V.cross(V.UP, out));
                Brick pole = wallBlock(level, top, out, 1.0, 1.8, 1.0, Blocks.DARK_OAK_FENCE.defaultBlockState(), life2, h, i);
                if (pole != null) bricks.add(pole);
                Brick cloth = wallBlock(level, V.up(V.add(top, side, 0.58), 1.05), out, 0.95, 0.62, 0.06, flagCloth(), life2, h + 1.05, i);
                if (cloth != null) bricks.add(cloth);
            }
        }
        // 솟음 (2틱 뒤부터 조금씩 엇갈리며 8틱) · 먼지 · 돌조각 · 흔들림
        for (int i = 0; i < bricks.size(); i++) {
            Brick b = bricks.get(i);
            int delay = 2 + (b.seg() % 4);
            RuneKit.later(delay, () -> FxDisplays.move(b.d(), FxDisplays.of(V.add(b.off(), V.scale(V.UP, -0.05)), b.q(), b.w(), b.h(), b.t()), 8));
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), r);
            Vec3 g = new Vec3(p.x, RuneKit.groundAt(level, p, c.y), p.z);
            RuneKit.later(3 + i % 3, () -> {
                RuneKit.rockBurst(level, V.up(g, 0.3), 3, 3.5, 0.18);
                SkillFx.fx(level, "rpg:dust", V.up(g, 0.6), Opt.of("size", 1.5, "life", 0.8, "color", DUST, "alpha", 0.5));
            });
        }
        RuneKit.shakeAround(level, c, 14, 0.35, 0.5);
        RuneKit.later(4, () -> SkillFx.play(level, "block.stone.place", c, 0.6, 1.0));
        RuneKit.later(7, () -> SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.7, 0.7));
        // 요새 동안: 4틱마다 성벽에 닿은 적을 밀어냄, 1초마다 성벽 밑 빛의 고리
        double push = k.num("push", 0.8);
        for (int t = 10; t < dur; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                if (tt % 20 == 10) JobFx.ring(lv, V.up(c, 0.08), r, j, tt % 40 == 10, 0.7, Opt.of("alpha", 0.45));
                for (LivingEntity e : Kit.around(sp, c, r + 1.3, 3)) {
                    double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
                    if (d < r - 1.1 || !JobKit.pushable(e)) continue;
                    RuneKit.knock(e, V.sub(e.position(), c), push * 0.6, 0.12);
                    if (RuneKit.fxQuota("jobFortPush", 4)) {
                        Vec3 at = V.up(V.add(c, V.flat(V.sub(e.position(), c)), r), 1.2);
                        JobFx.aegis(lv, at, 0.45, j, 2, 0.25, Opt.of("alpha", 0.8));
                        SkillFx.play(lv, "item.shield.block", at, 1.3, 0.5);
                    }
                }
            });
        }
        RuneKit.later(dur, () -> {
            JobTraits4.fortEnd(sp);
            if (RuneKit.sameLife(sp, life)) collapse(sp, j, k, P, c, r, bricks, life);
            else for (Brick b : bricks) FxDisplays.move(b.d(), FxDisplays.of(V.add(b.off(), V.scale(V.UP, -b.base() - b.h() - 0.4)), b.q(), b.w() * 0.9, b.h(), b.t() * 0.9), 12);
        });
        return true;
    }

    /** 깃발 천 (파란 양털 — 이 판에 없으면 청금석 블록) */
    private static BlockState flagCloth() {
        net.minecraft.world.level.block.Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier
                .withDefaultNamespace("blue_wool"));
        return b == null || b == Blocks.AIR ? Blocks.LAPIS_BLOCK.defaultBlockState() : b.defaultBlockState();
    }

    /**
     * 성벽 조각 하나 (처음엔 땅 밑 — 성벽 위 조각도 기준점 높이 base 만큼 더 내려가 땅 밑에서 시작: 0.30.0 사진첩에서 성벽이 솟기 전에 성가퀴가 공중에 떠 있었음)
     */
    private static Brick wallBlock(ServerLevel level, Vec3 g, Vec3 out, double w, double h, double t, BlockState st, int life, double base, int seg) {
        Vec3 ez = out;
        Vec3 ey = V.UP;
        Vec3 ex = V.unit(V.cross(ey, ez));
        Quaternionf q = FxDisplays.basis(ex, ey, ez);
        // 블록 모서리 → 가운데 아래 (x · z 반) 를 g 에
        Vec3 off = V.add(V.scale(ex, -w / 2), V.scale(ez, -t / 2));
        Display.BlockDisplay d = FxDisplays.block(level, g, st, FxDisplays.of(V.add(off, V.scale(V.UP, -base - h - 0.4)), q, w, h, t), life, null);
        return d == null ? null : new Brick(d, q, off, w, h, t, out, base, seg);
    }

    /** 무너짐: 성벽이 바깥으로 기울며 가라앉음 · 돌조각 · 먼지 · 흔들림 — 둘레 collapse 칸 (final — 큰 숫자 · 밀침) */
    private static void collapse(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 c, double r, List<Brick> bricks, String life) {
        ServerLevel level = sp.level();
        for (int i = 0; i < bricks.size(); i++) {
            Brick b = bricks.get(i);
            // 바깥쪽으로 40° 기울며 땅 밑으로 (12틱) — 성벽 위 조각 (성가퀴 · 깃대 · 깃발) 은 기준점 높이 base 만큼 더 내려감 (0.30.0 사진첩: 성벽이 가라앉은 뒤 공중에 떠 있었음)
            Vec3 tilt = V.unit(V.add(V.UP, b.out(), 0.85));
            Vec3 ez = V.unit(V.sub(b.out(), V.scale(tilt, V.dot(b.out(), tilt))));
            Vec3 ex = V.unit(V.cross(tilt, ez));
            Quaternionf q = FxDisplays.basis(ex, tilt, ez);
            Vec3 off = V.add(V.scale(ex, -b.w() / 2), V.scale(ez, -b.t() / 2));
            int delay = b.seg() % 3;
            RuneKit.later(delay, () -> FxDisplays.move(b.d(), FxDisplays.of(V.add(V.add(off, V.scale(tilt, -b.h() - 0.6)), V.scale(V.UP, -b.base())), q, b.w() * 0.9,
                    b.h(), b.t() * 0.9), 12));
        }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), r);
            Vec3 g = new Vec3(p.x, RuneKit.groundAt(level, p, c.y), p.z);
            RuneKit.later(2 + i % 4, () -> {
                RuneKit.rockBurst(level, V.up(g, 1.0), 5, 5.0, 0.22);
                SkillFx.fx(level, "rpg:dust", V.up(g, 0.8), Opt.of("size", 2.0, "life", 1.0, "color", DUST, "alpha", 0.55));
            });
        }
        JobFx.ring(level, V.up(c, 0.08), k.num("collapse", 9), j, false, 0.5, Opt.empty());
        JobFx.smash(level, V.up(c, 0.05), k.num("collapse", 9) * 0.7, j, 0.6, Opt.empty());
        Ult.flash(level, c, JobKit.glow(j), Opt.empty(), sp);
        RuneKit.shakeAround(level, c, 18, 0.5, 0.45);
        SkillFx.play(level, "block.deepslate_bricks.break", c, 0.6, 1.0);
        SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.6, 1.0);
        SkillFx.play(level, "entity.generic.explode", c, 0.8, 0.7);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, k.num("collapse", 9), 3.5)) {
            JobKit.strike(sp, e, j, P, k.num("final", 32), false, true, life, 0, d -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), c), 0.55, 0.4);
                if (cnt[0]++ < 6) JobFx.hit(e, j, 1.5);
            });
        }
    }
}
