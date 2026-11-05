package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 궁수 각성 스나이퍼 스킬 (jobs.json advance.sniper — 밝은 연두 · 금빛 · 흰 섬광, 특성 「호흡」 최대 8 · 급소 사격 — JobTraitsMore · JobTraits9).
 * 재설계안 2판 (2026-11-06 사용자 "다음 작업" — 궁수 차례 · 롤 바루스 · 케이틀린 · 미스 포츈 · 베인 · 진 참고): 1차와 같은 매커니즘 (원거리 · 단일 · 정밀 단타 · 멈춰 섬) 의
 * 새 스킬 — 하트시커 (끝까지 당겨 큰 한 발 — 다시 눌러 놓음) · 스네어 샷 (그물 화살 · 반동으로 물러남 · 호흡 +3) · 트릭 샷 (맞고 튀어 다른 적에게 두 번 — 치명) ·
 * 헌터스 마크 (태세 — 세 번째 화살마다 은빛 화살) · 데드아이 (저격 자세 — 다시 눌러 세 발 · 셋째 치명). 화살은 공격 피해. 화면 흔들림 없음 (사용자 "멀미나").
 * 연출 = 2D 그림 (거대한 화살 rfx_jgreatarrow · 약점 과녁 rfx_jweak · 그물 rfx_jnet · 빛의 활 · 바람 고리).
 */
final class SniperSkills {
    private SniperSkills() {}

    /** 흰 섬광 (급소 · 큰 화살의 심지) */
    private static final double[] FLASH = JobTraits9.SNIPE;
    /** 금빛 (튄 화살 · 데드아이 셋째) */
    private static final double[] GOLD = {1.0, 0.84, 0.32};

    static void register() {
        JobSystem.register("sniper_heart", SniperSkills::heart);
        JobSystem.register("sniper_snare", SniperSkills::snare);
        JobSystem.register("sniper_trick", SniperSkills::trick);
        JobSystem.register("sniper_mark", SniperSkills::mark);
        JobSystem.register("sniper_ult", SniperSkills::ult);
    }

