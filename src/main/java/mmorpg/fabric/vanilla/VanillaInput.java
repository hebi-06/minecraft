package mmorpg.fabric.vanilla;

import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.rune.SkillBar;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3단계: 모드 없는 클라이언트의 스킬 입력 (사용자 2026-11-07 "F 한번 누르면 룬 스킬이고 한번 더 누르면 직업 스킬 모드 한번 더 누르면 일반 상태로 …
 * 무기 스킬은 똑같이 우클릭 쉬프트 우클릭으로"). 바닐라 클라이언트가 서버에 알려 주는 키는 핫바 칸 번호 · F (왼손 바꾸기) · Q · 움직임 · 클릭뿐이라 그것으로 만듦.
 *
 * <ul>
 * <li>F (왼손 바꾸기 키 — 바닐라 바꾸기는 하지 않음, Shift + F 는 일반 모드에서 원래대로 바꿈): 일반 → 룬 스킬 → 직업 스킬 → 일반</li>
 * <li>스킬 모드 동안 든 칸은 9번 (들고 있던 무기를 9번 칸과 바꿔 둠 — 나올 때 되돌림): 숫자 키는 지금 든 칸을 다시 누르면 아무것도 보내지 않으므로,
 * 1 ~ 8 이 모두 "칸 바뀜" 을 보내도록 든 칸을 스킬 키가 아닌 9번에 둠. 칸 바뀜이 오면 그 칸의 스킬을 쓰고 든 칸을 9번으로 되돌림</li>
 * <li>룬 스킬: 숫자 1 ~ 8 = 스킬 칸 1 ~ 8 ({@link SkillBar#cast}) · 직업 스킬: 숫자 1 ~ 5 = 직업 스킬 1 ~ 5 ({@link JobSkills#press})</li>
 * <li>스킬 모드 동안 핫바 1 ~ 8 칸에는 그 모드의 스킬 그림이 보임 (VanillaPackets 가 보내는 가방 내용을 바꿈 — 서버의 가방은 그대로) +
 * 재사용 대기는 바닐라 아이템 쿨타임 덮개 (룬 = 룬 아이템 쿨타임 묶음 · 직업 = mmorpg:job/&lt;스킬 id&gt;)</li>
 * <li>일반으로 돌아가는 때: F 세 번째 · 가방을 누름 (누른 것은 하지 않음 — 진짜 칸이 다시 보임) · 상자 등 창이 열림 · 죽음 · 나감 · 생존 · 모험이 아님</li>
 * <li>무기 스킬 (우클릭 · Shift + 우클릭 · 길게) 은 모드와 상관없이 아이템 사용 그대로 (RpgWeaponItem.use — 모드 클라이언트와 같은 길)</li>
 * </ul>
 * 마우스 휠도 칸 바뀜을 보내므로 스킬 모드에서 휠을 굴리면 1번 · 8번 스킬이 나감 (서버가 키와 휠을 가를 수 없음 — 첫 접속 안내).
 */
public final class VanillaInput {
    private VanillaInput() {}

    public enum Mode { NORMAL, RUNE, JOB }

    /** 스킬 모드에서 드는 칸 (9번) */
    static final int ANCHOR = 8;
    /** 핫바 칸 수 */
    static final int HOTBAR = 9;

    private static final class State {
        Mode mode = Mode.NORMAL;
        /** 들어올 때 든 칸 (9번과 바꿈) */
        int from = -1;
        /** 9번으로 옮긴 것 (돌려놓을 때 아직 9번에 있는지) */
        Item moved = Items.AIR;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** 첫 접속 안내를 본 사람 (엔티티 태그 — 저장됨) */
    private static final String GUIDE_TAG = "mmorpg.vanilla_guide";

    static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> guide(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> exit(handler.player, false));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) exit(sp, false);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            exit(oldPlayer, false);
            STATES.remove(newPlayer.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(VanillaInput::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    /** 지금 모드 (모드 클라이언트 · 없으면 NORMAL) */
    public static Mode mode(ServerPlayer sp) {
        State st = sp == null ? null : STATES.get(sp.getUUID());
        return st == null ? Mode.NORMAL : st.mode;
    }

    // ------------------------------------------------------------ 키

    /** F (왼손 바꾸기 패킷 — 서버 스레드): 모드 없는 클라이언트면 모드를 넘김. 바닐라 바꾸기를 막았으면 true */
    public static boolean onSwapKey(ServerPlayer sp) {
        if (sp == null || !VanillaClients.isVanilla(sp)) return false;
        Mode m = mode(sp);
        if (m == Mode.NORMAL && sp.isShiftKeyDown()) return false; // Shift + F: 원래 왼손 바꾸기
        if (!RpgHealth.isRpgMode(sp)) { // 크리에이티브 · 관전: 스킬 모드 없음 (F 는 원래대로)
            if (m == Mode.NORMAL) return false;
            exit(sp, true);
            return true;
        }
        switch (m) {
            case NORMAL -> enter(sp);
            case RUNE -> {
                STATES.get(sp.getUUID()).mode = Mode.JOB;
                refresh(sp);
                say(sp);
            }
            case JOB -> exit(sp, true);
        }
        return true;
    }

    /** 칸 바뀜 (숫자 키 · 휠 — 서버 스레드): 스킬 모드면 그 칸의 스킬을 쓰고 든 칸을 되돌림. 바닐라 처리를 막았으면 true */
    public static boolean onSlot(ServerPlayer sp, int slot) {
        State st = sp == null ? null : STATES.get(sp.getUUID());
        if (st == null || st.mode == Mode.NORMAL) return false;
        int held = sp.getInventory().getSelectedSlot();
        // 지금 든 칸 (되돌린 뒤 클라이언트가 맞춰 보내는 것 · 9번 키) · 없는 칸: 아무 일 없음
        if (slot == held || slot < 0 || slot >= HOTBAR) return true;
        sp.connection.send(new ClientboundSetHeldSlotPacket(held));
        sp.resetLastActionTime();
        if (st.mode == Mode.RUNE) {
            if (slot < SkillBar.SIZE) SkillBar.cast(sp, slot);
        } else if (slot < JobSkills.SIZE) {
            JobSkills.press(sp, slot);
        }
        return true;
    }

    /** 가방 · 창 누름 (서버 스레드): 스킬 모드면 누른 것은 하지 않고 일반으로 (클라이언트가 본 핫바는 스킬 그림이었으므로) */
    public static boolean onInventoryClick(ServerPlayer sp) {
        if (mode(sp) == Mode.NORMAL) return false;
        exit(sp, true);
        return true;
    }

    /** 블록 · 엔티티 고르기 (가운데 클릭): 스킬 모드면 막음 (서버가 든 칸을 바꾸므로) */
    public static boolean onPick(ServerPlayer sp) {
        return mode(sp) != Mode.NORMAL;
    }

    // ------------------------------------------------------------ 모드

    private static void enter(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        State st = new State();
        st.mode = Mode.RUNE;
        int from = inv.getSelectedSlot();
        st.from = from;
        if (from != ANCHOR && from >= 0 && from < HOTBAR) {
            ItemStack held = inv.getItem(from);
            ItemStack nine = inv.getItem(ANCHOR);
            inv.setItem(from, nine);
            inv.setItem(ANCHOR, held);
            inv.setSelectedSlot(ANCHOR);
            st.moved = held.getItem();
        } else {
            st.moved = inv.getItem(ANCHOR).getItem();
        }
        STATES.put(sp.getUUID(), st);
        sp.connection.send(new ClientboundSetHeldSlotPacket(inv.getSelectedSlot()));
        refresh(sp);
        say(sp);
    }

    /** 일반 모드로 (9번에 옮긴 무기가 아직 있으면 원래 칸으로 되돌림) + 진짜 가방 내용을 다시 보냄 */
    public static void exit(ServerPlayer sp, boolean say) {
        if (sp == null) return;
        State st = STATES.remove(sp.getUUID());
        if (st == null || st.mode == Mode.NORMAL) return;
        Inventory inv = sp.getInventory();
        if (st.from >= 0 && st.from < HOTBAR && st.from != ANCHOR && inv.getItem(ANCHOR).is(st.moved)) {
            ItemStack a = inv.getItem(ANCHOR);
            ItemStack b = inv.getItem(st.from);
            inv.setItem(ANCHOR, b);
            inv.setItem(st.from, a);
            if (inv.getSelectedSlot() == ANCHOR) inv.setSelectedSlot(st.from);
        }
        if (sp.isRemoved() || sp.connection == null) return;
        sp.connection.send(new ClientboundSetHeldSlotPacket(inv.getSelectedSlot()));
        sp.inventoryMenu.sendAllDataToRemote();
        if (say) say(sp, Mode.NORMAL);
    }

    /** 스킬 그림 핫바를 다시 보냄 */
    static void refresh(ServerPlayer sp) {
        sendHotbar(sp);
    }

    /** 핫바 9칸을 클라이언트에 (스킬 모드면 1 ~ 8 칸은 스킬 그림, 아니면 진짜) */
    static void sendHotbar(ServerPlayer sp) {
        if (sp == null || sp.connection == null || sp.isRemoved()) return;
        Inventory inv = sp.getInventory();
        for (int i = 0; i < HOTBAR; i++) {
            ItemStack fake = fake(sp, i);
            sp.connection.send(new ClientboundSetPlayerInventoryPacket(i, fake != null ? fake : inv.getItem(i).copy()));
        }
    }

    private static void say(ServerPlayer sp) {
        say(sp, mode(sp));
    }

    private static void say(ServerPlayer sp, Mode m) {
        switch (m) {
            case RUNE -> {
                Notify.notify(sp, "§b◆ 룬 스킬 §8| §f숫자 1 ~ 8 §7스킬 칸 · §fF §7직업 스킬로", 50);
                Notify.sound(sp, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.4F);
            }
            case JOB -> {
                Notify.notify(sp, "§d◆ 직업 스킬 §8| §f숫자 1 ~ 5 §7직업 스킬 · §fF §7일반으로", 50);
                Notify.sound(sp, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.0F);
            }
            default -> {
                Notify.notify(sp, "§7일반 모드 §8| §fF §7룬 스킬로", 40);
                Notify.sound(sp, SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, 0.8F);
            }
        }
    }

    private static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        for (Map.Entry<UUID, State> e : STATES.entrySet()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null) {
                STATES.remove(e.getKey());
                continue;
            }
            if (!RpgHealth.isRpgMode(sp) || sp.isSpectator()) exit(sp, true);
            else if (sp.containerMenu != sp.inventoryMenu) exit(sp, false); // 상자 · 상점 등 창이 열림 — 창은 진짜 칸을 보여 줌
        }
    }

    // ------------------------------------------------------------ 핫바 그림 (VanillaPackets)

    /** 스킬 모드면 핫바 칸 i (0 ~ 7) 에 보일 것 (빈 칸 = EMPTY), 아니면 null (진짜 그대로) */
    static ItemStack fake(ServerPlayer sp, int i) {
        State st = sp == null ? null : STATES.get(sp.getUUID());
        if (st == null || st.mode == Mode.NORMAL || i < 0 || i >= HOTBAR) return null;
        if (i == sp.getInventory().getSelectedSlot()) return null;
        try {
            return switch (st.mode) {
                case RUNE -> i < SkillBar.SIZE ? SkillBar.get(sp, i).copy() : ItemStack.EMPTY;
                case JOB -> jobIcon(sp, i);
                default -> null;
            };
        } catch (RuntimeException ex) {
            Mmorpg.LOG.warn("[vanilla] skill hotbar {}", i, ex);
            return ItemStack.EMPTY;
        }
    }

    /** 직업 스킬 칸 i 의 그림 (종이 + rpg:vjob/&lt;id&gt; 모델 · 이름 · 쿨타임 묶음 — 잠기면 어두운 모델 + 사유) */
    private static ItemStack jobIcon(ServerPlayer sp, int i) {
        if (i >= JobSkills.SIZE) return ItemStack.EMPTY;
        JobSkills.Skill s = JobSkills.skill(sp, i);
        if (s == null) return ItemStack.EMPTY;
        String lock = s.lock(sp);
        boolean locked = lock != null && !lock.isEmpty();
        String id = iconId(s.id());
        ItemStack st = new ItemStack(Items.PAPER);
        st.set(DataComponents.ITEM_MODEL, Mmorpg.rpg("vjob/" + id + (locked ? "_locked" : "")));
        st.set(DataComponents.ITEM_NAME, Component.literal((locked ? "§8" : "§d") + s.name()));
        if (locked) st.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("§c" + JobSkills.lockText(lock)))));
        st.set(DataComponents.USE_COOLDOWN, new UseCooldown(0.01F, Optional.of(jobGroup(s.id()))));
        st.set(DataComponents.MAX_STACK_SIZE, 1);
        return st;
    }

    /** 직업 스킬 그림 이름 (리소스 팩 경로에 쓸 수 있게 — 소문자 · 영숫자 · _) */
    static String iconId(String skillId) {
        return skillId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    /** 직업 스킬의 바닐라 쿨타임 묶음 */
    static Identifier jobGroup(String skillId) {
        return Mmorpg.id("job/" + iconId(skillId));
    }

    /**
     * 재사용 대기 (SkillBarNet.cooldown — 룬 = 룬 ID, 직업 = "job:" + 스킬 id): 모드 없는 클라이언트에는 바닐라 쿨타임 패킷 (핫바의 스킬 그림 덮개).
     * 남은 틱부터 다시 그림 (줄어들면 덮개가 처음부터 짧게).
     */
    public static void cooldown(ServerPlayer sp, String name, int left) {
        if (sp == null || sp.isRemoved() || name == null || name.equals("*") || !VanillaClients.isVanilla(sp)) return;
        Identifier group = name.startsWith(JobSkills.CD_PREFIX) ? jobGroup(name.substring(JobSkills.CD_PREFIX.length())) : Identifier.tryParse(name);
        if (group != null) sp.connection.send(new ClientboundCooldownPacket(group, Math.max(0, left)));
    }

    /** 스킬 칸이 바뀜 (SkillBarNet.sendBar) — 룬 스킬 모드면 핫바 그림을 다시 */
    public static void onBar(ServerPlayer sp) {
        if (mode(sp) == Mode.RUNE) refresh(sp);
    }

    /** 직업 스킬 칸이 바뀜 (JobSkills.send — 직업 · 레벨 · 무기) — 직업 스킬 모드면 핫바 그림을 다시 */
    public static void onJobBar(ServerPlayer sp) {
        if (mode(sp) == Mode.JOB) refresh(sp);
    }

    // ------------------------------------------------------------ 안내

    /** 모드 없는 클라이언트 접속: 처음이면 조작 안내 전부, 그 뒤로는 한 줄 */
    private static void guide(ServerPlayer sp) {
        if (sp == null || !VanillaClients.isVanilla(sp)) return;
        if (sp.entityTags().contains(GUIDE_TAG)) {
            sp.sendSystemMessage(Component.literal(Notify.PREFIX + "§7F §f스킬 모드 (룬 → 직업 → 일반) §8· §7우클릭 · Shift + 우클릭 §f무기 스킬"));
            return;
        }
        sp.addTag(GUIDE_TAG);
        for (String line : new String[]{
                "§6━━━━━━━━ §f모드 없이 접속한 조작 §6━━━━━━━━",
                "§fF §7: 일반 → §b룬 스킬 §7(숫자 1 ~ 8) → §d직업 스킬 §7(숫자 1 ~ 5) → 일반",
                "§7스킬 모드 동안 핫바에 스킬 그림이 보이고, 들고 있던 무기는 9번 칸에 있습니다",
                "§f우클릭 · Shift + 우클릭 §7: 무기 스킬 (활 · 완드는 길게 누르기도)",
                "§fShift + F §7: 원래의 왼손 바꾸기",
                "§7숫자 키가 멀면 §f옵션 → 조작 → 키 설정 §7의 §f핫바 칸 1 ~ 8 §7을 다른 키로 바꿔 쓰세요 (예: R Z V B …)",
                "§c스킬 모드에서 마우스 휠을 굴리면 1번 · 8번 스킬이 나갑니다"}) {
            sp.sendSystemMessage(Component.literal(line));
        }
    }
}
