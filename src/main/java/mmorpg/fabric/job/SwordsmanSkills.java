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
 */
final class SwordsmanSkills {
    private SwordsmanSkills() {}

    /** 잔상 · 환영 검의 그림자 몸 (청백 — 풀밭 위에서도 보이게) */
    private static final double[] SHADE = {0.16, 0.42, 0.5};
    private static final double[] WHITE = RuneKit.WHITE;
    /** 연참 베인 자국 기울기 (가로 · 반대 가로 · 크게) */
    private static final double[] CUT = {-8, 172, 20};
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
            // 검세를 씀: 칼끝에 모이는 칼날 빛
            RuneKit.Hand h = RuneKit.handOf(sp);
            SkillFx.ring(sp.level(), V.up(h.hand(), 0.15), h.f(), JobKit.glow(j), 0.6, 0.3, 1.5);
            SkillFx.play(sp.level(), "block.bell.hit", sp.position(), 1.8, 0.35);
        }
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
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
                waveSlash(sp, j, P, mult, wave, reach, arc, glow, core, life);
                return;
            }
            boolean flip = i % 2 == 1;
            SkillFx.arc(level, V.up(V.add(eye, f, 1.5), -0.3), f, Opt.of("shape", last ? "heavy" : "arc", "flip", flip, "size", last ? 2.9 : 2.5, "life", 0.24,
                    "speed", 3.0, "spin", flip ? 10.0 : -10.0, "color", glow, "aura", core));
            if (last) {
                RuneKit.rfx2(level, "wave", V.up(V.add(eye, f, 2.4), -0.35), Opt.of("dir", f, "speed", 5.0, "size", 1.5, "life", 0.22, "grow", 0.3, "fade", 0.6,
                        "spin", -4.0), glow, WHITE);
                SkillFx.play(level, "entity.player.attack.sweep", eye, 1.1, 0.8);
            }
            SkillFx.play(level, "item.spear.attack_miss", eye, 1.15 + i * 0.12, 0.9);
            int[] cnt = {0};
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                    // 맞은 자리 (파티클 예산): 자국 8명 · 불꽃 3명까지
                    int c = cnt[0]++;
                    Vec3 at = Kit.bodyPoint(e);
                    if (c < 8) SkillFx.cutMark(level, at, V.unit(V.sub(at, sp.getEyePosition())), CUT[i % 3], 0.07, glow, core, last ? 1.2 : 1.0);
                    if (c < 3) SkillFx.sparks(level, at, core, 4, 4);
                });
            }
        });
        return true;
    }

    /** 연참 마지막 베기의 검기: 눈앞에서 앞으로 날아가는 초승달 검기 (rpg:rfx_wave) + 지나간 자리 빛줄기 — 선 가까이 (폭 wave.width) · 부채꼴 안의 적 (적마다 한 번) */
    private static void waveSlash(ServerPlayer sp, Jobs.Job j, double P, double mult, Obj wave, double reach, double arc, double[] glow, double[] core,
            String life) {
        ServerLevel level = sp.level();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        Vec3 dir = V.unit(new Vec3(f.x, view.y * 0.3, f.z));
        Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.1);
        double half = wave.numOr("width", 1.4) / 2;
        double len = Math.max(1, RuneKit.rayLength(level, from, dir, wave.numOr("range", 6)));
        double speed = 30;
        double s0 = Math.min(1.4, len);
        RuneKit.crescent(level, V.add(from, dir, 1.0), dir, 1.9, glow, core);
        RuneKit.rfx2(level, "wave", V.add(from, dir, s0), Opt.of("dir", dir, "speed", speed, "size", 1.3, "life", Math.max(0.05, (len - s0) / speed), "fade", 0.15,
                "spin", -8.0), glow, WHITE);
        int seg = 0;
        for (double s = 1.5; s < len; s += 1.5) {
            double delay = s / speed;
            SkillFx.fx(level, "rpg:light_cut", V.add(from, dir, s), Opt.of("dir", dir, "size", 0.9, "thick", 0.14, "life", delay + 0.3, "delay", delay, "color",
                    seg++ % 2 == 0 ? glow : core, "alpha", 0.85));
        }
        SkillFx.play(level, "item.trident.throw", from, 1.3, 0.9);
        SkillFx.play(level, "item.spear.attack_miss", from, 1.6, 0.7);
        Set<UUID> done = new HashSet<>();
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, dir, len, half)) {
            LivingEntity e = h.e();
            if (!done.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, mult, false, false, life, (int) Math.round(h.t() / speed * 20), d -> waveMark(sp, e, dir, glow, core, cnt));
        }
        for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
            if (!done.add(e.getUUID())) continue;
            JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> waveMark(sp, e, dir, glow, core, cnt));
        }
    }

    private static void waveMark(ServerPlayer sp, LivingEntity e, Vec3 dir, double[] glow, double[] core, int[] cnt) {
        int c = cnt[0]++;
        if (c >= 6 || !(e.level() instanceof ServerLevel level)) return;
        Vec3 at = Kit.bodyPoint(e);
        SkillFx.cutMark(level, at, dir, 70, 0.08, glow, core, 1.15);
        if (c < 3) SkillFx.sparks(level, at, core, 5, 4);
    }

    // ------------------------------------------------------------ 섬보

    /**
     * 섬보: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 t 틱에 미끄러지듯 지나감 (한 틱에 한 마디씩 옮김 — 지나온 자리에 청백 잔상 · 빛줄기), 길 가까이 (폭 width) 의
     * 적을 지날 때 벰 (mult — 2틱 늦게 그어지는 X 자국). 끝나면 critFor 틱 안의 다음 칼 평타 한 번은 치명타 (손에 칼빛 · "치명 준비").
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
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        // 출발: 쓴 자리의 잔상 · 발밑 칼빛 고리 · 소리
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, glow, Opt.of("life", 0.5, "fade", 0.6, "alpha", 0.85));
        SkillFx.shock(level, start, glow, 0.9, 0.3);
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
                RuneKit.streak(d, V.up(prev, 1.0), V.up(to, 1.0), core, 0.12, 0.3);
                RuneKit.streak(d, V.up(prev, 0.6), V.up(to, 0.6), glow, 0.26, 0.35);
            });
        }
        // 지나가며 벰: 길 가까이 적 — 지나는 마디의 틱에
        double half = k.num("width", 1.6) / 2;
        double mult = k.num("mult", 3);
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(start, 1.0), f, len, half)) {
            LivingEntity e = h.e();
            int at = (int) Math.min(steps - 1, Math.floor(h.t() / Math.max(0.1, len) * steps));
            JobKit.strike(sp, e, j, P, mult, false, false, life, at, d -> RuneKit.later(2, () -> {
                if (e.isRemoved() || cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                Vec3 v = V.unit(V.sub(b, sp.getEyePosition()));
                SkillFx.fx(lv, "rpg:slash_x", b, Opt.of("dir", v, "size", 1.2 * RuneKit.nearK(sp, b, 3, 0.45), "life", 0.28, "color", glow, "alpha", 1.0));
                SkillFx.cutMark(lv, b, v, 35, 0.05, WHITE, core, 1.1);
                SkillFx.sparks(lv, b, core, 4, 5);
                SkillFx.play(lv, "game.player.attack.strong", b, 1.4, 0.7);
            }));
        }
        // 끝: 다음 평타 치명타 확정 (칼끝 빛 · 글자)
        int critFor = (int) k.num("critFor", 60);
        RuneKit.later(steps, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobTraits.sureCritFor(sp, critFor);
            RuneKit.Hand h = RuneKit.handOf(sp);
            RuneKit.rfx(sp.level(), "prop", V.up(h.hand(), 0.3), Opt.of("size", 0.32, "life", 0.25, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 20.0,
                    "color", WHITE, "grow", 0.8, "fade", 0.6));
            SkillFx.ring(sp.level(), V.up(h.hand(), 0.2), h.f(), glow, 0.5, 0.3, 1.5);
            DamageNumbers.showFace(sp, "§b§l치명 준비", 2.4, 0.9, -0.35);
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.7, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 원월참

    /**
     * 원월참: 검세를 모두 써서 (하나당 + perStack) 칼을 크게 한 바퀴 — 둘레 radius 칸 둥근 참격 두 번 (hits, gap 틱 간격 — 안쪽에서 바깥으로 퍼짐).
     * 그림: 몸 둘레 초승달 참격 여섯 (도는 쪽 — 1인칭 화면 앞의 것은 흐리게) · 바닥 초승달 고리 · 범위 고리, 검세를 쓴 만큼 발밑 칼날 문양이 밝게
     */
    static boolean moon(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> hits = numbers(k, "hits", List.of(4.5, 4.5));
        int gap = (int) k.num("gap", 10);
        double radius = k.num("radius", 4.5);
        double per = k.num("perStack", 0.08);
        int used = JobTraits.useStacks(sp, 99);
        double bonus = 1 + per * used;
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        if (used > 0) {
            DamageNumbers.showFace(sp, "§b§l검세 ×" + used + " §r§7+" + Math.round(per * used * 100) + "%", 2.4, 0.9, -0.3);
            RuneKit.domainSigil(level0, sp.position(), radius * 0.75, "blade", glow, WHITE, Opt.of("life", (gap + 8) / 20.0, "spinRate", 220.0, "grow", 0.15,
                    "alpha", Math.min(0.95, 0.45 + 0.1 * used), "fade", 0.3));
        }
        SkillFx.play(level0, "entity.player.attack.sweep", sp.position(), 0.8, 1.0);
        int n = hits.size();
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            double q = n <= 1 ? 1 : (double) i / (n - 1);
            double r0 = radius * (0.6 + 0.4 * q);
            Vec3 fc = Kit.facingOf(sp);
            for (int s = 0; s < 6; s++) {
                double a = s / 6.0 * Math.PI * 2 + i * 0.5;
                Vec3 dir = new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2));
                Vec3 at = new Vec3(c.x + Math.cos(a) * r0 * 0.6, c.y + 1.0, c.z + Math.sin(a) * r0 * 0.6);
                boolean ahead = V.dot(V.flat(V.sub(at, c)), fc) > 0.6;
                int ss = s;
                RuneKit.later(ss / 2, () -> SkillFx.arc(level, at, dir, Opt.of("shape", "heavy", "flip", i % 2 == 1, "size", 1.9 + 0.5 * q, "life", 0.26,
                        "speed", 2.4, "spin", i % 2 == 0 ? 30.0 : -30.0, "color", glow, "aura", ss % 2 == 0 ? core : null, "alpha", ahead ? 0.5 : 1.0)));
            }
            RuneKit.crescent(level, V.up(c, 0.08), V.UP, r0, glow, core, i * 140.0, 0.4);
            RuneKit.zonePulse(level, c, i % 2 == 0 ? glow : core, r0, 0.4);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.9 + i * 0.15, 1.0);
            SkillFx.play(level, "item.spear.attack_miss", c, 1.0 + i * 0.2, 0.8);
            double mult = hits.get(i) * bonus;
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                    if (cnt[0]++ >= 8) return;
                    Vec3 b = Kit.bodyPoint(e);
                    SkillFx.fx(level, "rpg:slash_x", b, Opt.of("dir", V.flat(V.sub(e.position(), c)), "size", 0.95, "life", 0.22, "color", glow, "alpha", 1.0));
                    if (cnt[0] <= 3) SkillFx.sparks(level, b, core, 3, 4);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 명경지수

    /**
     * 명경지수: dur 틱 태세 — 칼 평타가 두 번 베기 (두 번째 = 평타 × echo · JobTraits), 검세가 사라지지 않음, 받는 피해 −guard (태세 동안만).
     * 그림: 발밑 고요한 칼날 문양 · 차례로 퍼지는 잔물결 고리 셋 · 1인칭 고리 · 글자, 태세 동안 8틱마다 발밑 고리 · 칼의 물빛 (JobTraits)
     */
    static boolean mirror(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 160);
        JobTraits.startStance(sp, k, dur);
        Guards.set(sp, dur, k.num("guard", 0.15), null, p -> JobTraits.stanceOn(p, k.id()));
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        RuneKit.domainSigil(level, c, 2.6, "blade", glow, WHITE, Opt.of("life", 1.4, "spinRate", 30.0, "grow", 0.1, "alpha", 0.75, "fade", 0.4));
        String life = RuneKit.lifeToken(sp);
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(i * 6, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.zonePulse(sp.level(), sp.position(), ii % 2 == 0 ? glow : core, 2.2 + ii * 0.9, 0.6);
            });
        }
        RuneKit.selfCue(sp, glow, core, true);
        DamageNumbers.showFace(sp, "§b§l명경지수", 2.4, 0, 0.2);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.4, 1.0);
        SkillFx.play(level, "item.trident.return", c, 1.2, 0.8);
        SkillFx.play(level, "block.bell.resonate", c, 1.6, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 천검난무

    /**
     * 천검난무: 청백 하늘 · 발밑 칼날 문양 · 하늘 문양, 머리 뒤로 환영 검 swords 자루가 솟아 두 겹 반원 (rise 틱) → every 틱마다 (volleys 번) 둘레 radius 칸의
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
        RuneKit.domainSigil(level0, c0, 5.0, "blade", glow, WHITE, Opt.of("life", (finalAt + 4) / 20.0, "spinRate", 60.0, "grow", 0.2, "alpha", 0.8, "fade", 0.15));
        Vec3 skyAt = RuneKit.skyPoint(sp, c0, 12, 21, 6, 22);
        RuneKit.skyPlate(sp, level0, "sigil", skyAt, 5.0, glow, WHITE, Opt.of("cell", (double) RuneKit.SIGIL.get("blade"), "life", (finalAt + 6) / 20.0,
                "spinRate", 40.0, "grow", 0.15, "alpha", 0.7, "fade", 0.3));
        SkillFx.rune(level0, c0, Opt.of("size", 2.0, "life", rise / 20.0 + 0.3, "color", glow, "alpha", 0.85, "rate", 160.0, "progress", 1.0, "grow", 0.2));
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", c0, 1.2, 0.9);
        SkillFx.play(level0, "beacon.power", c0, 1.5, 0.8);
        SkillFx.play(level0, "block.bell.resonate", c0, 1.2, 0.8);
        // 환영 검 반원 (3틱마다 다시 그림 — 솟는 동안 아래에서 올라옴, 날아간 만큼 줄어듦)
        for (int t = 0; t <= lastVolley; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || left[0] <= 0) return;
                double q = Math.min(1, (tt + 3.0) / Math.max(1, rise));
                drawHalo(sp, left[0], q, glow, core);
                if (tt < rise && tt % 6 == 0) SkillFx.play(sp.level(), "block.bell.hit", sp.position(), 1.0 + q, 0.35);
            });
        }
        for (int v = 0; v < volleys; v++) {
            int vv = v;
            RuneKit.later(rise + v * every, () -> {
                if (RuneKit.sameLife(sp, life)) volley(sp, j, P, volleyMult, per, left, radius, targets, vv, glow, core, life);
            });
        }
        RuneKit.later(lastVolley + 2, () -> {
            if (RuneKit.sameLife(sp, life)) gatherFx(sp, gather - 2, glow, core, life);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) finalCut(sp, j, k, P, skyAt, glow, core, life);
        });
        return true;
    }

    /** 환영 검 반원 (머리 뒤 · 위 — 칼끝이 바깥으로): 바깥 겹 (반지름 2.5) 이 조금 더 많음, q (0 ~ 1) = 솟은 정도 */
    private static void drawHalo(ServerPlayer sp, int n, double q, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Vec3 center = V.up(V.add(sp.getEyePosition(), f, -0.8), 0.5 - (1 - q) * 1.6);
        Vec3 vel = RuneKit.bodyVel(sp);
        int outer = Math.min(n, (n + 1) / 2 + n / 8);
        for (int i = 0; i < n; i++) {
            boolean out = i < outer;
            int m = out ? outer : n - outer;
            int idx = out ? i : i - outer;
            double th = Math.toRadians(-80 + 160.0 * (m <= 1 ? 0.5 : (double) idx / (m - 1)));
            Vec3 radial = V.add(V.scale(right, Math.sin(th)), V.UP, Math.cos(th));
            Vec3 at = V.add(center, radial, out ? 2.5 : 1.7);
            RuneKit.rfx2(level, "sword", at, Opt.of("dir", radial, "size", out ? 0.75 : 0.6, "life", 0.17, "alpha", 0.5 + 0.45 * q, "vel", vel), glow, core);
        }
    }

    /** 한 번의 일제 사격: 둘레 radius 칸의 보이는 적 (가까운 순 max 명) 마다 검 per 자루 (그림은 앞 둘 per · 다음 둘 2 · 나머지 1 자루) */
    private static void volley(ServerPlayer sp, Jobs.Job j, double P, double mult, int per, int[] left, double radius, int max, int v, double[] glow, double[] core,
            String life) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Vec3 origin = V.up(V.add(eye, f, -0.8), 1.6);
        List<LivingEntity> list = Kit.visibleFrom(sp, eye, JobKit.near(sp, radius, 32));
        if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
        left[0] = Math.max(0, left[0] - per);
        SkillFx.play(level, "item.trident.throw", eye, 1.2 + v * 0.08, 0.9);
        SkillFx.play(level, "block.bell.hit", eye, 1.4 + v * 0.06, 0.35);
        if (list.isEmpty()) {
            // 적이 없으면 둘레 땅에 꽂힘 (피해 없음)
            for (int s = 0; s < per; s++) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 g = V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 3 + Kit.rand() * 3);
                Vec3 ground = new Vec3(g.x, RuneKit.groundAt(level, g, sp.getY()), g.z);
                int t = fly(level, V.add(origin, right, (s - (per - 1) / 2.0) * 0.7), V.up(ground, 0.3), glow, core);
                RuneKit.later(t, () -> {
                    SkillFx.shock(level, ground, glow, 0.5, 0.3);
                    SkillFx.sparks(level, V.up(ground, 0.3), core, 3, 3);
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
                Vec3 from = V.add(V.add(origin, right, (s - (nShow - 1) / 2.0) * 0.9 + (i % 2 == 0 ? 0.3 : -0.3)), V.UP, (s % 2) * 0.5);
                Vec3 aim = V.add(to, new Vec3((Kit.rand() - 0.5) * 0.4, (Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.5) * 0.4));
                int t = fly(level, from, aim, glow, core);
                if (s == 0) first = t;
            }
            JobKit.strike(sp, e, j, P, mult, false, false, life, first, d -> {
                if (!RuneKit.fxQuota("jobBladeRain", 6) || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                SkillFx.fx(lv, "rpg:slash_x", b, Opt.of("dir", V.unit(V.sub(b, sp.getEyePosition())), "size", 0.9 * RuneKit.nearK(sp, b, 3, 0.45), "life", 0.24,
                        "color", glow, "alpha", 1.0));
                SkillFx.sparks(lv, b, core, 4, 4);
            });
        }
    }

    /** 날아가는 환영 검 (rpg:rfx_sword — 칼끝이 가는 쪽) + 옅은 빛줄기. 도착까지 틱 */
    private static int fly(ServerLevel level, Vec3 from, Vec3 to, double[] glow, double[] core) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.1) return 0;
        double life = Math.max(0.1, len / FLY_SPEED);
        Vec3 u = V.unit(d);
        RuneKit.rfx2(level, "sword", from, Opt.of("dir", u, "speed", len / life, "size", 0.75, "life", life), glow, core);
        RuneKit.streak(level, from, to, core, 0.06, life + 0.1, 0.6, 0);
        return Math.max(1, (int) Math.round(life * 20));
    }

    /** 모임: 둘레 열두 곳에서 검이 눈앞으로 날아와 모이고 (ticks 틱), 그 자리에 거대한 검이 커짐 */
    private static void gatherFx(ServerPlayer sp, int ticks, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 point = V.up(V.add(sp.position(), f, 2.6), 1.6);
        double flyLife = Math.max(0.2, ticks * 0.6 / 20);
        for (int i = 0; i < 12; i++) {
            double a = i / 12.0 * Math.PI * 2;
            Vec3 from = V.up(V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 4.5), 1.0 + (i % 3) * 0.9);
            Vec3 d = V.sub(point, from);
            RuneKit.rfx2(level, "sword", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / flyLife, "size", 0.7, "life", flyLife, "alpha", 0.9), glow, core);
        }
        SkillFx.play(level, "block.beacon.power_select", sp.position(), 1.4, 0.8);
        for (int t = (int) Math.round(ticks * 0.5); t < ticks; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 ff = Kit.facingOf(sp);
                Vec3 hilt = V.up(V.add(sp.position(), ff, 2.6), 0.6);
                double q = (tt + 2.0) / ticks;
                double ls = 1.2 + q * 3.4;
                RuneKit.rfx2(sp.level(), "sword", V.up(hilt, ls / 2), Opt.of("dir", V.UP, "size", ls / 2, "life", 0.13), glow, WHITE);
                if (tt % 4 == 0) SkillFx.play(sp.level(), "block.bell.hit", hilt, 1.3 + q * 0.6, 0.35);
            });
        }
    }

    /** 일섬: 거대한 검기가 앞 finalRange 칸을 가름 (폭 finalWidth) — 번쩍임 · 하늘이 갈라짐 · 하늘 문양의 섬광 · 땅이 갈라져 빛남, 큰 숫자 */
    private static void finalCut(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 skyAt, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = V.up(V.add(sp.position(), f, 0.8), 1.2);
        double len = Math.max(1, RuneKit.rayLength(level, from, f, k.num("finalRange", 12)));
        double half = k.num("finalWidth", 3.2) / 2;
        Vec3 eye = sp.getEyePosition();
        double speed = 40;
        // 거대한 검기 (눈앞 1.6칸에서 나타나 날아감) + 화면을 가로지르는 빛 + 앞으로 뻗는 긴 빛줄기
        RuneKit.rfx2(level, "wave", V.add(from, f, 1.6), Opt.of("dir", f, "speed", speed, "size", half * 2 * 0.9, "life", Math.max(0.05, (len - 1.6) / speed),
                "fade", 0.15), glow, WHITE);
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(eye, f, 3.0), -0.35), Opt.of("dir", V.rightOf(f), "size", 5.0, "thick", 0.32, "life", 0.42, "delay", 0.05,
                "color", glow, "alpha", 1.0));
        SkillFx.fx(level, "rpg:light_cut", V.add(from, f, len / 2), Opt.of("dir", f, "size", len / 2, "thick", 0.3, "life", 0.6, "delay", 0.1, "color", core,
                "alpha", 1.0));
        RuneKit.flyStreak(level, from, V.add(from, f, len), speed, glow, core, 1.4, 0.5);
        Ult.flash(level, from, glow, Opt.empty(), sp);
        SkillFx.play(level, "item.trident.thunder", from, 1.5, 0.8);
        SkillFx.play(level, "firework.large_blast", from, 1.2, 0.8);
        SkillFx.play(level, "item.spear.attack_miss", eye, 0.8, 1.0);
        RuneKit.shakeAround(level, from, 12, 0.3, 0.35);
        // 하늘이 둘로 갈라짐 · 하늘 문양의 섬광
        Vec3 sky0 = V.up(V.add(eye, f, 15), 7.5);
        SkillFx.fx(level, "rpg:light_cut", sky0, Opt.of("dir", f, "size", 13.0, "thick", 0.9, "life", 1.1, "delay", 0.6, "color", glow, "alpha", 0.95));
        SkillFx.fx(level, "rpg:light_cut", sky0, Opt.of("dir", f, "size", 12.5, "thick", 0.3, "life", 1.0, "delay", 0.55, "color", WHITE, "alpha", 1.0));
        RuneKit.skyFlare(level, skyAt, 3.8, glow, WHITE, Opt.of("life", 0.5));
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
                Vec3 b = Kit.bodyPoint(e);
                Vec3 v = V.unit(V.sub(b, sp.getEyePosition()));
                SkillFx.fx(lv, "rpg:slash_x", V.add(b, v, -0.2), Opt.of("dir", v, "size", 2.0 * RuneKit.nearK(sp, b, 3, 0.45), "life", 0.34, "color", glow,
                        "alpha", 1.0));
                SkillFx.burst(lv, b, core, 1.2, 0.3);
                SkillFx.sparks(lv, b, core, 8, 6);
            });
        }
    }
}
