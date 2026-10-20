package mmorpg.fabric.client;

import java.util.Locale;
import mmorpg.core.Runes;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.mount.Riding;
import mmorpg.fabric.rune.RuneItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 스킬 칸 HUD (11단계 — 메이플 퀵슬롯처럼 화면 오른쪽 아래): 칸마다 룬 그림 · 등급 색 테두리 · 키 글자 (왼쪽 위),
 * 재사용 대기 = 위에서 내려 덮는 어두운 덮개 (줄어듦) + 남은 초, 연출 = 올림 (금빛 고리) · 시전 (흰 번쩍 + 퍼지는 고리) · 준비 (등급 색 고리) ·
 * 눌림 (그림이 눌렸다 돌아옴) · 막힘 (빨간 덮개 + 흔들림) · 뺌. 넓으면 한 줄, 좁으면 두 줄 (핫바와 겹치지 않게).
 * 무기 전용 룬 (0.12.6 사신 = 낫) 은 그 무기를 들지 않으면 어둡게 + 칸 아래에 무기 이름.
 * 칸이 모두 비었으면 "[J] 스킬 칸" 안내. 탈것에 탄 동안은 어둡게 (탄 동안 스킬 불가).
 * 0.26.0 직업 스킬 칸 5 (Shift + 룬 칸 1 ~ 5 의 키 — 키 글자 "⇧R"): 룬 칸 바로 위 (같은 칸 수의 줄 · 오른쪽 맞춤), 위에 작은 "직업 스킬" 글자.
 * 빈 칸은 푸른 기 도는 흐린 테두리 (직업이 추가되면 직업 스킬 그림).
 * 0.27.0 직업: 칸 테두리 · 연출 고리가 직업 색, 잠긴 칸은 어둡게 + 아래에 사유 ("15렙" · "무기" · "스탯"), 칸 위 글자 = 직업 이름 (두 빛 그라데이션) +
 * 자원 보석 (검세 다섯 · 마력 각인 셋 — 찬 만큼 빛남) · 태세 이름과 남은 초 (오른쪽) + 남은 시간 줄.
 */
public final class SkillBarHud {
    private SkillBarHud() {}

    static final Identifier FRAME = Mmorpg.id("hud/skill_slot");
    static final Identifier EDGE = Mmorpg.id("hud/skill_edge");
    static final Identifier GLOW = Mmorpg.id("hud/skill_glow");
    static final Identifier KEY = Mmorpg.id("hud/skill_key");
    /** HUD 칸 크기 · 사이 */
    public static final int S = 20;
    public static final int GAP = 2;

    /** 등급 색 (애드온 등급 보석 그림과 같은 색 — 일반 회색 · 희귀 파랑 · 고귀 보라 · 영웅 주황 · 초월 빨강) */
    static int gradeColor(String grade) {
        return switch (grade == null ? "" : grade) {
            case "rare" -> 0xFF4C8BFF;
            case "noble" -> 0xFFB66CFF;
            case "hero" -> 0xFFFF9A2E;
            case "transcend" -> 0xFFFF4A3D;
            default -> 0xFFC8C8C8;
        };
    }

    /**
     * 칸 자리 {x0, y0, 한 줄 칸 수} (GUI 좌표, 화면 오른쪽 아래): 넓으면 핫바 줄에 한 줄 (8), 아니면 두 줄 (4 × 2 — 위 줄이 RPG HUD 의 방어 · 레벨 · 포인트
     * 줄과 겹치지 않을 때), 그것도 안 되면 오른쪽 끝에 세로 (2 × 4). 1280 × 720 · GUI 3배 (427 × 240) 는 세로, 1920 × 1080 · 3배 (640) 는 한 줄
     */
    public static int[] layout(int w, int h) {
        int right = w - 4;
        int cx = w / 2;
        int n = ClientSkillBar.SIZE;
        int one = n * S + (n - 1) * GAP;
        if (right - one >= cx + 91 + 26) return new int[] {right - one, h - S - 1, n}; // 핫바 오른쪽 끝 + 공격 표시 · 왼손 칸 자리
        int hudRight = cx + 152; // RPG HUD 오른쪽 줄 (방어 · 레벨 · 포인트 · [K]) 의 끝 (넉넉히)
        int cols = (n + 1) / 2;
        int two = cols * S + (cols - 1) * GAP;
        if (right - two >= hudRight) return new int[] {right - two, h - 2 * S - GAP - 1, cols};
        int rows = (n + 1) / 2;
        return new int[] {right - (2 * S + GAP), h - rows * S - (rows - 1) * GAP - 1, 2};
    }

