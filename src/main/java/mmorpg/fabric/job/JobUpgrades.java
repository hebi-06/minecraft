package mmorpg.fabric.job;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import mmorpg.core.Economy;
import mmorpg.core.Jobs;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.shop.Shop;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 0.31.0 직업 스킬 강화 (사용자 "npc 한테 가서 일부 드랍 템과 골드를 주면 스킬 계수를 올려주는 것 — 계수는 한 5돌 정도 딜" · "완전 노가다 해야 업그레이드
 * 되게" → 고른 것: 스킬마다 따로 · 필드 드랍 + 보스 재료 · 단계마다 레벨 (44 ~ 60) · 피해 · 회복 · 보호막 모두).
 * <ul>
 * <li>기록: 스킬마다 0 ~ 5단계 (Props "rpg:job_up" — "스킬 id:단계" 를 쉼표로, 직업을 바꿔도 남음)</li>
 * <li>계수 = 1 + 0.1 × 단계 (core Jobs.upMult — 5단계 ×1.5 = 룬 5돌): 시전 위력 P 에 곱함 (JobSystem — 모든 타격), 스킬의 up 키 값 (회복 · 보호막 ·
 * 평타 강화 · 지속) 은 {@link #num} 으로 읽는 곳에서 곱함</li>
 * <li>값 (core Jobs.upStep): 최고 레벨 · 골드 (지갑 먼저, 모자라면 가방의 금화) · 가방의 아이템 (필드 전리품 · 4 · 5단계 보스 재료)</li>
 * <li>창: 직업 교관의 '직업 스킬 강화' (스킬 다섯 칸 — 단계 · 지금 · 다음 단계 · 재료 ✔ ✘) → 확인 창 (필요 레벨 · 골드 · 재료 칸) → 연출 (재료 빛이 눈앞으로
 * 모임 → 터짐 · 빛기둥 · 화면 가운데 스킬 아이콘 · 별 — {@link FxPayload}) → 창이 다시 열림. 5단계는 모두에게 알림</li>
 * </ul>
 */
public final class JobUpgrades {
    private JobUpgrades() {}

    /** 플레이어 속성 이름 ("swordsman_flurry:2,swordsman_ult:5") */
    public static final String PROP = "rpg:job_up";
    /** 재료 빛이 모이는 틱 (그 뒤에 값을 받고 단계가 오름) */
    public static final int GATHER = 26;
    /** 오른 뒤 화면 연출을 보여 주는 틱 (그 뒤에 창이 다시 열림) */
    public static final int SHOW = 44;

    /** 창의 칸 */
    static final int JOB_SLOT = 0;
    static final int GOLD_SLOT = 4;
    static final int BACK_SLOT = 8;
    static final int[] SKILL_SLOTS = {11, 12, 13, 14, 15};
    static final int GUIDE_SLOT = 22;
    /** 확인 창: 필요한 것 (윗줄 가운데부터) · 강화 · 스킬 · 취소 */
    static final int NEED_FIRST = 2;
    static final int OK_SLOT = 10;
    static final int CARD_SLOT = 13;
    static final int NO_SLOT = 16;

    private record Cache(String raw, Map<String, Integer> map) {}

    private static final Map<UUID, Cache> CACHE = new ConcurrentHashMap<>();

    // ------------------------------------------------------------ 기록

    /** 스킬 id → 단계 (1 ~ 5 만 — 0 은 없음) */
    public static Map<String, Integer> levels(ServerPlayer sp) {
        if (sp == null) return Map.of();
        String raw = Props.get(sp, PROP);
        if (raw == null) raw = "";
        Cache c = CACHE.get(sp.getUUID());
        if (c != null && c.raw().equals(raw)) return c.map();
        Map<String, Integer> m = parse(raw);
        CACHE.put(sp.getUUID(), new Cache(raw, m));
        return m;
    }

    static Map<String, Integer> parse(String raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return Map.of();
        for (String part : raw.split(",")) {
            int c = part.lastIndexOf(':');
            if (c <= 0) continue;
            try {
                int n = Jobs.upClamp(Integer.parseInt(part.substring(c + 1).trim()));
                if (n > 0) out.put(part.substring(0, c).trim(), n);
            } catch (NumberFormatException e) {
                // 망가진 조각은 버림
            }
        }
        return Map.copyOf(out);
    }

    /** 이 스킬의 강화 단계 (0 ~ 5) */
    public static int level(ServerPlayer sp, String skillId) {
        if (skillId == null || skillId.isEmpty()) return 0;
        Integer n = levels(sp).get(skillId);
        return n == null ? 0 : n;
    }

    /** 단계를 정함 (0 = 지움 · 5 를 넘으면 5) — 칸을 다시 보냄 */
    public static void set(ServerPlayer sp, String skillId, int level) {
        if (sp == null || skillId == null || skillId.isEmpty()) return;
        Map<String, Integer> m = new LinkedHashMap<>(levels(sp));
        int n = Jobs.upClamp(level);
        if (n <= 0) m.remove(skillId);
        else m.put(skillId, n);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            if (b.length() > 0) b.append(',');
            b.append(e.getKey()).append(':').append(e.getValue());
        }
        Props.set(sp, PROP, b.length() == 0 ? null : b.toString());
        JobSystem.refresh(sp, true);
    }

    /** 그 직업 다섯 스킬의 단계 합 (0 ~ 25) */
    public static int total(ServerPlayer sp, Jobs.Job j) {
        if (j == null) return 0;
        int n = 0;
        for (Jobs.Skill k : j.skills()) n += level(sp, k.id());
        return n;
    }

    // ------------------------------------------------------------ 계수 · 값

    /** 강화 계수 (1 ~ 1.5) — 시전 위력 P 에 곱함 */
    public static double mult(ServerPlayer sp, Jobs.Skill k) {
        return k == null ? 1 : Jobs.upMult(level(sp, k.id()));
    }

    /** 스킬 값 (강화가 곱하는 up 키면 계수를 곱한 값 — 아니면 그대로) */
    public static double num(ServerPlayer sp, Jobs.Skill k, String key, double fallback) {
        if (k == null) return fallback;
        return Jobs.upValue(k, key, k.num(key, fallback), level(sp, k.id()));
    }

    /** 틱 값 (지속 — 강화가 곱하면 반올림) */
    public static int ticks(ServerPlayer sp, Jobs.Skill k, String key, int fallback) {
        return (int) Math.round(num(sp, k, key, fallback));
    }

    // ------------------------------------------------------------ 재료

    /** 재료 하나 (아이템 · 이름 · § 색 · 빛 색 · 얻는 곳) */
    public record Mat(String id, Item item, String name, String code, double[] rgb, String from) {}

    private static final Map<String, Mat> MATS = new ConcurrentHashMap<>();
    private static final Map<Character, double[]> CODE_RGB = Map.ofEntries(Map.entry('5', new double[] {0.7, 0.35, 0.95}),
            Map.entry('3', new double[] {0.2, 0.75, 0.85}), Map.entry('e', new double[] {1, 0.92, 0.4}), Map.entry('6', new double[] {1, 0.66, 0.2}),
            Map.entry('c', new double[] {1, 0.38, 0.32}), Map.entry('b', new double[] {0.45, 0.9, 1}), Map.entry('a', new double[] {0.5, 1, 0.45}),
            Map.entry('d', new double[] {1, 0.5, 0.95}), Map.entry('2', new double[] {0.3, 0.8, 0.35}), Map.entry('9', new double[] {0.4, 0.55, 1}),
            Map.entry('4', new double[] {0.85, 0.15, 0.12}), Map.entry('f', new double[] {1, 1, 1}), Map.entry('7', new double[] {0.75, 0.75, 0.8}));
    /** 골드 빛 */
    static final double[] GOLD_RGB = {1, 0.8, 0.28};

    /** 아이템 id 의 재료 정보 (규칙의 몬스터 전리품 · 보스 재료 — 없으면 아이템 id 그대로) */
    public static Mat mat(String id) {
        return MATS.computeIfAbsent(id, JobUpgrades::findMat);
    }

    private static Mat findMat(String id) {
        Rules r = Rules.get();
        Obj drops = r.obj("MONSTER_DROPS");
        for (String key : drops.keys()) {
            Obj d = drops.obj(key);
            if (!id.equals(d.strOr("item", ""))) continue;
            Obj mon = r.obj("MONSTERS").objOrNull(key);
            String from = mon == null ? "" : mon.strOr("name", key) + " (" + Js.str(mon.numOr("level", 0)) + "렙) 전리품 · " + Math.round(d.numOr("chance", 0.5) * 100) + "%";
            List<Double> c = d.numbersOrNull("rgb");
            double[] rgb = c != null && c.size() == 3 ? new double[] {c.get(0), c.get(1), c.get(2)} : new double[] {0.8, 0.8, 0.8};
            return new Mat(id, Icon.itemById(id), d.strOr("name", id), d.strOr("color", "§f"), rgb, from);
        }
        Obj boss = r.obj("BOSS_MATERIALS");
        for (String key : boss.keys()) {
            Obj m = boss.obj(key);
            if (!id.equals(m.strOr("item", ""))) continue;
            Obj b = r.obj("BOSSES").objOrNull(m.strOr("boss", ""));
            String from = b == null ? "보스 재료" : b.strOr("name", "보스") + " 보스 재료";
            String code = m.strOr("color", "§f");
            double[] rgb = code.length() >= 2 ? CODE_RGB.getOrDefault(code.charAt(1), new double[] {1, 1, 1}) : new double[] {1, 1, 1};
            return new Mat(id, Icon.itemById(id), m.strOr("name", id), code, rgb, from);
        }
        return new Mat(id, Icon.itemById(id), id, "§f", new double[] {1, 1, 1}, "");
    }

    /** 가진 재료 하나 */
    public record Have(Jobs.UpItem need, Mat mat, int have) {
        public boolean ok() {
            return have >= need.count();
        }
    }

    /** 다음 단계로 올리는 조건 (step null = 이미 5단계) */
    public record Need(Jobs.Skill k, int from, Jobs.UpStep step, int myLevel, long gold, List<Have> items) {
        public boolean max() {
            return step == null;
        }

        public boolean levelOk() {
            return step != null && myLevel >= step.level();
        }

        public boolean goldOk() {
            return step != null && gold >= step.gold();
        }

        public boolean itemsOk() {
            for (Have h : items) if (!h.ok()) return false;
            return true;
        }

        public boolean ok() {
            return !max() && levelOk() && goldOk() && itemsOk();
        }
    }

    /** 이 스킬의 다음 단계 조건 (지금 가진 것과) */
    public static Need need(ServerPlayer sp, Jobs.Skill k) {
        int from = level(sp, k.id());
        Jobs.UpStep s = from >= Jobs.upMax() ? null : Jobs.upStep(from + 1);
        int lv = (int) Math.floor(RpgCombat.reqLevelOf(sp));
        long gold = Wallet.total(sp, Economy.Kind.GOLD);
        List<Have> items = new ArrayList<>();
        if (s != null) {
            for (Jobs.UpItem it : s.items()) {
                Mat m = mat(it.id());
                items.add(new Have(it, m, m.item() == null ? 0 : Inv.count(sp, m.item())));
            }
        }
        return new Need(k, from, s, lv, gold, List.copyOf(items));
    }

    /** 안 되는 까닭 ("" = 됨) */
    public static String why(Need n) {
        if (n.max()) return "이미 최대 강화입니다 (+" + Jobs.upMax() + ")";
        if (!n.levelOk()) return "+" + n.step().to() + " 은(는) " + n.step().level() + "렙부터입니다 (지금 " + n.myLevel() + "렙)";
        if (!n.goldOk()) return "골드가 모자랍니다 (필요 " + n.step().gold() + ", 보유 " + Economy.fmt(n.gold()) + ")";
        for (Have h : n.items()) {
            if (!h.ok()) return h.mat().name() + " 이(가) 모자랍니다 (필요 " + h.need().count() + ", 보유 " + h.have() + ")";
        }
        return "";
    }

    /** 값을 받음 (아이템 먼저 — 같은 틱에 확인했으므로 모자라지 않음) */
    static boolean pay(ServerPlayer sp, Need n) {
        if (!n.ok()) return false;
        for (Have h : n.items()) if (h.mat().item() == null) return false;
        for (Have h : n.items()) if (!Inv.remove(sp, h.mat().item(), h.need().count())) return false;
        return Wallet.pay(sp, Economy.Kind.GOLD, n.step().gold());
    }

    // ------------------------------------------------------------ 강화 한 번

    /**
     * 강화 한 번: 조건 → 재료 빛이 모이는 연출 ({@link #GATHER} 틱 — 창 없이, Forms.busy) → 그 틱에 다시 보고 값을 받아 단계를 올림 · 터지는 연출 · 화면 연출
     * ({@link #SHOW} 틱) → done (결과 문구 — 창 본문). 모이는 동안 쓰러지거나 나가면 done 을 부르지 않음 (값은 받지 않음)
     */
    public static void upgrade(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, Consumer<String> done) {
        Need n = need(sp, k);
        String w = why(n);
        if (!w.isEmpty()) {
            Shop.angry(sp);
            done.accept("§4" + w + "§r");
            return;
        }
        String life = RuneKit.lifeToken(sp);
        gatherFx(sp, j, n);
        Forms.busy(sp, GATHER + 2);
        Tasks.later(GATHER, () -> {
            if (sp.isRemoved() || sp.isDeadOrDying() || !RuneKit.sameLife(sp, life)) return;
            String msg = apply(sp, j, k);
            if (!msg.startsWith(DONE)) {
                done.accept(msg);
                return;
            }
            Forms.busy(sp, SHOW + 2);
            Tasks.later(SHOW, () -> {
                if (sp.isRemoved() || sp.isDeadOrDying() || !RuneKit.sameLife(sp, life)) return;
                done.accept(msg);
            });
        });
    }

    /** 올린 결과 문구의 앞 (창 본문 — 그 밖은 실패) */
    static final String DONE = "§2강화: ";

    /** 다시 보고 값을 받아 단계를 올림 + 연출. 결과 문구 */
    static String apply(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        Need n = need(sp, k);
        String w = why(n);
        if (!w.isEmpty()) {
            Shop.angry(sp);
            return "§4" + w + " — 강화하지 않았습니다.§r";
        }
        if (!pay(sp, n)) return "§4값을 받지 못했습니다.§r";
        int to = n.step().to();
        set(sp, k.id(), to);
        doneFx(sp, j, k, n.from(), to);
        Notify.say(sp, "§6[스킬 강화] " + JobTrainer.code(j) + k.name() + " §e+" + to + " " + Jobs.upStars(to) + " §7— " + String.join(" · ", Jobs.upNow(k, to)));
        if (to >= Jobs.upMax()) announce(sp, j, k);
        return DONE + "§0" + k.name() + " §5+" + to + "§2 — " + String.join(" · ", Jobs.upNow(k, to)) + "§r";
    }

    /** 5단계: 모두에게 알림 (채팅 · 폭죽 소리) */
    private static void announce(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        MinecraftServer server = sp.level().getServer();
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava("§6§l[스킬 강화] §r§f" + sp.getName().getString() + "님이 "
                + JobTrainer.code(j) + j.name() + "§f의 [" + k.name() + "] 을(를) §e§l+" + Jobs.upMax() + " (최대)§r§f 로 강화했습니다!")), false);
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            SkillFx.playFor(pl, "firework.large_blast", 1.0, 0.7);
            if (pl != sp) SkillFx.playFor(pl, "firework.twinkle", 1.2, 0.5);
        }
    }

    // ------------------------------------------------------------ 연출

    /** 눈앞 모이는 자리 (눈에서 보는 쪽 수평 2.2칸 · 0.1칸 위 — 1인칭 화면 가운데) */
    static Vec3 focus(ServerPlayer sp) {
        Vec3 f = V.flat(sp.getViewVector(1F));
        return V.up(V.add(sp.getEyePosition(), f, 2.2), 0.1);
    }

    /**
     * 모이는 연출 (GATHER 틱): 발밑 직업 색 고리 · 몸의 빛 (3인칭), 눈앞으로 재료마다 그 색의 빛 (골드 = 금빛, 전리품 · 보스 재료 = 그 색) 이 몸 둘레 앞쪽에서
     * 2틱마다 날아와 모이고, 그 자리의 직업 색 구체가 점점 커짐 · 차오르는 소리
     */
    static void gatherFx(ServerPlayer sp, Jobs.Job j, Need n) {
        ServerLevel level = sp.level();
        Vec3 feet = sp.position();
        JobFx.ring(level, V.up(feet, 0.06), 2.6, j, false, 1.5, Opt.empty());
        JobFx.aura(sp, j, 2.4, 1.4, 0.7);
        SkillFx.play(level, "block.enchantment_table.use", feet, 1.0, 1.0);
        SkillFx.play(level, "block.smithing_table.use", feet, 0.9, 0.7);
        List<double[]> colors = new ArrayList<>();
        colors.add(GOLD_RGB);
        for (Have h : n.items()) colors.add(h.mat().rgb());
        String life = RuneKit.lifeToken(sp);
        for (int wave = 0; wave < 8; wave++) {
            final int wv = wave;
            RuneKit.later(wave * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 to = focus(sp);
                Vec3 f = V.flat(sp.getViewVector(1F));
                Vec3 side = V.rightOf(f);
                for (int i = 0; i < colors.size(); i++) {
                    // 몸 양옆 (보는 쪽에서 35 ~ 110° — 물결마다 좌우를 바꿈) · 허리 ~ 머리 위에서: 1인칭 화면 가장자리에서 가운데로 날아옴
                    double sign = (i + wv) % 2 == 0 ? 1 : -1;
                    double a = Math.toRadians(sign * (35 + Kit.rand() * 75));
                    double r = 1.7 + Kit.rand() * 0.6;
                    Vec3 from = V.add(V.add(V.up(sp.position(), 0.4 + Kit.rand() * 1.8), f, Math.cos(a) * r), side, Math.sin(a) * r);
                    double[] c = colors.get(i);
                    JobFx.bolt(lv, from, to, c, lighter(c), 5.5 + Kit.rand() * 1.5, 0.32, Opt.empty());
                }
            });
        }
        double[] glow = JobKit.glow(j);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            RuneKit.later(4 + i * 7, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.orb(sp.level(), focus(sp), 0.16 + ii * 0.08, j, 0.5, Opt.of("alpha", 0.9));
                if (ii == 1) SkillFx.play(sp.level(), "block.respawn_anchor.charge", sp.position(), 1.2, 0.8);
            });
        }
        RuneKit.later(14, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.sparks(sp.level(), focus(sp), glow, 6, 2);
        });
    }

    /** 빛의 심지 색 (흰빛 쪽으로) */
    static double[] lighter(double[] c) {
        return new double[] {c[0] + (1 - c[0]) * 0.6, c[1] + (1 - c[1]) * 0.6, c[2] + (1 - c[2]) * 0.6};
    }

    /**
     * 오른 순간: 눈앞 터짐 · 불꽃 · 1인칭 고리, 몸 둘레 빛기둥 (단계만큼 더) · 발밑 거꾸로 도는 고리 · 화면 번쩍임, 소리 (레벨 업 · 자수정 · 모루 — 5단계는 신호기),
     * 화면 가운데 연출 ({@link FxPayload} — 스킬 아이콘 · 별 · 바뀐 값)
     */
    static void doneFx(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, int from, int to) {
        ServerLevel level = sp.level();
        Vec3 c = focus(sp);
        Vec3 feet = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        JobFx.burst(level, c, 1.1 + to * 0.1, j, 0.42, Opt.empty());
        SkillFx.burst(level, c, GOLD_RGB, 0.7 + to * 0.08, 0.3);
        SkillFx.sparks(level, c, core, 10 + to * 2, 4);
        JobFx.eyeRing(sp, j, 2.2, 0.9 + to * 0.06, 0.55);
        JobFx.ring(level, V.up(feet, 0.08), 2.2, j, true, 0.9, Opt.of("alpha", 0.9));
        RuneKit.godRays(sp, level, feet, 2.3, 3 + to, glow, core, Opt.of("h", 8.0 + to, "life", 1.2, "alpha", 0.75));
        JobFx.aura(sp, j, 2.6, 1.0, 0.9);
        Ult.flash(level, feet, glow, Opt.of("radius", 6.0, "time", new double[] {0.04, 0.08, 0.45}), sp);
        SkillFx.play(level, "random.levelup", feet, 0.9 + to * 0.08, 0.9);
        SkillFx.play(level, "chime.amethyst_block", feet, 0.8 + to * 0.12, 1.0);
        SkillFx.play(level, "block.anvil.use", feet, 1.5, 0.25);
        if (to >= Jobs.upMax()) SkillFx.play(level, "beacon.activate", feet, 1.2, 0.9);
        send(sp, new FxPayload(k.id(), k.name(), j.name(), to, Jobs.upMax(), Jobs.rgb(j.color()[0]), Jobs.rgb(j.color()[1]), Jobs.upLines(k, from, to)));
    }

    // ------------------------------------------------------------ 화면 연출 (클라이언트 JobUpgradeHud)

    /** 서버 → 클라이언트: 강화 성공 화면 연출 (스킬 id · 이름 · 직업 · 오른 단계 / 최대 · 직업 두 빛 · 바뀐 값 줄) */
    public record FxPayload(String skill, String name, String job, int level, int max, int colorA, int colorB, List<String> lines)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FxPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("job_up"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC = CustomPacketPayload.codec(FxPayload::write, FxPayload::read);

        private static FxPayload read(RegistryFriendlyByteBuf buf) {
            String skill = buf.readUtf(64);
            String name = buf.readUtf(64);
            String job = buf.readUtf(64);
            int level = buf.readVarInt();
            int max = buf.readVarInt();
            int a = buf.readInt();
            int b = buf.readInt();
            int n = Math.min(8, buf.readVarInt());
            List<String> lines = new ArrayList<>(n);
            for (int i = 0; i < n; i++) lines.add(buf.readUtf(128));
            return new FxPayload(skill, name, job, level, max, a, b, List.copyOf(lines));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(skill, 64);
            buf.writeUtf(name, 64);
            buf.writeUtf(job, 64);
            buf.writeVarInt(level);
            buf.writeVarInt(max);
            buf.writeInt(colorA);
            buf.writeInt(colorB);
            int n = Math.min(8, lines.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeUtf(lines.get(i), 128);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 시험용: 마지막으로 보낸 화면 연출 (사람마다) */
    static final Map<UUID, FxPayload> LAST_FX = new ConcurrentHashMap<>();

    /** 시험용: 이 사람에게 마지막으로 보낸 화면 연출 (없으면 null) */
    public static FxPayload lastFx(ServerPlayer sp) {
        return LAST_FX.get(sp.getUUID());
    }

    static void send(ServerPlayer sp, FxPayload p) {
        LAST_FX.put(sp.getUUID(), p);
        if (ServerPlayNetworking.canSend(sp, FxPayload.TYPE)) ServerPlayNetworking.send(sp, p);
    }

    // ------------------------------------------------------------ 창

    /** 직업 스킬 강화 창 (직업이 없으면 전직 창). 창을 닫으면 done (null 이면 없음) */
    public static void open(ServerPlayer sp, Runnable done) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.ready()) {
            JobTrainer.open(sp, done);
            return;
        }
        list(sp, "", done, 0);
    }

    private static void finish(Runnable done) {
        if (done != null) done.run();
    }

    private static void list(ServerPlayer sp, String message, Runnable done, int guard) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || guard > 200) {
            finish(done);
            return;
        }
        build(sp, j, message).show(sp, sel -> {
            if (sel < 0) {
                finish(done);
                return;
            }
            if (sel == BACK_SLOT) {
                JobTrainer.open(sp, done);
                return;
            }
            Jobs.Skill k = skillAt(j, sel);
            if (k == null) {
                list(sp, message, done, guard + 1);
                return;
            }
            if (level(sp, k.id()) >= Jobs.upMax()) {
                list(sp, "§8" + k.name() + " 은(는) 이미 최대 강화 (+" + Jobs.upMax() + ") 입니다.§r", done, guard + 1);
                return;
            }
            confirm(sp, j, k, msg -> list(sp, msg, done, guard + 1));
        });
    }

    /** 칸 번호의 스킬 (스킬 칸이 아니면 null) */
    static Jobs.Skill skillAt(Jobs.Job j, int slot) {
        for (int i = 0; i < SKILL_SLOTS.length; i++) if (SKILL_SLOTS[i] == slot) return j.skill(i);
        return null;
    }

    /** 스킬 아이콘 (창 그림 job_skill/스킬 id — 클라이언트의 메이플식 스킬 아이콘) */
    static Icon skillIcon(Jobs.Skill k) {
        return Icon.sprite("job_skill/" + k.id());
    }

    /** 보유 골드 칸 */
    static Slot goldSlot(ServerPlayer sp) {
        long g = Wallet.total(sp, Economy.Kind.GOLD);
        return Slot.of(Icon.item(EconomyItems.GOLD_COIN), "§6보유 골드 §f" + Economy.fmt(g)).count(g)
                .lore("§7지갑 " + Economy.fmt(Wallet.get(sp, Economy.Kind.GOLD)) + " · 가방의 금화 " + Wallet.coins(sp, Economy.Kind.GOLD), "§8골드: 보스 · 퀘스트 보상, 장터")
                .deco();
    }

    /** 강화 창 (시험이 칸을 봄) */
    static ChestForm build(ServerPlayer sp, Jobs.Job j, String message) {
        ChestForm form = new ChestForm("직업 스킬 강화 §8· " + j.name());
        int sum = total(sp, j);
        int all = Jobs.upMax() * Jobs.SLOTS;
        form.set(JOB_SLOT, Slot.of(JobTrainer.cardItem(j), JobTrainer.code(j) + "§l" + j.name() + " §r§7강화 §e" + sum + "§7/" + all)
                .lore("§7" + j.role(), "§8스킬마다 +" + Jobs.upMax() + " 까지 · 단계마다 계수 +" + Math.round(Jobs.upPer() * 100) + "%").deco());
        form.set(GOLD_SLOT, goldSlot(sp));
        form.set(BACK_SLOT, Slot.of(Icon.of("textures/items/book_enchanted"), "§7◀ 직업 창").lore("§8전직 · 직업 바꾸기 창으로"));
        double P = JobSystem.power(sp, j);
        for (int i = 0; i < SKILL_SLOTS.length; i++) {
            Jobs.Skill k = j.skill(i);
            if (k == null) continue;
            int lv = level(sp, k.id());
            Slot s = Slot.of(skillIcon(k), JobTrainer.code(j) + "§l" + k.name() + (lv > 0 ? " §r§6+" + lv : "")).lore(skillLore(sp, j, k, P));
            if (lv > 0) s.count(lv);
            form.set(SKILL_SLOTS[i], s);
        }
        form.set(GUIDE_SLOT, Slot.of(Icon.item(Items.NETHER_STAR), "§e직업 스킬 강화 안내").lore(guideLore()).deco());
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§8스킬을 누르면 필요한 레벨 · 골드 · 재료와 확인 창이 열립니다. 강화는 스킬마다 남습니다 (직업을 바꿔도).§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 스킬 칸 설명: 칸 · 역할 · 재사용 대기, 단계 별, 지금 (계수 · 값 · 피해), 다음 단계 (바뀌는 것 · 필요한 레벨 · 골드 · 재료 ✔ ✘), 누르면 */
    static List<String> skillLore(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<String> out = new ArrayList<>();
        int lv = level(sp, k.id());
        out.add("§8⇧" + JobTrainer.KEYS.charAt(Math.max(0, Math.min(4, k.slot()))) + " · " + Jobs.slotRole(k.slot()) + " · " + Js.str(k.cd()) + "초");
        out.add("§6" + Jobs.upStars(lv) + " §e+" + lv + " §8/ " + Jobs.upMax());
        out.add("§7지금: §f" + String.join(" §8· §f", Jobs.upNow(k, lv)));
        if (k.total() > 0) out.add("§7⚔ 과녁 하나 §f" + Economy.fmt(Math.round(Jobs.totalDamage(k, P) * Jobs.upMult(lv))) + " §8(지금 스탯)");
        out.add("");
        Need n = need(sp, k);
        if (n.max()) {
            out.add("§6✦ 최대 강화");
            return out;
        }
        out.add("§e다음 +" + n.step().to() + " §8(" + n.step().level() + "렙)");
        for (String l : Jobs.upLines(k, lv, n.step().to())) out.add("§7" + l);
        if (k.total() > 0) {
            out.add("§7⚔ §f" + Economy.fmt(Math.round(Jobs.totalDamage(k, P) * Jobs.upMult(lv))) + " §7→ §a"
                    + Economy.fmt(Math.round(Jobs.totalDamage(k, P) * Jobs.upMult(n.step().to()))));
        }
        out.add(mark(n.levelOk()) + " §7" + n.step().level() + "렙 §8(지금 " + n.myLevel() + ")");
        out.add(mark(n.goldOk()) + " §6골드 " + n.step().gold() + " §8(보유 " + Economy.fmt(n.gold()) + ")");
        for (Have h : n.items()) out.add(mark(h.ok()) + " " + h.mat().code() + h.mat().name() + " §7×" + h.need().count() + " §8(보유 " + h.have() + ")");
        out.add("");
        out.add(n.ok() ? "§a▶ 누르면 강화 확인" : "§c▶ 누르면 모자란 것 보기");
        return out;
    }

    private static String mark(boolean ok) {
        return ok ? "§a✔" : "§c✘";
    }

    private static List<String> guideLore() {
        List<String> out = new ArrayList<>();
        out.add("§7스킬마다 §f+" + Jobs.upMax() + "§7 까지 · 단계마다 계수 §f+" + Math.round(Jobs.upPer() * 100) + "%");
        out.add("§7(+" + Jobs.upMax() + " = ×" + Js.str(Jobs.upMult(Jobs.upMax())) + " — 룬 " + Jobs.upMax() + "돌과 같은 계수)");
        out.add("§7피해 · 회복 · 보호막 · 태세 값이 함께 오름");
        StringBuilder b = new StringBuilder("§8필요 레벨:");
        for (int to = 1; to <= Jobs.upMax(); to++) b.append(to == 1 ? " " : " · ").append('+').append(to).append(' ').append(Jobs.upLevel(to));
        out.add(b.toString());
        out.add("§7재료: 골드 + 필드 전리품");
        out.add("§7(+4 · +5 는 이미르 · 수르트 보스 재료도)");
        out.add("§8스킬 칸에 놓고 마우스를 올리면 단계마다 값");
        out.add("§8직업을 바꿔도 강화는 스킬마다 남음");
        return out;
    }

    // ------------------------------------------------------------ 확인 창

    private static void confirm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, Consumer<String> back) {
        Need n = need(sp, k);
        buildConfirm(sp, j, k, n).show(sp, sel -> {
            if (sel == OK_SLOT) {
                if (!n.ok()) {
                    back.accept("§4" + why(need(sp, k)) + "§r");
                    return;
                }
                upgrade(sp, j, k, back);
            } else back.accept("");
        });
    }

    /** 확인 창 (시험이 칸을 봄): 윗줄 필요한 것 (레벨 · 골드 · 재료 — 숫자 = 필요한 수), 가운데 강화 · 스킬 · 취소 */
    static ChestForm buildConfirm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, Need n) {
        int to = n.step() == null ? n.from() : n.step().to();
        ChestForm form = new ChestForm("스킬 강화 확인 §8· " + k.name() + " +" + to);
        int at = NEED_FIRST;
        if (n.step() != null) {
            form.set(at++, Slot.of(Icon.item(Items.EXPERIENCE_BOTTLE), mark(n.levelOk()) + " §f필요 레벨 " + n.step().level())
                    .lore("§7지금 " + n.myLevel() + "렙").count(n.step().level()).deco());
            form.set(at++, Slot.of(Icon.item(EconomyItems.GOLD_COIN), mark(n.goldOk()) + " §6골드 " + n.step().gold())
                    .lore("§7보유 " + Economy.fmt(n.gold()) + " §8(지갑 먼저, 모자라면 가방의 금화)").count(n.step().gold()).deco());
            for (Have h : n.items()) {
                Icon icon = h.mat().item() == null ? Icon.sprite("item_unknown") : Icon.item(h.mat().item());
                form.set(at++, Slot.of(icon, mark(h.ok()) + " " + h.mat().code() + h.mat().name() + " §7×" + h.need().count())
                        .lore("§7보유 §f" + h.have() + " §8/ 필요 " + h.need().count(), "§8" + h.mat().from()).count(h.need().count()).deco());
            }
        }
        Slot ok = Slot.of(Icon.sprite("icon_check"), n.ok() ? "§a강화하기 §7(+" + n.from() + " → +" + to + ")" : "§8강화할 수 없음");
        if (!n.ok()) ok.lore("§c" + why(n)).dim();
        form.set(OK_SLOT, ok);
        List<String> lore = new ArrayList<>();
        lore.add("§6" + Jobs.upStars(n.from()) + " §7→ §6" + Jobs.upStars(to));
        for (String l : Jobs.upLines(k, n.from(), to)) lore.add("§7" + l);
        form.set(CARD_SLOT, Slot.of(skillIcon(k), JobTrainer.code(j) + "§l" + k.name() + " §r§6+" + n.from() + " → +" + to).lore(lore).deco());
        form.set(NO_SLOT, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§0§l" + k.name() + "§r§8 +" + n.from() + " → §5+" + to + "§8 — " + String.join(" · ", Jobs.upLines(k, n.from(), to)) + "§r");
        if (n.step() != null) {
            StringBuilder b = new StringBuilder("§8필요: §0" + n.step().level() + "렙§8 · 골드 §0" + n.step().gold());
            for (Have h : n.items()) b.append("§8 · ").append(h.mat().name()).append(" §0").append(h.need().count());
            lines.add(b.append("§r").toString());
        }
        lines.add(n.ok() ? "§8왼쪽 초록 칸: 강화 (실패 없음) · 오른쪽 빨간 칸: 취소§r" : "§4" + why(n) + "§r");
        form.body(String.join("\n", lines));
        return form;
    }

    // ------------------------------------------------------------ 명령어 (/job up)

    /**
     * /job up — 내 직업 스킬의 강화 단계, 관리자: /job up &lt;스킬 id | all&gt; &lt;0 ~ 5&gt; [대상] (값 없이 — 시험 · 관리), /job up menu (강화 창 — 직업 교관
     * 없이)
     */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("up")
                .executes(c -> info(c.getSource().getPlayerOrException()))
                .then(Commands.literal("menu").requires(JobSystem::admin).executes(c -> {
                    open(c.getSource().getPlayerOrException(), null);
                    return 1;
                }))
                .then(Commands.argument("skill", StringArgumentType.word()).requires(JobSystem::admin)
                        .suggests((c, b) -> {
                            b.suggest("all");
                            for (Jobs.Job j : Jobs.all()) for (Jobs.Skill k : j.skills()) if (!k.id().isEmpty()) b.suggest(k.id());
                            return b.buildFuture();
                        })
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, Jobs.upMax()))
                                .executes(c -> setCmd(c, List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> setCmd(c, EntityArgument.getPlayers(c, "targets"))))));
    }

    private static int setCmd(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        String skill = StringArgumentType.getString(c, "skill");
        int lv = IntegerArgumentType.getInteger(c, "level");
        int n = 0;
        for (ServerPlayer sp : targets) {
            if (skill.equals("all")) {
                Jobs.Job j = JobSystem.job(sp);
                if (j == null) continue;
                for (Jobs.Skill k : j.skills()) set(sp, k.id(), lv);
                n++;
            } else {
                if (find(skill) == null) {
                    c.getSource().sendFailure(Component.literal("없는 스킬 id: " + skill));
                    return 0;
                }
                set(sp, skill, lv);
                n++;
            }
            info(sp);
        }
        int done = n;
        c.getSource().sendSuccess(() -> Component.literal(done + "명 → " + skill + " +" + lv), true);
        return n;
    }

    /** 스킬 id 로 (모든 직업 — 없으면 null) */
    static Jobs.Skill find(String id) {
        for (Jobs.Job j : Jobs.all()) {
            Jobs.Skill k = j.skill(id);
            if (k != null) return k;
        }
        return null;
    }

    private static int info(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) {
            Notify.say(sp, "§7직업이 없습니다 — 직업 스킬 강화는 직업을 고른 뒤 마을의 §b직업 교관§7에게서.");
            return 1;
        }
        Notify.say(sp, "§6직업 스킬 강화 §7— " + JobTrainer.code(j) + j.name() + " §e" + total(sp, j) + "§7/" + Jobs.upMax() * Jobs.SLOTS);
        for (Jobs.Skill k : j.skills()) {
            int lv = level(sp, k.id());
            Notify.say(sp, String.format(Locale.ROOT, " §e⇧%c §f%s §6%s §e+%d §7%s", JobTrainer.KEYS.charAt(Math.max(0, Math.min(4, k.slot()))), k.name(),
                    Jobs.upStars(lv), lv, String.join(" · ", Jobs.upNow(k, lv))));
        }
        return 1;
    }

    /** 나간 사람의 기록 캐시를 지움 */
    static void forget(UUID id) {
        CACHE.remove(id);
        LAST_FX.remove(id);
    }
}
