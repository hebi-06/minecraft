package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 0.20.0 일곱째 한정 룬 심연의 낙인 (abyss · abyssBrand — 사용자가 고름: 추천 유틸 중 적 약화, "곱연산으로"). 피해 없음.
 * <ul>
 * <li>흐름: 조준한 곳 (range 칸 — 조준한 적의 발밑 · 시선이 닿은 땅) 에 심연이 열림 (갈라진 검은 바닥 · 영역 문양 · 검은 가시 손) → charge 틱에 반지름 radius 칸 안
 * 적 (몬스터 · 보스 · 보스 소품 — 플레이어 · 동물은 아님) 마다 심연의 사슬 둘이 땅에서 감기며 머리 위에 낙인 (도는 분홍 문양) → ticks 틱</li>
 * <li>낙인: 그 적이 받는 피해 × (1 + 낙인) — 모든 플레이어가 준 피해 (평타 · 스킬 · 룬 — 바닐라 경로는 LivingEntity.hurtServer 앞, 보스 엔진은
 * Bosses.absorb: RpgCombat.takenMult), 그 적이 플레이어에게 주는 피해 × (1 − 낙인) (RpgHealth.weakenOf — 받는 쪽 감소와 곱).
 * 세계수의 가호 (공격 쪽 dealtMult · 받는 쪽 buffReduce) 와는 곱 (사용자 "곱연산" — 가호 20% + 낙인 20% = 1.2 × 1.2 = 1.44배 · 0.8 × 0.8 = 0.64배).
 * 낙인 = Runes.brandUp / brandCut (시전자의 네 스탯의 합 · 돌파 계수 — 0돌 20% · 3돌 26% · 6돌 32%), 낙인끼리는 센 것 하나 (같으면 오래 가는 것)</li>
 * <li>숫자: 낙인 찍힌 적에게 들어간 피해 중 낙인 몫 (준 피해 × 낙인 ÷ (1 + 낙인)) 과 낙인으로 줄인 피해를 시전마다 모아, 끝나면 시전자 눈앞에
 * "── 낙인 · 더한 피해 ──" · "── 낙인 · 줄인 피해 ──". 낙인 찍힌 적의 피해 숫자 앞에 분홍 ◆ (DamageNumbers.victimMark), 낙인 찍힌 적의 공격이
 * 약해지면 그 적에게 어두운 금이 감 (잦으면 잠깐에 한 번). 시전자 상태 줄 "낙인 3체 7.5초"</li>
 * <li>6돌 (finale — 심연의 문): 낙인 찍힌 적 발밑에 심연의 문양 · 검은 손이 계속 솟음 (보기만)</li>
 * </ul>
 * 하늘빛 · 번쩍임 (3돌부터): 블랙홀의 공허 하늘 (ULT_ALIAS abyssBrand → blackHole). 조준한 곳 반지름 안에 적이 없으면 재사용 대기 없이 거절.
 */
final class RuneAbyss {
    private RuneAbyss() {}

    static final String FN = "abyssBrand";
    private static final double[][] COLOR = RuneKit.colorOf("abyss");
    /** 핏빛 보라 (계열 첫째 색) · 분홍 심지 · 짙은 심연 (거의 검은 보라) · 밝은 자홍 */
    static final double[] WINE = COLOR[0];
    static final double[] PINK = COLOR[1];
    static final double[] VOID = {0.16, 0.03, 0.24};
    static final double[] MAGENTA = {0.95, 0.24, 0.68};
    /** 숫자 앞 표시 (낙인 찍힌 적) */
    static final String MARK = "§d◆";
    /** 검은 가시 손 (rpg:rfx_thorn — 위 줄 몸 0 ~ 2, 아래 줄 빛 3 ~ 5) */
    static final int THORN_CORE = 3;

    /** 낙인 하나 (적마다): 어느 시전의 것 · 끝 틱 · 받는 피해 + · 주는 피해 − · 그 적 */
    private record Brand(Cast cast, long until, double up, double cut, LivingEntity e) {}

    /** 시전 하나: 시전자 · 낙인이 끝나는 틱 · 낙인으로 더한 피해 · 줄인 피해 · 낙인 찍은 적 수 · 요약을 띄웠는지 · 6돌 */
    static final class Cast {
        final UUID caster;
        final long until;
        final boolean fin;
        double extra;
        double saved;
        int marked;
        boolean shown;

