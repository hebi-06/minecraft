package mmorpg.fabric;

import java.util.List;
import mmorpg.core.Combat;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * 애드온 무기 하나 (rpg:<종류>_l<레벨>_<등급>). 이름 · 설명 · 요구 스탯 · 피해는 규칙 코어의 Weapon 그대로.
 * 근접 (칼 · 단검 · 도끼 · 방패 · 마검): 평타 피해는 RpgCombat 이 바꿈 (공격 속도 2.0 = 0.5초마다 꽉 찬 공격 — 베드락의 초당 약 2번과 같게).
 * 활 · 완드: 누르면 (사용) 바라보는 대상에 바로 쏨 (완드는 시전 0.5초 뒤), 몹을 때려도 그 몹에게 쏨.
 */
public final class RpgWeaponItem extends Item {
    private final Weapon weapon;

    public RpgWeaponItem(Weapon weapon) {
        super(properties(weapon));
        this.weapon = weapon;
    }

    private static Item.Properties properties(Weapon w) {
        double damage = Combat.isMeleeWeapon(w) ? Math.max(0, w.damage() - 1) : 0;
        ItemAttributeModifiers mods = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()
                .withTooltip(false); // 바닐라 "공격 피해" 줄 대신 애드온 설명 (실제 피해는 스탯 배율이 곱해짐)
        return new Item.Properties().stacksTo(1).attributes(mods);
    }

    public Weapon weapon() {
        return weapon;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(BedrockText.toJava(weapon.name()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (String line : Weapons.weaponInfo(weapon, false, 0)) tooltip.add(Component.literal(BedrockText.toJava(line)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!RpgCombat.isRanged(weapon) || hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && player instanceof ServerPlayer sp) RpgCombat.fireRanged(sp, stack, weapon, null);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
