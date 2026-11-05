package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.mob.PierrotSummon;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Aegis;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 암살자 반전 피에로 직업 스킬 (재설계안 2판 — 사용자 "기존 암살자 변형 없애고 암살자 변형으로 샤코 같은 느낌에 광대컨셉으로 소환물로 어그로랑 탱킹을 해주는
 * 직업군" · 초당 피해는 "1차 암살자랑 같게"): 잭 인 더 박스 · 핸드스프링 · 벌룬 퍼레이드 · 할루시네이트 · 그랜드 피날레 (jobs.json advance.pierrot — 서커스 빨강 · 금빛,
 * 특성 「쇼타임」 — JobTraits10 · 소환물 PierrotSummons).
 * 1차 암살자 (숨어서 한 놈을 한 방 — 딜러) 를 뒤집음: 광대 소환물 (깜짝 상자 · 광대 인형) 이 적을 끌어들여 대신 맞고, 피에로는 재주를 넘어 적을 놀리며 (도발) 버팀.
 * 피해 = 위력 P × 배율 (JobKit.strike — 같은 적은 10틱 넘게 띄움). 연출 = 진짜 몹 (상자 · 인형 — 입체 모델 · 동작) + 2D 그림 (색종이 rfx_jconfetti · 풍선
 * rfx_jballoon · 불꽃놀이 rfx_jfirework · 조명 rfx_jspot · 단검 · 터짐 · 고리) · 서커스 하늘 (rpg:ult_circus).
 */
final class PierrotSkills {
    private PierrotSkills() {}

    /** 서커스 빨강 · 금빛 · 흰빛 · 청록 (색종이 · 실 · 고리) */
    static final double[] RED = {0.95, 0.22, 0.32};
    static final double[] GOLD = {1.0, 0.84, 0.32};
    static final double[] WHITE = {1.0, 0.97, 0.92};
    static final double[] TEAL = {0.2, 0.86, 0.8};

    static void register() {
        JobSystem.register("pierrot_box", PierrotSkills::box);
        JobSystem.register("pierrot_spring", PierrotSkills::spring);
        JobSystem.register("pierrot_balloon", PierrotSkills::balloon);
        JobSystem.register("pierrot_doll", PierrotSkills::doll);
        JobSystem.register("pierrot_ult", PierrotSkills::ult);
    }

