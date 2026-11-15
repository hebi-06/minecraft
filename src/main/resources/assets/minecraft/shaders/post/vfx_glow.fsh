#version 330
#extension GL_ARB_separate_shader_objects : enable

// 2026-11-15 VFX glow (post_effect/entity_outline.json, last pass).
// In   = glowing-entity silhouette blurred four times (rgb = glow colour * coverage, a = coverage)
// Mask = the silhouette before blurring (used to keep the glow outside the model)
// Comments stay ASCII: some GL drivers reject non-ASCII bytes even inside comments.
// The extension line is required for "layout(location = N) in" in a 330 fragment shader
// (CI 43663823750: glslang "location qualifier on input" -> post chain failed -> client disconnected).
uniform sampler2D InSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform GlowConfig {
    vec4 GlowParams; // x = strength, y = how much of the glow inside the silhouette is removed
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
