package mmorpg.fabric.job;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-07 재설계안 3판 (직업 스킬 도감 표시 고침 — claude/job-advance-redo.md "2026-11-07 도감 표시 고침") 의 그림: 소드마스터 블레이드 에코의 걸린 칼자국
 * (rfx_jecho) · 엑스칼리버의 빛의 대검 (rfx_jexcal) · 프로스트 레이더 샤터 · 핌불윈터의 얼음 결정 감옥 (rfx_jicecage) · 정령 궁수 (궁수 1차 · 스나이퍼 궁) 의 숲의 정령
 * (늑대 rfx_jswolf · 1인칭 판 rfx_jswolf1p · 사슴 정령 왕 rfx_jstag · rfx_jstagf · 부엉이 rfx_jowl · rfx_jowlside · 정령 꽃 rfx_jsbloom). 그림은 tools/job_fx_art_adv.py →
 * tools/java_fx_add.py.
 */
final class JobFx16 {
    private JobFx16() {}

    /** rfx_jecho 장 수 (0 그어짐 · 1 ~ 4 걸려 떨림 · 5 ~ 7 갈라져 터짐) */
    static final int ECHO = 8;
    /** rfx_jexcal 장 수 (0 모여 듦 · 1 자라남 · 2 번쩍 · 3 ~ 5 빛남 · 6 휘두름 · 7 흩어짐) */
    static final int EXCAL = 8;
    /** 빛의 대검 그림의 칼끝 · 손잡이 (칸 가운데에서 반 너비의 몫 — 손잡이 왼쪽 · 칼끝 오른쪽) */
    static final double EXCAL_HILT = 0.94;
    static final double EXCAL_TIP = 0.97;
    /** rfx_jicecage 장 수 (0 솟음 · 1 번쩍 · 2 ~ 4 갇혀 있음 · 5 금 · 6 깨짐 · 7 흩어짐) */
    static final int ICECAGE = 8;

    /**
     * 걸린 칼자국 (rfx_jecho — 가는 쪽으로 누움, 그림 384 × 64 · 칸 가운데 = 칼자국 가운데): 0 번쩍이며 그어짐 → 1 ~ 4 허공에 걸려 떨림 (loop 면 이어 돌림 — 가는 흰 날 ·
     * 금빛 번짐 · 날을 따라 흐르는 반짝임) → 5 갈라져 번쩍 → 6 ~ 7 조각으로 흩어짐. 칼자국 길이 len 칸 (= 반 너비 × 1.9), dir = 칼자국이 그어진 쪽
     */
    static void echo(ServerLevel level, Vec3 at, Vec3 dir, double len, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        JobFx.cells(level, "jecho", ECHO, f, n, at, life, loop ? o.num("fps", 12) : 0, loop, o.with("dir", d, "size", len / 1.9, "fade", o.num("fade", 0.1)), glow,
                core);
    }

    /**
     * 빛의 대검 (rfx_jexcal — 가는 쪽으로 누움, 그림 512 × 64 · 손잡이 = 왼쪽 (칸 가운데에서 반 너비 × 0.94) · 칼끝 = 오른쪽 (× 0.97)): 0 둘레의 빛이 한 줄로 모여 듦 →
     * 1 칼날이 자라남 → 2 다 자라 번쩍 → 3 ~ 5 빛남 (loop 면 이어 돌림 — 흰 칼날 · 금빛 번짐 · 홈을 따라 흐르는 빛 · 오르는 빛 알갱이) → 6 휘두름 (뒤로 끌리는 빛) →
     * 7 빛 알갱이로 흩어짐. hilt (손잡이 — 손) 에서 dir 쪽으로 길이 len 칸 (칼날 굵기 ≈ len × 0.06 · 번짐까지 len × 0.12)
     */
    static void excal(ServerLevel level, Vec3 hilt, Vec3 dir, double len, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        double size = len / (EXCAL_HILT + EXCAL_TIP);
        JobFx.cells(level, "jexcal", EXCAL, f, n, V.add(hilt, d, size * EXCAL_HILT), life, loop ? o.num("fps", 12) : 0, loop, o.with("dir", d, "size", size, "fade",
                o.num("fade", 0.1)), glow, core);
    }

