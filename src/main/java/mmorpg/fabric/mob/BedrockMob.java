package mmorpg.fabric.mob;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Json;
import mmorpg.fabric.BedrockText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 애드온 엔티티 (몬스터 · 보스 · 보스 소품) 의 바탕: 수치는 MobData (행동 팩 그대로), 그리기는 클라이언트 BedrockRenderer (리소스 팩 그대로).
 * <ul>
 * <li>속성 (베드락 entity.setProperty / q.property — rpg:flash · rpg:gait …): setProp · prop. 클라이언트에 글자 (JSON) 로 동기화 → 렌더 컨트롤러 · 동작 식</li>
 * <li>동작 틀기 (베드락 entity.playAnimation(이름, {controller, stopExpression})): playAnimation. 컨트롤러마다 하나 (같은 컨트롤러에 새로 틀면 바뀜),
 * 튼 틱과 함께 동기화 → 클라이언트가 지난 시간으로 그림 (늦게 들어온 사람도 같은 자세). 멈춤 식 기본 q.any_animation_finished · "0" = 다음 동작까지</li>
 * <li>사건 (triggerEvent — rpg:hold · rpg:release …): 컴포넌트 묶음을 더하고 빼서 이동 빠르기를 바꿈 (minecraft:movement)</li>
 * <li>보스 막대 (minecraft:boss — 이름 · hud_range 안 플레이어), 피해 면역 (damage_sensor), 효과 면역, 물속 숨, 밀리지 않음, 사라지지 않음, 경험치 구슬 없음</li>
 * </ul>
 * 이름표: setNameTag (베드락 서식 글자 그대로 — always_show 면 늘 보임).
 */
public abstract class BedrockMob extends PathfinderMob {
    private static final EntityDataAccessor<String> PROPS = SynchedEntityData.defineId(BedrockMob.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMS = SynchedEntityData.defineId(BedrockMob.class, EntityDataSerializers.STRING);
    /** playAnimation 의 기본 컨트롤러 이름 (베드락과 같음) */
    public static final String RUNTIME = "__runtime_controller";

    public final MobData.Def def;
    /** 서버의 속성 값 (기본값과 다른 것만) */
    private final Map<String, Double> props = new LinkedHashMap<>();
    /** 서버의 튼 동작: 컨트롤러 → [동작 이름, 튼 틱 (세계 시간), 멈춤 식] */
    private final Map<String, Object[]> anims = new LinkedHashMap<>();
    private final Set<String> groups = new LinkedHashSet<>();
    private ServerBossEvent bossBar;

    // 클라이언트: 동기화된 글자를 읽은 것 (글자가 바뀔 때만 다시 읽음)
    private String propsSrc = "";
    private Map<String, Double> propsSeen = Map.of();
    private String animsSrc = "";
    private List<Playing> animsSeen = List.of();

    /** 튼 동작 하나 (클라이언트가 읽은 것): 컨트롤러 · 동작 이름 · 튼 틱 · 멈춤 식 */
    public record Playing(String controller, String anim, long start, String stop) {}

    protected BedrockMob(EntityType<? extends BedrockMob> type, Level level) {
        super(type, level);
        MobData.Def d = MobData.get(EntityType.getKey(type).toString());
        if (d == null) throw new IllegalStateException("MobData 에 없는 엔티티: " + EntityType.getKey(type));
        this.def = d;
        if (!d.gravity()) setNoGravity(true);
        if (!d.collision()) this.noPhysics = true;
        if (d.persistent()) setPersistenceRequired();
        triggerEvent("minecraft:entity_spawned");
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PROPS, "");
        builder.define(ANIMS, "");
    }

    // ------------------------------------------------------------ 속성

    /** 속성 값 (bool = 0/1, enum = 번호). 서버는 지금 값, 클라이언트는 동기화된 값. 없는 속성 = 0 */
    public double prop(String id) {
        Double v = level().isClientSide() ? syncedProps().get(id) : props.get(id);
        if (v != null) return v;
        MobData.Prop p = def.props().get(id);
        return p == null ? 0 : p.def();
    }

    public boolean propBool(String id) {
        return prop(id) != 0;
    }

    public int propInt(String id) {
        return (int) Math.round(prop(id));
    }

    public void setProp(String id, boolean value) {
        setProp(id, value ? 1 : 0);
    }

