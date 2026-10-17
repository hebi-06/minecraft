package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import mmorpg.core.Gacha;
import mmorpg.core.Js;
import mmorpg.core.Runes;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 뽑기 연출 (0.14.0 — 서버가 결과를 보내면 열림, 화면 전체):
 * <ol>
 * <li>소환 (1.5초): 어둠 속 별 · 가운데 마법진이 한 칸씩 그려지며 돌고, 빛 알갱이가 빨려 듦 — 색은 일반부터 차례로 바뀌다 이번 결과의 가장 높은 등급에서 멈춤
 * (영웅부터 퍼지는 고리, 초월은 화면이 흔들림)</li>
 * <li>섬광 (0.45초): 화면이 하얗게 번쩍 · 등급 색 빛살 · 고리 · 등급 소리</li>
 * <li>공개 — 1회: 등급 색 카드가 튀어나오며 룬 (큰 그림 · 지나가는 빛) · 이름 · 계열 · 픽업 · NEW · 몇 회째. 10회: 카드 10장이 펼쳐지고 하나씩 뒤집힘 —
 * 0.14.1 부터 카드 색은 영웅 미만 흰 카드 · 영웅 이상 노란 카드 (뒷면부터 — 펼쳐질 때 보임, 앞면도 같은 색, 글자는 등급 색을 진하게),
 * 노란 카드는 뒤집기 전에 금빛이 모이고 뒤집을 때 번쩍 · 뒤에 금빛 (초월은 빛살 하나 더 · 둘레 반짝임)</li>
 * <li>끝: 다시 뽑기 · 확인 단추 (10회는 카드에 마우스를 올리면 풀이)</li>
 * </ol>
 * 누르면 다음 단계로 (소환 → 섬광, 10회 뒤집기 → 모두), 오른쪽 위 건너뛰기 · ESC. 결과가 보이면 서버에 알림 (revealed — 영웅 이상 알림 · 빛기둥).
 */
public final class GachaShowScreen extends Screen {
    static final int SUMMON = 1500;
    static final int FLASH = 450;
    static final int REVEAL = SUMMON + FLASH;
    static final int DROP = 400;
    static final int FLIP = 180;
    static final int STEP = 210;
    static final int BUILD = 330;
    static final int CW = 56;
    static final int CH = 77;
    static final int GAP = 10;
    static final int BTN_W = 86;
    static final int BTN_H = 22;

    private static final Identifier BTN = Mmorpg.id("stats/dialog_btn");
    private static final Identifier BTN_HOVER = Mmorpg.id("stats/dialog_btn_hover");

    private final ClientGacha.Result result;
    private final GachaScreen parent;
    private final long start = Util.getMillis();
    private final int top;
    private final boolean multi;
    /** 카드 i 가 뒤집히는 때 (시작에서 밀리초) */
    private final long[] flipAt;
    private final long end;
    private long shift;
    private boolean revealedSent;
    private final float[][] stars = new float[80][4];
    private final boolean[] played = new boolean[32];
    private final boolean[] flipSound;
    private final boolean[] buildSound;

