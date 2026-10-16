package mmorpg.fabric.client;

import java.util.List;
import mmorpg.fabric.FxOptions;
import mmorpg.fabric.FxSprite;
import mmorpg.fabric.cosmetic.CosmeticDefs;
import mmorpg.fabric.cosmetic.Look;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 오라 (장식 — 0.13.0): 오라를 입은 사람 (보이는 범위 48칸) 마다 각 클라이언트가 몸 둘레에 파티클을 뿌림 (패킷 없음 — 모습 표의 aura 칸).
 * <ul>
 * <li>embers (불씨): 발밑 고리 (반지름 0.35 ~ 0.6) 에서 불씨 빛 알갱이가 위로 가속하며 피어오름 + 가끔 큰 불꽃</li>
 * <li>orbit (별빛): 빛 둘이 허리 높이에서 몸 둘레를 돎 (2초에 한 바퀴 · 위아래로 출렁) — 지나간 자리에 꼬리, 가끔 반짝임이 떠오름</li>
 * <li>frost (서리): 머리 위 (반지름 0.7) 에서 눈송이가 천천히 내림 + 1.5초마다 발밑에 서리 고리</li>
 * </ul>
 * 투명 · 관전 중이면 뿌리지 않음. 파티클 설정이 '최소' 면 뿌리지 않고, '줄임' 이면 반만.
 */
public final class CosmeticAura {
    private CosmeticAura() {}

    private static final double RANGE = 48;

    /** 시험용: 지금까지 뿌린 수 */
    public static int spawned;

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || !ClientCosmetics.any()) return;
        ParticleStatus ps = mc.options.particles().get();
        if (ps == ParticleStatus.MINIMAL) return;
        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        AABB box = cam.getBoundingBox().inflate(RANGE);
        RandomSource r = level.getRandom();
        for (Avatar a : level.getEntitiesOfClass(Avatar.class, box, e -> !e.isSpectator() && !e.isRemoved() && !e.isInvisible())) {
            Look look = ClientCosmetics.lookOf(a.getUUID());
            CosmeticDefs.Def d = look.def("aura");
            if (d == null || d.aura() == null) continue;
            float scale = ps == ParticleStatus.DECREASED ? 0.5F : 1F;
            emit(level, r, a, d.aura(), a.tickCount, scale);
        }
    }

    /** 이번 틱에 뿌릴 수 (초당 rate 개 — 20틱에 고르게) */
    static int count(int tick, double rate) {
        return (int) Math.floor((tick + 1) * rate / 20.0) - (int) Math.floor(tick * rate / 20.0);
    }

    private static int argb(double[] c, double a) {
        return FxOptions.argb(c[0], c[1], c[2], a);
    }

    private static double[] pick(List<double[]> cols, RandomSource r) {
        return cols.get(r.nextInt(cols.size()));
    }

    static void emit(ClientLevel level, RandomSource r, Avatar a, CosmeticDefs.Aura aura, int tick, float scale) {
        Vec3 feet = a.position();
        double h = a.getBbHeight();
        List<double[]> cols = aura.colors();
        int n = count(tick, aura.rate() * scale);
        switch (aura.kind()) {
            case "orbit" -> {
                // 빛 둘이 몸 둘레를 돎 (틱마다 그 자리에 꼬리 알갱이)
                double t = tick / 20.0;
                for (int k = 0; k < 2; k++) {
                    double ang = t * Math.PI + k * Math.PI;
                    double y = h * 0.52 + Math.sin(t * 2.4 + k * Math.PI) * 0.28;
                    Vec3 at = feet.add(Math.cos(ang) * 0.72, y, Math.sin(ang) * 0.72);
                    double[] c = cols.get(k % cols.size());
                    add(level, new FxOptions(FxOptions.ARROW_SPARK, -1, Vec3.ZERO, 0F, 0.14F, 0F, 0.55F, 0F, 0.2F, 0F, 1F, argb(c, 0.95)), at);
                }
                for (int i = 0; i < n / 4; i++) {
                    Vec3 at = feet.add((r.nextDouble() - 0.5) * 1.2, r.nextDouble() * h, (r.nextDouble() - 0.5) * 1.2);
                    add(level, FxOptions.glint(0, new Vec3(0, 1, 0), 0.15, 0.2, 0.8, 0.06 + r.nextDouble() * 0.03, 1.1, argb(pick(cols, r), 1)), at);
                }
            }
            case "frost" -> {
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.25 + r.nextDouble() * 0.5;
                    Vec3 at = feet.add(Math.cos(ang) * rad, h + 0.35 + r.nextDouble() * 0.4, Math.sin(ang) * rad);
                    Vec3 drift = new Vec3((r.nextDouble() - 0.5) * 0.4, -1, (r.nextDouble() - 0.5) * 0.4).normalize();
                    add(level, FxOptions.glint(0, drift, 0.35, -0.15, 0.5, 0.05 + r.nextDouble() * 0.03, 1.6 + r.nextDouble() * 0.6,
                            argb(pick(cols, r), 1)), at);
                }
                if (tick % 30 == 0) {
                    add(level, FxOptions.ofSprite(FxSprite.SHOCK_RING, Vec3.ZERO, 0, 0.55, 1.1, r.nextFloat() * 360, argb(cols.get(0), 0.55)), feet.add(0, 0.05, 0));
                }
            }
            default -> { // embers
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.35 + r.nextDouble() * 0.25;
                    Vec3 at = feet.add(Math.cos(ang) * rad, 0.05 + r.nextDouble() * 0.25, Math.sin(ang) * rad);
                    Vec3 dir = new Vec3(Math.cos(ang) * 0.15, 1, Math.sin(ang) * 0.15).normalize();
                    int tex = r.nextInt(4) == 0 ? 1 : 0; // 넷 중 하나는 불꽃 그림
                    add(level, FxOptions.glint(tex, dir, 0.25 + r.nextDouble() * 0.25, 0.9, 1.4, 0.05 + r.nextDouble() * 0.04, 0.9 + r.nextDouble() * 0.6,
                            argb(pick(cols, r), 1)), at);
                }
                if (tick % 12 == 0) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    Vec3 at = feet.add(Math.cos(ang) * 0.45, 0.1, Math.sin(ang) * 0.45);
                    add(level, new FxOptions(FxOptions.ARROW_SPARK, -1, new Vec3(0, 1, 0), 0.5F, 0.16F, 0F, 0.7F, 0F, 0F, 0F, 1F, argb(cols.get(0), 0.8)), at);
                }
            }
        }
    }

    private static void add(ClientLevel level, FxOptions o, Vec3 at) {
        level.addParticle(o, at.x, at.y, at.z, 0, 0, 0);
        spawned++;
    }

    /** 시험 · 미리보기용: 오라 종류 이름 */
    public static String label(String kind) {
        return switch (kind) {
            case "orbit" -> "몸 둘레를 도는 빛";
            case "frost" -> "내리는 눈송이 · 서리 고리";
            default -> "피어오르는 불씨";
        };
    }
}
