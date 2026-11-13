package mmorpg.fabric.test;

import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import mmorpg.fabric.mob.RpgEntities;
import mmorpg.fabric.mob.TyrfingWarrior;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 5단계 게임 시험 (서버) — 애드온 엔티티 바탕: 35종 등록 (6단계 탈것 · 9단계 새 몬스터 8종 포함) · 행동 팩 수치 (체력 · 크기 · 이동 · 충돌 상자, 보스 체력은 자바 상한 1024 를 넘음),
 * 속성 (범위 · 기본값 · 동기화 글자), 튼 동작 (컨트롤러마다 하나), 사건 (rpg:hold · rpg:release 로 이동 빠르기), 소환 알.
 */
public final class RpgMobTests {
    private static BedrockMob spawn(GameTestHelper h, String id) {
        BedrockMob e = RpgEntities.type(id).create(h.getLevel(), EntitySpawnReason.COMMAND);
        Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
        e.snapTo(at.x, at.y, at.z, 0F, 0F);
        h.getLevel().addFreshEntity(e);
        return e;
    }

    @GameTest(maxTicks = 20)
    public void mobsRegistered(GameTestHelper h) {
        h.assertTrue(RpgEntities.types().size() == 57, "types (27 + 9단계 몬스터 8 + 0.32.0 공허 5 + 0.33.0 공허 기사 1 + 0.35.0 헬 1 + 0.50.0 티르빙 전사 1 + 0.51.0 망자 선원 1"
                + " + 2026-11-06 피에로 소환물 2 + 2026-11-07 늑대 정령 1 + 2026-11-11 불의 정령 샐러맨더 1"
                + " + 2026-11-12 룬 스킬 몹 6 — 매 · 야수왕 · 운디네 · 실프 · 노움 · 정령왕 + 직업 스킬 몹 1 — 드라군의 붉은 용 + 2026-11-13 그림자 분신 · 기의 화신 2) "
                + RpgEntities.types().size());
        h.assertTrue(RpgEntities.eggs().size() == 19, "eggs (몬스터 6 + 8 + 공허 5) " + RpgEntities.eggs().size());
        for (MobData.Def d : MobData.all().values()) {
            BedrockMob e = spawn(h, d.id());
            h.assertTrue(Math.abs(e.getMaxHealth() - d.health()) < 1e-3, d.id() + " max hp " + e.getMaxHealth() + " != " + d.health());
            h.assertTrue(Math.abs(e.getHealth() - d.health()) < 1e-3, d.id() + " hp " + e.getHealth());
            h.assertTrue(Math.abs(e.getScale() - d.scale()) < 1e-3, d.id() + " scale " + e.getScale());
            // 맞는 상자 (custom_hit_test) 가 있는 소품 (짚단 감옥 · 수르트 부위의 핵) 은 몸 = 맞는 상자 (RpgProp — 조준에 잡히게)
            float[] hb = MobData.hitbox(d.id());
            double w = (hb != null ? hb[0] : Math.max(0.01, d.width())) * d.scale();
            h.assertTrue(Math.abs(e.getBbWidth() - w) < 1e-3, d.id() + " width " + e.getBbWidth() + " != " + w);
            h.assertTrue(e.isNoGravity() == !d.gravity(), d.id() + " gravity");
            e.discard();
        }
        h.succeed();
    }

    /** 0.50.0 티르빙 망자의 각성의 아군 전사: 아군 표시 · 맞지 않음 · 노려지지 않음 · 저장하지 않음 · maxAge 틱 뒤 스스로 사라짐 */
    @GameTest(maxTicks = 40)
    public void tyrfingWarriorAlly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        TyrfingWarrior w = TyrfingWarrior.spawn(level, h.absoluteVec(new Vec3(1.5, 2, 1.5)), 0F, 10);
        h.assertTrue(w != null, "spawned");
        h.assertTrue(w.entityTags().contains(TyrfingWarrior.TAG) && w.entityTags().contains("tyrfing_warrior"), "ally tags " + w.entityTags());
        h.assertTrue(!w.shouldBeSaved() && !w.isAttackable() && !w.canBeSeenAsEnemy() && !w.canBeHitByProjectile(), "ally flags");
        h.assertTrue(w.isInvulnerableTo(level, level.damageSources().generic()), "takes no damage");
        h.assertTrue(w.getMaxHealth() == 100F && Math.abs(w.getScale() - 1.15) < 1e-3, "def " + w.getMaxHealth() + " " + w.getScale());
        h.runAfterDelay(15, () -> {
            h.assertTrue(w.isRemoved(), "gone after maxAge");
            h.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void bossHealthAboveJavaCap(GameTestHelper h) {
        BedrockMob surtr = spawn(h, "rpg:boss_surtr");
        h.assertTrue(surtr.getMaxHealth() == 90000F, "surtr " + surtr.getMaxHealth());
        surtr.setHealth(45000F);
        h.assertTrue(surtr.getHealth() == 45000F, "set " + surtr.getHealth());
        h.assertTrue(surtr.bossBar() != null && surtr.def.bossRange() == 72, "boss bar");
        surtr.discard();
        BedrockMob troll = spawn(h, "rpg:troll");
        h.assertTrue(troll.getMaxHealth() == 230F && Math.abs(troll.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.24) < 1e-6, "troll " + troll.getMaxHealth());
        h.assertTrue(troll.bossBar() == null, "troll has no boss bar");
        troll.discard();
        h.succeed();
    }

    @GameTest(maxTicks = 20)
    public void propsAndAnimations(GameTestHelper h) {
        BedrockMob s = spawn(h, "rpg:boss_surtr");
        h.assertTrue(!s.propBool("rpg:flash") && s.prop("rpg:heat") == 0, "defaults");
        s.setProp("rpg:flash", true);
        s.setProp("rpg:heat", 3.0); // 범위 0 ~ 1
        s.setProp("rpg:nope", 5);
        h.assertTrue(s.propBool("rpg:flash") && s.prop("rpg:heat") == 1.0 && s.prop("rpg:nope") == 0, "set " + s.prop("rpg:heat"));
        s.playAnimation("animation.rpg.boss.surtr.slam");
        s.playAnimation("animation.rpg.boss.surtr.roar_hold", "rpg_hold", "0");
        s.playAnimation("animation.rpg.boss.surtr.fist");
        long n = s.syncedAnims().size();
        h.assertTrue(n == 2, "controllers " + s.syncedAnims());
        h.assertTrue(s.syncedAnims().stream().anyMatch(p -> p.anim().endsWith(".fist") && p.controller().equals(BedrockMob.RUNTIME)), "runtime replaced");
        h.assertTrue(s.syncedAnims().stream().anyMatch(p -> p.anim().endsWith(".roar_hold") && p.stop().equals("0")), "hold");
        s.discard();
        BedrockMob k = spawn(h, "rpg:boss_scarecrow_king");
        h.assertTrue(Math.abs(k.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.24) < 1e-6, "spawned free " + k.getAttributeValue(Attributes.MOVEMENT_SPEED));
        k.triggerEvent("rpg:hold");
        h.assertTrue(k.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "held " + k.getAttributeValue(Attributes.MOVEMENT_SPEED));
        k.triggerEvent("rpg:release");
        h.assertTrue(Math.abs(k.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.24) < 1e-6, "released");
        k.discard();
        h.succeed();
    }
}
