package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 룬 (runes.js registerRunes · forgetRunes): 룬 도구 · 하늘빛 · 쓰기 · 스킬 칸 (11단계) · 뽑기 · 명령어 · 보호막 연출을 켜고, 계열 16개 (RuneFamilies) 의 룬 효과를 등록.
 * 나간 사람의 기록은 forget 이 모두 지움 (계열이 넣은 것도 — onForget).
 */
public final class RuneSystem {
    private RuneSystem() {}

    private static final List<Consumer<UUID>> FORGET = new ArrayList<>();
    /** 보호막이 피해를 막은 연출을 한 틱 (너무 자주 하지 않게) */
    private static final Map<UUID, Long> SHIELD_FX_AT = new HashMap<>();

    /** Mmorpg.onInitialize (아이템은 RpgItems.init 이 먼저 등록) */
    public static void init() {
        RuneData.init();
        RuneKit.init();
        RuneMarks.init(); // 0.49.0 룬 유틸 표식 (받는 피해 증가 · 가하는 피해 증가)
        Ult.init();
        RuneCast.init();
        SkillBar.init(); // 스킬 칸 (11단계 — 룬을 올려 키로 씀 · 저장 · 패킷)
        Breakthrough.init(); // 룬 돌파 (8단계 — 시전 단계 기록)
        RuneGacha.init();
        RuneCommands.init();
        RpgHealth.shieldBroken = RuneSystem::onShieldBreak;
        RpgHealth.shieldAbsorbed = (sp, amount) -> onShieldAbsorb(sp);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 10 == 0) for (ServerPlayer sp : server.getPlayerList().getPlayers()) shieldTick(sp);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHIELD_FX_AT.clear());
        RuneFamilies.registerAll();
        Mmorpg.LOG.info("MMO RPG: rune effects {}", RuneFamilies.count());
    }

    /** 나간 사람의 룬 기록을 지울 때 할 일 (계열마다 넣음) */
    public static void onForget(Consumer<UUID> fn) {
        FORGET.add(fn);
    }

    /** 나감 (RpgPlayers): 이 사람의 룬 기록을 모두 지움 */
    public static void forget(UUID id) {
        RuneKit.forget(id);
        Ult.forget(id);
        RuneCast.forget(id);
        RuneGacha.forget(id);
        SHIELD_FX_AT.remove(id);
        for (Consumer<UUID> fn : FORGET) {
            try {
                fn.accept(id);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] rune forget", e);
            }
        }
    }

    /** 보호막이 깨짐: 유리 깨지는 소리 + 금빛 조각 */
    private static void onShieldBreak(ServerPlayer sp) {
        ServerLevel level = sp.level();
        Vec3 at = V.up(sp.position(), 1.0);
        double[][] holy = RuneKit.colorOf("holy");
        SkillFx.burst(level, at, holy[0], 1.3, 0.3);
        SkillFx.sparks(level, at, holy[1], 10, 5);
        SkillFx.play(level, "random.glass", at, 1.2, 0.8);
        Notify.notify(sp, "§6보호막이 깨졌습니다", 24);
    }

    /** 보호막이 피해를 막음: 작은 금빛 고리 (5틱에 한 번) */
    private static void onShieldAbsorb(ServerPlayer sp) {
        long now = Tasks.now();
        Long last = SHIELD_FX_AT.get(sp.getUUID());
        if (last != null && now - last < 5) return;
        SHIELD_FX_AT.put(sp.getUUID(), now);
        ServerLevel level = sp.level();
        SkillFx.ring(level, V.up(sp.position(), 1.0), sp.getViewVector(1F), RuneKit.colorOf("holy")[0], 1.0, 0.3, 0);
        SkillFx.play(level, "item.shield.block", sp.position(), 1.4, 0.6);
    }

    /** 보호막이 있는 동안 몸 둘레 금빛 고리 (다른 사람에게도 보임 — 10틱마다) */
    private static void shieldTick(ServerPlayer sp) {
        if (RpgHealth.shieldOf(sp) <= 0) return;
        SkillFx.fx(sp.level(), "rpg:bash_ring", V.up(sp.position(), 1.0), Opt.of("dir", sp.getViewVector(1F), "size", 0.85, "life", 0.5, "color",
                RuneKit.colorOf("holy")[0], "alpha", 0.45));
    }
}
