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
 * 룬 게임 시험 (서버): 룬 아이템 80 · 뽑기권 등록, 룬 효과 80 등록, 뽑기권 (바로 쓰고 연출 뒤 룬 지급 · 연출 중 다시 못 뽑음) —
 * 룬은 스킬 칸에서만 씀 (칸을 고르면 바로 쓰기는 11단계에서 없앰 — RpgSkillBarTests),
 * 룬 쓰기 (위력 · 재사용 대기 · 핫바 쓸기 표시 · 대상이 없으면 재사용 대기 없음), 평타 강화 (평타에 더함).
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
        h.assertTrue(RuneItems.runes().size() == Runes.FAMILY_ORDER.size() * Runes.GRADE_ORDER.size(), "runes " + RuneItems.runes().size());
        for (String f : Runes.FAMILY_ORDER) {
            for (String g : Runes.GRADE_ORDER) {
                RuneItem it = RuneItems.byId(Runes.runeId(f, g));
                h.assertTrue(it != null && it.rune().family().equals(f) && it.rune().grade().equals(g), "rune item " + f + " " + g);
                String name = it.getName(new ItemStack(it)).getString();
                h.assertTrue(name.contains(it.rune().def().str("name")) && name.contains(it.rune().famName()), "name " + name);
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
        double bonus = RuneCast.power(sp, "strike") * r.def().num("bonus");
        float s = sp.getAttackStrengthScale(0.5F);
        double base = w.damage() * Combat.powerMultiplier(RpgStats.effective(sp), w, 0) * (0.2 + s * s * 0.8);
        sp.attack(z);
        double dealt = TestDummies.dealt(z);
        h.assertTrue(Math.abs(dealt - (base + bonus)) < 1e-3 || Math.abs(dealt - (base * w.critMult() + bonus)) < 1e-3,
                "basic " + base + " + strike " + bonus + " vs dealt " + dealt);
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
        h.assertTrue(Math.abs(power[0] - RuneCast.power(sp, "str")) < 1e-9 && power[0] > 0, "power " + power[0]);
        h.assertTrue(RuneCast.cooldownLeft(sp, r) == Math.round(base.cd() * 20), "cooldown " + RuneCast.cooldownLeft(sp, r));
        h.assertTrue(sp.getCooldowns().isOnCooldown(stack), "hotbar sweep");
        h.assertFalse(RuneCast.castRune(sp, r, false), "blocked by cooldown");
        h.assertTrue(calls[0] == 2, "fn not called while cooling: " + calls[0]);
        RuneCast.resetCooldowns(sp);
        h.assertTrue(RuneCast.cooldownLeft(sp, r) == 0 && !sp.getCooldowns().isOnCooldown(stack), "reset");
        done(h, sp);
    }
}
