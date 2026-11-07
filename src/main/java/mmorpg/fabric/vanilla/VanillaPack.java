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
     * 모드가 GUI 그림으로 그리는 것을 아이템 모델로 (모드 없는 클라이언트는 아이템만 그림): 직업 스킬 아이콘 (mmorpg:job_skill/&lt;스킬 id&gt; —
     * tools/job_skill_icons.py) → rpg:vjob/&lt;id&gt; (+ _locked 어둡게 — 잠긴 칸, 직업 스킬 모드 핫바 VanillaInput), 창 그림 (mmorpg:ui/&lt;이름&gt; —
     * 확인 · 취소 · 이전 · 다음 · 칸 장식 …) → rpg:vui/&lt;이름&gt; (상자 창 칸 VanillaForms).
     */
    private static void jobIcons(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path root) {
        Path sprites = root.resolve("assets").resolve(Mmorpg.MOD_ID).resolve("textures").resolve("gui").resolve("sprites");
        Mmorpg.LOG.info("[vanilla] job skill icons {}, ui icons {}", spriteModels(builder, sprites.resolve("job_skill"), "vjob", true),
                spriteModels(builder, sprites.resolve("ui"), "vui", false));
    }

    private static int spriteModels(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path dir, String prefix, boolean locked) {
        if (!Files.isDirectory(dir)) return 0;
        int n = 0;
        try (var files = Files.list(dir)) {
            for (Path png : (Iterable<Path>) files::iterator) {
                String name = png.getFileName().toString();
                if (!name.endsWith(".png")) continue;
                String id = VanillaInput.iconId(name.substring(0, name.length() - 4));
                String model = Mmorpg.NS + ":item/" + prefix + "/" + id;
                byte[] bytes = Files.readAllBytes(png);
                if (pngSide(bytes) > 64) continue; // 창 바탕 (9 칸 늘림 그림 — 아이콘 아님)
                builder.addData("assets/" + Mmorpg.NS + "/textures/item/" + prefix + "/" + id + ".png", bytes);
                builder.addStringData("assets/" + Mmorpg.NS + "/models/item/" + prefix + "/" + id + ".json",
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + model + "\"}}");
                builder.addStringData("assets/" + Mmorpg.NS + "/items/" + prefix + "/" + id + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}");
                if (locked) {
                    builder.addStringData("assets/" + Mmorpg.NS + "/items/" + prefix + "/" + id + "_locked.json",
                            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":5263440}]}}");
                }
                n++;
            }
        } catch (IOException e) {
            Mmorpg.LOG.warn("[vanilla] {} 그림을 팩에 넣지 못함: {}", prefix, e.toString());
        }
        return n;
    }

    /** PNG 의 긴 변 (IHDR 너비 · 높이, 읽지 못하면 0) */
    private static int pngSide(byte[] b) {
        if (b.length < 24) return 0;
        int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
        int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
        return Math.max(w, h);
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
