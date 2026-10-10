package mmorpg.fabric.shop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Armor;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 상점 · 장비 명령어 (commands.js): /shop [레벨] · /armor 종류 레벨 [등급] · /raidmat 종류 [수] · /ore [수] · /loot 종류 [수] (모두 관리자).
 */
public final class ShopCommands {
    private ShopCommands() {}

    private static final Rules R = Rules.get();

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(admin("shop")
                .executes(c -> shop(c, (int) (double) R.numbers("LEVELS").get(0)))
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100)).executes(c -> shop(c, IntegerArgumentType.getInteger(c, "level")))));
        d.register(admin("armor")
                .then(Commands.argument("type", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(R.strings("ARMOR_TYPE_ORDER"), b))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                                .executes(c -> armor(c, "common"))
                                .then(Commands.argument("grade", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(R.obj("ARMOR_GRADE_LEVELS").keys(), b))
                                        .executes(c -> armor(c, StringArgumentType.getString(c, "grade")))))));
        d.register(admin("raidmat")
                .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(R.strings("RAID_MATERIAL_ORDER"), b))
                        .executes(c -> raidmat(c, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 640)).executes(c -> raidmat(c, IntegerArgumentType.getInteger(c, "amount"))))));
        d.register(admin("ore")
                .executes(c -> ore(c, 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 640)).executes(c -> ore(c, IntegerArgumentType.getInteger(c, "amount")))));
        d.register(admin("loot")
                .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(R.strings("MONSTER_ORDER"), b))
                        .executes(c -> loot(c, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 640)).executes(c -> loot(c, IntegerArgumentType.getInteger(c, "amount"))))));
    }

    private static int shop(CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
        Store.openStore(c.getSource().getPlayerOrException(), level, "weapons");
        return 1;
    }

    private static int armor(CommandContext<CommandSourceStack> c, String grade) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String type = StringArgumentType.getString(c, "type");
        int lv = IntegerArgumentType.getInteger(c, "level");
        List<Armor.Piece> pieces = new ArrayList<>();
        for (String p : Armor.PIECE_ORDER) pieces.add(Armor.armorFrom(type, lv, grade, p));
        if (pieces.contains(null)) {
            Obj gl = R.obj("ARMOR_GRADE_LEVELS");
            Notify.say(sp, "§c없는 갑옷입니다. §7(일반 " + join(gl.numbersOrNull("common")) + "렙, 초월 " + join(gl.numbersOrNull("transcend")) + "렙)");
            return 0;
        }
        for (Armor.Piece a : pieces) Inv.give(sp, ArmorShop.armorItem(a));
        Notify.say(sp, "갑옷 지급: " + pieces.get(0).name().replace(pieces.get(0).pieceName(), "세트"));
        return 1;
    }

    private static String join(List<Double> l) {
        return String.join("·", l.stream().map(Js::str).toList());
    }

    /** 아이템 amount 개를 64 씩 */
    static void giveMany(ServerPlayer sp, Item item, int amount) {
        for (int left = amount; left > 0; left -= 64) Inv.give(sp, new ItemStack(item, Math.min(64, left)));
    }

    private static int raidmat(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Obj m = R.obj("RAID_MATERIALS").objOrNull(StringArgumentType.getString(c, "kind"));
        if (m == null) {
            Notify.say(sp, "§c종류: " + String.join(" · ", R.strings("RAID_MATERIAL_ORDER")));
            return 0;
        }
        giveMany(sp, Icon.itemById(m.str("item")), amount);
        Notify.say(sp, m.str("name") + " " + amount + "개 지급");
        return 1;
    }

    private static int ore(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Obj smith = R.obj("SMITH");
        giveMany(sp, Icon.itemById(smith.str("oreItem")), amount);
        Notify.say(sp, smith.str("oreName") + " " + amount + "개 지급");
        return 1;
    }

    private static int loot(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Obj d = R.obj("MONSTER_DROPS").objOrNull(StringArgumentType.getString(c, "kind"));
        if (d == null) {
            Notify.say(sp, "§c종류: " + String.join(" · ", R.strings("MONSTER_ORDER")));
            return 0;
        }
        giveMany(sp, Icon.itemById(d.str("item")), amount);
        Notify.say(sp, d.str("name") + " " + amount + "개 지급");
        return 1;
    }
}
