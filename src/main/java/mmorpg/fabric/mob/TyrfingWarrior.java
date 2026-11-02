package mmorpg.fabric.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 0.50.0 티르빙 망자의 각성의 아군 전사 — 앙간튀르의 형제 (사용자 "티르빙 궁은 실제 아군 몹이 나와서 때리는 느낌이면 좋을거 같음"): 진짜 몹.
 * 모습은 북쪽 몬스터 드라우그 (뿔 투구 · 해골 얼굴 · 사슬 갑옷 · 방패 · 도끼 · 망토 — 모델 · 동작 그대로) 를 보랏빛 망령으로 다시 칠한 것
 * (tools/tyrfing_warrior_tex.py — 엔티티 정의 rpg:tyrfing_warrior: mmorpg/entities.json · bedrock/entities.json).
 * <ul>
 * <li>스스로 생각하지 않음 (목표 없음): 무덤에서 솟기 · 적에게 달려가기 · 도끼 내려치기 (공격 동작) · 흩어짐은 Tyrfing.waking 이 틱마다 정함.
 *     피해는 주인의 스킬 피해 (Kit — 피해 숫자 · 처치는 주인의 것), 몹 자체는 치지 않음 (BedrockMob.doHurtTarget)</li>
 * <li>맞지 않음 (damage_sensor all — 조준 · 화살에도 잡히지 않음: 뒤의 적을 그대로 칠 수 있게), 아무도 노리지 않음 (canBeSeenAsEnemy — 몹 · 철 골렘),
 *     스킬 대상이 아님 (rpg_ally 태그 — Kit.canHit), 밀지도 밀리지도 않음 (적 · 플레이어 자리를 바꾸지 않게)</li>
 * <li>저장하지 않음 (서버가 꺼지거나 청크가 내려가면 사라짐), 전리품 · 경험치 없음, 제 소리 없음 (스킬이 냄), 스킬이 치우지 못해도 maxAge 틱 뒤 스스로 사라짐</li>
 * </ul>
 * 0.51.0 나글파르(네크로맨서) 의 망자 선원 (rpg:naglfar_crew — 같은 드라우그 모델 · 독초록 혼불 · 뼈색으로 다시 칠한 것, tools/naglfar_crew_tex.py) 도 이 몹
 * (생각은 job.NecroCrew 가 틱마다).
 */
public class TyrfingWarrior extends BedrockMob {
    public static final String ID = "rpg:tyrfing_warrior";
    /** 아군 몹 표시 (스킬 대상에서 빠짐 — Kit.canHit) */
    public static final String TAG = "rpg_ally";
    /** 도끼 내려치기 (드라우그 공격 동작 0.8초 — 0.25초에 머리 위로 들었다가 0.45초까지 내려침) */
    public static final String ATTACK = "animation.rpg.monster.draugr.attack";
    /** 공격 동작을 튼 뒤 도끼가 닿는 틱 (내려치는 한가운데) */
    public static final int STRIKE_AT = 6;

    /** 이 틱 (태어난 뒤) 이 지나면 스스로 사라짐 */
    public int maxAge = 200;

    public TyrfingWarrior(EntityType<? extends TyrfingWarrior> type, Level level) {
        super(type, level);
        for (String f : def.families()) addTag(f);
        addTag(TAG);
        setSilent(true);
    }

    /** 0.51.0 나글파르(네크로맨서) 의 망자 선원 (같은 몹 — 독초록 · 뼈색 그림) */
    public static final String CREW_ID = "rpg:naglfar_crew";

    /** 전사 하나를 at (발) 에 yaw (자바 각도) 로 세움 — maxAge 틱 뒤 스스로 사라짐. 못 세우면 null */
    public static TyrfingWarrior spawn(ServerLevel level, Vec3 at, float yaw, int maxAge) {
        return spawnAs(ID, level, at, yaw, maxAge);
    }

    /** 이 몹 꼴의 엔티티 id (티르빙의 전사 · 망자 선원) 하나를 세움 — 못 세우면 null */
    public static TyrfingWarrior spawnAs(String id, ServerLevel level, Vec3 at, float yaw, int maxAge) {
        EntityType<? extends BedrockMob> type = RpgEntities.type(id);
        if (type == null) return null;
        if (!(type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof TyrfingWarrior w)) return null;
        w.snapTo(at.x, at.y, at.z, yaw, 0F);
        w.setYHeadRot(yaw);
        w.setYBodyRot(yaw);
        w.maxAge = maxAge;
        return level.addFreshEntity(w) ? w : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > maxAge) discard();
    }

    /** 공격 동작 (도끼 내려치기 — 처음부터) */
    public void swingAxe() {
        playAnimation(ATTACK);
    }

    /**
     * 빛을 재는 자리: 보통은 눈 (바닐라). 땅 밑에서 솟는 동안 (태어난 뒤 14틱 — 눈이 땅 속) 은 그 위로 첫 빈 칸
     * (땅 속의 빛 0 으로 솟는 몸이 새까맣게 그려지지 않게 — 그 뒤는 바닐라 그대로: 낮은 천장 아래에서 지붕 위 빛을 재지 않게)
     */
    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        Vec3 eye = super.getLightProbePosition(partialTicks);
        if (tickCount > 14) return eye;
        BlockPos p = BlockPos.containing(eye);
        for (int i = 0; i < 4 && !level().getBlockState(p).isAir(); i++) p = p.above();
        return new Vec3(eye.x, Math.max(eye.y, p.getY() + 0.5), eye.z);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    // ------------------------------------------------------------ 밀지도 밀리지도 않음

    @Override
    public void push(Entity entity) {}

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}
}
