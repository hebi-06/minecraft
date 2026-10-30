package mmorpg.fabric.rune;

import com.mojang.math.Transformation;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 0.19.0 여섯째 한정 룬 세계수의 가호 (yggdrasil · worldTreeGrace — 사용자 요청 "버프 계열 한정 룬: 가하는 피해 증가 버프 · 받는 피해 감소"). 피해 없음.
 * <ul>
 * <li>흐름: 시전자 앞 dist 칸 · 왼쪽 side 칸 땅에 세계수의 환영 (아이템 표시 rpg:fx_worldtree — tools/worldtree_spirit.py) 이 씨앗 크기에서 돌며 자람 (GROW 틱) →
 * charge 틱에 잎 지붕이 피어 금빛 빛줄기가 쓴 자리 반지름 radius 칸 안 아군 (나 포함 — 결투 상대 빼고) 에게 닿음 → 가호 ticks 틱 →
 * 나무는 STAND 틱 서 있다가 오그라들며 잎으로 흩어짐</li>
 * <li>가호: 가하는 피해 × (1 + 가호) — 평타 · 활 · 완드 · 스킬 (RpgCombat.powerOf) · 평타에 더한 피해 · 룬 피해 (RuneKit.runeHit) 에 한 번씩,
 * 받는 피해 × (1 − 가호) — 룬 · 스킬 감소 (Guards) 와는 가장 센 것 하나 (RpgHealth.buffReduce). 가호 = Runes.graceUp / graceCut (시전자의 네 스탯의 합 ·
 * 돌파 계수 — 40렙 125포인트 0돌 20% · 3돌 26% · 6돌 32%). 여러 가호가 겹치면 센 것 (같으면 오래 가는 것) 하나</li>
 * <li>체감: 가호를 받은 사람이 맞히면 맞은 적에게 금빛 잎 · 반짝임, 가호가 막아 주면 몸 둘레에 비췻빛 고리 (자주 맞아도 잠깐에 한 번).
 * 끝에 띄우던 요약 글자 ("가호 · 더한 피해" · "가호 · 막은 피해") 는 0.20.1 에서 뺌 (사용자 "체감으로만, 거추장스럽다")</li>
 * <li>가호 동안: 상태 줄 "세계수 +20%·-20% 8.5초", 몸 둘레에 떨어지는 잎 · 1인칭 화면 아래 빛 알갱이. 죽으면 끝 (차원 이동 · 탈것으로는 끝나지 않음), 나가면 지움</li>
 * <li>6돌 (finale — 세계수 개화): 나무가 더 크게 (tree), 잎 지붕의 꽃이 피고 땅에 빛의 꽃 · 가호의 땅에 꽃잎 비 (수치는 돌파 계수 · 단계 값 그대로)</li>
 * </ul>
 * 하늘빛 · 번쩍임 (3돌부터): 하늘의 활의 새벽빛 (ULT_ALIAS worldTreeGrace → skyBow).
 */
public final class RuneGrove {
    private RuneGrove() {}

    static final String FN = "worldTreeGrace";
    private static final double[][] COLOR = RuneKit.colorOf("yggdrasil");
    /** 비췻빛 (계열 첫째 색) · 금빛 크림 (심지) · 금빛 · 민트 · 잎 초록 · 꽃잎 (연분홍 · 흰빛) */
    static final double[] JADE = COLOR[0];
    static final double[] CREAM = COLOR[1];
    static final double[] GOLD = {1, 0.8, 0.3};
    static final double[] MINT = {0.72, 1, 0.84};
    static final double[] LEAF = {0.32, 0.74, 0.36};
    static final double[] PETAL = {1, 0.8, 0.9};
    static final double[] PETAL_W = {1, 0.97, 0.95};
    static final Identifier TREE_MODEL = Identifier.fromNamespaceAndPath("rpg", "fx_worldtree");
    /** 세계수의 밝기 (밤에도 보이게 — 맥 · 열매 · 안쪽 잎은 모델에서 빛남) */
    static final Brightness LIGHT = new Brightness(13, 15);
    /**
     * 모델 치수 (크기 s 배 — 모델 48 단위 = 3s 칸): 밑동 (모델 y -16) → 모델 가운데 (y 8) 1.5s, 잎 지붕 가운데 높이 2.4s · 반지름 1.3s · 밑면 1.85s
     */
    static final double HALF = 1.5;
    static final double CANOPY_Y = 2.4;
    static final double CANOPY_R = 1.3;
    static final double CANOPY_LOW = 1.85;
    /** 자라는 틱 (charge 는 이보다 2틱 이상 — 다 자란 순간 가호) · 가호 뒤 서 있는 틱 (6돌 +20) · 오그라드는 틱 · 자랄 때 도는 각 (라디안) */
    static final int GROW = 16;
    static final int STAND = 70;
    static final int FADE = 14;
    static final float TWIST = 1.6F;
    /** 시전자 발에서 이 칸 안 (가로) 에는 꽃잎 비를 내리지 않음 (1인칭 화면 바로 앞을 덮지 않게) */
    static final double NEAR_CLEAR = 1.6;

