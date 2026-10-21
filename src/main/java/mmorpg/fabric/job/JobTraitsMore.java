package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
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
 * half 이상이면 도끼 평타 피해 + halfBonus, 다 차면 + fullBonus (RpgCombat.basicMult). 파쇄격의 방어 깨짐 (맞는 쪽 배율 — RpgCombat.takenMult).
 * 투혼: 체력이 low 아래로 떨어지면 한 번 heal 회복 (RpgHealth.lossHook)</li>
 * <li>매지션 「주문 연쇄」: 직업 스킬을 쓰고 window 틱 안에 다른 직업 스킬을 쓰면 연쇄 +1 (최대 max — 하나당 피해 + perChain, JobSystem 이 위력에 곱함).
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
        RpgCombat.addCritBonusHook(JobTraitsMore::critAdd);
        RpgCombat.addRangedCooldownHook(JobTraitsMore::rangedCd);
        RpgCombat.addWeaponHitHook(JobTraitsMore::onWeaponHit);
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
        st.spiritHealed = false;
    }

    private static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && j.id().equals(id) ? j : null;
    }

    private static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
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

    /** 분노 더하기 (최대 100) — 50 · 100 을 넘으면 손에서 불꽃 */
    static void gainRage(ServerPlayer sp, Jobs.Job j, int n) {
        State st = of(sp);
        int max = j.trait() == null ? 100 : Math.max(1, j.trait().max());
        int before = st.rage;
        st.rage = Math.min(max, st.rage + n);
        st.lastCombat = Tasks.now();
        int half = (int) trait(j, "half", 50);
        if ((before < half && st.rage >= half) || (before < max && st.rage >= max)) {
            ServerLevel level = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            boolean full = st.rage >= max;
            JobFx.burst(level, V.up(V.add(h.hand(), h.f(), 0.6), 0.3), full ? 0.45 : 0.32, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.sparks(level, h.hand(), JobKit.glow(j), full ? 10 : 6, 3);
            SkillFx.play(level, full ? "mob.ravager.roar" : "fire.ignite", sp.position(), full ? 1.4 : 1.2, full ? 0.5 : 0.8);
            if (full) DamageNumbers.showFace(sp, "§6§l분노 최고조", 2.4, 0.9, -0.3);
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
        if (j.id().equals("warrior") && "axe".equals(w.type())) {
            int max = j.trait() == null ? 100 : Math.max(1, j.trait().max());
            if (st.rage >= max) return 1 + trait(j, "fullBonus", 0.2);
            if (st.rage >= trait(j, "half", 50)) return 1 + trait(j, "halfBonus", 0.1);
            return 1;
        }
        if (j.id().equals("archer") && "bow".equals(w.type())) return 1 + st.breath * trait(j, "perStack", 0.04);
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
        Jobs.Job j = jobIf(sp, "warrior");
        if (j == null) return;
        if (src.getEntity() instanceof LivingEntity from && from != sp) gainRage(sp, j, (int) trait(j, "onHurt", 5));
        Jobs.Skill k = skill(j, "warrior_spirit");
        State st = of(sp);
        double max = RpgHealth.maxOf(sp);
        if (k == null || st.spiritHealed || !JobTraits.stanceOn(sp, k.id()) || !(max > 0) || after >= max * k.num("low", 0.3) || after <= 0) return;
        st.spiritHealed = true;
        double heal = max * k.num("heal", 0.2);
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
            DamageNumbers.showFace(sp, "§6§l투혼 — 다시 일어섬", 2.4, 0, 0.2);
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
        return j != null && Tasks.now() - st.lastCast <= trait(j, "window", 80) ? st.chain : 0;
    }

    /** 시험용: 연쇄를 정함 (마지막 시전 = 지금 · 스킬 이름은 비움 — 다음 스킬이 이어짐) */
    public static void setChain(ServerPlayer sp, int n) {
        State st = of(sp);
        st.chain = Math.max(0, n);
        st.lastCast = n > 0 ? Tasks.now() : -1000;
        st.lastSkill = "";
        JobTraits.sendGauge(sp, false);
    }

    /** 이번 시전의 연쇄 (매지션이 아니면 0): window 안에 다른 스킬이면 지금 + 1 (최대 max), 아니면 0 · 같은 스킬이면 그대로 */
    static int chainNext(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !j.id().equals("mage")) return 0;
        State st = of(sp);
        if (Tasks.now() - st.lastCast > trait(j, "window", 80)) return 0;
        if (k.id().equals(st.lastSkill)) return st.chain;
        return Math.min(Math.max(1, j.trait().max()), st.chain + 1);
    }

    /** 연쇄 피해 배율 (1 + 연쇄 × perChain) */
    static double chainMult(Jobs.Job j, int chain) {
        return chain <= 0 ? 1 : 1 + chain * trait(j, "perChain", 0.12);
    }

    /** 시전이 나감: 연쇄 · 마지막 스킬 · 틱을 기록 (연쇄가 붙으면 글자 · 금빛 고리) */
    static void chainCommit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, int chain) {
        if (j == null || !j.id().equals("mage")) return;
        State st = of(sp);
        st.chain = chain;
        st.lastCast = Tasks.now();
        st.lastSkill = k.id();
        if (chain > 0) {
            DamageNumbers.showFace(sp, "§e§l연쇄 ×" + chain + " §r§7+" + Math.round(chain * trait(j, "perChain", 0.12) * 100) + "%", 2.4, -0.9, -0.3);
            Vec3 tip = RpgCombat.circleAt(sp)[0];
            JobFx.ringFacing(sp.level(), tip, 0.22 + 0.08 * chain, j, V.scale(sp.getViewVector(1F), -1), 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.2 + chain * 0.2, 0.8);
        }
        JobTraits.sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 궁수 「호흡」

    /** 지금 호흡 */
    public static int breath(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.breath;
    }

    /** 시험용: 호흡을 정함 */
    public static void setBreath(ServerPlayer sp, int n) {
        State st = of(sp);
        st.breath = Math.max(0, Math.min(5, n));
        st.moving = 0;
        JobTraits.sendGauge(sp, false);
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

    /** 사거리 +: 궁수 활 = 호흡 × rangePer, 매지션 완드 (마나 과부하) = range */
    static double rangeAdd(ServerPlayer sp, Weapon w) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return 0;
        if (j.id().equals("archer") && "bow".equals(w.type())) return breath(sp) * trait(j, "rangePer", 1);
        if (j.id().equals("mage") && "wand".equals(w.type()) && JobTraits.stanceOn(sp, "mage_overload")) {
            Jobs.Skill k = skill(j, "mage_overload");
            return k == null ? 4 : k.num("range", 4);
        }
        return 0;
    }

    /** 관통 폭: 궁수 활 (정조준) · 매지션 완드 (마나 과부하) */
    static double pierceWidth(ServerPlayer sp, Weapon w) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return 0;
        if (j.id().equals("archer") && "bow".equals(w.type()) && JobTraits.stanceOn(sp, "archer_focus")) {
            Jobs.Skill k = skill(j, "archer_focus");
            return k == null ? 0.6 : k.num("width", 0.6);
        }
        if (j.id().equals("mage") && "wand".equals(w.type()) && JobTraits.stanceOn(sp, "mage_overload")) {
            Jobs.Skill k = skill(j, "mage_overload");
            return k == null ? 0.8 : k.num("width", 0.8);
        }
        return 0;
    }

    /** 치명타 +: 궁수 활 (정조준) */
    static double critAdd(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (w == null || !"bow".equals(w.type()) || !JobTraits.stanceOn(sp, "archer_focus") || jobIf(sp, "archer") == null) return 0;
        Jobs.Skill k = skill(JobSystem.job(sp), "archer_focus");
        return k == null ? 0.2 : k.num("crit", 0.2);
    }

    /** 공격 간격 배율: 매지션 완드 (마나 과부하) */
    static double rangedCd(ServerPlayer sp, Weapon w) {
        if (w == null || !"wand".equals(w.type()) || !JobTraits.stanceOn(sp, "mage_overload") || jobIf(sp, "mage") == null) return 1;
        Jobs.Skill k = skill(JobSystem.job(sp), "mage_overload");
        return k == null ? 0.6 : k.num("cdScale", 0.6);
    }

    /**
     * 쏨: 궁수 — 움직이지 않았으면 호흡 +1 (손에 작은 과녁 빛), 바람 타기가 남았으면 follow 틱 뒤 한 발 더 (평타와 같은 피해 · 빛의 화살) / 정조준이면 화살 끝 금빛.
     * 매지션 — 마나 과부하의 굵은 광선
     */
    private static void onFired(ServerPlayer sp, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return;
        ServerLevel level = sp.level();
        long now = Tasks.now();
        if (j.id().equals("archer") && "bow".equals(w.type())) {
            State st = of(sp);
            int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
            if (now - st.lastMove > 4 && st.breath < max) {
                st.breath++;
                Vec3 hand = RuneKit.bowHand(sp);
                JobFx.target(level, hand, 0.12 + 0.03 * st.breath, j, V.scale(sp.getViewVector(1F), -1), 0, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
                if (st.breath == max) SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.8, 0.6);
                JobTraits.sendGauge(sp, false);
            }
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
                    RpgCombat.extraArrow(sp, target, w);
                    JobFx.arrow(sp.level(), hand, Kit.bodyPoint(target), j, 60, 0.7, Opt.empty());
                    JobFx.wind(sp.level(), V.up(sp.position(), 0.08), 0.9, j, 0.35, Opt.of("alpha", 0.8));
                });
            }
            return;
        }
        if (j.id().equals("mage") && "wand".equals(w.type()) && JobTraits.stanceOn(sp, "mage_overload")) {
            Vec3 end = V.add(from, dir, Math.max(1, dist));
            JobFx.beam(level, V.add(from, dir, 0.6), end, j, 2.6, 0.3, Opt.of("alpha", 0.9));
            JobFx.burst(level, from, 0.35, j, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "mob.warden.sonic_charge", from, 1.9, 0.25);
        }
    }

    /** 무기로 맞힘: 전사 도끼 평타 → 분노 + onHit · 매지션 완드 평타 → 직업 스킬 재사용 대기 − wandCut (한 발에 한 번) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || w == null) return;
        if (j.id().equals("warrior") && "axe".equals(w.type()) && Combat.isMeleeWeapon(w)) {
            gainRage(sp, j, (int) trait(j, "onHit", 8));
            return;
        }
        if (j.id().equals("mage") && "wand".equals(w.type())) {
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

    // ------------------------------------------------------------ 틱 (JobTraits 가 4틱마다)

    /** 분노가 줄어듦 · 호흡 (움직임) · 연쇄가 끝남 · 방어 깨짐 표시 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        State st = STATES.get(sp.getUUID());
        if (j.id().equals("archer")) {
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
            return;
        }
        if (st == null) return;
        if (j.id().equals("warrior")) {
            if (st.rage > 0 && !JobTraits.stanceOn(sp, "warrior_spirit") && now - st.lastCombat >= trait(j, "idle", 100)) {
                st.rage = Math.max(0, st.rage - (int) Math.max(1, Math.round(trait(j, "decay", 5) * 4 / 20.0)));
                JobTraits.sendGauge(sp, false);
            }
        } else if (j.id().equals("mage")) {
            if (st.chain > 0 && now - st.lastCast > trait(j, "window", 80)) {
                st.chain = 0;
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

    /** 태세가 끝남 (JobTraits): 마나 과부하 → 이동 느려짐 (after 틱) · 투혼 → 회복 기회 지움 */
    static void stanceEnded(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return;
        if (id.equals("mage_overload")) {
            Jobs.Skill k = skill(j, id);
            int after = k == null ? 40 : (int) k.num("after", 40);
            RuneKit.effect(sp, "slowness", after, 1);
            SkillFx.play(sp.level(), "beacon.deactivate", sp.position(), 1.4, 0.6);
            SkillFx.sparks(sp.level(), V.up(sp.position(), 1.0), JobKit.glow(j), 8, 2);
        } else if (id.equals("warrior_spirit")) {
            State st = STATES.get(sp.getUUID());
            if (st != null) st.spiritHealed = false;
        }
    }

    /** 게이지 값: 전사 분노 · 매지션 연쇄 · 궁수 호흡 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return switch (j.id()) {
            case "warrior" -> rage(sp);
            case "mage" -> chain(sp);
            case "archer" -> breath(sp);
            default -> 0;
        };
    }
}
