package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 검사 2차 특성 · 태세 (재설계안 2판 — claude/job-advance-redo.md):
 * <ul>
 * <li>소드마스터 「투지」: 쌓고 쓰는 것은 검사와 같음 (JobTraits — 최대 8). 투지 8 에서 피해 직업 스킬을 쓰면 그 스킬이 치명 (위력 × (1 + crit) · 치명
 * 숫자 — 나가면 투지 critUse 개). 용의 피 (태세 — 롤 마스터 이 R): 공격 속도 + haste · 이동 + move · 칼 평타가 투지 +2 · 투지 유지 · 받는 피해 −guard (Guards —
 * 스킬). 궁극기 스타폴 블레이드는 태세가 없음 (2026-11-05 다시 벼린 칼 — 벼린 칼 동안 사거리 · 피해 + 를 뺌)</li>
 * <li>블러드 나이트 「피의 갈증」: 피해 직업 스킬을 쓸 때 체력이 costMin 이상이면 최대 체력 × cost 를 바쳐 그 스킬 위력 × (1 + bonus), 직업 스킬
 * 피해 × leech 흡혈, 체력이 적을수록 공격 속도 · 흡혈이 오름 (체력 thirstAt 에서 가장 셈 — 공격 속도 + thirstSpeed · 흡혈 × (1 + leechMax), 롤 올라프 · 브라이어
 * 패시브). 광란 (태세 — 롤 브라이어 W): 공격 속도 + haste · 흡혈 × leechMult · 둘레 autoRange 칸의 가장 가까운 적을 저절로 벰 (멈추지 않음)</li>
 * </ul>
 */
public final class JobTraits5 {
    private JobTraits5() {}

    static final String SM = "swordmaster";
    static final String BK = "bloodknight";
    /** 용의 피 (소드마스터 태세) · 광란 (블러드 나이트 태세) */
    static final String DRAGON = "swordmaster_blood";
    static final String FRENZY = "bloodknight_frenzy";
    /** 피 빛 (블러드 나이트 색이 없을 때) */
    static final double[] BLOOD = {0.62, 0.02, 0.08};
    static final double[] BLOOD_LIGHT = {0.95, 0.22, 0.24};
    /** 용의 피 이동 속도 (속성 수정치) */
    private static final Identifier DRAGON_SPEED = Mmorpg.id("job_dragon_speed");

    static final class State {
        /** 소드마스터: 이 틱까지 직업 스킬 피해 × (1 + crit) · 치명 숫자 */
        long critUntil;
        /** 이번 시전에 치명이 걸렸는지 (afterCast 가 투지를 씀) */
        boolean critPending;
        /** 블러드 나이트: 이번 시전에 체력을 바침 */
        boolean payNow;
        /** 흡혈 숫자 (모아서 1초에 한 번) */
        double leechAcc;
        long leechShown;
        /** 용의 피 이동 속도 (걸린 값) */
        double speedSet;
        /** 광란: 마지막으로 저절로 벤 틱 */
        long lastAuto;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static void init() {
        RpgCombat.addWeaponHitHook(JobTraits5::onWeaponHit);
        java.util.function.ToDoubleFunction<ServerPlayer> as = RpgCombat.attackSpeed;
        RpgCombat.attackSpeed = p -> as.applyAsDouble(p) * attackSpeed(p);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    /** 직업이 id 면 그 직업 (아니면 null) */
    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    static void reset(ServerPlayer sp) {
        State st = STATES.remove(sp.getUUID());
        if (st != null) clearMods(sp);
        JobImpact.forget(sp.getUUID());
    }

    static void forget(UUID id) {
        STATES.remove(id);
        JobImpact.forget(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.critUntil = 0;
            st.critPending = false;
            st.payNow = false;
        }
    }

    /** 이동 속도 수정치를 뗌 */
    private static void clearMods(ServerPlayer sp) {
        AttributeInstance m = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (m != null) m.removeModifier(DRAGON_SPEED);
    }

    // ------------------------------------------------------------ 시전 (JobSystem.Slot.cast)

    /** 위력 배율 (블러드 나이트 피의 갈증: 체력을 바칠 수 있으면 × (1 + bonus) — 바치는 것은 나간 뒤 afterCast). 다른 직업 1 */
    static double castMult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !BK.equals(j.id())) return 1;
        State st = of(sp);
        st.payNow = false;
        if (k == null || k.total() <= 0) return 1;
        if (sp.getHealth() < sp.getMaxHealth() * trait(j, "costMin", 0.25) || !RpgHealth.isRpgMode(sp)) return 1;
        st.payNow = true;
        return 1 + trait(j, "bonus", 0.1);
    }

