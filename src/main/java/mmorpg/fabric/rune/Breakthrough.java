package mmorpg.fabric.rune;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.RuneBreak;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

/**
 * 룬 돌파 (8단계 — 애드온에는 없음, 계산은 규칙 코어 RuneBreak):
 * <ul>
 * <li>단계: 아이템 구성 요소 mmorpg:rune_break (0 ~ 6 — 저장 · 클라이언트에도). 아이콘 배지는 minecraft:custom_model_data 의 첫 수 (아이템 모델이 고름)</li>
 * <li>합치기: 같은 룬 둘 → 남는 룬 (가장 높은 단계 — 같으면 핫바 · 앞 칸) + 재료 (합쳐도 6을 넘지 않는 것 중 가장 높은 단계) + 1. 룬술사 '돌파' 창 (RuneSmith)</li>
 * <li>시전 단계 기록: 룬을 쓸 때 (RuneCast) 사람 · 룬 효과마다 — 초월 룬의 화면 색 연출 (RuneKit.ultSkyFor · ultFlashFor) 이 이 단계를 봄 (3돌 미만이면 없음)</li>
 * <li>연출: 합칠 때 (양쪽 빛이 모여 번쩍 · 별), 초월 3 · 6돌에 이르면 그 단계의 하늘빛 · 번쩍임을 미리 보여 줌, 6돌 초월 룬을 쓰면 금빛 각성</li>
 * </ul>
 */
public final class Breakthrough {
    private Breakthrough() {}

    public static DataComponentType<Integer> LEVEL;
    /** "플레이어|룬 효과" → 지금 쓴 룬의 돌파 단계 */
    private static final Map<String, Integer> CAST = new HashMap<>();
    private static final double[] GOLD = {1.0, 0.8, 0.3};
    private static final double[] CYAN = {0.45, 0.92, 1.0};
    private static final double[] PURPLE = {0.72, 0.42, 1.0};

    /** Mmorpg.onInitialize (아이템보다 먼저 — WeaponEnhance 처럼) */
    public static void initComponent() {
        LEVEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Mmorpg.id("rune_break"),
                DataComponentType.<Integer>builder().persistent(Codec.intRange(0, RuneBreak.max())).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    }

