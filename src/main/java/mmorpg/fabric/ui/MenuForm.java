package mmorpg.fabric.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import mmorpg.fabric.BedrockText;
import net.minecraft.server.level.ServerPlayer;

/** 버튼 목록 창 (forms.js MenuForm — 관리자 메뉴: 제목 · 설명 · 버튼 여러 개, 길면 스크롤). show 의 받을 일은 누른 버튼 번호 (닫으면 -1) */
public final class MenuForm {
    private final String title;
    private final String text;
    private final List<FormNet.ChoiceData> buttons = new ArrayList<>();

    public MenuForm(String title) {
        this(title, "");
    }

    public MenuForm(String title, String text) {
        this.title = title == null ? "" : title;
        this.text = text == null ? "" : text;
    }

    public MenuForm button(String text) {
        return button(text, Icon.NONE);
    }

    public MenuForm button(String text, Icon icon) {
        buttons.add(new FormNet.ChoiceData(BedrockText.toJava(text == null ? "" : text), FormNet.IconData.of(icon)));
        return this;
    }

    public int size() {
        return buttons.size();
    }

    /** 누른 버튼 번호를 onSelect 로 (닫으면 -1). 돌려주는 값 = 창 번호 (Forms.pendingId) */
    public int show(ServerPlayer sp, IntConsumer onSelect) {
        int id = Forms.nextId();
        int n = buttons.size();
        Forms.open(sp, id, new FormNet.MenuPayload(id, BedrockText.toJava(title), BedrockText.toJava(text), List.copyOf(buttons)), r -> {
            int sel = r.selection();
            onSelect.accept(sel >= n ? -1 : sel);
        });
        return id;
    }
}
