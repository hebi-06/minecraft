package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
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
 * 0.51.0 2차 직업 — 마검사 각성 뵐룬드(룬 나이트) 스킬 (jobs.json advance runeknight — 보라 마력 · 금빛 룬, 특성 「마력 각인」 최대 5 · 터지면 번짐 — JobTraits ·
 * JobTraits7): 룬 새김 베기 · 세이드 섬광 · 푸타르크 대검무 · 룬 담금질 · 스물네 룬의 대검. 1차 마검사 컨셉 (마력 검 · 룬 · 각인) 그대로, 새 동작 · 새 그림
 * (공중 룬 글자 rfx_jglyph · 하늘의 룬 스물넷 rfx_jrunes24). 몸 쓰는 직업이 아니라 마력 검 · 룬이 스스로 날고 터짐. 스킬이 처음 맞힌 적마다 각인 하나 (한 번 쓸 때
 * 적마다 하나 — JobTraits.mark).
 */
final class RuneknightSkills {
    private RuneknightSkills() {}

    private static final double[] WHITE = RuneKit.WHITE;
    /** 세이드 섬광 잔상 몸 (짙은 보라) */
    private static final double[] SHADE = {0.32, 0.16, 0.52};
    /** 룬 새김 베기: 세 번 베기의 기울기 (오른쪽 위 → 왼쪽 아래 · 되돌려 · 가로) */
    private static final double[] CARVE_TILT = {-28.0, 205.0, -4.0};

    static void register() {
        JobSystem.register("runeknight_carve", RuneknightSkills::carve);
        JobSystem.register("runeknight_seid", RuneknightSkills::seid);
        JobSystem.register("runeknight_futhark", RuneknightSkills::futhark);
        JobSystem.register("runeknight_temper", RuneknightSkills::temper);
        JobSystem.register("runeknight_ult", RuneknightSkills::ult);
    }

    /** 이 스킬로 처음 맞힌 적이면 각인 하나 */
    private static void markOnce(ServerPlayer sp, LivingEntity e, Jobs.Job j, Set<UUID> marked) {
        if (marked.add(e.getUUID())) JobTraits.mark(sp, e, j);
    }

    // ------------------------------------------------------------ 룬 새김 베기

