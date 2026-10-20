package mmorpg.fabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;
import mmorpg.core.Hp;
import mmorpg.core.Jobs;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Skills;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import net.minecraft.world.item.ItemStack;

/**
 * 스탯 창 글자 (statui.js statLine · vitBonusText · weaponLines). 서버 (채팅 스탯 창) 와 클라이언트 (스탯 창 화면) 가 같이 씀.
 * 글자는 베드락 서식 (§) — 화면에 쓸 때 BedrockText.toJava. dark = 밝은 바탕용 색 (애드온 상자 창), false = 어두운 바탕 (자바 창).
 *
 * @param base 찍은 스탯 {str, dex, int, vit, point, lvmax} (요구 스탯 확인)
 * @param eff  실제 스탯 (찍은 스탯 + 장비 보너스 · 룬 버프 — 피해 · 최대 체력 계산)
 * @param tree 스탯 트리 보너스 합 (7단계 — atk · crit · critDmg · hp …, 없으면 빈 지도)
 * @param job  0.27.0 직업 id (없으면 "") — 마검사 「마력 검술」: 칼의 요구 힘 = 힘 + 마력 · 칼 피해의 힘 = 힘 + 마력 × 0.5 (core.Jobs)
 */
public record StatSheet(Map<String, Double> base, Map<String, Double> eff, double reqLevel, Map<String, Double> tree, String job) {
    private static final Obj CONFIG = Rules.get().obj("CONFIG");
    private static final Obj TYPES = Rules.get().obj("TYPES");
    private static final Obj SHIELD = Rules.get().obj("SHIELD");
    private static final Obj SKILLS = Rules.get().obj("SKILLS");

    /** 찍은 스탯 · 최고 레벨 · 지금 경험치 레벨로 (장비 보너스 · 룬 버프 없음 → 실제 = 찍은 스탯) */
    public static StatSheet of(int str, int dex, int intel, int vit, int point, int lvmax, int level) {
        return of(str, dex, intel, vit, point, lvmax, level, Map.of(), 0);
    }

    /** 찍은 스탯 + 장비 보너스 {str, dex, int, vit} · 룬 버프 비율로 */
    public static StatSheet of(int str, int dex, int intel, int vit, int point, int lvmax, int level, Map<String, Double> bonus, double buff) {
        Map<String, Double> b = new LinkedHashMap<>();
        b.put("str", (double) str);
        b.put("dex", (double) dex);
        b.put("int", (double) intel);
        b.put("vit", (double) vit);
        b.put("point", (double) point);
        b.put("lvmax", (double) lvmax);
        return new StatSheet(b, Stats.effectiveStats(b, bonus, buff), Stats.reqLevelOf(lvmax, level), Map.of(), "");
    }

    /** 스탯 트리 보너스 합을 넣은 것 (7단계 — 피해 · 치명타 · 최대 체력 줄에 반영) */
    public StatSheet withTree(Map<String, Double> t) {
        return new StatSheet(base, eff, reqLevel, t == null ? Map.of() : Map.copyOf(t), job);
    }

    /** 0.27.0 직업을 넣은 것 (마검사 「마력 검술」 — 요구 스탯 · 피해 줄에 반영) */
    public StatSheet withJob(String id) {
        return new StatSheet(base, eff, reqLevel, tree, id == null ? "" : id);
    }

    /** 요구 스탯을 따질 때의 찍은 스탯 (마검사 · 칼: 힘 = 힘 + 마력) */
    Map<String, Double> reqBase(Weapon w) {
        Jobs.Job j = Jobs.job(job);
        return j == null || w == null ? base : Jobs.reqStats(j, w.type(), base);
    }

    /** 무기 피해를 셀 때의 스탯 (마검사 · 칼: 힘 = 힘 + 마력 × 0.5) */
    Map<String, Double> dmgEff(Weapon w) {
        Jobs.Job j = Jobs.job(job);
        return j == null || w == null ? eff : Jobs.damageStats(j, w.type(), eff);
    }

    /** 이 무기에 「마력 검술」 이 걸리는지 */
    boolean magicSword(Weapon w) {
        Jobs.Job j = Jobs.job(job);
        return j != null && w != null && Jobs.magicSword(j, w.type());
    }

    /** 스탯 트리 보너스 (없으면 0) */
    public double tree(String kind) {
        Double v = tree.get(kind);
        return v == null ? 0 : v;
    }

    public static StatSheet of(RpgData d, int level) {
        return of(d.str(), d.dex(), d.intel(), d.vit(), d.point(), d.lvmax(), level);
    }

