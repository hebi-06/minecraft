package mmorpg.fabric.mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import mmorpg.core.Mobs;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.skill.Aegis;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 일반 몬스터 (monsters.js v1.7.0 — 허수아비 → 굴론 → 스켈야스크림슬리 → 닉스 → 드라우그 → 트롤, 수치는 config.js MONSTERS · MONSTER).
 * <ul>
 * <li>후공: 맞기 전에는 둔 자리 둘레를 돌아다니기만 함. 맞으면 때린 플레이어 (마지막으로 때린 사람) 를 쫓아가 공격.
 * 이동 · 추격 · 마주 보기는 엔티티 (BedrockGoals — hurt_by_target · melee_attack), 바닐라 근접 피해는 BedrockMob 이 막음 (doHurtTarget)</li>
 * <li>공격 (스크립트): reach 안이고 서로 보이면 공격 동작 (playAnimation) + 붉은 고리 → windup 틱 뒤 처음 노린 플레이어가 아직 reach + tolerance 안 ·
 * 노린 쪽 ±arc 도 · 보이면 맞음 (할퀴기 참격 · 불꽃 · 소리), 아니면 헛손질. 터치 사거리 (6.5칸) 에 맞춰 reach 밖이면 달려들기 (lunge),
 * 높이 차이로 닿지 못하는 곳에서 방금 때렸으면 원거리 반격 (발밑 붉은 고리 → 기운이 날아와 침)</li>
 * <li>룬 · 무기 효과: 구속 VII 이상 (속박 · 기절 · 빙결 · 석화) 이면 달려들기 · 원거리 반격 없음, 나약함까지 있으면 공격 못 함 (하던 동작도 끊김),
 * 나약함 한 단계마다 피해 -20%, 룬의 짧은 은신 (투명 5초 이하) 인 사람에게는 달려들기 · 원거리 반격 없음</li>
 * <li>대상이 크리에이티브 · 관전 · 쓰러지는 중이면 잠깐 멈춤 (pauseLimit 이 넘으면 끝), 죽음 · 나감 · 다른 차원 · leash 밖이면 끝 → 둔 자리로</li>
 * <li>특성: 스켈야스크림슬리 껍질 (플레이어 피해 -20%), 닉스 · 드라우그 맞으면 구속, 트롤 재생 · 내려치기 충격파</li>
 * <li>이름표 "Lv.8 굴론" (노란 레벨 + 흰 이름), 다치면 아래 줄 체력 막대 20칸 (hp.js 의 이름표 새로 고침 — 자바는 첫 줄이 이름이라 사망 메시지에는 늘 첫 줄만).
 * 피해 숫자는 몸통에서 떠올라 이름표 아래에서 끝남 (DamageNumbers.anchor)</li>
 * <li>회복: 싸움이 끝나고 regenDelay 틱 동안 맞지 않으면 1초마다 최대 체력 10% (다친 채 다시 불러와도). 트롤은 싸우는 중에도 재생</li>
 * <li>죽으면 잡은 사람에게 경험치 (구슬 대신) · 전리품 (Loot.lootOnDeath), 도발 (아이기스의 방패 — tauntMonster)</li>
 * </ul>
 * 자바에서 달라진 점: 몬스터 피해는 바닐라 피해 (mobAttack — 갑옷 · 방패 · RPG 체력은 RpgHealth 가, 난이도 배율 · 밀침은 바닐라 그대로 — 애드온의 applyDamage 도
 * 때린 몬스터 쪽에서 밀침), 무적 시간에 막혀도 맞힘 연출은 그대로 (구속만 없음). 이름표는 몬스터에 태운 글자 표시 엔티티. 싸움이 끝나면 엔티티의 대상도 놓아 줌
 * (자바의 대상 고르기가 다른 차원의 사람을 계속 쫓지 않게). 사냥터는 {@link HuntZones}, 명령어는 {@link MonsterCommands}.
 */
public final class Monsters {
    private Monsters() {}

    private static final Rules R = Rules.get();
    static final Obj MONSTERS = R.obj("MONSTERS");
    static final Obj MONSTER = R.obj("MONSTER");
    private static final double PLAYER_HALF = 0.3;
    /** 머리 위 이름표 (글자 표시 엔티티) 의 태그 */
    public static final String LABEL_TAG = "mmorpg_monster_label";
    /** 사냥터 몬스터 태그 머리 (huntzone.js tagOf — rpg_zone_&lt;번호&gt;) */
    public static final String ZONE_TAG = "rpg_zone_";
    private static final double[] RED = {1.0, 0.28, 0.2};

    // MONSTER 수치
    static final double LUNGE_RANGE = MONSTER.num("lungeRange");
    static final double LUNGE_HEIGHT = MONSTER.num("lungeHeight");
    static final double LUNGE_PER_BLOCK = MONSTER.num("lungePerBlock");
    static final double TOLERANCE = MONSTER.num("tolerance");
    static final double LEASH = MONSTER.num("leash");
    static final int REGEN_DELAY = (int) MONSTER.num("regenDelay");
    static final double REGEN_PER_SEC = MONSTER.num("regenPerSec");
    static final int HP_BAR = (int) MONSTER.num("hpBar");
    static final double ARC = MONSTER.num("arc");
    static final double COUNTER_RADIUS = MONSTER.num("counterRadius");
    static final int COUNTER_EXTRA = (int) MONSTER.num("counterExtra");
    static final int ROOT_AMP = (int) MONSTER.num("rootAmp");
    static final double WEAK_PER_LEVEL = MONSTER.num("weakPerLevel");
    static final int PAUSE_LIMIT = (int) MONSTER.num("pauseLimit");
    static final int COUNTER_WINDOW = (int) MONSTER.num("counterWindow");
    static final int HIDE_MAX = (int) MONSTER.num("hideMax");

    // ------------------------------------------------------------ 설정 (config.js MONSTERS)

    /** 맞으면 붙는 효과 (닉스 · 드라우그) */
    public record OnHit(String effect, double seconds, int amplifier) {}

    /** 싸우는 중에도 재생 (트롤): after 틱 동안 안 맞으면 1초마다 perSec */
    public record Regen(int after, double perSec) {}

    /** 내려치기 충격파 (트롤 · 9단계 서리 거인): 둘레 radius 칸의 다른 플레이어도 피해 × mult (fx "ice" = 얼음 고리 · 고드름 · 서리 금) */
    public record Shock(double radius, double mult, String fx) {}

    /** 9단계 니세: 공격한 뒤 뒤로 깡충 (가로 back · 위로 up) · 다음 공격 · 달려들기는 pause 틱 뒤 */
    public record Hop(double back, double up, int pause) {}

    /**
     * 9단계 광폭 멧돼지 돌진 (달려들기 대신): reach 밖 range 칸 안이면 prep 틱 동안 앞발로 땅을 긁고 (멈춤 · 붉은 줄 예고) → 그 쪽으로 speed 칸/틱 으로
     * dash 틱 동안 달림. 몸 앞 width 칸 안에 플레이어가 들어오면 피해 × mult · 밀쳐냄 (가로 knock · 위 lift)
     */
    public record Charge(double range, int prep, double speed, int dash, double width, double knock, double lift, double mult) {}

    /** 9단계 웜 유충 매복: 싸우지 않으면 rest 틱 뒤 모래 속으로, 플레이어가 detect 칸 안에 오거나 맞으면 튀어나와 먼저 덤빔 (첫 공격은 emerge 틱 뒤) */
    public record Burrow(double detect, int rest, int emerge) {}

    /** 9단계 서리 늑대 무리 사냥: 처음 맞으면 울부짖고 둘레 radius 칸의 같은 늑대도 때린 사람에게 덤빔 (0.32.0 — 동작 anim · 소리 sound, 공허 도약충은 울음 screech) */
    public record Pack(double radius, String anim, String sound) {}

    /** 몬스터 설정 (monsters.js DEFS 의 값 — 키 포함, 9단계 특성 hop · charge · burrow · pack 은 뒤에) */
    public record Kind(String key, String id, String name, int level, double hp, double attack, int every, int windup, double reach, int lungeEvery,
            double scale, double boxW, double boxH, double[] color, double shell, OnHit onHit, Regen regen, Shock shock, String windupSound,
            String hitSound, int xp, int xpUntil, double[] emerald, Hop hop, Charge charge, Burrow burrow, Pack pack, VoidMonsters.Traits traits) {
        /** 크기 배율을 곱한 가로 반폭 (monsterSize.half) */
        public double half() {
            return boxW * scale / 2;
        }

        /** 크기 배율을 곱한 키 (monsterSize.height) */
        public double height() {
            return boxH * scale;
        }
    }

    private static Map<String, Kind> kinds;

    /** 몬스터 설정 모두 (엔티티 ID rpg:troll → 설정, 몬스터 순서) */
    public static synchronized Map<String, Kind> kinds() {
        if (kinds == null) {
            Map<String, Kind> m = new LinkedHashMap<>();
            for (String key : R.strings("MONSTER_ORDER")) {
                Obj o = MONSTERS.objOrNull(key);
                if (o == null) continue;
                Obj hit = o.objOrNull("onHit");
                Obj regen = o.objOrNull("regen");
                Obj shock = o.objOrNull("shock");
                Obj hop = o.objOrNull("hop");
                Obj charge = o.objOrNull("charge");
                Obj burrow = o.objOrNull("burrow");
                Obj pack = o.objOrNull("pack");
                List<Object> box = o.list("box");
                List<Object> em = o.listOrNull("emerald");
                double[] emerald = em == null || em.size() < 3 ? null
                        : new double[] {((Number) em.get(0)).doubleValue(), ((Number) em.get(1)).doubleValue(), ((Number) em.get(2)).doubleValue()};
                m.put(Mobs.monsterId(key), new Kind(key, Mobs.monsterId(key), o.str("name"), (int) o.num("level"), o.num("hp"), o.num("attack"),
                        (int) o.num("every"), (int) o.num("windup"), o.num("reach"), (int) o.num("lungeEvery"), o.num("scale"),
                        ((Number) box.get(0)).doubleValue(), ((Number) box.get(1)).doubleValue(), Kit.rgb(o.list("color")), o.numOr("shell", 0),
                        hit == null ? null : new OnHit(hit.str("effect"), hit.num("seconds"), (int) hit.num("amplifier")),
                        regen == null ? null : new Regen((int) regen.num("after"), regen.num("perSec")),
                        shock == null ? null : new Shock(shock.num("radius"), shock.num("mult"), shock.strOr("fx", "")),
                        o.obj("sounds").str("windup"), o.obj("sounds").str("hit"), (int) o.num("xp"), (int) o.num("xpUntil"), emerald,
                        hop == null ? null : new Hop(hop.num("back"), hop.num("up"), (int) hop.num("pause")),
                        charge == null ? null : new Charge(charge.num("range"), (int) charge.num("prep"), charge.num("speed"), (int) charge.num("dash"),
                                charge.num("width"), charge.num("knock"), charge.num("lift"), charge.numOr("mult", 1)),
                        burrow == null ? null : new Burrow(burrow.num("detect"), (int) burrow.num("rest"), (int) burrow.num("emerge")),
                        pack == null ? null : new Pack(pack.num("radius"), pack.strOr("anim", "howl"), pack.strOr("sound", "item.goat_horn.sound.0")),
                        VoidMonsters.parse(o)));
            }
            kinds = Collections.unmodifiableMap(m);
        }
        return kinds;
    }

