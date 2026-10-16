package mmorpg.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.cosmetic.CosmeticDefs;
import mmorpg.fabric.cosmetic.Look;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 장식 창 (0.13.0 — 스탯 창 장식 단추 · U 키 · /장식): 왼쪽은 내 모습 미리보기 (끌어서 돌리기 · 고른 것을 입어 본 모습 · 칭호 · 오라),
 * 오른쪽은 탭 (무기 · 갑옷 · 머리 장식 · 등 장식 · 오라 · 칭호) 과 목록. 무기 탭은 무기 종류 줄 (스킨은 종류마다 하나 — 숨기기 없음),
 * 갑옷 탭은 부위 줄 (투구 · 갑옷 · 바지 · 신발) + 모두 숨기기 · 모두 보이기 (무료). 줄을 누르면 고르고 (미리보기에 입혀 봄),
 * 아래 단추로 장착 · 원래대로 · 숨기기 · 구매 (에메랄드 — 두 번 눌러 확인). 게임은 멈추지 않음.
 */
public final class CosmeticScreen extends Screen {
    public static final int W = 384;
    public static final int H = 234;
    public static final List<String> TABS = List.of("weapon", "armor", "head", "back", "aura", "title");
    private static final Map<String, String> TAB_LABEL = Map.of("weapon", "무기", "armor", "갑옷", "head", "머리", "back", "등", "aura", "오라", "title", "칭호");
    private static final Map<String, String> TAB_TIP = Map.of("weapon", "무기 스킨 (무기 종류마다 하나 · 숨길 수 없음)", "armor", "갑옷 스킨 · 숨기기",
            "head", "머리 장식", "back", "등 장식", "aura", "오라 (몸 둘레 빛)", "title", "칭호 (이름 위 한 줄)");
    private static final Map<String, String> PIECE_LABEL = Map.of("helmet", "투구", "chest", "갑옷", "legs", "바지", "boots", "신발");

    private static final Identifier BG = Mmorpg.id("stats/dialog_bg");
    private static final Identifier BTN = Mmorpg.id("stats/dialog_btn");
    private static final Identifier BTN_HOVER = Mmorpg.id("stats/dialog_btn_hover");
    private static final Identifier TAB = Mmorpg.id("ui/mode_btn");
    private static final Identifier TAB_ON = Mmorpg.id("ui/mode_btn_on");
    private static final Identifier PANEL = Mmorpg.id("ui/dialog_name");
    private static final Identifier CHECK = Mmorpg.id("ui/icon_check");
    private static final Identifier CROSS = Mmorpg.id("ui/icon_cross");
    private static final ItemStack EMERALD = new ItemStack(Items.EMERALD);

    // 자리 (창 왼쪽 위 기준)
    static final int PREVIEW_X = 10;
    static final int PREVIEW_Y = 26;
    static final int PREVIEW_W = 118;
    static final int PREVIEW_H = H - 26 - 8;
    static final int RIGHT_X = 136;
    static final int RIGHT_W = W - RIGHT_X - 10;
    static final int TAB_Y = 26;
    static final int TAB_H = 17;
    static final int SUB_Y = 46;
    static final int SUB_H = 19;
    static final int LIST_Y = 68;
    static final int ROW_H = 22;
    static final int ROWS = 6;
    static final int FOOT_Y = H - 30;
    static final int ACTION_W = 96;

    /** 목록 한 줄: kind = default (기본 · 원래 · 없음) · hide (숨기기) · item (장식) */
    record Row(String kind, CosmeticDefs.Def def) {}

    private int left;
    private int top;
    private String tab = "weapon";
    private String weaponType;
    private String armorSlot = "helmet";
    private int selected = -1;
    private int scroll;
    /** 미리보기 방향 (0 = 앞, 180 = 뒤) */
    private float yaw = -25F;
    private boolean dragging;
    /** 구매 확인 (같은 장식의 구매 단추를 이 시각 전에 다시 누르면 삼) */
    private String confirmId;
    private long confirmUntil;
    private final Map<String, ItemStack> typeIcons = new HashMap<>();

