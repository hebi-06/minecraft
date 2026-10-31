package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mmorpg.core.Runes;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.rune.RuneCast;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.26.0 룬 지속 보정 · 0.49.0 선딜 보정 (사용자 "풀딜을 다 때릴려면 시간이 걸리거나 장판기인 애들은 한방 딜 애들보다 조금 더 쎄게" · "선딜레이가 있으면 조금 더 쎄게"): 계열마다 다섯 등급 (한정은 초월 하나) 을
 * 차례로 앞 3칸 과녁 하나에 써서, 쓴 틱부터 첫 피해 · 마지막 피해까지의 틱을 잼 (과녁이 밀려나지 않게 밀쳐냄 저항 1). 결과는 로그 [rune-timing]
 * (CI 의 rune-timing.txt). 확인: 표 (RUNE_SPREAD) 에 t 가 있는 룬은 피해가 들어가고 (위력 × 총 배율 × 피해 배율 (유틸 값 · 시간 보정) — 다 맞았으면), 다 맞은 룬의
 * 마지막 피해 틱 = 표의 t (±3틱 — 몇 타가 빗나간 룬은 t + 3 안), 첫 피해 틱 = 표의 w (선딜 — ±3틱, w 가 없으면 8틱 안), t 가 없는 룬은 피해가 없음 (평타 강화 · 버프).
 */
