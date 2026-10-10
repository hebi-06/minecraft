package mmorpg.fabric.tree;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;

/**
 * 세계수의 블록 가리기 (worldtree.js 의 SOFT · PLANT · CROPS · TERRAIN · KEPT · DIRT · ROOTED · AQUATIC 목록과 isSoft · isTrunk · passable · isGround ·
 * isNatural · isKept · isLava · isRooted · naturalGround · halfOf) 와 나무 블록 (베드락 이름 · 상태) → 자바 블록 상태.
 * <p>
 * 목록은 애드온 그대로, 이름만 자바로: deadbush → dead_bush, snow_layer → snow (눈 층), snow → snow_block, reeds → sugar_cane, waterlily → lily_pad,
 * dirt_with_roots → rooted_dirt, beetroot → beetroots, small_dripleaf_block → small_dripleaf, magma → magma_block, hardened_clay → terracotta,
 * flowing_water → water (자바는 흐르는 물도 같은 블록). 자바에만 있는 마디 블록을 같은 무리로 더함: kelp_plant · tall_seagrass · big_dripleaf_stem ·
 * twisting_vines_plant · weeping_vines_plant · cave_vines_plant · attached_melon_stem · attached_pumpkin_stem (베드락은 한 블록 이름에 상태),
 * 26.x 의 golden_dandelion (민들레와 같은 꽃). 공기는 cave_air · void_air 도 (isAir).
 * 물에 잠김: 베드락은 블록과 따로 (isWaterlogged · AQUATIC) — 자바는 블록 상태 안 (waterlogged · 다시마 · 해초의 물) 이라 상태를 그대로 적고 되돌리면 됨.
 */
public final class TreeBlocks {
    private TreeBlocks() {}

    private static Set<String> ids(String list) {
        Set<String> out = new HashSet<>();
        for (String s : list.trim().split("\\s+")) if (!s.isEmpty()) out.add("minecraft:" + s);
        return Set.copyOf(out);
    }

