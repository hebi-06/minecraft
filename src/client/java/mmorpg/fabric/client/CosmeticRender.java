package mmorpg.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.cosmetic.CosmeticDefs;
import mmorpg.fabric.cosmetic.Look;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 장식 그리기 도우미 (믹스인이 부름 — 0.13.0):
 * <ul>
 * <li>무기 스킨 ({@link #skinned}): 사람 (플레이어 · 마네킹) 이 손에 든 RPG 무기를 그릴 때 (1인칭 · 3인칭 손 — ItemModelResolver.updateForLiving),
 * 그 무기 종류의 스킨이 있으면 아이템 모델만 rpg:cosmetic_&lt;스킨&gt; 으로 바꾼 복사본을 그림. 가방 · 핫바 아이콘 (GUI) · 땅에 떨어진 것은 그대로</li>
 * <li>갑옷 · 장식 ({@link #extract} — AvatarRenderer.extractRenderState 끝): 숨기거나 스킨을 입은 갑옷 칸은 렌더 상태에서 비움 (바닐라 · RPG 갑옷 모두
 * 그리지 않음 — 투구 칸은 머리에 쓴 블록 · 머리도), 그릴 것 (갑옷 스킨 · 머리 · 등 장식 · 칭호) 은 렌더 상태에 붙여 둠 → {@link CosmeticLayer}.
 * 등 장식이 있으면 바닐라 망토는 그리지 않음</li>
 * <li>칭호 ({@link #title} — AvatarRenderer.submitNameDisplay 끝): 이름 (아래 점수 줄이 있으면 그 위) 위에 한 줄 더</li>
 * </ul>
 */
public final class CosmeticRender {
    private CosmeticRender() {}

    /** 렌더 상태에 붙이는 그릴 것 */
    public record Data(List<PieceModel> armor, PieceModel head, PieceModel back, Component title) {}

    public static final Data NONE = new Data(List.of(), null, null, null);
    public static final RenderStateDataKey<Data> KEY = RenderStateDataKey.create(() -> "mmorpg:cosmetics");

    /** 무기 아이템 → 무기 종류 ("" = RPG 무기 아님) */
    private static final Map<Item, String> TYPE = new ConcurrentHashMap<>();
    /** 스킨 키 → 아이템 모델 ID */
    private static final Map<String, Identifier> MODEL = new ConcurrentHashMap<>();
    /** 칭호 글 → 글 조각 */
    private static final Map<String, Component> TITLES = new HashMap<>();

    /** 시험용: 스킨으로 바꿔 그린 수 (1인칭 · 3인칭) */
    public static int skinnedFirstPerson;
    public static int skinnedThirdPerson;

    // ------------------------------------------------------------ 무기 스킨

    public static String weaponType(ItemStack stack) {
        if (stack.isEmpty()) return "";
        return TYPE.computeIfAbsent(stack.getItem(), item -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!"rpg".equals(id.getNamespace())) return "";
            Weapon w = Weapons.parseWeapon(id.toString());
            return w == null ? "" : w.type();
        });
    }

    /** 스킨의 아이템 모델 (rpg:cosmetic_&lt;키&gt; — tools/cosmetic_art.py 가 만든 아이템 정의) */
    public static Identifier skinModel(String id) {
        return MODEL.computeIfAbsent(id, k -> Identifier.fromNamespaceAndPath("rpg", "cosmetic_" + k));
    }

    private static boolean hand(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    /** 손에 든 무기를 그릴 때: 스킨이 있으면 모델만 바꾼 복사본 (없으면 그대로) */
    public static ItemStack skinned(ItemStack stack, ItemDisplayContext ctx, LivingEntity entity) {
        if (stack == null || stack.isEmpty() || !(entity instanceof Avatar) || ctx == null || !hand(ctx) || !ClientCosmetics.any()) return stack;
        Look look = ClientCosmetics.lookOf(entity.getUUID());
        if (look.weapon().isEmpty()) return stack;
        String skin = look.weaponSkin(weaponType(stack));
        if (skin == null) return stack;
        ItemStack copy = stack.copy();
        copy.set(DataComponents.ITEM_MODEL, skinModel(skin));
        if (ctx.firstPerson()) skinnedFirstPerson++;
        else skinnedThirdPerson++;
        return copy;
    }

    // ------------------------------------------------------------ 갑옷 · 장식

    public static void extract(Avatar entity, AvatarRenderState state) {
        if (!ClientCosmetics.any()) {
            state.setData(KEY, NONE);
            return;
        }
        Look look = ClientCosmetics.lookOf(entity.getUUID());
        if (look.slots().isEmpty()) {
            state.setData(KEY, NONE);
            return;
        }
        List<PieceModel> armor = new ArrayList<>(4);
        for (String slot : CosmeticDefs.ARMOR) {
            String v = look.get(slot);
            if (v == null) continue;
            clear(state, slot);
            if (Look.HIDE.equals(v)) continue;
            PieceModel m = CosmeticModels.of(CosmeticDefs.get(v));
            if (m != null) armor.add(m);
        }
        PieceModel head = CosmeticModels.of(look.def("head"));
        PieceModel back = CosmeticModels.of(look.def("back"));
        if (back != null) state.showCape = false;
        CosmeticDefs.Def t = look.def("title");
        Component title = t == null || t.title() == null ? null : TITLES.computeIfAbsent(t.title(), s -> Component.literal(BedrockText.toJava(s)));
        state.setData(KEY, armor.isEmpty() && head == null && back == null && title == null ? NONE : new Data(List.copyOf(armor), head, back, title));
    }

    /** 갑옷 칸을 그리지 않게 (렌더 상태만 — 입은 갑옷 · 수치는 그대로) */
    private static void clear(AvatarRenderState state, String slot) {
        switch (slot) {
            case "helmet" -> {
                state.headEquipment = ItemStack.EMPTY;
                state.headItem.clear();
                state.wornHeadType = null;
                state.wornHeadProfile = null;
            }
            case "chest" -> state.chestEquipment = ItemStack.EMPTY;
            case "legs" -> state.legsEquipment = ItemStack.EMPTY;
            case "boots" -> state.feetEquipment = ItemStack.EMPTY;
            default -> {}
        }
    }

    // ------------------------------------------------------------ 칭호

    /** 한 줄 높이 (바닐라 이름표 — 글자 9 × 1.15 줄 간격 × 0.025 크기) */
    private static final float LINE = 9.0F * 1.15F * 0.025F;

    public static void title(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        Data d = state.getData(KEY);
        if (d == null || d.title() == null || state.nameTag == null || state.nameTagAttachment == null) return;
        pose.pushPose();
        pose.translate(0.0F, (state.scoreText != null ? 2 : 1) * LINE, 0.0F);
        collector.submitNameTag(pose, state.nameTagAttachment, state.showExtraEars ? -10 : 0, d.title(), !state.isDiscrete, state.lightCoords, camera);
        pose.popPose();
    }

    /** 장식 하나의 아이템 아이콘 모델 (장식 창 — rpg:cosmetic_&lt;키&gt;) */
    public static ItemStack icon(String id) {
        ItemStack s = new ItemStack(net.minecraft.world.item.Items.PAPER);
        s.set(DataComponents.ITEM_MODEL, skinModel(id));
        return s;
    }
}