    public CosmeticScreen() {
        super(Component.literal("장식"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(0, (height - H) / 2);
        if (weaponType == null) {
            String held = minecraft.player == null ? "" : CosmeticRender.weaponType(minecraft.player.getMainHandItem());
            weaponType = held.isEmpty() ? Weapons.allTypes().get(0) : held;
        }
        ClientCosmetics.send("sync", "", "");
        updatePreview();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ClientCosmetics.preview(null);
        super.removed();
    }

    /** 서버가 내 장식을 다시 보냄 (산 뒤 · 입은 뒤) */
    void refresh() {
        updatePreview();
    }

    // ------------------------------------------------------------ 목록

    /** 지금 탭의 칸 (weapon · helmet … · head · back · aura · title) */
    String slot() {
        return switch (tab) {
            case "weapon" -> "weapon";
            case "armor" -> armorSlot;
            default -> tab;
        };
    }

    List<Row> rows() {
        List<Row> out = new ArrayList<>();
        String slot = slot();
        out.add(new Row("default", null));
        if (CosmeticDefs.ARMOR.contains(slot)) out.add(new Row("hide", null));
        for (CosmeticDefs.Def d : CosmeticDefs.of(slot, "weapon".equals(slot) ? weaponType : null)) out.add(new Row("item", d));
        return out;
    }

    /** 칸의 지금 값 (내 모습 — 서버가 보낸 것): 장식 키 · HIDE · null (원래) */
    private String current() {
        Look mine = ClientCosmetics.mine();
        return "weapon".equals(slot()) ? mine.weaponSkin(weaponType) : mine.get(slot());
    }

    private boolean isCurrent(Row r) {
        String cur = current();
        return switch (r.kind()) {
            case "default" -> cur == null;
            case "hide" -> Look.HIDE.equals(cur);
            default -> r.def().id().equals(cur);
        };
    }

    private Row selectedRow() {
        List<Row> rows = rows();
        return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
    }

    /** 고른 줄을 입혀 본 모습 → 미리보기 (내 모습에만 — 다른 사람에게는 보이지 않음) */
    private void updatePreview() {
        Look base = ClientCosmetics.mine();
        Row r = selectedRow();
        Look look = base;
        if (r != null) {
            String slot = slot();
            String v = switch (r.kind()) {
                case "default" -> null;
                case "hide" -> Look.HIDE;
                default -> r.def().id();
            };
            look = "weapon".equals(slot) ? base.withWeapon(weaponType, v) : base.with(slot, v);
        }
        ClientCosmetics.preview(look);
    }

    private void select(int i) {
        selected = i;
        confirmId = null;
        updatePreview();
    }

    private void setTab(String t) {
        if (t.equals(tab)) return;
        tab = t;
        scroll = 0;
        select(-1);
        // 등 장식은 뒤에서 보이게 돌림, 다른 탭은 앞
        yaw = "back".equals(t) ? 155F : -25F;
    }

    // ------------------------------------------------------------ 할 일 (아래 단추)

    /** 아래 단추: 글 · 누를 수 있는지 · 누르면 할 일 */
    private record Action(String label, boolean enabled, Runnable run, List<String> tip) {}

    private Action action() {
        Row r = selectedRow();
        if (r == null) return new Action("§7고르세요", false, null, List.of("§7목록에서 하나를 누르면 미리보기에 입혀 봅니다"));
        String slot = slot();
        if (isCurrent(r)) return new Action("§a✔ 지금 모습", false, null, List.of("§7이미 이 모습입니다"));
        switch (r.kind()) {
            case "default": {
                String label = CosmeticDefs.ARMOR.contains(slot) ? "원래 갑옷으로" : "weapon".equals(slot) ? "기본 모습으로" : "벗기";
                return new Action("§f" + label, true, () -> ClientCosmetics.send("clear", slot, weaponType), List.of("§7무료"));
            }
            case "hide":
                return new Action("§f숨기기", true, () -> ClientCosmetics.send("hide", slot, ""),
                        List.of("§7무료 · 언제든 다시 보이게 할 수 있음", "§8막는 수치는 그대로 (모습만)"));
            default: {
                CosmeticDefs.Def d = r.def();
                if (ClientCosmetics.owns(d.id())) return new Action("§e장착", true, () -> ClientCosmetics.send("equip", d.id(), ""), List.of("§7가진 장식"));
                int money = ClientCosmetics.money();
                if (money < d.price()) {
                    return new Action("§c에메랄드 부족", false, null, List.of("§c" + d.price() + " 필요 §7(가진 것 " + money + ")"));
                }
                boolean confirming = d.id().equals(confirmId) && Util.getMillis() < confirmUntil;
                if (confirming) {
                    return new Action("§6§l정말 살까요?", true, () -> {
                        ClientCosmetics.send("buy", d.id(), "");
                        confirmId = null;
                    }, List.of("§e한 번 더 누르면 삽니다 §7(에메랄드 " + d.price() + ")"));
                }
                return new Action("§a구매 §f" + d.price(), true, () -> {
                    confirmId = d.id();
                    confirmUntil = Util.getMillis() + 4000;
                }, List.of("§7에메랄드 " + d.price() + " §8(가진 것 " + money + ")", "§7사면 바로 입습니다 · 한 번 사면 계속 가짐"));
            }
        }
    }

    // ------------------------------------------------------------ 자리 (시험용 — 화면 좌표)

    public int[] tabCenter(String t) {
        int i = TABS.indexOf(t);
        int w = tabW();
        return new int[] {left + RIGHT_X + i * (w + 3) + w / 2, top + TAB_Y + TAB_H / 2};
    }

    public int[] rowCenter(int i) {
        return new int[] {left + RIGHT_X + RIGHT_W / 2, top + LIST_Y + (i - scroll) * ROW_H + ROW_H / 2};
    }

    public int[] actionCenter() {
        return new int[] {left + W - 10 - ACTION_W / 2, top + FOOT_Y + 11};
    }

    public int[] subCenter(int i) {
        int[] r = subRect(i);
        return new int[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
    }

    public int[] previewCenter() {
        return new int[] {left + PREVIEW_X + PREVIEW_W / 2, top + PREVIEW_Y + PREVIEW_H / 2};
    }

    /** 시험용: 목록 줄의 장식 키 ("" = 기본 · 숨기기) */
    public List<String> rowIds() {
        List<String> out = new ArrayList<>();
        for (Row r : rows()) out.add(r.def() == null ? r.kind() : r.def().id());
        return out;
    }

    public String tab() {
        return tab;
    }

    public void setWeaponType(String type) {
        weaponType = type;
        select(-1);
    }

    public void setArmorSlot(String slot) {
        armorSlot = slot;
        select(-1);
    }

    public void setYaw(float y) {
        yaw = y;
    }

    private int tabW() {
        return (RIGHT_W - 3 * (TABS.size() - 1)) / TABS.size();
    }

    /** 아래 줄 단추 i 의 {x, y, w, h}: 무기 = 무기 종류들, 갑옷 = 부위 넷 + 모두 숨기기 · 모두 보이기 */
    private int[] subRect(int i) {
        int x0 = left + RIGHT_X;
        int y = top + SUB_Y;
        if ("weapon".equals(tab)) {
            int n = Weapons.allTypes().size();
            int w = (RIGHT_W - (n - 1)) / n;
            return new int[] {x0 + i * (w + 1), y, w, SUB_H};
        }
        if (i < 4) return new int[] {x0 + i * 48, y, 46, SUB_H};
        return new int[] {x0 + 4 * 48 + (i - 4) * 22 + 2, y, 20, SUB_H};
    }

    private int subCount() {
        return switch (tab) {
            case "weapon" -> Weapons.allTypes().size();
            case "armor" -> 6;
            default -> 0;
        };
    }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ------------------------------------------------------------ 그리기

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        updatePreview();
        RpgHud.sprite(g, BG, left, top, W, H);
        long now = Util.getMillis();

        // 제목 · 가진 에메랄드
        g.text(font, "§d§l장식", left + 12, top + 9, 0xFFFFFFFF, true);
        g.text(font, "§7· 무기 · 갑옷 스킨, 장식, 오라, 칭호", left + 46, top + 9, 0xFFFFFFFF, true);
        String money = "§a§l" + ClientCosmetics.money();
        int mw = font.width(money);
        g.text(font, money, left + W - 12 - mw, top + 9, 0xFFFFFFFF, true);
        g.pose().pushMatrix();
        g.pose().translate(left + W - 12 - mw - 15, top + 5);
        g.pose().scale(0.8125F, 0.8125F);
        g.item(EMERALD, 0, 0);
        g.pose().popMatrix();
        g.fill(left + 10, top + 21, left + W - 10, top + 22, 0x90C8A050);

        drawPreview(g, player, mx, my, now);

        List<String> tip = null;
        // 탭
        int tw = tabW();
        for (int i = 0; i < TABS.size(); i++) {
            String t = TABS.get(i);
            int x = left + RIGHT_X + i * (tw + 3);
            int y = top + TAB_Y;
            boolean on = t.equals(tab);
            boolean over = mx >= x && mx < x + tw && my >= y && my < y + TAB_H;
            RpgHud.sprite(g, on ? TAB_ON : over ? BTN_HOVER : TAB, x, y, tw, TAB_H);
            String label = (on ? "§f§l" : over ? "§0" : "§7") + TAB_LABEL.get(t);
            g.text(font, label, x + tw / 2 - font.width(label) / 2, y + 5, 0xFFFFFFFF, !over || on);
            if (over) tip = List.of("§d" + TAB_TIP.get(t));
        }

        // 아래 줄 (무기 종류 · 갑옷 부위)
        int nsub = subCount();
        for (int i = 0; i < nsub; i++) {
            int[] r = subRect(i);
            boolean over = in(r, mx, my);
            if ("weapon".equals(tab)) {
                String type = Weapons.allTypes().get(i);
                boolean on = type.equals(weaponType);
                RpgHud.sprite(g, on ? TAB_ON : over ? BTN_HOVER : TAB, r[0], r[1], r[2], r[3]);
                ItemStack icon = typeIcon(type);
                if (!icon.isEmpty()) g.item(icon, r[0] + (r[2] - 16) / 2, r[1] + 1);
                if (ClientCosmetics.mine().weaponSkin(type) != null) g.fill(r[0] + r[2] - 4, r[1] + 2, r[0] + r[2] - 2, r[1] + 4, 0xFFFF7FD8);
                if (over) {
                    String skin = ClientCosmetics.mine().weaponSkin(type);
                    CosmeticDefs.Def d = CosmeticDefs.get(skin);
                    tip = List.of("§f" + typeName(type), d == null ? "§8기본 모습" : "§d스킨: " + d.name());
                }
            } else if (i < 4) {
                String piece = CosmeticDefs.ARMOR.get(i);
                boolean on = piece.equals(armorSlot);
                RpgHud.sprite(g, on ? TAB_ON : over ? BTN_HOVER : TAB, r[0], r[1], r[2], r[3]);
                g.item(pieceIcon(piece), r[0] + 2, r[1] + 1);
                String v = ClientCosmetics.mine().get(piece);
                String state = v == null ? "" : Look.HIDE.equals(v) ? "§c" : "§d";
                g.text(font, (on ? "§f" : over ? "§0" : "§7") + PIECE_LABEL.get(piece), r[0] + 19, r[1] + 6, 0xFFFFFFFF, !over || on);
                if (!state.isEmpty()) g.fill(r[0] + r[2] - 4, r[1] + 2, r[0] + r[2] - 2, r[1] + 4, Look.HIDE.equals(v) ? 0xFFFF5555 : 0xFFFF7FD8);
                if (over) tip = List.of("§f" + PIECE_LABEL.get(piece), v == null ? "§8원래 갑옷" : Look.HIDE.equals(v) ? "§c숨김" : "§d스킨: " + name(v));
            } else {
                boolean hideAll = i == 4;
                RpgHud.sprite(g, over ? BTN_HOVER : BTN, r[0], r[1], r[2], r[3]);
                RpgHud.sprite(g, hideAll ? CROSS : CHECK, r[0] + 2, r[1] + 1, 16, 16);
                if (over) tip = List.of(hideAll ? "§c갑옷 모두 숨기기" : "§a갑옷 모두 원래대로", "§7무료 · 투구 · 갑옷 · 바지 · 신발");
            }
        }
        if (nsub == 0) {
            String slotName = CosmeticDefs.slot(slot()).name();
            g.text(font, "§7" + slotName + " §8— " + subtitle(), left + RIGHT_X + 2, top + SUB_Y + 6, 0xFFFFFFFF, true);
        }

        // 목록
        List<Row> rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - ROWS)));
        for (int vi = 0; vi < ROWS && scroll + vi < rows.size(); vi++) {
            int i = scroll + vi;
            Row r = rows.get(i);
            int x = left + RIGHT_X;
            int y = top + LIST_Y + vi * ROW_H;
            boolean over = mx >= x && mx < x + RIGHT_W && my >= y && my < y + ROW_H - 2;
            boolean sel = i == selected;
            boolean cur = isCurrent(r);
            g.fill(x, y, x + RIGHT_W, y + ROW_H - 2, sel ? 0x60FFD24A : over ? 0x34FFFFFF : 0x26000000);
            if (cur) g.fill(x, y, x + 2, y + ROW_H - 2, 0xFF7CFF8A);
            drawRow(g, r, x, y, cur);
            if (over) tip = rowTip(r, cur);
        }
        if (rows.size() > ROWS) {
            int bx = left + RIGHT_X + RIGHT_W - 2;
            int top0 = top + LIST_Y;
            int hgt = ROWS * ROW_H - 2;
            int barH = Math.max(10, hgt * ROWS / rows.size());
            int barY = top0 + (hgt - barH) * scroll / Math.max(1, rows.size() - ROWS);
            g.fill(bx, top0, bx + 2, top0 + hgt, 0x40000000);
            g.fill(bx, barY, bx + 2, barY + barH, 0xC0C8A050);
        }

