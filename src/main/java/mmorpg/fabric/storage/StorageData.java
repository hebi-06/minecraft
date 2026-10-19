package mmorpg.fabric.storage;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import mmorpg.core.Storage;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 0.22.0 엔더 창고의 저장 (플레이어 데이터 mmorpg:storage — 가방과 함께 저장 · 죽어도 그대로): 열린 상자 수 · 마지막으로 본 상자 ·
 * 상자마다 54칸. 창이 열린 동안 칸은 이 목록을 그대로 보고 고침 (복사본 없음 — 서버가 꺼질 때 저장해도 가방과 어긋나지 않음).
 * 저장 모양: {unlocked, last, items: [{page, slot, item}]} — 아이템 하나씩 읽어서 읽지 못한 것 (없어진 아이템 · 깨진 기록) 은 원래 글 그대로
 * 따로 두었다가 다시 저장 (나머지는 그대로, 다음에 읽을 수 있게 되면 그 자리로).
 */
public final class StorageData {
    /** 상자 번호 · 칸 번호의 위 끝 (기록이 이상해도 메모리를 크게 쓰지 않게) */
    static final int MAX_PAGES = 9;

    /** 열린 상자 수 (Storage.free ~ chests — 읽을 때 맞춤) */
    int unlocked = Storage.free();
    /** 마지막으로 본 상자 (0부터) */
    int last;
    /** 상자마다 54칸 (필요할 때 만듦) */
    final List<NonNullList<ItemStack>> pages = new ArrayList<>();
    /** 읽지 못한 기록 (원래 글 그대로 — 다시 저장) */
    final List<Dynamic<?>> broken = new ArrayList<>();

    public static AttachmentType<StorageData> TYPE;

    static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("storage"), b -> b.persistent(CODEC).copyOnDeath().initializer(StorageData::new));
    }

    public static StorageData of(ServerPlayer sp) {
        return sp.getAttachedOrCreate(TYPE);
    }

    /** 열린 상자 수 (설정 범위 안) */
    public int unlocked() {
        return Storage.clampUnlocked(unlocked);
    }

    /** 열린 상자 수를 정함 (운영자 명령 · 시험 — 사지 않고, 범위 안으로) */
    public void setUnlocked(int n) {
        unlocked = Storage.clampUnlocked(n);
        if (last >= unlocked) last = unlocked - 1;
    }

    /** 마지막으로 본 상자 (열린 상자 안 — 0부터) */
    public int last() {
        return Math.max(0, Math.min(unlocked() - 1, last));
    }

    /** 상자 i (0부터) 의 54칸 — 고치면 바로 저장 쪽에 들어감 */
    public NonNullList<ItemStack> page(int i) {
        int p = Math.max(0, Math.min(MAX_PAGES - 1, i));
        while (pages.size() <= p) pages.add(NonNullList.withSize(Storage.SIZE, ItemStack.EMPTY));
        return pages.get(p);
    }

    /** 앞 상자부터 처음 빈 칸 [상자, 칸] (열린 상자 안 — 없으면 null) */
    int[] firstEmpty() {
        for (int p = 0; p < unlocked(); p++) {
            NonNullList<ItemStack> items = page(p);
            for (int s = 0; s < Storage.SIZE; s++) if (items.get(s).isEmpty()) return new int[] {p, s};
        }
        return null;
    }

    /** 들어 있는 묶음 수 (상자 i) */
    public int stacks(int i) {
        int n = 0;
        for (ItemStack s : page(i)) if (!s.isEmpty()) n++;
        return n;
    }

    /** 읽지 못한 기록 수 (시험 · 운영자 안내용) */
    public int brokenCount() {
        return broken.size();
    }

    // ------------------------------------------------------------ 저장 모양

    public static final Codec<StorageData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<StorageData, T>> decode(DynamicOps<T> ops, T input) {
            Dynamic<T> root = new Dynamic<>(ops, input);
            StorageData d = new StorageData();
            d.unlocked = root.get("unlocked").asInt(Storage.free());
            d.last = root.get("last").asInt(0);
            // 지난번에 읽지 못한 기록도 다시 읽어 봄 (아이템이 다시 생겼으면 제자리로)
            Stream.concat(root.get("items").asStream(), root.get("broken").asStream()).forEach(e -> d.read(e));
            return DataResult.success(Pair.of(d, ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(StorageData d, DynamicOps<T> ops, T prefix) {
            List<T> items = new ArrayList<>();
            for (int p = 0; p < d.pages.size(); p++) {
                NonNullList<ItemStack> page = d.pages.get(p);
                for (int s = 0; s < page.size(); s++) {
                    ItemStack stack = page.get(s);
                    if (stack.isEmpty()) continue;
                    int slot = s;
                    int at = p;
                    Optional<T> item = ItemStack.CODEC.encodeStart(ops, stack)
                            .resultOrPartial(err -> Mmorpg.LOG.warn("[mmorpg] storage item {} at {}:{} not saved: {}", stack, at, slot, err));
                    if (item.isEmpty()) continue;
                    RecordBuilder<T> e = ops.mapBuilder();
                    e.add("page", ops.createInt(at));
                    e.add("slot", ops.createInt(slot));
                    e.add("item", item.get());
                    e.build(ops.empty()).result().ifPresent(items::add);
                }
            }
            List<T> raw = new ArrayList<>();
            for (Dynamic<?> b : d.broken) raw.add(b.convert(ops).getValue());
            RecordBuilder<T> out = ops.mapBuilder();
            out.add("unlocked", ops.createInt(d.unlocked));
            out.add("last", ops.createInt(d.last));
            out.add("items", ops.createList(items.stream()));
            if (!raw.isEmpty()) out.add("broken", ops.createList(raw.stream()));
            return out.build(prefix);
        }
    };

    /** 기록 하나 {page, slot, item}: 읽히면 그 칸에 (이미 차 있으면 읽지 못한 것으로), 아니면 원래 글 그대로 broken 에 */
    private <T> void read(Dynamic<T> e) {
        int p = e.get("page").asInt(-1);
        int s = e.get("slot").asInt(-1);
        Optional<ItemStack> item = e.get("item").result().flatMap(it -> ItemStack.CODEC.parse(it).result());
        if (item.isPresent() && !item.get().isEmpty() && p >= 0 && p < MAX_PAGES && s >= 0 && s < Storage.SIZE && page(p).get(s).isEmpty()) {
            page(p).set(s, item.get());
            return;
        }
        broken.add(e);
    }
}
