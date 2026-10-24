package mmorpg.fabric.client.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 0.38.0 팔꿈치 · 무릎 굽힘 그리기 (ModelPartMixin): 굽힘이 있는 부위 (팔 · 다리 — 소매 · 바지 · 갑옷 조각 포함) 를 그리는 동안,
 * 그 부위 좌표에서 관절 평면 (y = 관절) 아래 꼭짓점은 관절 축 (로컬 x) 으로 θ 만큼 돌리고, 평면을 가로지르는 면은 평면에서 잘라
 * 자른 자리 꼭짓점을 θ/2 · 비스듬한 이음 (두께 1/cos(θ/2)) 로 맞붙임 — 두 마디가 틈 없이 꺾인 모양 (bendy 방식, 텍스처는 그대로).
 * 아래 부위 (자식) 는 굽힌 부위 기준으로 옮겨 같은 식으로 굽힘.
 */
public final class BendMesh {
    private BendMesh() {}

    private static final float EPS = 1e-5F;

    /** 굽히는 중인 부위 하나 (렌더 스레드 하나라 정적) */
    private static ModelPart owner;
    private static final Matrix4f LIMB = new Matrix4f();
    private static final Matrix4f INV = new Matrix4f();
    private static final Matrix3f LIMB_N = new Matrix3f();
    private static final Matrix3f INV_N = new Matrix3f();
    private static float angle;
    private static float jx;
    private static float jy;
    private static float jz;
    private static float cosH;
    private static float sinH;
    private static float cosF;
    private static float sinF;
    private static float miter;

    public static boolean active() {
        return owner != null;
    }

    /** 굽힘이 있는 부위의 compile 처음 (pose = 그 부위 자리): 굽힘 시작 — 그 부위의 render 가 끝나면 end */
    public static void begin(ModelPart part, PoseStack.Pose pose, float bend, float jxPx, float jyPx, float jzPx) {
        if (owner != null) return;
        float a = Math.max(-2.6F, Math.min(2.6F, bend));
        owner = part;
        angle = a;
        jx = jxPx / 16F;
        jy = jyPx / 16F;
        jz = jzPx / 16F;
        LIMB.set(pose.pose());
        INV.set(pose.pose()).invert();
        LIMB_N.set(pose.normal());
        INV_N.set(pose.normal()).invert();
        cosF = (float) Math.cos(a);
        sinF = (float) Math.sin(a);
        cosH = (float) Math.cos(a / 2);
        sinH = (float) Math.sin(a / 2);
        miter = 1F / Math.max(0.2F, cosH);
    }

    public static void end(ModelPart part) {
        if (owner == part) owner = null;
    }

    /** 시험: 굽힘을 모두 끝냄 */
    public static void reset() {
        owner = null;
    }

    // ------------------------------------------------------------ 그리기

    /** 굽히는 중이면 이 부위의 상자들을 굽혀 그림 (true = 바닐라 compile 대신) */
    public static boolean compile(List<ModelPart.Cube> cubes, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color) {
        if (owner == null) return false;
        Matrix4f rel = new Matrix4f(INV).mul(pose.pose());
        Matrix3f relN = new Matrix3f(INV_N).mul(pose.normal());
        float[] px = new float[8];
        float[] py = new float[8];
        float[] pz = new float[8];
        float[] pu = new float[8];
        float[] pv = new float[8];
        int[] kind = new int[8];
        Vector3f v = new Vector3f();
        Vector3f n = new Vector3f();
        for (ModelPart.Cube cube : cubes) {
            for (ModelPart.Polygon poly : cube.polygons) {
                ModelPart.Vertex[] vs = poly.vertices();
                int k = vs.length;
                if (k < 3) continue;
                relN.transform(poly.normal(), n);
                // 굽힌 부위 좌표 (칸) 로
                float[] s = new float[k];
                float[] lx = new float[k];
                float[] ly = new float[k];
                float[] lz = new float[k];
                boolean any = false;
                boolean all = true;
                for (int i = 0; i < k; i++) {
                    rel.transformPosition(vs[i].x() / 16F, vs[i].y() / 16F, vs[i].z() / 16F, v);
                    lx[i] = v.x;
                    ly[i] = v.y;
                    lz[i] = v.z;
                    s[i] = v.y - jy;
                    if (s[i] > EPS) any = true;
                    if (s[i] < -EPS) all = false;
                }
                if (!any) {
                    emit(vc, lx, ly, lz, vs, k, n, 0, light, overlay, color);
                    continue;
                }
                if (all) {
                    emit(vc, lx, ly, lz, vs, k, n, 2, light, overlay, color);
                    continue;
                }
                // 관절 평면에서 자름 (위 마디: s ≤ 0, 아래 마디: s ≥ 0) — 자른 자리는 비스듬한 이음
                for (int side = 0; side < 2; side++) {
                    int c = 0;
                    for (int i = 0; i < k; i++) {
                        int j = (i + 1) % k;
                        float si = side == 0 ? -s[i] : s[i];
                        float sj = side == 0 ? -s[j] : s[j];
                        if (si >= -EPS) {
                            px[c] = lx[i];
                            py[c] = ly[i];
                            pz[c] = lz[i];
                            pu[c] = vs[i].u();
                            pv[c] = vs[i].v();
                            kind[c] = Math.abs(s[i]) <= EPS ? 1 : side * 2;
                            c++;
                        }
                        if ((si > EPS && sj < -EPS) || (si < -EPS && sj > EPS)) {
                            float f = si / (si - sj);
                            px[c] = lx[i] + (lx[j] - lx[i]) * f;
                            py[c] = jy;
                            pz[c] = lz[i] + (lz[j] - lz[i]) * f;
                            pu[c] = vs[i].u() + (vs[j].u() - vs[i].u()) * f;
                            pv[c] = vs[i].v() + (vs[j].v() - vs[i].v()) * f;
                            kind[c] = 1;
                            c++;
                        }
                        if (c >= 7) break;
                    }
                    if (c >= 3) emitClip(vc, px, py, pz, pu, pv, kind, c, n, side * 2, light, overlay, color);
                }
            }
        }
        return true;
    }

