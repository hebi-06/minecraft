package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.motion.ClientMotions;
import mmorpg.fabric.client.motion.MotionPoses;
import mmorpg.fabric.client.motion.MotionRender;
import mmorpg.fabric.client.motion.Pose;
import mmorpg.fabric.client.rpgui.RpgUi;
import mmorpg.fabric.smith.SmithNet;
import mmorpg.fabric.smith.SmithUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.44.0 대장장이 전용 창 (사용자: "대장장이 ui 수정 좀 하자 좀 난잡함, 강화나 장비를 만들때 망치 두드리는 소리가 나고 대장장이가 망치를 두드리는 애니메이션"
 * → 고른 것: 새 전용 창 · 창 옆 패널 + 세상 NPC). 계산 · 판정은 서버 (SmithUi — 이 창은 받은 목록을 그리고 누른 것을 보냄).
 * <ul>
 *   <li>위: 탭 (강화 · 레벨 올리기 · 제작 · 장신구 · 아티팩트) · 대장장이 이름 · [×]</li>
 *   <li>왼쪽 패널: 대장간 (돌벽 · 화덕 불 · 불씨) 에 대장장이 (그 NPC 의 모습 — 3D) 와 모루 (고른 것이 올라감). 망치질: 대장장이가 세 번 내려침 (세상의 그 NPC 도
 *       같은 동작) · 닿을 때 불꽃 · 번쩍임 · 흔들림 · 모루 소리, 끝나면 결과 연출 (성공 금빛 · 하락 붉은 빛 · 그대로 연기 · 주문서 금빛 고리)</li>
 *   <li>가운데: 목록 (끌거나 휠로 내림 · 묶음 이름) · 아래 재료 줄 (그 탭에서 쓰는 것 — 가진 수)</li>
 *   <li>오른쪽: 고른 것 — 이름 · 큰 변화 (+6 → +7) · 성공 확률 막대 · 효과 · 재료 (가진 수 / 필요 — 모자라면 빨강) · 실패 · 수호 주문서 ·
 *       고를 것 (공허의 갑옷 부위) · 큰 단추</li>
 * </ul>
 * 창이 화면보다 크면 (좁은 휴대폰 · 작은 창) 통째로 줄여 그림 — 마우스 자리도 같은 비율로 되돌림.
 */
public final class SmithScreen extends Screen {
    static final int W = 400;
    static final int H = 228;
    // 위 탭 줄
    static final int TAB_X = 8;
    static final int TAB_Y = 6;
    static final int TAB_H = 16;
    // 왼쪽 대장간 패널 (배경 그림 112 × 194 — tools/smith_art.py)
    static final int SX = 8;
    static final int SY = 26;
    static final int SW = 112;
    static final int SH = 194;
    // 가운데 목록
    static final int LX = 124;
    static final int LY = 26;
    static final int LW = 128;
    static final int LH = 194;
    static final int LIST_TOP = LY + 17;
    static final int RES_H = 18;
    static final int LIST_BOT = LY + LH - RES_H - 3;
    static final int ROW = 22;
    static final int HEAD_ROW = 11;
    // 오른쪽 상세
    static final int DX = 256;
    static final int DY = 26;
    static final int DW = 136;
    static final int DH = 194;
    // 패널 안 자리 (패널 기준). 대장장이는 1칸 = FIG 로, 모루는 1칸 높이 (세상의 모루와 같은 비율) — 망치질 (poses.json smith_hammer) 의 망치 치는 면이
    // 이 카메라로 모루 윗면 (HIT) 에 닿게 맞춤 (tools/smith_art.py 의 panel_layout 과 같은 계산)
    static final int SMITH_X = 32;
    static final int FEET_Y = 168;
    static final float FIG = 44F;
    /** 대장장이 몸 방향 (정면 = 0 — 음수면 오른쪽 모루 쪽으로 돎) */
    static final float FACE = -70F;
    /** 카메라 기울기 (도 — 음수 = 조금 위에서 내려다봄: 모루 윗면 · 바닥이 보이는 배경 그림과 맞춤) */
    static final float CAM = -8F;
    static final int ANVIL_X = 55;
    static final int ANVIL_Y = 125;
    static final int ANVIL_W = 58;
    static final int ANVIL_H = 48;
    /** 모루 윗면의 망치가 닿는 곳 · 올린 것이 놓이는 곳 */
    static final int HIT_X = 79;
    static final int HIT_Y = 127;
    static final int ITEM_X = 79;
    static final int ITEM_Y = 125;
    static final int FORGE_X = 80;
    static final int FORGE_Y = 106;

    static final Identifier BACKDROP = Mmorpg.id("smith/backdrop");
    static final Identifier ANVIL = Mmorpg.id("smith/anvil");
    static final String HAMMER = "rpg:smith_hammer";
    /** 결과 연출 길이 (밀리초) */
    static final long RESULT_MS = 2600;

    private float s = 1F;
    private float ox;
    private float oy;
    /** 고른 칸 열쇠 · 공허의 갑옷 부위 (-1 = 아직) · 수호 주문서 쓰기 */
    private String selKey = "";
    private int variant = -1;
    private boolean useScroll = true;
    /** 목록 첫 줄 (보이는 줄 번호) · 끌기 */
    private int first;
    private boolean pressList;
    private double pressY;
    private int pressFirst;
    private boolean dragged;
    private final long openedAt = Util.getMillis();
    private Avatar dummy;

    // 누르는 곳 (창 기준 {x, y, w, h} — 그릴 때 정함)
    private final List<int[]> tabRects = new ArrayList<>();
    private int[] closeRect = new int[4];
    private int[] pagePrev;
    private int[] pageNext;
    private int[] actionRect;
    private int[] scrollRect;
    private final List<int[]> variantRects = new ArrayList<>();
    private final List<int[]> costRects = new ArrayList<>();
    private final List<int[]> resRects = new ArrayList<>();
    private final List<int[]> rowRects = new ArrayList<>();
    private final List<String> rowKeys = new ArrayList<>();

    // 연출
    private record Spark(float x, float y, float vx, float vy, long born, int life, int rgb, float size) {}

    private final List<Spark> sparks = new ArrayList<>();
    private final List<Spark> embers = new ArrayList<>();
    private long nextEmber;
    private long shakeUntil;
    private final Random rnd = new Random();
    private long nextCrackle;

    public SmithScreen() {
        super(Component.literal("대장장이"));
    }

