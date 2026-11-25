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
 * cam back|first · cam film 각 거리 높이 보는높이 앞 따라감 (2026-11-11 — 모드 영상 (RpgFilm) 과 같은 자리: 처음 몸 자리 · 보는 쪽에서 왼쪽으로 각도, 화면 줄 숨김) ·
 * cam off · key f (F 키 패킷) · key N (숫자 키 N — 고른 칸) · shift on|off (Shift 누름) · slot N · shiftslot N (상자 메뉴 칸 누름 · Shift) ·
 * hover N (마우스를 칸 N 위로 — 설명 상자) ·
 * close (상자 메뉴 닫기) · click id sel (대화창 단추) ·
 * submit id (입력창 확인) · film 이름 n (2026-11-11 영상 — 지금부터 클라이언트 틱마다 n 장: screenshots/../films/이름/f0000.png … + manifest.json —
 * 서버가 /tick rate 를 늦춰 틱마다 한 장이 되게, ci/films.py 가 mp4 로) ·
 * done (닫힘). 접속이 끊기면 끊긴 화면을 찍고 닫힘. 아무 신호 없이 12분이면 찍고 닫힘. 사진은 게임 폴더 screenshots/probe_&lt;이름&gt;.png.
 */
public final class Probe implements ClientModInitializer {
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int wait;
    private static String pendingInv;
    private static int ticks;
    private static int quitIn = -1;
    private static boolean disconnectedShot;
    /** 영상: 이름 · 남은 장 · 찍은 장 · 틱 */
    private static String filmName;
    private static int filmLeft;
    private static int filmIndex;
    private static final java.util.List<Integer> FILM_TICKS = new java.util.ArrayList<>();
    /** 영상 카메라 (cam film): {각(라디안), 거리, 높이, 보는 높이, 앞, 따라감, 처음 x, y, z, 앞 x, 앞 z} — null 이면 보통 카메라 */
    private static volatile double[] filmCam;

    @Override
    public void onInitializeClient() {
        log("ready");
    }

    /** 채팅 (서버의 알림 줄) — 메인 스레드 */
    public static void chat(String text) {
        if (!text.startsWith("[probe] ")) return;
        String cmd = text.substring("[probe] ".length()).trim();
        if (cmd.startsWith("now ")) {
            // 2026-11-18 영상 중에도 바로 (tick 은 영상 동안 신호를 미룸 — 1인칭 시범의 Q · 우클릭): 실행하고 채팅 줄을 지움 (영상에 남지 않게)
            now(cmd.substring(4).trim());
            clearChat(Minecraft.getInstance());
            return;
        }
        QUEUE.add(cmd);
    }

