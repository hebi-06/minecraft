package mmorpg.fabric.client.motion;

import org.joml.Quaternionf;

/**
 * 0.38.0 동작 한 순간의 자세 (MotionPoses.evaluate — 라디안 · 모델 좌표: 픽셀, +x 모델 왼쪽, +y 아래, +z 뒤).
 * 팔 회전 ra · la 는 뿌리 (사람이 보는 쪽) 기준 — 몸통 비틀기와 따로. 굽힘: 팔꿈치는 음수가 앞으로 굽힘, 무릎은 양수가 뒤로 굽힘 (로컬 x 축).
 */
public final class Pose {
    public static final int BODY = 1;
    public static final int RA = 2;
    public static final int LA = 4;
    public static final int LEGS = 8;
    public static final int HEAD = 16;
    public static final int ROOT = 32;

    public String motion = "";
    /** 0 ~ 1 */
    public float t;
    /** 바닐라 자세와 섞는 몫 (들어오고 나갈 때 0 → 1 → 0) */
    public float w;
    public int parts;
    /** 걷거나 뛰는 동안은 다리를 바닐라 걸음에 맡김 (평타) */
    public boolean softLegs;
    public float lean;
    public float twist;
    public float side;
    public float headX;
    public float headY;
    public final Quaternionf ra = new Quaternionf();
    public final Quaternionf la = new Quaternionf();
    public float raBend;
    public float laBend;
    /** 손목 (든 무기만 — 팔뚝 비틀기 뒤 로컬 x 축 회전, 라디안) */
    public float raWrist;
    public float laWrist;
    /** 팔뚝 비틀기 (든 무기만 — 팔꿈치 뒤 로컬 y 축 (팔뚝 가운데) 회전, 라디안) */
    public float raTwist;
    public float laTwist;
    public float rlX;
    public float rlY;
    public float rlZ;
    public float llX;
    public float llY;
    public float llZ;
    public float rk;
    public float lk;
    /** 뿌리: 위 · 앞 (칸) */
    public float up;
    public float fwd;
    /** 왼쪽으로 돎 · 앞구르기 (라디안 — 무게를 곱하지 않음: 열쇠가 0 · 360° 로 시작 · 끝남) */
    public float spin;
    public float flip;
    /** 든 무기를 숨김 (던지기 · 구르기) */
    public boolean hide;
    /** 1인칭 무기: 오른쪽 · 위 · 뒤 (칸) · x · y · z (라디안) */
    public float fpX;
    public float fpY;
    public float fpZ;
    public float fpRX;
    public float fpRY;
    public float fpRZ;
    /** 구르기 쪽 (도 — 마인크래프트 yaw) */
    public float yaw;

    public boolean has(int part) {
        return (parts & part) != 0;
    }

    /** 왼손잡이 (주 손 = 왼손): 좌우를 뒤집음 */
    void mirror() {
        Quaternionf r = new Quaternionf(ra);
        ra.set(-la.x, la.y, la.z, -la.w);
        la.set(-r.x, r.y, r.z, -r.w);
        // 위 두 줄: x → -x 거울 (모델 좌표) 은 사원수 (x, -y, -z, w) — 부호를 모두 바꾼 같은 회전으로 씀
        float b = raBend;
        raBend = laBend;
        laBend = b;
        float wr = raWrist;
        raWrist = laWrist;
        laWrist = wr;
        float tw = raTwist;
        raTwist = -laTwist;
        laTwist = -tw;
        float x = rlX;
        float y = rlY;
        float z = rlZ;
        rlX = llX;
        rlY = -llY;
        rlZ = -llZ;
        llX = x;
        llY = -y;
        llZ = -z;
        float k = rk;
        rk = lk;
        lk = k;
        twist = -twist;
        side = -side;
        headY = -headY;
        spin = -spin;
        fpX = -fpX;
        fpRY = -fpRY;
        fpRZ = -fpRZ;
        int p = parts;
        parts = p & ~(RA | LA);
        if ((p & RA) != 0) parts |= LA;
        if ((p & LA) != 0) parts |= RA;
    }
}
