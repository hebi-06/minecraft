package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.client.ClientRpgState;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.RpgHud;
import mmorpg.fabric.client.StatScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 배포 환경 화면 시험 (CI 의 prodClient: 실제 설치와 같게 배포 jar + Fabric API jar 로 클라이언트를 띄움 — -Dmmorpg.prodSmoke=true 일 때만).
 * 세계를 만들고 RPG HUD · 스탯 동기화 · 무기 들기 · 스탯 창 (K) 이 되는지 짧게 확인하고 사진 (prod_hud · prod_stats). 다른 화면 시험은 이때 건너뜀.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgProdSmoke implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-prod-client");

    /** 배포 환경 시험으로 띄운 것인지 (그때는 긴 화면 시험 · 사진첩을 건너뜀) */
    public static boolean active() {
        return Boolean.getBoolean("mmorpg.prodSmoke");
    }

    /**
     * 이 화면 시험을 돌릴지: 환경 변수 MMORPG_ONLY (CI 수동 실행의 only 입력 — 쉼표로 client · weapon · particle · skill · rune · gear · form · nav · mob) 가
     * 비었으면 모두, 아니면 적힌 것만
     */
    public static boolean only(String key) {
        String v = System.getProperty("mmorpg.only", "");
        if (v.isBlank()) v = System.getenv().getOrDefault("MMORPG_ONLY", "");
        if (v.isBlank()) return true;
        for (String s : v.split(",")) if (s.trim().equalsIgnoreCase(key)) return true;
        return false;
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!active()) return;
        List<String> failures = new ArrayList<>();
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runCommand("xp set @a 40 levels");
            ctx.waitTicks(10);
            for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
            server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l40_transcend");
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            ctx.waitTicks(30);
            if (!ctx.computeOnClient(mc -> ClientRpgState.synced())) failures.add("stats not synced to client");
            if (!ctx.computeOnClient(mc -> RpgHud.active())) failures.add("RPG HUD not active");
            ctx.takeScreenshot("prod_hud");
            ctx.getInput().pressKey(MmorpgClient.statsKey);
            try {
                ctx.waitForScreen(StatScreen.class);
            } catch (AssertionError | RuntimeException e) {
                failures.add("K key did not open the stat screen: " + e.getMessage());
            }
            ctx.waitTicks(10);
            ctx.takeScreenshot("prod_stats");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        }
        LOG.info("[prod-client] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("production client smoke test failed: " + String.join(" / ", failures));
    }
}
