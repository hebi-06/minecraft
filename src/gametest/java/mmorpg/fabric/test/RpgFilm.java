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
import mmorpg.fabric.client.FilmCamera;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.48.2 스킬 영상 (사용자 "스킬들 모아서 나한테 보여줘 모델링하고 모션이 어떤지 봐야겠어"): CI 수동 실행의 only 에 film 과 묶음 열쇠 (filmsk · filmw12 ·
 * filmhero · filmjob1 ~ 3) 가 있으면 그 사진첩이 스킬마다 사진을 틱마다 한 장씩 (영화 카메라 — 몸 왼쪽 뒤 3/4 에서 몸과 과녁을 함께, 몸을 따라감 · HUD 숨김) 찍어
 * films/&lt;번호&gt;_&lt;스킬&gt;/f0000.png … 와 manifest.json (스킬 · 이름 · 사진마다 틱 · 쓴 틱 · 들어간 피해) 에 둠 → CI (ci/films.py) 가 mp4 로,
 * tools/skill_film.py 가 이름을 얹어 영상 하나로.
 * <p>사진은 찍는 동안 게임이 한 틱 이상 흐름 (Fabric — 사진이 다 될 때까지 틱을 돌림): 사진마다 그 틱을 적어 두고 영상은 틱 간격대로 (한 틱 = 0.05초).
 * 찍는 동안은 게임을 느리게 (/tick rate 4 — 틱 사이에 그림이 여러 번 그려져 사진이 첫 틱 안에 끝남: 20틱/초 그대로면 사진 한 장에 두 틱씩 흘러 10fps 였음),
 * 끝나면 20 으로.
 */
final class RpgFilm {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-film");
    /** 카메라: 보는 쪽에서 왼쪽으로 125° (왼쪽 뒤) · dist 칸 · 발보다 height 칸 위, 보는 곳 = 몸 앞 ahead 칸 · lookY 칸 높이 (view 로 바꿈) */
    private static final double ANGLE = Math.toRadians(125);
    private double dist = 6.0;
    private double height = 2.1;
    private double lookY = 1.0;
    private static int clips;
    /** 찍는 동안의 틱 빠르기 (틱/초) */
    private static final int SLOW_RATE = 4;

    private final ClientGameTestContext ctx;
    private TestServerContext server;
    private Path dir;
    private final List<Long> ticks = new ArrayList<>();
    private final Map<String, Object> info = new LinkedHashMap<>();
    private long castTick = -1;
    private double sy;
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
        ctx.runOnClient(mc -> {
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.gui.toastManager().clear();
            Player p = mc.player;
            float yaw = p.getYRot() * (float) (Math.PI / 180);
            sy = p.getY();
            fx = -Math.sin(yaw);
            fz = Math.cos(yaw);
        });
        server.runCommand("tick rate " + SLOW_RATE);
        ctx.waitTicks(2); // 클라이언트가 틱 빠르기를 받음
    }

    /** 카메라를 지금 몸 자리에 맞춤 (가로는 그대로 따라감 · 높이는 60% 만 — 솟구치는 스킬에서 땅이 다 빠지지 않게) */
    private void place() {
        ctx.runOnClient(mc -> {
            Player p = mc.player;
            double by = sy + (p.getY() - sy) * 0.6;
            double lx = fz;
            double lz = -fx;
            double ox = Math.cos(ANGLE) * fx + Math.sin(ANGLE) * lx;
            double oz = Math.cos(ANGLE) * fz + Math.sin(ANGLE) * lz;
            double cx = p.getX() + ox * dist;
            double cy = by + height;
            double cz = p.getZ() + oz * dist;
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

    /** 한 장 (그 틱을 적음) — 사진이 틱을 돌리지 않았으면 한 틱 기다림 */
    void frame() {
        place();
        long t = now();
        ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "f%04d", ticks.size())).disableCounterPrefix().withSize(1280, 720)
                .withDestinationDir(dir));
        ticks.add(t);
        if (now() == t) ctx.waitTick();
    }

    void frames(int n) {
        for (int i = 0; i < n; i++) frame();
    }

    /** 스킬 입력을 보낸 때 (영상의 0초 기준) */
    void cast() {
        castTick = now();
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
        server.runCommand("tick rate 20");
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> {
            FilmCamera.clear();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
    }
}
