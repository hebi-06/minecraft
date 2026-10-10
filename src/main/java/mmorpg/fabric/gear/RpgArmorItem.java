package mmorpg.fabric.gear;

import java.util.function.Consumer;
import mmorpg.core.Armor;
import mmorpg.fabric.BedrockText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * RPG 갑옷 한 부위 (rpg:armor_&lt;종류&gt;_l&lt;레벨&gt;_&lt;등급&gt;_&lt;부위&gt;, armor.js): 이름 · 설명은 규칙 코어 (Armor.armorInfo) 그대로,
 * 방어 포인트 = 아이템의 방어 속성 (바닐라 계산), 스탯 보너스 · 요구 조건은 ArmorSystem 이 0.5초마다.
 * 입은 모습은 클라이언트의 애드온 3D 모델 (ArmorModels). 한 칸 1개 · 내구도 없음.
 */
public final class RpgArmorItem extends Item {
    private final Armor.Piece piece;

    public RpgArmorItem(Armor.Piece piece, Item.Properties properties) {
        super(properties);
        this.piece = piece;
    }

    public Armor.Piece piece() {
        return piece;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(piece.name()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        for (String line : Armor.armorInfo(piece, false)) tooltip.accept(Component.literal(BedrockText.toJava(line)));
    }
}
