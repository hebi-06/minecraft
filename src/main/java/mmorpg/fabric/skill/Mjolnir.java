package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 묠니르 (초월 전투 망치 — 12단계, 자바 전용): 묠니르 투척 · 라그나로크의 뇌우. 수치는 SKILLS.hammer, 색은 번개의 하늘빛 · 흰 심지.
 * 그림: rpg:rfx_relicspin 칸 4 (묠니르 모델 — 돌며 날아가는 망치), 번개 (rpg:rfx_bolt · 지그재그 빛줄기), 먹구름 (rpg:rfx_cloud).
 * 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class Mjolnir {
    private Mjolnir() {}

    static final int RELIC_SPIN_CELL = 4;
    static final double[] CLOUD = {0.22, 0.24, 0.34};

    static void register() {
        Transcend.register("mjolnir_throw", Mjolnir::toss);
        Transcend.register("mjolnir_storm", Mjolnir::storm);
    }

    /** 도는 망치 그림 (at 에서 vel 칸/초로 움직이며 life 초) */
    static void hammer(ServerLevel level, Vec3 at, Vec3 vel, double size, double life) {
        RuneKit.rfx(level, "relicspin", at, Opt.of("size", size, "life", life, "spin", Kit.rand() * 360, "spinRate", -1080.0, "cell", (double) RELIC_SPIN_CELL,
                "alpha", 1.0, "fade", 0.15, "vel", vel));
    }

    /** 하늘에서 at 으로 떨어지는 번개 한 줄기 (지그재그 빛줄기 두 겹 + rfx_bolt + 맞은 자리 고리 · 불꽃) */
    static void strike(ServerPlayer sp, Vec3 at, double[] glow, double[] core, double scale) {
        ServerLevel level = sp.level();
        double room = Math.max(4, Math.min(14, Kit.skyRoom(level, V.up(at, 0.3), 14, true)));
        Vec3 top = V.up(at, room);
        SkillFx.bolt(level, top, at, glow, Opt.of("jag", 0.6, "thick", 0.22 * scale, "life", 0.25));
        SkillFx.bolt(level, top, at, core, Opt.of("jag", 0.35, "thick", 0.08 * scale, "life", 0.2));
        RuneKit.rfx2(level, "bolt", V.up(at, room / 2), Opt.of("size", room / 2, "life", 0.22, "fade", 0.5, "cell", Math.floor(Kit.rand() * 4)), glow, core,
                new int[] {0, RuneKit.BOLT_CORE}, null);
        SkillFx.shock(level, at, glow, 1.0 * scale, 0.3);
        SkillFx.sparks(level, V.up(at, 0.4), core, 6, 6);
    }

    // ------------------------------------------------------------ 스킬 1 묠니르 투척

    /**
     * 묠니르 투척: 보는 쪽 (위아래 pitch 도까지) 으로 망치를 던짐 — 틱당 speed 칸으로 range 칸 (벽 앞까지) 날며 폭 width 칸의 적을 꿰뚫음 (mult) →
     * 끝에서 hover 틱 동안 돌며 하늘의 번개를 부름: 둘레 radius 칸 번개 폭발 (boltMult, 기절 stun 틱) → 틱당 back 칸으로 손에 돌아오며 지나는 적을
     * 다시 침 (backMult — 3틱마다 지금 손을 다시 겨눔). 대상이 없어도 씀
     */
    static boolean toss(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 f = Kit.facingOf(sp);
        Vec3 view = sp.getViewVector(1F);
        double maxPitch = Math.toRadians(k.num("pitch"));
        double pitch = Math.max(-maxPitch, Math.min(maxPitch, Math.asin(Math.max(-1, Math.min(1, view.y)))));
        Vec3 dir = V.unit(new Vec3(f.x * Math.cos(pitch), Math.sin(pitch), f.z * Math.cos(pitch)));
        Vec3 from = V.add(V.add(V.up(sp.getEyePosition(), -0.35), dir, 0.8), V.rightOf(f), 0.3);
        double len = Math.max(1, Kit.rayDist(level, from, dir, k.num("range")) - 0.5);
        double speed = k.num("speed");
        int out = Math.max(1, (int) Math.ceil(len / speed));
        hammer(level, V.add(from, dir, 0.6), V.scale(dir, speed * 20), 1.5, out / 20.0 + 0.05);
        SkillFx.play(level, "item.trident.throw", from, 0.6, 1.0);
        SkillFx.play(level, "item.trident.riptide_2", from, 0.8, 0.6);
        Set<UUID> struck = new HashSet<>();
        Kit.ticks(t -> {
            if (!ok.getAsBoolean()) return false;
            double s0 = t == 0 ? 0 : Math.min(len, speed * t);
            double s1 = Math.min(len, speed * (t + 1));
            Vec3 at = V.add(from, dir, s1);
            if (t % 2 == 0) {
                RuneKit.zap(level, V.add(at, dir, -1.2), at, glow, core, 0.12, 0.2);
                SkillFx.sparks(level, at, core, 2, 3);
            }
            for (Kit.LineHit h : Kit.lineHits(sp, V.add(from, dir, s0), dir, Math.max(0.05, s1 - s0), k.num("width"))) {
                LivingEntity e = h.e();
                if (!struck.add(e.getUUID())) continue;
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                    Vec3 c = HeroKit.body(e, at);
                    RuneKit.hitFlash(level, c, dir, glow, core, 0.9 * HeroKit.near(sp.getEyePosition(), c, 0.5));
                    SkillFx.play(level, "random.anvil_land", c, 1.4, 0.5);
                    Kit.knockback(e, dir.x * 0.4, dir.z * 0.4, 0.2);
                });
            }
            if (t + 1 < out) return true;
            hover(sp, w, k, ok, at, glow, core);
            return false;
        });
        return true;
    }

    /** 끝에서 돌며 번개를 부름 → 번개 폭발 → 돌아옴 */
    private static void hover(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, Vec3 at, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        int hoverT = (int) k.num("hover");
        hammer(level, at, Vec3.ZERO, 1.6, hoverT / 20.0 + 0.05);
        RuneKit.rfx(level, "cloud", V.up(at, 5.5), Opt.of("size", 4.0, "life", (hoverT + 10) / 20.0, "color", CLOUD, "alpha", 0.75, "fade", 0.4, "cell", 0.0));
        for (int i = 0; i < 3; i++) {
            int ii = i;
            Tasks.later(i * 2, () -> {
                if (!ok.getAsBoolean()) return;
                double a = ii * 2.1;
                RuneKit.zap(level, V.up(at, 5), V.add(at, new Vec3(Math.cos(a) * 0.4, 0.3, Math.sin(a) * 0.4)), glow, core, 0.14, 0.18);
            });
        }
        SkillFx.play(level, "item.trident.thunder", at, 1.2, 0.8);
        Tasks.later(hoverT, () -> {
            if (!ok.getAsBoolean()) return;
            double r = k.num("radius");
            Vec3 ground = V.up(at, -Math.min(2.5, Kit.rayDist(level, at, V.DOWN, 3)));
            strike(sp, at, glow, core, 1.6);
            RuneKit.novaRing(level, ground, r * 0.4, 1.6, glow, core, Opt.of("life", 0.4));
            RuneKit.glowCrack(level, ground, r * 0.8, glow, core, Opt.of("life", 1.0));
            for (int i = 0; i < 6; i++) {
                double a = i / 6.0 * Math.PI * 2;
                RuneKit.zap(level, at, V.add(at, new Vec3(Math.cos(a) * r, -0.5, Math.sin(a) * r)), glow, core, 0.1, 0.2);
            }
            SkillFx.play(level, "ambient.weather.lightning.impact", at, 1.0, 1.0);
            SkillFx.play(level, "ambient.weather.thunder", at, 1.3, 0.6);
            SkillFx.shake(sp, 0.25, 0.25);
            long now = Tasks.now();
            for (LivingEntity e : Kit.around(sp, at, r, 3)) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("boltMult")) <= 0) return;
                    RuneKit.stun(e, (int) k.num("stun"), glow, true);
                    Vec3 c = HeroKit.body(e, at);
                    RuneKit.zap(level, at, c, glow, core, 0.1, 0.2);
                });
            }
            back(sp, w, k, ok, at, glow, core);
        });
    }

    /** 손으로 돌아오며 지나는 적을 다시 침 (backMult) */
    private static void back(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, Vec3 start, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        double per = k.num("back");
        Set<UUID> struck = new HashSet<>();
        Vec3[] cur = {start};
        Kit.ticks(t -> {
            if (!ok.getAsBoolean() || t > 120) return false;
            Vec3 hand = V.add(V.up(sp.getEyePosition(), -0.35), V.rightOf(Kit.facingOf(sp)), 0.3);
            Vec3 d = V.sub(hand, cur[0]);
            double left = V.len(d);
            if (left > 64) return false; // 손이 멀리 순간이동: 바로 손에
            Vec3 u = left > 1e-6 ? V.scale(d, 1 / left) : Vec3.ZERO;
            double step = Math.min(per, left);
            if (t % 3 == 0) hammer(level, cur[0], V.scale(u, per * 20), 1.3, Math.min(0.2, left / (per * 20)));
            for (Kit.LineHit h : Kit.lineHits(sp, cur[0], u, Math.max(0.05, step), k.num("width"))) {
                LivingEntity e = h.e();
                if (!struck.add(e.getUUID())) continue;
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("backMult")) <= 0) return;
                    Vec3 c = HeroKit.body(e, cur[0]);
                    RuneKit.hitFlash(level, c, u, glow, core, 0.8 * HeroKit.near(sp.getEyePosition(), c, 0.5));
                    SkillFx.play(level, "random.anvil_land", c, 1.6, 0.4);
                });
            }
            if (t % 2 == 0) RuneKit.zap(level, cur[0], V.add(cur[0], u, Math.min(1.5, left)), glow, core, 0.1, 0.15);
            cur[0] = V.add(cur[0], u, step);
            if (left > per + 0.4) return true;
            // 손에 돌아옴: 번쩍 + 소리
            SkillFx.burst(level, hand, glow, 0.6, 0.2);
            SkillFx.play(level, "item.trident.return", hand, 0.8, 0.9);
            return false;
        });
    }

    // ------------------------------------------------------------ 스킬 2 라그나로크의 뇌우

    /**
     * 라그나로크의 뇌우: windup 틱 동안 망치를 하늘로 들어 먹구름을 부름 (반지름 radius 칸 위) → gap 틱마다 hits 번, 구름 아래 적 (max 명 — 가까운 순) 에게
     * 번개 (mult, 그 사이 사이 빈 땅에도 번개 — 그림만) → 마지막에 나를 중심으로 거대한 번개 기둥: 둘레 pillar 칸 (afterMult, 바깥으로 밀쳐냄 knock).
     * 쓴 자리에 구름이 머묾 (따라가지 않음). 대상이 없어도 씀
     */
    static boolean storm(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 c = sp.position();
        double r = k.num("radius");
        int windup = (int) k.num("windup");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int max = (int) k.num("max");
        double sky = Math.max(5, Math.min(10, Kit.skyRoom(level, V.up(c, 1), 10, true)));
        int total = windup + hits * gap + 6;
        // 망치를 들어 올림: 손 위로 번개가 모이고 하늘로 빛줄기
        Vec3 over = V.up(sp.getEyePosition(), 0.9);
        hammer(level, over, Vec3.ZERO, 1.2, windup / 20.0);
        RuneKit.zap(level, over, V.up(c, sky), glow, core, 0.18, windup / 20.0);
        RuneKit.selfCue(sp, glow, core, true);
        SkillFx.play(level, "item.trident.thunder", c, 0.7, 1.0);
        SkillFx.play(level, "ambient.weather.thunder", c, 0.6, 0.7);
        // 먹구름 (쓴 자리 위에 머묾)
        for (int i = 0; i < 7; i++) {
            double a = i / 7.0 * Math.PI * 2;
            double rr = i == 0 ? 0 : r * 0.62;
            Vec3 at = new Vec3(c.x + Math.cos(a) * rr, c.y + sky, c.z + Math.sin(a) * rr);
            RuneKit.rfx(level, "cloud", at, Opt.of("size", r * 0.8, "life", total / 20.0 + 0.6, "color", CLOUD, "alpha", 0.8, "fade", 0.25, "cell", (double) (i % 4),
                    "grow", 0.15));
        }
        RuneKit.domainSigil(level, c, r, "arcane", glow, core, Opt.of("life", total / 20.0, "spinRate", 20.0, "alpha", 0.5));
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            Tasks.later(windup + i * gap, () -> {
                if (!ok.getAsBoolean()) return;
                List<LivingEntity> list = new ArrayList<>(Kit.around(sp, c, r, 4));
                list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
                if (list.size() > max) list = new ArrayList<>(list.subList(0, max));
                long now = Tasks.now();
                for (LivingEntity e : list) {
                    Vec3 feet = e.position();
                    strike(sp, feet, glow, core, 1.0);
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> Kit.skillHit(sp, e, w, k.num("mult")));
                }
                // 그림만: 빈 땅에 번개 둘
                for (int j = 0; j < 2; j++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = r * (0.3 + Kit.rand() * 0.65);
                    Vec3 at = new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr);
                    int jj = j;
                    Tasks.later(jj * 4 + 2, () -> {
                        if (ok.getAsBoolean()) strike(sp, at, glow, core, 0.8);
                    });
                }
                SkillFx.play(level, "ambient.weather.lightning.impact", c, 0.9 + Kit.rand() * 0.4, 0.8);
                if (ii % 3 == 0) SkillFx.play(level, "ambient.weather.thunder", c, 0.8 + ii * 0.03, 0.6);
            });
        }
        // 마지막: 나를 중심으로 거대한 번개 기둥
        Tasks.later(total, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 p = sp.position();
            double pr = k.num("pillar");
            for (int i = 0; i < 5; i++) {
                double a = i / 5.0 * Math.PI * 2;
                Vec3 at = i == 0 ? p : new Vec3(p.x + Math.cos(a) * 1.2, p.y, p.z + Math.sin(a) * 1.2);
                strike(sp, at, glow, core, i == 0 ? 2.4 : 1.4);
            }
            RuneKit.novaRing(level, p, pr * 0.3, 2.6, glow, core, Opt.of("life", 0.5));
            RuneKit.novaRing(level, p, pr * 0.2, 3.5, core, core, Opt.of("life", 0.4));
            RuneKit.glowCrack(level, p, pr, glow, core, Opt.of("life", 1.4));
            RuneKit.godRays(sp, level, p, pr * 0.8, 6, glow, core, Opt.of("h", 16.0, "life", 0.8));
            SkillFx.play(level, "ambient.weather.lightning.impact", p, 0.7, 1.0);
            SkillFx.play(level, "mob.warden.sonic_boom", p, 1.4, 0.6);
            SkillFx.shake(sp, 0.45, 0.4);
            long now = Tasks.now();
            for (LivingEntity e : Kit.around(sp, p, pr, 4)) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 d = V.flat(V.sub(e.position(), p));
                    double knock = k.num("knock");
                    Kit.knockback(e, d.x * knock, d.z * knock, 0.35);
                    RuneKit.zap(level, V.up(p, 1), HeroKit.body(e, p), glow, core, 0.12, 0.2);
                });
            }
        });
        return true;
    }
}
