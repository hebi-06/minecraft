package mmorpg.fabric.test;

import static mmorpg.fabric.test.RpgMonsterTests.fresh;
import static mmorpg.fabric.test.RpgMonsterTests.monster;
import static mmorpg.fabric.test.RpgMonsterTests.near;
import static mmorpg.fabric.test.RpgMonsterTests.player;
import static mmorpg.fabric.test.RpgMonsterTests.scaled;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.mob.RpgEntities;
import mmorpg.fabric.mob.RpgMonster;
import mmorpg.fabric.mob.VoidMonsters;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.quest.SideQuests;
import mmorpg.fabric.shop.Shop;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.32.0 게임 시험 (서버) — 공허 몬스터 다섯 (사용자 요청 "공허 시리즈 40렙 뒤 사냥터 · 42 · 48 · 52 · 55 · 58렙"):
 * 자료 (순서 19 · 지역 void · 엔티티 · 소환 알 · 전리품 · 레벨로 열리는 반복 의뢰 · 소리 · 동작 · 텍스처 · 소환 체력 · 크기),
 * 특성: 도약충 덮치기 (웅크림 → 포물선 → 떨어진 자리 1.3배 · 구속 II) · 무리 (울음) / 추적자 균열 이동 (사라짐 — 보이지 않고 맞지 않음 → 등 뒤에서 나와
 * 십자 베기 1.4배 · 어둠, 비키면 빗나감) / 해파리 공허 구체 (모아 쏨 → 닿아 터짐) · 촉수 구속 III / 굴착자 땅굴 (땅속 — 맞지 않음 · 이름표 없음 → 다가옴 →
 * 발밑 경고 → 솟구쳐 1.5배 · 띄움) / 거수 수정 가시 (일곱 개가 줄지어 솟음 · 같은 사람 한 번 1.1배 · 띄움).
 * 특성이 둘레를 살피므로 시험마다 따로 묶음 (환경 mmorpg:void_*). 통과하면 [void-monster-test] OK 줄.
 */
