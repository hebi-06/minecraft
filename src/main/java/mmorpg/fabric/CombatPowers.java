package mmorpg.fabric;

import mmorpg.core.Combat;
import mmorpg.core.CombatPower;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * 2026-11-08 총 스펙 · 총 전투력 (사용자 "총 스펙 표기할때 잘 만 표시해주고 공격력으로 바꾸고 … 총전투력을 만들어줘 해당 전투력은 그냥 보여주기용임
 * 랭킹에 뜰거임"): 플레이어에게서 core.CombatPower 의 값을 모음. 보여주기 · 순위용 — 피해 · 판정에는 쓰지 않음.
 * <ul>
 * <li>스탯: 찍은 스탯 + 갑옷 보너스. 잠깐 걸리는 룬 버프 (신체 강화 — RpgStats.buffOf) 는 뺌 (순위가 버프로 흔들리지 않게)</li>
 * <li>스탯 트리 · 아티팩트 (상한 적용 — StatSheet.tree): 공격력 % · 치명타 확률 · 치명타 피해 · 최대 체력 · 받는 피해 · 룬 피해 · 재사용 대기 · 이동 속도</li>
 * <li>지금 낀 것만 (사용자 "무기를 지금 낀걸로 해야지 갑옷도 악세도" — 뒤에 갑옷 · 장신구에 부옵션 · 갑옷 여러 벌이 들어올 예정): 무기 = 오른손에 든 RPG 무기
 *     (강화는 그 아이템의 단계 · 요구 조건이 모자라면 게임처럼 공격력 0), 갑옷 = 입은 것 (보너스 스탯 · 방어 점수), 장신구 = 낀 아티팩트 (스탯 트리 합에 들어감).
 *     방패를 들면 방패 상시 감소 (RpgCombat.shieldReduction 과 같은 식 — 오른손 방패)</li>
 * <li>방어 = 입은 갑옷의 방어 점수 (바닐라 ARMOR — RPG 갑옷이 줌)</li>
 * </ul>
 */
public final class CombatPowers {
    private CombatPowers() {}

    private static final Obj SHIELD = Rules.get().obj("SHIELD");

    /**
     * 총 스펙.
     *
     * @param cp           전투력
     * @param spec         전투력을 셀 값 (공격력 · 치명타 · 최대 체력 · 감소 · 방어 …)
     * @param weapon       든 RPG 무기 (없으면 null)
     * @param enh          든 무기의 강화 단계
     * @param usable       든 무기를 지금 쓸 수 있는지 (레벨 · 요구 스탯 — 모자라면 공격력 0)
     * @param weaponAttack 무기 공격력 (강화 포함)
     * @param statAttack   스탯이 더한 공격력
     * @param atkPct       스탯 트리 · 아티팩트 공격력 %
     * @param stats        스탯 시트 (룬 버프 없이 — 최대 체력 · 스탯 트리 값)
     */
    public record Sheet(long cp, CombatPower.Spec spec, Weapon weapon, int enh, boolean usable, double weaponAttack, double statAttack, double atkPct,
                        StatSheet stats) {}

    /** 이 플레이어의 총 스펙 (지금 든 무기 · 입은 갑옷 · 낀 아티팩트) */
    public static Sheet of(ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        StatSheet s = StatSheet.of(d.str(), d.dex(), d.intel(), d.vit(), d.point(), d.lvmax(), sp.experienceLevel, RpgStats.bonusOf(sp), 0)
                .withTree(mmorpg.fabric.stattree.StatTrees.allTotals(sp)).withJob(mmorpg.fabric.job.JobSystem.jobOf(sp));
        ItemStack main = sp.getMainHandItem();
        Weapon w = RpgCombat.weaponOf(main);
        return sheet(s, w, w == null ? 0 : mmorpg.fabric.gear.WeaponEnhance.of(main), sp.getAttributeValue(Attributes.ARMOR));
    }

    /** 무기 w (강화 enh — null 이면 맨손) 를 든 총 스펙 (요구 조건이 모자라면 공격력 0 — 게임의 "지금은 피해 0" 과 같음) */
    static Sheet sheet(StatSheet s, Weapon w, int enh, double armor) {
        boolean usable = w != null && s.usable(w);
        double atkPct = s.tree("atk");
        double weaponAttack = StatSheet.baseAttack(w, enh);
        double statAttack = w == null ? 0 : weaponAttack * s.scaleSum(w, s.eff());
        double critChance = usable ? Combat.critChanceOf(w, s.tree("crit") / 100) : 0;
        double critMult = usable ? w.critMult() + s.tree("critDmg") / 100 : 1;
        double shield = usable && w.type().equals("shield")
                ? Math.min(SHIELD.num("passiveCap"), w.passive() + Stats.get(s.eff(), "vit") * SHIELD.num("passivePerVit")) : 0;
        double attack = usable ? (weaponAttack + statAttack) * (1 + atkPct / 100) : 0;
        double reduction = CombatPower.combineReduction(s.tree("def") / 100, shield);
        CombatPower.Spec spec = new CombatPower.Spec(attack, critChance, critMult, s.tree("rune"), s.tree("cdr"), s.maxHp(), reduction, armor, s.tree("speed"));
        // 2026-11-14 민첩 = 공속: 전투력의 공격에 공속을 반만 (평타가 공격의 일부 — ATTACK_SPEED.cpWeight). 보이는 공격력 (spec) 은 그대로
        double spd = 1 + (mmorpg.core.AttackSpeed.speed(s.eff()) - 1) * mmorpg.core.AttackSpeed.cpWeight();
        CombatPower.Spec forCp = new CombatPower.Spec(attack * spd, critChance, critMult, s.tree("rune"), s.tree("cdr"), s.maxHp(), reduction, armor, s.tree("speed"));
        return new Sheet(CombatPower.of(forCp), spec, w, enh, usable, weaponAttack, statAttack, atkPct, s);
    }
}
