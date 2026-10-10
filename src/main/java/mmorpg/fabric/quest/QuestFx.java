package mmorpg.fabric.quest;

import java.util.concurrent.ThreadLocalRandom;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 퀘스트 연출 (quest.js sparkle · celebrateFx · broadcast · pathFx · legendFx · finaleFx · royalFx): 큰 글자 (Notify.title) +
 * 눈앞 빛기둥 · 마법진 섬광 · 폭발 · 발밑 충격파 (애드온 파티클 그대로 — SkillFx) + 모두에게 알림.
 */
final class QuestFx {
    private QuestFx() {}

    private static final Rules R = Rules.get();
    private static final Obj BOSSES = R.obj("BOSSES");
    private static final Obj ARMOR_TYPES = R.obj("ARMOR_TYPES");
    private static final double[] WHITE = {1, 1, 1};
    /** 전설 무기 등급 색 */
    private static final double[] HERO = {1.0, 0.8, 0.3};
    private static final double[] TRANSCEND = {1.0, 0.35, 0.35};
    private static final double[] ROYAL_COLOR = {0.86, 0.55, 1.0};
    private static final double[] FINALE = {1.0, 0.82, 0.35};

    /** 머리 위 반짝임 (베드락 바닐라 파티클 이름) */
    static void sparkle(ServerPlayer sp, String id) {
        Vec3 h = sp.getEyePosition();
        SkillFx.vanilla(sp.level(), id, new Vec3(h.x, h.y + 0.2, h.z));
    }

