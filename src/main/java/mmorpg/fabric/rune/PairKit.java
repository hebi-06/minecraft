package mmorpg.fabric.rune;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.25.0 두 스탯 룬 계열 여섯 (해류 · 골렘 · 무투 · 사냥꾼 · 화산 · 투사 — RuneTide … RuneGladiator) 이 함께 쓰는 것:
 * 몹 무적 시간을 피한 타격 (hit — 같은 적을 여러 번 쳐도 모두 들어감), 탄도 (lob — rpg:boulder · glob 의 중력), 블록 표시 모형 (Rig — 골렘 · 거신 · 주먹 · 철퇴 ·
 * 화산 · 바다뱀을 블록 몇 개로 짜서 한꺼번에 움직임), 땅 높이, 대상이 없을 때 거절.
 */
final class PairKit {
    private PairKit() {}

    /** 블록 표시의 밝기 (빛나는 룬 · 용암 — 밤 · 동굴에서도 보이게) */
    static final Brightness GLOW = new Brightness(15, 15);
    /** 돌 · 쇠처럼 빛나지 않는 것도 어둡지 않게 (블록 빛 9) */
    static final Brightness LIT = new Brightness(9, 15);
    /** rpg:boulder · rpg:glob 의 중력 (칸/초²) */
    static final double GRAVITY = 20;

    // ------------------------------------------------------------ 타격

