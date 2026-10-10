package mmorpg.fabric.client.form;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.ui.FormNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * 서버 창 (상자 창 · 대화창 · 버튼 목록 · 입력창) 의 바탕: 누르면 응답을 보내고 다음 창 (또는 닫기) 을 기다림 — 다음 창이 오면 그 자리에서 바뀜.
 * 닫으면 (ESC · 닫기 단추 · 다른 화면) -1 을 보냄. 게임은 멈추지 않음.
 */
public abstract class FormScreen extends Screen {
    final int id;
    /** 응답을 보냄 (다음 창을 기다리는 중) */
    boolean responded;
    /** 다음 창으로 바뀜 (닫기 응답을 보내지 않음) */
    boolean replaced;

    protected FormScreen(int id, Component title) {
        super(title);
        this.id = id;
    }

    /** 창 그림 (assets/mmorpg/textures/gui/sprites/ui/이름) */
    static Identifier ui(String name) {
        return Mmorpg.id("ui/" + name);
    }

    static void sprite(GuiGraphicsExtractor g, String name, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ui(name), x, y, w, h);
    }

    /** 칸 · 선택지 그림 (아이템 또는 창 그림) */
    static void icon(GuiGraphicsExtractor g, FormNet.IconData icon, int x, int y, int size) {
        if (icon == null) return;
        if (!icon.item().isEmpty()) {
            if (size == 16) g.item(icon.item(), x, y);
            else {
                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().scale(size / 16F, size / 16F);
                g.item(icon.item(), 0, 0);
                g.pose().popMatrix();
            }
        } else if (!icon.sprite().isEmpty()) {
            sprite(g, icon.sprite(), x, y, size, size);
        }
    }

    /** 응답 (누른 번호 · 입력 값) — 한 번만 */
    protected void respond(int selection, List<String> values) {
        if (responded) return;
        responded = true;
        if (selection >= 0) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        ClientPlayNetworking.send(new FormNet.ResponsePayload(id, selection, values == null ? List.of() : values));
    }

    protected void respond(int selection) {
        respond(selection, List.of());
    }

    @Override
    public void removed() {
        if (!responded && !replaced) {
            responded = true;
            ClientPlayNetworking.send(new FormNet.ResponsePayload(id, -1, List.of()));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 뒤의 세상: 흐리지 않고 살짝 어둡게만 (기본 화면 배경은 흐림 + 짙은 어둠 — 애드온 창처럼 NPC · 피해 숫자 · 파티클이 비쳐 보이게,
     * 바닐라 상자 창도 흐리지 않음)
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        g.fillGradient(0, 0, width, height, 0x58080A10, 0x88080A10);
    }

    /**
     * 밝은 회색 바탕 (상자 창) 의 글자: 애드온이 덧붙임에 쓰는 §7 (밝은 회색 #AAAAAA) 은 바탕 (#C6C6C6) 과 거의 같아 읽히지 않으므로 §8 (짙은 회색) 로
     */
    static String onLight(String s) {
        return s == null ? "" : s.replace("§7", "§8");
    }

    /** 세로 스크롤 막대 (오른쪽 가는 금색 줄) — 보이는 높이 view, 전체 view + max, 지금 scroll */
    static void scrollbar(GuiGraphicsExtractor g, int x, int top, int view, int scroll, int max) {
        if (max <= 0) return;
        int barH = Math.max(10, view * view / (view + max));
        int barY = top + (view - barH) * scroll / max;
        g.fill(x, top, x + 2, top + view, 0x30E0C070);
        g.fill(x, barY, x + 2, barY + barH, 0xC0E0C070);
    }

    /** 글자를 폭에 맞춰 줄로 (줄바꿈 "\n" 은 그대로 · 긴 줄은 접음) */
    static List<FormattedCharSequence> wrap(Font font, String text, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        String carry = "";
        for (String line : text.split("\n", -1)) {
            String full = carry + line;
            if (line.isEmpty()) {
                out.add(FormattedCharSequence.EMPTY);
            } else {
                out.addAll(font.split(Component.literal(full), width));
            }
            carry = lastFormat(full);
        }
        return out;
    }

    /** 줄 끝에 살아 있는 색 · 서식 (다음 줄로 이어 감 — 베드락 글자처럼) */
    private static String lastFormat(String s) {
        String color = "";
        StringBuilder style = new StringBuilder();
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            char c = Character.toLowerCase(s.charAt(i + 1));
            if ("0123456789abcdef".indexOf(c) >= 0) {
                color = "§" + c;
                style.setLength(0);
            } else if (c == 'r') {
                color = "";
                style.setLength(0);
            } else if ("klmno".indexOf(c) >= 0) {
                style.append('§').append(c);
            }
            i++;
        }
        return color + style;
    }

    /** 폭을 넘으면 잘라 "..." (서식 글자는 폭에 넣지 않음 — forms.js clipText) */
    static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        int limit = width - font.width("...");
        int cut = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i - 1) == '§') continue;
            if (font.width(text.substring(0, i)) > limit) break;
            cut = i;
        }
        return text.substring(0, cut) + "§r...";
    }

    /** 닫기 단추 (오른쪽 위 작은 X) */
    static void closeButton(GuiGraphicsExtractor g, int x, int y, boolean hover) {
        g.fill(x, y, x + 11, y + 11, hover ? 0xFFB33A3A : 0xFF6B6B6B);
        g.fill(x + 1, y + 1, x + 10, y + 10, hover ? 0xFFE05050 : 0xFF8E8E8E);
        int c = 0xFFFFFFFF;
        for (int i = 0; i < 5; i++) {
            g.fill(x + 3 + i, y + 3 + i, x + 4 + i, y + 4 + i, c);
            g.fill(x + 7 - i, y + 3 + i, x + 8 - i, y + 4 + i, c);
        }
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
