package mmorpg.fabric.economy;

import mmorpg.fabric.RpgNet;
import net.minecraft.server.level.ServerPlayer;

/**
 * 0.21.0 화폐 · 유저 장터 · 쓰레기통 (사용자 요청): 지갑 (골드 · 실버 — 화면 왼쪽 아래), 금화 · 은화, 에메랄드 → 실버 (처음 접속 · /환전),
 * 유저 장터 (골드 · 수수료 5% · 3일 · 10개), 쓰레기통, 버린 아이템은 가방으로 (DropGuard — mixin). Mmorpg.onInitialize 에서 init,
 * 접속 · 나감은 RpgPlayers 가 부름 (시험용 가짜 플레이어도 같게).
 */
public final class EconomySystem {
    private EconomySystem() {}

    public static void init() {
        Wallet.init();
        EconomyNet.init();
        MarketStore.init();
        MarketWindow.init();
        TrashWindow.init();
        EconomyCommands.init();
        RpgNet.beforeStatsOpen(Wallet::sync);
    }

    /** 접속: 에메랄드 → 실버 (한 번) · 지갑 보내기 · 장터 (그동안 팔린 대금 · 글 · 보관함) */
    public static void onJoin(ServerPlayer sp) {
        Wallet.migrate(sp);
        Wallet.sync(sp);
        MarketStore.onJoin(sp);
    }

    /** 나감 */
    public static void onLeave(ServerPlayer sp) {
        DropGuard.forget(sp.getUUID());
    }
}
