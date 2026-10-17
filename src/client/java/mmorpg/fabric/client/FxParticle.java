package mmorpg.fabric.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.fabric.FxOptions;
import mmorpg.fabric.FxSprite;
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
 * - 일반 그림 (FxSprite — 참격 · X · 방패 고리 · 폭발: 카메라를 향함, 충격파: 땅에 눕힘): 여러 장이면 수명 동안 차례로, 끌림으로 느려짐
 * - 무기 빛 알갱이 (GLINT — 애드온 weapon_fx): 바닐라 파티클 그림 (generic_0 · flame), 위로 가속 · 끌림, 줄어들며 사라짐
 *   (twinkle — 0.14.2 요정 날개 가루: 네 갈래 반짝임, 커졌다 작아지며 돎 · petal — 0.15.0 벚꽃잎: 빙글빙글 · 좌우로 흔들리며 떨어짐 ·
 *   heart — 0.15.0 하트: 튀어나오듯 커졌다 흐려짐)
 * 모두 밝기 최대 (빛), 반투명 섞기. 그림은 이름으로 찾음 (assets/mmorpg/particles/fx.json).
 */
public final class FxParticle extends SingleQuadParticle {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static List<TextureAtlasSprite> cachedList;
    private static Map<String, TextureAtlasSprite> cachedMap = Map.of();

    private final FxOptions o;
    private final FxSprite kind;
    private final Map<String, TextureAtlasSprite> sprites;
    private final TextureAtlasSprite fallback;
    private final Vec3 start;
    private final Vec3 dir;
    private final float r;
    private final float g;
    private final float b;
    private final float a;

    FxParticle(ClientLevel level, double x, double y, double z, FxOptions o, FabricSpriteSet set) {
        super(level, x, y, z, set.getSprites().get(0));
        this.o = o;
        this.kind = o.kind() == FxOptions.SPRITE ? FxSprite.byId(o.sprite()) : null;
        this.sprites = spriteMap(set);
        this.fallback = set.getSprites().get(0);
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
        float reach = Math.max(o.size(), o.size() + o.speed() * o.life()) * 2 * (kind != null ? kind.scale * (1 + kind.grow) : 1) + 0.5F;
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
        Vec3 p = posAt(Math.min(this.age / 20.0, o.life()));
        this.setPos(p.x, p.y, p.z);
    }

    /** t 초 뒤 자리 */
    private Vec3 posAt(double t) {
        if (o.kind() != FxOptions.GLINT) return start.add(dir.scale(travelled(t)));
        // 빛 알갱이: dv/dt = (0, accel, 0) - drag × v, 처음 v = dir × speed (애드온 particle_motion_dynamic)
        double d = o.drag();
        double along;
        double up;
        if (d > 1e-6) {
            double e = 1 - Math.exp(-d * t);
            along = o.speed() * e / d;
            up = o.accel() * (t / d - e / (d * d));
        } else {
            along = o.speed() * t;
            up = 0.5 * o.accel() * t * t;
        }
        Vec3 p = start.add(dir.scale(along)).add(0, up, 0);
        if (o.sprite() == FxOptions.PETAL) {
            // 꽃잎: 좌우로 하늘하늘 (처음 각으로 엇갈림)
            double ph = o.spin() * Mth.DEG_TO_RAD;
            p = p.add(Math.sin(t * 2.6 + ph) * 0.18, 0, Math.cos(t * 2.1 + ph) * 0.18);
        }
        return p;
    }

    /** t 초 동안 간 거리: 끌림이 있으면 처음 속도 / 끌림 × (1 - e^(-끌림 × t)) (애드온 linear_drag_coefficient) */
    private double travelled(double t) {
        float drag = kind != null ? kind.drag : 0F;
        return drag > 0 ? o.speed() / drag * (1 - Math.exp(-drag * t)) : o.speed() * t;
    }

    private static Map<String, TextureAtlasSprite> spriteMap(FabricSpriteSet set) {
        List<TextureAtlasSprite> list = set.getSprites();
        if (list != cachedList) {
            Map<String, TextureAtlasSprite> m = new HashMap<>();
            for (TextureAtlasSprite s : list) m.put(s.contents().name().getPath().replace("fx/", ""), s);
            cachedMap = m;
            cachedList = list;
        }
        return cachedMap;
    }

