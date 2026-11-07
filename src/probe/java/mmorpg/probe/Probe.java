package mmorpg.probe;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 모드 없는 클라이언트 시험의 사진기 (2026-11-07 1번 — CI ci/vanilla_probe.sh). Fabric API 없이 Fabric Loader 만 — 서버가 보기에 바닐라 클라이언트.
 * 서버 (모드 jar, -Dmmorpg.probe=true) 가 장면을 만들고 채팅 "[probe] …" 로 신호: shot 이름 (지금 화면) · inv 이름 (인벤토리를 열고 찍고 닫음) ·
 * cam back|first · key f (F 키 패킷) · key N (숫자 키 N — 고른 칸) · shift on|off (Shift 누름) · slot N · shiftslot N (상자 메뉴 칸 누름 · Shift) ·
 * hover N (마우스를 칸 N 위로 — 설명 상자) ·
 * close (상자 메뉴 닫기) · click id sel (대화창 단추) ·
 * submit id (입력창 확인) ·
 * done (닫힘). 접속이 끊기면 끊긴 화면을 찍고 닫힘. 아무 신호 없이 12분이면 찍고 닫힘. 사진은 게임 폴더 screenshots/probe_&lt;이름&gt;.png.
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
        if (!disconnectedShot && mc.gui.screen() instanceof DisconnectedScreen) {
            disconnectedShot = true;
            log("disconnected: " + mc.gui.screen().getTitle().getString());
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
            mc.gui.setScreen(null);
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
            mc.gui.setScreen(new InventoryScreen(mc.player));
            pendingInv = cmd.substring(4);
            wait = 20;
        } else if (cmd.equals("cam back")) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (cmd.equals("cam first")) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else if (cmd.startsWith("shiftslot ") && mc.player != null && mc.gameMode != null) {
            // Shift + 좌클릭 (빠르게 옮기기 — 마우스로 누를 때와 같은 패킷)
            int n = Integer.parseInt(cmd.substring(10).trim());
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, n, 0, ContainerInput.QUICK_MOVE, mc.player);
        } else if (cmd.startsWith("slot ") && mc.player != null && mc.gameMode != null) {
            // 상자 메뉴 칸을 왼쪽 클릭 (마우스로 누를 때와 같은 패킷)
            int n = Integer.parseInt(cmd.substring(5).trim());
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, n, 0, ContainerInput.PICKUP, mc.player);
        } else if (cmd.startsWith("hover ") && mc.player != null) {
            // 마우스를 상자 메뉴 칸 N 의 가운데로 (설명 상자 사진 — 창 자리는 바닐라 상자 화면처럼 가운데)
            int n = Integer.parseInt(cmd.substring(6).trim());
            var menu = mc.player.containerMenu;
            if (n < 0) {
                mc.mouseHandler.onMove(mc.getWindow().handle(), 0, 0, 0, 0); // 치움 (왼쪽 위 구석)
            } else if (n < menu.slots.size()) {
                int rows = menu instanceof net.minecraft.world.inventory.ChestMenu c ? c.getRowCount() : 3;
                var win = mc.getWindow();
                int left = (win.getGuiScaledWidth() - 176) / 2;
                int top = (win.getGuiScaledHeight() - (114 + rows * 18)) / 2;
                var slot = menu.slots.get(n);
                double scale = win.getGuiScale();
                mc.mouseHandler.onMove(win.handle(), (left + slot.x + 8) * scale, (top + slot.y + 8) * scale, 0, 0);
            }
        } else if (cmd.equals("close") && mc.player != null) {
            mc.player.closeContainer(); // Esc 로 상자 메뉴를 닫을 때와 같음 (닫기 패킷)
        } else if (cmd.startsWith("click ") && mc.player != null) {
            // 대화창 단추를 누를 때 클라이언트가 보내는 것 (ClickEvent.Custom mmorpg:form {id, sel})
            String[] a = cmd.split(" ");
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", Integer.parseInt(a[1]));
            tag.putInt("sel", Integer.parseInt(a[2]));
            mc.player.connection.send(new ServerboundCustomClickActionPacket(Identifier.parse("mmorpg:form"), Optional.of(tag)));
        } else if (cmd.startsWith("submit ") && mc.player != null) {
            // 입력창 확인 (CustomAll: 입력 칸 값 + 덧붙인 {id, sel, n}) — 글자 · 고른 칸 id · 숫자 막대 (float) · 켜고 끄기 (byte)
            CompoundTag tag = new CompoundTag();
            tag.putInt("id", Integer.parseInt(cmd.substring(7).trim()));
            tag.putInt("sel", 0);
            tag.putInt("n", 4);
            tag.putString("v0", "abc");
            tag.putString("v1", "2");
            tag.putFloat("v2", 7F);
            tag.putBoolean("v3", false);
            mc.player.connection.send(new ServerboundCustomClickActionPacket(Identifier.parse("mmorpg:form"), Optional.of(tag)));
        } else if (cmd.equals("shift on")) {
            // Shift 를 누른 채로 (다음 틱에 클라이언트가 입력 패킷을 보냄 — 서버의 isShiftKeyDown)
            mc.options.keyShift.setDown(true);
        } else if (cmd.equals("shift off")) {
            mc.options.keyShift.setDown(false);
        } else if (cmd.equals("key f") && mc.player != null) {
            // F (왼손 바꾸기 키) 와 같은 패킷 (Minecraft.handleKeybinds)
            mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        } else if (cmd.startsWith("key ") && mc.player != null) {
            // 숫자 키 N (핫바 칸 N) 과 같음: 고른 칸만 바꾸면 다음 틱에 클라이언트가 칸 바뀜을 보냄
            int n = Integer.parseInt(cmd.substring(4).trim());
            if (n >= 1 && n <= 9) mc.player.getInventory().setSelectedSlot(n - 1);
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