    /** 부드러운 블록: 잎 · 열매 · 덩굴 · 깔개를 놓아도 되는 곳 (풀 · 꽃 · 눈 · 덩굴 등, 공기 · 다른 나무의 잎 · 묘목 포함) */
    static final Set<String> SOFT = ids("""
            air cave_air void_air short_grass tall_grass fern large_fern dead_bush snow vine glow_lichen resin_clump leaf_litter wildflowers pink_petals
            moss_carpet pale_moss_carpet dandelion golden_dandelion poppy blue_orchid allium azure_bluet red_tulip orange_tulip white_tulip pink_tulip oxeye_daisy
            cornflower lily_of_the_valley torchflower closed_eyeblossom open_eyeblossom cactus_flower sunflower lilac rose_bush peony sweet_berry_bush bush
            short_dry_grass tall_dry_grass brown_mushroom red_mushroom""");
    /** 들에 자라는 식물 (땅을 찾을 때 지나감, 줄기 · 뿌리 자리면 바꿈): 대나무 · 사탕수수 · 선인장 · 다시마 · 해초 · 버섯 등 */
    static final Set<String> PLANT = ids("""
            bamboo bamboo_sapling sugar_cane cactus kelp kelp_plant seagrass tall_seagrass sea_pickle lily_pad big_dripleaf big_dripleaf_stem small_dripleaf
            cave_vines cave_vines_plant hanging_roots azalea flowering_azalea spore_blossom firefly_bush cocoa twisting_vines twisting_vines_plant weeping_vines
            weeping_vines_plant nether_sprouts crimson_roots warped_roots crimson_fungus warped_fungus pitcher_plant pale_hanging_moss shelf_mushroom""");
    /** 밭 작물 (땅을 찾을 때 지나가지만 바꾸지 않음) */
    static final Set<String> CROPS = ids("wheat carrots potatoes beetroots melon_stem pumpkin_stem attached_melon_stem attached_pumpkin_stem torchflower_crop pitcher_crop nether_wart");
    /** 자연 지형 (줄기 · 뿌리 자리면 바꿈) */
    static final Set<String> TERRAIN = ids("""
            grass_block dirt coarse_dirt podzol mycelium rooted_dirt mud muddy_mangrove_roots mangrove_roots clay gravel sand red_sand moss_block pale_moss_block
            snow_block powder_snow ice packed_ice blue_ice frosted_ice stone granite diorite andesite deepslate tuff calcite dripstone_block pointed_dripstone
            sandstone red_sandstone smooth_basalt sculk sculk_vein end_stone netherrack water brown_mushroom_block red_mushroom_block mushroom_stem""");
    /** 자연에 있지만 그대로 두는 블록 (광석 · 흑요석 · 자수정 · 테라코타 · 수상한 모래 등): 건물로 치지 않음 */
    static final Set<String> KEPT = ids("""
            ancient_debris obsidian crying_obsidian magma_block amethyst_block budding_amethyst amethyst_cluster large_amethyst_bud medium_amethyst_bud
            small_amethyst_bud terracotta suspicious_sand suspicious_gravel bedrock resin_block shroomlight""");
    /** 흙 종류 (덤불 · 진달래가 자랄 수 있는 땅) */
    static final Set<String> DIRT = ids("grass_block dirt coarse_dirt podzol mycelium rooted_dirt moss_block pale_moss_block mud muddy_mangrove_roots");
    /** 밑이 받쳐 줘야 서 있는 풀 · 꽃 · 작물 · 줄기식물: 밑 블록을 바꾸면 떨어지므로 먼저 치우고 기록 */
    static final Set<String> ROOTED = ids("""
            short_grass tall_grass fern large_fern dead_bush dandelion golden_dandelion poppy blue_orchid allium azure_bluet red_tulip orange_tulip white_tulip
            pink_tulip oxeye_daisy cornflower lily_of_the_valley torchflower closed_eyeblossom open_eyeblossom sunflower lilac rose_bush peony sweet_berry_bush
            bush short_dry_grass tall_dry_grass wildflowers pink_petals brown_mushroom red_mushroom bamboo bamboo_sapling sugar_cane cactus cactus_flower kelp
            kelp_plant seagrass tall_seagrass sea_pickle azalea flowering_azalea firefly_bush pitcher_plant big_dripleaf big_dripleaf_stem small_dripleaf wheat
            carrots potatoes beetroots melon_stem pumpkin_stem attached_melon_stem attached_pumpkin_stem torchflower_crop pitcher_crop nether_wart crimson_roots
            warped_roots crimson_fungus warped_fungus nether_sprouts twisting_vines twisting_vines_plant lily_pad""");
    /** 같은 기둥으로 치는 자바 마디 블록 → 무리 이름 (베드락은 한 이름 — 다시마 · 뒤틀린 덩굴 · 큰 흘림잎 줄기) */
    private static final Map<String, String> FAMILY = Map.of(
            "minecraft:kelp_plant", "minecraft:kelp",
            "minecraft:twisting_vines_plant", "minecraft:twisting_vines",
            "minecraft:big_dripleaf_stem", "minecraft:big_dripleaf",
            "minecraft:bamboo_sapling", "minecraft:bamboo");

    /** 블록 이름 (minecraft:stone) */
    public static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    /** 기둥 무리 (자바 마디 블록은 머리 블록 이름으로) */
    static String family(String id) {
        return FAMILY.getOrDefault(id, id);
    }

    /** 부드러운 블록 (공기 · 풀 · 꽃 · 다른 나무의 잎 등): 잎 · 열매 · 덩굴을 놓아도 됨 */
    public static boolean isSoft(BlockState b) {
        if (b == null) return false;
        if (b.isAir()) return true;
        String id = id(b);
        return SOFT.contains(id) || id.endsWith("_leaves") || id.endsWith("_sapling") || id.endsWith("_propagule");
    }

