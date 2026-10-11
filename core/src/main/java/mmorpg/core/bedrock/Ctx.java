package mmorpg.core.bedrock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import mmorpg.core.Molang;

/**
 * 엔티티 동작 · 렌더 컨트롤러 식의 값 칸 (Molang.Vars): 고정 query 칸 + 엔티티 속성 칸 (q.property('rpg:flash') — 엔티티 종류마다 속성 목록이 다름).
 * 값 배열 (double[size()]) 은 그릴 때마다 채움 (클라이언트 BedrockRenderer).
 */
public final class Ctx implements Molang.Vars {
    /** q.life_time: 엔티티가 (이 클라이언트에) 나타난 뒤 지난 초 */
    public static final int LIFE_TIME = 0;
    /** q.anim_time: 지금 계산하는 동작이 시작된 뒤 지난 초 (동작마다 바꿔 넣음) */
    public static final int ANIM_TIME = 1;
    /** q.modified_move_speed: 걷는 빠르기 (자바 walkAnimation.speed — 0 ~ 1, 베드락 바닐라 동작과 같은 뜻) */
    public static final int MOVE_SPEED = 2;
    /** q.modified_distance_moved: 걸은 거리 (자바 walkAnimation.position) */
    public static final int DISTANCE_MOVED = 3;
    /** q.hurt_time: 맞은 뒤 흔들림 (10 → 0 틱) */
    public static final int HURT_TIME = 4;
    /** q.target_x_rotation · q.target_y_rotation: 머리 숙임 · 몸에 대한 고개 돌림 (도) */
    public static final int TARGET_X = 5;
    public static final int TARGET_Y = 6;
    /** q.any_animation_finished: 지금 동작이 한 번 끝까지 돌았는지 (멈춤 식에서만) */
    public static final int ANIM_FINISHED = 7;
    public static final int ON_GROUND = 8;
    public static final int IN_WATER = 9;
    public static final int HEALTH = 10;
    public static final int MAX_HEALTH = 11;
    public static final int GROUND_SPEED = 12;
    public static final int IS_MOVING = 13;
    /** q.is_saddled: 안장을 얹었는지 (탈것 — minecraft:is_saddled 컴포넌트가 있으면 1: 다이어울프의 안장 · 고삐가 보임) */
    public static final int IS_SADDLED = 14;
    static final int FIXED = 16;

    private final List<String> props;

    /** props = 이 엔티티 종류의 속성 이름 (예: rpg:flash) */
    public Ctx(List<String> props) {
        List<String> p = new ArrayList<>();
        for (String s : props) p.add(s.toLowerCase(Locale.ROOT));
        this.props = Collections.unmodifiableList(p);
    }

    /** 속성 이름 → 칸 (-1 = 이 종류에 없음) */
    public int prop(String id) {
        int i = props.indexOf(id.toLowerCase(Locale.ROOT));
        return i < 0 ? -1 : FIXED + i;
    }

    public List<String> props() {
        return props;
    }

    /** 값 배열 크기 */
    public int size() {
        return FIXED + props.size();
    }

    @Override
    public int slot(String name) {
        return -1; // variable.* 은 쓰지 않음 (= 0)
    }

    @Override
    public int query(String name) {
        switch (name) {
            case "life_time":
                return LIFE_TIME;
            case "anim_time":
                return ANIM_TIME;
            case "modified_move_speed":
                return MOVE_SPEED;
            case "modified_distance_moved":
                return DISTANCE_MOVED;
            case "hurt_time":
                return HURT_TIME;
            case "target_x_rotation":
                return TARGET_X;
            case "target_y_rotation":
                return TARGET_Y;
            case "any_animation_finished":
            case "all_animations_finished":
                return ANIM_FINISHED;
            case "is_on_ground":
                return ON_GROUND;
            case "is_in_water":
            case "is_in_water_or_rain":
                return IN_WATER;
            case "health":
                return HEALTH;
            case "max_health":
                return MAX_HEALTH;
            case "ground_speed":
                return GROUND_SPEED;
            case "is_moving":
                return IS_MOVING;
            case "is_saddled":
                return IS_SADDLED;
            default:
                if (name.startsWith("property:")) return prop(name.substring("property:".length()));
                return -1;
        }
    }
}
