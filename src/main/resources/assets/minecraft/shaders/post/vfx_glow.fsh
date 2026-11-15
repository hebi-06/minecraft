#version 330

// 2026-11-15 발광 외곽선 → 부드러운 빛 번짐 (블룸 흉내 — 사용자 "너가 추천한거 다 넣어서 시범으로 만들어봐", post_effect/entity_outline.json):
// In = 실루엣을 네 번 흐린 것 (rgb = 발광 색 × 덮인 몫 · a = 덮인 몫), Mask = 흐리기 전 실루엣. 몸 바깥에만 빛 (몸 안은 Mask 로 지움 — 모델을 덮지 않게)
uniform sampler2D InSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform GlowConfig {
    vec4 GlowParams; // x = 세기 · y = 몸 안 지우는 몫
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 b = texture(InSampler, texCoord);
    float m = texture(MaskSampler, texCoord).a;
    vec3 c = b.a > 0.0001 ? b.rgb / b.a : vec3(0.0);
    float a = clamp(b.a * GlowParams.x, 0.0, 1.0);
    a = a * a * (3.0 - 2.0 * a);
    a *= 1.0 - clamp(m * GlowParams.y, 0.0, 1.0);
    fragColor = vec4(c, a);
}
