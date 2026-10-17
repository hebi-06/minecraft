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

    /** 시험용: 모델 이름 → 움직이는 동안 (나이 0 ~ 44틱 · 걷기 0 · 0.5 · 1) 가장 앞 꼭짓점 z (픽셀 — 몸통 등 면 2 이상이면 몸을 뚫지 않음) */
    public static float frontmostZ(String model) {
        PieceModel m = MODELS.get(model);
        if (m == null) return Float.NaN;
        float min = Float.MAX_VALUE;
        for (float speed : new float[] {0F, 0.5F, 1F}) {
            for (int i = 0; i < 120; i++) min = Math.min(min, m.frontmostZ(i * 0.37F, speed));
        }
        return min;
    }

    /** 시험용: 모델 이름 → 반투명인지 */
    public static boolean translucent(String model) {
        PieceModel m = MODELS.get(model);
        return m != null && m.translucent();
    }
}
