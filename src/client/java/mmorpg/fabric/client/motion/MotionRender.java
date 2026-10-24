package mmorpg.fabric.client.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.38.0 동작 그리기 (믹스인이 부름):
 * <ul>
 *   <li>extract (AvatarRenderer.extractRenderState 끝): 이 사람의 지금 자세 → 렌더 상태 (구르기는 몸을 구르는 쪽으로 · 던지기는 무기를 숨김)</li>
 *   <li>applyModel (HumanoidModel.setupAnim 끝 — 사람 모델 · 갑옷 모델): 몸통은 엉덩이 축으로 숙이고 비틀어 목 · 어깨 자리를 옮기고, 팔 회전 (뿌리 기준) ·
 *       다리 · 머리를 바닐라 자세와 무게만큼 섞음, 팔꿈치 · 무릎 굽힘 (BendablePart)</li>
 *   <li>rotate (AvatarRenderer.setupRotations 끝): 뿌리 — 앞으로 내딛기 · 낮추기 · 회전 (spin) · 앞구르기 (flip)</li>
 *   <li>hand (PlayerModel.translateToHand 끝): 든 무기가 굽힌 팔뚝을 따라감</li>
 *   <li>firstPerson (1인칭 무기 휘두르기 대신) · 화면 (구르기 — ClientMotions.cameraDip)</li>
 * </ul>
 */
public final class MotionRender {
    private MotionRender() {}

