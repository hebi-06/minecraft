package mmorpg.fabric.rune;

import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-12 룬의 3D 물체 (사용자 룬 도감 메모 — "검 3d" · "망치 3d" · "얼음 가시 3d" · "바위 가시 3d" · "구름 3d" · "파도 · 물 연출 3d" · "운석" ·
 * "달 3d" · "손 모양 3d" · "거대한 주먹 고퀄" · "그물 3d"): tools/vfx_props.py 의 아이템 모델 (rpg:vfx/&lt;이름&gt;) 을 서버가 아이템 표시로 띄움 (vfx.Vfx —
 * 모드 · 모드 없는 클라이언트 모두 같은 모습). 계열 색이 드는 재질 (빛 · 속빛) 은 아이템의 custom_model_data 색 (Prop.tint).
 * 모델 치수 (픽셀) 는 tools/vfx_props.py 와 같음 — 길이 · 쥔 곳.
 */
public final class RuneProps {
    private RuneProps() {}

    public static final String SWORD = "rpg:vfx/rune_sword";
    public static final String HAMMER = "rpg:vfx/rune_hammer";
    public static final String ICE_SPIKE = "rpg:vfx/ice_spike";
    public static final String ICE_BLADE = "rpg:vfx/ice_blade";
    public static final String ROCK_SPIKE = "rpg:vfx/rock_spike";
    public static final String CLOUD = "rpg:vfx/cloud";
    public static final String METEOR = "rpg:vfx/meteor";
    public static final String MOON = "rpg:vfx/moon";
    public static final String PALM = "rpg:vfx/palm";
    public static final String FIST = "rpg:vfx/fist";
    public static final String NET = "rpg:vfx/net";
    public static final String WAVE = "rpg:vfx/wave";
    public static final String WATER_COLUMN = "rpg:vfx/water_column";

    /** 룬 전용 검: 칼끝 y 31 · 자루 끝 y −10.2 (길이 41.2 픽셀) · 날 가운데 (8, 17.5, 8) · 손잡이 가운데 (8, −3.5, 8) */
    public static final double SWORD_LEN = 41.2;
    public static final double[] SWORD_MID = {8, 10.4, 8};
    public static final double[] SWORD_TIP = {8, 31, 8};
    public static final double[] SWORD_HILT = {8, -3.5, 8};
    /** 룬 전용 망치: 자루 끝 y −11 · 머리 꼭대기 y 30.5 (길이 41.5) · 머리 가운데 (8, 21.5, 8) · 손잡이 (8, −4, 8) */
    public static final double HAMMER_LEN = 41.5;
    public static final double[] HAMMER_HEAD = {8, 21.5, 8};
    public static final double[] HAMMER_GRIP = {8, -4, 8};
    /** 얼음 가시 · 바위 가시: 밑 가운데 (8, 0, 8) · 높이 27 · 26.5 */
    public static final double ICE_SPIKE_H = 27;
    public static final double ROCK_SPIKE_H = 26.5;
    /** 얼음 칼: 칼끝 y 29.5 · 자루 끝 y −8.5 (길이 38) · 손잡이 가운데 (8, −3, 8) */
    public static final double ICE_BLADE_LEN = 38;
    public static final double[] ICE_BLADE_HILT = {8, -3, 8};
    /** 구름: 가운데 (8, 8.6, 8) · 너비 28 픽셀 · 높이 14 */
    public static final double CLOUD_W = 28;
    /** 운석 · 달: 가운데 (8, 8, 8) · 지름 약 19 · 24 픽셀 */
    public static final double METEOR_D = 19;
    public static final double MOON_D = 24;
    /** 손바닥: 손바닥 가운데 (8, 5, 8) · 높이 (손목 ~ 손끝) 약 26 · 앞 (장풍을 미는 면) = +Z */
    public static final double[] PALM_MID = {8, 5, 8};
    /** 주먹: 치는 면 (아래) 가운데 (8, 0.5, 0.6) · 팔 위 끝 y 24.5 · 손등 = +Z */
    public static final double[] FIST_FACE = {8, 0.5, 0.6};
    public static final double FIST_H = 24;
    /** 그물: 가운데 바닥 (8, 0, 8) · 지름 30 · 높이 12 */
    public static final double NET_D = 30;
    /** 파도: 밑 가운데 (8, 0, 1.5) · 너비 (x) 30 · 높이 18 · 앞 (말린 물마루) = +Z */
    public static final double[] WAVE_BASE = {8, 0, 1.5};
    public static final double WAVE_W = 30;
    /** 물기둥: 밑 가운데 (8, 0, 7.5) · 높이 28 */
    public static final double WATER_H = 28;

    /** 모델 +Y 가 up, +X 가 side 쪽 (side 를 up 에 수직으로 맞춤) 인 회전 */
    public static Quaternionf along(Vec3 up, Vec3 side) {
        Vec3 y = V.unit(up);
        Vec3 sx = V.sub(side, V.scale(y, V.dot(side, y)));
        if (V.len(sx) < 1e-4) sx = Math.abs(y.y) < 0.9 ? new Vec3(0, 1, 0).cross(y) : new Vec3(1, 0, 0).cross(y);
        Vec3 x = V.unit(sx);
        Vec3 z = V.unit(V.cross(x, y));
        return Vfx.basis(x, y, z);
    }

    /** 모델 +Z (앞) 가 fwd, +Y 가 up 쪽인 회전 */
    public static Quaternionf facing(Vec3 fwd, Vec3 up) {
        return Vfx.look(fwd, up);
    }

    /** 물체 하나: model 을 at 에 (grip = 이 모델 점이 at), 회전 · 크기 · 빛 · 물들임 */
    public static Vfx.Prop prop(Vfx.Fx fx, String model, Vec3 at, double[] grip, Quaternionf rot, double scale, double[]... tints) {
        Vfx.Prop p = fx.prop(model, at).grip(grip[0], grip[1], grip[2]).bright();
        if (tints.length > 0) p.tint(tints);
        p.to(at, rot, scale, 0);
        return p;
    }

    /** 길이 (칸) → 크기 (모델 길이 px) */
    public static double scaleFor(double blocks, double modelPx) {
        return blocks / (modelPx / 16.0);
    }

    /** 효과 하나 (세계의 한 점 · life 틱) */
    public static Vfx.Fx fx(ServerLevel level, Vec3 at, int life) {
        return Vfx.at(level, at, life);
    }
}
