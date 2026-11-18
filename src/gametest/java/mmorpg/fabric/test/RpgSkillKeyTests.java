package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Weapons;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.job.CommonSkills;
import mmorpg.fabric.job.SkillKeys;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * 2026-11-18 축복의 길 스킬 키 여섯 (SkillKeys) 게임 시험 — 패킷 처리 함수를 바로 부름 (모드 · 모드 없는 클라이언트가 같은 길):
 * <ul>
 * <li>처음 값 (우 직업 1 · ⇧우 3 · ⇧좌 2 · ⇧⇧ 공용 · Q 4 · ⇧Q 5) · 끼우기 (한 스킬은 한 키에만 — 옮겨 옴) · 빼기 · 처음 값으로 · 기록 (플레이어 속성)</li>
 * <li>우클릭: 웅크림에 따라 우 · ⇧우, 누르고 있는 동안 (5틱 안에 다시 옴) 은 한 번만 · 빈 키는 원래대로 (false)</li>
 * <li>Shift + 좌클릭: 웅크리지 않으면 평타 (false) · 휘두르기 + 공격 패킷이 같은 틱이면 한 번 · 무기를 든 채면 블록 캐기 시작을 막음</li>
 * <li>Q · Shift + Q · Ctrl + Q (한 묶음 버리기 = Shift + Q)</li>
 * <li>Shift 두 번: 0.3초 안에 두 번 켜짐 · 그 사이 다른 Shift 키를 쓰면 아님 · 느리면 아님 · 공용 스킬이 열려 있으면 그 스킬이 나감</li>
 * </ul>
 */