    /** 부위 좌표 (칸) 의 점 하나를 굽힘 (kind 0 = 위 마디 그대로 · 1 = 관절 이음 · 2 = 아래 마디) 뒤 화면 자리로 */
    private static void place(float x, float y, float z, int kind, Vector3f out) {
        float dy = y - jy;
        float dz = z - jz;
        if (kind == 2) {
            float ny = dy * cosF - dz * sinF;
            float nz = dy * sinF + dz * cosF;
            dy = ny;
            dz = nz;
        } else if (kind == 1) {
            dz *= miter;
            float ny = dy * cosH - dz * sinH;
            float nz = dy * sinH + dz * cosH;
            dy = ny;
            dz = nz;
        }
        LIMB.transformPosition(x, jy + dy, jz + dz, out);
    }

    private static void normal(Vector3f n, int kind, Vector3f out) {
        out.set(n);
        if (kind == 2) {
            float ny = n.y * cosF - n.z * sinF;
            float nz = n.y * sinF + n.z * cosF;
            out.set(n.x, ny, nz);
        }
        LIMB_N.transform(out);
        if (out.lengthSquared() > 1e-12F) out.normalize();
    }

    private static void emit(VertexConsumer vc, float[] lx, float[] ly, float[] lz, ModelPart.Vertex[] vs, int k, Vector3f n, int kind, int light, int overlay,
            int color) {
        Vector3f nn = new Vector3f();
        normal(n, kind, nn);
        Vector3f p = new Vector3f();
        for (int i = 0; i < k; i++) {
            place(lx[i], ly[i], lz[i], kind, p);
            vc.addVertex(p.x, p.y, p.z, color, vs[i].u(), vs[i].v(), overlay, light, nn.x, nn.y, nn.z);
        }
        for (int i = k; i < 4; i++) {
            place(lx[k - 1], ly[k - 1], lz[k - 1], kind, p);
            vc.addVertex(p.x, p.y, p.z, color, vs[k - 1].u(), vs[k - 1].v(), overlay, light, nn.x, nn.y, nn.z);
        }
    }

    /** 잘린 면 (꼭짓점 3 ~ 7) 을 사각형들로: (0, i, i + 1, i + 2) 부채 — 남는 하나는 마지막 꼭짓점을 겹쳐 */
    private static void emitClip(VertexConsumer vc, float[] x, float[] y, float[] z, float[] u, float[] v, int[] kind, int c, Vector3f n, int faceKind,
            int light, int overlay, int color) {
        Vector3f nn = new Vector3f();
        normal(n, faceKind, nn);
        Vector3f p = new Vector3f();
        int i = 1;
        while (i < c - 1) {
            int a = i;
            int b = i + 1;
            int d = Math.min(i + 2, c - 1);
            int[] idx = {0, a, b, d};
            for (int q : idx) {
                place(x[q], y[q], z[q], kind[q], p);
                vc.addVertex(p.x, p.y, p.z, color, u[q], v[q], overlay, light, nn.x, nn.y, nn.z);
            }
            i += 2;
        }
    }
}
