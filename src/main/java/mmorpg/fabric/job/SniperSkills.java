package mmorpg.fabric.job;

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
 * 0.51.0 2차 직업 — 궁수 각성 스나이퍼 스킬 (jobs.json advance.sniper — 밝은 연두 · 금빛 · 흰 섬광, 특성 「호흡」 최대 8 · 급소 사격 — JobTraitsMore · JobTraits9):
 * 1차 궁수의 컨셉 (멈춰 쏘는 명사수 · 빛의 화살 · 화살비 · 매의 눈 · 거대한 화살) 그대로 새 동작 — 꿰뚫는 연사 (뒤로 구르며 두 발 → 무릎 꿇고 세 발 · 마지막 관통) ·
 * 순풍 공중 사격 (바람을 타고 높이 물러나며 공중에서 세 발 — 활 평타 화살) · 울르의 폭우 (뛰어올라 하늘로 두 번 → 화살비 두 번 · 묶음) · 사과 겨누기 (8초 — 모든 화살이 급소
 * 사격 · 약점 과녁) · 세 번째 화살 (뒤로 뛰어 공중에서 두 발 → 무릎 꿇고 가장 큰 셋째 화살 · 반동). 화살은 공격 피해. 연출 = 2D 그림 (빛의 화살 · 약점 과녁
 * rfx_jweak · 거대한 셋째 화살 rfx_jgreatarrow · 바람 고리 · 과녁 · 꽂힌 화살).
 */
final class SniperSkills {
    private SniperSkills() {}

    /** 흰 섬광 (급소 · 셋째 화살의 심지) */
    private static final double[] FLASH = JobTraits9.SNIPE;
    /** 순풍 잔상 (짙은 연두) */
    private static final double[] SHADE = {0.18, 0.4, 0.12};

    static void register() {
        JobSystem.register("sniper_pierce", SniperSkills::pierce);
        JobSystem.register("sniper_gale", SniperSkills::gale);
        JobSystem.register("sniper_storm", SniperSkills::storm);
        JobSystem.register("sniper_apple", SniperSkills::apple);
        JobSystem.register("sniper_ult", SniperSkills::ult);
    }

