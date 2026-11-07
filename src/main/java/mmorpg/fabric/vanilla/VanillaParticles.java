package mmorpg.fabric.vanilla;

import eu.pb4.polymer.core.api.other.PolymerParticleType;
import mmorpg.fabric.AddonParticleOptions;
import mmorpg.fabric.FxOptions;
import mmorpg.fabric.FxSprite;
import mmorpg.fabric.RpgParticles;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * RPG 그림 파티클 (mmorpg:fx — 빛줄기 · 마법진 · 참격 · 빛 알갱이, mmorpg:addon — 애드온 파티클 331) 의 바닐라 대체 (1단계 — 비슷한 바닐라 파티클).
 * 참격 가운데는 휩쓸기, 테두리는 치명타 불티, 고리 · 터짐은 폭발 연기, 나머지는 색에 가까운 바닐라 파티클 (빨강 · 주황 불꽃 · 초록 · 하늘빛
 * 영혼 불꽃 · 보라 마녀 · 흰 끝막대 · 어두운 연기). 5단계에서 큰 그림은 표시 엔티티로 같은 그림을 띄움. 모드 클라이언트에는 그대로.
 */
final class VanillaParticles {
    private VanillaParticles() {}

    static void init() {
        PolymerParticleType.setOverlay(RpgParticles.FX, new PolymerParticleType<FxOptions>() {
            @Override
            public ParticleOptions getPolymerParticleReplacement(FxOptions o, PacketContext ctx) {
                return VanillaClients.isModded(ctx) ? o : of(o);
            }

            @Override
            public ParticleType<?> getPolymerReplacement(ParticleType<?> type, PacketContext ctx) {
                return VanillaClients.isModded(ctx) ? type : ParticleTypes.END_ROD;
            }

            @Override
            public boolean canSyncRawToClient(PacketContext ctx) {
                return VanillaClients.isModded(ctx);
            }
        });
        PolymerParticleType.setOverlay(RpgParticles.ADDON, new PolymerParticleType<AddonParticleOptions>() {
            @Override
            public ParticleOptions getPolymerParticleReplacement(AddonParticleOptions o, PacketContext ctx) {
                return VanillaClients.isModded(ctx) ? o : ParticleTypes.CRIT;
            }

            @Override
            public ParticleType<?> getPolymerReplacement(ParticleType<?> type, PacketContext ctx) {
                return VanillaClients.isModded(ctx) ? type : ParticleTypes.CRIT;
            }

            @Override
            public boolean canSyncRawToClient(PacketContext ctx) {
                return VanillaClients.isModded(ctx);
            }
        });
    }

    /** 그림 파티클 하나 → 바닐라 파티클 */
    static ParticleOptions of(FxOptions o) {
        if (o.kind() == FxOptions.SPRITE) {
            FxSprite s = FxSprite.byId(o.sprite());
            if (s == null) return byColor(o.color());
            return switch (s) {
                case SHOCK_RING, BASH_RING, BASH_BURST -> ParticleTypes.EXPLOSION;
                case SLASH_X -> ParticleTypes.SWEEP_ATTACK;
                default -> s.name().endsWith("_CORE") ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.CRIT;
            };
        }
        if (o.kind() == FxOptions.ARROW_SHOT) return ParticleTypes.END_ROD;
        if (o.kind() == FxOptions.ARROW_SPARK) return ParticleTypes.CRIT;
        if (o.kind() == FxOptions.GLINT) {
            String tex = o.sprite() >= 0 && o.sprite() < FxOptions.GLINT_TEXTURES.length ? FxOptions.GLINT_TEXTURES[o.sprite()] : "";
            if (tex.equals("flame")) return ParticleTypes.FLAME;
            if (tex.equals("twinkle")) return ParticleTypes.END_ROD;
            if (tex.equals("heart")) return ParticleTypes.HEART;
        }
        return byColor(o.color());
    }

    /** ARGB 색에 가까운 바닐라 파티클 */
    static ParticleOptions byColor(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255F, g = ((argb >> 8) & 0xFF) / 255F, b = (argb & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float sat = max <= 0 ? 0 : (max - min) / max;
        if (sat < 0.25F) return max < 0.35F ? ParticleTypes.SMOKE : ParticleTypes.END_ROD;
        float hue;
        if (max == r) hue = 60 * (((g - b) / (max - min)) % 6);
        else if (max == g) hue = 60 * ((b - r) / (max - min) + 2);
        else hue = 60 * ((r - g) / (max - min) + 4);
        if (hue < 0) hue += 360;
        if (hue < 50 || hue >= 330) return ParticleTypes.FLAME;
        if (hue < 70) return ParticleTypes.END_ROD;
        if (hue < 165) return ParticleTypes.HAPPY_VILLAGER;
        if (hue < 250) return ParticleTypes.SOUL_FIRE_FLAME;
        return ParticleTypes.WITCH;
    }
}
