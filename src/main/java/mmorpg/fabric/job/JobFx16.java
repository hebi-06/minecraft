package mmorpg.fabric.job;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-07 재설계안 3판 (직업 스킬 도감 표시 고침 — claude/job-advance-redo.md "2026-11-07 도감 표시 고침") 의 그림: 소드마스터 블레이드 에코의 걸린 칼자국
 * (rfx_jecho) · 엑스칼리버의 빛의 대검 (rfx_jexcal) · 솟는 칼 (2026-11-14 — 얼음 결정 감옥 rfx_jicecage (3D 얼음) · 정령 궁수의 숲의 정령 (늑대 · 사슴 ·
 * 부엉이 · 정령 꽃 — 궁수 계열을 다시 만들며) 은 뺌). 그림은 tools/job_fx_art_adv.py → tools/java_fx_add.py.
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
}
