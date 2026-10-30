package mmorpg.fabric.test;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import mmorpg.fabric.client.FilmCamera;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.48.2 스킬 영상 (사용자 "스킬들 모아서 나한테 보여줘 모델링하고 모션이 어떤지 봐야겠어"): CI 수동 실행의 only 에 film 과 묶음 열쇠 (filmsk · filmw12 ·
 * filmhero · filmjob1 ~ 3) 가 있으면 그 사진첩이 스킬마다 사진을 틱마다 한 장씩 (영화 카메라 — 몸 왼쪽 뒤 3/4 에서 몸과 과녁을 함께, 몸을 따라감 · HUD 숨김) 찍어
 * films/&lt;번호&gt;_&lt;스킬&gt;/f0000.png … 와 manifest.json (스킬 · 이름 · 사진마다 틱 · 쓴 틱 · 들어간 피해) 에 둠 → CI (ci/films.py) 가 mp4 로,
 * tools/skill_film.py 가 이름을 얹어 영상 하나로.
 * <p>사진: 창을 1280×720 으로 두고, 틱마다 바로 앞 틱 뒤에 그려진 화면 (main render target) 을 읽어 (Screenshot.takeScreenshot — GPU 에서 읽힌 그림은
 * 렌더 스레드가 다음 그림 때 넘겨줌) 따로 도는 쓰기 스레드가 PNG 로 쓰고 닫음 → 한 틱에 한 장, 게임은 20틱/초 그대로.
 * (예전: Fabric 의 takeScreenshot 은 사진마다 창 크기를 바꿔 (854×480 → 1280×720 → 되돌림) 새로 그리고, 다 읽힐 때까지 틱을 돌려 한 장에 두 틱이 흘렀고 —
 * 게임을 /tick rate 4 · 2 · 1 로 늦춰 맞춤 — 읽은 그림을 닫지 않아 (한 장 3.7MB) 계열 몇 개를 지나면 사진 한 장이 1초 넘게 걸렸음.)
 * 사진마다 그 틱을 적어 두고 영상은 틱 간격대로 (한 틱 = 0.05초).
 */
