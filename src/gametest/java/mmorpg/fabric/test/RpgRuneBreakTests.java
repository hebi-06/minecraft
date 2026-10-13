package mmorpg.fabric.test;

import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.RuneBreak;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.rune.Breakthrough;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.rune.RuneSystem;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * 8단계 게임 시험 (서버): 룬 돌파 — 아이템의 단계 (구성 요소 · 이름 · 배지 모델 값 · 저장), 시전 (위력 × 계수 · 회복 · 보호막 × 계수 · 6 까지),
 * 실제 피해 (수호의 함성 0돌 → 5돌 = ×1.5), 초월 룬 화면 색 연출 단계 (0 · 2돌 없음 · 3돌 하늘빛 · 6돌 테두리 빛 · 아이템으로 쓰면 그 단계 · 3돌 돌파 때 미리 보기),
 * 합치기 (남는 룬 · 재료 고르기 · 핫바로 · 넘침 · 옮긴 룬 거절 · 모두 돌파), 갈기 (단계만큼 조각 · 단계가 바뀌면 거절 · 겹친 룬은 높은 단계를 남김),
 * 룬술사 돌파 창 흐름 (클라이언트 없이 — Forms.HEADLESS).
 */
public final class RpgRuneBreakTests {
    private static final Obj SMITH = Rules.get().obj("RUNE_SMITH");

    private static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    private static Runes.Rune rune(String family, String grade) {
        return Runes.parseRune(Runes.runeId(family, grade));
    }

    /** 칸의 룬 ID · 단계 ("-" = 빈 칸) */
    private static String at(ServerPlayer sp, int slot) {
        ItemStack s = sp.getInventory().getItem(slot);
        Runes.Rune r = RuneItems.runeOf(s);
        return r == null ? (s.isEmpty() ? "-" : s.getItem().toString()) : r.id() + "@" + Breakthrough.of(s);
    }

    /** 아이템 구성 요소: 단계 · 이름 "[3돌]" · 배지 모델 값 (custom_model_data 첫 수) · 0 이면 둘 다 지움 · 6 까지 · 저장 (코덱) */
    @GameTest(maxTicks = 20)
    public void componentNameAndModel(GameTestHelper h) {
        ItemStack s = Breakthrough.stack("int", "transcend", 3);
        h.assertTrue(Breakthrough.of(s) == 3, "level 3: " + Breakthrough.of(s));
        CustomModelData cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
        h.assertTrue(cmd != null && cmd.floats().equals(List.of(3F)), "badge model value " + cmd);
        String nm = s.getHoverName().getString().replaceAll("§.", "");
        h.assertTrue(nm.contains("메테오 [3돌]"), "name " + nm);
        ItemStack copy = s.copy();
        h.assertTrue(Breakthrough.of(copy) == 3, "copy keeps the level");
        // 저장 (아이템 코덱 → 다시 읽기)
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        com.google.gson.JsonElement json = ItemStack.CODEC.encodeStart(ops, s).getOrThrow();
        ItemStack back = ItemStack.CODEC.parse(ops, json).getOrThrow();
        h.assertTrue(Breakthrough.of(back) == 3 && RuneItems.runeOf(back) != null, "saved and loaded: " + json);
        Breakthrough.write(s, 9);
        h.assertTrue(Breakthrough.of(s) == RuneBreak.max(), "clamped to " + RuneBreak.max());
        Breakthrough.write(s, 0);
        h.assertTrue(Breakthrough.of(s) == 0 && !s.has(Breakthrough.LEVEL) && !s.has(DataComponents.CUSTOM_MODEL_DATA), "cleared");
        h.assertFalse(s.getHoverName().getString().replaceAll("§.", "").contains("돌]"), "no tag at 0: " + s.getHoverName().getString());
        h.assertTrue(Breakthrough.of(RuneItems.stack("int", "transcend")) == 0, "plain rune = 0");
        h.succeed();
    }

