package mmorpg.fabric;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 12단계 새 무기의 기본 공격 특징 (TYPES[종류] — rules_more.json). 근접 평타의 피해가 실제로 들어간 뒤 (DamageNumbers — 참격 연출 다음) 부름.
 * <ul>
 *   <li>낫 sweep: 맞힌 적 말고 앞 cone° · range 칸 안의 다른 적 (적대 무리 · 결투 상대 — Kit.splashFoe, 눈에서 보이는) 에게 준 피해 × ratio,
 *       맞은 자리에 낫 빛 불꽃 (0.25.2 — 작은 참격 대신: 낫의 참격은 휘두른 초승달 하나만)</li>
 *   <li>전투 망치 quake: 맞힌 적 둘레 radius 칸의 다른 적에게 준 피해 × ratio + 살짝 띄움 (lift)</li>
 *   <li>성스러운 홀 lifesteal: 준 피해 × rate 만큼 회복 (한 번에 최대 체력 × cap 까지 — 초록 + 숫자)</li>
 *   <li>창 reach 는 아이템 속성 (RpgWeaponItem) 이라 여기 없음</li>
 * </ul>
 * 번진 피해는 평타 공식을 다시 거치지 않음 (스킬 피해처럼 그대로 · 숫자는 평타 색 — 치명타였으면 금색) 이라 다시 번지지 않음.
 */
public final class WeaponTraits {
    private WeaponTraits() {}

    /** 한 번에 번지는 적 수 상한 */
    static final int MAX_SPLASH = 6;

    /** 0.29.0 낫 넓게 베기를 바꾸는 것 (사신 현신 — {범위 칸, 비율}, null = 그대로) */
    @FunctionalInterface
    public interface SweepMod {
        double[] mod(ServerPlayer sp, Weapon w);
    }

    public static SweepMod sweepMod = (sp, w) -> null;

    /** 0.29.0 넓게 베기가 다른 적에게 들어감 (사신 「혼」 — 맞힌 적마다 +1) */
    public static java.util.function.BiConsumer<ServerPlayer, LivingEntity> swept = (sp, e) -> {};