public final class RpgVoidMonsterTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-void-monster-test");

    /** 공허 몬스터 (레벨 순서) */
    static final List<String> VOID = List.of("void_leaper", "void_stalker", "void_jelly", "void_borer", "void_behemoth");

    private static void ok(String what) {
        LOG.info("[void-monster-test] OK {}", what);
    }

    /** 시험 자리 바닥 (y 1): 8 × 8 돌 */
    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new net.minecraft.core.BlockPos(x, 1, z), Blocks.STONE);
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    private static boolean played(BedrockMob m, String name) {
        return m.syncedAnims().stream().anyMatch(p -> p.anim().endsWith("." + name));
    }

    private static JsonObject resourceJson(String path) {
        try (InputStream in = Monsters.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static void effect(GameTestHelper h, ServerPlayer sp, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int amp, int ticks, String what) {
        MobEffectInstance x = sp.getEffect(e);
        h.assertTrue(x != null && x.getAmplifier() == amp && Math.abs(x.getDuration() - ticks) <= 3, what + ": " + x);
    }

    // ------------------------------------------------------------ 자료

    /**
     * 공허 다섯: 몬스터 순서 19 (끝 다섯 — 레벨 순), 지역 void, 공허 특성, 엔티티 · 소환 알 · 전리품 아이템 · 반복 의뢰 (메인 단계 없이 레벨로 열림), 소리 (몬스터 · 특성 연출),
     * 그림 (모델 · idle · walk · attack · 특성 동작 · 텍스처 · 빛나는 텍스처), 소환하면 체력 · 크기 · 이름표, 전리품 팔기 창의 칸 (19종 — 넷째 줄)
     */
    @GameTest(environment = "mmorpg:void_data", maxTicks = 40)
    public void voidMonstersData(GameTestHelper h) {
        List<String> order = Rules.get().strings("MONSTER_ORDER");
        h.assertTrue(order.size() == 19 && order.subList(14, 19).equals(VOID), "monster order " + order);
        Obj monsters = Rules.get().obj("MONSTERS");
        Obj rq = Rules.get().obj("REPEAT_QUESTS");
        int prev = 39;
        for (String key : VOID) {
            Monsters.Kind k = Monsters.kind(key);
            h.assertTrue(k != null && k.traits().isVoid() && "void".equals(monsters.obj(key).str("region")), key + " void region");
            h.assertTrue(k.level() > prev, key + " level order");
            prev = k.level();
            h.assertTrue(RpgEntities.type("rpg:" + key) != null && RpgEntities.eggs().containsKey(key), key + " entity · spawn egg");
            h.assertTrue(GearItems.byId("rpg:loot_" + key) != null && Loot.dropItemIds().contains("rpg:loot_" + key), key + " loot item");
            h.assertTrue(Monsters.soundOf(k.windupSound()) != null && Monsters.soundOf(k.hitSound()) != null, key + " sounds " + k.windupSound() + " / " + k.hitSound());
            Quest rep = Quests.getQuest(SideQuests.repeatId(key));
            h.assertTrue(rep != null && rep.repeat && rep.afterStep == 0 && rep.minLevel == (int) rq.obj(key).num("minLevel") && rep.target.equals("rpg:loot_" + key),
                    key + " repeat quest opens by level " + (rep == null ? null : rep.minLevel));
        }
        h.assertTrue(Monsters.kind("void_leaper").traits().leap() != null && Monsters.kind("void_leaper").pack() != null
                && "screech".equals(Monsters.kind("void_leaper").pack().anim()), "leaper: leap · swarm");
        h.assertTrue(Monsters.kind("void_stalker").traits().blink() != null, "stalker: blink");
        h.assertTrue(Monsters.kind("void_jelly").traits().orb() != null && Monsters.kind("void_jelly").onHit() != null, "jelly: orb · sting");
        h.assertTrue(Monsters.kind("void_borer").traits().tunnel() != null, "borer: tunnel");
        h.assertTrue(Monsters.kind("void_behemoth").traits().spikes() != null && "void".equals(Monsters.kind("void_behemoth").shock().fx()), "behemoth: spikes · void shock");
        h.assertTrue(!Monsters.kind("frost_giant").traits().isVoid() && Monsters.kind("frost_giant").traits() == VoidMonsters.NONE, "older monsters have no void traits");
        for (String s : VoidMonsters.SOUNDS) h.assertTrue(Monsters.soundOf(s) != null, "void sound " + s);
        // 그림: 엔티티 (모델 · 동작 이름) · 텍스처 · 빛나는 텍스처
        JsonObject rend = resourceJson("/assets/mmorpg/bedrock/entities.json");
        h.assertTrue(rend != null, "bedrock render data");
        Map<String, List<String>> special = Map.of("void_leaper", List.of("leap_prep", "leap", "screech"), "void_stalker", List.of("vanish", "appear"), "void_jelly",
                List.of("cast"), "void_borer", List.of("burrow", "emerge"), "void_behemoth", List.of("slam", "roar"));
        for (String key : VOID) {
            JsonObject e = rend.getAsJsonObject("entities").getAsJsonObject("rpg:" + key);
            h.assertTrue(e != null, key + " render entity");
            JsonObject a = e.getAsJsonObject("animations");
            List<String> need = new ArrayList<>(List.of("idle", "walk", "attack"));
            need.addAll(special.get(key));
            for (String n : need) {
                String full = "animation.rpg.monster." + key + "." + n;
                h.assertTrue(a.has(n) && a.get(n).getAsString().equals(full) && rend.getAsJsonObject("animations").has(full), key + " animation " + n);
            }
            h.assertTrue(rend.getAsJsonObject("models").has(e.getAsJsonObject("geometry").get("default").getAsString()), key + " model");
            h.assertTrue(Monsters.class.getResource("/assets/mmorpg/textures/entity/rpg/monsters/" + key + ".png") != null, key + " texture");
            h.assertTrue(Monsters.class.getResource("/assets/mmorpg/textures/entity/rpg/monsters/" + key + "_emissive.png") != null, key + " glow texture");
        }
        // 소환: 체력 · 크기 · 이름표 · 숨지 않음
        List<RpgMonster> spawned = new ArrayList<>();
        for (int i = 0; i < VOID.size(); i++) {
            String key = VOID.get(i);
            Monsters.Kind k = Monsters.kind(key);
            RpgMonster m = monster(h, key, 1.5 + (i % 3) * 3, 2, 1.5 + (i / 3) * 4, false);
            spawned.add(m);
            near(h, m.getMaxHealth(), k.hp(), 1e-3, key + " max hp");
            near(h, m.getBbWidth(), k.half() * 2, 1e-3, key + " width");
            near(h, m.getBbHeight(), k.height(), 1e-3, key + " height");
            h.assertTrue(("§eLv." + k.level() + " §f" + k.name()).equals(Monsters.labelOf(m)), key + " label " + Monsters.labelOf(m));
            h.assertTrue(!Monsters.buried(m) && !VoidMonsters.phased(m) && !m.isInvisible(), key + " plain at spawn");
        }
        // 전리품 팔기 창: 19종 — 마지막 전리품은 넷째 줄 (칸 32)
        h.assertTrue(Shop.sellSlot(18) == 32 && Shop.sellSlot(14) == 28, "sell slots for 19 drops " + Shop.sellSlot(14) + " " + Shop.sellSlot(18));
        ok("void data: order · region · traits · entities · eggs · loot · repeat by level · sounds · animations · textures · spawn · sell slots");
        for (RpgMonster m : spawned) m.discard();
        h.succeed();
    }

    /** 반복 의뢰: 메인 처치 단계 없이 레벨로 열림 (도약충 40 · 거수 56) */
    @GameTest(environment = "mmorpg:void_data", maxTicks = 40)
    public void voidRepeatQuestsByLevel(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 0);
        Quest leaper = Quests.getQuest(SideQuests.repeatId("void_leaper"));
        Quest behemoth = Quests.getQuest(SideQuests.repeatId("void_behemoth"));
        sp.setExperienceLevels(leaper.minLevel - 1);
        h.assertTrue("level".equals(Quests.lockReason(sp, leaper)), "locked below level " + leaper.minLevel + ": " + Quests.lockReason(sp, leaper));
        sp.setExperienceLevels(leaper.minLevel);
        h.assertTrue(Quests.lockReason(sp, leaper).isEmpty(), "open at level " + leaper.minLevel + ": " + Quests.lockReason(sp, leaper));
        h.assertTrue("level".equals(Quests.lockReason(sp, behemoth)), "behemoth still locked at " + leaper.minLevel);
        sp.setExperienceLevels(behemoth.minLevel);
        h.assertTrue(Quests.lockReason(sp, behemoth).isEmpty(), "behemoth open at " + behemoth.minLevel);
        ok("void repeat quests open by level " + leaper.minLevel + " · " + behemoth.minLevel);
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 공허 도약충

    /** 덮치기: 2.5 ~ 9칸이면 웅크림 (떨어질 자리 = 그때 내 자리) → 포물선 비행 → 떨어진 자리 둘레 피해 attack × 1.3 · 구속 II 1.5초 */
    @GameTest(environment = "mmorpg:void_leap", maxTicks = 200)
    public void leaperLeaps(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("void_leaper");
        VoidMonsters.Leap l = k.traits().leap();
        RpgMonster m = monster(h, "void_leaper", 7.5, 2, 6.5, false);
        float[] hp0 = {-1};
        boolean[] seen = {false, false}; // [웅크림, 비행]
        double[] top = {0};
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            h.assertTrue(Monsters.fightOf(m) != null, "fight");
        });
        h.onEachTick(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            if (f == null) return;
            String ph = VoidMonsters.runPhase(f);
            if ("prep".equals(ph)) seen[0] = true;
            if ("fly".equals(ph)) {
                seen[1] = true;
                top[0] = Math.max(top[0], m.getY() - h.absoluteVec(new Vec3(0, 2, 0)).y);
            }
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && "leap".equals(f.lastKind()), "leap resolved " + (f == null ? null : f.lastKind()));
            h.assertTrue(seen[0] && seen[1] && top[0] > 1.0, "prep → fly (top " + top[0] + ")");
            h.assertTrue(f.hits() == 1 && flat(m.position(), sp.position()) < 1.2, "landed on the player " + flat(m.position(), sp.position()));
            near(h, hp0[0] - sp.getHealth(), scaled(h, k.attack() * l.mult()), 1e-3, "leap damage ×" + l.mult());
            effect(h, sp, MobEffects.SLOWNESS, l.slowAmp(), (int) Math.round(l.slowSec() * 20), "leap: slowness");
            h.assertTrue(played(m, "leap_prep") || played(m, "leap") || played(m, "attack"), "leap animations " + m.syncedAnims());
            ok("leaper leap: prep → fly (top " + String.format(java.util.Locale.ROOT, "%.2f", top[0]) + ") → land ×" + l.mult());
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    /** 무리: 처음 맞은 도약충이 울고 (screech) 둘레 10칸의 싸우지 않던 도약충도 때린 사람에게 덤빔 */
    @GameTest(environment = "mmorpg:void_swarm", maxTicks = 120)
    public void leaperSwarm(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        RpgMonster a = monster(h, "void_leaper", 4.5, 2, 1.5, false);
        RpgMonster b = monster(h, "void_leaper", 4.5, 2, 4.5, false);
        RpgMonster c = monster(h, "void_leaper", 7.5, 2, 6.5, false);
        h.runAfterDelay(66, () -> {
            a.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            Monsters.Fight fa = Monsters.fightOf(a);
            h.assertTrue(fa != null && fa.packCalled() >= 2, "screech called the swarm " + (fa == null ? null : fa.packCalled()));
            for (RpgMonster w : List.of(b, c)) {
                Monsters.Fight fw = Monsters.fightOf(w);
                h.assertTrue(fw != null && fw.player() == sp, "swarm joins the fight");
            }
            h.assertTrue(played(a, "screech"), "screech animation " + a.syncedAnims());
            ok("leaper swarm: screech calls " + fa.packCalled());
            for (RpgMonster m : List.of(a, b, c)) m.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 공허 추적자

    /**
     * 균열 이동: 사라짐 (vanish) → 보이지 않고 맞지 않음 · 이름표 없음, 대상의 등 뒤에 균열 → 나타나 (보임 · 맞음) 십자 베기 attack × 1.4 · 어둠
     */
    @GameTest(environment = "mmorpg:void_blink", maxTicks = 200)
    public void stalkerBlinkStrike(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 2.5, 2, 2.5, 300);
        sp.snapTo(sp.getX(), sp.getY(), sp.getZ(), 0F, 0F); // +z 를 봄 → 등 뒤는 -z
        Monsters.Kind k = Monsters.kind("void_stalker");
        VoidMonsters.Blink b = k.traits().blink();
        RpgMonster m = monster(h, "void_stalker", 6.5, 2, 6.5, false);
        float[] hp0 = {-1};
        boolean[] hidden = {false, false, false}; // [보이지 않음, 맞지 않음, 이름표 없음]
        Vec3[] spot = {null};
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
        });
        h.onEachTick(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            if (f == null || !"hidden".equals(VoidMonsters.runPhase(f)) || hidden[0]) return;
            hidden[0] = m.isInvisible() && VoidMonsters.phased(m) && !m.isPickable();
            float before = m.getHealth();
            m.setInvulnerableTime(0);
            m.damageCooldownTime = 0;
            boolean took = m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 5F);
            hidden[1] = !took && m.getHealth() == before;
            hidden[2] = Monsters.labelOf(m) == null;
            spot[0] = VoidMonsters.runSpot(f);
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && "blink".equals(f.lastKind()), "blink resolved " + (f == null ? null : f.lastKind()));
            h.assertTrue(hidden[0] && hidden[1] && hidden[2], "in the rift: invisible · not hit · no tag " + hidden[0] + " " + hidden[1] + " " + hidden[2]);
            h.assertTrue(spot[0] != null && spot[0].z < sp.getZ() - 1.0, "rift behind the player " + spot[0]);
            h.assertTrue(!m.isInvisible() && !VoidMonsters.phased(m) && flat(m.position(), spot[0]) < 0.6, "came out at the rift " + m.position());
            h.assertTrue(f.hits() == 1, "cross slash hit " + f.hits() + " / " + f.misses());
            near(h, hp0[0] - sp.getHealth(), scaled(h, k.attack() * b.mult()), 1e-3, "blink damage ×" + b.mult());
            h.assertTrue(sp.hasEffect(MobEffects.DARKNESS), "darkness");
            h.assertTrue(played(m, "appear") || played(m, "vanish") || played(m, "attack"), "blink animations " + m.syncedAnims());
            ok("stalker blink: vanish (invisible · immune) → rift behind → cross slash ×" + b.mult() + " · darkness");
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    /** 균열 이동을 비킴: 균열이 열린 동안 멀리 가면 나와서 베어도 빗나감 (피해 없음) */
    @GameTest(environment = "mmorpg:void_dodge", maxTicks = 200)
    public void stalkerBlinkDodged(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 2.5, 2, 2.5, 300);
        sp.snapTo(sp.getX(), sp.getY(), sp.getZ(), 0F, 0F);
        RpgMonster m = monster(h, "void_stalker", 6.5, 2, 6.5, false);
        float[] hp0 = {-1};
        boolean[] moved = {false};
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
        });
        h.onEachTick(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            if (f == null || moved[0] || !"hidden".equals(VoidMonsters.runPhase(f))) return;
            Vec3 to = h.absoluteVec(new Vec3(7.5, 2, 2.5));
            sp.snapTo(to.x, to.y, to.z, 0F, 0F); // 균열에서 5칸 비킴
            moved[0] = true;
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && "blink".equals(f.lastKind()) && moved[0], "blink resolved after the dodge");
            h.assertTrue(f.misses() >= 1 && f.hits() == 0, "missed " + f.hits() + " / " + f.misses());
            near(h, hp0[0] - sp.getHealth(), 0, 1e-3, "no damage when dodged");
            ok("stalker blink dodged");
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    // ------------------------------------------------------------ 공허 해파리

    /** 공허 구체: 사거리 밖이면 모아 (cast) 쏨 → 날아가 닿으면 터짐 (둘레 피해 = 구체 attack) · 촉수 (근접) 는 구속 III 2초 */
    @GameTest(environment = "mmorpg:void_orb", maxTicks = 240)
    public void jellyOrb(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("void_jelly");
        VoidMonsters.Orb o = k.traits().orb();
        RpgMonster m = monster(h, "void_jelly", 7.5, 2, 6.5, false);
        float[] hp0 = {-1};
        int[] fired0 = {0};
        int[] burst0 = {0};
        boolean[] flew = {false};
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            fired0[0] = VoidMonsters.orbsFired;
            burst0[0] = VoidMonsters.orbsBurst;
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
        });
        h.onEachTick(() -> {
            if (VoidMonsters.orbCount() > 0) flew[0] = true;
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && "orb".equals(f.lastKind()), "orb fired " + (f == null ? null : f.lastKind()));
            h.assertTrue(VoidMonsters.orbsFired > fired0[0] && VoidMonsters.orbsBurst > burst0[0] && flew[0], "orb flew and burst");
            near(h, hp0[0] - sp.getHealth(), scaled(h, o.attack()), 1e-3, "orb burst damage");
            h.assertTrue(played(m, "cast"), "cast animation " + m.syncedAnims());
            fresh(sp);
            sp.removeEffect(MobEffects.SLOWNESS);
            h.assertTrue(Monsters.monsterHit(m, k, sp, 1), "tendril sting");
            effect(h, sp, MobEffects.SLOWNESS, 2, 40, "jelly: slowness III 2s");
            ok("jelly orb: cast → fly → burst " + o.attack() + " · sting slowness III");
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    // ------------------------------------------------------------ 공허 굴착자

    /** 땅굴: 파고듦 → 땅속 (낮은 몸 · 맞지 않음 · 이름표 없음) 으로 다가옴 → 발밑 경고 → 솟구쳐 attack × 1.5 · 띄움 → 다시 땅 위 */
    @GameTest(environment = "mmorpg:void_tunnel", maxTicks = 260)
    public void borerTunnels(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("void_borer");
        VoidMonsters.Tunnel t = k.traits().tunnel();
        RpgMonster m = monster(h, "void_borer", 7.5, 2, 6.5, false);
        float[] hp0 = {-1};
        boolean[] under = {false, false, false, false}; // [땅속, 낮은 몸, 맞지 않음, 이름표 없음]
        double[] dist = {99, 99}; // [땅속에 든 때 거리, 땅속에서 가장 가까운 거리]
        double[] lift = {0};
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
        });
        h.onEachTick(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            if (f == null) return;
            lift[0] = Math.max(lift[0], sp.getDeltaMovement().y);
            if (!"under".equals(VoidMonsters.runPhase(f))) return;
            double d = flat(m.position(), sp.position());
            if (!under[0]) {
                under[0] = Monsters.buried(m) && VoidMonsters.phased(m);
                under[1] = m.getBbHeight() < 0.6;
                float before = m.getHealth();
                m.setInvulnerableTime(0);
            m.damageCooldownTime = 0;
                under[2] = !m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 5F) && m.getHealth() == before;
                under[3] = Monsters.labelOf(m) == null;
                dist[0] = d;
            }
            dist[1] = Math.min(dist[1], d);
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && "tunnel".equals(f.lastKind()), "tunnel resolved " + (f == null ? null : f.lastKind()));
            h.assertTrue(under[0] && under[1] && under[2] && under[3], "under: buried · low box · not hit · no tag " + java.util.Arrays.toString(under));
            h.assertTrue(dist[1] < dist[0] - 3, "came closer underground " + dist[0] + " → " + dist[1]);
            h.assertTrue(!Monsters.buried(m) && !VoidMonsters.phased(m) && m.getBbHeight() > 1, "surfaced");
            h.assertTrue(f.hits() == 1, "erupt hit " + f.hits() + " / " + f.misses());
            near(h, hp0[0] - sp.getHealth(), scaled(h, k.attack() * t.mult()), 1e-3, "erupt damage ×" + t.mult());
            h.assertTrue(lift[0] >= t.lift() - 1e-6, "launched up " + lift[0]);
            h.assertTrue(played(m, "emerge") || played(m, "burrow") || played(m, "attack"), "tunnel animations " + m.syncedAnims());
            ok("borer tunnel: dive → under (immune) " + String.format(java.util.Locale.ROOT, "%.1f → %.1f", dist[0], dist[1]) + " → warn → erupt ×" + t.mult());
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    // ------------------------------------------------------------ 공허 거수

    /** 수정 가시: 일어섬 (slam) → 나를 향한 줄에 가시 count 개가 차례로 솟음 (블록 표시) → 내 자리 가시가 attack × 1.1 · 띄움 (같은 사람은 한 번만) */
    @GameTest(environment = "mmorpg:void_spikes", maxTicks = 260)
    public void behemothSpikes(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("void_behemoth");
        VoidMonsters.Spikes s = k.traits().spikes();
        RpgMonster m = monster(h, "void_behemoth", 7.5, 2, 6.5, false);
        float[] hp0 = {-1};
        int[] fx0 = {0};
        double[] lift = {0};
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            fx0[0] = FxDisplays.spawned;
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
        });
        h.onEachTick(() -> lift[0] = Math.max(lift[0], sp.getDeltaMovement().y));
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && "spikes".equals(f.lastKind()) && VoidMonsters.runKind(f) == null, "spikes done " + (f == null ? null : f.lastKind()));
            h.assertTrue(VoidMonsters.lastSpikes.size() == s.count() && FxDisplays.spawned - fx0[0] >= s.count(), "spikes rose " + VoidMonsters.lastSpikes.size());
            Vec3 a = VoidMonsters.lastSpikes.get(0);
            Vec3 z = VoidMonsters.lastSpikes.get(s.count() - 1);
            near(h, flat(a, z), s.step() * (s.count() - 1), 0.05, "spike line length");
            h.assertTrue(flat(z, sp.position()) < flat(z, m.position()), "line toward the player");
            h.assertTrue(f.hits() == 1, "hit once " + f.hits());
            near(h, hp0[0] - sp.getHealth(), scaled(h, k.attack() * s.mult()), 1e-3, "spike damage ×" + s.mult());
            h.assertTrue(lift[0] >= s.lift() - 1e-6, "launched up " + lift[0]);
            h.assertTrue(played(m, "slam"), "slam animation " + m.syncedAnims());
            ok("behemoth spikes: " + s.count() + " in a line · hit once ×" + s.mult());
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }
}
