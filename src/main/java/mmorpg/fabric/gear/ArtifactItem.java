package mmorpg.fabric.gear;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import mmorpg.core.Artifacts;
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
 * 0.37.0 아티팩트 아이템 (종류 넷 × 등급 다섯 — core {@link Artifacts}): 이름은 등급 색 + "청옥 반지", 설명은 종류 · 칸 · 등급 · 착용 레벨 · 옵션 둘 ·
 * 쓰는 법. 들고 우클릭하면 아티팩트 칸에 낌 ({@link mmorpg.fabric.artifact.ArtifactSystem#equipHeld} — 반지는 빈 반지 칸, 둘 다 차 있으면 낮은 등급과 바꿈).
 * 설명의 "지금 레벨" 은 {@link #viewer} (클라이언트가 렙제 레벨을 줌 — 모르면 음수).
 */
public final class ArtifactItem extends Item {
    /** 클라이언트: 지금 렙제 레벨 (모르면 -1) — 설명의 착용 레벨 색 */
    public static volatile DoubleSupplier viewer = () -> -1;

    private final Artifacts.Art art;

    public ArtifactItem(Artifacts.Art art, Item.Properties properties) {
        super(properties);
        this.art = art;
    }

    public Artifacts.Art art() {
        return art;
    }

    /** 이 칸의 아이템이 아티팩트면 그것 (아니면 null) */
    public static Artifacts.Art of(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ArtifactItem a ? a.art : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(art.coloredName()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        double lv;
        try {
            lv = viewer.getAsDouble();
        } catch (RuntimeException e) {
            lv = -1;
        }
        for (String line : Artifacts.lore(art, lv)) tooltip.accept(Component.literal(BedrockText.toJava(line)));
        tooltip.accept(Component.literal(BedrockText.toJava("§r§8우클릭: 끼기 · I 키: 아티팩트 창")));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) mmorpg.fabric.artifact.ArtifactSystem.equipHeld(sp, hand);
        return InteractionResult.CONSUME;
    }
}
