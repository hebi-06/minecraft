package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-15 팬텀 아처 (궁수 리메이크 — 사용자 "궁수는 정확히는 리메이크임" · "아처 1차는 … 에미야 시로 같은 느낌으로", claude/archer-remake.md): 특성 「환영
 * 무기」 와 직업이 같이 쓰는 그림.
 * <p>
 * 투영: 빛의 선 (푸른 흰빛 2D 선) 이 칼 모양 (칼끝 · 날 어깨 · 코등이 · 자루) 을 그린 뒤 3D 칼 (rune_sword — 반투명 강철빛 날 · 진홍 홈 · 보석) 이 커지며
 * 나타남. 활 평타는 화살 대신 투영한 칼을 쏘고, 맞은 적 몸에 칼이 꽂혀 남음 (적마다 최대 max · 칼마다 life 틱). max 째가 꽂히면 burstDelay 틱 뒤 꽂힌 칼이
 * 모두 터짐 (둘레 radius 칸의 적마다 활 평타 × burst — 칼 파편 · 진홍 폭발). 직업 스킬로 맞혀도 꽂힘 (스킬마다 lodge). 원작의 이름 · 고유 무기 디자인은 쓰지
 * 않음 (느낌만).
 */
public final class PhantomArcher {
    private PhantomArcher() {}

    /** 칼 날 (반투명 강철빛 — rune_sword 물들임 0) · 홈 · 보석 (진홍 — 물들임 1) */
    static final double[] BLADE = {0.9, 0.95, 1.0}; // 2026-11-15 영상: 0.8 · 0.88 은 낮 하늘 앞에서 잿빛으로 가라앉았음
    static final double[] GEM = {0.92, 0.12, 0.14}; // 2026-11-15 미리보기: 더 짙은 진홍
    /** 투영 선 (푸른 흰빛) */
    static final double[] TRACE = {0.42, 0.78, 1.0};
    static final double[] TRACE_CORE = {0.9, 0.97, 1.0};
    /** 폭발 (진홍 불꽃 · 흰 심지) */
    static final double[] FIRE = {1.0, 0.36, 0.12};
    static final double[] FIRE_CORE = {1.0, 0.86, 0.5};

    /** 꽂힌 칼 하나 */
    private static final class Stuck {
        final RuneSwords.Blade blade;
        final long until;
        boolean gone;

        Stuck(RuneSwords.Blade blade, long until) {
            this.blade = blade;
            this.until = until;
        }
    }

    /** 한 사람이 한 적에게 꽂은 칼 */
    private static final class Lodge {
        final List<Stuck> swords = new ArrayList<>();
        boolean bursting;
    }

    private static final Map<String, Lodge> LODGES = new HashMap<>();
    private static final Map<UUID, LivingEntity> LAST = new HashMap<>();

    private static String key(ServerPlayer sp, LivingEntity e) {
        return sp.getUUID() + ":" + e.getUUID();
    }

    private static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static int max(Jobs.Job j) {
        return j == null || j.trait() == null ? 5 : Math.max(1, j.trait().max());
    }

    /** 칼의 넓은 면 (옆 · 조금 위 — 3인칭 옆에서도 1인칭 뒤에서도 날이 보이게) */
    static Vec3 faceOf(Vec3 d) {
        Vec3 side = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : V.rightOf(V.flat(d));
        return V.unit(V.add(side, V.UP, 0.6));
    }

    // ------------------------------------------------------------ 투영

    /**
     * 투영 선: 칼 모양 (자루 hilt → 칼끝, 길이 len 칸 · 넓은 면 face) 을 빛의 선 다섯으로 그림 (칼끝 ← 날 어깨 둘 · 코등이 · 자루) — life 초 뒤 사라짐
     */
    static void traceLines(ServerLevel level, Vec3 hilt, Vec3 dir, Vec3 face, double len, double life) {
        trace2d(level, hilt, dir, len, Math.max(0.3, life));
    }