    /** 키 (troll) 또는 ID (rpg:troll) 의 설정 (없으면 null) */
    public static Kind kind(String keyOrId) {
        if (keyOrId == null) return null;
        return kinds().get(keyOrId.indexOf(':') < 0 ? Mobs.monsterId(keyOrId) : keyOrId);
    }

    /** 몬스터 설정 (몬스터가 아니면 null — monsterDef) */
    public static Kind kindOf(Entity e) {
        return e instanceof RpgMonster m ? kinds().get(m.def.id()) : null;
    }

    // ------------------------------------------------------------ 몸 · 거리 (monsterSize · gapTo · heightOk · canSee · touchReach · inArc)

    /** 몬스터 몸 가장자리 ~ 플레이어 몸 가장자리 (가로 칸), 높이 차 (플레이어 발 - 몬스터 발), 가운데끼리 거리 */
    public record Gap(double gap, double dy, double dist) {}

    public static Gap gapTo(Entity m, Kind k, Entity p) {
        double d = Math.hypot(p.getX() - m.getX(), p.getZ() - m.getZ());
        return new Gap(Math.max(0, d - k.half() - PLAYER_HALF), p.getY() - m.getY(), d);
    }

    /** 높이가 닿는지: 플레이어 발이 몬스터 발 아래 1.5칸 ~ 몬스터 키 (+extra) */
    static boolean heightOk(Kind k, double dy, double extra) {
        return dy >= -1.5 - extra && dy <= k.height() + extra;
    }

    /** 피해 숫자 기준 높이 (발에서): 몸통 (키 × 0.62) 이되, 떠오른 숫자가 이름표에 닿지 않게 키 - 1 을 넘지 않음 (최소 0.45) */
    public static double damageTextHeight(Kind k) {
        double h = k.height();
        return Math.min(h * 0.62, Math.max(0.45, h - 1.0));
    }

    /**
     * 플레이어 눈에서 몬스터 몸이 보이는지 (벽 너머로는 서로 때리지 못함): 몸 위 가운데 · 가운데 · 위 네 모서리 중 하나라도 보이면
     * (기둥 밑에 붙은 몬스터처럼 가운데가 가려도 모서리가 보이면 터치로 때릴 수 있으니 보이는 것으로)
     */
    static boolean canSee(Entity m, Kind k, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        double x = m.getX();
        double y = m.getY();
        double z = m.getZ();
        double top = y + k.height() - 0.1;
        double c = Math.max(0, k.half() - 0.05);
        Vec3[] pts = {new Vec3(x, top, z), new Vec3(x, y + k.height() * 0.5, z), new Vec3(x + c, top, z + c), new Vec3(x - c, top, z + c),
            new Vec3(x + c, top, z - c), new Vec3(x - c, top, z - c)};
        for (Vec3 to : pts) if (RpgCombat.lineOfSight(p, eye, to)) return true;
        return false;
    }

    /** 플레이어가 터치로 때릴 수 있는 거리: 눈 → 몬스터 충돌 상자의 가장 가까운 점이 lungeRange 안 */
    static boolean touchReach(Entity m, Kind k, ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        double dh = Math.max(0, Math.hypot(eye.x - m.getX(), eye.z - m.getZ()) - k.half());
        double dv = eye.y < m.getY() ? m.getY() - eye.y : eye.y > m.getY() + k.height() ? eye.y - (m.getY() + k.height()) : 0;
        return Math.hypot(dh, dv) <= LUNGE_RANGE;
    }

    /** 공격 동작 끝에 노린 쪽 (시작할 때 몬스터 → 플레이어) 에서 arc 도 안인지. 몸에 붙어 있으면 늘 안 */
    static boolean inArc(Entity m, Kind k, Vec3 aim, Entity p) {
        double dx = p.getX() - m.getX();
        double dz = p.getZ() - m.getZ();
        double d = Math.hypot(dx, dz);
        if (aim == null || d <= k.half() + 0.1) return true;
        return (dx * aim.x + dz * aim.z) / d >= Math.cos(Math.toRadians(ARC));
    }

    // ------------------------------------------------------------ 대상 상태 · 효과

    enum State {
        OK,
        PAUSE,
        GONE
    }

