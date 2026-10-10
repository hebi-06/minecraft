package mmorpg.fabric.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 상자 창의 칸 하나 (chestui.js Slot: {icon, name, lore, count, price, deco}). 숫자는 칸 오른쪽 아래에 — count 는 흰 글자 (보유 수),
 * price 는 초록 글자 (가격), deco 는 누를 수 없는 장식 칸.
 */
public final class Slot {
    /** 종류: 숫자 없음 · 개수 · 가격 · 장식 (chestui.js 의 n · c · p · d) */
    public static final byte NONE = 0;
    public static final byte COUNT = 1;
    public static final byte PRICE = 2;
    public static final byte DECO = 3;

    /** 빈 칸 채우기 (chestui.js PANE) */
    public static final Slot PANE = new Slot(Icon.sprite("pane"), "").deco();

    Icon icon;
    String name;
    List<String> lore = List.of();
    Integer count;
    Integer price;
    boolean deco;

    public Slot(Icon icon, String name) {
        this.icon = icon == null ? Icon.NONE : icon;
        this.name = name == null ? "" : name;
    }

    public static Slot of(Icon icon, String name) {
        return new Slot(icon, name);
    }

    public static Slot of(ItemLike item, String name) {
        return new Slot(Icon.item(item), name);
    }

    public static Slot of(ItemStack item, String name) {
        return new Slot(Icon.item(item), name);
    }

    /** 애드온 그림 경로로 (Icon.of) */
    public static Slot of(String iconPath, String name) {
        return new Slot(Icon.of(iconPath), name);
    }

    public Slot lore(List<String> lines) {
        this.lore = lines == null ? List.of() : List.copyOf(lines);
        return this;
    }

    public Slot lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    /** 설명 줄을 더함 */
    public Slot addLore(String... lines) {
        List<String> l = new ArrayList<>(lore);
        l.addAll(Arrays.asList(lines));
        this.lore = List.copyOf(l);
        return this;
    }

    /** 보유 수 (흰 숫자) */
    public Slot count(double n) {
        this.count = (int) Math.floor(n);
        return this;
    }

    /** 가격 (초록 숫자) */
    public Slot price(double n) {
        this.price = (int) Math.floor(n);
        return this;
    }

    /** 누를 수 없는 장식 칸 */
    public Slot deco() {
        this.deco = true;
        return this;
    }

    public Slot deco(boolean on) {
        this.deco = on;
        return this;
    }

    public Icon icon() {
        return icon;
    }

    public String name() {
        return name;
    }

    public List<String> loreLines() {
        return lore;
    }

    public boolean isDeco() {
        return deco;
    }

    /** chestui.js encode: 종류 (장식 > 가격 > 개수) 와 숫자 (애드온은 세 자리까지 — 자바 창은 그대로 보임) */
    byte kind() {
        if (deco) return DECO;
        if (price != null) return PRICE;
        if (count != null) return COUNT;
        return NONE;
    }

    int number() {
        int n = deco ? 0 : price != null ? price : count != null ? count : 0;
        return Math.max(0, Math.min(99999, n));
    }
}