    // ------------------------------------------------------------ 2026-11-15 단색 블록 볼륨 (사용자 "그냥 위르드처럼" · "도트처럼 보이는게 문제" — BlockFx)
    // (같은 날 그린 2D rfx_jp* 는 바닐라 글꼴에서 픽셀이 그대로 커져 도트로 보였음 → 본체를 무늬 없는 단색 상자 볼륨으로)

    /** 날아가는 칼의 진홍 잔상 둘 (1 · 2틱 늦게 같은 길 — 날 = 진홍 · 홈 = 흰빛, 닿으면 작아져 사라짐) */
    static void afterimages(ServerLevel level, Vec3 from, Vec3 hiltEnd, Vec3 d, double len, int ticks, double[] glow) {
        for (int g = 1; g <= 2; g++) {
            int delay = g;
            double k = 1.0 - 0.12 * g;
            Vfx.Fx fx = RuneProps.fx(level, from, ticks + delay + 8);
            RuneSwords.Blade b = RuneSwords.phantom(fx, from, d, faceOf(d), len * k, 0.05, glow, mmorpg.fabric.vfx.BlockFx.WHITE);
            fx.on(delay, () -> {
                b.grow(1, 1);
                b.pose(V.add(hiltEnd, d, -0.15 * delay), d, faceOf(d), Math.max(1, ticks));
            });
            fx.on(delay + ticks, () -> b.grow(0.05, 3));
            fx.on(delay + ticks + 4, b::remove);
        }
    }

    /** 투영 선: 칼 윤곽 (자루 · 코등이 · 날 양쪽 · 칼끝) 을 가는 푸른 흰빛 줄 다섯으로 — life 초 동안 가늘어지며 사라짐 */
    static void trace2d(ServerLevel level, Vec3 hilt, Vec3 dir, double len, double life) {
        Vec3 d = V.unit(dir);
        Vec3 side = V.rightOf(V.flat(d));
        if (V.len(side) < 1e-3) side = new Vec3(1, 0, 0);
        Vec3 up = V.unit(V.cross(side, d));
        Vec3 guard = V.add(hilt, d, len * 0.22);
        Vec3 tip = V.add(hilt, d, len);
        Vec3 sa = V.add(V.add(guard, d, len * 0.05), up, len * 0.07);
        Vec3 sb = V.add(V.add(guard, d, len * 0.05), up, -len * 0.07);
        int t = Math.max(4, (int) Math.round(life * 20));
        double th = 0.035 + len * 0.012;
        mmorpg.fabric.vfx.BlockFx.beam(level, hilt, guard, th, TRACE, t);
        mmorpg.fabric.vfx.BlockFx.beam(level, V.add(guard, up, len * 0.16), V.add(guard, up, -len * 0.16), th, TRACE, t);
        mmorpg.fabric.vfx.BlockFx.beam(level, sa, tip, th, TRACE, t);
        mmorpg.fabric.vfx.BlockFx.beam(level, sb, tip, th, TRACE, t);
    }

    /** 칼 궤적: 진홍 빛 줄 (흰 심지) — from → to, life 초 동안 가늘어짐 */
    static void streak2d(ServerLevel level, Vec3 from, Vec3 to, double life, double[] glow, double[] core) {
        mmorpg.fabric.vfx.BlockFx.beam(level, from, to, 0.16, glow, Math.max(3, (int) Math.round(life * 20)));
    }