        // 아래: 고른 것의 설명 · 할 일 단추
        g.fill(left + RIGHT_X, top + FOOT_Y - 4, left + W - 10, top + FOOT_Y - 3, 0x60C8A050);
        Row sr = selectedRow();
        String info = sr == null ? "§8줄을 누르면 미리보기에 입혀 봅니다" : sr.def() == null ? (sr.kind().equals("hide") ? "§7이 칸의 갑옷을 숨깁니다 (무료)" : "§7원래 모습 (무료)")
                : sr.def().desc().isEmpty() ? "§7" + sr.def().name() : sr.def().desc().get(0);
        drawFit(g, BedrockText.toJava(info), left + RIGHT_X + 2, top + FOOT_Y + 7, RIGHT_W - ACTION_W - 8);
        Action a = action();
        int ax = left + W - 10 - ACTION_W;
        int ay = top + FOOT_Y;
        boolean overA = mx >= ax && mx < ax + ACTION_W && my >= ay && my < ay + 22;
        RpgHud.sprite(g, a.enabled() && overA ? BTN_HOVER : BTN, ax, ay, ACTION_W, 22);
        String al = BedrockText.toJava(a.label());
        if (sr != null && sr.def() != null && a.label().startsWith("§a구매")) {
            int lw = font.width(al) + 14;
            int lx = ax + ACTION_W / 2 - lw / 2;
            g.text(font, al, lx, ay + 7, 0xFFFFFFFF, true);
            g.pose().pushMatrix();
            g.pose().translate(lx + font.width(al) + 2, ay + 4);
            g.pose().scale(0.75F, 0.75F);
            g.item(EMERALD, 0, 0);
            g.pose().popMatrix();
        } else {
            g.text(font, al, ax + ACTION_W / 2 - font.width(al) / 2, ay + 7, a.enabled() ? 0xFFFFFFFF : 0xFFB0B0B0, true);
        }
        if (overA) tip = a.tip();

