package mmorpg.fabric.test;

import java.util.List;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.mob.MobData;
import mmorpg.fabric.mob.RpgEntities;
import mmorpg.fabric.mob.RpgMount;
import mmorpg.fabric.mount.MountDefs;
import mmorpg.fabric.mount.MountItem;
import mmorpg.fabric.mount.MountItems;
import mmorpg.fabric.mount.Mounts;
import mmorpg.fabric.mount.Riding;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 6단계 게임 시험 (서버) — 탈것 (mount.js · riding.js + 여러 탈것): 탈것 목록 · 엔티티 수치 (행동 팩 — 빠르기 · 뛰는 힘 · 오르는 높이 · 자리 · 저장 안 함),
 * 부르기 → 바로 탐 (보던 방향) · 돌려보내기 · 다시 부르기 대기 · 내리면 사라짐 · 쓰러지면 치움, 탄 동안 막기 (공격 · 스킬 · 피해 · 잡기),
 * 탈것은 다치지 않음 (/kill 은 됨), 머리 위 막힘, 내 탈것 (처음부터 가진 탈것 · 탈것 아이템으로 얻기 · 고르기 · 빼기 · 저장), 고르기 창 (탄 채로 고르면 갈아탐),
 * 부른 기록이 없는 탈것 정리 · 탄 사람이 있으면 기록, 스탯 창 단추 (mount · mount_select), 뛰기 세기.
 */
public final class RpgMountTests {
    static final String ENV = "mmorpg:mount";
    static final String WOLF = "dire_wolf";

