package mmorpg.fabric.rune;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.PBridge;
import mmorpg.fabric.vfx.Vfx;
import mmorpg.fabric.vfx.VfxKit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-28 지원 룬 다섯 (사용자 "근접 및 힐러 등 같은 애들의 룬 추가가 필요할듯" → 고른 것 "회복 + 아군 버프 반반"): 피해 없음 — 예산 (등급 초당 값 × 쿨) 을 모두 회복 ·
 * 버프로 (tools/rune_set.py SUPPORT_* · support_split — 회복은 남는 예산 전부). 원거리 직업도 쓸 수 있는 지키는 룬 (중 · 원거리).
 * <ul>
 * <li>라이프 볼트 (일반): 겨눈 아군 (16칸 — 없으면 나) 에게 생명의 빛살이 곡선으로 날아가 닿은 자리에 빛꽃이 핌 — 대상 회복 · 둘레 아군은 절반. 동작 rn_fireball</li>
 * <li>워 드럼 (희귀): 머리 위 뒤에 전쟁 북이 떠올라 세 번 울림 — 울릴 때마다 땅의 불빛 충격파, 마지막 울림에 나와 둘레 아군 공격 속도 + · 이동 속도 (신속). 동작 rn_fighting_spirit</li>
 * <li>새크리드 서클 (고귀): 발밑에 금빛 성스러운 원 (자리에 남음) · 가장자리 빛기둥 여섯 — 1초마다 안의 나 · 아군 회복 (아군은 절반) · 처음에 나쁜 효과 하나 걷음. 동작
 * rn_light_barrier</li>
 * <li>발키리 송 (영웅): 하늘에서 3D 빛의 날개 · 고리 · 은빛 창을 든 발키리가 머리 위 뒤로 내려와 노래 — 음표 · 깃털, 나와 둘레 아군 가하는 피해 + · 받는 피해 −
 * (다른 감소와 겹치지 않음 — 가호와 같음). 동작 rn_archangel</li>
 * <li>에덴 블룸 (초월): 발밑에 거대한 연꽃이 피고 둘레가 꽃밭으로 — 꽃잎 폭풍 · 빛기둥 · 나비, 몸을 따라 회복 (그로브 블레싱처럼 · 아군은 절반) · 나와 아군 가하는 피해 +.
 * 6돌 낙원의 개화 (꽃비). 동작 rn_world_tree</li>
 * </ul>
 * 버프: 같은 룬의 버프는 센 것 (같으면 오래 가는 것) 하나, 다른 룬끼리는 겹침 — 가하는 피해 · 공격 속도는 곱 (세계수의 가호와도), 받는 피해 감소는 가장 센 것 하나
 * (RpgHealth.buffReduce). 죽으면 끝 · 나가면 지움. 상태 줄 "워 드럼 공속 +35% 4.5초". 화면 흔들림은 없음 (사용자 "화면 흔들림은 넣지마 멀미나").
 */
final class RuneSupport {
    private RuneSupport() {}

    /** 생명빛 (라이프 볼트) · 전쟁 불빛 (워 드럼) · 성광 (새크리드 서클) · 발키리 은빛 하늘 · 낙원 꽃빛 (에덴 블룸) — 빛 · 심지 */
    static final double[] LIFE = {0.5, 1.0, 0.62};
    static final double[] LIFE_CORE = {0.93, 1.0, 0.82};
    static final double[] WAR = {1.0, 0.42, 0.14};
    static final double[] WAR_CORE = {1.0, 0.86, 0.5};
    static final double[] HOLY = {1.0, 0.85, 0.38};
    static final double[] HOLY_CORE = {1.0, 0.98, 0.86};
    static final double[] VALK = {0.68, 0.84, 1.0};
    static final double[] VALK_CORE = {1.0, 0.96, 0.84};
    static final double[] EDEN = {1.0, 0.5, 0.76};
    static final double[] EDEN_CORE = {1.0, 0.94, 0.97};

    // ------------------------------------------------------------ 버프

    /** 버프 하나 (사람마다 · 룬마다): 끝 틱 · 가하는 피해 + · 받는 피해 − · 공격 속도 + · 신속 단계 (−1 = 없음) · 걸 때의 죽음 표 */
    record Buff(String kind, long until, double up, double cut, double haste, int speed, int deaths) {}

    private static final Map<UUID, Map<String, Buff>> BUFFS = new HashMap<>();
    private static final Map<String, String> NAMES = Map.of("wardrum", "§6워 드럼", "valkyrie", "§b발키리 송", "eden", "§d에덴 블룸");

