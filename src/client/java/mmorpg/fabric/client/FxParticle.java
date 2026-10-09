package mmorpg.fabric.client;

import java.util.List;
import mmorpg.fabric.FxOptions;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 이펙트 그림 하나 (애드온 파티클과 같은 모양 · 움직임 · 색 — RPG_RP/particles/rpg/fx · magic):
 * - 빛줄기 · 지나간 자리 선 (lookat_direction): 길이 방향은 dir, 면은 카메라 쪽으로 (dir 축으로 돎). 정사각형 조각을 이어 길쭉한 그림
 * - 불꽃 · 마법진 · 번쩍임 (rotate_xyz): 카메라를 향한 사각형 + 돌린 각
 * 모두 밝기 최대 (빛), 반투명 섞기.
 */
public final class FxParticle extends SingleQuadParticle {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int TEX_STREAK = 0;
    private static final int TEX_TRAIL = 1;
    private static final int TEX_SPARK = 2;
    private static final int TEX_CIRCLE = 3; // 3 ~ 12 = 마법진 10단계

    private final FxOptions o;
    private final List<TextureAtlasSprite> sprites;
    private final Vec3 start;
    private final Vec3 dir;
    private final float r;
    private final float g;
    private final float b;
    private final float a;

    FxParticle(ClientLevel level, double x, double y, double z, FxOptions o, FabricSpriteSet set) {
        super(level, x, y, z, set.getSprites().get(0));
        this.o = o;
        this.sprites = set.getSprites();
        this.start = new Vec3(x, y, z);
        Vec3 d = o.dir();
        double len = d.length();
        this.dir = len > 1e-6 ? d.scale(1 / len) : new Vec3(0, 1, 0);
        this.lifetime = Math.max(1, (int) Math.ceil(o.life() * 20)) + 1;
        this.hasPhysics = false;
        this.gravity = 0;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        int c = o.color();
        this.a = ((c >>> 24) & 255) / 255F;
        this.r = ((c >> 16) & 255) / 255F;
        this.g = ((c >> 8) & 255) / 255F;
        this.b = (c & 255) / 255F;
        float reach = Math.max(o.size(), o.size() + o.speed() * o.life()) * 2 + 0.5F;
        this.setSize(reach, reach);
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        double t = Math.min(this.age / 20.0, o.life());
        Vec3 p = start.add(dir.scale(o.speed() * t));
        this.setPos(p.x, p.y, p.z);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        float life = Math.max(0.01F, o.life());
        float sec = (this.age + partialTick) / 20F;
        if (sec > life) return;
        float tn = Mth.clamp(sec / life, 0F, 1F);
        // 자리 (빛줄기는 시간으로 바로 — 틱 사이도 매끄럽게)
        double moved = o.speed() * Math.min(sec, life);
        Vec3 cam = camera.position();
        float rx = (float) (start.x + dir.x * moved - cam.x);
        float ry = (float) (start.y + dir.y * moved - cam.y);
        float rz = (float) (start.z + dir.z * moved - cam.z);
        switch (o.kind()) {
            case FxOptions.ARROW_SHOT -> {
                float alpha = a * (1 - tn * tn * tn);
                directional(state, rx, ry, rz, o.size(), o.thick(), sprites.get(TEX_STREAK), alpha);
            }
            case FxOptions.ARROW_TRAIL -> {
                if (sec < o.delay()) return;
                float k = Mth.clamp((sec - o.delay()) / Math.max(0.01F, life - o.delay()), 0F, 1F);
                directional(state, rx, ry, rz, o.size(), o.thick() * (1 - 0.6F * k), sprites.get(TEX_TRAIL), a * (1 - k));
            }
            case FxOptions.ARROW_SPARK -> {
                float size = o.size() * (1 + (o.grow() - 1) * tn);
                billboard(state, camera, rx, ry, rz, size, 0F, sprites.get(TEX_SPARK), a * (1 - tn));
            }
            case FxOptions.MAGIC_CIRCLE -> {
                int frame = Mth.clamp((int) Math.floor(o.progress() * 10 - 0.001), 0, 9);
                billboard(state, camera, rx, ry, rz, o.size(), o.spin(), sprites.get(TEX_CIRCLE + frame), a);
            }
            case FxOptions.MAGIC_FLASH -> {
                float size = o.size() * (1 + 0.35F * tn);
                billboard(state, camera, rx, ry, rz, size, o.spin(), sprites.get(TEX_CIRCLE + 9), a * (1 - tn * tn));
            }
            default -> {}
        }
    }

    /** 카메라를 향한 사각형 (rotate_xyz) + 돌린 각 (도) */
    private void billboard(QuadParticleRenderState state, Camera camera, float x, float y, float z, float size, float spinDeg, TextureAtlasSprite s,
            float alpha) {
        if (size <= 0 || alpha <= 0.003F) return;
        Quaternionf q = new Quaternionf(camera.rotation());
        if (spinDeg != 0) q.rotateZ(spinDeg * Mth.DEG_TO_RAD);
        state.add(getLayer(), x, y, z, q.x, q.y, q.z, q.w, size, s.getU0(), s.getU1(), s.getV0(), s.getV1(), ARGB.colorFromFloat(alpha, r, g, b), FULL_BRIGHT);
    }

    /**
     * 길이 방향이 dir 인 길쭉한 그림 (lookat_direction): 반 길이 half · 반 두께 thick.
     * 정사각형 조각 n 개 (n = 길이 / 두께) 를 dir 을 따라 잇고, 그림도 n 조각으로 나눠 붙임. 면은 카메라 쪽.
     */
    private void directional(QuadParticleRenderState state, float x, float y, float z, float half, float thick, TextureAtlasSprite s, float alpha) {
        if (half <= 0 || thick <= 0 || alpha <= 0.003F) return;
        Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        Vector3f toCam = new Vector3f(-x, -y, -z);
        Vector3f n = new Vector3f(toCam).sub(new Vector3f(d).mul(toCam.dot(d)));
        if (n.lengthSquared() < 1e-8F) {
            // 카메라가 빛줄기 축 위: 아무 수직 방향
            n.set(Math.abs(d.y) < 0.9F ? 0 : 1, Math.abs(d.y) < 0.9F ? 1 : 0, 0);
            n.sub(new Vector3f(d).mul(n.dot(d)));
        }
        n.normalize();
        Vector3f up = new Vector3f(n).cross(d).normalize(); // Z × X = Y
        Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(d, up, n));
        int count = Math.max(1, Math.round(half / thick));
        float h = half / count;
        int color = ARGB.colorFromFloat(alpha, r, g, b);
        float u0 = s.getU0();
        float du = (s.getU1() - u0) / count;
        for (int i = 0; i < count; i++) {
            float off = -half + h * (2 * i + 1);
            state.add(getLayer(), x + d.x * off, y + d.y * off, z + d.z * off, q.x, q.y, q.z, q.w, h, u0 + du * i, u0 + du * (i + 1), s.getV0(), s.getV1(),
                    color, FULL_BRIGHT);
        }
    }
}
