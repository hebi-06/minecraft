package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.SkillBarNet;
import mmorpg.fabric.skill.Status;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 0.26.0 직업 스킬 칸 (사용자 "각 무기에 맞는 직업을 추가할 거라서 직업 스킬도 — 룬 스킬 단축키 말고 직업 스킬 단축도, shift 누르고 r 이런 식으로 공간을
 * 5개만 더"): 칸 SIZE 개. 키는 Shift + 룬 스킬 칸 1 ~ 5 의 키 (기본 R · Z · V · B · Y — 클라이언트 MmorpgClient 가 Shift 를 누른 채면 이쪽으로 보냄).
 * <ul>
 * <li>직업 · 직업 스킬은 아직 없음: 칸 i 의 스킬은 {@link #provider} 가 정함 (지금은 늘 빈 칸 — 직업을 만들 때 바꿈)</li>
 * <li>누르면 그 스킬을 씀: 재사용 대기 (스킬마다) · 침묵 · 탈것은 룬과 같음, 빈 칸이면 안내만</li>
 * <li>칸의 이름 · 그림은 클라이언트에 (접속 · 다시 태어남 · {@link #send}) — 화면 오른쪽 아래 룬 칸 위 · 스킬 칸 창 (J). 재사용 대기는 룬과 같은 패킷
 * (SkillBarNet.cooldown — 이름 "job:" + 스킬 id)</li>
 * </ul>
 */
public final class JobSkills {
    private JobSkills() {}

    public static final int SIZE = 5;
    /** 재사용 대기 패킷의 이름 앞 (룬 ID 와 겹치지 않게) */
    public static final String CD_PREFIX = "job:";

    /** 직업 스킬 하나 */
    public interface Skill {
        String id();

        String name();

        /** 칸 그림 */
        ItemStack icon();

        /** 재사용 대기 (틱) */
        int cooldown();

        /** 씀 — 나갔으면 true (재사용 대기 시작) */
        boolean cast(ServerPlayer sp);
    }

    /** 이 사람의 칸 i (0 ~ SIZE - 1) 에 든 직업 스킬 (없으면 null) — 직업을 만들 때 바꿈 (시험은 시험용 스킬) */
    public static volatile BiFunction<ServerPlayer, Integer, Skill> provider = (sp, i) -> null;

    /** "UUID|스킬 id" → 재사용 대기가 끝나는 서버 틱 */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    /** 서버 → 클라이언트: 직업 스킬 칸 (늘 SIZE 개 — 빈 칸은 id "") */
    public record BarPayload(List<String> ids, List<String> names, List<ItemStack> icons) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BarPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("job_bar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BarPayload> CODEC = CustomPacketPayload.codec(BarPayload::write, BarPayload::read);

        private static BarPayload read(RegistryFriendlyByteBuf buf) {
            int n = Math.min(16, buf.readVarInt());
            List<String> ids = new ArrayList<>(n);
            List<String> names = new ArrayList<>(n);
            List<ItemStack> icons = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ids.add(buf.readUtf(64));
                names.add(buf.readUtf(128));
                icons.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            return new BarPayload(List.copyOf(ids), List.copyOf(names), List.copyOf(icons));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                buf.writeUtf(ids.get(i), 64);
                buf.writeUtf(names.get(i), 128);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icons.get(i));
            }
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** SkillBar.init (스킬 칸과 함께) */
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(BarPayload.TYPE, BarPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> send(newPlayer));
    }

    /** 칸 i 의 직업 스킬 (없으면 null) */
    public static Skill skill(ServerPlayer sp, int i) {
        if (sp == null || i < 0 || i >= SIZE) return null;
        try {
            return provider.apply(sp, i);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] job skill provider", e);
            return null;
        }
    }

    private static String key(ServerPlayer sp, Skill s) {
        return sp.getUUID() + "|" + s.id();
    }

    /** 남은 재사용 대기 (틱) */
    public static long cooldownLeft(ServerPlayer sp, Skill s) {
        Long until = COOLDOWNS.get(key(sp, s));
        return until == null ? 0 : Math.max(0, until - Tasks.now());
    }

    /** 시험용: 이 사람의 직업 스킬 재사용 대기를 모두 지움 */
    public static void resetCooldowns(ServerPlayer sp) {
        String p = sp.getUUID() + "|";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(p));
    }

    /** 칸 i 의 키 (Shift + 룬 칸 i 의 키): 그 직업 스킬을 씀. 나갔으면 true */
    public static boolean press(ServerPlayer sp, int i) {
        if (sp == null || i < 0 || i >= SIZE || sp.isRemoved() || sp.isSpectator() || sp.isDeadOrDying()) return false;
        Skill s = skill(sp, i);
        if (s == null) {
            Notify.notify(sp, "§7직업 스킬 " + (i + 1) + "번 칸이 비어 있습니다 §8(직업이 추가되면 직업 스킬이 들어갑니다)", 30);
            return false;
        }
        if (Status.blockedBySilence(sp)) return false; // 보스 기믹: 침묵 (룬과 같음)
        if (mmorpg.fabric.mount.Riding.blocked(sp, "skill")) return false; // 탈것에 탄 동안 (룬과 같음)
        long left = cooldownLeft(sp, s);
        if (left > 0) {
            Notify.notify(sp, "§c" + s.name() + " §7재사용 대기 §f" + String.format(Locale.ROOT, "%.1f", left / 20.0) + "초", 20);
            return false;
        }
        boolean ok;
        try {
            ok = s.cast(sp);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] job skill {}", s.id(), e);
            ok = false;
        }
        if (!ok) return false;
        int cd = Math.max(0, s.cooldown());
        if (cd > 0) {
            COOLDOWNS.put(key(sp, s), Tasks.now() + cd);
            SkillBarNet.cooldown(sp, CD_PREFIX + s.id(), cd, cd);
        }
        return true;
    }

    /** 칸들을 클라이언트에 (모드가 있는 클라이언트만) — 직업이 바뀌면 부름 */
    public static void send(ServerPlayer sp) {
        if (sp == null || sp.isRemoved() || !ServerPlayNetworking.canSend(sp, BarPayload.TYPE)) return;
        List<String> ids = new ArrayList<>(SIZE);
        List<String> names = new ArrayList<>(SIZE);
        List<ItemStack> icons = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            Skill s = skill(sp, i);
            ids.add(s == null ? "" : s.id());
            names.add(s == null ? "" : s.name());
            icons.add(s == null || s.icon() == null ? ItemStack.EMPTY : s.icon().copy());
        }
        ServerPlayNetworking.send(sp, new BarPayload(ids, names, icons));
    }
}