    /** RuneSystem.init */
    static void init() {
        RuneSystem.onForget(id -> {
            String p = id + "|";
            CAST.keySet().removeIf(k -> k.startsWith(p));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CAST.clear());
    }

    // ------------------------------------------------------------ 아이템의 단계

    /** 룬 아이템의 돌파 단계 (없으면 0) */
    public static int of(ItemStack stack) {
        if (stack == null || stack.isEmpty() || LEVEL == null) return 0;
        Integer v = stack.get(LEVEL);
        return v == null ? 0 : RuneBreak.clamp(v);
    }

    /** 돌파 단계를 적음 (0 이면 지움) — 아이콘 배지용 custom_model_data 도 함께 */
    public static void write(ItemStack stack, int level) {
        int lv = RuneBreak.clamp(level);
        if (lv > 0) {
            stack.set(LEVEL, lv);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) lv), List.of(), List.of(), List.of()));
        } else {
            stack.remove(LEVEL);
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    /** 돌파 단계가 적힌 룬 한 개 (없는 룬이면 빈 칸) */
    public static ItemStack stack(String family, String grade, int level) {
        ItemStack s = RuneItems.stack(family, grade);
        if (!s.isEmpty()) write(s, level);
        return s;
    }

    /** 이름 뒤 단계 (" §e[3돌]" — 0돌이면 "") */
    public static String nameTag(int level) {
        String t = RuneBreak.tag(level);
        return t.isEmpty() ? "" : " §e" + t;
    }

    // ------------------------------------------------------------ 시전 단계 (화면 색 연출)

    private static String castKey(ServerPlayer sp, String fn) {
        return sp.getUUID() + "|" + fn;
    }

    /** 룬을 쓸 때 (RuneCast — 효과보다 먼저): 이 사람 · 이 룬 효과의 지금 단계 */
    static void noteCast(ServerPlayer sp, Runes.Rune r, int level) {
        String fn = r.def().strOrNull("fn");
        if (fn != null) CAST.put(castKey(sp, fn), RuneBreak.clamp(level));
    }

    /** 이 사람이 마지막으로 쓴 룬 효과 fn 의 돌파 단계 (없으면 0) */
    public static int castLevel(ServerPlayer sp, String fn) {
        Integer v = CAST.get(castKey(sp, fn));
        return v == null ? 0 : v;
    }

    /** 화면 색 연출 단계 (0 = 없음 · 3 · 6) */
    public static RuneBreak.Fx castFx(ServerPlayer sp, String fn) {
        return RuneBreak.fx(RuneBreak.fxTier(castLevel(sp, fn)));
    }

    /** 룬이 나간 뒤 (RuneCast): 6돌 초월 룬이면 금빛 각성 */
    static void onCast(ServerPlayer sp, Runes.Rune r, int level) {
        if (!RuneBreak.tiered(r)) return;
        RuneBreak.Fx fx = RuneBreak.fx(RuneBreak.fxTier(level));
        if (fx != null && fx.awaken()) awakenFx(sp);
    }

    // ------------------------------------------------------------ 합치기

    /** 가방 칸의 룬 하나 (slot 0 ~ 35 · 룬 · 돌파 단계) */
    public record Held(int slot, Runes.Rune r, int level) {}

    /** 합치기 하나: 남는 룬 · 재료 · 결과 단계 · 결과를 둘 칸 (재료만 핫바에 있으면 핫바 칸) */
    public record Merge(Held target, Held material, int result, int slot) {}

    /** 가방의 룬 (칸 순서) */
    public static List<Held> held(ServerPlayer sp) {
        List<Held> out = new ArrayList<>();
        for (int i = 0; i < Inv.SIZE; i++) {
            ItemStack s = Inv.get(sp, i);
            Runes.Rune r = RuneItems.runeOf(s);
            if (r != null && s.getCount() == 1) out.add(new Held(i, r, of(s)));
        }
        return out;
    }

    /** 남는 룬 고르기 순서: 높은 단계 → 핫바 → 앞 칸 */
    private static final Comparator<Held> TARGET_ORDER = Comparator.<Held>comparingInt(h -> -h.level())
            .thenComparingInt(h -> h.slot() < 9 ? 0 : 1).thenComparingInt(Held::slot);

    /** 같은 룬들 중 합치기 하나 (남는 룬 = 가장 높은 단계, 재료 = 합쳐도 넘치지 않는 것 중 가장 높은 단계 — 없으면 null) */
    public static Merge bestMerge(List<Held> same) {
        if (same.size() < 2) return null;
        List<Held> sorted = new ArrayList<>(same);
        sorted.sort(TARGET_ORDER);
        Held target = sorted.get(0);
        Held mat = null;
        for (int i = 1; i < sorted.size(); i++) {
            Held h = sorted.get(i);
            if (!RuneBreak.canMerge(target.level(), h.level())) continue;
            if (mat == null || h.level() > mat.level() || (h.level() == mat.level() && h.slot() > mat.slot())) mat = h; // 같은 단계면 뒤 칸 (가방) 을 재료로
        }
        if (mat == null) return null;
        int slot = mat.slot() < 9 && target.slot() >= 9 ? mat.slot() : target.slot();
        return new Merge(target, mat, RuneBreak.merged(target.level(), mat.level()), slot);
    }

    /** 룬 ID → 가방의 그 룬들 */
    private static Map<String, List<Held>> byId(ServerPlayer sp) {
        Map<String, List<Held>> m = new LinkedHashMap<>();
        for (Held h : held(sp)) m.computeIfAbsent(h.r().id(), k -> new ArrayList<>()).add(h);
        return m;
    }

    /** 지금 할 수 있는 돌파 (룬마다 하나 — 낮은 등급 · 계열 순서) */
    public static List<Merge> options(ServerPlayer sp) {
        List<Merge> out = new ArrayList<>();
        for (List<Held> same : byId(sp).values()) {
            Merge m = bestMerge(same);
            if (m != null) out.add(m);
        }
        out.sort(Comparator.<Merge>comparingInt(m -> Runes.GRADE_ORDER.indexOf(m.target().r().grade()))
                .thenComparingInt(m -> Runes.FAMILY_ORDER.indexOf(m.target().r().family())));
        return out;
    }

    /** 모두 돌파 계획: 룬마다 가장 높은 것 하나를 넘치지 않게 끝까지 (차례대로 — 앞 합치기의 결과 단계를 이어 씀) */
    public static List<Merge> planAll(ServerPlayer sp) {
        List<Merge> out = new ArrayList<>();
        for (List<Held> same : byId(sp).values()) {
            List<Held> left = new ArrayList<>(same);
            for (int guard = 0; guard < 64; guard++) {
                Merge m = bestMerge(left);
                if (m == null) break;
                out.add(m);
                left.remove(m.material());
                left.remove(m.target());
                left.add(new Held(m.slot(), m.target().r(), m.result())); // 남는 룬은 늘 가장 높은 단계라 다음에도 남는 룬
            }
        }
        return out;
    }

    /**
     * 합치기 (칸에 그 룬 · 그 단계가 그대로 있을 때만): 재료를 가져가고 남는 룬의 단계를 올림 + 연출. 결과 문구 (창 본문용 — 실패면 "§4…")
     */
    public static String merge(ServerPlayer sp, Merge m) {
        String err = apply(sp, m);
        if (err != null) {
            SkillFx.playFor(sp, "note.bass", 0.6, 1);
            return err;
        }
        sp.getInventory().setChanged();
        mergeFx(sp, m.target().r(), m.target().level(), m.result());
        Runes.Rune r = m.target().r();
        Notify.notify(sp, "§b돌파! " + r.gradeColor() + r.def().str("name") + " §e" + RuneBreak.tag(m.result()) + " §7(계수 " + RuneBreak.coefText(m.result()) + ")", 50);
        return "§2돌파: " + r.def().str("name") + " " + m.target().level() + "돌 → §5" + m.result() + "돌§2 (계수 " + RuneBreak.coefText(m.result()) + ")§r";
    }

    /** 모두 돌파 (planAll 차례대로 — 칸이 바뀌어 하나라도 안 되면 거기서 멈춤). 결과 문구 */
    public static String mergeAll(ServerPlayer sp, List<Merge> plan) {
        int done = 0;
        Map<String, int[]> best = new LinkedHashMap<>(); // 룬 ID → {처음, 끝} 단계
        Map<String, Runes.Rune> runes = new LinkedHashMap<>();
        for (Merge m : plan) {
            if (apply(sp, m) != null) break;
            done++;
            String id = m.target().r().id();
            best.computeIfAbsent(id, k -> new int[] {m.target().level(), m.result()})[1] = m.result();
            runes.put(id, m.target().r());
        }
        // 연출은 하나: 초월 룬 먼저, 그다음 높은 단계
        Runes.Rune top = null;
        int[] topFt = null;
        for (Map.Entry<String, int[]> e : best.entrySet()) {
            Runes.Rune r = runes.get(e.getKey());
            int[] ft = e.getValue();
            boolean better = top == null || (RuneBreak.tiered(r) && !RuneBreak.tiered(top)) || (RuneBreak.tiered(r) == RuneBreak.tiered(top) && ft[1] > topFt[1]);
            if (better) {
                top = r;
                topFt = ft;
            }
        }
        if (done == 0) {
            SkillFx.playFor(sp, "note.bass", 0.6, 1);
            return "§4룬이 옮겨져서 취소했습니다. 다시 골라 주세요.§r";
        }
        sp.getInventory().setChanged();
        mergeFx(sp, top, topFt[0], topFt[1]);
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, int[]> e : best.entrySet()) parts.add(runes.get(e.getKey()).def().str("name") + " " + e.getValue()[1] + "돌");
        String list = String.join(" · ", parts.subList(0, Math.min(3, parts.size()))) + (parts.size() > 3 ? " 외 " + (parts.size() - 3) : "");
        Notify.notify(sp, "§b돌파 " + done + "번 §7(" + list + ")", 60);
        return "§2돌파 " + done + "번: §5" + list + "§r";
    }

    /** 합치기 하나를 가방에 (칸 확인) — 실패 문구, 되면 null. 연출 없음 */
    private static String apply(ServerPlayer sp, Merge m) {
        Held t = m.target();
        Held mat = m.material();
        if (t.slot() == mat.slot() || !t.r().id().equals(mat.r().id()) || (m.slot() != t.slot() && m.slot() != mat.slot())) return "§4같은 룬 둘이 필요합니다.§r";
        if (!RuneBreak.canMerge(t.level(), mat.level())) return "§4" + RuneBreak.max() + "돌을 넘어서 합칠 수 없습니다.§r";
        if (!same(sp, t) || !same(sp, mat)) return "§4룬이 옮겨져서 취소했습니다. 다시 골라 주세요.§r";
        ItemStack keep = Inv.get(sp, t.slot()).copy();
        write(keep, RuneBreak.merged(t.level(), mat.level()));
        Inv.set(sp, mat.slot(), ItemStack.EMPTY);
        Inv.set(sp, t.slot(), ItemStack.EMPTY);
        Inv.set(sp, m.slot(), keep);
        return null;
    }

    /** 그 칸에 그 룬 · 그 단계 (한 개) 가 그대로 있는지 */
    private static boolean same(ServerPlayer sp, Held h) {
        if (h.slot() < 0 || h.slot() >= Inv.SIZE) return false;
        ItemStack s = Inv.get(sp, h.slot());
        Runes.Rune r = RuneItems.runeOf(s);
        return r != null && r.id().equals(h.r().id()) && of(s) == h.level() && s.getCount() == 1;
    }

    // ------------------------------------------------------------ 연출

    /** 눈앞 연출 자리 (보는 쪽 수평으로 2.2칸, 발에서 1.4칸 위 — 룬술사 창 연출과 같음) */
    private static Vec3 faceAt(ServerPlayer sp) {
        Vec3 f = V.flat(sp.getViewVector(1F));
        return V.up(V.add(sp.position(), f, 2.2), 1.4);
    }

    /**
     * 합치기 연출: 눈앞 양쪽에서 두 룬의 빛 (등급 색 작은 마법진 + 빛 알갱이) 이 가운데로 모임 → 6틱 뒤 번쩍 (큰 마법진 · 빛 · 고리) + "돌파! ★★★☆☆☆".
     * 초월 룬이 3 · 6돌에 이르면 그 단계를 열어 보임 (tierFx)
     */
    static void mergeFx(ServerPlayer sp, Runes.Rune r, int from, int to) {
        if (r == null || sp.isRemoved()) return;
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 side = V.rightOf(f);
        Vec3 at = faceAt(sp);
        double[] col = RuneKit.RUNE_GRADE_FX.getOrDefault(r.grade(), PURPLE);
        for (int sd : new int[] {-1, 1}) {
            Vec3 p = V.add(at, side, sd * 1.15);
            SkillFx.fx(level, "rpg:magic_circle_flash", p, Opt.of("size", 0.34, "life", 0.32, "spin", Kit.rand() * 360, "progress", 1.0, "color", col, "alpha", 1.0));
            for (int i = 0; i < 5; i++) {
                SkillFx.fx(level, "rpg:arrow_spark", V.up(p, (i - 2) * 0.12), Opt.of("dir", V.scale(side, -sd), "speed", 3.6, "size", 0.12, "grow", 0.4,
                        "life", 0.32, "color", i % 2 == 0 ? col : CYAN, "alpha", 1.0));
            }
        }
        SkillFx.play(level, "block.smithing_table.use", sp.position(), 1.0, 0.8);
        RuneKit.later(6, () -> {
            if (sp.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 c = faceAt(sp);
            Vec3 ff = V.flat(sp.getViewVector(1F));
            int gi = Math.max(0, Runes.GRADE_ORDER.indexOf(r.grade()));
            SkillFx.fx(lv, "rpg:magic_circle_flash", c, Opt.of("size", 0.7 + gi * 0.08 + to * 0.04, "life", 0.5, "spin", Kit.rand() * 360, "progress", 1.0,
                    "color", col, "alpha", 1.0));
            SkillFx.burst(lv, c, col, 0.9 + to * 0.12, 0.32);
            SkillFx.burst(lv, c, CYAN, 0.5 + to * 0.06, 0.25);
            SkillFx.ring(lv, c, ff, CYAN, 0.6 + to * 0.08, 0.4, 2.0);
            SkillFx.sparks(lv, c, col, 8 + to * 2, 4);
            SkillFx.play(lv, "chime.amethyst_block", sp.position(), 0.8 + to * 0.12, 1.0);
            SkillFx.play(lv, "random.anvil_use", sp.position(), 1.4, 0.35);
            if (to >= RuneBreak.max()) SkillFx.play(lv, "random.levelup", sp.position(), 1.0, 0.7);
            DamageNumbers.showFace(sp, "§b§l돌파! §e" + RuneBreak.stars(to), 2.0, 0, 0.55);
        });
        if (RuneBreak.tiered(r)) {
            int crossed = 0; // 이번에 넘은 가장 높은 단계 (0 → 6 이면 6만 보여 줌)
            for (int t : RuneBreak.tiers()) if (from < t && to >= t) crossed = t;
            int tier = crossed;
            if (tier > 0) RuneKit.later(10, () -> tierFx(sp, r, tier));
        }
    }

    /**
     * 초월 룬이 3 · 6돌에 이름: 그 룬의 하늘빛 · 번쩍임을 이 사람에게만 미리 보여 줌 (6돌 = 짙고 테두리 빛) + 발밑 금빛 문양 · 빛기둥 + 제목 · 채팅 (바뀐 값)
     */
    static void tierFx(ServerPlayer sp, Runes.Rune r, int tier) {
        if (sp.isRemoved()) return;
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String fn = r.def().str("fn");
        Obj u = RuneKit.ultOf(fn);
        RuneBreak.Fx fx = RuneBreak.fx(tier);
        String fog = u.strOrNull("fog");
        if (fog == null && u.listOrNull("fogs") != null && !u.list("fogs").isEmpty()) fog = (String) u.list("fogs").get(0);
        if (fx != null) Ult.setSky(sp, fog, (int) Math.round(70 * fx.skyTicks()), fx.skyDist(), fx.edge());
        List<Object> flash = u.listOrNull("flash");
        if (flash != null) Ult.flash(level, c, Kit.rgb(flash), Opt.of("radius", 0.1, "time", new double[] {0.04, fx != null ? fx.flashHold() : 0, 0.5}), sp);
        boolean six = tier >= RuneBreak.max();
        double[] glow = six ? GOLD : RuneKit.RUNE_GRADE_FX.get("transcend");
        RuneKit.domainSigil(level, c, six ? 3.6 : 2.8, "holy", glow, RuneKit.WHITE, Opt.of("life", 1.6, "spinRate", 40.0, "grow", 0.3, "alpha", 0.85));
        RuneKit.godRays(sp, level, c, 5, six ? 6 : 4, glow, RuneKit.WHITE, Opt.of("h", 14.0, "life", 1.0));
        SkillFx.vanilla(level, "minecraft:totem_particle", V.up(c, 1.2));
        SkillFx.play(level, "beacon.activate", c, six ? 1.3 : 1.0, 0.9);
        SkillFx.play(level, six ? "random.totem" : "random.levelup", c, 1.0, 0.6);
        String name = fx != null ? fx.name() : "";
        Notify.title(sp, "§6§l초월 돌파 " + tier + "돌", "§d" + r.def().str("name") + " §f· " + name, 5, 50, 15);
        List<String> ch = RuneBreak.changeTexts(r, tier);
        Notify.say(sp, "§6초월 돌파 " + tier + "돌 §f" + r.def().str("name") + " §7— " + String.join(" · ", ch) + (fx != null ? " §d+ " + fx.name() + " (" + fx.text() + ")" : ""));
    }

    /** 6돌 초월 룬을 쓸 때: 발밑 금빛 성광 문양 · 충격 고리 · 머리 위 빛기둥 · 몸의 금빛 기운 · 1인칭 고리 + "★6 각성" */
    static void awakenFx(ServerPlayer sp) {
        if (sp.isRemoved()) return;
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        RuneKit.domainSigil(level, c, 2.4, "holy", GOLD, RuneKit.WHITE, Opt.of("life", 1.1, "spinRate", 90.0, "grow", 0.35, "alpha", 0.9));
        RuneKit.novaRing(level, c, 1.2, 3.0, GOLD, RuneKit.WHITE, Opt.of("life", 0.55));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 5.0), Opt.of("dir", V.UP, "speed", 0.0, "size", 3.0, "thick", 0.9, "life", 0.6, "color", GOLD, "alpha", 0.8));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 5.0), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.8, "thick", 0.28, "life", 0.5, "color", RuneKit.WHITE,
                "alpha", 1.0));
        RuneKit.selfCue(sp, GOLD, RuneKit.WHITE, true);
        RuneKit.bodyAura(sp, GOLD, Opt.of("n", 6, "size", 1.2, "alpha", 0.85, "life", 0.4, "force", true));
        SkillFx.vanilla(level, "minecraft:totem_particle", V.up(c, 1.2));
        SkillFx.play(level, "beacon.activate", c, 1.4, 0.8);
        SkillFx.play(level, "random.levelup", c, 0.7, 0.45);
        DamageNumbers.showFace(sp, "§6§l★" + RuneBreak.max() + " 각성", 2.6, 0, 0.8);
    }
}
