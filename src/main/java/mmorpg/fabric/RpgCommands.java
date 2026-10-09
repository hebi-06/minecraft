package mmorpg.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Grade;
import mmorpg.core.Hp;
import mmorpg.core.Js;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 명령어 (commands.js · main.js). /rpg … 아래에 모두, 애드온과 같은 이름 (/stats · /points · /weapon · /heal) 도.
 * 관리자 (권한 2) 전용: 포인트 · 무기 · 회복 · 초기화 · 스탯 정하기 · 스탯 책.
 */
public final class RpgCommands {
    private RpgCommands() {}

    private static final List<String> HELP = List.of(
            "§6[RPG 명령어]",
            "§f/stats §7- 스탯 창 (키 K · 스탯 책도 됨)",
            "§f/rpg info §7- 내 스탯 · 체력 · 기준 무기",
            "§f/rpg spend str 1 §7- 포인트 투자 (str 힘 · dex 민첩 · int 마력 · vit 체력)",
            "§f/weapon sword 10 rare §7- 무기 받기 (관리자) — 종류 sword·dagger·axe·shield·bow·wand·greatsword, 등급 common·rare·noble·hero·transcend",
            "§f/points 5 §7- 스탯 포인트 (관리자), §f/heal §7- 체력 회복 (관리자)",
            "§f/rpg reset §7- 스탯 초기화 (관리자), §f/rpg set str 20 §7- 스탯 값 정하기 (관리자 · 시험용), §f/rpg book §7- 스탯 책");

    private static final SuggestionProvider<CommandSourceStack> STATS =
            (c, b) -> SharedSuggestionProvider.suggest(Stats.STAT_KEYS, b);
    private static final SuggestionProvider<CommandSourceStack> TYPES = (c, b) -> {
        List<String> types = new ArrayList<>(Rules.get().strings("TYPE_ORDER"));
        for (String t : Rules.get().obj("TYPE_ONLY").keys()) if (!types.contains(t)) types.add(t);
        return SharedSuggestionProvider.suggest(types, b);
    };
    private static final SuggestionProvider<CommandSourceStack> GRADES =
            (c, b) -> SharedSuggestionProvider.suggest(Rules.get().grades().stream().map(Grade::id).toList(), b);

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rpg")
                .executes(c -> help(c.getSource()))
                .then(Commands.literal("help").executes(c -> help(c.getSource())))
                .then(Commands.literal("stats").executes(c -> stats(c)))
                .then(Commands.literal("info").executes(c -> info(c)))
                .then(Commands.literal("spend")
                        .then(Commands.argument("stat", StringArgumentType.word()).suggests(STATS)
                                .executes(c -> spend(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                                        .executes(c -> spend(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(admin("points").then(pointsArg()))
                .then(admin("heal").executes(c -> heal(c)))
                .then(admin("reset").executes(c -> reset(c)))
                .then(admin("book").executes(c -> book(c)))
                .then(admin("weapon").then(weaponArgs()))
                .then(admin("set")
                        .then(Commands.argument("stat", StringArgumentType.word()).suggests(STATS)
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100000))
                                        .executes(c -> set(c))))));
        // 애드온과 같은 이름
        d.register(Commands.literal("stats").executes(c -> stats(c)));
        d.register(admin("points").then(pointsArg()));
        d.register(admin("weapon").then(weaponArgs()));
        d.register(admin("heal").executes(c -> heal(c)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> pointsArg() {
        return Commands.argument("n", IntegerArgumentType.integer(-100000, 100000)).executes(c -> points(c));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> weaponArgs() {
        return Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                        .then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
                                .executes(c -> weapon(c))));
    }

    private static int help(CommandSourceStack src) {
        for (String l : HELP) src.sendSystemMessage(Component.literal(BedrockText.toJava(l)));
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RpgNet.openStats(c.getSource().getPlayerOrException());
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        RpgData d = RpgData.of(sp);
        StatSheet s = StatSheet.of(d, sp.experienceLevel);
        Notify.say(sp, "최고 레벨 " + d.lvmax() + " · 남은 포인트 " + d.point() + " · 체력 " + Hp.hpNumber(sp.isDeadOrDying(), sp.getHealth())
                + " / " + Js.str(Js.round(sp.getMaxHealth() * 10) / 10.0));
        for (String key : Stats.STAT_KEYS) sp.sendSystemMessage(Component.literal(BedrockText.toJava(" " + s.statLine(key, false))));
        Weapon w = StatSheet.referenceWeapon(sp.getMainHandItem(), sp.getOffhandItem(), sp.getInventory().items, s::usable);
        for (String l : s.weaponLines(w, false)) sp.sendSystemMessage(Component.literal(BedrockText.toJava(" " + l)));
        return 1;
    }

    private static int spend(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(c, "stat");
        if (!Stats.STAT_KEYS.contains(key)) {
            c.getSource().sendFailure(Component.literal("스탯: str · dex · int · vit"));
            return 0;
        }
        int n = RpgPlayers.spend(sp, key, count);
        if (n <= 0) {
            Notify.say(sp, "§c포인트가 없습니다.");
            return 0;
        }
        StatSheet s = StatSheet.of(RpgData.of(sp), sp.experienceLevel);
        Notify.say(sp, Stats.info(key).str("name") + " +" + n + "§7 → " + s.statLine(key, false));
        return n;
    }

    private static int points(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int n = IntegerArgumentType.getInteger(c, "n");
        RpgPlayers.addPoints(sp, n);
        Notify.say(sp, "스탯 포인트 " + (n >= 0 ? "+" : "") + n);
        return 1;
    }

    private static int heal(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        RpgHealth.healFull(sp);
        Notify.say(sp, "체력을 회복했습니다.");
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Notify.say(sp, "스탯 초기화: 포인트 " + RpgPlayers.reset(sp) + " 반환");
        return 1;
    }

    private static int book(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Notify.give(sp, new ItemStack(RpgItems.STAT_BOOK));
        Notify.say(sp, "스탯 책을 받았습니다.");
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(c, "stat");
        if (!Stats.STAT_KEYS.contains(key)) {
            c.getSource().sendFailure(Component.literal("스탯: str · dex · int · vit"));
            return 0;
        }
        RpgPlayers.setStat(sp, key, IntegerArgumentType.getInteger(c, "value"));
        Notify.say(sp, StatSheet.of(RpgData.of(sp), sp.experienceLevel).statLine(key, false));
        return 1;
    }

    /** 무기 받기 (/weapon sword 10 rare — commands.js weapon) */
    private static int weapon(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String type = StringArgumentType.getString(c, "type");
        int level = IntegerArgumentType.getInteger(c, "level");
        String grade = StringArgumentType.getString(c, "grade");
        RpgWeaponItem item = RpgItems.byId(Weapons.weaponId(type, level, grade));
        if (item == null) {
            Grade g = Rules.get().grade(grade);
            String gname = g == null ? grade : g.name();
            c.getSource().sendFailure(Component.literal(BedrockText.toJava("§c없는 무기입니다: " + gname + " 등급은 " + (g == null ? "?" : Weapons.gradeLevelsText(grade))
                    + " 무기만 있습니다. §7(종류 sword·dagger·axe·shield·bow·wand·greatsword)")));
            return 0;
        }
        Notify.give(sp, new ItemStack(item));
        Notify.say(sp, item.weapon().name() + "§r§f 지급" + Weapons.levelNote(item.weapon(), RpgCombat.reqLevelOf(sp)));
        return 1;
    }
}
