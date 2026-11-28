package mmorpg.fabric.field;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.MenuForm;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 섬 맵 이동 지점 (2026-11-07 — 사용자가 고른 섬형: "섬 사이: 다리 · 배 · 포털"): 마을 광장과 거점마다 이동 지점 돌 (자석석 · 끝막대 빛).
 * 돌을 누르면 열린 곳 목록 → 고른 곳으로 순간 이동. 서버만으로 동작 (창은 서버 창 — 모드 없는 클라이언트는 상자 창).
 * <p>
 * 2026-11-28 다리 없음 (사용자 "다리 없앨거임 텔포로 이동시킬거여서 다리 없애고 그냥 섬을 멀리 둬도 됨"): 섬 사이는 이 돌로만 오감. 걸어서 가 볼 수
 * 없으므로 섬의 돌은 그 섬의 레벨 (field.json islands.level 의 앞 수) 에 오르면 열림 (마을 광장은 처음부터). 가까이 가 본 돌도 열림 (곶처럼 걸어서
 * 닿는 곳). 창에는 이야기 차례로 모든 곳을 두고, 잠긴 곳은 "레벨 N부터" 로 보임. 섬이 새로 열리면 알림.
 */
public final class FieldWaystones {
    private FieldWaystones() {}

    static final String KEY = "rpg:waystones";
    static final String HOME = "waystone_midgard";

    /** 이동 지점: 번호 · 이름 · 돌 자리 · 섬 · 열리는 레벨 (마을 광장 0) */
    public record Stone(String id, String name, BlockPos pos, String island, int level) {}

    /** 이름 (이야기 차례 — 창의 차례) */
    static final List<String> ORDER = List.of("waystone_midgard", "waystone_farm", "waystone_desert", "waystone_sea", "waystone_snow", "waystone_north",
            "waystone_shield", "waystone_muspel", "waystone_void", "waystone_rift");

    static final Map<String, String> NAMES = Map.of(
            "waystone_midgard", "마을 광장",
            "waystone_farm", "올라프의 농가 (농장 섬)",
            "waystone_desert", "라그나르의 오아시스 (사막 섬)",
            "waystone_sea", "비요른의 항구 (바다 섬)",
            "waystone_snow", "눈꽃성 남쪽 광장 (눈꽃성 섬)",
            "waystone_north", "스카디의 사냥막 (북쪽 섬)",
            "waystone_shield", "방패 성벽 북문 (불 나라 섬)",
            "waystone_muspel", "불꽃 전초기지 (무스펠 섬)",
            "waystone_void", "균열 전초기지 (균열 곶)",
            "waystone_rift", "공허 섬 어귀 (공허 섬)");

    private static volatile List<Stone> stones;

    static void init() {
        UseBlockCallback.EVENT.register(FieldWaystones::onUse);
        ServerTickEvents.END_SERVER_TICK.register(FieldWaystones::tick);
    }

    /** 이동 지점 모두 (이야기 차례) */
    public static List<Stone> stones() {
        List<Stone> s = stones;
        if (s == null) {
            synchronized (FieldWaystones.class) {
                s = stones;
                if (s == null) {
                    FieldTerrain t = FieldWorld.terrain();
                    FieldTerrain.Column c = new FieldTerrain.Column();
                    List<Stone> out = new ArrayList<>();
                    for (Blueprint.Mark m : FieldBuild.build(t).marks()) {
                        if (!m.id().startsWith("waystone")) continue;
                        t.sample((int) Math.floor(m.x()), (int) Math.floor(m.z()), c);
                        FieldPlan.Island is = c.island;
                        int lv = m.id().equals(HOME) || is == null ? 0 : is.levelMin();
                        out.add(new Stone(m.id(), NAMES.getOrDefault(m.id(), m.id()), BlockPos.containing(m.x(), m.y(), m.z()), is == null ? "" : is.id(), lv));
                    }
                    out.sort(Comparator.comparingInt(x -> ORDER.indexOf(x.id()) < 0 ? 99 : ORDER.indexOf(x.id())));
                    stones = s = List.copyOf(out);
                }
            }
        }
        return s;
    }

    static Stone at(BlockPos pos) {
        for (Stone s : stones()) if (s.pos().equals(pos)) return s;
        return null;
    }

    /** 알린 · 가 본 이동 지점 (마을 광장은 늘) */
    public static List<String> known(ServerPlayer sp) {
        List<String> out = new ArrayList<>();
        out.add(HOME);
        String raw = Props.get(sp, KEY);
        if (raw != null) for (String id : raw.split(",")) if (!id.isBlank() && !out.contains(id)) out.add(id);
        return out;
    }

    /** 이 사람이 이 돌로 갈 수 있는지: 마을 광장 · 레벨이 그 섬의 레벨 이상 · 가 본 곳 */
    public static boolean open(ServerPlayer sp, Stone s) {
        return s.level() <= 0 || Quests.questLevel(sp) >= s.level() || known(sp).contains(s.id());
    }

