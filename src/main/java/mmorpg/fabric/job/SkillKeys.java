package mmorpg.fabric.job;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.StatTree;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;

/**
 * 2026-11-18 축복의 길 — 스킬 키 여섯 (사용자 "무기 스킬이 없어서 서버가 받을수 있는 조작이 좌클릭, 우클릭, 쉬프트*2, 쉬프트 좌클릭, 쉬프트 우클릭, q, 쉬프트 f,
 * 쉬프트 q … 사용자가 원하는 스킬을 저중에서 직업스킬이랑 공용스킬을 추가 하는 방식" · "우클릭에 1차 스킬을 두고 싶으면 둘수 있고 우클릭에 4차 스킬을 두고 싶으면
 * 두게 커스텀 마이징" · "공용스킬 하나만"). 좌클릭은 평타, Shift + F 는 메뉴, F 는 룬 모드 (VanillaInput).
 * <ul>
 * <li>키 (저장 순서 = HUD 칸 순서): 우클릭 · Shift + 우클릭 · Shift + 좌클릭 · Shift 두 번 · Q · Shift + Q</li>
 * <li>끼울 것: 직업 스킬 칸 "j1" ~ "j5" (2차 전직은 같은 칸을 바꿔 끼우므로 칸으로 기억) · 공용 스킬 "c" (스탯 트리에서 연 것 가운데 끼운 하나 —
 * CommonSkills.equipped) · "" 빈 키. 한 스킬은 한 키에만. 처음 값 = 컨셉 문서 조작 표 (우 직업 1 · ⇧우 직업 3 · ⇧좌 직업 2 · ⇧⇧ 공용 · Q 직업 4 · ⇧Q 직업 5)</li>
 * <li>기록: 플레이어 속성 {@link #PROP} (죽어도 남음 — 룬 스킬 칸과 같음)</li>
 * <li>서버가 보는 패킷 (모드 · 모드 없는 클라이언트 같음): 우클릭 = 무기의 아이템 사용 (RpgWeaponItem.use — 상자 · 문 · NPC 는 그쪽이 먼저 받음, 누르고 있으면
 * 4틱마다 다시 오므로 같은 키가 {@link #HOLD_GAP} 틱 안에 다시 오면 누르고 있는 것으로 봄) · Shift + 좌클릭 = 웅크린 채 휘두르기 · 공격 (평타 대신 —
 * 블록 캐기 시작은 막음) · Shift 두 번 = 움직임 입력의 웅크림이 {@link #DOUBLE_GAP} 틱 안에 두 번 켜짐 (그 사이 다른 Shift 키를 쓰지 않음) · Q = 버리기
 * (버리지 않고 손 칸을 되돌림) · Shift + Q = 웅크린 채 버리기 · Ctrl + Q (한 묶음 버리기)</li>
 * <li>빈 키는 원래대로 (우클릭 = 아무것도 · Shift + 좌클릭 = 평타 · Q = 버리기 — 버린 것은 가방으로 DropGuard)</li>
 * </ul>
 */
public final class SkillKeys {
    private SkillKeys() {}

    /** 키 여섯 (순서 = 저장 · HUD 칸) */
    public enum Key {
        RMB("rmb", "우클릭", "우"),
        SHIFT_RMB("srmb", "Shift + 우클릭", "⇧우"),
        SHIFT_LMB("slmb", "Shift + 좌클릭", "⇧좌"),
        SHIFT2("ss", "Shift 두 번", "⇧⇧"),
        Q("q", "Q", "Q"),
        SHIFT_Q("sq", "Shift + Q", "⇧Q");

        /** 명령 · 기록 이름 */
        public final String id;
        /** 설명 상자 · 알림 */
        public final String label;
        /** HUD · 메뉴 칸 아래 */
        public final String shortLabel;

        Key(String id, String label, String shortLabel) {
            this.id = id;
            this.label = label;
            this.shortLabel = shortLabel;
        }

        public static Key of(String id) {
            for (Key k : values()) if (k.id.equals(id)) return k;
            return null;
        }
    }

    public static final int SIZE = Key.values().length;
    /** 플레이어 속성 (쉼표로 여섯 — "j1,j3,j2,c,j4,j5") */
    public static final String PROP = "rpg:skill_keys";
    /** 공용 스킬 (끼운 하나) */
    public static final String COMMON = "c";
    /** 처음 값 (컨셉 문서 조작 표) */
    static final String[] DEFAULT = {"j1", "j3", "j2", COMMON, "j4", "j5"};
    /** 우클릭을 누르고 있으면 클라이언트가 4틱마다 다시 보냄 — 같은 키가 이 틱 안에 다시 오면 누르고 있는 것 (한 번만 셈) */
    static final int HOLD_GAP = 5;
    /** Shift 두 번: 두 번째 켜짐이 첫 켜짐에서 이 틱 안 (0.3초) */
    static final int DOUBLE_GAP = 6;

