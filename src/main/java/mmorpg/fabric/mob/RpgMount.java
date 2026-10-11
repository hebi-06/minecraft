package mmorpg.fabric.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 탈것 (6단계 — rpg_mounts: 다이어울프 · 앞으로 더할 탈것). 수치는 행동 팩 그대로 (MobData): 빠르기 (minecraft:movement), 뛰는 힘 (horse.jump_strength),
 * 걸어 오르는 높이 (variable_max_auto_step), 자리 (rideable seats — RpgEntities 가 탄 사람 자리로), 피해 면역 (damage_sensor all).
 * <ul>
 * <li>타기: 첫 탄 사람 (플레이어) 이 몰기 — 말과 같은 조작 (바라보는 쪽으로 돎, 옆 · 뒤는 느리게), 점프 키를 길게 누를수록 높이 뜀 (베드락 can_power_jump —
 * 바닐라 말 뛰기 막대가 화면에 나옴)</li>
 * <li>부르기 · 사라지기 · 연출 · 탄 동안 막기는 탈것 관리 (mount.Mounts — {@link #manager})</li>
 * <li>저장하지 않음 (EntityType noSave — 세계를 닫거나 나가면 사라지고, 나간 사람의 데이터에도 함께 적히지 않음)</li>
 * <li>탈것의 체력은 화면에 보이지 않음 (다치지 않음), 내리면 탈것이 있던 자리에 섬 (탈것은 곧 사라짐)</li>
 * </ul>
 */
public class RpgMount extends BedrockMob implements PlayerRideableJumping {
    /** 탈것 관리 (부른 기록 · 연출 — mount.Mounts 가 넣음) */
    public interface Manager {
        /** 서버 틱마다 (부른 기록이 없는 탈것 정리 등) */
        void tick(RpgMount m);

        /** 탄 사람이 뛰기 시작 (서버 — 점프 키를 뗌, power 0 ~ 100) */
        void jump(RpgMount m, int power);
    }

    public static Manager manager = new Manager() {
        @Override
        public void tick(RpgMount m) {}

        @Override
        public void jump(RpgMount m, int power) {}
    };

    /** 뛰기 세기 (0 ~ 1 — 점프 키를 뗄 때 정해지고 땅에 닿아 있을 때 뜀) */
    private float pendingJump;

    public RpgMount(EntityType<? extends RpgMount> type, Level level) {
        super(type, level);
        for (String f : def.families()) addTag(f);
    }

    @Override
    protected void registerGoals() {
        BedrockGoals.add(this); // behavior.float (아무도 안 탔을 때 물에 뜸)
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) manager.tick(this);
    }

    // ------------------------------------------------------------ 몰기 (말과 같음 — AbstractHorse)

    /** 첫 탄 사람이 플레이어면 그 사람이 몲 */
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : super.getControllingPassenger();
    }

    /** 누가 타면 스스로 움직이지 않음 (목표 멈춤 — 탄 사람의 입력으로만) */
    @Override
    public boolean isImmobile() {
        return super.isImmobile() || isVehicle();
    }

    @Override
    protected void tickRidden(Player player, Vec3 travel) {
        super.tickRidden(player, travel);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        this.yRotO = this.yBodyRot = this.yHeadRot = getYRot();
        if (isLocalInstanceAuthoritative() && onGround()) {
            if (pendingJump > 0) executeRidersJump(pendingJump, travel);
            pendingJump = 0;
        }
    }

    /** 앞뒤 · 옆 입력 (옆은 절반, 뒤는 1/4 — 말과 같음) */
    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        float x = player.xxa * 0.5F;
        float z = player.zza;
        if (z <= 0) z *= 0.25F;
        return new Vec3(x, 0, z);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /** 뜀: 위로 뛰는 힘 × 세기, 앞으로 가는 중이면 앞으로도 조금 (말과 같음) */
    protected void executeRidersJump(float scale, Vec3 travel) {
        double y = getJumpPower(scale);
        Vec3 d = getDeltaMovement();
        setDeltaMovement(d.x, y, d.z);
        if (travel.z > 0) {
            float s = Mth.sin(getYRot() * Mth.DEG_TO_RAD);
            float c = Mth.cos(getYRot() * Mth.DEG_TO_RAD);
            setDeltaMovement(getDeltaMovement().add(-0.4F * s * scale, 0, 0.4F * c * scale));
        }
    }

    // ------------------------------------------------------------ 뛰기 막대 (PlayerRideableJumping)

    @Override
    public void onPlayerJump(int power) {
        pendingJump = getPlayerJumpPendingScale(Math.max(0, power));
    }

    @Override
    public boolean canJump() {
        return true;
    }

    /** 서버: 뛰기 시작 (연출) */
    @Override
    public void handleStartJump(int power) {
        if (level() instanceof ServerLevel) manager.jump(this, power);
    }

    @Override
    public void handleStopJump() {}

    /** 지금 정해진 뛰기 세기 (시험용) */
    public float pendingJump() {
        return pendingJump;
    }

    // ------------------------------------------------------------ 그 밖

    /** 탈것의 체력은 보이지 않음 (다치지 않음 — RPG 체력 바와 겹치지 않게) */
    @Override
    public boolean showVehicleHealth() {
        return false;
    }

    /** 내리면 탈것이 있던 자리에 섬 (막혀 있으면 바닐라대로 — 탈것은 곧 사라짐) */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 at = position();
        AABB box = passenger.getDimensions(Pose.STANDING).makeBoundingBox(at);
        if (level().noCollision(passenger, box)) return at;
        return super.getDismountLocationForPassenger(passenger);
    }

    /** 다른 엔티티를 밀지 않음 (탄 채로 다른 플레이어 · 몹을 밀어내지 않게 — 베드락 pushable false) */
    @Override
    protected void pushEntities() {}
}
