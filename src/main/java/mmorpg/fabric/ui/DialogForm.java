package mmorpg.fabric.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import mmorpg.fabric.BedrockText;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/**
 * 대화창 (forms.js DialogForm): 화면 아래 금테 대화 상자 — 말하는 이름표 · 대사 · 선택지 (그림 + 글자, 한 줄에 넘치면 … — 클라이언트가 자름).
 * show 의 받을 일은 누른 선택지 번호 (닫으면 -1). 여러 장 대사 (showPages) · 저장 문자열 ↔ 장 (toPages · toEditText) 도 여기.
 */
public final class DialogForm {
    /** forms.js ICON */
    public static final Icon NEW = Icon.sprite("q_new");
    public static final Icon ACTIVE = Icon.sprite("q_active");
    public static final Icon READY = Icon.sprite("q_ready");
    public static final Icon NEXT = Icon.sprite("icon_next");
    public static final Icon OK = Icon.sprite("icon_check");
    public static final Icon BYE = Icon.sprite("icon_cross");
    public static final Icon SHOP = Icon.item(Items.EMERALD);

    private final String name;
    private final String text;
    private final List<FormNet.ChoiceData> choices = new ArrayList<>();

    public DialogForm(String name, String text) {
        this.name = name == null ? "" : name;
        this.text = text == null ? "" : text;
    }

    public DialogForm choice(String text) {
        return choice(text, Icon.NONE);
    }

    public DialogForm choice(String text, Icon icon) {
        choices.add(new FormNet.ChoiceData(BedrockText.toJava(text == null ? "" : text), FormNet.IconData.of(icon)));
        return this;
    }

    public int size() {
        return choices.size();
    }

    /** 누른 선택지 번호를 onSelect 로 (닫으면 -1) */
    public void show(ServerPlayer sp, IntConsumer onSelect) {
        int id = Forms.nextId();
        int n = choices.size();
        Forms.open(sp, id, new FormNet.DialogPayload(id, BedrockText.toJava(name), BedrockText.toJava(text), List.copyOf(choices)), r -> {
            int sel = r.selection();
            onSelect.accept(sel >= n ? -1 : sel);
        });
    }

    /** 저장된 대사를 장으로 나눔 (forms.js toPages): "||" = 다음 장, "|" = 줄바꿈 */
    public static List<String> toPages(String text) {
        List<String> out = new ArrayList<>();
        for (String p : (text == null ? "" : text).split("\\|\\|", -1)) {
            String page = String.join("\n", Arrays.asList(p.split("\\|", -1))).trim();
            if (!page.isEmpty()) out.add(page);
        }
        return out;
    }

    /** 저장된 대사를 입력창에 넣을 때 (forms.js toEditText: 줄바꿈 → |) */
    public static String toEditText(String text) {
        return String.join("|", (text == null ? "" : text).split("\n", -1));
    }

    /**
     * 여러 장 대사를 "다음" 으로 넘겨 보여 주고 마지막 장을 then 으로 (forms.js showPages — 중간에 닫으면 null). 대사가 비면 fallback 한 장.
     * 마지막 장은 보여 주지 않고 돌려줌 (부르는 쪽이 선택지와 함께 보여 줌)
     */
    public static void showPages(ServerPlayer sp, String name, String text, String fallback, Consumer<String> then) {
        List<String> pages = toPages(text);
        if (pages.isEmpty()) pages.add(fallback == null ? "" : fallback);
        page(sp, name, pages, 0, then);
    }

    private static void page(ServerPlayer sp, String name, List<String> pages, int i, Consumer<String> then) {
        if (i >= pages.size() - 1) {
            then.accept(pages.get(pages.size() - 1));
            return;
        }
        new DialogForm(name, pages.get(i)).choice("§f다음", NEXT).show(sp, sel -> {
            if (sel != 0) then.accept(null);
            else page(sp, name, pages, i + 1, then);
        });
    }
}
