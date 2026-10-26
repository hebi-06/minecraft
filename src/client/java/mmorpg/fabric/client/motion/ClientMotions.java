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
 *   <li>내 평타: 공격 키 (Minecraft.startAttack 처음) 에 연타 단계를 정해 서버에 먼저 보내고 (참격 방향이 같게), 실제로 휘두를 때 (swing) 동작 시작 — 블록을 캘 때는 없음.
 *       0.39.0 공격 대기 대신 평타 사이 (MOTION.interval 틱): 앞 평타에서 그만큼 지나기 전에 누르면 기억해 두었다가 (선입력) 지나는 틱에 그때 누른 것처럼 나감 —
 *       매번 온전한 피해, 여러 번 눌러도 하나만</li>
 *   <li>다른 사람 · 스킬: 서버의 PlayPayload (0.41.0 평타면 평타 강화 모습도 — 내 평타는 서버가 미리 알린 LookPayload 의 모습)</li>
 *   <li>구르기: 쉬프트를 doubleTap 틱 안에 두 번 — 움직이는 쪽 (가만히면 앞) 으로 ticks 틱 동안 distance 칸 (틱마다 빠르기를 정함), 재사용 대기 cooldown 틱,
 *       땅 위 · 탈것 · 물 · 겉날개 · 비행 아닐 때만, 구르는 동안 공격 없음 (무적 없음 — 서버는 둘레에 모습만 알림)</li>
 * </ul>
 */
public final class ClientMotions {
    private ClientMotions() {}

    /** 하는 중인 동작 (start = 시작 시각 — 클라이언트 틱, look = 0.41.0 평타 강화 모습 — 없으면 NONE) */
    record Active(String motion, double start, float yaw, int ticks, MotionNet.Look look) {}

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
    /** 0.39.0 선입력: 평타 사이가 지나기 전에 누른 틱 (없으면 -1) */
    private static long buffered = -1;
    /** 0.41.0 내 평타 강화 모습 (서버 LookPayload — 바뀔 때마다) · 끝나는 클라이언트 틱 */
    private static MotionNet.Look selfLook = MotionNet.Look.NONE;
    private static long selfLookUntil = -1;

    // 구르기
    private static long rollStart = -1000;
    private static double rollX;
    private static double rollZ;
    private static double[] rollSpeeds = new double[0];
    private static long lastTap = -1000;

    // 시험 (사진첩): 사람마다 자세를 이 동작 · t 로 멈춤
    record Frozen(String motion, float t) {}

    private static final Map<Integer, Frozen> FROZEN = new HashMap<>();

    public static void clear() {
        ACTIVE.clear();
        inAttack = false;
        pendingStep = 0;
        lastStep = 0;
        lastSwing = -1000;
        buffered = -1;
        rollStart = -1000;
        lastTap = -1000;
        selfLook = MotionNet.Look.NONE;
        selfLookUntil = -1;
        FROZEN.clear();
    }

    public static long ticks() {
        return ticks;
    }

    /** 0.40.0 참격 궤적: 지금 하는 동작들 (사람 id → 동작 — 읽기만) */
    static Map<Integer, Active> activeMap() {
        return ACTIVE;
    }

    /** 0.40.0 참격 궤적: 자세를 멈춘 사람들 (사진첩 — id → 동작 · t, 읽기만) */
    static Map<Integer, Frozen> frozenMap() {
        return FROZEN;
    }

    // ------------------------------------------------------------ 시작 · 지금 자세

    /** 동작 시작 (지금 틱이 끝나는 때부터 — 렌더의 다음 프레임이 t = 0) */
    public static void start(int entity, String motion, float yaw) {
        start(entity, motion, yaw, MotionNet.Look.NONE);
    }

    /** 같은 것 — 0.41.0 평타 강화 모습과 함께 (궤적 색 · 길이) */
    public static void start(int entity, String motion, float yaw, MotionNet.Look look) {
        MotionPoses.Motion m = MotionPoses.get(motion);
        if (m == null) return;
        ACTIVE.put(entity, new Active(motion, ticks + 1, yaw, Math.max(1, m.ticks), look != null ? look : MotionNet.Look.NONE));
    }

    /** 서버가 알린 동작 */
    public static void receive(MotionNet.PlayPayload p) {
        start(p.entity(), p.motion(), p.yaw(), p.look());
    }

    /** 0.41.0 서버가 알린 내 평타 강화 모습 (NONE = 끝남) — ticks 동안 (끝나면 서버가 다시 알림 · 늦으면 여기서도 끝) */
    public static void receiveLook(MotionNet.LookPayload p) {
        MotionNet.Look l = p.look();
        if (l == null || l.none() || p.ticks() <= 0) {
            selfLook = MotionNet.Look.NONE;
            selfLookUntil = -1;
        } else {
            selfLook = l;
            selfLookUntil = ticks + p.ticks() + 2;
        }
    }

    /** 0.41.0 내가 지금 평타 motion 을 휘두르면 실을 모습 (궤적이 대신하는 평타만 — 없으면 NONE) */
    static MotionNet.Look selfLookFor(String motion) {
        if (selfLook.none() || ticks >= selfLookUntil || motion == null || !Motions.trailBasic(motion)) return MotionNet.Look.NONE;
        return selfLook;
    }

    /** 시험: 내 평타 강화 모습 (서버가 알린 것 — 끝났으면 NONE) */
    public static MotionNet.Look selfLook() {
        return ticks >= selfLookUntil ? MotionNet.Look.NONE : selfLook;
    }

