package mmorpg.fabric.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;

/**
 * 퀘스트 편집에 쓰는 이름 목록 (names.js — 한국어 표시 이름 ↔ ID). 목록에 없는 몹 · 아이템은 편집 창에서 ID 를 직접 입력.
 * 몬스터 전리품은 편집 창이 MONSTER_DROPS 에서 바로 보여 줌.
 */
public final class Names {
    private Names() {}

    /** 이름 · ID 한 쌍 */
    public record Named(String name, String id) {}

    /** 처치 퀘스트에서 고를 수 있는 몹 (RPG 몬스터만, 레벨 순 — names.js MOBS) */
    public static final List<Named> MOBS;

    static {
        Obj monsters = Rules.get().obj("MONSTERS");
        List<Named> l = new ArrayList<>();
        for (String k : Rules.get().strings("MONSTER_ORDER")) {
            Obj m = monsters.obj(k);
            l.add(new Named(m.str("name") + " (Lv." + Js.str(m.num("level")) + ")", monsterId(k)));
        }
        MOBS = List.copyOf(l);
    }

    /** 자주 쓰는 아이템의 표시 이름 (names.js ITEM_NAMES — 베드락 ID) */
    public static final Map<String, String> ITEM_NAMES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        String[][] list = {
            {"minecraft:iron_ingot", "철 주괴"}, {"minecraft:gold_ingot", "금 주괴"}, {"minecraft:copper_ingot", "구리 주괴"},
            {"minecraft:diamond", "다이아몬드"}, {"minecraft:emerald", "에메랄드"}, {"minecraft:coal", "석탄"},
            {"minecraft:redstone", "레드스톤 가루"}, {"minecraft:lapis_lazuli", "청금석"}, {"minecraft:bread", "빵"},
            {"minecraft:wheat", "밀"}, {"minecraft:carrot", "당근"}, {"minecraft:potato", "감자"}, {"minecraft:apple", "사과"},
            {"minecraft:bone", "뼈"}, {"minecraft:rotten_flesh", "썩은 살점"}, {"minecraft:string", "실"},
            {"minecraft:gunpowder", "화약"}, {"minecraft:spider_eye", "거미 눈"}, {"minecraft:leather", "가죽"},
            {"minecraft:feather", "깃털"}, {"minecraft:arrow", "화살"}, {"minecraft:ender_pearl", "엔더 진주"},
            {"minecraft:slime_ball", "슬라임볼"}, {"minecraft:blaze_rod", "블레이즈 막대"}, {"minecraft:oak_log", "참나무 원목"},
            {"minecraft:cobblestone", "조약돌"}, {"minecraft:beef", "생소고기"}, {"minecraft:porkchop", "생돼지고기"},
            {"minecraft:chicken", "생닭고기"}, {"minecraft:egg", "달걀"}, {"minecraft:white_wool", "흰색 양털"},
        };
        for (String[] e : list) m.put(e[0], e[1]);
        ITEM_NAMES = Collections.unmodifiableMap(m);
    }

    /** RPG 몬스터 ID (config.js monsterId — rpg:&lt;키&gt;) */
    public static String monsterId(String key) {
        return "rpg:" + key;
    }

    /** "minecraft:iron_ingot" → "iron_ingot" */
    public static String shortId(String id) {
        String s = id == null ? "" : id;
        int i = s.indexOf(':');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    /** 입력한 ID 정리: 공백 제거, 소문자, 이름공간이 없으면 minecraft: */
    public static String normalizeId(String text) {
        String s = (text == null ? "" : text).strip().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return "";
        return s.contains(":") ? s : "minecraft:" + s;
    }

    /** 같은 몹의 예전 · 새 ID (_v2) 를 하나로 봄 */
    public static String mobKey(String id) {
        return (id == null ? "" : id).replaceAll("_v2$", "");
    }

    /** 몹 이름 (목록에 없으면 ID 의 뒷부분) */
    public static String mobName(String id) {
        String k = mobKey(id);
        for (Named m : MOBS) if (mobKey(m.id()).equals(k)) return m.name();
        return shortId(id);
    }

    /** 아이템 이름 (붙인 이름 → 표시 이름 → ID 의 뒷부분 — names.js itemName) */
    public static String itemName(String typeId, String nameTag) {
        if (nameTag != null && !nameTag.isEmpty()) return nameTag;
        String n = ITEM_NAMES.get(typeId);
        return n != null ? n : shortId(typeId);
    }
}
