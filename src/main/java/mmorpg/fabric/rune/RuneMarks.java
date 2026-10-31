package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.49.0 룬 유틸 표식 (재설계 — 사용자 "가하는 피해 증가 버프, 상대 받는 피해 증가 … 를 주고 대신 데미지를 좀 덜 주는 형식"):
 * <ul>
 * <li>받는 피해 증가 ({@link #vuln}): 적 → 배율 · 끝나는 틱 (방어 깨짐 · 표식 · 약화 · 녹은 갑옷 · 낙인 · 사냥감 표식). RpgCombat.takenMult 에 곱 — 룬 · 평타 · 직업
 * 피해 모두, 보스도. 같은 적에 여러 번 걸면 겹치지 않고 가장 센 값 · 늦게 끝나는 틱. 머리 위에 금 간 표식 (rfx_jbreak — 1초마다 다시 그림, 표식 색)</li>
 * <li>가하는 피해 증가 ({@link #empower}): 플레이어 → 배율 · 끝나는 틱 (분노 · 검세 · 기). RpgCombat.dealtMult 에 곱 (평타 · 룬 · 직업 피해). 겹치지 않음 (가장 센 값).
 * 몸의 기운 (4틱마다) · 상태 줄 ("분노 +10% 4.2초"). 죽으면 끝</li>
 * </ul>
 */
public final class RuneMarks {
    private RuneMarks() {}

    private record Vuln(LivingEntity e, long until, double add, double[] glow, double[] core) {}

    private record Buff(long until, double add, String name, double[] glow, double[] core) {}

    private static final Map<UUID, Vuln> VULN = new HashMap<>();
    private static final Map<UUID, Buff> BUFF = new HashMap<>();
    /** 금 간 표식 그림 (rfx_jbreak: 빛 0~5 · 심지 6~11) */
    private static final int BREAK_FRAMES = 6;
    /** 한 틱에 다시 그리는 표식 수 (파티클 예산) */
    private static final int MARKS_PER_TICK = 6;
    private static long ticks;

    static void init() {
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * vulnMult(e);
        java.util.function.ToDoubleFunction<ServerPlayer> dm = RpgCombat.dealtMult;
        RpgCombat.dealtMult = p -> dm.applyAsDouble(p) * buffMult(p);
        ServerTickEvents.END_SERVER_TICK.register(RuneMarks::tick);
        Notify.addHudSuffix(RuneMarks::hud);
        RuneKit.onLifeEnded(BUFF::remove);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            VULN.clear();
            BUFF.clear();
        });
    }

    // ------------------------------------------------------------ 받는 피해 증가

    /**
     * 받는 피해 증가: e 가 ticks 동안 받는 피해 × (1 + add). 처음 걸 때 금 간 표식이 크게 갈라짐 (show — 여러 적이면 앞의 몇만)
     */
    public static void vuln(LivingEntity e, double add, int ticks, double[] glow, double[] core, boolean show) {
        if (e == null || e.isRemoved() || !(add > 0) || ticks <= 0) return;
        long until = Tasks.now() + ticks;
        Vuln old = VULN.get(e.getUUID());
        boolean fresh = old == null || old.until() <= Tasks.now();
        if (old != null && old.until() > Tasks.now()) {
            add = Math.max(add, old.add());
            until = Math.max(until, old.until());
        }
        VULN.put(e.getUUID(), new Vuln(e, until, add, glow, core));
        if (show && e.level() instanceof ServerLevel level) {
            drawMark(level, e, glow, core, fresh ? 0.62 : 0.5, 0.7);
            if (fresh) SkillFx.play(level, "block.chain.break", RuneKit.overHead(e, 0.6), 1.1, 0.7);
        }
    }

    /** 이 적의 받는 피해 배율 (1 = 없음) */
    public static double vulnMult(LivingEntity e) {
        if (e == null || VULN.isEmpty()) return 1;
        Vuln v = VULN.get(e.getUUID());
        return v == null || v.until() <= Tasks.now() ? 1 : 1 + v.add();
    }

    /** 표식이 남은 틱 (없으면 0) — 시험 · 연출 */
    public static long vulnLeft(LivingEntity e) {
        Vuln v = e == null ? null : VULN.get(e.getUUID());
        return v == null ? 0 : Math.max(0, v.until() - Tasks.now());
    }

    private static void drawMark(ServerLevel level, LivingEntity e, double[] glow, double[] core, double size, double life) {
        Vec3 at = RuneKit.overHead(e, 0.55 + size * 0.4);
        double fps = BREAK_FRAMES / Math.max(0.05, life);
        Opt b = Opt.of("size", size, "life", life, "frames", (double) BREAK_FRAMES, "fps", fps, "fade", 0.3, "vel", RuneKit.entVel(e));
        RuneKit.rfx(level, "jbreak", at, b.with("color", glow, "cell", 0.0, "alpha", 0.95));
        RuneKit.rfx(level, "jbreak", at, b.with("color", core, "cell", (double) BREAK_FRAMES, "alpha", 0.9));
    }

    // ------------------------------------------------------------ 가하는 피해 증가

    /** 가하는 피해 증가: sp 가 ticks 동안 주는 피해 × (1 + add) — name 은 상태 줄 이름 (분노 · 검세 · 기) */
    public static void empower(ServerPlayer sp, double add, int ticks, String name, double[] glow, double[] core) {
        if (sp == null || sp.isRemoved() || !(add > 0) || ticks <= 0) return;
        long until = Tasks.now() + ticks;
        Buff old = BUFF.get(sp.getUUID());
        if (old != null && old.until() > Tasks.now() && old.add() > add) {
            add = old.add();
            name = old.name();
        }
        BUFF.put(sp.getUUID(), new Buff(until, add, name, glow, core));
        ServerLevel level = sp.level();
        RuneKit.bodyAura(sp, glow, Opt.of("n", 8.0, "size", 1.25, "life", 0.45, "alpha", 0.85, "force", true));
        RuneKit.auraCue(sp, core, 6);
        SkillFx.burst(level, V.up(sp.position(), 1.0), glow, 1.2, 0.35);
        SkillFx.play(level, "item.armor.equip_netherite", sp.position(), 0.9, 0.9);
        Notify.notify(sp, "§6" + name + " §f+" + Js.str(Math.round(add * 100)) + "% §7가하는 피해 · " + Js.str(Math.round(ticks / 2.0) / 10.0) + "초", 30);
    }

    /** 이 플레이어의 가하는 피해 배율 (1 = 없음) */
    public static double buffMult(ServerPlayer sp) {
        if (sp == null || BUFF.isEmpty()) return 1;
        Buff b = BUFF.get(sp.getUUID());
        return b == null || b.until() <= Tasks.now() ? 1 : 1 + b.add();
    }

    /** 버프가 남은 틱 (없으면 0) — 시험 */
    public static long empowerLeft(ServerPlayer sp) {
        Buff b = sp == null ? null : BUFF.get(sp.getUUID());
        return b == null ? 0 : Math.max(0, b.until() - Tasks.now());
    }

    // ------------------------------------------------------------ 매 틱 · 상태 줄

    private static void tick(MinecraftServer server) {
        long now = Tasks.now();
        long n = ticks++;
        if (!VULN.isEmpty()) {
            int drawn = 0;
            for (Iterator<Vuln> it = VULN.values().iterator(); it.hasNext(); ) {
                Vuln v = it.next();
                LivingEntity e = v.e();
                if (v.until() <= now || e.isRemoved() || !e.isAlive()) {
                    it.remove();
                    continue;
                }
                // 1초마다 다시 그림 (적마다 다른 틱 — 한 틱에 몰리지 않게), 끝나기 1초 전부터는 작게
                if (drawn < MARKS_PER_TICK && (n + e.getId()) % 20 == 0 && e.level() instanceof ServerLevel level) {
                    drawn++;
                    drawMark(level, e, v.glow(), v.core(), v.until() - now > 20 ? 0.42 : 0.32, 0.9);
                }
            }
        }
        if (!BUFF.isEmpty()) {
            for (Iterator<Map.Entry<UUID, Buff>> it = BUFF.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Buff> en = it.next();
                Buff b = en.getValue();
                if (b.until() <= now) {
                    it.remove();
                    continue;
                }
                if (n % 4 != 0) continue;
                ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
                if (sp == null || sp.isRemoved() || sp.isSpectator()) continue;
                RuneKit.bodyAura(sp, b.glow(), Opt.of("n", 4.0, "size", 0.95, "alpha", 0.6));
                if (n % 16 == 0) RuneKit.auraCue(sp, b.core(), 2);
            }
        }
    }

    private static String hud(ServerPlayer sp) {
        Buff b = BUFF.get(sp.getUUID());
        long now = Tasks.now();
        if (b == null || b.until() <= now) return "";
        List<String> out = new ArrayList<>();
        out.add("§6" + b.name() + " §f+" + Js.str(Math.round(b.add() * 100)) + "% §7" + Js.toFixed(Math.max(0, (b.until() - now) / 20.0), 1) + "초");
        return String.join(" §8| ", out);
    }

    /** 시험 · 사진첩: 표식 · 버프를 모두 지움 */
    public static void testClear() {
        VULN.clear();
        BUFF.clear();
    }
}
