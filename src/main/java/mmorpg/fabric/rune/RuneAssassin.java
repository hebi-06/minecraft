package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 암살자 룬 (runes.js "v1.6.0 암살자" — 자홍: 파고들고 · 숨고 · 난도질, 표식과 이어 쓰기 콤보): 배후 일격 · 독 표창 · 연막 · 그림자 난도질 · 월광 암살.
 * 암살자 룬에 맞은 적은 암살 표식 (머리 위 자홍 룬). 암살자 룬을 minOwned 종 이상 가지면 이어 쓰기 콤보 (RUNE_COMBO — RuneCast.Combo 로 castRune · 상태 줄에 넣음),
 * 5종을 모두 이으면 마무리 기술 그림자 처형. 자바 판 (11단계): "가진" 룬 = 스킬 칸에 올린 룬 (룬은 스킬 칸에서만 씀), 콤보가 이어지는 동안 상태 줄에 이은 단계.
 */
final class RuneAssassin {
    private RuneAssassin() {}

    /** 그림자 (짙은 자홍) */
    private static final double[] SHADE = {0.26, 0.04, 0.34};
    /** 연막 (회보라) */
    private static final double[] SMOKE = {0.38, 0.32, 0.44};
    /** 독 (연두) */
    private static final double[] POISON = {0.45, 1.0, 0.35};
    /** 달빛 */
    private static final double[] MOON = {0.93, 0.9, 1.0};
    /** v2.25.0 그림자 분신의 몸 (그림자보다 밝게 — 풀밭 위에서도 사람 모양이 보이게) */
    private static final double[] CLONE = {0.4, 0.12, 0.5};
    /** 연막탄 심지 불티 */
    private static final double[] EMBER = {1.0, 0.7, 0.4};
    /** 그림자 난도질: 벨 때마다 분신이 나타나는 각 (내 쪽에서 본 각 — 내 쪽 ±55° 는 비움) */
    private static final int[] FLURRY_DEG = {100, 250, 150, 210, 125};

    private static final Obj COMBO = Rules.get().obj("RUNE_COMBO");
    /** 마무리 기술 (그림자 처형) */
    private static final Obj FIN = COMBO.obj("finisher");
    private static final String FAMILY = COMBO.str("family");
    private static final List<String> GRADE_ORDER = RuneKit.GRADE_ORDER;
    /** 이어 쓰기: 마지막으로 누른 뒤 이 틱 안 (3초) */
    private static final long WINDOW = (long) COMBO.num("window");
    /** 이어 쓴 룬은 앞 룬의 마지막 타격 이 틱 뒤에 나감 (몹 무적 시간 10틱에 막히지 않게) */
    private static final long GAP_AFTER = (long) COMBO.num("gapAfter");
    private static final long MARK_TICKS = (long) COMBO.num("markTicks");
    private static final long FIN_GAP = (long) FIN.num("gap");
    private static final int FIN_MAX = (int) FIN.num("maxTargets");
    /** 그림자 처형: 시작하고 첫 타격까지 틱 */
    private static final int FINISH_LEAD = 4;

