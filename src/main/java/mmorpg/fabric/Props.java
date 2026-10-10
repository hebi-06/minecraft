package mmorpg.fabric;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 베드락 동적 속성 (getDynamicProperty · setDynamicProperty) 의 자바 판: 이름 → 글자 (숫자 · JSON 도 글자로).
 * - 엔티티 (NPC 의 번호 · 이름 · 인사말 …): 엔티티에 붙여 저장 (mmorpg:props)
 * - 플레이어 (퀘스트 진행 · 길 안내 …): 플레이어에 붙여 저장, 죽어도 그대로 (mmorpg:player_props)
 * - 세계 (NPC 목록 · 자리 · 퀘스트 정의 …): 오버월드에 붙여 저장 (mmorpg:world_props)
 * 값은 바꿀 때마다 새 지도로 바꿔 붙임 (붙인 값은 저장할 때 그대로 씀).
 */
public final class Props {
    private Props() {}

    private static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);
    public static AttachmentType<Map<String, String>> ENTITY;
    public static AttachmentType<Map<String, String>> PLAYER;
    public static AttachmentType<Map<String, String>> WORLD;

    static void init() {
        ENTITY = AttachmentRegistry.create(Mmorpg.id("props"), b -> b.persistent(CODEC).initializer(Map::of));
        PLAYER = AttachmentRegistry.create(Mmorpg.id("player_props"), b -> b.persistent(CODEC).copyOnDeath().initializer(Map::of));
        WORLD = AttachmentRegistry.create(Mmorpg.id("world_props"), b -> b.persistent(CODEC).initializer(Map::of));
    }

    private static AttachmentType<Map<String, String>> typeOf(Entity e) {
        return e instanceof Player ? PLAYER : ENTITY;
    }

    private static Map<String, String> mapOf(Entity e) {
        Map<String, String> m = e.getAttached(typeOf(e));
        return m == null ? Map.of() : m;
    }

    /** 엔티티 · 플레이어 속성 (없으면 null) */
    public static String get(Entity e, String key) {
        return mapOf(e).get(key);
    }

    /** 숫자 속성 (없거나 숫자가 아니면 fallback) */
    public static double num(Entity e, String key, double fallback) {
        return parse(get(e, key), fallback);
    }

    /** 엔티티 · 플레이어 속성을 바꿈 (null 이면 지움) */
    public static void set(Entity e, String key, String value) {
        Map<String, String> cur = mapOf(e);
        if (value == null ? !cur.containsKey(key) : value.equals(cur.get(key))) return;
        Map<String, String> next = new HashMap<>(cur);
        if (value == null) next.remove(key);
        else next.put(key, value);
        e.setAttached(typeOf(e), Map.copyOf(next));
    }

    private static ServerLevel world(MinecraftServer server) {
        return server.overworld();
    }

    private static Map<String, String> worldMap(MinecraftServer server) {
        Map<String, String> m = world(server).getAttached(WORLD);
        return m == null ? Map.of() : m;
    }

    /** 세계 속성 (없으면 null) */
    public static String world(MinecraftServer server, String key) {
        return worldMap(server).get(key);
    }

    public static double worldNum(MinecraftServer server, String key, double fallback) {
        return parse(world(server, key), fallback);
    }

    /** 세계 속성을 바꿈 (null 이면 지움) */
    public static void setWorld(MinecraftServer server, String key, String value) {
        Map<String, String> cur = worldMap(server);
        if (value == null ? !cur.containsKey(key) : value.equals(cur.get(key))) return;
        Map<String, String> next = new HashMap<>(cur);
        if (value == null) next.remove(key);
        else next.put(key, value);
        world(server).setAttached(WORLD, Map.copyOf(next));
    }

    /** 이름이 prefix 로 시작하는 세계 속성 모두 (이름 순 — 퀘스트 정의 rpg:quest:… 처럼) */
    public static Map<String, String> worldPrefixed(MinecraftServer server, String prefix) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, String> e : worldMap(server).entrySet()) if (e.getKey().startsWith(prefix)) out.put(e.getKey(), e.getValue());
        return out;
    }

    private static double parse(String s, double fallback) {
        if (s == null) return fallback;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
