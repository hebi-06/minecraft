package mmorpg.fabric.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import mmorpg.fabric.BedrockText;
import net.minecraft.server.level.ServerPlayer;

/**
 * 상자 모양 창 (chestui.js ChestForm): 9 × 3 칸 + 아래 본문. 칸에는 그림 · 이름 · 설명 · 보유 수 (흰 숫자) · 가격 (초록 숫자) · 장식.
 * show 의 받을 일은 누른 칸 번호 (0 ~ 26, 닫으면 -1).
 */
public final class ChestForm {
    public static final int SIZE = 27;

    private final String title;
    private final Slot[] slots = new Slot[SIZE];
    private String body = "";

    public ChestForm(String title) {
        this.title = title == null ? "" : title;
    }

    public ChestForm set(int index, Slot slot) {
        if (index >= 0 && index < SIZE) slots[index] = slot;
        return this;
    }

    public Slot get(int index) {
        return index >= 0 && index < SIZE ? slots[index] : null;
    }

    /** 빈 칸을 모두 장식으로 (chestui.js fillEmpty) */
    public ChestForm fillEmpty() {
        return fillEmpty(Slot.PANE);
    }

    public ChestForm fillEmpty(Slot slot) {
        for (int i = 0; i < SIZE; i++) if (slots[i] == null) slots[i] = slot;
        return this;
    }

    public ChestForm body(String text) {
        this.body = text == null ? "" : text;
        return this;
    }

    /** 창을 보여 주고 누른 칸 번호를 onSelect 로 (닫으면 -1) */
    public void show(ServerPlayer sp, IntConsumer onSelect) {
        List<FormNet.SlotData> list = new ArrayList<>(SIZE);
        for (Slot s : slots) {
            if (s == null) {
                list.add(FormNet.SlotData.EMPTY);
                continue;
            }
            List<String> lore = new ArrayList<>(s.lore.size());
            for (String l : s.lore) lore.add(BedrockText.toJava(l));
            list.add(new FormNet.SlotData(false, FormNet.IconData.of(s.icon), BedrockText.toJava(s.name), lore, s.kind(), s.number()));
        }
        int id = Forms.nextId();
        Forms.open(sp, id, new FormNet.ChestPayload(id, BedrockText.toJava(title), BedrockText.toJava(body), list), r -> {
            int sel = r.selection();
            onSelect.accept(sel >= SIZE ? -1 : sel); // 장식 · 빈 칸은 클라이언트가 누르지 못하게 함 (와도 흐름이 새로 그림)
        });
    }
}