    /** 시험: 이 사람이 지금 하는 동작에 실린 모습 (없으면 NONE) */
    public static MotionNet.Look lookOf(int entity) {
        Active a = ACTIVE.get(entity);
        return a == null || a.look() == null ? MotionNet.Look.NONE : a.look();
    }

    /** 이 사람이 지금 하는 동작 이름 (없으면 null) */
    public static String current(int entity) {
        Frozen f = FROZEN.get(entity);
        if (f != null) return f.motion();
        Active a = ACTIVE.get(entity);
        if (a == null) return null;
        return ticks + 1 - a.start() >= a.ticks() ? null : a.motion();
    }

    /** 이 사람의 지금 자세 (out 에 — 없으면 null). left = 주 손이 왼손 */
    public static Pose pose(int entity, boolean left, float partial, Pose out) {
        Frozen f = FROZEN.get(entity);
        if (f != null) {
            MotionPoses.Motion m = MotionPoses.get(f.motion());
            if (m == null) return null;
            MotionPoses.evaluate(m, f.t(), left, out);
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
        // 구르기 · 0.44.0 대장장이 망치질 (faceYaw — 모루 쪽): 몸이 서버가 알린 쪽을 봄 (NaN 이면 그대로)
        out.yaw = a.motion().equals("roll") || m.faceYaw ? a.yaw() : Float.NaN;
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
        fireBuffered(mc);
        rearm(p);
        ticks++;
        if (ticks % 40 == 0) ACTIVE.entrySet().removeIf(e -> ticks + 1 - e.getValue().start() > e.getValue().ticks() + 2);
    }

    // ------------------------------------------------------------ 0.46.0 룬 동작 뒤 무기를 다시 듦

    private static final Pose REARM = new Pose();
    /** 앞 틱에 1인칭 주 손 · 왼손이 무기 대신 맨손 · 소품이었는지 */
    private static boolean mainAway;
    private static boolean offAway;
    /** 시험: 다시 든 횟수 (주 손) */
    private static int rearmed;

    /**
     * 룬 동작이 맨손 (주먹 · 던진 뒤 빈손) · 소품 (연막탄 · 망치 …) 으로 쓰던 손을 무기에 돌려줄 때 (동작이 끝남 · 소품 구간이 끝남) 바닐라 바꿔 들기처럼
     * 무기가 화면 아래에서 올라오게 (FirstPersonHandsAndItems.itemUsed) — 갑자기 손에 나타나지 않게
     */
    static void rearm(LocalPlayer p) {
        Pose lp = pose(p.getId(), p.getMainArm() == HumanoidArm.LEFT, 0F, REARM);
        boolean main = lp != null && (lp.mainBare() || lp.propNow());
        boolean off = lp != null && lp.offMode != MotionPoses.WEAPON;
        if (mainAway && !main) {
            p.firstPersonHandsAndItems().itemUsed(net.minecraft.world.InteractionHand.MAIN_HAND);
            rearmed++;
        }
        if (offAway && !off) p.firstPersonHandsAndItems().itemUsed(net.minecraft.world.InteractionHand.OFF_HAND);
        mainAway = main;
        offAway = off;
    }

    /** 시험: 룬 동작 뒤 주 손 무기를 다시 든 횟수 */
    public static int rearmed() {
        return rearmed;
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
        if (mc.gameMode == null || p.isHandsBusy() || p.isSpectator()) return true;
        Weapon w = meleeOf(p);
        if (w == null) return true;
        List<String> combo = Motions.combo(w.type());
        if (combo.isEmpty()) return true;
        // 0.39.0 평타 사이 (공격 대기 대신): 앞 평타 동작이 끝나기 전 (또는 허공을 친 뒤 바닐라 대기) 에 누르면 선입력 — 지나는 틱에 fireBuffered 가 나감
        if (ticks - lastSwing < Motions.attackInterval() || mc.missTime > 0) {
            buffered = ticks;
            return false;
        }
        buffered = -1;
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
        String motion = pendingCombo.get(pendingStep - 1);
        start(e.getId(), motion, 0, selfLookFor(motion)); // 0.41.0 평타 강화 모습 (서버가 미리 알린 것)
        lastStep = pendingStep;
        lastSwing = ticks;
        lastType = pendingType;
        pendingStep = 0;
    }

    public static void attackEnd() {
        inAttack = false;
        pendingStep = 0;
    }

    /**
     * 선입력 (틱 끝): 평타 사이가 지났으면 미뤄 둔 공격 키를 그때 누른 것처럼 (Minecraft.startAttack — 지금 조준한 몹 · 허공).
     * 한 평타 사이보다 오래된 것 · 창이 열림 · 근접 무기를 내려놓음 · 블록을 조준함이면 버림
     */
    static void fireBuffered(Minecraft mc) {
        if (buffered < 0) return;
        LocalPlayer p = mc.player;
        int gap = Motions.attackInterval();
        if (p == null || mc.gui.screen() != null || ticks - buffered > gap || meleeOf(p) == null || rolling()) {
            buffered = -1;
            return;
        }
        if (ticks - lastSwing < gap || mc.missTime > 0) return;
        buffered = -1;
        HitResult hr = mc.hitResult;
        if (hr == null || hr.getType() == HitResult.Type.BLOCK) return;
        ((mmorpg.fabric.client.mixin.MinecraftInvoker) mc).mmorpg$startAttack();
    }

    /** 시험: 선입력이 있는지 */
    public static boolean hasBuffered() {
        return buffered >= 0;
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
        if (motion == null) FROZEN.remove(entity);
        else FROZEN.put(entity, new Frozen(motion, t));
    }

    /** 멈춘 자세를 모두 풀기 */
    public static void unfreezeAll() {
        FROZEN.clear();
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
