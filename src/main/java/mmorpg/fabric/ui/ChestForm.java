package mmorpg.fabric.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import mmorpg.fabric.BedrockText;
import net.minecraft.server.level.ServerPlayer;

/**
 * 상자 모양 창 (chestui.js ChestForm): 9 × 3 칸 (0.21.0 — 9 × 1 ~ 6 줄, 유저 장터는 큰 상자 9 × 6) + 아래 본문. 칸에는 그림 · 이름 · 설명 ·
 * 보유 수 (흰 숫자) · 가격 (초록 숫자) · 장식 · 진열 (3 × 3 칸 크기). 줄 수는 보내는 칸 수로 (클라이언트가 칸 수 ÷ 9).
 * show 의 받을 일은 누른 칸 번호 (0 ~ 칸 수 − 1, 닫으면 -1).
 */
public final class ChestForm {
    /** 기본 창 (작은 상자 9 × 3) 의 칸 수 */
    public static final int SIZE = 27;
    /** 큰 상자 (9 × 6) 의 줄 수 */
    public static final int BIG_ROWS = 6;

    private final String title;
    private final Slot[] slots;
    private String body = "";

    public ChestForm(String title) {
        this(title, 3);
    }

    /** rows 줄 (1 ~ 6) 창 */
    public ChestForm(String title, int rows) {
        this.title = title == null ? "" : title;
        this.slots = new Slot[Math.max(1, Math.min(6, rows)) * 9];
    }

    /** 칸 수 (줄 × 9) */
    public int size() {
        return slots.length;
    }

    public ChestForm set(int index, Slot slot) {
        if (index >= 0 && index < slots.length) slots[index] = slot;
        return this;
    }

    public Slot get(int index) {
        return index >= 0 && index < slots.length ? slots[index] : null;
    }

    /** 빈 칸을 모두 장식으로 (chestui.js fillEmpty) */
    public ChestForm fillEmpty() {
        return fillEmpty(Slot.PANE);
    }

    public ChestForm fillEmpty(Slot slot) {
        return fillRange(0, slots.length, slot);
    }

    /** from ~ to − 1 칸 중 빈 칸만 slot 으로 (나머지 빈 칸은 그림 없는 빈 칸 — 상자의 빈 칸처럼) */
    public ChestForm fillRange(int from, int to, Slot slot) {
        for (int i = Math.max(0, from); i < Math.min(slots.length, to); i++) if (slots[i] == null) slots[i] = slot;
        return this;
    }

    public ChestForm body(String text) {
        this.body = text == null ? "" : text;
        return this;
    }

    /** 창을 보여 주고 누른 칸 번호를 onSelect 로 (닫으면 -1) */
    public void show(ServerPlayer sp, IntConsumer onSelect) {
        int size = slots.length;
        List<FormNet.SlotData> list = new ArrayList<>(size);
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
            onSelect.accept(sel >= size ? -1 : sel); // 장식 · 빈 칸은 클라이언트가 누르지 못하게 함 (와도 흐름이 새로 그림)
        });
    }
}
