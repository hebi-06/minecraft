package mmorpg.fabric.skill;

import net.minecraft.world.phys.Vec3;

/** 벡터 도구 (skills.js 의 add · sub · len · unit · flat · up · rightOf · turnY · screenDir 와 같은 계산) */
public final class V {
    private V() {}

    public static final Vec3 UP = new Vec3(0, 1, 0);
    public static final Vec3 DOWN = new Vec3(0, -1, 0);
    public static final Vec3 ZERO = Vec3.ZERO;
    public static final Vec3 SOUTH = new Vec3(0, 0, 1);

    /** a + b × k */
    public static Vec3 add(Vec3 a, Vec3 b, double k) {
        return new Vec3(a.x + b.x * k, a.y + b.y * k, a.z + b.z * k);
    }

    public static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    public static Vec3 sub(Vec3 a, Vec3 b) {
        return new Vec3(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    public static double len(Vec3 a) {
        return Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z);
    }

    /** 단위 벡터 (길이 0 이면 그대로 — JS: len || 1) */
    public static Vec3 unit(Vec3 a) {
        double l = len(a);
        if (l == 0) l = 1;
        return new Vec3(a.x / l, a.y / l, a.z / l);
    }

    /** 수평 단위 방향 (거의 수직이면 남쪽) */
    public static Vec3 flat(Vec3 a) {
        double l = Math.hypot(a.x, a.z);
        return l < 1e-6 ? SOUTH : new Vec3(a.x / l, 0, a.z / l);
    }

    public static Vec3 up(Vec3 a, double h) {
        return new Vec3(a.x, a.y + h, a.z);
    }

    /** 수평 방향 f 의 오른쪽 */
    public static Vec3 rightOf(Vec3 f) {
        return new Vec3(-f.z, 0, f.x);
    }

    public static Vec3 scale(Vec3 a, double k) {
        return new Vec3(a.x * k, a.y * k, a.z * k);
    }

    public static double dot(Vec3 a, Vec3 b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    public static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    /** 수평 거리 */
    public static double hdist(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** 수평 방향 f 를 y 축으로 deg 도 돌림 (skills.js turnY — 결과는 수평, y = 0) */
    public static Vec3 turnY(Vec3 f, double deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new Vec3(f.x * c - f.z * s, 0, f.x * s + f.z * c);
    }

    /** 보는 방향 view 에 수직인 면 (화면) 안에서 기울기 angle 도의 방향 (0° = 화면 가로, + 는 오른쪽 위로 — skills.js screenDir) */
    public static Vec3 screenDir(Vec3 view, double angle) {
        Vec3 side = unit(Math.abs(view.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(-view.z, 0, view.x));
        Vec3 upv = unit(new Vec3(side.y * view.z - side.z * view.y, side.z * view.x - side.x * view.z, side.x * view.y - side.y * view.x));
        double a = Math.toRadians(angle);
        return unit(add(new Vec3(side.x * Math.cos(a), side.y * Math.cos(a), side.z * Math.cos(a)), upv, Math.sin(a)));
    }

    /** 선형 보간 */
    public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
