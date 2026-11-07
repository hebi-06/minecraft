package mmorpg.core.field;

import java.util.Random;

/**
 * 섬 맵 지형 잡음 (2026-11-07): 시드를 고정한 2D 펄린 잡음 · 겹친 잡음 (fbm) · 능선 잡음 (ridged).
 * 세계 시드와 상관없이 field.json 의 seed 로만 정해져, 어느 서버 · 어느 월드든 같은 지도가 됨. 만든 뒤에는 바뀌지 않아 여러 스레드가 함께 써도 됨.
 */
public final class FieldNoise {
    /** 기울기 16방향 (단위 벡터) — 8방향보다 줄무늬가 덜 보임 */
    private static final double[] GX = new double[16];
    private static final double[] GZ = new double[16];

    static {
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            GX[i] = Math.cos(a);
            GZ[i] = Math.sin(a);
        }
    }

    private final int[] perm = new int[512];

    public FieldNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double grad(int ix, int iz, double fx, double fz) {
        int h = perm[perm[ix & 255] + (iz & 255)] & 15;
        return GX[h] * fx + GZ[h] * fz;
    }

    /** 펄린 잡음 (대략 −1 ~ 1) */
    public double noise(double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double u = fade(fx);
        double v = fade(fz);
        double a = grad(ix, iz, fx, fz);
        double b = grad(ix + 1, iz, fx - 1, fz);
        double c = grad(ix, iz + 1, fx, fz - 1);
        double d = grad(ix + 1, iz + 1, fx - 1, fz - 1);
        double ab = a + u * (b - a);
        double cd = c + u * (d - c);
        return (ab + v * (cd - ab)) * 1.41;
    }

    /** 겹친 잡음: 옥타브마다 2배 촘촘 · 세기 절반 (대략 −1 ~ 1) */
    public double fbm(double x, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * f + i * 31.7, z * f - i * 17.3);
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return sum / norm;
    }

    /** 능선 잡음 (0 ~ 1, 능선 = 1): 산줄기 · 모래 언덕 */
    public double ridged(double x, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        double weight = 1;
        for (int i = 0; i < octaves; i++) {
            double n = 1 - Math.abs(noise(x * f + i * 13.1, z * f + i * 7.9));
            n *= n;
            n *= weight;
            weight = Math.max(0, Math.min(1, n * 1.6));
            sum += amp * n;
            norm += amp;
            amp *= 0.5;
            f *= 2.07;
        }
        return sum / norm;
    }

    /** 0 ~ 1 사이의 고르게 퍼진 수 (자리마다 고정 — 나무 · 바위 · 무늬 고르기) */
    public static double hash(long x, long z, long k) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + k * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }
}
