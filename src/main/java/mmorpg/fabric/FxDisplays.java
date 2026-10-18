package mmorpg.fabric;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 연출용 표시 엔티티 (0.18.0 — 대지 가르기의 대지의 대검 (item_display) · 들리는 땅 · 바위 기둥 (block_display)).
 * 정해진 틱 뒤 지우고, 서버가 꺼질 때 지우고, 꺼지기 전에 남아 저장된 것은 다시 불러오지 않음 (태그 mmorpg_fx). 충돌 · 피해 없음 (표시 엔티티).
 * 움직임은 변환 (Transformation) 을 클라이언트가 보간 — 띄운 틱에는 처음 모양만 가므로 움직임 ({@link #move}) 은 띄우고 2틱 뒤부터.
 */
public final class FxDisplays {
    private FxDisplays() {}

    public static final String TAG = "mmorpg_fx";
    /** 한 번에 떠 있을 수 있는 수 (넘으면 새 것은 띄우지 않음 — 여럿이 한꺼번에 써도 서버 · 클라이언트가 버티게) */
    private static final int MAX = 600;

    private static final class Held {
        final Display e;
        final long until;

        Held(Display e, long until) {
            this.e = e;
            this.until = until;
        }
    }

    private static final List<Held> ACTIVE = new ArrayList<>();
    /** 시험용: 지금까지 띄운 수 */
    public static int spawned;

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Held h : ACTIVE) if (!h.e.isRemoved()) h.e.discard();
            ACTIVE.clear();
        });
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) -> !(fromDisk && entity instanceof Display && entity.entityTags().contains(TAG)));
    }

    /** 지금 떠 있는 수 (시험용) */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 아이템 모델 하나 (at = 모델 가운데 (8, 8, 8) 자리 · 변환 t · life 틱 뒤 지움 · light = 밝기 덮기 (null 이면 그 자리 밝기)) */
    public static Display.ItemDisplay item(ServerLevel level, Vec3 at, ItemStack stack, Transformation t, int life, Brightness light) {
        if (ACTIVE.size() >= MAX) return null;
        Display.ItemDisplay d = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (d == null) return null;
        d.setItemStack(stack);
        d.setItemTransform(ItemDisplayContext.NONE);
        return add(level, d, at, t, life, light) ? d : null;
    }

    /** 블록 하나 (at = 블록 모서리 (0, 0, 0) 자리 · 변환 t) */
    public static Display.BlockDisplay block(ServerLevel level, Vec3 at, BlockState state, Transformation t, int life, Brightness light) {
        if (ACTIVE.size() >= MAX) return null;
        Display.BlockDisplay d = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (d == null) return null;
        d.setBlockState(state);
        return add(level, d, at, t, life, light) ? d : null;
    }

    private static boolean add(ServerLevel level, Display d, Vec3 at, Transformation t, int life, Brightness light) {
        d.setTransformation(t);
        if (light != null) d.setBrightnessOverride(light);
        d.setViewRange(2.0F);
        d.snapTo(at.x, at.y, at.z, 0F, 0F);
        d.addTag(TAG);
        Held h = new Held(d, Tasks.now() + Math.max(1, life));
        ACTIVE.add(h);
        if (!level.addFreshEntity(d)) {
            ACTIVE.remove(h);
            return false;
        }
        spawned++;
        return true;
    }

    /** ticks 동안 변환 t 로 (클라이언트 보간 — 0 이면 바로) */
    public static void move(Display d, Transformation t, int ticks) {
        if (d == null || d.isRemoved()) return;
        d.setTransformationInterpolationDelay(0);
        d.setTransformationInterpolationDuration(Math.max(0, ticks));
        d.setTransformation(t);
    }

    public static void remove(Display d) {
        if (d != null && !d.isRemoved()) d.discard();
    }

    private static void tick() {
        if (ACTIVE.isEmpty()) return;
        long now = Tasks.now();
        for (Iterator<Held> it = ACTIVE.iterator(); it.hasNext(); ) {
            Held h = it.next();
            if (h.e.isRemoved() || now >= h.until) {
                if (!h.e.isRemoved()) h.e.discard();
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------ 변환 도움

    /** 모델 축 (x · y · z) 을 세상의 ex · ey · ez (서로 직교하는 단위 벡터, 오른손 — ex = ey × ez) 로 보내는 회전 */
    public static Quaternionf basis(Vec3 ex, Vec3 ey, Vec3 ez) {
        Matrix3f m = new Matrix3f((float) ex.x, (float) ex.y, (float) ex.z, (float) ey.x, (float) ey.y, (float) ey.z, (float) ez.x, (float) ez.y, (float) ez.z);
        return new Quaternionf().setFromNormalized(m);
    }

    /** 옮김 (세상 축 — 엔티티 자리에서) · 회전 · 크기 (x · y · z) */
    public static Transformation of(Vec3 translation, Quaternionf rot, double sx, double sy, double sz) {
        return new Transformation(new Vector3f((float) translation.x, (float) translation.y, (float) translation.z), rot,
                new Vector3f((float) sx, (float) sy, (float) sz), new Quaternionf());
    }

    public static Transformation of(Vec3 translation, Quaternionf rot, double scale) {
        return of(translation, rot, scale, scale, scale);
    }

    /** 표시 엔티티의 밝기 (블록 빛 · 하늘 빛 0 ~ 15) */
    public static Brightness light(int block, int sky) {
        return new Brightness(block, sky);
    }
}
