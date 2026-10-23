package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
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
 * 0.28.0 매지션 직업 스킬 (jobs.json mage — 남색 · 금빛, 특성 「주문 연쇄」): 마탄 연사 · 위상 전환 · 비전 구체 · 마나 과부하 · 금서 개방. 모두 마법 피해 (방어 무시).
 * 빠른 연타 (마탄 연사 · 금서의 주문) 는 피해를 한 번에 넣고 숫자를 여러 줄로 (메이플식 쌓이는 숫자 — JobKit.strikeSplit, 몹 무적 시간에 막히지 않게).
 * 연출 = 메이플식 2D 그림 (JobFx — 마탄 · 깨지는 거울 · 구체 · 광선 · 금서 · 책장 · 불꽃 꼬리 · 터짐, 마법진 · 룬 글자는 쓰지 않음).
 */
final class MageSkills {
    private MageSkills() {}

    private static final double[] WHITE = RuneKit.WHITE;
    /** 잔상 (짙은 남색) */
    private static final double[] SHADE = {0.12, 0.14, 0.42};
    /** 금서의 세 주문 색 {빛, 심지}: 불 · 얼음 · 번개 */
    private static final double[][][] SPELLS = {
        {{1.0, 0.42, 0.12}, {1.0, 0.86, 0.5}},
        {{0.42, 0.8, 1.0}, {0.9, 0.98, 1.0}},
        {{0.62, 0.5, 1.0}, {1.0, 0.96, 0.6}},
    };

    static void register() {
        JobSystem.register("mage_bolts", MageSkills::bolts);
        JobSystem.register("mage_phase", MageSkills::phase);
        JobSystem.register("mage_orb", MageSkills::orb);
        JobSystem.register("mage_overload", MageSkills::overload);
        JobSystem.register("mage_ult", MageSkills::ult);
    }

    /** 완드 끝 (평타 마법진 자리 — 눈앞 오른쪽 아래) */
    static Vec3 wandTip(ServerPlayer sp) {
        return RpgCombat.circleAt(sp)[0];
    }

    // ------------------------------------------------------------ 마탄 연사

