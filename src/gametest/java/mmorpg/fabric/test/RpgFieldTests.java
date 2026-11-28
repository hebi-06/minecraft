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
import mmorpg.fabric.field.FieldExtras;
import mmorpg.fabric.field.FieldFog;
import mmorpg.fabric.field.FieldLifts;
import mmorpg.fabric.field.FieldShrines;
import mmorpg.fabric.field.FieldWaystones;
import mmorpg.fabric.field.FieldPieces;
import mmorpg.fabric.field.FieldWorld;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
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
        // 섬마다 둘레 (2026-11-28 섬이 멀어져 섬 둘레만 — 바다 한가운데는 열린 바다뿐)
        for (FieldPlan.Island is : plan.islands) {
            int R = (int) (is.radius() * 1.3);
            for (int x = (int) is.x() - R; x <= is.x() + R; x += 37) {
                for (int z = (int) is.z() - R; z <= is.z() + R; z += 41) seen.add(t.sample(x, z, c).biome);
            }
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
        // 미리 짓는 것: 보스 신전 (field.json boss 자리마다) · 세계수
        h.assertTrue(FieldExtras.altars().size() == plan.sites("boss").size(), "temples " + FieldExtras.altars().size() + "/" + plan.sites("boss").size());
        h.assertTrue(FieldExtras.treeBase() != null, "world tree base");
        LOG.info("[field-test] OK preset · {} biomes · {} columns · {} building block kinds · {} temples", seen.size(), ok, texts.size(), FieldExtras.altars().size());
        h.succeed();
    }

    /**
     * 2026-11-28 다리 없음 (사용자 "텔포로 이동시킬거여서 다리 없애고 그냥 섬을 멀리 둬도 됨"): 이동 지점 돌 열 (이야기 차례 · 무리마다 하나 이상 ·
     * 섬의 레벨에 열림) · 바다 끝 안개 (fog.push 밖이면 섬 쪽으로 밀고, fog.back 밖이면 되돌림).
     */
    @GameTest(maxTicks = 40)
    public void fieldStonesAndFog(GameTestHelper h) {
        FieldTerrain t = FieldWorld.terrain();
        FieldPlan plan = t.plan();
        List<FieldWaystones.Stone> stones = FieldWaystones.stones();
        List<String> ids = new ArrayList<>();
        for (FieldWaystones.Stone s : stones) ids.add(s.id());
        h.assertTrue(ids.equals(List.of("waystone_midgard", "waystone_farm", "waystone_desert", "waystone_sea", "waystone_thunder", "waystone_snow", "waystone_north",
                "waystone_shield", "waystone_muspel", "waystone_void", "waystone_rift", "waystone_sage")), "stones in story order " + ids);
        Set<String> groups = new HashSet<>();
        for (FieldWaystones.Stone s : stones) {
            FieldPlan.Island is = plan.island(s.island());
            h.assertTrue(is != null, "stone " + s.id() + " on an island");
            groups.add(is.group());
            int want = s.id().equals("waystone_midgard") ? 0 : is.levelMin();
            h.assertTrue(s.level() == want, "stone " + s.id() + " opens at " + s.level() + " (want " + want + ")");
        }
        for (FieldPlan.Island is : plan.islands) if (is.with() == null) h.assertTrue(groups.contains(is.id()), "a stone on " + is.id());
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        FieldWaystones.Stone farm = stones.get(1);
        FieldWaystones.Stone desert = stones.get(2);
        sp.experienceLevel = 1;
        h.assertTrue(FieldWaystones.open(sp, stones.get(0)) && FieldWaystones.open(sp, farm) && !FieldWaystones.open(sp, desert),
                "level 1: square · farm open, desert locked");
        sp.experienceLevel = desert.level();
        h.assertTrue(FieldWaystones.open(sp, desert), "level " + desert.level() + ": desert open");
        // 안개: 마을 섬 가운데에서 동쪽으로 — push 밖 첫 칸 · back 밖 첫 칸
        double xPush = Double.NaN;
        double xBack = Double.NaN;
        for (double x = 0; x < 2400; x += 2) {
            double off = t.offshore(x, 0);
            if (Double.isNaN(xPush) && off >= plan.fogPush + 5) xPush = x;
            if (off >= plan.fogBack + 5) {
                xBack = x;
                break;
            }
        }
        h.assertTrue(!Double.isNaN(xPush) && !Double.isNaN(xBack) && xBack > xPush, "fog lines east of the village " + xPush + " · " + xBack);
        sp.setPos(xPush, 70, 0);
        int r1 = FieldFog.check(sp, h.getLevel(), t);
        h.assertTrue(r1 == 1 && sp.getDeltaMovement().x < 0, "pushed back toward the island (" + r1 + ", vx " + sp.getDeltaMovement().x + ")");
        sp.setPos(xBack, 70, 0);
        int r2 = FieldFog.check(sp, h.getLevel(), t);
        h.assertTrue(r2 == 2 && Math.abs(sp.getX() - xBack) > 50, "returned to land (" + r2 + ", x " + sp.getX() + ")");
        sp.setPos(0, 80, 60);
        h.assertTrue(FieldFog.check(sp, h.getLevel(), t) == 0, "on the island: untouched");
        LOG.info("[field-test] OK stones {} · fog push at x {} · back at x {}", ids.size(), xPush, xBack);
        h.succeed();
    }

    /**
     * 2026-11-28 나라 섬 (사용자 "번개의 나라 … 추가해도 됨"): 큰 저울 오르내림 발판 한 쌍 (탑 아래 → 꼭대기 — 1초 서면 짝으로, 옮겨 온 발판에서는 내려섰다
     * 다시 서야 돌아감) · 벼락받이 사당의 벼락 자리 하나 (기둥 꼭대기 — 사당 마당보다 25칸 넘게 높음) · 나라 NPC 자리표 (17 ~ 22).
     */
    @GameTest(maxTicks = 40)
    public void nationLiftsAndShrine(GameTestHelper h) {
        FieldTerrain t = FieldWorld.terrain();
        List<FieldLifts.Pad> pads = FieldLifts.pads();
        h.assertTrue(pads.size() == 2 && pads.get(0).id().equals("lift_scale_a") && pads.get(0).pair().equals("lift_scale_b"), "lift pads " + pads);
        FieldLifts.Pad a = pads.get(0);
        FieldLifts.Pad b = pads.get(1);
        h.assertTrue(b.at().y - a.at().y > 40, "pad b on the top deck (" + a.at() + " → " + b.at() + ")");
        List<net.minecraft.world.phys.Vec3> bolts = FieldShrines.bolts();
        FieldPlan.Site shrine = t.plan().site("thunder_shrine");
        h.assertTrue(bolts.size() == 1 && shrine != null && bolts.get(0).y > t.surfaceAt((int) shrine.x(), (int) shrine.z()) + 25, "bolt mark on the pillar " + bolts);
        Set<String> npcs = new HashSet<>();
        for (Blueprint.Mark m : FieldBuild.build(t).marks()) if (m.id().startsWith("npc_")) npcs.add(m.id());
        for (int n = 17; n <= 22; n++) h.assertTrue(npcs.contains("npc_" + n), "nation npc mark npc_" + n);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        sp.setPos(a.at().x, a.at().y, a.at().z);
        h.assertTrue(FieldLifts.step(sp, h.getLevel(), 10) == null, "half a second on pad a: stays");
        String went = FieldLifts.step(sp, h.getLevel(), 10);
        h.assertTrue("lift_scale_b".equals(went) && sp.position().distanceTo(b.at()) < 1.0, "1 s on pad a → pad b (" + went + ", " + sp.position() + ")");
        h.assertTrue(FieldLifts.step(sp, h.getLevel(), 20) == null, "arrived on pad b: stays until stepping off");
        sp.setPos(b.at().x + 3, b.at().y, b.at().z);
        FieldLifts.step(sp, h.getLevel(), 2);
        sp.setPos(b.at().x, b.at().y, b.at().z);
        FieldLifts.step(sp, h.getLevel(), 10);
        String back = FieldLifts.step(sp, h.getLevel(), 10);
        h.assertTrue("lift_scale_a".equals(back) && sp.position().distanceTo(a.at()) < 1.0, "off and on again → back down (" + back + ")");
        LOG.info("[field-test] OK nation lifts {} · bolts {} · npcs {}", pads.size(), bolts.size(), npcs.size());
        h.succeed();
    }
}