    /** 서버: 이 플레이어의 지금 스탯 (장비 보너스 · 룬 버프 포함) */
    public static StatSheet of(net.minecraft.server.level.ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        return of(d.str(), d.dex(), d.intel(), d.vit(), d.point(), d.lvmax(), sp.experienceLevel, RpgStats.bonusOf(sp), RpgStats.buffOf(sp))
                .withTree(mmorpg.fabric.stattree.StatTrees.totals(sp)).withJob(mmorpg.fabric.job.JobSystem.jobOf(sp));
    }

    /** 소수 둘째 자리까지 (끝의 0은 뺌) */
    static String num(double n) {
        return Js.str(Js.round(n * 100) / 100);
    }

    /** 스탯 key 를 v 만큼 찍었을 때 무기 피해가 몇 % 오르는지 */
    static String bonusPct(String key, double v) {
        return num(v * Stats.damagePerPoint(key) * 100);
    }

    public int point() {
        return (int) Stats.get(base, "point");
    }

    public double lvmax() {
        return Stats.get(base, "lvmax");
    }

    public int stat(String key) {
        return (int) Stats.get(base, key);
    }

    /** 최대 체력 (실제 체력 스탯 + 스탯 트리) */
    public double maxHp() {
        return Hp.maxHpOf(lvmax(), Stats.get(eff, "vit")) + tree("hp");
    }

    /** 갑옷 · 버프 보너스 글자 (없으면 "") */
    String plusText(String key, boolean dark) {
        double b = Stats.get(eff, key) - Stats.get(base, key);
        return b > 0 ? (dark ? "§2" : "§a") + "+" + Js.str(b) : "";
    }

    /** 스탯 이름과 값 ("§c§l힘 §r§f12") */
    public String title(String key) {
        Obj info = Stats.info(key);
        return info.str("color") + "§l" + info.str("name") + " §r§f" + stat(key) + plusText(key, false);
    }

    /** 효과 한 줄 ("칼·도끼·마검 피해 +24% (1당 2%)" · 체력은 최대 체력) */
    public String effect(String key) {
        Obj info = Stats.info(key);
        if (key.equals("vit")) {
            return "§7최대 체력 §f" + Js.str(maxHp()) + "§7 (1당 +" + CONFIG.text("HP_PER_VIT") + " · 레벨 보너스 +" + Js.str(Hp.levelHpOf(lvmax())) + ")";
        }
        return "§7" + info.str("weapons") + " 피해 §f+" + bonusPct(key, Stats.get(eff, key)) + "%§7 (1당 " + bonusPct(key, 1) + "%)";
    }

    /** statui.js statLine (한 줄 요약) */
    public String statLine(String key, boolean dark) {
        Obj info = Stats.info(key);
        String L = dark ? "§8" : "§7";
        String V = dark ? "§0" : "§f";
        String head = (dark ? info.str("dark") : info.str("color")) + info.str("name") + " " + stat(key) + plusText(key, dark);
        if (key.equals("vit")) return head + L + " · 최대 체력 " + V + Js.str(maxHp()) + L + " (1당 +" + CONFIG.text("HP_PER_VIT") + ")";
        return head + L + " · " + info.str("weapons") + " " + V + "+" + bonusPct(key, Stats.get(eff, key)) + "%" + L + " (1당 " + bonusPct(key, 1) + "%)";
    }

    /** 스탯 칸 설명 (statui.js buildForm 의 lore) */
    public List<String> tooltip(String key) {
        Obj info = Stats.info(key);
        List<String> lore = new ArrayList<>();
        lore.add(statLine(key, false));
        if (key.equals("vit")) {
            String extra = vitBonusText();
            lore.add("§71포인트: 최대 체력 +" + CONFIG.text("HP_PER_VIT") + (extra.isEmpty() ? "" : " · " + extra));
            lore.add("§7레벨 보너스 +" + Js.str(Hp.levelHpOf(lvmax())) + " (레벨당 +" + CONFIG.text("HP_PER_LEVEL") + ")");
            if (tree("hp") > 0) lore.add("§7스탯 트리 최대 체력 +" + Js.str(tree("hp")));
            Obj tyrfing = SKILLS.objOrNull("tyrfing");
            lore.add("§7티르빙 스킬 피해 × 최대 체력 ÷ " + Js.str(tyrfing == null ? 100 : tyrfing.numOr("hpBase", 100)));
        } else {
            lore.add("§71포인트: " + info.str("effect") + " +" + bonusPct(key, 1) + "%");
        }
        lore.add(point() > 0 ? "§a눌러서 +1 §7(Shift: +5)" : "§8포인트 없음");
        return lore;
    }

