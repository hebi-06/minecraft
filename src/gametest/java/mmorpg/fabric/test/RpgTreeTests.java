package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.tree.TreeBlocks;
import mmorpg.fabric.tree.TreeLog;
import mmorpg.fabric.tree.TreePlan;
import mmorpg.fabric.tree.WorldTree;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 세계수 (worldtree.js): 설계가 애드온과 같음 (높이 · 씨앗 → 칸 수 · 지문이 애드온 JS 결과와 같은 값, 같은 씨앗 = 같은 설계),
 * 나무 블록 → 자바 블록 (떨어지지 않는 잎 · 열매 달린 덩굴 · 다 자란 덩굴 끝), 블록 가리기 (자연 · 부드러운 · 땅 · 그대로 두는 블록), 칸 번호 글 (줄임 · 되읽기 · 망가진 글 거절),
 * 기록 저장 · 다시 읽기 (30000자씩 나눔, 큰 팔레트), 바로 거절 (세계 높이 · 보스 필드 · 물 속 · 네더 · 불러오지 않은 곳), 살핌 (건물 · 용암 · 막힌 하늘 — 여러 틱),
 * 작은 나무 (minHeight) 심기 → 확인 창 → 다 자람 (설계대로 · 소 · 두 칸 꽃 · 양귀비) → 기억을 지우고 (다시 불러온 것처럼) 되돌리기 → 원래 블록.
 * 큰 블록 시험은 시험 세계 (지형이 있는 세계) 의 먼 좌표 · 하늘 높이에 땅을 깔고 함. 통과하면 [tree-test] OK 줄.
 */