    /**
     * 마탄 연사: 겨눈 적 (range 칸 · 없으면 쓰지 않음) 에게 every 틱마다 마탄 bolts 발 (완드 끝 번쩍 — 조금씩 갈라져 날아가 꽂힘). 피해는 첫 마탄이 닿을 때 한 번 (bolt × bolts —
     * 숫자 bolts 줄이 마탄이 닿을 때마다 한 줄씩)
     */
    static boolean bolts(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 20), 12);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        int n = Math.max(1, (int) k.num("bolts", 5));
        int every = Math.max(1, (int) k.num("every", 2));
        double mult = k.num("bolt", 1.4) * n;
        String life = RuneKit.lifeToken(sp);
        double speed = 26;
        for (int i = 0; i < n; i++) {
            int ii = i;
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) return;
                ServerLevel level = sp.level();
                Vec3 from = wandTip(sp);
                Vec3 to = Kit.bodyPoint(target);
                // 조금씩 갈라져 날아감 (화면에서 위 · 아래 · 옆으로 휜 자리를 지나는 대신 끝을 흩뜨림)
                Vec3 side = V.rightOf(V.flat(V.sub(to, from)));
                Vec3 aim = V.add(V.up(to, (ii % 3 - 1) * 0.25), side, ((ii * 37) % 5 - 2) * 0.12);
                int t = JobFx.bolt(level, from, aim, j, speed, 0.42, Opt.empty());
                JobFx.burst(level, from, 0.22, j, 0.18, Opt.of("vel", RuneKit.bodyVel(sp)));
                SkillFx.play(level, "mob.shulker.shoot", from, 1.5 + ii * 0.08, 0.5);
                if (ii == 0) {
                    JobKit.strikeSplit(sp, target, j, P, mult, n, every, true, false, life, t, d -> {});
                }
                RuneKit.later(t, () -> {
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    JobFx.burst(lv, Kit.bodyPoint(target), 0.45 + Kit.bodyPad(target) * 0.3, j, 0.25, Opt.empty());
                    SkillFx.play(lv, "mob.shulker.bullet.hit", Kit.bodyPoint(target), 1.4, 0.5);
                });
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 위상 전환

    /**
     * 위상 전환: 뒤로 dist 칸 순간 이동 (벽 앞까지, 땅 높이로) — 원래 자리에 남색 잔상이 남아 delay 틱 뒤 거울처럼 깨지며 둘레 radius 칸 폭발 (blast · 마법) · slow 틱 둔화.
     * 묶였거나 뒤가 막혀 못 가면 쓰지 않음
     */
    static boolean phase(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 back = V.scale(f, -1);
        double dist = RuneKit.dashDistance(sp, back, k.num("dist", 6), 1.1, 1.8);
        Vec3 end = null;
        if (dist > 0.4) {
            Vec3 b = V.add(start, back, dist);
            Vec3 at = new Vec3(b.x, RuneKit.groundAt(level, b, start.y), b.z);
            if (Kit.moveTo(sp, at)) end = at;
            else {
                List<Kit.Spot> spots = Kit.dashSpots(level, start, back, dist);
                for (int i = spots.size() - 1; i >= 0 && end == null; i--) if (Kit.moveTo(sp, spots.get(i).p())) end = spots.get(i).p();
            }
        }
        if (end == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 뒤가 막혔습니다", 30);
            return false;
        }
        Vec3 dest = end;
        int delay = (int) k.num("delay", 10);
        double radius = k.num("radius", 3);
        String life = RuneKit.lifeToken(sp);
        Kit.noFall(sp, 30);
        // 떠난 자리: 잔상 (깨질 때까지) · 둥근 베기 · 길의 줄기 (1인칭에서는 숨김) · 1인칭 빠른 줄
        RuneKit.shadeClone(sp, level, start, f, 0, SHADE, JobKit.glow(j), Opt.of("life", delay / 20.0 + 0.05, "fade", 0.15, "alpha", 0.9));
        JobFx.ring(level, V.up(start, 0.06), radius * 0.85, j, true, delay / 20.0 + 0.1, Opt.of("alpha", 0.85));
        JobFx.dash(level, V.up(start, 0.9), V.up(dest, 0.9), j, 0.3, JobFx.hide1p(sp));
        JobFx.speed(sp, j, 0.3, 0.85);
        JobFx.burst(level, V.up(dest, 1.0), 0.6, j, 0.25, JobFx.hide1p(sp));
        SkillFx.play(level, "mob.endermen.portal", start, 1.6, 0.8);
        SkillFx.play(level, "block.amethyst_block.resonate", start, 1.4, 0.6);
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 c = V.up(start, 1.0);
            // 잔상이 깨짐: 거울 조각 · 터짐 · 둥근 베기
            JobFx.shatter(d, c, 1.5, j, 0.5, Opt.empty());
            JobFx.burst(d, c, radius * 0.55, j, 0.36, Opt.empty());
            JobFx.ring(d, V.up(start, 0.06), radius, j, false, 0.35, Opt.empty());
            SkillFx.sparks(d, c, JobKit.core(j), 10, 5);
            SkillFx.play(d, "block.glass.break", c, 0.9, 1.0);
            SkillFx.play(d, "random.explode", c, 1.6, 0.5);
            int slow = (int) k.num("slow", 40);
            for (LivingEntity e : Kit.around(sp, start, radius, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("blast", 4.2), true, false, life, 0, x -> {
                    RuneKit.effect(e, "slowness", slow, 1);
                    RuneKit.knock(e, V.sub(e.position(), start), 0.3, 0.25);
                    if (RuneKit.fxQuota("jobPhase", 6)) JobFx.hit(e, j, 1.0);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 비전 구체

    /**
     * 비전 구체: 완드 끝에서 구체가 나와 겨눈 적 (없으면 조준한 곳) 을 천천히 쫓아가 그 몸을 감쌈 (speed 칸/초 · 모두 range 칸까지 — 1인칭에서 구체가 눈앞을 가리지 않게
     * 적 앞에서 멈추지 않음) — gap 틱마다 (pulses 번) 둘레 radius 칸 적에게 금빛 마력 줄기 (pulse) → dur 틱에 터짐 (burst — 둘레 radius 칸). 모두 마법 피해
     */
    static boolean orb(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, 20, 12);
        Vec3 aim = target == null ? V.up(Kit.aimPoint(sp, k.num("range", 12)), 1.2) : null;
        int pulses = (int) k.num("pulses", 6);
        int gap = (int) k.num("gap", 10);
        int dur = (int) k.num("dur", 70);
        double radius = k.num("radius", 3.5);
        double speed = k.num("speed", 3) / 20.0;
        double range = k.num("range", 12);
        Vec3[] pos = {wandTip(sp)};
        double[] travelled = {0};
        String life = RuneKit.lifeToken(sp);
        double[] core = JobKit.core(j);
        SkillFx.play(sp.level(), "beacon.activate", pos[0], 1.8, 0.6);
        SkillFx.play(sp.level(), "mob.evocation_illager.cast_spell", pos[0], 1.3, 0.6);
        JobFx.burst(sp.level(), pos[0], 0.4, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
        for (int t = 1; t <= dur; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 goal = target != null && !target.isRemoved() && target.isAlive() && target.level() == sp.level() ? Kit.bodyPoint(target) : aim != null ? aim : pos[0];
                Vec3 d = V.sub(goal, pos[0]);
                double len = V.len(d);
                double step = Math.min(Math.max(0, len - 0.2), Math.min(speed * (tt < 6 ? 3 : 1), range - travelled[0]));
                if (step > 0) {
                    pos[0] = V.add(pos[0], V.scale(d, 1 / Math.max(1e-6, len)), step);
                    travelled[0] += step;
                }
                if (tt % 2 == 0) JobFx.orb(sp.level(), pos[0], 0.36, j, 0.14, Opt.of("fade", 0.1));
                if (tt % 6 == 0) SkillFx.fx(sp.level(), "rpg:arrow_spark", pos[0], Opt.of("dir", V.unit(new Vec3(Kit.rand() - 0.5, 0.6, Kit.rand() - 0.5)), "speed", 1.5,
                        "size", 0.12, "grow", 0.6, "life", 0.35, "color", core, "alpha", 1.0));
            });
        }
        for (int p = 1; p <= pulses; p++) {
            RuneKit.later(p * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = pos[0];
                List<LivingEntity> list = Kit.visibleFrom(sp, c, Kit.around(sp, c, radius, 3));
                int[] cnt = {0};
                for (LivingEntity e : list) {
                    if (cnt[0]++ < 6) RuneKit.zap(level, c, Kit.bodyPoint(e), core, WHITE, 0.14, 0.25);
                    JobKit.strike(sp, e, j, P, k.num("pulse", 1.4), true, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobOrb", 6)) JobFx.hit(e, j, 0.8);
                    });
                }
                JobFx.burst(level, c, 0.55, j, 0.22, Opt.empty());
                SkillFx.play(level, "block.amethyst_block.chime", c, 1.5, 0.7);
            });
        }
        RuneKit.later(dur, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = pos[0];
            JobFx.burst(level, c, radius * 0.6, j, 0.42, Opt.empty());
            JobFx.ringFacing(level, c, radius * 0.7, j, V.unit(V.sub(sp.getEyePosition(), c)), 0.4, Opt.empty());
            JobFx.ring(level, new Vec3(c.x, RuneKit.groundAt(level, c, c.y - 1.5) + 0.06, c.z), radius, j, false, 0.4, Opt.empty());
            SkillFx.sparks(level, c, core, 14, 6);
            SkillFx.play(level, "random.explode", c, 1.3, 0.8);
            SkillFx.play(level, "chime.amethyst_block", c, 0.8, 1.0);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                JobKit.strike(sp, e, j, P, k.num("burst", 4.2), true, false, life, 0, x -> {
                    RuneKit.knock(e, V.sub(e.position(), c), 0.35, 0.3);
                    if (RuneKit.fxQuota("jobOrbBurst", 6)) JobFx.hit(e, j, 1.2);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 마나 과부하

    /**
     * 마나 과부하: dur 틱 태세 — 완드 평타가 관통 광선 (사거리 + range · 폭 width) · 공격 간격 × cdScale, 끝나면 after 틱 이동 느려짐 (JobTraitsMore).
     * 그림: 몸에서 솟는 남색 빛 · 발밑 둥근 베기 · 완드 끝 구체 · 1인칭 눈앞 고리 · 글자, 태세 동안 8틱마다 완드 끝 구체 · 둘레를 도는 반짝임 (JobTraits)
     */
    static boolean overload(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 200); // 0.31.0 강화: 지속
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.aura(sp, j, 2.8, 1.1, 0.95);
        JobFx.ring(level, V.up(c, 0.06), 2.4, j, false, 0.5, Opt.empty());
        JobFx.orb(level, wandTip(sp), 0.2, j, 0.6, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.eyeRing(sp, j, 2.4, 0.95, 0.5);
        SkillFx.sparks(level, V.up(c, 1.0), JobKit.core(j), 12, 4);
        DamageNumbers.showFace(sp, "§9§l마나 과부하", 2.4, 0, 0.2);
        SkillFx.play(level, "beacon.power", c, 1.6, 0.8);
        SkillFx.play(level, "mob.warden.sonic_charge", c, 1.6, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 금서 개방

    /**
     * 금서 개방: 남색 밤하늘 · 조준한 곳 (range 칸) 위에 금서가 펼쳐짐 (rise 틱 — 책장이 넘어감) · 책장 pages 장이 둘레 radius 칸을 맴돎 → gap 틱마다 책장 page 장씩 (waves 번)
     * 불 · 얼음 · 번개 주문이 되어 떨어짐 (둘레 radius 칸의 적에게 나눠 — 적이 적으면 같은 적에게 여러 장: 숫자 여러 줄) → finalDelay 틱 뒤 책이 닫히며 대폭발 (final — 둘레
     * radius 칸, 큰 숫자). 모두 마법 피해
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 aim = Kit.aimPoint(sp, k.num("range", 20));
        Vec3 center = new Vec3(aim.x, RuneKit.groundAt(level0, aim, sp.getY()), aim.z);
        int rise = (int) k.num("rise", 20);
        int gap = (int) k.num("gap", 10);
        int waves = (int) k.num("waves", 4);
        int per = Math.max(1, (int) k.num("pages", 12) / Math.max(1, waves));
        int finalAt = rise + (waves - 1) * gap + (int) k.num("finalDelay", 15);
        double radius = k.num("radius", 8);
        // 금서 자리: 과녁 가운데 위 — 가까우면 조금 더 뒤로 · 낮게 (1인칭 화면 위쪽에 책이 들어오게), 멀면 조금 더 높이
        double distH = Math.hypot(center.x - sp.getX(), center.z - sp.getZ());
        Vec3 back = distH > 0.1 ? V.flat(V.sub(center, sp.position())) : Kit.facingOf(sp);
        Vec3 bookAt = V.up(V.add(center, back, Math.max(0, 7 - distH) * 0.6), 3.4 + Math.max(0, Math.min(1.2, (distH - 7) * 0.15)));
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, sp.position(), "rpg:ult_night", finalAt + 30);
        JobFx.aura(sp, j, 2.8, rise / 20.0 + 0.4, 0.9);
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        JobFx.ring(level0, V.up(center, 0.06), radius, j, false, rise / 20.0 + 0.3, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "item.book.page_turn", sp.position(), 0.8, 1.0);
        SkillFx.play(level0, "beacon.power", center, 1.3, 0.9);
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", center, 1.1, 0.9);
        // 펼쳐진 금서 (6틱마다 다시 — 책장이 넘어감, 처음엔 아래에서 솟아오름) · 맴도는 책장
        int[] left = {Math.max(1, (int) k.num("pages", 12))};
        for (int t = 0; t < finalAt - 4; t += 6) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double q = Math.min(1, (tt + 6.0) / rise);
                JobFx.book(sp.level(), V.up(bookAt, -1.6 * (1 - q)), 1.2 + 0.6 * q, j, 0.36, false, Opt.of("alpha", 0.6 + 0.4 * q));
                if (tt % 12 == 0) SkillFx.play(sp.level(), "item.book.page_turn", bookAt, 1.2, 0.6);
            });
        }
        for (int t = 0; t < finalAt; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                int n = left[0];
                for (int i = 0; i < n; i++) {
                    double a = tt * 0.12 + (double) i / n * Math.PI * 2;
                    double rr = radius * (0.55 + 0.25 * Math.sin(i * 1.7 + tt * 0.05));
                    Vec3 p = new Vec3(center.x + Math.cos(a) * rr, bookAt.y - 1.2 + Math.sin(a * 2 + i) * 0.6, center.z + Math.sin(a) * rr);
                    double[][] col = SPELLS[i % 3];
                    JobFx.page(sp.level(), p, 0.32, mix(col[0], glow, 0.4), WHITE, 0.17, Opt.of("fade", 0.1, "alpha", 0.95));
                }
            });
        }
        for (int w = 0; w < waves; w++) {
            int ww = w;
            RuneKit.later(rise + w * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                left[0] = Math.max(0, left[0] - per);
                spellWave(sp, j, k, P, center, bookAt, radius, per, ww, life);
            });
        }
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) closeBook(sp, j, k, P, center, bookAt, radius, glow, core, life);
        });
        return true;
    }

    private static double[] mix(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** 주문 한 물결: 책장 per 장이 둘레 radius 칸의 적 (가까운 순 — 돌아가며) 에게 불 · 얼음 · 번개로 떨어짐. 같은 적에 여러 장이면 한 번에 넣고 숫자 여러 줄 */
    private static void spellWave(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 center, Vec3 bookAt, double radius, int per, int w, String life) {
        ServerLevel level = sp.level();
        List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, center, radius, 4));
        foes.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(center)));
        Map<UUID, Integer> count = new LinkedHashMap<>();
        Map<UUID, LivingEntity> byId = new LinkedHashMap<>();
        for (int p = 0; p < per; p++) {
            int kind = (w * per + p) % 3;
            Vec3 from = V.add(bookAt, new Vec3(Math.cos(p * 2.1 + w) * 1.4, -0.4, Math.sin(p * 2.1 + w) * 1.4), 1);
            Vec3 to;
            if (foes.isEmpty()) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 g = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * Math.sqrt(Kit.rand()));
                to = new Vec3(g.x, RuneKit.groundAt(level, g, center.y) + 0.2, g.z);
            } else {
                LivingEntity e = foes.get((w * per + p) % foes.size());
                count.merge(e.getUUID(), 1, Integer::sum);
                byId.put(e.getUUID(), e);
                to = Kit.bodyPoint(e);
            }
            spellFx(level, from, to, kind, p * 2);
        }
        for (var en : count.entrySet()) {
            LivingEntity e = byId.get(en.getKey());
            int n = en.getValue();
            JobKit.strikeSplit(sp, e, j, P, k.num("page", 3) * n, n, 2, true, false, life, 6, x -> {
                if (RuneKit.fxQuota("jobBook", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }

    /** 주문 하나 (delay 틱 뒤): 불 = 불꽃 꼬리 · 얼음 = 하늘빛 꼬리 + 깨지는 조각 · 번개 = 내리꽂는 번개. 닿는 데 약 6틱 */
    private static void spellFx(ServerLevel level, Vec3 from, Vec3 to, int kind, int delay) {
        double[] glow = SPELLS[kind][0];
        double[] core = SPELLS[kind][1];
        RuneKit.later(delay, () -> {
            Vec3 d = V.sub(to, from);
            double len = Math.max(0.5, V.len(d));
            Vec3 u = V.scale(d, 1 / len);
            double life = 6 / 20.0;
            if (kind == 2) {
                RuneKit.zap(level, from, to, glow, core, 0.2, 0.35);
                RuneKit.later(6, () -> {
                    SkillFx.burst(level, to, core, 1.2, 0.3);
                    SkillFx.play(level, "ambient.weather.lightning.impact", to, 1.5, 0.35);
                });
                return;
            }
            JobFx.comet(level, from, u, 0.7, len / life, life, glow, core, Opt.empty());
            RuneKit.later(6, () -> {
                SkillFx.burst(level, to, core, 1.1, 0.3);
                SkillFx.sparks(level, to, glow, 6, 4);
                SkillFx.play(level, kind == 0 ? "mob.ghast.fireball" : "block.glass.break", to, kind == 0 ? 1.2 : 1.5, 0.5);
            });
        });
    }

    /** 책이 닫히며 대폭발: 닫히는 책 · 큰 터짐 셋 · 둥근 베기 둘 · 빛기둥 · 번쩍임 — 둘레 radius 칸 (final — 큰 숫자) */
    private static void closeBook(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 center, Vec3 bookAt, double radius, double[] glow, double[] core,
            String life) {
        ServerLevel level = sp.level();
        JobFx.book(level, bookAt, 1.8, j, 0.3, true, Opt.empty());
        RuneKit.later(4, () -> {
            for (int i = 0; i < 3; i++) {
                int ii = i;
                RuneKit.later(i * 2, () -> JobFx.burst(level, V.up(center, 0.8 + ii * 0.7), 2.2 + ii * 0.7, j, 0.42, Opt.empty()));
            }
            JobFx.ring(level, V.up(center, 0.06), radius, j, false, 0.5, Opt.empty());
            RuneKit.later(3, () -> JobFx.ring(level, V.up(center, 0.1), radius * 0.6, j, true, 0.45, Opt.empty()));
            RuneKit.godRays(sp, level, center, radius * 0.6, 6, glow, core, Opt.of("h", 14.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
            SkillFx.sparks(level, V.up(center, 1.0), core, 20, 7);
            Ult.flash(level, center, glow, Opt.empty(), sp);
            RuneKit.shakeAround(level, center, 18, 0.4, 0.4);
            SkillFx.play(level, "random.explode", center, 0.8, 1.0);
            SkillFx.play(level, "firework.large_blast", center, 1.0, 0.9);
            SkillFx.play(level, "item.trident.thunder", center, 1.4, 0.7);
            for (LivingEntity e : Kit.around(sp, center, radius, 4)) {
                JobKit.strike(sp, e, j, P, k.num("final", 20), true, true, life, 0, x -> {
                    RuneKit.knock(e, V.sub(e.position(), center), 0.45, 0.45);
                    if (RuneKit.fxQuota("jobBookFinal", 6)) JobFx.hit(e, j, 1.5);
                });
            }
        });
    }
}