    /** 체력 스탯이 피해를 올려 주는 무기 ("도끼·방패 피해 +0.5%") */
    static String vitBonusText() {
        Map<String, List<String>> by = new LinkedHashMap<>();
        for (String k : TYPES.keys()) {
            Obj t = TYPES.obj(k);
            Obj bonus = t.objOrNull("bonus");
            double per = (bonus == null ? 0 : bonus.numOr("vit", 0)) + ("vit".equals(t.strOr("stat", "")) ? t.numOr("perPoint", 0) : 0);
            if (per <= 0) continue;
            by.computeIfAbsent(num(per * 100), x -> new ArrayList<>()).add(t.str("name"));
        }
        StringJoiner j = new StringJoiner(", ");
        by.forEach((pct, names) -> j.add(String.join("·", names) + " 피해 +" + pct + "%"));
        return j.toString();
    }

    static double shieldCut(Weapon w, double vit) {
        return Math.min(SHIELD.num("passiveCap"), w.passive() + vit * SHIELD.num("passivePerVit"));
    }

    /** 무기를 지금 쓸 수 있는지 (레벨 · 찍은 스탯) */
    public boolean usable(Weapon w) {
        return Weapons.weaponLack(w, reqLevel, reqBase(w)).isEmpty();
    }

    /**
     * 스탯 창의 기준 무기 (weapons.js referenceWeapon): 손에 든 RPG 무기 → 가진 무기 중 쓸 수 있는 것 먼저, 같으면 공격력이 높은 것
     * → 왼손.
     */
    public static Weapon referenceWeapon(ItemStack main, ItemStack off, Iterable<ItemStack> inventory, Predicate<Weapon> usable) {
        Weapon m = RpgCombat.weaponOf(main);
        if (m != null) return m;
        Weapon best = null;
        for (ItemStack s : inventory) {
            Weapon w = RpgCombat.weaponOf(s);
            if (w == null) continue;
            if (best == null) {
                best = w;
                continue;
            }
            boolean a = usable.test(w);
            boolean b = usable.test(best);
            if (a != b ? a : w.damage() > best.damage()) best = w;
        }
        return best != null ? best : RpgCombat.weaponOf(off);
    }

    /**
     * 0.23.0 인벤토리 캐릭터 패널: 기준 무기의 지금 수치 {피해 (요구 조건이 모자라면 0), 치명타 확률, 치명타 배율, 쓸 수 있음 1 · 없음 0} —
     * weaponLines 의 "피해 · 치명타" 줄과 같은 식 (강화 enh 단계). 무기가 없으면 null
     */
    public double[] weaponNow(Weapon w, int enh) {
        if (w == null) return null;
        double base0 = w.damage() * (1 + mmorpg.core.Enhance.enhanceBonus(enh));
        double atk = 1 + tree("atk") / 100;
        double critChance = mmorpg.core.Combat.critChanceOf(w, tree("crit") / 100);
        double critMult = w.critMult() + tree("critDmg") / 100;
        double sum = 0;
        Map<String, Double> de = dmgEff(w);
        for (Weapons.Scaling b : w.scaling()) sum += Stats.get(de, b.stat()) * b.per();
        boolean ok = Weapons.weaponLack(w, reqLevel, reqBase(w)).isEmpty();
        return new double[] {ok ? base0 * (1 + sum) * atk : 0, critChance, critMult, ok ? 1 : 0};
    }

    /** 가진 무기 기준 실제 수치 (statui.js weaponLines) — 강화 없이 */
    public List<String> weaponLines(Weapon w, boolean dark) {
        return weaponLines(w, 0, dark);
    }

