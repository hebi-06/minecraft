package mmorpg.fabric.job;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 수호기사 2차 그림 (tools/job_fx_art_adv.py — rpg:rfx_j*, 빛 칸 = 넓은 빛 · 심지 칸 = 테 · 결): 센티널 — 무지개 장막 (세워 카메라 쪽으로 돎 — 아래 끝 = 땅) ·
 * 빛의 피난처 (세워 카메라 쪽으로 돎 — 돔 · 아래 끝 = 땅) · 물결 (카메라를 봄 — 말리는 물마루) · 황금 뿔피리 (가는 쪽으로 누움 — 나팔 = 앞) · 프리즘 방패 (세워 카메라 쪽으로 돎),
 * 어벤저 — 분노의 부채꼴 (땅에 누움 — 꼭짓점에서 앞으로) · 늑대의 턱 (카메라를 봄) · 불씨 기둥 (세워 카메라 쪽으로 돎 — 아래 끝 = 땅). 크기 뜻은 그림 정의
 * (tools/job_fx_art_adv.py 의 설명) 와 같음.
 */
final class JobFx15 {
    private JobFx15() {}

    static final int RAMPART = 8;
    static final int HAVEN = 8;
    static final int TIDE = 8;
    static final int HORN = 8;
    static final int PRISM = 8;
    static final int FURY = 8;
    static final int JAW = 8;
    static final int CINDER = 8;
    /** 분노의 부채꼴: 꼭짓점이 칸 가운데에서 왼쪽으로 반 크기 × 0.917 · 부채 길이 = 반 크기 × 1.54 */
    static final double FURY_APEX = 0.917;
    static final double FURY_LEN = 1.54;

    /** 빛 고리 (rfx_jhalo — 땅에 누움, 반지름 radius 칸 = 반 크기 × 0.88) — 색을 직접 줌 (JobFx.halo 는 직업 색) */
    static void halo(ServerLevel level, Vec3 center, double radius, double life, Opt o, double[] glow, double[] core) {
        JobFx.two(level, "jhalo", JobFx.HALO, center, life, o.with("dir", V.UP, "size", radius / 0.88, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), glow,
                core);
    }

