package mmorpg.fabric.client;

import mmorpg.fabric.FxOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 무기 빛 알갱이 (애드온 영웅 · 초월 무기의 fx 파티클): 무기를 든 사람 (플레이어 · 마네킹) 마다 무기 모델의 fx 자리에서 초당 rate 개.
 * 애드온처럼 알갱이는 세계에 남아 떠오르고, 뿌리는 자리만 무기를 따라감. 각 클라이언트가 보이는 사람마다 직접 뿌림 (패킷 없음).
 * 자리 (WeaponFxData — tools/weapon_models.py 가 애드온 모델에서 계산):
 * - 내 1인칭: 카메라 공간 자리 → 눈 + 보는 방향으로 돌림. 손은 시야 70 으로 그려지므로 지금 시야에 맞춰 옆 · 위를 늘림
 * - 3인칭 (다른 사람 · 마네킹 · 내 3인칭): 오른팔 공간 자리 → 팔 자세 (HumanoidModel: 무기 든 팔 = 걷기 흔들림 절반 - π/10,
 *   숙이면 +0.4 · 어깨 3.2 아래, 숨쉬기 흔들림) → 팔 축 (-5, 2) → 몸 (-1.501 · 0.9375 · 뒤집기 · 몸 방향 · 크기)
 * 휘두르는 동안의 팔 움직임 · 1인칭 손 흔들림은 넣지 않음 (쉬는 자세 기준). 왼손잡이는 좌우를 뒤집음. 넓은 팔 기준 (얇은 팔은 0.5 픽셀 차이).
 * 파티클 설정이 '최소' 면 뿌리지 않음.
 */
public final class WeaponAura {
    private static final double RANGE = 48;
    /** 1인칭 손을 그리는 시야 (Camera.BASE_HUD_FOV) */
    private static final float HAND_FOV = 70F;
    private static final float MODEL_SCALE = 0.9375F;
    private static final float MODEL_DROP = -1.501F;

    /** 시험용: 지금까지 뿌린 수 · 마지막 자리 · 크기 배율 (사진첩에서 자리를 잘 보이게 — 평소 1) */
    public static int spawned;
    public static float testScale = 1F;
    public static Vec3 lastFirstPerson;
    public static Vec3 lastThirdPerson;

    private WeaponAura() {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        Entity cameraEntity = mc.getCameraEntity();
        if (cameraEntity == null) return;
        AABB box = cameraEntity.getBoundingBox().inflate(RANGE);
        for (Avatar a : level.getEntitiesOfClass(Avatar.class, box, e -> !e.isSpectator() && !e.isRemoved())) {
            WeaponFxData.Spec spec = specOf(a, a.getMainHandItem());
            if (spec == null) continue;
            int n = count(a.tickCount, spec.rate());
            if (n == 0) continue;
            boolean firstPerson = a == cameraEntity && mc.options.getCameraType().isFirstPerson();
            Vec3 at;
            if (firstPerson) {
                at = firstPerson(mc, a, spec);
                lastFirstPerson = at;
            } else {
                at = thirdPerson(a, spec);
                lastThirdPerson = at;
            }
            for (int i = 0; i < n; i++) spawn(level, level.getRandom(), at, spec);
        }
    }

    /** 이 사람이 든 무기의 빛 알갱이: 그 무기 종류의 스킨을 입었으면 스킨 디자인의 것 (없으면 빛 알갱이 없음 — 0.13.0 장식) */
    public static WeaponFxData.Spec specOf(Avatar a, ItemStack stack) {
        if (stack.isEmpty()) return null;
        String type = CosmeticRender.weaponType(stack);
        if (!type.isEmpty() && ClientCosmetics.any()) {
            String skin = ClientCosmetics.lookOf(a.getUUID()).weaponSkin(type);
            if (skin != null) return WeaponFxData.BY_SKIN.get(skin);
        }
        return specOf(stack);
    }

