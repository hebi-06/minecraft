package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.field.FieldBlocks;
import mmorpg.fabric.field.FieldWorld;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2026-11-07 섬 맵 게임 시험 (서버): 시험용 차원 mmorpg:field_test (gametest 의 data/mmorpg/dimension — 지상 세계와 같은 섬 맵 생성기) 에서
 * 청크를 실제로 만들어 core FieldTerrain 과 같은지 — 칸마다 땅 높이 · 겉흙 블록 (나무 · 눈 덮임은 허용) · 바다 물 · 바이옴 · 다리 갑판 · 기둥.
 * 통과하면 [field-test] OK 줄.
 */
public final class RpgFieldTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-field-test");
    static final ResourceKey<Level> FIELD = ResourceKey.create(Registries.DIMENSION, Mmorpg.id("field_test"));

    /** 땅 블록이 맞는지 (풀 → 나무 밑 흙 · 눈 덮인 풀도 같음) */
    private static boolean sameGround(BlockState got, BlockState want) {
        Block g = got.getBlock();
        Block w = want.getBlock();
        if (g == w) return true;
        if (w == Blocks.GRASS_BLOCK) return g == Blocks.DIRT || g == Blocks.PODZOL || g == Blocks.COARSE_DIRT || g == Blocks.MOSS_BLOCK;
        return false;
    }

    @GameTest(environment = "mmorpg:field", maxTicks = 60)
    public void terrainMatchesPlan(GameTestHelper h) {
        ServerLevel level = h.getLevel().getServer().getLevel(FIELD);
        h.assertTrue(level != null, "field_test dimension present");
        h.assertTrue(FieldWorld.isField(level), "field_test uses the mmorpg:field generator");
        FieldTerrain t = FieldWorld.terrain();
        FieldPlan plan = t.plan();
        List<int[]> probes = new ArrayList<>();
        probes.add(new int[] {plan.spawnX, plan.spawnZ});
        for (FieldPlan.Island is : plan.islands) {
            probes.add(new int[] {(int) is.x() + 37, (int) is.z() - 23});
            probes.add(new int[] {(int) (is.x() + is.radius() * 1.6), (int) is.z()}); // 바다
        }
        for (FieldPlan.Site s : plan.sites("camp")) probes.add(new int[] {(int) s.x() + 5, (int) s.z() + 5});
        FieldTerrain.Column c = new FieldTerrain.Column();
        int ok = 0;
        List<String> bad = new ArrayList<>();
        for (int[] p : probes) {
            level.getChunk(p[0] >> 4, p[1] >> 4);
            t.sample(p[0], p[1], c);
            BlockState got = level.getBlockState(new BlockPos(p[0], c.ground, p[1]));
            BlockState want = FieldBlocks.of(c.top, p[0], c.ground, p[1]);
            boolean ground = sameGround(got, want);
            boolean above = true;
            if (c.hasWater() && c.water > c.ground) {
                BlockState w = level.getBlockState(new BlockPos(p[0], c.water, p[1]));
                above = c.lava ? w.is(Blocks.LAVA) : w.is(Blocks.WATER) || w.is(Blocks.ICE) || w.is(Blocks.PACKED_ICE) || w.is(Blocks.BLUE_ICE) || w.is(Blocks.KELP)
                        || w.is(Blocks.KELP_PLANT) || w.is(Blocks.SEAGRASS) || w.is(Blocks.TALL_SEAGRASS);
            }
            // 바이옴 칸 (4 × 4) 그대로 (getBiome 은 이웃 칸과 섞어 고르므로 칸 값을 직접 봄)
            String biome = level.getNoiseBiome(p[0] >> 2, (c.ground + 1) >> 2, p[1] >> 2).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
            String wantBiome = t.biomeAt(((p[0] >> 2) << 2) + 2, ((p[1] >> 2) << 2) + 2);
            boolean bio = biome.equals(wantBiome);
            if (ground && above && bio) ok++;
            else bad.add(p[0] + " " + p[1] + ": ground " + got + " want " + want + " at " + c.ground + (above ? "" : " · no water/lava at " + c.water)
                    + (bio ? "" : " · biome " + biome + " want " + wantBiome));
        }
        h.assertTrue(bad.isEmpty(), "field columns = plan (" + ok + " ok): " + String.join(" / ", bad));
        // 다리: 가운데 갑판 · 기둥 (물 위)
        for (FieldTerrain.Span sp : t.spans()) {
            int mx = (int) Math.floor((sp.ax() + sp.bx()) / 2);
            int mz = (int) Math.floor((sp.az() + sp.bz()) / 2);
            level.getChunk(mx >> 4, mz >> 4);
            BlockState deck = level.getBlockState(new BlockPos(mx, sp.deck(), mz));
            h.assertTrue(deck.is(Blocks.SPRUCE_PLANKS) || deck.is(Blocks.STRIPPED_SPRUCE_LOG) || deck.is(Blocks.DARK_OAK_LOG),
                    "bridge " + sp.bridge().from() + "→" + sp.bridge().to() + " deck at " + mx + " " + sp.deck() + " " + mz + " = " + deck);
            BlockState under = level.getBlockState(new BlockPos(mx, sp.deck() - 1, mz));
            h.assertTrue(under.is(Blocks.SMOOTH_STONE) || under.is(Blocks.STONE_BRICKS), "bridge arch under the deck " + under);
        }
        LOG.info("[field-test] OK {} columns = plan · {} bridges", ok, t.spans().size());
        h.succeed();
    }
}
