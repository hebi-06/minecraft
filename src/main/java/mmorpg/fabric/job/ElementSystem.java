package mmorpg.fabric.job;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Elements;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneMarks;
import mmorpg.fabric.skill.Kit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-07 원소 반응 (core.Elements — rules_more.json ELEMENTS): 원소 스킬 (직업 스킬 jobs.json elem · 룬 RUNE_SPREAD.runes elem) 을 쓰는 동안의 원소 맥락
 * (Tasks.elem — JobSystem · RuneCast 가 정하고 later 가 이어 줌) 에서 들어간 직업 · 룬 피해가 맞은 적에 원소를 묻힘 (6초 · 머리 위 원소 그림). 같은 원소면 다시 6초,
 * 다른 원소가 묻은 적이면 반응 (두 원소를 지움 · 같은 적은 2초에 한 번 — 그동안 묻은 원소는 그대로): 증발 · 쇄빙 (즉시 피해) · 과부하 · 열충격 (둘레 폭발) · 감전 · 빙결
 * (기절) · 진흙 (묶음) · 초전도 (둘레 받는 피해 +10%) · 용암 (불타는 땅) · 자력 (끌어모음). 반응의 위력 = 반응을 일으킨 사람의 공격력 (직업 위력 · 룬 위력 — 스킬 배율 · 피해
 * 배율 없이) × 스탯 트리 공격력 · 가호 × power (사용자 "내 공격력의 % (고정)"). 보스는 기절 · 묶음 대신 구속 II · 끌려오지 않음. 플레이어 (결투) 에게는 묻지 않음
 */
public final class ElementSystem {
    private ElementSystem() {}

    /** 원소 맥락: 원소 (다섯 중 하나 · multi = 타마다 {@link #with}) · 공격력 (반응 위력의 바탕) · 직업 피해인지 (스탯 트리 공격력 — 아니면 룬 피해) */
    public record Ctx(String el, double power, boolean job) {}

    private static final class Aura {
        final LivingEntity e;
        final String el;
        long until;

        Aura(LivingEntity e, String el, long until) {
            this.e = e;
            this.el = el;
            this.until = until;
        }
    }

