package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.TyrfingWarrior;
import mmorpg.fabric.rune.RuneKit;
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
 * 2026-11-07 정령 궁수 (궁수 1차 재설계안 3판 — claude/job-advance-redo.md "2026-11-07 도감 표시 고침") 와일드 헌트 (archer_focus — 이글 아이 대신) 의 늑대 정령 둘:
 * 진짜 몹 (rpg:spirit_wolf — 서리 늑대 모델 · 정령빛, TyrfingWarrior: 맞지 않음 · 아무도 노리지 않음 · 스킬 대상이 아님 · 저장하지 않음). 스스로 생각하지 않고 이 틀이 틱마다
 * 움직임.
 * <ul>
 * <li>깨어남: 주인 양옆 뒤에 정령빛이 감겨 들며 늑대가 서고 울부짖음 (howl)</li>
 * <li>따름: 주인 왼쪽 · 오른쪽 뒤 (FOLLOW 칸) 를 길 찾기로 달림 — 멀어지면 (WARP 칸) 곁으로 옮겨 옴, 서 있으면 주인이 보는 쪽을 봄</li>
 * <li>물어뜯음: 주인의 활 평타가 겨눈 적을 맞히면 ({@link #bite}) 쉬는 늑대 (없으면 먼저 쉬게 될 늑대) 가 LEAP 틱 동안 그 적 앞으로 뛰어올라 (포물선) 물어뜯음 —
 * 피해는 맞힌 틱 + LEAP 에 평타 × bite (RpgCombat.bonusHit — 치명타 · 더한 피해까지 평타 그대로), 물어뜯는 동작 · 금빛 이빨 자국 · 으르렁 → HOLD 틱 뒤 곁으로</li>
 * <li>끝: 태세가 끝나거나 주인이 죽음 · 차원을 옮김 · 직업이 바뀜 · 나감 → 정령빛 · 잎으로 흩어짐</li>
 * </ul>
 */
final class SpiritWolves {
    private SpiritWolves() {}

    /** 뛰어올라 물어뜯기까지 (틱 — 피해가 들어가는 때: 화살이 맞힌 틱 + LEAP) · 물어뜯은 뒤 그 자리에 머무는 틱 */
    static final int LEAP = 6;
    static final int HOLD = 8;
    /** 주인 곁 자리 (보는 쪽에서 왼쪽 · 오른쪽 뒤 각 · 거리) · 이만큼 멀면 곁으로 옮겨 옴 */
    static final double[] SIDE_ANG = {125, -125};
    static final double FOLLOW = 1.9;
    static final double WARP = 18;
    static final double RUN = 1.45;
    /** 뛰어오르는 높이 (칸) · 적 몸 겉에서 이만큼 앞에 내려섬 */
    static final double HOP = 1.1;
    static final double REACH = 0.7;
    /** 정령빛 (늑대 몸빛 — 연두) · 금빛 */
    static final double[] SPIRIT = {0.55, 1.0, 0.42};
    static final double[] GOLD = {1.0, 0.86, 0.36};

    static final class Wolf {
        final TyrfingWarrior x;
        final int slot;
        /** 뛰어오름: 시작 틱 (−1 = 아님) · 시작 자리 · 노리는 적 */
        long leapAt = -1;
        Vec3 from;
        LivingEntity foe;
        /** 이 틱까지 물어뜯은 자리에 머묾 */
        long holdUntil;
        long pathAt;

        Wolf(TyrfingWarrior x, int slot) {
            this.x = x;
            this.slot = slot;
        }

        boolean free(long now) {
            return leapAt < 0 && now >= holdUntil;
        }
    }

    static final class Pack {
        final String life;
        final long until;
        final List<Wolf> wolves = new ArrayList<>();
        int next;

        Pack(String life, long until) {
            this.life = life;
            this.until = until;
        }
    }

    private static final Map<UUID, Pack> PACKS = new HashMap<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SpiritWolves::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PACKS.clear());
    }

    /** 늑대 정령 n 마리를 깨움 (ticks 틱 — 앞 무리는 흩어짐) */
    static void summon(ServerPlayer sp, Jobs.Job j, int n, int ticks) {
        dismiss(sp, true);
        ServerLevel level = sp.level();
        Pack p = new Pack(RuneKit.lifeToken(sp), Tasks.now() + ticks);
        Vec3 f = Kit.facingOf(sp);
        float yaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
        for (int i = 0; i < n; i++) {
            Vec3 spot = NecroCrew.standAt(level, V.add(sp.position(), V.turnY(f, SIDE_ANG[i % SIDE_ANG.length]), FOLLOW));
            TyrfingWarrior x = TyrfingWarrior.spawnAs(TyrfingWarrior.WOLF_ID, level, spot, yaw, ticks + 60);
            if (x == null) continue;
            x.playAnimation(TyrfingWarrior.WOLF_HOWL);
            p.wolves.add(new Wolf(x, i));
            awaken(level, spot, j);
        }
        if (p.wolves.isEmpty()) return;
        PACKS.put(sp.getUUID(), p);
        SkillFx.play(level, "item.goat_horn.sound.1", sp.position(), 1.6, 0.5); // 울부짖음 (26.3 에는 늑대 울음소리가 없어 뿔피리 — 몬스터 늑대와 같음)
        SkillFx.play(level, "entity.wolf.growl", sp.position(), 1.3, 0.6);
        SkillFx.play(level, "block.amethyst_block.resonate", sp.position(), 1.4, 0.6);
    }

    /** 깨어나는 자리: 정령빛이 감겨 듦 · 잎 · 땅의 빛 고리 */
    private static void awaken(ServerLevel level, Vec3 at, Jobs.Job j) {
        JobFx.ring(level, V.up(at, 0.06), 1.1, j, false, 0.5, Opt.of("alpha", 0.85));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            Vec3 p0 = new Vec3(at.x + Math.cos(a) * 1.6, at.y + 0.3 + (k % 2) * 0.5, at.z + Math.sin(a) * 1.6);
            Vec3 d = V.sub(V.up(at, 0.7), p0);
            SkillFx.fx(level, "rpg:arrow_spark", p0, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.3, "size", 0.14, "grow", 0.5, "life", 0.3, "color", GOLD, "alpha",
                    1.0));
        }
        SkillFx.sparks(level, V.up(at, 0.7), SPIRIT, 10, 3.0);
    }

    /** 늑대들이 정령빛으로 흩어짐 */
    static void dismiss(ServerPlayer sp, boolean fx) {
        Pack p = PACKS.remove(sp.getUUID());
        if (p == null) return;
        for (Wolf w : p.wolves) vanish(w, fx);
    }

    private static void vanish(Wolf w, boolean fx) {
        if (w.x.isRemoved()) return;
        if (fx && w.x.level() instanceof ServerLevel level) {
            Vec3 at = V.up(w.x.position(), 0.6);
            SkillFx.sparks(level, at, SPIRIT, 12, 4.0);
            SkillFx.sparks(level, at, GOLD, 6, 3.0);
            for (int k = 0; k < 6; k++) {
                Vec3 d = new Vec3(Kit.rand() - 0.5, 0.6 + Kit.rand() * 0.6, Kit.rand() - 0.5);
                SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", V.unit(d), "speed", 2.5, "size", 0.12, "grow", 0.4, "life", 0.5, "color", SPIRIT, "alpha", 0.9));
            }
            SkillFx.play(level, "block.amethyst_block.break", at, 1.6, 0.5);
        }
        w.x.discard();
    }

    /** 무리가 있는지 (시험 · 화면) */
    static boolean on(ServerPlayer sp) {
        Pack p = PACKS.get(sp.getUUID());
        return p != null && !p.wolves.isEmpty();
    }

    /** 시험: 늑대 몸들 */
    static List<TyrfingWarrior> bodies(ServerPlayer sp) {
        Pack p = PACKS.get(sp.getUUID());
        List<TyrfingWarrior> out = new ArrayList<>();
        if (p != null) for (Wolf w : p.wolves) if (!w.x.isRemoved()) out.add(w.x);
        return out;
    }

    /**
     * 활 평타가 겨눈 적을 맞힘 → 쉬는 늑대 (없으면 먼저 쉬게 될 늑대 — 하던 것을 그만두고) 가 그 적에게 뛰어올라 물어뜯음 (LEAP 틱 뒤 평타 × ratio). 무리가 없으면 false
     */
    static boolean bite(ServerPlayer sp, Jobs.Job j, LivingEntity foe, Weapon w, double ratio) {
        Pack p = PACKS.get(sp.getUUID());
        if (p == null || foe == null || w == null) return false;
        long now = Tasks.now();
        Wolf pick = null;
        for (int i = 0; i < p.wolves.size(); i++) {
            Wolf c = p.wolves.get((p.next + i) % p.wolves.size());
            if (!c.x.isRemoved() && c.free(now)) {
                pick = c;
                break;
            }
        }
        if (pick == null) {
            for (Wolf c : p.wolves) {
                if (c.x.isRemoved()) continue;
                if (pick == null || Math.max(c.leapAt + LEAP, c.holdUntil) < Math.max(pick.leapAt + LEAP, pick.holdUntil)) pick = c;
            }
        }
        if (pick == null) return false;
        p.next = (p.wolves.indexOf(pick) + 1) % p.wolves.size();
        Wolf wolf = pick;
        wolf.leapAt = now;
        wolf.from = wolf.x.position();
        wolf.foe = foe;
        wolf.x.getNavigation().stop();
        SkillFx.play(sp.level(), "entity.wolf.growl", wolf.from, 1.3, 0.8);
        String life = p.life;
        RuneKit.later(LEAP, () -> {
            if (!RuneKit.sameLife(sp, life) || foe.isRemoved() || !foe.isAlive()) return;
            if (!(foe.level() instanceof ServerLevel lv)) return;
            RpgCombat.bonusHit(sp, foe, w, ratio);
            // 금빛 이빨 자국 (위아래 두 초승달이 맞물림) · 정령빛 불티 · 물어뜯는 소리
            Vec3 at = Kit.bodyPoint(foe);
            Vec3 d = V.flat(V.sub(foe.position(), wolf.x.position()));
            JobFx.hit(foe, j, 1.2);
            Vec3 side = V.rightOf(d);
            for (int s : new int[] {-1, 1}) {
                Vec3 c = V.add(at, V.UP, s * 0.18);
                SkillFx.fx(lv, "rpg:arrow_spark", V.add(c, side, -0.35), Opt.of("dir", side, "speed", 4.0, "size", 0.16, "grow", 0.4, "life", 0.18, "color", GOLD,
                        "alpha", 1.0));
            }
            SkillFx.sparks(lv, at, SPIRIT, 8, 3.5);
            SkillFx.sparks(lv, at, GOLD, 5, 2.5);
            SkillFx.play(lv, "entity.wolf.ambient", at, 0.9, 0.55);
            SkillFx.play(lv, "entity.player.attack.strong", at, 1.2, 0.6);
        });
        return true;
    }

    // ------------------------------------------------------------ 틱

    private static void tick(MinecraftServer server) {
        if (PACKS.isEmpty()) return;
        long now = Tasks.now();
        for (UUID id : new ArrayList<>(PACKS.keySet())) {
            Pack p = PACKS.get(id);
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            Jobs.Job j = sp == null ? null : JobSystem.job(sp);
            if (sp == null || !RuneKit.sameLife(sp, p.life) || j == null || !"archer".equals(j.id()) || now >= p.until || !JobTraits.stanceOn(sp, "archer_focus")) {
                PACKS.remove(id);
                for (Wolf w : p.wolves) vanish(w, sp != null);
                continue;
            }
            p.wolves.removeIf(w -> w.x.isRemoved());
            for (Wolf w : p.wolves) {
                if (w.x.level() != sp.level()) {
                    w.x.discard();
                    continue;
                }
                drive(sp, j, w, now);
            }
            p.wolves.removeIf(w -> w.x.isRemoved());
            if (p.wolves.isEmpty()) PACKS.remove(id);
        }
    }

    /** 늑대 하나 한 틱: 뛰어오름 (포물선 — 마지막 틱에 물어뜯는 동작) → 머묾 → 주인 곁으로 */
    private static void drive(ServerPlayer sp, Jobs.Job j, Wolf w, long now) {
        TyrfingWarrior x = w.x;
        ServerLevel level = sp.level();
        if (w.leapAt >= 0) {
            LivingEntity e = w.foe;
            if (e == null || e.isRemoved() || !e.isAlive() || e.level() != level) {
                w.leapAt = -1;
                return;
            }
            double u = Math.min(1.0, (now - w.leapAt) / (double) LEAP);
            Vec3 ep = e.position();
            Vec3 away = V.sub(w.from, ep);
            Vec3 dir = Math.hypot(away.x, away.z) > 1e-3 ? V.flat(away) : V.scale(Kit.facingOf(sp), -1);
            Vec3 land = V.add(ep, dir, Kit.bodyPad(e) + REACH);
            Vec3 p = V.lerp(w.from, new Vec3(land.x, Math.max(land.y, ep.y), land.z), u);
            p = V.up(p, 4 * HOP * u * (1 - u));
            x.setPos(p.x, p.y, p.z);
            x.setDeltaMovement(Vec3.ZERO);
            float yaw = (float) Math.toDegrees(Math.atan2(-(ep.x - p.x), ep.z - p.z));
            x.setYRot(yaw);
            x.setYBodyRot(yaw);
            x.setYHeadRot(yaw);
            if (u <= 0.34 && (now - w.leapAt) % 2 == 0 && RuneKit.fxQuota("spiritWolf", 6)) {
                SkillFx.fx(level, "rpg:arrow_spark", V.up(p, 0.5), Opt.of("dir", V.unit(V.sub(ep, p)), "speed", 1.5, "size", 0.12, "grow", 0.4, "life", 0.3, "color",
                        SPIRIT, "alpha", 0.8));
            }
            if (now - w.leapAt == LEAP - 3) x.playAnimation(TyrfingWarrior.WOLF_BITE);
            if (u >= 1.0) {
                w.leapAt = -1;
                w.holdUntil = now + HOLD;
            }
            return;
        }
        if (now < w.holdUntil) {
            x.getNavigation().stop();
            return;
        }
        follow(sp, w, now);
        // 달리는 발밑 정령빛 (8틱마다 — 늑대마다 엇갈려)
        if ((now + w.slot * 4) % 8 == 0 && x.getDeltaMovement().horizontalDistance() > 0.08 && RuneKit.fxQuota("spiritWolfStep", 6)) {
            SkillFx.sparks(level, V.up(x.position(), 0.2), SPIRIT, 2, 1.0);
        }
    }

    private static void follow(ServerPlayer sp, Wolf w, long now) {
        TyrfingWarrior x = w.x;
        Vec3 f = Kit.facingOf(sp);
        Vec3 spot = V.add(sp.position(), V.turnY(f, SIDE_ANG[w.slot % SIDE_ANG.length]), FOLLOW);
        double d = V.hdist(x.position(), sp.position());
        if (d > WARP || Math.abs(x.getY() - sp.getY()) > 8) {
            Vec3 g = NecroCrew.standAt(sp.level(), spot);
            SkillFx.sparks(sp.level(), V.up(x.position(), 0.6), SPIRIT, 8, 3.0);
            x.getNavigation().stop();
            x.teleportTo(g.x, g.y, g.z);
            SkillFx.sparks(sp.level(), V.up(g, 0.6), SPIRIT, 8, 3.0);
            return;
        }
        if (V.hdist(x.position(), spot) > 1.1) {
            if (now - w.pathAt >= 4) w.pathAt = x.getNavigation().moveTo(spot.x, spot.y, spot.z, d > 5 ? RUN * 1.25 : RUN) ? now : now - 2;
        } else {
            x.getNavigation().stop();
            Vec3 lp = V.add(sp.position(), f, 8);
            x.getLookControl().setLookAt(lp.x, sp.getEyeY(), lp.z);
        }
    }
}