    public static WeaponFxData.Spec specOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "rpg".equals(id.getNamespace()) ? WeaponFxData.BY_ITEM.get(id.getPath()) : null;
    }

    /** 이번 틱에 뿌릴 수 (초당 rate 개 — 20틱에 고르게) */
    static int count(int tick, float rate) {
        return (int) Math.floor((tick + 1) * rate / 20F) - (int) Math.floor(tick * rate / 20F);
    }

    /** 내 1인칭: 카메라 공간 자리 → 세계 */
    static Vec3 firstPerson(Minecraft mc, Avatar a, WeaponFxData.Spec s) {
        float side = a.getMainArm() == HumanoidArm.LEFT ? -1F : 1F;
        float fov = mc.gameRenderer.mainCamera().getFov();
        float k = fov > 1F && fov < 179F ? (float) (Math.tan(Math.toRadians(fov) / 2) / Math.tan(Math.toRadians(HAND_FOV) / 2)) : 1F;
        Vector3f v = new Vector3f(side * s.fpX() * k, s.fpY() * k, s.fpZ());
        // Camera.setRotation 과 같은 회전 (카메라 공간 → 세계)
        v.rotate(new Quaternionf().rotationYXZ(Mth.PI - a.getYRot() * Mth.DEG_TO_RAD, -a.getXRot() * Mth.DEG_TO_RAD, 0F));
        Vec3 eye = a.getEyePosition();
        return eye.add(v.x, v.y, v.z);
    }

    /** 3인칭: 오른팔 공간 자리 → 세계 (AvatarRenderer · HumanoidModel · ItemInHandLayer 와 같은 순서) */
    static Vec3 thirdPerson(Avatar a, WeaponFxData.Spec s) {
        float age = a.tickCount;
        float walkPos = a.walkAnimation.position();
        float walkSpeed = Math.min(1F, a.walkAnimation.speed());
        // 팔 자세: 걷기 흔들림 → 무기 든 팔 (절반 - π/10) → 숙이기 → 숨쉬기 흔들림
        float xRot = Mth.cos(walkPos * 0.6662F + Mth.PI) * walkSpeed * 0.5F - Mth.PI / 10F;
        float armY = 2F;
        if (a.isCrouching()) {
            xRot += 0.4F;
            armY += 3.2F;
        }
        float zRot = Mth.cos(age * 0.09F) * 0.05F + 0.05F;
        xRot += Mth.sin(age * 0.067F) * 0.05F;
        Vector3f p = new Vector3f(s.armX(), s.armY(), s.armZ());
        p.rotate(new Quaternionf().rotationZYX(zRot, 0F, xRot));
        p.add(-5F / 16F, armY / 16F + MODEL_DROP, 0F);
        if (a.getMainArm() == HumanoidArm.LEFT) p.x = -p.x;
        float scale = MODEL_SCALE * a.getScale();
        p.mul(-scale, -scale, scale);
        p.rotateY((180F - a.yBodyRot) * Mth.DEG_TO_RAD);
        return a.position().add(p.x, p.y, p.z);
    }

    /** 알갱이 하나: 반지름 radius 공 안 아무 곳 (부피 고르게) 에서 바깥쪽으로 */
    private static void spawn(ClientLevel level, RandomSource r, Vec3 at, WeaponFxData.Spec s) {
        double x;
        double y;
        double z;
        do {
            x = r.nextDouble() * 2 - 1;
            y = r.nextDouble() * 2 - 1;
            z = r.nextDouble() * 2 - 1;
        } while (x * x + y * y + z * z > 1);
        Vec3 off = new Vec3(x, y, z).scale(s.radius());
        Vec3 dir = off.lengthSqr() > 1e-8 ? off.normalize() : new Vec3(0, 1, 0);
        int texture = textureIndex(s.texture());
        FxOptions o = FxOptions.glint(texture, dir, s.speed(), s.accel(), s.drag(), s.size() * testScale, s.life(), 0xFF000000 | color(s, r.nextFloat()));
        level.addParticle(o, at.x + off.x, at.y + off.y, at.z + off.z, 0, 0, 0);
        spawned++;
    }

    /** 그림 이름 → FxOptions.GLINT_TEXTURES 순번 */
    static int textureIndex(String name) {
        for (int i = 0; i < FxOptions.GLINT_TEXTURES.length; i++) if (FxOptions.GLINT_TEXTURES[i].equals(name)) return i;
        return 0;
    }

    /** 무작위 색: r 이 cuts[i] 보다 작으면 colors[i], 아니면 마지막 */
    static int color(WeaponFxData.Spec s, float r) {
        for (int i = 0; i < s.cuts().length; i++) if (r < s.cuts()[i]) return s.colors()[i];
        return s.colors()[s.colors().length - 1];
    }
}
