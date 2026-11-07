package mmorpg.fabric.field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.MenuForm;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
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
 * 8칸 안에 처음 오면 "새 이동 지점" (플레이어 속성 rpg:waystones), 돌을 누르면 가 본 곳 목록 → 고른 곳으로 순간 이동. 마을 광장은 처음부터 열림.
 * 서버만으로 동작 (창은 서버 창 — 모드 없는 클라이언트는 상자 창).
 */
public final class FieldWaystones {
    private FieldWaystones() {}

    static final String KEY = "rpg:waystones";
    static final String HOME = "waystone_midgard";

    /** 이동 지점: 번호 · 이름 · 돌 자리 */
    public record Stone(String id, String name, BlockPos pos) {}

    static final Map<String, String> NAMES = Map.of(
            "waystone_midgard", "마을 광장",
            "waystone_farm", "올라프의 농가 (농장 섬)",
            "waystone_desert", "라그나르의 오아시스 (사막 섬)",
            "waystone_sea", "비요른의 항구 (바다 섬)",
            "waystone_north", "스카디의 사냥막 (북쪽 섬)",
            "waystone_void", "균열 전초기지 (공허 섬)",
            "waystone_muspel", "불꽃 전초기지 (무스펠 섬)");

    private static volatile List<Stone> stones;

    static void init() {
        UseBlockCallback.EVENT.register(FieldWaystones::onUse);
        ServerTickEvents.END_SERVER_TICK.register(FieldWaystones::tick);
    }

    public static List<Stone> stones() {
        List<Stone> s = stones;
        if (s == null) {
            synchronized (FieldWaystones.class) {
                s = stones;
                if (s == null) {
                    List<Stone> out = new ArrayList<>();
                    for (Blueprint.Mark m : FieldBuild.build(FieldWorld.terrain()).marks()) {
                        if (!m.id().startsWith("waystone")) continue;
                        out.add(new Stone(m.id(), NAMES.getOrDefault(m.id(), m.id()), BlockPos.containing(m.x(), m.y(), m.z())));
                    }
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

    /** 가 본 이동 지점 (마을 광장은 늘) */
    public static List<String> known(ServerPlayer sp) {
        List<String> out = new ArrayList<>();
        out.add(HOME);
        String raw = Props.get(sp, KEY);
        if (raw != null) for (String id : raw.split(",")) if (!id.isBlank() && !out.contains(id)) out.add(id);
        return out;
    }

    static void learn(ServerPlayer sp, Stone s) {
        List<String> k = known(sp);
        if (k.contains(s.id())) return;
        k.add(s.id());
        k.remove(HOME);
        Props.set(sp, KEY, String.join(",", k));
        Notify.notify(sp, "§b[이동 지점] §f새 이동 지점: §e" + s.name() + " §7(돌을 누르면 가 본 곳으로 이동)", 100);
        SkillFx.playFor(sp, "random.levelup", 1.4, 0.6);
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
        for (ServerPlayer sp : level.players()) {
            for (Stone s : stones()) {
                if (sp.position().distanceToSqr(Vec3.atCenterOf(s.pos())) < 64) learn(sp, s);
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
        learn(sp, here);
        open(sp, here);
        return InteractionResult.SUCCESS;
    }

    /** 이동 지점 창: 가 본 곳 (지금 자리 빼고) → 고르면 그곳 돌 앞 (남쪽 2칸) 으로 */
    static void open(ServerPlayer sp, Stone here) {
        Map<String, Stone> byId = new LinkedHashMap<>();
        for (Stone s : stones()) byId.put(s.id(), s);
        List<Stone> list = new ArrayList<>();
        for (String id : known(sp)) {
            Stone s = byId.get(id);
            if (s != null && !s.id().equals(here.id())) list.add(s);
        }
        if (list.isEmpty()) {
            Notify.notify(sp, "§b[이동 지점] §7아직 다른 이동 지점에 가 보지 않았습니다. 섬의 거점에 가면 열립니다.", 80);
            return;
        }
        MenuForm form = new MenuForm("§l이동 지점", "§f여기: §e" + here.name() + "\n§7갈 곳을 고르세요 (가 본 곳만)");
        for (Stone s : list) form.button("§1" + s.name());
        form.show(sp, sel -> {
            if (sel < 0 || sel >= list.size()) return;
            travel(sp, list.get(sel));
        });
    }

    static void travel(ServerPlayer sp, Stone to) {
        ServerLevel level = sp.level();
        Vec3 from = sp.position();
        SkillFx.vanilla(level, "minecraft:reverse_portal", from.add(0, 1, 0));
        level.getChunk(to.pos());
        Vec3 dest = new Vec3(to.pos().getX() + 0.5, to.pos().getY() - 1, to.pos().getZ() + 2.5);
        sp.teleportTo(level, dest.x, dest.y, dest.z, java.util.Set.of(), 180F, 0F, true);
        SkillFx.playFor(sp, "random.orb", 0.7, 1);
        for (int i = 0; i < 20; i++) {
            double a = i / 20.0 * Math.PI * 2;
            SkillFx.vanilla(level, "minecraft:end_rod", new Vec3(dest.x + Math.cos(a) * 1.2, dest.y + 0.3, dest.z + Math.sin(a) * 1.2));
        }
        Notify.notify(sp, "§b[이동 지점] §f" + to.name(), 60);
    }
}
