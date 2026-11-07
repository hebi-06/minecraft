package mmorpg.fabric.vanilla;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * RPG 블록 (보스 제단 11 — 큰 그림 블록) 의 바닐라 대체: 모드 없는 클라이언트에는 바닐라 블록 상태 하나를 빌려 (Polymer blocks — 소리 블록
 * 상태) 리소스 팩이 그 상태를 우리 모델 (rpg:block/&lt;제단&gt; — 위 · 옆 · 아래 그림) 로 그림. 빌릴 상태가 모자라면 자석석.
 * 모드 클라이언트에는 그대로.
 */
final class VanillaBlocks {
    private VanillaBlocks() {}

    static void init() {
        int n = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!VanillaSupport.ours(id)) continue;
            BlockState look = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK,
                    PolymerBlockModel.of(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath())));
            BlockState vanilla = look != null ? look : Blocks.LODESTONE.defaultBlockState();
            PolymerBlock.registerOverlay(block, new PolymerBlock() {
                @Override
                public BlockState getPolymerBlockState(BlockState state, PacketContext ctx) {
                    return VanillaClients.isModded(ctx) ? state : vanilla;
                }

                @Override
                public Block getPolymerReplacement(Block b, PacketContext ctx) {
                    return VanillaClients.isModded(ctx) ? b : vanilla.getBlock();
                }

                @Override
                public boolean canSyncRawToClient(PacketContext ctx) {
                    return VanillaClients.isModded(ctx);
                }
            });
            n++;
        }
        Mmorpg.LOG.info("[vanilla] blocks {}", n);
    }
}
