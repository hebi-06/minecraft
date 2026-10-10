package mmorpg.fabric.rune;

import java.util.List;
import java.util.function.Consumer;
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

/** 룬 뽑기권 (rpg:rune_ticket) · 고급 룬 뽑기권 (rpg:high_rune_ticket — 일반 룬 없음): 우클릭하면 룬 하나를 뽑음 (RuneGacha) */
public final class TicketItem extends Item {
    private final boolean adv;

    public TicketItem(boolean adv, Item.Properties properties) {
        super(properties);
        this.adv = adv;
    }

    public boolean advanced() {
        return adv;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(adv ? "§6§l고급 룬 뽑기권§r§7 (누르면 룬 뽑기 · 일반 없음)" : "§d§l룬 뽑기권§r§7 (누르면 룬 뽑기)"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        List<Object> lore = RuneKit.RUNE.list(adv ? "advTicketLore" : "ticketLore");
        for (Object line : lore) tooltip.accept(Component.literal(BedrockText.toJava(String.valueOf(line).replace("들고 누르면", "들고 우클릭하면"))));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) RuneGacha.useTicket(sp, adv);
        return InteractionResult.CONSUME;
    }
}
