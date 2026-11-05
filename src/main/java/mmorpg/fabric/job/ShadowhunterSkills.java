package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 궁수 반전 섀도 헌터 스킬 (jobs.json advance.shadowhunter — 남보라 · 은빛 · 검은 깃 화살, 특성 「추격」 · 저주 — JobTraits9):
 * 1차 궁수와 거꾸로 달리며 쏨 — 검은 화살 (달리며 세 발 · 저주) · 그림자 구르기 (옆으로 구르며 한 발 — 구르는 동안 받는 피해 없음) · 저주 번짐 (꿰뚫는 저주 화살 → 문양이 터지며
 * 둘레로 번짐 · 쓰러지면 바로) · 그믐밤 (6초 — 어둠 · 치명타 · 적이 나를 놓침) · 어둠숲의 사냥 (어둠숲 하늘 · 가시덤불 · 저주 걸린 적마다 검은 화살 → 사냥의 화살). 화살은
 * 공격 피해. 연출 = 2D 그림 (검은 화살 rfx_jblackarrow · 저주 문양 rfx_jcurse · 어둠숲 가시덤불 rfx_jthicket · 그림자 연기 · 달 rfx_jmoon).
 */
final class ShadowhunterSkills {
    private ShadowhunterSkills() {}

    private static final double[] DUSK = JobTraits9.DUSK;
    private static final double[] SILVER = JobTraits9.SILVER;
    /** 그림자 잔상 (검은 남색) */
    private static final double[] SHADE = {0.08, 0.07, 0.2};
    /** 그믐달의 빛 (옅은 은청 — 먹빛이면 달이 하늘에 묻힘) */
    private static final double[] MOON_GLOW = {0.5, 0.55, 0.85};

    static void register() {
        JobSystem.register("shadowhunter_black", ShadowhunterSkills::black);
        JobSystem.register("shadowhunter_roll", ShadowhunterSkills::roll);
        JobSystem.register("shadowhunter_spread", ShadowhunterSkills::spread);
        JobSystem.register("shadowhunter_moonless", ShadowhunterSkills::moonless);
        JobSystem.register("shadowhunter_ult", ShadowhunterSkills::ult);
    }

    /** 옆으로 가는 첫 쪽 (사람마다 번갈아) */
    private static final Map<UUID, Boolean> SIDE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 달리는 · 구르는 쪽: 움직이는 쪽 (옆 성분이 있으면 — onlySide 면 옆만), 서 있으면 옆 (쓸 때마다 번갈아) */
    private static Vec3 runDir(ServerPlayer sp, boolean onlySide) {
        Vec3 look = Kit.facingOf(sp);
        Vec3 right = V.rightOf(look);
        Vec3 m = sp.getKnownMovement();
        Vec3 mh = new Vec3(m.x, 0, m.z);
        if (V.len(mh) > 0.04) {
            Vec3 u = V.unit(mh);
            if (!onlySide) return u;
            double s = V.dot(u, right);
            if (Math.abs(s) > 0.3) return s > 0 ? right : V.scale(right, -1);
        }
        boolean r = SIDE.merge(sp.getUUID(), true, (a, b) -> !a);
        return r ? right : V.scale(right, -1);
    }

    // ------------------------------------------------------------ 검은 화살

