package mmorpg.fabric.gear;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import mmorpg.core.Relics;
import mmorpg.fabric.BedrockText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * 0.43.0 아티팩트 아이템 (신화 유물 열 × 등급 다섯 — core {@link Relics}): 이름은 등급 색 + (강화 "+N ") + "메긴교르드", 설명은 별칭 · 등급 · 착용 레벨 · 계열 ·
 * 패시브 (단계마다) · 연결 안내 · 이 등급 (강화 포함) 의 스탯 줄 값 · 설명 · 쓰는 법. 들고 우클릭하면 판의 빈 칸에 꽂음
 * ({@link mmorpg.fabric.relic.RelicSystem#equipHeld}). 강화 단계는 무기와 같은 구성 요소 mmorpg:enhance ({@link WeaponEnhance}).
 */
public final class RelicItem extends Item {
    /** 클라이언트: 지금 렙제 레벨 (모르면 -1) — 설명의 착용 레벨 색 */
    public static volatile DoubleSupplier viewer = () -> -1;

    private final Relics.Relic relic;

    public RelicItem(Relics.Relic relic, Item.Properties properties) {
        super(properties);
        this.relic = relic;
    }

    public Relics.Relic relic() {
        return relic;
    }

    /** 이 칸의 아이템이 아티팩트면 그것 (아니면 null) */
    public static Relics.Relic of(ItemStack stack) {
        return stack != null && stack.getItem() instanceof RelicItem r ? r.relic : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(relic.coloredName(WeaponEnhance.of(stack))));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        double lv;
        try {
            lv = viewer.getAsDouble();
        } catch (RuntimeException e) {
            lv = -1;
        }
        for (String line : Relics.lore(relic, WeaponEnhance.of(stack), lv)) tooltip.accept(Component.literal(BedrockText.toJava(line)));
        tooltip.accept(Component.literal(BedrockText.toJava("§r§8우클릭: 판의 빈 칸에 · I 키: 아티팩트 판")));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) mmorpg.fabric.relic.RelicSystem.equipHeld(sp, hand);
        return InteractionResult.CONSUME;
    }
}
