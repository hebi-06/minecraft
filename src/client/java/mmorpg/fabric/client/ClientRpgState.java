package mmorpg.fabric.client;

import mmorpg.fabric.RpgNet;
import mmorpg.fabric.StatSheet;
import net.minecraft.client.Minecraft;

/** 서버가 보낸 스탯 데이터 (모드가 있는 서버에 들어왔을 때만 synced) */
public final class ClientRpgState {
    private ClientRpgState() {}

    private static RpgNet.SyncPayload last;
    /** 탈것 상태 (스탯 창 탈것 칸) · 받은 때 (밀리초 — 다시 부르기 대기를 셈) */
    private static RpgNet.MountPayload mount;
    private static long mountAt;

    static void update(RpgNet.SyncPayload p) {
        last = p;
    }

    static void updateMount(RpgNet.MountPayload p) {
        mount = p;
        mountAt = net.minecraft.util.Util.getMillis();
    }

    static void clear() {
        last = null;
        mount = null;
    }

    /** 지금 고른 탈것 키 ("" = 없음 · 모름) */
    public static String mountCurrent() {
        return mount == null ? "" : mount.current();
    }

    /** 가진 탈것 키들 */
    public static java.util.List<String> mountOwned() {
        return mount == null ? java.util.List.of() : mount.owned();
    }

    /** 다시 부를 수 있을 때까지 남은 초 (0 = 지금) */
    public static double mountCooldown() {
        if (mount == null || mount.cooldown() <= 0) return 0;
        return Math.max(0, mount.cooldown() / 20.0 - (net.minecraft.util.Util.getMillis() - mountAt) / 1000.0);
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
        return StatSheet.of(p.str(), p.dex(), p.intel(), p.vit(), p.point(), p.lvmax(), level, p.bonus(), p.buff());
    }
}
