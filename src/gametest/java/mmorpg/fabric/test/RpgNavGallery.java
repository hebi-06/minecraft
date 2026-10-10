package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Props;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.client.NavHud;
import mmorpg.fabric.client.form.DialogFormScreen;
import mmorpg.fabric.nav.Nav;
import mmorpg.fabric.nav.NavMath;
import mmorpg.fabric.nav.NavNet;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.Quests;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 길 안내 사진첩 (화면 시험 — MMORPG_ONLY=nav): 메인 이야기 (할아버지 → 훈련 조교 브란, 금빛) 를 받은 뒤 화면 위 가운데 화살표 칸 · 바닥 빛 화살표 · 빛기둥,
 * 몸을 돌리면 화살표가 바로 따라 돎 (클라이언트 계산), 브란 곁에 가면 도착 (핀 그림 · 고리 · 반짝임), 만든 퀘스트 (일반 하늘빛), 무기 시험 → 허수아비,
 * 큰 글자 (제목) · 창이 열리면 숨김, /nav off 면 숨김 · /길안내 on 이면 다시.
 * 사진 이름 n_… → ci-logs 의 shots/. 확인 항목은 로그 [nav-gallery] OK / FAIL (사진첩이라 시험을 실패시키지는 않음).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgNavGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-nav-gallery");
    /** 플레이어 자리 (평평한 세계 땅 위 y = -60, 남쪽을 봄) */
    private static final Vec3 HOME = new Vec3(0.5, -60, 0.5);
    private static final Vec3 BRAN_AT = new Vec3(8.5, -60, 26.5);

    private final List<String> failures = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("nav")) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만 · 고른 시험만 (MMORPG_ONLY)
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            home(server, 0F, 0F);
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            part(ctx, server, "main", () -> mainStory(ctx, server));
            part(ctx, server, "common", () -> commonQuest(ctx, server));
            part(ctx, server, "dummy", () -> dummy(ctx, server));
            part(ctx, server, "hide", () -> hide(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[nav-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 메인 이야기 (금빛)

    /** 할아버지 하랄의 "모험의 꿈" 을 받은 뒤 → 훈련 조교 브란에게 (남동쪽 27칸): 화살표 · 바닥 빛 · 빛기둥 → 몸 돌리기 → 도착 */
    private void mainStory(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Vec3 eye = sp.getEyePosition();
            spawned.add(mainNpc(sp.level(), "main_1", new Vec3(-2.5, -60, 2.5), eye));
            spawned.add(mainNpc(sp.level(), "main_2", BRAN_AT, eye));
            setQuests(sp, "{\"" + MainQuests.MAIN_START + "\":{\"s\":\"a\",\"n\":0,\"c\":0}}", MainQuests.MAIN_START);
        });
        if (!waitHud(ctx, p -> p.visible() && p.mode() == NavNet.NavPayload.ARROW && plainLabel(p).contains("훈련 조교 브란"), "main story arrow")) return;
        NavNet.NavPayload p = ctx.computeOnClient(mc -> NavHud.state());
        check(p.frame() == 1, "main quest → gold frame (" + p.frame() + ")");
        check(Math.abs(p.x() - BRAN_AT.x) < 0.11 && Math.abs(p.z() - BRAN_AT.z) < 0.11, "target = Bran (" + p.x() + ", " + p.z() + ")");
        ctx.waitTicks(4);
        checkSector(ctx, "facing south, Bran south-south-east");
        String text = ctx.computeOnClient(mc -> NavHud.lastText());
        check(BedrockText.plain(text).matches("훈련 조교 브란에게 · \\d+칸"), "row text with distance: " + BedrockText.plain(text));
        check(ctx.computeOnClient(mc -> NavHud.lastIcon()).startsWith("a_m"), "gold arrow icon " + ctx.computeOnClient(mc -> NavHud.lastIcon()));
        shot(ctx, "n_main_far");
        // 바닥 빛 화살표가 보이게 아래를 봄 (2초마다 차례로 켜짐)
        look(ctx, 0F, 28F);
        waitWave(ctx, server);
        shot(ctx, "n_main_ground");
        // 몸을 동쪽으로 → 서버 왕복 없이 화살표가 오른쪽 아래 (브란은 남동쪽)
        look(ctx, -90F, 0F);
        ctx.waitTicks(2);
        checkSector(ctx, "turned east");
        int s1 = ctx.computeOnClient(mc -> NavHud.sector());
        check(s1 >= 2 && s1 <= 4, "arrow turned right-ish when facing east (sector " + s1 + ")");
        shot(ctx, "n_main_turn");
        look(ctx, 90F, 0F);
        ctx.waitTicks(2);
        int s2 = ctx.computeOnClient(mc -> NavHud.sector());
        check(s2 >= 10 && s2 <= 12, "arrow turned left-behind when facing west (sector " + s2 + ")");
        shot(ctx, "n_main_turn_west");
        // 3인칭 (뒤): 브란 쪽 (yaw -17) — 빛기둥 · 바닥 빛
        look(ctx, -17F, 8F);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        waitWave(ctx, server);
        shot(ctx, "n_main_3p");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // 브란 곁 (북쪽 2칸) 으로 → 도착 (핀 그림 · "여기!" · 고리 · 반짝임)
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 10", BRAN_AT.x, BRAN_AT.y, BRAN_AT.z - 2.2));
        if (waitHud(ctx, q -> q.mode() == NavNet.NavPayload.HERE, "arrived at Bran")) {
            ctx.waitTicks(3);
            check(BedrockText.plain(ctx.computeOnClient(mc -> NavHud.lastText())).endsWith("· 여기!") && ctx.computeOnClient(mc -> NavHud.lastIcon()).equals("a_mhr"),
                    "arrived row: " + BedrockText.plain(ctx.computeOnClient(mc -> NavHud.lastText())) + " / " + ctx.computeOnClient(mc -> NavHud.lastIcon()));
            shot(ctx, "n_main_arrived");
        }
        clearSpawned(server);
        home(server, 0F, 0F);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 만든 퀘스트 (일반 하늘빛)

    /** 만든 말 걸기 퀘스트 (촌장 → 약초꾼 리브, 남서쪽): 하늘빛 테두리 · 화살표 */
    private void commonQuest(ClientGameTestContext ctx, TestServerContext server) {
        String id = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            Vec3 eye = sp.getEyePosition();
            Mannequin giver = npc(sp.level(), new Vec3(2.5, -60, 3.5), eye, "촌장");
            Mannequin herb = npc(sp.level(), new Vec3(-15.5, -60, 14.5), eye, "약초꾼 리브");
            spawned.add(giver);
            spawned.add(herb);
            Quest q = new Quest();
            q.giver = NpcData.key(giver);
            q.title = "약초 전해 주기";
            q.type = "talk";
            q.target = NpcData.key(herb);
            String qid = Quests.saveQuest(q).id;
            setQuests(sp, "{\"" + qid + "\":{\"s\":\"a\",\"n\":0,\"c\":0}}", qid);
            return qid;
        });
        if (waitHud(ctx, p -> p.visible() && p.frame() == 2 && plainLabel(p).contains("약초꾼 리브"), "common quest arrow")) {
            ctx.waitTicks(4);
            checkSector(ctx, "common quest");
            check(ctx.computeOnClient(mc -> NavHud.lastIcon()).startsWith("a_c"), "sky-blue arrow icon " + ctx.computeOnClient(mc -> NavHud.lastIcon()));
            look(ctx, 0F, 22F);
            waitWave(ctx, server);
            shot(ctx, "n_common");
        }
        server.runOnServer(s -> Quests.deleteQuest(id));
        clearSpawned(server);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 무기 시험 → 허수아비

    private void dummy(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("summon rpg:training_dummy 5.5 -60 9.5 {Rotation:[180f,0f]}");
        server.runOnServer(s -> setQuests(player(s), "{\"" + MainQuests.MAIN_START + "\":{\"s\":\"d\",\"n\":0,\"c\":1},\"" + MainQuests.MAIN_TRIAL
                + "\":{\"s\":\"a\",\"n\":0,\"c\":0,\"t\":[]}}", MainQuests.MAIN_TRIAL));
        look(ctx, 0F, 12F);
        if (waitHud(ctx, p -> p.visible() && p.mode() == NavNet.NavPayload.ARROW && plainLabel(p).startsWith("무기 시험"), "weapon trial → dummy")) {
            NavNet.NavPayload p = ctx.computeOnClient(mc -> NavHud.state());
            check(Math.abs(p.x() - 5.5) < 0.11 && Math.abs(p.z() - 9.5) < 0.11, "target = dummy (" + p.x() + ", " + p.z() + ")");
            ctx.waitTicks(4);
            checkSector(ctx, "dummy");
            waitWave(ctx, server);
            shot(ctx, "n_dummy");
        }
        server.runCommand("kill @e[type=rpg:training_dummy]");
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 숨김: 큰 글자 · 창 · /nav off

    private void hide(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Vec3 eye = sp.getEyePosition();
            spawned.add(mainNpc(sp.level(), "main_2", BRAN_AT, eye));
            setQuests(sp, "{\"" + MainQuests.MAIN_START + "\":{\"s\":\"a\",\"n\":0,\"c\":0}}", MainQuests.MAIN_START);
        });
        look(ctx, 0F, 0F);
        if (!waitHud(ctx, p -> p.visible() && p.mode() == NavNet.NavPayload.ARROW, "arrow before hiding")) return;
        ctx.waitTicks(3);
        check(!ctx.computeOnClient(mc -> NavHud.lastText()).isEmpty(), "row drawn");
        // 큰 글자 (제목) 가 떠 있는 동안 숨김
        server.runCommand("title @a times 10 60 20");
        server.runCommand("title @a title \"길 안내\"");
        server.runCommand("title @a subtitle \"큰 글자가 떠 있으면 칸을 숨김\"");
        ctx.waitTicks(15);
        check(ctx.computeOnClient(mc -> NavHud.titleShowing()) && ctx.computeOnClient(mc -> NavHud.lastText()).isEmpty(), "hidden while a title is shown");
        shot(ctx, "n_hidden_title");
        server.runCommand("title @a clear");
        ctx.waitTicks(3);
        check(!ctx.computeOnClient(mc -> NavHud.titleShowing()) && !ctx.computeOnClient(mc -> NavHud.lastText()).isEmpty(), "back after the title is cleared");
        // 창 (퀘스트 목록) 이 열린 동안 숨김
        server.runCommand("execute as @a run quest");
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof DialogFormScreen, 60);
            ctx.waitTicks(3);
            check(ctx.computeOnClient(mc -> NavHud.lastText()).isEmpty(), "hidden while a form is open");
            shot(ctx, "n_hidden_form");
        } catch (AssertionError | RuntimeException e) {
            check(false, "quest log did not open: " + e.getMessage());
        }
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        conn.waitForServerboundPackets();
        ctx.waitTicks(5);
        check(!ctx.computeOnClient(mc -> NavHud.lastText()).isEmpty(), "back after the form is closed");
        // /nav off → 숨김, /길안내 on → 다시
        server.runCommand("execute as @a run nav off");
        boolean off = waitHud(ctx, p -> !p.visible(), "hidden after /nav off");
        check(off && server.computeOnServer(s -> Quests.navOf(player(s)).off()), "/nav off");
        ctx.waitTicks(2);
        shot(ctx, "n_nav_off");
        server.runCommand("execute as @a run 길안내 on");
        check(waitHud(ctx, p -> p.visible() && p.mode() == NavNet.NavPayload.ARROW, "shown after /길안내 on"), "/길안내 on");
        ctx.waitTicks(3);
        shot(ctx, "n_nav_on");
        clearSpawned(server);
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void home(TestServerContext server, float yaw, float pitch) {
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", HOME.x, HOME.y, HOME.z, yaw, pitch));
    }

    /** 플레이어 진행을 이것으로 바꾸고 그 퀘스트를 길 안내로 고름 (서버 스레드) */
    private static void setQuests(ServerPlayer sp, String json, String pin) {
        Props.set(sp, "rpg:qs", json);
        Quests.forgetQuestPlayer(sp.getUUID());
        Quests.setNavOff(sp, false);
        Quests.pinQuest(sp, pin);
    }

    private static Mannequin npc(ServerLevel level, Vec3 at, Vec3 face, String name) {
        Mannequin m = NpcData.spawnAt(level, at, face);
        NpcData.setName(m, name);
        NpcData.refresh(m, 0);
        return m;
    }

    /** 메인 퀘스트 NPC (번호 · 이름 · 세운 기록) */
    private static Mannequin mainNpc(ServerLevel level, String key, Vec3 at, Vec3 face) {
        Mannequin m = NpcData.spawnAt(level, at, face);
        NpcData.setKey(m, key);
        NpcData.setName(m, MainQuests.mainNpcByKey(key).name());
        NpcData.refresh(m, 0);
        NpcData.setMainRecord(key, m, null, null);
        return m;
    }

    private void clearSpawned(TestServerContext server) {
        List<Entity> list = List.copyOf(spawned);
        spawned.clear();
        server.runOnServer(s -> {
            for (Entity e : list) e.discard();
        });
    }

    private static String plainLabel(NavNet.NavPayload p) {
        return BedrockText.plain(p.label());
    }

    /** 보는 방향 (도) — 클라이언트에서 바로 (서버 왕복 없이 화살표가 바뀌는지 보려고) */
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

    /**
     * 다음 바닥 빛 화살표 (2초마다) 가 다섯 개 다 켜진 때 (켜진 뒤 10틱 — i 번째는 2.4i 틱부터 12틱 동안) 까지 기다림 (서버 틱으로 맞춤 — Nav.nextWave)
     */
    private static void waitWave(ClientGameTestContext ctx, TestServerContext server) {
        long due = server.computeOnServer(s -> Nav.nextWave(player(s)));
        long now = server.computeOnServer(s -> Tasks.now());
        if (due < 0) {
            ctx.waitTicks(10);
            return;
        }
        long fire = due > now ? due : now + 2; // 지난 틱이면 다음 길 안내 틱 (2틱마다) 에 켜짐
        ctx.waitTicks((int) Math.max(1, Math.min(60, fire + 10 - now)));
    }

    /** 클라이언트 칸 패킷이 조건에 맞을 때까지 (100틱) */
    private boolean waitHud(ClientGameTestContext ctx, Predicate<NavNet.NavPayload> test, String what) {
        try {
            ctx.waitFor(mc -> NavHud.state() != null && test.test(NavHud.state()), 100);
            return true;
        } catch (AssertionError | RuntimeException e) {
            NavNet.NavPayload p = ctx.computeOnClient(mc -> NavHud.state());
            check(false, what + " (now " + (p == null ? "none" : BedrockText.plain(p.text()) + " mode " + p.mode() + " visible " + p.visible()) + ")");
            return false;
        }
    }

    /** 화면 칸의 화살표가 지금 내 자리 · 보는 방향에서 계산한 칸과 같은지 (경계 머무름 4° 안이면 옆 칸도) */
    private void checkSector(ClientGameTestContext ctx, String what) {
        int[] r = ctx.computeOnClient(mc -> {
            NavNet.NavPayload p = NavHud.state();
            LocalPlayer pl = mc.player;
            if (p == null || pl == null) return new int[] {-2, -2};
            int want = NavMath.sectorOf(NavMath.facing(pl.getViewVector(1F), pl.getYRot()), pl.position(), new Vec3(p.x(), p.y(), p.z()));
            return new int[] {want, NavHud.sector()};
        });
        int diff = Math.floorMod(r[0] - r[1], 16);
        check(r[1] >= 0 && (diff == 0 || diff == 1 || diff == 15), what + ": arrow sector " + r[1] + " (expected " + r[0] + ")");
    }

    private void part(ClientGameTestContext ctx, TestServerContext server, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[nav-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[nav-gallery] part " + name, e);
        }
        try {
            clearSpawned(server);
            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.waitTicks(5);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[nav-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[nav-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    /** 사진 (알림 토스트는 치움) */
    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }
}
