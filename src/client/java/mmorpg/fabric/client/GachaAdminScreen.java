package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mmorpg.core.Gacha;
import mmorpg.core.Js;
import mmorpg.core.Json;
import mmorpg.core.Runes;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gacha.GachaSystem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * 한정 뽑기 설정 창 (0.14.0 — 관리자, 뽑기 창의 설정 단추): 이름 · 기간 (일 — 0 = 끝없음) · 룬술사 값 (룬 조각 — 0 = 팔지 않음),
 * 픽업 초월 (계열 그림을 눌러 고름 — 최대 3, 0.16.0: 뒤의 금빛 테두리 셋은 한정 초월 룬 — 한정 뽑기에서만 나옴) · 픽업 영웅 (뽑기 계열 17 — 최대 6),
 * 확률 (초월 · 영웅 % — 나머지 등급은 비율대로), 픽업 몫 (%), 천장 (초월 · 오르기 시작 · 영웅).
 * 저장하면 서버가 검사하고 (틀리면 까닭) 모두에게 알림 — 열린 한정 뽑기를 고치면 시작은 그대로. 지금 끝내기 (두 번 눌러 확인).
 */
public final class GachaAdminScreen extends Screen {
    public static final int W = 384;
    public static final int H = 236;
    static final int ICON_X = 12;
    static final int ICON_STEP = 21;
    static final int ROW5_Y = 60;
    static final int ROW4_Y = 96;
    static final int NUM_Y = 120;
    static final int FOOT_Y = 204;
    static final int BTN_H = 22;

    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier BTN = Mmorpg.id("stats/dialog_btn");
    private static final Identifier BTN_HOVER = Mmorpg.id("stats/dialog_btn_hover");

    /** 숫자 칸: 키 · 이름 · 풀이 */
    private record Field(String key, String label, String tip) {}

    private static final List<Field> FIELDS = List.of(
            new Field("rate5", "초월 %", "초월 기본 확률 (천장 전) — 나머지 등급은 일반 뽑기 비율대로"),
            new Field("rate4", "영웅 %", "영웅 기본 확률"),
            new Field("share", "픽업 몫 %", "초월 · 영웅이 나올 때 픽업 룬일 확률 (원신 50) — 놓치면 다음은 확정"),
            new Field("hard", "초월 천장", "이 회차에 초월 확정 (원신 90)"),
            new Field("softStart", "오르기 시작", "이 회차부터 초월 확률이 회차마다 +6% (원신 74 — 0 이면 오르지 않음)"),
            new Field("heroEvery", "영웅 확정", "이 회 안에 영웅 이상 반드시 (원신 10)"));

    private final GachaScreen parent;
    private int left;
    private int top;
    private EditBox nameBox;
    private EditBox daysBox;
    private EditBox priceBox;
    private final Map<String, EditBox> boxes = new LinkedHashMap<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Set<String> featured5 = new LinkedHashSet<>();
    private final Set<String> featured4 = new LinkedHashSet<>();
    private String editId = "";
    private String message = "";
    private long messageAt;
    private boolean saving;
    private boolean closing;
    private long confirmCloseUntil;

    public GachaAdminScreen(GachaScreen parent) {
        super(Component.literal("한정 뽑기 설정"));
        this.parent = parent;
        load();
    }

    /** 지금 열린 한정 뽑기 (고치기) 또는 기본값 */
    private void load() {
        Gacha.Spec s = ClientGacha.limitedActive() ? ClientGacha.limited() : null;
        Gacha.Spec d = s != null ? s : Gacha.Spec.defaults("", "", List.of(), List.of(), ClientGacha.now());
        editId = s != null ? s.id() : "";
        values.put("name", d.name());
        double days = d.end() > 0 ? Math.max(0, Math.round((d.end() - d.start()) / 86_400_000.0 * 10) / 10.0) : 0;
        values.put("days", Js.str(days));
        values.put("price", String.valueOf(d.price()));
        values.put("rate5", Js.str(d.rate5()));
        values.put("rate4", Js.str(d.rate4()));
        values.put("share", Js.str(d.share()));
        values.put("hard", String.valueOf(d.hard()));
        values.put("softStart", String.valueOf(d.softStart()));
        values.put("heroEvery", String.valueOf(d.heroEvery()));
        featured5.clear();
        featured5.addAll(d.featured5());
        featured4.clear();
        featured4.addAll(d.featured4());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(0, (height - H) / 2);
        if (nameBox != null) keep();
        boxes.clear();
        nameBox = box(left + 44, top + 27, 150, values.get("name"), 32);
        nameBox.setHint(Component.literal("§7예: 불꽃의 축제"));
        daysBox = box(left + 236, top + 27, 30, values.get("days"), 6);
        priceBox = box(left + 322, top + 27, 30, values.get("price"), 4);
        for (int i = 0; i < FIELDS.size(); i++) {
            Field f = FIELDS.get(i);
            int[] r = fieldRect(i);
            boxes.put(f.key(), box(r[0] + 70, r[1], 40, values.get(f.key()), 7));
        }
    }

