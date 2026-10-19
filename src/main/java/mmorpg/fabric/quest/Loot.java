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
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 몬스터 전리품 · 경험치 · 잡은 사람 (loot.js — 몬스터가 죽으면 5단계 몬스터 (mob.Monsters) 가 lootOnDeath 를 부름).
 * 경험치: 구슬 대신 잡은 사람에게 바로 (몸 자리에 "+N 경험치" · 플레이어 쪽으로 날아가는 연두 빛 · 구슬 소리), 최고 레벨이 xpUntil 이상이면 0 —
 * 몬스터마다 채팅으로 한 번 알림 (10분에 한 번, 추천 사냥과 함께). 전리품: MONSTER_DROPS 의 확률로 1개가 몸 자리에 (짧은 빛기둥 · 반짝이 · 소리).
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

    /** 그 몬스터를 잡으면 받는 경험치 (못 받으면 0 — 0.24.2 최고 레벨이어도) */
    public static int monsterXp(ServerPlayer sp, String key) {
        Obj m = key == null ? null : MONSTERS.objOrNull(key);
        return m == null || xpCapped(sp, key) || mmorpg.core.Leveling.atMax(sp.experienceLevel) ? 0 : (int) m.num("xp");
    }

    /** 레벨에 맞는 몬스터 (몬스터 레벨 ≤ 내 레벨 + 2 중 가장 높은 것) — 추천 사냥 */
    public static String huntKind(double lv) {
        return QuestText.huntKind(lv);
    }

    // ------------------------------------------------------------ 죽음 (lootOnDeath)

    private static final double[] XP_COLOR = {0.65, 1.0, 0.32};
    /** 경험치 없음 알림: 같은 몬스터는 이 틱에 한 번 (10분) */
    private static final int NOTE_GAP = 12000;
    /** "플레이어|몬스터" → 마지막으로 알린 틱 */
    private static final Map<String, Long> NOTED = new HashMap<>();

    /**
     * 몬스터가 죽음 (monsters.js 의 entityDie): 잡은 플레이어 (killCredit) 가 있으면 경험치 + 전리품.
     * @param dead 몬스터 (몸은 쓰러지는 중 — 자리 · 차원만 씀)
     * @param key 몬스터 키 (MONSTERS)
     * @param killer 잡은 플레이어 (killCredit, 없으면 null)
     * @param height 몬스터 키 (연출 높이)
     */
    public static void lootOnDeath(Entity dead, String key, ServerPlayer killer, double height) {
        if (killer == null || killer.isRemoved() || dead == null || !(dead.level() instanceof ServerLevel level)) return;
        Vec3 at = dead.position();
        giveXp(killer, key, level, V.up(at, Math.max(0.6, height * 0.75)));
        rollDrop(key, level, at);
    }

    /** 경험치를 주거나 (연출), 못 받으면 알림 */
    static void giveXp(ServerPlayer sp, String key, ServerLevel level, Vec3 from) {
        int xp = monsterXp(sp, key);
        if (xp <= 0) {
            capNote(sp, key);
            return;
        }
        sp.giveExperiencePoints(xp);
        DamageNumbers.showAt(level, from, "§a+" + xp + " 경험치");
        // 몸에서 빠져나와 플레이어에게 빨려 드는 연두 빛 (구슬 대신) — 같은 차원에 있을 때
        if (sp.level() == level) {
            Vec3 to = V.up(sp.position(), 1.1);
            for (int i = 0; i < 4; i++) {
                Vec3 s = V.add(from, new Vec3((rand.getAsDouble() - 0.5) * 0.7, rand.getAsDouble() * 0.5, (rand.getAsDouble() - 0.5) * 0.7));
                Vec3 d = V.sub(to, s);
                double len = V.len(d);
                if (len < 0.4) continue;
                SkillFx.fx(level, "rpg:arrow_spark", s, Opt.of("dir", V.unit(d), "speed", Math.min(40, len / 0.35), "size", 0.13, "grow", 0.5, "life", 0.35,
                        "color", XP_COLOR, "alpha", 1.0));
            }
        }
        SkillFx.playFor(sp, "random.orb", 0.9 + rand.getAsDouble() * 0.5, 0.55);
    }

    /** 경험치를 못 받는 까닭을 몬스터마다 한 번 (10분에 한 번) 채팅으로: 추천 사냥 몬스터와 함께 */
    static void capNote(ServerPlayer sp, String key) {
        String k = sp.getUUID() + "|" + key;
        long now = Tasks.now();
        Long last = NOTED.get(k);
        if (last != null && now - last < NOTE_GAP) return;
        NOTED.put(k, now);
        Obj m = MONSTERS.objOrNull(key);
        if (m == null) return;
        // 0.24.2 최고 레벨: 몬스터와 상관없이 경험치가 오르지 않음
        if (mmorpg.core.Leveling.atMax(sp.experienceLevel) && !xpCapped(sp, key)) {
            sp.sendSystemMessage(Component.literal(BedrockText.toJava("§7[경험치] §f최고 레벨 " + mmorpg.core.Leveling.maxLevel() + "§7 — 더는 경험치가 오르지 않습니다")));
            return;
        }
        String bestKey = huntKind(RpgCombat.reqLevelOf(sp));
        Obj best = bestKey == null ? null : MONSTERS.objOrNull(bestKey);
        String hint = best != null && !bestKey.equals(key) ? " §8· §7추천 사냥: §f" + best.str("name") + " (Lv." + (int) best.num("level") + ")" : "";
        sp.sendSystemMessage(Component.literal(BedrockText.toJava("§7[경험치] §f" + m.str("name") + "§7" + eunNeun(m.str("name")) + " 최고 레벨 "
                + (int) m.num("xpUntil") + "부터 경험치를 주지 않습니다" + hint)));
    }

    /** 받침이 있으면 "은", 없으면 "는" (트롤은 · 허수아비는) */
    static String eunNeun(String word) {
        if (word == null || word.isEmpty()) return "는";
        int c = word.charAt(word.length() - 1) - 0xAC00;
        return c >= 0 && c < 11172 && c % 28 != 0 ? "은" : "는";
    }

    /** 전리품: chance 로 1개가 그 자리 (0.4칸 위) 에 떨어짐 + 짧은 빛기둥 (전리품 색, 흰 심지) · 반짝이 · 소리 */
    static void rollDrop(String key, ServerLevel level, Vec3 at) {
        Obj d = MONSTER_DROPS.objOrNull(key);
        if (d == null || rand.getAsDouble() >= d.num("chance")) return;
        Vec3 spot = V.up(at, 0.4);
        ItemStack stack = dropItem(key, 1);
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(level, spot.x, spot.y, spot.z, stack);
        item.setDefaultPickUpDelay();
        if (!level.addFreshEntity(item)) return;
        double[] rgb = Kit.rgb(d.list("rgb"));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(at, 1.3), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.2, "thick", 0.22, "life", 0.9, "color", rgb, "alpha", 0.85));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(at, 1.3), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.15, "thick", 0.07, "life", 0.8, "color", SkillFx.WHITE,
                "alpha", 0.9));
        SkillFx.sparks(level, spot, rgb, 5, 2.5);
        SkillFx.play(level, "random.pop", spot, 1.2 + rand.getAsDouble() * 0.3, 0.7);
    }

    /** 플레이어가 나가면 알림 기록 · 때린 기록을 지움 (loot.js forgetLoot) */
    public static void forgetLoot(UUID playerId) {
        LAST_HIT.values().removeIf(h -> h.player().equals(playerId));
        String p = playerId + "|";
        NOTED.keySet().removeIf(k -> k.startsWith(p));
    }

    static void clear() {
        LAST_HIT.clear();
        NOTED.clear();
    }
}
