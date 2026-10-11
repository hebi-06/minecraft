package mmorpg.fabric.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.client.TitleFit;
import mmorpg.fabric.client.form.DialogFormScreen;
import mmorpg.fabric.client.form.MenuFormScreen;
import mmorpg.fabric.duel.DuelSystem;
import mmorpg.fabric.duel.Duels;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 6단계 결투 사진첩 (화면 시험 — MMORPG_ONLY=pvp): 서버에 두 번째 플레이어 ("Rival" — 가짜 연결) 를 넣고
 * /pvp Rival (실제 명령 보내기) → 상대가 수락 → 3 · 2 · 1 (제목 · 붉은 마법진 · 머리 위 결투 표식) → 시작 (충격파) → 실제 공격 (피해 90% 감소 · 빨간 피해 숫자) →
 * 상대에게 맞음 (내 체력 바) → 상태 줄 (상대 이름 · 남은 체력) → 쓰러뜨림 (승리 — 금빛 · 상대는 체력 1) → 상대가 신청 (내 화면 아래 대화창 → 실제 클릭 수락) →
 * /pvp 끝 (결투 끝), /pvp (결투 창 — 접속한 사람 목록). 사진 이름 d_… → ci-logs 의 shots/. 확인 항목은 로그 [pvp-gallery] OK / FAIL.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgDuelGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-pvp-gallery");
    private static final Vec3 HOME = new Vec3(0.5, -60, 0.5);
    private static final Vec3 RIVAL_AT = new Vec3(0.5, -60, 3.5);
    static final int COUNTDOWN = (int) Rules.get().obj("PVP").num("countdownTicks");

    private final List<String> failures = new ArrayList<>();
    private UUID rivalId;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("pvp")) return;
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
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 0", HOME.x, HOME.y, HOME.z));
            ctx.waitTicks(5);
            server.runOnServer(s -> {
                ServerPlayer me = s.getPlayerList().getPlayers().get(0);
                me.setExperienceLevels(30);
                RpgPlayers.checkLevelUp(me);
                Weapon w = Weapons.weaponFrom("sword", 30, "hero");
                for (Weapons.Req r : w.reqs()) RpgPlayers.setStat(me, r.stat(), (int) Math.ceil(r.value()) + 10);
                me.getInventory().setItem(0, new ItemStack(RpgItems.byId(w.id())));
                me.getInventory().setSelectedSlot(0);
                ServerPlayer r = rival(s, "Rival", RIVAL_AT, 180F);
                rivalId = r.getUUID();
                Forms.HEADLESS.add(rivalId);
                s.getGameRules().set(GameRules.PVP, false, s); // 꺼진 세계: 결투 동안만 켜짐
            });
            ctx.waitTicks(40);
            boolean seen = ctx.computeOnClient(mc -> mc.level.players().stream().anyMatch(p -> p.getUUID().equals(rivalId)));
            check(seen, "rival player visible to the client");
            part(ctx, "request", () -> request(ctx, server));
            part(ctx, "fight", () -> fight(ctx, server, conn));
            part(ctx, "win", () -> win(ctx, server));
            part(ctx, "incoming", () -> incoming(ctx, server, conn));
            part(ctx, "end", () -> end(ctx, server));
            part(ctx, "menu", () -> menu(ctx, server, conn));
            server.runOnServer(s -> {
                ServerPlayer r = s.getPlayerList().getPlayer(rivalId);
                if (r != null) s.getPlayerList().remove(r);
                Forms.HEADLESS.remove(rivalId);
            });
            ctx.setScreen(() -> null);
        }
        LOG.info("[pvp-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 신청 → 수락 → 3 · 2 · 1 → 시작

    private void request(ClientGameTestContext ctx, TestServerContext server) {
        look(ctx, 0F, 8F);
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("pvp Rival"));
        boolean asked = waitServer(ctx, server, s -> DuelSystem.hasRequest(rival(s)), 40);
        check(asked, "/pvp Rival → request (dialog for the rival)");
        if (!asked) return;
        server.runOnServer(s -> Forms.answer(rival(s), 0, null));
        ctx.waitTicks(3);
        boolean on = server.computeOnServer(s -> Duels.inDuel(me(s).getUUID()) && s.getGameRules().get(GameRules.PVP));
        check(on, "accepted → duel (pvp rule turned on for the duel)");
        String t3 = ctx.computeOnClient(mc -> TitleFit.titleText());
        check(t3.contains("3"), "countdown title '" + t3 + "'");
        shot(ctx, "d_count_3");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        ctx.waitTicks(20);
        shot(ctx, "d_count_2_3p");
        camera(ctx, CameraType.FIRST_PERSON);
        ctx.waitTicks(COUNTDOWN - 20);
        String go = ctx.computeOnClient(mc -> TitleFit.titleText());
        check(go.contains("결투 시작"), "start title '" + go + "'");
        shot(ctx, "d_start");
        boolean started = server.computeOnServer(s -> Duels.isPartner(me(s), rival(s)));
        check(started, "duel started after the countdown");
    }

    // ------------------------------------------------------------ 싸움

    private void fight(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        float max = server.computeOnServer(s -> rival(s).getMaxHealth());
        float r0 = server.computeOnServer(s -> rival(s).getHealth());
        look(ctx, 0F, 12F);
        ctx.waitTicks(25);
        ctx.getInput().pressKey(o -> o.keyAttack);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        shot(ctx, "d_hit");
        float r1 = server.computeOnServer(s -> rival(s).getHealth());
        check(r1 < r0 && r1 > 0, String.format(Locale.ROOT, "my sword hit the rival (%.1f → %.1f of %.1f)", r0, r1, max));
        // 상대에게 맞음 (피해 40 → × 0.1)
        float m0 = server.computeOnServer(s -> me(s).getHealth());
        server.runOnServer(s -> {
            ServerPlayer me = me(s);
            me.setInvulnerableTime(0);
            me.hurtServer(me.level(), me.damageSources().playerAttack(rival(s)), 40F);
        });
        ctx.waitTicks(3);
        float m1 = server.computeOnServer(s -> me(s).getHealth());
        double taken = Rules.get().obj("CONFIG").num("DAMAGE_TAKEN_SCALE");
        check(Math.abs((m0 - m1) - 40 * taken * Duels.PVP.num("mult")) < 0.05, String.format(Locale.ROOT, "rival's hit scaled: lost %.2f", m0 - m1));
        camera(ctx, CameraType.THIRD_PERSON_FRONT);
        ctx.waitTicks(2);
        shot(ctx, "d_hit_back");
        camera(ctx, CameraType.FIRST_PERSON);
        // 상태 줄: 상대 이름 · 남은 체력 (들고 있는 것이 초월 무기 · 룬이 아니면 덧붙임만)
        ctx.waitTicks(30);
        shot(ctx, "d_hud");
    }

    private void win(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer r = rival(s);
            r.setInvulnerableTime(0);
            r.hurtServer(r.level(), r.damageSources().playerAttack(me(s)), 100000F);
        });
        ctx.waitTicks(4);
        String t = ctx.computeOnClient(mc -> TitleFit.titleText());
        check(t.contains("승리"), "victory title '" + t + "'");
        shot(ctx, "d_win");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        ctx.waitTicks(2);
        shot(ctx, "d_win_3p");
        camera(ctx, CameraType.FIRST_PERSON);
        boolean over = server.computeOnServer(s -> !Duels.inDuel(me(s).getUUID()) && rival(s).isAlive() && rival(s).getHealth() <= 1.001F
                && !s.getGameRules().get(GameRules.PVP));
        check(over, "rival floored at 1 hp, duel over, pvp rule restored");
        server.runOnServer(s -> {
            rival(s).setHealth(rival(s).getMaxHealth());
            me(s).setHealth(me(s).getMaxHealth());
        });
        ctx.waitTicks(30);
    }

    // ------------------------------------------------------------ 받은 신청 (내 화면 대화창)

    private void incoming(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            DuelSystem.resetRequests();
            DuelSystem.requestTo(rival(s), me(s));
        });
        boolean dialog = waitScreen(ctx, sc -> sc instanceof DialogFormScreen, "duel request dialog");
        if (!dialog) return;
        away(ctx);
        shot(ctx, "d_request_dialog");
        click(ctx, conn, sc -> ((DialogFormScreen) sc).choiceCenter(0));
        boolean on = waitServer(ctx, server, s -> Duels.inDuel(me(s).getUUID()), 20);
        check(on, "clicked accept → duel");
        ctx.waitTicks(2);
        shot(ctx, "d_accepted");
        ctx.waitTicks(COUNTDOWN + 25);
        shot(ctx, "d_marks");
    }

    private void end(ClientGameTestContext ctx, TestServerContext server) {
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("pvp 끝"));
        boolean off = waitServer(ctx, server, s -> !Duels.inDuel(me(s).getUUID()), 20);
        check(off, "/pvp 끝 → duel over");
        ctx.waitTicks(3);
        String t = ctx.computeOnClient(mc -> TitleFit.titleText());
        check(t.contains("결투 끝"), "end title '" + t + "'");
        shot(ctx, "d_end");
    }

    private void menu(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.waitTicks(40);
        ctx.runOnClient(mc -> mc.player.connection.sendCommand("pvp"));
        boolean open = waitScreen(ctx, sc -> sc instanceof MenuFormScreen, "duel window");
        if (!open) return;
        away(ctx);
        shot(ctx, "d_menu");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 두 번째 플레이어 (가짜 연결 — GameTestHelper 의 시험 플레이어와 같게)

    static ServerPlayer rival(MinecraftServer s, String name, Vec3 at, float yaw) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("mmorpg-gallery:" + name).getBytes(StandardCharsets.UTF_8)), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(s, s.overworld(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        s.getPlayerList().placeNewPlayer(connection, p, cookie);
        try {
            Method m = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            m.setAccessible(true);
            m.invoke(p.connection);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot mark the rival loaded", e);
        }
        p.setGameMode(GameType.SURVIVAL);
        p.snapTo(at.x, at.y, at.z, yaw, 0F);
        p.setYHeadRot(yaw);
        p.setYBodyRot(yaw);
        RpgPlayers.onJoin(p);
        return p;
    }

    private ServerPlayer rival(MinecraftServer s) {
        return s.getPlayerList().getPlayer(rivalId);
    }

    private ServerPlayer me(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (!p.getUUID().equals(rivalId)) return p;
        throw new IllegalStateException("no client player");
    }

    // ------------------------------------------------------------ 도움

    private boolean waitServer(ClientGameTestContext ctx, TestServerContext server, Predicate<MinecraftServer> test, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (server.computeOnServer(test::test)) return true;
            ctx.waitTick();
        }
        return server.computeOnServer(test::test);
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

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[pvp-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[pvp-gallery] part " + name, e);
        }
        try {
            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(5);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[pvp-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[pvp-gallery] {} {}", ok ? "OK  " : "FAIL", what);
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

    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
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
}
