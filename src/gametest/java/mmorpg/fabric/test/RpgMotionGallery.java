package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Armor;
import mmorpg.core.Motions;
import mmorpg.core.SwingLooks;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.client.motion.BladeTrails;
import mmorpg.fabric.client.motion.ClientMotions;
import mmorpg.fabric.client.motion.MotionPoses;
import mmorpg.fabric.client.motion.MotionRender;
import mmorpg.fabric.motion.MotionNet;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.motion.SwingLook;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.38.0 사진첩 (화면 시험, only=motion): 동작 22 가지를 마네킹 셋 (같은 동작의 세 순간) 에 멈춰 3/4 · 옆에서 찍고 (mo_&lt;동작&gt;_34 · _side),
 * 공허 갑옷을 입은 마네킹의 팔꿈치 · 무릎 굽힘 (mo_armor_*), 실제 입력: 공격 키 세 번 = 가로 → 세로 → 회전 (클라이언트 · 서버 단계가 같은지 · 1인칭 사진),
 * 쉬프트 두 번 = 구르기 (앞으로 3칸 넘게 · 재사용 대기 중에는 다시 안 됨 · 1인칭 · 3인칭 사진), 우클릭 무기 스킬 → 서버가 보낸 동작 (skill_slash),
 * 맨손 1인칭 (굽힘이 남지 않음). 0.40.0 참격 궤적: 렌더가 그린 무기 자리 = BladeTrails 계산 (3인칭 마네킹 · 1인칭), 실제로 눌러 찍은 궤적 (mo_trail_tp_* · mo_trail_fp_*) —
 * 멈춘 사진 (mo_&lt;동작&gt;_* · mo_fp_*) 에도 그 순간까지의 궤적. 0.41.0 평타 강화 모습: 멈춘 마네킹의 가로 베기 궤적을 모습마다 (mo_look_&lt;key&gt;),
 * 가르기 · 사신 현신을 걸고 실제로 평타 (mo_look_tp_* · tb_* · fp_* — 서버가 알린 모습 = 궤적 색 · 무기 끝 늘임). 사진 이름 mo_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgMotionGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-motion-gallery");
    private final List<String> failures = new ArrayList<>();

    /** {동작, 무기 종류, 세 순간} */
    private static final String[][] SAMPLES = {
        {"slash_h", "sword", "0.3,0.5,0.68"},
        {"slash_v", "sword", "0.32,0.52,0.86"},
        {"spin", "sword", "0.16,0.45,0.8"},
        {"dagger_x", "dagger", "0.16,0.36,0.72"},
        {"chop", "axe", "0.36,0.56,0.76"},
        {"thrust", "lance", "0.3,0.5,0.7"},
        {"bash", "shield", "0.3,0.5,0.72"},
        {"smite", "scepter", "0.32,0.55,0.75"},
        {"skill_slash", "greatsword", "0.3,0.48,0.66"},
        {"skill_flurry", "sword", "0.26,0.4,0.54"},
        {"slam", "hammer", "0.4,0.58,0.8"},
        {"leap_slam", "axe", "0.2,0.45,0.62"},
        {"dash", "sword", "0.18,0.5,0.75"},
        {"throw", "dagger", "0.38,0.55,0.75"},
        {"throw_weapon", "hammer", "0.38,0.55,0.7"},
        {"cast", "wand", "0.28,0.45,0.8"},
        {"cast_up", "scepter", "0.3,0.5,0.82"},
        {"guard", "shield", "0.3,0.5,0.8"},
        {"shoot", "bow", "0.3,0.55,0.62"},
        {"shoot_up", "bow", "0.3,0.55,0.62"},
        {"spin2", "scythe", "0.12,0.4,0.7"},
        {"roll", "sword", "0.22,0.38,0.62"},
    };
    /** 마네킹을 보는 쪽 {이름, yaw} — 3/4 (오른쪽 앞) · 오른쪽 옆 · 앞 */
    private static final String[][] VIEWS = {{"34", "-35"}, {"side", "-90"}, {"front", "0"}};
    /** 마네킹 자리 {x, z} — 나 (0.5, 0.5) 에서 북쪽으로 3.5칸 */
    private static final float[][] ROW = {{-1.2F, -3.0F}, {0.5F, -3.0F}, {2.2F, -3.0F}};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("motion")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 -60 0.5 180 8");
            server.runCommand("xp set @a 50 levels");
            for (String st : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + st + " 150");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            server.runOnServer(s -> player(s).getInventory().clearContent());
            ctx.waitTicks(20);
            check(MotionPoses.names().equals(Motions.KNOWN), "poses loaded " + MotionPoses.names().size());
            double err = MotionPoses.worstBladeError();
            check(err < 1.0, "blade solve worst " + String.format("%.4f", err) + " deg");
            part(ctx, "poses", () -> poses(ctx, server));
            part(ctx, "armor", () -> armor(ctx, server));
            part(ctx, "fp", () -> firstPerson(ctx, server));
            part(ctx, "attack", () -> attack(ctx, server));
            part(ctx, "trail", () -> trail(ctx, server));
            part(ctx, "area", () -> area(ctx, server));
            part(ctx, "look", () -> looks(ctx, server));
            part(ctx, "roll", () -> roll(ctx, server));
            part(ctx, "skill", () -> skill(ctx, server));
            part(ctx, "empty", () -> empty(ctx, server));
        }
        LOG.info("[motion-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("motion gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 1. 동작 22 (마네킹 셋)

    private void poses(ClientGameTestContext ctx, TestServerContext server) {
        hideGui(ctx, true);
        for (String[] s : SAMPLES) {
            String motion = s[0];
            String[] ts = s[2].split(",");
            for (String[] view : VIEWS) {
                List<Integer> row = spawn(server, Float.parseFloat(view[1]), s[1], false);
                ctx.runOnClient(mc -> {
                    for (int i = 0; i < row.size(); i++) ClientMotions.freeze(row.get(i), motion, Float.parseFloat(ts[i]));
                });
                ctx.waitTicks(3);
                ctx.takeScreenshot(TestScreenshotOptions.of("mo_" + motion + "_" + view[0]).withSize(960, 540));
                check(ctx.computeOnClient(mc -> motion.equals(ClientMotions.current(row.get(0))) && mc.level.getEntity(row.get(0)) != null), "frozen " + motion);
                discard(server, row);
                ctx.runOnClient(mc -> ClientMotions.unfreezeAll());
            }
        }
    }

    // ------------------------------------------------------------ 2. 갑옷도 굽힘

    private void armor(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> player(s).getInventory().clearContent());
        hideGui(ctx, true);
        String[][] poses = {{"guard", "0.3"}, {"roll", "0.12"}, {"slash_h", "0.3"}};
        for (String[] view : VIEWS) {
            List<Integer> row = spawn(server, Float.parseFloat(view[1]), "sword", true);
            ctx.runOnClient(mc -> {
                for (int i = 0; i < row.size(); i++) ClientMotions.freeze(row.get(i), poses[i][0], Float.parseFloat(poses[i][1]));
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot(TestScreenshotOptions.of("mo_armor_" + view[0]).withSize(960, 540));
            discard(server, row);
            ctx.runOnClient(mc -> ClientMotions.unfreezeAll());
        }
        hideGui(ctx, false);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 2b. 1인칭 (내 자세를 멈춤 — 동작마다 세 순간)

    private void firstPerson(ClientGameTestContext ctx, TestServerContext server) {
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(180F, 5F);
        int me = ctx.computeOnClient(mc -> mc.player.getId());
        for (String[] s : SAMPLES) {
            String motion = s[0];
            String type = s[1];
            server.runOnServer(sv -> {
                ServerPlayer sp = player(sv);
                sp.getInventory().clearContent();
                sp.getInventory().setItem(0, weapon(type));
                sp.getInventory().setSelectedSlot(0);
            });
            ctx.waitTicks(3);
            String[] ts = s[2].split(",");
            for (int i = 0; i < ts.length; i++) {
                float t = Float.parseFloat(ts[i]);
                ctx.runOnClient(mc -> ClientMotions.freeze(me, motion, t));
                ctx.waitTicks(1);
                ctx.takeScreenshot(TestScreenshotOptions.of("mo_fp_" + motion + "_" + i).withSize(960, 540));
            }
            ctx.runOnClient(mc -> ClientMotions.unfreezeAll());
        }
        server.runOnServer(sv -> player(sv).getInventory().clearContent());
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 3. 공격 키 세 번 (가로 → 세로 → 회전)

    private void attack(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(0, weapon("sword"));
            sp.getInventory().setSelectedSlot(0);
        });
        ctx.getInput().lookAt(180F, -35F); // 하늘 (블록을 캐지 않게)
        ctx.waitTicks(12);
        int me = ctx.computeOnClient(mc -> mc.player.getId());
        String[] want = {"slash_h", "slash_v", "spin"};
        for (int i = 0; i < want.length; i++) {
            ctx.getInput().pressKey(o -> o.keyAttack);
            ctx.waitTicks(3);
            String cur = ctx.computeOnClient(mc -> ClientMotions.current(me));
            check(want[i].equals(cur), "client motion " + i + " " + cur);
            ctx.takeScreenshot(TestScreenshotOptions.of("mo_fp_" + want[i]).withSize(960, 540));
            String srv = server.computeOnServer(s -> {
                MotionSystem.Swing sw = MotionSystem.last(player(s));
                return sw == null ? "-" : sw.motion() + "#" + sw.step();
            });
            check(srv.equals(want[i] + "#" + (i + 1)), "server step " + i + " " + srv);
            ctx.waitTicks(10); // 허공 공격 뒤 바닐라 공격 대기 (missTime 10)
        }
        // 0.39.0 선입력: 2틱 간격으로 두 번 누르면 둘째는 미뤄 두었다가 평타 사이 (10틱) 가 지나는 틱에 나감 (2단으로 이어짐)
        ctx.waitTicks(Motions.comboWindow() + 2);
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(2);
        long firstSwing = server.computeOnServer(s -> {
            MotionSystem.Swing sw = MotionSystem.last(player(s));
            return sw == null ? -1L : sw.tick();
        });
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(1);
        check(ctx.computeOnClient(mc -> ClientMotions.hasBuffered()), "early press buffered (not sent)");
        check(ctx.computeOnClient(mc -> ClientMotions.lastStep()) == 1, "still step 1 while buffered");
        ctx.waitTicks(Motions.attackInterval());
        check(!ctx.computeOnClient(mc -> ClientMotions.hasBuffered()) && ctx.computeOnClient(mc -> ClientMotions.lastStep()) == 2, "buffered press fired as step 2");
        String buf = server.computeOnServer(s -> {
            MotionSystem.Swing sw = MotionSystem.last(player(s));
            return sw == null ? "-" : sw.step() + "@" + (sw.tick() - firstSwing);
        });
        check(buf.startsWith("2@") && Math.abs(Integer.parseInt(buf.substring(2)) - Motions.attackInterval()) <= 2, "server: step 2 one interval later " + buf);
        ctx.waitTicks(12);
        // 3인칭 (앞 · 시야 40 으로 가까이): 연타 셋
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.getInput().lookAt(180F, 10F);
        fov(ctx, 40);
        ctx.waitTicks(30);
        for (String m : want) {
            ctx.getInput().pressKey(o -> o.keyAttack);
            ctx.waitTicks(4);
            ctx.takeScreenshot(TestScreenshotOptions.of("mo_tp_" + m).withSize(960, 540));
            ctx.waitTicks(9);
        }
        fov(ctx, 70);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(15);
    }

    // ------------------------------------------------------------ 3-1. 0.40.0 참격 궤적 (칼이 지나간 자리에 띠)

    /** {종류, 등급, 동작 · 찍는 틱 …} — 평타를 실제로 눌러 그 동작이 진행된 틱에 찍음 (칼은 연타 셋) */
    private static final String[][] TRAILS = {
        {"sword", "rare", "slash_h:5", "slash_v:5", "spin:6"},
        {"scythe", "hero", "slash_h:5"},
        {"greatsword", "transcend", "slash_h:5"},
        {"dagger", "rare", "dagger_x:5"},
        {"axe", "rare", "chop:6"},
        {"scepter", "rare", "smite:6"},
    };

    private void trail(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 180 0"); // 북쪽 (-z) 을 봄
        // (1) 렌더가 실제로 그린 무기 자리 = BladeTrails 의 계산 (3인칭: 마네킹을 멈춰 · 1인칭: 내 자세를 멈춰)
        hideGui(ctx, true);
        String[][] cases = {{"slash_h", "sword", "0.5"}, {"slash_v", "sword", "0.45"}, {"spin", "sword", "0.45"}, {"chop", "axe", "0.5"},
            {"dagger_x", "dagger", "0.3"}, {"smite", "scepter", "0.55"}, {"skill_flurry", "sword", "0.5"}};
        double worst = 0;
        for (String[] c : cases) {
            List<Integer> row = spawn(server, -35F, c[1], false);
            int id = row.get(1);
            float t = Float.parseFloat(c[2]);
            ctx.runOnClient(mc -> {
                ClientMotions.freeze(id, c[0], t);
                BladeTrails.captured = null;
                BladeTrails.captureEntity = id;
            });
            ctx.waitTicks(3);
            double[] got = ctx.computeOnClient(mc -> BladeTrails.captured);
            double[] want = ctx.computeOnClient(mc -> BladeTrails.testWorldBlade(id));
            String model = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof net.minecraft.client.entity.ClientAvatarEntity av
                    ? String.valueOf(av.getSkin().model()) : "-");
            ctx.runOnClient(mc -> BladeTrails.captureEntity = Integer.MIN_VALUE);
            check(got != null && want != null, "captured " + c[0]);
            if (got != null && want != null) {
                double dg = dist(got, want, 0);
                double dt = dist(got, want, 3);
                worst = Math.max(worst, Math.max(dg, dt));
                check(dg < 0.02 && dt < 0.03, String.format("3rd person blade = render %s %s (%s) grip %.3f tip %.3f", c[0], c[1], model, dg, dt));
            }
            discard(server, row);
            ctx.runOnClient(mc -> ClientMotions.unfreezeAll());
        }
        LOG.info("[motion-gallery] trail kinematics worst {} (my model {})", String.format("%.4f", worst),
                ctx.computeOnClient(mc -> mc.player == null ? "-" : String.valueOf(mc.player.getSkin().model())));
        // 그리는 방식 (렌더 타입의 파이프라인 — 깊이 쓰기 · 깊이 시험 · 면 버리기 · 섞기): 로그로 남겨 둠
        ctx.runOnClient(mc -> {
            net.minecraft.resources.Identifier tex = mmorpg.fabric.Mmorpg.id("textures/fx/blade_trail_core.png");
            LOG.info("[motion-gallery] pipeline {}", pipeline("text", net.minecraft.client.renderer.rendertype.RenderTypes.text(tex)));
            LOG.info("[motion-gallery] pipeline {}", pipeline("eyes", net.minecraft.client.renderer.rendertype.RenderTypes.eyes(tex)));
            LOG.info("[motion-gallery] pipeline {}", pipeline("entityTranslucentEmissive", net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(tex)));
            LOG.info("[motion-gallery] pipeline {}", pipeline("beaconBeam", net.minecraft.client.renderer.rendertype.RenderTypes.beaconBeam(tex, true)));
            LOG.info("[motion-gallery] pipeline {}", pipeline("itemTranslucent", net.minecraft.client.renderer.rendertype.RenderTypes.itemTranslucent(tex)));
        });
        int me = ctx.computeOnClient(mc -> mc.player.getId());
        hideGui(ctx, false); // 1인칭 손은 화면 표시가 켜져 있어야 그려짐
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        for (String[] c : cases) {
            String type = c[1];
            server.runOnServer(sv -> {
                ServerPlayer sp = player(sv);
                sp.getInventory().setItem(0, weapon(type));
                sp.getInventory().setSelectedSlot(0);
            });
            ctx.waitTicks(2);
            float t = Float.parseFloat(c[2]);
            ctx.runOnClient(mc -> {
                ClientMotions.freeze(me, c[0], t);
                BladeTrails.capturedFp = null;
                BladeTrails.captureFp = true;
            });
            ctx.waitTicks(3);
            double[] got = ctx.computeOnClient(mc -> BladeTrails.capturedFp);
            double[] want = ctx.computeOnClient(mc -> BladeTrails.testFirstPersonBlade());
            ctx.runOnClient(mc -> {
                BladeTrails.captureFp = false;
                ClientMotions.unfreezeAll();
            });
            check(got != null && want != null, "captured 1st person " + c[0]);
            if (got != null && want != null) {
                double dg = dist(got, want, 0);
                double dt = dist(got, want, 3);
                check(dg < 0.03 && dt < 0.05, String.format("1st person blade = render %s %s grip %.3f tip %.3f", c[0], type, dg, dt));
            }
        }
        // (2) 실제로 눌러 찍음: 3인칭 앞 (시야 50) · 3인칭 뒤 (보통 3인칭 — 시야 70) · 1인칭 — 칼은 가로 → 세로 → 회전
        for (CameraType cam : new CameraType[] {CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON}) {
            boolean tp = cam != CameraType.FIRST_PERSON;
            String tag = cam == CameraType.THIRD_PERSON_FRONT ? "tp_" : cam == CameraType.THIRD_PERSON_BACK ? "tb_" : "fp_";
            hideGui(ctx, tp);
            ctx.runOnClient(mc -> mc.options.setCameraType(cam));
            fov(ctx, cam == CameraType.THIRD_PERSON_FRONT ? 50 : 70);
            ctx.getInput().lookAt(180F, tp ? 8F : 0F);
            for (String[] row : TRAILS) {
                server.runOnServer(sv -> {
                    ServerPlayer sp = player(sv);
                    sp.getInventory().setItem(0, weaponOf(row[0], row[1]));
                    sp.getInventory().setSelectedSlot(0);
                });
                ctx.waitTicks(Motions.comboWindow() + 4); // 연타가 처음부터
                for (int i = 2; i < row.length; i++) {
                    String[] mt = row[i].split(":");
                    int wait = Integer.parseInt(mt[1]);
                    ctx.getInput().pressKey(o -> o.keyAttack);
                    ctx.waitTicks(wait);
                    String cur = ctx.computeOnClient(mc -> ClientMotions.current(me));
                    check(mt[0].equals(cur), "trail swing " + row[0] + " " + mt[0] + " (" + cur + ")");
                    int samples = ctx.computeOnClient(mc -> tp ? BladeTrails.lastWorldSamples : BladeTrails.lastFpSamples);
                    check(samples >= 6, (tp ? "3rd" : "1st") + " person trail samples " + row[0] + " " + mt[0] + " " + samples);
                    ctx.takeScreenshot(TestScreenshotOptions.of("mo_trail_" + tag + row[0] + "_" + mt[0]).withSize(960, 540));
                    ctx.waitTicks(Motions.attackInterval() + 1 - wait);
                }
            }
        }
        fov(ctx, 70);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        hideGui(ctx, false);
        server.runOnServer(sv -> player(sv).getInventory().clearContent());
        ctx.waitTicks(10);
    }

    /** 렌더 타입의 파이프라인 값 (필드 이름 = 값 — 깊이 쓰기 · 깊이 시험 · 면 버리기 · 섞기 …, 시그니처 목록에 없는 것을 로그로 봄) */
    private static String pipeline(String name, net.minecraft.client.renderer.rendertype.RenderType t) {
        StringBuilder sb = new StringBuilder(name).append(" →");
        try {
            Object p = t.pipeline();
            for (Class<?> k = p.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    String v = String.valueOf(f.get(p));
                    sb.append(' ').append(f.getName()).append('=').append(v.length() > 80 ? v.substring(0, 80) + "…" : v);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            sb.append(" (").append(e).append(')');
        }
        return sb.toString();
    }

    private static double dist(double[] a, double[] b, int o) {
        double dx = a[o] - b[o];
        double dy = a[o + 1] - b[o + 1];
        double dz = a[o + 2] - b[o + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------ 3-2. 0.39.0 허공을 베어도 참격 범위 안 적에게 피해

    private void area(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 180 0"); // 북쪽 (-z) 을 봄
        server.runOnServer(sv -> {
            ServerPlayer sp = player(sv);
            sp.getInventory().setItem(0, weapon("sword")); // 앞 부분 (궤적) 이 가방을 비움
            sp.getInventory().setSelectedSlot(0);
        });
        ctx.getInput().lookAt(180F, 0F);
        ctx.waitTicks(25); // 앞 평타에서 충분히 지남
        // 앞 오른쪽 비스듬히 (약 37° · 2.5칸 — 조준선 밖) 과 뒤 (1.5칸)
        List<Integer> ids = server.computeOnServer(s -> {
            ServerLevel level = player(s).level();
            List<Integer> out = new ArrayList<>();
            for (double[] at : new double[][] {{2.0, -1.5}, {0.5, 2.0}}) {
                net.minecraft.world.entity.monster.zombie.Husk m = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
                m.snapTo(at[0], -60, at[1], 0F, 0F);
                m.setNoAi(true);
                m.setPersistenceRequired();
                level.addFreshEntity(m);
                out.add(m.getId());
            }
            return out;
        });
        ctx.waitTicks(3);
        float[] hp0 = server.computeOnServer(s -> {
            ServerLevel level = player(s).level();
            return new float[] {((net.minecraft.world.entity.LivingEntity) level.getEntity(ids.get(0))).getHealth(),
                    ((net.minecraft.world.entity.LivingEntity) level.getEntity(ids.get(1))).getHealth()};
        });
        check(ctx.computeOnClient(mc -> mc.hitResult == null || mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS), "aiming at the air");
        ctx.getInput().pressKey(o -> o.keyAttack);
        ctx.waitTicks(4);
        String r = server.computeOnServer(s -> {
            ServerLevel level = player(s).level();
            Entity a = level.getEntity(ids.get(0));
            Entity b = level.getEntity(ids.get(1));
            float ha = a instanceof net.minecraft.world.entity.LivingEntity la && la.isAlive() ? la.getHealth() : 0F;
            float hb = b instanceof net.minecraft.world.entity.LivingEntity lb && lb.isAlive() ? lb.getHealth() : 0F;
            return ha + "/" + hp0[0] + " " + hb + "/" + hp0[1] + " n=" + mmorpg.fabric.SwingArea.lastCount(player(s));
        });
        String[] parts = r.split(" ");
        float ha = Float.parseFloat(parts[0].split("/")[0]);
        float hb = Float.parseFloat(parts[1].split("/")[0]);
        check(ha < hp0[0], "air swing: front-right husk damaged " + r);
        check(hb == hp0[1], "air swing: husk behind untouched " + r);
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_area_air").withSize(960, 540));
        discard(server, ids);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 3-3. 0.41.0 평타 강화 모습 (궤적 색 · 무기 끝 너머로 늘임)

    /** {모습 key (none = 없음), 무기 종류} — 멈춘 마네킹 (3/4 · 가로 베기) 궤적 사진 mo_look_&lt;key&gt; */
    private static final String[][] LOOK_STILLS = {
        {"none", "sword"}, {"edgeHone", "sword"}, {"flurry", "sword"}, {"cleave", "sword"}, {"bloodBlade", "sword"}, {"swordGod", "sword"},
        {"swordsman_mirror", "sword"}, {"spellblade_awaken", "greatsword"}, {"none", "scythe"}, {"reaper_avatar", "scythe"},
    };

    private static MotionNet.Look netLook(String key) {
        SwingLooks.Look l = SwingLooks.of(key);
        return l == null ? null : new MotionNet.Look(SwingLooks.pack(l.glow()), SwingLooks.pack(l.aura()), (float) l.scale(), (float) l.range());
    }

    private void looks(ClientGameTestContext ctx, TestServerContext server) {
        // 마네킹 (북쪽 3칸) 에서 6.5칸 · 3칸 높은 자리 (돌 기둥) 에서 비스듬히 내려다봄 — 넓어진 궤적 (둥근 띠) 이 화면에 다 들고 모양이 보이게
        server.runCommand("fill 0 -60 3 0 -58 3 minecraft:stone");
        server.runCommand("tp @a 0.5 -57 3.5 180 27");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(180F, 27F);
        hideGui(ctx, true);
        // (1) 멈춘 마네킹 하나 (가운데): 같은 가로 베기 순간에 모습마다
        for (String[] lk : LOOK_STILLS) {
            List<Integer> row = spawn(server, -35F, lk[1], false);
            discard(server, List.of(row.get(0), row.get(2)));
            int id = row.get(1);
            MotionNet.Look look = lk[0].equals("none") ? null : netLook(lk[0]);
            check(lk[0].equals("none") || look != null, "look " + lk[0]);
            ctx.runOnClient(mc -> {
                BladeTrails.frozenLook = look;
                ClientMotions.freeze(id, "slash_h", 0.72F);
            });
            ctx.waitTicks(3);
            ctx.takeScreenshot(TestScreenshotOptions.of("mo_look_" + lk[0] + (lk[0].equals("none") ? "_" + lk[1] : "")).withSize(960, 540));
            ctx.runOnClient(mc -> {
                ClientMotions.unfreezeAll();
                BladeTrails.frozenLook = null;
            });
            discard(server, List.of(id));
        }
        // (2) 실제로: 가르기 (평타 강화 룬) 를 걸고 평타 — 서버가 알린 내 모습 → 궤적 색 · 늘임 (3인칭 앞 · 뒤 · 1인칭)
        int me = ctx.computeOnClient(mc -> mc.player.getId());
        server.runCommand("tp @a 0.5 -60 0.5 180 0");
        server.runCommand("fill 0 -60 3 0 -58 3 minecraft:air");
        server.runOnServer(sv -> {
            ServerPlayer sp = player(sv);
            sp.getInventory().setItem(0, weaponOf("sword", "rare"));
            sp.getInventory().setSelectedSlot(0);
        });
        ctx.waitTicks(Motions.comboWindow() + 4);
        check(server.computeOnServer(sv -> mmorpg.fabric.rune.RuneCast.castRune(player(sv), mmorpg.core.Runes.parseRune("rpg:rune_strike_noble"), false)), "cast 가르기");
        ctx.waitTicks(4);
        check("cleave".equals(server.computeOnServer(sv -> SwingLook.testKey(player(sv)))), "server look cleave");
        int want = SwingLooks.pack(SwingLooks.of("cleave").glow());
        check(ctx.computeOnClient(mc -> ClientMotions.selfLook().glow()) == want, "client got my look " + Integer.toHexString(ctx.computeOnClient(mc -> ClientMotions.selfLook().glow())));
        swingShots(ctx, me, "cleave", want);
        server.runOnServer(sv -> mmorpg.fabric.rune.RuneStrike.testClear(player(sv)));
        ctx.waitTicks(4);
        check(ctx.computeOnClient(mc -> ClientMotions.selfLook().none()), "look ended on the client");
        // (3) 직업 태세: 사신 현신 (낫 — 5칸)
        server.runOnServer(sv -> {
            ServerPlayer sp = player(sv);
            sp.getInventory().setItem(0, weaponOf("scythe", "hero"));
            sp.getInventory().setSelectedSlot(0);
            mmorpg.fabric.job.JobTraits.testStance(sp, "reaper", "reaper_avatar", 400);
        });
        ctx.waitTicks(Motions.comboWindow() + 4);
        int wantR = SwingLooks.pack(SwingLooks.of("reaper_avatar").glow());
        check(ctx.computeOnClient(mc -> ClientMotions.selfLook().glow()) == wantR, "client got the reaper look");
        swingShots(ctx, me, "reaper_avatar", wantR);
        server.runOnServer(sv -> {
            ServerPlayer sp = player(sv);
            mmorpg.fabric.job.JobTraits.testEndStance(sp);
            sp.getInventory().clearContent();
        });
        fov(ctx, 70);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        hideGui(ctx, false);
        ctx.waitTicks(10);
    }

    /** 모습이 실린 평타 한 번씩 (3인칭 앞 · 뒤 · 1인칭): 궤적 색 = 모습 · 무기 끝 늘임, 사진 mo_look_&lt;tp|tb|fp&gt;_&lt;key&gt; */
    private void swingShots(ClientGameTestContext ctx, int me, String key, int want) {
        for (CameraType cam : new CameraType[] {CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON}) {
            boolean tp = cam != CameraType.FIRST_PERSON;
            String tag = cam == CameraType.THIRD_PERSON_FRONT ? "tp_" : cam == CameraType.THIRD_PERSON_BACK ? "tb_" : "fp_";
            hideGui(ctx, tp);
            ctx.runOnClient(mc -> mc.options.setCameraType(cam));
            fov(ctx, cam == CameraType.THIRD_PERSON_FRONT ? 50 : 70);
            ctx.getInput().lookAt(180F, tp ? 8F : 0F);
            ctx.waitTicks(Motions.comboWindow() + 4); // 연타가 처음부터 (가로 베기)
            ctx.getInput().pressKey(o -> o.keyAttack);
            ctx.waitTicks(5);
            MotionNet.Look l = ctx.computeOnClient(mc -> ClientMotions.lookOf(me));
            check(l.glow() == want, "swing carries the look " + key + " " + Integer.toHexString(l.glow()));
            check(ctx.computeOnClient(mc -> BladeTrails.lastLookGlow) == want, "trail glow = look " + key);
            float ext = ctx.computeOnClient(mc -> tp ? BladeTrails.lastLookExt : BladeTrails.lastLookExtFp);
            check(ext > 0.2F, "blade tip extended " + key + " " + ext);
            ctx.takeScreenshot(TestScreenshotOptions.of("mo_look_" + tag + key).withSize(960, 540));
            ctx.waitTicks(Motions.attackInterval() + 1);
        }
    }

    // ------------------------------------------------------------ 4. 쉬프트 두 번 = 구르기

    private void roll(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 0"); // 남쪽 (+z) 을 봄
        ctx.getInput().lookAt(0F, 0F);
        ctx.runOnClient(mc -> ClientMotions.resetRoll());
        ctx.waitTicks(10);
        int me = ctx.computeOnClient(mc -> mc.player.getId());
        Vec3 p0 = ctx.computeOnClient(mc -> mc.player.position());
        ctx.getInput().pressKey(o -> o.keyShift);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyShift);
        ctx.waitTicks(3);
        check("roll".equals(ctx.computeOnClient(mc -> ClientMotions.current(me))), "rolling");
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_fp_roll").withSize(960, 540));
        ctx.waitTicks(8); // 구른 지 약 12틱 (구르기 10틱 + 남은 미끄러짐)
        Vec3 p1 = ctx.computeOnClient(mc -> mc.player.position());
        double moved = p1.z - p0.z;
        check(moved > 3.0 && moved < 5.0 && Math.abs(p1.x - p0.x) < 0.5, "rolled forward " + String.format("%.2f", moved));
        check(server.computeOnServer(s -> MotionSystem.lastRoll(player(s)) >= 0), "server saw the roll");
        long left = ctx.computeOnClient(mc -> ClientMotions.rollCooldownLeft());
        check(left > 3, "cooldown left " + left);
        // 재사용 대기 안 (구른 지 약 14틱 < 20): 다시 두 번 눌러도 그대로
        ctx.getInput().pressKey(o -> o.keyShift);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyShift);
        ctx.waitTicks(8);
        Vec3 p2 = ctx.computeOnClient(mc -> mc.player.position());
        check(p2.distanceTo(p1) < 0.3, "cooldown: no second roll " + String.format("%.2f", p2.distanceTo(p1)));
        // 3인칭 (앞에서 · 시야 40) 구르기
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        fov(ctx, 40);
        ctx.waitTicks(25);
        ctx.getInput().pressKey(o -> o.keyShift);
        ctx.waitTicks(2);
        ctx.getInput().pressKey(o -> o.keyShift);
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_tp_roll").withSize(960, 540));
        ctx.waitTicks(2);
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_tp_roll_late").withSize(960, 540));
        fov(ctx, 70);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(15);
    }

    // ------------------------------------------------------------ 5. 무기 스킬 (우클릭) → 서버가 보낸 동작

    private void skill(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(0, weapon("sword"));
            sp.getInventory().setSelectedSlot(0);
        });
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        fov(ctx, 40);
        ctx.waitTicks(10);
        int me = ctx.computeOnClient(mc -> mc.player.getId());
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(2);
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_tp_skill_slash_0").withSize(960, 540));
        ctx.waitTicks(2);
        String cur = ctx.computeOnClient(mc -> ClientMotions.current(me));
        check("skill_slash".equals(cur), "weapon skill motion " + cur);
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_tp_skill_slash_1").withSize(960, 540));
        ctx.waitTicks(3);
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_tp_skill_slash_2").withSize(960, 540));
        fov(ctx, 70);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(20);
    }

    // ------------------------------------------------------------ 6. 맨손 1인칭 (굽힘이 남지 않음)

    private void empty(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(0, ItemStack.EMPTY);
            sp.getInventory().setSelectedSlot(0);
        });
        ctx.getInput().lookAt(0F, 10F);
        ctx.waitTicks(10);
        ctx.takeScreenshot(TestScreenshotOptions.of("mo_fp_empty").withSize(960, 540));
    }

    // ------------------------------------------------------------ 도움

    /**
     * 마네킹 셋 (나에게서 북쪽으로 3.5칸) 을 yaw 로 세움 — 몸은 세울 때만 바로 그쪽을 보므로 (가만히 선 사람은 머리만 돌아감) 보는 쪽마다 새로 세움.
     * yaw 0 = 나를 봄 · -35 = 3/4 · -90 = 오른쪽 옆. 손에 type 무기, armor = 공허 갑옷 (힘 · 초월)
     */
    private static List<Integer> spawn(TestServerContext server, float yaw, String type, boolean armor) {
        return server.computeOnServer(s -> {
            ServerLevel level = player(s).level();
            List<Integer> ids = new ArrayList<>();
            for (float[] a : ROW) {
                Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
                m.snapTo(a[0], -60, a[1], yaw, 0F);
                m.setYHeadRot(yaw);
                m.setYBodyRot(yaw);
                if (type != null) m.setItemSlot(EquipmentSlot.MAINHAND, weapon(type));
                if (armor) {
                    for (int i = 0; i < 4; i++) m.setItemSlot(SLOTS[i], stack(Armor.armorId("str", 50, "transcend", Armor.PIECE_ORDER.get(i))));
                }
                level.addFreshEntity(m);
                ids.add(m.getId());
            }
            return ids;
        });
    }

    private static void discard(TestServerContext server, List<Integer> ids) {
        server.runOnServer(s -> {
            ServerLevel level = player(s).level();
            for (int id : ids) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        });
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    /** 이 종류 · 등급의 무기 (40 · 30 · 20 · 10 · 0렙 차례 — 없으면 weapon(type)) */
    private static ItemStack weaponOf(String type, String grade) {
        for (int lv : new int[] {40, 30, 20, 10, 0}) {
            Weapon w = Weapons.weaponFrom(type, lv, grade);
            if (w != null && RpgItems.byId(w.id()) != null) return new ItemStack(RpgItems.byId(w.id()));
        }
        return weapon(type);
    }

    /** 이 종류의 무기 (영웅 40렙 → 없으면 초월 · 고귀 · 희귀 · 일반, 40 · 30 · 20 · 10 · 0렙 차례 — 마검은 영웅이 없음) */
    private static ItemStack weapon(String type) {
        for (String g : List.of("hero", "transcend", "noble", "rare", "common")) {
            for (int lv : new int[] {40, 30, 20, 10, 0}) {
                Weapon w = Weapons.weaponFrom(type, lv, g);
                if (w != null && RpgItems.byId(w.id()) != null) return new ItemStack(RpgItems.byId(w.id()));
            }
        }
        return ItemStack.EMPTY;
    }

    private static void fov(ClientGameTestContext ctx, int deg) {
        ctx.runOnClient(mc -> mc.options.fov().set(deg));
    }

    private static void hideGui(ClientGameTestContext ctx, boolean hide) {
        ctx.runOnClient(mc -> {
            if (mc.gui.hud.isHidden() != hide) mc.gui.hud.toggle();
        });
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[motion-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[motion-gallery] part " + name, e);
        }
        try {
            ctx.runOnClient(mc -> {
                ClientMotions.unfreezeAll();
                MotionRender.debugEntity = Integer.MIN_VALUE;
                BladeTrails.captureEntity = Integer.MIN_VALUE;
                BladeTrails.captureFp = false;
                BladeTrails.frozenLook = null;
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[motion-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[motion-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }
}