    /** 소드마스터: 투지가 critAt 이상이면 이번 피해 스킬을 치명으로 (지금부터 그 스킬이 끝날 때까지 — 나가지 않으면 afterCast 가 거둠) */
    static void beforeCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !SM.equals(j.id()) || k == null || k.total() <= 0) return;
        if (JobTraits.stacks(sp) < trait(j, "critAt", 8)) return;
        State st = of(sp);
        st.critPending = true;
        st.critUntil = Tasks.now() + Math.max(20, (long) k.num("t", 20) + 12);
    }

    /** 나간 뒤: 소드마스터 치명 → 투지 critUse 개를 씀 (나가지 않았으면 치명을 거둠), 블러드 나이트 → 체력을 바침. 알림 줄에 덧붙일 말 (없으면 null) */
    static String afterCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, boolean ok) {
        if (j == null) return null;
        State st = STATES.get(sp.getUUID());
        if (st == null) return null;
        if (SM.equals(j.id()) && st.critPending) {
            st.critPending = false;
            if (!ok) {
                st.critUntil = 0;
                return null;
            }
            JobTraits.useStacks(sp, (int) trait(j, "critUse", 3));
            critFx(sp, j);
            return "§6치명 +" + Math.round(trait(j, "crit", 0.3) * 100) + "%";
        }
        if (BK.equals(j.id()) && st.payNow) {
            st.payNow = false;
            if (!ok) return null;
            double paid = payBlood(sp, j, trait(j, "cost", 0.04));
            return paid > 0 ? "§4피 −" + Math.round(trait(j, "cost", 0.04) * 100) + "% · 위력 +" + Math.round(trait(j, "bonus", 0.1) * 100) + "%" : null;
        }
        return null;
    }

    /** 치명 시작: 칼끝 금빛 번쩍 · 몸의 기운 · 소리 */
    private static void critFx(ServerPlayer sp, Jobs.Job j) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        JobFx.glint(level, V.up(V.add(h.hand(), h.f(), 0.7), 0.45), 0.8, j, 0.5, Opt.of("vel", RuneKit.bodyVel(sp), "spin", Kit.rand() * 30 - 15));
        JobFx.burst(level, V.up(V.add(h.hand(), h.f(), 0.6), 0.3), 0.55, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.aura(sp, j, 2.6, 0.5, 0.85);
        SkillFx.play(level, "entity.player.attack.crit", sp.position(), 1.2, 0.9);
        SkillFx.play(level, "block.bell.hit", sp.position(), 1.6, 0.6);
    }

    /** 체력 frac (최대 체력의 몫) 을 바침 (1 아래로는 못 냄) — 핏방울이 몸에서 튐. 바친 양 (0 = 못 냄 · 크리에이티브) */
    static double payBlood(ServerPlayer sp, Jobs.Job j, double frac) {
        double paid = RpgHealth.payHp(sp, sp.getMaxHealth() * frac);
        if (paid > 0) {
            ServerLevel level = sp.level();
            Vec3 chest = V.up(sp.position(), 1.1);
            SkillFx.sparks(level, chest, BLOOD_LIGHT, 10, 3.0);
            blood(level, chest, 0.75, j, 0.35, JobFx.hide1p(sp));
            SkillFx.play(level, "entity.player.hurt_sweet_berry_bush", chest, 0.8, 0.6);
        }
        return paid;
    }

    /** 이번 직업 스킬 피해의 치명 배율 (소드마스터 치명 동안 1 + crit — 그 밖 1) */
    static double critMult(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !SM.equals(j.id())) return 1;
        State st = STATES.get(sp.getUUID());
        return st != null && st.critUntil >= Tasks.now() ? 1 + trait(j, "crit", 0.3) : 1;
    }

    // ------------------------------------------------------------ 공격 속도 · 흡혈 (용의 피 · 광란 · 피의 갈증)

    /** 공격 속도 배율 (RpgCombat.attackSpeed 에 곱함 — 클라이언트는 게이지 payload 의 speed) */
    static double attackSpeed(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return 1;
        if (SM.equals(j.id())) {
            if (!JobTraits.stanceOn(sp, DRAGON)) return 1;
            Jobs.Skill k = j.skill(DRAGON);
            return 1 + (k == null ? 0.35 : JobUpgrades.num(sp, k, "haste", k.num("haste", 0.35)));
        }
        if (!BK.equals(j.id())) return 1;
        double m = 1 + trait(j, "thirstSpeed", 0.3) * thirst(sp, j);
        if (JobTraits.stanceOn(sp, FRENZY)) {
            Jobs.Skill k = j.skill(FRENZY);
            m *= 1 + (k == null ? 0.6 : JobUpgrades.num(sp, k, "haste", k.num("haste", 0.6)));
        }
        return m;
    }

    /** 피의 갈증 (0 = 체력 가득 ~ 1 = 체력 thirstAt 아래) */
    static double thirst(ServerPlayer sp, Jobs.Job j) {
        double max = Math.max(1, sp.getMaxHealth());
        double at = trait(j, "thirstAt", 0.3);
        double lost = 1 - sp.getHealth() / max;
        return Math.max(0, Math.min(1, lost / Math.max(0.05, 1 - at)));
    }

    /** 흡혈 배수 (피의 갈증 · 광란) */
    static double leechMult(ServerPlayer sp, Jobs.Job j) {
        double m = 1 + trait(j, "leechMax", 1.0) * thirst(sp, j);
        if (JobTraits.stanceOn(sp, FRENZY)) {
            Jobs.Skill k = j.skill(FRENZY);
            m *= k == null ? 2 : k.num("leechMult", 2);
        }
        return m;
    }

    // ------------------------------------------------------------ 직업 스킬 피해가 들어간 뒤 (JobKit.hit)

    /** 블러드 나이트: 흡혈 (피해 × leech × 피의 갈증 · 광란) */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double dealt) {
        if (j == null || !BK.equals(j.id()) || !(dealt > 0)) return;
        leech(sp, j, dealt * trait(j, "leech", 0.05) * leechMult(sp, j));
    }

    /** 체력을 amount 회복 (숫자는 모아서 1초에 한 번) — 실제로 오른 양 */
    static double leech(ServerPlayer sp, Jobs.Job j, double amount) {
        if (!(amount > 0) || !sp.isAlive()) return 0;
        double got = RpgHealth.healBy(sp, amount);
        if (got > 0) of(sp).leechAcc += got;
        return got;
    }

    /** 게이지 값: 블러드 나이트 = 피의 갈증 (0 ~ trait.max) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !BK.equals(j.id())) return 0;
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        return (int) Math.round(thirst(sp, j) * max);
    }

    /** 시험용: 치명 창이 열려 있는지 */
    public static boolean critOn(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.critUntil >= Tasks.now();
    }

    // ------------------------------------------------------------ 평타 훅

    /** 무기로 맞힘: 광란의 칼 평타 — 맞은 자리에 핏빛 베기 (연출) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null || !Combat.isMeleeWeapon(w) || !"sword".equals(w.type())) return;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return;
        if (BK.equals(j.id()) && JobTraits.stanceOn(sp, FRENZY)) {
            JobImpact.hit(sp, victim, j, 0.9);
        }
    }

    // ------------------------------------------------------------ 틱 · 태세 연출

    /** 틱마다 (JobTraits.tick): 흡혈 숫자 (1초에 한 번) · 용의 피 이동 속도 · 광란이 저절로 벰 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        State st = STATES.get(sp.getUUID());
        if (st == null) {
            if (!JobTraits.stanceOn(sp, DRAGON) && !JobTraits.stanceOn(sp, FRENZY)) return;
            st = of(sp);
        }
        if (st.leechAcc >= 0.5 && now - st.leechShown >= 20) {
            DamageNumbers.show(sp, st.leechAcc, DamageNumbers.Style.HEAL, "", 0);
            st.leechAcc = 0;
            st.leechShown = now;
        }
        double want = 0;
        if (SM.equals(j.id()) && JobTraits.stanceOn(sp, DRAGON)) {
            Jobs.Skill k = j.skill(DRAGON);
            want = k == null ? 0.15 : k.num("move", 0.15);
        }
        if (Math.abs(want - st.speedSet) > 1e-9) {
            st.speedSet = want;
            AttributeInstance m = sp.getAttribute(Attributes.MOVEMENT_SPEED);
            if (m != null) {
                if (want > 0) m.addOrUpdateTransientModifier(new AttributeModifier(DRAGON_SPEED, want, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                else m.removeModifier(DRAGON_SPEED);
            }
        }
        if (BK.equals(j.id()) && JobTraits.stanceOn(sp, FRENZY)) frenzyAuto(sp, j, st, now);
    }

    /**
     * 광란: 평타 사이가 지났고 둘레 autoRange 칸에 적이 있으면 가장 가까운 적을 저절로 벰 (칼 평타 그대로 — Player#attack: 참격 범위 · 맞힘 훅 · 둘레에 동작).
     * 내가 누른 평타와 겹치지 않게 마지막 휘두르기에서 평타 사이 (공격 속도) 뒤
     */
    private static void frenzyAuto(ServerPlayer sp, Jobs.Job j, State st, long now) {
        if (sp.isSpectator() || !sp.isAlive() || sp.isUsingItem()) return;
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        if (w == null || !"sword".equals(w.type()) || !RpgCombat.lackOf(sp, w).isEmpty()) return;
        int gap = mmorpg.core.Motions.serverInterval(RpgCombat.attackSpeed.applyAsDouble(sp));
        MotionSystem.Swing last = MotionSystem.last(sp);
        if (last != null && now - last.tick() < gap) return;
        if (now - st.lastAuto < gap) return;
        Jobs.Skill k = j.skill(FRENZY);
        double r = k == null ? 3.6 : k.num("autoRange", 3.6);
        List<LivingEntity> near = JobKit.near(sp, r, 1);
        if (near.isEmpty()) return;
        st.lastAuto = now;
        LivingEntity e = near.get(0);
        // 평타 그대로 (Player#attack — Slash.onAttack 이 둘레에 평타 동작 · 참격 범위를 알림. 바닐라 팔 휘두르기는 이 판의 swing 이 휘두르기 꼴까지 받아 쓰지 않음)
        sp.attack(e);
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 용의 피 = 몸의 금빛 비늘 기운 · 칼의 불티 / 광란 = 붉은 눈빛 · 칼에서 떨어지는 피 · 몸의 핏빛 김. 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        if (DRAGON.equals(stance)) {
            Vec3 chest = V.add(V.up(sp.position(), 1.2), V.flat(sp.getViewVector(1F)), 0.2);
            JobFx.burst(level, chest, 0.42, j, 0.35, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.8));
            RuneKit.godRays(sp, level, sp.position(), 0.8, 3, JobKit.glow(j), JobKit.core(j), JobFx.hide1p(sp).with("h", 2.6, "life", 0.45, "alpha", 0.55));
            SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.3), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.15, "grow", 0.6, "life", 0.4, "color", JobKit.glow(j),
                    "alpha", 1.0));
            return true;
        }
        if (FRENZY.equals(stance)) {
            Vec3 tip = V.up(V.add(h.hand(), h.f(), 0.55), 0.15);
            drop(level, tip, V.scale(V.UP, -1), 0.14, JobFx.outer(j), 0.45, Opt.of("speed", 2.4, "fade", 0.3));
            SkillFx.sparks(level, tip, JobFx.outer(j), 3, 1.4);
            blood(level, V.up(sp.position(), 1.0), 0.6, j, 0.4, JobFx.hide1p(sp).with("alpha", 0.6, "vel", RuneKit.bodyVel(sp)));
            // 붉은 눈빛 (두 점 — 3인칭)
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            Vec3 r = V.rightOf(f);
            for (int s = -1; s <= 1; s += 2) {
                RuneKit.rfx(level, "prop", V.add(V.add(eye, f, 0.28), r, 0.1 * s), JobFx.hide1p(sp).with("size", 0.07, "life", 0.42, "cell",
                        (double) RuneKit.RFX.get("glint")[0], "spin", 0.0, "color", BLOOD_LIGHT, "fade", 0.4, "vel", RuneKit.bodyVel(sp)));
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 피 그림

    /** 핏방울 하나 (rfx_jdrop — 눈물 꼴, dir 쪽으로 늘어짐: 빛 칸 0 · 심지 칸 1). o: speed · vel · grow · fade · view · owner */
    static void drop(ServerLevel level, Vec3 at, Vec3 dir, double size, double[] color, double life, Opt o) {
        Opt b = o.with("dir", dir, "size", size, "life", life);
        RuneKit.rfx(level, "jdrop", at, b.with("cell", 0.0, "color", color, "alpha", o.num("alpha", 0.95)));
        RuneKit.rfx(level, "jdrop", at, b.with("cell", 1.0, "color", lighter(color), "alpha", o.num("alpha", 0.95)));
    }

    /** 피 튀김 (rfx_jblood — 카메라를 봄: 빛 0 ~ 5 · 심지 6 ~ 11): 가운데에서 핏방울이 튀어 흩어짐 */
    static void blood(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        double[] glow = j == null ? BLOOD : JobFx.outer(j);
        double[] core = j == null ? BLOOD_LIGHT : JobFx.inner(j);
        JobFx.two(level, "jblood", BLOOD_FRAMES, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360, "fade", o.num("fade",
                0.3)), glow, core);
    }

    /** rfx_jblood 장 수 */
    static final int BLOOD_FRAMES = 6;

    static double[] lighter(double[] c) {
        return new double[] {c[0] + (1 - c[0]) * 0.35, c[1] + (1 - c[1]) * 0.35, c[2] + (1 - c[2]) * 0.35};
    }
}
