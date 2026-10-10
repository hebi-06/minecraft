package mmorpg.fabric.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 퀘스트 명령어 (commands.js): /quest (누구나 — 내 퀘스트 목록), /mainnpc [번호] (관리자 — 메인 퀘스트 NPC 세우기 · 목록), /questreset (관리자 — 내 퀘스트 기록 초기화, 시험용).
 */
public final class QuestCommands {
    private QuestCommands() {}

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("quest").executes(c -> {
            QuestLog.openQuestLog(c.getSource().getPlayerOrException());
            return 1;
        }));
        d.register(admin("mainnpc")
                .executes(c -> mainnpc(c, null))
                .then(Commands.argument("number", IntegerArgumentType.integer()).executes(c -> mainnpc(c, IntegerArgumentType.getInteger(c, "number")))));
        d.register(admin("questreset").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            Quests.resetQuests(sp);
            Notify.say(sp, "퀘스트 기록을 초기화했습니다.");
            return 1;
        }));
    }

    private static int mainnpc(CommandContext<CommandSourceStack> c, Integer n) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(MainNpcs.mainNpcCommand(sp, n))));
        return 1;
    }
}
