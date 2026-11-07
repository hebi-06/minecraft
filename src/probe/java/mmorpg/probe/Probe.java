package mmorpg.probe;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 모드 없는 클라이언트 시험의 사진기 (2026-11-07 1번 — CI ci/vanilla_probe.sh). Fabric API 없이 Fabric Loader 만 — 서버가 보기에 바닐라 클라이언트.
 * 서버 (모드 jar, -Dmmorpg.probe=true) 가 장면을 만들고 채팅 "[probe] …" 로 신호: shot 이름 (지금 화면) · inv 이름 (인벤토리를 열고 찍고 닫음) ·
 * cam back|first · done (닫힘). 접속이 끊기면 끊긴 화면을 찍고 닫힘. 아무 신호 없이 12분이면 찍고 닫힘. 사진은 게임 폴더 screenshots/probe_&lt;이름&gt;.png.
 */
public final class Probe implements ClientModInitializer {
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int wait;
    private static String pendingInv;
    private static int ticks;
    private static int quitIn = -1;
    private static boolean disconnectedShot;

    @Override
    public void onInitializeClient() {
        log("ready");
    }

    /** 채팅 (서버의 알림 줄) — 메인 스레드 */
    public static void chat(String text) {
        if (!text.startsWith("[probe] ")) return;
        QUEUE.add(text.substring("[probe] ".length()).trim());
    }

    /** 클라이언트 틱 끝 */
    public static void tick(Minecraft mc) {
        ticks++;
        if (quitIn >= 0 && --quitIn == 0) {
            log("quit");
            mc.stop();
            return;
        }
        if (!disconnectedShot && mc.screen instanceof DisconnectedScreen) {
            disconnectedShot = true;
            log("disconnected: " + mc.screen.getTitle().getString());
            shot(mc, "disconnected");
            quitIn = 40;
            return;
        }
        if (ticks == 12 * 60 * 20 && quitIn < 0) {
            log("timeout");
            shot(mc, "timeout");
            quitIn = 40;
        }
        if (wait > 0 && --wait > 0) return;
        if (pendingInv != null) {
            shot(mc, pendingInv);
            pendingInv = null;
            mc.setScreen(null);
            wait = 10;
            return;
        }
        String cmd = QUEUE.poll();
        if (cmd == null) return;
        log("signal " + cmd);
        if (cmd.startsWith("shot ")) {
            shot(mc, cmd.substring(5));
            wait = 5;
        } else if (cmd.startsWith("inv ") && mc.player != null) {
            mc.setScreen(new InventoryScreen(mc.player));
            pendingInv = cmd.substring(4);
            wait = 20;
        } else if (cmd.equals("cam back")) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (cmd.equals("cam first")) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else if (cmd.equals("done")) {
            log("done");
            quitIn = 40;
        }
    }

    private static void shot(Minecraft mc, String name) {
        Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve("probe_" + name + ".png");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            log("mkdir failed " + e);
        }
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage image) -> {
            try (image) {
                image.writeToFile(file);
                log("shot " + file.getFileName());
            } catch (IOException e) {
                log("shot failed " + name + " " + e);
            }
        });
    }

    static void log(String text) {
        System.out.println("[vanilla-client] " + text);
    }
}
