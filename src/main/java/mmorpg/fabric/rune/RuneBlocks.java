package mmorpg.fabric.rune;

import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.48.2 초월 룬 손보기 (사용자 "초월 31종 먼저" → "골렘 소환 같은 느낌이면 좋을거 같은데 너무 3d에 매끈매끈 한거보다" → 블록 · 2D · 입체 세 판을 나란히 본 뒤
 * 룬마다 고름 — "몇개는 블록으로 하는게 이쁘고 몇개는 2d가 예쁘고 몇개는 3d가 예쁜데"): 블록 판을 고른 룬의 큰 물체를 골렘 소환처럼 블록 표시 여럿으로 짠 모형
 * (PairKit.Rig) — 블록 무늬 그대로, 빛나는 곳은 밝기 15, 끝에 부서져 흩어짐 (shatter). 쓰는 룬: 천붕 · 검신 강림 (검) · 대천사 강림 (날개).
 * 모형 축 (PairKit 과 같음): +Y = 위 (칼끝), +Z = 앞 (넓은 면 — 보는 쪽), +X = 앞을 보는 모형의 왼쪽. 2D 그림 (빛 · 고리 · 문양) 은 룬마다 그대로 둠.
 * 첫 블록 판의 달 · 창 · 구름 · 눈 · 블랙홀 모형은 커밋 b9b80e4c 에 (적월 강림 · 천공의 눈 · 블랙홀은 2D, 뇌신의 창은 입체로 고름).
 */
final class RuneBlocks {
    private RuneBlocks() {}

    static final BlockState QUARTZ = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    static final BlockState SHROOM = Blocks.SHROOMLIGHT.defaultBlockState();
    static final BlockState GLOWSTONE = Blocks.GLOWSTONE.defaultBlockState();
    static final BlockState DARK_OAK = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
    // 26.3 의 물들인 블록 (양털 · 콘크리트) 은 색 묶음 (Blocks.DYED_… 의 .white() 꼴) 이라 이름이 바뀌어 — 흰 것은 눈 블록
    static final BlockState WHITE = Blocks.SNOW_BLOCK.defaultBlockState();

    // ------------------------------------------------------------ 모양 도움

    private static double snap(double w, double step) {
        double q = step / 2;
        return Math.max(q, Math.round(w / q) * q);
    }

    /** 타원 원판 (반너비 a · 반높이 b · 두께 depth, 가운데 c — 모형 축) 을 가로줄 블록으로 쌓음 — 줄 높이 약 step, 줄 너비는 step/2 칸 단위 (픽셀 원) */
    static void disc(PairKit.Rig g, int grp, Vec3 c, double a, double b, double step, double depth, BlockState st, Brightness light) {
        int n = Math.max(1, (int) Math.round(2 * b / step));
        double h = 2 * b / n;
        for (int i = 0; i < n; i++) {
            double y = -b + h * (i + 0.5);
            double w = snap(a * Math.sqrt(Math.max(0, 1 - (y * y) / (b * b))), step);
            g.add(grp, st, new Vec3(c.x, c.y + y, c.z), new Vec3(2 * w, h, depth), null, light);
        }
    }

    /** 고리 (바깥 반지름 ro · 안 반지름 ri) — 안을 지나는 줄은 왼쪽 · 오른쪽 둘 */
    static void ring(PairKit.Rig g, int grp, Vec3 c, double ro, double ri, double step, double depth, BlockState st, Brightness light) {
        int n = Math.max(1, (int) Math.round(2 * ro / step));
        double h = 2 * ro / n;
        for (int i = 0; i < n; i++) {
            double y = -ro + h * (i + 0.5);
            double wo = snap(Math.sqrt(Math.max(0, ro * ro - y * y)), step);
            if (Math.abs(y) >= ri) {
                g.add(grp, st, new Vec3(c.x, c.y + y, c.z), new Vec3(2 * wo, h, depth), null, light);
                continue;
            }
            double wi = Math.min(wo - step / 2, snap(Math.sqrt(Math.max(0, ri * ri - y * y)), step));
            double seg = wo - wi;
            double mid = (wo + wi) / 2;
            g.add(grp, st, new Vec3(c.x + mid, c.y + y, c.z), new Vec3(seg, h, depth), null, light);
            g.add(grp, st, new Vec3(c.x - mid, c.y + y, c.z), new Vec3(seg, h, depth), null, light);
        }
    }

    /** z 축으로 45° 돌린 상자 (마름모 — 칼끝 · 창날 · 코등이 끝) */
    private static Quaternionf diamond() {
        return PairKit.about(new Vec3(0, 0, 1), 45);
    }

    /** 처음 자세 (띄우기 전에 정함 — 띄운 뒤 2틱부터 pose 로 옮김) */
    private static PairKit.Rig rig(ServerLevel level, Vec3 base, int life, Vec3 offset, Quaternionf rot, double scale) {
        PairKit.Rig g = new PairKit.Rig(level, base, life);
        g.offset = offset;
        g.rot = rot;
        g.scale = scale;
        return g;
    }