public final class RpgRuneTimingTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-rune-timing");
    private static final String[] GRADES = {"common", "rare", "noble", "hero", "transcend"};
    /** 마지막 피해 뒤 이만큼 더 피해가 없으면 끝 · 아무 피해가 없으면 이만큼 · 한 룬을 재는 가장 긴 틱 */
    private static final int QUIET = 100;
    private static final int NONE = 200;
    private static final int MAX = 360;

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
        RpgPlayers.onJoin(sp);
        return sp;
    }

    private static void clearStrays(GameTestHelper h, net.minecraft.world.entity.Entity... keep) {
        var box = new net.minecraft.world.phys.AABB(h.absoluteVec(new Vec3(-14, -4, -14)), h.absoluteVec(new Vec3(30, 12, 30)));
        java.util.Set<net.minecraft.world.entity.Entity> own = java.util.Set.of(keep);
        for (net.minecraft.world.entity.Mob m : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                e -> !own.contains(e) && e instanceof net.minecraft.world.entity.monster.Enemy)) {
            m.discard();
        }
    }

    private static Husk dummy(GameTestHelper h) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, 4.5F));
        m.setYRot(180F);
        m.setYHeadRot(180F);
        m.setYBodyRot(180F);
        m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false));
        return m;
    }

    /** 이 계열의 룬을 차례로 재는 상태 */
    private static final class Probe {
        final List<Runes.Rune> runes = new ArrayList<>();
        int idx;
        boolean measuring;
        final List<String> failures = new ArrayList<>();
        long t0;
        int first = -1;
        int last = -1;
        /** 0.46.0 피해가 늘어난 틱들 (룬 동작의 타격 시각을 맞추는 자료 — 로그 hits) */
        final List<Integer> hits = new ArrayList<>();
        double prev;
        double want;
        boolean cast;
        Husk z;
    }

    private static void probe(GameTestHelper h, String family) {
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        for (String s : new String[] {"str", "dex", "vit", "int"}) RpgPlayers.setStat(sp, s, 120);
        if ("scythe".equals(Runes.weaponOnly(family))) sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(Weapons.weaponId("scythe", 40, "transcend"))));
        Probe p = new Probe();
        for (String g : GRADES) {
            Runes.Rune r = Runes.parseRune(Runes.runeId(family, g));
            if (r != null) p.runes.add(r);
        }
        h.assertTrue(!p.runes.isEmpty(), family + " runes");
        h.succeedWhen(() -> {
            long now = h.getTick();
            if (now < 3) throw h.assertionException("loading");
            if (!p.measuring) {
                if (p.idx >= p.runes.size()) {
                    h.getLevel().getServer().getPlayerList().remove(sp);
                    h.assertTrue(p.failures.isEmpty(), family + " timing: " + String.join(" / ", p.failures));
                    return;
                }
                Runes.Rune r = p.runes.get(p.idx);
                Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
                sp.snapTo(at.x, at.y, at.z, 0F, 0F);
                sp.setDeltaMovement(Vec3.ZERO);
                sp.removeAllEffects();
                RpgHealth.healFull(sp);
                RuneCast.resetCooldowns(sp);
                mmorpg.fabric.rune.RuneMarks.testClear(); // 0.49.0 앞 룬의 가하는 피해 증가 (분노 · 검세 · 기) 가 다음 룬에 남지 않게
                p.z = dummy(h);
                clearStrays(h, p.z);
                sp.lookAt(EntityAnchorArgument.Anchor.EYES, p.z.getEyePosition());
                p.want = RuneCast.power(sp, family) * Runes.runeTotalMult(r.def()) * Runes.damageScale(r);
                p.cast = RuneCast.castRune(sp, r, false, 0);
                p.t0 = now;
                p.first = -1;
                p.last = -1;
                p.hits.clear();
                p.prev = 0;
                p.measuring = true;
                throw h.assertionException("measuring");
            }
            int t = (int) (now - p.t0);
            double d = TestDummies.dealt(p.z);
            if (d > p.prev + 1e-9) {
                if (p.first < 0) p.first = t;
                p.last = t;
                if (p.hits.size() < 80) p.hits.add(t);
                p.prev = d;
            }
            boolean done = t >= MAX || (p.first >= 0 && t - p.last >= QUIET) || (p.first < 0 && t >= NONE) || !p.cast && t >= 5;
            if (!done) throw h.assertionException("measuring " + t);
            Runes.Rune r = p.runes.get(p.idx);
            LOG.info("[rune-timing] {} {} {} cast={} first={} last={} dealt={} want={} hits={}", family, r.grade(), r.def().strOrNull("fn"), p.cast, p.first,
                    p.last, String.format(Locale.ROOT, "%.1f", p.prev), String.format(Locale.ROOT, "%.1f", p.want), p.hits);
            // 지속 보정 표와 맞는지
            mmorpg.core.Obj e = Runes.spreadEntry(r);
            String id = family + "." + r.grade();
            // 0.49.0 받는 피해 증가 표식을 스스로 건 룬 (피의 낙인 · 마그마 대지 — 낙인 · 녹은 뒤 타격) 은 뒤 타격이 + vuln 만큼 더 들어감
            double vuln = r.def().numOr("vuln", 0);
            boolean full = p.want > 0 && p.prev >= p.want * 0.99 && p.prev <= p.want * (1 + vuln) * 1.01;
            if (!p.cast) p.failures.add(id + " not cast");
            else if (e == null) {
                if (p.prev > 0) p.failures.add(id + " deals damage (" + Math.round(p.prev) + ") but has no RUNE_SPREAD entry");
            } else if (!e.has("t")) {
                if (p.prev > 0) p.failures.add(id + " deals damage (" + Math.round(p.prev) + ") but RUNE_SPREAD has no t");
            } else {
                int want = (int) e.num("t");
                // 0.49.0 선딜 (첫 피해까지 — 표의 w, 6틱 미만은 0): 표에 w 가 있으면 첫 피해 = w (±3틱), 없으면 첫 피해가 8틱 안
                int w = (int) e.numOr("w", 0);
                if (!(p.prev > 0)) p.failures.add(id + " no damage");
                else if (full && Math.abs(p.last - want) > 3) p.failures.add(id + " last " + p.last + " vs t " + want);
                else if (!full && p.last > want + 3) p.failures.add(id + " (partial) last " + p.last + " > t " + want);
                if (p.prev > 0 && w > 0 && Math.abs(p.first - w) > 3) p.failures.add(id + " first " + p.first + " vs w " + w);
                if (p.prev > 0 && !e.has("w") && p.first >= 8) p.failures.add(id + " first " + p.first + " but no w (windup)"); // w 0 을 적은 룬 (반격 자세) 은 빼고
                if (p.prev > p.want * (1 + vuln) * 1.01 + 1e-6 && !"holy".equals(family)) p.failures.add(id + " dealt " + Math.round(p.prev) + " > want " + Math.round(p.want)); // 성기사: 언데드 ×1.5
            }
            p.z.discard();
            p.idx++;
            p.measuring = false;
            throw h.assertionException("next");
        });
    }

    @GameTest(environment = "mmorpg:rt_str", maxTicks = 2100)
    public void timingStr(GameTestHelper h) {
        probe(h, "str");
    }

    @GameTest(environment = "mmorpg:rt_dex", maxTicks = 2100)
    public void timingDex(GameTestHelper h) {
        probe(h, "dex");
    }

    @GameTest(environment = "mmorpg:rt_vit", maxTicks = 2100)
    public void timingVit(GameTestHelper h) {
        probe(h, "vit");
    }

    @GameTest(environment = "mmorpg:rt_int", maxTicks = 2100)
    public void timingInt(GameTestHelper h) {
        probe(h, "int");
    }

    @GameTest(environment = "mmorpg:rt_axe", maxTicks = 2100)
    public void timingAxe(GameTestHelper h) {
        probe(h, "axe");
    }

    @GameTest(environment = "mmorpg:rt_bow", maxTicks = 2100)
    public void timingBow(GameTestHelper h) {
        probe(h, "bow");
    }

    @GameTest(environment = "mmorpg:rt_holy", maxTicks = 2100)
    public void timingHoly(GameTestHelper h) {
        probe(h, "holy");
    }

    @GameTest(environment = "mmorpg:rt_storm", maxTicks = 2100)
    public void timingStorm(GameTestHelper h) {
        probe(h, "storm");
    }

    @GameTest(environment = "mmorpg:rt_assassin", maxTicks = 2100)
    public void timingAssassin(GameTestHelper h) {
        probe(h, "assassin");
    }

    @GameTest(environment = "mmorpg:rt_blade", maxTicks = 2100)
    public void timingBlade(GameTestHelper h) {
        probe(h, "blade");
    }

    @GameTest(environment = "mmorpg:rt_spellblade", maxTicks = 2100)
    public void timingSpellblade(GameTestHelper h) {
        probe(h, "spellblade");
    }

    @GameTest(environment = "mmorpg:rt_element", maxTicks = 2100)
    public void timingElement(GameTestHelper h) {
        probe(h, "element");
    }

    @GameTest(environment = "mmorpg:rt_spirit", maxTicks = 2100)
    public void timingSpirit(GameTestHelper h) {
        probe(h, "spirit");
    }

    @GameTest(environment = "mmorpg:rt_gravity", maxTicks = 2100)
    public void timingGravity(GameTestHelper h) {
        probe(h, "gravity");
    }

    @GameTest(environment = "mmorpg:rt_reaper", maxTicks = 2100)
    public void timingReaper(GameTestHelper h) {
        probe(h, "reaper");
    }

    @GameTest(environment = "mmorpg:rt_tide", maxTicks = 2100)
    public void timingTide(GameTestHelper h) {
        probe(h, "tide");
    }

    @GameTest(environment = "mmorpg:rt_golem", maxTicks = 2100)
    public void timingGolem(GameTestHelper h) {
        probe(h, "golem");
    }

    @GameTest(environment = "mmorpg:rt_monk", maxTicks = 2100)
    public void timingMonk(GameTestHelper h) {
        probe(h, "monk");
    }

    @GameTest(environment = "mmorpg:rt_hunter", maxTicks = 2100)
    public void timingHunter(GameTestHelper h) {
        probe(h, "hunter");
    }

    @GameTest(environment = "mmorpg:rt_volcano", maxTicks = 2100)
    public void timingVolcano(GameTestHelper h) {
        probe(h, "volcano");
    }

    @GameTest(environment = "mmorpg:rt_gladiator", maxTicks = 2100)
    public void timingGladiator(GameTestHelper h) {
        probe(h, "gladiator");
    }

    @GameTest(environment = "mmorpg:rt_body", maxTicks = 2100)
    public void timingBody(GameTestHelper h) {
        probe(h, "body");
    }

    @GameTest(environment = "mmorpg:rt_strike", maxTicks = 2100)
    public void timingStrike(GameTestHelper h) {
        probe(h, "strike");
    }

    @GameTest(environment = "mmorpg:rt_arcana", maxTicks = 460)
    public void timingArcana(GameTestHelper h) {
        probe(h, "arcana");
    }

    @GameTest(environment = "mmorpg:rt_dipper", maxTicks = 460)
    public void timingDipper(GameTestHelper h) {
        probe(h, "dipper");
    }

    @GameTest(environment = "mmorpg:rt_skyeye", maxTicks = 460)
    public void timingSkyeye(GameTestHelper h) {
        probe(h, "skyeye");
    }

    @GameTest(environment = "mmorpg:rt_endless", maxTicks = 460)
    public void timingEndless(GameTestHelper h) {
        probe(h, "endless");
    }

    @GameTest(environment = "mmorpg:rt_terra", maxTicks = 460)
    public void timingTerra(GameTestHelper h) {
        probe(h, "terra");
    }

    @GameTest(environment = "mmorpg:rt_yggdrasil", maxTicks = 460)
    public void timingYggdrasil(GameTestHelper h) {
        probe(h, "yggdrasil");
    }

    @GameTest(environment = "mmorpg:rt_abyss", maxTicks = 460)
    public void timingAbyss(GameTestHelper h) {
        probe(h, "abyss");
    }

    @GameTest(environment = "mmorpg:rt_glacier", maxTicks = 460)
    public void timingGlacier(GameTestHelper h) {
        probe(h, "glacier");
    }
}
