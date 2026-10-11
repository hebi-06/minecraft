package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 정령사 룬 (runes.js v2.14.0 "정령사" — 마력: 정령을 불러 몇 초 동안 대신 싸우게 함): 불의 정령 · 물의 정령 · 바람의 정령 · 땅의 정령 · 정령왕의 부름
 * (다섯 등급 모두 summonSpirits). 정령 = 엔티티가 아니라 2틱마다 움직이고 파티클 (rpg:rfx_spirit) 로 그리는 빛 (RUNE_SPIRITS).
 * 내가 보는 적부터 노리고, 없으면 가장 오래 안 때린 적
 */
final class RuneSpirit {
    private RuneSpirit() {}

    private static final Obj SPIRIT_DEFS = Rules.get().obj("RUNE_SPIRITS");
    /** 싸운 것으로 치는 시간 (틱) */
    private static final int FOUGHT_TICKS = 200;
    /** 몬스터 무리지만 먼저 건드리지 않으면 싸우지 않는 몹 (정령이 스스로 노리지 않음 — 자바 이름) */
    private static final Set<String> NEUTRAL_MONSTERS = Set.of(Kit.javaType("minecraft:enderman"), Kit.javaType("minecraft:zombie_pigman"),
            Kit.javaType("minecraft:piglin"));
    /** 정령 자리 {가로 h 도 (− 왼쪽), 세로 v 도}: 보는 방향 (위아래 포함) 기준 — 위아래를 봐도 1인칭 화면 위쪽 양옆 (폰 세로 시야 ±30° 안) */
    private static final double[][] SPIRIT_SLOTS = {{-20, 12}, {20, 12}, {-32, 3}, {32, 3}, {-9, 21}, {9, 21}, {-33, 18}, {33, 18}};
    /** 눈에서 정령까지 (칸). 벽이 더 가까우면 벽 앞 */
    private static final double SPIRIT_DIST = 2.2;
    /** 눈이 한 번 (2틱) 에 이보다 멀리 옮겨지면 순간 이동으로 봄 (바람의 정령 돌진도 멈추고 같이 옮김) */
    private static final double SPIRIT_TELEPORT = 8;
    /** 정령이 노리지 않는 엔티티 표시 (허수아비 킹이 분신 사이에 숨은 동안: 정령이 진짜를 찾아 주지 않게 — 보스가 붙임) */
    static final String NO_AUTO_TAG = "rpg_no_auto";
    private static final double DEG = Math.PI / 180;

    /** 정령 하나 (spirits 의 항목 — 엔티티가 아님) */
    static final class Spirit {
        final String kind;
        final Obj def;
        final double[] glow;
        final double[] core;
        /** 그리는 차원 (차원 이동으로 흩어질 때 떠난 쪽에 그리려고 — 삶 표시가 같을 때만 바꿈) */
        ServerLevel level;
        final double P;
        /** 룬 설정 (range · gap · mult) */
        final Obj k;
        final long born;
        final long until;
        /** 남은 공격 */
        int left;
        long nextAt;
        final int slot;
        Vec3 pos;
        Vec3 eye;
        final String token;
        /** 적 → 이 정령이 마지막으로 때린 틱 (범위에 맞은 적도 — 다음에는 다른 적부터) */
        final Map<UUID, Long> lastHit = new HashMap<>();
        long busyUntil;
        /** 바람의 정령 돌진 중 (아니면 null) */
        Dash dash;

        Spirit(String kind, ServerLevel level, double P, Obj k, long now, int left, long nextAt, int slot, Vec3 pos, Vec3 eye, String token) {
            this.kind = kind;
            this.def = SPIRIT_DEFS.obj(kind);
            double[][] c = spiritColor(kind);
            this.glow = c[0];
            this.core = c[1];
            this.level = level;
            this.P = P;
            this.k = k;
            this.born = now;
            this.until = now + Math.round(k.num("ticks"));
            this.left = left;
            this.nextAt = nextAt;
            this.slot = slot;
            this.pos = pos;
            this.eye = eye;
            this.token = token;
        }
    }

    /** 바람의 정령 돌진: from 에서 u 로 len 칸, t0 틱부터 ticks 틱 동안 */
    private record Dash(Vec3 from, Vec3 u, double len, long t0, int ticks) {}

    /** 플레이어 → 정령 목록 */
    private static final Map<UUID, List<Spirit>> SPIRITS = new HashMap<>();
    /** 정령 피해 예약 (몹 무적 시간 10틱): 플레이어 → (적 → 마지막으로 잡아 둔 정령 피해 틱) */
    private static final Map<UUID, Map<UUID, Long>> HIT_AT = new HashMap<>();
    /** 싸운 중립 몹 · 동물 (정령이 노려도 되는 적): 플레이어 → (몹 → 마지막으로 때리거나 맞은 틱) */
    private static final Map<UUID, Map<UUID, Long>> FOUGHT = new HashMap<>();

