package mmorpg.fabric.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.BedrockText;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 몬스터 명령어 (commands.js — 운영자, 애드온 글 그대로):
 * /mob &lt;종류&gt; [수] (눈앞 4칸, 1~10마리) · /mob clear (주변 48칸 몬스터 치우기 — 사냥터 몬스터는 남김),
 * /mobzone [종류 [수] | clear] (사냥터 — {@link HuntZones}).
 * 애드온의 /loot (전리품 받기 — 시험용) 는 자바의 바닐라 /loot 와 이름이 겹쳐 옮기지 않음.
 */
public final class MonsterCommands {
    private MonsterCommands() {}

    private static final SuggestionProvider<CommandSourceStack> KINDS_CLEAR = (c, b) -> {
        List<String> l = new ArrayList<>(Monsters.keys());
        l.add("clear");
        return SharedSuggestionProvider.suggest(l, b);
    };

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mob").requires(MonsterCommands::admin)
                .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS_CLEAR)
                        .executes(c -> mob(c, null))
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(c -> mob(c, IntegerArgumentType.getInteger(c, "count"))))));
        d.register(Commands.literal("mobzone").requires(MonsterCommands::admin)
                .executes(MonsterCommands::zoneList)
                .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS_CLEAR)
                        .executes(c -> zone(c, null))
                        .then(Commands.argument("count", IntegerArgumentType.integer())
                                .executes(c -> zone(c, IntegerArgumentType.getInteger(c, "count"))))));
    }

    /** 채팅 (애드온 sendMessage — 베드락 서식 그대로) */
    static void send(ServerPlayer sp, String text) {
        for (String line : text.split("\n")) sp.sendSystemMessage(Component.literal(BedrockText.toJava(line)));
    }

    /** /mob 종류 [수] · /mob clear */
    private static int mob(CommandContext<CommandSourceStack> c, Integer n) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        if (kind.equals("clear")) {
            int cleared = Monsters.clearMonsters(sp, 48);
            int kept = HuntZones.zoneMonstersNear(sp, 48);
            send(sp, "§6[RPG] §f몬스터 " + cleared + "마리를 치웠습니다." + (kept > 0 ? " §7(사냥터 몬스터 " + kept + "마리는 그대로 — /mobzone clear)" : ""));
            return cleared;
        }
        Monsters.Kind k = kind.indexOf(':') < 0 ? Monsters.kind(kind) : null;
        if (k == null) {
            send(sp, "§c종류: " + String.join(" · ", Monsters.keys()) + " · clear");
            return 0;
        }
        List<RpgMonster> list = Monsters.spawnMonsters(sp, k.key(), n == null ? 1 : n);
        send(sp, "§6[RPG] §fLv." + k.level() + " " + k.name() + " " + list.size() + "마리 §7(먼저 때리기 전에는 공격하지 않음)");
        return list.size();
    }

    /** /mobzone (목록) */
    private static int zoneList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        send(sp, HuntZones.zoneListText(c.getSource().getServer()));
        return 1;
    }

    /** /mobzone 종류 [수] · /mobzone clear */
    private static int zone(CommandContext<CommandSourceStack> c, Integer n) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String kind = StringArgumentType.getString(c, "kind");
        send(sp, kind.equals("clear") ? HuntZones.removeNearestZone(sp, 16) : HuntZones.addZone(sp, kind, n));
        return 1;
    }
}
