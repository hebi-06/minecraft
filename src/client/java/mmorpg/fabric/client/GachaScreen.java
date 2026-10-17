package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import mmorpg.core.Gacha;
import mmorpg.core.Js;
import mmorpg.core.Runes;
import mmorpg.core.Stats;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gacha.GachaSystem;
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
 * 룬 뽑기 창 (0.14.0 — 스탯 창 뽑기 단추 · 뽑기권 우클릭 · /뽑기): 위 탭 (한정 — 열렸을 때 · 일반 · 고급 · 스탯 룬 뽑기권 — 가진 것),
 * 왼쪽 그림 판 (한정: 이름 · 기간 · 픽업 초월 (큰 그림 · 빛살) · 픽업 영웅, 일반 · 고급: 초월 룬이 차례로 · 나오는 등급, 스탯: 그 계열 5종),
 * 오른쪽 확률 · 천장 (초월 · 영웅 막대 — 몇 회 남았는지, 한정은 다음 픽업 확정인지 · 천장을 넣은 확률), 아래 기록 · 설정 (관리자) · 1회 · 10회 뽑기 단추.
 * 뽑으면 서버가 결과를 보내고 연출 창 ({@link GachaShowScreen}) 이 열림. 게임은 멈추지 않음.
 */
public final class GachaScreen extends Screen {
    public static final int W = 384;
    public static final int H = 236;
    static final int TAB_Y = 26;
    static final int TAB_H = 17;
    static final int BODY_Y = 47;
    static final int BODY_H = 146;
    static final int ART_X = 10;
    static final int ART_W = 232;
    static final int INFO_X = 248;
    static final int INFO_W = 126;
    static final int FOOT_Y = 200;
    static final int FOOT_H = 24;
    static final int PULL1_W = 80;
    static final int PULL10_W = 92;
    static final int SMALL_W = 48;

    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier BTN = Mmorpg.id("stats/dialog_btn");
    private static final Identifier BTN_HOVER = Mmorpg.id("stats/dialog_btn_hover");
    private static final Identifier TAB = Mmorpg.id("ui/mode_btn");
    private static final Identifier TAB_ON = Mmorpg.id("ui/mode_btn_on");

    private int left;
    private int top;
    private String tab;
    private boolean history;
    private int historyScroll;
    private final long opened = Util.getMillis();

