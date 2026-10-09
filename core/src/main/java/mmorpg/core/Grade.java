package mmorpg.core;

/**
 * One entry of config.js {@code GRADES} (일반 · 희귀 · 고귀 · 영웅 · 초월).
 *
 * @param index position in GRADES (등급 순번, used by requirement and crit formulas)
 * @param color text color on dark backgrounds (item lore), {@code dark}: on light backgrounds (chest UI)
 * @param mult  weapon damage multiplier
 */
public record Grade(int index, String id, String name, String color, String dark, double mult, String icon) {}
