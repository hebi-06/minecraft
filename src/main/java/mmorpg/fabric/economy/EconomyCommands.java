package mmorpg.fabric.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import mmorpg.core.Economy;
import mmorpg.fabric.BedrockText;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 0.21.0 화폐 · 장터 · 쓰레기통 명령어 (영어 · 한글):
 * <ul>
 * <li>/골드 · /실버 (/gold · /silver) — 지갑 보기 · "/골드 수" = 금화로 꺼내기 · "/골드 넣기" = 가방의 금화를 모두 지갑에</li>
 * <li>/지갑 (/wallet) — 골드 · 실버 보기 · 운영자: /지갑 주기 · 빼기 · 정하기 &lt;대상&gt; &lt;골드|실버&gt; &lt;수&gt;</li>
 * <li>/환전 (/exchange) — 가방의 에메랄드 (블록은 9개) 를 실버로 1:1</li>
 * <li>/장터 (/market) — 장터 창 · /장터 등록 &lt;가격&gt; (손에 든 것) · /장터 내물건 · /장터 보관함 · 운영자: /장터 내리기 &lt;번호&gt;</li>
 * <li>/쓰레기통 (/trash) — 쓰레기통 창 · /쓰레기통 되살리기 — 마지막으로 지운 것 되찾기</li>
 * </ul>
 */
