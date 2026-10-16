package mmorpg.fabric.client;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Json;
import mmorpg.fabric.cosmetic.CosmeticNet;
import mmorpg.fabric.cosmetic.Look;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 클라이언트의 장식 (0.13.0): 내 장식 (가진 것 · 모습 — 서버가 보냄), 보이는 사람들의 모습 (UUID → 모습), 장식 창의 입어 보기 (preview).
 * 그리기 (CosmeticRender · CosmeticLayer · CosmeticAura) 는 {@link #lookOf} 로 모습을 얻음 — 내 모습은 장식 창이 열려 있으면 입어 보는 모습.
 */
public final class ClientCosmetics {
    private ClientCosmetics() {}

    private static final Map<UUID, Look> LOOKS = new HashMap<>();
    private static Set<String> owned = Set.of();
    private static Look mine = Look.EMPTY;
    private static boolean received;
    /** 장식 창의 입어 보기 (null = 없음) */
    private static Look preview;

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CosmeticNet.StatePayload.TYPE, (payload, context) -> {
            String open = update(payload.json());
            if (open != null && !open.isEmpty() && context.client().player != null) {
                if (context.client().gui.screen() instanceof CosmeticScreen s) s.refresh();
                else context.client().gui.setScreen(new CosmeticScreen());
            } else if (context.client().gui.screen() instanceof CosmeticScreen s) {
                s.refresh();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CosmeticNet.LooksPayload.TYPE, (payload, context) -> looks(payload.full(), payload.json()));
    }

    /** 내 장식 받기 → 열 탭 ("" = 열지 않음) */
    @SuppressWarnings("unchecked")
    static String update(String json) {
        Map<String, Object> m;
        try {
            m = Json.parseObject(json);
        } catch (RuntimeException e) {
            return null;
        }
        Set<String> o = new LinkedHashSet<>();
        if (m.get("owned") instanceof List<?> l) for (Object x : l) o.add(String.valueOf(x));
        owned = Set.copyOf(o);
        mine = m.get("look") instanceof Map<?, ?> lm ? Look.fromMap(lm) : Look.EMPTY;
        received = true;
        Player p = Minecraft.getInstance().player;
        if (p != null) LOOKS.put(p.getUUID(), mine);
        return m.get("open") == null ? "" : String.valueOf(m.get("open"));
    }

    /** 모습들 받기 (full = 모두 바꿈, 값이 null 이면 그 사람을 뺌) */
    static void looks(boolean full, String json) {
        Map<String, Object> m;
        try {
            m = Json.parseObject(json);
        } catch (RuntimeException e) {
            return;
        }
        if (full) {
            Player p = Minecraft.getInstance().player;
            Look keep = p == null ? null : LOOKS.get(p.getUUID());
            LOOKS.clear();
            if (keep != null) LOOKS.put(p.getUUID(), keep);
        }
        for (Map.Entry<String, Object> e : m.entrySet()) {
            UUID id;
            try {
                id = UUID.fromString(e.getKey());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (e.getValue() instanceof Map<?, ?> lm) LOOKS.put(id, Look.fromMap(lm));
            else LOOKS.remove(id);
        }
    }

    /** 이 사람의 모습 (내 것은 입어 보는 중이면 그 모습, 없으면 빈 모습) */
    public static Look lookOf(UUID id) {
        if (id == null) return Look.EMPTY;
        Player p = Minecraft.getInstance().player;
        if (preview != null && p != null && id.equals(p.getUUID())) return preview;
        Look l = LOOKS.get(id);
        return l == null ? Look.EMPTY : l;
    }

    /** 모습이 있는 사람이 있는지 (그리기에서 빨리 건너뛰기) */
    public static boolean any() {
        return !LOOKS.isEmpty() || preview != null;
    }

    public static Set<String> owned() {
        return owned;
    }

    public static boolean owns(String id) {
        return owned.contains(id);
    }

    /** 서버가 보낸 내 모습 (입어 보기 아님) */
    public static Look mine() {
        return mine;
    }

    public static boolean received() {
        return received;
    }

    public static void preview(Look look) {
        preview = look;
    }

    public static Look preview() {
        return preview;
    }

    /** 가진 에메랄드 (내 가방 — 서버 Inv.money 와 같게 핫바 + 가방 36칸) */
    public static int money() {
        Player p = Minecraft.getInstance().player;
        if (p == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.EMERALD)) n += s.getCount();
        }
        return n;
    }

    /** 시험용: 이 사람의 모습을 바로 넣음 (서버를 거치지 않음) */
    public static void putLook(UUID id, Look look) {
        if (look == null) LOOKS.remove(id);
        else LOOKS.put(id, look);
    }

    static void clear() {
        LOOKS.clear();
        owned = Set.of();
        mine = Look.EMPTY;
        received = false;
        preview = null;
    }

    static void send(String action, String a, String b) {
        ClientPlayNetworking.send(new CosmeticNet.ActionPayload(action, a == null ? "" : a, b == null ? "" : b));
    }
}
