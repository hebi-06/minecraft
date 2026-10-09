package mmorpg.fabric;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** 이펙트 그림 파티클 종류 (mmorpg:fx — 그림은 assets/mmorpg/particles/fx.json · textures/particle/fx) */
public final class RpgParticles {
    private RpgParticles() {}

    /** 늘 보임 (파티클 설정 · 거리 제한 무시 — 전투 연출이라 빠지면 안 됨) */
    public static final ParticleType<FxOptions> FX = FabricParticleTypes.complex(true, FxOptions.CODEC, FxOptions.STREAM_CODEC);

    static void init() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, Mmorpg.id("fx"), FX);
    }
}
