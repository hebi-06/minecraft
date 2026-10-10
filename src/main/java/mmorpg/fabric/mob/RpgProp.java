package mmorpg.fabric.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * 보스 소품 (rpg:prop_* — 촉수 · 뿌리 · 감옥 · 폭탄 · 얼음 · 핵 · 고리 · 글자 · 업화 막대): 스스로 움직이지 않음 (목표 없음), 보스 모듈이 옮기고 동작을 틂.
 */
public class RpgProp extends BedrockMob {
    public RpgProp(EntityType<? extends RpgProp> type, Level level) {
        super(type, level);
    }
}