    /** 빛의 활 자리 (활을 든 손 앞) */
    private static Vec3 bowAt(ServerPlayer sp, double ahead) {
        return V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), ahead);
    }

    private static boolean alive(ServerPlayer sp, String life, LivingEntity e) {
        return RuneKit.sameLife(sp, life) && e != null && !e.isRemoved() && e.isAlive();
    }

    /** 보이는 빠르기: 가까운 적이면 화살이 적어도 2틱 날아가게 (len 칸을 speed 칸/초 — 1틱이면 날아가는 것이 보이지 않음) */
    private static double visSpeed(double speed, double len) {
        return Math.min(speed, Math.max(25, len / 0.1));
    }

    /** 맞은 적에 꽂힌 큰 화살 (촉이 몸 너머로 조금 · 화살대가 쏜 쪽으로 — life 초, 적을 따라감) */
    private static void stuck(ServerLevel level, LivingEntity e, Vec3 look, double size, double life, double[] glow, double[] core) {
        Vec3 tip = V.add(Kit.bodyPoint(e), look, 0.3 + Kit.bodyPad(e));
        JobFx.greatArrowAt(level, tip, look, size, life, Opt.of("vel", RuneKit.entVel(e), "fade", 0.35), glow, core);
    }

    /** 보는 쪽 일직선에서 처음 맞는 적 (없으면 null) */
    private static Kit.LineHit firstOnLine(ServerPlayer sp, Vec3 from, Vec3 dir, double len, double half) {
        Kit.LineHit best = null;
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, dir, len, half)) if (best == null || h.t() < best.t()) best = h;
        return best;
    }

    // ------------------------------------------------------------ 하트시커

    /** 당기는 중 (시작 틱 · 목숨 · 끝남) */
    private record Draw(long start, String life, boolean[] done) {}

    private static final Map<UUID, Draw> DRAWS = new HashMap<>();

    /**
     * 하트시커: 활을 끝까지 당김 (charge 틱 — 느려짐 · 밀려나지 않음 · 활 앞에 큰 화살이 걸려 당긴 만큼 커짐 · 양옆에서 빛이 모여 듦 · 화살촉 앞 과녁이 조여 듦) → gap 틱 뒤부터
     * 다시 누르면 놓음 (안 누르면 charge 틱에 저절로 — 끝까지 당기면 번쩍). 놓으면 당긴 몫 c 만큼 멀리 (rangeMin → rangeMax 칸) · 세게 (배율 합 × (minPower → 1)) 날아가
     * 일직선 (폭 width) 에서 처음 맞는 적에게 큰 한 발 — 호흡 하나당 + breathPer (호흡 breathUse 씀)
     */
    static boolean heart(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int charge = Math.max(8, (int) k.num("charge", 20));
        if (JobRecast.stage(sp, k.id()) >= 1) {
            Draw d = DRAWS.get(sp.getUUID());
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":release");
            int held = d == null ? charge : (int) Math.min(charge, JobKit.now() - d.start());
            loose(sp, j, k, P, held, charge);
            return true;
        }
        String life = RuneKit.lifeToken(sp);
        boolean[] done = {false};
        DRAWS.put(sp.getUUID(), new Draw(JobKit.now(), life, done));
        RuneKit.effect(sp, "slowness", charge + 2, 1);
        RpgCombat.setSteadfast(sp, charge + 8);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        JobFx.wind(level0, V.up(sp.position(), 0.06), 1.2, j, 0.4, Opt.empty());
        SkillFx.play(level0, "crossbow.loading.start", sp.position(), 0.9, 0.9);
        for (int t = 1; t <= charge; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (done[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                double q = Math.min(1, tt / (double) charge);
                Vec3 look = sp.getViewVector(1F);
                Vec3 hand = RuneKit.bowHand(sp);
                // 활에 걸린 큰 화살 (3인칭 — 몸 앞) · 1인칭은 눈앞에 작게
                JobFx.greatArrowAt(level, V.add(hand, look, 1.0 + q * 0.7), look, 0.45 + q * 0.5, 0.1, JobFx.hide1p(sp).with("alpha", 0.95), glow, FLASH);
                JobFx.greatArrowAt(level, V.add(sp.getEyePosition(), look, 1.6 + q * 0.3), look, 0.22 + q * 0.12, 0.1, JobFx.only1p(sp).with("alpha", 0.8), glow, FLASH);
                if (tt % 2 == 0) {
                    // 양옆 · 위에서 빛이 화살촉으로 빨려 듦
                    Vec3 tip = V.add(hand, look, 1.9 + q * 1.4);
                    for (int sd : new int[] {-1, 1}) {
                        Vec3 a = V.add(V.add(tip, V.rightOf(V.flat(look)), sd * (1.6 - q)), V.UP, 0.9 - q * 0.6);
                        SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.unit(V.sub(tip, a)), "speed", 4.0, "size", 0.15, "grow", 0.5, "life", 0.22, "color", FLASH,
                                "alpha", 1.0));
                    }
                }
                if (tt % 5 == 0 || tt == charge) {
                    // 화살촉 앞 과녁이 조여 듦 (다 당기면 맞춰짐) · 차오르는 소리
                    Vec3 tip = V.add(hand, look, 2.4 + q * 1.4);
                    JobFx.target(level, tip, 0.9 - q * 0.45, j, V.scale(look, -1), tt == charge ? 1 : 0, 0.25, JobFx.hide1p(sp).with("alpha", 0.85, "vel", RuneKit.bodyVel(sp)));
                    SkillFx.play(level, "crossbow.loading.middle", sp.position(), 0.7 + q * 0.6, 0.6);
                }
                if (tt == charge) {
                    JobFx.glint(level, V.add(hand, look, 2.2), 0.9, j, 0.35, Opt.of("vel", RuneKit.bodyVel(sp)));
                    JobFx.aura(sp, j, 2.4, 0.4, 0.9);
                    SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.8, 0.9);
                }
            });
        }
        JobRecast.open(sp, k, 1, charge, (int) k.num("gap", 6), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobKit.motion(sp, k.id() + ":release");
            loose(sp, j, k, P, charge, charge);
        });
        JobSystem.note(sp, "§a다시 누르면 놓음 §7(끝까지 당기면 " + charge / 20.0 + "초 · " + (int) k.num("rangeMax", 40) + "칸)");
        return true;
    }

    /** 놓음 (held 틱 당김): 큰 화살 · 흰 섬광 줄 · 연두 빛 줄 · 지나가는 바람 고리 → 처음 맞는 적에게 큰 한 발 (깨지는 약점 과녁 · 큰 타격) */
    private static void loose(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, int held, int charge) {
        Draw dr = DRAWS.remove(sp.getUUID());
        if (dr != null) dr.done()[0] = true;
        sp.removeEffect(MobEffects.SLOWNESS);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        double c = Math.max(0, Math.min(1, held / (double) charge));
        boolean full = held >= charge;
        double min = k.num("minPower", 0.5);
        double frac = min + (1 - min) * c;
        double range = k.num("rangeMin", 16) + (k.num("rangeMax", 40) - k.num("rangeMin", 16)) * c;
        int breath = JobTraitsMore.breath(sp);
        double boost = 1 + k.num("breathPer", 0.06) * breath;
        JobTraitsMore.useBreath(sp, (int) k.num("breathUse", 3));
        double[] glow = JobKit.glow(j);
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1F);
        double len = Math.max(2, RuneKit.rayLength(level, eye, look, range));
        Kit.LineHit first = firstOnLine(sp, eye, look, len, k.num("width", 1.2) / 2);
        double reach = first == null ? len : Math.max(1, first.t());
        Vec3 from = V.add(RuneKit.bowHand(sp), look, 0.7);
        Vec3 end = V.add(eye, look, reach);
        // 보이는 빠르기: 가까운 적이면 화살이 2틱은 보이게 (1틱이면 영상 · 화면에서 날아가는 것이 보이지 않았음 — 영상 1), 맞는 틱도 그 빠르기
        double speed = visSpeed(k.num("speed", 80), reach);
        JobFx.greatArrow(level, from, end, speed, 0.85 + 0.55 * c, Opt.empty(), glow, FLASH);
        RuneKit.streak(level, V.add(from, look, 0.6), end, FLASH, 0.1 + 0.08 * c, 0.4, 1.0, 0);
        RuneKit.streak(level, V.add(from, look, 0.6), end, glow, 0.26 + 0.16 * c, 0.35, 0.6, 0);
        JobFx.bow(level, bowAt(sp, 0.55), 0.6, j, 4, 2, 0.25, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
        JobFx.burst(level, from, 0.55 + 0.35 * c, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.wind(level, V.up(sp.position(), 0.06), 1.0 + c, j, 0.35, Opt.of("alpha", 0.85));
        for (double dd : new double[] {2.5, 6, 11}) {
            if (dd > reach) break;
            Vec3 at = V.add(eye, look, dd);
            int delay = (int) Math.round(dd / speed * 20);
            double rr = 0.5 + dd * 0.03 + c * 0.3;
            RuneKit.later(delay, () -> JobFx.ringFacing(level, at, rr, j, V.scale(look, -1), 0.3, Opt.of("alpha", 0.85)));
        }
        SkillFx.play(level, "item.crossbow.shoot", from, 0.8 + 0.2 * c, 1.0);
        SkillFx.play(level, "item.trident.throw", from, 1.3, 0.7);
        if (full) SkillFx.play(level, "item.trident.thunder", from, 1.8, 0.35);
        if (first == null) return;
        LivingEntity e = first.e();
        int hitT = Math.max(1, (int) Math.round(first.t() / speed * 20));
        JobKit.strike(sp, e, j, P, k.total() * frac * boost, false, full, life, hitT, x -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            Vec3 at = Kit.bodyPoint(e);
            stuck(lv, e, look, 0.75 + 0.45 * c, 0.5, glow, FLASH);
            JobFx.weak(lv, at, 0.8 + 0.4 * c + Kit.bodyPad(e) * 0.3, 4, 2, 0.3, false, Opt.of("vel", RuneKit.entVel(e)), glow, FLASH);
            JobImpact.hit(sp, e, j, 1.1 + 0.6 * c);
            JobFx.burst(lv, at, 0.7 + 0.5 * c, j, 0.3, Opt.empty());
            SkillFx.sparks(lv, at, FLASH, 8 + (int) (8 * c), 4);
        });
    }

    // ------------------------------------------------------------ 스네어 샷

    /**
     * 스네어 샷: shotAt 틱 겨눈 적 (range + 호흡 칸 — 없으면 조준한 곳) 으로 그물 화살 (활 평타와 같은 피해 × arrow — RpgCombat.extraArrow · 치명타 따로): 날아가며 그물이
     * 펼쳐지고 닿은 자리에서 감김 — 맞은 적과 둘레 net 칸 slow 틱 둔화 (감긴 그물). 같은 틱 반동으로 뒤로 back 칸 (lift 칸 높이 · rise 틱) 물러나 내려앉으면 호흡 + breath.
     * 묶였으면 쓰지 않음
     */
    static boolean snare(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        double range = k.num("range", 20) + JobTraitsMore.breath(sp);
        LivingEntity target = Kit.pickTarget(sp, range, 12);
        String life = RuneKit.lifeToken(sp);
        int shotAt = (int) k.num("shotAt", 2);
        int rise = Math.max(4, (int) k.num("rise", 10));
        double netR = k.num("net", 2.0);
        int slow = (int) k.num("slow", 40);
        Vec3 start = sp.position();
        JobFx.bow(level0, bowAt(sp, 0.5), 0.5, j, 1, 3, shotAt / 20.0 + 0.05, Opt.of("vel", RuneKit.bodyVel(sp)));
        RuneKit.later(shotAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 from = bowAt(sp, 0.6);
            boolean on = alive(sp, life, target) && target.level() == level;
            Vec3 to = on ? Kit.bodyPoint(target) : Kit.aimPoint(sp, range);
            Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
            int t = on && w != null && "bow".equals(w.type()) ? RpgCombat.extraArrow(sp, target, w, JobUpgrades.num(sp, k, "arrow", 1)) : -1;
            double dist = V.len(V.sub(to, from));
            int fly = t > 0 ? t : Math.max(2, (int) Math.round(dist / 40 * 20));
            // 그물: 화살 뒤에서 펼쳐지며 날아감 (빛의 화살이 끌고 감) → 닿은 자리에서 감김
            JobFx.netFly(level, from, to, fly, 0.9 + netR * 0.35, j, Opt.empty());
            JobFx.arrow(level, from, to, j, dist / (fly / 20.0), 0.7, Opt.empty());
            JobFx.burst(level, from, 0.45, j, 0.2, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "item.crossbow.shoot", from, 1.3, 0.9);
            SkillFx.play(level, "entity.fishing_bobber.throw", from, 0.8, 0.8);
            RuneKit.later(fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 at = on && !target.isRemoved() ? Kit.bodyPoint(target) : to;
                JobFx.burst(level, at, 0.6, j, 0.25, Opt.empty());
                SkillFx.play(level, "block.cobweb.place", at, 0.8, 1.0);
                SkillFx.play(level, "entity.fishing_bobber.retrieve", at, 0.7, 0.8);
                List<LivingEntity> caught = Kit.around(sp, at, netR, 3);
                if (on && !target.isRemoved() && !caught.contains(target)) caught.add(target);
                if (caught.isEmpty()) JobFx.netWrap(level, at, 1.3, slow / 20.0, j, Opt.empty());
                for (LivingEntity e : caught) {
                    RuneKit.effect(e, "slowness", slow, JobKit.isBoss(e) ? 1 : 2);
                    // 감긴 그물: 몸 가운데보다 쏜 쪽으로 조금 앞 (몸에 반쯤 가려 작게 보였음 — 영상 1)
                    Vec3 b = Kit.bodyPoint(e);
                    Vec3 toward = V.flat(V.sub(sp.position(), e.position()));
                    JobFx.netWrap(level, V.add(b, toward, 0.3 + Kit.bodyPad(e)), 0.95 + Kit.bodyPad(e) * 0.5, slow / 20.0, j, Opt.of("vel", RuneKit.entVel(e)));
                }
            });
        });
        // 반동: 뒤로 물러남 (낮은 도약) → 내려앉으면 호흡 + breath
        JobMove.run(sp, JobMove.hop(shotAt, shotAt + rise, JobMove.ahead(sp, -k.num("back", 6), 0), k.num("lift", 1.2)));
        Kit.noFall(sp, shotAt + rise + 20);
        RuneKit.later(shotAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.wind(level0, V.up(start, 0.08), 1.6, j, 0.45, Opt.empty());
            JobFx.feathers(level0, V.up(start, 1.1), 5, 0.7, 0.18, j, 0.9, JobFx.hide1p(sp));
            SkillFx.play(level0, "entity.breeze.jump", start, 1.2, 0.8);
        });
        RuneKit.later(shotAt + rise, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            JobTraitsMore.addBreath(sp, (int) k.num("breath", 3));
            JobFx.wind(level, V.up(sp.position(), 0.08), 1.3, j, 0.4, Opt.of("alpha", 0.9));
            JobFx.target(level, V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), 0.6), 0.32, j, V.scale(sp.getViewVector(1F), -1), 0, 0.3,
                    Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.6, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 트릭 샷

    /**
     * 트릭 샷: shotAt 틱 겨눈 적 (range + 호흡 칸 — 없으면 쓰지 않음) 에게 화살 (first) → 맞은 적에서 튀어 올라 bounces 번 튐 (bounce — 치명 숫자): 둘레 bounceRange 칸에서 아직
     * 안 맞은 적 (쏜 쪽에서 뒤쪽 먼저 · 가까운 차례 — bounceGap 틱), 없으면 같은 적에게 다시 내리꽂힘 (sameGap 틱 — 몹 무적 시간). 튀는 화살은 금빛 · 높이 휘어 날아감
     */
    static boolean trick(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 24) + JobTraitsMore.breath(sp), 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        int shotAt = (int) k.num("shotAt", 3);
        ServerLevel level0 = sp.level();
        JobFx.bow(level0, bowAt(sp, 0.5), 0.5, j, 1, 3, shotAt / 20.0 + 0.05, Opt.of("vel", RuneKit.bodyVel(sp)));
        SkillFx.play(level0, "crossbow.loading.end", sp.position(), 1.4, 0.7);
        RuneKit.later(shotAt, () -> {
            if (!alive(sp, life, target) || target.level() != sp.level()) return;
            ServerLevel level = sp.level();
            Vec3 from = bowAt(sp, 0.6);
            Vec3 to = Kit.bodyPoint(target);
            int t = JobFx.arrow(level, from, to, j, visSpeed(k.num("speed", 60), V.len(V.sub(to, from))), 0.9, Opt.empty());
            RuneKit.streak(level, from, to, JobKit.glow(j), 0.14, 0.3, 0.75, 0);
            JobFx.burst(level, from, 0.4, j, 0.2, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "random.bow", from, 1.2, 0.9);
            Map<UUID, Long> hitAt = new HashMap<>();
            hitAt.put(target.getUUID(), JobKit.now() + t);
            JobKit.strike(sp, target, j, P, k.num("first", 3.5), false, false, life, t, d -> {
                JobFx.hit(target, j, 1.0);
                if (target.level() instanceof ServerLevel lv) JobFx.weak(lv, Kit.bodyPoint(target), 0.7, 4, 2, 0.25, false, Opt.of("vel", RuneKit.entVel(target)), JobKit.glow(j),
                        FLASH);
            });
            Vec3 dir = V.flat(V.sub(target.position(), sp.position()));
            RuneKit.later(t, () -> bounce(sp, j, k, P, life, target, dir, Math.max(0, (int) k.num("bounces", 2)), hitAt));
        });
        return true;
    }

    /** 튐 하나: from 에서 다음 적으로 (아니면 같은 적에게 다시) — 금빛 화살이 위로 휘어 날아가 꽂힘 (치명 숫자) → 남은 튐 */
    private static void bounce(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life, LivingEntity from, Vec3 dir, int left, Map<UUID, Long> hitAt) {
        if (left <= 0 || !RuneKit.sameLife(sp, life) || from.isRemoved() || !(from.level() instanceof ServerLevel level)) return;
        double range = k.num("bounceRange", 7);
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, from.position(), range, 4)) {
            if (e == from || hitAt.containsKey(e.getUUID()) || !Kit.splashFoe(sp, e)) continue;
            Vec3 to = V.flat(V.sub(e.position(), from.position()));
            double score = e.distanceToSqr(from) - (V.dot(V.unit(to), dir) > 0.1 ? 12 : 0);
            if (score < best) {
                best = score;
                next = e;
            }
        }
        boolean same = next == null;
        if (same) {
            if (!from.isAlive()) return;
            next = from;
        }
        LivingEntity n = next;
        long now = JobKit.now();
        int delay = same ? (int) Math.max(k.num("bounceGap", 3), hitAt.getOrDefault(n.getUUID(), now) + (long) k.num("sameGap", 10) - now) : (int) k.num("bounceGap", 3);
        hitAt.put(n.getUUID(), now + delay);
        Vec3 a = Kit.bodyPoint(from);
        Vec3 b = Kit.bodyPoint(n);
        // 금빛 화살이 튀어 오름 → 휘어 내리꽂힘 (같은 적이면 높이 솟았다가 바로 위에서)
        Vec3 apex = same ? V.up(a, 3.6 + Kit.bodyPad(from)) : V.up(V.lerp(a, b, 0.5), 2.2 + V.len(V.sub(b, a)) * 0.2);
        double[] glow = JobKit.glow(j);
        int up = Math.max(1, delay / 2);
        int down = Math.max(1, delay - up);
        JobFx.greatArrow(level, a, apex, V.len(V.sub(apex, a)) / (up / 20.0), 1.0, Opt.empty(), glow, GOLD);
        RuneKit.streak(level, a, apex, GOLD, 0.16, 0.35, 0.9, 0);
        RuneKit.later(up, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 bb = n.isRemoved() ? b : Kit.bodyPoint(n);
            JobFx.greatArrow(level, apex, bb, V.len(V.sub(bb, apex)) / (down / 20.0), 1.1, Opt.empty(), glow, GOLD);
            RuneKit.streak(level, apex, bb, GOLD, 0.2, 0.4, 1.0, 0);
            JobFx.glint(level, apex, 0.8, j, 0.25, Opt.empty());
        });
        SkillFx.sparks(level, a, GOLD, 10, 5);
        JobFx.glint(level, a, 0.8, j, 0.25, Opt.empty());
        SkillFx.play(level, "item.trident.return", a, 1.6, 0.7);
        SkillFx.play(level, "block.anvil.land", a, 2.0, 0.25);
        JobKit.strikeCrit(sp, n, j, P, k.num("bounce", 5), false, false, life, delay, d -> {
            if (!(n.level() instanceof ServerLevel lv)) return;
            Vec3 at = Kit.bodyPoint(n);
            JobImpact.hit(sp, n, j, 1.15);
            JobFx.weak(lv, at, 0.85 + Kit.bodyPad(n) * 0.3, 4, 2, 0.3, false, Opt.of("vel", RuneKit.entVel(n)), glow, GOLD);
            SkillFx.sparks(lv, at, GOLD, 12, 5);
        });
        Vec3 nd = same ? dir : V.flat(V.sub(n.position(), from.position()));
        RuneKit.later(delay, () -> bounce(sp, j, k, P, life, n, nd, left - 1, hitAt));
    }

    // ------------------------------------------------------------ 헌터스 마크

    /**
     * 헌터스 마크: dur 틱 태세 (강화 — 지속) — 활 평타가 같은 적을 every 번째 맞힐 때마다 은빛 화살 (그 적 최대 체력 × pct — 위력 × min ~ cap, JobTraits9). 그림: 화살통에서
     * 은빛 화살을 뽑아 듦 (손 앞 은빛 큰 화살) · 은빛 기운 · 발밑 고리 · 은빛 반짝임
     */
    static boolean mark(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        JobTraits9.markStart(sp);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 look = sp.getViewVector(1F);
        JobFx.aura(sp, j, 2.4, 0.9, 0.9);
        JobFx.halo(level, V.up(c, 0.06), 2.0, j, 0.45, Opt.empty()); // 빛 고리 (칼질 고리 JobFx.ring 은 발밑의 초승달 베기로 보였음 — 영상 2)
        JobFx.greatArrowAt(level, V.up(V.add(c, V.flat(look), 0.5), 3.0), V.UP, 1.0, 0.5, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), JobTraits9.STEEL,
                JobTraits9.SILVER);
        JobFx.weak(level, V.add(sp.getEyePosition(), look, 2.6), 0.6, 0, 3, 0.5, false, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)), JobTraits9.STEEL, JobTraits9.SILVER);
        SkillFx.sparks(level, V.up(c, 1.4), JobTraits9.SILVER, 12, 4);
        SkillFx.play(level, "item.armor.equip_chain", c, 1.4, 0.9);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.6, 0.7);
        return true;
    }

    // ------------------------------------------------------------ 데드아이

    /** 저격 자세 (끝 틱 · 쏜 수 · 목숨) */
    private static final class Snipe {
        final long deadline;
        final String life;
        int shot;

        Snipe(long deadline, String life) {
            this.deadline = deadline;
            this.life = life;
        }
    }

    private static final Map<UUID, Snipe> SNIPES = new HashMap<>();

    /**
     * 데드아이: 무릎 꿇고 저격 자세 (어두운 하늘 rpg:ult_deadeye · window 틱 — 앞 range 칸 조준 과녁이 따라다님) → 다시 누를 때마다 (gap 틱 뒤부터) 거대한 화살 한 발 —
     * 일직선 (폭 width) 에서 처음 맞는 적에게 shot1 · shot2 → 셋째 shot3 (치명 · 큰 숫자 · 큰 타격), 그 적이 잃은 체력만큼 최대 + missing. window 틱이 지나면 남은 화살을 10틱마다
     * 저절로 쏨
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int stage = JobRecast.stage(sp, k.id());
        int gap = Math.max(10, (int) k.num("gap", 10));
        if (stage >= 1) {
            Snipe s = SNIPES.get(sp.getUUID());
            if (s == null || !RuneKit.sameLife(sp, s.life)) {
                JobRecast.close(sp, k.id());
                return true;
            }
            JobRecast.motion(sp, k.id() + ":shot");
            fire(sp, j, k, P, s);
            if (s.shot >= 3) {
                JobRecast.close(sp, k.id());
                SNIPES.remove(sp.getUUID());
            } else JobRecast.open(sp, k, stage + 1, (int) Math.max(gap + 1, s.deadline - JobKit.now()), gap, () -> autoFire(sp, j, k, P));
            return true;
        }
        ServerLevel level0 = sp.level();
        int window = Math.max(20, (int) k.num("window", 60));
        String life = RuneKit.lifeToken(sp);
        Snipe s = new Snipe(JobKit.now() + window, life);
        SNIPES.put(sp.getUUID(), s);
        Ult.sky(level0, sp.position(), "rpg:ult_deadeye", window + 50);
        RpgCombat.setSteadfast(sp, window + 30);
        JobFx.aura(sp, j, 2.6, 0.8, 0.9);
        JobFx.wind(level0, V.up(sp.position(), 0.06), 2.0, j, 0.5, Opt.empty());
        SkillFx.play(level0, "crossbow.loading.end", sp.position(), 0.6, 1.0);
        SkillFx.play(level0, "beacon.power", sp.position(), 1.6, 0.8);
        double[] glow = JobKit.glow(j);
        // 자세 동안 (2틱마다): 보는 쪽에서 처음 맞는 적 (없으면 range 칸) 에 큰 조준 과녁 · 1인칭 눈앞 조준 · 활 앞 흰 반짝임
        for (int t = 2; t <= window + 30; t += 2) {
            RuneKit.later(t, () -> {
                Snipe cur = SNIPES.get(sp.getUUID());
                if (cur != s || !RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 eye = sp.getEyePosition();
                Vec3 look = sp.getViewVector(1F);
                double len = Math.max(2, RuneKit.rayLength(level, eye, look, k.num("range", 48)));
                Kit.LineHit h = firstOnLine(sp, eye, look, len, k.num("width", 1.6) / 2);
                Vec3 at = h == null ? V.add(eye, look, Math.min(len, 18)) : Kit.bodyPoint(h.e());
                double size = h == null ? 0.9 : 1.05 + Kit.bodyPad(h.e()) * 0.5;
                JobFx.weak(level, at, size, 1, 3, 0.14, true, Opt.of("fade", 0.0, "alpha", 0.95), glow, FLASH);
                JobFx.weak(level, V.add(eye, look, 3.2), 0.9, 1, 3, 0.14, true, JobFx.only1p(sp).with("fade", 0.0, "alpha", 0.55), glow, FLASH);
                RuneKit.rfx(level, "prop", bowAt(sp, 0.5), JobFx.hide1p(sp).with("size", 0.16, "life", 0.2, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 0.0,
                        "spinRate", 200.0, "color", FLASH, "fade", 0.4, "vel", RuneKit.bodyVel(sp)));
            });
        }
        JobRecast.open(sp, k, 1, window, gap, () -> autoFire(sp, j, k, P));
        JobSystem.note(sp, "§a다시 누를 때마다 쏨 §7(" + window / 20 + "초 안에 세 발 — 지나면 저절로)");
        return true;
    }

    /** 창이 닫힘 (다시 누르지 않음): 남은 화살을 10틱마다 저절로 */
    private static void autoFire(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        Snipe s = SNIPES.get(sp.getUUID());
        if (s == null || !RuneKit.sameLife(sp, s.life)) return;
        int left = 3 - s.shot;
        for (int i = 0; i < left; i++) {
            RuneKit.later(i * 10, () -> {
                if (SNIPES.get(sp.getUUID()) != s || !RuneKit.sameLife(sp, s.life) || s.shot >= 3) return;
                JobKit.motion(sp, k.id() + ":shot");
                fire(sp, j, k, P, s);
                if (s.shot >= 3) SNIPES.remove(sp.getUUID());
            });
        }
    }

    /** 한 발: 거대한 화살 (셋째는 더 크고 금빛) · 굵은 빛줄기 · 지나가는 충격 고리 · 반동 → 처음 맞는 적 (잃은 체력만큼 +) */
    private static void fire(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Snipe s) {
        int i = s.shot++;
        boolean last = i == 2;
        double mult = k.num(i == 0 ? "shot1" : i == 1 ? "shot2" : "shot3", i == 0 ? 14 : i == 1 ? 18 : 28);
        ServerLevel level = sp.level();
        String life = s.life;
        double[] glow = JobKit.glow(j);
        double[] core = last ? GOLD : FLASH;
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1F);
        double len = Math.max(2, RuneKit.rayLength(level, eye, look, k.num("range", 48)));
        Kit.LineHit h = firstOnLine(sp, eye, look, len, k.num("width", 1.6) / 2);
        double reach = h == null ? len : Math.max(1, h.t());
        Vec3 from = V.add(RuneKit.bowHand(sp), look, 0.9);
        Vec3 end = V.add(eye, look, reach);
        double speed = visSpeed(k.num("speed", 100), reach);
        double size = last ? 2.4 : 1.4 + i * 0.3;
        JobFx.greatArrow(level, from, end, speed, size, Opt.empty(), glow, core);
        JobFx.beam(level, V.add(from, look, 0.8), end, glow, core, last ? 3.4 : 2.4, reach / speed + 0.45, Opt.of("alpha", 0.85));
        RuneKit.streak(level, V.add(from, look, 0.6), end, core, last ? 0.26 : 0.16, 0.45, 1.0, 0);
        JobFx.burst(level, from, last ? 1.2 : 0.8, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.bow(level, bowAt(sp, 0.6), 0.7, j, 4, 2, 0.3, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
        for (double dd : new double[] {3, 7, 12, 18, 26}) {
            if (dd > reach) break;
            Vec3 at = V.add(eye, look, dd);
            int delay = (int) Math.round(dd / speed * 20);
            double rr = (last ? 1.1 : 0.7) + dd * 0.035;
            RuneKit.later(delay, () -> JobFx.ringFacing(level, at, rr, j, V.scale(look, -1), 0.4, Opt.of("alpha", 0.9)));
        }
        JobFx.wind(level, V.up(sp.position(), 0.06), last ? 2.2 : 1.4, j, 0.4, Opt.of("alpha", 0.9));
        SkillFx.play(level, "item.crossbow.shoot", from, last ? 0.5 : 0.7 + i * 0.1, 1.0);
        SkillFx.play(level, "item.trident.thunder", from, last ? 1.2 : 1.8, last ? 0.8 : 0.4);
        if (last) {
            Ult.flash(level, from, core, Opt.empty(), sp);
            SkillFx.play(level, "firework.large_blast", from, 0.9, 0.9);
            JobMove.run(sp, JobMove.to(0, 5, JobMove.ahead(sp, -1.2, 0), JobMove.OUT));
        }
        if (h == null) return;
        LivingEntity e = h.e();
        double max = e.getMaxHealth();
        double missing = max > 0 ? Math.max(0, Math.min(1, 1 - e.getHealth() / max)) : 0;
        double m = mult * (1 + k.num("missing", 0.5) * missing);
        int hitT = Math.max(1, (int) Math.round(h.t() / speed * 20));
        java.util.function.DoubleConsumer then = d -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            Vec3 at = Kit.bodyPoint(e);
            stuck(lv, e, look, last ? 1.5 : 1.0 + i * 0.15, last ? 0.8 : 0.6, glow, core);
            // 뒤로 뚫고 나가는 빛 (몸 너머 2.5칸 — 가까운 적이면 빛줄기가 짧아 맞은 것만 보였음)
            Vec3 back = V.add(at, look, 0.4 + Kit.bodyPad(e));
            RuneKit.streak(lv, back, V.add(back, look, last ? 3.2 : 2.4), core, last ? 0.22 : 0.14, 0.35, 1.0, 0);
            SkillFx.fx(lv, "rpg:arrow_spark", back, Opt.of("dir", look, "speed", 6.0, "size", 0.2, "grow", 0.6, "life", 0.3, "color", core, "alpha", 1.0));
            JobImpact.hit(sp, e, j, last ? 2.0 : 1.4);
            JobFx.weak(lv, at, (last ? 1.0 : 0.75) + Kit.bodyPad(e) * 0.4, 4, 2, 0.35, false, Opt.of("vel", RuneKit.entVel(e)), glow, core);
            JobFx.burst(lv, at, last ? 1.6 : 1.0, j, 0.35, Opt.empty());
            if (last) {
                JobFx.halo(lv, V.up(e.position(), 0.08), 2.4 + Kit.bodyPad(e), j, 0.5, Opt.empty());
                SkillFx.sparks(lv, at, GOLD, 24, 7);
            } else SkillFx.sparks(lv, at, FLASH, 10, 4);
        };
        if (last) JobKit.strikeCrit(sp, e, j, P, m, false, true, life, hitT, then);
        else JobKit.strike(sp, e, j, P, m, false, false, life, hitT, then);
    }

    /** 시험: 데드아이 저격 자세가 남았는지 · 쏜 수 (없으면 −1) */
    static int deadeyeShots(ServerPlayer sp) {
        Snipe s = SNIPES.get(sp.getUUID());
        return s == null ? -1 : s.shot;
    }
}
