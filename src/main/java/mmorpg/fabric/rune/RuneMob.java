package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.TyrfingWarrior;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-12 룬 스킬의 3D 몹 (사용자 "스킬에 있는 몹도 지금" — tools/rune_mobs_art.py: 매 · 야수왕 · 운디네 · 실프 · 노움 · 정령왕, 늑대 무리는 늑대 정령
 * rpg:spirit_wolf): 진짜 몹 (TyrfingWarrior — 맞지 않음 · 아무도 노리지 않음 · 스킬 대상이 아님 · 저장하지 않음 · 제 소리 없음) 을 스킬이 틱마다 옮김.
 * <ul>
 * <li>옮기기 (moveTo): 지금 자리에서 끝 자리까지 ticks 틱 (곧게 · 포물선 arc 칸, 빠르기 곡선 ease) — 끝 자리는 움직이는 적을 따라갈 수 있음 (Supplier).
 *     스킬이 옮기는 몹 (scripted) 은 물리 · 중력을 끔 (날거나 뛰어오르는 동안 떨어지지 않게)</li>
 * <li>동작: 그 몹의 동작 (animation.rpg.monster.&lt;키&gt;.&lt;이름&gt; — 모드 클라이언트 BedrockRenderer · 모드 없는 클라이언트 VanillaModels 가 같은 계산)</li>
 * <li>떠남 (leave): 흩어지는 동작 뒤 after 틱에 지움 (스킬이 치우지 못해도 maxAge 틱 뒤 스스로 사라짐)</li>
 * </ul>
 */
final class RuneMob {
    static final String HAWK = "rpg:rune_hawk";
    static final String BEAST_KING = "rpg:rune_beast_king";
    static final String UNDINE = "rpg:rune_undine";
    static final String SYLPH = "rpg:rune_sylph";
    static final String GNOME = "rpg:rune_gnome";
    static final String SPIRIT_KING = "rpg:rune_spirit_king";
    static final String WOLF = TyrfingWarrior.WOLF_ID;

    /** 빠르기 곡선: 고르게 · 빨리 시작해 느려짐 · 느리게 시작해 빨라짐 · 부드럽게 */
    static final int LINEAR = 0;
    static final int OUT = 1;
    static final int IN = 2;
    static final int SMOOTH = 3;

    final TyrfingWarrior x;
    /** 동작 이름의 키 (rune_hawk · spirit_wolf …) */
    final String key;
    private Move move;
    /** 서 있을 때 볼 쪽 (null = 그대로) */
    private Vec3 face;
    private boolean leaving;

    private record Move(Vec3 from, Supplier<Vec3> to, long t0, int ticks, double arc, int ease, boolean faceMove) {}

    private static final List<RuneMob> LIVE = new ArrayList<>();

