package mmorpg.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 애드온 파티클 하나 (mmorpg:addon): 애드온 파티클 번호 (AddonFxTable.PARTICLES) + 변수 (AddonFxTable.VARS 번호 → 값) — 베드락의
 * dimension.spawnParticle(id, 자리, MolangVariableMap) 과 같은 것. 클라이언트 (AddonParticles) 가 애드온 정의의 식으로 그림.
 *
 * @param particle 파티클 번호
 * @param slots    변수 번호
 * @param values   변수 값 (slots 와 같은 순서)
 */
public record AddonParticleOptions(int particle, int[] slots, float[] values) implements ParticleOptions {
    public static final MapCodec<AddonParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("particle").forGetter(AddonParticleOptions::particle),
            Codec.INT.listOf().optionalFieldOf("slots", List.of()).forGetter(o -> toList(o.slots)),
            Codec.FLOAT.listOf().optionalFieldOf("values", List.of()).forGetter(o -> toList(o.values))).apply(i, AddonParticleOptions::of));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddonParticleOptions> STREAM_CODEC = StreamCodec.ofMember(AddonParticleOptions::write,
            AddonParticleOptions::read);

    private static AddonParticleOptions of(int particle, List<Integer> slots, List<Float> values) {
        int n = Math.min(slots.size(), values.size());
        int[] s = new int[n];
        float[] v = new float[n];
        for (int i = 0; i < n; i++) {
            s[i] = slots.get(i);
            v[i] = values.get(i);
        }
        return new AddonParticleOptions(particle, s, v);
    }

    private static List<Integer> toList(int[] a) {
        List<Integer> l = new ArrayList<>(a.length);
        for (int x : a) l.add(x);
        return l;
    }

    private static List<Float> toList(float[] a) {
        List<Float> l = new ArrayList<>(a.length);
        for (float x : a) l.add(x);
        return l;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(particle);
        buf.writeVarInt(slots.length);
        for (int i = 0; i < slots.length; i++) {
            buf.writeVarInt(slots[i]);
            buf.writeFloat(values[i]);
        }
    }

    private static AddonParticleOptions read(RegistryFriendlyByteBuf buf) {
        int particle = buf.readVarInt();
        int n = Math.min(64, buf.readVarInt());
        int[] s = new int[n];
        float[] v = new float[n];
        for (int i = 0; i < n; i++) {
            s[i] = buf.readVarInt();
            v[i] = buf.readFloat();
        }
        return new AddonParticleOptions(particle, s, v);
    }

    @Override
    public ParticleType<AddonParticleOptions> getType() {
        return RpgParticles.ADDON;
    }
}
