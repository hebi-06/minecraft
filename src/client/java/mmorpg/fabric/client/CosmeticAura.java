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
 * <li>embers (불씨): 발밑 고리 (반지름 0.3 ~ 0.65) 에서 빛나는 불꽃이 피어오르며 작아짐 + 작은 불꽃 그림 · 0.8초마다 발밑 고리</li>
 * <li>orbit (별빛): 빛 둘이 허리 높이에서 몸 둘레를 돎 (2초에 한 바퀴 · 위아래로 출렁 · 흰 심지) — 지나간 자리에 꼬리, 반짝임이 떠오름</li>
 * <li>frost (서리): 머리 위 (반지름 0.85) 에서 눈송이가 흔들리며 내림 + 1.2초마다 발밑 서리 고리 · 발밑 반짝임</li>
 * </ul>
 * 투명 · 관전 중이면 뿌리지 않음. 파티클 설정이 '최소' 면 뿌리지 않고, '줄임' 이면 반만 (orbit 의 도는 빛은 두 틱마다 — 꼬리를 길게).
 * 내 오라는 1인칭에서는 뿌리지 않음 (눈 바로 앞을 지나는 빛 · 눈송이가 시야를 가리지 않게 — 3인칭 (F5) · 장식 창 미리보기 · 다른 사람 화면에서는 보임).
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
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Avatar a : level.getEntitiesOfClass(Avatar.class, box, e -> !e.isSpectator() && !e.isRemoved() && !e.isInvisible())) {
            if (firstPerson && a == cam) continue;
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

    /** 빛나는 불꽃 (FxParticle ARROW_SPARK — 끌림 없이 dir 로 speed, 크기는 수명 동안 size → size × grow, 흐려지며 사라짐) */
    private static FxOptions spark(Vec3 dir, double speed, double size, double life, double grow, int color) {
        return new FxOptions(FxOptions.ARROW_SPARK, -1, dir, (float) speed, (float) size, 0F, (float) life, 0F, (float) grow, 0F, 1F, color);
    }

    static void emit(ClientLevel level, RandomSource r, Avatar a, CosmeticDefs.Aura aura, int tick, float scale) {
        Vec3 feet = a.position();
        double h = a.getBbHeight();
        List<double[]> cols = aura.colors();
        int n = count(tick, aura.rate() * scale);
        switch (aura.kind()) {
            case "orbit" -> {
                // 빛 둘이 몸 둘레를 돎 (그 자리에 큰 빛 + 흰 심지 — 꼬리처럼 남음): 틱마다, 파티클 '줄임' 이면 두 틱마다 (꼬리를 길게)
                boolean decreased = scale < 1F;
                if (!decreased || tick % 2 == 0) {
                    double t = tick / 20.0;
                    for (int k = 0; k < 2; k++) {
                        double ang = t * Math.PI + k * Math.PI;
                        double y = h * 0.5 + Math.sin(t * 2.4 + k * Math.PI) * 0.3;
                        Vec3 at = feet.add(Math.cos(ang) * 0.75, y, Math.sin(ang) * 0.75);
                        double[] c = cols.get(k % cols.size());
                        add(level, spark(Vec3.ZERO, 0, 0.26, decreased ? 0.9 : 0.7, 0.1, argb(c, 1)), at);
                        if (tick % (decreased ? 4 : 2) == 0) add(level, spark(Vec3.ZERO, 0, 0.12, 0.35, 0.3, argb(new double[] {1, 1, 1}, 1)), at);
                    }
                }
                // 반짝임: 몸 둘레에서 천천히 떠오름 (초당 rate 개)
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.4 + r.nextDouble() * 0.5;
                    Vec3 at = feet.add(Math.cos(ang) * rad, r.nextDouble() * h * 1.1, Math.sin(ang) * rad);
                    add(level, spark(new Vec3(0, 1, 0), 0.15 + r.nextDouble() * 0.15, 0.09 + r.nextDouble() * 0.05, 0.9, 0.3, argb(pick(cols, r), 1)), at);
                }
            }
            case "frost" -> {
                // 눈송이: 머리 위에서 천천히 흔들리며 내림
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.2 + r.nextDouble() * 0.65;
                    Vec3 at = feet.add(Math.cos(ang) * rad, h + 0.3 + r.nextDouble() * 0.5, Math.sin(ang) * rad);
                    Vec3 drift = new Vec3((r.nextDouble() - 0.5) * 0.5, -1, (r.nextDouble() - 0.5) * 0.5).normalize();
                    add(level, spark(drift, 0.4 + r.nextDouble() * 0.3, 0.09 + r.nextDouble() * 0.06, 1.7 + r.nextDouble() * 0.6, 0.5,
                            argb(pick(cols, r), 1)), at);
                }
                // 발밑 서리: 고리 (1.2초마다) · 반짝임
                if (tick % 24 == 0) {
                    add(level, FxOptions.ofSprite(FxSprite.SHOCK_RING, Vec3.ZERO, 0, 0.5, 1.2, r.nextFloat() * 360, argb(cols.get(0), 0.7)), feet.add(0, 0.06, 0));
                }
                if (tick % 3 == 0) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    Vec3 at = feet.add(Math.cos(ang) * 0.55, 0.08, Math.sin(ang) * 0.55);
                    add(level, spark(new Vec3(0, 1, 0), 0.25, 0.12, 0.6, 0.2, argb(pick(cols, r), 1)), at);
                }
            }
            default -> { // embers
                // 불씨: 발밑 고리에서 빛나는 불꽃이 피어오르며 작아짐
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.3 + r.nextDouble() * 0.35;
                    Vec3 at = feet.add(Math.cos(ang) * rad, 0.05 + r.nextDouble() * 0.35, Math.sin(ang) * rad);
                    Vec3 dir = new Vec3(Math.cos(ang) * 0.12, 1, Math.sin(ang) * 0.12).normalize();
                    add(level, spark(dir, 0.5 + r.nextDouble() * 0.45, 0.12 + r.nextDouble() * 0.08, 0.9 + r.nextDouble() * 0.6, 0.45,
                            argb(pick(cols, r), 1)), at);
                }
                // 작은 불꽃 (바닐라 불꽃 그림) · 발밑 고리 (0.8초마다)
                if (r.nextFloat() < 0.6F * scale) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    Vec3 at = feet.add(Math.cos(ang) * 0.4, 0.05, Math.sin(ang) * 0.4);
                    add(level, FxOptions.glint(1, new Vec3(0, 1, 0), 0.3, 0.8, 1.2, 0.09 + r.nextDouble() * 0.04, 0.7, argb(cols.get(0), 1)), at);
                }
                if (tick % 16 == 0) {
                    add(level, FxOptions.ofSprite(FxSprite.SHOCK_RING, Vec3.ZERO, 0, 0.4, 0.9, r.nextFloat() * 360, argb(cols.get(0), 0.55)), feet.add(0, 0.05, 0));
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
