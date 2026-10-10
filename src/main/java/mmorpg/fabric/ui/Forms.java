package mmorpg.fabric.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 창 열기 · 응답 (chestui.js showForm · withFormLock 의 자바 판). 애드온은 창마다 await 로 기다리지만 자바는 응답이 오면 부르는 콜백:
 * show(플레이어, 받을 일) — 누른 칸 (닫으면 -1) 으로 받을 일을 서버 스레드에서 부름. 받을 일이 다음 창을 열면 클라이언트는 창을 그대로 바꾸고,
 * 열지 않으면 (흐름 끝) 닫기 패킷을 보냄. 한 사람에게 창은 하나 (새 창이 열리면 예전 창의 받을 일은 버림).
 * isOpen: 창이 열려 있거나 잠깐 묶어 둔 동안 (강화 망치질 같은 연출 — busy) — 그 사이 NPC 를 눌러도 새 창을 열지 않음.
 */
public final class Forms {
    private Forms() {}

    private record Pending(int id, Consumer<FormNet.ResponsePayload> handler) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();
    /** 시험용: 클라이언트 없이 창 흐름을 돌리는 플레이어 (보낼 창은 LAST 에 적어 둠) */
    public static final java.util.Set<UUID> HEADLESS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<UUID, CustomPacketPayload> LAST = new HashMap<>();
    private static int seq;

    public static void init() {
        FormNet.register();
        ServerPlayNetworking.registerGlobalReceiver(FormNet.ResponsePayload.TYPE, (payload, context) -> respond(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            PENDING.clear();
            BUSY_UNTIL.clear();
        });
    }

    static void forget(UUID id) {
        PENDING.remove(id);
        BUSY_UNTIL.remove(id);
        LAST.remove(id);
    }

    /** 창이 열려 있는지 · 연출 중이라 묶여 있는지 (chestui.js isFormOpen) */
    public static boolean isOpen(ServerPlayer sp) {
        if (PENDING.containsKey(sp.getUUID())) return true;
        Long until = BUSY_UNTIL.get(sp.getUUID());
        return until != null && until > Tasks.now();
    }

    /** ticks 틱 동안 묶어 둠 (창 없이 연출만 보이는 동안 다른 창 흐름이 끼어들지 않게 — 0 이면 풂) */
    public static void busy(ServerPlayer sp, int ticks) {
        if (ticks <= 0) BUSY_UNTIL.remove(sp.getUUID());
        else BUSY_UNTIL.put(sp.getUUID(), Tasks.now() + ticks);
    }

    /** 이 클라이언트가 창을 그릴 수 있는지 (모드가 있는 클라이언트) */
    public static boolean canShow(ServerPlayer sp) {
        return ServerPlayNetworking.canSend(sp, FormNet.ChestPayload.TYPE);
    }

    static int nextId() {
        seq = seq >= 1_000_000_000 ? 1 : seq + 1;
        return seq;
    }

    /** 창을 보냄 (응답이 오면 handler) */
    static void open(ServerPlayer sp, int id, CustomPacketPayload payload, Consumer<FormNet.ResponsePayload> handler) {
        if (sp.isRemoved()) return;
        boolean headless = HEADLESS.contains(sp.getUUID());
        if (!headless && !canShow(sp)) {
            Notify.warn(sp, "§c이 창은 MMO RPG 모드를 넣은 클라이언트에서 열립니다.");
            return;
        }
        BUSY_UNTIL.remove(sp.getUUID());
        PENDING.put(sp.getUUID(), new Pending(id, handler));
        if (headless) LAST.put(sp.getUUID(), payload);
        else ServerPlayNetworking.send(sp, payload);
    }

    private static void respond(ServerPlayer sp, FormNet.ResponsePayload p) {
        Pending cur = PENDING.get(sp.getUUID());
        if (cur == null || cur.id() != p.id()) return; // 늦게 온 응답 · 바뀐 창
        PENDING.remove(sp.getUUID());
        try {
            cur.handler().accept(p);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] form handler", e);
        }
        if (!PENDING.containsKey(sp.getUUID())) {
            LAST.remove(sp.getUUID());
            if (!HEADLESS.contains(sp.getUUID()) && canShow(sp)) ServerPlayNetworking.send(sp, new FormNet.ClosePayload(p.id()));
        }
    }

    /** 열린 창을 닫음 (받을 일은 부르지 않음 — 서버가 흐름을 끝낼 때) */
    public static void close(ServerPlayer sp) {
        Pending cur = PENDING.remove(sp.getUUID());
        BUSY_UNTIL.remove(sp.getUUID());
        LAST.remove(sp.getUUID());
        if (cur != null && !HEADLESS.contains(sp.getUUID()) && canShow(sp)) ServerPlayNetworking.send(sp, new FormNet.ClosePayload(cur.id()));
    }

    /** 시험용: 열린 창에 응답한 것처럼 (클라이언트 없이 흐름을 돌림) */
    public static boolean answer(ServerPlayer sp, int selection, java.util.List<String> values) {
        Pending cur = PENDING.get(sp.getUUID());
        if (cur == null) return false;
        respond(sp, new FormNet.ResponsePayload(cur.id(), selection, values == null ? java.util.List.of() : values));
        return true;
    }

    /** 시험용: 창이 열려 기다리는 중인지 */
    public static boolean pending(ServerPlayer sp) {
        return PENDING.containsKey(sp.getUUID());
    }

    /** 시험용 (HEADLESS): 마지막으로 연 창 (상자 창 · 대화창 · 버튼 목록 · 입력창 패킷, 없으면 null) */
    public static CustomPacketPayload last(ServerPlayer sp) {
        return LAST.get(sp.getUUID());
    }
}
