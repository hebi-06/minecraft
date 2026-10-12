package mmorpg.fabric.mob;

import mmorpg.fabric.BedrockText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 일반 몬스터 (rpg:scarecrow · gulon · skeljaskrimsli · nix · draugr · troll — 행동 팩 그대로, 그리기는 BedrockRenderer).
 * 움직임 · 쫓기는 목표 (BedrockGoals), 공격 · 이름표 · 회복 · 전리품은 monsters.js 의 이식 {@link Monsters} 가 맡음.
 * <ul>
 * <li>무리 (minecraft:type_family — rpg_monster · 키 · mob · undead) 를 엔티티 태그로 (Kit.hasFamily). 드라우그의 언데드는 자바 태그 #minecraft:undead 에도 넣음
 * (data/minecraft/tags/entity_type/undead.json — 성기사 룬 1.5배 · 강타 마법부여)</li>
 * <li>이름표 (애드온 nameTag "Lv.8 굴론\n체력 막대"): 이름 (사망 메시지 · /kill) 은 첫 줄만 (getTypeName — 사용자 이름이 아니라서 죽을 때 서버 기록에 남지 않음),
 * 머리 위 글은 몬스터에 태운 글자 표시 엔티티 ({@link #label}) —
 * 타고 있으니 클라이언트가 몬스터와 같이 옮겨 그림 (늦거나 앞서지 않음). 몸 위 {@link #LABEL_GAP} 칸에 앉음 (getPassengerAttachmentPoint — 서버 · 클라이언트 같은 식)</li>
 * <li>받는 피해 (combat.js 몬스터 훅 incoming): 플레이어가 준 피해는 껍질만큼 줄임 (스켈야스크림슬리 -20%, 평타 · 활 · 완드 · 스킬 · 룬 모두)</li>
 * <li>에메랄드 (행동 팩 loot_tables/entities/rpg_&lt;키&gt;.json — random_chance · set_count): 죽으면 (누가 잡든) MONSTERS.&lt;키&gt;.emerald [확률, 최소, 최대]</li>
 * </ul>
 */
public class RpgMonster extends BedrockMob {
    /** 이름표 글자 표시 엔티티가 앉는 높이 (몸 위 칸 — 바닐라 이름표처럼 머리에서 조금 떠서) */
    public static final double LABEL_GAP = 0.3;

    /** 머리 위 이름표 (글자 표시 엔티티 — 이 몬스터에 탐, 서버에서만) */
    Display.TextDisplay label;
    /** 지금 머리 위 글자 (베드락 서식 — 같으면 다시 보내지 않음) */
    String labelText;
    /** 이름표를 마지막으로 맞춘 체력 · 최대 체력 (바뀌면 다시 맞춤) */
    float labelHp = -1F;
    float labelMax = -1F;
    /** 첫 서버 틱을 지났는지 (무리 태그 · 회복 시작 · 이름표) */
    boolean tracked;
    /** 9단계 웜 유충 (매복): 숨어 있는지 — 속성 (클라이언트도 앎 → 충돌 상자 · 그리기) */
    public static final String BURIED = "rpg:buried";
    /** 숨은 동안의 충돌 상자 키 (모래 둔덕만 — 크기 배율 전) */
    static final float BURIED_HEIGHT = 0.25F;
    /** 싸우지 않고 지낸 첫 틱 (웜 유충이 다시 숨을 때를 셈 — 0 = 지금 싸우는 중이거나 아직 셈 시작 전, 서버) */
    long calmSince;
    /** 이 틱까지 스스로 걷지 못함 (멧돼지 돌진 준비 · 돌진 — 서버) */
    long holdUntil;

    public RpgMonster(EntityType<? extends RpgMonster> type, Level level) {
        super(type, level);
        for (String f : def.families()) addTag(f);
        // 물뭍 (닉스 — navigation.generic can_swim · movement.amphibious · underwater_movement): 물속에서도 쫓아오고 헤엄침
        if (MobData.swims(def.id())) this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.1F, 0.5F, false);
    }

    /** 물뭍 길 찾기 (닉스) — 생성자 안에서 불려서 def 대신 종류로 */
    @Override
    protected PathNavigation createNavigation(Level level) {
        if (MobData.swims(EntityType.getKey(getType()).toString())) return new AmphibiousPathNavigation(this, level);
        return super.createNavigation(level);
    }

    /** 물뭍 몬스터는 물살에 밀리지 않음 (베드락 navigation.generic) */
    @Override
    public boolean isPushedByFluid() {
        return !MobData.swims(def.id()) && super.isPushedByFluid();
    }

    @Override
    protected void registerGoals() {
        BedrockGoals.add(this);
    }

    /** 숨어 있는지 (웜 유충 — 서버 · 클라이언트) */
    public boolean buried() {
        return def != null && def.props().containsKey(BURIED) && propBool(BURIED); // def: 만드는 중 (BedrockMob 생성자 앞) 에 크기를 물어도 되게
    }

    /** 숨은 동안은 모래 둔덕 높이만 (때리면 튀어나옴 — 몸에 부딪히지 않게) */
    @Override
    protected EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        EntityDimensions d = super.getDefaultDimensions(pose);
        return buried() ? EntityDimensions.scalable(d.width(), BURIED_HEIGHT) : d;
    }

    @Override
    protected void onPropsChanged() {
        refreshDimensions();
    }

    /** 숨은 동안 · 돌진 준비 중에는 스스로 걷지 않음 (목표 쪽으로 미는 힘이 0 — 돌진은 Monsters 가 빠르기를 줌) */
    @Override
    public boolean isImmobile() {
        if (super.isImmobile() || buried()) return true;
        return !level().isClientSide() && holdUntil > 0 && mmorpg.fabric.Tasks.now() < holdUntil;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) Monsters.serverTick(this);
    }

    /** 애드온의 nameTag (여러 줄): 첫 줄은 이름 (사망 메시지), 전체는 머리 위 이름표 */
    @Override
    public void setNameTag(String text) {
        Monsters.setLabel(this, text);
    }

    /** 이름 = 이름표 첫 줄 "Lv.8 굴론" (서버 — 사망 메시지에 첫 줄만, 애드온 hp.js 처럼). 클라이언트는 종류 이름 (번역) */
    @Override
    protected Component getTypeName() {
        String t = labelText;
        if (t == null || t.isEmpty()) return super.getTypeName();
        int nl = t.indexOf('\n');
        return Component.literal(BedrockText.toJava(nl < 0 ? t : t.substring(0, nl)));
    }

    /** 플레이어가 준 피해는 껍질만큼 줄여서 (combat.js 의 incoming 훅 — 무기 공식 뒤) */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return super.hurtServer(level, source, Monsters.adjustIncoming(this, source, amount));
    }

    /** 이름표 (글자 표시 엔티티) 는 몸 위 LABEL_GAP 칸에 앉음 — 서버 · 클라이언트가 같은 자리를 셈 */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        if (passenger instanceof Display.TextDisplay) return new Vec3(0, getBbHeight() + LABEL_GAP, 0);
        return super.getPassengerAttachmentPoint(passenger, dimensions, scale);
    }

    /** 에메랄드 (행동 팩 전리품 표 — 몹 전리품 규칙을 따름) */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        Monsters.dropEmerald(this, level);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        // 죽음 · 치움은 이름표도 치움. 청크가 내려가거나 차원을 옮길 때는 이름표가 타고 있는 채로 같이 저장 · 옮겨짐 (다시 불러오면 이어서 씀)
        if (!level().isClientSide() && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) Monsters.dropLabel(this);
    }
}
