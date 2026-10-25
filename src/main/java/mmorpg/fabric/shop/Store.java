package mmorpg.fabric.shop;

import java.util.List;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.ui.Forms;
import net.minecraft.server.level.ServerPlayer;

/**
 * 상점 (armor.js storeLoop · openStore): 무기 ↔ 갑옷 ↔ 전리품 팔기 (윗줄 칸으로 오감). NPC 대화 · /shop 이 엶.
 * 0.45.0: 모드 클라이언트는 상인 전용 창 ({@link ShopUi} — 같은 셋을 탭으로).
 */
public final class Store {
    private Store() {}

    /** 상점 창 (흐름 안에서 — 예: NPC 대화에서). start: "weapons" · "armor" · "sell", 끝나면 done (null 이면 없음) */
    public static void storeLoop(ServerPlayer sp, int level, String start, Runnable done) {
        storeLoop(sp, null, level, start, done);
    }

    /**
     * 같은 것 — npc = 상인 NPC (0.45.0: 모드 클라이언트면 상인 전용 창 {@link ShopUi} — 창의 상인 패널 · 세상의 그 NPC 가 물건을 건넴. 모드가 없거나
     * 시험 · 사진첩이 legacy 로 고르면 예전 상자 창)
     */
    public static void storeLoop(ServerPlayer sp, net.minecraft.world.entity.Entity npc, int level, String start, Runnable done) {
        String st = start == null ? "weapons" : start;
        if (ShopUi.usable(sp)) {
            ShopUi.open(sp, npc, level, st, done);
            return;
        }
        go(sp, level, st, 0, done);
    }

    private static void go(ServerPlayer sp, int level, String next, int i, Runnable done) {
        if (next == null || i >= 20) {
            if (done != null) done.run();
            return;
        }
        switch (next) {
            case "armor" -> ArmorShop.armorShopLoop(sp, level, n -> go(sp, level, n, i + 1, done));
            case "sell" -> Shop.sellLoop(sp, n -> go(sp, level, n, i + 1, done));
            default -> Shop.shopLoop(sp, level, n -> go(sp, level, n, i + 1, done));
        }
    }

    /** 상점 열기 (명령어용 — 창이 열려 있으면 무시) */
    public static void openStore(ServerPlayer sp, int level, String start) {
        List<Double> levels = Rules.get().numbers("LEVELS");
        if (!levels.contains((double) level)) {
            Notify.say(sp, "§c상점 레벨은 " + String.join(", ", levels.stream().map(d -> String.valueOf(d.intValue())).toList()) + " 중 하나여야 합니다.");
            return;
        }
        if (Forms.isOpen(sp)) return;
        storeLoop(sp, level, start, null);
    }
}
