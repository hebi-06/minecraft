package mmorpg.fabric.client;

import mmorpg.fabric.job.JobUpgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;

/**
 * 0.31.0 직업 스킬 강화 성공 화면 연출 (서버 JobUpgrades.FxPayload — 강화한 사람에게만): 화면 가운데 위쪽에
 * <ul>
 * <li>어두운 띠 · 직업 색 빛살 (천천히 돎 — 5단계는 금빛 빛살이 거꾸로 하나 더) · 빛 덩이 · 퍼지는 빛 고리</li>
 * <li>스킬 아이콘 (메이플식 직업 스킬 아이콘 · 네모 칸 · 직업 색 테) — 작게 나타나 크게 튀었다 돌아옴 · 흰 번쩍임 · 둘레 반짝임, 처음 0.25초 온 화면이 직업
 * 색으로 옅게 번쩍임</li>
 * <li>이름 (직업 색) + "+3" (금빛) · 별 다섯 (오른 별은 0.45초에 금빛으로 크게 튀어 들어옴 · 반짝임 · 고리) · 바뀐 값 줄 ("위력 ×1.2 → ×1.3"),
 * 5단계는 별 옆 "MAX"</li>
 * </ul>
 * 자리: 조준점 (화면 가운데) 을 피해 아이콘 · 이름 · 별은 가운데 위, 바뀐 값 줄은 가운데 아래. 2.2초 (처음 0.15초 나타남 · 끝 0.45초 사라짐 — 서버는 2.2초
 * 뒤에 강화 창을 다시 엶).
 */
public final class JobUpgradeHud {
    private JobUpgradeHud() {}

    /** 연출 길이 (밀리초) */
    static final long LEN = 2200;
    /** 오른 별이 튀어 들어오는 때 (초) */
    static final float STAR_AT = 0.45F;
    static final int GOLD = 0xFFD24A;

    private static JobUpgrades.FxPayload cur;
    private static long start;

    static void start(JobUpgrades.FxPayload p) {
        cur = p;
        start = Util.getMillis();
    }

    /** 시험 · 사진첩: 지금 연출 중인지 */
    public static boolean active() {
        return cur != null && Util.getMillis() - start <= LEN;
    }

