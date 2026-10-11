package mmorpg.fabric.tree;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.MenuForm;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 세계수 (v2.5.1, worldtree.js): /worldtree · /세계수 · /segyesu [plant|undo] [높이 40~96] (운영자) — 바라보는 곳에 거대한 나무 (이그드라실) 를 심음.
 * <ul>
 * <li>설계 {@link TreePlan} (애드온과 칸마다 같음 · 지문 fp), 블록 가리기 · 나무 블록 {@link TreeBlocks}, 기록 {@link TreeLog}</li>
 * <li>심기 전 살핌: 네더 · 세계 높이 · 보스 필드 · 불러오지 않은 곳 · 물 속 → 바로 거절, 그다음 (여러 틱에 나눠) 둘레 용암 · 줄기 · 뿌리 자리의 건물 · 설치물 ·
 * 잎 자리가 막힌 정도 (산 · 동굴 안) → 거절하며 까닭을 알림. 확인 창 (MenuForm) 을 여는 동안은 자리를 놓아주고 심을 때 다시 붙잡음</li>
 * <li>놓는 법 (wood · leaf · hang · carpet · bush), 받침을 잃는 풀 · 꽃 · 대나무 등과 두 칸짜리 풀 · 꽃의 다른 반쪽은 먼저 치우고 기록 (되돌릴 때 다시 놓음)</li>
 * <li>뿌리부터 단계마다 자라남: 한 틱에 perTick 개 · msPerTick 밀리초까지 (틱 사이가 lagMs 넘게 벌어지면 0.6배씩 줄여 minPerTick 까지, 괜찮으면 40개씩 늘림),
 * 단계 사이 stagePause 틱 쉬며 소리 · 파티클, 자라는 칸에 선 사람 · 동물 · 이 모드의 몬스터를 뿌리 끝 밖 땅으로 옮김</li>
 * <li>불러오기: 살피기 · 자라기 · 되돌리기 동안 나무를 덮는 네모를 {@link Ticking#holdArea} 로 붙잡고 잎 끝까지의 원에 걸친 청크가 다 불러와질 때까지 (loadWait 틱) 기다림</li>
 * <li>기록: 세계 동적 속성 (최근 keep 그루 + 칸마다 원래 블록), 자라는 동안 saveEvery 틱마다 · 단계마다 저장</li>
 * <li>되돌리기 (확인 창): 기록에서 놓은 칸만, 지금도 우리가 놓은 블록일 때만 원래 블록으로, 늦게 놓은 것부터. 설계가 달라졌으면 하지 않음,
 * 못 한 곳은 기록에 남겨 다시 하면 이어서</li>
 * </ul>
 * 자바에서 달라진 점: 설계 계산은 서버 스레드 밖의 일꾼 스레드에서 (애드온은 system.runJob), 틱킹 영역 대신 {@link Ticking} 의 청크 표
 * (서버가 꺼지면 저절로 풀려 "남은 영역 치우기" 가 없음), 블록은 이웃을 건드리지 않고 놓음 (UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE — 풀 · 꽃이 떨어져 아이템이 되지 않게),
 * 관전 모드 플레이어는 옮기지 않음 (블록에 끼지 않음), 한글 이름 /세계수 는 늘 함께 등록 (Brigadier 는 한글 리터럴을 받음).
 */
public final class WorldTree {
    private WorldTree() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-worldtree");
    private static final Obj CFG = Rules.get().obj("WORLD_TREE");
    /** 놓을 때: 클라이언트에 보냄 · 이웃 모양을 건드리지 않음 (받침을 잃은 풀 · 꽃이 아이템으로 떨어지지 않게 — 치울 것은 먼저 치움) */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    private static final String NOT_LOADED = "나무가 자랄 곳이 다 불러와지지 않았습니다";
    private static final Map<String, String> DIM_NAMES = Map.of("minecraft:overworld", "오버월드", "minecraft:the_end", "엔드", "minecraft:the_nether", "네더");

    // ------------------------------------------------------------ 시험 훅

    /** 시험용: 한 틱에 놓는 수 (0 = 설정 perTick, 되돌리기는 두 배) */
    public static volatile int perTickOverride = 0;
    /** 시험용 (사진첩): 이 단계 번호 앞에서 자라기를 멈춤 (null = 멈추지 않음 — 멈춘 자리는 {@link #heldAt()}) */
    public static volatile IntPredicate holdBeforeStage;

    // ------------------------------------------------------------ 상태

    /** 지금 자라는 (되돌리는) 나무: 한 번에 하나 */
    private static Job job;
    /** 설계 · 살핌 · 불러오기 중인 일의 표 (0 = 없음, 한 번에 하나): 끝날 때 자기 표일 때만 지움 */
    private static int preparing;
    private static int prepSeq;
    /** 확인 창이 열린 사람 (시험용 — treeBusy) */
    private static final Set<UUID> ASKING = new HashSet<>();
    private static int idc;
    /** 명령 이름 (안내 글): 영어 이름과, 한글 이름이 등록되었으면 그 이름 */
    private static String cmdName = "/worldtree";
    private static String cmdAlias;
    /** 여러 틱에 걸친 기다림 (설계 계산 · 불러오기 · 살핌) */
    private static final List<Waiter> WAITERS = new ArrayList<>();
    private static ExecutorService exec;
    private static Result last;

    /** 끝난 일의 결과 (시험용): 자라기 = placed · skipped · errors · moved · movedMobs, 되돌리기 = restored · changed · unknown · failed */
    public record Result(boolean undo, String id, int placed, int skipped, int errors, int moved, int movedMobs, int restored, int changed, int unknown, int failed) {}

    /** 지금 일 (시험용) */
    public record JobInfo(boolean undo, String id, BlockPos base, TreePlan plan, int stage, int placed) {}

    private static final class Job {
        final boolean undo;
        final String id;
        final TreeLog.Rec rec;
        final ServerLevel level;
        final BlockPos base;
        final TreePlan plan;
        final TreeLog log;
        final ServerPlayer player;
        String area;
        int pause;
        int budget;
        long lastT;
        // 자라기
        int[] starts;
        int si;
        int oi;
        int held = -1;
        int placed;
        int skipped;
        int errors;
        int moved;
        int movedMobs;
        long saveAt;
        // 되돌리기
        List<TreePlan.Op> ops;
        int gi;
        int restored;
        int changed;
        int unknown;
        int failed;

        Job(boolean undo, String id, TreeLog.Rec rec, ServerLevel level, BlockPos base, TreePlan plan, TreeLog log, ServerPlayer player, String area) {
            this.undo = undo;
            this.id = id;
            this.rec = rec;
            this.level = level;
            this.base = base;
            this.plan = plan;
            this.log = log;
            this.player = player;
            this.area = area;
        }
    }

    // ------------------------------------------------------------ 설정

    private static int cfgInt(String k) {
        return (int) CFG.num(k);
    }

    private static int perTick() {
        int o = perTickOverride;
        return o > 0 ? o : cfgInt("perTick");
    }

    /** 세계수 이름 (설정 name — 세계수 이그드라실) */
    public static String name() {
        return CFG.str("name");
    }

    // ------------------------------------------------------------ 등록

    /** Mmorpg.onInitialize: 틱 · 명령어 · 서버가 멈추면 정리 */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WorldTree::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> stopped());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ASKING.remove(handler.player.getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    /**
     * /worldtree [plant|undo] [높이] — 영어 이름을 먼저, 그다음 한글 이름 (/세계수) 과 소리 나는 대로 쓴 이름 (/segyesu). 운영자만.
     * 높이는 범위 밖도 받아서 애드온처럼 "높이는 40 ~ 96칸입니다" 로 알림
     */
    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("worldtree", "세계수", "segyesu")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                    .requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(c -> run(c, null, null))
                    .then(Commands.literal("plant").executes(c -> run(c, "plant", null))
                            .then(Commands.argument("height", IntegerArgumentType.integer()).executes(c -> run(c, "plant", IntegerArgumentType.getInteger(c, "height")))))
                    .then(Commands.literal("undo").executes(c -> run(c, "undo", null))
                            .then(Commands.argument("height", IntegerArgumentType.integer()).executes(c -> run(c, "undo", null))));
            d.register(cmd);
        }
        cmdName = "/worldtree";
        cmdAlias = "/세계수";
    }

    private static int run(CommandContext<CommandSourceStack> c, String action, Integer height) {
        ServerPlayer sp = c.getSource().getPlayer();
        if (sp == null) {
            c.getSource().sendFailure(Component.literal("플레이어만 쓸 수 있는 명령어입니다."));
            return 0;
        }
        try {
            if ("undo".equals(action)) undoTree(sp);
            else plantTree(sp, height);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] world tree", e);
            say(sp, "§c오류: " + e);
        }
        return 1;
    }

    /** 되돌리는 명령 안내 */
    private static String undoHint() {
        return "§e" + cmdName + " undo" + (cmdAlias != null ? " §7(또는 " + cmdAlias + " undo)" : "");
    }

    // ------------------------------------------------------------ 도우미

    private static boolean alive(ServerPlayer p) {
        return p != null && !p.isRemoved() && p.connection != null;
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(BedrockText.toJava(text)));
    }

    /** 받침이 있으면 a (이), 없으면 b (가) */
    static String josa(String word, String a, String b) {
        int c = word.charAt(word.length() - 1) - 0xac00;
        return c >= 0 && c < 11172 && c % 28 != 0 ? a : b;
    }

    /** 블록 (불러오지 않은 곳 · 세계 높이 밖이면 null) */
    static BlockState blockAt(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return null;
        return level.getBlockState(p);
    }

    static BlockState blockAt(ServerLevel level, int x, int y, int z) {
        return blockAt(level, new BlockPos(x, y, z));
    }

    private static String dimId(ServerLevel level) {
        return NpcData.dimId(level);
    }

    // ------------------------------------------------------------ 소리 · 파티클 (베드락 이름 → 자바)

    private static final Map<String, SoundEvent> SOUNDS = new LinkedHashMap<>();

    static {
        SOUNDS.put("block.mangrove_roots.place", SoundEvents.MANGROVE_ROOTS_PLACE);
        SOUNDS.put("dig.wood", SoundEvents.WOOD_BREAK);
        SOUNDS.put("place.wood", SoundEvents.WOOD_PLACE);
        SOUNDS.put("place.azalea_leaves", SoundEvents.AZALEA_LEAVES_PLACE);
        SOUNDS.put("chime.amethyst_block", SoundEvents.AMETHYST_BLOCK_CHIME);
        SOUNDS.put("place.moss", SoundEvents.MOSS_PLACE);
        SOUNDS.put("item.bone_meal.use", SoundEvents.BONE_MEAL_USE);
        SOUNDS.put("block.creaking_heart.place", SoundEvents.CREAKING_HEART_PLACE);
        SOUNDS.put("random.levelup", SoundEvents.PLAYER_LEVELUP);
        SOUNDS.put("beacon.activate", SoundEvents.BEACON_ACTIVATE);
    }

    /** 그 자리에서 소리 (fx.js play(dim, 이름, 자리, pitch)) */
    private static void play(ServerLevel level, String bedrock, Vec3 at, double pitch) {
        SoundEvent s = SOUNDS.get(bedrock);
        if (s == null) s = SkillFx.sound(bedrock);
        if (s != null) level.playSound(null, at.x, at.y, at.z, s, SoundSource.BLOCKS, 1F, (float) pitch);
    }

    /** 베드락 바닐라 파티클 (fx.js particle): 뼛가루 반짝임 · 벚꽃잎은 여기서, 나머지는 SkillFx.vanilla */
    private static void particle(ServerLevel level, String bedrock, Vec3 at) {
        switch (bedrock) {
            case "minecraft:crop_growth_emitter" -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 12, 0.6, 0.5, 0.6, 0);
            case "minecraft:cherry_leaves_particle" -> level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y, at.z, 5, 1.5, 0.8, 1.5, 0);
            default -> SkillFx.vanilla(level, bedrock, at);
        }
    }

    /** 나무 둘레 사람 (96칸 안) */
    private static List<ServerPlayer> watchers(ServerLevel level, BlockPos base) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (Math.hypot(p.getX() - base.getX(), p.getZ() - base.getZ()) <= 96) out.add(p);
        return out;
    }

    private static void bigTitle(ServerLevel level, BlockPos base, String title, String subtitle) {
        for (ServerPlayer p : watchers(level, base)) Notify.title(p, title, subtitle);
    }

    // ------------------------------------------------------------ 기다림 (애드온의 await — 서버 틱마다 살핌)

    /** 여러 틱에 걸친 일 (끝나면 true) — 오류가 나면 그 준비를 끝냄 (애드온의 finally) */
    private abstract static class Waiter {
        final Prep prep;

        Waiter(Prep prep) {
            this.prep = prep;
        }

        abstract boolean poll();
    }

    /** 설계 · 살핌 · 불러오기 하나 (preparing 표 · 붙잡은 땅): end() = 애드온의 finally { endPrep(me); releaseArea(area) } */
    private static final class Prep {
        final int token;
        String area;
        boolean ended;

        Prep(int token) {
            this.token = token;
        }

        void end() {
            if (ended) return;
            ended = true;
            if (preparing == token) preparing = 0;
            Ticking.releaseArea(area);
            area = null;
        }
    }

    private static Prep beginPrep() {
        preparing = ++prepSeq;
        return new Prep(preparing);
    }

    private static void tick(MinecraftServer server) {
        if (!WAITERS.isEmpty()) {
            for (Waiter w : new ArrayList<>(WAITERS)) {
                boolean done;
                try {
                    done = w.poll();
                } catch (RuntimeException e) {
                    LOG.warn("[mmorpg] world tree", e);
                    done = true;
                    if (w.prep != null) w.prep.end();
                }
                if (done) WAITERS.remove(w);
            }
        }
        if (job != null) treeTick(server);
    }

    private static synchronized ExecutorService executor() {
        if (exec == null) {
            exec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "mmorpg-worldtree-plan");
                t.setDaemon(true);
                return t;
            });
        }
        return exec;
    }

    /** 세계수 설계를 서버 스레드 밖에서 (애드온 planAsync — 폰에서 한 틱이 길어지지 않게 나눠 계산하던 것), 끝나면 서버 틱에서 then */
    private static void planAsync(int height, int seed, Prep prep, Consumer<TreePlan> then) {
        CompletableFuture<TreePlan> f = CompletableFuture.supplyAsync(() -> TreePlan.plan(height, seed), executor());
        WAITERS.add(new Waiter(prep) {
            @Override
            boolean poll() {
                if (!f.isDone()) return false;
                TreePlan plan;
                try {
                    plan = f.join();
                } catch (RuntimeException e) {
                    LOG.warn("[mmorpg] world tree plan", e);
                    prep.end();
                    return true;
                }
                then.accept(plan);
                return true;
            }
        });
    }

    /** 붙잡은 땅 (이름 — 못 만들었으면 null) · 다 불러와졌는지 */
    private record Area(String name, boolean loaded) {}

    /**
     * 나무 자리를 붙잡고 (나무를 덮는 네모 — 청크 100개 제한 안: 높이 96 이어도 64개) 잎 끝까지의 원에 걸친 청크가 다 불러와질 때까지 기다림
     * (못 만들면 지금 불러온 것만으로 10틱). 붙잡은 땅은 prep 이 맡음
     */
    private static void holdArea(ServerLevel level, BlockPos base, int reach, Prep prep, Consumer<Area> then) {
        int r = reach + 1;
        String name = Ticking.holdArea(level, new Vec3(base.getX(), base.getY(), base.getZ()), r, "t");
        if (name != null) prep.area = name;
        int limit = name != null ? cfgInt("loadWait") : 10;
        WAITERS.add(new Waiter(prep) {
            int t;

            @Override
            boolean poll() {
                boolean loaded = footprintLoaded(level, base, reach);
                if (!loaded && t < limit) {
                    t++;
                    return false;
                }
                then.accept(new Area(name, loaded));
                return true;
            }
        });
    }

    /** 불러오지 못한 까닭 */
    private static String loadWhy(Area area) {
        return NOT_LOADED + (area.name() != null ? " (시간 초과)" : " (틱킹 영역을 만들지 못함 — 붙잡을 청크가 너무 많음)");
    }

    /** 나무 자리 (밑동에서 r 칸 원) 에 걸친 청크가 모두 불러와졌는지: 청크마다 밑동에 가장 가까운 칸이 원 안이면 그 청크를 봄 */
    public static boolean footprintLoaded(ServerLevel level, BlockPos base, int r) {
        int bx = base.getX();
        int bz = base.getZ();
        for (int cx = Math.floorDiv(bx - r, 16); cx <= Math.floorDiv(bx + r, 16); cx++) {
            for (int cz = Math.floorDiv(bz - r, 16); cz <= Math.floorDiv(bz + r, 16); cz++) {
                int px = Math.max(cx * 16, Math.min(cx * 16 + 15, bx));
                int pz = Math.max(cz * 16, Math.min(cz * 16 + 15, bz));
                if (Math.hypot(px - bx, pz - bz) > r) continue;
                if (!level.getChunkSource().hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ 자리 찾기

    /** 블록이 땅이 아닌 (지나갈) 곳인지 */
    private static boolean open(BlockState b) {
        return b != null && TreeBlocks.passable(b);
    }

    /**
     * x, z 칸의 땅 바로 위 높이: y0 에서 (막혀 있으면 16칸까지 위로 올라가) 아래로 48칸까지 땅을 찾음
     * (풀 · 꽃 · 잎 · 다른 나무 줄기 · 큰 버섯 · 대나무 · 작물 등 식물 · 물은 지나감). 없으면 null
     */
    public static Integer groundY(ServerLevel level, int x, int z, int y0) {
        int y = y0;
        for (int i = 0; i < 16 && !open(blockAt(level, x, y, z)); i++) y++;
        if (!open(blockAt(level, x, y, z))) return null;
        for (int i = 0; i < 48; i++, y--) {
            BlockState b = blockAt(level, x, y - 1, z);
            if (b == null) return null;
            if (TreeBlocks.isGround(b)) return y;
        }
        return null;
    }

    /** 심을 자리 (base) 또는 까닭 (reason) */
    public record Spot(BlockPos base, String reason) {}

    /** 심을 자리: 바라보는 블록 (lookRange 안). 가까우면 (minD 안) 바라보는 쪽으로 minD 칸 앞, 아무것도 안 보이면 minD 칸 앞 */
    public static Spot treeSpot(ServerPlayer sp, int minD) {
        ServerLevel level = sp.level();
        Vec3 l = sp.position();
        // 보는 쪽 = 플레이어가 돌린 시선 (yRot · xRot). getViewVector 는 산 것의 머리 방향 (yHeadRot) 이라 순간이동 직후엔 옛 방향일 수 있음
        Vec3 v = sp.getLookAngle();
        double hx = v.x;
        double hz = v.z;
        double hl = Math.hypot(hx, hz);
        if (hl < 0.05) {
            // 바로 아래 · 위를 봄: 몸 방향
            double yaw = Math.toRadians(sp.getYRot());
            hx = -Math.sin(yaw);
            hz = Math.cos(yaw);
        } else {
            hx /= hl;
            hz /= hl;
        }
        Kit.Hit hit = Kit.ray(level, sp.getEyePosition(), v.normalize(), CFG.num("lookRange"), true);
        int x;
        int z;
        int y0;
        if (hit != null && Math.hypot(hit.pos().getX() + 0.5 - l.x, hit.pos().getZ() + 0.5 - l.z) >= minD) {
            x = hit.pos().getX();
            z = hit.pos().getZ();
            y0 = hit.pos().getY() + 1;
        } else {
            x = (int) Math.floor(l.x + hx * minD);
            z = (int) Math.floor(l.z + hz * minD);
            y0 = (int) Math.floor(l.y);
        }
        Integer y = groundY(level, x, z, y0);
        if (y == null) return new Spot(null, "심을 땅을 찾지 못했습니다 (허공·불러오지 않은 곳)");
        return new Spot(new BlockPos(x, y, z), null);
    }

    // ------------------------------------------------------------ 살핌

    /** 바로 알 수 있는 심을 수 없는 까닭 (없으면 null): 네더, 세계 높이, 보스 필드와 겹침, (loaded 면) 불러오지 않은 곳, 물 속 */
    public static String treeProblem(ServerLevel level, BlockPos base, TreePlan plan, boolean loaded) {
        if (level.dimension() == Level.NETHER) return "네더에서는 자라지 않습니다 (용암·천장)";
        int min = level.getMinY();
        int max = level.getMinY() + level.getHeight();
        if (base.getY() + plan.top + 2 > max) return "너무 높은 곳입니다 (꼭대기가 세계 높이 " + max + "에 닿음 — y " + (max - plan.top - 2) + " 아래를 보세요)";
        if (base.getY() + plan.bottom - 1 < min) return "너무 낮은 곳입니다 (뿌리가 세계 바닥에 닿음)";
        String dim = dimId(level);
        for (Arena.Altar a : Arena.altars()) {
            if (!a.dim.equals(dim)) continue;
            Arena.Size s = Arena.arenaSize(a);
            if (base.getY() + plan.top < a.y - 6 || base.getY() + plan.bottom > a.y + s.height() + 2) continue;
            if (Math.hypot(a.x - base.getX(), a.z - base.getZ()) < s.radius() + plan.reach + 3) {
                Obj b = Arena.BOSSES.objOrNull(a.key);
                return "보스 필드(" + (b == null ? a.key : b.strOr("name", a.key)) + ", " + a.x + " " + a.y + " " + a.z + ")와 겹칩니다";
            }
        }
        if (loaded && !footprintLoaded(level, base, plan.reach)) return NOT_LOADED;
        BlockState b = blockAt(level, base);
        if (b != null && TreeBlocks.isLiquid(b)) return "물(용암) 속에는 심을 수 없습니다 — 물 밖의 땅을 보세요";
        return null;
    }

    /** 살핌 결과: 까닭 (심을 수 없으면) · 건물 블록 수 · 잎 자리가 막힌 비율 */
    public record Survey(String why, int built, double blocked) {}

    private static String lavaWhy(BlockPos p) {
        return "가까이에 용암이 있습니다 (" + p.getX() + " " + p.getY() + " " + p.getZ() + " — 나무에 불이 붙을 수 있음)";
    }

    /**
     * 심기 전 살핌 (여러 틱에 나눠 — 한 틱에 msPerTick 밀리초까지): 줄기 · 뿌리 · 가지 자리의 건물 · 설치물 · 둘레 용암 · 잎 자리가 막힌 정도
     */
    private static final class Surveyor extends Waiter {
        final ServerLevel level;
        final BlockPos base;
        final Consumer<Survey> then;
        final List<TreePlan.Op> wood = new ArrayList<>();
        final List<int[]> lava = new ArrayList<>();
        final List<TreePlan.Op> sky = new ArrayList<>();
        final Map<String, Integer> built = new LinkedHashMap<>();
        int phase;
        int i;
        int nBuilt;
        BlockPos where;
        int n;
        int blocked;

        Surveyor(ServerLevel level, BlockPos base, TreePlan plan, Prep prep, Consumer<Survey> then) {
            super(prep);
            this.level = level;
            this.base = base;
            this.then = then;
            List<TreePlan.Op> leafOps = new ArrayList<>();
            for (TreePlan.Stage st : plan.stages) {
                for (TreePlan.Op o : st.ops()) {
                    if (o.mode() == TreePlan.Mode.WOOD) wood.add(o);
                    else if (o.mode() == TreePlan.Mode.LEAF) leafOps.add(o);
                }
            }
            int R = plan.rootReach + 2;
            for (int dx = -R; dx <= R; dx += 2) for (int dz = -R; dz <= R; dz += 2) if (dx * dx + dz * dz <= R * R) lava.add(new int[] {dx, dz});
            int step = Math.max(1, leafOps.size() / cfgInt("skySamples"));
            for (int k = 0; k < leafOps.size(); k += step) sky.add(leafOps.get(k));
        }

        @Override
        boolean poll() {
            long t0 = System.nanoTime();
            long budget = Math.max(2, cfgInt("msPerTick")) * 1_000_000L;
            int k = 0;
            while (true) {
                Survey r = step();
                if (r != null) {
                    then.accept(r);
                    return true;
                }
                if ((++k & 63) == 0 && System.nanoTime() - t0 > budget) return false;
            }
        }

        /** 한 칸 살핌 (끝나면 결과, 아니면 null) */
        private Survey step() {
            if (phase == 0) {
                // 줄기 · 뿌리 · 가지 자리: 건물 · 설치물 수
                if (i >= wood.size()) {
                    phase = 1;
                    i = 0;
                    return null;
                }
                TreePlan.Op o = wood.get(i++);
                BlockPos p = base.offset(o.dx(), o.dy(), o.dz());
                BlockState b = blockAt(level, p);
                if (b == null) return new Survey(NOT_LOADED, 0, 0);
                if (TreeBlocks.isLava(b)) return new Survey(lavaWhy(p), 0, 0);
                String id = TreeBlocks.id(b);
                if (!TreeBlocks.isNatural(b) && !TreeBlocks.isKept(id)) {
                    nBuilt++;
                    built.merge(id, 1, Integer::sum);
                    if (where == null) where = p;
                }
                return null;
            }
            if (phase == 1) {
                // 땅 가까이의 용암 (뿌리 둘레, 2칸 간격)
                if (i >= lava.size()) {
                    if (nBuilt > cfgInt("maxBuilt")) {
                        List<Map.Entry<String, Integer>> top = new ArrayList<>(built.entrySet());
                        top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                        StringBuilder t = new StringBuilder();
                        for (int k = 0; k < Math.min(3, top.size()); k++) {
                            if (k > 0) t.append(", ");
                            t.append(top.get(k).getKey().replace("minecraft:", "")).append(' ').append(top.get(k).getValue());
                        }
                        return new Survey("줄기·뿌리 자리에 건물·설치물이 있습니다 (블록 " + nBuilt + "개: " + t + (built.size() > 3 ? " …" : "") + " · 예: " + where.getX() + " "
                                + where.getY() + " " + where.getZ() + ") — 빈 땅을 보세요", nBuilt, 0);
                    }
                    phase = 2;
                    i = 0;
                    return null;
                }
                int[] c = lava.get(i++);
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos p = base.offset(c[0], dy, c[1]);
                    if (TreeBlocks.isLava(blockAt(level, p))) return new Survey(lavaWhy(p), 0, 0);
                }
                return null;
            }
            // 잎 자리 (고르게 뽑아서): 막힌 곳이 많으면 산 · 동굴 · 건물 안
            if (i >= sky.size()) {
                double frac = n > 0 ? (double) blocked / n : 0;
                if (frac > CFG.num("maxBlocked")) return new Survey("잎이 자랄 곳의 " + Math.round(frac * 100) + "%가 막혀 있습니다 (산·동굴·건물 안) — 트인 곳을 보세요", nBuilt, frac);
                return new Survey(null, nBuilt, frac);
            }
            TreePlan.Op o = sky.get(i++);
            BlockPos p = base.offset(o.dx(), o.dy(), o.dz());
            BlockState b = blockAt(level, p);
            if (b == null) return new Survey(NOT_LOADED, 0, 0);
            if (TreeBlocks.isLava(b)) return new Survey(lavaWhy(p), 0, 0);
            n++;
            if (!TreeBlocks.isSoft(b)) blocked++;
            return null;
        }
    }

    /** 심기 전 살핌 (여러 틱에 나눠) — 시험용으로도 (prep 없이 null) */
    public static void treeSurvey(ServerLevel level, BlockPos base, TreePlan plan, Consumer<Survey> then) {
        WAITERS.add(new Surveyor(level, base, plan, null, then));
    }

    // ------------------------------------------------------------ 심기 (/worldtree plant)

    /** 하는 중이면 알리고 true */
    private static boolean busy(ServerPlayer player) {
        if (job != null) {
            say(player, "§c[세계수] 지금 세계수가 자라는(되돌리는) 중입니다 — 끝난 뒤에 다시 해 주세요");
            return true;
        }
        if (preparing != 0) {
            say(player, "§c[세계수] 세계수 자리를 살피는(계산하는) 중입니다 — 잠시 뒤에 다시 해 주세요");
            return true;
        }
        return false;
    }

    /** /worldtree [plant] [높이]: 바라보는 곳을 살피고 (자리를 붙잡아 불러와), 심을지 묻고 (그동안은 놓아줌), 다시 붙잡아 심음 */
    public static void plantTree(ServerPlayer player, Integer height) {
        if (busy(player)) return;
        int H = height == null ? cfgInt("height") : height;
        if (!(H >= cfgInt("minHeight") && H <= cfgInt("maxHeight"))) {
            say(player, "§c[세계수] 높이는 " + cfgInt("minHeight") + " ~ " + cfgInt("maxHeight") + "칸입니다 §7(기본 " + cfgInt("height") + ", 예: " + cmdName + " plant 80)");
            return;
        }
        ServerLevel level = player.level();
        if (level.dimension() == Level.NETHER) {
            say(player, "§c[세계수] 심을 수 없습니다: 네더에서는 자라지 않습니다 (용암·천장)");
            return;
        }
        Spot spot = treeSpot(player, TreePlan.rootReachOf(H) + cfgInt("clearance"));
        if (spot.base() == null) {
            say(player, "§c[세계수] " + spot.reason());
            return;
        }
        plantAt(player, level, spot.base(), H);
    }

    /** 이 자리 (밑동 = 땅 바로 위 칸) 에 심기: 설계 → 바로 알 수 있는 까닭 → 붙잡고 불러옴 → 살핌 → 확인 창 (시험 · 사진첩도 여기서) */
    public static void plantAt(ServerPlayer player, ServerLevel level, BlockPos base, int H) {
        if (busy(player)) return;
        Prep p = beginPrep();
        Notify.notify(player, "§7세계수 자리를 살피는 중...", 40);
        planAsync(H, TreePlan.seedOf(base.getX(), base.getY(), base.getZ()), p, plan -> {
            if (!alive(player)) {
                p.end();
                return;
            }
            String why0 = treeProblem(level, base, plan, false);
            if (why0 != null) {
                say(player, "§c[세계수] 심을 수 없습니다: " + why0);
                p.end();
                return;
            }
            holdArea(level, base, plan.reach, p, area -> {
                if (!alive(player)) {
                    p.end();
                    return;
                }
                String why = area.loaded() ? treeProblem(level, base, plan, true) : loadWhy(area);
                if (why != null) {
                    say(player, "§c[세계수] 심을 수 없습니다: " + why);
                    p.end();
                    return;
                }
                WAITERS.add(new Surveyor(level, base, plan, p, survey -> {
                    // 확인 창을 여는 동안에는 붙잡지 않음 (창을 오래 열어 두어도 청크를 계속 돌리지 않게)
                    p.end();
                    if (!alive(player)) return;
                    if (survey.why() != null) {
                        say(player, "§c[세계수] 심을 수 없습니다: " + survey.why());
                        return;
                    }
                    askPlant(player, level, base, plan);
                }));
            });
        });
    }

    /** 심을 자리 표시: 밑동 원판 · 잎 넓이 원 (확인 창을 여는 동안) */
    private static void markSpot(ServerLevel level, BlockPos base, TreePlan plan) {
        Vec3 c = new Vec3(base.getX() + 0.5, base.getY() + 0.1, base.getZ() + 0.5);
        SkillFx.fx(level, "rpg:tele_disc", c, Opt.of("dir", Vec3.ZERO, "size", plan.trunkR, "life", 6.0, "color", new double[] {0.55, 0.9, 0.35}, "alpha", 0.8, "grow", 1.0));
        for (int i = 0; i < 24; i++) {
            double a = (i / 24.0) * TreePlan.TAU;
            particle(level, "minecraft:villager_happy", new Vec3(c.x + Math.cos(a) * plan.canopy, base.getY() + 1, c.z + Math.sin(a) * plan.canopy));
        }
    }

    private static void askPlant(ServerPlayer player, ServerLevel level, BlockPos base, TreePlan plan) {
        markSpot(level, base, plan);
        UUID u = player.getUUID();
        ASKING.add(u);
        new MenuForm("§l" + name(),
                "§f바라보는 곳 §e" + base.getX() + " " + base.getY() + " " + base.getZ() + "§f에 높이 §e" + plan.H + "칸§f, 잎 넓이 §e" + plan.canopy * 2
                        + "칸§f쯤 되는 세계수를 심습니다 §7(블록 약 " + plan.total + "개)§f.\n\n"
                        + "§7- 뿌리부터 몇 초 동안 자라납니다 (자라는 자리에 선 사람·동물은 옆으로 옮김)\n"
                        + "§7- 줄기·뿌리는 흙·돌·모래·눈·얼음·풀·꽃·물·들의 식물·통나무 자리를 바꾸고, 잎은 빈 곳·풀·꽃·다른 나무의 잎 자리에 자랍니다 (사람이 쌓은 흙·돌이라도 바뀜)\n"
                        + "§7- 판자·유리·조약돌·벽돌·껍질 벗긴 통나무 같은 건축 블록, 광석, 상자, 밭, 길, 보스 제단은 건드리지 않습니다\n"
                        + "§7- " + undoHint() + "§7 로 되돌릴 수 있습니다 (최근 " + cfgInt("keep") + "그루, 바뀐 블록을 적어 둠)")
                .button("§2심기")
                .button("그만두기")
                .show(player, sel -> {
                    ASKING.remove(u);
                    if (sel != 0 || !alive(player)) return;
                    if (busy(player)) return;
                    // 다시 붙잡고 심기
                    Prep p = beginPrep();
                    holdArea(level, base, plan.reach, p, area -> {
                        if (!alive(player)) {
                            p.end();
                            return;
                        }
                        String again = area.loaded() ? treeProblem(level, base, plan, true) : loadWhy(area);
                        if (again != null) {
                            say(player, "§c[세계수] 심을 수 없습니다: " + again);
                            p.end();
                            return;
                        }
                        if (job != null) {
                            busy(player);
                            p.end();
                            return;
                        }
                        if (startTree(level, base, plan, player, area.name())) p.area = null; // 붙잡은 땅은 일이 맡음
                        p.end();
                    });
                });
        if (!Forms.pending(player)) ASKING.remove(u); // 창을 열지 못함 (모드가 없는 클라이언트)
    }

    /**
     * 자라기 시작 (단계마다 나눠서): 기록을 남기고 큰 글자 · 소리. area = 붙잡아 둔 땅 (다 자라면 놓음). 시작했으면 true
     */
    public static boolean startTree(ServerLevel level, BlockPos base, TreePlan plan, ServerPlayer player, String area) {
        if (job != null) return false;
        MinecraftServer server = level.getServer();
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString(++idc, 36);
        TreeLog.Rec rec = new TreeLog.Rec(id, dimId(level), base.getX(), base.getY(), base.getZ(), plan.H, plan.seed, plan.reach,
                player != null ? player.getName().getString() : "?", 2, plan.fp, plan.total);
        TreeLog.addRecord(server, rec, cfgInt("keep"));
        TreeLog log = TreeLog.fresh(plan.total);
        log.save(server, id, base, 0);
        Job j = new Job(false, id, rec, level, base, plan, log, player, area);
        j.starts = plan.stageStarts();
        j.budget = perTick();
        j.saveAt = Tasks.now() + cfgInt("saveEvery");
        job = j;
        bigTitle(level, base, "§a§l세계수", "§f뿌리부터 자라납니다");
        Vec3 c = new Vec3(base.getX(), base.getY(), base.getZ());
        play(level, "item.bone_meal.use", c, 0.8);
        play(level, "block.creaking_heart.place", c, 0.7);
        return true;
    }

    // ------------------------------------------------------------ 놓기

    /** 받침을 잃는 칸 (치우기 전 블록과 함께) */
    private record Dep(TreeLog.Extra extra, BlockState state) {}

    /** 한 칸 놓기 (놓는 법에 맞으면). gi = 칸 번호 */
    private static void placeOp(Job j, int gi, TreePlan.Op op) {
        ServerLevel level = j.level;
        BlockPos b = j.base;
        if (op.mode() == TreePlan.Mode.CARPET || op.mode() == TreePlan.Mode.BUSH) {
            int x = b.getX() + op.dx();
            int z = b.getZ() + op.dz();
            Block mine = TreeBlocks.block(op.block());
            for (int dy = op.dy() + 2; dy >= op.dy() - 4; dy--) {
                int y = b.getY() + dy;
                BlockState here = blockAt(level, x, y, z);
                if (here == null) {
                    j.errors++;
                    return;
                }
                if (!TreeBlocks.isSoft(here) || here.getBlock() == mine) continue;
                BlockState below = blockAt(level, x, y - 1, z);
                if (below == null || !(op.mode() == TreePlan.Mode.BUSH ? TreeBlocks.DIRT.contains(TreeBlocks.id(below)) : TreeBlocks.naturalGround(below))) continue;
                if (setAt(j, gi, new BlockPos(x, y, z), here, op)) j.log.dys.put(gi, dy);
                return;
            }
            j.skipped++;
            return;
        }
        BlockPos loc = b.offset(op.dx(), op.dy(), op.dz());
        BlockState here = blockAt(level, loc);
        if (here == null) {
            j.errors++;
            return;
        }
        // 줄기 · 뿌리 · 가지: 자연 블록만 (그 밖의 블록은 그대로) · 잎 · 열매 · 덩굴: 빈 곳 · 풀 · 꽃 · 잎에만
        if (op.mode() == TreePlan.Mode.WOOD ? !TreeBlocks.isNatural(here) : !TreeBlocks.isSoft(here)) {
            j.skipped++;
            return;
        }
        if (op.mode() == TreePlan.Mode.HANG) {
            // 덩굴 · 포자 꽃: 빈 칸에, 위가 단단한 블록 (이끼 · 가지 · 덩굴 윗마디) 일 때만
            BlockState up = blockAt(level, loc.above());
            if (!here.isAir() || up == null || up.isAir() || TreeBlocks.isLiquid(up) || TreeBlocks.isSoft(up)) {
                j.skipped++;
                return;
            }
        }
        setAt(j, gi, loc, here, op);
    }

    /**
     * 받침을 잃는 칸 (바꾸기 전에 읽어 둠): 두 칸짜리 풀 · 꽃의 아래 반쪽 (here 가 윗 반쪽이면), 바로 위에 선 풀 · 꽃 · 작물 · 줄기식물 (같은 무리로 이어진 기둥).
     * 아래에서 위 차례
     */
    private static List<Dep> dependents(Job j, BlockPos loc, BlockState here) {
        List<Dep> out = new ArrayList<>();
        if (Boolean.TRUE.equals(TreeBlocks.halfOf(here))) {
            BlockState b = blockAt(j.level, loc.below());
            if (b != null && b.getBlock() == here.getBlock()) out.add(new Dep(new TreeLog.Extra(loc.getX(), loc.getY() - 1, loc.getZ(), j.log.codeOf(b)), b));
        }
        String first = null;
        for (int y = loc.getY() + 1; y <= loc.getY() + 24; y++) {
            BlockState b = blockAt(j.level, loc.getX(), y, loc.getZ());
            if (b == null || b.isAir() || !TreeBlocks.isRooted(b)) break;
            String id = TreeBlocks.id(b);
            String fam = TreeBlocks.family(id);
            if (first == null) first = fam;
            else if (!fam.equals(first) && !id.equals("minecraft:cactus_flower")) break;
            out.add(new Dep(new TreeLog.Extra(loc.getX(), y, loc.getZ(), j.log.codeOf(b)), b));
        }
        return out;
    }

    /** 놓고 원래 블록을 기록 (바꾸기 전에 읽음). 받침을 잃는 칸은 먼저 (위에서부터) 치움 (물을 품은 것은 물로). 놓았으면 true */
    private static boolean setAt(Job j, int gi, BlockPos loc, BlockState here, TreePlan.Op op) {
        TreeLog log = j.log;
        int prev = log.codeOf(here);
        List<Dep> deps = here.isAir() ? List.of() : dependents(j, loc, here);
        List<Dep> cleared = new ArrayList<>();
        for (int k = deps.size() - 1; k >= 0; k--) {
            Dep d = deps.get(k);
            try {
                j.level.setBlock(pos(d.extra()), TreeBlocks.wet(d.state()) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), FLAGS);
                cleared.add(d);
            } catch (RuntimeException e) {
                j.errors++;
                putBack(j, cleared);
                return false;
            }
        }
        try {
            j.level.setBlock(loc, TreeBlocks.state(op.block()), FLAGS);
        } catch (RuntimeException e) {
            j.errors++;
            if (j.errors == 1) LOG.warn("[mmorpg] world tree", e);
            putBack(j, cleared);
            return false;
        }
        log.codes[gi] = prev;
        if (!deps.isEmpty()) {
            List<TreeLog.Extra> ex = new ArrayList<>(deps.size());
            for (Dep d : deps) ex.add(d.extra());
            log.extras.put(gi, ex);
        }
        j.placed++;
        return true;
    }

    /** 치웠던 칸을 되돌려 놓음 (놓지 못했을 때, 아래부터) */
    private static void putBack(Job j, List<Dep> cleared) {
        for (int k = cleared.size() - 1; k >= 0; k--) {
            Dep d = cleared.get(k);
            try {
                j.level.setBlock(pos(d.extra()), d.state(), FLAGS);
            } catch (RuntimeException e) {
                // 무시
            }
        }
    }

    private static BlockPos pos(TreeLog.Extra e) {
        return new BlockPos(e.x(), e.y(), e.z());
    }

    // ------------------------------------------------------------ 사람 · 동물 옮기기

    /** 이번 틱에 놓을 줄기 · 잎 칸 (from ~ to) 에 몸이 걸친 사람 · 동물 · 몬스터는 옆으로 옮김 (나무에 끼지 않게) */
    private static void clearEntities(Job j, TreePlan.Stage st, int from, int to) {
        BlockPos base = j.base;
        int x0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y0 = Integer.MAX_VALUE;
        int y1 = Integer.MIN_VALUE;
        int z0 = Integer.MAX_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (int i = from; i < to; i++) {
            TreePlan.Op o = st.ops().get(i);
            if (o.mode() != TreePlan.Mode.WOOD && o.mode() != TreePlan.Mode.LEAF) continue;
            x0 = Math.min(x0, o.dx());
            x1 = Math.max(x1, o.dx());
            y0 = Math.min(y0, o.dy());
            y1 = Math.max(y1, o.dy());
            z0 = Math.min(z0, o.dz());
            z1 = Math.max(z1, o.dz());
        }
        if (x0 > x1) return;
        Vec3 c = new Vec3(base.getX() + (x0 + x1 + 1) / 2.0, base.getY() + (y0 + y1 + 1) / 2.0, base.getZ() + (z0 + z1 + 1) / 2.0);
        double rad = Math.sqrt(Math.pow(x1 - x0 + 1, 2) + Math.pow(y1 - y0 + 1, 2) + Math.pow(z1 - z0 + 1, 2)) / 2 + 4;
        List<Entity> ents = j.level.getEntities((Entity) null, new AABB(c.x - rad, c.y - rad, c.z - rad, c.x + rad, c.y + rad, c.z + rad),
                e -> e.position().distanceToSqr(c) <= rad * rad);
        for (Entity e : ents) {
            boolean isPlayer = e instanceof Player;
            if (isPlayer ? e.isSpectator() : !movable(e)) continue;
            Vec3 l = e.position();
            // 몸: 사람 0.6 × 1.8, 동물 · 몬스터는 넉넉히 (말 · 골렘 · 거미도) 2 × 3
            double w = isPlayer ? 0.3 : 1.0;
            double h = isPlayer ? 1.8 : 3.0;
            int ex0 = (int) Math.floor(l.x - w) - base.getX();
            int ex1 = (int) Math.floor(l.x + w) - base.getX();
            int ey0 = (int) Math.floor(l.y) - base.getY();
            int ey1 = (int) Math.floor(l.y + h - 0.01) - base.getY();
            int ez0 = (int) Math.floor(l.z - w) - base.getZ();
            int ez1 = (int) Math.floor(l.z + w) - base.getZ();
            if (ex1 < x0 || ex0 > x1 || ey1 < y0 || ey0 > y1 || ez1 < z0 || ez0 > z1) continue;
            for (int i = from; i < to; i++) {
                TreePlan.Op o = st.ops().get(i);
                if ((o.mode() == TreePlan.Mode.WOOD || o.mode() == TreePlan.Mode.LEAF) && o.dx() >= ex0 && o.dx() <= ex1 && o.dy() >= ey0 && o.dy() <= ey1 && o.dz() >= ez0
                        && o.dz() <= ez1) {
                    moveOut(j, e, isPlayer);
                    break;
                }
            }
        }
    }

    /**
     * 옮길 엔티티: 바닐라 생물 (갑옷 거치대 · NPC (마네킹 — 애드온의 minecraft:npc) 빼고, 아이템 · 투사체 · 경험치 같은 생물이 아닌 것도 빼고) 과
     * 이 모드의 몬스터 (rpg_monster — 보스 · 소품 · 허수아비는 빼고)
     */
    private static boolean movable(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand || NpcData.isNpcEntity(e) || !e.isAlive()) return false;
        if (EntityType.getKey(e.getType()).getNamespace().equals("minecraft")) return true;
        return (e instanceof mmorpg.fabric.mob.BedrockMob bm && bm.def.family("rpg_monster")) || Kit.hasFamily(e, "rpg_monster");
    }

    /** 나무 밖 (뿌리 끝 너머) 의 땅으로 옮기고 나무를 바라보게 */
    private static void moveOut(Job j, Entity e, boolean isPlayer) {
        BlockPos base = j.base;
        TreePlan plan = j.plan;
        Vec3 l = e.position();
        double dx = l.x - (base.getX() + 0.5);
        double dz = l.z - (base.getZ() + 0.5);
        double d = Math.hypot(dx, dz);
        if (d < 0.5) {
            dx = 0;
            dz = 1;
        } else {
            dx /= d;
            dz /= d;
        }
        int R = plan.rootReach + cfgInt("clearance");
        int x = (int) Math.floor(base.getX() + 0.5 + dx * R);
        int z = (int) Math.floor(base.getZ() + 0.5 + dz * R);
        Integer gy = groundY(j.level, x, z, base.getY() + 4);
        int y = gy != null ? gy : base.getY() + 1;
        Vec3 to = new Vec3(x + 0.5, y, z + 0.5);
        float[] rot = Kit.lookRotation(to.add(0, e.getEyeHeight(), 0), new Vec3(base.getX() + 0.5, base.getY() + plan.top * 0.4, base.getZ() + 0.5));
        try {
            e.teleportTo(j.level, to.x, to.y, to.z, Set.of(), rot[0], rot[1], false);
            e.setYHeadRot(rot[0]);
            if (!isPlayer) e.setDeltaMovement(Vec3.ZERO);
        } catch (RuntimeException ex) {
            return;
        }
        if (!isPlayer) {
            j.movedMobs++;
            return;
        }
        j.moved++;
        if (e instanceof ServerPlayer sp) say(sp, "§e[세계수] §f자라는 나무에 끼지 않게 옆으로 옮겼습니다");
    }

    // ------------------------------------------------------------ 연출

    /** 단계 연출: 뿌리 = 흙먼지, 줄기 = 자라는 높이에 초록 반짝임 고리, 가지 · 잎 = 가지 끝 반짝임 · 꽃잎, 빛 = 빛 알갱이 */
    private static void stageFx(Job j, TreePlan.Stage st) {
        ServerLevel level = j.level;
        BlockPos base = j.base;
        TreePlan plan = j.plan;
        Vec3 c = new Vec3(base.getX() + 0.5, base.getY() + st.y(), base.getZ() + 0.5);
        play(level, st.sound(), c, st.pitch());
        try {
            switch (st.kind()) {
                case "root" -> {
                    for (int i = 0; i < 12; i++) {
                        double a = (i / 12.0) * TreePlan.TAU;
                        double r = plan.rootReach * 0.7;
                        SkillFx.fx(level, "rpg:dust", new Vec3(c.x + Math.cos(a) * r, base.getY() + 0.3, c.z + Math.sin(a) * r),
                                Opt.of("size", 2.2, "life", 1.1, "color", new double[] {0.45, 0.36, 0.28}, "alpha", 0.85));
                    }
                    SkillFx.fx(level, "rpg:shock_ring", new Vec3(c.x, base.getY() + 0.1, c.z),
                            Opt.of("dir", Vec3.ZERO, "size", plan.rootReach * 0.55, "life", 0.8, "color", new double[] {0.55, 0.85, 0.35}, "alpha", 0.85));
                }
                case "trunk" -> {
                    for (int i = 0; i < 10; i++) {
                        double a = (i / 10.0) * TreePlan.TAU;
                        double r = plan.trunkR * 0.9 + 1;
                        particle(level, "minecraft:crop_growth_emitter", new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
                    }
                }
                case "branch", "leaf" -> {
                    for (TreePlan.Pt m : plan.branches) particle(level, "minecraft:crop_growth_emitter", new Vec3(base.getX() + m.x() + 0.5, base.getY() + m.y() + 1, base.getZ() + m.z() + 0.5));
                    if (st.kind().equals("leaf")) {
                        play(level, "item.bone_meal.use", c, 1.1);
                        for (int i = 0; i < 8; i++) {
                            double a = (i / 8.0) * TreePlan.TAU;
                            particle(level, "minecraft:cherry_leaves_particle", new Vec3(c.x + Math.cos(a) * plan.canopy * 0.6, c.y, c.z + Math.sin(a) * plan.canopy * 0.6));
                        }
                    }
                }
                case "glow" -> {
                    for (int i = 0; i < 16; i++) {
                        double a = (i / 16.0) * TreePlan.TAU;
                        particle(level, "minecraft:totem_particle", new Vec3(c.x + Math.cos(a) * plan.canopy * 0.7, c.y - 2 + (i % 4), c.z + Math.sin(a) * plan.canopy * 0.7));
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException e) {
            // 표시만
        }
    }

    /** 다 자람: 기록 저장 · 붙잡은 땅 놓음, 큰 글자 · 소리 · 빛 고리, 심은 사람에게 되돌리는 법 */
    private static void finishTree(Job j) {
        ServerLevel level = j.level;
        BlockPos base = j.base;
        TreePlan plan = j.plan;
        j.log.save(level.getServer(), j.id, base, plan.total);
        Ticking.releaseArea(j.area);
        j.area = null;
        last = new Result(false, j.id, j.placed, j.skipped, j.errors, j.moved, j.movedMobs, 0, 0, 0, 0);
        Vec3 c = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        play(level, "random.levelup", c, 0.7);
        play(level, "beacon.activate", c, 1);
        SkillFx.fx(level, "rpg:shock_ring", new Vec3(c.x, base.getY() + 0.2, c.z),
                Opt.of("dir", Vec3.ZERO, "size", (double) plan.canopy, "life", 1.2, "color", new double[] {0.6, 1.0, 0.45}, "alpha", 0.9));
        for (int i = 0; i < 24; i++) {
            double a = (i / 24.0) * TreePlan.TAU;
            particle(level, "minecraft:totem_particle", new Vec3(c.x + Math.cos(a) * plan.trunkR * 1.4, base.getY() + 1 + (i % 6) * 2, c.z + Math.sin(a) * plan.trunkR * 1.4));
        }
        String nm = name();
        bigTitle(level, base, "§a§l" + nm, "§f아홉 세계를 잇는 나무가 자라났습니다");
        if (alive(j.player)) {
            List<String> extra = new ArrayList<>();
            if (j.skipped > 0) extra.add("지형·설치물에 막혀 건너뛴 곳 " + j.skipped);
            if (j.moved > 0) extra.add("옆으로 옮긴 사람 " + j.moved);
            if (j.movedMobs > 0) extra.add("옆으로 옮긴 동물 " + j.movedMobs);
            say(j.player, "§a[세계수] §f" + nm + josa(nm, "이", "가") + " 자라났습니다 §7(높이 " + plan.H + "칸, 블록 " + j.placed + "개" + (extra.isEmpty() ? "" : ", " + String.join(", ", extra))
                    + ") §f— 되돌리려면 " + undoHint());
            if (j.errors > 0) say(j.player, "§c[세계수] " + j.errors + "곳은 놓지 못했습니다 (불러오지 않은 곳 등)");
        }
    }

    // ------------------------------------------------------------ 되돌리기 (/worldtree undo)

    /** 마지막으로 심은 세계수를 되돌림 (확인 창 → 나무 자리를 불러오고 설계를 다시 계산 → 늦게 놓은 것부터 원래 블록으로) */
    public static void undoTree(ServerPlayer player) {
        if (busy(player)) return;
        MinecraftServer server = player.level().getServer();
        TreeLog.Rec rec = TreeLog.last(server);
        if (rec == null) {
            say(player, "§c[세계수] 되돌릴 세계수가 없습니다 §7(최근 심은 " + cfgInt("keep") + "그루까지 기록)");
            return;
        }
        ServerLevel level = NpcData.levelOf(server, rec.d());
        if (level == null) {
            say(player, "§c[세계수] 세계수가 있는 곳(" + rec.d() + ")을 찾지 못했습니다");
            return;
        }
        BlockPos base = rec.base();
        UUID u = player.getUUID();
        ASKING.add(u);
        new MenuForm("§l세계수 되돌리기",
                "§f마지막으로 심은 세계수를 없애고, 심을 때 적어 둔 원래 블록으로 되돌립니다.\n\n"
                        + "§7- 자리: §e" + rec.x() + " " + rec.y() + " " + rec.z() + "§7 (" + DIM_NAMES.getOrDefault(rec.d(), rec.d()) + ")\n"
                        + "§7- 높이: §e" + rec.h() + "칸§7, 심은 사람: §e" + rec.by() + "\n"
                        + "§7- 나무가 놓은 칸 중 지금도 나무 블록인 곳만 되돌립니다 (누가 캐거나 바꾼 곳은 그대로)\n"
                        + "§7- 자라는 도중에 월드를 나갔다면 마지막 1초쯤 사이에 놓인 블록은 남을 수 있습니다")
                .button("§c되돌리기")
                .button("그만두기")
                .show(player, sel -> {
                    ASKING.remove(u);
                    if (sel != 0 || !alive(player)) return;
                    if (busy(player)) return;
                    Prep p = beginPrep();
                    TreeLog.Rec now = TreeLog.last(server);
                    if (now == null || !now.id().equals(rec.id())) {
                        say(player, "§c[세계수] 그사이 세계수 기록이 바뀌었습니다 — 다시 해 주세요");
                        p.end();
                        return;
                    }
                    Notify.notify(player, "§7세계수 자리를 불러오는 중...", 40);
                    holdArea(level, base, rec.r(), p, area -> {
                        if (!area.loaded()) {
                            if (alive(player)) say(player, "§c[세계수] 세계수(" + rec.x() + " " + rec.y() + " " + rec.z() + ") 자리를 되돌릴 수 없습니다: " + loadWhy(area));
                            p.end();
                            return;
                        }
                        planAsync(rec.h(), rec.s(), p, plan -> {
                            if (plan.fp != rec.fp() || plan.total != rec.n()) {
                                if (alive(player)) say(player, "§c[세계수] 심은 뒤 모드나 세계수 설정이 바뀌어 설계가 달라졌습니다 — 되돌릴 수 없으니 직접 치워 주세요");
                                p.end();
                                return;
                            }
                            TreeLog log = TreeLog.load(server, rec);
                            if (log == null) {
                                if (alive(player)) say(player, "§c[세계수] 이 세계수의 블록 기록이 없거나 망가져 되돌릴 수 없습니다");
                                p.end();
                                return;
                            }
                            TreeLog.Rec cur = TreeLog.last(server);
                            if (job != null || cur == null || !cur.id().equals(rec.id())) {
                                if (alive(player)) say(player, "§c[세계수] 그사이 다른 세계수 일이 시작되었습니다 — 끝난 뒤에 다시 해 주세요");
                                p.end();
                                return;
                            }
                            Job j = new Job(true, rec.id(), rec, level, base, plan, log, player, p.area);
                            j.ops = plan.allOps();
                            j.gi = j.ops.size() - 1;
                            j.budget = perTick() * 2;
                            job = j;
                            p.area = null; // 붙잡은 땅은 일이 맡음
                            p.end();
                            if (alive(player)) say(player, "§6[세계수] §f세계수를 되돌리는 중...");
                        });
                    });
                });
        if (!Forms.pending(player)) ASKING.remove(u);
    }

    /** 되돌리기 한 칸 (gi): 놓은 칸이고 지금도 우리가 놓은 블록일 때만 원래대로, 함께 치운 칸도 (불러오지 않은 곳은 기록에 남겨 다음에) */
    private static void undoOp(Job j, int gi) {
        TreeLog log = j.log;
        int code = log.codes[gi];
        if (code == 0) return;
        TreePlan.Op op = j.ops.get(gi);
        boolean ground = op.mode() == TreePlan.Mode.CARPET || op.mode() == TreePlan.Mode.BUSH;
        BlockPos loc = j.base.offset(op.dx(), ground ? log.dys.getOrDefault(gi, op.dy()) : op.dy(), op.dz());
        BlockState b = blockAt(j.level, loc);
        if (b == null) {
            j.failed++;
            return;
        }
        if (b.getBlock() != TreeBlocks.block(op.block())) {
            // 누가 캐거나 바꿈: 그대로 둠
            log.codes[gi] = 0;
            log.extras.remove(gi);
            j.changed++;
            return;
        }
        BlockState orig = log.stateOf(code);
        if (orig == null) {
            // 원래 블록을 알 수 없음 (다른 버전에서 이름이 바뀜 등): 구멍을 내지 않고 그대로 둠
            log.codes[gi] = 0;
            log.extras.remove(gi);
            j.unknown++;
            return;
        }
        try {
            j.level.setBlock(loc, orig, FLAGS);
        } catch (RuntimeException e) {
            j.failed++;
            return;
        }
        log.codes[gi] = 0;
        j.restored++;
        List<TreeLog.Extra> ex = log.extras.remove(gi);
        if (ex == null) return;
        for (TreeLog.Extra e : ex) {
            BlockState t = blockAt(j.level, pos(e));
            if (t == null || !(t.isAir() || t.is(Blocks.WATER))) continue;
            BlockState s = log.stateOf(e.code());
            if (s == null) continue;
            try {
                j.level.setBlock(pos(e), s, FLAGS);
            } catch (RuntimeException err) {
                // 그 칸은 그대로
            }
        }
    }

    /** 되돌리기 끝: 다 되돌렸으면 기록을 지움, 못 한 곳이 있으면 남은 기록을 저장 (다시 하면 이어서) */
    private static void finishUndo(Job j) {
        job = null;
        Ticking.releaseArea(j.area);
        j.area = null;
        MinecraftServer server = j.level.getServer();
        play(j.level, "dig.wood", new Vec3(j.base.getX() + 0.5, j.base.getY() + 1, j.base.getZ() + 0.5), 0.6);
        if (j.failed > 0) j.log.save(server, j.rec.id(), j.base, 0);
        else TreeLog.dropRecord(server, j.rec.id());
        last = new Result(true, j.id, 0, 0, 0, 0, 0, j.restored, j.changed, j.unknown, j.failed);
        if (alive(j.player)) {
            List<String> extra = new ArrayList<>();
            if (j.changed > 0) extra.add("그사이 바뀐 곳 " + j.changed + "은 그대로 둠");
            if (j.unknown > 0) extra.add("원래 블록을 알 수 없는 곳 " + j.unknown + "은 그대로 둠");
            say(j.player, "§a[세계수] §f세계수를 되돌렸습니다 §7(블록 " + j.restored + "개" + (extra.isEmpty() ? "" : ", " + String.join(", ", extra)) + ")");
            if (j.failed > 0) say(j.player, "§e[세계수] §f" + j.failed + "곳은 되돌리지 못했습니다 (불러오지 못한 곳 등) — " + undoHint() + "§f 를 한 번 더 해 주세요");
        }
    }

    // ------------------------------------------------------------ 틱

    private static void treeTick(MinecraftServer server) {
        try {
            treeStep(server);
        } catch (RuntimeException e) {
            // 뜻밖의 오류: 일을 멈춤 (기록은 남아 있어 되돌릴 수 있음), 한 번만 알림
            Job j = job;
            job = null;
            LOG.warn("[mmorpg] world tree", e);
            if (j == null) return;
            try {
                j.log.save(server, j.undo ? j.rec.id() : j.id, j.base, 0);
                Ticking.releaseArea(j.area);
            } catch (RuntimeException ignored) {
                // 무시
            }
            if (alive(j.player)) say(j.player, "§c[세계수] 오류로 멈췄습니다 — " + undoHint() + "§c 로 치울 수 있습니다");
        }
    }

    private static void treeStep(MinecraftServer server) {
        Job j = job;
        // 서버가 느려지면 (틱 사이가 lagMs 넘게 벌어지면) 한 틱에 놓는 수를 줄이고, 괜찮으면 조금씩 늘림
        long now = System.currentTimeMillis();
        long gap = j.lastT != 0 ? now - j.lastT : 50;
        j.lastT = now;
        int cap = j.undo ? perTick() * 2 : perTick();
        j.budget = gap > CFG.num("lagMs") ? Math.max(cfgInt("minPerTick"), (int) Math.floor(j.budget * 0.6)) : Math.min(cap, j.budget + 40);
        if (j.pause > 0) {
            j.pause--;
            return;
        }
        long ms = (long) CFG.num("msPerTick");
        if (j.undo) {
            int n = 0;
            int k = 0;
            while (n < j.budget && j.gi >= 0) {
                if (j.log.codes[j.gi] != 0) n++;
                undoOp(j, j.gi--);
                if ((++k & 31) == 0 && System.currentTimeMillis() - now > ms) break;
            }
            if (j.gi < 0) finishUndo(j);
            return;
        }
        TreePlan plan = j.plan;
        if (j.si >= plan.stages.size()) {
            job = null;
            finishTree(j);
            return;
        }
        TreePlan.Stage st = plan.stages.get(j.si);
        IntPredicate hold = holdBeforeStage;
        if (j.oi == 0 && hold != null && hold.test(j.si)) {
            j.held = j.si;
            return;
        }
        j.held = -1;
        int end = Math.min(st.ops().size(), j.oi + j.budget);
        if (!st.kind().equals("ground")) clearEntities(j, st, j.oi, end);
        int i0 = j.starts[j.si];
        int n = 0;
        while (j.oi < end) {
            placeOp(j, i0 + j.oi, st.ops().get(j.oi));
            j.oi++;
            if ((++n & 15) == 0 && System.currentTimeMillis() - now > ms) break;
        }
        if (j.oi >= st.ops().size()) {
            stageFx(j, st);
            j.si++;
            j.oi = 0;
            j.pause = cfgInt("stagePause");
            int done = j.si < j.starts.length ? j.starts[j.si] : plan.total;
            j.log.save(server, j.id, j.base, done);
            j.saveAt = Tasks.now() + cfgInt("saveEvery");
            if (alive(j.player)) {
                Notify.notify(j.player, "§a세계수가 자라는 중 §7— " + st.name() + " §f" + Math.min(100, Math.round((done / (double) Math.max(1, plan.total)) * 100)) + "%", 30);
            }
        } else if (Tasks.now() >= j.saveAt) {
            j.log.save(server, j.id, j.base, i0);
            j.saveAt = Tasks.now() + cfgInt("saveEvery");
        }
    }

    // ------------------------------------------------------------ 시험용

    /** 시험용: 무언가 하는 중 (계산 · 확인 창 · 자라기 · 되돌리기) */
    public static boolean treeBusy() {
        return job != null || preparing != 0 || !ASKING.isEmpty() || !WAITERS.isEmpty();
    }

    /** 시험용: 지금 일 (없으면 null) */
    public static JobInfo treeJob() {
        Job j = job;
        if (j == null) return null;
        return new JobInfo(j.undo, j.id, j.base, j.plan, j.undo ? -1 : j.si, j.undo ? j.restored : j.placed);
    }

    /** 시험용 (사진첩): holdBeforeStage 로 멈춘 단계 번호 (멈추지 않았으면 -1) */
    public static int heldAt() {
        Job j = job;
        return j == null || j.undo ? -1 : j.held;
    }

    /** 시험용: 마지막으로 끝난 일의 결과 (없으면 null) */
    public static Result lastResult() {
        return last;
    }

    /** 마지막으로 심은 세계수 기록 (없으면 null) */
    public static TreeLog.Rec lastTreeOf(MinecraftServer server) {
        return TreeLog.last(server);
    }

    /** 시험: 스크립트의 기억을 지움 (월드를 다시 불러온 것처럼 — 애드온 forgetTree(false)). 기다리는 살핌은 그대로 */
    public static void forget() {
        Job j = job;
        job = null;
        if (j != null) Ticking.releaseArea(j.area);
        preparing = 0;
        ASKING.clear();
        last = null;
    }

    /** 서버가 멈출 때: 기억 · 기다림을 모두 지움 */
    private static void stopped() {
        forget();
        WAITERS.clear();
    }

    /** 시험용: 기억과 세계의 기록 (목록 · 칸 기록) 을 모두 지움 */
    public static void forgetAll(MinecraftServer server) {
        forget();
        for (TreeLog.Rec r : TreeLog.records(server)) TreeLog.dropLog(server, r.id());
        TreeLog.saveRecords(server, List.of());
    }
}
