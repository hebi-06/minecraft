package mmorpg.fabric.rune;

import java.util.List;
import java.util.function.Consumer;
import mmorpg.core.Gacha;
import mmorpg.core.Js;
import mmorpg.core.Runes;
import mmorpg.core.StatTree;
import mmorpg.core.Stats;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.gacha.GachaSystem;
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
 * 이름이 같은 계열 5종만, 등급 확률은 룬 뽑기권과 같음) · 한정 룬 뽑기권 (0.14.0 — rpg:limited_rune_ticket, 관리자가 연 한정 뽑기에만):
 * 우클릭하면 뽑기 창 (1회 · 10회 — GachaSystem, 모드가 없는 클라이언트는 예전처럼 눈앞 마법진으로 1회 — RuneGacha)
 */
public final class TicketItem extends Item {
    /** normal · advanced · stat · limited */
    private final String kind;
    /** 스탯 룬 뽑기권이면 그 스탯 (나오는 계열은 StatTree.tickets) — 아니면 null */
    private final String stat;

    public TicketItem(boolean adv, Item.Properties properties) {
        this(adv, null, properties);
    }

    public TicketItem(boolean adv, String stat, Item.Properties properties) {
        this(stat != null ? "stat" : adv ? "advanced" : "normal", stat, properties);
    }

    public TicketItem(String kind, String stat, Item.Properties properties) {
        super(properties);
        this.kind = kind;
        this.stat = stat;
    }

    public boolean advanced() {
        return "advanced".equals(kind);
    }

    /** 한정 룬 뽑기권 (0.14.0) */
    public boolean limited() {
        return "limited".equals(kind);
    }

    public String kind() {
        return kind;
    }

    /** 나오는 계열 (스탯 룬 뽑기권 — 아니면 null = 모든 계열) */
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
        if (limited()) return Component.literal(BedrockText.toJava("§c§l" + Gacha.ticket().str("name") + "§r§7 (누르면 한정 뽑기)"));
        return Component.literal(BedrockText.toJava(advanced() ? "§6§l고급 룬 뽑기권§r§7 (누르면 룬 뽑기 · 일반 없음)" : "§d§l룬 뽑기권§r§7 (누르면 룬 뽑기)"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (stat != null) {
            String fam = family();
            mmorpg.core.Obj f = fam == null ? null : mmorpg.core.Rules.get().obj("RUNE_FAMILIES").objOrNull(fam);
            String famName = f == null ? stat : f.str("name");
            tooltip.accept(line("§r§7스탯 트리에서 얻는 뽑기권"));
            tooltip.accept(line("§r§7들고 우클릭하면 " + famName + " 룬 뽑기 창 (1회 · 10회)"));
            var w = Runes.gradeWeights(false);
            tooltip.accept(line("§r§7일반 " + Js.str(w.get("common")) + "% · 희귀 " + Js.str(w.get("rare")) + "% · 고귀 " + Js.str(w.get("noble")) + "%"));
            tooltip.accept(line("§r§7영웅 " + Js.str(w.get("hero")) + "% · 초월 " + Js.str(w.get("transcend")) + "%"));
            tooltip.accept(line("§r§7" + famName + " 계열 5종 중 하나 (다른 계열은 나오지 않음)"));
            return;
        }
        if (limited()) {
            Gacha.Pity p = Gacha.defaultPity();
            tooltip.accept(line("§r§7관리자가 연 §d한정 뽑기§7에서 씀"));
            tooltip.accept(line("§r§7픽업 초월 · 영웅 확률 UP (반천장 · 놓치면 다음은 확정)"));
            tooltip.accept(line("§r§7천장: 초월 " + p.hard() + "회 · 영웅 " + p.heroEvery() + "회 확정 (한정 뽑기끼리 이어짐)"));
            tooltip.accept(line("§r§7룬술사에게서 룬 조각으로 (한정 뽑기 기간) · 관리자 지급"));
            tooltip.accept(line("§r§e들고 우클릭: 뽑기 창 (1회 · 10회)"));
            return;
        }
        List<Object> lore = RuneKit.RUNE.list(advanced() ? "advTicketLore" : "ticketLore");
        for (Object l : lore) {
            String s = String.valueOf(l);
            if (!s.contains("들고 누르면")) tooltip.accept(line(s)); // 누르면 바로 뽑던 설명 → 아래 뽑기 창 줄
        }
        if (!advanced() && Gacha.normalPity()) {
            Gacha.Pity p = Gacha.defaultPity();
            tooltip.accept(line("§r§7천장: 초월 " + p.hard() + "회 · 영웅 " + p.heroEvery() + "회 확정"));
        }
        tooltip.accept(line("§r§e우클릭: 뽑기 창 (1회 · 10회)"));
    }

    private static Component line(String s) {
        return Component.literal(BedrockText.toJava(s));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) GachaSystem.useTicketItem(sp, this);
        return InteractionResult.CONSUME;
    }
}