    /** 착탄 섬광: 흰 심 · 진홍 겉 상자가 터졌다 꺼짐 · 강철 조각 여섯 */
    static void impact2d(ServerLevel level, Vec3 at, double size, double[] glow, double[] core) {
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.8, glow, mmorpg.fabric.vfx.BlockFx.WHITE, 6);
        mmorpg.fabric.vfx.BlockFx.shards(level, at, 6, size * 1.4, size * 0.4, BLADE, 8);
    }

    /** X 베기: 보는 쪽 (facing — 쓴 사람 → 과녁) 에 수직인 판에서 엇갈린 빛 호 둘 */
    static void cross2d(ServerLevel level, Vec3 at, Vec3 facing, double size, double[] glow, double[] core) {
        Vec3 u = V.rightOf(V.flat(facing));
        if (V.len(u) < 1e-3) u = new Vec3(1, 0, 0);
        Vec3 v = V.UP;
        double Rc = size * 2.2;
        double span = size * 1.15 / Rc;
        for (int s = 0; s < 2; s++) {
            Vec3 dd = V.unit(s == 0 ? V.sub(u, v) : V.add(V.scale(u, -1), V.scale(v, -1)));
            Vec3 n = V.unit(s == 0 ? V.add(u, v) : V.sub(u, v));
            Vec3 center = V.add(at, n, Rc);
            mmorpg.fabric.vfx.BlockFx.arc(level, center, V.scale(n, -1), dd, Rc, -span, span, 9, size * 0.22, glow, 3, 8);
        }
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.45, glow, mmorpg.fabric.vfx.BlockFx.WHITE, 6);
    }

    /** 폭발: 진홍 · 주황 상자 볼륨 · 흰 심 · 땅 충격파 고리 · 강철 조각 */
    static void blast2d(ServerLevel level, Vec3 at, double size) {
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.75, FIRE, FIRE_CORE, 12);
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.5, GEM, mmorpg.fabric.vfx.BlockFx.WHITE, 9);
        Vec3 g = new Vec3(at.x, RuneKit.groundAt(level, at, at.y - 1) + 0.05, at.z);
        mmorpg.fabric.vfx.BlockFx.ring(level, g, size * 0.3, size * 1.25, 22, 0.32, FIRE, 11);
        mmorpg.fabric.vfx.BlockFx.shards(level, at, 10, size * 1.6, size * 0.35, BLADE, 12);
    }

    /** 불꽃: 주황 · 노란 상자가 솟으며 돌고 작아짐 */
    static void flame2d(ServerLevel level, Vec3 foot, double size, double life) {
        mmorpg.fabric.vfx.BlockFx.flame(level, foot, size * 2.0, FIRE, FIRE_CORE, Math.max(6, (int) Math.round(life * 20)));
    }

    /**
     * 칼 하나를 투영: 선이 먼저 (lines) → appear 틱에 3D 칼이 2틱 동안 커지며 나타남. 칼 (fx 수명 life 틱 — 다 쓰면 remove)
     */
    static RuneSwords.Blade project(ServerLevel level, Vec3 hilt, Vec3 dir, double len, int appear, int life, boolean lines) {
        Vec3 d = V.unit(dir);
        Vec3 face = faceOf(d);
        if (lines) traceLines(level, hilt, d, face, len, Math.max(0.15, (appear + 3) / 20.0));
        Vfx.Fx fx = RuneProps.fx(level, hilt, Math.max(appear + 4, life));
        RuneSwords.Blade b = RuneSwords.phantom(fx, hilt, d, face, len, 0.05, BLADE, GEM);
        fx.on(Math.max(1, appear), () -> b.grow(1, 2));
        return b;
    }

    /** 칼이 부서짐 (빛 조각 · 작은 섬광 — 칼은 지움) */
    static void shatter(ServerLevel level, RuneSwords.Blade b, double size) {
        Vec3 mid = V.lerp(b.hilt(), b.tip(), 0.5);
        JobFx.glint(level, mid, 0.35 * size, BLADE, TRACE_CORE, 0.2, Opt.empty());
        if (RuneKit.fxQuota("phantomShard", 10)) shards(level, mid, 3, 5.0, 0.3 * size);
        b.remove();
    }

    /** 칼 파편 (rfx_icicle — 강철빛 조각이 사방으로) */
    static void shards(ServerLevel level, Vec3 at, int n, double speed, double size) {
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2 + Kit.rand() * 0.5;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.2 + Kit.rand() * 0.7, Math.sin(a)));
            RuneKit.rfx2(level, "icicle", V.add(at, d, 0.25), Opt.of("dir", d, "speed", speed * (0.7 + Kit.rand() * 0.6), "size", size, "life", 0.3, "fade", 0.3),
                    BLADE, TRACE_CORE);
        }
    }

    /** 진홍 폭발 (칼이 터짐 — 크기 size 칸) */
    static void blast(ServerLevel level, Jobs.Job j, Vec3 at, double size) {
        blast2d(level, at, size * 1.1);
        impact2d(level, at, size * 0.8, JobKit.glow(j), JobKit.core(j));
        SkillFx.sparks(level, at, FIRE_CORE, 6 + (int) (size * 3), 5);
        shards(level, at, 4 + (int) (size * 2), 6.0, 0.25 + size * 0.06);
        SkillFx.play(level, "entity.generic.explode", at, 1.5 - Math.min(0.6, size * 0.12), 0.35 + size * 0.1);
        SkillFx.play(level, "block.glass.break", at, 1.3, 0.5);
    }

    // ------------------------------------------------------------ 꽂힌 칼

    /** 이 사람이 이 적에게 꽂은 칼 수 (살아 있는 것만) */
    public static int count(ServerPlayer sp, LivingEntity e) {
        if (sp == null || e == null) return 0;
        Lodge l = LODGES.get(key(sp, e));
        if (l == null) return 0;
        long now = Tasks.now();
        int n = 0;
        for (Stuck s : l.swords) if (!s.gone && s.until > now) n++;
        return n;
    }

    /** 게이지: 마지막으로 맞힌 적에게 꽂힌 칼 수 */
    static int gauge(ServerPlayer sp) {
        LivingEntity e = LAST.get(sp.getUUID());
        if (e == null || e.isRemoved() || !e.isAlive()) return 0;
        return count(sp, e);
    }

    /**
     * 적 e 에게 칼 n 자루를 꽂음 (from 쪽에서 날아옴 — 칼이 몸에 박힌 채 따라다님, 칼마다 life 틱 뒤 빛으로 흩어짐). 가득 (max) 이면 burstDelay 틱 뒤 모두 터짐.
     * 직업 스킬 (skill) 은 max − 1 까지만 꽂음 — 터짐 (다섯째) 은 활 평타로 (CI 43619048420: 블레이드 레인 · 소드 생추어리의 물결이 다섯째를 채우면 터짐이 스킬의 다음
     * 타와 같은 틱에 겹쳐 무적 시간에 10틱 밀림 — 스킬의 마지막 피해가 늦어짐). 꽂은 수
     */
    public static int lodge(ServerPlayer sp, Jobs.Job j, LivingEntity e, int n, Vec3 from) {
        return lodge(sp, j, e, n, from, true);
    }

    /** 활 평타의 칼 (skill false — 다섯째까지 · 터짐) */
    static int lodgeShot(ServerPlayer sp, Jobs.Job j, LivingEntity e, Vec3 from) {
        return lodge(sp, j, e, 1, from, false);
    }

    private static int lodge(ServerPlayer sp, Jobs.Job j, LivingEntity e, int n, Vec3 from, boolean skill) {
        if (sp == null || e == null || e.isRemoved() || !e.isAlive() || n <= 0 || !(e.level() instanceof ServerLevel level)) return 0;
        prune();
        Lodge l = LODGES.computeIfAbsent(key(sp, e), k -> new Lodge());
        if (l.bursting) return 0;
        int max = max(j);
        int life = (int) trait(j, "life", 160);
        long now = Tasks.now();
        l.swords.removeIf(s -> s.gone || s.until <= now);
        int add = Math.min(n, (skill ? max - 1 : max) - l.swords.size());
        Vec3 body = Kit.bodyPoint(e);
        Vec3 in = V.sub(body, from);
        Vec3 base = V.len(in) > 0.1 ? V.unit(in) : Kit.facingOf(sp);
        double pad = Kit.bodyPad(e);
        double len = 1.15 + pad * 0.3; // 2026-11-15 컨셉아트: 꽂힌 칼이 몸에서 잘 보이게
        for (int i = 0; i < add; i++) {
            Vec3 d = V.unit(V.add(base, new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.5 - 0.35, Kit.rand() - 0.5), 0.55));
            Vec3 tip = V.add(body, new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.8 - 0.3, Kit.rand() - 0.5), 0.3 + pad * 0.4);
            double reach = len * 0.9;
            Vec3 hilt = V.add(tip, d, -reach * 0.72);
            Vec3 off = V.sub(hilt, body);
            Vec3 face = faceOf(d);
            Vfx.Fx fx = RuneProps.fx(level, hilt, life + 12);
            RuneSwords.Blade b = RuneSwords.phantom(fx, V.add(hilt, d, -0.35), d, face, len, 0.6, BLADE, GEM);
            Stuck s = new Stuck(b, now + life);
            fx.on(1, () -> {
                b.grow(1, 1);
                b.pose(V.add(Kit.bodyPoint(e), off), d, face, 1);
            });
            fx.each(3, life - 1, 2, t -> {
                if (s.gone || e.isRemoved()) return;
                b.pose(V.add(Kit.bodyPoint(e), off), d, face, 2);
            });
            fx.on(life, () -> {
                if (s.gone) return;
                s.gone = true;
                b.grow(0.1, 5);
            });
            fx.on(life + 6, b::remove);
            l.swords.add(s);
        }
        LAST.put(sp.getUUID(), e);
        if (add > 0) {
            mmorpg.fabric.vfx.BlockFx.star(level, body, 0.8 + pad * 0.4, JobKit.glow(j)); // 2026-11-15 꽂힐 때 가벼운 번쩍 (평타마다라 폭발 대신)
            SkillFx.play(level, "item.trident.hit", body, 1.3 + Kit.rand() * 0.3, 0.45);
        }
        if (l.swords.size() >= max) {
            l.bursting = true;
            int delay = (int) trait(j, "burstDelay", 10);
            Weapon w0 = RpgCombat.weaponOf(sp.getMainHandItem());
            String life0 = RuneKit.lifeToken(sp);
            RuneKit.later(delay, () -> burst(sp, j, e, l, w0, life0));
        }
        JobTraits.sendGauge(sp, false);
        return add;
    }

    /** 가득 찬 칼이 터짐: 둘레 radius 칸의 적마다 활 평타 × burst (평타 그대로 — 치명타 · 더한 피해) · 칼 파편 · 진홍 폭발 */
    private static void burst(ServerPlayer sp, Jobs.Job j, LivingEntity e, Lodge l, Weapon w0, String life) {
        l.bursting = false;
        if (!(e.level() instanceof ServerLevel level)) {
            clear(l, null);
            return;
        }
        Vec3 at = Kit.bodyPoint(e);
        clear(l, level);
        if (e.isRemoved() || !RuneKit.sameLife(sp, life)) return;
        blast(level, j, at, 1.4 + Kit.bodyPad(e) * 0.6);
        mmorpg.fabric.vfx.BlockFx.ring(level, V.up(e.position(), 0.05), 0.4, trait(j, "radius", 2), 16, 0.25, JobKit.glow(j), 7); // 2026-11-15 2D 고리 대신 단색 블록
        Weapon held = RpgCombat.weaponOf(sp.getMainHandItem());
        Weapon w = held != null && "bow".equals(held.type()) ? held : w0;
        if (w == null || !"bow".equals(w.type())) return;
        double ratio = trait(j, "burst", 1.0);
        for (LivingEntity x : Kit.around(sp, e.position(), trait(j, "radius", 2), 2.5)) RpgCombat.bonusHit(sp, x, w, ratio);
        JobTraits.sendGauge(sp, false);
    }

    /** 꽂힌 칼을 모두 부숨 (level 이 있으면 조각 · 섬광) */
    private static void clear(Lodge l, ServerLevel level) {
        for (Stuck s : l.swords) {
            if (s.gone) continue;
            s.gone = true;
            if (level != null) shatter(level, s.blade, 1);
            else s.blade.remove();
        }
        l.swords.clear();
    }

    /** 꽂힌 칼을 모두 터뜨려 없앰 (팬텀 버스트 — 피해는 그 스킬이 넣음): 터뜨린 수 */
    public static int detonate(ServerPlayer sp, LivingEntity e) {
        int n = count(sp, e);
        Lodge l = LODGES.get(key(sp, e));
        if (l == null) return 0;
        clear(l, e.level() instanceof ServerLevel lv ? lv : null);
        l.bursting = false;
        return n;
    }

    /** 다 끝난 기록을 지움 (128 개가 넘을 때) */
    private static void prune() {
        if (LODGES.size() < 128) return;
        long now = Tasks.now();
        Iterator<Map.Entry<String, Lodge>> it = LODGES.entrySet().iterator();
        while (it.hasNext()) {
            Lodge l = it.next().getValue();
            l.swords.removeIf(s -> s.gone || s.until <= now);
            if (l.swords.isEmpty() && !l.bursting) it.remove();
        }
    }

    /** 시험: 이 사람의 기록을 지움 */
    public static void testReset(ServerPlayer sp) {
        String p = sp.getUUID() + ":";
        LODGES.entrySet().removeIf(en -> {
            if (!en.getKey().startsWith(p)) return false;
            clear(en.getValue(), null);
            return true;
        });
        LAST.remove(sp.getUUID());
    }

    // ------------------------------------------------------------ 활 평타 (JobTraitsMore 훅)

    /** 쏨: 활 위에 칼이 투영되어 (섬광) 화살 대신 날아감 — 평타 빛줄과 같은 틱에 닿음. 맞으면 onHit 이 꽂음, 빗나가면 끝에서 사라짐 */
    static void onFired(ServerPlayer sp, Jobs.Job j, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
        ServerLevel level = sp.level();
        Vec3 d = V.unit(dir);
        double len = 1.45; // 2026-11-15 컨셉아트: 평타 칼 크게
        int ticks = Math.max(1, (int) Math.round(RpgCombat.arrowShot(dist).life() * 20));
        Vec3 start = V.add(from, d, 0.2);
        Vfx.Fx fx = RuneProps.fx(level, start, ticks + 14);
        Vec3 face = faceOf(d);
        RuneSwords.Blade b = RuneSwords.phantom(fx, start, d, face, len, 0.35, BLADE, GEM);
        double reach = b.reach() / 0.35;
        Vec3 end = V.add(from, d, Math.max(0.6, dist - reach * (target != null ? 0.75 : 1.0)));
        fx.on(1, () -> {
            b.grow(1, 1);
            b.pose(end, d, face, Math.max(1, ticks - 1));
        });
        if (target != null) fx.on(ticks, b::remove);
        else {
            fx.on(ticks + 8, () -> b.grow(0.1, 4));
            fx.on(ticks + 13, b::remove);
        }
        if (RuneKit.fxQuota("phantomShotGlint", 6)) JobFx.glint(level, V.add(from, d, 0.5), 0.3, TRACE, TRACE_CORE, 0.14, Opt.empty());
        SkillFx.play(level, "item.trident.throw", from, 1.5 + Kit.rand() * 0.2, 0.3);
    }

    /** 활 평타가 맞음: 칼이 꽂힘 (+1) */
    static void onHit(ServerPlayer sp, Jobs.Job j, LivingEntity victim) {
        lodgeShot(sp, j, victim, sp.getEyePosition());
    }
}
