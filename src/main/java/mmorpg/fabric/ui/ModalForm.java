package mmorpg.fabric.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.fabric.BedrockText;
import net.minecraft.server.level.ServerPlayer;

/**
 * 입력창 (forms.js ModalBuilder): 글자 칸 · 고르기 · 밀대 · 켜고 끄기 + 제목 줄 · 설명 줄 · 구분선. 값은 붙인 이름으로 돌려줌 —
 * 글자 = String, 고르기 = Integer (번호), 밀대 = Integer, 켜고 끄기 = Boolean. 닫으면 null.
 */
public final class ModalForm {
    private record Key(String key, byte type, int min, int max, int options) {}

    private final String title;
    private final List<FormNet.ControlData> controls = new ArrayList<>();
    private final List<Key> keys = new ArrayList<>();
    private String submit = "확인";

    public ModalForm(String title) {
        this.title = title == null ? "" : title;
    }

    private static String java(String s) {
        return BedrockText.toJava(s == null ? "" : s);
    }

    public ModalForm header(String text) {
        controls.add(new FormNet.ControlData(FormNet.ControlData.HEADER, java(text), "", "", List.of(), 0, 0));
        return this;
    }

    public ModalForm label(String text) {
        controls.add(new FormNet.ControlData(FormNet.ControlData.LABEL, java(text), "", "", List.of(), 0, 0));
        return this;
    }

    public ModalForm divider() {
        controls.add(new FormNet.ControlData(FormNet.ControlData.DIVIDER, "", "", "", List.of(), 0, 0));
        return this;
    }

    /** 글자 칸 (value 는 처음 들어 있는 글자 — 서식 그대로) */
    public ModalForm text(String key, String label, String placeholder, String value) {
        controls.add(new FormNet.ControlData(FormNet.ControlData.TEXT, java(label), java(placeholder), value == null ? "" : value, List.of(), 0, 0));
        keys.add(new Key(key, FormNet.ControlData.TEXT, 0, 0, 0));
        return this;
    }

    /** 고르기 (index = 처음 고른 번호) */
    public ModalForm dropdown(String key, String label, List<String> items, int index) {
        List<String> list = new ArrayList<>();
        for (String it : items) list.add(java(it));
        if (list.isEmpty()) list.add("-");
        int i = Math.max(0, Math.min(list.size() - 1, index));
        controls.add(new FormNet.ControlData(FormNet.ControlData.DROPDOWN, java(label), "", String.valueOf(i), list, 0, list.size() - 1));
        keys.add(new Key(key, FormNet.ControlData.DROPDOWN, 0, list.size() - 1, list.size()));
        return this;
    }

    /** 밀대 (min ~ max, 1 씩) */
    public ModalForm slider(String key, String label, int min, int max, int value) {
        int v = Math.max(min, Math.min(max, value));
        controls.add(new FormNet.ControlData(FormNet.ControlData.SLIDER, java(label), "", String.valueOf(v), List.of(), min, max));
        keys.add(new Key(key, FormNet.ControlData.SLIDER, min, max, 0));
        return this;
    }

    public ModalForm toggle(String key, String label, boolean value) {
        controls.add(new FormNet.ControlData(FormNet.ControlData.TOGGLE, java(label), "", String.valueOf(value), List.of(), 0, 1));
        keys.add(new Key(key, FormNet.ControlData.TOGGLE, 0, 1, 0));
        return this;
    }

    public ModalForm submit(String text) {
        this.submit = text == null ? "확인" : text;
        return this;
    }

    /** 값 (이름 → 값) 을 onSubmit 으로 (닫으면 null) */
    public void show(ServerPlayer sp, Consumer<Map<String, Object>> onSubmit) {
        int id = Forms.nextId();
        Forms.open(sp, id, new FormNet.ModalPayload(id, java(title), java(submit), List.copyOf(controls)), r -> {
            if (r.selection() < 0) {
                onSubmit.accept(null);
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            List<String> vals = r.values();
            for (int i = 0; i < keys.size(); i++) {
                Key k = keys.get(i);
                String v = i < vals.size() ? vals.get(i) : "";
                out.put(k.key(), switch (k.type()) {
                    case FormNet.ControlData.TEXT -> v.length() > 600 ? v.substring(0, 600) : v;
                    case FormNet.ControlData.TOGGLE -> Boolean.parseBoolean(v);
                    default -> clampInt(v, k.min(), k.max());
                });
            }
            onSubmit.accept(out);
        });
    }

    private static int clampInt(String v, int min, int max) {
        int n;
        try {
            n = (int) Math.round(Double.parseDouble(v.trim()));
        } catch (NumberFormatException e) {
            n = min;
        }
        return Math.max(min, Math.min(max, n));
    }
}