    /**
     * 몹 무적 시간 (10틱) 을 피해 지금 (그 적이 무적이면 끝나는 틱에) 위력 × mult 한 번 — 같은 적을 여러 번 치는 룬 · 화상 · 마무리가 겹쳐도 모두 들어감.
     * style = 숫자 모양 (null = 룬 노랑), 들어가면 then (깎인 체력). 쓴 사람이 쓰러지거나 다른 삶이면 취소
     */
    static void hit(ServerPlayer sp, Runes.Rune r, LivingEntity e, double P, double mult, DamageNumbers.Style style, String life, DoubleConsumer then) {
        if (!(mult > 0) || e == null) return;
        Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), () -> RuneKit.sameLife(sp, life) && !e.isRemoved(), () -> {
            double d = RuneKit.runeHit(sp, e, P, mult, r.stat(), null, style);
            if (d > 0 && then != null) then.accept(d);
        });
    }

    static void hit(ServerPlayer sp, Runes.Rune r, LivingEntity e, double P, double mult, String life, DoubleConsumer then) {
        hit(sp, r, e, P, mult, null, life, then);
    }

    /** 보스 (보스 9종) — 묶음 · 석화 · 끌어오기 · 가두기를 받지 않음 (느려짐만) */
    static boolean boss(Entity e) {
        return Bosses.isBossEntity(e);
    }

    /** 묶음: ticks 동안 움직이지 못함 (구속 XI — 보스는 구속 II) */
    static void root(LivingEntity e, int ticks) {
        RuneKit.effect(e, "slowness", ticks, boss(e) ? 1 : 10);
    }

    /** 대상이 없으면 거절 (재사용 대기 없음) */
    static boolean refuse(ServerPlayer sp, Obj k, String what) {
        Notify.warn(sp, "§c" + k.str("name") + ": §7" + what);
        return false;
    }

    /** 앞쪽 range 칸에 대상이 없을 때 */
    static boolean noTarget(ServerPlayer sp, Obj k) {
        return refuse(sp, k, "앞쪽 " + k.text("range") + "칸 안에 대상이 없습니다");
    }

    /** 몸이 있는 적만 (쓰러지지 않은) */
    static List<LivingEntity> alive(List<LivingEntity> in) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : in) if (!e.isRemoved() && e.isAlive()) out.add(e);
        return out;
    }

    // ------------------------------------------------------------ 자리 · 탄도

    /** (x, z) 의 땅 높이 (hint 둘레, 못 찾으면 hint) */
    static double groundY(ServerLevel level, double x, double z, double hint) {
        return RuneTerra.groundY(level, x, z, hint);
    }

    /** p 를 그 자리 땅 위로 */
    static Vec3 onGround(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, groundY(level, p.x, p.z, p.y), p.z);
    }

    /** 원 위 한 점 (수평) */
    static Vec3 ring(Vec3 c, double r, double ang) {
        return new Vec3(c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r);
    }

    /** from 에서 to 로 t 초에 날아가는 속도 (중력 g 칸/초² — 포물선) */
    static Vec3 lob(Vec3 from, Vec3 to, double t, double g) {
        double s = Math.max(0.05, t);
        return new Vec3((to.x - from.x) / s, (to.y - from.y) / s + g * s / 2, (to.z - from.z) / s);
    }

    /** 포물선 위 자리 (u = 0 ~ 1, 꼭대기 높이 h) */
    static Vec3 arcAt(Vec3 a, Vec3 b, double h, double u) {
        return new Vec3(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u + 4 * h * u * (1 - u), a.z + (b.z - a.z) * u);
    }

    /** 시전자 눈 · 3인칭 카메라에서 clear 칸 안이면 true (큰 그림 · 모형을 세우지 않음) */
    static boolean nearCam(ServerPlayer sp, Vec3 at, double clear) {
        return RuneKit.camDist(sp, at) < clear;
    }

    // ------------------------------------------------------------ 블록 표시 모형

    /** 수평 방향 f (모형의 앞 = +z) 로 돌림: 모형 y = 위 · z = 앞 · x = y × z (앞을 보는 모형의 왼쪽) */
    static Quaternionf facing(Vec3 f) {
        Vec3 z = V.flat(f);
        return FxDisplays.basis(new Vec3(z.z, 0, -z.x), V.UP, z);
    }

    /** 아무 방향 dir (모형의 앞 = +z) 로 돌림 — 위아래로도 (모형 y 는 될수록 위쪽) */
    static Quaternionf look(Vec3 dir) {
        Vec3 z = V.unit(dir);
        Vec3 ref = Math.abs(z.y) > 0.98 ? new Vec3(0, 0, 1) : V.UP;
        Vec3 x = V.unit(V.cross(ref, z));
        Vec3 y = V.cross(z, x);
        return FxDisplays.basis(x, y, z);
    }

    /** 축 axis 둘레로 deg 도 */
    static Quaternionf about(Vec3 axis, double deg) {
        Vec3 u = V.unit(axis);
        return new Quaternionf().rotationAxis((float) Math.toRadians(deg), (float) u.x, (float) u.y, (float) u.z);
    }

    /** 상자 하나의 변환: 가운데 c (엔티티 자리 기준 · 세상 축) · 돌림 q · 크기 (블록 모형 0 ~ 1 을 가운데로 맞춤) */
    static Transformation box(Vec3 c, Quaternionf q, double sx, double sy, double sz) {
        Vector3f half = new Vector3f((float) sx / 2, (float) sy / 2, (float) sz / 2).rotate(new Quaternionf(q));
        return new Transformation(new Vector3f((float) c.x - half.x, (float) c.y - half.y, (float) c.z - half.z), new Quaternionf(q),
                new Vector3f((float) sx, (float) sy, (float) sz), new Quaternionf());
    }

    /** 부품 하나: 표시 · 무리 (0 = 몸, 1 ~ = 팔 등 따로 도는 무리) · 가운데 · 크기 · 돌림 (모형 축) */
    record Part(Display.BlockDisplay d, int group, Vec3 c, Vec3 size, Quaternionf q) {}

    /**
     * 블록 몇 개로 짠 모형 (골렘 · 거신 · 거대한 주먹 · 철퇴 · 화산 · 바다뱀): 모두 같은 자리 (base) 에 띄우고 변환으로 짬.
     * 모형 전체는 자리 (base 기준 옮김) · 돌림 · 크기로, 무리마다 축 (pivot — 모형 축) 둘레로 따로 돌림 (팔 휘두르기)
     */
    static final class Rig {
        final ServerLevel level;
        final Vec3 base;
        final int life;
        final List<Part> parts = new ArrayList<>();
        final List<Vec3> pivots = new ArrayList<>();
        Vec3 offset = Vec3.ZERO;
        Quaternionf rot = new Quaternionf();
        double scale = 1;
        Quaternionf[] groupRot = new Quaternionf[0];

        Rig(ServerLevel level, Vec3 base, int life) {
            this.level = level;
            this.base = base;
            this.life = life;
            pivots.add(Vec3.ZERO);
        }

        /** 무리 하나 더 (축 = 모형 축의 한 점). 무리 번호 */
        int group(Vec3 pivot) {
            pivots.add(pivot);
            return pivots.size() - 1;
        }

        /** 부품 하나를 지금 자세로 띄움 (light null = 그 자리 밝기) */
        Rig add(int group, BlockState st, Vec3 c, Vec3 size, Quaternionf q, Brightness light) {
            Quaternionf qq = q == null ? new Quaternionf() : q;
            Display.BlockDisplay d = FxDisplays.block(level, base, st, transformOf(group, c, size, qq), life, light);
            if (d != null) parts.add(new Part(d, group, c, size, qq));
            return this;
        }

        Rig add(BlockState st, Vec3 c, double sx, double sy, double sz, Brightness light) {
            return add(0, st, c, new Vec3(sx, sy, sz), null, light);
        }

        private Quaternionf gr(int g) {
            return g > 0 && g < groupRot.length && groupRot[g] != null ? groupRot[g] : null;
        }

        private Transformation transformOf(int group, Vec3 c, Vec3 size, Quaternionf q) {
            Vec3 p = c;
            Quaternionf qq = new Quaternionf(q);
            Quaternionf g = gr(group);
            if (g != null) {
                Vec3 pv = pivots.get(group);
                Vector3f d = new Vector3f((float) (c.x - pv.x), (float) (c.y - pv.y), (float) (c.z - pv.z)).rotate(new Quaternionf(g));
                p = new Vec3(pv.x + d.x, pv.y + d.y, pv.z + d.z);
                qq = new Quaternionf(g).mul(q);
            }
            Vector3f w = new Vector3f((float) (p.x * scale), (float) (p.y * scale), (float) (p.z * scale)).rotate(new Quaternionf(rot));
            Vec3 center = new Vec3(offset.x + w.x, offset.y + w.y, offset.z + w.z);
            return box(center, new Quaternionf(rot).mul(qq), size.x * scale, size.y * scale, size.z * scale);
        }

        /** 자세를 정하고 ticks 동안 그리로 (클라이언트 보간 — 띄우고 2틱 뒤부터) */
        void pose(Vec3 offset, Quaternionf rot, double scale, int ticks, Quaternionf... groups) {
            this.offset = offset;
            this.rot = rot;
            this.scale = scale;
            this.groupRot = groups;
            for (Part p : parts) FxDisplays.move(p.d(), transformOf(p.group(), p.c(), p.size(), p.q()), ticks);
        }

        /** 무리 하나만 다시 돌림 (몸은 그대로) */
        void swing(int group, Quaternionf g, int ticks) {
            if (groupRot.length <= group) {
                Quaternionf[] n = new Quaternionf[group + 1];
                System.arraycopy(groupRot, 0, n, 0, groupRot.length);
                groupRot = n;
            }
            groupRot[group] = g;
            for (Part p : parts) if (p.group() == group) FxDisplays.move(p.d(), transformOf(p.group(), p.c(), p.size(), p.q()), ticks);
        }

        /** 모형 축의 한 점 → 세상 자리 (지금 자세) */
        Vec3 world(Vec3 local) {
            Vector3f w = new Vector3f((float) (local.x * scale), (float) (local.y * scale), (float) (local.z * scale)).rotate(new Quaternionf(rot));
            return new Vec3(base.x + offset.x + w.x, base.y + offset.y + w.y, base.z + offset.z + w.z);
        }

        /** 무리 group 의 모형 축 한 점 → 세상 자리 (그 무리의 돌림까지) */
        Vec3 world(int group, Vec3 local) {
            Quaternionf g = gr(group);
            if (g == null) return world(local);
            Vec3 pv = pivots.get(group);
            Vector3f d = new Vector3f((float) (local.x - pv.x), (float) (local.y - pv.y), (float) (local.z - pv.z)).rotate(new Quaternionf(g));
            return world(new Vec3(pv.x + d.x, pv.y + d.y, pv.z + d.z));
        }

        /**
         * 무너짐: 부품마다 모형 가운데에서 바깥으로 spread 배 · 아래로 drop 칸 (땅 아래로는 안 감) 떨어지며 shrink 배로 줄고 조금씩 기욺 (ticks 동안)
         */
        void crumble(int ticks, double spread, double drop, double shrink) {
            for (Part p : parts) {
                Vec3 c = p.c();
                double sy = p.size().y * shrink;
                double y = Math.max(sy * 0.5, c.y - drop * (0.6 + 0.4 * Kit.rand()));
                Vec3 c2 = new Vec3(c.x * (1 + spread) + (Kit.rand() - 0.5) * 0.5, y, c.z * (1 + spread) + (Kit.rand() - 0.5) * 0.5);
                Quaternionf q2 = new Quaternionf(p.q()).rotateXYZ((float) ((Kit.rand() - 0.5) * 1.2), (float) ((Kit.rand() - 0.5) * 1.2),
                        (float) ((Kit.rand() - 0.5) * 1.2));
                FxDisplays.move(p.d(), transformOf(p.group(), c2, V.scale(p.size(), shrink), q2), ticks);
            }
        }

        void remove() {
            for (Part p : parts) FxDisplays.remove(p.d());
        }

        boolean empty() {
            return parts.isEmpty();
        }
    }
}
