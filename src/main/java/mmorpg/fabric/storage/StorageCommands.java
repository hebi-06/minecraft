package mmorpg.fabric.storage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import java.util.List;
import mmorpg.core.Storage;
import mmorpg.fabric.BedrockText;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 0.22.0 엔더 창고 명령어 (영어 · 한글):
 * <ul>
 * <li>/창고 (/storage) — 마지막으로 본 상자로 엶 · "/창고 번호" = 그 상자로 · "/창고 사기" (buy) = 다음 상자 사기 · "/창고 정보" (info) = 열린 상자 · 값</li>
 * <li>운영자: "/창고 열기 &lt;대상&gt; &lt;수&gt;" (unlock) = 열린 상자 수를 정함 (사지 않고 — 보상 · 시험용)</li>
 * </ul>
 */
public final class StorageCommands {
    private StorageCommands() {}

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("storage", "창고")) {
            LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal(name).executes(c -> StorageWindow.open(c.getSource().getPlayerOrException(), -1, null, null) ? 1 : 0);
            cmd.then(Commands.argument("chest", IntegerArgumentType.integer(1, StorageData.MAX_PAGES)).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int chest = IntegerArgumentType.getInteger(c, "chest");
                if (chest > StorageData.of(sp).unlocked()) return say(c, "§c" + Storage.label(chest) + "는 아직 잠겨 있습니다 §7(" + info(sp) + "§7)");
                if (StorageWindow.menu(sp) != null) return StorageWindow.switchPage(sp, chest - 1) ? 1 : 0;
                return StorageWindow.open(sp, chest - 1, null, null) ? 1 : 0;
            }));
            for (String w : List.of("buy", "사기")) cmd.then(Commands.literal(w).executes(c -> say(c, StorageWindow.buy(c.getSource().getPlayerOrException()))));
            for (String w : List.of("info", "정보")) cmd.then(Commands.literal(w).executes(c -> say(c, "§a" + info(c.getSource().getPlayerOrException()))));
            for (String w : List.of("unlock", "열기")) {
                cmd.then(Commands.literal(w).requires(StorageCommands::admin).then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, StorageData.MAX_PAGES))
                                .executes(c -> unlock(c, EntityArgument.getPlayers(c, "targets"), IntegerArgumentType.getInteger(c, "count"))))));
            }
            d.register(cmd);
        }
    }

    /** "열린 상자 2/5 · 다음: 3번 상자 30 골드" */
    static String info(ServerPlayer sp) {
        StorageData d = StorageData.of(sp);
        int next = Storage.next(d.unlocked());
        Storage.Price p = next < 0 ? null : Storage.price(next);
        return "엔더 창고: 열린 상자 " + d.unlocked() + "/" + Storage.chests() + (p == null ? " §7(모두 열림)" : " §7· 다음 " + Storage.label(next) + ": " + p.text() + " §7(/창고 사기)");
    }

    private static int unlock(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets, int count) {
        int n = Storage.clampUnlocked(count);
        for (ServerPlayer sp : targets) {
            StorageData.of(sp).setUnlocked(n);
            StorageWindow.StorageMenu m = StorageWindow.menu(sp);
            if (m != null) {
                if (m.view.page >= n) StorageWindow.switchPage(sp, n - 1);
                StorageWindow.sendState(sp, m);
            }
        }
        return say(c, "§a엔더 창고: " + targets.size() + "명의 열린 상자를 " + n + "/" + Storage.chests() + " 로 정했습니다");
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
}
