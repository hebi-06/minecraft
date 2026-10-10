package mmorpg.fabric.boss;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 보스 명령어 (commands.js 의 boss — 운영자): /boss altar [종류] = 제단 받기, list = 제단 목록, build · summon · reset · cooldown = 가장 가까운 (64칸)
 * 제단의 필드 짓기 · 바로 소환 · 초기화 · 재사용 대기 없애기. 글은 애드온 그대로.
 */
public final class BossCommands {
    private BossCommands() {}

    static final List<String> ACTIONS = List.of("altar", "list", "build", "summon", "reset", "cooldown");
    private static final SuggestionProvider<CommandSourceStack> ACTION = (c, b) -> SharedSuggestionProvider.suggest(ACTIONS, b);
    private static final SuggestionProvider<CommandSourceStack> KINDS = (c, b) -> SharedSuggestionProvider.suggest(Rules.get().strings("ALL_BOSS_ORDER"), b);

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("boss").requires(BossCommands::admin)
                .executes(c -> help(c))
                .then(Commands.argument("action", StringArgumentType.word()).suggests(ACTION)
                        .executes(c -> run(c, StringArgumentType.getString(c, "action"), null))
                        .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                                .executes(c -> run(c, StringArgumentType.getString(c, "action"), StringArgumentType.getString(c, "kind"))))));
    }

    private static void tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(BedrockText.toJava(text)));
    }

    private static int help(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        tell(c.getSource().getPlayerOrException(), "§6[보스] §f/boss altar 종류 §7= 제단 받기, §f/boss list §7= 제단 목록, §f/boss build·summon·reset·cooldown §7= 가장 가까운 제단의 필드 짓기·바로 소환·초기화·재사용 대기 없애기");
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> c, String action, String kind) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        return boss(p, action, kind) ? 1 : 0;
    }

    /** /boss 행동 (commands.js 그대로 — 시험이 바로 부를 수 있게 따로). 알 수 없는 행동이면 false */
    public static boolean boss(ServerPlayer p, String action, String kind) {
        List<String> order = Rules.get().strings("ALL_BOSS_ORDER");
        if ("altar".equals(action)) {
            String k = kind != null ? kind : order.get(0);
            if (Arena.BOSSES.objOrNull(k) == null) {
                tell(p, "§c종류: " + String.join(" · ", order));
                return true;
            }
            Notify.give(p, Arena.altarItem(k, 1));
            tell(p, "§6[보스] §f" + Arena.BOSSES.obj(k).str("name") + " 제단 지급 §7(놓으면 보스 필드를 만들지 묻습니다)");
            return true;
        }
        if ("list".equals(action)) {
            List<Arena.Altar> list = Arena.altars();
            if (list.isEmpty()) {
                tell(p, "§7제단이 없습니다");
                return true;
            }
            List<String> lines = new ArrayList<>();
            for (Arena.Altar a : list) {
                lines.add("§6" + Arena.BOSSES.obj(a.key).str("name") + " §7" + a.dim.replace("minecraft:", "") + " " + a.x + " " + a.y + " " + a.z + " §8· "
                        + (a.built ? "§a필드" : "§c필드 없음") + (Bosses.encounterAt(a) != null ? " §c전투 중" : ""));
            }
            tell(p, String.join("\n", lines));
            return true;
        }
        if (!ACTIONS.contains(action)) {
            tell(p, "§c행동: " + String.join(" · ", ACTIONS));
            return false;
        }
        Arena.Altar a = Arena.nearestAltar(p, 64);
        if (a == null) {
            tell(p, "§c64칸 안에 보스 제단이 없습니다");
            return true;
        }
        String name = Arena.BOSSES.obj(a.key).str("name");
        switch (action) {
            case "build" -> Arena.askBuild(p, a);
            case "summon" -> {
                if (Bosses.encounterAt(a) != null) tell(p, "§c이미 전투 중입니다");
                else if (!a.built) tell(p, "§c보스 필드가 없습니다 §7— /boss build 로 먼저 지으세요");
                else {
                    a.cooldownUntil = 0;
                    Arena.saveAltars();
                    Bosses.summon(a, p);
                }
            }
            case "reset" -> {
                Bosses.Enc enc = Bosses.encounterAt(a);
                if (enc != null) Bosses.endEncounter(enc, "manual");
                tell(p, "§6[보스] §f" + name + " " + (enc != null ? "초기화" : "전투 없음"));
            }
            case "cooldown" -> {
                a.cooldownUntil = 0;
                Arena.saveAltars();
                tell(p, "§6[보스] §f" + name + " 재사용 대기 없앰");
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
