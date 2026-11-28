package mmorpg.fabric.field;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.fabric.job.CommonSkills;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 벼락받이 사당 (2026-11-28 — 세계관 2-6 "계단식 논 꼭대기, 볏짚 금줄을 두른 큰 기둥에 밤마다 벼락이 떨어진다"): 자리표 bolt (core FieldNations — 기둥 꼭대기
 * 피뢰침 위) 둘레 NEAR 칸 안에 사람이 있으면 밤에 GAP_MIN ~ GAP_MAX 틱마다 하늘에서 기둥으로 번개 (그림 · 소리만 — 불 · 피해 없음, CommonSkills.shrineBolt).
 * 밤 = 하루 시각 13000 ~ 23000 (시각을 읽을 수 없는 판이면 늘 — 반사로 읽음).
 */
public final class FieldShrines {
    private FieldShrines() {}

    static final double NEAR = 96;
    static final int GAP_MIN = 240;
    static final int GAP_MAX = 600;
    /** 기둥 받침 (벼락 자리에서 아래로) — 받침 둘레 번개 고리 */
    static final double PILLAR = 30;

    private static volatile List<Vec3> bolts;
    private static long next;
    private static int clock;

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FieldShrines::tick);
    }

    /** 벼락 자리 모두 */
    public static List<Vec3> bolts() {
        List<Vec3> b = bolts;
        if (b == null) {
            synchronized (FieldShrines.class) {
                b = bolts;
                if (b == null) {
                    List<Vec3> out = new ArrayList<>();
                    for (Blueprint.Mark m : FieldBuild.build(FieldWorld.terrain()).marks()) if (m.id().equals("bolt")) out.add(new Vec3(m.x(), m.y(), m.z()));
                    bolts = b = List.copyOf(out);
                }
            }
        }
        return b;
    }

    private static void tick(MinecraftServer server) {
        if (++clock % 20 != 0) return;
        ServerLevel level = server.overworld();
        if (!FieldWorld.isField(level) || bolts().isEmpty() || !night(level)) return;
        long now = level.getGameTime();
        if (now < next) return;
        boolean any = false;
        for (Vec3 at : bolts()) {
            for (ServerPlayer sp : level.players()) {
                if (sp.position().distanceToSqr(at) > NEAR * NEAR) continue;
                strike(level, at);
                any = true;
                break;
            }
        }
        if (any) next = now + GAP_MIN + (long) (Math.random() * (GAP_MAX - GAP_MIN));
    }

    /** 벼락 하나 (그림 · 소리) */
    public static void strike(ServerLevel level, Vec3 at) {
        CommonSkills.shrineBolt(level, at, 48, new Vec3(at.x, at.y - PILLAR + 3, at.z));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 0.6, 0.6, 0.6, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 24, 0.2, 0.2, 0.2, 0.25);
        SkillFx.play(level, "entity.lightning_bolt.thunder", at, 0.9 + Math.random() * 0.2, 6.0);
        SkillFx.play(level, "entity.lightning_bolt.impact", at, 1.0, 2.0);
    }

    /** 밤인지 (하루 시각 13000 ~ 23000) — 판마다 이름이 달라질 수 있어 반사로 읽고, 못 읽으면 늘 밤 */
    static boolean night(ServerLevel level) {
        try {
            Object v = level.getClass().getMethod("getDayTime").invoke(level);
            long t = ((Number) v).longValue() % 24000L;
            return t >= 13000 && t <= 23000;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }
}