    /** 사람마다 지금 입력 상태 (저장하지 않음) */
    private static final class Input {
        final long[] lastUse = new long[SIZE];
        /** Shift + 좌클릭을 쓴 틱 (휘두르기 · 공격 패킷이 같은 틱에 같이 옴 — 한 번만) */
        long slmbTick = -1000;
        boolean shift;
        /** 첫 웅크림 켜짐 틱 (없으면 -1000) */
        long shiftDownAt = -1000;
        /** 첫 켜짐 뒤 다른 Shift 키를 씀 (Shift 두 번이 아님) */
        boolean shiftUsed;
        /** 시험: 이 사람이 마지막으로 누른 키 · 누른 수 */
        Key last;
        int count;

        Input() {
            Arrays.fill(lastUse, -1000);
        }
    }

    private static final Map<UUID, Input> INPUT = new HashMap<>();
    /** 시험 · 영상: 키가 눌린 수 (스킬을 썼든 못 썼든) · 마지막으로 눌린 키 */
    public static int pressed;
    public static volatile Key lastKey;
    /** 바뀜을 알림 (모드 클라이언트 칸 · HUD — 처음은 없음) */
    public static volatile java.util.function.Consumer<ServerPlayer> onChange = sp -> {};

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> INPUT.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            INPUT.remove(h.player.getUUID());
            mmorpg.fabric.RpgCombat.forgetPunch(h.player.getUUID());
        });
        CommandRegistrationCallback.EVENT.register((d, access, env) -> register(d));
    }

    private static Input input(ServerPlayer sp) {
        return INPUT.computeIfAbsent(sp.getUUID(), k -> new Input());
    }

    // ------------------------------------------------------------ 기록

    static boolean valid(String what) {
        if (what == null) return false;
        if (what.isEmpty() || what.equals(COMMON)) return true;
        if (what.length() != 2 || what.charAt(0) != 'j') return false;
        int n = what.charAt(1) - '0';
        return n >= 1 && n <= JobSkills.SIZE;
    }

    /** 지금 키 여섯에 끼운 것 (고친 사본) */
    public static String[] binds(ServerPlayer sp) {
        String raw = sp == null ? null : Props.get(sp, PROP);
        if (raw == null) return DEFAULT.clone();
        String[] out = new String[SIZE];
        String[] parts = raw.split(",", -1);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < SIZE; i++) {
            String w = i < parts.length ? parts[i].trim() : "";
            if (!valid(w) || (!w.isEmpty() && !seen.add(w))) w = "";
            out[i] = w;
        }
        return out;
    }

    /** 키 k 에 끼운 것 ("" = 빈 키) */
    public static String bound(ServerPlayer sp, Key k) {
        return binds(sp)[k.ordinal()];
    }

    /** what 을 끼운 키 (없으면 null) */
    public static Key keyOf(ServerPlayer sp, String what) {
        if (what == null || what.isEmpty()) return null;
        String[] b = binds(sp);
        for (Key k : Key.values()) if (what.equals(b[k.ordinal()])) return k;
        return null;
    }

    private static void save(ServerPlayer sp, String[] b) {
        Props.set(sp, PROP, String.join(",", b));
        try {
            onChange.accept(sp);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] skill keys change", e);
        }
    }

    /** 키 k 에 what 을 끼움 (한 스킬은 한 키에만 — 다른 키에 있던 것은 뺌, "" = 빼기). 안 되면 까닭 */
    public static String bind(ServerPlayer sp, Key k, String what) {
        if (sp == null || k == null) return "키가 없습니다";
        if (!valid(what)) return "끼울 수 없는 것입니다: " + what;
        String[] b = binds(sp);
        if (!what.isEmpty()) for (int i = 0; i < SIZE; i++) if (what.equals(b[i])) b[i] = "";
        b[k.ordinal()] = what;
        save(sp, b);
        return null;
    }

    /** 처음 값으로 */
    public static void reset(ServerPlayer sp) {
        save(sp, DEFAULT.clone());
    }

    /** 끼운 것의 이름 (빈 키 · 빈 칸이면 회색 글) */
    public static String name(ServerPlayer sp, String what) {
        if (what == null || what.isEmpty()) return "§8(비어 있음)";
        if (what.equals(COMMON)) {
            String id = CommonSkills.equipped(sp);
            StatTree.Skill k = id == null ? null : StatTree.skill(id);
            return k == null ? "§8공용 스킬 (열린 것 없음)" : "§b" + k.name();
        }
        int i = what.charAt(1) - '1';
        JobSkills.Skill s = JobSkills.skill(sp, i);
        return s == null ? "§8직업 스킬 " + (i + 1) + " (없음)" : "§d" + s.name();
    }

    // ------------------------------------------------------------ 쓰기

    /** 키 k 를 누름: 끼운 스킬을 씀 (quiet = 공용 스킬이 열리지 않았으면 알리지 않음 — Shift 두 번). 끼운 것이 없으면 false */
    public static boolean press(ServerPlayer sp, Key k, boolean quiet) {
        if (sp == null || k == null || sp.isRemoved() || sp.isSpectator() || !sp.isAlive()) return false;
        String what = bound(sp, k);
        if (what.isEmpty()) return false;
        pressed++;
        lastKey = k;
        Input in = input(sp);
        in.last = k;
        in.count++;
        if (what.equals(COMMON)) {
            if (quiet && CommonSkills.equipped(sp) == null) return true;
            CommonSkills.press(sp);
            return true;
        }
        JobSkills.press(sp, what.charAt(1) - '1');
        return true;
    }

    /** 시험: 이 사람이 마지막으로 누른 키 (없으면 null) · 누른 수 */
    public static Key lastKey(ServerPlayer sp) {
        Input in = sp == null ? null : INPUT.get(sp.getUUID());
        return in == null ? null : in.last;
    }

    public static int pressedCount(ServerPlayer sp) {
        Input in = sp == null ? null : INPUT.get(sp.getUUID());
        return in == null ? 0 : in.count;
    }

    /** 끼운 것이 있는 키인지 (원래 동작을 막을지) */
    public static boolean has(ServerPlayer sp, Key k) {
        return sp != null && !bound(sp, k).isEmpty() && mmorpg.fabric.RpgHealth.isRpgMode(sp);
    }

    // ------------------------------------------------------------ 패킷 (서버 스레드)

    /** 우클릭 · Shift + 우클릭 (무기의 아이템 사용 — RpgWeaponItem.use, 주 손): 끼운 스킬이 있으면 true (누르고 있는 동안 다시 온 것은 쓰지 않음) */
    public static boolean onUse(ServerPlayer sp) {
        Key k = sp.isShiftKeyDown() ? Key.SHIFT_RMB : Key.RMB;
        if (!has(sp, k)) return false;
        Input in = input(sp);
        long now = Tasks.now();
        long last = in.lastUse[k.ordinal()];
        in.lastUse[k.ordinal()] = now;
        if (k == Key.SHIFT_RMB) in.shiftUsed = true;
        if (now - last <= HOLD_GAP) return true; // 누르고 있음
        press(sp, k, false);
        return true;
    }

    /** 휘두르기 패킷 (좌클릭 — 허공 · 블록 · 몹): 웅크렸고 Shift + 좌클릭에 끼운 것이 있으면 그 스킬 (평타 대신) — 막았으면 true */
    public static boolean onPunch(ServerPlayer sp) {
        return shiftLeft(sp);
    }

    /** 공격 패킷 (몹을 누름 — 휘두르기와 같은 틱): 웅크렸고 Shift + 좌클릭에 끼운 것이 있으면 그 스킬 · 평타 공격은 막음 — 막았으면 true */
    public static boolean onAttack(ServerPlayer sp) {
        return shiftLeft(sp);
    }

    private static boolean shiftLeft(ServerPlayer sp) {
        if (sp == null || !sp.isShiftKeyDown() || !has(sp, Key.SHIFT_LMB)) return false;
        Input in = input(sp);
        long now = Tasks.now();
        in.shiftUsed = true;
        if (in.slmbTick == now) return true; // 같은 누름의 두 번째 패킷
        in.slmbTick = now;
        press(sp, Key.SHIFT_LMB, false);
        return true;
    }

    /** 블록 캐기 시작: Shift + 좌클릭 스킬 키면 막음 (무기를 든 채 웅크려 누름 — 풀 · 꽃이 바로 부서지지 않게) */
    public static boolean blocksMining(ServerPlayer sp) {
        return sp != null && sp.isShiftKeyDown() && has(sp, Key.SHIFT_LMB) && sp.getMainHandItem().getItem() instanceof RpgWeaponItem;
    }

    /** 버리기 패킷 (Q · Ctrl + Q — 핫바): Q = 웅크리지 않은 Q, Shift + Q = 웅크린 Q · Ctrl + Q. 끼운 것이 있으면 버리지 않고 손 칸을 되돌림 — 막았으면 true */
    public static boolean onDrop(ServerPlayer sp, boolean all) {
        Key k = sp.isShiftKeyDown() || all ? Key.SHIFT_Q : Key.Q;
        if (!has(sp, k)) return false;
        if (k == Key.SHIFT_Q) input(sp).shiftUsed = true;
        // 클라이언트는 보내기 전에 손 칸을 비움 → 진짜 칸을 다시 보냄 (모드 없는 클라이언트의 무기 그림 · 1인칭 동작 이름은 Polymer 가 받는 사람마다 바꿈)
        int slot = sp.getInventory().getSelectedSlot();
        sp.connection.send(new ClientboundSetPlayerInventoryPacket(slot, sp.getInventory().getItem(slot).copy()));
        mmorpg.fabric.vanilla.VanillaFirstPerson.noteDrop(sp);
        press(sp, k, false);
        return true;
    }

    /** 움직임 입력 (웅크림 켜짐 · 꺼짐): 켜짐이 DOUBLE_GAP 틱 안에 두 번이면 Shift 두 번 */
    public static void onInput(ServerPlayer sp, boolean shift) {
        if (sp == null) return;
        Input in = input(sp);
        boolean was = in.shift;
        in.shift = shift;
        if (!shift || was) return;
        long now = Tasks.now();
        if (now - in.shiftDownAt <= DOUBLE_GAP && !in.shiftUsed) {
            in.shiftDownAt = -1000;
            if (has(sp, Key.SHIFT2)) press(sp, Key.SHIFT2, true);
            return;
        }
        in.shiftDownAt = now;
        in.shiftUsed = false;
    }

    /** Shift + F (메뉴) 등 다른 Shift 키를 씀 — 그 웅크림은 Shift 두 번으로 세지 않음 */
    public static void shiftUsed(ServerPlayer sp) {
        if (sp != null) input(sp).shiftUsed = true;
    }

    // ------------------------------------------------------------ 명령

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String root : new String[] {"skillkey", "스킬키"}) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(root).executes(c -> {
                info(c.getSource().getPlayerOrException());
                return 1;
            });
            LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set");
            for (Key k : Key.values()) {
                LiteralArgumentBuilder<CommandSourceStack> key = Commands.literal(k.id);
                for (String what : List.of("j1", "j2", "j3", "j4", "j5", "common", "none")) {
                    key.then(Commands.literal(what).executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        String w = what.equals("common") ? COMMON : what.equals("none") ? "" : what;
                        String err = bind(sp, k, w);
                        if (err != null) Notify.warn(sp, err);
                        else info(sp);
                        return err == null ? 1 : 0;
                    }));
                }
                set.then(key);
            }
            LiteralArgumentBuilder<CommandSourceStack> press = Commands.literal("press");
            for (Key k : Key.values()) {
                press.then(Commands.literal(k.id).executes(c -> press(c.getSource().getPlayerOrException(), k, false) ? 1 : 0));
            }
            cmd.then(set).then(press).then(Commands.literal("reset").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                reset(sp);
                info(sp);
                return 1;
            }));
            d.register(cmd);
        }
    }

    /** 채팅 요약: 키마다 끼운 것 (누르면 그 키의 다음 것으로 — 직업 1 ~ 5 · 공용 · 빼기를 차례로) */
    static void info(ServerPlayer sp) {
        String[] b = binds(sp);
        sp.sendSystemMessage(Component.literal(BedrockText.toJava("§6━━ 스킬 키 §7(누르면 다음 스킬로 · 메뉴 Shift + F → 스킬에서도) §6━━")));
        String[] cycle = {"j1", "j2", "j3", "j4", "j5", COMMON, ""};
        for (Key k : Key.values()) {
            String cur = b[k.ordinal()];
            int at = Arrays.asList(cycle).indexOf(cur);
            String next = cycle[(at + 1) % cycle.length];
            String nextArg = next.equals(COMMON) ? "common" : next.isEmpty() ? "none" : next;
            MutableComponent line = Component.literal(BedrockText.toJava("  §e" + k.label + " §8→ "));
            line.append(Component.literal(BedrockText.toJava(name(sp, cur))).withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/skillkey set " + k.id + " " + nextArg))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(BedrockText.toJava("§7누르면 " + name(sp, next) + " §7로"))))));
            sp.sendSystemMessage(line);
        }
        sp.sendSystemMessage(Component.literal("  [처음 값으로]").withStyle(st -> st.withColor(ChatFormatting.GRAY).withClickEvent(new ClickEvent.RunCommand("/skillkey reset"))));
    }
}