    /** 직업 칸과 룬 칸 사이 · 직업 칸 위 글자 높이 */
    static final int JOB_GAP = 5;
    static final int JOB_CAPTION = 13;

    /**
     * 0.26.0 직업 스킬 칸 자리 {x0, y0, 한 줄 칸 수}: 룬 칸 바로 위에 같은 칸 수 (5보다 많으면 5) 의 줄들, 오른쪽을 룬 칸에 맞춤
     */
    public static int[] jobLayout(int w, int h) {
        int[] l = layout(w, h);
        int n = ClientJobBar.SIZE;
        int cols = Math.min(l[2], n);
        int rows = (n + cols - 1) / cols;
        int runeRight = l[0] + l[2] * S + (l[2] - 1) * GAP;
        int x0 = runeRight - (cols * S + (cols - 1) * GAP);
        int y0 = l[1] - JOB_GAP - rows * S - (rows - 1) * GAP;
        return new int[] {x0, y0, cols};
    }

    /** 직업 칸 i 의 왼쪽 위 (GUI 좌표 — 시험용) */
    public static int[] jobSlotPos(int w, int h, int i) {
        int[] l = jobLayout(w, h);
        return new int[] {l[0] + (i % l[2]) * (S + GAP), l[1] + (i / l[2]) * (S + GAP)};
    }

    /** 칸 i 의 왼쪽 위 (GUI 좌표 — 시험용) */
    public static int[] slotPos(int w, int h, int i) {
        int[] l = layout(w, h);
        return new int[] {l[0] + (i % l[2]) * (S + GAP), l[1] + (i / l[2]) * (S + GAP)};
    }

