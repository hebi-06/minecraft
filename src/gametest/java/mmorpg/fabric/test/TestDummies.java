package mmorpg.fabric.test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 시험용 과녁 (쓰러지지 않는 몹): 최대 체력은 바닐라 상한 (1024) 이라 센 스킬 한 번에도 쓰러질 수 있어서,
 * 맞을 때마다 받은 피해를 더해 두고 체력을 다시 가득 채움 (쓰러질 피해도 막고 채움). 이름표 mmorpg_test_dummy.
 */
final class TestDummies {
    private TestDummies() {}

    static final String TAG = "mmorpg_test_dummy";
    static final float HP = 1024F;
    private static final Map<UUID, Double> DEALT = new ConcurrentHashMap<>();
    private static boolean installed;

    /** 서버 스레드에서 한 번 (여러 번 불러도 됨) */
    static synchronized void install() {
        if (installed) return;
        installed = true;
        ServerLivingEntityEvents.AFTER_DAMAGE.register((e, source, base, taken, blocked) -> {
            if (!e.entityTags().contains(TAG)) return;
            DEALT.merge(e.getUUID(), (double) taken, Double::sum);
            e.setHealth(e.getMaxHealth());
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((e, source, amount) -> {
            if (!e.entityTags().contains(TAG)) return true;
            e.setHealth(e.getMaxHealth()); // 이어서 AFTER_DAMAGE 가 이 피해를 더함
            return false;
        });
    }

    /** 과녁으로 만듦 (움직이지 않음 · 방어 0 · 체력 1024 · 쓰러지지 않음) */
    static <T extends Mob> T make(T m) {
        install();
        m.setNoAi(true);
        m.setSilent(true);
        m.setPersistenceRequired();
        m.getAttribute(Attributes.ARMOR).setBaseValue(0);
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HP);
        m.setHealth(HP);
        m.addTag(TAG);
        return m;
    }

    /** 이 과녁이 받은 피해 합 */
    static double dealt(LivingEntity e) {
        return DEALT.getOrDefault(e.getUUID(), 0.0);
    }

    static void reset() {
        DEALT.clear();
    }
}
