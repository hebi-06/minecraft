package mmorpg.fabric.economy;

import java.util.function.Consumer;
import mmorpg.core.Economy;
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
 * 0.21.0 금화 · 은화 (rpg:gold_coin · rpg:silver_coin — 사용자 요청 "골드와 실버 아이템을 우클릭하면 획득"): 우클릭하면 손에 든 묶음이 지갑으로,
 * 웅크리고 우클릭하면 가방의 같은 동전 모두. 보스 전리품 · 몹이 떨어뜨림 · "/골드 수" · "/실버 수" 로 꺼낸 것.
 */
public final class CoinItem extends Item {
    public final Economy.Kind kind;

    public CoinItem(Economy.Kind kind, Item.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(kind == Economy.Kind.GOLD ? "§6금화" : "§f은화");
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        String name = kind.label();
        tooltip.accept(Component.literal("§7우클릭: 지갑에 넣기 §f+" + Economy.fmt(stack.getCount()) + " " + name));
        tooltip.accept(Component.literal("§8웅크리고 우클릭: 가방의 " + (kind == Economy.Kind.GOLD ? "금화" : "은화") + " 모두"));
        tooltip.accept(Component.literal(kind == Economy.Kind.GOLD ? "§8골드: 유저 장터 · 장식" : "§8실버: 상점 · 대장장이 · 룬술사"));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) Wallet.deposit(sp, hand, kind, sp.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }
}
