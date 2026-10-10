package mmorpg.fabric.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.npc.NpcData;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 보스 제단 · 필드의 이벤트 (arena.js onPlace · onBreakBefore · onBreakAfter · onInteractBefore · bucketBefore · bucketUseBefore · onExplosionBefore)
 * 와 arena 작업 전체의 초기화 ({@link #init} — 제단 블록 · 짓기 · 이벤트).
 * 필드 보호 (운영자 · 크리에이티브는 예외): 지어진 필드 안 (울타리 고리까지, 위로는 끝까지) 블록을 부술 수 없음 · 놓을 수 없음 (놓기 전에 막음,
 * 그래도 놓인 것은 놓은 뒤 되돌리고 돌려줌), 물 · 용암 · 불 · 도끼 껍질 벗기기 · 괭이 · 삽 · 가위 · 뼛가루 못 씀, 빈 양동이로 필드 안 물 · 용암을 못 뜸,
 * 폭발이 필드 블록을 부수지 않음 (ServerExplosionMixin).
 * 자바에서 달라진 점: 양동이 · 수련은 자바에서 블록 누르기가 아니라 아이템 쓰기 (UseItemCallback) 로 오므로 보는 곳 (광선) 으로 판단,
 * 막은 뒤 클라이언트가 미리 줄인 아이템 개수를 되돌려 보냄.
 */
public final class ArenaGuard {
    private ArenaGuard() {}

    /** 필드를 바꾸는 도구 (필드 안에서는 운영자만): 물·용암·불, 껍질 벗기기·밭 갈기·길 만들기, 가위, 뼛가루 */
    static final Pattern TERRAIN_TOOLS = Pattern.compile("^minecraft:(.*_bucket|flint_and_steel|fire_charge|.*_axe|.*_hoe|.*_shovel|shears|bone_meal)$");

    private static boolean initialized;

    /** Mmorpg.onInitialize (Ticking.init 바로 뒤): 제단 블록 9종 · 짓기 · 필드 보호 이벤트 */
    public static void init() {
        if (initialized) return;
        initialized = true;
        AltarBlocks.init();
        ArenaBuild.init();
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> onBreakBefore(level, player, pos, state));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> onBreakAfter(level, pos, state));
        UseBlockCallback.EVENT.register(ArenaGuard::onUseBlock);
        UseItemCallback.EVENT.register(ArenaGuard::onUseItem);
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(BedrockText.toJava(text)));
    }

    // ------------------------------------------------------------ 제단 놓기 · 누르기

    /** 제단 블록을 플레이어가 놓음 (애드온 onPlace): 목록에 넣고 놓은 사람에게 알림 + 다음 틱에 "보스 필드 만들기" 창 (관리자만) */
    public static void onAltarPlaced(ServerLevel level, BlockPos pos, String key, ServerPlayer player) {
        Arena.Altar a = Arena.addAltar(key, NpcData.dimId(level), pos);
        if (player == null || player.isRemoved()) return;
        say(player, "§6[보스 제단] §f" + Arena.BOSSES.obj(key).str("name") + " 제단을 놓았습니다 §7(Lv." + ArenaBuild.levelText(key) + " · 권장 3~4명)");
        Tasks.later(1, () -> {
            if (!player.isRemoved()) Arena.askBuild(player, a);
        });
    }

    /** 제단 블록을 누름 (애드온 rpg:boss_altar onPlayerInteract → bosses.js onAltarTouch): 목록에 없으면 넣고 보스 엔진의 훅으로 */
    public static void useAltar(ServerPlayer sp, ServerLevel level, BlockPos pos, String key) {
        String dim = NpcData.dimId(level);
        Arena.Altar a = Arena.altarAt(dim, pos);
        if (a == null) a = Arena.addAltar(key, dim, pos);
        try {
            Arena.onAltarUse.accept(sp, a);
        } catch (RuntimeException e) {
            mmorpg.fabric.Mmorpg.LOG.warn("[mmorpg] altar use", e);
        }
    }

    // ------------------------------------------------------------ 부수기

    /** 부수기 직전 (애드온 onBreakBefore): 관리자가 아니면 제단 · 지어진 필드 안 블록은 막음 */
    static boolean onBreakBefore(Level level, Player player, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer sp) || Arena.canManage(sp)) return true;
        if (AltarBlocks.keyOf(state) != null || Arena.builtArenaAt(NpcData.dimId(level), pos) != null) {
            Tasks.later(1, () -> {
                if (!sp.isRemoved()) Notify.warn(sp, "§c보스 필드는 부술 수 없습니다");
            });
            return false;
        }
        return true;
    }

    /** 부순 뒤 (애드온 onBreakAfter): 제단이었으면 목록에서 뺌 (훅으로 보스 엔진이 싸움을 정리) */
    static void onBreakAfter(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || AltarBlocks.keyOf(state) == null) return;
        Arena.Altar a = Arena.altarAt(NpcData.dimId(level), pos);
        if (a != null) Arena.removeAltar(a);
    }

    // ------------------------------------------------------------ 블록 누르기 (놓기 · 도구)

    private static boolean hasItems(Player p) {
        return !p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty();
    }

    /** 클라이언트가 미리 바꾼 가방을 서버 것으로 되돌림 (막은 놓기 · 양동이) */
    private static void resync(ServerPlayer sp) {
        sp.containerMenu.sendAllDataToRemote();
    }

    /**
     * 블록을 누르기 직전 (애드온 onInteractBefore): 필드 안에 블록 놓기 · 필드 바꾸는 도구는 막음 (놓은 뒤 되돌리면 상자 속 물건 등이 사라질 수 있어서 미리).
     * 제단: 웅크린 채 물건을 들면 바닐라는 블록 쓰기를 건너뛰므로 여기서 제단 누르기로 (웅크린 관리자의 관리 창)
     */
    static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        BlockState bs = level.getBlockState(pos);
        String altar = AltarBlocks.keyOf(bs);
        if (altar != null) {
            if (player.isSecondaryUseActive() && hasItems(player)) {
                if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) useAltar(sp, sl, pos, altar);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS; // 제단 누르기 (블록의 useItemOn) 는 그대로
        }
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) return InteractionResult.PASS;
        Item item = stack.getItem();
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        boolean tool = TERRAIN_TOOLS.matcher(id).matches();
        boolean placeable = !tool && item instanceof BlockItem;
        if (!tool && !placeable) return InteractionResult.PASS;
        if (Arena.canManage(sp)) return InteractionResult.PASS;
        String dim = NpcData.dimId(level);
        // 누른 블록과 그 면 쪽 칸 (물·블록이 놓이는 자리)
        BlockPos side = pos.relative(hit.getDirection());
        if (Arena.builtArenaAt(dim, pos) == null && Arena.builtArenaAt(dim, side) == null) return InteractionResult.PASS;
        Notify.warn(sp, tool ? "§c보스 필드 안에서는 이 도구를 쓸 수 없습니다" : "§c보스 필드 안에는 블록을 놓을 수 없습니다");
        resync(sp);
        return InteractionResult.FAIL;
    }

    /**
     * 아이템 쓰기 직전 (애드온 bucketUseBefore · 자바는 양동이 · 수련을 이렇게 씀): 보는 곳 (광선) 이 지어진 필드 안이면 막음.
     * 빈 양동이 = 필드 안의 물 · 용암 (크라켄의 물구덩이 · 요르문간드의 해자) 을 못 뜸, 물 · 용암 양동이 = 도구, 수련 · 개구리알 = 블록 놓기
     */
    static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();
        boolean bucket = item instanceof BucketItem;
        boolean placeable = !bucket && item instanceof BlockItem;
        if (!bucket && !placeable) return InteractionResult.PASS;
        if (Arena.canManage(sp)) return InteractionResult.PASS;
        boolean empty = item == Items.BUCKET;
        Vec3 eye = sp.getEyePosition();
        Vec3 to = eye.add(sp.getViewVector(1F).scale(sp.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.OUTLINE, bucket && !empty ? ClipContext.Fluid.NONE : ClipContext.Fluid.SOURCE_ONLY,
                CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if ((empty || placeable) && level.getFluidState(pos).isEmpty()) return InteractionResult.PASS; // 액체가 아니면 그대로
        String dim = NpcData.dimId(level);
        BlockPos side = pos.relative(hit.getDirection());
        if (Arena.builtArenaAt(dim, pos) == null && Arena.builtArenaAt(dim, side) == null && Arena.builtArenaAt(dim, pos.above()) == null) return InteractionResult.PASS;
        Notify.warn(sp, empty ? "§c보스 필드의 물은 뜰 수 없습니다" : placeable ? "§c보스 필드 안에는 블록을 놓을 수 없습니다" : "§c보스 필드 안에서는 이 도구를 쓸 수 없습니다");
        resync(sp);
        return InteractionResult.FAIL;
    }

    /**
     * 블록 아이템을 놓은 뒤 (BlockItemMixin — 애드온 onPlace 의 되돌리기): 미리 막지 못하고 지어진 필드 안에 놓였으면 (관리자가 아니면)
     * 공기로 되돌리고 그 블록의 아이템을 돌려줌
     */
    public static void afterPlace(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide() || !(ctx.getPlayer() instanceof ServerPlayer sp) || Arena.canManage(sp)) return;
        BlockPos pos = ctx.getClickedPos();
        if (Arena.builtArenaAt(NpcData.dimId(level), pos) == null) return;
        BlockState placed = level.getBlockState(pos);
        if (placed.isAir()) return;
        ItemStack back = new ItemStack(placed.getBlock().asItem());
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (!back.isEmpty()) Notify.give(sp, back);
        resync(sp);
        Notify.warn(sp, "§c보스 필드 안에는 블록을 놓을 수 없습니다");
    }

    // ------------------------------------------------------------ 폭발

    /** 폭발이 부술 블록 중 지어진 필드 안의 것을 뺌 (애드온 onExplosionBefore — ServerExplosionMixin) */
    public static List<BlockPos> filterExplosion(ServerLevel level, List<BlockPos> list) {
        if (list == null || list.isEmpty()) return list;
        boolean any = false;
        for (Arena.Altar a : Arena.altars()) {
            if (a.built) {
                any = true;
                break;
            }
        }
        if (!any) return list;
        String dim = NpcData.dimId(level);
        List<BlockPos> keep = new ArrayList<>(list.size());
        for (BlockPos p : list) if (Arena.builtArenaAt(dim, p) == null) keep.add(p);
        return keep.size() == list.size() ? list : keep;
    }
}
