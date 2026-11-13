package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.BowFx;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneMob;
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
 * 2차 직업 — 궁수 반전 배틀 아처 스킬, 2026-11-14 그림자 사수 (사용자가 고른 컨셉 — jobs.json advance.battlearcher: 보랏빛 · 옅은 라일락, 특성 「분신」 — JobTraits9):
 * 섀도 볼리 (분신 넷과 함께 쏘아 올린 열두 발이 휘어 둘레 적들에게) · 섀도 스위치 (분신이 적 무리로 뛰어듦 → 내려앉는 순간 자리를 바꿔 둘레에 화살) · 섀도 트랩 (분신이 덫처럼
 * 터지며 부채꼴 그림자 화살 조각) · 섀도 댄스 (태세 — 곁에서 함께 달리는 분신 둘 · 늘 세 갈래) · 섀도 레기온 (분신 여덟의 화살 물결 → 한 점에 모인 거대한 그림자 화살).
 * 피해 · 재사용 대기 · 틱 · 매커니즘은 2판 그대로 (섀도 스위치만 뛰어오름 대신 분신과 자리 바꿈 — 내려앉는 틱 · 자리 · 화살 그대로). 화살은 공격 피해 · 3칸 안 +20% ·
 * 10칸 밖 −30% (분신 — JobKit.hit). 화면 흔들림 없음. 연출 = 모드 없는 클라이언트에도 보이는 것 (3D 그림자 분신 몹 · 3D 그림자 화살 · 룬 그림 · 직업 연기)
 */
final class BattlearcherSkills {
    private BattlearcherSkills() {}

    static void register() {
        JobSystem.register("battlearcher_swarm", BattlearcherSkills::swarm);
        JobSystem.register("battlearcher_dive", BattlearcherSkills::dive);
        JobSystem.register("battlearcher_splinter", BattlearcherSkills::splinter);
        JobSystem.register("battlearcher_stride", BattlearcherSkills::stride);
        JobSystem.register("battlearcher_ult", BattlearcherSkills::ult);
    }

