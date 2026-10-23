package mmorpg.fabric.client;

import java.util.HashMap;
import java.util.Map;
import mmorpg.core.Armor;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.RpgArmorItem;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 갑옷 입은 모습 (애드온 art/armor 의 3D 모델 — 순찰대 · 강철 기사 · 흑요석 수호자 · 적룡 · 황제, 0.36.0 공허 — tools/void_armor.py): tools/armor_items.py 가 베드락 모델을
 * 자바 사람 모델 좌표로 바꾼 것 (assets/mmorpg/armor/&lt;세트&gt;_&lt;부위&gt;.json) 을 읽어 모델 ({@link PieceModel}) 을 만들고, Fabric ArmorRenderer 로
 * 입은 사람의 머리 · 몸 · 팔 · 다리 움직임을 그대로 따라 그림 (바닐라 갑옷 층 대신). 황제 망토는 걷는 빠르기만큼 뒤로 날림 (fw_cape).
 * 장식 창에서 숨기거나 갑옷 스킨을 입으면 그 부위는 그리지 않음 (CosmeticRender — 렌더 상태의 장비 칸을 비움).
 */
public final class ArmorModels {
    private ArmorModels() {}

    private static final String[] PIECES = {"helmet", "chest", "legs", "boots"};
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Map<String, PieceModel> MODELS = new HashMap<>();

    /** 세트 · 부위의 모델 (없으면 null) */
    static PieceModel get(String set, String piece) {
        return MODELS.get(set + "_" + piece);
    }

    /** 클라이언트 시작: 모델을 모두 읽고, RPG 갑옷 240 에 그리기를 맡김 */
    static void init() {
        for (RpgArmorItem item : GearItems.armor()) {
            Armor.Piece a = item.piece();
            String key = a.set() + "_" + a.piece();
            if (MODELS.containsKey(key)) continue;
            PieceModel m = PieceModel.load("/assets/mmorpg/armor/" + key + ".json");
            if (m != null) MODELS.put(key, m);
        }
        ArmorRenderer renderer = new ArmorRenderer() {
            @Override
            public void render(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, ItemStack stack,
                    HumanoidRenderState state, net.minecraft.world.entity.EquipmentSlot slot, int light,
                    net.minecraft.client.model.HumanoidModel<HumanoidRenderState> contextModel) {
                if (!(stack.getItem() instanceof RpgArmorItem item)) return;
                PieceModel m = get(item.piece().set(), item.piece().piece());
                if (m == null) return;
                if (m.emissive == null) {
                    ArmorRenderer.submitTransformCopyingModel(contextModel, state, m, state, true, collector, poseStack, RenderTypes.armorCutoutNoCull(m.texture), light,
                            OverlayTexture.NO_OVERLAY, state.outlineColor);
                    return;
                }
                // 0.36.0 공허의 갑옷 (빛나는 그림 emissive — 분홍 핏줄 · 수정 끝, 어두워도 보임): 보스 (BedrockRenderer — 몸 entityCutout + 빛 GlowLayer) 와 같은 짝.
                // 바탕을 armorCutoutNoCull 로 그리면 빛 그림 (eyes · entityTranslucentEmissive 모두) 보다 뒤에 그려져 덮음 — 사진첩 밤 사진 두 번 (0.36.0)
                ArmorRenderer.submitTransformCopyingModel(contextModel, state, m, state, true, collector, poseStack, RenderTypes.entityCutout(m.texture), light,
                        OverlayTexture.NO_OVERLAY, state.outlineColor);
                ArmorRenderer.submitTransformCopyingModel(contextModel, state, m, state, true, collector, poseStack, RenderTypes.entityTranslucentEmissive(m.emissive),
                        FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor);
            }

            /** 투구 칸의 아이템 모양 (평면 아이콘) 을 머리에 또 그리지 않음 */
            @Override
            public boolean shouldRenderDefaultHeadItem(LivingEntity entity, ItemStack stack) {
                return false;
            }
        };
        ArmorRenderer.register(renderer, GearItems.armor().toArray(new RpgArmorItem[0]));
        Mmorpg.LOG.info("MMO RPG: armor models {}", MODELS.size());
    }

    /** 시험용: 읽은 모델 수 (세트 6 × 부위 4 = 24 — 0.36.0 공허) */
    public static int count() {
        return MODELS.size();
    }

    static String[] pieces() {
        return PIECES;
    }
}
