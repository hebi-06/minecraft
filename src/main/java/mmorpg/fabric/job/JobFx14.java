package mmorpg.fabric.job;

import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 사제 2차 그림 (tools/job_fx_art_adv.py — rpg:rfx_j*, 빛 칸 = 넓은 빛 · 심지 칸 = 테 · 결): 세인트 — 빛의 연꽃 (땅에 누움) · 큰 꽃 (카메라를 봄 —
 * 꽃봉오리 → 활짝) · 빛나는 나비 (카메라를 봄 — 날갯짓), 워록 — 핏빛 창 (가는 쪽으로 누움) · 핏물 웅덩이 (땅에 누움) · 핏물 기둥 (세워 카메라 쪽으로 돎 — 아래 끝 = 땅) ·
 * 피의 심장 (카메라를 봄 — 고동침) · 핏빛 불기둥 (세워 카메라 쪽으로 돎 — 아래 끝 = 땅). 크기 뜻은 그림 정의 (tools/job_fx_art_adv.py 의 설명) 와 같음.
 */
final class JobFx14 {
    private JobFx14() {}

    static final int LOTUS = 8;
    static final int BLOOM = 8;
    static final int FLY = 8;
    static final int LANCE = 4;
    static final int POOL = 8;
    static final int GEYSER = 8;
    static final int HEART = 8;
    static final int PYRE = 8;
    /** 핏빛 창의 창끝 (그림 너비의 몫 — 가운데에서 끝까지 = 반 길이 × 0.94) */
    static final double LANCE_HEAD = 0.94;