public final class RpgTreeTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-tree-test");

    // ------------------------------------------------------------ 도우미

    /** 그 자리 둘레 half 칸 네모를 붙잡고 지금 바로 불러옴 */
    private static String loadArea(ServerLevel level, BlockPos center, int half) {
        String hold = Ticking.holdArea(level, Vec3.atCenterOf(center), half, "t");
        for (int cx = Math.floorDiv(center.getX() - half, 16); cx <= Math.floorDiv(center.getX() + half, 16); cx++) {
            for (int cz = Math.floorDiv(center.getZ() - half, 16); cz <= Math.floorDiv(center.getZ() + half, 16); cz++) level.getChunk(cx, cz);
        }
        return hold;
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** 평평한 땅: 밑동 둘레 half 칸 네모에 풀 한 층 (밑동 바로 아래) + 흙 다섯 층, 그 위 나무 칸은 공기 */
    private static void flatGround(ServerLevel level, BlockPos base, int half, TreePlan plan) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = -6; dy <= 3; dy++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    level.setBlock(m, dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState() : dy < -1 ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
        for (TreePlan.Op o : plan.allOps()) if (o.dy() > 3) level.setBlock(base.offset(o.dx(), o.dy(), o.dz()), Blocks.AIR.defaultBlockState(), FLAGS);
    }

    /** 나무 자리의 지금 블록 (설계 칸, 깔개 · 덤불은 그 둘레 기둥) */
    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos base, TreePlan plan) {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        for (TreePlan.Op o : plan.allOps()) {
            boolean ground = o.mode() == TreePlan.Mode.CARPET || o.mode() == TreePlan.Mode.BUSH;
            for (int dy = -5; dy <= 2; dy++) {
                if (!ground && dy != 0) continue;
                BlockPos p = base.offset(o.dx(), o.dy() + dy, o.dz());
                m.put(p, level.getBlockState(p));
            }
        }
        return m;
    }

    /**
     * 처음과 다른 칸 — 나무 칸 (cells) 이 아닌 곳에서 풀이 흙이 된 것은 셈하지 않음 (나무 밑 그늘: 바닐라 풀은 빛이 없으면 무작위 틱에 흙이 됨 — 나무가 바꾼 칸이 아님)
     */
    private static List<String> diff(ServerLevel level, Map<BlockPos, BlockState> before, java.util.Set<BlockPos> cells) {
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
            BlockState now = level.getBlockState(e.getKey());
            if (now != e.getValue() && !(cells != null && !cells.contains(e.getKey()) && e.getValue().getBlock() == Blocks.GRASS_BLOCK && now.getBlock() == Blocks.DIRT)) {
                n++;
                if (bad.size() < 5) bad.add(e.getKey().toShortString() + " " + e.getValue() + " → " + now);
            }
        }
        if (n > 5) bad.add("… " + n + " cells");
        return bad;
    }

    private static boolean has(TreePlan plan, int dx, int dy, int dz) {
        for (TreePlan.Op o : plan.allOps()) {
            if (o.mode() != TreePlan.Mode.CARPET && o.mode() != TreePlan.Mode.BUSH && o.dx() == dx && o.dy() == dy && o.dz() == dz) return true;
        }
        return false;
    }

    private static boolean groundCol(TreePlan plan, int dx, int dz) {
        for (TreePlan.Op o : plan.allOps()) if ((o.mode() == TreePlan.Mode.CARPET || o.mode() == TreePlan.Mode.BUSH) && o.dx() == dx && o.dz() == dz) return true;
        return false;
    }

    private static Arena.Altar altarAt(ServerLevel level, String key, BlockPos pos) {
        level.setBlock(pos, AltarBlocks.block(key).defaultBlockState(), Block.UPDATE_ALL);
        return Arena.addAltar(key, NpcData.dimId(level), pos);
    }

    private static void forgetAltar(ServerLevel level, Arena.Altar a) {
        if (a == null) return;
        Consumer<Arena.Altar> hook = Arena.altarRemovedHook();
        Arena.setAltarRemovedHook(null);
        try {
            Arena.removeAltar(a);
        } finally {
            Arena.setAltarRemovedHook(hook);
        }
        if (level.isLoaded(a.pos())) level.setBlock(a.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static String plain(String s) {
        return BedrockText.plain(s).replaceAll("§.", "");
    }

    private static BlockState st(Block b) {
        return b.defaultBlockState();
    }

    // ------------------------------------------------------------ 설계

    /** 설계: 애드온 JS (worldtree.js treePlan) 와 같은 칸 수 · 지문 (로컬 대조 도구로 칸마다 · 차례마다 같음을 확인한 값), 같은 씨앗 = 같은 설계 */
    @GameTest(maxTicks = 20)
    public void planMatchesAddon(GameTestHelper h) {
        TreePlan a = TreePlan.plan(64, 12345);
        TreePlan b = TreePlan.plan(64, 12345);
        h.assertTrue(a.stages.equals(b.stages) && a.fp == b.fp, "same seed → same plan");
        h.assertTrue(a.fp == 3625873582L && a.total == 16367 && a.reach == 32 && a.rootReach == 23 && a.canopy == 32 && a.top == 61 && a.bottom == -3 && a.woodCount == 3657,
                "addon plan (64, 12345): fp " + a.fp + " total " + a.total + " reach " + a.reach + " root " + a.rootReach + " canopy " + a.canopy + " top " + a.top);
        h.assertTrue(TreePlan.fingerprint(a.stages) == a.fp, "fingerprint again");
        h.assertTrue(a.counts.get(TreePlan.LEAF) == 9162 && a.counts.get(TreePlan.BLOOM) == 2913 && a.counts.get(TreePlan.FRUIT) == 151 && a.counts.get(TreePlan.VINE_TIP) == 53
                && a.counts.get(TreePlan.BLOSSOM) == 7 && a.counts.get(TreePlan.CARPET) == 228, "addon block counts " + a.counts);
        TreePlan c = TreePlan.plan(64, 777);
        h.assertTrue(c.fp == 2493440601L && c.total == 16680, "addon plan (64, 777): " + c.fp + " " + c.total);
        TreePlan s = TreePlan.plan(40, 5);
        TreePlan l = TreePlan.plan(96, 5);
        h.assertTrue(s.fp == 3078257565L && s.total == 4438 && l.fp == 1054093330L && l.total == 54253, "addon plans (40, 5) · (96, 5): " + s.fp + " " + s.total + " / " + l.fp + " " + l.total);
        h.assertTrue(TreePlan.plan(10, 5).H == 40 && TreePlan.plan(500, 5).H == 96, "heights kept within 40~96");
        h.assertTrue(TreePlan.seedOf(-40, -64, -40) == 1344764044 && TreePlan.seedOf(38, 300, 28) == 1099368473, "seedOf = addon");
        h.assertTrue(TreePlan.rootReachOf(40) == 16 && TreePlan.rootReachOf(64) == 24 && TreePlan.rootReachOf(96) == 36 && TreePlan.rootReachOf(110) == 36, "rootReachOf = addon");
        // 단계 차례: 뿌리 → 줄기 → 가지 → 잎 → 빛 → 이끼
        List<String> order = List.of("root", "trunk", "branch", "leaf", "glow", "ground");
        int prev = 0;
        for (TreePlan.Stage st : a.stages) {
            int k = order.indexOf(st.kind());
            h.assertTrue(k >= prev, "stage order " + st.kind());
            prev = k;
        }
        // 나무 블록 → 자바
        for (String id : a.counts.keySet()) h.assertTrue(TreeBlocks.state(id) != null, "java block for " + id);
        BlockState leaf = TreeBlocks.state(TreePlan.LEAF);
        BlockState bloom = TreeBlocks.state(TreePlan.BLOOM);
        h.assertTrue(leaf.getBlock() == Blocks.AZALEA_LEAVES && leaf.getValue(BlockStateProperties.PERSISTENT) && bloom.getBlock() == Blocks.FLOWERING_AZALEA_LEAVES
                && bloom.getValue(BlockStateProperties.PERSISTENT), "persistent azalea leaves " + leaf + " " + bloom);
        BlockState vine = TreeBlocks.state(TreePlan.VINE);
        BlockState tip = TreeBlocks.state(TreePlan.VINE_TIP);
        h.assertTrue(vine.getBlock() == Blocks.CAVE_VINES_PLANT && vine.getValue(BlockStateProperties.BERRIES) && tip.getBlock() == Blocks.CAVE_VINES
                && tip.getValue(BlockStateProperties.BERRIES) && tip.getValue(BlockStateProperties.AGE_25) == 25, "glow berry vines " + vine + " " + tip);
        h.assertTrue(TreeBlocks.state(TreePlan.WOOD).getBlock() == Blocks.PALE_OAK_WOOD && TreeBlocks.state(TreePlan.FRUIT).getBlock() == Blocks.SHROOMLIGHT
                && TreeBlocks.state(TreePlan.FIREFLY).getBlock() == Blocks.FIREFLY_BUSH && TreeBlocks.state(TreePlan.CARPET).getBlock() == Blocks.MOSS_CARPET, "other tree blocks");
        LOG.info("[tree-test] OK plan = addon (fp {} · {} cells), Java blocks", a.fp, a.total);
        h.succeed();
    }

    /** 블록 가리기 (애드온 시험과 같은 예) · 칸 번호 글 */
    @GameTest(maxTicks = 20)
    public void blockRulesAndCodes(GameTestHelper h) {
        List<Block> nat = List.of(Blocks.AIR, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.STONE, Blocks.DEEPSLATE, Blocks.SAND, Blocks.GRAVEL, Blocks.WATER, Blocks.SNOW_BLOCK,
                Blocks.OAK_LOG, Blocks.PALE_OAK_WOOD, Blocks.OAK_LEAVES, Blocks.TALL_GRASS, Blocks.POPPY, Blocks.BAMBOO, Blocks.SUGAR_CANE, Blocks.KELP, Blocks.BROWN_MUSHROOM_BLOCK);
        for (Block b : nat) h.assertTrue(TreeBlocks.isNatural(st(b)), "natural " + b);
        List<Block> kept = List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.OAK_PLANKS, Blocks.GLASS,
                Blocks.STRIPPED_OAK_LOG, Blocks.BIRCH_WOOD, Blocks.FARMLAND, Blocks.DIRT_PATH, Blocks.WHEAT, Blocks.CHEST, Blocks.BEDROCK, Blocks.LAVA, Blocks.OBSIDIAN,
                Blocks.TERRACOTTA, Blocks.TORCH, Blocks.RAIL, AltarBlocks.block("ymir"));
        for (Block b : kept) h.assertTrue(!TreeBlocks.isNatural(st(b)), "left alone " + b);
        for (Block b : List.of(Blocks.AIR, Blocks.SHORT_GRASS, Blocks.POPPY, Blocks.OAK_LEAVES, Blocks.CHERRY_LEAVES, Blocks.OAK_SAPLING, Blocks.SNOW)) {
            h.assertTrue(TreeBlocks.isSoft(st(b)), "soft " + b);
        }
        for (Block b : List.of(Blocks.STONE, Blocks.WATER, Blocks.BAMBOO, Blocks.OAK_LOG)) h.assertTrue(!TreeBlocks.isSoft(st(b)), "not soft " + b);
        for (Block b : List.of(Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.SAND, Blocks.FARMLAND, Blocks.OAK_PLANKS)) h.assertTrue(TreeBlocks.isGround(st(b)), "ground " + b);
        for (Block b : List.of(Blocks.TALL_GRASS, Blocks.BAMBOO, Blocks.WHEAT, Blocks.OAK_LOG, Blocks.WATER, Blocks.RED_MUSHROOM_BLOCK, Blocks.KELP, Blocks.KELP_PLANT)) {
            h.assertTrue(!TreeBlocks.isGround(st(b)), "not ground " + b);
        }
        h.assertTrue(TreeBlocks.isKept("minecraft:coal_ore") && TreeBlocks.isKept("minecraft:obsidian") && TreeBlocks.isKept("minecraft:white_terracotta")
                && !TreeBlocks.isKept("minecraft:white_glazed_terracotta") && TreeBlocks.isKept("minecraft:brain_coral_block"), "kept (not buildings)");
        // 칸 번호 글
        int[] a = {0, 0, 0, 0, 0, 0, 1, 1, 2, 0, 3, 3, 3, 3, 3, 15, 35, 0};
        String s = TreeLog.encodeCodes(a, 0, a.length, 1);
        h.assertTrue(s.equals("0*6,11203*5,fz0"), "run-length codes = addon " + s);
        h.assertTrue(java.util.Arrays.equals(TreeLog.decodeCodes(s, a.length, 1), a), "decoded back");
        int[] a2 = {1, 40, 40, 40, 40, 1295, 2, 0, 0, 0};
        h.assertTrue(java.util.Arrays.equals(TreeLog.decodeCodes(TreeLog.encodeCodes(a2, 0, a2.length, 2), a2.length, 2), a2), "two characters a code");
        h.assertTrue(TreeLog.decodeCodes(s, a.length + 1, 1) == null && TreeLog.decodeCodes("0*zz", 5, 1) == null && TreeLog.decodeCodes("G", 1, 1) == null
                && TreeLog.decodeCodes("1", 1, 2) == null, "a broken log is refused");
        int[] big = new int[55000];
        for (int i = 0; i < big.length; i++) big[i] = i < 3000 ? (i % 7 == 0 ? 3 : 2) : 1;
        h.assertTrue(TreeLog.encodeCodes(big, 0, big.length, 1).length() < 4000, "a big tree's log stays small");
        LOG.info("[tree-test] OK block rules (natural · soft · ground · kept) + code strings");
        h.succeed();
    }

    /** 기록 저장 · 다시 읽기: 큰 팔레트 (두 글자 번호) · 30000자씩 나눔 · 깔개 높이 · 함께 치운 칸 · 목록 (최근 5그루) */
    @GameTest(maxTicks = 20)
    public void logSavesAndLoads(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        List<BlockState> states = new ArrayList<>();
        for (Block b : List.of(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
                Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS, Blocks.BAMBOO_PLANKS, Blocks.CRIMSON_PLANKS, Blocks.WARPED_PLANKS, Blocks.PALE_OAK_PLANKS, Blocks.STONE_BRICKS,
                Blocks.MOSSY_STONE_BRICKS, Blocks.BRICKS, Blocks.GLASS, Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND)) states.add(st(b));
        for (int layers = 1; layers <= 8; layers++) states.add(Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, layers));
        states.add(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
        states.add(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Z));
        states.add(Blocks.SUNFLOWER.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        states.add(Blocks.SEAGRASS.defaultBlockState());
        states.add(Blocks.WATER.defaultBlockState());
        states.add(Blocks.MANGROVE_ROOTS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        for (int i = 0; i < 10; i++) states.add(Blocks.KELP.defaultBlockState().setValue(BlockStateProperties.AGE_25, i));
        int n = 70000;
        TreeLog log = TreeLog.fresh(n);
        int[] want = new int[n];
        for (int i = 0; i < n; i++) {
            int k = (i * 7919) % 97;
            want[i] = k < 40 ? 0 : k < 45 ? 1 : log.codeOf(states.get(k % states.size()));
            log.codes[i] = want[i];
        }
        h.assertTrue(log.palette.size() >= 36, "big palette " + log.palette.size());
        BlockPos base = new BlockPos(48_000, 120, -48_000);
        log.dys.put(5, -1);
        log.dys.put(9, 2);
        log.extras.put(7, List.of(new TreeLog.Extra(base.getX() + 1, base.getY() + 2, base.getZ() + 3, log.codeOf(states.get(30)))));
        TreeLog.Rec rec = new TreeLog.Rec("zztest" + System.nanoTime() % 100000, "minecraft:overworld", base.getX(), base.getY(), base.getZ(), 40, 5, 20, "tester", 2, 3078257565L, n);
        TreeLog.addRecord(server, rec, 5);
        try {
            h.assertTrue(log.save(server, rec.id(), base, 0), "saved");
            h.assertTrue(mmorpg.fabric.Props.world(server, "rpg:wt_" + rec.id() + ".1") != null, "split into parts of 30000 characters");
            TreeLog back = TreeLog.load(server, rec);
            h.assertTrue(back != null && java.util.Arrays.equals(back.codes, want), "codes read back");
            for (int i = 0; i < n; i += 997) {
                if (want[i] > 1) h.assertTrue(back.stateOf(want[i]) == log.stateOf(want[i]) && back.stateOf(want[i]) != null, "state " + want[i] + " " + back.stateOf(want[i]));
            }
            for (BlockState s : states) h.assertTrue(back.stateOf(log.codeOf(s)) == s, "exact state back " + s);
            h.assertTrue(back.dys.equals(Map.of(5, -1, 9, 2)) && back.extras.get(7).equals(log.extras.get(7)), "carpet heights and cleared cells");
            TreeLog.Rec r = TreeLog.last(server);
            h.assertTrue(r != null && r.equals(rec), "record list " + r);
            // 망가진 기록
            mmorpg.fabric.Props.setWorld(server, "rpg:wt_" + rec.id(), "{broken");
            h.assertTrue(TreeLog.load(server, rec) == null, "broken log refused");
        } finally {
            TreeLog.dropRecord(server, rec.id());
        }
        h.assertTrue(mmorpg.fabric.Props.world(server, "rpg:wt_" + rec.id()) == null && mmorpg.fabric.Props.world(server, "rpg:wt_" + rec.id() + ".1") == null, "log parts dropped");
        LOG.info("[tree-test] OK block log saved in parts and read back (palette {}, {} cells)", log.palette.size(), n);
        h.succeed();
    }

    // ------------------------------------------------------------ 살핌

    /** 바로 거절: 세계 높이 · 보스 필드 · 물 속 · 네더 · 불러오지 않은 곳 */
    @GameTest(maxTicks = 40)
    public void refusesBadSpots(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        TreePlan plan = TreePlan.plan(40, 5);
        BlockPos base = new BlockPos(47_008, 150, 47_008);
        String hold = loadArea(level, base, 34);
        Arena.Altar near = null;
        Arena.Altar far = null;
        try {
            level.setBlock(base.below(), Blocks.STONE.defaultBlockState(), FLAGS);
            level.setBlock(base, Blocks.AIR.defaultBlockState(), FLAGS);
            h.assertTrue(WorldTree.treeProblem(level, base, plan, true) == null, "open ground: fine — " + WorldTree.treeProblem(level, base, plan, true));
            int top = level.getMinY() + level.getHeight();
            String high = WorldTree.treeProblem(level, base.atY(top - 20), plan, false);
            h.assertTrue(high != null && high.contains("너무 높은 곳"), "too high: " + high);
            String low = WorldTree.treeProblem(level, base.atY(level.getMinY() + 1), plan, false);
            h.assertTrue(low != null && low.contains("너무 낮은 곳"), "too low: " + low);
            level.setBlock(base, Blocks.WATER.defaultBlockState(), FLAGS);
            String wet = WorldTree.treeProblem(level, base, plan, true);
            h.assertTrue(wet != null && wet.contains("물(용암) 속"), "under water: " + wet);
            level.setBlock(base, Blocks.AIR.defaultBlockState(), FLAGS);
            near = altarAt(level, "ymir", base.offset(30, 0, 0));
            String boss = WorldTree.treeProblem(level, base, plan, true);
            String ymir = Rules.get().obj("BOSSES").obj("ymir").str("name");
            h.assertTrue(boss != null && boss.contains("보스 필드(" + ymir), "overlapping a boss field: " + boss);
            forgetAltar(level, near);
            near = null;
            far = Arena.addAltar("ymir", NpcData.dimId(level), base.offset(400, 0, 0));
            h.assertTrue(WorldTree.treeProblem(level, base, plan, true) == null, "a boss field far away: fine");
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            if (nether != null) {
                String n = WorldTree.treeProblem(nether, new BlockPos(0, 64, 0), plan, false);
                h.assertTrue(n != null && n.contains("네더"), "nether: " + n);
            }
            String unloaded = WorldTree.treeProblem(level, new BlockPos(9_000_008, 150, 9_000_008), plan, true);
            h.assertTrue(unloaded != null && unloaded.contains("불러와지지 않았습니다"), "unloaded: " + unloaded);
            LOG.info("[tree-test] OK refused: {} / {} / {} / {} / {}", high, low, wet, boss, unloaded);
        } finally {
            forgetAltar(level, near);
            forgetAltar(level, far);
            Ticking.releaseArea(hold);
        }
        h.succeed();
    }

    /** 살핌 (여러 틱): 트인 풀밭 = 괜찮음, 줄기 자리에 판자 13개 = 거절 (무엇이 몇 개), 뿌리 사이 땅속 용암 = 거절, 잎 자리 절반이 돌 = 거절 */
    @GameTest(maxTicks = 400)
    public void surveyRefusesBuildingsLavaAndRock(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        TreePlan plan = TreePlan.plan(40, 7);
        BlockPos base = new BlockPos(47_508, 200, 47_508);
        String hold = loadArea(level, base, plan.reach + 4);
        flatGround(level, base, plan.reach + 3, plan);
        List<TreePlan.Op> wood = new ArrayList<>();
        List<TreePlan.Op> leaves = new ArrayList<>();
        for (TreePlan.Op o : plan.allOps()) {
            if (o.mode() == TreePlan.Mode.WOOD && o.dy() >= 2 && o.dy() < 20) wood.add(o);
            if (o.mode() == TreePlan.Mode.LEAF) leaves.add(o);
        }
        AtomicReference<WorldTree.Survey> got = new AtomicReference<>();
        int[] phase = {0};
        BlockPos[] lava = {null};
        WorldTree.treeSurvey(level, base, plan, got::set);
        h.succeedWhen(() -> {
            WorldTree.Survey sv = got.get();
            if (sv == null) throw h.assertionException("surveying (phase " + phase[0] + ")");
            got.set(null);
            if (phase[0] == 0) {
                h.assertTrue(sv.why() == null && sv.built() == 0 && sv.blocked() == 0, "open grass: fine — " + sv);
                for (int i = 0; i < 13; i++) level.setBlock(base.offset(wood.get(i).dx(), wood.get(i).dy(), wood.get(i).dz()), Blocks.OAK_PLANKS.defaultBlockState(), FLAGS);
                phase[0] = 1;
                WorldTree.treeSurvey(level, base, plan, got::set);
                throw h.assertionException("next: planks");
            }
            if (phase[0] == 1) {
                h.assertTrue(sv.why() != null && sv.why().contains("건물·설치물") && sv.why().contains("oak_planks 13") && sv.why().contains("블록 13개"), "a house in the way: " + sv.why());
                for (int i = 0; i < 13; i++) level.setBlock(base.offset(wood.get(i).dx(), wood.get(i).dy(), wood.get(i).dz()), Blocks.AIR.defaultBlockState(), FLAGS);
                // 용암: 뿌리 사이 땅속 (나무 칸이 아닌 기둥, 살피는 2칸 간격 자리)
                int R = plan.rootReach + 2;
                outer:
                for (int dx = -R; dx <= R; dx += 2) {
                    for (int dz = -R; dz <= R; dz += 2) {
                        double r = Math.hypot(dx, dz);
                        if (r > 8 && r < R - 1 && !has(plan, dx, -1, dz) && !has(plan, dx, -2, dz) && !has(plan, dx, 0, dz)) {
                            lava[0] = base.offset(dx, -1, dz);
                            break outer;
                        }
                    }
                }
                level.setBlock(lava[0], Blocks.LAVA.defaultBlockState(), FLAGS);
                phase[0] = 2;
                WorldTree.treeSurvey(level, base, plan, got::set);
                throw h.assertionException("next: lava");
            }
            if (phase[0] == 2) {
                h.assertTrue(sv.why() != null && sv.why().contains("용암") && sv.why().contains(lava[0].getX() + " " + lava[0].getY() + " " + lava[0].getZ()), "lava between the roots: " + sv.why());
                level.setBlock(lava[0], Blocks.DIRT.defaultBlockState(), FLAGS);
                for (int i = 0; i < leaves.size(); i++) if (i % 2 == 0 || i % 3 == 0) level.setBlock(base.offset(leaves.get(i).dx(), leaves.get(i).dy(), leaves.get(i).dz()), Blocks.STONE.defaultBlockState(), FLAGS);
                phase[0] = 3;
                WorldTree.treeSurvey(level, base, plan, got::set);
                throw h.assertionException("next: rock");
            }
            h.assertTrue(sv.why() != null && sv.why().contains("막혀 있습니다") && sv.why().contains("%"), "inside rock: " + sv.why());
            for (TreePlan.Op o : leaves) level.setBlock(base.offset(o.dx(), o.dy(), o.dz()), Blocks.AIR.defaultBlockState(), FLAGS);
            LOG.info("[tree-test] OK survey: open grass fine, refused planks · lava · rock");
            Ticking.releaseArea(hold);
        });
    }

    // ------------------------------------------------------------ 심기 · 자라기 · 되돌리기

    /**
     * 작은 나무 (minHeight 40) 를 하늘의 풀밭에: 확인 창 → 심기 → 단계마다 자라남 → 설계대로 (모든 칸, 떨어지지 않는 잎, 덩굴 · 깔개 · 덤불), 밑동의 소는 뿌리 끝 밖으로,
     * 두 칸 해바라기 · 양귀비는 먼저 치움 → 기록 (지문 · 칸 수 · 원래 블록) → 기억을 지우고 (다시 불러온 것처럼) 되돌리기 확인 창 → 되돌림 → 처음과 칸마다 같음, 기록 지움
     */
    @GameTest(maxTicks = 3000)
    public void growsAndUndoes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        int H = (int) Rules.get().obj("WORLD_TREE").num("minHeight");
        BlockPos base = new BlockPos(46_008, 200, 46_008);
        TreePlan plan = TreePlan.plan(H, TreePlan.seedOf(base.getX(), base.getY(), base.getZ()));
        String hold = loadArea(level, base, plan.reach + 4);
        flatGround(level, base, plan.reach + 3, plan);
        // 두 칸 해바라기 (줄기 칸 dy 0 · 1), 양귀비 (뿌리가 바꾸는 땅 위, 나무 칸이 아닌 곳)
        BlockPos sun = null;
        for (int dx = 1; dx <= 3 && sun == null; dx++) if (has(plan, dx, 0, 0) && has(plan, dx, 1, 0)) sun = base.offset(dx, 0, 0);
        h.assertTrue(sun != null, "trunk cells for a sunflower");
        level.setBlock(sun, Blocks.SUNFLOWER.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), FLAGS);
        level.setBlock(sun.above(), Blocks.SUNFLOWER.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), FLAGS);
        BlockPos poppy = null;
        for (TreePlan.Op o : plan.allOps()) {
            if (o.mode() == TreePlan.Mode.WOOD && o.dy() == -1 && Math.hypot(o.dx(), o.dz()) > 8 && !has(plan, o.dx(), 0, o.dz()) && !has(plan, o.dx(), 1, o.dz())
                    && !groundCol(plan, o.dx(), o.dz())) {
                poppy = base.offset(o.dx(), 0, o.dz());
                break;
            }
        }
        h.assertTrue(poppy != null, "a root under open grass");
        level.setBlock(poppy, Blocks.POPPY.defaultBlockState(), FLAGS);
        Map<BlockPos, BlockState> before = snapshot(level, base, plan);
        before.put(poppy, level.getBlockState(poppy));
        before.put(sun.above(), level.getBlockState(sun.above()));
        // 밑동의 소 (줄기 칸)
        Entity cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        cow.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0F, 0F);
        if (cow instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true); // 제자리에 (줄기 칸에 걸쳐 섬)
        level.addFreshEntity(cow);
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        WorldTree.plantAt(sp, level, base, H);
        int[] phase = {0};
        String[] id = {null};
        BlockPos sunPos = sun;
        BlockPos poppyPos = poppy;
        h.succeedWhen(() -> {
            if (phase[0] == 0) {
                // 확인 창: 자리 · 높이 · 되돌리는 법
                if (!(Forms.last(sp) instanceof FormNet.MenuPayload m)) throw h.assertionException("waiting for the window (busy " + WorldTree.treeBusy() + ")");
                String body = plain(m.text());
                h.assertTrue(plain(m.title()).contains(WorldTree.name()) && body.contains(base.getX() + " " + base.getY() + " " + base.getZ()) && body.contains("높이 " + H + "칸")
                        && body.contains("/worldtree undo") && body.contains("/세계수 undo") && m.buttons().size() == 2, "plant window: " + body);
                h.assertTrue(cow.isAlive() && cow.position().distanceTo(Vec3.atBottomCenterOf(base)) < 1, "the cow still stands at the foot " + cow.position());
                Forms.answer(sp, 0, null);
                phase[0] = 1;
                throw h.assertionException("planting");
            }
            if (phase[0] == 1) {
                WorldTree.JobInfo j = WorldTree.treeJob();
                if (j == null && WorldTree.lastResult() == null) throw h.assertionException("waiting for the job");
                TreeLog.Rec rec = TreeLog.last(server);
                h.assertTrue(rec != null && rec.x() == base.getX() && rec.y() == base.getY() && rec.z() == base.getZ() && rec.h() == H && rec.fp() == plan.fp && rec.n() == plan.total
                        && rec.s() == plan.seed, "written down: " + rec);
                id[0] = rec.id();
                phase[0] = 2;
                throw h.assertionException("growing");
            }
            if (phase[0] == 2) {
                if (WorldTree.treeJob() != null) throw h.assertionException("growing (" + WorldTree.treeJob().placed() + " placed)");
                WorldTree.Result r = WorldTree.lastResult();
                h.assertTrue(r != null && !r.undo() && r.id().equals(id[0]), "grown " + r);
                h.assertTrue(r.placed() == plan.total && r.skipped() == 0 && r.errors() == 0, "every block placed on open grass: " + r + " / " + plan.total);
                h.assertTrue(r.movedMobs() == 1 && cow.position().distanceTo(Vec3.atBottomCenterOf(base)) > plan.rootReach + 2, "the cow at the foot was moved out of the way: "
                        + cow.position() + " (" + r.movedMobs() + ")");
                // 설계대로
                int bad = 0;
                String first = "";
                int carpets = 0;
                for (TreePlan.Op o : plan.allOps()) {
                    if (o.mode() == TreePlan.Mode.CARPET || o.mode() == TreePlan.Mode.BUSH) {
                        for (int dy = 0; dy <= 3; dy++) {
                            if (level.getBlockState(base.offset(o.dx(), dy, o.dz())).getBlock() == TreeBlocks.block(o.block())) {
                                carpets++;
                                break;
                            }
                        }
                        continue;
                    }
                    BlockState s = level.getBlockState(base.offset(o.dx(), o.dy(), o.dz()));
                    if (s.getBlock() != TreeBlocks.block(o.block())) {
                        if (bad++ == 0) first = o + " → " + s;
                    }
                }
                h.assertTrue(bad == 0, bad + " cells not as planned, e.g. " + first);
                h.assertTrue(carpets == plan.stages.get(plan.stages.size() - 1).ops().size(), "moss carpets and bushes on the grass: " + carpets);
                for (TreePlan.Op o : plan.allOps()) {
                    if (o.block().equals(TreePlan.LEAF)) {
                        BlockState s = level.getBlockState(base.offset(o.dx(), o.dy(), o.dz()));
                        h.assertTrue(s.getValue(BlockStateProperties.PERSISTENT), "persistent leaves " + s);
                        break;
                    }
                }
                h.assertTrue(level.getBlockState(sunPos).getBlock() == Blocks.PALE_OAK_WOOD && level.getBlockState(sunPos.above()).getBlock() == Blocks.PALE_OAK_WOOD,
                        "the sunflower (both halves) gave way to the trunk");
                h.assertTrue(level.getBlockState(poppyPos).isAir() || level.getBlockState(poppyPos).getBlock() == Blocks.MOSS_CARPET, "the poppy was cleared before the root took its ground");
                // 기록
                TreeLog.Rec rec = TreeLog.last(server);
                TreeLog log = TreeLog.load(server, rec);
                h.assertTrue(log != null && log.placed() == plan.total, "block log: every placed cell " + (log == null ? "none" : log.placed()));
                boolean air = false;
                boolean grass = false;
                boolean dirt = false;
                for (List<Object> e : log.palette) {
                    air |= e.get(0).equals("minecraft:air");
                    grass |= e.get(0).equals("minecraft:grass_block");
                    dirt |= e.get(0).equals("minecraft:dirt");
                }
                h.assertTrue(air && grass && dirt && log.extras.size() >= 2, "original blocks (air · grass · dirt) and cleared plants: " + log.palette + " extras " + log.extras.size());
                // 다시 불러온 것처럼 (기억을 지우고 세계의 기록만으로) 되돌리기
                WorldTree.forget();
                WorldTree.undoTree(sp);
                phase[0] = 3;
                throw h.assertionException("undo asked");
            }
            if (phase[0] == 3) {
                if (!(Forms.last(sp) instanceof FormNet.MenuPayload m)) throw h.assertionException("waiting for the undo window");
                String body = plain(m.text());
                h.assertTrue(plain(m.title()).contains("되돌리기") && body.contains(base.getX() + " " + base.getY() + " " + base.getZ()) && body.contains("높이: " + H + "칸"), "undo window: " + body);
                Forms.answer(sp, 0, null);
                phase[0] = 4;
                throw h.assertionException("undoing");
            }
            if (phase[0] == 4) {
                WorldTree.Result r = WorldTree.lastResult();
                if (WorldTree.treeJob() != null || r == null || !r.undo()) throw h.assertionException("undoing (busy " + WorldTree.treeBusy() + ")");
                h.assertTrue(r.restored() == plan.total && r.changed() == 0 && r.unknown() == 0 && r.failed() == 0, "every block restored: " + r);
                java.util.Set<BlockPos> cells = new java.util.HashSet<>();
                for (TreePlan.Op o : plan.allOps()) if (o.mode() != TreePlan.Mode.CARPET && o.mode() != TreePlan.Mode.BUSH) cells.add(base.offset(o.dx(), o.dy(), o.dz()));
                List<String> d = diff(level, before, cells);
                h.assertTrue(d.isEmpty(), "back as it was (air · grass · dirt · sunflower · poppy): " + d);
                h.assertTrue(level.getBlockState(sunPos.above()).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER, "sunflower top half back");
                TreeLog.Rec rec = TreeLog.last(server);
                h.assertTrue(rec == null || !rec.id().equals(id[0]), "record dropped");
                h.assertTrue(mmorpg.fabric.Props.world(server, "rpg:wt_" + id[0]) == null, "block log dropped");
                LOG.info("[tree-test] OK grown ({} blocks, cow moved) and undone after a reload — every cell back", plan.total);
                // 한글 명령 (/세계수 plant 40): 가까운 땅을 보면 뿌리 끝 밖 (뿌리 어림 + clearance) 으로 밀어낸 자리에 심을지 물음 → 그만두기 = 아무것도 하지 않음
                sp.snapTo(base.getX() + 0.5, base.getY(), base.getZ() - 19.5, 0F, 10F);
                int minD = TreePlan.rootReachOf(H) + (int) Rules.get().obj("WORLD_TREE").num("clearance");
                h.assertTrue(minD == 20, "pushed-out distance " + minD);
                WorldTree.Spot spot = WorldTree.treeSpot(sp, minD);
                h.assertTrue(base.equals(spot.base()), "looking at the ground close by: pushed out to the tree's spot " + spot);
                server.getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "세계수 plant " + H);
                phase[0] = 5;
                throw h.assertionException("command");
            }
            if (phase[0] == 5) {
                if (!(Forms.last(sp) instanceof FormNet.MenuPayload m)) throw h.assertionException("waiting for the command's window (busy " + WorldTree.treeBusy() + ")");
                String body = plain(m.text());
                h.assertTrue(body.contains(base.getX() + " " + base.getY() + " " + base.getZ()) && body.contains("높이 " + H + "칸"), "command window: " + body);
                Forms.answer(sp, 1, null); // 그만두기
                phase[0] = 6;
                throw h.assertionException("cancelled");
            }
            if (phase[0] == 6) {
                h.assertTrue(WorldTree.treeJob() == null && Forms.last(sp) == null, "cancel: nothing grows");
                TreeLog.Rec rec = TreeLog.last(server);
                h.assertTrue(rec == null || !rec.base().equals(base), "cancel: nothing written down");
                h.assertTrue(level.getBlockState(base.above(3)).isAir(), "cancel: no tree");
                LOG.info("[tree-test] OK /세계수 plant {} → window at the pushed-out spot → cancel", H);
                Forms.HEADLESS.remove(sp.getUUID());
                server.getPlayerList().remove(sp);
                cow.discard();
                Ticking.releaseArea(hold);
                return;
            }
            throw h.assertionException("phase " + phase[0]);
        });
    }

    /** 명령어: /worldtree · /세계수 · /segyesu (운영자 — plant [높이] · undo) */
    @GameTest(maxTicks = 20)
    public void commandsRegistered(GameTestHelper h) {
        var root = h.getLevel().getServer().getCommands().getDispatcher().getRoot();
        for (String name : List.of("worldtree", "세계수", "segyesu")) {
            var node = root.getChild(name);
            h.assertTrue(node != null && node.getChild("plant") != null && node.getChild("undo") != null && node.getChild("plant").getChild("height") != null, "command " + name);
        }
        LOG.info("[tree-test] OK commands /worldtree · /세계수 · /segyesu");
        h.succeed();
    }
}
