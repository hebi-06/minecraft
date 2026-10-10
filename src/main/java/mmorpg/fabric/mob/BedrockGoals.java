package mmorpg.fabric.mob;

import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * 베드락 행동 (minecraft:behavior.*) → 자바 목표 (goal). 우선순위 · 빠르기 배수 · 거리는 행동 팩 그대로:
 * float · hurt_by_target (플레이어만) · nearest_attackable_target (플레이어) · melee_attack (쫓아가기 — 피해는 스크립트, 바닐라 근접은 BedrockMob 이 막음) ·
 * move_towards_home_restriction · random_stroll (interval 틱마다 1/interval 확률) · look_at_player · random_look_around.
 * 엔티티 생성자 안 (registerGoals) 에서 부르므로 def 대신 종류로 수치를 찾음.
 */
public final class BedrockGoals {
    private BedrockGoals() {}

    /** 행동 팩의 행동을 목표로 (몬스터 · 보스의 기본 — 다르게 하려면 registerGoals 를 바꿈) */
    public static void add(BedrockMob mob) {
        MobData.Def d = MobData.get(EntityType.getKey(mob.getType()).toString());
        if (d == null) return;
        for (Map.Entry<String, Map<String, Object>> e : d.behaviors().entrySet()) {
            Map<String, Object> b = e.getValue();
            int pr = (int) MobData.num(b.get("priority"), 5);
            double speed = MobData.num(b.get("speed_multiplier"), 1);
            switch (e.getKey()) {
                case "float" -> mob.goals().addGoal(pr, new FloatGoal(mob));
                case "hurt_by_target" -> mob.targets().addGoal(pr, new PlayerHurtByTarget(mob));
                case "nearest_attackable_target" -> mob.targets().addGoal(pr,
                        new NearestAttackableTargetGoal<>(mob, Player.class, !Boolean.FALSE.equals(b.get("must_see")) && b.containsKey("must_see")));
                case "melee_attack" -> mob.goals().addGoal(pr, new MeleeAttackGoal(mob, speed, Boolean.TRUE.equals(b.get("track_target"))));
                case "move_towards_home_restriction" -> mob.goals().addGoal(pr, new MoveTowardsRestrictionGoal(mob, speed));
                case "random_stroll" -> mob.goals().addGoal(pr, new RandomStrollGoal(mob, speed, (int) MobData.num(b.get("interval"), 120)));
                case "look_at_player" -> mob.goals().addGoal(pr, new LookAtPlayerGoal(mob, Player.class, (float) MobData.num(b.get("look_distance"), 8)));
                case "random_look_around" -> mob.goals().addGoal(pr, new RandomLookAroundGoal(mob));
                default -> {
                }
            }
        }
    }

    /** 때린 플레이어만 쫓음 (베드락 hurt_by_target 의 entity_types = 플레이어) */
    public static final class PlayerHurtByTarget extends HurtByTargetGoal {
        public PlayerHurtByTarget(BedrockMob mob) {
            super(mob);
        }

        @Override
        public boolean canUse() {
            return mob.getLastHurtByMob() instanceof Player && super.canUse();
        }
    }
}
