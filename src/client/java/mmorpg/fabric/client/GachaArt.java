package mmorpg.fabric.client;

import java.util.List;
import java.util.Map;
import mmorpg.core.Grade;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

/**
 * 룬 뽑기 창 · 연출의 그림 도우미 (0.14.0): GUI 그림 (assets/mmorpg/textures/gui/sprites/gacha — tools/gacha_art.py), 등급 색,
 * 돌리거나 키운 그림 · 아이템 · 글자, 소리.
 */
final class GachaArt {
    private GachaArt() {}

    static final Identifier GLOW = Mmorpg.id("gacha/glow");
    static final Identifier RAYS = Mmorpg.id("gacha/rays");
    static final Identifier RING = Mmorpg.id("gacha/ring");
    static final Identifier STAR = Mmorpg.id("gacha/star");
    static final Identifier SHINE = Mmorpg.id("gacha/shine");
    static final Identifier CARD = Mmorpg.id("gacha/card");
    static final Identifier CARD_BACK = Mmorpg.id("gacha/card_back");
    static final Identifier[] CIRCLE = new Identifier[10];

    static {
        for (int i = 0; i < 10; i++) CIRCLE[i] = Mmorpg.id("gacha/circle_" + i);
    }

    static Identifier banner(String kind) {
        return Mmorpg.id("gacha/banner_" + switch (kind) {
            case "limited" -> "limited";
            case "advanced" -> "advanced";
            case "stat" -> "stat";
            default -> "normal";
        });
    }

    /** 등급 색 (RGB — 룬 그림의 테두리 색과 같은 쪽) */
    private static final Map<String, Integer> GRADE_RGB = Map.of("common", 0xE0E0F0, "rare", 0x5C99FF, "noble", 0xB86BFF, "hero", 0xFF9E29, "transcend", 0xFF4646);
    static final List<String> GRADES = Runes.GRADE_ORDER;

    static int rgb(String grade) {
        return GRADE_RGB.getOrDefault(grade, 0xFFFFFF);
    }

    static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    /** a → b 사이 색 (k 0 ~ 1) */
    static int mix(int a, int b, double k) {
        double t = Math.max(0, Math.min(1, k));
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int gradeIndex(String grade) {
        return Math.max(0, GRADES.indexOf(grade));
    }

    static String gradeName(String grade) {
        Grade g = Rules.get().grade(grade);
        return g == null ? grade : g.name();
    }

    /** 등급 글자 색 (자바 서식 — §u · §v 같은 베드락 색도) */
    static String gradeColor(String grade) {
        Grade g = Rules.get().grade(grade);
        return BedrockText.toJava(g == null ? "§f" : g.color());
    }

    static ItemStack rune(String id) {
        RuneItem it = RuneItems.byId(id);
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    static ItemStack rune(String family, String grade) {
        return rune(Runes.runeId(family, grade));
    }

    static String runeName(String id) {
        Runes.Rune r = Runes.parseRune(id);
        return r == null ? id : r.def().str("name");
    }

    static String familyName(String family) {
        mmorpg.core.Obj f = Rules.get().obj("RUNE_FAMILIES").objOrNull(family);
        return f == null ? family : f.str("name");
    }

    // ------------------------------------------------------------ 그리기

    /** 가운데 (cx, cy) 에 size 크기 그림 · 돌림 (라디안) · 색 */
    static void sprite(GuiGraphicsExtractor g, Identifier id, float cx, float cy, float size, float angle, int argb) {
        sprite(g, id, cx, cy, size, size, angle, argb);
    }

    static void sprite(GuiGraphicsExtractor g, Identifier id, float cx, float cy, float w, float h, float angle, int argb) {
        if (w <= 0.5F || h <= 0.5F || (argb >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        if (angle != 0) g.pose().rotate(angle);
        int iw = Math.max(1, Math.round(w));
        int ih = Math.max(1, Math.round(h));
        g.blitSprite(RenderPipelines.GUI_TEXTURED, id, -iw / 2, -ih / 2, iw, ih, argb);
        g.pose().popMatrix();
    }

    /** 아이템을 가운데 (cx, cy) 에 scale 배로 (16 × scale) */
    static void item(GuiGraphicsExtractor g, ItemStack stack, float cx, float cy, float scale) {
        if (stack.isEmpty() || scale <= 0.01F) return;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.item(stack, -8, -8);
        g.pose().popMatrix();
    }

    /** 글자를 가운데 (cx) 에 scale 배로 */
    static void textCentered(GuiGraphicsExtractor g, Font font, String s, float cx, float y, float scale, int argb) {
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        g.text(font, s, -font.width(s) / 2, 0, argb, true);
        g.pose().popMatrix();
    }

    static void text(GuiGraphicsExtractor g, Font font, String s, float x, float y, float scale, int argb) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, argb, true);
        g.pose().popMatrix();
    }

    /** 너비 maxW 를 넘으면 가로로 줄여서 */
    static void textFit(GuiGraphicsExtractor g, Font font, String s, int x, int y, int maxW, int argb) {
        int w = font.width(s);
        if (w <= maxW) {
            g.text(font, s, x, y, argb, true);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(maxW / (float) w, 1F);
        g.text(font, s, 0, 0, argb, true);
        g.pose().popMatrix();
    }

    /** 반짝이는 점 (네 갈래 별 그림) */
    static void twinkle(GuiGraphicsExtractor g, float x, float y, float size, int argb) {
        sprite(g, STAR, x, y, size, 0F, argb);
    }

    // ------------------------------------------------------------ 소리

    static void sound(SoundEvent e, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
    }

    static void sound(Holder<SoundEvent> e, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch));
    }
}