    /** HUD 그리기 (Fabric HUD API — 핫바 위층) */
    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!RpgHud.active() || mc.player == null || mc.player.isSpectator() || !ClientSkillBar.received()) return;
        // NPC 대화창 (아래쪽 HUD 를 덮음) · 스킬 칸 창 (큰 칸이 따로 있음) 이 열린 동안은 숨김
        if (mmorpg.fabric.client.form.FormClient.dialogOpen() || mc.gui.screen() instanceof SkillBarScreen) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        long now = Util.getMillis();
        int w = g.guiWidth();
        int h = g.guiHeight();
        int[] l = layout(w, h);
        boolean any = ClientSkillBar.any();
        boolean riding = Riding.isMounted(mc.player);
        for (int i = 0; i < ClientSkillBar.SIZE; i++) {
            int x = l[0] + (i % l[2]) * (S + GAP);
            int y = l[1] + (i / l[2]) * (S + GAP);
            drawSlot(g, mc.font, i, x, y, S, partial, now, riding, true);
        }
        // 0.26.0 직업 스킬 칸 (룬 칸 위) + "직업 스킬" 글자
        int[] jl = jobLayout(w, h);
        for (int i = 0; i < ClientJobBar.SIZE; i++) {
            int x = jl[0] + (i % jl[2]) * (S + GAP);
            int y = jl[1] + (i / jl[2]) * (S + GAP);
            drawJobSlot(g, mc.font, i, x, y, S, partial, now, riding, true);
        }
        int rowW = jl[2] * S + (jl[2] - 1) * GAP;
        drawJobCaption(g, mc.font, jl[0], jl[1] - JOB_CAPTION, rowW, partial, now);
        if (!any) {
            String hint = "§e[" + label(MmorpgClient.skillBarKey) + "] §7스킬 칸 §8· §7룬 우클릭 = 올리기";
            int tw = mc.font.width(hint);
            int tx = w - 4 - tw;
            int ty = jl[1] - JOB_CAPTION - 12;
            g.fill(tx - 3, ty - 2, w - 1, ty + 9, 0x90000000);
            g.text(mc.font, hint, tx, ty, 0xFFFFFFFF, true);
        }
    }

    /**
     * 0.26.0 직업 스킬 칸 하나 (HUD · 스킬 칸 창 공용): 빈 칸은 푸른 기 도는 흐린 테두리, 스킬이 있으면 그 그림 · 재사용 대기 덮개 · 남은 초, 눌림 · 막힘 (빨간
     * 덮개 + 흔들림) · 시전 (흰 번쩍) · 준비 (빛) 연출, 키 글자 "⇧R" (key 이면 왼쪽 위)
     */
    static void drawJobSlot(GuiGraphicsExtractor g, Font font, int i, int x0, int y, int s, float partial, long now, boolean dim, boolean key) {
        boolean empty = ClientJobBar.empty(i);
        boolean locked = ClientJobBar.locked(i);
        int edge = ClientJobBar.colorA();
        float deny = phase(ClientJobBar.DENY_AT[i], now, 260);
        int x = x0 + (deny >= 0 ? Math.round((float) Math.sin(deny * Math.PI * 6) * 2 * (1 - deny)) : 0);
        int cx = x + s / 2;
        int cy = y + s / 2;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, s, s, empty ? 0x8CA8C8FF : 0xFFFFFFFF);
        if (!empty) g.blitSprite(RenderPipelines.GUI_TEXTURED, EDGE, x, y, s, s, (locked ? 0x80000000 : 0xFF000000) | edge);
        float cast = phase(ClientJobBar.CAST_AT[i], now, 380);
        if (cast >= 0) ring(g, cx, cy, s * (0.5F + 0.7F * cast), ClientJobBar.colorB(), 0.9F * (1 - cast));
        float ready = phase(ClientJobBar.READY_AT[i], now, 520);
        if (ready >= 0) ring(g, cx, cy, s * (0.62F + 0.18F * (float) Math.sin(ready * Math.PI)), edge, (float) Math.sin(ready * Math.PI));
        ItemStack icon = ClientJobBar.icon(i);
        if (!icon.isEmpty()) {
            float press = phase(ClientJobBar.PRESS_AT[i], now, 160);
            float k = s / 20F * (press >= 0 ? 0.84F + 0.16F * press : 1F);
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(k, k);
            g.item(icon, -8, -8);
            g.pose().popMatrix();
        }
        double frac = ClientJobBar.cooldownFrac(i, partial);
        if (frac > 0) {
            int inner = s - 2;
            int hgt = (int) Math.ceil(inner * frac);
            g.fill(x + 1, y + 1, x + s - 1, y + 1 + hgt, 0xB4000008);
            if (hgt > 0 && hgt < inner) g.fill(x + 1, y + hgt, x + s - 1, y + 1 + hgt, 0x70FFFFFF);
            double left = ClientJobBar.cooldownLeft(i, partial);
            String t = left >= 10 ? String.valueOf((int) Math.ceil(left)) : String.format(Locale.ROOT, "%.1f", left);
            int tw = font.width(t);
            g.text(font, t, cx - tw / 2 + 1, key ? y + s - 9 : cy - 3, left < 1 ? 0xFFFFE066 : 0xFFFFFFFF, true);
        }
        if (cast >= 0 && cast < 0.5F) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.75F * (1 - cast * 2), 0xFFFFFF));
        if (ready >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.35F * (float) Math.sin(ready * Math.PI), 0xFFFFFF));
        float press = phase(ClientJobBar.PRESS_AT[i], now, 160);
        if (empty && press >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.3F * (1 - press), 0xFFFFFF));
        if (deny >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.5F * (1 - deny), 0xFF2A2A));
        if (dim) g.fill(x, y, x + s, y + s, 0x88000000);
        // 0.27.0 잠김: 어둡게 + 아래에 짧은 사유 (레벨 "15렙" · 무기 "무기" · 요구 스탯 "스탯" — 작은 글자)
        if (locked && !dim) {
            g.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0xA0080610);
            String lock = ClientJobBar.lock(i);
            String t = lock.endsWith("렙") ? lock : lock.contains("스탯") ? "스탯" : "무기";
            int col = lock.endsWith("렙") ? 0xFFFFB04A : 0xFFFF6A6A;
            float k = 0.75F;
            g.pose().pushMatrix();
            g.pose().translate(cx, y + s - 7);
            g.pose().scale(k, k);
            g.text(font, t, -font.width(t) / 2, 0, col, true);
            g.pose().popMatrix();
        }
        if (key) {
            String label = ClientJobBar.keyLabel(i);
            if (!label.isEmpty()) {
                int lw = font.width(label);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, KEY, x - 2, y - 2, lw + 5, 11, empty ? 0xB0FFFFFF : 0xFFFFFFFF);
                g.text(font, label, x + 1, y, empty ? 0xFF98A8C0 : 0xFFFFFFFF, true);
            }
        }
    }

    /**
     * 0.27.0 직업 칸 위 글자 (x, y = 왼쪽 위 · w = 칸 줄 너비): 직업 이름 (첫째 빛 → 둘째 빛 그라데이션, 직업이 없으면 "직업 스킬") + 자원 보석
     * (검세 · 마력 각인 — 찬 것은 둘째 빛, 빈 것은 어두운 테두리, 다 차면 반짝임) · 오른쪽에 태세 이름과 남은 초 + 아래 남은 시간 줄
     */
    static void drawJobCaption(GuiGraphicsExtractor g, Font font, int x, int y, int w, float partial, long now) {
        if (ClientJobBar.jobId().isEmpty()) {
            g.text(font, ClientJobBar.any() ? "§7직업 스킬" : "§8직업 스킬", x, y, 0xFFFFFFFF, true);
            return;
        }
        int a = ClientJobBar.colorA();
        int b = ClientJobBar.colorB();
        int tx = gradientText(g, font, ClientJobBar.jobName(), x, y, a, b);
        int max = ClientJobBar.gaugeMax();
        if (max > 0) {
            int v = Math.min(max, ClientJobBar.gaugeValue());
            boolean full = v >= max;
            float pulse = full ? 0.5F + 0.5F * (float) Math.sin(now / 140.0) : 0;
            int px = tx + 4;
            for (int i = 0; i < max; i++) {
                int bx = px + i * 6;
                int by = y + 1;
                // 마름모 보석 (5 × 5): 바깥 어두운 테두리 + 안쪽 (찬 것은 둘째 빛)
                g.fill(bx + 2, by, bx + 3, by + 5, 0xC0000000);
                g.fill(bx, by + 2, bx + 5, by + 3, 0xC0000000);
                g.fill(bx + 1, by + 1, bx + 4, by + 4, 0xC0000000);
                if (i < v) {
                    int col = 0xFF000000 | lerp(b, 0xFFFFFF, pulse * 0.6F);
                    g.fill(bx + 2, by + 1, bx + 3, by + 4, col);
                    g.fill(bx + 1, by + 2, bx + 4, by + 3, col);
                } else {
                    g.fill(bx + 2, by + 2, bx + 3, by + 3, 0x80FFFFFF & (0xFF000000 | a));
                }
            }
        }
        String stance = ClientJobBar.stance();
        if (!stance.isEmpty()) {
            double left = ClientJobBar.stanceLeft(partial);
            String t = stance + " " + String.format(Locale.ROOT, "%.1f", left);
            int sw = font.width(t);
            g.text(font, t, x + w - sw, y, 0xFF000000 | a, true);
            double frac = ClientJobBar.stanceFrac(partial);
            int bw = (int) Math.round(w * frac);
            g.fill(x, y + 10, x + w, y + 11, 0x80000000);
            g.fill(x + w - bw, y + 10, x + w, y + 11, 0xFF000000 | b);
        }
    }

    /** 글자마다 a → b 로 바뀌는 색 (굵게) — 그린 끝 x */
    static int gradientText(GuiGraphicsExtractor g, Font font, String text, int x, int y, int a, int b) {
        int n = text.codePointCount(0, text.length());
        int cx = x;
        int i = 0;
        for (int off = 0; off < text.length(); ) {
            int cp = text.codePointAt(off);
            String ch = new String(Character.toChars(cp));
            int col = 0xFF000000 | lerp(a, b, n <= 1 ? 0 : i / (float) (n - 1));
            g.text(font, "§l" + ch, cx, y, col, true);
            cx += font.width("§l" + ch);
            off += Character.charCount(cp);
            i++;
        }
        return cx;
    }

    /** 0xRRGGBB 사이 t (0 ~ 1) */
    static int lerp(int a, int b, float t) {
        float k = Mth.clamp(t, 0, 1);
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int gg = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bb = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (gg << 8) | bb;
    }

    static String label(net.minecraft.client.KeyMapping k) {
        String s = ClientSkillBar.shortKey(k);
        return s.isEmpty() ? "-" : s;
    }

    /** 0 ~ 1 사이 진행 (since 밀리초 전부터 dur 밀리초 동안 — 범위 밖이면 -1) */
    private static float phase(long at, long now, int dur) {
        if (at <= 0) return -1;
        long d = now - at;
        return d < 0 || d > dur ? -1 : d / (float) dur;
    }

    private static int argb(float a, int rgb) {
        return (Mth.clamp(Math.round(a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** 고리 (GLOW 그림 — 가운데 cx, cy · 반지름 r · 색 · 투명도) */
    private static void ring(GuiGraphicsExtractor g, int cx, int cy, float r, int rgb, float a) {
        if (a <= 0.01F || r < 2) return;
        int ir = Math.round(r);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, GLOW, cx - ir, cy - ir, ir * 2, ir * 2, argb(a, rgb));
    }

    /**
     * 칸 하나 (HUD · 스킬 칸 창 공용): s = 칸 크기 (그림은 s / 20 배), dim = 어둡게 (탈것), key = 키 글자를 그릴지
     */
    static void drawSlot(GuiGraphicsExtractor g, Font font, int i, int x0, int y, int s, float partial, long now, boolean dim, boolean key) {
        ItemStack stack = ClientSkillBar.get(i);
        Runes.Rune r = RuneItems.runeOf(stack);
        int grade = gradeColor(r == null ? null : r.grade());
        // 막힘: 짧게 좌우로 흔들림
        float deny = phase(ClientSkillBar.DENY_AT[i], now, 260);
        int x = x0 + (deny >= 0 ? Math.round((float) Math.sin(deny * Math.PI * 6) * 2 * (1 - deny)) : 0);
        int cx = x + s / 2;
        int cy = y + s / 2;
        // 바탕 · 테두리 (빈 칸은 흐리게)
        g.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x, y, s, s, stack.isEmpty() ? 0x99FFFFFF : 0xFFFFFFFF);
        if (r != null) g.blitSprite(RenderPipelines.GUI_TEXTURED, EDGE, x, y, s, s, grade);
        // 올림 · 시전 · 준비 고리 (그림 뒤)
        float eq = phase(ClientSkillBar.EQUIP_AT[i], now, 450);
        if (eq >= 0) ring(g, cx, cy, s * (0.55F + 0.45F * eq), 0xFFD24A, 1 - eq);
        float cast = phase(ClientSkillBar.CAST_AT[i], now, 380);
        if (cast >= 0) ring(g, cx, cy, s * (0.5F + 0.7F * cast), grade, 0.9F * (1 - cast));
        float ready = phase(ClientSkillBar.READY_AT[i], now, 520);
        if (ready >= 0) ring(g, cx, cy, s * (0.62F + 0.18F * (float) Math.sin(ready * Math.PI)), grade, (float) Math.sin(ready * Math.PI));
        // 룬 그림 (눌림: 0.84 배로 눌렸다가 돌아옴)
        if (!stack.isEmpty()) {
            float press = phase(ClientSkillBar.PRESS_AT[i], now, 160);
            float k = s / 20F * (press >= 0 ? 0.84F + 0.16F * press : 1F);
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(k, k);
            g.item(stack, -8, -8);
            g.pose().popMatrix();
        }
        // 재사용 대기: 위에서 내려 덮은 어두운 덮개 (남은 비율만큼) + 경계 빛줄 + 남은 초
        double frac = ClientSkillBar.cooldownFrac(i, partial);
        if (frac > 0) {
            int inner = s - 2;
            int hgt = (int) Math.ceil(inner * frac);
            g.fill(x + 1, y + 1, x + s - 1, y + 1 + hgt, 0xB4000008);
            if (hgt > 0 && hgt < inner) g.fill(x + 1, y + hgt, x + s - 1, y + 1 + hgt, 0x70FFFFFF);
            double left = ClientSkillBar.cooldownLeft(i, partial);
            String t = left >= 10 ? String.valueOf((int) Math.ceil(left)) : String.format(Locale.ROOT, "%.1f", left);
            int tw = font.width(t);
            // 남은 초: 키 글자 (왼쪽 위) 를 가리지 않게 아래쪽 가운데 (키 글자가 없는 창의 칸은 가운데)
            int ty = key ? y + s - 9 : cy - 3;
            g.text(font, t, cx - tw / 2 + 1, ty, left < 1 ? 0xFFFFE066 : 0xFFFFFFFF, true);
        }
        // 흰 번쩍 (시전 · 올림), 준비 빛, 막힘 빨강
        if (cast >= 0 && cast < 0.5F) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.75F * (1 - cast * 2), 0xFFFFFF));
        if (eq >= 0 && eq < 0.6F) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.45F * (1 - eq / 0.6F), 0xFFE9A8));
        if (ready >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.35F * (float) Math.sin(ready * Math.PI), 0xFFFFFF));
        if (deny >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.5F * (1 - deny), 0xFF2A2A));
        float rem = phase(ClientSkillBar.REMOVE_AT[i], now, 300);
        if (rem >= 0) ring(g, cx, cy, s * (0.5F + 0.3F * rem), 0x9A9AA8, 0.8F * (1 - rem));
        if (dim) g.fill(x, y, x + s, y + s, 0x88000000);
        // 0.12.6: 무기 전용 룬 (사신 = 낫) 인데 그 무기를 들지 않음 — 어둡게 + 아래에 그 무기 이름 (빨강)
        if (!dim && ClientSkillBar.locked(i)) {
            g.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0x99100008);
            String w = Runes.weaponName(Runes.weaponOnly(r.family()));
            int tw = font.width(w);
            g.text(font, w, cx - tw / 2 + 1, y + s - 9, 0xFFFF5A5A, true);
        }
        // 키 글자 (왼쪽 위 — 작은 키 모양)
        if (key) {
            String label = ClientSkillBar.keyLabel(i);
            if (!label.isEmpty()) {
                int lw = font.width(label);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, KEY, x - 2, y - 2, lw + 5, 11, stack.isEmpty() ? 0xB0FFFFFF : 0xFFFFFFFF);
                g.text(font, label, x + 1, y, stack.isEmpty() ? 0xFFA8A8B4 : 0xFFFFFFFF, true);
            }
        }
    }
}
