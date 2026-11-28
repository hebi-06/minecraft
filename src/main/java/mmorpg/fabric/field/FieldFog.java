package mmorpg.fabric.field;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 바다 끝 안개 (2026-11-28 — 다리 없음, 사용자 "텔포로 이동시킬거여서 다리 없애고 그냥 섬을 멀리 둬도 됨"): 섬 사이는 이동 지점 돌로만 오가게,
 * 가장 가까운 해안에서 fogPush 칸 (field.json fog.push) 밖 바다에 나가면 섬 쪽으로 밀어내고 (탄 배도), fogBack 칸 밖이면 마지막으로 디딘 땅으로 되돌림.
 * 생존 · 모험만 (만들기 · 관전 — 짓는 사람 · 사진 시험은 그대로). 섬끼리 해안 사이가 1,000칸 남짓이라 헤엄 · 배로는 닿지 않음 (FieldTest 가 확인).
 */
public final class FieldFog {
    private FieldFog() {}

    /** 마지막으로 디딘 땅 (사람마다) */
    private static final Map<UUID, Vec3> SAFE = new HashMap<>();
    private static int clock;

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FieldFog::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> SAFE.remove(h.player.getUUID()));
    }

    private static void tick(MinecraftServer server) {
        if (++clock % 10 != 0) return;
        ServerLevel level = server.overworld();
        if (!FieldWorld.isField(level)) return;
        FieldTerrain t = FieldWorld.terrain();
        for (ServerPlayer sp : level.players()) {
            if (sp.isSpectator() || sp.isCreative() || !sp.isAlive()) continue;
            check(sp, level, t);
        }
    }

    /** 한 사람: 땅이면 자리 기억 · 안개 밖이면 밀어냄 · 더 멀면 되돌림. 밀었으면 1, 되돌렸으면 2, 그대로 0 (시험용) */
    public static int check(ServerPlayer sp, ServerLevel level, FieldTerrain t) {
        FieldPlan plan = t.plan();
        double x = sp.getX();
        double z = sp.getZ();
        double off = t.offshore(x, z);
        if (off <= 0) {
            if (sp.onGround()) SAFE.put(sp.getUUID(), sp.position());
            return 0;
        }
        if (off < plan.fogPush) return 0;
        FieldTerrain.Column c = t.sample((int) Math.floor(x), (int) Math.floor(z), new FieldTerrain.Column());
        FieldPlan.Island near = c.island;
        Vec3 home = near == null ? new Vec3(plan.spawnX, 0, plan.spawnZ) : new Vec3(near.x(), 0, near.z());
        Vec3 dir = new Vec3(home.x - x, 0, home.z - z).normalize();
        if (off >= plan.fogBack) {
            Vec3 back = SAFE.get(sp.getUUID());
            if (back == null) {
                BlockPos sp0 = level.getRespawnData().pos();
                back = Vec3.atBottomCenterOf(sp0);
            }
            sp.stopRiding();
            level.getChunk(BlockPos.containing(back));
            sp.teleportTo(level, back.x, back.y, back.z, java.util.Set.of(), sp.getYRot(), sp.getXRot(), true);
            Notify.notify(sp, "§7짙은 안개 속에서 길을 잃었다가 §f땅§7으로 돌아왔습니다. §8섬 사이는 §b이동 지점 돌§8로 오갑니다.", 100);
            SkillFx.playFor(sp, "random.orb", 0.6, 0.6);
            return 2;
        }
        Entity mover = sp.getRootVehicle();
        Vec3 v = dir.scale(0.55).add(0, mover == sp ? 0.08 : 0, 0);
        mover.setDeltaMovement(v);
        Kit.syncVelocity(mover);
        if (mover != sp) Kit.syncVelocity(sp);
        // 앞을 막는 안개 (그 사람에게만): 가는 쪽 6칸 앞에 구름 벽
        Vec3 ahead = sp.position().add(dir.scale(-6));
        level.sendParticles(sp, ParticleTypes.CLOUD, true, false, ahead.x, ahead.y + 1.2, ahead.z, 30, 2.5, 1.2, 2.5, 0.01);
        level.sendParticles(sp, ParticleTypes.WHITE_ASH, true, false, sp.getX(), sp.getY() + 1.5, sp.getZ(), 20, 3, 1.5, 3, 0.01);
        if (Notify.actionbarFree(sp)) Notify.notify(sp, "§7안개가 너무 짙습니다. 더 나아갈 수 없습니다. §8섬 사이는 §b이동 지점 돌§8로 오갑니다.", 40);
        return 1;
    }
}
