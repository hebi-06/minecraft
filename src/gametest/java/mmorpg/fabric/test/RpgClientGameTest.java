package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.client.ClientRpgState;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.RpgHud;
import mmorpg.fabric.client.StatScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 화면 시험 (클라이언트를 띄워 실제 입력으로): ./gradlew runClientGameTest (CI 는 xvfb).
 * 평평한 세계에서 무기 · 스탯을 준비한 뒤 HUD · 평타 / 치명타 숫자 · 3인칭 · 피해 · 낮은 체력 · 스탯 창 (K 키 · 툴팁 · +1 클릭)
 * · 활 · 완드 장면을 찍음 (build/run/clientGameTest/screenshots). 확인 항목이 하나라도 틀리면 마지막에 실패.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-client-test");
    /** 과녁 허스크 자리 (평평한 세계 땅 위 y = -60): 근접 · 완드 · 활 */
    private static final Vec3[] TARGETS = {new Vec3(0.5, -60, 3.5), new Vec3(4.5, -60, 12.5), new Vec3(-3.5, -60, 8.5)};
    private static final int MELEE = 0;
    private static final int WAND = 1;
    private static final int BOW = 2;
    private static final float HUSK_HP = 1000F;

    private final List<String> failures = new ArrayList<>();
    private List<Husk> husks = List.of();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            setup(ctx, server, conn);
            melee(ctx, server);
            hurt(ctx, server);
            statScreen(ctx, server, conn);
            ranged(ctx, server);
        }
        LOG.info("[client-test] done, {} failed checks", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("RPG client test failed: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 장면

    private void setup(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runCommand("time set 6000");
        server.runCommand("weather clear");
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        server.runCommand("xp set @a 30 levels");
        ctx.waitTicks(10);
        // 30렙 영웅 무기 요구 스탯 (83) 보다 넉넉히
        for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 120");
        server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l30_hero");
        server.runCommand("item replace entity @a hotbar.1 with rpg:bow_l30_hero");
        server.runCommand("item replace entity @a hotbar.2 with rpg:wand_l30_hero");
        server.runCommand("item replace entity @a hotbar.3 with rpg:shield_l30_hero");
        server.runCommand("item replace entity @a hotbar.8 with rpg:stat_book");
        husks = server.computeOnServer(s -> {
            ServerLevel level = s.getPlayerList().getPlayers().get(0).level();
            List<Husk> list = new ArrayList<>();
            for (Vec3 at : TARGETS) {
                Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                h.setNoAi(true);
                h.setSilent(true);
                h.setPersistenceRequired();
                h.getAttribute(Attributes.ARMOR).setBaseValue(0);
                h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HUSK_HP);
                h.setHealth(HUSK_HP);
                h.snapTo(at.x, at.y, at.z, 180F, 0F);
                h.setYHeadRot(180F);
                h.setYBodyRot(180F);
                level.addFreshEntity(h);
                list.add(h);
            }
            return list;
        });
        conn.waitForClientboundEntityUpdates(EntityTypes.HUSK);
        ctx.waitTicks(30);
        check(ctx.computeOnClient(mc -> ClientRpgState.synced()), "stats synced to client");
        check(ctx.computeOnClient(mc -> RpgHud.active()), "RPG HUD active in survival");
        check(ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(Husk.class, new AABB(mc.player.blockPosition()).inflate(30)).size()) == 3, "3 husks on client");
    }

    private void melee(ClientGameTestContext ctx, TestServerContext server) {
        select(ctx, 0);
        look(ctx, MELEE);
        ctx.waitTicks(10);
        ctx.takeScreenshot("hud"); // 채팅이 보이는 채로 (체력 바 글자가 채팅창과 겹쳐도 깨지지 않는지)
        ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
        ctx.waitTicks(20);

        attack(ctx, server, false);
        ctx.waitTicks(3);
        ctx.takeScreenshot("melee_number");
        float hp1 = huskHealth(server, MELEE);
        check(hp1 < HUSK_HP, "melee hit (husk " + hp1 + ")");
        List<String> nums = numbersOnClient(ctx);
        check(!nums.isEmpty(), "damage number visible on client");

        ctx.waitTicks(20);
        attack(ctx, server, true);
        ctx.waitTicks(3);
        ctx.takeScreenshot("melee_crit");
        float hp2 = huskHealth(server, MELEE);
        check(hp2 < hp1, "crit hit (husk " + hp2 + ")");
        List<String> critNums = numbersOnClient(ctx);
        check(critNums.stream().anyMatch(t -> t.contains("!")), "crit number with '!' " + critNums);
        server.runOnServer(s -> RpgCombat.forceCrit = null);

        camera(ctx, CameraType.THIRD_PERSON_BACK);
        ctx.waitTicks(20);
        attack(ctx, server, false);
        ctx.waitTicks(3);
        ctx.takeScreenshot("third_person");
        server.runOnServer(s -> RpgCombat.forceCrit = null);
        camera(ctx, CameraType.FIRST_PERSON);
    }

    private void hurt(ClientGameTestContext ctx, TestServerContext server) {
        float max = server.computeOnServer(s -> player(s).getMaxHealth());
        server.runCommand("damage @a 60 minecraft:generic");
        ctx.waitTicks(3);
        ctx.takeScreenshot("hp_hit");
        ctx.waitTicks(30);
        server.runCommand("damage @a 100 minecraft:generic");
        ctx.waitTicks(8);
        ctx.takeScreenshot("hp_low");
        float hp = server.computeOnServer(s -> player(s).getHealth());
        check(hp > 0 && hp < max * 0.3F, "low health " + hp + " / " + max);
    }

    private void statScreen(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        try {
            ctx.waitForScreen(StatScreen.class);
        } catch (AssertionError | RuntimeException e) {
            fail("K key did not open the stat screen: " + e.getMessage());
            ctx.setScreen(StatScreen::new);
        }
        ctx.waitTicks(10);
        ctx.takeScreenshot("stats");

        int[] before = server.computeOnServer(s -> statAndPoints(player(s)));
        // 힘 줄의 +1 단추 위에 마우스 → 툴팁
        double[] at = ctx.computeOnClient(mc -> {
            StatScreen sc = (StatScreen) mc.gui.screen();
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {sc.buttonX() * k, sc.rowY(0) * k};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(6);
        ctx.takeScreenshot("stats_tooltip");

        // 누르면 서버의 힘 +1 · 포인트 -1
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        int[] after = server.computeOnServer(s -> statAndPoints(player(s)));
        check(after[0] == before[0] + 1 && after[1] == before[1] - 1,
                "stat screen +1: str " + before[0] + "→" + after[0] + ", points " + before[1] + "→" + after[1]);
        check(ctx.computeOnClient(mc -> ClientRpgState.point()) == after[1], "client points synced");
        ctx.waitTicks(2);
        ctx.takeScreenshot("stats_spent");

        // K 로 닫기
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        try {
            ctx.waitForScreen(null);
        } catch (AssertionError | RuntimeException e) {
            fail("K key did not close the stat screen: " + e.getMessage());
            ctx.setScreen(() -> null);
        }
        ctx.getInput().setCursorPos(640, 360);
    }

    private void ranged(ClientGameTestContext ctx, TestServerContext server) {
        select(ctx, 1);
        look(ctx, BOW);
        ctx.waitTicks(15);
        use(ctx);
        ctx.waitTicks(1);
        ctx.takeScreenshot("bow_flight");
        ctx.waitTicks(5);
        ctx.takeScreenshot("bow_hit");
        ctx.waitTicks(5);
        check(huskHealth(server, BOW) < HUSK_HP, "bow hit (husk " + huskHealth(server, BOW) + ")");

        select(ctx, 2);
        look(ctx, WAND);
        ctx.waitTicks(20);
        use(ctx);
        ctx.waitTicks(5);
        ctx.takeScreenshot("wand_cast");
        ctx.waitTicks(7);
        ctx.takeScreenshot("wand_hit");
        ctx.waitTicks(5);
        check(huskHealth(server, WAND) < HUSK_HP, "wand hit (husk " + huskHealth(server, WAND) + ")");

        camera(ctx, CameraType.THIRD_PERSON_BACK);
        ctx.waitTicks(25);
        use(ctx);
        ctx.waitTicks(6);
        ctx.takeScreenshot("wand_cast_third_person");
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.waitTicks(20);
    }

    // ------------------------------------------------------------ 도움

    private void check(boolean ok, String what) {
        LOG.info("[client-test] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private void fail(String what) {
        check(false, what);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static int[] statAndPoints(ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        return new int[] {d.str(), d.point()};
    }

    private float huskHealth(TestServerContext server, int i) {
        Husk h = husks.get(i);
        return server.computeOnServer(s -> h.getHealth());
    }

    private static void select(ClientGameTestContext ctx, int slot) {
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
    }

    private static void camera(ClientGameTestContext ctx, CameraType type) {
        ctx.runOnClient(mc -> mc.options.setCameraType(type));
    }

    /** 클라이언트에서 과녁 i 에 가장 가까운 허스크 */
    private static Husk clientHusk(Minecraft mc, int i) {
        Vec3 at = TARGETS[i];
        return mc.level.getEntitiesOfClass(Husk.class, new AABB(at, at).inflate(2)).stream()
                .min(Comparator.comparingDouble(h -> h.position().distanceToSqr(at)))
                .orElseThrow(() -> new AssertionError("husk " + i + " not found on client"));
    }

    /** 과녁 i 의 눈 조금 아래를 바라봄 */
    private static void look(ClientGameTestContext ctx, int i) {
        ctx.runOnClient(mc -> {
            Husk h = clientHusk(mc, i);
            mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(h.getX(), h.getEyeY() - 0.2, h.getZ()));
        });
    }

    /** 근접 과녁을 침 (마우스 왼쪽과 같은 공격 패킷) */
    private static void attack(ClientGameTestContext ctx, TestServerContext server, boolean crit) {
        server.runOnServer(s -> RpgCombat.forceCrit = crit);
        ctx.runOnClient(mc -> mc.gameMode.attack(mc.player, clientHusk(mc, MELEE)));
    }

    /** 손에 든 무기 쓰기 (마우스 오른쪽과 같은 경로) */
    private static void use(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
    }

    /** 클라이언트에 보이는 피해 숫자 글자 */
    private static List<String> numbersOnClient(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.level.getEntitiesOfClass(Display.TextDisplay.class, mc.player.getBoundingBox().inflate(16)).stream()
                .map(e -> e.getText().getString())
                .filter(t -> !t.isBlank())
                .toList());
    }
}
