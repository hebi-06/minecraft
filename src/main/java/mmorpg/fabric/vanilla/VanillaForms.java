package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.SimpleGuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.InputControl;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 4단계: 모드 없는 클라이언트의 창 (Forms 의 네 가지 창 — 모드 클라이언트는 FormScreen 이 그림). 같은 흐름 (Forms.open · 받을 일) 을 바닐라 화면으로:
 * <ul>
 * <li>상자 창 (ChestPayload) → 바닐라 상자 메뉴 (sgui — 9 × 1 ~ 6, 칸 그림 · 이름 · 설명 · 보유 수 · 가격 줄, 장식 · 진열 칸은 누를 수 없음).
 * 본문 글은 빈 칸 하나에 책 그림 (마우스를 올리면 보임)</li>
 * <li>버튼 목록 · 대화창 (MenuPayload · DialogPayload) → 바닐라 대화창 (1.21.6+ dialog — 제목 · 본문 · 단추 · 닫기)</li>
 * <li>입력창 (ModalPayload) → 같은 대화창의 입력 칸 (글자 · 고르기 · 숫자 막대 · 켜고 끄기) + 확인 단추 (값을 모두 보냄)</li>
 * </ul>
 * 단추 · 확인은 ClickEvent.Custom (mmorpg:form — {id, sel}) 으로 오고 (VanillaDialogMixin), 대화창은 응답을 기다리는 동안 열려 있다가
 * 다음 창이 열리면 바뀌고 흐름이 끝나면 닫힘 (Forms.respond → close).
 */
public final class VanillaForms {
    private VanillaForms() {}

    /** 대화창 단추가 보내는 이름 */
    public static final Identifier CLICK = Mmorpg.id("form");
    /** 대화창 · 단추 너비 */
    private static final int WIDTH = 300;

    private static final Map<UUID, SimpleGui> CHESTS = new ConcurrentHashMap<>();
    private static final Set<UUID> DIALOGS = ConcurrentHashMap.newKeySet();

