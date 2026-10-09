package mmorpg.fabric.smoke;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.client.StatScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CI 화면 시험 (-Dmmorpg.smoke=<폴더> 일 때만): 클라이언트를 띄워 평평한 세계를 만들고, 무기 · 스탯을 준비한 뒤
 * 평타 · 치명타 · 피해 · 낮은 체력 · 스탯 창 · 활 · 완드 장면을 차례로 찍음 (screenshots/*.png). 끝나면 DONE 을 쓰고 게임을 끔.
 * 모드 jar 에는 들어가지 않음.
 */
public final class SmokeClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-smoke");
    private static final String OUT = System.getProperty("mmorpg.smoke");

    private record Step(int delay, String name, Consumer<Minecraft> run) {}

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private int stage;
    private int ticks;
    private int index;
    private int waitLeft;

    @Override
    public void onInitializeClient() {
        if (OUT == null) return;
        LOG.info("[smoke] enabled, out={}", OUT);
        buildSteps();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        ticks++;
        if (ticks > 20 * 60 * 8) {
            LOG.error("[smoke] timeout at stage {} step {}", stage, index);
            finish(mc, false);
            return;
        }
        switch (stage) {
            case 0 -> {
                if (mc.screen instanceof AccessibilityOnboardingScreen) mc.setScreen(new TitleScreen());
                if (mc.screen instanceof TitleScreen && ticks > 40) {
                    LOG.info("[smoke] creating world");
                    createWorld(mc);
                    stage = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null) {
                    if (++waitLeft >= 80) {
                        LOG.info("[smoke] in world, running {} steps", steps.size());
                        stage = 2;
                        index = 0;
                        waitLeft = steps.get(0).delay();
                    }
                } else {
                    waitLeft = 0;
                }
            }
            case 2 -> {
                if (index >= steps.size()) return;
                if (waitLeft-- > 0) return;
                Step s = steps.get(index++);
                LOG.info("[smoke] step {}: {}", index, s.name());
                try {
                    s.run().accept(mc);
                } catch (RuntimeException e) {
                    LOG.error("[smoke] step {} failed", s.name(), e);
                }
                if (index < steps.size()) waitLeft = steps.get(index).delay();
            }
            default -> {}
        }
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("smoke", GameType.SURVIVAL, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("smoke", settings, new WorldOptions(20261010L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    // ------------------------------------------------------------ 장면

    private void step(int wait, String name, Consumer<Minecraft> run) {
        steps.add(new Step(wait, name, run));
    }

    private void buildSteps() {
        step(0, "world rules", mc -> cmds(mc,
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                "time set 6000", "weather clear", "kill @e[type=slime]", "tp @s 0.5 -60 0.5 0 0", "xp set @s 30 levels"));
        // 30렙 영웅 무기 요구 스탯 (83) 보다 넉넉히
        step(10, "stats", mc -> cmds(mc, "rpg set str 120", "rpg set dex 120", "rpg set int 120", "rpg set vit 120"));
        step(10, "items", mc -> cmds(mc,
                "item replace entity @s hotbar.0 with rpg:sword_l30_hero",
                "item replace entity @s hotbar.1 with rpg:bow_l30_hero",
                "item replace entity @s hotbar.2 with rpg:wand_l30_hero",
                "item replace entity @s hotbar.3 with rpg:shield_l30_hero",
                "item replace entity @s hotbar.8 with rpg:stat_book"));
        String husk = "{NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],Health:1000f,"
                + "attributes:[{id:\"minecraft:generic.max_health\",base:1000d},{id:\"minecraft:generic.armor\",base:0d}]}";
        step(10, "targets", mc -> cmds(mc,
                "summon husk 0.5 -60 3.5 " + husk,
                "summon husk 0.5 -60 13.5 " + husk,
                "summon husk -3.5 -60 8.5 " + husk));
        step(40, "aim melee", mc -> {
            mc.player.getInventory().selected = 0;
            look(mc, 0);
        });
        step(10, "shot hud", mc -> shot(mc, "01_hud")); // 채팅이 보이는 채로 (체력 바 글자가 채팅창과 겹쳐도 깨지지 않는지)
        step(2, "hide chat", mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
        step(20, "attack", mc -> attack(mc, false));
        step(3, "shot melee", mc -> shot(mc, "02_melee_number"));
        step(20, "attack crit", mc -> attack(mc, true));
        step(3, "shot crit", mc -> shot(mc, "03_melee_crit"));
        step(2, "crit off", mc -> RpgCombat.forceCrit = null);
        step(5, "third person", mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        step(20, "attack 3p", mc -> attack(mc, false));
        step(3, "shot 3p", mc -> shot(mc, "04_third_person"));
        step(5, "first person", mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        step(10, "hurt", mc -> cmds(mc, "damage @s 60 minecraft:generic"));
        step(3, "shot hit", mc -> shot(mc, "05_hp_hit"));
        step(30, "hurt more", mc -> cmds(mc, "damage @s 100 minecraft:generic"));
        step(8, "shot low", mc -> shot(mc, "06_hp_low"));
        step(5, "open stats", mc -> mc.setScreen(new StatScreen()));
        step(10, "shot stats", mc -> shot(mc, "07_stats"));
        step(2, "hover str", mc -> hover(mc, mc.getWindow().getGuiScaledWidth() / 2 - 60, mc.getWindow().getGuiScaledHeight() / 2 - 111 + 34 + 10));
        step(6, "shot tooltip", mc -> shot(mc, "08_stats_tooltip"));
        step(3, "close", mc -> {
            mc.setScreen(null);
            hover(mc, 0, 0);
        });
        step(10, "aim bow", mc -> {
            mc.player.getInventory().selected = 1;
            look(mc, 1);
        });
        step(15, "fire bow", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        step(1, "shot bow", mc -> shot(mc, "09_bow_flight"));
        step(5, "shot bow hit", mc -> shot(mc, "10_bow_hit"));
        step(20, "aim wand", mc -> {
            mc.player.getInventory().selected = 2;
            look(mc, 2);
        });
        step(15, "cast wand", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        step(5, "shot cast", mc -> shot(mc, "11_wand_cast"));
        step(7, "shot wand hit", mc -> shot(mc, "12_wand_hit"));
        step(5, "third person", mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        step(25, "cast wand 3p", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        step(6, "shot cast 3p", mc -> shot(mc, "13_wand_cast_3p"));
        step(40, "done", mc -> finish(mc, true));
    }

    // ------------------------------------------------------------ 도움

    private static void cmds(Minecraft mc, String... commands) {
        for (String c : commands) mc.player.connection.sendCommand(c);
    }

    /** 가까운 순서로 i 번째 허스크를 바라봄 */
    private static void look(Minecraft mc, int i) {
        List<Husk> list = new ArrayList<>(mc.level.getEntitiesOfClass(Husk.class, new AABB(mc.player.blockPosition()).inflate(30)));
        list.sort(Comparator.comparingDouble(h -> h.distanceToSqr(mc.player)));
        if (i >= list.size()) {
            LOG.warn("[smoke] husk {} not found ({} husks)", i, list.size());
            return;
        }
        Husk h = list.get(i);
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(h.getX(), h.getEyeY() - 0.2, h.getZ()));
    }

    private static void attack(Minecraft mc, boolean crit) {
        RpgCombat.forceCrit = crit;
        List<Husk> list = new ArrayList<>(mc.level.getEntitiesOfClass(Husk.class, new AABB(mc.player.blockPosition()).inflate(6)));
        list.sort(Comparator.comparingDouble(h -> h.distanceToSqr(mc.player)));
        if (list.isEmpty()) {
            LOG.warn("[smoke] no husk to attack");
            return;
        }
        mc.gameMode.attack(mc.player, list.get(0));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** 마우스를 GUI 좌표 (x, y) 로 (툴팁 장면) */
    private static void hover(Minecraft mc, double guiX, double guiY) {
        try {
            double s = mc.getWindow().getGuiScale();
            Field fx = MouseHandler.class.getDeclaredField("xpos");
            Field fy = MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, guiX * s);
            fy.setDouble(mc.mouseHandler, guiY * s);
        } catch (ReflectiveOperationException e) {
            LOG.warn("[smoke] cannot move mouse", e);
        }
    }

    private void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LOG.info("[smoke] {}", msg.getString()));
        shots.add(name);
    }

    private void finish(Minecraft mc, boolean ok) {
        stage = 3;
        try {
            Path out = Path.of(OUT);
            Files.createDirectories(out);
            Files.writeString(out.resolve(ok ? "DONE" : "FAILED"), String.join("\n", shots) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[smoke] cannot write result", e);
        }
        LOG.info("[smoke] finished ok={} shots={}", ok, shots.size());
        mc.stop();
    }
}
