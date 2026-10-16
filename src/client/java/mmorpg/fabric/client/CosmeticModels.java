package mmorpg.fabric.client;

import java.util.HashMap;
import java.util.Map;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.cosmetic.CosmeticDefs;

/** 장식 모델 (갑옷 스킨 · 머리 · 등 장식 — assets/mmorpg/cosmetic/&lt;model&gt;.json, tools/cosmetic_art.py 가 만듦): 클라이언트 시작 때 모두 읽음 */
public final class CosmeticModels {
    private CosmeticModels() {}

    private static final Map<String, PieceModel> MODELS = new HashMap<>();

    static void init() {
        for (CosmeticDefs.Def d : CosmeticDefs.all().values()) {
            if (d.model() == null || MODELS.containsKey(d.model())) continue;
            PieceModel m = PieceModel.load("/assets/mmorpg/cosmetic/" + d.model() + ".json");
            if (m != null) MODELS.put(d.model(), m);
            else Mmorpg.LOG.warn("[mmorpg] cosmetic model {} missing", d.model());
        }
        Mmorpg.LOG.info("MMO RPG: cosmetic models {}", MODELS.size());
    }

    /** 장식의 모델 (모델이 없는 장식 · 없으면 null) */
    public static PieceModel of(CosmeticDefs.Def d) {
        return d == null || d.model() == null ? null : MODELS.get(d.model());
    }

    /** 시험용: 읽은 모델 수 */
    public static int count() {
        return MODELS.size();
    }

    /** 시험용: 모델 이름 → 움직이는 뼈 수 */
    public static int animCount(String model) {
        PieceModel m = MODELS.get(model);
        return m == null ? -1 : m.animCount();
    }
}
