package mmorpg.fabric.client.rpgui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mmorpg.core.Economy;
import mmorpg.core.Hp;
import mmorpg.core.Js;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.StatSheet;
import mmorpg.fabric.client.ClientRpgState;
import mmorpg.fabric.client.ClientWallet;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.mixin.AbstractContainerScreenAccessor;
import mmorpg.fabric.client.mixin.AbstractRecipeBookScreenAccessor;
import mmorpg.fabric.economy.EconomyItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 0.23.0 캐릭터 정보 패널 (인벤토리 창 왼쪽 — 사용자가 고른 것): 이름 · 레벨 · 경험치 막대, 체력 (막대), 힘 · 민첩 · 마력 · 체력, 기준 무기의 피해 ·
 * 치명타 (스탯 창과 같은 식), 방어, 골드 · 실버, "스탯 창" 단추 (남은 포인트가 있으면 +N). 스탯 줄에 마우스를 올리면 스탯 창과 같은 풀이.
 * 조합법 책이 열리면 (창 왼쪽을 책이 씀) · 화면이 좁으면 숨김. 패널을 누르면 아래로 새지 않음 (든 아이템이 버려지지 않게).
 */
public final class CharacterPanel extends AbstractWidget {
    public static final int W = 100;
    public static final int H = 166;
    static final int GAP = 2;
    /** 스탯 줄 (위에서) · 줄 높이 */
    static final int STAT_Y = 43;
    static final int STAT_H = 13;
    /** 단추 (위에서 · 높이) */
    static final int BTN_Y = 146;
    static final int BTN_H = 15;
    static final int ICON = 10;
    private static final ItemStack GOLD = new ItemStack(EconomyItems.GOLD_COIN);
    private static final ItemStack SILVER = new ItemStack(EconomyItems.SILVER_COIN);

    private final InventoryScreen screen;
    private boolean pressed;

    CharacterPanel(InventoryScreen screen) {
        super(0, 0, W, H, Component.literal("캐릭터 정보"));
        this.screen = screen;
        place();
    }

    /** 창 왼쪽에 (조합법 책을 열고 닫으면 창이 움직이므로 그릴 때마다) */
    private void place() {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        setX(a.mmorpg$leftPos() - W - GAP);
        setY(a.mmorpg$topPos());
    }

    /** 보이는지: 조합법 책이 닫혀 있고 창 왼쪽에 자리가 있을 때 */
    public boolean shown() {
        place();
        if (getX() < 2) return false;
        var book = ((AbstractRecipeBookScreenAccessor) screen).mmorpg$recipeBook();
        return book == null || !book.isVisible();
    }

    /** 시험용: 스탯 창 단추 가운데 (화면 좌표) */
    public int[] buttonCenter() {
        place();
        return new int[] {getX() + W / 2, getY() + BTN_Y + BTN_H / 2};
    }

    /** 시험용: 스탯 줄 i 의 가운데 */
    public int[] statCenter(int i) {
        place();
        return new int[] {getX() + W / 2, getY() + STAT_Y + i * STAT_H + STAT_H / 2};
    }

    private boolean inButton(double mx, double my) {
        return mx >= getX() + 8 && mx < getX() + W - 8 && my >= getY() + BTN_Y && my < getY() + BTN_Y + BTN_H;
    }

