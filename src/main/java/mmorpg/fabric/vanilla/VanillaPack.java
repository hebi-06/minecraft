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
                        jobIcons(builder, root);
                    }
                }));
        // 전용 서버는 켬 · 클라이언트 (싱글 — 다른 사람이 들어오지 않음) 는 끔: 개발 환경 (화면 시험) 에서 Polymer 기본은 켜짐이라 세계를 열 때마다 팩을 다시 만듦
        autoHostDefault(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER);
    }

    /**
     * 직업 스킬 아이콘 (모드는 GUI 그림 mmorpg:job_skill/&lt;스킬 id&gt; 로 그림 — tools/job_skill_icons.py) 을 아이템 모델로:
     * rpg:vjob/&lt;id&gt; (그대로) · rpg:vjob/&lt;id&gt;_locked (어둡게 — 잠긴 칸). 모드 없는 클라이언트의 직업 스킬 모드 핫바 (VanillaInput).
     */
    private static void jobIcons(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path root) {
        Path dir = root.resolve("assets").resolve(Mmorpg.MOD_ID).resolve("textures").resolve("gui").resolve("sprites").resolve("job_skill");
        if (!Files.isDirectory(dir)) return;
        int n = 0;
        try (var files = Files.list(dir)) {
            for (Path png : (Iterable<Path>) files::iterator) {
                String name = png.getFileName().toString();
                if (!name.endsWith(".png")) continue;
                String id = VanillaInput.iconId(name.substring(0, name.length() - 4));
                String model = Mmorpg.NS + ":item/vjob/" + id;
                builder.addData("assets/" + Mmorpg.NS + "/textures/item/vjob/" + id + ".png", Files.readAllBytes(png));
                builder.addStringData("assets/" + Mmorpg.NS + "/models/item/vjob/" + id + ".json",
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + model + "\"}}");
                builder.addStringData("assets/" + Mmorpg.NS + "/items/vjob/" + id + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}");
                builder.addStringData("assets/" + Mmorpg.NS + "/items/vjob/" + id + "_locked.json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":5263440}]}}");
                n++;
            }
        } catch (IOException e) {
            Mmorpg.LOG.warn("[vanilla] 직업 스킬 아이콘을 팩에 넣지 못함: {}", e.toString());
        }
        Mmorpg.LOG.info("[vanilla] job skill icons {}", n);
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
