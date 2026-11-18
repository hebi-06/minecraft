package mmorpg.fabric;

import java.util.function.Consumer;
import mmorpg.core.Combat;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * 애드온 무기 하나 (rpg:<종류>_l<레벨>_<등급>). 이름 · 설명 · 요구 스탯 · 피해는 규칙 코어의 Weapon 그대로.
 * 근접 (칼 · 단검 · 도끼 · 방패 · 마검): 평타 피해는 RpgCombat 이 바꿈 (공격 속도 2.0 = 0.5초마다 꽉 찬 공격 — 베드락의 초당 약 2번과 같게).
 * 활 · 완드: 좌클릭 (휘두르기) 으로 바라보는 대상에 바로 쏨 (완드는 시전 0.5초 뒤), 몹을 때려도 그 몹에게 쏨 — 2026-11-18 전에는 우클릭 (우클릭은 스킬 키로).
 * 2026-11-16 무기 스킬 (초월 · 영웅 무기의 우클릭 스킬 — Transcend) 은 지움 (사용자 "무기 스킬도 빼고 대신 무기 디자인을 늘리건데"): 모든 무기가 같은 조작.
 */
public final class RpgWeaponItem extends Item {
    /**
     * 0.39.0 공격 속도 +96 (바닐라 4 → 100번/초 — 공격 대기가 0.2틱): 바닐라 공격 대기 (화면 가운데 칼 그림 · 덜 찬 공격 약함 · 손이 내려감) 를 없앰.
     * 근접 평타 사이는 MOTION.interval (클라이언트 선입력 · 서버 Slash.admit), 활 · 완드는 RpgCombat.fireRanged 의 재사용 대기 그대로
     */
    static final double NO_COOLDOWN = 96.0;

    /** 창의 사거리 (ENTITY_INTERACTION_RANGE) 속성 이름 */
    public static final net.minecraft.resources.Identifier REACH_ID = Mmorpg.id("weapon_reach");

    private final Weapon weapon;

    public RpgWeaponItem(Weapon weapon, Item.Properties properties) {
        super(properties);
        this.weapon = weapon;
    }

    /** 아이템 속성 (등록 키 setId 는 부르는 쪽이 붙임) */
    static Item.Properties properties(Weapon w) {
        double damage = Combat.isMeleeWeapon(w) ? Math.max(0, w.damage() - 1) : 0;
        // 바닐라 "공격 피해" 줄은 숨김 (실제 피해는 스탯 배율이 곱해지므로 애드온 설명으로)
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND, ItemAttributeModifiers.Display.hidden())
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, NO_COOLDOWN, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND, ItemAttributeModifiers.Display.hidden());
        // 창 (12단계): 몹을 칠 수 있는 거리 +reach 칸 (설명은 애드온 식 설명 줄 — Weapons.traitLines)
        double reach = Combat.isMeleeWeapon(w) ? w.typeDef().numOr("reach", 0) : 0;
        if (reach > 0) {
            b.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND,
                    ItemAttributeModifiers.Display.hidden());
        }
        return new Item.Properties().stacksTo(1).attributes(b.build());
    }

    public Weapon weapon() {
        return weapon;
    }

    /** 강화한 무기는 "+N 이름 [레벨렙]" (weapons.js enhancedName) */
    @Override
    public Component getName(ItemStack stack) {
        int n = mmorpg.fabric.gear.WeaponEnhance.of(stack);
        return Component.literal(BedrockText.toJava(n > 0 ? Weapons.enhancedName(weapon, n, null) : weapon.name()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        for (String line : Weapons.weaponInfo(weapon, false, mmorpg.fabric.gear.WeaponEnhance.of(stack))) {
            tooltip.accept(Component.literal(BedrockText.toJava(line)));
        }
    }

    /**
     * 2026-11-18 축복의 길: 우클릭 · Shift + 우클릭 = 스킬 키 (SkillKeys — 끼운 직업 · 공용 스킬). 활 · 완드는 좌클릭으로 쏨 (RpgCombat.punchRanged ·
     * 몹을 누르면 그 몹에게) — 우클릭은 모든 무기가 같은 스킬 키. 빈 키면 아무것도 하지 않음 (PASS)
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        mmorpg.fabric.vanilla.VanillaFirstPerson.onUse(sp); // 1인칭 시범: 우클릭 패킷이 1인칭 쿨타임 중에도 닿는지 셈
        return mmorpg.fabric.job.SkillKeys.onUse(sp) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 0;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }
}