final class RpgFilm {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-film");
    /** 카메라: 보는 쪽에서 왼쪽으로 angle (기본 125° — 왼쪽 뒤) · dist 칸 · 발보다 height 칸 위, 보는 곳 = 몸 앞 ahead 칸 · lookY 칸 높이 (view · angle 로 바꿈) */
    private double angle = Math.toRadians(125);
    private double dist = 6.0;
    private double height = 2.1;
    private double lookY = 1.0;
    /** 카메라 자리가 몸을 따라가는 몫 (1 = 그대로 따라감; 룬 영상은 0.6 — 보는 곳은 늘 몸을 따라감) */
    private double follow = 1.0;
    private static int clips;
    /** 사진 크기 (창 크기) */
    private static final int W = 1280;
    private static final int H = 720;
    /** PNG 쓰기 (렌더 스레드 밖 — 게임 루프를 막지 않게) */
    private static final ExecutorService SAVE = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "mmorpg-film-save");
        t.setDaemon(true);
        return t;
    });

    private final ClientGameTestContext ctx;
    private TestServerContext server;
    /** GPU 에서 읽는 중인 사진 수 (렌더 스레드가 다음 그림 때 넘겨줌) */
    private final AtomicInteger reading = new AtomicInteger();
    /** 파일로 쓰는 중인 사진 수 (쓰기 스레드) */
    private final AtomicInteger saving = new AtomicInteger();
    /** 영상 앞의 창 크기 (끝나면 되돌림) */
    private int[] prevSize;
    private Path dir;
    private final List<Long> ticks = new ArrayList<>();
    private final Map<String, Object> info = new LinkedHashMap<>();
    private long castTick = -1;
    private double sx;
    private double sy;
    private double sz;
    private double fx;
    private double fz;
    private double ahead;

    RpgFilm(ClientGameTestContext ctx) {
        this.ctx = ctx;
    }

    /** 카메라 거리 · 높이 · 보는 높이 (무기 스킬은 넓게 — 하늘의 먹구름 · 해 · 떨어지는 창이 들어오게) */
    RpgFilm view(double dist, double height, double lookY) {
        this.dist = dist;
        this.height = height;
        this.lookY = lookY;
        return this;
    }

    /** 카메라 방향: 보는 쪽에서 왼쪽으로 몇 도 (90 = 바로 옆, 180 = 바로 뒤) */
    RpgFilm angle(double deg) {
        this.angle = Math.toRadians(deg);
        return this;
    }

    /** 카메라 자리가 몸을 따라가는 몫 (0 ~ 1) */
    RpgFilm follow(double k) {
        this.follow = k;
        return this;
    }

    /** only 에 film 이 있는지 (영상 실행 — 다른 사진은 찍지 않음) */
    static boolean on() {
        return listed("film");
    }

    /** only 에 이 열쇠가 적혀 있는지 (비었을 때 모두 도는 규칙은 빼고 — 영상은 고를 때만) */
    static boolean listed(String key) {
        String v = System.getProperty("mmorpg.only", "");
        if (v.isBlank()) v = System.getenv().getOrDefault("MMORPG_ONLY", "");
        for (String s : v.split(",")) if (s.trim().equalsIgnoreCase(key)) return true;
        return false;
    }

    /** 영상 하나 시작: 지금 플레이어 자리 · 보는 쪽이 카메라의 기준 (스킬을 쓰기 전에 부름) */
    void begin(TestServerContext server, String id, String title, String sub, double ahead) {
        this.server = server;
        clips++;
        this.ahead = ahead;
        ticks.clear();
        info.clear();
        castTick = -1;
        info.put("id", id);
        info.put("title", title);
        info.put("sub", sub);
        dir = FabricLoader.getInstance().getGameDir().resolve("films").resolve(String.format(Locale.ROOT, "%03d_%s", clips, id));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        prevSize = ctx.computeOnClient(mc -> new int[] {mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight()});
        ctx.getInput().resizeWindow(W, H);
        ctx.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.gui.toastManager().clear();
            Player p = mc.player;
            float yaw = p.getYRot() * (float) (Math.PI / 180);
            sx = p.getX();
            sy = p.getY();
            sz = p.getZ();
            fx = -Math.sin(yaw);
            fz = Math.cos(yaw);
        });
        place();
        ctx.waitTicks(2); // 새 창 크기 · 영화 카메라로 한 번 그려 둠 (첫 장이 3인칭 카메라 · 작은 창이 아니게)
    }

    /** 카메라를 지금 몸 자리에 맞춤 (가로는 follow 몫만큼 따라감 · 높이는 60% 만 — 솟구치는 스킬에서 땅이 다 빠지지 않게; 보는 곳은 몸 그대로) */
    private void place() {
        ctx.runOnClient(mc -> {
            Player p = mc.player;
            double by = sy + (p.getY() - sy) * 0.6;
            double lx = fz;
            double lz = -fx;
            double ox = Math.cos(angle) * fx + Math.sin(angle) * lx;
            double oz = Math.cos(angle) * fz + Math.sin(angle) * lz;
            double cx = sx + (p.getX() - sx) * follow + ox * dist;
            double cy = by + height;
            double cz = sz + (p.getZ() - sz) * follow + oz * dist;
            double dx = p.getX() + fx * ahead - cx;
            double dy = by + lookY - cy;
            double dz = p.getZ() + fz * ahead - cz;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            FilmCamera.set(cx, cy, cz, yaw, pitch);
        });
    }

    private long now() {
        return ctx.computeOnClient(mc -> mc.level == null ? 0L : mc.level.getGameTime());
    }

    /** 한 장: 바로 앞 틱 뒤에 그려진 화면을 읽어 씀 (그 틱을 적음) → 다음 그림의 카메라 → 한 틱 */
    void frame() {
        long t = now();
        Path file = dir.resolve(String.format(Locale.ROOT, "f%04d.png", ticks.size()));
        // 쓰기가 밀리면 기다림 (시험 스레드만 — 게임은 이 동안 멈춰 있고 쓰기 스레드는 따로 돎; 렌더 스레드를 기다리는 읽기는 세지 않음)
        for (int i = 0; i < 4000 && saving.get() > 24; i++) sleep(5);
        reading.incrementAndGet();
        ctx.runOnClient(mc -> Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            saving.incrementAndGet();
            reading.decrementAndGet();
            SAVE.execute(() -> {
                try (image) {
                    image.writeToFile(file);
                } catch (IOException e) {
                    LOG.warn("[film] save " + file, e);
                } finally {
                    saving.decrementAndGet();
                }
            });
        }));
        ticks.add(t);
        place();
        ctx.waitTick();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void frames(int n) {
        for (int i = 0; i < n; i++) frame();
    }

    /** 스킬 입력을 보낸 때 (영상의 0초 기준) */
    void cast() {
        castTick = now();
    }

    /** 키를 한 번 누름 (Fabric pressKey 와 같음 — 누르고 떼고 한 틱; 그 틱도 한 장 찍어 쓰는 순간에 사진이 빠지지 않게) · 쓴 때를 적음 */
    void press(net.minecraft.client.KeyMapping key) {
        ctx.getInput().holdKey(key);
        ctx.getInput().releaseKey(key);
        cast();
        frame();
    }

    /** 지금 틱 (입력을 누르고 기다리는 길게 누르기 — since 와 함께) */
    long mark() {
        return now();
    }

    /** from 틱부터 n 틱이 지날 때까지 찍음 */
    void untilSince(long from, int n) {
        int guard = 0;
        while (now() - from < n && guard++ < 400) frame();
    }

    /** 쓴 뒤 n 틱이 지날 때까지 찍음 */
    void until(int n) {
        untilSince(castTick < 0 ? now() : castTick, n);
    }

    /**
     * 0.48.2 룬 영상: 쓴 뒤 min 틱은 꼭 찍고, 그 뒤 quiet 틱 동안 새 피해가 없으면 끝 (최대 max — 버프 · 회복 룬은 min 에서 끝).
     * dealt = 지금까지 과녁에 들어간 피해 합 (서버에서 읽음)
     */
    void untilQuiet(java.util.function.DoubleSupplier dealt, int min, int quiet, int max) {
        long from = castTick < 0 ? now() : castTick;
        double last = dealt.getAsDouble();
        long changed = from;
        int guard = 0;
        while (guard++ < 600) {
            long t = now();
            if (t - from >= max) break;
            if (t - from >= min && t - changed >= quiet) break;
            frame();
            double d = dealt.getAsDouble();
            if (d > last + 1e-6) {
                last = d;
                changed = now();
            }
        }
    }

    void put(String k, Object v) {
        info.put(k, v);
    }

    /** 영상 끝: manifest.json · 카메라 · HUD 되돌림 */
    void end(double dealt) {
        info.put("cast", castTick);
        info.put("ticks", ticks);
        info.put("dealt", Math.round(dealt * 10) / 10.0);
        try {
            Files.writeString(dir.resolve("manifest.json"), new Gson().toJson(info), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOG.info("[film] {} frames={} ticks={} dealt={}", dir.getFileName(), ticks.size(), ticks.isEmpty() ? 0 : ticks.get(ticks.size() - 1) - ticks.get(0),
                String.format(Locale.ROOT, "%.1f", dealt));
        for (int i = 0; i < 100 && reading.get() > 0; i++) ctx.waitTick(); // GPU 읽기가 다 넘어올 때까지 (렌더 스레드)
        for (int i = 0; i < 12000 && saving.get() > 0; i++) sleep(5); // 쓰기가 다 끝날 때까지
        if (reading.get() + saving.get() > 0) LOG.warn("[film] {} unsaved frames: reading={} saving={}", dir.getFileName(), reading.get(), saving.get());
        // 남은 것 치움 (0.48.2 룬 영상: 계열 몇 개를 지나면 장면마다 사진이 1초 넘게 걸림 — 앞 룬이 남긴 글자 · 물체 · 그림이 쌓임):
        // 플레이어 · 과녁 (허스크 — 다음 장면이 새로 세움) 밖의 존재 · 클라이언트 그림 · 입체 효과 물체
        int left = server.computeOnServer(s -> {
            List<net.minecraft.world.entity.Entity> gone = new ArrayList<>();
            for (net.minecraft.server.level.ServerLevel level : s.getAllLevels()) {
                for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                    if (e instanceof net.minecraft.world.entity.player.Player || e instanceof net.minecraft.world.entity.monster.zombie.Husk) continue;
                    gone.add(e);
                }
            }
            for (net.minecraft.world.entity.Entity e : gone) e.discard();
            return gone.size();
        });
        if (left > 0) LOG.info("[film] {} cleared {} leftover entities", dir.getFileName(), left);
        ctx.runOnClient(mc -> {
            mc.particleEngine.clearParticles();
            mmorpg.fabric.client.FxModels.clear();
            FilmCamera.clear();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        if (prevSize != null) ctx.getInput().resizeWindow(prevSize[0], prevSize[1]);
    }
}
