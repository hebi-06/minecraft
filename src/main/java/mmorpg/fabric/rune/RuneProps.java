package mmorpg.fabric.rune;

import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-12 룬의 3D 물체 (사용자 룬 도감 메모 — "검 3d" · "망치 3d" · "얼음 가시 3d" · "바위 가시 3d" · "구름 3d" · "파도 · 물 연출 3d" · "운석" ·
 * "손 모양 3d" · "거대한 주먹 고퀄" · "그물 3d"): 아이템 모델 (rpg:vfx/&lt;이름&gt;) 을 서버가 아이템 표시로 띄움 (vfx.Vfx —
 * 모드 · 모드 없는 클라이언트 모두 같은 모습). 계열 색이 드는 재질 (빛 · 속빛) 은 아이템의 custom_model_data 색 (Prop.tint).
 * 모델 치수 (픽셀) 는 tools/vfx_blocky.py (2026-11-12 사진 방식 — 큰 상자 + 면마다 그린 픽셀 그림, 사용자 "그냥 3d모델링만 내가 보낸 저 사진처럼") ·
 * tools/vfx_props.py (그물) 와 같음 — 길이 · 쥔 곳.
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
    /** 조각 물체 (tools/vfx_shapes.py · mmorpg/vfx_shapes.json — 2026-11-12 사용자 "만년빙하처럼 파도 같은거 만들어도 됨"): 이름 */
    public static final String PALM = "palm";
    public static final String FIST = "rpg:vfx/fist";
    public static final String NET = "rpg:vfx/net";
    public static final String WAVE = "wave";
    public static final String WATER_COLUMN = "water_column";

    /**
     * 2026-11-13 룬 빛 화살 (저격 · 천궁 — tools/vfx_blocky.py rune_arrow): 촉 끝 y 31 · 오늬 끝 y −14 (길이 45) · 가운데 (8, 8.5, 8) · 오늬 (8, −13, 8).
     * 촉의 두 판 · 깃 넷 (+ 자)
     */
    public static final String ARROW = "rpg:vfx/rune_arrow";
    public static final double ARROW_LEN = 45;
    public static final double[] ARROW_MID = {8, 8.5, 8};
    public static final double[] ARROW_NOCK = {8, -13, 8};
    /**
     * 2026-11-13 천궁의 하늘의 활 (sky_bow): 쏘는 쪽 +Y · 활 면 YZ (보는 쪽 ±X) · 가운데 (8, 8, 8) · 너비 (z) 약 43 (끝 z −13.6 ~ 29.6 · y 5.2) — 시위는 서버가
     * 끝 (BOW_TIP — 가운데에서 ±21.6, −2.8 픽셀) 에서 오늬까지 2D 빛줄로
     */
    public static final String SKY_BOW = "rpg:vfx/sky_bow";
    public static final double SKY_BOW_W = 43.2;
    public static final double[] BOW_TIP = {21.6, -2.8};
    /** 2026-11-13 피의 낙인의 핏빛 낫 (rune_scythe): 자루 x 8 · z 12 (y −16 ~ 25), 날은 자루 끝 (y ~27) 에서 −Z 로 약 24 픽셀 · 자루 가운데 (8, 5, 12) */
    public static final String SCYTHE = "rpg:vfx/rune_scythe";
    public static final double SCYTHE_LEN = 43;
    public static final double[] SCYTHE_MID = {8, 5, 12};

    /** 2026-11-13 떠오르는 바위 (boulder — 중력 · 초인 각성): 가운데 (8, 8, 8) · 지름 약 16 · 틈의 빛 = 계열 색 (tint 0) */
    public static final String BOULDER = "rpg:vfx/boulder";
    public static final double BOULDER_D = 16;
    /** 2026-11-13 블랙홀의 공허 핵 (void_orb): 가운데 (8, 8, 8) · 지름 약 19 · 면 테두리 빛 = 계열 색 (tint 0) */
    public static final String VOID_ORB = "rpg:vfx/void_orb";

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
    /** 운석: 가운데 (8, 8, 8) · 지름 약 19 픽셀 (핏빛 달은 2026-11-12 사용자 "적월 그냥 다시 2d로" — 3D 달 없음) */
    public static final double METEOR_D = 19;
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

    /**
     * 2026-11-12 직업 스킬: 하늘의 단두대 (엑시큐셔너 길로틴 오브 헤븐 — tools/vfx_blocky.py guillotine · guillotine_blade): 틀과 칼날이 같은 자리 · 크기 ·
     * 쥔 곳 (밑 가운데 (8, −16, 8)) — 높이 48, 칼날을 GUILLOTINE_DROP 만큼 내리면 날 끝이 목판의 목 구멍에 닿음. 앞 (+Z) · 뒤 모두 룬 판
     */
    public static final String GUILLOTINE = "rpg:vfx/guillotine";
    public static final String GUILLOTINE_BLADE = "rpg:vfx/guillotine_blade";
    public static final double[] GUILLOTINE_BASE = {8, -16, 8};
    public static final double GUILLOTINE_H = 48;
    public static final double GUILLOTINE_DROP = 14;
    /** 룬 전용 망치의 머리 가운데 → 머리 끝 (때리는 쪽 반대 — 가시 뿌리, 모델 +Y) 픽셀: 머리를 아래로 세워 박을 때 땅에 닿는 면 */
    public static final double HAMMER_HEAD_END = 4.5;

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
        // 2026-11-13 처음 모습 (쥔 곳 · 빛 · 물들임 · 회전 · 크기) 을 정한 뒤 띄움 (Vfx.Fx.prop init)
        return fx.prop(model, at, p -> {
            p.grip(grip[0], grip[1], grip[2]).bright();
            if (tints.length > 0) p.tint(tints);
            p.to(at, rot, scale, 0);
        });
    }

    /**
     * 3D 얼음 가시 (사용자 "얼음 가시 3d" · "얼음 3d"): at (땅) 에서 높이 h 칸으로 솟고 (3틱 · 조금 기울어짐) hold 틱 머문 뒤 땅으로 가라앉으며 작아짐 (5틱).
     * yaw = 수평 돌림 (도)
     */
    public static void iceSpike(ServerLevel level, Vec3 at, double h, int hold, double yaw) {
        Vfx.Fx fx = fx(level, at, hold + 10);
        double s = scaleFor(h, ICE_SPIKE_H);
        Vec3 side = new Vec3(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        Vec3 lean = V.unit(V.add(V.UP, V.unit(new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5)), 0.18));
        Vfx.Prop p = prop(fx, ICE_SPIKE, V.up(at, -h * 0.7), new double[] {8, 0, 8}, along(lean, side), s * 0.6);
        fx.on(1, () -> p.to(V.up(at, -0.15), null, s, 3));
        fx.on(hold + 4, () -> p.to(V.up(at, -h * 0.8), null, s * 0.5, 5));
        fx.on(hold + 9, p::remove);
    }

    /**
     * 3D 바위 가시 (사용자 "바위 가시 / 가시 연출 3d로" — 골렘 · 노움 · 정령왕 · 힘 · 중력 …): at (땅) 에서 높이 h 칸으로 솟고 (3틱 — 흙먼지는 부르는 쪽) hold 틱
     * 머문 뒤 가라앉음 (6틱). 금 사이 빛 = glow (계열 색)
     */
    public static void rockSpike(ServerLevel level, Vec3 at, double h, int hold, double yaw, double[] glow) {
        Vfx.Fx fx = fx(level, at, hold + 12);
        double s = scaleFor(h, ROCK_SPIKE_H);
        Vec3 side = new Vec3(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        Vec3 lean = V.unit(V.add(V.UP, V.unit(new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5)), 0.22));
        Vfx.Prop p = prop(fx, ROCK_SPIKE, V.up(at, -h * 0.85), new double[] {8, 0, 8}, along(lean, side), s * 0.7, glow);
        fx.on(1, () -> p.to(V.up(at, -0.2), null, s, 3));
        fx.on(hold + 4, () -> p.to(V.up(at, -h * 0.9), null, s * 0.7, 6));
        fx.on(hold + 10, p::remove);
    }

    /**
     * 3D 구름 무리 (사용자 "구름 3d" — 눈보라 · 번개 구름 · 치유의 비 · 뇌창의 먹구름): 덩이마다 구름 모델 하나 (수평으로 아무렇게나 돌림 — 같은 모양이 되풀이되지 않게),
     * 색 = tint (구름 빛 — 회색 그림에 곱함) · core (밑의 빛 — 번개 · 얼음 · 물빛). 덩이 크기 s = 2D 구름 그림의 반 너비 (칸).
     * 나타남 (작게 → 크게 6틱) → place 로 옮김 · 커짐 → end 로 작아지며 사라짐
     */
    public static final class CloudBank {
        final Vfx.Fx fx;
        final Vfx.Prop[] puffs;
        final double[] scale;

        CloudBank(Vfx.Fx fx, int n) {
            this.fx = fx;
            this.puffs = new Vfx.Prop[n];
            this.scale = new double[n];
        }

        /** 덩이 i 를 at 으로 · 반 너비 s 칸 (ticks 틱 보간) */
        public void place(int i, Vec3 at, double s, int ticks) {
            if (i < 0 || i >= puffs.length || puffs[i] == null) return;
            scale[i] = puffScale(s);
            puffs[i].to(at, null, scale[i], ticks);
        }

        /** 끝: ticks 틱 동안 작아지며 사라짐 */
        public void end(int ticks) {
            fx.keep(ticks + 2);
            for (int i = 0; i < puffs.length; i++) {
                Vfx.Prop p = puffs[i];
                if (p == null) continue;
                double s = scale[i];
                fx.on(fx.age(), () -> p.size(s * 0.15, ticks));
                fx.on(fx.age() + ticks, p::remove);
            }
        }
    }

    /** 2D 구름 그림의 반 너비 (칸) → 구름 모델 크기 (모델 너비 28 픽셀 · 2D 그림은 가장자리가 옅으므로 0.85) */
    static double puffScale(double halfWidth) {
        return halfWidth * 2 * 0.85 / (CLOUD_W / 16.0);
    }

    /** 구름 무리: centers[i] = 덩이 자리, halves[i] = 반 너비 (칸), life = 틱 (end 를 부르지 않으면 이만큼 뒤 사라짐) */
    public static CloudBank clouds(ServerLevel level, Vec3[] centers, double[] halves, double[] tint, double[] core, int life) {
        Vfx.Fx fx = fx(level, centers[0], life + 12);
        CloudBank b = new CloudBank(fx, centers.length);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        double[] mid = {8, 8.6, 8};
        for (int i = 0; i < centers.length; i++) {
            double s = puffScale(halves[i]);
            b.scale[i] = s;
            double yaw = r.nextDouble() * Math.PI * 2;
            Vfx.Prop p = prop(fx, CLOUD, centers[i], mid, along(V.UP, new Vec3(Math.cos(yaw), 0, Math.sin(yaw))), s * 0.3, tint, core);
            b.puffs[i] = p;
            fx.on(1, () -> p.size(s, 6));
        }
        fx.on(life, () -> b.end(8));
        return b;
    }

    /** 조각 물체 하나 (손바닥 · 파도 · 물기둥 — 쥔 곳은 모양마다 vfx_shapes.json): at · 회전 · 크기 · 늘 밝게 · 물들임 */
    public static Vfx.Group shape(Vfx.Fx fx, String shape, Vec3 at, Quaternionf rot, double scale, double[]... tints) {
        Vfx.Group g = fx.group(shape, at).bright();
        if (tints.length > 0) g.tint(tints);
        g.to(at, rot, scale, 0);
        return g;
    }

    /** 2026-11-13 아무 축 둘레로 조금씩 도는 회전 (구르는 바위) — 씨앗 seed · 나이 t · 도/틱 rate */
    public static Quaternionf tumble(long seed, double t, double rate) {
        java.util.Random rr = new java.util.Random(seed);
        org.joml.Vector3f ax = new org.joml.Vector3f((float) (rr.nextDouble() - 0.5), (float) (rr.nextDouble() - 0.5), (float) (rr.nextDouble() - 0.5)).normalize();
        float a0 = (float) (rr.nextDouble() * Math.PI * 2);
        return new Quaternionf().rotateAxis(a0 + (float) Math.toRadians(t * rate), ax.x, ax.y, ax.z);
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
