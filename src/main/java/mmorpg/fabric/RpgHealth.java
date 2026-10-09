package mmorpg.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Hp;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;

/**
 * RPG 체력 (hp.js 의 자바 판). 베드락은 바닐라 체력 20 을 "거울" 로 썼지만, 자바는 최대 체력 속성을 바로 바꿀 수 있어서
 * 최대 체력 = 기본 20 + 최고 레벨 × 2 + 체력 스탯 × 1 (규칙 코어 Hp.maxHpOf) 을 속성 수정자 (mmorpg:rpg_max_hp) 로 넣음.
 * - 받는 피해: 바닐라 그대로 (방어구 · 흡수 뒤) × DAMAGE_TAKEN_SCALE × (1 − 방패 감소). 독 · 배고픔 (어려움 제외) 은 1 에서 멈춤
 * - 회복: 바닐라 회복 1 = 최대 체력의 1/20 (자연 회복 · 물약 모두 최대 체력 비율로 — hp.js onPlayerHealBefore)
 * - 배고픔 없음: 배고픔은 늘 가득 · 포화 0 (자연 회복 = 4초마다 1/20, 베드락과 같게)
 * - 남은 체력 비율을 저장 (나갔다 들어와도 이어짐), 불사의 토템은 최대 체력의 10% 로 살림
 * 하트 대신 체력 바는 클라이언트 (RpgHud) 가 그림.
 */
public final class RpgHealth {
    private RpgHealth() {}

    private static final Obj CONFIG = Rules.get().obj("CONFIG");
    public static final Identifier MAX_HP_ID = Mmorpg.id("rpg_max_hp");
    /** hp.js MIRROR_MAX: 바닐라 회복량의 기준 (회복 1 = 최대 체력의 1/20) */
    static final double MIRROR_MAX = 20;

