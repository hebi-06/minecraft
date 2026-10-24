package mmorpg.fabric.job;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

/**
 * 0.27.0 직업 (사용자 "직업 11종 넣을 거임 … 직업 스킬 구현을 시작하자"): 플레이어의 직업 (Props "rpg:job"), 직업 스킬 칸 (JobSkills.provider — 직업의 스킬
 * 다섯, 칸 i 는 해금 레벨 · 직업 무기 · 요구 스탯이 맞아야 — 아니면 잠김), 직업 위력 P (core.Jobs.power — 룬과 같은 레벨 기본값 × (1 + 적용 스탯 × 2%)),
 * 마검사 「마력 검술」 (RpgCombat.lackOf · powerOf 가 부름 — 칼의 힘 요구를 힘 + 마력으로 · 칼 배율 = 1 + (힘 + 마력 × 0.5) × 2%), 칸이 바뀌면 다시 보냄
 * (직업 · 레벨 · 든 무기 — 5틱마다 견줌), 명령어 /job · /직업.
 */
public final class JobSystem {
    private JobSystem() {}

    /** 플레이어 속성 이름 (직업 id — 없으면 직업 없음) */
    public static final String PROP = "rpg:job";

    /** 직업 스킬 하나의 효과: P = 직업 위력 × 지속 보정, k = 스킬 정의. 나갔으면 true (재사용 대기 시작) */
    @FunctionalInterface
    public interface Impl {
        boolean cast(ServerPlayer sp, Jobs.Job job, Jobs.Skill k, double P);
    }

    private static final Map<String, Impl> IMPL = new HashMap<>();
    /** 마지막으로 보낸 칸 (직업 · 스킬 · 잠김 — 바뀌었을 때만 다시 보냄) */
    private static final Map<UUID, String> SENT = new HashMap<>();
    /** 시전 알림 줄에 덧붙일 말 (0.28.0 — 분노 −30 · 호흡 → 4발: 눈앞 글자 대신 아래 알림 줄에) */
    private static final Map<UUID, String> NOTES = new HashMap<>();

    /** 지금 시전하는 스킬의 알림 줄에 덧붙임 (스킬 효과 안에서 부름) */
    static void note(ServerPlayer sp, String text) {
        NOTES.put(sp.getUUID(), text);
    }

