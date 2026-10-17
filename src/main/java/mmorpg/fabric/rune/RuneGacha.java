package mmorpg.fabric.rune;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Gacha;
import mmorpg.core.Runes;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gacha.GachaSystem;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 룬 뽑기 (runes.js "뽑기") — 0.14.0 부터는 모드가 없는 클라이언트 · 시험용 (모드가 있으면 뽑기권 우클릭은 뽑기 창 — mmorpg.fabric.gacha):
 * 뽑기권을 바로 쓰고 DRAW_TICKS 동안 눈앞 마법진 연출 (등급 색이 차례로 바뀌다 결과 등급에서 멈춤) 뒤 룬 지급 · 큰 글자 · 채팅 (영웅부터 모두에게 알림).
 * 확률 · 천장은 뽑기 창과 같음 (GachaSystem.draw). 뽑은 룬은 먼저 적어 두고 (RuneData.pending) 연출 뒤에 줌 — 연출 중에 나가면 다시 들어올 때 받음.
 * 고급 룬 뽑기권: 일반 룬 없음 (연출의 색도 희귀부터).
 */
public final class RuneGacha {
    private RuneGacha() {}

    public static final int DRAW_TICKS = 26;
    /** 플레이어 → 지금 연출 중인 뽑기의 룬 ID */
    private static final Map<UUID, String> DRAWING = new HashMap<>();
    private static final List<String> GRADES = Runes.GRADE_ORDER;

