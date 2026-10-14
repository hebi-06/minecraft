package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mmorpg.core.Obj;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 영웅 무기 스킬 (10단계 — 자바 전용) 공통 도구: 스킬 색 (HERO_SKILLS[종류].color), 적대 무리, 1인칭에서 가까운 그림 줄이기, 준 피해만큼 회복 (모아서 한 번).
 * 그림 도구는 초월 스킬 (Kit · SkillFx · Gram · Solas) 과 룬 (RuneKit — 바위 가시 · 분신 · rfx 그림) 의 것을 함께 씀.
 */
final class HeroKit {
    private HeroKit() {}

    /** 영웅 스킬 10종 (10단계 6 + 12단계 새 무기 4) 을 Transcend 에 등록 */
    static void registerAll() {
        Balmung.register();
        ShadowDagger.register();
        EarthJudgment.register();
        Durand.register();
        ElfBow.register();
        LichStaff.register();
        // 12단계 새 무기 영웅 스킬
        GaeBolg.register();
        Harpe.register();
        Dagda.register();
        Caduceus.register();
    }

    /** 스킬 색 {빛, 심지} (HERO_SKILLS[종류].color — 없으면 흰색) */
    static double[][] colors(Obj k) {
        List<Object> c = k.listOrNull("color");
        if (c == null || c.size() < 2) return new double[][] {SkillFx.WHITE, SkillFx.WHITE};
        return new double[][] {Kit.rgb(c.get(0)), Kit.rgb(c.get(1))};
    }

    /** JSON 의 [r, g, b] 값 (없으면 def) */
    static double[] rgbOr(Obj k, String key, double[] def) {
        Object o = k.has(key) ? k.get(key) : null;
        return o instanceof List<?> ? Kit.rgb(o) : def;
    }

    /** 이어서 고를 수 있는 적대 무리 (적대 몹 · 이 애드온 몬스터 · 보스 · 허수아비 — 섬광 난무 · 후긴과 무닌과 같음). 조준한 적은 무엇이든 */
    static boolean hostile(Entity e) {
        return Solas.hasFamily(e, Solas.RUSH_FAMILIES) && !Solas.RUSH_NEUTRAL.contains(Kit.typeId(e));
    }

    /** 눈 가까이의 그림 크기 배율: 눈에서 3칸 넘으면 1, 가까울수록 작게 (min 까지 — 1인칭 화면을 덮지 않게) */
    static double near(Vec3 eye, Vec3 at, double min) {
        return Math.min(1, Math.max(min, V.len(V.sub(at, eye)) / 3));
    }

    /** 맞힌 적의 몸 가운데 (사라졌으면 fallback) */
    static Vec3 body(LivingEntity e, Vec3 fallback) {
        return Kit.bodyAt0(e, fallback);
    }

    /**
     * 준 피해의 rate 만큼 회복 (한 번 쓸 때 실제로 회복한 양이 최대 체력의 cap 까지): add 로 모으고 open 으로 한 번에 (숫자 하나 — 초록 +).
     * open 뒤에 늦게 들어온 피해 (몹 무적 시간으로 밀린 타격) 는 들어오는 대로 다음 틱에 회복
     */
    static final class Heal {
        private final ServerPlayer sp;
        private final double rate;
        private double left;
        private double pool;
        private boolean open;
        private boolean pending;

        Heal(ServerPlayer sp, double rate, double cap) {
            this.sp = sp;
            this.rate = rate;
            this.left = RpgHealth.maxOf(sp) * cap;
        }

        void add(double dealt) {
            if (!(dealt > 0)) return;
            pool += dealt;
            if (!open || pending) return;
            pending = true;
            Tasks.later(1, () -> {
                pending = false;
                flush();
            });
        }

        /** 이제부터 회복: 모은 것을 바로 — 실제로 회복한 양 */
        double open() {
            open = true;
            return flush();
        }

        /** 모은 피해만큼 회복 — 실제로 회복한 양 (0 = 없음) */
        private double flush() {
            if (pool <= 0 || left <= 0 || sp.isRemoved() || !sp.isAlive()) return 0;
            double want = Math.min(left, pool * rate);
            pool = 0;
            double healed = RpgHealth.healBy(sp, want);
            left -= Math.max(0, healed);
            if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
            return Math.max(0, healed);
        }
    }

    /** t 틱에 fn (0 이하면 바로) */
    static void at(int t, Runnable fn) {
        if (t <= 0) fn.run();
        else Tasks.later(t, fn);
    }

    /** rfx_relic 그림 칸 안의 창 길이 비율 (512점 칸에 약 488점 — tools/java_fx_art.py) */
    static final double RELIC_LEN = 0.95;

    /**
     * 날아가는 창 그림 한 자루 (rpg:rfx_relic — 모델 옆모습, 날아가는 쪽으로 눕는 창): 가운데 at, 창끝 방향 dir, 길이 len 칸, 초당 speed 칸으로 dir 쪽,
     * cell = tools/java_fx_art.py RELIC 순서 (게이 볼그 1 — 예전 브류나크에서 옮김)
     */
    static void relicSpear(ServerLevel level, Vec3 at, Vec3 dir, double len, double life, double speed, int cell, double alpha) {
        RuneKit.rfx(level, "relic", at, Opt.of("dir", dir, "size", len / RELIC_LEN, "life", life, "speed", speed, "cell", (double) cell, "alpha", alpha,
                "fade", 0.25));
    }

    /** 앞 cone° · range 칸 안의 보이는 적: 조준한 적이 먼저, 그다음 화면 가운데에 가까운 순 (max 명 — 카두케우스, 예전 브류나크에서 옮김) */
    static List<LivingEntity> targetsIn(ServerPlayer sp, double range, double cone, int max) {
        Vec3 eye = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        double half = Math.toRadians(cone / 2);
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, sp.position(), range, 6)) {
            Vec3 d = V.sub(Kit.bodyPoint(e), eye);
            double len = V.len(d);
            if (len < 0.5 || Math.acos(Math.max(-1, Math.min(1, V.dot(d, view) / len))) > half + Math.asin(Math.min(1, Kit.bodyPad(e) / len))) continue;
            list.add(e);
        }
        list = Kit.visibleFrom(sp, eye, list);
        list.sort(Comparator.comparingDouble(e -> {
            Vec3 d = V.unit(V.sub(Kit.bodyPoint(e), eye));
            return -V.dot(d, view) + V.len(V.sub(e.position(), sp.position())) * 0.002;
        }));
        LivingEntity aim = Kit.pickTarget(sp, range, 0);
        if (aim != null && list.remove(aim)) list.add(0, aim);
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }
}