    // ------------------------------------------------------------ 부서짐

    /**
     * 부서져 흩어짐 (세상 축 — 칼끝이 아래인 검처럼 돌린 모형도): 부품마다 지금 자리에서 모형 가운데 → 바깥으로 spread 칸 안 · 아래로 fall 칸 안 (floorY 밑으로는
     * 안 감) 옮기며 shrink 배로 줄고 제멋대로 기욺 (ticks 동안). 모형이 사라지는 것은 띄울 때 정한 삶 (life) 에서
     */
    static void shatter(PairKit.Rig g, int ticks, double spread, double fall, double floorY, double shrink) {
        Vec3 mid = g.world(Vec3.ZERO);
        for (PairKit.Part p : g.parts) {
            Vec3 w = g.world(p.group(), p.c());
            Vec3 out = V.flat(V.sub(w, mid));
            double k = 0.4 + 0.6 * mmorpg.fabric.skill.Kit.rand();
            Vec3 n = V.add(w, out, spread * k);
            n = new Vec3(n.x + (mmorpg.fabric.skill.Kit.rand() - 0.5) * 0.6, Math.max(floorY + 0.15, n.y - fall * (0.5 + 0.5 * mmorpg.fabric.skill.Kit.rand())),
                    n.z + (mmorpg.fabric.skill.Kit.rand() - 0.5) * 0.6);
            Quaternionf q = new Quaternionf(g.rot);
            if (p.group() > 0 && p.group() < g.groupRot.length && g.groupRot[p.group()] != null) q.mul(g.groupRot[p.group()]);
            q.mul(p.q());
            q.rotateXYZ((float) ((mmorpg.fabric.skill.Kit.rand() - 0.5) * 1.6), (float) ((mmorpg.fabric.skill.Kit.rand() - 0.5) * 1.6),
                    (float) ((mmorpg.fabric.skill.Kit.rand() - 0.5) * 1.6));
            double sc = g.scale * shrink;
            FxDisplays.move(p.d(), PairKit.box(V.sub(n, g.base), q, p.size().x * sc, p.size().y * sc, p.size().z * sc), ticks);
        }
    }

    // ------------------------------------------------------------ 돌림 도움

    /** 모형 +Y 가 up, 넓은 면 (+Z) 이 face 쪽 (face 는 up 에 수직으로 맞춤) */
    static Quaternionf orient(Vec3 up, Vec3 face) {
        Vec3 y = V.unit(up);
        Vec3 z = V.sub(face, V.scale(y, V.dot(face, y)));
        if (V.len(z) < 1e-4) z = Math.abs(y.y) > 0.9 ? new Vec3(0, 0, 1) : V.UP;
        z = V.unit(z);
        Vec3 x = V.cross(y, z);
        return FxDisplays.basis(x, y, z);
    }

    /** 넓은 면이 from → to (가로) 쪽을 보는 세운 모형 (원판 · 눈 · 구름) */
    static Quaternionf faceTo(Vec3 from, Vec3 to) {
        Vec3 d = V.flat(V.sub(to, from));
        return PairKit.facing(V.len(d) < 1e-4 ? new Vec3(0, 0, 1) : d);
    }

    // ------------------------------------------------------------ 검

    /**
     * 검 (가운데가 원점, 칼끝 +Y, 넓은 면 ±Z — 길이 L): 머리 · 손잡이 · 코등이 (양 끝 마름모) · 칼날 세 마디 (끝으로 좁아짐) · 칼끝 마름모 · 홈 (빛나는 줄).
     * 빛나는 곳 (칼날 · 홈 · 머리) 은 밝기 15
     */
    static PairKit.Rig sword(ServerLevel level, Vec3 base, int life, Vec3 offset, Quaternionf rot, double L, BlockState blade, BlockState fuller, BlockState guard,
            BlockState grip, BlockState pommel) {
        PairKit.Rig g = rig(level, base, life, offset, rot, 1);
        double u = L / 9;
        double y0 = -L / 2;
        double gripLen = 0.16 * L;
        double t = 0.3 * u;
        g.add(0, pommel, new Vec3(0, y0 + 0.25 * u, 0), new Vec3(0.5 * u, 0.5 * u, 0.5 * u), null, PairKit.GLOW);
        g.add(0, grip, new Vec3(0, y0 + 0.5 * u + gripLen / 2, 0), new Vec3(0.3 * u, gripLen, 0.3 * u), null, PairKit.LIT);
        double yg = y0 + 0.5 * u + gripLen + 0.2 * u;
        g.add(0, guard, new Vec3(0, yg, 0), new Vec3(2.3 * u, 0.4 * u, 0.6 * u), null, PairKit.LIT);
        for (int s : new int[] {-1, 1}) g.add(0, pommel, new Vec3(s * 1.3 * u, yg, 0), new Vec3(0.42 * u, 0.42 * u, 0.5 * u), diamond(), PairKit.GLOW);
        double yb = yg + 0.2 * u;
        double B = L / 2 - yb;
        double[][] secs = {{0, 0.5, 1.0}, {0.5, 0.78, 0.8}, {0.78, 0.92, 0.56}};
        for (double[] s : secs) {
            double a = yb + B * s[0];
            double b = yb + B * s[1];
            g.add(0, blade, new Vec3(0, (a + b) / 2, 0), new Vec3(s[2] * u, b - a, t), null, PairKit.GLOW);
        }
        g.add(0, blade, new Vec3(0, L / 2 - 0.3 * u, 0), new Vec3(0.42 * u, 0.42 * u, t), diamond(), PairKit.GLOW);
        g.add(0, fuller, new Vec3(0, yb + B * 0.4, 0), new Vec3(0.22 * u, B * 0.7, t + 0.06), null, PairKit.GLOW);
        return g;
    }

