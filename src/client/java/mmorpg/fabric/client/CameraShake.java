package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 화면 흔들림 (애드온 camerashake add @s 세기 초 rotational): 서버가 RpgNet.ShakePayload 로 보내면 카메라 보는 방향을 잠깐 떨게 함.
 * 세기는 여러 번 오면 더해지고 (최대 4), 시간 동안 줄어듦. 세기 1 = 최대 약 DEGREES_PER_INTENSITY 도 (어림값 — 베드락은 수치를 밝히지 않음).
 * 시간은 게임 틱 (초 × 20, 멈춤 화면에서는 멈춤). 보는 방향 (조준) 은 바꾸지 않고 카메라만 돌림 — mixin.CameraMixin 이 Camera.alignWithEntity 뒤에 더함.
 */
public final class CameraShake {
    private static final float DEGREES_PER_INTENSITY = 10F;
    private static final float MAX_INTENSITY = 4F;
    private static final double TAU = Math.PI * 2;

    private record Shake(float intensity, long start, long end) {}

    private static final List<Shake> SHAKES = new ArrayList<>();
    private static long ticks;
    /** 시험용: 흔든 프레임 수 · 가장 크게 흔든 각 (도) */
    public static int frames;
    public static float maxDegrees;

    private CameraShake() {}

    /** 클라이언트 틱마다 (멈춤 화면이 아닐 때) */
    public static void tick() {
        ticks++;
    }

    public static void add(float intensity, float seconds) {
        if (intensity <= 0 || seconds <= 0) return;
        SHAKES.add(new Shake(Math.min(intensity, MAX_INTENSITY), ticks, ticks + Math.max(1, Math.round(seconds * 20))));
    }

    public static void clear() {
        SHAKES.clear();
    }

    /** 지금 더할 각 (도: [좌우, 위아래]) — 흔들림이 없으면 null */
    public static float[] offset(float partialTick) {
        if (SHAKES.isEmpty()) return null;
        double now = ticks + partialTick;
        float amp = 0;
        for (Iterator<Shake> it = SHAKES.iterator(); it.hasNext();) {
            Shake s = it.next();
            if (now >= s.end) {
                it.remove();
                continue;
            }
            amp += (float) (s.intensity * (s.end - now) / (s.end - s.start));
        }
        if (amp <= 0) return null;
        float deg = Math.min(amp, MAX_INTENSITY) * DEGREES_PER_INTENSITY;
        double t = now / 20.0;
        // 서로 다른 빠르기의 떨림 둘을 섞음 (규칙적으로 보이지 않게)
        float yaw = (float) (deg * (0.6 * Math.sin(t * TAU * 13 + 1.3) + 0.4 * Math.sin(t * TAU * 23 + 0.4)));
        float pitch = (float) (deg * (0.6 * Math.sin(t * TAU * 17 + 2.1) + 0.4 * Math.sin(t * TAU * 29 + 3.0)));
        frames++;
        maxDegrees = Math.max(maxDegrees, Math.max(Math.abs(yaw), Math.abs(pitch)));
        return new float[] {yaw, pitch};
    }
}
