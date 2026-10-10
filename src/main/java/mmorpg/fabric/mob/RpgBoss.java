package mmorpg.fabric.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 보스 (rpg:boss_* · 분신 rpg:decoy_scarecrow_king — 행동 팩 그대로, 그리기는 BedrockRenderer).
 * 기본 목표는 행동 팩 그대로 (BedrockGoals) — 패턴 · 체력 · 보스 막대 이름 · 이동 방식 (움직이지 않음 · 헤엄 · 날기) 은 보스 엔진이 맡음.
 */
public class RpgBoss extends BedrockMob {
    public RpgBoss(EntityType<? extends RpgBoss> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        BedrockGoals.add(this);
    }
}