    /** 다른 나무의 줄기 · 큰 버섯 · 세계수 줄기 (땅을 찾을 때 지나감). 사방이 껍질인 나무 블록 (_wood) 은 사람이 만든 것이라 땅으로 봄 */
    static boolean isTrunk(String id) {
        return id.equals("minecraft:pale_oak_wood") || id.endsWith("_log") || id.endsWith("_stem") || id.endsWith("mangrove_roots") || id.endsWith("_mushroom_block");
    }

    /** 액체 (물 · 용암 블록 — 물에 잠긴 블록은 아님: 베드락 isLiquid) */
    static boolean isLiquid(BlockState b) {
        return b.liquid();
    }

    /** 땅이 아님: 부드러운 블록 · 액체 · 다른 나무 줄기 · 식물 · 작물 */
    static boolean passable(BlockState b) {
        if (isSoft(b) || isLiquid(b)) return true;
        String id = id(b);
        return isTrunk(id) || PLANT.contains(id) || CROPS.contains(id);
    }

    /** 땅 (나무를 세울 수 있는 곳) */
    public static boolean isGround(BlockState b) {
        return b != null && !passable(b);
    }

    /**
     * 줄기 · 뿌리가 바꿔도 되는 블록 (자연): 공기 · 부드러운 블록 · 식물 · 자연 지형 · 물 · 다른 나무의 통나무 · 다른 세계수의 줄기.
     * 껍질 벗긴 통나무 · 사방이 껍질인 나무 블록 (_wood, _hyphae) · 판자는 사람이 만든 것으로 봄
     */
    public static boolean isNatural(BlockState b) {
        if (b == null) return false;
        if (b.isAir() || isSoft(b)) return true;
        String id = id(b);
        if (TERRAIN.contains(id) || PLANT.contains(id) || id.equals("minecraft:pale_oak_wood")) return true;
        return !id.contains("stripped_") && (id.endsWith("_log") || id.equals("minecraft:crimson_stem") || id.equals("minecraft:warped_stem"));
    }

    /** 자연에 있지만 그대로 두는 블록 (건물로 치지 않음) */
    public static boolean isKept(String id) {
        return KEPT.contains(id) || id.endsWith("_ore") || id.contains("coral") || id.startsWith("minecraft:infested_")
                || (id.endsWith("_terracotta") && !id.contains("glazed"));
    }

    /** 용암 (흐르는 용암도) */
    public static boolean isLava(BlockState b) {
        return b != null && b.is(Blocks.LAVA);
    }

    /** 밑이 받쳐 줘야 서 있는 식물 */
    static boolean isRooted(BlockState b) {
        String id = id(b);
        return ROOTED.contains(id) || id.endsWith("_sapling") || id.endsWith("_propagule");
    }

    /** 깔개 · 덤불을 올려도 되는 땅: 자연 지형 (물 빼고) · 통나무 · 세계수 뿌리 */
    static boolean naturalGround(BlockState b) {
        String id = id(b);
        return (TERRAIN.contains(id) && !isLiquid(b)) || isTrunk(id);
    }

