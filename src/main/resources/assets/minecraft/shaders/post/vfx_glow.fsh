#version 330
#extension GL_ARB_separate_shader_objects : require

// 2026-11-15 VFX glow, last pass (post_effect/entity_outline.json).
// In    = wide gaussian of the glowing-entity silhouette (premultiplied rgb, normalised alpha)
// Tight = narrow gaussian of the same silhouette
// Mask  = the silhouette before blurring (the glow stays outside the model)
// The result is alpha-blended over the world by the vanilla outline blit.
// Comments stay ASCII: some GL drivers reject non-ASCII bytes even inside comments.
// The extension line is required for "layout(location = N) in" in a 330 fragment shader
// (CI 43663823750: glslang "location qualifier on input" -> post chain failed -> client disconnected).
// Same header as the vanilla 26.3 post shaders (CI shader probe: "#extension GL_ARB_separate_shader_objects : require").
uniform sampler2D InSampler;
uniform sampler2D TightSampler;
uniform sampler2D MaskSampler;

layout(std140) uniform GlowConfig {
    vec4 GlowParams; // x = tight gain, y = wide gain, z = how much of the glow inside the silhouette is removed, w = white-hot rim
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec4 w = texture(InSampler, texCoord);
    vec4 t = texture(TightSampler, texCoord);
    float m = texture(MaskSampler, texCoord).a;
    float cov = w.a + t.a;
    vec3 c = cov > 0.0001 ? (w.rgb + t.rgb) / cov : vec3(0.0);
    float at = clamp(t.a * GlowParams.x, 0.0, 1.0);
    float aw = clamp(w.a * GlowParams.y, 0.0, 1.0);
    float a = 1.0 - (1.0 - at) * (1.0 - aw);
    c = mix(c, vec3(1.0, 0.95, 0.88), clamp(at * GlowParams.w, 0.0, 1.0));
    a *= 1.0 - clamp(m * GlowParams.z, 0.0, 1.0);
    fragColor = vec4(c, a);
}
