package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Economy;
import mmorpg.core.Storage;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.mixin.AbstractContainerScreenAccessor;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.storage.StorageNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 0.22.0 엔더 창고 상자 탭 (큰 상자 창 왼쪽 — 위에서부터 1 ~ 5번 상자): 열린 상자 = 엔더 상자 그림 · 든 칸 막대 (초록 → 노랑 → 빨강),
 * 지금 상자 = 밝은 탭이 창에 붙음, 다음에 살 상자 = 그 화폐의 동전 + 작은 자물쇠 (두 번 누르면 삼 — 첫 번째 누름 뒤 3초 동안 노란 테가 깜빡임),
 * 그 뒤 상자 = 자물쇠 (앞 상자부터). 누르면 서버가 같은 창의 칸을 그 상자로 바꿈 (든 아이템은 그대로 — 다른 상자로 옮기기).
 * 창 제목 줄 오른쪽에 "2번 상자 · 3/5". 마우스를 올리면 풀이 (든 칸 · 값 · 보유 · 누르면 할 일).
 */
public final class StorageTabs extends AbstractWidget {
    static final int TAB_W = 26;
    static final int TAB_H = 25;
    static final int GAP = 2;
    /** 지금 상자의 탭이 창 테두리를 덮는 폭 (창에 붙어 보이게) */
    static final int JOIN = 4;
    /** 두 번 눌러 사기: 첫 누름 뒤 이만큼 (클라이언트 틱 — 3초) 안에 다시 누르면 삼 */
    static final int ARM_TICKS = 60;
    static final Identifier LOCK = Mmorpg.id("ui/lock");

    private final ContainerScreen screen;
    private final int left;
    private final int top;
    private final int imageW;
    /** 두 번 눌러 사기: 첫 번째로 누른 탭 · 그 때 */
    private static int armed = -1;
    private static int armedAt;

    StorageTabs(ContainerScreen screen) {
        super(0, 0, TAB_W + JOIN, 9 * (TAB_H + GAP), Component.literal("엔더 창고"));
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        this.screen = screen;
        this.left = a.mmorpg$leftPos();
        this.top = a.mmorpg$topPos();
        this.imageW = a.mmorpg$imageWidth();
        setX(left - TAB_W);
        setY(top + 4);
    }

    /** 시험용: 이 창에 서버 상태가 와서 탭이 보이는지 */
    public boolean shown() {
        return visible && state() != null;
    }

    /** 시험용: 두 번 눌러 사기를 기다리는 탭 (없으면 -1) */
    public static int armedTab() {
        return armed >= 0 && clock() - armedAt < ARM_TICKS ? armed : -1;
    }

    /** 클라이언트 틱 시계 (내 플레이어의 틱 수 — 창이 열린 동안에도 흐름) */
    private static int clock() {
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : p.tickCount;
    }

    private StorageNet.StatePayload state() {
        return ClientStorage.stateFor(screen.getMenu());
    }

    /** 탭 i 의 위 */
    private int tabY(int i) {
        return getY() + i * (TAB_H + GAP);
    }

    /** 마우스 아래 탭 (없으면 -1) */
    private int tabAt(StorageNet.StatePayload s, double mx, double my) {
        if (s == null || mx < getX() || mx >= left) return -1;
        for (int i = 0; i < s.chests(); i++) if (my >= tabY(i) && my < tabY(i) + TAB_H) return i;
        return -1;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible && tabAt(state(), mx, my) >= 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        StorageNet.StatePayload s = state();
        int tab = visible ? tabAt(s, event.x(), event.y()) : -1;
        if (tab < 0) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        click(s, tab);
        return true;
    }

    private static void click(StorageNet.StatePayload s, int tab) {
        if (tab == s.page()) return;
        if (tab < s.unlocked()) {
            armed = -1;
            ClientStorage.send("page", s.containerId(), tab);
            return;
        }
        if (tab == s.unlocked()) {
            int now = clock();
            if (armed == tab && now - armedAt < ARM_TICKS) {
                armed = -1;
                ClientStorage.send("buy", s.containerId(), tab);
            } else {
                armed = tab;
                armedAt = now;
            }
        }
        // 그 뒤 상자: 앞 상자부터 (풀이에 안내)
    }

    /** 시험용: 탭 i 의 가운데 (화면 좌표) */
    public int[] tabCenter(int i) {
        return new int[] {getX() + TAB_W / 2, tabY(i) + TAB_H / 2};
    }

    /** 지금 상자의 든 묶음 수 (창의 칸에서 바로 — 서버 상태보다 새로움) */
    private int liveStacks() {
        ChestMenu m = screen.getMenu();
        int n = 0;
        for (int k = 0; k < Storage.SIZE && k < m.slots.size(); k++) if (!m.getSlot(k).getItem().isEmpty()) n++;
        return n;
    }

    private static Economy.Kind kindOf(String price) {
        int c = price.indexOf(':');
        return c < 0 ? null : Economy.Kind.of(price.substring(0, c));
    }

