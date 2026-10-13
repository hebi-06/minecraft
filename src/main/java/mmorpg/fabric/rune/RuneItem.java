package mmorpg.fabric.rune;

import java.util.function.Consumer;
import mmorpg.core.RuneBreak;
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
 * 룬 아이템 하나 (rpg:rune_&lt;계열&gt;_&lt;등급&gt;): 이름 · 설명은 규칙 코어 (Runes.runeLore — 돌파 단계가 있으면 RuneBreak.lore) 그대로 (조작 줄만 자바 판으로).
 * 룬은 스킬 칸 (11단계 — SkillBar) 에 올려 키로만 씀 (요구 스탯 없음 — RuneCast): 들고 우클릭하면 쓰지 않고 빈 스킬 칸에 올림
 * (같은 룬이 칸에 있으면 바꿔 끼움). 돌파 단계 (8단계 — Breakthrough) 는 이름 뒤 "[3돌]" · 설명 · 위력에.
 * 재사용 대기는 가방의 같은 룬 아이콘에도 쓸기로 보임 (아이템 재사용 대기).
 */
public final class RuneItem extends Item {
    private final Runes.Rune rune;

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
        // 돌파 단계의 설명 (8단계 — 돌파 줄 · 단계별 값 · 초월 3 · 6돌 줄)
        for (String line : RuneBreak.lore(rune, Breakthrough.of(stack), false)) {
            // 조작 줄: 애드온의 터치 · 누르기 (모바일) → 자바 (스킬 칸에 올려 키로만 — 우클릭은 올리기)
            String l = line.replace("터치: 칸을 고르면 바로 사용", "우클릭: 빈 스킬 칸에 올리기").replace("누르면 사용 · 요구 스탯 없음", "스킬 칸에 올려 키로 사용 · 요구 스탯 없음");
            tooltip.accept(Component.literal(BedrockText.toJava(l)));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) SkillBar.equipHeld(sp, hand); // 쓰지 않고 스킬 칸에 올림
        return InteractionResult.CONSUME;
    }
}
