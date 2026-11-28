package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import mmorpg.core.Combat;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneData;
import mmorpg.fabric.rune.RuneGacha;
import mmorpg.fabric.rune.RuneItem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSign;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 룬 게임 시험 (서버): 룬 아이템 85 · 뽑기권 등록, 룬 효과 85 등록 (0.12.6 사신 계열 포함), 뽑기권 (바로 쓰고 연출 뒤 룬 지급 · 연출 중 다시 못 뽑음) —
 * 룬은 스킬 칸에서만 씀 (칸을 고르면 바로 쓰기는 11단계에서 없앰 — RpgSkillBarTests),
 * 룬 쓰기 (위력 · 재사용 대기 · 핫바 쓸기 표시 · 대상이 없으면 재사용 대기 없음), 평타 강화 (평타에 더함),
 * 무기 조건 없음 (0.12.6 에 사신 = 낫 전용이었으나 0.27.0 에 뺌 — 맨손 · 칼 · 낫 모두 씀).
 */
public final class RpgRuneTests {
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        try {
            Method m = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            m.setAccessible(true);
            m.invoke(sp.connection);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot mark mock player loaded", e);
        }
        sp.setGameMode(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        RpgPlayers.onJoin(sp);
        return sp;
    }

    private static void done(GameTestHelper h, ServerPlayer sp) {
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    /** 가방의 룬 아이템 수 */
    private static int runesIn(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) if (sp.getInventory().getItem(i).getItem() instanceof RuneItem) n++;
        return n;
    }

