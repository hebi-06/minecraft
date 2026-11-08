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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 2026-11-08 총 스펙 · 총 전투력 (사용자 "총 스펙 표기할때 잘 만 표시해주고 공격력으로 바꾸고 … 총전투력을 만들어줘 해당 전투력은 그냥 보여주기용임
 * 랭킹에 뜰거임"): 플레이어에게서 core.CombatPower 의 값을 모음. 보여주기 · 순위용 — 피해 · 판정에는 쓰지 않음.
 * <ul>
 * <li>스탯: 찍은 스탯 + 갑옷 보너스. 잠깐 걸리는 룬 버프 (신체 강화 — RpgStats.buffOf) 는 뺌 (순위가 버프로 흔들리지 않게)</li>
 * <li>스탯 트리 · 아티팩트 (상한 적용 — StatSheet.tree): 공격력 % · 치명타 확률 · 치명타 피해 · 최대 체력 · 받는 피해 · 룬 피해 · 재사용 대기 · 이동 속도</li>
 * <li>기준 무기: 가진 무기 (양손 · 가방) 중 지금 쓸 수 있는 것에서 전투력이 가장 높게 나오는 것 (강화는 그 아이템의 단계) — 든 것을 바꿔도 그대로.
 *     방패면 방패 상시 감소 (RpgCombat.shieldReduction 과 같은 식 — 오른손 방패)</li>
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
     * @param weapon       기준 무기 (없으면 null)
     * @param enh          기준 무기의 강화 단계
     * @param weaponAttack 무기 공격력 (강화 포함)
     * @param statAttack   스탯이 더한 공격력
     * @param atkPct       스탯 트리 · 아티팩트 공격력 %
     * @param stats        스탯 시트 (룬 버프 없이 — 최대 체력 · 스탯 트리 값)
     */
    public record Sheet(long cp, CombatPower.Spec spec, Weapon weapon, int enh, double weaponAttack, double statAttack, double atkPct, StatSheet stats) {}

    /** 이 플레이어의 총 스펙 (기준 무기 = 전투력이 가장 높게 나오는 것) */
    public static Sheet of(ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        StatSheet s = StatSheet.of(d.str(), d.dex(), d.intel(), d.vit(), d.point(), d.lvmax(), sp.experienceLevel, RpgStats.bonusOf(sp), 0)
                .withTree(mmorpg.fabric.stattree.StatTrees.allTotals(sp)).withJob(mmorpg.fabric.job.JobSystem.jobOf(sp));
        double armor = sp.getAttributeValue(Attributes.ARMOR);
        Sheet best = sheet(s, null, 0, armor);
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(sp.getMainHandItem());
        stacks.add(sp.getOffhandItem());
        for (ItemStack st : sp.getInventory().getNonEquipmentItems()) stacks.add(st);
        Set<String> seen = new HashSet<>();
        for (ItemStack st : stacks) {
            Weapon w = RpgCombat.weaponOf(st);
            if (w == null || !s.usable(w)) continue;
            int enh = mmorpg.fabric.gear.WeaponEnhance.of(st);
            if (!seen.add(w.id() + "+" + enh)) continue;
            Sheet c = sheet(s, w, enh, armor);
            if (c.cp() > best.cp()) best = c;
        }
        return best;
    }

    /** 무기 w (강화 enh — null 이면 맨손) 를 기준으로 한 총 스펙 */
    static Sheet sheet(StatSheet s, Weapon w, int enh, double armor) {
        double atkPct = s.tree("atk");
        double weaponAttack = StatSheet.baseAttack(w, enh);
        double statAttack = w == null ? 0 : weaponAttack * s.scaleSum(w, s.eff());
        double critChance = w == null ? 0 : Combat.critChanceOf(w, s.tree("crit") / 100);
        double critMult = w == null ? 1 : w.critMult() + s.tree("critDmg") / 100;
        double shield = w != null && w.type().equals("shield")
                ? Math.min(SHIELD.num("passiveCap"), w.passive() + Stats.get(s.eff(), "vit") * SHIELD.num("passivePerVit")) : 0;
        double attack = (weaponAttack + statAttack) * (1 + atkPct / 100);
        double reduction = CombatPower.combineReduction(s.tree("def") / 100, shield);
        CombatPower.Spec spec = new CombatPower.Spec(attack, critChance, critMult, s.tree("rune"), s.tree("cdr"), s.maxHp(), reduction, armor, s.tree("speed"));
        return new Sheet(CombatPower.of(spec), spec, w, enh, weaponAttack, statAttack, atkPct, s);
    }
}
