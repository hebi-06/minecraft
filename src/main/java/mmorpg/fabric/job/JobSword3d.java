package mmorpg.fabric.job;

import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-13 직업 스킬의 3D 검 (직업 도감 메모 — 마검사 아스트랄 클레이모어 · 아케인 블레이드 월드 클리버 · 엘리멘탈 블레이드 엘리멘탈 칼라미티 "검 연출을 3d로"):
 * 룬 전용 검 모델 (tools/vfx_blocky.py rune_sword — 반투명 빛 날 = 물들임 0 · 솟은 룬 홈 · 보석 = 물들임 1) 을 Vfx 소품으로.
 * <p>
 * 모델 축: +Y = 칼 (손잡이 → 칼끝), ±Z = 양날, X = 넓은 면에 수직
 */
final class JobSword3d {
    private JobSword3d() {}

    static final String SWORD = RuneProps.SWORD;
    /** 손잡이 · 칼끝 (픽셀) · 손잡이 → 칼끝 (칸, 크기 1) */
    static final double[] HILT = RuneProps.SWORD_HILT;
    static final double[] TIP = RuneProps.SWORD_TIP;
    static final double REACH = (TIP[1] - HILT[1]) / 16.0;

    /** 모델 +Y 가 blade (손잡이 → 칼끝), +Z 가 edge (날) 쪽인 회전 (edge 는 blade 에 수직으로 맞춤) */
    static Quaternionf pose(Vec3 blade, Vec3 edge) {
        Vec3 y = V.unit(blade);
        Vec3 e = V.sub(edge, V.scale(y, V.dot(edge, y)));
        if (V.len(e) < 1e-4) e = Math.abs(y.y) < 0.9 ? V.cross(y, V.UP) : V.cross(y, new Vec3(1, 0, 0));
        Vec3 z = V.unit(e);
        Vec3 x = V.unit(V.cross(y, z));
        return Vfx.basis(x, y, z);
    }

    /** 칼끝이 dir 쪽 · 넓은 면이 수평에 가깝게 (날 = dir 과 위의 수직 — 하늘에서 내리꽂는 칼) */
    static Quaternionf aim(Vec3 dir) {
        Vec3 d = V.unit(dir);
        Vec3 side = V.cross(d, V.UP);
        if (V.len(side) < 1e-3) side = new Vec3(1, 0, 0);
        return pose(d, side);
    }

    /** 검 소품 하나 (grip = 모델의 이 점이 at — HILT · TIP): 빛 날 = glow · 룬 홈 · 보석 = core */
    static Vfx.Prop sword(Vfx.Fx fx, Vec3 at, double[] grip, Quaternionf rot, double scale, double[] glow, double[] core) {
        return RuneProps.prop(fx, SWORD, at, grip, rot, scale, glow, core);
    }
}
