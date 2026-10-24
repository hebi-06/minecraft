package mmorpg.fabric.client.motion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Combat;
import mmorpg.core.Motions;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.motion.MotionNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 0.38.0 동작 (클라이언트): 사람마다 지금 하는 동작 (시작 시각 · 이름 · 구르기 쪽).
 * <ul>
 *   <li>내 평타: 공격 키 (Minecraft.startAttack 처음) 에 연타 단계를 정해 서버에 먼저 보내고 (참격 방향이 같게), 실제로 휘두를 때 (swing) 동작 시작 — 블록을 캘 때는 없음</li>
 *   <li>다른 사람 · 스킬: 서버의 PlayPayload</li>
 *   <li>구르기: 쉬프트를 doubleTap 틱 안에 두 번 — 움직이는 쪽 (가만히면 앞) 으로 ticks 틱 동안 distance 칸 (틱마다 빠르기를 정함), 재사용 대기 cooldown 틱,
 *       땅 위 · 탈것 · 물 · 겉날개 · 비행 아닐 때만, 구르는 동안 공격 없음 (무적 없음 — 서버는 둘레에 모습만 알림)</li>
 * </ul>
 */
public final class ClientMotions {
    private ClientMotions() {}

    /** 하는 중인 동작 (start = 시작 시각 — 클라이언트 틱) */
    record Active(String motion, double start, float yaw, int ticks) {}

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    /** 세계에 있는 동안 센 클라이언트 틱 */
    private static long ticks;

    // 내 평타
    private static boolean inAttack;
    private static int pendingStep;
    private static List<String> pendingCombo = List.of();
    private static String pendingType = "";
    private static int lastStep;
    private static long lastSwing = -1000;
    private static String lastType = "";

    // 구르기
    private static long rollStart = -1000;
    private static double rollX;
    private static double rollZ;
    private static double[] rollSpeeds = new double[0];
    private static long lastTap = -1000;

    // 시험 (사진첩): 이 사람의 자세를 이 동작 · t 로 멈춤
    private static int frozenEntity = Integer.MIN_VALUE;
    private static String frozenMotion;
    private static float frozenT;

    public static void clear() {
        ACTIVE.clear();
        inAttack = false;
        pendingStep = 0;
        lastStep = 0;
        lastSwing = -1000;
        rollStart = -1000;
        lastTap = -1000;
        frozenEntity = Integer.MIN_VALUE;
    }

    public static long ticks() {
        return ticks;
    }

    // ------------------------------------------------------------ 시작 · 지금 자세

    /** 동작 시작 (지금 틱이 끝나는 때부터 — 렌더의 다음 프레임이 t = 0) */
    public static void start(int entity, String motion, float yaw) {
        MotionPoses.Motion m = MotionPoses.get(motion);
        if (m == null) return;
        ACTIVE.put(entity, new Active(motion, ticks + 1, yaw, Math.max(1, m.ticks)));
    }

    /** 서버가 알린 동작 */
    public static void receive(MotionNet.PlayPayload p) {
        start(p.entity(), p.motion(), p.yaw());
    }

    /** 이 사람이 지금 하는 동작 이름 (없으면 null) */
    public static String current(int entity) {
        if (entity == frozenEntity) return frozenMotion;
        Active a = ACTIVE.get(entity);
        if (a == null) return null;
        return ticks + 1 - a.start() >= a.ticks() ? null : a.motion();
    }

    /** 이 사람의 지금 자세 (out 에 — 없으면 null). left = 주 손이 왼손 */
    public static Pose pose(int entity, boolean left, float partial, Pose out) {
        if (entity == frozenEntity && frozenMotion != null) {
            MotionPoses.Motion m = MotionPoses.get(frozenMotion);
            if (m == null) return null;
            MotionPoses.evaluate(m, frozenT, left, out);
            out.yaw = Float.NaN;
            return out;
        }
        Active a = ACTIVE.get(entity);
        if (a == null) return null;
        double t = (ticks + partial - a.start()) / a.ticks();
        if (t >= 1) {
            ACTIVE.remove(entity);
            return null;
        }
        MotionPoses.Motion m = MotionPoses.get(a.motion());
        if (m == null) return null;
        MotionPoses.evaluate(m, (float) Math.max(0, t), left, out);
        out.yaw = a.motion().equals("roll") ? a.yaw() : Float.NaN;
        return out;
    }

