package mmorpg.fabric.client;

import mmorpg.fabric.RpgNet;
import mmorpg.fabric.StatSheet;
import net.minecraft.client.Minecraft;

/** 서버가 보낸 스탯 데이터 (모드가 있는 서버에 들어왔을 때만 synced) */
public final class ClientRpgState {
    private ClientRpgState() {}

    private static RpgNet.SyncPayload last;

    static void update(RpgNet.SyncPayload p) {
        last = p;
    }

    static void clear() {
        last = null;
    }

    /** 이 서버에 MMO RPG 모드가 있는지 (스탯 데이터를 받았는지) */
    public static boolean synced() {
        return last != null;
    }

    public static int point() {
        return last == null ? 0 : last.point();
    }

    public static int lvmax() {
        return last == null ? 0 : last.lvmax();
    }

    /** 스탯 창 · HUD 계산 (지금 경험치 레벨 포함) */
    public static StatSheet sheet() {
        Minecraft mc = Minecraft.getInstance();
        int level = mc.player == null ? 0 : mc.player.experienceLevel;
        RpgNet.SyncPayload p = last;
        if (p == null) return StatSheet.of(0, 0, 0, 0, 0, 0, level);
        return StatSheet.of(p.str(), p.dex(), p.intel(), p.vit(), p.point(), p.lvmax(), level);
    }
}
