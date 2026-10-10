package mmorpg.fabric.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 일반 몬스터 (rpg:scarecrow · gulon · skeljaskrimsli · nix · draugr · troll — 행동 팩 그대로, 그리기는 BedrockRenderer).
 * 움직임 · 쫓기는 목표 (BedrockGoals), 공격 · 이름표 · 회복 · 전리품은 monsters.js 의 이식이 맡음.
 */
public class RpgMonster extends BedrockMob {
    public RpgMonster(EntityType<? extends RpgMonster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        BedrockGoals.add(this);
    }
}
