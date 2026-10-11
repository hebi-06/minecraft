package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.mob.RpgMount;
import mmorpg.fabric.mount.MountDefs;
import mmorpg.fabric.mount.MountItems;
import mmorpg.fabric.mount.Mounts;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 6단계 탈것 · 스탯 창 아랫줄 사진첩 (화면 시험 — MMORPG_ONLY=mount, 실제 마우스 · 키 입력):
 * 스탯 창 아랫줄 (퀘스트 · 엔더 상자 · 탈것 — 풀이), 탈것 칸 누르기 → 창이 닫히고 소환 연출 · 바로 탐 (3인칭 앞 · 뒤 — 안장 위에 앉음),
 * 달리기 (앞으로 키 — 빠르기 · 발밑 반짝이), 뛰기 (점프 키를 길게 → 말 뛰기 막대 → 높이 뜀 · 발밑 고리), 한 칸 그냥 달려 오름, 탄 채 공격 → 막힘 안내,
 * Shift + 탈것 칸 → 탈것 고르기 창 → 다른 탈것 (시험용) 으로 갈아탐 → 스탯 창으로, 탈것 아이템 (손 · 풀이 · 쓰면 목록에 · 연출),
 * 엔더 상자 칸 → 바닐라 상자 창 (내 엔더 상자 27칸), 퀘스트 칸 → 퀘스트 목록 → 닫으면 스탯 창, 웅크려 내림 → 사라짐 연출.
 * 사진 이름 m_… → ci-logs 의 shots/. 확인 항목은 로그 [mount-gallery] OK / FAIL.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgMountGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-mount-gallery");
    private static final Vec3 HOME = new Vec3(0.5, -60, 0.5);

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("mount")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            home(server, 0F, 0F);
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(30);
            String gui = ctx.computeOnClient(mc -> mc.getWindow().getScreenWidth() + "x" + mc.getWindow().getScreenHeight() + " → gui "
                    + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight());
            LOG.info("[mount-gallery] window {}", gui);
            part(ctx, server, "stats", () -> stats(ctx));
            part(ctx, server, "summon", () -> summon(ctx, server, conn));
            part(ctx, server, "ride", () -> ride(ctx, server));
            part(ctx, server, "jump", () -> jump(ctx, server));
            part(ctx, server, "step", () -> step(ctx, server));
            part(ctx, server, "blocked", () -> blocked(ctx, server, conn));
            part(ctx, server, "select", () -> select(ctx, server, conn));
            part(ctx, server, "dismount", () -> dismount(ctx, server));
            part(ctx, server, "item", () -> item(ctx, server, conn));
            part(ctx, server, "ender", () -> ender(ctx, server, conn));
            part(ctx, server, "quest", () -> quest(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[mount-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 스탯 창 아랫줄

    private void stats(ClientGameTestContext ctx) {
        openStats(ctx);
        away(ctx);
        shot(ctx, "m_stats_bottom");
        int[] fit = ctx.computeOnClient(mc -> {
            StatScreen sc = (StatScreen) mc.gui.screen();
            int[] b = sc.bottomCenter(2);
            return new int[] {sc.height, (sc.height - StatScreen.H) / 2, b[1]};
        });
        check(fit[1] >= 0 && fit[2] < fit[0], "stat window fits the screen (gui height " + fit[0] + ", top " + fit[1] + ", bottom row y " + fit[2] + ")");
        String[] names = {"quest", "ender", "mount"};
        for (int i = 0; i < 3; i++) {
            hover(ctx, bottom(i));
            shot(ctx, "m_stats_tip_" + names[i]);
        }
        closeScreen(ctx);
    }

    // ------------------------------------------------------------ 소환 · 타기

    private void summon(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        home(server, 0F, 0F);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        openStats(ctx);
        click(ctx, conn, bottom(2));
        boolean riding = waitFor(ctx, mc -> mc.player.getVehicle() instanceof RpgMount, 20);
        check(riding, "mount button → riding a mount");
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "stat window closed before summoning");
        ctx.waitTicks(3);
        shot(ctx, "m_summon_3p");
        ctx.waitTicks(25);
        double[] seat = ctx.computeOnClient(mc -> {
            LocalPlayer p = mc.player;
            return p.getVehicle() == null ? new double[] {-1, -1} : new double[] {p.getY() - p.getVehicle().getY(), p.getVehicle().getBbHeight()};
        });
        check(seat[0] > 0.95 && seat[0] < 1.15, String.format(Locale.ROOT, "rider feet %.3f above the mount's (mount %.2f tall)", seat[0], seat[1]));
        shot(ctx, "m_ride_back");
        camera(ctx, CameraType.THIRD_PERSON_FRONT);
        ctx.waitTicks(4);
        shot(ctx, "m_ride_front");
        // 옆모습: 옆 4칸에 세운 (보이지 않는) 갑옷 거치대를 카메라로 — 안장 위에 앉은 높이 · 다리 · 꼬리
        sideView(ctx, server, "m_ride_side");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, 0F, 20F);
        ctx.waitTicks(4);
        shot(ctx, "m_ride_fp");
    }

    /** 탈것 옆 (동쪽 4칸, 서쪽을 봄) 갑옷 거치대의 눈으로 사진 한 장 */
    private void sideView(ClientGameTestContext ctx, TestServerContext server, String name) {
        Vec3 m = ctx.computeOnClient(mc -> mc.player.getVehicle() != null ? mc.player.getVehicle().position() : mc.player.position());
        int id = server.computeOnServer(s -> {
            ArmorStand as = EntityTypes.ARMOR_STAND.create(s.overworld(), EntitySpawnReason.COMMAND);
            as.snapTo(m.x + 4.5, m.y + 0.2, m.z, 90F, 5F);
            as.setYHeadRot(90F);
            as.setInvisible(true);
            as.setNoGravity(true);
            s.overworld().addFreshEntity(as);
            return as.getId();
        });
        boolean seen = waitFor(ctx, mc -> mc.level.getEntity(id) != null, 20);
        if (seen) {
            ctx.runOnClient(mc -> mc.setCameraEntity(mc.level.getEntity(id)));
            ctx.waitTicks(3);
            shot(ctx, name);
            ctx.runOnClient(mc -> mc.setCameraEntity(mc.player));
        } else {
            check(false, "side camera stand not on the client");
        }
        server.runOnServer(s -> {
            var e = s.overworld().getEntity(id);
            if (e != null) e.discard();
        });
        ctx.waitTick();
    }

    // ------------------------------------------------------------ 달리기 · 뛰기 · 오르기

    private void ride(ClientGameTestContext ctx, TestServerContext server) {
        ensureRiding(ctx, server);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        look(ctx, 0F, 15F);
        Vec3 a = ctx.computeOnClient(mc -> mc.player.position());
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(16);
        shot(ctx, "m_run");
        ctx.waitTicks(14);
        Vec3 b = ctx.computeOnClient(mc -> mc.player.position());
        sideView(ctx, server, "m_run_side");
        ctx.getInput().releaseKey(o -> o.keyUp);
        double moved = Math.hypot(b.x - a.x, b.z - a.z);
        check(moved > 8, String.format(Locale.ROOT, "rode %.1f blocks in 30 ticks (speed 0.32)", moved));
        float walk = ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof RpgMount m ? m.walkAnimation.speed() : -1F);
        LOG.info("[mount-gallery] walk animation speed after run {}", walk);
        ctx.waitTicks(10);
    }

    private void jump(ClientGameTestContext ctx, TestServerContext server) {
        ensureRiding(ctx, server);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        look(ctx, 0F, 10F);
        ctx.waitTicks(5);
        double ground = ctx.computeOnClient(mc -> mc.player.getVehicle().getY());
        ctx.getInput().holdKey(o -> o.keyJump);
        ctx.waitTicks(14);
        float scale = ctx.computeOnClient(mc -> mc.player.getJumpRidingScale());
        boolean bar = ctx.computeOnClient(mc -> mc.player.jumpableVehicle() != null);
        check(bar && scale > 0.5F, "jump bar charging (" + scale + ")");
        shot(ctx, "m_jump_charge");
        ctx.getInput().releaseKey(o -> o.keyJump);
        double top = ground;
        for (int i = 0; i < 16; i++) {
            ctx.waitTick();
            double y = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? 0 : mc.player.getVehicle().getY());
            top = Math.max(top, y);
            if (i == 5) shot(ctx, "m_jump_air");
        }
        check(top - ground > 1.5, String.format(Locale.ROOT, "power jump rose %.2f blocks", top - ground));
        ctx.waitTicks(20);
    }

    /** 앞에 한 칸 높이 턱 → 앞으로만 달려도 올라감 (오르는 높이 1.0625) */
    private void step(ClientGameTestContext ctx, TestServerContext server) {
        ensureRiding(ctx, server);
        Vec3 at = ctx.computeOnClient(mc -> mc.player.getVehicle().position());
        server.runOnServer(s -> {
            BlockPos base = BlockPos.containing(at.x, at.y, at.z + 4);
            for (int dx = -3; dx <= 3; dx++) for (int dz = 0; dz < 8; dz++) s.overworld().setBlockAndUpdate(base.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
        });
        look(ctx, 0F, 10F);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        ctx.waitTicks(4);
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(20);
        ctx.getInput().releaseKey(o -> o.keyUp);
        double y = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? at.y : mc.player.getVehicle().getY());
        check(Math.abs(y - (at.y + 1)) < 0.05, String.format(Locale.ROOT, "walked up a 1-block step without jumping (%.2f → %.2f)", at.y, y));
        shot(ctx, "m_step");
        home(server, 0F, 0F);
        ctx.waitTicks(10);
    }

    // ------------------------------------------------------------ 탄 동안 막기

    private void blocked(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ensureRiding(ctx, server);
        look(ctx, 0F, 10F);
        Vec3 at = ctx.computeOnClient(mc -> mc.player.position());
        server.runOnServer(s -> {
            Husk z = EntityTypes.HUSK.create(s.overworld(), EntitySpawnReason.COMMAND);
            z.snapTo(at.x, at.y - 1, at.z + 3.5, 180F, 0F);
            z.setNoAi(true);
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            z.setHealth(200);
            s.overworld().addFreshEntity(z);
        });
        ctx.waitTicks(10);
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, 0F, 20F);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyAttack);
        conn.waitForServerboundPackets();
        ctx.waitTicks(4);
        shot(ctx, "m_blocked");
        float hp = server.computeOnServer(s -> {
            List<Husk> zs = s.overworld().getEntitiesOfClass(Husk.class, new AABB(at, at).inflate(8));
            return zs.isEmpty() ? -1F : zs.get(0).getHealth();
        });
        check(hp == 200F, "attack while riding does nothing (husk " + hp + ")");
        server.runOnServer(s -> s.overworld().getEntitiesOfClass(Husk.class, new AABB(at, at).inflate(8)).forEach(z -> z.discard()));
    }

    // ------------------------------------------------------------ 탈것 고르기 (Shift + 탈것 칸)

    private void select(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ensureRiding(ctx, server);
        server.runOnServer(s -> {
            MountDefs.add(new MountDefs.Def("test_wolf", "불꽃 늑대 (시험)", "rpg:dire_wolf_mount", "", false, new double[] {1.0, 0.45, 0.15}, "minecraft:lava_particle",
                    List.of(new MountDefs.Sound("mob.wolf.big.growl", 0.7, 1.0)), List.of(new MountDefs.Sound("mob.wolf.big.whine", 0.9, 0.8)), List.of(), null));
            Mounts.unlock(player(s), "test_wolf");
        });
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        openStats(ctx);
        ctx.waitTicks(4);
        hover(ctx, bottom(2));
        shot(ctx, "m_select_tip");
        shiftClick(ctx, conn, bottom(2));
        boolean menu = waitScreen(ctx, sc -> sc instanceof MenuFormScreen, "mount select window");
        if (!menu) return;
        away(ctx);
        shot(ctx, "m_select_window");
        int before = ctx.computeOnClient(mc -> mc.player.getVehicle() == null ? -1 : mc.player.getVehicle().getId());
        int idx = List.copyOf(MountDefs.all().keySet()).indexOf("test_wolf");
        click(ctx, conn, sc -> ((MenuFormScreen) sc).buttonCenter(idx));
        boolean back = waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window after choosing");
        String cur = server.computeOnServer(s -> Mounts.current(player(s)));
        check("test_wolf".equals(cur), "chose the test mount (" + cur + ")");
        boolean swapped = waitFor(ctx, mc -> mc.player.getVehicle() instanceof RpgMount m && m.getId() != before, 20);
        check(swapped, "riding → swapped to the new mount");
        if (back) {
            away(ctx);
            shot(ctx, "m_select_stats");
        }
        closeScreen(ctx);
        ctx.waitTicks(2);
        shot(ctx, "m_select_swapped");
        // 다시 다이어울프로 (명령어 · 창)
        server.runOnServer(s -> Mounts.select(player(s), "dire_wolf"));
        server.runOnServer(s -> {
            Mounts.lock(player(s), "test_wolf");
            MountDefs.remove("test_wolf");
        });
    }

    // ------------------------------------------------------------ 내리기

    private void dismount(ClientGameTestContext ctx, TestServerContext server) {
        ensureRiding(ctx, server);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        look(ctx, 0F, 15F);
        ctx.waitTicks(10);
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(3);
        ctx.getInput().releaseKey(o -> o.keyShift);
        boolean off = waitFor(ctx, mc -> mc.player.getVehicle() == null, 20);
        check(off, "sneak → off the mount");
        ctx.waitTicks(2);
        shot(ctx, "m_gone");
        ctx.waitTicks(5);
        int left = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(RpgMount.class, new AABB(player(s).position(), player(s).position()).inflate(16)).size());
        check(left == 0, "mount despawned after dismount (" + left + " left)");
        long cd = server.computeOnServer(s -> Mounts.cooldownLeft(player(s)));
        check(cd > 0, "summon cooldown after dismount (" + cd + " ticks)");
    }

    // ------------------------------------------------------------ 탈것 아이템

    private void item(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Mounts.dismiss(sp);
            // 다이어울프를 처음부터 가진 탈것이 아닌 것처럼 (아이템으로 얻는 흐름을 보려고)
            MountDefs.Def d = MountDefs.get("dire_wolf");
            MountDefs.add(new MountDefs.Def(d.key(), d.name(), d.entity(), d.item(), false, d.color(), d.particle(), d.summon(), d.gone(), d.desc(), d.seatLift()));
            sp.getInventory().setItem(0, new ItemStack(MountItems.of("dire_wolf"), 2));
            sp.getInventory().setSelectedSlot(0);
        });
        home(server, 0F, 10F);
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.waitTicks(10);
        boolean owned0 = server.computeOnServer(s -> Mounts.owns(player(s), "dire_wolf"));
        check(!owned0, "dire wolf not owned before the item");
        shot(ctx, "m_item_hand");
        // 가방에서 풀이
        ctx.getInput().pressKey(o -> o.keyInventory);
        if (waitScreen(ctx, sc -> sc instanceof InventoryScreen, "inventory")) {
            hover(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 8, (sc.height - 166) / 2 + 142 + 8});
            shot(ctx, "m_item_tooltip");
            closeScreen(ctx);
        }
        ctx.getInput().pressKey(o -> o.keyUse);
        conn.waitForServerboundPackets();
        ctx.waitTicks(6);
        shot(ctx, "m_item_used");
        boolean owned1 = server.computeOnServer(s -> Mounts.owns(player(s), "dire_wolf"));
        int count = server.computeOnServer(s -> player(s).getInventory().getItem(0).getCount());
        check(owned1 && count == 1, "used the mount item → owned, 1 item used (" + count + " left)");
        ctx.waitTicks(30);
        ctx.getInput().pressKey(o -> o.keyUse);
        conn.waitForServerboundPackets();
        ctx.waitTicks(4);
        int count2 = server.computeOnServer(s -> player(s).getInventory().getItem(0).getCount());
        check(count2 == 1, "already owned → item kept (" + count2 + ")");
        shot(ctx, "m_item_owned");
        server.runOnServer(s -> {
            MountDefs.Def d = MountDefs.get("dire_wolf");
            MountDefs.add(new MountDefs.Def(d.key(), d.name(), d.entity(), d.item(), true, d.color(), d.particle(), d.summon(), d.gone(), d.desc(), d.seatLift()));
            player(s).getInventory().setItem(0, ItemStack.EMPTY);
        });
    }

    // ------------------------------------------------------------ 엔더 상자 · 퀘스트 목록

    private void ender(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 12));
            sp.getEnderChestInventory().setItem(4, new ItemStack(Items.EMERALD, 30));
            sp.getEnderChestInventory().setItem(13, new ItemStack(MountItems.of("dire_wolf"), 1));
        });
        home(server, 0F, 20F);
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        openStats(ctx);
        click(ctx, conn, bottom(1));
        boolean open = waitScreen(ctx, sc -> sc instanceof ContainerScreen, "ender chest window");
        if (!open) return;
        String title = ctx.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
        boolean rows = ctx.computeOnClient(mc -> mc.player.containerMenu instanceof ChestMenu cm && cm.getRowCount() == 3
                && cm.getSlot(0).getItem().is(Items.DIAMOND) && cm.getSlot(13).getItem().getCount() == 1);
        check(rows, "ender chest: 27 slots with my items (title '" + title + "')");
        away(ctx);
        shot(ctx, "m_ender");
        closeScreen(ctx);
        ctx.waitTicks(3);
        boolean closed = server.computeOnServer(s -> player(s).containerMenu == player(s).inventoryMenu);
        check(closed, "ender window closed on the server");
        shot(ctx, "m_ender_closed");
        server.runOnServer(s -> player(s).getEnderChestInventory().clearContent());
    }

    private void quest(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        openStats(ctx);
        click(ctx, conn, bottom(0));
        boolean open = waitScreen(ctx, sc -> sc instanceof mmorpg.fabric.client.form.FormScreen, "quest log");
        if (!open) return;
        away(ctx);
        shot(ctx, "m_quest_log");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        conn.waitForClientboundPackets();
        boolean back = waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window after the quest log");
        check(back, "quest log closed → back to the stat window");
        closeScreen(ctx);
    }

    // ------------------------------------------------------------ 도움

    private void ensureRiding(ClientGameTestContext ctx, TestServerContext server) {
        if (ctx.computeOnClient(mc -> mc.player.getVehicle() instanceof RpgMount)) return;
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Mounts.resetCooldown(sp);
            Mounts.summon(sp);
        });
        waitFor(ctx, mc -> mc.player.getVehicle() instanceof RpgMount, 20);
        ctx.waitTicks(20);
    }

    private void openStats(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window (K)")) ctx.setScreen(StatScreen::new);
        ctx.waitTicks(3);
    }

    private static Function<Screen, int[]> bottom(int i) {
        return sc -> ((StatScreen) sc).bottomCenter(i);
    }

    private static void camera(ClientGameTestContext ctx, CameraType t) {
        ctx.runOnClient(mc -> mc.options.setCameraType(t));
        ctx.waitTick();
    }

    private static void look(ClientGameTestContext ctx, float yaw, float pitch) {
        ctx.runOnClient(mc -> {
            LocalPlayer p = mc.player;
            p.setYRot(yaw);
            p.setXRot(pitch);
            p.yRotO = yaw;
            p.xRotO = pitch;
            p.setYHeadRot(yaw);
        });
        ctx.waitTick();
    }

    private static void home(TestServerContext server, float yaw, float pitch) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Mounts.dismiss(sp);
            Mounts.resetCooldown(sp);
        });
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", HOME.x, HOME.y, HOME.z, yaw, pitch));
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private void part(ClientGameTestContext ctx, TestServerContext server, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[mount-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[mount-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.getInput().releaseKey(o -> o.keyJump);
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[mount-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[mount-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
    }

    private static boolean waitFor(ClientGameTestContext ctx, Predicate<Minecraft> test, int ticks) {
        try {
            ctx.waitFor(mc -> mc.player != null && test.test(mc), ticks);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    private boolean waitScreen(ClientGameTestContext ctx, Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 100);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open (now " + ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()) + ")");
            return false;
        }
    }

    private static void closeScreen(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) ctx.setScreen(() -> null);
    }

    /** 지금 화면의 GUI 좌표 → 창 픽셀 좌표 */
    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen " + (sc == null ? "none" : sc.getClass().getSimpleName()));
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    /** Shift 를 누른 채 왼쪽 누르기 */
    private static void shiftClick(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().holdShift();
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.getInput().releaseShift();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }
}