    /** 시전: 위력 × 계수 (0 → 4돌 = ×1.4, 9 → 6돌 = ×1.6), 룬 정의의 회복 · 보호막 × 계수, 시전 단계 기록 */
    @GameTest(maxTicks = 20)
    public void castUsesCoefficient(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "vit", 40);
        double[] got = new double[3];
        RuneCast.register("mmorpgBreakTestRune", (p, r, P, k) -> {
            got[0] = P;
            got[1] = k.numOr("heal", -1);
            got[2] = k.numOr("shield", -1);
            return true;
        });
        Map<String, Object> m = new HashMap<>();
        m.put("fn", "mmorpgBreakTestRune");
        m.put("name", "돌파 시험 룬");
        m.put("heal", 0.1);
        m.put("shield", 0.2);
        Runes.Rune base = rune("vit", "common");
        Runes.Rune r = new Runes.Rune(base.id(), base.family(), base.stat(), base.famName(), base.grade(), new Obj(m, "test"), base.cd(), base.gradeName(),
                base.gradeColor());
        h.assertTrue(RuneCast.castRune(sp, r, false, 0), "cast 0");
        double p0 = got[0];
        h.assertTrue(Math.abs(p0 - RuneCast.power(sp, "vit")) < 1e-9 && p0 > 0, "0: power " + p0);
        h.assertTrue(got[1] == 0.1 && got[2] == 0.2, "0: def as is " + got[1] + " / " + got[2]);
        RuneCast.resetCooldowns(sp);
        h.assertTrue(RuneCast.castRune(sp, r, false, 4), "cast 4");
        h.assertTrue(Math.abs(got[0] - p0 * 1.4) < 1e-9, "4: power ×1.4 " + got[0] + " vs " + p0);
        h.assertTrue(Math.abs(got[1] - 0.14) < 1e-9 && Math.abs(got[2] - 0.28) < 1e-9, "4: heal · shield ×1.4 " + got[1] + " / " + got[2]);
        h.assertTrue(Breakthrough.castLevel(sp, "mmorpgBreakTestRune") == 4, "cast level noted");
        RuneCast.resetCooldowns(sp);
        h.assertTrue(RuneCast.castRune(sp, r, false, 9), "cast 9");
        h.assertTrue(Math.abs(got[0] - p0 * 1.6) < 1e-9 && Breakthrough.castLevel(sp, "mmorpgBreakTestRune") == 6, "9 → 6: ×1.6 " + got[0]);
        RpgGearTests.done(h, sp);
    }

    /** 실제 피해: 수호의 함성 (둘레 피해) 0돌 → 5돌 = ×1.5 (같은 자리의 새 과녁) */
    @GameTest(maxTicks = 30)
    public void damageScalesWithLevel(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "vit", 30);
        Runes.Rune r = rune("vit", "common");
        Husk z0 = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, 3.5F));
        h.assertTrue(RuneCast.castRune(sp, r, false, 0), "cast 0");
        double d0 = TestDummies.dealt(z0);
        double want0 = RuneCast.power(sp, "vit") * r.def().num("mult") * mmorpg.fabric.stattree.StatTrees.runeMult(sp);
        h.assertTrue(d0 > 0 && Math.abs(d0 - want0) < 1e-3, "0: dealt " + d0 + " want " + want0);
        z0.discard();
        RuneCast.resetCooldowns(sp);
        Husk z5 = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, 3.5F));
        h.assertTrue(RuneCast.castRune(sp, r, false, 5), "cast 5");
        double d5 = TestDummies.dealt(z5);
        h.assertTrue(Math.abs(d5 - d0 * 1.5) < 1e-3, "5: dealt " + d5 + " = 1.5 × " + d0);
        RpgGearTests.done(h, sp);
    }

    /**
     * 초월 룬 (불사의 맹약 — 쓰면 바로 하늘빛): 0 · 2돌은 화면 색 연출 없음, 3돌 하늘빛 (테두리 빛 없음), 6돌 하늘빛 + 테두리 빛, 룬 아이템을 쓰면 그 아이템의 단계,
     * 메테오 2돌 + 0돌 → 3돌 돌파 때 그 룬의 하늘빛을 미리 보여 줌, 천붕 6돌은 검이 한 번 더 떨어짐 (위력 60% — 앞 10칸의 과녁), 메테오 6돌은 맞은 적 기절.
     * 하늘빛은 둘레 사람 모두에게 가므로 이 시험 하나에서 차례로. 시험 환경 mmorpg:runebreak 로 따로 묶음 (앞 10칸 과녁은 시험 자리 밖 —
     * 다른 시험과 함께 돌면 남의 피해로 무적 시간이 걸려 두 번째 검이 들어가지 않을 수 있었음 · 천붕 9칸 범위가 남의 과녁도 맞힘, 빠른 CI 러너에서 2026-10-13).
     */
    @GameTest(environment = "mmorpg:runebreak", maxTicks = 260)
    public void transcendScreenColorByLevel(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "vit", 30);
        Runes.Rune u = rune("vit", "transcend");
        String fog = RuneKitView.fogOf("undyingPact");
        h.assertTrue(fog != null, "undying has a sky");
        for (int lv : new int[] {0, 2}) {
            Ult.forgetSky(sp);
            RuneCast.resetCooldowns(sp);
            h.assertTrue(RuneCast.castRune(sp, u, false, lv), "cast " + lv);
            h.assertTrue(Ult.skyOf(sp) == null, lv + ": no screen color " + Ult.skyOf(sp));
            h.assertTrue(Breakthrough.castFx(sp, "undyingPact") == null, lv + ": no fx tier");
        }
        Ult.forgetSky(sp);
        RuneCast.resetCooldowns(sp);
        h.assertTrue(RuneCast.castRune(sp, u, false, 3), "cast 3");
        h.assertTrue(fog.equals(Ult.skyOf(sp)) && !Ult.skyEdgeOf(sp), "3: sky without edge " + Ult.skyOf(sp));
        Ult.forgetSky(sp);
        RuneCast.resetCooldowns(sp);
        h.assertTrue(RuneCast.castRune(sp, u, false, 6), "cast 6");
        h.assertTrue(fog.equals(Ult.skyOf(sp)) && Ult.skyEdgeOf(sp), "6: sky + edge " + Ult.skyOf(sp));
        // 아이템으로 쓰면 그 아이템의 단계
        Ult.forgetSky(sp);
        RuneCast.resetCooldowns(sp);
        ItemStack st = Breakthrough.stack("vit", "transcend", 3);
        sp.setItemInHand(InteractionHand.MAIN_HAND, st);
        RuneCast.onUse(sp, st, RuneItems.runeOf(st));
        h.assertTrue(Breakthrough.castLevel(sp, "undyingPact") == 3 && fog.equals(Ult.skyOf(sp)) && !Ult.skyEdgeOf(sp), "item use at 3");
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // 3돌 돌파 미리 보기: 메테오 2돌 (가방 20) + 0돌 (가방 21) → 3돌, 그 룬의 하늘빛 (이 사람에게만)
        Ult.forgetSky(sp);
        sp.getInventory().setItem(20, Breakthrough.stack("int", "transcend", 2));
        sp.getInventory().setItem(21, Breakthrough.stack("int", "transcend", 0));
        List<Breakthrough.Merge> opts = Breakthrough.options(sp);
        h.assertTrue(opts.size() == 1 && opts.get(0).result() == 3, "one merge to 3: " + opts);
        String msg = Breakthrough.merge(sp, opts.get(0));
        h.assertTrue(msg.contains("3돌"), "merged: " + msg);
        String meteorFog = RuneKitView.fogOf("meteor");
        h.runAfterDelay(14, () -> {
            h.assertTrue(meteorFog != null && meteorFog.equals(Ult.skyOf(sp)), "tier preview sky " + Ult.skyOf(sp));
            // 천붕 6돌: 첫 검 (delay 24틱) → 34틱 뒤 한 번 더 (delay 14틱) — 두 번째는 위력 60%
            RpgPlayers.setStat(sp, "str", 30);
            // 앞 10칸 (불사의 맹약 반격 범위 5.5칸 밖 — 앞에서 쓴 맹약이 아직 돎). 시험 자리 (8 × 8) 밖이라 그 청크를 붙잡아 둠: 붙잡지 않으면
            // 함께 도는 시험이 없을 때 그 청크의 몹이 돌지 않아 (틱이 빠른 시험 서버) 첫 검의 무적 시간 (20틱) 이 줄지 않고 두 번째 검이 막혔음
            Husk z = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, 11.5F));
            String held = mmorpg.fabric.boss.Ticking.holdArea(h.getLevel(), z.position(), 3, "t");
            Runes.Rune fall = rune("str", "transcend");
            RuneCast.resetCooldowns(sp);
            h.assertTrue(RuneCast.castRune(sp, fall, false, 6), "heavenFall 6 cast");
            RuneBreak.Extra ex = RuneBreak.extraFor(fall, 6);
            int first = (int) RuneBreak.def(fall, 6).num("delay");
            int second = ex.after() + (int) RuneBreak.echoDef(fall, 6).num("delay");
            double[] d1 = {0};
            h.runAfterDelay(first + 4, () -> {
                d1[0] = TestDummies.dealt(z);
                h.assertTrue(d1[0] > 0, "first sword hit " + d1[0]);
                h.assertTrue(Breakthrough.castLevel(sp, "heavenFall") == 6, "cast level 6");
            });
            // 두 번째 검: 떨어질 틱 + 4 부터 들어올 때까지 (최대 30틱 더 — 무적 시간으로 밀리면) 기다려 봄
            h.runAfterDelay(second + 4, () -> RpgScorpionsTests.await(h, () -> TestDummies.dealt(z) - d1[0] > 0, 30, () -> {
                double d2 = TestDummies.dealt(z) - d1[0];
                h.assertTrue(Math.abs(d2 - d1[0] * ex.mult()) < 1e-3 * Math.max(1, d1[0]), "second sword = 60% of the first: " + d2 + " vs " + d1[0]);
                // 메테오 6돌: 맞은 적 기절 (구속 X · 1초) — 5돌은 기절 없음
                z.removeAllEffects();
                Runes.Rune met = rune("int", "transcend");
                RuneCast.resetCooldowns(sp);
                h.assertTrue(RuneCast.castRune(sp, met, false, 6), "meteor 6 cast");
                int land = (int) RuneBreak.def(met, 6).num("delay");
                h.runAfterDelay(land + 2, () -> {
                    net.minecraft.world.effect.MobEffectInstance slow = z.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
                    h.assertTrue(slow != null && slow.getAmplifier() >= 10, "meteor 6 stuns: " + slow);
                    RuneSystem.forget(sp.getUUID());
                    mmorpg.fabric.boss.Ticking.releaseArea(held);
                    RpgGearTests.done(h, sp);
                });
            }));
        });
    }

    /** 합치기 하나: 남는 룬 = 가장 높은 단계, 재료 = 같은 단계면 뒤 칸 · 핫바의 재료를 쓰면 결과는 핫바 칸에 */
    @GameTest(maxTicks = 20)
    public void mergePicksTargetAndMaterial(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        String id = Runes.runeId("str", "noble");
        sp.getInventory().setItem(3, Breakthrough.stack("str", "noble", 0));
        sp.getInventory().setItem(20, Breakthrough.stack("str", "noble", 0));
        sp.getInventory().setItem(21, Breakthrough.stack("str", "noble", 2));
        sp.getInventory().setItem(22, Breakthrough.stack("dex", "common", 0)); // 하나뿐 — 돌파 없음
        List<Breakthrough.Merge> opts = Breakthrough.options(sp);
        h.assertTrue(opts.size() == 1, "one rune can break through: " + opts);
        Breakthrough.Merge m = opts.get(0);
        h.assertTrue(m.target().slot() == 21 && m.material().slot() == 20 && m.result() == 3 && m.slot() == 21, "target 21 (2) + bag 20 (0) → 3 at 21: " + m);
        h.assertTrue(Breakthrough.merge(sp, m).contains("3돌"), "merged");
        h.assertTrue(at(sp, 21).equals(id + "@3") && at(sp, 20).equals("-") && at(sp, 3).equals(id + "@0"), "after 1: " + at(sp, 3) + " " + at(sp, 20) + " " + at(sp, 21));
        m = Breakthrough.options(sp).get(0);
        h.assertTrue(m.target().slot() == 21 && m.material().slot() == 3 && m.result() == 4 && m.slot() == 3, "bag 21 (3) + hotbar 3 (0) → 4 into the hotbar: " + m);
        Breakthrough.merge(sp, m);
        h.assertTrue(at(sp, 3).equals(id + "@4") && at(sp, 21).equals("-"), "after 2: " + at(sp, 3) + " " + at(sp, 21));
        h.assertTrue(Breakthrough.options(sp).isEmpty(), "no more");
        h.assertTrue(at(sp, 22).equals(Runes.runeId("dex", "common") + "@0"), "other rune untouched");
        RpgGearTests.done(h, sp);
    }

    /** 넘침 (3 + 3 = 7) · 다른 룬 · 옮긴 룬 · 단계가 바뀐 룬은 거절 (아무것도 바꾸지 않음) */
    @GameTest(maxTicks = 20)
    public void mergeRefusesOverflowAndMovedRunes(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Runes.Rune r = rune("holy", "rare");
        sp.getInventory().setItem(10, Breakthrough.stack("holy", "rare", 3));
        sp.getInventory().setItem(11, Breakthrough.stack("holy", "rare", 3));
        h.assertTrue(Breakthrough.options(sp).isEmpty(), "3 + 3 cannot merge (no option)");
        Breakthrough.Merge over = new Breakthrough.Merge(new Breakthrough.Held(10, r, 3), new Breakthrough.Held(11, r, 3), 6, 10);
        h.assertTrue(Breakthrough.merge(sp, over).contains("넘어서"), "overflow refused");
        h.assertTrue(at(sp, 10).endsWith("@3") && at(sp, 11).endsWith("@3"), "both kept");
        sp.getInventory().setItem(11, Breakthrough.stack("holy", "rare", 1));
        Breakthrough.Merge m = Breakthrough.options(sp).get(0);
        h.assertTrue(m.result() == 5, "3 + 1 → 5: " + m);
        sp.getInventory().setItem(12, sp.getInventory().getItem(11).copy()); // 재료를 옮김
        sp.getInventory().setItem(11, ItemStack.EMPTY);
        h.assertTrue(Breakthrough.merge(sp, m).contains("옮겨져서"), "moved material refused");
        h.assertTrue(at(sp, 10).endsWith("@3") && at(sp, 12).endsWith("@1"), "nothing changed");
        Breakthrough.Merge m2 = Breakthrough.options(sp).get(0);
        Breakthrough.write(sp.getInventory().getItem(10), 4); // 남는 룬의 단계가 바뀜
        h.assertTrue(Breakthrough.merge(sp, m2).contains("옮겨져서"), "changed level refused");
        Runes.Rune other = rune("holy", "noble");
        Breakthrough.Merge mixed = new Breakthrough.Merge(new Breakthrough.Held(10, r, 4), new Breakthrough.Held(12, other, 1), 6, 10);
        h.assertTrue(Breakthrough.merge(sp, mixed).contains("같은 룬"), "different runes refused");
        RpgGearTests.done(h, sp);
    }

    /** 모두 돌파: 룬마다 가장 높은 것 하나를 6돌을 넘지 않게 끝까지 (남는 것은 그대로) */
    @GameTest(maxTicks = 20)
    public void mergeAllRaisesBestToSix(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        String a = Runes.runeId("str", "common");
        String b = Runes.runeId("dex", "rare");
        for (int i = 9; i <= 16; i++) sp.getInventory().setItem(i, Breakthrough.stack("str", "common", 0)); // 8개
        sp.getInventory().setItem(3, Breakthrough.stack("dex", "rare", 1)); // 핫바
        sp.getInventory().setItem(20, Breakthrough.stack("dex", "rare", 0));
        sp.getInventory().setItem(21, Breakthrough.stack("dex", "rare", 4));
        List<Breakthrough.Merge> plan = Breakthrough.planAll(sp);
        h.assertTrue(plan.size() == 7, "6 merges for str + 1 for dex: " + plan.size());
        String msg = Breakthrough.mergeAll(sp, plan);
        h.assertTrue(msg.contains("돌파 7번"), "message " + msg);
        h.assertTrue(at(sp, 9).equals(a + "@6") && at(sp, 10).equals(a + "@0"), "str: slot 9 → 6, one left " + at(sp, 9) + " " + at(sp, 10));
        for (int i = 11; i <= 16; i++) h.assertTrue(at(sp, i).equals("-"), "str material " + i + " used");
        h.assertTrue(at(sp, 3).equals(b + "@6") && at(sp, 21).equals("-") && at(sp, 20).equals(b + "@0"), "dex: 4 + 1 → 6 into the hotbar, 0 left: "
                + at(sp, 3) + " " + at(sp, 20) + " " + at(sp, 21));
        h.assertTrue(Breakthrough.planAll(sp).isEmpty(), "nothing more (6 + 0 overflows)");
        RpgGearTests.done(h, sp);
    }

    /** 갈기: 돌파한 룬 = 조각 × (1 + 단계), 단계가 바뀌면 거절, 겹친 룬 모두 갈기는 가장 높은 단계를 남김 */
    @GameTest(maxTicks = 20)
    public void grindCountsBreakthrough(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        int v = (int) SMITH.obj("grind").num("noble");
        String id = Runes.runeId("int", "noble");
        sp.getInventory().setItem(2, Breakthrough.stack("int", "noble", 0));
        sp.getInventory().setItem(15, Breakthrough.stack("int", "noble", 2));
        List<RuneSmith.RuneSlot> dups = RuneSmith.duplicateSlots(sp);
        h.assertTrue(dups.size() == 1 && dups.get(0).slot() == 2, "keeps the 2-level rune (bag), grinds the hotbar one: " + dups);
        h.assertTrue(RuneSmith.grindValue(rune("int", "noble"), 2) == v * 3, "value ×3");
        String bad = RuneSmith.grindRunes(sp, List.of(new RuneSmith.Pick(15, id, 1)));
        h.assertTrue(bad.contains("옮겨져서") && at(sp, 15).equals(id + "@2"), "level changed: refused " + bad);
        String msg = RuneSmith.grindRunes(sp, List.of(new RuneSmith.Pick(15, id, 2)));
        h.assertTrue(msg.contains("+" + v * 3), "ground for " + v * 3 + ": " + msg);
        h.assertTrue(RpgGearTests.countIn(sp, RuneSmith.shardItem()) == v * 3 && at(sp, 15).equals("-"), "shards " + RpgGearTests.countIn(sp, RuneSmith.shardItem()));
        RpgGearTests.done(h, sp);
    }

    /** 룬술사 돌파 창: 목록 (숫자 = 돌파 뒤 단계) → 확인 창 → 돌파 → 돌아온 창 · 다른 창으로 오가기 (갈기 · 뽑기권 ↔ 돌파) */
    @GameTest(maxTicks = 20)
    public void breakWindowFlow(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            String id = Runes.runeId("assassin", "hero");
            sp.getInventory().setItem(20, Breakthrough.stack("assassin", "hero", 0));
            sp.getInventory().setItem(21, Breakthrough.stack("assassin", "hero", 0));
            RuneSmith.openRunesmith(sp, "break");
            FormNet.ChestPayload c = chest(sp);
            h.assertTrue(c != null && c.title().contains("돌파") && c.slots().size() == 27, "break window " + (c == null ? null : c.title()));
            String row = mmorpg.fabric.BedrockText.plain(c.slots().get(RuneSmith.LIST_FIRST).name());
            h.assertTrue(row.contains("0돌 → 1돌") && c.slots().get(RuneSmith.LIST_FIRST).kind() != Slot.DECO, "rune slot 0 → 1: " + row);
            h.assertTrue(c.slots().get(RuneSmith.BREAK_ALL_SLOT).num() == 1, "all: 1 merge");
            h.assertTrue(c.slots().get(RuneSmith.TAB).name().contains("갈기") && c.slots().get(RuneSmith.TAB2).name().contains("뽑기권"), "tabs");
            Forms.answer(sp, RuneSmith.LIST_FIRST, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().equals("룬 돌파") && c.body().contains("[1돌]"), "confirm " + (c == null ? null : c.body()));
            Forms.answer(sp, 11, null);
            h.assertTrue(at(sp, 20).equals(id + "@1") && at(sp, 21).equals("-"), "merged: " + at(sp, 20) + " " + at(sp, 21));
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("돌파") && c.body().contains("돌파:"), "back with message " + (c == null ? null : c.body()));
            h.assertTrue(c.slots().get(RuneSmith.BREAK_ALL_SLOT).kind() == Slot.DECO, "nothing more");
            Forms.answer(sp, RuneSmith.TAB2, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("뽑기권") && c.slots().get(RuneSmith.TAB2).name().contains("돌파"), "to tickets");
            Forms.answer(sp, RuneSmith.TAB2, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("돌파"), "back to break");
            Forms.answer(sp, RuneSmith.TAB, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("룬 갈기") && c.slots().get(RuneSmith.TAB2).name().contains("돌파"), "to grind");
            int v = (int) SMITH.obj("grind").num("hero");
            h.assertTrue(c.slots().get(RuneSmith.LIST_FIRST).num() == v * 2, "grind value of the 1-level rune " + c.slots().get(RuneSmith.LIST_FIRST).num());
            Forms.answer(sp, RuneSmith.TAB2, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("돌파"), "grind → break");
            Forms.answer(sp, -1, null);
            h.assertFalse(Forms.pending(sp), "closed");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
        }
        RpgGearTests.done(h, sp);
    }

    /** RuneKit 의 하늘빛 이름 (ULT_FX.runes[fn].fog) */
    static final class RuneKitView {
        private RuneKitView() {}

        static String fogOf(String fn) {
            return mmorpg.fabric.rune.RuneKit.ultOf(fn).strOrNull("fog");
        }
    }
}
