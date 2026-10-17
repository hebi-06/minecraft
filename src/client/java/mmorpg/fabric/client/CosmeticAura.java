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
 * <li>petals (벚꽃 — 0.15.0): 머리 위 (반지름 0.2 ~ 1.1) 에서 꽃잎이 빙글빙글 · 좌우로 하늘하늘 떨어짐</li>
 * <li>lightning (번개 — 0.15.0): 초당 rate 번, 몸 둘레 (반지름 0.45 ~ 0.75) 에 짧은 꺾은선 번개 (3 ~ 4 마디 · 0.12 ~ 0.22초) + 끝에 흰 불티,
 * 0.5초마다 몸 둘레 옅은 번쩍임</li>
 * <li>hearts (하트 — 0.15.0): 몸 둘레에서 하트가 튀어나오듯 생겨 천천히 피어오르며 흐려짐</li>
 * <li>shadow (그림자 — 0.15.0): 발밑 둘레에서 어두운 연기가 피어오르며 커지고 흐려짐 · 보랏빛 불티 (마지막 색) · 1.5초마다 발밑 어두운 고리</li>
 * </ul>
 * 등 장식의 반짝이는 가루 (dust — 0.14.2 요정의 날개, {@link #emitDust}) 도 여기서 (오라와 같은 조건).
 * 투명 · 관전 중이면 뿌리지 않음. 파티클 설정이 '최소' 면 뿌리지 않고, '줄임' 이면 반만 (orbit 의 도는 빛은 두 틱마다 — 꼬리를 길게).
 * 내 오라는 1인칭에서는 뿌리지 않음 (눈 바로 앞을 지나는 빛 · 눈송이가 시야를 가리지 않게 — 3인칭 (F5) · 장식 창 미리보기 · 다른 사람 화면에서는 보임).
 */
public final class CosmeticAura {
    private CosmeticAura() {}

    private static final double RANGE = 48;

    /** 시험용: 지금까지 뿌린 수 (오라 · 가루) */
    public static int spawned;
    /** 시험용: 지금까지 뿌린 가루 수 (등 장식 dust) */
    public static int dustSpawned;

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
            float scale = ps == ParticleStatus.DECREASED ? 0.5F : 1F;
            CosmeticDefs.Def d = look.def("aura");
            if (d != null && d.aura() != null) emit(level, r, a, d.aura(), a.tickCount, scale);
            CosmeticDefs.Def back = look.def("back");
            if (back != null && back.dust() != null) emitDust(level, r, a, back.dust(), a.tickCount, scale);
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
            case "petals" -> {
                // 꽃잎: 머리 위에서 빙글빙글 · 하늘하늘 떨어짐 (처음 각 0 ~ 359 — 도는 쪽 · 흔들림이 엇갈림)
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.2 + r.nextDouble() * 0.9;
                    Vec3 at = feet.add(Math.cos(ang) * rad, h + 0.1 + r.nextDouble() * 0.7, Math.sin(ang) * rad);
                    Vec3 drift = new Vec3((r.nextDouble() - 0.5) * 0.7, -1, (r.nextDouble() - 0.5) * 0.7).normalize();
                    add(level, FxOptions.glint(FxOptions.PETAL, drift, 0.45 + r.nextDouble() * 0.25, 0, 0, 0.07 + r.nextDouble() * 0.04, 2.2 + r.nextDouble() * 0.8,
                            r.nextInt(360), argb(pick(cols, r), 1)), at);
                }
            }
            case "lightning" -> {
                // 번개: 몸 둘레에 짧은 꺾은선 (마디마다 빛줄기 — 제자리) + 끝에 흰 불티
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.45 + r.nextDouble() * 0.3;
                    Vec3 p = feet.add(Math.cos(ang) * rad, 0.2 + r.nextDouble() * Math.max(0.1, h - 0.1), Math.sin(ang) * rad);
                    int segs = 3 + r.nextInt(2);
                    int color = argb(pick(cols, r), 1);
                    float life = (float) (0.12 + r.nextDouble() * 0.1);
                    for (int k = 0; k < segs; k++) {
                        Vec3 step = new Vec3((r.nextDouble() - 0.5) * 0.5, (r.nextBoolean() ? 1 : -1) * (0.12 + r.nextDouble() * 0.15), (r.nextDouble() - 0.5) * 0.5);
                        Vec3 q = p.add(step);
                        add(level, new FxOptions(FxOptions.ARROW_SHOT, -1, step, 0F, (float) (step.length() / 2), 0.025F, life, 0F, 0F, 0F, 1F, color),
                                p.add(q).scale(0.5));
                        p = q;
                    }
                    add(level, spark(Vec3.ZERO, 0, 0.09, 0.22, 0.2, argb(new double[] {1, 1, 1}, 1)), p);
                }
                if (tick % 10 == 0) add(level, spark(Vec3.ZERO, 0, 0.55, 0.18, 1.3, argb(cols.get(0), 0.3)), feet.add(0, h * 0.55, 0));
            }
            case "hearts" -> {
                // 하트: 몸 둘레에서 튀어나오듯 생겨 천천히 피어오름
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.4 + r.nextDouble() * 0.45;
                    Vec3 at = feet.add(Math.cos(ang) * rad, 0.3 + r.nextDouble() * h * 0.9, Math.sin(ang) * rad);
                    Vec3 up = new Vec3((r.nextDouble() - 0.5) * 0.25, 1, (r.nextDouble() - 0.5) * 0.25).normalize();
                    add(level, FxOptions.glint(FxOptions.HEART, up, 0.35 + r.nextDouble() * 0.2, 0, 0.6, 0.1 + r.nextDouble() * 0.05, 1.3 + r.nextDouble() * 0.5,
                            argb(pick(cols, r), 1)), at);
                }
            }
            case "shadow" -> {
                // 어두운 연기: 발밑 둘레에서 피어오르며 커지고 흐려짐 (마지막 색은 불티 — 연기는 그 앞 색들)
                int dark = Math.max(1, cols.size() - 1);
                for (int i = 0; i < n; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double rad = 0.15 + r.nextDouble() * 0.5;
                    Vec3 at = feet.add(Math.cos(ang) * rad, 0.05 + r.nextDouble() * 0.5, Math.sin(ang) * rad);
                    Vec3 dir = new Vec3(Math.cos(ang) * 0.15, 1, Math.sin(ang) * 0.15).normalize();
                    add(level, spark(dir, 0.3 + r.nextDouble() * 0.3, 0.14 + r.nextDouble() * 0.1, 1.1 + r.nextDouble() * 0.6, 2.2,
                            argb(cols.get(r.nextInt(dark)), 0.75)), at);
                }
                if (r.nextFloat() < 0.5F * scale) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    Vec3 at = feet.add(Math.cos(ang) * 0.5, 0.1 + r.nextDouble() * h * 0.7, Math.sin(ang) * 0.5);
                    add(level, spark(new Vec3(0, 1, 0), 0.4, 0.06, 0.9, 0.3, argb(cols.get(cols.size() - 1), 1)), at);
                }
                if (tick % 30 == 0) {
                    add(level, FxOptions.ofSprite(FxSprite.SHOCK_RING, Vec3.ZERO, 0, 0.6, 1.4, r.nextFloat() * 360, argb(cols.get(0), 0.6)), feet.add(0, 0.05, 0));
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

    /**
     * 반짝이는 가루 (등 장식 dust — 요정의 날개): 날개 자리 (등 뒤 · 허리 ~ 머리 위 · 양옆으로 1블록 — 바깥일수록 뒤로) 에서 천천히 떨어짐.
     * 네 갈래 반짝임 (커졌다 작아지며 돎) 이 반쯤, 나머지는 작은 빛. 초당 rate 개, 움직이면 더 많이 (달리면 2.5배 — 지나간 자리에 남음).
     * style ember (불사조 날개 — 0.15.0) 는 같은 자리에서 불꽃 · 불티가 피어오름.
     */
    static void emitDust(ClientLevel level, RandomSource r, Avatar a, CosmeticDefs.Dust dust, int tick, float scale) {
        double move = Math.min(1, Math.hypot(a.getX() - a.xo, a.getZ() - a.zo) / 0.22); // 이번 틱에 움직인 거리 (남의 플레이어도 — 달리기 약 0.28)
        int n = count(tick, dust.rate() * scale * (1 + 1.5 * move));
        if (n <= 0) return;
        double yaw = Math.toRadians(a.yBodyRot);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double k = a.getBbHeight() / 1.8; // 웅크리면 낮게
        Vec3 feet = a.position();
        List<double[]> cols = dust.colors();
        for (int i = 0; i < n; i++) {
            double side = (r.nextBoolean() ? 1 : -1) * (0.1 + r.nextDouble() * 0.9);
            double up = (0.55 + r.nextDouble() * 1.65) * k;
            double back = 0.25 + Math.abs(side) * 0.4 + r.nextDouble() * 0.1;
            Vec3 at = feet.add(right.scale(side)).add(fwd.scale(-back)).add(0, up, 0);
            int color = argb(pick(cols, r), 1);
            if ("ember".equals(dust.style())) {
                // 불티 (불사조 날개): 바닐라 불꽃 그림이 위로 피어오르며 작아짐 · 작은 빛
                Vec3 rise = new Vec3((r.nextDouble() - 0.5) * 0.4, 1, (r.nextDouble() - 0.5) * 0.4).normalize();
                if (r.nextFloat() < 0.55F) {
                    add(level, FxOptions.glint(1, rise, 0.25 + r.nextDouble() * 0.3, 0.5, 1.2, 0.05 + r.nextDouble() * 0.04, 0.6 + r.nextDouble() * 0.5, color), at);
                } else {
                    add(level, spark(rise, 0.35 + r.nextDouble() * 0.35, 0.035 + r.nextDouble() * 0.03, 0.7 + r.nextDouble() * 0.5, 0.3, color), at);
                }
                dustSpawned++;
                continue;
            }
            Vec3 drift = new Vec3((r.nextDouble() - 0.5) * 0.6, -1, (r.nextDouble() - 0.5) * 0.6).normalize();
            if (r.nextFloat() < 0.45F) {
                add(level, FxOptions.glint(FxOptions.TWINKLE, drift, 0.15 + r.nextDouble() * 0.15, 0, 0.5, 0.06 + r.nextDouble() * 0.04,
                        0.6 + r.nextDouble() * 0.5, color), at);
            } else {
                add(level, spark(drift, 0.2 + r.nextDouble() * 0.2, 0.03 + r.nextDouble() * 0.025, 0.9 + r.nextDouble() * 0.7, 0.35, color), at);
            }
            dustSpawned++;
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
            case "petals" -> "흩날리는 벚꽃잎";
            case "lightning" -> "몸 둘레의 번개";
            case "hearts" -> "피어오르는 하트";
            case "shadow" -> "피어오르는 그림자 연기";
            default -> "피어오르는 불씨";
        };
    }
}
