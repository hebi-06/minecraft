package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Runes;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 룬 아이템 등록: 룬 80 (계열 16 × 등급 5 — rpg:rune_&lt;계열&gt;_&lt;등급&gt;, 한 칸에 하나, 영웅 · 초월은 반짝임) + 룬 뽑기권 · 고급 룬 뽑기권 (64개씩, 반짝임).
 * 크리에이티브 탭 "룬" 하나 (뽑기권 → 계열 순 → 등급 순).
 */
public final class RuneItems {
    private RuneItems() {}

    /** 등록한 룬 (계열 순 → 등급 순) */
    private static final List<RuneItem> RUNES = new ArrayList<>();
    /** 룬 ID (rpg:rune_str_hero) → 아이템 */
    private static final Map<String, RuneItem> BY_ID = new HashMap<>();
    public static TicketItem TICKET;
    public static TicketItem ADV_TICKET;
    public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Mmorpg.id("runes"));

    private static ResourceKey<Item> key(String id) {
        return ResourceKey.create(Registries.ITEM, Mmorpg.rpg(id.substring((Mmorpg.NS + ":").length())));
    }

    /** 아이템 등록 (RpgItems.init 이 부름 — 레지스트리가 얼기 전) */
    public static void init() {
        for (String family : Runes.FAMILY_ORDER) {
            for (String grade : Runes.GRADE_ORDER) {
                Runes.Rune r = Runes.parseRune(Runes.runeId(family, grade));
                if (r == null) continue;
                ResourceKey<Item> key = key(r.id());
                Item.Properties props = new Item.Properties().stacksTo(1).setId(key);
                if (grade.equals("hero") || grade.equals("transcend")) props.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                RuneItem item = Registry.register(BuiltInRegistries.ITEM, key, new RuneItem(r, props));
                RUNES.add(item);
                BY_ID.put(r.id(), item);
            }
        }
        TICKET = ticket(RuneKit.RUNE.str("ticketItem"), false);
        ADV_TICKET = ticket(RuneKit.RUNE.str("advTicketItem"), true);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmorpg.runes"))
                .icon(() -> new ItemStack(TICKET))
                .displayItems((params, output) -> {
                    output.accept(TICKET);
                    output.accept(ADV_TICKET);
                    for (RuneItem r : RUNES) output.accept(r);
                })
                .build());
    }

    private static TicketItem ticket(String id, boolean adv) {
        ResourceKey<Item> key = key(id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new TicketItem(adv, new Item.Properties().stacksTo(64).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).setId(key)));
    }

    public static List<RuneItem> runes() {
        return Collections.unmodifiableList(RUNES);
    }

    /** 룬 ID 의 아이템 (없는 조합이면 null) */
    public static RuneItem byId(String id) {
        return BY_ID.get(id);
    }

    /** 룬 아이템 한 개 (createRuneItem — 이름 · 설명은 아이템이 그림) */
    public static ItemStack stack(String family, String grade) {
        RuneItem it = byId(Runes.runeId(family, grade));
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    /** 뽑기권 amount 장 (createTicketItem) */
    public static ItemStack ticket(int amount, boolean adv) {
        return new ItemStack(adv ? ADV_TICKET : TICKET, Math.max(1, Math.min(64, amount)));
    }

    /** 이 칸의 아이템이 룬이면 그 룬 (아니면 null — parseRune(heldTypeId)) */
    public static Runes.Rune runeOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof RuneItem ri ? ri.rune() : null;
    }
}
