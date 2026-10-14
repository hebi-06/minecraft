package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneSystem;
import mmorpg.fabric.rune.SkillBar;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Transcend;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 12단계 새 무기 사진첩 (화면 시험, only=weapons12): 평평한 세계에서
 * <ol>
 *   <li>임시 디자인 20개 (일반 · 희귀 · 고귀 · 영웅 · 초월) 를 손에 든 모습 (1인칭 · 3인칭 앞) — wm_hold_&lt;종류&gt;_&lt;등급&gt;[_3p]</li>
 *   <li>가방 아이콘 · 설명 (궁니르 · 카두케우스) — wm_inventory · wm_tooltip_&lt;종류&gt;</li>
 *   <li>기본 공격 (고귀 20렙 — 창 찌르기 · 낫 넓게 베기 · 망치 내려찍기 · 홀 성스러운 일격, 옆 과녁에 번진 숫자) — wm_basic_&lt;종류&gt;[_3p]_t3 · t6</li>
 *   <li>스킬 12개를 실제 입력 (우클릭 = 스킬1 · 웅크리고 우클릭 = 스킬2) 으로 — wm_&lt;스킬&gt;_&lt;틱&gt;[_3p]</li>
 *   <li>정령왕 강림 (스킬 칸 1번의 키) — wm_king_&lt;틱&gt;[_3p]</li>
 *   <li>대장장이 제작 창 (고귀 · 영웅 · 초월 쪽) — wm_craft_&lt;등급&gt;, 무기 상점 (기본 쪽 → 새 무기 쪽) — wm_shop_page1 · 2</li>
 * </ol>
 * 스킬마다 써졌는지 (재사용 대기) · 들어간 피해를 로그 (weapons12-gallery) 로, 써지지 않았거나 피해가 0 이면 마지막에 실패.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgWeaponsMoreGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-weapons12-gallery");
    /** 먼 과녁: 앞쪽 무리 넷 (6~9칸) + 옆 둘 — 평평한 세계 땅 y = -60 */
    private static final Vec3[] FAR = {
        new Vec3(0.5, -60, 6.5), new Vec3(-1.0, -60, 7.5), new Vec3(2.0, -60, 7.0), new Vec3(0.5, -60, 9.0), new Vec3(-3.5, -60, 4.5), new Vec3(4.0, -60, 5.0)
    };
    /** 가까운 과녁: 앞 · 양옆 앞 (2.5~4칸) + 양옆 */
    private static final Vec3[] NEAR = {
        new Vec3(0.5, -60, 3.5), new Vec3(-1.8, -60, 3.0), new Vec3(2.8, -60, 3.0), new Vec3(0.5, -60, 5.5), new Vec3(-3.2, -60, 1.0), new Vec3(4.2, -60, 1.0)
    };
    private static final String[] TYPES = {"lance", "scythe", "hammer", "scepter"};

    /** 스킬 하나: 무기 · 몇 번째 스킬 · 사진 틱 · 끝까지 기다릴 틱 · 가까운 과녁인지 */
    private record Case(String weapon, int index, int[] shots, int total, boolean near) {}

    private static final Case[] CASES = {
        new Case("lance_l40_hero", 0, new int[] {3, 8, 14}, 36, false),
        new Case("lance_l40_transcend", 0, new int[] {4, 12, 22}, 40, false),
        new Case("lance_l40_transcend", 1, new int[] {12, 26, 32, 46}, 70, false),
        new Case("scythe_l40_hero", 0, new int[] {3, 12, 26}, 40, true),
        new Case("scythe_l40_transcend", 0, new int[] {3, 7, 20, 35}, 50, true),
        new Case("scythe_l40_transcend", 1, new int[] {8, 30, 62, 74}, 92, true),
        new Case("hammer_l40_hero", 0, new int[] {4, 8, 18, 26}, 40, true),
        new Case("hammer_l40_transcend", 0, new int[] {4, 10, 15, 22}, 56, false),
        new Case("hammer_l40_transcend", 1, new int[] {8, 30, 70, 118, 122}, 140, true),
        new Case("scepter_l40_hero", 0, new int[] {4, 10, 24, 44}, 66, false),
        new Case("scepter_l40_transcend", 0, new int[] {4, 14, 34, 58}, 80, false),
        new Case("scepter_l40_transcend", 1, new int[] {6, 40, 100, 104}, 120, true),
    };

    private final List<String> failures = new ArrayList<>();
    private int lives;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("weapons12")) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만 · 고른 시험만 (MMORPG_ONLY)
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("xp set @a 40 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(10);
            for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 150");
            holds(ctx, server);
            inventory(ctx, server);
            basics(ctx, server);
            for (Case c : CASES) {
                run(ctx, server, c, false);
                run(ctx, server, c, true);
            }
            king(ctx, server, false);
            king(ctx, server, true);
            craft(ctx, server);
            shop(ctx, server);
        }
        LOG.info("[weapons12-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("weapons12 gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 손에 든 모습 · 가방

    private void holds(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        for (String type : TYPES) {
            for (String grade : new String[] {"common", "rare", "noble", "hero", "transcend"}) {
                String id = type + "_l" + (grade.equals("hero") || grade.equals("transcend") ? 40 : 20) + "_" + grade;
                String design = type + "_" + grade;
                server.runCommand("item replace entity @a hotbar.0 with rpg:" + id);
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
                ctx.waitTicks(10);
                ctx.takeScreenshot(TestScreenshotOptions.of("wm_hold_" + design).withSize(960, 540));
                ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
                ctx.waitTicks(10);
                ctx.takeScreenshot(TestScreenshotOptions.of("wm_hold_" + design + "_3p").withSize(960, 540));
            }
        }
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    private void inventory(ClientGameTestContext ctx, TestServerContext server) {
        int slot = 0;
        for (String type : TYPES) {
            for (String id : new String[] {type + "_l10_noble", type + "_l20_noble", type + "_l30_hero", type + "_l40_hero", type + "_l40_transcend"}) {
                String target = slot < 9 ? "hotbar." + slot : "inventory." + (slot - 9);
                server.runCommand("item replace entity @a " + target + " with rpg:" + id);
                slot++;
            }
        }
        ctx.waitTicks(5);
        ctx.getInput().pressKey(options -> options.keyInventory);
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof InventoryScreen, 40);
        } catch (AssertionError | RuntimeException e) {
            failures.add("inventory did not open");
            return;
        }
        ctx.waitTicks(5);
        ctx.takeScreenshot("wm_inventory");
        // 설명: 궁니르 (핫바 4 — 창 초월) · 카두케우스 (가방 — 홀 영웅 40)
        hover(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 18 * 4 + 8, (sc.height - 166) / 2 + 142 + 8});
        ctx.takeScreenshot("wm_tooltip_lance");
        int inv = 15 + 3 - 9; // 홀 영웅 40 = 18번째 (0부터) → 가방 칸 9
        hover(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 18 * (inv % 9) + 8, (sc.height - 166) / 2 + 84 + 18 * (inv / 9) + 8});
        ctx.takeScreenshot("wm_tooltip_scepter");
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        server.runCommand("clear @a");
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] q = sc == null ? null : where.apply(sc);
            if (q == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 기본 공격

    private void basics(ClientGameTestContext ctx, TestServerContext server) {
        for (String type : TYPES) {
            for (boolean third : new boolean[] {false, true}) {
                server.runCommand("tp @a 0.5 -60 0.5 0 10");
                server.runCommand("item replace entity @a hotbar.0 with rpg:" + type + "_l20_noble");
                server.runOnServer(s -> {
                    ServerPlayer sp = player(s);
                    clearHusks(sp);
                    RpgHealth.healFull(sp);
                    if (type.equals("scepter")) sp.setHealth(sp.getMaxHealth() * 0.6F);
                    // 맞힐 과녁 (앞 2.6칸 — 창은 3.7칸) + 옆 · 뒤
                    double front = type.equals("lance") ? 3.7 : 2.6;
                    spawnHusks(sp.level(), new Vec3[] {new Vec3(0.5, -60, 0.5 + front), new Vec3(2.0, -60, 2.9), new Vec3(-1.2, -60, 3.4)});
                    RpgCombat.forceCrit = false;
                });
                ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
                ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
                ctx.waitTicks(25); // 공격 쿨타임이 다 참
                double front = type.equals("lance") ? 3.7 : 2.6;
                // 과녁을 바라본 뒤 (조준점이 과녁에 놓이게 2틱) 실제 공격 키 — 휘두르는 손 · 조준점의 몹을 침. 사진은 서버 틱에 맞춤 (참격은 서버가 보냄)
                ctx.runOnClient(mc -> mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, -60 + 1.3, 0.5 + front)));
                ctx.waitTicks(2);
                long t0 = serverNow(server);
                ctx.getInput().pressKey(options -> options.keyAttack);
                String tag = "wm_basic_" + type + (third ? "_3p" : "");
                waitServer(ctx, server, t0 + 3);
                ctx.takeScreenshot(TestScreenshotOptions.of(tag + "_t3").withSize(1280, 720));
                waitServer(ctx, server, t0 + 6);
                ctx.takeScreenshot(TestScreenshotOptions.of(tag + "_t6").withSize(1280, 720));
                double[] dealt = server.computeOnServer(s -> {
                    ServerPlayer sp = player(s);
                    List<Husk> hs = sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(12), h -> true);
                    double main = 0;
                    double side = 0;
                    for (Husk h : hs) {
                        if (Math.abs(h.getZ() - (0.5 + front)) < 0.2) main += TestDummies.dealt(h);
                        else side += TestDummies.dealt(h);
                    }
                    RpgCombat.forceCrit = null;
                    return new double[] {main, side, sp.getHealth() / sp.getMaxHealth()};
                });
                LOG.info("[weapons12-gallery] basic {} {} main={} side={} hp={}", type, third ? "3p" : "1p", fmt(dealt[0]), fmt(dealt[1]), fmt(dealt[2]));
                if (!(dealt[0] > 0)) failures.add("basic " + type + " did not hit");
                if ((type.equals("scythe") || type.equals("hammer")) && !(dealt[1] > 0)) failures.add("basic " + type + " did not splash");
            }
        }
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    // ------------------------------------------------------------ 스킬

    private void run(ClientGameTestContext ctx, TestServerContext server, Case c, boolean third) {
        Weapon w = Weapons.parseWeapon("rpg:" + c.weapon());
        Obj k = w.skills().get(c.index());
        String id = k.str("id");
        server.runCommand("time set 6000");
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
        server.runCommand("item replace entity @a hotbar.0 with rpg:" + c.weapon());
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            clearHusks(sp);
            Transcend.resetCooldowns(sp);
            Guards.clear(sp.getUUID());
            RpgHealth.healFull(sp);
            if (id.equals("dagda_smash") || id.equals("caduceus_serpents") || id.equals("apollo_sanctuary")) sp.setHealth(sp.getMaxHealth() * 0.5F);
            spawnHusks(sp.level(), c.near() ? NEAR : FAR);
        });
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(12);
        float hp0 = server.computeOnServer(s -> player(s).getHealth());
        if (c.index() == 1) {
            ctx.getInput().holdShift();
            ctx.waitTicks(3);
        }
        long t0 = serverNow(server) + 1; // 우클릭 패킷을 서버가 받는 틱 (사진은 서버 틱에 맞춤 — 사진을 찍는 동안에도 서버는 돎)
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        if (c.index() == 1) ctx.getInput().releaseShift();
        for (int shot : c.shots()) {
            waitServer(ctx, server, t0 + shot);
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "wm_%s_%03d%s", id, shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        waitServer(ctx, server, t0 + c.total());
        boolean cast = server.computeOnServer(s -> Transcend.cooldownLeft(player(s), k) > 0);
        double dealt = dealtAll(server);
        float hp1 = server.computeOnServer(s -> player(s).getHealth());
        LOG.info("[weapons12-gallery] {} {} cast={} dealt={} healed={}", id, third ? "3p" : "1p", cast, fmt(dealt), fmt(hp1 - hp0));
        String tag = id + (third ? " (3p)" : "");
        if (!cast) failures.add(tag + " not cast");
        else if (!(dealt > 0)) failures.add(tag + " dealt no damage");
        if (cast && (id.equals("dagda_smash") || id.equals("caduceus_serpents") || id.equals("apollo_sanctuary")) && !(hp1 > hp0)) failures.add(tag + " did not heal");
    }

    // ------------------------------------------------------------ 정령왕 강림

    private void king(ClientGameTestContext ctx, TestServerContext server, boolean third) {
        Runes.Rune r = Runes.parseRune(Runes.runeId("spirit", "transcend"));
        server.runCommand("time set 6000");
        server.runCommand("item replace entity @a hotbar.0 with air");
        server.runCommand("tp @a 0.5 -60 0.5 0 -4");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            clearHusks(sp);
            RuneCast.resetCooldowns(sp);
            RuneSystem.forget(sp.getUUID());
            lives++;
            for (int i = 0; i < lives; i++) RuneKit.lifeEnded(sp.getUUID());
            RpgHealth.healFull(sp);
            spawnHusks(sp.level(), FAR);
            SkillBar.set(sp, 0, RuneItems.stack(r.family(), r.grade()));
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON));
        ctx.waitTicks(12);
        long t0 = serverNow(server) + 1; // 키 → 스킬 칸 패킷을 서버가 받는 틱
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        // 정령 (0~) → 합체 (34) → 정령왕 (42~) → 불 (48) · 물 (58) · 바람 (68) · 땅 (78) → 붕괴 (94 · 97)
        int[] shots = third ? new int[] {20, 46, 62, 84, 100} : new int[] {8, 22, 38, 44, 50, 60, 70, 80, 98, 102};
        for (int shot : shots) {
            waitServer(ctx, server, t0 + shot);
            ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "wm_king_%03d%s", shot, third ? "_3p" : "")).withSize(1280, 720));
        }
        waitServer(ctx, server, t0 + 125);
        boolean cast = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), r) > 0);
        double dealt = dealtAll(server);
        LOG.info("[weapons12-gallery] spirit king {} cast={} dealt={}", third ? "3p" : "1p", cast, fmt(dealt));
        if (!cast) failures.add("spirit king not cast" + (third ? " (3p)" : ""));
        else if (!(dealt > 0)) failures.add("spirit king dealt no damage" + (third ? " (3p)" : ""));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    // ------------------------------------------------------------ 대장장이 제작 창

    private void craft(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> clearHusks(player(s)));
        String[] names = {"hero", "transcend", "noble"};
        for (int page = 0; page < 3; page++) {
            int p = page;
            server.runOnServer(s -> {
                ServerPlayer sp = player(s);
                Forms.close(sp);
                Smith.openCraftPage(sp, p);
            });
            ctx.waitTicks(10);
            ctx.takeScreenshot("wm_craft_" + names[page]);
        }
        server.runOnServer(s -> Forms.close(player(s)));
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 무기 상점 (기본 쪽 → 새 무기 쪽)

    private void shop(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("give @a minecraft:emerald 64");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Forms.close(sp);
            mmorpg.fabric.shop.Store.openStore(sp, 20, "weapons");
        });
        if (!waitShop(ctx, false)) failures.add("shop did not open");
        ctx.takeScreenshot("wm_shop_page1");
        // 쪽 넘김 칸을 실제 마우스로 누름 (윗줄 왼쪽 셋째 칸)
        clickSlot(ctx, mmorpg.fabric.shop.Shop.PAGE_TAB);
        if (!waitShop(ctx, true)) failures.add("shop page 2 not shown");
        ctx.takeScreenshot("wm_shop_page2");
        // 새 무기 칸 하나 (희귀 전투 망치) 에 마우스를 올려 설명
        hoverSlot(ctx, mmorpg.fabric.shop.Shop.firstSlot(1, 1) + 2);
        ctx.takeScreenshot("wm_shop_page2_tooltip");
        server.runOnServer(s -> Forms.close(player(s)));
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    /** 무기 상점 창 (newPage = 새 무기 쪽) 이 열릴 때까지 */
    private static boolean waitShop(ClientGameTestContext ctx, boolean newPage) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof mmorpg.fabric.client.form.ChestFormScreen c && c.form().title().contains("무기 상점")
                    && c.form().title().contains("새 무기") == newPage, 100);
            ctx.waitTicks(3);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    /** 상자 창의 칸을 실제 마우스로 누름 (칸 자리는 ChestFormScreen.slotCenter — 창 픽셀로 바꿈) */
    private static void clickSlot(ClientGameTestContext ctx, int slot) {
        if (slot < 0) return;
        double[] p = slotPixel(ctx, slot);
        if (p == null) return;
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    private static void hoverSlot(ClientGameTestContext ctx, int slot) {
        double[] p = slotPixel(ctx, slot);
        if (p == null) return;
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(4);
    }

    private static double[] slotPixel(ClientGameTestContext ctx, int slot) {
        return ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof mmorpg.fabric.client.form.ChestFormScreen c)) return null;
            int[] q = c.slotCenter(slot);
            if (q == null) return null;
            double k = (double) mc.getWindow().getScreenWidth() / c.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
    }

    // ------------------------------------------------------------ 도움

    private static long serverNow(TestServerContext server) {
        return server.computeOnServer(s -> Tasks.now());
    }

    /** 서버 틱 (Tasks.now) 이 at 이 될 때까지 클라이언트 틱을 넘김 (사진을 찍는 동안에도 서버가 돌아 클라이언트 틱으로 세면 연출보다 늦어짐) */
    private static void waitServer(ClientGameTestContext ctx, TestServerContext server, long at) {
        for (int i = 0; i < 400 && serverNow(server) < at; i++) ctx.waitTick();
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static double dealtAll(TestServerContext server) {
        return server.computeOnServer(s -> {
            double sum = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(40), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void clearHusks(ServerPlayer sp) {
        for (Husk h : sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(48), h -> true)) h.discard();
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt) — 나를 봄 */
    private static void spawnHusks(ServerLevel level, Vec3[] spots) {
        TestDummies.reset();
        for (Vec3 at : spots) {
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (h == null) continue;
            TestDummies.make(h);
            h.snapTo(at.x, at.y, at.z, 180F, 0F);
            h.setYHeadRot(180F);
            h.setYBodyRot(180F);
            level.addFreshEntity(h);
        }
    }
}
