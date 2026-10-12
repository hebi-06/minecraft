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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.mob.RpgEntities;
import mmorpg.fabric.mob.RpgMonster;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.quest.SideQuests;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 9단계 게임 시험 (서버) — 새 몬스터 8종 (지역마다 2종 — 사용자 요청 "각 지역에 맞게 잡몹 더 추가"):
 * 등록 · 수치 · 레벨 순서 · 지역 · 소환 알 · 전리품 · 반복 의뢰 · 그림 자원 (모델 · Monsters 가 트는 동작 이름 · 텍스처) · 소리,
 * 특성: 니세 깡충 (때린 뒤 물러남) · 광폭 멧돼지 돌진 (앞발 긁기 동안 멈춤 → 달림 → 부딪히면 1.4배 · 밀쳐냄) · 모래 전갈 독 · 집게 게 등딱지 (-25%) · 붙잡기 (구속 IV) ·
 * 그렌델 시듦 · 웜 유충 매복 (쉬면 모래 속 — 낮은 충돌 상자 · 이름표 없음 · 걷지 않음 → 가까이 오거나 맞으면 튀어나와 덤빔) ·
 * 서리 늑대 무리 (처음 맞으면 울부짖어 둘레 늑대를 부름 · 냉기) · 서리 거인 얼음 충격파 (둘레 사람 절반 · 구속).
 * 서리 늑대 · 웜 유충은 둘레를 살피므로 같은 묶음 (mmorpg:monster) 의 다른 시험에 서리 늑대를 두지 않음. 통과하면 [more-monster-test] OK 줄.
 */