    /** 축하 연출: 눈앞 2.4칸에 빛기둥 + 마법진 섬광 + 폭발 + 발밑 충격파 (power 0~2: 길 고르기 · 전설 무기 · 마지막) */
    static void celebrateFx(ServerPlayer sp, double[] color, int power) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 ground = V.add(c, V.flat(sp.getViewVector(1F)), 2.4);
        double h = 3 + power * 1.5;
        Vec3 at = V.up(ground, 1.45);
        SkillFx.fx(level, "rpg:arrow_shot", V.up(ground, h), Opt.of("dir", V.UP, "speed", 0.0, "size", h, "thick", 0.6 + power * 0.2, "life", 0.9 + power * 0.2,
                "color", color, "alpha", 0.9));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(ground, h), Opt.of("dir", V.UP, "speed", 0.0, "size", h * 0.95, "thick", 0.2 + power * 0.06, "life", 0.8 + power * 0.2,
                "color", WHITE, "alpha", 1.0));
        SkillFx.fx(level, "rpg:magic_circle_flash", at, Opt.of("size", 1 + power * 0.2, "life", 0.7, "spin", ThreadLocalRandom.current().nextDouble() * 360,
                "progress", 1.0, "color", color, "alpha", 1.0));
        SkillFx.burst(level, at, color, 1.2 + power * 0.3, 0.45);
        SkillFx.shock(level, c, color, 2 + power, 0.6);
        SkillFx.sparks(level, at, color, 10 + power * 4, 5 + power);
        SkillFx.vanilla(level, "minecraft:totem_particle", V.up(c, 1.2));
        SkillFx.play(level, power >= 2 ? "random.totem" : "random.levelup", c, power >= 2 ? 1 : 1.2, 1);
        if (power >= 1) SkillFx.play(level, "firework.twinkle", c, 1.1, 1);
        if (power >= 2) {
            SkillFx.play(level, "firework.large_blast", c, 1, 1);
            for (int i = 0; i < 3; i++) {
                int k = i;
                Tasks.later(3 + i * 3, () -> SkillFx.shock(level, c, k % 2 == 1 ? WHITE : color, 3 + k * 1.5, 0.6));
            }
        }
    }

    /** 모두에게 채팅 */
    static void broadcast(MinecraftServer server, String text) {
        if (server != null) server.getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava(text)), false);
    }

    /** 무기의 길을 고름: 큰 글자 + 길 색 연출 + 모두에게 알림 */
    static void pathFx(ServerPlayer sp, String type) {
        MainQuests.MainPath p = MainQuests.MAIN_PATHS.get(type);
        if (p == null) return;
        Notify.title(sp, p.tint() + "§l" + p.name(), "§f스승: " + MainQuests.mainNpcCall(p.mentor()));
        celebrateFx(sp, p.color(), 0);
        broadcast(sp.level().getServer(), "§6[메인 퀘스트] §e" + sp.getName().getString() + "§f님이 " + p.tint() + p.name() + "§f을 걷기 시작했습니다!");
    }

    private static double[] rgb(Obj o, String key, double[] fallback) {
        if (o == null) return fallback;
        java.util.List<Double> c = o.numbersOrNull(key);
        return c == null || c.size() < 3 ? fallback : new double[] {c.get(0), c.get(1), c.get(2)};
    }

    /** 전설 무기 (영웅 · 초월) 를 받음. 초월 보스 전설 (무기 없음): 칭호 (honor) 를 큰 글자로 */
    static void legendFx(ServerPlayer sp, Quest.Reward reward, Quest q) {
        Weapon w = QuestText.rewardWeapon(reward);
        String name = sp.getName().getString();
        if (w == null && q != null && !q.honor.isEmpty()) {
            Obj boss = BOSSES.objOrNull(q.target);
            String bossName = boss != null ? boss.str("name") : q.targetName;
            Notify.title(sp, "§c§l" + q.honor, "§f" + bossName + " 토벌 §7· 전설 퀘스트 완료");
            celebrateFx(sp, rgb(boss, "color", TRANSCEND), 2);
            broadcast(sp.level().getServer(), "§c[전설 퀘스트] §e" + name + "§f님이 §c" + bossName + "§f을(를) 쓰러뜨리고 §6" + q.honor + "§f(이)가 되었습니다!");
            return;
        }
        if (w == null) return;
        Notify.title(sp, w.grade().color() + w.title(), "§7" + w.grade().name() + " 무기 획득!");
        String g = w.grade().id();
        celebrateFx(sp, g.equals("hero") ? HERO : g.equals("transcend") ? TRANSCEND : WHITE, g.equals("transcend") ? 2 : 1);
        broadcast(sp.level().getServer(), (q != null && q.side.equals("legend") ? "§c[전설 퀘스트]" : "§6[메인 퀘스트]") + " §e" + name + "§f님이 " + w.name() + "§r§f을(를) 얻었습니다!");
    }

    /** 메인 이야기의 끝 */
    static void finaleFx(ServerPlayer sp) {
        MainQuests.MainPath p = MainQuests.MAIN_PATHS.get(Quests.pathOf(sp));
        Notify.title(sp, "§6§l영웅의 귀환", "§f" + (p != null ? p.name() + " · " : "") + "메인 퀘스트 완료");
        celebrateFx(sp, p != null ? p.color() : FINALE, 2);
        if (p != null) broadcast(sp.level().getServer(), "§6[메인 퀘스트] §e" + sp.getName().getString() + "§f님이 " + p.tint() + p.name() + "§f을 끝까지 걸었습니다!");
    }

    /** 왕실 퀘스트의 끝: 큰 글자 "왕실의 수호자" + 보랏빛 연출, 모두에게 알림 */
    static void royalFx(ServerPlayer sp, Quest.Reward reward) {
        Obj type = reward.armorType.isEmpty() ? null : ARMOR_TYPES.objOrNull(reward.armorType);
        Notify.title(sp, "§d§l왕실의 수호자", "§f" + QuestText.EMPEROR + " 갑옷" + (type != null ? " · " + type.str("name") : "") + " §7(초월)");
        celebrateFx(sp, ROYAL_COLOR, 2);
        broadcast(sp.level().getServer(), "§d[왕실 퀘스트] §e" + sp.getName().getString() + "§f님이 왕실의 수호자가 되어 §c" + QuestText.EMPEROR + " 갑옷§f을 받았습니다!");
    }
}