        if (tip != null && !tip.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : tip) lines.add(Component.literal(BedrockText.toJava(l)));
            g.setTooltipForNextFrame(font, lines, Optional.empty(), mx, my);
        }
    }

    private String subtitle() {
        return switch (tab) {
            case "head" -> "머리 위에 쓰는 장식";
            case "back" -> "등에 메는 장식 (망토 · 날개)";
            case "aura" -> "몸 둘레의 빛 (다른 사람에게도 보임)";
            case "title" -> "이름 위에 보이는 한 줄";
            default -> "";
        };
    }

    private void drawRow(GuiGraphicsExtractor g, Row r, int x, int y, boolean cur) {
        int iy = y + (ROW_H - 2 - 16) / 2;
        String name;
        String right;
        switch (r.kind()) {
            case "default" -> {
                RpgHud.sprite(g, CHECK, x + 4, iy, 16, 16);
                name = "§f" + ("weapon".equals(slot()) ? "기본 모습" : CosmeticDefs.ARMOR.contains(slot()) ? "원래 갑옷" : "없음");
                right = cur ? "§a사용 중" : "§8무료";
            }
            case "hide" -> {
                RpgHud.sprite(g, CROSS, x + 4, iy, 16, 16);
                name = "§c숨기기";
                right = cur ? "§a사용 중" : "§8무료";
            }
            default -> {
                CosmeticDefs.Def d = r.def();
                g.item(CosmeticRender.icon(d.id()), x + 4, iy);
                name = "title".equals(d.slot()) && d.title() != null ? d.title() : colored(d) + d.name();
                boolean owned = ClientCosmetics.owns(d.id());
                right = cur ? "§a장착 중" : owned ? "§7가짐" : "§e" + d.price();
                if (!cur && !owned) {
                    int rw = font.width(BedrockText.toJava(right));
                    g.pose().pushMatrix();
                    g.pose().translate(x + RIGHT_W - 8 - rw - 13, y + 4);
                    g.pose().scale(0.75F, 0.75F);
                    g.item(EMERALD, 0, 0);
                    g.pose().popMatrix();
                }
            }
        }
        g.text(font, BedrockText.toJava(name), x + 25, y + 6, 0xFFFFFFFF, true);
        String rr = BedrockText.toJava(right);
        g.text(font, rr, x + RIGHT_W - 8 - font.width(rr), y + 6, 0xFFFFFFFF, true);
    }

    /** 장식 색에 가까운 글자 색 (§ 코드 — 밝은 색 위주) */
    private static String colored(CosmeticDefs.Def d) {
        int rgb = d.rgb();
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        if (r > 200 && g > 200 && b > 200) return "§f";
        if (r > 200 && g < 120 && b < 120) return "§c";
        if (r > 200 && g > 150 && b < 120) return "§6";
        if (r > 200 && b > 150 && g < 170) return "§d";
        if (r > 200 && g < 170) return "§6";
        if (b > 200 && r < 150 && g > 180) return "§b";
        if (b > 200 && r > 120) return "§d";
        if (b > 200) return "§9";
        if (g > 200) return "§a";
        return "§f";
    }

    private List<String> rowTip(Row r, boolean cur) {
        List<String> tip = new ArrayList<>();
        switch (r.kind()) {
            case "default" -> {
                tip.add("§f" + ("weapon".equals(slot()) ? "기본 모습" : CosmeticDefs.ARMOR.contains(slot()) ? "원래 갑옷" : "없음"));
                tip.add("weapon".equals(slot()) ? "§7스킨 없이 무기 그대로" : CosmeticDefs.ARMOR.contains(slot()) ? "§7입은 갑옷을 그대로 보임" : "§7이 칸의 장식을 벗음");
            }
            case "hide" -> {
                tip.add("§c" + PIECE_LABEL.get(slot()) + " 숨기기");
                tip.add("§7입은 갑옷을 보이지 않게 (무료)");
                tip.add("§8막는 수치는 그대로");
            }
            default -> {
                CosmeticDefs.Def d = r.def();
                tip.add(colored(d) + "§l" + d.name() + " §r§8(" + CosmeticDefs.slot(d.slot()).name()
                        + (d.weapon() != null ? " · " + typeName(d.weapon()) : "") + ")");
                if ("title".equals(d.slot()) && d.title() != null) tip.add(d.title());
                tip.addAll(d.desc());
                if (d.aura() != null) tip.add("§7" + CosmeticAura.label(d.aura().kind()));
                if ("weapon".equals(d.slot())) tip.add("§8손에 든 모습만 바뀜 (아이콘 · 수치는 그대로)");
                tip.add(cur ? "§a장착 중" : ClientCosmetics.owns(d.id()) ? "§7가진 장식 — 장착 단추로 입기" : "§e에메랄드 " + d.price() + " §7(가진 것 " + ClientCosmetics.money() + ")");
            }
        }
        if (!cur) tip.add("§8누르면 미리보기에 입혀 봄");
        return tip;
    }

    private static String name(String id) {
        CosmeticDefs.Def d = CosmeticDefs.get(id);
        return d == null ? id : d.name();
    }

    private static String typeName(String type) {
        try {
            return mmorpg.core.Rules.get().obj("TYPES").obj(type).str("name");
        } catch (RuntimeException e) {
            return type;
        }
    }

    /** 글이 넓으면 줄여서 */
    private void drawFit(GuiGraphicsExtractor g, String s, int x, int y, int maxW) {
        int w = font.width(s);
        if (w <= maxW) {
            g.text(font, s, x, y, 0xFFFFFFFF, true);
            return;
        }
        float k = Math.max(0.6F, maxW / (float) w);
        g.pose().pushMatrix();
        g.pose().translate(x, y + (1 - k) * 4);
        g.pose().scale(k, k);
        g.text(font, s, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    /** 무기 종류의 대표 무기 (아이콘 · 미리보기 — 고귀 · 영웅 · 희귀 · 일반 · 초월 중 있는 것) */
    ItemStack typeIcon(String type) {
        return typeIcons.computeIfAbsent(type, t -> {
            for (String grade : List.of("noble", "hero", "rare", "common", "transcend")) {
                for (double lv : Weapons.levelsOf(t, grade)) {
                    Identifier id = Identifier.tryParse(Weapons.weaponId(t, lv, grade));
                    if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                }
            }
            return ItemStack.EMPTY;
        });
    }

    private static ItemStack pieceIcon(String piece) {
        return new ItemStack(switch (piece) {
            case "helmet" -> Items.IRON_HELMET;
            case "chest" -> Items.IRON_CHESTPLATE;
            case "legs" -> Items.IRON_LEGGINGS;
            default -> Items.IRON_BOOTS;
        });
    }

    // ------------------------------------------------------------ 미리보기

    /** 미리보기 사람 크기 (블록 하나가 몇 점) · 머리 위 자리 (판 위에서) — 큰 모자 · 날개도 판 안에 들게 */
    static final float FIGURE_SCALE = 56F;
    static final int FIGURE_HEAD = 54;

    private void drawPreview(GuiGraphicsExtractor g, LocalPlayer p, int mx, int my, long now) {
        int x0 = left + PREVIEW_X;
        int y0 = top + PREVIEW_Y;
        int x1 = x0 + PREVIEW_W;
        int y1 = y0 + PREVIEW_H;
        RpgHud.sprite(g, PANEL, x0, y0, PREVIEW_W, PREVIEW_H);
        Look look = ClientCosmetics.lookOf(p.getUUID());
        CosmeticDefs.Def aura = look.def("aura");
        int bx0 = x0 + 3;
        int by0 = y0 + 3;
        int bx1 = x1 - 3;
        int by1 = y1 - 3;
        float h = p.getBbHeight();
        int headY = y0 + FIGURE_HEAD;
        int feetY = headY + Math.round(h * FIGURE_SCALE);
        int cx = (x0 + x1) / 2;
        g.enableScissor(bx0, by0, bx1, by1);
        if (aura != null && aura.aura() != null) drawAura(g, aura.aura(), cx, headY, feetY, now, false);

        // 사람 (끌어서 돌린 방향 · 고른 것을 입어 본 모습)
        float oBody = p.yBodyRot;
        float oYRot = p.getYRot();
        float oXRot = p.getXRot();
        float oHead = p.yHeadRot;
        float oHeadO = p.yHeadRotO;
        EntityRenderState state;
        try {
            p.yBodyRot = 180F + yaw;
            p.setYRot(180F + yaw);
            p.setXRot(0F);
            p.yHeadRot = p.getYRot();
            p.yHeadRotO = p.getYRot();
            state = minecraft.getEntityRenderDispatcher().getRenderer(p).createRenderState(p, 1.0F);
        } finally {
            p.yBodyRot = oBody;
            p.setYRot(oYRot);
            p.setXRot(oXRot);
            p.yHeadRot = oHead;
            p.yHeadRotO = oHeadO;
        }
        state.lightCoords = 0xF000F0;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        state.scoreText = null;
        if ("weapon".equals(tab) && state instanceof AvatarRenderState as) {
            // 무기 탭: 그 종류의 무기를 든 모습 (손에 그 종류가 없으면 대표 무기)
            ItemStack held = p.getMainHandItem();
            ItemStack show = weaponType.equals(CosmeticRender.weaponType(held)) ? held : typeIcon(weaponType);
            if (!show.isEmpty()) {
                as.rightHandItemStack = show;
                minecraft.getItemModelResolver().updateForLiving(as.rightHandItemState, show, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, p);
                as.rightArmPose = HumanoidModel.ArmPose.ITEM;
            }
        }
        // 그림 상자 가운데 기준: 발 = 가운데 + ty × 크기 → 머리가 headY 에 오게
        float centerY = (by0 + by1) / 2F;
        float ty = (headY - centerY) / FIGURE_SCALE + h;
        Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(8));
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(cam);
        g.entity(state, FIGURE_SCALE, new Vector3f(0F, ty, 0F), rot, cam, bx0, by0, bx1, by1);
        if (aura != null && aura.aura() != null) drawAura(g, aura.aura(), cx, headY, feetY, now, true);
        g.disableScissor();

        // 이름표 (발 아래 — 큰 모자 · 고리와 겹치지 않게): 칭호 한 줄 + 이름
        CosmeticDefs.Def title = look.def("title");
        String nameLine = p.getName().getString();
        int ny = feetY + 15;
        int nw = font.width(nameLine);
        g.fill(cx - nw / 2 - 2, ny - 1, cx + nw / 2 + 2, ny + 9, 0x50000000);
        g.text(font, nameLine, cx - nw / 2, ny, 0xFFFFFFFF, false);
        if (title != null && title.title() != null) {
            String t = BedrockText.toJava(title.title());
            int tw = font.width(t);
            g.fill(cx - tw / 2 - 2, ny - 12, cx + tw / 2 + 2, ny - 2, 0x50000000);
            g.text(font, t, cx - tw / 2, ny - 11, 0xFFFFFFFF, true);
        }
        String hint = BedrockText.toJava(dragging ? "§e돌리는 중" : "§8끌어서 돌리기");
        g.text(font, hint, cx - font.width(hint) / 2, y1 - 12, 0xFFFFFFFF, false);
    }

    /** 미리보기 오라 (그림 — 세계의 파티클과 비슷하게): front = 사람 앞쪽에 그릴 것 */
    private void drawAura(GuiGraphicsExtractor g, CosmeticDefs.Aura a, int cx, int top0, int bottom, long now, boolean front) {
        List<double[]> cols = a.colors();
        double t = now / 1000.0;
        int h = bottom - top0;
        switch (a.kind()) {
            case "orbit" -> {
                for (int k = 0; k < 2; k++) {
                    for (int tail = 0; tail < 7; tail++) {
                        double ang = (t - tail * 0.05) * Math.PI + k * Math.PI;
                        boolean isFront = Math.sin(ang) > 0;
                        if (isFront != front) continue;
                        int x = cx + (int) Math.round(Math.cos(ang) * 30);
                        int y = top0 + (int) (h * 0.46 + Math.sin((t - tail * 0.05) * 2.4 + k * Math.PI) * 8 + Math.sin(ang) * 6);
                        int s = Math.max(1, 3 - tail / 2);
                        int alpha = 230 - tail * 30;
                        g.fill(x - s, y - s, x + s, y + s, (alpha << 24) | rgb(cols.get(k % cols.size())));
                    }
                }
            }
            case "frost" -> {
                if (!front) return;
                for (int i = 0; i < 12; i++) {
                    double ph = (t * 0.35 + i * 0.137) % 1.0;
                    int x = cx + (int) Math.round(Math.sin(i * 2.3 + t * 0.6) * 26);
                    int y = top0 - 8 + (int) (ph * (h + 6));
                    int alpha = (int) (220 * Math.sin(Math.PI * ph));
                    g.fill(x - 1, y - 1, x + 1, y + 1, (alpha << 24) | rgb(cols.get(i % cols.size())));
                }
                double ring = (t % 1.5) / 1.5;
                int rw = (int) (14 + ring * 26);
                int alpha = (int) (160 * (1 - ring));
                g.fill(cx - rw, bottom - 1, cx + rw, bottom, (alpha << 24) | rgb(cols.get(0)));
            }
            default -> {
                if (!front) return;
                for (int i = 0; i < 14; i++) {
                    double ph = (t * 0.7 + i * 0.173) % 1.0;
                    int x = cx + (int) Math.round(Math.sin(i * 1.7) * 22 + Math.sin(t * 2 + i) * 2);
                    int y = bottom - (int) (ph * h * 0.75);
                    int alpha = (int) (230 * (1 - ph));
                    int s = ph < 0.5 ? 2 : 1;
                    g.fill(x - s / 2, y - s / 2, x + s - s / 2, y + s - s / 2, (alpha << 24) | rgb(cols.get(i % cols.size())));
                }
            }
        }
    }

    private static int rgb(double[] c) {
        return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }

    // ------------------------------------------------------------ 입력

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        int tw = tabW();
        for (int i = 0; i < TABS.size(); i++) {
            int x = left + RIGHT_X + i * (tw + 3);
            int y = top + TAB_Y;
            if (mx >= x && mx < x + tw && my >= y && my < y + TAB_H) {
                click();
                setTab(TABS.get(i));
                return true;
            }
        }
        for (int i = 0; i < subCount(); i++) {
            if (!in(subRect(i), mx, my)) continue;
            click();
            if ("weapon".equals(tab)) setWeaponType(Weapons.allTypes().get(i));
            else if (i < 4) setArmorSlot(CosmeticDefs.ARMOR.get(i));
            else ClientCosmetics.send(i == 4 ? "hide_all" : "show_all", "", "");
            return true;
        }
        List<Row> rows = rows();
        for (int vi = 0; vi < ROWS && scroll + vi < rows.size(); vi++) {
            int x = left + RIGHT_X;
            int y = top + LIST_Y + vi * ROW_H;
            if (mx >= x && mx < x + RIGHT_W && my >= y && my < y + ROW_H - 2) {
                click();
                int i = scroll + vi;
                if (doubleClick && i == selected) {
                    Action a = action();
                    if (a.enabled() && a.run() != null && !(rows.get(i).def() != null && !ClientCosmetics.owns(rows.get(i).def().id()))) a.run().run();
                } else {
                    select(i);
                }
                return true;
            }
        }
        int[] ac = actionCenter();
        if (Math.abs(mx - ac[0]) <= ACTION_W / 2.0 && Math.abs(my - ac[1]) <= 11) {
            Action a = action();
            if (a.enabled() && a.run() != null) {
                click();
                a.run().run();
            }
            return true;
        }
        if (mx >= left + PREVIEW_X && mx < left + PREVIEW_X + PREVIEW_W && my >= top + PREVIEW_Y && my < top + PREVIEW_Y + PREVIEW_H) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            yaw -= (float) dx * 2.2F;
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        int n = rows().size();
        if (n > ROWS) {
            scroll = Math.max(0, Math.min(n - ROWS, scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (MmorpgClient.cosmeticKey != null && MmorpgClient.cosmeticKey.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
