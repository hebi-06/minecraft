package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.field.FieldBlocks;
import mmorpg.fabric.field.FieldExtras;
import mmorpg.fabric.field.FieldWorld;
import mmorpg.fabric.mob.HuntZones;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.quest.MainQuests;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 2026-11-07 섬 맵 사진첩 (화면 시험): 새 세계를 "월드 유형 = 섬 맵 (mmorpg:field)" 으로 만들어 찍음.
 * only=field: 섬마다 땅 모양 (fd_*) + 확인 (생성기 · 세계 경계 · 시작 자리 · 실제 청크 = 계획 · 다리 · 분수 · 제단 · 사냥터 · 메인 NPC · 이동 지점 돌).
 * only=fieldb: 마을 · 거점 · 신전 · 세계수 가까이 (fb_*). 사진마다 걸린 시간 · 서버 틱 · 스레드가 머문 곳을 기록. 사진 → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgFieldGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-field-gallery");
    private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Mmorpg.id("field"));

    private final List<String> failures = new ArrayList<>();

    /** 사진 자리: 이름 · 눈 자리 (x, y, z) · 보는 곳 (x, y, z) */
    private record Shot(String name, double px, double py, double pz, double tx, double ty, double tz) {}

    /**
     * only=field: 섬의 땅 모양 (과녁에서 100칸 안팎) + 실제 청크 · 처음 준비 확인. 자리는 지형 + 짓는 것의 높이로 고름 (시야 막힘 확인 — 2026-11-07).
     * 하늘 높이에서 섬 전체를 찍던 것 (CI 42356359483) 은 보이는 거리 밖이라 빈 하늘만 나와 뺌 — 섬 전체는 배치도 (FieldPreview) 로.
     */
    private static final List<Shot> SHOTS = List.of(
            new Shot("fd_01_town_south", 0, 90, 214, 0, 85, 164),
            new Shot("fd_02_town_roofs", 67, 111, -7, 0, 75, 60),
            new Shot("fd_03_world_tree", 87, 114, -165, 0, 110, -215),
            new Shot("fd_04_bridge_deck", 478, 71, 1, 620, 72, 1),
            new Shot("fd_05_bridge_side", 560, 66, 45, 613, 68, 0),
            new Shot("fd_06_farm_woods", 1093, 96, -143, 1151, 76, -74),
            new Shot("fd_07_dunes", 105, 99, 1102, 5, 80, 1102),
            new Shot("fd_08_red_mesa", -1, 111, 1615, -1, 101, 1505),
            new Shot("fd_09_fjords", -1714, 101, -108, -1604, 97, -108),
            new Shot("fd_10_mire", -1424, 82, 350, -1473, 66, 301),
            new Shot("fd_11_frost_peaks", -340, 145, -1344, -400, 140, -1448),
            new Shot("fd_12_glacier", -140, 160, -1598, -140, 115, -1698),
            new Shot("fd_13_void_rift", -39, 107, -2326, -39, 86, -2426),
            new Shot("fd_14_helheim", 69, 107, -2803, 69, 83, -2903),
            new Shot("fd_15_volcano", -143, 140, 2497, -243, 100, 2439),
            new Shot("fd_16_muspel", -1, 100, 2422, -1, 77, 2522));

    /** only=fieldb: 마을 · 거점 · 세계수 · 신전 가까이 */
    private static final List<Shot> SHOTS_B = List.of(
            new Shot("fb_01_plaza", 0, 75, 84, 0, 74, 58),
            new Shot("fb_02_south_gate", 0, 76, 140, 0, 78, 164),
            new Shot("fb_03_royal_hall", 12, 90, 55, -12, 78, 30),
            new Shot("fb_04_training", 60, 86, 75, 52, 73, 40),
            new Shot("fb_05_market", 34, 84, 118, 34, 73, 90),
            new Shot("fb_06_mentors", 54, 84, 56, 54, 75, 22),
            new Shot("fb_07_rune_tower", -10, 90, 110, -36, 84, 86),
            new Shot("fb_08_hamlet", 243, 89, -153, 255, 74, -120),
            new Shot("fb_09_farm_camp", 868, 82, 2, 840, 72, 30),
            new Shot("fb_10_desert_camp", 75, 88, 790, 40, 74, 790),
            new Shot("fb_11_harbor", -641, 83, 66, -690, 70, 75),
            new Shot("fb_12_lodge", 20, 86, -730, 20, 75, -770),
            new Shot("fb_13_void_outpost", 0, 99, -2130, 0, 85, -2170),
            new Shot("fb_14_ember_outpost", 0, 92, 2110, 0, 76, 2150),
            new Shot("fb_15_scarecrow_temple", 1599, 86, 29, 1560, 80, -10),
            new Shot("fb_16_surtr_arena", 0, 92, 2670, 0, 85, 2610),
            new Shot("fb_17_nidhogg_arena", -360, 124, -1170, -420, 118, -1170),
            new Shot("fb_18_world_tree_base", 25, 91, -172, 0, 90, -215));

    /** 보이는 거리 (청크) — 12 는 CI 의 소프트웨어 그리기에서 사진마다 45초 안에 다 못 그림 (CI 42356359483) */
    private static final int VIEW = 8;
    /** 사진마다 청크를 기다리는 최대 틱 */
    private static final int CHUNK_WAIT = 1500;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        boolean a = RpgProdSmoke.only("field");
        boolean b2 = RpgProdSmoke.only("fieldb");
        if (RpgProdSmoke.active() || !a && !b2) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(c -> {
                    c.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    for (WorldCreationUiState.WorldTypeEntry e : c.getNormalPresetList()) if (e.preset().is(PRESET)) c.setWorldType(e);
                })
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            if (a) server.runOnServer(s -> checkWorld(s));
            // 26.3 의 놀이 규칙 이름 (doDaylightCycle → advance_time, doWeatherCycle → advance_weather — 옛 이름은 "Incorrect argument" 로 무시돼 밤이 됨)
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule advance_weather false");
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamemode spectator @a");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.renderDistance().set(VIEW);
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); // 손 · 화면 칸 없이 (F1)
            });
            ctx.waitTicks(20);
            Sampler sampler = new Sampler();
            sampler.watch("server", server.computeOnServer(s -> Thread.currentThread()));
            sampler.watch("render", ctx.computeOnClient(mc -> Thread.currentThread()));
            sampler.start();
            try {
                if (a) for (Shot s : SHOTS) shot(ctx, server, world, s, sampler);
                if (b2) for (Shot s : SHOTS_B) shot(ctx, server, world, s, sampler);
            } finally {
                sampler.stop();
            }
        }
        LOG.info("[field-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("field gallery failed: " + String.join(" / ", failures));
    }

    private void check(boolean ok, String what) {
        if (ok) LOG.info("[field-gallery] OK   {}", what);
        else {
            LOG.warn("[field-gallery] FAIL {}", what);
            failures.add(what);
        }
    }

    private void checkWorld(MinecraftServer s) {
        ServerLevel level = s.overworld();
        FieldTerrain t = FieldWorld.terrain();
        FieldPlan plan = t.plan();
        check(FieldWorld.isField(level), "overworld uses the island map generator");
        check(Math.abs(level.getWorldBorder().getSize() - plan.borderSize) < 1, "world border " + level.getWorldBorder().getSize());
        var spawn = level.getRespawnData().pos();
        check(spawn.getX() == plan.spawnX && spawn.getZ() == plan.spawnZ, "spawn at the village square " + spawn.toShortString());
        // 실제 청크: 섬 가운데 · 바다 · 거점의 땅 블록 (나무 밑 흙 · 눈 덮임 허용) · 바이옴 칸
        FieldTerrain.Column c = new FieldTerrain.Column();
        List<int[]> probes = new ArrayList<>();
        for (FieldPlan.Island is : plan.islands) {
            probes.add(new int[] {(int) is.x() + 37, (int) is.z() - 23});
            probes.add(new int[] {(int) (is.x() + is.radius() * 1.6), (int) is.z()});
        }
        for (FieldPlan.Site site : plan.sites("camp")) probes.add(new int[] {(int) site.x() + 5, (int) site.z() + 5});
        List<String> bad = new ArrayList<>();
        for (int[] p : probes) {
            level.getChunk(p[0] >> 4, p[1] >> 4);
            t.sample(p[0], p[1], c);
            BlockState got = level.getBlockState(new BlockPos(p[0], c.ground, p[1]));
            BlockState want = FieldBlocks.of(c.top, p[0], c.ground, p[1]);
            boolean ground = sameGround(got, want, c.hasWater() && c.water > c.ground);
            String biome = level.getNoiseBiome(p[0] >> 2, (c.ground + 1) >> 2, p[1] >> 2).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
            String wantBiome = t.biomeAt(((p[0] >> 2) << 2) + 2, ((p[1] >> 2) << 2) + 2);
            if (!ground || !biome.equals(wantBiome)) bad.add(p[0] + " " + p[1] + ": " + got + " want " + want + " · " + biome + " want " + wantBiome);
        }
        check(bad.isEmpty(), "real chunks = plan (" + (probes.size() - bad.size()) + "/" + probes.size() + ") " + String.join(" / ", bad));
        // 다리 갑판 (가운데)
        for (FieldTerrain.Span sp : t.spans()) {
            int mx = (int) Math.floor((sp.ax() + sp.bx()) / 2);
            int mz = (int) Math.floor((sp.az() + sp.bz()) / 2);
            level.getChunk(mx >> 4, mz >> 4);
            BlockState deck = level.getBlockState(new BlockPos(mx, sp.deck(), mz));
            check(deck.is(Blocks.SPRUCE_PLANKS) || deck.is(Blocks.STRIPPED_SPRUCE_LOG), "bridge " + sp.bridge().from() + "→" + sp.bridge().to() + " deck " + deck);
        }
        // 마을: 광장 분수 · 이동 지점 돌
        FieldPlan.Site town = plan.site("town");
        int g = plan.sea + 9;
        level.getChunk((int) town.x() >> 4, (int) town.z() >> 4);
        check(level.getBlockState(new BlockPos((int) town.x(), g, (int) town.z())).is(Blocks.CHISELED_STONE_BRICKS), "fountain at the square");
        // 처음 준비: 보스 제단 (지어짐) · 사냥터 · 메인 NPC 1 ~ 16 기록
        int built = 0;
        for (FieldExtras.AltarSpot a : FieldExtras.altars()) {
            Arena.Altar found = Arena.altarAt(NpcData.dimId(level), a.pos());
            if (found != null && found.built) built++;
            level.getChunk(a.pos().getX() >> 4, a.pos().getZ() >> 4);
            if (a.key().equals("scarecrow_king")) check(level.getBlockState(a.pos()).getBlock() == AltarBlocks.block(a.key()), "altar block of " + a.key());
        }
        check(built == FieldExtras.altars().size() && built == plan.sites("boss").size(), "temples registered as built " + built + "/" + plan.sites("boss").size());
        check(HuntZones.zoneList().size() >= plan.sites("zone").size(), "hunt zones " + HuntZones.zoneList().size() + "/" + plan.sites("zone").size());
        int recorded = 0;
        for (int n = 1; n <= 16; n++) {
            MainQuests.MainNpc def = MainQuests.mainNpcByNumber(n);
            if (def != null && NpcData.mainRecord(s, def.key()) != null) recorded++;
        }
        check(recorded == 16, "main quest NPCs 1~16 placed (" + recorded + ")");
        for (Blueprint.Mark m : FieldBuild.build(t).marks()) {
            if (!m.id().startsWith("waystone")) continue;
            BlockPos at = BlockPos.containing(m.x(), m.y(), m.z());
            level.getChunk(at.getX() >> 4, at.getZ() >> 4);
            check(level.getBlockState(at).is(Blocks.LODESTONE), "waystone stone at " + at.toShortString() + " = " + level.getBlockState(at));
        }
    }

    /**
     * 땅 블록이 계획과 같은지: 같은 블록, 풀 → 흙 · 고사리흙 · 이끼 · 눈 (나무 밑 · 눈 덮임), 돌 겉 → 다른 돌 · 광석 (바이옴 장식의 광석 덩이 —
     * CI 42356359483: 바다 밑 심층암 자리에 안산암), 물 밑 바닥 → 굳은 블록이면 됨 (자갈 · 점토 덩이).
     */
    static boolean sameGround(BlockState got, BlockState want, boolean underWater) {
        if (got.getBlock() == want.getBlock()) return true;
        if (want.is(Blocks.GRASS_BLOCK) && (got.is(Blocks.DIRT) || got.is(Blocks.PODZOL) || got.is(Blocks.MOSS_BLOCK) || got.is(Blocks.SNOW_BLOCK))) return true;
        boolean solid = !got.isAir() && got.getFluidState().isEmpty();
        if (underWater) return solid;
        return want.is(BlockTags.BASE_STONE_OVERWORLD) && solid && (got.is(BlockTags.BASE_STONE_OVERWORLD) || got.getBlock().getDescriptionId().contains("ore"));
    }

    private static void shot(ClientGameTestContext ctx, TestServerContext server, TestSingleplayerContext world, Shot s, Sampler sampler) {
        double dx = s.tx() - s.px();
        double dz = s.tz() - s.pz();
        double dy = s.ty() - (s.py() + 1.62);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.hypot(dx, dz)));
        long t0 = System.nanoTime();
        int k0 = server.computeOnServer(MinecraftServer::getTickCount);
        sampler.reset();
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", s.px(), s.py(), s.pz(), yaw, pitch));
        ctx.waitTicks(3);
        ctx.getInput().lookAt(yaw, pitch);
        ctx.waitTicks(60);
        boolean all = true;
        try {
            world.getConnection().waitForChunksRender(true, CHUNK_WAIT);
        } catch (AssertionError e) {
            all = false;
        }
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of(s.name()).withSize(1280, 720));
        double sec = (System.nanoTime() - t0) / 1e9;
        int k1 = server.computeOnServer(MinecraftServer::getTickCount);
        int entities = server.computeOnServer(sv -> {
            int n = 0;
            for (Entity e : sv.overworld().getAllEntities()) if (e != null) n++;
            return n;
        });
        LOG.info("[field-gallery] shot {} in {} s{} · server {} tps · {} entities · {}", s.name(), String.format(Locale.ROOT, "%.0f", sec),
                all ? "" : " (chunks not all rendered)", String.format(Locale.ROOT, "%.1f", (k1 - k0) / sec), entities, sampler.report());
    }

    /**
     * 서버 · 화면 스레드가 어디서 시간을 쓰는지 (25 ms 마다 스택을 봄 — 사진마다 많이 걸린 곳 다섯). CI 42356359483 에서 바다 쪽 사진부터 서버가
     * 15초씩 밀려 (Can't keep up) 청크가 늦게 옴 — 원인을 보려고.
     */
    private static final class Sampler {
        private final List<Thread> threads = new ArrayList<>();
        private final List<String> names = new ArrayList<>();
        private final List<Map<String, Integer>> counts = new ArrayList<>();
        private int samples;
        private volatile boolean running = true;

        void watch(String name, Thread t) {
            names.add(name);
            threads.add(t);
            counts.add(new HashMap<>());
        }

        void start() {
            Thread th = new Thread(() -> {
                while (running) {
                    sample();
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "field-gallery-sampler");
            th.setDaemon(true);
            th.start();
        }

        void stop() {
            running = false;
        }

        private synchronized void sample() {
            samples++;
            for (int i = 0; i < threads.size(); i++) {
                Thread t = threads.get(i);
                Thread.State st = t.getState();
                String key = st == Thread.State.RUNNABLE ? key(t.getStackTrace()) : "idle";
                counts.get(i).merge(key, 1, Integer::sum);
            }
        }

        /** 맨 위 칸 ← 처음 게임 칸 ← 처음 모드 칸 */
        static String key(StackTraceElement[] st) {
            if (st.length == 0) return "?";
            StringBuilder b = new StringBuilder(shortName(st[0]));
            String game = null;
            String mod = null;
            for (StackTraceElement e : st) {
                String c = e.getClassName();
                if (game == null && (c.startsWith("net.minecraft.") || c.startsWith("com.mojang."))) game = shortName(e);
                if (mod == null && c.startsWith("mmorpg.")) mod = shortName(e);
            }
            if (game != null && !game.equals(b.toString())) b.append(" < ").append(game);
            if (mod != null) b.append(" < ").append(mod);
            return b.toString();
        }

        static String shortName(StackTraceElement e) {
            String c = e.getClassName();
            return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName();
        }

        synchronized void reset() {
            samples = 0;
            for (Map<String, Integer> m : counts) m.clear();
        }

        synchronized String report() {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < threads.size(); i++) {
                List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.get(i).entrySet());
                list.sort((x, y) -> y.getValue() - x.getValue());
                b.append(names.get(i)).append(" [");
                for (int k = 0; k < Math.min(5, list.size()); k++) {
                    if (k > 0) b.append(" | ");
                    b.append(Math.round(100.0 * list.get(k).getValue() / Math.max(1, samples))).append("% ").append(list.get(k).getKey());
                }
                b.append("] ");
            }
            return b.toString().trim();
        }
    }
}
