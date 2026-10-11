package mmorpg.fabric.rune;

import java.util.List;
import java.util.function.Consumer;
import mmorpg.core.Js;
import mmorpg.core.Runes;
import mmorpg.core.StatTree;
import mmorpg.core.Stats;
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
 * 룬 뽑기권 (rpg:rune_ticket) · 고급 룬 뽑기권 (rpg:high_rune_ticket — 일반 룬 없음) · 스탯 룬 뽑기권 (7단계 스탯 트리 — rpg:str_rune_ticket …:
 * 이름이 같은 계열 5종만, 등급 확률은 룬 뽑기권과 같음): 우클릭하면 룬 하나를 뽑음 (RuneGacha)
 */
public final class TicketItem extends Item {
    private final boolean adv;
    /** 스탯 룬 뽑기권이면 그 스탯 (나오는 계열은 StatTree.tickets) — 아니면 null */
    private final String stat;

    public TicketItem(boolean adv, Item.Properties properties) {
        this(adv, null, properties);
    }

    public TicketItem(boolean adv, String stat, Item.Properties properties) {
        super(properties);
        this.adv = adv;
        this.stat = stat;
    }

    public boolean advanced() {
        return adv;
    }

    /** 나오는 계열 (스탯 룬 뽑기권 — 아니면 null = 16계열 모두) */
    public String family() {
        if (stat == null) return null;
        StatTree.Ticket t = StatTree.tickets().get(stat);
        return t == null ? null : t.family();
    }

    public String stat() {
        return stat;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (stat != null) {
            StatTree.Ticket t = StatTree.tickets().get(stat);
            String name = t == null ? stat + " 룬 뽑기권" : t.name();
            return Component.literal(BedrockText.toJava(Stats.info(stat).str("color") + "§l" + name + "§r§7 (누르면 " + Stats.info(stat).str("name") + " 룬 뽑기)"));
        }
        return Component.literal(BedrockText.toJava(adv ? "§6§l고급 룬 뽑기권§r§7 (누르면 룬 뽑기 · 일반 없음)" : "§d§l룬 뽑기권§r§7 (누르면 룬 뽑기)"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (stat != null) {
            String fam = family();
            mmorpg.core.Obj f = fam == null ? null : mmorpg.core.Rules.get().obj("RUNE_FAMILIES").objOrNull(fam);
            String famName = f == null ? stat : f.str("name");
            tooltip.accept(Component.literal(BedrockText.toJava("§r§7스탯 트리에서 얻는 뽑기권")));
            tooltip.accept(Component.literal(BedrockText.toJava("§r§7들고 우클릭하면 " + famName + " 룬 하나를 뽑습니다")));
            var w = Runes.gradeWeights(adv);
            tooltip.accept(Component.literal(BedrockText.toJava("§r§7일반 " + Js.str(w.get("common")) + "% · 희귀 " + Js.str(w.get("rare")) + "% · 고귀 " + Js.str(w.get("noble")) + "%")));
            tooltip.accept(Component.literal(BedrockText.toJava("§r§7영웅 " + Js.str(w.get("hero")) + "% · 초월 " + Js.str(w.get("transcend")) + "%")));
            tooltip.accept(Component.literal(BedrockText.toJava("§r§7" + famName + " 계열 5종 중 하나 (다른 계열은 나오지 않음)")));
            return;
        }
        List<Object> lore = RuneKit.RUNE.list(adv ? "advTicketLore" : "ticketLore");
        for (Object line : lore) tooltip.accept(Component.literal(BedrockText.toJava(String.valueOf(line).replace("들고 누르면", "들고 우클릭하면"))));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) RuneGacha.useTicket(sp, this);
        return InteractionResult.CONSUME;
    }
}
