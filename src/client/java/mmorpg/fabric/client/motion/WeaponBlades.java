package mmorpg.fabric.client.motion;

import java.util.Map;

/**
 * 0.40.0 참격 궤적: 근접 무기 디자인마다 손에 든 무기의 손잡이 · 무기 끝 (tools/weapon_blades.py 가 무기 모델에서 계산 — 손으로 고치지 않음).
 * <ul>
 *   <li>grip · tip: 3인칭 오른팔 공간 (자바 모델 공간 블록 — 팔 축 기준 · 팔 회전 전, x 왼쪽 · y 아래 · z 뒤)</li>
 *   <li>fpGrip · fpTip: 1인칭 카메라 공간 (블록 — 오른쪽 +x · 위 +y · 앞 -z), 오른손 · 동작 없음</li>
 * </ul>
 * 왼손은 x 를 뒤집음. 무기 ID 의 디자인은 종류 · 등급 (보통 = 종류_lv0, 나머지 = 종류_등급_lv0) — BladeTrails.designOf.
 */
public final class WeaponBlades {
    private WeaponBlades() {}

    public record Blade(float gripX, float gripY, float gripZ, float tipX, float tipY, float tipZ, float fpGripX, float fpGripY, float fpGripZ,
            float fpTipX, float fpTipY, float fpTipZ) {}