    /** 두 칸짜리 풀 · 꽃이면 윗 반쪽인지 (true/false), 아니면 null (베드락 upper_block_bit — 자바 half) */
    static Boolean halfOf(BlockState b) {
        if (!b.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return null;
        return b.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    /** 물을 품은 블록 (물에 잠긴 블록 · 다시마 · 해초 — 치울 때 물로 채움) */
    static boolean wet(BlockState b) {
        return b.getFluidState().getType() == Fluids.WATER;
    }

    // ------------------------------------------------------------ 나무 블록 (베드락 이름 · 상태 → 자바)

    private static final Map<String, BlockState> TREE = new LinkedHashMap<>();

    /**
     * 설계의 블록 (베드락 이름 · 상태) → 자바 블록 상태: 창백한 참나무 목재 · 송진 블록 · 이끼 블록 · 진달래 잎 · 꽃 핀 진달래 잎 (떨어지지 않게 persistent) ·
     * 버섯불 · 발광 열매 덩굴 마디 (cave_vines_plant berries) · 끝 (cave_vines berries age 25 — 더 자라지 않음) · 포자 꽃 · 이끼 깔개 · 반딧불이 덤불 · 꽃 핀 진달래.
     * 설계에 없는 이름이면 IllegalArgumentException
     */
    public static BlockState state(String bedrock) {
        synchronized (TREE) {
            if (TREE.isEmpty()) {
                TREE.put(TreePlan.WOOD, Blocks.PALE_OAK_WOOD.defaultBlockState());
                TREE.put(TreePlan.RESIN, Blocks.RESIN_BLOCK.defaultBlockState());
                TREE.put(TreePlan.MOSS, Blocks.MOSS_BLOCK.defaultBlockState());
                TREE.put(TreePlan.LEAF, with(Blocks.AZALEA_LEAVES.defaultBlockState(), BlockStateProperties.PERSISTENT, true));
                TREE.put(TreePlan.BLOOM, with(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), BlockStateProperties.PERSISTENT, true));
                TREE.put(TreePlan.FRUIT, Blocks.SHROOMLIGHT.defaultBlockState());
                TREE.put(TreePlan.VINE, with(Blocks.CAVE_VINES_PLANT.defaultBlockState(), BlockStateProperties.BERRIES, true));
                TREE.put(TreePlan.VINE_TIP, with(with(Blocks.CAVE_VINES.defaultBlockState(), BlockStateProperties.BERRIES, true), BlockStateProperties.AGE_25, 25));
                TREE.put(TreePlan.BLOSSOM, Blocks.SPORE_BLOSSOM.defaultBlockState());
                TREE.put(TreePlan.CARPET, Blocks.MOSS_CARPET.defaultBlockState());
                TREE.put(TreePlan.FIREFLY, Blocks.FIREFLY_BUSH.defaultBlockState());
                TREE.put(TreePlan.AZALEA, Blocks.FLOWERING_AZALEA.defaultBlockState());
            }
            BlockState s = TREE.get(bedrock);
            if (s == null) throw new IllegalArgumentException("not a world tree block: " + bedrock);
            return s;
        }
    }

    /** 설계의 블록 → 자바 블록 (지금도 우리가 놓은 블록인지 볼 때 — 잎 거리 · 덩굴 나이 같은 상태는 보지 않음) */
    public static Block block(String bedrock) {
        return state(bedrock).getBlock();
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, T v) {
        return s.hasProperty(p) ? s.setValue(p, v) : s;
    }

    // ------------------------------------------------------------ 기록의 팔레트 항목 ([블록 이름, {상태: 값}] — 자바 이름 · 상태 그대로)

    /** 블록 상태 → 팔레트 항목 [이름, {상태: 값}] (물에 잠김도 상태 안) */
    public static List<Object> entryOf(BlockState s) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Property<?> p : s.getProperties()) props.put(p.getName(), valueName(s, p));
        return List.of(id(s), props);
    }

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    /** 팔레트 항목 → 블록 상태 (알 수 없는 블록 — 다른 버전에서 이름이 바뀜 등 — 이면 null. 틀린 상태는 버리고 기본값 — 애드온 permOf 처럼) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState stateOf(String name, Map<?, ?> props) {
        Identifier rl = Identifier.tryParse(name);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return null;
        BlockState s = BuiltInRegistries.BLOCK.getValue(rl).defaultBlockState();
        if (props != null) {
            for (Map.Entry<?, ?> e : props.entrySet()) {
                Property<?> p = s.getBlock().getStateDefinition().getProperty(String.valueOf(e.getKey()));
                if (p == null) continue;
                Optional<?> v = p.getValue(String.valueOf(e.getValue()));
                if (v.isEmpty()) continue;
                s = s.setValue((Property) p, (Comparable) v.get());
            }
        }
        return s;
    }
}
