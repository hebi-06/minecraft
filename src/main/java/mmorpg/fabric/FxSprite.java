package mmorpg.fabric;

/**
 * 일반 그림 파티클 (애드온 RPG_RP/particles/rpg 의 같은 이름) — tools/fx_textures.py 가 만듦, 손으로 고치지 않음.
 * 그림 assets/mmorpg/textures/particle/fx/<texture>[_<장>].png (정사각형으로 늘린 것).
 * 반 크기 = size × scale × (1 + grow × 진행), 움직임 = 처음 속도 × 끌림 (초당 감속 계수), 여러 장이면 수명 동안 차례로.
 */
public enum FxSprite {
    SLASH_ARC_R_GLOW("slash_arc_r_glow", 5, 1.0616F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_ARC_R_CORE("slash_arc_r_core", 5, 1.0616F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_ARC_R_AURA("slash_arc_r_aura", 5, 1.0616F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_ARC_L_GLOW("slash_arc_l_glow", 5, 1.0616F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_ARC_L_CORE("slash_arc_l_core", 5, 1.0616F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_ARC_L_AURA("slash_arc_l_aura", 5, 1.0616F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_THIN_R_GLOW("slash_thin_r_glow", 5, 0.5619F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_THIN_R_CORE("slash_thin_r_core", 5, 0.5619F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_THIN_R_AURA("slash_thin_r_aura", 5, 0.5619F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_THIN_L_GLOW("slash_thin_l_glow", 5, 0.5619F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_THIN_L_CORE("slash_thin_l_core", 5, 0.5619F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_THIN_L_AURA("slash_thin_l_aura", 5, 0.5619F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_HEAVY_R_GLOW("slash_heavy_r_glow", 5, 1.2198F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_HEAVY_R_CORE("slash_heavy_r_core", 5, 1.2198F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_HEAVY_R_AURA("slash_heavy_r_aura", 5, 1.2198F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_HEAVY_L_GLOW("slash_heavy_l_glow", 5, 1.2198F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_HEAVY_L_CORE("slash_heavy_l_core", 5, 1.2198F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_HEAVY_L_AURA("slash_heavy_l_aura", 5, 1.2198F, 0.25F, 5.0F, Fade.NONE, Facing.CAMERA),
    SLASH_X("slash_x", 1, 1.0F, 0.35F, 8.0F, Fade.SQUARE, Facing.CAMERA),
    SHOCK_RING("shock_ring", 1, 1.0F, 1.6F, 8.0F, Fade.SQUARE, Facing.FLAT),
    BASH_RING("shock_ring", 1, 1.0F, 1.4F, 8.0F, Fade.SQUARE, Facing.CAMERA),
    BASH_BURST("bash_burst", 1, 1.0F, 0.5F, 8.0F, Fade.SQUARE, Facing.CAMERA);

    public enum Fade { NONE, SQUARE }

    public enum Facing { CAMERA, FLAT }

    public final String texture;
    public final int frames;
    public final float scale;
    public final float grow;
    public final float drag;
    public final Fade fade;
    public final Facing facing;

    FxSprite(String texture, int frames, float scale, float grow, float drag, Fade fade, Facing facing) {
        this.texture = texture;
        this.frames = frames;
        this.scale = scale;
        this.grow = grow;
        this.drag = drag;
        this.fade = fade;
        this.facing = facing;
    }

    private static final FxSprite[] VALUES = values();

    public static FxSprite byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
