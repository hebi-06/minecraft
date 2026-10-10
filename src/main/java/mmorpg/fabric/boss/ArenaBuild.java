package mmorpg.fabric.boss;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmorpg.core.Js;
import mmorpg.core.Mobs;
import mmorpg.core.Obj;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.MenuForm;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 보스 필드 짓기 (arena.js planOps 뒤쪽 · buildArena · buildTick · stageFx · finishFx · buildProblem · liquidsAround · askBuild · checkAltarBlocks).
 * 설계 ({@link ArenaThemes}) 의 작업 목록을 한 틱에 BOSS.buildPerTick (1500) 블록까지 (fill 은 부피만큼 셈), 단계 사이 8틱 쉼 · 둘레 빛 · 소리,
 * 끝나면 알림 + a.built = true · saveAltars. 큰 필드 (설정 arena.tick — 수르트) 는 짓는 동안 Ticking 으로 붙잡고 다 불러와질 때까지 (최대 200틱) 기다림.
 * <p>
 * 자바에서 달라진 점: 블록은 애드온처럼 이웃을 건드리지 않고 그대로 놓음 (UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE — 울타리 · 담장 이음은 설계가 셈한 그대로,
 * 상자 속 물건은 쏟지 않음), 불러오지 않은 칸은 건너뛰고 그 작업을 "못 놓은 묶음" 으로 셈 (애드온은 그 fill 하나가 통째로 실패).
 */
public final class ArenaBuild {
    private ArenaBuild() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-arena");

    /** 큰 필드를 붙잡아 불러올 때 기다리는 최대 틱 (10초) */
    public static final int TICK_WAIT = 200;
    /** 놓을 때: 클라이언트에 보냄 · 이웃 모양을 건드리지 않음 · 블록 엔티티 (상자 등) 를 쏟지 않음 */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    /** 짓는 중인 필드 하나 */
    private static final class Job {
        final ServerLevel level;
        final List<ArenaThemes.StageOps> stages;
        final ServerPlayer player;
        int si;
        int oi;
        int pause;
        int errors;
        int wait;
        String area;

        Job(ServerLevel level, List<ArenaThemes.StageOps> stages, ServerPlayer player) {
            this.level = level;
            this.stages = stages;
            this.player = player;
        }
    }

    /** 짓는 중인 필드: 제단 → 작업 */
    private static final Map<Arena.Altar, Job> JOBS = new LinkedHashMap<>();
    /** 다 지은 필드의 못 놓은 묶음 수 (시험 · 사진첩 확인용) */
    private static final Map<Arena.Altar, Integer> LAST_ERRORS = new java.util.WeakHashMap<>();

