package mmorpg.fabric.vanilla;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
                        guiFonts(builder, root);
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

    /**
     * 그림 창 (VanillaGui — 상자 메뉴 제목 글자로 그리는 바탕 · 글자): mmorpg/vanilla/gui (tools/vanilla_gui.py) 를 팩에 —
     * 메뉴 쪽 바탕 (rpg:gui/menu_*.png · 글꼴 rpg:gui_bg), 글자판 (rpg:gui/text.png · 줄 글꼴 rpg:gl&lt;d&gt;), 숫자판 (rpg:gui/num.png ·
     * 숫자 글꼴 rpg:gn&lt;d&gt;), 자리 옮김 (rpg:gui_space), 칸 그림 (icons → rpg:vui/&lt;이름&gt;), 설명 상자 (tooltip → rpg:tooltip/menu_*),
     * 홈 카드 그림 (rpg:vtile/&lt;이름&gt; — 아이템 모델의 gui 자리 옮김 · 크기로 카드 가운데에 크게).
     */
    private static void guiFonts(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path root) {
        Path dir = root.resolve("mmorpg").resolve("vanilla").resolve("gui");
        if (!Files.isDirectory(dir)) return;
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
            // 바탕 그림 (메뉴 쪽마다 하나 — 그림 위 = 창 위 − MENU_TOP: 제목 줄 (y 6) 에서 6 + 7 − ascent)
            com.google.gson.JsonArray bg = new com.google.gson.JsonArray();
            for (VanillaGui.Bg b : VanillaGui.Bg.values()) {
                builder.addData("assets/" + Mmorpg.NS + "/textures/gui/" + b.file + ".png", Files.readAllBytes(dir.resolve(b.file + ".png")));
                com.google.gson.JsonObject p = new com.google.gson.JsonObject();
                p.addProperty("type", "bitmap");
                p.addProperty("file", Mmorpg.NS + ":gui/" + b.file + ".png");
                p.addProperty("height", VanillaGui.MENU_H);
                p.addProperty("ascent", 13 + VanillaGui.MENU_TOP);
                com.google.gson.JsonArray chars = new com.google.gson.JsonArray();
                chars.add(String.valueOf(b.glyph()));
                p.add("chars", chars);
                bg.add(p);
            }
            builder.addStringData("assets/" + Mmorpg.NS + "/font/gui_bg.json", gson.toJson(providers(bg)));
            // 자리 옮김
            com.google.gson.JsonObject adv = new com.google.gson.JsonObject();
            for (int bit = 0; bit <= 8; bit++) {
                adv.addProperty(String.valueOf((char) ('\uF801' + bit)), -(1 << bit));
                adv.addProperty(String.valueOf((char) ('\uF811' + bit)), 1 << bit);
            }
            com.google.gson.JsonObject sp = new com.google.gson.JsonObject();
            sp.addProperty("type", "space");
            sp.add("advances", adv);
            com.google.gson.JsonArray spArr = new com.google.gson.JsonArray();
            spArr.add(sp);
            builder.addStringData("assets/" + Mmorpg.NS + "/font/gui_space.json", gson.toJson(providers(spArr)));
            // 글자판 + 줄 글꼴
            builder.addData("assets/" + Mmorpg.NS + "/textures/gui/text.png", Files.readAllBytes(dir.resolve("text.png")));
            com.google.gson.JsonArray rows = com.google.gson.JsonParser.parseString(Files.readString(dir.resolve("text_chars.json"), StandardCharsets.UTF_8)).getAsJsonArray();
            for (int d : VanillaGui.LINES) {
                builder.addStringData("assets/" + Mmorpg.NS + "/font/gl" + d + ".json", gson.toJson(providers(lineFont("text.png", rows, d, 4))));
            }
            // 숫자판 + 숫자 글꼴
            builder.addData("assets/" + Mmorpg.NS + "/textures/gui/num.png", Files.readAllBytes(dir.resolve("num.png")));
            com.google.gson.JsonObject nums = com.google.gson.JsonParser.parseString(Files.readString(dir.resolve("num_widths.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            com.google.gson.JsonArray numRows = new com.google.gson.JsonArray();
            numRows.add(nums.get("chars").getAsString());
            for (int d : VanillaGui.NUM_LINES) {
                builder.addStringData("assets/" + Mmorpg.NS + "/font/gn" + d + ".json",
                        gson.toJson(providers(lineFont("num.png", numRows, d, nums.get("space").getAsInt()))));
            }
            // 상자 메뉴의 "인벤토리" 글자 (가방 위 — 그림 창의 바탕 위에 겹쳐 보임) 를 비움 (영어 · 한국어)
            for (String lang : new String[]{"en_us", "ko_kr"}) {
                builder.addStringData("assets/minecraft/lang/" + lang + ".json", "{\"container.inventory\":\"\"}");
            }
            // 설명 상자 (tooltip_style rpg:menu → 그림 rpg:tooltip/menu_background · menu_frame, 9 칸 늘림)
            for (String part : new String[]{"menu_background", "menu_frame"}) {
                Path png = dir.resolve("tooltip").resolve(part + ".png");
                if (!Files.exists(png)) continue;
                String base = "assets/" + Mmorpg.NS + "/textures/gui/sprites/tooltip/" + part + ".png";
                builder.addData(base, Files.readAllBytes(png));
                builder.addStringData(base + ".mcmeta", "{\"gui\":{\"scaling\":{\"type\":\"nine_slice\",\"width\":32,\"height\":32,\"border\":9}}}");
            }
            // 홈 카드 그림 (카드 = 칸 둘 × 줄 셋 — 왼쪽 위 칸의 아이템을 오른쪽 9 · 아래 10 옮기고 1.5배)
            for (Map.Entry<String, String> e : VanillaMenu.TILES.entrySet()) {
                String model = Mmorpg.NS + ":item/vtile/" + e.getKey();
                builder.addStringData("assets/" + Mmorpg.NS + "/models/item/vtile/" + e.getKey() + ".json",
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + e.getValue() + "\"},"
                                + "\"display\":{\"gui\":{\"rotation\":[0,0,0],\"translation\":[9,-10,0],\"scale\":[1.5,1.5,1.5]}}}");
                builder.addStringData("assets/" + Mmorpg.NS + "/items/vtile/" + e.getKey() + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"}}");
            }
            Mmorpg.LOG.info("[vanilla] gui backgrounds {}, line fonts {} + {}, tiles {}, icons {}", VanillaGui.Bg.values().length, VanillaGui.LINES.length,
                    VanillaGui.NUM_LINES.length, VanillaMenu.TILES.size(), spriteModels(builder, dir.resolve("icons"), "vui", false));
        } catch (IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vanilla] 그림 창 글꼴을 팩에 넣지 못함: {}", e.toString());
        }
    }

    /** 줄 글꼴 하나: 빈칸 (너비 blank) + 글자판 (칸 12 · 기준선 10 → 칸 위 = 제목 줄 y + 7 − ascent = 3 + d) */
    private static com.google.gson.JsonArray lineFont(String file, com.google.gson.JsonArray rows, int d, int blank) {
        com.google.gson.JsonArray list = new com.google.gson.JsonArray();
        com.google.gson.JsonObject space = new com.google.gson.JsonObject();
        space.addProperty("type", "space");
        com.google.gson.JsonObject one = new com.google.gson.JsonObject();
        one.addProperty(" ", blank);
        space.add("advances", one);
        list.add(space);
        com.google.gson.JsonObject p = new com.google.gson.JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", Mmorpg.NS + ":gui/" + file);
        p.addProperty("height", 12);
        p.addProperty("ascent", 10 - d);
        p.add("chars", rows);
        list.add(p);
        return list;
    }

    private static com.google.gson.JsonObject providers(com.google.gson.JsonArray list) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.add("providers", list);
        return o;
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