    /** 넣어 둔 RPG 최대 체력 (속성 수정자) */
    private static final Map<UUID, Double> MAXES = new HashMap<>();
    /** 마지막으로 저장한 체력 비율 */
    private static final Map<UUID, Double> SAVED = new HashMap<>();

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MAXES.clear();
            SAVED.clear();
        });
    }

    static void forget(UUID id) {
        MAXES.remove(id);
        SAVED.remove(id);
    }

    /** 생존 · 모험 (크리에이티브 · 관전은 RPG 체력 규칙 없음 — hp.js isRpgMode) */
    public static boolean isRpgMode(ServerPlayer sp) {
        return sp.gameMode().isSurvival();
    }

    /** 최대 체력 (hp.js maxHpOf): 기본 + 최고 레벨 보너스 + 실제 체력 스탯 */
    public static double maxHpOf(ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        return Hp.maxHpOf(d.lvmax(), Stats.get(d.effective(), "vit"));
    }

    /** 지금 넣어 둔 RPG 최대 체력 (없으면 계산) */
    public static double maxOf(ServerPlayer sp) {
        Double m = MAXES.get(sp.getUUID());
        return m != null ? m : maxHpOf(sp);
    }

    private static void applyMax(ServerPlayer sp, double max) {
        AttributeInstance inst = sp.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null) return;
        double add = max - inst.getBaseValue();
        AttributeModifier cur = inst.getModifier(MAX_HP_ID);
        if (cur == null || Math.abs(cur.amount() - add) > 1e-9) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(MAX_HP_ID, add, AttributeModifier.Operation.ADD_VALUE));
        }
        MAXES.put(sp.getUUID(), max);
    }

    /** 접속: 최대 체력을 넣고 저장한 비율로 체력 (hp.js hpState) */
    public static void onJoin(ServerPlayer sp) {
        double max = maxHpOf(sp);
        applyMax(sp, max);
        double ratio = RpgData.of(sp).hpRatio();
        if (!sp.isDeadOrDying()) sp.setHealth((float) (Math.max(0.001, Math.min(1, ratio)) * max));
        SAVED.put(sp.getUUID(), ratio);
    }

    /**
     * 다시 태어남 (hp.js onPlayerRespawn): 죽어서면 가득, 엔드에서 돌아온 것 (alive) 이면 그 전 체력.
     * @param health 그 전 플레이어의 체력 (alive 일 때)
     */
    public static void onRespawn(ServerPlayer sp, boolean alive, float health) {
        double max = maxHpOf(sp);
        applyMax(sp, max);
        sp.setHealth((float) (alive ? Math.max(1, Math.min(max, health)) : max));
        SAVED.remove(sp.getUUID());
    }

    /**
     * 최대 체력 다시 계산 (hp.js refreshMaxHp): keepRatio = false (스탯 · 레벨) 는 늘어난 만큼 체력도 늘림,
     * true (갑옷 — 4단계) 는 비율 유지.
     */
    public static void refreshMax(ServerPlayer sp, boolean keepRatio) {
        double nm = maxHpOf(sp);
        double max = maxOf(sp);
        if (nm == max) {
            applyMax(sp, nm);
            return;
        }
        boolean dead = sp.isDeadOrDying();
        double hp = Hp.refreshMaxHp(sp.getHealth(), max, nm, dead, keepRatio);
        applyMax(sp, nm);
        if (!dead) sp.setHealth((float) hp);
    }

    /** 바닐라 회복량 → RPG 회복량 (hp.js onPlayerHealBefore). LivingEntity#heal 처음 */
    public static float scaleHeal(ServerPlayer sp, float amount) {
        if (!isRpgMode(sp) || !(amount > 0)) return amount;
        return (float) (amount * maxOf(sp) / MIRROR_MAX);
    }

    /**
     * 받은 피해 (Player#actuallyHurt 의 setHealth): 방어구 · 흡수 뒤 깎일 양 × DAMAGE_TAKEN_SCALE × (1 − 방패 감소).
     * 바닐라에서 죽지 않는 피해 (독 · 배고픔 — 어려움 제외) 는 체력 1 에서 멈춤 (hp.js isNonLethal · takeLoss).
     * @return 실제로 넣을 체력
     */
    public static float onLoss(ServerPlayer sp, DamageSource source, float health, float newHealth) {
        if (!isRpgMode(sp)) return newHealth;
        double raw = health - newHealth;
        if (!(raw > 0)) return newHealth;
        double loss = raw * CONFIG.num("DAMAGE_TAKEN_SCALE") * (1 - RpgCombat.shieldReduction(sp, source));
        double next = health - loss;
        if (next < 1 && isNonLethal(sp, source, raw)) next = Math.min(health, 1);
        // 다른 플레이어가 준 피해: 맞은 사람 머리 위에 빨간 숫자 (fx.js player)
        if (loss > 0 && source.getEntity() instanceof ServerPlayer attacker && attacker != sp) {
            DamageNumbers.show(sp, Math.min(loss, health), DamageNumbers.Style.PLAYER, "", 0);
        }
        return (float) next;
    }

    /** 바닐라에서 죽지 않는 피해: 독 (마법 피해 1), 배고픔 (어려움 제외) */
    static boolean isNonLethal(ServerPlayer sp, DamageSource src, double damage) {
        if (src.is(DamageTypes.STARVE)) return sp.level().getDifficulty() != Difficulty.HARD;
        return src.is(DamageTypes.MAGIC) && damage <= 1.01 && sp.hasEffect(MobEffects.POISON);
    }

    /** 불사의 토템이 막음: 최대 체력의 10% (hp.js revive) */
    public static void onTotem(ServerPlayer sp) {
        if (isRpgMode(sp)) sp.setHealth((float) Hp.reviveHp(maxOf(sp)));
    }

    /** 체력 가득 (/rpg heal) */
    public static void healFull(ServerPlayer sp) {
        if (!sp.isDeadOrDying()) sp.setHealth(sp.getMaxHealth());
    }

    /** 배고픔 없음 (hp.js feedTick): 배고픔 가득 · 포화 0 → 자연 회복이 4초마다 (베드락처럼), 달리기 가능 */
    static void feed(ServerPlayer sp) {
        if (!CONFIG.truthy("NO_HUNGER")) return;
        FoodData fd = sp.getFoodData();
        if (fd.getFoodLevel() < 20) fd.setFoodLevel(20);
        if (fd.getSaturationLevel() != 0) fd.setSaturation(0);
    }

    /** 1초마다: 수정자가 빠졌으면 다시 넣고, 남은 체력 비율 저장 (0.005 이상 바뀌었을 때 — hp.js saveHp) */
    static void periodic(ServerPlayer sp) {
        Double m = MAXES.get(sp.getUUID());
        if (m != null) applyMax(sp, m);
        saveRatio(sp, false);
    }

    public static void saveRatio(ServerPlayer sp, boolean force) {
        double ratio = Hp.saveRatio(sp.isDeadOrDying(), sp.getHealth(), maxOf(sp));
        Double saved = SAVED.get(sp.getUUID());
        if (!force && saved != null && Math.abs(ratio - saved) < 0.005) return;
        RpgData d = RpgData.of(sp);
        if (d.hpRatio() != ratio) RpgData.set(sp, d.withHpRatio(ratio));
        SAVED.put(sp.getUUID(), ratio);
    }
}
