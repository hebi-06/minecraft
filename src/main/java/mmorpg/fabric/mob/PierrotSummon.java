package mmorpg.fabric.mob;

import java.util.UUID;
import java.util.function.BiConsumer;
import mmorpg.fabric.Mmorpg;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 피에로 (암살자 반전 — 광대 소환물 탱커, 사용자 "샤코 같은 느낌에 광대컨셉으로 소환물로 어그로랑 탱킹을 해주는 직업군") 의 소환물 — 진짜 몹:
 * 깜짝 상자 (rpg:pierrot_box — 그랜드 피날레의 거대한 상자는 같은 몹 × 크기) · 광대 인형 (rpg:pierrot_doll — 할루시네이트).
 * <ul>
 * <li>몬스터가 노리고 때림 (Monsters.lure — 바닐라 몹은 대상을 이 몹으로), 체력 = 주인 최대 체력의 몫 (스킬이 정함) · 무적 시간 없음 (여럿이 한꺼번에 때려도 모두
 * 들어감 — 탱커가 맞아 주는 몸)</li>
 * <li>플레이어 · 플레이어 스킬은 치지 않음 (rpg_ally 태그 — Kit.canHit · 플레이어가 준 피해는 버림), 조준 · 화살에 잡히지 않음 (뒤의 적을 그대로 칠 수 있게 —
 * isPickable), 밀지도 밀리지도 않음, 저장하지 않음 (서버가 꺼지거나 청크가 내려가면 사라짐), 전리품 · 경험치 없음, 제 소리 없음 (스킬이 냄)</li>
 * <li>체력이 다하면 쓰러지는 대신 {@link #onBreak} (색종이 폭죽 — job.PierrotSummons) 뒤 바로 사라짐. 스스로 생각하지 않음 (움직임 · 칼 던지기 · 뿅망치는
 * job.PierrotSummons · PierrotSkills 가 틱마다)</li>
 * </ul>
 */
public class PierrotSummon extends BedrockMob {
    public static final String BOX = "rpg:pierrot_box";
    public static final String DOLL = "rpg:pierrot_doll";
    /** 피에로 소환물 표시 (스킬 대상에서 빠짐 — rpg_ally 와 함께) */
    public static final String TAG = "rpg_summon";

    /** 깜짝 상자 동작: 떨어져 섬 (태어날 때 — 위에서 떨어져 통통 튐) · 뚜껑이 열리며 용수철 광대가 튀어나옴 (끝 자세 유지) · 칼을 던짐 (고개를 앞으로) · 들썩임 (피날레 불꽃놀이) */
    public static final String BOX_DROP = "animation.rpg.monster.pierrot_box.drop";
    public static final String BOX_POP = "animation.rpg.monster.pierrot_box.pop";
    public static final String BOX_THROW = "animation.rpg.monster.pierrot_box.throw";
    public static final String BOX_FIRE = "animation.rpg.monster.pierrot_box.fire";
    /** 광대 인형 동작: 떨어져 내려섬 (태어날 때) · 뿅망치 내려치기 (공격 — HAMMER_AT 틱에 땅에 닿음) · 놀림 (팔을 흔듦) */
    public static final String DOLL_DROP = "animation.rpg.monster.pierrot_doll.drop";
    public static final String DOLL_SWING = "animation.rpg.monster.pierrot_doll.attack";
    public static final String DOLL_TAUNT = "animation.rpg.monster.pierrot_doll.taunt";
    /** 뿅망치 내려치기 동작을 튼 뒤 망치가 땅에 닿는 틱 (0.45초) */
    public static final int HAMMER_AT = 9;

    /** 체력이 다해 부서짐 (주인의 특성 — 색종이 폭죽): 이 몹 · 마지막 피해 */
    public static BiConsumer<PierrotSummon, DamageSource> onBreak = (s, src) -> {};
    /** 맞음 (연출 — 숫자): 이 몹 · 깎인 체력 */
    public static BiConsumer<PierrotSummon, Float> onHurt = (s, amount) -> {};

    /** 주인 (피에로) */
    public UUID owner;
    /** 이 틱 (태어난 뒤) 이 지나면 스스로 사라짐 (스킬이 치우지 못해도) */
    public int maxAge = 200;
    private boolean broken;

    public PierrotSummon(EntityType<? extends PierrotSummon> type, Level level) {
        super(type, level);
        for (String f : def.families()) addTag(f);
        addTag(TyrfingWarrior.TAG);
        addTag(TAG);
        setSilent(true);
    }

    /**
     * id (BOX · DOLL) 하나를 at (발) 에 yaw (자바 각도) 로 세움 — 최대 체력 maxHp · 크기 scale (그리기와 충돌 상자가 함께 커짐), maxAge 틱 뒤 스스로 사라짐.
     * 못 세우면 null
     */
    public static PierrotSummon spawn(String id, ServerLevel level, Vec3 at, float yaw, int maxAge, double maxHp, double scale, UUID owner) {
        EntityType<? extends BedrockMob> type = RpgEntities.type(id);
        if (type == null) return null;
        if (!(type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof PierrotSummon s)) return null;
        s.snapTo(at.x, at.y, at.z, yaw, 0F);
        s.setYHeadRot(yaw);
        s.setYBodyRot(yaw);
        s.maxAge = maxAge;
        s.owner = owner;
        AttributeInstance hp = s.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.max(1, Math.min(RpgEntities.MAX_HEALTH_CAP, maxHp)));
        AttributeInstance sc = s.getAttribute(Attributes.SCALE);
        if (sc != null && scale > 0) sc.setBaseValue(Math.max(0.0625, Math.min(16, scale)));
        s.setHealth(s.getMaxHealth());
        s.refreshDimensions();
        return level.addFreshEntity(s) ? s : null;
    }

    /** 부서졌는지 (체력이 다함 — 색종이 폭죽이 나감) */
    public boolean broken() {
        return broken;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount > maxAge) discard();
    }

    /** 플레이어가 준 피해 (평타 · 화살 · 룬) 는 버림, 나머지 (몬스터 · 바닐라 몹 · 주인이 나눠 준 몫) 는 무적 시간 없이 모두 */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) return false;
        float before = getHealth();
        boolean ok = super.hurtServer(level, source, amount);
        invulnerableTime = 0;
        if (ok && !isRemoved()) {
            float lost = Math.max(0F, before - getHealth());
            try {
                onHurt.accept(this, lost > 0 ? lost : amount);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] pierrot summon hurt", e);
            }
        }
        return ok;
    }

    /** 체력이 다함: 쓰러지는 대신 부서짐 (주인의 색종이 폭죽) → 바로 사라짐 (죽음 메시지 · 전리품 · 쓰러지는 동작 없음) */
    @Override
    public void die(DamageSource source) {
        if (!(level() instanceof ServerLevel) || broken) return;
        broken = true;
        try {
            onBreak.accept(this, source);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] pierrot summon break", e);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** 조준 · 화살에 잡히지 않음 (소환물 너머의 적을 그대로 겨누고 칠 수 있게 — 몬스터 · 바닐라 몹의 근접은 충돌 상자로 닿음) */
    @Override
    public boolean isPickable() {
        return false;
    }

    // ------------------------------------------------------------ 밀지도 밀리지도 않음

    @Override
    public void push(Entity entity) {}

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean isPushable() {
        return false;
    }
}
