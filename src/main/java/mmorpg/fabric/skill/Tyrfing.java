package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 티르빙 (대검 · 마검 — 마력 2 : 힘 1) — 무덤불 · 망자의 각성 (skills.js v2.23.0 "앙간튀르의 무덤": 헤르보르가 무덤불 속에서 아버지 앙간튀르를 깨워
 * 티르빙을 받음). 모든 타격 × 최대 체력 ÷ hpBase. 검 = 공격 피해 (하늘색 숫자) · 마력 = 마법 피해 (파란 숫자).
 * 수치는 SKILLS.greatsword, 색은 SKILLS.tyrfing.
 */
final class Tyrfing {
    private Tyrfing() {}

    static final double[] TY_FIRE = {0.58, 0.42, 0.95}; // 무덤불 보랏빛 (번쩍임 · 고리 — 칼날 룬의 보랏빛)
    static final double[] TY_GHOST = {0.74, 0.76, 1.0}; // 유령 흰 보랏빛 (벤 자국 · 불티)
    static final double[] TY_DEEP = {0.32, 0.18, 0.66}; // 짙은 보랏빛 (기운)
    /** 무덤불을 그리지 않는 눈 둘레 (칸) · 가까우면 키를 거리 × 이 비율까지 — 1인칭 화면을 덮지 않게 */
    static final double TY_NEAR = 1.4;
    static final double TY_NEAR_K = 0.75;

    static void register() {
        Transcend.register("tyrfing_fire", Tyrfing::fire);
        Transcend.register("tyrfing_waking", Tyrfing::waking);
    }

    // ------------------------------------------------------------ 티르빙 공통 (몹 무적 시간 · 삶 · 쓰러짐 · 체력 대가 계산은 Kit)

    /** 티르빙 연출 색 (SKILLS.tyrfing): spell 하늘빛 룬 (마력) · rune 보랏빛 룬 · curse 핏빛 · dark 어두운 자주 */
    record TyColors(double[] spell, double[] rune, double[] curse, double[] dark) {}

    static TyColors tyColors() {
        Obj t = Kit.SKILLS.objOrNull("tyrfing");
        if (t == null) {
            return new TyColors(new double[] {0.53, 0.8, 1.0}, new double[] {0.62, 0.36, 0.9}, new double[] {0.8, 0.08, 0.2},
                    new double[] {0.32, 0.05, 0.26});
        }
        return new TyColors(Kit.rgb(t.get("spell")), Kit.rgb(t.get("rune")), Kit.rgb(t.get("curse")), Kit.rgb(t.get("dark")));
    }

    /** 마력 한 번: 마법 피해 (방어 무시), 파란 숫자 (치명타는 파란 !!) */
    static double spellHit(ServerPlayer sp, LivingEntity target, Weapon w, double mult, Opt o) {
        return Kit.skillHit(sp, target, w, mult, o.with("cause", Kit.Cause.MAGIC, "spell", true));
    }

    static double spellHit(ServerPlayer sp, LivingEntity target, Weapon w, double mult) {
        return spellHit(sp, target, w, mult, Opt.empty());
    }

