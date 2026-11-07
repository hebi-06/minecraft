package mmorpg.fabric.vanilla;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 모드 없는 클라이언트가 받을 리소스 팩 (Polymer 가 서버를 켤 때 만듦 · AutoHost 가 같은 포트로 보냄 — 접속할 때 받기 창, 필수):
 * assets/rpg (아이템 정의 · 모델 · 그림 — 바닐라 형식, 약 14MB) + 제단 블록 상태 (Polymer blocks 가 더함).
 * assets/mmorpg (모드만 읽는 형식 — 베드락 모델 · 효과 모델 · 동작 · 애드온 파티클) 는 넣지 않음.
 *
 * <p>처음 켤 때 config/polymer/auto-host.json 이 없으면 전용 서버는 켜 둠 (Polymer 기본은 꺼짐 — 관리자가 끄거나 바꾼 것은 그대로) · 클라이언트는 끔.
 */
final class VanillaPack {
    private VanillaPack() {}

    static void init() {
        PolymerResourcePackUtils.markAsRequired();
        PolymerResourcePackUtils.RESOURCE_PACK_CREATION_EVENT.register(builder ->
                FabricLoader.getInstance().getModContainer(Mmorpg.MOD_ID).ifPresent(mod -> {
                    for (Path root : mod.getRootPaths()) {
                        Path assets = root.resolve("assets").resolve(Mmorpg.NS);
                        if (Files.isDirectory(assets)) builder.copyFromPath(assets, "assets/" + Mmorpg.NS + "/", false);
                    }
                }));
        // 전용 서버는 켬 · 클라이언트 (싱글 — 다른 사람이 들어오지 않음) 는 끔: 개발 환경 (화면 시험) 에서 Polymer 기본은 켜짐이라 세계를 열 때마다 팩을 다시 만듦
        autoHostDefault(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER);
    }

    private static void autoHostDefault(boolean enabled) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("polymer").resolve("auto-host.json");
        if (Files.exists(file)) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\n  \"enabled\": " + enabled + ",\n  \"required\": true\n}\n", StandardCharsets.UTF_8);
            Mmorpg.LOG.info("[vanilla] 리소스 팩 자동 전송 {} ({})", enabled ? "켬" : "끔", file);
        } catch (IOException e) {
            Mmorpg.LOG.warn("[vanilla] {} 를 쓰지 못함: {}", file, e.toString());
        }
    }
}