    public GachaShowScreen(ClientGacha.Result result, GachaScreen parent) {
        super(Component.literal("뽑기"));
        this.result = result;
        this.parent = parent;
        int t = 0;
        for (ClientGacha.Pulled p : result.pulls()) t = Math.max(t, GachaArt.gradeIndex(p.grade()));
        this.top = t;
        this.multi = result.pulls().size() > 1;
        int n = result.pulls().size();
        flipAt = new long[n];
        flipSound = new boolean[n];
        buildSound = new boolean[n];
        long at = REVEAL + DROP;
        for (int i = 0; i < n; i++) {
            boolean big = GachaArt.goldCard(result.pulls().get(i).grade());
            flipAt[i] = at + (big ? BUILD : 0);
            at = flipAt[i] + STEP + (big ? 230 : 0);
        }
        end = multi ? flipAt[n - 1] + FLIP + 350 : REVEAL + 700;
        Random r = new Random(result.id() * 31 + 7);
        for (float[] s : stars) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat();
            s[2] = 0.5F + r.nextFloat() * 1.5F;
            s[3] = r.nextFloat() * 6.283F;
        }
    }

    ClientGacha.Result result() {
        return result;
    }

    GachaScreen parent() {
        return parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 시험용: 이 때에 멈춘 연출 (-1 = 흐름) */
    private long frozen = -1;

    /** 지금 (시작에서 밀리초 — 건너뛰기 포함) */
    public long elapsed() {
        return frozen >= 0 ? frozen : Util.getMillis() - start + shift;
    }

    /** 시험용: 연출을 이 때 (밀리초) 에 멈춤 — 사진 */
    public void freezeAt(long ms) {
        frozen = Math.max(0, ms);
    }

    /** 시험용: 멈춘 곳에서 다시 흐름 */
    public void unfreeze() {
        if (frozen < 0) return;
        shift = frozen - (Util.getMillis() - start);
        frozen = -1;
    }

    /** 공개가 끝나는 때 (밀리초) */
    public long endAt() {
        return end;
    }

    /** 카드 i 가 뒤집히는 때 (10회 — 밀리초) */
    public long flipAt(int i) {
        return i >= 0 && i < flipAt.length ? flipAt[i] : end;
    }

    /** 공개가 다 끝났는지 */
    public boolean done() {
        return elapsed() >= end;
    }

    /** 건너뛰기 (공개 끝으로) */
    public void skip() {
        long t = elapsed();
        if (frozen >= 0) frozen = Math.max(frozen, end);
        else if (t < end) shift += end - t;
        sendRevealed();
    }

    private void sendRevealed() {
        if (revealedSent) return;
        revealedSent = true;
        ClientGacha.send("revealed", String.valueOf(result.id()), "");
    }

    @Override
    public void removed() {
        sendRevealed();
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fill(0, 0, width, height, 0xFF060610);
    }

    // ------------------------------------------------------------ 그리기

    private int colorAt(double p) {
        // 일반 → 이번 결과의 가장 높은 등급까지 차례로 (p 0 ~ 0.78 에서 다 올라감)
        double steps = Math.min(1, p / 0.78) * top;
        int a = (int) Math.floor(steps);
        int b = Math.min(top, a + 1);
        return GachaArt.mix(GachaArt.rgb(GachaArt.GRADES.get(a)), GachaArt.rgb(GachaArt.GRADES.get(b)), steps - a);
    }

    private void once(int i, Runnable r) {
        if (i < played.length && !played[i]) {
            played[i] = true;
            r.run();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        long t = elapsed();
        float cx = width / 2F;
        float cy = height / 2F - 8;
        float sec = t / 1000F;
        // 별
        for (float[] s : stars) {
            float a = 0.35F + 0.65F * (0.5F + 0.5F * (float) Math.sin(sec * s[2] * 1.7 + s[3]));
            int x = (int) (s[0] * width);
            int y = (int) (s[1] * height);
            int sz = s[2] > 1.6F ? 2 : 1;
            g.fill(x, y, x + sz, y + sz, GachaArt.argb((int) (a * 200), 0xDDE6FF));
        }
        if (t < SUMMON) drawSummon(g, t, cx, cy);
        else if (t < REVEAL) drawFlash(g, t, cx, cy);
        else {
            if (!multi) sendRevealed();
            List<String> tip = multi ? drawMulti(g, t, cx, cy, mx, my) : drawSingle(g, t, cx, cy);
            if (t >= end) {
                sendRevealed();
                drawButtons(g, mx, my);
            }
            if (tip != null && !tip.isEmpty()) {
                List<Component> lines = new ArrayList<>();
                for (String l : tip) lines.add(Component.literal(BedrockText.toJava(l)));
                g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
            }
        }
        // 건너뛰기
        if (t < end) {
            int[] r = skipRect();
            boolean over = in(r, mx, my);
            String s = (over ? "§f" : "§7") + "건너뛰기 ▶▶";
            g.text(font, s, r[0] + r[2] - font.width(s), r[1] + 4, 0xFFFFFFFF, true);
        }
    }

    private void drawSummon(GuiGraphicsExtractor g, long t, float cx, float cy) {
        double p = t / (double) SUMMON;
        int col = colorAt(p);
        once(0, () -> GachaArt.sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1F, 1F));
        if (t > 450) once(1, () -> GachaArt.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1F));
        if (t > 800) once(2, () -> GachaArt.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1F));
        if (t > 1100) once(3, () -> {
            GachaArt.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.25F, 1F);
            if (top >= 3) GachaArt.sound(SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.8F);
        });
        // 초월: 끝 무렵 화면이 흔들림
        if (top >= 4 && p > 0.72) {
            float amp = (float) (2.5 * (p - 0.72) / 0.28);
            cx += (float) Math.sin(t * 0.09) * amp;
            cy += (float) Math.cos(t * 0.13) * amp;
        }
        float a = (float) Math.min(1, p * 1.6);
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, (float) (90 + 220 * p), 0F, GachaArt.argb((int) (60 + 120 * p), col));
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, (float) (180 + 140 * p), (float) (-t * 0.0008), GachaArt.argb((int) (90 * p), col));
        int frame = (int) Math.min(9, Math.floor(p * 11));
        float size = (float) (110 + 34 * p);
        GachaArt.sprite(g, GachaArt.CIRCLE[frame], cx, cy, size, (float) (t * 0.0015), GachaArt.argb((int) (235 * a), GachaArt.mix(col, 0xFFFFFF, 0.25)));
        GachaArt.sprite(g, GachaArt.CIRCLE[Math.max(0, frame - 2)], cx, cy, size * 0.62F, (float) (-t * 0.0024), GachaArt.argb((int) (160 * a), col));
        // 빨려 드는 빛 알갱이
        for (int i = 0; i < 32; i++) {
            double ph = ((t / 900.0) + i / 32.0) % 1.0;
            double r = 160 * (1 - ph);
            double ang = i * 2.39996 + t * 0.003 + ph * 3;
            float x = cx + (float) (Math.cos(ang) * r);
            float y = cy + (float) (Math.sin(ang) * r * 0.75);
            float s = (float) (5 + 7 * (1 - ph));
            int al = (int) (255 * Math.min(1, ph * 3) * (1 - Math.pow(ph, 4)) * a);
            GachaArt.twinkle(g, x, y, s, GachaArt.argb(al, GachaArt.mix(0xFFFFFF, col, 0.5)));
        }
        // 영웅 이상: 퍼지는 고리
        if (top >= 3 && p > 0.66) {
            for (int k = 0; k < 2; k++) {
                double q = ((t - 990) / 380.0 + k * 0.5) % 1.0;
                GachaArt.sprite(g, GachaArt.RING, cx, cy, (float) (40 + 260 * q), 0F, GachaArt.argb((int) (200 * (1 - q)), col));
            }
        }
        // 가운데 빛
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, (float) (30 + 40 * p), 0F, GachaArt.argb((int) (255 * a), GachaArt.mix(col, 0xFFFFFF, 0.6)));
        GachaArt.textCentered(g, font, "§7룬을 부르는 중…", cx, height - 30, 1F, GachaArt.argb((int) (200 * a), 0xFFFFFF));
    }

    private void drawFlash(GuiGraphicsExtractor g, long t, float cx, float cy) {
        double k = (t - SUMMON) / (double) FLASH;
        int col = GachaArt.rgb(GachaArt.GRADES.get(top));
        once(4, this::flashSound);
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, (float) (300 + 220 * k), (float) (t * 0.0012), GachaArt.argb((int) (230 * (1 - k)), col));
        if (top >= 4) GachaArt.sprite(g, GachaArt.RAYS, cx, cy, (float) (260 + 200 * k), (float) (-t * 0.0016), GachaArt.argb((int) (200 * (1 - k)), 0xFFD27A));
        GachaArt.sprite(g, GachaArt.RING, cx, cy, (float) (60 + 420 * k), 0F, GachaArt.argb((int) (255 * (1 - k)), GachaArt.mix(col, 0xFFFFFF, 0.3)));
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, (float) (200 + 300 * k), 0F, GachaArt.argb((int) (200 * (1 - k)), col));
        g.nextStratum();
        g.fill(0, 0, width, height, GachaArt.argb((int) (255 * Math.pow(1 - k, 2)), 0xFFFFFF));
    }

    private void flashSound() {
        switch (top) {
            case 4 -> {
                GachaArt.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
                GachaArt.sound(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1F, 1F);
                GachaArt.sound(SoundEvents.ENDER_DRAGON_GROWL, 1.6F, 0.3F);
            }
            case 3 -> {
                GachaArt.sound(SoundEvents.BEACON_ACTIVATE, 1F, 1F);
                GachaArt.sound(SoundEvents.TOTEM_USE, 1.5F, 0.35F);
            }
            case 2 -> GachaArt.sound(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.8F);
            case 1 -> GachaArt.sound(SoundEvents.PLAYER_LEVELUP, 1.6F, 0.5F);
            default -> GachaArt.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F, 0.8F);
        }
    }

    // ------------------------------------------------------------ 1회

    private List<String> drawSingle(GuiGraphicsExtractor g, long t, float cx, float cy) {
        ClientGacha.Pulled p = result.pulls().get(0);
        int gi = GachaArt.gradeIndex(p.grade());
        int col = GachaArt.rgb(p.grade());
        long u = t - REVEAL;
        float sec = t / 1000F;
        // 뒤 빛
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 240, 0F, GachaArt.argb(gi >= 2 ? 150 : 80, col));
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 300, sec * 0.25F, GachaArt.argb(gi >= 2 ? 150 : 60, col));
        if (gi >= 4) GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 260, -sec * 0.4F, GachaArt.argb(120, 0xFFD27A));
        if (gi >= 3) {
            double q = (sec * 0.8) % 1.0;
            GachaArt.sprite(g, GachaArt.RING, cx, cy, (float) (120 + 160 * q), 0F, GachaArt.argb((int) (140 * (1 - q)), col));
        }
        // 카드 (튀어나옴) — 화면 높이 240 에서 위 글자 · 카드 · 아래 글자 · 단추가 겹치지 않게
        float s = u < 200 ? (float) (0.5 + 0.6 * ease(u / 200.0)) : u < 350 ? (float) (1.1 - 0.1 * ((u - 200) / 150.0)) : 1F;
        float w = 84 * s;
        float h = 116 * s;
        float ccy = height / 2F - 22;
        GachaArt.sprite(g, GachaArt.CARD, cx, ccy, w, h, 0F, GachaArt.argb(255, col));
        float bob = (float) Math.sin(sec * 2) * 2;
        GachaArt.sprite(g, GachaArt.GLOW, cx, ccy - 8 + bob, 80 * s, 0F, GachaArt.argb(gi >= 3 ? 170 : 110, col));
        GachaArt.item(g, GachaArt.rune(p.rune()), cx, ccy - 8 + bob, 4F * s);
        // 지나가는 빛
        if (u > 250 && u < 700) {
            double q = (u - 250) / 450.0;
            g.enableScissor((int) (cx - w / 2), (int) (ccy - h / 2), (int) (cx + w / 2), (int) (ccy + h / 2));
            GachaArt.sprite(g, GachaArt.SHINE, (float) (cx - w / 2 - 20 + (w + 40) * q), ccy, 28, h * 1.4F, 0.35F, GachaArt.argb(150, 0xFFFFFF));
            g.disableScissor();
        }
        // 반짝임 (영웅 이상)
        if (gi >= 3) {
            for (int i = 0; i < 6; i++) {
                double a = sec * 1.1 + i * Math.PI / 3;
                float x = cx + (float) Math.cos(a) * (w * 0.75F);
                float y = ccy + (float) Math.sin(a) * (h * 0.55F);
                GachaArt.twinkle(g, x, y, 9 + 4 * (float) Math.sin(sec * 4 + i), GachaArt.argb(220, i % 2 == 0 ? 0xFFFFFF : col));
            }
        }
        // 글자
        float ta = (float) Math.min(1, Math.max(0, (u - 150) / 300.0));
        int alpha = (int) (255 * ta);
        if (alpha > 8) {
            float cardTop = ccy - 58;
            float cardBottom = ccy + 58;
            String gl = GachaArt.gradeColor(p.grade()) + "§l" + GachaArt.gradeName(p.grade()) + " 룬";
            GachaArt.textCentered(g, font, gl, cx, cardTop - 24, 1.5F, GachaArt.argb(alpha, 0xFFFFFF));
            GachaArt.textCentered(g, font, "★".repeat(gi + 1), cx, cardTop - 10, 1F, GachaArt.argb(alpha, GachaArt.mix(col, 0xFFFFFF, 0.3)));
            GachaArt.textCentered(g, font, GachaArt.gradeColor(p.grade()) + GachaArt.runeName(p.rune()), cx, cardBottom + 5, 1.5F, GachaArt.argb(alpha, 0xFFFFFF));
            Runes.Rune r = Runes.parseRune(p.rune());
            String fam = r == null ? "" : "§7" + r.famName() + " 계열 룬 · 재사용 " + Js.str(r.cd()) + "초" + (p.at() > 0 && gi >= 3 ? " §8· " + p.at() + "회째" : "");
            GachaArt.textCentered(g, font, fam, cx, cardBottom + 20, 1F, GachaArt.argb(alpha, 0xFFFFFF));
            String desc = r == null ? null : r.def().strOrNull("desc");
            if (desc != null) GachaArt.textCentered(g, font, "§8" + BedrockText.toJava(desc), cx, cardBottom + 31, 0.75F, GachaArt.argb(alpha, 0xFFFFFF));
            if (p.featured()) badge(g, "§0§l픽업!", cx - w / 2 - 6, cardTop + 4, 0xFFFFD24A, alpha);
            if (p.fresh()) badge(g, "§0§lNEW", cx + w / 2 - 22, cardTop - 4, 0xFF7CFF8A, alpha);
            if (p.lost()) GachaArt.textCentered(g, font, "§7픽업을 놓쳤습니다 — 다음 " + GachaArt.gradeName(p.grade()) + "은(는) 픽업 확정", cx, cardBottom + 40, 0.75F,
                    GachaArt.argb(alpha, 0xFFFFFF));
        }
        return null;
    }

    private void badge(GuiGraphicsExtractor g, String s, float x, float y, int bg, int alpha) {
        int w = font.width(s) + 6;
        g.fill((int) x, (int) y, (int) x + w, (int) y + 11, GachaArt.argb(alpha, bg));
        g.text(font, s, (int) x + 3, (int) y + 2, GachaArt.argb(alpha, 0xFFFFFF), false);
    }

    private static double ease(double x) {
        double k = Math.max(0, Math.min(1, x));
        return 1 - Math.pow(1 - k, 3);
    }

    // ------------------------------------------------------------ 10회

    private float[] slot(int i, float cx, float cy) {
        int cols = 5;
        int rows = (result.pulls().size() + cols - 1) / cols;
        float totalW = cols * CW + (cols - 1) * GAP;
        float totalH = rows * CH + (rows - 1) * 12;
        float x = cx - totalW / 2 + (i % cols) * (CW + GAP) + CW / 2F;
        float y = cy - totalH / 2 + (i / cols) * (CH + 12) + CH / 2F + 6;
        return new float[] {x, y};
    }

    private List<String> drawMulti(GuiGraphicsExtractor g, long t, float cx, float cy, int mx, int my) {
        long u = t - REVEAL;
        float sec = t / 1000F;
        int n = result.pulls().size();
        List<String> tip = null;
        // 은은한 빛 — 노란 카드가 있으면 금빛, 없으면 흰빛
        int ambient = GachaArt.goldCard(GachaArt.GRADES.get(top)) ? GachaArt.GOLD : GachaArt.SILVER;
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 420, 0F, GachaArt.argb(70, ambient));
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 460, sec * 0.12F, GachaArt.argb(45, ambient));
        int[] count = new int[5];
        int flipped = 0;
        for (int i = 0; i < n; i++) {
            ClientGacha.Pulled p = result.pulls().get(i);
            int gi = GachaArt.gradeIndex(p.grade());
            // 카드 색 (0.14.1): 영웅 미만 흰 카드 · 영웅 이상 노란 카드 — 빛도 카드 색
            boolean gold = GachaArt.goldCard(p.grade());
            int col = gold ? GachaArt.GOLD : GachaArt.SILVER;
            float[] at = slot(i, cx, cy);
            // 펼쳐지기
            double q = ease((u - i * 20) / 280.0);
            if (u - i * 20 < 0) continue;
            float x = (float) (cx + (at[0] - cx) * q);
            float y = (float) (cy + (at[1] - cy) * q);
            float s = (float) (0.6 + 0.4 * q);
            double f = (t - flipAt[i]) / (double) FLIP;
            // 노란 카드 (영웅 이상): 뒤집기 전에 금빛이 모임
            if (gold && f < 0 && t >= flipAt[i] - BUILD) {
                double b = 1 - (flipAt[i] - t) / (double) BUILD;
                if (!buildSound[i]) {
                    buildSound[i] = true;
                    GachaArt.sound(SoundEvents.BEACON_POWER_SELECT, 1.3F + (gi - 3) * 0.2F, 0.7F);
                }
                GachaArt.sprite(g, GachaArt.GLOW, x, y, (float) (60 + 70 * b), 0F, GachaArt.argb((int) (220 * b), col));
                x += (float) Math.sin(t * 0.08) * (float) (1.5 * b);
            }
            if (f >= 0.5 && !flipSound[i]) {
                flipSound[i] = true;
                GachaArt.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F + gi * 0.15F, 0.9F);
                if (gi >= 4) {
                    GachaArt.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1F, 0.8F);
                    GachaArt.sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1F, 0.8F);
                } else if (gi == 3) {
                    GachaArt.sound(SoundEvents.TOTEM_USE, 1.6F, 0.3F);
                }
            }
            float w = CW * s;
            float h = CH * s;
            if (f < 0.5) {
                float sx = f <= 0 ? 1 : (float) (1 - 2 * f);
                GachaArt.sprite(g, gold ? GachaArt.CARD_BACK_GOLD : GachaArt.CARD_BACK_WHITE, x, y, Math.max(1, w * sx), h, 0F, 0xFFFFFFFF);
                continue;
            }
            flipped++;
            count[gi]++;
            float sx = f >= 1 ? 1 : (float) (2 * f - 1);
            if (gold) {
                float pulse = 0.85F + 0.15F * (float) Math.sin(sec * 3 + i);
                GachaArt.sprite(g, GachaArt.GLOW, x, y, 92 * pulse, 0F, GachaArt.argb(190, col));
                GachaArt.sprite(g, GachaArt.RAYS, x, y, 110, sec * 0.5F + i, GachaArt.argb(gi >= 4 ? 170 : 120, col));
                if (gi >= 4) GachaArt.sprite(g, GachaArt.RAYS, x, y, 96, -sec * 0.7F - i, GachaArt.argb(110, 0xFFFFFF));
            }
            GachaArt.sprite(g, gold ? GachaArt.CARD_GOLD : GachaArt.CARD_WHITE, x, y, Math.max(1, w * sx), h, 0F, 0xFFFFFFFF);
            if (sx > 0.3F) {
                GachaArt.item(g, GachaArt.rune(p.rune()), x, y - 8, 2.25F * sx);
                // 밝은 카드라 글자는 그림자 없이 등급 색을 진하게 (초월은 굵게)
                int ink = GachaArt.argb(255, GachaArt.ink(p.grade()));
                String name = (gi >= 4 ? "§l" : "") + GachaArt.runeName(p.rune());
                float ns = Math.min(0.7F, (CW - 8) / (float) Math.max(1, font.width(name)));
                GachaArt.textCentered(g, font, name, x, y + 18, ns * sx, ink, false);
                GachaArt.textCentered(g, font, "★".repeat(gi + 1), x, y + 26, 0.5F * sx, ink, false);
                // 픽업은 노란 카드에만 붙어 노란 배지 대신 붉은 배지
                if (p.featured()) badge(g, "§f§lUP", x - w / 2 - 2, y - h / 2 - 3, 0xFFE5394F, 255);
                if (p.fresh()) badge(g, "§0§lN", x + w / 2 - 9, y - h / 2 - 3, 0xFF7CFF8A, 255);
                // 초월: 카드 둘레 반짝임
                if (gi >= 4) {
                    for (int k = 0; k < 4; k++) {
                        double a = sec * 1.3 + k * Math.PI / 2 + i;
                        GachaArt.twinkle(g, x + (float) Math.cos(a) * w * 0.56F, y + (float) Math.sin(a) * h * 0.54F, 7 + 3 * (float) Math.sin(sec * 4 + k),
                                GachaArt.argb(230, k % 2 == 0 ? 0xFFFFFF : col));
                    }
                }
            }
            // 뒤집은 순간 번쩍 (노란 카드)
            double fl = (t - flipAt[i] - FLIP / 2.0) / 260.0;
            if (gold && fl >= 0 && fl < 1) {
                GachaArt.sprite(g, GachaArt.RING, x, y, (float) (40 + 140 * fl), 0F, GachaArt.argb((int) (230 * (1 - fl)), col));
                g.fill((int) (x - w / 2), (int) (y - h / 2), (int) (x + w / 2), (int) (y + h / 2), GachaArt.argb((int) (200 * (1 - fl)), 0xFFFFFF));
            }
            if (t >= end && mx >= x - w / 2 && mx < x + w / 2 && my >= y - h / 2 && my < y + h / 2) {
                List<String> rt = GachaScreen.runeTip(p.rune(), p.featured());
                tip = rt == null ? null : new ArrayList<>(rt);
                if (tip != null) {
                    if (p.fresh()) tip.add("§a처음 뽑은 룬 (NEW)");
                    if (p.lost()) tip.add("§7픽업을 놓침 — 다음 " + GachaArt.gradeName(p.grade()) + "은(는) 픽업 확정");
                    if (p.at() > 0 && gi >= 3) tip.add("§8" + p.at() + "회째에 나옴");
                }
            }
        }
        // 위: 제목 · 등급별 수
        if (u > 0) {
            String head = "§f§l" + n + "회 결과";
            GachaArt.textCentered(g, font, head, cx, 12, 1.25F, 0xFFFFFFFF);
            List<String> parts = new ArrayList<>();
            for (int gi = 4; gi >= 0; gi--) if (count[gi] > 0) parts.add(GachaArt.gradeColor(GachaArt.GRADES.get(gi)) + GachaArt.gradeName(GachaArt.GRADES.get(gi)) + " " + count[gi]);
            if (!parts.isEmpty()) GachaArt.textCentered(g, font, String.join("§8 · ", parts) + (flipped < n ? " §8…" : ""), cx, 27, 1F, 0xFFFFFFFF);
        }
        return tip;
    }

    // ------------------------------------------------------------ 단추

    private int[] skipRect() {
        return new int[] {width - 90, 6, 84, 16};
    }

    private int[] buttonRect(boolean again) {
        int y = height - BTN_H - 10;
        int x0 = width / 2 - BTN_W - 4;
        return new int[] {again ? x0 : x0 + BTN_W + 8, y, BTN_W, BTN_H};
    }

    private boolean canAgain() {
        ClientGacha.Banner b = bannerOf();
        return b != null && ClientGacha.tickets(b.ticket()) >= result.pulls().size() && !ClientGacha.waiting();
    }

    private ClientGacha.Banner bannerOf() {
        for (ClientGacha.Banner b : ClientGacha.banners()) if (b.key().equals(result.banner())) return b;
        return null;
    }

    private void drawButtons(GuiGraphicsExtractor g, int mx, int my) {
        for (boolean again : new boolean[] {true, false}) {
            int[] r = buttonRect(again);
            boolean can = !again || canAgain();
            boolean over = in(r, mx, my);
            RpgHud.sprite(g, can && over ? BTN_HOVER : BTN, r[0], r[1], r[2], r[3]);
            String s = again ? (can ? over ? "§0§l" : "§e§l" : "§8§l") + "다시 " + result.pulls().size() + "회" : (over ? "§0§l" : "§f§l") + "확인";
            g.text(font, s, r[0] + r[2] / 2 - font.width(s) / 2, r[1] + 7, 0xFFFFFFFF, !over || !can);
            if (again && over) {
                ClientGacha.Banner b = bannerOf();
                int have = b == null ? 0 : ClientGacha.tickets(b.ticket());
                List<Component> tip = List.of(Component.literal(BedrockText.toJava(can ? "§7뽑기권 " + result.pulls().size() + "장 · 가진 것 " + have + "장"
                        : "§c뽑기권이 모자랍니다 §7(가진 것 " + have + "장)")));
                g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
            }
        }
        if (!multi) return;
        String hint = "§8카드에 마우스 → 풀이";
        GachaArt.text(g, font, hint, width - 8 - font.width(hint) * 0.75F, 8, 0.75F, 0xFFFFFFFF);
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private void back() {
        sendRevealed();
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return true;
        long t = elapsed();
        double mx = event.x();
        double my = event.y();
        if (t < end && in(skipRect(), mx, my)) {
            skip();
            return true;
        }
        if (t < SUMMON) {
            if (frozen >= 0) frozen = SUMMON;
            else shift += SUMMON - t; // 소환 → 섬광
            return true;
        }
        if (t < end) {
            if (multi && t >= REVEAL) skip();
            return true;
        }
        if (in(buttonRect(false), mx, my)) {
            GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
            back();
            return true;
        }
        if (in(buttonRect(true), mx, my)) {
            if (!canAgain()) {
                GachaArt.sound(SoundEvents.NOTE_BLOCK_BASS, 0.7F);
                return true;
            }
            GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
            ClientGacha.pull(result.banner(), result.pulls().size());
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            back();
            return true;
        }
        if (event.isConfirmation() || event.isSelection()) {
            long t = elapsed();
            if (t < end) skip();
            else back();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------ 시험용

    public int[] againCenter() {
        int[] r = buttonRect(true);
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public int[] okCenter() {
        int[] r = buttonRect(false);
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public int[] skipCenter() {
        int[] r = skipRect();
        return new int[] {r[0] + r[2] - 30, r[1] + r[3] / 2};
    }

    /** 카드 i 의 가운데 (10회 — 펼쳐진 뒤) */
    public int[] cardCenter(int i) {
        float[] s = slot(i, width / 2F, height / 2F - 8);
        return new int[] {(int) s[0], (int) s[1]};
    }

    /** 10회 카드 색들 (시험용 — gold 노란 카드 · white 흰 카드) */
    public List<String> cardTones() {
        List<String> out = new ArrayList<>();
        for (ClientGacha.Pulled p : result.pulls()) out.add(GachaArt.goldCard(p.grade()) ? "gold" : "white");
        return out;
    }

    /** 카드 i 의 뒷면 몸통 색을 재는 곳 (시험용 — 펼쳐진 뒤, 위 테 안쪽 · 마법진 위) */
    public int[] cardProbe(int i) {
        float[] s = slot(i, width / 2F, height / 2F - 8);
        return new int[] {(int) s[0], (int) (s[1] - CH / 2F + 9)};
    }

    /** 결과 등급들 (시험용) */
    public List<String> grades() {
        List<String> out = new ArrayList<>();
        for (ClientGacha.Pulled p : result.pulls()) out.add(p.grade());
        return out;
    }

    /** 이번 결과의 가장 높은 등급 */
    public String topGrade() {
        return GachaArt.GRADES.get(top);
    }

    static boolean pity(Gacha.Banner b) {
        return b.pity().on();
    }
}