public final class EconomyCommands {
    private EconomyCommands() {}

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        // 골드 · 실버: 보기 · 꺼내기 · 넣기
        for (Economy.Kind k : Economy.Kind.values()) {
            for (String name : k == Economy.Kind.GOLD ? List.of("gold", "골드") : List.of("silver", "실버")) {
                LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name).executes(c -> show(c, k));
                for (String w : List.of("deposit", "넣기")) cmd.then(Commands.literal(w).executes(c -> depositAll(c, k)));
                cmd.then(Commands.argument("amount", LongArgumentType.longArg(1, 1_000_000L))
                        .executes(c -> say(c, Wallet.withdraw(c.getSource().getPlayerOrException(), k, LongArgumentType.getLong(c, "amount")))));
                d.register(cmd);
            }
        }
        // 지갑: 보기 · 운영자 주기 · 빼기 · 정하기
        for (String name : List.of("wallet", "지갑")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name).executes(EconomyCommands::wallet);
            // 화폐 이름은 글자 칸 대신 고정 낱말 (한글은 따옴표 없는 글자 칸에 쓸 수 없음)
            for (String[] op : new String[][] {{"give", "주기"}, {"take", "빼기"}, {"set", "정하기"}}) {
                for (String w : op) {
                    var targets = Commands.argument("targets", EntityArgument.players());
                    for (Economy.Kind k : Economy.Kind.values()) {
                        for (String kn : List.of(k.id, k.label())) {
                            targets.then(Commands.literal(kn).then(Commands.argument("amount", LongArgumentType.longArg(0, 1_000_000_000L))
                                    .executes(c -> adminMoney(c, op[0], EntityArgument.getPlayers(c, "targets"), k, LongArgumentType.getLong(c, "amount")))));
                        }
                    }
                    cmd.then(Commands.literal(w).requires(EconomyCommands::admin).then(targets));
                }
            }
            d.register(cmd);
        }
        // 환전
        for (String name : List.of("exchange", "환전")) {
            d.register(Commands.literal(name).executes(c -> say(c, Wallet.exchange(c.getSource().getPlayerOrException()))));
        }
        // 장터
        for (String name : List.of("market", "장터")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name).executes(c -> {
                MarketWindow.open(c.getSource().getPlayerOrException());
                return 1;
            });
            for (String w : List.of("sell", "등록")) {
                cmd.then(Commands.literal(w).then(Commands.argument("price", LongArgumentType.longArg(1, 1_000_000_000L))
                        .executes(c -> outcome(c, MarketStore.listHeld(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "price"))))));
            }
            for (String w : List.of("mine", "내물건")) {
                cmd.then(Commands.literal(w).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    MarketWindow.mine(sp, "", msg -> MarketWindow.browse(sp, MarketWindow.View.START, msg, 1), 0);
                    return 1;
                }));
            }
            for (String w : List.of("box", "보관함")) {
                cmd.then(Commands.literal(w).executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    MarketWindow.box(sp, 0, "", msg -> MarketWindow.browse(sp, MarketWindow.View.START, msg, 1), 0);
                    return 1;
                }));
            }
            for (String w : List.of("remove", "내리기")) {
                cmd.then(Commands.literal(w).requires(EconomyCommands::admin).then(Commands.argument("id", LongArgumentType.longArg(1))
                        .executes(c -> outcome(c, MarketStore.cancel(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "id"), true)))));
            }
            d.register(cmd);
        }
        // 쓰레기통
        for (String name : List.of("trash", "쓰레기통")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name).executes(c -> TrashWindow.open(c.getSource().getPlayerOrException()) ? 1 : 0);
            for (String w : List.of("restore", "되살리기")) cmd.then(Commands.literal(w).executes(c -> say(c, TrashWindow.restore(c.getSource().getPlayerOrException()))));
            d.register(cmd);
        }
    }

    /** 결과 글: §a 로 시작하면 성공 (채팅), 그 밖은 실패 (빨간 글) */
    private static int say(CommandContext<CommandSourceStack> c, String text) {
        if (text != null && text.startsWith("§a")) {
            c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava(text)), false);
            return 1;
        }
        c.getSource().sendFailure(Component.literal(BedrockText.toJava(text == null ? "" : text)));
        return 0;
    }

    private static int outcome(CommandContext<CommandSourceStack> c, MarketStore.Outcome o) {
        if (o.ok()) c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava(o.text())), false);
        else c.getSource().sendFailure(Component.literal(BedrockText.toJava(o.text())));
        return o.ok() ? 1 : 0;
    }

    private static int show(CommandContext<CommandSourceStack> c, Economy.Kind k) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        long w = Wallet.get(sp, k);
        int coins = Wallet.coins(sp, k);
        String coin = k == Economy.Kind.GOLD ? "금화" : "은화";
        String use = k == Economy.Kind.GOLD ? "유저 장터 · 장식" : "상점 · 대장장이 · 스탯 트리";
        c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava(k.color() + "[" + k.label() + "] §f지갑 " + k.color() + Economy.fmt(w) + " §7· 가방의 " + coin + " "
                + coins)), false);
        c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava("§7쓰는 곳: " + use + " §8· /" + k.label() + " <수> = " + coin + "로 꺼내기 · " + coin
                + " 우클릭 = 지갑에 넣기")), false);
        return 1;
    }

    private static int depositAll(CommandContext<CommandSourceStack> c, Economy.Kind k) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        long got = Wallet.deposit(sp, net.minecraft.world.InteractionHand.MAIN_HAND, k, true);
        if (got <= 0) return say(c, "§7가방에 " + (k == Economy.Kind.GOLD ? "금화" : "은화") + "가 없습니다");
        return say(c, "§a" + k.label() + " " + Economy.fmt(got) + " 을(를) 지갑에 넣었습니다 §7(지갑 " + Economy.fmt(Wallet.get(sp, k)) + ")");
    }

    private static int wallet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        StringBuilder sb = new StringBuilder("§6[지갑] ");
        for (Economy.Kind k : Economy.Kind.values()) {
            sb.append(k.color()).append(k.label()).append(" §f").append(Economy.fmt(Wallet.get(sp, k)));
            int coins = Wallet.coins(sp, k);
            if (coins > 0) sb.append(" §8(+동전 ").append(coins).append(")");
            sb.append("  ");
        }
        c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava(sb.toString().strip())), false);
        c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava("§7골드: 유저 장터 · 장식 §8| §7실버: 상점 · 대장장이 · 스탯 트리 §8| §7/골드 <수> · /실버 <수> 로 꺼내기")),
                false);
        return 1;
    }

    private static int adminMoney(CommandContext<CommandSourceStack> c, String op, Collection<ServerPlayer> targets, Economy.Kind k, long amount) {
        for (ServerPlayer sp : targets) {
            switch (op) {
                case "give" -> Wallet.add(sp, k, amount);
                case "take" -> Wallet.set(sp, k, Math.max(0, Wallet.get(sp, k) - amount));
                default -> Wallet.set(sp, k, amount);
            }
            long now = Wallet.get(sp, k);
            c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava("§a" + sp.getName().getString() + ": " + k.label() + " " + Economy.fmt(now))), true);
        }
        return targets.size();
    }
}
