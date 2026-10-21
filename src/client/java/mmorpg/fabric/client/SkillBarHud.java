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
    /** 0.27.0 직업 칸 (룬 칸과 다른 네모 퀵슬롯 — tools/job_skill_icons.py) */
    static final Identifier JOB_SLOT = Mmorpg.id("hud/job_slot");
    static final Identifier JOB_EDGE = Mmorpg.id("hud/job_edge");
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
            int ty = jl[1] - JOB_CAPTION - 12 - (ClientJobBar.stance().isEmpty() ? 0 : STANCE_LINE); // 태세 줄이 있으면 그 위
            g.fill(tx - 3, ty - 2, w - 1, ty + 9, 0x90000000);
            g.text(mc.font, hint, tx, ty, 0xFFFFFFFF, true);
        }
    }

    /**
     * 직업 스킬 칸 하나 (HUD · 스킬 칸 창 공용 — 0.27.0 메이플식 퀵슬롯): 네모 칸 (job_slot — 룬 칸과 다른 모양) 에 스킬 아이콘 (job_skill/&lt;스킬 id&gt;
     * 32×32 — 룬 그림 대신), 직업 첫째 빛의 안쪽 줄 · 모서리 ㄱ자 (job_edge), 재사용 대기 = 12시에서 시계 방향으로 걷히는 어두운 덮개 + 남은 초,
     * 연출 = 눌림 (아이콘이 눌렸다 돌아옴) · 막힘 (빨간 덮개 + 흔들림) · 시전 (흰 번쩍 + 퍼지는 직업 색 고리) · 준비 (빛 고리), 잠기면 어둡게 + 아래 사유,
     * 키 글자 "⇧R" (key 이면 왼쪽 위 · 0.75배). 빈 칸은 흐린 칸만
     */
    static void drawJobSlot(GuiGraphicsExtractor g, Font font, int i, int x0, int y, int s, float partial, long now, boolean dim, boolean key) {
        boolean empty = ClientJobBar.empty(i);
        boolean locked = ClientJobBar.locked(i);
        int edge = ClientJobBar.colorA();
        float deny = phase(ClientJobBar.DENY_AT[i], now, 260);
        int x = x0 + (deny >= 0 ? Math.round((float) Math.sin(deny * Math.PI * 6) * 2 * (1 - deny)) : 0);
        int cx = x + s / 2;
        int cy = y + s / 2;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, JOB_SLOT, x, y, s, s, empty ? 0x9CFFFFFF : 0xFFFFFFFF);
        float cast = phase(ClientJobBar.CAST_AT[i], now, 380);
        float ready = phase(ClientJobBar.READY_AT[i], now, 520);
        int in = Math.max(1, s / 10); // 아이콘 둘레 여백 (20칸 → 2)
        if (!empty) {
            float press = phase(ClientJobBar.PRESS_AT[i], now, 160);
            float k = press >= 0 ? 0.84F + 0.16F * press : 1F;
            int is = s - in * 2;
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(k, k);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, jobIcon(ClientJobBar.id(i)), -is / 2, -is / 2, is, is, locked ? 0xFF8C8C8C : 0xFFFFFFFF);
            g.pose().popMatrix();
        }
        double frac = ClientJobBar.cooldownFrac(i, partial);
        if (frac > 0) {
            clockWipe(g, x + in, y + in, s - in * 2, s - in * 2, frac, 0xB8000006);
            double left = ClientJobBar.cooldownLeft(i, partial);
            String t = left >= 10 ? String.valueOf((int) Math.ceil(left)) : String.format(Locale.ROOT, "%.1f", left);
            int tw = font.width(t);
            g.text(font, t, cx - tw / 2 + 1, key ? y + s - 9 : cy - 3, left < 1 ? 0xFFFFE066 : 0xFFFFFFFF, true);
        }
        if (!empty) g.blitSprite(RenderPipelines.GUI_TEXTURED, JOB_EDGE, x, y, s, s, (locked || frac > 0 ? 0x70000000 : 0xFF000000) | edge);
        if (cast >= 0) ring(g, cx, cy, s * (0.5F + 0.7F * cast), ClientJobBar.colorB(), 0.9F * (1 - cast));
        if (ready >= 0) ring(g, cx, cy, s * (0.62F + 0.18F * (float) Math.sin(ready * Math.PI)), edge, (float) Math.sin(ready * Math.PI));
        if (cast >= 0 && cast < 0.5F) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.75F * (1 - cast * 2), 0xFFFFFF));
        if (ready >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.35F * (float) Math.sin(ready * Math.PI), 0xFFFFFF));
        float press = phase(ClientJobBar.PRESS_AT[i], now, 160);
        if (empty && press >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.3F * (1 - press), 0xFFFFFF));
        if (deny >= 0) g.fill(x + 1, y + 1, x + s - 1, y + s - 1, argb(0.5F * (1 - deny), 0xFF2A2A));
        if (dim) g.fill(x, y, x + s, y + s, 0x88000000);
        // 잠김: 어둡게 + 아래에 짧은 사유 (해금 레벨 "15렙" · 든 무기의 레벨 · 직업 무기 아님 "무기" · 요구 스탯 "스탯" — 작은 글자)
        if (locked && !dim) {
            g.fill(x + 1, y + 1, x + s - 1, y + s - 1, 0x90080610);
            String lock = ClientJobBar.lock(i);
            String t = mmorpg.fabric.job.JobSkills.lockShort(lock);
            int col = t.endsWith("렙") ? 0xFFFFB04A : 0xFFFF6A6A;
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
                // 키 글자는 0.75배 (스킬 아이콘을 덜 가리게 — 룬 칸은 그대로)
                float k = 0.75F;
                int lw = font.width(label);
                g.pose().pushMatrix();
                g.pose().translate(x - 1, y - 1);
                g.pose().scale(k, k);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, KEY, -2, -2, lw + 5, 11, empty ? 0xB0FFFFFF : 0xFFFFFFFF);
                g.text(font, label, 1, 0, empty ? 0xFF98A8C0 : 0xFFFFFFFF, true);
                g.pose().popMatrix();
            }
        }
    }

    /** 직업 스킬 아이콘 (GUI 그림 job_skill/&lt;스킬 id&gt; — tools/job_skill_icons.py) */
    static Identifier jobIcon(String skillId) {
        return Mmorpg.id("job_skill/" + skillId);
    }

    /**
     * 시계 방향으로 걷히는 덮개 (메이플 퀵슬롯의 재사용 대기): frac = 남은 비율 (1 = 다 덮음). 12시에서 시계 방향으로 지난 몫 (1 - frac) 이 걷히고 남은
     * 쪽만 덮음 — 줄마다 덮인 칸 구간을 한 번에 칠함
     */
    static void clockWipe(GuiGraphicsExtractor g, int x0, int y0, int w, int h, double frac, int color) {
        if (!(frac > 0) || w <= 0 || h <= 0) return;
        if (frac >= 1) {
            g.fill(x0, y0, x0 + w, y0 + h, color);
            return;
        }
        double done = (1 - frac) * Math.PI * 2;
        double cx = w / 2.0;
        double cy = h / 2.0;
        for (int yy = 0; yy < h; yy++) {
            int start = -1;
            for (int xx = 0; xx <= w; xx++) {
                boolean cov = false;
                if (xx < w) {
                    double a = Math.atan2(xx + 0.5 - cx, -(yy + 0.5 - cy)); // 12시 = 0, 시계 방향으로 커짐
                    if (a < 0) a += Math.PI * 2;
                    cov = a >= done;
                }
                if (cov && start < 0) start = xx;
                else if (!cov && start >= 0) {
                    g.fill(x0 + start, y0 + yy, x0 + xx, y0 + yy + 1, color);
                    start = -1;
                }
            }
        }
    }

    /**
     * 0.27.0 직업 칸 위 글자 (x, y = 왼쪽 위 · w = 칸 줄 너비): 직업 이름 (첫째 빛 → 둘째 빛 그라데이션, 직업이 없으면 "직업 스킬") + 자원 보석
     * (검세 · 마력 각인 — 찬 것은 둘째 빛, 빈 것은 어두운 테두리, 다 차면 반짝임). 이름 + 보석이 칸 줄보다 넓으면 (좁은 화면의 두 칸 배치) 칸 줄의 오른쪽 끝에
     * 맞춤 (화면 밖으로 나가지 않게). 태세는 한 줄 위에 이름과 남은 초 (오른쪽 맞춤) + 칸 바로 위에 남은 시간 줄
     */
    static void drawJobCaption(GuiGraphicsExtractor g, Font font, int x, int y, int w, float partial, long now) {
        if (ClientJobBar.jobId().isEmpty()) {
            g.text(font, ClientJobBar.any() ? "§7직업 스킬" : "§8직업 스킬", x, y, 0xFFFFFFFF, true);
            return;
        }
        int a = ClientJobBar.colorA();
        int b = ClientJobBar.colorB();
        String name = ClientJobBar.jobName();
        int max = ClientJobBar.gaugeMax();
        String job = ClientJobBar.jobId();
        // 0.28.0 자원이 큰 직업 (전사 분노 0 ~ 100) 은 막대, 나머지는 보석 (검세 · 각인 = 마름모, 매지션 연쇄 = 고리, 궁수 호흡 = 과녁,
        // 0.29.0 암살자 표식 = 꽃, 창술사 창끝 = 창날, 사신 혼 = 혼불)
        boolean bar = max > 10;
        int gw = max <= 0 ? 0 : bar ? RAGE_W : max * PIP - 1;
        int total = gradientWidth(font, name) + (max > 0 ? 4 + gw : 0);
        int x0 = total > w ? Math.max(2, x + w - total) : x;
        int tx = gradientText(g, font, name, x0, y, a, b);
        if (max > 0) {
            int v = Math.min(max, ClientJobBar.gaugeValue());
            boolean full = v >= max;
            float pulse = full ? 0.5F + 0.5F * (float) Math.sin(now / 140.0) : 0;
            int px = tx + 4;
            if (bar) {
                drawRageBar(g, px, y + 1, v, max, a, b, pulse, now);
            } else {
                for (int i = 0; i < max; i++) {
                    int bx = px + i * PIP;
                    int by = y + 1;
                    int col = 0xFF000000 | lerp(b, 0xFFFFFF, pulse * 0.6F);
                    int dim = 0x80FFFFFF & (0xFF000000 | a);
                    // 0.29.0 둘째 빛이 짙은 직업 (암살자 짙은 보라 · 사신 짙은 청록) 은 찬 것을 첫째 빛으로
                    int bright = 0xFF000000 | lerp(a, 0xFFFFFF, 0.15F + pulse * 0.5F);
                    switch (job) {
                        case "mage" -> {
                            // 금빛 고리 (5 × 5): 찬 것은 고리 전체가 둘째 빛 + 가운데 흰 점
                            g.fill(bx + 1, by, bx + 4, by + 5, 0xC0000000);
                            g.fill(bx, by + 1, bx + 5, by + 4, 0xC0000000);
                            int c = i < v ? col : dim;
                            g.fill(bx + 1, by, bx + 4, by + 1, c);
                            g.fill(bx + 1, by + 4, bx + 4, by + 5, c);
                            g.fill(bx, by + 1, bx + 1, by + 4, c);
                            g.fill(bx + 4, by + 1, bx + 5, by + 4, c);
                            if (i < v) g.fill(bx + 2, by + 2, bx + 3, by + 3, 0xFFFFFFFF);
                        }
                        case "archer" -> {
                            // 과녁 (5 × 5): 바깥 네모 줄 + 가운데 점 — 찬 것은 둘째 빛
                            g.fill(bx, by, bx + 5, by + 5, 0xC0000000);
                            int c = i < v ? col : dim;
                            g.fill(bx, by, bx + 5, by + 1, c);
                            g.fill(bx, by + 4, bx + 5, by + 5, c);
                            g.fill(bx, by + 1, bx + 1, by + 4, c);
                            g.fill(bx + 4, by + 1, bx + 5, by + 4, c);
                            g.fill(bx + 2, by + 2, bx + 3, by + 3, i < v ? 0xFFFFFFFF : dim);
                        }
                        case "assassin" -> {
                            // 꽃 (5 × 5): 둥근 어두운 바탕 · 찬 것은 꽃잎 여덟 점 (첫째 빛) + 가운데 흰 점
                            g.fill(bx + 1, by, bx + 4, by + 5, 0xC0000000);
                            g.fill(bx, by + 1, bx + 5, by + 4, 0xC0000000);
                            if (i < v) {
                                g.fill(bx + 2, by, bx + 3, by + 1, bright);
                                g.fill(bx + 2, by + 4, bx + 3, by + 5, bright);
                                g.fill(bx, by + 2, bx + 1, by + 3, bright);
                                g.fill(bx + 4, by + 2, bx + 5, by + 3, bright);
                                g.fill(bx + 1, by + 1, bx + 2, by + 2, bright);
                                g.fill(bx + 3, by + 1, bx + 4, by + 2, bright);
                                g.fill(bx + 1, by + 3, bx + 2, by + 4, bright);
                                g.fill(bx + 3, by + 3, bx + 4, by + 4, bright);
                                g.fill(bx + 2, by + 2, bx + 3, by + 3, 0xFFFFFFFF);
                            } else {
                                g.fill(bx + 2, by + 2, bx + 3, by + 3, dim);
                            }
                        }
                        case "lancer" -> {
                            // 창날 (5 × 8 — 하나뿐이라 줄 높이만큼 크게: 5 × 5 는 글자 옆 점처럼 보였음, 사진 검토): 위가 뾰족한 잎 모양 날 · 아래 자루,
                            // 켜지면 첫째 빛 (금빛) + 가운데 흰 줄 (기세가 남은 동안 반짝임), 꺼지면 어두운 날
                            int c = i < v ? bright : dim;
                            int ty = y - 1;
                            g.fill(bx + 1, ty, bx + 4, ty + 8, 0xC0000000);
                            g.fill(bx, ty + 2, bx + 5, ty + 4, 0xC0000000);
                            g.fill(bx + 2, ty, bx + 3, ty + 1, c);
                            g.fill(bx + 1, ty + 1, bx + 4, ty + 2, c);
                            g.fill(bx, ty + 2, bx + 5, ty + 4, c);
                            g.fill(bx + 1, ty + 4, bx + 4, ty + 5, c);
                            g.fill(bx + 2, ty + 5, bx + 3, ty + 8, i < v ? col : dim);
                            if (i < v) g.fill(bx + 2, ty + 1, bx + 3, ty + 4, 0xFFFFFFFF);
                        }
                        case "reaper" -> {
                            // 혼불 (5 × 5): 위로 가늘어지는 불꽃 — 찬 것은 첫째 빛 (청록) + 아래 흰 심지
                            g.fill(bx + 1, by, bx + 4, by + 5, 0xC0000000);
                            g.fill(bx, by + 2, bx + 5, by + 5, 0xC0000000);
                            int c = i < v ? bright : dim;
                            g.fill(bx + 2, by, bx + 3, by + 1, c);
                            g.fill(bx + 1, by + 1, bx + 4, by + 3, c);
                            g.fill(bx, by + 3, bx + 5, by + 4, c);
                            g.fill(bx + 1, by + 4, bx + 4, by + 5, c);
                            if (i < v) g.fill(bx + 2, by + 3, bx + 3, by + 4, 0xFFFFFFFF);
                        }
                        default -> {
                            // 마름모 보석 (5 × 5): 바깥 어두운 테두리 + 안쪽 (찬 것은 둘째 빛)
                            g.fill(bx + 2, by, bx + 3, by + 5, 0xC0000000);
                            g.fill(bx, by + 2, bx + 5, by + 3, 0xC0000000);
                            g.fill(bx + 1, by + 1, bx + 4, by + 4, 0xC0000000);
                            if (i < v) {
                                g.fill(bx + 2, by + 1, bx + 3, by + 4, col);
                                g.fill(bx + 1, by + 2, bx + 4, by + 3, col);
                            } else {
                                g.fill(bx + 2, by + 2, bx + 3, by + 3, dim);
                            }
                        }
                    }
                }
            }
        }
        String stance = ClientJobBar.stance();
        if (!stance.isEmpty()) {
            double left = ClientJobBar.stanceLeft(partial);
            String t = stance + " " + String.format(Locale.ROOT, "%.1f", left);
            int sw = font.width(t);
            int sx = Math.max(2, x + w - sw);
            g.text(font, t, sx, y - STANCE_LINE, 0xFF000000 | a, true);
            double frac = ClientJobBar.stanceFrac(partial);
            int bw = (int) Math.round(w * frac);
            g.fill(x, y + 10, x + w, y + 11, 0x80000000);
            g.fill(x + w - bw, y + 10, x + w, y + 11, 0xFF000000 | b);
        }
    }

    /** 자원 보석 간격 · 태세 줄 높이 (직업 이름 줄 위) */
    static final int PIP = 6;
    static final int STANCE_LINE = 11;
    /** 0.28.0 분노 막대 너비 */
    static final int RAGE_W = 30;

    /**
     * 0.28.0 전사 분노 막대 (RAGE_W × 5): 어두운 테두리 · 첫째 빛 → 둘째 빛으로 차는 줄 · 가운데 (반) 눈금 — 다 차면 깜빡이며 위로 불티 몇 점
     */
    static void drawRageBar(GuiGraphicsExtractor g, int x, int y, int v, int max, int a, int b, float pulse, long now) {
        int w = RAGE_W;
        g.fill(x, y, x + w, y + 5, 0xC0000000);
        int inner = w - 2;
        int fillW = (int) Math.round(inner * Math.min(1.0, v / (double) Math.max(1, max)));
        for (int i = 0; i < fillW; i++) {
            float t = inner <= 1 ? 1 : i / (float) (inner - 1);
            int c = lerp(lerp(a, b, t), 0xFFFFFF, pulse * 0.45F);
            g.fill(x + 1 + i, y + 1, x + 2 + i, y + 4, 0xFF000000 | c);
        }
        g.fill(x + 1, y + 1, x + 1 + fillW, y + 2, 0x40FFFFFF);
        int half = x + 1 + inner / 2;
        g.fill(half, y, half + 1, y + 5, v >= max / 2 ? 0xFFFFFFFF : 0x80FFFFFF);
        if (v >= max) {
            for (int i = 0; i < 4; i++) {
                int fx = x + 3 + (int) ((now / 90 + i * 7) % (w - 6));
                int fy = y - 1 - (int) ((now / 60 + i * 3) % 3);
                g.fill(fx, fy, fx + 1, fy + 1, 0xFF000000 | lerp(b, 0xFFE070, 0.6F));
            }
        }
    }

    /** gradientText 로 그릴 때의 너비 (글자마다 굵게) */
    static int gradientWidth(Font font, String text) {
        int wsum = 0;
        for (int off = 0; off < text.length(); ) {
            int cp = text.codePointAt(off);
            wsum += font.width("§l" + new String(Character.toChars(cp)));
            off += Character.charCount(cp);
        }
        return wsum;
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
