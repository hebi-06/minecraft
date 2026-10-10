package mmorpg.fabric.client;

import java.util.Map;

/**
 * 무기 빛 알갱이 (애드온 영웅 · 초월 무기 attachable 의 fx 파티클 — RPG_RP/particles/rpg/weapon_fx) — tools/weapon_models.py 가 만듦, 손으로 고치지 않음.
 * 무기를 들고 있는 동안 무기의 fx 자리에서 초당 rate 개: 반지름 radius 공 안 아무 곳에서 바깥쪽으로 speed, 위로 accel 가속 · drag 끌림,
 * 반 크기 size 에서 수명 life 동안 줄어들어 사라짐. 색은 무작위 r 이 cuts[i] 보다 작으면 colors[i] (없으면 마지막 색).
 *
 * @param fpX  1인칭 fx 자리: 카메라 공간 (블록 — 오른쪽 +x · 위 +y · 앞 -z), 오른손 · 휘두르기 없음 · 손 시야 70
 * @param armX 3인칭 fx 자리: 오른팔 공간 (자바 모델 공간 블록 — 팔 축 기준 · 팔 회전 전, x 왼쪽 · y 아래 · z 뒤)
 */
public final class WeaponFxData {
    private WeaponFxData() {}

    public record Spec(String design, float fpX, float fpY, float fpZ, float armX, float armY, float armZ, float rate, float radius, float speed,
            float accel, float drag, float life, float size, String texture, float[] cuts, int[] colors) {}

    private static final Spec AXE_HERO_LV0 = new Spec("axe_hero_lv0", 0.7506F, 0.10847F, -1.54024F, -0.0625F, 0.1875F, -0.64952F,
            10.0F, 0.188F, 0.15F, 1.6F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFF8A2A});
    private static final Spec BOW_HERO_LV0 = new Spec("bow_hero_lv0", 1.29425F, -0.34334F, -1.88163F, -0.0625F, 0.68628F, -0.0174F,
            10.0F, 0.313F, 0.15F, -0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x6DFF8E});
    private static final Spec BOW_TRANSCEND_LV0 = new Spec("bow_transcend_lv0", 1.26384F, -0.33639F, -1.87979F, -0.06281F, 0.67853F, -0.01643F,
            10.0F, 0.45F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xC7E6FF});
    private static final Spec DAGGER_HERO_LV0 = new Spec("dagger_hero_lv0", 0.68209F, -0.0368F, -1.24744F, -0.0625F, 0.33956F, -0.44864F,
            10.0F, 0.156F, 0.15F, 0.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x9B4DFF});
    private static final Spec DAGGER_TRANSCEND_LV0 = new Spec("dagger_transcend_lv0", 0.53269F, 0.22621F, -1.25525F, -0.0625F, -0.16657F, -0.95028F,
            10.0F, 0.375F, 0.15F, 0.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x8A4DD6});
    private static final Spec GREATSWORD_TRANSCEND_LV0 = new Spec("greatsword_transcend_lv0", 0.72059F, 0.39957F, -1.60626F, -0.0625F, -0.1875F, -1.29904F,
            10.0F, 0.42F, 0.15F, 0.7F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {0.4F, 0.6F}, new int[] {0x87CCFF, 0x9E5CE6, 0xF5948C});
    private static final Spec SHIELD_HERO_LV0 = new Spec("shield_hero_lv0", 0.86253F, -0.55214F, -1.05695F, -0.1875F, 0.625F, 0.03125F,
            10.0F, 0.188F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFFE27A});
    private static final Spec SHIELD_TRANSCEND_LV0 = new Spec("shield_transcend_lv0", 0.89167F, -0.50711F, -0.9357F, -0.3125F, 0.59375F, 0.0F,
            10.0F, 0.297F, 0.15F, 0.2F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x7FF5E0});
    private static final Spec SPEAR_TRANSCEND_LV0 = new Spec("spear_transcend_lv0", 0.66357F, 0.95266F, -1.73171F, -0.0625F, -0.5625F, -1.94856F,
            10.0F, 0.156F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x8FD4FF});
    private static final Spec STAFF_HERO_LV0 = new Spec("staff_hero_lv0", 0.9475F, 0.19842F, -1.72677F, -0.0625F, -0.33059F, -0.51562F,
            10.0F, 0.094F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x5DFFB0});
    private static final Spec STAFF_TRANSCEND_LV0 = new Spec("staff_transcend_lv0", 0.86732F, 0.97621F, -1.90317F, -0.0625F, -1.30487F, -1.07812F,
            10.0F, 0.188F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFFD66B});
    private static final Spec SWORD_HERO_LV0 = new Spec("sword_hero_lv0", 0.74435F, 0.16911F, -1.554F, -0.0625F, 0.15625F, -0.70365F,
            10.0F, 0.25F, 0.15F, 1.6F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x7FD4FF});
    private static final Spec SWORD_TRANSCEND_LV0 = new Spec("sword_transcend_lv0", 0.71059F, 0.4966F, -1.62827F, -0.0625F, -0.15625F, -1.24491F,
            10.0F, 0.344F, 0.15F, 2.4F, 1.0F, 0.9F, 0.05F, "flame", new float[] {}, new int[] {0xFF5A1A});

    /** 무기 ID (rpg: 뒤) → 빛 알갱이 */
    public static final Map<String, Spec> BY_ITEM = Map.ofEntries(
            Map.entry("axe_l30_hero", AXE_HERO_LV0),
            Map.entry("axe_l35_hero", AXE_HERO_LV0),
            Map.entry("axe_l40_hero", AXE_HERO_LV0),
            Map.entry("axe_l40_transcend", SPEAR_TRANSCEND_LV0),
            Map.entry("bow_l30_hero", BOW_HERO_LV0),
            Map.entry("bow_l35_hero", BOW_HERO_LV0),
            Map.entry("bow_l40_hero", BOW_HERO_LV0),
            Map.entry("bow_l40_transcend", BOW_TRANSCEND_LV0),
            Map.entry("dagger_l30_hero", DAGGER_HERO_LV0),
            Map.entry("dagger_l35_hero", DAGGER_HERO_LV0),
            Map.entry("dagger_l40_hero", DAGGER_HERO_LV0),
            Map.entry("dagger_l40_transcend", DAGGER_TRANSCEND_LV0),
            Map.entry("greatsword_l40_transcend", GREATSWORD_TRANSCEND_LV0),
            Map.entry("shield_l30_hero", SHIELD_HERO_LV0),
            Map.entry("shield_l35_hero", SHIELD_HERO_LV0),
            Map.entry("shield_l40_hero", SHIELD_HERO_LV0),
            Map.entry("shield_l40_transcend", SHIELD_TRANSCEND_LV0),
            Map.entry("sword_l30_hero", SWORD_HERO_LV0),
            Map.entry("sword_l35_hero", SWORD_HERO_LV0),
            Map.entry("sword_l40_hero", SWORD_HERO_LV0),
            Map.entry("sword_l40_transcend", SWORD_TRANSCEND_LV0),
            Map.entry("wand_l30_hero", STAFF_HERO_LV0),
            Map.entry("wand_l35_hero", STAFF_HERO_LV0),
            Map.entry("wand_l40_hero", STAFF_HERO_LV0),
            Map.entry("wand_l40_transcend", STAFF_TRANSCEND_LV0));
}