    /** 가호 하나 (사람마다): 어느 시전의 것 · 끝 틱 · 가하는 피해 + · 받는 피해 − · 걸 때의 죽음 표 (죽으면 끝) */
    private record Grace(Cast cast, long until, double up, double cut, int deaths) {}

    /** 시전 하나: 시전자 · 가호가 끝나는 틱 · 받은 사람 수 */
    static final class Cast {
        final UUID caster;
        final long until;
        int members;

        Cast(UUID caster, long until) {
            this.caster = caster;
            this.until = until;
        }
    }

    private static final Map<UUID, Grace> GRACE = new HashMap<>();
    /** 시험 · 사진첩: 사람마다 마지막 시전 */
    private static final Map<UUID, Cast> LAST = new HashMap<>();
    /** 맞힘 · 막음 연출을 그린 틱 (잦으면 건너뜀) */
    private static final Map<UUID, Long> HIT_CUE = new HashMap<>();
    private static final Map<UUID, Long> WARD_CUE = new HashMap<>();

    static void register() {
        RuneCast.register(FN, RuneGrove::cast);
        // 훅: 앞에 다른 것이 있으면 이어서 (가하는 피해는 곱, 받는 피해 감소는 센 것, 막음 · 맞힘 연출은 둘 다에)
        java.util.function.ToDoubleFunction<ServerPlayer> dm = RpgCombat.dealtMult;
        RpgCombat.dealtMult = p -> dm.applyAsDouble(p) * dealtMult(p);
        java.util.function.ToDoubleFunction<ServerPlayer> br = RpgHealth.buffReduce;
        RpgHealth.buffReduce = p -> Math.max(br.applyAsDouble(p), cutOf(p));
        java.util.function.BiConsumer<ServerPlayer, Double> bs = RpgHealth.buffSaved;
        RpgHealth.buffSaved = (p, a) -> {
            bs.accept(p, a);
            saved(p, a);
        };
        DamageNumbers.DealtHook dh = DamageNumbers.dealtHook;
        DamageNumbers.dealtHook = (a, v, d) -> {
            dh.dealt(a, v, d);
            dealt(a, v, d);
        };
        Notify.addHudSuffix(RuneGrove::hud);
        ServerTickEvents.END_SERVER_TICK.register(RuneGrove::tick);
        RuneSystem.onForget(RuneGrove::forget);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            GRACE.clear();
            LAST.clear();
            HIT_CUE.clear();
            WARD_CUE.clear();
        });
    }

    /** 나감 · 기록 지우기: 가호 · 연출 틱 */
    private static void forget(UUID id) {
        GRACE.remove(id);
        HIT_CUE.remove(id);
        WARD_CUE.remove(id);
    }

    // ------------------------------------------------------------ 가호 (훅)

    /** 지금 걸린 가호 (끝났거나 그 뒤 죽었으면 null) */
    private static Grace active(ServerPlayer sp) {
        if (sp == null) return null;
        Grace g = GRACE.get(sp.getUUID());
        if (g == null || g.until() <= Tasks.now() || g.deaths() != RuneKit.deathToken(sp)) return null;
        return g;
    }

    /** 가하는 피해 배율 (가호가 없으면 1) */
    public static double dealtMult(ServerPlayer sp) {
        Grace g = active(sp);
        return g == null ? 1 : 1 + g.up();
    }

    /** 받는 피해 감소 (가호가 없으면 0) */
    static double cutOf(ServerPlayer sp) {
        Grace g = active(sp);
        return g == null ? 0 : g.cut();
    }

    /** 가호를 받은 사람이 준 피해가 들어감: 맞은 적에게 금빛 잎 (공격자마다 6틱에 한 번) */
    private static void dealt(ServerPlayer attacker, LivingEntity victim, double dealt) {
        Grace g = active(attacker);
        if (g == null || !(g.up() > 0) || !(dealt > 0)) return;
        long now = Tasks.now();
        Long last = HIT_CUE.get(attacker.getUUID());
        if (last != null && now - last < 6) return;
        HIT_CUE.put(attacker.getUUID(), now);
        hitCue(victim);
    }

    /** 가호가 피해를 막아 줌 (가장 센 감소일 때): 몸 둘레 비췻빛 고리 (8틱에 한 번) */
    private static void saved(ServerPlayer sp, double amount) {
        Grace g = active(sp);
        if (g == null || !(amount > 0)) return;
        long now = Tasks.now();
        Long last = WARD_CUE.get(sp.getUUID());
        if (last != null && now - last < 8) return;
        WARD_CUE.put(sp.getUUID(), now);
        wardCue(sp);
    }

    /** 상태 줄: "세계수 +20%·-20% 8.5초" */
    private static String hud(ServerPlayer sp) {
        Grace g = active(sp);
        if (g == null) return "";
        double left = Math.max(0, (g.until() - Tasks.now()) / 20.0);
        return "§a세계수 §f+" + pct(g.up()) + "%§7·§f-" + pct(g.cut()) + "% §7" + Js.toFixed(left, 1) + "초";
    }

    private static String pct(double f) {
        return Js.str(Math.round(f * 100));
    }

    /** 시험용: 이 사람의 마지막 시전 {받은 사람 수, 가호가 끝나는 틱} (없으면 null) */
    static double[] last(UUID id) {
        Cast c = LAST.get(id);
        return c == null ? null : new double[] {c.members, c.until};
    }

    /** 매 틱: 끝난 가호 (잎이 흩어지며 걷힘 · 알림) · 가호 동안의 기운 (사람마다 10틱에 한 번 — 엇갈리게) */
    private static void tick(MinecraftServer server) {
        long now = Tasks.now();
        if (!GRACE.isEmpty()) {
            for (Iterator<Map.Entry<UUID, Grace>> it = GRACE.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Grace> e = it.next();
                Grace g = e.getValue();
                ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
                if (sp == null) {
                    it.remove();
                    continue;
                }
                if (g.deaths() != RuneKit.deathToken(sp)) {
                    it.remove();
                    continue;
                }
                if (now >= g.until()) {
                    it.remove();
                    expired(sp);
                    continue;
                }
                if ((now + (e.getKey().hashCode() & 7)) % 10 == 0) aura(sp, now);
            }
        }
    }

    // ------------------------------------------------------------ 시전

    static boolean cast(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = RuneLimited.finale(sp, r);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double radius = k.num("radius");
        int ticks = (int) k.num("ticks");
        int charge = Math.max(GROW + 2, (int) k.numOr("charge", 18));
        double s = k.numOr("tree", 2.8);
        double dist = k.numOr("dist", 12);
        double side = k.numOr("side", 6);
        // 가호 세기: 시전자의 네 스탯의 합 (장비 · 버프 포함) · 이 단계의 정의 (돌파 계수가 든 값)
        double applied = Runes.runeStat(RpgStats.effective(sp), r.family()).value();
        double up = Runes.graceUp(k, applied);
        double cut = Runes.graceCut(k, applied);
        // 나무 자리: 앞 dist 칸 · 왼쪽 side 칸 (1인칭 화면 왼쪽에 나무 전체가 들고 가운데 (조준 · 적) 는 비게, 3인칭에서 몸에 가리지 않게)
        Vec3 g0 = V.add(V.add(c, f, dist), V.rightOf(f), -side);
        Vec3 base = new Vec3(g0.x, RuneTerra.groundY(level, g0.x, g0.z, c.y), g0.z);
        float rot = (float) Math.atan2(f.x, f.z);
        int standEnd = charge + STAND + (fin ? 20 : 0);
        Cast cast = new Cast(sp.getUUID(), Tasks.now() + charge + ticks);
        LAST.put(sp.getUUID(), cast);
        RuneKit.ultSkyFor(sp, level, c, FN, standEnd + 10, null);

        // ---- 세계수: 0.48.2 초월 룬 손보기 — 블록 모양 아이템 표시 (rpg:fx_worldtree) 대신 입체 세계수 (world_tree — 0.48.0 위그드라실과 같은 모델:
        // 밑동이 원점 · 높이 3.3, 줄기 · 가지의 빛나는 룬 줄 = 속빛, 잎 무리 = 빛깔). 키는 예전과 같게 (3 × 크기 칸): 줄기가 솟고 잎이 피어남 (GROW 틱) → 서서
        // 잎이 흔들림 → 끝에 오그라들며 사라짐 (FADE 틱)
        Vec3 toMe = V.flat(V.sub(c, base));
        int tree = Fx3d.spawn(level, "world_tree", base, Opt.of("dir", V.UP, "face", toMe.lengthSqr() > 1e-6 ? V.unit(toMe) : new Vec3(0, 0, 1), "scale",
                3.0 * s / 3.3, "life", (standEnd + FADE) / 20.0, "color", LEAF, "glow", GOLD, "anim", "grow", "animSpeed", 20.0 / GROW, "glowAlpha", 0.45, "shine",
                0.3, "shrink", FADE / 20.0, "fadeOut", FADE / 20.0));
        RuneKit.later(GROW + 2, () -> Fx3d.anim(level, tree, "sway", true, base));

        // ---- 쓸 때: 발밑 룬 · 가호의 땅 (영역 문양 — 반지름 radius) · 시전자 → 나무 빛 뿌리 · 밑동에서 사방으로 뿌리 · 씨앗 빛 · 소리
        SkillFx.rune(level, c, Opt.of("size", 1.5, "life", (charge + 4) / 20.0, "color", JADE, "alpha", 0.9, "rate", 110.0, "progress", 1.0));
        RuneKit.domainSigil(level, c, radius, "spirit", JADE, CREAM, Opt.of("life", (standEnd + 6) / 20.0, "spinRate", 8.0, "alpha", 0.62, "grow", 0.05, "fade", 0.25,
                "coreAlpha", 0.5));
        RuneKit.streak(level, V.up(c, 0.06), V.up(base, 0.06), JADE, 0.2, (charge + 8) / 20.0, 0.75, 0);
        for (int i = 0; i < 8; i++) {
            double a = rot + i * Math.PI / 4 + (Kit.rand() - 0.5) * 0.4;
            double len = s * (1.0 + Kit.rand() * 0.5);
            Vec3 end = new Vec3(base.x + Math.sin(a) * len, base.y, base.z + Math.cos(a) * len);
            RuneKit.streak(level, V.up(base, 0.07), V.up(end, 0.07), i % 2 == 0 ? JADE : GOLD, 0.16, (standEnd + 4) / 20.0, 0.7, i * 0.03);
        }
        SkillFx.fx(level, "rpg:arrow_shot", V.up(base, 0.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.4, "thick", 0.5, "life", 0.5, "color", JADE, "alpha", 0.85));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(base, 0.6), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.3, "thick", 0.18, "life", 0.45, "color", CREAM, "alpha", 1.0));
        SkillFx.play(level, "beacon.activate", c, 1.3, 0.7);
        SkillFx.play(level, "resonate.amethyst_block", base, 0.7, 1.0);
        SkillFx.play(level, "item.bone_meal.use", base, 0.8, 1.0);
        RuneKit.selfCue(sp, JADE, CREAM, true);
        RuneKit.later(GROW / 2, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(level, "block.chorus_flower.grow", base, 0.7, 1.0);
        });

        // ---- 자람 · 서 있음 (매 틱) → 오그라들며 흩어짐. 시전자가 쓰러지거나 차원을 옮기면 나무는 바로 흩어짐 (이미 받은 가호는 그대로)
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life)) {
                Fx3d.kill(level, tree, base, 0.5);
                fade(level, base, s);
                return false;
            }
            if (t < charge) grow(level, base, s, t, charge);
            else if (t < standEnd) stand(sp, level, base, s, rot, t - charge, fin, c, radius);
            if (t >= standEnd) {
                fade(level, base, s); // 나무는 남은 삶 (FADE 틱) 동안 오그라들며 사라짐 (shrink · fadeOut)
                return false;
            }
            return true;
        });

        // ---- 가호 (charge 틱): 잎 지붕이 피어 빛줄기가 반지름 안 아군 (나 포함) 에게 닿음
        RuneKit.later(charge, () -> {
            if (RuneKit.sameLife(sp, life)) bless(sp, level, cast, c, base, s, rot, radius, ticks, up, cut, fin);
        });
        return true;
    }

    /** 가호를 내림: 잎 지붕이 핌 (섬광 · 고리 · 금빛 잎) · 가호의 땅이 퍼짐 · 반지름 안 아군마다 가호 + 빛줄기 (닿으면 몸에 가호 연출) */
    private static void bless(ServerPlayer sp, ServerLevel level, Cast cast, Vec3 c, Vec3 base, double s, float rot, double radius, int ticks, double up, double cut,
            boolean fin) {
        Vec3 crown = V.up(base, CANOPY_Y * s);
        RuneKit.skyFlare(level, crown, 1.6 * s, GOLD, CREAM, Opt.of("life", 0.8, "grow", 0.4, "fade", 0.6));
        RuneKit.novaRing(level, V.up(base, CANOPY_LOW * s), CANOPY_R * s * 0.8, 1.2, JADE, CREAM, Opt.of("life", 0.7));
        leaves(level, crown, 16, GOLD, 3.0, 1.5, 1.4, 0.14);
        leaves(level, crown, 10, LEAF, 3.4, 1.0, 1.6, 0.16);
        RuneKit.zonePulse(level, c, JADE, radius, 0.8);
        RuneKit.ultFlashFor(sp, level, crown, FN, "flash", Opt.empty());
        SkillFx.play(level, "beacon.power", crown, 1.2, 1.0);
        SkillFx.play(level, "chime.amethyst_block", crown, 1.4, 1.2);
        SkillFx.play(level, "random.levelup", c, 1.6, 0.45);
        if (fin) {
            // 6돌 세계수 개화: 가호의 땅에 빛의 꽃 · 잎 지붕 위 꽃 여섯이 핌
            RuneKit.rfx(level, "bloomflat", V.up(c, 0.09), Opt.of("dir", V.UP, "size", radius * 0.85, "life", 1.4, "frames", 4.0, "fps", 9.0, "fade", 0.4,
                    "spin", Kit.rand() * 360));
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + 0.1;
                Vec3 b = modelAt(base, s, rot, 8 + Math.cos(a) * 11.5, 27.8, 8 + Math.sin(a) * 11.5);
                RuneKit.rfx(level, "bloom", b, Opt.of("size", 0.22 * s, "life", 1.0, "frames", 4.0, "fps", 10.0, "fade", 0.5, "grow", 0.3));
            }
            SkillFx.play(level, "block.cherry_leaves.break", crown, 1.0, 1.0);
            SkillFx.play(level, "firework.twinkle", crown, 1.0, 1.0);
        }
        long until = cast.until;
        int idx = 0;
        for (ServerPlayer m : RuneKit.playersAround(level, c, radius, sp)) {
            Grace old = active(m);
            if (old != null && (old.up() > up + 1e-9 || (Math.abs(old.up() - up) <= 1e-9 && old.until() > until))) continue; // 더 센 (같으면 더 오래 가는) 가호가 이미
            GRACE.put(m.getUUID(), new Grace(cast, until, up, cut, RuneKit.deathToken(m)));
            cast.members++;
            double a = Kit.rand() * Math.PI * 2;
            double rr = Math.sqrt(Kit.rand()) * CANOPY_R * s * 0.7;
            Vec3 from = new Vec3(crown.x + Math.cos(a) * rr, crown.y - 0.3 * s, crown.z + Math.sin(a) * rr);
            boolean self = m == sp;
            RuneKit.later(idx * 2, () -> {
                if (m.isRemoved()) return;
                int fly = RuneKit.flyStreak(level, from, V.up(m.position(), 1.1), 26, GOLD, CREAM, 0.9, 0.24);
                RuneKit.later(fly, () -> blessFx(m, up, cut, ticks, self ? null : sp));
            });
            idx++;
        }
    }

    /** 가호가 몸에 닿음: 발밑 룬 · 감아 오르는 잎 · 금빛 터짐 · 1인칭 표시 · 얼굴 앞 글자 · 알림 */
    private static void blessFx(ServerPlayer m, double up, double cut, int ticks, ServerPlayer caster) {
        if (m.isRemoved()) return;
        ServerLevel level = m.level();
        Vec3 c = m.position();
        SkillFx.rune(level, c, Opt.of("size", 1.2, "life", 0.9, "color", JADE, "alpha", 0.9, "rate", 140.0, "grow", 0.25));
        SkillFx.shock(level, c, CREAM, 0.7, 0.45);
        SkillFx.burst(level, V.up(c, 1.0), GOLD, 1.2, 0.3);
        for (int i = 0; i < 10; i++) {
            // 발밑에서 바깥으로 감아 도는 잎 (눈보다 아래 — 1인칭 화면을 덮지 않게)
            double a = i * Math.PI * 2 / 10;
            Vec3 p = new Vec3(c.x + Math.cos(a) * 1.1, c.y + 0.05 + i * 0.04, c.z + Math.sin(a) * 1.1);
            Vec3 vel = new Vec3(-Math.sin(a) * 1.2 + Math.cos(a) * 0.5, 0.7, Math.cos(a) * 1.2 + Math.sin(a) * 0.5);
            RuneKit.rfx(level, "leaf", p, Opt.of("size", 0.14, "life", 0.8, "spin", Kit.rand() * 360, "spinRate", 500.0, "cell", (double) (i % 4), "color",
                    i % 2 == 0 ? JADE : GOLD, "fade", 0.4, "vel", vel));
        }
        RuneKit.selfCue(m, JADE, CREAM, false);
        DamageNumbers.showFace(m, "§a§l세계수의 가호", 2.4, 0, -0.25);
        String who = caster != null && !caster.isRemoved() ? " §8(" + caster.getName().getString() + ")" : "";
        Notify.notify(m, "§a세계수의 가호 §f가하는 피해 +" + pct(up) + "% §7· §f받는 피해 -" + pct(cut) + "% §7" + Js.str(ticks / 20.0) + "초" + who, 60);
        SkillFx.playFor(m, "chime.amethyst_block", 1.8, 0.6);
    }

    // ------------------------------------------------------------ 나무 연출

    /** 세계수의 변환: 크기 scale · y 축 회전 rot (밑동이 엔티티 자리 = 땅에 붙게 모델 가운데를 1.5 × 크기 위로) */
    static Transformation treeAt(double scale, float rot) {
        return FxDisplays.of(new Vec3(0, HALF * scale, 0), new Quaternionf().rotationY(rot), scale);
    }

    /** 모델 좌표 (모델 단위 — 가운데 (8, 8, 8)) 의 세상 자리 (밑동 base · 크기 s · 회전 rot — 표시 엔티티와 같은 변환) */
    static Vec3 modelAt(Vec3 base, double s, float rot, double mx, double my, double mz) {
        Vector3f v = new Quaternionf().rotationY(rot).transform(new Vector3f((float) ((mx - 8) / 16 * s), (float) ((my - 8) / 16 * s), (float) ((mz - 8) / 16 * s)));
        return new Vec3(base.x + v.x, base.y + HALF * s + v.y, base.z + v.z);
    }

    /** 자라는 동안 (t = 0 ~ charge): 줄기를 감아 오르는 잎 (비취 · 금빛 나선) · 밑동에서 솟는 빛 알갱이 · 밑동 고리 · 잎 소리 */
    private static void grow(ServerLevel level, Vec3 base, double s, int t, int charge) {
        double k = Math.min(1, t / (double) charge);
        if (t % 2 == 0) {
            for (int j = 0; j < 2; j++) {
                double a = t * 0.7 + j * Math.PI;
                double rr = (0.5 + 0.4 * k) * s * 0.45;
                Vec3 p = new Vec3(base.x + Math.cos(a) * rr, base.y + 0.2 + k * s * 1.6, base.z + Math.sin(a) * rr);
                RuneKit.rfx(level, "leaf", p, Opt.of("size", 0.16, "life", 0.6, "spin", Kit.rand() * 360, "spinRate", 400.0, "cell", Math.floor(Kit.rand() * 4),
                        "color", j == 0 ? JADE : GOLD, "fade", 0.4, "vel", new Vec3(-Math.sin(a) * 1.6, 2.2, Math.cos(a) * 1.6)));
            }
            SkillFx.fx(level, "rpg:arrow_spark", V.up(base, 0.3), Opt.of("dir", V.UP, "speed", 3.0 + k * 3, "size", 0.14, "grow", 1.0, "life", 0.6, "color", MINT,
                    "alpha", 1.0));
        }
        if (t % 4 == 0) SkillFx.shock(level, base, JADE, 0.5 + k * s * 0.3, 0.4);
        if (t % 6 == 3) SkillFx.play(level, "block.azalea_leaves.break", V.up(base, k * s), 0.6 + k * 0.6, 0.6);
    }

    /** 서 있는 동안 (u = 가호 뒤 틱): 잎 지붕에서 떨어지는 잎 · 열매가 차례로 반짝임 · 둘레에서 오르는 빛 알갱이 · 밑동 고리 · (6돌) 꽃잎 비 */
    private static void stand(ServerPlayer sp, ServerLevel level, Vec3 base, double s, float rot, int u, boolean fin, Vec3 c, double radius) {
        if (u % 3 == 0) {
            for (int j = 0; j < 2; j++) {
                double a = Kit.rand() * Math.PI * 2;
                double rr = Math.sqrt(Kit.rand()) * CANOPY_R * s;
                Vec3 p = new Vec3(base.x + Math.cos(a) * rr, base.y + CANOPY_LOW * s - 0.2, base.z + Math.sin(a) * rr);
                fallingLeaf(level, p, Kit.rand() < 0.3 ? GOLD : LEAF, 0.16 + Kit.rand() * 0.06, 2.2, 0.8 + Kit.rand() * 0.5);
            }
        }
        if (u % 8 == 0) {
            int i = (u / 8) % 8;
            double a = Math.PI * 2 * i / 8 + 0.2 + Math.PI / 8;
            Vec3 fr = modelAt(base, s, rot, 8 + Math.cos(a) * 12.5, 9.3, 8 + Math.sin(a) * 12.5);
            glint(level, fr, 0.5, 0.4);
        }
        if (u % 5 == 0) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = s * (0.6 + Kit.rand() * 0.9);
            SkillFx.fx(level, "rpg:arrow_spark", new Vec3(base.x + Math.cos(a) * rr, base.y + 0.3 + Kit.rand() * s, base.z + Math.sin(a) * rr),
                    Opt.of("dir", V.UP, "speed", 0.6, "size", 0.12, "grow", 0.8, "life", 1.2, "color", Kit.rand() < 0.5 ? MINT : CREAM, "alpha", 1.0));
        }
        if (u % 20 == 0) SkillFx.shock(level, base, JADE, s * 0.45, 0.7);
        if (fin && u % 2 == 0) {
            // 6돌: 가호의 땅에 꽃잎 비 (시전자 바로 둘레 · 3인칭 카메라 (보는 쪽 뒤 4.5칸) 둘레는 비움 — 사진 검토: 카메라 앞을 지나는 꽃잎이 크게 보임)
            Vec3 f = Kit.facingOf(sp);
            double camX = sp.getX() - f.x * 4.5;
            double camZ = sp.getZ() - f.z * 4.5;
            for (int j = 0; j < 3; j++) {
                double a = Kit.rand() * Math.PI * 2;
                double rr = NEAR_CLEAR + Math.sqrt(Kit.rand()) * Math.max(0, radius - NEAR_CLEAR);
                Vec3 p = new Vec3(c.x + Math.cos(a) * rr, c.y + 6 + Kit.rand() * 3, c.z + Math.sin(a) * rr);
                if (Math.hypot(p.x - sp.getX(), p.z - sp.getZ()) < NEAR_CLEAR || Math.hypot(p.x - camX, p.z - camZ) < 2.2) continue;
                fallingLeaf(level, p, Kit.rand() < 0.5 ? PETAL : PETAL_W, 0.14, 4.0, 1.6 + Kit.rand() * 0.6);
            }
        }
    }

    /** 오그라들며 흩어짐 (FADE 틱 — 돌며 작아짐, 잎 지붕에서 잎 · 금빛이 흩날림) → 지움 */
    private static void fade(ServerLevel level, Vec3 base, double s) {
        Vec3 crown = V.up(base, CANOPY_Y * s);
        leaves(level, crown, 18, LEAF, 3.2, 1.2, 1.4, 0.16);
        leaves(level, crown, 10, GOLD, 2.6, 1.8, 1.2, 0.13);
        glint(level, crown, 1.6, 0.5);
        SkillFx.play(level, "block.azalea_leaves.break", crown, 0.8, 1.0);
        SkillFx.play(level, "firework.twinkle", crown, 1.2, 0.6);
    }

    // ------------------------------------------------------------ 몸 · 맞힘 · 막음 · 끝 연출

    /**
     * 가호 동안 (10틱마다): 몸 둘레 (1.5 ~ 1.9칸 · 눈보다 아래 — 1인칭 카메라 바로 앞에 큰 잎이 지나지 않게) 에서 떨어지는 잎 둘 (비취 · 금빛) ·
     * 20틱마다 1인칭 화면 아래 빛 알갱이 · 40틱마다 발밑 작은 고리
     */
    private static void aura(ServerPlayer sp, long now) {
        if (sp.isRemoved() || sp.isSpectator()) return;
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        for (int i = 0; i < 2; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = 1.5 + Kit.rand() * 0.4;
            Vec3 p = new Vec3(c.x + Math.cos(a) * rr, c.y + 1.0 + Kit.rand() * 0.4, c.z + Math.sin(a) * rr);
            fallingLeaf(level, p, i == 0 ? JADE : GOLD, 0.12, 1.3, 0.55);
        }
        long n = now / 10;
        if (n % 2 == 0) RuneKit.auraCue(sp, MINT, 2);
        if (n % 4 == 0) SkillFx.shock(level, c, JADE, 0.32, 0.5);
    }

    /** 가호로 더한 피해가 들어간 적: 금빛 잎 넷 · 반짝임 */
    private static void hitCue(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level) || victim.isRemoved()) return;
        Vec3 at = RuneLimited.body(victim);
        leaves(level, at, 4, GOLD, 2.2, 1.2, 0.6, 0.11);
        glint(level, at, 0.42, 0.26);
    }

    /**
     * 가호가 막아 줌: 발밑 비췻빛 고리 · 발치에서 바깥으로 흩어지는 잎 · 1인칭 화면 아래 빛 알갱이 · 맑은 소리 (사진 검토: 가슴 높이 고리는 1인칭 화면을 통째로 덮음)
     */
    private static void wardCue(ServerPlayer sp) {
        ServerLevel level = sp.level();
        Vec3 feet = sp.position();
        SkillFx.shock(level, feet, JADE, 0.55, 0.4);
        SkillFx.shock(level, feet, CREAM, 0.3, 0.3);
        for (int i = 0; i < 5; i++) {
            double a = Kit.rand() * Math.PI * 2;
            Vec3 p = new Vec3(feet.x + Math.cos(a) * 0.9, feet.y + 0.3, feet.z + Math.sin(a) * 0.9);
            RuneKit.rfx(level, "leaf", p, Opt.of("size", 0.11, "life", 0.6, "spin", Kit.rand() * 360, "spinRate", 500.0, "cell", Math.floor(Kit.rand() * 4),
                    "color", JADE, "fade", 0.4, "vel", new Vec3(Math.cos(a) * 2.0, 0.4, Math.sin(a) * 2.0)));
        }
        RuneKit.auraCue(sp, JADE, 2);
        SkillFx.play(level, "hit.amethyst_cluster", feet, 1.5, 0.5);
    }

    /** 가호가 걷힘 (끝): 잎이 흩어짐 · 알림 · 작은 소리 */
    private static void expired(ServerPlayer sp) {
        if (sp.isRemoved()) return;
        ServerLevel level = sp.level();
        leaves(level, V.up(sp.position(), 0.3), 8, JADE, 2.2, 0.5, 0.9, 0.12);
        Notify.notify(sp, "§7세계수의 가호가 걷혔습니다", 40);
        SkillFx.playFor(sp, "beacon.deactivate", 1.5, 0.35);
    }

    // ------------------------------------------------------------ 그림 도구

    /** 잎 n 장 (rpg:rfx_leaf — 모양 4가지, 색을 곱함): at 둘레에서 옆으로 spread · 위로 upV 칸/초 (아래면 음수) · life 초 · 크기 size */
    static void leaves(ServerLevel level, Vec3 at, int n, double[] color, double spread, double upV, double life, double size) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double sp = spread * (0.4 + Kit.rand() * 0.6);
            Vec3 vel = new Vec3(Math.cos(a) * sp, upV * (0.5 + Kit.rand()), Math.sin(a) * sp);
            Vec3 p = new Vec3(at.x + Math.cos(a) * 0.25, at.y + Kit.rand() * 0.3, at.z + Math.sin(a) * 0.25);
            RuneKit.rfx(level, "leaf", p, Opt.of("size", size * (0.8 + Kit.rand() * 0.5), "life", life * (0.7 + Kit.rand() * 0.5), "spin", Kit.rand() * 360,
                    "spinRate", (Kit.rand() - 0.5) * 720, "cell", Math.floor(Kit.rand() * 4), "color", color, "fade", 0.4, "vel", vel));
        }
    }

    /** 떨어지는 잎 하나 (천천히 흔들리며 fall 칸/초로 내려옴) */
    static void fallingLeaf(ServerLevel level, Vec3 p, double[] color, double size, double life, double fall) {
        double a = Kit.rand() * Math.PI * 2;
        Vec3 vel = new Vec3(Math.cos(a) * 0.5, -fall, Math.sin(a) * 0.5);
        RuneKit.rfx(level, "leaf", p, Opt.of("size", size, "life", life, "spin", Kit.rand() * 360, "spinRate", (Kit.rand() - 0.5) * 300, "cell",
                Math.floor(Kit.rand() * 4), "color", color, "fade", 0.3, "vel", vel));
    }

    /** 금빛 반짝임 (rpg:rfx_prop 의 반짝임 칸 — 빛 · 심지) */
    static void glint(ServerLevel level, Vec3 at, double size, double life) {
        RuneKit.rfx2(level, "prop", at, Opt.of("size", size, "life", life, "spin", Kit.rand() * 90, "grow", 0.5, "fade", 0.6), GOLD, CREAM, RuneKit.RFX.get("glint"), null);
    }
}
