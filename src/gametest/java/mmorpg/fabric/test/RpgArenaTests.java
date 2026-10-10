package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Mobs;
import mmorpg.core.Rules;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaBlocks;
import mmorpg.fabric.boss.ArenaBuild;
import mmorpg.fabric.boss.ArenaThemes;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 보스 제단 · 보스 필드 (arena.js): 제단 블록 9종 등록 (빛 · 부술 수 없음 · 폭발 · 아이템 · 이름), 신전 설계 9종의 블록이
 * 모두 자바 블록으로 풀림, 제단을 놓으면 목록에 들어가고 관리자에게 "보스 필드 만들기" 창, 짓기가 끝나면 특징 자리의 블록 (허수아비 킹 토템 짚단 ·
 * 잭오랜턴, 기둥) · 설계와 칸마다 같은지 · 다시 지어도 같은 블록, 필드 보호 (서바이벌은 못 부숨 · 못 놓음 · 도구 · 양동이 · 폭발), 제단이 없어지면
 * 목록에서 빠지고 훅이 불림, 겹치는 제단 · 높이가 맞지 않는 제단은 짓기 거절.
 * 짓는 시험은 시험 구조물 · 지형과 겹치지 않게 멀리 (x · z 수만) 하늘 (y 150) 에서 — 붙잡아 둔 땅 (Ticking) 을 바로 불러옴. 통과하면 [arena-test] OK 줄.
 */
public final class RpgArenaTests {
    private static final String KEY = "scarecrow_king";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-arena-test");

    // ------------------------------------------------------------ 도우미

    /** 그 자리 둘레 half 칸 네모를 붙잡고 지금 바로 불러옴 (시험) */
    private static String loadArea(ServerLevel level, BlockPos center, int half) {
        String hold = Ticking.holdArea(level, Vec3.atCenterOf(center), half, "t");
        for (int cx = Math.floorDiv(center.getX() - half, 16); cx <= Math.floorDiv(center.getX() + half, 16); cx++) {
            for (int cz = Math.floorDiv(center.getZ() - half, 16); cz <= Math.floorDiv(center.getZ() + half, 16); cz++) level.getChunk(cx, cz);
        }
        return hold;
    }

    /** 제단 블록을 놓고 목록에 넣음 (플레이어 없이) */
    private static Arena.Altar altarAt(ServerLevel level, String key, BlockPos pos) {
        level.setBlock(pos, AltarBlocks.block(key).defaultBlockState(), Block.UPDATE_ALL);
        return Arena.addAltar(key, NpcData.dimId(level), pos);
    }

