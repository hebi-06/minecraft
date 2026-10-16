package mmorpg.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import mmorpg.core.Combat;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 무기 전투 (combat.js): 평타 피해 공식 · 요구 스탯 · 치명타, 활 · 완드 (누르거나 몹을 치면 바라보는 대상에 명중), 방패 감소.
 * 수치는 모두 규칙 코어 (Combat · Weapons · Stats) — 애드온과 같은 식.
 */
public final class RpgCombat {
    private RpgCombat() {}

    private static final Obj BOW = Rules.get().obj("BOW");
    private static final Obj WAND = Rules.get().obj("WAND");
    private static final Obj SHIELD = Rules.get().obj("SHIELD");

    /** 시험용: 치명타를 정함 (null = 확률대로) */
    public static Boolean forceCrit = null;

    /**
     * 맞히기 직전에 기억한 공격 (피해가 실제로 들어간 뒤 숫자 · 연출에 씀): 치명타였는지, 무기 (평타 · 활 · 완드 — 스킬은 null),
     * 근접 평타인지 (참격 연출), 스킬 · 룬 피해면 숫자 모양 (평타는 null — combat.js skillMarks), 평타에 더한 피해 (없으면 null)
     */
    public record Mark(boolean crit, long tick, Weapon w, boolean melee, DamageNumbers.Style style, WeaponBonus bonus) {}

    /**
     * 평타에 더하는 피해 하나 (룬 훅이 줌 — combat.js bonusHooks): add = 더할 양, mark = 숫자 앞 표시, key = 훅 구분,
     * style = 더한 몫의 숫자 모양 (null = 마검 부여의 보라 SPELL), grades = 평타 강화가 더한 등급들
     */
    public record Bonus(double add, String mark, String key, DamageNumbers.Style style, List<String> grades) {}

    /** 평타 한 번에 더한 피해 모두: add = 합, total = 평타 + 더한 것, mark = 표시들, parts = 훅마다 */
    public record WeaponBonus(double add, double total, String mark, List<Bonus> parts) {}

    /** 평타에 더할 것 (combat.js addWeaponBonusHook): 피해를 넣기 직전 (치명타 뒤) — 없으면 null */
    @FunctionalInterface
    public interface BonusHook {
        Bonus bonus(ServerPlayer attacker, LivingEntity victim, Weapon w);
    }

    /** 무기로 맞힘 (평타 · 활 · 완드 피해가 실제로 들어간 뒤 — combat.js addWeaponHitHook) */
    @FunctionalInterface
    public interface HitHook {
        void hit(ServerPlayer attacker, LivingEntity victim, Weapon w, WeaponBonus bonus);
    }

    /** 평타 치명타 확률에 더할 것 (combat.js addCritBonusHook — victim 은 null 일 수 있음: 표시용) */
    @FunctionalInterface
    public interface CritHook {
        double add(ServerPlayer attacker, Weapon w, LivingEntity victim);
    }

    /** 활 · 완드 재사용 대기 배율 (combat.js addRangedCooldownHook — 가장 작은 것, 1 = 그대로) */
    @FunctionalInterface
    public interface RangedCdHook {
        double scale(ServerPlayer sp, Weapon w);
    }

    private static final List<BonusHook> BONUS_HOOKS = new ArrayList<>();
    private static final List<HitHook> HIT_HOOKS = new ArrayList<>();
    private static final List<CritHook> CRIT_HOOKS = new ArrayList<>();
    private static final List<RangedCdHook> RANGED_CD_HOOKS = new ArrayList<>();
    /** 평타에 더한 피해가 들어간 틱 (대상|공격자): 그 뒤 BONUS_GAP 틱 (무적 시간) 안의 평타에는 더하지 않음 */
    private static final Map<String, Long> BONUS_LANDED = new HashMap<>();
    private static final int BONUS_GAP = 10;
    /** 밀려나지 않음 (신체 강화 룬 강철 근육): 플레이어 → 끝나는 틱 */
    private static final Map<UUID, Long> STEADFAST = new HashMap<>();
    private static final double CRIT_CAP = Rules.get().obj("RUNE_STRIKE").num("critCap");

    public static void addWeaponBonusHook(BonusHook h) {
        BONUS_HOOKS.add(h);
    }