    static void register() {
        RuneCast.register("lifeBolt", RuneSupport::lifeBolt);
        RuneCast.register("warDrum", RuneSupport::warDrum);
        RuneCast.register("sacredCircle", RuneSupport::sacredCircle);
        RuneCast.register("valkyrieSong", RuneSupport::valkyrieSong);
        RuneCast.register("edenBloom", RuneSupport::edenBloom);
        // 훅: 앞에 다른 것이 있으면 이어서 (가하는 피해 · 공격 속도는 곱, 받는 피해 감소는 센 것)
        ToDoubleFunction<ServerPlayer> dm = RpgCombat.dealtMult;
        RpgCombat.dealtMult = p -> dm.applyAsDouble(p) * dealtMult(p);
        ToDoubleFunction<ServerPlayer> br = RpgHealth.buffReduce;
        RpgHealth.buffReduce = p -> Math.max(br.applyAsDouble(p), cutOf(p));
        ToDoubleFunction<ServerPlayer> as = RpgCombat.attackSpeed;
        RpgCombat.attackSpeed = p -> as.applyAsDouble(p) * hasteMult(p);
        Notify.addHudSuffix(RuneSupport::hud);
        ServerTickEvents.END_SERVER_TICK.register(RuneSupport::tick);
        RuneSystem.onForget(BUFFS::remove);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BUFFS.clear());
    }

    /** 지금 걸린 버프인지 (끝나지 않았고 그 뒤 죽지 않음) */
    private static boolean live(Buff b, long now, int deaths) {
        return b.until() > now && b.deaths() == deaths;
    }

    /** 가하는 피해 배율 (버프가 없으면 1 — 룬마다 곱) */
    static double dealtMult(ServerPlayer sp) {
        Map<String, Buff> m = sp == null ? null : BUFFS.get(sp.getUUID());
        if (m == null || m.isEmpty()) return 1;
        long now = Tasks.now();
        int deaths = RuneKit.deathToken(sp);
        double k = 1;
        for (Buff b : m.values()) if (live(b, now, deaths)) k *= 1 + b.up();
        return k;
    }

    /** 받는 피해 감소 (없으면 0 — 가장 센 것) */
    static double cutOf(ServerPlayer sp) {
        Map<String, Buff> m = sp == null ? null : BUFFS.get(sp.getUUID());
        if (m == null || m.isEmpty()) return 0;
        long now = Tasks.now();
        int deaths = RuneKit.deathToken(sp);
        double c = 0;
        for (Buff b : m.values()) if (live(b, now, deaths)) c = Math.max(c, b.cut());
        return c;
    }

    /** 공격 속도 배율 (없으면 1 — 룬마다 곱) */
    static double hasteMult(ServerPlayer sp) {
        Map<String, Buff> m = sp == null ? null : BUFFS.get(sp.getUUID());
        if (m == null || m.isEmpty()) return 1;
        long now = Tasks.now();
        int deaths = RuneKit.deathToken(sp);
        double k = 1;
        for (Buff b : m.values()) if (live(b, now, deaths)) k *= 1 + b.haste();
        return k;
    }

    /** 시험용: 걸린 버프 (없으면 null) */
    static Buff buffOf(ServerPlayer sp, String kind) {
        Map<String, Buff> m = sp == null ? null : BUFFS.get(sp.getUUID());
        Buff b = m == null ? null : m.get(kind);
        return b != null && live(b, Tasks.now(), RuneKit.deathToken(sp)) ? b : null;
    }

    /** 버프 주기: 같은 룬의 버프는 센 것 (같으면 오래 가는 것) 하나. 신속은 바닐라 효과 (그 동안) */
    static void give(ServerPlayer pl, String kind, int ticks, double up, double cut, double haste, int speed) {
        long now = Tasks.now();
        long until = now + ticks;
        int deaths = RuneKit.deathToken(pl);
        Map<String, Buff> m = BUFFS.computeIfAbsent(pl.getUUID(), id -> new HashMap<>());
        Buff old = m.get(kind);
        if (old != null && live(old, now, deaths)) {
            double so = old.up() + old.cut() + old.haste();
            double sn = up + cut + haste;
            if (so > sn + 1e-9 || (Math.abs(so - sn) <= 1e-9 && old.until() >= until)) return;
        }
        m.put(kind, new Buff(kind, until, up, cut, haste, speed, deaths));
        if (speed >= 0) RuneKit.effect(pl, "speed", ticks, speed);
    }

    /** 상태 줄: "워 드럼 공속 +35% 4.5초 · 발키리 송 +15%·-15% 8.2초" */
    private static String hud(ServerPlayer sp) {
        Map<String, Buff> m = BUFFS.get(sp.getUUID());
        if (m == null || m.isEmpty()) return "";
        long now = Tasks.now();
        int deaths = RuneKit.deathToken(sp);
        StringBuilder sb = new StringBuilder();
        for (Buff b : m.values()) {
            if (!live(b, now, deaths)) continue;
            if (sb.length() > 0) sb.append(" §8· ");
            sb.append(NAMES.getOrDefault(b.kind(), "§f지원"));
            if (b.up() > 0) sb.append(" §f+").append(pct(b.up())).append("%");
            if (b.cut() > 0) sb.append("§7·§f-").append(pct(b.cut())).append("%");
            if (b.haste() > 0) sb.append(" §f공속 +").append(pct(b.haste())).append("%");
            sb.append(" §7").append(Js.toFixed(Math.max(0, (b.until() - now) / 20.0), 1)).append("초");
        }
        return sb.toString();
    }

    private static String pct(double f) {
        return Js.str(Math.round(f * 100));
    }

    /** 매 틱: 끝난 · 죽은 버프를 뺌, 버프 동안의 기운 (사람마다 10틱에 한 번 — 엇갈리게) */
    private static void tick(MinecraftServer server) {
        if (BUFFS.isEmpty()) return;
        long now = Tasks.now();
        for (Iterator<Map.Entry<UUID, Map<String, Buff>>> it = BUFFS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<String, Buff>> e = it.next();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null) {
                it.remove();
                continue;
            }
            int deaths = RuneKit.deathToken(sp);
            boolean cue = (now + (e.getKey().hashCode() & 7)) % 10 == 0;
            for (Iterator<Buff> bi = e.getValue().values().iterator(); bi.hasNext(); ) {
                Buff b = bi.next();
                if (b.deaths() != deaths || now >= b.until()) {
                    bi.remove();
                    continue;
                }
                if (cue) aura(sp, b);
            }
            if (e.getValue().isEmpty()) it.remove();
        }
    }

    /** 버프 동안의 기운 (그 사람의 1인칭에는 보이지 않음): 워 드럼 = 발밑 불빛 질주선 · 발키리 송 = 떨어지는 깃털 · 에덴 블룸 = 떨어지는 꽃잎 */
    private static void aura(ServerPlayer sp, Buff b) {
        if (sp.isRemoved() || sp.isSpectator()) return;
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vfx.Fx fx = Vfx.at(level, c, 24, others(sp));
        Vec3 vel = RuneKit.bodyVel(sp);
        double a = Kit.rand() * Math.PI * 2;
        Vec3 side = new Vec3(Math.cos(a), 0, Math.sin(a));
        switch (b.kind()) {
            case "wardrum" -> {
                Vec3 p = V.up(V.add(c, side, 0.45), 0.25);
                fx.sprite("el_rage_speed", p).size(0.55).play(0, 0, 4, 2, false).to(V.add(p, vel, 8), -1, 8);
                Vfx.Sprite e = fx.sprite("el_rage_ember", V.up(p, 0.4)).size(0.3).play(0, 0, 4, 3, false);
                e.to(V.up(V.add(p, vel, 10), 1.1), -1, 12);
            }
            case "valkyrie" -> {
                Vec3 p = V.up(V.add(c, side, 0.55), 2.3);
                Vfx.Sprite f = fx.sprite("el_holy_feather", p).size(0.38).play(0, (int) (Kit.rand() * 8), 8, 3, true).roll(Kit.rand() * 360, 0);
                f.to(V.add(V.up(V.add(p, side, 0.4), -1.9), vel, 20), -1, 20).fade(14, 6);
            }
            default -> {
                Vec3 p = V.up(V.add(c, side, 0.5), 2.2);
                Vfx.Sprite f = fx.sprite("el_petal_petal", p).size(0.3).frame((int) (Kit.rand() * 4)).roll(Kit.rand() * 360, 0);
                f.to(V.add(V.up(V.add(p, side, 0.5), -1.8), vel, 20), -1, 20).roll(Kit.rand() * 360, 20).fade(14, 6);
            }
        }
    }

    // ------------------------------------------------------------ 공통

    /** 그 사람의 1인칭이 아닌 모두 (둘레 사람 · 3인칭) */
    static Predicate<ServerPlayer> others(ServerPlayer sp) {
        int id = sp.getId();
        return p -> !(p.getId() == id && !PBridge.thirdPersonOf(p));
    }

    /** 그 사람의 1인칭만 */
    static Predicate<ServerPlayer> eye(ServerPlayer sp) {
        int id = sp.getId();
        return (Vfx.FirstPerson) p -> p.getId() == id && !PBridge.thirdPersonOf(p);
    }

    /** 1인칭 눈앞 자리: 시선으로 dist 칸 · 화면 아래로 down 칸 · 오른쪽으로 right 칸 */
    static Vec3 fpAt(ServerPlayer p, double dist, double down, double right) {
        Vec3 look = p.getViewVector(1F);
        Vec3 r = V.len(V.cross(look, V.UP)) < 1e-4 ? V.rightOf(Kit.facingOf(p)) : V.unit(V.cross(look, V.UP));
        Vec3 up = V.unit(V.cross(r, look));
        return V.add(V.add(V.add(p.getEyePosition(), look, dist), up, -down), r, right);
    }

    /** 몸 가운데 */
    static Vec3 chest(ServerPlayer p) {
        return V.up(p.position(), p.getBbHeight() * 0.58);
    }

    /** 두 점 사이 곡선 (가운데 c 로 휨) */
    static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double u) {
        double v = 1 - u;
        return new Vec3(v * v * a.x + 2 * v * u * c.x + u * u * b.x, v * v * a.y + 2 * v * u * c.y + u * u * b.y, v * v * a.z + 2 * v * u * c.z + u * u * b.z);
    }

    /** a 와 b 가 서로 도울 수 있는지 (나 자신 · 결투 중인 남은 아님) */
    static boolean ally(ServerPlayer a, ServerPlayer b) {
        return b != null && !b.isSpectator() && b.isAlive() && b.level() == a.level() && (a == b || RuneKit.canAssist.test(a, b));
    }

    /** 겨눈 아군 (range 칸 · 시선에서 12° 안 · 나는 아님 — 직업 스킬의 겨눈 아군과 같음), 없으면 null */
    static ServerPlayer aimAlly(ServerPlayer sp, double range) {
        RpgCombat.Aim a = RpgCombat.assist(sp, range, 12, x -> x instanceof ServerPlayer o && o != sp && ally(sp, o));
        return a != null && a.target() instanceof ServerPlayer o ? o : null;
    }

    /** 회복 + 초록 숫자 (실제로 오른 양) */
    static double heal(ServerPlayer pl, double amount) {
        double got = RpgHealth.healBy(pl, amount);
        if (got >= 0.05) DamageNumbers.show(pl, got, DamageNumbers.Style.HEAL, "", 0);
        return got;
    }

    /** 나쁜 효과 하나 걷기 (RuneKit.BAD_EFFECTS 차례의 첫 것) — 걷었으면 true */
    static boolean cleanseOne(ServerPlayer pl) {
        for (String id : RuneKit.BAD_EFFECTS) {
            Holder<MobEffect> h = RuneKit.effectOf(id);
            if (h != null && pl.hasEffect(h) && pl.removeEffect(h)) return true;
        }
        return false;
    }

    /** 2 × 2 조각 그림 (이름_rc — 칠한 큰 그림): 가운데 at · 크기 size 칸 · 고정 q (null = 카메라를 봄). 조각 넷 */
    static Vfx.Sprite[] tiled(Vfx.Fx fx, String base, Vec3 at, double size, Quaternionf q) {
        Vfx.Sprite[] out = new Vfx.Sprite[4];
        double t = size / 2;
        int i = 0;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                Vfx.Sprite s = fx.sprite(base + "_" + r + c, at);
                if (q != null) s.fixed(q);
                s.size(t).offset((c - 0.5) * t, (0.5 - r) * t);
                out[i++] = s;
            }
        }
        return out;
    }

    /** 1인칭 작은 신호 (눈앞 아래 — 화면을 가리지 않게 작게): 그림판 sheet 한 장이 칸을 넘기며 솟았다 사라짐 */
    static void fpCue(ServerPlayer p, String sheet, double size, int frames, double right) {
        if (p.isRemoved() || PBridge.thirdPersonOf(p)) return;
        Vec3 at = fpAt(p, 1.9, 0.62, right);
        Vfx.Fx fx = Vfx.at(p.level(), at, 22, eye(p));
        Vfx.Sprite s = fx.sprite(sheet, at).size(size).play(0, 0, frames, 2, false);
        s.to(V.up(at, 0.25), -1, 14).fade(12, 6);
    }

    // ------------------------------------------------------------ 라이프 볼트

    /**
     * 라이프 볼트: 겨눈 아군 (range 칸 — 없으면 나) 에게 손끝에서 생명의 빛살 (둥근 빛 · 흰 심지 · 도는 잎 둘 · 꼬리 반짝임) 이 곡선으로 flight 틱 날아감 (나면 앞 위로
     * 솟았다 가슴으로) → 닿은 자리: 대상 회복 (최대 체력 × heal × 쓴 사람의 회복 배율) · 둘레 splash 칸 아군은 share, 빛꽃 · 초록 십자 · 땅의 연꽃 · 꽃잎
     */
    static boolean lifeBolt(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        ServerPlayer aimed = aimAlly(sp, k.numOr("range", 16));
        ServerPlayer to = aimed != null ? aimed : sp;
        boolean self = to == sp;
        int flight = Math.max(2, (int) k.numOr("flight", 6));
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        double amount = k.num("heal");
        double share = k.numOr("share", 0.5);
        double splash = k.numOr("splash", 3);
        Vec3 f = Kit.facingOf(sp);
        Vec3 a = V.up(V.add(V.add(sp.position(), f, 0.6), V.rightOf(f), 0.32), 1.3);
        Vec3 b0 = chest(to);
        // 2026-11-28 나에게: 앞 위로 높이 솟았다 가슴 앞 (0.5칸) 으로 — 예전 (앞 1.8 · 위 2.8) 은 머리 둘레에 머물러 3인칭에서 날아가는 게 안 보였고, 가슴까지 오면
        // 1인칭 눈앞을 큰 빛 공이 덮었음 (CI 46114720685). 끝 40% 동안 작아짐
        Vec3 ctrl = self ? V.up(V.add(sp.position(), f, 2.4), 4.4) : V.up(V.lerp(a, b0, 0.5), 0.6 + V.len(V.sub(b0, a)) * 0.18);
        SkillFx.play(level, "block.amethyst_block.chime", a, 1.6, 1.0);
        SkillFx.play(level, "item.bone_meal.use", a, 1.2, 0.9);
        SkillFx.play(level, "entity.allay.item_thrown", a, 1.3, 0.9);
        RuneKit.selfCue(sp, LIFE, LIFE_CORE, false);
        // 손끝에 피는 빛꽃 (쏘는 순간)
        Vfx.Fx hand = Vfx.at(level, a, 18, others(sp));
        hand.sprite("el_holy_bloom", a).size(0.9).play(0, 1, 6, 1, false).fade(9, 5);
        VfxKit.sparks(hand, a, LIFE, 6, 0.9, 0);
        // 빛살 (대상 몸을 따라감)
        Vfx.Fx fx = Vfx.at(level, a, flight + 24);
        Vfx.Sprite orb = fx.sprite("glow", a).color(LIFE).size(0.85).alpha(0.95);
        Vfx.Sprite heart = fx.sprite("flare", a).color(LIFE_CORE).size(0.62);
        Vfx.Sprite[] leaves = {fx.sprite("el_gale_feather", a).size(0.42), fx.sprite("el_gale_feather", a).size(0.42)};
        fx.each(1, flight, 1, t -> {
            double u = t / (double) flight;
            Vec3 end = self ? V.add(chest(to), Kit.facingOf(to), 0.5) : chest(to);
            Vec3 p = bez(a, ctrl, end, u);
            double shrink = self && u > 0.6 ? 1 - (u - 0.6) / 0.4 * 0.65 : 1;
            orb.to(p, (0.85 + 0.2 * Math.sin(t * 1.3)) * shrink, 1);
            heart.to(p, 0.62 * shrink, 1).roll(t * 45, 1);
            for (int i = 0; i < 2; i++) {
                double ang = t * 1.5 + i * Math.PI;
                Vec3 off = new Vec3(Math.cos(ang) * 0.38, Math.sin(ang * 0.7) * 0.22, Math.sin(ang) * 0.38);
                leaves[i].to(V.add(p, off), -1, 1).frame((t + i * 4) % 8);
            }
            Vfx.Sprite tr = fx.sprite("burst2", p).color(Vfx.mix(LIFE, VfxKit.WHITE, 0.4)).size(0.32).frame(8 + (t & 1));
            tr.to(V.up(p, -0.15), -1, 5).fade(fx.age() + 1, 4);
            if ((t & 1) == 0) {
                Vfx.Sprite pe = fx.sprite("el_petal_petal", p).size(0.22).frame(t % 4).roll(t * 50.0, 0);
                pe.to(V.up(p, -0.6), -1, 10).fade(fx.age() + 5, 5);
            }
        });
        fx.on(flight + 1, () -> {
            orb.remove();
            heart.remove();
            for (Vfx.Sprite s : leaves) s.remove();
        });
        RuneKit.later(flight, () -> {
            if (!RuneKit.sameLife(sp, life) || to.isRemoved() || !to.isAlive()) return;
            lifeBloom(sp, to, amount, scale, share, splash);
        });
        return true;
    }

    /** 라이프 볼트가 닿음: 회복 · 빛꽃 · 초록 십자 · 땅의 연꽃 · 꽃잎 (대상의 1인칭에는 눈앞 아래 작은 십자) */
    private static void lifeBloom(ServerPlayer sp, ServerPlayer to, double amount, double scale, double share, double splash) {
        ServerLevel lv = to.level();
        Vec3 c = to.position();
        Vec3 ch = chest(to);
        heal(to, RpgHealth.maxOf(to) * amount * scale);
        int n = 0;
        for (ServerPlayer pl : RuneKit.playersAround(lv, c, splash, sp)) {
            if (pl == to || !pl.isAlive()) continue;
            if (heal(pl, RpgHealth.maxOf(pl) * amount * scale * share) > 0 && n++ < 6) {
                Vfx.Fx sf = Vfx.at(lv, chest(pl), 20, others(pl));
                Vfx.Sprite s = sf.sprite("el_holy_heal", V.up(chest(pl), 0.9)).size(0.55).play(0, 0, 6, 2, false);
                s.to(V.up(chest(pl), 1.5), -1, 14).fade(12, 6);
            }
        }
        Vfx.Fx fx = Vfx.at(lv, ch, 44, others(to));
        fx.sprite("el_holy_bloom", ch).size(1.7).play(0, 1, 7, 2, false).fade(16, 8);
        Vfx.Sprite cross = fx.sprite("el_holy_heal", V.up(ch, 0.9)).size(0.95).play(0, 0, 6, 2, false);
        cross.to(V.up(ch, 1.7), -1, 16).fade(14, 8);
        Vec3 g = VfxKit.ground(lv, c);
        fx.sprite("el_holy_lotus", V.up(g, 0.07)).fixed(Vfx.flat(Kit.rand() * 360)).size(2.4).play(0, 0, 8, 2, false).fade(22, 12);
        VfxKit.ring(fx, g, splash, LIFE, 1, 12);
        VfxKit.sparks(fx, ch, LIFE, 10, 1.6, 0);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Kit.rand() * 0.4;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vfx.Sprite pe = fx.sprite("el_petal_petal", ch).size(0.26).frame(i % 4).roll(Kit.rand() * 360, 0);
            pe.to(V.up(V.add(ch, d, 1.3 + Kit.rand() * 0.5), -0.2 + Kit.rand() * 0.6), -1, 12).roll(Kit.rand() * 360, 12).fade(10, 8);
        }
        RuneKit.zonePulse(lv, c, LIFE, splash, 0.5);
        // 하트 셋: 머리 위에 옆으로 벌려 (예전 한 줄로 겹쳐 붉은 덩어리로 보였음 — CI 46114720685)
        Vec3 hr = V.rightOf(Kit.facingOf(to));
        for (int i = 0; i < 3; i++) SkillFx.vanilla(lv, "minecraft:heart_particle", V.up(V.add(c, hr, (i - 1) * 0.55), 2.1 + (i == 1 ? 0.22 : 0)));
        SkillFx.play(lv, "block.amethyst_block.resonate", c, 1.5, 0.9);
        SkillFx.play(lv, "random.orb", c, 1.3, 0.7);
        SkillFx.play(lv, "block.cherry_leaves.place", c, 1.2, 1.0);
        fpCue(to, "el_holy_heal", 0.4, 6, 0);
        if (to != sp) Notify.notify(to, "§a" + sp.getName().getString() + " §7님의 §a라이프 볼트", 30);
    }

    // ------------------------------------------------------------ 워 드럼

    /**
     * 워 드럼: 머리 위 뒤 (몸을 따라옴) 에 전쟁 북이 떠오름 — 테 둘 (빛 고리 halo) · 북면 (빛) · 몸통 안 불꽃 · 양옆 북채 둘 · 둘레를 도는 불티. beats 번 (beatGap 틱마다)
     * 울림: 북채가 번갈아 내려침 (마지막은 둘 다) · 북면 번쩍 · 테가 튐 · 북면의 눕힌 불빛 충격 · 땅의 충격파 (반지름 radius) · 흙먼지 · 둥 (낮은 북) 소리.
     * 마지막 울림에 나와 둘레 radius 칸 아군 buffTicks 틱 공격 속도 + haste · 이동 속도 (신속 speedAmp) — 받은 사람마다 불빛 질주 · 불꽃 기운
     */
    static boolean warDrum(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        int beats = Math.max(1, (int) k.numOr("beats", 3));
        int gap = Math.max(2, (int) k.numOr("beatGap", 6));
        double radius = k.numOr("radius", 8);
        int ticks = (int) k.num("buffTicks");
        double haste = k.num("haste");
        int amp = (int) k.numOr("speedAmp", -1);
        int last = (beats - 1) * gap + 2;
        int span = last + 14;
        IntFunction<Vec3> drumAt = t -> V.up(V.add(sp.position(), Kit.facingOf(sp), -0.55), 2.85 + 0.06 * Math.sin(t * 0.5));
        Vfx.Fx fx = RuneProps.fx(level, sp.position(), span + 20);
        double rimS = RuneProps.scaleFor(1.55, RuneProps.HALO_D);
        Vec3 d0 = drumAt.apply(0);
        Vfx.Prop top = RuneProps.prop(fx, RuneProps.HALO, V.up(d0, 0.3), RuneProps.MID, RuneProps.along(V.UP, new Vec3(1, 0, 0)), rimS * 0.1, WAR, WAR_CORE);
        Vfx.Prop bottom = RuneProps.prop(fx, RuneProps.HALO, V.up(d0, -0.3), RuneProps.MID, RuneProps.along(V.UP, new Vec3(0, 0, 1)), rimS * 0.1, WAR, WAR_CORE);
        Vfx.Sprite head = fx.sprite("glow", V.up(d0, 0.3)).color(WAR_CORE).fixed(Vfx.flat(0)).size(0.2).alpha(0.8);
        Vfx.Sprite body = fx.sprite("glow", d0).color(WAR).size(0.2).alpha(0.45);
        // 2026-11-28 불꽃은 북 몸통 안 (테 사이) 에서 타오름 — 예전 (아래 0.1 · 크기 1.6) 은 북 밑에 다리처럼 보였음 (CI 46114720685)
        Vfx.Sprite aura = fx.sprite("el_rage_aura", d0).size(1.15).alpha(0.7).play(0, 0, 8, 2, true);
        aura.vertical();
        // 북채 둘 (북 양옆 위 — 울림마다 번갈아 내려침 · 마지막은 둘 다): 칼자루 = 북 옆 위 · 머리가 북면에 닿음
        double malletS = RuneProps.scaleFor(0.95, RuneProps.HAMMER_LEN);
        Vfx.Prop[] mallets = new Vfx.Prop[2];
        for (int i = 0; i < 2; i++) {
            int s = i == 0 ? 1 : -1;
            Vec3 f0 = Kit.facingOf(sp);
            mallets[i] = RuneProps.prop(fx, RuneProps.HAMMER, malletGrip(d0, f0, s), RuneProps.HAMMER_GRIP, RuneProps.along(malletDir(f0, s, 0), f0), malletS * 0.1,
                    WAR, WAR_CORE);
        }
        int[] beatTicks = new int[beats];
        for (int i = 0; i < beats; i++) beatTicks[i] = 2 + i * gap;
        fx.each(1, last + 7, 1, t -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 d = drumAt.apply(t);
            Vec3 f = Kit.facingOf(sp);
            double grow = Math.min(1, (t + 1) / 3.0);
            for (int i = 0; i < 2; i++) {
                int s = i == 0 ? 1 : -1;
                double hit = 0;
                for (int b = 0; b < beats; b++) {
                    boolean mine = b == beats - 1 || (b % 2 == 0) == (s > 0);
                    if (!mine) continue;
                    int dt = t - beatTicks[b];
                    if (dt >= -2 && dt <= 0) hit = Math.max(hit, (dt + 2) / 2.0);
                    else if (dt > 0 && dt <= 3) hit = Math.max(hit, 1 - dt / 3.0);
                }
                mallets[i].to(malletGrip(d, f, s), RuneProps.along(malletDir(f, s, hit), f), malletS * grow, 1);
            }
        });
        // 떠오름 (3틱) → 몸을 따라옴 · 울림마다 튐
        fx.on(1, () -> {
            Vec3 d = drumAt.apply(1);
            top.to(V.up(d, 0.3), null, rimS, 3);
            bottom.to(V.up(d, -0.3), null, rimS, 3);
            head.to(V.up(d, 0.3), 1.45, 3);
            body.to(d, 1.25, 3);
        });
        int[] beatAt = {-100};
        fx.each(4, span, 1, t -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 d = drumAt.apply(t);
            int since = t - beatAt[0];
            double pop = since >= 0 && since < 3 ? 1.18 - 0.06 * since : 1.0;
            top.to(V.up(d, 0.3), RuneProps.along(V.UP, RuneKit.turn(new Vec3(1, 0, 0), t * 9.0)), rimS * pop, 1);
            bottom.to(V.up(d, -0.3), RuneProps.along(V.UP, RuneKit.turn(new Vec3(1, 0, 0), -t * 9.0)), rimS * pop, 1);
            head.to(V.up(d, 0.3), 1.45 * pop, 1);
            body.to(d, 1.25 * pop, 1);
            aura.to(d, -1, 1);
            if (t % 3 == 0) {
                double ang = t * 0.7;
                Vec3 p = V.add(d, new Vec3(Math.cos(ang) * 0.95, -0.2, Math.sin(ang) * 0.95));
                Vfx.Sprite e = fx.sprite("el_rage_ember", p).size(0.32).play(fx.age(), 0, 4, 2, false);
                e.to(V.up(p, 0.9), -1, 10).fade(fx.age() + 6, 4);
            }
        });
        SkillFx.play(level, "item.goat_horn.sound.0", sp.position(), 1.0, 1.2);
        SkillFx.play(level, "entity.ravager.roar", sp.position(), 1.4, 0.35);
        RuneKit.selfCue(sp, WAR, WAR_CORE, false);
        for (int i = 0; i < beats; i++) {
            final int bi = i;
            final boolean fin = i == beats - 1;
            fx.on(2 + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                beatAt[0] = fx.age();
                Vec3 d = drumAt.apply(fx.age());
                Vec3 g = VfxKit.ground(lv, sp.position());
                head.alpha(1.0);
                fx.on(fx.age() + 3, () -> head.alpha(0.8));
                // 북면의 울림: 눕힌 불빛 충격 (북면 위) · 번쩍 — 예전 세운 땅 금 (el_rage_quake) 은 북 위에 나뭇가지처럼 보였음 (CI 46114720685)
                Vfx.Sprite hs = fx.sprite("el_rage_shock_s", V.up(d, 0.34)).fixed(Vfx.flat(Kit.rand() * 360)).size(fin ? 2.8 : 2.1).play(fx.age(), 0, 10, 1, false);
                hs.fade(fx.age() + 8, 3);
                Vfx.Sprite fl = fx.sprite("el_rage_flash", V.up(d, 0.42)).size(fin ? 2.2 : 1.5).play(fx.age(), 0, 8, 1, false);
                fl.fade(fx.age() + 6, 2);
                fx.sprite("el_rage_shock_s", V.up(g, 0.08)).fixed(Vfx.flat(Kit.rand() * 360)).size(fin ? radius * 2 : radius * 1.4).play(fx.age(), 0, 10, 1, false)
                        .fade(fx.age() + 8, 4);
                VfxKit.ring(fx, g, fin ? radius : radius * 0.7, WAR, 0, 10);
                VfxKit.dust(fx, g, 1.4, fin ? 6 : 3, VfxKit.DUST, 0);
                VfxKit.sparks(fx, d, WAR, fin ? 14 : 8, 1.8, 0);
                RuneKit.zonePulse(lv, g, WAR, fin ? radius : radius * 0.7, 0.45);
                SkillFx.play(lv, "block.note_block.basedrum", d, 0.5 + bi * 0.06, 2.0);
                SkillFx.play(lv, "entity.iron_golem.attack", d, 0.55, 0.9);
                SkillFx.play(lv, "mace.heavy_smash_ground", g, 1.3, 0.4);
                // 1인칭: 화면 아래 불빛 고동
                if (!PBridge.thirdPersonOf(sp)) {
                    Vec3 at = fpAt(sp, 2.2, 0.75, 0);
                    Vfx.Fx ef = Vfx.at(lv, at, 16, eye(sp));
                    ef.sprite("el_rage_shock_s", at).size(0.9).play(0, 0, 10, 1, false).fade(8, 4);
                }
                if (fin) warBuff(sp, lv, radius, ticks, haste, amp);
            });
        }
        // 북이 오그라들며 사라짐
        fx.on(last + 8, () -> {
            Vec3 d = drumAt.apply(fx.age());
            top.to(V.up(d, 0.6), null, rimS * 0.1, 5);
            bottom.to(V.up(d, 0.2), null, rimS * 0.1, 5);
            head.to(V.up(d, 0.6), 0.1, 5);
            body.to(d, 0.1, 5);
            aura.fade(fx.age(), 5);
            for (Vfx.Prop m : mallets) m.to(V.up(d, 0.4), null, malletS * 0.1, 5);
        });
        fx.on(last + 14, () -> {
            top.remove();
            bottom.remove();
            head.remove();
            body.remove();
            aura.remove();
            for (Vfx.Prop m : mallets) m.remove();
        });
        return true;
    }

    /** 워 드럼 북채의 칼자루 자리: 북 가운데 d 에서 s 쪽 (오른쪽 +1) 0.95칸 · 위 0.45 (윗테 바로 위 — 예전 0.62 · 곧추선 북채는 북 위로 장대처럼 솟아 보였음) */
    private static Vec3 malletGrip(Vec3 d, Vec3 f, int s) {
        return V.up(V.add(d, V.rightOf(f), s * 0.95), 0.45);
    }

    /** 북채 자루 방향: hit 0 = 들어 올림 (바깥으로 33° 누운 V) → 1 = 북면을 침 (안쪽 · 조금 아래 — 머리가 북면 위 0.36 에 닿음) */
    private static Vec3 malletDir(Vec3 f, int s, double hit) {
        Vec3 r = V.rightOf(f);
        Vec3 up = V.add(V.scale(r, s * 0.55), V.UP, 0.84);
        Vec3 down = V.add(V.scale(r, -s * 0.986), V.UP, -0.165);
        return V.unit(V.add(V.scale(up, 1 - hit), down, hit));
    }

    /** 워 드럼 마지막 울림: 둘레 아군 (나 포함) 에게 버프 · 받은 사람마다 불빛 질주 · 불꽃 기운 (그 사람의 1인칭에는 화면 아래 작은 질주 빛) */
    private static void warBuff(ServerPlayer sp, ServerLevel lv, double radius, int ticks, double haste, int amp) {
        int n = 0;
        for (ServerPlayer pl : RuneKit.playersAround(lv, sp.position(), radius, sp)) {
            if (!ally(sp, pl)) continue;
            give(pl, "wardrum", ticks, 0, 0, haste, amp);
            if (n++ >= 8) continue;
            Vec3 ch = chest(pl);
            Vfx.Fx fx = Vfx.at(lv, ch, 30, others(pl));
            fx.sprite("el_rage_speed", ch).size(1.5).play(0, 0, 4, 2, false).fade(7, 4);
            Vfx.Sprite au = fx.sprite("el_rage_aura", V.up(pl.position(), 0.9)).size(2.1).alpha(0.8).play(0, 0, 8, 2, true);
            au.vertical();
            au.fade(16, 8);
            VfxKit.sparks(fx, ch, WAR, 8, 1.4, 0);
            fpCue(pl, "el_rage_speed", 0.55, 4, 0);
            SkillFx.play(lv, "entity.blaze.shoot", pl.position(), 1.3, 0.5);
            if (pl != sp) Notify.notify(pl, "§6" + sp.getName().getString() + " §7님의 §6워 드럼", 30);
        }
    }

    // ------------------------------------------------------------ 새크리드 서클

    /**
     * 새크리드 서클: 발밑 (쓴 자리 — 남음) 에 금빛 성스러운 원 (큰 빛 고리 el_holy_halo 2 × 2 · 천천히 돎 · 영역 문양 · 가운데 성흔) · 가장자리 빛기둥 여섯 · 솟는 빛
     * 알갱이 — gap 틱마다 pulses 번: 원 안 (반지름 radius · 높이 ±3칸) 의 나 · 아군 회복 (heal ÷ pulses — 아군은 share), 안에서 밖으로 빛 물결 · 머리 위 초록 십자.
     * 처음 펄스에 사람마다 나쁜 효과 cleanse 개 걷음 (몸에서 검은 기운이 빠져나와 빛으로 탐)
     */
    static boolean sacredCircle(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        double radius = k.numOr("radius", 4.5);
        int pulses = Math.max(1, (int) k.numOr("pulses", 6));
        int gap = Math.max(1, (int) k.numOr("gap", 20));
        int zone = (int) k.numOr("zoneTicks", (pulses - 1) * gap);
        double share = k.numOr("share", 0.5);
        int cleanse = (int) k.numOr("cleanse", 1);
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        double per = k.num("heal") / pulses;
        Vec3 c = VfxKit.ground(level, sp.position());
        Vfx.Fx fx = Vfx.at(level, c, zone + 34);
        double yaw0 = Kit.rand() * 360;
        // 땅: 큰 빛 고리 (솟으며 커짐 → 돎) · 가운데 성흔 · 영역 문양
        Vfx.Sprite[] halo = tiled(fx, "el_holy_halo", V.up(c, 0.07), radius * 2.1, Vfx.flat(yaw0));
        for (Vfx.Sprite s : halo) s.play(0, 0, 3, 2, false);
        Vfx.Sprite stig = fx.sprite("el_holy_stigma", V.up(c, 0.09)).fixed(Vfx.flat(yaw0)).size(0.4).play(0, 0, 4, 3, true);
        stig.to(null, 1.9, 6);
        RuneKit.domainSigil(level, c, radius * 0.92, "holy", HOLY, HOLY_CORE, Opt.of("life", (zone + 12) / 20.0, "spinRate", -12.0, "alpha", 0.55, "grow", 0.06,
                "fade", 0.25, "coreAlpha", 0.45));
        fx.each(6, zone, 10, t -> {
            Quaternionf q = Vfx.flat(yaw0 + t * 2.5);
            for (Vfx.Sprite s : halo) s.face(q, 10).frame(2);
            stig.face(Vfx.flat(yaw0 - t * 4.0), 10);
        });
        // 가장자리 빛기둥 여섯 (세운 빛줄기 — 세로축만 카메라를 봄): 보는 쪽 ±30° · ±90° · ±150° — 바로 앞은 비움 (예전 아무 각이라 1인칭 조준점 위에 기둥이
        // 서서 앞 적을 가렸음, CI 46114720685)
        Vec3 fc = Kit.facingOf(sp);
        double front = Math.toDegrees(Math.atan2(fc.z, fc.x));
        Vfx.Sprite[] pillars = new Vfx.Sprite[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(front + 30 + i * 60);
            Vec3 p = new Vec3(c.x + Math.cos(a) * radius, c.y + 1.35, c.z + Math.sin(a) * radius);
            Vfx.Sprite s = fx.sprite("el_holy_beam", V.up(p, -1.2)).size(0.1).alpha(0.72);
            s.vertical();
            s.roll(90, 0).play(i, 1, 4, 3, true);
            s.to(p, 0.62, 5 + i);
            pillars[i] = s;
        }
        // 솟는 빛 알갱이 (원 안 — 가운데 1칸 안은 비움: 쓴 사람 1인칭 눈앞)
        fx.each(4, zone, 3, t -> {
            for (int i = 0; i < 2; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double rr = 1.1 + Kit.rand() * (radius - 1.2);
                Vec3 p = new Vec3(c.x + Math.cos(a) * rr, c.y + 0.15, c.z + Math.sin(a) * rr);
                Vfx.Sprite e = fx.sprite(i == 0 ? "el_holy_ember" : "el_holy_dust", p).size(i == 0 ? 0.28 : 0.42).play(fx.age(), 0, 4, 3, false);
                e.to(V.up(p, 1.6 + Kit.rand()), -1, 14).fade(fx.age() + 9, 5);
            }
        });
        SkillFx.play(level, "beacon.activate", c, 1.4, 0.9);
        SkillFx.play(level, "block.bell.resonate", c, 1.2, 0.8);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.0, 0.8);
        // 고리 없는 쓰는 신호 — 겨누는 룬이 아님 (눈앞 세운 고리는 3인칭에서 "(" 베기처럼 보였음, CI 46114720685)
        RuneKit.selfCue(sp, HOLY, HOLY_CORE, false);
        RuneBoost.bloom(level, c, HOLY, HOLY_CORE, radius * 0.7);
        for (int i = 0; i < pulses; i++) {
            final int pi = i;
            fx.on(4 + i * gap, () -> {
                ServerLevel lv = fx.level();
                VfxKit.ring(fx, c, radius, HOLY, 0, 12);
                for (Vfx.Sprite s : pillars) {
                    s.to(null, 0.8, 2);
                    fx.on(fx.age() + 3, () -> s.to(null, 0.62, 4));
                }
                SkillFx.play(lv, "block.amethyst_block.chime", c, 1.1 + pi * 0.08, 1.0);
                if (pi == 0) SkillFx.play(lv, "random.orb", c, 0.9, 0.6);
                int n = 0;
                for (ServerPlayer pl : RuneKit.playersAround(lv, c, radius, sp)) {
                    if (!ally(sp, pl) || Math.abs(pl.getY() - c.y) > 3) continue;
                    double got = heal(pl, RpgHealth.maxOf(pl) * per * scale * (pl == sp ? 1 : share));
                    boolean cleaned = pi == 0 && cleanse > 0 && cleanseOne(pl);
                    if (n++ >= 8) continue;
                    Vec3 ch = chest(pl);
                    Vfx.Fx pf = Vfx.at(lv, ch, 30, others(pl));
                    if (got > 0) {
                        Vfx.Sprite x = pf.sprite("el_holy_heal", V.up(ch, 1.05)).size(0.7).play(0, 0, 6, 2, false);
                        x.to(V.up(ch, 1.6), -1, 14).fade(12, 6);
                    }
                    if (cleaned) {
                        // 걷힌 나쁜 기운: 몸에서 검은 연기가 솟아 빛으로 탐
                        for (int j = 0; j < 4; j++) {
                            Vec3 p = V.add(ch, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.6));
                            Vfx.Sprite s = pf.sprite("el_hex_smoke", p).size(0.6).play(0, 0, 6, 2, false);
                            s.to(V.up(p, 1.2), 0.9, 10).fade(8, 4);
                        }
                        pf.on(9, () -> {
                            Vfx.Sprite fl = pf.sprite("el_holy_flash", V.up(ch, 1.2)).size(1.2).play(pf.age(), 0, 8, 1, false);
                            fl.fade(pf.age() + 6, 3);
                        });
                        SkillFx.play(lv, "block.fire.extinguish", pl.position(), 1.4, 0.4);
                        Notify.notify(pl, "§e정화 §7나쁜 효과 하나를 걷었습니다", 30);
                    }
                    if (got > 0 && pi == 0) fpCue(pl, "el_holy_heal", 0.36, 6, 0);
                }
            });
        }
        // 끝: 빛 고리가 옅어지고 마지막 빛 알갱이가 솟음
        fx.on(zone + 8, () -> {
            for (Vfx.Sprite s : halo) s.fade(fx.age(), 10);
            stig.to(null, 0.2, 8).fade(fx.age(), 8);
            for (Vfx.Sprite s : pillars) s.to(V.up(s.pos(), 1.0), 0.1, 8).fade(fx.age(), 8);
            VfxKit.sparks(fx, V.up(c, 0.6), HOLY, 12, radius * 0.5, 0);
            SkillFx.play(fx.level(), "beacon.deactivate", c, 1.5, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 발키리 송

    /** 발키리 날개의 뿌리 · 방향 (대천사 날개와 같은 셈 — ArrowFx.wingPose): 몸 가운데 v · 보는 쪽 f · s = +1 오른쪽 · −1 왼쪽, sweep 뒤로 젖힘 · flap 위로 듦 (도) */
    private static Quaternionf wingRot(Vec3 f, int s, double sweep, double flap) {
        Vec3 r = V.rightOf(f);
        double ps = Math.toRadians(sweep);
        double th = Math.toRadians(flap);
        Vec3 out0 = V.add(V.scale(r, s * Math.cos(ps)), f, -Math.sin(ps));
        Vec3 out = V.add(V.scale(out0, Math.cos(th)), V.UP, Math.sin(th));
        Vec3 upv = V.sub(V.scale(V.UP, Math.cos(th)), V.scale(out0, Math.sin(th)));
        return RuneProps.facing(out, upv);
    }

    /**
     * 발키리 송: 하늘 높이에서 발키리가 머리 위 뒤로 내려옴 (descend 틱 — 몸을 따라옴): 3D 빛의 날개 한 쌍 (뒤로 40° 펴고 천천히 날갯짓) · 머리 위 빛 고리 · 오른쪽에 세운 은빛 창 ·
     * 빛 무리 · 도는 흰 섬광 (몸) · 지나간 자리의 빛 알갱이 (쓴 사람 1인칭: 화면 위쪽 은빛 날개 · 떨어지는 깃털) → 노래 (SONG 틱): 음표가 사방으로 퍼짐 · 노래 물결 셋 (세운 빛 고리 · 땅의 충격파) · 깃털이 흩날림 · 노래 소리. 내려온 뒤 2틱에 나와 둘레
     * radius 칸 아군 buffTicks 틱 가하는 피해 + buffUp · 받는 피해 − buffCut — 받은 사람마다 등 뒤 은빛 날개 (el_steel_wings) · 발밑 고리 · 음표 터짐
     */
    static boolean valkyrieSong(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        int descend = Math.max(4, (int) k.numOr("descend", 12));
        double radius = k.numOr("radius", 8);
        int ticks = (int) k.num("buffTicks");
        double up = k.num("buffUp");
        double cut = k.num("buffCut");
        final int song = 36;
        int span = descend + song;
        IntFunction<Vec3> at = t -> {
            double u = Math.min(1, t / (double) descend);
            double e = 1 - (1 - u) * (1 - u);
            double rise = t > span ? (t - span) * 0.35 : 0;
            return V.up(V.add(sp.position(), Kit.facingOf(sp), -1.15), 3.35 + 7.5 * (1 - e) + 0.08 * Math.sin(t * 0.25) + rise);
        };
        Vfx.Fx fx = RuneProps.fx(level, sp.position(), span + 30);
        Vec3 v0 = at.apply(0);
        Vec3 f0 = Kit.facingOf(sp);
        double ws = RuneProps.scaleFor(2.7, RuneProps.ANGEL_WING_SPAN);
        Vfx.Group[] wings = new Vfx.Group[2];
        for (int i = 0; i < 2; i++) {
            int s = i == 0 ? 1 : -1;
            wings[i] = RuneProps.shape(fx, RuneProps.ANGEL_WING, V.add(v0, V.rightOf(f0), s * 0.16), wingRot(f0, s, 70, 50), ws * 0.3, VALK, VALK_CORE);
        }
        double hs = RuneProps.scaleFor(0.85, RuneProps.HALO_D);
        Vfx.Prop halo = RuneProps.prop(fx, RuneProps.HALO, V.up(v0, 0.95), RuneProps.MID, RuneProps.along(V.UP, V.rightOf(f0)), hs, HOLY, HOLY_CORE);
        double spearS = RuneProps.scaleFor(2.6, RuneProps.THUNDER_SPEAR_LEN);
        Vfx.Prop spear = RuneProps.prop(fx, RuneProps.THUNDER_SPEAR, V.add(v0, V.rightOf(f0), 0.55), RuneProps.THUNDER_SPEAR_GRIP, RuneProps.along(V.UP, f0), spearS,
                VALK_CORE, VALK);
        Vfx.Sprite glow = fx.sprite("glow", v0).color(VALK_CORE).size(1.7).alpha(0.8);
        // 2026-11-28 몸 = 도는 흰 섬광 (예전 금빛 기운 el_holy_aura 는 세운 금빛 판처럼 보였음 — CI 46114720685)
        Vfx.Sprite aura = fx.sprite("flare", v0).color(VALK_CORE).size(1.25).alpha(0.95);
        boolean[] gone = {false};
        fx.each(1, span + 12, 1, t -> {
            if (gone[0]) return;
            if (!RuneKit.sameLife(sp, life)) {
                gone[0] = true;
                fx.fadeAll(4);
                return;
            }
            Vec3 v = at.apply(t);
            Vec3 f = Kit.facingOf(sp);
            double open = Math.min(1, t / (double) descend);
            double ph = 2 * Math.PI * (t % 20) / 20.0;
            // 펼친 날개는 뒤로 40° (예전 15° — 옆에서 보면 날개 면이 거의 선으로 보였음: 40° 면 옆 · 뒤 어디서나 날개 면이 보임)
            double sweep = 70 - 30 * open + 6 * Math.cos(ph) * open;
            double flap = 50 - 32 * open - 14 * Math.cos(ph) * open;
            boolean end = t > span;
            for (int i = 0; i < 2; i++) {
                int s = i == 0 ? 1 : -1;
                double size = end ? ws * Math.max(0.1, 1 - (t - span) / 10.0) : ws * (0.3 + 0.7 * open);
                wings[i].to(V.add(v, V.rightOf(f), s * 0.16), wingRot(f, s, end ? 70 : sweep, end ? 60 : flap), size, 1);
            }
            halo.to(V.up(v, 0.95), RuneProps.along(V.UP, RuneKit.turn(V.rightOf(f), t * 6.0)), end ? hs * 0.1 : hs, 1);
            spear.to(V.add(v, V.rightOf(f), 0.55), RuneProps.along(V.UP, f), end ? spearS * 0.1 : spearS, 1);
            glow.to(v, end ? 0.2 : 1.7 + 0.15 * Math.sin(t * 0.6), 1);
            aura.to(v, end ? 0.15 : 1.25 + 0.12 * Math.sin(t * 0.9), 1).roll(t * 7.0, 1);
            if (end) aura.alpha(Math.max(0, 0.95 - (t - span) * 0.1));
            // 내려오는 동안: 지나간 자리에 빛 알갱이가 남아 위로 흩어짐 (예전 하늘의 빛줄기 판 대신)
            if (t <= descend && !end) {
                Vec3 p = V.add(v, new Vec3((Kit.rand() - 0.5) * 0.9, (Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.9));
                Vfx.Sprite tr = fx.sprite("burst2", p).color(Vfx.mix(VALK, VfxKit.WHITE, 0.45)).size(0.3 + Kit.rand() * 0.14).frame(8 + (t & 1));
                tr.to(V.up(p, 0.9), -1, 10).fade(fx.age() + 5, 5);
            }
        });
        fx.on(span + 13, () -> {
            for (Vfx.Group g : wings) g.remove();
            halo.remove();
            spear.remove();
            glow.remove();
            aura.remove();
        });
        // 내려올 때: 소리
        SkillFx.play(level, "item.trident.riptide_1", sp.position(), 1.6, 0.6);
        SkillFx.play(level, "block.beacon.power_select", sp.position(), 1.3, 0.8);
        RuneKit.selfCue(sp, VALK, VALK_CORE, false); // 고리 없음 (새크리드 서클과 같은 까닭)
        // 쓴 사람의 1인칭: 발키리는 머리 위 뒤라 안 보임 → 내려올 즈음 화면 위 양쪽 모서리에 은빛 날개가 펼쳐지고 (왼 날개 · 오른 날개를 벌려 가운데는 비움 — 예전
        // 한 쌍을 가운데에 두어 앞 적의 머리를 2초 가렸음, CI 46116414178) 위 가운데에 작은 빛, 노래 동안 눈앞에 깃털이 떨어짐
        fx.on(Math.max(1, descend - 4), () -> {
            if (!RuneKit.sameLife(sp, life) || PBridge.thirdPersonOf(sp)) return;
            Vec3 wAt = fpAt(sp, 3.2, -1.3, 0);
            Vfx.Fx wf = Vfx.at(sp.level(), wAt, 36, eye(sp));
            double tile = 1.2;
            double gap = 1.0;
            for (int rr = 0; rr < 2; rr++) {
                for (int cc = 0; cc < 2; cc++) {
                    Vfx.Sprite s = wf.sprite("el_steel_wings_" + rr + cc, wAt).size(tile).offset((cc - 0.5) * tile + (cc == 0 ? -gap : gap), (0.5 - rr) * tile);
                    s.alpha(0.82).play(0, 0, 6, 2, false);
                    s.fade(20, 10);
                }
            }
            Vec3 top = fpAt(sp, 3.2, -1.95, 0);
            Vfx.Sprite wg = wf.sprite("glow", top).color(VALK_CORE).size(0.2).alpha(0.7);
            wg.to(null, 0.75, 6).fade(20, 10);
            Vfx.Sprite wl = wf.sprite("flare", top).color(VALK_CORE).size(0.15);
            wl.to(null, 0.55, 6).roll(160, 26).fade(18, 8);
        });
        Vfx.Fx feathers = Vfx.at(level, sp.getEyePosition(), span + 30, eye(sp));
        feathers.each(descend, span - 6, 4, t -> {
            if (!RuneKit.sameLife(sp, life) || PBridge.thirdPersonOf(sp)) return;
            Vec3 p = fpAt(sp, 2.3 + Kit.rand() * 0.9, -1.25 - Kit.rand() * 0.35, (Kit.rand() - 0.5) * 2.6);
            Vfx.Sprite fe = feathers.sprite("el_holy_feather", p).size(0.3).play(feathers.age(), (int) (Kit.rand() * 8), 8, 3, true).roll(Kit.rand() * 360, 0);
            fe.to(V.add(V.up(p, -2.3), new Vec3((Kit.rand() - 0.5) * 0.8, 0, (Kit.rand() - 0.5) * 0.8)), -1, 28).fade(feathers.age() + 20, 8);
        });
        // 노래 (descend 틱부터)
        fx.on(descend, () -> {
            SkillFx.play(fx.level(), "item.goat_horn.sound.1", sp.position(), 1.25, 1.1);
            SkillFx.play(fx.level(), "entity.allay.ambient_with_item", sp.position(), 0.8, 1.0);
        });
        fx.each(descend, span - 4, 3, t -> {
            if (gone[0]) return;
            Vec3 v = at.apply(t);
            for (int i = 0; i < 2; i++) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 d = new Vec3(Math.cos(a), 0.25 + Kit.rand() * 0.35, Math.sin(a));
                Vec3 p = V.add(v, d, 0.6);
                Vfx.Sprite nt = fx.sprite("el_holy_note", p).size(0.42 + Kit.rand() * 0.18).frame((int) (Kit.rand() * 4)).roll(Kit.rand() * 30 - 15, 0);
                nt.to(V.add(p, d, 3.2 + Kit.rand() * 1.5), -1, 16).fade(fx.age() + 11, 5);
            }
            Vec3 fp = V.add(v, new Vec3((Kit.rand() - 0.5) * 2.4, -0.3, (Kit.rand() - 0.5) * 2.4));
            Vfx.Sprite fe = fx.sprite("el_holy_feather", fp).size(0.42).play(fx.age(), (int) (Kit.rand() * 8), 8, 3, true).roll(Kit.rand() * 360, 0);
            fe.to(V.up(V.add(fp, new Vec3((Kit.rand() - 0.5) * 1.5, 0, (Kit.rand() - 0.5) * 1.5)), -3.0), -1, 24).fade(fx.age() + 18, 6);
            if (t % 6 == 0) SkillFx.play(fx.level(), "block.note_block.chime", v, 0.9 + ((t / 6) % 5) * 0.12, 0.7);
        });
        for (int w = 0; w < 3; w++) {
            final int wi = w;
            fx.on(descend + w * 12, () -> {
                if (gone[0]) return;
                Vec3 v = at.apply(fx.age());
                Vec3 g = VfxKit.ground(fx.level(), sp.position());
                Vfx.Sprite ring = fx.sprite("el_holy_shock_s", v).size(1.2).play(fx.age(), 0, 10, 1, false);
                ring.to(null, 5.5 + wi, 10).fade(fx.age() + 7, 4);
                VfxKit.ring(fx, g, radius, VALK, 0, 12);
                RuneKit.zonePulse(fx.level(), g, VALK, radius, 0.5);
                SkillFx.play(fx.level(), "block.bell.resonate", v, 1.4 + wi * 0.15, 0.6);
            });
        }
        // 버프 (내려온 뒤 2틱)
        fx.on(descend + 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 v = at.apply(fx.age());
            int n = 0;
            for (ServerPlayer pl : RuneKit.playersAround(lv, sp.position(), radius, sp)) {
                if (!ally(sp, pl)) continue;
                give(pl, "valkyrie", ticks, up, cut, 0, -1);
                if (n++ >= 8) continue;
                valkyrieMark(sp, pl, lv, v);
            }
            RuneBoost.bloom(lv, v, VALK, VALK_CORE, 2.4);
        });
        return true;
    }

    /** 발키리의 가호를 받음: 발키리에서 빛 알갱이가 날아와 · 등 뒤 은빛 날개 (el_steel_wings 2 × 2 — 펼쳤다 사라짐) · 발밑 고리 · 음표 터짐 */
    private static void valkyrieMark(ServerPlayer sp, ServerPlayer pl, ServerLevel lv, Vec3 from) {
        Vec3 ch = chest(pl);
        Vec3 f = Kit.facingOf(pl);
        Vfx.Fx fx = Vfx.at(lv, ch, 40, others(pl));
        for (int i = 0; i < 5; i++) {
            Vfx.Sprite s = fx.sprite("burst2", from).color(Vfx.mix(VALK, VfxKit.WHITE, 0.4)).size(0.34).frame(8 + (i & 1));
            s.to(V.lerp(from, ch, 0.5 + i * 0.1), -1, 3 + i);
            fx.on(4 + i, () -> s.to(ch, -1, 3).fade(fx.age() + 2, 3));
        }
        Vec3 back = V.up(V.add(pl.position(), f, -0.35), 1.5);
        Vfx.Sprite[] w = tiled(fx, "el_steel_wings", back, 2.1, null);
        for (Vfx.Sprite s : w) {
            s.alpha(0.9).play(6, 0, 6, 2, false);
            s.fade(22, 10);
        }
        fx.on(6, () -> VfxKit.ring(fx, VfxKit.ground(lv, pl.position()), 1.4, VALK, 0, 10));
        fx.on(7, () -> {
            for (int i = 0; i < 4; i++) {
                double a = Math.PI * 2 * i / 4 + Kit.rand();
                Vec3 d = new Vec3(Math.cos(a), 0.6, Math.sin(a));
                Vfx.Sprite nt = fx.sprite("el_holy_note", V.up(ch, 0.4)).size(0.4).frame(i % 4);
                nt.to(V.add(V.up(ch, 0.4), d, 1.4), -1, 12).fade(fx.age() + 8, 4);
            }
        });
        fpCue(pl, "el_holy_note", 0.38, 4, -0.25);
        fpCue(pl, "el_holy_feather", 0.34, 8, 0.25);
        SkillFx.play(lv, "entity.allay.item_given", pl.position(), 1.2, 0.8);
        if (pl != sp) Notify.notify(pl, "§b" + sp.getName().getString() + " §7님의 §b발키리 송", 30);
    }

    // ------------------------------------------------------------ 에덴 블룸

    /**
     * 에덴 블룸: 0 ~ charge 틱 발밑에 거대한 연꽃 (el_petal_lotus — 눕힘) 이 피고 머리 위로 큰 꽃 (el_petal_bloom) · 영역 문양 (spirit) · 하프 소리가 오름 → charge 틱:
     * 둘레 radius 칸이 꽃밭으로 (고리마다 분홍 꽃이 차례로 솟음 — 세로축만 카메라를 봄) · 빛기둥 · 하늘 섬광 · 하늘빛 (ULT — 새벽빛), 나와 아군 buffTicks 틱 가하는 피해
     * + buffUp (받은 사람마다 머리 위 꽃 고리) → charge 부터 gap 틱마다 pulses 번 회복 (몸을 따라감 — 그로브 블레싱처럼: 나 heal ÷ pulses · 아군 share) · 꽃 터짐 · 나비.
     * 꽃잎 폭풍이 몸 둘레를 감아 오름. 6돌 낙원의 개화: 꽃밭에 빛의 꽃비
     */
    static boolean edenBloom(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = RuneLimited.finale(sp, r);
        double radius = k.numOr("radius", 10);
        int charge = Math.max(4, (int) k.numOr("charge", 14));
        int pulses = Math.max(1, (int) k.numOr("pulses", 5));
        int gap = Math.max(1, (int) k.numOr("gap", 15));
        int ticks = (int) k.num("buffTicks");
        double up = k.num("buffUp");
        double share = k.numOr("share", 0.5);
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        double per = k.num("heal") / pulses;
        Vec3 c = VfxKit.ground(level, sp.position());
        int heals = charge + (pulses - 1) * gap;
        int span = heals + 26;
        Vfx.Fx fx = Vfx.at(level, c, span + 30);
        RuneKit.ultSkyFor(sp, level, c, "edenBloom", span + 10, null);
        double yaw0 = Kit.rand() * 360;
        // ---- 피어남 (0 ~ charge): 땅의 연꽃 · 영역 문양 · 머리 위 큰 꽃 · 하프
        Vfx.Sprite lotus = fx.sprite("el_petal_lotus", V.up(c, 0.08)).fixed(Vfx.flat(yaw0)).size(0.6).play(0, 0, 7, Math.max(1, charge / 7), false);
        lotus.to(null, fin ? 7.5 : 6.0, charge);
        RuneKit.domainSigil(level, c, radius * 0.55, "spirit", EDEN, EDEN_CORE, Opt.of("life", (span + 6) / 20.0, "spinRate", 8.0, "alpha", 0.5, "grow", 0.08,
                "fade", 0.25, "coreAlpha", 0.4));
        Vfx.Fx top = Vfx.at(level, c, span + 20, others(sp));
        Vec3 crown = V.up(sp.position(), 3.0);
        Vfx.Sprite bloom = top.sprite("el_petal_bloom", crown).size(0.4).play(0, 0, 7, Math.max(1, charge / 7), false);
        bloom.to(null, 2.6, charge);
        SkillFx.play(level, "block.chorus_flower.grow", c, 1.0, 1.0);
        SkillFx.play(level, "block.cherry_leaves.place", c, 1.1, 1.0);
        RuneKit.selfCue(sp, EDEN, EDEN_CORE, false); // 고리 없음 (새크리드 서클과 같은 까닭)
        for (int i = 0; i < 5; i++) {
            final int hi = i;
            fx.on(i * Math.max(1, charge / 5), () -> SkillFx.play(fx.level(), "block.note_block.harp", c, 0.8 + hi * 0.2, 0.9));
        }
        // ---- 꽃잎 폭풍 (몸 둘레를 감아 오름 — 쓴 사람의 1인칭 눈앞 0.9칸 안은 Vfx 가 거름)
        fx.each(2, heals + 10, 2, t -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 b = sp.position();
            for (int i = 0; i < 2; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double rr = 1.6 + Kit.rand() * 1.8;
                Vec3 p = new Vec3(b.x + Math.cos(a) * rr, b.y + 0.2, b.z + Math.sin(a) * rr);
                Vec3 q = new Vec3(b.x + Math.cos(a + 1.6) * (rr + 0.6), b.y + 2.6 + Kit.rand(), b.z + Math.sin(a + 1.6) * (rr + 0.6));
                Vfx.Sprite pe = fx.sprite("el_petal_petal", p).size(0.3).frame((int) (Kit.rand() * 4)).roll(Kit.rand() * 360, 0);
                pe.to(q, -1, 16).roll(Kit.rand() * 360, 16).fade(fx.age() + 11, 5);
            }
        });
        // ---- 꽃밭 (charge 틱부터 고리마다)
        fx.on(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = fx.level();
            bloomField(fx, c, radius, heals + 18 - charge, yaw0);
            RuneKit.godRays(sp, lv, c, radius * 0.75, 6, EDEN, EDEN_CORE, Opt.of("h", 12.0, "life", 1.6, "alpha", 0.75, "skipNear", true));
            RuneKit.skyFlare(lv, V.up(crown, 1.2), 3.4, EDEN, EDEN_CORE, Opt.of("life", 0.8, "grow", 0.5));
            RuneKit.ultFlashFor(sp, lv, crown, "edenBloom", "flash", Opt.empty());
            RuneBoost.bloom(lv, c, EDEN, EDEN_CORE, Math.max(4.0, radius * 0.6));
            VfxKit.ring(fx, c, radius, EDEN, 0, 14);
            SkillFx.play(lv, "beacon.power", c, 1.2, 1.0);
            SkillFx.play(lv, "block.amethyst_block.resonate", c, 1.3, 1.0);
            SkillFx.play(lv, "random.levelup", c, 1.5, 0.5);
            int n = 0;
            for (ServerPlayer pl : RuneKit.playersAround(lv, sp.position(), radius, sp)) {
                if (!ally(sp, pl)) continue;
                give(pl, "eden", ticks, up, 0, 0, -1);
                if (n++ >= 8) continue;
                Vec3 h = V.up(pl.position(), pl.getBbHeight() + 0.35);
                Vfx.Fx pf = Vfx.at(lv, h, 40, others(pl));
                Vfx.Sprite[] ring = tiled(pf, "el_petal_halo", h, 1.0, Vfx.flat(Kit.rand() * 360));
                for (Vfx.Sprite s : ring) s.play(0, 0, 8, 2, false).fade(26, 10);
                Vfx.Sprite au = pf.sprite("el_petal_aura", V.up(pl.position(), 0.9)).size(2.0).alpha(0.7).play(0, 0, 8, 2, true);
                au.vertical();
                au.fade(14, 8);
                fpCue(pl, "el_petal_blossom", 0.4, 4, 0);
                if (pl != sp) Notify.notify(pl, "§d" + sp.getName().getString() + " §7님의 §d에덴 블룸", 30);
            }
            if (fin) petalRain(fx, c, radius, heals - charge + 10);
        });
        // ---- 회복 (몸을 따라감)
        for (int i = 0; i < pulses; i++) {
            final int pi = i;
            fx.on(charge + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 b = sp.position();
                SkillFx.play(lv, "block.amethyst_block.chime", b, 1.0 + pi * 0.1, 0.9);
                int n = 0;
                for (ServerPlayer pl : RuneKit.playersAround(lv, b, radius, sp)) {
                    if (!ally(sp, pl)) continue;
                    double got = heal(pl, RpgHealth.maxOf(pl) * per * scale * (pl == sp ? 1 : share));
                    if (!(got > 0) || n++ >= 8) continue;
                    Vec3 ch = chest(pl);
                    Vfx.Fx pf = Vfx.at(lv, ch, 26, others(pl));
                    pf.sprite("el_petal_bloom", ch).size(1.3).play(0, 2, 6, 2, false).fade(12, 6);
                    Vfx.Sprite x = pf.sprite("el_holy_heal", V.up(ch, 1.0)).size(0.7).play(0, 0, 6, 2, false);
                    x.to(V.up(ch, 1.6), -1, 14).fade(12, 6);
                    Vec3 bp = V.add(ch, new Vec3((Kit.rand() - 0.5) * 1.2, 0, (Kit.rand() - 0.5) * 1.2));
                    Vfx.Sprite bf = pf.sprite("el_petal_fly", bp).size(0.45).play(0, 0, 6, 2, true);
                    bf.to(V.up(V.add(bp, new Vec3((Kit.rand() - 0.5) * 2, 0, (Kit.rand() - 0.5) * 2)), 2.2), -1, 20).fade(16, 6);
                }
            });
        }
        // ---- 끝: 연꽃 · 큰 꽃이 오그라들며 꽃잎으로 흩어짐
        fx.on(heals + 14, () -> {
            lotus.to(null, 0.4, 10).fade(fx.age() + 2, 8);
            bloom.to(null, 0.3, 10);
            bloom.fade(top.age() + 2, 8);
            VfxKit.sparks(fx, V.up(sp.position(), 1.2), EDEN, 12, 2.2, 0);
            SkillFx.play(fx.level(), "block.cherry_leaves.break", c, 1.0, 1.0);
        });
        return true;
    }

    /** 꽃밭: 고리 (반지름 2.5 · 5 · 7.5 · 10 — radius 까지) 마다 분홍 꽃이 차례로 솟음 (세로축만 카메라를 봄 · 살랑임) → life 틱 뒤 오그라듦 */
    private static void bloomField(Vfx.Fx fx, Vec3 c, double radius, int life, double yaw0) {
        int ring = 0;
        for (double rr = 2.5; rr <= radius + 0.01; rr += 2.5, ring++) {
            int n = (int) Math.round(rr * 1.8);
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(yaw0 + ring * 17 + 360.0 * i / n) + (Kit.rand() - 0.5) * 0.2;
                double r1 = rr + (Kit.rand() - 0.5) * 0.8;
                Vec3 g = VfxKit.ground(fx.level(), new Vec3(c.x + Math.cos(a) * r1, c.y + 1, c.z + Math.sin(a) * r1));
                Vec3 p = V.up(g, 0.32);
                int at = fx.age() + ring * 3 + (i % 3);
                double size = 0.55 + Kit.rand() * 0.3;
                fx.on(at, () -> {
                    Vfx.Sprite s = fx.sprite("el_petal_blossom", V.up(p, -0.25)).size(0.05).frame((int) (Kit.rand() * 4));
                    s.vertical();
                    s.to(p, size, 4);
                    s.roll((Kit.rand() - 0.5) * 20, 0);
                    s.until(at + life + 8);
                    fx.on(at + life, () -> s.to(V.up(p, -0.2), 0.05, 7));
                });
            }
        }
    }

    /** 6돌 낙원의 개화: 꽃밭 위로 빛의 꽃잎 · 꽃이 비처럼 내림 */
    private static void petalRain(Vfx.Fx fx, Vec3 c, double radius, int span) {
        fx.each(fx.age() + 2, fx.age() + span, 2, t -> {
            for (int i = 0; i < 3; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double rr = 2 + Kit.rand() * (radius - 2);
                Vec3 p = new Vec3(c.x + Math.cos(a) * rr, c.y + 6 + Kit.rand() * 2, c.z + Math.sin(a) * rr);
                boolean fl = i == 0;
                Vfx.Sprite s = fx.sprite(fl ? "el_petal_blossom" : "el_petal_petal", p).size(fl ? 0.45 : 0.3).frame((int) (Kit.rand() * 4)).roll(Kit.rand() * 360, 0);
                s.to(V.up(V.add(p, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5)), -5.6), -1, 26).roll(Kit.rand() * 360, 26).fade(fx.age() + 20, 6);
            }
        });
    }
}