    /** 싸우는 대상의 상태: OK = 공격 가능, PAUSE = 잠깐 멈춤 (크리에이티브 · 관전 · 쓰러지는 중), GONE = 싸움 끝 (없어짐 · 다른 차원) */
    static State targetState(ServerPlayer p, Entity m) {
        if (p == null || p.isRemoved() || m == null || m.isRemoved()) return State.GONE;
        if (p.level() != m.level()) return State.GONE;
        GameType gm = p.gameMode.getGameModeForPlayer(); // 게임 모드 그대로 (RpgHealth.isRpgMode 와 같게)
        if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) return State.PAUSE;
        return p.isDeadOrDying() ? State.PAUSE : State.OK;
    }

    /** 효과 세기 (없으면 -1) */
    static int effectAmp(LivingEntity e, net.minecraft.core.Holder<MobEffect> h) {
        MobEffectInstance x = e.getEffect(h);
        return x == null ? -1 : x.getAmplifier();
    }

    /** 움직이지 못함: 구속 VII 이상 (속박 화살 · 기절 · 빙결 · 석화) */
    public static boolean rooted(LivingEntity m) {
        return effectAmp(m, MobEffects.SLOWNESS) >= ROOT_AMP;
    }

    /** 공격하지 못함: 움직이지 못하고 나약함까지 (기절 · 빙결 · 석화) */
    public static boolean stunned(LivingEntity m) {
        return rooted(m) && effectAmp(m, MobEffects.WEAKNESS) >= 0;
    }

    /** 나약함: 한 단계마다 피해 -weakPerLevel */
    static double weakMult(LivingEntity m) {
        int w = effectAmp(m, MobEffects.WEAKNESS);
        return w < 0 ? 1 : Math.max(0, 1 - WEAK_PER_LEVEL * (w + 1));
    }

    /** 룬의 짧은 은신 (연막 · 월광 암살, 남은 시간 hideMax 틱 이하): 멀리서는 못 찾음. 투명화 물약처럼 긴 투명은 셈하지 않음 */
    static boolean hidden(ServerPlayer p) {
        MobEffectInstance e = p.getEffect(MobEffects.INVISIBILITY);
        return e != null && !e.isInfiniteDuration() && e.getDuration() >= 0 && e.getDuration() <= HIDE_MAX;
    }

    // ------------------------------------------------------------ 싸움

    /** 때린 사람 하나: 플레이어 · 마지막으로 때린 틱 */
    record Attacker(ServerPlayer p, long t) {}

    /** 도발 (v2.20.0): 그 플레이어 · 끝나는 틱 */
    record Taunt(UUID id, long until) {}

    /** 공격 동작 하나: 시작 틱, 끝 (판정) 틱, 종류 (melee · lunge · counter), 처음 노린 플레이어, 노린 쪽 (가로 단위 벡터), 원거리 반격이 떨어질 자리 */
    record Windup(long start, long at, String kind, ServerPlayer target, Vec3 aim, Vec3 spot) {}

    /** 싸우는 몬스터 하나 (monsters.js fights 의 값) */
    public static final class Fight {
        final RpgMonster m;
        final Kind k;
        ServerPlayer player;
        final Map<UUID, Attacker> attackers = new LinkedHashMap<>();
        long nextAttack;
        long nextLunge;
        Long paused;
        Windup windup;
        Taunt taunt;
        int hits;
        int misses;
        String lastKind;
        long lastResolved;
        /** 9단계: 멧돼지 돌진 (없으면 null) · 니세가 깡충 뛴 수 · 서리 늑대가 울부짖어 부른 수 */
        ChargeRun charge;
        int hops;
        int packCalled = -1;
        /** 마지막 돌진에 맞은 사람을 밀어낸 속도 (없으면 null — 시험 · 사진첩) */
        Vec3 lastPush;
        /** 0.29.0 미끼 (암살자 잔영): 이 틱까지 그 사람 대신 미끼 자리로 감 (없으면 null) */
        Vec3 decoy;
        long decoyUntil;
        /** 0.32.0 공허 특성: 진행 중인 것 (VoidMonsters.Run — 없으면 null) · 다음에 쓸 수 있는 틱 */
        Object voidRun;
        long nextTrait;

        Fight(RpgMonster m, Kind k, ServerPlayer p, long now) {
            this.m = m;
            this.k = k;
            this.player = p;
            this.attackers.put(p.getUUID(), new Attacker(p, now));
            this.nextAttack = now;
            this.nextLunge = now + 6;
            this.nextTrait = now + 16;
        }

        /** 지금 노리는 플레이어 */
        public ServerPlayer player() {
            return player;
        }

        /** 하던 공격 동작의 종류 (melee · lunge · counter — 없으면 null) */
        public String windupKind() {
            return windup == null ? null : windup.kind();
        }

        /** 하던 공격 동작이 판정되는 틱 (없으면 -1) */
        public long windupAt() {
            return windup == null ? -1 : windup.at();
        }

        /** 원거리 반격이 떨어질 자리 (없으면 null) */
        public Vec3 counterSpot() {
            return windup == null ? null : windup.spot();
        }

        public long nextAttack() {
            return nextAttack;
        }

        public long nextLunge() {
            return nextLunge;
        }

        /** 잠깐 멈춰 있는지 (크리에이티브 · 관전 · 쓰러지는 중) */
        public boolean paused() {
            return paused != null;
        }

        /** 도발한 사람 (도발 중이 아니면 null) */
        public UUID tauntId() {
            return taunt != null && taunt.until() > Tasks.now() ? taunt.id() : null;
        }

        /** 때린 사람들 */
        public Set<UUID> attackers() {
            return Collections.unmodifiableSet(attackers.keySet());
        }

        /** 맞힌 수 · 빗나간 수 · 마지막으로 판정한 동작 (시험 · 사진첩) */
        public int hits() {
            return hits;
        }

        public int misses() {
            return misses;
        }

        public String lastKind() {
            return lastKind;
        }

        /** 멧돼지 돌진 단계: "prep" (앞발 긁기) · "dash" (달림) · null (돌진 중이 아님) */
        public String chargePhase() {
            return charge == null ? null : charge.dashing ? "dash" : "prep";
        }

        /** 니세가 깡충 뛴 수 (시험) */
        public int hops() {
            return hops;
        }

        /** 서리 늑대가 처음 맞았을 때 부른 늑대 수 (-1 = 부르지 않음 — 시험) */
        public int packCalled() {
            return packCalled;
        }

        /** 마지막 돌진에 맞은 사람을 밀어낸 속도 (없으면 null) */
        public Vec3 lastPush() {
            return lastPush;
        }

        /** 0.29.0 미끼 자리 (미끼를 쫓는 중이 아니면 null — 시험) */
        public Vec3 decoy() {
            return decoy != null && decoyUntil > Tasks.now() ? decoy : null;
        }
    }

    /** 멧돼지 돌진 하나: 준비 끝 틱 · 달리기 끝 틱 · 방향 (가로 단위) · 노린 사람, 달리는 중인지 · 맞혔는지 · 지난 틱 자리 · 막힌 틱 수 */
    static final class ChargeRun {
        final long start;
        final long prepUntil;
        final long dashUntil;
        final Vec3 dir;
        final ServerPlayer target;
        boolean dashing;
        boolean hit;
        Vec3 last;
        int stuck;

        ChargeRun(long start, long prepUntil, long dashUntil, Vec3 dir, ServerPlayer target) {
            this.start = start;
            this.prepUntil = prepUntil;
            this.dashUntil = dashUntil;
            this.dir = dir;
            this.target = target;
        }
    }

    /** 싸우는 몬스터: 몬스터 UUID → 싸움 */
    private static final Map<UUID, Fight> FIGHTS = new LinkedHashMap<>();

    /** 다친 몬스터 (회복 · 재생용): 몬스터, 마지막으로 맞은 틱 */
    private record Hurt(RpgMonster m, Kind k, long t) {}

    private static final Map<UUID, Hurt> HURT = new LinkedHashMap<>();

    /** 지금 싸우는 대상 (시험 · 다른 모듈용 — 없으면 null) */
    public static Fight fightOf(Entity m) {
        return m == null ? null : FIGHTS.get(m.getUUID());
    }

    /** 싸우는 몬스터 수 (시험용) */
    public static int fightCount() {
        return FIGHTS.size();
    }

    /**
     * 도발 (v2.20.0 아이기스의 방패): ticks 동안 이 몬스터가 그 플레이어를 노림 — 다음 공격 대상이 그 사람이고, 그동안 다른 사람이 때려도 바뀌지 않음
     * (싸우지 않던 몬스터도 싸움을 시작함). 걷는 쪽은 엔티티 (hurt_by_target) 가 정함 — 도발한 스킬의 피해가 마지막 피해면 같은 사람.
     * 몬스터가 아니거나 · 쓰러지는 중이거나 · 그 사람을 노릴 수 없으면 (크리에이티브 · 다른 차원) false
     */
    public static boolean tauntMonster(LivingEntity e, ServerPlayer p, int ticks) {
        Kind k = kindOf(e);
        if (k == null || Kit.hasNoHealth(e) || targetState(p, e) != State.OK) return false;
        RpgMonster m = (RpgMonster) e;
        long now = Tasks.now();
        Fight f = FIGHTS.get(m.getUUID());
        if (f == null) {
            f = new Fight(m, k, p, now);
            FIGHTS.put(m.getUUID(), f);
        }
        f.player = p;
        f.attackers.put(p.getUUID(), new Attacker(p, now));
        f.taunt = new Taunt(p.getUUID(), now + ticks);
        return true;
    }

    /**
     * 0.29.0 미끼 (암살자 잔영): at 둘레 radius 칸 안에서 그 사람과 싸우던 몬스터가 ticks 동안 그 사람 대신 미끼 자리로 걸어감 (하던 공격 동작은 멈춤 — 끝나면 다시 그 사람).
     * 바닐라 몹 (좀비 등) 은 그 사람을 놓고 미끼 자리로 감. 미끼를 쫓는 몬스터 · 몹 수
     */
    public static int decoy(ServerPlayer p, Vec3 at, double radius, int ticks) {
        long now = Tasks.now();
        int n = 0;
        for (Fight f : FIGHTS.values()) {
            if (f.m.isRemoved() || Kit.hasNoHealth(f.m) || (f.player != p && !f.attackers.containsKey(p.getUUID()))) continue;
            if (f.m.position().distanceTo(at) > radius) continue;
            f.decoy = at;
            f.decoyUntil = now + ticks;
            f.windup = null;
            if (f.charge != null) endCharge(f, now, false);
            if (f.voidRun != null && VoidMonsters.interruptible(f)) VoidMonsters.cancel(f, now);
            f.m.setTarget(null);
            f.m.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
            n++;
        }
        ServerLevel level = p.level();
        for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(at, at).inflate(radius),
                x -> x.getTarget() == p && !(x instanceof RpgMonster))) {
            mob.setTarget(null);
            mob.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
            n++;
        }
        return n;
    }

    /** 미끼가 사라짐 (잔영이 터짐): 그 사람의 미끼를 쫓던 몬스터가 다시 그 사람을 노림 */
    public static void endDecoy(ServerPlayer p) {
        for (Fight f : FIGHTS.values()) {
            if (f.decoy == null || (f.player != p && !f.attackers.containsKey(p.getUUID()))) continue;
            f.decoy = null;
            f.decoyUntil = 0;
            if (!f.m.isRemoved() && f.player != null && !f.player.isRemoved()) f.m.setTarget(f.player);
        }
    }

    // ------------------------------------------------------------ 연출

    private static Vec3 eyeish(Entity m, Kind k) {
        return V.up(m.position(), k.height() * 0.62);
    }

    /** 몬스터 소리 (베드락 이름 — 대응표에 없는 몬스터 소리는 자바 소리로 바로) */
    private static final Map<String, SoundEvent> SOUNDS = Map.of(
            "mob.creaking.ambient", SoundEvents.CREAKING_AMBIENT,
            "mob.creaking.attack", SoundEvents.CREAKING_ATTACK,
            "mob.hoglin.angry", SoundEvents.HOGLIN_ANGRY,
            "mob.hoglin.attack", SoundEvents.HOGLIN_ATTACK,
            "mob.ravager.ambient", SoundEvents.RAVAGER_AMBIENT,
            "mob.ravager.bite", SoundEvents.RAVAGER_ATTACK,
            "mob.drowned.say_water", SoundEvents.DROWNED_AMBIENT_WATER,
            "mob.stray.ambient", SoundEvents.STRAY_AMBIENT,
            "mob.warden.attack", SoundEvents.WARDEN_ATTACK_IMPACT);

    /** 몬스터 소리 이름 → 자바 소리 (대응표 → SkillFx 대응표 → 자바 이름 그대로, 없으면 null — 시험이 새 몬스터 소리를 모두 확인) */
    public static SoundEvent soundOf(String bedrock) {
        SoundEvent s = SOUNDS.get(bedrock);
        return s != null ? s : SkillFx.sound(bedrock);
    }

    /** 그 자리에서 소리 (fx.js play — 몬스터 소리는 적대 몹 소리 갈래로) */
    static void play(ServerLevel level, String bedrock, Vec3 at, double pitch, double volume) {
        SoundEvent s = soundOf(bedrock);
        if (s != null) level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, (float) volume, (float) pitch);
    }

    /** 공격 동작 시작: 몬스터 앞 바닥에 사거리만큼 퍼지는 붉은 고리 (공격 예고) */
    private static void telegraph(ServerLevel level, Entity m, Kind k, Vec3 toward) {
        Vec3 d = V.flat(V.sub(toward, m.position()));
        double reachAll = k.half() + k.reach();
        Vec3 c = V.add(m.position(), d, reachAll * 0.55);
        SkillFx.shock(level, c, RED, (reachAll * 0.55) / 2.1, k.windup() / 20.0);
    }

    /** 맞힘: 플레이어 가슴 앞 (몬스터 쪽) 에 몬스터 색 할퀴기 참격 + 불꽃 (1인칭에서도 화면 아래쪽에 보임) */
    static void impactFx(ServerLevel level, Entity m, Kind k, ServerPlayer p) {
        Vec3 chest = V.up(p.position(), 1.2);
        Vec3 toM = V.flat(V.sub(m.position(), p.position()));
        Vec3 at = V.add(chest, toM, 0.7);
        Vec3 v = V.unit(V.sub(chest, eyeish(m, k)));
        boolean heavy = k.key().equals("troll") || k.key().equals("frost_giant") || k.key().equals("grendel") || k.key().equals("void_borer")
                || k.key().equals("void_behemoth") || k.key().equals("void_stalker");
        SkillFx.arc(level, at, v, Opt.of("shape", heavy ? "heavy" : "thin", "flip", Kit.rand() < 0.5, "size", heavy ? 1.45 : 1.0, "life", 0.2, "speed", 1.0,
                "spin", Kit.rand() * 60 - 30, "color", k.color()));
        SkillFx.sparks(level, at, k.color(), 5, 3);
    }

    /** 헛손질: 몬스터 앞 허공을 가르는 흐린 참격 */
    private static void missFx(ServerLevel level, Entity m, Kind k, Vec3 toward) {
        Vec3 d = V.flat(V.sub(toward, m.position()));
        Vec3 at = V.up(V.add(m.position(), d, k.half() + k.reach() * 0.6), k.height() * 0.5);
        SkillFx.arc(level, at, d, Opt.of("shape", "thin", "flip", false, "size", 1.1, "life", 0.18, "speed", 1.0, "spin", -20.0, "color", k.color(),
                "alpha", 0.5, "core", false));
        play(level, "item.spear.attack_miss", at, 0.9, 0.5);
    }

    private static void attackAnim(RpgMonster m, Kind k) {
        m.playAnimation("animation.rpg.monster." + k.key() + ".attack");
    }

    // ------------------------------------------------------------ 공격

    /** 공격 동작 시작 (근접 · 달려들기): windup 틱 뒤 처음 노린 플레이어가 맞았는지 봄. 다음 공격은 시작부터 every 틱 뒤 */
    private static void startAttack(Fight f, long now, String kind) {
        RpgMonster m = f.m;
        Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        attackAnim(m, k);
        play(level, k.windupSound(), m.position(), 0.9 + Kit.rand() * 0.2, 0.8);
        telegraph(level, m, k, f.player.position());
        f.nextAttack = now + k.every();
        f.windup = new Windup(now, now + k.windup(), kind, f.player, V.flat(V.sub(f.player.position(), m.position())), null);
    }

    /** 달려들기: 플레이어 쪽으로 뛰어듦 (남은 거리 - reach 절반만큼) + 공격 동작 */
    private static void lunge(Fight f, Gap g, long now) {
        RpgMonster m = f.m;
        Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        Vec3 dir = V.flat(V.sub(f.player.position(), m.position()));
        double travel = Math.max(0, g.gap() - k.reach() * 0.5);
        double h = Math.max(0.3, Math.min(1.0, travel * LUNGE_PER_BLOCK));
        double vy = g.dy() > 0.6 ? 0.5 : 0.32;
        m.setDeltaMovement(dir.x * h, vy, dir.z * h); // clearVelocity + applyImpulse
        m.needsSync = true;
        SkillFx.burst(level, V.up(m.position(), 0.3), new double[] {0.55, 0.5, 0.45}, 0.9 + k.half(), 0.3);
        SkillFx.beam(level, V.up(m.position(), k.height() * 0.45), V.up(V.add(m.position(), dir, Math.min(travel, 4)), k.height() * 0.45), k.color(),
                Opt.of("half", 0.3, "thick", 0.1, "life", 0.25, "alpha", 0.6));
        play(level, "mob.breeze.jump", m.position(), 0.8, 0.8);
        f.nextLunge = now + k.lungeEvery();
        startAttack(f, now, "lunge");
    }

    /**
     * 원거리 반격: 높이 차이로 닿지도 달려들지도 못하는데 플레이어는 때릴 수 있는 곳 → 플레이어 발밑에 붉은 고리,
     * windup + counterExtra 틱 뒤 몬스터 색 기운이 날아와 그 자리를 침 (달려들기와 같은 lungeEvery 마다)
     */
    private static void counter(Fight f, long now) {
        RpgMonster m = f.m;
        Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        Vec3 spot = f.player.position();
        int ticks = k.windup() + COUNTER_EXTRA;
        attackAnim(m, k);
        play(level, k.windupSound(), m.position(), 0.8, 1);
        SkillFx.shock(level, spot, RED, COUNTER_RADIUS / 2.1, ticks / 20.0);
        // 날아가는 기운: 마지막 0.2초 동안 몬스터 → 그 자리
        SkillFx.beam(level, eyeish(m, k), V.up(spot, 0.9), k.color(),
                Opt.of("half", 0.3, "thick", 0.12, "life", 0.15, "delay", Math.max(0, ticks / 20.0 - 0.2), "sweep", 0.2, "alpha", 0.85));
        f.nextLunge = now + k.lungeEvery();
        f.nextAttack = now + Math.max(k.every(), ticks + 4);
        f.windup = new Windup(now, now + ticks, "counter", f.player, null, spot);
    }

    /**
     * 몬스터 피해 한 번 (스크립트 공격 — 바닐라 근접은 BedrockMob 이 막음). 들어갔으면 true
     * (무적 시간 등으로 안 들어가면 false: 맞힘 연출은 그대로, 맞으면 붙는 효과만 없음). amount = 바닐라 단위 (갑옷 · 방패 · 체력 스탯은 RpgHealth)
     */
    public static boolean monsterHit(RpgMonster m, Kind k, ServerPlayer target, double amount) {
        if (!(amount > 0) || !(target.level() instanceof ServerLevel level) || target.isRemoved()) return false;
        boolean ok = target.hurtServer(level, m.damageSources().mobAttack(m), (float) amount);
        if (!ok) return false;
        if (k.onHit() != null) {
            net.minecraft.core.Holder<MobEffect> h = mmorpg.fabric.rune.RuneKit.effectOf(k.onHit().effect());
            if (h != null && !target.isRemoved()) {
                target.addEffect(new MobEffectInstance(h, (int) Math.round(k.onHit().seconds() * 20), k.onHit().amplifier(), false, true));
                onHitFx(level, k, target);
            }
        }
        return true;
    }

    /** 맞으면 붙는 효과의 연출 (9단계): 독 = 초록 방울, 시듦 = 검은 연기, 냉기 (서리 · 드라우그) = 눈송이 · 하늘빛, 붙잡기 (게) = 몬스터 색 불꽃 */
    private static void onHitFx(ServerLevel level, Kind k, ServerPlayer p) {
        Vec3 chest = V.up(p.position(), 1.0);
        switch (k.onHit().effect()) {
            case "poison" -> {
                SkillFx.sparks(level, chest, POISON, 8, 2.5);
                around(level, p, ParticleTypes.ITEM_SLIME, 6, 0.02);
            }
            case "wither" -> {
                SkillFx.sparks(level, chest, WITHER, 8, 2);
                around(level, p, ParticleTypes.LARGE_SMOKE, 8, 0.01);
            }
            case "slowness" -> {
                if (k.key().startsWith("frost") || k.key().equals("draugr")) {
                    SkillFx.sparks(level, chest, FROST, 6, 2);
                    around(level, p, ParticleTypes.SNOWFLAKE, 14, 0.02);
                } else SkillFx.sparks(level, chest, k.color(), 6, 2);
            }
            default -> {
            }
        }
    }

    /**
     * 맞은 사람 둘레의 바닐라 알갱이: 다른 사람에게는 가슴 둘레에, 맞은 사람 자신에게는 발밑에 몇 개만 (가슴 높이의 큰 알갱이 · 연기가 1인칭 화면 바로 앞을
     * 가리지 않게 — 9단계 사진첩에서 독 방울 · 시듦 연기가 화면을 덮던 것)
     */
    private static void around(ServerLevel level, ServerPlayer victim, net.minecraft.core.particles.ParticleOptions type, int n, double speed) {
        Vec3 chest = V.up(victim.position(), 1.0);
        Vec3 feet = V.up(victim.position(), 0.15);
        for (ServerPlayer viewer : level.players()) {
            boolean self = viewer == victim;
            Vec3 at = self ? feet : chest;
            if (viewer.position().distanceToSqr(at) > 48 * 48) continue;
            level.sendParticles(viewer, type, false, false, at.x, at.y, at.z, self ? Math.max(1, n / 2) : n, self ? 0.45 : 0.25, self ? 0.05 : 0.35,
                    self ? 0.45 : 0.25, self ? 0 : speed);
        }
    }

    /** 트롤 내려치기 충격파: 맞은 곳 둘레 radius 칸의 다른 플레이어도 피해 × mult */
    private static void shockwave(Fight f, ServerPlayer target, double dmg) {
        RpgMonster m = f.m;
        Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        Vec3 at = target.position();
        SkillFx.shock(level, at, k.color(), k.shock().radius() / 2.1, 0.45);
        if ("ice".equals(k.shock().fx())) {
            // 서리 거인: 얼음 고리가 퍼지고 땅에 서리 금 · 고드름이 사방으로 튐 · 눈보라
            double rr = k.shock().radius();
            mmorpg.fabric.rune.RuneKit.novaRing(level, at, 0.5, rr, FROST, mmorpg.fabric.rune.RuneKit.WHITE, Opt.of("life", 0.5));
            mmorpg.fabric.rune.RuneKit.glowCrack(level, at, rr * 0.9, FROST, mmorpg.fabric.rune.RuneKit.WHITE, Opt.of("life", 1.6, "fade", 0.5));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + Kit.rand() * 0.5;
                Vec3 d = V.unit(new Vec3(Math.cos(a), 0.45, Math.sin(a)));
                mmorpg.fabric.rune.RuneKit.rfx2(level, "icicle", V.add(V.up(at, 0.4), d, 0.4), Opt.of("dir", d, "speed", 9.0, "size", 0.34, "life", 0.35), FROST,
                        mmorpg.fabric.rune.RuneKit.WHITE);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 0.4, at.z, 30, rr * 0.5, 0.25, rr * 0.5, 0.06);
            play(level, "block.glass.break", at, 0.7, 0.9);
        } else if ("void".equals(k.shock().fx())) VoidMonsters.shockFx(level, at, k.shock().radius()); // 0.32.0 공허 거수: 분홍 고리 · 빛나는 금 · 수정 조각
        else SkillFx.burst(level, V.up(at, 0.2), new double[] {0.5, 0.42, 0.32}, 1.6, 0.35);
        double r = k.shock().radius() + 0.5;
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (p.position().distanceTo(at) > r) continue;
            if (targetState(p, m) != State.OK) continue;
            SkillFx.shake(p, 0.25, 0.3);
            if (p == target) continue;
            if (Math.hypot(p.getX() - at.x, p.getZ() - at.z) > k.shock().radius()) continue;
            monsterHit(m, k, p, dmg * k.shock().mult());
        }
    }

    /** 맞힘: 피해 (나약함만큼 줄어듦) + 연출 + 트롤 충격파 */
    private static void strike(Fight f, ServerPlayer target) {
        RpgMonster m = f.m;
        Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        double dmg = k.attack() * weakMult(m);
        f.hits++;
        monsterHit(m, k, target, dmg);
        impactFx(level, m, k, target);
        play(level, k.hitSound(), target.position(), 0.9 + Kit.rand() * 0.2, 1);
        if (k.shock() != null) shockwave(f, target, dmg);
    }

    /** 공격 동작 끝: 처음 노린 플레이어가 아직 사거리 · 노린 쪽 안이고 보이면 맞음, 아니면 빗나감. 대상이 사라졌으면 조용히 끝 */
    private static boolean resolve(Fight f, long now) {
        RpgMonster m = f.m;
        Kind k = f.k;
        Windup w = f.windup;
        f.windup = null;
        f.nextAttack = Math.max(f.nextAttack, now + 4);
        if (w == null) return false;
        ServerPlayer target = w.target();
        if (targetState(target, m) != State.OK) return false;
        ServerLevel level = (ServerLevel) m.level();
        f.lastKind = w.kind();
        f.lastResolved = now;
        if (w.kind().equals("counter")) {
            Vec3 at = w.spot();
            SkillFx.shock(level, at, k.color(), COUNTER_RADIUS / 2.1, 0.35);
            SkillFx.burst(level, V.up(at, 0.3), k.color(), 1.2, 0.3);
            if (Math.hypot(target.getX() - at.x, target.getZ() - at.z) <= COUNTER_RADIUS && Math.abs(target.getY() - at.y) <= 1.5 && canSee(m, k, target)) {
                strike(f, target);
                return true;
            }
            f.misses++;
            play(level, "item.spear.attack_miss", at, 0.9, 0.5);
            return false;
        }
        Gap g = gapTo(m, k, target);
        if (g.gap() <= k.reach() + TOLERANCE && heightOk(k, g.dy(), 0.5) && inArc(m, k, w.aim(), target) && canSee(m, k, target)) {
            strike(f, target);
            hop(f, target, now);
            return true;
        }
        f.misses++;
        missFx(level, m, k, target.position());
        hop(f, target, now);
        return false;
    }

    /** 이번에 쫓을 플레이어 (상태): 지금 대상 → 최근에 때린 순서. 공격할 수 있는 첫 사람, 없으면 잠깐 멈춘 사람. 모두 없어졌거나 leash 밖이면 null */
    private record Pick(ServerPlayer p, State st) {}

    private static Pick pickTarget(Fight f) {
        List<ServerPlayer> list = new ArrayList<>();
        list.add(f.player);
        List<Attacker> recent = new ArrayList<>(f.attackers.values());
        recent.sort(Comparator.comparingLong(Attacker::t).reversed());
        for (Attacker a : recent) list.add(a.p());
        ServerPlayer paused = null;
        for (ServerPlayer p : list) {
            State st = targetState(p, f.m);
            if (st == State.GONE) {
                if (p != null) f.attackers.remove(p.getUUID());
                continue;
            }
            if (gapTo(f.m, f.k, p).dist() > LEASH) continue;
            if (st == State.OK) return new Pick(p, State.OK);
            if (paused == null) paused = p;
        }
        return paused != null ? new Pick(paused, State.PAUSE) : null;
    }

    /** 싸움 끝: 기록을 지우고 엔티티가 노리던 이 싸움의 사람을 놓아 줌 (집으로 돌아가게) */
    private static void endFight(Fight f) {
        if (f.charge != null) endCharge(f, Tasks.now(), false);
        if (f.voidRun != null) VoidMonsters.cancel(f, Tasks.now());
        FIGHTS.remove(f.m.getUUID(), f);
        LivingEntity t = f.m.getTarget();
        if (t instanceof ServerPlayer p && (p == f.player || f.attackers.containsKey(p.getUUID()) || targetState(p, f.m) != State.OK)) f.m.setTarget(null);
    }

    /** 2틱마다: 싸우는 몬스터마다 대상 고르기 · 멈춤 · 공격 동작 · 판정 */
    static void fightTick() {
        long now = Tasks.now();
        for (Fight f : new ArrayList<>(FIGHTS.values())) {
            if (FIGHTS.get(f.m.getUUID()) != f) continue; // 이번 틱에 끝난 싸움 (플레이어가 쓰러짐 등)
            try {
                fightStep(f, now);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] monster fight", e);
                FIGHTS.remove(f.m.getUUID(), f);
            }
        }
    }

    private static void fightStep(Fight f, long now) {
        RpgMonster m = f.m;
        Kind k = f.k;
        if (m.isRemoved() || Kit.hasNoHealth(m)) {
            FIGHTS.remove(m.getUUID(), f);
            return;
        }
        Pick pick = pickTarget(f);
        if (pick == null) {
            endFight(f); // 때린 사람이 모두 없어지거나 leash 밖
            return;
        }
        f.player = pick.p();
        Gap g = gapTo(m, k, f.player);
        if (pick.st() == State.PAUSE) {
            // 크리에이티브 · 관전 · 쓰러지는 중: 하던 동작은 끊고 기다림 (토템으로 살아나면 이어서), 오래가면 싸움 끝
            f.windup = null;
            if (f.charge != null) endCharge(f, now, false);
            if (f.voidRun != null) VoidMonsters.cancel(f, now);
            if (f.paused == null) f.paused = now;
            if (now - f.paused > PAUSE_LIMIT) endFight(f);
            return;
        }
        f.paused = null;
        if (f.decoy != null) {
            // 0.29.0 미끼 (잔영): 그 자리로 걸어감 · 공격하지 않음 — 끝나면 다시 그 사람
            if (now < f.decoyUntil) {
                f.windup = null;
                if (m.getTarget() != null) m.setTarget(null);
                if (now % 10 == 0 && m.position().distanceTo(f.decoy) > 1.2) m.getNavigation().moveTo(f.decoy.x, f.decoy.y, f.decoy.z, 1.0);
                return;
            }
            f.decoy = null;
            m.setTarget(f.player);
        }
        if (stunned(m)) {
            f.windup = null; // 기절 · 빙결 · 석화: 하던 동작도 끊김
            if (f.charge != null) endCharge(f, now, false);
            if (f.voidRun != null && VoidMonsters.interruptible(f)) VoidMonsters.cancel(f, now); // 0.32.0 공허 특성 준비도 끊김 (땅속 · 균열 속 · 공중은 그대로)
            if (f.voidRun == null) return;
        }
        if (f.charge != null) {
            chargeStep(f, now);
            return;
        }
        if (f.voidRun != null) {
            VoidMonsters.step(f, now); // 0.32.0 덮치기 · 균열 이동 · 구체 · 땅굴 · 수정 가시
            return;
        }
        if (f.windup != null) {
            if (now >= f.windup.at()) resolve(f, now);
            return;
        }
        boolean traits = k.traits() != VoidMonsters.NONE;
        if (traits) VoidMonsters.hold(f, g, now); // 해파리: 구체를 쏠 거리면 다가오지 않음
        if (now < f.nextAttack) return;
        ServerPlayer p = f.player;
        if (g.gap() <= k.reach() && heightOk(k, g.dy(), 0)) {
            if (canSee(m, k, p)) startAttack(f, now, "melee");
            return;
        }
        if (traits && VoidMonsters.tryTrait(f, g, now)) return;
        if (traits && k.traits().orb() != null) return; // 해파리: 달려들지 않음 (구체로 싸움)
        if (now < f.nextLunge || rooted(m) || hidden(p)) return;
        Charge ch = k.charge();
        if (ch != null ? g.gap() <= ch.range() && Math.abs(g.dy()) <= 1.5 : g.gap() <= LUNGE_RANGE && Math.abs(g.dy()) <= LUNGE_HEIGHT) {
            if (canSee(m, k, p)) {
                if (ch != null) startCharge(f, now); // 멧돼지: 달려들기 대신 돌진
                else lunge(f, g, now);
            }
        } else {
            Attacker a = f.attackers.get(p.getUUID());
            if (a != null && now - a.t() <= COUNTER_WINDOW && touchReach(m, k, p) && canSee(m, k, p)) counter(f, now); // 닿지 못하는 곳에서 방금 때렸을 때만
        }
    }

    /** 1초마다 회복: 싸움이 끝나고 regenDelay 틱 동안 맞지 않으면 10%, 트롤은 싸우는 중에도 regen.after 틱 동안 안 맞으면 */
    static void regenTick() {
        long now = Tasks.now();
        for (Map.Entry<UUID, Hurt> en : new ArrayList<>(HURT.entrySet())) {
            Hurt s = en.getValue();
            RpgMonster m = s.m();
            Kind k = s.k();
            if (m.isRemoved()) {
                HURT.remove(en.getKey(), s);
                continue;
            }
            boolean fighting = FIGHTS.containsKey(en.getKey());
            double rate = 0;
            if (fighting) {
                if (k.regen() != null && now - s.t() >= k.regen().after()) rate = k.regen().perSec();
            } else if (now - s.t() >= REGEN_DELAY) rate = REGEN_PER_SEC;
            else if (k.regen() != null && now - s.t() >= k.regen().after()) rate = k.regen().perSec();
            if (rate <= 0) continue;
            float max = m.getMaxHealth();
            float cur = m.getHealth();
            if (cur <= 0) continue; // 죽는 중
            double next = Math.min(max, cur + max * rate);
            if (next > cur + 1e-9) {
                m.setHealth((float) next);
                if (k.regen() != null && rate == k.regen().perSec() && m.level() instanceof ServerLevel level) {
                    SkillFx.sparks(level, V.up(m.position(), k.height() * 0.5), new double[] {0.45, 1.0, 0.45}, 3, 1.5);
                }
            }
            if (next >= max - 1e-6 && !fighting) HURT.remove(en.getKey(), s);
        }
    }

    // ------------------------------------------------------------ 9단계 특성: 니세 깡충 · 멧돼지 돌진 · 웜 유충 매복 · 서리 늑대 무리

    private static final double[] DUST = {0.66, 0.55, 0.4};
    private static final double[] SAND = {0.93, 0.8, 0.52};
    private static final double[] FROST = {0.72, 0.92, 1.0};
    private static final double[] POISON = {0.45, 0.95, 0.3};
    private static final double[] WITHER = {0.2, 0.17, 0.22};

    static String anim(Kind k, String name) {
        return "animation.rpg.monster." + k.key() + "." + name;
    }

    /** 니세: 공격한 뒤 노린 사람 반대쪽으로 깡충 (구속 · 공중이면 안 함) — 다음 공격 · 달려들기는 pause 틱 뒤 */
    private static void hop(Fight f, ServerPlayer from, long now) {
        RpgMonster m = f.m;
        Kind k = f.k;
        Hop h = k.hop();
        if (h == null || rooted(m) || !m.onGround()) return;
        ServerLevel level = (ServerLevel) m.level();
        Vec3 away = V.flat(V.sub(m.position(), from.position()));
        Kit.launch(m, away.x * h.back(), h.up(), away.z * h.back());
        m.playAnimation(anim(k, "hop"));
        SkillFx.burst(level, V.up(m.position(), 0.1), DUST, 0.5, 0.25);
        play(level, "entity.rabbit.jump", m.position(), 1.3, 0.8);
        f.nextAttack = Math.max(f.nextAttack, now + h.pause());
        f.nextLunge = Math.max(f.nextLunge, now + h.pause());
        f.hops++;
    }

    /** 멧돼지 돌진 시작: 그 사람 쪽을 보고 멈춰 앞발로 땅을 긁음 (prep 틱) — 달릴 길에 붉은 고리 줄 · 먼지 · 콧김 소리 */
    private static void startCharge(Fight f, long now) {
        RpgMonster m = f.m;
        Kind k = f.k;
        Charge c = k.charge();
        ServerLevel level = (ServerLevel) m.level();
        Vec3 dir = V.flat(V.sub(f.player.position(), m.position()));
        f.charge = new ChargeRun(now, now + c.prep(), now + c.prep() + c.dash(), dir, f.player);
        m.holdUntil = now + c.prep() + c.dash() + 4;
        m.getNavigation().stop();
        float yaw = Kit.lookRotation(m.position(), V.add(m.position(), dir, 1))[0];
        m.setYRot(yaw);
        m.setYBodyRot(yaw);
        m.setYHeadRot(yaw);
        m.playAnimation(anim(k, "charge_prep"), "charge", "0");
        play(level, k.windupSound(), m.position(), 0.75, 1.2);
        for (double s = k.half() + 0.6; s <= c.range() + k.half(); s += 1.3) {
            SkillFx.shock(level, V.add(m.position(), dir, s), RED, c.width() * 0.3, c.prep() / 20.0);
        }
        SkillFx.burst(level, V.up(m.position(), 0.1), DUST, 0.8 + k.half(), 0.4);
        f.nextAttack = Math.max(f.nextAttack, now + c.prep() + c.dash() + 6);
        f.nextLunge = now + c.prep() + c.dash() + k.lungeEvery();
    }

    /** 돌진 단계 (2틱마다): 준비 동안 앞발 먼지 → 준비가 끝나면 달리기 시작, 시간이 다하면 헛돌진 */
    private static void chargeStep(Fight f, long now) {
        ChargeRun c = f.charge;
        RpgMonster m = f.m;
        Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        if (!c.dashing) {
            if (now < c.prepUntil) {
                if ((now - c.start) % 4 == 0) SkillFx.burst(level, V.up(V.add(m.position(), c.dir, k.half() * 0.7), 0.05), DUST, 0.45, 0.3);
                return;
            }
            c.dashing = true;
            c.last = m.position();
            m.playAnimation(anim(k, "charge"), "charge", "0");
            play(level, "entity.camel.dash", m.position(), 0.9, 1);
            play(level, k.windupSound(), m.position(), 1.1, 1);
            return;
        }
        if (now >= c.dashUntil) endCharge(f, now, false);
    }

    /** 달리는 동안 (틱마다): 그 방향으로 speed 칸/틱, 먼지 자국, 몸 앞 width 칸 안의 사람과 부딪히면 피해 · 밀쳐냄, 벽에 막히면 끝 */
    private static void dashTick(Fight f, long now) {
        ChargeRun c = f.charge;
        RpgMonster m = f.m;
        Kind k = f.k;
        Charge ch = k.charge();
        ServerLevel level = (ServerLevel) m.level();
        Vec3 v = m.getDeltaMovement();
        m.setDeltaMovement(c.dir.x * ch.speed(), v.y, c.dir.z * ch.speed());
        m.needsSync = true;
        if (now % 2 == 0) SkillFx.burst(level, V.up(m.position(), 0.1), DUST, 0.5 + k.half() * 0.5, 0.35);
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (targetState(p, m) != State.OK || hidden(p)) continue;
            Vec3 rel = V.sub(p.position(), m.position());
            double along = rel.x * c.dir.x + rel.z * c.dir.z;
            double side = Math.abs(rel.x * c.dir.z - rel.z * c.dir.x);
            if (along < -0.3 || along > k.half() + 0.9 || side > ch.width() * 0.5 + PLAYER_HALF || rel.y < -1.5 || rel.y > k.height()) continue;
            chargeHit(f, p, now);
            return;
        }
        double moved = Math.hypot(m.getX() - c.last.x, m.getZ() - c.last.z);
        c.last = m.position();
        if (now - c.prepUntil >= 3 && moved < 0.12 && ++c.stuck >= 2) endCharge(f, now, false); // 벽 · 나무에 막힘
    }

    /** 돌진에 맞음: 피해 × mult (나약함만큼 줄어듦) · 달리던 쪽으로 밀쳐냄 · 화면 흔들림 · 흙먼지 */
    private static void chargeHit(Fight f, ServerPlayer p, long now) {
        RpgMonster m = f.m;
        Kind k = f.k;
        Charge ch = k.charge();
        ChargeRun c = f.charge;
        ServerLevel level = (ServerLevel) m.level();
        c.hit = true;
        f.hits++;
        f.lastKind = "charge";
        f.lastResolved = now;
        monsterHit(m, k, p, k.attack() * ch.mult() * weakMult(m));
        // 바닐라 밀침 (monsterHit 의 피해가 넣은 것) 위에 더하지 않고 바꿈: 가로 knock 칸/틱 (약 5칸 날아감) · 위로 lift
        f.lastPush = new Vec3(c.dir.x * ch.knock(), ch.lift(), c.dir.z * ch.knock());
        Kit.launch(p, f.lastPush.x, f.lastPush.y, f.lastPush.z);
        impactFx(level, m, k, p);
        SkillFx.burst(level, V.up(p.position(), 0.3), DUST, 1.6, 0.4);
        SkillFx.shake(p, 0.4, 0.35);
        play(level, k.hitSound(), p.position(), 0.8, 1.2);
        play(level, "entity.player.attack.knockback", p.position(), 0.9, 1);
        endCharge(f, now, true);
    }

    /** 돌진 끝: 멈춤 · 돌진 동작을 끔. 헛돌진이면 빗나감으로 세고 잠깐 숨을 고름 (다음 공격 24틱 뒤) */
    private static void endCharge(Fight f, long now, boolean hit) {
        ChargeRun c = f.charge;
        if (c == null) return;
        f.charge = null;
        RpgMonster m = f.m;
        m.holdUntil = 0;
        m.stopAnimation("charge");
        Vec3 v = m.getDeltaMovement();
        m.setDeltaMovement(v.x * 0.2, v.y, v.z * 0.2);
        m.needsSync = true;
        if (!hit && c.dashing) {
            f.misses++;
            f.lastKind = "charge";
            f.lastResolved = now;
            if (m.level() instanceof ServerLevel level) {
                SkillFx.burst(level, V.up(m.position(), 0.2), DUST, 1.2, 0.35);
                play(level, "entity.hoglin.step", m.position(), 0.7, 1);
            }
        }
        f.nextAttack = Math.max(f.nextAttack, now + (hit ? 10 : 24));
    }

    /** 숨었는지 (웜 유충 — 시험 · 다른 모듈) */
    public static boolean buried(Entity e) {
        return e instanceof RpgMonster m && m.buried();
    }

    /** 웜 유충 숨기: 모래 속으로 (속성 rpg:buried — 충돌 상자 낮춤 · 걷지 않음, 동작 buried 끝 자세 유지, 이름표 치움) · 모래가 튐 */
    public static void burrow(RpgMonster m) {
        Kind k = kindOf(m);
        if (k == null || k.burrow() == null || m.buried() || !(m.level() instanceof ServerLevel level)) return;
        m.setProp(RpgMonster.BURIED, true);
        m.playAnimation(anim(k, "buried"), "burrow", "0");
        m.getNavigation().stop();
        setLabel(m, null);
        sandFx(level, m, k, 0.8);
        play(level, "block.sand.break", m.position(), 0.8, 1);
    }

    /** 튀어나오기: 숨은 웜 유충이 솟구침 (모래 · 흔들림 · 소리) — p 가 있으면 그 사람과 먼저 싸움을 시작 (첫 공격은 emerge 틱 뒤) */
    public static void emerge(RpgMonster m, Kind k, ServerPlayer p, long now) {
        if (!m.buried() || !(m.level() instanceof ServerLevel level)) return;
        m.setProp(RpgMonster.BURIED, false);
        m.playAnimation(anim(k, "emerge"), "burrow", null);
        m.calmSince = 0;
        sandFx(level, m, k, 1.6);
        play(level, "block.sand.break", m.position(), 0.6, 1.4);
        play(level, k.windupSound(), m.position(), 0.7, 1.2);
        mmorpg.fabric.rune.RuneKit.shakeAround(level, m.position(), 6, 0.25, 0.3);
        if (p == null || targetState(p, m) != State.OK) return;
        Fight f = FIGHTS.get(m.getUUID());
        if (f == null) {
            f = new Fight(m, k, p, now);
            FIGHTS.put(m.getUUID(), f);
        }
        f.player = p;
        f.attackers.put(p.getUUID(), new Attacker(p, now));
        f.nextAttack = Math.max(f.nextAttack, now + (k.burrow() != null ? k.burrow().emerge() : 10));
        f.nextLunge = Math.max(f.nextLunge, f.nextAttack + 10);
        m.setTarget(p);
    }

    /** 모래가 튐: 발밑 블록 조각 · 모래빛 고리 · 불꽃 */
    private static void sandFx(ServerLevel level, RpgMonster m, Kind k, double size) {
        Vec3 at = m.position();
        net.minecraft.world.level.block.state.BlockState below = level.getBlockState(m.blockPosition().below());
        if (below.isAir()) below = net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below), at.x, at.y + 0.3, at.z, (int) (18 * size), 0.45 * size,
                0.3, 0.45 * size, 0.15);
        SkillFx.shock(level, at, SAND, (size + k.half()) / 2.1, 0.5);
        SkillFx.burst(level, V.up(at, 0.3), SAND, size + k.half(), 0.4);
    }

    /** 웜 유충 (5틱마다): 숨은 채면 가까이 온 사람에게 튀어나옴 (맞았으면 그 사람), 나와 있으면 싸우지 않은 지 rest 틱 뒤 다시 숨음 */
    private static void burrowTick(RpgMonster m, Kind k, long now) {
        Burrow b = k.burrow();
        ServerLevel level = (ServerLevel) m.level();
        if (Kit.hasNoHealth(m)) return;
        Fight f = FIGHTS.get(m.getUUID());
        if (m.buried()) {
            if (f != null) {
                emerge(m, k, f.player, now);
                return;
            }
            ServerPlayer near = null;
            double best = b.detect();
            for (ServerPlayer p : level.players()) {
                if (targetState(p, m) != State.OK || hidden(p)) continue;
                double d = p.position().distanceTo(m.position());
                if (d <= best) {
                    best = d;
                    near = p;
                }
            }
            if (near != null) emerge(m, k, near, now);
            else if ((now / 5) % 8 == 0) {
                // 숨은 자리 표시: 모래가 조금씩 흘러내림
                net.minecraft.world.level.block.state.BlockState below = level.getBlockState(m.blockPosition().below());
                if (!below.isAir()) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST, below), m.getX(), m.getY() + 0.4, m.getZ(), 3,
                            0.3, 0.1, 0.3, 0);
                }
            }
            return;
        }
        if (f != null) {
            m.calmSince = 0;
            return;
        }
        if (m.calmSince == 0) {
            m.calmSince = now;
            return;
        }
        if (now - m.calmSince >= b.rest()) burrow(m);
    }

    /** 서리 늑대: 처음 맞으면 울부짖고 (동작 howl · 하늘빛 고리) 둘레 radius 칸의 싸우지 않던 서리 늑대도 그 사람에게 덤빔 (조금씩 늦게). 부른 수 */
    private static int packAlert(RpgMonster m, Kind k, ServerPlayer p, long now) {
        ServerLevel level = (ServerLevel) m.level();
        double r = k.pack().radius();
        boolean voidKind = k.traits().isVoid();
        double[] col = voidKind ? VoidMonsters.PINK : FROST;
        m.playAnimation(anim(k, k.pack().anim()));
        if (voidKind) {
            // 0.32.0 공허 도약충: 날카로운 울음 · 분홍 고리 · 공허 알갱이
            play(level, k.pack().sound(), m.position(), 0.6, 1.4);
            play(level, k.windupSound(), m.position(), 1.4, 1.0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + k.height() * 0.6, m.getZ(), 20, 0.4, 0.3, 0.4, 0.05);
        } else {
            play(level, "item.goat_horn.sound.0", m.position(), 1.25, 1.4); // 울부짖음 (26.3 에는 늑대 울음소리가 없어 뿔피리 — 무리를 부르는 소리)
            play(level, "entity.wolf.growl", m.position(), 0.8, 1.2);
            level.sendParticles(ParticleTypes.SNOWFLAKE, m.getX(), m.getY() + k.height() * 0.6, m.getZ(), 16, 0.4, 0.3, 0.4, 0.04);
        }
        SkillFx.shock(level, m.position(), col, r / 2.1, 0.8);
        int n = 0;
        for (RpgMonster o : level.getEntitiesOfClass(RpgMonster.class, m.getBoundingBox().inflate(r), x -> x != m && kindOf(x) == k)) {
            if (o.position().distanceTo(m.position()) > r || FIGHTS.containsKey(o.getUUID()) || Kit.hasNoHealth(o)) continue;
            Fight f = new Fight(o, k, p, now);
            f.nextLunge = now + 8 + n * 4;
            FIGHTS.put(o.getUUID(), f);
            o.setTarget(p);
            int delay = 4 + n * 3;
            Tasks.later(delay, () -> {
                if (o.isRemoved() || Kit.hasNoHealth(o)) return;
                o.playAnimation(anim(k, k.pack().anim()));
                SkillFx.shock(level, o.position(), col, 0.9, 0.5);
            });
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------ 이벤트

    /** 몬스터가 맞음 (AFTER_DAMAGE): 회복 시계 · 싸움 시작 / 다음 대상 (마지막으로 때린 사람 — 도발 중이면 도발한 사람 그대로) */
    static void onHurt(RpgMonster m, DamageSource source) {
        Kind k = kindOf(m);
        if (k == null || Kit.hasNoHealth(m)) return; // 죽는 중인 몸은 싸움을 시작하지 않음
        long now = Tasks.now();
        HURT.put(m.getUUID(), new Hurt(m, k, now));
        ServerPlayer hitter = source.getEntity() instanceof ServerPlayer sp && targetState(sp, m) == State.OK ? sp : null;
        if (m.buried()) emerge(m, k, hitter, now); // 웜 유충: 숨은 채 맞으면 튀어나옴
        if (!(source.getEntity() instanceof ServerPlayer p) || targetState(p, m) != State.OK) return;
        Fight f = FIGHTS.get(m.getUUID());
        if (f != null) {
            // 다음 공격은 마지막으로 때린 사람에게 (엔티티 hurt_by_target 과 같게). 하던 동작은 처음 노린 사람에게. 도발 중에는 도발한 사람 그대로
            if (f.taunt == null || now >= f.taunt.until() || f.taunt.id().equals(p.getUUID())) f.player = p;
            f.attackers.put(p.getUUID(), new Attacker(p, now));
            return;
        }
        // 처음 맞음: 사거리 안이면 바로 반격, 멀면 잠깐 뒤 달려듦
        Fight nf = new Fight(m, k, p, now);
        FIGHTS.put(m.getUUID(), nf);
        if (k.pack() != null) nf.packCalled = packAlert(m, k, p, now); // 서리 늑대: 울부짖어 무리를 부름
    }

    /** 죽음 (AFTER_DEATH): 플레이어면 그 사람과의 싸움 끝, 몬스터면 연출 + 경험치 · 전리품 (loot.js lootOnDeath) */
    static void onDeath(LivingEntity e, DamageSource source) {
        if (e instanceof ServerPlayer sp) {
            forgetMonsterFights(sp.getUUID()); // 죽은 플레이어와의 싸움 끝 (되살아나도 다시 때려야 싸움)
            return;
        }
        Kind k = kindOf(e);
        if (k == null || !(e.level() instanceof ServerLevel level)) return;
        FIGHTS.remove(e.getUUID());
        HURT.remove(e.getUUID());
        Vec3 at = V.up(e.position(), k.height() * 0.5);
        if (k.traits().isVoid()) VoidMonsters.deathFx(level, e, at, k); // 0.32.0 빨려 들어가듯 사라짐 (동작 die)
        else {
            SkillFx.burst(level, at, k.color(), 1.2 + k.half(), 0.35);
            SkillFx.sparks(level, at, k.color(), 8, 4);
        }
        // 잡은 플레이어에게 경험치 (최고 레벨이 xpUntil 이상이면 0) + 전리품. 불에 타 죽으면 10초 안에 마지막으로 때린 사람
        try {
            Loot.lootOnDeath(e, k.key(), Loot.killCredit(e, source), k.height());
        } catch (RuntimeException err) {
            Mmorpg.LOG.warn("[mmorpg] monster loot", err);
        }
    }

    /** 플레이어가 나가거나 죽으면 그 플레이어와의 싸움을 끝냄 (같은 몬스터를 때린 다른 플레이어가 있으면 그 사람과 계속) */
    public static void forgetMonsterFights(UUID playerId) {
        for (Fight f : new ArrayList<>(FIGHTS.values())) {
            f.attackers.remove(playerId);
            if (f.windup != null && f.windup.target().getUUID().equals(playerId)) f.windup = null;
            if (f.charge != null && f.charge.target.getUUID().equals(playerId)) endCharge(f, Tasks.now(), false);
            if (f.voidRun != null && f.player != null && f.player.getUUID().equals(playerId)) VoidMonsters.cancel(f, Tasks.now());
            if (f.taunt != null && f.taunt.id().equals(playerId)) f.taunt = null; // 죽은 사람의 도발은 끝
            if (f.player != null && !f.player.getUUID().equals(playerId) && !f.player.isRemoved()) continue;
            Attacker next = null;
            for (Attacker a : f.attackers.values()) {
                if (a.p().isRemoved() || a.p().getUUID().equals(playerId)) continue;
                if (next == null || a.t() > next.t()) next = a;
            }
            if (next != null) f.player = next.p();
            else endFight(f);
        }
    }

    // ------------------------------------------------------------ 받는 피해 · 전리품 (RpgMonster 가 부름)

    /** 몬스터가 받는 피해 (플레이어가 때린 것): 스켈야스크림슬리 껍질 -20% (combat.js 몬스터 훅 incoming — 평타 · 활 · 완드 · 스킬 · 룬 모두) */
    static float adjustIncoming(RpgMonster m, DamageSource source, float amount) {
        Kind k = kindOf(m);
        if (k == null || !(k.shell() > 0) || !(source.getEntity() instanceof Player)) return amount;
        return (float) (amount * (1 - k.shell()));
    }

    /**
     * 은화 (0.21.0 — 실버, 예전 에메랄드 그대로 1:1. 행동 팩 전리품 표: random_chance → set_count 최소 ~ 최대 — 확률은 Loot.rand). 주우면 가방에,
     * 우클릭하면 지갑으로 (상점에서는 가방의 은화도 그대로 씀)
     */
    static void dropSilver(RpgMonster m, ServerLevel level) {
        Kind k = kindOf(m);
        if (k == null || k.emerald() == null || Loot.rand.getAsDouble() >= k.emerald()[0]) return;
        int min = (int) k.emerald()[1];
        int max = (int) k.emerald()[2];
        int n = Math.max(min, Math.min(max, min + (int) Math.floor(Loot.rand.getAsDouble() * (max - min + 1))));
        if (n > 0) m.spawnAtLocation(level, new ItemStack(mmorpg.fabric.economy.EconomyItems.SILVER_COIN, n));
    }

    // ------------------------------------------------------------ 이름표 (monsterLabel · refreshTag · hp.js 이름표 새로 고침)

    /** 이름표: "Lv.8 굴론" (노란 레벨 + 흰 이름), 다쳤으면 아래 줄에 체력 막대 20칸 (절반 넘게 초록, 1/4 넘게 노랑, 그 아래 빨강) */
    public static String monsterLabel(Kind k, double hp, double max) {
        String head = "§eLv." + k.level() + " §f" + k.name();
        if (!(max > 0) || hp >= max - 1e-6) return head;
        int n = HP_BAR;
        double r = Math.max(0, hp) / max;
        int filled = (int) Math.min(n, Math.max(hp > 0 ? 1 : 0, Math.round(r * n)));
        String col = r > 0.5 ? "§a" : r > 0.25 ? "§e" : "§c";
        return head + "\n" + col + "|".repeat(filled) + "§8" + "|".repeat(n - filled);
    }

    /** 지금 머리 위 글자 (베드락 서식 — 없으면 null) */
    public static String labelOf(Entity e) {
        return e instanceof RpgMonster m ? m.labelText : null;
    }

    /** 머리 위 이름표 엔티티 (없으면 null — 시험용) */
    public static Display.TextDisplay labelEntity(Entity e) {
        return e instanceof RpgMonster m && m.label != null && !m.label.isRemoved() ? m.label : null;
    }

    /** 이름표를 바꿈: 첫 줄은 이름 (RpgMonster.getTypeName — 사망 메시지에 첫 줄만), 전체는 머리 위 글자 표시 엔티티 */
    static void setLabel(RpgMonster m, String text) {
        if (text == null || text.isEmpty()) {
            m.labelText = null;
            dropLabel(m);
            return;
        }
        if (m.isCustomNameVisible()) m.setCustomNameVisible(false);
        boolean same = text.equals(m.labelText);
        m.labelText = text;
        if (!(m.level() instanceof ServerLevel level) || m.isRemoved()) return;
        Display.TextDisplay before = m.label;
        Display.TextDisplay td = ensureLabel(m, level);
        if (td != null && (!same || td != before)) td.setText(Component.literal(BedrockText.toJava(text)));
    }

    /** 이름표 엔티티: 타고 있는 것을 이어 쓰고 (다시 불러온 몬스터 — 남는 것은 치움), 없으면 만들어 태움 */
    private static Display.TextDisplay ensureLabel(RpgMonster m, ServerLevel level) {
        Display.TextDisplay td = m.label;
        if (td != null && (td.isRemoved() || td.level() != level)) td = null;
        for (Entity p : new ArrayList<>(m.getPassengers())) {
            if (!(p instanceof Display.TextDisplay t) || !t.entityTags().contains(LABEL_TAG)) continue;
            if (td == null) td = t;
            else if (t != td) t.discard();
        }
        if (td == null) td = spawnLabel(level, m);
        if (td == null) return null;
        if (td.getFlags() != LABEL_FLAGS) td.setFlags(LABEL_FLAGS);
        if (td.getVehicle() != m && !td.startRiding(m)) td.setPos(m.getX(), m.getY() + m.getBbHeight() + RpgMonster.LABEL_GAP, m.getZ());
        m.label = td;
        return td;
    }

    /** 이름표 글자 모양: 그림자 · 앞에 그림 (베드락 이름표처럼 몸 (트롤 가시 · 허수아비 모자) · 벽에 가리지 않음) */
    private static final byte LABEL_FLAGS = (byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH);

    /** 글자 표시 엔티티 (여러 줄 — 늘 카메라를 보고, 밤에도 밝게, 이름표처럼 반투명 검은 바탕) — 몬스터에 태워 같이 저장 · 옮겨짐 */
    private static Display.TextDisplay spawnLabel(ServerLevel level, RpgMonster m) {
        Display.TextDisplay td = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (td == null) return null;
        td.setText(Component.literal(BedrockText.toJava(m.labelText == null ? "" : m.labelText)));
        td.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        td.setFlags(LABEL_FLAGS);
        td.setBrightnessOverride(Brightness.FULL_BRIGHT);
        td.addTag(LABEL_TAG);
        td.snapTo(m.getX(), m.getY() + m.getBbHeight() + RpgMonster.LABEL_GAP, m.getZ(), 0F, 0F);
        if (!level.addFreshEntity(td)) return null;
        td.startRiding(m);
        return td;
    }

    /** 이름표를 치움 (몬스터를 치우거나 죽을 때) */
    static void dropLabel(RpgMonster m) {
        if (m.label != null && !m.label.isRemoved()) m.label.discard();
        m.label = null;
        for (Entity p : new ArrayList<>(m.getPassengers())) if (p instanceof Display.TextDisplay t && t.entityTags().contains(LABEL_TAG)) t.discard();
    }

    /** 몬스터의 서버 틱 (RpgMonster.tick): 처음 틱에 무리 태그 · 회복 시작, 체력이 바뀌었거나 이름표가 없으면 이름표를 다시 맞춤 */
    static void serverTick(RpgMonster m) {
        Kind k = kindOf(m);
        if (k == null || m.isRemoved()) return;
        if (!m.tracked) {
            m.tracked = true;
            for (String f : m.def.families()) m.addTag(f);
            track(m, k);
        }
        long now = Tasks.now();
        if (k.charge() != null) {
            Fight f = FIGHTS.get(m.getUUID());
            if (f != null && f.charge != null && f.charge.dashing) dashTick(f, now);
        }
        if (k.burrow() != null && (now + m.getId()) % 5 == 0) burrowTick(m, k, now);
        if (k.traits() != VoidMonsters.NONE) VoidMonsters.tick(m, k, now); // 0.32.0 덮치기 비행 · 땅속 이동 · 공허 알갱이
        if (VoidMonsters.phased(m)) {
            if (m.labelText != null || m.label != null) setLabel(m, null); // 균열 속 · 땅속: 이름표 없음 (나오면 아래에서 다시 붙음)
            return;
        }
        if (m.buried()) {
            if (m.labelText != null || m.label != null) setLabel(m, null); // 숨은 동안 이름표 없음 (다시 나오면 아래에서 다시 붙음)
            return;
        }
        float hp = m.getHealth();
        float max = m.getMaxHealth();
        Display.TextDisplay td = m.label;
        boolean labelOk = td != null && !td.isRemoved() && td.getVehicle() == m;
        if (hp != m.labelHp || max != m.labelMax || !labelOk || m.labelText == null) {
            m.labelHp = hp;
            m.labelMax = max;
            setLabel(m, monsterLabel(k, hp, max));
        }
    }

    /** 다친 채로 (다시) 불러온 몬스터는 바로 회복을 시작 */
    private static void track(RpgMonster m, Kind k) {
        float hp = m.getHealth();
        if (!(hp > 0) || hp >= m.getMaxHealth() - 1e-6) return;
        Hurt h = HURT.get(m.getUUID());
        // 같은 몬스터를 다시 불러오면 엔티티가 새로 만들어짐 (UUID 는 같음) — 예전 몸의 기록은 마지막으로 맞은 틱만 이어 받음
        if (h == null || h.m() != m) HURT.put(m.getUUID(), new Hurt(m, k, h != null ? h.t() : Tasks.now() - REGEN_DELAY));
    }

    /** 불러온 엔티티 (ENTITY_LOAD): 몬스터에 타지 않은 이름표 (몬스터가 사라진 뒤 남은 것) 는 치움 */
    private static void onLoad(Entity e, ServerLevel level) {
        if (!(e instanceof Display.TextDisplay td) || !td.entityTags().contains(LABEL_TAG)) return;
        Tasks.later(2, () -> {
            if (!td.isRemoved() && !(td.getVehicle() instanceof RpgMonster)) td.discard();
        });
    }

    // ------------------------------------------------------------ 소환 · 치우기 (/mob)

    /** 그 자리에 몬스터 하나 (yaw 쪽을 보게). 못 세웠으면 null */
    public static RpgMonster spawnAt(ServerLevel level, String key, Vec3 at, float yaw) {
        EntityType<? extends BedrockMob> type = RpgEntities.type(Mobs.monsterId(key));
        if (type == null) return null;
        BedrockMob e = type.create(level, EntitySpawnReason.COMMAND);
        if (!(e instanceof RpgMonster m)) return null;
        m.snapTo(at.x, at.y, at.z, yaw, 0F);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);
        if (!level.addFreshEntity(m)) return null;
        Kind k = kindOf(m);
        if (k != null) m.setNameTag(monsterLabel(k, 1, 1));
        if (k != null && k.traits().isVoid()) VoidMonsters.spawnFx(level, m, k); // 0.32.0 균열이 열리며 나타남
        return m;
    }

    /** 눈앞 (4칸) 에 몬스터 n 마리 (시험 · 배치용, 운영자 — spawnMonsters) */
    public static List<RpgMonster> spawnMonsters(ServerPlayer sp, String key, int n) {
        Kind k = kind(key);
        List<RpgMonster> out = new ArrayList<>();
        if (k == null) return out;
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 side = new Vec3(-f.z, 0, f.x);
        int count = Math.max(1, Math.min(10, n));
        for (int i = 0; i < count; i++) {
            double off = (i - (count - 1) / 2.0) * 1.8;
            Vec3 at = V.add(V.add(sp.position(), f, 4 + k.half()), side, off);
            float yaw = Kit.lookRotation(at, new Vec3(sp.getX(), at.y, sp.getZ()))[0];
            RpgMonster m = spawnAt(level, k.key(), at, yaw);
            if (m != null) out.add(m);
        }
        return out;
    }

    /** 사냥터 몬스터인지 (태그 rpg_zone_&lt;번호&gt;) */
    public static boolean inZone(Entity e) {
        for (String t : e.entityTags()) if (t.startsWith(ZONE_TAG)) return true;
        return false;
    }

    /** 주변 radius 칸의 몬스터 치우기 (사냥터 몬스터는 남김 — 치워도 다시 채워짐 → /mobzone clear). 치운 수 */
    public static int clearMonsters(ServerPlayer sp, double radius) {
        Vec3 c = sp.position();
        int n = 0;
        for (RpgMonster m : sp.level().getEntitiesOfClass(RpgMonster.class, new AABB(c, c).inflate(radius), x -> x.position().distanceTo(c) <= radius)) {
            if (inZone(m)) continue;
            FIGHTS.remove(m.getUUID());
            HURT.remove(m.getUUID());
            m.discard();
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------ 등록

    /** Mmorpg.onInitialize: 피해 · 죽음 · 불러옴 이벤트, 2틱마다 싸움 · 1초마다 회복, 도발 · 피해 숫자 자리 훅, 사냥터 · 명령어 */
    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!(entity instanceof RpgMonster m)) return;
            try {
                onHurt(m, source);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] monster hurt", e);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            try {
                onDeath(entity, source);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] monster die", e);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register(Monsters::onLoad);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetMonsterFights(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = Tasks.now();
            if (now % 2 == 0) fightTick();
            if (now % 20 == 0) {
                try {
                    regenTick();
                } catch (RuntimeException e) {
                    Mmorpg.LOG.warn("[mmorpg] monster regen", e);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FIGHTS.clear();
            HURT.clear();
        });
        // v2.20.0 아이기스의 방패 도발
        Aegis.addTauntHook((sp, e, ticks) -> tauntMonster(e, sp, ticks));
        // 피해 숫자: 몸통에서 떠올라 이름표 아래에서 끝남
        Function<LivingEntity, Vec3> prev = DamageNumbers.anchor;
        DamageNumbers.anchor = e -> {
            Kind k = kindOf(e);
            return k != null ? new Vec3(e.getX(), e.getY() + damageTextHeight(k), e.getZ()) : prev.apply(e);
        };
        HuntZones.init();
        MonsterCommands.init();
        VoidMonsters.init();
    }

    /** 시험용: 싸움 · 회복 기록을 지움 */
    public static void clearState() {
        FIGHTS.clear();
        HURT.clear();
        VoidMonsters.clearState();
    }

    /** 다친 몬스터로 기록되어 있는지 (회복 대기 — 시험용) */
    public static boolean isHurt(Entity m) {
        return m != null && HURT.containsKey(m.getUUID());
    }

    /** 몬스터 키들 (명령어 제안 등 — 몬스터 순서) */
    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (Kind k : kinds().values()) out.add(k.key());
        return out;
    }
}