    public static void addWeaponHitHook(HitHook h) {
        HIT_HOOKS.add(h);
    }

    public static void addCritBonusHook(CritHook h) {
        CRIT_HOOKS.add(h);
    }

    public static void addRangedCooldownHook(RangedCdHook h) {
        RANGED_CD_HOOKS.add(h);
    }

    public static void setSteadfast(ServerPlayer sp, int ticks) {
        STEADFAST.merge(sp.getUUID(), Tasks.now() + ticks, Math::max);
    }

    /** 보스 · 몬스터 기술의 밀치기 · 끌어당김을 받지 않는지 */
    public static boolean isSteadfast(Entity e) {
        Long t = e == null ? null : STEADFAST.get(e.getUUID());
        return t != null && t > Tasks.now();
    }

    public static void clearSteadfast(UUID id) {
        STEADFAST.remove(id);
    }

    /**
     * 평타 치명타 확률 (combat.js critChanceOf): 무기 확률 + 훅들 (더한 것이 있으면 RUNE_STRIKE.critCap 까지 — 무기 확률이 더 높으면 그대로)
     */
    public static double critChanceOf(ServerPlayer sp, Weapon w, LivingEntity victim) {
        double add = 0;
        for (CritHook h : CRIT_HOOKS) {
            try {
                add += Math.max(0, h.add(sp, w, victim));
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] crit bonus", e);
            }
        }
        return Combat.critChanceOf(w, add, CRIT_CAP);
    }

    static double rangedCdScale(ServerPlayer sp, Weapon w) {
        double k = 1;
        for (RangedCdHook h : RANGED_CD_HOOKS) {
            try {
                double v = h.scale(sp, w);
                if (v > 0 && v < k) k = v;
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] ranged cooldown", e);
            }
        }
        return k;
    }

    /** 평타 피해 dmg 에 더할 것 (combat.js weaponBonus — 없으면 null) */
    static WeaponBonus weaponBonus(ServerPlayer attacker, LivingEntity victim, Weapon w, double dmg) {
        if (BONUS_HOOKS.isEmpty()) return null;
        Long landed = BONUS_LANDED.get(key(victim, attacker));
        if (landed != null && Tasks.now() - landed < BONUS_GAP) return null; // 무적 시간 안의 평타에는 더하지 않음
        double add = 0;
        StringBuilder mk = new StringBuilder();
        List<Bonus> parts = new ArrayList<>();
        for (BonusHook h : BONUS_HOOKS) {
            try {
                Bonus b = h.bonus(attacker, victim, w);
                if (b != null && b.add() > 0) {
                    add += b.add();
                    if (b.mark() != null) mk.append(b.mark());
                    parts.add(b);
                }
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] weapon bonus", e);
            }
        }
        return add > 0 ? new WeaponBonus(add, dmg + add, mk.toString(), parts) : null;
    }

    /** 평타에 더한 피해가 들어감 (DamageNumbers — 그 뒤 무적 시간 동안은 더하지 않음) */
    public static void bonusLanded(Entity victim, Entity attacker, long tick) {
        BONUS_LANDED.put(key(victim, attacker), tick);
        if (BONUS_LANDED.size() > 512) BONUS_LANDED.values().removeIf(t -> Tasks.now() - t > 40);
    }

    /** 무기로 맞힘 훅들 (DamageNumbers 가 피해가 들어간 뒤 부름) */
    public static void weaponHit(ServerPlayer attacker, LivingEntity victim, Weapon w, WeaponBonus bonus) {
        for (HitHook h : HIT_HOOKS) {
            try {
                h.hit(attacker, victim, w, bonus);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] weapon hit hook", e);
            }
        }
    }

    private static final Map<String, Mark> MARKS = new HashMap<>();
    /** 활 · 완드 재사용 (플레이어|종류 → 다시 쏠 수 있는 틱) */
    private static final Map<String, Long> RANGED_READY = new HashMap<>();
    /** 방패로 때린 틱 (그 뒤 잠깐 막기 풀림) */
    private static final Map<UUID, Long> LAST_BASH = new HashMap<>();
    private static final Map<UUID, Cast> CASTING = new HashMap<>();

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            MARKS.clear();
            RANGED_READY.clear();
            LAST_BASH.clear();
            NO_BLOCK.clear();
            CASTING.clear();
        });
    }

    static void forget(UUID id) {
        RANGED_READY.keySet().removeIf(k -> k.startsWith(id + "|"));
        LAST_BASH.remove(id);
        CASTING.remove(id);
        STEADFAST.remove(id);
    }

    // ------------------------------------------------------------ 무기 · 요구 조건

    public static Weapon weaponOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof RpgWeaponItem item ? item.weapon() : null;
    }

    public static boolean isRanged(Weapon w) {
        return w != null && (w.type().equals("bow") || w.type().equals("wand"));
    }

    /** 요구 레벨에 쓰는 레벨 = max(최고 레벨 기록, 지금 레벨) (stats.js reqLevelOf) */
    public static double reqLevelOf(ServerPlayer sp) {
        return Stats.reqLevelOf(RpgData.of(sp).lvmax(), sp.experienceLevel);
    }

    /** 무기를 쓰는 데 모자란 것 (레벨 · 찍은 스탯 — 장비 보너스 제외) */
    public static List<Weapons.Lack> lackOf(ServerPlayer sp, Weapon w) {
        return Weapons.weaponLack(w, reqLevelOf(sp), RpgData.of(sp).base());
    }

    public static String reqText(ServerPlayer sp, Weapon w) {
        return Combat.reqText(w, reqLevelOf(sp), RpgData.of(sp).base());
    }

    /**
     * 무기 피해 배율 = 스탯 배율 × (1 + 강화 보너스 — 손에 든 그 무기의 강화 단계, combat.js powerMultiplier) × 스탯 트리 공격력 (7단계 — 평타 · 스킬)
     */
    public static double powerOf(ServerPlayer sp, Weapon w) {
        return Combat.powerMultiplier(RpgStats.effective(sp), w, mmorpg.fabric.gear.WeaponEnhance.held(sp, w)) * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
    }

    /** 치명타 배율 = 무기 배율 + 스탯 트리 치명타 피해 (7단계 — ×1.5 에 +10% → ×1.6) */
    public static double critMultOf(ServerPlayer sp, Weapon w) {
        return w.critMult() + mmorpg.fabric.stattree.StatTrees.critDmgAdd(sp);
    }

    public static boolean rollCrit(ServerPlayer sp, Weapon w) {
        return rollCrit(sp, w, null);
    }

    /** 평타 치명타 (룬 훅으로 더한 확률 포함 — victim 은 null 일 수 있음) */
    public static boolean rollCrit(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (forceCrit != null) return forceCrit;
        return sp.getRandom().nextDouble() < critChanceOf(sp, w, victim);
    }

    /** 주민 · 상인 (눌러서 거래 — 활 · 완드로 쏘지 않음) */
    public static boolean isTalkable(Entity e) {
        return e instanceof AbstractVillager;
    }

    private static String key(Entity victim, Entity attacker) {
        return victim.getUUID() + "|" + attacker.getUUID();
    }

    static void mark(Entity victim, ServerPlayer attacker, boolean crit, Weapon w, boolean melee, WeaponBonus bonus) {
        MARKS.put(key(victim, attacker), new Mark(crit, Tasks.now(), w, melee, null, bonus));
    }

    /** 스킬 · 룬 피해 표시 (숫자 모양 style — 평타 공식 · 참격 연출 없음) */
    public static void skillMark(Entity victim, ServerPlayer attacker, boolean crit, DamageNumbers.Style style) {
        MARKS.put(key(victim, attacker), new Mark(crit, Tasks.now(), null, false, style, null));
    }

    /** 들어가지 않은 공격의 기억을 지움 */
    public static void dropMark(Entity victim, Entity attacker) {
        MARKS.remove(key(victim, attacker));
    }

    /** 맞은 뒤 기억을 꺼냄 (2틱 안의 공격만) */
    public static Mark takeMark(Entity victim, ServerPlayer attacker) {
        Mark m = MARKS.remove(key(victim, attacker));
        if (MARKS.size() > 256) MARKS.values().removeIf(x -> Tasks.now() - x.tick() > 40);
        return m != null && Tasks.now() - m.tick() <= 2 ? m : null;
    }

    // ------------------------------------------------------------ 평타 (PlayerMixin)

    /**
     * Player#attack 시작 (서버 · 클라이언트 둘 다). true = 바닐라 공격 취소.
     * 활 · 완드로 몹을 치면 그 몹에게 쏨 (모바일 터치와 같게), 요구 조건이 모자란 근접 무기는 막고 경고.
     */
    public static boolean beforeAttack(Player p, Entity target) {
        // 탈것에 탄 동안은 공격 없음 (riding.js — 맨손 · 모든 무기)
        if (mmorpg.fabric.mount.Riding.isMounted(p)) {
            if (p instanceof ServerPlayer sp) mmorpg.fabric.mount.Riding.blocked(sp, "attack");
            return true;
        }
        Weapon w = weaponOf(p.getMainHandItem());
        if (w == null) return false;
        if (isRanged(w)) {
            if (isTalkable(target)) return false;
            if (p instanceof ServerPlayer sp) fireRanged(sp, p.getMainHandItem(), w, target instanceof LivingEntity le ? le : null);
            return true;
        }
        if (!(p instanceof ServerPlayer sp) || !Combat.isMeleeWeapon(w)) return false;
        if (!lackOf(sp, w).isEmpty()) {
            Notify.warn(sp, reqText(sp, w));
            return true;
        }
        if (w.type().equals("shield")) LAST_BASH.put(sp.getUUID(), Tasks.now());
        return false;
    }

    /**
     * 근접 무기 평타 피해 (combat.js adjustOutgoing): 무기 공격력 × 스탯 배율 × 치명타 배율.
     * 자바의 공격 쿨타임은 그대로 (덜 찬 공격은 0.2 + 0.8 × 충전² — 바닐라와 같음).
     */
    public static float meleeDamage(Player p, Entity target, float vanilla, float strength) {
        if (!(p instanceof ServerPlayer sp) || target == null) return vanilla;
        Weapon w = weaponOf(p.getMainHandItem());
        if (w == null || !Combat.isMeleeWeapon(w)) return vanilla;
        double s = Math.max(0, Math.min(1, strength));
        double dmg = w.damage() * powerOf(sp, w) * (0.2 + s * s * 0.8);
        LivingEntity victim = target instanceof LivingEntity le ? le : null;
        boolean crit = rollCrit(sp, w, victim);
        if (crit) dmg *= critMultOf(sp, w);
        WeaponBonus bonus = victim != null ? weaponBonus(sp, victim, w, dmg) : null; // 마검 부여 · 평타 강화 룬
        if (bonus != null) dmg += bonus.add();
        mark(target, sp, crit, w, true, bonus);
        return (float) dmg;
    }

    // ------------------------------------------------------------ 방패 (combat.js shieldReduction)

    /**
     * 받는 피해 감소 비율 (0~1): 오른손 방패 = min(상한, 방패 감소 + 체력 × 1포인트당), 웅크리고 앞에서 온 공격은 막기 비율.
     * 방패로 때린 뒤 잠깐 (bashBlockLockTicks) 은 막기 없음.
     */
    public static double shieldReduction(ServerPlayer sp, DamageSource src) {
        Weapon w = weaponOf(sp.getMainHandItem());
        if (w == null || !w.type().equals("shield")) return 0;
        if (!lackOf(sp, w).isEmpty()) {
            if (sp.isShiftKeyDown()) Notify.warn(sp, reqText(sp, w));
            return 0;
        }
        double vit = Stats.get(RpgStats.effective(sp), "vit");
        double r = Math.min(SHIELD.num("passiveCap"), w.passive() + vit * SHIELD.num("passivePerVit"));
        Entity from = src.getDirectEntity() != null ? src.getDirectEntity() : src.getEntity();
        if (sp.isShiftKeyDown() && from != null && !from.isRemoved() && !bashLocked(sp) && !noBlockNow(sp)) {
            Vec3 dir = sp.getViewVector(1F);
            double dx = from.getX() - sp.getX();
            double dz = from.getZ() - sp.getZ();
            double hv = Math.hypot(dir.x, dir.z);
            double len = Math.hypot(dx, dz) * (hv > 0 ? hv : 1);
            double dot = len > 0 ? (dx * dir.x + dz * dir.z) / len : 1;
            if (dot > 0.2) {
                r = Math.max(r, w.block());
                Fx.block(sp);
            }
        }
        return r;
    }

    static boolean bashLocked(ServerPlayer sp) {
        Long t = LAST_BASH.get(sp.getUUID());
        return t != null && Tasks.now() - t < SHIELD.numOr("bashBlockLockTicks", 0);
    }

    /** v1.8.0 방향이 없는 공격 (보스의 발밑 폭발 · 까마귀 · 기믹 벌 — combat.js markNoBlock): 플레이어 → 틱 */
    private static final Map<UUID, Long> NO_BLOCK = new HashMap<>();

    /** 이번 (과 다음) 틱의 피해는 웅크려 막기 (방향 막기) 가 되지 않음 — 방패의 기본 감소 · 보호 룬은 그대로 (보스 bossHit 이 부름) */
    public static void markNoBlock(ServerPlayer sp) {
        NO_BLOCK.put(sp.getUUID(), Tasks.now());
        if (NO_BLOCK.size() > 256) NO_BLOCK.values().removeIf(t -> Tasks.now() - t > 1);
    }

    static boolean noBlockNow(ServerPlayer sp) {
        Long t = NO_BLOCK.get(sp.getUUID());
        if (t == null) return false;
        if (Tasks.now() - t > 1) {
            NO_BLOCK.remove(sp.getUUID());
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ 활 · 완드 (combat.js castRanged)

    /**
     * 활 · 완드 발사: 바라보는 대상 (또는 forced = 눌러서 때린 몹) 에 명중. 재사용 대기는 종류마다.
     * 활은 빛줄기가 닿는 순간 (초당 arrowSpeed 칸), 완드는 마법진을 다 그린 뒤 (castTicks) 맞음.
     */
    public static void fireRanged(ServerPlayer sp, ItemStack stack, Weapon w, LivingEntity forced) {
        if (mmorpg.fabric.mount.Riding.blocked(sp, "attack")) return; // 탈것에 탄 동안 활 · 완드 평타 없음
        Obj cfg = w.type().equals("bow") ? BOW : WAND;
        String k = sp.getUUID() + "|" + w.type();
        long now = Tasks.now();
        Long ready = RANGED_READY.get(k);
        if (ready != null && ready > now) return;
        if (!lackOf(sp, w).isEmpty()) {
            Notify.warn(sp, reqText(sp, w));
            return;
        }
        // 연타 (평타 강화 룬): 재사용 대기 × 배율 (완드는 시전 시간보다 짧아지지 않게)
        int cd = (int) Math.max(w.type().equals("wand") ? WAND.num("castTicks") + 1 : 2, Math.round(cfg.num("cooldownTicks") * rangedCdScale(sp, w)));
        RANGED_READY.put(k, now + cd);
        sp.getCooldowns().addCooldown(stack, cd);
        if (w.type().equals("wand")) {
            if (!CASTING.containsKey(sp.getUUID())) startWandCast(sp, w, forced);
            return;
        }
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 dir;
        LivingEntity target;
        double dist;
        if (forced != null) {
            // 눌러서 때린 몹: 몸 가운데 (키의 3/4) 쪽으로, 몸 겉까지
            Vec3 d = bodyPoint(forced).subtract(head);
            double len = d.length();
            dir = len > 1e-6 ? d.scale(1 / len) : sp.getViewVector(1F);
            target = forced;
            dist = Math.max(0.5, len - 0.3);
        } else {
            Aim a = aimTarget(sp, cfg.num("range"), cfg.numOr("aimAssist", 0));
            target = a.target();
            dist = a.dist();
            dir = a.dir();
        }
        Path path = arrowPath(head, dir, dist);
        Shot shot = arrowShot(path.length());
        Fx.sound(level, head, SoundEvents.ARROW_SHOOT, 1.1, 0.8);
        int ticks = Math.max(1, (int) Math.round(shot.life() * 20));
        Fx.arrowFx(level, path, shot, w);
        if (target != null) {
            LivingEntity t = target;
            Tasks.later(ticks, () -> rangedHit(sp, t, w, path));
        }
    }

    /** 원거리 명중 (combat.js rangedHit): 활 = 공격 피해 (방어구 적용), 완드 = 마법 피해 (방어 무시) */
    static void rangedHit(ServerPlayer sp, LivingEntity target, Weapon w, Path path) {
        if (sp.isRemoved() || target.isRemoved() || !target.isAlive()) return;
        ServerLevel level = sp.level();
        double dmg = w.damage() * powerOf(sp, w);
        boolean crit = rollCrit(sp, w, target);
        if (crit) dmg *= critMultOf(sp, w);
        WeaponBonus bonus = weaponBonus(sp, target, w, dmg);
        if (bonus != null) dmg += bonus.add();
        mark(target, sp, crit, w, false, bonus);
        DamageSource src = w.type().equals("bow") ? sp.damageSources().playerAttack(sp) : sp.damageSources().indirectMagic(sp, sp);
        boolean hurt = target.level() instanceof ServerLevel tl && target.hurtServer(tl, src, (float) dmg);
        if (!hurt) {
            MARKS.remove(key(target, sp));
            return;
        }
        if (path != null) {
            Fx.arrowImpact(level, path.aim(), path.dir(), w);
            Fx.sound(level, path.aim(), SoundEvents.ARROW_HIT, 1.0, 0.7);
        } else {
            Vec3 at = target.getEyePosition();
            Fx.wandImpact(level, at, w);
            Fx.sound(level, at, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6, 0.5);
        }
    }

    private record Cast(Weapon w, long start, LivingEntity forced, double spin) {}

    /** 완드 시전: 매 틱 플레이어를 따라가며 마법진이 한 단계씩 그려짐. 손의 완드를 바꾸거나 쓰러지면 취소 */
    static void startWandCast(ServerPlayer sp, Weapon w, LivingEntity forced) {
        Cast cast = new Cast(w, Tasks.now(), forced, sp.getRandom().nextDouble() * 360);
        CASTING.put(sp.getUUID(), cast);
        Fx.sound(sp.level(), sp.getEyePosition(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.3, 0.8);
        castStep(sp, cast);
    }

    private static void castStep(ServerPlayer sp, Cast cast) {
        if (CASTING.get(sp.getUUID()) != cast) return;
        Weapon held = weaponOf(sp.getMainHandItem());
        if (sp.isRemoved() || !sp.isAlive() || held == null || !held.id().equals(cast.w().id())) {
            CASTING.remove(sp.getUUID());
            return;
        }
        long t = Tasks.now() - cast.start();
        double castTicks = WAND.num("castTicks");
        if (t >= castTicks) {
            CASTING.remove(sp.getUUID());
            fireWand(sp, cast);
            return;
        }
        Vec3[] c = circleAt(sp);
        Fx.magicCircle(sp.level(), c[0], cast.w(), cast.spin() + t * WAND.obj("circle").num("spinPerTick"), (t + 1) / castTicks);
        Tasks.later(1, () -> castStep(sp, cast));
    }

    /** 시전 완료: 마법진이 번쩍이고 가운데서 대상까지 궤적 + 마법 피해 */
    private static void fireWand(ServerPlayer sp, Cast cast) {
        ServerLevel level = sp.level();
        Vec3[] c = circleAt(sp);
        Vec3 from = c[0];
        Vec3 head = c[2];
        LivingEntity target;
        Vec3 aim;
        LivingEntity f = cast.forced();
        Vec3 fp = f != null && f.isAlive() && !f.isRemoved() && f.level() == level ? bodyPoint(f) : null;
        if (fp != null && fp.distanceTo(head) <= WAND.num("range") + 2) {
            Vec3 d = fp.subtract(head);
            target = f;
            aim = head.add(d.normalize().scale(Math.max(0.5, d.length() - 0.3)));
        } else {
            // 다 그려진 순간 바라보는 대상 (시전 중에 조준을 옮길 수 있음)
            Aim a = aimTarget(sp, WAND.num("range"), WAND.numOr("aimAssist", 0));
            target = a.target();
            aim = head.add(a.dir().scale(a.dist()));
        }
        Fx.circleFlash(level, from, cast.w(), cast.spin() + WAND.num("castTicks") * WAND.obj("circle").num("spinPerTick"));
        Fx.wandBeam(level, from, aim, cast.w());
        Fx.sound(level, head, SoundEvents.EVOKER_CAST_SPELL, 1.5, 0.6);
        if (target != null) rangedHit(sp, target, cast.w(), null);
    }

    /** 마법진 자리: 눈앞 · 완드를 든 오른손 쪽 · 조금 아래 → {자리, 바라보는 방향, 머리} */
    public static Vec3[] circleAt(ServerPlayer sp) {
        Vec3 head = sp.getEyePosition();
        Vec3 dir = sp.getViewVector(1F);
        Vec3 right = Fx.basis(dir)[0];
        Obj c = WAND.obj("circle");
        Vec3 at = head.add(dir.scale(c.num("dist"))).add(right.scale(c.num("side"))).add(0, -c.num("drop"), 0);
        return new Vec3[] {at, dir, head};
    }

    // ------------------------------------------------------------ 조준 (combat.js aimTarget · assistTarget)

    /** 맞힐 대상 (없으면 null), 머리에서 잰 연출 거리, 방향 */
    public record Aim(LivingEntity target, double dist, Vec3 dir) {}

    /** 바라보는 쪽 사거리 안의 가장 가까운 대상 (블록 뒤는 안 됨), 없으면 시선 가까이의 몹 (조준 보정 assistDeg 도) */
    public static Aim aimTarget(ServerPlayer sp, double range, double assistDeg) {
        Vec3 head = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        Entity mount = sp.getVehicle();
        Vec3 end = head.add(view.scale(range));
        BlockHitResult bh = sp.level().clip(new ClipContext(head, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double blockDist = bh.getType() == HitResult.Type.MISS ? range : head.distanceTo(bh.getLocation());
        AABB box = sp.getBoundingBox().expandTowards(view.scale(range)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(sp, head, end, box, e -> e != mount && aimable(sp, e), range * range);
        LivingEntity target = null;
        double dist = Math.min(range, blockDist);
        if (eh != null && eh.getEntity() instanceof LivingEntity le) {
            double d = head.distanceTo(eh.getLocation());
            if (d <= blockDist + 0.25) {
                target = le;
                dist = Math.min(Math.min(range, d), blockDist);
            }
        }
        if (target == null && assistDeg > 0) {
            Aim a = assist(sp, range, assistDeg, x -> x != mount && assistable(sp, x));
            if (a != null) return a;
        }
        return new Aim(target, dist, view);
    }

    /** 바로 겨눌 수 있는 것: 살아 있는 생명체 (갑옷 거치대 제외, 다른 플레이어는 결투 상대만 — 아니면 지나가 뒤의 몹을 맞힘) */
    public static boolean aimable(ServerPlayer sp, Entity e) {
        if (!(e instanceof LivingEntity le) || e == sp || !le.isAlive() || e.isSpectator() || !e.isPickable()) return false;
        if (e instanceof Player p) return mmorpg.fabric.skill.Kit.duelPartner.test(sp, p);
        return !(e instanceof ArmorStand);
    }

    /** 조준 보정 대상: 주민 · 골렘 · 길들인 동물 · 플레이어 제외 */
    public static boolean assistable(ServerPlayer sp, LivingEntity e) {
        if (!aimable(sp, e) || isTalkable(e) || e instanceof IronGolem || e instanceof SnowGolem) return false;
        return !(e instanceof OwnableEntity o && o.getOwnerReference() != null);
    }

    /**
     * 조준 보정 (combat.js assistTarget): 시선에서 deg 도 안 (가까우면 옆으로 1칸 안) 의 대상 (ok) 중 가장 가운데에 가까운 것 (벽 뒤 제외).
     * 없으면 null. 스킬은 ok 에 맞힐 수 있는 대상 (Kit.canHit) 을 줌
     */
    public static Aim assist(ServerPlayer sp, double range, double deg, Predicate<LivingEntity> ok) {
        Vec3 head = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        double cosMax = Math.cos(Math.toRadians(deg));
        AABB box = new AABB(head, head).inflate(range + 1.5);
        record Cand(LivingEntity e, Vec3 p, double len, Vec3 d, double score) {}
        List<Cand> cands = new ArrayList<>();
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, box, ok)) {
            Vec3 p = bodyToward(e, head, view);
            Vec3 d = p.subtract(head);
            double len = d.length();
            if (len > range + 0.5 || len < 0.3) continue;
            double c = d.dot(view) / len;
            if (c <= 0) continue;
            double lateral = Math.sqrt(Math.max(0, 1 - c * c)) * len;
            if (c < cosMax && lateral > 1.0 + bodyPad(e)) continue;
            cands.add(new Cand(e, p, len, d, Math.toDegrees(Math.acos(Math.min(1, c))) + len * 0.15));
        }
        cands.sort(Comparator.comparingDouble(Cand::score));
        for (int i = 0; i < Math.min(6, cands.size()); i++) {
            Cand c = cands.get(i);
            if (lineOfSight(sp, head, c.p())) return new Aim(c.e(), Math.max(0.5, c.len() - 0.3), c.d().normalize());
        }
        return null;
    }

    /** from 에서 to 까지 막는 블록이 없는지 (풀 · 꽃 · 물은 통과) */
    public static boolean lineOfSight(ServerPlayer sp, Vec3 from, Vec3 to) {
        double len = to.distanceTo(from);
        if (len < 0.3) return true;
        BlockHitResult hit = sp.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(from) >= len - 0.35;
    }

    /** 몸 가운데 (발과 눈 사이 3/4) */
    public static Vec3 bodyPoint(Entity e) {
        return new Vec3(e.getX(), e.getY() + (e.getEyeY() - e.getY()) * 0.75, e.getZ());
    }

    /** 보통 몹보다 넓은 만큼 (가로 반폭 − 0.4 — 큰 몸은 몸 가장자리가 범위에 걸쳐도 맞음) */
    public static double bodyPad(Entity e) {
        return Math.max(0, e.getBbWidth() / 2 - 0.4);
    }

    /**
     * 스킬이 겨누는 몸의 세로 범위 {아래, 위} (combat.js bodySpan): 보통 몹은 몸 가운데 한 점,
     * 키가 2칸 넘는 몸 (보스 · 엔더맨 등) 은 발 1.28칸 위부터 머리 0.72칸 아래까지 (세로 선분)
     */
    public static double[] bodySpan(Entity e) {
        if (e.getBbHeight() > 2) return new double[] {e.getY() + 1.28, e.getY() + e.getBbHeight() - 0.72};
        double y = bodyPoint(e).y;
        return new double[] {y, y};
    }

    /** 직선 (from → 단위 방향 d) 에 가장 가까운 몸 위의 점 (키 2칸 넘는 몸은 세로 선분) */
    public static Vec3 bodyToward(Entity e, Vec3 from, Vec3 d) {
        double[] span = bodySpan(e);
        double lo = span[0];
        double hi = span[1];
        if (hi - lo < 1e-6) return new Vec3(e.getX(), lo, e.getZ());
        double wx = from.x - e.getX();
        double wz = from.z - e.getZ();
        double dw = d.x * wx + d.y * from.y + d.z * wz;
        double den = 1 - d.y * d.y;
        double s = den > 1e-6 ? (from.y - d.y * dw) / den : (lo + hi) / 2;
        return new Vec3(e.getX(), Math.max(lo, Math.min(hi, s)), e.getZ());
    }

    // ------------------------------------------------------------ 활 빛줄기 모양 (combat.js SHOT · arrowPath · arrowShot)

    public record Path(Vec3 from, Vec3 aim, Vec3 dir, double length) {}

    public record Shot(double half, double life, double speed) {}

    /** 활을 든 오른손 아래쪽에서 나와 조준점 (머리에서 dist 칸) 에 닿는 경로 */
    public static Path arrowPath(Vec3 head, Vec3 dir, double dist) {
        Vec3 right = Fx.basis(dir)[0];
        double k = Math.min(1, Math.max(0.15, dist / 1.5));
        Vec3 from = head.add(dir.scale(0.6 * k)).add(right.scale(0.28 * k)).add(0, -0.22 * k, 0);
        Vec3 aim = head.add(dir.scale(dist));
        Vec3 d = aim.subtract(from);
        double len = d.length();
        return new Path(from, aim, len > 1e-6 ? d.scale(1 / len) : dir, len);
    }

    /** 빛줄기 크기 · 수명 · 속도 (앞끝이 수명이 끝날 때 조준점에 닿음) */
    public static Shot arrowShot(double pathLength) {
        double half = Math.min(1.1, pathLength * 0.35);
        double travel = Math.max(0, pathLength - 2 * half);
        double life = Math.max(0.05, travel / BOW.num("arrowSpeed"));
        return new Shot(half, life, travel / life);
    }
}
