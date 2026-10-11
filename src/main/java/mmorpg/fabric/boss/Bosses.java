package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.BossKit.BOSS;
import static mmorpg.fabric.boss.BossKit.BOSSES;
import static mmorpg.fabric.boss.BossKit.audience;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossLabel;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.fanMarks;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.groundUnder;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.inFan;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.lobArc;
import static mmorpg.fabric.boss.BossKit.meleeTarget;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rgb;
import static mmorpg.fabric.boss.BossKit.say;
import static mmorpg.fabric.boss.BossKit.showFaceText;
import static mmorpg.fabric.boss.BossKit.snd;
import static mmorpg.fabric.boss.BossKit.sound;
import static mmorpg.fabric.boss.BossKit.title;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Js;
import mmorpg.core.Mobs;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.RpgBoss;
import mmorpg.fabric.nav.Nav;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.quest.QuestSystem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Aegis;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Solas;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.MenuForm;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 보스 엔진 (bosses.js v1.8.0~v2.27.0): 제단 누르기 → 소환 → 싸움 → 보상 · 재사용 대기 / 초기화. 이름 · 순서는 애드온 그대로.
 * <ul>
 * <li>보스 체력: 바닐라 체력 (보스 막대에 보임) 을 엔진이 줄임. 플레이어가 준 피해는 RpgBoss · RpgProp 의 hurtServer 가 {@link #absorb} 로 가져와
 * (바닐라 피해 없음) 플레이어마다 무적 시간 (BOSS.iframes) 을 따로 셈 → 파티가 때리면 그만큼 빨리 잡힘. 환경 · 몹 피해는 받지 않음.
 * 평타 (근접 · 활 · 완드) 와 스킬 · 룬 피해는 무적 시간을 따로 셈 (스킬 · 룬 = RpgCombat 의 맞힘 기억에 숫자 모양이 있는 것)</li>
 * <li>맞으면 붉게 번쩍임 (속성 rpg:flash → 렌더 컨트롤러), 피해 숫자 · 치명타 · 참격은 평소와 같이 (DamageNumbers.feedback) + 근접 보너스 » · 모듈 표시</li>
 * <li>싸우는 사람이 필드에 없는 채로 BOSS.resetAfter 틱 → 초기화 (재사용 대기 없음). 잡으면 필드 안이었거나 participantGrace 틱 안에 때린 사람 모두 보상</li>
 * <li>패턴은 보스별 모듈 ({@link BossModule}) 이 고르고, 엔진은 틀 (cast) · 평타 · 원거리 견제 · 끌어오기 · 정리를 맡음. 여러 마리 싸움 (units) 은 마리마다</li>
 * </ul>
 * 자바에서 달라진 점: 피해는 before 이벤트 + 다음 틱 처리 대신 hurtServer 안에서 바로 처리 (돌려주는 값 = 들어갔는지 — 애드온의 absorbed),
 * /fog 대신 하늘빛 패킷 (BossKit.FOGS), 남은 보스 · 소품은 디스크에서 불러올 때 막음 (ServerEntityEvents.ALLOW_LOAD), 시간은 Tasks.now().
 */
public final class Bosses {
    private Bosses() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-boss");
    private static final Rules R = Rules.get();
    /** 근접 보너스가 붙은 피해 숫자 앞 표시 (v2.3.0) */
    public static final String CLOSE_MARK = "§c»";

    /** 보스 모듈 — 새 보스는 여기에 한 줄 (애드온 bosses.js 의 MODS) */
    static final List<BossModule> MODULES = List.of(
            new BossScarecrow(),
            new BossScorpions(),
            new BossKraken(),
            new BossWorm(),
            new BossJormungandr(),
            new BossNidhogg(),
            new BossSkoll(),
            new BossYmir(),
            new BossSurtr());

    /** 보스 키 → 모듈 */
    static final Map<String, BossModule> MODS = new LinkedHashMap<>();

    // ------------------------------------------------------------ 싸움 · 마리

    /** 플레이어마다 무적 시간 (마리 · 소품마다 따로): 그 사이 더 센 공격은 차이만큼 */
    public static final class Iframe {
        public long tick;
        public double amount;

        Iframe(long tick, double amount) {
            this.tick = tick;
            this.amount = amount;
        }
    }

    /** 싸움에 든 사람 (보상 · 즉사 대상): 준 피해 합 · 마지막으로 때린 틱 · 쓰러진 틱 */
    public static final class Part {
        public ServerPlayer p;
        public double dmg;
        public long last;
        public long diedAt = -1;

        Part(ServerPlayer p, long last) {
            this.p = p;
            this.last = last;
        }
    }

    /** 도발 (아이기스의 방패): 그 사람 · 끝나는 틱 */
    public record Taunt(UUID id, long until) {}

    /** 패턴 틀의 한 단계: at = 틱 (cast 에 넘길 때는 시작 뒤 틱, 틀 안에서는 절대 틱), fn(그 마리, now) */
    public record Step(long at, StepFn fn) {}

    @FunctionalInterface
    public interface StepFn {
        void run(Unit u, long now);
    }

    /** 틀 단계 [시작 뒤 틱, fn] (애드온 [t, (e, now) => …]) */
    public static Step step(long at, StepFn fn) {
        return new Step(at, fn);
    }

    /** 진행 중인 틀 */
    public static final class Cast {
        public final String id;
        public final long until;
        final ArrayDeque<Step> steps;

        Cast(String id, long until, List<Step> steps) {
            this.id = id;
            this.until = until;
            this.steps = new ArrayDeque<>(steps);
        }

        /** 남은 단계 수 */
        public int left() {
            return steps.size();
        }
    }

    /**
     * 싸움의 기억 (애드온 enc.mem): 모듈이 start 에서 자기 것 (이 클래스를 넓힌 것) 으로 바꿈 — {@code enc.mem = new M()}, 쓸 때 {@code (M) u.mem}.
     * 엔진 · 도구가 보는 칸: fog (보스 안개 — BossKit.syncBossFog) · fogWant
     */
    public static class Mem {
        public Map<UUID, BossKit.Fog> fog;
        public String fogWant;
    }

    /**
     * 보스 한 마리 (애드온 Unit — 한 마리 보스는 싸움 자신). 칸 이름은 애드온 그대로:
     * enc · key · index · def (설정 — 여러 마리면 units 한 줄 + 싸움의 level · color · sounds) · a (제단) · dim (세계) · center · feat (필드 특징 자리) ·
     * parts · api · mod · mem · boss (엔티티) · alive · home · leash · iframes · cast · meleeAt · staggerUntil · held · immune · fast · dmgMult · cdMult ·
     * faceTo · faceNow (움직이지 않는 보스의 각도, 라디안) · farAt · farPausedAt · taunt
     */
    public static class Unit {
        public Enc enc;
        public String key;
        public int index;
        public Obj def;
        public Arena.Altar a;
        public ServerLevel dim;
        public Vec3 center;
        public Arena.Features feat;
        public Map<UUID, Part> parts;
        public Api api;
        public BossModule mod;
        public Mem mem;
        public BedrockMob boss;
        public boolean alive = true;
        public Vec3 home;
        public double leash;
        public final Map<String, Iframe> iframes = new HashMap<>();
        public Cast cast;
        public long meleeAt;
        public long staggerUntil;
        public boolean held;
        public boolean immune;
        public boolean fast;
        /** 주는 피해 배율 (애드온 dmgMult ?? 1) */
        public double dmgMult = 1;
        /** 재사용 대기 배율 (애드온 cdMult ?? 1) */
        public double cdMult = 1;
        public Double faceTo;
        public Double faceNow;
        public Long farAt;
        public Long farPausedAt;
        public Taunt taunt;
    }

    /** 싸움 하나 (제단마다 — 애드온 Enc): state "summon" · "fight" · "dead", t0, spawnAt, units, emptySince, props, starter, farShots, farTold, farSince, area */
    public static final class Enc extends Unit {
        public String state;
        public long t0;
        public long spawnAt;
        public List<Unit> units = List.of();
        public Long emptySince;
        public final Set<Entity> props = new LinkedHashSet<>();
        public ServerPlayer starter;
        public List<FarShot> farShots = new ArrayList<>();
        public boolean farTold;
        public final Map<UUID, Long> farSince = new HashMap<>();
        public String area;
    }

    /** 날아가는 원거리 견제: 던진 마리 · 떨어질 자리 · 떨어지는 틱 · 모양 */
    public record FarShot(Unit u, Vec3 spot, long hitAt, Obj style) {}

    /** 맞는 소품 (v2.0.0): 최대 체력 · 피해 숫자 높이 (0 = 없음) · 플레이어마다 무적 시간 · 쓰러짐 */
    public static final class PropInfo {
        public final double max;
        public final double textHeight;
        final Map<String, Iframe> iframes = new HashMap<>();
        public boolean dead;

        PropInfo(double max, double textHeight) {
            this.max = max;
            this.textHeight = textHeight;
        }
    }

    /**
     * 맞은 기록 (애드온 h): 싸움 · 마리 (소품이면 null) · 맞은 것 · 때린 사람 · 피해 (무적 시간 · 근접 보너스 뒤) · 원인 · 소품인지 · 맞는 소품 정보 ·
     * close (근접 보너스를 받음) · near (근접 보너스 거리에서 때림 — 보너스가 0 이어도) · skill (스킬 · 룬 피해)
     */
    public static final class Hit {
        public final Enc enc;
        public final Unit unit;
        public final LivingEntity target;
        public final ServerPlayer p;
        public final double amount;
        public final DamageSource cause;
        public final boolean prop;
        public final PropInfo info;
        public final boolean close;
        public final boolean near;
        public final boolean skill;
        final RpgCombat.Mark mark;

        Hit(Enc enc, Unit unit, LivingEntity target, ServerPlayer p, double amount, DamageSource cause, boolean prop, PropInfo info, boolean close,
                boolean near, boolean skill, RpgCombat.Mark mark) {
            this.enc = enc;
            this.unit = unit;
            this.target = target;
            this.p = p;
            this.amount = amount;
            this.cause = cause;
            this.prop = prop;
            this.info = info;
            this.close = close;
            this.near = near;
            this.skill = skill;
            this.mark = mark;
        }
    }

    /** 모듈 hurt 의 돌려주는 값 (애드온: 수 또는 {amount, mark}) */
    public record Hurt(double amount, String mark) {
        public static Hurt of(double amount) {
            return new Hurt(amount, "");
        }

        public static Hurt of(double amount, String mark) {
            return new Hurt(amount, mark == null ? "" : mark);
        }
    }

    /** 제단 → 싸움 */
    private static final Map<Arena.Altar, Enc> encs = new LinkedHashMap<>();
    /** 보스 · 소품 엔티티 → 싸움 */
    private static final Map<UUID, Enc> byEntity = new HashMap<>();
    /** 보스 엔티티 → 그 마리 */
    private static final Map<UUID, Unit> unitById = new HashMap<>();
    /** 싸움이 가진 엔티티 (보스 · 소품 · 감옥): 이 밖의 보스 · 소품 엔티티는 남은 것 (디스크에서 불러올 때 지움) */
    private static final Set<UUID> owned = new HashSet<>();
    /** 필드 밖에서 때려 막힌 공격 알림 (플레이어 → 틱) */
    private static final Map<UUID, Long> farWarned = new HashMap<>();
    /** 맞는 소품: 엔티티 → 정보 */
    private static final Map<UUID, PropInfo> propInfo = new HashMap<>();
    /** 피해 숫자 자리를 정하는 동안의 때린 사람 (큰 보스의 textNear) */
    private static ServerPlayer feedbackAttacker;

    // ------------------------------------------------------------ 조회

    /** 이 엔티티 (보스 · 소품) 의 싸움 (없으면 null) */
    public static Enc encounterOf(Entity e) {
        return e == null ? null : byEntity.get(e.getUUID());
    }

    /** 보스 엔티티 → 그 마리 (소품이면 null) */
    public static Unit unitOf(Entity e) {
        return e == null ? null : unitById.get(e.getUUID());
    }

    /** 제단의 싸움 (없으면 null) */
    public static Enc encounterAt(Arena.Altar a) {
        return encs.get(a);
    }

    public static List<Enc> allEncounters() {
        return new ArrayList<>(encs.values());
    }

    /** 살아 있는 엔티티인지 (애드온 isValid — 쓰러지는 모습 중이면 아님) */
    static boolean valid(Entity e) {
        return e != null && !e.isRemoved() && (!(e instanceof LivingEntity le) || le.isAlive());
    }

    /** 남은 체력 비율 (마리마다) */
    static double ratio(Unit u) {
        if (!u.alive || u.boss == null) return 0;
        float max = u.boss.getMaxHealth();
        return max > 0 ? u.boss.getHealth() / max : 1;
    }

    public static double bossRatio(Unit u) {
        return ratio(u);
    }

    static boolean live(Enc enc) {
        return encs.get(enc.a) == enc && "fight".equals(enc.state);
    }

    /** 같은 싸움의 살아 있는 다른 마리 (없으면 null) */
    static Unit other(Unit u) {
        for (Unit x : u.enc.units) if (x != u && x.alive) return x;
        return null;
    }

    static boolean stationary(Unit u) {
        return u.def.objOrNull("stationary") != null;
    }

    // ------------------------------------------------------------ 소환

    /**
     * 소환 시작 (제단 연출 BOSS.summonDelay 틱 뒤 보스가 나옴). 이미 싸우는 중 · 모듈이 없는 보스면 null
     * @param by 소환한 플레이어 (없으면 null)
     */
    public static Enc summon(Arena.Altar a, ServerPlayer by) {
        if (a == null || encs.containsKey(a) || !MODS.containsKey(a.key)) return null;
        ServerLevel dim = Arena.levelOf(a);
        if (dim == null) return null;
        Obj def = BOSSES.obj(a.key);
        long now = Tasks.now();
        Vec3 center = Arena.arenaCenter(a);
        Enc enc = new Enc();
        enc.a = a;
        enc.key = a.key;
        enc.index = 0;
        enc.def = def;
        enc.mod = MODS.get(a.key);
        enc.dim = dim;
        enc.center = center;
        enc.feat = Arena.arenaFeatures(a);
        enc.state = "summon";
        enc.t0 = now;
        enc.spawnAt = now + (long) BOSS.num("summonDelay");
        enc.parts = new LinkedHashMap<>();
        enc.meleeAt = 0;
        enc.immune = false;
        enc.staggerUntil = 0;
        enc.mem = new Mem();
        enc.starter = by;
        enc.held = false;
        enc.api = API;
        enc.alive = true;
        enc.home = center;
        enc.leash = Mobs.unitHomeRadius(a.key, null) + 0.5;
        enc.enc = enc;
        List<Obj> uds = def.objsOrNull("units");
        if (uds != null) {
            List<Unit> units = new ArrayList<>();
            for (int i = 0; i < uds.size(); i++) units.add(makeUnit(enc, uds.get(i), i));
            enc.units = List.copyOf(units);
        } else enc.units = List.of(enc);
        // v2.12.0 큰 필드 (arena.tick): 싸우는 동안 필드를 붙잡음 (사람이 한쪽 끝에 있어도 보스 · 블록이 멈추지 않게, 끝나면 놓음)
        Obj ar = def.obj("arena");
        if (ar.truthy("tick")) enc.area = Ticking.holdArea(dim, center, ar.num("radius") + 4, "f");
        encs.put(a, enc);
        title(enc, "§6§l" + def.str("name"), "§7" + def.strOr("title", "") + " §8· §fLv." + Js.str(def.num("level")) + " §8· §7권장 3~4명");
        say(enc, "§6[보스] §f" + (by != null ? by.getName().getString() : "누군가") + "님이 §6" + def.str("name") + "§f을(를) 깨웠습니다!");
        sound(enc, snd(enc, "summon"), enc.center, 0.8, 1);
        return enc;
    }

    /** 여러 마리 싸움의 한 마리: 설정 (units 한 줄) + 싸움 레벨 · 색 · 소리, 제 자리 = 가운데 + spawn */
    private static Unit makeUnit(Enc enc, Obj ud, int i) {
        Vec3 c = enc.center;
        double[] sp = BossKit.pair(ud, "spawn");
        double sx = sp != null ? sp[0] : 0;
        double sz = sp != null ? sp[1] : 0;
        Unit u = new Unit();
        u.enc = enc;
        u.key = ud.str("key");
        u.index = i;
        u.def = unitDef(enc, ud, i);
        u.a = enc.a;
        u.dim = enc.dim;
        u.center = c;
        u.feat = enc.feat;
        u.parts = enc.parts;
        u.api = enc.api;
        u.mod = enc.mod;
        u.mem = enc.mem;
        u.alive = true;
        u.home = new Vec3(c.x + sx, c.y, c.z + sz);
        u.leash = Mobs.unitHomeRadius(enc.key, ud) + 0.5;
        return u;
    }

    /** { ...ud, level: 싸움 level, color: ud.color ?? 싸움 color, sounds: { ...싸움 sounds, ...ud.sounds } } */
    private static Obj unitDef(Enc enc, Obj ud, int i) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : ud.keys()) m.put(k, ud.get(k));
        m.put("level", enc.def.get("level"));
        m.put("color", ud.get("color") != null ? ud.get("color") : enc.def.get("color"));
        Map<String, Object> sounds = new LinkedHashMap<>();
        Obj es = enc.def.objOrNull("sounds");
        if (es != null) for (String k : es.keys()) sounds.put(k, es.get(k));
        Obj us = ud.objOrNull("sounds");
        if (us != null) for (String k : us.keys()) sounds.put(k, us.get(k));
        m.put("sounds", Collections.unmodifiableMap(sounds));
        return new Obj(Collections.unmodifiableMap(m), "BOSSES." + enc.key + ".units[" + i + "]");
    }

    /** 보스가 서는 높이 (바닥 기준): 걷는 보스는 1칸 위에서 내려앉고, 움직이지 않는 보스 (중력 없음) 는 stationary.y 에 그대로 */
    static double liftOf(Unit u) {
        Obj st = u.def.objOrNull("stationary");
        return st != null ? st.numOr("y", 0) : 1;
    }

    /** 수식어: 마리의 title (없거나 빈 글이면 싸움의 title) */
    private static String titleOf(Unit u) {
        String t = u.def.strOrNull("title");
        return t != null && !t.isEmpty() ? t : u.enc.def.strOrNull("title");
    }

    private static void spawnBoss(Enc enc) {
        Vec3 c = enc.center;
        long now = Tasks.now();
        for (Unit u : enc.units) {
            BedrockMob e;
            try {
                e = BossKit.spawn(enc.dim, Mobs.bossId(u.key), new Vec3(u.home.x, u.home.y + liftOf(u), u.home.z), 0F);
            } catch (RuntimeException err) {
                LOG.warn("[mmorpg] boss spawn", err);
                e = null;
            }
            if (e == null) {
                LOG.warn("[mmorpg] boss spawn failed: {}", Mobs.bossId(u.key));
                endEncounter(enc, "error");
                return;
            }
            u.boss = e;
            byEntity.put(e.getUUID(), enc);
            unitById.put(e.getUUID(), u);
            owned.add(e.getUUID());
            try {
                e.setNameTag(bossLabel(enc.def.num("level"), titleOf(u), u.def.str("name"))); // 보스 막대 이름과 같은 글
                // 여러 마리면 가운데 (서로) 를 보고 나옴
                if (u != enc) BossKit.teleport(e, new Vec3(u.home.x, u.home.y + 1, u.home.z), new Vec3(c.x, u.home.y + 1, c.z));
                // 움직이지 않는 보스: 깨운 사람 쪽 (없으면 남쪽) 을 보고 나옴
                if (stationary(u)) {
                    Vec3 p = enc.starter != null && !enc.starter.isRemoved() ? enc.starter.position() : new Vec3(c.x, c.y, c.z + 8);
                    setFacing(u, Math.atan2(p.z - u.home.z, p.x - u.home.x));
                    u.faceTo = u.faceNow;
                }
            } catch (RuntimeException err) {
                LOG.warn("[mmorpg] boss spawn setup", err);
            }
            u.meleeAt = now + 30;
            SkillFx.fx(enc.dim, "rpg:shock_ring", new Vec3(u.home.x, u.home.y + 0.1, u.home.z), Opt.of("dir", Vec3.ZERO, "size", 3.0, "life", 0.8, "color",
                    rgb(u.def, "color"), "alpha", 0.9));
        }
        enc.state = "fight";
        sound(enc, snd(enc, "roar"), c, 0.8, 1);
        try {
            enc.mod.start(enc);
        } catch (RuntimeException err) {
            LOG.warn("[mmorpg] boss start", err);
        }
        // 모듈이 싸움의 기억 (mem) 을 새로 만들면 마리들도 같은 것을 봄
        for (Unit u : enc.units) u.mem = enc.mem;
    }

    // ------------------------------------------------------------ 피해 (RpgBoss · RpgProp 의 hurtServer)

    private static ServerPlayer attackerOf(DamageSource src) {
        return src != null && src.getEntity() instanceof ServerPlayer sp ? sp : null;
    }

    /** 플레이어마다 무적 시간 (마리 · 소품마다 따로): 그 사이 더 센 공격은 차이만큼 (바닐라와 같은 규칙을 사람마다 따로). 막히면 0 */
    static double throughIframes(Map<String, Iframe> map, String id, double amount, long now) {
        Iframe last = map.get(id);
        if (last != null && now - last.tick < BOSS.num("iframes")) {
            if (amount <= last.amount) return 0;
            double diff = amount - last.amount;
            last.amount = amount;
            return diff;
        }
        map.put(id, new Iframe(now, amount));
        return amount;
    }

    /** 필드 밖 (울타리 너머) 에서 때린 공격인지 (패턴이 닿지 않는 곳에서 안전하게 때리지 못하게 막음, 가끔 알림) */
    static boolean hitFromOutside(Enc enc, ServerPlayer p, long now) {
        if (Arena.inArena(enc.a, NpcData.dimId(p.level()), p.position(), BOSS.num("combatMargin"))) return false;
        if (now - farWarned.getOrDefault(p.getUUID(), -100L) > 40) {
            farWarned.put(p.getUUID(), now);
            BossKit.notify(p, "§c필드 밖에서는 보스에게 피해를 줄 수 없습니다", 40);
        }
        return true;
    }

    /**
     * RpgBoss · RpgProp 의 hurtServer (애드온 absorb + processHit): 싸움의 보스 · 소품이면 피해를 가져감 (바닐라 피해 없음).
     * null = 이 엔진의 것이 아님 (바닐라 피해 그대로 — 싸움 밖 · /kill 같은 무적을 넘는 피해), true = 들어감 (스킬의 "맞힘"), false = 막힘
     */
    public static Boolean absorb(BedrockMob e, ServerLevel level, DamageSource src, float damage) {
        Enc enc = byEntity.get(e.getUUID());
        if (enc == null) return null;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return null; // /kill · 세계 밖
        ServerPlayer p = attackerOf(src);
        if (p == null) return false; // 환경 · 몹 피해 없음
        long now = Tasks.now();
        // 무적 시간 칸: 평타 (근접 · 활 · 완드) 와 스킬 · 룬 피해는 따로 (v2.1.0: 평타 바로 뒤에 쓴 스킬이 막히거나 줄지 않게)
        RpgCombat.Mark mark = RpgCombat.takeMark(e, p);
        boolean skill = mark != null && mark.style() != null;
        String ch = skill ? p.getUUID() + "|skill" : p.getUUID().toString();
        Unit u = unitById.get(e.getUUID());
        if (u == null) {
            PropInfo info = propInfo.get(e.getUUID());
            if (info != null) {
                // 맞는 소품 (촉수 등): 보스처럼 무적 시간 · 필드 밖 막기, 체력은 엔진이
                if (!"fight".equals(enc.state) || info.dead) return false;
                double amount = hitFromOutside(enc, p, now) ? 0 : throughIframes(info.iframes, ch, damage, now);
                if (amount <= 0) return false;
                boolean close = BOSS.obj("close").num("bonus") > 0 && isClose(e, null, p, info.textHeight > 0 ? info.textHeight : 3);
                processHit(new Hit(enc, null, e, p, amount * (close ? 1 + BOSS.obj("close").num("bonus") : 1), src, true, info, close, close, skill, mark), now);
                return true;
            }
            // 소품 (분신 등): 모듈이 처리
            processHit(new Hit(enc, null, e, p, damage, src, true, null, false, false, skill, mark), now);
            return true;
        }
        if (!"fight".equals(enc.state) || !u.alive) return false;
        if (hitFromOutside(enc, p, now)) return false;
        double amount = throughIframes(u.iframes, ch, damage, now);
        if (amount <= 0) return false; // 막힌 공격은 맞은 것으로 치지 않음 (스킬의 흡혈 등)
        // 근접 보너스 (v2.3.0): 몸 가까이에서 준 피해는 더 셈 (무적 시간은 원래 피해로 셈). near = 보너스와 상관없이 몸 가까이에서 때렸는지 (v2.5.0)
        boolean near = isClose(e, u.def, p, 3);
        double bonus = BOSS.obj("close").num("bonus");
        boolean close = bonus > 0 && near;
        processHit(new Hit(enc, u, e, p, amount * (close ? 1 + bonus : 1), src, false, null, close, near, skill, mark), now);
        return true;
    }

    /**
     * 근접 보너스를 받는 자리인지 (v2.3.0): 때린 사람이 몸 겉면 (가로: 가운데 − 반폭) 에서 BOSS.close.range 칸 안, 높이도 몸 둘레 (날고 있는 보스 밑은 아님).
     * def 가 없으면 (맞는 소품) 반폭 0.6칸 · 키 height
     */
    public static boolean isClose(Entity e, Obj def, Entity p, double height) {
        BossKit.Size size = def != null && def.listOrNull("box") != null ? bossSize(def) : new BossKit.Size(0.6, height);
        Vec3 l = e.position();
        Vec3 q = p.position();
        return Math.hypot(q.x - l.x, q.z - l.z) - size.half() <= BOSS.obj("close").num("range") && q.y > l.y - 3 && q.y < l.y + size.height() + 1;
    }

    /**
     * v2.12.0 큰 보스의 피해 숫자 자리: 몸 가운데 l 에서 때린 사람 쪽으로 몸 겉면 (반폭 + 0.6칸, 때린 사람보다 멀지 않게), 높이 = 그 사람 눈높이.
     * 때린 사람이 없거나 몸 안이면 null (보통 자리)
     */
    public static Vec3 textNearAt(Vec3 l, Obj def, Entity attacker) {
        if (attacker == null || attacker.isRemoved()) return null;
        Vec3 q = attacker.position();
        BossKit.Size size = bossSize(def);
        double dx = q.x - l.x;
        double dz = q.z - l.z;
        double L = Math.hypot(dx, dz);
        if (L < 0.5) return null;
        double r = Math.min(size.half() + 0.6, Math.max(0, L - 0.8));
        double y = Math.max(l.y + 1, Math.min(l.y + size.height() - 1, q.y + 1.5));
        return new Vec3(l.x + (dx / L) * r, y, l.z + (dz / L) * r);
    }

    /** 보스의 바닐라 근접 (엔티티가 휘두른 것) 은 막음 — 스크립트 공격 (isOwnHit) 과 쓰러지는 플레이어의 마무리만 통과 (ALLOW_DAMAGE) */
    private static boolean block(LivingEntity victim, DamageSource src) {
        Entity m = src.getEntity();
        if (m == null || !byEntity.containsKey(m.getUUID()) || !src.is(DamageTypes.MOB_ATTACK)) return false;
        if (victim instanceof ServerPlayer sp && sp.isDeadOrDying()) return false;
        return !BossKit.isOwnHit(victim, m);
    }

    /** 맞으면 붉게 번쩍임 (속성 rpg:flash, BOSS.flashTicks 틱) */
    static void flash(Entity e) {
        if (!(e instanceof BedrockMob m)) return;
        m.setProp("rpg:flash", true);
        Tasks.later((int) BOSS.num("flashTicks"), () -> {
            if (!m.isRemoved()) m.setProp("rpg:flash", false);
        });
    }

    /** 피해 숫자 · 치명타 · 참격 · 무기 맞힘 훅 (combat.js hitFeedback — mark = 숫자 앞 표시) */
    private static void feedback(LivingEntity target, ServerPlayer p, double amount, RpgCombat.Mark m, String mark) {
        feedbackAttacker = p;
        try {
            DamageNumbers.feedback(target, p, amount, m, mark);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss feedback", e);
        } finally {
            feedbackAttacker = null;
        }
    }

    /** 싸움에 든 사람 (보상 · 즉사 대상): 피해가 들어간 공격만 셈. dealt = 실제로 준 피해 (v2.12.0 딜 순위 — 무적일 때 때린 것은 싸움에 든 것으로만) */
    static void noteHit(Enc enc, ServerPlayer p, double amount, long now, double dealt) {
        if (!(amount > 0)) return;
        Part part = enc.parts.get(p.getUUID());
        if (part == null) {
            part = new Part(p, now);
            enc.parts.put(p.getUUID(), part);
        }
        part.dmg += Math.max(0, dealt);
        part.last = now;
        part.p = p;
    }

    /** 맞는 소품 (촉수 등): 체력을 줄이고 번쩍임 · 피해 숫자 → 모듈 propHit, 체력 0 이면 propDied (치우기는 모듈) */
    private static void propHurt(Hit h, long now) {
        Enc enc = h.enc;
        if (!"fight".equals(enc.state) || h.info.dead || !(h.amount > 0)) return;
        double amount = h.amount;
        noteHit(enc, h.p, amount, now, amount);
        double left = Math.max(0, h.target.getHealth() - amount);
        if (left > 0) h.target.setHealth((float) left);
        flash(h.target);
        feedback(h.target, h.p, amount, h.mark, h.close ? CLOSE_MARK : "");
        if (left <= 0) h.info.dead = true;
        try {
            enc.mod.propHit(enc, h.target, h.p, amount, left);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss prop", e);
        }
        if (left > 0) return;
        try {
            enc.mod.propDied(enc, h.target, h.p);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss prop died", e);
        }
    }

    private static void processHit(Hit h, long now) {
        Enc enc = h.enc;
        if (encs.get(enc.a) != enc || h.target.isRemoved()) return;
        if (h.prop) {
            if (h.info != null) {
                propHurt(h, now);
                return;
            }
            try {
                enc.mod.propHit(enc, h.target, h.p, h.amount, Double.NaN);
            } catch (RuntimeException e) {
                LOG.warn("[mmorpg] boss prop", e);
            }
            return;
        }
        Unit u = h.unit;
        if (!"fight".equals(enc.state) || u == null || !u.alive) return;
        double amount = h.amount;
        // 모듈 hurt: 받을 피해 · 피해 숫자 앞 표시 (v2.4.0 약점 …), 맞은 기록 h (h.near · h.close · h.skill)
        String mark = "";
        try {
            Hurt r = enc.mod.hurt(u, h.p, amount, h);
            if (r != null) {
                amount = r.amount();
                mark = r.mark() == null ? "" : r.mark();
            }
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss hurt", e);
        }
        if (!live(enc) || !u.alive) return;
        noteHit(enc, h.p, amount, now, u.immune ? 0 : amount);
        if (u.immune || amount <= 0) {
            showFaceText(h.p, "§7무적", Opt.of("dist", 2.4, "side", 0.5, "up", 0.1));
            return;
        }
        double left = Math.max(0, h.target.getHealth() - amount);
        if (left > 0) h.target.setHealth((float) left);
        flash(h.target);
        feedback(h.target, h.p, amount, h.mark, mark + (h.close ? CLOSE_MARK : ""));
        if (left <= 0) {
            unitDied(enc, u, h.p);
            return;
        }
        try {
            enc.mod.afterHurt(u, h.p, amount, left);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss after hurt", e);
        }
    }

    // ------------------------------------------------------------ 소품

    /**
     * 소품 (분신 · 폭탄): 받는 공격을 모듈이 처리하고 (propHit), 싸움이 끝나면 함께 치움.
     * hp &gt; 0 이면 맞는 소품 (v2.0.0 촉수): 엔티티 체력 (엔티티 정의의 최대 체력 = hp) 을 엔진이 줄이고 무적 시간 · 번쩍임 · 피해 숫자 (textHeight = 숫자 높이, 0 = 보통)
     */
    public static void addProp(Enc enc, Entity e, double hp, double textHeight) {
        if (e == null) return;
        enc.props.add(e);
        byEntity.put(e.getUUID(), enc);
        owned.add(e.getUUID());
        if (hp > 0) propInfo.put(e.getUUID(), new PropInfo(hp, textHeight));
    }

    public static void addProp(Enc enc, Entity e) {
        addProp(enc, e, 0, 0);
    }

    public static void dropProp(Enc enc, Entity e) {
        if (e == null) return;
        enc.props.remove(e);
        byEntity.remove(e.getUUID());
        owned.remove(e.getUUID());
        propInfo.remove(e.getUUID());
        if (!e.isRemoved()) e.discard();
    }

    /** 맞는 소품을 더는 맞지 않게 (물러나는 촉수 등): 공격은 가져가되 피해 없음 */
    public static void retireProp(Entity e) {
        PropInfo i = e == null ? null : propInfo.get(e.getUUID());
        if (i != null) i.dead = true;
    }

    /** 싸움이 가진 엔티티로 표시만 (감옥처럼 바닐라 피해를 그대로 받는 것): 남은 것 정리에서 빠짐 */
    public static void own(Entity e) {
        if (e != null) owned.add(e.getUUID());
    }

    public static void disown(Entity e) {
        if (e != null) owned.remove(e.getUUID());
    }

    /** 맞는 소품 정보 (없으면 null — 시험용) */
    public static PropInfo propInfoOf(Entity e) {
        return e == null ? null : propInfo.get(e.getUUID());
    }

    // ------------------------------------------------------------ 끝 (처치 · 초기화)

    private static void cleanup(Enc enc) {
        try {
            enc.mod.end(enc);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss end", e);
        }
        for (Entity e : new ArrayList<>(enc.props)) dropProp(enc, e);
        for (Unit u : enc.units) {
            if (u.boss == null) continue;
            byEntity.remove(u.boss.getUUID());
            unitById.remove(u.boss.getUUID());
            owned.remove(u.boss.getUUID());
        }
        Ticking.releaseArea(enc.area);
        enc.area = null;
        encs.remove(enc.a, enc);
    }

    /**
     * 초기화 (보상 없음, 재사용 대기 없음): 보스는 제단으로 돌아감. why = "empty" (싸우는 사람 없음) · "manual" (/boss reset · 관리 창) · "lost" (보스가 사라짐) ·
     * "removed" (제단이 없어짐) · "error"
     */
    public static void endEncounter(Enc enc, String why) {
        if (enc == null || encs.get(enc.a) != enc) return;
        for (Unit u : enc.units) {
            BedrockMob b = u.boss;
            try {
                if (valid(b) && u.alive) {
                    Vec3 at = b.position();
                    SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 2, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 3.0, "life", 0.5, "color",
                            rgb(u.def, "color"), "alpha", 0.9));
                    b.discard();
                }
            } catch (RuntimeException e) {
                // 무시
            }
        }
        if (!why.equals("error") && !why.equals("removed")) {
            say(enc, "§7[보스] " + enc.def.str("name") + "이(가) 제단으로 돌아갔습니다 " + (why.equals("empty") ? "(필드에 싸우는 사람이 없음)" : ""));
        }
        cleanup(enc);
    }

    /** [최소, 최대, 확률] → 개수 (확률이면 하나 더) */
    static int rollRange(List<Double> r) {
        if (r == null || r.size() < 2) return 0;
        double lo = r.get(0);
        double hi = r.get(1);
        int n = (int) (lo + Math.floor(random() * (hi - lo + 1)));
        if (r.size() > 2 && random() < r.get(2)) n += 1;
        return Math.max(0, n);
    }

    /** n 개를 64 씩 묶어 줌 */
    private static void giveMany(ServerPlayer p, Item item, int n) {
        if (item == null) return;
        for (int left = n; left > 0; left -= 64) Inv.give(p, new ItemStack(item, Math.min(64, left)));
    }

    /**
     * 보상 한 사람분 (아이템, 경험치) → 받은 것 글자. v2.12.0: ticketAdv = 고급 룬 뽑기권, 모듈 bonusLoot(enc) = 더 주는 것 {키: 개수} — 굴린 수에 더함
     */
    static List<String> reward(Enc enc, ServerPlayer p) {
        Obj L = enc.def.objOrNull("loot");
        if (L == null) L = BossKit.obj();
        Map<String, Double> bonus = null;
        try {
            bonus = enc.mod.bonusLoot(enc);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss bonus loot", e);
        }
        Map<String, Double> b = bonus == null ? Map.of() : bonus;
        Function<String, Integer> more = k -> (int) Math.max(0, Math.floor(b.getOrDefault(k, 0.0)));
        List<String> got = new ArrayList<>();
        giveLoot(got, p, Items.EMERALD, rollRange(L.numbersOrNull("emerald")) + more.apply("emerald"), "에메랄드");
        giveTickets(got, p, rollRange(L.numbersOrNull("ticket")), false, "룬 뽑기권");
        giveTickets(got, p, rollRange(L.numbersOrNull("ticketAdv")), true, "고급 룬 뽑기권");
        Obj raid = R.obj("RAID_MATERIALS");
        for (String k : List.of("stone", "bright", "scroll", "ore")) {
            giveLoot(got, p, GearItems.byId(raid.obj(k).str("item")), rollRange(L.numbersOrNull(k)), raid.obj(k).str("name"));
        }
        // 보스 재료 (v2.0.0): 보스 무기 제작용
        Obj mats = R.obj("BOSS_MATERIALS");
        for (String k : R.strings("BOSS_MATERIAL_ORDER")) {
            giveLoot(got, p, GearItems.byId(mats.obj(k).str("item")), rollRange(L.numbersOrNull(k)) + more.apply(k), mats.obj(k).str("name"));
        }
        double xp = L.numOr("xp", 0);
        if (xp > 0) {
            p.giveExperiencePoints((int) xp);
            got.add("경험치 " + Js.str(xp));
        }
        return got;
    }

    private static void giveLoot(List<String> got, ServerPlayer p, Item item, int n, String label) {
        if (n <= 0 || item == null) return;
        giveMany(p, item, n);
        got.add(label + " " + n);
    }

    private static void giveTickets(List<String> got, ServerPlayer p, int n, boolean adv, String label) {
        if (n <= 0) return;
        for (int left = n; left > 0; left -= 64) Inv.give(p, RuneItems.ticket(Math.min(64, left), adv));
        got.add(label + " " + n);
    }

    static boolean canReward(ServerPlayer p) {
        if (p == null || p.isRemoved()) return false;
        GameType gm = BossKit.modeOf(p);
        return gm != GameType.CREATIVE && gm != GameType.SPECTATOR;
    }

    /** 한 마리가 쓰러짐: 모두 쓰러졌으면 처치 (보상), 아니면 그 마리만 쓰러뜨리고 모듈에 알림 (남은 마리의 분노 등) */
    static void unitDied(Enc enc, Unit u, ServerPlayer lastHitter) {
        if (!u.alive || !"fight".equals(enc.state)) return;
        u.alive = false;
        u.cast = null;
        u.immune = false;
        boolean all = true;
        for (Unit x : enc.units) if (x.alive) all = false;
        if (all) {
            bossDied(enc, lastHitter, u);
            return;
        }
        BedrockMob b = u.boss;
        Vec3 at = valid(b) ? b.position() : u.home;
        if (b != null) {
            byEntity.remove(b.getUUID());
            unitById.remove(b.getUUID());
            owned.remove(b.getUUID());
        }
        double[] col = rgb(u.def, "color");
        SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1.5, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 3.5, "life", 0.6, "color", col, "alpha", 0.95));
        SkillFx.fx(enc.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", 4.0, "life", 0.8, "color", col, "alpha", 0.9));
        play(enc.dim, snd(u, "death"), at, 0.7, 1);
        say(enc, "§6[보스] §e" + (lastHitter != null ? lastHitter.getName().getString() : "누군가") + "§f님이 §6" + u.def.str("name") + "§f을(를) 쓰러뜨렸습니다!");
        try {
            enc.mod.unitDied(enc, u);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss unit died", e);
        }
        killEntity(b);
    }

    /** 바닐라 죽음 (쓰러지는 모습) — 안 되면 치움 */
    private static void killEntity(BedrockMob b) {
        if (!valid(b)) return;
        try {
            b.kill(b.level() instanceof ServerLevel sl ? sl : null);
        } catch (RuntimeException e) {
            if (!b.isRemoved()) b.discard();
        }
    }

    private static void bossDied(Enc enc, ServerPlayer lastHitter, Unit lastUnit) {
        if ("dead".equals(enc.state)) return;
        enc.state = "dead";
        BedrockMob b = (lastUnit != null ? lastUnit : enc.units.get(0)).boss;
        // 모듈이 쓰러지는 자리를 정리 (v2.3.0 니드호그: 날다가 쓰러지면 땅에 내려놓음)
        try {
            enc.mod.dying(enc);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss dying", e);
        }
        try {
            celebrate(enc, lastHitter, valid(b) ? b.position() : enc.center);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss reward", e);
        } finally {
            // 보상 중에 오류가 나도 싸움은 반드시 정리 (보스가 무적으로 남지 않게)
            cleanup(enc);
            killEntity(b);
        }
    }

    /** 보스 처치 훅 (v2.6.0 메인 퀘스트의 보스 처치): fn(보스 키, 보상을 받은 플레이어들) */
    private static final List<BiConsumer<String, List<ServerPlayer>>> killHooks = new ArrayList<>();

    public static void addBossKillHook(BiConsumer<String, List<ServerPlayer>> fn) {
        if (fn != null) killHooks.add(fn);
    }

    /** 시험용: 훅 빼기 */
    public static void removeBossKillHook(BiConsumer<String, List<ServerPlayer>> fn) {
        killHooks.remove(fn);
    }

    /** 처치 훅을 부름 (celebrate, 시험) */
    public static void notifyBossKilled(String key, List<ServerPlayer> players) {
        for (BiConsumer<String, List<ServerPlayer>> h : List.copyOf(killHooks)) {
            try {
                h.accept(key, players);
            } catch (RuntimeException e) {
                LOG.warn("[mmorpg] boss kill hook", e);
            }
        }
    }

    /** 세 자리마다 쉼표 (12,345) */
    public static String commas(double n) {
        String s = Js.str(Math.max(0, Js.round(n)));
        return s.replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
    }

    /** v2.12.0 초월 보스: 준 피해 순위 줄 (보스와 맞는 소품에 들어간 피해의 합, 많은 순서로 8명까지) */
    public static List<String> damageBoard(Enc enc) {
        List<Part> list = new ArrayList<>();
        for (Part x : enc.parts.values()) if (x.dmg > 0) list.add(x);
        list.sort(Comparator.comparingDouble((Part x) -> x.dmg).reversed());
        double total = 0;
        for (Part x : list) total += x.dmg;
        if (!(total > 0)) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add("§6[딜 순위] §f" + enc.def.str("name") + " §7— 모두 §f" + commas(total));
        for (int i = 0; i < Math.min(8, list.size()); i++) {
            Part x = list.get(i);
            String name = x.p != null ? x.p.getName().getString() : "?";
            lines.add("§e" + (i + 1) + ". §f" + name + " §6" + commas(x.dmg) + " §7(" + Js.str(Js.round((x.dmg / total) * 100)) + "%)");
        }
        return lines;
    }

    /** 처치: 보상 · 재사용 대기 · 알림 · 연출 (at = 마지막으로 쓰러진 자리) */
    private static void celebrate(Enc enc, ServerPlayer lastHitter, Vec3 at) {
        long now = Tasks.now();
        String name = enc.def.str("name");
        // 보상 받을 사람: 지금 필드 안이거나 participantGrace 안에 때린 사람 (크리에이티브 · 관전은 빼고, 방금 쓰러진 사람은 받음)
        Map<UUID, ServerPlayer> who = new LinkedHashMap<>();
        for (ServerPlayer p : Arena.playersIn(enc.a, 2)) if (canReward(p)) who.put(p.getUUID(), p);
        for (Map.Entry<UUID, Part> e : enc.parts.entrySet()) {
            if (now - e.getValue().last <= BOSS.num("participantGrace") && canReward(e.getValue().p)) who.put(e.getKey(), e.getValue().p);
        }
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : who.values()) names.add(p.getName().getString());
        for (ServerPlayer p : who.values()) {
            List<String> got = reward(enc, p);
            p.sendSystemMessage(Component.literal(BedrockText.toJava("§6[보스] §f" + name + " 처치! §7보상: §f" + (got.isEmpty() ? "없음" : String.join("§7, §f", got)))));
        }
        // v2.12.0 초월 보스: 준 피해 순위 + 모듈이 남기는 줄 — 보상을 받은 사람과 필드 둘레의 사람에게
        if ("transcend".equals(enc.def.strOrNull("tier"))) {
            List<String> notes = null;
            try {
                notes = enc.mod.killNote(enc);
            } catch (RuntimeException e) {
                LOG.warn("[mmorpg] boss kill note", e);
            }
            List<String> lines = new ArrayList<>(damageBoard(enc));
            if (notes != null) lines.addAll(notes);
            Map<UUID, ServerPlayer> to = new LinkedHashMap<>();
            for (ServerPlayer p : audience(enc)) to.put(p.getUUID(), p);
            to.putAll(who);
            for (String line : lines) say(enc, line, new ArrayList<>(to.values()));
        }
        // 퀘스트 (v2.6.0): 보상을 받은 사람 모두 처치로 셈
        notifyBossKilled(enc.key, new ArrayList<>(who.values()));
        MinecraftServer server = enc.dim.getServer();
        String by = !names.isEmpty() ? String.join(", ", names) : lastHitter != null ? lastHitter.getName().getString() : "누군가";
        server.getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava("§6[보스] §e" + by + "§f님이 §6" + name + "§f을(를) 쓰러뜨렸습니다!")), false);
        enc.a.cooldownUntil = System.currentTimeMillis() + (long) (enc.def.numOr("cooldown", 600) * 1000);
        Arena.saveAltars();
        // 연출: 큰 폭발 + 소리, 보스는 바닐라 죽음
        double[] col = rgb(enc.def, "color");
        for (int i = 0; i < 3; i++) {
            SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1 + i * 1.5, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 3.0 + i, "life", 0.6, "color", col,
                    "alpha", 0.95));
        }
        SkillFx.fx(enc.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", 5.0, "life", 0.9, "color", col, "alpha", 0.9));
        play(enc.dim, snd(enc, "death"), at, 0.9, 1);
        play(enc.dim, "random.totem", at, 1, 0.8);
        title(enc, "§6§l" + name + " 처치!", "§7보상을 받았습니다");
    }

    // ------------------------------------------------------------ 싸움 (매 틱)

    /** 패턴 틀 시작 (마리마다): steps = step(시작 뒤 틱, fn(u, now)) …, length = 끝나는 틱 (그동안 그 마리의 다른 패턴 · 평타 없음) */
    public static void cast(Unit u, String id, int length, List<Step> steps) {
        long now = Tasks.now();
        List<Step> abs = new ArrayList<>(steps.size());
        for (Step s : steps) abs.add(new Step(now + s.at(), s.fn()));
        abs.sort(Comparator.comparingLong(Step::at));
        u.cast = new Cast(id, now + length, abs);
    }

    public static void cast(Unit u, String id, int length, Step... steps) {
        cast(u, id, length, List.of(steps));
    }

    /** 보스를 제자리에 (이동 막기) / 풀기: 엔티티 사건으로 이동 빠르기를 바꿈. 분노 (fast) 면 빠른 걸음으로 풂 */
    public static void hold(Unit u, boolean on) {
        if (!valid(u.boss) || u.held == on) return;
        u.held = on;
        u.boss.triggerEvent(on ? "rpg:hold" : u.fast ? "rpg:release_fast" : "rpg:release");
    }

    private static final double TAU = Math.PI * 2;

    /** 각도 차이 a - b (-π ~ π) */
    static double angDiff(double a, double b) {
        double d = (a - b) % TAU;
        if (d > Math.PI) d -= TAU;
        if (d < -Math.PI) d += TAU;
        return d;
    }

    /**
     * 몸 방향을 바로 정함 (움직이지 않는 보스): ang = 가로 각도 (라디안, +x 에서 +z 쪽으로). 제 자리 (home + 높이) 에서 그쪽을 봄 (고개를 숙이지 않게).
     * 늘 제 자리로 옮기므로 스킬 넉백 등으로 조금 밀려도 돌아옴
     */
    static void setFacing(Unit u, double ang) {
        u.faceNow = ang;
        BedrockMob b = u.boss;
        if (!valid(b)) return;
        BossKit.place(b, u.dim, new Vec3(u.home.x, u.home.y + liftOf(u), u.home.z), BossKit.yawOfAng(ang));
    }

    /** 몸 돌리기 (움직이지 않는 보스): dir = 가로 방향 (길이 상관없음). 매 틱 stationary.turn 도씩 그쪽으로 */
    public static void face(Unit u, Vec3 dir) {
        if (!stationary(u) || Math.hypot(dir.x, dir.z) < 1e-6) return;
        u.faceTo = Math.atan2(dir.z, dir.x);
    }

    /** 제 자리 옮기기 (v2.1.0 거대 웜의 굴 이동): home 을 at (바닥 높이) 으로 바꾸고 바로 그 자리로 (ang = 바라볼 각도, null 이면 지금 방향) */
    public static void seat(Unit u, Vec3 at, Double ang) {
        u.home = at;
        double a = ang != null ? ang : u.faceNow != null ? u.faceNow : 0;
        setFacing(u, a);
        u.faceTo = a;
    }

    /** 자리와 방향을 바로 정함 (v2.2.0 헤엄치는 보스: 모듈이 매 틱 옮김). seat 과 달리 바라보려는 쪽 (faceTo) 은 그대로 */
    public static void pose(Unit u, Vec3 at, double ang) {
        u.home = at;
        setFacing(u, ang);
    }

    /** 아직 더 돌아야 하는 각도 (라디안, 0 = 다 돌았음) */
    public static double turnLeft(Unit u) {
        if (u.faceTo == null || u.faceNow == null) return 0;
        return Math.abs(angDiff(u.faceTo, u.faceNow));
    }

    /** 매 틱: 원하는 방향 쪽으로 stationary.turn 도까지 */
    private static void turnTick(Unit u) {
        if (u.faceTo == null || u.faceNow == null) return;
        double d = angDiff(u.faceTo, u.faceNow);
        if (Math.abs(d) < 1e-4) return;
        double step = (u.def.obj("stationary").numOr("turn", 16) * Math.PI) / 180;
        setFacing(u, Math.abs(d) <= step ? u.faceTo : u.faceNow + Math.signum(d) * step);
    }

    /** 빠른 걸음으로 (짝 잃은 분노): 멈춰 있지 않으면 바로 바꿈 */
    public static void setFast(Unit u) {
        u.fast = true;
        if (u.held || !valid(u.boss)) return;
        u.boss.triggerEvent("rpg:release_fast");
    }

    /**
     * 동작 틀기 (animation.rpg.boss.&lt;마리 키&gt;.&lt;name&gt;): target = 틀 엔티티 (null = 그 마리), controller · stop = playAnimation 선택
     * (땅속처럼 다음 동작까지 유지할 때 stop "0")
     */
    public static void anim(Unit u, String name, Entity target, String controller, String stop) {
        Entity t = target != null ? target : u.boss;
        if (t instanceof BedrockMob m && !m.isRemoved()) m.playAnimation("animation.rpg.boss." + u.key + "." + name, controller, stop);
    }

    public static void anim(Unit u, String name, Entity target) {
        anim(u, name, target, null, null);
    }

    public static void anim(Unit u, String name) {
        anim(u, name, null, null, null);
    }

    /** 모듈이 대상에서 뺀 사람 (삼켜진 사람 등, 모듈 targetable) 을 뺌 */
    static List<ServerPlayer> targetable(Enc enc, List<ServerPlayer> list) {
        try {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : list) if (enc.mod.targetable(enc, p)) out.add(p);
            return out;
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss targets", e);
            return list;
        }
    }

    /** 평타: 앞 부채꼴 (그 마리에게 가장 가까운 플레이어가 닿으면). 시작했으면 true (시험 · 사진첩이 바로 부를 수 있음) */
    public static boolean melee(Unit u, long now, List<ServerPlayer> list) {
        Obj m = u.def.objOrNull("melee");
        if (m == null || now < u.meleeAt || !valid(u.boss)) return false;
        BedrockMob b = u.boss;
        double half = bossSize(u.def).half();
        double reach = m.num("reach");
        // 닿는 사람 가운데 가장 가까운 사람 — v2.20.0 도발 (아이기스의 방패) 한 사람이 닿으면 그 사람
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), b.position()) - half - 0.3 <= reach) pool.add(p);
        ServerPlayer tgt = meleeTarget(u, pool, b.position());
        if (tgt == null) return false;
        double dx = tgt.getX() - b.getX();
        double dz = tgt.getZ() - b.getZ();
        double L = Math.hypot(dx, dz);
        if (L == 0) L = 1;
        Vec3 dir = new Vec3(dx / L, 0, dz / L);
        Vec3 apex = b.position();
        double radius = half + reach;
        int windup = (int) m.num("windup");
        double cone = m.num("cone");
        fanMarks(u.dim, apex, dir, Js.round(radius), cone, windup, new double[] {1.0, 0.25, 0.15});
        face(u, dir); // 움직이지 않는 보스: 대상 쪽으로 몸을 돌림 (걷는 보스는 스스로 봄)
        anim(u, m.strOr("anim", "slash"));
        sound(u, snd(u, "swing"), b.position(), 0.8, 1);
        u.meleeAt = now + (long) Js.round(m.num("every") * u.cdMult);
        hold(u, true);
        double damage = m.num("damage");
        cast(u, "melee", windup + 6, step(windup, (e, t) -> {
            for (ServerPlayer p : targetable(e.enc, fighters(e))) {
                if (!inFan(p.position(), apex, dir, radius, cone, 0.5)) continue;
                bossHit(e, p, damage, Opt.of("knock", knock(dir.x * 1.2, dir.z * 1.2, 0.35), "directional", true));
            }
            sound(e, snd(e, "hit"), apex, 0.9, 1);
            SkillFx.fx(e.dim, "rpg:slash_heavy_r_glow", new Vec3(apex.x + dir.x * radius * 0.5, apex.y + 1.2, apex.z + dir.z * radius * 0.5),
                    Opt.of("dir", dir, "speed", 0.0, "spin", 0.0, "size", radius * 0.6, "life", 0.25, "color", rgb(e.def, "color"), "alpha", 0.95));
        }), step(windup + 5, (e, t) -> hold(e, false)));
        return true;
    }

    /** 제 자리 (home) 에서 너무 멀어졌으면 (밀려남 · 끼임) 제 자리로. 움직이지 않는 보스는 조금만 밀려도 조용히 제 자리로 (방향은 그대로) */
    private static void leash(Unit u) {
        BedrockMob b = u.boss;
        Vec3 h = u.home;
        if (stationary(u)) {
            Vec3 l = b.position();
            if (Math.hypot(l.x - h.x, l.z - h.z) > 0.25 || Math.abs(l.y - (h.y + liftOf(u))) > 0.25) setFacing(u, u.faceNow != null ? u.faceNow : 0);
            return;
        }
        if (hdist(b.position(), h) <= u.leash && Math.abs(b.getY() - h.y) < 6) return;
        BossKit.teleport(b, new Vec3(h.x, h.y + liftOf(u), h.z));
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(h.x, h.y + 2, h.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.5, "life", 0.4, "color", rgb(u.def, "color"),
                "alpha", 0.8));
    }

    private static void fightTick(Enc enc, long now) {
        for (Unit u : enc.units) {
            if (u.alive && !valid(u.boss)) {
                endEncounter(enc, "lost");
                return;
            }
        }
        List<ServerPlayer> list = fighters(enc);
        if (list.isEmpty()) {
            if (enc.emptySince == null) enc.emptySince = now;
            if (now - enc.emptySince > BOSS.num("resetAfter")) {
                endEncounter(enc, "empty");
                return;
            }
        } else enc.emptySince = null;
        if (now % 10 == 0) for (Unit u : enc.units) if (u.alive) leash(u);
        // 헤엄치는 보스 (swim) · 걷고 나는 보스 (drive) 는 모듈이 몸을 돌림
        for (Unit u : enc.units) if (u.alive && stationary(u) && !u.def.truthy("swim") && !u.def.truthy("drive")) turnTick(u);
        try {
            enc.mod.tick(enc, now, list);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss tick", e);
        }
        if (!live(enc)) return;
        // 평타 · 패턴 고르기의 대상 (삼켜진 사람 등은 모듈이 뺌)
        List<ServerPlayer> targets = targetable(enc, list);
        // 원거리 견제 (v2.3.0): 멀리 떨어진 사람에게 던짐 (틀 없이), 날아가는 것이 떨어짐
        try {
            farTrack(enc, now, targets);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss far track", e);
        }
        for (Unit u : enc.units) {
            if (!u.alive || u.def.objOrNull("far") == null) continue;
            try {
                farTick(enc, u, now, targets);
            } catch (RuntimeException e) {
                LOG.warn("[mmorpg] boss far", e);
            }
        }
        try {
            farShotTick(enc, now);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss far shot", e);
        }
        if (!live(enc)) return;
        for (Unit u : enc.units) {
            if (!u.alive) continue;
            unitTick(enc, u, now, targets);
            if (!live(enc)) return;
        }
    }

    // ------------------------------------------------------------ 원거리 견제 (v2.3.0)

    /** 원거리 견제 모양: 날아가는 파티클 · 색 · 크기 · 발밑 원 색 · 떨어질 때 (splash · dust · fire · spike · ice, 색 mark) · 던질 때 · 떨어질 때 소리 */
    public record Look(String shot, double[] color, double size, double[] disc, String hit, double[] mark, String sound, String land) {}

    public static final Map<String, Look> FAR_LOOKS = Map.of(
            "fire", new Look("rpg:glob", new double[] {1.0, 0.55, 0.15}, 1.0, new double[] {1.0, 0.5, 0.14}, "fire", new double[] {1.0, 0.5, 0.14}, "mob.blaze.shoot",
                    "random.explode"),
            "rock", new Look("rpg:boulder", new double[] {1, 1, 1}, 0.9, new double[] {0.66, 0.52, 0.38}, "dust", new double[] {0.56, 0.48, 0.4}, "mob.irongolem.throw",
                    "dig.stone"),
            "ink", new Look("rpg:glob", new double[] {0.14, 0.1, 0.22}, 1.1, new double[] {0.62, 0.3, 0.96}, "splash", new double[] {0.2, 0.12, 0.3}, "mob.squid.ambient",
                    "random.splash"),
            "venom", new Look("rpg:glob", new double[] {0.55, 0.95, 0.25}, 1.0, new double[] {0.55, 0.95, 0.25}, "splash", new double[] {0.55, 0.95, 0.25},
                    "mob.llama.spit", "random.fizz"),
            "spike", new Look("rpg:glob", new double[] {0.74, 0.3, 0.96}, 0.8, new double[] {0.74, 0.3, 0.96}, "spike", new double[] {0.74, 0.3, 0.96},
                    "mob.enderdragon.flap", "random.fizz"),
            // v2.4.0 스콜과 하티의 밤: 달 서리 (눈밭에서도 보이게 원은 짙은 파랑)
            "frost", new Look("rpg:glob", new double[] {0.78, 0.93, 1.0}, 1.0, new double[] {0.3, 0.55, 1.0}, "splash", new double[] {0.75, 0.92, 1.0},
                    "mob.snowgolem.shoot", "random.glass"),
            // v2.5.0 이미르: 얼음 창 (날아가는 쪽으로 길쭉한 창, 떨어진 자리에 얼음 가시가 솟음)
            "ice", new Look("rpg:ice_lance", new double[] {0.72, 0.93, 1.0}, 1.6, new double[] {0.2, 0.5, 1.0}, "ice", new double[] {0.75, 0.93, 1.0},
                    "mob.irongolem.throw", "random.glass"));

    /** 큰 패턴 (모듈 farOk 가 false) 이 끝난 뒤 원거리 견제를 쉬는 틱 */
    public static final long FAR_GRACE = 80;
    /** 이만큼 (틱) 계속 멀리 있어야 노림 (보스에게 밀려나거나 잠깐 물러선 사람은 아님) */
    public static final long FAR_SETTLE = 40;

    /** 살아 있는 마리 중 가장 가까운 몸 겉면 (가로: 가운데 − 반폭) 까지의 거리 */
    static double farDist(Enc enc, ServerPlayer p) {
        double d = Double.POSITIVE_INFINITY;
        for (Unit x : enc.units) {
            if (!x.alive || !valid(x.boss)) continue;
            d = Math.min(d, hdist(p.position(), x.boss.position()) - bossSize(x.def).half());
        }
        return d;
    }

    /** 원거리 견제 대상: 몸 겉면에서 far.from 칸 넘게 떨어진 사람, 가장 먼 사람부터 count 명. 속박된 사람은 노리지 않음. ok(p) = 더 거를 것 */
    public static List<ServerPlayer> farTargets(Unit u, List<ServerPlayer> list, Predicate<ServerPlayer> ok) {
        Obj F = u.def.obj("far");
        record D(ServerPlayer p, double d) {}
        List<D> ds = new ArrayList<>();
        for (ServerPlayer p : list) {
            if (Status.isRooted(p) || !ok.test(p)) continue;
            double d = farDist(u.enc, p);
            if (d > F.num("from") && d < Double.POSITIVE_INFINITY) ds.add(new D(p, d));
        }
        ds.sort(Comparator.comparingDouble((D x) -> x.d()).reversed());
        List<ServerPlayer> out = new ArrayList<>();
        for (int i = 0; i < Math.min((int) F.numOr("count", 2), ds.size()); i++) out.add(ds.get(i).p());
        return out;
    }

    /** 매 틱: 모듈이 쉬게 하는지 (쉰 마지막 틱) · 사람마다 멀리 있기 시작한 틱 */
    private static void farTrack(Enc enc, long now, List<ServerPlayer> list) {
        List<Unit> units = new ArrayList<>();
        for (Unit u : enc.units) if (u.alive && u.def.objOrNull("far") != null && valid(u.boss)) units.add(u);
        if (units.isEmpty()) return;
        for (Unit u : units) {
            boolean ok = !u.immune && now >= u.staggerUntil;
            if (ok) {
                try {
                    ok = enc.mod.farOk(u, now);
                } catch (RuntimeException e) {
                    LOG.warn("[mmorpg] boss far ok", e);
                    ok = false;
                }
            }
            if (!ok) u.farPausedAt = now;
        }
        double from = Double.POSITIVE_INFINITY;
        for (Unit u : units) from = Math.min(from, u.def.obj("far").num("from"));
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : list) {
            if (farDist(enc, p) <= from) continue;
            seen.add(p.getUUID());
            enc.farSince.putIfAbsent(p.getUUID(), now);
        }
        enc.farSince.keySet().removeIf(id -> !seen.contains(id));
    }

    /** 마리마다: 때가 되면 멀리 떨어진 사람 발밑에 원 + 던짐 (떨어지는 것은 farShotTick). 쉬는 중 · 쉰 직후 · 대상이 없으면 1초 뒤 다시 봄 */
    private static void farTick(Enc enc, Unit u, long now, List<ServerPlayer> list) {
        Obj F = u.def.obj("far");
        if (!valid(u.boss)) return;
        if (u.farAt == null) u.farAt = now + (long) F.numOr("first", 200);
        if (now < u.farAt) return;
        boolean rested = u.farPausedAt == null || now - u.farPausedAt >= FAR_GRACE;
        List<ServerPlayer> far = rested ? farTargets(u, list, p -> now - enc.farSince.getOrDefault(p.getUUID(), now) >= FAR_SETTLE) : List.of();
        if (far.isEmpty()) {
            u.farAt = now + 20;
            return;
        }
        double[] ev = BossKit.pair(F, "every");
        double lo = ev[0];
        double hi = ev[1];
        u.farAt = now + (long) Js.round((lo + Math.floor(random() * (hi - lo + 1))) * u.cdMult);
        farThrow(enc, u, now, far);
    }

    /** 원거리 견제 한 번: 대상 발밑에 원 + 던진 것이 포물선으로 (떨어지는 것은 farShotTick — 시험 · 사진첩이 바로 부를 수 있게 따로) */
    public static void farThrow(Enc enc, Unit u, long now, List<ServerPlayer> far) {
        Obj F = u.def.obj("far");
        Obj S = farStyleOf(enc, u);
        Look L = FAR_LOOKS.getOrDefault(S.strOr("look", "rock"), FAR_LOOKS.get("rock"));
        Vec3 from = null;
        try {
            from = enc.mod.farFrom(u, S);
        } catch (RuntimeException e) {
            from = null;
        }
        Vec3 at = u.boss.position();
        if (from == null) from = new Vec3(at.x, at.y + bossSize(u.def).height() * 0.75, at.z);
        int windup = (int) S.num("windup");
        double t = windup / 20.0;
        double radius = S.num("radius");
        for (ServerPlayer p : far) {
            // 떨어지는 자리 = 서 있는 바닥 (뛰는 중이면 발밑 땅)
            Vec3 spot = new Vec3(p.getX(), groundUnder(p, u.center.y), p.getZ());
            disc(u.dim, spot, radius, windup, L.disc());
            BossKit.Arc arc = lobArc(from, new Vec3(spot.x, spot.y + 0.3, spot.z), t);
            SkillFx.fx(u.dim, L.shot(), from, Opt.of("dir", arc.dir(), "speed", arc.speed(), "size", L.size(), "life", t, "spin", random() * 360, "color", L.color(),
                    "alpha", 1.0));
            showFaceText(p, "§e§l" + S.str("name") + "! §f발밑 원 밖으로", Opt.of("dist", 2.4, "up", 0.45));
            enc.farShots.add(new FarShot(u, spot, now + windup, S));
        }
        sound(u, L.sound(), from, 0.9, 1);
        if (!enc.farTold) {
            enc.farTold = true;
            double bonus = BOSS.obj("close").num("bonus");
            say(enc, "§e[보스] " + S.str("name") + ": §f" + u.def.str("name") + "에게서 " + Js.str(F.num("from")) + "칸 넘게 떨어져 있으면 가장 먼 사람부터 발밑에 원이 뜨고 "
                    + S.str("name") + "이(가) 날아옵니다 — 원 밖으로" + (bonus > 0 ? " §7(가까이 붙어 싸우면 준 피해 +" + Js.str(Js.round(bonus * 100)) + "%)" : ""));
        }
    }

    /** 이번 원거리 견제의 모양 (v2.4.0): 마리 설정 far 에 모듈 farStyle(u) 가 돌려준 것을 덮어씀. 던질 때 정해 날아가는 것에 붙임 */
    private static Obj farStyleOf(Enc enc, Unit u) {
        Obj F = u.def.obj("far");
        Obj over = null;
        try {
            over = enc.mod.farStyle(u);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss far style", e);
        }
        return over != null ? BossKit.merge(F, over) : F;
    }

    /** 날아간 것이 떨어짐: 원 안 (반지름 + 0.3칸, 위아래 3칸) 의 사람에게 피해 + 효과 (때린 엔티티 없이: 게임이 보스 쪽에서 밀어내지 않음) */
    private static void farShotTick(Enc enc, long now) {
        if (enc.farShots.isEmpty()) return;
        List<FarShot> due = new ArrayList<>();
        List<FarShot> keep = new ArrayList<>();
        for (FarShot s : enc.farShots) (now >= s.hitAt() ? due : keep).add(s);
        if (due.isEmpty()) return;
        enc.farShots = keep;
        List<ServerPlayer> list = targetable(enc, fighters(enc));
        for (FarShot s : due) {
            Unit u = s.u();
            Obj F = s.style() != null ? s.style() : u.def.obj("far");
            Look L = FAR_LOOKS.getOrDefault(F.strOr("look", "rock"), FAR_LOOKS.get("rock"));
            double radius = F.num("radius");
            farImpact(enc.dim, L, s.spot(), radius);
            play(enc.dim, L.land(), s.spot(), 0.9, 1);
            if (!u.alive || !valid(u.boss)) continue;
            for (ServerPlayer p : list) {
                // 원이 뜬 뒤 속박된 사람 (감옥 · 뿌리 등으로 비킬 수 없게 된 사람) 은 맞지 않음
                if (hdist(p.position(), s.spot()) > radius + 0.3 || Math.abs(p.getY() - s.spot().y) > 3 || Status.isRooted(p)) continue;
                boolean landed = bossHit(u, p, F.num("damage"), Opt.of("noSource", true, "cause", "projectile", "effect", BossKit.effect(F.objOrNull("effect"))));
                double burn = F.numOr("burn", 0);
                if (landed && burn > 0 && !p.isRemoved()) p.igniteForSeconds((float) burn);
            }
        }
    }

    /** 떨어진 자리 연출 */
    static void farImpact(ServerLevel dim, Look L, Vec3 at, double radius) {
        double[] c = L.mark();
        switch (L.hit()) {
            case "splash" -> SkillFx.fx(dim, "rpg:splash", new Vec3(at.x, at.y + radius * 0.75, at.z), Opt.of("size", radius, "life", 0.6, "color", c, "alpha", 0.95));
            case "fire" -> {
                SkillFx.fx(dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", radius * 1.2, "life", 0.45, "color", c, "alpha",
                        0.95));
                SkillFx.fx(dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", 0.5, "color", c, "alpha", 0.9));
            }
            case "ice" -> {
                SkillFx.fx(dim, "rpg:ice_spike", at, Opt.of("size", radius * 0.95, "life", 1.2, "spin", 0.0, "color", c, "alpha", 1.0));
                SkillFx.fx(dim, "rpg:splash", new Vec3(at.x, at.y + radius * 0.6, at.z), Opt.of("size", radius * 0.9, "life", 0.5, "color", c, "alpha", 0.9));
                SkillFx.fx(dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2, "life", 1.0,
                        "color", new double[] {0.35, 0.45, 0.62}, "alpha", 0.85));
            }
            case "spike" -> {
                SkillFx.fx(dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2.2, "life", 1.0,
                        "color", c, "alpha", 0.9));
                SkillFx.fx(dim, "rpg:splash", new Vec3(at.x, at.y + radius * 0.6, at.z), Opt.of("size", radius * 0.8, "life", 0.5, "color", c, "alpha", 0.9));
            }
            default -> SkillFx.fx(dim, "rpg:ground_crack", new Vec3(at.x, at.y + 0.05, at.z), Opt.of("dir", Vec3.ZERO, "spin", random() * 360, "size", radius * 2.2,
                    "life", 1.0, "color", new double[] {0.36, 0.28, 0.22}, "alpha", 0.85));
        }
        SkillFx.fx(dim, "rpg:dust", new Vec3(at.x, at.y + radius * 0.35, at.z), Opt.of("size", radius, "life", 0.7, "color", c, "alpha", 0.8));
    }

    // ------------------------------------------------------------ 마리 · 틱

    /** 한 마리의 틱: 진행 중인 틀의 단계, 없으면 패턴 고르기 → 평타 */
    private static void unitTick(Enc enc, Unit u, long now, List<ServerPlayer> list) {
        if (u.cast != null) {
            Cast c = u.cast;
            while (!c.steps.isEmpty() && c.steps.peekFirst().at() <= now) {
                Step st = c.steps.pollFirst();
                try {
                    st.fn().run(u, now);
                } catch (RuntimeException e) {
                    LOG.warn("[mmorpg] boss step " + c.id, e);
                }
                if (!live(enc) || !u.alive) return;
                // 단계가 틀을 끊거나 새 틀로 바꿨으면 (속박 · 반격 등) 옛 틀의 남은 단계는 하지 않음
                if (u.cast != c) return;
            }
            if (c.steps.isEmpty() && now >= c.until && u.cast == c) u.cast = null;
            return;
        }
        if (now < u.staggerUntil || list.isEmpty()) return;
        boolean started = false;
        try {
            started = enc.mod.pick(u, now, list);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss pick", e);
        }
        if (!started && live(enc)) melee(u, now, list);
    }

    private static void tick() {
        long now = Tasks.now();
        for (Enc enc : new ArrayList<>(encs.values())) {
            try {
                if ("summon".equals(enc.state)) {
                    summonFx(enc, now);
                    if (now >= enc.spawnAt) spawnBoss(enc);
                    continue;
                }
                if ("fight".equals(enc.state)) fightTick(enc, now);
            } catch (RuntimeException e) {
                LOG.warn("[mmorpg] boss tick " + enc.key, e);
            }
        }
        BossKit.cleanOwn(now);
    }

    private static void summonFx(Enc enc, long now) {
        long k = now - enc.t0;
        if (k % 5 != 0) return;
        Vec3 c = enc.center;
        double delay = BOSS.num("summonDelay");
        double r = 1 + (k / delay) * 3;
        double[] col = rgb(enc.def, "color");
        SkillFx.fx(enc.dim, "rpg:shock_ring", new Vec3(c.x, c.y + 0.1, c.z), Opt.of("dir", Vec3.ZERO, "size", r, "life", 0.5, "color", col, "alpha", 0.85));
        SkillFx.fx(enc.dim, "rpg:skill_rune", new Vec3(c.x, c.y + 0.12, c.z), Opt.of("size", 2.5, "life", 0.4, "spin", (double) (k * 9), "speed", 60.0, "grow", 0.0,
                "progress", Math.min(1, k / delay), "color", col, "alpha", 0.9));
        // 여러 마리: 나올 자리마다 모이는 고리
        for (Unit u : enc.units) {
            if (u == enc) continue;
            SkillFx.fx(enc.dim, "rpg:shock_ring", new Vec3(u.home.x, u.home.y + 0.1, u.home.z), Opt.of("dir", Vec3.ZERO, "size", Math.max(0.5, 3.5 - (k / delay) * 3),
                    "life", 0.5, "color", rgb(u.def, "color"), "alpha", 0.85));
        }
        if (k % 20 == 0) play(enc.dim, "mob.warden.heartbeat", c, 0.8, 1);
    }

    // ------------------------------------------------------------ 제단 누르기

    static String fmtTime(double sec) {
        long s = (long) Math.max(0, Math.ceil(sec));
        return (s / 60) + "분 " + String.format("%02d", s % 60) + "초";
    }

    /**
     * 제단 누르기 (애드온 rpg:boss_altar 블록 부품 → onAltarTouch — Arena.onAltarUse): 웅크린 관리자 (운영자 · 크리에이티브) = 관리 창,
     * 싸우는 중 = 체력 %, 짓는 중 · 필드 없음 · 재사용 대기 = 알림, 아니면 도전 창 → 도전 = 소환
     */
    public static void onAltarTouch(ServerPlayer player, Arena.Altar a) {
        if (a == null || player == null || player.isRemoved()) return;
        Obj def = BOSSES.obj(a.key);
        if (player.isShiftKeyDown() && Arena.canManage(player)) {
            manageMenu(player, a);
            return;
        }
        Enc enc = encs.get(a);
        if (enc != null) {
            Function<Unit, String> pct = u -> Js.str("fight".equals(enc.state) ? Js.round(ratio(u) * 100) : 100);
            String hp;
            if (enc.units.size() > 1) {
                List<String> parts = new ArrayList<>();
                for (Unit u : enc.units) parts.add(u.def.strOr("short", u.def.str("name")) + " " + pct.apply(u) + "%");
                hp = String.join(" · ", parts);
            } else hp = pct.apply(enc) + "%";
            BossKit.notify(player, "§6" + def.str("name") + " §7전투 중 §8· §f체력 " + hp + " §8· §f필드 안 " + fighters(enc).size() + "명", 60);
            return;
        }
        if (Arena.isBuilding(a)) {
            BossKit.notify(player, "§7보스 필드를 짓는 중입니다", 40);
            return;
        }
        if (!a.built) {
            BossKit.notify(player, Arena.canManage(player) ? "§7보스 필드가 없습니다 §8· §f웅크리고 눌러 필드를 지으세요"
                    : "§7보스 필드가 없어 도전할 수 없습니다 §8· §f운영자가 필드를 지어야 합니다", 60);
            return;
        }
        double left = (a.cooldownUntil - System.currentTimeMillis()) / 1000.0;
        if (left > 0) {
            BossKit.notify(player, "§7" + def.str("name") + "이(가) 다시 나타나기까지 §f" + fmtTime(left), 60);
            return;
        }
        challengeMenu(player, a, def).show(player, sel -> {
            if (sel != 0 || encs.containsKey(a) || !Arena.altars().contains(a) || !a.built) return;
            if ((a.cooldownUntil - System.currentTimeMillis()) / 1000.0 > 0) return;
            summon(a, player);
        });
    }

    /** 도전 창 (레벨 · 권장 인원 · 규칙 · 보상 · 재사용 대기) */
    static MenuForm challengeMenu(ServerPlayer player, Arena.Altar a, Obj def) {
        Obj loot = def.objOrNull("loot");
        Obj mats = R.obj("BOSS_MATERIALS");
        // 보스 재료 (v2.0.0): 이 보스가 주는 것만
        List<String> matList = new ArrayList<>();
        for (String k : R.strings("BOSS_MATERIAL_ORDER")) {
            if (loot != null && loot.truthy(k)) matList.add(mats.obj(k).str("color") + mats.obj(k).str("name") + "§7");
        }
        // v2.3.0 근접 보너스 · 원거리 견제 안내
        Obj far = def.objOrNull("far");
        List<Obj> units = def.objsOrNull("units");
        if (far == null && units != null) {
            for (Obj u : units) {
                if (u.objOrNull("far") != null) {
                    far = u.obj("far");
                    break;
                }
            }
        }
        List<String> rules = new ArrayList<>();
        Obj close = BOSS.obj("close");
        if (close.num("bonus") > 0) {
            rules.add("§c근접 보너스: §f보스 몸 " + Js.str(close.num("range")) + "칸 안에서 준 피해 +" + Js.str(Js.round(close.num("bonus") * 100)) + "%");
        }
        // v2.4.0: 밤에 다른 것을 던지는 보스 (스콜과 하티: far.night)
        if (far != null) {
            Obj night = far.objOrNull("night");
            rules.add("§e원거리 견제: §f" + Js.str(far.num("from")) + "칸 넘게 떨어지면 " + far.str("name") + (night != null ? " §7(밤: " + night.str("name") + ")" : ""));
        }
        String rule = rules.isEmpty() ? "" : "\n" + String.join(" §8· ", rules);
        // v2.12.0 초월 보스: 머리말 + 깨는 법 (설정 notes 한 줄씩), 보스 재료 줄은 "초월 장비 재료"
        boolean transcend = "transcend".equals(def.strOrNull("tier"));
        List<Object> notesRaw = def.listOrNull("notes");
        String notes = "";
        if (transcend && notesRaw != null && !notesRaw.isEmpty()) {
            List<String> ns = new ArrayList<>();
            for (Object o : notesRaw) ns.add(String.valueOf(o));
            notes = "\n\n§6§l패턴을 깨는 법§r\n" + String.join("\n", ns);
        }
        String matNote = matList.isEmpty() ? "" : "\n§7보스 재료: " + String.join(" · ", matList) + " §8(" + (transcend ? "초월 장비 재료" : "대장장이 보스 무기 제작") + ")";
        String with = "";
        if (units != null) {
            List<String> names = new ArrayList<>();
            for (Obj u : units) names.add(u.str("name"));
            with = "\n§7함께 나옴: §f" + String.join(" §7· §f", names) + " §7(모두 쓰러뜨려야 보상)";
        }
        String body = (transcend ? "§c§l[초월 보스]§r " : "") + "§f" + def.strOr("title", "") + " §7— §fLv." + Js.str(def.num("level")) + with
                + "\n§7권장: " + (transcend ? "초월 장비를 갖춘 4명 파티" : "3~4명 파티 (혼자서는 사실상 어려움)") + rule + notes
                + "\n\n§7보상: 에메랄드 · 경험치 · 룬 뽑기권" + (transcend ? " · 고급 룬 뽑기권" : "") + " · 강화 재료" + matNote
                + "\n§7잡힌 뒤 " + Js.str(Js.round(def.numOr("cooldown", 600) / 60)) + "분 동안 다시 나오지 않습니다";
        return new MenuForm("§l" + def.str("name") + "에게 도전", body).button("§4도전").button("취소");
    }

    /** 제단 관리 창 (웅크린 운영자 · 크리에이티브): 필드 (다시) 짓기 · 재사용 대기 없애기 · 보스 초기화 · 닫기 */
    static void manageMenu(ServerPlayer player, Arena.Altar a) {
        Obj def = BOSSES.obj(a.key);
        Enc enc = encs.get(a);
        long cd = (long) Math.max(0, Math.ceil((a.cooldownUntil - System.currentTimeMillis()) / 1000.0));
        new MenuForm("§l" + def.str("name") + " 제단 관리", "§7필드: " + (a.built ? "§a지어짐" : "§c없음") + " §8· §7재사용 대기: §f" + cd + "초" + (enc != null ? " §8· §c전투 중" : ""))
                .button(a.built ? "필드 다시 짓기" : "필드 짓기")
                .button("재사용 대기 없애기")
                .button("보스 초기화 (돌려보내기)")
                .button("닫기")
                .show(player, sel -> {
                    if (sel == 0) Arena.askBuild(player, a);
                    else if (sel == 1) {
                        a.cooldownUntil = 0;
                        Arena.saveAltars();
                        BossKit.notify(player, "§a재사용 대기를 없앴습니다", 40);
                    } else if (sel == 2 && encs.get(a) != null) endEncounter(encs.get(a), "manual");
                });
    }

    /** 제단이 없어지면 싸움도 정리 */
    static void onAltarRemoved(Arena.Altar a) {
        Enc enc = encs.get(a);
        if (enc != null) endEncounter(enc, "removed");
    }

    // ------------------------------------------------------------ 남은 것 정리

    private static Set<String> strayCache;

    /** 보스 · 소품 엔티티 종류 (보스 9 + 분신 · 모듈의 propIds) */
    static Set<String> strayTypes() {
        Set<String> c = strayCache;
        if (c != null) return c;
        Set<String> out = new HashSet<>();
        for (String k : R.strings("ALL_BOSS_ORDER")) for (String u : Mobs.bossUnitKeys(k)) out.add(Mobs.bossId(u));
        for (BossModule m : MODS.values()) out.addAll(m.propIds());
        strayCache = Set.copyOf(out);
        return strayCache;
    }

    /** 싸움이 없는 보스 · 소품 엔티티인지 */
    static boolean isStray(Entity e) {
        return strayTypes().contains(Kit.typeId(e)) && !owned.contains(e.getUUID());
    }

    /** 남아 있는 보스 · 소품 엔티티 치우기 (세계를 다시 불러오면 싸움 기록이 없으므로 — 불러온 것만) */
    public static void clearStrays() {
        MinecraftServer s = Arena.server();
        if (s == null) return;
        Set<String> ids = strayTypes();
        for (ServerLevel level : s.getAllLevels()) {
            List<Entity> list = new ArrayList<>();
            for (Entity e : level.getAllEntities()) if (e != null && ids.contains(Kit.typeId(e)) && !owned.contains(e.getUUID())) list.add(e);
            for (Entity e : list) if (!e.isRemoved()) e.discard();
        }
    }

    /** 보스 엔티티인지 (보스 9종의 마리 — 분신 · 소품은 아님) */
    public static boolean isBossEntity(Entity e) {
        if (e == null) return false;
        String id = Kit.typeId(e);
        for (String k : R.strings("ALL_BOSS_ORDER")) for (String u : Mobs.bossUnitKeys(k)) if (Mobs.bossId(u).equals(id)) return true;
        return false;
    }

    // ------------------------------------------------------------ 보스 안개

    /** v2.27.0 이 사람에게 보스 안개가 씌워져 있는지 (싸움마다 mem.fog). 초월 룬의 하늘빛이 보스 안개를 가리지 않게 (Ult.skyBlocked) */
    public static boolean hasBossFog(ServerPlayer player) {
        for (Enc enc : encs.values()) {
            BossKit.Fog f = enc.mem != null && enc.mem.fog != null ? enc.mem.fog.get(player.getUUID()) : null;
            if (f != null && f.id() != null) return true;
        }
        return false;
    }

    /** 보스 안개 걷기 → 싸움 중인 보스 모듈에 알림 (fogCleared: 필드 둘레의 사람이면 모듈이 다시 씌움) */
    public static void clearBossFog(List<ServerPlayer> list) {
        for (ServerPlayer p : list) {
            BossKit.dropFog(p);
            for (Enc enc : new ArrayList<>(encs.values())) {
                try {
                    enc.mod.fogCleared(enc, p);
                } catch (RuntimeException e) {
                    LOG.warn("[mmorpg] boss fog", e);
                }
            }
        }
    }

    // ------------------------------------------------------------ 초기화 (main.js registerBosses)

    private static boolean initialized;

    /**
     * 피해 숫자 자리 (fx.js setDamageTextAnchor): 보스 · 분신은 가슴 높이, 맞는 소품은 정한 높이, 큰 보스 (textNear) 는 때린 사람 쪽 몸 겉면.
     * 자바: 글자 엔티티는 깊이 시험을 받아 몸 안에 두면 몸에 가려짐 (베드락의 숫자는 몸 위에 그려짐) → 가슴 높이 숫자도 몸 가운데가 아니라
     * 때린 사람 쪽 몸 겉면 (반폭 + 0.5칸, 때린 사람보다 멀지 않게 — textNearAt 과 같은 가로 자리, 높이는 가슴 그대로)
     */
    public static Vec3 textAnchor(LivingEntity victim, ServerPlayer attacker) {
        Enc enc = byEntity.get(victim.getUUID());
        if (enc == null) return null;
        PropInfo pi = propInfo.get(victim.getUUID());
        if (pi != null && pi.textHeight > 0) return new Vec3(victim.getX(), victim.getY() + pi.textHeight, victim.getZ());
        Unit u = unitById.get(victim.getUUID());
        Obj def = u != null ? u.def : enc.def;
        if (def.listOrNull("box") == null) return null;
        Vec3 l = victim.position();
        if (def.truthy("textNear")) {
            Vec3 near = textNearAt(l, def, attacker);
            if (near != null) return near;
        }
        double y = l.y + def.numOr("textHeight", Math.min(bossSize(def).height() * 0.45, 3.2));
        Vec3 side = surfaceToward(l, def, attacker);
        return side != null ? new Vec3(side.x, y, side.z) : new Vec3(l.x, y, l.z);
    }

    /** 몸 가운데 l 에서 때린 사람 쪽 몸 겉면 (반폭 + 0.5칸, 때린 사람보다 0.8칸 앞까지만). 때린 사람이 없거나 몸 안이면 null */
    static Vec3 surfaceToward(Vec3 l, Obj def, Entity attacker) {
        if (attacker == null || attacker.isRemoved()) return null;
        double dx = attacker.getX() - l.x;
        double dz = attacker.getZ() - l.z;
        double L = Math.hypot(dx, dz);
        if (L < 0.5) return null;
        double r = Math.min(bossSize(def).half() + 0.5, Math.max(0, L - 0.8));
        return new Vec3(l.x + (dx / L) * r, l.y, l.z + (dz / L) * r);
    }

    /** 모듈 등록 (키 → 모듈) */
    public static void register(BossModule m) {
        MODS.put(m.key(), m);
        strayCache = null;
    }

    /** 이 키의 모듈 (없으면 null) */
    public static BossModule module(String key) {
        return MODS.get(key);
    }

    /** Mmorpg.onInitialize (ArenaGuard.init 다음): 모듈 · 피해 흡수 · 훅 (제단 누르기 · 도발 · 섬광 난무 · 하늘빛 · 피해 숫자 · 길 안내 · 퀘스트) · 틱 · 명령어 */
    public static void init() {
        if (initialized) return;
        initialized = true;
        for (BossModule m : MODULES) register(m);
        RpgBoss.absorb = Bosses::absorb;
        // v2.27.0 보스 안개 아래에서는 초월 룬 하늘빛을 씌우지 않음
        Predicate<ServerPlayer> skyPrev = Ult.skyBlocked;
        Ult.skyBlocked = p -> hasBossFog(p) || skyPrev.test(p);
        // v2.19.0 섬광 난무: 필드 밖 (울타리 너머) 에 선 사람은 그 보스 · 소품 옆으로 순간이동하지 않음
        Solas.bossFieldOk = (sp, e, at) -> {
            Enc enc = e == null ? null : byEntity.get(e.getUUID());
            if (enc == null) return true;
            return Arena.inArena(enc.a, NpcData.dimId(sp.level()), at, BOSS.num("combatMargin"));
        };
        // v2.20.0 아이기스의 방패 도발: ticks 동안 그 보스 (마리) 의 평타가 도발한 사람을 먼저 노림 (닿는 거리 안이면 — 패턴은 그대로). 소품은 아님
        Aegis.addTauntHook((sp, e, ticks) -> {
            Unit u = e == null ? null : unitById.get(e.getUUID());
            if (u == null || !u.alive) return false;
            u.taunt = new Taunt(sp.getUUID(), Tasks.now() + ticks);
            return true;
        });
        Arena.setAltarRemovedHook(Bosses::onAltarRemoved);
        Arena.onAltarUse = Bosses::onAltarTouch;
        // 보스 · 분신은 피해 숫자를 가슴 높이에서 (머리는 너무 높음)
        Function<LivingEntity, Vec3> anchorPrev = DamageNumbers.anchor;
        DamageNumbers.anchor = e -> {
            Vec3 v = textAnchor(e, feedbackAttacker);
            return v != null ? v : anchorPrev.apply(e);
        };
        // 길 안내: 싸우는 보스 필드 근처에서는 화면 칸을 숨김
        Nav.bossFights = () -> {
            List<Nav.BossFight> out = new ArrayList<>();
            for (Enc enc : encs.values()) out.add(new Nav.BossFight(enc.a.nav(), enc.center));
            return out;
        };
        // 퀘스트 (v2.6.0 메인 퀘스트의 보스 처치): 보상을 받은 사람 모두
        addBossKillHook(QuestSystem::onBossKilled);
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        // 디스크에서 불러온 보스 · 소품 중 싸움이 없는 것 (청크가 내려가 싸움이 끝난 뒤 남은 것 · 서버를 다시 켬) 은 들이지 않음
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> !(fromDisk && isStray(entity)));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !block(entity, source));
        // v2.5.0: 처음 들어온 사람에게 남아 있을 수 있는 보스 안개를 걷음 (싸움 중이면 모듈이 다시 씌움)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearBossFog(List.of(handler.player)));
        // 쓰러진 사람: 다시 때리기 전까지는 즉사 기믹이 따라가지 않게 (다시 살아난 곳이 멀어도)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer sp)) return;
            for (Enc enc : encs.values()) {
                Part part = enc.parts.get(sp.getUUID());
                if (part != null) part.diedAt = Tasks.now();
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> clearStrays());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            encs.clear();
            byEntity.clear();
            unitById.clear();
            owned.clear();
            farWarned.clear();
            propInfo.clear();
            BossKit.clear();
        });
        BossCommands.init();
    }

    // ------------------------------------------------------------ 모듈이 쓰는 엔진 함수 (enc.api)

    /** 애드온 enc.api = { cast, hold, anim, setFast, addProp, dropProp, retireProp, own, disown, other, ratio, face, turnLeft, seat, pose } */
    public static final class Api {
        Api() {}

        public void cast(Unit u, String id, int length, Step... steps) {
            Bosses.cast(u, id, length, steps);
        }

        public void cast(Unit u, String id, int length, List<Step> steps) {
            Bosses.cast(u, id, length, steps);
        }

        public void hold(Unit u, boolean on) {
            Bosses.hold(u, on);
        }

        public void anim(Unit u, String name) {
            Bosses.anim(u, name);
        }

        public void anim(Unit u, String name, Entity target) {
            Bosses.anim(u, name, target);
        }

        public void anim(Unit u, String name, Entity target, String controller, String stop) {
            Bosses.anim(u, name, target, controller, stop);
        }

        public void setFast(Unit u) {
            Bosses.setFast(u);
        }

        public void addProp(Enc enc, Entity e) {
            Bosses.addProp(enc, e);
        }

        public void addProp(Enc enc, Entity e, double hp, double textHeight) {
            Bosses.addProp(enc, e, hp, textHeight);
        }

        public void dropProp(Enc enc, Entity e) {
            Bosses.dropProp(enc, e);
        }

        public void retireProp(Entity e) {
            Bosses.retireProp(e);
        }

        public void own(Entity e) {
            Bosses.own(e);
        }

        public void disown(Entity e) {
            Bosses.disown(e);
        }

        public Unit other(Unit u) {
            return Bosses.other(u);
        }

        public double ratio(Unit u) {
            return Bosses.ratio(u);
        }

        public void face(Unit u, Vec3 dir) {
            Bosses.face(u, dir);
        }

        public double turnLeft(Unit u) {
            return Bosses.turnLeft(u);
        }

        public void seat(Unit u, Vec3 at, Double ang) {
            Bosses.seat(u, at, ang);
        }

        public void pose(Unit u, Vec3 at, double ang) {
            Bosses.pose(u, at, ang);
        }
    }

    public static final Api API = new Api();
}
