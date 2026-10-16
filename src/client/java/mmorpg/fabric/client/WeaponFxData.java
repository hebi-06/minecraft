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

    private static final Spec AXE_HERO_LV0 = new Spec("axe_hero_lv0", 0.76078F, 0.00979F, -1.51786F, -0.0625F, 0.23834F, -0.56146F,
            10.0F, 0.188F, 0.15F, 1.6F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFF7417});
    private static final Spec AXE_TRANSCEND_LV0 = new Spec("axe_transcend_lv0", 0.555F, 0.13251F, -2.02861F, -0.0625F, 0.54431F, -1.43347F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xC2FFD4});
    private static final Spec BOW_HERO_LV0 = new Spec("bow_hero_lv0", 1.35518F, -0.35687F, -1.88493F, -0.0625F, 0.74818F, -0.02609F,
            10.0F, 0.313F, 0.15F, -0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x53FFBC});
    private static final Spec BOW_TRANSCEND_LV0 = new Spec("bow_transcend_lv0", 1.25861F, -0.33062F, -1.88168F, -0.0625F, 0.66992F, -0.02155F,
            10.0F, 0.45F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xA0C1FF});
    private static final Spec DAGGER_HERO_LV0 = new Spec("dagger_hero_lv0", 0.6388F, -0.03125F, -1.27137F, -0.0625F, 0.33658F, -0.46421F,
            10.0F, 0.156F, 0.15F, 0.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xB97AFF});
    private static final Spec DAGGER_TRANSCEND_LV0 = new Spec("dagger_transcend_lv0", 0.62613F, 0.09369F, -1.29918F, -0.0625F, 0.11525F, -0.88511F,
            10.0F, 0.375F, 0.15F, 0.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFFCC59});
    private static final Spec GREATSWORD_TRANSCEND_LV0 = new Spec("greatsword_transcend_lv0", 0.72335F, 0.37279F, -1.60019F, -0.0625F, -0.16779F, -1.2649F,
            10.0F, 0.42F, 0.15F, 0.7F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x9ED7FF});
    private static final Spec HAMMER_HERO_LV0 = new Spec("hammer_hero_lv0", 0.71276F, 0.47558F, -1.6235F, -0.0625F, -0.14271F, -1.22146F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x47FF7E});
    private static final Spec HAMMER_TRANSCEND_LV0 = new Spec("hammer_transcend_lv0", 0.74863F, 0.03663F, -1.6115F, -0.09466F, 0.18847F, -0.87037F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x7AE0FF});
    private static final Spec LANCE_HERO_LV0 = new Spec("lance_hero_lv0", 0.6814F, 0.77978F, -1.6925F, -0.0625F, -0.49767F, -1.83627F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFF1E31});
    private static final Spec LANCE_TRANSCEND_LV0 = new Spec("lance_transcend_lv0", 0.67319F, 0.92929F, -1.70839F, -0.0625F, -0.56517F, -1.90087F,
            10.0F, 0.156F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x51D3FF});
    private static final Spec SCEPTER_HERO_LV0 = new Spec("scepter_hero_lv0", 0.6828F, 0.76616F, -1.68941F, -0.0625F, -0.27738F, -1.45472F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFFB22F});
    private static final Spec SCEPTER_TRANSCEND_LV0 = new Spec("scepter_transcend_lv0", 0.66215F, 0.9665F, -1.73485F, -0.0625F, -0.39883F, -1.66507F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFFC85B});
    private static final Spec SCYTHE_HERO_LV0 = new Spec("scythe_hero_lv0", 0.62144F, 0.72046F, -1.84425F, -0.0625F, -0.21848F, -1.83231F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xB093FF});
    private static final Spec SCYTHE_TRANSCEND_LV0 = new Spec("scythe_transcend_lv0", 0.56476F, 0.73737F, -1.98546F, -0.0625F, -0.0827F, -1.99596F,
            10.0F, 0.18F, 0.15F, 1.4F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xAF6DFF});
    private static final Spec SHIELD_HERO_LV0 = new Spec("shield_hero_lv0", 0.87549F, -0.18702F, -1.0108F, -0.2696F, 0.26738F, 0.0F,
            10.0F, 0.188F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFFB22F});
    private static final Spec SHIELD_TRANSCEND_LV0 = new Spec("shield_transcend_lv0", 0.88779F, -0.4308F, -0.97241F, -0.28356F, 0.51408F, 0.0F,
            10.0F, 0.297F, 0.15F, 0.2F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x35ADFF});
    private static final Spec STAFF_HERO_LV0 = new Spec("staff_hero_lv0", 1.06049F, 0.28906F, -1.74991F, -0.11197F, -0.34573F, -0.65288F,
            10.0F, 0.094F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x60FFAC});
    private static final Spec STAFF_TRANSCEND_LV0 = new Spec("staff_transcend_lv0", 0.87068F, 0.94366F, -1.89579F, -0.0625F, -1.27069F, -1.05839F,
            10.0F, 0.188F, 0.15F, 0.8F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0xFFF0C1});
    private static final Spec SWORD_HERO_LV0 = new Spec("sword_hero_lv0", 0.73654F, 0.24487F, -1.57118F, -0.0625F, 0.11721F, -0.77126F,
            10.0F, 0.25F, 0.15F, 1.6F, 1.0F, 0.9F, 0.05F, "generic_0", new float[] {}, new int[] {0x66DBFF});
    private static final Spec SWORD_TRANSCEND_LV0 = new Spec("sword_transcend_lv0", 0.69777F, 0.62097F, -1.65648F, -0.0625F, -0.23636F, -1.38366F,
            10.0F, 0.344F, 0.15F, 2.4F, 1.0F, 0.9F, 0.05F, "flame", new float[] {}, new int[] {0xFF463A});

    /** 무기 ID (rpg: 뒤) → 빛 알갱이 */
    public static final Map<String, Spec> BY_ITEM = Map.ofEntries(
            Map.entry("axe_l30_hero", AXE_HERO_LV0),
            Map.entry("axe_l35_hero", AXE_HERO_LV0),
            Map.entry("axe_l40_hero", AXE_HERO_LV0),
            Map.entry("axe_l40_transcend", AXE_TRANSCEND_LV0),
            Map.entry("bow_l30_hero", BOW_HERO_LV0),
            Map.entry("bow_l35_hero", BOW_HERO_LV0),
            Map.entry("bow_l40_hero", BOW_HERO_LV0),
            Map.entry("bow_l40_transcend", BOW_TRANSCEND_LV0),
            Map.entry("dagger_l30_hero", DAGGER_HERO_LV0),
            Map.entry("dagger_l35_hero", DAGGER_HERO_LV0),
            Map.entry("dagger_l40_hero", DAGGER_HERO_LV0),
            Map.entry("dagger_l40_transcend", DAGGER_TRANSCEND_LV0),
            Map.entry("greatsword_l40_transcend", GREATSWORD_TRANSCEND_LV0),
            Map.entry("hammer_l30_hero", HAMMER_HERO_LV0),
            Map.entry("hammer_l35_hero", HAMMER_HERO_LV0),
            Map.entry("hammer_l40_hero", HAMMER_HERO_LV0),
            Map.entry("hammer_l40_transcend", HAMMER_TRANSCEND_LV0),
            Map.entry("lance_l30_hero", LANCE_HERO_LV0),
            Map.entry("lance_l35_hero", LANCE_HERO_LV0),
            Map.entry("lance_l40_hero", LANCE_HERO_LV0),
            Map.entry("lance_l40_transcend", LANCE_TRANSCEND_LV0),
            Map.entry("scepter_l30_hero", SCEPTER_HERO_LV0),
            Map.entry("scepter_l35_hero", SCEPTER_HERO_LV0),
            Map.entry("scepter_l40_hero", SCEPTER_HERO_LV0),
            Map.entry("scepter_l40_transcend", SCEPTER_TRANSCEND_LV0),
            Map.entry("scythe_l30_hero", SCYTHE_HERO_LV0),
            Map.entry("scythe_l35_hero", SCYTHE_HERO_LV0),
            Map.entry("scythe_l40_hero", SCYTHE_HERO_LV0),
            Map.entry("scythe_l40_transcend", SCYTHE_TRANSCEND_LV0),
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
