package mmorpg.fabric.job;

import java.util.function.Predicate;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-16 위르드식 바닐라 파티클 겹 (사용자 "위르드는 마크 기존 이펙트도 사용하고 도트 2d도 쓰고 3d 블록도 쓰는거임" → "다음 작업 시작해"): 칠한 직업 여덟
 * 묶음 (팬텀 아처 · 아스널 킹 · 소드 마스터 · 블레이드 레인저 · 검사 · 블러드 나이트 · 암살자 · 매지션 · 아크메이지 · 네크로맨서) 의 공용 연출에 묶음 색의 바닐라
 * 파티클을 겹침 — 도트 2D 그림 (그림판) 은 그대로, 이 겹은 그 둘레의 움직임 · 부피를 맡음.
 * <ul>
 *   <li>먼지 덩이: 묶음의 세 빛 (짙은 · 가운데 · 밝은) — 크게 키운 색 덩이 (위르드의 색 덩이). 먼지는 날아가지 않으므로 자리를 틱마다 옮겨 찍어 움직임을 만듦 (껍질이 커짐 ·
 *       고리를 따라 돎 · 줄어드는 고리)</li>
 *   <li>날아가는 것: 맞음 불티 (crit · enchanted_hit · flame · 먹물) · 반짝임 (end_rod · 용암 방울 · 마녀 · 초록 반짝임) · 연기 뭉치 (poof · 큰 연기) — 파티클마다
 *       마찰 · 수명이 달라 "속도 1 이면 몇 칸 가는지" (Part.reach) 로 속도를 맞춤</li>
 *   <li>빨려 듦: portal · enchant (at + vel 에서 at 으로) 또는 안쪽으로 날림 (모으기)</li>
 * </ul>
 * 블록 · 아이템 그림 부스러기 파티클 (block · item) 은 쓰지 않음 (바닐라 그림 금지). 화면 흔들림 없음. 한 틱에 겹이 많으면 (여러 마리 맞음) fxQuota 로 거름.
 */
final class WyrdKit {
    private WyrdKit() {}

    /** 켬 (끄면 도트 2D 만 — 비교 영상) */
    static boolean ON = true;

    /** 바닐라 파티클 하나 · 속도 1 (칸/틱) 로 날렸을 때 대략 가는 거리 reach 칸 (0 = 속도를 거의 안 따름 — 떠오름 · 맴돎) · in = 빨려 드는 것 (at + vel → at) */
    record Part(ParticleOptions o, double reach, boolean in) {
        Part(ParticleOptions o, double reach) {
            this(o, reach, false);
        }

        /** dist 칸 가는 속도 */
        double v(double dist) {
            return in ? dist : reach <= 0 ? 0 : dist / reach;
        }
    }

    /**
     * 묶음 하나의 겹: 먼지 세 빛 · spark (맞음 불티 — 밖으로 튐) · glint (반짝임) · puff (연기 뭉치) · mote (묶음 고유 알갱이) · pull (모을 때 빨려 듦)
     */
    record Pal(int dark, int main, int light, Part spark, Part glint, Part puff, Part mote, Part pull) {}