    /**
     * 무지개 장막 (rfx_jrampart — 세워 카메라 쪽으로 돎, 그림 128 × 384 · 아래 끝 = 땅): 0 땅에서 빛줄기가 솟음 · 1 장막이 아래부터 펼쳐짐 → 2 ~ 5 오로라처럼 주름이
     * 일렁이며 빛이 위로 흐름 (loop 면 이어 돌림) → 6 프리즘 조각으로 부서져 떨어짐 · 7 반짝이며 흐려짐. 높이 height 칸 (= 반 너비 × 6 — 너비 = 높이 ÷ 3)
     */
    static void rampart(ServerLevel level, Vec3 ground, double height, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jrampart", RAMPART, f, n, V.up(ground, height / 2), life, loop ? o.num("fps", 8) : 0, loop, o.with("size", height / 6, "fade", o.num("fade",
                0.15)), glow, core);
    }

    /**
     * 빛의 피난처 (rfx_jhaven — 세워 카메라 쪽으로 돎, 그림 384 × 240 · 돔 밑 = 땅 — 칸 가운데에서 반 너비 × 0.51 아래): 0 땅의 빛 고리에서 테가 솟음 · 1 반구 돔이 생김 → 2 ~ 5 빛 띠가 돔을 타고 오르며
     * 반짝임 (loop 면 이어 돌림) → 6 돔에 금이 감 · 7 조각이 흩어지며 흐려짐. 돔 반지름 radius 칸 (= 반 너비 × 0.88)
     */
    static void haven(ServerLevel level, Vec3 ground, double radius, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        double size = radius / 0.88;
        JobFx.cells(level, "jhaven", HAVEN, f, n, V.up(ground, size * 0.51), life, loop ? o.num("fps", 8) : 0, loop, o.with("size", size, "fade", o.num("fade", 0.15)),
                glow, core);
    }

    /**
     * 물결 (rfx_jtide — 카메라를 봄, 그림 192 × 192): 0 ~ 5 물마루가 말리며 굴러감 (loop 면 이어 돌림 — 거품 · 물보라) → 6 부서지며 튐 · 7 물보라가 흐려짐. 물마루 너비 약
     * size × 1.7 칸. o 의 dir · speed 로 흘러감
     */
    static void tide(ServerLevel level, Vec3 at, double size, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jtide", TIDE, f, n, at, life, loop ? o.num("fps", 12) : 0, loop, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 16 - 8,
                "fade", o.num("fade", 0.15)), glow, core);
    }

    /**
     * 황금 뿔피리 (rfx_jhorn — 가는 쪽으로 누움, 그림 256 × 128 · 나팔 = 오른쪽): 0 빛 테로 나타남 → 1 ~ 4 울림 (loop 면 이어 돌림 — 나팔이 빛나고 소리 물결이 번짐) →
     * 5 크게 울림 (나팔 앞 번쩍) → 6 ~ 7 빛 알갱이로 흩어짐. 뿔 길이 약 size × 1.8 칸 · dir = 나팔이 향하는 쪽
     */
    static void horn(ServerLevel level, Vec3 at, Vec3 dir, double size, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jhorn", HORN, f, n, at, life, loop ? o.num("fps", 8) : 0, loop, o.with("dir", dir, "size", size, "fade", o.num("fade", 0.15)), glow, core);
    }

    /**
     * 프리즘 방패 (rfx_jprism — 세워 카메라 쪽으로 돎, 그림 192 × 256): 0 빛 테가 그어지며 생김 → 1 ~ 4 버팀 (loop 면 이어 돌림 — 무지개 띠가 면을 쓸고 지나감) →
     * 5 맞음 (가운데 번쩍 · 금 별) → 6 부서짐 · 7 흐려짐. 방패 높이 약 size × 2.4 칸 (size = 반 너비)
     */
    static void prism(ServerLevel level, Vec3 at, double size, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jprism", PRISM, f, n, at, life, loop ? o.num("fps", 8) : 0, loop, o.with("size", size, "fade", o.num("fade", 0.15)), glow, core);
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

    /**
     * 늑대의 턱 (rfx_jjaw — 카메라를 봄, 그림 320 × 320 · 정면에서 본 벌린 아가리): 0 위아래 턱이 크게 벌어진 채 나타남 → 1 ~ 2 다물며 조여 듦 (송곳니가 엇갈림) →
     * 3 다물지 못하고 버팀 (턱이 떨림 · 틈의 불빛) → 4 위아래로 찢어짐 (금 · 번쩍) → 5 조각이 튐 → 6 ~ 7 불씨로 흩어짐. 턱 너비 약 size × 1.8 칸
     */
    static void jaw(ServerLevel level, Vec3 at, double size, int f, int n, double life, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jjaw", JAW, f, n, at, life, 0, false, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 10 - 5, "fade", o.num("fade",
                0.15)), glow, core);
    }

    /**
     * 불씨 기둥 (rfx_jcinder — 세워 카메라 쪽으로 돎, 그림 192 × 384 · 아래 끝 = 땅): 0 발밑에 불씨가 붙음 → 1 솟아오름 → 2 ~ 5 이글거림 (loop 면 이어 돌림 — 솟는 불씨 ·
     * 아지랑이 · 꼭대기 연기) → 6 무너짐 · 7 재 연기. 높이 height 칸 (= 반 너비 × 4)
     */
    static void cinder(ServerLevel level, Vec3 ground, double height, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jcinder", CINDER, f, n, V.up(ground, height / 2), life, loop ? o.num("fps", 10) : 0, loop, o.with("size", height / 4, "fade", o.num("fade",
                0.15)), glow, core);
    }
}
