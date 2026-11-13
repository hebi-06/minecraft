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
                        cosmeticItems(builder, root);
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
     * 홈 타일 그림 (rpg:vtile/&lt;이름&gt; — 아이템 모델의 gui 자리 옮김 · 크기로 타일 왼쪽 그림 자리 가운데에 크게).
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
            // 홈 타일 그림 (타일 = 칸 넷 × 줄 둘, 왼쪽 그림 자리 30 × 30 — 왼쪽 위 칸의 아이템을 오른쪽 9 · 아래 9 옮기고 1.5배)
            for (Map.Entry<String, String> e : VanillaMenu.TILES.entrySet()) {
                String model = Mmorpg.NS + ":item/vtile/" + e.getKey();
                builder.addStringData("assets/" + Mmorpg.NS + "/models/item/vtile/" + e.getKey() + ".json",
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + e.getValue() + "\"},"
                                + "\"display\":{\"gui\":{\"rotation\":[0,0,0],\"translation\":[9,-9,0],\"scale\":[1.5,1.5,1.5]}}}");
                // oversized_in_gui: 1.21.6+ 는 GUI 아이템을 칸 (16 × 16) 크기로 잘라 그림 — 칸 밖까지 그리게 (CI 42387417421 사진: 왼쪽 위 조각만 보임)
                builder.addStringData("assets/" + Mmorpg.NS + "/items/vtile/" + e.getKey() + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + model + "\"},\"oversized_in_gui\":true}");
            }
            glyphFonts(builder, dir, gson);
            cineFonts(builder, dir.resolve("cine"), gson);
            hudFonts(builder, dir.resolve("hud"), rows, numRows, nums.get("space").getAsInt(), gson);
            int big = bigRunes(builder, root);
            Mmorpg.LOG.info("[vanilla] gui backgrounds {}, line fonts {} + {}, tiles {}, icons {}, big runes {}", VanillaGui.Bg.values().length,
                    VanillaGui.LINES.length, VanillaGui.NUM_LINES.length, VanillaMenu.TILES.size(), spriteModels(builder, dir.resolve("icons"), "vui", true), big);
        } catch (IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vanilla] 그림 창 글꼴을 팩에 넣지 못함: {}", e.toString());
        }
    }

    /**
     * 그림 글자 (tools/vanilla_gui.py tree_sheets — 흰 그림, 글자 색으로 물들임): 줄 글꼴 rpg:gt&lt;r&gt; (r = 0 ~ 9 — 칸 틀 위 = cellY(r)):
     * 선 반쪽 · 탭 빛 · 결과 테 (18 × 18 — 칸 틀 자리), 칸 테 (22 × 22 — 2px 바깥), 목록 테 (56 × 20 — 1px 바깥),
     * rpg:gtx: 세모 (탭 아래 y 215) · 스크롤 손잡이 (나무 판 오른쪽 y 38). 글자 위 = 제목 줄 y (6) + 7 − ascent
     */
    private static void glyphFonts(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path dir, com.google.gson.Gson gson) throws IOException {
        Path meta = dir.resolve("tree_glyphs.json");
        if (!Files.exists(meta)) return;
        com.google.gson.JsonObject files = com.google.gson.JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("files");
        for (String f : files.keySet()) {
            builder.addData("assets/" + Mmorpg.NS + "/textures/gui/" + f + ".png", Files.readAllBytes(dir.resolve(f + ".png")));
        }
        for (int r = 0; r < VanillaGui.GLYPH_ROWS; r++) {
            int top = VanillaGui.cellY(r);
            com.google.gson.JsonArray list = new com.google.gson.JsonArray();
            list.add(bitmap("tree_cells", files, 18, 13 - top));
            list.add(bitmap("tree_frames", files, 22, 13 - (top - 2)));
            list.add(bitmap("tree_plate", files, 20, 13 - (top - 1)));
            builder.addStringData("assets/" + Mmorpg.NS + "/font/gt" + r + ".json", gson.toJson(providers(list)));
        }
        com.google.gson.JsonArray x = new com.google.gson.JsonArray();
        x.add(bitmap("tree_tri", files, 4, 13 - VanillaGui.TRI_Y));
        x.add(bitmap("tree_thumb", files, files.getAsJsonObject("tree_thumb").get("h").getAsInt(), 13 - VanillaGui.THUMB_Y));
        if (files.has("tree_bar")) x.add(bitmap("tree_bar", files, files.getAsJsonObject("tree_bar").get("h").getAsInt(), 13 - VanillaGui.BAR_Y));
        builder.addStringData("assets/" + Mmorpg.NS + "/font/gtx.json", gson.toJson(providers(x)));
    }

    /** 바닐라 하트 줄 그림 (투명으로 덮음 — VanillaHud 가 체력 막대를 그림). 탈것 체력 (vehicle_*) 은 그대로 */
    private static final String[] HEART_KINDS = {"container", "full", "half", "hardcore_full", "hardcore_half", "poisoned_full", "poisoned_half",
            "poisoned_hardcore_full", "poisoned_hardcore_half", "withered_full", "withered_half", "withered_hardcore_full", "withered_hardcore_half",
            "absorbing_full", "absorbing_half", "absorbing_hardcore_full", "absorbing_hardcore_half", "frozen_full", "frozen_half",
            "frozen_hardcore_full", "frozen_hardcore_half", "container_hardcore"};
    /** 바닐라 갑옷 · 배고픔 줄 그림 (투명 — 방어는 VanillaHud 배지, 배고픔은 게임에서 꺼짐) */
    private static final String[] BAR_SPRITES = {"armor_empty", "armor_half", "armor_full", "food_empty", "food_empty_hunger", "food_half",
            "food_half_hunger", "food_full", "food_full_hunger"};

    /**
     * 2026-11-08 HUD (VanillaHud — tools/vanilla_hud.py): 그림 rpg:gui/hud/*.png · 글꼴 rpg:hud (hud.json 의 제공자 그대로 — 세로 자리는 ascent),
     * 글자 글꼴 rpg:hud_t&lt;d&gt; (줄 글꼴과 같은 글자판 — 액션바 글자 줄 기준), 바닐라 하트 · 갑옷 · 배고픔 그림은 투명으로 덮음.
     */
    private static void hudFonts(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path dir, com.google.gson.JsonArray rows,
                                 com.google.gson.JsonArray numRows, int numSpace, com.google.gson.Gson gson) throws IOException {
        Path meta = dir.resolve("hud.json");
        if (!Files.exists(meta)) return;
        byte[] blank = null;
        try (var files = Files.list(dir)) {
            for (Path png : (Iterable<Path>) files::iterator) {
                String name = png.getFileName().toString();
                if (!name.endsWith(".png")) continue;
                byte[] bytes = Files.readAllBytes(png);
                if (name.equals("blank9.png")) blank = bytes;
                else builder.addData("assets/" + Mmorpg.NS + "/textures/gui/hud/" + name, bytes);
            }
        }
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
        // 모든 HUD 글꼴에 자리 옮김 글자 (VanillaHud.Line — 같은 글꼴 · 색을 한 조각으로 이어 보냄)
        builder.addStringData("assets/" + Mmorpg.NS + "/font/hud.json", gson.toJson(providers(withMoves(o.getAsJsonArray("providers")))));
        if (o.has("skill_fonts")) {
            com.google.gson.JsonObject sf = o.getAsJsonObject("skill_fonts");
            for (String f : sf.keySet()) builder.addStringData("assets/" + Mmorpg.NS + "/font/" + f + ".json", gson.toJson(providers(withMoves(sf.getAsJsonArray(f)))));
        }
        for (int d : VanillaHud.TEXT_D) {
            builder.addStringData("assets/" + Mmorpg.NS + "/font/hud_t" + d + ".json", gson.toJson(providers(withMoves(lineFont("text.png", rows, d, 4)))));
        }
        for (int d : VanillaHud.NUM_D) {
            builder.addStringData("assets/" + Mmorpg.NS + "/font/hud_n" + d + ".json", gson.toJson(providers(withMoves(lineFont("num.png", numRows, d, numSpace)))));
        }
        if (blank == null) return;
        for (String k : HEART_KINDS) {
            builder.addData("assets/minecraft/textures/gui/sprites/hud/heart/" + k + ".png", blank);
            builder.addData("assets/minecraft/textures/gui/sprites/hud/heart/" + k + "_blinking.png", blank);
        }
        for (String k : BAR_SPRITES) builder.addData("assets/minecraft/textures/gui/sprites/hud/" + k + ".png", blank);
    }

    /** 제공자 목록 맨 앞에 자리 옮김 글자 (U+F801 ~ U+F809 = −1 ~ −256, U+F811 ~ U+F819 = +1 ~ +256 — rpg:gui_space 와 같음) */
    private static com.google.gson.JsonArray withMoves(com.google.gson.JsonArray list) {
        com.google.gson.JsonObject adv = new com.google.gson.JsonObject();
        for (int bit = 0; bit <= 8; bit++) {
            adv.addProperty(String.valueOf((char) ('\uF801' + bit)), -(1 << bit));
            adv.addProperty(String.valueOf((char) ('\uF811' + bit)), 1 << bit);
        }
        com.google.gson.JsonObject sp = new com.google.gson.JsonObject();
        sp.addProperty("type", "space");
        sp.add("advances", adv);
        com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        out.add(sp);
        out.addAll(list);
        return out;
    }

    /**
     * 룬 뽑기 연출 (GachaCinematic — tools/vanilla_gui_cine.py): 그림 rpg:gui/cine/*.png · 글꼴 rpg:cine_* (cine.json 의 fonts — 글꼴마다 세로 자리가
     * 정해진 제공자 목록 그대로)
     */
    private static void cineFonts(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path dir, com.google.gson.Gson gson) throws IOException {
        Path meta = dir.resolve("cine.json");
        if (!Files.exists(meta)) return;
        try (var files = Files.list(dir)) {
            for (Path png : (Iterable<Path>) files::iterator) {
                String name = png.getFileName().toString();
                if (name.endsWith(".png")) builder.addData("assets/" + Mmorpg.NS + "/textures/gui/cine/" + name, Files.readAllBytes(png));
            }
        }
        com.google.gson.JsonObject fonts = com.google.gson.JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("fonts");
        for (String f : fonts.keySet()) {
            builder.addStringData("assets/" + Mmorpg.NS + "/font/" + f + ".json", gson.toJson(providers(fonts.getAsJsonArray(f))));
        }
    }

    private static com.google.gson.JsonObject bitmap(String file, com.google.gson.JsonObject files, int height, int ascent) {
        com.google.gson.JsonObject p = new com.google.gson.JsonObject();
        p.addProperty("type", "bitmap");
        p.addProperty("file", Mmorpg.NS + ":gui/" + file + ".png");
        p.addProperty("height", height);
        p.addProperty("ascent", ascent);
        com.google.gson.JsonArray chars = new com.google.gson.JsonArray();
        chars.add(files.getAsJsonObject(file).get("chars").getAsString());
        p.add("chars", chars);
        return p;
    }

    /**
     * 룬 · 뽑기권 그림의 진열용 아이템 (뽑기 창 — VanillaGacha): rpg:vbig/&lt;그림&gt; (2배 · 오른쪽 · 아래 9px — 칸 넷 사이 가운데, 진열판의 큰 그림 —
     * 칸 밖까지 그리게 oversized_in_gui), rpg:vdim/&lt;그림&gt; (어둡게 칠한 것 — 가진 것이 없는 뽑기권 · 닫힌 한정 뽑기).
     * 1배로 자리만 옮긴 그림은 칸 밖이 잘려 그려짐 (CI 42429659728 — oversized 는 크기를 키운 것만) → 반 칸 자리는 쓰지 않음
     */
    private static int bigRunes(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path root) {
        Path dir = root.resolve("assets").resolve(Mmorpg.NS).resolve("textures").resolve("item").resolve("rune");
        if (!Files.isDirectory(dir)) return 0;
        int n = 0;
        try (var files = Files.list(dir)) {
            for (Path png : (Iterable<Path>) files::iterator) {
                String name = png.getFileName().toString();
                if (!name.endsWith(".png") || name.startsWith("break_")) continue;
                String id = name.substring(0, name.length() - 4);
                String big = Mmorpg.NS + ":item/vbig/" + id;
                builder.addStringData("assets/" + Mmorpg.NS + "/models/item/vbig/" + id + ".json",
                        "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + Mmorpg.NS + ":item/rune/" + id + "\"},"
                                + "\"display\":{\"gui\":{\"rotation\":[0,0,0],\"translation\":[9,-9,0],\"scale\":[2,2,2]}}}");
                builder.addStringData("assets/" + Mmorpg.NS + "/items/vbig/" + id + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + big + "\"},\"oversized_in_gui\":true}");
                builder.addStringData("assets/" + Mmorpg.NS + "/items/vdim/" + id + ".json",
                        "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + Mmorpg.NS + ":item/" + id
                                + "\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":5263440}]}}");
                n++;
            }
        } catch (IOException e) {
            Mmorpg.LOG.warn("[vanilla] 진열용 룬 그림을 팩에 넣지 못함: {}", e.toString());
        }
        return n;
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

    private static final String[] HANDS = {"firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"};

    /**
     * 2026-11-08 꾸미기 (VanillaCosmetics): RPG 무기 · 투구 아이템 정의를 custom_model_data 글자 (서버가 그 사람의 장식으로 넣음) 로 감쌈 —
     * 무기: 스킨 키면 손 (1 · 3인칭) 에서 스킨 모델 (rpg:item/model/&lt;디자인&gt; — 모드 장식과 같은 모델), 투구: 머리 장식 모델 이름이면 머리 자리 (head —
     * 투구 그림을 뺀 투구를 바닐라가 CustomHeadLayer 로 그림) 에 그 장식 (뼈 모델 묶음 — tools/vanilla_cosmetics.py), "hide" 면 머리 자리 빈 그림.
     * 2026-11-13 무기는 스킨이 없는 계열도 감쌈: "hide" 면 손에서 빈 그림 (드라군 변신 — VanillaCosmetics.hideHands).
     * 다른 자리 (가방 · 핫바 · 땅 · 액자) 와 글자가 없을 때는 그대로. 장식 아이콘의 어두운 것 rpg:vcosdim/&lt;키&gt; (꾸미기 창 — 아직 가지지 않은 것).
     */
    private static void cosmeticItems(eu.pb4.polymer.resourcepack.api.ResourcePackBuilder builder, Path root) {
        Path items = root.resolve("assets").resolve(Mmorpg.NS).resolve("items");
        if (!Files.isDirectory(items)) return;
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
        Map<String, java.util.List<String[]>> byType = new java.util.HashMap<>();
        for (Map.Entry<String, String> e : VanillaCosmetics.skinDesigns().entrySet()) {
            mmorpg.fabric.cosmetic.CosmeticDefs.Def d = mmorpg.fabric.cosmetic.CosmeticDefs.get(e.getKey());
            byType.computeIfAbsent(d.weapon(), k -> new java.util.ArrayList<>()).add(new String[]{e.getKey(), e.getValue()});
        }
        int weapons = 0, helmets = 0;
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (!Mmorpg.NS.equals(id.getNamespace())) continue;
            Path f = items.resolve(id.getPath() + ".json");
            if (!Files.isRegularFile(f)) continue;
            com.google.gson.JsonArray cases = new com.google.gson.JsonArray();
            com.google.gson.JsonObject def;
            try {
                def = com.google.gson.JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                continue;
            }
            com.google.gson.JsonElement original = def.get("model");
            if (item instanceof mmorpg.fabric.RpgWeaponItem) {
                java.util.List<String[]> skins = byType.get(VanillaCosmetics.weaponType(item));
                if (skins != null) {
                    for (String[] s : skins) {
                        cases.add(when(s[0], context(HANDS, model(Mmorpg.NS + ":item/model/" + s[1]), original)));
                    }
                }
                // 2026-11-13 드라군 변신 (VanillaCosmetics.hideHands — 사람이 용이 된 동안 내 화면): "hide" 면 손 (1 · 3인칭) 에서 빈 그림
                com.google.gson.JsonObject none = new com.google.gson.JsonObject();
                none.addProperty("type", "minecraft:empty");
                cases.add(when("hide", context(HANDS, none, original)));
                weapons++;
            } else if (item instanceof mmorpg.fabric.gear.RpgArmorItem) {
                net.minecraft.world.item.equipment.Equippable eq = item.components().get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                if (eq == null || eq.slot() != net.minecraft.world.entity.EquipmentSlot.HEAD) continue;
                for (Map.Entry<String, java.util.List<String>> h : VanillaCosmetics.headModels().entrySet()) {
                    com.google.gson.JsonArray parts = new com.google.gson.JsonArray();
                    for (String m : h.getValue()) parts.add(model(m));
                    com.google.gson.JsonObject composite = new com.google.gson.JsonObject();
                    composite.addProperty("type", "minecraft:composite");
                    composite.add("models", parts);
                    cases.add(when(h.getKey(), context(new String[]{"head"}, composite, original)));
                }
                com.google.gson.JsonObject empty = new com.google.gson.JsonObject();
                empty.addProperty("type", "minecraft:empty");
                cases.add(when("hide", context(new String[]{"head"}, empty, original)));
                helmets++;
            } else {
                continue;
            }
            com.google.gson.JsonObject sel = new com.google.gson.JsonObject();
            sel.addProperty("type", "minecraft:select");
            sel.addProperty("property", "minecraft:custom_model_data");
            sel.addProperty("index", 0);
            sel.add("cases", cases);
            sel.add("fallback", original);
            def.add("model", sel);
            builder.addStringData("assets/" + Mmorpg.NS + "/items/" + id.getPath() + ".json", gson.toJson(def));
        }
        int dim = 0;
        for (mmorpg.fabric.cosmetic.CosmeticDefs.Def d : mmorpg.fabric.cosmetic.CosmeticDefs.all().values()) {
            builder.addStringData("assets/" + Mmorpg.NS + "/items/vcosdim/" + d.id() + ".json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + Mmorpg.NS + ":item/cosmetic/" + d.id()
                            + "\",\"tints\":[{\"type\":\"minecraft:constant\",\"value\":5263440}]}}");
            dim++;
        }
        Mmorpg.LOG.info("[vanilla] cosmetics: weapon definitions {}, helmet definitions {}, dim icons {}", weapons, helmets, dim);
    }

    private static com.google.gson.JsonObject model(String id) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("type", "minecraft:model");
        o.addProperty("model", id);
        return o;
    }

    /** select 의 경우 하나 (when = 글자) */
    private static com.google.gson.JsonObject when(String value, com.google.gson.JsonElement model) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("when", value);
        o.add("model", model);
        return o;
    }

    /** 그 자리 (display_context) 들에서만 model, 나머지는 fallback */
    private static com.google.gson.JsonObject context(String[] contexts, com.google.gson.JsonElement model, com.google.gson.JsonElement fallback) {
        com.google.gson.JsonArray w = new com.google.gson.JsonArray();
        for (String c : contexts) w.add(c);
        com.google.gson.JsonObject one = new com.google.gson.JsonObject();
        one.add("when", w);
        one.add("model", model);
        com.google.gson.JsonArray cases = new com.google.gson.JsonArray();
        cases.add(one);
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("type", "minecraft:select");
        o.addProperty("property", "minecraft:display_context");
        o.add("cases", cases);
        o.add("fallback", fallback.deepCopy());
        return o;
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