    private static ColorParticleOption swirl(int rgb) {
        return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | rgb);
    }

    // 파티클 (reach — 마찰 · 수명으로 어림: crit 0.7 마찰 · 0.4 배, 불꽃 0.96 · 짧은 수명, end_rod · 불꽃놀이 0.91, poof 0.9, 큰 연기 0.96 · 긴 수명)
    static final Part CRIT = new Part(ParticleTypes.CRIT, 1.4);
    static final Part MAGIC_CRIT = new Part(ParticleTypes.ENCHANTED_HIT, 1.4);
    static final Part END_ROD = new Part(ParticleTypes.END_ROD, 11);
    static final Part FLAME = new Part(ParticleTypes.FLAME, 10);
    static final Part SMALL_FLAME = new Part(ParticleTypes.SMALL_FLAME, 10);
    static final Part POOF = new Part(ParticleTypes.POOF, 9);
    static final Part BIG_SMOKE = new Part(ParticleTypes.LARGE_SMOKE, 16);
    static final Part SMOKE = new Part(ParticleTypes.SMOKE, 14);
    static final Part INK = new Part(ParticleTypes.SQUID_INK, 10);
    static final Part SOUL = new Part(ParticleTypes.SOUL, 10);
    static final Part WITCH = new Part(ParticleTypes.WITCH, 0);
    static final Part LAVA = new Part(ParticleTypes.LAVA, 0);
    static final Part GREEN = new Part(ParticleTypes.HAPPY_VILLAGER, 0);
    static final Part WAX = new Part(ParticleTypes.WAX_ON, 0);
    static final Part UNDER = new Part(ParticleTypes.REVERSE_PORTAL, 0);
    static final Part PORTAL = new Part(ParticleTypes.PORTAL, 1, true);
    static final Part ENCHANT = new Part(ParticleTypes.ENCHANT, 1, true);

    /** 검사 — 강철 · 푸른 달빛 (흰 빛 · 맞음 crit · 흰 연기) */
    static final Pal STEEL = new Pal(0x4A5A78, 0x9DB4E0, 0xE8F2FF, CRIT, END_ROD, POOF, new Part(swirl(0xBFD4FF), 0), END_ROD);
    /** 블러드 나이트 — 피 (짙은 연기 · 핏빛 안개) */
    static final Pal BLOOD = new Pal(0x5A0A14, 0xC8182F, 0xFF6A68, CRIT, new Part(swirl(0xFF4A50), 0), BIG_SMOKE, new Part(swirl(0x8A0E1E), 0), CRIT);
    /** 아스널 킹 · 소드 마스터 — 금빛 (흰 금빛 반짝임 · 밀랍 금빛 알갱이) */
    static final Pal GOLD = new Pal(0x9A5C0C, 0xF0B028, 0xFFE28A, CRIT, END_ROD, POOF, WAX, END_ROD);
    /** 블레이드 레인저 — 단조 불 (불꽃 · 용암 방울 · 짙은 연기) */
    static final Pal FORGE = new Pal(0x9C2A08, 0xF77A18, 0xFFD27A, FLAME, LAVA, BIG_SMOKE, SMALL_FLAME, FLAME);
    /** 팬텀 아처 — 진홍 불 · 푸른 흰 투영 (흰 빛 반짝임) */
    static final Pal PHANTOM = new Pal(0x7A0D2A, 0xF2412F, 0xFF9E6E, CRIT, END_ROD, SMOKE, SMALL_FLAME, FLAME);
    /** 암살자 — 그림자 (먹물 · 마녀 보라 · 짙은 연기 · 솟는 보랏빛) */
    static final Pal SHADOW = new Pal(0x340A30, 0xA42A85, 0xCF55AC, INK, WITCH, BIG_SMOKE, UNDER, PORTAL);
    /** 매지션 · 아크메이지 — 비전 (마법 crit · 흰 빛 · 마법 글자가 빨려 듦) */
    static final Pal ARCANE = new Pal(0x2A3BA8, 0x5D7CF2, 0xB5C8FF, MAGIC_CRIT, END_ROD, POOF, ENCHANT, ENCHANT);
    /** 네크로맨서 — 혼 (녹색 혼불 맴돎 · 초록 반짝임 · 짙은 연기 · 솟는 혼) */
    static final Pal SOUL_P = new Pal(0x14662E, 0x35BC5E, 0xA0F2B4, new Part(swirl(0x35BC5E), 0), GREEN, BIG_SMOKE, SOUL, SOUL);

    /** 묶음 → 겹 (그리지 않은 직업 · 묶음 없음 = null) */
    static Pal pal(PhantomFx.Lib L) {
        if (!ON || L == null) return null;
        return switch (L.p()) {
            case "el_steel" -> STEEL;
            case "el_blood" -> BLOOD;
            case "ak" -> GOLD;
            case "br" -> FORGE;
            case "pa" -> PHANTOM;
            case "el_shadow" -> SHADOW;
            case "el_arcane" -> ARCANE;
            case "el_soul" -> SOUL_P;
            default -> null;
        };
    }

    static Pal pal(Jobs.Job j) {
        return pal(PhantomFx.lib(j));
    }

    // ------------------------------------------------------------ 보내기

    private static boolean go(String key, int max) {
        return RuneKit.fxQuota("wyrd_" + key, max);
    }

    static DustParticleOptions dust(int rgb, double scale) {
        return new DustParticleOptions(rgb, (float) Math.max(0.1, Math.min(4.0, scale)));
    }

    /** count 개를 상자 (dx, dy, dz) 안에 · 속도 speed (count 0 = 한 알을 속도 (dx, dy, dz) × speed 로) — who 가 있으면 그 사람들에게만 */
    static void p(ServerLevel l, Predicate<ServerPlayer> who, ParticleOptions o, Vec3 at, int n, double dx, double dy, double dz, double speed) {
        if (o == null || n < 0) return;
        if (who == null) {
            l.sendParticles(o, true, true, at.x, at.y, at.z, n, dx, dy, dz, speed);
            return;
        }
        for (ServerPlayer sp : l.players()) if (who.test(sp)) l.sendParticles(sp, o, true, true, at.x, at.y, at.z, n, dx, dy, dz, speed);
    }

    /** 한 알을 속도 vel (칸/틱) 로 (portal · enchant 는 at + vel 에서 at 으로) */
    static void pv(ServerLevel l, Predicate<ServerPlayer> who, ParticleOptions o, Vec3 at, Vec3 vel) {
        p(l, who, o, at, 0, vel.x, vel.y, vel.z, 1.0);
    }

    /** n 개를 상자 안에 흩뿌림 · 사방으로 대략 dist 칸 (빨려 드는 것은 dist 칸 밖에서 들어옴) */
    static void spray(ServerLevel l, Predicate<ServerPlayer> who, Part P, Vec3 at, int n, double dx, double dy, double dz, double dist) {
        if (P == null || n <= 0) return;
        // 가우스 세 축 속도의 크기는 평균 약 1.6 배
        p(l, who, P.o(), at, n, dx, dy, dz, P.v(dist) / 1.6);
    }

    static void spray(ServerLevel l, Predicate<ServerPlayer> who, Part P, Vec3 at, int n, double d, double dist) {
        spray(l, who, P, at, n, d, d, d, dist);
    }

    /** 공 껍질 위 n 자리 (피보나치 — 고르게) 에 먼지 덩이 */
    private static void shell(ServerLevel l, Predicate<ServerPlayer> who, Vec3 c, double r, int n, int rgb, double scale) {
        double ga = Math.PI * (3 - Math.sqrt(5));
        double off = Kit.rand() * Math.PI * 2;
        DustParticleOptions o = dust(rgb, scale);
        for (int i = 0; i < n; i++) {
            double y = 1 - (i + 0.5) * 2.0 / n;
            double rad = Math.sqrt(Math.max(0, 1 - y * y));
            double a = off + i * ga;
            p(l, who, o, new Vec3(c.x + Math.cos(a) * rad * r, c.y + y * r * 0.8, c.z + Math.sin(a) * rad * r), 1, 0.04, 0.04, 0.04, 0);
        }
    }

    /** 연기 뭉치 n 개가 둘레로 (조금 위로) dist 칸 밀려남 */
    private static void puffs(ServerLevel l, Predicate<ServerPlayer> who, Part P, Vec3 c, int n, double dist) {
        if (P == null || n <= 0) return;
        double off = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = off + i * Math.PI * 2 / n + (Kit.rand() - 0.5) * 0.4;
            Vec3 d = V.unit(new Vec3(Math.cos(a), (Kit.rand() - 0.25) * 0.6, Math.sin(a)));
            pv(l, who, P.o(), c, V.scale(d, P.v(dist * (0.7 + Kit.rand() * 0.5))));
        }
    }

    // ------------------------------------------------------------ 맞음 · 터짐

    /** 맞음 (PhantomFx.hit · JobFx.hit 팬텀 아처 줄기): 몸 가운데 — 불티가 튐 · 세 빛 먼지 덩이 · 연기 뭉치 · 반짝임. power 1 = 보통 */
    static void hit(ServerLevel l, PhantomFx.Lib L, LivingEntity e, double power) {
        Pal P = pal(L);
        if (P == null || e == null || !go("hit", 6)) return;
        Vec3 b = Kit.bodyPoint(e);
        double pw = Math.max(0.5, Math.min(2.0, power));
        double s = (0.75 + Kit.bodyPad(e) * 0.6) * pw;
        int k = (int) Math.round(pw * 3);
        spray(l, null, P.spark(), b, 5 + k, 0.1, 0.9 * s);
        p(l, null, dust(P.main(), 1.5), b, 3 + k / 2, 0.2 * s, 0.2 * s, 0.2 * s, 0);
        p(l, null, dust(P.dark(), 2.0), b, 2, 0.28 * s, 0.22 * s, 0.28 * s, 0);
        p(l, null, dust(P.light(), 1.0), b, 3, 0.1 * s, 0.1 * s, 0.1 * s, 0);
        spray(l, null, P.puff(), b, 2, 0.12 * s, 0.35 * s);
        spray(l, null, P.glint(), b, 2 + k / 3, 0.1 * s, 0.5 * s);
    }

    /**
     * 터짐 (PhantomFx.burst · impact · boomSmall · boom): 가운데 at · 불덩이 지름 d 칸. 작으면 (1.2 아래) 맞음 크기의 번쩍, 보통 · 크면 두 틱에 커지는 먼지 껍질 (안쪽 가운데
     * 빛 → 바깥 짙은 빛) · 밝은 속 · 둘레로 밀려나는 연기 뭉치 · 불티 · 반짝임 · 고유 알갱이 (크면 (3.4 넘게) 폭발 연기 둘)
     */
    static void burst(ServerLevel l, PhantomFx.Lib L, Vec3 at, double d, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || !go("burst", 4)) return;
        double r = Math.max(0.3, d * 0.5);
        if (d < 1.2) {
            spray(l, who, P.spark(), at, 6, 0.08, r * 1.6);
            p(l, who, dust(P.main(), 1.4), at, 4, r * 0.45, r * 0.45, r * 0.45, 0);
            p(l, who, dust(P.light(), 0.9), at, 3, r * 0.25, r * 0.25, r * 0.25, 0);
            spray(l, who, P.glint(), at, 3, r * 0.3, r);
            return;
        }
        boolean big = d >= 3.4;
        int n = big ? 18 : 11;
        shell(l, who, at, r * 0.5, n, P.main(), big ? 2.6 : 2.0);
        RuneKit.later(2, () -> shell(l, who, at, r * 0.95, n, P.dark(), big ? 3.0 : 2.4));
        p(l, who, dust(P.light(), big ? 2.0 : 1.5), at, big ? 6 : 4, r * 0.22, r * 0.22, r * 0.22, 0);
        puffs(l, who, P.puff(), at, big ? 14 : 8, r * 1.4);
        spray(l, who, P.spark(), at, big ? 16 : 9, r * 0.2, r * 1.8);
        spray(l, who, P.glint(), at, big ? 12 : 6, r * 0.35, r * 1.3);
        spray(l, who, P.mote(), at, big ? 10 : 5, r * 0.5, r * 0.6);
        if (big) p(l, who, ParticleTypes.EXPLOSION, at, 2, r * 0.3, r * 0.2, r * 0.3, 0);
    }

    // ------------------------------------------------------------ 땅

    /** 땅 충격파 (PhantomFx.shock · boom · smash): 땅 g · 지름 size — 세 번 (2틱마다) 커지는 땅 먼지 고리 · 땅을 따라 밀려나는 연기 뭉치 */
    static void shock(ServerLevel l, PhantomFx.Lib L, Vec3 g, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || size < 0.8 || !go("shock", 3)) return;
        double R = size * 0.45;
        int per = Math.max(10, Math.min(28, (int) Math.round(R * 4.5)));
        double off = Kit.rand() * Math.PI * 2;
        for (int t = 0; t < 3; t++) {
            int tt = t;
            RuneKit.later(t * 2, () -> {
                double rr = R * (0.45 + 0.275 * tt);
                for (int i = 0; i < per; i++) {
                    double a = off + (i + tt * 0.5) * Math.PI * 2 / per;
                    int c = tt == 2 ? P.dark() : i % 2 == 0 ? P.main() : P.light();
                    p(l, who, dust(c, tt == 2 ? 2.2 : 1.6), new Vec3(g.x + Math.cos(a) * rr, g.y + 0.12, g.z + Math.sin(a) * rr), 1, 0.05, 0.02, 0.05, 0);
                }
            });
        }
        if (P.puff() == null) return;
        int m = Math.max(8, Math.min(18, (int) Math.round(R * 3)));
        double off2 = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < m; i++) {
            double a = off2 + i * Math.PI * 2 / m;
            Vec3 d = new Vec3(Math.cos(a), 0.06, Math.sin(a));
            pv(l, who, P.puff().o(), new Vec3(g.x + d.x * R * 0.3, g.y + 0.15, g.z + d.z * R * 0.3), V.scale(d, P.puff().v(R * 0.8)));
        }
    }

    /** 땅 내려찍기 (PhantomFx.smash): 땅 g · 반지름 radius — 충격파 + 솟는 먼지 · 불티 · 반짝임 */
    static void smash(ServerLevel l, PhantomFx.Lib L, Vec3 g, double radius, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null) return;
        shock(l, L, g, radius * 2.3, who);
        if (!go("smash", 3)) return;
        double r = Math.max(0.5, radius);
        p(l, who, dust(P.main(), 2.2), V.up(g, 0.35), 6, r * 0.35, 0.25, r * 0.35, 0);
        spray(l, who, P.spark(), V.up(g, 0.3), 10, r * 0.3, 0.1, r * 0.3, r * 1.2);
        spray(l, who, P.glint(), V.up(g, 0.4), 6, r * 0.3, 0.2, r * 0.3, r);
    }

    // ------------------------------------------------------------ 고리 · 베기

    /**
     * 한 바퀴 베기 · 범위 고리 (JobFx.ring): 가운데 c · 반지름 r · reverse = 반시계 · hold 틱. 짧으면 (12틱 이하) 네 틱에 걸쳐 날을 따라 도는 세 빛 먼지 · 날 끝에서
     * 밖으로 (도는 쪽으로) 튀는 불티 · 반짝임, 오래 머물면 6틱마다 땅 고리 먼지 (가운데 · 짙은 빛 번갈아 — 반 칸씩 엇갈려 도는 듯) · 고유 알갱이
     */
    static void ring(ServerLevel l, PhantomFx.Lib L, Vec3 c, double r, boolean reverse, int hold, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || r < 0.4 || !go("ring", 3)) return;
        double a0 = Kit.rand() * Math.PI * 2;
        double dir = reverse ? -1 : 1;
        if (hold <= 12) {
            int per = Math.max(5, Math.min(14, (int) Math.round(r * 3.0)));
            for (int t = 0; t < 4; t++) {
                int tt = t;
                RuneKit.later(t, () -> {
                    for (int i = 0; i < per; i++) {
                        double a = a0 + dir * (tt + (i + 0.5) / per) * Math.PI / 2;
                        Vec3 q = new Vec3(c.x + Math.cos(a) * r, c.y + 0.12, c.z + Math.sin(a) * r);
                        boolean hi = i % 3 == 0;
                        p(l, who, dust(hi ? P.light() : P.main(), hi ? 1.1 : 1.7), q, 1, 0.04, 0.04, 0.04, 0);
                        if (i % 3 == 1 && P.spark() != null) {
                            Vec3 out = new Vec3(Math.cos(a), 0.2, Math.sin(a));
                            Vec3 tan = new Vec3(-Math.sin(a) * dir, 0, Math.cos(a) * dir);
                            pv(l, who, P.spark().o(), q, V.scale(V.unit(V.add(out, tan)), P.spark().v(0.9)));
                        }
                    }
                    double e = a0 + dir * (tt + 1) * Math.PI / 2;
                    spray(l, who, P.glint(), new Vec3(c.x + Math.cos(e) * r, c.y + 0.2, c.z + Math.sin(e) * r), 2, 0.1, 0.4);
                });
            }
            return;
        }
        int passes = Math.max(1, Math.min(10, hold / 6 + 1));
        int per = Math.max(8, Math.min(30, (int) Math.round(r * 4.0)));
        for (int k = 0; k < passes; k++) {
            int kk = k;
            RuneKit.later(k * 6, () -> {
                for (int i = 0; i < per; i++) {
                    double a = a0 + dir * ((i + (kk % 2) * 0.5) * Math.PI * 2 / per + kk * 0.08);
                    p(l, who, dust(i % 2 == 0 ? P.main() : P.dark(), 1.3), new Vec3(c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r), 1, 0.03, 0.02, 0.03, 0);
                }
                spray(l, who, P.mote(), V.up(c, 0.25), 3, r * 0.5, 0.15, r * 0.5, 0.6);
            });
        }
    }

    /**
     * 눈앞 큰 베기 (JobFx.slash): 판 가운데 at · 보는 쪽 look · 기울기 tilt (eyeSlash 와 같은 뜻 — 불룩한 날 = 화면 아래를 tilt 도 반시계) · 가로 반 칸 size. 세 틱에 걸쳐
     * 호를 따라 먼지 (가운데 · 밝은 빛) · 앞서가는 날 끝 불티 · 끝에 반짝임과 연기 — 몸과 같이 움직임. 주인 1인칭에는 눈앞 덩이 대신 작은 불티만
     */
    static void slash(ServerPlayer sp, PhantomFx.Lib L, Vec3 at, Vec3 look, double tilt, double size) {
        Pal P = pal(L);
        if (P == null || sp == null || !go("slash", 3)) return;
        ServerLevel l = sp.level();
        Vec3 up = JobFx.screenUp(look);
        Vec3 right = V.unit(V.cross(look, up));
        double th = Math.toRadians(tilt);
        Vec3 e1 = V.add(V.scale(right, Math.cos(th)), V.scale(up, Math.sin(th))); // 휘두르는 쪽 (tilt 0 = 왼쪽 → 오른쪽)
        Vec3 e2 = V.add(V.scale(right, Math.sin(th)), V.scale(up, -Math.cos(th))); // 불룩한 쪽 (tilt 0 = 아래)
        double hw = size * 0.85, hh = size * 0.5;
        Vec3 vel = RuneKit.bodyVel(sp);
        Predicate<ServerPlayer> others = PhantomFx.hide1p(sp), own = PhantomFx.only1p(sp);
        int per = Math.max(4, Math.min(9, (int) Math.round(size * 4)));
        for (int t = 0; t < 3; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                Vec3 base = V.add(at, vel, tt / 20.0);
                for (int i = 0; i < per; i++) {
                    double ph = -Math.PI / 2 + Math.PI * (tt * per + i + 0.5) / (3.0 * per);
                    Vec3 q = V.add(V.add(base, e1, hw * Math.sin(ph)), e2, hh * (Math.cos(ph) - 0.35));
                    p(l, others, dust(i % 2 == 0 ? P.main() : P.light(), i % 2 == 0 ? 1.5 : 1.0), q, 1, 0.03, 0.03, 0.03, 0);
                    if (i == per - 1 && P.spark() != null) {
                        Vec3 tan = V.unit(V.add(V.scale(e1, Math.cos(ph)), V.scale(e2, -Math.sin(ph) * hh / hw)));
                        for (int k = 0; k < 2; k++) {
                            Vec3 v = V.scale(V.unit(V.add(tan, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.3) * 0.6, (Kit.rand() - 0.5) * 0.6))), P.spark().v(0.8));
                            pv(l, others, P.spark().o(), q, v);
                            pv(l, own, P.spark().o(), q, V.scale(v, 0.6));
                        }
                    }
                }
                if (tt == 2) {
                    Vec3 end = V.add(V.add(base, e1, hw), e2, -hh * 0.35);
                    spray(l, others, P.glint(), end, 3, 0.1, 0.5);
                    spray(l, others, P.puff(), V.add(base, e2, hh * 0.5), 2, size * 0.3, 0.3);
                }
            });
        }
    }

    // ------------------------------------------------------------ 모으기 · 반짝임 · 문

    /** 힘 모으기 한 걸음 (JobFx.chargeLib — 2틱마다): 모으는 자리 p · 차오름 q (0 ~ 1) · 크기 size — 둘레에서 빨려 드는 묶음 알갱이 셋 · 돌며 줄어드는 먼지 고리 */
    static void chargeTick(ServerLevel l, PhantomFx.Lib L, Vec3 p, double q, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || P.pull() == null || !go("charge", 4)) return;
        double R = 1.5 - q * 0.6;
        for (int i = 0; i < 3; i++) {
            Vec3 r = V.unit(new Vec3(Kit.rand() - 0.5, (Kit.rand() - 0.4) * 0.8, Kit.rand() - 0.5));
            if (P.pull().in()) pv(l, who, P.pull().o(), p, V.scale(r, R));
            else pv(l, who, P.pull().o(), V.add(p, r, R), V.scale(r, -P.pull().v(R)));
        }
        double rr = Math.max(0.25, size * (1.0 - q * 0.7));
        double off = q * Math.PI * 3;
        for (int i = 0; i < 6; i++) {
            double a = off + i * Math.PI / 3;
            p(l, who, dust(i % 2 == 0 ? P.main() : P.light(), 0.9 + q * 0.6), new Vec3(p.x + Math.cos(a) * rr, p.y + Math.sin(a * 2) * 0.08, p.z + Math.sin(a) * rr), 1, 0.02,
                    0.02, 0.02, 0);
        }
    }

    /** 힘 모으기 끝 (번쩍): 작은 터짐 · 반짝임 */
    static void chargeEnd(ServerLevel l, PhantomFx.Lib L, Vec3 p, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null) return;
        burst(l, L, p, Math.min(1.1, size * 1.6), who);
        spray(l, who, P.glint(), p, 5, 0.15, 0.8);
    }

    /** 칼끝 반짝임 (PhantomFx.glint): 반짝임 둘 · 밝은 먼지 */
    static void glint(ServerLevel l, PhantomFx.Lib L, Vec3 at, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || !go("glint", 4)) return;
        spray(l, who, P.glint(), at, 2, 0.05, 0.25);
        p(l, who, dust(P.light(), Math.max(0.6, Math.min(1.2, size * 2))), at, 2, 0.06, 0.06, 0.06, 0);
    }

    /** 금빛 문 (PhantomFx.gate): 가운데 c · 앞 f · 테 반지름 r · life 틱 — 3틱마다 테에 먼지 · 고유 알갱이, 6틱마다 문 앞 반짝임 */
    static void gate(ServerLevel l, PhantomFx.Lib L, Vec3 c, Vec3 f, double r, int life) {
        Pal P = pal(L);
        if (P == null || !go("gate", 6)) return;
        Vec3 fu = V.len(f) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(f);
        Vec3 ax = V.unit(V.cross(fu, Math.abs(fu.y) > 0.9 ? new Vec3(1, 0, 0) : V.UP));
        Vec3 ay = V.unit(V.cross(ax, fu));
        for (int t = 0; t < life; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 q = V.add(V.add(c, ax, Math.cos(a) * r), ay, Math.sin(a) * r);
                    p(l, null, dust(i == 0 ? P.light() : P.main(), 1.0), q, 1, 0.03, 0.03, 0.03, 0);
                    if (i == 0) spray(l, null, P.mote(), q, 1, 0.05, 0.1);
                }
                if (tt % 6 == 0) spray(l, null, P.glint(), V.add(c, fu, 0.1), 1, r * 0.4, 0.3);
            });
        }
    }
}
