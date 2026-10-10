package mmorpg.fabric;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * 이펙트 그림 파티클 종류: mmorpg:fx (활 · 완드 · 참격 — 그림은 assets/mmorpg/particles/fx.json · textures/particle/fx),
 * mmorpg:addon (애드온 파티클 119종 — 정의 assets/mmorpg/addon_particles.json · 그림 textures/particle/addon, AddonFx)
 */
public final class RpgParticles {
    private RpgParticles() {}

    /** 늘 보임 (파티클 설정 · 거리 제한 무시 — 전투 연출이라 빠지면 안 됨) */
    public static final ParticleType<FxOptions> FX = FabricParticleTypes.complex(true, FxOptions.CODEC, FxOptions.STREAM_CODEC);
    public static final ParticleType<AddonParticleOptions> ADDON = FabricParticleTypes.complex(true, AddonParticleOptions.CODEC,
            AddonParticleOptions.STREAM_CODEC);

    static void init() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mmorpg.id("fx"), FX);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mmorpg.id("addon"), ADDON);
    }
}
