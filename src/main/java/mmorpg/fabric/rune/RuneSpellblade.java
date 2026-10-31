package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 마검사 룬 (runes.js "v2.13.0 마검사" — 남보라 마력 + 청록 칼날: 검과 마력을 섞어 씀): 마력 베기 · 마나 블라스터 · 차원 베기 · 마검 결계 · 마검 해방.
 * 검 = 공격 피해 (노란 숫자, RuneKit.swordHit) · 마력 = 마법 피해 (보라 숫자, RuneKit.spellHit).
 * 마검 부여 (RUNE_SPELLBLADE): 마검사 룬을 쓰면 다음 평타 몇 번에 마력 검기 — 평타에 더한 피해 + 맞은 적 너머를 꿰뚫는 검기
 */
final class RuneSpellblade {
    private RuneSpellblade() {}

    private static final Obj SPELLBLADE = Rules.get().obj("RUNE_SPELLBLADE");
    /** 마검 부여: hits (평타 수) · ticks (룬 뒤 시간) · pierce · width · speed · lead (검기) */
    private static final Obj IMBUE = SPELLBLADE.obj("imbue");
    private static final String FAMILY = SPELLBLADE.str("family");

    static void register() {
        RuneCast.register("arcaneCut", RuneSpellblade::arcaneCut);
        RuneCast.register("manaBlaster", RuneSpellblade::manaBlaster);
        RuneCast.register("riftSlash", RuneSpellblade::riftSlash);
        RuneCast.register("orbitBlades", RuneSpellblade::orbitBlades);
        RuneCast.register("spellbladeRelease", RuneSpellblade::spellbladeRelease);
        // 마검 부여: 마검사 룬을 쓰면 부여 (castRune), 평타 · 활 · 완드 피해에 더함 (RpgCombat), 들어가면 한 번 씀 + 마력 검기, 상태 줄 덧붙임, 손 둘레 빛
        RuneCast.afterCast(FAMILY, RuneSpellblade::grantImbue);
        RpgCombat.addWeaponBonusHook(RuneSpellblade::imbueBonus);
        RpgCombat.addWeaponHitHook(RuneSpellblade::imbueLanded);
        Notify.addHudSuffix(RuneSpellblade::imbueHud);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 4 == 0) imbueTick(server);
        });
        // 죽음 · 차원 이동 · 탈것 부르기 (onLifeEnded) · 나감 (forgetRunes) · 서버 멈춤: 마검 부여도 끝
        RuneKit.onLifeEnded(id -> IMBUES.remove(id));
        RuneSystem.onForget(id -> IMBUES.remove(id));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> IMBUES.clear());
    }

    /**
     * 마력 베기: 앞을 베고 (검) gap 틱 뒤 벤 자리에 마력 폭발 (마력) — 앞으로 퍼지는 초승달 검기 (rpg:rfx_wave),
     * 터질 때 마력의 별빛 문장 (rpg:rfx_sun 작게) 이 번쩍 (6명까지)
     */
    static boolean arcaneCut(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[][] cs = RuneKit.colorOf("spellblade");
        double[] glow = cs[0];
        double[] core = cs[1];
        RuneKit.arc(level, V.up(V.add(head, f, 1.4), -0.3), f, Opt.of("shape", "arc", "flip", false, "size", 2.7, "life", 0.24, "speed", 3.0, "spin", -6.0,
                "color", core, "aura", glow));
        RuneKit.rfx2(level, "wave", V.up(V.add(head, f, 2.3), -0.35), Opt.of("dir", f, "speed", 6.0, "size", 1.45, "life", 0.22, "grow", 0.35, "fade", 0.6,
                "spin", 4.0, "alpha", 0.9), glow, core);
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(head, f, 2.0), -0.25), Opt.of("dir", V.rightOf(f), "size", 2.6, "thick", 0.16, "life", 0.3, "delay", 0.04,
                "color", glow, "alpha", 1.0));
        SkillFx.play(level, "item.spear.attack_miss", head, 1.2, 0.9);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", head, 1.7, 0.4);
        String life = RuneKit.lifeToken(sp);
        double gap = k.num("gap");
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 10)) {
            if (RuneKit.swordHit(sp, e, P, k.num("mult")) <= 0) continue;
            hit.add(e);
            Vec3 at = Kit.bodyPoint(e);
            SkillFx.cutMark(level, at, V.unit(V.sub(at, head)), -8, 0.1, core, RuneKit.WHITE, 1.0);
            // 벤 자리에 마력 문양 (gap 틱 동안 그려지다 터짐)
            SkillFx.fx(level, "rpg:magic_circle", at, Opt.of("size", 0.42, "life", gap / 20, "spin", 0.0, "progress", 1.0, "color", glow, "alpha", 0.9));
        }
        RuneKit.later(gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            int n = 0;
            for (LivingEntity e : hit) {
                if (e.isRemoved()) continue;
                Vec3 at = Kit.bodyPoint(e);
                if (RuneKit.spellHit(sp, e, P, k.num("mult")) <= 0) continue;
                // 터지는 자리 (파티클 예산): 별빛 문장 6명 · 번쩍임 10명 · 불꽃 4명까지
                if (n < 10) {
                    SkillFx.burst(level, at, glow, 1.1, 0.3);
                    SkillFx.fx(level, "rpg:magic_circle_flash", at, Opt.of("size", 0.55, "life", 0.25, "spin", 0.0, "progress", 1.0, "color", glow, "alpha", 1.0));
                }
                if (n < 6) RuneKit.rfx2(level, "sun", at, Opt.of("size", 0.55, "life", 0.26, "grow", 0.7, "fade", 0.6, "spin", Kit.rand() * 360), glow, core);
                if (n < 4) SkillFx.sparks(level, at, core, 4, 5);
                n++;
            }
            if (!hit.isEmpty()) SkillFx.play(level, "chime.amethyst_block", sp.position(), 1.3, 1);
        });
        return true;
    }

    /** 지금의 광선: 피해는 눈 → 보는 방향 (조준점과 같은 줄, 길이 len), 그림은 오른손 앞 (hand) → 그 끝 (end) */
    private record Beam(Vec3 eye, Vec3 dir, double len, Vec3 end, Vec3 hand) {}

    private static Beam beamNow(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition();
        Vec3 dir = sp.getViewVector(1F);
        double len = Math.max(0.5, RuneKit.rayLength(sp.level(), eye, dir, range));
        Vec3 f = V.flat(dir);
        return new Beam(eye, dir, len, V.add(eye, dir, len), V.up(V.add(V.add(eye, f, 0.9), V.rightOf(f), 0.35), -0.32));
    }

    /**
     * 마나 블라스터 (마검사 희귀 — "마력 쪽 지속 딜링기"): (hits − 1) × gap 틱 동안 조준점을 따라가는 마력 광선.
     * 광선 = 눈에서 보는 방향으로 range 칸 (벽 · 땅 앞까지) — 그림은 오른손 앞에서 그 끝까지 (1인칭에서 손에서 조준점으로 뻗음).
     * 누른 순간부터 gap 틱마다 광선 폭 width 칸 안의 적 모두에게 위력 × mult (마력, 마법 피해, 보라 숫자). 대상이 없어도 나감 (취소 없음).
     * 죽음 · 차원 이동 · 탈것 부르기면 멈춤 (삶 표시). 광선 끝에 마력 구슬 (rpg:rfx_plasma — 남보라 빛 · 청록 심지)
     */
    static boolean manaBlaster(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("spellblade");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int lastHit = (hits - 1) * gap; // 마지막 타격 틱
        double range = k.num("range");
        ServerLevel level0 = sp.level();
        SkillFx.play(level0, "beacon.activate", sp.position(), 1.6, 0.9);
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", sp.position(), 1.4, 0.6);
        // 광선 (2틱마다 다시 그림 — 굵은 남보라 빛 + 청록 심지, 피해가 들어가는 순간은 더 굵게, 끝나는 4틱은 가늘어짐)
        //   빛줄기 조각 (rpg:arrow_trail) 은 양 끝 15% 가 흐려지므로 두 조각을 겹쳐 잇고 끝을 0.8칸 더 늘림 (조준점 · 맞은 적에서 흐려지지 않게)
        // · 손 앞 작게 도는 마법진 · 끝에서 튀는 빛과 마력 구슬 · 광선을 타고 나가는 고리
        for (int t = 0; t <= lastHit + 4; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Beam b = beamNow(sp, range);
                Vec3 seg = V.sub(b.end(), b.hand());
                double sl = V.len(seg);
                if (sl < 0.3) return;
                Vec3 u = V.scale(seg, 1 / sl);
                boolean pulse = tt % gap == 0 && tt <= lastHit;
                double thick = (pulse ? 0.42 : 0.3) * (tt > lastHit ? 0.5 : 1);
                double total = sl + 0.8;
                // 손 쪽 조각은 가늘게 · 먼 조각은 굵게 (1인칭 원근: 손 앞이 화면을 덮지 않고 먼 곳도 보이게) — {시작, 끝, 굵기 배율}
                for (double[] p : new double[][] {{0, total / 2 + 0.6, 0.75}, {total / 2 - 0.6, total, 1.25}}) {
                    Vec3 mid = V.add(b.hand(), u, (p[0] + p[1]) / 2);
                    SkillFx.fx(level, "rpg:arrow_trail", mid, Opt.of("dir", u, "speed", 0.0, "size", (p[1] - p[0]) / 2, "thick", thick * p[2], "life", 0.15, "delay", 0.0,
                            "color", glow, "alpha", 0.85));
                    SkillFx.fx(level, "rpg:arrow_trail", mid, Opt.of("dir", u, "speed", 0.0, "size", (p[1] - p[0]) / 2, "thick", thick * p[2] * 0.36, "life", 0.15,
                            "delay", 0.0, "color", core, "alpha", 1.0));
                }
                if (tt % 4 == 0 && tt <= lastHit) {
                    SkillFx.fx(level, "rpg:magic_circle", b.hand(), Opt.of("size", 0.16, "life", 0.22, "spin", tt * 12.0, "progress", 1.0, "color", glow, "alpha", 0.9));
                    // 광선 끝 (맞는 자리) 의 마력 구슬 — 가까운 벽을 겨누면 작게
                    RuneKit.plasmaOrb(level, b.end(), (pulse ? 0.55 : 0.42) * RuneKit.nearK(sp, b.end(), 3, 0.2), glow, Opt.of("life", 0.24, "fade", 0.2));
                    SkillFx.sparks(level, b.end(), core, 3, 4);
                }
                if (tt % 6 == 0 && tt <= lastHit && sl > 1.5) SkillFx.ring(level, V.add(b.hand(), u, 1.0), u, glow, 0.28, Math.min(0.6, (sl - 1) / 24), 24);
            });
        }
        // 피해 (누른 순간 · gap 틱마다): 그때 광선 안의 적 모두 (적마다 한 번)
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Beam b = beamNow(sp, range);
            int n = 0;
            for (Kit.LineHit h : RuneKit.lineTargets(sp, b.eye(), b.dir(), b.len(), k.num("width"))) {
                LivingEntity e = h.e();
                if (RuneKit.spellHit(sp, e, P, k.num("mult")) <= 0) continue;
                Vec3 at = Kit.bodyPoint(e);
                if (n < 10) SkillFx.burst(level, at, glow, 0.7, 0.2); // 번쩍임 10명 · 불꽃 4명까지 (파티클 예산)
                if (n < 4) SkillFx.sparks(level, at, core, 4, 5);
                n++;
            }
            SkillFx.burst(level, b.end(), glow, 0.6, 0.2);
            // 맞는 자리 번쩍임
            SkillFx.fx(level, "rpg:magic_circle_flash", b.end(), Opt.of("size", 0.5, "life", 0.2, "spin", i * 40.0, "progress", 1.0, "color", glow, "alpha", 1.0));
            SkillFx.play(level, "mob.evocation_illager.cast_spell", b.hand(), 1.9, 0.35);
            if (n > 0) SkillFx.play(level, "item.trident.hit", b.end(), 1.7, 0.4);
            if (i == hits - 1) RuneKit.later(4, () -> SkillFx.play(level, "beacon.deactivate", sp.position(), 1.5, 0.7));
        });
        return true;
    }

    /** 허공의 틈 조각 (rpg:rfx_rift): 자리 · 방향 · 반 길이 */
    private record Rift(Vec3 at, Vec3 dir, double size) {}

    /**
     * 차원 베기: 앞으로 dist 칸 순간이동하며 (벽 앞까지, 한 칸 턱은 넘음) 지나간 길 폭 width 칸을 벰 (검, 공격 피해)
     * → 남보라로 갈라진 자리가 delay 틱 뒤 터짐 (마력, 마법 피해: 그때 갈라진 자리 폭 width + 0.4 칸 안의 적, 조금 띄움).
     * 벤 적이 있으면 도착해서 갈라진 자리 쪽으로 돌아섬 (1인칭에서 폭발과 피해 숫자가 보이게), 없으면 (이동으로 씀) 앞을 본 채로.
     * 허공이 찢어진 틈 그림 (rpg:rfx_rift — 안은 어두운 공허) 이 길을 따라 열려 있다가 터질 때 크게 벌어지며 사라짐
     */
    static boolean riftSlash(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double dist = RuneKit.dashDistance(sp, f, k.num("dist"), 1.1, 1.8);
        Vec3 ahead = V.add(start, f, dist);
        Vec3 end = new Vec3(ahead.x, RuneKit.groundAt(level, ahead, start.y), ahead.z);
        double[][] cs = RuneKit.colorOf("spellblade");
        double[] glow = cs[0];
        double[] core = cs[1];
        double len = Math.max(0.5, dist + 0.5);
        Vec3 from = V.up(start, 1.0);
        double width = k.num("width");
        List<Kit.LineHit> path = RuneKit.lineTargets(sp, from, f, len, width);
        RuneKit.arc(level, V.up(V.add(start, f, 1.2), 1.1), f, Opt.of("shape", "thin", "flip", false, "size", 1.8, "life", 0.24, "speed", 5.0, "spin", -15.0,
                "color", core, "aura", glow));
        SkillFx.burst(level, from, glow, 1.1, 0.25);
        SkillFx.sparks(level, from, core, 6, 5);
        SkillFx.play(level, "mob.endermen.portal", start, 1.3, 0.8);
        SkillFx.play(level, "item.trident.riptide_1", start, 1.5, 0.7);
        boolean turnBack = dist > 0.2 && !path.isEmpty();
        if (dist > 0.2) RuneKit.moveTo(sp, end, turnBack ? V.up(V.add(start, f, len * 0.45), 1.0) : null);
        Vec3 g = turnBack ? V.scale(f, -1) : f; // 도착해서 보는 쪽
        RuneKit.arc(level, V.up(V.add(end, g, 0.8), 1.1), g, Opt.of("shape", "thin", "flip", true, "size", 2.0, "life", 0.26, "speed", 3.0, "spin", 20.0,
                "color", core, "aura", glow));
        SkillFx.ring(level, V.add(V.up(end, 1.5), g, 0.9), g, glow, 1.0, 0.3, 2.5);
        int cutN = 0;
        for (Kit.LineHit h : path) {
            LivingEntity e = h.e();
            if (RuneKit.swordHit(sp, e, P, k.num("mult")) <= 0) continue;
            Vec3 at = Kit.bodyPoint(e);
            // X 10명 · 불꽃 3명까지 (파티클 예산), 가까우면 작게
            if (cutN < 10) SkillFx.fx(level, "rpg:slash_x", at, Opt.of("dir", f, "size", 1.0 * RuneKit.nearK(sp, at, 3, 0.4), "life", 0.25, "color", core, "alpha", 1.0));
            if (cutN++ < 3) SkillFx.sparks(level, at, core, 4, 4);
        }
        // 갈라진 자리: 바닥의 남보라 금 + 허공의 틈 (delay 동안 열려 있음 — 처음 0.1초에 벌어짐)
        double delay = k.num("delay");
        for (double s = 0.4; s <= len; s += 0.9) {
            SkillFx.fx(level, "rpg:light_cut", V.up(V.add(start, f, s), 0.1), Opt.of("dir", f, "size", 0.55, "thick", 0.22, "life", delay / 20 + 0.1, "color", glow,
                    "alpha", 0.95));
        }
        // 허공의 틈: 2.2칸 남짓한 조각들이 길을 따라 이어짐 (조각마다 조금씩 비뚤게) — 도착한 내 몸 1.8칸 안의 조각은 빼서 1인칭 화면 아래를 덮지 않게
        List<Rift> segs = new ArrayList<>();
        int nSeg = (int) Math.max(1, Math.round(len / 2.2));
        for (int i = 0; i < nSeg; i++) {
            double s0 = len * (i + 0.5) / nSeg;
            int sd = i % 2 == 1 ? 1 : -1;
            Vec3 at = V.up(V.add(V.add(start, f, s0), V.rightOf(f), sd * 0.12), 1.0 + (i % 3) * 0.08);
            if (Math.hypot(at.x - end.x, at.z - end.z) < 1.8) continue;
            // 이웃 조각과 겹쳐 한 줄로 이어 보이게
            segs.add(new Rift(at, V.unit(V.add(f, V.rightOf(f), sd * 0.1)), len / nSeg / 2 + 0.45));
        }
        for (Rift g2 : segs) {
            RuneKit.rfx2(level, "rift", g2.at(), Opt.of("dir", g2.dir(), "size", g2.size(), "life", delay / 20 + 0.05, "grow", 0.15, "alpha", 0.95), glow, core);
        }
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 크게 벌어지며 터짐
            for (Rift g2 : segs) {
                RuneKit.rfx2(level, "rift", g2.at(), Opt.of("dir", g2.dir(), "size", g2.size() * 1.1, "life", 0.3, "grow", 0.6, "fade", 0.8, "alpha", 1.0),
                        RuneKit.WHITE, core);
            }
            // 터지는 빛 · 충격 고리: 내 몸 2.5칸 안은 빼고 (돌아서서 본 1인칭 화면이 보랏빛으로 덮이지 않게)
            Vec3 pc = sp.position();
            Predicate<Vec3> far = q -> Math.hypot(q.x - pc.x, q.z - pc.z) >= 2.5;
            for (double s = 0.6; s <= len; s += 1.2) if (far.test(V.add(start, f, s))) SkillFx.burst(level, V.up(V.add(start, f, s), 0.7), glow, 1.2, 0.3);
            for (double s = 0; s <= len; s += 2.4) if (far.test(V.add(start, f, s))) SkillFx.shock(level, V.add(start, f, s), glow, 0.9, 0.35);
            if (far.test(V.add(start, f, len / 2))) SkillFx.sparks(level, V.up(V.add(start, f, len / 2), 0.8), core, 10, 6);
            SkillFx.play(level, "random.explode", V.up(V.add(start, f, len / 2), 0.5), 1.6, 0.5);
            SkillFx.play(level, "chime.amethyst_block", start, 0.8, 1);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, width + 0.4)) {
                if (RuneKit.spellHit(sp, h.e(), P, k.num("mult")) > 0) RuneKit.knock(h.e(), f, 0.1, 0.35);
            }
        });
        return true;
    }

    /**
     * 마검 결계: hits × gap 틱 동안 마력 검 swords 자루가 몸 둘레 orbit 칸을 돌고 (나를 따라옴),
     * gap 틱마다 주위 radius 칸의 적을 벰 — 짝수 번째 검 (공격 피해) · 홀수 번째 마력 (마법 피해).
     * 도는 검 = 빛의 검 그림 (rpg:rfx_sword — 남보라 빛 · 청록 심지, 칼날이 도는 쪽으로 누움). 1인칭 화면 앞을 지날 때는 흐리게
     */
    static boolean orbitBlades(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("spellblade");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int total = hits * gap;
        int swords = (int) k.num("swords");
        double orbit = k.num("orbit");
        double radius = k.num("radius");
        RuneKit.selfCue(sp, glow, core, true);
        SkillFx.play(sp.level(), "mob.evocation_illager.prepare_attack", sp.position(), 1.3, 0.8);
        SkillFx.play(sp.level(), "beacon.activate", sp.position(), 1.6, 0.6);
        // 도는 검 (3틱마다 다시 그림, 한 틱에 0.16 라디안 — 약 2초에 한 바퀴)
        for (int t = 0; t < total; t += 3) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = V.up(sp.position(), 1.05);
                Vec3 fc = Kit.facingOf(sp);
                Vec3 vel = RuneKit.bodyVel(sp);
                for (int i = 0; i < swords; i++) {
                    double a = tt * 0.16 + ((double) i / swords) * Math.PI * 2;
                    Vec3 rd = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 at = V.add(c, rd, orbit);
                    Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    boolean ahead = V.dot(rd, fc) > 0.45;
                    RuneKit.rfx2(level, "sword", at, Opt.of("dir", tan, "size", 0.72, "life", 0.17, "alpha", ahead ? 0.45 : 0.95, "vel", vel), glow, core);
                }
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 eye = sp.getEyePosition();
            boolean magic = i % 2 == 1;
            RuneKit.zonePulse(level, c, magic ? glow : core, radius, 0.35);
            int n = 0;
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                double d = magic ? RuneKit.spellHit(sp, e, P, k.num("mult")) : RuneKit.swordHit(sp, e, P, k.num("mult"));
                if (d <= 0) continue;
                Vec3 at = Kit.bodyPoint(e);
                // 맞은 자리 그림 (파티클 예산): 마력 = 6명까지 번개 구슬 + 번쩍임, 12명까지 번쩍임 · 검 = 10명까지 베인 자국
                if (magic) {
                    if (n < 12) SkillFx.burst(level, at, glow, 0.8, 0.25);
                    if (n < 6) RuneKit.plasmaOrb(level, at, 0.45, glow, Opt.of("life", 0.22));
                } else if (n < 10) {
                    SkillFx.cutMark(level, at, V.unit(V.sub(at, eye)), ((i * 47) % 180) - 90, 0.08, core, RuneKit.WHITE, 0.9);
                }
                n++;
            }
            SkillFx.play(level, magic ? "chime.amethyst_block" : "item.spear.attack_miss", c, magic ? 1.5 : 1.2 + (i % 3) * 0.1, 0.8);
        });
        return true;
    }

    /**
     * 마검 해방: charge 틱 동안 눈앞에 거대한 마력 검 (rpg:rfx_sword — 남보라 빛 · 청록 심지) 이 솟아오른 뒤 — 가로 베기 (거대한 검기, 앞 cone 도 · range 칸, 검, 공격 피해)
     * → gap 틱 뒤 그 검이 앞으로 쓰러지며 내려찍음 + 십자 폭발 (앞 length 칸 (벽 앞까지) 폭 width 칸 + 그 한가운데 가로 cross 칸씩 폭 1.2칸, 마력, 마법 피해 — 적마다 한 번).
     * 초월 2차 강화: 남보라 하늘 · 발밑의 거대한 비전 문양 · 앞쪽 하늘의 비전 문양 (거꾸로 돎) → 가로 베기 때 섬광 → 십자 폭발 때 번쩍임 · 빛기둥 · 고리
     */
    static boolean spellbladeRelease(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("spellblade");
        double[] glow = cs[0];
        double[] core = cs[1];
        int charge = (int) k.num("charge");
        int gap = (int) k.num("gap");
        ServerLevel level0 = sp.level();
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", sp.position(), 1.0, 1);
        SkillFx.play(level0, "beacon.power", sp.position(), 1.2, 0.8);
        SkillFx.rune(level0, sp.position(), Opt.of("size", 2.2, "life", charge / 20.0 + 0.3, "color", glow, "alpha", 0.9, "rate", 160.0, "progress", 1.0, "grow", 0.2));
        String life = RuneKit.lifeToken(sp);
        // 2차 강화 — 예고: 남보라 하늘 · 발밑의 거대한 비전 문양 · 앞쪽 하늘의 비전 문양 (거꾸로 돎)
        Vec3 c0 = sp.position();
        RuneKit.ultSkyFor(sp, level0, c0, "spellbladeRelease", charge + gap + 36, null);
        RuneKit.domainSigil(level0, c0, 5.6, "arcane", glow, core, Opt.of("life", (charge + gap + 2) / 20.0, "spinRate", 50.0, "grow", 0.25, "alpha", 0.82,
                "fade", 0.15));
        Vec3 skyAt = RuneKit.skyPoint(sp, c0, 12, 21, 6, 22);
        RuneKit.skyPlate(sp, level0, "sigil", skyAt, 5.6, glow, core, Opt.of("cell", (double) RuneKit.SIGIL.get("arcane"), "life", (charge + gap + 8) / 20.0,
                "spinRate", -30.0, "grow", 0.15, "alpha", 0.78, "fade", 0.3));
        // 솟아오르는 검 (눈앞 2.6칸, 칼끝 위 — 1인칭에서 보이게) + 빨려 드는 빛
        for (int t = 0; t < charge; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 f = V.flat(sp.getViewVector(1F));
                double q = (tt + 2.0) / charge;
                Vec3 hilt = V.up(V.add(sp.position(), f, 2.6), 0.4);
                double ls = 1.5 + q * 4.5;
                RuneKit.rfx2(level, "sword", V.up(hilt, ls / 2), Opt.of("dir", V.UP, "size", ls / 2, "life", 0.13), glow, core);
                for (int sd : new int[] {-1, 1}) {
                    Vec3 a = V.add(V.up(hilt, 1 + q * 2.5), V.rightOf(f), sd * (2.6 - q * 1.6));
                    SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.scale(V.rightOf(f), -sd), "speed", 3.0, "size", 0.16, "grow", 0.5, "life", 0.25,
                            "color", core, "alpha", 1.0));
                }
                if (tt % 6 == 0) SkillFx.play(level, "block.bell.hit", hilt, 1.0 + q, 0.35);
            });
        }
        // 가로 베기 (검): 거대한 검기 + 화면을 가로지르는 빛
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            RuneKit.waveCard(level, V.up(V.add(eye, f, 2.8), -0.4), f, V.UP, Opt.of("size", 3.3, "life", 0.3, "grow", 0.25, "fade", 0.5), glow, core);
            SkillFx.fx(level, "rpg:light_cut", V.up(V.add(eye, f, 3.2), -0.4), Opt.of("dir", V.rightOf(f), "size", 4.8, "thick", 0.3, "life", 0.4, "delay", 0.05,
                    "color", glow, "alpha", 1.0));
            SkillFx.play(level, "item.trident.thunder", eye, 1.4, 0.8);
            SkillFx.play(level, "item.spear.attack_miss", eye, 0.9, 1);
            RuneKit.shakeAround(level, sp.position(), 10, 0.2, 0.25);
            RuneKit.skyFlare(level, skyAt, 3.4, glow, core, Opt.of("life", 0.45));
            for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 16)) {
                if (RuneKit.swordHit(sp, e, P, k.num("mult")) <= 0) continue;
                Vec3 at = Kit.bodyPoint(e);
                // 적이 많으면 앞 8명만 (파티클 예산)
                if (RuneKit.fxQuota("spellbladeCut", 8)) SkillFx.cutMark(level, at, V.unit(V.sub(at, eye)), -4, 0.12, core, RuneKit.WHITE, 1.3);
                if (RuneKit.fxQuota("spellbladeSpark", 4)) SkillFx.sparks(level, at, core, 6, 5);
                RuneKit.knock(e, V.sub(e.position(), sp.position()), 0.3, 0.15);
            }
        });
        // 세로 내려찍기 → 십자 폭발 (마력)
        RuneKit.later(charge + gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = V.flat(sp.getViewVector(1F));
            Vec3 side = V.rightOf(f);
            Vec3 start = sp.position();
            Vec3 from = V.up(start, 1.0);
            double length = k.num("length");
            double cross = k.num("cross");
            double len = Math.max(1, RuneKit.rayLength(level, from, f, length));
            // 앞으로 쓰러지며 내려찍는 거대한 검 (세운 검 → 앞으로 눕힘, 4장) + 세로 참격
            Vec3 hilt = V.up(V.add(start, f, 1.8), 0.5); // 손잡이가 눈앞 1.8칸 (가까운 칼밑이 1인칭 화면을 덮지 않게)
            double[] tilt = {25, 55, 80};
            for (int i = 0; i < tilt.length; i++) {
                double deg = tilt[i];
                RuneKit.later(i, () -> {
                    double a = deg * Math.PI / 180;
                    Vec3 dir = V.unit(V.add(V.scale(V.UP, Math.cos(a)), f, Math.sin(a)));
                    RuneKit.rfx2(level, "sword", V.add(hilt, dir, len / 2), Opt.of("dir", dir, "size", len / 2, "life", 0.08), glow, core);
                });
            }
            RuneKit.later(3, () -> RuneKit.rfx2(level, "sword", V.add(hilt, f, len / 2), Opt.of("dir", f, "size", len / 2, "life", 0.55, "fade", 0.5), glow, core));
            SkillFx.fx(level, "rpg:light_cut", V.up(V.add(start, f, 3), 2.2), Opt.of("dir", V.UP, "size", 2.6, "thick", 0.35, "life", 0.3, "color", core, "alpha", 1.0));
            Vec3 mid = V.add(start, f, Math.min(len, length) * 0.55);
            double wk = k.num("width") / 2.4; // 폭을 따라 (기본 2.4칸 — 8단계 6돌은 넓음)
            for (double s = 1; s <= len; s += 1.5) {
                double ss = s;
                RuneKit.later(3 + Math.round(ss / 3), () -> {
                    SkillFx.burst(level, V.up(V.add(start, f, ss), 0.7), glow, 1.3 * wk, 0.35);
                    SkillFx.shock(level, V.add(start, f, ss), ss % 3 < 1.5 ? glow : core, 0.9 * wk, 0.4);
                });
            }
            RuneKit.later(3, () -> {
                for (int sd : new int[] {-1, 1}) for (double s = 1.2; s <= cross; s += 1.2) SkillFx.burst(level, V.up(V.add(mid, side, sd * s), 0.7), glow, 1.2, 0.35);
                SkillFx.fx(level, "rpg:magic_circle_flash", V.up(mid, 0.15), Opt.of("size", 2.4, "life", 0.45, "spin", Kit.rand() * 360, "progress", 1.0, "color", glow,
                        "alpha", 1.0));
                RuneKit.zonePulse(level, mid, glow, cross + 1, 0.5);
                SkillFx.sparks(level, V.up(mid, 1), core, 14, 7);
            });
            SkillFx.play(level, "random.explode", mid, 1.2, 0.8);
            SkillFx.play(level, "mace.heavy_smash_ground", mid, 0.8, 1);
            SkillFx.play(level, "chime.amethyst_block", mid, 0.7, 1);
            RuneKit.shakeAround(level, mid, 14, 0.35, 0.35);
            // 발동 (십자 폭발): 남보라 번쩍임 · 십자 끝 넷과 가운데의 빛기둥 (5틱 뒤 — 폭발이 퍼진 뒤) · 가운데에서 퍼지는 고리 (4틱 뒤)
            RuneKit.ultFlashFor(sp, level, mid, "spellbladeRelease", "flash", Opt.empty());
            RuneKit.later(4, () -> RuneKit.novaRing(level, mid, 1.8, 3.4, glow, core, Opt.of("life", 0.5)));
            RuneKit.later(5, () -> {
                double span = Math.min(len, length) * 0.4;
                for (Vec3 p : new Vec3[] {mid, V.add(mid, side, cross), V.add(mid, side, -cross), V.add(mid, f, span), V.add(mid, f, -span)}) {
                    RuneKit.godRays(sp, level, p, 0, 1, glow, core, Opt.of("h", 14.0, "life", 0.7, "skipNear", true));
                }
            });
            List<Kit.LineHit> crossHits = new ArrayList<>(RuneKit.lineTargets(sp, from, f, len, k.num("width")));
            crossHits.addAll(RuneKit.lineTargets(sp, V.add(V.up(mid, 1.0), side, -cross), side, cross * 2, 1.2));
            Set<UUID> hit = new HashSet<>();
            for (Kit.LineHit h : crossHits) {
                LivingEntity e = h.e();
                if (!hit.add(e.getUUID())) continue;
                if (RuneKit.spellHit(sp, e, P, k.num("mult")) > 0) {
                    RuneKit.knock(e, V.UP, 0, 0.45);
                    if (k.numOr("stun", 0) > 0) RuneKit.stun(e, (int) k.num("stun"), glow, RuneKit.fxQuota("releaseStun", 8)); // 8단계 6돌: 기절
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 마검 부여 (RUNE_SPELLBLADE): 마검사 룬을 쓰면 다음 평타 몇 번에 마력 검기

    /** 마검 부여 하나: 남은 평타 · 끝나는 틱 · 평타마다 더할 피해 · 룬의 imbue · 룬 이름 · 삶 표시 */
    static final class Imbue {
        int left;
        final double until;
        final double amount;
        final double mult;
        final String name;
        final String token;

        Imbue(int left, double until, double amount, double mult, String name, String token) {
            this.left = left;
            this.until = until;
            this.amount = amount;
            this.mult = mult;
            this.name = name;
            this.token = token;
        }
    }

    /** 플레이어 → 마검 부여 */
    private static final Map<UUID, Imbue> IMBUES = new HashMap<>();

    /** 지금 마검 부여 (끝났거나 다 썼거나 그 사이 죽었으면 null) */
    static Imbue imbueOf(ServerPlayer sp) {
        Imbue st = IMBUES.get(sp.getUUID());
        if (st == null) return null;
        if (Tasks.now() >= st.until || st.left <= 0 || !st.token.equals(RuneKit.lifeToken(sp))) return null;
        return st;
    }

    /** 마검사 룬을 씀 (RuneCast 의 쓴 뒤 할 일): 마검 부여 (마지막에 쓴 룬의 값으로 새로, 횟수 · 시간 처음부터 — 시간은 룬이 끝난 뒤 4초까지) */
    static void grantImbue(ServerPlayer sp, Runes.Rune r, double P) {
        double mult = r.def().numOr("imbue", 0);
        if (!(mult > 0) || !(P > 0)) return;
        // 룬의 마지막 타격 (span 틱) 뒤 ticks 동안 (룬 타격 사이에는 몹 무적 시간에 막히므로: 마검 결계 4초 · 마검 해방 1.3초 등). 그 사이에도 다른 적에게는 쓸 수 있음
        // 8단계 3돌: 마검 해방은 부여 평타 + imbueHits 번 (r = 돌파 단계의 룬)
        IMBUES.put(sp.getUUID(), new Imbue((int) IMBUE.num("hits") + (int) r.def().numOr("imbueHits", 0), Tasks.now() + r.def().numOr("span", 0) + IMBUE.num("ticks"),
                P * mult, mult,
                r.def().str("name"), RuneKit.lifeToken(sp)));
        double[][] cs = RuneKit.colorOf("spellblade");
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.ring(level, h.hand(), h.f(), cs[0], 0.5, 0.3, 0); // 손 앞 작은 고리 (1인칭 화면을 가리지 않게)
        SkillFx.sparks(level, h.hand(), cs[1], 6, 3);
        // 무기를 든 오른손 쪽 아래 (가운데의 적 · 피해 숫자를 가리지 않게)
        DamageNumbers.showFace(sp, "§9§l마검 부여 §r§f×" + IMBUE.text("hits"), 3.0, 1.2, -1.0);
        SkillFx.play(level, "item.trident.return", sp.position(), 1.4, 0.7);
    }

    /** 평타에 더할 피해 (RpgCombat 이 평타 · 활 · 완드 피해를 넣기 직전에 부름 — 세계를 바꾸지 않음) */
    static RpgCombat.Bonus imbueBonus(ServerPlayer sp, LivingEntity victim, Weapon w) {
        Imbue st = imbueOf(sp);
        if (st == null || !Kit.canHit(sp, victim)) return null;
        return new RpgCombat.Bonus(st.amount, SPELLBLADE.str("mark"), FAMILY, null, null);
    }

    /** 마검 부여가 더해진 평타가 들어감 (피해가 들어간 뒤): 한 번 쓰고, 마력 검기가 맞은 적 너머로 날아가 다른 적을 꿰뚫음 */
    static void imbueLanded(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        RpgCombat.Bonus part = null;
        if (bonus != null && bonus.parts() != null) {
            for (RpgCombat.Bonus x : bonus.parts()) {
                if (FAMILY.equals(x.key())) {
                    part = x;
                    break;
                }
            }
        }
        if (part == null) return;
        Imbue st = IMBUES.get(sp.getUUID());
        if (st != null) {
            st.left -= 1;
            if (st.left <= 0) IMBUES.remove(sp.getUUID());
        }
        arcaneWave(sp, victim, part.add());
        int left = st != null ? Math.max(0, st.left) : 0;
        Notify.notify(sp, left > 0 ? imbueGems(left) + " §7마검 부여 남은 평타 " + left + "번" : "§7마검 부여를 모두 썼습니다", 16);
    }

    /** 남은 횟수 보석 (◆ 남음 · ◇ 씀) */
    private static String imbueGems(int left) {
        int n = (int) IMBUE.num("hits");
        return "§9" + "◆".repeat(Math.max(0, Math.min(n, left))) + "§8" + "◇".repeat(Math.max(0, n - left));
    }

    /**
     * 마력 검기 (마검 부여): 맞은 적 앞 lead 칸 (가까우면 내 눈에서 1.5칸, 그 사이 벽이면 벽 앞) 에서 그 너머 pierce 칸 (벽 앞까지) 까지 초승달 검기가 speed 칸/초로,
     * 폭 width 칸 안의 다른 적에게 같은 피해 (마력, 마법 피해, 적마다 한 번 — 맞은 적은 평타에 이미 들어감)
     */
    private static void arcaneWave(ServerPlayer sp, LivingEntity victim, double amount) {
        double[][] cs = RuneKit.colorOf("spellblade");
        double[] glow = cs[0];
        double[] core = cs[1];
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        // 맞은 적의 몸에서 내 눈높이에 가장 가까운 점 (보통 몹은 몸 가운데, 키 큰 몹 · 보스는 눈높이 — 몸 높은 곳에서 하늘로 날아가지 않게)
        Vec3 vp = victim != null && !victim.isRemoved() ? Kit.bodyAt(victim, eye.y) : V.add(eye, sp.getViewVector(1F), 3);
        Vec3 d0 = V.sub(vp, eye);
        // 거의 같은 높이 (근접 등): 맞은 적의 몸 높이로 수평으로 날아감 (눈높이에서 비스듬히 내려가 땅에 박히지 않게)
        double flatDist = Math.hypot(d0.x, d0.z);
        if (Math.abs(d0.y) < flatDist * 0.6) d0 = new Vec3(d0.x, 0, d0.z);
        double dist = V.len(d0);
        Vec3 dir = dist > 1e-6 ? V.scale(d0, 1 / dist) : V.flat(sp.getViewVector(1F));
        // 나타나는 자리: 맞은 적 앞 lead 칸, 단 내 눈에서 1.5칸보다 가깝지 않게 (1인칭 화면을 덮지 않게 — 아주 가까운 적은 그 너머에서),
        // 그 사이에 벽 · 언덕이 있으면 그 앞
        double back = Math.min(IMBUE.num("lead"), dist - 1.5);
        if (back > 0) back = Math.min(back, RuneKit.rayLength(level, vp, V.scale(dir, -1), back));
        Vec3 start = V.add(vp, dir, -back);
        double len = back + RuneKit.rayLength(level, vp, dir, IMBUE.num("pierce")); // 맞은 적 너머 pierce 칸 (벽 앞까지)
        if (len < 0.3) return; // 아주 가까운 적 바로 뒤가 벽
        Vec3 end = V.add(start, dir, len);
        double speed = IMBUE.num("speed");
        int ticks = Math.max(1, (int) Math.round(len / speed * 20));
        for (int t = 0; t <= ticks; t += 2) {
            Vec3 at = V.add(start, dir, Math.min(len, t / 20.0 * speed));
            RuneKit.later(t, () -> RuneKit.arc(level, at, dir, Opt.of("shape", "heavy", "flip", false, "size", 1.25, "life", 0.14, "speed", 0.0, "spin", -90.0,
                    "color", glow, "aura", core)));
        }
        RuneKit.flyStreak(level, start, end, speed, glow, core, 0.6, 0.18);
        if (victim != null && !victim.isRemoved()) {
            SkillFx.cutMark(level, vp, dir, 60, 0.06, glow, core, 0.9);
            SkillFx.sparks(level, vp, core, 5, 4);
        }
        SkillFx.play(level, "mob.evocation_illager.cast_spell", start, 1.8, 0.35);
        String life = RuneKit.lifeToken(sp);
        for (Kit.LineHit h : RuneKit.lineTargets(sp, start, dir, len, IMBUE.num("width"))) {
            LivingEntity e = h.e();
            if (victim != null && e.getUUID().equals(victim.getUUID())) continue;
            RuneKit.later(Math.max(1, Math.round(h.t() / speed * 20)), () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                if (RuneKit.runeHit(sp, e, amount, 1, "int", Kit.Cause.MAGIC, DamageNumbers.Style.SPELL) <= 0) return;
                Vec3 at = Kit.bodyPoint(e);
                SkillFx.cutMark(level, at, dir, 60, 0.06, glow, core, 0.8);
                SkillFx.burst(level, at, glow, 0.6, 0.2);
            });
        }
    }

    // 상태 줄을 초월 무기 스킬 줄이 쓰는지 (heldSkillWeapon) 는 RuneCast.hudTick 이 봄 (그 줄 뒤에는 Transcend 가 덧붙임을 붙임)

    /** 화면 하단 상태 줄의 마검 부여 (룬 · 초월 무기 스킬 줄 뒤에 붙음) */
    static String imbueHud(ServerPlayer sp) {
        Imbue st = imbueOf(sp);
        return st != null ? imbueGems(st.left) + " §9마검 §7" + Js.toFixed(Math.max(0, (st.until - Tasks.now()) / 20), 1) + "초" : "";
    }

    /** 마검 부여가 있는 동안: 손 둘레 남보라 · 청록 빛 (4틱마다, 다른 사람에게도 보임). 끝나면 흩어지며 사라짐 */
    private static void imbueTick(MinecraftServer server) {
        long now = Tasks.now();
        if (IMBUES.isEmpty()) return;
        double[][] cs = RuneKit.colorOf("spellblade");
        double[] glow = cs[0];
        double[] core = cs[1];
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Imbue st = IMBUES.get(sp.getUUID());
            if (st == null) continue;
            ServerLevel level = sp.level();
            if (imbueOf(sp) == null) {
                IMBUES.remove(sp.getUUID());
                if (now >= st.until && st.left > 0 && st.token.equals(RuneKit.lifeToken(sp))) {
                    SkillFx.burst(level, RuneKit.handOf(sp).hand(), glow, 0.5, 0.25);
                    // 상태 줄의 마지막 "◆◆◇ 마검 0.2초" 가 화면에 남아 아직 있는 것처럼 보이지 않게
                    if (Notify.actionbarFree(sp)) Notify.notify(sp, "§7마검 부여가 끝났습니다", 16);
                }
                continue;
            }
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 side = V.rightOf(h.f());
            for (int i = 0; i < 2; i++) {
                double a = now * 0.5 + i * Math.PI;
                SkillFx.fx(level, "rpg:arrow_spark", V.add(V.up(h.hand(), Math.sin(a) * 0.18), side, Math.cos(a) * 0.22), Opt.of("dir", V.UP, "speed", 0.8,
                        "size", 0.09, "life", 0.35, "color", i == 1 ? core : glow, "alpha", 1.0));
            }
        }
    }
}
