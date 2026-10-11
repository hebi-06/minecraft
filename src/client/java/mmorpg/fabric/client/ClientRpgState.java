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
        treeOn = false;
        treeState = mmorpg.core.StatTree.State.EMPTY;
        treeTotals = java.util.Map.of();
        money = 0;
    }

    // ------------------------------------------------------------ 스탯 트리 (7단계)

    private static boolean treeOn;
    private static mmorpg.core.StatTree.State treeState = mmorpg.core.StatTree.State.EMPTY;
    private static java.util.Map<String, Double> treeTotals = java.util.Map.of();
    private static int money;

    /** 서버가 보낸 스탯 트리 (JSON) → 열 탭 ("" = 열지 않음) */
    static String updateStatTree(RpgNet.StatTreePayload p) {
        try {
            mmorpg.core.Obj root = mmorpg.core.Obj.of(mmorpg.core.Json.parse(p.json()), "stat_tree_payload");
            treeOn = root.truthy("on");
            Object st = root.get("state");
            treeState = mmorpg.core.StatTree.State.parse(st == null ? null : mmorpg.core.Json.stringify(st));
            java.util.Map<String, Double> t = new java.util.LinkedHashMap<>();
            mmorpg.core.Obj tot = root.objOrNull("totals");
            if (tot != null) for (String k : tot.keys()) t.put(k, tot.num(k));
            treeTotals = java.util.Map.copyOf(t);
            money = (int) root.numOr("money", 0);
            return root.strOr("open", "");
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** 이 서버에서 스탯 트리를 쓰는지 */
    public static boolean treeOn() {
        return treeOn;
    }

    /** 고른 갈래 · 받은 칸 */
    public static mmorpg.core.StatTree.State treeState() {
        return treeState;
    }

    /** 보너스 합 (서버가 계산 — 나무 넷 · 상한) */
    public static java.util.Map<String, Double> treeTotals() {
        return treeTotals;
    }

    /** 가진 에메랄드 (갈래 초기화 비용 표시) */
    public static int money() {
        return money;
    }

    /** 찍은 스탯 값 (스탯 트리 칸 계산) */
    public static int spent(String stat) {
        RpgNet.SyncPayload p = last;
        if (p == null) return 0;
        return switch (stat) {
            case "str" -> p.str();
            case "dex" -> p.dex();
            case "int" -> p.intel();
            case "vit" -> p.vit();
            default -> 0;
        };
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
        return StatSheet.of(p.str(), p.dex(), p.intel(), p.vit(), p.point(), p.lvmax(), level, p.bonus(), p.buff()).withTree(treeOn ? treeTotals : java.util.Map.of());
    }
}
