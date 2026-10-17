package mmorpg.fabric.rune;

import java.util.function.Consumer;
import java.util.function.Supplier;
import mmorpg.core.RuneTip;
import mmorpg.core.Runes;
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
 * 룬 아이템 하나 (rpg:rune_&lt;계열&gt;_&lt;등급&gt;): 설명은 규칙 코어 RuneTip (0.16.1 — 총 피해 · 회복을 보는 사람의 지금 스탯으로 계산한 숫자,
 * 계수 줄 · 돌파 줄, 연출 · 용도 · 공식 줄 없음 — 대조 시험의 Runes.runeLore 는 애드온 그대로 둠). 숫자에 쓰는 스탯은 {@link #viewer} (클라이언트).
 * 룬은 스킬 칸 (11단계 — SkillBar) 에 올려 키로만 씀 (요구 스탯 없음 — RuneCast): 들고 우클릭하면 쓰지 않고 빈 스킬 칸에 올림
 * (같은 룬이 칸에 있으면 바꿔 끼움). 돌파 단계 (8단계 — Breakthrough) 는 이름 뒤 "[3돌]" · 설명 · 위력에.
 * 재사용 대기는 가방의 같은 룬 아이콘에도 쓸기로 보임 (아이템 재사용 대기).
 */
public final class RuneItem extends Item {
    private final Runes.Rune rune;
    /** 툴팁 숫자를 계산할 보는 사람 (클라이언트가 정함 — MmorpgClient: 받은 스탯 · 스탯 트리 · 최대 체력). null 이면 "위력×N" · 비율로 */
    public static volatile Supplier<RuneTip.Viewer> viewer = () -> null;

    public RuneItem(Runes.Rune rune, Item.Properties properties) {
        super(properties);
        this.rune = rune;
    }

    public Runes.Rune rune() {
        return rune;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(rune.gradeColor() + rune.def().str("name") + Breakthrough.nameTag(Breakthrough.of(stack)) + "§r§7 · "
                + rune.famName() + " 룬 (" + rune.gradeName() + ")"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        // 돌파 단계의 설명 (총 피해 · 회복 숫자 = 보는 사람의 지금 스탯, 돌파 줄 · 초월 3 · 6돌 줄)
        RuneTip.Viewer v;
        try {
            v = viewer.get();
        } catch (RuntimeException e) {
            v = null;
        }
        for (String line : RuneTip.lines(rune, Breakthrough.of(stack), v)) tooltip.accept(Component.literal(BedrockText.toJava(line)));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) SkillBar.equipHeld(sp, hand); // 쓰지 않고 스킬 칸에 올림
        return InteractionResult.CONSUME;
    }
}