    private static void forget(ServerLevel level, Arena.Altar a) {
        if (a == null) return;
        Consumer<Arena.Altar> hook = Arena.altarRemovedHook();
        Arena.setAltarRemovedHook(null);
        try {
            Arena.removeAltar(a);
        } finally {
            Arena.setAltarRemovedHook(hook);
        }
        level.setBlock(a.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** 설계의 작업을 차례대로 놓았을 때 칸마다 마지막 블록 (자바 블록 상태) */
    private static Map<BlockPos, BlockState> expected(Arena.Altar a) {
        ArenaThemes.Plan plan = Arena.arenaPlan(a);
        Map<BlockPos, BlockState> out = new HashMap<>();
        for (ArenaThemes.StageOps st : ArenaThemes.planOps(a.x, a.y, a.z, Arena.arenaSize(a).radius(), plan)) {
            for (ArenaThemes.Op op : st.ops()) {
                BlockState s = ArenaBlocks.state(op.block(), op.states());
                for (int x = Math.min(op.x(), op.x2()); x <= Math.max(op.x(), op.x2()); x++) {
                    for (int y = Math.min(op.y(), op.y2()); y <= Math.max(op.y(), op.y2()); y++) {
                        for (int z = Math.min(op.z(), op.z2()); z <= Math.max(op.z(), op.z2()); z++) out.put(new BlockPos(x, y, z), s);
                    }
                }
            }
        }
        return out;
    }

    /** 기대한 블록과 다른 칸 (처음 몇 개) */
    private static List<String> mismatches(ServerLevel level, Map<BlockPos, BlockState> want) {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : want.entrySet()) {
            BlockState got = level.getBlockState(e.getKey());
            if (got != e.getValue()) {
                if (bad.size() < 5) bad.add(e.getKey().toShortString() + " " + got + " != " + e.getValue());
                else if (bad.size() == 5) bad.add("…");
            }
        }
        return bad;
    }

    private static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    private static BlockPos at(Vec3 v, int dy) {
        return BlockPos.containing(v.x, v.y + dy, v.z);
    }

    /** 운영자가 아닌 서바이벌 시험 플레이어 */
    private static ServerPlayer survivor(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        if (sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) h.getLevel().getServer().getPlayerList().deop(sp.nameAndId());
        h.assertFalse(Arena.canManage(sp), "mock survivor is not a manager");
        return sp;
    }

    // ------------------------------------------------------------ 시험

    /** 제단 블록 9종: 등록 · 빛 10 · 서바이벌에서 부술 수 없음 · 폭발에 안 부서짐 · 아이템 (블록 아이템, /boss altar) · 이름 */
    @GameTest(maxTicks = 20)
    public void altarBlocksRegistered(GameTestHelper h) {
        h.assertTrue(AltarBlocks.blocks().size() == 9, "9 altars " + AltarBlocks.blocks().keySet());
        for (String key : Arena.BOSSES.keys()) {
            AltarBlocks.AltarBlock b = AltarBlocks.block(key);
            h.assertTrue(b != null, "altar block " + key);
            h.assertTrue(BuiltInRegistries.BLOCK.getKey(b).toString().equals(Mobs.altarId(key)), "id " + BuiltInRegistries.BLOCK.getKey(b));
            h.assertTrue(Arena.altarKeyOf(Mobs.altarId(key)).equals(key), "altarKeyOf " + key);
            BlockState s = b.defaultBlockState();
            h.assertTrue(s.getLightEmission() == 10, key + " light " + s.getLightEmission());
            h.assertTrue(b.defaultDestroyTime() < 0, key + " unbreakable in survival " + b.defaultDestroyTime());
            h.assertTrue(b.getExplosionResistance() >= 3600000F, key + " explosion " + b.getExplosionResistance());
            h.assertTrue(b.getDescriptionId().equals("block.rpg.altar_" + key), "name key " + b.getDescriptionId());
            ItemStack it = Arena.altarItem(key, 2);
            h.assertTrue(it.getCount() == 2 && it.getItem() instanceof BlockItem bi && bi.getBlock() == b, key + " item " + it);
            h.assertTrue(b.asItem() == it.getItem(), key + " block → item");
            h.assertTrue(AltarBlocks.keyOf(s).equals(key), "keyOf");
        }
        h.assertTrue(Arena.altarItem("nope", 1).isEmpty(), "unknown key → empty");
        LOG.info("[arena-test] OK altar blocks 9 registered");
        h.succeed();
    }

    /** 신전 설계 9종: 블록 · 상태가 모두 자바로 풀림, 같은 자리 = 같은 작업 목록, 상태 대응 몇 가지 */
    @GameTest(maxTicks = 20)
    public void designsMapToJavaBlocks(GameTestHelper h) {
        int ops = 0;
        for (String key : Arena.BOSSES.keys()) {
            int R = (int) Arena.BOSSES.obj(key).obj("arena").num("radius");
            ArenaThemes.Plan plan = ArenaThemes.arenaPlan(key, 123, 70, -456);
            h.assertTrue(plan != null, "plan " + key);
            List<ArenaThemes.StageOps> a = ArenaThemes.planOps(123, 70, -456, R, plan);
            List<ArenaThemes.StageOps> b = ArenaThemes.planOps(123, 70, -456, R, ArenaThemes.arenaPlan(key, 123, 70, -456));
            h.assertTrue(a.equals(b), key + " same design twice");
            h.assertTrue(a.size() == (key.equals("surtr") ? 6 : 5), key + " stages " + a.size());
            for (ArenaThemes.StageOps st : a) {
                for (ArenaThemes.Op op : st.ops()) {
                    ArenaBlocks.state(op.block(), op.states());
                    ops++;
                }
            }
            for (ArenaThemes.ColumnAt c : plan.features().crystals()) for (ArenaThemes.Put q : c.frost()) ArenaBlocks.state(q.block(), q.states());
        }
        h.assertTrue(ArenaBlocks.problems().isEmpty(), "unmapped " + ArenaBlocks.problems());
        h.assertTrue(ops > 1000, "ops " + ops);
        // 상태 대응
        BlockState lantern = ArenaBlocks.state("minecraft:lantern", Map.of("hanging", true));
        h.assertTrue((lantern.getBlock() == Blocks.LANTERN) && lantern.toString().contains("hanging=true"), "hanging lantern " + lantern);
        BlockState jack = ArenaBlocks.state("minecraft:lit_pumpkin", Map.of("minecraft:cardinal_direction", "west"));
        h.assertTrue((jack.getBlock() == Blocks.JACK_O_LANTERN) && jack.toString().contains("facing=west"), "jack o lantern " + jack);
        BlockState snow = ArenaBlocks.state("minecraft:snow_layer", Map.of("height", 1, "covered_bit", false));
        h.assertTrue((snow.getBlock() == Blocks.SNOW) && snow.toString().contains("layers=2"), "snow layer " + snow);
        BlockState pickle = ArenaBlocks.state("minecraft:sea_pickle", Map.of("cluster_count", 2, "dead_bit", true));
        h.assertTrue(pickle.toString().contains("pickles=3") && pickle.toString().contains("waterlogged=false"), "dead sea pickle " + pickle);
        BlockState drip = ArenaBlocks.state("minecraft:pointed_dripstone", Map.of("dripstone_thickness", "frustum", "hanging", false));
        h.assertTrue(drip.toString().contains("vertical_direction=up") && drip.toString().contains("thickness=frustum"), "stalagmite " + drip);
        BlockState fire = ArenaBlocks.state("minecraft:campfire", Map.of("extinguished", false, "minecraft:cardinal_direction", "south"));
        h.assertTrue(fire.toString().contains("lit=true") && fire.toString().contains("facing=south"), "campfire " + fire);
        BlockState wall = ArenaBlocks.state("minecraft:cobblestone_wall", Map.of("wall_connection_type_east", "short", "wall_connection_type_west", "short",
                "wall_connection_type_south", "none", "wall_connection_type_north", "none", "wall_post_bit", false));
        h.assertTrue(wall.toString().contains("east=low") && wall.toString().contains("up=false") && wall.toString().contains("north=none"), "wall " + wall);
        BlockState fence = ArenaBlocks.state("minecraft:dark_oak_fence", Map.of("minecraft:connection_east", true, "minecraft:connection_north", false));
        h.assertTrue(fence.toString().contains("east=true") && fence.toString().contains("north=false"), "fence " + fence);
        BlockState candle = ArenaBlocks.state("minecraft:gray_candle", Map.of("candles", 3, "lit", true));
        h.assertTrue(candle.toString().contains("candles=4") && candle.toString().contains("lit=true"), "candles " + candle);
        BlockState fan = ArenaBlocks.state("minecraft:dead_brain_coral_fan", Map.of("coral_fan_direction", 1));
        h.assertTrue(fan.toString().contains("waterlogged=false"), "dry coral fan " + fan);
        h.assertTrue(ArenaBlocks.problems().isEmpty(), "unmapped after spot checks " + ArenaBlocks.problems());
        // 테마 도우미
        h.assertTrue(ArenaThemes.deepTile(0, 0, 3).dark() && !ArenaThemes.deepTile(2, 0, 3).dark() && ArenaThemes.deepTileSize("great_worm") == 3, "deepTile");
        h.assertTrue("sun".equals(Arena.discSide(new Vec3(2.5, 0, 0.5), new Vec3(0.5, 0, 0.5), 7)) && "moon".equals(Arena.discSide(new Vec3(-1.5, 0, 0.5), new Vec3(0.5, 0, 0.5), 7))
                && "line".equals(Arena.discSide(new Vec3(0.9, 0, 4.5), new Vec3(0.5, 0, 0.5), 7)) && Arena.discSide(new Vec3(9.5, 0, 0.5), new Vec3(0.5, 0, 0.5), 7) == null, "discSide");
        LOG.info("[arena-test] OK designs map to Java blocks ({} ops)", ops);
        h.succeed();
    }

    /** 제단을 놓음 (크리에이티브): 목록에 들어가고 다음 틱에 "보스 필드 만들기" 창 → 나중에 = 짓지 않음 */
    @GameTest(maxTicks = 40)
    public void placingAltarAddsAndAsks(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setGameMode(GameType.CREATIVE);
        Forms.HEADLESS.add(sp.getUUID());
        ServerLevel level = h.getLevel();
        // 다른 시험의 제단과 겹치지 않게 멀리, 하늘에 (시험 세계는 지형이 있는 세계 — 땅 · 동굴과 겹치지 않게)
        BlockPos pos = new BlockPos(42_003, 150, 42_005);
        String hold = loadArea(level, pos, 4);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ItemStack altarItem = Arena.altarItem(KEY, 1);
        sp.setItemInHand(InteractionHand.MAIN_HAND, altarItem);
        // 플레이어가 돌 윗면을 누른 것처럼 (블록 누르기 이벤트 → 블록 아이템 놓기 → 놓음 처리)
        BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
        InteractionResult placed = sp.gameMode.useItemOn(sp, level, altarItem, InteractionHand.MAIN_HAND, click);
        h.assertTrue(AltarBlocks.keyOf(level.getBlockState(pos)) != null, "altar block placed " + level.getBlockState(pos) + " (" + placed + ")");
        Arena.Altar a = Arena.altarAt(NpcData.dimId(level), pos);
        h.assertTrue(a != null && a.key.equals(KEY) && !a.built, "added to the altar list " + a);
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(Forms.last(sp) instanceof FormNet.MenuPayload m && m.title().contains("보스 필드") && m.buttons().size() == 2,
                        "build question " + Forms.last(sp));
                Forms.answer(sp, 1, null); // 나중에
                h.assertFalse(Arena.isBuilding(a) || a.built, "later = no build");
                LOG.info("[arena-test] OK placed altar listed + build question");
            } finally {
                Forms.HEADLESS.remove(sp.getUUID());
                forget(level, a);
                level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                Ticking.releaseArea(hold);
            }
            RpgGearTests.done(h, sp);
        });
    }

    /** 짓기: 단계별로 다 지으면 a.built · 특징 자리 블록 (허수아비 토템 짚단 2 + 잭오랜턴, 기둥 원목 3 + 잭오랜턴), 설계와 칸마다 같음, 다시 지어도 같은 블록 */
    @GameTest(maxTicks = 900)
    public void buildMakesTheTemple(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.assertTrue(Arena.server() != null, "arena knows the server");
        BlockPos pos = new BlockPos(40_007, 150, 40_013);
        String hold = loadArea(level, pos, 22);
        Arena.Altar a = altarAt(level, KEY, pos);
        h.assertTrue(Arena.buildProblem(a) == null, "no build problem: " + Arena.buildProblem(a));
        h.assertTrue(Arena.buildArena(a, null) && Arena.isBuilding(a), "build started");
        h.assertFalse(Arena.buildArena(a, null), "already building");
        Map<BlockPos, BlockState> want = expected(a);
        int[] phase = {0};
        Map<BlockPos, BlockState> first = new HashMap<>();
        h.succeedWhen(() -> {
            if (Arena.isBuilding(a)) throw h.assertionException("building (phase " + phase[0] + ")");
            if (phase[0] == 0) {
                h.assertTrue(a.built && ArenaBuild.lastErrors(a) == 0, "built, errors " + ArenaBuild.lastErrors(a));
                h.assertTrue(AltarBlocks.keyOf(level.getBlockState(pos)) != null, "altar kept");
                List<String> bad = mismatches(level, want);
                h.assertTrue(bad.isEmpty(), "world = design: " + bad);
                Arena.Features f = Arena.arenaFeatures(a);
                h.assertTrue(f.totems().size() == 4 && f.decoys().size() == 4 && f.gates().size() == 4 && f.posts().size() == 8, "features " + f.totems().size() + " " + f.posts().size());
                for (Vec3 t : f.totems()) {
                    h.assertTrue((level.getBlockState(at(t, 0)).getBlock() == Blocks.HAY_BLOCK) && (level.getBlockState(at(t, 1)).getBlock() == Blocks.HAY_BLOCK), "totem hay at " + t);
                    BlockState head = level.getBlockState(at(t, 2));
                    h.assertTrue((head.getBlock() == Blocks.JACK_O_LANTERN), "totem head " + head);
                    h.assertTrue((level.getBlockState(at(t, -1)).getBlock() == Blocks.HAY_BLOCK), "hay ring under the totem");
                }
                for (Vec3 p : f.posts()) {
                    for (int dy = 0; dy <= 2; dy++) h.assertTrue((level.getBlockState(at(p, dy)).getBlock() == Blocks.DARK_OAK_LOG), "post log " + p + " +" + dy);
                    h.assertTrue((level.getBlockState(at(p, 3)).getBlock() == Blocks.JACK_O_LANTERN), "post lantern " + p);
                }
                for (Vec3 g : f.gates()) h.assertTrue(level.getBlockState(at(g, 0)).isAir() && level.getBlockState(at(g, 1)).isAir(), "gate open " + g);
                h.assertTrue(id(level.getBlockState(pos.below())).equals("minecraft:dark_oak_planks"), "altar dais " + level.getBlockState(pos.below()));
                // 울타리 고리: 이음이 있는 짙은 참나무 울타리
                BlockState fence = level.getBlockState(at(f.gates().get(0), 0).relative(Direction.SOUTH, 2));
                h.assertTrue((fence.getBlock() == Blocks.DARK_OAK_FENCE), "fence beside the east gate " + fence);
                // 다시 짓기 → 같은 블록
                first.putAll(want);
                for (BlockPos p : want.keySet()) first.put(p, level.getBlockState(p));
                h.assertTrue(Arena.buildArena(a, null), "rebuild started");
                phase[0] = 1;
                throw h.assertionException("rebuilding");
            }
            List<String> bad = mismatches(level, first);
            h.assertTrue(bad.isEmpty(), "rebuilt = first build: " + bad);
            LOG.info("[arena-test] OK temple built = design ({} cells), rebuilt the same", want.size());
            Ticking.releaseArea(hold);
            forget(level, a);
        });
    }

    /** 필드 보호: 서바이벌은 필드 블록을 못 부숨 · 못 놓음 · 부싯돌 · 빈 양동이 (물) 못 씀, 폭발이 필드를 부수지 않음, 크리에이티브는 부숨 */
    @GameTest(maxTicks = 900)
    public void fieldIsProtected(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = new BlockPos(40_209, 150, 40_011);
        String hold = loadArea(level, pos, 22);
        Arena.Altar a = altarAt(level, KEY, pos);
        h.assertTrue(Arena.buildArena(a, null), "build started");
        h.succeedWhen(() -> {
            if (Arena.isBuilding(a) || !a.built) throw h.assertionException("building");
            ServerPlayer sp = survivor(h);
            ServerPlayer op = RpgGearTests.player(h);
            op.setGameMode(GameType.CREATIVE);
            try {
                BlockPos floor = pos.offset(5, -1, 6);
                BlockState before = level.getBlockState(floor);
                h.assertFalse(before.isAir(), "floor " + before);
                h.assertTrue(Arena.builtArenaAt(NpcData.dimId(level), floor) == a, "inside the built field");
                // 부수기
                h.assertFalse(sp.gameMode.destroyBlock(floor), "survivor cannot break");
                h.assertTrue(level.getBlockState(floor) == before, "floor kept");
                h.assertFalse(sp.gameMode.destroyBlock(pos), "survivor cannot break the altar");
                // 놓기 (바닥 윗면을 누름)
                ItemStack stone = new ItemStack(Items.STONE, 5);
                sp.setItemInHand(InteractionHand.MAIN_HAND, stone);
                BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
                InteractionResult r = sp.gameMode.useItemOn(sp, level, stone, InteractionHand.MAIN_HAND, top);
                h.assertTrue(r == InteractionResult.FAIL && level.getBlockState(floor.above()).isAir() && stone.getCount() == 5, "survivor cannot place " + r);
                // 도구 (부싯돌)
                ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
                sp.setItemInHand(InteractionHand.MAIN_HAND, flint);
                r = sp.gameMode.useItemOn(sp, level, flint, InteractionHand.MAIN_HAND, top);
                h.assertTrue(r == InteractionResult.FAIL && level.getBlockState(floor.above()).isAir(), "no fire " + r);
                // 빈 양동이: 필드 안의 물은 못 뜸 (위에서 내려다봄)
                BlockPos water = pos.offset(-6, -1, 5);
                level.setBlock(water, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                sp.snapTo(water.getX() + 0.5, water.getY() + 1.2, water.getZ() + 0.5, 0F, 90F);
                ItemStack bucket = new ItemStack(Items.BUCKET);
                sp.setItemInHand(InteractionHand.MAIN_HAND, bucket);
                r = sp.gameMode.useItem(sp, level, bucket, InteractionHand.MAIN_HAND);
                h.assertTrue(r == InteractionResult.FAIL && (level.getBlockState(water).getBlock() == Blocks.WATER) && sp.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET),
                        "cannot scoop the field water " + r + " " + level.getBlockState(water));
                // 폭발: 필드 블록은 그대로
                BlockPos boom = pos.offset(-4, 0, -7);
                Map<BlockPos, BlockState> around = new HashMap<>();
                for (BlockPos p : BlockPos.betweenClosed(boom.offset(-2, -2, -2), boom.offset(2, 2, 2))) around.put(p.immutable(), level.getBlockState(p));
                level.explode(null, boom.getX() + 0.5, boom.getY() + 0.5, boom.getZ() + 0.5, 3F, Level.ExplosionInteraction.TNT);
                for (Map.Entry<BlockPos, BlockState> e : around.entrySet()) {
                    h.assertTrue(level.getBlockState(e.getKey()) == e.getValue(), "explosion kept " + e.getKey().toShortString() + " " + level.getBlockState(e.getKey()));
                }
                // 크리에이티브 (관리자) 는 부숨
                h.assertTrue(Arena.canManage(op), "creative manages");
                h.assertTrue(op.gameMode.destroyBlock(floor) && level.getBlockState(floor).isAir(), "creative can break");
                LOG.info("[arena-test] OK field protection (break · place · fire · bucket · explosion · creative)");
            } finally {
                level.getServer().getPlayerList().remove(sp);
                level.getServer().getPlayerList().remove(op);
            }
            Ticking.releaseArea(hold);
            forget(level, a);
        });
    }

    /** 제단이 없어지면 (명령어 · 크리에이티브가 부숨) 목록에서 빠지고 없어짐 훅이 불림 */
    @GameTest(maxTicks = 20)
    public void removedAltarLeavesList(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        List<Arena.Altar> removed = new ArrayList<>();
        Consumer<Arena.Altar> prev = Arena.altarRemovedHook();
        Arena.setAltarRemovedHook(a -> {
            removed.add(a);
            if (prev != null) prev.accept(a);
        });
        ServerPlayer op = RpgGearTests.player(h);
        op.setGameMode(GameType.CREATIVE);
        try {
            BlockPos p1 = h.absolutePos(new BlockPos(2, 30, 2));
            BlockPos p2 = h.absolutePos(new BlockPos(5, 30, 5));
            Arena.Altar a1 = altarAt(level, KEY, p1);
            Arena.Altar a2 = altarAt(level, "kraken", p2);
            h.assertTrue(Arena.altars().contains(a1) && Arena.altars().contains(a2), "listed");
            // 명령어처럼 블록만 바꿈 → 확인 (10초마다) 에서 빠짐
            level.setBlock(p1, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            h.assertTrue(Arena.altars().contains(a1), "still listed until the check");
            Arena.checkAltarBlocks();
            h.assertFalse(Arena.altars().contains(a1), "removed by the block check");
            h.assertTrue(removed.contains(a1), "hook called for the replaced altar");
            h.assertTrue(Arena.altars().contains(a2), "other altar kept");
            // 크리에이티브가 부숨
            h.assertTrue(op.gameMode.destroyBlock(p2), "creative breaks the altar");
            h.assertFalse(Arena.altars().contains(a2), "removed when broken");
            h.assertTrue(removed.contains(a2), "hook called for the broken altar");
            LOG.info("[arena-test] OK removed altars leave the list (block check · creative break) + hook");
            level.setBlock(p1, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } finally {
            Arena.setAltarRemovedHook(prev);
        }
        RpgGearTests.done(h, op);
    }

    /** 짓기 거절: 다른 제단의 필드와 겹침 · 세계 높이 끝에 닿음 (까닭을 알려 줌) */
    @GameTest(maxTicks = 20)
    public void overlappingAltarIsRefused(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos p1 = new BlockPos(41_000, 150, 41_000);
        BlockPos p2 = p1.offset(20, 0, 0);
        String hold = loadArea(level, p1, 30);
        Arena.Altar a = altarAt(level, KEY, p1);
        Arena.Altar b = altarAt(level, "kraken", p2);
        Arena.Altar low = altarAt(level, "kraken", new BlockPos(p1.getX() + 400, level.getMinY() + 3, p1.getZ()));
        try {
            String why = Arena.buildProblem(b);
            h.assertTrue(why != null && why.contains("너무 가깝습니다") && why.contains("허수아비 킹"), "overlap problem: " + why);
            h.assertFalse(Arena.buildArena(b, null) || Arena.isBuilding(b), "overlapping altar not built");
            String why2 = Arena.buildProblem(low);
            h.assertTrue(why2 != null && why2.contains("높이가 맞지 않습니다"), "height problem: " + why2);
            h.assertFalse(Arena.buildArena(low, null), "too low not built");
            double gap = Arena.arenaSize(a).radius() + Arena.arenaSize(b).radius() + Rules.get().obj("BOSS").num("nearAltars");
            h.assertTrue(Math.hypot(p2.getX() - p1.getX(), p2.getZ() - p1.getZ()) < gap, "test spots really overlap");
            LOG.info("[arena-test] OK build refused: {} / {}", why, why2);
        } finally {
            forget(level, a);
            forget(level, b);
            forget(level, low);
            Ticking.releaseArea(hold);
        }
        h.succeed();
    }
}
