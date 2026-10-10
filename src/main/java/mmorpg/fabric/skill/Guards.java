package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 받는 피해 감소 · 반사 버프 (combat.js setGuard · guardOf · reflectHit): 신의 방벽 (돔 안에서만), 룬 (강철 피부 · 반격 자세) 등.
 * 건 버프마다 한 층 (층마다 제 시간에 끝남): 감소는 걸린 것 중 가장 센 것, 반사는 가장 나중에 건 것.
 * 방패 감소와 합침: 1 − (1 − 방패) × (1 − 버프) (RpgHealth.onLoss).
 */
public final class Guards {
    private Guards() {}

    /**
     * 반사: fn 이 있으면 그것 (반격 자세), 아니면 amount (정한 양) 또는 무기 공격력 × mult × 스탯 배율을 가시 피해로, 숫자 모양 style
     */
    public record Reflect(double mult, Weapon w, Double amount, DamageNumbers.Style style, BiConsumer<ServerPlayer, LivingEntity> fn) {
        public static Reflect amount(double amount, DamageNumbers.Style style) {
            return new Reflect(0, null, amount, style, null);
        }

        public static Reflect fn(BiConsumer<ServerPlayer, LivingEntity> fn) {
            return new Reflect(0, null, null, null, fn);
        }
    }

    private record Layer(long until, double reduce, Reflect reflect, Predicate<ServerPlayer> cond) {}

    /** 지금 걸린 버프를 합친 것 */
    public record Guard(long until, double reduce, Reflect reflect) {}

    private static final Map<UUID, List<Layer>> GUARDS = new HashMap<>();

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GUARDS.clear());
    }

    /**
     * 버프 걸기: ticks 틱 동안 받는 피해 × (1 − reduce), reflect 가 있으면 때린 것에게 반사, cond 가 있으면 그 조건일 때만 (맞을 때마다 확인)
     */
    public static void set(ServerPlayer sp, int ticks, double reduce, Reflect reflect, Predicate<ServerPlayer> cond) {
        long now = Tasks.now();
        List<Layer> list = new ArrayList<>();
        for (Layer g : GUARDS.getOrDefault(sp.getUUID(), List.of())) if (g.until() > now) list.add(g);
        list.add(new Layer(now + ticks, reduce, reflect, cond));
        GUARDS.put(sp.getUUID(), list);
    }

    /** 모두 지움 (죽음 · 나감) */
    public static void clear(UUID id) {
        GUARDS.remove(id);
    }

    /** 지금 걸린 버프를 합친 것 (없으면 null) */
    public static Guard of(ServerPlayer sp) {
        long now = Tasks.now();
        List<Layer> cur = GUARDS.get(sp.getUUID());
        if (cur == null) return null;
        List<Layer> list = new ArrayList<>();
        for (Layer g : cur) if (g.until() > now) list.add(g);
        if (list.isEmpty()) {
            GUARDS.remove(sp.getUUID());
            return null;
        }
        GUARDS.put(sp.getUUID(), list);
        double reduce = 0;
        long until = now;
        Reflect reflect = null;
        boolean any = false;
        for (Layer g : list) {
            if (g.cond() != null) {
                boolean on;
                try {
                    on = g.cond().test(sp);
                } catch (RuntimeException e) {
                    on = false;
                }
                if (!on) continue;
            }
            any = true;
            reduce = Math.max(reduce, g.reduce());
            until = Math.max(until, g.until());
            if (g.reflect() != null) reflect = g.reflect();
        }
        return any ? new Guard(until, reduce, reflect) : null;
    }

    /**
     * 플레이어가 피해를 받은 뒤 (RpgHealth.onLoss): 반사 버프면 때린 것 (화살은 쏜 것) 에게 다음 틱에 반사.
     * 다른 플레이어에게는 결투 상대일 때만, 반사 피해 (가시) 에는 다시 반사하지 않음
     */
    public static void afterHurt(ServerPlayer sp, DamageSource source, Guard g) {
        if (g == null || g.reflect() == null || !(source.getEntity() instanceof LivingEntity from) || from == sp || from.isRemoved()) return;
        if (from instanceof Player p && (!(p instanceof ServerPlayer other) || !Kit.duelPartner.test(sp, other) || source.is(DamageTypes.THORNS))) return;
        Tasks.later(1, () -> reflectHit(sp, from, g));
    }

    static void reflectHit(ServerPlayer sp, LivingEntity attacker, Guard g) {
        if (sp.isRemoved() || attacker.isRemoved() || !attacker.isAlive()) return;
        Reflect r = g.reflect();
        if (r.fn() != null) {
            try {
                r.fn().accept(sp, attacker);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] counter", e);
            }
            return;
        }
        double dmg = r.amount() != null ? r.amount() : r.w() != null ? r.w().damage() * r.mult() * RpgCombat.powerOf(sp, r.w()) : 0;
        if (!Kit.applySkillDamage(sp, attacker, dmg, false, Kit.Cause.THORNS, r.style())) return;
        Vec3 at = Kit.bodyPoint(attacker);
        SkillFx.fx(sp.level(), "rpg:bash_burst", at, Opt.of("dir", V.SOUTH, "speed", 0.0, "spin", Kit.rand() * 360, "size", 0.7, "life", 0.25,
                "color", new double[] {1.0, 0.85, 0.4}, "alpha", 0.95));
        SkillFx.play(sp.level(), "item.shield.block", at, 1.3, 0.6);
    }
}
