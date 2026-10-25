package mmorpg.fabric.smith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Armor;
import mmorpg.core.Artifacts;
import mmorpg.core.Economy;
import mmorpg.core.Enhance;
import mmorpg.core.Grade;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Relics;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.ArtifactItem;
import mmorpg.fabric.gear.RelicItem;
import mmorpg.fabric.job.JobUpgrades;
import mmorpg.fabric.relic.RelicSystem;
import mmorpg.fabric.shop.ArmorShop;
import mmorpg.fabric.shop.Shop;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.44.0 대장장이 전용 창 (서버 — 사용자: "대장장이 ui 수정 좀 하자 좀 난잡함, 강화나 장비를 만들때 망치 두드리는 소리가 나고 대장장이가 망치를 두드리는
 * 애니메이션" → 고른 것: 새 전용 창 · 창 옆 패널 + 세상 NPC).
 * <ul>
 *   <li>탭 넷: 강화 (가방의 RPG 무기) · 레벨 올리기 (영웅 30 → 40 · 초월 40 → 60) · 제작 (쪽 — 영웅 · 초월 · 고귀 무기 · 공허의 갑옷 · 장신구) ·
 *       장신구 · 아티팩트 강화 (판 · 낀 장신구 · 가방). 비용 · 판정은 상자 창과 같은 함수 (EnhanceWindow · GearEnhance · Smith · VoidArmorCraft ·
 *       ArtifactCraft — quiet: 눈앞 연출 · 알림 없이)</li>
 *   <li>단추를 누르면 창을 닫지 않고 망치질 ({@link #TICKS} — 창의 대장장이 패널 · 세상의 대장장이 NPC 가 같은 동작, 망치가 닿을 때 불꽃 · 소리) 이 끝난
 *       뒤 결과를 정함 (재료는 그때 받음 — 도중에 나가거나 죽으면 아무것도 쓰지 않음). 결과는 창에 (닫았으면 화면 제목)</li>
 *   <li>세상: NPC 바로 옆 ({@link #ANVIL_RANGE} 칸 — 곧은 · 대각선) 에 모루가 있으면 그쪽을 보고 그 윗면을 침, 없으면 옆으로 돌아서서 그 앞에 잠깐 모루
 *       (표시 엔티티 — 1칸) 를 놓음</li>
 *   <li>모드가 없는 클라이언트 · 시험의 가짜 플레이어는 예전 상자 창 ({@link Smith#smithLoop}) — {@link #FORCE} · {@link #legacy} 로 시험이 고름</li>
 * </ul>
 * 0.45.0: 창의 흐름 (열기 · 탭 · 일 · 판정 · 보내기) 은 룬술사 · 상인 창과 함께 쓰는 {@link Workshop} 으로 옮김 — 여기는 대장장이 가게 ({@link #KIND}:
 * 탭 · 목록 · 할 일 · 세상의 모루 연출) 와 예전 이름 (시험이 씀).
 */
public final class SmithUi {
    private SmithUi() {}

    private static final Rules R = Rules.get();
    private static final Obj CONF = R.root().objOrNull("SMITH_UI");
    /** 망치질 동작 이름 (poses.json) */
    public static final String MOTION = CONF != null ? CONF.strOr("motion", "smith_hammer") : "smith_hammer";
    /** 망치질 틱 (이 뒤에 판정) */
    public static final int TICKS = CONF != null ? (int) CONF.numOr("ticks", 30) : 30;
    /** 망치가 모루에 닿는 틱 */
    public static final List<Integer> IMPACTS = impacts();
    /** 대장장이가 쓰는 모루까지의 거리 (NPC 와 모루 가운데끼리 — 바로 옆 1 · 대각선 1.41: 망치가 닿는 앞 REACH 칸이 모루 윗면에 들어가는 거리) */
    public static final double ANVIL_RANGE = CONF != null ? CONF.numOr("anvilRange", 1.75) : 1.75;
    /**
     * 망치가 닿는 자리: 몸 앞 REACH 칸 · 높이 1칸 (poses.json smith_hammer 의 내려친 열쇠 — 망치 치는 면이 앞 1.15 · 높이 0.94 ~ 0.97 칸, 세상의
     * 모루 (1칸) 윗면). 둘레 모루가 있으면 그 점을 모루 윗면 안으로 잡아 불꽃을 냄
     */
    static final double REACH = 1.15;
    /** 모루가 없을 때 옆에 놓는 모루 (표시 엔티티) 의 크기 — 진짜 모루와 같은 1칸 (망치가 윗면에 닿게) */
    static final double TEMP_SCALE = 1.0;

    public static final String ENHANCE = "enhance";
    public static final String RAISE = "raise";
    public static final String CRAFT = "craft";
    public static final String GEAR = "gear";

    /** 시험: 이 사람들은 클라이언트 없이도 새 창 흐름 (보내는 것은 {@link #SENT} 에 쌓임 — {@link Workshop#FORCE} 와 같은 것) */
    public static final Set<UUID> FORCE = Workshop.FORCE;
    public static final Map<UUID, List<CustomPacketPayload>> SENT = Workshop.SENT;
    /** 사진첩: 예전 상자 창으로 (모드 클라이언트도) */
    public static boolean legacy = false;
    /** 대장장이 가게 (Workshop) */
    public static final Workshop.Kind KIND = new Forge();

    private static final double[] GOLD = {1.0, 0.82, 0.3};
    private static final double[] WHITE = {1, 1, 1};
    private static final double[] SMOKE = {0.45, 0.45, 0.5};
    private static final double[] RED = {0.9, 0.2, 0.15};
    /** 불꽃 색 (등급 순번) */
    private static final double[][] SPARK = {{1.0, 0.75, 0.35}, {0.55, 0.8, 1.0}, {0.8, 0.55, 1.0}, {1.0, 0.6, 0.2}, {1.0, 0.35, 0.3}};

    private static List<Integer> impacts() {
        List<Integer> out = new ArrayList<>();
        List<Double> ds = CONF != null ? CONF.numbersOrNull("impacts") : null;
        if (ds != null) for (double d : ds) out.add((int) d);
        if (out.isEmpty()) out.addAll(List.of(8, 17, 26));
        return List.copyOf(out);
    }

    /** 대장장이의 할 일: 모루에 올릴 것 · 불꽃 색 (등급) · 망치질 (TICKS · IMPACTS) */
    private static Workshop.Prepared forge(Workshop.Job job, ItemStack shown, int grade) {
        return new Workshop.Prepared(job, shown, grade, null, "forge", MOTION, TICKS, IMPACTS);
    }

    /** 세상에서 망치가 닿는 자리 · 모루 쪽 (yaw — 마인크래프트 각도) · 잠깐 놓는 모루인지 */
    record Spot(Vec3 hit, float yaw, boolean temp) {}

    /** 몸 앞에 잠깐 놓은 모루 (NPC 엔티티 번호마다 — gen 이 바뀌면 치우기를 미룸) */
    private static final class TempAnvil {
        Display.BlockDisplay d;
        int gen;
    }

    private static final Map<Integer, TempAnvil> ANVILS = new HashMap<>();

    /** Smith.init */
    static void init() {
        Workshop.init();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> ANVILS.clear());
    }

    /** 새 창을 쓸지 (모드 클라이언트 — 시험은 FORCE, 사진첩은 legacy 로 예전 창) */
    public static boolean usable(ServerPlayer sp) {
        return Workshop.usable(sp, KIND);
    }

    /** 창이 열려 있는지 (시험) */
    public static boolean isOpen(ServerPlayer sp) {
        return Workshop.isOpen(sp);
    }

    /** 망치질 중인지 (시험) */
    public static boolean busy(ServerPlayer sp) {
        return Workshop.busy(sp);
    }

    /** 시험: 그 NPC 가 마지막으로 망치질을 시작한 틱 (없으면 -1) */
    public static long npcWorkTick(Entity npc) {
        return Workshop.npcWorkTick(npc);
    }

    /** 시험: 그 NPC 앞에 잠깐 놓은 모루 (없으면 null) */
    public static Display.BlockDisplay tempAnvil(Entity npc) {
        TempAnvil a = npc == null ? null : ANVILS.get(npc.getId());
        return a == null || a.d == null || a.d.isRemoved() ? null : a.d;
    }

    // ------------------------------------------------------------ 열기 · 창의 일 (Workshop)

    /**
     * 대장장이 창을 엶. npc = 대장장이 NPC (없으면 null — 명령어), start: enhance · smith (= 레벨 올리기) · craft · artifact (제작의 장신구 쪽) · gear,
     * done = 창을 닫으면 (null 이면 없음)
     */
    public static void open(ServerPlayer sp, Entity npc, String start, Runnable done) {
        Workshop.open(sp, KIND, npc, start, -1, done);
    }

    /** 창의 일 (클라이언트 → 서버): tab · page · do · sync · close */
    public static void action(ServerPlayer sp, String action, String key, int a, int b) {
        Workshop.action(sp, action, key, a, b);
    }

    /** 보이는 탭 (장신구 · 아티팩트 강화는 규칙에 있을 때) */
    static List<String> tabs() {
        List<String> out = new ArrayList<>(List.of(ENHANCE, RAISE, CRAFT));
        if (Enhance.gearEnabled()) out.add(GEAR);
        return out;
    }

    /** 대장장이 가게 (Workshop.Kind) */
    private static final class Forge implements Workshop.Kind {
        @Override
        public String id() {
            return "smith";
        }

        @Override
        public String role() {
            return "대장장이";
        }

        @Override
        public boolean legacy() {
            return legacy;
        }

        @Override
        public List<String> tabs(ServerPlayer sp, Workshop.Session s) {
            return SmithUi.tabs();
        }

        @Override
        public String tabName(String tab) {
            return switch (tab) {
                case ENHANCE -> "강화";
                case RAISE -> "레벨 올리기";
                case CRAFT -> "제작";
                case GEAR -> "장신구 · 아티팩트";
                default -> tab;
            };
        }

        @Override
        public void begin(ServerPlayer sp, Workshop.Session s, String start) {
            switch (start == null ? ENHANCE : start) {
                case "smith", RAISE -> s.tab = RAISE;
                case CRAFT -> s.tab = CRAFT;
                case "artifact" -> {
                    s.tab = CRAFT;
                    s.page = Math.max(0, Smith.artifactPageIndex());
                }
                case GEAR -> s.tab = Enhance.gearEnabled() ? GEAR : ENHANCE;
                default -> s.tab = ENHANCE;
            }
            onTab(sp, s);
        }

        @Override
        public void onTab(ServerPlayer sp, Workshop.Session s) {
            if (s.tab.equals(CRAFT) && s.page < 0) s.page = Smith.firstCraftPage(sp, Smith.craftRecipes());
        }

        @Override
        public List<String> pages(ServerPlayer sp, Workshop.Session s) {
            List<String> pages = new ArrayList<>();
            if (s.tab.equals(CRAFT)) for (Smith.CraftPage p : Smith.CRAFT_PAGES) pages.add(p.label());
            return pages;
        }

        @Override
        public String listTitle(ServerPlayer sp, Workshop.Session s) {
            return switch (s.tab) {
                case RAISE -> "올릴 무기";
                case GEAR -> "장신구 · 아티팩트";
                default -> "가방의 무기";
            };
        }

        @Override
        public List<SmithNet.Entry> entries(ServerPlayer sp, Workshop.Session s) {
            return SmithUi.entries(sp, s);
        }

        @Override
        public List<SmithNet.Res> res(ServerPlayer sp, Workshop.Session s) {
            return SmithUi.res(sp, s.tab);
        }

        @Override
        public String emptyText(ServerPlayer sp, Workshop.Session s) {
            return SmithUi.emptyText(s);
        }

        @Override
        public Workshop.Prepared prepare(ServerPlayer sp, Workshop.Session s, String key, int variant, boolean flag) {
            return SmithUi.prepare(sp, key, variant, flag);
        }

        /** 세상: 둘레 모루 (없으면 옆으로 돌아서서 잠깐 모루) 를 보고 망치질 — 닿을 때 불꽃 · 소리, 끝에 결과 연출 · 잠깐 모루를 치움 */
        @Override
        public Workshop.Show show(ServerPlayer sp, Workshop.Session s, Entity npc, Workshop.Prepared p) {
            Spot spot = spot(npc);
            if (spot.temp()) placeTempAnvil(npc, spot.yaw());
            int grade = p.grade();
            return new Workshop.Show() {
                @Override
                public float yaw() {
                    return spot.yaw();
                }

                @Override
                public void impact(int i, boolean last) {
                    strikeFx(sp, npc, spot.hit(), grade);
                }

                @Override
                public void end(SmithNet.Result r) {
                    releaseTempAnvil(npc);
                    if (r != null) resultFx(sp, npc, spot.hit(), r.kind());
                }
            };
        }
    }

    // ------------------------------------------------------------ 망치질

    /** 열쇠 (창의 칸) 의 할 일을 준비 (그 순간의 무기 · 비용을 잡아 둠). 열쇠: w:칸 · g:자리 · r:칸 · c:제작 번호 · v:갑옷 종류 · a:종류:등급 */
    static Workshop.Prepared prepare(ServerPlayer sp, String key, int variant, boolean scroll) {
        String[] k = key == null ? new String[0] : key.split(":");
        try {
            switch (k.length > 0 ? k[0] : "") {
                case "w" -> {
                    EnhanceWindow.Entry e = EnhanceWindow.entryAt(sp, Integer.parseInt(k[1]));
                    if (e == null) return Workshop.Prepared.fail("§c무기가 옮겨졌습니다 — 다시 골라 주세요");
                    Enhance.Cost c = Enhance.enhanceCost(e.n(), e.w().grade().id());
                    if (c == null) return Workshop.Prepared.fail("§6이미 최대 강화 +" + EnhanceWindow.MAX + "입니다");
                    boolean guard = scroll && c.down();
                    if (guard && scrolls(sp) < 1) return Workshop.Prepared.fail("§c" + EnhanceWindow.SCROLL.str("name") + "가 없습니다");
                    String name = e.w().grade().color() + e.w().title();
                    Workshop.Job job = seq -> {
                        EnhanceWindow.Outcome o = EnhanceWindow.judgeQuiet(sp, e, c, guard);
                        if (o.ok() && o.after() >= EnhanceWindow.ANNOUNCE_FROM) EnhanceWindow.announce(sp, e.w(), o.after());
                        // 0.44.1 강화가 반영된 공격력 (오르면 초록 · 떨어지면 빨강 · 그대로면 한 값)
                        String eff = EnhanceWindow.attackText(e.w(), o.before(), o.after(), "§7", "§f", o.kind().equals("down") ? "§c" : "§a");
                        return enhanceResult(seq, key, o, name, eff, Inv.get(sp, e.slot()).copy());
                    };
                    return forge(job, e.item(), e.w().gradeIndex());
                }
                case "g" -> {
                    int loc = Integer.parseInt(k[1]);
                    GearEnhance.Entry e = GearEnhance.entryAt(sp, loc);
                    if (e == null) return Workshop.Prepared.fail("§c옮겨졌습니다 — 다시 골라 주세요");
                    Enhance.Cost c = Enhance.gearCost(e.n(), e.grade());
                    if (c == null) return Workshop.Prepared.fail("§6이미 최대 강화 +" + EnhanceWindow.MAX + "입니다");
                    boolean guard = scroll && c.down();
                    if (guard && scrolls(sp) < 1) return Workshop.Prepared.fail("§c" + EnhanceWindow.SCROLL.str("name") + "가 없습니다");
                    Workshop.Job job = seq -> {
                        EnhanceWindow.Outcome o = GearEnhance.judgeQuiet(sp, e, c, guard);
                        if (o.ok() && o.after() >= EnhanceWindow.ANNOUNCE_FROM) EnhanceWindow.announce(sp, e.gradeColor(), e.gradeName(), e.title(), o.after());
                        return enhanceResult(seq, key, o, e.gradeColor() + e.title(), e.effect(o.after()), GearEnhance.at(sp, loc).copy());
                    };
                    return forge(job, e.item(), e.gradeIndex());
                }
                case "r" -> {
                    int slot = Integer.parseInt(k[1]);
                    Smith.Raise e = null;
                    for (Smith.Raise x : Smith.raisable(sp)) if (x.slot() == slot) e = x;
                    if (e == null) return Workshop.Prepared.fail("§c무기가 옮겨졌습니다 — 다시 골라 주세요");
                    Smith.Raise raise = e;
                    Workshop.Job job = seq -> {
                        String text = Smith.raise(sp, raise, true);
                        boolean ok = text.startsWith("§2");
                        return new SmithNet.Result(seq, ok ? "done" : "error", key, ok ? "§6§l레벨 제한 " + raise.w().level() + " → " + raise.to().level() : "§c올리지 못했습니다",
                                onDark(text), Inv.get(sp, slot).copy());
                    };
                    return forge(job, e.item(), e.w().gradeIndex());
                }
                case "c" -> {
                    int i = Integer.parseInt(k[1]);
                    List<Smith.Recipe> all = Smith.craftRecipes();
                    if (i < 0 || i >= all.size()) return Workshop.Prepared.fail("§c만들 수 없습니다");
                    Smith.Recipe e = all.get(i);
                    Workshop.Job job = seq -> {
                        String text = Smith.craft(sp, e, true);
                        boolean ok = text.startsWith("§2");
                        return new SmithNet.Result(seq, ok ? "done" : "error", key, ok ? "§6§l제작 완료" : "§c만들지 못했습니다", onDark(text), Shop.weaponItem(e.w()));
                    };
                    return forge(job, Shop.weaponItem(e.w()), e.w().gradeIndex());
                }
                case "v" -> {
                    List<Armor.Piece> set = VoidArmorCraft.enabled() ? VoidArmorCraft.set(k[1]) : List.of();
                    if (set.isEmpty() || set.stream().anyMatch(java.util.Objects::isNull)) return Workshop.Prepared.fail("§c만들 수 없습니다");
                    List<Armor.Piece> pieces = variant >= 0 && variant < set.size() ? List.of(set.get(variant)) : set;
                    Armor.Piece shown = pieces.get(Math.min(1, pieces.size() - 1));
                    Workshop.Job job = seq -> {
                        String text = VoidArmorCraft.craft(sp, pieces, true);
                        boolean ok = text.startsWith("§2");
                        return new SmithNet.Result(seq, ok ? "done" : "error", key, ok ? "§5§l공허의 갑옷 제작" : "§c만들지 못했습니다", onDark(text), ArmorShop.armorItem(shown));
                    };
                    return forge(job, ArmorShop.armorItem(shown), shown.gradeIndex());
                }
                case "a" -> {
                    Artifacts.Art a = ArtifactCraft.enabled() ? Artifacts.art(k[1], k[2]) : null;
                    if (a == null) return Workshop.Prepared.fail("§c만들 수 없습니다");
                    Workshop.Job job = seq -> {
                        String text = ArtifactCraft.craft(sp, a, true);
                        boolean ok = text.startsWith("§2");
                        return new SmithNet.Result(seq, ok ? "done" : "error", key, ok ? "§d§l장신구 제작" : "§c만들지 못했습니다", onDark(text), ArtifactCraft.item(a));
                    };
                    return forge(job, ArtifactCraft.item(a), a.gradeIndex());
                }
                default -> {
                    return Workshop.Prepared.fail("§c알 수 없는 칸");
                }
            }
        } catch (RuntimeException err) {
            return Workshop.Prepared.fail("§c알 수 없는 칸");
        }
    }

    private static SmithNet.Result enhanceResult(int seq, String key, EnhanceWindow.Outcome o, String name, String effect, ItemStack item) {
        String title = switch (o.kind()) {
            case "success" -> "§6§l+" + o.after() + " 강화 성공!";
            case "down" -> "§c§l강화 실패 §r§c+" + o.before() + " → +" + o.after();
            case "guard" -> "§e§l강화 실패 §r§e" + EnhanceWindow.SCROLL.str("name") + "가 지킴";
            case "keep" -> "§7§l강화 실패 §r§7+" + o.after() + " 그대로";
            default -> "§c취소";
        };
        String text = o.kind().equals("error") ? onDark(o.text()) : name + " §7" + effect;
        return new SmithNet.Result(seq, o.kind(), key, title, text, item);
    }

    // ------------------------------------------------------------ 세상 연출 (대장장이 NPC)

    /** NPC 바로 옆 (가운데끼리 ANVIL_RANGE 칸 안 · 발 높이 -1 ~ +1) 의 가장 가까운 모루 (없으면 null) */
    static BlockPos anvilNear(Entity npc) {
        return Workshop.blockNear(npc, ANVIL_RANGE, st -> st.getBlock() instanceof AnvilBlock);
    }

    /**
     * 세상에서 칠 자리: 둘레 모루가 있으면 그쪽 yaw · 망치가 닿는 점 (몸 앞 REACH 칸) 을 모루 윗면 안으로. 없으면 옆으로 돌아서서 (보던 사람에게
     * 옆모습 — 사람과 대장장이 사이를 막지 않게: 오른쪽, 막혔으면 왼쪽) 그 앞 REACH 칸에 잠깐 모루
     */
    static Spot spot(Entity npc) {
        BlockPos a = anvilNear(npc);
        if (a != null) {
            float yaw = Kit.lookRotation(npc.position(), new Vec3(a.getX() + 0.5, npc.getY(), a.getZ() + 0.5))[0];
            Vec3 f = Vec3.directionFromRotation(0, yaw);
            double x = Mth.clamp(npc.getX() + f.x * REACH, a.getX() + 0.12, a.getX() + 0.88);
            double z = Mth.clamp(npc.getZ() + f.z * REACH, a.getZ() + 0.12, a.getZ() + 0.88);
            return new Spot(new Vec3(x, a.getY() + 1.02, z), yaw, false);
        }
        float yaw = sideYaw(npc);
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 c = npc.position().add(f.x * REACH, 0, f.z * REACH);
        return new Spot(new Vec3(c.x, c.y + TEMP_SCALE + 0.02, c.z), yaw, true);
    }

    /** 모루가 없을 때 돌아설 쪽: 몸의 오른쪽 (yaw + 90), 그 자리가 막혔으면 왼쪽 (둘 다 막혔으면 오른쪽) */
    static float sideYaw(Entity npc) {
        return Workshop.sideYaw(npc, REACH);
    }

    /** 몸 앞에 잠깐 모루 (있으면 그대로 — 치우기를 미룸): 0 → TEMP_SCALE 로 커지며 나타남 · 연기 */
    private static void placeTempAnvil(Entity npc, float yaw) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        TempAnvil t = ANVILS.computeIfAbsent(npc.getId(), id -> new TempAnvil());
        t.gen++;
        if (t.d != null && !t.d.isRemoved()) {
            FxDisplays.move(t.d, anvilTransform(TEMP_SCALE), 2);
            return;
        }
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 c = npc.position().add(f.x * REACH, 0, f.z * REACH);
        // 모루의 긴 쪽이 대장장이 앞을 가로지르게 (놓을 때처럼 — 바라보는 쪽의 시계 방향)
        Direction facing = Direction.fromYRot(yaw).getClockWise();
        t.d = FxDisplays.block(level, c, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, facing), anvilTransform(0.01), 6000, null);
        if (t.d == null) return;
        Display.BlockDisplay d = t.d;
        Tasks.later(2, () -> FxDisplays.move(d, anvilTransform(TEMP_SCALE), 4));
        level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.3, c.z, 6, 0.25, 0.1, 0.25, 0.01);
        SkillFx.play(level, "random.anvil_land", c, 1.4, 0.35);
    }

    /** 망치질이 끝남: 16틱 뒤 (그 사이 새 망치질이 없으면) 작아지며 사라짐 */
    private static void releaseTempAnvil(Entity npc) {
        TempAnvil t = ANVILS.get(npc.getId());
        if (t == null || t.d == null) return;
        int gen = t.gen;
        Display.BlockDisplay d = t.d;
        Tasks.later(16, () -> {
            if (t.gen != gen || d.isRemoved()) return;
            FxDisplays.move(d, anvilTransform(0.01), 6);
            Tasks.later(7, () -> {
                if (t.gen != gen) return;
                if (d.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.POOF, d.getX(), d.getY() + 0.2, d.getZ(), 5, 0.2, 0.08, 0.2, 0.01);
                FxDisplays.remove(d);
                if (ANVILS.get(npc.getId()) == t) ANVILS.remove(npc.getId());
            });
        });
    }

    /** 표시 엔티티 자리 = 모루 바닥 가운데 → 블록 모서리를 (-s/2, 0, -s/2) 로 옮기고 s 배 */
    private static com.mojang.math.Transformation anvilTransform(double s) {
        return FxDisplays.of(new Vec3(-s / 2, 0, -s / 2), new Quaternionf(), s);
    }

    /** 망치가 닿음: 불꽃 · 모루 소리 (일하는 사람은 창에서 듣고 — 둘레 사람만) */
    private static void strikeFx(ServerPlayer sp, Entity npc, Vec3 hit, int grade) {
        if (npc.isRemoved() || !(npc.level() instanceof ServerLevel level) || hit == null) return;
        double[] col = SPARK[Math.max(0, Math.min(SPARK.length - 1, grade))];
        SkillFx.sparks(level, hit, col, 8, 4);
        SkillFx.sparks(level, hit, GOLD, 4, 2.5);
        level.sendParticles(ParticleTypes.SMALL_FLAME, hit.x, hit.y + 0.05, hit.z, 2, 0.08, 0.02, 0.08, 0.02);
        level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y + 0.05, hit.z, 5, 0.1, 0.05, 0.1, 0.25);
        playExcept(level, sp, "random.anvil_use", hit, 0.95 + Kit.rand() * 0.2, 0.55);
    }

    /** 결과 연출 (세상 — 모루 위): 성공 · 완료 금빛 · 실패 연기 · 하락 붉은 폭발 */
    private static void resultFx(ServerPlayer sp, Entity npc, Vec3 hit, String kind) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Vec3 at = V.up(hit, 0.15);
        switch (kind) {
            case "success", "done" -> {
                SkillFx.burst(level, at, GOLD, 0.9, 0.35);
                SkillFx.burst(level, at, WHITE, 0.5, 0.25);
                SkillFx.sparks(level, at, GOLD, 14, 5);
                SkillFx.vanilla(level, "minecraft:totem_particle", V.up(at, 0.3));
                playExcept(level, sp, "random.levelup", at, 1.3, 0.5);
            }
            case "down" -> {
                SkillFx.burst(level, at, RED, 0.8, 0.35);
                SkillFx.sparks(level, at, RED, 8, 3);
                playExcept(level, sp, "random.anvil_break", at, 0.8, 0.6);
            }
            case "keep", "guard" -> {
                SkillFx.burst(level, at, SMOKE, 0.7, 0.35);
                for (int i = 0; i < 3; i++) {
                    SkillFx.vanilla(level, "minecraft:basic_smoke_particle", V.add(at, new Vec3((Kit.rand() - 0.5) * 0.5, Kit.rand() * 0.3, (Kit.rand() - 0.5) * 0.5)));
                }
                playExcept(level, sp, "random.fizz", at, 0.9, 0.5);
            }
            default -> {}
        }
    }

    /** 그 사람만 빼고 들리는 소리 (일하는 사람은 창에서 듣는 소리와 겹치지 않게) */
    static void playExcept(ServerLevel level, ServerPlayer except, String bedrock, Vec3 at, double pitch, double volume) {
        Workshop.playExcept(level, except, bedrock, at, pitch, volume);
    }

    // ------------------------------------------------------------ 보기 (탭마다 목록 · 상세)

    /** 시험: 지금 탭의 목록 (서버가 보낼 것과 같음) */
    public static List<SmithNet.Entry> testEntries(ServerPlayer sp) {
        return Workshop.testEntries(sp);
    }

    /** 시험: 지금 탭 */
    public static String testTab(ServerPlayer sp) {
        return Workshop.testTab(sp);
    }

    /** 시험: 마지막 결과 */
    public static SmithNet.Result testResult(ServerPlayer sp) {
        return Workshop.testResult(sp);
    }

    /** 시험: 알림 줄 */
    public static String testMessage(ServerPlayer sp) {
        return Workshop.testMessage(sp);
    }

    private static String emptyText(Workshop.Session s) {
        return switch (s.tab) {
            case ENHANCE -> "§7강화할 RPG 무기가 없습니다\n§8무기를 가방에 넣고 오세요";
            case RAISE -> "§7레벨을 올릴 무기가 없습니다\n§8영웅 30 · 35렙 · 초월 40 ~ 55렙 무기";
            case GEAR -> "§7강화할 장신구 · 아티팩트가 없습니다";
            default -> "§7이 쪽에 만들 것이 없습니다";
        };
    }

    static List<SmithNet.Entry> entries(ServerPlayer sp, Workshop.Session s) {
        return switch (s.tab) {
            case RAISE -> raiseEntries(sp);
            case CRAFT -> craftEntries(sp, Math.max(0, s.page));
            case GEAR -> gearEntries(sp);
            default -> enhanceEntries(sp);
        };
    }

    /** 재료 줄 (그 탭에서 쓰는 것) */
    static List<SmithNet.Res> res(ServerPlayer sp, String tab) {
        List<SmithNet.Res> out = new ArrayList<>();
        if (tab.equals(ENHANCE) || tab.equals(GEAR)) {
            for (Obj m : List.of(EnhanceWindow.STONE, EnhanceWindow.BRIGHT, EnhanceWindow.SCROLL)) {
                Item it = EnhanceWindow.itemOf(m);
                out.add(new SmithNet.Res(stack(it), m.str("name"), it == null ? 0 : Inv.count(sp, it)));
            }
        } else {
            out.add(new SmithNet.Res(stack(Inv.CURRENCY), Inv.CURRENCY_NAME, Inv.money(sp)));
            out.add(new SmithNet.Res(stack(EconomyItems.GOLD_COIN), "골드", Wallet.total(sp, Economy.Kind.GOLD)));
            Item ore = Smith.ore();
            out.add(new SmithNet.Res(stack(ore), oreName(), ore == null ? 0 : Inv.count(sp, ore)));
        }
        return out;
    }

    private static String oreName() {
        return R.obj("SMITH").str("oreName");
    }

    private static ItemStack stack(Item it) {
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static SmithNet.Cost cost(Item it, String name, long have, long need) {
        return new SmithNet.Cost(stack(it), name, have, need);
    }

    private static SmithNet.Cost matCost(ServerPlayer sp, String itemId, int need) {
        JobUpgrades.Mat m = JobUpgrades.mat(itemId);
        return cost(m.item(), m.name(), m.item() == null ? 0 : Inv.count(sp, m.item()), need);
    }

    private static SmithNet.Cost goldCost(ServerPlayer sp, long need) {
        return cost(EconomyItems.GOLD_COIN, "골드", Wallet.total(sp, Economy.Kind.GOLD), need);
    }

    private static SmithNet.Cost silverCost(ServerPlayer sp, long need) {
        return cost(Inv.CURRENCY, Inv.CURRENCY_NAME, Inv.money(sp), need);
    }

    private static List<SmithNet.Cost> enhanceCosts(ServerPlayer sp, Enhance.Cost c) {
        List<SmithNet.Cost> out = new ArrayList<>();
        Item st = EnhanceWindow.itemOf(EnhanceWindow.STONE);
        out.add(cost(st, EnhanceWindow.STONE.str("name"), st == null ? 0 : Inv.count(sp, st), (long) c.stone()));
        if (c.bright() > 0) {
            Item br = EnhanceWindow.itemOf(EnhanceWindow.BRIGHT);
            out.add(cost(br, EnhanceWindow.BRIGHT.str("name"), br == null ? 0 : Inv.count(sp, br), (long) c.bright()));
        }
        return out;
    }

    static int scrolls(ServerPlayer sp) {
        Item it = EnhanceWindow.itemOf(EnhanceWindow.SCROLL);
        return it == null ? 0 : Inv.count(sp, it);
    }

    private static String failLine(Enhance.Cost c) {
        return c.down() ? "§c실패하면 한 단계 하락" : "§7실패해도 그대로";
    }

    private static String lackNote(List<String> lack) {
        return Workshop.lackNote(lack);
    }

    private static String gapNote(double[] gap) {
        return gap == null ? "" : "§e" + Js.str(gap[0]) + "렙부터 사용 §7(지금 최고 " + Js.str(gap[1]) + ")";
    }

    /** 강화 탭: 가방 (핫바부터) 의 RPG 무기 */
    static List<SmithNet.Entry> enhanceEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        for (int i = 0; i < Inv.SIZE && out.size() < SmithNet.MAX_ENTRIES; i++) {
            EnhanceWindow.Entry e = EnhanceWindow.entryAt(sp, i);
            if (e == null) continue;
            String key = "w:" + i;
            String name = "§r" + EnhanceWindow.slotName(e);
            String sub = (i < 9 ? "단축 칸 " + (i + 1) : "가방") + " §8· §7" + e.w().typeLabel() + " " + e.w().level() + "렙";
            Enhance.Cost c = Enhance.enhanceCost(e.n(), e.w().grade().id());
            if (e.n() >= EnhanceWindow.MAX || c == null) {
                out.add(new SmithNet.Entry(key, e.item(), name, "+" + e.n(), 2, "", sub, "+" + e.n(), "", List.of(EnhanceWindow.attackText(e.w(), e.n(), e.n(), "§7", "§a", "§a")),
                        -1, List.of(), "", -1, "강화", false, "§6최대 강화 +" + EnhanceWindow.MAX, List.of()));
                continue;
            }
            List<String> lack = EnhanceWindow.lacking(sp, c, false);
            // 0.44.1 피해 % 대신 강화가 반영된 공격력 (지금 → 성공하면)
            List<String> lines = List.of(EnhanceWindow.attackText(e.w(), e.n(), e.n() + 1, "§7", "§f", "§a"));
            out.add(new SmithNet.Entry(key, e.item(), name, "+" + e.n(), lack.isEmpty() ? 0 : 1, "", sub, "+" + e.n(), "+" + (e.n() + 1), lines, c.rate(),
                    enhanceCosts(sp, c), failLine(c), c.down() ? scrolls(sp) : -1, "강화", lack.isEmpty(), lackNote(lack), List.of()));
        }
        return out;
    }

    /** 장신구 · 아티팩트 탭: 판 → 낀 장신구 → 가방 (핫바부터 · 왼손) */
    static List<SmithNet.Entry> gearEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        List<Integer> locs = new ArrayList<>();
        if (Relics.enabled() && RelicSystem.TYPE != null) for (int i = 0; i < RelicSystem.SIZE; i++) locs.add(GearEnhance.BOARD + i);
        if (Artifacts.enabled() && ArtifactSystem.TYPE != null) for (int i = 0; i < ArtifactSystem.SIZE; i++) locs.add(GearEnhance.ACC + i);
        for (int i = 0; i < Inv.SIZE; i++) locs.add(i);
        locs.add(Inventory.SLOT_OFFHAND);
        String lastGroup = "";
        for (int loc : locs) {
            if (out.size() >= SmithNet.MAX_ENTRIES) break;
            GearEnhance.Entry e = GearEnhance.entryAt(sp, loc);
            if (e == null) continue;
            String g = loc >= GearEnhance.BOARD ? "아티팩트 판" : loc >= GearEnhance.ACC ? "낀 장신구" : "가방";
            String group = g.equals(lastGroup) ? "" : g;
            lastGroup = g;
            String key = "g:" + loc;
            String name = "§r" + e.name(e.n());
            String sub = e.where() + (e.stat() != null ? " §8· §7" + Relics.lineName(e.stat()) + " 줄" : e.relic() != null ? " §8· §7판에 끼면 스탯 줄" : "");
            Enhance.Cost c = Enhance.gearCost(e.n(), e.grade());
            if (e.n() >= EnhanceWindow.MAX || c == null) {
                out.add(new SmithNet.Entry(key, e.item(), name, "+" + e.n(), 2, group, sub, "+" + e.n(), "", List.of("§a" + e.effect(e.n())), -1, List.of(), "", -1, "강화",
                        false, "§6최대 강화 +" + EnhanceWindow.MAX, List.of()));
                continue;
            }
            List<String> lack = EnhanceWindow.lacking(sp, c, false);
            List<String> lines = List.of("§7" + e.effect(e.n()), "§a→ " + e.effect(e.n() + 1));
            out.add(new SmithNet.Entry(key, e.item(), name, "+" + e.n(), lack.isEmpty() ? 0 : 1, group, sub, "+" + e.n(), "+" + (e.n() + 1), lines, c.rate(),
                    enhanceCosts(sp, c), failLine(c), c.down() ? scrolls(sp) : -1, "강화", lack.isEmpty(), lackNote(lack), List.of()));
        }
        return out;
    }

    /** 레벨 올리기 탭: 영웅 · 초월 무기 (가방) */
    static List<SmithNet.Entry> raiseEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        Item ore = Smith.ore();
        for (Smith.Raise e : Smith.raisable(sp)) {
            List<SmithNet.Cost> costs = new ArrayList<>();
            List<String> lack;
            if (e.goldStep()) {
                if (e.gold() > 0) costs.add(goldCost(sp, e.gold()));
                for (Map.Entry<String, Integer> x : e.items().entrySet()) costs.add(matCost(sp, x.getKey(), x.getValue()));
                lack = Smith.goldStepLack(sp, e);
            } else {
                costs.add(silverCost(sp, e.emerald()));
                costs.add(cost(ore, oreName(), ore == null ? 0 : Inv.count(sp, ore), e.ore()));
                lack = new ArrayList<>();
                if (Inv.money(sp) < e.emerald()) lack.add(Inv.CURRENCY_NAME + " " + (e.emerald() - Inv.money(sp)));
                int oc = ore == null ? 0 : Inv.count(sp, ore);
                if (oc < e.ore()) lack.add(oreName() + " " + (e.ore() - oc));
            }
            double[] gap = Smith.levelGap(sp, e.to());
            String note = !lack.isEmpty() ? lackNote(lack) : gapNote(gap);
            int enh = Smith.enhOf(e);
            List<String> lines = List.of("§7" + Smith.statLine(e.w(), enh), "§a→ " + Smith.statLine(e.to(), enh), "§7요구 " + Weapons.reqsText(e.to().reqs(), false));
            int state = !lack.isEmpty() ? 1 : gap != null ? 3 : 0;
            out.add(new SmithNet.Entry("r:" + e.slot(), e.item(), "§r" + e.w().name(), e.w().level() + "→" + e.to().level(), state, "",
                    (e.slot() < 9 ? "단축 칸 " + (e.slot() + 1) : "가방") + " §8· §7" + e.w().typeLabel(), e.w().level() + "렙", e.to().level() + "렙", lines, -1, costs,
                    "§7강화 단계 · 이름은 그대로 옮김", -1, "올리기", lack.isEmpty(), note, List.of()));
        }
        return out;
    }

    /** 제작 탭 (쪽): 무기 · 공허의 갑옷 · 장신구 */
    static List<SmithNet.Entry> craftEntries(ServerPlayer sp, int page) {
        if (Smith.voidPage(page)) return voidEntries(sp);
        if (Smith.artifactPage(page)) return artifactEntries(sp);
        List<SmithNet.Entry> out = new ArrayList<>();
        String grade = page >= 0 && page < Smith.CRAFT_PAGES.size() ? Smith.CRAFT_PAGES.get(page).grade() : "";
        List<Smith.Recipe> all = Smith.craftRecipes();
        Item ore = Smith.ore();
        for (int i = 0; i < all.size(); i++) {
            Smith.Recipe e = all.get(i);
            if (!e.grade().equals(grade)) continue;
            List<SmithNet.Cost> costs = new ArrayList<>();
            for (Smith.Mat m : e.mats()) costs.add(cost(m.item(), m.name(), m.item() == null ? 0 : Inv.count(sp, m.item()), m.count()));
            costs.add(silverCost(sp, e.emerald()));
            if (e.ore() > 0) costs.add(cost(ore, oreName(), ore == null ? 0 : Inv.count(sp, ore), e.ore()));
            List<String> lack = Smith.craftLack(sp, e);
            double[] gap = Smith.levelGap(sp, e.w());
            String note = !lack.isEmpty() ? lackNote(lack) : gapNote(gap);
            List<String> lines = new ArrayList<>();
            lines.add("§7" + Smith.statLine(e.w()));
            lines.add("§7요구 " + Weapons.reqsText(e.w().reqs(), false));
            lines.add("§8주재료: " + e.mat().name() + " §8(" + e.mat().from() + ")");
            int state = !lack.isEmpty() ? 1 : gap != null ? 3 : 0;
            out.add(new SmithNet.Entry("c:" + i, Shop.weaponItem(e.w()), "§r" + e.w().name(), lack.isEmpty() ? "가능" : "", state, "",
                    e.w().typeLabel() + " " + e.w().level() + "렙 §8· §7" + e.mat().from(), "", "", List.copyOf(lines), -1, costs, "§7강화 +0 으로 만듦", -1, "만들기", lack.isEmpty(),
                    note, List.of()));
        }
        return out;
    }

    /** 공허의 갑옷 쪽: 종류 열 (고를 것 = 부위 넷 · 한 벌) */
    static List<SmithNet.Entry> voidEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        if (!VoidArmorCraft.enabled()) return out;
        Obj types = R.obj("ARMOR_TYPES");
        for (String type : R.strings("ARMOR_TYPE_ORDER")) {
            List<Armor.Piece> set = VoidArmorCraft.set(type);
            if (set.isEmpty() || set.stream().anyMatch(java.util.Objects::isNull)) continue;
            Armor.Piece chest = set.get(Math.min(1, set.size() - 1));
            List<SmithNet.Variant> vs = new ArrayList<>();
            boolean any = false;
            for (Armor.Piece p : set) {
                VoidArmorCraft.Cost c = VoidArmorCraft.pieceCost(p.piece());
                List<String> lack = VoidArmorCraft.lack(sp, c);
                vs.add(new SmithNet.Variant(p.pieceName(), ArmorShop.armorItem(p), voidCosts(sp, c), lack.isEmpty(), lackNote(lack)));
                any |= lack.isEmpty();
            }
            VoidArmorCraft.Cost all = VoidArmorCraft.setCost();
            List<String> lackAll = VoidArmorCraft.lack(sp, all);
            vs.add(new SmithNet.Variant("한 벌", ArmorShop.armorItem(chest), voidCosts(sp, all), lackAll.isEmpty(), lackNote(lackAll)));
            any |= lackAll.isEmpty();
            List<String> lines = new ArrayList<>();
            lines.add("§7세트 방어 §f" + Js.str(chest.setProt()) + " §8· " + Armor.bonusText(chest.setBonus(), false));
            lines.add("§7요구 " + Armor.armorReqText(chest));
            List<String> ws = new ArrayList<>();
            for (String w : Armor.weaponsFor(type)) ws.add(R.obj("TYPES").obj(w).str("name"));
            if (!ws.isEmpty()) lines.add("§8어울리는 무기: " + String.join(" · ", ws));
            Obj t = types.objOrNull(type);
            String label = t != null ? t.str("color") + t.str("name") + " 갑옷" : chest.typeName();
            out.add(new SmithNet.Entry("v:" + type, ArmorShop.armorItem(chest), "§r" + label + " §5공허", any ? "가능" : "", any ? 0 : 1, "",
                    "초월 " + VoidArmorCraft.level() + "렙 §8· §7부위마다 또는 한 벌", "", "", List.copyOf(lines), -1, List.of(), "§7가방이 차면 만들지 않음", -1, "만들기", any, "",
                    List.copyOf(vs)));
        }
        return out;
    }

    private static List<SmithNet.Cost> voidCosts(ServerPlayer sp, VoidArmorCraft.Cost c) {
        List<SmithNet.Cost> out = new ArrayList<>();
        if (c.gold() > 0) out.add(goldCost(sp, c.gold()));
        for (Map.Entry<String, Integer> x : c.items().entrySet()) out.add(matCost(sp, x.getKey(), x.getValue()));
        return out;
    }

    /** 장신구 쪽: 종류 × 등급 (묶음 = 종류) */
    static List<SmithNet.Entry> artifactEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        if (!ArtifactCraft.enabled()) return out;
        double lv = RpgCombat.reqLevelOf(sp);
        for (String type : Artifacts.types()) {
            boolean first = true;
            for (Grade g : R.grades()) {
                Artifacts.Art a = Artifacts.art(type, g.id());
                if (a == null) continue;
                Artifacts.Cost c = Artifacts.cost(a.grade());
                List<SmithNet.Cost> costs = new ArrayList<>();
                if (c.silver() > 0) costs.add(silverCost(sp, c.silver()));
                if (c.gold() > 0) costs.add(goldCost(sp, c.gold()));
                for (Map.Entry<String, Integer> x : c.items().entrySet()) costs.add(matCost(sp, x.getKey(), x.getValue()));
                List<String> lack = ArtifactCraft.lack(sp, c);
                boolean usable = Artifacts.usable(a, lv);
                String note = !lack.isEmpty() ? lackNote(lack) : usable ? "" : "§e" + a.level() + "렙부터 낄 수 있음";
                out.add(new SmithNet.Entry("a:" + type + ":" + g.id(), ArtifactCraft.item(a), "§r" + a.coloredName(), lack.isEmpty() ? "가능" : "",
                        !lack.isEmpty() ? 1 : usable ? 0 : 3, first ? a.typeName() : "", a.gradeName() + " §8· §7착용 " + a.level() + "렙", "", "",
                        List.of("§a" + Artifacts.optionText(a)), -1, costs, "", -1, "만들기", lack.isEmpty(), note, List.of()));
                first = false;
            }
        }
        return out;
    }

    // ------------------------------------------------------------ 글

    /** 등급 순번 (아이템 — RPG 무기 · 장신구 · 아티팩트, 모르면 0) */
    static int gradeOf(ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        Weapon w = RpgCombat.weaponOf(s);
        if (w != null) return w.gradeIndex();
        Artifacts.Art a = ArtifactItem.of(s);
        if (a != null) return a.gradeIndex();
        Relics.Relic r = RelicItem.of(s);
        if (r != null) return r.gradeIndex();
        return 0;
    }

    /** 밝은 상자 창용 짙은 색 (§0 · §8 · §2 · §4 …) → 어두운 창용 */
    static String onDark(String s) {
        return Workshop.onDark(s);
    }
}
