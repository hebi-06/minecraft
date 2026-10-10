package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.client.AddonParticles.Def;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.phys.Vec3;

/**
 * 계속 뿜는 애드온 파티클 발생기 (emitter_rate_steady): active_time 초 동안 초당 spawn_rate 개 (살아 있는 알갱이 max_particles 까지).
 * 보이지 않는 파티클 — 알갱이 (AddonParticle) 를 엔진에 넣기만 함. spawn_rate · max 는 발생기 나이 (emitter_age) 가 든 식이면 틱마다 다시 계산.
 */
final class AddonEmitter extends Particle {
    private final Def d;
    private final double[] ev;
    private final double active;
    private final TextureAtlasSprite sprite;
    private final Vec3 origin;
    private final List<Particle> live = new ArrayList<>();
    private double carry;

    AddonEmitter(ClientLevel level, Vec3 origin, Def d, double[] emitterVars, TextureAtlasSprite sprite) {
        super(level, origin.x, origin.y, origin.z);
        this.d = d;
        this.ev = emitterVars;
        this.sprite = sprite;
        this.origin = origin;
        this.active = Math.max(0, d.active.eval(ev));
        this.lifetime = (int) Math.ceil(active * 20) + 1;
        this.hasPhysics = false;
        this.gravity = 0;
    }

    @Override
    public void tick() {
        double t = this.age / 20.0;
        if (this.age++ >= this.lifetime || t > active) {
            this.remove();
            return;
        }
        ev[AddonParticles.EAGE] = t;
        live.removeIf(p -> !p.isAlive());
        double rate = Math.max(0, d.rate.eval(ev));
        int max = (int) Math.max(0, Math.round(d.max.eval(ev)));
        carry += rate / 20.0;
        int n = (int) Math.floor(carry);
        carry -= n;
        for (int i = 0; i < n && live.size() < max; i++) {
            AddonParticle p = new AddonParticle(this.level, origin, d, ev, t, sprite);
            live.add(p);
            Minecraft.getInstance().particleEngine.add(p);
        }
    }

    @Override
    public ParticleRenderType getGroup() {
        return ParticleRenderType.NO_RENDER;
    }
}
