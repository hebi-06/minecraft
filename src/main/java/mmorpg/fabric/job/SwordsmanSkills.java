package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Obj;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Guards;
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
 * 0.27.0 검사 직업 스킬 (jobs.json swordsman — 청백 칼빛, 특성 「검세」): 연참 · 섬보 · 원월참 · 명경지수 · 천검난무.
 * 피해 = 위력 P (지속 보정 포함) × 배율 (JobKit.strike — 직업 색 그라데이션 숫자, 같은 적을 다시 칠 때는 몹 무적 시간이 끝난 뒤로 미룸).
 * 같은 적을 여러 번 치는 스킬은 10틱 넘게 띄움 (jobs.json gap · every · gather — JobsTest).
 * 연출 = 메이플식 2D 그림 (JobFx — 눈앞 큰 베기 · 맞은 자리 타격 그림 · 둥근 베기 · 일섬 · 빠른 줄, 룬 연출의 바닥 문양 · 룬 글자는 쓰지 않음).
 */
final class SwordsmanSkills {
    private SwordsmanSkills() {}

    /** 잔상 · 환영 검의 그림자 몸 (청백 — 풀밭 위에서도 보이게) */
    private static final double[] SHADE = {0.16, 0.42, 0.5};
    private static final double[] WHITE = RuneKit.WHITE;
    /** 연참 눈앞 베기의 기울기 (도 — 가로 · 되돌려 가로 (뒤집힘) · 크게) */
    private static final double[] TILT = {-10, 170, 12};
    /** 환영 검이 날아가는 빠르기 (칸/초) — 10칸을 5틱 */
    private static final double FLY_SPEED = 40;

    static void register() {
        JobSystem.register("swordsman_flurry", SwordsmanSkills::flurry);
        JobSystem.register("swordsman_flash", SwordsmanSkills::flash);
        JobSystem.register("swordsman_moon", SwordsmanSkills::moon);
        JobSystem.register("swordsman_mirror", SwordsmanSkills::mirror);
        JobSystem.register("swordsman_ult", SwordsmanSkills::ult);
    }

    static List<Double> numbers(Jobs.Skill k, String key, List<Double> fallback) {
        List<Double> l = k.def().numbersOrNull(key);
        return l == null || l.isEmpty() ? fallback : l;
    }

    // ------------------------------------------------------------ 연참