    static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Mounts.resetCooldown(sp);
        return sp;
    }

    /** 시험용 두 번째 탈것 (다이어울프 엔티티를 함께 씀 — 아이템 없음) */
    static MountDefs.Def testMount() {
        return new MountDefs.Def("test_wolf", "시험 늑대", "rpg:dire_wolf_mount", "", false, new double[] {1, 0.5, 0.2}, "minecraft:snowflake_particle", List.of(),
                List.of(), List.of("§7시험용"), null);
    }

    private static void near(GameTestHelper h, double actual, double expected, double eps, String what) {
        h.assertTrue(Math.abs(actual - expected) <= eps, what + ": expected " + expected + ", got " + actual);
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void mountDataAndEntity(GameTestHelper h) {
        MountDefs.Def d = MountDefs.get(WOLF);
        h.assertTrue(d != null && d.starter() && d.entity().equals("rpg:dire_wolf_mount") && d.item().equals("rpg:mount_dire_wolf"), "dire wolf def " + d);
        h.assertTrue(MountItems.of(WOLF) instanceof MountItem mi && mi.key.equals(WOLF), "dire wolf item registered");
        for (MountDefs.Def m : MountDefs.all().values()) {
            h.assertTrue(RpgEntities.type(m.entity()) != null, "entity of " + m.key());
            for (MountDefs.Sound s : m.summon()) h.assertTrue(BossKit.sound(s.name()) != null, "sound " + s.name());
            for (MountDefs.Sound s : m.gone()) h.assertTrue(BossKit.sound(s.name()) != null, "sound " + s.name());
        }
        MobData.Def md = MobData.get("rpg:dire_wolf_mount");
        h.assertTrue(md != null && md.mount(), "mob data mount");
        EntityType<?> type = RpgEntities.type("rpg:dire_wolf_mount");
        h.assertTrue(!type.canSerialize(), "mount not saved");
        Vec3 seat = type.getDimensions().attachments().getNullable(EntityAttachment.PASSENGER, 0, 0F);
        h.assertTrue(seat != null, "seat attachment");
        near(h, seat.y, 1.52 + RpgEntities.SEAT_LIFT, 1e-4, "seat y");
        near(h, seat.z, -0.125, 1e-4, "seat z");
        near(h, type.getDimensions().width(), 1.6, 1e-4, "width");
        near(h, type.getDimensions().height(), 2.0, 1e-4, "height");
        RpgMount m = (RpgMount) type.create(h.getLevel(), EntitySpawnReason.COMMAND);
        near(h, m.getAttributeValue(Attributes.MOVEMENT_SPEED), 0.32, 1e-6, "speed");
        near(h, m.getAttributeValue(Attributes.JUMP_STRENGTH), 0.8, 1e-6, "jump strength");
        near(h, m.getAttributeValue(Attributes.STEP_HEIGHT), 1.0625, 1e-6, "step height");
        near(h, m.getMaxHealth(), 40, 1e-4, "health");
        h.assertTrue(type.fireImmune(), "fire immune");
        h.assertTrue(!m.showVehicleHealth(), "no vehicle hearts");
        // 뛰기 세기 (말과 같음: 90 이상 = 1, 아니면 0.4 + 0.4 × 세기 / 90)
        m.onPlayerJump(90);
        near(h, m.pendingJump(), 1.0, 1e-6, "full jump");
        m.onPlayerJump(45);
        near(h, m.pendingJump(), 0.6, 1e-6, "half jump");
        m.onPlayerJump(-5);
        near(h, m.pendingJump(), 0.4, 1e-6, "no charge");
        h.assertTrue(m.canJump(), "can jump");
        m.discard();
        h.succeed();
    }

    @GameTest(environment = ENV, maxTicks = 120)
    public void summonRideDismiss(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.setYRot(90F);
        sp.setYHeadRot(90F);
        h.assertTrue(Mounts.summon(sp), "summoned");
        h.assertTrue(sp.getVehicle() instanceof RpgMount, "riding " + sp.getVehicle());
        RpgMount m = (RpgMount) sp.getVehicle();
        h.assertTrue(Mounts.mountOf(sp) == m && Mounts.hasMount(sp) && Riding.isMounted(sp), "tracked");
        h.assertTrue(sp.getUUID().toString().equals(Props.get(m, Mounts.OWNER)), "owner prop");
        near(h, m.getYRot(), 90, 1e-3, "faces player's way");
        h.assertTrue(m.getControllingPassenger() == sp, "player controls");
        h.assertTrue(m.isImmobile(), "no AI while ridden");
        h.assertTrue(Mounts.summon(sp) && Mounts.activeCount() >= 1 && sp.getVehicle() == m, "summon while riding = same");
        h.runAfterDelay(3, () -> {
            double dy = sp.getY() - m.getY();
            h.assertTrue(dy > 0.9 && dy < 1.2, "rider height " + dy);
            // 돌려보내기 → 사라짐 · 다시 부르기 대기
            h.assertTrue(Mounts.dismiss(sp), "dismissed");
            h.assertTrue(m.isRemoved() && !sp.isPassenger(), "gone");
            h.assertTrue(Mounts.cooldownLeft(sp) == Mounts.COOLDOWN, "cooldown " + Mounts.cooldownLeft(sp));
            h.assertTrue(!Mounts.summon(sp) && sp.getVehicle() == null, "cooldown blocks summon");
            Mounts.resetCooldown(sp);
            h.assertTrue(Mounts.summon(sp) && sp.getVehicle() instanceof RpgMount, "summon again");
            RpgMount m2 = (RpgMount) sp.getVehicle();
            // 웅크려 내림 (바닐라 내리기) → 다음 틱에 사라짐
            sp.stopRiding();
            h.runAfterDelay(2, () -> {
                h.assertTrue(m2.isRemoved() && Mounts.mountOf(sp) == null, "dismount despawns");
                h.assertTrue(Mounts.cooldownLeft(sp) > 0, "cooldown after dismount");
                RpgGearTests.done(h, sp);
            });
        });
    }

    @GameTest(environment = ENV, maxTicks = 80)
    public void ridingBlocksActions(GameTestHelper h) {
        ServerPlayer sp = player(h);
        ServerLevel level = h.getLevel();
        Zombie z = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, 4.5F, 2, 4.5F);
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        z.setHealth(500);
        h.assertTrue(Mounts.summon(sp), "summoned");
        RpgMount m = (RpgMount) sp.getVehicle();
        // 공격 · 스킬 · 룬 막힘
        h.assertTrue(RpgCombat.beforeAttack(sp, z), "melee cancelled");
        h.assertTrue(Riding.blocked(sp, "skill"), "skill blocked");
        h.assertTrue(Loot.mounted.test(sp), "no kill credit");
        // 탄 사람이 주는 피해는 막힘
        z.setInvulnerableTime(0);
        boolean hit = z.hurtServer(level, level.damageSources().playerAttack(sp), 20F);
        h.assertTrue(!hit && z.getHealth() == 500F, "rider damage blocked " + z.getHealth());
        // 탈것은 맞지 않고 다치지 않음 (/kill 은 됨)
        ServerPlayer other = player(h);
        h.assertTrue(!Kit.canHit(other, m), "skills skip mounts");
        h.assertTrue(!RpgCombat.aimable(other, m), "aim skips mounts");
        h.assertTrue(!m.hurtServer(level, level.damageSources().playerAttack(other), 30F) && m.getHealth() == m.getMaxHealth(), "mount immune");
        h.assertTrue(!m.hurtServer(level, level.damageSources().inFire(), 5F) && !m.hurtServer(level, level.damageSources().fall(), 50F), "fire · fall immune");
        // 타지 않으면 그대로
        Mounts.dismiss(sp);
        Mounts.resetCooldown(sp);
        h.assertTrue(!Riding.blocked(sp, "attack") && !RpgCombat.beforeAttack(sp, z), "free after dismount");
        h.assertTrue(Mounts.summon(sp), "summon again");
        RpgMount m2 = (RpgMount) sp.getVehicle();
        m2.kill(level);
        h.runAfterDelay(2, () -> {
            h.assertTrue(m2.isRemoved() && !sp.isPassenger() && Mounts.mountOf(sp) == null, "killed mount cleared");
            h.assertTrue(Mounts.cooldownLeft(sp) == 0, "no cooldown after kill");
            z.discard();
            h.getLevel().getServer().getPlayerList().remove(other);
            RpgGearTests.done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 40)
    public void headroom(GameTestHelper h) {
        ServerPlayer sp = player(h);
        BlockPos feet = BlockPos.containing(sp.position());
        // 탄 사람의 머리 (발 + 자리 1.65 − 0.6 + 키 1.8 = 2.85): 발 위 두 칸이 막히면 못 부름, 세 칸째는 괜찮음
        h.getLevel().setBlockAndUpdate(feet.above(2), Blocks.STONE.defaultBlockState());
        h.assertTrue(!Mounts.summon(sp) && sp.getVehicle() == null, "blocked at +2");
        h.getLevel().setBlockAndUpdate(feet.above(2), Blocks.COBWEB.defaultBlockState());
        h.assertTrue(Mounts.summon(sp) && sp.getVehicle() instanceof RpgMount, "cobweb (no collision) is fine");
        Mounts.dismiss(sp);
        Mounts.resetCooldown(sp);
        h.getLevel().setBlockAndUpdate(feet.above(2), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(feet.above(3), Blocks.STONE.defaultBlockState());
        h.assertTrue(Mounts.summon(sp) && sp.getVehicle() instanceof RpgMount, "+3 is fine");
        Mounts.dismiss(sp);
        Mounts.resetCooldown(sp);
        h.getLevel().setBlockAndUpdate(feet.above(2), Blocks.FIRE.defaultBlockState());
        h.assertTrue(!Mounts.summon(sp), "fire blocks");
        h.getLevel().setBlockAndUpdate(feet.above(2), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(feet.above(3), Blocks.AIR.defaultBlockState());
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 40)
    public void ownedSelectUnlock(GameTestHelper h) {
        MountDefs.add(testMount());
        try {
            ServerPlayer sp = player(h);
            Props.set(sp, Mounts.DATA, null);
            h.assertTrue(Mounts.owned(sp).equals(List.of(WOLF)) && WOLF.equals(Mounts.current(sp)), "starter only " + Mounts.owned(sp));
            h.assertTrue(!Mounts.select(sp, "test_wolf"), "cannot select locked");
            h.assertTrue(!Mounts.unlock(sp, WOLF), "starter already owned");
            h.assertTrue(Mounts.unlock(sp, "test_wolf") && !Mounts.unlock(sp, "test_wolf"), "unlock once");
            h.assertTrue(Mounts.owned(sp).equals(List.of(WOLF, "test_wolf")), "owned " + Mounts.owned(sp));
            h.assertTrue(Mounts.select(sp, "test_wolf") && "test_wolf".equals(Mounts.current(sp)), "selected");
            String raw = Props.get(sp, Mounts.DATA);
            h.assertTrue(raw != null && raw.contains("\"current\":\"test_wolf\"") && raw.contains("test_wolf"), "saved " + raw);
            h.assertTrue(Mounts.lock(sp, "test_wolf") && WOLF.equals(Mounts.current(sp)) && !Mounts.lock(sp, WOLF), "lock falls back to starter");
            // 탈것 아이템: 얻으면 1개 씀, 이미 있으면 그대로
            ItemStack stack = new ItemStack(MountItems.of(WOLF), 3);
            h.assertTrue(Mounts.useItem(sp, stack, "test_wolf") && stack.getCount() == 2 && Mounts.owns(sp, "test_wolf"), "item unlock " + stack.getCount());
            h.assertTrue(!Mounts.useItem(sp, stack, "test_wolf") && stack.getCount() == 2, "owned: not used");
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MountItems.of(WOLF), 2));
            sp.getMainHandItem().use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
            h.assertTrue(sp.getMainHandItem().getCount() == 2, "dire wolf item: starter already owned");
            RpgGearTests.done(h, sp);
        } finally {
            MountDefs.remove("test_wolf");
        }
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void selectWindowSwapsWhileRiding(GameTestHelper h) {
        MountDefs.add(testMount());
        ServerPlayer sp = player(h);
        Forms.HEADLESS.add(sp.getUUID());
        Props.set(sp, Mounts.DATA, null);
        Mounts.unlock(sp, "test_wolf");
        h.assertTrue(Mounts.summon(sp), "summoned");
        RpgMount first = (RpgMount) sp.getVehicle();
        // 스탯 창 탈것 칸 Shift + 누르기 → 고르기 창 (탈것 목록 + 닫기)
        h.assertTrue(RpgNet.statAction(sp, "mount_select"), "stat action");
        h.assertTrue(Forms.last(sp) instanceof FormNet.MenuPayload mp && mp.buttons().size() == MountDefs.all().size() + 1, "select window " + Forms.last(sp));
        int idx = List.copyOf(MountDefs.all().keySet()).indexOf("test_wolf");
        h.assertTrue(Forms.answer(sp, idx, null), "answered");
        h.assertTrue("test_wolf".equals(Mounts.current(sp)), "current " + Mounts.current(sp));
        h.assertTrue(first.isRemoved() && sp.getVehicle() instanceof RpgMount m2 && m2 != first && Mounts.mountOf(sp) == m2, "swapped mounts");
        h.assertTrue(Mounts.cooldownLeft(sp) == 0, "swap has no cooldown");
        // 스탯 창 탈것 칸 (누르기) = 돌려보내기
        h.assertTrue(RpgNet.statAction(sp, "mount") && sp.getVehicle() == null && Mounts.mountOf(sp) == null, "stat button dismisses");
        Forms.close(sp);
        Forms.HEADLESS.remove(sp.getUUID());
        MountDefs.remove("test_wolf");
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void strayMountsCleaned(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        EntityType<?> type = RpgEntities.type("rpg:dire_wolf_mount");
        RpgMount lone = (RpgMount) type.create(level, EntitySpawnReason.COMMAND);
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2, 4.5));
        lone.snapTo(at.x, at.y, at.z, 0F, 0F);
        level.addFreshEntity(lone);
        ServerPlayer sp = player(h);
        RpgMount ridden = (RpgMount) type.create(level, EntitySpawnReason.COMMAND);
        ridden.snapTo(sp.getX(), sp.getY(), sp.getZ(), 0F, 0F);
        level.addFreshEntity(ridden);
        sp.startRiding(ridden, true, true);
        h.runAfterDelay(12, () -> {
            h.assertTrue(lone.isRemoved(), "riderless stray removed");
            h.assertTrue(!ridden.isRemoved() && Mounts.mountOf(sp) == ridden, "ridden stray adopted");
            Mounts.dismiss(sp);
            RpgGearTests.done(h, sp);
        });
    }
}