    /** 가진 무기 기준 실제 수치 (statui.js weaponLines): 강화 enh 단계면 기본 공격력 × (1 + 강화 보너스) */
    public List<String> weaponLines(Weapon w, int enh, boolean dark) {
        String L = dark ? "§8" : "§7";
        String V = dark ? "§0" : "§f";
        String R = dark ? "§4" : "§c";
        List<String> lines = new ArrayList<>();
        if (w == null) {
            lines.add(L + "RPG 무기를 가지고 있으면 실제 피해가 나옵니다.");
            return lines;
        }
        double base0 = w.damage() * (1 + mmorpg.core.Enhance.enhanceBonus(enh));
        lines.add(L + "기준 무기: " + (enh > 0 ? "§6+" + enh + " " : "") + (dark ? w.darkName() : w.name()));
        List<Weapons.Lack> lack = Weapons.weaponLack(w, reqLevel, reqBase(w));
        Map<String, Double> de = dmgEff(w);
        // 스탯 트리 (7단계): 공격력 · 치명타 확률 (평타 상한 RUNE_STRIKE.critCap) · 치명타 피해
        double atk = 1 + tree("atk") / 100;
        double critChance = mmorpg.core.Combat.critChanceOf(w, tree("crit") / 100);
        double critMult = w.critMult() + tree("critDmg") / 100;
        double critAvg = 1 + critChance * (critMult - 1);
        double sum = 0;
        for (Weapons.Scaling b : w.scaling()) sum += Stats.get(de, b.stat()) * b.per();
        double now = base0 * (1 + sum) * atk;
        lines.add(L + "피해 " + V + num(now) + L + " · 치명타 " + V + num(critChance * 100) + "%" + L + " ×" + num(critMult));
        Weapons.Scaling main = w.scaling().get(0);
        lines.add(L + "평균 " + V + num(now * critAvg) + L + " · " + Stats.info(main.stat()).str("name") + " 1당 " + V + "+" + num(base0 * main.per() * atk * critAvg));
        for (int i = 1; i < w.scaling().size(); i++) {
            Weapons.Scaling b = w.scaling().get(i);
            String hp = b.stat().equals("vit") ? L + " · 최대 체력 " + V + "+" + CONFIG.text("HP_PER_VIT") : "";
            lines.add(L + Stats.info(b.stat()).str("name") + " 1당 피해 " + V + "+" + num(base0 * b.per() * atk * critAvg) + hp);
        }
        if (magicSword(w)) {
            // 0.27.0 마검사 「마력 검술」: 마력 1 이 늘 때 피해 배율 스탯이 얼마나 느는지 (core.Jobs.damageStats)
            Jobs.Job j = Jobs.job(job);
            Map<String, Double> plus = new LinkedHashMap<>(eff);
            plus.put("int", Stats.get(eff, "int") + 1);
            Map<String, Double> dp = Jobs.damageStats(j, w.type(), plus);
            double per = 0;
            for (Weapons.Scaling b : w.scaling()) per += (Stats.get(dp, b.stat()) - Stats.get(de, b.stat())) * b.per();
            lines.add(L + "「마력 검술」 마력 1당 피해 " + V + "+" + num(base0 * per * atk * critAvg) + L + " · 요구 힘 = 힘 + 마력");
        }
        if (w.type().equals("shield")) {
            double vit = Stats.get(eff, "vit");
            double per = shieldCut(w, vit + 1) - shieldCut(w, vit);
            lines.add(L + "피해 감소 " + V + num(shieldCut(w, vit) * 100) + "%" + L + " · 체력 1당 " + V + "+" + num(per * 100) + "%p");
        }
        if (w.skills() != null) {
            double hpf = w.type().equals("greatsword") ? Skills.tyrfingHpFactor(maxHp()) : 1;
            StringJoiner parts = new StringJoiner(L + " / " + V);
            for (Obj k : w.skills()) parts.add(num(now * Skills.skillTotalMult(k) * hpf));
            lines.add(L + "스킬 피해 " + V + parts + L + " (스탯 반영)");
            if (w.type().equals("greatsword")) {
                Obj tyrfing = SKILLS.objOrNull("tyrfing");
                lines.add(L + "스킬 × 최대 체력 " + V + num(maxHp()) + L + " ÷ " + Js.str(tyrfing == null ? 100 : tyrfing.numOr("hpBase", 100)) + " = " + V + "×" + num(hpf));
            }
        }
        if (!lack.isEmpty()) {
            String zero = "지금은 " + (w.type().equals("shield") ? "피해·방어" : "피해") + " 0";
            if (lack.stream().anyMatch(Weapons.Lack::isLevel)) {
                lines.add(R + "레벨 " + w.level() + "부터 - " + zero);
            } else {
                StringJoiner req = new StringJoiner(" · ");
                for (Weapons.Req r : w.reqs()) req.add(Stats.info(r.stat()).str("name") + " " + Js.str(r.value()));
                lines.add(R + req + " 필요" + (magicSword(w) ? " (힘 = 힘 + 마력)" : "") + " - " + zero);
            }
        }
        return lines;
    }
}
