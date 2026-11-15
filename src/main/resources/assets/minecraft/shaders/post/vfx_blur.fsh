#version 330
#extension GL_ARB_separate_shader_objects : require

// 2026-11-15 VFX glow, pass 2: separable gaussian blur (post_effect/entity_outline.json).
// Pilot v1 used the vanilla entity_outline_box_blur: its alpha is not normalised, so the glow became a
// thin dark-red outline (CI 43665812727). This blur keeps rgb premultiplied and alpha normalised,
// so the composite pass (vfx_glow.fsh) gets a soft coverage falloff.
// Comments stay ASCII: some GL drivers reject non-ASCII bytes even inside comments.
// Same header as the vanilla 26.3 post shaders.
// 2026-11-15 cost: the chain blurs into fixed low-resolution targets (tight 960x540, wide 480x270 -
// PostChainConfig.InternalTarget width/height). Full-screen blurs took about 240M texture reads per frame
// at 1080p (a software renderer drew one frame per second, CI 43672651858); now about 23M.
// Reading a larger input into a smaller output is a 2x box downsample (bilinear at texel corners).
// Sigma stays in screen pixels at 1080 lines whatever the target size (InSize.y / 1080).
uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform VfxBlur {
    vec4 BlurParams; // xy = direction (1,0 or 0,1), z = sigma in pixels at 1080 lines, w = unused
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / InSize;
    vec2 dir = BlurParams.xy * texel;
    // keep the glow the same size on screen at any resolution
    float sigma = max(BlurParams.z * InSize.y / 1080.0, 0.75);
    float radius = min(ceil(sigma * 3.0), 96.0);
    float inv = 1.0 / (2.0 * sigma * sigma);
    vec4 sum = texture(InSampler, texCoord);
    float wsum = 1.0;
    // linear sampling: texels i and i+1 merged into one bilinear tap
    for (int k = 0; k < 48; k++) {
        float i = float(k) * 2.0 + 1.0;
        if (i > radius) break;
        float wa = exp(-i * i * inv);
        float wb = exp(-(i + 1.0) * (i + 1.0) * inv);
        float w = wa + wb;
        float off = (i * wa + (i + 1.0) * wb) / w;
        sum += texture(InSampler, texCoord + dir * off) * w;
        sum += texture(InSampler, texCoord - dir * off) * w;
        wsum += 2.0 * w;
    }
    fragColor = sum / wsum;
}
