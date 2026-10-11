package mmorpg.fabric.stattree;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import mmorpg.core.StatTree;
import mmorpg.core.Stats;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 스탯 트리 명령어 (영어 /stattree · 한글 /스탯트리):
 * <ul>
 * <li>/stattree [스탯] — 스탯 트리 창 (모드가 없으면 채팅 요약) · /stattree info — 채팅 요약</li>
 * <li>/stattree pick &lt;스탯&gt; &lt;단계&gt; &lt;갈래 1 · 2&gt; — 갈래 고르기 (채팅의 갈래 이름을 누르면 이 명령)</li>
 * <li>/stattree reset &lt;스탯&gt; — 그 나무의 갈래 초기화 (에메랄드)</li>
 * <li>운영자: /stattree clear [대상] — 나무 넷의 고른 갈래를 모두 지움 (에메랄드 없이 — 받은 뽑기권 기록은 그대로, 다시 주지 않음)</li>
 * </ul>
 * 스탯: str · dex · int · vit 또는 힘 · 민첩 · 마력 · 체력.
 */
public final class StatTreeCommands {
    private StatTreeCommands() {}

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    /** 스탯 이름 (str · 힘 …) → 키 (모르면 null) */
    public static String statKey(String s) {
        if (s == null) return null;
        for (String k : StatTree.stats()) if (k.equalsIgnoreCase(s) || Stats.info(k).str("name").equals(s)) return k;
        return null;
    }

    /** 스탯 이름 (영어 키 · 한글) — 명령의 글자 (한글은 따옴표 없는 인자로 받을 수 없어 글자 그대로 둠) */
    private static List<String[]> statWords() {
        List<String[]> out = new ArrayList<>();
        for (String k : StatTree.stats()) {
            out.add(new String[] {k, k});
            out.add(new String[] {Stats.info(k).str("name"), k});
        }
        return out;
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("stattree", "스탯트리")) {
            LiteralArgumentBuilder<CommandSourceStack> pick = Commands.literal("pick");
            LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset");
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                    .executes(c -> open(c, null))
                    .then(Commands.literal("info").executes(c -> {
                        chatSummary(c.getSource().getPlayerOrException());
                        return 1;
                    }))
                    .then(Commands.literal("clear").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(c -> clear(c, List.of(c.getSource().getPlayerOrException())))
                            .then(Commands.argument("targets", EntityArgument.players()).executes(c -> clear(c, EntityArgument.getPlayers(c, "targets")))));
            for (String[] w : statWords()) {
                String key = w[1];
                cmd.then(Commands.literal(w[0]).executes(c -> open(c, key)));
                pick.then(Commands.literal(w[0]).then(Commands.argument("tier", IntegerArgumentType.integer(1, 64))
                        .then(Commands.argument("option", IntegerArgumentType.integer(1, 8)).executes(c -> pick(c, key)))));
                reset.then(Commands.literal(w[0]).executes(c -> reset(c, key)));
            }
            cmd.then(pick).then(reset);
            d.register(cmd);
        }
    }

    private static int open(CommandContext<CommandSourceStack> c, String key) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        StatTrees.open(sp, key == null ? firstTab(sp) : key);
        return 1;
    }

    /** 처음 열 탭: 고를 갈래가 있는 나무 → 가장 많이 자란 나무 → 첫 나무 */
    static String firstTab(ServerPlayer sp) {
        StatTree.State s = StatTrees.state(sp);
        Map<String, Double> spent = StatTrees.spent(sp);
        String best = StatTree.stats().get(0);
        int bestTier = -1;
        for (String k : StatTree.stats()) {
            if (!StatTree.pending(k, Stats.get(spent, k), s.picksOf(k)).isEmpty()) return k;
            int t = StatTree.tier(k, Stats.get(spent, k));
            if (t > bestTier) {
                bestTier = t;
                best = k;
            }
        }
        return best;
    }

    private static int pick(CommandContext<CommandSourceStack> c, String key) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String err = StatTrees.pick(sp, key, IntegerArgumentType.getInteger(c, "tier") - 1, IntegerArgumentType.getInteger(c, "option") - 1);
        if (err != null) Notify.warn(sp, err);
        return err == null ? 1 : 0;
    }

    private static int reset(CommandContext<CommandSourceStack> c, String key) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String err = StatTrees.reset(sp, key, false);
        if (err != null) Notify.warn(sp, err);
        return err == null ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
        int picks = 0;
        for (ServerPlayer p : targets) picks += StatTrees.clear(p);
        int n = picks;
        c.getSource().sendSuccess(() -> Component.literal("스탯 트리 갈래를 지웠습니다: " + targets.size() + "명 · 갈래 " + n + "개 (받은 뽑기권 기록은 그대로)"), true);
        return targets.size();
    }

    /** 채팅 요약 (모드가 없는 클라이언트 · /stattree info): 나무마다 단계 · 보너스, 고를 갈래는 누르면 고름 */
    static void chatSummary(ServerPlayer sp) {
        StatTree.State s = StatTrees.state(sp);
        Map<String, Double> spent = StatTrees.spent(sp);
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(Notify.PREFIX + "§e§l스탯 트리 §r§7(스탯을 찍으면 자람 · 갈래는 눌러서 고름)")));
        for (String k : StatTree.stats()) {
            StatTree.Tree t = StatTree.tree(k);
            double v = Stats.get(spent, k);
            int tier = StatTree.tier(k, v);
            int next = StatTree.nextAt(k, v);
            String bonus = StatTree.bonusText(StatTree.treeTotals(k, v, s.picksOf(k)));
            String head = " " + StatTrees.color(k) + Stats.info(k).str("name") + " · " + t.title() + " §f" + tier + "/" + t.nodes().size()
                    + " §7(" + Stats.info(k).str("name") + " " + (int) v + (next > 0 ? " · 다음 " + next : " · 다 자람") + ")" + (bonus.isEmpty() ? "" : " §a" + bonus);
            sp.sendSystemMessage(Component.literal(BedrockText.toJava(head)));
            for (int idx : StatTree.pending(k, v, s.picksOf(k))) {
                StatTree.Node n = t.nodes().get(idx);
                MutableComponent line = Component.literal(BedrockText.toJava("   §e" + (idx + 1) + "단계 갈래: "));
                for (int i = 0; i < n.pick().size(); i++) {
                    StatTree.Option o = n.pick().get(i);
                    String cmd = "/stattree pick " + k + " " + (idx + 1) + " " + (i + 1);
                    line.append(Component.literal("[" + o.name() + "]").withStyle(st -> st.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.RunCommand(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(BedrockText.toJava("§a" + StatTree.bonusText(o.bonus())))))));
                    line.append(Component.literal(" "));
                }
                sp.sendSystemMessage(line);
            }
        }
        String all = StatTree.bonusText(StatTrees.totals(sp));
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(" §7합계: " + (all.isEmpty() ? "없음" : "§a" + all))));
    }
}