    static void register() {
        RuneCast.register("backstab", RuneAssassin::backstab);
        RuneCast.register("poisonStars", RuneAssassin::poisonStars);
        RuneCast.register("smokeBomb", RuneAssassin::smokeBomb);
        RuneCast.register("shadowFlurry", RuneAssassin::shadowFlurry);
        RuneCast.register("moonAssassin", RuneAssassin::moonAssassin);
        // 이어 쓰기 콤보: castRune 의 comboPlan · comboCommit · comboLanded · comboRollback, 상태 줄의 "암살자 N/5 · N 콤보" (콤보가 이어지는 동안 —
        // 룬을 들지 않고 스킬 칸 키로 쓰므로 무기 줄 · 덧붙임 줄에도 붙음)
        RuneCast.combo = new ComboHook();
        Notify.addHudSuffix(sp -> RuneCast.combo == null ? "" : RuneCast.combo.hud(sp));
        // v1.6.0 암살 표식 표시 (10틱마다 — registerRunes 의 markTick)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 10 == 0) markTick();
        });
        // 죽음 · 차원 이동 (onLifeEnded): 콤보와 암살 표식이 끝남
        RuneKit.onLifeEnded(RuneAssassin::endCombo);
        RuneSystem.onForget(id -> {
            endCombo(id);
            PLANNED.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            COMBOS.clear();
            MARKS.clear();
            PLANNED.clear();
        });
    }

    /** 이 사람의 콤보와 암살 표식을 끝냄 (onLifeEnded · forgetRunes) */
    private static void endCombo(UUID id) {
        COMBOS.remove(id);
        String p = id + "|";
        MARKS.keySet().removeIf(key -> key.startsWith(p));
    }

    /** 대상 뒤 (안 되면 앞) 1.3칸의 설 자리 (standSpot: 단단한 땅 위, 물 · 용암 · 허공 · 막힌 곳 제외). 둘 다 안 되면 null */
    private static Vec3 behindSpot(ServerLevel level, Entity target, Vec3 from) {
        Vec3 tl = target.position();
        Vec3 d = V.flat(V.sub(tl, from));
        for (double k : new double[] {1.3, -1.3}) {
            Vec3 at = Kit.standSpot(level, V.add(tl, d, k));
            if (at != null) return at;
        }
        return null;
    }

    /** 그림자 걸음 자리: 짙은 연기 + 자홍 불꽃 */
    private static void shadowPuff(ServerLevel level, Vec3 at, double[] glow) {
        SkillFx.burst(level, V.up(at, 1.0), SHADE, 1.3, 0.35);
        SkillFx.burst(level, V.up(at, 0.9), glow, 0.7, 0.25);
        SkillFx.sparks(level, V.up(at, 1.0), glow, 6, 3);
    }

    /** 암살자 룬 피해: 맞으면 암살 표식 */
    static double assassinHit(ServerPlayer sp, LivingEntity e, double P, double mult) {
        double d = RuneKit.runeHit(sp, e, P, mult, "dex");
        if (d > 0) markTarget(sp, e);
        return d;
    }

    /** 대상이 없을 때 (§c룬 이름: §7앞쪽 N칸 안에 대상이 없습니다) */
    private static void noTarget(ServerPlayer sp, Obj k) {
        Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 대상이 없습니다");
    }

    /**
     * 배후 일격: 대상 뒤로 순간이동해 찌름 — v2.25.0 떠난 자리에 그림자 잔상 (rpg:rfx_shade), 대상 옆에서 그림자 분신이 함께 찌르고,
     * 오른손에서 빛의 단검 (rpg:rfx_sword 작게) 이 몸을 꿰뚫음 (가까운 적의 X 는 작게)
     */
    static boolean backstab(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) {
            noTarget(sp, k);
            return false;
        }
        ServerLevel level = sp.level();
        Vec3 from = sp.position();
        double[][] cs = RuneKit.colorOf("assassin");
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 dest = behindSpot(level, target, from);
        shadowPuff(level, from, glow);
        SkillFx.play(level, "mob.endermen.portal", from, 1.5, 0.7);
        if (dest != null) {
            // 떠난 자리의 그림자 잔상 (대상 쪽을 봄)
            RuneKit.shadeClone(sp, level, from, V.flat(V.sub(target.position(), from)), 0, CLONE, glow, Opt.of("life", 0.45, "fade", 0.7, "alpha", 0.7));
            SkillFx.beam(level, V.up(from, 1), V.up(dest, 1), SHADE, Opt.of("half", 0.3, "thick", 0.28, "life", 0.3, "alpha", 0.75));
            RuneKit.moveTo(sp, dest, target.getEyePosition()); // 못 옮기면 그 자리에서 찌름
            shadowPuff(level, dest, glow);
        }
        Vec3 eye = sp.getEyePosition();
        Vec3 at = Kit.bodyPoint(target);
        Vec3 v = V.unit(V.sub(at, eye));
        // 빛의 단검: 오른손 (눈 오른쪽 아래) 에서 몸 너머 0.6칸까지 찌름 (2틱 — 1인칭에서 손에서 비스듬히 뻗어 보임)
        Vec3 fv = V.flat(v);
        Vec3 hand = V.up(V.add(V.add(eye, V.rightOf(fv), 0.3), fv, 0.45), -0.3);
        Vec3 tip = V.add(at, v, 0.6);
        Vec3 dv = V.unit(V.sub(tip, hand));
        double L = V.len(V.sub(tip, hand));
        double S = 0.35;
        RuneKit.rfx2(level, "sword", V.add(hand, dv, Math.min(L, S + 0.15)), Opt.of("dir", dv, "speed", Math.max(0, L - 2 * S - 0.15) / 0.1, "size", S,
                "life", 0.1), glow, core);
        // 대상 옆 (내 쪽에서 ±100°) 에서 그림자 분신이 함께 찌름 (가까우면 작게)
        Vec3 tf = V.flat(V.sub(target.position(), sp.position()));
        Vec3 flank = RuneKit.turn(tf, Kit.rand() < 0.5 ? 100 : -100);
        double pad = 0.9 + Kit.bodyPad(target);
        Vec3 cf = V.add(target.position(), flank, -pad);
        RuneKit.shadeClone(sp, level, cf, flank, 3, CLONE, glow, Opt.of("life", 0.4, "fade", 0.5, "alpha", 0.9, "size",
                0.95 * RuneKit.nearK(sp, V.up(cf, 1), 3.5, 0.55)));
        if (assassinHit(sp, target, P, k.num("mult")) > 0) {
            // 몸 앞 (보는 쪽 0.6칸) 에 그림: 바로 뒤에서 찌르므로 몸통 안에 그리면 몹에 가려짐 — 가까우면 작게 (1인칭 화면을 덮지 않게)
            double xs = RuneKit.nearK(sp, V.add(at, v, -0.6), 3, 0.2);
            SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.45), Opt.of("dir", v, "size", 1.3 * xs, "life", 0.3, "color", glow, "alpha", 1.0));
            SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.5), Opt.of("dir", v, "size", 0.9 * xs, "life", 0.26, "color", core, "alpha", 1.0));
            SkillFx.sparks(level, V.add(at, v, -0.5), core, 7, 5);
            SkillFx.play(level, "game.player.attack.critical", at, 1.3, 0.9);
        }
        return true;
    }

    /**
     * 독 표창: 표창 stars 개를 부채꼴 (spread 도) 로 (도는 표창 = rpg:rfx_prop 표창), 표창마다 처음 닿는 적 (적마다 한 번),
     * 맞으면 dotGap 틱마다 dots 번 중독 피해
     */
    static boolean poisonStars(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        double[][] cs = RuneKit.colorOf("assassin");
        double[] glow = cs[0];
        double[] core = cs[1];
        Set<UUID> hit = new HashSet<>();
        double range = k.num("range");
        List<LivingEntity> cands = Kit.around(sp, sp.position(), range + 1, 4);
        int stars = (int) k.num("stars");
        double spread = k.num("spread");
        int[] shuriken = RuneKit.RFX.get("shuriken");
        for (int i = 0; i < stars; i++) {
            double deg = -spread / 2 + (spread * i) / Math.max(1, stars - 1);
            Vec3 h = RuneKit.turn(f, deg);
            Vec3 d = V.unit(new Vec3(h.x, view.y * 0.6, h.z));
            LivingEntity best = null;
            double bestT = range;
            for (LivingEntity e : cands) {
                Vec3 q = Kit.bodyToward(e, head, d);
                Vec3 p = V.sub(q, head);
                double t = V.dot(p, d);
                if (t < 0 || t > bestT) continue;
                if (V.len(V.sub(p, V.scale(d, t))) > 0.9 + Kit.bodyPad(e)) continue;
                if (!Kit.lineOfSight(sp, head, q)) continue;
                best = e;
                bestT = t;
            }
            double L = Math.min(best != null ? bestT : range, RuneKit.rayLength(level, head, d, range));
            Vec3 from = V.add(head, d, 0.6);
            int ticks = RuneKit.flyStreak(level, from, V.add(head, d, L), k.num("speed"), POISON, core, 0.4, 0.14);
            // 도는 표창 (날아가는 길, 2틱마다 한 장 — 손 앞 1칸 안은 그리지 않음)
            for (int t = 0; t < ticks; t += 2) {
                double s = ((t + 1.0) / ticks) * Math.max(0, L - 0.6);
                if (s < 0.7) continue;
                Vec3 pos = V.add(from, d, s);
                double spin = t * 70.0;
                RuneKit.later(t, () -> RuneKit.rfx2(level, "prop", pos, Opt.of("size", 0.3, "life", 0.12, "spin", spin), glow, core, shuriken, null));
            }
            if (best != null && hit.add(best.getUUID())) {
                LivingEntity target = best;
                RuneKit.later(ticks, () -> {
                    if (sp.isRemoved() || target.isRemoved()) return;
                    if (assassinHit(sp, target, P, k.num("mult")) <= 0) return;
                    Vec3 at = Kit.bodyPoint(target);
                    // 꽂힌 표창
                    RuneKit.rfx2(level, "prop", V.add(at, d, -0.3), Opt.of("size", 0.34, "life", 0.5, "spin", 30.0, "fade", 0.4), POISON, core, shuriken, null);
                    SkillFx.fx(level, "rpg:slash_x", at, Opt.of("dir", d, "size", 0.8, "life", 0.22, "color", glow, "alpha", 1.0));
                    SkillFx.burst(level, at, POISON, 0.7, 0.3);
                    SkillFx.play(level, "item.trident.hit", at, 1.8, 0.5);
                    // 중독: dotGap 틱마다 dots 번 (첫 타와 무적 시간이 겹치지 않게 dotGap 뒤부터, 맞을 때마다 표식도 새로)
                    int dots = (int) k.num("dots");
                    for (int j = 1; j <= dots; j++) {
                        RuneKit.later(j * k.num("dotGap"), () -> {
                            if (sp.isRemoved() || target.isRemoved()) return;
                            if (assassinHit(sp, target, P, k.num("dot")) > 0) {
                                Vec3 b = Kit.bodyPoint(target);
                                SkillFx.sparks(level, V.up(b, 0.3), POISON, 4, 1.5);
                                SkillFx.fx(level, "rpg:arrow_spark", V.up(b, 0.6), Opt.of("dir", V.UP, "speed", 1.2, "size", 0.14, "grow", 1.4, "life", 0.5,
                                        "color", POISON, "alpha", 0.9));
                            }
                        });
                    }
                });
            }
        }
        RuneKit.arc(level, V.up(V.add(head, f, 1.2), -0.2), f, Opt.of("shape", "thin", "flip", true, "size", 1.4, "life", 0.2, "speed", 4.0, "spin", 80.0,
                "color", glow));
        SkillFx.play(level, "item.trident.throw", head, 1.8, 0.7);
        SkillFx.play(level, "random.bow", head, 2.0, 0.5);
        return true;
    }

    /**
     * 연막: 연막 속 적을 hits 번 베고 몸을 숨김 — v2.25.0 손에서 연막탄 (rpg:rfx_bomb) 이 발 앞에 떨어져 터지고, 짙은 연기 덩이 (rpg:rfx_cloud) 가
     * hide 틱 동안 피어오름. 벨 때마다 연기 속에서 그림자 분신이 나타나 벰 (3명까지)
     */
    static boolean smokeBomb(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[][] cs = RuneKit.colorOf("assassin");
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 f0 = V.flat(sp.getViewVector(1F));
        int hide = (int) k.num("hide");
        double radius = k.num("radius");
        RuneKit.effect(sp, "invisibility", hide, 0);
        RuneKit.effect(sp, "speed", hide, 0);
        // 연막탄: 손 앞 → 발 앞 1.8칸 (3틱), 도는 탄 + 심지 불티 꼬리
        Vec3 hand = V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), 0.6);
        Vec3 drop = V.up(V.add(c, f0, 1.8), 0.2);
        Vec3 dv = V.sub(drop, hand);
        RuneKit.rfx(level, "bomb", hand, Opt.of("dir", V.unit(dv), "speed", V.len(dv) / 0.15, "size", 0.2, "life", 0.15, "spin", 0.0, "spinRate", 720.0,
                "frames", 2.0, "fps", 20.0, "loop", true));
        for (int i = 1; i <= 2; i++) {
            Vec3 ember = V.add(hand, dv, i / 3.0); // 지나간 자리의 불티
            RuneKit.later(i, () -> SkillFx.fx(level, "rpg:arrow_spark", ember, Opt.of("dir", V.UP, "speed", 0.5, "size", 0.1, "grow", 0.5, "life", 0.3,
                    "color", EMBER, "alpha", 1.0)));
        }
        RuneKit.later(3, () -> {
            SkillFx.burst(level, V.up(c, 0.8), SMOKE, 2.4, 0.5);
            SkillFx.burst(level, V.up(c, 0.6), SHADE, 1.6, 0.4);
            SkillFx.sparks(level, drop, EMBER, 6, 4);
            if (!sp.isRemoved()) RuneKit.selfCue(sp, SMOKE, glow, true);
            SkillFx.play(level, "random.explode", c, 1.8, 0.4);
        });
        SkillFx.play(level, "random.fizz", c, 0.7, 1);
        SkillFx.play(level, "fire.ignite", c, 0.6, 0.8);
        SkillFx.play(level, "mob.endermen.portal", c, 1.2, 0.6);
        // 연기 덩이가 hide 틱 동안 피어오름: 둘레와 안쪽 (조금씩 도는 자리), 처음 1초는 짙게
        for (int t = 2; t < hide; t += 5) {
            final int tt = t;
            RuneKit.later(t, () -> {
                int n = tt < 20 ? 7 : 5;
                boolean last = tt + 5 >= hide;
                for (int i = 0; i < n; i++) {
                    double a = ((double) i / n) * Math.PI * 2 + tt * 0.07;
                    double rr = (0.35 + 0.55 * ((i + tt / 5) % 2)) * radius; // 안쪽 0.35 · 바깥 0.9 (범위 안)
                    Vec3 p = new Vec3(c.x + Math.cos(a) * rr, c.y + 0.5 + (i % 3) * 0.45, c.z + Math.sin(a) * rr);
                    // 1인칭 눈 · 3인칭 카메라 가까이의 연기는 옅게, 1.6칸 안이면 그리지 않음 (화면을 덮는 얼룩이 되지 않게 — 나간 사람이면 99)
                    double cd = RuneKit.camDist(sp, p);
                    if (cd < 1.6) continue;
                    double near = Math.max(0.2, Math.min(1, (cd - 1) / 2.5));
                    RuneKit.rfx(level, "cloud", p, Opt.of("size", 1.35 + (i % 2) * 0.4, "life", 0.8, "grow", 0.5, "cell", (double) ((i + tt) % 4), "color",
                            i % 3 != 0 ? SMOKE : SHADE, "alpha", 0.52 * near, "fade", last ? 0.6 : 0.4, "dir", V.UP, "speed", 0.3, "spin",
                            (double) ((i * 37 + tt * 3) % 30 - 15)));
                }
                // 연막 안에 선 내 눈앞에도 (1인칭에서 연기 속에 있는 것이 보이게 — 옅게)
                if (!sp.isRemoved() && V.len(V.sub(sp.position(), c)) <= radius) {
                    Vec3 eye = sp.getEyePosition();
                    Vec3 f = V.flat(sp.getViewVector(1F));
                    for (int sd : new int[] {-1, 1}) {
                        RuneKit.rfx(level, "cloud", V.up(V.add(V.add(eye, f, 2.4), V.rightOf(f), sd * (1.0 + (tt % 10) * 0.05)), -0.6), Opt.of("size", 0.75,
                                "life", 0.7, "grow", 0.4, "cell", (double) ((tt + (sd > 0 ? 1 : 2)) % 4), "color", SMOKE, "alpha", 0.32, "fade", 0.5));
                    }
                }
                if (tt % 10 == 2) {
                    for (int j = 0; j < 2; j++) {
                        SkillFx.vanilla(level, "minecraft:basic_smoke_particle", new Vec3(c.x + (Kit.rand() - 0.5) * radius, c.y + 0.6,
                                c.z + (Kit.rand() - 0.5) * radius));
                    }
                }
            });
        }
        String life = RuneKit.lifeToken(sp);
        SkillFx.every((int) k.num("hits"), (int) k.num("gap"), i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.zonePulse(level, c, glow, radius, 0.35);
            int shown = 0;
            for (LivingEntity e : Kit.around(sp, c, radius)) {
                if (assassinHit(sp, e, P, k.num("mult")) <= 0) continue;
                Vec3 at = Kit.bodyPoint(e);
                Vec3 v = V.unit(V.sub(at, V.up(c, 1.2)));
                if (shown < 16) SkillFx.cutMark(level, at, v, i % 2 == 1 ? 35 : -35, 0.08, glow, core, 0.8); // 베인 자국은 16명까지 (파티클 예산)
                // 연기 속 그림자 분신이 벰 (3명까지 — 파티클 예산)
                if (shown++ < 3) {
                    Vec3 side = RuneKit.turn(V.flat(V.sub(e.position(), c)), (i % 2 == 1 ? 1 : -1) * 70 + 180);
                    Vec3 cf = V.add(e.position(), side, 0.85 + Kit.bodyPad(e));
                    RuneKit.shadeClone(sp, level, cf, V.scale(side, -1), 2, CLONE, glow, Opt.of("life", 0.3, "fade", 0.5, "alpha", 0.85, "size",
                            0.95 * RuneKit.nearK(sp, V.up(cf, 1), 3.5, 0.55)));
                }
                if (i == 0) {
                    RuneKit.effect(e, "slowness", (int) k.num("slowTicks"), 1);
                    RuneKit.effect(e, "weakness", (int) k.num("slowTicks"), 0);
                }
            }
            SkillFx.play(level, "item.trident.hit", c, 1.5 + i * 0.1, 0.5);
        });
        return true;
    }

    /**
     * 그림자 난도질: 대상 (range 칸) 에게 달라붙어 gap 틱마다 hits 번 벰 (대상이 쓰러지면 4칸 안의 가장 가까운 적), 마지막은 큰 X —
     * v2.25.0 벨 때마다 대상 둘레의 다른 쪽에서 그림자 분신이 나타나 베고 (내 쪽 ±55° 는 비움), 마지막은 분신 셋이 한꺼번에 찌름
     */
    static boolean shadowFlurry(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) {
            noTarget(sp, k);
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("assassin");
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 from = sp.position();
        Vec3 tl = target.position();
        Vec3 d = V.flat(V.sub(tl, from));
        double gap0 = Math.hypot(tl.x - from.x, tl.z - from.z);
        double want = Math.max(0, gap0 - 1.4);
        double dist = Math.min(want, RuneKit.dashDistance(sp, d, want));
        // 설 수 있는 가장 먼 자리까지 (물 · 용암 · 허공이면 조금씩 짧게, 없으면 그 자리에서 벰)
        Vec3 to = null;
        for (double s = dist; s > 0.3 && to == null; s -= 0.5) to = Kit.standSpot(level, V.add(from, d, s));
        if (to != null) {
            SkillFx.beam(level, V.up(from, 1), V.up(to, 1), SHADE, Opt.of("half", 0.3, "thick", 0.28, "life", 0.3, "alpha", 0.75));
            shadowPuff(level, from, glow);
            RuneKit.moveTo(sp, to, target.getEyePosition()); // 못 옮기면 그 자리에서 벰
        }
        SkillFx.play(level, "mob.vex.charge", from, 1.4, 0.8);
        LivingEntity[] cur = {target};
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        SkillFx.every(hits, (int) k.num("gap"), i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            if (cur[0] == null || cur[0].isRemoved() || !Kit.canHit(sp, cur[0])) {
                List<LivingEntity> near = RuneKit.nearestTo(Kit.around(sp, sp.position(), 4), sp.position());
                cur[0] = near.isEmpty() ? null : near.get(0);
                if (cur[0] == null) return;
            }
            LivingEntity e = cur[0];
            Vec3 at = Kit.bodyPoint(e);
            Vec3 eye = sp.getEyePosition();
            Vec3 v = V.unit(V.sub(at, eye));
            boolean last = i == hits - 1;
            // 그림자 분신: 대상 둘레 (내 쪽에서 본 각 — 내 쪽 ±55° 는 비움), 대상을 보고 벰 (마지막은 셋이 찌름)
            Vec3 mine = V.flat(V.sub(sp.position(), e.position()));
            double pad = 0.9 + Kit.bodyPad(e);
            for (int deg : last ? new int[] {90, 180, 270} : new int[] {FLURRY_DEG[i % 5]}) {
                Vec3 sd = RuneKit.turn(mine, deg);
                Vec3 cf = V.add(e.position(), sd, pad);
                RuneKit.shadeClone(sp, level, cf, V.scale(sd, -1), last ? 3 : 2, CLONE, glow, Opt.of("life", last ? 0.4 : 0.28, "fade", 0.5, "alpha", 0.9,
                        "size", 0.95 * RuneKit.nearK(sp, V.up(cf, 1), 3.5, 0.55)));
            }
            double as = RuneKit.nearK(sp, V.add(at, v, -0.6), 3, 0.45); // 달라붙어 베는 참격도 가까우면 작게
            SkillFx.arc(level, V.add(at, v, -0.6), v, Opt.of("shape", "thin", "flip", i % 2 == 1, "size", 1.5 * as, "life", 0.2, "speed", 2.0, "spin",
                    (double) ((i % 2 == 1 ? 1 : -1) * (30 + i * 12)), "color", glow, "aura", SHADE));
            SkillFx.cutMark(level, V.add(at, v, -0.5), v, i % 2 == 1 ? 50 - i * 8 : -50 + i * 8, last ? 0.05 : 0.12, glow, core, 0.9 * as);
            if (assassinHit(sp, e, P, k.num("mult")) > 0) {
                SkillFx.sparks(level, V.add(at, v, -0.5), core, last ? 10 : 4, last ? 6 : 4);
                if (last) {
                    double xs = RuneKit.nearK(sp, V.add(at, v, -0.6), 3, 0.25); // 달라붙어 베므로 X 가 눈앞 — 작게 (1인칭 화면을 덮지 않게)
                    SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.6), Opt.of("dir", v, "size", 1.8 * xs, "life", 0.32, "color", glow, "alpha", 1.0));
                    SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.65), Opt.of("dir", v, "size", 1.2 * xs, "life", 0.28, "color", RuneKit.WHITE,
                            "alpha", 1.0));
                    SkillFx.burst(level, V.add(at, v, -0.5), SHADE, 1.2 * xs, 0.3);
                    SkillFx.play(level, "game.player.attack.critical", at, 1.0, 1);
                }
            }
            SkillFx.play(level, last ? "item.trident.hit" : "game.player.attack.strong", at, 1.2 + i * 0.12, 0.7);
        });
        return true;
    }

    /**
     * 월광 암살: 대상 (range 칸) 에 달빛 표식 delay 틱 (대상 둔화, 나는 사라짐) — 대상 머리 위에 거대한 초승달 (rpg:rfx_moon) 이 차오름 →
     * 대상 뒤에 나타나 거대한 X 와 달빛 검기: 대상 mult, 대상 주위 splash 칸 splashMult.
     * v2.27.0 초월 2차 강화: 한밤의 하늘 · 하늘의 초승달 · 칼날 문양 → 자홍 번쩍임 · 달이 갈라지며 섬광 · 충격 고리
     */
    static boolean moonAssassin(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) {
            noTarget(sp, k);
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("assassin");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int delay = (int) k.num("delay");
        RuneKit.effect(target, "slowness", delay + 10, 3);
        RuneKit.effect(sp, "invisibility", delay + 4, 0);
        shadowPuff(level, sp.position(), glow);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", sp.position(), 1.4, 0.8);
        SkillFx.play(level, "chime.amethyst_block", target.position(), 0.6, 1);
        // 2차 강화 — 예고: 한밤의 하늘 · 대상 뒤 하늘에 떠오르는 거대한 초승달 · 대상 발밑의 칼날 문양 (자홍)
        RuneKit.ultSkyFor(sp, level, target.position(), "moonAssassin", delay + 34, null);
        Vec3 moonAt = RuneKit.skyPoint(sp, target.position(), 9, 20, 6, 22);
        RuneKit.rfx2(level, "moon", moonAt, Opt.of("size", 4.4, "life", (delay + 6) / 20.0, "grow", 0.12, "spin", -20.0, "alpha", 0.95, "fade", 0.25), MOON,
                RuneKit.WHITE);
        RuneKit.domainSigil(level, target.position(), 3.4, "blade", glow, MOON, Opt.of("life", (delay + 2) / 20.0, "spinRate", -45.0, "grow", 0.2,
                "alpha", 0.8, "fade", 0.15));
        // 달빛 표식: 대상 머리 위 초승달이 커지며 차오르고 (달 그림 + 자홍 무리), 발밑에 자홍 룬
        SkillFx.rune(level, target.position(), Opt.of("size", 1.6, "life", delay / 20.0 + 0.2, "color", glow, "alpha", 0.85, "rate", 120.0, "progress", 1.0,
                "grow", 0.3));
        for (int t = 0; t < delay; t += 4) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (target.isRemoved() || sp.isRemoved()) return;
                Vec3 top = V.up(target.getEyePosition(), 2.2);
                RuneKit.rfx2(level, "moon", top, Opt.of("size", 0.9 + tt * 0.03, "life", 0.24, "spin", -20.0, "alpha", 0.55 + (0.45 * tt) / delay), MOON,
                        RuneKit.WHITE);
                if (tt % 8 == 0) SkillFx.fx(level, "rpg:light_cut", V.up(target.position(), 2.6), Opt.of("dir", V.DOWN, "size", 1.2, "thick", 0.08, "life", 0.3,
                        "color", MOON, "alpha", 0.6));
            });
        }
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return; // 그 사이 죽었거나 다른 차원: 옛 대상에게 옮기지 않음
            LivingEntity tgt = !target.isRemoved() && target.level() == sp.level() ? target : null;
            Vec3 c = tgt != null ? tgt.position() : V.add(sp.position(), V.flat(sp.getViewVector(1F)), 3);
            if (tgt != null) {
                Vec3 dest = behindSpot(level, tgt, sp.position());
                if (dest != null) RuneKit.moveTo(sp, dest, tgt.getEyePosition()); // 못 옮기면 그 자리에서 벰
            }
            Vec3 eye = sp.getEyePosition();
            Vec3 at = tgt != null ? Kit.bodyPoint(tgt) : V.up(c, 1);
            Vec3 v = V.unit(V.sub(at, eye));
            shadowPuff(level, sp.position(), glow);
            // 발동: 자홍 번쩍임 · 하늘의 달이 갈라짐 → 섬광 · 대상 둘레로 퍼지는 고리 (1틱 뒤)
            RuneKit.ultFlashFor(sp, level, at, "moonAssassin", "flash", Opt.empty());
            RuneKit.skyPlate(sp, level, "crack", moonAt, 5, glow, MOON, Opt.of("life", 0.6, "grow", 0.5, "fade", 0.6));
            RuneKit.later(1, () -> {
                RuneKit.skyFlare(level, moonAt, 3.6, glow, RuneKit.WHITE, Opt.of("life", 0.5));
                RuneKit.novaRing(level, c, 1.2, 3.5, glow, MOON, Opt.of("life", 0.45));
            });
            // 달이 부서지며 거대한 X + 달빛 검기 둘
            if (tgt != null) RuneKit.rfx2(level, "moon", V.up(tgt.getEyePosition(), 2.2), Opt.of("size", 1.5, "life", 0.25, "grow", 0.5, "fade", 0.8,
                    "spin", -20.0), MOON, RuneKit.WHITE);
            SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.6), Opt.of("dir", v, "size", 2.8, "life", 0.4, "color", glow, "alpha", 1.0));
            SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.7), Opt.of("dir", v, "size", 2.0, "life", 0.34, "color", MOON, "alpha", 1.0));
            double wk = RuneKit.nearK(sp, at, 4, 0.45);
            for (double spin : new double[] {35, -35}) {
                RuneKit.rfx2(level, "wave", V.add(at, v, -0.25), Opt.of("size", 1.2 * wk, "life", 0.3, "spin", spin, "grow", 0.25, "fade", 0.5, "alpha", 0.75),
                        glow, MOON);
            }
            SkillFx.cutMark(level, V.add(at, v, -0.55), v, 45, 0.05, glow, RuneKit.WHITE, 1.6);
            SkillFx.cutMark(level, V.add(at, v, -0.55), v, -45, 0.05, glow, RuneKit.WHITE, 1.6);
            SkillFx.burst(level, V.add(at, v, -0.5), SHADE, 1.8, 0.4);
            SkillFx.ring(level, V.add(at, v, -0.7), v, glow, 1.4, 0.4, 0);
            RuneKit.zonePulse(level, c, glow, k.num("splash"), 0.45);
            SkillFx.sparks(level, at, core, 12, 7);
            SkillFx.play(level, "item.trident.thunder", at, 1.6, 0.6);
            SkillFx.play(level, "game.player.attack.critical", at, 0.8, 1);
            RuneKit.shakeAround(level, at, 8, 0.2, 0.25);
            int stun = (int) k.numOr("stun", 0); // 8단계 3돌: X 베기에 맞은 적 기절
            if (tgt != null && assassinHit(sp, tgt, P, k.num("mult")) > 0 && stun > 0) RuneKit.stun(tgt, stun, glow, true);
            int marks = 0;
            for (LivingEntity e : Kit.around(sp, c, k.num("splash"))) {
                if (tgt != null && e.getUUID().equals(tgt.getUUID())) continue;
                if (assassinHit(sp, e, P, k.num("splashMult")) > 0 && stun > 0) RuneKit.stun(e, stun, glow, marks++ < 8);
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 암살 표식 · 이어 쓰기 콤보 (RUNE_COMBO)

    /** 암살 표식 하나: 붙인 사람 · 몹 · 끝나는 틱 */
    private static final class Mark {
        final UUID owner;
        final LivingEntity e;
        long until;

        Mark(UUID owner, LivingEntity e, long until) {
            this.owner = owner;
            this.e = e;
            this.until = until;
        }
    }

    /**
     * 콤보 하나: 이은 등급들 · 마지막으로 누른 틱 · 마지막 타격 틱 (마무리 기술 포함) · 마무리 기술이 잡힘.
     * 이어 쓴 룬 · 마무리 기술은 누를 때의 콤보를 들고 있음 (그 사이 새 콤보가 시작돼도 서로 건드리지 않게)
     */
    private static final class St {
        final List<String> chain = new ArrayList<>();
        long last;
        long end;
        boolean finishing;

        St(long now) {
            last = now;
            end = now;
        }
    }

    /** 누를 때 세운 콤보 계획과 그 단계 (0 = 콤보 없음, 1 = 새로 시작) — RuneCast.Plan 에는 배율 · 나갈 틱만 있어 comboCommit 이 여기서 단계를 찾음 */
    private record Planned(RuneCast.Plan plan, int step) {}

    /** 암살 표식: "플레이어|몹 id" → 표식 */
    private static final Map<String, Mark> MARKS = new LinkedHashMap<>();
    /** 콤보: 플레이어 → 지금 콤보 */
    private static final Map<UUID, St> COMBOS = new HashMap<>();
    /** 플레이어 → 방금 세운 콤보 계획 (comboPlan → comboCommit, 같은 틱) */
    private static final Map<UUID, Planned> PLANNED = new HashMap<>();

    private static void markTarget(ServerPlayer sp, LivingEntity e) {
        MARKS.put(sp.getUUID() + "|" + e.getUUID(), new Mark(sp.getUUID(), e, Tasks.now() + MARK_TICKS));
    }

    /** 표식이 남은 적 (range 칸 안, 가까운 순) — 끝났거나 맞을 수 없는 표식은 지움 */
    static List<LivingEntity> markedTargets(ServerPlayer sp, double range) {
        long now = Tasks.now();
        String p = sp.getUUID() + "|";
        List<LivingEntity> out = new ArrayList<>();
        for (Iterator<Map.Entry<String, Mark>> it = MARKS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Mark> en = it.next();
            if (!en.getKey().startsWith(p)) continue;
            Mark m = en.getValue();
            if (m.until <= now || m.e.isRemoved() || !Kit.canHit(sp, m.e)) {
                it.remove();
                continue;
            }
            if (V.len(V.sub(m.e.position(), sp.position())) <= range) out.add(m.e);
        }
        return RuneKit.nearestTo(out, sp.position());
    }

    /** 콤보가 살아 있는지 (지우지 않고 봄): 3초 안에 눌렀거나 이은 룬 · 마무리 기술이 아직 나가는 중 */
    private static boolean comboLive(UUID id, long now) {
        St st = COMBOS.get(id);
        return st != null && (now - st.last <= WINDOW || now <= comboBusyEnd(st) + GAP_AFTER);
    }

    /** 표식 표시: 0.5초마다 표식이 남은 몹의 머리 위에 작은 자홍 룬 (몹을 따라다님). 콤보가 이어지는 동안은 표식이 사라지지 않음 (처형 대상) */
    private static void markTick() {
        if (MARKS.isEmpty()) return;
        long now = Tasks.now();
        double[] glow = RuneKit.colorOf("assassin")[0];
        for (Iterator<Mark> it = MARKS.values().iterator(); it.hasNext(); ) {
            Mark m = it.next();
            if (comboLive(m.owner, now)) m.until = Math.max(m.until, now + 20);
            if (m.until <= now || m.e.isRemoved()) {
                it.remove();
                continue;
            }
            if (m.e.level() instanceof ServerLevel level) {
                SkillFx.rune(level, V.up(m.e.getEyePosition(), 0.45), Opt.of("size", 0.38, "life", 0.55, "color", glow, "alpha", 0.95, "rate", 260.0));
            }
        }
    }

    /** 가진 암살자 룬 (등급) — 스킬 칸에 올린 것 (자바 판은 룬을 스킬 칸에서만 씀 — 애드온은 가방 36칸 + 왼손) */
    static Set<String> ownedComboRunes(ServerPlayer sp) {
        Set<String> set = new HashSet<>();
        for (ItemStack it : SkillBar.slots(sp)) noteRune(set, it);
        return set;
    }

    private static void noteRune(Set<String> set, ItemStack it) {
        Runes.Rune r = RuneItems.runeOf(it);
        if (r != null && r.family().equals(FAMILY)) set.add(r.grade());
    }

    /** 그림자 처형이 시작해서 마지막 타격까지 (대상이 가장 많을 때) */
    private static long finishTicks() {
        return FINISH_LEAD + (FIN_MAX - 1) * FIN_GAP;
    }

    /** 이 콤보의 마지막 타격 틱: 다섯 가지를 모두 이었는데 마무리 기술이 아직 안 잡혔으면 (마지막 룬이 대기 중) 그것까지 */
    private static long comboBusyEnd(St st) {
        boolean full = st.chain.size() >= GRADE_ORDER.size() && !st.finishing;
        return st.end + (full ? GAP_AFTER + finishTicks() : 0);
    }

    /** 지금 콤보 (끊겼으면 지우고 null) */
    private static St comboOf(ServerPlayer sp) {
        St st = COMBOS.get(sp.getUUID());
        if (st == null) return null;
        long now = Tasks.now();
        if (now - st.last > WINDOW && now > comboBusyEnd(st) + GAP_AFTER) {
            COMBOS.remove(sp.getUUID());
            return null;
        }
        return st;
    }

    /** 룬의 마지막 타격까지 틱 (r.def.span — 가장 늦을 때) */
    private static long span(Runes.Rune r) {
        return (long) r.def().numOr("span", 0);
    }

    /**
     * 암살자 룬을 누를 때의 콤보 계획: 배율 (1 + step × (단계 − 1)), 나갈 틱 — 단계 (0 = 콤보 없음, 1 = 새로 시작) 는 PLANNED 에 적어 둠.
     * 이어 쓰기 = 가진 암살자 룬 minOwned 종 이상 · 마지막으로 누른 뒤 window 틱 안 · 이번 연계에서 아직 안 쓴 룬 · 마무리 기술 전.
     * 앞 룬이나 마무리 기술이 아직 나가는 중이면 (이어 쓰기가 아니어도) 그 마지막 타격 gapAfter 틱 뒤에 나감: 무적 시간에 피해가 사라지지 않게
     */
    private static RuneCast.Plan comboPlan(ServerPlayer sp, Runes.Rune r) {
        long now = Tasks.now();
        int owned = ownedComboRunes(sp).size();
        St st = COMBOS.get(sp.getUUID());
        long start = st != null ? Math.max(now, comboBusyEnd(st) + GAP_AFTER) : now;
        int step = 0;
        if (owned >= COMBO.num("minOwned")) {
            boolean chaining = st != null && !st.finishing && now - st.last <= WINDOW && !st.chain.contains(r.grade()) && st.chain.size() < GRADE_ORDER.size();
            step = chaining ? st.chain.size() + 1 : 1;
        }
        RuneCast.Plan plan = new RuneCast.Plan(step == 0 ? 1 : 1 + COMBO.num("step") * (step - 1), start);
        PLANNED.put(sp.getUUID(), new Planned(plan, step));
        return plan;
    }

    /** 이 계획의 단계 (comboPlan 이 적어 둔 것 — 없으면 가진 룬 수와 배율로 되돌려 셈) */
    private static int stepOf(ServerPlayer sp, RuneCast.Plan plan) {
        Planned p = PLANNED.remove(sp.getUUID());
        if (p != null && p.plan() == plan) return p.step();
        if (ownedComboRunes(sp).size() < COMBO.num("minOwned")) return 0;
        double k = COMBO.num("step");
        return k > 0 ? 1 + (int) Math.round((plan.mult() - 1) / k) : 1;
    }

    /** 콤보에 넣음 (누른 순간). 2단계부터 눈앞에 "N 콤보!". 넣은 콤보를 돌려줌 (콤보 없음이면 null) */
    private static St comboCommit(ServerPlayer sp, Runes.Rune r, RuneCast.Plan plan) {
        long now = Tasks.now();
        int step = stepOf(sp, plan);
        if (step == 0) {
            COMBOS.remove(sp.getUUID());
            return null;
        }
        St st = step == 1 ? new St(now) : COMBOS.get(sp.getUUID());
        if (st == null) return null;
        st.chain.add(r.grade());
        st.last = now;
        st.end = plan.start() + span(r);
        COMBOS.put(sp.getUUID(), st);
        if (step >= 2) {
            // 조준점 오른쪽 조금 아래 (바로 앞 몹의 피해 숫자는 조준점 위에 뜸)
            DamageNumbers.showFace(sp, "§d§l" + step + " 콤보!", 2.2, 0.8, -0.2);
            Notify.notify(sp, "§d" + step + " 콤보 §7" + r.def().str("name") + " 피해 §f+" + Js.str(Js.round((plan.mult() - 1) * 100)) + "%"
                    + (plan.start() > now ? " §8(앞 룬 뒤에 나감)" : ""), 30);
            SkillFx.playFor(sp, "note.pling", 0.9 + step * 0.12, 0.7);
        }
        return st;
    }

    /**
     * 이어 쓴 룬이 나가지 못함 (대상이 없음 · 죽음 등): 그 콤보에서 빼고 재사용 대기를 돌려줌.
     * 자바는 죽어서 다시 태어나면 플레이어 객체가 바뀜 (castRune 은 옛 객체라 돌려주지 않음) — 지금의 플레이어에게 돌려줌 (나갔으면 없음)
     */
    private static void comboRollback(ServerPlayer sp, Runes.Rune r, St st) {
        if (st != null) st.chain.removeIf(g -> g.equals(r.grade()));
        ServerPlayer cur = sp.isRemoved() ? RuneKit.player(sp.level(), sp.getUUID()) : sp;
        if (cur != null) RuneCast.refundCooldown(cur, r);
    }

    /** 룬이 실제로 나감: 그 콤보에 다섯 가지를 모두 이었고 이 룬이 마지막이면, 마지막 타격 gapAfter 틱 뒤에 마무리 기술 */
    private static void comboLanded(ServerPlayer sp, Runes.Rune r, St st) {
        if (st == null || st.chain.size() < GRADE_ORDER.size() || st.finishing || !st.chain.get(st.chain.size() - 1).equals(r.grade())) return;
        st.finishing = true;
        String life = RuneKit.lifeToken(sp);
        long now = Tasks.now();
        long at = now + span(r) + GAP_AFTER;
        // 마무리 기술의 마지막 타격까지 (대상이 가장 많을 때): 그 사이에 누른 암살자 룬은 이 다음에 나감
        st.end = Math.max(st.end, at + finishTicks());
        RuneKit.later(at - now, () -> {
            int n = RuneKit.sameLife(sp, life) ? shadowExecution(sp) : 0;
            // 실제 대상 수로 끝나는 틱을 줄이고, 마지막 타격이 끝나면 이 콤보를 지움 (그 사이 새 콤보가 시작됐으면 그대로 둠)
            st.end = Math.min(st.end, Tasks.now() + FINISH_LEAD + Math.max(0, n - 1) * FIN_GAP);
            RuneKit.later(Math.max(1, st.end + GAP_AFTER - Tasks.now()), () -> {
                if (COMBOS.get(sp.getUUID()) == st) COMBOS.remove(sp.getUUID());
            });
        });
    }

    /** 앞에서 n 개 */
    private static List<LivingEntity> first(List<LivingEntity> list, int n) {
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    /** 마무리 기술 그림자 처형: 표식이 남은 적마다 (없으면 주위 radius 칸) 양옆에서 그림자 분신이 교차하며 벰. 대상 수를 돌려줌 */
    static int shadowExecution(ServerPlayer sp) {
        double P = RuneCast.power(sp, FAMILY);
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("assassin");
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 c = sp.position();
        List<LivingEntity> marked = first(markedTargets(sp, FIN.num("range")), FIN_MAX);
        List<LivingEntity> list = !marked.isEmpty() ? marked : first(RuneKit.nearestTo(Kit.around(sp, c, FIN.num("radius")), c), FIN_MAX);
        Vec3 eye = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        // 조준점 아래에서 떠오름 (조준점 위는 맞은 적의 피해 숫자 자리)
        DamageNumbers.showFace(sp, "§5§l" + FIN.str("name") + "!", 2.2, 0, -0.6);
        SkillFx.fx(level, "rpg:slash_x", V.add(eye, view, 2.4), Opt.of("dir", view, "size", 2.2, "life", 0.45, "color", SHADE, "alpha", 0.95));
        SkillFx.ring(level, V.add(eye, view, 2.0), view, glow, 2.4, 0.5, 0);
        SkillFx.rune(level, c, Opt.of("size", 3.2, "life", 1.0, "color", glow, "alpha", 0.9, "rate", 200.0, "grow", 0.4));
        SkillFx.play(level, "mob.evocation_illager.cast_spell", c, 0.8, 1);
        SkillFx.play(level, "item.trident.thunder", c, 1.8, 0.5);
        for (int i = 0; i < list.size(); i++) {
            LivingEntity e = list.get(i);
            int ii = i;
            RuneKit.later(FINISH_LEAD + i * FIN_GAP, () -> {
                if (sp.isRemoved() || e.isRemoved()) return;
                Vec3 at = Kit.bodyPoint(e);
                Vec3 toward = V.flat(V.sub(at, sp.position()));
                Vec3 side = V.rightOf(toward);
                for (int s : new int[] {-1, 1}) {
                    Vec3 a = V.add(V.up(at, 0.2), side, s * 1.9);
                    Vec3 b = V.add(V.up(at, -0.2), side, -s * 1.9);
                    RuneKit.flyStreak(level, a, b, 38, SHADE, glow, 1.1, 0.55);
                }
                Vec3 v = V.unit(V.sub(at, sp.getEyePosition()));
                SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.2), Opt.of("dir", v, "size", 2.1, "life", 0.36, "color", glow, "alpha", 1.0));
                SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.25), Opt.of("dir", v, "size", 1.4, "life", 0.3, "color", RuneKit.WHITE, "alpha", 1.0));
                SkillFx.burst(level, at, SHADE, 1.5, 0.35);
                SkillFx.sparks(level, at, core, 10, 6);
                SkillFx.fx(level, "rpg:arrow_shot", V.up(e.position(), 1.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.7, "thick", 0.45, "life", 0.4,
                        "color", glow, "alpha", 0.85));
                SkillFx.play(level, "game.player.attack.critical", at, 0.9 + ii * 0.08, 1);
                RuneKit.runeHit(sp, e, P, FIN.num("mult"), "dex");
                MARKS.remove(sp.getUUID() + "|" + e.getUUID());
            });
        }
        RuneKit.later(6 + list.size() * FIN_GAP, () -> {
            if (sp.isRemoved()) return;
            SkillFx.play(level, "firework.large_blast", sp.position(), 0.9, 1);
            RuneKit.shakeAround(level, sp.position(), 12, 0.3, 0.35);
        });
        return list.size();
    }

    /** castRune · 상태 줄에 넣는 이어 쓰기 콤보 (RuneCast.Combo — 콤보 상태 = St) */
    private static final class ComboHook implements RuneCast.Combo {
        @Override
        public RuneCast.Plan plan(ServerPlayer sp, Runes.Rune r) {
            return comboPlan(sp, r);
        }

        @Override
        public Object commit(ServerPlayer sp, Runes.Rune r, RuneCast.Plan plan) {
            return comboCommit(sp, r, plan);
        }

        @Override
        public void landed(ServerPlayer sp, Runes.Rune r, Object state) {
            comboLanded(sp, r, state instanceof St st ? st : null);
        }

        @Override
        public void rollback(ServerPlayer sp, Runes.Rune r, Object state) {
            comboRollback(sp, r, state instanceof St st ? st : null);
        }

        /** 죽거나 차원이 바뀜: 그 콤보가 지금 콤보면 끝 (castRune 의 combos.get(player.id) === st → delete) */
        @Override
        public void drop(ServerPlayer sp, Object state) {
            if (COMBOS.get(sp.getUUID()) == state) COMBOS.remove(sp.getUUID());
        }

        /** 상태 줄 덧붙임 (콤보가 이어지는 동안만 — 아니면 ""): 이은 단계 / 5 · 2단계부터 콤보 수 */
        @Override
        public String hud(ServerPlayer sp) {
            St st = comboOf(sp);
            if (st == null || st.chain.isEmpty()) return "";
            int n = st.chain.size();
            return " §8| §d암살자 " + n + "/" + GRADE_ORDER.size() + (n >= 2 ? " §l" + n + " 콤보" : "");
        }
    }
}