    /**
     * 빛의 연꽃 (rfx_jlotus — 땅에 누움, 그림 384 × 384): 0 씨앗 빛 · 1 ~ 2 꽃잎 두 겹이 열림 → 3 ~ 5 활짝 (loop 면 이어 돌림 — 꽃잎 끝의 반짝임 · 속의 맥동) → 6 꽃잎이
     * 바깥으로 떠오름 · 7 흐려짐. 꽃잎 끝 반지름 radius 칸 (= 반 크기 × 0.9)
     */
    static void lotus(ServerLevel level, Vec3 center, double radius, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jlotus", LOTUS, f, n, center, life, loop ? o.num("fps", 8) : 0, loop, o.with("dir", V.UP, "size", radius / 0.9, "spin", o.has("spin") ? o.num("spin", 0)
                : Kit.rand() * 360, "fade", o.num("fade", 0.2)), glow, core);
    }

    /**
     * 큰 꽃 (rfx_jbloom — 카메라를 봄, 그림 384 × 384): 0 꽃봉오리 (빛나는 틈) · 1 부풀어 빛이 샘 → 2 ~ 3 열림 → 4 ~ 5 활짝 (loop 면 이어 돌림 — 빛살 · 꽃가루) → 6 꽃잎이
     * 흩날림 · 7 흐려짐. 꽃 반지름 약 size × 0.85 칸
     */
    static void bloom(ServerLevel level, Vec3 at, double size, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jbloom", BLOOM, f, n, at, life, loop ? o.num("fps", 8) : 0, loop, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 20 - 10,
                "fade", o.num("fade", 0.2)), glow, core);
    }

    /**
     * 빛나는 나비 (rfx_jfly — 카메라를 봄, 그림 192 × 192): 0 ~ 5 날갯짓 (loop 면 이어 돌림) → 6 반짝이로 흩어짐 · 7 흐려짐. 날개 너비 약 size × 1.8 칸. o 의 dir · speed
     * 로 날아감
     */
    static void fly(ServerLevel level, Vec3 at, double size, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jfly", FLY, f, n, at, life, loop ? o.num("fps", 16) : 0, loop, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 24 - 12,
                "fade", o.num("fade", 0.12)), glow, core);
    }

    /** 나비가 from → to 를 ticks 틱에 날아감 (날갯짓 이어 돌림 — 반은 위로 휘어 오르고 반은 내려앉음) */
    static void flyTo(ServerLevel level, Vec3 from, Vec3 to, int ticks, double size, double rise, Opt o, double[] glow, double[] core) {
        int half = Math.max(1, ticks / 2);
        Vec3 mid = V.up(V.lerp(from, to, 0.5), rise);
        Vec3 d1 = V.sub(mid, from);
        double l1 = V.len(d1);
        if (l1 > 0.05) fly(level, from, size, 0, 6, half / 20.0, true, o.with("dir", V.scale(d1, 1 / l1), "speed", l1 / (half / 20.0)), glow, core);
        int rest = Math.max(1, ticks - half);
        RuneKit.later(half, () -> {
            Vec3 d2 = V.sub(to, mid);
            double l2 = V.len(d2);
            if (l2 > 0.05) fly(level, mid, size, 0, 6, rest / 20.0, true, o.with("dir", V.scale(d2, 1 / l2), "speed", l2 / (rest / 20.0)), glow, core);
        });
    }

    /**
     * 핏빛 창 (rfx_jlance — 가는 쪽으로 누움, 이어 돌림, 그림 320 × 96): 젖은 핏빛 창날 · 가시 · 흐르는 반짝임 · 떨어지는 핏방울 꼬리. 길이 약 size × 2 칸. from → to 를
     * ticks 틱에 날아감
     */
    static void lance(ServerLevel level, Vec3 from, Vec3 to, int ticks, double size, Opt o, double[] glow, double[] core) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.1) return;
        Vec3 u = V.scale(d, 1 / len);
        double life = Math.max(0.05, ticks / 20.0);
        // 창끝이 from 에서 나와 to 에 닿게 (가운데 = 창끝 − 반 길이 × 0.94)
        Vec3 c0 = V.add(from, u, -size * LANCE_HEAD);
        JobFx.loop(level, "jlance", LANCE, c0, life, 14, o.with("dir", u, "size", size, "speed", len / life, "fade", o.num("fade", 0.08)), glow, core);
    }

    /** 핏빛 창이 한 자리에 떠 있음 (맺힘 — 창끝 tip · 가리키는 쪽 u) */
    static void lanceHold(ServerLevel level, Vec3 tip, Vec3 u, double size, double life, Opt o, double[] glow, double[] core) {
        JobFx.loop(level, "jlance", LANCE, V.add(tip, u, -size * LANCE_HEAD), life, 14, o.with("dir", u, "size", size), glow, core);
    }

    /**
     * 핏물 웅덩이 (rfx_jpool — 땅에 누움, 그림 256 × 256): 0 튀며 퍼짐 · 1 고임 → 2 ~ 5 물결 · 거품 (loop 면 이어 돌림) → 6 빨려 들며 줄어듦 · 7 흐려짐. 웅덩이 반지름
     * radius 칸 (= 반 크기 × 0.85)
     */
    static void pool(ServerLevel level, Vec3 center, double radius, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jpool", POOL, f, n, center, life, loop ? o.num("fps", 8) : 0, loop, o.with("dir", V.UP, "size", radius / 0.85, "spin", o.has("spin") ? o.num("spin", 0)
                : Kit.rand() * 360, "fade", o.num("fade", 0.2)), glow, core);
    }

    /**
     * 핏물 기둥 (rfx_jgeyser — 세워 카메라 쪽으로 돎, 그림 192 × 384 · 아래 끝 = 땅): 0 땅이 부풀어 튐 · 1 ~ 2 솟구침 → 3 꼭대기 왕관 물보라 → 4 ~ 5 가늘어지며 방울이
     * 떨어짐 → 6 땅에 튀는 방울 · 7 흐려짐. 높이 약 height 칸 (= 반 너비 × 4)
     */
    static void geyser(ServerLevel level, Vec3 ground, double height, int f, int n, double life, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jgeyser", GEYSER, f, n, V.up(ground, height / 2), life, 0, false, o.with("size", height / 4, "fade", o.num("fade", 0.15)), glow, core);
    }

    /**
     * 피의 심장 (rfx_jheart — 카메라를 봄, 그림 256 × 256): 0 핏물이 감겨 모임 → 1 ~ 4 고동침 (loop 면 이어 돌림 — 부풀었다 줄어듦 · 핏줄 맥동 · 금빛 불티) → 5 크게
     * 부풂 → 6 터짐 · 7 방울이 흩어짐. 심장 반지름 약 size × 0.55 칸
     */
    static void heart(ServerLevel level, Vec3 at, double size, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jheart", HEART, f, n, at, life, loop ? o.num("fps", 10) : 0, loop, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 16 - 8,
                "fade", o.num("fade", 0.15)), glow, core);
    }

    /**
     * 핏빛 불기둥 (rfx_jpyre — 세워 카메라 쪽으로 돎, 그림 192 × 448 · 아래 끝 = 땅): 0 불붙음 · 1 ~ 2 솟구침 → 3 ~ 5 타오름 (loop 면 이어 돌림 — 불의 혀 · 불티 · 재) →
     * 6 무너짐 · 7 재 연기. 높이 약 height 칸 (= 반 너비 × 4.67)
     */
    static void pyre(ServerLevel level, Vec3 ground, double height, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jpyre", PYRE, f, n, V.up(ground, height / 2), life, loop ? o.num("fps", 12) : 0, loop, o.with("size", height * 96 / 448.0, "fade", o.num("fade", 0.15)),
                glow, core);
    }
}
