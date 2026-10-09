package mmorpg.fabric;

import java.util.function.Consumer;
import mmorpg.core.Rules;
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

/** 스탯 책: 누르면 스탯 창 (애드온과 같음 — 처음 접속하면 하나 받음). 키 K 로도 열림 */
public final class StatBookItem extends Item {
    public StatBookItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("§e스탯 책");
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§7누르면 스탯 창이 열립니다 (키 K)"));
        tooltip.accept(Component.literal("§7레벨이 처음 오를 때마다 포인트 +" + (int) Rules.get().obj("CONFIG").num("POINTS_PER_LEVEL")));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) RpgNet.openStats(sp);
        return InteractionResult.SUCCESS;
    }
}