    /**
     * 연참: gap 틱마다 세 번 베기 (hits — 1.2 · 1.2 · 2.6, 앞 reach 칸 · arc 도), 벨 때마다 반 걸음 (step 칸) 들어감 — 가로 → 반대 가로 → 크게 (검기).
     * 쓸 때 검세가 wave.need 이상이면 wave.use 개를 쓰고 마지막 베기가 날아가는 검기 (앞 wave.range 칸 · 폭 wave.width 관통 — 부채꼴 안의 적도)
     */
    static boolean flurry(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> hits = numbers(k, "hits", List.of(1.2, 1.2, 2.6));
        int gap = (int) k.num("gap", 10);
        double reach = k.num("reach", 3.6);
        double arc = k.num("arc", 130);
        double step = k.num("step", 0.6);
        Obj wave = k.def().objOrNull("wave");
        boolean waveOn = wave != null && JobTraits.stacks(sp) >= wave.numOr("need", 3);
        if (waveOn) {
            JobTraits.useStacks(sp, (int) wave.numOr("use", 3));
            // 검세를 씀: 칼끝 (1인칭 오른쪽 아래) 에 모이는 작은 터짐
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.burst(sp.level(), V.up(V.add(h.hand(), h.f(), 0.6), 0.3), 0.32, j, 0.3, Opt.empty());
            SkillFx.play(sp.level(), "block.bell.hit", sp.position(), 1.8, 0.35);
        }
        String life = RuneKit.lifeToken(sp);
        int n = hits.size();
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            boolean last = i == n - 1;
            double mult = hits.get(i);
            // 반 걸음 들어감 (땅 위에서만 — 공중에서 밀려 떨어지지 않게)
            if (step > 0 && sp.onGround() && !Status.isRooted(sp)) Kit.launch(sp, f.x * step * 0.45, sp.getDeltaMovement().y, f.z * step * 0.45);
            if (last && waveOn) {
                waveSlash(sp, j, P, mult, wave, reach, arc, life);
                return;
            }
            // 눈앞 큰 베기: 가로 → 되돌려 가로 (뒤집힘) → 크게 (조금 기욺)
            JobFx.slash(sp, j, 2.2, 0.35, last ? 2.1 : 1.65, TILT[i % 3], last ? 0.3 : 0.26);
            if (last) SkillFx.play(level, "entity.player.attack.sweep", eye, 1.1, 0.8);
            SkillFx.play(level, "item.spear.attack_miss", eye, 1.15 + i * 0.12, 0.9);
            int[] cnt = {0};
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, last ? 1.25 : 1.0); // 맞은 자리 (파티클 예산 8명)
                });
            }
        });
        return true;
    }

    /**
     * 연참 마지막 베기의 검기: 눈앞에서 크게 휘두른 큰 베기 그림이 그대로 앞으로 날아감 (rfx_jslash — 날아가며 그려지고 사라짐) — 선 가까이 (폭 wave.width) ·
     * 부채꼴 안의 적 (적마다 한 번), 맞은 자리마다 타격 그림
     */
    private static void waveSlash(ServerPlayer sp, Jobs.Job j, double P, double mult, Obj wave, double reach, double arc, String life) {
        ServerLevel level = sp.level();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        Vec3 dir = V.unit(new Vec3(f.x, view.y * 0.3, f.z));
        Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.1);
        double half = wave.numOr("width", 1.4) / 2;
        double len = Math.max(1, RuneKit.rayLength(level, from, dir, wave.numOr("range", 6)));
        double speed = 30;
        double s0 = Math.min(1.8, len);
        JobFx.slash(sp, j, 2.0, 0.3, 2.3, TILT[2], 0.24);
        double fl = Math.max(0.12, (len - s0) / speed) + 0.1;
        JobFx.two(level, "jslash", JobFx.SLASH, V.add(from, dir, s0), fl, Opt.of("dir", dir, "speed", speed, "size", 1.8, "spin", TILT[2], "fade", 0.2),
                JobFx.outer(j), JobFx.inner(j));
        SkillFx.play(level, "item.trident.throw", from, 1.3, 0.9);
        SkillFx.play(level, "item.spear.attack_miss", from, 1.6, 0.7);
        Set<UUID> done = new HashSet<>();
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, dir, len, half)) {
            LivingEntity e = h.e();
            if (!done.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, mult, false, false, life, (int) Math.round(h.t() / speed * 20), d -> {
                if (cnt[0]++ < 6) JobFx.hit(e, j, 1.3);
            });
        }
        for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
            if (!done.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                if (cnt[0]++ < 6) JobFx.hit(e, j, 1.3);
            });
        }
    }

    // ------------------------------------------------------------ 섬보

    /**
     * 섬보: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 t 틱에 미끄러지듯 지나감 (한 틱에 한 마디씩 옮김 — 지나온 자리에 청백 잔상 · 빛줄기), 길 가까이 (폭 width) 의
     * 적을 지날 때 벰 (mult — 2틱 늦게 그어지는 일섬 · 타격 그림, 다 지나간 뒤 길 전체 · 1인칭 화면을 가로지르는 일섬). 끝나면 critFor 틱 안의 다음 칼 평타
     * 한 번은 치명타 (칼끝 반짝임 · "치명 준비"). 지나는 동안 1인칭 빠른 줄
     * 묶였거나 앞이 막혀 한 칸도 못 가면 쓰지 않음 (재사용 대기 없음)
     */
    static boolean flash(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double free = Kit.reachFlat(level, start, f, k.num("dist", 6));
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        double len = spots.get(spots.size() - 1).s();
        int steps = Math.max(1, (int) k.num("t", 3));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        double[] glow = JobKit.glow(j);
        String life = RuneKit.lifeToken(sp);
        // 출발: 쓴 자리의 잔상 · 발밑 작은 둥근 베기 · 1인칭 빠른 줄 · 소리
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, glow, Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        JobFx.ring(level, V.up(start, 0.06), 0.9, j, false, 0.3, Opt.of("alpha", 0.85));
        SkillFx.play(level, "item.trident.riptide_1", start, 1.6, 0.8);
        SkillFx.play(level, "item.spear.attack_miss", start, 1.5, 0.9);
        Kit.noFall(sp, 30);
        boolean[] stopped = {false};
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (stopped[0] || !RuneKit.sameLife(sp, life)) return;
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) {
                    stopped[0] = true;
                    return;
                }
                ServerLevel d = sp.level();
                if (ss < marks.size() - 1) RuneKit.shadeClone(sp, d, prev, f, 1, SHADE, glow, Opt.of("life", 0.32, "fade", 0.6, "alpha", 0.7));
                // 지나온 마디의 돌진 줄기 (몸 높이 — 1인칭에서는 카메라 옆이라 숨김) · 1인칭 빠른 줄 (옮긴 자리 눈앞에 — 마디마다 새로, 마지막은 조금 길게)
                JobFx.dash(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.3, JobFx.hide1p(sp));
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.85);
            });
        }
        // 지나가며 벰: 길 가까이 적 — 지나는 마디의 틱에 (그림은 2틱 늦게 — 다 지나간 뒤 베인 줄이 그어짐)
        double half = k.num("width", 1.6) / 2;
        double mult = k.num("mult", 3);
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(start, 1.0), f, len, half)) {
            LivingEntity e = h.e();
            int at = (int) Math.min(steps - 1, Math.floor(h.t() / Math.max(0.1, len) * steps));
            JobKit.strike(sp, e, j, P, mult, false, false, life, at, d -> RuneKit.later(2, () -> {
                if (e.isRemoved() || cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                JobFx.line(lv, V.add(b, f, -1.3), V.add(b, f, 1.3), j, 0.3, Opt.empty());
                JobFx.hit(e, j, 1.15);
                SkillFx.play(lv, "game.player.attack.strong", b, 1.4, 0.7);
            }));
        }
        // 다 지나간 뒤: 지나온 길 전체에 일섬 (3인칭 · 다른 사람) + 1인칭 화면을 가로지르는 일섬
        RuneKit.later(steps + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 end = sp.position();
            JobFx.line(sp.level(), V.up(start, 1.0), V.up(V.add(end, f, 0.6), 1.0), j, 0.34, JobFx.hide1p(sp));
            Vec3 look = sp.getViewVector(1F);
            Vec3 c = V.add(V.add(sp.getEyePosition(), look, 2.6), JobFx.screenUp(look), -0.25);
            Vec3 r = V.rightOf(V.flat(look));
            JobFx.line(sp.level(), V.add(c, r, -2.6), V.add(c, r, 2.6), j, 0.3, JobFx.only1p(sp));
        });
        // 끝: 다음 평타 치명타 확정 (칼끝 작은 반짝임 · 글자)
        int critFor = (int) k.num("critFor", 60);
        RuneKit.later(steps, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobTraits.sureCritFor(sp, critFor);
            RuneKit.Hand h = RuneKit.handOf(sp);
            RuneKit.rfx(sp.level(), "prop", V.up(V.add(h.hand(), h.f(), 0.7), 0.5), Opt.of("size", 0.16, "life", 0.3, "cell", (double) RuneKit.RFX.get("glint")[0],
                    "spin", 20.0, "spinRate", 180.0, "color", WHITE, "grow", 0.5, "fade", 0.5, "vel", RuneKit.bodyVel(sp)));
            DamageNumbers.showFace(sp, "§b§l치명 준비", 2.4, 0.9, -0.35);
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.7, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 원월참

    /**
     * 원월참: 검세를 모두 써서 (하나당 + perStack) 칼을 크게 한 바퀴 — 둘레 radius 칸 둥근 참격 두 번 (hits, gap 틱 간격 — 안쪽에서 바깥으로 퍼짐).
     * 그림: 허리 높이에서 한 바퀴 도는 둥근 베기 · 땅의 옅은 고리 · 눈앞 큰 베기 (도는 쪽), 검세를 쓰면 글자 · 몸에서 솟는 빛
     */
    static boolean moon(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> hits = numbers(k, "hits", List.of(4.5, 4.5));
        int gap = (int) k.num("gap", 10);
        double radius = k.num("radius", 4.5);
        double per = k.num("perStack", 0.08);
        int used = JobTraits.useStacks(sp, 99);
        double bonus = 1 + per * used;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        if (used > 0) {
            // 검세를 씀: 글자 (1인칭) · 몸에서 솟는 빛 (3인칭 · 다른 사람)
            DamageNumbers.showFace(sp, "§b§l검세 ×" + used + " §r§7+" + Math.round(per * used * 100) + "%", 2.4, 0.9, -0.3);
            JobFx.aura(sp, j, 2.4, (gap + 10) / 20.0, Math.min(0.95, 0.5 + 0.1 * used));
        }
        SkillFx.play(level0, "entity.player.attack.sweep", sp.position(), 0.8, 1.0);
        int n = hits.size();
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            double q = n <= 1 ? 1 : (double) i / (n - 1);
            double r0 = radius * (0.6 + 0.4 * q);
            // 허리 높이에서 한 바퀴 도는 둥근 베기 (눕힌 그림 — 1인칭에서는 눈 아래로 둘러 보임) + 땅의 옅은 고리 + 눈앞 큰 베기 (도는 쪽)
            JobFx.ring(level, V.up(c, 1.0), r0, j, i % 2 == 1, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.ring(level, V.up(c, 0.06), r0 * 0.92, j, i % 2 == 1, 0.42, Opt.of("alpha", 0.55, "coreAlpha", 0.45));
            JobFx.slash(sp, j, 2.5, 0.5, 1.9 + 0.3 * q, i % 2 == 0 ? 0.0 : 180.0, 0.26);
            // 0.47.0 입체: 허리 높이에서 몸과 같이 한 바퀴 도는 환영 검 둘 (칼끝이 바깥 — 첫 바퀴는 오른쪽으로, 둘째는 왼쪽으로)
            JobFx3d.orbitOwner(sp, "phantom_sword", 2, new Vec3(0, 1.0, 0), 0.75, i % 2 == 0 ? -1150 : 1150, true, 0.32, 1.1 + 0.25 * q, j, Opt.of("angle", Kit.rand()
                    * 360, "fadeIn", 0.03, "fadeOut", 0.1, "shine", 0.3));
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.9 + i * 0.15, 1.0);
            SkillFx.play(level, "item.spear.attack_miss", c, 1.0 + i * 0.2, 0.8);
            double mult = hits.get(i) * bonus;
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.1);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 명경지수

    /**
     * 명경지수: dur 틱 태세 — 칼 평타가 두 번 베기 (두 번째 = 평타 × echo · JobTraits), 검세가 사라지지 않음, 받는 피해 −guard (태세 동안만).
     * 그림: 몸에서 솟는 물빛 (3인칭) · 발밑에서 차례로 퍼지는 잔물결 고리 셋 · 1인칭 눈앞 고리 · 글자, 태세 동안 8틱마다 옅은 빛 · 칼의 물빛 (JobTraits)
     */
    static boolean mirror(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 160);
        JobTraits.startStance(sp, k, dur);
        Guards.set(sp, dur, k.num("guard", 0.15), null, p -> JobTraits.stanceOn(p, k.id()));
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        // 몸에서 솟는 물빛 (3인칭 · 다른 사람) · 발밑에서 차례로 퍼지는 잔물결 고리 셋 · 1인칭 눈앞 고리 · 글자
        JobFx.aura(sp, j, 2.6, 1.0, 0.9);
        String life = RuneKit.lifeToken(sp);
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(i * 6, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.ring(sp.level(), V.up(sp.position(), 0.06), 1.4 + ii * 0.8, j, ii % 2 == 1, 0.5, Opt.of("alpha", 0.8 - ii * 0.15));
            });
        }
        JobFx.eyeRing(sp, j, 2.4, 0.95, 0.5);
        // 0.47.0 입체: 태세 동안 몸 둘레를 천천히 도는 환영 검 둘 (가슴 높이 · 칼끝이 가는 쪽 — 1인칭에서는 숨김)
        int[] ids = JobFx3d.orbitOwner(sp, "phantom_sword", 2, new Vec3(0, 1.15, 0), 1.15, 75, false, dur / 20.0, 0.62, j, Opt.of("view", 1.0, "owner", (double) sp
                .getId(), "bob", 0.12, "bobHz", 0.6, "fadeIn", 0.3, "fadeOut", 0.5, "alpha", 0.9));
        JobFx3d.hold(sp, "swordsman_mirror", ids);
        JobFx3d.guard(sp, life, ids, dur);
        DamageNumbers.showFace(sp, "§b§l명경지수", 2.4, 0, 0.2);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.4, 1.0);
        SkillFx.play(level, "item.trident.return", c, 1.2, 0.8);
        SkillFx.play(level, "block.bell.resonate", c, 1.6, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 천검난무

    /**
     * 천검난무: 청백 하늘 · 발밑에서 크게 도는 둥근 베기, 머리 뒤로 환영 검 swords 자루가 솟아 두 겹 반원 (rise 틱) → every 틱마다 (volleys 번) 둘레 radius 칸의
     * 보이는 적 targets 명에게 검이 날아가 꽂힘 (적마다 한 번에 swords ÷ volleys 자루 = 배율 그만큼 × sword — 반원의 검이 그만큼 줄어듦, 적이 없으면 둘레 땅에 꽂힘)
     * → 흩어진 검이 눈앞으로 모여 (gather 틱) 거대한 한 자루 → 앞 finalRange 칸 · 폭 finalWidth 를 가르는 일섬 (final — 큰 숫자, 번쩍임 · 하늘이 갈라짐 ·
     * 땅이 갈라져 빛남). 마지막 꽂힘 (날아가는 데 6틱 안) 과 일섬은 10틱 넘게 떨어짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int swords = Math.max(1, (int) k.num("swords", 24));
        int volleys = Math.max(1, (int) k.num("volleys", 6));
        int per = Math.max(1, swords / volleys);
        double volleyMult = per * k.num("sword", 1);
        double radius = k.num("radius", 10);
        int targets = (int) k.num("targets", 8);
        int rise = (int) k.num("rise", 16);
        int every = (int) k.num("every", 10);
        int gather = (int) k.num("gather", 16);
        int lastVolley = rise + (volleys - 1) * every;
        int finalAt = lastVolley + gather;
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        int[] left = {swords};
        Ult.sky(level0, c0, "rpg:ult_blade", finalAt + 30);
        // 발동: 발밑에서 크게 도는 둥근 베기 둘 (안 · 밖 — 거꾸로) · 몸에서 솟는 빛 · 1인칭 눈앞 고리
        JobFx.ring(level0, V.up(c0, 0.06), 5.0, j, false, rise / 20.0 + 0.3, Opt.of("alpha", 0.85));
        JobFx.ring(level0, V.up(c0, 0.08), 3.0, j, true, rise / 20.0 + 0.2, Opt.of("alpha", 0.7));
        JobFx.aura(sp, j, 2.8, rise / 20.0 + 0.4, 0.9);
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", c0, 1.2, 0.9);
        SkillFx.play(level0, "beacon.power", c0, 1.5, 0.8);
        SkillFx.play(level0, "block.bell.resonate", c0, 1.2, 0.8);
        // 0.47.0 환영 검 반원 (입체 — 주인을 따라감): 머리 뒤 두 겹 반원으로 아래에서 솟아오름 (rise 틱), 일제 사격마다 per 자루가 그 자리에서 날아감
        Halo halo = halo(sp, j, swords, rise, (lastVolley + 4) / 20.0);
        JobFx3d.guard(sp, life, halo.ids, finalAt);
        for (int t = 0; t < rise; t += 6) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "block.bell.hit", sp.position(), 1.0 + (tt + 3.0) / Math.max(1, rise), 0.35);
            });
        }
        for (int v = 0; v < volleys; v++) {
            int vv = v;
            RuneKit.later(rise + v * every, () -> {
                if (RuneKit.sameLife(sp, life)) volley(sp, j, P, volleyMult, per, left, radius, targets, vv, glow, core, life, halo);
            });
            JobKit.motionLater(sp, "swordsman_ult:volley", rise + v * every - 1, life); // 0.47.0 칼끝을 튕겨 환영 검을 날려 보내는 동작
        }
        RuneKit.later(lastVolley + 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx3d.killAll(sp.level(), halo.ids, sp.position(), 0.15); // 남은 반원의 검 (일제 사격이 다 못 쓴 것)
            gatherFx(sp, j, gather - 2, glow, core, life);
        });
        JobKit.motionLater(sp, "swordsman_ult:gather", lastVolley + 2, life); // 0.47.0 두 손으로 칼을 들어 기를 모으는 동작
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) finalCut(sp, j, k, P, glow, core, life);
        });
        JobKit.motionLater(sp, "swordsman_ult:final", finalAt - 1, life); // 0.47.0 크게 되돌려 베는 일섬 (1틱 뒤 닿음)
        return true;
    }

    /** 0.47.0 입체 환영 검 반원: 검마다 번호 · 주인 기준 자리 [오른쪽, 위, 앞] · 칼끝 쪽, 날려 보낼 차례 (바깥 · 안 겹을 양끝에서 번갈아 — 반원이 고르게 줄어듦) */
    record Halo(int[] ids, Vec3[] local, Vec3[] radial, int[] order, int[] next) {}

    /** 환영 검 반원 (머리 뒤 · 위 — 칼끝이 바깥으로): 바깥 겹 (반지름 2.5) 이 조금 더 많음, 아래에서 rise 틱에 솟아오름 */
    private static Halo halo(ServerPlayer sp, Jobs.Job j, int n, int rise, double life) {
        Vec3 center = new Vec3(0, sp.getEyeHeight() + 0.5, -0.8);
        int outer = Math.min(n, (n + 1) / 2 + n / 8);
        int[] ids = new int[n];
        Vec3[] local = new Vec3[n];
        Vec3[] radial = new Vec3[n];
        List<double[]> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean out = i < outer;
            int m = out ? outer : n - outer;
            int idx = out ? i : i - outer;
            double th = Math.toRadians(-80 + 160.0 * (m <= 1 ? 0.5 : (double) idx / (m - 1)));
            Vec3 rd = new Vec3(Math.sin(th), Math.cos(th), 0);
            Vec3 at = V.add(center, rd, out ? 2.5 : 1.7);
            local[i] = at;
            radial[i] = rd;
            // 양끝에서 가운데로 (바깥 · 안 겹을 비율로 섞음)
            int fromEnd = Math.min(idx, m - 1 - idx);
            order.add(new double[] {(fromEnd * 2 + (idx > (m - 1) / 2.0 ? 1 : 0)) / (double) Math.max(1, m), out ? 0 : 1, i});
            ids[i] = JobFx3d.spawn(sp.level(), "phantom_sword", V.add(at, V.UP, -1.6), j, Opt.of("follow", (double) sp.getId(), "to", at, "ctrl", V.add(at, V.UP, -0.4),
                    "travel", Math.max(0.2, rise / 20.0 * (0.7 + 0.3 * (double) idx / Math.max(1, m))), "ease", "out3", "dir", rd, "face", new Vec3(0, 0, 1), "life", life,
                    "scale", out ? 0.78 : 0.64, "fadeIn", 0.25, "fadeOut", 0.15, "glowAlpha", 0.45, "shine", 0.25));
        }
        order.sort((x, y) -> x[0] != y[0] ? Double.compare(x[0], y[0]) : Double.compare(x[1], y[1]));
        int[] ord = new int[n];
        for (int k = 0; k < n; k++) ord[k] = (int) order.get(k)[2];
        return new Halo(ids, local, radial, ord, new int[] {0});
    }

    /** 반원의 검 k 의 지금 세계 자리 (칼끝) */
    private static Vec3 haloTip(ServerPlayer sp, Halo h, int k) {
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        Vec3 l = V.add(h.local()[k], h.radial()[k], JobFx3d.SWORD_TIP * 0.7);
        return V.add(V.add(V.add(sp.position(), r, l.x), V.UP, l.y), f, l.z);
    }

    /** 한 번의 일제 사격: 둘레 radius 칸의 보이는 적 (가까운 순 max 명) 마다 검 per 자루 (그림은 앞 둘 per · 다음 둘 2 · 나머지 1 자루) */
    private static void volley(ServerPlayer sp, Jobs.Job j, double P, double mult, int per, int[] left, double radius, int max, int v, double[] glow, double[] core,
            String life, Halo halo) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Vec3 origin = V.up(V.add(eye, f, -0.8), 1.6);
        List<LivingEntity> list = Kit.visibleFrom(sp, eye, JobKit.near(sp, radius, 32));
        if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
        left[0] = Math.max(0, left[0] - per);
        // 0.47.0 반원에서 per 자루가 빠져 날아감 (그 검의 칼끝 자리에서 — 반원의 검은 지움)
        List<Vec3> starts = new ArrayList<>();
        for (int s = 0; s < per && halo.next()[0] < halo.order().length; s++) {
            int k = halo.order()[halo.next()[0]++];
            starts.add(haloTip(sp, halo, k));
            JobFx3d.kill(level, halo.ids()[k], sp.position(), 0.0);
        }
        if (starts.isEmpty()) starts.add(origin);
        SkillFx.play(level, "item.trident.throw", eye, 1.2 + v * 0.08, 0.9);
        SkillFx.play(level, "block.bell.hit", eye, 1.4 + v * 0.06, 0.35);
        if (list.isEmpty()) {
            // 적이 없으면 둘레 땅에 꽂힘 (피해 없음)
            for (int s = 0; s < per; s++) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 g = V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 3 + Kit.rand() * 3);
                Vec3 ground = new Vec3(g.x, RuneKit.groundAt(level, g, sp.getY()), g.z);
                int t = fly(level, j, starts.get(s % starts.size()), V.up(ground, 0.3), glow, core, 0.6);
                RuneKit.later(t, () -> {
                    JobFx.ring(level, V.up(ground, 0.05), 0.7, j, false, 0.3, Opt.empty());
                    JobFx.burst(level, V.up(ground, 0.35), 0.55, j, 0.3, Opt.empty());
                });
            }
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            LivingEntity e = list.get(i);
            Vec3 to = Kit.bodyPoint(e);
            int nShow = i < 2 ? per : i < 4 ? Math.min(2, per) : 1;
            int first = 0;
            for (int s = 0; s < nShow; s++) {
                Vec3 from = V.add(starts.get((i * 2 + s) % starts.size()), right, (s / starts.size()) * 0.5);
                Vec3 aim = V.add(to, new Vec3((Kit.rand() - 0.5) * 0.4, (Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.5) * 0.4));
                int t = fly(level, j, from, aim, glow, core, 0.3);
                if (s == 0) first = t;
            }
            JobKit.strike(sp, e, j, P, mult, false, false, life, first, d -> {
                if (RuneKit.fxQuota("jobBladeRain", 6)) JobFx.hit(e, j, 1.0);
            });
        }
    }

    /** 날아가는 환영 검 (0.47.0 입체 — 칼끝이 가는 쪽, 조금 휘어 날아가 hold 초 꽂혀 있음) + 옅은 빛줄기. 도착까지 틱 */
    private static int fly(ServerLevel level, Jobs.Job j, Vec3 from, Vec3 to, double[] glow, double[] core, double hold) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.1) return 0;
        double life = Math.max(0.1, len / FLY_SPEED);
        JobFx3d.fly(level, "phantom_sword", from, to, JobFx3d.SWORD_TIP, 0.8, life, hold, j, Opt.of("ctrl", V.up(V.lerp(from, to, 0.5), Math.min(1.2, len * 0.12)),
                "ease", "in", "shine", 0.3, "fadeOut", 0.15));
        RuneKit.streak(level, from, to, core, 0.06, life + 0.1, 0.6, 0);
        return Math.max(1, (int) Math.round(life * 20));
    }

    /**
     * 0.47.0 모인 거대한 검 (주인 기준 앞 칸 · 오른쪽 칸 · 크기 · 떠 있을 때 코등이 높이 — 칼끝이 땅 0.9칸 위 · 박혔을 때 코등이 높이 — 칼끝이 땅 1.1칸 아래). 사진첩: 앞 3칸
     * 가운데에 꽂힌 검은 1인칭에서 과녁 몸 뒤에 (코등이만 보임) · 3인칭에서 내 몸 뒤에 가려졌음 → 앞 오른쪽 (1인칭 조준점 옆 · 3인칭 몸 옆)
     */
    private static final double GIANT_AHEAD = 2.7;
    private static final double GIANT_RIGHT = 1.15;
    private static final double GIANT_SCALE = 3.2;
    private static final double GIANT_HOVER = JobFx3d.SWORD_TIP * GIANT_SCALE + 0.9;
    private static final double GIANT_STUCK = JobFx3d.SWORD_TIP * GIANT_SCALE - 1.1;

    /** 거대한 검이 박히는 땅 자리 (앞 GIANT_AHEAD · 오른쪽 GIANT_RIGHT) */
    private static Vec3 giantAt(ServerPlayer sp, Vec3 f) {
        return V.add(V.add(sp.position(), f, GIANT_AHEAD), V.rightOf(f), GIANT_RIGHT);
    }

    /**
     * 모임: 둘레 열두 곳에서 검이 눈앞으로 날아와 모이고 (ticks 틱), 그 자리에 거대한 검이 커짐 → 일섬 3틱 전 땅에 꽂힘. 0.47.0 입체 — 앞 오른쪽 위에 칼끝을 아래로 한 검이
     * 떠서 (주인을 따라감 · 넓은 면이 보는 사람 쪽) 0.7 → 3.2 배로 커지며 칼끝이 땅 쪽으로 내려오고, 일섬 때 내리꽂혀 박힘 (사진첩: 앞으로 내리치던 검은 1인칭에서 손잡이가
     * 화면을 덮고 3인칭에서는 칼날의 옆선으로만 보였음)
     */
    private static void gatherFx(ServerPlayer sp, Jobs.Job j, int ticks, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 point = V.up(giantAt(sp, f), 3.0);
        double flyLife = Math.max(0.2, ticks * 0.6 / 20);
        for (int i = 0; i < 12; i++) {
            double a = i / 12.0 * Math.PI * 2;
            Vec3 from = V.up(V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 4.5), 1.0 + (i % 3) * 0.9);
            JobFx3d.fly(level, "phantom_sword", from, point, JobFx3d.SWORD_TIP, 0.7, flyLife, 0, j, Opt.of("ease", "in", "shine", 0.3, "fadeOut", 0.12, "delay", (i % 4)
                    * 0.03));
        }
        SkillFx.play(level, "block.beacon.power_select", sp.position(), 1.4, 0.8);
        int start = (int) Math.round(ticks * 0.5);
        int plungeAt = Math.max(start + 2, ticks - 3);
        double grow = Math.max(0.05, (plungeAt - start) / 20.0);
        double owner = sp.getId();
        RuneKit.later(start, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 떠 있는 거대한 검 (칼끝이 아래): 코등이 높이 GIANT_HOVER 에서 0.7 → 3.2 배로 커짐 (칼끝이 내려옴)
            JobFx3d.spawn(sp.level(), "phantom_sword", new Vec3(GIANT_RIGHT, GIANT_HOVER, GIANT_AHEAD), j, Opt.of("follow", owner, "dir", V.DOWN, "faceCam", 1.0, "scale", 0.7,
                    "grow", GIANT_SCALE / 0.7 - 1, "life", grow, "fadeIn", 0.1, "fadeOut", 0.0, "glowAlpha", 0.6, "shine", 0.4));
        });
        RuneKit.later(plungeAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 내리꽂힘 (3틱 — 점점 빠르게) → 박힌 채 흐려짐
            JobFx3d.spawn(sp.level(), "phantom_sword", new Vec3(GIANT_RIGHT, GIANT_HOVER, GIANT_AHEAD), j, Opt.of("follow", owner, "to", new Vec3(GIANT_RIGHT,
                    GIANT_STUCK, GIANT_AHEAD), "ctrl", new Vec3(GIANT_RIGHT, (GIANT_HOVER + GIANT_STUCK) / 2, GIANT_AHEAD), "travel", 0.15, "ease", "in", "dir", V.DOWN,
                    "faceCam", 1.0, "scale", GIANT_SCALE, "life", 1.0, "fadeOut", 0.5, "glowAlpha", 0.65, "shine", 0.5));
        });
        for (int t = start; t < ticks; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 at = V.up(giantAt(sp, Kit.facingOf(sp)), 2.0);
                SkillFx.play(sp.level(), "block.bell.hit", at, 1.3 + (tt + 2.0) / ticks * 0.6, 0.35);
            });
        }
    }

    /** 일섬: 거대한 검기가 앞 finalRange 칸을 가름 (폭 finalWidth) — 눈앞 거대한 베기 · 날아가는 검기 · 화면을 가로지르는 일섬 · 번쩍임 · 하늘이 갈라짐 · 땅이 갈라져 빛남, 큰 숫자 */
    private static void finalCut(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = V.up(V.add(sp.position(), f, 0.8), 1.2);
        double len = Math.max(1, RuneKit.rayLength(level, from, f, k.num("finalRange", 12)));
        double half = k.num("finalWidth", 3.2) / 2;
        Vec3 eye = sp.getEyePosition();
        double speed = 40;
        // (0.47.0 입체 거대한 검은 모임 (gatherFx) 에서 3틱 전에 내리꽂혀 지금 앞 오른쪽 땅에 박힘) 박힌 자리의 땅 터짐
        Vec3 stuck = giantAt(sp, f);
        Vec3 stuckGround = new Vec3(stuck.x, RuneKit.groundAt(level, stuck, sp.getY()), stuck.z);
        JobFx.smash(level, V.up(stuckGround, 0.05), 2.2, j, 0.55, Opt.empty());
        RuneKit.rockBurst(level, V.up(stuckGround, 0.2), 8, 4.5, 0.18);
        // 거대한 베기 (눈앞) + 그 베기가 앞으로 날아감 + 화면을 가로지르는 일섬 (1인칭) · 지나는 길의 일섬 (3인칭 · 다른 사람) + 앞으로 뻗는 긴 빛줄기
        JobFx.slash(sp, j, 2.6, 0.25, 3.0, 4.0, 0.42);
        JobFx.two(level, "jslash", JobFx.SLASH, V.add(from, f, 2.4), Math.max(0.15, (len - 2.4) / speed) + 0.12, Opt.of("dir", f, "speed", speed, "size", half * 1.6,
                "spin", 4.0, "fade", 0.2), JobFx.outer(j), JobFx.inner(j));
        Vec3 look = sp.getViewVector(1F);
        Vec3 mid = V.add(V.add(eye, look, 3.0), JobFx.screenUp(look), -0.3);
        Vec3 r = V.rightOf(f);
        JobFx.line(level, V.add(mid, r, -3.4), V.add(mid, r, 3.4), j, 0.42, JobFx.only1p(sp));
        JobFx.line(level, V.add(from, f, 1.2), V.add(from, f, len), j, 0.5, JobFx.hide1p(sp));
        RuneKit.flyStreak(level, from, V.add(from, f, len), speed, glow, core, 1.4, 0.5);
        Ult.flash(level, from, glow, Opt.empty(), sp);
        SkillFx.play(level, "item.trident.thunder", from, 1.5, 0.8);
        SkillFx.play(level, "firework.large_blast", from, 1.2, 0.8);
        SkillFx.play(level, "item.spear.attack_miss", eye, 0.8, 1.0);
        RuneKit.shakeAround(level, from, 12, 0.3, 0.35);
        // 하늘이 둘로 갈라짐
        Vec3 sky0 = V.up(V.add(eye, f, 15), 7.5);
        SkillFx.fx(level, "rpg:light_cut", sky0, Opt.of("dir", f, "size", 13.0, "thick", 0.9, "life", 1.1, "delay", 0.6, "color", glow, "alpha", 0.95));
        SkillFx.fx(level, "rpg:light_cut", sky0, Opt.of("dir", f, "size", 12.5, "thick", 0.3, "life", 1.0, "delay", 0.55, "color", WHITE, "alpha", 1.0));
        for (double s = 3; s < len; s += 4) {
            Vec3 p = V.add(from, f, s);
            double crackV = (s / 4) % 2 > 0.5 ? 1 : 0;
            RuneKit.later(Math.round((s / speed) * 20) + 1, () -> RuneKit.glowCrack(level, new Vec3(p.x, RuneKit.groundAt(level, p, from.y - 1.2), p.z), 2.2, glow,
                    WHITE, Opt.of("life", 1.6, "v", crackV)));
        }
        double mult = k.num("final", 16);
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, half)) {
            LivingEntity e = h.e();
            JobKit.strike(sp, e, j, P, mult, false, true, life, (int) Math.round(h.t() / speed * 20), d -> {
                RuneKit.knock(e, f, 0.5, 0.3);
                if (!RuneKit.fxQuota("jobFinalCut", 4) || !(e.level() instanceof ServerLevel lv)) return;
                JobFx.hit(e, j, 1.8);
                JobFx.burst(lv, Kit.bodyPoint(e), 1.1 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
            });
        }
    }
}
