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
import mmorpg.fabric.rune.RuneProps;
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
import org.joml.Quaternionf;

/**
 * 0.27.0 마검사 직업 스킬 (jobs.json spellblade — 남보라 마력 · 청록 칼날, 특성 「마력 검술」 · 마력 각인): 비전 참격 · 마력 도약 · 룬 검무 · 스펠 가이저 (0.51.0
 * 마검 각성 태세 대신 — 사용자 "직업 중에 구지 강화 시키면 어색한 애들은 빼자") · 비전 거검.
 * 칼 = 공격 피해, 마력 = 마법 피해 (방어 무시) — 숫자는 둘 다 직업 색 그라데이션. 스킬이 처음 맞힌 적마다 마력 각인 하나 (한 번 쓸 때 적마다 하나 — JobTraits.mark),
 * 평타가 각인 찍힌 적을 치면 하나가 터지고 (JobTraits), 비전 거검은 남은 각인을 모두 터뜨림.
 * 연출 = 메이플식 2D 그림 (JobFx — 눈앞 큰 베기 · 타격 그림 · 터짐 · 둥근 베기 · 돌진 줄기, 룬 연출의 문양 · 룬 글자는 쓰지 않음).
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
        JobSystem.register("spellblade_awaken", SpellbladeSkills::geyser); // 0.51.0 스펠 가이저 (id 그대로 — 강화 단계 기록)
        JobSystem.register("spellblade_ult", SpellbladeSkills::ult);
    }

    /** 이 스킬로 처음 맞힌 적이면 각인 하나 */
    private static void markOnce(ServerPlayer sp, LivingEntity e, Jobs.Job j, Set<UUID> marked) {
        if (marked.add(e.getUUID())) JobTraits.mark(sp, e, j);
    }

    // ------------------------------------------------------------ 비전 참격

    /**
     * 비전 참격: 칼에 마력을 둘러 앞 reach 칸 · arc 도 부채꼴 베기 (slash — 칼, 공격 피해) → delay 틱 뒤 벤 자리마다 마력 폭발 (burst — 둘레 burstRadius 칸,
     * 마력, 마법 피해, 적마다 한 번 · 벤 적이 없으면 칼끝 앞에서). 그림: 눈앞 큰 베기 · 앞으로 퍼지는 베기, 벤 자리에 타격 그림과 도는 고리 → 터짐 · 발밑 둥근 베기
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
        // 0.50.0 동작: 베며 앞으로 미끄러짐 (slide 칸 · 4틱 — 베는 자리 · 룬 폭발은 쓴 자리 기준 그대로)
        JobMove.run(sp, JobMove.to(0, 4, JobMove.ahead(sp, k.num("slide", 2), 0), JobMove.OUT));
        // 칼에 두른 마력 (손의 칼빛) → 눈앞 큰 베기 · 그 베기가 앞으로 퍼져 날아감
        RuneKit.Hand h = RuneKit.handOf(sp);
        RuneKit.rfx2(level, "sword", V.up(h.hand(), 0.3), Opt.of("dir", V.UP, "size", 0.55, "life", 0.18, "alpha", 0.8, "vel", RuneKit.bodyVel(sp)), glow, core);
        JobFx.slash(sp, j, 2.2, 0.35, 1.9, -8.0, 0.26);
        JobFx.wave(sp, j, V.up(V.add(eye, f, 3.0), -0.4), f, 8.0, 0.26, Opt.of("size", 1.7, "grow", 0.35, "spin", -8.0, "alpha", 0.75, "fade", 0.3)); // 0.50.0 참격 판
        SkillFx.play(level, "item.spear.attack_miss", eye, 1.15, 0.9);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", eye, 1.7, 0.45);
        List<LivingEntity> cut = RuneKit.coneTargets(sp, reach, k.num("arc", 100), 10);
        List<Vec3> centers = new ArrayList<>();
        int n = 0;
        for (LivingEntity e : cut) {
            Vec3 at = Kit.bodyPoint(e);
            centers.add(at);
            if (n++ < 8) {
                // 벤 자리: 타격 그림 + 터질 때까지 그 둘레를 도는 고리 (보는 사람 쪽을 봄)
                JobFx.hit(e, j, 1.0);
                JobFx.ringFacing(level, at, 0.55 + Kit.bodyPad(e) * 0.3, j, V.unit(V.sub(eye, at)), delay / 20.0, Opt.of("alpha", 0.9));
            }
            JobKit.strike(sp, e, j, P, k.num("slash", 3.8), false, false, life, 0, d -> markOnce(sp, e, j, marked));
        }
        if (centers.isEmpty()) {
            Vec3 tip = V.up(V.add(sp.position(), f, reach * 0.6), 1.0);
            centers.add(tip);
            JobFx.ringFacing(level, tip, 0.6, j, V.unit(V.sub(eye, tip)), delay / 20.0, Opt.of("alpha", 0.9));
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
                // 터지는 자리 (파티클 예산 6곳): 터짐 · 발밑 둥근 베기
                if (m++ < 6) {
                    JobFx.burst(d, c, burstR * 0.5, j, 0.36, Opt.empty());
                    JobFx.ring(d, V.up(c, -0.95), burstR * 0.9, j, m % 2 == 0, 0.32, Opt.of("alpha", 0.8));
                }
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
     * delay 틱 뒤 닿은 자리 둘레 radius 칸 마력 폭발 (blast, 마법 피해). 떠난 자리에 남보라 잔상 · 터짐, 길에는 마력 번개 · 돌진 줄기 (1인칭은 빠른 줄), 닿은 자리에 도는 둥근 베기가 그려지다 터짐.
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
        // 떠난 자리: 잔상 · 터짐 · 발밑 둥근 베기
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, glow, Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        JobFx.burst(level, V.up(start, 1.0), 0.9, j, 0.3, Opt.empty());
        JobFx.ring(level, V.up(start, 0.06), 1.1, j, false, 0.35, Opt.of("alpha", 0.85));
        // 길: 마력 번개 (허리 높이 · 닿은 자리 1.8칸 앞까지 — 1인칭 카메라 · 3인칭 카메라 가까이를 지나지 않게) + 돌진 줄기 (1인칭에서는 숨김) + 1인칭 빠른 줄
        Vec3 zapEnd = V.add(dest, f, -Math.min(1.8, len * 0.4));
        RuneKit.zap(level, V.up(start, 0.6), V.up(zapEnd, 0.6), glow, core, 0.18, 0.35);
        JobFx.dash(level, V.up(start, 0.9), V.up(zapEnd, 0.9), j, 0.35, JobFx.hide1p(sp));
        JobFx.speed(sp, j, 0.35, 0.9);
        SkillFx.play(level, "mob.endermen.portal", start, 1.4, 0.8);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", dest, 1.5, 0.5);
        double half = k.num("width", 1.5) / 2;
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(start, 1.0), f, len, half)) {
            LivingEntity e = h.e();
            JobKit.strike(sp, e, j, P, k.num("path", 2.55), true, false, life, 0, d -> {
                markOnce(sp, e, j, marked);
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.0);
            });
        }
        // 닿은 자리: 발밑에서 도는 둥근 베기가 delay 틱 동안 그려지다 터짐
        int delay = (int) k.num("delay", 10);
        double radius = k.num("radius", 3);
        JobFx.ring(level, V.up(dest, 0.06), radius * 0.9, j, true, delay / 20.0 + 0.1, Opt.of("alpha", 0.85));
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            // 터짐: 둘레 둥근 베기 · 몸 둘레 큰 터짐 (3인칭 · 다른 사람) · 1인칭 눈앞 터짐과 고리 (보는 쪽 2.8칸 — 화면 가운데 조금 아래) · 알갱이도 눈앞에서
            JobFx.ring(d, V.up(dest, 0.06), radius, j, false, 0.4, Opt.empty());
            JobFx.burst(d, V.up(dest, 1.0), radius * 0.6, j, 0.4, JobFx.hide1p(sp));
            Vec3 look = sp.getViewVector(1F);
            Vec3 ahead = V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), -0.3);
            JobFx.burst(d, ahead, 1.5, j, 0.42, JobFx.only1p(sp));
            JobFx.ringFacing(d, ahead, 1.1, j, V.scale(look, -1), 0.4, JobFx.only1p(sp));
            SkillFx.sparks(d, ahead, core, 8, 5);
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
     * 룬 검무: 쓴 자리 둘레 반지름 radius 칸 원 (dur 틱 — 그 자리에 남음) → 마력 검 swords 자루가 원을 따라 돌고, gap 틱마다 (laps 번) 원 안의 적을 벰
     * (lap — 짝수 번째 칼, 공격 피해 · 홀수 번째 마력, 마법 피해). 그림: 발밑 둥근 베기 (벨 때마다 다시 · 거꾸로 번갈아) · 벨 때 허리 높이 둥근 베기 · 1인칭 눈앞 고리
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
        // 0.50.0 동작: 칼을 세우고 떠올라 (lift 칸 · 6틱) 검무 동안 떠 있다가 내려앉음 (둘레 피해는 쓴 자리 기준 그대로)
        JobMove.run(sp, JobMove.to(0, 6, JobMove.up(sp, k.num("lift", 1)), JobMove.OUT), JobMove.hold(6, dur), JobMove.to(dur, dur + 6, JobMove.down(sp, c.y),
                JobMove.IN));
        // 0.50.0 룬 결계: 검무 동안 (guardFor 틱) 받는 피해 − guard
        if (k.num("guard", 0) > 0) {
            mmorpg.fabric.skill.Guards.set(sp, (int) k.num("guardFor", dur), k.num("guard", 0.2), null, null);
        }
        // 0.50.0 푸타르크 (사용자 "직업 고유 연출" — 룬 글자 띠 rfx_jrunes): 발밑 큰 고리 (검무의 범위) 와 몸 둘레 작은 고리 (룬 결계) 가 거꾸로 돌며 깜빡임
        for (int t = 0; t < dur; t += 10) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                JobFx.runeRing(lv, V.up(c, 0.14), r * 0.98, j, 10, tt * 4.5, 0.55, Opt.of("alpha", 0.85));
                if (k.num("guard", 0) > 0) JobFx.runeRing(lv, V.up(sp.position(), 1.1), 1.25, j, 6, -tt * 9.0, 0.55, JobFx.hide1p(sp).with("alpha", 0.9, "vel",
                        RuneKit.bodyVel(sp)));
            });
        }
        // 자리: 발밑에서 도는 둥근 베기 (벨 때마다 다시 — 거꾸로 번갈아) · 1인칭 눈앞 고리
        JobFx.ring(level, V.up(c, 0.06), r, j, false, gap / 20.0 + 0.2, Opt.of("alpha", 0.8));
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.45);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", c, 1.4, 0.8);
        SkillFx.play(level, "beacon.activate", c, 1.7, 0.6);
        // 0.47.0 입체: 원을 따라 도는 마력 검 (칼끝이 도는 쪽 · 넓은 면이 보는 사람 쪽 — 쓴 자리에 남음), 위아래로 너울거림
        int[] ids = JobFx3d.orbitAt(level, "phantom_sword", swords, V.up(c, 1.0), r * 0.9, Math.toDegrees(0.21 * 20), dur / 20.0 + 0.1, 0.9, glow, core, Opt.of("rune", 1.0, "bob",
                0.25, "bobHz", 0.9, "fadeIn", 0.25, "fadeOut", 0.3, "shine", 0.3));
        JobFx3d.guard(sp, life, ids, dur);
        SkillFx.every(laps, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            boolean magic = i % 2 == 1;
            // 벨 때마다: 허리 높이 둥근 베기 (거꾸로 번갈아) · 발밑 고리
            JobFx.ring(level, V.up(c, 1.0), r * 0.92, j, magic, 0.3, Opt.empty());
            if (i > 0) JobFx.ring(level, V.up(c, 0.06), r, j, magic, gap / 20.0 + 0.2, Opt.of("alpha", 0.75));
            SkillFx.play(level, magic ? "chime.amethyst_block" : "entity.player.attack.sweep", c, magic ? 1.4 : 1.1 + i * 0.1, 0.9);
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, r, 3)) {
                JobKit.strike(sp, e, j, P, lap, magic, false, life, 0, d -> {
                    markOnce(sp, e, j, marked);
                    if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                    if (magic) JobFx.burst(lv, Kit.bodyPoint(e), 0.8 + Kit.bodyPad(e) * 0.4, j, 0.3, Opt.empty());
                    else JobFx.hit(e, j, 1.0);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스펠 가이저

    /**
     * 스펠 가이저 (2026-11-05 사용자 "구지 신체 강화 스킬도 필수로 안넣어도 될거 같다" · "직업 중에 구지 강화 시키면 어색한 애들은 빼자" · "딜링기도 괜찮고",
     * 스펠 인챈트 대신 — 롤 브랜드 W): 칼을 거꾸로 쥐어 두 손으로 들었다가 plantAt 틱에 앞 ahead 칸 땅에 꽂음 — 둘레 radius 칸 룬 원 (circle · root 틱 묶음 — 보스는
     * 느려짐 · 각인) → 원의 룬 글자가 떠오르며 빛이 가운데로 모임 → eruptAt 틱에 원에서 거대한 마력 기둥이 솟구침 (height 칸 — pillar 큰 숫자 · 띄움 · 각인). 스킬
     * id 는 그대로 (spellblade_awaken — 강화 단계 기록)
     */
    static boolean geyser(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int plantAt = Math.max(2, (int) k.num("plantAt", 6));
        int eruptAt = Math.max(plantAt + 10, (int) k.num("eruptAt", 18));
        double r = k.num("radius", 3.5);
        double height = k.num("height", 12);
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 a = V.add(c0, f, k.num("ahead", 2.2));
        Vec3 g = new Vec3(a.x, RuneKit.groundAt(level0, V.up(a, 0.6), c0.y), a.z);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        // 듦: 거꾸로 쥔 칼끝에 마력 · 꽂힐 자리의 옅은 룬 원 (경고)
        JobFx.charge(sp, j, new Vec3(0.0, 2.3, 0.35), plantAt, 0.8);
        JobFx.runeRing(level0, V.up(g, 0.08), r, j, 9, Kit.rand() * 40, plantAt / 20.0 + 0.05, Opt.of("alpha", 0.45));
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", c0, 1.4, 0.6);
        RuneKit.later(plantAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 꽂음: 룬 원 · 퍼지는 고리 · 충격파 · 돌 · 1인칭 세로 베기 — 둘레 적 circle · 묶음
            JobFx.runeRing(lv, V.up(g, 0.09), r, j, 9, Kit.rand() * 40, (eruptAt - plantAt) / 20.0 + 0.25, Opt.of("alpha", 1.0));
            JobFx.ring(lv, V.up(g, 0.07), r, j, false, 0.45, Opt.of("alpha", 0.95));
            SkillFx.shock(lv, V.up(g, 0.1), JobFx.outer(j), r * 0.9, 0.4);
            JobFx.burst(lv, V.up(g, 0.5), 1.4, j, 0.3, Opt.empty());
            RuneKit.rockBurst(lv, V.up(g, 0.2), 6, 4.0, 0.15);
            JobFx.slash(sp, j, 1.9, 0.4, 2.1, 92.0, 0.22);
            SkillFx.play(lv, "block.anvil.land", g, 1.6, 0.5);
            SkillFx.play(lv, "block.amethyst_block.resonate", g, 0.8, 1.0);
            int root = (int) k.num("root", 10);
            for (LivingEntity e : Kit.around(sp, g, r, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("circle", 6), true, false, life, 0, x -> {
                    markOnce(sp, e, j, marked);
                    JobFx.hit(e, j, 1.0);
                    RuneKit.effect(e, "slowness", root, JobKit.isBoss(e) ? 1 : 10);
                });
            }
        });
        // 모음 (plantAt ~ eruptAt): 원에서 떠오르는 룬 글자 · 가운데로 빨려 드는 빛 · 땅울림
        for (int t = plantAt + 2; t < eruptAt; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double q = (tt - plantAt) / (double) (eruptAt - plantAt);
                double ang = Kit.rand() * Math.PI * 2;
                Vec3 p = V.add(g, new Vec3(Math.cos(ang), 0, Math.sin(ang)), r * (0.4 + Kit.rand() * 0.5));
                JobFx.glyph(lv, V.up(p, 0.6 + q), 0.5, (int) (Kit.rand() * 3), 0, 4, 0.35, false, Opt.of("vel", new Vec3(0, 2.0, 0)), j);
                JobFx.line(lv, V.up(p, 0.2), V.up(g, 0.3 + q * 0.8), j, 0.15, Opt.of("alpha", 0.7));
                SkillFx.sparks(lv, V.up(g, 0.3), JobFx.inner(j), 3 + (int) (q * 5), 2.0 + q * 2);
                SkillFx.play(lv, "block.respawn_anchor.charge", g, 0.8 + q * 0.6, 0.35);
            });
        }
        RuneKit.later(eruptAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 솟구침: 하늘까지 닿는 마력 기둥 (빛기둥 · 굵은 광선) · 둘레에서 위로 솟는 큰 검기 여섯 · 터짐 · 고리 · 충격파 · 섬광 — 둘레 적 pillar · 띄움
            RuneKit.godRays(sp, lv, g, r * 0.85, 10, glow, core, Opt.of("h", height, "life", 0.9, "alpha", 0.95, "skipNear", true));
            JobFx.beam(lv, V.up(g, 0.2), V.up(g, height), j, 3.2, 0.45, Opt.of("alpha", 1.0));
            JobFx.beam(lv, V.up(g, 0.2), V.up(g, height), j, 7.0, 0.5, Opt.of("alpha", 0.55)); // 겉 기둥 (영상 1: 기둥이 가는 두 줄로만 보였음)
            for (int q = 0; q < 6; q++) {
                double ang = Math.toRadians(q * 60 + Kit.rand() * 20);
                Vec3 p = V.add(g, new Vec3(Math.cos(ang), 0, Math.sin(ang)), r * 0.6);
                // 2026-11-14 사용자 도감 메모 "참격이 위로 보이게 수정": 세로 (90° — 옆을 보는 "(") → 가로 (0° — 위로 불룩한 초승달이 솟음)
                JobFx.bigWave(lv, null, V.up(p, 0.4), V.UP, 18.0, 1.6, (Kit.rand() - 0.5) * 20, 0.4, j, Opt.empty());
            }
            JobFx.burst(lv, V.up(g, 1.2), 3.0, j, 0.45, Opt.empty());
            JobFx.ring(lv, V.up(g, 0.08), r, j, true, 0.45, Opt.of("alpha", 1.0));
            SkillFx.shock(lv, V.up(g, 0.1), JobFx.outer(j), r * 1.2, 0.5);
            RuneKit.rockBurst(lv, V.up(g, 0.3), 10, 6.5, 0.18);
            SkillFx.sparks(lv, V.up(g, 1.5), JobFx.inner(j), 24, 8.0);
            Ult.flash(lv, g, glow, Opt.of("radius", 6.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
            SkillFx.play(lv, "random.explode", g, 0.9, 0.8);
            SkillFx.play(lv, "beacon.activate", g, 1.6, 0.8);
            SkillFx.play(lv, "item.trident.thunder", g, 1.5, 0.6);
            for (LivingEntity e : Kit.around(sp, g, r + 0.3, 3.0)) {
                JobKit.strike(sp, e, j, P, k.num("pillar", 14.4), true, true, life, 0, x -> {
                    markOnce(sp, e, j, marked);
                    JobFx.hit(e, j, 1.5);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, 1.0, 0);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 비전 거검

    /**
     * 비전 거검: 남보라 하늘 · 발밑에서 크게 도는 둥근 베기 · 둘레 빛기둥 → 눈앞에 거대한 마력 검이 솟음 (rise 틱) → gap 틱마다 가로 · 세로 · 대각 베기
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
        // 발동: 발밑에서 크게 도는 둥근 베기 둘 (거꾸로) · 둘레에서 솟는 빛기둥 여섯 · 몸에서 솟는 빛 · 1인칭 눈앞 고리
        JobFx.ring(level0, V.up(c0, 0.06), 5.5, j, false, rise / 20.0 + 0.3, Opt.of("alpha", 0.85));
        JobFx.ring(level0, V.up(c0, 0.08), 3.4, j, true, rise / 20.0 + 0.2, Opt.of("alpha", 0.7));
        RuneKit.godRays(sp, level0, c0, 5.0, 6, glow, core, Opt.of("h", 12.0, "life", rise / 20.0 + 0.4, "alpha", 0.7, "skipNear", true));
        JobFx.aura(sp, j, 2.8, rise / 20.0 + 0.4, 0.9);
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        // 0.50.0 하늘의 룬 아홉 (재설계표 — rfx_jninerunes): 솟는 동안 앞 하늘에 아홉 글자가 위로 굽은 줄로 차례로 켜지고 → 베는 동안 모두 번쩍이며 이어짐
        JobFx.nineRunes(level0, V.up(V.add(c0, Kit.facingOf(sp), 4.0), 7.2), 3.2, j, rise / 20.0, (thrustTick - rise) / 20.0 + 0.2, Opt.of("alpha", 0.95));
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", c0, 1.0, 1.0);
        SkillFx.play(level0, "beacon.power", c0, 1.2, 0.8);
        // 0.50.0 동작 (사용자 "역동적으로"): 거검과 함께 솟아올라 (rise 틱 · lift 칸) 공중에서 세 번 베고, 찌르기 직전에 조준한 쪽으로 내리꽂힘 (착지 → 찌르기)
        int dive = (int) k.num("dive", 6);
        JobMove.run(sp, JobMove.to(0, rise, JobMove.up(sp, k.num("lift", 3)), JobMove.OUT), JobMove.hold(rise, thrustTick - dive - 1),
                JobMove.to(thrustTick - dive - 1, thrustTick - 1, JobMove.landAhead(sp, k.num("diveDist", 2), c0.y), JobMove.IN));
        // 2026-11-13 3D 거검 (직업 도감 메모 "검 연출을 3d로" — 환영 검 판 대신): 솟음 → 세 번 베기 → 찌르기를 틱마다 주인을 따라 (claymore)
        claymore(sp, j, k, life, rise, gap, slashes.size(), thrustTick, c0);
        // 빨려 드는 빛 (솟는 동안)
        for (int t = 0; t < rise; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 f = Kit.facingOf(sp);
                double q = Math.min(1, (tt + 2.0) / rise);
                Vec3 hilt = V.up(V.add(sp.position(), f, 2.6), 0.4);
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
            int next = i + 1 < slashes.size() ? gap : after;
            RuneKit.later(rise + i * gap, () -> {
                if (RuneKit.sameLife(sp, life)) giantSlash(sp, j, P, slashes.get(ii), ii, range, half, glow, core, life, marked, next, c0.y);
            });
            JobKit.motionLater(sp, "spellblade_ult:slash" + (i % 3), rise + i * gap - 1, life); // 0.47.0 가로 · 세로 · 대각 베기 동작
        }
        RuneKit.later(thrustTick, () -> {
            if (RuneKit.sameLife(sp, life)) thrust(sp, j, k, P, range, glow, core, life, c0);
        });
        JobKit.motionLater(sp, "spellblade_ult:thrust", thrustTick - 4, life); // 0.47.0 당겼다가 깊게 찌르는 동작 (4틱 뒤 닿음)
        return true;
    }

    /** 2026-11-13 찌르기 폭발 자리: 쓴 자리 (c0) 의 몸 높이에서 보는 쪽으로 thrustAt + diveDist 칸 (벽까지) — 3D 거검의 칼끝 · 폭발이 같은 자리 */
    private static Vec3 thrustPoint(ServerPlayer sp, Jobs.Skill k, Vec3 c0) {
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = new Vec3(c0.x, Math.min(c0.y, sp.getY()) + 1.0, c0.z);
        double reach = Math.max(1, RuneKit.rayLength(sp.level(), from, f, k.num("thrustAt", 6) + k.num("diveDist", 2)));
        return V.add(from, f, reach);
    }

    /** 2026-11-13 아스트랄 클레이모어의 3D 거검 크기 (rune_sword — 길이 약 6.9칸, 예전 환영 검 판 3.4배와 비슷) */
    private static final double CLAY = 2.7;

    /**
     * 2026-11-13 아스트랄 클레이모어의 3D 거검 (직업 도감 메모 "검 연출을 3d로" — 환영 검 판 대신, 계열 색 반투명 빛 날 · 룬 홈): 틱마다 주인을 따라 —
     * 솟음 (0 ~ rise: 몸 앞 2.6칸 땅속에서 칼끝을 위로 솟으며 커짐) → 베기 (rise + i × gap: 몸 앞 2.2칸을 손잡이로 −25° → 105° 를 4틱 (처음에 빠르게) · 2틱 멈춤 ·
     * 3틱 동안 다시 세움 — 가로 · 세로 · 대각) → 찌르기 (thrustTick − 4: 몸 쪽으로 당겨 눕힘 → thrustTick: 칼끝이 폭발 자리까지 2틱) → 머물다 오그라들며 사라짐
     */
    private static void claymore(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, String life, int rise, int gap, int n, int thrustTick, Vec3 c0) {
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        int end = thrustTick + 16;
        int prep = thrustTick - 4;
        Vec3 f0 = Kit.facingOf(sp);
        Vfx.Fx fx = RuneProps.fx(sp.level(), sp.position(), end + 4);
        Vfx.Prop p = JobSword3d.sword(fx, V.up(V.add(sp.position(), f0, 2.6), -2.2), JobSword3d.HILT, JobSword3d.pose(V.UP, V.rightOf(f0)), CLAY * 0.3, glow, core);
        Vec3[] point = {null};
        fx.each(1, end, 1, a -> {
            if (!RuneKit.sameLife(sp, life) || sp.isRemoved()) {
                fx.end();
                return;
            }
            Vec3 f = Kit.facingOf(sp);
            Vec3 right = V.rightOf(f);
            Vec3 base = sp.position();
            Vec3 front = V.up(V.add(base, f, 2.6), 0.4);
            Quaternionf upright = JobSword3d.pose(V.UP, right);
            if (a <= rise) {
                double q = 1 - Math.pow(1 - a / (double) Math.max(1, rise), 2);
                p.to(V.up(front, -2.6 * (1 - q)), upright, CLAY * (0.3 + 0.7 * q), 1);
                return;
            }
            if (a < prep) {
                int i = Math.min(n - 1, (a - rise) / Math.max(1, gap));
                int t = a - (rise + i * Math.max(1, gap));
                if (t >= 9) {
                    p.to(front, upright, CLAY, 1);
                    return;
                }
                Vec3 sw = switch (i % 3) {
                    case 0 -> right;
                    case 1 -> f;
                    default -> V.unit(V.add(right, f, 0.6));
                };
                double th = t < 4 ? -25 + 130 * (1 - Math.pow(1 - (t + 1) / 4.0, 2)) : t < 6 ? 105 : 105 * (1 - (t - 5) / 3.0);
                double r = Math.toRadians(th);
                Vec3 blade = V.add(V.scale(V.UP, Math.cos(r)), sw, Math.sin(r));
                Vec3 edge = V.add(V.scale(V.UP, -Math.sin(r)), sw, Math.cos(r));
                Vec3 pivot = V.up(V.add(base, f, 2.2), 0.25);
                p.to(t >= 6 ? V.lerp(pivot, front, (t - 5) / 3.0) : pivot, JobSword3d.pose(blade, edge), CLAY, 1);
                return;
            }
            Vec3 hilt0 = V.up(V.add(base, f, 0.4), 1.1);
            if (a < thrustTick) {
                // 당겨 눕힘 (3틱 동안 돌고 그 뒤로는 자리만 따라감)
                if (a == prep) p.to(hilt0, JobSword3d.pose(f, right), CLAY, 3);
                else p.move(hilt0, 1);
                return;
            }
            if (a == thrustTick) {
                point[0] = thrustPoint(sp, k, c0);
                Vec3 d = V.unit(V.sub(point[0], hilt0));
                p.to(V.add(point[0], d, -JobSword3d.REACH * CLAY), JobSword3d.pose(d, right), CLAY, 2);
                return;
            }
            if (a == thrustTick + 9) p.size(CLAY * 0.08, 6);
        });
        fx.on(end, p::remove);
    }

    /** 거대한 검의 베기 하나 (i = 0 가로 · 1 세로 · 2 대각): 검이 휘둘러지는 세 장 · 거대한 검기 · 화면을 가르는 빛, 앞 range 칸 · 폭 half × 2 의 적 */
    private static void giantSlash(ServerPlayer sp, Jobs.Job j, double P, double mult, int i, double range, double half, double[] glow, double[] core, String life,
            Set<UUID> marked, int untilNext, double groundY) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Vec3 eye = sp.getEyePosition();
        // 0.47.0 사진첩: 코등이를 축으로 돌던 검은 손잡이가 1인칭 눈앞을 가렸음 → 끝 보석 바로 위를 쥐고 (몸 앞 2.2칸 · 낮게) 돎
        Vec3 pommel = V.up(V.add(sp.position(), f, 2.2), 0.25);
        // 베는 면의 두 축: 가로 = (위 → 오른쪽), 세로 = (위 → 앞), 대각 = (위 → 오른쪽 아래 비스듬히)
        Vec3 sweep = switch (i % 3) {
            case 0 -> right;
            case 1 -> f;
            default -> V.unit(V.add(right, f, 0.6));
        };
        // 2026-11-13 거대한 검의 휘두름은 3D 거검 (claymore — 같은 손잡이 자리 · 같은 베는 면) 이 맡음
        Vec3 cutDir = switch (i % 3) {
            case 0 -> right;
            case 1 -> V.UP;
            default -> V.unit(V.add(right, V.UP));
        };
        double spin = i % 3 == 0 ? -4.0 : i % 3 == 1 ? 90.0 : 45.0;
        JobFx.slash(sp, j, 3.0, 0.2, 3.0, spin, 0.3);
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(eye, f, 3.4), -0.3), Opt.of("dir", cutDir, "size", 4.6, "thick", 0.3, "life", 0.4, "delay", 0.05, "color", glow,
                "alpha", 1.0));
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(eye, f, 3.35), -0.3), Opt.of("dir", cutDir, "size", 4.3, "thick", 0.1, "life", 0.36, "delay", 0.05, "color",
                WHITE, "alpha", 1.0));
        SkillFx.play(level, "item.trident.thunder", eye, 1.3 + i * 0.15, 0.6);
        SkillFx.play(level, "item.spear.attack_miss", eye, 0.8 + i * 0.1, 1.0);
        RuneKit.shakeAround(level, sp.position(), 10, 0.15, 0.2);
        Vec3 from = new Vec3(sp.getX(), groundY + 1.0, sp.getZ()); // 0.50.0 공중에서 베어도 땅 높이의 줄 (떠 있는 동안에도 땅의 적을 벰)
        double len = Math.max(1, RuneKit.rayLength(level, from, f, range));
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, half)) {
            LivingEntity e = h.e();
            JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                markOnce(sp, e, j, marked);
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.3);
            });
        }
    }

    /** 찌르기 폭발 + 각인 터뜨림 (비전 거검 마지막) */
    private static void thrust(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double range, double[] glow, double[] core, String life, Vec3 c0) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        // 0.50.0 찌르기는 쓴 자리 (c0) 에서 앞으로 — 내리꽂힌 거리 (diveDist) 만큼 더 멀리 (내려앉은 자리 뒤의 적도 찌르는 길에 들게)
        Vec3 from = new Vec3(c0.x, Math.min(c0.y, sp.getY()) + 1.0, c0.z);
        Vec3 point = thrustPoint(sp, k, c0);
        double reach = Math.max(1, V.len(V.sub(point, from)));
        Vec3 ground = new Vec3(point.x, RuneKit.groundAt(level, point, sp.getY()), point.z);
        double radius = k.num("thrustRadius", 4);
        double markBurst = j.trait() == null ? 1.0 : j.trait().def().numOr("markBurst", 1.0);
        // 거대한 검이 앞으로 뻗어 찌름 (손잡이 눈앞 1.8칸 → 칼끝이 폭발 자리)
        Vec3 hilt = V.up(V.add(sp.position(), f, 1.8), 1.0);
        Vec3 dv = V.unit(V.sub(point, hilt));
        double lv0 = Math.max(1, V.len(V.sub(point, hilt)));
        // 2026-11-13 거대한 검의 찌르기는 3D 거검 (claymore — 칼끝이 같은 폭발 자리 thrustPoint 에 닿음) 이 맡음
        RuneKit.flyStreak(level, hilt, point, 60, glow, core, 1.2, 0.45);
        // 폭발: 큰 터짐 · 둘레 둥근 베기 둘 (거꾸로) · 빛기둥 · 번쩍임
        JobFx.burst(level, point, 2.6, j, 0.45, Opt.empty());
        JobFx.ring(level, V.up(ground, 0.06), radius, j, false, 0.4, Opt.empty());
        RuneKit.later(2, () -> JobFx.ring(level, V.up(ground, 0.1), radius * 0.6, j, true, 0.36, Opt.empty()));
        SkillFx.sparks(level, point, core, 14, 7);
        RuneKit.later(3, () -> RuneKit.godRays(sp, level, ground, 0, 1, glow, core, Opt.of("h", 16.0, "life", 0.8, "skipNear", true)));
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
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), ground), k.num("knock", 0.8), 0.45); // 0.50.0 밀쳐냄 (유틸 −5%)
                if (marks > 0 && e.level() instanceof ServerLevel lv) markBlast(sp, lv, e, marks, j);
            });
        }
        // 찌르기에 닿지 않은 각인 찍힌 적 (range + radius 칸 안): 그 자리에서 터짐
        for (LivingEntity e : JobKit.near(sp, range + radius, 32)) {
            if (hit.containsKey(e.getUUID())) continue;
            int marks = JobTraits.takeAllMarks(sp, e);
            if (marks <= 0) continue;
            JobKit.strike(sp, e, j, P, markBurst * marks, true, false, life, 0, d -> {
                if (e.level() instanceof ServerLevel lv) markBlast(sp, lv, e, marks, j);
            });
        }
    }

    /** 각인이 터짐: 각인 수만큼 겹쳐 커지는 터짐 · 타격 그림 · "◈×n" */
    private static void markBlast(ServerPlayer sp, ServerLevel level, LivingEntity e, int marks, Jobs.Job j) {
        if (!RuneKit.fxQuota("jobMarkBlast", 6)) return;
        Vec3 b = Kit.bodyPoint(e);
        for (int m = 0; m < Math.min(3, marks); m++) {
            int mm = m;
            RuneKit.later(m * 2, () -> JobFx.burst(level, V.up(b, 0.25 * mm), 0.8 + 0.25 * mm + Kit.bodyPad(e) * 0.4, j, 0.32, Opt.empty()));
        }
        JobFx.hit(e, j, 1.3);
    }
}
