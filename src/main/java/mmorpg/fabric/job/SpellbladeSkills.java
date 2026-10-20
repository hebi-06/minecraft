package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
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
 * 0.27.0 마검사 직업 스킬 (jobs.json spellblade — 남보라 마력 · 청록 칼날, 특성 「마력 검술」 · 마력 각인): 비전 참격 · 마력 도약 · 룬 검무 · 마검 각성 · 비전 거검.
 * 칼 = 공격 피해, 마력 = 마법 피해 (방어 무시) — 숫자는 둘 다 직업 색 그라데이션. 스킬이 처음 맞힌 적마다 마력 각인 하나 (한 번 쓸 때 적마다 하나 — JobTraits.mark),
 * 평타가 각인 찍힌 적을 치면 하나가 터지고 (JobTraits), 비전 거검은 남은 각인을 모두 터뜨림.
 */
final class SpellbladeSkills {
    private SpellbladeSkills() {}

    private static final double[] WHITE = RuneKit.WHITE;
    /** 마력 도약 잔상 몸 (짙은 남보라) */
    private static final double[] SHADE = {0.3, 0.16, 0.5};

    static void register() {
        JobSystem.register("spellblade_arcslash", SpellbladeSkills::arcslash);
        JobSystem.register("spellblade_blink", SpellbladeSkills::blink);
        JobSystem.register("spellblade_runedance", SpellbladeSkills::runedance);
        JobSystem.register("spellblade_awaken", SpellbladeSkills::awaken);
        JobSystem.register("spellblade_ult", SpellbladeSkills::ult);
    }

    /** 이 스킬로 처음 맞힌 적이면 각인 하나 */
    private static void markOnce(ServerPlayer sp, LivingEntity e, Jobs.Job j, Set<UUID> marked) {
        if (marked.add(e.getUUID())) JobTraits.mark(sp, e, j);
    }

    // ------------------------------------------------------------ 비전 참격