    /** 칼끝이 아래 · 넓은 면이 face 쪽 (하늘에서 떨어져 꽂히는 검) */
    static Quaternionf tipDown(Vec3 face) {
        return orient(V.DOWN, face);
    }

    // ------------------------------------------------------------ 날개

    static final int WING_R = 1;
    static final int WING_L = 2;

    /**
     * 날개 한 쌍 (등 가운데가 원점, 뒤 = -Z 쪽에 붙임 — 날개 폭 span): 어깨에서 바깥 위로 뻗은 뼈 (금 블록) · 뼈를 따라 아래 바깥으로 부채처럼 늘어선 깃털 다섯
     * (눈 블록 — 바깥일수록 길고 옆으로 누움) · 안쪽 짧은 깃털 셋 (석영 — 밝기 15). 날개마다 어깨 축으로 도는 무리 (WING_R · WING_L — 날갯짓)
     */
    static PairKit.Rig wings(ServerLevel level, Vec3 base, int life, Vec3 offset, Quaternionf rot, double scale, double span) {
        PairKit.Rig g = rig(level, base, life, offset, rot, scale);
        g.group(new Vec3(-0.25, 0, 0));
        g.group(new Vec3(0.25, 0, 0));
        for (int side : new int[] {-1, 1}) {
            int grp = side < 0 ? WING_R : WING_L;
            Vec3 sh = new Vec3(side * 0.25, 0, 0);
            Vec3 boneDir = V.unit(new Vec3(side * 0.9, 0.42, 0));
            double boneLen = span * 0.62;
            Vec3 boneC = V.add(sh, boneDir, boneLen / 2);
            g.add(grp, GOLD, boneC, new Vec3(boneLen, 0.2, 0.18), PairKit.about(new Vec3(0, 0, 1), side * Math.toDegrees(Math.atan2(0.42, 0.9))), PairKit.GLOW);
            for (int i = 0; i < 5; i++) {
                double k = i / 4.0;
                Vec3 root = V.add(sh, boneDir, boneLen * (0.25 + 0.75 * k));
                double ang = 8 + 62 * k; // 아래에서 바깥으로 누운 각 (도)
                double len = span * (0.5 + 0.3 * k);
                Vec3 d = new Vec3(side * Math.sin(Math.toRadians(ang)), -Math.cos(Math.toRadians(ang)), 0);
                Vec3 c = V.add(root, d, len / 2);
                g.add(grp, WHITE, new Vec3(c.x, c.y, -0.02 * i), new Vec3(0.34, len, 0.1), PairKit.about(new Vec3(0, 0, 1), side * ang), PairKit.LIT);
            }
            for (int i = 0; i < 3; i++) {
                double k = i / 2.0;
                Vec3 root = V.add(sh, boneDir, boneLen * (0.2 + 0.55 * k));
                double ang = 18 + 40 * k;
                double len = span * 0.3;
                Vec3 d = new Vec3(side * Math.sin(Math.toRadians(ang)), -Math.cos(Math.toRadians(ang)), 0);
                Vec3 c = V.add(root, d, len / 2);
                g.add(grp, QUARTZ, new Vec3(c.x, c.y, -0.12), new Vec3(0.3, len, 0.08), PairKit.about(new Vec3(0, 0, 1), side * ang), PairKit.GLOW);
            }
        }
        return g;
    }

    /** 날갯짓: lift 도 (+ = 날개 끝이 위로) · 날개는 뒤로 30° 젖힘 (V 자 — 옆에서도 날개 면이 보임) */
    static Quaternionf[] flap(double lift) {
        Vec3 y = new Vec3(0, 1, 0);
        Vec3 z = new Vec3(0, 0, 1);
        return new Quaternionf[] {new Quaternionf(), PairKit.about(y, -30).mul(PairKit.about(z, -lift)), PairKit.about(y, 30).mul(PairKit.about(z, lift))};
    }
}