    /** 내 (1인칭) 지금 자세 */
    public static Pose localPose(float partial, Pose out) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        return pose(p.getId(), p.getMainArm() == HumanoidArm.LEFT, partial, out);
    }

    // ------------------------------------------------------------ 틱

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!ACTIVE.isEmpty()) ACTIVE.clear();
            return;
        }
        // 쉬프트 두 번 → 구르기 (창이 열려 있으면 키가 오지 않음)
        while (mc.options.keyShift.consumeClick()) {
            if (mc.gui.screen() != null) continue;
            if (ticks - lastTap <= Motions.doubleTap()) {
                tryRoll(mc);
                lastTap = -1000;
            } else lastTap = ticks;
        }
        // 구르는 동안: 틱마다 빠르기 (다음 틱의 움직임 — 높이 빠르기는 그대로)
        long i = ticks - rollStart;
        LocalPlayer p = mc.player;
        if (i >= 0 && i < rollSpeeds.length) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(rollX * rollSpeeds[(int) i], v.y, rollZ * rollSpeeds[(int) i]);
        }
        ticks++;
        if (ticks % 40 == 0) ACTIVE.entrySet().removeIf(e -> ticks + 1 - e.getValue().start() > e.getValue().ticks() + 2);
    }

    // ------------------------------------------------------------ 내 평타

    private static Weapon meleeOf(LocalPlayer p) {
        if (!(p.getMainHandItem().getItem() instanceof RpgWeaponItem item)) return null;
        Weapon w = item.weapon();
        return Combat.isMeleeWeapon(w) ? w : null;
    }

    /**
     * Minecraft.startAttack 처음: false 면 공격을 막음 (구르는 중). 공격이 나갈 것 같으면 연타 단계를 정해 서버에 먼저 보냄
     * (공격 · 휘두르기 패킷보다 앞 — 서버의 참격 방향 · 회전 베기 판정이 화면과 같게)
     */
    public static boolean attackBegin(Minecraft mc) {
        inAttack = true;
        pendingStep = 0;
        LocalPlayer p = mc.player;
        if (p == null) return true;
        if (rolling()) return false;
        HitResult hr = mc.hitResult;
        if (hr == null || hr.getType() == HitResult.Type.BLOCK) return true;
        if (mc.gameMode == null || mc.gameMode.hasMissTime() || p.isHandsBusy() || p.isSpectator()) return true;
        Weapon w = meleeOf(p);
        if (w == null) return true;
        List<String> combo = Motions.combo(w.type());
        if (combo.isEmpty()) return true;
        int step = Motions.nextStep(lastType.equals(w.type()) ? lastStep : 0, lastSwing, ticks, combo.size());
        pendingStep = step;
        pendingCombo = combo;
        pendingType = w.type();
        if (ClientPlayNetworking.canSend(MotionNet.ReqPayload.TYPE)) ClientPlayNetworking.send(new MotionNet.ReqPayload("basic", step, 0));
        return true;
    }

    /** 휘두르기 (LivingEntity.swing — 내 사람 · startAttack 안에서만): 동작 시작 */
    public static void attackSwing(Entity e) {
        if (!inAttack || pendingStep <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e != mc.player) return;
        start(e.getId(), pendingCombo.get(pendingStep - 1), 0);
        lastStep = pendingStep;
        lastSwing = ticks;
        lastType = pendingType;
        pendingStep = 0;
    }

    public static void attackEnd() {
        inAttack = false;
        pendingStep = 0;
    }

    public static boolean inAttack() {
        return inAttack;
    }

    // ------------------------------------------------------------ 구르기

    public static boolean rolling() {
        return ticks - rollStart < Motions.rollTicks();
    }

    /** 남은 재사용 대기 (틱) */
    public static long rollCooldownLeft() {
        return Math.max(0, Motions.rollCooldown() - (ticks - rollStart));
    }

    static void tryRoll(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || !ClientPlayNetworking.canSend(MotionNet.ReqPayload.TYPE)) return;
        if (rollCooldownLeft() > 0) return;
        if (!p.onGround() || p.isPassenger() || p.isInWater() || p.isFallFlying() || p.isSpectator() || p.getAbilities().flying || p.isSleeping()) return;
        double fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        double left = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double dx = fx * fwd + lx * left;
        double dz = fz * fwd + lz * left;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) {
            dx = fx;
            dz = fz;
            len = 1;
        }
        rollX = dx / len;
        rollZ = dz / len;
        rollSpeeds = Motions.rollSpeeds();
        rollStart = ticks;
        float rollYaw = (float) Math.toDegrees(Math.atan2(-rollX, rollZ));
        start(p.getId(), "roll", rollYaw);
        ClientPlayNetworking.send(new MotionNet.ReqPayload("roll", 0, rollYaw));
    }

    /** 1인칭 구르기 화면: {낮춤 칸, 내려다봄 도} (구르지 않으면 null) */
    public static float[] cameraDip(float partial) {
        double t = (ticks + partial - (rollStart + 1)) / Math.max(1, Motions.rollTicks());
        if (t < 0 || t >= 1) return null;
        float s = (float) Math.sin(Math.PI * t);
        return new float[] {0.85F * s, 28F * s};
    }

    // ------------------------------------------------------------ 시험 (사진첩)

    /** 이 사람의 자세를 동작 motion 의 t 에 멈춤 (motion null = 풀기) */
    public static void freeze(int entity, String motion, float t) {
        frozenEntity = motion == null ? Integer.MIN_VALUE : entity;
        frozenMotion = motion;
        frozenT = t;
    }

    /** 시험: 구르기 재사용 대기를 지움 */
    public static void resetRoll() {
        rollStart = -1000;
        lastTap = -1000;
    }

    /** 시험: 마지막 평타 단계 */
    public static int lastStep() {
        return lastStep;
    }
}