    /** 상자 · 인형이 설 자리: 겨눈 적 앞 (나와 적 사이 — 적 몸에서 gap 칸 · 나에게서 적어도 min 칸) 이나 보는 쪽 fallback 칸 (벽 앞까지), 그 아래 땅 */
    private static Vec3 spotFor(ServerPlayer sp, LivingEntity aim, double gap, double fallback, double min) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 want;
        if (aim != null) {
            double d = V.hdist(aim.position(), sp.position()); // V.flat 은 단위 방향 — 거리는 hdist
            Vec3 u = d < 0.1 ? f : V.flat(V.sub(aim.position(), sp.position()));
            double s = Math.max(min, d - gap - Kit.bodyPad(aim));
            want = V.add(sp.position(), u, Math.min(s, RuneKit.dashDistance(sp, u, s, 0.3, 1.5)));
        } else want = V.add(sp.position(), f, RuneKit.dashDistance(sp, f, fallback, 0.3, 1.5));
        Double gy = RuneKit.solidGround(level, new Vec3(want.x, Math.max(want.y, sp.getY()) + 1.2, want.z), 4);
        return new Vec3(want.x, gy != null ? gy : sp.getY(), want.z);
    }

    /** 광대 인형: 그 자리에서 오른쪽으로 side 칸 (커다란 인형이 화면 가운데를 가리지 않게 — 오른쪽이 막혔으면 왼쪽, 둘 다 막혔으면 그대로) */
    private static Vec3 beside(ServerPlayer sp, Vec3 spot, double side) {
        ServerLevel level = sp.level();
        Vec3 r = V.rightOf(Kit.facingOf(sp));
        for (double s : new double[] {side, -side}) {
            Vec3 want = V.add(spot, r, s);
            Double gy = RuneKit.solidGround(level, new Vec3(want.x, spot.y + 1.2, want.z), 3);
            if (gy == null || Math.abs(gy - spot.y) > 1.1) continue;
            Vec3 at = new Vec3(want.x, gy, want.z);
            if (level.noCollision(new AABB(at.x - 0.35, at.y + 0.1, at.z - 0.35, at.x + 0.35, at.y + 1.8, at.z + 0.35))) return at;
        }
        return spot;
    }

    /** 자바 각도 (몸이 그쪽을 봄) */
    private static float yawToward(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    }

    /** 상자 · 인형이 나타남: 연기 · 색종이 · 금빛 고리 · 장난감 소리 */
    private static void appear(ServerLevel level, Vec3 feet, double size) {
        JobFx.smoke(level, V.up(feet, 0.4 * size), 0.7 * size, WHITE, GOLD, 0.45, Opt.of("alpha", 0.65));
        JobFx.confetti(level, V.up(feet, 0.8 * size), 0.55 * size, 0.7, Opt.empty());
        SkillFx.shock(level, V.up(feet, 0.06), GOLD, 0.55 * size, 0.35);
        SkillFx.play(level, "block.wood.place", feet, 1.3, 0.9);
        SkillFx.play(level, "entity.chicken.egg", feet, 0.7, 0.8);
    }

    /** 광대 웃음 (높은 마녀 웃음) */
    private static void giggle(ServerLevel level, Vec3 at, double pitch) {
        SkillFx.play(level, "entity.witch.celebrate", at, pitch, 0.7);
    }

    // ------------------------------------------------------------ 잭 인 더 박스

    /**
     * 잭 인 더 박스: 겨눈 적 앞 (range 칸 — 없으면 앞 fallback 칸) 으로 깜짝 상자를 던짐 (throwAt 틱 — 손에서 금빛 포물선) → landAt 틱 떨어져 섬 (상자 몹 — 체력 = 내
     * 최대 체력 × hp · life 틱) → popAt 틱 뚜껑이 열리며 용수철 광대가 튀어나옴 (끝 자세 유지 · 둘레 몬스터를 끌어들임 — 쇼타임) → first 틱부터 every 틱마다 knives 번
     * 상자 둘레 reach 칸의 가장 가까운 적에게 칼을 던짐 (knife — 2틱 날아감, 던지는 동작은 그 2틱 전). 상자는 boxes 개까지 (넘으면 가장 오래된 것이 펑).
     */
    static boolean box(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        LivingEntity aim = Kit.pickTarget(sp, k.num("range", 8), 25);
        Vec3 spot = spotFor(sp, aim, 1.3, k.num("fallback", 4), 0.9);
        int throwAt = Math.max(0, (int) k.num("throwAt", 2));
        int landAt = Math.max(throwAt + 1, (int) k.num("landAt", 4));
        int popAt = Math.max(landAt + 1, (int) k.num("popAt", 6));
        int first = Math.max(popAt + 2, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int knives = Math.max(1, (int) k.num("knives", 5));
        int life = Math.max(first + every * knives, (int) k.num("life", 120));
        double reach = k.num("reach", 6);
        String lifeTok = RuneKit.lifeToken(sp);
        // 상자는 boxes 개까지: 넘으면 가장 오래된 것이 펑
        List<PierrotSummons.Member> boxes = PierrotSummons.of(sp, PierrotSummons.BOX);
        int max = Math.max(1, (int) k.num("boxes", 2));
        for (int i = 0; i <= boxes.size() - max; i++) PierrotSummons.dismiss(boxes.get(i), true);
        // 던짐: 손에서 금빛 포물선 (도는 상자 빛) · 휘파람
        SkillFx.play(level0, "entity.snowball.throw", sp.position(), 0.8, 0.8);
        RuneKit.later(throwAt, () -> {
            if (!RuneKit.sameLife(sp, lifeTok)) return;
            ServerLevel lv = sp.level();
            Vec3 hand = RuneKit.handOf(sp).hand();
            int n = Math.max(1, landAt - throwAt);
            for (int t = 0; t <= n; t++) {
                int tt = t;
                RuneKit.later(t, () -> {
                    double u = tt / (double) n;
                    Vec3 p = V.up(V.lerp(hand, V.up(spot, 0.5), u), Math.sin(u * Math.PI) * 1.3);
                    JobFx.glint(lv, p, 0.35, GOLD, WHITE, 0.15, Opt.of("spin", tt * 90.0));
                    SkillFx.sparks(lv, p, RED, 2, 1.5);
                });
            }
        });
        // 떨어져 섬
        PierrotSummons.Member[] box = {null};
        RuneKit.later(landAt, () -> {
            if (!RuneKit.sameLife(sp, lifeTok)) return;
            ServerLevel lv = sp.level();
            float yaw = aim != null && aim.isAlive() ? yawToward(spot, aim.position()) : yawToward(spot, V.add(spot, Kit.facingOf(sp), 2));
            box[0] = PierrotSummons.spawn(sp, PierrotSummons.BOX, spot, yaw, life, k.num("hp", 0.35), 1.0, P, popAt - landAt);
            if (box[0] != null) box[0].x.playAnimation(PierrotSummon.BOX_DROP);
            appear(lv, spot, 1.0);
            SkillFx.play(lv, "block.chest.close", spot, 1.4, 0.8);
        });
        // 뚜껑이 열림: 용수철 광대 · 색종이 · 웃음
        RuneKit.later(popAt, () -> {
            PierrotSummons.Member m = box[0];
            if (m == null || !m.alive() || !RuneKit.sameLife(sp, lifeTok)) return;
            ServerLevel lv = sp.level();
            m.x.playAnimation(PierrotSummon.BOX_POP, "state", "0");
            Vec3 head = V.up(m.x.position(), 1.25);
            JobFx.confetti(lv, head, 0.75, 0.8, Opt.empty());
            JobFx.burst(lv, head, 0.6, GOLD, WHITE, 0.25, Opt.empty());
            SkillFx.play(lv, "block.chest.open", head, 1.5, 0.8);
            SkillFx.play(lv, "entity.slime.jump", head, 1.6, 0.9);
            giggle(lv, head, 1.5);
        });
        // 칼 던지기: 둘레 reach 칸의 가장 가까운 적 (2틱 날아감)
        for (int i = 0; i < knives; i++) {
            int at = first + i * every;
            RuneKit.later(at - 2, () -> {
                PierrotSummons.Member m = box[0];
                if (m == null || !m.alive() || !RuneKit.sameLife(sp, lifeTok)) return;
                LivingEntity e = nearest(sp, m.x.position(), reach);
                if (e == null) return;
                ServerLevel lv = sp.level();
                m.x.playAnimation(PierrotSummon.BOX_THROW);
                PierrotSummons.face(m.x, e.position());
                Vec3 from = V.up(m.x.position(), 1.3);
                JobFx.dagger(lv, from, Kit.bodyPoint(e), 2, 0.42, j, Opt.empty());
                SkillFx.play(lv, "entity.snowball.throw", from, 1.6, 0.6);
                JobKit.strike(sp, e, j, P, k.num("knife", 0.95), false, false, lifeTok, 2, d -> {
                    if (!(e.level() instanceof ServerLevel elv)) return;
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.cross(elv, b, 0.5 + Kit.bodyPad(e) * 0.3, j, 0.2, Opt.of("vel", RuneKit.entVel(e)));
                    SkillFx.sparks(elv, b, GOLD, 4, 2.5);
                    SkillFx.play(elv, "item.trident.hit", b, 1.6, 0.6);
                });
            });
        }
        return true;
    }

    /** at 둘레 r 칸의 가장 가까운 적 (보스 · 몬스터 · 적대 몹 — 없으면 null) */
    private static LivingEntity nearest(ServerPlayer sp, Vec3 at, double r) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, at, r, 3)) {
            double d = e.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ 핸드스프링

    /**
     * 핸드스프링: 앞으로 dist 칸 재주를 넘음 (land 틱 — lift 칸 솟는 포물선 · 몸을 뒤집는 동작) → 내려서며 둘레 radius 칸을 내리침 (total) · 맞은 적을 taunt 틱 도발
     * (보스도 나를 — Aegis.tauntFor) · guardFor 틱 받는 피해 − guard. 묶였거나 타고 있으면 제자리에서 (피해 · 도발은 그대로)
     */
    static boolean spring(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int land = Math.max(2, (int) k.num("land", 8));
        double R = k.num("radius", 3);
        String life = RuneKit.lifeToken(sp);
        boolean free = !Status.isRooted(sp) && !Kit.ridingAny(sp);
        Vec3 start = sp.position();
        if (free) {
            Kit.noFall(sp, land + 20);
            JobMove.run(sp, JobMove.hop(0, land, JobMove.landAhead(sp, k.num("dist", 5), sp.getY()), k.num("lift", 1.4)));
        }
        RpgCombat.setSteadfast(sp, land + 4);
        // 출발: 발밑 금빛 고리 · 리본 · 휙
        SkillFx.shock(level0, V.up(start, 0.06), GOLD, 0.5, 0.3);
        JobFx.confetti(level0, V.up(start, 0.6), 0.45, 0.5, JobFx.hide1p(sp));
        SkillFx.play(level0, "entity.player.attack.sweep", start, 1.6, 0.6);
        SkillFx.play(level0, "mob.breeze.jump", start, 1.4, 0.7);
        for (int t = 1; t < land; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = V.up(sp.position(), 0.9);
                JobFx.glint(lv, c, 0.3, GOLD, WHITE, 0.2, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
                SkillFx.sparks(lv, c, RED, 2, 1.5);
            });
        }
        RuneKit.later(land, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            // 내려섬: 금빛 충격 고리 · 색종이 · 별 · 쿵
            SkillFx.shock(lv, V.up(c, 0.06), GOLD, R / 2.1, 0.45);
            SkillFx.shock(lv, V.up(c, 0.08), RED, R / 3.2, 0.35);
            JobFx.confetti(lv, V.up(c, 0.5), 1.0, 0.8, Opt.empty());
            JobFx.burst(lv, V.up(c, 0.3), 1.1, RED, GOLD, 0.3, Opt.empty());
            SkillFx.play(lv, "entity.player.attack.knockback", c, 0.8, 1.0);
            SkillFx.play(lv, "block.note_block.bell", c, 1.2, 0.6);
            giggle(lv, c, 1.3);
            JobTraits10.guardFor(sp, (int) k.num("guardFor", 40), JobUpgrades.num(sp, k, "guard", k.num("guard", 0.3)));
            int taunt = (int) k.num("taunt", 60);
            for (LivingEntity e : Kit.around(sp, c, R, 2.5)) {
                JobKit.strike(sp, e, j, P, k.total(), false, false, life, 0, d -> {
                    if (!(e.level() instanceof ServerLevel elv)) return;
                    JobFx.hit(e, j, 1.0);
                    tauntCue(elv, e);
                });
                Aegis.tauntFor(sp, e, taunt, true);
            }
            // 받는 피해 감소: 몸 둘레 금빛 고리 (3인칭)
            JobFx.halo(lv, V.up(c, 0.9), 0.9, j, 0.5, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        });
        return true;
    }

    /** 도발 표시: 머리 위 작은 색종이 · 빨간 느낌표 빛 */
    static void tauntCue(ServerLevel level, LivingEntity e) {
        Vec3 over = RuneKit.overHead(e, 0.45);
        JobFx.confetti(level, over, 0.32, 0.5, Opt.of("vel", RuneKit.entVel(e)));
        JobFx.glint(level, over, 0.3, RED, WHITE, 0.4, Opt.of("vel", RuneKit.entVel(e)));
    }

    // ------------------------------------------------------------ 벌룬 퍼레이드

    /**
     * 벌룬 퍼레이드: 풍선 동물 balloons 개를 두 손에서 날려 보냄 — 둘레 radius 칸의 가까운 적 (balloons 명까지 · 적이 적으면 나눠 붙음) 에게 휘어 날아가 tangleAt 틱
     * 칭칭 감음 (tangle · slow 틱 둔화) → popAt 틱 펑 터짐 (pop · 색종이 · taunt 틱 도발 — 나를). 적이 없으면 앞으로 떠올라 터짐 (피해 없음)
     */
    static boolean balloon(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int n = Math.max(1, (int) k.num("balloons", 6));
        int tangleAt = Math.max(4, (int) k.num("tangleAt", 14));
        int popAt = Math.max(tangleAt + 10, (int) k.num("popAt", 24));
        String life = RuneKit.lifeToken(sp);
        List<LivingEntity> near = new ArrayList<>(Kit.around(sp, sp.position(), k.num("radius", 7), 3));
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        List<LivingEntity> foes = List.copyOf(near.size() > n ? near.subList(0, n) : near);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        SkillFx.play(level0, "entity.item.pickup", c0, 0.6, 0.9);
        SkillFx.play(level0, "block.note_block.chime", c0, 1.4, 0.6);
        giggle(level0, c0, 1.6);
        for (int i = 0; i < n; i++) {
            LivingEntity e = foes.isEmpty() ? null : foes.get(i % foes.size());
            double[][] col = JobFx.CIRCUS[i % JobFx.CIRCUS.length];
            int kind = i % 2;
            double side = (i - (n - 1) / 2.0) * 0.45;
            Vec3 from = V.up(V.add(V.add(c0, f, 0.5), r, side), 1.4);
            // 가는 길: 위로 솟았다가 적의 머리 쪽으로 (적이 여럿에 붙으면 둘레로 조금씩 비켜 섬)
            double ang = i * 2.39996;
            Vec3 off = new Vec3(Math.cos(ang) * 0.35, 0, Math.sin(ang) * 0.35);
            for (int t = 0; t < tangleAt; t += 2) {
                int tt = t;
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    ServerLevel lv = sp.level();
                    double u = tt / (double) tangleAt;
                    double u2 = (tt + 2) / (double) tangleAt;
                    Vec3 to = e != null && e.isAlive() ? V.add(V.up(e.position(), e.getBbHeight() * 0.75), off, 1) : V.up(V.add(V.add(c0, f, 5), r, side * 2), 2.4);
                    Vec3 p = V.up(V.lerp(from, to, u * u * (3 - 2 * u)), Math.sin(u * Math.PI) * 1.6);
                    Vec3 p2 = V.up(V.lerp(from, to, Math.min(1, u2) * Math.min(1, u2) * (3 - 2 * Math.min(1, u2))), Math.sin(Math.min(1, u2) * Math.PI) * 1.6);
                    JobFx.balloon(lv, p, kind, 0.5, 0.11, Opt.of("vel", V.scale(V.sub(p2, p), 10), "fade", 0.0), col[0], col[1]);
                });
            }
            if (e == null) {
                RuneKit.later(tangleAt, () -> {
                    ServerLevel lv = sp.level();
                    Vec3 at = V.up(V.add(V.add(c0, f, 5), r, side * 2), 2.4);
                    JobFx.balloonPop(lv, at, 0.6, 0.3, Opt.empty(), col[0], col[1]);
                    JobFx.confetti(lv, at, 0.5, 0.6, Opt.empty());
                    SkillFx.play(lv, "entity.firework_rocket.blast", at, 1.8, 0.5);
                });
            }
        }
        // 감음 · 터짐 (적마다 한 번 — 풍선이 여럿 붙어도)
        for (LivingEntity e : foes) {
            int stack = 0;
            for (int i = 0; i < n; i++) if (foes.get(i % foes.size()) == e) stack++;
            int count = stack;
            double[][] col = JobFx.CIRCUS[foes.indexOf(e) % JobFx.CIRCUS.length];
            JobKit.strike(sp, e, j, P, k.num("tangle", 2.55), false, false, life, tangleAt, d -> {
                if (!(e.level() instanceof ServerLevel lv)) return;
                if (!JobKit.isBoss(e)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) k.num("slow", 40), 1, false, true));
                SkillFx.play(lv, "entity.slime.squish", Kit.bodyPoint(e), 1.7, 0.7);
                SkillFx.play(lv, "block.wool.place", Kit.bodyPoint(e), 1.2, 0.8);
            });
            // 감긴 풍선 고리 (터질 때까지 — 적을 따라감)
            for (int t = tangleAt; t < popAt; t += 4) {
                int tt = t;
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !(e.level() instanceof ServerLevel lv)) return;
                    double s = 0.55 + Kit.bodyPad(e) * 0.45 + 0.05 * count;
                    JobFx.balloonWrap(lv, Kit.bodyPoint(e), s, 0.22, Opt.of("vel", RuneKit.entVel(e), "fade", 0.0, "phase", (double) ((tt / 4) % 2)), col[0], col[1]);
                    if (tt == tangleAt) JobFx.balloonWrap(lv, V.up(Kit.bodyPoint(e), 0.35), s * 0.8, 0.22 + (popAt - tangleAt) / 20.0, Opt.of("vel", RuneKit.entVel(e),
                            "phase", 1.0), JobFx.CIRCUS[(foes.indexOf(e) + 2) % JobFx.CIRCUS.length][0], JobFx.CIRCUS[(foes.indexOf(e) + 2) % JobFx.CIRCUS.length][1]);
                });
            }
            JobKit.strike(sp, e, j, P, k.num("pop", 6.0), false, false, life, popAt, d -> {
                if (!(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                double s = 0.8 + Kit.bodyPad(e) * 0.5;
                JobFx.balloonPop(lv, b, s, 0.3, Opt.of("vel", RuneKit.entVel(e)), col[0], col[1]);
                JobFx.confetti(lv, b, s, 0.8, Opt.empty());
                JobFx.burst(lv, b, s * 0.8, col[0], col[1], 0.25, Opt.empty());
                SkillFx.play(lv, "entity.firework_rocket.blast", b, 1.7, 0.8);
                SkillFx.play(lv, "entity.firework_rocket.twinkle", b, 1.4, 0.5);
                tauntCue(lv, e);
            });
            RuneKit.later(popAt, () -> {
                if (RuneKit.sameLife(sp, life) && e.isAlive() && !e.isRemoved()) Aegis.tauntFor(sp, e, (int) k.num("taunt", 40), true);
            });
        }
        if (!foes.isEmpty()) RuneKit.later(popAt, () -> {
            if (RuneKit.sameLife(sp, life)) giggle(sp.level(), sp.position(), 1.4);
        });
        return true;
    }

    // ------------------------------------------------------------ 할루시네이트

    /**
     * 할루시네이트: 조명이 내리비추고 커다란 광대 인형 (크기 scale · 체력 = 내 최대 체력 × hp · life 틱) 이 겨눈 적 앞 (없으면 앞 2.5칸 — 화면 가운데를 가리지 않게 오른쪽으로
     * scale / 2 칸) 으로 떨어져 내려섬 (줄 인형 —
     * 끌어들임은 내려선 뒤) → 가까운 적에게 걸어가 섬 (PierrotSummons) → first 틱부터 every 틱마다 swings 번 커다란 뿅망치로 내리침 (동작은 HAMMER_AT 틱 전 — 인형 앞
     * reach 칸 둘레 radius 칸 swing). 인형이 부서지면 남은 뿅망치는 없음 (쇼타임 색종이 폭죽)
     */
    static boolean doll(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        LivingEntity aim = Kit.pickTarget(sp, 8, 30);
        double scale = k.num("scale", 2.0);
        Vec3 spot = beside(sp, spotFor(sp, aim, 1.0 + 0.45 * scale, 2.5, 0.9 + 0.45 * scale), 0.5 * scale);
        int first = Math.max(PierrotSummon.HAMMER_AT + 2, (int) k.num("first", 20));
        int every = Math.max(10, (int) k.num("every", 20));
        int swings = Math.max(1, (int) k.num("swings", 10));
        int life = Math.max(first + every * (swings - 1) + 12, (int) k.num("life", 200) + 12);
        String lifeTok = RuneKit.lifeToken(sp);
        // 한 번에 하나: 앞 인형은 펑
        for (PierrotSummons.Member m : PierrotSummons.of(sp, PierrotSummons.DOLL)) PierrotSummons.dismiss(m, true);
        float yaw = aim != null ? yawToward(spot, aim.position()) : yawToward(spot, V.add(spot, Kit.facingOf(sp), 2));
        PierrotSummons.Member m = PierrotSummons.spawn(sp, PierrotSummons.DOLL, spot, yaw, life, k.num("hp", 0.8), scale, P, PierrotSummons.DROP);
        if (m == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 인형이 설 자리가 없습니다", 30);
            return false;
        }
        m.x.playAnimation(PierrotSummon.DOLL_DROP);
        // 조명 · 떨어짐 · 내려섬
        JobFx.spotlight(level0, spot, 7, 1.2, Opt.empty(), GOLD, WHITE);
        SkillFx.play(level0, "block.note_block.bell", spot, 0.8, 0.8);
        SkillFx.play(level0, "entity.player.levelup", spot, 1.6, 0.5);
        RuneKit.later(PierrotSummons.DROP - 2, () -> {
            if (!m.alive()) return;
            ServerLevel lv = sp.level();
            appear(lv, m.x.position(), scale * 0.9);
            SkillFx.shock(lv, V.up(m.x.position(), 0.06), RED, scale * 0.9, 0.4);
            SkillFx.play(lv, "entity.player.attack.knockback", m.x.position(), 0.6, 1.0);
            giggle(lv, m.x.position(), 0.9);
        });
        double reach = k.num("reach", 2.2);
        double R = k.num("radius", 2);
        double mult = k.num("swing", 1.14);
        for (int i = 0; i < swings; i++) {
            int at = first + i * every;
            boolean last = i == swings - 1;
            RuneKit.later(at - PierrotSummon.HAMMER_AT, () -> {
                if (!m.alive() || !RuneKit.sameLife(sp, lifeTok)) return;
                m.holdUntil = JobKit.now() + PierrotSummon.HAMMER_AT + 4;
                m.x.getNavigation().stop();
                if (m.foe != null && m.foe.isAlive()) PierrotSummons.face(m.x, m.foe.position());
                m.x.playAnimation(PierrotSummon.DOLL_SWING);
                SkillFx.play(sp.level(), "entity.player.attack.sweep", m.x.position(), 0.7, 0.7);
            });
            RuneKit.later(at, () -> {
                if (!m.alive() || !RuneKit.sameLife(sp, lifeTok)) return;
                ServerLevel lv = sp.level();
                double yr = Math.toRadians(m.x.getYRot());
                Vec3 fw = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
                Vec3 hit = V.add(m.x.position(), fw, reach * Math.max(1, m.x.getScale()) / 2.0);
                Double gy = RuneKit.solidGround(lv, V.up(hit, 1.5), 4);
                Vec3 g = new Vec3(hit.x, gy != null ? gy : m.x.getY(), hit.z);
                // 뿅망치: 금빛 · 빨간 충격 고리 · 별 · 색종이 · 삑 소리
                SkillFx.shock(lv, V.up(g, 0.06), GOLD, R / 2.1, 0.4);
                SkillFx.shock(lv, V.up(g, 0.08), RED, R / 3.0, 0.3);
                JobFx.burst(lv, V.up(g, 0.4), 0.9, RED, GOLD, 0.25, Opt.empty());
                JobFx.confetti(lv, V.up(g, 0.6), last ? 1.0 : 0.55, 0.6, Opt.empty());
                SkillFx.sparks(lv, V.up(g, 0.3), GOLD, 6, 3);
                SkillFx.play(lv, "entity.slime.squish", g, 0.8, 1.0);
                SkillFx.play(lv, "block.note_block.basedrum", g, 0.7, 0.9);
                SkillFx.play(lv, "entity.chicken.egg", g, 1.8, 0.6);
                for (LivingEntity e : Kit.around(sp, g, R, 2.5)) {
                    JobKit.strike(sp, e, j, m.P, mult, false, last, lifeTok, 0, d -> {
                        if (e.level() instanceof ServerLevel elv) JobFx.hit(e, j, 1.0);
                    });
                }
            });
        }
        // 마지막에 인사하고 펑 (펑은 PierrotSummons — 시간이 다 됨)
        RuneKit.later(first + every * (swings - 1) + 6, () -> {
            if (m.alive()) m.x.playAnimation(PierrotSummon.DOLL_TAUNT);
        });
        return true;
    }

    // ------------------------------------------------------------ 그랜드 피날레

    /** 피날레의 거대한 상자 크기 */
    static final double BIG = 2.2;

    /** 거대한 상자가 설 자리: 둘레 ring 칸 (a 쪽) — 벽 · 막힌 곳이면 안쪽으로 (0.7 · 0.4 배), 그래도 막혔으면 null (그 상자는 빠짐) */
    private static Vec3 finaleSpot(ServerLevel level, Vec3 c, double a, double ring, double half) {
        for (double k : new double[] {1.0, 0.7, 0.4}) {
            Vec3 want = new Vec3(c.x + Math.cos(a) * ring * k, c.y, c.z + Math.sin(a) * ring * k);
            Double gy = RuneKit.solidGround(level, V.up(want, 2), 5);
            Vec3 p = new Vec3(want.x, gy != null ? gy : c.y, want.z);
            if (level.noCollision(new AABB(p.x - half, p.y + 0.05, p.z - half, p.x + half, p.y + 1.6, p.z + half))) return p;
        }
        return null;
    }

    /**
     * 그랜드 피날레: 서커스 하늘 (rpg:ult_circus) · 둘레를 비추는 조명 넷 → dropAt 틱 둘레 ring 칸에 거대한 깜짝 상자 boxes 개가 떨어짐 (크기 BIG · 체력 = 내 최대 체력 ×
     * hp — 벽이면 안쪽으로) → first 틱 활짝 열림 (용수철 광대 · 큰 색종이 — 둘레 몬스터를 끌어들임 · radius 칸 보스는 taunt 틱 나를 도발) → first 틱부터 every 틱마다 volleys 번 상자마다
     * 불꽃을 쏘아 올려 radius 칸 적에게 쏟아짐 (rocket — 적마다 한 번) → finaleAt 틱 상자들이 한꺼번에 터지며 대폭죽 (finale · 밀쳐냄 knock). 상자는 그때 사라짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c = sp.position();
        double R = k.num("radius", 10);
        int dropAt = Math.max(1, (int) k.num("dropAt", 4));
        int first = Math.max(dropAt + 4, (int) k.num("first", 10));
        int every = Math.max(10, (int) k.num("every", 10));
        int volleys = Math.max(1, (int) k.num("volleys", 8));
        int finaleAt = Math.max(first + every * (volleys - 1) + 10, (int) k.num("finaleAt", 90));
        int nBox = Math.max(1, (int) k.num("boxes", 4));
        double ring = k.num("ring", 3.5);
        String life = RuneKit.lifeToken(sp);
        Ult.sky(level0, c, "rpg:ult_circus", finaleAt + 30);
        SkillFx.play(level0, "block.note_block.bell", c, 0.6, 1.0);
        SkillFx.play(level0, "entity.player.levelup", c, 0.8, 0.8);
        giggle(level0, c, 1.0);
        RpgCombat.setSteadfast(sp, first);
        // 조명 넷: 둘레를 천천히 돌며 비춤
        Vec3 f = Kit.facingOf(sp);
        double base = Math.atan2(f.z, f.x);
        for (int t = 0; t < finaleAt + 6; t += 6) {
            int tt = t;
            RuneKit.later(t, () -> {
                ServerLevel lv = sp.level();
                for (int i = 0; i < 4; i++) {
                    double a = base + i * Math.PI / 2 + Math.PI / 4 + tt * 0.025;
                    double rr = ring + 1.5 + 1.5 * Math.sin(tt * 0.07 + i);
                    Vec3 p = new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr);
                    double[][] col = JobFx.CIRCUS[i % 3];
                    JobFx.spotlight(lv, new Vec3(p.x, RuneKit.groundAt(lv, p, c.y), p.z), 9, 0.4, Opt.of("alpha", 0.75), col[0], col[1]);
                }
            });
        }
        // 거대한 상자: 떨어짐 → 열림
        List<PierrotSummons.Member> boxes = new ArrayList<>();
        RuneKit.later(dropAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (int i = 0; i < nBox; i++) {
                double a = base + Math.PI / 4 + i * Math.PI * 2 / nBox;
                Vec3 at = finaleSpot(lv, c, a, ring, 0.45 * BIG);
                if (at == null) continue;
                PierrotSummons.Member m = PierrotSummons.spawn(sp, PierrotSummons.FINALE, at, yawToward(at, c) + 180F, finaleAt - dropAt + 2, k.num("hp", 0.4), BIG, P,
                        first - dropAt);
                if (m == null) continue;
                boxes.add(m);
                m.x.playAnimation(PierrotSummon.BOX_DROP);
                appear(lv, at, 2.0);
                SkillFx.shock(lv, V.up(at, 0.06), RED, 1.4, 0.4);
            }
            SkillFx.play(lv, "entity.player.attack.knockback", c, 0.5, 1.0);
            SkillFx.play(lv, "block.anvil.land", c, 1.5, 0.5);
        });
        RuneKit.later(first, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (PierrotSummons.Member m : boxes) {
                if (!m.alive()) continue;
                m.x.playAnimation(PierrotSummon.BOX_POP, "state", "0");
                Vec3 head = V.up(m.x.position(), 2.7);
                JobFx.confetti(lv, head, 1.4, 1.0, Opt.empty());
                JobFx.firework(lv, V.up(head, 1.5), 1.6, 0.8, Opt.empty(), JobFx.CIRCUS[boxes.indexOf(m) % 4][0], JobFx.CIRCUS[boxes.indexOf(m) % 4][1]);
            }
            SkillFx.play(lv, "block.chest.open", c, 0.7, 1.0);
            SkillFx.play(lv, "entity.firework_rocket.large_blast", c, 1.0, 1.0);
            giggle(lv, c, 1.2);
            // 보스는 나를 (몬스터 · 바닐라 몹은 상자가 끌어들임 — 쇼타임)
            for (LivingEntity e : Kit.around(sp, c, R, 4)) if (JobKit.isBoss(e)) Aegis.tauntFor(sp, e, (int) k.num("taunt", 80), true);
        });
        // 불꽃놀이: 상자마다 쏘아 올림 → 둘레 적에게 쏟아짐 (적마다 한 번)
        for (int v = 0; v < volleys; v++) {
            int at = first + v * every;
            int vi = v;
            RuneKit.later(at - 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                for (PierrotSummons.Member m : boxes) {
                    if (!m.alive()) continue;
                    m.x.playAnimation(PierrotSummon.BOX_FIRE);
                    double[][] col = JobFx.CIRCUS[(boxes.indexOf(m) + vi) % JobFx.CIRCUS.length];
                    Vec3 from = V.up(m.x.position(), 2.9);
                    JobFx.comet(lv, from, V.UP, 0.5, 40, 0.15, col[0], col[1], Opt.empty());
                    SkillFx.play(lv, "entity.firework_rocket.launch", from, 1.0 + Kit.rand() * 0.2, 0.6);
                }
            });
            JobKit.motionLater(sp, "pierrot_ult:cheer", at - 3, life);
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                List<LivingEntity> foes = Kit.around(sp, c, R, 4);
                int idx = 0;
                for (LivingEntity e : foes) {
                    double[][] col = JobFx.CIRCUS[(idx++ + vi) % JobFx.CIRCUS.length];
                    Vec3 over = V.up(RuneKit.overHead(e, 0.6), 1.2);
                    JobFx.firework(lv, over, 1.1 + Kit.bodyPad(e) * 0.4, 0.6, Opt.of("vel", RuneKit.entVel(e)), col[0], col[1]);
                    JobFx.comet(lv, over, V.DOWN, 0.45, 30, 0.1, col[0], col[1], Opt.empty());
                    JobKit.strike(sp, e, j, P, k.num("rocket", 2.5), false, false, life, 0, d -> {
                        if (e.level() instanceof ServerLevel elv) SkillFx.sparks(elv, Kit.bodyPoint(e), col[1], 4, 2.5);
                    });
                }
                if (foes.isEmpty()) {
                    // 적이 없으면 상자 위 하늘에서 터짐
                    for (PierrotSummons.Member m : boxes) {
                        if (!m.alive()) continue;
                        double[][] col = JobFx.CIRCUS[(boxes.indexOf(m) + vi) % JobFx.CIRCUS.length];
                        JobFx.firework(lv, V.up(m.x.position(), 6), 1.4, 0.7, Opt.empty(), col[0], col[1]);
                    }
                }
                SkillFx.play(lv, "entity.firework_rocket.blast", c, 1.0 + Kit.rand() * 0.3, 0.9);
                SkillFx.play(lv, "entity.firework_rocket.twinkle", c, 1.1, 0.6);
            });
        }
        // 대폭죽: 상자가 한꺼번에 터짐 — 큰 색종이 · 불꽃 고리 · 큰 충격 고리 · 밀쳐냄
        JobKit.motionLater(sp, "pierrot_ult:bow", finaleAt - 6, life);
        RuneKit.later(finaleAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (PierrotSummons.Member m : boxes) {
                if (!m.alive()) continue;
                Vec3 b = V.up(m.x.position(), 1.4);
                JobFx.confetti(lv, b, 2.0, 1.2, Opt.empty());
                JobFx.burst(lv, b, 2.2, RED, GOLD, 0.4, Opt.empty());
                for (int i = 0; i < 3; i++) {
                    double[][] col = JobFx.CIRCUS[(boxes.indexOf(m) + i * 2) % JobFx.CIRCUS.length];
                    JobFx.firework(lv, V.add(V.up(b, 2.5 + i * 1.2), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 1.5), 1.8, 0.9, Opt.empty(), col[0], col[1]);
                }
                PierrotSummons.dismiss(m, false);
            }
            SkillFx.shock(lv, V.up(c, 0.06), GOLD, R / 2.1, 0.6);
            SkillFx.shock(lv, V.up(c, 0.09), RED, R / 3.0, 0.5);
            SkillFx.play(lv, "entity.firework_rocket.large_blast", c, 0.8, 1.2);
            SkillFx.play(lv, "entity.generic.explode", c, 1.3, 0.8);
            SkillFx.play(lv, "entity.player.levelup", c, 1.2, 0.8);
            giggle(lv, c, 0.9);
            for (LivingEntity e : Kit.around(sp, c, R, 4)) {
                JobKit.strike(sp, e, j, P, k.num("finale", 18), false, true, life, 0, d -> {
                    if (!(e.level() instanceof ServerLevel elv)) return;
                    JobFx.hit(e, j, 1.4);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), c), k.num("knock", 0.8), 0.35);
                });
            }
        });
        return true;
    }
}
