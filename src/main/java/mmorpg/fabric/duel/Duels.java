package mmorpg.fabric.duel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 결투 상태 (duel.js — 누가 누구와 결투 중인지만, 명령 · 창 · 연출은 {@link DuelSystem}).
 * 전투 (평타 · 활 · 완드 · 피해 막기 · 배율) · 스킬 (Kit.duelPartner) · 룬 (RuneKit — 대상 · 아군 효과) · 상태 줄 (Notify 덧붙임) 이 읽음.
 */
public final class Duels {
    private Duels() {}

    /** 애드온 결투 설정 (rules.json PVP) */
    public static final Obj PVP = Rules.get().obj("PVP");

    /** 결투 하나 (한 사람 쪽): 상대 · 상대 이름 · 피해가 들어가기 시작하는 틱 (3 · 2 · 1 뒤) · 수락한 틱 · 같은 결투 표시 */
    public record Duel(UUID partner, String partnerName, long start, long since, int token) {}

    private static final Map<UUID, Duel> DUELS = new HashMap<>();
    private static int nextToken = 1;
    /** 결투 상대에게 마지막으로 맞은 틱 (맞은 사람 → 틱): 그 뒤 PVP.blameTicks 안의 불 · 용암 · 떨어짐 · 위더는 결투 상대 탓 */
    private static final Map<UUID, Long> PARTNER_HITS = new HashMap<>();

    /** 이 플레이어의 결투 (없으면 null) */
    public static Duel of(UUID id) {
        return DUELS.get(id);
    }

    /** 결투 중 (3 · 2 · 1 포함) 상대 (없으면 null) */
    public static UUID partnerId(Player p) {
        Duel d = p == null ? null : DUELS.get(p.getUUID());
        return d == null ? null : d.partner();
    }

    /** a 와 b 가 서로 결투 상대이고 결투가 시작됐는지 (3 · 2 · 1 이 끝남). 이때만 플레이어끼리 피해가 들어감 */
    public static boolean isPartner(Player a, Player b) {
        if (a == null || b == null || a == b || a.getUUID().equals(b.getUUID())) return false;
        Duel d = DUELS.get(a.getUUID());
        Duel o = DUELS.get(b.getUUID());
        return d != null && o != null && d.partner().equals(b.getUUID()) && o.partner().equals(a.getUUID()) && Tasks.now() >= d.start();
    }

    /** 결투 시작 (둘 다 적음). 돌려주는 token 으로 나중에 같은 결투인지 확인 */
    static int set(ServerPlayer a, ServerPlayer b, long start) {
        int token = nextToken++;
        long now = Tasks.now();
        DUELS.put(a.getUUID(), new Duel(b.getUUID(), b.getPlainTextName(), start, now, token));
        DUELS.put(b.getUUID(), new Duel(a.getUUID(), a.getPlainTextName(), start, now, token));
        return token;
    }

    /** 결투를 지움 (둘 다). 지운 결투 (없었으면 null) */
    static Duel clear(UUID id) {
        Duel d = DUELS.remove(id);
        if (d == null) return null;
        Duel o = DUELS.get(d.partner());
        if (o != null && o.partner().equals(id)) DUELS.remove(d.partner());
        PARTNER_HITS.remove(id);
        PARTNER_HITS.remove(d.partner());
        return d;
    }

    /** 결투 중인지 (3 · 2 · 1 포함) */
    public static boolean inDuel(UUID id) {
        return DUELS.containsKey(id);
    }

    /** 도움 (회복 · 보호막 · 방벽) 을 줄 수 있는 사람인지: 자기 자신, 또는 결투 중이 아닌 사람 (결투 중인 두 사람은 밖에서 돕지 못함 · 결투 상대도 아님) */
    public static boolean canAssist(ServerPlayer caster, ServerPlayer pl) {
        return pl == caster || (caster != null && pl.getUUID().equals(caster.getUUID())) || !DUELS.containsKey(pl.getUUID());
    }

    /** 피해를 준 플레이어: 플레이어 자신 (화살 등을 쏜 사람 포함), 또는 길들인 동물 (늑대 등) 의 주인 (없으면 null) */
    public static ServerPlayer attackerOf(DamageSource src) {
        Entity e = src == null ? null : src.getEntity();
        if (e instanceof ServerPlayer p) return p;
        if (e instanceof OwnableEntity o && o.getOwnerReference() != null) {
            LivingEntity owner = o.getOwner();
            return owner instanceof ServerPlayer p ? p : null;
        }
        return null;
    }

    /** 길들인 동물의 피해인데 주인이 접속하지 않았거나 불러오지 않음 (결투일 수 없으므로 막음) */
    public static boolean petOfAbsentOwner(DamageSource src) {
        Entity e = src == null ? null : src.getEntity();
        return !(e instanceof Player) && e instanceof OwnableEntity o && o.getOwnerReference() != null && !(o.getOwner() instanceof ServerPlayer);
    }

    /** 결투 상대의 피해가 들어감 (그 뒤 잠깐의 불 · 용암 · 떨어짐은 결투 상대 탓) */
    static void notePartnerHit(UUID victim) {
        PARTNER_HITS.put(victim, Tasks.now());
    }

    /** 결투 상대에게 마지막으로 맞은 틱 (없으면 -100000) */
    static long partnerHitAt(UUID victim) {
        return PARTNER_HITS.getOrDefault(victim, -100000L);
    }

    /** 지금 결투가 하나라도 있는지 */
    public static boolean any() {
        return !DUELS.isEmpty();
    }

    /** 결투 중인 플레이어들 */
    public static List<UUID> duelists() {
        return new ArrayList<>(DUELS.keySet());
    }

    /** 결투 상대에게 들어가는 피해: × PVP.mult, PVP.capRatio > 0 이면 받는 사람 최대 체력 × capRatio 까지 (규칙 코어 Pvp.duelDamage) */
    public static double damage(ServerPlayer victim, double dmg) {
        return mmorpg.core.Pvp.duelDamage(RpgHealth.maxOf(victim), dmg);
    }

    /** 상태 줄 덧붙임: 결투 상대 이름 · 남은 체력 (3 · 2 · 1 동안은 남은 초) */
    public static String hud(ServerPlayer p) {
        Duel d = DUELS.get(p.getUUID());
        if (d == null) return "";
        long now = Tasks.now();
        if (now < d.start()) return "§e⚔ " + d.partnerName() + " §f" + (long) Math.ceil((d.start() - now) / 20.0) + "초 뒤 결투";
        ServerPlayer o = p.level().getServer().getPlayerList().getPlayer(d.partner());
        String pct = o == null ? "" : " §f" + Math.max(0, Math.round(o.getHealth() / Math.max(1F, o.getMaxHealth()) * 100)) + "%";
        return "§c⚔ " + d.partnerName() + pct;
    }

    static void clearAll() {
        DUELS.clear();
        PARTNER_HITS.clear();
    }
}
