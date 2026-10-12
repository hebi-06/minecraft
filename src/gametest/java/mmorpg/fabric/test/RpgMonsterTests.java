package mmorpg.fabric.test;

import java.util.List;
import java.util.function.DoubleSupplier;
import mmorpg.core.Json;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.HuntZones;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.mob.RpgMonster;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 일반 몬스터 (monsters.js) · 사냥터 (huntzone.js) · 전리품 (loot.js lootOnDeath):
 * 맞기 전에는 공격하지 않음 · 맞으면 windup 뒤 설정 attack 만큼 (난이도 · 갑옷 · 방패 규칙), 사거리 밖으로 나가면 빗나감, 멈춤 (크리에이티브),
 * 달려들기 · 속박/기절이면 달려들기/공격 없음, 원거리 반격 (맞음 · 고리 밖으로 피함), 껍질 -20% · 구속 · 언데드 · 무리 태그, 트롤 재생 · 회복 (다친 채 불러와도),
 * 도발, 죽으면 경험치 · 전리품 · 에메랄드 (Loot.rand) · 퀘스트 처치 수 · 경험치 제한, 이름표 (첫 줄 = 이름, 체력 막대 · 타고 있는 글자 표시),
 * 사냥터 (만들기 · 채우기 · 넘치면 치우기 · /mob clear 가 사냥터 몬스터를 남김 · 지우기 · 기록 JSON).
 * 사냥터 · /mob clear 시험은 다른 시험과 겹치지 않게 멀리 (x · z 7만) 하늘 (y 150) 에서. 통과하면 [monster-test] OK 줄.
 */