    /** 바로 실행하는 신호 (메인 스레드 — 채팅 패킷 처리 중) */
    private static void now(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
        boolean cd = mc.player.getCooldowns().isOnCooldown(held);
        float pct = mc.player.getCooldowns().getCooldownPercent(held, 0F);
        switch (cmd) {
            case "key q" -> {
                // Q 를 누름: 버리기 키의 누름 수 (KeyMapping.clickCount) 를 하나 올리면 다음 틱 Minecraft.handleKeybinds 가 진짜 Q 와 같은 길로 처리
                // (손 칸을 먼저 비우고 버리기 패킷). 1차 시범은 26.3 에 공개 drop(boolean) 이 없어 실패 — 이름이 바뀌어도 되게 리플렉션 (int 칸 clickCount,
                // 없으면 정적 click(Key))
                String how;
                net.minecraft.client.KeyMapping drop = mc.options.keyDrop;
                try {
                    java.lang.reflect.Field f = net.minecraft.client.KeyMapping.class.getDeclaredField("clickCount");
                    f.setAccessible(true);
                    f.setInt(drop, f.getInt(drop) + 1);
                    how = "clickCount";
                } catch (ReflectiveOperationException | RuntimeException e) {
                    how = "no clickCount (" + e + ")";
                    try {
                        Object key = null;
                        for (java.lang.reflect.Field kf : net.minecraft.client.KeyMapping.class.getDeclaredFields()) {
                            if (java.lang.reflect.Modifier.isStatic(kf.getModifiers()) || !kf.getType().getSimpleName().equals("Key")) continue;
                            kf.setAccessible(true);
                            key = kf.get(drop);
                        }
                        for (java.lang.reflect.Method m : net.minecraft.client.KeyMapping.class.getDeclaredMethods()) {
                            if (key != null && m.getName().equals("click") && java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                                    && m.getParameterTypes()[0].isInstance(key)) {
                                m.setAccessible(true);
                                m.invoke(null, key);
                                how += " → click(" + key + ")";
                            }
                        }
                    } catch (ReflectiveOperationException | RuntimeException e2) {
                        how += " → " + e2;
                    }
                }
                qWatch = 0;
                log("now key q via " + how + " cooldown " + cd + " pct " + pct + " hand " + held.getCount() + " " + held.getItem());
            }
            case "use" -> {
                // 우클릭 (허공) 과 같음 (Minecraft.startUseItem 의 아이템 사용 — 쿨타임이어도 패킷을 보내는지)
                Object r = mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                log("now use cooldown " + cd + " pct " + pct + " result " + r);
            }
            case "cd" -> log("now cd cooldown " + cd + " pct " + pct + " cmd " + held.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA));
            // 2026-11-18 축복의 길 스킬 키 (SkillKeys): 좌클릭 (공격 키 누름 수 — 다음 틱 startAttack) · 웅크림 켜고 끄기 (움직임 입력) · Shift 두 번 (틱마다 차례로)
            case "attack" -> {
                clickKey(mc.options.keyAttack);
                log("now attack");
            }
            case "sneak on", "sneak off" -> {
                mc.options.keyShift.setDown(cmd.endsWith("on"));
                log("now " + cmd);
            }
            case "shift2" -> {
                shift2 = 0;
                log("now shift2");
            }
            case "fpdiag noswing-off", "fpdiag noswing-on" -> log("now " + cmd + " components " + held.getComponents());
            default -> log("now unknown " + cmd);
        }
    }

    private static boolean skinsLogged;

    /**
     * 2026-11-13 기본 스킨 기록 (서버 VanillaRig.defaultFor 가 UUID 로 고르는 차례가 클라이언트 DefaultPlayerSkin 과 같은지 — CI 기록에서 맞춤):
     * UUID(0, i) 의 hashCode 는 i 라 get(UUID(0, i)) = 목록 i 번째. 판마다 이름이 달라도 되게 리플렉션 · toString 만
     */
    private static void logSkins(Minecraft mc) {
        try {
            Class<?> c = Class.forName("net.minecraft.client.resources.DefaultPlayerSkin");
            java.lang.reflect.Method get = null;
            for (java.lang.reflect.Method m : c.getMethods()) {
                if (m.getName().equals("get") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == java.util.UUID.class) get = m;
            }
            if (get == null) {
                log("default-skins: no get(UUID)");
            } else {
                for (int i = 0; i < 18; i++) log("default-skin " + i + " " + get.invoke(null, new java.util.UUID(0L, i)));
                log("default-skin 18 " + get.invoke(null, new java.util.UUID(0L, 18)));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log("default-skins failed " + e);
        }
        if (mc.player == null) return;
        log("own uuid " + mc.player.getUUID() + " hash " + mc.player.getUUID().hashCode() + " mod18 " + Math.floorMod(mc.player.getUUID().hashCode(), 18));
        for (String name : new String[] {"getSkin", "skin"}) {
            try {
                java.lang.reflect.Method m = mc.player.getClass().getMethod(name);
                log("own skin " + m.invoke(mc.player));
                break;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // 다음 이름
            }
        }
    }

    /** 클라이언트 틱 끝 */
    /** Q 를 누른 뒤 틱 (−1 = 안 봄): 손 칸의 수를 틱마다 기록 (클라이언트가 먼저 비움 → 서버가 되돌림) */
    private static int qWatch = -1;
    /** 2026-11-18 Shift 두 번 차례 (0 켬 · 2 끔 · 4 켬 · 6 끔 — 움직임 입력은 바뀔 때만 감) */
    private static int shift2 = -1;

    /** 키 한 번 누름 (누름 수를 올림 — 다음 틱 Minecraft.handleKeybinds 가 진짜 키와 같은 길로) */
    private static void clickKey(net.minecraft.client.KeyMapping k) {
        try {
            java.lang.reflect.Field f = net.minecraft.client.KeyMapping.class.getDeclaredField("clickCount");
            f.setAccessible(true);
            f.setInt(k, f.getInt(k) + 1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("click " + k.getName() + " failed: " + e);
        }
    }

    public static void tick(Minecraft mc) {
        ticks++;
        if (shift2 >= 0) {
            if (shift2 == 0 || shift2 == 4) mc.options.keyShift.setDown(true);
            if (shift2 == 2 || shift2 == 6) mc.options.keyShift.setDown(false);
            if (++shift2 > 6) shift2 = -1;
        }
        if (qWatch >= 0 && mc.player != null) {
            net.minecraft.world.item.ItemStack h = mc.player.getMainHandItem();
            log("q watch " + qWatch + " hand " + h.getCount() + " " + h.getItem());
            if (++qWatch > 6) qWatch = -1;
        }
        if (!skinsLogged && mc.player != null && ticks > 40) {
            skinsLogged = true;
            logSkins(mc);
        }
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
        if (filmName != null) {
            filmFrame(mc);
            return;
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
        } else if (cmd.startsWith("film ")) {
            String[] a = cmd.split(" ");
            filmName = a[1];
            filmLeft = Integer.parseInt(a[2]);
            filmIndex = 0;
            FILM_TICKS.clear();
            // 2026-11-15 시범 2판: 시야 효과 끔 (느려짐의 화면 확대 · 축소 — 팬텀 버스트 1판 영상이 세 번 줌인 · 줌아웃) · 채팅 지움 (1인칭 영상에 [probe] 줄이 보였음)
            fovEffectsOff(mc);
            clearChat(mc);
            // 글꼴 · 모델 메모리 재기는 첫 영상이 끝난 뒤 (film done) — 2026-11-25 여기서 재면 (GC 포함 약 2초) 그동안 서버가 먼저 영상 일을 해서, 묶음의 첫 영상
            // 앞부분이 따라잡는 틱마다 새로 그리지 않은 같은 화면으로 찍혔음 (CI 44296333035 vanilla_fp_sword 0 ~ 19장)
            handMin = 2F;
            handDips = 0;
            // 2026-11-16 자바 힙 (사용자 "8gb 지원" — 런처 기본 -Xmx2G 에서 버티는지: 글꼴 그림은 힙 밖 · 힙은 엔티티 · 청크 · 모델)
            Runtime rt = Runtime.getRuntime();
            log("heap used MB " + (rt.totalMemory() - rt.freeMemory()) / 1_000_000 + " committed MB " + rt.totalMemory() / 1_000_000 + " max MB "
                    + rt.maxMemory() / 1_000_000);
            log("film start " + filmName + " " + filmLeft);
        } else if (cmd.startsWith("inv ") && mc.player != null) {
            mc.gui.setScreen(new InventoryScreen(mc.player));
            pendingInv = cmd.substring(4);
            wait = 20;
        } else if (cmd.startsWith("cam film ") && mc.player != null) {
            // 모드 영상 (RpgFilm) 과 같은 카메라: 처음 몸 자리 · 보는 쪽 기준 — 옆 뒤 높이에서 몸 앞을 봄 (3인칭이라 몸이 그려짐)
            String[] a = cmd.substring(9).trim().split(" ");
            double yaw = Math.toRadians(mc.player.getYRot());
            filmCam = new double[] {Math.toRadians(Double.parseDouble(a[0])), Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                    Double.parseDouble(a[3]), Double.parseDouble(a[4]), Double.parseDouble(a[5]), mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    -Math.sin(yaw), Math.cos(yaw)};
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.gui.toastManager().clear(); // 접속 때의 알림 (채팅 확인 불가 등) 이 영상 첫 장을 가리지 않게
        } else if (cmd.equals("cam off")) {
            filmCam = null;
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        } else if (cmd.equals("cam back")) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (cmd.equals("cam first")) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            clearChat(mc); // 2026-11-15 1인칭 영상 첫 장에 [probe] cam 줄이 남았음
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
        } else if (cmd.equals("ents") && mc.level != null && mc.player != null) {
            // 둘레 10칸 엔티티: 번호 · 종류 · 자리 · 탄 것 (꾸미기 표시 엔티티가 사람에게 탔는지)
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e.distanceTo(mc.player) > 10) continue;
                net.minecraft.world.phys.Vec3 p = e.position();
                log(String.format("ent %d %s pos %.2f %.2f %.2f vehicle %d passengers %d", e.getId(),
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()), p.x, p.y, p.z,
                        e.getVehicle() == null ? -1 : e.getVehicle().getId(), e.getPassengers().size()));
            }
        } else if (cmd.equals("done")) {
            log("done");
            quitIn = 40;
        }
    }

    /**
     * 영상 카메라 자리 · 방향 {x, y, z, yaw, pitch} (ProbeCameraMixin — 그림마다) 또는 null. 가로는 몸을 따라감 몫만큼 · 높이는 60% 만 (솟구쳐도 땅이 남게) ·
     * 보는 곳 = 몸 앞 (RpgFilm.place 와 같은 셈)
     */
    public static double[] filmCamera(Minecraft mc) {
        double[] c = filmCam;
        if (c == null || mc.player == null) return null;
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double fx = c[9];
        double fz = c[10];
        double by = c[7] + (py - c[7]) * 0.6;
        double lx = fz;
        double lz = -fx;
        double ox = Math.cos(c[0]) * fx + Math.sin(c[0]) * lx;
        double oz = Math.cos(c[0]) * fz + Math.sin(c[0]) * lz;
        double cx = c[6] + (px - c[6]) * c[5] + ox * c[1];
        double cy = by + c[2];
        double cz = c[8] + (pz - c[8]) * c[5] + oz * c[1];
        double dx = px + fx * c[4] - cx;
        double dy = by + c[3] - cy;
        double dz = pz + fz * c[4] - cz;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return new double[] {cx, cy, cz, yaw, pitch};
    }

    /**
     * 2026-11-11 진단: 둘레 12칸의 갑옷 거치대 (RPG 몹의 대역) 마다 발 높이 · 크기 · 키 · 작은 거치대 · 탄 것 수 · 첫 탄 것의 높이 (뼈가 타는 높이 — 발 + 키 여야 함)
     */
    private static void dumpStands(Minecraft mc, String tag) {
        if (mc.level == null || mc.player == null) return;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand a) || e.distanceTo(mc.player) > 12) continue;
            java.util.List<net.minecraft.world.entity.Entity> ps = a.getPassengers();
            double py = ps.isEmpty() ? Double.NaN : ps.get(0).getY() - a.getY();
            log(String.format(java.util.Locale.ROOT, "stand %s id %d y %.2f scale %.3f height %.3f small %s passengers %d rider %.3f", tag, a.getId(), a.getY(),
                    a.getScale(), a.getBbHeight(), a.isSmall(), ps.size(), py));
        }
    }

    /** 영상 한 장 (클라이언트 틱마다) — 다 찍으면 manifest.json (틱 목록) */
    private static void filmFrame(Minecraft mc) {
        if (filmIndex == 30) dumpStands(mc, filmName);
        Path dir = mc.gameDirectory.toPath().resolve("films").resolve(filmName);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log("film mkdir failed " + e);
        }
        Path file = dir.resolve(String.format("f%04d.png", filmIndex++));
        FILM_TICKS.add(ticks);
        fpDiag(mc, filmIndex - 1);
        // 2026-11-18 1인칭 프레임: 손 높이 (바꿔 들기 — 아이템이 구성 요소까지 바뀌면 바닐라가 손을 내렸다 올림) 가장 낮은 값 · 낮았던 장 수
        float hh = handHeight(mc);
        if (hh >= 0) {
            handMin = Math.min(handMin, hh);
            if (hh < 0.98F) handDips++;
        }
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage image) -> {
            try (image) {
                image.writeToFile(file);
            } catch (IOException e) {
                log("film shot failed " + file + " " + e);
            }
        });
        if (--filmLeft > 0) return;
        StringBuilder b = new StringBuilder("{\"id\": \"").append(filmName).append("\", \"ticks\": [");
        for (int i = 0; i < FILM_TICKS.size(); i++) b.append(i == 0 ? "" : ", ").append(FILM_TICKS.get(i));
        b.append("]}\n");
        try {
            Files.writeString(dir.resolve("manifest.json"), b.toString());
        } catch (IOException e) {
            log("film manifest failed " + e);
        }
        log("film done " + filmName + " " + filmIndex);
        if (handMin <= 1F) log(String.format(java.util.Locale.ROOT, "film hand %s min %.2f dips %d", filmName, handMin, handDips));
        filmName = null;
        wait = 5;
        if (!fontMeasured) {
            // 첫 영상이 끝난 뒤 한 번 (다음 영상은 서버가 30틱 넘게 차린 뒤 시작 — 재는 동안 찍는 장이 없음)
            fontMeasured = true;
            fontMemory(mc);
            modelMemory(mc);
        }
    }

    /**
     * 2026-11-18 진단 (1인칭 영상 vanilla_fp_* — CI 44292888089 에서 평타 프레임이 움직이지 않음): 장마다 든 아이템의 쿨타임 몫 · custom_model_data 글자 ·
     * 휘두르기 구성 요소 (attack_animation) 가 있는지 · 손 그림이 쓰는 아이템 (ItemInHandRenderer 의 주 손 아이템) 이 든 것과 같은 것인지 · 손 그림 실수 칸들
     */
    private static void fpDiag(Minecraft mc, int n) {
        if (mc.player == null || filmName == null || !filmName.startsWith("vanilla_fp_") || n > 260) return;
        try {
            net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
            float pct = mc.player.getCooldowns().getCooldownPercent(held, 0F);
            Object cmd = held.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            String strings = cmd == null ? "-" : String.valueOf(((net.minecraft.world.item.component.CustomModelData) cmd).strings());
            boolean attackAnim = String.valueOf(held.getComponents()).contains("attack_animation");
            StringBuilder hr = new StringBuilder();
            Object renderer = handRenderer(mc);
            if (renderer != null) {
                for (java.lang.reflect.Field f : renderer.getClass().getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.getType() == float.class) hr.append(' ').append(f.getName()).append('=').append(String.format(java.util.Locale.ROOT, "%.2f", f.getFloat(renderer)));
                    else if (f.getType().getSimpleName().equals("ItemStack")) {
                        Object v = f.get(renderer);
                        hr.append(' ').append(f.getName()).append(v == held ? "=held" : v instanceof net.minecraft.world.item.ItemStack is ? "=other(" + (is.isEmpty() ? "empty" : "same-model " + java.util.Objects.equals(
                                is.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA), cmd)) + ")" : "=null");
                    }
                }
            }
            log(String.format(java.util.Locale.ROOT, "fpdiag %s %d pct %.2f cmd %s attack-anim %s |%s", filmName, n, pct, strings, attackAnim, hr));
        } catch (Throwable e) {
            log("fpdiag failed " + e);
        }
    }

    /** 영상 동안 손 높이 (ItemInHandRenderer 주 손 — 1 = 다 올림) 가장 낮은 값 · 0.98 아래인 장 수 */
    private static float handMin = 2F;
    private static int handDips;
    private static java.lang.reflect.Field handHeightField;
    private static boolean handTried;

    /** 주 손 높이 (리플렉션 — 손 그림을 못 찾거나 이름이 판마다 바뀌면 −1) */
    private static float handHeight(Minecraft mc) {
        Object r = handRenderer(mc);
        if (r == null) return -1;
        try {
            if (!handTried) {
                handTried = true;
                java.lang.reflect.Field loose = null;
                for (Class<?> c = r.getClass(); c != null && c != Object.class && handHeightField == null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (f.getType() != float.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        String n = f.getName();
                        if (n.equals("mainHandHeight")) {
                            handHeightField = f;
                            break;
                        }
                        String low = n.toLowerCase(java.util.Locale.ROOT);
                        if (loose == null && low.contains("mainhand") && low.contains("height") && !n.startsWith("oM")) loose = f;
                    }
                }
                if (handHeightField == null) handHeightField = loose;
                if (handHeightField != null) handHeightField.setAccessible(true);
                log("hand height field " + (handHeightField == null ? "none" : handHeightField.getName()));
            }
            if (handHeightField == null) return -1;
            return handHeightField.getFloat(r);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (handHeightField != null) log("hand height failed " + e);
            handHeightField = null;
            return -1;
        }
    }

    /**
     * 2026-11-25 1인칭 손 그림 (ItemInHandRenderer): 26.3 에서는 GameRenderer 의 선언 필드에 없었음 (CI 44296333035 "hand height field false" — 손 높이 ·
     * fpdiag 손 칸이 비었음) → GameRenderer · Minecraft 의 클라이언트 필드와 그 아래 한 단계에서 형 이름에 ItemInHand 가 든 것을 찾음. 처음 한 번 찾은 길 ·
     * 실수 필드 이름을 기록
     */
    private static Object handRendererObj;
    private static boolean handRendererTried;

    private static Object handRenderer(Minecraft mc) {
        if (handRendererTried) return handRendererObj;
        handRendererTried = true;
        StringBuilder path = new StringBuilder();
        try {
            handRendererObj = findHandRenderer(mc.gameRenderer, "gameRenderer", 0, path);
            if (handRendererObj == null) handRendererObj = findHandRenderer(mc, "minecraft", 0, path);
        } catch (RuntimeException e) {
            log("hand renderer search failed " + e);
        }
        if (handRendererObj == null) {
            // 2026-11-25 CI 45586370783 도 못 찾음 → 다음 판을 위해 GameRenderer 의 클라이언트 필드 형을 기록
            StringBuilder types = new StringBuilder();
            for (Class<?> c = mc.gameRenderer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType().getName().startsWith("net.minecraft.client")) {
                        types.append(' ').append(f.getName()).append(':').append(f.getType().getSimpleName());
                    }
                }
            }
            log("hand renderer not found | gameRenderer fields" + types);
            return null;
        }
        StringBuilder floats = new StringBuilder();
        for (Class<?> c = handRendererObj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == float.class) floats.append(' ').append(f.getName());
            }
        }
        log("hand renderer " + path + " " + handRendererObj.getClass().getName() + " floats" + floats);
        return handRendererObj;
    }

    /**
     * root 의 net.minecraft.client 필드 (depth 1 까지) 가운데 클래스 이름에 ItemInHand 또는 HandsAndItems 가 든 값 — 2026-11-25 CI 45590731437:
     * 26.3 은 GameRenderer.firstPersonHandsAndItemsRenderer (FirstPersonHandsAndItemsRenderer) 로 이름이 바뀌었음
     */
    private static Object findHandRenderer(Object root, String name, int depth, StringBuilder path) {
        if (root == null) return null;
        for (Class<?> c = root.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().isArray()) continue;
                if (!f.getType().getName().startsWith("net.minecraft.client")) continue;
                Object v;
                try {
                    f.setAccessible(true);
                    v = f.get(root);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
                if (v == null || v == root) continue;
                String sn = v.getClass().getSimpleName();
                if (sn.contains("ItemInHand") || sn.contains("HandsAndItems")) {
                    path.append(name).append('.').append(f.getName());
                    return v;
                }
                if (depth < 1) {
                    Object r = findHandRenderer(v, name + "." + f.getName(), depth + 1, path);
                    if (r != null) return r;
                }
            }
        }
        return null;
    }

    /**
     * 2026-11-18 1인칭 프레임 예산 (축복의 길 — 무기 디자인 × 동작 프레임 모델): 구운 아이템 모델의 면 (BakedQuad) 수 · 꼭짓점 자리 (Vector3f) 수 ·
     * 같은 손 모델을 쓰는 두 무기 (40 · 30렙 영웅 칼 — 아이템 정의는 따로) 가 면을 함께 쓰는지 (모델마다 한 번 굽는지, 정의마다 굽는지) · GC 뒤 힙
     */
    private static void modelMemory(Minecraft mc) {
        try {
            Object mm = Minecraft.class.getMethod("getModelManager").invoke(mc);
            long[] all = new long[4];
            java.util.Set<Object> quads = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            java.util.Set<Object> pos = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            walkQuads(mm, quads, pos, all);
            StringBuilder share = new StringBuilder();
            java.lang.reflect.Method get = null;
            for (java.lang.reflect.Method m : mm.getClass().getMethods()) {
                if (m.getName().equals("getItemModel") && m.getParameterCount() == 1) get = m;
            }
            if (get != null) {
                java.util.List<java.util.Set<Object>> per = new java.util.ArrayList<>();
                for (String id : new String[] {"rpg:sword_l40_hero", "rpg:sword_l30_hero"}) {
                    java.util.Set<Object> q = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                    walkQuads(get.invoke(mm, net.minecraft.resources.Identifier.parse(id)), q, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), new long[4]);
                    per.add(q);
                    share.append(id).append(' ').append(q.size()).append(' ');
                }
                int both = 0;
                for (Object q : per.get(0)) if (per.get(1).contains(q)) both++;
                share.append("shared ").append(both);
            } else {
                share.append("no getItemModel");
            }
            long quadBytes = all[2];
            System.gc();
            System.gc();
            Runtime rt = Runtime.getRuntime();
            log("models quads " + quads.size() + " positions " + pos.size() + " int-vertex arrays " + all[1] + " quad shallow bytes ~" + quadBytes + " | " + share
                    + " | heap after gc MB " + (rt.totalMemory() - rt.freeMemory()) / 1_000_000);
        } catch (Throwable e) {
            log("model memory failed " + e);
        }
    }

    /** 모델 관리자에서 닿는 BakedQuad (그림판 · 텍스처 · 엔티티 모델은 건너뜀) — all[1] int[] 꼭짓점 배열 수 · all[2] 면 얕은 크기 합 */
    private static void walkQuads(Object root, java.util.Set<Object> quads, java.util.Set<Object> pos, long[] all) {
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.ArrayDeque<Object> todo = new java.util.ArrayDeque<>();
        if (root != null) todo.push(root);
        while (!todo.isEmpty()) {
            Object o = todo.pop();
            if (o == null || !seen.add(o)) continue;
            Class<?> c = o.getClass();
            if (c.getSimpleName().equals("BakedQuad")) {
                quads.add(o);
                long size = 12;
                for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                    for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        Class<?> t = f.getType();
                        size += t == long.class || t == double.class ? 8 : t == boolean.class || t == byte.class ? 1 : t == short.class || t == char.class ? 2 : 4;
                        try {
                            f.setAccessible(true);
                            Object v = f.get(o);
                            if (v instanceof int[] arr) {
                                all[1]++;
                                all[2] += 16 + 4L * arr.length;
                            } else if (v != null && v.getClass().getName().startsWith("org.joml")) {
                                pos.add(v);
                            }
                        } catch (RuntimeException | IllegalAccessException ignored) {
                            // 막힌 칸
                        }
                    }
                }
                all[2] += (size + 7) / 8 * 8;
                continue;
            }
            if (o instanceof java.util.Map<?, ?> m) {
                for (Object v : m.values()) if (v != null) todo.push(v);
                continue;
            }
            if (o instanceof Iterable<?> it) {
                for (Object v : it) if (v != null) todo.push(v);
                continue;
            }
            if (c.isArray()) {
                if (!c.getComponentType().isPrimitive()) for (Object v : (Object[]) o) if (v != null) todo.push(v);
                continue;
            }
            String n = c.getName();
            String s = c.getSimpleName();
            if (!(n.startsWith("net.minecraft.client") || n.startsWith("com.mojang"))) continue;
            if (s.contains("Texture") || s.contains("Sprite") || s.contains("Atlas") || s.contains("NativeImage") || s.contains("EntityModel") || s.contains("ModelPart")
                    || s.contains("Font") || s.equals("Minecraft")) continue;
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(o);
                        if (v != null) todo.push(v);
                    } catch (RuntimeException | IllegalAccessException ignored) {
                        // 막힌 칸
                    }
                }
            }
        }
    }

    /** 시야 효과 (FOV Effects) 0 — 이름이 판마다 바뀌어도 컴파일되게 리플렉션 (실패하면 기록만) */
    private static void fovEffectsOff(Minecraft mc) {
        try {
            Object opt = mc.options.getClass().getMethod("fovEffectScale").invoke(mc.options);
            opt.getClass().getMethod("set", Object.class).invoke(opt, 0.0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("fov effects off failed " + e);
        }
    }

    /** 채팅 줄 지움 (ChatComponent.clearMessages — gui 안을 리플렉션으로 찾음, 실패하면 기록만) */
    private static boolean fontMeasured;

    /**
     * 2026-11-15 글꼴 그림판 메모리 (원소 공용 그림 예산 — 비트맵 글꼴은 그림판을 통째로 풀어 들고 있다가 처음 쓰는 글자만 텍스처에 올림): 글꼴 관리자에서 닿는 그림
     * (NativeImage) 의 수 · 풀린 크기 (가로 × 세로 × 4) 합
     */
    private static void fontMemory(Minecraft mc) {
        try {
            Object fm = null;
            for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getType().getSimpleName().equals("FontManager")) continue;
                f.setAccessible(true);
                fm = f.get(mc);
            }
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            long[] acc = {0, 0, 0};
            walkImages(fm, 0, seen, acc);
            log("font images " + acc[0] + " decoded MB " + acc[1] / 1_000_000 + " (largest " + acc[2] / 1_000_000 + " MB)");
        } catch (Throwable e) {
            log("font memory failed " + e);
        }
    }

    private static void walkImages(Object o, int depth, java.util.Set<Object> seen, long[] acc) throws ReflectiveOperationException {
        if (o == null || depth > 10 || !seen.add(o)) return;
        Class<?> c = o.getClass();
        if (o instanceof NativeImage ni) {
            long w = ni.getWidth();
            long h = ni.getHeight();
            acc[0]++;
            acc[1] += w * h * 4;
            acc[2] = Math.max(acc[2], w * h * 4);
            return;
        }
        if (o instanceof java.util.Map<?, ?> m) {
            for (Object v : m.values()) walkImages(v, depth + 1, seen, acc);
            return;
        }
        if (o instanceof Iterable<?> it) {
            for (Object v : it) walkImages(v, depth + 1, seen, acc);
            return;
        }
        if (c.isArray()) {
            if (!c.getComponentType().isPrimitive()) for (Object v : (Object[]) o) walkImages(v, depth + 1, seen, acc);
            return;
        }
        String n = c.getName();
        if (!n.startsWith("net.minecraft.client.gui.font") && !n.startsWith("com.mojang.blaze3d")) return;
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    walkImages(f.get(o), depth + 1, seen, acc);
                } catch (RuntimeException ignored) {
                    // 모듈이 막은 칸
                }
            }
        }
    }

    private static void clearChat(Minecraft mc) {
        try {
            Object chat = findChat(mc.gui, 0);
            if (chat == null) {
                log("clear chat: no chat component");
                return;
            }
            chat.getClass().getMethod("clearMessages", boolean.class).invoke(chat, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log("clear chat failed " + e);
        }
    }

    private static Object findChat(Object o, int depth) throws IllegalAccessException {
        if (o == null || depth > 2) return null;
        if (o.getClass().getSimpleName().equals("ChatComponent")) return o;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                if (!f.getType().getName().startsWith("net.minecraft.client.gui")) continue;
                f.setAccessible(true);
                Object v = f.get(o);
                if (v == null) continue;
                if (v.getClass().getSimpleName().equals("ChatComponent")) return v;
                Object r = findChat(v, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
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
