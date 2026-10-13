package mmorpg.fabric;

import java.util.function.Consumer;
import mmorpg.core.Combat;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.skill.Transcend;
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
 * 활 · 완드: 누르면 (사용) 바라보는 대상에 바로 쏨 (완드는 시전 0.5초 뒤), 몹을 때려도 그 몹에게 쏨.
 * 초월 무기 (스킬 둘): 우클릭 · 웅크리고 우클릭 · 길게 우클릭으로 스킬 (Transcend) — 활 · 완드는 누르고 있는 동안 아이템 사용, 짧게 떼면 평타.
 * 영웅 무기 (스킬 하나 — 10단계): 근접은 우클릭, 활 · 완드는 웅크리고 우클릭 (그냥 누르면 바로 평타).
 */
public final class RpgWeaponItem extends Item {
    private final Weapon weapon;

    public RpgWeaponItem(Weapon weapon, Item.Properties properties) {
        super(properties);
        this.weapon = weapon;
    }

    /** 아이템 속성 (등록 키 setId 는 부르는 쪽이 붙임) */
    static Item.Properties properties(Weapon w) {
        double damage = Combat.isMeleeWeapon(w) ? Math.max(0, w.damage() - 1) : 0;
        // 바닐라 "공격 피해" 줄은 숨김 (실제 피해는 스탯 배율이 곱해지므로 애드온 설명으로)
        ItemAttributeModifiers mods = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND, ItemAttributeModifiers.Display.hidden())
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND, ItemAttributeModifiers.Display.hidden())
                .build();
        return new Item.Properties().stacksTo(1).attributes(mods);
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
            tooltip.accept(Component.literal(BedrockText.toJava(javaControls(line))));
        }
    }

    /** 조작 줄: 애드온의 "버튼" (모바일) → 자바의 우클릭 (스킬 둘 · 하나 — 하나는 줄 끝이 "스킬") */
    public static String javaControls(String line) {
        return line.replace("버튼: 스킬1, 웅크리면: 스킬2", "우클릭: 스킬1, 웅크리고 우클릭: 스킬2")
                .replace("웅크리면: 스킬1, 길게: 스킬2", "웅크리고 우클릭: 스킬1, 길게 우클릭: 스킬2")
                .replaceAll("버튼: 스킬$", "우클릭: 스킬")
                .replaceAll("웅크리면: 스킬$", "웅크리고 우클릭: 스킬");
    }

    /** 활 · 완드 초월 무기 (스킬 둘): 길게 누르기를 재려고 아이템 사용 (스킬 2 는 HOLD_TICKS 틱에, 그 뒤 떼면 아무 일 없음) */
    private boolean holdable() {
        return Transcend.holdable(weapon);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (weapon.skills() != null) return Transcend.use(player, hand, weapon);
        if (!RpgCombat.isRanged(weapon) || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide() && player instanceof ServerPlayer sp) RpgCombat.fireRanged(sp, player.getItemInHand(hand), weapon, null);
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return holdable() ? 72000 : 0;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (holdable() && entity instanceof ServerPlayer sp) Transcend.useTick(sp, weapon);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (holdable() && entity instanceof ServerPlayer sp) Transcend.release(sp, weapon);
        return false;
    }
}
