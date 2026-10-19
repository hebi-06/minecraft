package mmorpg.fabric.storage;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 0.22.0 엔더 창고 (사용자 요청 "엔더 상자를 플레이어 전용 창고로 — 큰 상자 UI · 최대 5개 · 1개는 기본 · 2단계는 실버 · 그다음부터 골드"):
 * 저장 (StorageData — 플레이어 데이터 mmorpg:storage), 창 (StorageWindow — 큰 상자 · 상자 탭 · 사기), 패킷 (StorageNet), 명령어 (/창고 · /storage).
 * 놓인 엔더 상자를 누르면 바닐라 27칸 대신 창고 (웅크리고 물건을 들면 바닐라처럼 블록 놓기 · 위가 막히면 열리지 않음).
 * Mmorpg.onInitialize 에서 init, 접속은 RpgPlayers 가 부름 (바닐라 엔더 상자 → 1번 상자 — 에메랄드 → 실버 다음).
 */
public final class StorageSystem {
    private StorageSystem() {}

    public static void init() {
        StorageData.init();
        StorageNet.register();
        StorageCommands.init();
        UseBlockCallback.EVENT.register(StorageSystem::onUseBlock);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> StorageWindow.Lids.clear());
    }

    /** 접속: 바닐라 엔더 상자에 남은 아이템을 1번 상자로 (Wallet.migrate — 에메랄드 → 실버 다음에 부름) */
    public static void onJoin(ServerPlayer sp) {
        StorageWindow.migrate(sp);
    }

    /** 놓인 엔더 상자 누르기 → 엔더 창고 (서버에서 — 클라이언트는 바닐라대로 팔을 휘두르고 서버에 알림) */
    static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(Blocks.ENDER_CHEST)) return InteractionResult.PASS;
        // 웅크리고 무언가를 들었으면 바닐라처럼 블록을 쓰지 않음 (그 위에 블록 놓기 등)
        if (sp.isSecondaryUseActive() && (!sp.getMainHandItem().isEmpty() || !sp.getOffhandItem().isEmpty())) return InteractionResult.PASS;
        // 위가 막힌 엔더 상자는 바닐라처럼 열리지 않음
        BlockPos above = pos.above();
        if (level.getBlockState(above).isRedstoneConductor(level, above)) return InteractionResult.SUCCESS;
        StorageWindow.open(sp, -1, sl, pos);
        return InteractionResult.SUCCESS;
    }
}