    /** ArenaGuard.init */
    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            buildTick();
            // 명령어 (/fill 등) 로 제단이 없어진 경우: 10초마다 불러온 곳의 제단 블록을 확인해 목록에서 뺌
            if (server.getTickCount() % 200 == 0) checkAltarBlocks();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            JOBS.clear();
            LAST_ERRORS.clear();
        });
    }

    private static boolean alive(ServerPlayer p) {
        return p != null && !p.isRemoved() && p.connection != null;
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(BedrockText.toJava(text)));
    }

    public static boolean isBuilding(Arena.Altar a) {
        return JOBS.containsKey(a);
    }

    /** 시험용: 다 지은 필드의 못 놓은 묶음 수 (지은 적 없으면 -1) */
    public static int lastErrors(Arena.Altar a) {
        Integer n = LAST_ERRORS.get(a);
        return n == null ? -1 : n;
    }

    // ------------------------------------------------------------ 지을 수 있는지

    /**
     * 지을 수 없는 까닭 (없으면 null): 세계 높이 끝 · 지옥 기반암에 닿음, 다른 제단의 필드와 겹침 (필드끼리 BOSS.nearAltars 칸 이상 떨어져야)
     */
    public static String buildProblem(Arena.Altar a) {
        Arena.Size s = Arena.arenaSize(a);
        int R = s.radius();
        int H = s.height();
        ServerLevel level = Arena.levelOf(a);
        if (level == null) return "차원을 찾을 수 없습니다";
        int min = level.getMinY();
        int max = level.getMinY() + level.getHeight();
        if (a.y - 4 < min + 5 || a.y + H + 2 > max) return "높이가 맞지 않습니다 (제단을 y " + (min + 9) + " ~ " + (max - H - 2) + " 사이에 놓아 주세요)";
        if (a.dim.equals("minecraft:the_nether") && (a.y + H + 2 >= 123 || a.y - 4 <= 5)) return "지옥에서는 천장·바닥 기반암에 닿습니다 (제단을 y 10 ~ 106 사이에 놓아 주세요)";
        for (Arena.Altar o : Arena.altars()) {
            if (o == a || !o.dim.equals(a.dim) || Math.abs(o.y - a.y) >= 40) continue;
            if (Math.hypot(o.x - a.x, o.z - a.z) < Arena.arenaSize(o).radius() + R + Arena.BOSS.num("nearAltars")) {
                return "다른 보스 제단(" + Arena.BOSSES.obj(o.key).str("name") + ", " + o.x + " " + o.y + " " + o.z + ")과 너무 가깝습니다 (필드가 겹쳐 지워짐)";
            }
        }
        return null;
    }

    /** 필드 둘레 (바로 바깥) 에 물 · 용암이 있는지 (지은 뒤 흘러들 수 있음): 몇 층만 훑어봄 (불러오지 않은 곳은 건너뜀) */
    static boolean liquidsAround(Arena.Altar a) {
        Arena.Size s = Arena.arenaSize(a);
        ServerLevel level = Arena.levelOf(a);
        if (level == null) return false;
        int r0 = s.radius() + 2;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= s.height(); dy += 2) {
            for (int i = 0; i < 96; i++) {
                double ang = (i / 96.0) * Math.PI * 2;
                m.set(a.x + (int) Math.round(Math.cos(ang) * (r0 + 1)), a.y + dy, a.z + (int) Math.round(Math.sin(ang) * (r0 + 1)));
                if (!level.isLoaded(m)) continue;
                if (!level.getFluidState(m).isEmpty()) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 짓기

    /** 필드 짓기 시작 (몇 틱에 나눠서, 단계 사이 쉬며 연출) — player = 알릴 사람 (없으면 null). 시작했으면 true */
    public static boolean buildArena(Arena.Altar a, ServerPlayer player) {
        if (JOBS.containsKey(a)) return false;
        ArenaThemes.Plan plan = ArenaThemes.arenaPlan(a.key, a.x, a.y, a.z);
        if (plan == null) return false;
        String why = buildProblem(a);
        if (why != null) {
            if (alive(player)) say(player, "§c[보스 필드] 지을 수 없습니다: " + why);
            return false;
        }
        ServerLevel level = Arena.levelOf(a);
        if (level == null) return false;
        List<ArenaThemes.StageOps> stages = ArenaThemes.planOps(a.x, a.y, a.z, Arena.arenaSize(a).radius(), plan);
        Job job = new Job(level, stages, player);
        // v2.12.0 큰 필드 (arena.tick, 수르트 반지름 44): 짓는 동안 붙잡고 다 불러와질 때까지 (최대 TICK_WAIT 틱) 기다림
        Obj ar = Arena.BOSSES.obj(a.key).obj("arena");
        if (ar.truthy("tick")) {
            job.area = Ticking.holdArea(level, Arena.arenaCenter(a), Arena.arenaSize(a).radius() + 4, "b");
            job.wait = job.area != null ? TICK_WAIT : 0;
        }
        JOBS.put(a, job);
        if (alive(player)) Notify.notify(player, "§6[보스 필드] §f" + Arena.BOSSES.obj(a.key).str("name") + "의 신전을 짓는 중...", 80);
        return true;
    }

    private static void buildTick() {
        if (JOBS.isEmpty()) return;
        for (Arena.Altar a : new ArrayList<>(JOBS.keySet())) {
            Job job = JOBS.get(a);
            if (job == null) continue;
            if (job.pause > 0) {
                job.pause--;
                continue;
            }
            // 붙잡은 땅이 다 불러와질 때까지 (시간이 다 되면 불러온 만큼으로 지음)
            if (job.wait > 0) {
                job.wait--;
                if (!Ticking.areaLoaded(job.level, Arena.arenaCenter(a), Arena.arenaSize(a).radius() + 4)) continue;
                job.wait = 0;
            }
            if (job.si >= job.stages.size()) {
                JOBS.remove(a);
                Ticking.releaseArea(job.area);
                a.built = true;
                Arena.saveAltars();
                LAST_ERRORS.put(a, job.errors);
                finishFx(a, job);
                continue;
            }
            ArenaThemes.StageOps st = job.stages.get(job.si);
            // 한 틱에 바꾸는 블록 수로 나눔 (fill 은 부피만큼 셈)
            int budget = (int) Arena.BOSS.num("buildPerTick");
            while (budget > 0 && job.oi < st.ops().size()) {
                ArenaThemes.Op op = st.ops().get(job.oi++);
                budget -= op.volume();
                try {
                    if (!apply(job.level, op)) {
                        job.errors++;
                        if (job.errors == 1) LOG.warn("[mmorpg] arena build {}: not loaded at {} {} {}", a, op.x(), op.y(), op.z());
                    }
                } catch (RuntimeException e) {
                    job.errors++;
                    if (job.errors == 1) LOG.warn("[mmorpg] arena build " + a, e);
                }
            }
            if (job.oi >= st.ops().size()) {
                stageFx(a, job, st);
                job.si++;
                job.oi = 0;
                job.pause = 8; // 단계 사이 0.4초 (올라오는 느낌)
            }
        }
    }

    /** 작업 하나를 놓음 (불러오지 않은 칸이 있었으면 false — 나머지는 놓음) */
    static boolean apply(ServerLevel level, ArenaThemes.Op op) {
        BlockState state = ArenaBlocks.state(op.block(), op.states());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean ok = true;
        int x0 = Math.min(op.x(), op.x2());
        int x1 = Math.max(op.x(), op.x2());
        int y0 = Math.min(op.y(), op.y2());
        int y1 = Math.max(op.y(), op.y2());
        int z0 = Math.min(op.z(), op.z2());
        int z1 = Math.max(op.z(), op.z2());
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    if (!level.isLoaded(m)) {
                        ok = false;
                        continue;
                    }
                    level.setBlock(m, state, FLAGS);
                }
            }
        }
        return ok;
    }

    /** 보스 색 (설정 color — 없으면 흰색) */
    private static double[] colorOf(String key) {
        List<Double> c = Arena.BOSSES.obj(key).numbersOrNull("color");
        if (c == null || c.size() < 3) return SkillFx.WHITE;
        return new double[] {c.get(0), c.get(1), c.get(2)};
    }

    /** 단계가 끝날 때: 둘레 16곳 빛 + 바닥 고리 + 그 단계 소리 */
    private static void stageFx(Arena.Altar a, Job job, ArenaThemes.StageOps st) {
        try {
            int R = Arena.arenaSize(a).radius();
            Vec3 c = Arena.arenaCenter(a);
            double[] col = colorOf(a.key);
            for (int i = 0; i < 16; i++) {
                double d = (i / 16.0) * Math.PI * 2;
                SkillFx.fx(job.level, "rpg:bash_burst", new Vec3(c.x + Math.cos(d) * R, a.y + 0.3, c.z + Math.sin(d) * R),
                        Opt.of("dir", new Vec3(0, 0, 1), "size", 1.2, "life", 0.4, "color", col, "alpha", 0.8));
            }
            SkillFx.fx(job.level, "rpg:shock_ring", new Vec3(c.x, a.y + 0.1, c.z), Opt.of("dir", Vec3.ZERO, "size", R / 2.1, "life", 0.6, "color", col, "alpha", 0.8));
            if (st.sound() != null) play(job.level, st.sound(), c, 0.8, 1);
        } catch (RuntimeException e) {
            // 무시 (연출만)
        }
    }

    /** 다 지었을 때: 소리 + 알림 (못 놓은 묶음이 있으면 다시 짓는 법) */
    private static void finishFx(Arena.Altar a, Job job) {
        Vec3 c = Arena.arenaCenter(a);
        Obj b = Arena.BOSSES.obj(a.key);
        play(job.level, "random.levelup", c, 0.8, 1);
        Obj sounds = b.objOrNull("sounds");
        play(job.level, sounds == null ? "mob.ravager.roar" : sounds.strOr("roar", "mob.ravager.roar"), c, 0.7, 0.8);
        if (alive(job.player)) {
            say(job.player, "§6[보스 필드] §f" + b.str("name") + "의 신전이 완성되었습니다. §7제단을 누르면 보스에게 도전합니다");
            if (job.errors > 0) say(job.player, "§c[보스 필드] 블록 " + job.errors + "묶음을 놓지 못했습니다 (불러오지 않은 곳 등). §7/boss build 로 다시 지을 수 있습니다");
        }
    }

    // ------------------------------------------------------------ 소리 (단계 소리 · 끝 · 보스 울음 — 베드락 이름 → 자바, 없으면 SkillFx 의 대응표)

    private static final Map<String, String[]> SOUNDS = Map.ofEntries(
            Map.entry("dig.grass", new String[] {"block.grass.break"}),
            Map.entry("dig.gravel", new String[] {"block.gravel.break"}),
            Map.entry("dig.wood", new String[] {"block.wood.break"}),
            Map.entry("dig.sand", new String[] {"block.sand.break"}),
            Map.entry("dig.stone", new String[] {"block.stone.break"}),
            Map.entry("dig.coral", new String[] {"block.coral_block.break"}),
            Map.entry("dig.deepslate", new String[] {"block.deepslate.break"}),
            Map.entry("dig.deepslate_bricks", new String[] {"block.deepslate_bricks.break"}),
            Map.entry("dig.snow", new String[] {"block.snow.break"}),
            Map.entry("dig.basalt", new String[] {"block.basalt.break"}),
            Map.entry("place.deepslate", new String[] {"block.deepslate.place"}),
            Map.entry("place.amethyst_block", new String[] {"block.amethyst_block.place"}),
            Map.entry("block.lantern.place", new String[] {"block.lantern.place"}),
            Map.entry("bucket.empty_water", new String[] {"item.bucket.empty"}),
            Map.entry("random.glass", new String[] {"block.glass.break"}),
            Map.entry("fire.ignite", new String[] {"item.flintandsteel.use"}),
            Map.entry("mob.blaze.shoot", new String[] {"entity.blaze.shoot"}),
            Map.entry("random.levelup", new String[] {"entity.player.levelup"}),
            Map.entry("mob.ravager.roar", new String[] {"entity.ravager.roar"}),
            Map.entry("mob.elderguardian.idle", new String[] {"entity.elder_guardian.ambient"}),
            Map.entry("mob.warden.roar", new String[] {"entity.warden.roar"}),
            Map.entry("mob.enderdragon.growl", new String[] {"entity.ender_dragon.growl"}),
            Map.entry("mob.wolf.big.growl", new String[] {"entity.wolf_big.growl", "entity.wolf.growl", "entity.wolf_angry.growl"}));
    private static final Set<String> SOUND_WARNED = new HashSet<>();

    /** 베드락 소리 이름 → 자바 소리 (없으면 null) */
    static SoundEvent sound(String bedrock) {
        String[] ids = SOUNDS.get(bedrock);
        if (ids != null) {
            for (String id : ids) {
                Identifier rl = Identifier.withDefaultNamespace(id);
                if (BuiltInRegistries.SOUND_EVENT.containsKey(rl)) return BuiltInRegistries.SOUND_EVENT.getValue(rl);
            }
            if (SOUND_WARNED.add(bedrock)) LOG.warn("[mmorpg] arena: no Java sound for {}", bedrock);
            return null;
        }
        return SkillFx.sound(bedrock);
    }

    /** 그 자리에서 소리 (fx.js play(dim, 이름, 자리, pitch, volume)) */
    static void play(ServerLevel level, String bedrock, Vec3 at, double pitch, double volume) {
        SoundEvent s = sound(bedrock);
        if (s != null) level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, (float) volume, (float) pitch);
    }

    // ------------------------------------------------------------ 확인 창

    /** 필드를 지을지 묻기 (운영자 · 크리에이티브 — arena.js askBuild) */
    public static void askBuild(ServerPlayer player, Arena.Altar a) {
        if (!Arena.canManage(player)) return;
        Obj b = Arena.BOSSES.obj(a.key);
        Arena.Size s = Arena.arenaSize(a);
        String why = buildProblem(a);
        if (why != null) {
            say(player, "§c[보스 필드] 지을 수 없습니다: " + why);
            return;
        }
        String wet = liquidsAround(a) ? "\n§c- 둘레에 물·용암이 있습니다: 필드 안으로 흘러들 수 있습니다" : "";
        new MenuForm("§l" + b.str("name") + " 보스 필드",
                "§f제단 둘레 반지름 §e" + s.radius() + "칸§f, 높이 §e" + s.height() + "칸§f 안의 지형을 §6" + b.strOr("title", b.str("name"))
                        + "§f의 신전으로 바꿉니다.\n\n§7- 그 안의 블록은 사라지고 바닥·울타리·기둥이 새로 생깁니다\n§7- 필드 안 블록은 운영자가 아니면 부술 수 없습니다"
                        + "\n§7- 보스는 필드를 지어야 도전할 수 있습니다\n§7- 나중에 크리에이티브에서 웅크리고 제단을 누르거나 /boss build 로 지을 수 있습니다" + wet)
                .button("§2필드 만들기")
                .button("나중에")
                .show(player, sel -> {
                    if (sel != 0) return;
                    if (!Arena.altars().contains(a)) return;
                    buildArena(a, player);
                });
    }

    // ------------------------------------------------------------ 제단 블록 확인

    /** 목록의 제단 자리에 제단 블록이 없으면 (불러온 곳만, 짓는 중 아님) 목록에서 뺌 */
    public static void checkAltarBlocks() {
        if (Arena.server() == null) return;
        for (Arena.Altar a : new ArrayList<>(Arena.altars())) {
            ServerLevel level = Arena.levelOf(a);
            if (level == null) continue;
            BlockPos pos = a.pos();
            if (!level.isLoaded(pos)) continue; // 불러오지 않은 곳
            String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            if (!id.equals(Mobs.altarId(a.key)) && !isBuilding(a)) Arena.removeAltar(a);
        }
    }

    /** 레벨 글자 (설정 level — JS 숫자 글자처럼) */
    static String levelText(String key) {
        return Js.str(Arena.BOSSES.obj(key).num("level"));
    }
}