    /**
     * 검은 화살: 겨눈 적 (range 칸 · 없으면 쓰지 않음) — 움직이는 쪽 (서 있으면 옆 — 번갈아) 으로 dash 칸 달리며 every 틱마다 검은 화살 arrows 발 (arrow × 발 수 — 첫 화살이 닿을
     * 때 한 번에 · 숫자 여러 줄) · 맞은 적 저주 + curse · 달리며 쏘아 추격 +1
     */
    static boolean black(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 24), 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        int n = Math.max(1, (int) k.num("arrows", 3));
        int every = Math.max(1, (int) k.num("every", 4));
        double speed = k.num("speed", 55);
        String life = RuneKit.lifeToken(sp);
        Vec3 dir = runDir(sp, false);
        double dash = k.num("dash", 3);
        if (!Status.isRooted(sp)) {
            JobMove.run(sp, JobMove.to(0, n * every + 2, from -> V.add(from, dir, RuneKit.dashDistance(sp, dir, dash, 0.3, 1.5)), JobMove.OUT));
        }
        JobTraits9.addPursuit(sp, j, 1);
        ServerLevel level0 = sp.level();
        RuneKit.shadeClone(sp, level0, sp.position(), Kit.facingOf(sp), 1, SHADE, JobKit.glow(j), Opt.of("life", 0.35, "fade", 0.6, "alpha", 0.75));
        JobFx.smoke(level0, V.up(sp.position(), 0.4), 0.9, DUSK, SILVER, 0.5, Opt.of("alpha", 0.6));
        for (int i = 0; i < n; i++) {
            int ii = i;
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) return;
                ServerLevel level = sp.level();
                Vec3 from = RuneKit.bowHand(sp);
                Vec3 to = V.up(Kit.bodyPoint(target), ((ii % 3) - 1) * 0.22);
                JobFx.bow(level, V.add(from, sp.getViewVector(1F), 0.5), 0.42, j, 2, 4, every / 20.0 + 0.06, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
                int t = JobFx.blackArrow(level, from, to, speed, 0.82, Opt.empty(), DUSK, SILVER);
                // 0.51.0 영상 1 뒤: 화살 길에 그림자 연기 줄 · 가는 은빛 줄 (빠른 검은 화살이 1 ~ 2틱만 보였음)
                RuneKit.streak(level, V.add(from, V.unit(V.sub(to, from)), 0.6), to, SHADE, 0.2, 0.4, 0.8, 0);
                RuneKit.streak(level, V.add(from, V.unit(V.sub(to, from)), 0.6), to, SILVER, 0.04, 0.3, 0.8, 0);
                SkillFx.play(level, "random.bow", from, 1.0 + ii * 0.08, 0.7);
                if (ii == 0) {
                    JobKit.strikeSplit(sp, target, j, P, k.num("arrow", 2.5) * n, n, every, false, false, life, t, d -> JobTraits9.addCurse(sp, target, (int) k.num("curse", 1), j));
                }
                RuneKit.later(t, () -> {
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    JobFx.hit(target, j, 0.8);
                    JobFx.smoke(lv, Kit.bodyPoint(target), 0.5, DUSK, SILVER, 0.35, Opt.of("alpha", 0.7));
                    SkillFx.play(lv, "random.bowhit", Kit.bodyPoint(target), 0.9, 0.5);
                });
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 그림자 구르기

    /**
     * 그림자 구르기: 옆으로 (움직이는 쪽 — 서 있으면 번갈아) dist 칸을 steps 틱에 구름 (그동안 받는 피해 없음 — JobTraits9.rollFor · 적 사이를 통과) · shotAt 틱에 겨눈 적에게
     * 그림자 화살 한 발 (활 평타 피해 × arrow — RpgCombat.extraArrow · 치명타 따로) → 맞은 적 저주 +1 (활 평타 훅) · 추격 +1. 묶였거나 옆이 막혀 못 가면 쓰지 않음
     */
    static boolean roll(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 dir = runDir(sp, true);
        double free = RuneKit.dashDistance(sp, dir, k.num("dist", 4), 0.3, 1.5);
        if (free < 0.8) {
            Vec3 other = V.scale(dir, -1);
            double alt = RuneKit.dashDistance(sp, other, k.num("dist", 4), 0.3, 1.5);
            if (alt < 0.8) {
                Notify.notify(sp, "§c" + k.name() + " §7— 옆이 막혔습니다", 30);
                return false;
            }
            dir = other;
            free = alt;
        }
        ServerLevel level0 = sp.level();
        Vec3 start = sp.position();
        Vec3 look = Kit.facingOf(sp);
        LivingEntity target = Kit.pickTarget(sp, 24, 12);
        String life = RuneKit.lifeToken(sp);
        int steps = Math.max(2, (int) k.num("steps", 8));
        Vec3 d0 = dir;
        double go = free;
        JobMove.run(sp, JobMove.to(0, steps, from -> V.add(from, d0, go), JobMove.INOUT));
        // 동작: 기본은 오른쪽으로 구름 — 왼쪽이면 단계 열쇠 left 를 1틱에 다시 보냄 (스킬 동작은 cast 가 끝난 뒤에 나감)
        if (V.dot(d0, V.rightOf(look)) < 0) JobKit.motionLater(sp, "shadowhunter_roll:left", 1, life);
        JobTraits9.rollFor(sp, (int) k.num("invuln", steps));
        JobTraits9.addPursuit(sp, j, 1);
        // 떠난 자리 그림자 잔상 · 구르는 길을 따라 검은 연기 · 은빛 줄
        RuneKit.shadeClone(sp, level0, start, look, 1, SHADE, SILVER, Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        for (int t = 0; t <= steps; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                JobFx.smoke(level, V.up(sp.position(), 0.5), 0.75 + tt * 0.03, DUSK, SILVER, 0.45, JobFx.hide1p(sp).with("alpha", 0.65));
                if (tt == 0 || tt == steps) SkillFx.sparks(level, V.up(sp.position(), 0.6), SILVER, 4, 3);
            });
        }
        RuneKit.later(steps, () -> {
            if (RuneKit.sameLife(sp, life)) RuneKit.streak(sp.level(), V.up(start, 0.7), V.up(sp.position(), 0.7), SILVER, 0.07, 0.3, 0.8, 0);
        });
        SkillFx.play(level0, "entity.player.attack.sweep", start, 0.7, 0.5);
        SkillFx.play(level0, "entity.phantom.flap", start, 0.8, 0.7);
        RuneKit.later((int) k.num("shotAt", 4), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 from = RuneKit.bowHand(sp);
            boolean on = target != null && !target.isRemoved() && target.isAlive() && target.level() == level;
            Vec3 to = on ? Kit.bodyPoint(target) : Kit.aimPoint(sp, 24);
            JobFx.blackArrow(level, from, to, 60, 0.85, Opt.empty(), DUSK, SILVER);
            RuneKit.streak(level, V.add(from, V.unit(V.sub(to, from)), 0.6), to, SHADE, 0.2, 0.4, 0.8, 0);
            RuneKit.streak(level, V.add(from, V.unit(V.sub(to, from)), 0.6), to, SILVER, 0.04, 0.3, 0.8, 0);
            SkillFx.play(level, "random.bow", from, 0.9, 0.8);
            Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
            if (!on || w == null || !"bow".equals(w.type())) return;
            // 맞으면 저주 +1 은 활 평타 훅이 넣음 (JobTraits9.onWeaponHit — 덧쏘기도 활 평타 명중)
            RpgCombat.extraArrow(sp, target, w, JobUpgrades.num(sp, k, "arrow", 1.5));
        });
        return true;
    }

    // ------------------------------------------------------------ 저주 번짐

    /** 저주 번짐 한 번 (문양이 터질 때 · 쓰러져 바로 번질 때 같이 씀): 같은 적에게 번진 피해는 한 번 */
    static final class Spread {
        final ServerPlayer sp;
        final Jobs.Job j;
        final Jobs.Skill k;
        final double P;
        final String life;
        final Set<UUID> marked = new HashSet<>();
        final Set<UUID> splashed = new HashSet<>();

        Spread(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
            this.sp = sp;
            this.j = j;
            this.k = k;
            this.P = P;
            this.life = life;
        }
    }

    /** 저주 문양이 박힌 적 → 그 번짐 */
    private static final Map<UUID, Spread> MARKED = new HashMap<>();

    /** 시험: 이 적에 저주 번짐 문양이 있는지 */
    public static boolean spreadMarked(LivingEntity e) {
        return e != null && MARKED.containsKey(e.getUUID());
    }

    /**
     * 저주 번짐: shotAt 틱 시위를 당겨 (그림자가 활로 모여 듦) 저주 화살 한 발 — 겨눈 적 쪽 (없으면 보는 쪽) range 칸 일직선 폭 width 를 꿰뚫음 (pierce — 화살이 닿는 틱에) ·
     * 맞은 적 저주 + curse · 검은 저주 문양 → burstAt 틱에 문양마다 터짐 (burst) · 둘레 radius 칸의 다른 적에게 저주가 번짐 (splash · 저주 + curse — 적마다 한 번). 문양이
     * 터지기 전에 쓰러지면 그 자리에서 바로 번짐 (JobTraits9 죽음)
     */
    static boolean spread(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int shotAt = (int) k.num("shotAt", 6);
        int burstAt = (int) k.num("burstAt", 36);
        double range = k.num("range", 24);
        double speed = k.num("speed", 40);
        double half = k.num("width", 1.6) / 2;
        String life = RuneKit.lifeToken(sp);
        LivingEntity aimed = Kit.pickTarget(sp, range, 8);
        ServerLevel level0 = sp.level();
        // 시위를 당김: 둘레의 그림자가 활로 모여 듦 · 활 앞 저주 문양
        for (int t = 0; t < shotAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 hand = RuneKit.bowHand(sp);
                for (int i = 0; i < 3; i++) {
                    double a = (tt * 0.9 + i * 2.1);
                    Vec3 p = V.add(hand, new Vec3(Math.cos(a), 0.4 + 0.3 * Math.sin(a * 1.3), Math.sin(a)), 1.4 - tt * 0.15);
                    SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(V.sub(hand, p)), "speed", 3.0, "size", 0.14, "grow", 0.5, "life", 0.25, "color", DUSK,
                            "alpha", 1.0));
                }
            });
        }
        JobFx.curse(level0, V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), 0.6), 0.4, 0, 2, shotAt / 20.0 + 0.1, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)),
                DUSK, SILVER);
        SkillFx.play(level0, "crossbow.loading.start", sp.position(), 0.6, 0.9);
        SkillFx.play(level0, "entity.wither.ambient", sp.position(), 1.6, 0.3);
        Spread c = new Spread(sp, j, k, P, life);
        RuneKit.later(shotAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 dir = aimed != null && !aimed.isRemoved() && aimed.isAlive() ? V.unit(V.sub(Kit.bodyPoint(aimed), eye)) : sp.getViewVector(1F);
            double len = Math.max(2, RuneKit.rayLength(level, eye, dir, range));
            Vec3 from = RuneKit.bowHand(sp);
            Vec3 end = V.add(eye, dir, len);
            JobFx.blackArrow(level, from, V.add(from, dir, len), speed, 1.3, Opt.empty(), DUSK, SILVER);
            RuneKit.streak(level, V.add(from, dir, 0.8), end, DUSK, 0.32, 0.5, 0.6, 0);
            RuneKit.streak(level, V.add(from, dir, 0.8), end, SILVER, 0.06, 0.35, 0.9, 0);
            JobFx.burst(level, from, 0.5, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "item.crossbow.shoot", from, 0.7, 1.0);
            SkillFx.play(level, "entity.wither.shoot", from, 1.4, 0.4);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, eye, dir, len, half)) {
                LivingEntity e = h.e();
                int hitT = Math.max(1, (int) Math.round(h.t() / speed * 20));
                c.marked.add(e.getUUID());
                MARKED.put(e.getUUID(), c);
                JobKit.strike(sp, e, j, P, k.num("pierce", 5.5), false, false, life, hitT, d -> {
                    JobTraits9.addCurse(sp, e, (int) k.num("curse", 1), j);
                    if (e.level() instanceof ServerLevel lv) {
                        int left = Math.max(4, burstAt - shotAt - hitT);
                        JobFx.curse(lv, Kit.bodyPoint(e), 0.8 + Kit.bodyPad(e) * 0.3, 0, 4, left / 20.0, false, Opt.of("vel", RuneKit.entVel(e)), DUSK, SILVER);
                        JobFx.hit(e, j, 1.0);
                    }
                });
            }
        });
        RuneKit.later(burstAt, () -> {
            for (UUID id : new java.util.ArrayList<>(c.marked)) {
                if (MARKED.get(id) != c) continue;
                MARKED.remove(id);
                if (!RuneKit.sameLife(sp, life) || !(sp.level().getEntity(id) instanceof LivingEntity e) || e.isRemoved() || !e.isAlive()) continue;
                burst(c, e.position(), e);
            }
        });
        return true;
    }

    /** 문양이 터짐 (center 가 null 이면 쓰러진 자리에서 번지기만): 검은 저주 터짐 · 은빛 불티 · 고리 → center 에 burst, 둘레 radius 칸의 다른 적에게 splash · 저주 */
    private static void burst(Spread c, Vec3 at, LivingEntity center) {
        ServerLevel level = c.sp.level();
        double r = c.k.num("radius", 3.5);
        Vec3 mid = center != null ? Kit.bodyPoint(center) : V.up(at, 1.0);
        JobFx.curse(level, mid, 1.0, 4, 2, 0.35, false, Opt.empty(), DUSK, SILVER);
        JobFx.smoke(level, mid, r * 0.6, DUSK, SILVER, 0.6, Opt.of("alpha", 0.8));
        JobFx.ring(level, V.up(at, 0.06), r, c.j, false, 0.4, Opt.empty());
        SkillFx.sparks(level, mid, SILVER, 10, 5);
        SkillFx.play(level, "entity.wither.break_block", at, 1.5, 0.4);
        SkillFx.play(level, "random.explode", at, 1.5, 0.5);
        if (center != null) {
            JobKit.strike(c.sp, center, c.j, c.P, c.k.num("burst", 8), false, false, c.life, 0, d -> {
                if (RuneKit.fxQuota("jobShBurst", 6)) JobFx.hit(center, c.j, 1.2);
            });
        }
        for (LivingEntity o : Kit.around(c.sp, at, r, 3)) {
            if (o == center || c.marked.contains(o.getUUID()) || !c.splashed.add(o.getUUID())) continue;
            RuneKit.streak(level, mid, Kit.bodyPoint(o), DUSK, 0.18, 0.3, 0.7, 0);
            RuneKit.streak(level, mid, Kit.bodyPoint(o), SILVER, 0.05, 0.25, 0.9, 0);
            JobKit.strike(c.sp, o, c.j, c.P, c.k.num("splash", 4), false, false, c.life, 2, d -> {
                JobTraits9.addCurse(c.sp, o, (int) c.k.num("curse", 1), c.j);
                if (RuneKit.fxQuota("jobShSplash", 6)) JobFx.hit(o, c.j, 0.9);
            });
        }
    }

    /** 죽음 (JobTraits9): 저주 번짐 문양이 터지기 전에 쓰러진 적 → 그 자리에서 바로 번짐 */
    static void onDeath(LivingEntity e) {
        Spread c = MARKED.remove(e.getUUID());
        if (c == null || !RuneKit.sameLife(c.sp, c.life) || e.level() != c.sp.level()) return;
        burst(c, e.position(), null);
    }

    // ------------------------------------------------------------ 그믐밤

    /**
     * 그믐밤: dur 틱 태세 — 둘레 radius 칸이 어두워짐 (나를 따라옴 — JobTraits9 태세 연출) · 활 평타 치명타 + crit (강화) · 추격이 줄지 않음 · 그 안의 몹은 lose 틱마다 나를
     * 놓침. 쓸 때: 그림자가 둘레로 퍼짐 · 하늘에 그믐달 (달 rfx_jmoon 의 테) · 바로 둘레 몹이 나를 놓침
     */
    static boolean moonless(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 120);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 6);
        JobFx.smoke(level, V.up(c, 0.8), r * 0.5, DUSK, SILVER, 0.9, Opt.of("alpha", 0.5));
        JobFx.ring(level, V.up(c, 0.06), r, j, false, 0.6, Opt.empty());
        JobFx.aura(sp, j, 2.4, 0.8, 0.7);
        Vec3 look = V.flat(sp.getViewVector(1F));
        // 하늘의 그믐달 (달 그림 5 — 그림자가 거의 삼킨 가는 달 · 은빛): 1인칭 눈높이에서 보이게 앞 9칸 · 4.5칸 위 (영상 1 — 7.5칸 위는 화면 밖)
        JobFx.moon(level, V.up(V.add(c, look, 9), 4.5), 1.4, 5, 1, dur / 20.0 * 0.5, Opt.of("fade", 0.3, "alpha", 0.9), MOON_GLOW, SILVER);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.8);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(p, 0.3), Opt.of("dir", V.unit(new Vec3(-Math.cos(a), 0.3, -Math.sin(a))), "speed", 1.6, "size", 0.16, "grow", 0.5, "life",
                    0.5, "color", SILVER, "alpha", 0.9));
        }
        for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, sp.getBoundingBox().inflate(r, 3, r), x -> x.getTarget() == sp)) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
        SkillFx.play(level, "ambient.cave", c, 1.4, 0.8);
        SkillFx.play(level, "entity.phantom.ambient", c, 0.6, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 어둠숲의 사냥

    /**
     * 가시덤불을 세우지 않을 자리: 쓴 사람 둘레 3.5칸 · 뒤쪽 7칸 (3인칭 카메라와 몸 사이 — 영상 1 에서 카메라 곁의 덤불이 화면을 가로지르는 검은 띠로 보였음)
     */
    private static boolean nearCamera(ServerPlayer sp, Vec3 p) {
        double dx = p.x - sp.getX();
        double dz = p.z - sp.getZ();
        double d = Math.hypot(dx, dz);
        Vec3 look = Kit.facingOf(sp);
        return d < 3.5 || (dx * look.x + dz * look.z < 0 && d < 7);
    }

    /**
     * 어둠숲의 사냥: 어둠숲 하늘 · 하늘로 검은 화살을 쏘아 올림 → curseAt 틱 조준한 곳 (range 칸) 둘레 radius 칸에 가시덤불이 솟고 적마다 저주 + curse → first 틱부터 gap 틱마다
     * (waves 번) 저주 걸린 적마다 하늘에서 검은 화살 두 발이 내리꽂힘 (wave — 한 번에 · 숫자 둘) → huntAt 틱 다시 쏘아 올린 사냥의 화살이 갈라져 저주 걸린 적마다 거대한 검은
     * 화살 (hunt — 큰 숫자) · 가시덤불이 부서짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 aim = Kit.aimPoint(sp, k.num("range", 20));
        Vec3 center = new Vec3(aim.x, RuneKit.groundAt(level0, aim, sp.getY()), aim.z);
        double radius = k.num("radius", 9);
        int curseAt = (int) k.num("curseAt", 10);
        int first = (int) k.num("first", 16);
        int gap = Math.max(1, (int) k.num("gap", 10));
        int waves = Math.max(1, (int) k.num("waves", 4));
        int huntAt = (int) k.num("huntAt", 58);
        String life = RuneKit.lifeToken(sp);
        int fall = 6;
        Ult.sky(level0, sp.position(), "rpg:ult_myrkvidr", huntAt + 30);
        JobMove.run(sp, JobMove.hop(0, 10, JobMove.ahead(sp, -1.5, 0), 1.2));
        // 하늘로 검은 화살 (4틱) — 하늘이 어둠숲으로 덮임
        RuneKit.later(4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 from = RuneKit.bowHand(sp);
            JobFx.blackArrow(level, from, V.up(from, 14), 60, 1.0, Opt.empty(), DUSK, SILVER);
            JobFx.smoke(level, V.up(from, 1.5), 1.2, DUSK, SILVER, 0.6, Opt.of("alpha", 0.7));
            SkillFx.play(level, "item.crossbow.shoot", from, 0.6, 1.0);
            SkillFx.play(level, "entity.phantom.swoop", from, 0.7, 0.8);
        });
        // 가시덤불 (둘레 고리 + 안쪽 몇) · 적마다 저주
        RuneKit.later(curseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            int ring = 12;
            for (int i = 0; i < ring + 4; i++) {
                double a = i < ring ? Math.PI * 2 * i / ring + Kit.rand() * 0.2 : Kit.rand() * Math.PI * 2;
                double rr = i < ring ? radius * (0.92 + Kit.rand() * 0.12) : radius * (0.3 + Kit.rand() * 0.4);
                Vec3 p = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), rr);
                if (nearCamera(sp, p)) continue;
                Vec3 g = new Vec3(p.x, RuneKit.groundAt(level, p, center.y), p.z);
                double size = i < ring ? 1.2 + Kit.rand() * 0.5 : 0.8 + Kit.rand() * 0.3;
                int left = huntAt - curseAt;
                JobFx.thicket(level, g, size, 0, 0.18, Opt.empty(), DUSK, SILVER);
                RuneKit.later(4, () -> JobFx.thicket(level, g, size, 1, (left - 4) / 20.0, Opt.empty(), DUSK, SILVER));
            }
            JobFx.ring(level, V.up(center, 0.06), radius, j, false, (huntAt - curseAt) / 20.0, Opt.of("alpha", 0.7));
            JobFx.smoke(level, V.up(center, 0.6), radius * 0.5, DUSK, SILVER, 1.0, Opt.of("alpha", 0.6));
            SkillFx.play(level, "block.chorus_flower.grow", center, 0.5, 1.0);
            SkillFx.play(level, "entity.wither.ambient", center, 0.8, 0.6);
            for (LivingEntity e : Kit.around(sp, center, radius, 4)) JobTraits9.addCurse(sp, e, (int) k.num("curse", 2), j);
        });
        // 저주 걸린 적마다 검은 화살 (물결마다 — 하늘 8칸 위에서)
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int at = first + w * gap;
            RuneKit.later(Math.max(0, at - fall), () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                for (LivingEntity e : JobTraits9.cursedNear(sp, center, radius + 1)) {
                    Vec3 to = Kit.bodyPoint(e);
                    for (int p = 0; p < 2; p++) {
                        Vec3 from = V.add(V.up(to, 8), new Vec3(Kit.rand() * 2 - 1, Kit.rand(), Kit.rand() * 2 - 1), 1.5);
                        Vec3 tp = V.up(to, (p - 0.5) * 0.35);
                        JobFx.blackArrow(level, from, tp, V.len(V.sub(tp, from)) / (fall / 20.0), 0.7, Opt.of("fade", 0.05), DUSK, SILVER);
                    }
                }
                if (ww % 2 == 0) SkillFx.play(level, "random.bowhit", center, 0.7 + ww * 0.08, 0.8);
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : JobTraits9.cursedNear(sp, center, radius + 1)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 6), 2, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobShWave", 6)) JobFx.hit(e, j, 0.9);
                        if (e.level() instanceof ServerLevel lv && RuneKit.fxQuota("jobShWaveArrow", 8)) {
                            JobFx.blackArrowAt(lv, V.up(e.position(), 0.05), new Vec3(0, -1, 0), 0.45, 0.6, Opt.of("alpha", 0.85, "fade", 0.35), DUSK, SILVER);
                        }
                    });
                }
            });
        }
        JobKit.motionLater(sp, "shadowhunter_ult:hunt", huntAt - 14, life);
        // 사냥의 화살: 다시 하늘로 (huntAt − 8) → 갈라져 적마다 거대한 검은 화살 (huntAt)
        RuneKit.later(huntAt - 8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 from = RuneKit.bowHand(sp);
            JobFx.blackArrow(level, from, V.up(V.add(center, V.flat(V.sub(sp.position(), center)), 2), 12), 70, 1.4, Opt.empty(), DUSK, SILVER);
            JobFx.burst(level, from, 0.6, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "item.crossbow.shoot", from, 0.5, 1.0);
            SkillFx.play(level, "item.trident.riptide_3", from, 0.8, 0.8);
        });
        RuneKit.later(huntAt - fall, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 split = V.up(center, 11);
            JobFx.curse(level, split, 1.3, 4, 2, 0.3, false, Opt.empty(), DUSK, SILVER);
            for (LivingEntity e : JobTraits9.cursedNear(sp, center, radius + 1)) {
                Vec3 to = Kit.bodyPoint(e);
                JobFx.blackArrow(level, split, to, V.len(V.sub(to, split)) / (fall / 20.0), 2.0, Opt.of("fade", 0.05), DUSK, SILVER);
            }
        });
        RuneKit.later(huntAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            List<LivingEntity> prey = JobTraits9.cursedNear(sp, center, radius + 1);
            for (LivingEntity e : prey) {
                Vec3 at = e.position();
                JobFx.blackArrowAt(level, V.up(at, 0.6), new Vec3(0, -1, 0), 1.1, 0.9, Opt.of("alpha", 0.9, "fade", 0.4), DUSK, SILVER);
                JobFx.curse(level, Kit.bodyPoint(e), 0.9, 4, 2, 0.35, false, Opt.empty(), DUSK, SILVER);
                JobFx.smoke(level, V.up(at, 0.6), 1.4, DUSK, SILVER, 0.6, Opt.of("alpha", 0.8));
                JobKit.strike(sp, e, j, P, k.num("hunt", 36), false, true, life, 0, d -> {
                    if (RuneKit.fxQuota("jobShHunt", 8)) JobFx.hit(e, j, 1.5);
                });
            }
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                Vec3 p = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * 0.95);
                if (nearCamera(sp, p)) continue;
                JobFx.thicket(level, new Vec3(p.x, RuneKit.groundAt(level, p, center.y), p.z), 1.3, 2, 0.4, Opt.empty(), DUSK, SILVER);
            }
            JobFx.burst(level, V.up(center, 1.0), 2.4, j, 0.45, Opt.empty());
            Ult.flash(level, center, SILVER, Opt.empty(), sp);
            RuneKit.shakeAround(level, center, 18, 0.4, 0.45);
            SkillFx.play(level, "random.explode", center, 0.8, 1.2);
            SkillFx.play(level, "entity.wither.shoot", center, 0.6, 1.0);
        });
        return true;
    }
}
