package mmorpg.fabric.job;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 수호기사 2차 그림 (다시 — 사용자 "레인보우 컨셉이 개짜치고 반전도 이렇다할 멋이 없음" → 고른 것: 푸른 불사조 · 타락한 흑기사. tools/job_fx_art_adv.py — rpg:rfx_j*,
 * 빛 칸 = 넓은 빛 · 심지 칸 = 테 · 결): 피닉스 가드 — 푸른 불꽃 날개 한 쌍 (카메라를 봄 — 등 = 두 날개의 뿌리) · 푸른 불사조 (카메라를 봄 — 가슴) · 분노의 부채꼴 (땅에
 * 누움 — 꼭짓점에서 앞으로) · 불씨 기둥 (세워 카메라 쪽으로 돎 — 아래 끝 = 땅), 다크 나이트 — 저주받은 방패의 아가리 (카메라를 봄) · 검은 혀 (가는 쪽으로 누움 — 뿌리 =
 * 왼쪽 끝) · 땅의 거대한 아가리 (세워 카메라 쪽으로 돎 — 아래 끝 = 땅). 크기 뜻은 그림 정의 (tools/job_fx_art_adv.py 의 설명) 와 같음.
 */
final class JobFx15 {
    private JobFx15() {}

    static final int FWINGS = 8;
    static final int PHOENIX = 8;
    static final int FURY = 8;
    static final int CINDER = 8;
    static final int DMAW = 8;
    static final int TENDRIL = 4;
    static final int ABYSS = 8;
    /** 분노의 부채꼴: 꼭짓점이 칸 가운데에서 왼쪽으로 반 크기 × 0.917 · 부채 길이 = 반 크기 × 1.54 */
    static final double FURY_APEX = 0.917;
    static final double FURY_LEN = 1.54;

    /** 빛 고리 (rfx_jhalo — 땅에 누움, 반지름 radius 칸 = 반 크기 × 0.88) — 색을 직접 줌 (JobFx.halo 는 직업 색) */
    static void halo(ServerLevel level, Vec3 center, double radius, double life, Opt o, double[] glow, double[] core) {
        JobFx.two(level, "jhalo", JobFx.HALO, center, life, o.with("dir", V.UP, "size", radius / 0.88, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), glow,
                core);
    }

    /**
     * 푸른 불꽃 날개 한 쌍 (rfx_jfwings — 카메라를 봄, 그림 512 × 416 · 등 (두 날개의 뿌리) = 칸 가운데에서 반 너비 × 0.21 아래): 0 둘레의 불티가 등으로 빨려 들며 작은 날개 →
     * 1 펼쳐짐 → 2 ~ 5 날갯짓 (loop 면 이어 돌림 — 깃 끝마다 위로 휘는 불의 혀) → 6 감쌈 (깃 끝이 몸 앞 아래로 감김) → 7 불꽃 깃털로 흩어짐. 날개 끝 사이 span 칸
     * (= 반 너비 × 1.9)
     */
    static void fwings(ServerLevel level, Vec3 back, double span, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        double size = span / 1.9;
        JobFx.cells(level, "jfwings", FWINGS, f, n, V.up(back, size * 0.21), life, loop ? o.num("fps", 12) : 0, loop, o.with("size", size, "fade", o.num("fade", 0.15)),
                glow, core);
    }

    /**
     * 푸른 불사조 (rfx_jphoenix — 카메라를 봄, 그림 512 × 512 · 가슴 = 칸 가운데): 0 감겨 드는 불길 속에 생겨남 → 1 날개를 활짝 → 2 ~ 4 날갯짓 (loop 면 이어 돌림) →
     * 5 내리꽂음 (머리가 아래 · 날개를 위로 모아 V) → 6 땅에 닿아 크게 터짐 (고리 · 불줄기) → 7 깃털 · 불티로 흩어짐. 날개 끝 사이 span 칸 (= 반 크기 × 1.7 — 2026-11-06
     * 다시 그림: 꼬리 = 깃 부채 · 날개가 칸 안. 볏 끝 ~ 꼬리 끝 ≈ span 의 1.04 배 — 가슴 위 0.49 · 아래 0.55)
     */
    static void phoenix(ServerLevel level, Vec3 chest, double span, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        double size = span / 1.7;
        JobFx.cells(level, "jphoenix", PHOENIX, f, n, chest, life, loop ? o.num("fps", 10) : 0, loop, o.with("size", size, "fade", o.num("fade", 0.12)), glow, core);
    }

    /**
     * 분노의 부채꼴 (rfx_jfury — 땅에 누움, 그림 384 × 384 · 꼭짓점 = 칸 왼쪽 가운데): 0 꼭짓점의 번쩍 → 1 부채가 반쯤 퍼짐 → 2 다 퍼짐 (가운데 흰 불길 · 불씨 줄) → 3 금이
     * 달아오름 → 4 ~ 5 불씨가 흩어지며 사그라짐 → 6 ~ 7 재 연기. 꼭짓점 apex 에서 f 쪽 (수평) 으로 reach 칸 (부채 반각 40°)
     */
    static void fury(ServerLevel level, Vec3 apex, Vec3 f, double reach, int fr, int n, double life, Opt o, double[] glow, double[] core) {
        Vec3 d = V.flat(f);
        if (V.len(d) < 1e-3) return;
        double size = reach / FURY_LEN;
        // direction_z (면 = 위): 칸의 +X 는 세계 −X, +Y 는 +Z → 칸 안에서 spin 도 돌리면 +X = (−cos, 0, sin) — 앞 d 가 되게 (JobFx.wheel 과 같음)
        double spin = Math.toDegrees(Math.atan2(d.z, -d.x));
        JobFx.cells(level, "jfury", FURY, fr, n, V.add(apex, d, size * FURY_APEX), life, 0, false, o.with("dir", V.UP, "size", size, "spin", spin, "fade", o.num("fade",
                0.2)), glow, core);
    }

