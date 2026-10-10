package mmorpg.fabric.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 훈련용 허수아비 엔티티 (rpg:training_dummy — RPG_BP/entities/rpg_training_dummy.json): 크기 0.8 × 2.3, 밀리지 않음 (밀치기 저항 1),
 * 떨어짐 · 숨막힘 · 물 · 불 · 용암 · 얼음 · 마그마 · 가시 피해 없음, 사라지지 않음, 무리 rpg_dummy · mob (엔티티 태그 — Kit.hasFamily).
 * 죽지 않음: 받은 피해를 기록하고 (Dummy.onHurt) 체력을 다시 채움 — 애드온은 쓰러질 피해를 체력 - 1 로 줄였지만 자바는 피해를 그대로 받은 뒤
 * 채워서 피해 숫자 · 기록이 원래 피해 그대로. 체력은 20000 (애드온) 대신 자바 상한 1024 (한 방이 1024 를 넘으면 숫자는 1024, 기록은 원래 피해).
 * 움직임 · 머리 위 표시 · 제자리 돌아오기는 Dummy (서버) · 모델 · 흔들림은 클라이언트 DummyRenderer.
 */
public class TrainingDummy extends LivingEntity {
    /** 자바 최대 체력 상한 (애드온은 20000) */
    public static final float HP = 1024F;

    /** 머리 위 표시 (글자 표시 엔티티 — 저장하지 않음, 서버에서만) */
    Display.TextDisplay label;
    /** 지금 머리 위 글자 (같으면 다시 보내지 않음) */
    String labelText;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        addTag(Dummy.FAMILY);
        addTag("mob");
    }

    /** 기본 속성 (최대 체력 1024 · 밀치기 · 폭발 밀치기 저항 1) */
    public static AttributeSupplier.Builder attributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, HP)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) Dummy.serverTick(this);
    }

    /** 몸은 늘 세운 방향 그대로 (가만히 선 생명체가 고개 쪽으로 몸을 돌리지 않게 — 갑옷 거치대처럼) */
    @Override
    protected void tickHeadTurn(float yBodyRotTarget) {
        this.yBodyRotO = this.yRotO;
        this.yBodyRot = this.getYRot();
    }

    /** 피해를 받은 뒤: 기록하고 체력을 채움 (같은 hurtServer 안 — 이어지는 죽음 확인 전에) */
    @Override
    protected void actuallyHurt(ServerLevel level, DamageSource source, float amount) {
        float before = getHealth() + getAbsorptionAmount();
        super.actuallyHurt(level, source, amount);
        float after = getHealth() + getAbsorptionAmount();
        float lost = before - after;
        boolean emptied = getHealth() <= 0;
        if (!(lost > 0) && !emptied) return; // 면역 · 다 막힘
        // 체력이 바닥났으면 깎인 양은 남은 체력까지라서 원래 피해 (허수아비는 방어 0 — 받은 그대로)
        double dealt = emptied ? Math.max(lost, amount) : lost;
        setHealth(getMaxHealth());
        Dummy.onHurt(this, dealt);
    }

    /** 애드온 damage_sensor: 떨어짐 · 숨막힘 · 물 · 불 · 용암 · 얼음 · 마그마 · 가시 (+ 끼임 · 벽 박치기 · 종유석) 는 받지 않음 */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (super.isInvulnerableTo(level, source)) return true;
        return source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_FIRE)
                || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.CAMPFIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.FREEZE)
                || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.STALAGMITE)
                || source.is(DamageTypes.DRY_OUT) || source.is(DamageTypes.STARVE);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    /** /kill 은 그대로 치움 (애드온은 어떤 피해로도 쓰러지지 않지만 자바 관리자가 치울 수 있게) */
    @Override
    public void kill(ServerLevel level) {
        Dummy.removeDummy(this);
    }

    /** 세계 아래로 떨어지면 치움 (끝없이 채우며 떨어지지 않게) */
    @Override
    protected void onBelowWorld() {
        Dummy.removeDummy(this);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        // 청크가 내려갈 때 (UNLOADED_*) 는 표시도 같이 내려가고 저장되지 않음 — 그 밖 (치움 · 차원 이동) 은 표시도 치움
        if (reason != Entity.RemovalReason.UNLOADED_TO_CHUNK && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER) Dummy.dropLabel(this);
    }

    // ------------------------------------------------------------ 밀리지 않음 (애드온 pushable false)

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {}

    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** 몹이 적으로 노리지 않음 */
    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    // ------------------------------------------------------------ 이름 · 소리 · 고르기

    @Override
    protected Component getTypeName() {
        return Component.literal(Dummy.NAME);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ARMOR_STAND_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ARMOR_STAND_BREAK;
    }

    /** 크리에이티브 가운데 버튼: 소환 알 */
    @Override
    public ItemStack getPickResult() {
        return Dummy.EGG == null ? ItemStack.EMPTY : new ItemStack(Dummy.EGG);
    }
}
