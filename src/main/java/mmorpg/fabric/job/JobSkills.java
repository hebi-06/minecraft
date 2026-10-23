package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
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
 * <li>칸 i 의 스킬은 {@link #provider} 가 정함 (0.27.0 — JobSystem: 직업의 스킬, 레벨 · 무기가 맞지 않으면 잠김 {@link Skill#lock})</li>
 * <li>누르면 그 스킬을 씀: 잠김 · 재사용 대기 (스킬마다) · 침묵 · 탈것은 룬과 같음, 빈 칸이면 안내만</li>
 * <li>칸의 이름 · 그림 · 잠김 · 직업 이름 · 색은 클라이언트에 (접속 · 다시 태어남 · {@link #send} — 직업 · 레벨 · 무기가 바뀌면 JobSystem 이 다시 보냄) —
 * 화면 오른쪽 아래 룬 칸 위 · 스킬 칸 창 (J). 재사용 대기는 룬과 같은 패킷 (SkillBarNet.cooldown — 이름 "job:" + 스킬 id),
 * 0.27.0 직업 자원 (검세 · 마력 각인 …) · 태세 남은 시간은 {@link GaugePayload}</li>
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

        /** 0.27.0 잠김 사유 ("" = 쓸 수 있음 — 해금 레벨 "15렙" · 직업 무기 "칼 · 마검" · 요구 스탯) */
        default String lock(ServerPlayer sp) {
            return "";
        }

        /** 0.31.0 직업 스킬 강화 단계 (0 ~ 5 — 칸 배지) */
        default int up(ServerPlayer sp) {
            return 0;
        }
    }

    /** 0.27.0 칸 위 직업 표시: id · 이름 · 두 빛 (0xRRGGBB) — 직업이 없으면 null */
    public record Header(String id, String name, int colorA, int colorB) {}

    /** 이 사람의 칸 i (0 ~ SIZE - 1) 에 든 직업 스킬 (없으면 null) — JobSystem 이 바꿈 (시험은 시험용 스킬) */
    public static volatile BiFunction<ServerPlayer, Integer, Skill> provider = (sp, i) -> null;
    /** 이 사람의 직업 표시 (없으면 null) — JobSystem 이 바꿈 */
    public static volatile Function<ServerPlayer, Header> header = sp -> null;

    /** "UUID|스킬 id" → 재사용 대기가 끝나는 서버 틱 */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    /** "UUID|스킬 id" → 그 재사용 대기의 전체 틱 (줄일 때 칸의 덮개 비율) */
    private static final Map<String, Integer> TOTALS = new HashMap<>();

    /** 서버 → 클라이언트: 직업 스킬 칸 (늘 SIZE 개 — 빈 칸은 id "", 잠김은 사유 글자, 0.31.0 강화 단계) + 직업 (없으면 job "") */
    public record BarPayload(String job, String jobName, int colorA, int colorB, List<String> ids, List<String> names, List<ItemStack> icons,
            List<String> locks, List<Integer> ups) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BarPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("job_bar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BarPayload> CODEC = CustomPacketPayload.codec(BarPayload::write, BarPayload::read);

        private static BarPayload read(RegistryFriendlyByteBuf buf) {
            String job = buf.readUtf(32);
            String jobName = buf.readUtf(64);
            int a = buf.readInt();
            int b = buf.readInt();
            int n = Math.min(16, buf.readVarInt());
            List<String> ids = new ArrayList<>(n);
            List<String> names = new ArrayList<>(n);
            List<ItemStack> icons = new ArrayList<>(n);
            List<String> locks = new ArrayList<>(n);
            List<Integer> ups = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ids.add(buf.readUtf(64));
                names.add(buf.readUtf(128));
                icons.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                locks.add(buf.readUtf(64));
                ups.add(buf.readVarInt());
            }
            return new BarPayload(job, jobName, a, b, List.copyOf(ids), List.copyOf(names), List.copyOf(icons), List.copyOf(locks), List.copyOf(ups));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(job, 32);
            buf.writeUtf(jobName, 64);
            buf.writeInt(colorA);
            buf.writeInt(colorB);
            buf.writeVarInt(ids.size());
            for (int i = 0; i < ids.size(); i++) {
                buf.writeUtf(ids.get(i), 64);
                buf.writeUtf(names.get(i), 128);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icons.get(i));
                buf.writeUtf(i < locks.size() ? locks.get(i) : "", 64);
                buf.writeVarInt(i < ups.size() ? Math.max(0, ups.get(i)) : 0);
            }
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 0.27.0 서버 → 클라이언트: 직업 자원 게이지 (칸 위 — 검세 칼날 다섯 · 마력 각인 셋 …: name · value / max, max 0 = 없음) + 태세 (명경지수 · 마검 각성 —
     * stance 이름 · 남은 틱 / 전체 틱, 0 = 없음)
     */
    public record GaugePayload(String name, int value, int max, String stance, int left, int total) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<GaugePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("job_gauge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GaugePayload> CODEC = CustomPacketPayload.codec(GaugePayload::write, GaugePayload::read);

        private static GaugePayload read(RegistryFriendlyByteBuf buf) {
            return new GaugePayload(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(name, 32);
            buf.writeVarInt(Math.max(0, value));
            buf.writeVarInt(Math.max(0, max));
            buf.writeUtf(stance, 32);
            buf.writeVarInt(Math.max(0, left));
            buf.writeVarInt(Math.max(0, total));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** SkillBar.init (스킬 칸과 함께) */
    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(BarPayload.TYPE, BarPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GaugePayload.TYPE, GaugePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JobUpgrades.FxPayload.TYPE, JobUpgrades.FxPayload.CODEC); // 0.31.0 직업 스킬 강화 화면 연출
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

    /** 스킬 id 로 남은 재사용 대기 (틱 — 시험 · 표시) */
    public static long cooldownLeft(ServerPlayer sp, String skillId) {
        Long until = COOLDOWNS.get(sp.getUUID() + "|" + skillId);
        return until == null ? 0 : Math.max(0, until - Tasks.now());
    }

    /** 시험용: 이 사람의 직업 스킬 재사용 대기를 모두 지움 */
    public static void resetCooldowns(ServerPlayer sp) {
        String p = sp.getUUID() + "|";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(p));
        TOTALS.keySet().removeIf(k -> k.startsWith(p));
    }

    /**
     * 0.28.0 이 사람의 직업 스킬 재사용 대기를 모두 ticks 틱 줄임 (매지션 「주문 연쇄」 — 완드 평타가 맞을 때): 줄어든 칸은 클라이언트에 다시 보냄
     * (전체 틱은 그대로 — 덮개가 그만큼 걷힘). 줄인 칸 수
     */
    public static int reduceCooldowns(ServerPlayer sp, int ticks) {
        if (sp == null || ticks <= 0) return 0;
        String p = sp.getUUID() + "|";
        long now = Tasks.now();
        int n = 0;
        for (var it = COOLDOWNS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!e.getKey().startsWith(p) || e.getValue() <= now) continue;
            long until = Math.max(now, e.getValue() - ticks);
            String id = e.getKey().substring(p.length());
            int total = TOTALS.getOrDefault(e.getKey(), (int) (e.getValue() - now));
            n++;
            if (until <= now) {
                it.remove();
                SkillBarNet.cooldown(sp, CD_PREFIX + id, 0, total);
            } else {
                e.setValue(until);
                SkillBarNet.cooldown(sp, CD_PREFIX + id, (int) (until - now), total);
            }
        }
        return n;
    }

    /** 칸 i 의 키 (Shift + 룬 칸 i 의 키): 그 직업 스킬을 씀. 나갔으면 true */
    /**
     * 잠김 사유 (Skill.lock — "30렙" · "무기 40렙" · "요구 스탯" · 직업 무기 이름) → 알림 · 툴팁 문장: "30렙부터", "든 무기는 40렙부터", "든 무기의 요구 스탯이
     * 모자람", "칼 을(를) 들어야 함" (서버 알림 · 클라이언트 스킬 칸 창이 같이 씀)
     */
    public static String lockText(String lock) {
        if (lock == null || lock.isEmpty()) return "";
        if (lock.startsWith("무기 ") && lock.endsWith("렙")) return "든 무기는 " + lock.substring(3) + "부터";
        if (lock.endsWith("렙")) return lock + "부터";
        if (lock.contains("스탯")) return "든 무기의 요구 스탯이 모자람";
        return lock + " 을(를) 들어야 함";
    }

    /** HUD 칸 아래의 짧은 사유: 해금 레벨 "30렙" 그대로, 든 무기 레벨 · 직업 무기 아님 "무기", 요구 스탯 "스탯" */
    public static String lockShort(String lock) {
        if (lock == null || lock.isEmpty()) return "";
        if (lock.startsWith("무기 ")) return "무기";
        if (lock.endsWith("렙")) return lock;
        return lock.contains("스탯") ? "스탯" : "무기";
    }

    public static boolean press(ServerPlayer sp, int i) {
        if (sp == null || i < 0 || i >= SIZE || sp.isRemoved() || sp.isSpectator() || sp.isDeadOrDying()) return false;
        Skill s = skill(sp, i);
        if (s == null) {
            Header h = header.apply(sp);
            Notify.notify(sp, h == null ? "§7직업 스킬 " + (i + 1) + "번 칸이 비어 있습니다 §8(마을의 직업 교관에게서 직업을 고르면 직업 스킬이 들어갑니다)"
                    : "§7직업 스킬 " + (i + 1) + "번 칸이 비어 있습니다", 30);
            return false;
        }
        String lock = s.lock(sp);
        if (lock != null && !lock.isEmpty()) {
            Notify.notify(sp, "§c" + s.name() + " §7— " + lockText(lock), 30);
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
            TOTALS.put(key(sp, s), cd);
            SkillBarNet.cooldown(sp, CD_PREFIX + s.id(), cd, cd);
        }
        return true;
    }

    /** 칸들을 클라이언트에 (모드가 있는 클라이언트만) — 직업 · 레벨 · 무기가 바뀌면 부름 */
    public static void send(ServerPlayer sp) {
        if (sp == null || sp.isRemoved() || !ServerPlayNetworking.canSend(sp, BarPayload.TYPE)) return;
        ServerPlayNetworking.send(sp, payload(sp));
    }

    /** 지금 칸들 (보낼 것 — JobSystem 이 바뀌었는지 견줌) */
    public static BarPayload payload(ServerPlayer sp) {
        List<String> ids = new ArrayList<>(SIZE);
        List<String> names = new ArrayList<>(SIZE);
        List<ItemStack> icons = new ArrayList<>(SIZE);
        List<String> locks = new ArrayList<>(SIZE);
        List<Integer> ups = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            Skill s = skill(sp, i);
            ids.add(s == null ? "" : s.id());
            names.add(s == null ? "" : s.name());
            icons.add(s == null || s.icon() == null ? ItemStack.EMPTY : s.icon().copy());
            String lock = "";
            int up = 0;
            if (s != null) {
                try {
                    lock = s.lock(sp);
                    up = s.up(sp);
                } catch (RuntimeException e) {
                    Mmorpg.LOG.warn("[mmorpg] job skill lock {}", s.id(), e);
                }
            }
            locks.add(lock == null ? "" : lock);
            ups.add(up);
        }
        Header h = null;
        try {
            h = header.apply(sp);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] job header", e);
        }
        return h == null ? new BarPayload("", "", 0, 0, ids, names, icons, locks, ups)
                : new BarPayload(h.id(), h.name(), h.colorA(), h.colorB(), ids, names, icons, locks, ups);
    }

    /** 0.27.0 직업 자원 게이지를 클라이언트에 */
    public static void sendGauge(ServerPlayer sp, GaugePayload g) {
        if (sp == null || sp.isRemoved() || !ServerPlayNetworking.canSend(sp, GaugePayload.TYPE)) return;
        ServerPlayNetworking.send(sp, g);
    }
}
