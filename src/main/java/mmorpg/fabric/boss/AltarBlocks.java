package mmorpg.fabric.boss;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import mmorpg.core.Mobs;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gear.GearItems;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 보스 제단 블록 9종 rpg:altar_&lt;보스 키&gt; (행동 팩 blocks/rpg_altars/*.json 그대로): 꽉 찬 블록 · 옆 · 위 · 아래 텍스처, 빛 10,
 * 서바이벌에서 부술 수 없음 (자바: 부수는 시간 -1 — 크리에이티브는 부숨), 폭발에 안 부서짐, 피스톤에 밀리지 않음 (부술 수 없는 블록 — 제단 목록과 자리가 어긋나지 않게),
 * 이름 "&lt;보스 이름&gt; 제단" (lang block.rpg.altar_*), 크리에이티브 건축 탭 + RPG 장비 탭.
 * 누르면 (애드온 rpg:boss_altar 블록 부품의 onPlayerInteract) Arena.onAltarUse — 도전 창 · 체력 보기 · 웅크린 관리자의 관리 창은 보스 엔진이.
 * 놓으면 (playerPlaceBlock) 제단 목록에 넣고 놓은 관리자에게 "보스 필드 만들기" 창 ({@link ArenaGuard#onAltarPlaced}).
 */
public final class AltarBlocks {
    private AltarBlocks() {}

    private static final Map<String, AltarBlock> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, BlockItem> ITEMS = new LinkedHashMap<>();

    /** 제단 블록 하나 (보스 키) */
    public static final class AltarBlock extends Block {
        public final String key;

        AltarBlock(String key, BlockBehaviour.Properties props) {
            super(props);
            this.key = key;
        }

        /** 물건을 들고 누름 (빈손도 여기로 옴 — 웅크린 채 물건을 들면 바닐라가 건너뛰므로 ArenaGuard 의 블록 누르기 이벤트가 받음) */
        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) ArenaGuard.useAltar(sp, sl, pos, key);
            return InteractionResult.SUCCESS;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) ArenaGuard.useAltar(sp, sl, pos, key);
            return InteractionResult.SUCCESS;
        }

        /** 플레이어가 놓음 (애드온 playerPlaceBlock) */
        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level instanceof ServerLevel sl) ArenaGuard.onAltarPlaced(sl, pos, key, placer instanceof ServerPlayer sp ? sp : null);
        }
    }

    /**
     * 블록 속성 (행동 팩: light_emission 10, destructible_by_mining false, destructible_by_explosion false).
     * 부수는 시간 -1 (기반암처럼) 이라 피스톤도 밀지 못함 (자바 피스톤은 부술 수 없는 블록을 밀지 않음)
     */
    private static BlockBehaviour.Properties props(ResourceKey<Block> id) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .sound(SoundType.STONE)
                .strength(Block.INDESTRUCTIBLE, 3600000.0F)
                .lightLevel(s -> 10)
                .noLootTable()
                .setId(id);
    }

    /** ArenaGuard.init (레지스트리가 얼기 전) */
    static void init() {
        for (String key : Arena.BOSSES.keys()) {
            String path = Mobs.altarId(key).substring((Mmorpg.NS + ":").length());
            ResourceKey<Block> bk = ResourceKey.create(Registries.BLOCK, Mmorpg.rpg(path));
            AltarBlock block = Registry.register(BuiltInRegistries.BLOCK, bk, new AltarBlock(key, props(bk)));
            ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, Mmorpg.rpg(path));
            BlockItem item = new BlockItem(block, new Item.Properties().setId(ik).useBlockDescriptionPrefix());
            Registry.register(BuiltInRegistries.ITEM, ik, item);
            item.registerBlocks(Item.BY_BLOCK, item); // block.asItem() (집어 들기 · 되돌려 줄 때)
            BLOCKS.put(key, block);
            ITEMS.put(key, item);
        }
        // 크리에이티브: 건축 탭 (애드온 menu_category construction) + RPG 장비 탭
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ITEMS.values().forEach(output::accept));
        CreativeModeTabEvents.modifyOutputEvent(GearItems.TAB).register(output -> ITEMS.values().forEach(output::accept));
    }

    /** 보스 키의 제단 블록 (없으면 null) */
    public static AltarBlock block(String key) {
        return BLOCKS.get(key);
    }

    /** 제단 블록 모두 (보스 키 → 블록) */
    public static Map<String, AltarBlock> blocks() {
        return Collections.unmodifiableMap(BLOCKS);
    }

    /** 제단 블록 아이템 (/boss altar — 없는 키면 빈 것) */
    public static ItemStack item(String key, int amount) {
        BlockItem it = ITEMS.get(key);
        return it == null || amount <= 0 ? ItemStack.EMPTY : new ItemStack(it, amount);
    }

    /** 이 블록 상태가 제단이면 그 보스 키 (아니면 null) */
    public static String keyOf(BlockState s) {
        return s != null && s.getBlock() instanceof AltarBlock a ? a.key : null;
    }
}
