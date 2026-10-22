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
        double dmg = P * mult * veil * mmorpg.fabric.stattree.StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp) + counter;
        float before = e.getHealth();
        boolean bigger = big || veil > 1 || counter > 0;
        if (!Kit.applyJobDamage(sp, e, dmg, veil > 1, magic ? Kit.Cause.MAGIC : Kit.Cause.ATTACK, bigger ? DamageNumbers.Style.JOB_BIG : DamageNumbers.Style.JOB,
                grad(j))) {
            if (veil > 1) JobTraits3.firstHitMissed(sp);
            if (counter > 0) JobTraits4.counterMissed(sp);
            return 0;
        }
        if (veil > 1) JobTraits3.firstHitLanded(sp, e, j);
        if (counter > 0) JobTraits4.counterLanded(sp, e, j, counter);
        float after = e.isRemoved() ? 0 : e.getHealth();
        return before > after ? before - after : dmg;
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
        if (e == null || !Kit.canHit(sp, e) || !(mult > 0)) return 0;
        double veil = JobTraits3.firstHit(sp, j); // 0.29.0 암영의 첫 타
        double counter = JobTraits4.counterTake(sp, j); // 0.30.0 반격 게이지
        double dmg = P * mult * veil * mmorpg.fabric.stattree.StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp) + counter;
        float before = e.getHealth();
        boolean bigger = big || veil > 1 || counter > 0;
        if (!Kit.applyJobDamage(sp, e, dmg, veil > 1, magic ? Kit.Cause.MAGIC : Kit.Cause.ATTACK, bigger ? DamageNumbers.Style.JOB_BIG : DamageNumbers.Style.JOB,
                grad(j), split, gap)) {
            if (veil > 1) JobTraits3.firstHitMissed(sp);
            if (counter > 0) JobTraits4.counterMissed(sp);
            return 0;
        }
        if (veil > 1) JobTraits3.firstHitLanded(sp, e, j);
        if (counter > 0) JobTraits4.counterLanded(sp, e, j, counter);
        float after = e.isRemoved() ? 0 : e.getHealth();
        return before > after ? before - after : dmg;
    }

    /** {@link #hitSplit} 를 delay 틱 뒤 (그 적이 무적 시간이면 끝난 뒤로 미룸 — strike 와 같음). 들어가면 then */
    public static void strikeSplit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, int split, int gap, boolean magic, boolean big, String life,
            int delay, java.util.function.DoubleConsumer then) {
        if (e == null || !(mult > 0)) return;
        hitLater(sp, e, life, now() + Math.max(0, delay), () -> {
            double d = hitSplit(sp, e, j, P, mult, split, gap, magic, big);
            if (d > 0 && then != null) then.accept(d);
        });
    }

    /** 보스 · 보스 소품 (밀리지 않음 · 방어 깨짐이 약함) */
    public static boolean isBoss(LivingEntity e) {
        return Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop");
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
}