    public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "mmorpg:motion");
    /** 동작 없음 */
    public static final Pose NONE = new Pose();

    /** 앞구르기 축 높이 (칸) */
    static final float FLIP_PIVOT = 0.55F;
    private static final Vector3f HIP = new Vector3f(0, 12, 0);

    // ------------------------------------------------------------ 렌더 상태

    public static void extract(Avatar entity, AvatarRenderState state, float partial) {
        Pose p = null;
        if (!state.isVisuallySwimming && !state.isFallFlying && !state.isSpectator) {
            p = ClientMotions.pose(entity.getId(), state.mainArm == HumanoidArm.LEFT, partial, new Pose());
        }
        state.setData(KEY, p == null ? NONE : p);
        if (p == null) return;
        if (!Float.isNaN(p.yaw)) {
            // 구르기: 몸 · 머리가 구르는 쪽을 봄
            state.bodyRot = p.yaw;
            state.yRot = 0;
            state.xRot = 0;
        }
        if (p.hide) {
            if (state.mainArm == HumanoidArm.LEFT) state.leftHandItemState.clear();
            else state.rightHandItemState.clear();
        }
    }

    static Pose of(HumanoidRenderState state) {
        Pose p = state.getData(KEY);
        return p == null || p == NONE || p.w <= 0 ? null : p;
    }

    // ------------------------------------------------------------ 모델

    private static float lerp(float a, float b, float u) {
        return a + (b - a) * u;
    }

    private static void bend(ModelPart part, float angle, float dx, float dy, float dz) {
        BendablePart b = (BendablePart) (Object) part;
        if (angle == 0) {
            b.mmorpg$setBend(0, 0, 0, 0);
            return;
        }
        float[] c = b.mmorpg$center();
        if (c == null || Float.isNaN(c[0])) b.mmorpg$setBend(angle, dx, dy, dz);
        else b.mmorpg$setBend(angle, c[0], c[1], c[2]);
    }

    /** 팔 회전 (뿌리 기준 사원수) 을 바닐라 회전과 w 만큼 섞어 부위에 */
    private static void armTo(ModelPart arm, Quaternionf target, float w) {
        Quaternionf vanilla = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot);
        Quaternionf q = vanilla.slerp(target, w, new Quaternionf());
        Vector3f e = new Matrix3f().set(q).getEulerAnglesZYX(new Vector3f());
        arm.xRot = e.x;
        arm.yRot = e.y;
        arm.zRot = e.z;
    }

    public static void applyModel(HumanoidModel<?> m, HumanoidRenderState state) {
        Pose p = of(state);
        if (p == null) {
            bend(m.rightArm, 0, 0, 0, 0);
            bend(m.leftArm, 0, 0, 0, 0);
            bend(m.rightLeg, 0, 0, 0, 0);
            bend(m.leftLeg, 0, 0, 0, 0);
            return;
        }
        float w = p.w;
        boolean crouch = state.isCrouching;
        // 몸통: 엉덩이 축 — 목 · 어깨가 따라감 (웅크리면 바닐라 자리)
        if (p.has(Pose.BODY) && !crouch) {
            float lean = lerp(m.body.xRot, p.lean, w);
            float twist = lerp(m.body.yRot, p.twist, w);
            float side = lerp(m.body.zRot, p.side, w);
            Matrix3f rb = new Matrix3f().rotationZYX(side, twist, lean);
            Vector3f neck = new Vector3f(0, 0, 0).sub(HIP).mul(rb).add(HIP);
            m.body.xRot = lean;
            m.body.yRot = twist;
            m.body.zRot = side;
            m.body.x = neck.x;
            m.body.y = neck.y;
            m.body.z = neck.z;
            m.head.x = neck.x;
            m.head.y = neck.y;
            m.head.z = neck.z;
            Vector3f rs = new Vector3f(-5, 2, 0).sub(HIP).mul(rb).add(HIP);
            Vector3f ls = new Vector3f(5, 2, 0).sub(HIP).mul(rb).add(HIP);
            m.rightArm.x = lerp(m.rightArm.x, rs.x, w);
            m.rightArm.y = lerp(m.rightArm.y, rs.y, w);
            m.rightArm.z = lerp(m.rightArm.z, rs.z, w);
            m.leftArm.x = lerp(m.leftArm.x, ls.x, w);
            m.leftArm.y = lerp(m.leftArm.y, ls.y, w);
            m.leftArm.z = lerp(m.leftArm.z, ls.z, w);
        }
        if (p.has(Pose.HEAD)) {
            m.head.xRot += p.headX * w;
            m.head.yRot += p.headY * w;
        }
        if (p.has(Pose.RA)) {
            armTo(m.rightArm, p.ra, w);
            bend(m.rightArm, p.raBend * w, -1, 4, 0);
        } else bend(m.rightArm, 0, 0, 0, 0);
        if (p.has(Pose.LA)) {
            armTo(m.leftArm, p.la, w);
            bend(m.leftArm, p.laBend * w, 1, 4, 0);
        } else bend(m.leftArm, 0, 0, 0, 0);
        float wl = w;
        if (p.softLegs) wl *= 1 - Math.min(1, state.walkAnimationSpeed * 2.5F);
        if (state.isPassenger) wl = 0;
        if (p.has(Pose.LEGS) && wl > 0) {
            m.rightLeg.xRot = lerp(m.rightLeg.xRot, p.rlX, wl);
            m.rightLeg.yRot = lerp(m.rightLeg.yRot, p.rlY, wl);
            m.rightLeg.zRot = lerp(m.rightLeg.zRot, p.rlZ, wl);
            m.leftLeg.xRot = lerp(m.leftLeg.xRot, p.llX, wl);
            m.leftLeg.yRot = lerp(m.leftLeg.yRot, p.llY, wl);
            m.leftLeg.zRot = lerp(m.leftLeg.zRot, p.llZ, wl);
            bend(m.rightLeg, p.rk * wl, 0, 6, 0);
            bend(m.leftLeg, p.lk * wl, 0, 6, 0);
        } else {
            bend(m.rightLeg, 0, 0, 0, 0);
            bend(m.leftLeg, 0, 0, 0, 0);
        }
    }

    /** 갑옷 조각 모델 (PieceModel — 사람 모델과 같은 이름의 뼈): 팔 · 다리 뼈에 같은 굽힘 (관절 자리는 넓은 팔 기준) */
    public static void bendPieces(ModelPart root, HumanoidRenderState state) {
        Pose p = of(state);
        float w = p == null ? 0 : p.w;
        float wl = w;
        if (p != null && p.softLegs) wl *= 1 - Math.min(1, state.walkAnimationSpeed * 2.5F);
        if (state.isPassenger) wl = 0;
        piece(root, "right_arm", p != null && p.has(Pose.RA) ? p.raBend * w : 0, -1, 4);
        piece(root, "left_arm", p != null && p.has(Pose.LA) ? p.laBend * w : 0, 1, 4);
        piece(root, "right_leg", p != null && p.has(Pose.LEGS) ? p.rk * wl : 0, 0, 6);
        piece(root, "left_leg", p != null && p.has(Pose.LEGS) ? p.lk * wl : 0, 0, 6);
    }

    private static void piece(ModelPart root, String name, float angle, float jx, float jy) {
        if (!root.hasChild(name)) return;
        ((BendablePart) (Object) root.getChild(name)).mmorpg$setBend(angle, jx, jy, 0);
    }

    // ------------------------------------------------------------ 뿌리 · 손

    public static void rotate(AvatarRenderState state, PoseStack ps) {
        Pose p = of(state);
        if (p == null || !p.has(Pose.ROOT)) return;
        float w = p.w;
        if (p.up != 0 || p.fwd != 0) ps.translate(0, p.up * w, -p.fwd * w);
        if (p.spin != 0) ps.rotate(Axis.YP.rotation(p.spin));
        if (p.flip != 0) {
            ps.translate(0, FLIP_PIVOT, 0);
            ps.rotate(Axis.XP.rotation(-p.flip));
            ps.translate(0, -FLIP_PIVOT, 0);
        }
    }

    /** 손 자리 (translateToHand 끝): 팔꿈치를 굽혔으면 팔뚝을 따라 돌리고, 손목만큼 무기를 더 꺾음 (손목 축 = 손 끝 가운데) */
    public static void hand(HumanoidModel<?> m, HumanoidRenderState state, HumanoidArm arm, PoseStack ps) {
        ModelPart part = arm == HumanoidArm.RIGHT ? m.rightArm : m.leftArm;
        BendablePart b = (BendablePart) (Object) part;
        float a = b.mmorpg$bend();
        float x = b.mmorpg$jointX() / 16F;
        float y = b.mmorpg$jointY() / 16F;
        float z = b.mmorpg$jointZ() / 16F;
        if (a != 0) {
            ps.translate(x, y, z);
            ps.rotate(Axis.XP.rotation(a));
            ps.translate(-x, -y, -z);
        }
        Pose p = of(state);
        if (p == null) return;
        float wr = (arm == HumanoidArm.RIGHT ? (p.has(Pose.RA) ? p.raWrist : 0) : (p.has(Pose.LA) ? p.laWrist : 0)) * p.w;
        if (wr == 0) return;
        float[] c = b.mmorpg$center();
        float cx = c != null && !Float.isNaN(c[0]) ? c[0] / 16F : (arm == HumanoidArm.RIGHT ? -1 : 1) / 16F;
        float wy = 9.5F / 16F;
        ps.translate(cx, wy, 0);
        ps.rotate(Axis.XP.rotation(wr));
        ps.translate(-cx, -wy, 0);
    }

    // ------------------------------------------------------------ 1인칭

    private static final Pose FP = new Pose();

    /** 1인칭 무기 (주 손 — 휘두르기 대신): 동작 중이면 true */
    public static boolean firstPerson(PoseStack ps, HumanoidArm arm) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || arm != mc.player.getMainArm()) return false;
        Pose p = ClientMotions.localPose(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), FP);
        if (p == null) return false;
        float w = p.w;
        ps.translate(p.fpX * w, p.fpY * w, p.fpZ * w);
        if (p.fpRY != 0) ps.rotate(Axis.YP.rotation(p.fpRY * w));
        if (p.fpRX != 0) ps.rotate(Axis.XP.rotation(p.fpRX * w));
        if (p.fpRZ != 0) ps.rotate(Axis.ZP.rotation(p.fpRZ * w));
        return true;
    }
}