    private EditBox box(int x, int y, int w, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 16, Component.literal(""));
        b.setMaxLength(max);
        b.setValue(value == null ? "" : value);
        addRenderableWidget(b);
        return b;
    }

    /** 칸의 값을 적어 둠 (창 크기가 바뀌어 다시 만들 때) */
    private void keep() {
        values.put("name", nameBox.getValue());
        values.put("days", daysBox.getValue());
        values.put("price", priceBox.getValue());
        for (Map.Entry<String, EditBox> e : boxes.entrySet()) values.put(e.getKey(), e.getValue().getValue());
    }

    private int[] fieldRect(int i) {
        int col = i % 3;
        int row = i / 3;
        return new int[] {left + 12 + col * 122, top + NUM_Y + 14 + row * 20, 116, 16};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 서버가 상태를 다시 보냄 (저장 · 끝내기 결과) */
    void refresh() {
        String note = ClientGacha.note();
        if (saving || closing) {
            if (!note.isEmpty()) {
                say(note);
                saving = false;
                closing = false;
                ClientGacha.clearNote();
                return;
            }
            boolean ok = closing ? !ClientGacha.limitedActive() : ClientGacha.limitedActive();
            if (ok) {
                GachaArt.sound(SoundEvents.PLAYER_LEVELUP, 1.4F, 0.6F);
                parent.refresh(closing ? GachaSystem.NORMAL : GachaSystem.LIMITED);
                minecraft.gui.setScreen(parent);
            }
        }
    }

    private void say(String s) {
        message = s;
        messageAt = Util.getMillis();
    }

    // ------------------------------------------------------------ 값

    private Map<String, Object> collect() {
        keep();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", editId);
        m.put("name", values.get("name").strip());
        m.put("featured5", new ArrayList<>(featured5));
        m.put("featured4", new ArrayList<>(featured4));
        m.put("days", num(values.get("days"), -1));
        m.put("price", num(values.get("price"), -1));
        for (Field f : FIELDS) m.put(f.key(), num(values.get(f.key()), -1));
        return m;
    }

    private static double num(String s, double def) {
        try {
            double v = Double.parseDouble(s.strip());
            return Double.isFinite(v) ? v : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** 이 칸들로 만든 설정 (시작 = 지금 · 고치기면 원래 시작) — 미리 검사 · 미리보기 */
    private Gacha.Spec preview(Map<String, Object> m) {
        long now = ClientGacha.now();
        long start = !editId.isEmpty() && ClientGacha.limited() != null ? ClientGacha.limited().start() : now;
        double days = ((Number) m.get("days")).doubleValue();
        Map<String, Object> full = new LinkedHashMap<>(m);
        full.put("start", (double) start);
        full.put("end", days > 0 ? (double) (start + (long) (days * 86_400_000L)) : 0.0);
        return Gacha.Spec.fromMap(full, now);
    }

    private void save() {
        Map<String, Object> m = collect();
        List<String> bad = new ArrayList<>();
        if (((Number) m.get("days")).doubleValue() < 0) bad.add("기간은 0 이상 (0 = 끝없음)");
        Gacha.Spec s = preview(m);
        bad.addAll(s.problems());
        if (!bad.isEmpty()) {
            say("§c" + String.join(" · ", bad));
            GachaArt.sound(SoundEvents.NOTE_BLOCK_BASS, 0.7F);
            return;
        }
        saving = true;
        closing = false;
        ClientGacha.clearNote();
        say("§7저장하는 중…");
        ClientGacha.send("limited_save", Json.stringify(m), "");
    }

    // ------------------------------------------------------------ 그리기

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        RpgHud.sprite(g, BG, left, top, W, H);
        super.extractRenderState(g, mx, my, partialTick);
        List<String> tip = null;
        g.text(font, "§c§l한정 뽑기 설정 §r§7(관리자)", left + 12, top + 9, 0xFFFFFFFF, true);
        Gacha.Spec cur = ClientGacha.limited();
        String status = ClientGacha.limitedActive() ? "§a열림 §7" + cur.name() : cur != null ? "§8끝남 §7" + cur.name() : "§8없음";
        GachaArt.textFit(g, font, status, left + W - 12 - Math.min(150, font.width(status)), top + 9, 150, 0xFFFFFFFF);
        g.fill(left + 10, top + 21, left + W - 10, top + 22, 0x90C8A050);
        // 이름 · 기간 · 값
        g.text(font, "§f이름", left + 14, top + 31, 0xFFFFFFFF, true);
        g.text(font, "§f기간", left + 206, top + 31, 0xFFFFFFFF, true);
        g.text(font, "§7일", left + 270, top + 31, 0xFFFFFFFF, true);
        g.text(font, "§f룬술사", left + 286, top + 31, 0xFFFFFFFF, true);
        g.text(font, "§7조각", left + 355, top + 31, 0xFFFFFFFF, true);
        if (my >= top + 27 && my < top + 43) {
            if (mx >= left + 200 && mx < left + 280) tip = List.of("§f기간 (일)", "§70 이면 관리자가 끝낼 때까지", "§7고칠 때는 처음 연 때부터 셈");
            else if (mx >= left + 288 && mx < left + W - 6) tip = List.of("§f룬술사의 한정 룬 뽑기권 값 (룬 조각)", "§70 이면 팔지 않음 (관리자 지급만 — /뽑기 뽑기권)");
        }
        // 픽업 고르기
        int max5 = (int) Gacha.limitedDefaults().num("maxFeatured5");
        int max4 = (int) Gacha.limitedDefaults().num("maxFeatured4");
        g.text(font, "§c§l초월 픽업 §r§7(" + featured5.size() + " / 최대 " + max5 + " — 누르면 고르기)", left + 12, top + ROW5_Y - 12, 0xFFFFFFFF, true);
        List<String> t5 = drawIcons(g, "transcend", featured5, top + ROW5_Y, mx, my);
        g.text(font, "§6§l영웅 픽업 §r§7(" + featured4.size() + " / 최대 " + max4 + ")", left + 12, top + ROW4_Y - 12, 0xFFFFFFFF, true);
        List<String> t4 = drawIcons(g, "hero", featured4, top + ROW4_Y, mx, my);
        if (t5 != null) tip = t5;
        if (t4 != null) tip = t4;
        // 숫자 칸
        g.text(font, "§f§l확률 · 천장", left + 12, top + NUM_Y + 2, 0xFFFFFFFF, true);
        for (int i = 0; i < FIELDS.size(); i++) {
            Field f = FIELDS.get(i);
            int[] r = fieldRect(i);
            g.text(font, "§7" + f.label(), r[0], r[1] + 4, 0xFFFFFFFF, true);
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) tip = List.of("§f" + f.label(), "§7" + f.tip());
        }
        // 미리보기
        Map<String, Object> m = collect();
        Gacha.Spec s = preview(m);
        List<String> bad = s.problems();
        String line;
        if (!bad.isEmpty()) line = "§c" + bad.get(0);
        else {
            String end = s.end() > 0 ? endText(s.end()) + " 까지" : "끝없음";
            line = "§7저장하면: §f" + end + " §7· 천장 포함 초월 약 §f" + GachaScreen.pct(1 / Gacha.expected5(s.banner())) + " §7· 초월 중 픽업 약 §f"
                    + GachaScreen.pct(1 / (2 - Math.max(0, Math.min(1, s.share() / 100.0)))); // 놓치면 다음은 확정 → 1 / (2 − 몫)
        }
        GachaArt.textFit(g, font, line, left + 12, top + NUM_Y + 58, W - 24, 0xFFFFFFFF);
        // 알림
        if (!message.isEmpty() && Util.getMillis() - messageAt < 6000) {
            GachaArt.textFit(g, font, BedrockText.toJava(message), left + 12, top + FOOT_Y - 13, W - 24, 0xFFFFFFFF);
        }
        // 단추
        g.fill(left + 10, top + FOOT_Y - 4, left + W - 10, top + FOOT_Y - 3, 0x60C8A050);
        for (String k : List.of("save", "close", "back")) {
            int[] r = buttonRect(k);
            if (r == null) continue;
            boolean over = in(r, mx, my);
            RpgHud.sprite(g, over ? BTN_HOVER : BTN, r[0], r[1], r[2], r[3]);
            String label = switch (k) {
                case "save" -> (over ? "§0§l" : "§a§l") + (editId.isEmpty() ? "저장 · 열기" : "고치기");
                case "close" -> (over ? "§0§l" : "§c§l") + (Util.getMillis() < confirmCloseUntil ? "한 번 더 누르면 끝" : "지금 끝내기");
                default -> (over ? "§0" : "§f") + "닫기";
            };
            g.text(font, label, r[0] + r[2] / 2 - font.width(label) / 2, r[1] + 7, 0xFFFFFFFF, !over);
            if (over && k.equals("save")) {
                tip = editId.isEmpty() ? List.of("§a새 한정 뽑기를 지금 엶", "§7모두에게 알림 · 뽑기 창에 한정 탭", "§7천장 · 확정은 한정 뽑기끼리 이어짐")
                        : List.of("§a열린 한정 뽑기를 고침", "§7시작은 그대로 (기간은 시작부터)");
            }
            if (over && k.equals("close")) tip = List.of("§c한정 뽑기를 지금 끝냄", "§7한정 룬 뽑기권은 다음 한정 뽑기에 쓸 수 있음");
        }
        if (tip != null) {
            List<Component> lines = new ArrayList<>();
            for (String l : tip) lines.add(Component.literal(BedrockText.toJava(l)));
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
        }
    }

    /** 픽업 줄의 계열: 초월 = 모든 계열 (0.16.0 한정 계열 셋이 뒤에), 영웅 = 뽑기 계열 (한정 계열에는 영웅 룬이 없음) */
    static List<String> rowFamilies(String grade) {
        return "hero".equals(grade) ? Runes.FAMILY_ORDER : Runes.ALL_FAMILIES;
    }

    /** 줄의 칸 간격 (창 넓이 안에 들어가게 — 기본 21) · 칸 크기 (간격보다 크지 않게, 최대 19) */
    static int step(String grade) {
        int n = rowFamilies(grade).size();
        return Math.min(ICON_STEP, (W - 2 * ICON_X - 19) / Math.max(1, n - 1));
    }

    static int box(String grade) {
        return Math.min(19, step(grade) - 1);
    }

    private List<String> drawIcons(GuiGraphicsExtractor g, String grade, Set<String> picked, int y, int mx, int my) {
        List<String> tip = null;
        int col = GachaArt.rgb(grade);
        List<String> fams = rowFamilies(grade);
        int st = step(grade);
        int bx = box(grade);
        float half = bx / 2F;
        int firstLimited = -1;
        for (int i = 0; i < fams.size(); i++) {
            String f = fams.get(i);
            int x = left + ICON_X + i * st;
            boolean on = picked.contains(f);
            boolean lim = Runes.limited(f);
            if (lim && firstLimited < 0) firstLimited = x;
            boolean over = mx >= x && mx < x + bx && my >= y && my < y + bx;
            if (on) {
                GachaArt.sprite(g, GachaArt.GLOW, x + half, y + half, 34, 0F, GachaArt.argb(200, col));
                g.fill(x - 1, y - 1, x + bx + 1, y + bx + 1, GachaArt.argb(255, col));
                g.fill(x, y, x + bx, y + bx, 0xFF1A1A22);
            } else {
                if (lim) g.fill(x - 1, y - 1, x + bx + 1, y + bx + 1, 0xC0E0B040); // 한정: 금빛 테두리
                g.fill(x, y, x + bx, y + bx, over ? 0x60FFFFFF : lim ? 0xFF2A2214 : 0x40000000);
            }
            GachaArt.item(g, GachaArt.rune(f, grade), x + half, y + half, bx / 19F);
            if (!on) g.fill(x, y, x + bx, y + bx, over ? 0x00000000 : 0x50000000);
            if (over) {
                List<String> rt = GachaScreen.runeTip(Runes.runeId(f, grade), false);
                tip = rt == null ? null : new ArrayList<>(rt);
                if (tip != null) tip.add(on ? "§e누르면 픽업에서 뺌" : "§a누르면 픽업으로");
            }
        }
        if (firstLimited >= 0) g.text(font, "§6§l한정", firstLimited, y - 10, 0xFFFFFFFF, true);
        return tip;
    }

    static String endText(long end) {
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(end), ZoneId.systemDefault());
        return String.format(Locale.ROOT, "%d.%02d %02d:%02d", t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
    }

    private int[] buttonRect(String k) {
        int y = top + FOOT_Y;
        return switch (k) {
            case "save" -> new int[] {left + W - 10 - 96, y, 96, BTN_H};
            case "close" -> ClientGacha.limitedActive() ? new int[] {left + W - 10 - 96 - 6 - 104, y, 104, BTN_H} : null;
            default -> new int[] {left + 10, y, 60, BTN_H};
        };
    }

    private static boolean in(int[] r, double mx, double my) {
        return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double mx = event.x();
            double my = event.y();
            if (toggle("transcend", featured5, top + ROW5_Y, mx, my, (int) Gacha.limitedDefaults().num("maxFeatured5"))) return true;
            if (toggle("hero", featured4, top + ROW4_Y, mx, my, (int) Gacha.limitedDefaults().num("maxFeatured4"))) return true;
            if (in(buttonRect("save"), mx, my)) {
                GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
                save();
                return true;
            }
            if (in(buttonRect("close"), mx, my)) {
                GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
                if (Util.getMillis() < confirmCloseUntil) {
                    closing = true;
                    saving = false;
                    confirmCloseUntil = 0;
                    ClientGacha.clearNote();
                    ClientGacha.send("limited_close", "", "");
                } else {
                    confirmCloseUntil = Util.getMillis() + 4000;
                }
                return true;
            }
            if (in(buttonRect("back"), mx, my)) {
                GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, 1F);
                minecraft.gui.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean toggle(String grade, Set<String> picked, int y, double mx, double my, int max) {
        List<String> fams = rowFamilies(grade);
        int st = step(grade);
        int bx = box(grade);
        for (int i = 0; i < fams.size(); i++) {
            int x = left + ICON_X + i * st;
            if (!(mx >= x && mx < x + bx && my >= y && my < y + bx)) continue;
            String f = fams.get(i);
            if (picked.contains(f)) picked.remove(f);
            else if (picked.size() >= max) {
                say("§c" + GachaArt.gradeName(grade) + " 픽업은 " + max + "개까지");
                GachaArt.sound(SoundEvents.NOTE_BLOCK_BASS, 0.7F);
                return true;
            } else picked.add(f);
            GachaArt.sound(SoundEvents.UI_BUTTON_CLICK, picked.contains(f) ? 1.2F : 0.9F);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------ 시험용

    public int[] iconCenter(String grade, String family) {
        int i = rowFamilies(grade).indexOf(family);
        int y = top + ("hero".equals(grade) ? ROW4_Y : ROW5_Y);
        return new int[] {left + ICON_X + i * step(grade) + box(grade) / 2, y + box(grade) / 2};
    }

    public int[] saveCenter() {
        int[] r = buttonRect("save");
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public int[] closeCenter() {
        int[] r = buttonRect("close");
        return r == null ? null : new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public EditBox nameBox() {
        return nameBox;
    }

    public EditBox field(String key) {
        return switch (key) {
            case "days" -> daysBox;
            case "price" -> priceBox;
            default -> boxes.get(key);
        };
    }

    public Set<String> featured(String grade) {
        return "hero".equals(grade) ? featured4 : featured5;
    }

    public String message() {
        return message;
    }
}
