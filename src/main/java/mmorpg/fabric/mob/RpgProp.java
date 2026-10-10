package mmorpg.fabric.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/**
 * 보스 소품 (rpg:prop_* — 촉수 · 뿌리 · 감옥 · 폭탄 · 얼음 · 핵 · 고리 · 글자 · 업화 막대): 스스로 움직이지 않음 (목표 없음), 보스 모듈이 옮기고 동작을 틂.
 * 무리는 엔티티 태그로 (rpg_boss_prop — Kit.hasFamily). 받는 피해는 보스 엔진 (RpgBoss.absorb) 이 먼저 봄 (싸움의 소품이면 모듈이 처리, 감옥처럼
 * 엔진에 표시만 한 것은 바닐라 피해).
 * 맞는 상자 (베드락 custom_hit_test — 짚단 감옥 1.6×2.6 · 수르트 부위의 핵): 자바는 몸 크기를 그 상자로 두고 (때릴 때 조준에 잡히게) 둘레를 밀지 않음
 * (베드락은 충돌 상자를 작게 두어 갇힌 사람을 밀지 않음).
 */
public class RpgProp extends BedrockMob {
    public RpgProp(EntityType<? extends RpgProp> type, Level level) {
        super(type, level);
        for (String f : def.families()) addTag(f);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Boolean r = RpgBoss.absorb.hurt(this, level, source, amount);
        return r != null ? r : super.hurtServer(level, source, amount);
    }

    /** 맞는 상자가 있으면 몸 크기 = 맞는 상자 */
    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        float[] h = def == null ? null : MobData.hitbox(def.id());
        return h != null ? EntityDimensions.scalable(h[0], h[1]) : super.getDefaultDimensions(pose);
    }

    /** 맞는 상자가 있는 소품은 둘레를 밀지 않음 (갇힌 사람이 감옥 밖으로 밀려나지 않게) */
    @Override
    protected void pushEntities() {
        if (MobData.hitbox(def.id()) == null) super.pushEntities();
    }
}