    /** 빛의 활 자리 (활을 든 손 앞) */
    private static Vec3 bowAt(ServerPlayer sp, double ahead) {
        return V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), ahead);
    }

    private static boolean alive(ServerPlayer sp, String life, LivingEntity e) {
        return RuneKit.sameLife(sp, life) && e != null && !e.isRemoved() && e.isAlive();
    }

    // ------------------------------------------------------------ 꿰뚫는 연사

    /**
     * 꿰뚫는 연사: 겨눈 적 (range + 호흡 칸 · 없으면 쓰지 않음) — 뒤로 roll 칸 구르며 (8틱 · 낮은 도약) rollAt 틱부터 rollEvery 틱마다 rollShots 발 (rollArrow — 첫 화살이 닿을 때
     * 한 번에 · 숫자 여러 줄) → kneelAt 틱 무릎 꿇고 kneelEvery 틱마다 kneelShots 발 + 마지막 꿰뚫는 화살 (pierce — 겨눈 적은 첫 화살이 닿을 때 한 번에, 같은 일직선 위의
     * 다른 적은 화살이 닿는 틱에 — 앞 range 칸 · 폭 width)
     */
    static boolean pierce(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int breath = JobTraitsMore.breath(sp);
        double range = k.num("range", 24) + breath;
        LivingEntity target = Kit.pickTarget(sp, range, 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        double speed = k.num("speed", 55);
        int rollAt = (int) k.num("rollAt", 2);
        int rollEvery = Math.max(1, (int) k.num("rollEvery", 4));
        int rollShots = Math.max(1, (int) k.num("rollShots", 2));
        int kneelAt = (int) k.num("kneelAt", 12);
        int kneelEvery = Math.max(1, (int) k.num("kneelEvery", 3));
        int kneelShots = Math.max(0, (int) k.num("kneelShots", 2));
        double half = k.num("width", 1.2) / 2;
        // 몸: 뒤로 구름 (낮은 도약 — 동작이 몸을 굴림) → 무릎 꿇고 쏨 (제자리)
        JobMove.run(sp, JobMove.hop(0, 8, JobMove.ahead(sp, -k.num("roll", 2.5), 0), 0.45));
        ServerLevel level0 = sp.level();
        JobFx.wind(level0, V.up(sp.position(), 0.08), 1.2, j, 0.4, Opt.empty());
        SkillFx.play(level0, "entity.player.attack.sweep", sp.position(), 1.6, 0.4);
        for (int i = 0; i < rollShots; i++) {
            int ii = i;
            RuneKit.later(rollAt + i * rollEvery, () -> {
                if (!alive(sp, life, target)) return;
                ServerLevel level = sp.level();
                Vec3 from = RuneKit.bowHand(sp);
                Vec3 to = V.up(Kit.bodyPoint(target), (ii - 0.5) * 0.3);
                JobFx.bow(level, bowAt(sp, 0.5), 0.42, j, 2, 4, rollEvery / 20.0 + 0.06, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
                int t = JobFx.arrow(level, from, to, j, speed, 0.62, Opt.empty());
                SkillFx.play(level, "random.bow", from, 1.3 + ii * 0.1, 0.7);
                if (ii == 0) JobKit.strikeSplit(sp, target, j, P, k.num("rollArrow", 1.2) * rollShots, rollShots, rollEvery, false, false, life, t, d -> {});
                RuneKit.later(t, () -> {
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    JobFx.hit(target, j, 0.8);
                    SkillFx.play(lv, "random.bowhit", Kit.bodyPoint(target), 1.2, 0.5);
                });
            });
        }
        int volley = kneelShots + 1;
        for (int i = 0; i < volley; i++) {
            int ii = i;
            boolean last = i == kneelShots;
            RuneKit.later(kneelAt + i * kneelEvery, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 from = RuneKit.bowHand(sp);
                boolean on = !target.isRemoved() && target.isAlive();
                JobFx.bow(level, bowAt(sp, 0.5), last ? 0.55 : 0.45, j, 2, 4, kneelEvery / 20.0 + 0.08, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
                if (!last) {
                    if (!on) return;
                    Vec3 to = V.up(Kit.bodyPoint(target), (ii - 0.5) * 0.25);
                    int t = JobFx.arrow(level, from, to, j, speed, 0.66, Opt.empty());
                    SkillFx.play(level, "random.bow", from, 1.1 + ii * 0.1, 0.8);
                    if (ii == 0) {
                        // 무릎 꿇고 쏜 세 발 (마지막 꿰뚫는 화살까지) — 겨눈 적은 첫 화살이 닿을 때 한 번에
                        JobKit.strikeSplit(sp, target, j, P, k.num("kneelArrow", 1.4) * kneelShots + k.num("pierce", 2.3), volley, kneelEvery, false, false, life, t,
                                d -> {});
                    }
                    RuneKit.later(t, () -> {
                        if (!target.isRemoved()) JobFx.hit(target, j, 0.85);
                    });
                    return;
                }
                // 마지막: 꿰뚫는 화살 — 겨눈 적 쪽 (쓰러졌으면 보는 쪽) 일직선, 흰 섬광 줄 · 화살이 지나간 자리 바람 고리
                Vec3 eye = sp.getEyePosition();
                Vec3 dir = on ? V.unit(V.sub(Kit.bodyPoint(target), eye)) : sp.getViewVector(1F);
                double len = Math.max(2, RuneKit.rayLength(level, eye, dir, range));
                Vec3 end = V.add(eye, dir, len);
                JobFx.greatArrow(level, from, V.add(from, dir, len), speed * 1.3, 1.25, Opt.empty(), JobKit.glow(j), FLASH);
                // 0.51.0 영상 1 뒤: 굵은 흰 섬광 줄 · 연두 빛 줄 · 활 앞 터짐 (가는 줄은 3인칭에서 거의 안 보였음)
                RuneKit.streak(level, V.add(from, dir, 1.0), end, FLASH, 0.12, 0.45, 1.0, 0);
                RuneKit.streak(level, V.add(from, dir, 1.0), end, JobKit.glow(j), 0.32, 0.4, 0.5, 0);
                JobFx.burst(level, V.add(from, dir, 0.6), 0.5, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
                for (double dd : new double[] {3, 7, 12}) {
                    if (dd > len) break;
                    Vec3 at = V.add(eye, dir, dd);
                    int delay = (int) Math.round(dd / (speed * 1.3) * 20);
                    RuneKit.later(delay, () -> JobFx.ringFacing(level, at, 0.55 + dd * 0.03, j, V.scale(dir, -1), 0.35, Opt.of("alpha", 0.85)));
                }
                SkillFx.play(level, "item.crossbow.shoot", from, 1.4, 0.9);
                SkillFx.play(level, "item.trident.throw", from, 1.6, 0.6);
                for (Kit.LineHit h : RuneKit.lineTargets(sp, eye, dir, len, half)) {
                    LivingEntity e = h.e();
                    if (e == target) continue; // 겨눈 적은 무릎 꿇은 세 발에 넣음
                    int hitT = Math.max(1, (int) Math.round(h.t() / (speed * 1.3) * 20));
                    JobKit.strike(sp, e, j, P, k.num("pierce", 2.3), false, false, life, hitT, d -> {
                        if (RuneKit.fxQuota("jobSnPierce", 6)) JobFx.hit(e, j, 0.9);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 순풍 공중 사격

    /**
     * 순풍 공중 사격: 바람을 타고 뒤로 back 칸 · lift 칸 높이 (rise 틱 포물선) 뛰어오르며 firstShot 틱부터 every 틱마다 겨눈 적에게 shots 발 (각각 활 평타와 같은 피해 ×
     * arrow — RpgCombat.extraArrow · 치명타 따로) · for 틱 동안 호흡이 줄지 않음. 겨눈 적이 없으면 조준한 곳으로 (피해 없음). 묶였으면 쓰지 않음
     */
    static boolean gale(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        LivingEntity target = Kit.pickTarget(sp, 24 + JobTraitsMore.breath(sp), 12);
        Vec3 start = sp.position();
        Vec3 look = Kit.facingOf(sp);
        String life = RuneKit.lifeToken(sp);
        int rise = Math.max(4, (int) k.num("rise", 12));
        int shots = Math.max(1, (int) k.num("shots", 3));
        int first = (int) k.num("firstShot", 4);
        int every = Math.max(1, (int) k.num("every", 3));
        JobTraitsMore.holdBreath(sp, (int) k.num("for", 100));
        JobMove.run(sp, JobMove.hop(0, rise, JobMove.ahead(sp, -k.num("back", 5), 0), k.num("lift", 2.5)));
        // 발밑 바람 회오리 · 떠난 자리 연두 잔상 · 위로 솟는 바람 줄 · 날아오르는 깃털
        JobFx.wind(level0, V.up(start, 0.08), 1.8, j, 0.5, Opt.empty());
        RuneKit.shadeClone(sp, level0, start, look, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.8));
        JobFx.feathers(level0, V.up(start, 1.2), 6, 0.8, 0.18, j, 1.0, JobFx.hide1p(sp));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            Vec3 p = V.add(start, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.7);
            SkillFx.fx(level0, "rpg:arrow_spark", V.up(p, 0.2), Opt.of("dir", V.UP, "speed", 3.0, "size", 0.16, "grow", 0.6, "life", 0.4, "color", JobKit.glow(j), "alpha", 1.0));
        }
        SkillFx.play(level0, "entity.breeze.jump", start, 1.3, 0.9);
        SkillFx.play(level0, "mob.phantom.flap", start, 1.1, 0.8);
        for (int i = 0; i < shots; i++) {
            int ii = i;
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 from = RuneKit.bowHand(sp);
                Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
                boolean on = target != null && !target.isRemoved() && target.isAlive() && target.level() == level;
                Vec3 to = on ? V.up(Kit.bodyPoint(target), (ii - 1) * 0.25) : Kit.aimPoint(sp, 24);
                JobFx.bow(level, bowAt(sp, 0.5), 0.45, j, 2, 4, every / 20.0 + 0.08, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
                JobFx.arrow(level, from, to, j, 60, 0.7, Opt.empty());
                JobFx.wind(level, from, 0.55, j, 0.3, Opt.of("dir", V.unit(V.sub(to, from)), "alpha", 0.8));
                SkillFx.play(level, "random.bow", from, 1.2 + ii * 0.1, 0.7);
                if (on && w != null && "bow".equals(w.type())) RpgCombat.extraArrow(sp, target, w, JobUpgrades.num(sp, k, "arrow", 1));
            });
        }
        RuneKit.later(rise, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.wind(sp.level(), V.up(sp.position(), 0.08), 1.2, j, 0.35, Opt.of("alpha", 0.85));
            JobFx.eyeRing(sp, j, 2.2, 0.7, 0.4);
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.6, 0.6);
        });
        return true;
    }

    // ------------------------------------------------------------ 울르의 폭우

    /**
     * 울르의 폭우: 조준한 곳 (range + 호흡 칸) 바닥에 과녁 (반지름 radius — 조여 들어와 맞춰짐) · 제자리에서 hop 칸 뛰어올라 하늘로 두 번 쏨 (0 · shootGap 틱) → first 틱부터
     * gap 틱마다 (volleys 번) 하늘에서 화살비가 쏟아짐 — 과녁 안의 적마다 한 번에 volley (숫자 split 줄), 처음 맞은 적은 root 틱 묶임 (발밑 꽂힌 화살 — 보스는 둔화)
     */
    static boolean storm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 aim = Kit.aimPoint(sp, k.num("range", 24) + JobTraitsMore.breath(sp));
        Vec3 center = new Vec3(aim.x, RuneKit.groundAt(level0, aim, sp.getY()), aim.z);
        double r = k.num("radius", 5.5);
        int volleys = Math.max(1, (int) k.num("volleys", 2));
        int gap = (int) k.num("gap", 14);
        int first = (int) k.num("first", 16);
        int split = Math.max(1, (int) k.num("split", 6));
        int shootGap = (int) k.num("shootGap", 8);
        int end = first + (volleys - 1) * gap;
        String life = RuneKit.lifeToken(sp);
        java.util.Set<java.util.UUID> rooted = new java.util.HashSet<>();
        JobMove.run(sp, JobMove.hop(0, 12, from -> from, k.num("hop", 1.5)));
        // 하늘로 두 번 쏨 (활 · 빛의 화살 셋씩)
        for (int s = 0; s < 2; s++) {
            int ss = s;
            RuneKit.later(s * shootGap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                JobFx.bow(level, bowAt(sp, 0.5), 0.52, j, 1, 5, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
                for (int i = 0; i < 3; i++) {
                    Vec3 from = RuneKit.bowHand(sp);
                    Vec3 up = V.add(V.up(from, 9 + i), V.flat(sp.getViewVector(1F)), 1.5 + i * 0.7 + ss);
                    JobFx.arrow(level, from, up, j, 48, 0.62, Opt.empty());
                }
                JobFx.burst(level, V.up(RuneKit.bowHand(sp), 0.6), 0.45, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
                SkillFx.play(level, "random.bow", sp.position(), 0.85 + ss * 0.15, 1.0);
            });
        }
        JobFx.target(level0, V.up(center, 0.06), r, j, V.UP, 0, 0.3, Opt.empty());
        RuneKit.later(6, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.target(sp.level(), V.up(center, 0.06), r, j, V.UP, 1, (end - 6) / 20.0 + 0.4, Opt.of("alpha", 0.85));
        });
        boolean onMe = Math.hypot(sp.getX() - center.x, sp.getZ() - center.z) < 0.1;
        Vec3 sky = V.up(V.add(center, onMe ? V.ZERO : V.flat(V.sub(sp.position(), center)), 2.5), 8.5);
        int fall = 6;
        // 쏟아지는 화살 (물결마다 촘촘히 · 그 사이 듬성듬성 — 피해와 따로, 빈 자리에도): 닿으면 불티 · 땅에 잠깐 꽂혀 있음
        for (int t = Math.max(0, first - fall - 4); t <= end - 2; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                boolean dense = false;
                for (int v = 0; v < volleys; v++) if (Math.abs(tt + fall - (first + v * gap)) <= 3) dense = true;
                int n = dense ? 6 : 2;
                for (int i = 0; i < n; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 g = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * Math.sqrt(Kit.rand()));
                    Vec3 to = new Vec3(g.x, RuneKit.groundAt(level, g, center.y) + 0.05, g.z);
                    Vec3 from = V.add(sky, new Vec3(Kit.rand() * 2 - 1, Kit.rand(), Kit.rand() * 2 - 1), 1.8);
                    Vec3 dv = V.unit(V.sub(to, from));
                    JobFx.arrow(level, from, to, j, V.len(V.sub(to, from)) / (fall / 20.0), 0.58, Opt.of("fade", 0.05));
                    RuneKit.later(fall, () -> {
                        SkillFx.sparks(level, V.up(to, 0.1), JobKit.glow(j), 2, 2);
                        JobFx.arrowAt(level, V.up(to, 0.05), dv, 0.44, 0.6, j, Opt.of("alpha", 0.85, "fade", 0.35));
                    });
                }
            });
        }
        for (int v = 0; v < volleys; v++) {
            int vv = v;
            RuneKit.later(Math.max(0, first + v * gap - fall), () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    Vec3 to = Kit.bodyPoint(e);
                    for (int p = 0; p < 3; p++) {
                        Vec3 from = V.add(sky, new Vec3(Kit.rand() * 2 - 1, Kit.rand(), Kit.rand() * 2 - 1), 1.2);
                        Vec3 at = V.up(to, (p - 1) * 0.3);
                        JobFx.arrow(level, from, at, j, V.len(V.sub(at, from)) / (fall / 20.0), 0.62, Opt.of("fade", 0.05));
                    }
                }
                SkillFx.play(level, "random.bowhit", center, 0.85 + vv * 0.15, 0.9);
            });
            RuneKit.later(first + v * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                JobFx.ring(level, V.up(center, 0.08), r * 0.9, j, false, 0.35, Opt.of("alpha", 0.8));
                Kit.shakeNear(level, center, 10, 0.15, 0.2);
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    boolean pin = rooted.add(e.getUUID());
                    JobKit.strikeSplit(sp, e, j, P, k.num("volley", 6.75), split, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobSnStorm", 6)) JobFx.hit(e, j, 1.0);
                        if (pin && k.num("root", 0) > 0) {
                            boolean boss = JobKit.isBoss(e);
                            RuneKit.effect(e, "slowness", (int) k.num("root", 30), boss ? 1 : 10);
                            if (RuneKit.fxQuota("jobSnPin", 6)) {
                                Vec3 toMe = V.flat(V.sub(sp.position(), e.position()));
                                Vec3 at = V.add(e.position(), toMe, Kit.bodyPad(e) + 0.15);
                                JobFx.pinned(sp.level(), V.up(at, 0.02), 0.44 + Kit.bodyPad(e) * 0.25, j, k.num("root", 30) / 20.0, Opt.of("vel", RuneKit.entVel(e)));
                            }
                        }
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 사과 겨누기

    /**
     * 사과 겨누기: dur 틱 태세 (강화 — 지속) — 호흡 즉시 가득 · 줄지 않음 · 활 평타가 모두 급소 사격 (치명타 확정 · 꿰뚫음 — 호흡을 쓰지 않음, JobTraitsMore · JobTraits9).
     * 그림: 몸에서 솟는 빛 · 눈앞 (1인칭) · 몸 앞 (3인칭) 큰 약점 과녁이 조여 듦 · 발밑 고리 · 흰 섬광, 태세 동안 8틱마다 앞 적들의 머리 위 금빛 약점 과녁 (JobTraits9)
     */
    static boolean apple(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        JobTraitsMore.focusStart(sp, j);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 look = sp.getViewVector(1F);
        JobFx.aura(sp, j, 2.6, 1.0, 0.9);
        JobFx.ring(level, V.up(c, 0.06), 2.2, j, false, 0.45, Opt.empty());
        JobFx.weak(level, V.add(sp.getEyePosition(), look, 3.0), 0.75, 0, 3, 0.55, false, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)), JobKit.glow(j), FLASH);
        JobFx.weak(level, V.add(V.up(c, 1.3), V.flat(look), 1.2), 0.6, 0, 3, 0.6, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), JobKit.glow(j), FLASH);
        JobFx.feathers(level, V.up(c, 2.4), 5, 0.8, 0.18, j, 1.2, JobFx.hide1p(sp));
        SkillFx.sparks(level, V.up(c, 1.2), FLASH, 10, 4);
        SkillFx.play(level, "crossbow.loading.end", c, 1.2, 0.9);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.8, 0.7);
        return true;
    }

    // ------------------------------------------------------------ 세 번째 화살

    /** 쓴 때의 땅 높이 (공중에서 쏴도 셋째 화살은 눈 높이 — 땅의 적을 그대로 꿰뚫음) */
    private static final java.util.Map<java.util.UUID, Double> ULT_GROUND = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 세 번째 화살: 새벽빛 하늘 · 뒤로 back 칸 · lift 칸 높이 뛰어올라 (8틱) airAt 틱부터 airGap 틱마다 공중에서 겨눈 적에게 airShots 발 (air — 화살이 닿는 틱에) → landAt
     * 틱에 내려앉아 무릎 꿇고 시위를 끝까지 당김 (눈앞에 커지는 셋째 화살 · 빛이 모여 듦) → shotAt 틱 가장 큰 셋째 화살 — 앞 range 칸 일직선 폭 width 관통 (shot — 큰 숫자,
     * 화살이 닿는 틱에) · 반동으로 recoil 칸 밀려남 → waveDelay 틱 뒤 화살이 지나간 길의 바람이 한꺼번에 터짐 (wave)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        LivingEntity target = Kit.pickTarget(sp, 30, 12);
        Vec3 c0 = sp.position();
        double ground0 = sp.getY();
        String life = RuneKit.lifeToken(sp);
        int airAt = (int) k.num("airAt", 8);
        int airGap = (int) k.num("airGap", 10);
        int airShots = Math.max(0, (int) k.num("airShots", 2));
        int landAt = (int) k.num("landAt", 26);
        int shotAt = (int) k.num("shotAt", 40);
        int waveDelay = (int) k.num("waveDelay", 14);
        double[] glow = JobKit.glow(j);
        Ult.sky(level0, c0, "rpg:ult_dawn", shotAt + waveDelay + 30);
        ULT_GROUND.put(sp.getUUID(), ground0);
        JobMove.run(sp, JobMove.to(0, 8, from -> V.up(JobMove.ahead(sp, -k.num("back", 3), 0).apply(from), k.num("lift", 2.2)), JobMove.OUT),
                JobMove.hold(8, landAt - 4), JobMove.to(landAt - 4, landAt, JobMove.down(sp, ground0), JobMove.IN),
                JobMove.to(shotAt, shotAt + 6, JobMove.ahead(sp, -k.num("recoil", 2), 0), JobMove.OUT));
        JobFx.aura(sp, j, 2.8, 0.8, 0.85);
        JobFx.wind(level0, V.up(c0, 0.08), 2.0, j, 0.5, Opt.empty());
        SkillFx.play(level0, "entity.breeze.jump", c0, 1.0, 1.0);
        SkillFx.play(level0, "beacon.power", c0, 1.6, 0.8);
        // 공중의 두 발 (빛나는 큰 화살 — 겨눈 적이 없으면 조준한 곳)
        double airSpeed = k.num("airSpeed", 60);
        for (int i = 0; i < airShots; i++) {
            int ii = i;
            RuneKit.later(airAt + i * airGap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 from = RuneKit.bowHand(sp);
                boolean on = target != null && !target.isRemoved() && target.isAlive() && target.level() == level;
                Vec3 to = on ? Kit.bodyPoint(target) : Kit.aimPoint(sp, 30);
                JobFx.bow(level, bowAt(sp, 0.55), 0.55, j, 2, 4, 0.25, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
                int t = JobFx.greatArrow(level, from, to, airSpeed, 0.85, Opt.empty(), glow, FLASH);
                JobFx.burst(level, from, 0.45, j, 0.22, Opt.of("vel", RuneKit.bodyVel(sp)));
                SkillFx.play(level, "item.crossbow.shoot", from, 1.2 + ii * 0.15, 0.9);
                if (!on) return;
                JobKit.strike(sp, target, j, P, k.num("air", 8), false, false, life, t, d -> {
                    JobFx.hit(target, j, 1.2);
                    if (target.level() instanceof ServerLevel lv) JobFx.weak(lv, Kit.bodyPoint(target), 0.5, 4, 2, 0.25, false, Opt.empty(), glow, FLASH);
                });
            });
        }
        JobKit.motionLater(sp, "sniper_ult:land", landAt - 4, life);
        // 무릎 꿇고 시위를 당김: 활 앞에 셋째 화살이 걸려 커짐 · 양옆 위에서 빛이 모여 듦 · 발밑 바람 고리
        for (int t = landAt; t < shotAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                double q = (tt - landAt + 2.0) / Math.max(2, shotAt - landAt);
                Vec3 look = sp.getViewVector(1F);
                Vec3 hand = RuneKit.bowHand(sp);
                JobFx.greatArrowAt(level, V.add(hand, look, 1.2 + q * 0.6), look, 0.6 + q * 0.7, 0.14, JobFx.hide1p(sp).with("alpha", 0.9), glow, FLASH);
                for (int sd : new int[] {-1, 1}) {
                    Vec3 a = V.add(V.add(hand, V.rightOf(V.flat(look)), sd * (2.2 - q * 1.6)), V.UP, 1.2 - q);
                    SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.unit(V.sub(hand, a)), "speed", 4.0, "size", 0.16, "grow", 0.5, "life", 0.25, "color", FLASH,
                            "alpha", 1.0));
                }
                if (tt % 6 == 0) {
                    JobFx.wind(level, V.up(sp.position(), 0.08), 1.0 + q, j, 0.3, Opt.of("alpha", 0.7));
                    SkillFx.play(level, "crossbow.loading.middle", sp.position(), 0.7 + q * 0.6, 0.6);
                }
            });
        }
        JobKit.motionLater(sp, "sniper_ult:release", shotAt - 1, life);
        RuneKit.later(shotAt, () -> {
            if (RuneKit.sameLife(sp, life)) release(sp, j, k, P, life);
        });
        return true;
    }

    /** 셋째 화살을 놓음: 거대한 화살 · 굵은 빛줄기 · 번쩍임 · 화살이 지나가며 남기는 바람 고리 → 길 위의 적 (shot), waveDelay 틱 뒤 길의 바람이 터짐 (wave) */
    private static void release(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 look = sp.getViewVector(1F);
        Double g = ULT_GROUND.remove(sp.getUUID());
        Vec3 eye0 = g == null ? sp.getEyePosition() : new Vec3(sp.getX(), Math.min(sp.getEyeY(), g + sp.getEyeHeight()), sp.getZ());
        Vec3 from = V.add(eye0, look, 0.8);
        double len = Math.max(2, RuneKit.rayLength(level, from, look, k.num("range", 40)));
        Vec3 end = V.add(from, look, len);
        double speed = k.num("speed", 80);
        double half = k.num("width", 3) / 2;
        int waveDelay = (int) k.num("waveDelay", 14);
        double[] glow = JobKit.glow(j);
        Vec3 hand = V.add(RuneKit.bowHand(sp), look, 1.2);
        JobFx.burst(level, hand, 1.0, j, 0.25, Opt.of("alpha", 0.85));
        JobFx.greatArrow(level, V.add(from, look, 1.4), end, speed, 2.6, Opt.empty(), glow, FLASH);
        JobFx.beam(level, hand, end, j, 2.8, len / speed + 0.35, Opt.of("alpha", 0.8));
        for (double dd = 1.6; dd <= len; dd += 1.4) {
            Vec3 at = V.add(from, look, dd);
            int delay = (int) Math.round(dd / speed * 20);
            double sz = 0.3 + Math.min(0.3, dd * 0.008);
            RuneKit.later(delay, () -> JobFx.burst(level, at, sz, j, 0.45, Opt.of("alpha", 0.9)));
        }
        for (double dd : new double[] {3, 6, 10, 15, 21, 28, 36}) {
            if (dd > len) break;
            Vec3 ringAt = V.add(from, look, dd);
            int delay = (int) Math.round(dd / speed * 20);
            double rr = 1.0 + dd * 0.04;
            RuneKit.later(delay, () -> JobFx.ringFacing(level, ringAt, rr, j, V.scale(look, -1), 0.6, Opt.of("alpha", 0.9)));
        }
        SkillFx.beamAway(level, sp.getEyePosition(), from, end, FLASH, Opt.of("half", 0.4, "thick", 0.45, "life", len / speed + 0.4, "alpha", 0.9, "sweep", len / speed), 2.4);
        Ult.flash(level, from, glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, from, 12, 0.35, 0.4);
        SkillFx.play(level, "item.trident.thunder", from, 1.4, 0.8);
        SkillFx.play(level, "firework.large_blast", from, 1.2, 0.9);
        SkillFx.play(level, "item.crossbow.shoot", from, 0.6, 1.0);
        // 길의 바람이 터짐 (waveDelay 틱 — 가까운 쪽부터 한꺼번에): 바람 회오리 · 터짐 · 땅 고리
        RuneKit.later(waveDelay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (double dd = 2; dd <= len; dd += 2.5) {
                Vec3 at = V.add(from, look, dd);
                JobFx.burst(level, at, 0.9, j, 0.3, Opt.empty());
                JobFx.wind(level, new Vec3(at.x, RuneKit.groundAt(level, at, at.y - 1.6) + 0.08, at.z), 1.4, j, 0.4, Opt.of("alpha", 0.85));
            }
            SkillFx.play(level, "firework.blast", V.add(from, look, Math.min(len, 6)), 1.0, 1.0);
            SkillFx.play(level, "entity.breeze.wind_burst", V.add(from, look, Math.min(len, 6)), 1.0, 1.0);
        });
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, look, len, half)) {
            LivingEntity e = h.e();
            int hitT = Math.max(1, (int) Math.round(h.t() / speed * 20));
            JobKit.strike(sp, e, j, P, k.num("shot", 30), false, true, life, hitT, d -> {
                RuneKit.knock(e, look, 0.5, 0.25);
                if (RuneKit.fxQuota("jobSnThird", 6)) JobFx.hit(e, j, 1.6);
            });
            JobKit.strike(sp, e, j, P, k.num("wave", 14), false, false, life, waveDelay, d -> {
                if (RuneKit.fxQuota("jobSnWave", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }
}
