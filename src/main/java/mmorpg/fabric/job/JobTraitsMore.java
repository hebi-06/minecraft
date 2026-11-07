package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.28.0 전사 · 매지션 · 궁수 특성 (JobTraits 가 부름 — 자원 · 태세는 JobTraits 와 같은 틀):
 * <ul>
 * <li>전사 「분노」 (0 ~ 100): 도끼 평타가 맞으면 +onHit · 맞으면 +onHurt, idle 틱 동안 싸우지 않으면 1초에 decay 씩 줄어듦 (투혼 동안은 그대로).
 * 0.51.0 각성 워로드 도 같은 분노 — 다 차면 곰의 화신 (JobTraits6: 그동안 분노가 줄어 끝나면 0 · 쌓이지 않음 · 평타 + fullBonus).
 * half 이상이면 도끼 평타 피해 + halfBonus, 다 차면 + fullBonus (RpgCombat.basicMult). 파쇄격의 방어 깨짐 (맞는 쪽 배율 — RpgCombat.takenMult).
 * 투혼: 체력이 low 아래로 떨어지면 한 번 heal 회복 (RpgHealth.lossHook)</li>
 * <li>매지션 「주문 연쇄」: 직업 스킬을 쓰고 window 틱 안에 다른 직업 스킬을 쓰면 연쇄 +1 (최대 max — 하나당 피해 + perChain, JobSystem 이 위력에 곱함).
 * 0.51.0 각성 아크메이지 도 같은 연쇄 (최대 5 — 이은 스킬마다 별 · 다섯째에 별자리 완성: JobTraits8 · 이어지는 틱: JobTraits8.chainWindow).
 * 완드 평타가 맞으면 직업 스킬 재사용 대기 wandCut 틱 줄어듦 (한 발 — 같은 틱 — 에 한 번). 마나 과부하 (태세): 완드 평타 사거리 + · 관통 광선 · 공격 간격 ×
 * cdScale, 끝나면 이동 느려짐</li>
 * <li>궁수 「호흡」 (0 ~ 5): 움직이지 않고 활을 쏘면 +1, 움직이는 동안 decay 틱마다 하나씩 줄어듦. 하나당 활 평타 피해 + perStack · 사거리 + rangePer.
 * 바람 타기: 다음 활 평타 두 번이 각각 한 발 더 (RpgCombat.extraArrow — 평타와 같은 피해), 그동안 호흡이 줄지 않음. 정조준 (태세): 호흡 5 · 줄지 않음 · 활 평타
 * 관통 · 치명타 + crit</li>
 * </ul>
 */
public final class JobTraitsMore {
    private JobTraitsMore() {}

    static final class State {
        // 전사
        int rage;
        long lastCombat = -1000;
        boolean spiritHealed;
        // 매지션
        int chain;
        long lastCast = -1000;
        String lastSkill = "";
        long lastCut = -1;
        // 궁수
        int breath;
        long lastMove = -1000;
        Vec3 lastPos;
        int moving;
        long windUntil;
        int windShots;
        /** 0.51.0 스나이퍼 급소 사격: 호흡 8 에서 쏜 활 평타의 화살이 닿는 동안 (치명타 확정 — RpgCombat.sureCrit) */
        long snipeUntil;
        /** 2026-11-07 와일드 헌트: 마지막으로 쏜 활 평타가 겨눈 적 (그 적을 맞히면 늑대 정령이 물어뜯음 — 한 발에 한 번) */
        LivingEntity huntTarget;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 파쇄격 방어 깨짐: 적 → {적, 끝 틱, 받는 피해 +} */
    private record Broken(LivingEntity e, long until, double add) {}

    private static final Map<UUID, Broken> BREAKS = new HashMap<>();