    public GachaScreen(String open) {
        super(Component.literal("룬 뽑기"));
        this.tab = open == null ? "" : open;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(0, (height - H) / 2);
        fixTab();
        ClientGacha.send("sync", "", "");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 서버가 상태를 다시 보냄 (open = 열 뽑기 — "" 이면 그대로) */
    void refresh(String open) {
        if (open != null && !open.isEmpty()) tab = open;
        fixTab();
    }

    private void fixTab() {
        List<ClientGacha.Banner> bs = ClientGacha.banners();
        for (ClientGacha.Banner b : bs) if (b.key().equals(tab)) return;
        tab = ClientGacha.limitedActive() ? GachaSystem.LIMITED : GachaSystem.NORMAL;
    }

    ClientGacha.Banner banner() {
        for (ClientGacha.Banner b : ClientGacha.banners()) if (b.key().equals(tab)) return b;
        List<ClientGacha.Banner> bs = ClientGacha.banners();
        return bs.isEmpty() ? null : bs.get(0);
    }

    // ------------------------------------------------------------ 자리

    private int tabW(int n) {
        return Math.min(70, (W - 20 - 3 * (n - 1)) / Math.max(1, n));
    }

    private int[] tabRect(int i, int n) {
        int tw = tabW(n);
        return new int[] {left + 10 + i * (tw + 3), top + TAB_Y, tw, TAB_H};
    }

    private int[] pullRect(int count) {
        int x10 = left + W - 10 - PULL10_W;
        if (count > 1) return new int[] {x10, top + FOOT_Y, PULL10_W, FOOT_H};
        return new int[] {x10 - 6 - PULL1_W, top + FOOT_Y, PULL1_W, FOOT_H};
    }

    private int[] historyRect() {
        return new int[] {left + 10, top + FOOT_Y, SMALL_W, FOOT_H};
    }

    private int[] adminRect() {
        return new int[] {left + 10 + SMALL_W + 4, top + FOOT_Y, SMALL_W, FOOT_H};
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ------------------------------------------------------------ 그리기

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        if (minecraft.player == null) return;
        long now = Util.getMillis();
        RpgHud.sprite(g, BG, left, top, W, H);
        ClientGacha.Banner b = banner();
        List<String> tip = null;

        // 제목 · 가진 뽑기권
        g.text(font, "§b§l룬 뽑기", left + 12, top + 9, 0xFFFFFFFF, true);
        g.text(font, "§7· 1회 · 10회 · 천장", left + 58, top + 9, 0xFFFFFFFF, true);
        if (b != null) {
            String cnt = "§f§l" + ClientGacha.tickets(b.ticket());
            int cw = font.width(cnt);
            g.text(font, cnt, left + W - 12 - cw, top + 9, 0xFFFFFFFF, true);
            GachaArt.item(g, new ItemStack(b.ticket()), left + W - 12 - cw - 9, top + 12, 0.8125F);
            if (my >= top + 4 && my < top + 20 && mx >= left + W - 30 - cw && mx < left + W - 10) {
                tip = List.of("§f" + ticketName(b) + " §7" + ClientGacha.tickets(b.ticket()) + "장");
            }
        }
        g.fill(left + 10, top + 21, left + W - 10, top + 22, 0x90C8A050);

        // 탭
        List<ClientGacha.Banner> bs = ClientGacha.banners();
        for (int i = 0; i < bs.size(); i++) {
            ClientGacha.Banner t = bs.get(i);
            int[] r = tabRect(i, bs.size());
            boolean on = t.key().equals(tab);
            boolean over = in(r, mx, my);
            RpgHud.sprite(g, on ? TAB_ON : over ? BTN_HOVER : TAB, r[0], r[1], r[2], r[3]);
            GachaArt.item(g, new ItemStack(t.ticket()), r[0] + 9, r[1] + 8.5F, 0.6875F);
            String label = (on ? "§f§l" : over ? "§0" : t.kind().equals("limited") ? "§c" : "§7") + tabLabel(t);
            GachaArt.textFit(g, font, label, r[0] + 18, r[1] + 5, r[2] - 20, 0xFFFFFFFF);
            if (over) tip = List.of(tabTitle(t), "§7" + ticketName(t) + " " + ClientGacha.tickets(t.ticket()) + "장");
        }
        if (b == null) return;

        // 그림 판 · 확률 · 천장
        int ax = left + ART_X;
        int ay = top + BODY_Y;
        g.enableScissor(ax, ay, ax + ART_W, ay + BODY_H);
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, GachaArt.banner(b.kind()), ax, ay, ART_W, BODY_H);
        List<String> artTip = history ? drawHistory(g, ax, ay, mx, my) : switch (b.kind()) {
            case "limited" -> drawLimited(g, b, ax, ay, now, mx, my);
            case "stat" -> drawStat(g, b, ax, ay, now, mx, my);
            default -> drawStandard(g, b, ax, ay, now, mx, my);
        };
        g.disableScissor();
        g.fill(ax, ay, ax + ART_W, ay + 1, 0x60FFFFFF);
        if (artTip != null) tip = artTip;
        List<String> infoTip = drawInfo(g, b, left + INFO_X, ay, mx, my);
        if (infoTip != null) tip = infoTip;

        // 알림 (실패 까닭 등)
        String note = ClientGacha.note();
        if (!note.isEmpty()) {
            String n = BedrockText.toJava(note);
            int nw = Math.min(ART_W - 8, font.width(n));
            g.fill(ax + 2, ay + BODY_H - 15, ax + 6 + nw + 4, ay + BODY_H - 3, 0xC0000000);
            GachaArt.textFit(g, font, n, ax + 6, ay + BODY_H - 13, ART_W - 12, 0xFFFFFFFF);
        }

        // 아래 단추
        g.fill(left + 10, top + FOOT_Y - 4, left + W - 10, top + FOOT_Y - 3, 0x60C8A050);
        int[] hr = historyRect();
        boolean overH = in(hr, mx, my);
        RpgHud.sprite(g, overH || history ? BTN_HOVER : BTN, hr[0], hr[1], hr[2], hr[3]);
        String hl = history ? "§0§l기록" : "§f기록";
        g.text(font, hl, hr[0] + hr[2] / 2 - font.width(hl) / 2, hr[1] + 8, 0xFFFFFFFF, !history);
        if (overH) tip = List.of("§f최근 뽑기 기록", "§7다시 누르면 닫음");
        if (ClientGacha.admin()) {
            int[] ar = adminRect();
            boolean overA = in(ar, mx, my);
            RpgHud.sprite(g, overA ? BTN_HOVER : BTN, ar[0], ar[1], ar[2], ar[3]);
            String al = overA ? "§0설정" : "§c설정";
            g.text(font, al, ar[0] + ar[2] / 2 - font.width(al) / 2, ar[1] + 8, 0xFFFFFFFF, !overA);
            if (overA) tip = List.of("§c§l한정 뽑기 설정 §7(관리자)", "§7픽업 초월 · 영웅, 기간, 확률 · 천장, 룬술사 값");
        }
        for (int count : new int[] {1, Gacha.multi()}) {
            int[] r = pullRect(count);
            int have = ClientGacha.tickets(b.ticket());
            boolean can = have >= count && !ClientGacha.waiting();
            boolean over = in(r, mx, my);
            RpgHud.sprite(g, can && over ? BTN_HOVER : BTN, r[0], r[1], r[2], r[3]);
            if (can && count > 1) g.fill(r[0] + 2, r[1] + 2, r[0] + r[2] - 2, r[1] + 4, 0x50FFD24A);
            String l1 = (can ? over ? "§0§l" : "§f§l" : "§8§l") + count + "회 뽑기";
            g.text(font, l1, r[0] + r[2] / 2 - font.width(l1) / 2, r[1] + 3, 0xFFFFFFFF, can && !over);
            String l2 = (can ? over ? "§0" : "§e" : "§8") + "×" + count;
            int l2w = font.width(l2) + 11;
            int l2x = r[0] + r[2] / 2 - l2w / 2;
            GachaArt.item(g, new ItemStack(b.ticket()), l2x + 4, r[1] + 17, 0.5625F);
            g.text(font, l2, l2x + 11, r[1] + 13, 0xFFFFFFFF, can && !over);
            if (over) tip = pullTip(b, count, have);
        }

        if (tip != null && !tip.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : tip) lines.add(Component.literal(BedrockText.toJava(l)));
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
        }
    }

    static String tabLabel(ClientGacha.Banner b) {
        return switch (b.kind()) {
            case "limited" -> "한정";
            case "advanced" -> "고급";
            case "stat" -> Stats.info(b.key().substring(GachaSystem.STAT.length())).str("name");
            default -> "일반";
        };
    }

    static String tabTitle(ClientGacha.Banner b) {
        return switch (b.kind()) {
            case "limited" -> "§c§l한정 뽑기 §r§f" + b.name();
            case "advanced" -> "§6§l고급 뽑기 §r§7일반 룬 없음";
            case "stat" -> "§a§l" + b.name() + " §r§7" + GachaArt.familyName(b.banner().families().get(0)) + " 계열만";
            default -> "§b§l일반 뽑기 §r§7룬 " + Runes.FAMILY_ORDER.size() * 5 + "종 모두";
        };
    }

    static String ticketName(ClientGacha.Banner b) {
        return BedrockText.toJava(b.ticket().getName(new ItemStack(b.ticket())).getString()).replaceAll("§.", "").replaceAll(" \\(.*\\)$", "");
    }

    private List<String> pullTip(ClientGacha.Banner b, int count, int have) {
        List<String> t = new ArrayList<>();
        if (have < count) {
            t.add("§c" + ticketName(b) + "이 모자랍니다");
            t.add("§7가진 것 " + have + "장 · 필요 " + count + "장");
            switch (b.kind()) {
                case "limited" -> {
                    Gacha.Spec s = b.spec();
                    if (s != null && s.price() > 0) t.add("§7룬술사에게서 룬 조각 " + s.price() + "개로 (한정 뽑기 기간)");
                    t.add("§7관리자 지급");
                }
                case "advanced" -> t.add("§7룬술사 (룬 조각 40) · 초월 보스");
                case "stat" -> t.add("§7스탯 트리 보상");
                default -> t.add("§7보스 · 퀘스트 · 룬술사 (룬 조각 10)");
            }
            return t;
        }
        if (ClientGacha.waiting()) return List.of("§7결과를 기다리는 중…");
        t.add("§f§l" + count + "회 뽑기 §r§7(" + ticketName(b) + " " + count + "장)");
        Gacha.Banner bn = b.banner();
        if (bn.pity().on()) {
            Gacha.Counter c = ClientGacha.counter(b.pool());
            t.add("§7초월 천장까지 §c" + Math.max(0, bn.pity().hard() - c.n5()) + "회 §7· 영웅 확정까지 §6" + Math.max(0, bn.pity().heroEvery() - c.n4()) + "회");
        }
        if (count > 1) t.add("§810회 안에 영웅 이상 하나는 반드시 (천장이 있는 뽑기)");
        return bn.pity().on() || count == 1 ? t : List.of(t.get(0));
    }

    // ------------------------------------------------------------ 그림 판

    /** 큰 룬 그림 + 빛살 · 빛 (등급 색) */
    private static void hero(GuiGraphicsExtractor g, ItemStack stack, String grade, float cx, float cy, float scale, long now, int themeRgb) {
        int rgb = GachaArt.rgb(grade);
        float t = now / 1000F;
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 120 * scale / 3.2F, t * 0.35F, GachaArt.argb(110, themeRgb));
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 96 * scale / 3.2F, -t * 0.5F, GachaArt.argb(90, rgb));
        float pulse = 0.85F + 0.15F * (float) Math.sin(t * 2.4);
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 92 * scale / 3.2F * pulse, 0F, GachaArt.argb(150, rgb));
        GachaArt.sprite(g, GachaArt.RING, cx, cy, 70 * scale / 3.2F * (0.95F + 0.05F * pulse), 0F, GachaArt.argb(90, GachaArt.mix(rgb, 0xFFFFFF, 0.4)));
        GachaArt.item(g, stack, cx, cy, scale);
        // 반짝임
        for (int i = 0; i < 4; i++) {
            double a = t * 0.9 + i * Math.PI / 2;
            float rr = 34 * scale / 3.2F;
            float s = 7 + 3 * (float) Math.sin(t * 3 + i);
            GachaArt.twinkle(g, cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr * 0.8F, s, GachaArt.argb(200, 0xFFFFFF));
        }
    }

    private List<String> drawLimited(GuiGraphicsExtractor g, ClientGacha.Banner b, int ax, int ay, long now, int mx, int my) {
        List<String> tip = null;
        Gacha.Spec s = b.spec();
        // 띠 · 이름 · 기간
        g.fill(ax + 6, ay + 6, ax + 6 + font.width("§l한정 뽑기") + 8, ay + 17, 0xD0A0102A);
        g.text(font, "§f§l한정 뽑기", ax + 10, ay + 8, 0xFFFFFFFF, true);
        String name = "§f§l" + s.name();
        float ns = Math.min(1.5F, 150F / Math.max(1, font.width(name)));
        GachaArt.text(g, font, name, ax + 8, ay + 21, ns, 0xFFFFFFFF);
        g.text(font, period(s), ax + 8, ay + 36, 0xFFFFFFFF, true);
        // 픽업 초월 (큰 그림)
        List<String> f5 = s.featured5();
        int n5 = f5.size();
        float cx = ax + 166;
        float cy = ay + 66;
        float sc = n5 <= 1 ? 3.2F : n5 == 2 ? 2.4F : 2.0F;
        float gap = n5 <= 1 ? 0 : n5 == 2 ? 30 : 38;
        for (int i = 0; i < n5; i++) {
            float x = cx + (i - (n5 - 1) / 2F) * gap;
            hero(g, GachaArt.rune(f5.get(i), "transcend"), "transcend", x, cy, sc, now + i * 333L, 0xFFC864);
            float half = 8 * sc;
            if (mx >= x - half && mx < x + half && my >= cy - half && my < cy + half) tip = runeTip(Runes.runeId(f5.get(i), "transcend"), true);
        }
        String up = "§e§l픽업 확률 UP";
        int uw = font.width(up);
        g.fill((int) cx - uw / 2 - 4, ay + 6, (int) cx + uw / 2 + 4, ay + 17, 0xC0503000);
        g.text(font, up, (int) cx - uw / 2, ay + 8, 0xFFFFFFFF, true);
        if (n5 == 1) {
            GachaArt.textCentered(g, font, GachaArt.gradeColor("transcend") + GachaArt.runeName(Runes.runeId(f5.get(0), "transcend")), cx, cy + 32, 1F, 0xFFFFFFFF);
            GachaArt.textCentered(g, font, "§7초월 · " + GachaArt.familyName(f5.get(0)) + " 계열", cx, cy + 43, 0.75F, 0xFFFFFFFF);
        } else {
            GachaArt.textCentered(g, font, "§c§l초월 픽업 " + n5 + "종", cx, cy + 34, 1F, 0xFFFFFFFF);
        }
        // 픽업 영웅
        List<String> f4 = s.featured4();
        if (!f4.isEmpty()) {
            g.text(font, "§6§l영웅 픽업", ax + 8, ay + 100, 0xFFFFFFFF, true);
            for (int i = 0; i < f4.size(); i++) {
                float x = ax + 18 + i * 22;
                float y = ay + 120;
                GachaArt.sprite(g, GachaArt.GLOW, x, y, 26, 0F, GachaArt.argb(120, GachaArt.rgb("hero")));
                GachaArt.item(g, GachaArt.rune(f4.get(i), "hero"), x, y, 1.125F);
                if (mx >= x - 9 && mx < x + 9 && my >= y - 9 && my < y + 9) tip = runeTip(Runes.runeId(f4.get(i), "hero"), true);
            }
        }
        GachaArt.text(g, font, "§8천장 · 확정은 한정 뽑기끼리 이어집니다", ax + 8, ay + BODY_H - 9, 0.75F, 0xFFFFFFFF);
        return tip;
    }

    /** 기간 글자 (~ 10.31 23:59 · 13일 남음) */
    static String period(Gacha.Spec s) {
        if (s.end() <= 0) return "§7기간 없음 §8(관리자가 끝낼 때까지)";
        long left = s.end() - ClientGacha.now();
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(s.end()), ZoneId.systemDefault());
        String end = String.format(Locale.ROOT, "%d.%02d %02d:%02d", t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
        String rest;
        if (left <= 0) rest = "§8끝남";
        else if (left >= 86_400_000L) rest = "§e" + (left / 86_400_000L) + "일 남음";
        else if (left >= 3_600_000L) rest = "§e" + (left / 3_600_000L) + "시간 " + (left % 3_600_000L) / 60_000L + "분 남음";
        else rest = "§c" + Math.max(1, left / 60_000L) + "분 남음";
        return "§7~ " + end + " §8· " + rest;
    }

    private List<String> drawStandard(GuiGraphicsExtractor g, ClientGacha.Banner b, int ax, int ay, long now, int mx, int my) {
        List<String> tip = null;
        boolean adv = b.kind().equals("advanced");
        String title = adv ? "§6§l고급 뽑기" : "§b§l일반 뽑기";
        GachaArt.text(g, font, title, ax + 8, ay + 8, 1.5F, 0xFFFFFFFF);
        Gacha.Banner bn = b.banner();
        String sub = adv ? "§7일반 룬 없음 · 희귀 이상만" : "§7룬 " + bn.families().size() * 5 + "종 모두" + (bn.pity().on() ? " · 천장 " + bn.pity().hard() + "회" : "");
        g.text(font, sub, ax + 8, ay + 24, 0xFFFFFFFF, true);
        // 초월 룬이 차례로
        List<String> fams = bn.families();
        long period = 1600;
        int idx = (int) ((now / period) % fams.size());
        float k = (now % period) / (float) period;
        String fam = fams.get(idx);
        float cx = ax + 166;
        float cy = ay + 68;
        float fade = k > 0.85F ? 1 - (k - 0.85F) / 0.15F : k < 0.1F ? k / 0.1F : 1;
        int theme = adv ? 0xFFC65A : 0x6EA8FF;
        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 120, now / 1000F * 0.3F, GachaArt.argb(90, theme));
        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 96, 0F, GachaArt.argb((int) (140 * fade), GachaArt.rgb("transcend")));
        // 바뀔 때: 작아졌다 커짐 (아이템 그림은 흐리게 할 수 없어 크기로)
        GachaArt.item(g, GachaArt.rune(fam, "transcend"), cx, cy, 3.2F * (0.55F + 0.45F * (float) Math.sqrt(fade)));
        GachaArt.textCentered(g, font, GachaArt.gradeColor("transcend") + GachaArt.runeName(Runes.runeId(fam, "transcend")), cx, cy + 32, 1F, 0xFFFFFFFF);
        GachaArt.textCentered(g, font, "§7초월 · " + GachaArt.familyName(fam) + " 계열", cx, cy + 43, 0.75F, 0xFFFFFFFF);
        if (mx >= cx - 26 && mx < cx + 26 && my >= cy - 26 && my < cy + 26) tip = runeTip(Runes.runeId(fam, "transcend"), false);
        // 나오는 등급 (그 계열의 다섯)
        g.text(font, "§7나오는 등급", ax + 8, ay + 100, 0xFFFFFFFF, true);
        int shown = 0;
        for (String grade : GachaArt.GRADES) {
            if (bn.weights().get(grade) <= 0) continue;
            float x = ax + 18 + shown * 22;
            float y = ay + 120;
            GachaArt.sprite(g, GachaArt.GLOW, x, y, 24, 0F, GachaArt.argb(110, GachaArt.rgb(grade)));
            GachaArt.item(g, GachaArt.rune(fam, grade), x, y, 1F);
            if (mx >= x - 9 && mx < x + 9 && my >= y - 9 && my < y + 9) tip = runeTip(Runes.runeId(fam, grade), false);
            shown++;
        }
        return tip;
    }

    private List<String> drawStat(GuiGraphicsExtractor g, ClientGacha.Banner b, int ax, int ay, long now, int mx, int my) {
        List<String> tip = null;
        String stat = b.key().substring(GachaSystem.STAT.length());
        String fam = b.banner().families().get(0);
        GachaArt.text(g, font, BedrockText.toJava(Stats.info(stat).str("color")) + "§l" + b.name(), ax + 8, ay + 8, 1.3F, 0xFFFFFFFF);
        g.text(font, "§7" + GachaArt.familyName(fam) + " 계열 5종만 · 확률은 룬 뽑기권과 같음", ax + 8, ay + 24, 0xFFFFFFFF, true);
        float t = now / 1000F;
        for (int i = 0; i < 5; i++) {
            String grade = GachaArt.GRADES.get(i);
            float x = ax + 30 + i * 43;
            float y = ay + 76 + (float) Math.sin(t * 1.6 + i) * 2;
            GachaArt.sprite(g, GachaArt.RAYS, x, y, 56, t * 0.4F + i, GachaArt.argb(60 + i * 25, GachaArt.rgb(grade)));
            GachaArt.sprite(g, GachaArt.GLOW, x, y, 46, 0F, GachaArt.argb(90 + i * 25, GachaArt.rgb(grade)));
            GachaArt.item(g, GachaArt.rune(fam, grade), x, y, 2F);
            GachaArt.textCentered(g, font, GachaArt.gradeColor(grade) + GachaArt.gradeName(grade), x, y + 20, 0.75F, 0xFFFFFFFF);
            if (mx >= x - 16 && mx < x + 16 && my >= y - 16 && my < y + 16) tip = runeTip(Runes.runeId(fam, grade), false);
        }
        GachaArt.text(g, font, "§8스탯 트리에서 얻는 뽑기권 · 천장 없음", ax + 8, ay + BODY_H - 9, 0.75F, 0xFFFFFFFF);
        return tip;
    }

    private List<String> drawHistory(GuiGraphicsExtractor g, int ax, int ay, int mx, int my) {
        g.fill(ax, ay, ax + ART_W, ay + BODY_H, 0xE0101018);
        g.text(font, "§f§l최근 뽑기", ax + 8, ay + 6, 0xFFFFFFFF, true);
        List<ClientGacha.Entry> h = ClientGacha.history();
        g.text(font, "§8" + h.size() + "개", ax + ART_W - 8 - font.width(h.size() + "개"), ay + 6, 0xFFFFFFFF, true);
        if (h.isEmpty()) {
            g.text(font, "§7아직 뽑은 기록이 없습니다", ax + 8, ay + 24, 0xFFFFFFFF, true);
            return null;
        }
        int rows = (BODY_H - 22) / 11;
        historyScroll = Math.max(0, Math.min(historyScroll, Math.max(0, h.size() - rows)));
        List<String> tip = null;
        for (int i = 0; i < rows; i++) {
            int idx = h.size() - 1 - historyScroll - i;
            if (idx < 0) break;
            ClientGacha.Entry e = h.get(idx);
            int y = ay + 19 + i * 11;
            LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.t()), ZoneId.systemDefault());
            String when = String.format(Locale.ROOT, "§8%d.%02d %02d:%02d", t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
            Runes.Rune r = Runes.parseRune(e.rune());
            String grade = r == null ? "common" : r.grade();
            String b = switch (e.banner()) {
                case GachaSystem.LIMITED -> "§c한정";
                case GachaSystem.ADVANCED -> "§6고급";
                case GachaSystem.NORMAL -> "§b일반";
                default -> "§a스탯";
            };
            String extra = (e.featured() ? " §e픽업" : "") + (e.lost() ? " §7놓침" : "") + (e.at() > 0 && GachaArt.gradeIndex(grade) >= 3 ? " §8(" + e.at() + "회째)" : "");
            GachaArt.text(g, font, when, ax + 6, y + 1, 0.75F, 0xFFFFFFFF);
            g.text(font, b, ax + 52, y, 0xFFFFFFFF, true);
            GachaArt.item(g, GachaArt.rune(e.rune()), ax + 83, y + 4, 0.5625F);
            GachaArt.textFit(g, font, GachaArt.gradeColor(grade) + GachaArt.runeName(e.rune()) + extra, ax + 92, y, ART_W - 98, 0xFFFFFFFF);
            if (my >= y && my < y + 10 && mx >= ax && mx < ax + ART_W) tip = runeTip(e.rune(), false);
        }
        if (h.size() > rows) {
            int bx = ax + ART_W - 4;
            int top0 = ay + 18;
            int hgt = rows * 11;
            int barH = Math.max(8, hgt * rows / h.size());
            int barY = top0 + (hgt - barH) * historyScroll / Math.max(1, h.size() - rows);
            g.fill(bx, top0, bx + 2, top0 + hgt, 0x40FFFFFF);
            g.fill(bx, barY, bx + 2, barY + barH, 0xC0C8A050);
        }
        return tip;
    }

    /** 룬 풀이 (이름 · 등급 · 계열 · 설명) */
    static List<String> runeTip(String id, boolean pickup) {
        Runes.Rune r = Runes.parseRune(id);
        if (r == null) return null;
        List<String> t = new ArrayList<>();
        t.add(r.gradeColor() + r.def().str("name") + (pickup ? " §e[픽업]" : ""));
        t.add("§7" + r.gradeName() + " · " + r.famName() + " 계열 룬 · 재사용 " + Js.str(r.cd()) + "초");
        String desc = r.def().strOrNull("desc");
        if (desc != null) t.add("§8" + desc);
        return t;
    }

    // ------------------------------------------------------------ 확률 · 천장

    private List<String> drawInfo(GuiGraphicsExtractor g, ClientGacha.Banner b, int x, int y, int mx, int my) {
        Gacha.Banner bn = b.banner();
        g.fill(x, y, x + INFO_W, y + BODY_H, 0x70000000);
        g.fill(x, y, x + INFO_W, y + 1, 0x60FFFFFF);
        g.text(font, "§f§l확률", x + 6, y + 5, 0xFFFFFFFF, true);
        for (int i = 0; i < 5; i++) {
            String grade = GachaArt.GRADES.get(4 - i);
            int ry = y + 17 + i * 10;
            double rate = bn.rate(grade);
            g.fill(x + 7, ry + 1, x + 13, ry + 7, GachaArt.argb(255, GachaArt.rgb(grade)));
            g.text(font, GachaArt.gradeColor(grade) + GachaArt.gradeName(grade), x + 17, ry, 0xFFFFFFFF, true);
            String pct = rate > 0 ? "§f" + pct(rate) : "§8없음";
            g.text(font, pct, x + INFO_W - 7 - font.width(pct), ry, 0xFFFFFFFF, true);
        }
        g.fill(x + 6, y + 70, x + INFO_W - 6, y + 71, 0x50C8A050);
        List<String> tip = null;
        if (bn.pity().on()) {
            Gacha.Counter c = ClientGacha.counter(b.pool());
            Gacha.Pity p = bn.pity();
            g.text(font, "§f§l천장", x + 6, y + 75, 0xFFFFFFFF, true);
            bar(g, x, y + 88, "§c초월", c.n5(), p.hard(), GachaArt.rgb("transcend"));
            bar(g, x, y + 100, "§6영웅", c.n4(), p.heroEvery(), GachaArt.rgb("hero"));
            int ly = y + 113;
            if (!bn.featured5().isEmpty()) {
                g.text(font, c.g5() ? "§a다음 초월: 픽업 확정" : "§7다음 초월: 픽업 " + pct(bn.share()), x + 6, ly, 0xFFFFFFFF, true);
                ly += 10;
            }
            if (!bn.featured4().isEmpty()) {
                GachaArt.text(g, font, c.g4() ? "§a다음 영웅: 픽업 확정" : "§7다음 영웅: 픽업 " + pct(bn.share()), x + 6, ly + 1, 0.75F, 0xFFFFFFFF);
                ly += 9;
            }
            GachaArt.text(g, font, "§8천장 포함 초월 약 " + pct(1 / Gacha.expected5(bn)), x + 6, Math.max(ly + 1, y + BODY_H - 10), 0.75F, 0xFFFFFFFF);
            if (mx >= x && mx < x + INFO_W && my >= y + 72 && my < y + BODY_H) {
                tip = new ArrayList<>(List.of("§f§l천장 (원신식)", "§7초월: " + p.softStart() + "회째부터 회차마다 +" + Js.str(p.softStep()) + "% · " + p.hard() + "회째는 확정",
                        "§7영웅: " + p.heroEvery() + "회 안에 반드시 (" + p.heroSoftStart() + "회째부터 확률이 크게 오름)", "§7초월이 나오면 초월 천장은 0 부터 다시",
                        "§7지금: 초월 " + c.n5() + "회째 · 영웅 " + c.n4() + "회째 (마지막으로 나온 뒤)"));
                if (!bn.featured5().isEmpty()) {
                    tip.add("§e픽업: 초월 · 영웅이 나오면 " + pct(bn.share()) + " 확률로 픽업 룬");
                    tip.add("§e놓치면 다음 그 등급은 픽업 확정 (한정 뽑기끼리 이어짐)");
                }
                tip.add("§8천장 포함 평균 " + Js.str(Math.round(Gacha.expected5(bn) * 10) / 10.0) + "회에 초월 하나");
            }
        } else {
            g.text(font, "§7천장 없음", x + 6, y + 75, 0xFFFFFFFF, true);
            GachaArt.text(g, font, "§8고급 · 스탯 룬 뽑기권은", x + 6, y + 88, 0.75F, 0xFFFFFFFF);
            GachaArt.text(g, font, "§8정해진 확률로만 뽑습니다", x + 6, y + 97, 0.75F, 0xFFFFFFFF);
        }
        if (tip == null && mx >= x && mx < x + INFO_W && my >= y && my < y + 70) {
            tip = List.of("§f§l등급 확률 §7(한 번 뽑을 때)", "§7계열은 " + bn.families().size() + "개 중 하나 (같은 확률)" + (b.kind().equals("limited") ? " — 픽업은 따로" : ""));
        }
        return tip;
    }

    private void bar(GuiGraphicsExtractor g, int x, int y, String label, int n, int max, int rgb) {
        g.text(font, label, x + 6, y, 0xFFFFFFFF, true);
        int bx = x + 32;
        int bw = INFO_W - 32 - 40;
        g.fill(bx, y + 2, bx + bw, y + 7, 0x80000000);
        double k = max > 0 ? Math.min(1, n / (double) max) : 0;
        int fw = (int) Math.round(bw * k);
        if (fw > 0) {
            g.fill(bx, y + 2, bx + fw, y + 7, GachaArt.argb(255, rgb));
            g.fill(bx, y + 2, bx + fw, y + 3, GachaArt.argb(160, 0xFFFFFF));
        }
        String s = "§f" + n + "§7/" + max;
        g.text(font, s, x + INFO_W - 6 - font.width(s), y, 0xFFFFFFFF, true);
    }

    static String pct(double v) {
        double p = v * 100;
        double r = p >= 10 ? Math.round(p * 10) / 10.0 : Math.round(p * 100) / 100.0;
        return Js.str(r) + "%";
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        List<ClientGacha.Banner> bs = ClientGacha.banners();
        for (int i = 0; i < bs.size(); i++) {
            if (!in(tabRect(i, bs.size()), mx, my)) continue;
            if (!bs.get(i).key().equals(tab)) {
                tab = bs.get(i).key();
                history = false;
                GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
            }
            return true;
        }
        if (in(historyRect(), mx, my)) {
            history = !history;
            historyScroll = 0;
            GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
            return true;
        }
        if (ClientGacha.admin() && in(adminRect(), mx, my)) {
            GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
            minecraft.gui.setScreen(new GachaAdminScreen(this));
            return true;
        }
        ClientGacha.Banner b = banner();
        if (b != null) {
            for (int count : new int[] {1, Gacha.multi()}) {
                if (!in(pullRect(count), mx, my)) continue;
                if (ClientGacha.tickets(b.ticket()) < count || ClientGacha.waiting()) {
                    GachaArt.sound(SoundEvents.NOTE_BLOCK_BASS, 0.7F);
                    return true;
                }
                GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
                ClientGacha.pull(b.key(), count);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (history) {
            historyScroll = (int) Math.max(0, historyScroll - Math.signum(sy) * 2);
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------ 시험용

    public String tab() {
        return tab;
    }

    public void setTab(String key) {
        tab = key;
        fixTab();
    }

    public List<String> tabKeys() {
        List<String> out = new ArrayList<>();
        for (ClientGacha.Banner b : ClientGacha.banners()) out.add(b.key());
        return out;
    }

    public int[] tabCenter(String key) {
        List<ClientGacha.Banner> bs = ClientGacha.banners();
        for (int i = 0; i < bs.size(); i++) {
            if (!bs.get(i).key().equals(key)) continue;
            int[] r = tabRect(i, bs.size());
            return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
        }
        return null;
    }

    public int[] pullCenter(int count) {
        int[] r = pullRect(count);
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public int[] historyCenter() {
        int[] r = historyRect();
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public int[] adminCenter() {
        int[] r = adminRect();
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public boolean showingHistory() {
        return history;
    }

    long openedAt() {
        return opened;
    }
}
