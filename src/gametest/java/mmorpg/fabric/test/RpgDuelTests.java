package mmorpg.fabric.test;

import mmorpg.core.Rules;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.duel.DuelSystem;
import mmorpg.fabric.duel.Duels;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * 6단계 게임 시험 (서버) — 결투 (duel.js · pvp.js): 신청 → 받는 사람 대화창 → 수락 → 3 · 2 · 1 (그동안은 피해 없음) → 시작 (세계 PvP 가 꺼져 있었으면 켬),
 * 결투 상대의 피해 × PVP.mult (90% 감소), 결투가 아닌 플레이어 사이 피해는 막음, 쓰러지면 죽지 않고 체력 1 · 짐 (이긴 사람 · 진 사람) · 진 뒤 잠깐 떨어짐 피해로 쓰러지지 않음,
 * /pvp 끝 · 모드 바꿈으로 끝 → 세계 PvP 되돌림, 거절 → 다시 신청 대기, 신청 받지 않기, 서로 신청 = 바로 수락, 아군 효과 (회복 · 보호막) 는 결투 중인 사람에게 안 줌,
 * 스킬 · 룬 · 겨누기는 결투 상대만, 상태 줄.
 */
public final class RpgDuelTests {
    /** 세계 PvP 규칙 · 결투 기록은 서버 하나에 하나 — 시험마다 따로 (같은 묶음은 동시에 돎) */
    static final String ENV = "mmorpg:duel";
    static final String ENV_END = "mmorpg:duel_end";
    static final String ENV_ASK = "mmorpg:duel_ask";
    static final double MULT = Rules.get().obj("PVP").num("mult");
    static final int COUNTDOWN = (int) Rules.get().obj("PVP").num("countdownTicks");
    static final double TAKEN = Rules.get().obj("CONFIG").num("DAMAGE_TAKEN_SCALE");

