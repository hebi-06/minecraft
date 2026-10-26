package mmorpg.fabric.client.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mmorpg.fabric.RpgWeaponItem;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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
        if (!Float.isNaN(p.yaw) && p.faceYaw) {
            // 0.44.0 대장장이 망치질: 몸이 모루 쪽으로 돌아섬 (들어오고 나갈 때 무게만큼 — 바로 돌지 않게), 머리는 몸 쪽 (숙임은 동작의 head 열쇠)
            float w = Math.max(0F, Math.min(1F, p.w));
            state.bodyRot += net.minecraft.util.Mth.wrapDegrees(p.yaw - state.bodyRot) * w;
            state.yRot *= 1F - w;
            state.xRot *= 1F - w;
        } else if (!Float.isNaN(p.yaw)) {
            // 구르기: 몸 · 머리가 구르는 쪽을 봄
            state.bodyRot = p.yaw;
            state.yRot = 0;
            state.xRot = 0;
        }
        boolean leftMain = state.mainArm == HumanoidArm.LEFT;
        // 0.46.0 룬 동작의 손: 맨손 (주먹 · 손바닥) 이면 든 것을 숨기고, 소품 구간이면 그 소품을 듦
        if (p.hide || p.mainBare()) {
            if (leftMain) state.leftHandItemState.clear();
            else state.rightHandItemState.clear();
        }
        if (p.offBare()) {
            if (leftMain) state.rightHandItemState.clear();
            else state.leftHandItemState.clear();
        }
        // 0.44.0 동작이 드는 아이템 (대장장이 망치 — 주 손에 든 것 대신), 0.46.0 소품 (연막탄 · 표창 · 덫 …) 은 그 t 구간에만
        if (p.hold != null && p.w > 0 && (p.mainMode == MotionPoses.WEAPON || p.propNow())) holdIn(entity, state, p.hold, true);
        if (p.holdOff != null && p.w > 0 && p.offMode == MotionPoses.PROP && p.holdOffNow) holdIn(entity, state, p.holdOff, false);
    }

    private static final java.util.Map<String, ItemStack> HOLD = new java.util.HashMap<>();

    /** item_model id 의 아이템 (막대기에 모델만 바꿈 — 그리기용, 없으면 EMPTY) */
    public static ItemStack holdStack(String model) {
        return HOLD.computeIfAbsent(model, k -> {
            Identifier id = Identifier.tryParse(k);
            if (id == null) return ItemStack.EMPTY;
            ItemStack s = new ItemStack(Items.STICK);
            s.set(DataComponents.ITEM_MODEL, id);
            return s;
        });
    }

    /** 주 손에 이 모델의 아이템을 들림 (렌더 상태만) */
    public static void holdIn(Avatar entity, AvatarRenderState state, String model) {
        holdIn(entity, state, model, true);
    }

    /** 0.46.0 주 손 (main) · 왼손 (아니면) 에 이 모델의 아이템을 들림 (렌더 상태만) */
    public static void holdIn(Avatar entity, AvatarRenderState state, String model, boolean main) {
        ItemStack s = holdStack(model);
        if (s.isEmpty()) return;
        boolean left = (state.mainArm == HumanoidArm.LEFT) == main;
        Minecraft.getInstance().getItemModelResolver().updateForLiving(left ? state.leftHandItemState : state.rightHandItemState, s,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
        if (left) state.leftArmPose = HumanoidModel.ArmPose.ITEM;
        else state.rightArmPose = HumanoidModel.ArmPose.ITEM;
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

    /** 시험 (사진첩): 이 사람의 모델을 Y 축으로 debugYaw 도 돌려 옆 · 3/4 모습을 찍음 */
    public static int debugEntity = Integer.MIN_VALUE;
    public static float debugYaw;

    public static void rotate(AvatarRenderState state, PoseStack ps) {
        if (state.id == debugEntity && debugYaw != 0) ps.rotate(Axis.YP.rotationDegrees(debugYaw));
        Pose p = of(state);
        if (p == null || !p.has(Pose.ROOT)) return;
        float w = p.w;
        // 0.46.0 걸으면 다리를 바닐라 걸음에 맡기는 동작 (softLegs) 은 몸을 낮춘 몫도 같이 줄임 (무릎 꿇기 · 웅크림이 걸으며 땅에 묻히지 않게)
        float wu = p.softLegs && p.up < 0 ? w * (1 - Math.min(1, state.walkAnimationSpeed * 2.5F)) : w;
        if (p.up != 0 || p.fwd != 0) ps.translate(0, p.up * wu, -p.fwd * w);
        if (p.spin != 0) ps.rotate(Axis.YP.rotation(p.spin));
        if (p.flip != 0) {
            ps.translate(0, FLIP_PIVOT, 0);
            ps.rotate(Axis.XP.rotation(-p.flip));
            ps.translate(0, -FLIP_PIVOT, 0);
        }
    }

    /** 손 자리 (translateToHand 끝): 팔꿈치를 굽혔으면 팔뚝을 따라 돌리고, 팔뚝 가운데 축으로 비틀고, 손목 (손 끝 가운데) 에서 무기를 꺾음 (x · 옆 z) */
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
        boolean right = arm == HumanoidArm.RIGHT;
        boolean has = p.has(right ? Pose.RA : Pose.LA);
        float tw = has ? (right ? p.raTwist : p.laTwist) * p.w : 0;
        float wr = has ? (right ? p.raWrist : p.laWrist) * p.w : 0;
        float dv = has ? (right ? p.raDev : p.laDev) * p.w : 0;
        if (tw == 0 && wr == 0 && dv == 0) return;
        float[] c = b.mmorpg$center();
        boolean known = c != null && !Float.isNaN(c[0]);
        float cx = known ? c[0] / 16F : (right ? -1 : 1) / 16F;
        float cz = known ? c[2] / 16F : 0;
        if (tw != 0) {
            ps.translate(cx, 0, cz);
            ps.rotate(Axis.YP.rotation(tw));
            ps.translate(-cx, 0, -cz);
        }
        if (wr != 0 || dv != 0) {
            float wy = 9.5F / 16F;
            ps.translate(cx, wy, cz);
            if (wr != 0) ps.rotate(Axis.XP.rotation(wr));
            if (dv != 0) ps.rotate(Axis.ZP.rotation(dv));
            ps.translate(-cx, -wy, -cz);
        }
    }

    // ------------------------------------------------------------ 1인칭

    private static final Pose FP = new Pose();

    /** 1인칭: 이 손 (주 손) 에 동작이 있는지 (바닐라 휘두르기를 막을지) */
    public static boolean firstPersonActive(HumanoidArm arm) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || arm != mc.player.getMainArm()) return false;
        return ClientMotions.localPose(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), FP2) != null;
    }

    private static final Pose FP2 = new Pose();

    /**
     * 1인칭 무기 손잡이 자리 (손에 든 아이템 자리 → 손잡이, 화면 공간 칸 · 오른손 — tools/weapon_models.py 의 J_P − JAVA_FP_ARM).
     * 무기 모델의 1인칭 display 가 손잡이를 손 자리에서 이만큼 떨어뜨려 두므로, 회전은 이 점을 축으로 (아니면 무기가 화면 밖으로 휘둘림)
     */
    private static final Vector3f GRIP = new Vector3f(0.266F, -0.099F, -0.655F);
    private static final Vector3f GRIP_DAGGER = new Vector3f(0.143F, -0.002F, -0.405F);
    private static final Vector3f GRIP_SHIELD = new Vector3f(0.331F, 0.019F, -0.468F);
    private static final Vector3f GRIP_WAND = new Vector3f(0.491F, -0.282F, -0.780F);
    private static final Vector3f GRIP_BOW = new Vector3f(0.612F, 0.204F, -1.155F);

    /** 무기 종류 → 1인칭 손잡이 자리 (칼 · 도끼 · 마검 · 창 · 낫 · 망치 · 홀 · 그 밖은 GRIP) */
    static Vector3f grip(String type) {
        return switch (type) {
            case "dagger" -> GRIP_DAGGER;
            case "shield" -> GRIP_SHIELD;
            case "wand" -> GRIP_WAND;
            case "bow" -> GRIP_BOW;
            default -> GRIP;
        };
    }

    /** 주 손을 그리기 시작할 때 (submitArmWithItem 처음) 의 자리 — 바닐라가 손 자리까지 옮긴 몫을 재는 기준 */
    private static final Matrix4f FP_BASE = new Matrix4f();
    private static boolean fpBaseSet;

    /** submitArmWithItem 처음: 주 손이면 지금 자리를 적어 둠 */
    public static void firstPersonBase(PoseStack ps, InteractionHand hand) {
        fpBaseSet = hand == InteractionHand.MAIN_HAND;
        if (fpBaseSet) FP_BASE.set(ps.last().pose());
    }

    /**
     * 1인칭 무기 (주 손 — 손에 든 아이템 자리 뒤, 휘두르기 대신): 동작 중이면 true.
     * 바닐라가 손 자리 (0.56, -0.52, -0.72) 밖으로 옮긴 몫 (휘두를 때 흔들림 · 공격 대기 · 바꿔 들 때 내려간 손) 을 무게만큼 되돌리고,
     * 손잡이를 fp 자리만큼 옮기고, 손잡이를 축으로 fp 회전 (방패는 옮기기만 — 겉면이 앞을 봄, 활은 비틀기 없이 끝 방향만)
     */
    public static boolean firstPerson(PoseStack ps, HumanoidArm arm) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (arm != mc.player.getMainArm()) {
            // 0.46.0 왼손 소품 (바닐라 아이템 길로 그림 — firstPersonProps 가 바꿔 넣음): fpo 만큼 옮기고 손 자리에서 돌림
            Pose p = ClientMotions.localPose(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), FP);
            if (p == null || p.offMode != MotionPoses.PROP || !p.holdOffNow) return false;
            ps.translate(p.fpoX * p.w, p.fpoY * p.w, p.fpoZ * p.w);
            ps.rotate(new Quaternionf().slerp(p.fpoQ, p.w));
            return true;
        }
        Pose p = ClientMotions.localPose(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), FP);
        if (p == null) return false;
        float w = p.w;
        if (fpBaseSet) {
            Vector3f moved = new Matrix4f(FP_BASE).invertAffine().mul(ps.last().pose()).getTranslation(new Vector3f());
            moved.sub(arm == HumanoidArm.RIGHT ? 0.56F : -0.56F, -0.52F, -0.72F);
            if (moved.lengthSquared() < 4F) ps.translate(-moved.x * w, -moved.y * w, -moved.z * w);
            fpBaseSet = false;
        }
        ps.translate(p.fpX * w, p.fpY * w, p.fpZ * w);
        if (p.propNow()) {
            // 0.46.0 소품 (바닐라 아이템처럼 손 자리에 듦): 손 자리를 축으로 돌림
            ps.rotate(new Quaternionf().slerp(p.fpQ, w));
            return true;
        }
        String type = mc.player.getMainHandItem().getItem() instanceof RpgWeaponItem item && item.weapon() != null ? item.weapon().type() : "";
        if (!type.equals("shield")) {
            Vector3f g = grip(type);
            boolean right = arm == HumanoidArm.RIGHT;
            float gx = right ? g.x : -g.x;
            Quaternionf q = p.fpQ;
            if (type.equals("bow")) {
                // 활은 1인칭 자세가 세운 무기와 달라 (날이 없고 활대가 앞으로 기욺) 비틀기 없이 끝 방향만 돌림 (가장 작은 회전 — 위로 쏘기는 뒤로 젖힘)
                Vector3f b0 = new Vector3f(right ? MotionPoses.FP_REST_BLADE.x : -MotionPoses.FP_REST_BLADE.x, MotionPoses.FP_REST_BLADE.y,
                        MotionPoses.FP_REST_BLADE.z).normalize();
                q = new Quaternionf().rotationTo(b0, p.fpQ.transform(new Vector3f(b0)));
            }
            ps.translate(gx, g.y, g.z);
            ps.rotate(new Quaternionf().slerp(q, w));
            ps.translate(-gx, -g.y, -g.z);
        }
        if (BladeTrails.captureFp) BladeTrails.captureFirstPerson(FP_BASE, ps.last().pose(), arm == HumanoidArm.LEFT); // 0.40.0 시험
        return true;
    }

    // ------------------------------------------------------------ 0.46.0 1인칭 맨손 · 소품 (룬 동작)

    private static final Pose FP3 = new Pose();
    private static final Pose FP4 = new Pose();
    /** 바닐라 1인칭 맨팔의 어깨 자리 (renderPlayerArm 의 옮김 · 회전을 따라간 팔 위 끝 — 오른팔 기준, tools/hands_preview.py) — 맨팔 회전의 축 */
    private static final Vector3f ARM_BASE = new Vector3f(0.49F, -0.83F, -0.53F);
    /** 들어오고 나갈 때 맨팔이 화면 아래에서 올라오는 몫 (칸) */
    private static final float ARM_RISE = 0.55F;

    /** 1인칭 손 하나를 맨팔로 그려야 하는지 (주먹 · 손바닥 · 소품 구간 밖) — 지금 동작이 없으면 false */
    public static boolean firstPersonBare(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Pose p = ClientMotions.localPose(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), FP3);
        if (p == null) return false;
        return hand == InteractionHand.MAIN_HAND ? p.mainBare() : p.offBare();
    }

    /**
     * 1인칭 맨팔 (submitArmWithItem 처음 — true 면 바닐라 대신 그렸음): 룬 동작이 이 손을 맨손으로 쓰면 바닐라 맨팔 (renderPlayerArm) 을 fp (주 손) · fpo (왼손)
     * 만큼 옮기고 맨팔 자리를 축으로 돌려 그림 — 들어오고 나갈 때는 화면 아래에서 올라오고 내려감. 든 무기 · 왼손 아이템은 그리지 않음
     */
    public static boolean firstPersonHands(Object renderer, net.minecraft.client.renderer.state.level.PlayerRenderState player, InteractionHand hand, PoseStack ps,
            net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Pose p = ClientMotions.localPose(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), FP3);
        if (p == null) return false;
        boolean main = hand == InteractionHand.MAIN_HAND;
        if (!(main ? p.mainBare() : p.offBare())) return false;
        if (mc.player.isInvisible()) return true;
        HumanoidArm arm = main ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1F : -1F;
        float w = p.w;
        float x = main ? p.fpX : p.fpoX;
        float y = main ? p.fpY : p.fpoY;
        float z = main ? p.fpZ : p.fpoZ;
        Quaternionf q = main ? p.fpQ : p.fpoQ;
        ps.pushPose();
        ps.translate(x * w, y * w - ARM_RISE * (1F - w), z * w);
        ps.translate(side * ARM_BASE.x, ARM_BASE.y, ARM_BASE.z);
        ps.rotate(new Quaternionf().slerp(q, w));
        ps.translate(-side * ARM_BASE.x, -ARM_BASE.y, -ARM_BASE.z);
        ((mmorpg.fabric.client.mixin.FirstPersonArmInvoker) renderer).mmorpg$renderPlayerArm(ps, collector, light, 0F, 0F, arm, player);
        ps.popPose();
        return true;
    }

    /**
     * 1인칭 소품 (FirstPersonHandsAndItems.extractRenderState 끝): 룬 동작의 소품 구간이면 주 손 · 왼손의 그릴 아이템을 그 소품으로 바꿈 (렌더 상태만 —
     * 들고 있는 아이템 · 바꿔 들기 애니메이션은 그대로). 움직임은 applyItemArmTransform 끝의 {@link #firstPerson} (fp · fpo)
     */
    public static void firstPersonProps(net.minecraft.client.player.LocalPlayer player, float partial,
            net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state) {
        if (player == null) return;
        boolean leftMain = player.getMainArm() == HumanoidArm.LEFT;
        Pose p = ClientMotions.pose(player.getId(), leftMain, partial, FP4);
        if (p == null) return;
        if (p.propNow() && p.hold != null) {
            ItemStack s = holdStack(p.hold);
            if (!s.isEmpty()) {
                state.mainHandItem = s;
                Minecraft.getInstance().getItemModelResolver().updateForLiving(state.mainHandRenderState, s,
                        leftMain ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, player);
            }
        }
        if (p.offMode == MotionPoses.PROP && p.holdOffNow && p.holdOff != null) {
            ItemStack s = holdStack(p.holdOff);
            if (!s.isEmpty()) {
                state.offHandItem = s;
                Minecraft.getInstance().getItemModelResolver().updateForLiving(state.offHandRenderState, s,
                        leftMain ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, player);
            }
        }
    }
}
