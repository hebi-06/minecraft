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
import mmorpg.fabric.RpgCombat;
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
 * 0.27.0 검사 직업 스킬 (jobs.json swordsman — 청백 칼빛, 특성 「검세」): 연참 · 섬보 · 원월참 · 명경지수 · 천검난무 (0.50.0 은빛 연격 · 섬광 질주 ·
 * 만월 베기 · 에인헤랴르 · 라그나로크 — 궁극기는 몸으로 돌진해 베고 땅을 가름).
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
        JobFx.wave(sp, j, V.add(from, dir, s0), dir, speed, fl, Opt.of("size", 1.8, "spin", TILT[2], "fade", 0.2)); // 0.50.0 3인칭 · 다른 사람은 날아가는 참격 판
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
            DamageNumbers.showFace(sp, "§b§l" + j.trait().name() + " ×" + used + " §r§7+" + Math.round(per * used * 100) + "%", 2.4, 0.9, -0.3);
            JobFx.aura(sp, j, 2.4, (gap + 10) / 20.0, Math.min(0.95, 0.5 + 0.1 * used));
        }
        SkillFx.play(level0, "entity.player.attack.sweep", sp.position(), 0.8, 1.0);
        int n = hits.size();
        boolean[] cut = {false};
        double ground = sp.getY();
        // 0.50.0 동작: 제자리에서 뛰어올라 (hop 칸) 공중에서 두 바퀴 돌며 벰 → 착지 (사용자 "역동적으로")
        JobMove.run(sp, JobMove.hop(0, gap + 6, from -> from, k.num("hop", 0.8)));
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 cg = new Vec3(c.x, ground, c.z); // 땅 (뛰어오른 동안에도 땅 고리는 땅에)
            double q = n <= 1 ? 1 : (double) i / (n - 1);
            double r0 = radius * (0.6 + 0.4 * q);
            // 허리 높이에서 한 바퀴 도는 둥근 베기 (눕힌 그림 — 1인칭에서는 눈 아래로 둘러 보임) + 땅의 옅은 고리 + 눈앞 큰 베기 (도는 쪽)
            JobFx.ring(level, V.up(c, 1.0), r0, j, i % 2 == 1, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.ring(level, V.up(cg, 0.06), r0 * 0.92, j, i % 2 == 1, 0.42, Opt.of("alpha", 0.55, "coreAlpha", 0.45));
            JobFx.slash(sp, j, 2.5, 0.5, 1.9 + 0.3 * q, i % 2 == 0 ? 0.0 : 180.0, 0.26);
            // 0.47.0 입체: 허리 높이에서 몸과 같이 한 바퀴 도는 환영 검 둘 (칼끝이 바깥 — 첫 바퀴는 오른쪽으로, 둘째는 왼쪽으로)
            JobFx3d.orbitOwner(sp, "phantom_sword", 2, new Vec3(0, 1.0, 0), 0.75, i % 2 == 0 ? -1150 : 1150, true, 0.32, 1.1 + 0.25 * q, j, Opt.of("angle", Kit.rand()
                    * 360, "fadeIn", 0.03, "fadeOut", 0.1, "shine", 0.3));
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.9 + i * 0.15, 1.0);
            SkillFx.play(level, "item.spear.attack_miss", c, 1.0 + i * 0.2, 0.8);
            double mult = hits.get(i) * bonus;
            int[] cnt = {0};
            List<LivingEntity> targets = Kit.around(sp, c, radius, 2.5);
            if (!targets.isEmpty()) cut[0] = true;
            // 0.50.0 발키리의 가호: 마지막 바퀴까지 하나라도 베면 buffTicks 동안 가하는 피해 + buff (몸의 기운 · 상태 줄 — 만월 베기 자신에는 안 걸림).
            // 마지막 바퀴의 타격이 모두 들어간 뒤에 검 (적의 무적 시간 때문에 미뤄진 타격이 가호를 받지 않게 — 0.50.0 시험: 둘째 바퀴가 ×1.1 로 들어갔음),
            // 빗나간 타격이 있으면 (알림이 안 옴) 12틱 뒤
            boolean last = i == n - 1 && cut[0] && k.num("buff", 0) > 0;
            boolean[] given = {false};
            Runnable bless = () -> {
                if (given[0] || !RuneKit.sameLife(sp, life)) return;
                given[0] = true;
                mmorpg.fabric.rune.RuneMarks.empower(sp, k.num("buff", 0.1), (int) k.num("buffTicks", 80), "발키리의 가호", JobFx.outer(j), JobFx.inner(j));
                // 발키리의 날개 (가호 동안 등 뒤 — 1인칭에서는 숨김) · 내려앉는 깃털
                JobFx.valWings(sp, j, 0.95, (int) k.num("buffTicks", 80), 0.72);
                JobFx.feathers(sp.level(), V.up(sp.position(), 2.2), 6, 1.0, 0.17, j, 1.5, JobFx.hide1p(sp));
            };
            int[] pending = {targets.size()};
            for (LivingEntity e : targets) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.1);
                    if (last && --pending[0] <= 0) bless.run();
                });
            }
            if (last) {
                if (targets.isEmpty()) bless.run();
                else RuneKit.later(12, bless);
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
        DamageNumbers.showFace(sp, "§b§l" + k.name(), 2.4, 0, 0.2);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.4, 1.0);
        SkillFx.play(level, "item.trident.return", c, 1.2, 0.8);
        SkillFx.play(level, "block.bell.resonate", c, 1.6, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 라그나로크 (0.50.0 — 발할라를 바꿈)

    /** 라그나로크 네 베기의 눈앞 베기 기울기 (도 — JobFx.slash: 180 근처 = 오른쪽 → 왼쪽 · 0 근처 = 왼쪽 → 오른쪽 · 275 = 아래 → 위) */
    private static final double[] BEAT_TILT = {172, -8, 0, 275};
    /** 흙먼지 · 튀는 흙 (땅 빛) */
    private static final double[] DUST = {0.62, 0.56, 0.48};
    /** 갈라진 땅의 속 (어두운 틈) */
    private static final double[] RIFT = {0.03, 0.05, 0.09};
    /** 갈라짐이 앞으로 뻗는 빠르기 (칸/초) */
    private static final double SPLIT_SPEED = 30;

    /**
     * 라그나로크 (0.50.0 — 사용자 "검사 몸 쓰는 직업인데 갑자기 하늘 날아서 마법마냥 쏘는게 마음에 안듬": 하늘로 솟아 환영 검을 쏘던 발할라 대신 땅에서 몸으로):
     * 앞 (100°) dash 칸 안의 가장 가까운 보이는 적 1.5칸 앞까지 rush 틱에 돌진 (그 적을 바라봄 — 적이 없으면 보는 쪽 step 칸, 벽 · 낭떠러지 앞까지) → 그 자리에
     * 버티고 every 틱마다 네 베기 (beats — 바로 베기 · 되돌려 베기 · 한 바퀴 · 올려 베기, 둘레 radius 칸) → 칼을 두 손으로 머리 위로 들고 뛰어올랐다가 (hop 칸 ·
     * hopTicks 틱) finalDelay 틱 뒤 땅에 내리꽂음 (final — 둘레 finalRadius 칸 + 보는 쪽 finalRange 칸 · 폭 finalWidth 가 갈라짐 — 갈라짐이 닿는 때, 큰 숫자).
     * 그림: 돌진의 잔상 · 줄기 · 흙먼지, 베기마다 눈앞 큰 베기 (3인칭 · 다른 사람은 참격 판) · 회전은 허리 높이 둥근 베기, 내리꽂음은 땅 터짐 · 튀는 돌 · 앞으로
     * 뻗는 청백 균열. 같은 적은 10틱 넘게 띄움 (every · finalDelay — JobsTest)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        List<Double> beats = numbers(k, "beats", List.of(5.0, 5.0, 5.0, 5.0));
        int n = beats.size();
        int rush = Math.max(2, (int) k.num("rush", 6));
        int every = Math.max(1, (int) k.num("every", 10));
        int finalDelay = Math.max(3, (int) k.num("finalDelay", 14));
        int hopTicks = Math.max(2, Math.min(finalDelay - 1, (int) k.num("hopTicks", 9)));
        int lastBeat = rush + (n - 1) * every;
        int finalAt = lastBeat + finalDelay;
        double radius = k.num("radius", 4.5);
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        // 돌진할 곳: 앞 dash 칸 안의 가장 가까운 보이는 적 1.5칸 앞 (화면도 그 적 쪽으로) — 없으면 보는 쪽 step 칸
        List<LivingEntity> cone = RuneKit.coneTargets(sp, k.num("dash", 7) + 1.5, 100, 4);
        LivingEntity first = cone.isEmpty() ? null : cone.get(0);
        Vec3 f = Kit.facingOf(sp);
        double want = k.num("step", 3);
        if (first != null) {
            f = V.flat(V.sub(first.position(), c0));
            want = Math.max(0, Math.hypot(first.getX() - c0.x, first.getZ() - c0.z) - 1.5 - Kit.bodyPad(first));
            RuneKit.moveTo(sp, c0, V.up(first.position(), first.getBbHeight() * 0.6));
        }
        boolean held = Status.isRooted(sp) || Kit.ridingAny(sp);
        // 뛰는 중에 쓰면 발밑 땅 (6칸 안) 에서 — 돌진하며 내려앉음 (땅에서 싸우는 궁극기 · 공중에 떠 있지 않게)
        Double gy0 = RuneKit.solidGround(level0, V.up(c0, 0.1), 6);
        Vec3 base = gy0 != null && c0.y - gy0 > 0.05 ? new Vec3(c0.x, gy0, c0.z) : c0;
        double free = held || want < 0.3 ? 0 : Kit.reachFlat(level0, base, f, want);
        List<Kit.Spot> spots = free > 0.3 ? Kit.dashSpots(level0, base, f, free) : List.of();
        Vec3 stop = spots.isEmpty() ? base : spots.get(spots.size() - 1).p();
        Ult.sky(level0, c0, "rpg:ult_blade", finalAt + 25);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Kit.noFall(sp, finalAt + 30);
        // 몸: 돌진 (rush 틱) → 그 자리에 버팀 → 제자리에서 뛰어올라 내리꽂음 (hopTicks 틱 — 마지막 틱에 땅)
        int hop0 = finalAt - hopTicks;
        if (held) JobMove.run(sp, JobMove.hold(0, hop0), JobMove.hop(hop0, finalAt, from -> from, k.num("hop", 0.8)));
        else JobMove.run(sp, JobMove.to(0, rush, JobMove.at(stop), JobMove.OUT), JobMove.hold(rush, hop0), JobMove.hop(hop0, finalAt, from -> from, k.num("hop",
                0.8)));
        // 발동: 발을 굴러 땅이 울림 (작은 땅 터짐 · 흙먼지) · 짧게 솟는 투지 (0.4초 — 몸의 동작을 가리지 않게 길게 두지 않음) · 1인칭 눈앞 고리
        JobFx.smash(level0, V.up(c0, 0.05), 1.6, j, 0.35, Opt.of("alpha", 0.8));
        SkillFx.sparks(level0, V.up(c0, 0.2), DUST, 10, 3.5);
        JobFx.aura(sp, j, 2.4, 0.4, 0.6);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.4);
        SkillFx.play(level0, "mob.ravager.roar", c0, 1.4, 0.55);
        SkillFx.play(level0, "item.trident.riptide_1", c0, 1.1, 0.8);
        SkillFx.play(level0, "block.bell.resonate", c0, 1.3, 0.6);
        // 돌진: 지나온 자리마다 잔상 · 줄기 (1인칭에서는 숨김) · 발밑 흙먼지 · 1인칭 빠른 줄
        Vec3[] last = {c0};
        Vec3 face = f;
        for (int t = 1; t <= rush; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 now = sp.position();
                if (now.distanceToSqr(last[0]) > 0.04) {
                    if (tt % 2 == 1) RuneKit.shadeClone(sp, d, last[0], face, 1, SHADE, glow, Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.75));
                    JobFx.dash(d, V.up(last[0], 0.9), V.up(now, 0.9), j, 0.3, JobFx.hide1p(sp));
                    SkillFx.sparks(d, V.up(now, 0.12), DUST, 3, 2.2);
                    JobFx.speed(sp, j, tt == rush ? 0.22 : 0.1, 0.8);
                }
                last[0] = now;
            });
        }
        // 네 베기 (every 틱마다 — 그 자리 둘레 radius 칸)
        for (int i = 0; i < n; i++) {
            int ii = i;
            double mult = beats.get(i);
            RuneKit.later(rush + i * every, () -> {
                if (RuneKit.sameLife(sp, life)) beat(sp, j, P, mult, ii, radius, life);
            });
        }
        // 내리꽂기 준비: 칼을 두 손으로 머리 위로 (칼끝 반짝임 · 짧게 솟는 투지 · 바람 소리)
        RuneKit.later(hop0, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.aura(sp, j, 2.6, (hopTicks + 3) / 20.0, 0.6);
            RuneKit.rfx(d, "prop", V.up(sp.position(), 3.1), Opt.of("size", 0.32, "life", 0.4, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 20.0, "spinRate",
                    240.0, "color", WHITE, "grow", 0.6, "fade", 0.5, "view", 1.0, "owner", (double) sp.getId()));
            SkillFx.sparks(d, V.up(sp.position(), 0.2), DUST, 6, 2.5);
            SkillFx.play(d, "item.trident.riptide_2", sp.position(), 1.3, 0.7);
            SkillFx.play(d, "block.beacon.power_select", sp.position(), 1.6, 0.5);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) splitEarth(sp, j, k, P, life);
        });
        return true;
    }

    /** 라그나로크의 베기 하나 (i — 0 바로 베기 · 1 되돌려 베기 · 2 한 바퀴 · 3 올려 베기): 눈앞 큰 베기 (3인칭 · 다른 사람은 참격 판) · 둘레 radius 칸의 적 */
    private static void beat(ServerPlayer sp, Jobs.Job j, double P, double mult, int i, double radius, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        int kind = i % 4;
        double tilt = BEAT_TILT[kind];
        if (kind == 2) {
            // 한 바퀴: 허리 높이에서 몸째 도는 둥근 베기 + 땅의 옅은 고리 + 눈앞 큰 베기 (도는 쪽)
            JobFx.ring(level, V.up(c, 1.0), radius, j, false, 0.32, Opt.empty());
            JobFx.ring(level, V.up(c, 0.06), radius * 0.92, j, false, 0.42, Opt.of("alpha", 0.55, "coreAlpha", 0.45));
            JobFx.slash(sp, j, 2.5, 0.45, 2.3, tilt, 0.26);
            SkillFx.sparks(level, V.up(c, 0.15), DUST, 8, 3.2);
        } else {
            // 큰 베기 (바로 · 되돌려 — 몸 앞을 크게 · 올려 베기 — 아래에서 위로) · 참격 판 둘 (조금 크고 옅은 잔상)
            JobFx.slash(sp, j, 2.4, kind == 3 ? 0.05 : 0.3, kind == 3 ? 2.4 : 2.6, tilt, 0.3);
            Vec3 at = V.up(V.add(c, f, 1.1), kind == 3 ? 1.3 : 1.05);
            JobFx.slashCard(level, at, f, tilt, 3.4, 0.3, V.ZERO, j, JobFx.hide1p(sp));
            if (kind == 3) {
                // 올려 베기: 앞 땅에서 솟는 흙 · 튀는 돌
                Vec3 g = V.add(c, f, 1.6);
                RuneKit.rockBurst(level, V.up(g, 0.2), 5, 3.2, 0.16);
                SkillFx.sparks(level, V.up(g, 0.2), DUST, 8, 3.5);
            }
        }
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.75 + i * 0.08, 1.0);
        SkillFx.play(level, "item.spear.attack_miss", c, 0.95 + i * 0.12, 0.85);
        JobKit.shake(sp, 8, 0.12 + 0.03 * i, 0.15);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
            JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                if (kind == 3 && JobKit.pushable(e)) RuneKit.knock(e, f, 0.05, 0.42); // 올려 베기: 살짝 띄움 (연출 — 0.6 미만)
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.15);
            });
        }
    }

    /**
     * 라그나로크 마지막: 칼을 땅에 내리꽂음 — 둘레 finalRadius 칸 (지금) + 보는 쪽 finalRange 칸 · 폭 finalWidth (갈라짐이 SPLIT_SPEED 로 뻗어 닿는 때) 의 적마다
     * 한 번 final (큰 숫자 · 밀려남). 그림: 눈앞 세로 베기 · 땅 터짐 · 충격파 · 튀는 돌 · 흙먼지 · 번쩍임 · 화면 흔들림 · 앞으로 뻗는 균열 (rfx_fissure — 어두운 틈 ·
     * 청백 빛 · 흰 금) 과 그 위로 튀는 돌
     */
    private static void splitEarth(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double[] glow = JobKit.glow(j);
        double R = k.num("finalRadius", 5);
        double range = k.num("finalRange", 10);
        double half = k.num("finalWidth", 3) / 2;
        Vec3 g = new Vec3(c.x, RuneKit.groundAt(level, c, c.y), c.z);
        Vec3 tip = V.add(g, f, 1.2);
        JobFx.slash(sp, j, 2.3, -0.1, 3.0, 95.0, 0.34);
        JobFx.slashCard(level, V.up(V.add(c, f, 1.0), 1.4), f, 95.0, 3.8, 0.32, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.smash(level, V.up(tip, 0.05), R, j, 0.6, Opt.empty());
        JobFx.burst(level, V.up(tip, 0.4), 1.6, j, 0.35, Opt.empty());
        RuneKit.rockBurst(level, V.up(tip, 0.2), 10, 5.0, 0.2);
        SkillFx.sparks(level, V.up(tip, 0.2), DUST, 14, 5);
        Ult.flash(level, V.up(c, 1.0), glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, c, 16, 0.45, 0.55);
        SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.8, 1.2);
        SkillFx.play(level, "random.explode", c, 0.7, 0.9);
        SkillFx.play(level, "entity.player.attack.crit", c, 0.6, 1.0);
        // 앞으로 뻗는 균열: 조각마다 그 자리에 닿는 때 (어두운 틈 · 청백 빛 · 흰 금 — 땅에 눕힘) · 튀는 돌 · 흙먼지
        double len = Math.max(2, RuneKit.rayLength(level, V.up(g, 0.6), f, range));
        double piece = 2.5;
        double spin = Math.toDegrees(Math.atan2(f.z, -f.x));
        for (double s = 0.6; s < len; s += piece) {
            double a0 = s;
            double a1 = Math.min(len, s + piece * 1.3);
            int at = (int) Math.round(a0 / SPLIT_SPEED * 20);
            RuneKit.later(at, () -> {
                Vec3 mid = V.add(g, f, (a0 + a1) / 2);
                Vec3 m = new Vec3(mid.x, RuneKit.groundAt(level, mid, g.y), mid.z);
                double size = (a1 - a0) / 2 * 1.25;
                Opt base = Opt.of("dir", V.UP, "size", size, "life", 2.0, "fade", 0.35, "spin", spin + (Kit.rand() < 0.5 ? 180 : 0));
                RuneKit.rfx(level, "fissure", V.up(m, 0.035), base.with("cell", 0.0, "color", RIFT, "alpha", 0.92));
                RuneKit.rfx(level, "fissure", V.up(m, 0.055), base.with("cell", 1.0, "color", glow, "alpha", 0.95));
                RuneKit.rfx(level, "fissure", V.up(m, 0.075), base.with("cell", 2.0, "color", WHITE, "alpha", 1.0));
                RuneKit.glowCrack(level, m, 1.5, glow, WHITE, Opt.of("life", 1.6, "v", a0 % 5 < 2.5 ? 1.0 : 0.0));
                RuneKit.rockBurst(level, V.up(m, 0.15), 3, 3.0, 0.15);
                SkillFx.sparks(level, V.up(m, 0.15), DUST, 4, 2.5);
            });
        }
        RuneKit.later((int) Math.round(len / SPLIT_SPEED * 20), () -> SkillFx.play(level, "item.mace.smash_ground", V.add(g, f, len), 0.9, 0.8));
        // 피해: 둘레 R 은 지금 · 균열 위는 닿는 때 (적마다 한 번)
        double mult = k.num("final", 20);
        Set<UUID> done = new HashSet<>();
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, R, 3)) {
            done.add(e.getUUID());
            JobKit.strike(sp, e, j, P, mult, false, true, life, 0, d -> finalHitFx(e, j, c, cnt));
        }
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(g, 0.6), f, len, half)) {
            LivingEntity e = h.e();
            if (!done.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, mult, false, true, life, (int) Math.round(h.t() / SPLIT_SPEED * 20), d -> finalHitFx(e, j, c, cnt));
        }
    }

    /** 내리꽂음에 맞은 적: 타격 그림 · 터짐 (8명까지) · 밀려남 (가운데에서 바깥으로 — 연출) */
    private static void finalHitFx(LivingEntity e, Jobs.Job j, Vec3 c, int[] cnt) {
        if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), c)), 0.35, 0.3);
        if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
        JobFx.hit(e, j, 1.7);
        JobFx.burst(lv, Kit.bodyPoint(e), 1.0 + Kit.bodyPad(e) * 0.5, j, 0.32, Opt.empty());
    }
}