    private static long amountOf(String price) {
        int c = price.indexOf(':');
        try {
            return c < 0 ? 0 : Long.parseLong(price.substring(c + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        StorageNet.StatePayload s = state();
        if (s == null) return;
        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis();
        if (armed >= 0 && (armed != s.unlocked() || clock() - armedAt >= ARM_TICKS)) armed = -1;
        int hover = tabAt(s, mx, my);
        for (int i = 0; i < s.chests(); i++) {
            boolean current = i == s.page();
            boolean open = i < s.unlocked();
            boolean next = i == s.unlocked();
            int x = getX() + (current ? 0 : 3);
            int y = tabY(i);
            int w = current ? TAB_W + JOIN : TAB_W - 3;
            int body = current ? 0xFFC6C6C6 : open ? (i == hover ? 0xFFB4B4B4 : 0xFFA2A2A2) : next ? (i == hover ? 0xFF9C9C9C : 0xFF8E8E8E) : 0xFF6E6E6E;
            int light = current ? 0xFFFFFFFF : open ? 0xFFD6D6D6 : 0xFFAAAAAA;
            int dark = current ? 0xFF555555 : 0xFF4A4A4A;
            // 바탕 · 테두리 (지금 상자는 오른쪽 테두리 없이 창에 붙음)
            g.fill(x, y, x + w, y + TAB_H, 0xFF000000);
            g.fill(x + 1, y + 1, x + w - (current ? 0 : 1), y + TAB_H - 1, body);
            g.fill(x + 1, y + 1, x + w - (current ? 0 : 2), y + 2, light);
            g.fill(x + 1, y + 1, x + 2, y + TAB_H - 2, light);
            g.fill(x + 2, y + TAB_H - 2, x + w - (current ? 0 : 1), y + TAB_H - 1, dark);
            if (!current) g.fill(x + w - 2, y + 2, x + w - 1, y + TAB_H - 1, dark);
            int ix = x + 4 + (current ? 1 : 0);
            int iy = y + 3;
            if (open) {
                g.item(new ItemStack(Items.ENDER_CHEST), ix, iy);
                // 든 칸 막대
                int n = current ? liveStacks() : i < s.stacks().size() ? s.stacks().get(i) : 0;
                int bw = w - 8 - (current ? JOIN : 0);
                int fillW = Math.round(bw * Math.min(1F, n / (float) Storage.SIZE));
                int color = n >= Storage.SIZE ? 0xFFE05050 : n >= Storage.SIZE * 3 / 4 ? 0xFFE0C040 : 0xFF55C055;
                g.fill(x + 4, y + TAB_H - 5, x + 4 + bw, y + TAB_H - 3, 0xFF3A3A3A);
                if (fillW > 0) g.fill(x + 4, y + TAB_H - 5, x + 4 + fillW, y + TAB_H - 3, color);
            } else if (next) {
                Economy.Kind k = kindOf(i < s.prices().size() ? s.prices().get(i) : "");
                if (k != null) g.item(new ItemStack(EconomyItems.coin(k)), ix, iy);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK, ix + 9, iy + 9, 9, 9);
                if (armed == i) {
                    // 두 번째 누름을 기다림: 노란 테 깜빡임
                    int a = (int) (0x90 + 0x6F * Math.abs(Math.sin(now / 160.0)));
                    int c = (a << 24) | 0xFFD24A;
                    g.fill(x, y, x + w, y + 1, c);
                    g.fill(x, y + TAB_H - 1, x + w, y + TAB_H, c);
                    g.fill(x, y, x + 1, y + TAB_H, c);
                    g.fill(x + w - 1, y, x + w, y + TAB_H, c);
                }
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK, ix, iy, 16, 16, 0xB0FFFFFF);
            }
            // 상자 번호 (왼쪽 아래)
            g.text(font, String.valueOf(i + 1), x + 3, y + TAB_H - 13, open ? 0xFFFFFFFF : 0xFFBBBBBB, true);
        }
        // 창 제목 줄 오른쪽: "2번 상자 · 3/5"
        String label = Storage.label(s.page() + 1) + " · " + s.unlocked() + "/" + s.chests();
        g.text(font, label, left + imageW - 8 - font.width(label), top + 6, 0xFF404040, false);
        if (hover >= 0) g.setComponentTooltipForNextFrame(font, tooltip(s, hover), mx, my);
    }

    /** 탭 풀이 */
    private List<Component> tooltip(StorageNet.StatePayload s, int i) {
        List<Component> out = new ArrayList<>();
        int chest = i + 1;
        if (i < s.unlocked()) {
            int n = i == s.page() ? liveStacks() : i < s.stacks().size() ? s.stacks().get(i) : 0;
            out.add(Component.literal("§d" + Storage.label(chest) + (i == s.page() ? " §7(보는 중)" : "")));
            out.add(Component.literal("§7든 칸 §f" + n + " §7/ " + Storage.SIZE));
            if (i != s.page()) {
                boolean carrying = !screen.getMenu().getCarried().isEmpty();
                out.add(Component.literal(carrying ? "§e누르면 든 아이템과 함께 이 상자로" : "§e누르면 이 상자로"));
            }
            return out;
        }
        String price = i < s.prices().size() ? s.prices().get(i) : "";
        Economy.Kind k = kindOf(price);
        long amount = amountOf(price);
        out.add(Component.literal("§7" + Storage.label(chest) + " §8— 잠김"));
        if (k == null) return out;
        String cost = Economy.amount(k, amount);
        if (i == s.unlocked()) {
            long have = ClientWallet.total(k);
            out.add(Component.literal("§e열기: " + cost));
            out.add(Component.literal("§7보유: " + Economy.amount(k, have)));
            if (have < amount) out.add(Component.literal("§c" + k.label() + "가 모자랍니다"));
            else if (armed == i) out.add(Component.literal("§e§l한 번 더 누르면 삽니다"));
            else out.add(Component.literal("§a두 번 누르면 삽니다 §8(" + Storage.SIZE + "칸 더)"));
        } else {
            out.add(Component.literal("§7먼저 " + Storage.label(s.unlocked() + 1) + "를 여세요"));
            out.add(Component.literal("§8값: " + cost));
        }
        return out;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