    static void render(GuiGraphicsExtractor g) {
        JobUpgrades.FxPayload p = cur;
        if (p == null) return;
        long ms = Util.getMillis() - start;
        if (ms < 0 || ms > LEN) {
            cur = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float t = ms / 1000F;
        float a = t < 0.15F ? t / 0.15F : t > 1.75F ? Math.max(0, 1 - (t - 1.75F) / 0.45F) : 1;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float cx = w / 2F;
        // 조준점 (화면 가운데) 을 피함: 아이콘 · 이름 · 별은 가운데 위, 바뀐 값 줄은 가운데 아래
        float mid0 = h / 2F;
        float cy = mid0 - 64;
        int colA = p.colorA() & 0xFFFFFF;
        int colB = p.colorB() & 0xFFFFFF;
        boolean max = p.level() >= p.max();
        int n = Math.min(4, p.lines().size());

        // 처음 0.25초: 직업 색이 온 화면에 옅게 번쩍임 (글자를 가리지 않게 옅게)
        if (t < 0.25F) g.fill(0, 0, w, h, GachaArt.argb(Math.round(70 * (1 - t / 0.25F)), GachaArt.mix(colA, 0xFFFFFF, 0.5)));
        // 어두운 띠 (위 · 아래로 옅어짐)
        int top = Math.max(0, Math.round(cy - 58));
        int mid = Math.round(cy + 30);
        int bot = Math.round(mid0 + 18 + n * 11);
        int band = Math.round(150 * a);
        g.fillGradient(0, top, w, mid, GachaArt.argb(0, 0x05030A), GachaArt.argb(band, 0x05030A));
        g.fillGradient(0, mid, w, bot, GachaArt.argb(band, 0x05030A), GachaArt.argb(0, 0x05030A));

        // 빛살 · 빛 덩이 · 고리
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 130 + (max ? 40 : 0), 0F, GachaArt.argb(Math.round(150 * a), colB));
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 190 + (max ? 50 : 0), t * 0.55F, GachaArt.argb(Math.round(120 * a), colA));
        if (max) GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 210, -t * 0.8F, GachaArt.argb(Math.round(140 * a), GOLD));
        if (t > 0.05F && t < 0.65F) {
            float q = (t - 0.05F) / 0.6F;
            GachaArt.sprite(g, GachaArt.RING, cx, cy, 40 + 220 * q, 0F, GachaArt.argb(Math.round(220 * (1 - q)), GachaArt.mix(colA, 0xFFFFFF, 0.35)));
        }

        // 스킬 아이콘 (튀어 커졌다 돌아옴)
        float k = t < 0.16F ? 0.3F + 0.95F * easeOut(t / 0.16F) : t < 0.3F ? 1.25F - 0.25F * ((t - 0.16F) / 0.14F) : 1F;
        int frame = Math.round(44 * k);
        int icon = Math.round(36 * k);
        int alpha = Math.round(255 * a) << 24;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SkillBarHud.JOB_SLOT, Math.round(cx - frame / 2F), Math.round(cy - frame / 2F), frame, frame, alpha | 0xFFFFFF);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SkillBarHud.jobIcon(p.skill()), Math.round(cx - icon / 2F), Math.round(cy - icon / 2F), icon, icon,
                alpha | 0xFFFFFF);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SkillBarHud.JOB_EDGE, Math.round(cx - frame / 2F), Math.round(cy - frame / 2F), frame, frame, alpha | colA);
        if (t > 0.08F && t < 0.42F) {
            float f = 1 - (t - 0.08F) / 0.34F;
            int half = icon / 2;
            g.fill(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half, GachaArt.argb(Math.round(220 * f * a), 0xFFFFFF));
        }
        // 둘레 반짝임 (정해진 자리 — 매 장 흔들리지 않게)
        for (int i = 0; i < 6; i++) {
            float st = 0.12F + i * 0.13F;
            if (t < st || t > st + 0.5F) continue;
            float q = (t - st) / 0.5F;
            double ang = i * 2.39 + 0.6;
            float r = 30 + (i % 3) * 7;
            GachaArt.twinkle(g, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r * 0.8F, 9 * (float) Math.sin(q * Math.PI),
                    GachaArt.argb(Math.round(230 * a), i % 2 == 0 ? GOLD : GachaArt.mix(colA, 0xFFFFFF, 0.5)));
        }

        // 이름 + 단계
        String name = p.name();
        String lv = " +" + p.level();
        float ts = 1.35F;
        float tw = (font.width(name) + font.width(lv)) * ts;
        float tx = cx - tw / 2;
        float ty = cy + 27;
        GachaArt.text(g, font, name, tx, ty, ts, GachaArt.argb(Math.round(255 * a), GachaArt.mix(colA, 0xFFFFFF, 0.25)));
        GachaArt.text(g, font, lv, tx + font.width(name) * ts, ty, ts, GachaArt.argb(Math.round(255 * a), GOLD));

        // 별 다섯 (오른 별은 STAR_AT 에 튀어 들어옴) — 별 줄 가운데가 조준점보다 17 위
        float sy = mid0 - 22;
        float gap = 16;
        for (int i = 0; i < p.max(); i++) {
            float sx = cx + (i - (p.max() - 1) / 2F) * gap;
            boolean filled = i < p.level();
            boolean fresh = i == p.level() - 1;
            if (fresh && t < STAR_AT) filled = false;
            float s = 1.5F;
            int col = filled ? GOLD : 0x5C5C70;
            if (fresh && t >= STAR_AT && t < STAR_AT + 0.3F) {
                float q = (t - STAR_AT) / 0.3F;
                s = 1.5F + 1.6F * (1 - easeOut(q));
                GachaArt.sprite(g, GachaArt.RING, sx, sy + 5, 10 + 46 * q, 0F, GachaArt.argb(Math.round(230 * (1 - q) * a), GOLD));
                GachaArt.twinkle(g, sx + 7, sy - 2, 12 * (1 - q), GachaArt.argb(Math.round(255 * a), 0xFFFFFF));
            }
            GachaArt.textCentered(g, font, filled ? "★" : "☆", sx, sy + 5 - 4.5F * s, s, GachaArt.argb(Math.round(255 * a), col));
        }
        if (max && t >= STAR_AT + 0.1F) {
            float pulse = 0.75F + 0.25F * (float) Math.sin(t * 9);
            GachaArt.text(g, font, "MAX", cx + (p.max() / 2F) * gap + 6, sy - 1, 1.1F, GachaArt.argb(Math.round(255 * a * pulse), GOLD));
        }

        // 바뀐 값 줄 (조준점 아래)
        for (int i = 0; i < n; i++) {
            GachaArt.textCentered(g, font, p.lines().get(i), cx, mid0 + 11 + i * 11, 1F, GachaArt.argb(Math.round(240 * a), 0xEDE8F5));
        }
    }

    private static float easeOut(float x) {
        float c = Math.max(0, Math.min(1, x));
        return 1 - (1 - c) * (1 - c) * (1 - c);
    }
}