    @GameTest(maxTicks = 20)
    public void runeItemsRegistered(GameTestHelper h) {
        // 뽑기 계열 17 × 등급 5 + 0.16.0 한정 초월 룬 셋 (초월 하나씩)
        // 2026-11-14 숨긴 계열 (RUNE_HIDDEN) 의 아이템도 그대로 (이미 가진 룬)
        // 2026-11-17 룬 재개발: 뽑기 룬 = 새 룬 30 (등급 하나씩) · 옛 계열 스물셋 (숨김 — 아이템은 남김, 접속 때 바꿈) × 5 · 한정 다섯
        h.assertTrue(RuneItems.runes().size() == Runes.drawCount() + Runes.HIDDEN.size() * Runes.GRADE_ORDER.size() + Runes.LIMITED_ORDER.size(), "runes "
                + RuneItems.runes().size());
        for (String f : Runes.LIMITED_ORDER) {
            RuneItem it = RuneItems.byId(Runes.runeId(f, "transcend"));
            h.assertTrue(it != null && RuneCast.has(it.rune().def().str("fn")), "limited rune item " + f);
            h.assertTrue(RuneItems.byId(Runes.runeId(f, "hero")) == null, "no limited hero " + f);
        }
        for (String f : Runes.FAMILY_ORDER) {
            for (String g : Runes.GRADE_ORDER) {
                if (!Runes.hasGrade(f, g)) continue;
                RuneItem it = RuneItems.byId(Runes.runeId(f, g));
                h.assertTrue(it != null && it.rune().family().equals(f) && it.rune().grade().equals(g), "rune item " + f + " " + g);
                String name = it.getName(new ItemStack(it)).getString();
                // 2026-11-17 계열 이름 없음 ("어스 브레이커 · 룬 (일반)")
                h.assertTrue(name.contains(it.rune().def().str("name")) && name.contains(Runes.kindLabel(it.rune())) && !name.contains(" 계열"), "name " + name);
            }
        }
        h.assertTrue(RuneItems.TICKET != null && RuneItems.ADV_TICKET != null && RuneItems.TICKET != RuneItems.ADV_TICKET, "tickets");
        h.assertTrue(new ItemStack(RuneItems.TICKET).getMaxStackSize() == 64 && new ItemStack(RuneItems.byId("rpg:rune_str_hero")).getMaxStackSize() == 1, "stack sizes");
        h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void allRuneFnsRegistered(GameTestHelper h) {
        StringBuilder missing = new StringBuilder();
        for (String f : Runes.FAMILY_ORDER) {
            for (String g : Runes.GRADE_ORDER) {
                if (!Runes.hasGrade(f, g)) continue;
                String fn = Runes.parseRune(Runes.runeId(f, g)).def().str("fn");
                if (!RuneCast.has(fn)) missing.append(' ').append(f).append('_').append(g).append('=').append(fn);
            }
        }
        h.assertTrue(missing.isEmpty(), "rune fns not registered:" + missing);
        h.succeed();
    }

    @GameTest(maxTicks = 80)
    public void ticketDrawsRune(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.setItemInHand(InteractionHand.MAIN_HAND, RuneItems.ticket(2, false));
        int before = runesIn(sp);
        h.assertTrue(RuneGacha.useTicket(sp, false), "draw");
        h.assertTrue(sp.getMainHandItem().getCount() == 1, "ticket used first: " + sp.getMainHandItem().getCount());
        h.assertTrue(RuneData.pending(sp).size() == 1 && RuneGacha.drawing(sp), "pending while drawing");
        h.assertFalse(RuneGacha.useTicket(sp, false), "no second draw while drawing");
        h.assertTrue(sp.getMainHandItem().getCount() == 1, "ticket kept when blocked");
        h.runAfterDelay(RuneGacha.DRAW_TICKS + 2, () -> {
            h.assertFalse(RuneGacha.drawing(sp), "draw ended");
            h.assertTrue(RuneData.pending(sp).isEmpty(), "pending delivered");
            h.assertTrue(runesIn(sp) == before + 1, "got a rune: " + runesIn(sp));
            // 고급 뽑기권이 없으면 쓰지 않음 (적은 것도 되돌림)
            h.assertFalse(RuneGacha.useTicket(sp, true), "no advanced ticket");
            h.assertTrue(RuneData.pending(sp).isEmpty(), "pending rolled back");
            done(h, sp);
        });
    }

    /** 평타 강화 (날 세우기): 다음 평타에 위력 × bonus 를 더함 (치명타 뒤에 더함 — 함께 도는 시험 때문에 치명타 여부는 둘 다 맞는 것으로) */
    @GameTest(maxTicks = 40)
    public void strikeRuneAddsToBasicAttack(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        for (Weapons.Req q : w.reqs()) RpgPlayers.setStat(sp, q.stat(), (int) Math.ceil(q.value()) + 10);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(w.id())));
        Husk z = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, 3.5F));
        Runes.Rune r = Runes.parseRune("rpg:rune_strike_common");
        h.assertTrue(RuneCast.castRune(sp, r, false), "cast");
        double bonus = RuneCast.power(sp, "strike") * r.def().num("bonus") * Runes.damageScale(r); // 0.50.0 범위 보정 (평타에 덧붙임 — 날 세우기 단일 +15% · 가르기 좁은 범위 +8%)
        double base = w.damage() * Combat.powerMultiplier(RpgStats.effective(sp), w, 0); // 0.39.0: 공격 대기 충전 없이 온전한 피해
        sp.attack(z);
        double dealt = TestDummies.dealt(z);
        h.assertTrue(Math.abs(dealt - (base + bonus)) < 1e-3 || Math.abs(dealt - (base * w.critMult() + bonus)) < 1e-3,
                "basic " + base + " + strike " + bonus + " vs dealt " + dealt);
        done(h, sp);
    }

    /**
     * 2026-11-27 룬 시전 서명 (사용자 "룬 쓸때 룬 글자 띄울거임"): 룬이 나가면 그 룬의 글자 (그림판 rn_sign — 룬 30 다음 한정 5, 룬마다 5칸) 가 뜸.
     * 대상이 없어 취소한 룬 · 숨긴 옛 계열은 없음
     */
    @GameTest(maxTicks = 40)
    public void runeSignFlashes(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(mmorpg.fabric.vfx.Vfx.hasSheet("rn_sign") && mmorpg.fabric.vfx.Vfx.sheet("rn_sign").frames() == 45 * RuneSign.FRAMES,
                "sheet rn_sign 45 runes (룬 40 + 한정 5 — 2026-11-28 근접 딜 · 지원 다섯씩) × " + RuneSign.FRAMES);
        h.assertTrue(RuneSign.cellOf("earthbreaker") == 0 && RuneSign.cellOf("immortaloath") == 37 && RuneSign.cellOf("grandcross") == 38
                && RuneSign.cellOf("edenbloom") == 39 && RuneSign.cellOf("endless") == 40 && RuneSign.cellOf("glacier") == 44 && RuneSign.cellOf("str") == -1,
                "cells " + RuneSign.cellOf("earthbreaker") + " " + RuneSign.cellOf("glacier"));
        boolean[] target = {false};
        RuneCast.register("mmorpgSignRune", (p, r, P, k) -> target[0]);
        Map<String, Object> m = new HashMap<>();
        m.put("fn", "mmorpgSignRune");
        m.put("name", "시험 룬");
        Runes.Rune base = Runes.parseRune("rpg:rune_earthbreaker_common");
        Runes.Rune r = new Runes.Rune(base.id(), base.family(), base.stat(), base.famName(), base.grade(), new Obj(m, "test"), base.cd(), base.gradeName(),
                base.gradeColor());
        int n0 = RuneSign.shown;
        h.assertFalse(RuneCast.castRune(sp, r, false), "no target");
        h.assertTrue(RuneSign.shown == n0, "cancelled cast: no sign");
        target[0] = true;
        h.assertTrue(RuneCast.castRune(sp, r, false), "cast");
        h.assertTrue(RuneSign.shown == n0 + 1 && "earthbreaker".equals(RuneSign.lastFamily) && RuneSign.lastCell == 0,
                "sign " + RuneSign.shown + " " + RuneSign.lastFamily + " " + RuneSign.lastCell);
        done(h, sp);
    }

    @GameTest(maxTicks = 40)
    public void castStartsCooldown(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgPlayers.setStat(sp, "str", 50);
        int[] calls = {0};
        double[] power = {0};
        boolean[] target = {false};
        RuneCast.register("mmorpgTestRune", (p, r, P, k) -> {
            calls[0]++;
            power[0] = P;
            return target[0];
        });
        Map<String, Object> m = new HashMap<>();
        m.put("fn", "mmorpgTestRune");
        m.put("name", "시험 룬");
        Runes.Rune base = Runes.parseRune("rpg:rune_str_common");
        Runes.Rune r = new Runes.Rune(base.id(), base.family(), base.stat(), base.famName(), base.grade(), new Obj(m, "test"), base.cd(), base.gradeName(),
                base.gradeColor());
        ItemStack stack = new ItemStack(RuneItems.byId(base.id()));
        // 대상이 없음 (false) → 재사용 대기 없음
        h.assertFalse(RuneCast.castRune(sp, r, false), "no target");
        h.assertTrue(calls[0] == 1 && RuneCast.cooldownLeft(sp, r) == 0 && !sp.getCooldowns().isOnCooldown(stack), "no cooldown when cancelled");
        target[0] = true;
        h.assertTrue(RuneCast.castRune(sp, r, false), "cast");
        // 0.49.0 위력 P = 룬 위력 × 피해 배율 (그 룬 자리 — 대지 강타: 기절 0.5초 −5%)
        h.assertTrue(Math.abs(power[0] - RuneCast.power(sp, "str") * Runes.damageScale(r)) < 1e-9 && power[0] > 0, "power " + power[0]);
        h.assertTrue(RuneCast.cooldownLeft(sp, r) == Math.round(base.cd() * 20), "cooldown " + RuneCast.cooldownLeft(sp, r));
        h.assertTrue(sp.getCooldowns().isOnCooldown(stack), "hotbar sweep");
        h.assertFalse(RuneCast.castRune(sp, r, false), "blocked by cooldown");
        h.assertTrue(calls[0] == 2, "fn not called while cooling: " + calls[0]);
        RuneCast.resetCooldowns(sp);
        h.assertTrue(RuneCast.cooldownLeft(sp, r) == 0 && !sp.getCooldowns().isOnCooldown(stack), "reset");
        done(h, sp);
    }

    /** 0.27.0 사신 계열의 낫 조건을 뺌 (사용자 "사신의 룬 조건인 낫을 없애고"): 맨손 · 칼 · 낫 모두 씀 (효과를 부르고 재사용 대기) · 다른 계열도 그대로 */
    @GameTest(maxTicks = 40)
    public void reaperRuneAnyWeapon(GameTestHelper h) {
        ServerPlayer sp = player(h);
        int[] calls = {0};
        RuneCast.register("mmorpgTestReaper", (p, r, P, k) -> {
            calls[0]++;
            return true;
        });
        Map<String, Object> m = new HashMap<>();
        m.put("fn", "mmorpgTestReaper");
        m.put("name", "시험 사신 룬");
        Runes.Rune base = Runes.parseRune("rpg:rune_reaper_common");
        h.assertTrue(base != null && Runes.weaponOnly(base.family()) == null, "reaper rune has no weapon condition");
        Runes.Rune r = new Runes.Rune(base.id(), base.family(), base.stat(), base.famName(), base.grade(), new Obj(m, "test"), base.cd(), base.gradeName(),
                base.gradeColor());
        RuneCast.resetCooldowns(sp);
        h.assertTrue(RuneCast.usableNow(sp, r) && RuneCast.castRune(sp, r, true), "bare hand: cast");
        h.assertTrue(calls[0] == 1 && RuneCast.cooldownLeft(sp, r) > 0, "cast with cooldown: calls " + calls[0]);
        RuneCast.resetCooldowns(sp);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(Weapons.weaponId("sword", 10, "common"))));
        h.assertTrue(RuneCast.castRune(sp, r, true) && calls[0] == 2, "sword: cast");
        RuneCast.resetCooldowns(sp);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(Weapons.weaponId("scythe", 10, "common"))));
        h.assertTrue(RuneCast.castRune(sp, r, true) && calls[0] == 3, "scythe: cast");
        Runes.Rune str = Runes.parseRune("rpg:rune_str_common");
        h.assertTrue(RuneCast.usableNow(sp, str), "other families: any weapon");
        done(h, sp);
    }
}
