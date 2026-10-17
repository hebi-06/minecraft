package mmorpg.fabric.rune;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import mmorpg.core.Runes;
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
 * 룬 명령어 (commands.js): /rune &lt;계열&gt; &lt;등급&gt; [돌파 0 ~ 6] (룬 받기 · 관리자 — 돌파는 8단계), /runeticket [장수] [advanced · str · dex · int · vit] (뽑기권 받기 · 관리자 —
 * 스탯 룬 뽑기권은 7단계 스탯 트리),
 * /runequick (11단계부터 없어진 '룬 칸을 고르면 바로 사용' — 스킬 칸으로 안내만).
 */
public final class RuneCommands {
    private RuneCommands() {}

    private static final SuggestionProvider<CommandSourceStack> FAMILIES = (c, b) -> SharedSuggestionProvider.suggest(Runes.ALL_FAMILIES, b);
    private static final SuggestionProvider<CommandSourceStack> GRADES = (c, b) -> SharedSuggestionProvider.suggest(Runes.GRADE_ORDER, b);
    /** 뽑기권 종류: normal · advanced · 스탯 룬 뽑기권 (7단계 — str · dex · int · vit) */
    private static final SuggestionProvider<CommandSourceStack> KINDS = (c, b) -> {
        List<String> all = new java.util.ArrayList<>(List.of("normal", "advanced"));
        all.addAll(mmorpg.core.StatTree.tickets().keySet());
        return SharedSuggestionProvider.suggest(all, b);
    };

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rune").requires(RuneCommands::admin)
                .then(Commands.argument("family", StringArgumentType.word()).suggests(FAMILIES)
                        .then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
                                .executes(c -> give(c, 0))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, mmorpg.core.RuneBreak.max()))
                                        .executes(c -> give(c, IntegerArgumentType.getInteger(c, "level")))))));
        d.register(Commands.literal("runeticket").requires(RuneCommands::admin)
                .executes(c -> tickets(c, 1, "normal"))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 640))
                        .executes(c -> tickets(c, IntegerArgumentType.getInteger(c, "amount"), "normal"))
                        .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                                .executes(c -> tickets(c, IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "kind"))))));
        d.register(Commands.literal("runequick")
                .executes(RuneCommands::quick)
                .then(Commands.argument("mode", StringArgumentType.greedyString()).executes(RuneCommands::quick)));
    }

    private static void tell(ServerPlayer sp, String text) {
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(text)));
    }

    private static int give(CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String family = StringArgumentType.getString(c, "family");
        String grade = StringArgumentType.getString(c, "grade");
        Runes.Rune r = Runes.parseRune(Runes.runeId(family, grade));
        if (r == null) {
            tell(sp, "§c없는 룬입니다. §7(계열 " + String.join("·", Runes.ALL_FAMILIES) + ", 등급 common·rare·noble·hero·transcend)");
            return 0;
        }
        Notify.give(sp, Breakthrough.stack(family, grade, level));
        tell(sp, Notify.PREFIX + "룬 지급: " + r.gradeColor() + r.def().str("name") + Breakthrough.nameTag(level) + "§r §7(" + r.famName() + " 룬 · " + r.gradeName() + ")");
        return 1;
    }

    /** 뽑기권 받기: kind = normal · advanced · 스탯 (str · dex · int · vit — 스탯 룬 뽑기권) */
    private static int tickets(CommandContext<CommandSourceStack> c, int amount, String kind) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        mmorpg.core.StatTree.Ticket st = mmorpg.core.StatTree.tickets().get(kind);
        if (st != null) {
            for (int left = amount; left > 0; left -= 64) Notify.give(sp, RuneItems.ticketById(st.item(), Math.min(64, left)));
            tell(sp, Notify.PREFIX + st.name() + " " + amount + "장 지급");
            return amount;
        }
        boolean adv = "advanced".equals(kind);
        for (int left = amount; left > 0; left -= 64) Notify.give(sp, RuneItems.ticket(Math.min(64, left), adv));
        tell(sp, Notify.PREFIX + RuneKit.RUNE.str(adv ? "advTicketName" : "ticketName") + " " + amount + "장 지급");
        return amount;
    }

    /** 룬 바로 쓰기는 없어짐 (11단계): 룬은 스킬 칸에 올려 키로만 씀 — 안내만 */
    private static int quick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        tell(sp, Notify.PREFIX + "룬 바로 쓰기는 없어졌습니다. §7룬은 스킬 칸 (기본 J 키 — 스킬 칸 창) 에 올려 칸의 키로 씁니다 §8(룬을 들고 우클릭 = 빈 칸에 올리기)");
        return 1;
    }
}
