package mmorpg.fabric.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.GearItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 몬스터 전리품 · 경험치 · 잡은 사람 (loot.js 의 일부 — 몬스터 자체 · lootOnDeath (경험치 주기 · 전리품 떨어뜨리기) 는 5단계 몬스터가 이 함수들로).
 * 잡은 플레이어 (killCredit): 마지막 피해를 준 것이 플레이어면 그 사람, 불에 타거나 떨어져 죽거나 다른 몹이 마무리하면 CREDIT_TICKS (10초) 안에
 * 마지막으로 때린 플레이어 (noteHit — 자바는 모든 생명체의 피해에서 적음). 퀘스트 처치 수도 같은 사람에게.
 * 자바는 다시 태어나면 플레이어 객체가 바뀌어서 때린 사람을 UUID 로 적고 그때의 플레이어를 찾음.
 */
public final class Loot {
    private Loot() {}

    private static final Rules R = Rules.get();
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj MONSTER_DROPS = R.obj("MONSTER_DROPS");

    /** 때린 엔티티가 없는 죽음 (불 · 떨어짐) 을 마지막으로 때린 플레이어가 잡은 것으로 치는 시간 (10초) */
    public static final int CREDIT_TICKS = 200;
    /** 확률 (시험이 바꿈 — loot.js setLootRandom) */
    public static DoubleSupplier rand = Math::random;
    /** 탈것에 탄 동안은 잡을 수 없음 (riding.js isMounted — 6단계 탈것이 채움) */
    public static Predicate<ServerPlayer> mounted = sp -> false;

    private record Hit(UUID player, long tick) {}

    /** 몬스터 UUID → 마지막으로 때린 플레이어 · 틱 */
    private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();

    /** 전리품 아이템 ID → 몬스터 키 (몬스터 순서) */
    private static final Map<String, String> BY_ITEM;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        for (String k : R.strings("MONSTER_ORDER")) {
            Obj d = MONSTER_DROPS.objOrNull(k);
            if (d != null) m.put(d.str("item"), k);
        }
        BY_ITEM = m;
    }

    /** 플레이어가 몬스터를 때림 (monsters.js 의 entityHurt — 자바는 QuestSystem 이 피해 뒤 이벤트에서) */
    public static void noteHit(Entity entity, ServerPlayer player) {
        if (entity == null || player == null) return;
        long now = Tasks.now();
        LAST_HIT.put(entity.getUUID(), new Hit(player.getUUID(), now));
        if (LAST_HIT.size() > 256) LAST_HIT.values().removeIf(h -> now - h.tick() > CREDIT_TICKS);
    }

    /**
     * 이 죽음을 누가 잡았는지 (경험치 · 전리품 · 퀘스트 처치 수): 마지막 피해를 준 플레이어, 아니면 CREDIT_TICKS 안에 마지막으로 때린 플레이어.
     * 탈것에 탄 동안은 없음. 없으면 null
     */
    public static ServerPlayer killCredit(Entity dead, DamageSource source) {
        Entity k = source == null ? null : source.getEntity();
        if (k instanceof Player) return k instanceof ServerPlayer sp && !sp.isRemoved() && !mounted.test(sp) ? sp : null;
        if (dead == null) return null;
        Hit h = LAST_HIT.get(dead.getUUID());
        if (h == null || Tasks.now() - h.tick() > CREDIT_TICKS) return null;
        MinecraftServer server = dead.level().getServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(h.player());
        return p != null && !p.isRemoved() && !mounted.test(p) ? p : null;
    }

    /** 전리품 아이템 ID 의 몬스터 키 (전리품이 아니면 null) */
    public static String dropKeyOf(String itemId) {
        return itemId == null ? null : BY_ITEM.get(itemId);
    }

    /** 전리품 아이템 ID 목록 (몬스터 순서) */
    public static List<String> dropItemIds() {
        return new ArrayList<>(BY_ITEM.keySet());
    }

    /** 전리품 (몬스터가 떨어뜨린 것 · 퀘스트 · 명령어 모두 이것 — 설명은 아이템이 그림, 1 ~ 64개) */
    public static ItemStack dropItem(String key, int amount) {
        Obj d = MONSTER_DROPS.objOrNull(key);
        Item it = d == null ? null : GearItems.byId(d.str("item"));
        return it == null ? ItemStack.EMPTY : new ItemStack(it, Math.max(1, Math.min(64, amount)));
    }

    /** 이 플레이어는 그 몬스터에게서 경험치를 못 받는지 (최고 레벨 ≥ xpUntil) */
    public static boolean xpCapped(ServerPlayer sp, String key) {
        Obj m = key == null ? null : MONSTERS.objOrNull(key);
        return m != null && m.num("xpUntil") > 0 && RpgCombat.reqLevelOf(sp) >= m.num("xpUntil");
    }

    /** 그 몬스터를 잡으면 받는 경험치 (못 받으면 0) */
    public static int monsterXp(ServerPlayer sp, String key) {
        Obj m = key == null ? null : MONSTERS.objOrNull(key);
        return m == null || xpCapped(sp, key) ? 0 : (int) m.num("xp");
    }

    /** 레벨에 맞는 몬스터 (몬스터 레벨 ≤ 내 레벨 + 2 중 가장 높은 것) — 추천 사냥 */
    public static String huntKind(double lv) {
        return QuestText.huntKind(lv);
    }

    /** 플레이어가 나가면 때린 기록을 지움 (loot.js forgetLoot) */
    public static void forgetLoot(UUID playerId) {
        LAST_HIT.values().removeIf(h -> h.player().equals(playerId));
    }

    static void clear() {
        LAST_HIT.clear();
    }
}
