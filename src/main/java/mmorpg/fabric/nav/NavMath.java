package mmorpg.fabric.nav;

import net.minecraft.world.phys.Vec3;

/**
 * 길 안내 계산 (nav.js 의 순수 함수 — angleOf · sectorFor · sectorOf · distText · facing): 서버 (Nav — 시험 · /nav) 와 클라이언트 화면 (NavHud — 매 프레임
 * 화살표 · 거리 글자) 가 함께 씀. 벡터는 skills.js 의 flat · sub · rightOf 와 같은 계산.
 */
public final class NavMath {
    private NavMath() {}

    /** 화살표 칸 하나의 각 (16방향) */
    public static final double SECTOR = 22.5;

    /** 수평 단위 방향 (거의 수직이면 남쪽 — skills.js flat) */
    static Vec3 flat(Vec3 a) {
        double l = Math.hypot(a.x, a.z);
        return l < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(a.x / l, 0, a.z / l);
    }

    /** 내가 보는 쪽에서 목적지까지 시계 방향 각 (도, -180 ~ 180: 0 = 앞, 90 = 오른쪽, -90 = 왼쪽) */
    public static double angleOf(Vec3 view, Vec3 from, Vec3 to) {
        Vec3 f = flat(view);
        Vec3 d = flat(new Vec3(to.x - from.x, to.y - from.y, to.z - from.z));
        Vec3 r = new Vec3(-f.z, 0, f.x); // rightOf
        return (Math.atan2(d.x * r.x + d.z * r.z, d.x * f.x + d.z * f.z) * 180) / Math.PI;
    }

    /**
     * 각 → 0 ~ 15 칸 (0 = 앞, 4 = 오른쪽, 8 = 뒤, 12 = 왼쪽 — 그림 a_m00 ~ a_m15 · a_c00 ~ a_c15). prev 가 있으면 (0 이상) 칸 경계를 hold 도 넘어야 바뀜
     * (경계에서 화살표가 떨리지 않게 — 애드온 NAV.sectorHold)
     */
    public static int sectorFor(double ang, int prev, double hold) {
        if (prev >= 0) {
            double off = ang - prev * SECTOR;
            off = ((((off + 180) % 360) + 360) % 360) - 180;
            if (Math.abs(off) <= SECTOR / 2 + hold) return prev;
        }
        return (int) (((Math.round(ang / SECTOR) % 16) + 16) % 16);
    }

    /** 경계 머무름 없이 칸 (시험용 — sectorOf) */
    public static int sectorOf(Vec3 view, Vec3 from, Vec3 to) {
        return sectorFor(angleOf(view, from, to), -1, 0);
    }

    /** 거리 글자: 20칸까지 1칸, 100칸까지 5칸, 그 밖은 10칸 단위 (걸을 때 글자가 너무 자주 바뀌지 않게 — distText) */
    public static String distText(double d) {
        int step = d < 20 ? 1 : d < 100 ? 5 : 10;
        return Math.round(d / step) * step + "칸";
    }

    /** 높이 글자: 목적지가 height 칸 넘게 위 · 아래면 " §7(위 N)" · " §7(아래 N)", 아니면 "" */
    public static String heightText(double dy, double height) {
        if (!(Math.abs(dy) >= height)) return "";
        return " §7(" + (dy > 0 ? "위" : "아래") + " " + Math.round(Math.abs(dy)) + ")";
    }

    /** 화살표일 때 이름 뒤에 붙는 글자: " §7· §f거리" + 높이 (d = 수평 거리, dy = 목적지 높이 - 내 발 높이) */
    public static String arrowTail(double d, double dy, double height) {
        return " §7· §f" + distText(d) + heightText(dy, height);
    }

    /** 내가 보는 쪽: 거의 바로 위 · 아래를 보면 수평 성분이 없어서 몸의 방향 (yaw, 도) 으로 (facing) */
    public static Vec3 facing(Vec3 view, float yawDeg) {
        if (Math.hypot(view.x, view.z) >= 0.1) return view;
        double yaw = (yawDeg * Math.PI) / 180;
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** 화살표 그림 이름 (frame 1 = 메인 금빛 m, 2 = 일반 하늘빛 c — 칸 0 ~ 15): "a_m00" … */
    public static String arrowSprite(int frame, int sector) {
        int s = ((sector % 16) + 16) % 16;
        return "a_" + (frame == 1 ? "m" : "c") + (s < 10 ? "0" : "") + s;
    }

    /** 도착 그림 이름 ("a_mhr" · "a_chr") */
    public static String hereSprite(int frame) {
        return "a_" + (frame == 1 ? "m" : "c") + "hr";
    }
}
