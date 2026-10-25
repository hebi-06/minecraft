package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmorpg.core.Relics;
import mmorpg.fabric.gear.RelicItem;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.relic.RelicNet;
import mmorpg.fabric.relic.RelicSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 0.43.0 아티팩트 판 클라이언트 상태: 서버가 보낸 칸 아홉 (아이템 · 고른 스탯 줄) · 칸마다 연출 시각 (꽂음 · 뺌 · 줄 바꿈) · 패시브 단계가 오른 시각,
 * 판 평가 (core Relics.evaluate — 서버와 같은 식: 이웃 연결 · 착용 레벨). 스탯 시트 (ClientRpgState.sheet) 가 스탯 트리 · 장신구 합에 더해 씀.
 */
public final class ClientRelics {
    private ClientRelics() {}

    public static final int SIZE = RelicSystem.SIZE;

    private static List<ItemStack> items = emptyItems();
    private static List<String> stats = defaultStats();
    private static boolean received;
    /** 칸마다 연출 시각 (밀리초, 0 = 없음) */
    static final long[] EQUIP_AT = new long[SIZE];
    static final long[] REMOVE_AT = new long[SIZE];
    static final long[] STAT_AT = new long[SIZE];
    /** 패시브 단계가 오른 시각 (종류 → 밀리초) */
    static final Map<String, Long> TIER_AT = new HashMap<>();
    /** 합계 줄이 바뀐 시각 (스탯 줄 → 밀리초) */
    static final Map<String, Long> CHANGED_AT = new HashMap<>();

    /** 판 평가 기억 (칸이 바뀐 번호 · 그때 레벨) */
    private static int version;
    private static int boardVersion = -1;
    private static double boardLevel = Double.NaN;
    private static Relics.Board board;

    private static List<ItemStack> emptyItems() {
        List<ItemStack> l = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) l.add(ItemStack.EMPTY);
        return l;
    }

    private static List<String> defaultStats() {
        List<String> l = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) l.add(Relics.defaultLine());
        return l;
    }

    static void clear() {
        items = emptyItems();
        stats = defaultStats();
        received = false;
        Arrays.fill(EQUIP_AT, 0);
        Arrays.fill(REMOVE_AT, 0);
        Arrays.fill(STAT_AT, 0);
        TIER_AT.clear();
        CHANGED_AT.clear();
        version++;
    }

    /** 서버가 보낸 판: 바뀐 칸에 연출 (처음 받은 것은 연출 없이) + 소리, 오른 패시브 · 바뀐 합계 줄 표시. open 이면 판 창 */
    static void update(RelicNet.BoardPayload p) {
        List<ItemStack> nextItems = emptyItems();
        List<String> nextStats = defaultStats();
        for (int i = 0; i < Math.min(SIZE, p.items().size()); i++) nextItems.set(i, p.items().get(i));
        for (int i = 0; i < Math.min(SIZE, p.stats().size()); i++) if (Relics.isLine(p.stats().get(i))) nextStats.set(i, p.stats().get(i));
        long now = Util.getMillis();
        Relics.Board before = board();
        boolean filled = false;
        boolean emptied = false;
        boolean restat = false;
        int top = -1;
        if (received) {
            for (int i = 0; i < SIZE; i++) {
                boolean sameItem = ItemStack.matches(items.get(i), nextItems.get(i));
                if (!sameItem) {
                    if (!nextItems.get(i).isEmpty()) {
                        EQUIP_AT[i] = now;
                        filled = true;
                        Relics.Relic r = RelicItem.of(nextItems.get(i));
                        if (r != null) top = Math.max(top, r.gradeIndex());
                    } else {
                        REMOVE_AT[i] = now;
                        emptied = true;
                    }
                }
                if (!stats.get(i).equals(nextStats.get(i))) {
                    STAT_AT[i] = now;
                    restat = true;
                }
            }
        }
        items = nextItems;
        stats = nextStats;
        version++;
        boolean first = !received;
        received = true;
        Relics.Board after = board();
        if (!first) {
            for (String k : Relics.types()) if (after.tier(k) > before.tier(k)) TIER_AT.put(k, now);
            Set<String> keys = new HashSet<>(before.totals().keySet());
            keys.addAll(after.totals().keySet());
            for (String k : keys) {
                if (Math.abs(after.totals().getOrDefault(k, 0.0) - before.totals().getOrDefault(k, 0.0)) > 1e-9) CHANGED_AT.put(k, now);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (filled) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.85F + 0.12F * Math.max(0, top), 1.0F));
        } else if (emptied) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8F, 0.7F));
        } else if (restat) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_HIT, 1.5F, 0.6F));
        }
        if (p.open() && mc.player != null) {
            GearTabs.last = GearTabs.RELICS;
            if (!(mc.gui.screen() instanceof RelicScreen)) mc.gui.setScreen(new RelicScreen());
        }
    }

    public static boolean received() {
        return received;
    }

    /** 칸 i 의 아이템 (없으면 EMPTY) */
    public static ItemStack get(int i) {
        return i < 0 || i >= SIZE ? ItemStack.EMPTY : items.get(i);
    }

    /** 칸 i 의 아티팩트 (없으면 null) */
    public static Relics.Relic relic(int i) {
        return RelicItem.of(get(i));
    }

    /** 칸 i 의 고른 스탯 줄 */
    public static String stat(int i) {
        return i < 0 || i >= SIZE ? Relics.defaultLine() : stats.get(i);
    }

    /** 칸 i 의 강화 단계 */
    public static int enhance(int i) {
        return WeaponEnhance.of(get(i));
    }

    /** 꽂은 칸 수 */
    public static int filled() {
        int n = 0;
        for (ItemStack s : items) if (RelicItem.of(s) != null) n++;
        return n;
    }

    /** 첫 빈 칸 (없으면 -1) */
    public static int firstEmpty() {
        for (int i = 0; i < SIZE; i++) if (get(i).isEmpty()) return i;
        return -1;
    }

    /** 판 평가 (서버 RelicSystem.board 와 같은 식 — 칸 · 레벨이 바뀌면 다시 셈) */
    public static Relics.Board board() {
        double level = ClientArtifacts.level();
        if (board == null || boardVersion != version || boardLevel != level) {
            List<Relics.Cell> cells = new ArrayList<>(SIZE);
            for (int i = 0; i < SIZE; i++) {
                Relics.Relic r = relic(i);
                cells.add(r == null ? new Relics.Cell(null, stat(i), 0) : new Relics.Cell(r.id(), stat(i), enhance(i)));
            }
            board = Relics.evaluate(cells, level);
            boardVersion = version;
            boardLevel = level;
        }
        return board;
    }

    /** 스탯 줄 합 + 켜진 패시브의 스탯 몫 (상한 전 — 서버 RelicSystem.totals 와 같음) */
    public static Map<String, Double> totals() {
        return received ? board().totals() : Map.of();
    }

    /** 서버에 판 창의 일 (equip · unequip · move · stat · sync) */
    static void send(String action, int a, int b, String s) {
        if (ClientPlayNetworking.canSend(RelicNet.ActionPayload.TYPE)) ClientPlayNetworking.send(new RelicNet.ActionPayload(action, a, b, s == null ? "" : s));
    }
}
