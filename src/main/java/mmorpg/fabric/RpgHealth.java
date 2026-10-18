package mmorpg.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mmorpg.core.Hp;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import mmorpg.fabric.skill.Guards;
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
 * - 받는 피해: 바닐라 그대로 (방어구 · 흡수 뒤) × DAMAGE_TAKEN_SCALE × (1 − 감소). 감소 = 1 − (1 − 방패) × (1 − 스킬 · 룬 버프).
 *   독 · 배고픔 (어려움 제외) 은 1 에서 멈춤
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

    /** 보호막 (hp.js shields — 성기사 룬 등): 남은 양 · 끝나는 틱. 받는 피해를 체력보다 먼저 흡수 (죽지 않는 피해는 흡수하지 않음) */
    private record Shield(double amount, long until) {}

    private static final Map<UUID, Shield> SHIELDS = new HashMap<>();
    /** 보호막이 깨짐 · 피해를 흡수함 (룬이 연출을 넣음 — 다음 틱에) */
    public static Consumer<ServerPlayer> shieldBroken = p -> {};
    public static BiConsumer<ServerPlayer, Double> shieldAbsorbed = (p, a) -> {};
    /** 쓰러지지 않는 상태인지 (불사의 맹약 룬) · 그것으로 버텼을 때 (다음 틱에) */
    public static Predicate<ServerPlayer> undying = p -> false;
    public static Consumer<ServerPlayer> undyingSaved = p -> {};
    /**
     * 0.19.0 버프의 받는 피해 감소 (세계수의 가호 — 0 = 없음): 룬 · 스킬 감소 (Guards) 와는 가장 센 것 하나 (Guards 끼리처럼), 방패 감소와는 곱함.
     * 막아 준 양 (이 감소가 가장 셀 때 — 감소가 없었을 때보다 덜 깎인 체력) 은 buffSaved 로
     */
    public static java.util.function.ToDoubleFunction<ServerPlayer> buffReduce = p -> 0;
    public static BiConsumer<ServerPlayer, Double> buffSaved = (p, a) -> {};
    /**
     * 0.20.0 때린 쪽의 약화 (심연의 낙인 — 0 = 없음): 이 적 (피해의 주인 — 화살은 쏜 몹) 이 준 피해 × (1 − 약화). 받는 쪽 감소 (방패 · 룬 · 가호 ·
     * 스탯 트리) 와는 곱. 줄인 양은 weakenSaved 로 (맞은 사람 · 때린 적 · 양)
     */
    public static java.util.function.ToDoubleFunction<net.minecraft.world.entity.Entity> weakenOf = e -> 0;
    public interface WeakenSaved {
        void saved(ServerPlayer victim, net.minecraft.world.entity.Entity attacker, double amount);
    }

    public static WeakenSaved weakenSaved = (p, e, a) -> {};

    /** 결투 훅 (hp.js setDuelHooks — 6단계 결투가 넣음) */
    public interface DuelHooks {
        /** 결투 상대의 피해면 줄인 양 (× PVP.mult — 아니면 그대로) */
        double scale(ServerPlayer sp, DamageSource source, double loss);

        /** 이 (죽을) 피해로는 쓰러지지 않는지 (결투 상대 (탓) 의 피해 → 체력 1 로 남고 짐) */
        boolean floor(ServerPlayer sp, DamageSource source);

        /** 결투 상대에게 쓰러져 체력 1 로 남음 (다음 틱) */
        void lost(ServerPlayer sp, DamageSource source);
    }

    public static DuelHooks duel = new DuelHooks() {
        @Override
        public double scale(ServerPlayer sp, DamageSource source, double loss) {
            return loss;
        }

        @Override
        public boolean floor(ServerPlayer sp, DamageSource source) {
            return false;
        }

        @Override
        public void lost(ServerPlayer sp, DamageSource source) {}
    };

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MAXES.clear();
            SAVED.clear();
            SHIELDS.clear();
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) SHIELDS.remove(sp.getUUID());
        });
    }

    static void forget(UUID id) {
        MAXES.remove(id);
        SAVED.remove(id);
        SHIELDS.remove(id);
    }

    /** 보호막 더하기 (hp.js addShield): 양은 cap 까지, 지속은 새 ticks 와 남은 시간 중 긴 쪽. 실제로 늘어난 양 */
    public static double addShield(ServerPlayer sp, double amount, int ticks, double cap) {
        long now = Tasks.now();
        Shield cur = SHIELDS.get(sp.getUUID());
        Shield live = cur != null && cur.until() > now ? cur : null;
        double have = live != null ? live.amount() : 0;
        double next = Math.max(have, Math.min(cap, have + Math.max(0, amount)));
        SHIELDS.put(sp.getUUID(), new Shield(next, Math.max(live != null ? live.until() : 0, now + ticks)));
        return next - have;
    }

    /** 남은 보호막 (없으면 0) */
    public static double shieldOf(ServerPlayer sp) {
        Shield sh = SHIELDS.get(sp.getUUID());
        return sh != null && sh.until() > Tasks.now() ? sh.amount() : 0;
    }

    /** 보호막이 흡수하고 남은 피해 (hp.js absorbShield) */
    private static double absorbShield(ServerPlayer sp, double loss) {
        Shield sh = SHIELDS.get(sp.getUUID());
        if (sh == null) return loss;
        if (sh.until() <= Tasks.now()) {
            SHIELDS.remove(sp.getUUID());
            return loss;
        }
        double used = Math.min(sh.amount(), loss);
        double left = sh.amount() - used;
        if (left <= 1e-6) {
            SHIELDS.remove(sp.getUUID());
            Tasks.later(1, () -> {
                if (!sp.isRemoved()) shieldBroken.accept(sp);
            });
        } else {
            SHIELDS.put(sp.getUUID(), new Shield(left, sh.until()));
            if (used > 0) Tasks.later(1, () -> {
                if (!sp.isRemoved()) shieldAbsorbed.accept(sp, used);
            });
        }
        return loss - used;
    }

    /** 생존 · 모험 (크리에이티브 · 관전은 RPG 체력 규칙 없음 — hp.js isRpgMode) */
    public static boolean isRpgMode(ServerPlayer sp) {
        return sp.gameMode.getGameModeForPlayer().isSurvival();
    }

    /** 최대 체력 (hp.js maxHpOf): 기본 + 최고 레벨 보너스 + 실제 체력 스탯 + 스탯 트리 (7단계) */
    public static double maxHpOf(ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        return Hp.maxHpOf(d.lvmax(), Stats.get(RpgStats.effective(sp), "vit")) + mmorpg.fabric.stattree.StatTrees.hpAdd(sp);
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
        double shield = RpgCombat.shieldReduction(sp, source);
        Guards.Guard g = Guards.of(sp);
        double gr = g != null ? g.reduce() : 0;
        double buff = Math.max(0, Math.min(0.95, buffReduce.applyAsDouble(sp))); // 0.19.0 세계수의 가호
        double reduce = 1 - (1 - shield) * (1 - Math.max(gr, buff));
        // 스탯 트리 받는 피해 감소 (7단계): 방패 · 버프 감소 뒤에 곱함
        double tree = mmorpg.fabric.stattree.StatTrees.defMult(sp);
        double loss = duel.scale(sp, source, raw * CONFIG.num("DAMAGE_TAKEN_SCALE") * (1 - reduce) * tree); // 결투 상대의 피해 × PVP.mult
        Guards.afterHurt(sp, source, g); // 반사 버프: 때린 것에게 (다음 틱)
        // 0.20.0 심연의 낙인: 낙인 찍힌 적이 준 피해 × (1 − 낙인) — 받는 쪽 감소와 곱 (가호의 막은 양은 이 뒤의 깎임으로 셈)
        net.minecraft.world.entity.Entity from = source.getEntity();
        double weak = from != null && from != sp ? Math.max(0, Math.min(0.95, weakenOf.applyAsDouble(from))) : 0;
        if (weak > 0 && loss > 0) {
            double cut = loss * weak;
            loss -= cut;
            weakenSaved.saved(sp, from, cut);
        }
        if (buff > gr && loss > 0) {
            // 가호가 막아 준 양: 가호가 없었을 때 (Guards 감소만) 의 깎임 − 지금 깎임
            double without = 1 - (1 - shield) * (1 - gr);
            if (1 - reduce > 1e-6) buffSaved.accept(sp, loss * ((1 - without) / (1 - reduce) - 1));
        }
        boolean safe = isNonLethal(sp, source, raw);
        if (loss > 0 && !safe) loss = absorbShield(sp, loss); // 보호막 먼저
        double next = health - loss;
        if (next < 1 && safe) next = Math.min(health, 1);
        else if (next < 1 && undying.test(sp)) {
            next = Math.min(health, 1); // 불사의 맹약: 체력 1 에서 버팀
            Tasks.later(1, () -> {
                if (!sp.isRemoved()) undyingSaved.accept(sp);
            });
        } else if (next <= 0 && duel.floor(sp, source)) {
            next = Math.min(health, 1); // 결투 상대에게 쓰러짐 (죽을 피해): 죽지 않고 체력 1 (그보다 적었으면 그대로) 로 남음 → 결투에서 짐
            Tasks.later(1, () -> {
                if (!sp.isRemoved()) duel.lost(sp, source);
            });
        }
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

    /** 체력 회복 (hp.js healBy — 정한 양 그대로, 최대 체력까지). 실제로 오른 양 */
    public static double healBy(ServerPlayer sp, double amount) {
        if (sp.isDeadOrDying() || !(amount > 0)) return 0;
        float before = sp.getHealth();
        float next = (float) Math.min(sp.getMaxHealth(), before + amount);
        if (next > before) sp.setHealth(next);
        return Math.max(0, next - before);
    }

    /** 체력을 대가로 냄 (hp.js payHp): 1 아래로는 내지 못함 (0 = 못 냄). RPG 체력이 아니면 (크리에이티브) 0 */
    public static double payHp(ServerPlayer sp, double amount) {
        if (!isRpgMode(sp) || !(amount > 0) || sp.isDeadOrDying()) return 0;
        float hp = sp.getHealth();
        if (hp - amount < 1 - 1e-6) return 0;
        sp.setHealth((float) Math.max(1, hp - amount));
        return amount;
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