    private TextureAtlasSprite tex(String name) {
        return sprites.getOrDefault(name, fallback);
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
        // 자리 (시간으로 바로 — 틱 사이도 매끄럽게)
        Vec3 at = posAt(Math.min(sec, life));
        Vec3 cam = camera.position();
        float rx = (float) (at.x - cam.x);
        float ry = (float) (at.y - cam.y);
        float rz = (float) (at.z - cam.z);
        switch (o.kind()) {
            case FxOptions.ARROW_SHOT -> {
                float alpha = a * (1 - tn * tn * tn);
                directional(state, rx, ry, rz, o.size(), o.thick(), tex("arrow_streak"), alpha);
            }
            case FxOptions.ARROW_TRAIL -> {
                if (sec < o.delay()) return;
                float k = Mth.clamp((sec - o.delay()) / Math.max(0.01F, life - o.delay()), 0F, 1F);
                directional(state, rx, ry, rz, o.size(), o.thick() * (1 - 0.6F * k), tex("arrow_trail"), a * (1 - k));
            }
            case FxOptions.ARROW_SPARK -> {
                float size = o.size() * (1 + (o.grow() - 1) * tn);
                billboard(state, camera, rx, ry, rz, size, 0F, tex("arrow_spark"), a * (1 - tn));
            }
            case FxOptions.MAGIC_CIRCLE -> {
                int frame = Mth.clamp((int) Math.floor(o.progress() * 10 - 0.001), 0, 9);
                billboard(state, camera, rx, ry, rz, o.size(), o.spin(), tex("magic_circle_" + frame), a);
            }
            case FxOptions.MAGIC_FLASH -> {
                float size = o.size() * (1 + 0.35F * tn);
                billboard(state, camera, rx, ry, rz, size, o.spin(), tex("magic_circle_9"), a * (1 - tn * tn));
            }
            case FxOptions.SPRITE -> {
                if (kind == null) return;
                int frame = kind.frames > 1 ? Math.min(kind.frames - 1, (int) Math.floor(tn * kind.frames)) : 0;
                TextureAtlasSprite s = tex(kind.frames > 1 ? kind.texture + "_" + frame : kind.texture);
                float size = o.size() * kind.scale * (1 + kind.grow * tn);
                float alpha = kind.fade == FxSprite.Fade.SQUARE ? a * (1 - tn * tn) : a;
                if (kind.facing == FxSprite.Facing.FLAT) flat(state, rx, ry, rz, size, o.spin(), s, alpha);
                else billboard(state, camera, rx, ry, rz, size, o.spin(), s, alpha);
            }
            case FxOptions.GLINT -> {
                int t = Mth.clamp(o.sprite(), 0, FxOptions.GLINT_TEXTURES.length - 1);
                if (t == FxOptions.TWINKLE) {
                    // 반짝임: 커졌다 작아지며 (수명의 가운데에서 가장 큼) 천천히 돎
                    float k = Mth.sin(Mth.PI * tn);
                    billboard(state, camera, rx, ry, rz, o.size() * (0.2F + 0.8F * k), sec * 140F, tex(FxOptions.GLINT_TEXTURES[t]), a * Math.min(1F, 0.3F + k));
                } else if (t == FxOptions.PETAL) {
                    // 꽃잎: 크기 그대로 · 빙글빙글 (처음 각에 따라 시계 · 반시계) · 끝 25% 동안 흐려짐
                    float turn = ((int) o.spin()) % 2 == 0 ? 150F : -150F;
                    float fade = tn > 0.75F ? 1 - (tn - 0.75F) / 0.25F : 1F;
                    billboard(state, camera, rx, ry, rz, o.size(), o.spin() + sec * turn, tex(FxOptions.GLINT_TEXTURES[t]), a * fade);
                } else if (t == FxOptions.HEART) {
                    // 하트: 처음 1/6 동안 튀어나오듯 커짐 · 60% 부터 흐려짐
                    float pop = Math.min(1F, tn * 6F);
                    float fade = tn > 0.6F ? 1 - (tn - 0.6F) / 0.4F : 1F;
                    billboard(state, camera, rx, ry, rz, o.size() * (0.4F + 0.6F * pop), 0F, tex(FxOptions.GLINT_TEXTURES[t]), a * fade);
                } else {
                    billboard(state, camera, rx, ry, rz, o.size() * (1 - tn), 0F, tex(FxOptions.GLINT_TEXTURES[t]), a);
                }
            }
            default -> {}
        }
    }

    /** 땅에 눕힌 사각형 (emitter_transform_xz — 충격파 고리) + 세로축으로 돌린 각 */
    private void flat(QuadParticleRenderState state, float x, float y, float z, float size, float spinDeg, TextureAtlasSprite s, float alpha) {
        if (size <= 0 || alpha <= 0.003F) return;
        Quaternionf q = new Quaternionf().rotationY(spinDeg * Mth.DEG_TO_RAD).rotateX(-Mth.HALF_PI);
        state.add(getLayer(), x, y, z, q.x, q.y, q.z, q.w, size, s.getU0(), s.getU1(), s.getV0(), s.getV1(), ARGB.colorFromFloat(alpha, r, g, b), FULL_BRIGHT);
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
