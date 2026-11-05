package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
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
 * 0.51.0 2차 직업 — 궁수 반전 배틀 아처 스킬 (jobs.json advance.battlearcher — 폭풍 하늘빛 · 번개 노랑, 특성 「난사」 — JobTraits9). 재설계안 2판 (2026-11-06 사용자
 * "다음 작업" — 섀도 헌터 대신: 궁수를 뒤집음 — 멀리서 · 단일 · 한 발 · 멈춤 → 가까이 · 광역 · 난사 · 움직임, 롤 카이사 · 트리스타나 · 그레이브즈 · 미스 포츈 참고):
 * 호넷 스웜 (화살 열둘이 휘어 둘레 적들에게 나뉨) · 팰컨 다이브 (적 무리 가운데로 뛰어들어 내려앉으며 둘레에 화살 — 쓰러뜨리면 다시) · 스플린터 샷 (부채꼴 화살 조각 — 가까울수록
 * 많이) · 스톰 스트라이드 (태세 — 이동 + 늘 세 갈래) · 퀴버 스톰 (3초 부채꼴 화살 물결 → 화살비). 화살은 공격 피해 · 3칸 안 +20% · 10칸 밖 −30% (난사 — JobKit.hit).
 * 화면 흔들림 없음 (사용자 "멀미나"). 연출 = 2D 그림 (빛의 화살 rfx_jarrow2 · 폭풍 매 rfx_jfalcon · 바람 고리 · 터짐 · 하늘빛 불티).
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

    private static Vec3 bowAt(ServerPlayer sp, double ahead) {
        return V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), ahead);
    }

    /** 2차 베지어 (a → b, 조절점 c) 의 u 자리 */
    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double u) {
        double v = 1 - u;
        return new Vec3(v * v * a.x + 2 * v * u * c.x + u * u * b.x, v * v * a.y + 2 * v * u * c.y + u * u * b.y, v * v * a.z + 2 * v * u * c.z + u * u * b.z);
    }

    /** 휘어 날아가는 화살: a → (조절점 c) → b 를 ticks 틱에 — 틱마다 짧은 화살 한 조각 (다음 자리로 움직이며) · 하늘빛 꼬리 */
    private static void curved(ServerPlayer sp, String life, Vec3 a, Vec3 c, Vec3 b, int ticks, Jobs.Job j, double size) {
        for (int t = 0; t < ticks; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 p = bez(a, c, b, tt / (double) ticks);
                Vec3 q = bez(a, c, b, (tt + 1) / (double) ticks);
                JobFx.arrow(level, p, q, j, V.len(V.sub(q, p)) * 20, size, Opt.of("fade", 0.05));
                RuneKit.streak(level, p, q, JobKit.core(j), 0.09, 0.25, 0.85, 0);
            });
        }
    }

    // ------------------------------------------------------------ 호넷 스웜

    /**
     * 호넷 스웜: 활을 머리 위로 들어 launchAt 틱에 화살 arrows 발을 사방으로 쏘아 올림 (어깨 둘레 고리에서 바깥 · 위로) → flight 틱 휘어 날아 둘레 radius 칸 적들에게 나뉘어 꽂힘
     * (가까운 차례로 돌아가며 — 적이 하나면 모두, 적마다 맞은 수만큼 arrow × 수를 한 번에 · 숫자 여섯 줄까지). 적이 없으면 둘레 땅에 꽂힘
     */
    static boolean swarm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int n = Math.max(1, (int) k.num("arrows", 12));
        int launchAt = (int) k.num("launchAt", 2);
        int flight = Math.max(2, (int) k.num("flight", 5));
        double r = k.num("radius", 7);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        // 어깨 위에 화살 고리가 맺힘 (쏘아 올리기 전)
        Vec3 c0 = V.up(sp.position(), 1.7);
        for (int i = 0; i < n; i += 2) {
            double a = i * Math.PI * 2 / n;
            Vec3 p = V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.9);
            JobFx.arrowAt(level0, V.up(p, 0.4), V.UP, 0.4, launchAt / 20.0 + 0.05, j, JobFx.hide1p(sp).with("alpha", 0.9, "vel", RuneKit.bodyVel(sp)));
        }
        JobFx.wind(level0, V.up(sp.position(), 0.06), 1.4, j, 0.35, Opt.empty());
        SkillFx.play(level0, "crossbow.loading.end", sp.position(), 1.6, 0.8);
        RuneKit.later(launchAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, sp.position(), r, 4));
            foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
            if (foes.size() > n) foes = new ArrayList<>(foes.subList(0, n));
            int[] count = new int[Math.max(1, foes.size())];
            Vec3 c = V.up(sp.position(), 1.6);
            Vec3 f = Kit.facingOf(sp);
            for (int i = 0; i < n; i++) {
                double a = Math.atan2(f.z, f.x) + (i + 0.5) * Math.PI * 2 / n;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 start = V.add(c, out, 0.8);
                Vec3 to;
                if (foes.isEmpty()) {
                    Vec3 g = V.add(sp.position(), out, 2.5 + (i % 3) * 1.0);
                    to = new Vec3(g.x, RuneKit.groundAt(level, g, sp.getY()) + 0.1, g.z);
                } else {
                    LivingEntity e = foes.get(i % foes.size());
                    count[i % foes.size()]++;
                    to = V.up(Kit.bodyPoint(e), (Kit.rand() - 0.5) * 0.6);
                }
                Vec3 ctrl = V.up(V.add(c, out, 2.4 + Kit.rand() * 0.8), 2.2 + Kit.rand() * 1.2);
                curved(sp, life, start, ctrl, to, flight, j, 0.72);
            }
            JobFx.burst(level, V.up(sp.position(), 2.0), 0.9, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.halo(level, V.up(sp.position(), 1.8), 1.6, j, 0.3, Opt.of("alpha", 0.85));
            SkillFx.sparks(level, V.up(sp.position(), 2.0), JobKit.core(j), 10, 5);
            SkillFx.play(level, "random.bow", sp.position(), 1.5, 1.0);
            SkillFx.play(level, "item.crossbow.quick_charge_3", sp.position(), 1.4, 0.8);
            SkillFx.play(level, "item.crossbow.shoot", sp.position(), 1.6, 0.7);
            for (int q = 0; q < foes.size(); q++) {
                LivingEntity e = foes.get(q);
                int got = count[q];
                if (got <= 0) continue;
                JobKit.strikeSplit(sp, e, j, P, k.num("arrow", 0.625) * got, Math.min(6, got), 1, false, false, life, flight, d -> {
                    JobFx.hit(e, j, 0.6 + 0.05 * got);
                    if (e.level() instanceof ServerLevel lv) SkillFx.sparks(lv, Kit.bodyPoint(e), JobKit.core(j), 4 + got, 3);
                });
            }
            if (foes.isEmpty()) RuneKit.later(flight, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx.wind(sp.level(), V.up(sp.position(), 0.06), 3.0, j, 0.35, Opt.of("alpha", 0.7));
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 팰컨 다이브

    /**
     * 팰컨 다이브: 조준한 곳 (range 칸 — 겨눈 적의 발밑 · 땅) 둘레 3칸 적 무리의 가운데로 lift 칸 높이 뛰어오름 (flight 틱 — 폭풍 매가 함께 날아 내리꽂힘) → 내려앉으며 둘레
     * radius 칸 적마다 화살 한 발 (활 평타와 같은 피해 × arrow — RpgCombat.extraArrow · slow 틱 둔화) · 땅에 하늘빛 고리 · 바람. reset 틱 안에 그 적 하나라도 쓰러지면
     * 재사용 대기가 바로 돌아옴 (JobTraits9). 묶였으면 쓰지 않음
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
        JobMove.run(sp, JobMove.hop(0, flight, from -> dest, k.num("lift", 2.6)));
        Kit.noFall(sp, flight + 30);
        // 떠남: 발밑 바람 회오리 · 깃털 · 바람 소리
        JobFx.wind(level0, V.up(start, 0.08), 1.8, j, 0.45, Opt.empty());
        JobFx.feathers(level0, V.up(start, 1.0), 6, 0.8, 0.2, j, 1.0, JobFx.hide1p(sp));
        SkillFx.play(level0, "entity.breeze.jump", start, 1.1, 1.0);
        SkillFx.play(level0, "entity.phantom.flap", start, 1.3, 0.8);
        // 폭풍 매가 따로 날아 먼저 내리꽂힘 (2틱마다 한 조각 — 몸 위 오른쪽 3.6칸에서 더 높이 솟았다가 내려앉는 곳으로, 영상 1 은 몸에 겹쳐 날개 달린 사람으로 보였음)
        Vec3 side = V.rightOf(V.flat(V.sub(dest, start)));
        Vec3 f0 = V.add(V.up(start, 3.6), side, 1.8);
        Vec3 sky = V.add(V.up(V.lerp(start, dest, 0.45), k.num("lift", 2.6) + 4.6), side, 1.0);
        Vec3 f1 = V.up(dest, 0.7);
        int falconT = Math.max(4, flight - 2);
        for (int t = 0; t < falconT; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                double u = tt / (double) falconT;
                Vec3 p = bez(f0, sky, f1, u);
                Vec3 q = bez(f0, sky, f1, Math.min(1, u + 2.0 / falconT));
                JobFx.falcon(level, p, V.sub(q, p), 1.9, u > 0.5, 0.15, Opt.of("vel", V.scale(V.sub(q, p), 10), "alpha", 0.95), j);
                RuneKit.streak(level, V.up(sp.position(), 0.9), V.up(sp.position(), 1.4), JobKit.glow(j), 0.1, 0.25, 0.6, 0);
                JobFx.feathers(level, p, 1, 0.4, 0.16, j, 0.6, Opt.empty());
            });
        }
        RuneKit.later(flight, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            double rad = k.num("radius", 3.5);
            JobFx.halo(level, V.up(c, 0.06), rad, j, 0.45, Opt.empty());
            JobFx.wind(level, V.up(c, 0.08), rad * 0.8, j, 0.4, Opt.empty());
            JobFx.burst(level, V.up(c, 0.6), 1.1, j, 0.3, Opt.empty());
            JobFx.falcon(level, V.up(c, 0.9), V.DOWN, 1.6, true, 0.2, Opt.empty(), j);
            SkillFx.sparks(level, V.up(c, 0.4), JobKit.core(j), 14, 6);
            SkillFx.play(level, "entity.breeze.wind_burst", c, 1.0, 1.0);
            SkillFx.play(level, "random.bow", c, 1.3, 1.0);
            Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
            List<LivingEntity> hit = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, c, rad, 4)) {
                if (w == null || !"bow".equals(w.type())) break;
                if (RpgCombat.extraArrow(sp, e, w, JobUpgrades.num(sp, k, "arrow", 1)) < 0) continue;
                hit.add(e);
                Vec3 to = Kit.bodyPoint(e);
                JobFx.arrow(level, V.up(c, 1.3), to, j, 60, 0.55, Opt.empty());
                RuneKit.effect(e, "slowness", (int) k.num("slow", 20), JobKit.isBoss(e) ? 0 : 1);
            }
            JobTraits9.diveFoes(sp, hit, (int) k.num("reset", 30));
        });
        return true;
    }

    // ------------------------------------------------------------ 스플린터 샷

    /**
     * 스플린터 샷: fireAt 틱 당김 (활에 빛이 모임) → 부채꼴 (앞 range 칸 · spread 도) 로 화살 조각 pellets 개가 터짐 — 적마다 맞는 조각 = 가까울수록 많이 (몸까지 close 칸
     * 안이면 모두 · 그 밖은 close ÷ 거리 — minFrac 까지), 배율 합 × 맞은 몫을 한 번에 (숫자 조각 수 — 여섯 줄까지) · close 칸 안의 적은 밀쳐냄 (knock)
     */
    static boolean splinter(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int fireAt = Math.max(1, (int) k.num("fireAt", 6));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        JobFx.charge(sp, j, new Vec3(-0.25, 1.35, 0.8), fireAt, 0.55);
        JobFx.bow(level0, bowAt(sp, 0.5), 0.55, j, 0, 4, fireAt / 20.0 + 0.05, Opt.of("vel", RuneKit.bodyVel(sp)));
        SkillFx.play(level0, "crossbow.loading.middle", sp.position(), 1.2, 0.8);
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
            // 조각: 부채꼴로 고르게 (조금씩 흔들림) — 처음 맞는 적에서 멈춤 (그림만)
            for (int i = 0; i < pellets; i++) {
                double a = Math.toRadians(-spread / 2 + spread * (i + 0.5) / pellets + (Kit.rand() - 0.5) * 4);
                Vec3 d = V.unit(V.add(V.scale(f, Math.cos(a)), rgt, Math.sin(a)));
                d = V.unit(V.add(d, V.UP, (Kit.rand() - 0.5) * 0.12));
                double len = Math.max(1, RuneKit.rayLength(level, from, d, range));
                Kit.LineHit h = null;
                for (Kit.LineHit x : RuneKit.lineTargets(sp, from, d, len, 0.3)) if (h == null || x.t() < h.t()) h = x;
                Vec3 to = V.add(from, d, h == null ? len : h.t());
                JobFx.arrow(level, from, to, j, 60, 0.55, Opt.of("fade", 0.05));
                RuneKit.streak(level, from, to, i % 3 == 0 ? JobKit.glow(j) : JobKit.core(j), 0.08, 0.22, 0.9, 0);
            }
            JobFx.burst(level, from, 0.9, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.ringFacing(level, V.add(from, f, 0.8), 1.0, j, f, 0.25, Opt.of("alpha", 0.9));
            JobFx.wind(level, V.up(sp.position(), 0.06), 1.3, j, 0.3, Opt.of("alpha", 0.8));
            SkillFx.sparks(level, V.add(from, f, 0.6), JobKit.core(j), 14, 6);
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
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스톰 스트라이드

    /**
     * 스톰 스트라이드: dur 틱 태세 (강화 — 지속) — 이동 속도 + speed · 활 평타가 늘 세 갈래 (갈래 화살 = 평타 × side, JobTraits9). 그림: 발밑 폭풍 바람 두 겹 · 몸 둘레 바람 ·
     * 위로 튀는 번개 불티 · 바람 소리
     */
    static boolean stride(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        JobTraits9.strideStart(sp, j, k);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.wind(level, V.up(c, 0.06), 2.2, j, 0.5, Opt.empty());
        JobFx.wind(level, V.up(c, 0.5), 1.4, j, 0.4, Opt.of("alpha", 0.8));
        JobFx.twister(sp, j, 2.6, 0.7, 0.85);
        JobFx.speed(sp, j, 0.5, 0.9, RuneKit.bodyVel(sp));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3 p = V.add(V.up(c, 0.3), new Vec3(Math.cos(a), 0, Math.sin(a)), 0.8);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.2, Math.sin(a))), "speed", 3.5, "size", 0.15, "grow", 0.6, "life", 0.4,
                    "color", JobKit.core(j), "alpha", 1.0));
        }
        SkillFx.play(level, "entity.breeze.wind_burst", c, 1.3, 0.8);
        SkillFx.play(level, "item.trident.riptide_2", c, 1.4, 0.7);
        return true;
    }

    // ------------------------------------------------------------ 퀴버 스톰

    /**
     * 퀴버 스톰: 폭풍 하늘 (rpg:ult_tempest) · 천천히 걸으며 (느려짐) 보는 쪽 부채꼴 (range 칸 · cone 도 — 쏘는 때의 보는 쪽) 로 2틱마다 화살 물결 (그림 — 처음 맞는 적까지) →
     * first 틱부터 every 틱마다 pulses 번 부채꼴 안의 적마다 pulse (숫자 split 줄) → finalAt − 8 틱 하늘로 화살 다발을 쏘아 올림 → finalAt 틱 부채꼴 안에 화살비가 한꺼번에
     * 내리꽂힘 (final · 큰 숫자 · 밀쳐냄)
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
        Ult.sky(level0, sp.position(), "rpg:ult_tempest", finalAt + 30);
        RuneKit.effect(sp, "slowness", finalAt + 2, 1);
        JobFx.aura(sp, j, 2.8, 0.9, 0.9);
        JobFx.wind(level0, V.up(sp.position(), 0.06), 2.4, j, 0.5, Opt.empty());
        SkillFx.play(level0, "entity.breeze.wind_burst", sp.position(), 0.8, 1.0);
        SkillFx.play(level0, "item.trident.thunder", sp.position(), 1.6, 0.5);
        int lastWave = first + (pulses - 1) * every;
        // 화살 물결 (그림): 2틱마다 넷 — 부채꼴 안 고르게 흔들림 · 처음 맞는 적에서 멈춤
        for (int t = 2; t <= lastWave + 2; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 f = Kit.facingOf(sp);
                Vec3 rgt = V.rightOf(f);
                Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.35);
                for (int i = 0; i < 5; i++) {
                    double a = Math.toRadians((Kit.rand() - 0.5) * cone);
                    Vec3 d = V.unit(V.add(V.scale(f, Math.cos(a)), rgt, Math.sin(a)));
                    d = V.unit(V.add(d, V.UP, (Kit.rand() - 0.4) * 0.15));
                    double len = Math.max(1, RuneKit.rayLength(level, from, d, range));
                    Kit.LineHit h = null;
                    for (Kit.LineHit x : RuneKit.lineTargets(sp, from, d, len, 0.3)) if (h == null || x.t() < h.t()) h = x;
                    Vec3 to = V.add(from, d, h == null ? len : h.t());
                    JobFx.arrow(level, from, to, j, 55, 0.66, Opt.of("fade", 0.05));
                    if (i < 2) RuneKit.streak(level, from, to, JobKit.core(j), 0.06, 0.2, 0.8, 0);
                }
                if (tt % 4 == 0) {
                    JobFx.bow(level, bowAt(sp, 0.55), 0.6, j, 3, 3, 0.2, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
                    SkillFx.play(level, "random.bow", sp.position(), 1.1 + Kit.rand() * 0.4, 0.8);
                }
                if (tt % 8 == 0) JobFx.ringFacing(level, V.add(from, f, 1.2), 0.9, j, f, 0.25, Opt.of("alpha", 0.8, "vel", RuneKit.bodyVel(sp)));
            });
        }
        for (int i = 0; i < pulses; i++) {
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : RuneKit.coneTargets(sp, range, cone, 16)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("pulse", 8), split, 1, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobBaStorm", 6)) JobFx.hit(e, j, 0.9);
                        if (e.level() instanceof ServerLevel lv && RuneKit.fxQuota("jobBaStormSpark", 6)) SkillFx.sparks(lv, Kit.bodyPoint(e), JobKit.core(j), 5, 3);
                    });
                }
            });
        }
        JobKit.motionLater(sp, "battlearcher_ult:final", finalAt - 9, life);
        // 하늘로 화살 다발 (finalAt − 8) → 부채꼴 안에 화살비 (finalAt − 6 ~ finalAt)
        RuneKit.later(finalAt - 8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 hand = RuneKit.bowHand(sp);
            for (int i = 0; i < 6; i++) JobFx.arrow(level, hand, V.add(V.up(hand, 9 + i * 0.5), Kit.facingOf(sp), 2 + i * 0.6), j, 60, 0.6, Opt.empty());
            JobFx.burst(level, V.up(hand, 0.6), 0.7, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "item.crossbow.shoot", sp.position(), 0.6, 1.0);
        });
        int fall = 6;
        RuneKit.later(finalAt - fall, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 rgt = V.rightOf(f);
            for (int i = 0; i < 26; i++) {
                double a = Math.toRadians((Kit.rand() - 0.5) * cone);
                double dist = 2 + Kit.rand() * (range - 2);
                Vec3 g = V.add(sp.position(), V.unit(V.add(V.scale(f, Math.cos(a)), rgt, Math.sin(a))), dist);
                Vec3 to = new Vec3(g.x, RuneKit.groundAt(level, g, sp.getY()) + 0.1, g.z);
                Vec3 from = V.add(V.up(to, 8 + Kit.rand() * 2), f, -2.0);
                JobFx.arrow(level, from, to, j, V.len(V.sub(to, from)) / (fall / 20.0), 0.8, Opt.of("fade", 0.05));
            }
            for (LivingEntity e : RuneKit.coneTargets(sp, range, cone, 16)) {
                Vec3 to = Kit.bodyPoint(e);
                for (int p = 0; p < 2; p++) {
                    Vec3 from = V.add(V.up(to, 8 + p), f, -2.0);
                    JobFx.arrow(level, from, V.up(to, (p - 0.5) * 0.3), j, V.len(V.sub(to, from)) / (fall / 20.0), 0.85, Opt.of("fade", 0.05));
                    RuneKit.streak(level, from, to, JobKit.core(j), 0.07, 0.3, 0.8, 0);
                }
            }
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 mid = V.add(sp.position(), f, range * 0.55);
            JobFx.halo(level, V.up(mid, 0.06), range * 0.5, j, 0.5, Opt.empty());
            JobFx.wind(level, V.up(mid, 0.08), range * 0.45, j, 0.45, Opt.empty());
            Ult.flash(level, mid, JobKit.core(j), Opt.empty(), sp);
            SkillFx.play(level, "firework.large_blast", mid, 0.9, 1.0);
            SkillFx.play(level, "random.explode", mid, 1.2, 0.6);
            for (LivingEntity e : RuneKit.coneTargets(sp, range, cone, 16)) {
                JobKit.strike(sp, e, j, P, k.num("final", 12), false, true, life, 0, d -> {
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), sp.position())), k.num("knock", 0.5), 0.25);
                    JobImpact.hit(sp, e, j, 1.3);
                    if (e.level() instanceof ServerLevel lv) {
                        JobFx.burst(lv, Kit.bodyPoint(e), 0.9, j, 0.3, Opt.empty());
                        JobFx.arrowAt(lv, V.up(e.position(), 0.05), V.DOWN, 0.5, 0.8, j, Opt.of("alpha", 0.9, "fade", 0.3));
                    }
                });
            }
        });
        return true;
    }
}
