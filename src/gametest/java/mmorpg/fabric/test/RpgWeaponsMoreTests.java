package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.Combat;
import mmorpg.core.Grade;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Transcend;
import mmorpg.fabric.smith.Smith;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 12단계 새 무기 게임 시험 (서버): 창 · 낫 · 전투 망치 · 성스러운 홀 × 고귀 · 영웅 · 초월 24개의 등록 · 조합 · 조작 줄 · 창의 사거리 속성,
 * 기본 공격 특징 (낫 넓게 베기 · 망치 내려찍기 — 다른 적에게 준 피해의 비율, 앞 · 둘레 밖은 안 맞음 · 홀 회복), 스킬 12개마다 들어간 피해 (배율 합 —
 * 몹 무적 시간을 피해 모두) · 상태 (시간 정지 · 구속 · 보호막 · 회복), 대장장이 제작 (고귀 쪽 — 전리품 + 에메랄드만), 정령왕 강림 (합 ×32 · 6돌 ×50).
 * 사거리가 길거나 넓은 스킬은 시험마다 시험 환경을 따로 (mmorpg:w_…) — 같은 묶음에서 함께 도는 다른 시험의 과녁을 맞히지 않게.
 */
public final class RpgWeaponsMoreTests {
    static final String ENV = "mmorpg:weapons";

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
        RpgCombat.forceCrit = null;
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 쓰러지지 않는 과녁 허스크 (불에 타지 않음 — 받은 피해는 TestDummies.dealt). yaw 180 = 나를 봄 */
    private static Husk dummy(GameTestHelper h, double x, double y, double z) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) x, (float) y, (float) z));
        m.setYRot(180F);
        m.setYHeadRot(180F);
        m.setYBodyRot(180F);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
        return m;
    }

    private static Weapon weapon(String type, int level, String grade) {
        return Weapons.weaponFrom(type, level, grade);
    }

    private static void hold(ServerPlayer sp, Weapon w) {
        if (sp.experienceLevel < w.level()) {
            sp.setExperienceLevels(w.level());
            RpgPlayers.checkLevelUp(sp);
        }
        for (String s : List.of("str", "dex", "int", "vit")) RpgPlayers.setStat(sp, s, 150);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(w.id())));
    }

    private static double base(ServerPlayer sp, Weapon w) {
        return w.damage() * Combat.powerMultiplier(RpgData.of(sp).effective(), w, 0);
    }

    private static boolean hitsMatch(double dealt, double b, double c, double... mults) {
        return RpgHeroSkillTests.hitsMatch(dealt, b, c, mults);
    }

    private static double[] times(int n, double m) {
        double[] out = new double[n];
        java.util.Arrays.fill(out, m);
        return out;
    }

    private static double[] concat(double[] a, double... b) {
        double[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ------------------------------------------------------------ 등록 · 조작

    @GameTest(environment = ENV, maxTicks = 20)
    public void newWeaponsRegistered(GameTestHelper h) {
        ServerPlayer sp = player(h);
        int n = 0;
        for (String type : Weapons.moreTypes()) {
            for (Grade g : Rules.get().grades()) {
                for (double lv : Rules.get().numbers("ALL_LEVELS")) {
                    boolean valid = Weapons.isValidWeapon(lv, g.id(), type);
                    RpgWeaponItem item = RpgItems.byId(Weapons.weaponId(type, lv, g.id()));
                    h.assertTrue((item != null) == valid, type + " " + g.id() + " " + lv + " registered " + valid);
                    if (item == null) continue;
                    n++;
                    Weapon w = item.weapon();
                    h.assertTrue(Combat.isMeleeWeapon(w), w.id() + " melee");
                    if (w.skills() != null) for (Obj k : w.skills()) h.assertTrue(Transcend.has(k.str("id")), "skill registered " + k.str("id"));
                    if (w.skills() != null && w.skills().size() == 2) {
                        h.assertTrue(Weapons.weaponInfo(w).stream().anyMatch(l -> RpgWeaponItem.javaControls(l).endsWith("우클릭: 스킬1, 웅크리고 우클릭: 스킬2")),
                                w.id() + " melee controls");
                        h.assertFalse(Transcend.holdable(w), w.id() + " no long press (melee)");
                    }
                }
            }
        }
        h.assertTrue(n == 24, "24 new weapons, got " + n);
        // 창: 몹을 칠 수 있는 거리 +1 (아이템 속성 — 손에 들면)
        ItemStack lance = new ItemStack(RpgItems.byId("rpg:lance_l40_transcend"));
        ItemAttributeModifiers mods = lance.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        h.assertTrue(mods.modifiers().stream().anyMatch(e -> e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE) && Math.abs(e.modifier().amount() - 1) < 1e-9),
                "lance reach +1 " + mods.modifiers());
        ItemStack hammer = new ItemStack(RpgItems.byId("rpg:hammer_l40_transcend"));
        h.assertTrue(hammer.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .noneMatch(e -> e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)), "hammer keeps the normal reach");
        done(h, sp);
    }

    // ------------------------------------------------------------ 기본 공격 특징

    @GameTest(environment = ENV, maxTicks = 30)
    public void scytheSweepHitsOthersInFront(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("scythe", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 맞힌 적 (앞 2칸)
        Husk b = dummy(h, 3.0, 2, 3.5); // 앞 비스듬히 (약 37°) — 넓게 베기
        Husk c = dummy(h, 1.5, 2, 0.1); // 뒤 — 안 맞음
        RpgCombat.forceCrit = false;
        h.runAfterDelay(2, () -> {
            sp.attack(a);
            Obj sweep = w.typeDef().obj("sweep");
            double main = TestDummies.dealt(a);
            h.assertTrue(main > 0, "main hit " + main);
            h.assertTrue(Math.abs(TestDummies.dealt(b) - main * sweep.num("ratio")) < 1e-3, "sweep " + TestDummies.dealt(b) + " vs " + main * sweep.num("ratio"));
            h.assertTrue(TestDummies.dealt(c) == 0, "behind not hit " + TestDummies.dealt(c));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 30)
    public void hammerQuakeHitsAroundTheTarget(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("hammer", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5);
        Husk b = dummy(h, 3.0, 2, 4.0); // 맞힌 적 둘레 1.6칸
        Husk c = dummy(h, 1.5, 2, 6.8); // 3.3칸 — 안 맞음
        RpgCombat.forceCrit = false;
        h.runAfterDelay(2, () -> {
            sp.attack(a);
            Obj quake = w.typeDef().obj("quake");
            double main = TestDummies.dealt(a);
            h.assertTrue(main > 0, "main hit " + main);
            h.assertTrue(Math.abs(TestDummies.dealt(b) - main * quake.num("ratio")) < 1e-3, "quake " + TestDummies.dealt(b) + " vs " + main * quake.num("ratio"));
            h.assertTrue(TestDummies.dealt(c) == 0, "far not hit " + TestDummies.dealt(c));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 30)
    public void scepterHealsOnHit(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("scepter", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5);
        sp.getFoodData().setFoodLevel(17);
        sp.getFoodData().setSaturation(0F);
        sp.setHealth(sp.getMaxHealth() * 0.5F);
        RpgCombat.forceCrit = false;
        h.runAfterDelay(2, () -> {
            float hp0 = sp.getHealth();
            sp.attack(a);
            Obj steal = w.typeDef().obj("lifesteal");
            double want = Math.min(TestDummies.dealt(a) * steal.num("rate"), RpgHealth.maxOf(sp) * steal.num("cap"));
            double got = sp.getHealth() - hp0;
            h.assertTrue(want > 0 && Math.abs(got - want) < 0.05, "healed " + got + " vs " + want);
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void nobleCraftNeedsOnlyDrops(GameTestHelper h) {
        ServerPlayer sp = player(h);
        List<Smith.Recipe> noble = Smith.craftPage(Smith.craftRecipes(), 2);
        h.assertTrue(noble.size() == 8, "noble page: 8 weapons, got " + noble.size());
        h.assertTrue(Smith.craftPage(Smith.craftRecipes(), 0).size() == 8 && Smith.craftPage(Smith.craftRecipes(), 1).size() == 9, "hero 8 · transcend 9");
        h.assertTrue(Smith.nextCraftPage(Smith.craftRecipes(), 0) == 1 && Smith.nextCraftPage(Smith.craftRecipes(), 1) == 2
                && Smith.nextCraftPage(Smith.craftRecipes(), 2) == 0, "page cycle hero → transcend → noble → hero");
        Smith.Recipe r = noble.get(0);
        h.assertTrue(r.ore() == 0 && r.mats().stream().allMatch(m -> m.kind().equals("drop")), "drops only");
        for (Smith.Mat m : r.mats()) sp.getInventory().add(new ItemStack(m.item(), m.count()));
        sp.getInventory().setItem(30, new ItemStack(net.minecraft.world.item.Items.EMERALD, r.emerald()));
        h.assertTrue(Smith.firstCraftPage(sp, Smith.craftRecipes()) == 2, "opens on the noble page with those drops");
        String msg = Smith.craft(sp, r);
        h.assertTrue(msg.contains("완료"), "crafted: " + msg);
        h.assertTrue(RpgGearTests.countIn(sp, RpgItems.byId(r.w().id())) == 1, "got " + r.w().id());
        for (Smith.Mat m : r.mats()) h.assertTrue(RpgGearTests.countIn(sp, m.item()) == 0, "used " + m.name());
        done(h, sp);
    }

    // ------------------------------------------------------------ 스킬 (시험마다 따로 묶음)

    /** 한 과녁 앞에서 스킬 하나: index 번 스킬, 과녁 (앞 dz 칸), wait 틱 뒤 피해 합이 mults 의 치명타 조합 중 하나 */
    private static void skillCase(GameTestHelper h, String type, String grade, int index, double dz, int wait, java.util.function.Function<Obj, double[]> mults,
            java.util.function.BiConsumer<ServerPlayer, Husk> check) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon(type, grade.equals("hero") ? 40 : 40, grade);
        hold(sp, w);
        Obj k = w.skills().get(index);
        Husk z = dummy(h, 1.5, 2, 1.5 + dz);
        h.runAfterDelay(2, () -> {
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            h.assertTrue(Transcend.castSkill(sp, w, index), "cast " + k.str("id"));
            h.assertTrue(Transcend.cooldownLeft(sp, k) == Math.round(k.num("cd") * 20), "cooldown " + Transcend.cooldownLeft(sp, k));
            double b = base(sp, w);
            double c = w.critMult();
            if (check != null) check.accept(sp, z);
            h.runAfterDelay(wait, () -> {
                double[] m = mults.apply(k);
                h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, m), k.str("id") + " dealt " + TestDummies.dealt(z) + " vs " + java.util.Arrays.toString(m)
                        + " × " + b);
                done(h, sp);
            });
        });
    }

    @GameTest(environment = "mmorpg:w_gaebolg", maxTicks = 50)
    public void gaeBolgPiercesThenThorns(GameTestHelper h) {
        skillCase(h, "lance", "hero", 0, 5.5, 34, k -> new double[] {k.num("mult"), k.num("afterMult")}, (sp, z) -> h.runAfterDelay(26, () -> {
            MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() == 0, "thorns slow " + slow);
        }));
    }

    @GameTest(environment = "mmorpg:w_brionac1", maxTicks = 90)
    public void brionacFiveSpears(GameTestHelper h) {
        skillCase(h, "lance", "transcend", 0, 5.5, 72, k -> times((int) k.num("shots"), k.num("mult")), null);
    }

    @GameTest(environment = "mmorpg:w_brionac2", maxTicks = 90)
    public void brionacFlurryThenSpiral(GameTestHelper h) {
        skillCase(h, "lance", "transcend", 1, 3.5, 66, k -> concat(times((int) k.num("hits"), k.num("mult")), k.num("afterMult")), null);
    }

    @GameTest(environment = "mmorpg:w_harpe", maxTicks = 60)
    public void harpeSpinsAndBrandBursts(GameTestHelper h) {
        skillCase(h, "scythe", "hero", 0, 2.5, 40, k -> concat(times((int) k.num("hits"), k.num("mult")), k.num("afterMult")), null);
    }

    @GameTest(environment = "mmorpg:w_kronos1", maxTicks = 70)
    public void kronosRendStopsTime(GameTestHelper h) {
        skillCase(h, "scythe", "transcend", 0, 3.0, 52, k -> new double[] {k.num("mult"), k.num("afterMult")}, (sp, z) -> h.runAfterDelay(8, () -> {
            MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() >= 10, "time stopped " + slow);
        }));
    }

    @GameTest(environment = "mmorpg:w_kronos2", maxTicks = 110)
    public void kronosClockTicksThenMidnight(GameTestHelper h) {
        skillCase(h, "scythe", "transcend", 1, 3.0, 92, k -> concat(times((int) k.num("hits"), k.num("mult")), k.num("afterMult")),
                (sp, z) -> h.runAfterDelay(14, () -> {
                    MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
                    h.assertTrue(slow != null && slow.getAmplifier() == 2, "clock slow III " + slow);
                }));
    }

    @GameTest(environment = "mmorpg:w_dagda", maxTicks = 60)
    public void dagdaSmashHeals(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("hammer", 35, "hero");
        hold(sp, w);
        Obj k = w.skills().get(0);
        Husk z = dummy(h, 1.5, 2, 4.0);
        sp.getFoodData().setFoodLevel(17);
        sp.getFoodData().setSaturation(0F);
        sp.setHealth(sp.getMaxHealth() * 0.5F);
        float hp0 = sp.getHealth();
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        double b = base(sp, w);
        double c = w.critMult();
        h.runAfterDelay(40, () -> {
            double dealt = TestDummies.dealt(z);
            h.assertTrue(hitsMatch(dealt, b, c, k.num("mult"), k.num("afterMult")), "smash + aftershock " + dealt);
            double want = Math.min(dealt * k.num("heal"), RpgHealth.maxOf(sp) * k.num("healCap"));
            double got = sp.getHealth() - hp0;
            h.assertTrue(Math.abs(got - want) < 0.05, "healed " + got + " vs " + want);
            done(h, sp);
        });
    }

    @GameTest(environment = "mmorpg:w_mjolnir1", maxTicks = 80)
    public void mjolnirThrowGoesAndReturns(GameTestHelper h) {
        // 과녁은 가는 길 (5칸) 에만 — 끝 (12칸) 의 번개 폭발 밖: 나갈 때 · 돌아올 때 두 번
        skillCase(h, "hammer", "transcend", 0, 5.0, 56, k -> new double[] {k.num("mult"), k.num("backMult")}, null);
    }

    @GameTest(environment = "mmorpg:w_mjolnir2", maxTicks = 160)
    public void mjolnirStormStrikesThenPillar(GameTestHelper h) {
        skillCase(h, "hammer", "transcend", 1, 3.0, 140, k -> concat(times((int) k.num("hits"), k.num("mult")), k.num("afterMult")), null);
    }

    @GameTest(environment = "mmorpg:w_caduceus", maxTicks = 80)
    public void caduceusBitesAndHeals(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("scepter", 40, "hero");
        hold(sp, w);
        Obj k = w.skills().get(0);
        sp.setXRot(-70F);
        h.assertFalse(Transcend.castSkill(sp, w, 0), "no target: refused");
        h.assertTrue(Transcend.cooldownLeft(sp, k) == 0, "no cooldown when refused");
        Husk z = dummy(h, 1.5, 2, 6.0);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        sp.getFoodData().setFoodLevel(17);
        sp.getFoodData().setSaturation(0F);
        sp.setHealth(sp.getMaxHealth() * 0.5F);
        float hp0 = sp.getHealth();
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        double b = base(sp, w);
        double c = w.critMult();
        h.runAfterDelay(66, () -> {
            double dealt = TestDummies.dealt(z);
            h.assertTrue(hitsMatch(dealt, b, c, times((int) k.num("hits"), k.num("mult"))), "four bites " + dealt);
            double want = Math.min(dealt * k.num("heal"), RpgHealth.maxOf(sp) * k.num("healCap"));
            double got = sp.getHealth() - hp0;
            h.assertTrue(Math.abs(got - want) < 0.05, "healed " + got + " vs " + want);
            done(h, sp);
        });
    }

    @GameTest(environment = "mmorpg:w_apollo1", maxTicks = 100)
    public void apolloSunBurnsThenBursts(GameTestHelper h) {
        skillCase(h, "scepter", "transcend", 0, 5.0, 80, k -> concat(times((int) k.num("pulses"), k.num("pulseMult")), k.num("afterMult")), null);
    }

    @GameTest(environment = "mmorpg:w_apollo2", maxTicks = 140)
    public void apolloSanctuaryGuardsAndHeals(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("scepter", 40, "transcend");
        hold(sp, w);
        Obj k = w.skills().get(1);
        Husk z = dummy(h, 1.5, 2, 4.5);
        sp.getFoodData().setFoodLevel(17);
        sp.getFoodData().setSaturation(0F);
        sp.setHealth(sp.getMaxHealth() * 0.4F);
        float hp0 = sp.getHealth();
        h.assertTrue(Transcend.castSkill(sp, w, 1), "cast");
        double b = base(sp, w);
        double c = w.critMult();
        h.runAfterDelay(3, () -> {
            Guards.Guard g = Guards.of(sp);
            h.assertTrue(g != null && Math.abs(g.reduce() - k.num("reduce")) < 1e-9, "sanctuary guard " + (g == null ? null : g.reduce()));
            h.assertTrue(sp.getHealth() > hp0, "healed on the first pulse " + hp0 + " → " + sp.getHealth());
        });
        h.runAfterDelay(120, () -> {
            double dealt = TestDummies.dealt(z);
            h.assertTrue(hitsMatch(dealt, b, c, concat(times((int) k.num("pulses"), k.num("pulseMult")), k.num("afterMult"))), "ten pulses + burst " + dealt);
            h.assertTrue(Guards.of(sp) == null, "guard ended");
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 정령왕 강림

    private static void kingCase(GameTestHelper h, int breakLevel, double total) {
        floor(h);
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        for (String s : List.of("str", "dex", "int", "vit")) RpgPlayers.setStat(sp, s, 150);
        Husk z = dummy(h, 1.5, 2, 6.0);
        Runes.Rune r = Runes.parseRune(Runes.runeId("spirit", "transcend"));
        h.assertTrue(r != null && r.def().str("fn").equals("spiritKing"), "spirit king rune");
        h.runAfterDelay(2, () -> {
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            double P = RuneCast.power(sp, "spirit") * mmorpg.core.RuneBreak.coef(breakLevel);
            h.assertTrue(RuneCast.castRune(sp, r, false, breakLevel), "cast");
            h.runAfterDelay(130, () -> {
                double dealt = TestDummies.dealt(z);
                h.assertTrue(Math.abs(dealt - total * P) < 1e-2 * P, "spirit king dealt " + dealt + " vs " + total + " × " + P);
                done(h, sp);
            });
        });
    }

    @GameTest(environment = "mmorpg:w_king", maxTicks = 160)
    public void spiritKingDescends(GameTestHelper h) {
        kingCase(h, 0, 32);
    }

    @GameTest(environment = "mmorpg:w_king6", maxTicks = 160)
    public void spiritKingSixStars(GameTestHelper h) {
        kingCase(h, 6, 50);
    }
}