    /** 체력 대가 스킬 (망자의 각성) 이 체력을 돌려주는 적: 적대 몹 (monster 무리) · RPG 몬스터 · 보스 · 드래곤 (동물 · 보스 소품 · 허수아비는 아님) */
    static boolean soulWorthy(Entity e) {
        return Kit.hasFamily(e, "monster") || Kit.hasFamily(e, "rpg_monster") || Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "dragon");
    }

    /** 보스 · 보스 소품 (석화 · 둔화 · 밀기가 듣지 않음 — 보스 기믹을 깨지 않게 — skills.js bossLike) */
    static boolean bossLike(Entity e) {
        return Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop");
    }

    /** 바친 피를 돌려받는 모습: 쓰러진 적에서 내 가슴으로 핏빛 빛줄기 + 초록 회복 숫자 */
    static void soulReturn(ServerPlayer sp, Vec3 from, double amount, TyColors cs) {
        if (sp.isRemoved()) return;
        ServerLevel level = sp.level();
        Vec3 chest = V.up(sp.position(), 1.1);
        double len = V.len(V.sub(chest, from));
        // 멀리 걸어가 있어도 조각 열두 개까지
        if (len > 0.5) {
            SkillFx.beam(level, from, chest, cs.curse(), Opt.of("half", 0.3, "thick", 0.12, "life", 0.35, "sweep", 0.15, "gap", Math.max(0.6, len / 12)));
        }
        SkillFx.sparks(level, V.add(chest, V.flat(sp.getViewVector(1F)), 1.2), cs.curse(), 6, 2);
        double healed = RpgHealth.healBy(sp, amount);
        if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
        SkillFx.play(level, "respawn_anchor.charge", chest, 1.4, 0.7);
    }

    // ------------------------------------------------------------ 그림 도구

    /** 무덤불 한 장 (rpg:tyr_ghostfire — 밑동이 at, 보는 사람을 향해 섬): size = 키 (칸), life 초 */
    static void tyrFireFx(ServerLevel level, Vec3 at, double size, double life, double[] color, double alpha) {
        SkillFx.fx(level, "rpg:tyr_ghostfire", at, Opt.of("size", size, "life", life, "color", color, "alpha", alpha));
    }

    static void tyrFireFx(ServerLevel level, Vec3 at, double size, double life) {
        tyrFireFx(level, at, size, life, SkillFx.WHITE, 1);
    }

    /**
     * 유령 전사 한 장 (rpg:tyr_warrior — 꼬리 끝이 at, 보는 사람을 향해 섬): o = size 반 크기 · life 초 · frame 시작 장 · frames 장 수 · fps 초당 장 ·
     * loop (되풀이) · sink 칸 아래에서 rise 초 동안 솟음 · fade 마지막 초 동안 사라짐 (· color · alpha)
     */
    static void tyrWarriorFx(ServerLevel level, Vec3 at, Opt o) {
        SkillFx.fx(level, "rpg:tyr_warrior", at, Opt.of("size", o.num("size", 0), "life", o.num("life", 0), "color", o.color("color", SkillFx.WHITE),
                "alpha", o.num("alpha", 1), "frame", o.num("frame", 4), "frames", Math.max(1, o.num("frames", 1)), "fps", o.num("fps", 0),
                "loop", o.truthy("loop") ? 1.0 : 0.0, "sink", o.num("sink", 0), "rise", o.num("rise", 0), "fade", o.num("fade", 0)));
    }

    /** 갈라진 땅 (rpg:ground_crack — 짙은 보랏빛) */
    static void tyCrack(ServerLevel level, Vec3 at, double size, double life) {
        SkillFx.fx(level, "rpg:ground_crack", V.up(at, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", size, "life", life, "color", TY_DEEP,
                "alpha", 0.9));
    }

    /** 3인칭 카메라 근사 자리 (보는 쪽 뒤 4.5칸 · 눈 위 1칸) 와 보는 쪽 */
    record Cam(Vec3 cam, Vec3 f) {}

    static Cam tpCam(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        return new Cam(new Vec3(eye.x - f.x * 4.5, eye.y + 1.0, eye.z - f.z * 4.5), f);
    }

    /**
     * 그림을 보는 곳까지 거리: 1인칭 눈과 3인칭 카메라 (보는 쪽 뒤 4.5칸 · 위 1칸 — 기본 3인칭의 근사) 중 가까운 쪽.
     * 가까운 그림은 작게 · 흐리게 (어느 시점에서도 화면을 덮지 않게 — 등 뒤의 그림은 3인칭 카메라 바로 앞)
     */
    static double viewDist(ServerPlayer sp, Vec3 at) {
        if (sp.isRemoved()) return 99;
        Cam c = tpCam(sp); // 바로 위 · 아래를 볼 때도 몸이 향한 쪽 뒤
        return Math.min(V.len(V.sub(at, sp.getEyePosition())), V.len(V.sub(at, c.cam())));
    }

    /** 가까운 무덤불은 흐리게 (눈에서 TY_NEAR 칸 0.55 → 2.6칸 더 멀면 1) — 뒤의 적이 비쳐 보이게 */
    static double tyNearAlpha(double d) {
        return Math.max(0.55, Math.min(1, 0.55 + (0.45 * (d - TY_NEAR)) / 2.6));
    }

    /** 눈에서 d 칸 떨어진 곳에 그릴 반 크기: 가까우면 작게 (반 크기 ≤ 거리 × k — 1인칭 화면을 덮지 않게), near 칸 안이면 그리지 않음 (0) */
    static double nearSize(double size, double d, double k, double near) {
        return d < near ? 0 : Math.min(size, k * d);
    }

    /** 한 틱에 맞는 적이 많으면 그림은 앞의 n 까지 (몰린 적 · 미뤄진 타격) — 스킬 한 번마다 하나 */
    static BooleanSupplier fxBudget(int n) {
        long[] st = {-1, 0}; // 틱 · 그 틱에 쓴 수
        return () -> {
            long now = Tasks.now();
            if (now != st[0]) {
                st[0] = now;
                st[1] = 0;
            }
            return ++st[1] <= n;
        };
    }

    // ------------------------------------------------------------ 땅

    /**
     * at 아래 (at.y - maxDown 까지) 의 첫 단단한 블록 윗면 높이 (반 블록 · 계단은 맞은 면 높이 — 물 · 용암 · 땅 없음 = null).
     * ceilFrom 을 주면 그 높이가 빈 칸 (공기) 일 때 위로 천장을 찾아 at 을 천장 바로 밑으로 (광선이 천장 블록 안에서 시작하면 그 블록 위를 땅으로 봄).
     * 그 높이가 블록 속 (그 자리 땅이 높음 — 반 블록 · 턱) 이면 천장을 찾지 않음
     */
    static Double tyGround(Level level, Vec3 at, double maxDown, Double ceilFrom) {
        double top = at.y;
        if (ceilFrom != null && level.getBlockState(BlockPos.containing(at.x, ceilFrom, at.z)).isAir()) {
            Kit.Hit c = Kit.ray(level, new Vec3(at.x, ceilFrom, at.z), V.UP, Math.max(0.1, at.y - ceilFrom), false);
            if (c != null) top = Math.max(ceilFrom, Math.min(top, c.point().y - 0.05));
        }
        Kit.Hit hit = Kit.ray(level, new Vec3(at.x, top, at.z), V.DOWN, Math.max(0.1, maxDown - (at.y - top)), true);
        if (hit == null) return null;
        String id = Kit.blockId(hit.state());
        if (Kit.liquidAt(level, hit) || id.contains("water") || id.contains("lava")) return null;
        double fy = hit.point().y - hit.pos().getY();
        return hit.pos().getY() + (fy > 0.05 && fy <= 1 ? fy : 1);
    }

    /** 발밑 땅 높이: 땅에 서 있으면 발 높이 그대로 (낭떠러지 끝에 걸쳐 서도), 뛰는 중이면 3칸 안의 땅 (반 블록은 그 윗면) */
    static Vec3 tyFeet(ServerPlayer sp) {
        Vec3 feet = sp.position();
        if (sp.onGround()) return feet;
        Double g = tyGround(sp.level(), V.up(feet, 0.3), 3.3, null);
        return new Vec3(feet.x, g == null ? feet.y : Math.min(feet.y, g), feet.z);
    }

    /** a → b 사이에 막는 블록이 하나도 없는지 (끝 여유 없이 — 얇은 문 · 유리판 · 쇠창살 · 울타리도 막음, 풀 · 꽃은 아님) */
    static boolean tyClear(Level level, Vec3 a, Vec3 b) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return true;
        return Kit.ray(level, a, V.unit(d), len, false) == null;
    }

    // ------------------------------------------------------------ 스킬 1 무덤불

    /** 무덤불 기둥 자리: p 선 자리, o 옆으로 비낀 칸 (+ 오른쪽) */
    record Post(Vec3 p, double o) {}

    /** 기둥 중 하나에서 가로 r 칸 (몸 폭 여유) · 위아래 3칸 안인지 */
    static boolean nearPosts(List<Post> posts, Entity e, double r) {
        for (Post p : posts) {
            if (Math.hypot(e.getX() - p.p().x, e.getZ() - p.p().z) <= r + Kit.bodyPad(e) && Math.abs(e.getY() - p.p().y) <= 3) return true;
        }
        return false;
    }

    /**
     * 무덤불: 칼끝으로 땅을 앞으로 그음 — 발밑 땅에서 (뛰는 중이면 3칸 안의 땅) 보는 쪽 (조준한 적이 있으면 그 적 쪽, 가로만) 으로 땅을 따라
     * (한 칸씩 오르내림 · 벽 · 낭떠러지 · 물 · 용암 · 닫힌 문 · 유리판 · 울타리 앞에서 끊김)
     * start ~ range 칸에 고르게 (좌우로 zig 칸씩 번갈아 비껴) pillars 개 무덤불 기둥이 lead 틱부터 한 틱씩 차례로 솟음 (갈라진 금 · 번쩍임) —
     * 기둥마다 width 칸 안의 적 (기둥 가운데에서 보이는) 을 벰 (검 mult, 적마다 한 번) → burst 틱에 기둥이 한꺼번에 터짐
     * (마력 spellMult — 기둥 width + 0.4칸 안의 보이는 적 + 벤 적은 밀려났어도 기둥에서 width + 2칸 안이면, 적마다 한 번).
     * 앞이 바로 막혔거나 땅이 없어 (물 · 낭떠러지) 기둥을 하나도 세울 수 없으면 재사용 대기 없이 거절.
     * 쓰러지거나 차원을 옮기면 남은 것은 없음 (무기를 바꿔도 이미 그은 불은 끝까지)
     */
    static boolean fire(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        // 땅을 긋는 자리: 발 높이 (뛰는 중이면 3칸 안의 발밑 땅 — 반 블록은 그 윗면, 낭떠러지 끝에 걸쳐 서면 선 땅 그대로)
        Vec3 start = tyFeet(sp);
        double range = k.num("range");
        Vec3 f0 = Kit.facingOf(sp);
        RpgCombat.Aim aim = RpgCombat.aimTarget(sp, range + 1, Kit.BOW.numOr("aimAssist", 0));
        if (aim.target() != null && !aim.target().isRemoved() && Kit.canHit(sp, aim.target())) {
            Vec3 d = V.sub(aim.target().position(), start);
            if (Math.hypot(d.x, d.z) >= 1.5) f0 = V.flat(d);
        }
        Vec3 f = f0;
        List<Kit.Spot> spots = Kit.dashSpots(level, start, f, range);
        Vec3 side = V.rightOf(f);
        int pillars = (int) k.num("pillars");
        double startDist = k.num("start");
        double zig = k.num("zig");
        // 기둥 (갈라진 금을 따라 좌우로 zig 칸씩 번갈아 — 1인칭에서도 겹치지 않고 하나하나 보이게)
        List<Post> posts = new ArrayList<>();
        Vec3 prev = start;
        for (int j = 0; j < pillars; j++) {
            double s = startDist + ((range - startDist) * j) / Math.max(1, pillars - 1);
            Kit.Spot best = null;
            for (Kit.Spot x : spots) if (Math.abs(x.s() - s) <= 0.3 && (best == null || Math.abs(x.s() - s) < Math.abs(best.s() - s))) best = x;
            if (best == null) break;
            // 기둥 자리 그대로 다시 봄 (0.5칸마다 본 길 사이 · 옆으로 비낀 자리 — 벽 · 낭떠러지 끝을 넘어 벽 속 · 허공에 세우지 않게).
            // 비낀 자리가 옆 벽 속 · 옆 낭떠러지면 가운데 줄에 (좁은 굴 · 다리 · 벽에 붙어서도 줄이 끊기지 않게)
            // 앞 기둥 (처음은 발밑) 에서 이 기둥까지 1.2칸 높이로 막는 것이 없어야 함 (0.5칸마다 아래로만 보는 길 사이의 얇은 문 · 유리판 · 울타리)
            double gy = best.p().y;
            Vec3 back = prev;
            DoubleFunction<Post> spotAt = o -> {
                Vec3 q = Kit.standSpot(level, new Vec3(start.x + f.x * s + side.x * o, gy + 1.1, start.z + f.z * s + side.z * o), 2.2);
                return q != null && Math.abs(q.y - gy) <= 1.05 && tyClear(level, V.up(back, 1.2), V.up(q, 1.2)) ? new Post(q, o) : null;
            };
            Post post = spotAt.apply((j % 2 == 0 ? 1 : -1) * zig);
            if (post == null) post = spotAt.apply(0);
            if (post == null) break;
            posts.add(post);
            prev = post.p();
        }
        if (posts.isEmpty()) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞이 막혔거나 땅이 없습니다");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double hf = mmorpg.core.Skills.tyrfingHpFactor(RpgHealth.maxOf(sp)); // 스킬 × 최대 체력 ÷ hpBase (쓸 때의 최대 체력)
        TyColors cs = tyColors();
        BooleanSupplier budget = fxBudget(6);
        double mult = k.num("mult");
        double spellMult = k.num("spellMult");
        double width = k.num("width");
        int lead = (int) k.num("lead");
        int burstAt = (int) k.num("burst");
        // 벤 적 (밀려나도 터질 때 맞음)
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        // 칼끝이 땅을 긁음: 눈앞 아래 불티 · 짧은 금 · 소리
        Vec3 tip = V.add(start, f, 1.3);
        SkillFx.sparks(level, V.up(tip, 0.2), TY_GHOST, 5, 3);
        tyCrack(level, V.add(start, f, 1.6), 0.9, 1.0);
        SkillFx.play(level, "mace.smash_ground", tip, 0.9, 1.0);
        SkillFx.play(level, "particle.soul_escape", tip, 0.8, 1.2);
        SkillFx.shake(sp, 0.08, 0.1);
        for (int j = 0; j < posts.size(); j++) {
            Post p = posts.get(j);
            int at = lead + j;
            int jj = j;
            Tasks.later(at, () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 eye = sp.getEyePosition();
                double de = viewDist(sp, V.up(p.p(), 1.0));
                // 갈라진 금 · 무덤불 기둥 (터질 때까지 탐 — 가까우면 작게 · 흐리게) · 밑동 번쩍임 · 불티
                tyCrack(level, p.p(), 1.3, (burstAt - at) / 20.0 + 0.6);
                double h = Math.min(2.6, TY_NEAR_K * de);
                if (de >= TY_NEAR) {
                    // 터질 때까지 세게 탐 (사라지기 시작하는 65% 가 터지는 틱 즈음)
                    tyrFireFx(level, p.p(), h, (burstAt - at) / 20.0 + 0.45, SkillFx.WHITE, tyNearAlpha(de));
                    // 바깥쪽 작은 불혀 (0 · 3 · 6 — 좌우 번갈아)
                    if (jj % 3 == 0 && p.o() != 0) {
                        tyrFireFx(level, V.add(p.p(), side, Math.signum(p.o()) * 0.5), h * 0.6, (burstAt - at) / 20.0 + 0.4, SkillFx.WHITE, tyNearAlpha(de));
                    }
                }
                SkillFx.burst(level, V.up(p.p(), 0.3), TY_FIRE, Math.min(0.7, 0.3 * de), 0.25);
                SkillFx.sparks(level, V.up(p.p(), 0.6), TY_GHOST, 3, 3);
                SkillFx.play(level, "particle.soul_escape", p.p(), 0.8 + 0.06 * jj, 1.0); // 기둥마다 한 음씩 높게
                if (jj % 2 == 0) SkillFx.play(level, "mob.blaze.shoot", p.p(), 0.7 + 0.05 * jj, 0.6);
                // 검: 이 기둥의 width 칸 안 (기둥에서 보이는 — 벽 너머는 아님 · 아직 베지 않은 적)
                for (LivingEntity e : Kit.visibleFrom(sp, V.up(p.p(), 1.0), Kit.around(sp, V.up(p.p(), 1.0), width, 2.0))) {
                    if (struck.containsKey(e.getUUID())) continue;
                    struck.put(e.getUUID(), e);
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                        if (Kit.skillHit(sp, e, w, mult * hf) <= 0 || !budget.getAsBoolean()) return;
                        Vec3 c = Kit.bodyAt0(e, V.up(p.p(), 1));
                        double dc = viewDist(sp, c);
                        if (dc >= 1.2) SkillFx.cutMark(level, c, V.unit(V.sub(c, eye)), 58, 0.06, TY_FIRE, SkillFx.WHITE, Math.min(1, 0.4 * dc));
                        SkillFx.sparks(level, c, TY_GHOST, 2, 4);
                    });
                }
            });
        }
        // 터짐: 기둥마다 높이 솟는 무덤불 · 번쩍임 · 고리 · 솟는 불티 → 마력
        Tasks.later(burstAt, () -> {
            if (!ok.getAsBoolean()) return;
            for (Post p : posts) {
                double de = viewDist(sp, V.up(p.p(), 1.2));
                if (de >= TY_NEAR) tyrFireFx(level, p.p(), Math.min(4.2, TY_NEAR_K * 1.3 * de), 0.4, SkillFx.WHITE, tyNearAlpha(de));
                SkillFx.burst(level, V.up(p.p(), 1.0), TY_GHOST, Math.min(1.2, 0.3 * de), 0.3);
                SkillFx.shock(level, p.p(), TY_FIRE, Math.min(1.4, 0.4 * de), 0.4);
                SkillFx.fx(level, "rpg:arrow_spark", V.up(p.p(), 0.5), Opt.of("dir", V.UP, "speed", 3 + Kit.rand() * 2, "size", 0.15, "life", 0.6,
                        "color", TY_GHOST, "alpha", 1.0));
            }
            Vec3 mid = posts.get(posts.size() / 2).p();
            SkillFx.play(level, "mob.ghast.fireball", mid, 0.7, 1.2);
            SkillFx.play(level, "random.explode", mid, 1.2, 0.6);
            SkillFx.play(level, "particle.soul_escape", mid, 0.6, 1.4);
            Kit.shakeNear(level, mid, 10, 0.25, 0.3);
            List<LivingEntity> zone = new ArrayList<>();
            for (Post p : posts) zone.addAll(Kit.visibleFrom(sp, V.up(p.p(), 1.0), Kit.around(sp, V.up(p.p(), 1.0), width + 0.4, 2.0)));
            List<LivingEntity> pushed = new ArrayList<>();
            for (LivingEntity e : struck.values()) if (Kit.canHit(sp, e) && nearPosts(posts, e, width + 2)) pushed.add(e);
            List<LivingEntity> list = Kit.union(zone, pushed);
            long now = Tasks.now();
            for (int j = 0; j < list.size(); j++) {
                LivingEntity e = list.get(j);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now + j / 6), ok, () -> {
                    if (spellHit(sp, e, w, spellMult * hf) <= 0 || !budget.getAsBoolean()) return;
                    Vec3 c = Kit.bodyAt0(e, start);
                    double bs = nearSize(0.9, viewDist(sp, c), 0.3, 1.0);
                    if (bs > 0) SkillFx.burst(level, c, cs.rune(), bs, 0.25);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스킬 2 망자의 각성

    /** 앙간튀르의 형제 한 명: i 번째 (앞에서부터 오른쪽으로), u 무덤 가운데에서 본 방향, at 선 자리 (그 땅 높이), rise 솟기 시작하는 틱 */
    record Warrior(int i, Vec3 u, Vec3 at, int rise) {}

    /** 망자의 각성 한 번 동안 바뀌는 것: 벤 적 (밀려나도 터질 때 맞음), 바친 체력을 돌려받았는지 */
    private static final class Grave {
        final Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        boolean refunded;
    }

    /** 전사 한 토막 그리기 (from 틱부터 to 틱까지 — o: tyrWarriorFx 의 frame · frames · fps · loop · sink · rise · fade) */
    @FunctionalInterface
    private interface Pose {
        void draw(Warrior p, int from, int to, Object[] o);
    }

    /** 섬 (되풀이 4-5) · 벰 */
    private static final Object[] IDLE = {"frame", 4, "frames", 2, "fps", 5, "loop", true};
    private static final Object[] SWING = {"frame", 6, "frames", 2, "fps", 12};

    /** t 틱에 할 일 더하기 */
    private static void on(Map<Integer, List<Runnable>> events, int t, Runnable fn) {
        events.computeIfAbsent(t, x -> new ArrayList<>()).add(fn);
    }

    /** 섬 (되풀이 4-5, 한 바퀴 8틱) 은 8틱씩 나눠 그림 — 오래 남는 한 장이 나중에 다가간 눈앞을 덮지 않게 (바퀴가 끊기지 않게 한 바퀴씩) */
    private static void idle(Map<Integer, List<Runnable>> events, Pose pose, Warrior p, int from, int to) {
        for (int t = from; t < to; t += 8) {
            int t0 = t;
            on(events, t, () -> pose.draw(p, t0, Math.min(to, t0 + 8), IDLE));
        }
    }

    /** 적의 쪽 (전사 번호): 무덤 가운데 c0 에서 본 각 (a0 = 앞, n 명) */
    static int sectorOf(Entity e, Vec3 c0, double a0, int n) {
        double dx = e.getX() - c0.x;
        double dz = e.getZ() - c0.z;
        if (Math.hypot(dx, dz) < 0.3) return 0;
        double a = Math.atan2(dz, dx) - a0;
        a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        return (int) (Math.round(a / ((Math.PI * 2) / n)) % n);
    }

    /**
     * 망자의 각성 (체력 대가 — 전설의 마검은 뽑을 때마다 생명을 앗아야 함): 둘레 안 (radius 칸 · 발밑 위 1.2칸에서 보이는) 에 맞을 적이 없으면
     * 쓰지 않음 (대가 · 재사용 대기 없음 — 벽 너머의 적만 있을 때도).
     * 최대 체력 × cost 를 바침 (생존 · 모험 모드 — 피해가 아니라 보호막 · 방어와 상관없음, 바치면 1 아래가 되면 쓰지 못함 · 재사용 대기 없음).
     * 발밑 (그 자리에 남음) 에 핏빛 무덤 룬 → 둘레 radius 칸에 앙간튀르의 열두 형제 (warriors — 앞에서부터 오른쪽으로 30° 마다, 그 땅 높이) 가
     * 앞에서부터 양옆 · 뒤로 한 틱 사이로 무덤에서 솟음 (rise 틱) → first 틱부터 step 틱마다 차례로 자기 쪽 (30°) 을 안으로 벰
     * (검 mult — 적마다 한 번, 마지막 전사 때 아직 안 베인 둘레 안의 적도) → second 틱에 다 같이 내리벰 (검 mult) →
     * final 틱에 무덤이 무덤불로 터짐 (마력 spellMult, 밀쳐 띄움 — 보스는 밀지 않음 · 벤 적은 밀려났어도 radius + 2.5칸 안이면).
     * 둘레 안의 적은 무덤 가운데 위 1.2칸에서 보이는 적. 이 스킬로 적대 몹 · 보스를 쓰러뜨리면 바친 체력을 돌려받음
     * (한 번 — 보스처럼 다음 틱에 쓰러지는 적은 2틱 뒤에 봄). 쓰러지거나 차원을 옮기면 남은 것은 없음 (무기를 바꿔도 깨운 망령은 끝까지)
     */
    static boolean waking(ServerPlayer sp, Weapon w, Obj k) {
        TyColors cs = tyColors();
        // 무덤 자리: 발 높이 (뛰는 중이면 3칸 안의 발밑 땅 — 반 블록은 그 윗면 · 룬 · 땅의 빛이 허공에 뜨지 않게, 낭떠러지 끝에 걸쳐 서면 선 땅 그대로)
        Vec3 c0 = tyFeet(sp);
        Vec3 center = V.up(c0, 1.2);
        String name = k.str("name");
        double radius = k.num("radius");
        // 둘레 안의 적 (무덤 가운데 위 1.2칸에서 보이는 — 벽 너머는 아님)
        Supplier<List<LivingEntity>> inside = () -> Kit.visibleFrom(sp, center, Kit.around(sp, c0, radius, 3));
        if (inside.get().isEmpty()) {
            Notify.warn(sp, "§c" + name + ": §7주위 " + Js.str(radius) + "칸 안에 적이 없습니다");
            return false;
        }
        double cost = Kit.curseCost(sp, k);
        boolean rpgMode = RpgHealth.isRpgMode(sp);
        if (!Kit.canPayCurse(sp, k)) {
            Notify.warn(sp, "§c" + name + ": §7체력 " + Js.str(Js.round((cost + 1) * 10) / 10) + " 이상 필요 §8(대가 " + Js.str(cost) + " · 1은 남김)");
            return false;
        }
        double paid = rpgMode ? RpgHealth.payHp(sp, cost) : 0;
        if (rpgMode && paid <= 0) return false;
        double hf = mmorpg.core.Skills.tyrfingHpFactor(RpgHealth.maxOf(sp)); // 스킬 × 최대 체력 ÷ hpBase
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        Vec3 f0 = Kit.facingOf(sp);
        double a0 = Math.atan2(f0.z, f0.x);
        BooleanSupplier budget = fxBudget(6);
        long castTick = Tasks.now();
        double mult = k.num("mult");
        double spellMult = k.num("spellMult");
        double size = k.num("size");
        int n = (int) k.num("warriors");
        int riseLen = (int) k.num("rise");
        int first = (int) k.num("first");
        int step = (int) k.num("step");
        int second = (int) k.num("second");
        int fin = (int) k.num("final");
        // 대가: 눈앞 아래 핏빛 불꽃 + 발밑 고리 + 심장 소리 + 얼굴 앞 왼쪽 아래 "-10 피의 대가" (회복 숫자는 오른쪽 아래)
        Vec3 front = V.up(V.add(c0, f0, 1.4), 0.9);
        SkillFx.burst(level, front, cs.curse(), 0.6, 0.3);
        SkillFx.sparks(level, front, cs.curse(), 6, 3);
        SkillFx.shock(level, c0, cs.curse(), 1.8, 0.45);
        if (paid > 0) DamageNumbers.showFace(sp, "§c§l-" + Js.str(Js.round(paid * 10) / 10) + " §r§c피의 대가", 2.6, -0.95, -0.8);
        SkillFx.play(level, "mob.warden.heartbeat", c0, 0.8, 1.0);
        SkillFx.play(level, "respawn_anchor.deplete", c0, 0.8, 0.8);
        // 무덤: 핏빛 룬 (반지름 radius) + 짙은 보랏빛 고리, 망령이 깨어나는 소리
        SkillFx.rune(level, c0, Opt.of("size", radius + 0.4, "life", (fin + 10) / 20.0, "color", cs.curse(), "alpha", 0.8, "rate", 14.0));
        SkillFx.shock(level, c0, TY_DEEP, (radius + 0.5) / 1.8, 0.6);
        SkillFx.play(level, "mob.warden.emerge", c0, 0.9, 1.1);
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", c0, 1.0, 0.8);
        // 전사 자리: 앞에서부터 오른쪽으로 30° 마다 (위에서 볼 때 시계 방향), 그 땅 높이
        List<Warrior> posts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = a0 + ((double) i / n) * Math.PI * 2;
            Vec3 u = new Vec3(Math.cos(a), 0, Math.sin(a));
            double x = c0.x + u.x * radius;
            double z = c0.z + u.z * radius;
            Double gy = tyGround(level, new Vec3(x, c0.y + 2.5, z), 6, c0.y + 0.1);
            posts.add(new Warrior(i, u, new Vec3(x, gy != null ? gy : c0.y, z), Math.min(i, n - i)));
        }
        Grave g = new Grave();
        Consumer<Vec3> refund = at -> {
            if (g.refunded) return;
            g.refunded = true;
            // 무덤불이 터지는 틱이면 한 틱 뒤에 (그 틱의 파티클이 60개를 넘지 않게)
            if (Tasks.now() - castTick != fin) {
                soulReturn(sp, at, paid, cs);
            } else {
                Tasks.later(1, () -> {
                    if (Kit.sameLife(sp, life)) soulReturn(sp, at, paid, cs);
                });
            }
        };
        // 한 번 침 (검 또는 마력) → 쓰러뜨렸으면 한 번 돌려받음. 들어갔는지 돌려줌
        BiPredicate<LivingEntity, Boolean> strike = (e, spell) -> {
            Vec3 at = Kit.bodyAt0(e, center);
            double got = spell ? spellHit(sp, e, w, spellMult * hf) : Kit.skillHit(sp, e, w, mult * hf);
            if (got <= 0) return false;
            g.struck.put(e.getUUID(), e);
            if (budget.getAsBoolean()) {
                double dc = viewDist(sp, at);
                double bs = nearSize(spell ? 1.0 : 0.6, dc, 0.3, 1.0);
                if (bs > 0) SkillFx.burst(level, at, spell ? cs.rune() : TY_GHOST, bs, spell ? 0.28 : 0.22);
                if (spell && dc >= TY_NEAR) {
                    tyrFireFx(level, !e.isRemoved() ? e.position() : at, Math.min(2.2, TY_NEAR_K * dc), 0.35, SkillFx.WHITE, tyNearAlpha(dc));
                }
            }
            if (g.refunded || paid <= 0 || !soulWorthy(e)) return true;
            if (Kit.fallen(e)) {
                refund.accept(at);
            } else if (bossLike(e)) {
                // 보스는 받은 피해를 다음 틱에 넣어 쓰러짐 — 2틱 뒤에 다시 봄 (보통 몹은 맞은 그 틱에만: 다른 사람이 곧바로 쓰러뜨린 것은 아님)
                Tasks.later(2, () -> {
                    if (!g.refunded && Kit.fallen(e) && Kit.sameLife(sp, life)) refund.accept(at);
                });
            }
            return true;
        };
        // 베기 그림: 전사의 칼 → 안쪽 (무덤 가운데) 으로 땅을 가르는 빛 + 전사 앞의 초승달 참격 (가운데 1.8칸 안은 빼고 · 눈 · 3인칭 카메라 가까이는 작게)
        BiConsumer<Warrior, Boolean> slashFx = (p, core) -> {
            Vec3 from = V.up(V.add(c0, p.u(), radius - 0.4), 0.15);
            Vec3 to = V.up(V.add(c0, p.u(), 1.8), 0.15);
            Gram.streakLine(level, from, to, TY_FIRE, 0.32, 0.3, 0.9, 0);
            if (core) Gram.streakLine(level, from, to, TY_GHOST, 0.1, 0.25, 1, 0);
            Vec3 at = V.up(V.add(c0, p.u(), radius * 0.72), 1.0);
            double s = nearSize(1.6, viewDist(sp, at), 0.3, 1.6);
            if (core && s > 0) {
                SkillFx.arc(level, at, new Vec3(-p.u().x, 0, -p.u().z), Opt.of("shape", "heavy", "flip", false, "size", s, "life", 0.28, "speed", 0.0,
                        "spin", 70.0, "color", TY_GHOST, "aura", TY_DEEP));
            }
        };
        // 전사 한 토막 (from 틱부터 to 틱까지 — 다음 토막과 0.05초 겹쳐 끊기지 않게). 그릴 때마다 지금 눈 · 3인칭 카메라로 다시 봄 (쓴 뒤 걷거나 돌아섰을 수 있음):
        // 눈에서 2칸 안 · 3인칭 카메라 앞 2칸 안이면 그리지 않고, 눈 · 3인칭 카메라에서 3칸 안이면 흐리게 (0.55)
        Pose pose = (p, from, to, o) -> {
            Vec3 c = V.up(p.at(), 1.5);
            double eyeD = 99;
            double camD = 99;
            if (!sp.isRemoved()) {
                eyeD = V.len(V.sub(c, sp.getEyePosition()));
                Cam tc = tpCam(sp);
                Vec3 rel = V.sub(c, tc.cam());
                camD = V.len(rel);
                if (rel.x * tc.f().x + rel.z * tc.f().z < 0) camD = Math.max(camD, 2); // 카메라 뒤는 보이지 않음 (흐림만)
            }
            if (eyeD < 2 || camD < 2) return;
            tyrWarriorFx(level, p.at(), Opt.of("size", size, "life", (to - from) / 20.0 + 0.05, "alpha", Math.min(eyeD, camD) < 3 ? 0.55 : 1.0).with(o));
        };
        // 틱마다 할 일 (전사 토막: 솟음 → 섬 → 벰 → 섬 → 벰 → 섬 → 흩어짐)
        Map<Integer, List<Runnable>> events = new HashMap<>();
        for (Warrior p : posts) {
            int s1 = first + p.i() * step;
            on(events, p.rise(), () -> {
                pose.draw(p, p.rise(), p.rise() + riseLen,
                        new Object[] {"frame", 0, "frames", 4, "fps", 4 / (riseLen / 20.0), "sink", 1.2, "rise", riseLen / 20.0});
                tyrFireFx(level, p.at(), 1.5, riseLen / 20.0 + 0.2);
                tyCrack(level, p.at(), 1.1, (fin - p.rise()) / 20.0 + 0.5);
                if (p.i() % 4 == 0) SkillFx.play(level, "particle.soul_escape", p.at(), 0.7 + 0.05 * p.i(), 1.0);
            });
            idle(events, pose, p, p.rise() + riseLen, s1);
            on(events, s1, () -> pose.draw(p, s1, s1 + 4, SWING));
            idle(events, pose, p, s1 + 4, second);
            on(events, second, () -> pose.draw(p, second, second + 4, SWING));
            idle(events, pose, p, second + 4, fin);
            on(events, fin, () -> pose.draw(p, fin, fin + 8, new Object[] {"frame", 8, "frames", 4, "fps", 10, "fade", 0.15}));
        }
        Kit.ticks(t -> {
            if (!ok.getAsBoolean() || t > fin + 2) return false;
            for (Runnable fn : events.getOrDefault(t, List.of())) fn.run();
            // 첫 바퀴: 차례로 자기 쪽을 안으로 벰
            int since = t - first;
            if (step > 0 && since >= 0 && since % step == 0 && since / step < n) {
                int i1 = since / step;
                Warrior p = posts.get(i1);
                slashFx.accept(p, true);
                SkillFx.sparks(level, V.up(p.at(), 2.4), TY_GHOST, 2, 3);
                SkillFx.play(level, "mob.vex.charge", p.at(), 0.8 + 0.03 * i1, 0.8); // 차례마다 한 음씩 높게
                boolean last = i1 == n - 1;
                for (LivingEntity e : inside.get()) {
                    if (g.struck.containsKey(e.getUUID()) || (!last && sectorOf(e, c0, a0, n) != i1)) continue;
                    g.struck.put(e.getUUID(), e);
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> strike.test(e, false));
                }
            }
            // 둘째 바퀴: 다 같이 내리벰
            if (t == second) {
                for (Warrior p : posts) slashFx.accept(p, false);
                SkillFx.shock(level, c0, TY_FIRE, (radius * 0.7) / 1.8, 0.4);
                SkillFx.play(level, "mace.smash_air", c0, 0.7, 1.0);
                SkillFx.play(level, "mob.vex.charge", c0, 0.6, 1.0);
                SkillFx.play(level, "item.trident.thunder", c0, 1.4, 0.6);
                Kit.shakeNear(level, c0, 10, 0.2, 0.25);
                List<LivingEntity> all = inside.get();
                long now = Tasks.now();
                for (int j = 0; j < all.size(); j++) {
                    LivingEntity e = all.get(j);
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now + j / 6), ok, () -> strike.test(e, false));
                }
            }
            if (t != fin) return true;
            // 무덤이 무덤불로 터짐: 전사가 유령불로 흩어지며 그 자리에서 높이 솟는 무덤불 · 핏빛 룬 번쩍 · 고리 둘 → 마력 (밀쳐 띄움)
            for (Warrior p : posts) {
                double de = viewDist(sp, V.up(p.at(), 1.5));
                if (de >= TY_NEAR) tyrFireFx(level, p.at(), Math.min(3.6, TY_NEAR_K * 1.3 * de), 0.5, SkillFx.WHITE, tyNearAlpha(de));
                SkillFx.burst(level, V.up(p.at(), 1.2), TY_GHOST, Math.min(1.1, 0.3 * de), 0.3);
            }
            SkillFx.rune(level, c0, Opt.of("size", radius + 0.4, "life", 0.6, "grow", 0.25, "rate", -120.0, "color", cs.curse(), "alpha", 0.95));
            SkillFx.shock(level, c0, TY_FIRE, (radius + 1) / 1.8, 0.5);
            SkillFx.shock(level, c0, cs.curse(), radius / 3.6, 0.4);
            SkillFx.play(level, "mob.wither.shoot", c0, 0.8, 0.8);
            SkillFx.play(level, "mace.heavy_smash_ground", c0, 0.9, 1.0);
            SkillFx.play(level, "mob.ghast.fireball", c0, 0.6, 1.2);
            SkillFx.play(level, "particle.soul_escape", c0, 0.5, 1.4);
            Kit.shakeNear(level, c0, 14, 0.35, 0.4);
            List<LivingEntity> pushed = new ArrayList<>();
            for (LivingEntity e : g.struck.values()) {
                if (Kit.canHit(sp, e) && Math.hypot(e.getX() - c0.x, e.getZ() - c0.z) <= radius + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - c0.y) <= 5) {
                    pushed.add(e);
                }
            }
            List<LivingEntity> list = Kit.union(inside.get(), pushed);
            long now = Tasks.now();
            for (int j = 0; j < list.size(); j++) {
                LivingEntity e = list.get(j);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now + j / 6), ok, () -> {
                    if (!strike.test(e, true) || bossLike(e) || e.isRemoved()) return; // 들어간 적만 밀침 (보스는 밀지 않음)
                    Vec3 d = Math.hypot(e.getX() - c0.x, e.getZ() - c0.z) > 0.3 ? V.flat(V.sub(e.position(), c0)) : f0;
                    Kit.knockback(e, d.x * 0.5, d.z * 0.5, 0.45);
                });
            }
            return false;
        });
        return true;
    }
}