    static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        return sp;
    }

    static void bye(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) {
            Forms.HEADLESS.remove(p.getUUID());
            h.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    /** 연속 피해 막기 없이 */
    static boolean hurt(GameTestHelper h, LivingEntity e, DamageSource src, float amount) {
        e.setInvulnerableTime(0);
        e.damageCooldownTime = 0;
        return e.hurtServer(h.getLevel(), src, amount);
    }

    /** a 가 b 에게 신청 → b 가 대화창에서 수락 */
    static void startDuel(GameTestHelper h, ServerPlayer a, ServerPlayer b) {
        DuelSystem.resetRequests();
        DuelSystem.requestTo(a, b);
        h.assertTrue(DuelSystem.hasRequest(b), "request sent");
        h.assertTrue(Forms.last(b) instanceof FormNet.DialogPayload d && d.choices().size() == 2, "dialog " + Forms.last(b));
        h.assertTrue(Forms.answer(b, 0, null), "accepted");
        h.assertTrue(Duels.inDuel(a.getUUID()) && Duels.inDuel(b.getUUID()), "duel set");
    }

    @GameTest(environment = ENV, maxTicks = 200)
    public void duelFlowDamageAndDefeat(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        ServerLevel level = h.getLevel();
        s.getGameRules().set(GameRules.PVP, false, s); // 꺼진 세계: 결투 동안만 켬
        ServerPlayer a = player(h);
        ServerPlayer b = player(h);
        ServerPlayer c = player(h);
        startDuel(h, a, b);
        h.assertTrue(s.getGameRules().get(GameRules.PVP), "pvp rule on during duel");
        h.assertTrue(!Duels.isPartner(a, b), "countdown: not yet");
        h.assertTrue(Duels.hud(a).contains("초 뒤 결투"), "hud countdown " + Duels.hud(a));
        float b0 = b.getHealth();
        hurt(h, b, level.damageSources().playerAttack(a), 50F);
        h.assertTrue(b.getHealth() == b0, "no damage during countdown " + b.getHealth());
        h.runAfterDelay(COUNTDOWN + 2, () -> {
            h.assertTrue(Duels.isPartner(a, b) && Duels.isPartner(b, a), "started");
            h.assertTrue(Kit.duelPartner.test(a, b) && RpgCombat.aimable(a, b) && !RpgCombat.aimable(a, c), "skills · aim: partner only");
            h.assertTrue(Duels.hud(a).startsWith("§c⚔"), "hud " + Duels.hud(a));
            // 결투 상대의 피해 × mult
            float before = b.getHealth();
            hurt(h, b, level.damageSources().playerAttack(a), 100F);
            double lost = before - b.getHealth();
            h.assertTrue(Math.abs(lost - 100 * TAKEN * MULT) < 0.05, "scaled duel damage " + lost + " (expected " + 100 * TAKEN * MULT + ")");
            // 결투가 아닌 사람: 막힘 (양쪽)
            float c0 = c.getHealth();
            hurt(h, c, level.damageSources().playerAttack(a), 50F);
            hurt(h, a, level.damageSources().playerAttack(c), 50F);
            h.assertTrue(c.getHealth() == c0, "outsider protected");
            // 아군 효과: 결투 중인 사람에게는 안 줌 (자기 자신은 됨)
            h.assertTrue(!RuneKit.canAssist.test(c, a) && RuneKit.canAssist.test(a, a) && RuneKit.canAssist.test(c, c), "assist rules");
            // 쓰러뜨림: 죽지 않고 체력 1 → 다음 틱 결투 끝 (b 짐)
            hurt(h, b, level.damageSources().playerAttack(a), 100000F);
            h.assertTrue(b.isAlive() && b.getHealth() >= 1F - 1e-4 && b.getHealth() <= 1.0001F, "floored at 1: " + b.getHealth());
            h.runAfterDelay(2, () -> {
                h.assertTrue(!Duels.inDuel(a.getUUID()) && !Duels.inDuel(b.getUUID()), "duel over");
                h.assertTrue(!s.getGameRules().get(GameRules.PVP), "pvp rule restored");
                // 진 뒤 잠깐: 떨어짐 피해로 쓰러지지 않음
                hurt(h, b, level.damageSources().fall(), 1000F);
                h.assertTrue(b.isAlive() && b.getHealth() >= 0.999F, "grace after defeat " + b.getHealth());
                RpgHealth.healFull(b);
                bye(h, a, b, c);
                h.succeed();
            });
        });
    }

    @GameTest(environment = ENV_END, maxTicks = 200)
    public void endByCommandAndMode(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        s.getGameRules().set(GameRules.PVP, true, s); // 켜진 세계: 끝나도 그대로
        ServerPlayer a = player(h);
        ServerPlayer b = player(h);
        startDuel(h, a, b);
        DuelSystem.pvpCommand(a, "끝");
        h.assertTrue(!Duels.inDuel(a.getUUID()) && !Duels.inDuel(b.getUUID()) && s.getGameRules().get(GameRules.PVP), "ended by command, rule kept");
        startDuel(h, a, b);
        b.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(12, () -> {
            h.assertTrue(!Duels.inDuel(a.getUUID()), "ended by mode change");
            b.setGameMode(GameType.SURVIVAL);
            bye(h, a, b);
            h.succeed();
        });
    }

    @GameTest(environment = ENV_ASK, maxTicks = 40)
    public void declineRetryAndBlock(GameTestHelper h) {
        ServerPlayer a = player(h);
        ServerPlayer b = player(h);
        DuelSystem.resetRequests();
        DuelSystem.requestTo(a, b);
        h.assertTrue(Forms.answer(b, 1, null), "declined");
        h.assertTrue(!Duels.inDuel(a.getUUID()) && !DuelSystem.hasRequest(b), "no duel");
        DuelSystem.requestTo(a, b);
        h.assertTrue(!DuelSystem.hasRequest(b), "retry wait after decline");
        // 서로 신청 = 바로 수락
        DuelSystem.resetRequests();
        DuelSystem.requestTo(b, a);
        Forms.close(a);
        DuelSystem.requestTo(a, b);
        h.assertTrue(Duels.inDuel(a.getUUID()) && Duels.inDuel(b.getUUID()), "mutual request accepts");
        DuelSystem.pvpCommand(b, "end");
        // 신청 받지 않기
        DuelSystem.resetRequests();
        DuelSystem.pvpCommand(b, "차단");
        h.assertTrue(DuelSystem.blocksDuels(b), "blocking");
        DuelSystem.requestTo(a, b);
        h.assertTrue(!DuelSystem.hasRequest(b), "blocked request");
        DuelSystem.pvpCommand(b, "차단");
        h.assertTrue(!DuelSystem.blocksDuels(b), "unblocked");
        // 글로 수락
        DuelSystem.resetRequests();
        DuelSystem.requestTo(a, b);
        DuelSystem.pvpCommand(b, "수락");
        h.assertTrue(Duels.inDuel(b.getUUID()), "typed accept");
        DuelSystem.pvpCommand(a, "끝");
        bye(h, a, b);
        h.succeed();
    }
}
