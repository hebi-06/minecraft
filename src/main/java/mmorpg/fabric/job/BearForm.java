package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneMob;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vanilla.VanillaCosmetics;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-14 워로드 곰 변신 (사용자 직업 도감 메모 하이버네이트 · 그리즐리 크러시 · 베어 스톰프 · 카타클리즘 "곰으로 변해서 때리는걸로" — 드라군의 용 변신
 * {@link DragonForm} 과 같은 틀): 곰 스킬을 쓰는 동안 사람은 사라지고 (투명 · 손에 든 도끼도 — {@link VanillaCosmetics#hideHands}) 그 자리에 곰의 혼
 * (rpg:job_bear — 3D 몹, 호박빛 핏줄 · 등의 돌 판) 이 서서 스킬의 동작을 함 (웅크림 · 깨어남 · 일어섬 · 껴안기 · 내리찍기 · 포효 — 서버가 뼈를 움직이므로
 * 모드 없는 클라이언트에도 보임). 스킬이 끝나고 {@link #LINGER} 틱 뒤 사람으로 (그 사이 다른 곰 스킬을 쓰면 곰 그대로 — 이어 씀), 그 사이 평타 · 룬 · 대지 가르기
 * (사람의 도끼 스킬) 를 쓰면 바로 사람으로. 숫자 · 틱 · 범위는 스킬 그대로 (그림만).
 *
 * <p>곰 자리 = 사람 발에서 앞으로 {@link #AHEAD} × 크기 (1인칭 눈이 곰의 엉덩이 바로 위 — 곰을 탄 자리, 일어서면 가슴 · 머리가 눈앞) + 앞당김 (모드 · 모드 없는
 * 클라이언트 모두 다른 엔티티의 몸 자리를 3틱에 걸쳐 따라감 → 스킬 움직임 (JobMove — 베어 스톰프의 뛰어오름) 의 {@link #LEAD} 틱 뒤 자리).
 */
public final class BearForm {
    private BearForm() {}

    /** 변신한 곰 크기 (네 발로 선 등 높이 약 1.6칸 · 몸길이 약 2.7칸 · 일어서면 약 2.9칸) */
    static final double SCALE = 0.9;
    /** 스킬이 끝난 뒤 곰으로 남는 틱 (이어 쓸 틈) */
    static final int LINGER = 12;
    /** 곰 원점을 사람 발에서 보는 쪽으로 (크기 1 칸) — 1인칭 눈이 엉덩이 위 */
    static final double AHEAD = 0.65;
    /** 몸 자리 보간 (3틱) 만큼 앞당김 */
    private static final int LEAD = 3;
    /** 투명 시간 (변신하는 동안 120틱 아래로 내려가면 다시 — 끝나면 지움): 이보다 긴 투명 (물약 · 다른 것) 은 건드리지 않음 */
    private static final int INVIS = 200;
    /** 곰이 스스로 사라지는 틱 (변신이 정리되지 못했을 때의 안전) */
    private static final int MAX_LIFE = 1200;
    /** 생겨날 때 틱마다 크기 몫 (작게 → 조금 넘쳤다가 → 1) */
    private static final double[] GROW = {0.3, 0.6, 0.9, 1.06, 1.0};
    /** 흙먼지 · 호박빛 (변신 · 돌아옴의 그림) */
    private static final double[] DUST = JobTraits6.DUST;

    /** 스킬 하나의 변신 (begin 이 돌려줌 — 다음 스킬이 이어 쓰면 그 스킬의 것이 되어 앞 스킬의 남은 동작 · 끝은 무시됨) */
    record Use(RuneMob d, int id) {}

    private static final class Form {
        final RuneMob d;
        final String life;
        final double[] glow;
        int id;
        long endAt;
        boolean busy = true;
        Vec3 face;
        long faceUntil;
        Vec3 last;
        double scale = SCALE;

        Form(RuneMob d, String life, double[] glow) {
            this.d = d;
            this.life = life;
            this.glow = glow;
        }
    }

    private static final Map<UUID, Form> FORMS = new HashMap<>();
    private static int ids;
    private static MinecraftServer server;
    private static boolean ready;

    static void init() {
        if (ready) return;
        ready = true;
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            server = s;
            tick();
        });
        // 남은 틈의 평타 (휘두르기) · 룬: 바로 사람으로 (도끼를 휘두르거나 룬을 쓰는 것은 사람)
        java.util.function.BiConsumer<ServerPlayer, Weapon> sh = RpgCombat.swingHook;
        RpgCombat.swingHook = (sp, w) -> {
            interrupt(sp);
            sh.accept(sp, w);
        };
        java.util.function.BiConsumer<ServerPlayer, Runes.Rune> oc = RuneCast.onCast;
        RuneCast.onCast = (sp, r) -> {
            interrupt(sp);
            oc.accept(sp, r);
        };
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            Form f = FORMS.remove(h.player.getUUID());
            if (f == null) return;
            f.d.discard();
            MobEffectInstance inv = h.player.getEffect(MobEffects.INVISIBILITY);
            if (inv != null && !inv.isInfiniteDuration() && inv.getDuration() <= INVIS) h.player.removeEffect(MobEffects.INVISIBILITY);
        });
    }

    /**
     * 곰으로 (이미 곰이면 그 곰 그대로 — 끝을 미루고 이 스킬의 것으로): 사람을 숨기고 그 자리 땅에서 작게 솟아 4틱에 커짐 · 흙먼지 · 돌이 터짐 · 낮은 포효.
     * maxTicks = 이 스킬이 {@link #end} 를 부르지 못했을 때의 안전 끝. 못 세우면 null (사람 그대로 — 스킬은 예전 곰의 혼 · 2D 그림)
     */
    static Use begin(ServerPlayer sp, Jobs.Job j, int maxTicks) {
        UUID id = sp.getUUID();
        Form f = FORMS.get(id);
        String life = RuneKit.lifeToken(sp);
        long now = Tasks.now();
        if (f != null && f.d.alive() && f.life.equals(life) && f.d.level() == sp.level()) {
            f.id = ++ids;
            f.busy = true;
            f.endAt = now + Math.max(1, maxTicks);
            f.face = null;
            conceal(sp);
            Use u = new Use(f.d, f.id);
            if (Math.abs(f.scale - SCALE) > 1e-3) resize(sp, u, SCALE, 4); // 카타클리즘의 큰 곰 뒤에 이어 씀 — 제 크기로
            return u;
        }
        if (f != null) finish(sp, f, false, 0);
        ServerLevel level = sp.level();
        Vec3 at = sp.position();
        Vec3 face = Kit.facingOf(sp);
        RuneMob d = RuneMob.spawn(WarlordSkills.BEAR, level, V.add(at, V.flat(face), AHEAD * SCALE * GROW[0]), face, MAX_LIFE, SCALE * GROW[0], true);
        if (d == null) return null;
        Form nf = new Form(d, life, JobKit.glow(j));
        nf.id = ++ids;
        nf.endAt = now + Math.max(1, maxTicks);
        FORMS.put(id, nf);
        for (int i = 1; i < GROW.length; i++) {
            double g = GROW[i];
            Tasks.later(i, () -> {
                if (FORMS.get(id) == nf && d.alive()) d.size(nf.scale * g);
            });
        }
        conceal(sp);
        // 변신: 발밑 땅이 터지며 (흙먼지 · 돌 · 호박빛 불티 · 땅 고리) 곰이 솟아남 · 낮은 포효
        Vec3 c = V.up(at, 0.8);
        JobFx.burst(level, c, 1.6, j, 0.3, Opt.empty());
        JobFx.ring(level, V.up(at, 0.08), 2.2, j, false, 0.35, Opt.empty());
        RuneKit.rockBurst(level, V.up(at, 0.3), 8, 4.5, 0.2);
        SkillFx.sparks(level, V.up(at, 0.3), DUST, 16, 3.6);
        SkillFx.sparks(level, c, JobKit.glow(j), 12, 4.0);
        SkillFx.play(level, "entity.polar_bear.warning", at, 0.7, 0.9);
        SkillFx.play(level, "block.rooted_dirt.break", at, 0.8, 0.7);
        return new Use(d, nf.id);
    }

    /** 곰의 보는 쪽 (정해 둔 쪽 — 아니면 사람이 보는 쪽, 수평) */
    private static Vec3 facing(ServerPlayer sp) {
        Form f = FORMS.get(sp.getUUID());
        Vec3 dir = f != null && f.face != null && Tasks.now() < f.faceUntil ? f.face : Kit.facingOf(sp);
        Vec3 h = V.flat(dir);
        double len = Math.hypot(h.x, h.z);
        return len > 1e-4 ? new Vec3(h.x / len, 0, h.z / len) : Kit.facingOf(sp);
    }

    /**
     * 변신한 곰의 한 점 (크기 1 칸: 곰 원점에서 곰이 보는 쪽 fwd · 위 up) — 사람의 지금 발 자리 기준 (곰은 앞당겨 놓여 클라이언트에서 이 자리에 보임). 곰이 없으면
     * 사람 발에서 같은 셈 (크기 SCALE)
     */
    static Vec3 at(ServerPlayer sp, Use u, double fwd, double up) {
        double s = u != null && u.d().alive() ? u.d().scale() : SCALE;
        Vec3 h = facing(sp);
        return V.up(V.add(sp.position(), h, (AHEAD + fwd) * s), up * s);
    }

    /** 이 스킬이 아직 변신의 주인인지 (이어 쓴 다음 스킬이 가져가지 않았는지) — 곰이 살아 있을 때 */
    static boolean owns(ServerPlayer sp, Use u) {
        if (u == null) return false;
        Form f = FORMS.get(sp.getUUID());
        return f != null && f.id == u.id() && f.d == u.d() && f.d.alive();
    }

    /** 이 스킬의 곰 동작 (한 번) — 주인일 때만 */
    static void anim(ServerPlayer sp, Use u, String name) {
        if (owns(sp, u)) u.d().anim(name);
    }

    /** 이 스킬의 곰 동작 (끝 자세를 지킴 · 이어 도는 동작) — 주인일 때만 */
    static void hold(ServerPlayer sp, Use u, String name) {
        if (owns(sp, u)) u.d().hold(name);
    }

    /** at 틱 뒤 곰 동작 (hold = 끝 자세를 지킴) — 그때도 주인일 때만 */
    static void animLater(ServerPlayer sp, Use u, int at, String name, boolean hold) {
        if (u == null) return;
        Runnable r = () -> {
            if (hold) hold(sp, u, name);
            else anim(sp, u, name);
        };
        if (at <= 0) r.run();
        else Tasks.later(at, r);
    }

    /** ticks 틱 동안 곰이 dir 쪽을 봄 (아니면 사람이 보는 쪽) */
    static void face(ServerPlayer sp, Use u, Vec3 dir, int ticks) {
        Form f = FORMS.get(sp.getUUID());
        if (f == null || !owns(sp, u) || dir == null || Math.hypot(dir.x, dir.z) < 1e-4) return;
        f.face = V.flat(dir);
        f.faceUntil = Tasks.now() + Math.max(1, ticks);
    }

    /** 곰 크기를 ticks 틱에 걸쳐 scale 로 — 주인일 때만 */
    static void resize(ServerPlayer sp, Use u, double scale, int ticks) {
        Form f = FORMS.get(sp.getUUID());
        if (f == null || !owns(sp, u)) return;
        double from = u.d().scale();
        f.scale = scale;
        int n = Math.max(1, ticks);
        for (int q = 1; q <= n; q++) {
            double sz = from + (scale - from) * q / n;
            Tasks.later(q, () -> {
                if (owns(sp, u)) u.d().size(sz);
            });
        }
    }

    /** 이 스킬이 끝남: after 틱 뒤 사람으로 (그 사이 다음 곰 스킬이면 곰 그대로) — 주인일 때만 */
    static void end(ServerPlayer sp, Use u, int after) {
        Form f = FORMS.get(sp.getUUID());
        if (f == null || u == null || f.id != u.id()) return;
        f.busy = false;
        f.endAt = Tasks.now() + Math.max(0, after);
        f.face = null;
    }

    /** at 틱 뒤 end (그때 이 스킬이 주인이면) */
    static void endLater(ServerPlayer sp, Use u, int at, int after) {
        if (u == null) return;
        Tasks.later(Math.max(1, at), () -> end(sp, u, after));
    }

    /** 변신 중인지 (사람이 곰) */
    public static boolean active(ServerPlayer sp) {
        Form f = FORMS.get(sp.getUUID());
        return f != null && f.d.alive();
    }

    /** 바로 사람으로 (곰이 흩어짐 — 사람의 도끼 스킬을 쓸 때) */
    static void drop(ServerPlayer sp) {
        Form f = FORMS.get(sp.getUUID());
        if (f != null) finish(sp, f, true, 0);
    }

    /** 남은 틈이면 바로 사람으로 (평타 · 룬) */
    private static void interrupt(ServerPlayer sp) {
        Form f = FORMS.get(sp.getUUID());
        if (f != null && !f.busy) finish(sp, f, true, 0);
    }

    /** 사람으로: 곰은 흙먼지 · 호박빛 불티로 흩어지고 (흩어지는 동작) reveal 틱 뒤 사람이 보임 (그 사이 다시 변신하면 그대로 숨김) */
    private static void finish(ServerPlayer sp, Form f, boolean fx, int reveal) {
        FORMS.remove(sp == null ? null : sp.getUUID(), f);
        if (sp == null) FORMS.values().remove(f);
        RuneMob d = f.d;
        if (d.alive()) {
            if (fx) {
                ServerLevel level = d.level();
                Vec3 c = V.up(sp != null ? sp.position() : d.pos(), 0.9 * d.scale());
                JobFx.burst(level, c, 1.2, f.glow, DUST, 0.25, Opt.empty());
                SkillFx.sparks(level, c, f.glow, 12, 3.4);
                SkillFx.sparks(level, V.up(c, -0.6), DUST, 12, 2.8);
                SkillFx.play(level, "entity.polar_bear.ambient", c, 0.5, 0.8);
                d.leave("vanish", 10);
            } else d.discard();
        }
        if (sp == null) return;
        Runnable show = () -> {
            if (!FORMS.containsKey(sp.getUUID())) reveal(sp);
        };
        if (reveal <= 0) show.run();
        else Tasks.later(reveal, show);
    }

    /** 사람을 숨김: 투명 (알갱이 · 아이콘 없이) · 손에 든 것 · 뼈 몸 모션 */
    private static void conceal(ServerPlayer sp) {
        MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
        if (inv == null || (!inv.isInfiniteDuration() && inv.getDuration() < INVIS - 20)) {
            sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVIS, 0, false, false, false));
        }
        VanillaCosmetics.hideHands(sp, true);
        mmorpg.fabric.vanilla.VanillaRig.end(sp);
    }

    /** 사람이 다시 보임 (이 변신이 건 투명만 지움) */
    private static void reveal(ServerPlayer sp) {
        MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
        if (inv != null && !inv.isInfiniteDuration() && inv.getDuration() <= INVIS) sp.removeEffect(MobEffects.INVISIBILITY);
        VanillaCosmetics.hideHands(sp, false);
    }

    private static void tick() {
        if (FORMS.isEmpty()) return;
        long now = Tasks.now();
        for (Map.Entry<UUID, Form> e : new ArrayList<>(FORMS.entrySet())) {
            Form f = e.getValue();
            ServerPlayer sp = server == null ? null : server.getPlayerList().getPlayer(e.getKey());
            boolean gone = sp == null || sp.isRemoved() || !sp.isAlive() || sp.isSpectator() || !RuneKit.sameLife(sp, f.life);
            if (gone || !f.d.alive() || f.d.level() != sp.level()) {
                if (sp == null) FORMS.remove(e.getKey(), f);
                finish(sp, f, !gone && f.d.alive() && f.d.level() == sp.level(), 0);
                continue;
            }
            if (now >= f.endAt) {
                finish(sp, f, true, 3);
                continue;
            }
            Vec3 cur = sp.position();
            Vec3 p = JobMove.predict(sp, LEAD);
            if (p == null) {
                Vec3 v = f.last == null ? Vec3.ZERO : cur.subtract(f.last);
                p = v.lengthSqr() > 2.25 ? cur : cur.add(v.scale(LEAD));
            }
            f.last = cur;
            Vec3 face = f.face != null && now < f.faceUntil ? f.face : Kit.facingOf(sp);
            Vec3 h = V.flat(face);
            double len = Math.hypot(h.x, h.z);
            h = len > 1e-4 ? new Vec3(h.x / len, 0, h.z / len) : Kit.facingOf(sp);
            f.d.place(V.add(p, h, AHEAD * f.d.scale()));
            f.d.face(face);
            MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
            if (inv == null || (!inv.isInfiniteDuration() && inv.getDuration() < 120)) conceal(sp);
        }
    }
}