    /** 부채꼴을 옆으로 벌려 둘 (±spread° — 반각 40° 둘을 겹쳐 앞 arc° 를 덮음: arc 120 이면 ±20°) */
    static void furyWide(ServerLevel level, Vec3 apex, Vec3 f, double reach, double arc, int fr, int n, double life, Opt o, double[] glow, double[] core) {
        double spread = Math.max(0, arc / 2 - 40);
        if (spread < 4) {
            fury(level, apex, f, reach, fr, n, life, o, glow, core);
            return;
        }
        for (int s = -1; s <= 1; s += 2) {
            double a = Math.toRadians(spread * s);
            Vec3 d = V.flat(f);
            Vec3 r = V.rightOf(d);
            Vec3 dd = V.unit(V.add(V.scale(d, Math.cos(a)), V.scale(r, Math.sin(a))));
            fury(level, apex, dd, reach, fr, n, life, o, glow, core);
        }
    }

    /**
     * 불씨 기둥 (rfx_jcinder — 세워 카메라 쪽으로 돎, 그림 192 × 384 · 아래 끝 = 땅): 0 발밑에 불씨가 붙음 → 1 솟아오름 → 2 ~ 5 이글거림 (loop 면 이어 돌림 — 솟는 불씨 ·
     * 아지랑이 · 꼭대기 연기) → 6 무너짐 · 7 재 연기. 높이 height 칸 (= 반 너비 × 4)
     */
    static void cinder(ServerLevel level, Vec3 ground, double height, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jcinder", CINDER, f, n, V.up(ground, height / 2), life, loop ? o.num("fps", 10) : 0, loop, o.with("size", height / 4, "fade", o.num("fade",
                0.15)), glow, core);
    }

    /**
     * 저주받은 방패의 아가리 (rfx_jdmaw — 카메라를 봄, 그림 256 × 256 · 방패 가운데 = 칸 가운데): 0 다문 방패 (눈 · 금) → 1 벌어짐 → 2 크게 벌어짐 (붉은 목구멍 · 검은 송곳니)
     * → 3 콱 다묾 (번쩍) → 4 물어 찢음 (핏빛 튐) → 5 삼킴 → 6 포효 (고리) → 7 연기로 흐려짐. 방패 너비 약 size × 1.34 칸 (2026-11-06 다시 그림 — 방패가 칸 안으로 0.8 배
     * 줄어 (가장 크게 벌려도 뿔 · 아래 턱이 잘리지 않음) 그림 반 크기 = size × 1.25)
     */
    static void dmaw(ServerLevel level, Vec3 at, double size, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jdmaw", DMAW, f, n, at, life, loop ? o.num("fps", 10) : 0, loop, o.with("size", size * 1.25, "spin", o.has("spin") ? o.num("spin", 0) : Kit
                .rand() * 8 - 4, "fade", o.num("fade", 0.12)), glow, core);
    }

    /**
     * 검은 혀 (rfx_jtendril — 가는 쪽으로 누움, 그림 512 × 96 · 뿌리 = 왼쪽 끝 (칸 가운데에서 반 너비 × 0.96) · 갈고리 끝 = 오른쪽): 0 ~ 3 꿈틀거림 (loop 면 이어 돌림 — 가시 ·
     * 가로 주름 · 붉은 핏줄이 흐름). from (뿌리) 에서 to (끝) 까지 — 길이 = 반 너비 × 1.9 · 뿌리 굵기 ≈ 길이의 0.075 (2026-11-06 굵게 — 처음 512 × 64 는 가는 줄로만 보였음)
     */
    static void tendril(ServerLevel level, Vec3 from, Vec3 to, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.3) return;
        Vec3 u = V.scale(d, 1 / len);
        double size = len / 1.9;
        JobFx.cells(level, "jtendril", TENDRIL, f, n, V.add(from, u, size * 0.96), life, loop ? o.num("fps", 12) : 0, loop, o.with("dir", u, "size", size, "fade", o.num(
                "fade", 0.12)), glow, core);
    }

    /**
     * 땅의 거대한 아가리 (rfx_jabyss — 세워 카메라 쪽으로 돎, 그림 384 × 384 · 아래 끝 = 땅 (칸 가운데에서 반 크기 × 0.95 아래)): 0 땅이 검붉게 갈라짐 → 1 턱 끝이 땅을 뚫음
     * → 2 크게 벌어진 아가리 (목구멍 빛 · 검은 혀 · 송곳니) → 3 콱 다묾 (번쩍) → 4 ~ 5 짓씹음 (loop 면 이어 돌림 — 이음매의 빛) → 6 물어 터뜨림 (빛살 · 조각) → 7 연기 속에
     * 가라앉음. 턱 너비의 반 radius 칸 (= 반 크기 × 0.92)
     */
    static void abyss(ServerLevel level, Vec3 ground, double radius, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        double size = radius / 0.92;
        JobFx.cells(level, "jabyss", ABYSS, f, n, V.up(ground, size * 0.95), life, loop ? o.num("fps", 8) : 0, loop, o.with("size", size, "fade", o.num("fade", 0.12)),
                glow, core);
    }
}
