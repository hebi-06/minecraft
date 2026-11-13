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
 * 2026-11-13 드라군 변신 (사용자 "이게 모션도 없고 그냥 스킬만 써서 그런지 몰라도 예전이 낫은데 드라군은 드래곤으로 변해서 공격하는게 더 간지날듯 사람 빼고
 * 드레곤으로 변해서 공격하는걸로"): 드라군 스킬을 쓰는 동안 사람은 사라지고 (투명 · 손에 든 창도 — {@link VanillaCosmetics#hideHands}) 그 발 자리에 붉은 용
 * (rpg:job_dragon — 3D 몹) 이 서서 스킬의 동작을 함 (물기 · 꼬리 휩쓸기 · 뛰어오름 · 내리꽂기 · 불길 — 동작은 서버가 뼈를 움직이므로 모드 없는
 * 클라이언트에도 보임). 스킬이 끝나고 {@link #LINGER} 틱 뒤 사람으로 (그 사이 다른 드라군 스킬을 쓰면 용 그대로 — 이어 씀), 그 사이 평타 · 룬을 쓰면 바로
 * 사람으로. 숫자 · 틱 · 범위는 스킬 그대로 (그림만).
 *
 * <p>용 자리 = 사람 발 + 앞당김: 모드 · 모드 없는 클라이언트 모두 다른 엔티티의 몸 자리를 3틱에 걸쳐 따라감 → 스킬 움직임 (JobMove) 중이면 그 움직임의
 * {@link #LEAD} 틱 뒤 자리, 아니면 지난 틱 속도로 그만큼 앞. 그래서 그림 (불꽃 · 땅 고리) 은 사람의 지금 자리에 내면 용과 맞음.
 */
public final class DragonForm {
    private DragonForm() {}

    /**
     * 변신한 용 크기 (날개 끝 사이 약 6.2칸 · 등 높이 약 1.45칸 — 1인칭 눈 (1.62) 이 등 바로 위, 앞에 목 · 머리 · 양옆에 날개). 영상 1 (0.9): 과녁 (드라우그) 과
     * 키가 비슷해 묻혀 보였고 물기 · 꼬리가 앞 과녁에 닿지 않았음
     */
    static final double SCALE = 1.15;
    /** 스킬이 끝난 뒤 용으로 남는 틱 (이어 쓸 틈) */
    static final int LINGER = 12;
    /** 몸 자리 보간 (3틱) 만큼 앞당김 */
    private static final int LEAD = 3;
    /** 투명 시간 (변신하는 동안 120틱 아래로 내려가면 다시 — 끝나면 지움): 이보다 긴 투명 (물약 · 다른 것) 은 건드리지 않음 */
    private static final int INVIS = 200;
    /** 용이 스스로 사라지는 틱 (변신이 정리되지 못했을 때의 안전) */
    private static final int MAX_LIFE = 1200;
    /** 생겨날 때 틱마다 크기 몫 (작게 → 조금 넘쳤다가 → 1) */
    private static final double[] GROW = {0.25, 0.55, 0.85, 1.06, 1.0};
    /** 쉬는 자세의 발바닥 높이 (크기 1 칸 — 모델 원점 위): 용을 이만큼 (× 크기) 내려 놓아 땅에 섬 (1인칭 눈이 등 위 약 0.5칸 — 탄 사람 높이) */
    static final double FOOT = 0.36;

    /** 스킬 하나의 변신 (begin 이 돌려줌 — 다음 스킬이 이어 쓰면 그 스킬의 것이 되어 앞 스킬의 남은 동작 · 끝은 무시됨) */
    record Use(RuneMob d, int id) {}

    private static final class Form {
        final RuneMob d;
        final String life;
        int id;
        /** 사람으로 돌아갈 틱 (스킬이 정한 끝 · 안전 끝) */
        long endAt;
        /** 스킬 중 (아니면 남은 틈 — 평타 · 룬이 바로 끝냄) */
        boolean busy = true;
        Vec3 face;
        long faceUntil;
        Vec3 last;
        /** 목표 크기 (생겨남 · 드래곤폴이 키움) */
        double scale = SCALE;

        Form(RuneMob d, String life) {
            this.d = d;
            this.life = life;
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
        // 남은 틈의 평타 (휘두르기) · 룬: 바로 사람으로 (창을 휘두르거나 룬을 쓰는 것은 사람)
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
        // 나감: 용을 지우고 이 변신이 건 투명을 지움 (손 숨김은 VanillaCosmetics 가 나갈 때 지움)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            Form f = FORMS.remove(h.player.getUUID());
            if (f == null) return;
            f.d.discard();
            MobEffectInstance inv = h.player.getEffect(MobEffects.INVISIBILITY);
            if (inv != null && !inv.isInfiniteDuration() && inv.getDuration() <= INVIS) h.player.removeEffect(MobEffects.INVISIBILITY);
        });
    }

    /**
     * 용으로 (이미 용이면 그 용 그대로 — 끝을 미루고 이 스킬의 것으로): 사람을 숨기고 발 자리에 작게 생겨나 4틱에 커짐 · 몸에서 불꽃이 터짐. maxTicks = 이 스킬이
     * {@link #end} 를 부르지 못했을 때의 안전 끝. 못 세우면 null (사람 그대로 — 스킬은 예전 2D 그림)
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
            if (Math.abs(f.scale - SCALE) > 1e-3) resize(sp, u, SCALE, 4); // 드래곤폴의 큰 용 뒤에 이어 씀 — 제 크기로
            return u;
        }
        if (f != null) finish(sp, f, false, 0);
        ServerLevel level = sp.level();
        Vec3 at = sp.position();
        RuneMob d = RuneMob.spawn(DragoonSkills.DRAGON, level, V.up(at, -FOOT * SCALE * GROW[0]), Kit.facingOf(sp), MAX_LIFE, SCALE * GROW[0], true);
        if (d == null) return null;
        Form nf = new Form(d, life);
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
        // 변신: 몸에서 불꽃이 터지며 (불꽃 덩이 · 불티 · 발밑 불꽃 고리) 용이 솟아남 · 날갯짓 · 낮은 포효
        Vec3 c = V.up(at, 1.0);
        JobFx.burst(level, c, 1.7, DragoonSkills.FIRE_GLOW, DragoonSkills.FIRE_CORE, 0.3, Opt.empty());
        JobFx.fire(level, c, 1.5, 0.45, Opt.of("vel", new Vec3(0, 1.4, 0), "spin", 0.0), DragoonSkills.FIRE_GLOW, DragoonSkills.FIRE_CORE);
        JobFx.ring(level, V.up(at, 0.08), 2.0, j, false, 0.35, Opt.empty());
        SkillFx.sparks(level, c, DragoonSkills.FIRE_GLOW, 18, 4.2);
        SkillFx.play(level, "entity.ender_dragon.flap", at, 1.0, 1.0);
        SkillFx.play(level, "entity.blaze.shoot", at, 0.6, 0.6);
        SkillFx.play(level, "entity.ender_dragon.growl", at, 0.35, 1.3);
        return new Use(d, nf.id);
    }

    /**
     * 변신한 용의 한 점 (입 · 몸 — tools/job_mobs_art.py 동작의 뼈를 센 표, 크기 1 칸: 용 원점에서 용이 보는 쪽 fwd · 위 up): 사람의 지금 발 자리 기준
     * (용은 앞당겨 놓여 클라이언트에서 이 자리에 보임). 용이 없으면 사람 발에서 보는 쪽 · 위 (크기 SCALE)
     */
    static Vec3 at(ServerPlayer sp, Use u, double fwd, double up) {
        Form f = FORMS.get(sp.getUUID());
        double s = u != null && u.d().alive() ? u.d().scale() : SCALE;
        Vec3 dir = f != null && f.face != null && Tasks.now() < f.faceUntil ? f.face : Kit.facingOf(sp);
        Vec3 h = V.flat(dir);
        double len = Math.hypot(h.x, h.z);
        h = len > 1e-4 ? new Vec3(h.x / len, 0, h.z / len) : Kit.facingOf(sp);
        return V.up(V.add(sp.position(), h, fwd * s), (up - FOOT) * s);
    }

    /** 이 스킬이 아직 변신의 주인인지 (이어 쓴 다음 스킬이 가져가지 않았는지) — 용이 살아 있을 때 */
    static boolean owns(ServerPlayer sp, Use u) {
        if (u == null) return false;
        Form f = FORMS.get(sp.getUUID());
        return f != null && f.id == u.id() && f.d == u.d() && f.d.alive();
    }

    /** 이 스킬의 용 동작 (한 번) — 주인일 때만 */
    static void anim(ServerPlayer sp, Use u, String name) {
        if (owns(sp, u)) u.d().anim(name);
    }

    /** 이 스킬의 용 동작 (끝 자세를 지킴 · 이어 도는 동작) — 주인일 때만 */
    static void hold(ServerPlayer sp, Use u, String name) {
        if (owns(sp, u)) u.d().hold(name);
    }

    /** ticks 틱 동안 용이 dir 쪽을 봄 (아니면 사람이 보는 쪽) */
    static void face(ServerPlayer sp, Use u, Vec3 dir, int ticks) {
        Form f = FORMS.get(sp.getUUID());
        if (f == null || !owns(sp, u) || dir == null || Math.hypot(dir.x, dir.z) < 1e-4) return;
        f.face = V.flat(dir);
        f.faceUntil = Tasks.now() + Math.max(1, ticks);
    }

    /** 용 크기를 ticks 틱에 걸쳐 scale (SCALE 의 몇 배가 아니라 모델 크기) 로 — 주인일 때만 */
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

    /** 이 스킬이 끝남: after 틱 뒤 사람으로 (그 사이 다음 드라군 스킬이면 용 그대로) — 주인일 때만 */
    static void end(ServerPlayer sp, Use u, int after) {
        Form f = FORMS.get(sp.getUUID());
        if (f == null || u == null || f.id != u.id()) return;
        f.busy = false;
        f.endAt = Tasks.now() + Math.max(0, after);
        f.face = null;
    }

    /** 변신 중인지 (사람이 용) */
    public static boolean active(ServerPlayer sp) {
        Form f = FORMS.get(sp.getUUID());
        return f != null && f.d.alive();
    }

    /** 남은 틈이면 바로 사람으로 (평타 · 룬) */
    private static void interrupt(ServerPlayer sp) {
        Form f = FORMS.get(sp.getUUID());
        if (f != null && !f.busy) finish(sp, f, true, 0);
    }

    /** 사람으로: 용은 불티로 흩어지고 (흩어지는 동작 · 불꽃) reveal 틱 뒤 사람이 보임 (그 사이 다시 변신하면 그대로 숨김) */
    private static void finish(ServerPlayer sp, Form f, boolean fx, int reveal) {
        FORMS.remove(sp == null ? null : sp.getUUID(), f);
        if (sp == null) FORMS.values().remove(f);
        RuneMob d = f.d;
        if (d.alive()) {
            if (fx) {
                ServerLevel level = d.level();
                Vec3 c = V.up(sp != null ? sp.position() : d.pos(), 1.1 * d.scale());
                JobFx.fire(level, c, 1.3 * d.scale(), 0.45, Opt.of("vel", new Vec3(0, 1.8, 0)), DragoonSkills.FIRE_GLOW, DragoonSkills.FIRE_CORE);
                JobFx.burst(level, c, 1.1, DragoonSkills.FIRE_GLOW, DragoonSkills.FIRE_CORE, 0.25, Opt.empty());
                SkillFx.sparks(level, c, DragoonSkills.FIRE_GLOW, 14, 3.6);
                SkillFx.play(level, "entity.blaze.shoot", c, 0.45, 0.8);
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

    /** 사람을 숨김: 투명 (알갱이 · 아이콘 없이) · 손에 든 것 */
    private static void conceal(ServerPlayer sp) {
        MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
        if (inv == null || (!inv.isInfiniteDuration() && inv.getDuration() < INVIS - 20)) {
            sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, INVIS, 0, false, false, false));
        }
        VanillaCosmetics.hideHands(sp, true);
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
            f.d.place(V.up(p, -FOOT * f.d.scale()));
            f.d.face(f.face != null && now < f.faceUntil ? f.face : Kit.facingOf(sp));
            // 투명을 늘 100틱 넘게 (몬스터는 100틱 이하의 짧은 투명을 룬 은신으로 보고 멀리서 못 찾음 — Monsters.hidden: 변신은 숨는 스킬이 아님)
            MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
            if (inv == null || (!inv.isInfiniteDuration() && inv.getDuration() < 120)) conceal(sp);
        }
    }
}