    @Override
    protected void init() {
        s = Math.min(1F, Math.min((width - 8) / (float) W, (height - 8) / (float) H));
        ox = (width - W * s) / 2F;
        oy = (height - H * s) / 2F;
        SmithNet.ViewPayload v = ClientSmith.view;
        if (v != null) pickDefault(v);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------ 받은 창

    /** 서버가 보낸 새 창 (탭이 바뀌면 목록 처음 — 고른 칸은 같은 열쇠가 있으면 그대로) */
    void onView(SmithNet.ViewPayload before, SmithNet.ViewPayload v) {
        if (before == null || !before.tab().equals(v.tab()) || before.page() != v.page()) {
            first = 0;
            selKey = "";
            variant = -1;
        }
        pickDefault(v);
    }

    private void pickDefault(SmithNet.ViewPayload v) {
        SmithNet.Entry cur = entry(v, selKey);
        if (cur == null) {
            SmithNet.Entry pick = null;
            for (SmithNet.Entry e : v.entries()) {
                if (e.can()) {
                    pick = e;
                    break;
                }
            }
            if (pick == null && !v.entries().isEmpty()) pick = v.entries().get(0);
            selKey = pick == null ? "" : pick.key();
            variant = -1;
            cur = pick;
            if (pick != null) ensureVisible(v, pick.key());
        }
        if (cur != null && !cur.variants().isEmpty() && (variant < 0 || variant >= cur.variants().size())) {
            variant = 0;
            for (int i = 0; i < cur.variants().size(); i++) {
                if (cur.variants().get(i).can()) {
                    variant = i;
                    break;
                }
            }
        }
    }

    private static SmithNet.Entry entry(SmithNet.ViewPayload v, String key) {
        if (v == null || key == null || key.isEmpty()) return null;
        for (SmithNet.Entry e : v.entries()) if (e.key().equals(key)) return e;
        return null;
    }

    /** 망치가 닿음 (ClientSmith — 틱): 불꽃 · 번쩍임 · 흔들림 */
    void onHit(int i, boolean last) {
        long now = Util.getMillis();
        ClientSmith.Work w = ClientSmith.work;
        int rgb = RpgUi.gradeRgb(w == null ? 0 : w.grade());
        int n = last ? 24 : 15;
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(-170 + rnd.nextDouble() * 160);
            float sp = 35F + rnd.nextFloat() * (last ? 95F : 70F);
            int col = rnd.nextFloat() < 0.55F ? 0xFFE6A0 : rnd.nextFloat() < 0.5F ? 0xFF9A3C : rgb;
            sparks.add(new Spark(HIT_X + (rnd.nextFloat() - 0.5F) * 3, HIT_Y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, now,
                    280 + rnd.nextInt(last ? 420 : 300), col, rnd.nextFloat() < 0.3F ? 2F : 1F));
        }
        shakeUntil = now + (last ? 170 : 110);
    }

    // ------------------------------------------------------------ 자리 (시험 — GUI 좌표)

    private int gx(float x) {
        return Math.round(ox + x * s);
    }

    private int gy(float y) {
        return Math.round(oy + y * s);
    }

    private static int[] center(int[] r) {
        return r == null ? null : new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    private int[] gui(int[] c) {
        return c == null ? null : new int[] {gx(c[0]), gy(c[1])};
    }

    /** 탭 i 가운데 (시험) */
    public int[] tabCenter(int i) {
        return i >= 0 && i < tabRects.size() ? gui(center(tabRects.get(i))) : null;
    }

    /** 목록에 보이는 칸 key 의 가운데 (시험 — 안 보이면 null) */
    public int[] rowCenter(String key) {
        int i = rowKeys.indexOf(key);
        return i < 0 ? null : gui(center(rowRects.get(i)));
    }

    /** 큰 단추 가운데 (시험) */
    public int[] actionCenter() {
        return gui(center(actionRect));
    }

    /** 공허의 갑옷 부위 단추 i 가운데 (시험) */
    public int[] variantCenter(int i) {
        return i >= 0 && i < variantRects.size() ? gui(center(variantRects.get(i))) : null;
    }

    /** 수호 주문서 쓰기 가운데 (시험 — 없으면 null) */
    public int[] scrollCenter() {
        return gui(center(scrollRect));
    }

    /** 제작 쪽 ◀ · ▶ (시험) */
    public int[] pageCenter(boolean next) {
        return gui(center(next ? pageNext : pagePrev));
    }

    /** 대장장이 패널 가운데 (시험 — 사진) */
    public int[] sceneBox() {
        return new int[] {gx(SX), gy(SY), gx(SX + SW), gy(SY + SH)};
    }

    /** 고른 칸 열쇠 (시험) */
    public String selected() {
        return selKey;
    }

    /** 시험: 칸을 고름 (보이지 않아도) */
    public void select(String key) {
        selKey = key == null ? "" : key;
        variant = -1;
        SmithNet.ViewPayload v = ClientSmith.view;
        if (v != null) {
            pickDefault(v);
            ensureVisible(v, selKey);
        }
    }

    /** 시험: 부위를 고름 */
    public void chooseVariant(int i) {
        variant = i;
    }

    // ------------------------------------------------------------ 그리기

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        SmithNet.ViewPayload v = ClientSmith.view;
        if (v == null || minecraft == null || minecraft.player == null) return;
        long now = Util.getMillis();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int lmx = Math.round((mx - ox) / s);
        int lmy = Math.round((my - oy) / s);
        SmithNet.Entry sel = entry(v, selKey);
        List<Component> tip = null;

        // ---- 창 · 위 줄 · 목록 · 상세 · 패널 배경 (줄인 좌표)
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(s, s);
        RpgUi.panel(g, 0, 0, W, H);
        drawTabs(g, v, lmx, lmy);
        tip = merge(tip, drawList(g, v, lmx, lmy, now));
        tip = merge(tip, drawDetail(g, v, sel, lmx, lmy, now));
        int[] shake = shake(now);
        drawSceneBack(g, v, sel, shake, now);
        g.pose().popMatrix();

        // ---- 대장장이 (3D — 그림 상자는 GUI 좌표: 같은 층의 다른 그림 위에 그려짐)
        drawSmith(g, partial, shake);

        // ---- 다음 층: 불꽃 · 결과 연출 · 이름표 (대장장이 위)
        g.nextStratum();
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(s, s);
        drawSceneFront(g, v, shake, now);
        g.pose().popMatrix();

        if (tip != null && !tip.isEmpty()) g.setTooltipForNextFrame(font, tip, Optional.empty(), mx, my);
        ambience(now);
    }

