package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.core.Runes;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.client.ClientJobBar;
import mmorpg.fabric.client.ClientSkillBar;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.SkillBarHud;
import mmorpg.fabric.client.SkillBarScreen;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.rune.Breakthrough;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSystem;
import mmorpg.fabric.rune.SkillBar;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 11단계 스킬 칸 사진첩 (화면 시험, only=skillbar — 실제 키 · 마우스 입력): 키 목록 (바닐라 키와 겹치지 않는지 — 로그에 기본 키 표),
 * 빈 스킬 칸 HUD (안내), J 키 → 스킬 칸 창 → 룬을 끌어 칸에 놓기 (끄는 중 사진) · 누르면 빈 칸 · 룬 위에서 칸의 키 · 오른쪽 클릭 · 칸끼리 끌기 · 오른쪽 클릭 빼기 ·
 * 줄 밖으로 끌어 빼기 · 풀이, 칸의 키로 쓰기 (무기를 든 채 — 눌림 · 시전 연출 · 재사용 대기 덮개 · 다시 누르면 막힘 · 준비 반짝임), 메테오 (1 · 3인칭),
 * 룬을 들고 우클릭 = 쓰지 않고 빈 칸에 올림, 핫바에서 룬 칸을 골라도 쓰지 않음, 암살자 콤보 (스킬 칸 둘), 룬 설명 (조작 줄), 넓은 화면의 한 줄 HUD,
 * 0.26.0 직업 스킬 칸 5 (룬 칸 위 · 키 글자 ⇧R … — Shift + R 은 룬 칸 1 을 쓰지 않고 직업 칸 1, 빈 칸은 막힘 · 시험용 직업 스킬은 써지고 재사용 대기 · 창의 직업 줄).
 * 사진 이름 sb_… → ci-logs 의 shots/. 확인 항목은 로그 [skillbar-gallery] OK / FAIL.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgSkillBarGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-skillbar-gallery");
    /** 과녁: 앞 4~7칸 무리 — 평평한 세계 땅 y = -60 */
    private static final Vec3[] TARGETS = {new Vec3(0.5, -60, 4.5), new Vec3(-1.5, -60, 5.5), new Vec3(2.5, -60, 5.5), new Vec3(0.5, -60, 7.0)};
    /** 가방 9 ~ 에 넣을 룬 {계열, 등급, 돌파} */
    private static final String[][] BAG = {
        {"str", "common", "0"}, {"int", "transcend", "3"}, {"assassin", "rare", "0"}, {"assassin", "noble", "0"}, {"holy", "hero", "0"},
        {"vit", "rare", "2"}, {"storm", "noble", "0"}, {"element", "hero", "1"}, {"dex", "common", "0"}
    };

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("skillbar")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("xp set @a 40 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(10);
            for (String s : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + s + " 120");
            home(server);
            server.runOnServer(s -> {
                ServerPlayer sp = player(s);
                sp.getInventory().clearContent();
                sp.getInventory().setItem(0, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(
                        "rpg:sword_l40_hero"))));
                for (int i = 0; i < BAG.length; i++) sp.getInventory().setItem(9 + i, Breakthrough.stack(BAG[i][0], BAG[i][1], Integer.parseInt(BAG[i][2])));
                sp.getInventory().setItem(2, Breakthrough.stack("str", "rare", 0)); // 핫바의 룬 (우클릭 · 고르기)
                sp.getInventory().setSelectedSlot(0);
            });
            ctx.waitTicks(20);
            part(ctx, "keys", () -> keys(ctx));
            part(ctx, "empty", () -> empty(ctx));
            part(ctx, "window", () -> window(ctx, server, conn));
            part(ctx, "cast", () -> cast(ctx, server, conn));
            part(ctx, "rightclick", () -> rightClick(ctx, server, conn));
            part(ctx, "combo", () -> combo(ctx, server, conn));
            part(ctx, "job", () -> job(ctx, server, conn));
            part(ctx, "tooltip", () -> tooltip(ctx));
            part(ctx, "wide", () -> wide(ctx));
            ctx.setScreen(() -> null);
        }
        LOG.info("[skillbar-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("skill bar gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 키 목록

    /** 기본 키 표를 로그에 · 스킬 칸 · 창 · 스탯 · 장식 키가 바닐라 키 (디버그 — F3 과 함께 — 는 빼고) 와 겹치지 않는지 */
    private void keys(ClientGameTestContext ctx) {
        String[] r = ctx.computeOnClient(mc -> {
            StringBuilder table = new StringBuilder();
            List<String> conflicts = new ArrayList<>();
            List<KeyMapping> ours = new ArrayList<>(List.of(MmorpgClient.skillKeys));
            ours.add(MmorpgClient.skillBarKey);
            ours.add(MmorpgClient.statsKey);
            ours.add(MmorpgClient.cosmeticKey);
            for (KeyMapping k : mc.options.keyMappings) {
                table.append(k.getName()).append('=').append(k.getDefaultKey().getName()).append(' ');
            }
            for (KeyMapping o : ours) {
                for (KeyMapping k : mc.options.keyMappings) {
                    if (k == o || k.getCategory().equals(KeyMapping.Category.DEBUG)) continue;
                    if (k.getDefaultKey().equals(o.getDefaultKey())) conflicts.add(o.getName() + " ~ " + k.getName() + " (" + o.getDefaultKey().getName() + ")");
                }
            }
            StringBuilder labels = new StringBuilder();
            for (int i = 0; i < ClientSkillBar.SIZE; i++) labels.append(ClientSkillBar.keyLabel(i)).append(' ');
            return new String[] {table.toString(), String.join(", ", conflicts), labels.toString().trim()};
        });
        LOG.info("[skillbar-gallery] default keys: {}", r[0]);
        check(r[1].isEmpty(), "skill keys do not share a default key with any other mapping (" + r[1] + ")");
        check(r[2].equals("R Z V B Y H N M"), "slot key labels '" + r[2] + "'");
    }

    // ------------------------------------------------------------ 빈 칸 HUD

    private void empty(ClientGameTestContext ctx) {
        check(ctx.computeOnClient(mc -> ClientSkillBar.received() && !ClientSkillBar.any()), "bar received, empty");
        String fit = ctx.computeOnClient(mc -> {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            StringBuilder b = new StringBuilder("gui " + w + "x" + h + ":");
            boolean ok = true;
            for (int i = 0; i < ClientSkillBar.SIZE; i++) {
                int[] p = SkillBarHud.slotPos(w, h, i);
                b.append(' ').append(p[0]).append(',').append(p[1]);
                boolean inside = p[0] >= 0 && p[1] >= 0 && p[0] + SkillBarHud.S <= w && p[1] + SkillBarHud.S <= h;
                boolean overHotbar = p[0] + SkillBarHud.S > w / 2 - 91 && p[0] < w / 2 + 91 && p[1] + SkillBarHud.S > h - 22;
                boolean overHud = p[0] < w / 2 + 152 && p[0] + SkillBarHud.S > w / 2 + 12 && p[1] < h - 31 && p[1] + SkillBarHud.S > h - 47;
                if (!inside || overHotbar || overHud) ok = false;
            }
            return (ok ? "OK " : "BAD ") + b;
        });
        LOG.info("[skillbar-gallery] hud layout {}", fit);
        check(fit.startsWith("OK"), "HUD slots inside the screen, off the hotbar and the RPG HUD row (" + fit + ")");
        shot(ctx, "sb_hud_empty");
    }

    // ------------------------------------------------------------ 스킬 칸 창

    private void window(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.skillBarKey);
        if (!waitScreen(ctx, sc -> sc instanceof SkillBarScreen, "skill bar window (J)")) ctx.setScreen(SkillBarScreen::new);
        ctx.waitTicks(3);
        away(ctx);
        shot(ctx, "sb_window");
        int runes = ctx.computeOnClient(mc -> ((SkillBarScreen) mc.gui.screen()).runeSlots().size());
        check(runes == BAG.length + 1, "window lists the bag runes (" + runes + ")");
        // 1) 끌어 놓기: 그리드 첫 룬 (핫바 2 의 희귀 힘 룬 — 칸 순서) → 칸 1 (index 0)
        String first = invRuneAt(ctx, 0);
        drag(ctx, conn, s -> s.gridCenter(0), s -> s.barCenter(0), "sb_window_drag");
        check(bar(server, 0).equals(first), "drag grid → slot 1 (" + bar(server, 0) + " = " + first + ")");
        // 2) 누르기: 다음 룬 → 첫 빈 칸 (index 1)
        String second = invRuneAt(ctx, 0);
        click(ctx, conn, s -> s.gridCenter(0), InputConstants.MOUSE_BUTTON_LEFT);
        check(bar(server, 1).equals(second), "click grid → first empty slot 2 (" + bar(server, 1) + " = " + second + ")");
        // 3) 룬 위에서 칸의 키 (V = index 2)
        String third = invRuneAt(ctx, 0);
        hover(ctx, s -> s.gridCenter(0));
        ctx.getInput().pressKey(MmorpgClient.skillKeys[2]);
        settle(ctx, conn);
        check(bar(server, 2).equals(third), "hover + V → slot 3 (" + bar(server, 2) + " = " + third + ")");
        // 4) 오른쪽 클릭 (그리드) → 첫 빈 칸 (index 3)
        String fourth = invRuneAt(ctx, 0);
        click(ctx, conn, s -> s.gridCenter(0), InputConstants.MOUSE_BUTTON_RIGHT);
        check(bar(server, 3).equals(fourth), "right click grid → slot 4 (" + bar(server, 3) + ")");
        // 5) 칸끼리 끌기: 1 → 6
        drag(ctx, conn, s -> s.barCenter(0), s -> s.barCenter(5), null);
        check(bar(server, 5).equals(first) && bar(server, 0).equals("-"), "drag slot 1 → slot 6 (" + bar(server, 5) + ")");
        // 6) 오른쪽 클릭 (칸) → 빼기
        click(ctx, conn, s -> s.barCenter(5), InputConstants.MOUSE_BUTTON_RIGHT);
        check(bar(server, 5).equals("-") && bagHas(server, first), "right click slot 6 → back to the bag");
        // 7) 줄 밖으로 끌어 빼기 (칸 2 → 그리드)
        drag(ctx, conn, s -> s.barCenter(1), s -> s.gridCenter(20), null);
        check(bar(server, 1).equals("-") && bagHas(server, second), "drag slot 2 off the bar → back to the bag");
        // 칸을 채워 풀이 사진 (서버로 — 메테오 3돌 · 대지 강타 · 암살자 둘 · 신성 영웅)
        server.runOnServer(s -> fill(player(s)));
        settle(ctx, conn);
        hover(ctx, s -> s.barCenter(1));
        shot(ctx, "sb_window_tip");
        away(ctx);
        shot(ctx, "sb_window_full");
        ctx.getInput().pressKey(MmorpgClient.skillBarKey);
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "J closes the window");
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) ctx.setScreen(() -> null);
    }

    /** 칸: 0 대지 강타 · 1 메테오 3돌 · 2 암살자 희귀 · 3 암살자 고귀 · 4 신성 영웅 · 5 저항 희귀 2돌 (가방에서 옮김 — 가방에서는 빠짐) */
    private static void fill(ServerPlayer sp) {
        String[][] want = {{"str", "common"}, {"int", "transcend"}, {"assassin", "rare"}, {"assassin", "noble"}, {"holy", "hero"}, {"vit", "rare"}};
        for (int i = 0; i < SkillBar.SIZE; i++) SkillBar.unequip(sp, i);
        for (int i = 0; i < want.length; i++) {
            String id = Runes.runeId(want[i][0], want[i][1]);
            for (int j = 0; j < 36; j++) {
                Runes.Rune r = RuneItems.runeOf(sp.getInventory().getItem(j));
                if (r != null && r.id().equals(id)) {
                    SkillBar.equip(sp, j, i);
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------ 키로 쓰기

    private void cast(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        home(server);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            RuneCast.resetCooldowns(sp);
            RpgHealth.healFull(sp);
            sp.getInventory().setSelectedSlot(0); // 영웅 칼을 든 채
            spawnHusks(sp.level());
        });
        ctx.waitTicks(15);
        check(ctx.computeOnClient(mc -> mc.player.getMainHandItem().getItem() != net.minecraft.world.item.Items.AIR), "holding the sword");
        shot(ctx, "sb_hud_full");
        Runes.Rune slam = Runes.parseRune(Runes.runeId("str", "common"));
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        ctx.waitTicks(2);
        shot(ctx, "sb_cast_press");
        conn.waitForServerboundPackets();
        ctx.waitTicks(6);
        long cd = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), slam));
        double dealt = dealt(server);
        check(cd > 0, "R casts slot 1 (cooldown " + cd + ")");
        check(dealt > 0, "ground slam hit the targets (" + String.format(Locale.ROOT, "%.1f", dealt) + ")");
        check(ctx.computeOnClient(mc -> ClientSkillBar.cooldownFrac(0, 0) > 0.5), "HUD shows the cooldown");
        shot(ctx, "sb_cooldown");
        // 다시 누르면 막힘 (재사용 대기 그대로 줄어듦 — 다시 쓰이지 않음)
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        ctx.waitTicks(2);
        shot(ctx, "sb_deny");
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        long cd2 = server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), slam));
        check(cd2 < cd, "pressed during cooldown: not cast again (" + cd + " → " + cd2 + ")");
        // 메테오 (칸 2 — Z): 1인칭 사진
        ctx.getInput().pressKey(MmorpgClient.skillKeys[1]);
        ctx.waitTicks(12);
        shot(ctx, "sb_meteor_12");
        ctx.waitTicks(16);
        shot(ctx, "sb_meteor_28");
        Runes.Rune meteor = Runes.parseRune(Runes.runeId("int", "transcend"));
        check(server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), meteor) > 0 && Breakthrough.castLevel(player(s), "meteor") == 3), "Z casts the meteor at 3");
        // 준비: 대지 강타 재사용 대기가 끝나면 칸이 반짝
        boolean ready = waitFor(ctx, mc -> ClientSkillBar.cooldownLeft(0, 0) <= 0, 220);
        ctx.waitTicks(2);
        shot(ctx, "sb_ready");
        check(ready, "slot 1 ready again on the client");
        // 3인칭: 신성 영웅 (칸 5 — Y)
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(4);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[4]);
        ctx.waitTicks(10);
        shot(ctx, "sb_holy_3p");
        Runes.Rune holy = Runes.parseRune(Runes.runeId("holy", "hero"));
        check(server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), holy) > 0), "Y casts the holy rune");
        ctx.waitTicks(30);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    // ------------------------------------------------------------ 들고 우클릭 · 핫바에서 고르기

    private void rightClick(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            RuneCast.resetCooldowns(sp);
            SkillBar.unequip(sp, 7);
            sp.getInventory().setItem(3, Breakthrough.stack("dex", "rare", 0));
        });
        Runes.Rune held = Runes.parseRune(Runes.runeId("dex", "rare"));
        // 핫바에서 룬 칸을 골라도 쓰지 않음
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[3]);
        ctx.waitTicks(6);
        check(server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), held) == 0), "selecting a rune in the hotbar does not cast");
        // 들고 우클릭: 쓰지 않고 빈 칸에 올림
        int free = server.computeOnServer(s -> SkillBar.firstEmpty(player(s)));
        ctx.runOnClient(mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
        shot(ctx, "sb_rightclick");
        check(server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), held) == 0), "right click does not cast");
        String got = bar(server, free);
        check(got.equals(Runes.runeId("dex", "rare") + "@0") && ctx.computeOnClient(mc -> mc.player.getMainHandItem().isEmpty()), "right click → slot " + (free + 1)
                + " (" + got + ")");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(4);
    }

    // ------------------------------------------------------------ 암살자 콤보 (스킬 칸 둘)

    private void combo(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        home(server);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            RuneCast.resetCooldowns(sp);
            RuneSystem.forget(sp.getUUID());
            spawnHusks(sp.level());
        });
        ctx.waitTicks(10);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[2]);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[3]);
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        String hud = server.computeOnServer(s -> RuneCast.combo.hud(player(s))).replaceAll("§.", "");
        shot(ctx, "sb_combo");
        check(hud.contains("2 콤보"), "assassin combo from two slots ('" + hud + "')");
        ctx.waitTicks(50);
        shot(ctx, "sb_combo_after");
    }

    // ------------------------------------------------------------ 0.26.0 직업 스킬 칸 (Shift + 룬 칸 1 ~ 5 의 키)

    /** 시험용 직업 스킬 (칸 1 — 철 검 그림 · 재사용 대기 3초 · 쓰면 calls 하나 · 둘레 불꽃) */
    private static final java.util.concurrent.atomic.AtomicInteger JOB_CALLS = new java.util.concurrent.atomic.AtomicInteger();
    private static final JobSkills.Skill TEST_JOB = new JobSkills.Skill() {
        @Override
        public String id() {
            return "test_slash";
        }

        @Override
        public String name() {
            return "시험 직업 스킬";
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        }

        @Override
        public int cooldown() {
            return 60;
        }

        @Override
        public boolean cast(ServerPlayer sp) {
            JOB_CALLS.incrementAndGet();
            mmorpg.fabric.skill.SkillFx.sparks(sp.level(), sp.getEyePosition().add(sp.getViewVector(1F).scale(2)), new double[] {0.5, 0.85, 1}, 12, 4);
            return true;
        }
    };

    private void job(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        home(server);
        server.runOnServer(s -> {
            RuneCast.resetCooldowns(player(s));
            JobSkills.resetCooldowns(player(s));
        });
        ctx.waitTicks(5);
        // 자리: 화면 안 · 룬 칸 위 (겹치지 않음) · 핫바 · RPG HUD 줄과 겹치지 않음
        String fit = ctx.computeOnClient(mc -> {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            StringBuilder b = new StringBuilder("gui " + w + "x" + h + ":");
            boolean ok = true;
            int runeTop = Integer.MAX_VALUE;
            for (int i = 0; i < ClientSkillBar.SIZE; i++) runeTop = Math.min(runeTop, SkillBarHud.slotPos(w, h, i)[1]);
            for (int i = 0; i < ClientJobBar.SIZE; i++) {
                int[] p = SkillBarHud.jobSlotPos(w, h, i);
                b.append(' ').append(p[0]).append(',').append(p[1]);
                boolean inside = p[0] >= 0 && p[1] >= 12 && p[0] + SkillBarHud.S <= w && p[1] + SkillBarHud.S <= h;
                boolean overRunes = p[1] + SkillBarHud.S > runeTop - 2;
                boolean overHotbar = p[0] + SkillBarHud.S > w / 2 - 91 && p[0] < w / 2 + 91 && p[1] + SkillBarHud.S > h - 22;
                boolean overHud = p[0] < w / 2 + 152 && p[0] + SkillBarHud.S > w / 2 + 12 && p[1] < h - 31 && p[1] + SkillBarHud.S > h - 47;
                if (!inside || overRunes || overHotbar || overHud) ok = false;
            }
            return (ok ? "OK " : "BAD ") + b;
        });
        LOG.info("[skillbar-gallery] job layout {}", fit);
        check(fit.startsWith("OK"), "job slots inside the screen, above the rune slots, off the hotbar and the RPG HUD row (" + fit + ")");
        check(ctx.computeOnClient(mc -> ClientJobBar.received() && !ClientJobBar.any()), "job bar received, empty (no jobs yet)");
        String labels = ctx.computeOnClient(mc -> {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < ClientJobBar.SIZE; i++) b.append(ClientJobBar.keyLabel(i)).append(' ');
            return b.toString().trim();
        });
        check(labels.equals("⇧R ⇧Z ⇧V ⇧B ⇧Y"), "job key labels '" + labels + "'");
        shot(ctx, "sb_job_hud");
        // 빈 칸: Shift + R → 룬 칸 1 (대지 강타) 은 쓰지 않음 · 직업 칸 1 은 막힘 (안내)
        Runes.Rune slam = Runes.parseRune(Runes.runeId("str", "common"));
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        ctx.waitTicks(2);
        shot(ctx, "sb_job_empty_press");
        ctx.getInput().releaseKey(o -> o.keyShift);
        conn.waitForServerboundPackets();
        ctx.waitTicks(4);
        check(server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), slam) == 0), "Shift + R does not cast rune slot 1");
        // 시험용 직업 스킬 (칸 1) → Shift + R 로 씀 · 재사용 대기 덮개, 다시 누르면 막힘
        JOB_CALLS.set(0);
        // 0.27.0: 직업 칸은 JobSystem 의 것 — 시험용 스킬로 바꿨다가 끝에 되돌림
        java.util.concurrent.atomic.AtomicReference<java.util.function.BiFunction<ServerPlayer, Integer, JobSkills.Skill>> saved = new java.util.concurrent.atomic.AtomicReference<>();
        server.runOnServer(s -> {
            saved.set(JobSkills.provider);
            JobSkills.provider = (sp, i) -> i == 0 ? TEST_JOB : null;
            JobSkills.send(player(s));
        });
        conn.waitForClientboundPackets();
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> ClientJobBar.any() && !ClientJobBar.empty(0) && ClientJobBar.empty(1)), "job slot 1 shows the test skill");
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        ctx.waitTicks(2);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        shot(ctx, "sb_job_cast");
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        ctx.waitTicks(2);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        ctx.getInput().releaseKey(o -> o.keyShift);
        check(JOB_CALLS.get() == 1, "Shift + R casts job slot 1 once, then the cooldown blocks it (" + JOB_CALLS.get() + ")");
        check(server.computeOnServer(s -> JobSkills.cooldownLeft(player(s), TEST_JOB) > 0), "job skill cooldown on the server");
        check(ctx.computeOnClient(mc -> ClientJobBar.cooldownFrac(0, 0) > 0.3), "HUD shows the job cooldown");
        check(server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), slam) == 0), "rune slot 1 still not cast");
        // R 만 누르면 룬 칸 1
        ctx.getInput().pressKey(MmorpgClient.skillKeys[0]);
        ctx.waitTicks(2);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        check(server.computeOnServer(s -> RuneCast.cooldownLeft(player(s), slam) > 0), "R alone still casts rune slot 1");
        shot(ctx, "sb_job_and_rune");
        // 스킬 칸 창의 직업 줄
        ctx.getInput().pressKey(MmorpgClient.skillBarKey);
        if (!waitScreen(ctx, sc -> sc instanceof SkillBarScreen, "skill bar window (J)")) ctx.setScreen(SkillBarScreen::new);
        ctx.waitTicks(3);
        hover(ctx, sc -> sc.jobCenter(0));
        shot(ctx, "sb_window_job");
        hover(ctx, sc -> sc.jobCenter(2));
        shot(ctx, "sb_window_job_empty");
        ctx.setScreen(() -> null);
        ctx.waitTicks(2);
        server.runOnServer(s -> {
            if (saved.get() != null) JobSkills.provider = saved.get();
            JobSkills.resetCooldowns(player(s));
            JobSkills.send(player(s));
        });
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 룬 설명

    private void tooltip(ClientGameTestContext ctx) {
        String lore = ctx.computeOnClient(mc -> {
            StringBuilder b = new StringBuilder();
            for (Component c : Screen.getTooltipFromItem(mc, Breakthrough.stack("str", "noble", 0))) b.append(c.getString()).append(" / ");
            return b.toString();
        });
        check(lore.contains("우클릭: 빈 스킬 칸에 올리기") && lore.contains("칸의 키로 사용") && lore.contains("총 피해") && !lore.contains("위력×") && !lore.contains("runequick"),
                "rune lore lines (" + lore + ")");
        ctx.getInput().pressKey(options -> options.keyInventory);
        if (waitScreen(ctx, sc -> sc instanceof InventoryScreen, "inventory")) {
            // 가방 첫 줄 (9 ~) 의 룬
            hoverAny(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 8, (sc.height - 166) / 2 + 84 + 8});
            shot(ctx, "sb_rune_tooltip");
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    // ------------------------------------------------------------ 넓은 화면 (한 줄)

    private void wide(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.options.guiScale().set(3));
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.waitTicks(10);
        String row = ctx.computeOnClient(mc -> {
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            return w + "x" + h + " cols " + SkillBarHud.layout(w, h)[2];
        });
        LOG.info("[skillbar-gallery] wide {}", row);
        check(row.endsWith("cols " + ClientSkillBar.SIZE), "wide screen: one row (" + row + ")");
        shot(ctx, "sb_hud_wide");
        ctx.runOnClient(mc -> mc.options.guiScale().set(0));
        ctx.getInput().resizeWindow(1280, 720);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 도움

    private static String describe(ItemStack s) {
        Runes.Rune r = RuneItems.runeOf(s);
        return r == null ? (s.isEmpty() ? "-" : s.getItem().toString()) : r.id() + "@" + Breakthrough.of(s);
    }

    private static String bar(TestServerContext server, int i) {
        return server.computeOnServer(s -> describe(SkillBar.get(player(s), i)));
    }

    private static boolean bagHas(TestServerContext server, String what) {
        return server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            for (int i = 0; i < 36; i++) if (describe(sp.getInventory().getItem(i)).equals(what)) return true;
            return false;
        });
    }

    /** 창 그리드 n 번째 룬 (클라이언트가 보는 가방) */
    private static String invRuneAt(ClientGameTestContext ctx, int n) {
        return ctx.computeOnClient(mc -> {
            SkillBarScreen sc = (SkillBarScreen) mc.gui.screen();
            List<Integer> slots = sc.runeSlots();
            return n < slots.size() ? describe(mc.player.getInventory().getItem(slots.get(n))) : "?";
        });
    }

    private static double dealt(TestServerContext server) {
        return server.computeOnServer(s -> {
            double sum = 0;
            ServerLevel level = player(s).level();
            for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(30), h -> true)) sum += TestDummies.dealt(h);
            return sum;
        });
    }

    private static void home(TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 12");
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt) — 나를 봄 */
    private static void spawnHusks(ServerLevel level) {
        for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 5, 0.5, -60, 5).inflate(48), h -> true)) h.discard();
        TestDummies.reset();
        for (Vec3 at : TARGETS) {
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (h == null) continue;
            TestDummies.make(h);
            h.snapTo(at.x, at.y, at.z, 180F, 0F);
            h.setYHeadRot(180F);
            h.setYBodyRot(180F);
            level.addFreshEntity(h);
        }
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[skillbar-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[skillbar-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            if (!name.equals("window")) ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[skillbar-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[skillbar-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
    }

    private static boolean waitFor(ClientGameTestContext ctx, java.util.function.Predicate<Minecraft> test, int ticks) {
        try {
            ctx.waitFor(mc -> mc.player != null && test.test(mc), ticks);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    private boolean waitScreen(ClientGameTestContext ctx, java.util.function.Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 60);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open");
            return false;
        }
    }

    /** 스킬 칸 창의 GUI 좌표 → 창 픽셀 좌표 */
    private static double[] at(ClientGameTestContext ctx, Function<SkillBarScreen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            if (!(sc instanceof SkillBarScreen s)) throw new AssertionError("not the skill bar window: " + (sc == null ? "none" : sc.getClass().getSimpleName()));
            int[] p = where.apply(s);
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<SkillBarScreen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    /** 가방 창 등 다른 화면의 GUI 좌표로 마우스 */
    private static void hoverAny(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            if (sc == null) throw new AssertionError("no screen");
            int[] q = where.apply(sc);
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void settle(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<SkillBarScreen, int[]> where, int button) {
        hover(ctx, where);
        ctx.getInput().pressMouse(button);
        settle(ctx, conn);
    }

    /** 끌어 놓기 (누른 채 6걸음으로 옮김 — 중간 사진 이름이 있으면 놓기 전에 사진) */
    private static void drag(ClientGameTestContext ctx, TestServerConnection conn, Function<SkillBarScreen, int[]> from, Function<SkillBarScreen, int[]> to, String midShot) {
        double[] a = at(ctx, from);
        double[] b = at(ctx, to);
        ctx.getInput().setCursorPos(a[0], a[1]);
        ctx.waitTicks(2);
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        for (int k = 1; k <= 6; k++) {
            ctx.getInput().setCursorPos(a[0] + (b[0] - a[0]) * k / 6, a[1] + (b[1] - a[1]) * k / 6);
            ctx.waitTick();
        }
        if (midShot != null) shot(ctx, midShot);
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        settle(ctx, conn);
    }
}
