package mmorpg.fabric.boss;

import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 보스 모듈 (애드온 boss_*.js 의 export default 객체): 엔진 ({@link Bosses}) 이 부르는 훅. 모두 기본은 아무것도 안 함 (애드온의 mod.x?.() 와 같음).
 * 이름 · 순서 · 뜻은 애드온 그대로 — 모듈을 옮길 때 같은 이름의 메서드를 덮어씀.
 * <ul>
 * <li>싸움 전체: start(enc) · tick(enc, now, list) · unitDied(enc, u) · end(enc) · propHit · propDied · dying · fogCleared · bonusLoot · killNote</li>
 * <li>마리마다: pick(u, now, list) · hurt(u, p, amount, h) · afterHurt(u, p, amount, left) · farOk(u, now) · farFrom(u, style) · farStyle(u)</li>
 * </ul>
 * 한 마리 보스는 싸움 자신이 유일한 마리 (enc.units = [enc]) 라 u 를 {@code (Bosses.Enc) u} 또는 {@code u.enc} 로 써도 됨.
 * 등록: {@link Bosses#MODULES} 에 한 줄 (new BossKraken() …).
 */
public interface BossModule {
    /** 보스 키 (config BOSSES 의 키 — 애드온 모듈의 key) */
    String key();

    /** 이 보스의 소품 엔티티 ID (남은 것 정리 — 세계를 다시 불러오면 싸움 없는 것은 지움) */
    default List<String> propIds() {
        return List.of();
    }

    /** 보스가 나온 뒤 한 번: 싸움의 기억을 새로 만듦 ({@code enc.mem = new M(...)} — 엔진이 마리들에게도 같은 것을 줌) */
    default void start(Bosses.Enc enc) {}

    /** 매 틱 (싸우는 중, 원거리 견제 · 평타 · 패턴 고르기 전): list = 싸우는 사람 (필드 + combatMargin, 대상에서 빼기 전) */
    default void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {}

    /** 패턴 고르기 (그 마리에게 틀이 없고 비틀거리지 않을 때): 시작했으면 true (아니면 엔진의 평타). list = 대상 (targetable 을 거친 것) */
    default boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        return false;
    }

    /**
     * 받는 피해 바꾸기 (플레이어마다 무적 시간 · 근접 보너스 뒤): null = 그대로, {@link Bosses.Hurt#of(double)} = 수,
     * {@link Bosses.Hurt#of(double, String)} = 수 + 피해 숫자 앞 표시 (약점 · 빈틈 — 근접 보너스 » 앞에). h = 맞은 기록 (h.near · h.skill …)
     */
    default Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        return null;
    }

    /** 맞은 뒤 (쓰러지지 않았을 때): amount = 들어간 피해, left = 남은 체력 */
    default void afterHurt(Bosses.Unit u, ServerPlayer p, double amount, double left) {}

    /** 여러 마리 싸움에서 한 마리가 쓰러짐 (모두 쓰러지면 부르지 않고 보상) */
    default void unitDied(Bosses.Enc enc, Bosses.Unit u) {}

    /** 싸움이 끝남 (처치 · 초기화 · 오류 — 엔진이 소품을 치우기 전): 감옥 · 안개 · 블록 되돌리기 등 */
    default void end(Bosses.Enc enc) {}

    /**
     * 소품이 맞음: 그냥 소품 (분신 · 폭탄) 은 left = NaN (amount = 받은 피해 그대로), 맞는 소품 (addProp 의 hp) 은 엔진이 체력을 줄인 뒤 남은 체력
     */
    default void propHit(Bosses.Enc enc, Entity prop, ServerPlayer p, double amount, double left) {}

    /** 맞는 소품의 체력이 0 (치우는 것은 모듈이 dropProp) */
    default void propDied(Bosses.Enc enc, Entity prop, ServerPlayer p) {}

    /** 이 사람을 평타 · 패턴의 대상으로 삼을지 (false = 삼켜진 사람 등 — 평타에 맞지도 않음) */
    default boolean targetable(Bosses.Enc enc, ServerPlayer p) {
        return true;
    }

    /** 원거리 견제를 해도 되는지 (false = 큰 패턴 동안 쉼 — 쉰 뒤 FAR_GRACE 틱 더 쉼) */
    default boolean farOk(Bosses.Unit u, long now) {
        return true;
    }

    /** 원거리 견제를 던지는 곳 (null = 몸 높이의 3/4). style = 이번 모양 (farStyle 을 덮어쓴 설정 far) */
    default Vec3 farFrom(Bosses.Unit u, Obj style) {
        return null;
    }

    /** 이번 원거리 견제의 모양 (설정 far 에 덮어쓸 것 — 이름 · look · 효과 · 불, null = 그대로). BossKit.obj("name", …) 로 만듦 */
    default Obj farStyle(Bosses.Unit u) {
        return null;
    }

    /** 쓰러질 때 (보상 · 연출 전 — 쓰러지는 자리를 정리: 날던 보스를 땅에) */
    default void dying(Bosses.Enc enc) {}

    /** 엔진이 이 사람의 보스 안개를 걷었음 (다시 들어옴 — 필드 둘레면 모듈이 다시 씌우게 mem.fog 에서 지움) */
    default void fogCleared(Bosses.Enc enc, ServerPlayer p) {}

    /** 보상에 더할 것 (재료 키 → 개수 — 굴린 수에 더함, null = 없음) */
    default Map<String, Double> bonusLoot(Bosses.Enc enc) {
        return null;
    }

    /** 초월 보스: 처치 뒤 딜 순위 아래에 남길 줄 (null = 없음) */
    default List<String> killNote(Bosses.Enc enc) {
        return null;
    }
}
