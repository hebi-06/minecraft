package mmorpg.fabric.mount;

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

/**
 * 탈것 아이템 (rpg:mount_&lt;키&gt; — mounts.json 의 탈것마다): 쓰면 (우클릭 · 길게 누르기) 그 탈것이 내 탈것 목록에 들어가고 아이템 1개가 사라짐.
 * 이미 가진 탈것이면 그대로 (사라지지 않음). 탈것 바꾸기는 스탯 창 탈것 칸을 Shift + 누르기 (또는 /mount select).
 */
public final class MountItem extends Item {
    public final String key;

    public MountItem(Item.Properties properties, String key) {
        super(properties);
        this.key = key;
    }

    @Override
    public Component getName(ItemStack stack) {
        MountDefs.Def d = MountDefs.get(key);
        return Component.literal(BedrockText.toJava("§b" + (d != null ? d.name() : key) + " §f탈것"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        MountDefs.Def d = MountDefs.get(key);
        if (d != null) for (String l : d.desc()) tooltip.accept(Component.literal(BedrockText.toJava(l)));
        tooltip.accept(Component.literal(BedrockText.toJava("§a쓰면 내 탈것 목록에 들어갑니다")));
        tooltip.accept(Component.literal(BedrockText.toJava("§8탈것 바꾸기: 스탯 창 탈것 칸을 Shift + 누르기")));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        return Mounts.useItem(sp, player.getItemInHand(hand), key) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
}