    /** 속성 바꾸기 (서버). 이 종류에 없는 속성은 무시, float · int 는 범위 안으로 */
    public void setProp(String id, double value) {
        if (level().isClientSide()) return;
        MobData.Prop p = def.props().get(id);
        if (p == null) return;
        double v = Math.max(p.min(), Math.min(p.max(), value));
        if ("int".equals(p.type()) || "enum".equals(p.type())) v = Math.round(v);
        Double old = props.get(id);
        if (old != null && old == v) return;
        if (old == null && v == p.def()) return;
        props.put(id, v);
        entityData.set(PROPS, Json.stringify(props));
    }

    /** 클라이언트: 동기화된 속성 (id → 값) */
    @SuppressWarnings("unchecked")
    public Map<String, Double> syncedProps() {
        String s = entityData.get(PROPS);
        if (!s.equals(propsSrc)) {
            propsSrc = s;
            Map<String, Double> m = new LinkedHashMap<>();
            if (!s.isEmpty()) {
                try {
                    for (Map.Entry<String, Object> e : Json.parseObject(s).entrySet()) {
                        if (e.getValue() instanceof Number n) m.put(e.getKey(), n.doubleValue());
                    }
                } catch (RuntimeException ignored) {
                    // 잘못된 글자면 기본값
                }
            }
            propsSeen = Collections.unmodifiableMap(m);
        }
        return propsSeen;
    }

    // ------------------------------------------------------------ 동작

    /** 동작 틀기 (기본 컨트롤러, 한 번 끝까지 돌면 멈춤) — 이름은 전체 이름 (animation.rpg.boss.surtr.slam) */
    public void playAnimation(String animation) {
        playAnimation(animation, null, null);
    }

    /** 동작 틀기: controller (null = 기본) 의 동작을 바꿈, stopExpression (null = q.any_animation_finished, "0" = 다음 동작까지 유지) */
    public void playAnimation(String animation, String controller, String stopExpression) {
        if (level().isClientSide() || animation == null) return;
        anims.put(controller == null ? RUNTIME : controller, new Object[] {animation, level().getGameTime(), stopExpression == null ? "" : stopExpression});
        syncAnims();
    }

    /** 컨트롤러의 동작을 멈춤 (null = 모두) */
    public void stopAnimation(String controller) {
        if (level().isClientSide()) return;
        if (controller == null) anims.clear();
        else anims.remove(controller);
        syncAnims();
    }