    /** 넓게 베기의 {범위, 비율} (직업 태세가 바꾸면 그것) */
    static double[] sweepOf(ServerPlayer sp, Weapon w, Obj sweep) {
        double[] m = null;
        try {
            m = sweepMod.mod(sp, w);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] sweep mod", e);
        }
        return m != null && m.length >= 2 ? m : new double[] {sweep.num("range"), sweep.num("ratio")};
    }

    /**
     * 근접 평타가 victim 에게 dealt 만큼 들어감 (crit = 치명타였는지)
     */
    public static void hit(ServerPlayer sp, LivingEntity victim, Weapon w, boolean crit, double dealt) {
        Obj t = w.typeDef();
        if (t == null || !(dealt > 0) || sp.isRemoved()) return;
        // 0.20.0 넓게 베기 · 내려찍기는 맞힌 적의 낙인 몫을 빼고 번짐 (번진 적에게 낙인이 있으면 거기서 다시 곱함 — 두 번 세지 않게). 흡혈은 실제로 준 피해 그대로
        double mt = RpgCombat.takenMult.applyAsDouble(victim);
        double base = mt > 0 ? dealt / mt : dealt;
        Obj sweep = t.objOrNull("sweep");
        if (sweep != null) sweep(sp, victim, w, crit, base, sweep);
        Obj quake = t.objOrNull("quake");
        if (quake != null) quake(sp, victim, crit, base, quake);
        Obj steal = t.objOrNull("lifesteal");
        if (steal != null) lifesteal(sp, dealt, steal);
    }

    /** 낫: 앞 부채꼴의 다른 적 */
    static List<LivingEntity> sweepTargets(ServerPlayer sp, LivingEntity victim, Obj sweep) {
        return sweepTargets(sp, victim, sweep, sweep.num("range"));
    }

    /** 같은 것 — 범위 range 칸 (0.29.0 사신 현신) */
    static List<LivingEntity> sweepTargets(ServerPlayer sp, LivingEntity victim, Obj sweep, double range) {
        Vec3 pos = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double half = Math.toRadians(sweep.num("cone") / 2);
        List<LivingEntity> cone = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, pos, range, 2)) {
            if (e != victim && Kit.splashFoe(sp, e) && Kit.bodyInCone(e, pos, f, half, 0.6)) cone.add(e);
        }
        List<LivingEntity> out = Kit.visibleFrom(sp, sp.getEyePosition(), cone);
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        return out.size() > MAX_SPLASH ? new ArrayList<>(out.subList(0, MAX_SPLASH)) : out;
    }

    private static void sweep(ServerPlayer sp, LivingEntity victim, Weapon w, boolean crit, double dealt, Obj sweep) {
        ServerLevel level = sp.level();
        double[] rr = sweepOf(sp, w, sweep);
        double amount = dealt * rr[1];
        Obj style = Slash.styleOf(w);
        double[] glow = style != null && style.has("glow") ? Kit.rgb(style.get("glow")) : new double[] {0.66, 0.5, 1};
        for (LivingEntity e : sweepTargets(sp, victim, sweep, rr[0])) {
            if (!Kit.applySkillDamage(sp, e, amount, crit, Kit.Cause.ATTACK, crit ? DamageNumbers.Style.CRIT : DamageNumbers.Style.NORMAL)) continue;
            try {
                swept.accept(sp, e);
            } catch (RuntimeException err) {
                Mmorpg.LOG.warn("[mmorpg] swept hook", err);
            }
            Vec3 c = Kit.bodyPoint(e);
            Vec3 u = V.unit(V.sub(c, sp.getEyePosition()));
            Vec3 front = V.add(c, u, -0.35);
            SkillFx.burst(level, front, glow, 0.55, 0.2);
            SkillFx.sparks(level, front, glow, 6, 3);
        }
    }

    /** 전투 망치: 맞힌 적 둘레의 다른 적 */
    static List<LivingEntity> quakeTargets(ServerPlayer sp, LivingEntity victim, Obj quake) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, victim.position(), quake.num("radius"), 1.5)) if (e != victim && Kit.splashFoe(sp, e)) out.add(e);
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(victim)));
        return out.size() > MAX_SPLASH ? new ArrayList<>(out.subList(0, MAX_SPLASH)) : out;
    }

    private static void quake(ServerPlayer sp, LivingEntity victim, boolean crit, double dealt, Obj quake) {
        ServerLevel level = sp.level();
        double amount = dealt * quake.num("ratio");
        Vec3 c = victim.position();
        for (LivingEntity e : quakeTargets(sp, victim, quake)) {
            if (!Kit.applySkillDamage(sp, e, amount, crit, Kit.Cause.ATTACK, crit ? DamageNumbers.Style.CRIT : DamageNumbers.Style.NORMAL)) continue;
            Vec3 d = V.flat(V.sub(e.position(), c));
            Kit.knockback(e, d.x * 0.2, d.z * 0.2, quake.num("lift"));
            SkillFx.fx(level, "rpg:dust", V.up(e.position(), 0.2), Opt.of("size", 0.7, "life", 0.5, "color", new double[] {0.62, 0.52, 0.4}, "alpha", 0.7));
        }
        if (crit) Fx.sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.8, 0.6);
    }

    /** 0.30.0 홀 흡혈이 넘침 (체력이 가득이라 다 못 받은 몫 — 사제 「신앙」: 둘레의 다친 아군에게): (홀을 든 사람, 넘친 양) */
    public static java.util.function.BiConsumer<ServerPlayer, Double> stealOverflow = (sp, extra) -> {};

    /** 성스러운 홀: 준 피해의 rate (한 번에 최대 체력 × cap 까지) — 실제로 회복한 양 */
    static double lifesteal(ServerPlayer sp, double dealt, Obj steal) {
        if (!sp.isAlive() || sp.getHealth() <= 0) return 0;
        double want = Math.min(dealt * steal.num("rate"), RpgHealth.maxOf(sp) * steal.num("cap"));
        double healed = RpgHealth.healBy(sp, want);
        if (healed >= 0.05) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
        if (want - healed >= 0.05) {
            try {
                stealOverflow.accept(sp, want - healed);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] steal overflow", e);
            }
        }
        return healed;
    }
}
