package mmorpg.fabric.cosmetic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 장식 명령어 (영어 /cosmetic · 한글 /장식):
 * <ul>
 * <li>/장식 — 장식 창 (모드가 있는 클라이언트) · /장식 목록 — 가진 장식 · 입은 모습 (채팅)</li>
 * <li>/장식 입기 &lt;키&gt; · /장식 벗기 &lt;칸&gt; [무기 종류] · /장식 숨기기 &lt;갑옷 칸&gt; · /장식 사기 &lt;키&gt; (창과 같은 일 — 모드가 없어도)</li>
 * <li>운영자: /장식 지급 &lt;키|all&gt; [대상] · /장식 회수 &lt;키|all&gt; [대상] · /장식 초기화 [대상]</li>
 * </ul>
 */
public final class CosmeticCommands {
    private CosmeticCommands() {}

    private static final SuggestionProvider<CommandSourceStack> KEYS = (c, b) -> SharedSuggestionProvider.suggest(CosmeticDefs.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> KEYS_ALL = (c, b) -> {
        List<String> k = new ArrayList<>(CosmeticDefs.all().keySet());
        k.add("all");
        return SharedSuggestionProvider.suggest(k, b);
    };
    private static final SuggestionProvider<CommandSourceStack> SLOTS = (c, b) -> SharedSuggestionProvider.suggest(CosmeticDefs.SLOTS, b);
    private static final SuggestionProvider<CommandSourceStack> ARMOR = (c, b) -> SharedSuggestionProvider.suggest(CosmeticDefs.ARMOR, b);

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("cosmetic", "장식")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                    .executes(CosmeticCommands::open)
                    .then(Commands.literal("list").executes(CosmeticCommands::list))
                    .then(Commands.literal("목록").executes(CosmeticCommands::list));
            for (String w : List.of("equip", "입기")) {
                cmd.then(Commands.literal(w).then(Commands.argument("key", StringArgumentType.word()).suggests(KEYS)
                        .executes(c -> run(c, Cosmetics.equip(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "key"))))));
            }
            for (String w : List.of("buy", "사기")) {
                cmd.then(Commands.literal(w).then(Commands.argument("key", StringArgumentType.word()).suggests(KEYS)
                        .executes(c -> run(c, Cosmetics.buy(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "key"))))));
            }
            for (String w : List.of("clear", "벗기")) {
                cmd.then(Commands.literal(w).then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOTS)
                        .executes(c -> run(c, Cosmetics.clear(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "slot"), null)))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(c -> run(c, Cosmetics.clear(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "slot"),
                                        StringArgumentType.getString(c, "type")))))));
            }
            for (String w : List.of("hide", "숨기기")) {
                cmd.then(Commands.literal(w).then(Commands.argument("slot", StringArgumentType.word()).suggests(ARMOR)
                        .executes(c -> run(c, Cosmetics.hide(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "slot"))))));
            }
            cmd.then(adminCmd("give")).then(adminCmd("지급")).then(adminCmd("take")).then(adminCmd("회수"));
            for (String w : List.of("reset", "초기화")) {
                cmd.then(Commands.literal(w).requires(CosmeticCommands::admin)
                        .executes(c -> reset(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(c -> reset(c, EntityArgument.getPlayers(c, "targets")))));
            }
            d.register(cmd);
        }
    }

    private static int open(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        if (!CosmeticNet.canSend(sp)) {
            sp.sendSystemMessage(Component.literal("§c장식 창은 MMO RPG 모드가 있는 클라이언트에서 열립니다 §7(/장식 목록 · /장식 입기 <키>)"));
            return 0;
        }
        Cosmetics.sync(sp, "open");
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> c, String err) {
        if (err != null) {
            c.getSource().sendFailure(Component.literal(BedrockText.toJava(err)));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("§a장식: 완료"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Cosmetics.State s = Cosmetics.state(sp);
        StringBuilder sb = new StringBuilder("§d[장식] §f가진 장식 " + s.owned().size() + " / " + CosmeticDefs.all().size() + " §7(" + Inv.CURRENCY_NAME + " "
                + Inv.money(sp) + ")");
        for (String slot : CosmeticDefs.SLOTS) {
            CosmeticDefs.Slot sl = CosmeticDefs.slot(slot);
            sb.append("\n §e").append(sl.name()).append("§7: ");
            if ("weapon".equals(slot)) {
                sb.append(s.look().weapon().isEmpty() ? "§8기본" : String.join(", ", s.look().weapon().entrySet().stream()
                        .map(e -> e.getKey() + " → " + name(e.getValue())).toList()));
            } else {
                String v = s.look().get(slot);
                sb.append(v == null ? (sl.hide() ? "§8원래" : "§8없음") : Look.HIDE.equals(v) ? "§c숨김" : "§f" + name(v));
            }
        }
        for (CosmeticDefs.Def d : CosmeticDefs.all().values()) {
            boolean has = s.owned().contains(d.id());
            sb.append("\n ").append(has ? "§a✔ §f" : "§8").append(d.name()).append(" §8(").append(d.id()).append(" · ").append(CosmeticDefs.slot(d.slot()).name())
                    .append(has ? ")" : " · " + Inv.CURRENCY_NAME + " " + d.price() + ")");
        }
        for (String line : sb.toString().split("\n")) sp.sendSystemMessage(Component.literal(BedrockText.toJava(line)));
        return s.owned().size();
    }

    private static String name(String id) {
        CosmeticDefs.Def d = CosmeticDefs.get(id);
        return d == null ? id : d.name();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminCmd(String action) {
        return Commands.literal(action).requires(CosmeticCommands::admin)
                .then(Commands.argument("key", StringArgumentType.word()).suggests(KEYS_ALL)
                        .executes(c -> adminRun(c, action, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> adminRun(c, action, EntityArgument.getPlayers(c, "targets")))));
    }

    private static int adminRun(CommandContext<CommandSourceStack> c, String action, Collection<ServerPlayer> targets) {
        String key = StringArgumentType.getString(c, "key");
        List<String> keys = key.equals("all") ? new ArrayList<>(CosmeticDefs.all().keySet()) : List.of(key);
        if (!key.equals("all") && CosmeticDefs.get(key) == null) {
            c.getSource().sendFailure(Component.literal("장식 키: " + String.join(" · ", CosmeticDefs.all().keySet()) + " · all"));
            return 0;
        }
        boolean give = action.equals("give") || action.equals("지급");
        int n = 0;
        for (ServerPlayer sp : targets) {
            for (String k : keys) if (give ? Cosmetics.give(sp, k) : Cosmetics.take(sp, k)) n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal("장식 " + (give ? "지급" : "회수") + " " + key + ": " + count + "개"), true);
        return n;
    }

    private static int reset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) {
        for (ServerPlayer sp : targets) Cosmetics.reset(sp);
        c.getSource().sendSuccess(() -> Component.literal("장식 초기화: " + targets.size() + "명"), true);
        return targets.size();
    }
}