    static void init() {
        java.util.function.ToDoubleBiFunction<ServerPlayer, Weapon> bm = RpgCombat.basicMult;
        RpgCombat.basicMult = (p, w) -> bm.applyAsDouble(p, w) * basicMult(p, w);
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * breakMult(e);
        RpgHealth.LossHook lh = RpgHealth.lossHook;
        RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            onLoss(sp, src, loss, before, after);
        };
        RpgCombat.rangedMod = new RpgCombat.RangedMod() {
            @Override
            public double range(ServerPlayer sp, Weapon w) {
                return rangeAdd(sp, w);
            }

            @Override
            public double pierce(ServerPlayer sp, Weapon w) {
                return pierceWidth(sp, w);
            }

            @Override
            public void fired(ServerPlayer sp, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
                onFired(sp, w, target, from, dir, dist);
            }
        };
        RpgCombat.addWeaponHitHook(JobTraitsMore::onWeaponHit);
        SpiritWolves.init(); // 2026-11-07 와일드 헌트의 늑대 정령
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            BREAKS.clear();
        });
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    static void reset(ServerPlayer sp) {
        STATES.remove(sp.getUUID());
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st == null) return;
        st.rage = 0;
        st.chain = 0;
        st.breath = 0;
        st.windShots = 0;
        st.windUntil = 0;
        st.snipeUntil = 0;
        st.spiritHealed = false;
    }

    private static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && j.id().equals(id) ? j : null;
    }

    private static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    /** 0.51.0 주문 연쇄를 쓰는 직업 (매지션 · 각성 아크메이지 — 반전 네크로맨서 는 망자의 군대) */
    static boolean chainJob(Jobs.Job j) {
        return j != null && (j.id().equals("mage") || j.id().equals(JobTraits8.AM));
    }

    /** 0.51.0 호흡을 쓰는 직업 (궁수 · 각성 스나이퍼 — 반전 배틀 아처 는 난사, JobTraits9) */
    static boolean breathJob(Jobs.Job j) {
        return j != null && (j.id().equals("archer") || j.id().equals(JobTraits9.SN));
    }

    /** 0.51.0 분노를 쓰는 직업 (전사 · 각성 워로드) */
    static boolean rageJob(Jobs.Job j) {
        return j != null && (j.id().equals("warrior") || j.id().equals(JobTraits6.WL));
    }

    private static Jobs.Skill skill(Jobs.Job j, String id) {
        return j == null ? null : j.skill(id);
    }

    // ------------------------------------------------------------ 전사 「분노」

    /** 지금 분노 */
    public static int rage(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.rage;
    }

    /** 시험용: 분노를 정함 */
    public static void setRage(ServerPlayer sp, int n) {
        State st = of(sp);
        st.rage = Math.max(0, Math.min(100, n));
        st.lastCombat = Tasks.now();
        JobTraits.sendGauge(sp, false);
    }

    /** 분노 n 이 있으면 써서 true */
    static boolean useRage(ServerPlayer sp, int n) {
        State st = of(sp);
        if (st.rage < n) return false;
        if (!JobTraits.stanceOn(sp, "warrior_spirit")) st.rage -= n; // 투혼 동안은 줄지 않음
        st.lastCombat = Tasks.now();
        JobTraits.sendGauge(sp, false);
        return true;
    }

    /** 분노 더하기 (최대 100) — 50 · 100 을 넘으면 손에서 불꽃. 0.51.0 워로드: 곰의 화신 동안은 쌓이지 않음 · 다 차면 곰의 화신 */
    static void gainRage(ServerPlayer sp, Jobs.Job j, int n) {
        State st = of(sp);
        boolean warlord = j.id().equals(JobTraits6.WL);
        if (warlord && JobTraits6.bearOn(sp)) {
            st.lastCombat = Tasks.now();
            return;
        }
        int max = j.trait() == null ? 100 : Math.max(1, j.trait().max());
        int before = st.rage;
        st.rage = Math.min(max, st.rage + n);
        st.lastCombat = Tasks.now();
        int half = (int) trait(j, "half", 50);
        if (warlord && before < max && st.rage >= max) {
            JobTraits6.bearStart(sp, j, (int) trait(j, "bearFor", 160));
            JobTraits.sendGauge(sp, false);
            return;
        }
        if ((before < half && st.rage >= half) || (before < max && st.rage >= max)) {
            ServerLevel level = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            boolean full = st.rage >= max;
            JobFx.burst(level, V.up(V.add(h.hand(), h.f(), 0.6), 0.3), full ? 0.45 : 0.32, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.sparks(level, h.hand(), JobKit.glow(j), full ? 10 : 6, 3);
            SkillFx.play(level, full ? "mob.ravager.roar" : "fire.ignite", sp.position(), full ? 1.4 : 1.2, full ? 0.5 : 0.8);
            if (full) Notify.notify(sp, "§6§l분노 최고조 §7— 도끼 평타 +" + Math.round(trait(j, "fullBonus", 0.2) * 100) + "%", 30);
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 투혼 시작: 분노 가득 · 회복 한 번 다시 */
    static void spiritStart(ServerPlayer sp, Jobs.Job j) {
        State st = of(sp);
        st.rage = j.trait() == null ? 100 : Math.max(1, j.trait().max());
        st.lastCombat = Tasks.now();
        st.spiritHealed = false;
        JobTraits.sendGauge(sp, false);
    }

    /** 도끼 평타 배율: 분노 half 이상 + halfBonus, 다 차면 + fullBonus · 궁수 활: 1 + 호흡 × perStack */
    static double basicMult(ServerPlayer sp, Weapon w) {
        if (w == null) return 1;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return 1;
        State st = STATES.get(sp.getUUID());
        if (st == null) return 1;
        if (rageJob(j) && "axe".equals(w.type())) {
            int max = j.trait() == null ? 100 : Math.max(1, j.trait().max());
            if (j.id().equals(JobTraits6.WL) && JobTraits6.bearOn(sp)) return 1 + trait(j, "fullBonus", 0.2); // 0.51.0 곰의 화신 (분노가 줄어도)
            if (st.rage >= max) return 1 + trait(j, "fullBonus", 0.2);
            if (st.rage >= trait(j, "half", 50)) return 1 + trait(j, "halfBonus", 0.1);
            return 1;
        }
        if (breathJob(j) && "bow".equals(w.type())) return 1 + st.breath * trait(j, "perStack", 0.04); // 0.51.0 스나이퍼도 (최대 8)
        return 1;
    }

    /** 파쇄격 방어 깨짐: ticks 틱 동안 이 적이 받는 피해 + add (다시 걸면 새로) */
    static void applyBreak(LivingEntity e, double add, int ticks) {
        if (e == null || e.isRemoved()) return;
        BREAKS.put(e.getUUID(), new Broken(e, Tasks.now() + ticks, add));
    }

    /** 이 적의 받는 피해 배율 (방어 깨짐이 걸렸으면 1 + add) */
    public static double breakMult(LivingEntity e) {
        if (e == null || BREAKS.isEmpty()) return 1;
        Broken b = BREAKS.get(e.getUUID());
        return b == null || b.until() <= Tasks.now() ? 1 : 1 + b.add();
    }

    /** 맞음 (RpgHealth.lossHook): 전사 — 몹 · 플레이어에게 맞으면 분노 + onHurt, 투혼 동안 체력이 low 아래로 떨어지면 한 번 회복 */
    private static void onLoss(ServerPlayer sp, DamageSource src, double loss, double before, double after) {
        Jobs.Job j = JobSystem.job(sp);
        if (!rageJob(j)) return;
        if (src.getEntity() instanceof LivingEntity from && from != sp) gainRage(sp, j, (int) trait(j, "onHurt", 5));
        Jobs.Skill k = skill(j, "warrior_spirit");
        State st = of(sp);
        double max = RpgHealth.maxOf(sp);
        if (k == null || st.spiritHealed || !JobTraits.stanceOn(sp, k.id()) || !(max > 0) || after >= max * k.num("low", 0.3) || after <= 0) return;
        st.spiritHealed = true;
        double heal = max * JobUpgrades.num(sp, k, "heal", 0.2); // 0.31.0 강화
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double got = RpgHealth.healBy(sp, heal);
            if (got > 0) DamageNumbers.show(sp, got, DamageNumbers.Style.HEAL, "", 0);
            ServerLevel level = sp.level();
            JobFx.aura(sp, j, 3.0, 0.9, 1.0);
            JobFx.ring(level, V.up(sp.position(), 0.06), 2.6, j, false, 0.5, Opt.empty());
            JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
            SkillFx.sparks(level, V.up(sp.position(), 1.0), JobKit.glow(j), 14, 5);
            SkillFx.play(level, "random.totem", sp.position(), 1.3, 0.6);
            SkillFx.play(level, "mob.ravager.roar", sp.position(), 1.2, 0.8);
        });
    }

    // ------------------------------------------------------------ 매지션 「주문 연쇄」

    /** 지금 연쇄 (끝났으면 0) */
    public static int chain(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return 0;
        Jobs.Job j = JobSystem.job(sp);
        return j != null && Tasks.now() - st.lastCast <= JobTraits8.chainWindow(sp, j) ? st.chain : 0;
    }

    /** 시험용: 연쇄를 정함 (마지막 시전 = 지금 · 스킬 이름은 비움 — 다음 스킬이 이어짐) */
    public static void setChain(ServerPlayer sp, int n) {
        State st = of(sp);
        st.chain = Math.max(0, n);
        st.lastCast = n > 0 ? Tasks.now() : -1000;
        st.lastSkill = "";
        JobTraits8.clearStars(sp); // 0.51.0 아크메이지의 별도 처음부터
        JobTraits.sendGauge(sp, false);
    }

    /** 이번 시전의 연쇄 (매지션이 아니면 0): window 안에 다른 스킬이면 지금 + 1 (최대 max), 아니면 0 · 같은 스킬이면 그대로 */
    static int chainNext(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (!chainJob(j)) return 0;
        State st = of(sp);
        if (Tasks.now() - st.lastCast > JobTraits8.chainWindow(sp, j)) return 0;
        if (k.id().equals(st.lastSkill)) return st.chain;
        return Math.min(Math.max(1, j.trait().max()), st.chain + 1);
    }

    /** 연쇄 피해 배율 (1 + 연쇄 × perChain) */
    static double chainMult(Jobs.Job j, int chain) {
        return chain <= 0 ? 1 : 1 + chain * trait(j, "perChain", 0.12);
    }

    /** 시전이 나감: 연쇄 · 마지막 스킬 · 틱을 기록 (연쇄가 붙으면 글자 · 금빛 고리). 0.51.0 아크메이지 — 별 · 별자리 완성이면 연쇄를 처음부터 */
    static void chainCommit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, int chain) {
        if (!chainJob(j)) return;
        State st = of(sp);
        st.chain = chain;
        st.lastCast = Tasks.now();
        st.lastSkill = k.id();
        if (chain > 0) {
            // 연쇄 글자는 시전 알림 줄에 (JobSystem — "연쇄 ×n +x%"), 여기서는 완드 끝 금빛 고리 · 소리만
            Vec3 tip = RpgCombat.circleAt(sp)[0];
            JobFx.ringFacing(sp.level(), tip, 0.22 + 0.08 * chain, j, V.scale(sp.getViewVector(1F), -1), 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.2 + chain * 0.2, 0.8);
        }
        if (JobTraits8.commit(sp, j, k, chain)) st.chain = 0; // 0.51.0 아크메이지: 다섯째 별 → 별자리 완성 (연쇄는 처음부터 — 마지막 스킬 · 틱은 그대로라 다음 스킬이 연쇄 1)
        JobTraits.sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 궁수 「호흡」

    /** 지금 호흡 */
    public static int breath(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.breath;
    }

    /** 시험용: 호흡을 정함 (0 ~ 지금 직업의 최대 — 궁수 5 · 0.51.0 스나이퍼 8) */
    public static void setBreath(ServerPlayer sp, int n) {
        State st = of(sp);
        Jobs.Job j = JobSystem.job(sp);
        int max = j != null && breathJob(j) && j.trait() != null ? Math.max(1, j.trait().max()) : 5;
        st.breath = Math.max(0, Math.min(max, n));
        st.moving = 0;
        JobTraits.sendGauge(sp, false);
    }

    /** 0.51.0 스네어 샷 (스나이퍼): 내려앉으며 호흡 + n (최대까지) */
    static void addBreath(ServerPlayer sp, int n) {
        State st = of(sp);
        Jobs.Job j = JobSystem.job(sp);
        int max = j != null && breathJob(j) && j.trait() != null ? Math.max(1, j.trait().max()) : 5;
        st.breath = Math.max(0, Math.min(max, st.breath + n));
        JobTraits.sendGauge(sp, false);
    }

    /** 0.51.0 하트시커 (스나이퍼): 호흡 n 씀 — 쓴 만큼 (모자라면 있는 만큼) */
    static int useBreath(ServerPlayer sp, int n) {
        State st = of(sp);
        int used = Math.max(0, Math.min(st.breath, n));
        if (used > 0) {
            st.breath -= used;
            JobTraits.sendGauge(sp, false);
        }
        return used;
    }

    /** 0.51.0 급소 사격 중인지 (호흡 8 에서 쏜 활 평타의 화살이 닿는 동안 — 치명타 확정, RpgCombat.sureCrit) */
    static boolean snipeCrit(ServerPlayer sp, Weapon w) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.snipeUntil >= Tasks.now() && w != null && "bow".equals(w.type()) && jobIf(sp, JobTraits9.SN) != null;
    }

    /** 바람 타기의 남은 덧쏘기 */
    public static int windShots(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || st.windUntil <= Tasks.now() ? 0 : st.windShots;
    }

    /** 바람 타기: shots 번 덧쏘기 · ticks 틱 동안 호흡이 줄지 않음 */
    static void windStart(ServerPlayer sp, int shots, int ticks) {
        State st = of(sp);
        st.windShots = shots;
        st.windUntil = Tasks.now() + ticks;
    }

    /** 시험: 와일드 헌트의 늑대 정령 수 (2026-11-07) */
    public static int huntWolves(ServerPlayer sp) {
        return SpiritWolves.bodies(sp).size();
    }

    /** 정조준 시작: 호흡 가득 */
    static void focusStart(ServerPlayer sp, Jobs.Job j) {
        State st = of(sp);
        st.breath = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        st.moving = 0;
        JobTraits.sendGauge(sp, false);
    }

    private static boolean breathHeld(ServerPlayer sp, State st) {
        return st.windUntil > Tasks.now() || JobTraits.stanceOn(sp, "archer_focus");
    }

    // ------------------------------------------------------------ 활 · 완드 평타 (RpgCombat.RangedMod · 훅)

    /** 사거리 +: 궁수 활 = 호흡 × rangePer (0.51.0 매지션 4번 칸이 프리즘 레이 — 완드 평타 강화는 뺌) */
    static double rangeAdd(ServerPlayer sp, Weapon w) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return 0;
        if (breathJob(j) && "bow".equals(w.type())) return breath(sp) * trait(j, "rangePer", 1);
        return 0;
    }

    /** 관통 폭: 궁수 활 (정조준) · 스나이퍼 */
    static double pierceWidth(ServerPlayer sp, Weapon w) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return 0;
        if (j.id().equals("archer") && "bow".equals(w.type()) && JobTraits.stanceOn(sp, "archer_focus")) {
            Jobs.Skill k = skill(j, "archer_focus");
            return k == null ? 0.6 : k.num("width", 0.6);
        }
        if (j.id().equals(JobTraits9.SN) && "bow".equals(w.type())) {
            // 0.51.0 스나이퍼: 호흡 8 (급소 사격) 이면 화살이 꿰뚫음
            State st = STATES.get(sp.getUUID());
            int max = j.trait() == null ? 8 : Math.max(1, j.trait().max());
            if (st != null && st.breath >= max) return trait(j, "snipeWidth", 0.6);
        }
        return 0;
    }

    /**
     * 쏨: 궁수 — 움직이지 않았으면 교감 +1 (둘레의 정령빛 하나가 활로 날아듦), 교감이 가득이면 화살이 늑대 정령의 모습으로 날아감 (2026-11-07 정령 궁수 — 스나이퍼의 급소
     * 사격도), 바람 타기가 남았으면 follow 틱 뒤 한 발 더 (평타와 같은 피해 · 빛의 화살) / 와일드 헌트면 화살 끝 금빛 · 겨눈 적을 늑대가 노림
     */
    private static void onFired(ServerPlayer sp, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return;
        ServerLevel level = sp.level();
        long now = Tasks.now();
        if (breathJob(j) && "bow".equals(w.type())) {
            State st = of(sp);
            int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
            if (j.id().equals(JobTraits9.SN) && st.breath >= max) {
                // 0.51.0 스나이퍼 급소 사격: 교감 8 에서 쏜 화살 — 닿는 동안 치명타 확정 · 꿰뚫음 (pierceWidth), 교감 snipeUse 씀 · 늑대 정령의 모습으로 날아감
                st.snipeUntil = now + (long) trait(j, "snipeFor", 18);
                st.breath = Math.max(0, st.breath - (int) trait(j, "snipeUse", 3));
                JobTraits.sendGauge(sp, false);
                JobTraits9.snipeFx(sp, j, from, dir, dist);
                spiritArrow(sp, j, dir, dist);
            } else {
                if (now - st.lastMove > 4 && st.breath < max) {
                    st.breath++;
                    communionGain(sp, j, st.breath, max);
                    JobTraits.sendGauge(sp, false);
                }
                if (j.id().equals("archer") && st.breath >= max) spiritArrow(sp, j, dir, dist);
            }
            if (j.id().equals("archer") && JobTraits.stanceOn(sp, "archer_focus")) st.huntTarget = target;
            if (JobTraits.stanceOn(sp, "archer_focus")) {
                RuneKit.rfx(level, "prop", V.add(RuneKit.bowHand(sp), dir, 0.4), Opt.of("size", 0.16, "life", 0.25, "cell", (double) RuneKit.RFX.get("glint")[0], "spin",
                        20.0, "spinRate", 200.0, "color", JobKit.core(j), "grow", 0.6, "fade", 0.5, "vel", RuneKit.bodyVel(sp)));
            }
            if (target != null && st.windShots > 0 && st.windUntil > now) {
                st.windShots--;
                Jobs.Skill k = skill(j, "archer_wind");
                int follow = k == null ? 10 : (int) k.num("follow", 10);
                String life = RuneKit.lifeToken(sp);
                RuneKit.later(follow, () -> {
                    if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) return;
                    Weapon held = RpgCombat.weaponOf(sp.getMainHandItem());
                    if (held == null || !held.id().equals(w.id())) return;
                    Vec3 hand = RuneKit.bowHand(sp);
                    RpgCombat.extraArrow(sp, target, w, JobUpgrades.num(sp, k, "extra", 1)); // 0.31.0 강화: 덧쏘기 피해 비율
                    JobFx.arrow(sp.level(), hand, Kit.bodyPoint(target), j, 60, 0.7, Opt.empty());
                    JobFx.wind(sp.level(), V.up(sp.position(), 0.08), 0.9, j, 0.35, Opt.of("alpha", 0.8));
                });
            }
            return;
        }
        if (j.id().equals(JobTraits9.BA) && "bow".equals(w.type())) {
            JobTraits9.onFired(sp, j, w, target, from, dir, dist); // 0.51.0 배틀 아처 난사: 움직이며 쏘면 세 갈래
            return;
        }
    }

    /** 무기로 맞힘: 전사 도끼 평타 → 분노 + onHit · 매지션 완드 평타 → 직업 스킬 재사용 대기 − wandCut (한 발에 한 번) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return;
        if (j.id().equals("archer") && "bow".equals(w.type()) && !RpgCombat.bonusHitting() && JobTraits.stanceOn(sp, "archer_focus")) {
            // 2026-11-07 와일드 헌트: 활 평타가 겨눈 적을 맞힘 → 늑대 정령이 그 적에게 뛰어올라 물어뜯음 (평타 × bite — 한 발에 한 번)
            State st = STATES.get(sp.getUUID());
            if (st != null && st.huntTarget == victim) {
                st.huntTarget = null;
                Jobs.Skill k = skill(j, "archer_focus");
                SpiritWolves.bite(sp, j, victim, w, k == null ? 0.4 : JobUpgrades.num(sp, k, "bite", 0.4)); // 강화
            }
            return;
        }
        if (rageJob(j) && "axe".equals(w.type()) && Combat.isMeleeWeapon(w)) {
            gainRage(sp, j, (int) trait(j, "onHit", 8));
            return;
        }
        if (chainJob(j) && "wand".equals(w.type())) { // 0.51.0 아크메이지도 (같은 wandCut)
            State st = of(sp);
            long now = Tasks.now();
            if (st.lastCut == now) return;
            st.lastCut = now;
            if (JobSkills.reduceCooldowns(sp, (int) trait(j, "wandCut", 6)) > 0) {
                Vec3 tip = RpgCombat.circleAt(sp)[0];
                SkillFx.fx(sp.level(), "rpg:arrow_spark", tip, Opt.of("dir", V.UP, "speed", 1.2, "size", 0.12, "grow", 0.8, "life", 0.3, "color", JobKit.core(j),
                        "alpha", 1.0));
            }
        }
    }

    // ------------------------------------------------------------ 2026-11-07 정령 교감 (정령 궁수 — 궁수 · 스나이퍼의 호흡이 이름 · 그림만 바뀜)

    /** 정령빛 (연두) · 금빛 */
    private static final double[] SPIRIT = {0.55, 1.0, 0.42};
    private static final double[] SPIRIT_GOLD = {1.0, 0.86, 0.36};

    /** 교감 +1: 둘레에서 정령빛 하나가 활로 날아듦 (1인칭에도) · 가득이면 맑은 소리 · 발밑 고리 */
    private static void communionGain(ServerPlayer sp, Jobs.Job j, int n, int max) {
        ServerLevel level = sp.level();
        Vec3 hand = RuneKit.bowHand(sp);
        double a = Kit.rand() * Math.PI * 2;
        Vec3 p0 = new Vec3(hand.x + Math.cos(a) * 1.1, hand.y + 0.5 + Kit.rand() * 0.4, hand.z + Math.sin(a) * 1.1);
        Vec3 d = V.sub(hand, p0);
        SkillFx.fx(level, "rpg:arrow_spark", p0, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.22, "size", 0.13, "grow", 0.4, "life", 0.22, "color", n % 2 == 0
                ? SPIRIT_GOLD : SPIRIT, "alpha", 1.0));
        RuneKit.rfx(level, "prop", hand, Opt.of("size", 0.1 + 0.02 * n, "life", 0.3, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 15.0, "spinRate", 220.0,
                "color", SPIRIT_GOLD, "grow", 0.5, "fade", 0.5, "vel", RuneKit.bodyVel(sp)));
        if (n == max) {
            SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.8, 0.6);
            JobFx.ring(level, V.up(sp.position(), 0.06), 1.2, j, false, 0.4, JobFx.hide1p(sp).with("alpha", 0.8));
        }
    }

    /** 둘레를 도는 정령빛 (교감 수만큼 — 4틱마다, 3인칭 · 다른 사람: 1인칭에서는 화면을 가리지 않게 숨김) */
    private static void communionFx(ServerPlayer sp, Jobs.Job j, int n, long now) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Opt view = JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp));
        double base = now * 0.16;
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        for (int i = 0; i < n; i++) {
            double a = base + i * Math.PI * 2 / Math.max(3, n);
            double r = 0.85 + 0.08 * Math.sin(base * 1.7 + i);
            Vec3 p = new Vec3(c.x + Math.cos(a) * r, c.y + 1.05 + 0.18 * Math.sin(base * 2 + i * 1.3), c.z + Math.sin(a) * r);
            RuneKit.rfx(level, "prop", p, view.with("size", n >= max ? 0.11 : 0.085, "life", 0.24, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", i * 40.0,
                    "spinRate", 120.0, "color", i % 2 == 0 ? SPIRIT_GOLD : SPIRIT, "fade", 0.4));
        }
    }

    /**
     * 교감이 가득일 때 쏜 활 평타: 화살이 늑대 정령의 모습으로 날아감 (화살과 같은 빠르기 — 3인칭 · 다른 사람은 세운 옆모습 판, 1인칭은 오른쪽 아래 활에서 가운데로 뛰는
     * 거울 판) · 활에서 정령빛
     */
    static void spiritArrow(ServerPlayer sp, Jobs.Job j, Vec3 dir, double dist) {
        ServerLevel level = sp.level();
        RpgCombat.Path path = RpgCombat.arrowPath(sp.getEyePosition(), dir, Math.max(0.5, dist));
        RpgCombat.Shot shot = RpgCombat.arrowShot(path.length());
        double life = Math.max(0.1, shot.life() + 0.05);
        double speed = path.length() / Math.max(0.05, shot.life());
        Vec3 from = V.add(path.from(), path.dir(), 0.3);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        JobFx16.wolfRun(level, V.up(from, -0.35), path.dir(), 1.5, speed, life, JobFx.hide1p(sp).with("fade", 0.15), glow, core);
        JobFx16.wolf1p(level, V.add(from, path.dir(), 0.6), path.dir(), 0.9, speed, life, true, JobFx.only1p(sp).with("spin", -18.0, "fade", 0.2), glow, core);
        SkillFx.sparks(level, from, SPIRIT, 5, 2.0);
        if (RuneKit.fxQuota("spiritArrow", 4)) SkillFx.play(level, "entity.wolf.growl", from, 1.6, 0.35);
    }

    // ------------------------------------------------------------ 틱 (JobTraits 가 4틱마다)

    /** 분노가 줄어듦 · 호흡 (움직임) · 연쇄가 끝남 · 방어 깨짐 표시 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        State st = STATES.get(sp.getUUID());
        if (breathJob(j)) { // 0.51.0 스나이퍼도
            if (st == null) st = of(sp);
            Vec3 p = sp.position();
            boolean moved = st.lastPos != null && Math.hypot(p.x - st.lastPos.x, p.z - st.lastPos.z) > 0.2;
            st.lastPos = p;
            if (moved) {
                st.lastMove = now;
                st.moving += 4;
                int decay = (int) trait(j, "decay", 20);
                if (st.moving >= decay) {
                    st.moving -= decay;
                    if (st.breath > 0 && !breathHeld(sp, st)) {
                        st.breath--;
                        JobTraits.sendGauge(sp, false);
                    }
                }
            } else st.moving = 0;
            if (st.breath > 0) communionFx(sp, j, st.breath, now);
            return;
        }
        if (st == null) return;
        if (j.id().equals(JobTraits6.WL) && JobTraits6.bearOn(sp)) {
            // 0.51.0 곰의 화신: 분노가 남은 시간만큼 줄어 끝나면 0
            int max = j.trait() == null ? 100 : Math.max(1, j.trait().max());
            int want = (int) Math.ceil(max * JobTraits6.bearFrac(sp));
            if (want != st.rage) {
                st.rage = Math.max(0, Math.min(max, want));
                JobTraits.sendGauge(sp, false);
            }
        } else if (rageJob(j)) {
            if (st.rage > 0 && !JobTraits.stanceOn(sp, "warrior_spirit") && now - st.lastCombat >= trait(j, "idle", 100)) {
                st.rage = Math.max(0, st.rage - (int) Math.max(1, Math.round(trait(j, "decay", 5) * 4 / 20.0)));
                JobTraits.sendGauge(sp, false);
            }
        } else if (chainJob(j)) {
            if (st.chain > 0 && now - st.lastCast > JobTraits8.chainWindow(sp, j)) {
                st.chain = 0;
                JobTraits8.clearStars(sp); // 0.51.0 아크메이지: 연쇄가 끊기면 별도 사라짐
                JobTraits.sendGauge(sp, false);
            }
        }
    }

    /** 방어 깨짐 표시 (서버 틱마다 한 번 — JobTraits): 끝난 것을 지우고, 16틱마다 머리 위에 옅은 갈라진 방패 */
    static void tickBreaks(long now) {
        if (BREAKS.isEmpty()) return;
        BREAKS.values().removeIf(b -> b.until() <= now || b.e().isRemoved() || !b.e().isAlive());
        if (now % 16 != 0) return;
        Jobs.Job war = Jobs.job("warrior");
        if (war == null) return;
        for (Broken b : BREAKS.values()) {
            if (b.until() - now < 8 || !RuneKit.fxQuota("jobBreak", 8)) continue;
            JobFx.breakMark(b.e(), war, 0.3, 0.55, 0.65);
        }
    }

    /** 태세가 끝남 (JobTraits): 투혼 → 회복 기회 지움 */
    static void stanceEnded(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return;
        if (id.equals("warrior_spirit")) {
            State st = STATES.get(sp.getUUID());
            if (st != null) st.spiritHealed = false;
        }
    }

    /** 게이지 값: 전사 분노 · 매지션 연쇄 · 궁수 호흡 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return switch (j.id()) {
            case "warrior", "warlord" -> rage(sp); // 0.51.0 워로드 도 분노
            case "mage", "arcmage" -> chain(sp); // 0.51.0 아크메이지 도 연쇄 (최대 5)
            case "archer", "sniper" -> breath(sp); // 0.51.0 스나이퍼 도 호흡 (최대 8)
            default -> JobTraits3.gaugeValue(sp, j); // 0.29.0 표식 · 창끝 · 혼
        };
    }
}
