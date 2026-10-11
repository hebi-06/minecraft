package mmorpg.fabric.mount;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
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
 * 탈것 명령어 (영어 /mount · 한글 /탈것):
 * <ul>
 * <li>/mount — 고른 탈것 부르기 (타고 있으면 돌려보내기 — 스탯 창 탈것 칸과 같음)</li>
 * <li>/mount select (/탈것 고르기) — 탈것 고르기 창 · /mount list (/탈것 목록) — 가진 탈것</li>
 * <li>운영자: /mount unlock &lt;키&gt; [대상] · /mount lock &lt;키&gt; [대상] (탈것 아이템으로 얻은 것 빼기) · /mount give &lt;키&gt; [대상] (탈것 아이템)</li>
 * </ul>
 */
public final class MountCommands {
    private MountCommands() {}

    private static final SuggestionProvider<CommandSourceStack> KEYS = (c, b) -> SharedSuggestionProvider.suggest(MountDefs.all().keySet(), b);

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("mount", "탈것")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name)
                    .executes(c -> {
                        Mounts.toggle(c.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("select").executes(MountCommands::select))
                    .then(Commands.literal("고르기").executes(MountCommands::select))
                    .then(Commands.literal("list").executes(MountCommands::list))
                    .then(Commands.literal("목록").executes(MountCommands::list))
                    .then(adminCmd("unlock")).then(adminCmd("lock")).then(adminCmd("give"));
            d.register(cmd);
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminCmd(String action) {
        return Commands.literal(action).requires(MountCommands::admin)
                .then(Commands.argument("key", StringArgumentType.word()).suggests(KEYS)
                        .executes(c -> adminRun(c, action, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> adminRun(c, action, EntityArgument.getPlayers(c, "targets")))));
    }

    private static int select(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Mounts.openSelect(c.getSource().getPlayerOrException(), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        List<String> owned = Mounts.owned(sp);
        String cur = Mounts.current(sp);
        StringBuilder sb = new StringBuilder("§6[RPG] §f가진 탈것 " + owned.size() + " / " + MountDefs.all().size() + "§7:");
        for (MountDefs.Def m : MountDefs.all().values()) {
            boolean has = owned.contains(m.key());
            sb.append("\n ").append(has ? (m.key().equals(cur) ? "§a✔ §b" : "§b") : "§8").append(m.name()).append(" §8(").append(m.key()).append(")")
                    .append(has ? "" : " §7잠김");
        }
        sb.append("\n§7부르기: 스탯 창 탈것 칸 · /mount — 고르기: Shift + 탈것 칸 · /mount select");
        for (String line : sb.toString().split("\n")) sp.sendSystemMessage(Component.literal(BedrockText.toJava(line)));
        return owned.size();
    }

    private static int adminRun(CommandContext<CommandSourceStack> c, String action, Collection<ServerPlayer> targets) {
        String key = StringArgumentType.getString(c, "key");
        MountDefs.Def def = MountDefs.get(key);
        if (def == null) {
            c.getSource().sendFailure(Component.literal("탈것 키: " + String.join(" · ", MountDefs.all().keySet())));
            return 0;
        }
        int n = 0;
        for (ServerPlayer sp : targets) {
            switch (action) {
                case "unlock" -> {
                    if (Mounts.unlock(sp, key)) {
                        n++;
                        Notify.say(sp, "§b" + def.name() + " §f탈것이 내 탈것 목록에 들어왔습니다.");
                    }
                }
                case "lock" -> {
                    if (Mounts.lock(sp, key)) n++;
                }
                default -> {
                    MountItem item = MountItems.of(key);
                    if (item != null) {
                        Notify.give(sp, new ItemStack(item));
                        n++;
                    }
                }
            }
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal("탈것 " + action + " " + key + ": " + count + "명"), true);
        return n;
    }
}