public final class RpgMonsterTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-monster-test");

    // ------------------------------------------------------------ 도우미

    /** 서바이벌 플레이어 (RpgGearTests.player) 를 (x, 2, z) 에, 체력 스탯 vit (최대 체력 20 + vit) */
    static ServerPlayer player(GameTestHelper h, double x, double y, double z, int vit) {
        ServerPlayer sp = RpgGearTests.player(h);
        Vec3 at = h.absoluteVec(new Vec3(x, y, z));
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        if (vit > 0) RpgPlayers.setStat(sp, "vit", vit);
        return sp;
    }

    /** 몬스터를 (x, 2, z) 에 (ai = false 면 제자리 — 목표 없음, 스크립트 공격은 그대로) */
    static RpgMonster monster(GameTestHelper h, String key, double x, double y, double z, boolean ai) {
        Vec3 at = h.absoluteVec(new Vec3(x, y, z));
        RpgMonster m = Monsters.spawnAt(h.getLevel(), key, at, 0F);
        h.assertTrue(m != null, "spawn " + key);
        if (!ai) m.setNoAi(true);
        return m;
    }

    /** 바닐라 난이도 배율 (mobAttack — 플레이어가 받는 몹 피해) */
    static double scaled(GameTestHelper h, double a) {
        Difficulty d = h.getLevel().getDifficulty();
        return switch (d) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(a / 2 + 1, a);
            case HARD -> a * 1.5;
            default -> a;
        };
    }

    /** 무적 시간 없이 (다음 피해가 바로 들어가게) */
    static void fresh(ServerPlayer sp) {
        sp.setInvulnerableTime(0);
        sp.damageCooldownTime = 0;
    }

    static void near(GameTestHelper h, double got, double want, double eps, String what) {
        h.assertTrue(Math.abs(got - want) <= eps, what + ": " + got + " (want " + want + ")");
    }

    private static void ok(String what) {
        LOG.info("[monster-test] OK {}", what);
    }

    // ------------------------------------------------------------ 싸움

    /**
     * 허수아비 (제자리): 옆에 서 있어도 맞기 전에는 공격하지 않음 → 플레이어가 때리면 싸움 → windup 뒤 설정 attack (3) 만큼 맞음 →
     * 다음 공격 동작 중 사거리 밖으로 나가면 빗나감 → 크리에이티브면 멈춤 · 서바이벌이면 다시. 이름표: 첫 줄 = 이름 (숨김), 다치면 체력 막대
     */
    @GameTest(environment = "mmorpg:monster", maxTicks = 300)
    public void attacksOnlyAfterHit(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 100);
        Monsters.Kind k = Monsters.kind("scarecrow");
        RpgMonster m = monster(h, "scarecrow", 1.5 + k.half() + 0.3 + 1.0, 2, 1.5, false); // 몸 가장자리 사이 1칸 (reach 1.5 안)
        float hp0 = sp.getHealth();
        h.runAfterDelay(70, () -> {
            h.assertTrue(Monsters.fightOf(m) == null, "no fight before a hit");
            h.assertTrue(sp.getHealth() == hp0, "not attacked before a hit: " + sp.getHealth() + " / " + hp0);
            // 이름표: 다치지 않으면 한 줄 "Lv.1 허수아비", 이름 (사망 메시지) 은 첫 줄, 글자 표시 엔티티가 몬스터에 탐 (앞에 그림)
            h.assertTrue(Monsters.labelOf(m).equals("§eLv.1 §f허수아비"), "label " + Monsters.labelOf(m));
            h.assertTrue(m.getDisplayName().getString().equals(BedrockText.toJava("§eLv.1 §f허수아비")) && !m.hasCustomName() && !m.isCustomNameVisible(),
                    "name (death message) = first line " + m.getDisplayName().getString());
            Display.TextDisplay label = Monsters.labelEntity(m);
            h.assertTrue(label != null && label.getVehicle() == m && label.entityTags().contains(Monsters.LABEL_TAG), "label rides the monster");
            h.assertTrue((label.getFlags() & Display.TextDisplay.FLAG_SEE_THROUGH) != 0, "label drawn on top (like a Bedrock name tag)");
            h.assertTrue(label.getText().getString().equals(BedrockText.toJava(Monsters.labelOf(m))), "label text " + label.getText().getString());
            fresh(sp);
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 2F);
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && f.player() == sp, "fight with the hitter");
            float before = sp.getHealth();
            h.runAfterDelay(2, () -> {
                // 다친 이름표: 아래 줄 체력 막대 20칸 (28/30 → 초록 19 · 회색 1)
                String want = "§eLv.1 §f허수아비\n§a" + "|".repeat(19) + "§8|";
                h.assertTrue(want.equals(Monsters.labelOf(m)), "hurt label " + Monsters.labelOf(m));
                h.assertTrue(label.getText().getString().equals(BedrockText.toJava(want)), "label entity updated " + label.getText().getString());
                h.assertTrue(m.getDisplayName().getString().equals(BedrockText.toJava("§eLv.1 §f허수아비")), "name stays the first line " + m.getDisplayName().getString());
            });
            h.runAfterDelay(20, () -> {
                h.assertTrue(f.hits() == 1 && "melee".equals(f.lastKind()), "melee hit " + f.hits() + " " + f.lastKind());
                near(h, before - sp.getHealth(), scaled(h, k.attack()), 1e-3, "hit = attack (difficulty " + h.getLevel().getDifficulty() + ")");
                float after = sp.getHealth();
                // 다음 공격 동작 (every 30틱) 이 시작된 뒤 사거리 + tolerance 밖으로 → 헛손질
                h.runAfterDelay(15, () -> {
                    h.assertTrue("melee".equals(f.windupKind()), "second windup started " + f.windupKind());
                    sp.snapTo(m.getX() + k.half() + 0.3 + 3.0, sp.getY(), sp.getZ(), 0F, 0F);
                    h.runAfterDelay(15, () -> {
                        h.assertTrue(f.misses() == 1 && f.hits() == 1 && sp.getHealth() == after, "missed out of reach " + f.misses() + " hp " + sp.getHealth());
                        // 크리에이티브: 잠깐 멈춤 (하던 동작은 끊김) → 서바이벌: 다시
                        sp.setGameMode(GameType.CREATIVE);
                        h.runAfterDelay(4, () -> {
                            h.assertTrue(f.paused() && f.windupKind() == null && Monsters.fightOf(m) == f, "paused in creative");
                            sp.setGameMode(GameType.SURVIVAL);
                            h.runAfterDelay(4, () -> {
                                h.assertTrue(!f.paused() && Monsters.fightOf(m) == f, "resumed");
                                ok("attacks only after a hit · windup hit = attack · miss out of reach · creative pause · labels");
                                m.discard();
                                h.assertTrue(label.isRemoved(), "label removed with the monster");
                                RpgGearTests.done(h, sp);
                            });
                        });
                    });
                });
            });
        });
    }

    /** 갑옷 · 방패 규칙: 몬스터 피해 (Monsters.monsterHit — 바닐라 mobAttack) 는 갑옷만큼 줄고, 웅크려 앞을 막은 방패는 RPG 방패 감소만큼 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 120)
    public void armorAndShieldRules(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        RpgMonster troll = monster(h, "troll", 4.5, 2, 1.5, false);
        Monsters.Kind k = Monsters.kind("troll");
        h.runAfterDelay(65, () -> {
            // 맨몸: attack 그대로 (난이도 배율)
            fresh(sp);
            float a = sp.getHealth();
            h.assertTrue(Monsters.monsterHit(troll, k, sp, k.attack()), "bare hit lands");
            near(h, a - sp.getHealth(), scaled(h, k.attack()), 1e-3, "bare damage");
            double bare = a - sp.getHealth();
            // 갑옷 (방어 12 — 시험 플레이어는 장비 틱이 돌지 않아 방어 속성을 바로): 바닐라 방어 공식만큼 덜 아픔
            sp.getAttribute(Attributes.ARMOR).setBaseValue(12);
            h.runAfterDelay(1, () -> {
                fresh(sp);
                float b = sp.getHealth();
                h.assertTrue(sp.getArmorValue() == 12, "armor on " + sp.getArmorValue());
                h.assertTrue(Monsters.monsterHit(troll, k, sp, k.attack()), "armored hit lands");
                double armored = b - sp.getHealth();
                h.assertTrue(armored < bare - 0.5 && armored > 0, "armor reduces: " + armored + " < " + bare);
                sp.getAttribute(Attributes.ARMOR).setBaseValue(0);
                h.runAfterDelay(3, () -> shieldPart(h, sp, troll, k));
            });
        });
    }

    private static void shieldPart(GameTestHelper h, ServerPlayer sp, RpgMonster troll, Monsters.Kind k) {
        // 방패 (오른손) · 웅크려 몬스터 쪽을 봄 → RPG 방패 감소 (막기 비율)
        Weapon shield = Weapons.weaponFrom("shield", 0, "common");
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(shield.id())));
        for (Weapons.Req r : shield.reqs()) RpgPlayers.setStat(sp, r.stat(), Math.max(300, (int) Math.ceil(r.value())));
        sp.setShiftKeyDown(true);
        float[] rot = Kit.lookRotation(sp.getEyePosition(), troll.getEyePosition());
        sp.snapTo(sp.getX(), sp.getY(), sp.getZ(), rot[0], rot[1]);
        double reduce = RpgCombat.shieldReduction(sp, troll.damageSources().mobAttack(troll));
        h.assertTrue(reduce > 0, "shield reduces " + reduce);
        fresh(sp);
        float c = sp.getHealth();
        h.assertTrue(Monsters.monsterHit(troll, k, sp, k.attack()), "blocked hit lands");
        near(h, c - sp.getHealth(), scaled(h, k.attack()) * (1 - reduce), 1e-2, "shield block");
        ok("monster damage: bare = attack, armor reduces, shield block = RPG shield reduction " + reduce);
        sp.setShiftKeyDown(false);
        troll.discard();
        RpgGearTests.done(h, sp);
    }

    /**
     * 달려들기 (굴론 — 제자리라 움직임은 사진첩에서): 사거리 밖 6.5칸 안에서 맞으면 뛰어들며 공격 동작 (사거리 밖이면 헛손질로 끝).
     * 속박 (구속 VII) 이면 달려들기 없음, 기절 (구속 + 나약함) 이면 붙어 있어도 공격 없음
     */
    @GameTest(environment = "mmorpg:monster", maxTicks = 200)
    public void lungeAndRootStun(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 200);
        Monsters.Kind k = Monsters.kind("gulon");
        RpgMonster runner = monster(h, "gulon", 1.5 + k.half() + 0.3 + 4.3, 2, 1.5, false);
        RpgMonster rooted = monster(h, "gulon", 1.5, 2, 1.5 + k.half() + 0.3 + 4.3, false);
        RpgMonster stunned = monster(h, "gulon", 1.5 - k.half() - 0.3 - 1.0, 2, 1.5, false);
        rooted.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 6, false, false));
        stunned.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 10, false, false));
        stunned.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 4, false, false));
        for (RpgMonster m : List.of(runner, rooted, stunned)) m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
        h.assertTrue(Monsters.fightOf(runner) != null && Monsters.fightOf(rooted) != null && Monsters.fightOf(stunned) != null, "three fights");
        h.assertTrue(Monsters.rooted(rooted) && !Monsters.stunned(rooted) && Monsters.stunned(stunned), "root / stun states");
        h.runAfterDelay(40, () -> {
            Monsters.Fight r = Monsters.fightOf(rooted);
            Monsters.Fight s = Monsters.fightOf(stunned);
            h.assertTrue(r.lastKind() == null && r.windupKind() == null, "rooted: no lunge " + r.lastKind() + " " + r.windupKind());
            h.assertTrue(s.hits() == 0 && s.misses() == 0 && s.windupKind() == null, "stunned: no attack");
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(runner);
            h.assertTrue(f != null && "lunge".equals(f.lastKind()), "lunge resolved " + (f == null ? null : f.lastKind()));
            h.assertTrue(f.nextLunge() > 0, "lunge cooldown");
            ok("lunge (resolved " + f.lastKind() + ", hits " + f.hits() + ") · rooted no lunge · stunned no attack");
            for (RpgMonster m : List.of(runner, rooted, stunned)) m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    /**
     * 원거리 반격 (굴론 제자리): 플레이어가 4칸 위 (기둥 위) 2칸 옆에서 때리면 닿지도 달려들지도 못함 → 발밑 고리 → windup + 6 틱 뒤 맞음.
     * 다시 때리고 다음 반격 동작 중 고리 밖으로 나가면 피함
     */
    @GameTest(environment = "mmorpg:monster", maxTicks = 300)
    public void counterOnPillar(GameTestHelper h) {
        Monsters.Kind k = Monsters.kind("gulon");
        RpgMonster m = monster(h, "gulon", 2.5, 2, 1.5, false);
        ServerPlayer sp = player(h, 4.5, 6, 1.5, 200);
        h.runAfterDelay(65, () -> {
            fresh(sp);
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null, "fight");
            float before = sp.getHealth();
            h.runAfterDelay(10, () -> {
                h.assertTrue("counter".equals(f.windupKind()) && f.counterSpot() != null && f.counterSpot().distanceTo(sp.position()) < 1e-6,
                        "counter ring under the player " + f.windupKind());
            });
            h.runAfterDelay(26, () -> {
                h.assertTrue(f.hits() == 1 && "counter".equals(f.lastKind()), "counter hit " + f.hits() + " " + f.lastKind());
                near(h, before - sp.getHealth(), scaled(h, k.attack()), 1e-3, "counter damage");
                float after = sp.getHealth();
                fresh(sp);
                m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F); // 방금 때림 (반격 창 3초)
                h.runAfterDelay(17, () -> {
                    h.assertTrue("counter".equals(f.windupKind()), "second counter " + f.windupKind());
                    sp.snapTo(sp.getX(), sp.getY(), sp.getZ() + 2.5, 0F, 0F); // 고리 (반지름 1.3) 밖으로
                    h.runAfterDelay(20, () -> {
                        h.assertTrue(f.misses() == 1 && f.hits() == 1 && sp.getHealth() == after, "dodged the counter " + f.misses() + " hp " + sp.getHealth());
                        ok("ranged counter: ring → hit, dodged by leaving the ring");
                        m.discard();
                        RpgGearTests.done(h, sp);
                    });
                });
            });
        });
    }

    /** 특성: 스켈야스크림슬리 껍질 (플레이어 피해만 -20%), 닉스 구속 II · 드라우그 구속 I 2초, 드라우그 언데드 (성기사 룬 · 햇빛에 타지 않음), 무리 태그 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 120)
    public void traits(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        RpgMonster shell = monster(h, "skeljaskrimsli", 3.5, 2, 1.5, false);
        RpgMonster nix = monster(h, "nix", 3.5, 2, 3.5, false);
        RpgMonster draugr = monster(h, "draugr", 1.5, 2, 3.5, false);
        shell.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 10F);
        near(h, shell.getHealth(), 70 - 8, 1e-3, "shell: player damage -20%");
        shell.damageCooldownTime = 0;
        shell.setInvulnerableTime(0);
        shell.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 10F);
        near(h, shell.getHealth(), 62 - 10, 1e-3, "shell: other damage as is");
        h.assertTrue(RuneKit.isUndead(draugr) && !RuneKit.isUndead(nix), "draugr is undead (paladin rune 1.5x)");
        h.assertTrue(!draugr.getType().builtInRegistryHolder().is(EntityTypeTags.BURN_IN_DAYLIGHT), "draugr does not burn in daylight");
        h.assertTrue(Kit.hasFamily(nix, "rpg_monster") && Kit.hasFamily(nix, "nix") && !Kit.hasFamily(nix, "monster"), "families as entity tags");
        // 체력 막대 20칸: 절반 넘게 초록, 1/4 넘게 노랑, 그 아래 빨강 (살아 있으면 최소 1칸)
        Monsters.Kind sk = Monsters.kind("scarecrow");
        h.assertTrue(Monsters.monsterLabel(sk, 30, 30).equals("§eLv.1 §f허수아비"), "full hp: one line");
        h.assertTrue(Monsters.monsterLabel(sk, 16, 30).endsWith("\n§a" + "|".repeat(11) + "§8" + "|".repeat(9)), "green " + Monsters.monsterLabel(sk, 16, 30));
        h.assertTrue(Monsters.monsterLabel(sk, 10, 30).endsWith("\n§e" + "|".repeat(7) + "§8" + "|".repeat(13)), "yellow " + Monsters.monsterLabel(sk, 10, 30));
        h.assertTrue(Monsters.monsterLabel(sk, 5, 30).endsWith("\n§c" + "|".repeat(3) + "§8" + "|".repeat(17)), "red " + Monsters.monsterLabel(sk, 5, 30));
        h.assertTrue(Monsters.monsterLabel(sk, 0.1, 30).endsWith("\n§c|§8" + "|".repeat(19)) && Monsters.monsterLabel(sk, 0, 30).endsWith("\n§c§8" + "|".repeat(20)),
                "min 1 bar while alive, 0 when dead");
        near(h, Monsters.damageTextHeight(sk), Math.min(1.92 * 0.62, 0.92), 1e-6, "damage text height (scarecrow)");
        near(h, Monsters.damageTextHeight(Monsters.kind("troll")), 3.57 * 0.62, 1e-6, "damage text height (troll)");
        h.runAfterDelay(65, () -> {
            fresh(sp);
            h.assertTrue(Monsters.monsterHit(nix, Monsters.kind("nix"), sp, 1), "nix hit");
            MobEffectInstance slow = sp.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() == 1 && Math.abs(slow.getDuration() - 40) <= 1, "nix: slowness II 2s " + slow);
            sp.removeEffect(MobEffects.SLOWNESS);
            fresh(sp);
            h.assertTrue(Monsters.monsterHit(draugr, Monsters.kind("draugr"), sp, 1), "draugr hit");
            slow = sp.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() == 0 && Math.abs(slow.getDuration() - 40) <= 1, "draugr: slowness I 2s " + slow);
            // 무적 시간에 막힌 피해는 효과 없음
            sp.removeEffect(MobEffects.SLOWNESS);
            h.assertFalse(Monsters.monsterHit(nix, Monsters.kind("nix"), sp, 0.5), "blocked by invulnerability");
            h.assertTrue(sp.getEffect(MobEffects.SLOWNESS) == null, "no slowness when blocked");
            ok("shell -20% · nix/draugr slowness · draugr undead · families");
            for (RpgMonster m : List.of(shell, nix, draugr)) m.discard();
            RpgGearTests.done(h, sp);
        });
    }

    /**
     * 나약함 한 단계마다 몬스터 피해 -20% (허수아비 3 → 나약함 I 2.4), 룬의 짧은 은신 (투명 5초 이하) 인 사람에게는 달려들기 없음 (긴 투명은 그대로),
     * 트롤 내려치기: 맞은 사람 둘레 1.8칸의 다른 플레이어도 절반 피해
     */
    @GameTest(environment = "mmorpg:monster", maxTicks = 200)
    public void weaknessHiddenShockwave(GameTestHelper h) {
        ServerPlayer weakTarget = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind sk = Monsters.kind("scarecrow");
        RpgMonster weak = monster(h, "scarecrow", 1.5 + sk.half() + 0.3 + 1.0, 2, 1.5, false);
        weak.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, false));
        ServerPlayer hidden = player(h, 1.5, 2, 5.5, 300);
        ServerPlayer seen = player(h, 1.5, 2, 9.5, 300);
        hidden.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0, false, false)); // 연막 · 월광 암살 (5초 이하)
        seen.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6000, 0, false, false)); // 투명화 물약 (길다)
        Monsters.Kind gk = Monsters.kind("gulon");
        RpgMonster g1 = monster(h, "gulon", 1.5 + gk.half() + 0.3 + 4.3, 2, 5.5, false);
        RpgMonster g2 = monster(h, "gulon", 1.5 + gk.half() + 0.3 + 4.3, 2, 9.5, false);
        Monsters.Kind tk = Monsters.kind("troll");
        ServerPlayer a = player(h, 9.0, 2, 1.5, 300);
        ServerPlayer b = player(h, 9.0, 2, 2.7, 300); // a 에서 1.2칸 (1.8 안)
        RpgMonster troll = monster(h, "troll", 9.0 + tk.half() + 0.3 + 1.5, 2, 1.5, false);
        h.runAfterDelay(66, () -> {
            for (ServerPlayer p : List.of(weakTarget, hidden, seen, a, b)) fresh(p);
            weak.hurtServer(h.getLevel(), weakTarget.damageSources().playerAttack(weakTarget), 1F);
            g1.hurtServer(h.getLevel(), hidden.damageSources().playerAttack(hidden), 1F);
            g2.hurtServer(h.getLevel(), seen.damageSources().playerAttack(seen), 1F);
            troll.hurtServer(h.getLevel(), a.damageSources().playerAttack(a), 1F);
            float w0 = weakTarget.getHealth();
            float a0 = a.getHealth();
            float b0 = b.getHealth();
            h.runAfterDelay(26, () -> {
                Monsters.Fight fw = Monsters.fightOf(weak);
                h.assertTrue(fw != null && fw.hits() == 1, "weak scarecrow hit " + (fw == null ? null : fw.hits()));
                near(h, w0 - weakTarget.getHealth(), scaled(h, sk.attack() * 0.8), 1e-3, "weakness I: damage -20%");
                Monsters.Fight f1 = Monsters.fightOf(g1);
                Monsters.Fight f2 = Monsters.fightOf(g2);
                h.assertTrue(f1 != null && f1.lastKind() == null && f1.windupKind() == null, "no lunge at a short-invisible player");
                h.assertTrue(f2 != null && ("lunge".equals(f2.lastKind()) || "lunge".equals(f2.windupKind())), "lunge at a long-invisible player " + f2.lastKind());
                Monsters.Fight ft = Monsters.fightOf(troll);
                h.assertTrue(ft != null && ft.hits() == 1, "troll hit a " + (ft == null ? null : ft.hits()));
                double hitA = a0 - a.getHealth();
                double hitB = b0 - b.getHealth();
                near(h, hitA, scaled(h, tk.attack()), 1e-3, "troll hit");
                near(h, hitB, scaled(h, tk.attack() * 0.5), 1e-3, "shockwave: half to the player beside");
                ok("weakness -20% · short invisibility blocks lunge · troll shockwave half damage");
                for (RpgMonster m : List.of(weak, g1, g2, troll)) m.discard();
                for (ServerPlayer p : List.of(weakTarget, hidden, seen, b)) h.getLevel().getServer().getPlayerList().remove(p);
                RpgGearTests.done(h, a);
            });
        });
    }

    /** 트롤 재생: 싸우는 중에도 3초 안 맞으면 1초마다 3% · 회복: 싸움 없이 6초 안 맞으면 1초마다 10% · 다친 채 불러온 몬스터는 바로 회복 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 220)
    public void regeneration(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 100);
        RpgMonster troll = monster(h, "troll", 11.5, 2, 1.5, false); // 10칸 — 싸움은 이어지되 (leash 24) 닿지 않음
        // 시험 구조물 (8칸) 밖이라 트롤 청크가 엔티티 틱을 안 돌 수 있음 (시험 자리는 실행마다 다름) — 이름표는 몬스터 틱에 맞춰지므로 붙잡음
        String hold = Ticking.holdArea(h.getLevel(), troll.position(), 4, "t");
        troll.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
        troll.setHealth(100F);
        h.assertTrue(Monsters.fightOf(troll) != null, "troll fight");
        RpgMonster crow = monster(h, "scarecrow", 1.5, 2, 6.5, false);
        crow.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 20F); // 싸움 없이 10/30
        h.assertTrue(Monsters.fightOf(crow) == null && Monsters.isHurt(crow), "scarecrow hurt, no fight");
        RpgMonster loaded = monster(h, "scarecrow", 4.5, 2, 6.5, false);
        loaded.setHealth(10F); // 다친 채 불러온 것처럼 (첫 틱에 회복 시작)
        h.runAfterDelay(30, () -> {
            h.assertTrue(loaded.getHealth() >= 13F - 1e-3, "reloaded hurt monster regenerates at once " + loaded.getHealth());
            near(h, troll.getHealth(), 100, 1e-3, "troll: no regen before 3s");
            near(h, crow.getHealth(), 10, 1e-3, "scarecrow: no regen before 6s");
        });
        h.runAfterDelay(100, () -> {
            Monsters.Fight f = Monsters.fightOf(troll);
            h.assertTrue(f != null, "still fighting");
            double gained = troll.getHealth() - 100;
            double step = 230 * 0.03;
            h.assertTrue(gained >= step - 1e-3 && Math.abs(gained / step - Math.round(gained / step)) < 1e-3, "troll regen 3%/s while fighting: +" + gained);
            // 이름표는 몬스터 틱에 맞춰짐 (회복 바로 뒤 한 틱은 앞의 체력일 수 있음)
            String now = Monsters.monsterLabel(Monsters.kind("troll"), troll.getHealth(), 230);
            String prev = Monsters.monsterLabel(Monsters.kind("troll"), troll.getHealth() - step, 230);
            h.assertTrue(Monsters.labelOf(troll).equals(now) || Monsters.labelOf(troll).equals(prev), "bar follows regen " + Monsters.labelOf(troll));
        });
        h.runAfterDelay(160, () -> {
            h.assertTrue(crow.getHealth() >= 13F - 1e-3, "scarecrow regen 10%/s after 6s " + crow.getHealth());
            ok("troll regen while fighting · regen after the fight · reloaded hurt monster");
            for (RpgMonster m : List.of(troll, crow, loaded)) m.discard();
            Ticking.releaseArea(hold);
            RpgGearTests.done(h, sp);
        });
    }

    /** 도발: 다른 사람이 때려도 도발한 사람을 노림 → 끝나면 다시 마지막으로 때린 사람. 크리에이티브는 도발할 수 없음 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 60)
    public void taunt(GameTestHelper h) {
        ServerPlayer a = player(h, 1.5, 2, 1.5, 0);
        ServerPlayer b = player(h, 1.5, 2, 3.5, 0);
        RpgMonster m = monster(h, "troll", 11.5, 2, 2.5, false); // 10칸 — 달려들기 · 반격 없이 싸움만
        m.hurtServer(h.getLevel(), a.damageSources().playerAttack(a), 1F);
        Monsters.Fight f = Monsters.fightOf(m);
        h.assertTrue(f != null && f.player() == a, "fighting a");
        h.assertTrue(Monsters.tauntMonster(m, b, 10) && f.player() == b && b.getUUID().equals(f.tauntId()), "taunted by b");
        m.damageCooldownTime = 0;
        m.setInvulnerableTime(0);
        m.hurtServer(h.getLevel(), a.damageSources().playerAttack(a), 1F);
        h.assertTrue(f.player() == b, "taunt keeps b while a hits");
        b.setGameMode(GameType.CREATIVE);
        h.assertFalse(Monsters.tauntMonster(m, b, 10), "creative cannot taunt");
        b.setGameMode(GameType.SURVIVAL);
        h.runAfterDelay(14, () -> {
            h.assertTrue(f.tauntId() == null, "taunt over");
            m.damageCooldownTime = 0;
            m.setInvulnerableTime(0);
            m.hurtServer(h.getLevel(), a.damageSources().playerAttack(a), 1F);
            h.assertTrue(f.player() == a, "last hitter again after the taunt");
            ok("taunt");
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(b);
            RpgGearTests.done(h, a);
        });
    }

    // ------------------------------------------------------------ 죽음 (lootOnDeath)

    /** 죽으면: 잡은 사람에게 경험치 (구슬 없이) · 전리품 (50%) · 에메랄드 (확률 — Loot.rand 로 정함) · 퀘스트 처치 수, 최고 레벨이 xpUntil 이상이면 경험치 0 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 40)
    public void deathXpLootQuest(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 0);
        DoubleSupplier prevRand = Loot.rand;
        Quest q = new Quest();
        q.giver = "test_monster";
        q.title = "굴론 사냥";
        q.type = "kill";
        q.target = "rpg:gulon";
        q.count = 3;
        Quest saved = Quests.saveQuest(q);
        Quests.stateOf(sp).put(saved.id, new Quest.Entry("a", 0, 0));
        try {
            Monsters.Kind k = Monsters.kind("gulon");
            // 1. 잡음 (Loot.rand = 0 → 전리품 · 에메랄드 떨어짐)
            Loot.rand = () -> 0.0;
            RpgMonster g1 = monster(h, "gulon", 3.5, 2, 1.5, false);
            int xp0 = sp.totalExperience;
            g1.hurtServer(level, sp.damageSources().playerAttack(sp), 1000F);
            h.assertTrue(g1.isDeadOrDying(), "gulon dead");
            h.assertTrue(sp.totalExperience - xp0 == k.xp(), "xp +" + k.xp() + " (got " + (sp.totalExperience - xp0) + ")");
            AABB around = new AABB(g1.position(), g1.position()).inflate(3);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, around);
            long loot = items.stream().filter(e -> e.getItem().is(GearItems.byId("rpg:loot_gulon"))).count();
            long emerald = items.stream().filter(e -> e.getItem().is(Items.EMERALD)).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(loot == 1 && emerald == (int) k.emerald()[1], "dropped loot " + loot + " · emerald " + emerald);
            h.assertTrue(Quests.stateOf(sp).get(saved.id).n == 1, "quest kill counted " + Quests.stateOf(sp).get(saved.id).n);
            for (ItemEntity e : items) e.discard();
            // 2. 확률에 걸리지 않음 (0.99) → 전리품 · 에메랄드 없음, 경험치는 그대로
            Loot.rand = () -> 0.99;
            RpgMonster g2 = monster(h, "gulon", 3.5, 2, 3.5, false);
            int xp1 = sp.totalExperience;
            g2.hurtServer(level, sp.damageSources().playerAttack(sp), 1000F);
            List<ItemEntity> none = level.getEntitiesOfClass(ItemEntity.class, new AABB(g2.position(), g2.position()).inflate(3));
            h.assertTrue(none.isEmpty() && sp.totalExperience - xp1 == k.xp(), "no drops, xp again " + none.size());
            // 3. 경험치 제한: 최고 레벨 15 (= 굴론 xpUntil) → 0 (처치 수는 그대로 셈)
            sp.setExperienceLevels(k.xpUntil());
            h.assertTrue(Loot.xpCapped(sp, "gulon") && !Loot.xpCapped(sp, "skeljaskrimsli"), "capped at xpUntil");
            RpgMonster g3 = monster(h, "gulon", 1.5, 2, 3.5, false);
            int xp2 = sp.totalExperience;
            g3.hurtServer(level, sp.damageSources().playerAttack(sp), 1000F);
            h.assertTrue(sp.totalExperience == xp2, "no xp when capped " + (sp.totalExperience - xp2));
            h.assertTrue(Quests.stateOf(sp).get(saved.id).n == 3, "kills still counted " + Quests.stateOf(sp).get(saved.id).n);
            // 4. 플레이어가 잡지 않은 죽음 (때린 적 없음) → 경험치 · 전리품 없음, 에메랄드 (전리품 표) 는 떨어짐
            Loot.rand = () -> 0.0;
            RpgMonster g4 = monster(h, "gulon", 4.5, 2, 4.5, false);
            int xp3 = sp.totalExperience;
            g4.hurtServer(level, level.damageSources().generic(), 1000F);
            List<ItemEntity> after = level.getEntitiesOfClass(ItemEntity.class, new AABB(g4.position(), g4.position()).inflate(3));
            h.assertTrue(sp.totalExperience == xp3 && after.stream().noneMatch(e -> e.getItem().is(GearItems.byId("rpg:loot_gulon")))
                    && after.stream().anyMatch(e -> e.getItem().is(Items.EMERALD)), "no credit: emerald only");
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(sp.position(), sp.position()).inflate(8))) e.discard();
            ok("death: xp · loot · emerald · quest kill count · xp cap · no credit");
        } finally {
            Loot.rand = prevRand;
            Quests.deleteQuest(saved.id);
        }
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 사냥터 · /mob

    private static final int ZX = 70_000;
    private static final int ZY = 150;
    private static final int ZZ = 70_000;

    /** 사냥터 (멀리 하늘의 돌 바닥): 만들기 (기록 JSON · 태그 · 길 안내) · 하나 잡히면 respawn 뒤 채움 · 넘치면 먼 것부터 치움 · /mob · /mob clear 는 사냥터 몬스터를 남김 · 지우기 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 600)
    public void huntZoneFillsAndTrims(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = new BlockPos(ZX, ZY, ZZ);
        String hold = Ticking.holdArea(level, Vec3.atCenterOf(c), 24, "t");
        for (int cx = Math.floorDiv(ZX - 24, 16); cx <= Math.floorDiv(ZX + 24, 16); cx++) {
            for (int cz = Math.floorDiv(ZZ - 24, 16); cz <= Math.floorDiv(ZZ + 24, 16); cz++) level.getChunk(cx, cz);
        }
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                level.setBlock(c.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int dy = 0; dy <= 3; dy++) level.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        ServerPlayer sp = RpgGearTests.player(h);
        sp.snapTo(ZX + 0.5, ZY, ZZ + 0.5, 0F, 0F);
        int prevRespawn = HuntZones.respawn;
        HuntZones.respawn = 20;
        int[] phase = {0};
        long[] at = {0};
        HuntZones.Zone[] zone = {null};
        Entity[] extra = {null};
        h.succeedWhen(() -> {
            long now = h.getTick();
            switch (phase[0]) {
                case 0 -> {
                    if (now < 20) throw h.assertionException("loading");
                    String msg = HuntZones.addZone(sp, "gulon", 2);
                    h.assertTrue(msg.startsWith("§6[사냥터] §fz") && msg.contains("Lv.8 굴론 2마리"), "add message " + msg);
                    List<HuntZones.Zone> list = HuntZones.zoneList();
                    zone[0] = list.get(list.size() - 1);
                    HuntZones.Zone z = zone[0];
                    h.assertTrue(z.kind().equals("gulon") && z.count() == 2 && z.x() == ZX && z.y() == ZY && z.z() == ZZ && z.dim().equals("minecraft:overworld"), "zone " + z);
                    h.assertTrue(members(level, z).size() == 2, "spawned 2");
                    Object saved = Json.parse(Props.world(level.getServer(), "rpg:hunt_zones"));
                    h.assertTrue(saved instanceof List<?> l && l.stream().anyMatch(o -> o instanceof java.util.Map<?, ?> m && z.id().equals(m.get("id"))
                            && Double.valueOf(2).equals(((Number) m.get("count")).doubleValue())), "saved JSON " + saved);
                    h.assertTrue(mmorpg.fabric.nav.Nav.zones.get().stream().anyMatch(n -> n.id().equals(z.id())), "nav zone hook");
                    for (Entity e : members(level, z)) ((RpgMonster) e).setNoAi(true);
                    members(level, z).get(0).discard(); // 하나 잡힘
                    phase[0] = 1;
                    at[0] = now;
                    throw h.assertionException("refilling");
                }
                case 1 -> {
                    if (members(level, zone[0]).size() < 2) {
                        if (now - at[0] > 120) throw h.assertionException("not refilled after " + (now - at[0]));
                        throw h.assertionException("refilling");
                    }
                    h.assertTrue(now - at[0] >= HuntZones.respawn, "refilled after respawn ticks " + (now - at[0]));
                    for (Entity e : members(level, zone[0])) ((RpgMonster) e).setNoAi(true);
                    // 넘침: 사냥터 태그가 붙은 굴론을 하나 더 (가운데에서 9칸 — 사냥터 몬스터는 6.7칸 안) → 싸우지 않는 것 중 먼 것부터
                    RpgMonster x = Monsters.spawnAt(level, "gulon", new Vec3(ZX + 9.5, ZY, ZZ + 0.5), 0F);
                    x.addTag(zone[0].tag());
                    x.setNoAi(true);
                    extra[0] = x;
                    phase[0] = 2;
                    at[0] = now;
                    throw h.assertionException("trimming");
                }
                case 2 -> {
                    if (!extra[0].isRemoved()) throw h.assertionException("extra not trimmed yet");
                    h.assertTrue(members(level, zone[0]).size() == 2, "trimmed to 2");
                    // /mob gulon 2 (눈앞 4칸) → /mob clear: 사냥터 몬스터는 남김
                    List<RpgMonster> spawned = Monsters.spawnMonsters(sp, "gulon", 2);
                    h.assertTrue(spawned.size() == 2 && spawned.stream().allMatch(m -> m.position().distanceTo(sp.position()) < 6), "/mob spawned in front");
                    int cleared = Monsters.clearMonsters(sp, 12);
                    h.assertTrue(cleared == 2 && spawned.stream().allMatch(Entity::isRemoved), "/mob clear removed the plain monsters " + cleared);
                    int kept = members(level, zone[0]).size();
                    int near = HuntZones.zoneMonstersNear(sp, 12);
                    if (kept != 2 || near != 2) {
                        // 무엇이 남았는지 (사냥터 몬스터 수 · 둘레 RPG 몬스터의 자리 · 태그)
                        StringBuilder sb = new StringBuilder();
                        for (RpgMonster m : level.getEntitiesOfClass(RpgMonster.class, new AABB(sp.position(), sp.position()).inflate(16))) {
                            sb.append(String.format(java.util.Locale.ROOT, " [%.1f %.1f %.1f %s %s]", m.getX() - sp.getX(), m.getY() - sp.getY(), m.getZ() - sp.getZ(),
                                    m.isRemoved() ? "removed" : "live", m.entityTags()));
                        }
                        LOG.warn("[monster-test] zone check: members {} near {} player {} monsters{}", kept, near, sp.position(), sb);
                    }
                    h.assertTrue(kept == 2 && near == 2, "zone monsters kept (members " + kept + ", near " + near + ")");
                    String list = HuntZones.zoneListText(level.getServer());
                    h.assertTrue(list.contains(zone[0].id() + " §fLv.8 굴론 ×2") && list.contains("(지금 2마리)"), "list " + list);
                    String msg = HuntZones.removeNearestZone(sp, 16);
                    h.assertTrue(msg.contains(zone[0].id() + " (굴론) 를 지우고 몬스터 2마리를 치웠습니다"), "remove " + msg);
                    h.assertTrue(HuntZones.zoneList().stream().noneMatch(z -> z.id().equals(zone[0].id())) && members(level, zone[0]).isEmpty(), "zone gone");
                    ok("hunt zone: add · refill after respawn · trim farthest · /mob clear keeps zone monsters · list · remove");
                    HuntZones.respawn = prevRespawn;
                    Ticking.releaseArea(hold);
                    level.getServer().getPlayerList().remove(sp);
                }
                default -> {
                }
            }
        });
    }

    private static List<Entity> members(ServerLevel level, HuntZones.Zone z) {
        return new java.util.ArrayList<>(level.getEntities(mmorpg.fabric.mob.RpgEntities.type("rpg:" + z.kind()), e -> e.entityTags().contains(z.tag()) && !e.isRemoved()));
    }
}