    /** 디자인 → 손잡이 · 무기 끝 */
    public static final Map<String, Blade> BY_DESIGN = Map.ofEntries(
            Map.entry("axe_hero_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03858F, 0.04949F, -0.88856F,
                    0.82563F, -0.61929F, -1.37518F, 0.71444F, 0.37053F, -1.62256F)),
            Map.entry("axe_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.01559F, 0.12821F, -0.84603F,
                    0.82563F, -0.61929F, -1.37518F, 0.67029F, 0.2905F, -1.60911F)),
            Map.entry("axe_noble_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.0625F, 0.11803F, -0.8224F,
                    0.82563F, -0.61929F, -1.37518F, 0.70775F, 0.28714F, -1.57108F)),
            Map.entry("axe_rare_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.0625F, 0.11634F, -0.84161F,
                    0.82563F, -0.61929F, -1.37518F, 0.71809F, 0.2961F, -1.61573F)),
            Map.entry("axe_skin_lava", new Blade(-0.0625F, 0.5625F, 0.0F, -0.0625F, 0.11803F, -0.8224F,
                    0.82563F, -0.61929F, -1.37518F, 0.70775F, 0.28714F, -1.57108F)),
            Map.entry("axe_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.09391F, -0.32592F, -1.53879F,
                    0.82563F, -0.61929F, -1.37518F, 0.70574F, 0.62755F, -1.63633F)),
            Map.entry("dagger_hero_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04912F, 0.35723F, -0.77239F,
                    0.70335F, -0.52216F, -1.12516F, 0.53919F, 0.17602F, -1.478F)),
            Map.entry("dagger_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.05048F, 0.21864F, -0.63405F,
                    0.70335F, -0.52216F, -1.12516F, 0.61319F, 0.17271F, -1.29672F)),
            Map.entry("dagger_noble_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04976F, 0.19794F, -0.67221F,
                    0.70335F, -0.52216F, -1.12516F, 0.60776F, 0.21454F, -1.30705F)),
            Map.entry("dagger_rare_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.05048F, 0.21864F, -0.63405F,
                    0.70335F, -0.52216F, -1.12516F, 0.61319F, 0.17271F, -1.29672F)),
            Map.entry("dagger_skin_obsidian", new Blade(-0.0625F, 0.5625F, 0.0F, -0.05048F, 0.21864F, -0.63405F,
                    0.70335F, -0.52216F, -1.12516F, 0.61319F, 0.17271F, -1.29672F)),
            Map.entry("dagger_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04789F, -0.3399F, -1.80317F,
                    0.70335F, -0.52216F, -1.12516F, 0.53573F, 0.72839F, -1.47997F)),
            Map.entry("greatsword_skin_abyss", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04075F, -0.92854F, -2.61985F,
                    0.82563F, -0.61929F, -1.37518F, 0.59574F, 1.42486F, -1.84568F)),
            Map.entry("greatsword_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04075F, -0.92854F, -2.61985F,
                    0.82563F, -0.61929F, -1.37518F, 0.59574F, 1.42486F, -1.84568F)),
            Map.entry("hammer_hero_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.01496F, -0.11739F, -1.27269F,
                    0.82563F, -0.61929F, -1.37518F, 0.68851F, 0.50894F, -1.58065F)),
            Map.entry("hammer_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.01852F, -0.02534F, -0.9302F,
                    0.82563F, -0.61929F, -1.37518F, 0.67573F, 0.3309F, -1.61397F)),
            Map.entry("hammer_noble_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.01616F, -0.04184F, -1.04675F,
                    0.82563F, -0.61929F, -1.37518F, 0.70189F, 0.42619F, -1.65221F)),
            Map.entry("hammer_rare_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.02939F, 0.00262F, -1.03595F,
                    0.82563F, -0.61929F, -1.37518F, 0.68133F, 0.40114F, -1.62414F)),
            Map.entry("hammer_skin_coral", new Blade(-0.0625F, 0.5625F, 0.0F, -0.01616F, -0.04184F, -1.04675F,
                    0.82563F, -0.61929F, -1.37518F, 0.70189F, 0.42619F, -1.65221F)),
            Map.entry("hammer_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.14567F, 0.15681F, -0.95301F,
                    0.82563F, -0.61929F, -1.37518F, 0.78089F, 0.10231F, -1.63047F)),
            Map.entry("lance_hero_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03508F, -0.73896F, -2.30904F,
                    0.82563F, -0.61929F, -1.37518F, 0.62136F, 1.12482F, -1.78172F)),
            Map.entry("lance_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04096F, -0.6642F, -2.1764F,
                    0.82563F, -0.61929F, -1.37518F, 0.63581F, 1.02467F, -1.75945F)),
            Map.entry("lance_noble_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03587F, -0.70561F, -2.24082F,
                    0.82563F, -0.61929F, -1.37518F, 0.62828F, 1.0757F, -1.76738F)),
            Map.entry("lance_rare_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04024F, -0.70523F, -2.24919F,
                    0.82563F, -0.61929F, -1.37518F, 0.62946F, 1.07965F, -1.77231F)),
            Map.entry("lance_skin_dusk", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03587F, -0.70561F, -2.24082F,
                    0.82563F, -0.61929F, -1.37518F, 0.62828F, 1.0757F, -1.76738F)),
            Map.entry("lance_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.05043F, -0.88065F, -2.51258F,
                    0.82563F, -0.61929F, -1.37518F, 0.62334F, 1.40403F, -1.84175F)),
            Map.entry("scepter_hero_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.0459F, -0.13069F, -1.74764F,
                    0.82563F, -0.61929F, -1.37518F, 0.88671F, 0.91971F, -1.80159F)),
            Map.entry("scepter_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.10561F, -0.3616F, -1.51438F,
                    0.82563F, -0.61929F, -1.37518F, 0.72242F, 0.85258F, -1.68857F)),
            Map.entry("scepter_noble_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.0523F, -0.39339F, -1.65565F,
                    0.82563F, -0.61929F, -1.37518F, 0.65979F, 0.95543F, -1.74056F)),
            Map.entry("scepter_rare_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.0625F, -0.27519F, -1.53974F,
                    0.82563F, -0.61929F, -1.37518F, 0.71137F, 0.82621F, -1.71776F)),
            Map.entry("scepter_skin_dawn", new Blade(-0.0625F, 0.5625F, 0.0F, -0.0523F, -0.39339F, -1.65565F,
                    0.82563F, -0.61929F, -1.37518F, 0.65979F, 0.95543F, -1.74056F)),
            Map.entry("scepter_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.02991F, -0.59058F, -2.06236F,
                    0.82563F, -0.61929F, -1.37518F, 0.63974F, 1.32282F, -1.85274F)),
            Map.entry("scythe_hero_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.039F, -0.44975F, -1.89428F,
                    0.82563F, -0.61929F, -1.37518F, 0.65329F, 0.87888F, -1.71413F)),
            Map.entry("scythe_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03993F, -0.03111F, -1.91589F,
                    0.82563F, -0.61929F, -1.37518F, 0.66191F, 0.74185F, -1.69863F)),
            Map.entry("scythe_noble_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03802F, -0.55054F, -1.9768F,
                    0.82563F, -0.61929F, -1.37518F, 0.63957F, 0.96111F, -1.74399F)),
            Map.entry("scythe_rare_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03052F, 0.00967F, -2.01086F,
                    0.82563F, -0.61929F, -1.37518F, 0.52326F, 0.69865F, -2.02843F)),
            Map.entry("scythe_skin_wraith", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03802F, -0.55054F, -1.9768F,
                    0.82563F, -0.61929F, -1.37518F, 0.63957F, 0.96111F, -1.74399F)),
            Map.entry("scythe_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, 0.01656F, -0.94361F, -1.98751F,
                    0.82563F, -0.61929F, -1.37518F, 0.78027F, 1.16401F, -1.58667F)),
            Map.entry("sword_hero_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04641F, -0.23652F, -1.45911F,
                    0.82563F, -0.61929F, -1.37518F, 0.63087F, 0.98526F, -1.76913F)),
            Map.entry("sword_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04994F, -0.06449F, -1.1362F,
                    0.82563F, -0.61929F, -1.37518F, 0.6752F, 0.63349F, -1.67872F)),
            Map.entry("sword_noble_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04955F, -0.08224F, -1.17278F,
                    0.82563F, -0.61929F, -1.37518F, 0.69377F, 0.67243F, -1.69975F)),
            Map.entry("sword_rare_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04999F, -0.10538F, -1.10677F,
                    0.82563F, -0.61929F, -1.37518F, 0.67578F, 0.62862F, -1.67754F)),
            Map.entry("sword_skin_sakura", new Blade(-0.0625F, 0.5625F, 0.0F, -0.04955F, -0.08224F, -1.17278F,
                    0.82563F, -0.61929F, -1.37518F, 0.69377F, 0.67243F, -1.69975F)),
            Map.entry("sword_transcend_lv0", new Blade(-0.0625F, 0.5625F, 0.0F, -0.03892F, -0.61723F, -2.09642F,
                    0.82563F, -0.61929F, -1.37518F, 0.60802F, 1.24262F, -1.81082F)));

    /** 무기 스킨 (장식 ID — cosmetics.json) → 디자인 */
    public static final Map<String, String> SKIN_DESIGN = Map.ofEntries(
            Map.entry("abyss_greatsword", "greatsword_skin_abyss"),
            Map.entry("coral_hammer", "hammer_skin_coral"),
            Map.entry("dawn_scepter", "scepter_skin_dawn"),
            Map.entry("dusk_lance", "lance_skin_dusk"),
            Map.entry("lava_axe", "axe_skin_lava"),
            Map.entry("obsidian_dagger", "dagger_skin_obsidian"),
            Map.entry("sakura_sword", "sword_skin_sakura"),
            Map.entry("wraith_scythe", "scythe_skin_wraith"));
}
