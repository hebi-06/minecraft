package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.BowFx;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2차 직업 — 궁수 각성 스나이퍼 스킬, 2026-11-14 천벌의 사수 (사용자가 고른 컨셉 — jobs.json advance.sniper: 백금빛 · 하늘빛, 특성 「심판」 최대 8 · 천벌의 화살 —
 * JobTraitsMore · JobTraits9): 저지먼트 (끝까지 당겨 큰 한 발 — 다시 눌러 놓음 · 맞은 자리에 하늘의 빛기둥) · 윙 스텝 (빛의 고리 화살 · 빛의 날개로 뒤로 날아 물러남 ·
 * 심판 +3) · 헤븐즈 게이트 (빛의 화살 → 둘레 적 위 하늘의 문에서 하늘의 화살 두 번 — 치명) · 세라핌 (태세 — 빛의 날개 여섯 · 세 번째 화살마다 심판의 화살) ·
 * 디바인 펀니시먼트 (조준 — 하늘의 문 → 다시 눌러 단 한 발: 거대한 심판의 화살이 내리꽂힘). 피해 · 재사용 대기 · 틱 · 매커니즘은 2판 그대로 (그림 · 이름 · 설명만).
 * 화살은 공격 피해. 화면 흔들림 없음. 연출 = 모드 없는 클라이언트에도 보이는 것 (3D 빛 화살 · 3D 날개 · 3D 빛 고리 · 룬 빛기둥 · 문양 · 반짝임)
 */
final class SniperSkills {
    private SniperSkills() {}

    /** 흰 섬광 (급소 · 큰 화살의 심지) */
    private static final double[] FLASH = JobTraits9.SNIPE;
    /** 금빛 (하늘의 화살 · 날개 깃털) */
    private static final double[] GOLD = {1.0, 0.84, 0.36};
    private static final double[] FEATHER = {1.0, 0.86, 0.5};
    private static final double[] FEATHER_GLOW = {1.0, 0.97, 0.86};
    /** 하늘의 문양 (holy) 칸 */
    private static final double HOLY = RuneKit.SIGIL.get("holy");

    static void register() {
        JobSystem.register("sniper_heart", SniperSkills::heart);
        JobSystem.register("sniper_snare", SniperSkills::snare);
        JobSystem.register("sniper_trick", SniperSkills::trick);
        JobSystem.register("sniper_mark", SniperSkills::mark);
        JobSystem.register("sniper_ult", SniperSkills::ult);
    }

    private static boolean alive(ServerPlayer sp, String life, LivingEntity e) {
        return RuneKit.sameLife(sp, life) && e != null && !e.isRemoved() && e.isAlive();
    }

    /** 보이는 빠르기: 가까운 적이면 화살이 적어도 2틱 날아가게 (len 칸을 speed 칸/초 — 1틱이면 날아가는 것이 보이지 않음) */
    private static double visSpeed(double speed, double len) {
        return Math.min(speed, Math.max(25, len / 0.1));
    }

