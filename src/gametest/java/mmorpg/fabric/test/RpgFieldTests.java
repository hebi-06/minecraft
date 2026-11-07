package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.field.FieldBlocks;
import mmorpg.fabric.field.FieldChunkGenerator;
import mmorpg.fabric.field.FieldPieces;
import mmorpg.fabric.field.FieldWorld;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2026-11-07 섬 맵 게임 시험 (서버): 월드 유형 mmorpg:field · 섬 바이옴이 등록됨, 지형 기둥 (생성기의 칸 블록) = core FieldTerrain,
 * 짓는 것 (다리 · 마을) 의 블록 글자가 모두 자바 블록 (모르는 이름 없음). 실제 세계 (청크 · 짓는 것 · 시작 자리) 는 사진첩 RpgFieldGallery 가 봄.
 * 통과하면 [field-test] OK 줄.
 */
public final class RpgFieldTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-field-test");

    @GameTest(environment = "mmorpg:field", maxTicks = 40)
    public void presetBiomesAndColumns(GameTestHelper h) {
        HolderLookup.Provider ra = h.getLevel().registryAccess();
        h.assertTrue(ra.lookupOrThrow(Registries.WORLD_PRESET).get(ResourceKey.create(Registries.WORLD_PRESET, Mmorpg.id("field"))).isPresent(),
                "world preset mmorpg:field registered");
        FieldTerrain t = FieldWorld.terrain();
        FieldPlan plan = t.plan();
        // 바이옴: 지도 곳곳의 바이옴 이름이 모두 등록됨
        Set<String> seen = new HashSet<>();
        FieldTerrain.Column c = new FieldTerrain.Column();
        for (int x = -2300; x <= 2000; x += 37) {
            for (int z = -3200; z <= 3000; z += 41) seen.add(t.sample(x, z, c).biome);
        }
        var biomes = ra.lookupOrThrow(Registries.BIOME);
        List<String> missing = new ArrayList<>();
        for (String id : seen) if (biomes.get(ResourceKey.create(Registries.BIOME, Identifier.parse(id))).isEmpty()) missing.add(id);
        h.assertTrue(missing.isEmpty() && seen.size() >= 15, "island biomes registered (" + seen.size() + " seen, missing " + missing + ")");
        // 겉흙 블록
        for (FieldTerrain.Surface s : FieldTerrain.Surface.values()) h.assertTrue(!FieldBlocks.of(s, 0, 70, 0).isAir(), "surface block " + s);
        // 지형 기둥 = 지형 (기반암 · 땅 · 물)
        int minY = FieldChunkGenerator.MIN_Y;
        int ok = 0;
        List<int[]> probes = new ArrayList<>();
        probes.add(new int[] {plan.spawnX, plan.spawnZ});
        for (FieldPlan.Island is : plan.islands) {
            probes.add(new int[] {(int) is.x() + 37, (int) is.z() - 23});
            probes.add(new int[] {(int) (is.x() + is.radius() * 1.6), (int) is.z()});
        }
        for (int[] p : probes) {
            BlockState[] col = FieldChunkGenerator.columnStates(p[0], p[1], minY, FieldChunkGenerator.HEIGHT);
            t.sample(p[0], p[1], c);
            h.assertTrue(col[0].is(Blocks.BEDROCK), "bedrock at the bottom " + p[0] + " " + p[1]);
            BlockState g = col[c.ground - minY];
            h.assertTrue(g == FieldBlocks.of(c.top, p[0], c.ground, p[1]), "ground at " + p[0] + " " + c.ground + " " + p[1] + " = " + g + " (want " + c.top + ")");
            if (c.hasWater() && c.water > c.ground) {
                BlockState w = col[c.water - minY];
                h.assertTrue(c.lava ? w.is(Blocks.LAVA) : w.is(Blocks.WATER), "fluid top at " + p[0] + " " + c.water + " " + p[1] + " = " + w);
            }
            BlockState above = col[Math.max(c.ground, c.hasWater() ? c.water : c.ground) + 1 - minY];
            h.assertTrue(above.isAir() || c.floatTop > c.floatBottom, "air above the column " + p[0] + " " + p[1] + " = " + above);
            ok++;
        }
        // 짓는 것의 블록 글자 → 자바 블록 (모르는 이름이면 공기로 바뀌므로 공기가 아닌지)
        Set<String> texts = new HashSet<>();
        for (FieldBuild.Piece p : FieldPieces.pieces()) {
            if (p instanceof Blueprint.Ops o) {
                for (Blueprint.Box b : o.boxes()) for (String part : b.block().split("\\|")) texts.add(part);
            } else {
                int x = (p.minX() + p.maxX()) / 2;
                for (int z = p.minZ(); z <= p.maxZ(); z += 3) {
                    t.sample(x, z, c);
                    p.column(x, z, c, (y, b) -> texts.add(b));
                }
                int z = (p.minZ() + p.maxZ()) / 2;
                for (int x2 = p.minX(); x2 <= p.maxX(); x2 += 3) {
                    t.sample(x2, z, c);
                    p.column(x2, z, c, (y, b) -> texts.add(b));
                }
            }
        }
        List<String> unknown = new ArrayList<>();
        for (String s : texts) if (!s.equals("minecraft:air") && FieldPieces.state(s).isAir()) unknown.add(s);
        h.assertTrue(unknown.isEmpty(), "every building block is a Java block (" + texts.size() + " kinds, unknown " + unknown + ")");
        LOG.info("[field-test] OK preset · {} biomes · {} columns · {} building block kinds", seen.size(), ok, texts.size());
        h.succeed();
    }
}
