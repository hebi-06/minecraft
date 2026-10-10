package mmorpg.fabric.gear;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mmorpg.fabric.BedrockText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 재료 아이템 (레이드 재료 · 보스 재료 · 몬스터 전리품 · 룬 조각 · NPC 설정 도구): 이름은 애드온 아이템 정의 그대로, 설명은 규칙 (lore) —
 * 애드온은 설명을 아이템마다 붙이지만 (setLore) 자바는 아이템 종류가 그림 (그래서 /give 로 받은 것도 같은 설명 · 겹쳐 쌓임).
 */
public class MaterialItem extends Item {
    private final String name;
    private final Supplier<List<String>> lore;

    public MaterialItem(String name, Supplier<List<String>> lore, Item.Properties properties) {
        super(properties);
        this.name = name;
        this.lore = lore;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(name));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        for (String line : lore.get()) tooltip.accept(Component.literal(BedrockText.toJava(line)));
    }
}
