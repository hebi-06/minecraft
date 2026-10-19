package mmorpg.fabric.ui;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * 창 패킷: 서버 → 클라이언트 (상자 창 · 대화창 · 버튼 목록 창 · 입력창 · 닫기), 클라이언트 → 서버 (누른 칸 · 입력 값).
 * 글자는 서버가 자바 서식으로 바꿔 보냄 (BedrockText). 창마다 번호 (id) — 늦게 온 응답은 버림.
 */
public final class FormNet {
    private FormNet() {}

    private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list) {
        buf.writeVarInt(list.size());
        for (String s : list) buf.writeUtf(s);
    }

    private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
        int n = Math.min(512, buf.readVarInt());
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }

    /** 칸 · 선택지의 그림 */
    public record IconData(ItemStack item, String sprite) {
        static IconData of(Icon i) {
            return i == null ? new IconData(ItemStack.EMPTY, "") : new IconData(i.item(), i.sprite());
        }

        void write(RegistryFriendlyByteBuf buf) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
            buf.writeUtf(sprite);
        }

        static IconData read(RegistryFriendlyByteBuf buf) {
            return new IconData(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf());
        }

        public boolean isEmpty() {
            return item.isEmpty() && sprite.isEmpty();
        }
    }

    /** 상자 창 칸 (kind: Slot.NONE · COUNT · PRICE · DECO · BIG (0.21.0 진열 칸), 빈 칸은 empty) */
    public record SlotData(boolean empty, IconData icon, String name, List<String> lore, byte kind, int num) {
        static final SlotData EMPTY = new SlotData(true, new IconData(ItemStack.EMPTY, ""), "", List.of(), Slot.NONE, 0);

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(empty);
            if (empty) return;
            icon.write(buf);
            buf.writeUtf(name);
            writeStrings(buf, lore);
            buf.writeByte(kind);
            buf.writeVarInt(num);
        }

        static SlotData read(RegistryFriendlyByteBuf buf) {
            if (buf.readBoolean()) return EMPTY;
            return new SlotData(false, IconData.read(buf), buf.readUtf(), readStrings(buf), buf.readByte(), buf.readVarInt());
        }
    }

    /** 대화창 · 버튼 목록 창의 선택지 */
    public record ChoiceData(String text, IconData icon) {
        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(text);
            icon.write(buf);
        }

        static ChoiceData read(RegistryFriendlyByteBuf buf) {
            return new ChoiceData(buf.readUtf(), IconData.read(buf));
        }
    }

    /**
     * 입력창 칸 (type: HEADER · LABEL · DIVIDER · TEXT · DROPDOWN · SLIDER · TOGGLE). value: 글자 / 고른 번호 / 숫자 / "true"·"false"
     */
    public record ControlData(byte type, String label, String placeholder, String value, List<String> options, int min, int max) {
        public static final byte HEADER = 0;
        public static final byte LABEL = 1;
        public static final byte DIVIDER = 2;
        public static final byte TEXT = 3;
        public static final byte DROPDOWN = 4;
        public static final byte SLIDER = 5;
        public static final byte TOGGLE = 6;

        public boolean isInput() {
            return type >= TEXT;
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(type);
            buf.writeUtf(label);
            buf.writeUtf(placeholder);
            buf.writeUtf(value);
            writeStrings(buf, options);
            buf.writeVarInt(min);
            buf.writeVarInt(max);
        }

        static ControlData read(RegistryFriendlyByteBuf buf) {
            return new ControlData(buf.readByte(), buf.readUtf(), buf.readUtf(), buf.readUtf(), readStrings(buf), buf.readVarInt(), buf.readVarInt());
        }
    }

    /** 상자 창 (9 × 3 칸 + 본문 — chestui.js ChestForm, 0.21.0 칸 수 9 ~ 54 — 줄 = 칸 수 ÷ 9) */
    public record ChestPayload(int id, String title, String body, List<SlotData> slots) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ChestPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("form_chest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChestPayload> CODEC = CustomPacketPayload.codec(ChestPayload::write, ChestPayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(title);
            buf.writeUtf(body);
            buf.writeVarInt(slots.size());
            for (SlotData s : slots) s.write(buf);
        }

        private static ChestPayload read(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt();
            String title = buf.readUtf();
            String body = buf.readUtf();
            int n = Math.min(54, buf.readVarInt());
            List<SlotData> slots = new ArrayList<>(n);
            for (int i = 0; i < n; i++) slots.add(SlotData.read(buf));
            return new ChestPayload(id, title, body, slots);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 대화창 (화면 아래 — forms.js DialogForm): 말하는 이름 · 대사 · 선택지 */
    public record DialogPayload(int id, String name, String text, List<ChoiceData> choices) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DialogPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("form_dialog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DialogPayload> CODEC = CustomPacketPayload.codec(DialogPayload::write, DialogPayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(name);
            buf.writeUtf(text);
            buf.writeVarInt(choices.size());
            for (ChoiceData c : choices) c.write(buf);
        }

        private static DialogPayload read(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt();
            String name = buf.readUtf();
            String text = buf.readUtf();
            int n = Math.min(64, buf.readVarInt());
            List<ChoiceData> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(ChoiceData.read(buf));
            return new DialogPayload(id, name, text, list);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 버튼 목록 창 (관리자 메뉴 — forms.js MenuForm, 길면 스크롤) */
    public record MenuPayload(int id, String title, String text, List<ChoiceData> buttons) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MenuPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("form_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MenuPayload> CODEC = CustomPacketPayload.codec(MenuPayload::write, MenuPayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(title);
            buf.writeUtf(text);
            buf.writeVarInt(buttons.size());
            for (ChoiceData c : buttons) c.write(buf);
        }

        private static MenuPayload read(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt();
            String title = buf.readUtf();
            String text = buf.readUtf();
            int n = Math.min(256, buf.readVarInt());
            List<ChoiceData> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(ChoiceData.read(buf));
            return new MenuPayload(id, title, text, list);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 입력창 (forms.js ModalBuilder — 글자 · 고르기 · 밀대 · 켜고 끄기) */
    public record ModalPayload(int id, String title, String submit, List<ControlData> controls) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ModalPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("form_modal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ModalPayload> CODEC = CustomPacketPayload.codec(ModalPayload::write, ModalPayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(title);
            buf.writeUtf(submit);
            buf.writeVarInt(controls.size());
            for (ControlData c : controls) c.write(buf);
        }

        private static ModalPayload read(RegistryFriendlyByteBuf buf) {
            int id = buf.readVarInt();
            String title = buf.readUtf();
            String submit = buf.readUtf();
            int n = Math.min(128, buf.readVarInt());
            List<ControlData> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(ControlData.read(buf));
            return new ModalPayload(id, title, submit, list);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 클라이언트: 이 창의 흐름이 끝남 (다음 창 없음 — 기다리던 창을 닫음) */
    public record ClosePayload(int id) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ClosePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("form_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClosePayload> CODEC = CustomPacketPayload.codec(ClosePayload::write, ClosePayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(id);
        }

        private static ClosePayload read(RegistryFriendlyByteBuf buf) {
            return new ClosePayload(buf.readVarInt());
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: 누른 칸 · 선택지 번호 (닫으면 -1), 입력창이면 입력 칸의 값 (순서대로) */
    public record ResponsePayload(int id, int selection, List<String> values) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ResponsePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("form_response"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResponsePayload> CODEC = CustomPacketPayload.codec(ResponsePayload::write, ResponsePayload::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarInt(selection);
            writeStrings(buf, values);
        }

        private static ResponsePayload read(RegistryFriendlyByteBuf buf) {
            return new ResponsePayload(buf.readVarInt(), buf.readVarInt(), readStrings(buf));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ChestPayload.TYPE, ChestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DialogPayload.TYPE, DialogPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MenuPayload.TYPE, MenuPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ModalPayload.TYPE, ModalPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClosePayload.TYPE, ClosePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ResponsePayload.TYPE, ResponsePayload.CODEC);
    }
}
