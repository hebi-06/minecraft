package mmorpg.fabric;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * 2026-11-26 사용자 "마크 파티클은 최소한으로" · "피격시 나오는 이펙트를 지워" (평타만): 플레이어 근접 공격 (바닐라 Player.attack) 이 내는 바닐라 파티클 — 피해 하트
 * (damage_indicator — 깎인 체력 반 칸마다 하나라 RPG 피해면 한 번에 수십 개, 3인칭 영상의 맞은 적 위 검붉은 점) · 휩쓸기 (sweep_attack) · 치명타 별 (crit ·
 * 떨어지며 친 바닐라 치명타) · 마법 치명타 (enchanted_hit) 를 그 공격 동안 내지 않음. PlayerMixin 이 attack 처음 · 끝에 begin · end, ServerLevelParticleMixin ·
 * ServerPlayerCritMixin 이 막음. 같은 틱 안에서만 (틱이 바뀌면 풀림 — 예외로 end 를 못 불러도 다음 틱에는 막지 않음)
 */
public final class AttackMute {
    private AttackMute() {}

    private static int depth;
    private static long tick = Long.MIN_VALUE;
    /** 시험: 막은 파티클 · 치명타 알림 수 */
    public static int muted;

    public static void begin() {
        long now = Tasks.now();
        if (tick != now) {
            tick = now;
            depth = 0;
        }
        depth++;
    }

    public static void end() {
        if (depth > 0) depth--;
    }

    /** 지금 플레이어 근접 공격 안인지 */
    public static boolean active() {
        return depth > 0 && tick == Tasks.now();
    }

    /** 이 바닐라 파티클을 막는지 (근접 공격 안에서 피해 하트 · 휩쓸기 · 치명타 별 · 마법 치명타) */
    public static boolean blocks(ParticleOptions o) {
        if (o == null || !active()) return false;
        ParticleType<?> t = o.getType();
        if (t == ParticleTypes.DAMAGE_INDICATOR || t == ParticleTypes.SWEEP_ATTACK || t == ParticleTypes.CRIT || t == ParticleTypes.ENCHANTED_HIT) {
            muted++;
            return true;
        }
        return false;
    }

    /** 바닐라 치명타 · 마법 치명타 알림 (받는 클라이언트가 별 파티클을 뿌림) 을 막는지 */
    public static boolean blocksCrit() {
        if (!active()) return false;
        muted++;
        return true;
    }

    /** 시험: 보내는 패킷에서 막은 수 (ServerLevel.sendParticles 에서 못 막은 것 — 판마다 바닐라가 부르는 쪽이 달라도) */
    public static int mutedPackets;
    private static java.lang.reflect.Method particleOf;
    private static boolean particleOfTried;

    /**
     * 보내는 패킷 (VanillaSendMixin — 한 사람에게): 근접 공격 안의 바닐라 파티클 패킷 (피해 하트 · 휩쓸기 · 치명타 별 · 마법 치명타) · 치명타 알림이면 막음.
     * 파티클 패킷의 파티클은 그 형을 돌려주는 인자 없는 메서드로 (이름이 판 · 매핑마다 달라도)
     */
    public static boolean blocksPacket(net.minecraft.network.protocol.Packet<?> p) {
        if (p == null || !active()) return false;
        if (p instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket) {
            if (blocks(particleOf(p))) {
                mutedPackets++;
                return true;
            }
            return false;
        }
        if (p instanceof net.minecraft.network.protocol.game.ClientboundAnimatePacket a) {
            int k = a.getAction();
            if (k == net.minecraft.network.protocol.game.ClientboundAnimatePacket.CRITICAL_HIT || k == net.minecraft.network.protocol.game.ClientboundAnimatePacket.MAGIC_CRITICAL_HIT) {
                muted++;
                mutedPackets++;
                return true;
            }
        }
        return false;
    }

    private static ParticleOptions particleOf(Object packet) {
        try {
            if (!particleOfTried) {
                particleOfTried = true;
                for (java.lang.reflect.Method m : packet.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && ParticleOptions.class.isAssignableFrom(m.getReturnType())) {
                        particleOf = m;
                        break;
                    }
                }
            }
            Object o = particleOf == null ? null : particleOf.invoke(packet);
            return o instanceof ParticleOptions po ? po : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