public final class RpgSkillKeyTests {
    static final String ENV = "mmorpg:skillkeys";

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Props.set(sp, SkillKeys.PROP, null);
        sp.setShiftKeyDown(false);
        return sp;
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void bindings(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(String.join(",", SkillKeys.binds(sp)).equals("j1,j3,j2,c,j4,j5"), "처음 값: " + String.join(",", SkillKeys.binds(sp)));
        h.assertTrue(SkillKeys.keyOf(sp, "j1") == SkillKeys.Key.RMB && SkillKeys.keyOf(sp, SkillKeys.COMMON) == SkillKeys.Key.SHIFT2, "직업 1 = 우 · 공용 = ⇧⇧");
        h.assertTrue(SkillKeys.bind(sp, SkillKeys.Key.SHIFT_LMB, "j1") == null, "⇧좌 에 직업 1");
        h.assertTrue(SkillKeys.bound(sp, SkillKeys.Key.SHIFT_LMB).equals("j1") && SkillKeys.bound(sp, SkillKeys.Key.RMB).isEmpty(),
                "한 스킬은 한 키에만 (우에서 옮겨 옴): " + String.join(",", SkillKeys.binds(sp)));
        h.assertTrue(SkillKeys.bind(sp, SkillKeys.Key.RMB, "j9") != null && SkillKeys.bind(sp, SkillKeys.Key.RMB, "x") != null, "없는 것은 못 끼움");
        h.assertTrue(Props.get(sp, SkillKeys.PROP).equals(",j3,j1,c,j4,j5"), "기록: " + Props.get(sp, SkillKeys.PROP));
        Props.set(sp, SkillKeys.PROP, "j2,j2,zz,c");
        h.assertTrue(String.join(",", SkillKeys.binds(sp)).equals("j2,,,c,,"), "망가진 기록은 고쳐 읽음: " + String.join(",", SkillKeys.binds(sp)));
        SkillKeys.reset(sp);
        h.assertTrue(String.join(",", SkillKeys.binds(sp)).equals("j1,j3,j2,c,j4,j5"), "처음 값으로");
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 40)
    public void useAndHold(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgJobTests.hold(sp, Weapons.weaponFrom("sword", 0, "common"));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        int[] n0 = new int[1];
        steps.add(new RpgJobTests.Step(1, () -> {
            n0[0] = SkillKeys.pressedCount(sp);
            h.assertTrue(SkillKeys.onUse(sp) && SkillKeys.lastKey(sp) == SkillKeys.Key.RMB, "우클릭 = 우");
            h.assertTrue(SkillKeys.onUse(sp) && SkillKeys.pressedCount(sp) == n0[0] + 1, "누르고 있는 동안 다시 온 것은 쓰지 않음 (막기는 함)");
        }));
        steps.add(new RpgJobTests.Step(4, () -> {
            h.assertTrue(SkillKeys.onUse(sp) && SkillKeys.pressedCount(sp) == n0[0] + 1, "4틱 뒤 (누르고 있음) — 한 번만");
        }));
        steps.add(new RpgJobTests.Step(7, () -> {
            h.assertTrue(SkillKeys.onUse(sp) && SkillKeys.pressedCount(sp) == n0[0] + 2, "떼었다 다시 누름 (5틱 넘게)");
            sp.setShiftKeyDown(true);
            h.assertTrue(SkillKeys.onUse(sp) && SkillKeys.lastKey(sp) == SkillKeys.Key.SHIFT_RMB, "웅크린 우클릭 = ⇧우 (다른 키 — 바로)");
            sp.setShiftKeyDown(false);
            SkillKeys.bind(sp, SkillKeys.Key.RMB, "");
        }));
        steps.add(new RpgJobTests.Step(8, () -> {
            h.assertTrue(!SkillKeys.onUse(sp), "빈 우 키는 원래대로");
            SkillKeys.reset(sp);
        }));
        RpgJobTests.chain(h, steps, 0, () -> RpgGearTests.done(h, sp));
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void leftAndDrop(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgJobTests.hold(sp, Weapons.weaponFrom("sword", 0, "common"));
        h.assertTrue(!SkillKeys.onPunch(sp) && !SkillKeys.blocksMining(sp), "웅크리지 않은 좌클릭은 평타 · 캐기 그대로");
        sp.setShiftKeyDown(true);
        int n = SkillKeys.pressedCount(sp);
        h.assertTrue(SkillKeys.onPunch(sp) && SkillKeys.onAttack(sp) && SkillKeys.pressedCount(sp) == n + 1 && SkillKeys.lastKey(sp) == SkillKeys.Key.SHIFT_LMB,
                "⇧좌: 휘두르기 + 공격 패킷 같은 틱 = 한 번");
        h.assertTrue(SkillKeys.blocksMining(sp), "무기를 든 채 ⇧좌 = 블록 캐기 시작을 막음");
        h.assertTrue(SkillKeys.onDrop(sp, false) && SkillKeys.lastKey(sp) == SkillKeys.Key.SHIFT_Q, "웅크린 Q = ⇧Q");
        sp.setShiftKeyDown(false);
        h.assertTrue(SkillKeys.onDrop(sp, false) && SkillKeys.lastKey(sp) == SkillKeys.Key.Q, "Q");
        h.assertTrue(SkillKeys.onDrop(sp, true) && SkillKeys.lastKey(sp) == SkillKeys.Key.SHIFT_Q, "Ctrl + Q (한 묶음) = ⇧Q");
        h.assertTrue(!sp.getMainHandItem().isEmpty(), "버리지 않음 (손에 무기 그대로)");
        SkillKeys.bind(sp, SkillKeys.Key.Q, "");
        h.assertTrue(!SkillKeys.onDrop(sp, false), "빈 Q 키는 원래대로 (버리기 — 가방으로)");
        SkillKeys.reset(sp);
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void doubleShift(GameTestHelper h) {
        ServerPlayer sp = player(h);
        TestSetup.treeOn(sp);
        RpgPlayers.setStat(sp, "dex", 40);
        h.assertTrue(StatTrees.pick(sp, "dex", 1, 1) == null && "lightning_sprint".equals(CommonSkills.equipped(sp)), "라이트닝 스프린트 열림");
        CommonSkills.resetCooldown(sp);
        List<RpgJobTests.Step> steps = new ArrayList<>();
        int[] n0 = new int[1];
        steps.add(new RpgJobTests.Step(1, () -> {
            n0[0] = SkillKeys.pressedCount(sp);
            SkillKeys.onInput(sp, true);
        }));
        steps.add(new RpgJobTests.Step(2, () -> SkillKeys.onInput(sp, false)));
        steps.add(new RpgJobTests.Step(2, () -> {
            SkillKeys.onInput(sp, true); // 첫 켜짐에서 4틱 = 두 번
            h.assertTrue(SkillKeys.pressedCount(sp) == n0[0] + 1 && SkillKeys.lastKey(sp) == SkillKeys.Key.SHIFT2, "Shift 두 번");
            h.assertTrue(CommonSkills.cooldownLeft(sp) > 0 && "lightning_sprint".equals(CommonSkills.lastCast(sp)), "공용 스킬이 나감");
            SkillKeys.onInput(sp, false);
        }));
        steps.add(new RpgJobTests.Step(10, () -> SkillKeys.onInput(sp, true)));
        steps.add(new RpgJobTests.Step(1, () -> {
            SkillKeys.shiftUsed(sp); // 그사이 Shift + 우클릭 등
            SkillKeys.onInput(sp, false);
        }));
        steps.add(new RpgJobTests.Step(2, () -> {
            SkillKeys.onInput(sp, true);
            h.assertTrue(SkillKeys.pressedCount(sp) == n0[0] + 1, "그사이 다른 Shift 키를 쓰면 두 번이 아님");
            SkillKeys.onInput(sp, false);
        }));
        steps.add(new RpgJobTests.Step(10, () -> SkillKeys.onInput(sp, true)));
        steps.add(new RpgJobTests.Step(1, () -> SkillKeys.onInput(sp, false)));
        steps.add(new RpgJobTests.Step(8, () -> {
            SkillKeys.onInput(sp, true); // 첫 켜짐에서 9틱 — 느림
            h.assertTrue(SkillKeys.pressedCount(sp) == n0[0] + 1, "0.3초 넘으면 아님");
            SkillKeys.onInput(sp, false);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            CommonSkills.resetCooldown(sp);
            TestSetup.TREE_ON.remove(sp.getUUID());
            RpgGearTests.done(h, sp);
        });
    }
}
