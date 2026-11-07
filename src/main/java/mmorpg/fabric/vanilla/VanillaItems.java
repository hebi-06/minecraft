package mmorpg.fabric.vanilla;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.gear.RpgArmorItem;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;

/**
 * RPG 아이템 (무기 · 갑옷 · 장신구 · 유물 · 재료 · 룬 · 뽑기권 · 동전 · 소환 알 · 제단 · 탈것 · 스탯 책 — 등록한 것 모두) 의 바닐라 대체.
 *
 * <p>모드 없는 클라이언트: 종이 + 우리 아이템 모델 (item_model = rpg:&lt;아이템&gt; — 리소스 팩의 assets/rpg/items 그대로, 3D 무기 모델 포함)
 * + 이름 · 설명 (서버가 만든 글자 — Polymer 가 getName · appendHoverText 를 서버에서 불러 이름 · lore 로) + 쌓기 · 장착 칸 (그대로 복사).
 * 누르고 있는 무기 (초월 스킬 — getUseDuration) 는 바닐라 클라이언트도 "쓰는 중" 이 되도록 consumable (끝나지 않는 시간 · 소리 없음).
 * 갑옷은 바닐라가 몸 그림 한 장으로만 그려 3D 모양을 낼 수 없음 — 지금은 계열마다 바닐라 갑옷 그림 (힘 철 · 민첩 사슬 · 체력 네더라이트 ·
 * 힘체력 다이아 · 마력 금), 나중에 디자인을 평면 그림으로 구움.
 *
 * <p>모드 클라이언트: 그대로 (아이템 · 구성 요소 · 태그).
 */
final class VanillaItems {
    private VanillaItems() {}

    /** 누르고 있는 무기: 바닐라 클라이언트가 놓을 때까지 "쓰는 중" (999999초 — 서버가 놓음 · 끝을 정함) */
    private static final Consumable HOLD = new Consumable(999999f, ItemUseAnimation.NONE, Holder.direct(SoundEvents.EMPTY), false, List.of());

    static void init() {
        int n = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!VanillaSupport.ours(BuiltInRegistries.ITEM.getKey(item))) continue;
            PolymerItem.registerOverlay(item, OVERLAY);
            n++;
        }
        Mmorpg.LOG.info("[vanilla] items {}", n);
    }

    private static final PolymerItem OVERLAY = new PolymerItem() {
        @Override
        public Item getPolymerItem(ItemStack stack, PacketContext ctx) {
            return Items.PAPER;
        }

        @Override
        public ItemStack getPolymerItemStack(ItemStack stack, TooltipFlag flag, PacketContext ctx, HolderLookup.Provider lookup) {
            return VanillaClients.isModded(ctx) ? stack : PolymerItemUtils.createItemStack(stack, flag, ctx, lookup);
        }

        @Override
        public void modifyBasePolymerItemStack(ItemStack out, ItemStack stack, PacketContext ctx, HolderLookup.Provider lookup) {
            Item item = stack.getItem();
            if (item instanceof RpgWeaponItem w && w.getUseDuration(stack, null) > 0) out.set(DataComponents.CONSUMABLE, HOLD);
            if (item instanceof RpgArmorItem armor) {
                Equippable eq = out.get(DataComponents.EQUIPPABLE);
                Item look = eq == null ? null : armorLook(armor.piece().type(), eq.slot());
                Equippable vanilla = look == null ? null : look.components().get(DataComponents.EQUIPPABLE);
                if (vanilla != null) out.set(DataComponents.EQUIPPABLE, vanilla);
            }
        }

        @Override
        public Item getPolymerReplacement(Item item, PacketContext ctx) {
            return VanillaClients.isModded(ctx) ? item : Items.PAPER;
        }

        @Override
        public boolean canSyncRawToClient(PacketContext ctx) {
            return VanillaClients.isModded(ctx);
        }
    };

    /** 계열 (str · dex · vit · strvit · int) · 부위 → 그 모습의 바닐라 갑옷 (입은 그림만 씀) */
    static Item armorLook(String type, EquipmentSlot slot) {
        Item[] set = switch (type) {
            case "dex" -> new Item[]{Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
            case "vit" -> new Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
            case "strvit" -> new Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
            case "int" -> new Item[]{Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
            default -> new Item[]{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
        };
        return switch (slot) {
            case HEAD -> set[0];
            case CHEST -> set[1];
            case LEGS -> set[2];
            case FEET -> set[3];
            default -> null;
        };
    }
}
