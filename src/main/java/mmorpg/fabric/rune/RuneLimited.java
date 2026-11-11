package mmorpg.fabric.rune;

import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.RuneBreak;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 한정 초월 룬의 공통 도움 (0.16.0 — 위력 P 의 적용 스탯 = 네 스탯의 합 × 1.0, 마법 피해): 타격 (몹 무적 시간을 피해 — Kit.reserveHit · hitAt), 시전 기록 (시험 —
 * 준 피해의 합 · 타격 수), 6돌 전용 연출 (finale) 이 켜졌는지, 몸 가운데. 무간참역 · 대지 가르기 · 세계수의 가호 · 심연의 낙인 · 만년빙하가 씀.
 * 2026-11-11 사용자 룬 도감 표시 "삭제하고 다른 컨셉의 룬 추가 예정": 처음 셋 (만상귀일 · 칠성 강림 · 천공의 눈) 을 뺌 (규칙 · 돌파 · 시전 — 새 컨셉 룬은 사용자가 주면).
 */
final class RuneLimited {
    private RuneLimited() {}

    // ------------------------------------------------------------ 공통

    /** 한 번의 시전이 준 피해의 합 · 맞은 횟수 (시험 기록) */
    static final class Tally {
        double sum;
        int hits;
    }

    /** 시험용: 마지막 시전의 피해 합 (룬 함수 이름 → 합) */
    static final Map<String, double[]> LAST = new java.util.HashMap<>();

    /** 룬 타격 한 번 (마법 — 몹 무적 시간을 피해 넣음), 들어가면 합에 더하고 then */
    static void hit(ServerPlayer sp, String life, LivingEntity e, double P, double mult, DamageNumbers.Style style, Tally t, Consumer<LivingEntity> then) {
        if (!(mult > 0)) return;
        Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), () -> RuneKit.sameLife(sp, life), () -> {
            double d = RuneKit.runeHit(sp, e, P, mult, "int", Kit.Cause.MAGIC, style);
            if (d > 0) {
                t.sum += d;
                t.hits++;
                if (then != null) then.accept(e);
            }
        });
    }

    /** 이번 시전의 피해 합 · 타격 수를 delay 틱 뒤 (마지막 타격이 들어간 뒤) 기록 — 시험용, 화면에는 띄우지 않음 (0.20.1) */
    static void saveTally(ServerPlayer sp, String fn, Tally t, int delay) {
        RuneKit.later(delay, () -> LAST.put(sp.getUUID() + "|" + fn, new double[] {t.sum, t.hits}));
    }

    /** 이 사람이 지금 쓴 이 룬의 6돌 전용 연출 (finale) 이 켜졌는지 */
    static boolean finale(ServerPlayer sp, Runes.Rune r) {
        RuneBreak.Extra ex = RuneBreak.extraFor(r, Breakthrough.castLevel(sp, r.def().str("fn")));
        return ex != null && "finale".equals(ex.type());
    }

    /** 몸 가운데 (빛줄기가 닿는 곳) */
    static Vec3 body(LivingEntity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.55, e.getZ());
    }

    /** 땅의 원 위 한 점 */
    static Vec3 ring(Vec3 c, double r, double ang) {
        return new Vec3(c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r);
    }
}