    private void syncAnims() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object[]> e : anims.entrySet()) out.put(e.getKey(), List.of(e.getValue()[0], ((Long) e.getValue()[1]).doubleValue(), e.getValue()[2]));
        entityData.set(ANIMS, Json.stringify(out));
    }

    /** 클라이언트: 동기화된 튼 동작 */
    public List<Playing> syncedAnims() {
        String s = entityData.get(ANIMS);
        if (!s.equals(animsSrc)) {
            animsSrc = s;
            List<Playing> list = new java.util.ArrayList<>();
            if (!s.isEmpty()) {
                try {
                    for (Map.Entry<String, Object> e : Json.parseObject(s).entrySet()) {
                        if (e.getValue() instanceof List<?> l && l.size() >= 3) {
                            list.add(new Playing(e.getKey(), String.valueOf(l.get(0)), ((Number) l.get(1)).longValue(), String.valueOf(l.get(2))));
                        }
                    }
                } catch (RuntimeException ignored) {
                    // 잘못된 글자면 없음
                }
            }
            animsSeen = List.copyOf(list);
        }
        return animsSeen;
    }

    // ------------------------------------------------------------ 사건 (컴포넌트 묶음)

    /** 베드락 사건 (rpg:hold · rpg:release · rpg:release_fast · minecraft:entity_spawned): 묶음을 더하고 빼서 이동 빠르기를 다시 정함 */
    public void triggerEvent(String event) {
        List<List<String>> ev = def.events().get(event);
        if (ev == null) return;
        groups.removeAll(ev.get(1));
        groups.addAll(ev.get(0));
        double move = def.movement();
        for (String g : groups) {
            Double m = def.groupMovement().get(g);
            if (m != null) move = m;
        }
        AttributeInstance a = getAttribute(Attributes.MOVEMENT_SPEED);
        if (a != null) a.setBaseValue(move);
    }

    /** 지금 들어 있는 묶음 */
    public Set<String> groups() {
        return Collections.unmodifiableSet(groups);
    }

    // ------------------------------------------------------------ 이름표 · 보스 막대

    /** 이름표 (베드락 서식 글자 — 자바에서는 한 줄. 여러 줄 이름표는 글자 표시 엔티티로) */
    public void setNameTag(String text) {
        if (text == null || text.isEmpty()) {
            setCustomName(null);
            setCustomNameVisible(false);
            return;
        }
        setCustomName(Component.literal(BedrockText.toJava(text)));
        setCustomNameVisible(def.nameAlways());
    }

    /** 보스 막대 (minecraft:boss 가 있는 종류만, 없으면 null) — 서버 */
    public ServerBossEvent bossBar() {
        if (bossBar == null && def.bossName() != null && !level().isClientSide()) {
            bossBar = new ServerBossEvent(UUID.randomUUID(), Component.literal(BedrockText.toJava(def.bossName())), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.PROGRESS);
        }
        return bossBar;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) {
            // 집 (minecraft:home): 처음 놓인 자리 둘레 restriction_radius 칸
            if (def.homeRadius() > 0 && !hasHome()) setHomeTo(blockPosition(), (int) Math.round(def.homeRadius()));
            if (def.bossName() != null) tickBossBar(sl);
        }
    }

    /** 목표 (BedrockGoals · 보스 엔진이 더하고 뺌) */
    public GoalSelector goals() {
        return goalSelector;
    }

    /** 대상 고르기 목표 */
    public GoalSelector targets() {
        return targetSelector;
    }

    /** 바닐라 근접 피해는 없음 (몬스터 · 보스의 공격은 스크립트가 — 애드온 combat.js 가 바닐라 근접을 막은 것과 같음) */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        return false;
    }

    /** 보스 막대: 체력 비율, hud_range 안 (같은 차원) 플레이어에게만 */
    private void tickBossBar(ServerLevel level) {
        ServerBossEvent bar = bossBar();
        if (bar == null) return;
        bar.setProgress(Math.max(0F, Math.min(1F, getHealth() / Math.max(1F, getMaxHealth()))));
        if (tickCount % 10 != 0) return;
        double r = def.bossRange() > 0 ? def.bossRange() : 40;
        for (ServerPlayer p : level.players()) {
            boolean near = p.distanceToSqr(this) <= r * r && isAlive();
            if (near && !bar.getPlayers().contains(p)) bar.addPlayer(p);
            else if (!near && bar.getPlayers().contains(p)) bar.removePlayer(p);
        }
        for (ServerPlayer p : List.copyOf(bar.getPlayers())) if (p.level() != level || p.isRemoved()) bar.removePlayer(p);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (bossBar != null) bossBar.removeAllPlayers();
        super.remove(reason);
    }

    // ------------------------------------------------------------ 수치 (행동 팩)

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        if (def.noDamage().contains("fall") || def.noDamage().contains("all")) return false;
        return super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Set<String> no = def.noDamage();
            if (no.contains("all")) return true;
            if (no.contains("fall") && source.is(DamageTypes.FALL)) return true;
            if (no.contains("suffocation") && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING))) return true;
            if (no.contains("drowning") && source.is(DamageTypes.DROWN)) return true;
            if (no.contains("entity_explosion") && source.is(DamageTypeTags.IS_EXPLOSION)) return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        String name = effect.getEffect().getRegisteredName();
        int colon = name.indexOf(':');
        if (def.effectImmune().contains(colon >= 0 ? name.substring(colon + 1) : name)) return false;
        return super.canBeAffected(effect);
    }

    @Override
    public boolean canBreatheUnderwater() {
        return def.breathesWater() || super.canBreatheUnderwater();
    }

    @Override
    public boolean isPushable() {
        return def.pushable() && super.isPushable();
    }

    /** 맞을 수 없는 것 (보스 글자 · 업화 막대) 은 조준에도 잡히지 않음 */
    @Override
    public boolean isPickable() {
        return !def.noDamage().contains("all") && super.isPickable();
    }

    /** 그리는 거리: 작은 충돌 상자에 큰 모델 (요르문간드의 고리 · 보스 글자) 도 멀리서 보이게 (최소 4칸 크기로 셈) */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = Math.max(4.0, getBoundingBox().getSize()) * 64.0 * getViewScale();
        return distance < d * d;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return 0;
    }
}
