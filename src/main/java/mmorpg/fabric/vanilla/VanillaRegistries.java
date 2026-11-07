package mmorpg.fabric.vanilla;

import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import mmorpg.fabric.Mmorpg;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.TreeMap;

/**
 * 남은 RPG 등록 항목 (아이템 구성 요소 mmorpg:enhance · mmorpg:rune_break, 창작 탭 셋, 섬 맵 생성기 · 바이옴 고르개 등) 을 모두 서버 항목으로 —
 * Fabric 레지스트리 동기화가 모드 없는 클라이언트를 끊지 않게 (모드 항목이 남으면 "이 서버는 Fabric API 가 필요" 로 끊음).
 * 서버 항목은 서버 · 모드 클라이언트 모두 바닐라 항목 뒤로 같은 차례 (Polymer rsm) 라 모드 클라이언트의 번호는 그대로 맞음.
 * 아이템 · 블록 · 엔티티 · 파티클은 각자의 대체 (Polymer 오버레이) 가 먼저 서버 항목으로 둠.
 */
final class VanillaRegistries {
    private VanillaRegistries() {}

    static void init() {
        Map<String, Integer> marked = new TreeMap<>();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) mark(registry, marked);
        Mmorpg.LOG.info("[vanilla] server entries {}", marked);
    }

    private static <T> void mark(Registry<T> registry, Map<String, Integer> marked) {
        for (T entry : registry) {
            Identifier id = registry.getKey(entry);
            if (!VanillaSupport.ours(id)) continue;
            if (!RegistrySyncUtils.isServerEntry(registry, entry)) RegistrySyncUtils.setServerEntry(registry, entry);
            marked.merge(registry.key().identifier().getPath(), 1, Integer::sum);
        }
    }
}