    /** 2차 베지어 (a → b, 조절점 c) 의 u 자리 */
    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double u) {
        double v = 1 - u;
        return new Vec3(v * v * a.x + 2 * v * u * c.x + u * u * b.x, v * v * a.y + 2 * v * u * c.y + u * u * b.y, v * v * a.z + 2 * v * u * c.z + u * u * b.z);
    }

    /**
     * 휘어 날아가는 3D 그림자 화살: a → (조절점 c) → b 를 ticks 틱에 (틱마다 곡선의 다음 자리 · 곡선을 따라 돎 — 촉이 b 에 닿는 틱 = ticks) · 틱마다 짧은 빛 꼬리 →
     * 닿으면 몇 틱 꽂혀 있다가 작아지며 사라짐
     */
    private static void curved(ServerPlayer sp, String life, Vec3 a, Vec3 c, Vec3 b, int ticks, double[] glow, double[] core) {
        double alen = 1.2;
        double r = ArrowFx.reach(alen);
        Vfx.Fx fx = RuneProps.fx(sp.level(), a, ticks + 16);
        Vec3 d0 = V.unit(V.sub(bez(a, c, b, 0.15), a));
        RuneSwords.Blade ar = RuneSwords.arrow(fx, a, d0, V.rightOf(V.flat(d0)), alen, 0.6, glow, core);
        for (int t = 1; t <= ticks; t++) {
            final int tt = t;
            fx.on(t, () -> {
                double u = tt / (double) ticks;
                Vec3 tip = bez(a, c, b, u);
                Vec3 back = bez(a, c, b, Math.max(0, u - 0.12));
                Vec3 d = V.len(V.sub(tip, back)) < 1e-3 ? d0 : V.unit(V.sub(tip, back));
                if (tt == 1) ar.grow(1, 1);
                ar.pose(V.sub(tip, V.scale(d, r)), d, V.rightOf(V.flat(d)), 1);
                if (RuneKit.sameLife(sp, life)) RuneKit.streak(sp.level(), back, tip, core, 0.08, 0.22, 0.85, 0);
            });
        }
        fx.on(ticks + 6, () -> ar.grow(0.1, 4));
        fx.on(ticks + 11, ar::remove);
    }

    // ------------------------------------------------------------ 섀도 볼리

    /**
     * 섀도 볼리 (battlearcher_swarm): 둘레 (보는 쪽에서 ±50° · ±130° · 1.7칸) 에 3D 그림자 분신 넷이 솟아 활을 하늘로 들고 launchAt 틱에 함께 쏘아 올림 (분신마다 셋 — 열두 발) →
     * flight 틱 휘어 날아 둘레 radius 칸 적들에게 나뉘어 꽂힘 (가까운 차례로 돌아가며 — 적이 하나면 모두, 적마다 맞은 수만큼 arrow × 수를 한 번에 · 숫자 여섯 줄까지). 적이 없으면
     * 둘레 땅에 꽂힘. 분신은 쏜 뒤 연기로 꺼짐
     */
    static boolean swarm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int n = Math.max(1, (int) k.num("arrows", 12));
        int launchAt = (int) k.num("launchAt", 2);
        int flight = Math.max(2, (int) k.num("flight", 5));
        double r = k.num("radius", 7);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 f0 = Kit.facingOf(sp);
        double[] angs = {50, -50, 130, -130};
        RuneMob[] clones = new RuneMob[angs.length];
        Vec3[] spots = new Vec3[angs.length];
        for (int i = 0; i < angs.length; i++) {
            Vec3 out = RuneKit.turn(f0, angs[i]);
            spots[i] = V.add(sp.position(), out, 1.7);
            clones[i] = ShadeArchers.spawn(sp, spots[i], V.unit(V.add(f0, out, 0.6)), launchAt + flight + 20, glow);
            if (clones[i] != null) clones[i].anim("burst_up");
        }
        RuneKit.zonePulse(level0, sp.position(), glow, 2.2, 0.35);
        SkillFx.play(level0, "crossbow.loading.end", sp.position(), 1.6, 0.8);
        SkillFx.play(level0, "mob.endermen.portal", sp.position(), 1.6, 0.4);
        RuneKit.later(launchAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, sp.position(), r, 4));
            foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
            if (foes.size() > n) foes = new ArrayList<>(foes.subList(0, n));
            int[] count = new int[Math.max(1, foes.size())];
            Vec3 f = Kit.facingOf(sp);
            for (int i = 0; i < n; i++) {
                int ci = i % angs.length;
                RuneMob m = clones[ci];
                Vec3 start = m != null && m.alive() ? ShadeArchers.bowUpOf(m) : V.up(spots[ci], 2.0);
                double a = Math.atan2(f.z, f.x) + (i + 0.5) * Math.PI * 2 / n;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 to;
                if (foes.isEmpty()) {
                    Vec3 g = V.add(sp.position(), out, 2.5 + (i % 3) * 1.0);
                    to = new Vec3(g.x, RuneKit.groundAt(level, g, sp.getY()) + 0.1, g.z);
                } else {
                    LivingEntity e = foes.get(i % foes.size());
                    count[i % foes.size()]++;
                    to = V.up(Kit.bodyPoint(e), (Kit.rand() - 0.5) * 0.6);
                }
                Vec3 ctrl = V.up(V.add(start, out, 1.6 + Kit.rand() * 0.8), 3.0 + Kit.rand() * 1.2);
                curved(sp, life, start, ctrl, to, flight, glow, core);
            }
            for (int ci = 0; ci < angs.length; ci++) {
                RuneMob m = clones[ci];
                Vec3 b = m != null && m.alive() ? ShadeArchers.bowUpOf(m) : V.up(spots[ci], 2.0);
                SkillFx.burst(level, b, glow, 0.7, 0.2);
                if (m != null) RuneKit.later(flight + 4, () -> ShadeArchers.leave(m, glow));
            }
            SkillFx.sparks(level, V.up(sp.position(), 2.0), core, 10, 5);
            SkillFx.play(level, "random.bow", sp.position(), 1.5, 1.0);
            SkillFx.play(level, "item.crossbow.quick_charge_3", sp.position(), 1.4, 0.8);
            SkillFx.play(level, "item.crossbow.shoot", sp.position(), 1.6, 0.7);
            for (int q = 0; q < foes.size(); q++) {
                LivingEntity e = foes.get(q);
                int got = count[q];
                if (got <= 0) continue;
                JobKit.strikeSplit(sp, e, j, P, k.num("arrow", 0.625) * got, Math.min(6, got), 1, false, false, life, flight, d -> {
                    JobFx.hit(e, j, 0.6 + 0.05 * got);
                    if (e.level() instanceof ServerLevel lv) {
                        SkillFx.sparks(lv, Kit.bodyPoint(e), core, 4 + got, 3);
                        JobFx.smoke(lv, Kit.bodyPoint(e), 0.6 + 0.04 * got, ShadeArchers.DARK, glow, 0.3, Opt.of("alpha", 0.7));
                    }
                });
            }
            if (foes.isEmpty()) RuneKit.later(flight, () -> {
                if (RuneKit.sameLife(sp, life)) RuneKit.zonePulse(sp.level(), sp.position(), glow, 3.0, 0.35);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 섀도 스위치

    /**
     * 섀도 스위치 (battlearcher_dive — id 그대로): 조준한 곳 (range 칸 — 겨눈 적의 발밑 · 땅) 둘레 3칸 적 무리의 가운데로 3D 그림자 분신이 lift 칸 높이로 뛰어듦 (flight 틱 —
     * 아래를 겨눈 자세 · 지나는 길에 그림자 잔상) → flight 틱에 분신과 자리를 바꿈 (나는 그곳에 · 분신은 떠난 자리에서 연기로 꺼짐 — 두 자리의 그림자 연기 · 이어진 그림자) →
     * 둘레 radius 칸 적마다 화살 한 발 (활 평타와 같은 피해 × arrow — RpgCombat.extraArrow · slow 틱 둔화). reset 틱 안에 그 적 하나라도 쓰러지면 재사용 대기가 바로 돌아옴
     * (JobTraits9). 묶였으면 쓰지 않음
     */
    static boolean dive(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        double range = k.num("range", 8);
        LivingEntity aimed = Kit.pickTarget(sp, range + 1, 12);
        Vec3 aim = aimed != null ? aimed.position() : Kit.aimPoint(sp, range);
        if (V.hdist(aim, sp.position()) > range) aim = V.add(sp.position(), V.flat(V.sub(aim, sp.position())), range);
        // 그 둘레 적 무리의 가운데 (적이 없으면 조준한 곳) — 내 쪽으로 조금 (적 몸 속에 내려앉지 않게)
        List<LivingEntity> group = Kit.around(sp, aim, 3.0, 4);
        Vec3 land = aim;
        if (!group.isEmpty()) {
            double x = 0, z = 0;
            for (LivingEntity e : group) {
                x += e.getX();
                z += e.getZ();
            }
            land = new Vec3(x / group.size(), aim.y, z / group.size());
            Vec3 back = V.flat(V.sub(sp.position(), land));
            land = V.add(land, back, 1.1);
        }
        land = new Vec3(land.x, RuneKit.groundAt(level0, land, sp.getY()), land.z);
        Vec3 stand = Kit.standSpot(level0, V.up(land, 0.5), 1.5);
        Vec3 dest = stand != null ? stand : land;
        int flight = Math.max(6, (int) k.num("flight", 12));
        String life = RuneKit.lifeToken(sp);
        Vec3 start = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 f0 = V.len(V.flat(V.sub(dest, start))) > 0.3 ? V.unit(V.flat(V.sub(dest, start))) : Kit.facingOf(sp);
        Kit.noFall(sp, flight + 30);
        // 분신이 뛰어듦 (몸에서 솟아 · 포물선 · 아래를 겨눈 자세)
        RuneMob clone = ShadeArchers.spawn(sp, start, f0, flight + 24, glow);
        if (clone != null) {
            clone.hold("dive");
            clone.moveTo(dest, flight, k.num("lift", 2.6), RuneMob.LINEAR, true);
        }
        SkillFx.play(level0, "entity.breeze.jump", start, 1.1, 1.0);
        SkillFx.play(level0, "mob.endermen.portal", start, 1.4, 0.6);
        // 지나는 길의 그림자 잔상 (2D 분신 · 2틱마다)
        for (int t = 2; t < flight; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double u = tt / (double) flight;
                Vec3 p = V.up(V.lerp(start, dest, u), 4 * k.num("lift", 2.6) * u * (1 - u));
                RuneKit.shadeClone(sp, sp.level(), p, f0, 2, ShadeArchers.DARK, glow, Opt.of("life", 0.3, "fade", 0.7, "alpha", 0.55));
                SkillFx.sparks(sp.level(), V.up(p, 0.9), glow, 2, 1.5);
            });
        }
        RuneKit.later(flight, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            // 자리 바꿈: 나는 분신이 내려앉은 자리로 · 분신은 떠난 자리에서 꺼짐
            Vec3 was = sp.position();
            boolean moved = V.len(V.sub(dest, was)) > 0.3 && Kit.moveTo(sp, dest);
            Vec3 c = sp.position();
            if (clone != null && clone.alive()) {
                clone.place(was);
                clone.face(f0);
                ShadeArchers.leave(clone, glow);
            } else RuneKit.shadeClone(sp, level, was, f0, 0, ShadeArchers.DARK, glow, Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.75));
            if (moved) {
                SkillFx.beam(level, V.up(was, 1), V.up(c, 1), ShadeArchers.DARK, Opt.of("half", 0.3, "thick", 0.26, "life", 0.3, "alpha", 0.75));
                RuneKit.streak(level, V.up(was, 1), V.up(c, 1), glow, 0.1, 0.3, 0.8, 0);
                ShadeArchers.puff(level, was, glow, 1.0);
            }
            double rad = k.num("radius", 3.5);
            ShadeArchers.puff(level, c, glow, 1.4);
            RuneKit.zonePulse(level, c, glow, rad, 0.45);
            BowFx.impact(level, c, glow, core, rad, "rare");
            SkillFx.sparks(level, V.up(c, 0.4), core, 14, 6);
            SkillFx.play(level, "mob.endermen.portal", c, 1.2, 0.8);
            SkillFx.play(level, "random.bow", c, 1.3, 1.0);
            Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, c, rad, 4)) {
                if (w == null || !"bow".equals(w.type())) break;
                int t = RpgCombat.extraArrow(sp, e, w, JobUpgrades.num(sp, k, "arrow", 1));
                if (t < 0) continue;
                hit.add(e);
                Vec3 from = V.up(c, 1.3);
                Vec3 to = Kit.bodyPoint(e);
                if (hit.size() <= 6) ArrowFx.arrow3d(level, from, to, Math.max(1, t), 1.1, 3, e, glow, core);
                RuneKit.streak(level, from, to, glow, 0.08, 0.25, 0.8, 0);
                RuneKit.effect(e, "slowness", (int) k.num("slow", 20), JobKit.isBoss(e) ? 0 : 1);
            }
            JobTraits9.diveFoes(sp, hit, (int) k.num("reset", 30));
        });
        return true;
    }

    // ------------------------------------------------------------ 섀도 트랩

    /**
     * 섀도 트랩 (battlearcher_splinter): 몸 앞 양옆 (오른쪽 · 왼쪽 1.1칸 · 앞 0.4칸 — 1인칭 화면 밖) 에 3D 그림자 분신 둘이 솟아 fireAt 틱 동안 활을 당김 → 분신이 덫처럼 터지며
     * 그림자 화살 조각 pellets 개가 부채꼴 (앞 range 칸 · spread 도) 로 흩어짐 — 적마다 맞는 조각 = 가까울수록 많이 (몸까지 close 칸 안이면 모두 · 그 밖은 close ÷ 거리 — minFrac
     * 까지), 배율 합 × 맞은 몫을 한 번에 (숫자 조각 수 — 여섯 줄까지) · close 칸 안의 적은 밀쳐냄 (knock)
     */
    static boolean splinter(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int fireAt = Math.max(1, (int) k.num("fireAt", 6));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 f0 = Kit.facingOf(sp);
        RuneMob[] clones = new RuneMob[2];
        for (int i = 0; i < 2; i++) {
            Vec3 at = V.add(V.add(sp.position(), V.rightOf(f0), i == 0 ? 1.1 : -1.1), f0, 0.4);
            clones[i] = ShadeArchers.spawn(sp, at, f0, fireAt + 20, glow);
            if (clones[i] != null) clones[i].anim("aim");
        }
        SkillFx.play(level0, "crossbow.loading.middle", sp.position(), 1.2, 0.8);
        SkillFx.play(level0, "mob.endermen.portal", sp.position(), 1.7, 0.4);
        // 당기는 동안 분신의 활에 모이는 그림자 빛
        for (int t = 1; t < fireAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (RuneMob m : clones) {
                    if (m == null || !m.alive()) continue;
                    Vec3 b = ShadeArchers.bowOf(m);
                    Vec3 o = V.add(b, new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.2, Kit.rand() - 0.5), 1.2);
                    SkillFx.fx(sp.level(), "rpg:arrow_spark", o, Opt.of("dir", V.unit(V.sub(b, o)), "speed", 6.0, "size", 0.13, "grow", 0.3, "life", 0.2, "color", glow,
                            "alpha", 1.0));
                }
            });
        }
        RuneKit.later(fireAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            double range = k.num("range", 7.5);
            double spread = k.num("spread", 60);
            int pellets = Math.max(1, (int) k.num("pellets", 12));
            double close = k.num("close", 3);
            double minFrac = k.num("minFrac", 0.25);
            Vec3 f = Kit.facingOf(sp);
            Vec3 rgt = V.rightOf(f);
            Vec3 from = V.up(V.add(sp.position(), f, 0.7), 1.35);
            // 분신이 터짐 (덫): 그림자 연기 · 보랏빛 번쩍임 → 꺼짐
            Vec3[] origin = new Vec3[2];
            for (int i = 0; i < 2; i++) {
                RuneMob m = clones[i];
                origin[i] = m != null && m.alive() ? ShadeArchers.bowOf(m) : V.add(from, rgt, i == 0 ? 1.0 : -1.0);
                SkillFx.burst(level, origin[i], glow, 1.1, 0.25);
                if (m != null) {
                    m.anim("shoot");
                    RuneMob mm = m;
                    RuneKit.later(2, () -> ShadeArchers.leave(mm, glow));
                }
            }
            // 조각: 부채꼴로 고르게 (조금씩 흔들림) — 분신 둘의 활에서 번갈아, 처음 맞는 적에서 멈춤 (그림만 — 3D 그림자 화살 · 빛줄)
            for (int i = 0; i < pellets; i++) {
                double a = Math.toRadians(-spread / 2 + spread * (i + 0.5) / pellets + (Kit.rand() - 0.5) * 4);
                Vec3 d = V.unit(V.add(V.scale(f, Math.cos(a)), rgt, Math.sin(a)));
                d = V.unit(V.add(d, V.UP, (Kit.rand() - 0.5) * 0.12));
                double len = Math.max(1, RuneKit.rayLength(level, from, d, range));
                Kit.LineHit h = null;
                for (Kit.LineHit x : RuneKit.lineTargets(sp, from, d, len, 0.3)) if (h == null || x.t() < h.t()) h = x;
                Vec3 to = V.add(from, d, h == null ? len : h.t());
                Vec3 o = origin[i % 2];
                int t = ArrowFx.flyTicks(o, to, 60);
                ArrowFx.arrow3d(level, o, to, Math.max(1, t), 0.9, 2, h == null ? null : h.e(), glow, core);
                RuneKit.streak(level, o, to, i % 3 == 0 ? glow : core, 0.08, 0.22, 0.9, 0);
            }
            RuneKit.zonePulse(level, sp.position(), glow, 1.6, 0.3);
            SkillFx.ring(level, V.add(from, f, 0.8), f, glow, 1.0, 0.25, 2.0);
            SkillFx.sparks(level, V.add(from, f, 0.6), core, 14, 6);
            SkillFx.play(level, "random.explode", from, 1.6, 0.5);
            SkillFx.play(level, "item.crossbow.shoot", from, 0.7, 1.0);
            SkillFx.play(level, "random.bow", from, 0.8, 1.0);
            for (LivingEntity e : RuneKit.coneTargets(sp, range, spread, 12)) {
                double d = Math.max(0, V.hdist(sp.position(), e.position()) - Kit.bodyPad(e));
                double frac = d <= close ? 1 : Math.max(minFrac, close / d);
                int hits = Math.max(1, (int) Math.round(pellets * frac));
                boolean near = d <= close;
                JobKit.strikeSplit(sp, e, j, P, k.total() * hits / pellets, Math.min(6, hits), 1, false, false, life, 0, x -> {
                    if (near && JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), sp.position())), k.num("knock", 0.6), 0.2);
                    JobFx.hit(e, j, near ? 1.1 : 0.7);
                    if (near) JobImpact.hit(sp, e, j, 1.0);
                    if (e.level() instanceof ServerLevel lv) JobFx.smoke(lv, Kit.bodyPoint(e), near ? 0.9 : 0.6, ShadeArchers.DARK, glow, 0.3, Opt.of("alpha", 0.7));
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 섀도 댄스

    /**
     * 섀도 댄스 (battlearcher_stride): dur 틱 태세 (강화 — 지속) — 이동 속도 + speed · 활 평타가 늘 세 갈래 (갈래 화살 = 평타 × side, JobTraits9 — 곁의 분신이 함께 쏨) ·
     * 3D 그림자 분신 둘이 곁에서 따라 달림 (ShadeArchers.dance). 그림: 발밑 그림자 연기 · 퍼지는 고리 · 위로 튀는 보랏빛
     */
    static boolean stride(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        JobTraits9.strideStart(sp, j, k);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        ShadeArchers.dance(sp, glow, dur);
        ShadeArchers.puff(level, c, glow, 1.4);
        RuneKit.zonePulse(level, c, glow, 2.4, 0.45);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3 p = V.add(V.up(c, 0.3), new Vec3(Math.cos(a), 0, Math.sin(a)), 0.8);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.2, Math.sin(a))), "speed", 3.5, "size", 0.15, "grow", 0.6, "life", 0.4,
                    "color", i % 2 == 0 ? core : glow, "alpha", 1.0));
        }
        SkillFx.play(level, "mob.endermen.portal", c, 1.3, 0.8);
        SkillFx.play(level, "item.trident.riptide_2", c, 1.4, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 섀도 레기온

    /** 레기온의 분신 자리 (몸 뒤 반원 — 보는 쪽에서 각 · 반지름) */
    private static final double[][] LEGION = {{-150, 2.4}, {150, 2.4}, {-118, 2.9}, {118, 2.9}, {-86, 3.1}, {86, 3.1}, {-170, 3.4}, {170, 3.4}};

    private static Vec3 legionSpot(ServerPlayer sp, int i) {
        Vec3 f = Kit.facingOf(sp);
        return V.add(sp.position(), RuneKit.turn(f, LEGION[i][0]), LEGION[i][1]);
    }

    /**
     * 섀도 레기온 (battlearcher_ult): 그림자 하늘 (rpg:ult_void) · 몸 뒤 반원에 3D 그림자 분신 여덟이 솟아 나를 따라오며 (천천히 걸음 — 느려짐) 보는 쪽 부채꼴 (range 칸 · cone 도)
     * 로 함께 쏨 — 2틱마다 화살 물결 (그림 — 분신 · 내 활에서, 처음 맞는 적까지) → first 틱부터 every 틱마다 pulses 번 부채꼴 안의 적마다 pulse (숫자 split 줄) →
     * finalAt − 8 틱 분신 여덟이 하늘로 쏨 (화살이 부채꼴 가운데 위 한 점으로 모임) → 모인 자리에 거대한 3D 그림자 화살이 맺혀 finalAt 틱에 부채꼴 가운데로 내리꽂힘 —
     * 부채꼴 안의 적 모두 final (큰 숫자 · 밀쳐냄)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int first = (int) k.num("first", 10);
        int every = Math.max(1, (int) k.num("every", 10));
        int pulses = Math.max(1, (int) k.num("pulses", 6));
        int finalAt = (int) k.num("finalAt", 70);
        int split = Math.max(1, (int) k.num("split", 6));
        double range = k.num("range", 11);
        double cone = k.num("cone", 70);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, sp.position(), "rpg:ult_void", finalAt + 30);
        RuneKit.effect(sp, "slowness", finalAt + 2, 1);
        RuneKit.domainSigil(level0, sp.position(), 3.4, "blade", glow, core, Opt.of("life", 0.9, "spinRate", -50.0, "grow", 0.1, "alpha", 0.8));
        SkillFx.play(level0, "mob.endermen.portal", sp.position(), 0.8, 1.0);
        SkillFx.play(level0, "entity.warden.sonic_charge", sp.position(), 1.6, 0.4);
        // 분신 여덟: 몸 뒤 반원에서 차례로 솟아 (2틱마다 둘) 나를 따라옴 · 보는 쪽을 봄
        RuneMob[] legion = new RuneMob[LEGION.length];
        for (int i = 0; i < LEGION.length; i++) {
            int ii = i;
            RuneKit.later(i / 2 * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneMob m = ShadeArchers.spawn(sp, legionSpot(sp, ii), Kit.facingOf(sp), finalAt + 30, glow);
                if (m == null) return;
                m.follow(() -> sp.isRemoved() ? null : legionSpot(sp, ii), () -> sp.isRemoved() ? null : Kit.facingOf(sp), 0.35, finalAt + 10);
                legion[ii] = m;
            });
        }
        int lastWave = first + (pulses - 1) * every;
        // 화살 물결 (그림): 2틱마다 다섯 — 분신 넷 · 내 활 하나 (부채꼴 안 고르게 흔들림 · 처음 맞는 적에서 멈춤), 분신은 번갈아 재빨리 당겨 쏨
        for (int t = 4; t <= lastWave + 2; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 f = Kit.facingOf(sp);
                Vec3 rgt = V.rightOf(f);
                for (int i = 0; i < 5; i++) {
                    RuneMob m = i < 4 ? legion[(tt / 2 * 4 + i) % legion.length] : null;
                    Vec3 from = m != null && m.alive() ? ShadeArchers.bowOf(m) : V.up(V.add(sp.position(), f, 0.6), 1.35);
                    if (m != null && m.alive() && tt % 4 == 0) m.anim("burst");
                    double a = Math.toRadians((Kit.rand() - 0.5) * cone);
                    Vec3 d = V.unit(V.add(V.scale(f, Math.cos(a)), rgt, Math.sin(a)));
                    d = V.unit(V.add(d, V.UP, (Kit.rand() - 0.4) * 0.15));
                    Vec3 o = V.up(V.add(sp.position(), f, 0.6), 1.35);
                    double len = Math.max(1, RuneKit.rayLength(level, o, d, range));
                    Kit.LineHit h = null;
                    for (Kit.LineHit x : RuneKit.lineTargets(sp, o, d, len, 0.3)) if (h == null || x.t() < h.t()) h = x;
                    Vec3 to = V.add(o, d, h == null ? len : h.t());
                    RuneKit.flyArrow(level, from, to, 55, glow, core, 0.7, 0.4);
                    if (i < 2) RuneKit.streak(level, from, to, core, 0.06, 0.2, 0.8, 0);
                }
                if (tt % 4 == 0) SkillFx.play(level, "random.bow", sp.position(), 1.1 + Kit.rand() * 0.4, 0.8);
                if (tt % 8 == 0) SkillFx.ring(level, V.add(V.up(V.add(sp.position(), f, 0.6), 1.35), f, 1.2), f, glow, 0.9, 0.25, 2.0);
            });
        }
        for (int i = 0; i < pulses; i++) {
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : RuneKit.coneTargets(sp, range, cone, 16)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("pulse", 8), split, 1, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobBaStorm", 6)) JobFx.hit(e, j, 0.9);
                        if (e.level() instanceof ServerLevel lv && RuneKit.fxQuota("jobBaStormSpark", 6)) SkillFx.sparks(lv, Kit.bodyPoint(e), core, 5, 3);
                    });
                }
            });
        }
        JobKit.motionLater(sp, "battlearcher_ult:final", finalAt - 9, life);
        // finalAt − 9: 분신 여덟이 하늘로 겨눔 → finalAt − 8: 함께 쏨 (화살이 부채꼴 가운데 위 한 점으로 모임)
        RuneKit.later(finalAt - 10, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (RuneMob m : legion) if (m != null && m.alive()) m.anim("burst_up");
        });
        Vec3[] apex = {null};
        RuneKit.later(finalAt - 8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 mid = V.add(sp.position(), f, range * 0.55);
            Vec3 top = V.up(mid, 10);
            apex[0] = top;
            for (RuneMob m : legion) {
                Vec3 b = m != null && m.alive() ? ShadeArchers.bowUpOf(m) : V.up(sp.position(), 2.2);
                RuneKit.flyStreak(level, b, top, 70, glow, core, 0.9, 0.24);
                RuneKit.flyArrow(level, b, top, 70, glow, core, 0.8, 0.0);
            }
            Vec3 hand = RuneKit.bowHand(sp);
            RuneKit.flyStreak(level, hand, top, 70, glow, core, 0.9, 0.24);
            SkillFx.play(level, "item.crossbow.shoot", sp.position(), 0.6, 1.0);
            SkillFx.play(level, "mob.endermen.portal", top, 0.6, 1.0);
        });
        // 모인 자리에 거대한 그림자 화살 (3D 빛 화살 6칸 — 촉이 아래) 이 맺혀 (finalAt − 5 ~ − 3) 내리꽂힘 (finalAt − 3 ~ finalAt)
        RuneKit.later(finalAt - 5, () -> {
            if (!RuneKit.sameLife(sp, life) || apex[0] == null) return;
            ServerLevel level = sp.level();
            Vec3 top = apex[0];
            Vec3 g = new Vec3(top.x, RuneKit.groundAt(level, V.up(top, -8), sp.getY()), top.z);
            double alen = 6.0;
            double r = ArrowFx.reach(alen);
            Vfx.Fx fx = RuneProps.fx(level, top, 40);
            RuneSwords.Blade ar = RuneSwords.arrow(fx, V.up(top, r * 0.5), V.DOWN, new Vec3(1, 0, 0), alen, 0.2, glow, core);
            fx.on(1, () -> ar.grow(1, 2));
            fx.on(3, () -> ar.pose(V.up(g, r - 0.5), V.DOWN, new Vec3(1, 0, 0), 2));
            fx.on(22, () -> ar.grow(0.1, 6));
            fx.on(28, ar::remove);
            SkillFx.burst(level, top, glow, 2.4, 0.3);
            JobFx.smoke(level, top, 2.0, ShadeArchers.DARK, glow, 0.4, Opt.of("alpha", 0.8));
            RuneKit.flyStreak(level, top, V.up(g, 0.5), V.len(V.sub(top, g)) / 0.2, glow, core, 1.8, 0.5);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 mid = apex[0] != null ? new Vec3(apex[0].x, RuneKit.groundAt(level, V.up(apex[0], -8), sp.getY()), apex[0].z) : V.add(sp.position(), f, range * 0.55);
            // 내리꽂힌 자리: 큰 터짐 (흔들림 없음) · 부채꼴을 덮는 충격 고리 · 그림자 연기 · 갈라진 땅
            BowFx.bigImpact(level, mid, glow, core, range * 0.5);
            SkillFx.shock(level, mid, glow, range * 0.6, 0.5);
            JobFx.smoke(level, V.up(mid, 0.8), 2.6, ShadeArchers.DARK, glow, 0.5, Opt.of("alpha", 0.85));
            RuneKit.glowCrack(level, mid, 2.6, glow, core, Opt.of("life", 1.2));
            Ult.flash(level, mid, core, Opt.empty(), sp);
            SkillFx.play(level, "firework.large_blast", mid, 0.9, 1.0);
            SkillFx.play(level, "random.explode", mid, 1.2, 0.6);
            for (LivingEntity e : RuneKit.coneTargets(sp, range, cone, 16)) {
                JobKit.strike(sp, e, j, P, k.num("final", 12), false, true, life, 0, d -> {
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), sp.position())), k.num("knock", 0.5), 0.25);
                    JobImpact.hit(sp, e, j, 1.3);
                    if (e.level() instanceof ServerLevel lv) JobFx.smoke(lv, Kit.bodyPoint(e), 1.0, ShadeArchers.DARK, glow, 0.35, Opt.of("alpha", 0.8));
                });
            }
            for (RuneMob m : legion) {
                if (m == null) continue;
                RuneKit.later(2 + (int) (Kit.rand() * 6), () -> ShadeArchers.leave(m, glow));
            }
        });
        return true;
    }
}