    private int statAt(double mx, double my) {
        if (mx < getX() + 6 || mx >= getX() + W - 6) return -1;
        for (int i = 0; i < Stats.STAT_KEYS.size(); i++) {
            int y = getY() + STAT_Y + i * STAT_H;
            if (my >= y && my < y + STAT_H) return i;
        }
        return -1;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible && shown() && mx >= getX() && mx < getX() + W && my >= getY() && my < getY() + H;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;
        if (inButton(event.x(), event.y())) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            Minecraft.getInstance().gui.setScreen(new StatScreen());
        }
        return true; // 패널 위를 누르면 창 밖 누르기 (든 아이템 버리기) 가 되지 않게
    }

    /** 1,234 · 12.3k · 1.2m (칸에 들어가게) */
    static String shortAmount(long v) {
        if (v < 10_000) return Economy.fmt(v);
        if (v < 1_000_000) return String.format(Locale.ROOT, "%.1fk", v / 1000.0).replace(".0k", "k");
        return String.format(Locale.ROOT, "%.1fm", v / 1_000_000.0).replace(".0m", "m");
    }

    private static String num(double v) {
        return Js.str(Js.round(v * 10) / 10.0);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !shown()) return;
        Font font = mc.font;
        StatSheet s = ClientRpgState.sheet();
        int x = getX();
        int y = getY();
        int right = x + W - 9;
        RpgUi.panel(g, x, y, W, H);

        // 이름 · 레벨 · 경험치
        int level = Math.max(ClientRpgState.lvmax(), p.experienceLevel);
        String lv = "Lv " + level;
        int lvW = font.width(lv);
        String name = p.getName().getString();
        int nameMax = W - 18 - lvW - 4;
        if (font.width(name) > nameMax) name = font.plainSubstrByWidth(name, nameMax - font.width("…")) + "…";
        g.text(font, name, x + 9, y + 7, 0xFFFFFFFF, true);
        g.text(font, lv, right - lvW, y + 7, RpgUi.GOLD_TEXT, true);
        RpgUi.bar(g, x + 9, y + 18, W - 18, 2, p.experienceProgress, 0x78DC5A);

        // 체력
        float hp = p.getHealth();
        float max = p.getMaxHealth();
        g.text(font, "체력", x + 9, y + 23, 0xFFFF7878, true);
        String hpText = Hp.hpNumber(p.isDeadOrDying(), hp) + " / " + Js.str(Js.round(max * 10) / 10.0);
        g.text(font, hpText, right - font.width(hpText), y + 23, 0xFFFFFFFF, true);
        RpgUi.bar(g, x + 9, y + 33, W - 18, 2, max > 0 ? hp / max : 0, 0xDC3232);
        RpgUi.sep(g, x + 8, x + W - 8, y + 39);

        // 스탯 넷
        int hover = statAt(mx, my);
        for (int i = 0; i < Stats.STAT_KEYS.size(); i++) {
            String key = Stats.STAT_KEYS.get(i);
            int ry = y + STAT_Y + i * STAT_H;
            if (i == hover) g.fill(x + 6, ry, x + W - 6, ry + STAT_H, 0x26FFFFFF);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Mmorpg.id("stats/icon_" + key), x + 9, ry + 1, ICON, ICON);
            g.text(font, BedrockText.toJava(Stats.info(key).str("color") + Stats.info(key).str("name")), x + 22, ry + 2, 0xFFFFFFFF, true);
            String v = String.valueOf(s.stat(key));
            g.text(font, v, right - font.width(v), ry + 2, 0xFFFFFFFF, true);
        }
        RpgUi.sep(g, x + 8, x + W - 8, y + 97);

        // 기준 무기 피해 · 치명타 · 방어
        Weapon w = StatSheet.referenceWeapon(p.getMainHandItem(), p.getOffhandItem(), p.getInventory().getNonEquipmentItems(), s::usable);
        int enh = mmorpg.fabric.gear.WeaponEnhance.reference(p.getMainHandItem(), p.getInventory().getNonEquipmentItems(), w);
        double[] now = s.weaponNow(w, enh);
        String dmg = now == null ? "—" : now[3] > 0 ? num(now[0]) : "0";
        int dmgColor = now == null ? 0xFF8A8A8A : now[3] > 0 ? 0xFFFFFFFF : 0xFFFF5555;
        row(g, font, "피해", dmg, x, y + 100, dmgColor);
        row(g, font, "치명타", now == null ? "—" : num(now[1] * 100) + "%", x, y + 110, now == null ? 0xFF8A8A8A : 0xFFFFFFFF);
        row(g, font, "방어", String.valueOf(p.getArmorValue()), x, y + 120, 0xFFFFFFFF);
        RpgUi.sep(g, x + 8, x + W - 8, y + 130);

        // 골드 · 실버
        icon(g, GOLD, x + 9, y + 133);
        g.text(font, shortAmount(ClientWallet.total(Economy.Kind.GOLD)), x + 21, y + 134, 0xFFFFD25A, true);
        icon(g, SILVER, x + 52, y + 133);
        g.text(font, shortAmount(ClientWallet.total(Economy.Kind.SILVER)), x + 64, y + 134, 0xFFDCDCE6, true);

        // 스탯 창 단추
        boolean overBtn = inButton(mx, my);
        RpgUi.button(g, x + 8, y + BTN_Y, W - 16, BTN_H, overBtn, false);
        int pts = s.point();
        String key = MmorpgClient.statsKey == null ? "K" : MmorpgClient.statsKey.getTranslatedKeyMessage().getString();
        String label = "스탯 창 §7[" + key + "]" + (pts > 0 ? ((System.currentTimeMillis() / 500) % 2 == 0 ? " §e+" : " §6+") + pts : "");
        g.text(font, label, x + W / 2 - font.width(label) / 2, y + BTN_Y + 4, RpgUi.GOLD_TEXT, true);

        // 풀이
        List<Component> tip = null;
        if (hover >= 0) {
            tip = new ArrayList<>();
            for (String l : s.tooltip(Stats.STAT_KEYS.get(hover))) tip.add(Component.literal(BedrockText.toJava(l)));
        } else if (overBtn) {
            tip = List.of(Component.literal("§6§l스탯 창"), Component.literal("§7스탯 찍기 · 스탯 트리 · 장터 · 창고 · 탈것 · 장식 · 뽑기"),
                    Component.literal(pts > 0 ? "§e남은 포인트 " + pts : "§8남은 포인트 없음"));
        } else if (my >= y + 98 && my < y + 130 && mx >= x && mx < x + W) {
            tip = new ArrayList<>();
            tip.add(Component.literal("§6기준 무기 " + (w == null ? "§7없음" : "§f" + BedrockText.toJava(w.name()))));
            if (now != null) {
                tip.add(Component.literal("§7피해 §f" + num(now[0]) + (enh > 0 ? " §6(+" + enh + " 강화)" : "")));
                tip.add(Component.literal("§7치명타 §f" + num(now[1] * 100) + "% §7· 배율 §f×" + num(now[2])));
                if (now[3] <= 0) tip.add(Component.literal("§c요구 조건이 모자라 지금은 피해 0"));
            } else {
                tip.add(Component.literal("§7RPG 무기를 가지고 있으면 실제 피해가 나옵니다"));
            }
            tip.add(Component.literal("§7방어 §f" + p.getArmorValue() + " §8(갑옷)"));
        } else if (my >= y + 131 && my < y + 145 && mx >= x && mx < x + W) {
            tip = List.of(Component.literal(BedrockText.toJava(Economy.amount(Economy.Kind.GOLD, ClientWallet.total(Economy.Kind.GOLD)))),
                    Component.literal(BedrockText.toJava(Economy.amount(Economy.Kind.SILVER, ClientWallet.total(Economy.Kind.SILVER)))),
                    Component.literal("§8골드 = 장터 · 장식 · 창고, 실버 = 상점 · NPC"));
        }
        if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mx, my);
    }

    private static void row(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int valueColor) {
        g.text(font, label, x + 9, y, 0xFFB8B8C8, true);
        g.text(font, value, x + W - 9 - font.width(value), y, valueColor, true);
    }

    /** 동전 그림 (아이템 10 × 10 — 16 의 5/8) */
    private static void icon(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(ICON / 16F, ICON / 16F);
        g.item(stack, 0, 0);
        g.pose().popMatrix();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
