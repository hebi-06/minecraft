package mmorpg.fabric.mount;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.RpgMount;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 탈것에 탄 동안 막기 (riding.js — 사용자 요청: "타면서 몬스터를 잡을 수 없고 스킬도 못 사용하게"). 다른 모듈이 부르는 작은 판정만 (부르기 · 사라짐은 Mounts):
 * 공격 (평타 · 활 · 완드 · 스킬 · 룬의 피해 — Mounts 의 피해 막기), 무기 스킬 (Transcend.castSkill), 룬 (RuneCast.castRune), 활 · 완드 평타 (RpgCombat),
 * 근접 참격 연출 (Slash), 지속 피해로 잡기 (Loot.mounted). 우리 탈것 (RpgMount) 만 — 배 · 말 · 다른 모드의 탈것은 그대로.
 */
public final class Riding {
    private Riding() {}

    /** 막힘 안내 간격 (틱 — rules.json MOUNT.warnEvery 40) */
    public static final int WARN_EVERY = (int) mmorpg.core.Rules.get().obj("MOUNT").num("warnEvery");
    /** 마지막으로 막힘을 알린 틱 */
    private static final Map<UUID, Long> WARNED_AT = new HashMap<>();

    /** 우리 탈것을 타고 있는지 (서버 · 클라이언트 둘 다) */
    public static boolean isMounted(Player p) {
        return p != null && p.getVehicle() instanceof RpgMount;
    }

    /**
     * 탈것에 탔으면 막음: 화면 하단 안내 + 낮은 소리 (WARN_EVERY 틱에 한 번), true. 타지 않았으면 false.
     * @param what "attack" (공격할 수 없습니다) · "skill" (스킬 · 룬을 쓸 수 없습니다)
     */
    public static boolean blocked(ServerPlayer sp, String what) {
        if (!isMounted(sp)) return false;
        long now = Tasks.now();
        Long last = WARNED_AT.get(sp.getUUID());
        if (last == null || now - last >= WARN_EVERY) {
            WARNED_AT.put(sp.getUUID(), now);
            Notify.notify(sp, "§c탈것에 탄 동안에는 " + ("attack".equals(what) ? "공격할 수 없습니다" : "스킬 · 룬을 쓸 수 없습니다") + " §7(웅크리면 내림)", 30);
            SkillFx.playFor(sp, "note.bass", 0.6, 0.6);
        }
        return true;
    }

    static void forget(UUID id) {
        WARNED_AT.remove(id);
    }

    static void clear() {
        WARNED_AT.clear();
    }
}