    /**
     * 비전 참격: 칼에 마력을 둘러 앞 reach 칸 · arc 도 부채꼴 베기 (slash — 칼, 공격 피해) → delay 틱 뒤 벤 자리마다 룬 폭발 (burst — 둘레 burstRadius 칸,
     * 마력, 마법 피해, 적마다 한 번 · 벤 적이 없으면 칼끝 앞에서). 벤 자리에 마력 문양이 그려지다 터짐 (보라 별빛 · 고리 · 불꽃)
     */
    static boolean arcslash(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        int delay = (int) k.num("delay", 10);
        double reach = k.num("reach", 5);
        double burstR = k.num("burstRadius", 2.4);
        Set<UUID> marked = new HashSet<>();
        // 칼에 두른 마력 (손의 칼빛) → 초승달 베기 · 앞으로 퍼지는 검기 · 화면을 가로지르는 빛
        RuneKit.Hand h = RuneKit.handOf(sp);
        RuneKit.rfx2(level, "sword", V.up(h.hand(), 0.3), Opt.of("dir", V.UP, "size", 0.55, "life", 0.18, "alpha", 0.8, "vel", RuneKit.bodyVel(sp)), glow, core);
        SkillFx.arc(level, V.up(V.add(eye, f, 1.5), -0.3), f, Opt.of("shape", "arc", "flip", false, "size", 2.9, "life", 0.26, "speed", 3.0, "spin", -6.0,
                "color", core, "aura", glow));
        RuneKit.rfx2(level, "wave", V.up(V.add(eye, f, 2.6), -0.35), Opt.of("dir", f, "speed", 7.0, "size", 1.6, "life", 0.24, "grow", 0.35, "fade", 0.6,
                "spin", 4.0, "alpha", 0.9), glow, core);
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(eye, f, 2.2), -0.25), Opt.of("dir", V.rightOf(f), "size", 2.8, "thick", 0.16, "life", 0.3, "delay", 0.04,
                "color", glow, "alpha", 1.0));
        SkillFx.play(level, "item.spear.attack_miss", eye, 1.15, 0.9);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", eye, 1.7, 0.45);
        List<LivingEntity> cut = RuneKit.coneTargets(sp, reach, k.num("arc", 100), 10);
        List<Vec3> centers = new ArrayList<>();
        int n = 0;
        for (LivingEntity e : cut) {
            Vec3 at = Kit.bodyPoint(e);
            centers.add(at);
            if (n++ < 8) {
                SkillFx.cutMark(level, at, V.unit(V.sub(at, eye)), -8, 0.1, core, WHITE, 1.0);
                SkillFx.fx(level, "rpg:magic_circle", at, Opt.of("size", 0.5, "life", delay / 20.0, "spin", 0.0, "progress", 1.0, "color", glow, "alpha", 0.9));
            }
            JobKit.strike(sp, e, j, P, k.num("slash", 3.8), false, false, life, 0, d -> markOnce(sp, e, j, marked));
        }
        if (centers.isEmpty()) {
            Vec3 tip = V.up(V.add(sp.position(), f, reach * 0.6), 1.0);
            centers.add(tip);
            SkillFx.fx(level, "rpg:magic_circle", tip, Opt.of("size", 0.6, "life", delay / 20.0, "spin", 0.0, "progress", 1.0, "color", glow, "alpha", 0.9));
        }
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            // 벤 적이 그 사이 움직였으면 지금 자리에서 (죽었으면 벤 자리)
            List<Vec3> now = new ArrayList<>();
            for (int i = 0; i < centers.size(); i++) {
                LivingEntity e = i < cut.size() ? cut.get(i) : null;
                now.add(e != null && !e.isRemoved() && e.isAlive() && e.level() == d ? Kit.bodyPoint(e) : centers.get(i));
            }
            int m = 0;
            for (Vec3 c : now) {
                // 터지는 자리 (파티클 예산): 별빛 문장 · 번쩍임 6곳, 불꽃 3곳까지
                if (m < 6) {
                    SkillFx.fx(d, "rpg:magic_circle_flash", c, Opt.of("size", burstR * 0.45, "life", 0.28, "spin", 0.0, "progress", 1.0, "color", glow, "alpha", 1.0));
                    RuneKit.rfx2(d, "sun", c, Opt.of("size", 0.6, "life", 0.26, "grow", 0.7, "fade", 0.6, "spin", Kit.rand() * 360), glow, core);
                    SkillFx.burst(d, c, glow, 1.2, 0.3);
                    RuneKit.zonePulse(d, V.up(c, -1.0), core, burstR, 0.35);
                }
                if (m < 3) SkillFx.sparks(d, c, core, 5, 5);
                m++;
            }
            SkillFx.play(d, "chime.amethyst_block", sp.position(), 1.3, 1.0);
            SkillFx.play(d, "random.explode", now.get(0), 1.7, 0.4);
            Set<UUID> hit = new HashSet<>();
            for (Vec3 c : now) {
                for (LivingEntity e : Kit.around(sp, V.up(c, -1.0), burstR, 2.5)) {
                    if (!hit.add(e.getUUID())) continue;
                    JobKit.strike(sp, e, j, P, k.num("burst", 4.7), true, false, life, 0, x -> markOnce(sp, e, j, marked));
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 마력 도약

    /**
     * 마력 도약: 보는 쪽 dist 칸 (벽 앞까지, 땅 높이로 — 한 칸 턱 · 낮은 곳도) 으로 순간 이동 — 지나간 길 (폭 width) 의 적을 마력으로 벰 (path, 마법 피해) →
     * delay 틱 뒤 닿은 자리 둘레 radius 칸 룬 폭발 (blast, 마법 피해). 떠난 자리에 남보라 잔상, 길에는 마력 번개 · 룬 글자, 닿은 자리에 마력 문양이 그려지다 터짐.
     * 묶였거나 앞이 막혀 못 가면 쓰지 않음
     */
    static boolean blink(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double dist = RuneKit.dashDistance(sp, f, k.num("dist", 7), 1.1, 1.8);
        Vec3 end = null;
        if (dist > 0.4) {
            Vec3 ahead = V.add(start, f, dist);
            Vec3 at = new Vec3(ahead.x, RuneKit.groundAt(level, ahead, start.y), ahead.z);
            if (Kit.moveTo(sp, at)) end = at;
            else {
                // 그 자리가 막혔으면 길의 설 자리 중 가장 먼 곳
                List<Kit.Spot> spots = Kit.dashSpots(level, start, f, dist);
                for (int i = spots.size() - 1; i >= 0 && end == null; i--) if (Kit.moveTo(sp, spots.get(i).p())) end = spots.get(i).p();
            }
        }
        if (end == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        Vec3 dest = end;
        double len = Math.max(0.5, V.hdist(start, dest));
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        Kit.noFall(sp, 40);
        // 떠난 자리: 잔상 · 터지는 빛 · 룬
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, glow, Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        SkillFx.burst(level, V.up(start, 1.0), glow, 1.1, 0.25);
        SkillFx.rune(level, start, Opt.of("size", 1.1, "life", 0.5, "color", glow, "alpha", 0.85, "rate", 220.0, "progress", 1.0));
        SkillFx.sparks(level, V.up(start, 1.0), core, 6, 5);
        // 길: 마력 번개 · 룬 글자 (1.5칸마다 — 내 몸 1.6칸 안은 빼서 1인칭 화면을 덮지 않게)
        RuneKit.zap(level, V.up(start, 1.0), V.up(dest, 1.0), glow, core, 0.2, 0.35);
        for (double s = 1.0; s < len; s += 1.5) {
            Vec3 p = V.add(start, f, s);
            if (V.hdist(p, dest) < 1.6) continue;
            SkillFx.rune(level, new Vec3(p.x, RuneKit.groundAt(level, p, start.y), p.z), Opt.of("size", 0.55, "life", 0.6, "color", core, "alpha", 0.8, "rate", 300.0,
                    "progress", 1.0));
        }
        SkillFx.play(level, "mob.endermen.portal", start, 1.4, 0.8);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", dest, 1.5, 0.5);
        double half = k.num("width", 1.5) / 2;
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(start, 1.0), f, len, half)) {
            LivingEntity e = h.e();
            JobKit.strike(sp, e, j, P, k.num("path", 2.55), true, false, life, 0, d -> {
                markOnce(sp, e, j, marked);
                if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                SkillFx.fx(lv, "rpg:slash_x", b, Opt.of("dir", f, "size", RuneKit.nearK(sp, b, 3, 0.4), "life", 0.25, "color", core, "alpha", 1.0));
                RuneKit.plasmaOrb(lv, b, 0.4, glow, Opt.of("life", 0.2));
            });
        }
        // 닿은 자리: 마력 문양이 delay 틱 동안 그려지다 터짐
        int delay = (int) k.num("delay", 10);
        double radius = k.num("radius", 3);
        SkillFx.fx(level, "rpg:magic_circle", V.up(dest, 0.1), Opt.of("dir", V.UP, "size", radius * 0.8, "life", delay / 20.0, "spin", 0.0, "progress", 1.0, "color",
                glow, "alpha", 0.85));
        RuneKit.domainSigil(level, dest, radius, "arcane", glow, core, Opt.of("life", delay / 20.0 + 0.1, "spinRate", 180.0, "grow", 0.1, "alpha", 0.7, "fade", 0.2));
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            SkillFx.fx(d, "rpg:magic_circle_flash", V.up(dest, 0.15), Opt.of("size", radius * 0.8, "life", 0.4, "spin", Kit.rand() * 360, "progress", 1.0,
                    "color", glow, "alpha", 1.0));
            RuneKit.novaRing(d, dest, radius * 0.5, 1.2, glow, core, Opt.of("life", 0.4));
            RuneKit.zonePulse(d, dest, core, radius, 0.35);
            // 터지는 빛: 내 몸 가까이는 작게 (1인칭 화면이 보랏빛으로 덮이지 않게)
            Vec3 c = V.up(dest, 1.0);
            SkillFx.burst(d, V.add(c, Kit.facingOf(sp), 1.6), glow, 1.4, 0.3);
            SkillFx.sparks(d, c, core, 10, 6);
            SkillFx.play(d, "random.explode", dest, 1.5, 0.6);
            SkillFx.play(d, "chime.amethyst_block", dest, 0.9, 1.0);
            for (LivingEntity e : Kit.around(sp, dest, radius, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("blast", 2.55), true, false, life, 0, x -> {
                    markOnce(sp, e, j, marked);
                    RuneKit.knock(e, V.sub(e.position(), dest), 0.3, 0.3);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 룬 검무

    /**
     * 룬 검무: 쓴 자리 둘레 반지름 radius 칸 룬 원 (dur 틱 — 그 자리에 남음) → 마력 검 swords 자루가 원을 따라 돌고, gap 틱마다 (laps 번) 원 안의 적을 벰
     * (lap — 짝수 번째 칼, 공격 피해 · 홀수 번째 마력, 마법 피해). 원 = 비전 문양 · 안쪽 거꾸로 도는 룬 · 벨 때 범위 고리
     */
    static boolean runedance(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 4);
        int laps = (int) k.num("laps", 3);
        int gap = (int) k.num("gap", 15);
        int dur = (int) k.num("dur", 40);
        int swords = (int) k.num("swords", 6);
        double lap = k.num("lap", 5.1);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        RuneKit.domainSigil(level, c, r, "arcane", glow, core, Opt.of("life", (dur + 6) / 20.0, "spinRate", 45.0, "grow", 0.1, "alpha", 0.85, "fade", 0.2));
        SkillFx.rune(level, c, Opt.of("size", r * 0.55, "life", (dur + 4) / 20.0, "color", core, "alpha", 0.7, "rate", -90.0, "progress", 1.0));
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", c, 1.4, 0.8);
        SkillFx.play(level, "beacon.activate", c, 1.7, 0.6);
        // 도는 검 (2틱마다 — 칼날이 도는 쪽으로 누움, 눈 가까이는 흐리게)
        for (int t = 0; t <= dur; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 eye = sp.getEyePosition();
                for (int i = 0; i < swords; i++) {
                    double a = tt * 0.21 + (double) i / swords * Math.PI * 2;
                    Vec3 rd = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 at = V.up(V.add(c, rd, r * 0.9), 1.0 + 0.25 * Math.sin(a * 2 + tt * 0.3));
                    Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    boolean near = V.len(V.sub(at, eye)) < 2.2;
                    RuneKit.rfx2(level, "sword", at, Opt.of("dir", tan, "size", 0.8, "life", 0.12, "alpha", near ? 0.35 : 0.95), glow, core);
                }
            });
        }
        SkillFx.every(laps, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            boolean magic = i % 2 == 1;
            RuneKit.zonePulse(level, c, magic ? glow : core, r, 0.35);
            SkillFx.play(level, magic ? "chime.amethyst_block" : "entity.player.attack.sweep", c, magic ? 1.4 : 1.1 + i * 0.1, 0.9);
            Vec3 eye = sp.getEyePosition();
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, r, 3)) {
                JobKit.strike(sp, e, j, P, lap, magic, false, life, 0, d -> {
                    markOnce(sp, e, j, marked);
                    if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                    Vec3 b = Kit.bodyPoint(e);
                    if (magic) {
                        SkillFx.burst(lv, b, glow, 0.8, 0.25);
                        RuneKit.plasmaOrb(lv, b, 0.4, glow, Opt.of("life", 0.2));
                    } else {
                        SkillFx.cutMark(lv, b, V.unit(V.sub(b, eye)), ((i * 47) % 180) - 90, 0.08, core, WHITE, 0.9);
                    }
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 마검 각성

    /**
     * 마검 각성: dur 틱 태세 — 평타가 맞을 때마다 마력 참격 잔상 (위력 × echo — JobTraits) · 평타 사거리 +reach 칸.
     * 그림: 손의 칼이 룬 빛으로 길어짐 · 발밑 비전 문양 · 솟는 룬 빛 · 1인칭 고리 · 글자, 태세 동안 8틱마다 칼빛 (JobTraits)
     */
    static boolean awaken(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 240);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        RuneKit.domainSigil(level, c, 2.8, "arcane", glow, core, Opt.of("life", 1.2, "spinRate", 120.0, "grow", 0.25, "alpha", 0.8, "fade", 0.4));
        String life = RuneKit.lifeToken(sp);
        for (int t = 0; t < 10; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.Hand h = RuneKit.handOf(sp);
                double q = (tt + 2) / 10.0;
                RuneKit.rfx2(sp.level(), "sword", V.up(h.hand(), 0.3 + 0.3 * q), Opt.of("dir", V.UP, "size", 0.45 + 0.35 * q, "life", 0.14, "alpha", 0.9, "vel",
                        RuneKit.bodyVel(sp)), glow, core);
                SkillFx.fx(sp.level(), "rpg:arrow_spark", V.up(sp.position(), 0.2 + q * 1.6), Opt.of("dir", V.UP, "speed", 2.0, "size", 0.14, "grow", 1.0, "life", 0.4,
                        "color", tt % 4 == 0 ? glow : core, "alpha", 1.0));
            });
        }
        RuneKit.selfCue(sp, glow, core, true);
        Ult.flash(level, c, glow, Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        DamageNumbers.showFace(sp, "§d§l마검 각성", 2.4, 0, 0.2);
        SkillFx.play(level, "beacon.activate", c, 1.5, 0.8);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", c, 1.6, 0.6);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 0.9, 1.0);
        return true;
    }

    // ------------------------------------------------------------ 비전 거검

    /**
     * 비전 거검: 남보라 하늘 · 발밑 거대한 비전 문양 · 하늘에 룬 아홉이 원을 이룸 → 눈앞에 거대한 마력 검이 솟음 (rise 틱) → gap 틱마다 가로 · 세로 · 대각 베기
     * (slashes — 앞 range 칸 · 폭 width, 칼, 공격 피해) → after 틱 뒤 앞 thrustAt 칸 찌르기 폭발 (thrust — 그 둘레 thrustRadius 칸 + 찌르는 길, 마력, 큰 숫자) —
     * 맞은 적의 남은 각인을 모두 터뜨려 더함 (각인 하나 = 위력 × markBurst), 찌르기에 닿지 않은 각인 찍힌 적도 그 자리에서 터짐.
     * 발동: 번쩍임 · 빛기둥 · 퍼지는 고리 · 하늘 룬의 섬광
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> slashes = SwordsmanSkills.numbers(k, "slashes", List.of(11.0, 11.0, 11.0));
        double range = k.num("range", 10);
        double half = k.num("width", 4.5) / 2;
        int rise = (int) k.num("rise", 20);
        int gap = (int) k.num("gap", 10);
        int after = (int) k.num("after", 15);
        int thrustTick = rise + (slashes.size() - 1) * gap + after;
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Set<UUID> marked = new HashSet<>();
        Ult.sky(level0, c0, "rpg:ult_arcane", thrustTick + 30);
        RuneKit.domainSigil(level0, c0, 5.5, "arcane", glow, core, Opt.of("life", (thrustTick + 4) / 20.0, "spinRate", 50.0, "grow", 0.25, "alpha", 0.82,
                "fade", 0.15));
        // 하늘의 룬 아홉 (원 — 번갈아 거꾸로 돎) + 가운데 큰 문양
        Vec3 skyAt = RuneKit.skyPoint(sp, c0, 10, 24, 7, 22);
        Vec3 toEye = RuneKit.towardEye(sp, skyAt);
        Vec3 sx = V.unit(V.cross(toEye, V.UP));
        Vec3 sy = V.unit(V.cross(sx, toEye));
        for (int i = 0; i < 9; i++) {
            double a = i / 9.0 * Math.PI * 2;
            Vec3 at = V.add(V.add(skyAt, sx, Math.cos(a) * 4.2), sy, Math.sin(a) * 4.2);
            int ii = i;
            RuneKit.later(i, () -> RuneKit.skyPlate(sp, level0, "sigil", at, 1.0, glow, core, Opt.of("cell", (double) RuneKit.SIGIL.get("arcane"), "life",
                    (thrustTick + 6 - ii) / 20.0, "spinRate", ii % 2 == 0 ? 90.0 : -90.0, "grow", 0.1, "alpha", 0.9, "fade", 0.3)));
        }
        RuneKit.skyPlate(sp, level0, "sigil", skyAt, 3.2, glow, core, Opt.of("cell", (double) RuneKit.SIGIL.get("arcane"), "life", (thrustTick + 8) / 20.0,
                "spinRate", -30.0, "grow", 0.15, "alpha", 0.78, "fade", 0.3));
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", c0, 1.0, 1.0);
        SkillFx.play(level0, "beacon.power", c0, 1.2, 0.8);
        // 솟아오르는 거대한 마력 검 (눈앞 2.6칸, 칼끝 위) + 빨려 드는 빛 → 베기 사이에도 들고 있음
        for (int t = 0; t < thrustTick; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 f = Kit.facingOf(sp);
                double q = Math.min(1, (tt + 2.0) / rise);
                Vec3 hilt = V.up(V.add(sp.position(), f, 2.6), 0.4);
                double ls = 1.5 + q * 4.5;
                boolean slashing = tt >= rise && (tt - rise) % gap < 4 && tt < rise + slashes.size() * gap;
                if (!slashing) RuneKit.rfx2(level, "sword", V.up(hilt, ls / 2), Opt.of("dir", V.UP, "size", ls / 2, "life", 0.13), glow, core);
                if (tt < rise) {
                    for (int sd : new int[] {-1, 1}) {
                        Vec3 a = V.add(V.up(hilt, 1 + q * 2.5), V.rightOf(f), sd * (2.6 - q * 1.6));
                        SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.scale(V.rightOf(f), -sd), "speed", 3.0, "size", 0.16, "grow", 0.5, "life", 0.25,
                                "color", core, "alpha", 1.0));
                    }
                    if (tt % 6 == 0) SkillFx.play(level, "block.bell.hit", hilt, 1.0 + q, 0.35);
                }
            });
        }
        for (int i = 0; i < slashes.size(); i++) {
            int ii = i;
            RuneKit.later(rise + i * gap, () -> {
                if (RuneKit.sameLife(sp, life)) giantSlash(sp, j, P, slashes.get(ii), ii, range, half, glow, core, life, marked);
            });
        }
        RuneKit.later(thrustTick, () -> {
            if (RuneKit.sameLife(sp, life)) thrust(sp, j, k, P, range, skyAt, glow, core, life);
        });
        return true;
    }

    /** 거대한 검의 베기 하나 (i = 0 가로 · 1 세로 · 2 대각): 검이 휘둘러지는 세 장 · 거대한 검기 · 화면을 가르는 빛, 앞 range 칸 · 폭 half × 2 의 적 */
    private static void giantSlash(ServerPlayer sp, Jobs.Job j, double P, double mult, int i, double range, double half, double[] glow, double[] core, String life,
            Set<UUID> marked) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Vec3 eye = sp.getEyePosition();
        Vec3 hilt = V.up(V.add(sp.position(), f, 1.8), 0.6);
        // 베는 면의 두 축: 가로 = (위 → 오른쪽), 세로 = (위 → 앞), 대각 = (위 → 오른쪽 아래 비스듬히)
        Vec3 sweep = switch (i % 3) {
            case 0 -> right;
            case 1 -> f;
            default -> V.unit(V.add(right, f, 0.6));
        };
        double[] tilt = {20, 55, 90};
        for (int s = 0; s < tilt.length; s++) {
            double deg = tilt[s];
            RuneKit.later(s, () -> {
                double a = Math.toRadians(deg);
                Vec3 dir = V.unit(V.add(V.scale(V.UP, Math.cos(a)), sweep, Math.sin(a)));
                RuneKit.rfx2(level, "sword", V.add(hilt, dir, 3.0), Opt.of("dir", dir, "size", 3.0, "life", 0.08), glow, core);
            });
        }
        Vec3 cutDir = switch (i % 3) {
            case 0 -> right;
            case 1 -> V.UP;
            default -> V.unit(V.add(right, V.UP));
        };
        double spin = i % 3 == 0 ? -4.0 : i % 3 == 1 ? 90.0 : 45.0;
        RuneKit.rfx2(level, "wave", V.up(V.add(eye, f, 3.0), -0.4), Opt.of("dir", f, "speed", 9.0, "size", 2.6, "life", 0.3, "grow", 0.3, "fade", 0.5, "spin", spin),
                glow, core);
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(eye, f, 3.4), -0.3), Opt.of("dir", cutDir, "size", 4.6, "thick", 0.3, "life", 0.4, "delay", 0.05, "color", glow,
                "alpha", 1.0));
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(eye, f, 3.35), -0.3), Opt.of("dir", cutDir, "size", 4.3, "thick", 0.1, "life", 0.36, "delay", 0.05, "color",
                WHITE, "alpha", 1.0));
        SkillFx.play(level, "item.trident.thunder", eye, 1.3 + i * 0.15, 0.6);
        SkillFx.play(level, "item.spear.attack_miss", eye, 0.8 + i * 0.1, 1.0);
        RuneKit.shakeAround(level, sp.position(), 10, 0.15, 0.2);
        Vec3 from = V.up(sp.position(), 1.0);
        double len = Math.max(1, RuneKit.rayLength(level, from, f, range));
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, half)) {
            LivingEntity e = h.e();
            JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                markOnce(sp, e, j, marked);
                if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                SkillFx.cutMark(lv, b, V.unit(V.sub(b, sp.getEyePosition())), i % 3 == 0 ? -4 : i % 3 == 1 ? 90 : 45, 0.1, core, WHITE, 1.3);
                if (cnt[0] <= 4) SkillFx.sparks(lv, b, core, 5, 5);
            });
        }
    }

    /** 찌르기 폭발 + 각인 터뜨림 (비전 거검 마지막) */
    private static void thrust(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double range, Vec3 skyAt, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = V.up(sp.position(), 1.0);
        double reach = Math.max(1, RuneKit.rayLength(level, from, f, k.num("thrustAt", 6)));
        Vec3 point = V.add(from, f, reach);
        Vec3 ground = new Vec3(point.x, RuneKit.groundAt(level, point, sp.getY()), point.z);
        double radius = k.num("thrustRadius", 4);
        double markBurst = j.trait() == null ? 1.0 : j.trait().def().numOr("markBurst", 1.0);
        // 거대한 검이 앞으로 뻗어 찌름 (손잡이 눈앞 1.8칸 → 칼끝이 폭발 자리)
        Vec3 hilt = V.up(V.add(sp.position(), f, 1.8), 1.0);
        Vec3 dv = V.unit(V.sub(point, hilt));
        double lv0 = Math.max(1, V.len(V.sub(point, hilt)));
        RuneKit.rfx2(level, "sword", V.add(hilt, dv, lv0 / 2), Opt.of("dir", dv, "size", lv0 / 2 + 0.6, "life", 0.5, "fade", 0.5), glow, core);
        RuneKit.flyStreak(level, hilt, point, 60, glow, core, 1.2, 0.45);
        // 폭발: 문양 섬광 · 퍼지는 고리 · 빛기둥 · 하늘 룬 섬광 · 번쩍임
        SkillFx.fx(level, "rpg:magic_circle_flash", V.up(ground, 0.15), Opt.of("size", radius * 0.9, "life", 0.5, "spin", Kit.rand() * 360, "progress", 1.0,
                "color", glow, "alpha", 1.0));
        RuneKit.later(2, () -> RuneKit.novaRing(level, ground, radius * 0.45, 2.0, glow, core, Opt.of("life", 0.5)));
        RuneKit.zonePulse(level, ground, core, radius, 0.45);
        SkillFx.burst(level, point, glow, 2.4, 0.4);
        SkillFx.sparks(level, point, core, 14, 7);
        RuneKit.later(3, () -> RuneKit.godRays(sp, level, ground, 0, 1, glow, core, Opt.of("h", 16.0, "life", 0.8, "skipNear", true)));
        RuneKit.skyFlare(level, skyAt, 3.4, glow, core, Opt.of("life", 0.5));
        Ult.flash(level, point, glow, Opt.empty(), sp);
        SkillFx.play(level, "random.explode", point, 1.0, 1.0);
        SkillFx.play(level, "item.trident.thunder", point, 1.1, 0.9);
        SkillFx.play(level, "chime.amethyst_block", point, 0.6, 1.0);
        RuneKit.shakeAround(level, point, 14, 0.35, 0.35);
        // 맞을 적: 찌르는 길 (폭 1.2) + 폭발 둘레 — 남은 각인을 모두 떼어 찌르기에 더함
        Map<UUID, LivingEntity> hit = new LinkedHashMap<>();
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, reach, 1.2)) hit.putIfAbsent(h.e().getUUID(), h.e());
        for (LivingEntity e : Kit.around(sp, ground, radius, 3)) hit.putIfAbsent(e.getUUID(), e);
        double mult = k.num("thrust", 35);
        for (LivingEntity e : hit.values()) {
            int marks = JobTraits.takeAllMarks(sp, e);
            JobKit.strike(sp, e, j, P, mult + markBurst * marks, true, true, life, 0, d -> {
                RuneKit.knock(e, V.sub(e.position(), ground), 0.4, 0.45);
                if (marks > 0 && e.level() instanceof ServerLevel lv) markBlast(sp, lv, e, marks, glow, core);
            });
        }
        // 찌르기에 닿지 않은 각인 찍힌 적 (range + radius 칸 안): 그 자리에서 터짐
        for (LivingEntity e : JobKit.near(sp, range + radius, 32)) {
            if (hit.containsKey(e.getUUID())) continue;
            int marks = JobTraits.takeAllMarks(sp, e);
            if (marks <= 0) continue;
            JobKit.strike(sp, e, j, P, markBurst * marks, true, false, life, 0, d -> {
                if (e.level() instanceof ServerLevel lv) markBlast(sp, lv, e, marks, glow, core);
            });
        }
    }

    /** 각인이 터짐: 머리 위 룬이 각인 수만큼 겹쳐 터짐 · 별빛 · "◈×n" */
    private static void markBlast(ServerPlayer sp, ServerLevel level, LivingEntity e, int marks, double[] glow, double[] core) {
        if (!RuneKit.fxQuota("jobMarkBlast", 6)) return;
        Vec3 b = Kit.bodyPoint(e);
        for (int m = 0; m < Math.min(3, marks); m++) {
            SkillFx.fx(level, "rpg:magic_circle_flash", V.up(b, 0.3 * m), Opt.of("size", 0.6 + 0.2 * m, "life", 0.3, "spin", Kit.rand() * 360, "progress", 1.0,
                    "color", m % 2 == 0 ? glow : core, "alpha", 1.0));
        }
        RuneKit.rfx2(level, "sun", b, Opt.of("size", 0.8, "life", 0.3, "grow", 0.8, "fade", 0.6, "spin", Kit.rand() * 360), glow, core);
        DamageNumbers.showAt(level, V.up(b, 1.4), "§d§l◈×" + marks);
    }
}
