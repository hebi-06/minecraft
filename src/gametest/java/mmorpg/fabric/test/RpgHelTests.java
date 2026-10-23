package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.Armor;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossHel;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.gear.ArmorSystem;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.35.0 게임 시험 (서버) — 헬 (BossHel, Lv.58): 등장 (제단 앞 안개에서 — 무적 · 체력 2,200,000 (0.36.0 — 규칙 BOSSES.hel.hp) · 헬헤임의 문까지 막대), 움직임 (떠서 느리게 다가옴 · 붙으면 물러남),
 * 죽음의 손톱 (선딜레이 동안은 안 맞음 → 앞 부채꼴만), 망자의 손 (발밑 원 — 비키면 무사), 삶과 죽음의 경계 (검은 오른손 쪽 반쪽만), 굘 강의 물결 (띠 위를 물결이 쓸고 지나감 —
 * 앞에 선 사람부터), 헬의 시선 (가장 먼 사람을 따라가다 멈춤 → 멈춘 뒤 비키면 무사 · 줄 위 다른 사람은 맞음), 안개 걸음 (안 보임 · 무적 → 등 뒤 → 손톱),
 * 각성 (체력 절반 → 무적 울부짖음 → 선딜레이 ×0.85 · 재사용 ×0.8 · rpg:phase 1 · 걀라르 다리 예약), 걀라르 다리 (다리 위는 무사 · 밖은 피해),
 * 제한 시간 (막대 붉게 → 헬헤임의 문 즉사), 58렙 탱커 두 방 (0.36.0 기준 세팅 — 공허 갑옷 · 55렙 초월 무기: 방패 · 전투 망치는 첫 방을 버티고 둘째에 쓰러짐, 칼은 한 방 — 웅크려 막아도 막기 없음),
 * 처치 (보상 · 보스 재료 · 재사용 30분 · 막대 치움). 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단. 시험마다 멀리 (x -60_000 - 112 × 번호, z 68_000).
 * 통과하면 [hel-test] OK 줄. 한 방 1,300 (0.36.0 — 0.35.0 은 1,200) 을 재려고 싸우는 사람은 체력 스탯 3000 (최대 체력 3,000 남짓).
 */
