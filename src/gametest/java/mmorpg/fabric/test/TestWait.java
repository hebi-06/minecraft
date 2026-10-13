package mmorpg.fabric.test;

import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * 시험 서버의 "될 때까지 기다림" 도구. 시험 서버는 틱을 쉬지 않고 돌려서 (빠른 CI 러너일수록 더 많이) 청크를 불러오거나 엔티티가 돌기 시작하는 일
 * (다른 스레드 · 실제 시간) 이 몇십 ~ 몇백 틱 늦게 끝날 수 있음 — 시험 자리 밖 (먼 보스 필드 · 사냥터 · 자리 밖 과녁) 의 몹을 쓰는 시험은
 * 정해진 틱 대신 그 자리의 몹이 도는지 확인하고 시작함 (2026-10-13, 저장소를 공개로 바꾼 뒤 러너가 빨라지며 드러남).
 */
final class TestWait {
    private TestWait() {}

    /** cond 가 참이 될 때까지 (틱마다 확인, 최대 max 틱) 기다렸다가 then — 시간이 다 되어도 then (그 안의 확인이 실패를 알림) */
    static void await(GameTestHelper h, BooleanSupplier cond, int max, Runnable then) {
        if (cond.getAsBoolean() || max <= 0) {
            then.run();
            return;
        }
        h.runAfterDelay(1, () -> await(h, cond, max - 1, then));
    }

    /** 그 자리의 몹이 도는지 (청크가 엔티티 틱 범위 + 엔티티 구역이 도는 상태 — 바닐라가 몹을 자연 생성하는 조건과 같음) */
    static boolean entitiesTick(ServerLevel level, Vec3 at) {
        return level.canSpawnEntitiesInChunk(ChunkPos.containing(BlockPos.containing(at)));
    }
}
