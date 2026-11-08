package mmorpg.fabric.vanilla;

import mmorpg.fabric.cosmetic.CosmeticDefs;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * 모드 없는 클라이언트의 오라 · 날개 가루 (2026-11-08 꾸미기 — VanillaCosmetics 가 틱마다 부름): 모드 CosmeticAura 의 모양을 바닐라 파티클로
 * (색 먼지 dust · 불꽃 · 끝막대 · 눈송이 · 벚꽃잎 · 전기 불꽃 · 하트 · 연기). 초당 수 (rate) 는 모드와 같음.
 * count 0 이면 (dx, dy, dz) 가 움직임 방향 · speed 가 빠르기 (바닐라 sendParticles).
 */
final class VanillaAuras {
    private VanillaAuras() {}

    /** 이번 틱에 뿌릴 수 (초당 rate 개 — 20틱에 고르게, 모드와 같음) */
    static int count(int tick, double rate) {
        return (int) Math.floor((tick + 1) * rate / 20.0) - (int) Math.floor(tick * rate / 20.0);
    }

    static int rgb(double[] c) {
        return (to255(c[0]) << 16) | (to255(c[1]) << 8) | to255(c[2]);
    }

    private static int to255(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    private static DustParticleOptions dust(List<double[]> cols, RandomSource r, float scale) {
        return new DustParticleOptions(rgb(cols.get(r.nextInt(cols.size()))), scale);
    }

    private static DustParticleOptions dust(double[] c, float scale) {
        return new DustParticleOptions(rgb(c), scale);
    }

    static void aura(VanillaCosmetics.Emit emit, LivingEntity e, CosmeticDefs.Aura a, int tick, RandomSource r) {
        double x = e.getX(), y = e.getY(), z = e.getZ();
        double h = e.getBbHeight();
        int n = count(tick, a.rate());
        List<double[]> cols = a.colors();
        switch (a.kind()) {
            case "embers" -> {
                // 발밑 고리에서 피어오르는 불씨 + 0.8초마다 발밑 불꽃 고리
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, rad = 0.3 + r.nextDouble() * 0.35;
                    double px = x + Math.cos(ang) * rad, pz = z + Math.sin(ang) * rad, py = y + 0.05 + r.nextDouble() * 0.25;
                    emit.send(dust(cols, r, 0.9F), px, py, pz, 0, 0, 1, 0, 0.05 + r.nextDouble() * 0.05);
                    if (r.nextFloat() < 0.3F) emit.send(ParticleTypes.SMALL_FLAME, px, py, pz, 0, 0, 1, 0, 0.02 + r.nextDouble() * 0.02);
                }
                if (tick % 16 == 0) ring(emit, ParticleTypes.SMALL_FLAME, x, y + 0.06, z, 0.6, 14);
            }
            case "orbit" -> {
                // 허리 높이에서 몸 둘레를 도는 빛 둘 (2초에 한 바퀴 · 위아래 출렁) — 남는 빛이 꼬리, 가끔 떠오르는 반짝임
                for (int k = 0; k < 2; k++) {
                    double ang = tick / 40.0 * Math.PI * 2 + k * Math.PI;
                    double py = y + h * 0.55 + Math.sin(tick * 0.15 + k * 2) * 0.18;
                    double px = x + Math.cos(ang) * 0.75, pz = z + Math.sin(ang) * 0.75;
                    // 끝막대 (2 ~ 3초 남음) 는 한 바퀴 내내 남아 흰 고리처럼 뭉침 (CI 42455311391 사진) → 금방 사라지는 먼지로 (흰 심지 + 색)
                    emit.send(new DustParticleOptions(0xFFFFFF, 0.7F), px, py, pz, 0, 0, 0, 0, 0);
                    emit.send(dust(cols.get(k % cols.size()), 1.1F), px, py, pz, 1, 0.02, 0.02, 0.02, 0);
                }
                for (int i = 0; i < Math.max(0, n - 2) / 3; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    emit.send(dust(cols, r, 0.6F), x + Math.cos(ang) * 0.6, y + h * (0.3 + r.nextDouble() * 0.5), z + Math.sin(ang) * 0.6, 0, 0, 1, 0, 0.03);
                }
            }
            case "frost" -> {
                // 머리 위에서 흔들리며 내리는 눈송이 + 1.2초마다 발밑 서리 고리 · 반짝임
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, rad = Math.sqrt(r.nextDouble()) * 0.85;
                    emit.send(ParticleTypes.SNOWFLAKE, x + Math.cos(ang) * rad, y + h + 0.25 + r.nextDouble() * 0.3, z + Math.sin(ang) * rad, 0, 0, -1, 0, 0.02);
                }
                if (tick % 24 == 0) {
                    ring(emit, dust(cols.get(0), 1.0F), x, y + 0.05, z, 0.7, 18);
                    for (int i = 0; i < 4; i++) {
                        double ang = r.nextDouble() * Math.PI * 2;
                        emit.send(ParticleTypes.END_ROD, x + Math.cos(ang) * 0.5, y + 0.1, z + Math.sin(ang) * 0.5, 0, 0, 1, 0, 0.01);
                    }
                }
            }
            case "petals" -> {
                // 머리 위 (반지름 0.2 ~ 1.1) 에서 하늘하늘 떨어지는 벚꽃잎
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, rad = 0.2 + r.nextDouble() * 0.9;
                    emit.send(ParticleTypes.CHERRY_LEAVES, x + Math.cos(ang) * rad, y + h + 0.2 + r.nextDouble() * 0.4, z + Math.sin(ang) * rad, 1, 0, 0, 0, 0);
                }
            }
            case "lightning" -> {
                // 몸 둘레 지그재그 번개 (4 ~ 5 마디 — 위나 아래로) · 0.5초마다 몸 둘레 옅은 번쩍임
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, rad = 0.45 + r.nextDouble() * 0.3;
                    double py = y + 0.2 + r.nextDouble() * (h - 0.4);
                    double dir = r.nextBoolean() ? 1 : -1;
                    int segs = 4 + r.nextInt(2);
                    DustParticleOptions glow = dust(cols, r, 0.8F);
                    for (int s = 0; s <= segs; s++) {
                        double a2 = ang + (s % 2 == 0 ? 0.18 : -0.18);
                        double qx = x + Math.cos(a2) * rad, qz = z + Math.sin(a2) * rad, qy = py + dir * s * 0.12;
                        emit.send(ParticleTypes.ELECTRIC_SPARK, qx, qy, qz, 0, 0, 0, 0, 0);
                        emit.send(glow, qx, qy, qz, 1, 0.02, 0.02, 0.02, 0);
                    }
                }
                if (tick % 10 == 0) ring(emit, dust(cols.get(cols.size() - 1), 0.7F), x, y + h * 0.5, z, 0.65, 10);
            }
            case "hearts" -> {
                // 몸 둘레에서 튀어나오듯 생겨 피어오르는 하트
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, rad = 0.5 + r.nextDouble() * 0.3;
                    emit.send(ParticleTypes.HEART, x + Math.cos(ang) * rad, y + 0.4 + r.nextDouble() * (h - 0.3), z + Math.sin(ang) * rad, 1, 0, 0, 0, 0);
                    emit.send(dust(cols, r, 0.7F), x + Math.cos(ang) * rad, y + 0.4 + r.nextDouble() * (h - 0.3), z + Math.sin(ang) * rad, 0, 0, 1, 0, 0.02);
                }
            }
            case "shadow" -> {
                // 발밑 둘레에서 피어오르는 어두운 연기 · 보랏빛 불티 (마지막 색) · 1.5초마다 발밑 어두운 고리
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, rad = 0.3 + r.nextDouble() * 0.4;
                    double px = x + Math.cos(ang) * rad, pz = z + Math.sin(ang) * rad;
                    emit.send(dust(cols.get(r.nextInt(Math.max(1, cols.size() - 1))), 1.6F), px, y + 0.1, pz, 0, 0, 1, 0, 0.03 + r.nextDouble() * 0.03);
                    if (r.nextFloat() < 0.25F) emit.send(dust(cols.get(cols.size() - 1), 0.7F), px, y + 0.2 + r.nextDouble() * 0.6, pz, 0, 0, 1, 0, 0.05);
                    if (r.nextFloat() < 0.15F) emit.send(ParticleTypes.SMOKE, px, y + 0.1, pz, 0, 0, 1, 0, 0.02);
                }
                if (tick % 30 == 0) ring(emit, dust(cols.get(0), 1.3F), x, y + 0.05, z, 0.75, 18);
            }
            default -> {
            }
        }
    }

    /** 등 장식의 가루 (요정 · 얼음 날개 = 떨어지는 반짝임, 불사조 = 피어오르는 불티): 등 뒤 날개 자리에서 — 움직이면 더 */
    static void dust(VanillaCosmetics.Emit emit, LivingEntity e, CosmeticDefs.Dust d, int tick, RandomSource r) {
        boolean moving = e.getDeltaMovement().horizontalDistanceSqr() > 0.002;
        int n = count(tick, d.rate() * (moving ? 1.5 : 1));
        double yaw = Math.toRadians(e.yBodyRot);
        double bx = Math.sin(yaw), bz = -Math.cos(yaw);     // 등 쪽
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);    // 오른쪽
        boolean ember = "ember".equals(d.style());
        for (int i = 0; i < n; i++) {
            double side = (r.nextDouble() * 2 - 1) * 0.9, up = 0.7 + r.nextDouble() * 0.9, back = 0.3 + r.nextDouble() * 0.15;
            double px = e.getX() + bx * back + rx * side, py = e.getY() + up * e.getBbHeight() / 1.8, pz = e.getZ() + bz * back + rz * side;
            if (ember) {
                emit.send(dust(d.colors(), r, 0.8F), px, py, pz, 0, 0, 1, 0, 0.04);
                if (r.nextFloat() < 0.3F) emit.send(ParticleTypes.SMALL_FLAME, px, py, pz, 0, 0, 1, 0, 0.02);
            } else {
                emit.send(dust(d.colors(), r, 0.6F), px, py, pz, 0, 0, -1, 0, 0.02);
                if (r.nextFloat() < 0.12F) emit.send(ParticleTypes.END_ROD, px, py, pz, 0, 0, -1, 0, 0.01);
            }
        }
    }

    private static void ring(VanillaCosmetics.Emit emit, ParticleOptions type, double x, double y, double z, double rad, int pts) {
        for (int k = 0; k < pts; k++) {
            double ang = k * Math.PI * 2 / pts;
            emit.send(type, x + Math.cos(ang) * rad, y, z + Math.sin(ang) * rad, 0, 0, 0, 0, 0);
        }
    }
}