    static void register() {
        RuneCast.register("summonSpirits", RuneSpirit::summonSpirits);
        // 정령 움직이기 · 그리기 · 공격 (2틱마다), 상태 줄 덧붙임
        Notify.addHudSuffix(RuneSpirit::spiritHud);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 2 != 0) return;
            try {
                spiritTick(server);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] spirit tick", e); // 애드온의 runInterval 처럼 다음 번에 다시
            }
        });
        // 싸운 중립 몹 적기 (피해 전 — 보스 소품처럼 피해가 취소되는 것도. 보기만 함). 몹이 피해를 받은 틱은 Kit 가 적음 (무적 시간 피하기)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            noteFight(entity, source);
            return true;
        });
        // 죽음 · 차원 이동 (· 탈것): 정령이 흩어짐
        RuneKit.onLifeEnded(RuneSpirit::dismissSpirits);
        RuneSystem.onForget(id -> {
            SPIRITS.remove(id);
            HIT_AT.remove(id);
            FOUGHT.remove(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SPIRITS.clear();
            HIT_AT.clear();
            FOUGHT.clear();
        });
    }

    /** 정령 색 {빛, 심지} (RUNE_SPIRITS[kind].color) */
    private static double[][] spiritColor(String kind) {
        List<Object> c = SPIRIT_DEFS.obj(kind).list("color");
        return new double[][] {Kit.rgb(c.get(0)), Kit.rgb(c.get(1))};
    }

    // ------------------------------------------------------------ 자리

    /** 눈에서 화면의 (가로 h 도, 세로 v 도) 쪽으로 가는 방향 (보는 방향 위아래 포함, − h 는 왼쪽) */
    private static Vec3 viewRay(ServerPlayer sp, double h, double v) {
        Vec3 f = V.unit(sp.getViewVector(1F));
        Vec3 fwd = V.flat(f);
        if (Math.hypot(f.x, f.z) < 0.02) {
            // 바로 아래 · 위를 볼 때: 보는 방향의 가로 성분이 거의 없으므로 몸이 향한 쪽 (yaw) 으로 화면 오른쪽을 정함
            double yaw = Math.toRadians(sp.getYRot());
            fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        Vec3 r = V.rightOf(fwd); // 화면 오른쪽
        Vec3 u = V.cross(r, f); // 화면 위 (r × f)
        return V.unit(V.add(V.add(f, r, Math.tan(h * DEG)), u, Math.tan(v * DEG)));
    }

    /** 정령이 떠 있을 자리 (위아래로 천천히 흔들림) */
    private static Vec3 spiritSlot(ServerPlayer sp, int slot, long now, ServerLevel level) {
        double[] s = SPIRIT_SLOTS[slot % SPIRIT_SLOTS.length];
        Vec3 eye = sp.getEyePosition();
        Vec3 d = viewRay(sp, s[0], s[1] + Math.sin(now * 0.15 + slot * 1.7) * 2);
        double dist = Math.max(0.8, Math.min(SPIRIT_DIST, RuneKit.rayLength(level, eye, d, SPIRIT_DIST + 0.4) - 0.4)); // 벽에서 0.4칸 앞
        return V.add(eye, d, dist);
    }

    /** 정령 이름이 뜰 자리: 화면에서 정령 바로 위, 더 멀리 (눈앞 2칸에 띄우면 글자가 화면을 가릴 만큼 큼) */
    private static Vec3 spiritLabelAt(ServerPlayer sp, int slot, ServerLevel level) {
        double[] s = SPIRIT_SLOTS[slot % SPIRIT_SLOTS.length];
        Vec3 eye = sp.getEyePosition();
        Vec3 d = viewRay(sp, s[0], s[1] + 6);
        return V.add(eye, d, Math.max(1.2, Math.min(4.5, RuneKit.rayLength(level, eye, d, 4.8) - 0.3)));
    }

    // ------------------------------------------------------------ 노릴 적

    /** 늘 노리는 적: RPG 몬스터 · 보스 · 훈련용 허수아비 · 적대 몹 (중립 몬스터 · 동물 · 보스 소품은 아님) */
    static boolean hostileMob(Entity e) {
        if (Kit.hasFamily(e, "rpg_monster") || Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_dummy")) return true;
        if (Kit.hasFamily(e, "monster")) return !NEUTRAL_MONSTERS.contains(Kit.typeId(e));
        return Kit.hasFamily(e, "dragon");
    }

    /** 정령이 노려도 되는 적: 적대 몹, 또는 이 플레이어가 최근 FOUGHT_TICKS 안에 때렸거나 맞은 몹 (화난 중립 몹 · 보스 촉수 등) */
    static boolean spiritFoe(ServerPlayer sp, Entity e) {
        if (e.entityTags().contains(NO_AUTO_TAG)) return false;
        if (e instanceof Player p) return Kit.duelPartner.test(sp, p); // v2.26.0 결투 상대
        if (hostileMob(e)) return true;
        Map<UUID, Long> m = FOUGHT.get(sp.getUUID());
        Long t = m == null ? null : m.get(e.getUUID());
        return t != null && Tasks.now() - t <= FOUGHT_TICKS;
    }

    /** 피해 전: 플레이어가 때리거나 맞은 중립 몹 · 동물을 적어 둠 (적대 몹은 늘 노리므로 적지 않음) */
    private static void noteFight(LivingEntity victim, DamageSource source) {
        Entity src = source.getEntity();
        if (victim == null || src == null || victim.getUUID().equals(src.getUUID())) return;
        ServerPlayer pl = src instanceof ServerPlayer a ? a : victim instanceof ServerPlayer b ? b : null;
        if (pl == null) return;
        Entity other = pl == src ? victim : src;
        if (other instanceof Player || hostileMob(other)) return;
        long now = Tasks.now();
        Map<UUID, Long> m = FOUGHT.computeIfAbsent(pl.getUUID(), id -> new HashMap<>());
        m.put(other.getUUID(), now);
        if (m.size() > 32) m.values().removeIf(t -> now - t > FOUGHT_TICKS);
    }

    // ------------------------------------------------------------ 부르기

    /**
     * 정령 부르기 (정령사 룬 다섯): k.spirits 를 ticks 틱 동안 — 정령마다 hits ÷ 정령 수 번, first 틱 뒤부터 gap 틱마다 (둘째 정령부터 stagger 틱씩 늦게).
     * 넷을 부르면 정령왕이 나타남
     */
    static boolean summonSpirits(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        long now = Tasks.now();
        ServerLevel level = sp.level();
        List<Spirit> list = SPIRITS.getOrDefault(sp.getUUID(), new ArrayList<>());
        String life = RuneKit.lifeToken(sp);
        List<Object> kinds = k.list("spirits");
        int each = (int) Math.max(1, Math.round(k.num("hits") / kinds.size()));
        Vec3 king = kinds.size() > 1 ? spiritKing(sp) : null;
        for (int i = 0; i < kinds.size(); i++) {
            Set<Integer> used = new HashSet<>();
            for (Spirit x : list) used.add(x.slot);
            int slot = 0;
            while (used.contains(slot)) slot++;
            long nextAt = now + Math.round(k.num("first") + i * k.numOr("stagger", 0));
            Spirit s = new Spirit((String) kinds.get(i), level, P, k, now, each, nextAt, slot, spiritSlot(sp, slot, now, level), sp.getEyePosition(), life);
            list.add(s);
            SkillFx.burst(level, s.pos, s.glow, 0.55, 0.3); // 눈앞 2칸이므로 작게
            SkillFx.sparks(level, s.pos, s.core, 3, 3);
            if (king != null) RuneKit.streak(level, king, s.pos, s.glow, 0.08, 0.35, 0.8, 0.1 + i * 0.08); // 정령왕 → 정령 자리
            RuneKit.later(i * 3, () -> {
                if (RuneKit.sameLife(sp, life)) DamageNumbers.showAt(level, spiritLabelAt(sp, s.slot, level), s.def.str("text") + "§l" + s.def.str("name"));
            });
        }
        SPIRITS.put(sp.getUUID(), list);
        double[][] cs = RuneKit.colorOf("spirit");
        Vec3 feet = sp.position();
        SkillFx.rune(level, feet, Opt.of("size", 1.6, "life", 0.8, "color", cs[0], "alpha", 0.9, "rate", 120.0, "progress", 1.0, "grow", 0.2));
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", feet, 1.3, 0.7);
        SkillFx.play(level, "mob.allay.idle", feet, 1.2, 0.8);
        // v2.27.0 2차 강화 (정령왕의 부름 — 초월): 오로라 하늘 · 청록 번쩍임 · 정령왕 뒤 하늘의 정령 문양 · 발밑의 정령 문양 · 앞쪽 네 원소 빛기둥 (2틱 뒤)
        if (king != null && "transcend".equals(r.grade())) {
            double[] sg = cs[0];
            double[] sc = cs[1];
            RuneKit.ultSkyFor(sp, level, feet, "summonSpirits", 100, null);
            RuneKit.ultFlashFor(sp, level, feet, "summonSpirits", "flash", Opt.empty());
            RuneKit.skyPlate(sp, level, "sigil", V.up(V.add(king, Kit.facingOf(sp), 3.5), 0.6), 4.8, sg, sc, Opt.of("cell", (double) RuneKit.SIGIL.get("spirit"),
                    "life", 2.2, "spinRate", 25.0, "grow", 0.15, "alpha", 0.8, "fade", 0.35));
            RuneKit.domainSigil(level, feet, 4.8, "spirit", sg, sc, Opt.of("life", 1.9, "spinRate", 30.0, "grow", 0.2, "alpha", 0.82));
            double[][][] cols = new double[kinds.size()][][];
            for (int i = 0; i < kinds.size(); i++) cols[i] = spiritColor((String) kinds.get(i));
            RuneKit.later(2, () -> {
                if (RuneKit.sameLife(sp, life)) RuneKit.godRays(sp, sp.level(), sp.position(), 6, 4, sg, sc, Opt.of("arc", new double[] {-60, 60}, "h", 15.0,
                        "life", 1.1, "colors", cols));
            });
        }
        return true;
    }

    /** 정령왕 (정령왕의 부름): 앞쪽 하늘에 왕관을 쓴 정령왕 (rpg:rfx_king) 이 팔을 들어 네 정령을 부름. 그 자리 (정령에게 가는 빛줄기의 시작) */
    private static Vec3 spiritKing(ServerPlayer sp) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 at = V.up(V.add(sp.position(), f, 5.4), 3.5);
        RuneKit.rfx(level, "king", at, Opt.of("size", 2.2, "life", 1.6, "grow", 0.1, "alpha", 0.9, "frames", 2.0, "fps", 4.0, "loop", true, "fade", 0.35));
        SkillFx.fx(level, "rpg:magic_circle_flash", V.add(at, f, 0.4), Opt.of("size", 2.6, "life", 0.8, "spin", Kit.rand() * 360, "progress", 1.0, "color",
                new double[] {1.0, 0.92, 0.66}, "alpha", 0.75));
        String[] four = {"salamander", "undine", "sylph", "gnome"};
        for (int i = 0; i < 4; i++) {
            double a = (i / 4.0) * Math.PI * 2 + 0.6;
            SkillFx.fx(level, "rpg:arrow_spark", V.add(at, new Vec3(Math.cos(a) * 1.6, Math.sin(a) * 1.2, 0), 1), Opt.of("dir", V.UP, "speed", 0.8, "size", 0.5,
                    "grow", 1.4, "life", 0.9, "color", spiritColor(four[i])[0], "alpha", 0.9));
        }
        SkillFx.play(level, "beacon.activate", at, 1.2, 0.8);
        SkillFx.play(level, "random.totem", sp.position(), 1.5, 0.5);
        return at;
    }

    // ------------------------------------------------------------ 공격

    /**
     * 정령이 노릴 적: 내가 보는 적 (범위 안) → 이 정령이 가장 오래 안 때린 적 (같으면 가까운 적). 벽 너머는 아님.
     * 적 = 적대 몹 · 싸운 몹 (spiritFoe): 지나가며 본 동물이나 건드리지 않은 중립 몹은 노리지 않음
     */
    private static LivingEntity spiritTarget(ServerPlayer sp, Spirit s) {
        double range = s.k.num("range");
        LivingEntity aim = RpgCombat.aimTarget(sp, range, 0).target();
        if (aim != null && !aim.isRemoved() && Kit.canHit(sp, aim) && spiritFoe(sp, aim)) return aim;
        Vec3 head = sp.getEyePosition();
        Vec3 pl = sp.position();
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, pl, range, 4)) {
            if (spiritFoe(sp, e) && Kit.lineOfSight(sp, head, Kit.bodyAt(e, head.y))) list.add(e);
        }
        if (list.isEmpty()) return null;
        list.sort(Comparator.comparingDouble((LivingEntity e) -> lastHitOf(s, e)).thenComparingDouble(e -> V.len(V.sub(e.position(), pl))));
        return list.get(0);
    }

    private static double lastHitOf(Spirit s, Entity e) {
        Long t = s.lastHit.get(e.getUUID());
        return t == null ? -1e9 : t;
    }

    /**
     * 적 e 가 마지막으로 피해를 받은 틱: 바닐라 몹은 누구에게든 (무적 시간이 몹마다 하나 — Kit 가 LivingEntity#actuallyHurt 에서 바로 적음),
     * 보스는 이 플레이어의 스킬 · 룬 피해 (사람마다 따로 셈)
     */
    private static long lastHurtOf(ServerPlayer sp, Entity e) {
        return Kit.lastHurtTick(e, sp);
    }

    /**
     * 정령 피해 한 번 (마력, 마법 피해): 몹 무적 시간 (10틱) 에 막히지 않게 — 다른 정령 피해 · 내 공격 · 다른 룬 · 불 피해에서 10틱 뒤로 미룸.
     * 들어갈 때 그 사이 다른 피해가 있었으면 다시 미룸 (3번까지). fn = 들어간 뒤. s = 때린 정령 (범위에 맞은 적도 "때린 적" 으로 적어 다음에는 다른 적부터)
     */
    private static void spiritDamage(ServerPlayer sp, LivingEntity e, double P, double mult, Consumer<LivingEntity> fn, Spirit s) {
        long now = Tasks.now();
        if (s != null) s.lastHit.put(e.getUUID(), now);
        Map<UUID, Long> m = HIT_AT.computeIfAbsent(sp.getUUID(), id -> new HashMap<>());
        long at = Math.max(now, Math.max(m.getOrDefault(e.getUUID(), -100L) + 10, lastHurtOf(sp, e) + 10));
        m.put(e.getUUID(), at);
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(at - now, () -> spiritLand(sp, e, P, mult, fn, life, 0));
    }

    /** 정령 피해가 들어갈 틱 (tries = 미룬 횟수) */
    private static void spiritLand(ServerPlayer sp, LivingEntity e, double P, double mult, Consumer<LivingEntity> fn, String life, int tries) {
        if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
        long t = Tasks.now();
        long free = lastHurtOf(sp, e) + 10;
        if (free > t && tries < 3) {
            spiritRetry(sp, e, P, mult, fn, life, t, free, tries);
            return;
        }
        // (들어간 틱은 Kit 가 바로 적음 — 같은 틱의 다른 정령 · 다른 사람의 정령이 봄)
        if (RuneKit.runeHit(sp, e, P, mult, "int") > 0) {
            if (fn != null) fn.accept(e);
        } else if (tries < 3 && Kit.canHit(sp, e)) {
            // 같은 틱에 먼저 들어간 다른 피해의 무적 시간에 막힘: 다시
            spiritRetry(sp, e, P, mult, fn, life, t, Math.max(lastHurtOf(sp, e) + 10, t + 1), tries);
        }
    }

    private static void spiritRetry(ServerPlayer sp, LivingEntity e, double P, double mult, Consumer<LivingEntity> fn, String life, long t, long free, int tries) {
        Map<UUID, Long> mm = HIT_AT.get(sp.getUUID());
        if (mm != null && mm.getOrDefault(e.getUUID(), -100L) < free) mm.put(e.getUUID(), free);
        RuneKit.later(free - t, () -> spiritLand(sp, e, P, mult, fn, life, tries + 1));
    }

    /** 정령의 범위 공격에 맞는 적 (at 둘레 radius 칸, 위아래 2.5칸): 노려도 되는 적만 (곁의 동물 · 중립 몹은 다치지 않음) */
    private static List<LivingEntity> spiritFoesAround(ServerPlayer sp, Vec3 at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, at, radius, 2.5)) if (spiritFoe(sp, e)) out.add(e);
        return out;
    }

    /** 정령의 공격 한 번 (정령마다 다름: RUNE_SPIRITS) */
    private static void spiritAttack(ServerPlayer sp, Spirit s, LivingEntity tgt) {
        ServerLevel level = sp.level();
        Obj d = s.def;
        double[] glow = s.glow;
        double[] core = s.core;
        Vec3 from = s.pos;
        long now = Tasks.now();
        String life = RuneKit.lifeToken(sp);
        double P = s.P;
        double mult = s.k.num("mult");
        s.lastHit.put(tgt.getUUID(), now);
        switch (s.kind) {
            case "salamander" -> {
                // 불덩이: 날아가 폭발 (반지름 radius) + 불붙음
                Vec3 to = Kit.bodyPoint(tgt);
                int ticks = RuneKit.flyStreak(level, from, to, d.num("speed"), glow, core, 0.45, 0.42);
                s.busyUntil = now + ticks;
                SkillFx.play(level, "mob.blaze.shoot", from, 1.5, 0.5);
                double radius = d.num("radius");
                RuneKit.later(ticks, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 at = !tgt.isRemoved() ? Kit.bodyPoint(tgt) : to;
                    Vec3 feet = !tgt.isRemoved() ? tgt.position() : new Vec3(at.x, at.y - 0.9, at.z);
                    SkillFx.burst(level, at, glow, 1.4, 0.3);
                    SkillFx.fx(level, "rpg:fire_wall", feet, Opt.of("size", radius * 1.4, "life", 0.6, "color", glow, "alpha", 0.95, "spin", Kit.rand() * 360));
                    RuneKit.zonePulse(level, feet, glow, radius, 0.35);
                    SkillFx.sparks(level, at, core, 6, 5);
                    SkillFx.play(level, "fire.ignite", at, 1.2, 0.8);
                    for (LivingEntity e : spiritFoesAround(sp, at, radius)) spiritDamage(sp, e, P, mult, x -> RuneKit.burn(x, d.num("fire")), s);
                });
            }
            case "undine" -> {
                // 물줄기: 대상 둘레 반지름 radius 물보라 + 밀침 + 구속 I
                Vec3 to = Kit.bodyPoint(tgt);
                double len = V.len(V.sub(to, from));
                Vec3 u = V.unit(V.sub(to, from));
                int ticks = Math.max(1, (int) Math.round(len / d.num("speed") * 20));
                s.busyUntil = now + ticks;
                for (double st = 0.4; st < len; st += 0.8) {
                    double delay = (st / len) * (ticks / 20.0);
                    SkillFx.fx(level, "rpg:arrow_trail", V.add(from, u, st), Opt.of("dir", u, "size", 0.48, "thick", 0.18, "life", delay + 0.3, "delay", delay,
                            "color", glow, "alpha", 0.9)); // 조각끼리 겹침 (0.96 > 0.8)
                }
                SkillFx.play(level, "bucket.empty_water", from, 1.4, 0.7);
                double radius = d.num("radius");
                RuneKit.later(ticks, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 at = !tgt.isRemoved() ? Kit.bodyPoint(tgt) : to;
                    SkillFx.fx(level, "rpg:splash", new Vec3(at.x, at.y - 0.9 + radius * 0.75, at.z), Opt.of("size", radius, "life", 0.6, "color", glow, "alpha", 0.95));
                    for (int i = 0; i < 4; i++) {
                        double a = (i / 4.0) * Math.PI * 2 + Kit.rand();
                        SkillFx.fx(level, "rpg:drop", V.up(at, 0.3), Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.4, Math.sin(a))), "speed", 4.0, "size", 0.35,
                                "life", 0.5, "color", core, "alpha", 0.95));
                    }
                    SkillFx.play(level, "random.splash", at, 1.2, 0.6);
                    for (LivingEntity e : spiritFoesAround(sp, at, radius)) {
                        spiritDamage(sp, e, P, mult, x -> {
                            RuneKit.knock(x, V.sub(x.position(), sp.position()), d.num("knock"), 0.2);
                            RuneKit.effect(x, "slowness", (int) d.num("slowTicks"), 0);
                        }, s);
                    }
                });
            }
            case "sylph" -> {
                // 돌진: 정령이 대상을 꿰뚫고 pierce 칸 너머까지 날아감 (폭 width 의 적 모두) + 띄움, 끝나면 자리로 돌아옴
                Vec3 to = Kit.bodyPoint(tgt);
                Vec3 u = V.unit(V.sub(to, from));
                double len = V.len(V.sub(to, from)) + RuneKit.rayLength(level, to, u, d.num("pierce"));
                double speed = d.num("speed");
                int ticks = Math.max(2, (int) Math.round(len / speed * 20));
                s.dash = new Dash(from, u, len, now, ticks);
                s.busyUntil = now + ticks + 6;
                RuneKit.flyStreak(level, from, V.add(from, u, len), speed, glow, core, 0.8, 0.3);
                SkillFx.play(level, "mob.breeze.shoot", from, 1.3, 0.7);
                SkillFx.play(level, "mob.breeze.whirl", to, 1.6, 0.5);
                for (Kit.LineHit h : RuneKit.lineTargets(sp, from, u, len, d.num("width"))) {
                    LivingEntity e = h.e();
                    double t = h.t();
                    if (!spiritFoe(sp, e)) continue;
                    RuneKit.later(Math.max(1, Math.round(t / speed * 20)), () -> {
                        if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                        Vec3 at = Kit.bodyPoint(e);
                        SkillFx.arc(level, at, u, Opt.of("shape", "thin", "flip", ((int) t) % 2 == 1, "size", 1.2, "life", 0.2, "speed", 0.0, "spin", 40.0,
                                "color", glow, "core", false));
                        spiritDamage(sp, e, P, mult, x -> RuneKit.knock(x, u, 0.3, 0.35), s);
                    });
                }
            }
            case "gnome" -> {
                // 바위 가시: 대상 발밑에 warn 틱 예고 → 반지름 radius 가시 + 구속 II
                Vec3 g = tgt.position();
                double warn = d.num("warn");
                double radius = d.num("radius");
                s.busyUntil = now + Math.round(warn) + 4;
                RuneKit.zonePulse(level, g, glow, radius, warn / 20);
                SkillFx.rune(level, g, Opt.of("size", radius * 0.8, "life", warn / 20 + 0.1, "color", glow, "alpha", 0.85, "rate", 200.0, "progress", 1.0,
                        "grow", 0.0));
                SkillFx.fx(level, "rpg:arrow_shot", from, Opt.of("dir", V.unit(V.sub(g, from)), "speed", V.len(V.sub(g, from)) / Math.max(0.05, warn / 20),
                        "size", 0.4, "thick", 0.4, "life", warn / 20, "color", glow, "alpha", 1.0)); // 정령이 던지는 흙덩이
                SkillFx.play(level, "dig.stone", g, 0.8, 0.8);
                RuneKit.later(warn, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (int i = 0; i < 6; i++) {
                        double a = (i / 6.0) * Math.PI * 2;
                        double rr = i == 0 ? 0 : radius * 0.55;
                        SkillFx.fx(level, "rpg:ice_spike", new Vec3(g.x + Math.cos(a) * rr, g.y, g.z + Math.sin(a) * rr), Opt.of("size", 1.5 + (i % 2) * 0.4,
                                "life", 0.8, "spin", Kit.rand() * 30 - 15, "color", new double[] {0.66, 0.58, 0.5}, "alpha", 1.0)); // 돌 가시 (얼음 가시 그림을 돌 색으로)
                    }
                    SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.6, "life", 1.2,
                            "color", new double[] {0.3, 0.2, 0.1}, "alpha", 0.9));
                    for (int i = 0; i < 2; i++) {
                        double a = Kit.rand() * Math.PI * 2;
                        SkillFx.fx(level, "rpg:boulder", V.up(g, 0.5), Opt.of("dir", V.unit(new Vec3(Math.cos(a) * 0.5, 1, Math.sin(a) * 0.5)), "speed", 6.0,
                                "size", 0.35, "life", 0.7, "spin", Kit.rand() * 360, "color", new double[] {0.75, 0.58, 0.4}, "alpha", 1.0));
                    }
                    SkillFx.fx(level, "rpg:dust", V.up(g, radius * 0.35), Opt.of("size", radius, "life", 0.8, "color", new double[] {0.62, 0.5, 0.38}, "alpha", 0.8));
                    SkillFx.play(level, "mace.heavy_smash_ground", g, 0.9, 0.9);
                    for (LivingEntity e : spiritFoesAround(sp, g, radius)) {
                        spiritDamage(sp, e, P, mult, x -> {
                            RuneKit.effect(x, "slowness", (int) d.num("slowTicks"), 1);
                            RuneKit.knock(x, V.UP, 0, 0.3);
                        }, s);
                    }
                });
            }
            default -> {
                // 모르는 정령: 공격 없음
            }
        }
    }

    // ------------------------------------------------------------ 그리기 · 움직이기

    /** 정령 그리기 (2틱마다 부름): 4틱마다 정령 그림 (rpg:rfx_spirit — 샐러맨더 · 운디네 · 실프 · 노움, 4장이 이어 돎) + 사이 틱에 작은 빛 */
    private static void drawSpirit(ServerLevel level, Spirit s, long now) {
        Vec3 p = s.pos;
        if (((now >> 1) & 1) == 1) {
            SkillFx.fx(level, "rpg:arrow_spark", V.up(p, -0.3), Opt.of("dir", V.DOWN, "speed", 0.6, "size", 0.1, "grow", 0.5, "life", 0.3, "color", s.core,
                    "alpha", 0.9));
            return;
        }
        RuneKit.rfx(level, "spirit", p, Opt.of("size", 0.36, "life", 0.25, "cell", (double) RuneKit.SPIRIT_CELL.getOrDefault(s.kind, 0), "frames", 4.0,
                "fps", 8.0, "loop", true, "phase", (now / 20.0 * 8) % 4));
    }

    /** 정령이 떠남 (시간이 다 됨 · 공격을 다 함): 빛이 흩어짐 */
    private static void spiritLeave(ServerLevel level, Spirit s) {
        SkillFx.burst(level, s.pos, s.glow, 0.5, 0.3);
        SkillFx.fx(level, "rpg:arrow_spark", s.pos, Opt.of("dir", V.UP, "speed", 2.0, "size", 0.2, "grow", 0.5, "life", 0.5, "color", s.core, "alpha", 1.0));
        SkillFx.play(level, "mob.allay.item_given", s.pos, 1.3, 0.5);
    }

    /** 정령 움직이기 · 그리기 · 공격 (2틱마다) */
    private static void spiritTick(MinecraftServer server) {
        long now = Tasks.now();
        for (Iterator<Map<UUID, Long>> it = HIT_AT.values().iterator(); it.hasNext(); ) {
            Map<UUID, Long> m = it.next();
            m.values().removeIf(t -> now - t > 40);
            if (m.isEmpty()) it.remove();
        }
        if (SPIRITS.isEmpty()) return;
        // (공격이 바로 들어가 그 사이 정령이 흩어질 수 있으므로 — 죽음 · 차원 이동 — 사람 목록을 복사해서 돎)
        for (UUID pid : new ArrayList<>(SPIRITS.keySet())) {
            List<Spirit> list = SPIRITS.get(pid);
            if (list == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(pid);
            if (player == null || player.isRemoved()) {
                SPIRITS.remove(pid);
                continue;
            }
            ServerLevel level = player.level();
            for (int i = list.size() - 1; i >= 0; i--) {
                Spirit s = list.get(i);
                if (!RuneKit.sameLife(player, s.token)) {
                    // 죽음 · 차원 이동 · 탈것: 있던 차원에서 흩어짐
                    SkillFx.burst(s.level, s.pos, s.glow, 0.5, 0.25);
                    list.remove(i);
                    continue;
                }
                s.level = level;
                if (now >= s.until || (s.left <= 0 && now >= s.busyUntil + 8)) {
                    spiritLeave(level, s);
                    list.remove(i);
                    continue;
                }
                // 나와 같이 움직임 (걷기 · 겉날개 · 순간 이동 모두 같은 만큼 옮긴 뒤 자리로 다가감 — 멀리 처져 옛 자리에서 공격하지 않게)
                Vec3 eye = player.getEyePosition();
                Vec3 moved = V.sub(eye, s.eye != null ? s.eye : eye);
                s.eye = eye;
                if (s.dash != null && V.len(moved) > SPIRIT_TELEPORT) s.dash = null; // 순간 이동: 돌진을 멈추고 같이 옮김
                if (s.dash != null) {
                    double q = (double) (now - s.dash.t0()) / s.dash.ticks();
                    if (q >= 1) s.dash = null;
                    else s.pos = V.add(s.dash.from(), s.dash.u(), s.dash.len() * q);
                } else {
                    s.pos = V.add(s.pos, moved);
                }
                if (s.dash == null) {
                    Vec3 want = spiritSlot(player, s.slot, now, level);
                    s.pos = V.add(s.pos, V.sub(want, s.pos), 0.45);
                }
                drawSpirit(level, s, now);
                if (s.left > 0 && now >= s.nextAt && now >= s.busyUntil) {
                    LivingEntity tgt = spiritTarget(player, s);
                    if (tgt != null) {
                        s.left--;
                        s.nextAt = now + Math.round(s.k.num("gap"));
                        spiritAttack(player, s, tgt);
                    }
                }
            }
            if (list.isEmpty() && SPIRITS.get(pid) == list) SPIRITS.remove(pid);
        }
    }

    /** 지금 정령 (시험 · 상태 줄) */
    static List<Spirit> spiritsOf(ServerPlayer sp) {
        return SPIRITS.getOrDefault(sp.getUUID(), List.of());
    }

    /** 화면 하단 상태 줄의 정령 (룬 · 초월 무기 줄 뒤에 붙음) */
    static String spiritHud(ServerPlayer sp) {
        List<Spirit> list = SPIRITS.get(sp.getUUID());
        if (list == null || list.isEmpty()) return "";
        long now = Tasks.now();
        long left = Long.MIN_VALUE;
        for (Spirit s : list) left = Math.max(left, s.until - now);
        return "§a정령 " + list.size() + " §7" + String.format(Locale.ROOT, "%.1f", Math.max(0, left / 20.0)) + "초";
    }

    /** 죽음 · 차원 이동 · 탈것: 정령이 흩어짐 */
    static void dismissSpirits(UUID id) {
        List<Spirit> list = SPIRITS.remove(id);
        if (list == null) return;
        for (Spirit s : list) SkillFx.burst(s.level, s.pos, s.glow, 0.5, 0.25); // 정령이 있던 차원 (차원 이동이면 떠난 쪽)
    }
}