        Cast(UUID caster, long until, boolean fin) {
            this.caster = caster;
            this.until = until;
            this.fin = fin;
        }
    }

    private static final Map<UUID, Brand> BRANDS = new HashMap<>();
    private static final List<Cast> CASTS = new ArrayList<>();
    /** 시험 · 사진첩 · 상태 줄: 사람마다 마지막 시전 */
    private static final Map<UUID, Cast> LAST = new HashMap<>();
    /** 약해진 공격 연출을 그린 틱 (적마다) */
    private static final Map<UUID, Long> WEAK_CUE = new HashMap<>();

    static void register() {
        RuneCast.register(FN, RuneAbyss::cast);
        // 훅: 앞에 다른 것이 있으면 이어서 (배율은 곱, 약화는 1 − (1 − a)(1 − b))
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * takenMult(e);
        java.util.function.ToDoubleFunction<Entity> wk = RpgHealth.weakenOf;
        RpgHealth.weakenOf = e -> 1 - (1 - wk.applyAsDouble(e)) * (1 - weakenOf(e));
        RpgHealth.WeakenSaved ws = RpgHealth.weakenSaved;
        RpgHealth.weakenSaved = (p, e, a) -> {
            ws.saved(p, e, a);
            weakened(p, e, a);
        };
        DamageNumbers.DealtHook dh = DamageNumbers.dealtHook;
        DamageNumbers.dealtHook = (a, v, d) -> {
            dh.dealt(a, v, d);
            dealt(a, v, d);
        };
        java.util.function.Function<LivingEntity, String> vm = DamageNumbers.victimMark;
        DamageNumbers.victimMark = e -> {
            String s = vm.apply(e);
            return active(e) != null ? MARK + (s == null ? "" : s) : s;
        };
        Notify.addHudSuffix(RuneAbyss::hud);
        ServerTickEvents.END_SERVER_TICK.register(RuneAbyss::tick);
        RuneSystem.onForget(RuneAbyss::forget);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BRANDS.clear();
            CASTS.clear();
            LAST.clear();
            WEAK_CUE.clear();
        });
    }

    /** 나감 · 기록 지우기: 이 사람이 건 시전의 요약은 띄우지 않음 (찍힌 낙인은 그대로 — 남은 사람에게 도움) */
    private static void forget(UUID id) {
        CASTS.removeIf(c -> c.caster.equals(id));
        LAST.remove(id);
    }

    // ------------------------------------------------------------ 낙인 (훅)

    /** 지금 찍힌 낙인 (끝났거나 적이 없어졌으면 null) */
    private static Brand active(Entity e) {
        if (e == null) return null;
        Brand b = BRANDS.get(e.getUUID());
        if (b == null || b.until() <= Tasks.now() || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive())) return null;
        return b;
    }

    /** 플레이어가 준 피해에 곱하는 배율 (낙인이 없으면 1) */
    static double takenMult(LivingEntity e) {
        Brand b = active(e);
        return b == null ? 1 : 1 + b.up();
    }

    /** 이 적이 플레이어에게 주는 피해의 감소 (낙인이 없으면 0) */
    static double weakenOf(Entity e) {
        Brand b = active(e);
        return b == null ? 0 : b.cut();
    }

    /** 낙인 찍힌 적에게 피해가 들어감: 낙인 몫을 시전에 더함 */
    private static void dealt(ServerPlayer attacker, LivingEntity victim, double dealt) {
        Brand b = active(victim);
        if (b == null || !(b.up() > 0) || !(dealt > 0)) return;
        b.cast().extra += dealt * b.up() / (1 + b.up());
    }

    /** 낙인 찍힌 적의 공격이 약해짐: 줄인 양을 시전에 더함 · 그 적에게 어두운 금 (적마다 10틱에 한 번) */
    private static void weakened(ServerPlayer victim, Entity attacker, double amount) {
        Brand b = active(attacker);
        if (b == null || !(amount > 0)) return;
        b.cast().saved += amount;
        long now = Tasks.now();
        Long last = WEAK_CUE.get(attacker.getUUID());
        if (last != null && now - last < 10) return;
        WEAK_CUE.put(attacker.getUUID(), now);
        if (WEAK_CUE.size() > 256) WEAK_CUE.values().removeIf(t -> now - t > 40);
        if (attacker instanceof LivingEntity le && le.level() instanceof ServerLevel level) {
            Vec3 at = RuneLimited.body(le);
            RuneKit.glowCrack(level, at, 0.7, VOID, MAGENTA, Opt.of("dir", V.unit(V.sub(victim.getEyePosition(), at)), "life", 0.45, "v", Math.floor(Kit.rand() * 2)));
            SkillFx.sparks(level, at, PINK, 5, 2.5);
            SkillFx.play(level, "random.glass", at, 0.6, 0.35);
        }
    }

    /** 상태 줄 (시전자): "낙인 3체 7.5초" — 마지막 시전의 낙인이 남아 있는 동안 */
    private static String hud(ServerPlayer sp) {
        Cast c = LAST.get(sp.getUUID());
        if (c == null) return "";
        long now = Tasks.now();
        if (c.until <= now) return "";
        int n = 0;
        for (Brand b : BRANDS.values()) if (b.cast() == c && active(b.e()) == b) n++;
        if (n == 0) return "";
        return "§d낙인 §f" + n + "체 §7" + Js.toFixed(Math.max(0, (c.until - now) / 20.0), 1) + "초";
    }

    /** 시험용: 이 사람의 마지막 시전 {더한 피해, 줄인 피해, 낙인 찍은 적 수, 요약을 띄웠으면 1, 낙인이 끝나는 틱} (없으면 null) */
    static double[] last(UUID id) {
        Cast c = LAST.get(id);
        return c == null ? null : new double[] {c.extra, c.saved, c.marked, c.shown ? 1 : 0, c.until};
    }

    /** 매 틱: 끝난 낙인 (흩어짐) · 낙인 찍힌 적의 문양 (적마다 10틱에 한 번 — 엇갈리게) · 끝난 시전의 요약 */
    private static void tick(MinecraftServer server) {
        long now = Tasks.now();
        if (!BRANDS.isEmpty()) {
            int drawn = 0;
            for (Iterator<Map.Entry<UUID, Brand>> it = BRANDS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Brand> en = it.next();
                Brand b = en.getValue();
                LivingEntity e = b.e();
                if (e.isRemoved() || !e.isAlive()) {
                    it.remove();
                    continue;
                }
                if (now >= b.until()) {
                    it.remove();
                    fadeFx(e);
                    continue;
                }
                if ((now + (en.getKey().hashCode() & 7)) % 10 == 0 && drawn < 16) {
                    drawn++;
                    brandFx(e, b.cast(), now);
                }
            }
        }
        if (!CASTS.isEmpty()) {
            for (Iterator<Cast> it = CASTS.iterator(); it.hasNext(); ) {
                Cast c = it.next();
                if (now < c.until + 2) continue;
                it.remove();
                summary(server, c);
            }
        }
    }

    // ------------------------------------------------------------ 시전

    /** 낙인을 찍을 적: 조준한 곳 반지름 안 (몸 가운데 · 위아래 4칸), 적대 무리 (몬스터 · 보스 · 소품 · 허수아비) — 플레이어 · 동물은 아님 */
    static List<LivingEntity> targets(ServerPlayer sp, Vec3 c, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, radius, 4)) if (!(e instanceof Player) && Kit.splashFoe(sp, e)) out.add(e);
        return out;
    }

    static boolean cast(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = RuneLimited.finale(sp, r);
        double radius = k.num("radius");
        double range = k.numOr("range", 20);
        int ticks = (int) k.num("ticks");
        int charge = Math.max(4, (int) k.numOr("charge", 14));
        Vec3 c = RuneKit.groundAim(sp, range);
        if (targets(sp, c, radius).isEmpty()) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7낙인을 찍을 적이 없습니다 (조준한 곳 " + Js.str(radius) + "칸 안)");
            return false;
        }
        double applied = Runes.runeStat(RpgStats.effective(sp), r.family()).value();
        double up = Runes.brandUp(k, applied);
        double cut = Runes.brandCut(k, applied);
        Cast cast = new Cast(sp.getUUID(), Tasks.now() + charge + ticks, fin);
        LAST.put(sp.getUUID(), cast);
        RuneKit.ultSkyFor(sp, level, c, FN, charge + 30, null); // 공허 하늘은 심연이 열리는 동안만 (사진 검토: 길면 낙인 찍힌 적이 어둠에 묻힘)

        // ---- 쓸 때: 시전자 발밑 룬 · 손에서 조준한 곳으로 날아가는 심연의 빛 · 조준한 곳에 심연이 열림 (영역 문양 · 소용돌이 · 금) · 소리
        SkillFx.rune(level, sp.position(), Opt.of("size", 1.4, "life", (charge + 4) / 20.0, "color", WINE, "alpha", 0.9, "rate", 140.0, "progress", 1.0));
        Vec3 hand = V.add(V.up(sp.getEyePosition(), -0.4), V.rightOf(Kit.facingOf(sp)), 0.35);
        RuneKit.flyStreak(level, hand, V.up(c, 0.4), 34, VOID, MAGENTA, 0.9, 0.3);
        RuneKit.domainSigil(level, c, radius, "arcane", VOID, MAGENTA, Opt.of("life", (charge + 24) / 20.0, "spinRate", -14.0, "alpha", 0.8, "grow", 0.08, "fade", 0.3,
                "coreAlpha", 0.7));
        RuneKit.glowCrack(level, c, radius * 0.7, VOID, MAGENTA, Opt.of("life", (charge + 20) / 20.0, "v", 0.0));
        SkillFx.fx(level, "rpg:ground_crack", V.up(c, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.6, "life", (charge + 30) / 20.0,
                "color", VOID, "alpha", 0.9));
        SkillFx.play(level, "mob.elderguardian.curse", c, 0.7, 0.8);
        SkillFx.play(level, "mob.warden.heartbeat", c, 0.8, 1.0);
        RuneKit.selfCue(sp, WINE, PINK, true);

        // ---- 모임 (0 ~ charge): 영역 안 땅에서 검은 가시 손이 솟았다 꺼짐 · 가운데로 빨려 드는 분홍 알갱이
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= charge) return false;
            if (t % 2 == 0) {
                double a = Kit.rand() * Math.PI * 2;
                double rr = Math.sqrt(Kit.rand()) * radius;
                Vec3 g = new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr);
                if (Math.hypot(g.x - sp.getX(), g.z - sp.getZ()) > 2.5) hand(level, g, 0.7 + Kit.rand() * 0.5, 0.5);
                Vec3 p = new Vec3(c.x + Math.cos(a + 1) * radius, c.y + 0.4 + Kit.rand() * 1.5, c.z + Math.sin(a + 1) * radius);
                Vec3 to = V.sub(V.up(c, 0.6), p);
                SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(to), "speed", V.len(to) / 0.4, "size", 0.14, "life", 0.4, "color", MAGENTA, "alpha", 1.0));
            }
            if (t % 5 == 0) SkillFx.shock(level, c, WINE, radius * 0.25, 0.4);
            return true;
        });

        // ---- 낙인 (charge 틱): 반지름 안 적마다 사슬 둘 · 머리 위 낙인 · 번쩍임 → 낙인
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            brandAll(sp, level, cast, c, radius, up, cut, ticks, fin);
        });
        return true;
    }

    /** 조준한 곳 반지름 안 적에게 낙인: 사슬 · 낙인 문양 · 터짐 · 소리 · 알림 (찍은 수 — 없으면 알림만) */
    private static void brandAll(ServerPlayer sp, ServerLevel level, Cast cast, Vec3 c, double radius, double up, double cut, int ticks, boolean fin) {
        long until = cast.until;
        RuneKit.novaRing(level, c, radius * 0.5, 1.0, VOID, MAGENTA, Opt.of("life", 0.55));
        RuneKit.zonePulse(level, c, MAGENTA, radius, 0.6);
        RuneKit.ultFlashFor(sp, level, c, FN, "flash", Opt.empty());
        SkillFx.play(level, "place.chain", c, 0.6, 1.4);
        SkillFx.play(level, "mob.elderguardian.curse", c, 1.2, 0.6);
        SkillFx.play(level, "mob.warden.sonic_charge", c, 1.6, 0.5);
        if (fin) {
            // 6돌 심연의 문: 영역 바닥이 더 크게 갈라지고 거꾸로 도는 둘째 문양 · 사방으로 검은 손
            SkillFx.fx(level, "rpg:ground_crack", V.up(c, 0.06), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 2.2, "life", 2.0, "color", VOID,
                    "alpha", 0.95));
            RuneKit.domainSigil(level, c, radius * 0.75, "blade", WINE, PINK, Opt.of("life", 1.8, "spinRate", 22.0, "alpha", 0.75, "grow", 0.1, "fade", 0.35));
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10;
                Vec3 g = new Vec3(c.x + Math.cos(a) * radius * 0.7, c.y, c.z + Math.sin(a) * radius * 0.7);
                if (Math.hypot(g.x - sp.getX(), g.z - sp.getZ()) > 2.5) hand(level, g, 1.3, 1.0);
            }
        }
        CASTS.add(cast);
        int n = 0;
        for (LivingEntity e : targets(sp, c, radius)) {
            Brand old = active(e);
            if (old != null && (old.up() > up + 1e-9 || (Math.abs(old.up() - up) <= 1e-9 && old.until() > until))) continue; // 더 센 (같으면 더 오래 가는) 낙인이 이미
            BRANDS.put(e.getUUID(), new Brand(cast, until, up, cut, e));
            cast.marked++;
            chains(level, e, n, sp.getUUID());
            n++;
        }
        DamageNumbers.showFace(sp, "§d§l낙인 ×" + n, 2.4, 0, -0.2);
        Notify.notify(sp, "§d심연의 낙인 §f적 " + n + "체 §7· §f받는 피해 +" + pct(up) + "% §7· §f주는 피해 -" + pct(cut) + "% §7" + Js.str(ticks / 20.0) + "초", 60);
    }

    private static String pct(double f) {
        return Js.str(Math.round(f * 100));
    }

    // ------------------------------------------------------------ 연출

    /** 검은 가시 손 하나 (rpg:rfx_thorn — 짙은 심연 몸 · 자홍 빛, 밑동 고정): 크기 size · life 초 */
    static void hand(ServerLevel level, Vec3 at, double size, double life) {
        RuneKit.rfx2(level, "thorn", at, Opt.of("size", size, "life", life, "grow", 0.3, "fade", 0.4, "cell", Math.floor(Kit.rand() * 3), "alpha", 1.0), VOID,
                MAGENTA, new int[] {0, THORN_CORE}, null);
    }

    /** 적 하나에 낙인: 땅의 둘레에서 몸으로 감기는 사슬 둘 (짙은 심연 · 자홍 심지) · 발밑 검은 손 · 머리 위 낙인 · 터짐 · 쇠사슬 소리 (차례로 조금씩 늦게) */
    private static void chains(ServerLevel level, LivingEntity e, int idx, UUID caster) {
        RuneKit.later(Math.min(8, idx), () -> {
            if (e.isRemoved()) return;
            Vec3 body = RuneLimited.body(e);
            Vec3 feet = e.position();
            double a0 = Kit.rand() * Math.PI * 2;
            for (int j = 0; j < 2; j++) {
                double a = a0 + j * Math.PI;
                Vec3 g = new Vec3(feet.x + Math.cos(a) * 1.8, feet.y + 0.05, feet.z + Math.sin(a) * 1.8);
                RuneKit.streak(level, g, body, VOID, 0.2, 0.9, 0.95, 0);
                RuneKit.streak(level, g, body, MAGENTA, 0.07, 0.9, 1.0, 0);
                hand(level, g, 0.6, 0.6);
            }
            SkillFx.burst(level, body, MAGENTA, 1.1, 0.25);
            SkillFx.sparks(level, body, PINK, 6, 3);
            // 누가 낙인 찍혔는지 한눈에: 몸을 꿰는 자홍 빛기둥 (0.5초)
            Vec3 pillar = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.5 + 1.2, e.getZ());
            SkillFx.fx(level, "rpg:arrow_shot", pillar, Opt.of("dir", V.UP, "speed", 0.0, "size", 1.7, "thick", 0.42, "life", 0.5, "color", MAGENTA, "alpha", 0.85));
            SkillFx.fx(level, "rpg:arrow_shot", pillar, Opt.of("dir", V.UP, "speed", 0.0, "size", 1.6, "thick", 0.14, "life", 0.45, "color", PINK, "alpha", 1.0));
            mark(e, 0.9, caster);
            SkillFx.play(level, "place.chain", body, 0.8, 0.8);
        });
    }

    /**
     * 머리 위 낙인 (life 초): 시전자 (없으면 가까운 플레이어) 쪽을 보고 서서 도는 자홍 문양 (사진 검토: 눕힌 룬은 멀리서 · 어둠에서 거의 안 보임) ·
     * 가운데 반짝임 · 머리 위에 눕힌 작은 룬
     */
    private static void mark(LivingEntity e, double life, UUID caster) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Vec3 top = new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.6, e.getZ());
        net.minecraft.world.entity.player.Player viewer = caster != null ? level.getPlayerByUUID(caster) : null;
        if (viewer == null || viewer.distanceToSqr(e) > 64 * 64) viewer = level.getNearestPlayer(e, 48);
        Vec3 face = viewer != null ? V.sub(viewer.getEyePosition(), top) : V.SOUTH;
        if (V.len(face) < 1e-3) face = V.SOUTH;
        RuneKit.domainSigil(level, top, 0.42, "arcane", WINE, PINK, Opt.of("dir", V.unit(face), "life", life, "spinRate", 90.0, "alpha", 0.95, "fade", 0.3,
                "coreAlpha", 1.0));
        RuneKit.rfx2(level, "prop", top, Opt.of("size", 0.3, "life", Math.min(0.5, life), "spin", Kit.rand() * 90, "fade", 0.5), WINE, PINK, RuneKit.RFX.get("glint"), null);
        SkillFx.fx(level, "rpg:skill_rune", V.up(top, -0.25), Opt.of("size", 0.5, "life", life, "spin", Kit.rand() * 360, "speed", 160.0, "progress", 1.0, "color",
                MAGENTA, "alpha", 0.8));
    }

    /** 낙인 동안 (10틱마다): 머리 위 낙인 · 몸에서 떨어지는 어두운 방울 · (6돌) 발밑 심연 문양 · 가끔 검은 손 */
    private static void brandFx(LivingEntity e, Cast cast, long now) {
        if (!(e.level() instanceof ServerLevel level)) return;
        boolean fin = cast.fin;
        mark(e, 0.6, cast.caster);
        Vec3 body = RuneLimited.body(e);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(body, 0.3), Opt.of("dir", V.DOWN, "speed", 1.2, "size", 0.12, "life", 0.5, "color", WINE, "alpha", 1.0));
        if (fin) {
            if ((now / 10) % 2 == 0) RuneKit.domainSigil(level, e.position(), Math.max(0.9, e.getBbWidth() * 1.3), "arcane", VOID, MAGENTA,
                    Opt.of("life", 1.1, "spinRate", 40.0, "alpha", 0.75, "fade", 0.3));
            double a = Kit.rand() * Math.PI * 2;
            hand(level, new Vec3(e.getX() + Math.cos(a) * 0.9, e.getY(), e.getZ() + Math.sin(a) * 0.9), 0.5 + Kit.rand() * 0.3, 0.5);
        }
    }

    /** 낙인이 끝남: 분홍 조각이 흩어짐 */
    private static void fadeFx(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Vec3 top = new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.55, e.getZ());
        SkillFx.sparks(level, top, PINK, 6, 2);
        SkillFx.play(level, "random.glass", top, 1.3, 0.25);
    }

    /**
     * 시전의 요약: 시전자 눈앞 5칸에 "── 낙인 · 더한 피해 ──" (아래에 "── 낙인 · 줄인 피해 ──") — 0 이면 그 줄은 없음.
     * 두 줄이면 둘 다 1.25칸 올림 (사진 검토: 아랫줄이 앞 5칸쯤 서 있는 적의 머리 높이에 걸림)
     */
    private static void summary(MinecraftServer server, Cast cast) {
        cast.shown = true;
        ServerPlayer sp = server.getPlayerList().getPlayer(cast.caster);
        if (sp == null || sp.isRemoved()) return;
        ServerLevel level = sp.level();
        Vec3 at = V.up(V.add(sp.getEyePosition(), Kit.facingOf(sp), 5.0), cast.extra > 0 && cast.saved > 0 ? 1.8 : 0.55);
        DamageNumbers.Tone tone = RuneLimited.toneOf(FN);
        float k = 0.72F;
        if (cast.extra > 0) DamageNumbers.showTotal(level, at, cast.extra, "낙인 · 더한 피해", tone, k);
        if (cast.saved > 0) DamageNumbers.showTotal(level, V.up(at, cast.extra > 0 ? -1.25 : 0), cast.saved, "낙인 · 줄인 피해", tone, k * 0.82F);
        if (cast.extra > 0 || cast.saved > 0) {
            SkillFx.sparks(level, at, PINK, 8, 2);
            SkillFx.playFor(sp, "chime.amethyst_block", 0.8, 0.7);
        }
    }
}
