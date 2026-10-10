package mmorpg.fabric.rune;

import java.util.function.Consumer;
import mmorpg.core.Runes;
import mmorpg.fabric.BedrockText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * 룬 아이템 하나 (rpg:rune_&lt;계열&gt;_&lt;등급&gt;): 이름 · 설명은 규칙 코어 (Runes.runeLore) 그대로, 우클릭으로 사용 (요구 스탯 없음 — RuneCast).
 * 재사용 대기는 핫바 아이콘에도 쓸기로 보임 (아이템 재사용 대기).
 */
public final class RuneItem extends Item {
    private final Runes.Rune rune;

    public RuneItem(Runes.Rune rune, Item.Properties properties) {
        super(properties);
        this.rune = rune;
    }

    public Runes.Rune rune() {
        return rune;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(rune.gradeColor() + rune.def().str("name") + "§r§7 · " + rune.famName() + " 룬 (" + rune.gradeName() + ")"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        for (String line : Runes.runeLore(rune, false)) {
            // 조작 줄: 애드온의 터치 (모바일) → 자바 (우클릭 · /runequick on 이면 칸을 고르면 바로)
            String l = line.replace("터치: 칸을 고르면 바로 사용", "/runequick on: 칸을 고르면 바로 사용").replace("누르면 사용 · 요구 스탯 없음", "우클릭으로 사용 · 요구 스탯 없음");
            tooltip.accept(Component.literal(BedrockText.toJava(l)));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) RuneCast.onUse(sp, player.getItemInHand(hand), rune);
        return InteractionResult.CONSUME;
    }
}