    /** 알림 · 기록: near = 돌 곁에 와서 (가 본 곳), 아니면 레벨로 섬이 열림 */
    static void learn(ServerPlayer sp, Stone s, boolean near) {
        List<String> k = known(sp);
        if (k.contains(s.id())) return;
        k.add(s.id());
        k.remove(HOME);
        Props.set(sp, KEY, String.join(",", k));
        if (near) Notify.notify(sp, "§b[이동 지점] §f새 이동 지점: §e" + s.name() + " §7(돌을 누르면 열린 곳으로 이동)", 100);
        else Notify.notify(sp, "§b[이동 지점] §f새 섬으로 가는 길이 열렸습니다: §e" + s.name() + " §7(아무 이동 지점 돌에서)", 120);
        SkillFx.playFor(sp, "random.levelup", 1.4, 0.6);
        if (!near) return;
        ServerLevel level = sp.level();
        Vec3 c = Vec3.atCenterOf(s.pos());
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2;
            SkillFx.vanilla(level, "minecraft:end_rod", new Vec3(c.x + Math.cos(a) * 1.6, c.y + 0.5, c.z + Math.sin(a) * 1.6));
        }
    }

    private static int clock;

    private static void tick(MinecraftServer server) {
        if (++clock % 20 != 0) return;
        ServerLevel level = server.overworld();
        if (!FieldWorld.isField(level)) return;
        boolean levels = clock % 100 == 0;
        for (ServerPlayer sp : level.players()) {
            for (Stone s : stones()) {
                if (sp.position().distanceToSqr(Vec3.atCenterOf(s.pos())) < 64) learn(sp, s, true);
                else if (levels && s.level() > 0 && Quests.questLevel(sp) >= s.level()) learn(sp, s, false);
            }
        }
    }

    static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND || !FieldWorld.isField(sl) || sl != sl.getServer().overworld()) return InteractionResult.PASS;
        BlockPos pos = hit.getBlockPos();
        if (!level.getBlockState(pos).is(Blocks.LODESTONE)) return InteractionResult.PASS;
        Stone here = at(pos);
        if (here == null) return InteractionResult.PASS;
        learn(sp, here, true);
        menu(sp, here);
        return InteractionResult.SUCCESS;
    }

    /** 이동 지점 창: 이야기 차례로 모든 곳 (지금 자리 빼고) — 열린 곳은 고르면 그곳 돌 앞 (남쪽 2칸) 으로, 잠긴 곳은 레벨을 알려 줌 */
    static void menu(ServerPlayer sp, Stone here) {
        List<Stone> list = new ArrayList<>();
        for (Stone s : stones()) if (!s.id().equals(here.id())) list.add(s);
        MenuForm form = new MenuForm("§l이동 지점", "§f여기: §e" + here.name() + "\n§7섬 사이는 이 돌로만 오갑니다. 갈 곳을 고르세요.");
        for (Stone s : list) form.button(open(sp, s) ? "§1" + s.name() : "§8" + s.name() + " §7· 레벨 " + s.level() + "부터");
        form.show(sp, sel -> {
            if (sel < 0 || sel >= list.size()) return;
            Stone to = list.get(sel);
            if (!open(sp, to)) {
                Notify.notify(sp, "§b[이동 지점] §7" + to.name() + " §7은(는) 레벨 §e" + to.level() + "§7부터 열립니다 §8(지금 " + (int) Quests.questLevel(sp) + ")", 80);
                return;
            }
            travel(sp, to);
        });
    }

    static void travel(ServerPlayer sp, Stone to) {
        ServerLevel level = sp.level();
        Vec3 from = sp.position();
        ring(level, from, 1.1, 3);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 40, 0.3, 0.8, 0.3, 0.05);
        SkillFx.play(level, "mob.endermen.portal", from, 1.2, 0.8);
        level.getChunk(to.pos());
        Vec3 dest = new Vec3(to.pos().getX() + 0.5, to.pos().getY() - 1, to.pos().getZ() + 2.5);
        sp.stopRiding();
        sp.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), 180F, 0F, true);
        SkillFx.playFor(sp, "random.orb", 0.7, 1);
        ring(level, dest, 1.2, 3);
        level.sendParticles(ParticleTypes.END_ROD, dest.x, dest.y + 1, dest.z, 24, 0.25, 0.9, 0.25, 0.02);
        Notify.notify(sp, "§b[이동 지점] §f" + to.name(), 60);
    }

    /** 끝막대 빛 고리 n 단 (발 · 허리 · 머리) */
    static void ring(ServerLevel level, Vec3 at, double r, int rows) {
        for (int row = 0; row < rows; row++) {
            double y = at.y + 0.2 + row * 0.8;
            for (int i = 0; i < 20; i++) {
                double a = i / 20.0 * Math.PI * 2 + row * 0.3;
                level.sendParticles(ParticleTypes.END_ROD, at.x + Math.cos(a) * r, y, at.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
    }
}
