package mmorpg.fabric.gacha;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Gacha;
import mmorpg.core.Runes;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.rune.RuneItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

/**
 * 룬 뽑기 명령어 (영어 /gacha · 한글 /뽑기):
 * <ul>
 * <li>/뽑기 — 뽑기 창 (모드가 있는 클라이언트)</li>
 * <li>운영자: /뽑기 한정 — 한정 뽑기 설정 보기 · /뽑기 한정 열기 "이름" &lt;초월 픽업 계열+…&gt; [영웅 픽업 계열+…] [일수] (이름이 한글이면 따옴표,
 * 계열은 + 로 잇거나 따옴표 안에 쉼표 — 나머지는 기본값, 자세한 설정은 뽑기 창의 설정 단추) · /뽑기 한정 끝 · /뽑기 뽑기권 [장수] [대상] (한정 룬 뽑기권) ·
 * /뽑기 천장 [대상] · /뽑기 초기화 [대상]</li>
 * </ul>
 */
public final class GachaCommands {
    private GachaCommands() {}

    /** 초월 픽업: 모든 계열 (0.16.0 한정 계열 — 초월 하나 — 도) */
    private static final SuggestionProvider<CommandSourceStack> FAMILIES = (c, b) -> SharedSuggestionProvider.suggest(Runes.ALL_FAMILIES, b);
    /** 영웅 픽업: 뽑기 계열만 (한정 계열에는 영웅 룬이 없음) */
    private static final SuggestionProvider<CommandSourceStack> HERO_FAMILIES = (c, b) -> SharedSuggestionProvider.suggest(Runes.FAMILY_ORDER, b);

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("gacha", "뽑기")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name).executes(c -> {
                GachaSystem.open(c.getSource().getPlayerOrException(), "");
                return 1;
            });
            for (String w : List.of("limited", "한정")) {
                LiteralArgumentBuilder<CommandSourceStack> lim = Commands.literal(w).requires(GachaCommands::admin).executes(GachaCommands::info);
                for (String s : List.of("start", "열기")) {
                    lim.then(Commands.literal(s).then(Commands.argument("name", StringArgumentType.string())
                            .then(Commands.argument("transcend", StringArgumentType.string()).suggests(FAMILIES)
                                    .executes(c -> start(c, null, -1))
                                    .then(Commands.argument("hero", StringArgumentType.string()).suggests(HERO_FAMILIES)
                                            .executes(c -> start(c, StringArgumentType.getString(c, "hero"), -1))
                                            .then(Commands.argument("days", IntegerArgumentType.integer(0, 365))
                                                    .executes(c -> start(c, StringArgumentType.getString(c, "hero"), IntegerArgumentType.getInteger(c, "days"))))))));
                }
                for (String s : List.of("close", "끝")) {
                    lim.then(Commands.literal(s).executes(c -> {
                        String err = GachaSystem.closeLimited(c.getSource().getServer());
                        if (err != null) c.getSource().sendFailure(Component.literal(BedrockText.toJava(err)));
                        return err == null ? 1 : 0;
                    }));
                }
                cmd.then(lim);
            }
            for (String w : List.of("ticket", "뽑기권")) {
                cmd.then(Commands.literal(w).requires(GachaCommands::admin)
                        .executes(c -> give(c, 1, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 640))
                                .executes(c -> give(c, IntegerArgumentType.getInteger(c, "count"), List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(c -> give(c, IntegerArgumentType.getInteger(c, "count"), EntityArgument.getPlayers(c, "targets"))))));
            }
            for (String w : List.of("pity", "천장")) {
                cmd.then(Commands.literal(w).requires(GachaCommands::admin)
                        .executes(c -> pity(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> pity(c, EntityArgument.getPlayers(c, "targets")))));
            }
            for (String w : List.of("reset", "초기화")) {
                cmd.then(Commands.literal(w).requires(GachaCommands::admin)
                        .executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> reset(c, EntityArgument.getPlayers(c, "targets")))));
            }
            d.register(cmd);
        }
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        Gacha.Spec s = GachaSystem.limitedSpec(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava(GachaSystem.describe(s))), false);
        return 1;
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String p : s.split("[,+\\s]+")) if (!p.isBlank()) out.add(p.strip());
        return out;
    }

    private static int start(CommandContext<CommandSourceStack> c, String hero, int days) {
        Map<String, Object> m = new LinkedHashMap<>();
        Gacha.Spec d = Gacha.Spec.defaults("", StringArgumentType.getString(c, "name"), split(StringArgumentType.getString(c, "transcend")), split(hero),
                System.currentTimeMillis());
        m.putAll(d.toMap());
        m.remove("id");
        m.put("days", days >= 0 ? (double) days : Gacha.limitedDefaults().num("days"));
        ServerPlayer by = c.getSource().getPlayer();
        String err = GachaSystem.saveLimited(c.getSource().getServer(), m, by);
        if (err != null) {
            c.getSource().sendFailure(Component.literal(BedrockText.toJava(err)));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava(GachaSystem.describe(GachaSystem.limitedSpec(c.getSource().getServer())))), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> c, int count, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        if (RuneItems.LIMITED_TICKET == null) return 0;
        for (ServerPlayer sp : targets) {
            int left = count;
            while (left > 0) {
                int n = Math.min(64, left);
                Notify.give(sp, new ItemStack(RuneItems.LIMITED_TICKET, n));
                left -= n;
            }
            Notify.say(sp, "§d[룬 뽑기] §c" + Gacha.ticket().str("name") + " §f" + count + "장§7을 받았습니다");
            GachaSystem.sync(sp, "", null);
        }
        c.getSource().sendSuccess(() -> Component.literal(Gacha.ticket().str("name") + " " + count + "장 → " + targets.size() + "명"), true);
        return targets.size();
    }

    private static int pity(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
        for (ServerPlayer sp : targets) c.getSource().sendSuccess(() -> Component.literal(BedrockText.toJava(GachaSystem.describeCounters(sp))), false);
        return targets.size();
    }

    private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
        for (ServerPlayer sp : targets) {
            GachaSystem.reset(sp);
            GachaSystem.sync(sp, "", null);
        }
        c.getSource().sendSuccess(() -> Component.literal("뽑기 기록 (천장 · 확정 · 기록) 을 지웠습니다: " + targets.size() + "명"), true);
        return targets.size();
    }
}
