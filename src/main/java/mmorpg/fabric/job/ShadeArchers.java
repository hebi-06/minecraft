package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneMob;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-14 그림자 사수의 3D 그림자 분신 (rpg:job_shade_archer — tools/job_mobs_art.py: 두건을 쓴 보랏빛 그림자 궁수 · 아래는 그림자 연기, 왼손에 검보라 활 ·
 * 보랏빛 시위 · 그림자 화살). 동작: appear (연기에서 솟음 0.3초) · aim (당김 0.3초 — 끝 자세를 잡음) · shoot (놓음) · burst (재빨리 당겨 BURST_AT 틱에 놓음 — 갈래 화살 ·
 * 섀도 볼리) · aim_up · burst_up (하늘로) · dive (몸을 숙여 아래를 겨눔 — 섀도 스위치) · vanish (연기로 꺼짐). 맞지 않음 · 저장 안 함 (TyrfingWarrior.JOB_MOBS) —
 * 못 세우면 예전 2D 그림자 분신 (rfx_shade)
 */
final class ShadeArchers {
    private ShadeArchers() {}

    static final String ID = "rpg:job_shade_archer";
    /** burst · burst_up 이 시위를 놓는 때 (동작을 튼 뒤 틱) · aim 이 다 당기는 때 */
    static final int BURST_AT = 2;
    static final int AIM_TICKS = 6;
    /** 그림자 (2D 분신 · 연기의 어둠) */
    static final double[] DARK = {0.14, 0.08, 0.24};

    /** 분신 하나를 feet 에 face 쪽을 보게 세움 (연기에서 솟음 · 발밑 그림자 연기) — 못 세우면 2D 그림자 분신 (그때는 null) */
    static RuneMob spawn(ServerPlayer sp, Vec3 feet, Vec3 face, int life, double[] glow) {
        ServerLevel level = sp.level();
        RuneMob m = RuneMob.spawn(ID, level, feet, face, life, 1.0, true);
        puff(level, feet, glow, 0.9);
        if (m == null) {
            RuneKit.shadeClone(sp, level, feet, face, 1, DARK, glow, Opt.of("life", Math.min(1.0, life / 20.0), "fade", 0.5, "alpha", 0.85));
            return null;
        }
        m.anim("appear");
        return m;
    }

    /** 그림자 연기 한 무리 (발밑) · 보랏빛 불티 */
    static void puff(ServerLevel level, Vec3 feet, double[] glow, double size) {
        JobFx.smoke(level, V.up(feet, 0.6), size, DARK, glow, 0.45, Opt.of("alpha", 0.85));
        SkillFx.sparks(level, V.up(feet, 0.8), glow, 5, 2.5);
    }

    /** 활 끝 (화살이 나가는 자리 — 분신 몸 앞 · 왼쪽 · 가슴 높이): 수평으로 겨눌 때 */
    static Vec3 bowOf(RuneMob m) {
        Vec3 f = m.forward();
        return V.add(V.add(V.up(m.pos(), 1.42), f, 0.7), V.rightOf(f), -0.12);
    }

    /** 하늘로 겨눌 때의 활 끝 */
    static Vec3 bowUpOf(RuneMob m) {
        Vec3 f = m.forward();
        return V.add(V.up(m.pos(), 2.1), f, 0.45);
    }

    /** 떠남: 연기로 꺼짐 (7틱 뒤 지움) · 그림자 연기 */
    static void leave(RuneMob m, double[] glow) {
        if (m == null || !m.alive()) return;
        puff(m.level(), m.pos(), glow, 0.8);
        m.leave("vanish", 7);
    }

    // ------------------------------------------------------------ 섀도 댄스 (태세 동안 곁에서 함께 달리는 분신 둘)

    private static final Map<UUID, List<RuneMob>> DANCE = new HashMap<>();
    /** 곁 자리: 오른쪽 · 앞 (칸) — 둘 다 조금 뒤 (1인칭 화면 밖 · 3인칭에서 몸 양옆) */
    private static final double[][] SIDE = {{1.45, -0.7}, {-1.45, -0.7}};

    static Vec3 sideSpot(ServerPlayer sp, int i) {
        Vec3 f = Kit.facingOf(sp);
        return V.add(V.add(sp.position(), V.rightOf(f), SIDE[i][0]), f, SIDE[i][1]);
    }

    /** 섀도 댄스: 분신 둘이 ticks 틱 동안 곁에서 따라 달림 (앞 분신은 흩어짐) */
    static void dance(ServerPlayer sp, double[] glow, int ticks) {
        danceEnd(sp, glow);
        List<RuneMob> list = new ArrayList<>();
        for (int i = 0; i < SIDE.length; i++) {
            int ii = i;
            RuneMob m = spawn(sp, sideSpot(sp, i), Kit.facingOf(sp), ticks + 30, glow);
            if (m == null) continue;
            m.follow(() -> sp.isRemoved() ? null : sideSpot(sp, ii), () -> sp.isRemoved() ? null : Kit.facingOf(sp), 0.4, ticks + 20);
            list.add(m);
        }
        if (!list.isEmpty()) DANCE.put(sp.getUUID(), list);
    }

    /** 섀도 댄스가 끝남: 분신이 연기로 꺼짐 */
    static void danceEnd(ServerPlayer sp, double[] glow) {
        List<RuneMob> list = DANCE.remove(sp.getUUID());
        if (list != null) for (RuneMob m : list) leave(m, glow);
    }

    /** 곁의 분신들 (살아 있는 것) */
    static List<RuneMob> dancers(ServerPlayer sp) {
        List<RuneMob> list = DANCE.get(sp.getUUID());
        List<RuneMob> out = new ArrayList<>();
        if (list != null) for (RuneMob m : list) if (m.alive()) out.add(m);
        return out;
    }

    /** 시험: 곁의 분신 수 */
    static int dancerCount(ServerPlayer sp) {
        return dancers(sp).size();
    }
}
