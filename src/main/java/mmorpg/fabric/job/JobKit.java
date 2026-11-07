package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 0.27.0 직업 스킬 도구: 직업 색 (두 빛 — 피해 숫자 그라데이션 · 연출), 직업 스킬 피해 한 번 (P × 배율 × 스탯 트리 공격력 × 가호, 숫자는 직업 색 그라데이션),
 * 같은 적을 여러 번 칠 때 몹 무적 시간 (10틱) 을 피해 넣기, 화면 흔들기.
 */
public final class JobKit {
    private JobKit() {}

    /** 피해 숫자 그라데이션 {0xRRGGBB, 0xRRGGBB} (직업 색 두 빛) */
    public static int[] grad(Jobs.Job j) {
        return new int[] {Jobs.rgb(j.color()[0]), Jobs.rgb(j.color()[1])};
    }

    /** 연출 빛 (직업 첫째 색) */
    public static double[] glow(Jobs.Job j) {
        return j.color()[0];
    }

    /** 연출 심지 (직업 둘째 색) */
    public static double[] core(Jobs.Job j) {
        return j.color()[1];
    }

    /**
     * 직업 스킬 피해 한 번: P × mult × 스탯 트리 공격력 (평타 · 스킬 — 7단계) × 가하는 피해 배율 (0.19.0 세계수의 가호). magic = 마법 피해 (방어구 무시),
     * big = 궁극기 마지막 타 (숫자 1.4배 + "!"). 실제로 깎인 체력 (0 = 안 들어감 — 맞을 수 없는 대상 · 무적 시간)
     */
    public static double hit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, boolean magic, boolean big) {
        if (e == null || !Kit.canHit(sp, e) || !(mult > 0)) return 0;
        // 0.29.0 암영의 첫 타 (직업 스킬 첫 타 하나 — 치명타 확정 · × first): 큰 숫자 · 치명타 연출
        double veil = JobTraits3.firstHit(sp, j);
        // 0.30.0 수호기사 반격 게이지 (다음 공격 직업 스킬의 첫 타에 더함)
        double counter = JobTraits4.counterTake(sp, j);
        // 0.51.0 소드마스터 치명 (투지 8 에서 쓴 스킬 — 위력 × 1.3 · 치명 숫자)
        double crit = JobTraits5.critMult(sp, j);
        double frost = JobTraits16.hitMult(sp, e, j); // 2026-11-07 프로스트 레이더 깨짐 (이 사람이 얼린 적 +30%)
        double range = JobTraits9.rangeMult(sp, e, j); // 0.51.0 배틀 아처 난사 (가까운 적 +20% · 먼 적 −30%)
        double exec = JobTraits12.hitMult(sp, e, j); // 0.51.0 엑시큐셔너 처형 덤 (잃은 체력) · 선고 · 섀도 스토크
        double base = P * mult * veil * crit * frost * range * exec * mmorpg.fabric.stattree.StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
        double flat = flatAdd; // 2026-11-06 어벤저 앙갚음 (strikeFlat — 이 피해에 더하는 고정 피해)
        double dmg = base + counter + flat;
        float before = e.getHealth();
        boolean bigger = big || veil > 1 || counter > 0 || flat > base * 0.25;
        JobTraits8.beforeJobHit(sp, e, j); // 0.51.0 네크로맨서: 이 피해로 쓰러지면 망자로
        boolean landed;
        try {
            landed = Kit.applyJobDamage(sp, e, dmg, veil > 1 || crit > 1 || critShown, magic ? Kit.Cause.MAGIC : Kit.Cause.ATTACK, bigger ? DamageNumbers.Style.JOB_BIG
                    : DamageNumbers.Style.JOB, grad(j));
        } finally {
            JobTraits8.afterJobHitDone();
        }
        if (!landed) {
            if (veil > 1) JobTraits3.firstHitMissed(sp);
            if (counter > 0) JobTraits4.counterMissed(sp);
            return 0;
        }
        if (veil > 1) JobTraits3.firstHitLanded(sp, e, j);
        if (counter > 0) JobTraits4.counterLanded(sp, e, j, counter);
        float after = e.isRemoved() ? 0 : e.getHealth();
        double got = before > after ? before - after : dmg;
        JobTraits5.afterJobHit(sp, e, j, got); // 0.51.0 블러드 나이트 흡혈 (피의 갈증 · 광란)
        JobTraits16.afterJobHit(sp, e, j, got); // 2026-11-07 프로스트 레이더 서리 +1 · 얼린 적이면 깨짐 (얼음 조각)
        JobTraits12.afterJobHit(sp, e, j, got); // 0.51.0 소울 리퍼 영혼 (+1 · 가득이면 해방) · 엑시큐셔너 처형
        return got;
    }

    /** 2026-11-06 이번 피해에 더할 고정 피해 (체력 양 — 어벤저 앙갚음, {@link #strikeFlat} 가 정했다가 지움) */
    private static double flatAdd;

    /**
     * 2026-11-06 {@link #strike} 와 같음 — 피해에 고정 피해 flat (체력 양 — 어벤저 앙갚음: 배율 · 공격력에 곱하지 않고 더함, 반격 게이지처럼) 을 더함. magic 이면 방어구
     * 무시 (레크닝 가운데 — 고정 피해)
     */
    public static void strikeFlat(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, double flat, boolean magic, boolean big, String life, int delay,
            java.util.function.DoubleConsumer then) {
        if (e == null || !(mult > 0)) return;
        hitLater(sp, e, life, now() + Math.max(0, delay), () -> {
            double d;
            flatAdd = Math.max(0, flat);
            try {
                d = hit(sp, e, j, P, mult, magic, big);
            } finally {
                flatAdd = 0;
            }
            if (d > 0 && then != null) then.accept(d);
        });
    }

    /** 0.51.0 치명 숫자로 보일 피해 (트릭 샷의 튄 화살 · 데드아이 셋째 — 배율에 치명 몫을 넣어 둠): 이 동안 hit · hitSplit 의 숫자가 치명타 꼴 */
    private static boolean critShown;

    /** 0.51.0 {@link #strike} 와 같음 — 숫자만 치명타 꼴 (피해는 배율 그대로) */
    public static void strikeCrit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, boolean magic, boolean big, String life, int delay,
            java.util.function.DoubleConsumer then) {
        if (e == null || !(mult > 0)) return;
        hitLater(sp, e, life, now() + Math.max(0, delay), () -> {
            double d;
            critShown = true;
            try {
                d = hit(sp, e, j, P, mult, magic, big);
            } finally {
                critShown = false;
            }
            if (d > 0 && then != null) then.accept(d);
        });
    }

    /**
     * 같은 적을 여러 번 칠 때: want 틱 (또는 그 적의 무적 시간이 끝난 뒤) 에 fn — 그 사이 쓴 사람이 죽거나 차원을 옮기면 (삶 표시 life) · 적이 사라지면 취소
     */
    public static void hitLater(ServerPlayer sp, LivingEntity e, String life, long want, Runnable fn) {
        Kit.hitAt(sp, e, Kit.reserveHit(sp, e, want), () -> RuneKit.sameLife(sp, life) && !e.isRemoved(), fn);
    }

    /**
     * 직업 스킬 피해 한 번을 delay 틱 뒤 (0 = 지금 — 그 적이 무적 시간이면 끝난 뒤로 미룸, 같은 적을 여러 번 쳐도 하나도 버려지지 않게).
     * 들어가면 then (깎인 체력 — 연출 · 각인)
     */
    public static void strike(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, boolean magic, boolean big, String life, int delay,
            java.util.function.DoubleConsumer then) {
        if (e == null || !(mult > 0)) return;
        hitLater(sp, e, life, now() + Math.max(0, delay), () -> {
            double d = hit(sp, e, j, P, mult, magic, big);
            if (d > 0 && then != null) then.accept(d);
        });
    }

    /**
     * 0.28.0 빠른 연타 한 번 (메이플식 쌓이는 숫자): 피해 P × mult 를 한 번에 넣고 숫자는 split 줄 (줄마다 ÷ split, gap 틱마다 한 줄 위로) — 몹 무적 시간에
     * 막히지 않음. 깎인 체력 (0 = 안 들어감)
     */
    public static double hitSplit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, int split, int gap, boolean magic, boolean big) {
        return hitSplit(sp, e, j, P, mult, split, gap, null, magic, big);
    }

    /**
     * 같은 것 — 0.51.0 줄마다 다른 몫 (weights — 줄 수만큼, 합에 대한 몫: 글래디에이터 도끼 난타의 세 번째 내려침 · null 이면 같게). 피해는 한 번에 P × mult
     */
    public static double hitSplit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, int split, int gap, double[] weights, boolean magic, boolean big) {
        if (e == null || !Kit.canHit(sp, e) || !(mult > 0)) return 0;
        double veil = JobTraits3.firstHit(sp, j); // 0.29.0 암영의 첫 타
        double counter = JobTraits4.counterTake(sp, j); // 0.30.0 반격 게이지
        double crit = JobTraits5.critMult(sp, j); // 0.51.0 소드마스터 치명
        double frost = JobTraits16.hitMult(sp, e, j); // 2026-11-07 프로스트 레이더 깨짐
        double range = JobTraits9.rangeMult(sp, e, j); // 0.51.0 배틀 아처 난사
        double exec = JobTraits12.hitMult(sp, e, j); // 0.51.0 엑시큐셔너 처형 덤
        double base = P * mult * veil * crit * frost * range * exec * mmorpg.fabric.stattree.StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
        double flat = flatAdd; // 2026-11-06 어벤저 앙갚음 (strikeFlat — 이 피해에 더하는 고정 피해)
        double dmg = base + counter + flat;
        float before = e.getHealth();
        boolean bigger = big || veil > 1 || counter > 0 || flat > base * 0.25;
        JobTraits8.beforeJobHit(sp, e, j); // 0.51.0 네크로맨서: 이 피해로 쓰러지면 망자로
        boolean landed;
        try {
            landed = Kit.applyJobDamage(sp, e, dmg, veil > 1 || crit > 1 || critShown, magic ? Kit.Cause.MAGIC : Kit.Cause.ATTACK, bigger ? DamageNumbers.Style.JOB_BIG
                    : DamageNumbers.Style.JOB, grad(j), split, gap, weights);
        } finally {
            JobTraits8.afterJobHitDone();
        }
        if (!landed) {
            if (veil > 1) JobTraits3.firstHitMissed(sp);
            if (counter > 0) JobTraits4.counterMissed(sp);
            return 0;
        }
        if (veil > 1) JobTraits3.firstHitLanded(sp, e, j);
        if (counter > 0) JobTraits4.counterLanded(sp, e, j, counter);
        float after = e.isRemoved() ? 0 : e.getHealth();
        double got = before > after ? before - after : dmg;
        JobTraits5.afterJobHit(sp, e, j, got); // 0.51.0 블러드 나이트 흡혈 (피의 갈증 · 광란)
        JobTraits16.afterJobHit(sp, e, j, got); // 2026-11-07 프로스트 레이더 서리 +1 · 얼린 적이면 깨짐 (얼음 조각)
        JobTraits12.afterJobHit(sp, e, j, got); // 0.51.0 소울 리퍼 영혼 (+1 · 가득이면 해방) · 엑시큐셔너 처형
        return got;
    }

    /** {@link #hitSplit} 를 delay 틱 뒤 (그 적이 무적 시간이면 끝난 뒤로 미룸 — strike 와 같음). 들어가면 then */
    public static void strikeSplit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, int split, int gap, boolean magic, boolean big, String life,
            int delay, java.util.function.DoubleConsumer then) {
        strikeSplit(sp, e, j, P, mult, split, gap, null, magic, big, life, delay, then);
    }

    /** 같은 것 — 줄마다 다른 몫 (weights · null 이면 같게) */
    public static void strikeSplit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, int split, int gap, double[] weights, boolean magic, boolean big,
            String life, int delay, java.util.function.DoubleConsumer then) {
        if (e == null || !(mult > 0)) return;
        hitLater(sp, e, life, now() + Math.max(0, delay), () -> {
            double d = hitSplit(sp, e, j, P, mult, split, gap, weights, magic, big);
            if (d > 0 && then != null) then.accept(d);
        });
    }

    /** 보스 · 보스 소품 (밀리지 않음 · 방어 깨짐이 약함) */
    public static boolean isBoss(LivingEntity e) {
        return Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop");
    }

    /**
     * 0.51.0 적을 끌어옴 (밀 수 있는 적만 · 수평): ticks 틱 동안 틱마다 to 쪽으로 dist / ticks 칸씩 — to 에서 stop 칸 앞에서 멈춤 · 막히면 그 틱은 그대로.
     * 속도로 끌면 이어지는 움찔 (JobImpact — 반대쪽 밀치기) 이 덮어쓰고 AI 를 끈 몹은 움직이지 않아서 (CI 42162246010: 끌려온 거리 0), 사슬 · 낫으로
     * 끌어오는 스킬은 자리를 직접 옮김 (스톰랜서 드릴 · 아크메이지 소용돌이와 같은 길). to 는 틱마다 다시 읽음 (쓴 사람을 따라감)
     */
    public static void reel(ServerPlayer sp, LivingEntity e, java.util.function.Supplier<net.minecraft.world.phys.Vec3> to, double dist, int ticks, double stop,
            String life) {
        if (e == null || !pushable(e) || !(dist > 0)) return;
        int n = Math.max(1, ticks);
        double step = dist / n;
        for (int t = 1; t <= n; t++) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || !e.isAlive() || e.isRemoved()) return;
                net.minecraft.world.phys.Vec3 p = e.position();
                net.minecraft.world.phys.Vec3 c = to.get();
                double dx = c.x - p.x;
                double dz = c.z - p.z;
                double l = Math.hypot(dx, dz);
                double s = Math.min(step, l - stop);
                if (s <= 0.01) return;
                Kit.moveTo(e, new net.minecraft.world.phys.Vec3(p.x + dx / l * s, p.y, p.z + dz / l * s));
                e.resetFallDistance();
            });
        }
    }

    /** 밀고 갈 수 있는 적 (보스 · 허수아비 · 플레이어 · 밀치기 저항이 큰 것은 아님) */
    public static boolean pushable(LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.player.Player || isBoss(e) || Kit.hasFamily(e, "rpg_dummy")) return false;
        return e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) < 0.9;
    }

    /** 쓴 사람 둘레 r 칸 안의 맞을 수 있는 적 (가까운 순, max 명까지) */
    public static List<LivingEntity> near(ServerPlayer sp, double r, int max) {
        List<LivingEntity> list = new ArrayList<>(Kit.around(sp, sp.position(), r));
        list.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(sp)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** 화면 흔들기 (쓴 사람 둘레 r 칸의 사람) */
    public static void shake(ServerPlayer sp, double r, double intensity, double seconds) {
        Kit.shakeNear(sp.level(), sp.position(), r, intensity, seconds);
    }

    /** 지금 서버 틱 */
    public static long now() {
        return Tasks.now();
    }

    /** 소리 (베드락 이름 — SkillFx.play) */
    public static void sound(ServerPlayer sp, String name, double pitch, double volume) {
        SkillFx.play(sp.level(), name, sp.position(), pitch, volume);
    }

    /** 시험: 보낸 단계 동작 열쇠 (마지막 32개) */
    public static final java.util.Deque<String> PHASES = new java.util.ArrayDeque<>();

    /**
     * 0.47.0 직업 스킬 동작의 단계 (스킬 id:단계 — rules_more MOTION.skills, tools/job_motions.py): 지금 그 단계 동작을 쓴 사람과 둘레에 보냄
     * (궁극기의 물결 · 돌진 끝의 내려찍기 · 돌아온 방패를 받음 — 때가 바뀌거나 긴 스킬도 효과와 같은 틱에)
     */
    public static void motion(ServerPlayer sp, String key) {
        if (sp == null || sp.isRemoved()) return;
        mmorpg.fabric.motion.MotionSystem.skill(sp, key);
        PHASES.addLast(key);
        while (PHASES.size() > 32) PHASES.removeFirst();
    }

    /** 단계 동작을 ticks 틱 뒤에 (같은 목숨일 때만 — 0 이하면 지금) */
    public static void motionLater(ServerPlayer sp, String key, int ticks, String life) {
        RuneKit.later(Math.max(0, ticks), () -> {
            if (RuneKit.sameLife(sp, life)) motion(sp, key);
        });
    }
}