    private static final Map<UUID, Aura> AURA = new HashMap<>();
    private static final Map<UUID, Long> ICD = new HashMap<>();
    /** 시험 · 사진첩: 터진 반응 (마지막 64 — "id@엔티티 번호") */
    public static final Deque<String> LOG = new ArrayDeque<>();
    /** 한 틱에 다시 그리는 머리 위 원소 그림 수 (파티클 예산) */
    private static final int ICONS_PER_TICK = 12;
    /** 머리 위 원소 그림을 다시 그리는 틱 (수명은 조금 더 길게 — 같은 그림을 solo 로 바꿔 끼움) */
    private static final int ICON_EVERY = 10;
    private static long ticks;
    /** 게임 시험 · 영상의 과녁 꼬리표 (TestDummies.TAG) — 원소는 {@link #TEST_ELEMENTS} 가 같이 있을 때만 */
    public static final String TEST_DUMMY = "mmorpg_test_dummy";
    /** 원소 반응을 보는 시험 · 영상의 과녁 꼬리표 */
    public static final String TEST_ELEMENTS = "mmorpg_elements";

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ElementSystem::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> testClear());
    }

    // ------------------------------------------------------------ 맥락

    /** 원소 맥락을 정하고 예전 값을 돌려줌 (null 원소 = 무속성 — 바깥 맥락을 이어 받지 않게 지움). 끝나면 {@link #leave} */
    public static Object enter(String el, double power, boolean job) {
        return Tasks.elem(el == null || !Elements.on() ? null : new Ctx(el, power, job));
    }

    public static void leave(Object prev) {
        Tasks.elem(prev);
    }

    /** 지금 원소 맥락 (없으면 null) */
    public static Ctx ctx() {
        return Tasks.elem() instanceof Ctx c ? c : null;
    }

    /** fn 동안 원소를 el 로 (지금 맥락의 공격력 · 종류 그대로 — 맥락이 없으면 그냥 fn): multi 스킬의 타마다 (그 안의 later 도 이어 받음) */
    public static void with(String el, Runnable fn) {
        Ctx c = ctx();
        if (c == null) {
            fn.run();
            return;
        }
        Object prev = Tasks.elem(new Ctx(el, c.power(), c.job()));
        try {
            fn.run();
        } finally {
            Tasks.elem(prev);
        }
    }

    /** {@link #with(String, Runnable)} 와 같음 — 피해 한 번의 값을 돌려줌 (RuneKit.swordHit · spellHit) */
    public static double withHit(String el, java.util.function.DoubleSupplier fn) {
        double[] out = {0};
        with(el, () -> out[0] = fn.getAsDouble());
        return out[0];
    }

    /** fn 동안 원소 맥락 (공격력을 줌 — 쓰는 동안 밖에서 나가는 원소 타격: 정령 · 태세의 평타 덤) */
    public static void with(String el, double power, boolean job, Runnable fn) {
        Object prev = enter(el, power, job);
        try {
            fn.run();
        } finally {
            leave(prev);
        }
    }

    // ------------------------------------------------------------ 묻힘 · 반응

    /** 직업 · 룬 피해가 들어간 뒤 (JobKit.hit · hitSplit · RuneKit.runeHit): 지금 맥락의 원소를 묻힘 (다른 원소면 반응) */
    public static void onHit(ServerPlayer sp, LivingEntity e) {
        Ctx c = ctx();
        if (c == null || !Elements.known(c.el())) return;
        apply(sp, e, c.el(), c.power(), c.job(), false);
    }

    /**
     * 원소 el 을 e 에 묻힘 (sp = 묻힌 사람 · 반응을 일으킨 사람, power · job = 반응 위력의 바탕). ignoreIcd = 같은 적 2초 제한을 넘음 (엘리멘탈 블레이드 궁극기의
     * 이어지는 반응). 터진 반응 (없으면 null)
     */
    public static Elements.Reaction apply(ServerPlayer sp, LivingEntity e, String el, double power, boolean job, boolean ignoreIcd) {
        if (!Elements.on() || sp == null || e == null || !Elements.known(el)) return null;
        if (e instanceof Player || e.isRemoved() || !e.isAlive() || e.getHealth() <= 0) return null;
        // 시험 과녁 (게임 시험 · 영상 — mmorpg_test_dummy) 은 꼬리표 mmorpg_elements 가 있을 때만 (옛 시험의 피해 숫자가 반응으로 바뀌지 않게)
        if (e.entityTags().contains(TEST_DUMMY) && !e.entityTags().contains(TEST_ELEMENTS)) return null;
        long now = Tasks.now();
        UUID id = e.getUUID();
        Aura a = AURA.get(id);
        if (a != null && (a.until <= now || a.e != e)) {
            AURA.remove(id);
            a = null;
        }
        if (a == null) {
            AURA.put(id, new Aura(e, el, now + Elements.auraTicks()));
            ElementFx.applied(e, el, true);
            return null;
        }
        if (a.el.equals(el)) {
            a.until = now + Elements.auraTicks();
            ElementFx.applied(e, el, false);
            return null;
        }
        if (!ignoreIcd && ICD.getOrDefault(id, Long.MIN_VALUE) > now) return null; // 반응을 기다림: 묻은 원소는 그대로
        Elements.Reaction r = Elements.reaction(a.el, el);
        if (r == null) return null;
        AURA.remove(id);
        ICD.put(id, now + Elements.icdTicks());
        react(sp, e, r, a.el, el, power, job);
        return r;
    }

    /** 반응 하나 (first = 묻어 있던 원소 · then = 친 원소 — 숫자 그라데이션 · 그림 순서) */
    private static void react(ServerPlayer sp, LivingEntity e, Elements.Reaction r, String first, String then, double power, boolean job) {
        LOG.addLast(r.id() + "@" + e.getId());
        while (LOG.size() > 64) LOG.removeFirst();
        double base = power * (job ? mmorpg.fabric.stattree.StatTrees.atkMult(sp) : mmorpg.fabric.stattree.StatTrees.runeMult(sp)) * RpgCombat.dealtMult.applyAsDouble(sp);
        int[] grad = {Jobs.rgb(ElementFx.glow(first)), Jobs.rgb(ElementFx.glow(then))};
        boolean boss = JobKit.isBoss(e) || mmorpg.fabric.boss.Bosses.isBossEntity(e);
        ElementFx.reaction(sp, e, r, first, then);
        switch (r.kind()) {
            case "hit" -> hitNow(sp, e, base * r.num("power", 0), style(r), grad);
            case "blast" -> {
                List<LivingEntity> list = new ArrayList<>(Kit.around(sp, e.position(), r.num("radius", 3)));
                if (!list.contains(e)) list.add(0, e);
                for (LivingEntity x : list) hitNow(sp, x, base * r.num("power", 0), style(r), grad);
            }
            case "stun" -> {
                if (boss) RuneKit.effect(e, "slowness", (int) r.num("bossSlow", 40), 1);
                else RuneKit.stun(e, (int) r.num("ticks", 20), ElementFx.core(then), true);
            }
            case "root" -> {
                if (boss) RuneKit.effect(e, "slowness", (int) r.num("bossSlow", 40), 1);
                else RuneKit.effect(e, "slowness", (int) r.num("ticks", 40), 10);
            }
            case "vuln" -> {
                int n = 0;
                List<LivingEntity> list = new ArrayList<>(Kit.around(sp, e.position(), r.num("radius", 3)));
                if (!list.contains(e)) list.add(0, e);
                for (LivingEntity x : list) RuneMarks.vuln(x, r.num("add", 0.1), (int) r.num("ticks", 100), ElementFx.glow(first), ElementFx.core(then), n++ < 6);
            }
            case "field" -> lava(sp, e, r, base, grad);
            case "pull" -> {
                String life = RuneKit.lifeToken(sp);
                for (LivingEntity x : Kit.around(sp, e.position(), r.num("radius", 5))) {
                    if (x == e || !JobKit.pushable(x)) continue;
                    double d = Math.hypot(x.getX() - e.getX(), x.getZ() - e.getZ());
                    ElementFx.magnetLine(sp.level(), x, e, first, then);
                    JobKit.reel(sp, x, () -> e.isRemoved() ? x.position() : e.position(), d, (int) r.num("ticks", 6), r.num("stop", 1.2), life);
                }
            }
            default -> {
            }
        }
    }

    /** 불타는 땅 (용암): e 의 발밑 radius 칸에 ticks 동안 every 틱마다 둘레 적에게 base × power (첫 번은 바로) */
    private static void lava(ServerPlayer sp, LivingEntity e, Elements.Reaction r, double base, int[] grad) {
        ServerLevel level = sp.level();
        Vec3 c = e.position();
        double radius = r.num("radius", 2.5);
        int every = Math.max(1, (int) r.num("every", 10));
        int n = Math.max(1, (int) r.num("ticks", 60) / every);
        String life = RuneKit.lifeToken(sp);
        for (int i = 0; i < n; i++) {
            int k = i;
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ElementFx.lavaPulse(level, c, radius, k, n);
                for (LivingEntity x : Kit.around(sp, c, radius, 2)) {
                    if (hitNow(sp, x, base * r.num("power", 0), DamageNumbers.Style.REACT_LAVA, grad)) RuneKit.burn(x, 1);
                }
            });
        }
    }

    /** 반응 피해를 바로 (반응을 일으킨 타격과 같은 틱 — 몹 무적 시간 없이, 그 타격의 무적 시간은 그대로 둠) */
    private static boolean hitNow(ServerPlayer sp, LivingEntity x, double amount, DamageNumbers.Style st, int[] grad) {
        if (x == null || !(amount > 0) || !Kit.canHit(sp, x) || x instanceof Player) return false;
        int cd = x.damageCooldownTime;
        x.damageCooldownTime = 0;
        boolean ok = Kit.applyJobDamage(sp, x, amount, false, Kit.Cause.MAGIC, st, grad);
        if (ok && !x.isRemoved()) x.damageCooldownTime = Math.max(cd, 0);
        return ok;
    }

    /** 반응 숫자 모양 (이름 + 숫자 — DamageNumbers REACT_*) */
    private static DamageNumbers.Style style(Elements.Reaction r) {
        return switch (r.id()) {
            case "vaporize" -> DamageNumbers.Style.REACT_VAPORIZE;
            case "shatter" -> DamageNumbers.Style.REACT_SHATTER;
            case "overload" -> DamageNumbers.Style.REACT_OVERLOAD;
            case "thermal" -> DamageNumbers.Style.REACT_THERMAL;
            default -> DamageNumbers.Style.REACT_LAVA;
        };
    }

    // ------------------------------------------------------------ 틱 · 시험

    private static void tick(MinecraftServer server) {
        if (AURA.isEmpty() && ICD.isEmpty()) return;
        long now = Tasks.now();
        long n = ticks++;
        int drawn = 0;
        for (Iterator<Aura> it = AURA.values().iterator(); it.hasNext(); ) {
            Aura a = it.next();
            LivingEntity e = a.e;
            if (a.until <= now || e.isRemoved() || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (drawn < ICONS_PER_TICK && (n + e.getId()) % ICON_EVERY == 0) {
                drawn++;
                ElementFx.icon(e, a.el, a.until - now <= 20, (ICON_EVERY + 6) / 20.0);
            }
        }
        if (n % 40 == 0) ICD.values().removeIf(t -> t <= now);
    }

    /** 묻은 원소 (없으면 null) */
    public static String auraOf(LivingEntity e) {
        Aura a = e == null ? null : AURA.get(e.getUUID());
        return a == null || a.until <= Tasks.now() || a.e != e ? null : a.el;
    }

    /** 원소가 남은 틱 (없으면 0) */
    public static long auraLeft(LivingEntity e) {
        Aura a = e == null ? null : AURA.get(e.getUUID());
        return a == null || a.e != e ? 0 : Math.max(0, a.until - Tasks.now());
    }

    /** 반응을 다시 일으킬 수 있을 때까지 틱 (없으면 0) */
    public static long icdLeft(LivingEntity e) {
        Long t = e == null ? null : ICD.get(e.getUUID());
        return t == null ? 0 : Math.max(0, t - Tasks.now());
    }

    /** 시험 · 사진첩: 묻은 원소 · 반응 대기 · 기록을 모두 지움 */
    public static void testClear() {
        AURA.clear();
        ICD.clear();
        LOG.clear();
    }

    /** 시험: 이 적의 원소 · 반응 대기만 지움 */
    public static void clearOf(LivingEntity e) {
        if (e == null) return;
        AURA.remove(e.getUUID());
        ICD.remove(e.getUUID());
    }
}