    /** 보는 쪽 일직선에서 처음 맞는 적 (없으면 null) */
    private static Kit.LineHit firstOnLine(ServerPlayer sp, Vec3 from, Vec3 dir, double len, double half) {
        Kit.LineHit best = null;
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, dir, len, half)) if (best == null || h.t() < best.t()) best = h;
        return best;
    }

    /** 하늘의 빛기둥 (룬 빛기둥 — 밑동 g · 키 h): 시전자 가까이면 건너뜀 (1인칭 화면을 덮지 않게) */
    private static void pillar(ServerPlayer sp, ServerLevel level, Vec3 g, double h, double life, double[] glow, double[] core) {
        RuneKit.godRays(sp, level, g, 0, 1, glow, core, Opt.of("h", h, "life", life, "skipNear", true));
    }

    // ------------------------------------------------------------ 저지먼트

    /** 당기는 중 (시작 틱 · 목숨 · 끝남 · 3D 빛 화살) */
    private record Draw(long start, String life, boolean[] done, Vfx.Fx fx, RuneSwords.Blade arrow) {}

    private static final Map<UUID, Draw> DRAWS = new HashMap<>();

    /**
     * 저지먼트 (sniper_heart): 활을 끝까지 당김 (charge 틱 — 느려짐 · 밀려나지 않음 · 활에 걸린 3D 빛 화살이 당긴 만큼 길어지며 뒤로 당겨짐 · 양옆에서 빛이 모여 듦 · 화살촉 앞
     * 빛의 고리가 조여 듦 · 발밑 성광 문양) → gap 틱 뒤부터 다시 누르면 놓음 (안 누르면 charge 틱에 저절로 — 끝까지 당기면 번쩍). 놓으면 당긴 몫 c 만큼 멀리 (rangeMin →
     * rangeMax 칸) · 세게 (배율 합 × (minPower → 1)) 날아가 일직선 (폭 width) 에서 처음 맞는 적에게 큰 한 발 (맞은 자리에 하늘의 빛기둥) — 심판 하나당 + breathPer
     * (심판 breathUse 씀)
     */
    static boolean heart(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int charge = Math.max(8, (int) k.num("charge", 20));
        if (JobRecast.stage(sp, k.id()) >= 1) {
            Draw d = DRAWS.get(sp.getUUID());
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":release");
            int held = d == null ? charge : (int) Math.min(charge, JobKit.now() - d.start());
            loose(sp, j, k, P, held, charge);
            return true;
        }
        String life = RuneKit.lifeToken(sp);
        boolean[] done = {false};
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 look0 = sp.getViewVector(1F);
        Vfx.Fx afx = RuneProps.fx(level0, sp.position(), charge + 60);
        RuneSwords.Blade arrow = RuneSwords.arrow(afx, V.add(RuneKit.bowHand(sp), look0, 0.1), look0, V.rightOf(V.flat(look0)), 2.4, 0.3, glow, FLASH);
        DRAWS.put(sp.getUUID(), new Draw(JobKit.now(), life, done, afx, arrow));
        RuneKit.effect(sp, "slowness", charge + 2, 1);
        RpgCombat.setSteadfast(sp, charge + 8);
        RuneKit.domainSigil(level0, sp.position(), 1.8, "holy", glow, core, Opt.of("life", charge / 20.0 + 0.3, "spinRate", 50.0, "grow", 0.1, "alpha", 0.75));
        SkillFx.play(level0, "crossbow.loading.start", sp.position(), 0.9, 0.9);
        for (int t = 1; t <= charge; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (done[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                double q = Math.min(1, tt / (double) charge);
                Vec3 look = sp.getViewVector(1F);
                Vec3 hand = RuneKit.bowHand(sp);
                if (tt % 2 == 0) {
                    // 시위에 걸린 3D 빛 화살: 당길수록 길어지고 뒤로 당겨짐 · 촉의 반짝임
                    arrow.grow(0.4 + 0.6 * q, 2);
                    arrow.pose(V.add(hand, look, 0.1 - 0.4 * q), look, V.rightOf(V.flat(look)), 2);
                    ArrowFx.glint(level, arrow.tip(), 0.14 + 0.2 * q, 0.13, FLASH, Opt.of("grow", 0.0, "fade", 0.2));
                    // 양옆 · 위에서 빛이 화살촉으로 빨려 듦
                    Vec3 tip = arrow.tip();
                    for (int sd : new int[] {-1, 1}) {
                        Vec3 a = V.add(V.add(tip, V.rightOf(V.flat(look)), sd * (1.6 - q)), V.UP, 0.9 - q * 0.6);
                        SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.unit(V.sub(tip, a)), "speed", 4.0, "size", 0.15, "grow", 0.5, "life", 0.22, "color", core,
                                "alpha", 1.0));
                    }
                }
                if (tt % 5 == 0 || tt == charge) {
                    // 화살촉 앞 빛의 고리가 조여 듦 (다 당기면 맞춰짐) · 차오르는 소리
                    Vec3 tip = V.add(hand, look, 2.6 + q * 0.6);
                    SkillFx.ring(level, tip, look, tt == charge ? FLASH : glow, Math.max(0.3, 1.3 - q * 0.9), 0.2, 0);
                    SkillFx.play(level, "crossbow.loading.middle", sp.position(), 0.7 + q * 0.6, 0.6);
                }
                if (tt == charge) {
                    ArrowFx.glint(level, arrow.tip(), 0.9, 0.35, RuneKit.WHITE, Opt.of("vel", RuneKit.bodyVel(sp)));
                    RuneKit.zonePulse(level, sp.position(), glow, 2.0, 0.35);
                    SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.8, 0.9);
                }
            });
        }
        JobRecast.open(sp, k, 1, charge, (int) k.num("gap", 6), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobKit.motion(sp, k.id() + ":release");
            loose(sp, j, k, P, charge, charge);
        });
        JobSystem.note(sp, "§e다시 누르면 놓음 §7(끝까지 당기면 " + charge / 20.0 + "초 · " + (int) k.num("rangeMax", 40) + "칸)");
        return true;
    }

    /** 놓음 (held 틱 당김): 3D 빛 화살 · 흰 섬광 줄 · 금빛 줄 · 지나가는 빛 고리 → 처음 맞는 적에게 큰 한 발 (하늘의 빛기둥 · X 섬광 · 꽂힌 화살) */
    private static void loose(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, int held, int charge) {
        Draw dr = DRAWS.remove(sp.getUUID());
        if (dr != null) dr.done()[0] = true;
        sp.removeEffect(MobEffects.SLOWNESS);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        double c = Math.max(0, Math.min(1, held / (double) charge));
        boolean full = held >= charge;
        double min = k.num("minPower", 0.5);
        double frac = min + (1 - min) * c;
        double range = k.num("rangeMin", 16) + (k.num("rangeMax", 40) - k.num("rangeMin", 16)) * c;
        int breath = JobTraitsMore.breath(sp);
        double boost = 1 + k.num("breathPer", 0.06) * breath;
        JobTraitsMore.useBreath(sp, (int) k.num("breathUse", 3));
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1F);
        double len = Math.max(2, RuneKit.rayLength(level, eye, look, range));
        Kit.LineHit first = firstOnLine(sp, eye, look, len, k.num("width", 1.2) / 2);
        double reach = first == null ? len : Math.max(1, first.t());
        Vec3 from = V.add(RuneKit.bowHand(sp), look, 0.7);
        Vec3 end = V.add(eye, look, reach);
        // 보이는 빠르기: 가까운 적이면 화살이 2틱은 보이게, 맞는 틱도 그 빠르기
        double speed = visSpeed(k.num("speed", 80), reach);
        int hitT = first == null ? Math.max(1, (int) Math.round(reach / speed * 20)) : Math.max(1, (int) Math.round(first.t() / speed * 20));
        // 시위에 걸렸던 3D 빛 화살이 날아가 꽂힘 (없으면 새로)
        if (dr != null && dr.fx().age() < charge + 50) {
            RuneSwords.Blade a = dr.arrow();
            Vfx.Fx afx = dr.fx();
            a.grow(1, 1);
            double r = ArrowFx.reach(a.len);
            a.pose(V.sub(end, V.scale(look, r)), look, V.rightOf(V.flat(look)), Math.max(1, hitT - 1));
            int t0 = afx.age();
            LivingEntity e0 = first == null ? null : first.e();
            Vec3 off = e0 == null ? null : V.sub(V.sub(end, V.scale(look, r)), Kit.bodyPoint(e0));
            afx.each(t0 + hitT, t0 + hitT + 10, 1, tt -> {
                if (e0 == null || e0.isRemoved()) return;
                a.pose(V.add(Kit.bodyPoint(e0), off), look, V.rightOf(V.flat(look)), 1);
            });
            afx.on(t0 + hitT + 11, () -> a.grow(0.1, 4));
            afx.on(t0 + hitT + 15, a::remove);
        } else ArrowFx.arrow3d(level, from, end, hitT, 2.4, 10, first == null ? null : first.e(), glow, FLASH);
        RuneKit.flyStreak(level, from, end, speed, glow, FLASH, 1.0 + 0.5 * c, 0.3);
        RuneKit.streak(level, V.add(from, look, 0.6), end, FLASH, 0.1 + 0.08 * c, 0.4, 1.0, 0);
        RuneKit.streak(level, V.add(from, look, 0.6), end, glow, 0.26 + 0.16 * c, 0.35, 0.6, 0);
        SkillFx.burst(level, from, glow, 0.7 + 0.4 * c, 0.2);
        RuneKit.zonePulse(level, sp.position(), glow, 1.0 + c, 0.35);
        for (double dd : new double[] {2.5, 6, 11}) {
            if (dd > reach) break;
            Vec3 at = V.add(eye, look, dd);
            int delay = (int) Math.round(dd / speed * 20);
            double rr = 0.5 + dd * 0.03 + c * 0.3;
            RuneKit.later(delay, () -> SkillFx.ring(level, at, look, glow, rr, 0.3, 0));
        }
        SkillFx.play(level, "item.crossbow.shoot", from, 0.8 + 0.2 * c, 1.0);
        SkillFx.play(level, "item.trident.throw", from, 1.3, 0.7);
        if (full) SkillFx.play(level, "item.trident.thunder", from, 1.8, 0.35);
        if (first == null) return;
        LivingEntity e = first.e();
        JobKit.strike(sp, e, j, P, k.total() * frac * boost, false, full, life, hitT, x -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            Vec3 at = Kit.bodyPoint(e);
            // 하늘에서 심판의 빛기둥 · X 섬광 · 금빛 반짝임 · 발밑 성광
            pillar(sp, lv, e.position(), 12 + 4 * c, 0.5, glow, core);
            SkillFx.fx(lv, "rpg:slash_x", V.add(at, look, -0.3), Opt.of("dir", look, "size", 1.0 + 0.5 * c, "life", 0.3, "color", glow, "alpha", 1.0));
            ArrowFx.glint(lv, V.add(at, look, -0.4), 0.9 + 0.5 * c, 0.3, RuneKit.WHITE, Opt.empty());
            BowFx.impact(lv, e.position(), glow, core, 2.0 + c, "noble");
            JobImpact.hit(sp, e, j, 1.1 + 0.6 * c);
            SkillFx.sparks(lv, at, FLASH, 8 + (int) (8 * c), 4);
        });
    }

    // ------------------------------------------------------------ 윙 스텝

    /**
     * 윙 스텝 (sniper_snare): shotAt 틱 겨눈 적 (range + 심판 칸 — 없으면 조준한 곳) 으로 빛의 고리 화살 (활 평타와 같은 피해 × arrow — RpgCombat.extraArrow · 치명타 따로):
     * 3D 빛 화살이 닿은 자리에서 3D 빛 고리 셋이 펼쳐져 맞은 적과 둘레 net 칸 적을 옭아맴 (slow 틱 둔화 — 고리가 몸을 따라감). 같은 틱 등에 3D 빛의 날개가 펼쳐지며 한 번 크게
     * 내려쳐 뒤로 back 칸 (lift 칸 높이 · rise 틱) 날아 물러나고 내려앉으면 심판 + breath. 묶였으면 쓰지 않음
     */
    static boolean snare(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        double range = k.num("range", 20) + JobTraitsMore.breath(sp);
        LivingEntity target = Kit.pickTarget(sp, range, 12);
        String life = RuneKit.lifeToken(sp);
        int shotAt = (int) k.num("shotAt", 2);
        int rise = Math.max(4, (int) k.num("rise", 10));
        double netR = k.num("net", 2.0);
        int slow = (int) k.num("slow", 40);
        Vec3 start = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        // 등의 빛의 날개 (한 쌍 · 3.4칸): 쏘는 틱에 크게 내려쳐 날아오름 → 내려앉으면 접힘 (빠르게 움직이므로 틱마다 따라옴)
        LightWings wings = new LightWings(sp, 1, 3.4, shotAt + rise + 4, 3, 1, 0, FEATHER, FEATHER_GLOW);
        RuneKit.later(shotAt, () -> {
            if (RuneKit.sameLife(sp, life)) wings.beat();
        });
        RuneKit.later(shotAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 from = ArrowFx.bow(sp, 0.6);
            boolean on = alive(sp, life, target) && target.level() == level;
            Vec3 to = on ? Kit.bodyPoint(target) : Kit.aimPoint(sp, range);
            Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
            int t = on && w != null && "bow".equals(w.type()) ? RpgCombat.extraArrow(sp, target, w, JobUpgrades.num(sp, k, "arrow", 1)) : -1;
            double dist = V.len(V.sub(to, from));
            int fly = t > 0 ? t : Math.max(2, (int) Math.round(dist / 40 * 20));
            // 3D 빛 화살 · 빛줄 · 화살촉을 감싼 작은 빛 고리
            ArrowFx.arrow3d(level, from, to, fly, 1.6, 4, on ? target : null, glow, FLASH);
            RuneKit.flyStreak(level, from, to, dist / (fly / 20.0), glow, core, 0.9, 0.22);
            SkillFx.burst(level, from, glow, 0.6, 0.2);
            SkillFx.play(level, "item.crossbow.shoot", from, 1.3, 0.9);
            SkillFx.play(level, "block.amethyst_block.resonate", from, 1.6, 0.5);
            RuneKit.later(fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 at = on && !target.isRemoved() ? Kit.bodyPoint(target) : to;
                SkillFx.burst(level, at, core, 1.0, 0.25);
                RuneKit.zonePulse(level, on && !target.isRemoved() ? target.position() : V.up(at, -1.0), glow, netR, 0.4);
                SkillFx.play(level, "block.chain.place", at, 1.2, 1.0);
                SkillFx.play(level, "block.amethyst_block.chime", at, 1.4, 0.6);
                List<LivingEntity> caught = Kit.around(sp, at, netR, 3);
                if (on && !target.isRemoved() && !caught.contains(target)) caught.add(target);
                int n = 0;
                for (LivingEntity e : caught) {
                    RuneKit.effect(e, "slowness", slow, JobKit.isBoss(e) ? 1 : 2);
                    if (n++ < 4) bind(level, e, slow, glow);
                }
                if (caught.isEmpty()) SkillFx.ring(level, at, V.UP, glow, 1.4, 0.5, 0);
            });
        });
        // 날아 물러남 (낮은 도약) → 내려앉으면 심판 + breath
        JobMove.run(sp, JobMove.hop(shotAt, shotAt + rise, JobMove.ahead(sp, -k.num("back", 6), 0), k.num("lift", 1.2)));
        Kit.noFall(sp, shotAt + rise + 20);
        RuneKit.later(shotAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.zonePulse(level0, start, glow, 1.6, 0.4);
            for (int i = 0; i < 6; i++) ArrowFx.feather(level0, V.add(V.up(start, 1.2 + Kit.rand() * 0.8), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 1.4), 0.22, 1.0,
                    FEATHER);
            SkillFx.play(level0, "entity.breeze.jump", start, 1.2, 0.8);
            SkillFx.play(level0, "entity.phantom.flap", start, 1.4, 0.8);
        });
        RuneKit.later(shotAt + rise, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            JobTraitsMore.addBreath(sp, (int) k.num("breath", 3));
            SkillFx.ring(level, V.up(sp.position(), 0.1), V.UP, glow, 1.3, 0.4, 0);
            ArrowFx.glint(level, ArrowFx.bow(sp, 0.6), 0.32, 0.3, core, Opt.of("vel", RuneKit.bodyVel(sp)));
            for (int i = 0; i < 3; i++) ArrowFx.feather(level, V.add(V.up(sp.position(), 1.6), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 1.0), 0.2, 0.9, FEATHER);
            SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.6, 0.7);
        });
        return true;
    }

    /** 옭아매는 3D 빛 고리 셋 (발 · 허리 · 가슴 — 크게 나타나 조여 듦 · 비스듬히 돌며 몸을 따라감 · ticks 틱 뒤 작아지며 사라짐) */
    private static void bind(ServerLevel level, LivingEntity e, int ticks, double[] glow) {
        Vfx.Fx fx = RuneProps.fx(level, e.position(), ticks + 12);
        double w = Math.max(0.6, e.getBbWidth());
        double h = e.getBbHeight();
        double[] ys = {0.18, 0.5, 0.82};
        Vfx.Prop[] rings = new Vfx.Prop[3];
        double[] sc = new double[3];
        for (int i = 0; i < 3; i++) {
            sc[i] = RuneProps.scaleFor(w * (1.25 - 0.12 * i) + 0.4, RuneProps.HALO_D);
            Vec3 n = V.unit(new Vec3(Math.cos(i * 2.1) * 0.25, 1, Math.sin(i * 2.1) * 0.25));
            rings[i] = RuneProps.prop(fx, RuneProps.HALO, V.up(e.position(), h * ys[i]), new double[] {8, 8, 8}, RuneProps.along(n, new Vec3(1, 0, 0)), sc[i] * 2.4,
                    glow, RuneKit.WHITE);
        }
        fx.each(1, ticks, 1, t -> {
            if (e.isRemoved()) return;
            for (int i = 0; i < 3; i++) {
                double a = Math.toRadians(t * (i % 2 == 0 ? 9 : -7) + i * 60);
                Vec3 n = V.unit(new Vec3(Math.cos(a) * 0.28, 1, Math.sin(a) * 0.28));
                double k = t <= 3 ? 2.4 - 1.4 * t / 3.0 : 1.0;
                rings[i].to(V.up(e.position(), h * ys[i]), RuneProps.along(n, new Vec3(Math.cos(a), 0, Math.sin(a))), sc[i] * k, 1);
            }
        });
        fx.on(ticks, () -> {
            for (Vfx.Prop r : rings) r.size(0.01, 5);
        });
        fx.on(ticks + 6, () -> {
            for (Vfx.Prop r : rings) r.remove();
        });
    }

    // ------------------------------------------------------------ 헤븐즈 게이트

    /**
     * 헤븐즈 게이트 (sniper_trick): shotAt 틱 겨눈 적 (range + 심판 칸 — 없으면 쓰지 않음) 에게 빛의 화살 (first) → 맞은 적 둘레 bounceRange 칸의 아직 안 맞은 적 (쏜 쪽에서
     * 뒤쪽 먼저 · 가까운 차례 — bounceGap 틱) 위 하늘에 빛의 문 (성광 문양) 이 열려 하늘의 화살 (3D 금빛 화살) 이 내리꽂힘 (bounce — 치명 숫자, bounces 번). 다른 적이 없으면
     * 같은 적 위에서 다시 (sameGap 틱 — 몹 무적 시간)
     */
    static boolean trick(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 24) + JobTraitsMore.breath(sp), 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        int shotAt = (int) k.num("shotAt", 3);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        ArrowFx.glint(level0, ArrowFx.bow(sp, 0.5), 0.3, shotAt / 20.0 + 0.1, core, Opt.of("vel", RuneKit.bodyVel(sp)));
        SkillFx.play(level0, "crossbow.loading.end", sp.position(), 1.4, 0.7);
        RuneKit.later(shotAt, () -> {
            if (!alive(sp, life, target) || target.level() != sp.level()) return;
            ServerLevel level = sp.level();
            Vec3 from = ArrowFx.bow(sp, 0.6);
            Vec3 to = Kit.bodyPoint(target);
            int t = ArrowFx.shot(level, from, to, visSpeed(k.num("speed", 60), V.len(V.sub(to, from))), 1.6, 4, target, glow, FLASH);
            RuneKit.streak(level, from, to, glow, 0.14, 0.3, 0.75, 0);
            SkillFx.burst(level, from, glow, 0.5, 0.2);
            SkillFx.play(level, "random.bow", from, 1.2, 0.9);
            Map<UUID, Long> hitAt = new HashMap<>();
            hitAt.put(target.getUUID(), JobKit.now() + t);
            JobKit.strike(sp, target, j, P, k.num("first", 3.5), false, false, life, t, d -> {
                JobFx.hit(target, j, 1.0);
                if (target.level() instanceof ServerLevel lv) ArrowFx.glint(lv, Kit.bodyPoint(target), 0.8, 0.25, FLASH, Opt.empty());
            });
            Vec3 dir = V.flat(V.sub(target.position(), sp.position()));
            RuneKit.later(t, () -> bounce(sp, j, k, P, life, target, dir, Math.max(0, (int) k.num("bounces", 2)), hitAt));
        });
        return true;
    }

    /** 하늘의 문 하나: 다음 적 (아니면 같은 적) 위 하늘에 성광 문양이 열려 하늘의 화살이 내리꽂힘 (치명 숫자) → 남은 문 */
    private static void bounce(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life, LivingEntity from, Vec3 dir, int left, Map<UUID, Long> hitAt) {
        if (left <= 0 || !RuneKit.sameLife(sp, life) || from.isRemoved() || !(from.level() instanceof ServerLevel level)) return;
        double range = k.num("bounceRange", 7);
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, from.position(), range, 4)) {
            if (e == from || hitAt.containsKey(e.getUUID()) || !Kit.splashFoe(sp, e)) continue;
            Vec3 to = V.flat(V.sub(e.position(), from.position()));
            double score = e.distanceToSqr(from) - (V.dot(V.unit(to), dir) > 0.1 ? 12 : 0);
            if (score < best) {
                best = score;
                next = e;
            }
        }
        boolean same = next == null;
        if (same) {
            if (!from.isAlive()) return;
            next = from;
        }
        LivingEntity n = next;
        long now = JobKit.now();
        int delay = same ? (int) Math.max(k.num("bounceGap", 3), hitAt.getOrDefault(n.getUUID(), now) + (long) k.num("sameGap", 10) - now) : (int) k.num("bounceGap", 3);
        hitAt.put(n.getUUID(), now + delay);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 a = Kit.bodyPoint(from);
        Vec3 b = Kit.bodyPoint(n);
        // 맞은 자리에서 위로 솟는 빛 (다음 문으로) · 다음 적 위 하늘의 문 (성광 문양 — 시전자를 봄) · 내리꽂히는 3D 하늘의 화살 (delay 틱에 닿음)
        Vec3 gate = V.up(n.position(), n.getBbHeight() + 4.2);
        RuneKit.streak(level, a, gate, GOLD, 0.14, 0.3, 0.85, 0);
        RuneKit.skyPlate(sp, level, "sigil", gate, 1.5, glow, core, Opt.of("cell", HOLY, "life", (delay + 8) / 20.0, "spinRate", 90.0, "grow", 0.25, "alpha", 0.9,
                "fade", 0.4));
        SkillFx.burst(level, gate, core, 1.0, 0.25);
        ArrowFx.arrow3d(level, V.up(gate, 0.4), b, Math.max(1, delay), 2.2, 6, n, GOLD, FLASH);
        RuneKit.flyStreak(level, V.up(gate, 0.4), b, V.len(V.sub(b, gate)) / (Math.max(1, delay) / 20.0), GOLD, FLASH, 0.9, 0.26);
        SkillFx.sparks(level, a, GOLD, 10, 5);
        ArrowFx.glint(level, a, 0.8, 0.25, GOLD, Opt.empty());
        SkillFx.play(level, "item.trident.return", a, 1.6, 0.7);
        SkillFx.play(level, "block.amethyst_block.resonate", gate, 1.8, 0.6);
        JobKit.strikeCrit(sp, n, j, P, k.num("bounce", 5), false, false, life, delay, d -> {
            if (!(n.level() instanceof ServerLevel lv)) return;
            Vec3 at = Kit.bodyPoint(n);
            JobImpact.hit(sp, n, j, 1.15);
            pillar(sp, lv, n.position(), 9, 0.4, glow, core);
            ArrowFx.glint(lv, at, 1.0, 0.3, RuneKit.WHITE, Opt.empty());
            RuneKit.zonePulse(lv, n.position(), GOLD, 1.5 + Kit.bodyPad(n), 0.3);
            SkillFx.sparks(lv, at, GOLD, 12, 5);
        });
        Vec3 nd = same ? dir : V.flat(V.sub(n.position(), from.position()));
        RuneKit.later(delay, () -> bounce(sp, j, k, P, life, n, nd, left - 1, hitAt));
    }

    // ------------------------------------------------------------ 세라핌

    /** 세라핌의 날개 (태세 동안 — 사람마다 하나) */
    private static final Map<UUID, LightWings> SERAPH = new HashMap<>();

    /**
     * 세라핌 (sniper_mark): dur 틱 태세 (강화 — 지속) — 활 평타가 같은 적을 every 번째 맞힐 때마다 하늘에서 심판의 화살 (그 적 최대 체력 × pct — 위력 × min ~ cap,
     * JobTraits9). 그림: 등에 3D 빛의 날개 여섯 (세 쌍) 이 펼쳐지고 머리 위 3D 빛 고리 (심판 여덟 칸의 바탕) · 발밑 성광 문양 · 위로 솟는 빛 · 떨어지는 깃털
     */
    static boolean mark(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        JobTraits9.markStart(sp);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        LightWings old = SERAPH.remove(sp.getUUID());
        if (old != null) old.end();
        LightWings wings = new LightWings(sp, 3, 3.0, dur, 6, 2, 1.1, FEATHER, FEATHER_GLOW);
        wings.keep = () -> JobTraits.stanceOn(sp, "sniper_mark") && JobTraits9.jobIf(sp, JobTraits9.SN) != null;
        SERAPH.put(sp.getUUID(), wings);
        RuneKit.domainSigil(level, c, 2.4, "holy", glow, core, Opt.of("life", 0.9, "spinRate", 60.0, "grow", 0.15, "alpha", 0.85));
        RuneKit.zonePulse(level, c, glow, 2.6, 0.45);
        for (int i = 0; i < 12; i++) {
            Vec3 at = V.add(V.up(c, 0.3 + Kit.rand() * 1.4), RuneKit.turn(new Vec3(1, 0, 0), i * 30), 0.9 + Kit.rand() * 0.6);
            SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", V.UP, "speed", 2.0, "size", 0.14, "grow", 0.6, "life", 0.6, "color", i % 2 == 0 ? core : glow,
                    "alpha", 1.0));
        }
        for (int i = 0; i < 8; i++) ArrowFx.feather(level, V.add(V.up(c, 2.4 + Kit.rand()), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 2.2), 0.22, 1.4, FEATHER);
        SkillFx.play(level, "block.beacon.power_select", c, 1.6, 0.6);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.6, 0.7);
        return true;
    }

    /** 세라핌이 끝남 (태세가 끝나거나 바뀜 — JobTraits9.tick): 날개를 접음 */
    static void seraphEnd(ServerPlayer sp) {
        LightWings w = SERAPH.remove(sp.getUUID());
        if (w != null) w.end();
    }

    /** 세라핌의 날개를 크게 한 번 내려침 (심판의 화살이 떨어질 때) */
    static void seraphBeat(ServerPlayer sp) {
        LightWings w = SERAPH.get(sp.getUUID());
        if (w != null && !w.done) w.beat();
    }

    // ------------------------------------------------------------ 디바인 펀니시먼트 (2026-11-14 — 사일런트 아울 대신)

    /** 조준 (끝 틱 · 목숨 · 쏨 · 날개) */
    private static final class Snipe {
        final long deadline;
        final String life;
        final long start;
        int shot;
        LightWings wings;
        Vec3 gate;

        Snipe(long deadline, String life, long start) {
            this.deadline = deadline;
            this.life = life;
            this.start = start;
        }
    }

    private static final Map<UUID, Snipe> SNIPES = new HashMap<>();
    /** 심판의 화살 (3D 빛 화살 — 길이 칸) · 하늘의 문이 열리는 높이 (과녁 머리 위) */
    private static final double GREAT_LEN = 8.5;
    private static final double GATE_UP = 9.0;

    /**
     * 디바인 펀니시먼트 (sniper_ult): 무릎 꿇고 조준 (하늘이 금빛으로 — rpg:ult_heaven · 밀려나지 않음) · 등에 3D 빛의 날개가 크게 펼쳐지고 머리 위 빛 고리 · 보는 쪽에서 처음
     * 맞는 적 (range 칸 · 폭 width) 위 하늘에 빛의 문 (성광 문양 — 커지며 돎) 이 열리고 그 적에 금빛 고리가 조여 듦 → gap 틱 뒤부터 다시 누르면 (window 틱이 지나면 저절로) 단
     * 한 발: 빛의 화살을 놓는 순간 문에서 거대한 3D 심판의 화살이 내리꽂혀 그 적의 급소를 꿰뚫음 (shot · 치명 · 큰 숫자 · 잃은 체력만큼 최대 + missing — 화살이 날아가 닿는
     * 틱) → burstAt 틱 뒤 꽂힌 자리에서 성광이 터짐 (burst — 같은 적)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int stage = JobRecast.stage(sp, k.id());
        if (stage >= 1) {
            Snipe s = SNIPES.get(sp.getUUID());
            JobRecast.close(sp, k.id());
            if (s == null || !RuneKit.sameLife(sp, s.life) || s.shot > 0) return true;
            JobRecast.motion(sp, k.id() + ":shot");
            fire(sp, j, k, P, s);
            SNIPES.remove(sp.getUUID());
            return true;
        }
        ServerLevel level0 = sp.level();
        int window = Math.max(20, (int) k.num("window", 40));
        int gap = Math.max(10, (int) k.num("gap", 20));
        String life = RuneKit.lifeToken(sp);
        long now0 = JobKit.now();
        Snipe s = new Snipe(now0 + window, life, now0);
        SNIPES.put(sp.getUUID(), s);
        Ult.sky(level0, sp.position(), "rpg:ult_heaven", window + 50);
        RpgCombat.setSteadfast(sp, window + 30);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        s.wings = new LightWings(sp, 1, 4.4, window + 12, 6, 2, 1.3, FEATHER, FEATHER_GLOW);
        RuneKit.domainSigil(level0, sp.position(), 2.6, "holy", glow, core, Opt.of("life", window / 20.0 + 0.3, "spinRate", 30.0, "grow", 0.1, "alpha", 0.8));
        SkillFx.play(level0, "crossbow.loading.end", sp.position(), 0.6, 1.0);
        SkillFx.play(level0, "block.beacon.activate", sp.position(), 1.2, 0.7);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.3, 0.6);
        // 자세 동안 (2틱마다): 보는 쪽에서 처음 맞는 적 (없으면 18칸 앞) 위 하늘의 문 (커지며 돎) · 그 적에 조여 드는 금빛 고리 · 문에서 내려오는 가는 빛 · 떨어지는 깃털
        for (int t = 2; t <= window + 2; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                Snipe cur = SNIPES.get(sp.getUUID());
                if (cur != s || !RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 eye = sp.getEyePosition();
                Vec3 look = sp.getViewVector(1F);
                double len = Math.max(2, RuneKit.rayLength(level, eye, look, k.num("range", 48)));
                Kit.LineHit h = firstOnLine(sp, eye, look, len, k.num("width", 1.6) / 2);
                Vec3 at = h == null ? V.add(eye, look, Math.min(len, 18)) : Kit.bodyPoint(h.e());
                Vec3 foot = h == null ? new Vec3(at.x, RuneKit.groundAt(level, at, sp.getY()), at.z) : h.e().position();
                double q = Math.min(1.0, tt / (double) gap);
                double top = h == null ? 2.0 : h.e().getBbHeight();
                Vec3 gate = V.up(foot, top + GATE_UP);
                s.gate = gate;
                RuneKit.skyPlate(sp, level, "sigil", gate, 1.4 + 1.6 * q, glow, core, Opt.of("cell", HOLY, "life", 0.16, "spin", tt * 6.0, "alpha", 0.9, "fade", 0.0,
                        "solo", 9.0));
                double size = (h == null ? 0.9 : 1.0 + Kit.bodyPad(h.e()) * 0.5) * (1.9 - 0.9 * q);
                SkillFx.ring(level, at, V.unit(V.sub(eye, at)), GOLD, size, 0.14, 0);
                if (tt % 4 == 0) RuneKit.streak(level, V.up(gate, -0.3), V.up(foot, top + 0.4), core, 0.05 + 0.05 * q, 0.2, 0.6, 0);
                if (tt == gap) {
                    SkillFx.playFor(sp, "block.note_block.chime", 1.6, 0.6); // 조여 듦 끝 — 이제 쏠 수 있음
                    SkillFx.ring(level, at, V.unit(V.sub(eye, at)), FLASH, size * 1.3, 0.25, 0);
                    SkillFx.burst(level, gate, core, 2.0, 0.3);
                }
                if (tt % 6 == 0 && RuneKit.fxQuota("seraphFeather", 4)) ArrowFx.feather(level, V.add(V.up(sp.position(), 2.6), V.rightOf(Kit.facingOf(sp)),
                        (Kit.rand() - 0.5) * 3.0), 0.2, 1.2, FEATHER);
            });
        }
        JobRecast.open(sp, k, 1, window, gap, () -> autoFire(sp, j, k, P));
        JobSystem.note(sp, "§e" + Math.round(gap / 2.0) / 10.0 + "초 뒤 다시 눌러 쏨 §7(" + window / 20 + "초가 지나면 저절로)");
        return true;
    }

    /** 창이 닫힘 (다시 누르지 않음): 저절로 쏨 */
    private static void autoFire(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        Snipe s = SNIPES.get(sp.getUUID());
        if (s == null || !RuneKit.sameLife(sp, s.life) || s.shot > 0) return;
        JobKit.motion(sp, k.id() + ":shot");
        fire(sp, j, k, P, s);
        SNIPES.remove(sp.getUUID());
    }

    /**
     * 단 한 발: 날개를 크게 내려치며 빛의 화살을 놓음 (3D 빛 화살 · 금빛 줄) — 같은 틱 하늘의 문에서 거대한 3D 심판의 화살 (GREAT_LEN 칸) 이 내리꽂혀 화살이 닿는 틱에 그 적의
     * 급소를 꿰뚫음 (shot — 치명 · 큰 숫자, 잃은 체력만큼 +) · 하늘의 빛기둥 → burstAt 틱 뒤 꽂힌 자리에서 성광이 터짐 (burst — 흔들림 없는 큰 터짐 · 둘레 빛기둥 다섯)
     */
    private static void fire(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Snipe s) {
        s.shot = 1;
        ServerLevel level = sp.level();
        String life = s.life;
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1F);
        double len = Math.max(2, RuneKit.rayLength(level, eye, look, k.num("range", 48)));
        Kit.LineHit h = firstOnLine(sp, eye, look, len, k.num("width", 1.6) / 2);
        double reach = h == null ? len : Math.max(1, h.t());
        Vec3 from = V.add(RuneKit.bowHand(sp), look, 0.9);
        Vec3 end = V.add(eye, look, reach);
        double speed = visSpeed(k.num("speed", 60), reach);
        double fly = reach / speed;
        int hitT = Math.max(1, (int) Math.round(fly * 20));
        if (s.wings != null) {
            s.wings.beat();
            LightWings w = s.wings;
            RuneKit.later(10, w::end);
        }
        // 빛의 화살 (3D · 금빛 줄 · 흰 섬광 줄)
        ArrowFx.arrow3d(level, from, end, hitT, 2.0, 6, h == null ? null : h.e(), glow, FLASH);
        RuneKit.flyStreak(level, from, end, speed, GOLD, FLASH, 1.2, 0.32);
        RuneKit.streak(level, V.add(from, look, 0.6), end, GOLD, 0.12, 0.35, 0.8, 0);
        SkillFx.burst(level, from, glow, 1.0, 0.25);
        Ult.flash(level, from, GOLD, Opt.empty(), sp);
        SkillFx.play(level, "item.crossbow.shoot", from, 0.6, 1.0);
        SkillFx.play(level, "item.trident.thunder", from, 1.4, 0.6);
        JobMove.run(sp, JobMove.to(0, 5, JobMove.ahead(sp, -0.8, 0), JobMove.OUT));
        // 하늘의 문에서 거대한 심판의 화살 (과녁이 없으면 화살이 닿은 땅)
        Vec3 foot = h == null ? new Vec3(end.x, RuneKit.groundAt(level, end, sp.getY()), end.z) : h.e().position();
        double top = h == null ? 0.3 : h.e().getBbHeight() * 0.55;
        Vec3 gate = V.up(foot, (h == null ? 2.0 : h.e().getBbHeight()) + GATE_UP);
        Vec3 tipAt = V.up(foot, top);
        RuneKit.skyPlate(sp, level, "sigil", gate, 3.4, glow, core, Opt.of("cell", HOLY, "life", (hitT + 14) / 20.0, "spinRate", 120.0, "grow", 0.3, "alpha", 1.0,
                "fade", 0.5));
        SkillFx.burst(level, gate, core, 2.6, 0.35);
        ArrowFx.arrow3d(level, V.up(gate, -0.2 + ArrowFx.reach(GREAT_LEN)), tipAt, hitT, GREAT_LEN, 16, h == null ? null : h.e(), GOLD, FLASH);
        RuneKit.flyStreak(level, gate, tipAt, V.len(V.sub(tipAt, gate)) / Math.max(0.05, hitT / 20.0), GOLD, FLASH, 2.0, 0.6);
        RuneKit.later(hitT, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            pillar(sp, lv, foot, 18, 0.6, glow, core);
            BowFx.impact(lv, foot, glow, core, 2.6, "noble");
            SkillFx.play(lv, "ambient.weather.lightning.impact", foot, 1.4, 0.6);
        });
        if (h == null) return;
        LivingEntity e = h.e();
        double max = e.getMaxHealth();
        double missing = max > 0 ? Math.max(0, Math.min(1, 1 - e.getHealth() / max)) : 0;
        double m = k.num("shot", 45) * (1 + k.num("missing", 0.5) * missing);
        int burstAt = Math.max(10, (int) k.num("burstAt", 10)); // 같은 적 — 몹 무적 시간 (10틱) 을 넘게
        JobKit.strikeCrit(sp, e, j, P, m, false, true, life, hitT, d -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            Vec3 at = Kit.bodyPoint(e);
            JobImpact.hit(sp, e, j, 2.0);
            ArrowFx.glint(lv, at, 1.6, 0.4, RuneKit.WHITE, Opt.empty());
            SkillFx.fx(lv, "rpg:slash_x", V.add(at, look, -0.4), Opt.of("dir", look, "size", 1.8, "life", 0.35, "color", GOLD, "alpha", 1.0));
            SkillFx.sparks(lv, at, GOLD, 18, 6);
        });
        JobKit.strike(sp, e, j, P, k.num("burst", 15), false, true, life, hitT + burstAt, d -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            Vec3 at = Kit.bodyPoint(e);
            // 꽂힌 자리에서 성광이 터짐: 흔들림 없는 큰 터짐 · 둘레 빛기둥 다섯 · 큰 반짝임 · 위로 솟는 빛 알갱이
            BowFx.bigImpact(lv, e.position(), glow, core, 3.2 + Kit.bodyPad(e));
            RuneKit.godRays(sp, lv, e.position(), 2.4 + Kit.bodyPad(e), 5, glow, core, Opt.of("h", 12.0, "life", 0.6, "skipNear", true));
            ArrowFx.glint(lv, at, 2.4, 0.45, FLASH, Opt.empty());
            SkillFx.play(lv, "block.amethyst_block.break", at, 1.4, 0.9);
            SkillFx.play(lv, "firework.large_blast", at, 1.2, 0.6);
        });
    }

    /** 시험: 디바인 펀니시먼트 조준이 남았는지 · 쏜 수 (없으면 −1) */
    static int deadeyeShots(ServerPlayer sp) {
        Snipe s = SNIPES.get(sp.getUUID());
        return s == null ? -1 : s.shot;
    }
}
