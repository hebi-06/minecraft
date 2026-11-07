package mmorpg.fabric.field;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * 섬 맵 바이옴 고르기 (2026-11-07): 칸마다 core FieldTerrain 이 정한 바이옴 (섬 · 지역 · 물가) — world_preset field.json 의
 * {"type": "mmorpg:field", "biomes": "#mmorpg:field"}. 바이옴 칸 (4 × 4) 의 가운데로 고르고, 같은 칸을 여러 높이에서 물어도 한 번만 셈.
 */
public final class FieldBiomeSource extends BiomeSource implements BiomeResolver {
    public static final MapCodec<FieldBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes)).apply(i, i.stable(FieldBiomeSource::new)));

    private final HolderSet<Biome> biomes;
    private final Map<String, Holder<Biome>> byId = new HashMap<>();
    private final Holder<Biome> fallback;

    /** 스레드마다 최근 바이옴 칸 64개 (열 → 답) — 같은 열을 높이마다 묻는 생성 순서에 맞춤 */
    private static final class Cache {
        final long[] keys = new long[64];
        final String[] ids = new String[64];

        Cache() {
            java.util.Arrays.fill(keys, Long.MIN_VALUE);
        }
    }

    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    public FieldBiomeSource(HolderSet<Biome> biomes) {
        this.biomes = biomes;
        Holder<Biome> first = null;
        for (Holder<Biome> h : biomes) {
            if (first == null) first = h;
            h.unwrapKey().ifPresent(k -> byId.put(k.identifier().toString(), h));
        }
        Holder<Biome> sea = byId.get("mmorpg:open_sea");
        this.fallback = sea != null ? sea : first;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return this;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz) {
        long key = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
        Cache c = CACHE.get();
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 58);
        String id;
        if (c.keys[slot] == key) id = c.ids[slot];
        else {
            id = FieldWorld.terrain().biomeAt((qx << 2) + 2, (qz << 2) + 2);
            c.keys[slot] = key;
            c.ids[slot] = id;
        }
        Holder<Biome> h = byId.get(id);
        return h != null ? h : fallback;
    }
}
