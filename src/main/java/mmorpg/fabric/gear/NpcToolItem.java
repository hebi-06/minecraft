package mmorpg.fabric.gear;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** NPC 설정 도구 (rpg:npc_tool): 허공을 누르면 NPC 관리 창 (NPC 를 누르면 그 NPC 설정 창 — Npcs) */
public final class NpcToolItem extends MaterialItem {
    /** 허공 누르기 (Npcs 가 채움) */
    public static Consumer<ServerPlayer> onUse = sp -> {};

    public NpcToolItem(String name, Supplier<List<String>> lore, Properties properties) {
        super(name, lore, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) onUse.accept(sp);
        return InteractionResult.SUCCESS;
    }
}