public final class RpgHelTests {
    private static final String KEY = "hel";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-hel-test");
    private static final int R = 24;
    private static final long FAR = Long.MAX_VALUE / 4;
    /** 소환 (60틱) + 등장 (ARRIVE 틱) 이 끝나는 때 */
    private static final int ARRIVED = 60 + BossHel.ARRIVE + 4;
    private static final double SOUTH = Math.PI / 2;
    private static final int VIT = 3000;
    /** 0.36.0 기준 세팅의 갑옷 레벨 (공허의 갑옷 — 초월 50렙) */
    private static final int ARMOR_LEVEL = 50;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        return RpgNidhoggTests.field(level, KEY, new BlockPos(-60_000 - slot * 112, 150, 68_000), R, hold);
    }

    /** 체력 스탯을 크게 (한 방 1,300 도 버팀 — 받은 피해를 잼) · 가득 채움 */
    private static ServerPlayer fighter(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "vit", VIT);
        sp.setHealth(sp.getMaxHealth());
        return sp;
    }

    /** 스스로 패턴 · 안개 걸음 · 걀라르 다리 · 원거리 견제를 고르지 않게 (pause = 틀 — 움직이지도 않음) */
    private static void freeze(Bosses.Enc enc, boolean pause) {
        BossHel.M m = BossHel.mem(enc);
        m.next.replaceAll((k, v) -> FAR);
        for (String id : BossHel.PATTERNS) m.next.put(id, FAR);
        m.nextAttack = FAR;
        m.blinkAt = FAR;
        m.bridgeAt = FAR;
        enc.farAt = FAR;
        if (pause) Bosses.cast(enc, "pause", 100000);
    }

    /** 틀을 비우고 패턴을 바로 부름 */
    private static void unpause(Bosses.Enc enc) {
        enc.cast = null;
    }

    private static void fresh(ServerPlayer sp) {
        RpgMonsterTests.fresh(sp);
    }

    private static void near(GameTestHelper h, double got, double want, double eps, String what) {
        RpgMonsterTests.near(h, got, want, eps, what);
    }

    /** 보스 쪽에서 오는 공격 (난이도 배율) */
    private static double scaled(GameTestHelper h, double a) {
        return RpgMonsterTests.scaled(h, a);
    }

    private static Obj def() {
        return Arena.BOSSES.obj(KEY);
    }

    /** 체력을 채우고 무적 시간 없이 · 그 자리에 (밀려난 것도 되돌림) */
    private static float ready(ServerPlayer sp, Arena.Altar a, double dx, double dz) {
        RpgNidhoggTests.put(sp, a, dx, dz);
        sp.setDeltaMovement(Vec3.ZERO);
        sp.setHealth(sp.getMaxHealth());
        fresh(sp);
        return sp.getHealth();
    }

    /**
     * 58렙 한 사람 (헬의 대상 레벨): 손에 무기 · 40렙 초월 갑옷 4벌 (종류 armor). 58렙까지 받은 스탯 포인트는 무기 · 갑옷 요구 스탯을 채우고 남은 것을 dump 에 몰아줌
     */
    private static ServerPlayer level58(GameTestHelper h, String weapon, String armor, String dump) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(58);
        RpgPlayers.checkLevelUp(sp);
        int points = RpgData.of(sp).point();
        ItemStack held = new ItemStack(RpgItems.byId(weapon));
        Weapon w = RpgCombat.weaponOf(held);
        Map<String, Integer> need = new HashMap<>();
        for (Weapons.Req q : w.reqs()) need.merge(q.stat(), (int) Math.ceil(q.value()), Math::max);
        List<String> ids = new ArrayList<>();
        for (String piece : Armor.PIECE_ORDER) {
            Armor.Piece p = Armor.armorFrom(armor, ARMOR_LEVEL, "transcend", piece);
            for (Weapons.Req q : p.reqs()) need.merge(q.stat(), (int) Math.ceil(q.value()), Math::max);
            ids.add(Armor.armorId(armor, ARMOR_LEVEL, "transcend", piece));
        }
        int used = 0;
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            if (e.getKey().equals(dump)) continue;
            RpgPlayers.setStat(sp, e.getKey(), e.getValue());
            used += e.getValue();
        }
        RpgPlayers.setStat(sp, dump, Math.max(need.getOrDefault(dump, 0), points - used));
        sp.setItemInHand(InteractionHand.MAIN_HAND, held);
        double[] prot = {0};
        for (int i = 0; i < 4; i++) {
            ItemStack s = new ItemStack(GearItems.byId(ids.get(i)));
            sp.setItemSlot(SLOTS[i], s);
            EquipmentSlot slot = SLOTS[i];
            s.forEachModifier(slot, (attr, mod) -> {
                if (attr.value() == Attributes.ARMOR.value()) prot[0] += mod.amount();
            });
        }
        // 가짜 플레이어는 몸 틱 (장비 속성 반영) 을 돌지 않음 — 입은 갑옷의 방어를 속성에 바로 넣음 (게임에서는 입으면 들어가는 값)
        AttributeInstance armorAttr = sp.getAttribute(Attributes.ARMOR);
        if (armorAttr != null && sp.getArmorValue() < prot[0]) armorAttr.setBaseValue(prot[0]);
        ArmorSystem.armorTick(sp);
        RpgHealth.refreshMax(sp, false);
        sp.setHealth(sp.getMaxHealth());
        return sp;
    }

    // ------------------------------------------------------------ 시험

    /** 등장: 소환 → 제단 앞 안개에서 솟아오름 (무적 · 틀 arrive) · 체력 1,400,000 · 엔진 평타 없음 · 헬헤임의 문까지 막대 (10분) · 보스 막대 이름 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 300)
    public void helArrives(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 10);
        h.assertTrue(Bosses.module(KEY) instanceof BossHel, "hel module registered");
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null, "summoned");
        Vec3 c = Arena.arenaCenter(a);
        h.runAfterDelay(63, () -> {
            h.assertTrue("fight".equals(enc.state) && enc.boss != null, "fight " + enc.state);
            BedrockMob b = enc.boss;
            float hp = (float) def().num("hp");
            h.assertTrue(hp == 2200000F && b.getMaxHealth() == hp && b.getHealth() == hp, "hp " + b.getHealth() + " / " + b.getMaxHealth() + " (rules " + hp + ")");
            h.assertTrue(enc.cast != null && "arrive".equals(enc.cast.id) && enc.immune, "rises from the mist (immune)");
            // 제단 앞 (부른 사람 쪽으로 ALTAR_OFF 칸 — 가운데 제단 블록에 묻히지 않게)
            Vec3 front = new Vec3(c.x, c.y, c.z + BossHel.ALTAR_OFF);
            h.assertTrue(BossKit.hdist(b.position(), front) < 1e-6 && Math.abs(b.getY() - c.y) < 1e-6, "in front of the altar " + b.position());
            h.assertTrue(enc.meleeAt == Long.MAX_VALUE, "engine melee off");
            long left = BossHel.timeLeft(enc, Tasks.now());
            h.assertTrue(left > 12000 - 10 && left <= 12000, "10 minute limit " + left);
            h.assertTrue(BossHel.timerOf(enc) != null, "gate timer bar");
            String label = BossKit.bossLabel(58, "헬헤임의 주인", "헬");
            h.assertTrue(label.equals(MobData.get("rpg:boss_hel").bossName()), "boss bar name " + MobData.get("rpg:boss_hel").bossName());
        });
        h.runAfterDelay(ARRIVED + 2, () -> {
            h.assertTrue(!enc.immune, "hittable after arriving");
            ServerBossEvent bar = BossHel.timerOf(enc);
            h.assertTrue(bar.getPlayers().contains(p1), "timer bar shown to the fighter");
            h.assertTrue(bar.getName().getString().contains("헬헤임의 문까지") && bar.getColor() == BossEvent.BossBarColor.BLUE, "timer " + bar.getName().getString());
            LOG.info("[hel-test] OK summon (hp {}) rises in front of the altar (immune) → fight, gate timer bar 10:00", (long) def().num("hp"));
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 움직임: 떠서 느리게 다가옴 (틱마다 glide 칸 · rpg:gait) → 너무 붙으면 조금 물러남 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helGlidesSlowly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 14);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj D = def().obj("drive");
        double[] d0 = new double[1];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, false);
            BossHel.setBody(enc, c.x, c.z, SOUTH);
            RpgNidhoggTests.put(p1, a, 0, 14);
            d0[0] = BossKit.hdist(enc.boss.position(), p1.position());
        });
        h.runAfterDelay(ARRIVED + 40, () -> {
            double d = BossKit.hdist(enc.boss.position(), p1.position());
            double moved = d0[0] - d;
            double most = D.num("glide") * 41;
            h.assertTrue(moved > most * 0.6 && moved <= most + 1e-6, String.format(Locale.ROOT, "glides slowly toward the player %.2f → %.2f (at most %.2f)", d0[0], d, most));
            h.assertTrue(enc.boss.prop("rpg:gait") > 0.5, "gait " + enc.boss.prop("rpg:gait"));
            Vec3 b = enc.boss.position();
            p1.snapTo(b.x, a.y, b.z + 2.0, 0F, 0F);
        });
        h.runAfterDelay(ARRIVED + 72, () -> {
            double d = BossKit.hdist(enc.boss.position(), p1.position());
            h.assertTrue(d > 2.5, "backs off when crowded " + d);
            LOG.info("[hel-test] OK glides {} blocks per tick toward the far player → backs off when crowded ({})", D.num("glide"), String.format(Locale.ROOT, "%.2f", d));
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 죽음의 손톱: 선딜레이 (40틱) 동안은 안 맞음 → 앞 부채꼴만 1,300 (뒤는 무사) */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helClawFan(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        RpgNidhoggTests.put(p1, a, 0, 4);
        RpgNidhoggTests.put(p2, a, 0, -5);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("claw");
        int wu = (int) d.num("windup");
        float[] hp = new float[2];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z, SOUTH);
            hp[0] = ready(p1, a, 0, 4);
            hp[1] = ready(p2, a, 0, -5);
            unpause(enc);
            h.assertTrue(BossHel.claw(enc, Tasks.now(), BossKit.fighters(enc)), "claw starts");
            h.assertTrue(BossHel.windup(enc, d.num("windup")) == wu, "windup " + wu);
        });
        h.runAfterDelay(ARRIVED + wu - 1, () -> h.assertTrue(p1.getHealth() == hp[0], "not hit during the windup"));
        h.runAfterDelay(ARRIVED + wu + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, d.num("damage")), 1e-3, "claw hits the fan");
            h.assertTrue(p2.getHealth() == hp[1], "behind Hel is safe");
            LOG.info("[hel-test] OK death claw: windup {} ticks safe → fan {} (behind safe)", wu, d.num("damage"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 망자의 손: 싸우는 사람 모두의 발밑에 원 → 원을 벗어난 사람은 무사, 남은 사람은 1,300 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helDeadHands(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        RpgNidhoggTests.put(p1, a, 3, 3);
        RpgNidhoggTests.put(p2, a, -4, 6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("hands");
        int wu = (int) d.num("windup");
        float[] hp = new float[2];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z - 6, SOUTH);
            hp[0] = ready(p1, a, 3, 3);
            hp[1] = ready(p2, a, -4, 6);
            unpause(enc);
            h.assertTrue(BossHel.hands(enc, Tasks.now(), BossKit.fighters(enc)), "dead hands start");
        });
        // p2 는 원 밖으로 (6칸)
        h.runAfterDelay(ARRIVED + wu - 12, () -> RpgNidhoggTests.put(p2, a, -4, 12));
        h.runAfterDelay(ARRIVED + wu + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, d.num("damage")), 1e-3, "the hand rises under the one who stayed");
            h.assertTrue(p2.getHealth() == hp[1], "stepped out of the circle: safe");
            LOG.info("[hel-test] OK dead hands: a circle under everyone → stayed {} · stepped out safe", d.num("damage"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 삶과 죽음의 경계: 가운데를 가르는 선 → 검은 오른손이 가리키는 반쪽 (+z) 만 1,300, 반대쪽은 무사 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helDivideHalf(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        RpgNidhoggTests.put(p1, a, 2, 6);
        RpgNidhoggTests.put(p2, a, -2, -6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("divide");
        int wu = (int) d.num("windup");
        float[] hp = new float[2];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x + 3, c.z, SOUTH);
            hp[0] = ready(p1, a, 2, 6);
            hp[1] = ready(p2, a, -2, -6);
            unpause(enc);
            h.assertTrue(BossHel.divideAt(enc, SOUTH), "divide starts (+z half)");
            BossHel.M m = BossHel.mem(enc);
            // 몸의 오른쪽 (검은 손) 이 죽음 쪽을 봄
            Vec3 right = BossHel.rightOf(m.body.want);
            h.assertTrue(right.z > 0.99, "the dark right hand points at the deadly half " + right);
            h.assertTrue(BossHel.deathSide(enc, p1.position()) && !BossHel.deathSide(enc, p2.position()), "sides");
        });
        h.runAfterDelay(ARRIVED + wu - 1, () -> h.assertTrue(p1.getHealth() == hp[0], "not hit before the line closes"));
        h.runAfterDelay(ARRIVED + wu + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, d.num("damage")), 1e-3, "the deadly half is struck");
            h.assertTrue(p2.getHealth() == hp[1], "the pale half is safe");
            LOG.info("[hel-test] OK life and death: the dark hand's half {} · the pale half safe", d.num("damage"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 굘 강의 물결: 가로지르는 띠 (폭 7) → 물결이 띠를 따라 흘러 앞에 선 사람부터 1,300 (한 번씩) · 띠 밖은 무사 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helRiverFlows(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 5, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        ServerPlayer p3 = fighter(h);
        RpgNidhoggTests.put(p1, a, 5, 1);
        RpgNidhoggTests.put(p2, a, 0, 6);
        RpgNidhoggTests.put(p3, a, -8, -2);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("river");
        int wu = (int) d.num("windup");
        int flow = (int) d.num("flow");
        float[] hp = new float[3];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z - 10, SOUTH);
            hp[0] = ready(p1, a, 5, 1);
            hp[1] = ready(p2, a, 0, 6);
            hp[2] = ready(p3, a, -8, -2);
            unpause(enc);
            h.assertTrue(BossHel.riverAt(enc, 0, 0, 1), "river starts (band along +x, flowing +x)");
        });
        // 물결은 -x 끝에서 +x 로: x = -8 이 먼저, x = 5 는 나중
        h.runAfterDelay(ARRIVED + wu + 7, () -> {
            near(h, hp[2] - p3.getHealth(), scaled(h, d.num("damage")), 1e-3, "the wave reaches x -8 first");
            h.assertTrue(p1.getHealth() == hp[0], "not yet at x 5");
        });
        h.runAfterDelay(ARRIVED + wu + flow + 2, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, d.num("damage")), 1e-3, "then x 5 (once)");
            near(h, hp[2] - p3.getHealth(), scaled(h, d.num("damage")), 1e-3, "x -8 only once");
            h.assertTrue(p2.getHealth() == hp[1], "outside the band is safe");
            LOG.info("[hel-test] OK river of Gjoll: the wave sweeps along the band (front first, once each) · outside safe");
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2, p3);
        });
    }

    /** 헬의 시선: 가장 먼 사람을 따라가다 멈춤 (windup - lock) → 멈춘 뒤 비킨 그 사람은 무사 · 줄 위에 남은 다른 사람은 1,300 · 줄 밖은 무사 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helGazeLocks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 6, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        ServerPlayer p3 = fighter(h);
        RpgNidhoggTests.put(p1, a, 3, 2);
        RpgNidhoggTests.put(p2, a, 0, 14);
        RpgNidhoggTests.put(p3, a, 0, 9);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("gaze");
        int wu = (int) d.num("windup");
        int lock = (int) d.num("lock");
        float[] hp = new float[3];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z, SOUTH);
            hp[0] = ready(p1, a, 3, 2);
            hp[1] = ready(p2, a, 0, 14);
            hp[2] = ready(p3, a, 0, 9);
            unpause(enc);
            h.assertTrue(BossHel.gaze(enc, Tasks.now(), BossKit.fighters(enc)), "gaze starts (the farthest)");
        });
        // 멈춘 뒤 대상이 옆으로 비킴
        h.runAfterDelay(ARRIVED + wu - lock + 3, () -> {
            Vec3 g = BossHel.mem(enc).lastGaze;
            h.assertTrue(g != null && g.z > 0.99, "locked on the farthest " + g);
            RpgNidhoggTests.put(p2, a, 5, 14);
        });
        h.runAfterDelay(ARRIVED + wu + 1, () -> {
            h.assertTrue(p2.getHealth() == hp[1], "the target stepped aside after the lock: safe");
            near(h, hp[2] - p3.getHealth(), scaled(h, d.num("damage")), 1e-3, "the beam hits whoever stays on the line");
            h.assertTrue(p1.getHealth() == hp[0], "off the line is safe");
            LOG.info("[hel-test] OK gaze: follows the farthest → locks {} ticks before → the beam ({}) misses the one who moved, hits the line", lock, d.num("damage"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2, p3);
        });
    }

    /** 안개 걸음: 사라짐 (안 보임 · 무적) → 대상 (가장 가까운 사람이 아닌 쪽) 의 등 뒤 3칸 → 죽음의 손톱 1,300 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 500)
    public void helMistStep(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 7, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        RpgNidhoggTests.put(p1, a, 0, 8);
        RpgNidhoggTests.put(p2, a, 2, -2);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj b = def().obj("blink");
        int vanish = (int) b.num("vanish");
        int hidden = (int) b.num("hidden");
        int wu = (int) def().obj("claw").num("windup");
        float[] hp = new float[1];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z, SOUTH);
            // p1 은 북쪽 (-z) 을 봄 → 등 뒤 = +z. 바라보는 쪽은 머리 방향 (snapTo 는 몸 방향만)
            p1.snapTo(c.x, a.y, c.z + 8, 180F, 0F);
            p1.setYHeadRot(180F);
            p1.setHealth(p1.getMaxHealth());
            fresh(p1);
            hp[0] = p1.getHealth();
            RpgNidhoggTests.put(p2, a, 2, -2);
            unpause(enc);
            h.assertTrue(BossHel.blink(enc, Tasks.now(), BossKit.fighters(enc)), "mist step starts");
        });
        h.runAfterDelay(ARRIVED + vanish + 2, () -> {
            h.assertTrue(enc.boss.isInvisible() && enc.immune && BossHel.mem(enc).vanished, "in the mist: invisible + immune");
            float hp0 = enc.boss.getHealth();
            RpgNidhoggTests.hit(p2, enc.boss, 50F);
            h.assertTrue(enc.boss.getHealth() == hp0, "cannot be hit in the mist");
        });
        h.runAfterDelay(ARRIVED + vanish + hidden + 1, () -> {
            Vec3 behind = new Vec3(c.x, c.y, c.z + 8 + b.num("behind"));
            h.assertTrue(!enc.boss.isInvisible() && !enc.immune, "out of the mist");
            h.assertTrue(BossKit.hdist(enc.boss.position(), behind) < 0.3, "behind the target " + enc.boss.position() + " vs " + behind);
        });
        h.runAfterDelay(ARRIVED + vanish + hidden + 6 + wu + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, def().obj("claw").num("damage")), 1e-3, "claw from behind");
            LOG.info("[hel-test] OK mist step (invisible + immune → {} behind the target → death claw)", b.num("behind"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 각성 「망자의 여왕」: 체력 절반 아래 → 울부짖음 (무적) → 선딜레이 ×0.85 (40 → 34) · 재사용 ×0.8 · rpg:phase 1 · 걀라르 다리 예약 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helAwakens(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 8, hold);
        ServerPlayer p1 = fighter(h);
        RpgNidhoggTests.put(p1, a, 0, 5);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj P = def().obj("phase2");
        int roar = (int) P.num("roar");
        int wu = (int) Math.round(40 * P.num("windup"));
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z, SOUTH);
            BedrockMob b = enc.boss;
            b.setHealth(b.getMaxHealth() * 0.5F + 10);
            RpgNidhoggTests.hit(p1, b, 100F);
            h.assertTrue(BossHel.mem(enc).awakenPending, "half hp → awakening next");
            unpause(enc);
        });
        h.runAfterDelay(ARRIVED + 3, () -> h.assertTrue(enc.cast != null && "awaken".equals(enc.cast.id) && enc.immune, "roars (immune)"));
        h.runAfterDelay(ARRIVED + 2 + roar + 2, () -> {
            BossHel.M m = BossHel.mem(enc);
            h.assertTrue(m.phase2 && !enc.immune && Math.abs(enc.cdMult - P.num("cd")) < 1e-9, "awakened (cooldowns x" + enc.cdMult + ")");
            h.assertTrue(enc.boss.propInt("rpg:phase") == 1, "queen of the dead texture");
            h.assertTrue(BossHel.windup(enc, 40) == wu, "shorter windup " + BossHel.windup(enc, 40));
            h.assertTrue(m.bridgeAt < FAR && m.bridgeAt > Tasks.now(), "gjallar bridge scheduled");
            LOG.info("[hel-test] OK awaken at half hp (immune roar) → windup {} · cd x{} · phase texture · bridge scheduled", wu, P.num("cd"));
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 걀라르 다리 (각성 뒤): 가운데 곁으로 떠 감 → 필드를 가로지르는 금빛 다리 위는 무사, 밖은 1,300 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 500)
    public void helGjallarBridge(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 9, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        RpgNidhoggTests.put(p1, a, 8, 0.5);
        RpgNidhoggTests.put(p2, a, 0, 8);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj B = def().obj("bridge");
        int jump = (int) B.num("jump");
        int wu = (int) B.num("windup");
        float[] hp = new float[2];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.mem(enc).phase2 = true;
            BossHel.setBody(enc, c.x + 6, c.z + 4, SOUTH);
            hp[0] = ready(p1, a, 8, 0.5);
            hp[1] = ready(p2, a, 0, 8);
            unpause(enc);
            h.assertTrue(BossHel.bridgeAt(enc, 0), "gjallar bridge starts (along +x)");
        });
        // 다리 위 · 가운데에서 -x 로 ALTAR_OFF 칸
        h.runAfterDelay(ARRIVED + jump + 2, () -> h.assertTrue(BossKit.hdist(enc.boss.position(), new Vec3(c.x - BossHel.ALTAR_OFF, c.y, c.z)) < 0.3,
                "floated next to the center " + enc.boss.position()));
        h.runAfterDelay(ARRIVED + jump + wu - 1, () -> h.assertTrue(p2.getHealth() == hp[1], "not hit before the count ends"));
        h.runAfterDelay(ARRIVED + jump + wu + 1, () -> {
            h.assertTrue(p1.getHealth() == hp[0], "on the bridge: untouched");
            near(h, hp[1] - p2.getHealth(), scaled(h, B.num("damage")), 1e-3, "off the bridge: struck");
            LOG.info("[hel-test] OK gjallar bridge: floats next to the center → on the bridge safe · off the bridge {}", B.num("damage"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 제한 시간: 1분 아래면 막대가 붉게 → 넘기면 헬헤임의 문 (무적 · 가운데) → windup 틱 뒤 필드 안 모두 즉사 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 400)
    public void helGateKillsAll(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 10, hold);
        ServerPlayer p1 = fighter(h);
        RpgNidhoggTests.put(p1, a, 0, 6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        int wu = (int) def().obj("enrage").num("windup");
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.shiftClock(enc, BossHel.timeLeft(enc, Tasks.now()) - 30);
        });
        h.runAfterDelay(ARRIVED + 10, () -> {
            ServerBossEvent bar = BossHel.timerOf(enc);
            h.assertTrue(bar.getColor() == BossEvent.BossBarColor.RED, "red timer in the last minute");
            h.assertTrue(!p1.isDeadOrDying(), "alive before the limit");
        });
        h.runAfterDelay(ARRIVED + 32, () -> {
            h.assertTrue(enc.cast != null && "gate".equals(enc.cast.id) && enc.immune, "the gate of helheim (immune)");
            h.assertTrue(BossHel.timerOf(enc).getName().getString().contains("헬헤임의 문"), "timer says the gate");
        });
        h.runAfterDelay(ARRIVED + 31 + wu + 3, () -> {
            h.assertTrue(p1.isDeadOrDying(), "everyone in the field dies");
            LOG.info("[hel-test] OK 10 minute limit: red timer → the gate of helheim → instant death");
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /**
     * 58렙 탱커 두 방 (사용자 "탱커 같은 애들은 2방에 죽을 정도" — 0.36.0 기준 세팅: 공허 갑옷 (초월 50렙) · 55렙 초월 무기): 방패 (체력 몰빵 · 공허 체력 갑옷 ·
     * 웅크려 막아도 막기 없음 — 체력 355 · 한 방 195) 와 전투 망치 (체력 · 힘 공허 갑옷 — 체력 290 · 한 방 260) 는 첫 손톱을 버티고 둘째에 쓰러짐, 칼 (힘 몰빵 — 136) 은
     * 첫 손톱에 쓰러짐. 방패의 깎임 = 1,300 × (1 − 방어 4% × 20) × (1 − 방패 지속 감소)
     */
    @GameTest(environment = "mmorpg:hel", maxTicks = 500)
    public void helTanksTakeTwoHits(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 11, hold);
        ServerPlayer tank = level58(h, "rpg:shield_l55_transcend", "vit", "vit");
        ServerPlayer hammer = level58(h, "rpg:hammer_l55_transcend", "vitstr", "vit");
        ServerPlayer sword = level58(h, "rpg:sword_l55_transcend", "str", "str");
        RpgNidhoggTests.put(tank, a, -1.5, 4);
        RpgNidhoggTests.put(hammer, a, 0, 4.5);
        RpgNidhoggTests.put(sword, a, 1.5, 4);
        Bosses.Enc enc = Bosses.summon(a, tank);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("claw");
        int wu = (int) d.num("windup");
        float[] hp = new float[3];
        double[] want = new double[1];
        Vec3 south = new Vec3(0, 0, 1);
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z, SOUTH);
            hp[0] = ready(tank, a, -1.5, 4);
            hp[1] = ready(hammer, a, 0, 4.5);
            hp[2] = ready(sword, a, 1.5, 4);
            // 방패는 헬 쪽 (북쪽) 을 보고 웅크림 — 막기가 되면 덜 깎임
            tank.snapTo(tank.getX(), tank.getY(), tank.getZ(), 180F, 0F);
            tank.setYHeadRot(180F);
            tank.setShiftKeyDown(true);
            h.assertTrue(tank.getArmorValue() >= 20 && hammer.getArmorValue() >= 20 && sword.getArmorValue() >= 20,
                    "void armor (armor " + tank.getArmorValue() + " · " + hammer.getArmorValue() + " · " + sword.getArmorValue() + ")");
            Weapon w = RpgCombat.weaponOf(tank.getMainHandItem());
            h.assertTrue(w != null && "shield".equals(w.type()) && RpgCombat.lackOf(tank, w).isEmpty(), "tank holds a usable shield");
            Obj S = Rules.get().obj("SHIELD");
            double vit = Stats.get(RpgStats.effective(tank), "vit");
            double passive = Math.min(S.num("passiveCap"), w.passive() + vit * S.num("passivePerVit"));
            want[0] = scaled(h, d.num("damage")) * (1 - Math.min(20, tank.getArmorValue()) * 0.04) * (1 - passive);
            LOG.info("[hel-test] lv58 hp: shield {} · hammer {} · sword {} (shield passive {})", hp[0], hp[1], hp[2], passive);
            unpause(enc);
            h.assertTrue(BossHel.clawAt(enc, south), "first claw");
        });
        float[] after = new float[2];
        h.runAfterDelay(ARRIVED + wu + 1, () -> {
            after[0] = tank.getHealth();
            after[1] = hammer.getHealth();
            near(h, hp[0] - after[0], want[0], 1e-2, "shield: armor 80% · shield passive, no block");
            h.assertTrue(!tank.isDeadOrDying() && !hammer.isDeadOrDying(), "tanks survive the first claw (shield " + after[0] + " · hammer " + after[1] + ")");
            h.assertTrue(sword.isDeadOrDying(), "the sword dies at once");
        });
        int T2 = ARRIVED + wu + 30;
        h.runAfterDelay(T2, () -> {
            freeze(enc, true);
            BossHel.setBody(enc, c.x, c.z, SOUTH);
            RpgNidhoggTests.put(tank, a, -1.5, 4);
            RpgNidhoggTests.put(hammer, a, 0, 4.5);
            tank.setDeltaMovement(Vec3.ZERO);
            hammer.setDeltaMovement(Vec3.ZERO);
            fresh(tank);
            fresh(hammer);
            unpause(enc);
            h.assertTrue(BossHel.clawAt(enc, south), "second claw");
        });
        h.runAfterDelay(T2 + wu + 1, () -> {
            h.assertTrue(tank.isDeadOrDying() && hammer.isDeadOrDying(), "the second claw kills the tanks (shield " + tank.getHealth() + " · hammer " + hammer.getHealth() + ")");
            LOG.info("[hel-test] OK lv58: shield {} → {} → dead · hammer {} → {} → dead · sword dead at once (one claw {})", hp[0], after[0], hp[1], after[1],
                    String.format(Locale.ROOT, "%.0f", scaled(h, d.num("damage"))));
            tank.setShiftKeyDown(false);
            RpgNidhoggTests.finish(h, level, a, hold, tank, hammer, sword);
        });
    }

    /** 처치: 보상 (실버 64~80 · 골드 · 경험치 300 · 헬의 손톱 · 망자의 안개) · 재사용 대기 30분 · 헬헤임의 문 막대 치움 */
    @GameTest(environment = "mmorpg:hel", maxTicks = 300)
    public void helKilledRewards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 12, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BedrockMob b = enc.boss;
            ServerBossEvent bar = BossHel.timerOf(enc);
            int xp0 = p1.totalExperience;
            b.setHealth(10F);
            h.assertTrue(RpgNidhoggTests.hit(p1, b, 50F), "killing blow");
            h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "encounter over");
            Obj L = def().obj("loot");
            int em = RpgGearTests.silver(p1);
            int claw = RpgGearTests.countIn(p1, GearItems.byId("rpg:hel_claw"));
            int mist = RpgGearTests.countIn(p1, GearItems.byId("rpg:dead_mist"));
            h.assertTrue(em >= L.numbersOrNull("emerald").get(0) && em <= L.numbersOrNull("emerald").get(1), "silver " + em);
            h.assertTrue(RpgGearTests.bossGold(p1, KEY), "boss gold " + RpgGearTests.gold(p1) + " in " + java.util.Arrays.toString(mmorpg.core.Economy.bossGold(KEY)));
            h.assertTrue(claw >= 2 && claw <= 4 && mist >= 2 && mist <= 4, "boss materials " + claw + " / " + mist);
            h.assertTrue(p1.totalExperience - xp0 == (int) L.num("xp"), "xp " + (p1.totalExperience - xp0));
            h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 1_790_000, "cooldown 30 min");
            h.assertTrue(bar.getPlayers().isEmpty(), "gate timer bar removed");
            LOG.info("[hel-test] OK kill → silver {} · hel claw {} · dead mist {} · xp · 30 min cooldown · timer removed", em, claw, mist);
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }
}