    static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CHESTS.remove(handler.player.getUUID());
            DIALOGS.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CHESTS.clear();
            DIALOGS.clear();
        });
    }

    /** 이 사람에게 바닐라 창으로 보여 줄 수 있는지 (모드 없는 클라이언트) */
    public static boolean supports(ServerPlayer sp) {
        return sp != null && VanillaClients.isVanilla(sp);
    }

    /** Forms.open: 창 하나를 바닐라 화면으로 */
    public static void show(ServerPlayer sp, CustomPacketPayload payload) {
        try {
            if (payload instanceof FormNet.ChestPayload c) chest(sp, c);
            else if (payload instanceof FormNet.MenuPayload m) buttons(sp, m.id(), m.title(), m.text(), m.buttons());
            else if (payload instanceof FormNet.DialogPayload d) buttons(sp, d.id(), d.name(), d.text(), d.choices());
            else if (payload instanceof FormNet.ModalPayload m) modal(sp, m);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[vanilla] form {}", payload.type().id(), e);
        }
    }

    /** 흐름이 끝남 (Forms.respond · close): 열린 바닐라 창을 닫음 */
    public static void close(ServerPlayer sp) {
        closeChest(sp);
        if (DIALOGS.remove(sp.getUUID()) && sp.connection != null) sp.connection.send(ClientboundClearDialogPacket.INSTANCE);
    }

    private static void closeChest(ServerPlayer sp) {
        SimpleGui gui = CHESTS.remove(sp.getUUID());
        if (gui != null && gui.isOpen()) gui.close();
    }

    // ------------------------------------------------------------ 상자 창

    private static void chest(ServerPlayer sp, FormNet.ChestPayload c) {
        DIALOGS.remove(sp.getUUID()); // 상자 화면이 대화창 (응답 기다림) 을 바꿈
        int size = Math.max(9, Math.min(54, c.slots().size()));
        int rows = (size + 8) / 9;
        MenuType<?> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        int id = c.id();
        UUID uuid = sp.getUUID();
        SimpleGui gui = new SimpleGui(type, sp, false) {
            @Override
            public void onPlayerClose(boolean success) {
                if (CHESTS.get(uuid) != this) return;
                CHESTS.remove(uuid);
                Forms.answerVanilla(sp, id, -1, List.of());
            }
        };
        gui.setTitle(Component.literal(c.title()));
        int cells = rows * 9;
        for (int i = 0; i < c.slots().size() && i < cells; i++) {
            FormNet.SlotData s = c.slots().get(i);
            if (s.empty()) continue;
            boolean clickable = s.kind() != Slot.DECO && s.kind() != Slot.BIG;
            int index = i;
            GuiElement.ClickCallback cb = clickable ? (idx, click, action, g) -> {
                if (CHESTS.get(uuid) == gui) Forms.answerVanilla(sp, id, index, List.of());
            } : GuiElement.EMPTY_CALLBACK;
            gui.setSlot(i, new SimpleGuiElement(icon(s), cb));
        }
        if (!c.body().isEmpty()) {
            int at = -1;
            for (int i = cells - 1; i >= 0; i--) {
                if (i >= c.slots().size() || c.slots().get(i).empty()) {
                    at = i;
                    break;
                }
            }
            if (at >= 0) gui.setSlot(at, new SimpleGuiElement(info(c.body()), GuiElement.EMPTY_CALLBACK));
            else sp.sendSystemMessage(Component.literal(c.body()));
        }
        CHESTS.put(uuid, gui); // 예전 상자 메뉴는 새 메뉴가 열릴 때 바닐라가 닫음 (onPlayerClose 는 부르지 않음 — 응답으로 치지 않음)
        gui.open();
    }

    /** 칸의 그림: 아이템 (Polymer 가 종이 + 모델로 바꿈) 또는 창 그림 (rpg:vui/&lt;그림&gt; 모델) + 이름 · 설명 · 수 */
    private static ItemStack icon(FormNet.SlotData s) {
        ItemStack st;
        if (!s.icon().item().isEmpty()) {
            st = s.icon().item().copy();
        } else if (!s.icon().sprite().isEmpty()) {
            st = new ItemStack(Items.PAPER);
            st.set(DataComponents.ITEM_MODEL, Mmorpg.rpg("vui/" + VanillaInput.iconId(s.icon().sprite())));
        } else {
            st = new ItemStack(Items.PAPER);
        }
        List<Component> lore = new ArrayList<>();
        if (s.kind() == Slot.PRICE) lore.add(plain("§a가격 §f" + s.num()));
        for (String l : s.lore()) lore.add(plain(l));
        if (!s.name().isEmpty()) st.set(DataComponents.CUSTOM_NAME, plain(s.name()));
        else st.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        st.set(DataComponents.LORE, new ItemLore(lore));
        if (s.kind() == Slot.COUNT && s.num() > 1) {
            st.set(DataComponents.MAX_STACK_SIZE, 99);
            st.setCount(Math.min(99, s.num()));
        } else {
            st.setCount(1);
        }
        return st;
    }

    /** 본문 글 (상자 창 아래 글 — 줄마다 설명) */
    private static ItemStack info(String body) {
        ItemStack st = new ItemStack(Items.BOOK);
        st.set(DataComponents.CUSTOM_NAME, plain("§e설명 §7(마우스를 올려 보세요)"));
        List<Component> lore = new ArrayList<>();
        for (String l : body.split("\n")) lore.add(plain(l));
        st.set(DataComponents.LORE, new ItemLore(lore));
        return st;
    }

    /** 기울지 않은 글 (아이템 이름 · 설명의 기본 기울임을 끔) */
    private static Component plain(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withItalic(false));
    }

    // ------------------------------------------------------------ 대화창

    private static void buttons(ServerPlayer sp, int id, String title, String text, List<FormNet.ChoiceData> choices) {
        closeChest(sp);
        List<DialogBody> body = new ArrayList<>();
        if (!text.isEmpty()) body.add(new PlainMessage(Component.literal(text), WIDTH));
        List<ActionButton> actions = new ArrayList<>(choices.size());
        for (int i = 0; i < choices.size(); i++) actions.add(button(choices.get(i).text(), id, i, WIDTH));
        open(sp, title, body, List.of(), actions, button("§7닫기", id, -1, 150));
    }

    private static void modal(ServerPlayer sp, FormNet.ModalPayload m) {
        closeChest(sp);
        List<DialogBody> body = new ArrayList<>();
        List<Input> inputs = new ArrayList<>();
        int k = 0;
        for (FormNet.ControlData c : m.controls()) {
            Component label = Component.literal(c.label());
            InputControl control = switch (c.type()) {
                case FormNet.ControlData.TEXT -> new TextInput(WIDTH, label, true, c.value(), 600, Optional.empty());
                case FormNet.ControlData.DROPDOWN -> {
                    List<SingleOptionInput.Entry> entries = new ArrayList<>();
                    int chosen = parseInt(c.value(), 0);
                    for (int j = 0; j < c.options().size(); j++) {
                        entries.add(new SingleOptionInput.Entry(String.valueOf(j), Optional.of(Component.literal(c.options().get(j))), j == chosen));
                    }
                    yield entries.isEmpty() ? null : new SingleOptionInput(WIDTH, entries, label, true);
                }
                case FormNet.ControlData.SLIDER -> new NumberRangeInput(WIDTH, label, "options.generic_value",
                        new NumberRangeInput.RangeInfo(c.min(), Math.max(c.min() + 1, c.max()), Optional.of((float) parseInt(c.value(), c.min())), Optional.of(1F)));
                case FormNet.ControlData.TOGGLE -> new BooleanInput(label, Boolean.parseBoolean(c.value()), "true", "false");
                default -> null;
            };
            if (c.isInput()) {
                if (control != null) inputs.add(new Input(key(k), control));
                k++;
            } else if (c.type() != FormNet.ControlData.DIVIDER && !c.label().isEmpty()) {
                body.add(new PlainMessage(Component.literal(c.label()), WIDTH));
            }
        }
        CompoundTag extra = new CompoundTag();
        extra.putInt("id", m.id());
        extra.putInt("sel", 0);
        extra.putInt("n", k);
        ActionButton submit = new ActionButton(new CommonButtonData(Component.literal(m.submit().isEmpty() ? "확인" : m.submit()), Optional.empty(), 150),
                Optional.of(new CustomAll(CLICK, Optional.of(extra))));
        open(sp, m.title(), body, inputs, List.of(submit), button("§7닫기", m.id(), -1, 150));
    }

    private static void open(ServerPlayer sp, String title, List<DialogBody> body, List<Input> inputs, List<ActionButton> actions, ActionButton exit) {
        CommonDialogData common = new CommonDialogData(Component.literal(title), Optional.empty(), true, false, DialogAction.WAIT_FOR_RESPONSE, body, inputs);
        DIALOGS.add(sp.getUUID());
        sp.openDialog(Holder.direct(new MultiActionDialog(common, actions, Optional.of(exit), 1)));
    }

    private static ActionButton button(String label, int id, int sel, int width) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("sel", sel);
        return new ActionButton(new CommonButtonData(Component.literal(label), Optional.empty(), width),
                Optional.of(new StaticAction(new ClickEvent.Custom(CLICK, Optional.of(tag)))));
    }

    private static String key(int i) {
        return "v" + i;
    }

    /**
     * 대화창 단추 · 확인 (ServerboundCustomClickActionPacket — 서버 스레드): 우리 것이면 Forms 에 응답 (입력창은 입력 순서대로 값 — 글자 · 고른 번호 ·
     * 숫자 · "true"/"false"). 처리했으면 true
     */
    public static boolean onClick(ServerPlayer sp, Identifier id, Optional<Tag> payload) {
        if (!CLICK.equals(id) || sp == null) return false;
        CompoundTag tag = payload.filter(t -> t instanceof CompoundTag).map(t -> (CompoundTag) t).orElse(null);
        if (tag == null) return true;
        int form = tag.getIntOr("id", -1);
        int sel = tag.getIntOr("sel", -1);
        int n = Math.max(0, Math.min(64, tag.getIntOr("n", 0)));
        List<String> values = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String k = key(i);
            Tag v = tag.get(k);
            if (v == null) values.add("");
            else if (v instanceof net.minecraft.nbt.StringTag) values.add(tag.getStringOr(k, ""));
            else if (v instanceof net.minecraft.nbt.ByteTag) values.add(String.valueOf(tag.getBooleanOr(k, false)));
            else values.add(number(tag.getDoubleOr(k, 0)));
        }
        // 받을 일이 다음 창을 열면 그 창이 기다리는 화면을 바꾸고, 열지 않으면 Forms 가 close 로 닫음
        boolean handled = Forms.answerVanilla(sp, form, sel, values);
        if (!handled && Forms.pendingId(sp) < 0) close(sp); // 늦게 온 응답 (기다리는 창 없음): 기다리는 화면을 닫음
        return true;
    }

    private static String number(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.3f", d);
    }

    private static int parseInt(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (RuntimeException e) {
            return def;
        }
    }
}
