package mmorpg.fabric.skill;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 보스 기믹이 플레이어에게 거는 상태 (status.js): 속박 (걷기 · 뛰기 막음 — 이동 속도 · 점프 힘 0), 침묵 (무기 스킬 · 룬 못 씀, 머리 위 보라 고리).
 * 저장하지 않음: 죽거나 · 다시 태어나거나 · 나가면 풂.
 */
public final class Status {
    private Status() {}

    private static final Identifier ROOT_ID = Mmorpg.id("root");
    /** 속박: 플레이어 → 끝나는 틱 */
    private static final Map<UUID, Long> ROOTS = new HashMap<>();
    /** 침묵: 플레이어 → 끝나는 틱 */
    private static final Map<UUID, Long> SILENCES = new HashMap<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 2 == 0) tick(server);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) clear(sp);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clear(newPlayer));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ROOTS.clear();
            SILENCES.clear();
        });
    }

    private static void setMove(ServerPlayer sp, boolean on) {
        for (AttributeInstance a : new AttributeInstance[] {sp.getAttribute(Attributes.MOVEMENT_SPEED), sp.getAttribute(Attributes.JUMP_STRENGTH)}) {
            if (a == null) continue;
            if (on) a.removeModifier(ROOT_ID);
            else a.addOrUpdateTransientModifier(new AttributeModifier(ROOT_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** 속박 (걷기 · 뛰기 막음). 이미 걸려 있으면 더 늦게 끝나는 쪽으로. label: 눈앞 글자 (예: "짚단 감옥") */
    public static void root(ServerPlayer sp, int ticks, String label) {
        if (sp.isRemoved()) return;
        long until = Tasks.now() + Math.max(1, ticks);
        Long cur = ROOTS.get(sp.getUUID());
        ROOTS.put(sp.getUUID(), Math.max(until, cur == null ? 0 : cur));
        setMove(sp, false);
        if (cur == null) {
            DamageNumbers.showFace(sp, "§6§l" + label + "!", 2.2, 0, -0.35);
            SkillFx.play(sp.level(), "place.chain", sp.position(), 0.7, 1);
        }
    }

    public static void root(ServerPlayer sp, int ticks) {
        root(sp, ticks, "속박");
    }

    /** 속박 풀기 (감옥이 부서짐 등). 걸려 있었는지 */
    public static boolean unroot(ServerPlayer sp) {
        boolean had = ROOTS.remove(sp.getUUID()) != null;
        if (!sp.isRemoved()) setMove(sp, true);
        return had;
    }

    public static boolean isRooted(ServerPlayer sp) {
        Long u = ROOTS.get(sp.getUUID());
        return u != null && u > Tasks.now();
    }

    /** 침묵 (무기 스킬 · 룬 못 씀). 이미 걸려 있으면 더 늦게 끝나는 쪽으로 */
    public static void silence(ServerPlayer sp, int ticks) {
        if (sp.isRemoved()) return;
        long until = Tasks.now() + Math.max(1, ticks);
        SILENCES.merge(sp.getUUID(), until, Math::max);
        DamageNumbers.showFace(sp, "§5§l침묵!", 2.2, 0, 0.1);
        SkillFx.play(sp.level(), "mob.evocation_illager.cast_spell", sp.position(), 0.6, 1);
    }

    public static boolean unsilence(ServerPlayer sp) {
        return SILENCES.remove(sp.getUUID()) != null;
    }

    /** 남은 침묵 (틱, 없으면 0) */
    public static long silenceLeft(ServerPlayer sp) {
        Long u = SILENCES.get(sp.getUUID());
        return u == null ? 0 : Math.max(0, u - Tasks.now());
    }

    public static boolean isSilenced(ServerPlayer sp) {
        return silenceLeft(sp) > 0;
    }

    /** 침묵 중이면 알리고 true (스킬 · 룬 입구에서) */
    public static boolean blockedBySilence(ServerPlayer sp) {
        long left = silenceLeft(sp);
        if (left <= 0) return false;
        Notify.notify(sp, String.format(Locale.ROOT, "§5침묵 중 §7— 스킬·룬을 쓸 수 없습니다 §f%.1f초", left / 20.0), 30);
        SkillFx.playFor(sp, "note.bass", 0.5, 0.8);
        return true;
    }

    /**
     * 즉사 (보스 기믹 — status.js kill · hp.js instantKill): RPG 체력을 0 으로 (보호막 · 불사의 맹약 무시, 불사의 토템은 바닐라처럼 막음).
     * 크리에이티브 · 관전 · 이미 쓰러지는 사람은 무시 (false).
     * 자바: 때린 것 (attacker) 의 마법 피해 100000 (그동안 이 사람의 불사의 맹약만 끔) → 그래도 살아 있고 토템을 쓰지 않았으면 (저항 등) /kill 과 같은 피해
     */
    public static boolean kill(ServerPlayer sp, net.minecraft.world.entity.Entity attacker) {
        if (sp == null || sp.isRemoved() || !sp.isAlive() || sp.isDeadOrDying()) return false;
        net.minecraft.world.level.GameType gm = sp.gameMode.getGameModeForPlayer();
        if (gm == net.minecraft.world.level.GameType.CREATIVE || gm == net.minecraft.world.level.GameType.SPECTATOR) return false;
        int totems = totems(sp);
        java.util.function.Predicate<ServerPlayer> undying = mmorpg.fabric.RpgHealth.undying;
        mmorpg.fabric.RpgHealth.undying = p -> p != sp && undying.test(p);
        try {
            net.minecraft.server.level.ServerLevel level = sp.level();
            net.minecraft.world.damagesource.DamageSource src = attacker != null && !attacker.isRemoved()
                    ? level.damageSources().indirectMagic(attacker, attacker) : level.damageSources().magic();
            sp.hurtServer(level, src, 100000F);
            if (!sp.isRemoved() && sp.isAlive() && !sp.isDeadOrDying() && totems(sp) >= totems) sp.kill(level);
        } finally {
            mmorpg.fabric.RpgHealth.undying = undying;
        }
        return true;
    }

    /** 두 손의 불사의 토템 수 (토템이 막았는지 보려고) */
    private static int totems(ServerPlayer sp) {
        int n = 0;
        for (net.minecraft.world.item.ItemStack s : new net.minecraft.world.item.ItemStack[] {sp.getMainHandItem(), sp.getOffhandItem()}) {
            if (s.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        return n;
    }

    /** 상태를 모두 풂 (죽음 · 나감 · 다시 태어남) */
    public static void clear(ServerPlayer sp) {
        ROOTS.remove(sp.getUUID());
        SILENCES.remove(sp.getUUID());
        if (!sp.isRemoved()) setMove(sp, true);
    }

    static void forget(UUID id) {
        ROOTS.remove(id);
        SILENCES.remove(id);
    }

    private static void tick(MinecraftServer server) {
        long now = Tasks.now();
        for (Iterator<Map.Entry<UUID, Long>> it = ROOTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> r = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(r.getKey());
            if (sp == null) {
                it.remove();
                continue;
            }
            if (r.getValue() <= now) {
                it.remove();
                setMove(sp, true);
            }
        }
        for (Iterator<Map.Entry<UUID, Long>> it = SILENCES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> s = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(s.getKey());
            if (sp == null || s.getValue() <= now) {
                it.remove();
                continue;
            }
            // 머리 위 보라 고리 (0.5초마다)
            if (now % 10 < 2) {
                Vec3 h = sp.getEyePosition();
                SkillFx.fx(sp.level(), "rpg:skill_rune", new Vec3(h.x, h.y + 0.55, h.z), Opt.of("dir", V.UP, "speed", 0.0, "spin", (double) ((now * 12) % 360),
                        "size", 0.42, "life", 0.5, "progress", 1.0, "color", new double[] {0.62, 0.25, 0.95}, "alpha", 0.9));
            }
        }
    }
}
