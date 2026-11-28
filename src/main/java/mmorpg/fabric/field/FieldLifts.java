package mmorpg.fabric.field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 오르내림 발판 (2026-11-28 — 바위 나라 섬 큰 저울: 받침점 탑 아래 현자 회의장 ↔ 탑 꼭대기 둘레 마루): 자리표 "lift_&lt;이름&gt;_a" · "lift_&lt;이름&gt;_b" 가 한 쌍
 * (core FieldNations). 발판 (금 누름판) 위에 1초 서 있으면 빛 기둥이 차오르다 짝 발판으로 옮김. 옮겨 온 사람은 발판에서 내려섰다가 다시 서야 돌아감.
 * 서버만으로 동작 (입자 · 소리 — 모드 없는 클라이언트도 보임).
 */
public final class FieldLifts {
    private FieldLifts() {}

    /** 발판 하나: 자리표 id · 서는 자리 · 짝 id */
    public record Pad(String id, Vec3 at, String pair) {}

    /** 발판 위에 서 있어야 하는 틱 · 발판 반지름 (칸) */
    static final int HOLD_TICKS = 20;
    static final double R = 0.85;

    private static volatile List<Pad> pads;
    private static final Map<UUID, Integer> HOLD = new HashMap<>();
    private static final Map<UUID, String> ARRIVED = new HashMap<>();
    private static int clock;

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FieldLifts::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            HOLD.remove(h.player.getUUID());
            ARRIVED.remove(h.player.getUUID());
        });
    }

    /** 발판 모두 (짝이 있는 것만) */
    public static List<Pad> pads() {
        List<Pad> p = pads;
        if (p == null) {
            synchronized (FieldLifts.class) {
                p = pads;
                if (p == null) {
                    Map<String, Vec3> at = new HashMap<>();
                    for (Blueprint.Mark m : FieldBuild.build(FieldWorld.terrain()).marks()) {
                        if (m.id().startsWith("lift_")) at.put(m.id(), new Vec3(m.x(), m.y(), m.z()));
                    }
                    List<Pad> out = new ArrayList<>();
                    for (Map.Entry<String, Vec3> e : at.entrySet()) {
                        String id = e.getKey();
                        String pair = id.endsWith("_a") ? id.substring(0, id.length() - 2) + "_b" : id.endsWith("_b") ? id.substring(0, id.length() - 2) + "_a" : null;
                        if (pair != null && at.containsKey(pair)) out.add(new Pad(id, e.getValue(), pair));
                    }
                    out.sort(java.util.Comparator.comparing(Pad::id));
                    pads = p = List.copyOf(out);
                }
            }
        }
        return p;
    }

    static Pad pad(String id) {
        for (Pad p : pads()) if (p.id().equals(id)) return p;
        return null;
    }

    /** 이 자리가 발판 위인지 (가로 R 칸 · 높이 1칸 안) */
    static boolean on(Vec3 pos, Pad p) {
        double dx = pos.x - p.at().x;
        double dz = pos.z - p.at().z;
        return dx * dx + dz * dz <= R * R && pos.y >= p.at().y - 0.2 && pos.y <= p.at().y + 1.0;
    }

    private static void tick(MinecraftServer server) {
        if (++clock % 2 != 0) return;
        ServerLevel level = server.overworld();
        if (!FieldWorld.isField(level)) return;
        List<Pad> ps = pads();
        if (ps.isEmpty()) return;
        for (ServerPlayer sp : level.players()) {
            if (sp.isSpectator() || !sp.isAlive()) continue;
            step(sp, level, 2);
        }
    }

    /** 한 사람 (dt 틱 지남): 발판 위면 모으고 다 차면 옮김. 옮겼으면 그 짝 발판 id (시험용), 아니면 null */
    public static String step(ServerPlayer sp, ServerLevel level, int dt) {
        Pad at = null;
        for (Pad p : pads()) if (on(sp.position(), p)) at = p;
        UUID id = sp.getUUID();
        if (at == null) {
            HOLD.remove(id);
            ARRIVED.remove(id);
            return null;
        }
        if (at.id().equals(ARRIVED.get(id))) return null;
        int t = HOLD.merge(id, dt, Integer::sum);
        charge(level, at.at(), t);
        if (t < HOLD_TICKS) return null;
        HOLD.remove(id);
        Pad to = pad(at.pair());
        if (to == null) return null;
        go(sp, level, at, to);
        return to.id();
    }

    /** 모으는 동안: 발판 둘레에서 빛이 차오름 */
    static void charge(ServerLevel level, Vec3 at, int t) {
        double k = Math.min(1, t / (double) HOLD_TICKS);
        for (int i = 0; i < 6; i++) {
            double a = (t * 0.35 + i / 6.0) * Math.PI * 2;
            level.sendParticles(ParticleTypes.END_ROD, at.x + Math.cos(a) * 0.9, at.y + 0.1 + k * 2.0, at.z + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
        }
        if (t % 6 == 0) SkillFx.play(level, "block.amethyst_block.chime", at, 0.8 + k * 0.8, 0.6);
    }

    static void go(ServerPlayer sp, ServerLevel level, Pad from, Pad to) {
        Vec3 a = from.at();
        Vec3 b = to.at();
        boolean up = b.y > a.y;
        for (int i = 0; i < 24; i++) {
            double h = i * 0.25;
            level.sendParticles(ParticleTypes.END_ROD, a.x, a.y + h, a.z, 2, 0.25, 0.05, 0.25, 0.01);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, a.x, a.y + 1, a.z, 30, 0.4, 0.8, 0.4, 0.1);
        SkillFx.play(level, "block.beacon.activate", a, 1.6, 0.9);
        level.getChunk(BlockPos.containing(b));
        sp.stopRiding();
        sp.teleportTo(level, b.x, b.y + 0.1, b.z, java.util.Set.of(), sp.getYRot(), sp.getXRot(), true);
        ARRIVED.put(sp.getUUID(), to.id());
        for (int i = 0; i < 24; i++) level.sendParticles(ParticleTypes.END_ROD, b.x, b.y + 2.4 - i * 0.1, b.z, 2, 0.3, 0.05, 0.3, 0.01);
        SkillFx.play(level, "block.amethyst_block.resonate", b, 1.2, 1.0);
        Notify.notify(sp, up ? "§6[큰 저울] §f받침점 꼭대기 §7(발판에 다시 서면 내려감)" : "§6[큰 저울] §f현자 회의장", 60);
    }
}