    static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer sp = handler.getPlayer();
            server.execute(() -> claimPending(sp));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DRAWING.clear());
    }

    static void forget(UUID id) {
        DRAWING.remove(id);
    }

    /** 연출 중인지 (시험용) */
    public static boolean drawing(ServerPlayer sp) {
        return DRAWING.containsKey(sp.getUUID());
    }

    /** 적어 둔 룬 하나를 목록에서 빼고 줌. 목록에 없으면 (이미 받음) false */
    private static boolean deliver(ServerPlayer sp, String rid) {
        List<String> list = RuneData.pending(sp);
        if (!list.remove(rid)) return false;
        RuneData.setPending(sp, list);
        Runes.Rune r = Runes.parseRune(rid);
        if (r == null) return false;
        Notify.give(sp, RuneItems.stack(r.family(), r.grade()));
        return true;
    }

    /** 들어올 때: 지난번 연출 중에 나가서 받지 못한 룬을 줌 (지금 연출 중인 뽑기의 룬은 연출 끝에) */
    public static void claimPending(ServerPlayer sp) {
        if (sp.isRemoved()) return;
        String now = DRAWING.get(sp.getUUID());
        boolean skipped = false;
        for (String rid : RuneData.pending(sp)) {
            if (!skipped && rid.equals(now)) {
                skipped = true;
                continue;
            }
            Runes.Rune r = Runes.parseRune(rid);
            if (r == null || !deliver(sp, rid)) continue;
            Notify.say(sp, "지난번에 뽑은 룬을 받았습니다: " + r.gradeColor() + r.def().str("name") + "§r §7(" + r.famName() + " 룬 · " + r.gradeName() + ")");
        }
    }

    /** 뽑기 마법진 자리: 눈앞 2.4칸, 조준점보다 조금 아래 (at), 화면 가로 방향 (side), 그 아래 땅 (ground) */
    private record Altar(Vec3 at, Vec3 side, Vec3 ground) {}

    private static Altar altarOf(ServerPlayer sp) {
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 ground = V.add(sp.position(), f, 2.4);
        return new Altar(V.up(ground, 1.45), V.rightOf(f), ground);
    }

    /**
     * 뽑기권 사용 (useTicket): 뽑기권을 바로 쓰고 연출 (DRAW_TICKS) 뒤 룬 지급. 연출 중에는 다시 못 뽑음.
     * 뽑기권은 연출 전에 씀 (연출의 색을 보고 뽑기권을 버려 공짜로 다시 뽑을 수 없게). adv = 고급 룬 뽑기권
     */
    public static boolean useTicket(ServerPlayer sp, boolean adv) {
        return useTicket(sp, adv ? RuneItems.ADV_TICKET : RuneItems.TICKET);
    }

    /**
     * 뽑기권 아이템으로 뽑기 (모드가 없는 클라이언트 · 시험 — 모드가 있으면 뽑기권 우클릭은 뽑기 창): 룬 뽑기권 · 고급 룬 뽑기권 · 스탯 룬 뽑기권 (7단계 —
     * 계열이 정해짐, 등급 확률은 같음) · 한정 룬 뽑기권 (0.14.0 — 열린 한정 뽑기). 확률 · 천장 · 기록은 뽑기 창과 같음 (GachaSystem.draw — 적어 두고 연출 뒤 줌)
     */
    public static boolean useTicket(ServerPlayer sp, TicketItem ticket) {
        if (ticket == null) return false;
        boolean adv = ticket.advanced();
        if (DRAWING.containsKey(sp.getUUID())) {
            Notify.warn(sp, "§7룬을 뽑는 중입니다");
            return false;
        }
        GachaSystem.Drawn drawn = GachaSystem.draw(sp, GachaSystem.keyOf(ticket), 1, GachaSystem.Deliver.PENDING);
        if (drawn.error() != null) {
            Notify.warn(sp, drawn.error());
            return false;
        }
        Gacha.Pull res = drawn.pulls().get(0);
        String rid = res.id();
        DRAWING.put(sp.getUUID(), rid);
        int gi = GRADES.indexOf(res.grade());
        int g0 = adv ? 1 : 0; // 연출의 첫 색 (고급은 희귀부터)
        ServerLevel level0 = sp.level();
        SkillFx.play(level0, "block.enchanting_table.use", sp.position(), 1.0, 1);
        SkillFx.play(level0, "beacon.power", sp.position(), 1.6, 0.6);
        // 연출: 눈앞 마법진이 한 칸씩 그려지며 등급 색이 차례로 바뀌다가 결과 등급에서 멈춤 (1인칭에서도 보이게 눈높이), 발밑 룬은 주위 사람에게 보이는 표시
        for (int t = 0; t < DRAW_TICKS; t += 2) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (sp.isRemoved()) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                int step = Math.min(gi, g0 + (int) Math.floor(((double) tt / (DRAW_TICKS - 6)) * (gi + 1 - g0)));
                double[] col = RuneKit.RUNE_GRADE_FX.get(GRADES.get(step));
                Altar al = altarOf(sp);
                SkillFx.rune(level, c, Opt.of("size", 1.4 + tt * 0.03, "life", 0.14, "color", col, "alpha", 0.95, "progress", 1.0, "rate", 0.0, "spin", tt * 14.0));
                SkillFx.fx(level, "rpg:magic_circle", al.at(), Opt.of("size", 0.55 + tt * 0.012, "life", 0.14, "spin", tt * 14.0, "progress",
                        Math.min(1, (tt + 2.0) / (DRAW_TICKS - 6)), "color", col, "alpha", 0.95));
                // 마법진으로 빨려 드는 빛 알갱이
                double a = tt * 0.9;
                Vec3 from = V.add(V.add(al.at(), al.side(), Math.cos(a) * 1.3), V.UP, Math.sin(a) * 1.0);
                SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(V.sub(al.at(), from)), "speed", 2.6, "size", 0.14, "grow", 0.6, "life", 0.45,
                        "color", col, "alpha", 1.0));
                if (tt % 6 == 0) SkillFx.play(level, "note.pling", c, 0.8 + step * 0.2 + tt * 0.02, 0.5);
            });
        }
        Tasks.later(DRAW_TICKS, () -> {
            DRAWING.remove(sp.getUUID());
            if (sp.isRemoved()) return; // 나갔으면 다시 들어올 때 (claimPending)
            Runes.Rune r = Runes.parseRune(rid);
            if (r == null || !deliver(sp, rid)) return;
            revealFx(sp, res.family(), res.grade(), gi);
            String sn = r.famName() + " 룬";
            title(sp, r.gradeColor() + r.gradeName() + " 룬!", "§f" + r.def().str("name") + " §7(" + sn + ")");
            Notify.say(sp, "룬 획득: " + r.gradeColor() + r.def().str("name") + "§r §7(" + sn + " · " + r.gradeName() + ") §8- " + r.def().str("desc"));
            if (gi >= GRADES.indexOf(RuneKit.RUNE.str("broadcastFrom"))) {
                sp.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava(
                        Notify.PREFIX + sp.getName().getString() + "님이 " + r.gradeColor() + r.gradeName() + " 룬 " + r.def().str("name") + "§r§f을(를) 얻었습니다!")), false);
            }
        });
        return true;
    }

    /** 큰 글자 (showRealTitle — 0.5초 차오름 · 3.5초 · 1초 걷힘) */
    static void title(ServerPlayer sp, String title, String subtitle) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        if (subtitle != null) sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(BedrockText.toJava(subtitle))));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.literal(BedrockText.toJava(title))));
    }

    /** 결과 연출: 눈앞에 등급 색 빛기둥 + 마법진 섬광 + 폭발 + 소리 (높은 등급일수록 크게) */
    private static void revealFx(ServerPlayer sp, String family, String grade, int gi) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Altar al = altarOf(sp);
        double[] col = RuneKit.RUNE_GRADE_FX.get(grade);
        double[] sg = RuneKit.colorOf(family)[0];
        double h = 2 + gi * 1.2;
        // 빛기둥은 눈앞 마법진 자리에 (몸에 세우면 1인칭 카메라가 기둥 안에 들어가 안 보임)
        SkillFx.fx(level, "rpg:arrow_shot", V.up(al.ground(), h), Opt.of("dir", V.UP, "speed", 0.0, "size", h, "thick", 0.5 + gi * 0.2, "life", 0.7 + gi * 0.1,
                "color", col, "alpha", 0.9));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(al.ground(), h), Opt.of("dir", V.UP, "speed", 0.0, "size", h * 0.95, "thick", 0.18 + gi * 0.06, "life",
                0.6 + gi * 0.1, "color", SkillFx.WHITE, "alpha", 1.0));
        // 1인칭 화면을 다 덮지 않을 크기 (초월도 화면 높이의 3/4 정도)
        SkillFx.fx(level, "rpg:magic_circle_flash", al.at(), Opt.of("size", 0.85 + gi * 0.15, "life", 0.5 + gi * 0.08, "spin", Kit.rand() * 360, "progress", 1.0,
                "color", col, "alpha", 1.0));
        SkillFx.burst(level, al.at(), col, 1.0 + gi * 0.3, 0.4);
        SkillFx.shock(level, al.ground(), col, 1.2 + gi * 0.4, 0.5);
        SkillFx.sparks(level, al.at(), sg, 8 + gi * 3, 4 + gi);
        SkillFx.rune(level, c, Opt.of("size", 1.8 + gi * 0.4, "life", 1.0, "color", col, "alpha", 0.9, "progress", 1.0, "rate", 60.0, "grow", 0.3));
        String[] sounds = {"random.orb", "random.levelup", "beacon.activate", "random.totem", "mob.enderdragon.growl"};
        double[] pitch = {1.4, 1.2, 1.2, 1.0, 0.9};
        double[] vol = {0.8, 0.8, 1, 1, 0.8};
        SkillFx.play(level, sounds[gi], c, pitch[gi], vol[gi]);
        if (gi >= 3) {
            SkillFx.vanilla(level, "minecraft:totem_particle", V.up(c, 1.2));
            SkillFx.play(level, "firework.twinkle", c, 1.1, 1);
        }
        if (gi >= 4) {
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                Tasks.later(3 + i * 3, () -> SkillFx.shock(level, c, ii % 2 == 1 ? SkillFx.WHITE : col, 3 + ii * 1.5, 0.6));
            }
            SkillFx.play(level, "firework.large_blast", c, 1.0, 1);
        }
    }
}