    /** 땅에서 솟는 빛의 검 (빛의 대검을 위로 — ground 에서 높이 h 칸 · 아래에서 위로 자라며 번쩍 → 빛남 → 흩어짐) */
    static void risingBlade(ServerLevel level, Vec3 ground, double h, double life, Opt o, double[] glow, double[] core) {
        Vec3 tilt = V.unit(new Vec3((Kit.rand() - 0.5) * 0.18, 1, (Kit.rand() - 0.5) * 0.18));
        excal(level, V.up(ground, -0.3), tilt, h, 1, 7, life, false, o, glow, core);
    }

    /**
     * 얼음 결정 감옥 (rfx_jicecage — 세워 카메라 쪽으로 돎, 그림 256 × 384 · 아래 끝 = 땅): ground 에 아래 끝 · 높이 height 칸 (너비 = 높이 × 2/3). 0 서리가 기어올라
     * 육각 얼음 기둥 다섯이 솟음 → 1 다 솟아 번쩍 → 2 ~ 4 갇혀 있음 (loop 면 이어 돌림 — 비치는 얼음 · 위로 지나가는 빛 띠) → 5 금 → 6 깨짐 → 7 조각이 흩어짐.
     * 칸 f 부터 n 장을 life 초 (loop 면 fps 8 로 되풀이)
     */
    static void iceCage(ServerLevel level, Vec3 ground, double height, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jicecage", ICECAGE, f, n, V.up(ground, height / 2), life, loop ? o.num("fps", 8) : 0, loop, o.with("size", height / 3, "fade", o.num(
                "fade", 0.1)), glow, core);
    }

    // ------------------------------------------------------------ 2026-11-07 정령 궁수 — 숲의 정령

    /** rfx_jswolf 장 수 (0 ~ 5 머리 오른쪽 달림 · 6 ~ 11 거울 — 머리 왼쪽) · 한 벌 */
    static final int WOLF = 12;
    static final int WOLF_RUN = 6;
    /** rfx_jstag 장 수 (0 ~ 5 달림 · 6 ~ 11 거울 — 반대쪽 판) · 한 벌 · rfx_jstagf 장 수 (0 ~ 2 깨어남 · 3 ~ 5 서 있음 · 6 번쩍 · 7 흩어짐 · 8 ~ 11 뒷모습 달림) */
    static final int STAG = 12;
    static final int STAG_RUN = 6;
    static final int STAGF = 12;
    /** rfx_jowl 장 수 (0 모임 · 1 번쩍 · 2 ~ 5 날갯짓 · 6 내리꽂음 (뒷모습) · 7 흩어짐) · rfx_jowlside 장 수 (0 ~ 3 활공 · 4 ~ 7 거울 — 반대쪽 판) · 한 벌 */
    static final int OWL = 8;
    static final int OWLSIDE = 8;
    static final int OWL_GLIDE = 4;

    /**
     * 세운 판 (direction_x — 가로 = dir) 은 앞면만 그려져 한쪽에서만 보임 (영상 42332661771 · 42333733932: 옆에서 본 스탬피드 무리 · 부엉이가 안 보였음) → 같은 자리에
     * 반대쪽 판 (dir 을 뒤집고 거울 그림 — 칸 mirror 부터) 을 하나 더 그림. 양면으로 그리는 화면에서는 두 장이 같은 그림으로 겹침
     */
    private static void bothSides(ServerLevel level, String name, int total, int run, Vec3 at, Vec3 d, double life, double fps, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, name, total, 0, run, at, life, fps, true, o.with("dir", d), glow, core);
        JobFx.cells(level, name, total, run, run, at, life, fps, true, o.with("dir", V.scale(d, -1)), glow, core);
    }
    /** rfx_jsbloom 장 수 (0 싹 · 1 봉오리 · 2 핌 · 3 ~ 4 빛남 · 5 번쩍 · 6 터짐 · 7 티끌) */
    static final int BLOOM = 8;

    /**
     * 달리는 늑대 정령 옆모습 (rfx_jswolf — 세운 판이 dir 을 따라 섬, 그림 256 × 128 · 발 끝 아래 16 점): ground (땅) 에서 몸 길이 len 칸 (판 너비) · dir 쪽으로
     * speed 칸/초 · life 초 (이어 돌림 달리기)
     */
    static void wolfRun(ServerLevel level, Vec3 ground, Vec3 dir, double len, double speed, double life, Opt o, double[] glow, double[] core) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        double size = len / 2;
        Vec3 at = V.up(ground, size * 0.5 - size * (16.0 / 128.0));
        // 움직임은 speed × dir (판 방향과 따로 — 반대쪽 판도 같은 쪽으로 감)
        bothSides(level, "jswolf", WOLF, WOLF_RUN, at, d, life, o.num("fps", 18), o.with("size", size, "speed", 0.0, "vel", V.add(o.vec("vel", Vec3.ZERO), d, speed),
                "fade", o.num("fade", 0.12)), glow, core);
    }

    /**
     * 같은 늑대를 1인칭 판으로 (rfx_jswolf1p — 카메라를 봄): 화면에서 왼쪽으로 가면 mirror (머리 왼쪽). at = 몸 가운데 · dir 쪽으로 speed · spin = 화면의 기울기 (도)
     */
    static void wolf1p(ServerLevel level, Vec3 at, Vec3 dir, double len, double speed, double life, boolean mirror, Opt o, double[] glow, double[] core) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        JobFx.cells(level, "jswolf1p", WOLF, mirror ? WOLF_RUN : 0, WOLF_RUN, at, life, o.num("fps", 18), true, o.with("dir", d, "size", len / 2, "speed", speed, "fade",
                o.num("fade", 0.12)), glow, core);
    }

    /**
     * 달리는 사슴 정령 왕 옆모습 (rfx_jstag — 세운 판이 dir 을 따라 섬, 그림 384 × 256 · 발굽 아래 20 점): ground 에서 키 (뿔 끝까지) height 칸 · dir 쪽으로 speed 칸/초
     */
    static void stagRun(ServerLevel level, Vec3 ground, Vec3 dir, double height, double speed, double life, Opt o, double[] glow, double[] core) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        double size = height * 0.75;
        Vec3 at = V.up(ground, height / 2 - height * (20.0 / 256.0));
        bothSides(level, "jstag", STAG, STAG_RUN, at, d, life, o.num("fps", 16), o.with("size", size, "speed", 0.0, "vel", V.add(o.vec("vel", Vec3.ZERO), d, speed),
                "fade", o.num("fade", 0.12)), glow, core);
    }

    /**
     * 사슴 정령 왕 앞모습 · 뒷모습 (rfx_jstagf — 세워 카메라 쪽으로 돎, 그림 256 × 256 · 발굽 아래 10 점): ground 에서 키 height 칸. 칸 f 부터 n 장 (loop 면 fps 로 되풀이)
     */
    static void stagFront(ServerLevel level, Vec3 ground, double height, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        Vec3 at = V.up(ground, height / 2 - height * (10.0 / 256.0));
        JobFx.cells(level, "jstagf", STAGF, f, n, at, life, loop ? o.num("fps", 10) : 0, loop, o.with("size", height / 2, "fade", o.num("fade", 0.12)), glow, core);
    }

    /** 부엉이 정령 앞모습 (rfx_jowl — 카메라를 봄, 그림 384 × 256): at = 몸 가운데 · 날개 끝 사이 span 칸. 칸 f 부터 n 장 */
    static void owl(ServerLevel level, Vec3 at, double span, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        JobFx.cells(level, "jowl", OWL, f, n, at, life, loop ? o.num("fps", 10) : 0, loop, o.with("size", span / 2 * (384.0 / 340.0), "fade", o.num("fade", 0.12)),
                glow, core);
    }

    /** 활공하는 부엉이 정령 옆모습 (rfx_jowlside — 세운 판이 dir 을 따라 섬, 그림 256 × 128): at = 몸 가운데 · 길이 len 칸 · dir 쪽으로 speed */
    static void owlGlide(ServerLevel level, Vec3 at, Vec3 dir, double len, double speed, double life, Opt o, double[] glow, double[] core) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        bothSides(level, "jowlside", OWLSIDE, OWL_GLIDE, at, d, life, o.num("fps", 12), o.with("size", len / 2, "speed", 0.0, "vel", V.add(o.vec("vel", Vec3.ZERO), d,
                speed), "fade", o.num("fade", 0.1)), glow, core);
    }

    /** 정령 꽃 (rfx_jsbloom — 세워 카메라 쪽으로 돎, 그림 128 × 128 · 줄기 밑 4 점): ground 에서 키 height 칸. 칸 f 부터 n 장 */
    static void bloom(ServerLevel level, Vec3 ground, double height, int f, int n, double life, boolean loop, Opt o, double[] glow, double[] core) {
        Vec3 at = V.up(ground, height / 2 - height * (4.0 / 128.0));
        JobFx.cells(level, "jsbloom", BLOOM, f, n, at, life, loop ? o.num("fps", 8) : 0, loop, o.with("size", height / 2, "fade", o.num("fade", 0.1)), glow, core);
    }
}