    private static List<Component> merge(List<Component> a, List<Component> b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private static Component lit(String s) {
        return Component.literal(BedrockText.toJava(s));
    }

    private static String j(String s) {
        return BedrockText.toJava(s == null ? "" : s);
    }

    /** 글자를 maxW 안에 (넓으면 가로로 줄임 — 자리가 너무 좁으면 그리지 않음) */
    private void fit(GuiGraphicsExtractor g, String s, int x, int y, int maxW, int argb) {
        if (maxW < 6 || s == null || s.isEmpty()) return;
        GachaArt.textFit(g, font, s, x, y, maxW, argb);
    }

    /** 글자를 너비 w 안의 줄로 (" · " 에서 끊음 — 많아야 max 줄, 남는 것은 마지막 줄에 붙음: fit 이 줄임). 끊은 줄 앞 색은 다음 줄로 이어짐 */
    private List<String> wrap(String s, int w, int max) {
        List<String> out = new ArrayList<>();
        if (max <= 1 || font.width(s) <= w) {
            out.add(s);
            return out;
        }
        String cur = "";
        for (String part : s.split(" · ")) {
            String next = cur.isEmpty() ? part : cur + " · " + part;
            if (!cur.isEmpty() && font.width(next) > w && out.size() < max - 1) {
                out.add(cur);
                int c = cur.lastIndexOf('§');
                cur = (c >= 0 && c + 1 < cur.length() ? cur.substring(c, c + 2) : "") + part;
            } else cur = next;
        }
        out.add(cur);
        return out;
    }

    private static boolean in(int[] r, int x, int y) {
        return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    private static String tabLabel(String tab) {
        return switch (tab) {
            case SmithUi.ENHANCE -> "강화";
            case SmithUi.RAISE -> "레벨 올리기";
            case SmithUi.CRAFT -> "제작";
            case SmithUi.GEAR -> "장신구 · 아티팩트";
            default -> tab;
        };
    }

    private void drawTabs(GuiGraphicsExtractor g, SmithNet.ViewPayload v, int mx, int my) {
        tabRects.clear();
        int x = TAB_X;
        for (String t : v.tabs()) {
            String label = tabLabel(t);
            int w = font.width(label) + 16;
            int[] r = {x, TAB_Y, w, TAB_H};
            tabRects.add(r);
            boolean on = t.equals(v.tab());
            boolean hover = !v.busy() && in(r, mx, my);
            if (on) RpgUi.smallPanel(g, r[0], r[1], r[2], r[3], 0xFF3A2E18, 0xFF221A0C);
            else RpgUi.smallPanel(g, r[0], r[1], r[2], r[3], hover ? 0xFF2C3050 : 0xFF1C1F34, hover ? 0xFF1A1C30 : 0xFF111322);
            int col = on ? RpgUi.GOLD_TEXT : v.busy() ? 0xFF6A6A78 : hover ? 0xFFFFFFFF : 0xFFB8B8C8;
            g.text(font, label, x + 8, TAB_Y + 4, col, true);
            x += w + 3;
        }
        // 대장장이 이름 · 닫기
        closeRect = new int[] {W - 20, TAB_Y + 2, 12, 12};
        boolean ch = in(closeRect, mx, my);
        g.fill(closeRect[0], closeRect[1], closeRect[0] + 12, closeRect[1] + 12, ch ? 0xC0602020 : 0x80000000);
        g.outline(closeRect[0], closeRect[1], 12, 12, 0xA0C8A050);
        g.text(font, "×", closeRect[0] + 3, closeRect[1] + 2, ch ? 0xFFFFFFFF : 0xFFD0C090, false);
        String who = v.npcName().isEmpty() ? "§6대장장이" : "§6대장장이 §f" + v.npcName();
        String jw = j(who);
        int nx = Math.max(x + 6, W - 26 - font.width(jw));
        fit(g, jw, nx, TAB_Y + 4, W - 26 - nx, 0xFFFFFFFF);
    }

    // ---- 목록

    /** 보이는 줄: 묶음 이름 줄 (entry = null) · 칸 줄 */
    private record Line(SmithNet.Entry e, String group) {
        int h() {
            return e == null ? HEAD_ROW : ROW;
        }
    }

    private static List<Line> lines(SmithNet.ViewPayload v) {
        List<Line> out = new ArrayList<>();
        for (SmithNet.Entry e : v.entries()) {
            if (!e.group().isEmpty()) out.add(new Line(null, e.group()));
            out.add(new Line(e, ""));
        }
        return out;
    }

    private static int maxFirst(List<Line> ls) {
        int h = 0;
        int area = LIST_BOT - LIST_TOP;
        for (int i = ls.size() - 1; i >= 0; i--) {
            h += ls.get(i).h();
            if (h > area) return Math.min(ls.size() - 1, i + 1);
        }
        return 0;
    }

    private void ensureVisible(SmithNet.ViewPayload v, String key) {
        List<Line> ls = lines(v);
        int idx = -1;
        for (int i = 0; i < ls.size(); i++) if (ls.get(i).e() != null && ls.get(i).e().key().equals(key)) idx = i;
        if (idx < 0) return;
        if (idx < first) first = Math.max(0, idx > 0 && ls.get(idx - 1).e() == null ? idx - 1 : idx);
        int h = 0;
        int area = LIST_BOT - LIST_TOP;
        for (int i = first; i <= idx; i++) h += ls.get(i).h();
        while (h > area && first < idx) {
            h -= ls.get(first).h();
            first++;
        }
        first = Math.max(0, Math.min(first, maxFirst(ls)));
    }

    private List<Component> drawList(GuiGraphicsExtractor g, SmithNet.ViewPayload v, int mx, int my, long now) {
        List<Component> tip = null;
        g.fill(LX, LY, LX + LW, LY + LH, 0x60000000);
        g.outline(LX, LY, LW, LH, 0x90C8A050);
        // 머리 줄: 제목 · 개수 (제작은 ◀ 쪽 ▶)
        pagePrev = null;
        pageNext = null;
        if (v.tab().equals(SmithUi.CRAFT) && !v.pages().isEmpty()) {
            int page = Math.max(0, Math.min(v.pages().size() - 1, v.page()));
            pagePrev = new int[] {LX + 3, LY + 2, 14, 12};
            pageNext = new int[] {LX + LW - 17, LY + 2, 14, 12};
            for (int[] r : List.of(pagePrev, pageNext)) {
                boolean h = !v.busy() && in(r, mx, my);
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], h ? 0xC03A2E18 : 0x80000000);
                g.outline(r[0], r[1], r[2], r[3], 0xA0C8A050);
            }
            g.text(font, "◀", pagePrev[0] + 3, pagePrev[1] + 2, v.busy() ? 0xFF6A6A78 : RpgUi.GOLD_TEXT, false);
            g.text(font, "▶", pageNext[0] + 4, pageNext[1] + 2, v.busy() ? 0xFF6A6A78 : RpgUi.GOLD_TEXT, false);
            String label = j("§6" + v.pages().get(page) + " §8" + (page + 1) + "/" + v.pages().size());
            int lw = Math.min(LW - 40, font.width(label));
            fit(g, label, LX + (LW - lw) / 2, LY + 4, LW - 40, 0xFFFFFFFF);
        } else {
            String title = switch (v.tab()) {
                case SmithUi.RAISE -> "올릴 무기";
                case SmithUi.GEAR -> "장신구 · 아티팩트";
                default -> "가방의 무기";
            };
            g.text(font, title, LX + 5, LY + 4, RpgUi.GOLD_TEXT, true);
            String n = String.valueOf(v.entries().size());
            g.text(font, n, LX + LW - 5 - font.width(n), LY + 4, 0xFF9A9AAA, true);
        }
        RpgUi.sep(g, LX + 3, LX + LW - 3, LY + 15);

        // 줄
        rowRects.clear();
        rowKeys.clear();
        List<Line> ls = lines(v);
        first = Math.max(0, Math.min(first, maxFirst(ls)));
        int y = LIST_TOP;
        int last = first;
        for (int i = first; i < ls.size(); i++) {
            Line l = ls.get(i);
            if (y + l.h() > LIST_BOT) break;
            last = i;
            if (l.e() == null) {
                g.text(font, j("§6" + l.group()), LX + 5, y + 2, 0xFFFFFFFF, true);
                g.fill(LX + 6 + font.width(j(l.group())) + 4, y + 6, LX + LW - 6, y + 7, 0x50C8A050);
                y += l.h();
                continue;
            }
            SmithNet.Entry e = l.e();
            int[] r = {LX + 2, y, LW - 4, ROW - 1};
            rowRects.add(r);
            rowKeys.add(e.key());
            boolean on = e.key().equals(selKey);
            boolean hover = in(r, mx, my) && !pressList;
            if (on) {
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x70806030);
                g.outline(r[0], r[1], r[2], r[3], 0xFFD6AA50);
            } else if (hover) g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x30FFFFFF);
            else g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x28000000);
            RpgUi.gradeUnder(g, e.icon(), r[0] + 2, y + 2, 16, now);
            g.item(e.icon(), r[0] + 2, y + 2);
            // 이름 · 아래 줄 (오른쪽 배지 자리만큼 줄임)
            String badge = j(badge(e));
            int bw = badge.isEmpty() ? 0 : font.width(badge) + 4;
            fit(g, j(e.name()), r[0] + 21, y + 2, r[2] - 23 - bw, e.state() == 1 ? 0xFFA0A0A8 : 0xFFFFFFFF);
            fit(g, j("§7" + e.sub()), r[0] + 21, y + 11, r[2] - 23, 0xFFFFFFFF);
            if (!badge.isEmpty()) g.text(font, badge, r[0] + r[2] - 2 - font.width(badge), y + 2, 0xFFFFFFFF, true);
            if (e.state() == 1) g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x30000000);
            if (hover) {
                tip = new ArrayList<>();
                tip.add(lit(e.name()));
                tip.add(lit("§7" + e.sub()));
                if (!e.note().isEmpty()) tip.add(lit(e.note()));
            }
            y += l.h();
        }
        // 비었을 때
        if (v.entries().isEmpty()) {
            int ty = LIST_TOP + 30;
            for (String part : v.empty().split("\n")) {
                String t = j(part);
                fit(g, t, LX + Math.max(4, (LW - font.width(t)) / 2), ty, LW - 8, 0xFFFFFFFF);
                ty += 11;
            }
        }
        // 내림 막대
        int mf = maxFirst(ls);
        if (mf > 0) {
            int bx = LX + LW - 3;
            int h = LIST_BOT - LIST_TOP;
            g.fill(bx, LIST_TOP, bx + 2, LIST_BOT, 0x30FFFFFF);
            int vis = Math.max(1, last - first + 1);
            int bh = Math.max(8, h * vis / Math.max(vis, ls.size()));
            int by = LIST_TOP + (h - bh) * first / mf;
            g.fill(bx, by, bx + 2, by + bh, 0xC0D6AA50);
        }
        // 재료 줄
        resRects.clear();
        int ry = LY + LH - RES_H - 1;
        RpgUi.sep(g, LX + 3, LX + LW - 3, ry - 1);
        int n = Math.max(1, v.res().size());
        int cw = (LW - 6) / n;
        for (int i = 0; i < v.res().size(); i++) {
            SmithNet.Res r = v.res().get(i);
            int x = LX + 3 + i * cw;
            int[] rr = {x, ry + 1, cw, RES_H - 1};
            resRects.add(rr);
            g.pose().pushMatrix();
            g.pose().translate(x + 1, ry + 3);
            g.pose().scale(0.75F, 0.75F);
            g.item(r.icon(), 0, 0);
            g.pose().popMatrix();
            String c = compact(r.count());
            fit(g, c, x + 14, ry + 5, cw - 15, r.count() > 0 ? 0xFFE8E0C8 : 0xFF8A8A96);
            if (in(rr, mx, my)) tip = List.of(lit("§f" + r.name() + " §7" + mmorpg.core.Economy.fmt(r.count())));
        }
        return tip;
    }

    private static String badge(SmithNet.Entry e) {
        if (e.state() == 2) return "§6" + e.badge() + " §8최대";
        if (e.badge().isEmpty()) return "";
        if (e.badge().equals("가능")) return "§a가능";
        return (e.state() == 1 ? "§7" : "§e") + e.badge() + (e.state() == 3 ? "§6!" : "");
    }

    /** 큰 수를 짧게 (12,345 → 1.2만) */
    static String compact(long n) {
        if (n < 10_000) return String.valueOf(n);
        if (n < 100_000) return String.format(java.util.Locale.ROOT, "%.1f만", n / 10_000.0);
        if (n < 100_000_000) return (n / 10_000) + "만";
        return String.format(java.util.Locale.ROOT, "%.1f억", n / 100_000_000.0);
    }

    // ---- 상세

    private List<Component> drawDetail(GuiGraphicsExtractor g, SmithNet.ViewPayload v, SmithNet.Entry e, int mx, int my, long now) {
        List<Component> tip = null;
        RpgUi.smallPanel(g, DX, DY, DW, DH, 0xE01A1C2E, 0xE00E0F1A);
        actionRect = null;
        scrollRect = null;
        variantRects.clear();
        costRects.clear();
        int x = DX + 6;
        int w = DW - 12;
        if (e == null) {
            String t = j(v.entries().isEmpty() ? "§8고를 것이 없습니다" : "§7왼쪽 목록에서 고르세요");
            g.text(font, t, DX + (DW - font.width(t)) / 2, DY + DH / 2 - 4, 0xFFFFFFFF, true);
            return null;
        }
        // 머리: 그림 (크게) · 이름 · 아래 줄
        RpgUi.slot(g, DX + 5, DY + 5, 26);
        RpgUi.gradeUnder(g, e.icon(), DX + 6, DY + 6, 24, now);
        GachaArt.item(g, e.icon(), DX + 18, DY + 18, 1.4F);
        fit(g, j(e.name()), DX + 35, DY + 7, DW - 41, 0xFFFFFFFF);
        fit(g, j("§7" + e.sub()), DX + 35, DY + 18, DW - 41, 0xFFFFFFFF);
        RpgUi.sep(g, DX + 4, DX + DW - 4, DY + 34);
        int y = DY + 38;
        int bottom = DY + DH - 30; // 단추 위
        // 큰 변화
        if (!e.from().isEmpty()) {
            String ch = e.to().isEmpty() ? "§6" + e.from() : "§f" + e.from() + " §7→ §a§l" + e.to();
            GachaArt.textCentered(g, font, j(ch), DX + DW / 2F, y, 1.3F, 0xFFFFFFFF);
            y += 15;
        }
        // 성공 확률
        if (e.rate() >= 0) {
            String pct = Math.round(e.rate() * 100) + "%";
            g.text(font, j("§7성공 §f" + pct), x, y, 0xFFFFFFFF, true);
            int bx = x + 52;
            int bw = w - 52;
            int rgb = e.rate() >= 0.6 ? 0x6CD66C : e.rate() >= 0.35 ? 0xE8C050 : 0xE86050;
            RpgUi.bar(g, bx, y + 2, bw, 5, (float) e.rate(), rgb);
            y += 12;
        }
        // 효과 줄
        for (String l : e.lines()) {
            if (y + 10 > bottom - 22) break;
            fit(g, j(l), x, y, w, 0xFFFFFFFF);
            y += 10;
        }
        // 고를 것 (공허의 갑옷 부위 · 한 벌)
        List<SmithNet.Cost> costs = e.costs();
        boolean can = e.can();
        String note = e.note();
        if (!e.variants().isEmpty()) {
            y += 2;
            int n = e.variants().size();
            int bw = (w - (n - 1) * 2) / n;
            for (int i = 0; i < n; i++) {
                SmithNet.Variant vr = e.variants().get(i);
                int[] r = {x + i * (bw + 2), y, bw, 13};
                variantRects.add(r);
                boolean on = i == variant;
                boolean h = in(r, mx, my);
                g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? 0xC0806030 : h ? 0x60FFFFFF : 0x60000000);
                g.outline(r[0], r[1], r[2], r[3], on ? 0xFFD6AA50 : 0x80C8A050);
                String lb = j((vr.can() ? (on ? "§f" : "§e") : "§7") + vr.label());
                fit(g, lb, r[0] + Math.max(1, (bw - font.width(lb)) / 2), y + 3, bw - 2, 0xFFFFFFFF);
                if (h) tip = List.of(lit("§f" + vr.label() + (vr.can() ? " §a만들 수 있음" : " §c재료 부족")));
            }
            y += 16;
            if (variant >= 0 && variant < n) {
                SmithNet.Variant vr = e.variants().get(variant);
                costs = vr.costs();
                can = vr.can();
                note = vr.note();
            } else {
                costs = List.of();
                can = false;
                note = "§e만들 것을 고르세요";
            }
        }
        // 재료
        if (!costs.isEmpty() && y + 10 < bottom) {
            g.text(font, "재료", x, y, RpgUi.GOLD_TEXT, true);
            y += 10;
            boolean two = costs.size() > 3;
            int cw = two ? (w - 2) / 2 : w;
            int rows = two ? (costs.size() + 1) / 2 : costs.size();
            int rowH = 11;
            for (int i = 0; i < costs.size(); i++) {
                int col = two ? i % 2 : 0;
                int row = two ? i / 2 : i;
                int cy = y + row * rowH;
                if (cy + rowH > bottom) break;
                SmithNet.Cost c = costs.get(i);
                int cx = x + col * (cw + 2);
                int[] r = {cx, cy, cw, rowH};
                costRects.add(r);
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().scale(0.625F, 0.625F);
                g.item(c.icon(), 0, 0);
                g.pose().popMatrix();
                String num = (c.enough() ? "§a" : "§c") + compact(c.have()) + "§7/" + compact(c.need());
                String jn = j(num);
                int nw = font.width(jn);
                if (!two) fit(g, j("§7" + c.name()), cx + 12, cy + 1, cw - 14 - nw, 0xFFFFFFFF);
                fit(g, jn, two ? cx + 12 : cx + cw - nw, cy + 1, two ? cw - 13 : nw, 0xFFFFFFFF);
                if (in(r, mx, my)) tip = List.of(lit("§f" + c.name()), lit("§7가진 수 " + (c.enough() ? "§a" : "§c") + mmorpg.core.Economy.fmt(c.have()) + " §7/ 필요 §f"
                        + mmorpg.core.Economy.fmt(c.need())));
            }
            y += Math.min(rows, Math.max(0, (bottom - y) / rowH)) * rowH + 2;
        }
        // 실패
        if (!e.fail().isEmpty() && y + 10 <= bottom) {
            fit(g, j(e.fail()), x, y, w, 0xFFFFFFFF);
            y += 10;
        }
        // 수호 주문서 (하락 위험이 있을 때)
        if (e.scroll() >= 0 && y + 11 <= bottom + 4) {
            scrollRect = new int[] {x, y, w, 11};
            boolean has = e.scroll() > 0;
            boolean on = has && useScroll;
            g.fill(x, y + 1, x + 9, y + 10, 0xFF0C0C14);
            g.outline(x, y + 1, 9, 9, has ? 0xFFD6AA50 : 0xFF55556A);
            if (on) g.text(font, "✔", x + 1, y + 1, 0xFF7CE07C, false);
            String t = j((has ? "§f" : "§8") + "수호 주문서 쓰기 §7(" + e.scroll() + ")");
            fit(g, t, x + 13, y + 2, w - 13, 0xFFFFFFFF);
            if (in(scrollRect, mx, my)) tip = List.of(lit("§6수호 주문서"), lit("§7실패해도 단계가 떨어지지 않음"), lit("§7(실패했을 때만 사라짐)"));
            y += 12;
        }
        // 알림 (서버 · 모자람)
        String msg = !v.message().isEmpty() ? v.message() : note;
        if (msg != null && !msg.isEmpty()) {
            // 길면 두 줄 (" · " 에서 끊음 — 모자란 재료가 많을 때 글자가 납작해지지 않게)
            List<String> ls = wrap(j(msg), w, 2);
            int ny = Math.max(y + 1, DY + DH - 40 - 10 * (ls.size() - 1));
            for (String l : ls) {
                fit(g, l, x, ny, w, 0xFFFFFFFF);
                ny += 10;
            }
        }
        // 큰 단추
        actionRect = new int[] {DX + 6, DY + DH - 28, DW - 12, 22};
        boolean busy = v.busy() || ClientSmith.work != null && ClientSmith.progress(0) < 1;
        boolean enabled = can && !busy && e.state() != 2;
        boolean hover = enabled && in(actionRect, mx, my);
        RpgUi.button(g, actionRect[0], actionRect[1], actionRect[2], actionRect[3], hover, false);
        if (busy) {
            double t = Math.max(0, ClientSmith.progress(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
            int fw = (int) Math.round((actionRect[2] - 6) * t);
            g.fill(actionRect[0] + 3, actionRect[1] + actionRect[3] - 5, actionRect[0] + 3 + fw, actionRect[1] + actionRect[3] - 3, 0xFFFFC860);
        }
        String label = busy ? "망치질 중…" : e.action();
        int lc = enabled ? 0xFFFFE8B0 : busy ? 0xFFE8C070 : 0xFF7A7A88;
        GachaArt.textCentered(g, font, label, actionRect[0] + actionRect[2] / 2F, actionRect[1] + 7, 1F, lc);
        if (!enabled && !busy) g.fill(actionRect[0] + 2, actionRect[1] + 2, actionRect[0] + actionRect[2] - 2, actionRect[1] + actionRect[3] - 2, 0x50000000);
        return tip;
    }

    // ---- 대장간 패널

    /** 흔들림 (망치가 닿은 바로 뒤 — 창 기준 {dx, dy}) */
    private int[] shake(long now) {
        if (now >= shakeUntil) return new int[] {0, 0};
        float k = (shakeUntil - now) / 170F;
        return new int[] {Math.round((rnd.nextFloat() - 0.5F) * 3F * k), Math.round((rnd.nextFloat() - 0.5F) * 2.4F * k)};
    }

    /** 패널 뒤 (대장장이보다 아래): 배경 · 화덕 빛 · 불씨 · 모루 · 모루 위의 것 · 달군 빛 */
    private void drawSceneBack(GuiGraphicsExtractor g, SmithNet.ViewPayload v, SmithNet.Entry sel, int[] sh, long now) {
        int x0 = SX + sh[0];
        int y0 = SY + sh[1];
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKDROP, x0, y0, SW, SH);
        // 화덕 불 (깜빡임)
        double fl = 0.5 + 0.25 * Math.sin(now / 130.0) + 0.25 * Math.sin(now / 47.0 + 1.3);
        GachaArt.sprite(g, GachaArt.GLOW, x0 + FORGE_X, y0 + FORGE_Y, 54F + (float) fl * 6F, 0F, GachaArt.argb((int) (90 + 70 * fl), 0xFF9A3C));
        GachaArt.sprite(g, GachaArt.GLOW, x0 + FORGE_X, y0 + FORGE_Y + 6, 26F, 0F, GachaArt.argb((int) (120 + 60 * fl), 0xFFE08A));
        // 불씨 (화덕 입에서 위로)
        if (nextEmber < now - 1000) nextEmber = now;
        while (nextEmber <= now) {
            embers.add(new Spark(FORGE_X - 10 + rnd.nextFloat() * 20, FORGE_Y - 8, (rnd.nextFloat() - 0.5F) * 10F, -12F - rnd.nextFloat() * 12F, nextEmber,
                    1100 + rnd.nextInt(900), rnd.nextFloat() < 0.5F ? 0xFFC860 : 0xFF8A30, 1F));
            nextEmber += 120 + rnd.nextInt(160);
        }
        embers.removeIf(p -> now - p.born() > p.life());
        for (Spark p : embers) {
            float t = (now - p.born()) / 1000F;
            float px = p.x() + p.vx() * t + (float) Math.sin(t * 5 + p.born() % 7) * 2F;
            float py = p.y() + p.vy() * t;
            float a = 1F - (now - p.born()) / (float) p.life();
            if (py < 12) continue;
            int alpha = (int) (255 * a * (0.6 + 0.4 * Math.sin(now / 60.0 + p.born())));
            g.fill(x0 + Math.round(px), y0 + Math.round(py), x0 + Math.round(px) + 1, y0 + Math.round(py) + 1, GachaArt.argb(alpha, p.rgb()));
        }
        // 모루
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ANVIL, x0 + ANVIL_X, y0 + ANVIL_Y, ANVIL_W, ANVIL_H);
        // 모루 위의 것: 망치질 중 = 올린 것 (달굼 빛), 결과 = 결과 아이템, 아니면 고른 것
        ClientSmith.Work w = ClientSmith.work;
        SmithNet.Result r = ClientSmith.result;
        boolean showResult = r != null && now - ClientSmith.resultAt < RESULT_MS;
        ItemStack item = w != null && ClientSmith.progress(0) < 1 ? w.item() : showResult && !r.item().isEmpty() ? r.item() : sel != null ? sel.icon() : ItemStack.EMPTY;
        double prog = w != null ? Math.max(0, ClientSmith.progress(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false))) : 0;
        if (w != null && prog < 1) {
            GachaArt.sprite(g, GachaArt.GLOW, x0 + ITEM_X, y0 + ITEM_Y + 2, 30F, 0F, GachaArt.argb((int) (60 + 150 * prog), 0xFF7A28));
        }
        if (!item.isEmpty()) {
            float pop = 1F;
            float dx = 0;
            if (showResult) {
                float p = (now - ClientSmith.resultAt) / 1000F;
                if (r.kind().equals("success") || r.kind().equals("done")) pop = 1F + 0.5F * (float) Math.max(0, Math.sin(Math.min(1, p / 0.35F) * Math.PI)) * Math.max(0, 1 - p);
                if (r.kind().equals("down")) dx = (float) Math.sin(p * 60) * 2.5F * Math.max(0, 1 - p * 2);
            }
            g.pose().pushMatrix();
            if (lying(item)) {
                // 무기 (그림이 왼쪽 위 끝 → 오른쪽 아래 손잡이): 모루 위에 눕힘 — 끝이 오른쪽 (뿔 쪽) · 손잡이가 대장장이 쪽,
                // 돌린 뒤 위에서 비스듬히 본 만큼 납작하게 (돌리기 → 줄이기 차례: 줄이기를 먼저 곱함)
                g.pose().translate(x0 + ITEM_X + dx, y0 + ITEM_Y);
                g.pose().scale(pop, pop * 0.55F);
                g.pose().rotate((float) (Math.PI * 0.75));
                g.item(item, -8, -8);
            } else {
                // 그 밖 (갑옷 · 장신구 · 아티팩트 · 활 · 방패): 모루 위에 세워 둠 (조금 작게 — 아래가 윗면에 닿음)
                float sc = 0.75F * pop;
                g.pose().translate(x0 + ITEM_X + dx, y0 + HIT_Y - 8F * sc);
                g.pose().scale(sc, sc);
                g.item(item, -8, -8);
            }
            g.pose().popMatrix();
        }
    }

    /** 모루 위에 눕혀 놓는 것: RPG 무기 중 그림이 대각선인 것 (활 · 방패는 세움) */
    private static boolean lying(ItemStack s) {
        String type = CosmeticRender.weaponType(s);
        return !type.isEmpty() && !type.equals("bow") && !type.equals("shield");
    }

    /** 대장장이 (3D — 그 NPC 의 모습, 없으면 기본 마네킹): 망치질 중이면 smith_hammer 자세, 늘 대장장이 망치를 듦 */
    private void drawSmith(GuiGraphicsExtractor g, float partial, int[] sh) {
        Avatar a = smith();
        if (a == null) return;
        EntityRenderState st;
        try {
            st = minecraft.getEntityRenderDispatcher().getRenderer(a).createRenderState(a, 1.0F);
        } catch (RuntimeException err) {
            return;
        }
        st.lightCoords = 0xF000F0;
        st.shadowPieces.clear();
        st.outlineColor = 0;
        st.nameTag = null;
        st.scoreText = null;
        if (st instanceof AvatarRenderState as) {
            Pose p = panelPose(partial);
            as.setData(MotionRender.KEY, p != null ? p : MotionRender.NONE);
            as.bodyRot = 180F + FACE;
            as.yRot = 0F;
            as.xRot = p == null ? 20F : 0F;
            as.walkAnimationPos = 0F;
            as.walkAnimationSpeed = 0F;
            as.isCrouching = false;
            MotionRender.holdIn(a, as, HAMMER);
            as.rightHandItemStack = MotionRender.holdStack(HAMMER);
            as.rightArmPose = HumanoidModel.ArmPose.ITEM;
        }
        int bx0 = gx(SX + 2 + sh[0]);
        int by0 = gy(SY + 2 + sh[1]);
        int bx1 = gx(SX + SW - 2 + sh[0]);
        int by1 = gy(SY + SH - 2 + sh[1]);
        float scale = FIG * s;
        float cx = (bx0 + bx1) / 2F;
        float cy = (by0 + by1) / 2F;
        float tx = (ox + (SX + SMITH_X + sh[0]) * s - cx) / scale;
        float ty = (oy + (SY + FEET_Y + sh[1]) * s - cy) / scale;
        Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(CAM));
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(cam);
        g.entity(st, scale, new Vector3f(tx, ty, 0F), rot, cam, bx0, by0, bx1, by1);
    }

    private static MotionPoses.Motion panelBase;
    private static MotionPoses.Motion panelMotion;

    /**
     * 창 패널의 자세: 망치질 중이면 ClientSmith 의 시각 (세상의 NPC 와 같은 동작), 아니면 쉬는 자세 — 망치질의 첫 자세 (망치를 모루 위에 든 채
     * 모루를 내려다봄) 에서 숨쉬듯 조금 움직임. 망치질은 쉬는 자세에서 시작해 쉬는 자세로 끝남 ({@link MotionPoses#restLoop})
     */
    private static Pose panelPose(float partial) {
        MotionPoses.Motion base = MotionPoses.get(SmithUi.MOTION);
        if (base == null) return null;
        if (panelBase != base) {
            panelBase = base;
            panelMotion = MotionPoses.restLoop(base, 1);
        }
        double t = 0;
        ClientSmith.Work w = ClientSmith.work;
        if (w != null) {
            t = (ClientMotions.ticks() + partial - w.start()) / w.ticks();
            if (t < 0 || t >= 1) t = 0;
        }
        Pose p = MotionPoses.evaluate(panelMotion, (float) t, false, new Pose());
        p.yaw = Float.NaN;
        p.hold = null;
        if (t == 0) {
            double sec = Util.getMillis() / 1000.0;
            p.lean += (float) (0.025 * Math.sin(sec * 1.7));
            p.headX += (float) (0.03 * Math.sin(sec * 1.7 + 0.8));
        }
        return p;
    }

    private Avatar smith() {
        if (minecraft.level == null) return null;
        SmithNet.ViewPayload v = ClientSmith.view;
        if (v != null && v.npc() >= 0 && minecraft.level.getEntity(v.npc()) instanceof Avatar a) return a;
        if (dummy == null || dummy.level() != minecraft.level) dummy = EntityTypes.MANNEQUIN.create(minecraft.level, EntitySpawnReason.COMMAND);
        return dummy;
    }

    /** 패널 앞 (대장장이 위): 불꽃 · 번쩍임 · 결과 연출 · 이름표 · 테 */
    private void drawSceneFront(GuiGraphicsExtractor g, SmithNet.ViewPayload v, int[] sh, long now) {
        int x0 = SX + sh[0];
        int y0 = SY + sh[1];
        // 망치가 닿은 번쩍임 · 고리
        long dt = now - ClientSmith.hitAt;
        if (ClientSmith.hitAt > 0 && dt < 260) {
            float k = dt / 260F;
            GachaArt.sprite(g, GachaArt.GLOW, x0 + HIT_X, y0 + HIT_Y, 22F + 26F * k, 0F, GachaArt.argb((int) (230 * (1 - k)), 0xFFE6A0));
            GachaArt.sprite(g, GachaArt.RING, x0 + HIT_X, y0 + HIT_Y, 8F + 34F * k, 0F, GachaArt.argb((int) (190 * (1 - k)), 0xFFC860));
        }
        // 불꽃 (튀어 오르다 떨어짐 — 패널 밖은 그리지 않음)
        sparks.removeIf(p -> now - p.born() > p.life());
        for (Spark p : sparks) {
            float t = (now - p.born()) / 1000F;
            float px = p.x() + p.vx() * t;
            float py = p.y() + p.vy() * t + 0.5F * 190F * t * t;
            if (px < 2 || px > SW - 2 || py < 2 || py > SH - 2) continue;
            float a = 1F - (now - p.born()) / (float) p.life();
            float vx = p.vx();
            float vy = p.vy() + 190F * t;
            float ang = (float) Math.atan2(vy, vx);
            float len = Math.min(5F, 1.5F + (float) Math.hypot(vx, vy) / 40F);
            int col = GachaArt.mix(p.rgb(), 0xFF3010, Math.min(1, (now - p.born()) / (double) p.life() * 1.2));
            g.pose().pushMatrix();
            g.pose().translate(x0 + px, y0 + py);
            g.pose().rotate(ang);
            g.fill(Math.round(-len), 0, 0, Math.max(1, Math.round(p.size())), GachaArt.argb((int) (255 * a), col));
            g.pose().popMatrix();
        }
        // 결과 연출
        SmithNet.Result r = ClientSmith.result;
        if (r != null && now - ClientSmith.resultAt < RESULT_MS) drawResult(g, r, x0, y0, now - ClientSmith.resultAt);
        // 이름표 (아래)
        String name = j(v.npcName().isEmpty() ? "§6대장장이" : "§6" + v.npcName());
        int nw = Math.min(SW - 12, font.width(name));
        int ny = SY + SH - 18;
        g.fill(SX + (SW - nw) / 2 - 4, ny - 2, SX + (SW + nw) / 2 + 4, ny + 10, 0x90000000);
        fit(g, name, SX + (SW - nw) / 2, ny, SW - 12, 0xFFFFFFFF);
        // 테
        g.outline(SX, SY, SW, SH, 0xC0C8A050);
        g.outline(SX + 1, SY + 1, SW - 2, SH - 2, 0x60000000);
    }

    private void drawResult(GuiGraphicsExtractor g, SmithNet.Result r, int x0, int y0, long ms) {
        float p = ms / (float) RESULT_MS;
        float fade = p < 0.75F ? 1F : (1F - p) / 0.25F;
        float k = ms / 1000F;
        int ix = x0 + ITEM_X;
        int iy = y0 + ITEM_Y;
        switch (r.kind()) {
            case "success", "done" -> {
                GachaArt.sprite(g, GachaArt.RAYS, ix, iy, 70F + 10F * (float) Math.sin(k * 3), k * 0.9F, GachaArt.argb((int) (150 * fade), 0xFFD24A));
                GachaArt.sprite(g, GachaArt.GLOW, ix, iy, 44F, 0F, GachaArt.argb((int) (170 * fade), 0xFFE6A0));
                Random rr = new Random(r.seq() * 31L);
                for (int i = 0; i < 7; i++) {
                    float sx = ix + (rr.nextFloat() - 0.5F) * 70F;
                    float sy = iy - 8 - rr.nextFloat() * 50F;
                    float tw = (float) Math.max(0, Math.sin(k * 6 + i * 1.7));
                    GachaArt.twinkle(g, sx, sy, 5F + 6F * tw, GachaArt.argb((int) (230 * tw * fade), 0xFFF0C0));
                }
                if (ms < 300) g.fill(SX, SY, SX + SW, SY + SH, GachaArt.argb((int) (90 * (1 - ms / 300F)), 0xFFE6A0));
            }
            case "down" -> {
                if (ms < 450) g.fill(SX, SY, SX + SW, SY + SH, GachaArt.argb((int) (130 * (1 - ms / 450F)), 0xD02020));
                GachaArt.sprite(g, GachaArt.GLOW, ix, iy, 40F, 0F, GachaArt.argb((int) (150 * fade), 0xFF3020));
            }
            case "keep" -> {
                for (int i = 0; i < 3; i++) {
                    float q = Math.min(1F, k * 0.8F + i * 0.12F);
                    GachaArt.sprite(g, GachaArt.GLOW, ix + (i - 1) * 8, iy - 6 - q * 26F, 16F + q * 18F, 0F, GachaArt.argb((int) (150 * (1 - q) * fade), 0x9A9AA6));
                }
            }
            case "guard" -> {
                float q = Math.min(1F, k * 1.4F);
                GachaArt.sprite(g, GachaArt.RING, ix, iy, 14F + 40F * q, 0F, GachaArt.argb((int) (220 * (1 - q)), 0xFFD24A));
                GachaArt.twinkle(g, ix, iy - 4, 12F, GachaArt.argb((int) (220 * fade), 0xFFF0C0));
            }
            default -> {}
        }
        // 글자 (패널 위쪽 띠)
        int alpha = (int) (255 * Math.min(1F, fade * 1.2F));
        if (alpha < 8) return;
        g.fill(SX + 3, SY + 6, SX + SW - 3, SY + 34, GachaArt.argb((int) (170 * fade), 0x000000));
        String title = j(r.title());
        float ts = Math.min(1.15F, (SW - 10) / (float) Math.max(1, font.width(title)));
        GachaArt.textCentered(g, font, title, SX + SW / 2F, SY + 9, ts, GachaArt.argb(alpha, 0xFFFFFF));
        String text = j(r.text());
        int tw = Math.min(SW - 10, font.width(text));
        fit(g, text, SX + (SW - tw) / 2, SY + 22, SW - 10, GachaArt.argb(alpha, 0xFFFFFF));
    }

    /** 대장간 소리 (가끔 불 타닥 — 작게) */
    private void ambience(long now) {
        if (now < nextCrackle) return;
        if (nextCrackle > 0) ClientSmith.sound(SoundEvents.CAMPFIRE_CRACKLE, 0.85F + rnd.nextFloat() * 0.3F, 0.16F);
        nextCrackle = now + 1400 + rnd.nextInt(2200);
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        SmithNet.ViewPayload v = ClientSmith.view;
        if (v == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        int mx = Math.round((float) ((event.x() - ox) / s));
        int my = Math.round((float) ((event.y() - oy) / s));
        if (in(closeRect, mx, my)) {
            click();
            onClose();
            return true;
        }
        for (int i = 0; i < tabRects.size() && i < v.tabs().size(); i++) {
            if (!in(tabRects.get(i), mx, my)) continue;
            if (!v.busy() && !v.tabs().get(i).equals(v.tab())) {
                click();
                ClientSmith.send("tab", v.tabs().get(i), 0, 0);
            }
            return true;
        }
        if (!v.busy() && (in(pagePrev, mx, my) || in(pageNext, mx, my)) && !v.pages().isEmpty()) {
            int n = v.pages().size();
            int page = Math.floorMod(v.page() + (in(pageNext, mx, my) ? 1 : -1), n);
            click();
            ClientSmith.send("page", "", page, 0);
            return true;
        }
        SmithNet.Entry sel = entry(v, selKey);
        for (int i = 0; i < variantRects.size(); i++) {
            if (in(variantRects.get(i), mx, my)) {
                variant = i;
                click();
                return true;
            }
        }
        if (in(scrollRect, mx, my) && sel != null && sel.scroll() > 0) {
            useScroll = !useScroll;
            click();
            return true;
        }
        if (in(actionRect, mx, my)) {
            act(v, sel);
            return true;
        }
        if (mx >= LX && mx < LX + LW && my >= LIST_TOP && my < LIST_BOT) {
            pressList = true;
            pressY = my;
            pressFirst = first;
            dragged = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!pressList) return super.mouseDragged(event, dx, dy);
        double my = (event.y() - oy) / s;
        double d = pressY - my;
        if (Math.abs(d) > 4) dragged = true;
        SmithNet.ViewPayload v = ClientSmith.view;
        if (dragged && v != null) first = Math.max(0, Math.min(maxFirst(lines(v)), pressFirst + (int) Math.round(d / ROW)));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (!pressList || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseReleased(event);
        pressList = false;
        if (dragged) return true;
        int mx = Math.round((float) ((event.x() - ox) / s));
        int my = Math.round((float) ((event.y() - oy) / s));
        for (int i = 0; i < rowRects.size(); i++) {
            if (in(rowRects.get(i), mx, my)) {
                if (!rowKeys.get(i).equals(selKey)) {
                    selKey = rowKeys.get(i);
                    variant = -1;
                    SmithNet.ViewPayload v = ClientSmith.view;
                    if (v != null) pickDefault(v);
                    click();
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        double lx = (mx - ox) / s;
        double ly = (my - oy) / s;
        SmithNet.ViewPayload v = ClientSmith.view;
        if (v != null && lx >= LX && lx < LX + LW && ly >= LY && ly < LY + LH && sy != 0) {
            first = Math.max(0, Math.min(maxFirst(lines(v)), first - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 엔터 · 스페이스 = 큰 단추
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) && Util.getMillis() - openedAt > 200) {
            SmithNet.ViewPayload v = ClientSmith.view;
            if (v != null) act(v, entry(v, selKey));
            return true;
        }
        return super.keyPressed(event);
    }

    /** 큰 단추: 서버에 할 일 (공허의 갑옷 = 고른 부위, 수호 주문서 = 하락 위험 · 가졌을 때) */
    private void act(SmithNet.ViewPayload v, SmithNet.Entry e) {
        if (e == null || v.busy() || ClientSmith.work != null && ClientSmith.progress(0) < 1) return;
        boolean can = e.variants().isEmpty() ? e.can() : variant >= 0 && variant < e.variants().size() && e.variants().get(variant).can();
        if (!can || e.state() == 2) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.5F));
            return;
        }
        click();
        ClientSmith.send("do", e.key(), e.variants().isEmpty() ? 0 : variant, e.scroll() > 0 && useScroll ? 1 : 0);
    }

    /** 시험: 큰 단추를 누른 것처럼 */
    public void pressAction() {
        SmithNet.ViewPayload v = ClientSmith.view;
        if (v != null) act(v, entry(v, selKey));
    }

    @Override
    public void removed() {
        ClientSmith.send("close", "", 0, 0);
        super.removed();
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
