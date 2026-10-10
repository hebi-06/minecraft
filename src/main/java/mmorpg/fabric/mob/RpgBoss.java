package mmorpg.fabric.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 보스 (rpg:boss_* · 분신 rpg:decoy_scarecrow_king — 행동 팩 그대로, 그리기는 BedrockRenderer).
 * 기본 목표는 행동 팩 그대로 (BedrockGoals) — 패턴 · 체력 · 보스 막대 이름 · 이동 방식 (움직이지 않음 · 헤엄 · 날기) 은 보스 엔진이 맡음.
 * 무리 (type_family — rpg_boss · rpg_boss_prop …) 는 엔티티 태그로 (Kit.hasFamily — 스킬 · 룬이 보스를 알아봄).
 * 받는 피해는 보스 엔진 ({@link #absorb} — boss.Bosses) 이 먼저 봄: 싸움의 보스 · 분신이면 플레이어마다 무적 시간으로 엔진이 체력을 줄이고 바닐라 피해는 없음.
 */
public class RpgBoss extends BedrockMob {
    /** 보스 엔진의 피해 흡수 (bosses.js absorb): null = 바닐라 피해 그대로, true = 엔진이 가져가 들어감, false = 막힘 */
    @FunctionalInterface
    public interface Absorb {
        Boolean hurt(BedrockMob e, ServerLevel level, DamageSource source, float amount);
    }

    /** boss.Bosses.init 이 넣음 (그 전에는 바닐라) — RpgProp 도 같은 것을 씀 */
    public static Absorb absorb = (e, level, source, amount) -> null;

    public RpgBoss(EntityType<? extends RpgBoss> type, Level level) {
        super(type, level);
        for (String f : def.families()) addTag(f);
    }

    @Override
    protected void registerGoals() {
        BedrockGoals.add(this);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Boolean r = absorb.hurt(this, level, source, amount);
        return r != null ? r : super.hurtServer(level, source, amount);
    }
}
