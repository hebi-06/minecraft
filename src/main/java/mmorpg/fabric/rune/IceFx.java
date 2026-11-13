package mmorpg.fabric.rune;

import java.util.List;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-13 직업 스킬이 쓰는 3D 얼음 (룬 빙하의 결정 — 꼭짓점으로 세운 얼음 블록, 사용자 "만년빙하처럼" · 직업 도감 메모 "얼음 연출을 3d로"): 솟는 결정 ·
 * 몸을 가둔 얼음 덩어리 · 얼음 조각 파티클. 모양 · 솟는 법은 {@link RuneGlacier} 와 같음
 */
public final class IceFx {
    private IceFx() {}

    /**
     * 솟는 결정 하나: base (땅) 에서 axis 쪽으로 땅 위 높이 h · 가장 굵은 곳 지름 w · 축 둘레 spin 도 — 땅속에 띄운 뒤 delay 틱 (2 이상) 뒤 rise 틱 동안 솟고
     * hold 틱 머문 뒤 brk 틱 동안 오그라들며 가라앉음. core = 속에 푸른 얼음 · packed = 겉이 굳은 얼음 (불투명 — 아니면 맑은 얼음). 띄운 표시 목록
     */
    public static List<Display> crystal(ServerLevel level, Vec3 base, Vec3 axis, double h, double w, double spin, boolean core, boolean packed, int delay, int rise,
            int hold, int brk) {
        RuneGlacier.Crystal c = new RuneGlacier.Crystal(V.unit(axis), h, w, spin);
        return RuneGlacier.erupt(level, base, c, core, packed ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState(), delay, rise, hold, brk);
    }

    /** 위에서 tilt 도 · 수평 방위 dir 쪽으로 기운 축 */
    public static Vec3 tilted(Vec3 dir, double tilt) {
        return RuneGlacier.tilted(dir, tilt);
    }

    /** 몸을 가둔 얼음 덩어리 (feet 에 — 투명한 얼음 블록 + 삐죽한 조각 넷, 2틱 동안 커짐): life 틱 뒤 사라짐 (먼저 지우려면 {@link mmorpg.fabric.FxDisplays#remove}) */
    public static List<Display> encase(ServerLevel level, LivingEntity e, Vec3 feet, int life) {
        return RuneGlacier.encase(level, e, feet, life);
    }

    /** 얼음 조각 파티클 (얼음 블록 깨짐) */
    public static void shards(ServerLevel level, Vec3 at, int n, double spread, double up, double speed) {
        RuneGlacier.shards(level, at, n, spread, up, speed);
    }

    /** 눈송이 파티클 */
    public static void snow(ServerLevel level, Vec3 at, int n, double spread, double up, double speed) {
        RuneGlacier.snow(level, at, n, spread, up, speed);
    }

    /** 땅 높이 (x, z) — hint 둘레에서 못 찾으면 hint */
    public static double groundY(ServerLevel level, double x, double z, double hint) {
        return RuneGlacier.groundY(level, x, z, hint);
    }
}