    /**
     * 룬 새김 베기: 앞으로 slide 칸 미끄러지며 (cutGap 틱마다) 세 번 벰 — 앞 reach 칸 · arc 도 부채꼴 (cuts — 한 번에 넣고 숫자 split 줄) · 벨 때마다 앞 공중에 룬 글자
     * 하나가 새겨짐 → thrustAt 틱에 찔러 글자 셋을 터뜨림 (burst — 글자 둘레 burstRadius 칸, 마법 피해 · 적마다 한 번). 그림: 눈앞 큰 베기 셋 · 글자 (새김 → 빛남 →
     * 번쩍 · 터짐) · 찌르는 줄기 · 글자마다 터짐과 고리
     */
    static boolean carve(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        String life = RuneKit.lifeToken(sp);
        int gap = Math.max(2, (int) k.num("cutGap", 4));
        int thrustAt = Math.max(gap * 2 + 6, (int) k.num("thrustAt", 16));
        double reach = k.num("reach", 4.2);
        double burstR = k.num("burstRadius", 2.6);
        Set<UUID> marked = new HashSet<>();
        double slide = RuneKit.dashDistance(sp, f, k.num("slide", 1.5), 0.3, 1.5);
        JobMove.run(sp, JobMove.to(0, gap * 2, JobMove.ahead(sp, slide, 0), JobMove.OUT));
        // 글자 자리 (쓴 자리 기준 — 미끄러진 뒤 앞 공중): 왼쪽 아래 · 가운데 위 · 오른쪽 아래 (삼각)
        Vec3 c0 = sp.position();
        Vec3[] glyphAt = {V.up(V.add(V.add(c0, f, slide + 1.4), right, -0.95), 1.45), V.up(V.add(c0, f, slide + 1.8), 2.15),
                V.up(V.add(V.add(c0, f, slide + 1.4), right, 0.95), 1.45)};
        for (int c = 0; c < 3; c++) {
            int cc = c;
            RuneKit.later(c * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                JobFx.slash(sp, j, 2.2, 0.3, 1.9, CARVE_TILT[cc], 0.24);
                // 새김 (0 ~ 1 — 획이 그어짐) → 빛남 (2 ~ 3 이어 돌림 — 찌를 때까지)
                JobFx.glyph(lv, glyphAt[cc], 0.85, cc, 0, 2, 0.16, false, Opt.of("alpha", 0.95), j); // 영상 1: 0.62 는 3인칭에서 머리보다 작아 잘 안 보였음
                RuneKit.later(3, () -> JobFx.glyph(lv, glyphAt[cc], 0.85, cc, 2, 2, Math.max(0.1, (thrustAt - cc * gap - 3) / 20.0), true, Opt.of("alpha", 0.95), j));
                SkillFx.sparks(lv, glyphAt[cc], JobFx.inner(j), 5, 2.2);
                SkillFx.play(lv, "item.spear.attack_miss", sp.getEyePosition(), 1.15 + cc * 0.1, 0.85);
                SkillFx.play(lv, "block.amethyst_block.chime", glyphAt[cc], 1.3 + cc * 0.2, 0.7);
            });
        }
        // 세 번 베기의 피해: 한 번에 넣고 숫자 세 줄 (cutGap 틱마다 한 줄 — 베기와 같은 때)
        int split = Math.max(1, (int) k.num("split", 3));
        int[] cnt = {0};
        for (LivingEntity e : RuneKit.coneTargets(sp, reach, k.num("arc", 120), 10)) {
            JobKit.strikeSplit(sp, e, j, P, k.num("cuts", 3.6), split, gap, false, false, life, 0, d -> {
                markOnce(sp, e, j, marked);
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.0);
            });
        }
        JobKit.motionLater(sp, "runeknight_carve:thrust", thrustAt - 4, life);
        RuneKit.later(thrustAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 찌름: 칼끝에서 글자 가운데로 뻗는 줄기 · 글자마다 번쩍 → 터짐 · 둘레 고리
            Vec3 hand = RuneKit.handOf(sp).hand();
            Vec3 mid = V.scale(V.add(V.add(glyphAt[0], glyphAt[1]), glyphAt[2]), 1 / 3.0);
            JobFx.dash(lv, V.up(hand, 0.2), mid, j, 0.25, JobFx.hide1p(sp));
            JobFx.speed(sp, j, 0.22, 0.8);
            for (int c = 0; c < 3; c++) {
                Vec3 g = glyphAt[c];
                JobFx.glyph(lv, g, 1.0, c, 4, 2, 0.3, false, Opt.of("alpha", 1.0), j);
                JobFx.burst(lv, g, burstR * 0.45, j, 0.34, Opt.empty());
                JobFx.ringFacing(lv, g, burstR * 0.55, j, V.unit(V.sub(sp.getEyePosition(), g)), 0.3, Opt.of("alpha", 0.85));
            }
            JobFx.ring(lv, V.up(V.add(c0, f, slide + 1.5), 0.06), burstR * 1.1, j, false, 0.35, Opt.of("alpha", 0.8));
            SkillFx.sparks(lv, mid, JobFx.inner(j), 12, 5);
            SkillFx.play(lv, "random.explode", mid, 1.6, 0.45);
            SkillFx.play(lv, "chime.amethyst_block", mid, 1.0, 1.0);
            JobKit.shake(sp, 8, 0.15, 0.2);
            Map<UUID, LivingEntity> hit = new LinkedHashMap<>();
            for (Vec3 g : glyphAt) for (LivingEntity e : Kit.around(sp, V.up(g, -1.0), burstR, 3.0)) hit.putIfAbsent(e.getUUID(), e);
            int[] m = {0};
            for (LivingEntity e : hit.values()) {
                JobKit.strike(sp, e, j, P, k.num("burst", 4.9), true, false, life, 0, d -> {
                    markOnce(sp, e, j, marked);
                    if (m[0]++ < 8 && e.level() instanceof ServerLevel l2) JobFx.burst(l2, Kit.bodyPoint(e), 0.7 + Kit.bodyPad(e) * 0.4, j, 0.3, Opt.empty());
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 세이드 섬광

    /**
     * 세이드 섬광: 보는 쪽 dist 칸 (벽 앞까지 · 땅 높이) 으로 순간 이동 — 지나간 길 (폭 width) 의 적을 벰 (path, 마법) · 떠난 자리 A 와 닿은 자리 B 에 룬 원 →
     * returnAt 틱에 A 로 되돌아오며 길의 적을 한 번 더 (back) · B 의 룬 원이 터짐 (blast — 둘레 radius 칸) → homeAt 틱에 A 의 룬 원이 터짐 (home). 묶였거나 앞이 막혀
     * 못 가면 쓰지 않음. 되돌아올 자리가 막혔으면 그 자리에 남음
     */
    static boolean seid(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 end = blinkTo(sp, level, start, f, k.num("dist", 7));
        if (end == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        Vec3 dest = end;
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        int returnAt = Math.max(6, (int) k.num("returnAt", 10));
        int homeAt = Math.max(returnAt + 6, (int) k.num("homeAt", 20));
        double radius = k.num("radius", 3);
        double half = k.num("width", 1.5) / 2;
        Kit.noFall(sp, homeAt + 30);
        blinkFx(sp, level, start, dest, f, j);
        pathHits(sp, j, P, k.num("path", 1.2), start, dest, half, life, marked);
        // 룬 원 둘 (A 떠난 자리 · B 닿은 자리): 발밑 룬 띠가 돌며 그려짐 · 가운데 떠 있는 글자 (터질 때까지)
        runeCircle(level, start, radius, j, homeAt / 20.0, 0);
        runeCircle(level, dest, radius, j, returnAt / 20.0, 1);
        RuneKit.later(returnAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 now = sp.position();
            // V.flat 은 수평 단위 방향 (길이 1) — 되돌아갈 거리는 hdist (CI 41306605712: 길이를 V.len(V.flat(…)) 로 재어 1칸만 되돌아왔음)
            double backLen = V.hdist(start, now);
            Vec3 back = V.flat(V.sub(start, now));
            Vec3 home = backLen > 0.5 ? blinkTo(sp, d, now, back, backLen) : null;
            JobKit.motion(sp, "runeknight_seid:back");
            if (home != null) {
                blinkFx(sp, d, now, home, back, j);
                pathHits(sp, j, P, k.num("back", 1.2), now, home, half, life, marked);
            }
            circleBlast(sp, d, dest, radius, j, P, k.num("blast", 1.35), life, marked, 1);
        });
        RuneKit.later(homeAt, () -> {
            if (RuneKit.sameLife(sp, life)) circleBlast(sp, sp.level(), start, radius, j, P, k.num("home", 1.35), life, marked, 0);
        });
        return true;
    }

    /** 보는 쪽 dir 으로 dist 칸 (벽 앞까지 · 땅 높이 — 그 자리가 막혔으면 길의 설 자리 중 가장 먼 곳) 으로 옮김: 닿은 자리 (못 가면 null) */
    private static Vec3 blinkTo(ServerPlayer sp, ServerLevel level, Vec3 start, Vec3 dir, double max) {
        double dist = RuneKit.dashDistance(sp, dir, max, 1.1, 1.8);
        if (dist <= 0.4) return null;
        Vec3 ahead = V.add(start, dir, dist);
        Vec3 at = new Vec3(ahead.x, RuneKit.groundAt(level, ahead, start.y), ahead.z);
        if (Kit.moveTo(sp, at)) return at;
        List<Kit.Spot> spots = Kit.dashSpots(level, start, dir, dist);
        for (int i = spots.size() - 1; i >= 0; i--) if (Kit.moveTo(sp, spots.get(i).p())) return spots.get(i).p();
        return null;
    }

    /** 순간 이동 그림: 떠난 자리 잔상 · 터짐, 길의 마력 번개 · 돌진 줄기 (1인칭은 빠른 줄) */
    private static void blinkFx(ServerPlayer sp, ServerLevel level, Vec3 from, Vec3 to, Vec3 f, Jobs.Job j) {
        double len = Math.max(0.5, V.hdist(from, to));
        RuneKit.shadeClone(sp, level, from, f, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.85));
        JobFx.burst(level, V.up(from, 1.0), 0.8, j, 0.3, Opt.empty());
        Vec3 zapEnd = V.add(to, f, -Math.min(1.8, len * 0.4));
        RuneKit.zap(level, V.up(from, 0.6), V.up(zapEnd, 0.6), JobKit.glow(j), JobKit.core(j), 0.16, 0.32);
        JobFx.dash(level, V.up(from, 0.9), V.up(zapEnd, 0.9), j, 0.32, JobFx.hide1p(sp));
        JobFx.speed(sp, j, 0.3, 0.9);
        SkillFx.play(level, "mob.endermen.portal", from, 1.5, 0.75);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", to, 1.6, 0.45);
    }

    /** 지나간 길 (from → to · 반 폭 half) 의 적을 벰 (마법) */
    private static void pathHits(ServerPlayer sp, Jobs.Job j, double P, double mult, Vec3 from, Vec3 to, double half, String life, Set<UUID> marked) {
        double len = V.hdist(from, to); // V.flat 은 단위 방향 — 길이는 hdist (CI 41306605712: 길이 1 로 재어 길의 적을 거의 못 벴음)
        if (len < 0.3) return;
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(from, 1.0), V.flat(V.sub(to, from)), len, half)) {
            LivingEntity e = h.e();
            JobKit.strike(sp, e, j, P, mult, true, false, life, 0, x -> {
                markOnce(sp, e, j, marked);
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.0);
            });
        }
    }

    /** 룬 원 (터질 때까지): 발밑 룬 띠 (돌며) · 둥근 베기가 그려짐 · 가운데 떠 있는 글자 kind */
    private static void runeCircle(ServerLevel level, Vec3 c, double radius, Jobs.Job j, double life, int kind) {
        JobFx.runeRing(level, V.up(c, 0.12), radius * 0.92, j, 10, Kit.rand() * 36, Math.max(0.2, life), Opt.of("alpha", 0.85));
        JobFx.ring(level, V.up(c, 0.06), radius * 0.95, j, kind == 1, Math.max(0.2, life + 0.05), Opt.of("alpha", 0.7));
        JobFx.glyph(level, V.up(c, 1.0), 0.7, kind, 0, 2, 0.15, false, Opt.of("alpha", 0.9), j);
        RuneKit.later(3, () -> JobFx.glyph(level, V.up(c, 1.0), 0.7, kind, 2, 2, Math.max(0.1, life - 0.15), true, Opt.of("alpha", 0.9), j));
    }

    /** 룬 원이 터짐: 글자 번쩍 · 둥근 베기 · 큰 터짐 · 1인칭 눈앞 터짐 (쓴 사람이 그 안이면) · 둘레 radius 칸 (마법) */
    private static void circleBlast(ServerPlayer sp, ServerLevel level, Vec3 c, double radius, Jobs.Job j, double P, double mult, String life, Set<UUID> marked,
            int kind) {
        JobFx.glyph(level, V.up(c, 1.0), 1.0, kind, 4, 2, 0.32, false, Opt.of("alpha", 1.0), j);
        JobFx.ring(level, V.up(c, 0.06), radius, j, false, 0.4, Opt.empty());
        JobFx.burst(level, V.up(c, 1.0), radius * 0.55, j, 0.4, JobFx.hide1p(sp));
        if (sp.position().distanceTo(c) < radius) {
            Vec3 look = sp.getViewVector(1F);
            Vec3 ahead = V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), -0.3);
            JobFx.burst(level, ahead, 1.4, j, 0.4, JobFx.only1p(sp));
        }
        SkillFx.sparks(level, V.up(c, 0.8), JobFx.inner(j), 10, 5);
        SkillFx.play(level, "random.explode", c, 1.5, 0.55);
        SkillFx.play(level, "chime.amethyst_block", c, 0.9, 1.0);
        for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
            JobKit.strike(sp, e, j, P, mult, true, false, life, 0, x -> {
                markOnce(sp, e, j, marked);
                RuneKit.knock(e, V.sub(e.position(), c), 0.3, 0.3);
            });
        }
    }

    // ------------------------------------------------------------ 푸타르크 대검무

    /**
     * 푸타르크 대검무: lift 칸 떠올라 finalAt 틱 동안 떠 있음 — 마력 검 swords 자루가 둘레 (radius × 0.85) 를 돌고, gap 틱마다 (laps — 짝수 칼 · 홀수 마력) 둘레
     * radius 칸을 벰 → finalAt 직전 검이 모두 둘레 땅에 내리꽂히고 내려앉으며 룬 원 폭발 (final — 둘레 finalRadius 칸, 마법). 검무 동안 룬 결계 (guardFor 틱 —
     * 받는 피해 −guard). 쓴 자리에 남는 검무 (장판)
     */
    static boolean futhark(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 4.2);
        List<Double> laps = SwordsmanSkills.numbers(k, "laps", List.of(2.4, 2.4, 2.4, 2.4));
        int gap = Math.max(10, (int) k.num("gap", 15));
        int finalAt = Math.max((laps.size() - 1) * gap + 10, (int) k.num("finalAt", 60));
        int swords = Math.max(3, (int) k.num("swords", 9));
        double finalR = k.num("finalRadius", 4.5);
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        JobMove.run(sp, JobMove.to(0, 8, JobMove.up(sp, k.num("lift", 2)), JobMove.OUT), JobMove.hold(8, finalAt - 4), JobMove.to(finalAt - 4, finalAt,
                JobMove.down(sp, c.y), JobMove.IN));
        if (k.num("guard", 0) > 0) mmorpg.fabric.skill.Guards.set(sp, (int) k.num("guardFor", finalAt), k.num("guard", 0.2), null, null);
        // 발밑 큰 룬 띠 (검무 범위) · 몸 둘레 작은 룬 띠 (결계) — 거꾸로 돌며 깜빡임
        for (int t = 0; t < finalAt; t += 10) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                JobFx.runeRing(lv, V.up(c, 0.14), r * 0.98, j, 12, tt * 4.5, 0.55, Opt.of("alpha", 0.85));
                JobFx.runeRing(lv, V.up(sp.position(), 1.1), 1.3, j, 6, -tt * 9.0, 0.55, JobFx.hide1p(sp).with("alpha", 0.9, "vel", RuneKit.bodyVel(sp)));
            });
        }
        JobFx.ring(level, V.up(c, 0.06), r, j, false, gap / 20.0 + 0.2, Opt.of("alpha", 0.8));
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.45);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", c, 1.3, 0.8);
        SkillFx.play(level, "beacon.activate", c, 1.6, 0.6);
        // 둘레를 도는 마력 검 아홉 (몸 높이 · 위아래로 너울거림 — 쓴 자리에 남음) → 끝에 땅으로
        int[] ids = JobFx3d.orbitAt(level, "phantom_sword", swords, V.up(c, 1.0 + k.num("lift", 2) * 0.6), r * 0.85, 260, (finalAt - 6) / 20.0, 0.95, JobFx.outer(j),
                JobFx.inner(j), Opt.of("rune", 1.0, "bob", 0.3, "bobHz", 1.1, "fadeIn", 0.2, "fadeOut", 0.05, "shine", 0.35));
        JobFx3d.guard(sp, life, ids, finalAt);
        SkillFx.every(laps.size(), gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            boolean magic = i % 2 == 1;
            ServerLevel lv = sp.level();
            JobFx.ring(lv, V.up(c, 1.0), r * 0.92, j, magic, 0.3, Opt.empty());
            JobFx.bladeRing(lv, V.up(c, 0.9 + 0.3 * (i % 2)), r * 0.9, j, 0.3, 20, Opt.of("alpha", 0.8));
            if (i > 0) JobFx.ring(lv, V.up(c, 0.06), r, j, magic, gap / 20.0 + 0.2, Opt.of("alpha", 0.75));
            SkillFx.play(lv, magic ? "chime.amethyst_block" : "entity.player.attack.sweep", c, magic ? 1.4 : 1.1 + i * 0.1, 0.9);
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, r, 3.5)) {
                JobKit.strike(sp, e, j, P, laps.get(i), magic, false, life, 0, d -> {
                    markOnce(sp, e, j, marked);
                    if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel l2)) return;
                    if (magic) JobFx.burst(l2, Kit.bodyPoint(e), 0.75 + Kit.bodyPad(e) * 0.4, j, 0.3, Opt.empty());
                    else JobFx.hit(e, j, 1.0);
                });
            }
        });
        // 검이 모두 땅에 내리꽂힘 (finalAt − 6 → finalAt − 1): 둘레 위 하늘에서 칼끝을 아래로 · 꽂힌 채 0.6초
        JobKit.motionLater(sp, "runeknight_futhark:plant", finalAt - 6, life);
        RuneKit.later(finalAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobFx3d.killAll(lv, ids, c, 0.08);
            for (int i = 0; i < swords; i++) {
                double a = Math.toRadians(i * 360.0 / swords + 20);
                Vec3 g = new Vec3(c.x + Math.cos(a) * r * 0.85, c.y, c.z + Math.sin(a) * r * 0.85);
                Vec3 ground = new Vec3(g.x, RuneKit.groundAt(lv, g, c.y), g.z);
                JobFx3d.swordFly(lv, V.up(ground, 3.4), V.up(ground, 0.15), 22, 0.95, 0.6, j, Opt.of("rune", 1.0, "shine", 0.4));
            }
            SkillFx.play(lv, "item.trident.riptide_1", c, 1.5, 0.6);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 룬 원 폭발: 꽂힌 검 자리마다 글자 번쩍 · 큰 룬 띠 · 땅의 터짐 · 솟는 빛
            for (int i = 0; i < swords; i += 2) {
                double a = Math.toRadians(i * 360.0 / swords + 20);
                Vec3 g = new Vec3(c.x + Math.cos(a) * r * 0.85, c.y + 1.0, c.z + Math.sin(a) * r * 0.85);
                JobFx.glyph(lv, g, 0.55, i % 3, 4, 2, 0.3, false, Opt.empty(), j);
            }
            JobFx.runeRing(lv, V.up(c, 0.16), finalR, j, 14, Kit.rand() * 30, 0.5, Opt.of("alpha", 1.0));
            JobFx.smash(lv, V.up(c, 0.05), finalR, j, 0.5, Opt.empty());
            JobFx.ring(lv, V.up(c, 0.08), finalR, j, false, 0.45, Opt.empty());
            RuneKit.godRays(sp, lv, c, finalR * 0.7, 6, JobKit.glow(j), JobKit.core(j), Opt.of("h", 7.0, "life", 0.6, "alpha", 0.7, "skipNear", true));
            SkillFx.sparks(lv, V.up(c, 0.6), JobFx.inner(j), 16, 6);
            SkillFx.play(lv, "random.explode", c, 1.2, 0.8);
            SkillFx.play(lv, "chime.amethyst_block", c, 0.8, 1.0);
            JobKit.shake(sp, 10, 0.25, 0.3);
            for (LivingEntity e : Kit.around(sp, c, finalR, 3.5)) {
                JobKit.strike(sp, e, j, P, k.num("final", 5.7), true, false, life, 0, d -> {
                    markOnce(sp, e, j, marked);
                    RuneKit.knock(e, V.sub(e.position(), c), 0.35, 0.35);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 룬 담금질

    /**
     * 룬 담금질: dur 틱 태세 — 칼 평타가 맞을 때마다 몸 둘레의 마력 검 swords 자루가 함께 벰 (JobTraits7 — 위력 × echo × swords) · 사거리 +reach (룬 대검).
     * 그림: 발밑 룬 불 고리 · 칼이 룬 불에 잠겼다 금빛으로 커짐 (손의 칼빛) · 담금질 김 · 둘레를 도는 마력 검 셋 (태세 동안 — 1인칭 숨김) · 망치 소리
     */
    static boolean temper(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 240);
        int swords = Math.max(1, (int) k.num("swords", 3));
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String life = RuneKit.lifeToken(sp);
        JobFx.runeRing(level, V.up(c, 0.12), 1.5, j, 8, 0, 0.9, Opt.of("alpha", 0.95));
        JobFx.ring(level, V.up(c, 0.06), 2.3, j, false, 0.5, Opt.of("alpha", 0.85));
        // 담금질 (0 ~ 10틱): 칼이 룬 불 속에서 달아오르며 커짐 (손의 칼빛 · 불티) → 10틱 김 (흰 연기) · 번쩍
        for (int t = 0; t < 12; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.Hand h = RuneKit.handOf(sp);
                double q = (tt + 2) / 12.0;
                RuneKit.rfx2(sp.level(), "sword", V.up(h.hand(), 0.3 + 0.35 * q), Opt.of("dir", V.UP, "size", 0.45 + 0.45 * q, "life", 0.14, "alpha", 0.9, "vel",
                        RuneKit.bodyVel(sp)), JobKit.core(j), JobKit.glow(j));
                SkillFx.fx(sp.level(), "rpg:arrow_spark", V.up(h.hand(), 0.2 + q * 0.8), Opt.of("dir", V.UP, "speed", 2.2, "size", 0.14, "grow", 1.0, "life", 0.4,
                        "color", tt % 4 == 0 ? JobKit.core(j) : JobKit.glow(j), "alpha", 1.0));
                if (tt % 4 == 0) SkillFx.play(sp.level(), "block.anvil.use", sp.position(), 1.4 + q * 0.4, 0.35);
            });
        }
        RuneKit.later(10, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.smoke(lv, V.up(h.hand(), 0.6), 0.6, WHITE, JobKit.core(j), 0.7, Opt.of("vel", new Vec3(0, 0.9, 0), "alpha", 0.6));
            JobFx.glint(lv, V.up(V.add(h.hand(), h.f(), 0.6), 0.6), 0.75, j, 0.4, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.aura(sp, j, 2.6, 0.8, 0.9);
            SkillFx.play(lv, "block.lava.extinguish", h.hand(), 1.2, 0.6);
            SkillFx.play(lv, "beacon.activate", sp.position(), 1.5, 0.7);
            Ult.flash(lv, sp.position(), JobKit.core(j), Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        });
        JobFx.eyeRing(sp, j, 2.4, 0.95, 0.5);
        // 태세 동안 몸 둘레를 도는 마력 검 셋 (허리 ~ 머리 · 칼끝이 가는 쪽, 1인칭에서는 숨김) — 평타마다 이 검들이 함께 벰
        int[] ids = JobFx3d.orbitOwner(sp, "phantom_sword", swords, new Vec3(0, 1.25, 0), 1.25, 120, false, dur / 20.0, 0.65, j, Opt.of("view", 1.0, "owner",
                (double) sp.getId(), "bob", 0.3, "bobHz", 0.5, "fadeIn", 0.3, "fadeOut", 0.5, "alpha", 0.92, "rune", 1.0));
        JobFx3d.hold(sp, JobTraits7.TEMPER, ids);
        JobFx3d.guard(sp, life, ids, dur);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", c, 1.5, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 스물네 룬의 대검

    /**
     * 스물네 룬의 대검: 보랏빛 하늘 · 발밑 큰 고리 → rise 틱 동안 lift 칸 솟으며 앞 하늘에 룬 스물넷이 넷씩 켜지고 거대한 마력 검이 솟음 → gap 틱마다 가로 · 세로 ·
     * 대각 베기 (slashes — 앞 range 칸 · 폭 width, 칼) → plungeAt 틱에 검이 앞 ahead 칸 땅에 내리꽂힘 (plunge — 둘레 plungeRadius 칸, 마법) → 검과 함께 내리꽂혀
     * finalAt 틱에 내려앉음 (final — 앞 둘레 finalRadius 칸 · 큰 숫자 · 밀쳐냄, 남은 각인을 모두 터뜨려 더함 · 둘레로 번짐). 닿지 않은 각인 찍힌 적도 그 자리에서 터짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> slashes = SwordsmanSkills.numbers(k, "slashes", List.of(10.0, 10.0, 10.0));
        double range = k.num("range", 10);
        double half = k.num("width", 4.5) / 2;
        int rise = Math.max(10, (int) k.num("rise", 20));
        int gap = Math.max(10, (int) k.num("gap", 10));
        int plungeAt = Math.max(rise + (slashes.size() - 1) * gap + 10, (int) k.num("plungeAt", 52));
        int finalAt = Math.max(plungeAt + 10, (int) k.num("finalAt", 64));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Set<UUID> marked = new HashSet<>();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, c0, "rpg:ult_arcane", finalAt + 30);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Kit.noFall(sp, finalAt + 40);
        // 발동: 발밑에서 크게 도는 둥근 베기 둘 (거꾸로) · 둘레 빛기둥 · 1인칭 눈앞 고리 (몸의 기운은 그리지 않음 — 바로 솟아 쓴 자리에 빈 불꽃 판만 남음)
        JobFx.ring(level0, V.up(c0, 0.06), 5.5, j, false, rise / 20.0 + 0.3, Opt.of("alpha", 0.85));
        JobFx.ring(level0, V.up(c0, 0.08), 3.4, j, true, rise / 20.0 + 0.2, Opt.of("alpha", 0.7));
        JobFx.runeRing(level0, V.up(c0, 0.12), 4.4, j, 14, 0, rise / 20.0 + 0.3, Opt.of("alpha", 0.9));
        RuneKit.godRays(sp, level0, c0, 5.0, 6, glow, core, Opt.of("h", 12.0, "life", rise / 20.0 + 0.4, "alpha", 0.7, "skipNear", true));
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        // 하늘의 룬 스물넷 (앞 하늘 — 솟은 눈높이에서 조금 위): 솟는 동안 넷씩 켜짐 → 베는 동안 별 무늬로 이어짐
        double lift = k.num("lift", 6);
        JobFx.runes24(level0, V.up(V.add(c0, f0, 9.0), lift + 2.6), 4.6, j, rise / 20.0, (finalAt - rise) / 20.0 + 0.2, Opt.of("alpha", 0.95));
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", c0, 0.9, 1.0);
        SkillFx.play(level0, "beacon.power", c0, 1.1, 0.8);
        // 동작: 거검과 함께 솟아올라 (rise 틱 · lift 칸) 공중에서 세 번 베고 · 검을 내리꽂은 뒤 · 조준한 쪽으로 검과 함께 내리꽂힘 (finalAt 에 땅)
        int dive = 6;
        JobMove.run(sp, JobMove.to(0, rise, JobMove.up(sp, lift), JobMove.OUT), JobMove.hold(rise, finalAt - dive - 1), JobMove.to(finalAt - dive - 1, finalAt - 1,
                JobMove.landAhead(sp, k.num("diveDist", 2.5), c0.y), JobMove.IN));
        // 솟아오르는 거대한 마력 검 (눈앞 2.6칸 — 주인을 따라가며 rise 틱에 0.9 → 3.4 배)
        JobFx3d.spawn(level0, "phantom_sword", new Vec3(0, 0.4, 2.6), j, Opt.of("follow", (double) sp.getId(), "to", new Vec3(0, 0.4, 2.6), "ctrl", new Vec3(0, 0.2, 2.6),
                "travel", rise / 20.0, "dir", V.UP, "face", new Vec3(0, 0, 1), "scale", 0.9, "grow", 3.4 / 0.9 - 1, "life", rise / 20.0, "fadeIn", 0.2, "fadeOut", 0.0,
                "glowAlpha", 0.6, "shine", 0.35));
        for (int t = 0; t < rise; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 f = Kit.facingOf(sp);
                double q = Math.min(1, (tt + 2.0) / rise);
                Vec3 hilt = V.up(V.add(sp.position(), f, 2.6), 0.4);
                for (int sd : new int[] {-1, 1}) {
                    Vec3 a = V.add(V.up(hilt, 1 + q * 2.5), V.rightOf(f), sd * (2.6 - q * 1.6));
                    SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.scale(V.rightOf(f), -sd), "speed", 3.0, "size", 0.16, "grow", 0.5, "life", 0.25, "color",
                            core, "alpha", 1.0));
                }
                if (tt % 6 == 0) SkillFx.play(level, "block.bell.hit", hilt, 1.0 + q, 0.35);
            });
        }
        for (int i = 0; i < slashes.size(); i++) {
            int ii = i;
            int next = i + 1 < slashes.size() ? gap : plungeAt - (rise + i * gap);
            RuneKit.later(rise + i * gap, () -> {
                if (RuneKit.sameLife(sp, life)) giantSlash(sp, j, P, slashes.get(ii), ii, range, half, glow, core, life, marked, next, c0.y);
            });
            JobKit.motionLater(sp, "runeknight_ult:slash" + (i % 3), rise + i * gap - 1, life);
        }
        // 검이 내리꽂힘: 앞 ahead 칸 땅 (쓴 자리 기준) 으로 하늘에서 칼끝부터 (6틱 동안 떨어짐)
        Vec3 point = V.add(c0, f0, k.num("ahead", 6));
        Vec3 ground = new Vec3(point.x, RuneKit.groundAt(level0, point, c0.y), point.z);
        JobKit.motionLater(sp, "runeknight_ult:plunge", plungeAt - 6, life);
        RuneKit.later(plungeAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx3d.swordFly(sp.level(), V.up(ground, 11), V.up(ground, 0.2), 33, 3.2, 0.7, j, Opt.of("rune", 1.0, "shine", 0.5, "glowAlpha", 0.6));
            SkillFx.play(sp.level(), "item.trident.riptide_3", ground, 1.2, 0.8);
        });
        RuneKit.later(plungeAt, () -> {
            if (RuneKit.sameLife(sp, life)) plunge(sp, j, k, P, ground, life, marked);
        });
        JobKit.motionLater(sp, "runeknight_ult:final", finalAt - dive - 1, life);
        RuneKit.later(finalAt - dive - 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 검과 함께 내리꽂힘: 몸 앞으로 거대한 검 (칼끝 아래 — 주인을 따라감)
            JobFx3d.spawn(sp.level(), "phantom_sword", new Vec3(0, -0.6, 1.4), j, Opt.of("follow", (double) sp.getId(), "dir", V.DOWN, "face", new Vec3(0, 0, 1), "scale",
                    2.6, "life", (dive + 2) / 20.0, "fadeIn", 0.05, "fadeOut", 0.1, "glowAlpha", 0.6, "shine", 0.45, "rune", 1.0));
            SkillFx.play(sp.level(), "mob.breeze.jump", sp.position(), 0.7, 0.9);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) finale(sp, j, k, P, range, life, marked);
        });
        return true;
    }

    /** 거대한 검의 베기 하나 (i = 0 가로 · 1 세로 · 2 대각 — 1차 비전 거검과 같은 꼴): 앞 range 칸 · 폭 half × 2 의 적 (땅 높이의 줄 — 떠 있어도) */
    private static void giantSlash(ServerPlayer sp, Jobs.Job j, double P, double mult, int i, double range, double half, double[] glow, double[] core, String life,
            Set<UUID> marked, int untilNext, double groundY) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Vec3 eye = sp.getEyePosition();
        Vec3 pommel = V.up(V.add(sp.position(), f, 2.2), 0.25);
        Vec3 sweep = switch (i % 3) {
            case 0 -> right;
            case 1 -> f;
            default -> V.unit(V.add(right, f, 0.6));
        };
        Vec3 sw = V.unit(V.sub(sweep, V.scale(V.UP, V.dot(sweep, V.UP))));
        JobFx3d.swing(level, "phantom_sword", pommel, V.UP, sw, -25, 105, 0.2, 0.14, 3.4, JobFx3d.SWORD_GRIP, j, Opt.of("shine", 0.45, "glowAlpha", 0.6, "arcEase", "out"));
        RuneKit.later(5, () -> {
            if (!RuneKit.sameLife(sp, life) || untilNext <= 6) return;
            JobFx3d.spawn(sp.level(), "phantom_sword", new Vec3(0, 0.4, 2.6), j, Opt.of("follow", (double) sp.getId(), "dir", V.UP, "face", new Vec3(0, 0, 1), "scale",
                    3.4, "life", Math.max(0.1, (untilNext - 5) / 20.0), "pop", 0.12, "fadeIn", 0.05, "fadeOut", 0.0, "glowAlpha", 0.6, "shine", 0.35));
        });
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
                core, "alpha", 1.0));
        SkillFx.play(level, "item.trident.thunder", eye, 1.3 + i * 0.15, 0.6);
        SkillFx.play(level, "item.spear.attack_miss", eye, 0.8 + i * 0.1, 1.0);
        RuneKit.shakeAround(level, sp.position(), 10, 0.15, 0.2);
        Vec3 from = new Vec3(sp.getX(), groundY + 1.0, sp.getZ());
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

    /** 검이 땅에 내리꽂힘 (plunge — 둘레 plungeRadius 칸, 마법): 땅의 터짐 · 룬 띠 · 글자 번쩍 · 솟는 빛 */
    private static void plunge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 ground, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        double r = k.num("plungeRadius", 4);
        JobFx.smash(level, V.up(ground, 0.05), r, j, 0.5, Opt.empty());
        JobFx.runeRing(level, V.up(ground, 0.14), r * 0.9, j, 12, Kit.rand() * 30, 0.6, Opt.of("alpha", 0.95));
        JobFx.glyph(level, V.up(ground, 1.6), 1.0, 1, 4, 2, 0.35, false, Opt.empty(), j);
        RuneKit.godRays(sp, level, ground, 0, 1, JobKit.glow(j), JobKit.core(j), Opt.of("h", 12.0, "life", 0.6, "skipNear", true));
        SkillFx.sparks(level, V.up(ground, 0.5), JobFx.inner(j), 14, 6);
        SkillFx.play(level, "random.explode", ground, 1.1, 0.9);
        SkillFx.play(level, "item.trident.thunder", ground, 1.2, 0.7);
        RuneKit.shakeAround(level, ground, 14, 0.3, 0.3);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, ground, r, 3.5)) {
            JobKit.strike(sp, e, j, P, k.num("plunge", 14), true, false, life, 0, d -> {
                markOnce(sp, e, j, marked);
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.3);
            });
        }
    }

    /**
     * 검과 함께 내리꽂힘 (final — 내려앉은 자리 앞 둘레 finalRadius 칸 · 마법 · 큰 숫자 · 밀쳐냄): 맞은 적의 남은 각인을 모두 떼어 더하고 (각인 하나 = 위력 × markBurst)
     * 둘레로 번짐, 닿지 않은 각인 찍힌 적 (range + 반지름 칸 안) 도 그 자리에서 터짐
     */
    private static void finale(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double range, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 c = V.add(sp.position(), f, 1.5);
        Vec3 ground = new Vec3(c.x, RuneKit.groundAt(level, c, sp.getY()), c.z);
        double r = k.num("finalRadius", 5);
        double markBurst = j.trait() == null ? 1.0 : j.trait().def().numOr("markBurst", 1.0);
        // 큰 룬 폭발: 땅의 터짐 · 큰 룬 띠 둘 (거꾸로) · 둘레 빛기둥 · 번쩍 · 글자 셋
        JobFx.smash(level, V.up(ground, 0.05), r, j, 0.6, Opt.empty());
        JobFx.runeRing(level, V.up(ground, 0.16), r, j, 16, 0, 0.7, Opt.of("alpha", 1.0));
        RuneKit.later(2, () -> JobFx.runeRing(level, V.up(ground, 0.2), r * 0.6, j, 10, 30, 0.6, Opt.of("alpha", 0.95)));
        JobFx.ring(level, V.up(ground, 0.08), r, j, false, 0.45, Opt.empty());
        for (int i = 0; i < 3; i++) {
            Vec3 g = V.up(V.add(ground, V.turnY(f, -50 + 50 * i), r * 0.55), 1.4 + 0.3 * (i % 2));
            RuneKit.later(i * 2, () -> JobFx.glyph(level, g, 0.85, (int) (Kit.rand() * 3), 4, 2, 0.35, false, Opt.empty(), j));
        }
        RuneKit.godRays(sp, level, ground, r * 0.6, 6, JobKit.glow(j), JobKit.core(j), Opt.of("h", 14.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
        RuneKit.rockBurst(level, V.up(ground, 0.3), 12, 5.0, 0.2);
        SkillFx.sparks(level, V.up(ground, 0.5), JobFx.inner(j), 20, 7);
        Ult.flash(level, ground, JobKit.glow(j), Opt.empty(), sp);
        RuneKit.shakeAround(level, ground, 16, 0.45, 0.4);
        SkillFx.play(level, "random.explode", ground, 0.9, 1.0);
        SkillFx.play(level, "item.trident.thunder", ground, 1.0, 0.9);
        SkillFx.play(level, "chime.amethyst_block", ground, 0.6, 1.0);
        Map<UUID, LivingEntity> hit = new LinkedHashMap<>();
        for (LivingEntity e : Kit.around(sp, ground, r, 3.5)) hit.putIfAbsent(e.getUUID(), e);
        double mult = k.num("final", 24);
        for (LivingEntity e : hit.values()) {
            int marks = JobTraits.takeAllMarks(sp, e);
            JobKit.strike(sp, e, j, P, mult + markBurst * marks, true, true, life, 0, d -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), ground), k.num("knock", 0.8), 0.45);
                if (marks > 0) {
                    if (e.level() instanceof ServerLevel lv) markBlast(lv, e, marks, j);
                    JobTraits7.spread(sp, e, j, marks);
                }
            });
        }
        List<LivingEntity> rest = new ArrayList<>();
        for (LivingEntity e : JobKit.near(sp, range + r, 32)) if (!hit.containsKey(e.getUUID())) rest.add(e);
        for (LivingEntity e : rest) {
            int marks = JobTraits.takeAllMarks(sp, e);
            if (marks <= 0) continue;
            JobKit.strike(sp, e, j, P, markBurst * marks, true, false, life, 0, d -> {
                if (e.level() instanceof ServerLevel lv) markBlast(lv, e, marks, j);
                JobTraits7.spread(sp, e, j, marks);
            });
        }
    }

    /** 각인이 터짐: 각인 수만큼 겹쳐 커지는 터짐 · 글자 번쩍 · 타격 그림 */
    private static void markBlast(ServerLevel level, LivingEntity e, int marks, Jobs.Job j) {
        if (!RuneKit.fxQuota("jobMarkBlast", 6)) return;
        Vec3 b = Kit.bodyPoint(e);
        for (int m = 0; m < Math.min(3, marks); m++) {
            int mm = m;
            RuneKit.later(m * 2, () -> JobFx.burst(level, V.up(b, 0.25 * mm), 0.8 + 0.25 * mm + Kit.bodyPad(e) * 0.4, j, 0.32, Opt.empty()));
        }
        JobFx.glyph(level, V.up(b, 0.4), 0.5 + 0.06 * marks + Kit.bodyPad(e) * 0.3, marks % 3, 4, 2, 0.32, false, Opt.empty(), j);
        JobFx.hit(e, j, 1.3);
    }
}
