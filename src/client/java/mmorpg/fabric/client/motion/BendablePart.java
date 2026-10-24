package mmorpg.fabric.client.motion;

/**
 * 0.38.0 팔꿈치 · 무릎이 꺾이는 부위 (ModelPartMixin 이 ModelPart 에 붙임): 굽힘 각 (라디안 — 로컬 x 축, 0 = 곧음) 과 관절 자리 (부위 좌표, 픽셀).
 * 굽힘이 0 이 아니면 그 부위와 아래 부위 (소매 · 갑옷) 의 꼭짓점을 관절 아래만 돌려 그림 (BendMesh — 관절은 이음매 없이 비스듬히 맞붙음).
 */
public interface BendablePart {
    void mmorpg$setBend(float angle, float jx, float jy, float jz);

    float mmorpg$bend();

    float mmorpg$jointX();

    float mmorpg$jointY();

    float mmorpg$jointZ();

    /** 이 부위의 첫 상자 (바탕 피부) 의 x · z 가운데 (관절 자리 — 상자가 없으면 NaN) */
    float[] mmorpg$center();
}