public final class RpgMoreMonsterTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-more-monster-test");

    /** 9단계 새 몬스터 (지역 순서) */
    static final List<String> NEW = List.of("nisse", "boar", "sand_scorpion", "worm_larva", "crab", "grendel", "frost_wolf", "frost_giant");

    private static void ok(String what) {
        LOG.info("[more-monster-test] OK {}", what);
    }

    /** 시험 자리 바닥 (y 1 — 구조물은 비어 있어 AI 를 켠 몬스터는 떨어짐): 8 × 8 돌 */
    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new net.minecraft.core.BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE);
    }

    /** 가로 거리 */
    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /** 그 효과가 세기 amp · 길이 ticks (±1) 로 걸렸는지 */
    private static void effect(GameTestHelper h, ServerPlayer sp, net.minecraft.core.Holder<MobEffect> e, int amp, int ticks, String what) {
        MobEffectInstance x = sp.getEffect(e);
        h.assertTrue(x != null && x.getAmplifier() == amp && Math.abs(x.getDuration() - ticks) <= 2, what + ": " + x);
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

    // ------------------------------------------------------------ 등록 · 자료

    /**
     * 새 몬스터 8종: 몬스터 순서 14 (레벨 순서 — 사냥 추천이 씀), 지역마다 2종 (지역 NPC 목록에도), 엔티티 · 소환 알 · 전리품 아이템 · 반복 의뢰 (그 몬스터 처치 단계 뒤),
     * 소리 (몬스터 · 특성 연출 모두 자바 소리로 풀림), 그림 (모델 · idle · walk · attack · 특성 동작 · 텍스처), 소환하면 체력 · 크기 · 이름표.
     * 기본 묶음 (서리 늑대가 다른 몬스터 시험과 겹치지 않게)
     */
    @GameTest(maxTicks = 30)
    public void moreMonstersData(GameTestHelper h) {
        List<String> order = Rules.get().strings("MONSTER_ORDER");
        h.assertTrue(order.size() == 14 && order.containsAll(NEW), "monster order " + order);
        int prev = 0;
        for (String key : order) {
            Monsters.Kind k = Monsters.kind(key);
            h.assertTrue(k != null && k.level() >= prev, "level order at " + key);
            prev = k.level();
        }
        Obj monsters = Rules.get().obj("MONSTERS");
        Map<String, Integer> perRegion = new HashMap<>();
        for (String key : NEW) {
            Monsters.Kind k = Monsters.kind(key);
            String region = monsters.obj(key).str("region");
            perRegion.merge(region, 1, Integer::sum);
            h.assertTrue(MainQuests.REGIONS.get(region).mobs().contains(key), key + " in region list " + region);
            h.assertTrue(RpgEntities.type("rpg:" + key) != null && RpgEntities.eggs().containsKey(key), key + " entity · spawn egg");
            h.assertTrue(GearItems.byId("rpg:loot_" + key) != null && Loot.dropItemIds().contains("rpg:loot_" + key), key + " loot item");
            h.assertTrue(Monsters.soundOf(k.windupSound()) != null && Monsters.soundOf(k.hitSound()) != null, key + " sounds " + k.windupSound() + " / " + k.hitSound());
            Quest rep = Quests.getQuest(SideQuests.repeatId(key));
            h.assertTrue(rep != null && rep.repeat && rep.afterStep > 0 && rep.afterStep == MainQuests.mainKillStep(key) && rep.target.equals("rpg:loot_" + key),
                    key + " repeat quest after the main kill step");
        }
        h.assertTrue(perRegion.equals(Map.of("farm", 2, "desert", 2, "sea", 2, "north", 2)), "two per region " + perRegion);
        h.assertTrue(Monsters.kind("nisse").hop() != null && Monsters.kind("boar").charge() != null && Monsters.kind("worm_larva").burrow() != null
                && Monsters.kind("frost_wolf").pack() != null && Monsters.kind("frost_giant").shock() != null && "ice".equals(Monsters.kind("frost_giant").shock().fx())
                && Monsters.kind("crab").shell() > 0 && Monsters.kind("sand_scorpion").onHit() != null && Monsters.kind("grendel").onHit() != null, "traits");
        for (String s : List.of("entity.rabbit.jump", "entity.camel.dash", "entity.hoglin.step", "block.sand.break", "item.goat_horn.sound.0", "entity.wolf.growl",
                "block.glass.break", "entity.player.attack.knockback")) {
            h.assertTrue(Monsters.soundOf(s) != null, "trait sound " + s);
        }
        // 그림: 엔티티 (모델 · 동작 이름) — Monsters 가 트는 동작 (animation.rpg.monster.<키>.<이름>) 이 모두 있어야 함, 텍스처
        JsonObject rend = resourceJson("/assets/mmorpg/bedrock/entities.json");
        h.assertTrue(rend != null, "bedrock render data");
        Map<String, List<String>> special = Map.of("nisse", List.of("hop"), "boar", List.of("charge_prep", "charge"), "worm_larva", List.of("buried", "emerge"),
                "frost_wolf", List.of("howl"));
        for (String key : NEW) {
            JsonObject e = rend.getAsJsonObject("entities").getAsJsonObject("rpg:" + key);
            h.assertTrue(e != null, key + " render entity");
            JsonObject a = e.getAsJsonObject("animations");
            List<String> need = new ArrayList<>(List.of("idle", "walk", "attack"));
            need.addAll(special.getOrDefault(key, List.of()));
            for (String n : need) {
                String full = "animation.rpg.monster." + key + "." + n;
                h.assertTrue(a.has(n) && a.get(n).getAsString().equals(full) && rend.getAsJsonObject("animations").has(full), key + " animation " + n);
            }
            h.assertTrue(rend.getAsJsonObject("models").has(e.getAsJsonObject("geometry").get("default").getAsString()), key + " model");
            h.assertTrue(Monsters.class.getResource("/assets/mmorpg/textures/entity/rpg/monsters/" + key + ".png") != null, key + " texture");
        }
        // 소환: 체력 · 크기 (충돌 상자 × 크기 배율) · 이름표
        List<RpgMonster> spawned = new ArrayList<>();
        for (int i = 0; i < NEW.size(); i++) {
            String key = NEW.get(i);
            Monsters.Kind k = Monsters.kind(key);
            RpgMonster m = monster(h, key, 1.5 + (i % 4) * 2, 2, 1.5 + (i / 4) * 4, false);
            spawned.add(m);
            near(h, m.getMaxHealth(), k.hp(), 1e-3, key + " max hp");
            near(h, m.getBbWidth(), k.half() * 2, 1e-3, key + " width");
            near(h, m.getBbHeight(), k.height(), 1e-3, key + " height");
            h.assertTrue(("§eLv." + k.level() + " §f" + k.name()).equals(Monsters.labelOf(m)), key + " label " + Monsters.labelOf(m));
            h.assertTrue(!Monsters.buried(m), key + " not buried at spawn");
        }
        ok("data: order · regions · entities · eggs · loot · repeat quests · sounds · animations · textures · spawn");
        for (RpgMonster m : spawned) m.discard();
        h.succeed();
    }

    // ------------------------------------------------------------ 특성

    /** 니세: 때린 뒤 노린 사람 반대쪽으로 깡충 (1칸 넘게 물러남) — 다음 공격 · 달려들기는 pause 틱 뒤. 피해 = attack */
    @GameTest(environment = "mmorpg:monster", maxTicks = 200)
    public void nisseHopsBack(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("nisse");
        RpgMonster m = monster(h, "nisse", 1.5 + k.half() + 0.3 + 0.8, 2, 1.5, false); // 몸 가장자리 사이 0.8칸 (reach 1.2 안)
        float[] hp0 = {-1};
        long[] hopAt = {-1};
        double[] dist = {0, 0}; // [깡충 뛴 때 거리, 그 뒤 12틱 안 가장 먼 거리]
        long[] pause = {-1};
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            h.assertTrue(Monsters.fightOf(m) != null, "fight");
            m.setDeltaMovement(Vec3.ZERO); // 맞아서 밀린 것은 지움 (깡충만 보게)
            m.setNoAi(false); // 깡충은 땅 위의 몸 (물리) 이 있어야
        });
        h.onEachTick(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            if (f == null || m.isRemoved()) return;
            double d = flat(m.position(), sp.position());
            if (hopAt[0] < 0 && f.hops() >= 1) {
                hopAt[0] = h.getTick();
                dist[0] = d;
                dist[1] = d;
                pause[0] = f.nextAttack() - Tasks.now();
            } else if (hopAt[0] >= 0 && h.getTick() - hopAt[0] <= 12) dist[1] = Math.max(dist[1], d);
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && hopAt[0] >= 0 && h.getTick() - hopAt[0] > 12, "hopped " + (f == null ? null : f.hops()));
            h.assertTrue(f.hits() >= 1 && "melee".equals(f.lastKind()), "melee hit before the hop " + f.hits() + " " + f.lastKind());
            near(h, hp0[0] - sp.getHealth(), scaled(h, k.attack()) * f.hits(), 1e-3, "nisse damage");
            h.assertTrue(dist[1] - dist[0] > 1.0, "hopped back " + dist[0] + " → " + dist[1]);
            h.assertTrue(pause[0] >= k.hop().pause() - 3, "next attack after the pause " + pause[0]);
            h.assertTrue(played(m, "hop") || played(m, "attack"), "hop animation " + m.syncedAnims());
            ok("nisse hop back " + String.format(java.util.Locale.ROOT, "%.2f → %.2f", dist[0], dist[1]) + " · pause " + pause[0]);
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    /**
     * 광폭 멧돼지 돌진 (사거리 밖 · 돌진 범위 안에서 맞으면): 앞발 긁기 (prep — 멈춰 섬 · 걷지 않음) → 달림 (dash — 처음 노린 쪽으로) → 부딪히면 피해 × 1.4 ·
     * 달리던 쪽으로 밀쳐냄 (가로 knock · 위 lift) → 돌진 끝
     */
    @GameTest(environment = "mmorpg:monster", maxTicks = 220)
    public void boarCharges(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("boar");
        Monsters.Charge c = k.charge();
        RpgMonster m = monster(h, "boar", 1.5 + k.half() + 0.3 + 4.5, 2, 1.5, false); // 몸 가장자리 사이 4.5칸 (reach 1.6 밖, range 10 안)
        float[] hp0 = {-1};
        Vec3[] start = {null, null, null}; // [맞은 자리, 앞발 긁기 시작 자리, 달리기 시작 자리]
        double[] prepMove = {0};
        boolean[] seen = {false, false, true}; // [앞발 긁기, 달림, 긁는 동안 늘 멈춤 (isImmobile)]
        h.runAfterDelay(66, () -> {
            fresh(sp);
            hp0[0] = sp.getHealth();
            m.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            h.assertTrue(Monsters.fightOf(m) != null, "fight");
            m.setDeltaMovement(Vec3.ZERO);
            m.setNoAi(false); // 달리려면 몸이 움직여야
            start[0] = m.position();
        });
        h.onEachTick(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            if (f == null || m.isRemoved()) return;
            if ("prep".equals(f.chargePhase())) {
                if (!seen[0]) start[1] = m.position();
                seen[0] = true;
                prepMove[0] = Math.max(prepMove[0], flat(m.position(), start[1]));
                if (!m.isImmobile()) seen[2] = false;
            } else if ("dash".equals(f.chargePhase())) {
                if (!seen[1]) start[2] = m.position();
                seen[1] = true;
            }
        });
        h.succeedWhen(() -> {
            Monsters.Fight f = Monsters.fightOf(m);
            h.assertTrue(f != null && "charge".equals(f.lastKind()), "charge resolved " + (f == null ? null : f.lastKind()));
            h.assertTrue(f.hits() == 1 && f.misses() == 0, "charge hit the player (hits " + f.hits() + ", misses " + f.misses() + ")");
            h.assertTrue(f.chargePhase() == null && !m.isImmobile(), "charge over");
            h.assertTrue(seen[0] && seen[1], "prep then dash " + seen[0] + " " + seen[1]);
            h.assertTrue(seen[2] && prepMove[0] < 0.3, "stood still while pawing (moved " + prepMove[0] + ")");
            near(h, hp0[0] - sp.getHealth(), scaled(h, k.attack() * c.mult()), 1e-3, "charge damage ×" + c.mult());
            Vec3 dir = new Vec3(sp.getX() - start[2].x, 0, sp.getZ() - start[2].z).normalize();
            Vec3 push = f.lastPush();
            h.assertTrue(push != null && push.x * dir.x + push.z * dir.z > c.knock() * 0.9 && Math.abs(push.y - c.lift()) < 1e-6,
                    "pushed along the dash " + push + " (dir " + dir + ")");
            ok("boar charge: prep (still " + String.format(java.util.Locale.ROOT, "%.2f", prepMove[0]) + ") → dash → hit ×" + c.mult() + " · push " + push);
            m.discard();
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    /** 맞으면 붙는 효과: 모래 전갈 독 4초, 집게 게 붙잡기 (구속 IV 1.5초) · 등딱지 (플레이어 피해만 -25%), 그렌델 시듦 3초 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 120)
    public void onHitEffectsAndShell(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        RpgMonster scorpion = monster(h, "sand_scorpion", 4.5, 2, 1.5, false);
        RpgMonster crab = monster(h, "crab", 4.5, 2, 4.5, false);
        RpgMonster grendel = monster(h, "grendel", 1.5, 2, 4.5, false);
        Monsters.Kind ck = Monsters.kind("crab");
        crab.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 10F);
        near(h, crab.getHealth(), ck.hp() - 10 * (1 - ck.shell()), 1e-3, "crab shell: player damage -" + (int) Math.round(ck.shell() * 100) + "%");
        crab.damageCooldownTime = 0;
        crab.setInvulnerableTime(0);
        crab.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 10F);
        near(h, crab.getHealth(), ck.hp() - 10 * (1 - ck.shell()) - 10, 1e-3, "crab shell: other damage as is");
        h.runAfterDelay(66, () -> {
            fresh(sp);
            h.assertTrue(Monsters.monsterHit(scorpion, Monsters.kind("sand_scorpion"), sp, 1), "scorpion hit");
            effect(h, sp, MobEffects.POISON, 0, 80, "scorpion: poison 4s");
            sp.removeEffect(MobEffects.POISON);
            fresh(sp);
            h.assertTrue(Monsters.monsterHit(crab, ck, sp, 1), "crab hit");
            effect(h, sp, MobEffects.SLOWNESS, 3, 30, "crab: slowness IV 1.5s (held)");
            h.assertFalse(Monsters.rooted(sp), "held, not rooted (slowness IV < VII)");
            sp.removeEffect(MobEffects.SLOWNESS);
            fresh(sp);
            h.assertTrue(Monsters.monsterHit(grendel, Monsters.kind("grendel"), sp, 1), "grendel hit");
            effect(h, sp, MobEffects.WITHER, 0, 60, "grendel: wither 3s");
            sp.removeEffect(MobEffects.WITHER);
            ok("scorpion poison · crab hold + shell · grendel wither");
            for (RpgMonster x : List.of(scorpion, crab, grendel)) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    /**
     * 웜 유충 매복: 싸우지 않고 rest 틱이 지나면 모래 속으로 (속성 rpg:buried — 충돌 상자 키가 모래 둔덕만 · 이름표 없음 · 걷지 않음 · buried 동작),
     * 플레이어가 detect 칸 안에 오면 튀어나와 그 사람에게 덤빔 (emerge 동작 · 이름표 다시), 숨은 채 맞으면 때린 사람에게 튀어나옴 (첫 공격은 emerge 틱 뒤)
     */
    @GameTest(environment = "mmorpg:monster", maxTicks = 220)
    public void wormLarvaAmbush(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("worm_larva");
        Monsters.Burrow b = k.burrow();
        RpgMonster lurker = monster(h, "worm_larva", 7.5, 2, 6.5, false); // 플레이어에서 7.8칸 (detect 5 밖)
        RpgMonster hit = monster(h, "worm_larva", 7.5, 2, 1.5, false); // 6칸
        h.runAfterDelay(b.rest() + 18, () -> {
            for (RpgMonster m : List.of(lurker, hit)) {
                h.assertTrue(Monsters.buried(m), "buried after resting " + b.rest() + " ticks");
                near(h, m.getBbHeight(), 0.25 * k.scale(), 1e-3, "buried: low hit box");
                h.assertTrue(Monsters.labelOf(m) == null && Monsters.labelEntity(m) == null, "buried: no name tag");
                h.assertTrue(m.isImmobile() && played(m, "buried"), "buried: does not walk · buried pose " + m.syncedAnims());
                h.assertTrue(Monsters.fightOf(m) == null, "buried: no fight");
            }
            // 숨은 채 맞음 (멀리서) → 때린 사람에게 튀어나옴
            hit.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            Monsters.Fight f = Monsters.fightOf(hit);
            h.assertTrue(!Monsters.buried(hit) && f != null && f.player() == sp, "hit while buried: emerges at the hitter");
            h.assertTrue(f.nextAttack() >= Tasks.now() + b.emerge() - 1, "first attack after emerging " + (f.nextAttack() - Tasks.now()));
            near(h, hit.getBbHeight(), k.height(), 1e-3, "emerged: full hit box");
            h.assertTrue(played(hit, "emerge"), "emerge animation " + hit.syncedAnims());
            // 가까이 감 (3칸) → 튀어나와 덤빔
            sp.snapTo(lurker.getX() - 3, sp.getY(), lurker.getZ(), 0F, 0F);
            h.runAfterDelay(10, () -> {
                Monsters.Fight g = Monsters.fightOf(lurker);
                h.assertTrue(!Monsters.buried(lurker) && g != null && g.player() == sp, "emerged when the player came within " + b.detect());
                near(h, lurker.getBbHeight(), k.height(), 1e-3, "emerged: full hit box");
                h.assertTrue(Monsters.labelOf(lurker) != null && Monsters.labelOf(lurker).contains(k.name()), "name tag back " + Monsters.labelOf(lurker));
                h.assertTrue(!lurker.isImmobile() && played(lurker, "emerge"), "emerged: can move · emerge animation");
                ok("worm larva: burrow after rest · low box · no tag · emerge on approach / on hit");
                for (RpgMonster m : List.of(lurker, hit)) m.discard();
                RpgGearTests.done(h, sp);
            });
        });
    }

    /**
     * 서리 늑대 무리: 처음 맞은 늑대가 울부짖고 (howl 동작) 둘레 radius 칸의 싸우지 않던 서리 늑대도 때린 사람에게 덤빔 (조금씩 늦게 울부짖음),
     * 다른 몬스터 (드라우그) 는 부르지 않음, 이미 싸우는 늑대가 맞아도 다시 부르지 않음. 물리면 구속 II 2초 (냉기)
     */
    @GameTest(environment = "mmorpg:monster", maxTicks = 120)
    public void frostWolfPack(GameTestHelper h) {
        ServerPlayer sp = player(h, 1.5, 2, 1.5, 300);
        Monsters.Kind k = Monsters.kind("frost_wolf");
        RpgMonster a = monster(h, "frost_wolf", 4.5, 2, 1.5, false);
        RpgMonster b = monster(h, "frost_wolf", 4.5, 2, 4.5, false);
        RpgMonster c = monster(h, "frost_wolf", 7.5, 2, 6.5, false);
        RpgMonster draugr = monster(h, "draugr", 1.5, 2, 5.5, false);
        h.runAfterDelay(66, () -> {
            fresh(sp);
            a.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            Monsters.Fight fa = Monsters.fightOf(a);
            h.assertTrue(fa != null && fa.packCalled() >= 2, "howl called the pack " + (fa == null ? null : fa.packCalled()));
            for (RpgMonster w : List.of(b, c)) {
                Monsters.Fight fw = Monsters.fightOf(w);
                // getTarget 은 가짜 플레이어를 크리에이티브로 보아 비움 (asValidTarget) → 정해 둔 대상 그대로
                h.assertTrue(fw != null && fw.player() == sp && w.getTargetUnchecked() == sp, "pack wolf joins the fight " + (fw == null ? null : fw.player()) + " " + w.getTargetUnchecked());
                h.assertTrue(fw.packCalled() == -1, "called wolves do not howl for more");
            }
            h.assertTrue(Monsters.fightOf(draugr) == null, "other monsters are not called");
            h.assertTrue(played(a, "howl"), "howl animation " + a.syncedAnims());
            // 이미 싸우는 늑대가 맞아도 다시 부르지 않음
            b.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), 1F);
            h.assertTrue(Monsters.fightOf(b).packCalled() == -1, "no second howl");
            fresh(sp);
            h.assertTrue(Monsters.monsterHit(a, k, sp, 1), "wolf bite");
            effect(h, sp, MobEffects.SLOWNESS, 1, 40, "frost wolf: slowness II 2s");
            sp.removeEffect(MobEffects.SLOWNESS);
            h.runAfterDelay(14, () -> {
                h.assertTrue(played(b, "howl") && played(c, "howl"), "called wolves howl a little later");
                ok("frost wolf pack: howl calls " + fa.packCalled() + " · draugr not called · cold bite");
                for (RpgMonster m : List.of(a, b, c, draugr)) m.discard();
                RpgGearTests.done(h, sp);
            });
        });
    }

    /** 서리 거인 내려치기: 맞은 사람 둘레 radius 칸의 다른 플레이어도 절반 피해 (얼음 충격파), 둘 다 구속 II · 둘레 밖은 그대로 */
    @GameTest(environment = "mmorpg:monster", maxTicks = 200)
    public void frostGiantIceShock(GameTestHelper h) {
        Monsters.Kind k = Monsters.kind("frost_giant");
        ServerPlayer a = player(h, 1.5, 2, 1.5, 300);
        ServerPlayer b = player(h, 1.5, 2, 3.3, 300); // a 에서 1.8칸 (radius 2.6 안)
        ServerPlayer c = player(h, 1.5, 2, 5.0, 300); // 3.5칸 (밖)
        RpgMonster giant = monster(h, "frost_giant", 1.5 + k.half() + 0.3 + 1.5, 2, 1.5, false);
        h.runAfterDelay(66, () -> {
            for (ServerPlayer p : List.of(a, b, c)) fresh(p);
            giant.hurtServer(h.getLevel(), a.damageSources().playerAttack(a), 1F);
            float a0 = a.getHealth();
            float b0 = b.getHealth();
            float c0 = c.getHealth();
            h.runAfterDelay(k.windup() + 12, () -> {
                Monsters.Fight f = Monsters.fightOf(giant);
                h.assertTrue(f != null && f.hits() == 1 && "melee".equals(f.lastKind()), "giant hit a " + (f == null ? null : f.hits()));
                near(h, a0 - a.getHealth(), scaled(h, k.attack()), 1e-3, "giant hit");
                near(h, b0 - b.getHealth(), scaled(h, k.attack() * k.shock().mult()), 1e-3, "ice shock: half to the player beside");
                near(h, c0 - c.getHealth(), 0, 1e-6, "ice shock: nothing outside the radius");
                // 맞은 지 몇 틱 지남 (공격 동작 판정은 2틱마다) — 세기와 남은 길이 (2초 이하) 만
                for (ServerPlayer p : List.of(a, b)) {
                    MobEffectInstance x = p.getEffect(MobEffects.SLOWNESS);
                    h.assertTrue(x != null && x.getAmplifier() == 1 && x.getDuration() > 20 && x.getDuration() <= 40, "giant: slowness II 2s (target · beside) " + x);
                }
                h.assertTrue(c.getEffect(MobEffects.SLOWNESS) == null, "no slowness outside the radius");
                ok("frost giant ice shock: target " + (a0 - a.getHealth()) + " · beside " + (b0 - b.getHealth()) + " · outside 0");
                giant.discard();
                for (ServerPlayer p : List.of(b, c)) h.getLevel().getServer().getPlayerList().remove(p);
                RpgGearTests.done(h, a);
            });
        });
    }
}