    public static void init() {
        JobSkills.provider = JobSystem::slot;
        JobSkills.header = JobSystem::header;
        JobTraits.init();
        SwordsmanSkills.register();
        SpellbladeSkills.register();
        WarriorSkills.register(); // 0.28.0
        MageSkills.register();
        ArcherSkills.register();
        AssassinSkills.register(); // 0.29.0
        LancerSkills.register();
        ReaperSkills.register();
        PaladinSkills.register(); // 0.30.0
        PriestSkills.register();
        GuardianSkills.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 5 != 0) return;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) refresh(sp, false);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            SENT.remove(handler.player.getUUID());
            JobTraits.sendGauge(handler.player, true);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            SENT.remove(handler.player.getUUID());
            JobTraits.forget(handler.player.getUUID());
            JobUpgrades.forget(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    /** 직업 스킬 효과를 넣음 (스킬 id — jobs.json) */
    public static void register(String skillId, Impl impl) {
        IMPL.put(skillId, impl);
    }

    /** 효과가 들어간 스킬인지 (시험용) */
    public static boolean implemented(String skillId) {
        return IMPL.containsKey(skillId);
    }

    // ------------------------------------------------------------ 직업 기록

    /** 직업 id (없으면 "") */
    public static String jobOf(ServerPlayer sp) {
        String v = Props.get(sp, PROP);
        return v == null ? "" : v;
    }

    /** 직업 (없으면 null) */
    public static Jobs.Job job(ServerPlayer sp) {
        return sp == null ? null : Jobs.job(jobOf(sp));
    }

    /** 직업을 바꿈 (null · 빈 id = 없앰): 자원 · 태세 · 각인 · 직업 스킬 재사용 대기를 지우고 칸을 다시 보냄. 값 · 연출은 JobTrainer */
    public static void setJob(ServerPlayer sp, String id) {
        Jobs.Job j = Jobs.job(id);
        Props.set(sp, PROP, j == null ? null : j.id());
        JobTraits.reset(sp);
        JobSkills.resetCooldowns(sp);
        refresh(sp, true);
    }

    // ------------------------------------------------------------ 마검사 「마력 검술」 (RpgCombat)

    /** 요구 스탯을 따질 때의 찍은 스탯 (마검사 · 칼: 힘 = 힘 + 마력) — 다른 직업 · 무기는 base 그대로 */
    public static Map<String, Double> reqStats(ServerPlayer sp, Weapon w, Map<String, Double> base) {
        Jobs.Job j = job(sp);
        return j == null || w == null ? base : Jobs.reqStats(j, w.type(), base);
    }

    /** 무기 피해 배율을 셀 때의 스탯 (마검사 · 칼: 힘 = 힘 + 마력 × 0.5) — 다른 직업 · 무기는 eff 그대로 */
    public static Map<String, Double> damageStats(ServerPlayer sp, Weapon w, Map<String, Double> eff) {
        Jobs.Job j = job(sp);
        return j == null || w == null ? eff : Jobs.damageStats(j, w.type(), eff);
    }

    // ------------------------------------------------------------ 위력 · 칸

    /** 직업 위력 P (지속 보정 전 — 유효 스탯 · 최고 레벨) */
    public static double power(ServerPlayer sp, Jobs.Job j) {
        return Jobs.power(j, RpgStats.effective(sp));
    }

    /** 재사용 대기 틱 = 초 × 20 × 스탯 트리 재사용 대기 (무기 스킬 · 룬과 같음) */
    public static int cooldownTicks(ServerPlayer sp, Jobs.Skill k) {
        return (int) Math.round(k.cd() * 20 * mmorpg.fabric.stattree.StatTrees.cdMult(sp));
    }

    /** 칸의 잠김 사유 ("" = 쓸 수 있음): 해금 레벨 ("15렙") → 직업 무기 ("칼 · 마검") → 그 무기의 요구 스탯 ("요구 스탯") */
    public static String lockOf(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        int need = Jobs.unlockLevel(k.slot());
        if (RpgCombat.reqLevelOf(sp) < need) return need + "렙";
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        if (w == null || !j.usesWeapon(w.type())) return j.weaponText();
        List<mmorpg.core.Weapons.Lack> lack = RpgCombat.lackOf(sp, w);
        for (mmorpg.core.Weapons.Lack l : lack) if (l.isLevel()) return "무기 " + Js.str(l.value()) + "렙"; // 든 무기의 레벨이 모자람
        if (!lack.isEmpty()) return "요구 스탯";
        return "";
    }


    /**
     * 칸 그림: 0.27.0 부터 비움 — 직업 스킬은 룬 그림이 아니라 클라이언트가 스킬 id 로 그리는 메이플식 스킬 아이콘 (GUI 그림 job_skill/&lt;스킬 id&gt;,
     * tools/job_skill_icons.py)
     */
    static ItemStack icon(Jobs.Job j, int slot) {
        return ItemStack.EMPTY;
    }

    /** 칸 하나 (provider 가 부를 때마다 새로 — 재사용 대기는 그때의 스탯 트리) */
    private record Slot(Jobs.Job job, Jobs.Skill k, Impl impl, int cd) implements JobSkills.Skill {
        @Override
        public String id() {
            return k.id();
        }

        @Override
        public String name() {
            return k.name();
        }

        @Override
        public ItemStack icon() {
            return JobSystem.icon(job, k.slot());
        }

        @Override
        public int cooldown() {
            return cd;
        }

        @Override
        public String lock(ServerPlayer sp) {
            return lockOf(sp, job, k);
        }

        @Override
        public int up(ServerPlayer sp) {
            return JobUpgrades.level(sp, k.id());
        }

        @Override
        public boolean cast(ServerPlayer sp) {
            String lock = lockOf(sp, job, k);
            if (!lock.isEmpty()) {
                Notify.notify(sp, "§c" + k.name() + " §7— " + JobSkills.lockText(lock), 30);
                return false;
            }
            // 0.26.0 지속 보정: 시간이 걸리거나 장판인 스킬은 위력 × (1 + 5 ~ 15%) — 룬과 같은 단계 (Jobs.spreadMult)
            // 0.28.0 매지션 「주문 연쇄」: 4초 안에 다른 직업 스킬이면 위력 × (1 + 연쇄 × 12%)
            int chain = JobTraitsMore.chainNext(sp, job, k);
            // 0.29.0 창술사 「창끝」: 앞으로 움직이며 창 평타를 맞히면 5초 안의 다음 직업 스킬 위력 × (1 + 10%)
            double boost = JobTraits3.castMult(sp, job);
            // 0.30.0 사제 「신앙」: 공격 직업 스킬 (성가 · 새벽의 종) 은 신앙 하나당 위력 × (1 + 10%) — 나가면 모두 씀
            double faith = JobTraits4.castMult(sp, job, k);
            int faithN = JobTraits4.faith(sp);
            // 0.31.0 직업 스킬 강화: 위력 × (1 + 0.1 × 단계) — 모든 타격 (회복 · 보호막 · 태세 값은 JobUpgrades.num 으로 읽는 곳에서)
            int up = JobUpgrades.level(sp, k.id());
            double P = power(sp, job) * Jobs.spreadMult(k) * JobTraitsMore.chainMult(job, chain) * boost * faith * Jobs.upMult(up);
            NOTES.remove(sp.getUUID());
            JobTraits3.beforeCast(sp, job, k); // 0.29.0 암영 중 직업 스킬: 첫 타 강화를 걸어 둠 (나가면 은신이 풀림)
            boolean ok = impl.cast(sp, job, k, P);
            JobTraits3.afterCast(sp, job, k, ok);
            String note = NOTES.remove(sp.getUUID());
            if (ok) {
                mmorpg.fabric.motion.MotionSystem.skill(sp, k.id()); // 0.38.0 직업 스킬 동작 (MOTION.skills)
                JobTraitsMore.chainCommit(sp, job, k, chain);
                if (boost > 1) JobTraits3.castCommit(sp, job);
                JobTraits4.castCommit(sp, job, k);
                String chainText = chain > 0 ? " §e· 연쇄 ×" + chain + " +" + Math.round((JobTraitsMore.chainMult(job, chain) - 1) * 100) + "%" : "";
                String boostText = boost > 1 ? " §6· 창끝 +" + Math.round((boost - 1) * 100) + "%" : "";
                String faithText = faith > 1 ? " §e· 신앙 ×" + faithN + " +" + Math.round((faith - 1) * 100) + "%" : "";
                String powerText = k.total() > 0 ? " §7(위력 " + Js.str(Math.round(P * 10) / 10.0) + chainText + boostText + faithText + "§7)" : "";
                Notify.notify(sp, "§f" + job.name() + " §8· §b" + k.name() + (up > 0 ? " §6+" + up : "") + powerText + (note != null ? " §8· " + note : ""), 24);
            }
            return ok;
        }
    }

    /** JobSkills.provider: 직업 (스킬이 들어간 직업만) 의 칸 i 스킬 — 잠김이어도 칸에는 보임 (누르면 사유) */
    static JobSkills.Skill slot(ServerPlayer sp, int i) {
        Jobs.Job j = job(sp);
        if (j == null || !j.ready()) return null;
        Jobs.Skill k = j.skill(i);
        if (k == null || k.id().isEmpty()) return null;
        Impl impl = IMPL.get(k.id());
        if (impl == null) return null;
        return new Slot(j, k, impl, cooldownTicks(sp, k));
    }

    /** JobSkills.header: 칸 위 직업 이름 · 색 */
    static JobSkills.Header header(ServerPlayer sp) {
        Jobs.Job j = job(sp);
        return j == null ? null : new JobSkills.Header(j.id(), j.name(), Jobs.rgb(j.color()[0]), Jobs.rgb(j.color()[1]));
    }

    /** 칸이 바뀌었으면 (또는 force) 클라이언트에 다시 보냄 */
    public static void refresh(ServerPlayer sp, boolean force) {
        if (sp == null || sp.isRemoved()) return;
        String sig = signature(sp);
        if (!force && sig.equals(SENT.get(sp.getUUID()))) return;
        SENT.put(sp.getUUID(), sig);
        JobSkills.send(sp);
        JobTraits.sendGauge(sp, force);
    }

    private static String signature(ServerPlayer sp) {
        StringBuilder b = new StringBuilder(jobOf(sp));
        for (int i = 0; i < JobSkills.SIZE; i++) {
            JobSkills.Skill s = JobSkills.skill(sp, i);
            b.append('|');
            if (s != null) b.append(s.id()).append(':').append(s.lock(sp)).append('+').append(s.up(sp)); // 0.31.0 강화 단계 (칸 배지)
        }
        return b.toString();
    }

    // ------------------------------------------------------------ 명령어 (/job · /직업)

    static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /**
     * /job — 내 직업 · 칸 (잠김 · 위력), 운영자: /job set &lt;직업&gt; [대상] (값 없이 — 시험 · 관리), /job clear [대상], /job menu (전직 창 — 직업 교관 없이)
     */
    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("job", "직업")) {
            LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").requires(JobSystem::admin);
            for (Jobs.Job j : Jobs.all()) {
                for (String word : List.of(j.id(), j.name())) {
                    set.then(Commands.literal(word).executes(c -> setCmd(c, List.of(c.getSource().getPlayerOrException()), j))
                            .then(Commands.argument("targets", EntityArgument.players()).executes(c -> setCmd(c, EntityArgument.getPlayers(c, "targets"), j))));
                }
            }
            d.register(Commands.literal(name)
                    .executes(c -> info(c.getSource().getPlayerOrException()))
                    .then(set)
                    .then(Commands.literal("clear").requires(JobSystem::admin)
                            .executes(c -> setCmd(c, List.of(c.getSource().getPlayerOrException()), null))
                            .then(Commands.argument("targets", EntityArgument.players()).executes(c -> setCmd(c, EntityArgument.getPlayers(c, "targets"), null))))
                    .then(Commands.literal("menu").requires(JobSystem::admin).executes(c -> {
                        JobTrainer.open(c.getSource().getPlayerOrException(), null);
                        return 1;
                    }))
                    .then(JobUpgrades.command())); // 0.31.0 직업 스킬 강화 (/job up)
        }
    }

    private static int setCmd(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets, Jobs.Job j) throws CommandSyntaxException {
        for (ServerPlayer sp : targets) {
            setJob(sp, j == null ? null : j.id());
            Notify.say(sp, j == null ? "§7직업을 지웠습니다." : "§f직업: §b" + j.name() + " §7(" + j.weaponText() + " · " + j.statText() + ")");
        }
        String what = j == null ? "직업 없음" : j.name();
        c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(targets.size() + "명 → " + what), true);
        return targets.size();
    }

    /** /job: 내 직업 · 칸 다섯 (해금 레벨 · 잠김 · 위력) */
    private static int info(ServerPlayer sp) {
        Jobs.Job j = job(sp);
        if (j == null) {
            Notify.say(sp, "§7직업이 없습니다. §f" + Jobs.changeLevel() + "렙§7부터 마을의 §b직업 교관§7에게서 직업을 고를 수 있습니다.");
            return 1;
        }
        double P = power(sp, j);
        Notify.say(sp, "§f직업 §b" + j.name() + " §7— " + j.role() + " · 무기 " + j.weaponText() + " · 적용 스탯 " + j.statText() + " · 위력 P §f"
                + String.format(Locale.ROOT, "%.1f", P));
        for (int i = 0; i < JobSkills.SIZE; i++) {
            Jobs.Skill k = j.skill(i);
            if (k == null) continue;
            String lock = lockOf(sp, j, k);
            String key = "⇧" + "RZVBY".charAt(i);
            int up = JobUpgrades.level(sp, k.id());
            Notify.say(sp, " §e" + key + " §f" + Jobs.skillLine(k) + (up > 0 ? " §6+" + up : "") + (lock.isEmpty() ? " §a준비" : " §c잠김 (" + JobSkills.lockText(lock) + ")"));
        }
        return 1;
    }
}
