package mmorpg.fabric.client;

/**
 * 0.48.2 영상 카메라 (사용자 "스킬들 모아서 나한테 보여줘 모델링하고 모션이 어떤지 봐야겠어"): 화면 시험이 스킬 영상을 찍을 때만 켬 — 카메라를 정한 자리 · 방향에 둠
 * (3인칭 카메라를 대신함 — 플레이어가 그대로 카메라의 주인이라 입력 · 시점 판단 (1인칭 숨김 그림 등) 은 3인칭과 같음). 게임에서는 쓰지 않음 (null).
 */
public final class FilmCamera {
    private FilmCamera() {}

    private static volatile double[] pose;

    /** 카메라 자리 (눈) · 보는 방향 (도) */
    public static void set(double x, double y, double z, float yaw, float pitch) {
        pose = new double[] {x, y, z, yaw, pitch};
    }

    public static void clear() {
        pose = null;
    }

    /** {x, y, z, yaw, pitch} 또는 null */
    public static double[] pose() {
        return pose;
    }
}
