package mmorpg.fabric.client;

import mmorpg.fabric.RpgNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.fog.FogData;

/**
 * 초월 룬 하늘빛 · 화면 번쩍임 (서버의 Ult — 베드락 /fog · 카메라 페이드 대신).
 * 하늘빛: 안개가 start ~ end 칸에서 룬의 색으로 짙어지고 하늘도 그 색으로 (FogRendererMixin 이 안개를 바꿈) — 0.5초에 걸쳐 물들고 걷힘.
 * 번쩍임: 화면 전체를 색으로 덮었다가 걷음 (HUD 맨 위).
 * 테두리 빛 (8단계 — 6돌 초월 룬의 각성 연출): 하늘빛이 있는 동안 화면 가장자리가 하늘빛 색으로 빛남 (짙기만큼, 천천히 숨 쉬듯).
 */
public final class UltFx {
    private UltFx() {}

    /** 물들고 걷히는 시간 (ms) */
    private static final double SKY_FADE_MS = 500;
    /** 번쩍임이 가장 짙을 때 (1 = 화면을 다 덮음) */
    private static final float FLASH_MAX = 0.85F;
    /** 테두리 빛이 가장 짙을 때 (가장자리) · 두께 (화면 짧은 변의 비율) */
    private static final float EDGE_MAX = 0.62F;
    private static final float EDGE_SIZE = 0.2F;

    private static int skyToken;
    private static long skyStart;
    private static long skyUntil;
    private static float skyStartDist;
    private static float skyEndDist;
    private static float skyR;
    private static float skyG;
    private static float skyB;
    private static float skyEdge;

    private static long flashStart;
    private static float flashR;
    private static float flashG;
    private static float flashB;
    private static float flashIn;
    private static float flashHold;
    private static float flashOut;

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** 하늘빛 패킷: ticks 0 이면 (같은 표의 하늘빛만) 걷음 */
    public static void sky(RpgNet.SkyPayload p) {
        long now = nowMs();
        if (p.ticks() <= 0) {
            if (p.token() == skyToken && skyUntil > now) skyUntil = Math.min(skyUntil, now + (long) SKY_FADE_MS);
            return;
        }
        boolean showing = skyUntil > now;
        skyToken = p.token();
        // 이미 물든 하늘이면 그 짙기에서 이어감 (다시 옅어졌다 물들지 않게)
        if (!showing) skyStart = now;
        skyUntil = now + p.ticks() * 50L;
        skyStartDist = p.start();
        skyEndDist = p.end();
        skyR = p.r();
        skyG = p.g();
        skyB = p.b();
        skyEdge = Math.max(0, Math.min(1, p.edge()));
    }

    public static void flash(RpgNet.FlashPayload p) {
        flashStart = nowMs();
        flashR = p.r();
        flashG = p.g();
        flashB = p.b();
        flashIn = Math.max(0, p.fadeIn());
        flashHold = Math.max(0, p.hold());
        flashOut = Math.max(0.01F, p.fadeOut());
    }

    public static void clear() {
        skyUntil = 0;
        skyToken = 0;
        skyEdge = 0;
        flashStart = 0;
    }

    /** 지금 테두리 빛 짙기 (0 ~ 1 — 시험용) */
    public static float edgeStrength() {
        return skyEdge * skyStrength();
    }

    /** 하늘빛 짙기 (0 ~ 1 — 사진첩 시험도 봄) */
    public static float skyStrength() {
        long now = nowMs();
        if (skyUntil <= now) return 0;
        double in = (now - skyStart) / SKY_FADE_MS;
        double out = (skyUntil - now) / SKY_FADE_MS;
        return (float) Math.max(0, Math.min(1, Math.min(in, out)));
    }

    /** 안개 바꾸기 (FogRenderer.setupFog 끝): 가까운 안개 · 하늘 · 구름 끝을 하늘빛 거리로, 안개 색을 하늘빛 색으로 (짙기만큼) */
    public static void applyFog(FogData d) {
        float k = skyStrength();
        if (k <= 0 || d == null) return;
        d.environmentalStart = lerp(k, d.environmentalStart, Math.min(d.environmentalStart, skyStartDist));
        d.environmentalEnd = lerp(k, d.environmentalEnd, Math.min(d.environmentalEnd, skyEndDist));
        d.skyEnd = lerp(k, d.skyEnd, Math.min(d.skyEnd, skyEndDist));
        d.cloudEnd = lerp(k, d.cloudEnd, Math.min(d.cloudEnd, skyEndDist));
        if (d.color != null) d.color.set(lerp(k, d.color.x, skyR), lerp(k, d.color.y, skyG), lerp(k, d.color.z, skyB), d.color.w);
    }

    /** 화면 테두리 빛 (하늘빛 색 — 가장자리에서 안쪽으로 옅어짐, 1.2초 주기로 숨 쉬듯) */
    static void renderEdge(GuiGraphicsExtractor g) {
        float k = edgeStrength();
        if (k <= 0.01F) return;
        double breathe = 0.82 + 0.18 * Math.sin(nowMs() / 1000.0 * Math.PI * 2 / 1.2);
        int alpha = (int) Math.round(Math.max(0, Math.min(1, k * breathe)) * EDGE_MAX * 255);
        if (alpha <= 0) return;
        // 하늘빛 색을 조금 밝게 (어두운 하늘빛도 빛처럼 보이게)
        int r = Math.min(255, Math.round((skyR * 0.7F + 0.3F) * 255));
        int gg = Math.min(255, Math.round((skyG * 0.7F + 0.3F) * 255));
        int b = Math.min(255, Math.round((skyB * 0.7F + 0.3F) * 255));
        int rgb = (r << 16) | (gg << 8) | b;
        int on = (alpha << 24) | rgb;
        int off = rgb; // 알파 0
        int w = g.guiWidth();
        int h = g.guiHeight();
        int t = Math.max(8, Math.round(Math.min(w, h) * EDGE_SIZE));
        g.fillGradient(0, 0, w, t, on, off); // 위
        g.fillGradient(0, h - t, w, h, off, on); // 아래
        // 왼쪽 · 오른쪽: 세로 그러데이션을 90도 돌려서 (로컬 x = 화면 세로, 로컬 y = 가장자리에서 안쪽)
        g.pose().pushMatrix();
        g.pose().translate(0, h);
        g.pose().rotate((float) (-Math.PI / 2));
        g.fillGradient(0, 0, h, t, on, off);
        g.pose().popMatrix();
        g.pose().pushMatrix();
        g.pose().translate(w, 0);
        g.pose().rotate((float) (Math.PI / 2));
        g.fillGradient(0, 0, h, t, on, off);
        g.pose().popMatrix();
    }

    /** 화면 번쩍임 그리기 (HUD 맨 위) */
    static void renderFlash(GuiGraphicsExtractor g) {
        if (flashStart == 0) return;
        double t = (nowMs() - flashStart) / 1000.0;
        double a;
        if (t < flashIn) a = flashIn <= 0 ? 1 : t / flashIn;
        else if (t < flashIn + flashHold) a = 1;
        else if (t < flashIn + flashHold + flashOut) a = 1 - (t - flashIn - flashHold) / flashOut;
        else {
            flashStart = 0;
            return;
        }
        int alpha = (int) Math.round(Math.max(0, Math.min(1, a)) * FLASH_MAX * 255);
        if (alpha <= 0) return;
        int rgb = (Math.round(flashR * 255) << 16) | (Math.round(flashG * 255) << 8) | Math.round(flashB * 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | (rgb & 0xFFFFFF));
    }

    private static float lerp(float k, float a, float b) {
        return a + (b - a) * k;
    }
}