    private RuneMob(TyrfingWarrior x, String key) {
        this.x = x;
        this.key = key;
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> mmorpg.fabric.vfx.PBridge.pixel(RuneMob::tick)); // 2026-11-12 룬의 틱 그림도 픽셀 글꼴
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIVE.clear());
    }

    /**
     * id 몹 하나를 at (발) 에 face 쪽을 보게 세움 — life 틱 뒤 스스로 사라짐, 크기 scale (1 = 모델 그대로), scripted = 스킬이 옮김 (물리 · 중력 끔).
     * 못 세우면 null
     */
    static RuneMob spawn(String id, ServerLevel level, Vec3 at, Vec3 face, int life, double scale, boolean scripted) {
        TyrfingWarrior x = TyrfingWarrior.spawnAs(id, level, at, yawOf(face), life);
        if (x == null) return null;
        if (scale != 1) {
            var a = x.getAttribute(Attributes.SCALE);
            if (a != null) a.setBaseValue(scale);
            x.refreshDimensions();
        }
        RuneMob m = new RuneMob(x, id.substring(id.indexOf(':') + 1));
        m.scripted(scripted);
        m.face = face;
        LIVE.add(m);
        return m;
    }

    /** 자바 몸 각도 (face 쪽을 봄) */
    static float yawOf(Vec3 face) {
        return (float) Math.toDegrees(Math.atan2(-face.x, face.z));
    }

    boolean alive() {
        return !x.isRemoved() && !leaving;
    }

    Vec3 pos() {
        return x.position();
    }

    /** 크기 (Attributes.SCALE — 모델 몇 배) */
    double scale() {
        return x.getScale();
    }

    /** 몸 앞 쪽 (보는 쪽, 수평) */
    Vec3 forward() {
        double yaw = Math.toRadians(x.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** 스킬이 옮김 (물리 · 중력 끔) / 스스로 걸음 */
    void scripted(boolean on) {
        x.noPhysics = on;
        x.setNoGravity(on);
        if (on) x.getNavigation().stop();
    }

    /** 이 몹의 동작 (animation.rpg.monster.키.이름) — 끝나면 늘 트는 동작으로 */
    void anim(String name) {
        x.playAnimation("animation.rpg.monster." + key + "." + name);
    }

    /** 끝 자세를 다음 동작까지 그대로 (일어선 자세 · 든 팔) */
    void hold(String name) {
        x.playAnimation("animation.rpg.monster." + key + "." + name, null, "0");
    }

    /** 다른 몹의 동작 이름 그대로 (늑대 정령의 서리 늑대 동작) */
    void play(String fullName) {
        x.playAnimation(fullName);
    }

    /** dir 쪽을 봄 (수평) */
    void face(Vec3 dir) {
        if (dir == null || Math.hypot(dir.x, dir.z) < 1e-4) return;
        face = dir;
        float yaw = yawOf(dir);
        x.setYRot(yaw);
        x.setYBodyRot(yaw);
        x.setYHeadRot(yaw);
    }

    /** 바로 옮김 */
    void place(Vec3 p) {
        move = null;
        x.setPos(p.x, p.y, p.z);
        x.setDeltaMovement(Vec3.ZERO);
    }

    /** 지금 자리에서 to 까지 ticks 틱 (arc = 가운데에서 솟는 칸, faceMove = 가는 쪽을 봄) */
    void moveTo(Vec3 to, int ticks, double arc, int ease, boolean faceMove) {
        moveTo(() -> to, ticks, arc, ease, faceMove);
    }

    void moveTo(Supplier<Vec3> to, int ticks, double arc, int ease, boolean faceMove) {
        move = new Move(x.position(), to, Tasks.now(), Math.max(1, ticks), arc, ease, faceMove);
    }

    boolean moving() {
        return move != null;
    }

    /** 떠남: 동작 (없으면 null) 뒤 after 틱에 지움 */
    void leave(String anim, int after) {
        if (x.isRemoved() || leaving) return;
        leaving = true;
        if (anim != null) anim(anim);
        Tasks.later(Math.max(1, after), x::discard);
    }

    /** 바로 지움 */
    void discard() {
        leaving = true;
        x.discard();
    }

    static double ease(int kind, double u) {
        return switch (kind) {
            case OUT -> 1 - (1 - u) * (1 - u);
            case IN -> u * u;
            case SMOOTH -> u * u * (3 - 2 * u);
            default -> u;
        };
    }

    private static void tick() {
        if (LIVE.isEmpty()) return;
        long now = Tasks.now();
        for (int i = LIVE.size() - 1; i >= 0; i--) {
            RuneMob m = LIVE.get(i);
            if (m.x.isRemoved()) {
                LIVE.remove(i);
                continue;
            }
            Move mv = m.move;
            if (mv == null) {
                if (m.face != null && m.x.noPhysics) m.face(m.face); // 스킬이 옮기는 몹: 볼 쪽을 지킴
                continue;
            }
            double u = Math.min(1, Math.max(0, (now - mv.t0()) / (double) mv.ticks()));
            Vec3 to = mv.to().get();
            Vec3 p = V.add(V.lerp(mv.from(), to, ease(mv.ease(), u)), V.UP, mv.arc() * 4 * u * (1 - u));
            Vec3 old = m.x.position();
            m.x.setPos(p.x, p.y, p.z);
            m.x.setDeltaMovement(Vec3.ZERO);
            if (mv.faceMove()) {
                Vec3 d = V.sub(u < 1 ? to : p, old);
                if (Math.hypot(d.x, d.z) > 1e-3) m.face(d);
            } else if (m.face != null) m.face(m.face);
            if (u >= 1) m.move = null;
        }
    }
}
