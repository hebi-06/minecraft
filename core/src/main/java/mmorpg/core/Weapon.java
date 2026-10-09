package mmorpg.core;

import java.util.List;

/**
 * A weapon as weapons.js {@code parseWeapon} describes it (all numbers derived from LEVEL_DAMAGE,
 * GRADES, TYPES, REQ and SHIELD; see {@link Weapons#parseWeapon}).
 *
 * @param id         normalized item id {@code rpg:<type>_l<level>_<grade>}
 * @param typeDef    TYPES[type]
 * @param stat       main stat (요구 · 피해 보너스)
 * @param req        main stat requirement ({@code reqs.get(0).value()})
 * @param reqs       all stat requirements, main stat first (도끼: 힘 + 체력)
 * @param scaling    stats that raise damage (damage multiplier = 1 + Σ stat × per)
 * @param title      design name (WEAPON_NAMES) or "<grade> <type>"
 * @param name       colored display name for dark backgrounds, {@code darkName} for light ones
 * @param typeLabel  type word on the first lore line (궁니르: "창")
 * @param look       alternative slash effect (궁니르: "spear") or null
 * @param legacy     a combination from older versions: still works, cannot be obtained anew
 * @param skills     the two SKILLS of a current-rule transcend weapon, else null
 * @param damage     base damage (one decimal)
 * @param critChance crit chance (capped at CRIT_CAP), {@code critMult}: crit multiplier (two decimals)
 * @param passive    shields only: damage reduction while held, {@code block}: reduction while blocking
 */
public record Weapon(
        String id,
        String type,
        int level,
        int gradeIndex,
        Grade grade,
        Obj typeDef,
        String stat,
        double req,
        List<Weapons.Req> reqs,
        List<Weapons.Scaling> scaling,
        String icon,
        String title,
        String name,
        String darkName,
        String typeLabel,
        String look,
        boolean legacy,
        List<Obj> skills,
        double damage,
        double critChance,
        double critMult,
        double passive,
        double block) {}
