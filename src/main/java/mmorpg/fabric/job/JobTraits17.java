package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Elements;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-07 마검사 반전 엘리멘탈 블레이드 (소드 콜러 대신 — 사용자 "원소 반응 … 애는 룬하고 직업 스킬에도 들어감" · 고른 것 "모두 원소 (예외)") 특성 「원소의 칼」 ·
 * 프로스트타이드 (claude/job-advance-redo.md, 서버 — jobs.json advance.elementalblade):
 * <ul>
 * <li>칼에 깃든 원소: 직업 스킬을 쓰면 그 원소가 칼에 깃듦 ({@link #setBlade} — 플레어 엣지 불 · 어스 서지 바위 · 볼트 피어스 전기 · 칼라미티는 칼이 내리꽂힐 때마다
 * 그 원소). 칼날이 그 빛으로 타오름 ({@link #tick}) · 게이지 = 원소 차례 (1 불 · 2 물 · 3 전기 · 4 얼음 · 5 바위 — rules_more ELEMENTS.order)</li>
 * <li>칼 평타: 깃든 원소를 맞은 적에 묻힘 (ElementSystem.apply — 다른 원소가 묻은 적이면 반응) · 평타도 원소 몫 −5% (RpgCombat.basicMult × Elements.mult)</li>
 * <li>프로스트타이드 (태세): 칼 평타마다 물 → 얼음 → 물 … 이 번갈아 깃들어 묻음 (물 + 얼음 = 빙결 — 같은 적 2초에 한 번) · 칼끝에서 물결 · 서리 초승달 검기가 날아가
 * 그 적을 한 번 더 벰 (평타 × echo — 무기 덤)</li>
 * </ul>
 */
public final class JobTraits17 {
    private JobTraits17() {}

    static final String EB = "elementalblade";
    static final String TIDE = "elementalblade_tide";
    private static final String KEY_TIDE = "elementalblade_tide";

    /** 쓴 사람 → 칼에 깃든 원소 (없으면 무속성 — 아직 원소 스킬을 쓰지 않음) */
    private static final Map<UUID, String> BLADE = new HashMap<>();
    /** 프로스트타이드: 다음 평타가 얼음인지 (아니면 물 — 태세가 켜지면 물부터) */
    private static final Map<UUID, Boolean> TIDE_ICE = new HashMap<>();
    /** 칼의 원소가 바뀐 틱 (칼날 빛을 바로 그림) */
    private static final Map<UUID, Long> CHANGED = new HashMap<>();

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits17::tideBonus);
        RpgCombat.addWeaponHitHook(JobTraits17::onWeaponHit);
        java.util.function.ToDoubleBiFunction<ServerPlayer, Weapon> bm = RpgCombat.basicMult;
        RpgCombat.basicMult = (p, w) -> bm.applyAsDouble(p, w) * basicMult(p, w);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BLADE.clear();
            TIDE_ICE.clear();
            CHANGED.clear();
        });
    }

    static Jobs.Job jobIf(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && EB.equals(j.id()) ? j : null;
    }

    static void reset(ServerPlayer sp) {
        forget(sp.getUUID());
    }

    static void forget(UUID id) {
        BLADE.remove(id);
        TIDE_ICE.remove(id);
        CHANGED.remove(id);
    }

    static void lifeEnded(UUID id) {
        forget(id);
    }

    // ------------------------------------------------------------ 칼의 원소

    /** 칼에 깃든 원소 (없으면 null) */
    public static String blade(ServerPlayer sp) {
        return BLADE.get(sp.getUUID());
    }

    /** 칼에 원소 el 이 깃듦 (직업 스킬을 쓴 뒤): 칼날이 그 빛으로 번쩍 · 게이지 */
    static void setBlade(ServerPlayer sp, String el) {
        if (sp == null || !Elements.known(el)) return;
        String was = BLADE.put(sp.getUUID(), el);
        CHANGED.put(sp.getUUID(), mmorpg.fabric.Tasks.now());
        if (!el.equals(was)) bladeFlash(sp, el);
        JobTraits.sendGauge(sp, false);
    }

    /** 시험 · 영상: 칼의 원소를 정함 (null = 무속성) · 프로스트타이드 차례를 물로 */
    public static void testBlade(ServerPlayer sp, String el) {
        if (el == null) BLADE.remove(sp.getUUID());
        else BLADE.put(sp.getUUID(), el);
        TIDE_ICE.remove(sp.getUUID());
        JobTraits.sendGauge(sp, false);
    }

    /** 원소의 차례 (1 불 ~ 5 바위 · 없으면 0) */
    static int index(String el) {
        int i = el == null ? -1 : Elements.order().indexOf(el);
        return i < 0 ? 0 : i + 1;
    }

    /** 평타가 묻힐 원소 (프로스트타이드면 다음 차례의 물 · 얼음 — 바꾸지 않음) */
    static String next(ServerPlayer sp) {
        if (JobTraits.stanceOn(sp, TIDE)) return Boolean.TRUE.equals(TIDE_ICE.get(sp.getUUID())) ? "ice" : "water";
        return BLADE.get(sp.getUUID());
    }

    /** 칼을 쓰는 평타인지 (근접 · 직업 무기 — 칼 · 대검) */
    private static boolean bladeHit(Jobs.Job j, Weapon w) {
        return j != null && w != null && j.usesWeapon(w.type()) && Combat.isMeleeWeapon(w);
    }

    /** 평타 배율: 칼에 원소가 깃들어 있으면 원소 몫 (−5% — 무속성이면 그대로) */
    private static double basicMult(ServerPlayer sp, Weapon w) {
        Jobs.Job j = jobIf(sp);
        if (!bladeHit(j, w)) return 1;
        String el = next(sp);
        return el == null ? 1 : Elements.mult(el);
    }

    // ------------------------------------------------------------ 평타 · 프로스트타이드

    /** 프로스트타이드: 태세 동안 칼 평타가 맞으면 평타 × echo 를 더함 (칼끝에서 날아간 물결 · 서리 검기) */
    private static RpgCombat.Bonus tideBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (!(dmg > 0) || !JobTraits.stanceOn(sp, TIDE)) return null;
        Jobs.Job j = jobIf(sp);
        if (!bladeHit(j, w)) return null;
        Jobs.Skill k = j.skill(TIDE);
        double echo = k == null ? 0.4 : JobUpgrades.num(sp, k, "echo", 0.4);
        Jobs.Job tj = ElementalbladeSkills.tint(j, next(sp));
        return new RpgCombat.Bonus(dmg * echo, "", KEY_TIDE, DamageNumbers.Style.JOB, null, JobKit.grad(tj));
    }

    /** 칼 평타가 맞음 (피해가 들어간 뒤): 깃든 원소를 묻힘 · 프로스트타이드면 물 · 얼음을 번갈아 (검기 그림) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        Jobs.Job j = jobIf(sp);
        if (victim == null || !bladeHit(j, w)) return;
        boolean tide = JobTraits.stanceOn(sp, TIDE);
        String el = next(sp);
        if (el == null) return;
        if (tide) {
            TIDE_ICE.put(sp.getUUID(), !"ice".equals(el));
            BLADE.put(sp.getUUID(), el);
            boolean wave = false;
            if (bonus != null) for (RpgCombat.Bonus p : bonus.parts()) if (KEY_TIDE.equals(p.key())) wave = true;
            if (wave) tideWave(sp, j, victim, el);
        }
        ElementSystem.apply(sp, victim, el, JobSystem.power(sp, j), true, false);
        JobTraits.sendGauge(sp, false);
    }

    /** 칼끝에서 물결 (물) · 서리 (얼음) 초승달 검기가 맞은 적으로 날아가 벰 (번갈아 기욺) · 맞은 자리 물보라 · 서리 */
    private static void tideWave(ServerPlayer sp, Jobs.Job j, LivingEntity victim, String el) {
        ServerLevel level = sp.level();
        Jobs.Job tj = ElementalbladeSkills.tint(j, el);
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = V.up(V.add(sp.position(), f, 0.8), 1.15);
        Vec3 to = Kit.bodyPoint(victim);
        Vec3 d = V.sub(to, from);
        double len = Math.max(0.5, V.len(d));
        double speed = 22;
        boolean ice = "ice".equals(el);
        JobFx.bigWave(level, sp, from, V.scale(d, 1 / len), speed, ice ? 1.05 : 0.95, ice ? 160 : 20, len / speed + 0.1, tj, Opt.of("alpha", 0.95));
        int at = Math.max(1, (int) Math.round(len / speed * 20));
        RuneKit.later(at, () -> {
            if (victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
            Vec3 b = Kit.bodyPoint(victim);
            if (ice) {
                JobTraits16.shardsFx(lv, b, 0.55);
                SkillFx.play(lv, "block.glass.break", b, 1.5, 0.35);
            } else {
                SkillFx.fx(lv, "rpg:splash", V.up(victim.position(), 0.2), Opt.of("size", 1.1 + Kit.bodyPad(victim), "life", 0.45, "color", JobFx.inner(tj), "alpha", 0.95));
                SkillFx.sparks(lv, b, JobFx.inner(tj), 6, 3.5);
                SkillFx.play(lv, "entity.generic.splash", b, 1.4, 0.35);
            }
        });
        SkillFx.play(level, ice ? "block.powder_snow.break" : "item.trident.riptide_1", sp.position(), ice ? 1.2 : 1.7, 0.45);
    }

    /** 프로스트타이드가 켜짐: 물부터 · 칼을 감는 물 · 서리 소용돌이 */
    static void tideOn(ServerPlayer sp) {
        TIDE_ICE.put(sp.getUUID(), false);
        JobTraits.sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 칼날 빛

    /** 원소가 깃듦: 칼끝에서 그 원소 빛이 번쩍 · 칼날을 따라 불티 · 소리 (1인칭 눈앞 오른쪽 아래에서도 보임) */
    private static void bladeFlash(ServerPlayer sp, String el) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        Vec3 tip = V.up(V.add(h.hand(), h.f(), 0.55), 0.25);
        double[] g = ElementFx.glow(el);
        double[] c = ElementFx.core(el);
        JobFx.glint(level, tip, 0.42, g, c, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
        SkillFx.sparks(level, tip, c, 6, 2.6);
        SkillFx.play(level, switch (el) {
            case "fire" -> "item.firecharge.use";
            case "water" -> "entity.generic.splash";
            case "thunder" -> "entity.lightning_bolt.impact";
            case "ice" -> "block.glass.place";
            default -> "block.stone.place";
        }, sp.position(), 1.4, 0.35);
    }

    /**
     * 4틱마다 (JobTraits): 칼에 원소가 깃든 동안 8틱마다 칼날을 따라 그 원소 빛 (불 = 불티가 솟음 · 물 = 물방울이 떨어짐 · 전기 = 칼날을 기는 작은 번개 · 얼음 = 서리가 흩날림 ·
     * 바위 = 금빛 돌가루) — 프로스트타이드 동안은 {@link #stanceFx} 가 그림
     */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!EB.equals(j.id()) || now % 8 != 0 || sp.isSpectator() || JobTraits.stanceOn(sp, TIDE)) return;
        String el = BLADE.get(sp.getUUID());
        if (el == null) return;
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        if (!bladeHit(j, w)) return;
        bladeGlow(sp, el, 1.0);
    }

    /** 칼날을 따라 원소 빛 한 번 (strength = 크기 · 수 배) */
    static void bladeGlow(ServerPlayer sp, String el, double strength) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        Vec3 base = V.up(h.hand(), 0.1);
        Vec3 tip = V.up(V.add(h.hand(), h.f(), 0.6), 0.32);
        Vec3 mid = V.lerp(base, tip, 0.35 + Kit.rand() * 0.5);
        double[] g = ElementFx.glow(el);
        double[] c = ElementFx.core(el);
        Vec3 vel = RuneKit.bodyVel(sp);
        switch (el) {
            case "fire" -> SkillFx.fx(level, "rpg:arrow_spark", mid, Opt.of("dir", V.UP, "speed", 1.6, "size", 0.14 * strength, "grow", 0.8, "life", 0.4, "color", g,
                    "alpha", 1.0));
            case "water" -> SkillFx.fx(level, "rpg:arrow_spark", mid, Opt.of("dir", V.scale(V.UP, -1), "speed", 1.2, "size", 0.12 * strength, "grow", 0.4, "life", 0.4,
                    "color", c, "alpha", 0.95));
            case "thunder" -> RuneKit.zap(level, mid, V.add(mid, new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.4, Kit.rand() - 0.5), 0.45), g, c, 0.06 * strength, 0.14);
            case "ice" -> SkillFx.fx(level, "rpg:snowfall", mid, Opt.of("size", 0.28 * strength, "life", 0.5, "color", c, "alpha", 0.85));
            default -> SkillFx.fx(level, "rpg:arrow_spark", mid, Opt.of("dir", V.scale(V.UP, -1), "speed", 0.8, "size", 0.12 * strength, "grow", 0.3, "life", 0.45,
                    "color", c, "alpha", 0.95));
        }
        JobFx.glint(level, tip, 0.16 * strength, g, c, 0.2, Opt.of("vel", vel, "alpha", 0.8));
    }

    /** 프로스트타이드 동안 8틱마다: 칼을 감는 물 (다음이 물) · 서리 (다음이 얼음) 소용돌이 · 발밑 물빛 고리 (3인칭) */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        if (!TIDE.equals(stance)) return false;
        String el = next(sp);
        JobFx.aura(sp, ElementalbladeSkills.tint(j, el), 2.4, 0.5, 0.5);
        bladeGlow(sp, el, 1.2);
        String other = "ice".equals(el) ? "water" : "ice";
        bladeGlow(sp, other, 0.8);
        ServerLevel level = sp.level();
        JobFx.ring(level, V.up(sp.position(), 0.08), 1.1, ElementalbladeSkills.tint(j, el), Kit.rand() < 0.5, 0.45, JobFx.hide1p(sp).with("alpha", 0.6));
        return true;
    }

    // ------------------------------------------------------------ 게이지

    /** 게이지 = 칼에 깃든 원소의 차례 (프로스트타이드면 다음 평타의 물 · 얼음) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return index(next(sp));
    }

    /** 시험: 직업 j 를 원소 el 의 빛으로 (ElementalbladeSkills.tint — 그림 · 숫자 그라데이션) */
    public static Jobs.Job tintOf(Jobs.Job j, String el) {
        return ElementalbladeSkills.tint(j, el);
    }

    /** 시험: 지금 게이지 (원소의 차례 — 0 무속성 · 1 불 ~ 5 바위) */
    public static int gaugeOf(ServerPlayer sp) {
        return index(next(sp));
    }
}
