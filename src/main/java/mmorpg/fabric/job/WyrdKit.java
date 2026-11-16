package mmorpg.fabric.job;

import java.util.function.Predicate;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
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
 * 파티클을 겹침 — 도트 2D 그림 (그림판) 은 그대로, 이 겹은 그 둘레의 움직임 (튀는 불티 · 반짝임 · 펼쳐지는 고리 · 빨려 듦) 을 맡음.
 * <p>
 * 2판 (CI 43827713503 영상 50 개를 보고): 1판의 아무렇게나 뿌린 먼지 덩이 구름 · 짙은 빛 큰 덩이 (먼지 그림은 크면 얼룩진 네모로 보여 흙처럼 탁함 — 엑스칼리버 ·
 * 콘스텔레이션) · 큰 연기 · 용암 방울 (검은 부스러기가 하늘로 — 포지 브레이커 · 그레이브야드) · 먹물 · 폭발 연기 (칠한 폭발 가운데를 회색 원판이 덮음 — 팬텀 버스트) 를
 * 뺌. 남긴 것: 모양이 있는 먼지 (펼쳐지는 고리 · 날을 따라 도는 호 · 줄어드는 고리 — 가운데 · 밝은 빛, 크기 1.6 아래) · 튀는 불티 · 흰 반짝임 · 밝은 묶음의 흰 연기
 * 뭉치 (큰 터짐만) · 빨려 드는 알갱이.
 * <p>
 * 3판 (CI 43830855673): 해질녘 영상 (팬텀 아처) 에서 crit · poof · 먼지가 둘레 빛을 받아 검은 남색 X · 보랏빛 덩이로 보였음 → 불티 · 반짝임은 스스로 빛나는
 * 것 (end_rod · 불꽃놀이 · 토템 · 불꽃 · 밀랍 · 차원문) 으로, 연기 뭉치는 땅을 따라서만, 핏빛 · 녹색 소용돌이 (하늘로 솟아 나선 무늬) 는 뺌. 먼지는 모양 (고리 · 호) 에만.
 * <p>
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
     * 묶음 하나의 겹: 먼지 두 빛 (가운데 · 밝은 — 짙은 빛은 크게 쓰면 탁해서 뺌) · spark (맞음 불티 — 밖으로 튐) · glint (반짝임) · puff (연기 뭉치 — 밝은 묶음만, 없으면
     * null) · mote (묶음 고유 알갱이) · pull (모을 때 빨려 듦)
     */
    record Pal(int main, int light, Part spark, Part glint, Part puff, Part mote, Part pull) {}

    // 파티클 (reach — 마찰 · 수명으로 어림: crit 0.7 마찰 · 0.4 배, 불꽃 0.96 · 짧은 수명, end_rod · 불꽃놀이 0.91, 토템 0.6, 보랏빛 솟음 마찰 없이 수명 60틱).
    // 3판: 밤 · 해질녘에도 빛나는 것 (end_rod · 불꽃놀이 · 토템 · 불꽃 · 밀랍 · 차원문 — 스스로 빛남) 위주. crit · poof · 먼지는 둘레 빛을 받아 해질녘 영상 (팬텀 아처) 에서
    // 검은 남색 X · 보랏빛 덩이로 보였음
    static final Part CRIT = new Part(ParticleTypes.CRIT, 1.4);
    static final Part END_ROD = new Part(ParticleTypes.END_ROD, 11);
    static final Part SPARKLE = new Part(ParticleTypes.FIREWORK, 11);
    /** 번쩍이는 짧은 불똥 (2 ~ 3틱 · 스스로 빛남) — 맞음마다 (end_rod 는 3초 머물러 1인칭 돌진에서 눈앞 흰 네모가 됐음) */
    static final Part ZAP = new Part(ParticleTypes.ELECTRIC_SPARK, 1.8);
    static final Part TOTEM = new Part(ParticleTypes.TOTEM_OF_UNDYING, 2.5);
    static final Part FLAME = new Part(ParticleTypes.FLAME, 10);
    static final Part SMALL_FLAME = new Part(ParticleTypes.SMALL_FLAME, 10);
    static final Part POOF = new Part(ParticleTypes.POOF, 9);
    static final Part SOUL = new Part(ParticleTypes.SOUL, 10);
    static final Part WAX = new Part(ParticleTypes.WAX_ON, 0);
    static final Part UNDER = new Part(ParticleTypes.REVERSE_PORTAL, 30);
    static final Part PORTAL = new Part(ParticleTypes.PORTAL, 1, true);
    static final Part ENCHANT = new Part(ParticleTypes.ENCHANT, 1, true);

    /** 검사 — 강철 · 푸른 달빛 (흰 불꽃놀이 불티 · 흰 빛 · 땅 흰 연기) */
    static final Pal STEEL = new Pal(0x9DB4E0, 0xE8F2FF, SPARKLE, END_ROD, POOF, null, END_ROD);
    /** 블러드 나이트 — 피 (crit · 연기 · 반짝임 없음 — 핏빛 소용돌이는 하늘로 솟아 나선 무늬로 흩어졌음) */
    static final Pal BLOOD = new Pal(0xC8182F, 0xFF6A68, CRIT, null, null, null, CRIT);
    /** 아스널 킹 · 소드 마스터 — 금빛 (흰 불꽃놀이 불티 · 밀랍 금빛 반짝임 · 흰 빛 알갱이 · 땅 흰 연기) */
    static final Pal GOLD = new Pal(0xF0B028, 0xFFE28A, SPARKLE, WAX, POOF, END_ROD, END_ROD);
    /** 블레이드 레인저 — 단조 불 (불꽃 · 밀랍 불빛 반짝임 · 작은 불꽃 · 땅 흰 연기) */
    static final Pal FORGE = new Pal(0xF77A18, 0xFFD27A, FLAME, WAX, POOF, SMALL_FLAME, FLAME);
    /** 팬텀 아처 — 진홍 불 · 푸른 흰 투영 (불꽃 · 흰 빛 · 작은 불꽃 · 땅 흰 연기) */
    static final Pal PHANTOM = new Pal(0xF2412F, 0xFF9E6E, FLAME, END_ROD, POOF, SMALL_FLAME, FLAME);
    /** 암살자 — 그림자 (빨려 드는 보랏빛 — 맞은 자리로 그림자가 모임 · 흰 빛 · 솟는 보랏빛) */
    static final Pal SHADOW = new Pal(0xA42A85, 0xCF55AC, PORTAL, END_ROD, null, UNDER, PORTAL);
    /** 매지션 · 아크메이지 — 비전 (흰 불꽃놀이 별빛 · 흰 빛 · 마법 글자가 빨려 듦) */
    static final Pal ARCANE = new Pal(0x5D7CF2, 0xB5C8FF, SPARKLE, END_ROD, null, ENCHANT, ENCHANT);
    /** 네크로맨서 — 혼 (토템 초록 불티 · 솟는 혼 · 연기 없음) */
    static final Pal SOUL_P = new Pal(0x35BC5E, 0xA0F2B4, TOTEM, null, null, SOUL, TOTEM);

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

    // ------------------------------------------------------------ 보내기

    private static boolean go(String key, int max) {
        return RuneKit.fxQuota("wyrd_" + key, max);
    }

    /**
     * 묶음 함수 (PhantomFx hit · burst · impact · boomSmall · boom · smash · glint · JobFx 모으기 끝) 가 안에서 부르는 작은 그림 (flash · embers · dust) 에는 겹을
     * 다시 얹지 않음 — 스킬이 직접 부를 때만 (팬텀 아처 줄기 · 소드 마스터 · 블러드 나이트 등의 섬광 · 불씨 · 흙먼지). 서버 틱 한 줄기에서만 불리므로 셈 하나로 충분
     */
    private static int depth;

    static void enter() {
        depth++;
    }

    static void leave() {
        if (depth > 0) depth--;
    }

    static boolean direct() {
        return depth == 0;
    }

    /** 먼지 (크기 1.6 까지 — 더 크면 먼지 그림이 얼룩진 네모로 보임) */
    static DustParticleOptions dust(int rgb, double scale) {
        return new DustParticleOptions(rgb, (float) Math.max(0.1, Math.min(1.6, scale)));
    }

    /**
     * 1인칭 눈 바로 앞에는 보내지 않음 (칸): 먼지 · 연기 하나가 눈에서 1칸 남짓이면 화면에서 수백 픽셀 덩이 (PBridge.DUST_NEAR_EYE 와 같은 까닭 — 1인칭 눈앞 섬광 · 모으는
     * 빛에 겹이 붙을 때). 2.2칸 — 1인칭 영상 (CI 43830855673 트리니티 엣지) 에서 1.4칸은 칼 닿는 거리의 맞음 반짝임이 눈앞 흰 네모로 보였음. 상자로 흩뿌리면 상자 크기만큼
     * 더 멀리. 3인칭은 카메라가 몸 뒤라 그대로
     */
    static final double NEAR_EYE = 2.2;

    /** count 개를 상자 (dx, dy, dz) 안에 · 속도 speed (count 0 = 한 알을 속도 (dx, dy, dz) × speed 로) — who 가 있으면 그 사람들에게만 */
    static void p(ServerLevel l, Predicate<ServerPlayer> who, ParticleOptions o, Vec3 at, int n, double dx, double dy, double dz, double speed) {
        if (o == null || n < 0) return;
        double near = NEAR_EYE + (n > 0 ? Math.max(dx, Math.max(dy, dz)) * 1.5 : 0);
        for (ServerPlayer sp : l.players()) {
            if (who != null && !who.test(sp)) continue;
            if (sp.getEyePosition().distanceToSqr(at) < near * near && !mmorpg.fabric.vfx.PBridge.thirdPersonOf(sp)) continue;
            l.sendParticles(sp, o, true, true, at.x, at.y, at.z, n, dx, dy, dz, speed);
        }
    }

    /** 한 알을 속도 vel (칸/틱) 로 (portal · enchant 는 at + vel 에서 at 으로) */
    static void pv(ServerLevel l, Predicate<ServerPlayer> who, ParticleOptions o, Vec3 at, Vec3 vel) {
        p(l, who, o, at, 0, vel.x, vel.y, vel.z, 1.0);
    }

    /** n 개를 상자 안에 흩뿌림 · 사방으로 대략 dist 칸 (빨려 드는 것은 dist 칸 밖에서 들어옴 · 속도를 안 따르는 것은 dist 의 0.4 배 상자에 퍼뜨림) */
    static void spray(ServerLevel l, Predicate<ServerPlayer> who, Part P, Vec3 at, int n, double dx, double dy, double dz, double dist) {
        if (P == null || n <= 0) return;
        if (P.reach() <= 0 && !P.in()) {
            double b = dist * 0.4;
            p(l, who, P.o(), at, n, Math.max(dx, b), Math.max(dy, b), Math.max(dz, b), 0.01);
            return;
        }
        // 가우스 세 축 속도의 크기는 평균 약 1.6 배
        p(l, who, P.o(), at, n, dx, dy, dz, P.v(dist) / 1.6);
    }

    static void spray(ServerLevel l, Predicate<ServerPlayer> who, Part P, Vec3 at, int n, double d, double dist) {
        spray(l, who, P, at, n, d, d, d, dist);
    }

    /** 수평 먼지 고리 (가운데 c · 반지름 r · n 자리 · 반 칸 엇갈림 shift) */
    private static void ring(ServerLevel l, Predicate<ServerPlayer> who, Vec3 c, double r, int n, double off, int rgb, double scale) {
        DustParticleOptions o = dust(rgb, scale);
        for (int i = 0; i < n; i++) {
            double a = off + i * Math.PI * 2 / n;
            p(l, who, o, new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r), 1, 0.02, 0.02, 0.02, 0);
        }
    }

    // ------------------------------------------------------------ 맞음 · 터짐

    /** 맞음 (PhantomFx.hit · JobFx.hit 팬텀 아처 줄기): 몸 가운데 — 불티가 튐 · 번쩍이는 짧은 불똥 · 밝은 먼지 둘. power 1 = 보통 */
    static void hit(ServerLevel l, PhantomFx.Lib L, LivingEntity e, double power) {
        Pal P = pal(L);
        if (P == null || e == null || !go("hit", 4)) return;
        Vec3 b = Kit.bodyPoint(e);
        double pw = Math.max(0.5, Math.min(2.0, power));
        double s = (0.75 + Kit.bodyPad(e) * 0.6) * pw;
        int k = (int) Math.round(pw * 3);
        spray(l, null, P.spark(), b, 3 + k, 0.1, 0.9 * s);
        spray(l, null, ZAP, b, 2 + k / 2, 0.15 * s, 0.7 * s);
        p(l, null, dust(P.light(), 0.9), b, 2, 0.12 * s, 0.12 * s, 0.12 * s, 0);
    }

    /**
     * 터짐 (PhantomFx.burst · impact · boomSmall · boom): 가운데 at · 불덩이 지름 d 칸. 작으면 (1.2 아래) 불티 · 반짝임만, 보통 · 크면 두 틱에 펼쳐지는 수평 고리 (가운데 빛 →
     * 밝은 빛) · 불티 · 반짝임 · 고유 알갱이 · (크면 · 밝은 묶음만) 흰 연기 뭉치
     */
    static void burst(ServerLevel l, PhantomFx.Lib L, Vec3 at, double d, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || !go("burst", 4)) return;
        burst(l, P, at, d, who);
    }

    /** 섬광 (스킬이 직접 부른 PhantomFx.flash): 그림 크기 size 칸 → 지름 size × 0.6 의 터짐 (터짐과 따로 셈) */
    static void flash(ServerLevel l, PhantomFx.Lib L, Vec3 at, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || !go("flash", 4)) return;
        burst(l, P, at, size * 0.6, who);
    }

    private static void burst(ServerLevel l, Pal P, Vec3 at, double d, Predicate<ServerPlayer> who) {
        double r = Math.max(0.3, d * 0.5);
        if (d < 1.2) {
            spray(l, who, P.spark(), at, 5, 0.08, r * 1.4);
            spray(l, who, P.glint(), at, 2, r * 0.3, r);
            p(l, who, dust(P.light(), 0.9), at, 2, r * 0.2, r * 0.2, r * 0.2, 0);
            return;
        }
        boolean big = d >= 3.4;
        int n = big ? 20 : 12;
        double off = Kit.rand() * Math.PI * 2;
        ring(l, who, at, r * 0.55, n, off, P.main(), big ? 1.6 : 1.3);
        RuneKit.later(2, () -> ring(l, who, at, r * 0.95, n, off + Math.PI / n, P.light(), big ? 1.3 : 1.1));
        spray(l, who, P.spark(), at, big ? 12 : 7, r * 0.2, r * 1.2);
        spray(l, who, P.glint(), at, big ? 10 : 5, r * 0.35, r * 1.1);
        spray(l, who, P.mote(), at, big ? 6 : 3, r * 0.4, r * 0.5);
    }

    /** 불씨 (스킬이 직접 부른 PhantomFx.embers): 그림 n 개 → 불티 · 반짝임 (대략 dist 칸) */
    static void embers(ServerLevel l, PhantomFx.Lib L, Vec3 c, int n, double dist) {
        Pal P = pal(L);
        if (P == null || n <= 0 || !go("embers", 6)) return;
        spray(l, null, P.spark(), c, Math.max(2, n / 2), 0.15, dist);
        spray(l, null, P.glint(), c, Math.max(1, n / 3), 0.2, dist * 0.8);
    }

    // ------------------------------------------------------------ 땅

    /** 흙먼지 (스킬이 직접 부른 PhantomFx.dust — 땅에 꽂힘 · 내려앉음): 땅 g · 그림 크기 size — 땅 고리 하나 · (밝은 묶음) 땅을 따라 밀려나는 흰 연기 뭉치 */
    static void groundPuff(ServerLevel l, PhantomFx.Lib L, Vec3 g, double size) {
        Pal P = pal(L);
        if (P == null || !go("ground", 6)) return;
        double r = Math.max(0.4, size * 0.5);
        ring(l, null, V.up(g, 0.12), r * 0.8, 10, Kit.rand() * Math.PI * 2, P.main(), 1.2);
        if (P.puff() == null) return;
        int m = 5 + (int) Math.round(size);
        double off = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < m; i++) {
            double a = off + i * Math.PI * 2 / m;
            Vec3 d = new Vec3(Math.cos(a), 0.08, Math.sin(a));
            pv(l, null, P.puff().o(), new Vec3(g.x + d.x * r * 0.3, g.y + 0.12, g.z + d.z * r * 0.3), V.scale(d, P.puff().v(r)));
        }
    }

    /** 땅 충격파 (PhantomFx.shock · boom · boomSmall · smash): 땅 g · 지름 size — 세 번 (2틱마다) 커지는 땅 먼지 고리 · (밝은 묶음) 땅을 따라 밀려나는 흰 연기 */
    static void shock(ServerLevel l, PhantomFx.Lib L, Vec3 g, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || size < 0.8 || !go("shock", 3)) return;
        double R = size * 0.45;
        int per = Math.max(10, Math.min(28, (int) Math.round(R * 4.5)));
        double off = Kit.rand() * Math.PI * 2;
        for (int t = 0; t < 3; t++) {
            int tt = t;
            RuneKit.later(t * 2, () -> ring(l, who, V.up(g, 0.12), R * (0.45 + 0.275 * tt), per, off + tt * Math.PI / per, tt == 0 ? P.light() : P.main(), 1.4));
        }
        if (P.puff() == null) return;
        int m = Math.max(6, Math.min(12, (int) Math.round(R * 2)));
        double off2 = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < m; i++) {
            double a = off2 + i * Math.PI * 2 / m;
            Vec3 d = new Vec3(Math.cos(a), 0.06, Math.sin(a));
            pv(l, who, P.puff().o(), new Vec3(g.x + d.x * R * 0.3, g.y + 0.15, g.z + d.z * R * 0.3), V.scale(d, P.puff().v(R * 0.7)));
        }
    }

    /** 땅 내려찍기 (PhantomFx.smash): 땅 g · 반지름 radius — 충격파 + 솟는 불티 · 반짝임 */
    static void smash(ServerLevel l, PhantomFx.Lib L, Vec3 g, double radius, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null) return;
        shock(l, L, g, radius * 2.3, who);
        if (!go("smash", 3)) return;
        double r = Math.max(0.5, radius);
        spray(l, who, P.spark(), V.up(g, 0.3), 10, r * 0.3, 0.1, r * 0.3, r * 1.1);
        spray(l, who, P.glint(), V.up(g, 0.4), 6, r * 0.3, 0.2, r * 0.3, r);
    }

    // ------------------------------------------------------------ 고리 · 베기

    /**
     * 한 바퀴 베기 · 범위 고리 (JobFx.ring): 가운데 c · 반지름 r · reverse = 반시계 · hold 틱. 짧으면 (12틱 이하) 네 틱에 걸쳐 날을 따라 도는 먼지 (가운데 · 밝은 빛) · 날 끝에서
     * 밖으로 (도는 쪽으로) 튀는 불티 · 반짝임, 오래 머물면 6틱마다 땅 고리 먼지 (가운데 · 밝은 빛 번갈아 — 반 칸씩 엇갈려 도는 듯) · 고유 알갱이
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
                        p(l, who, dust(hi ? P.light() : P.main(), hi ? 1.0 : 1.4), q, 1, 0.03, 0.03, 0.03, 0);
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
                    p(l, who, dust(i % 2 == 0 ? P.main() : P.light(), 1.1), new Vec3(c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r), 1, 0.02, 0.02, 0.02, 0);
                }
                spray(l, who, P.mote(), V.up(c, 0.25), 2, r * 0.5, 0.15, r * 0.5, 0.6);
            });
        }
    }

    /**
     * 눈앞 큰 베기 (JobFx.slash): 판 가운데 at · 보는 쪽 look · 기울기 tilt (eyeSlash 와 같은 뜻 — 불룩한 날 = 화면 아래를 tilt 도 반시계) · 가로 반 칸 size. 세 틱에 걸쳐
     * 호를 따라 먼지 (가운데 · 밝은 빛) · 앞서가는 날 끝 불티 · 끝에 반짝임 — 몸과 같이 움직임. 주인 1인칭에는 눈앞 먼지 대신 작은 불티만
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
                    p(l, others, dust(i % 2 == 0 ? P.main() : P.light(), i % 2 == 0 ? 1.3 : 0.9), q, 1, 0.02, 0.02, 0.02, 0);
                    if (i == per - 1 && P.spark() != null) {
                        Vec3 tan = V.unit(V.add(V.scale(e1, Math.cos(ph)), V.scale(e2, -Math.sin(ph) * hh / hw)));
                        for (int k = 0; k < 2; k++) {
                            Vec3 v = V.scale(V.unit(V.add(tan, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.3) * 0.6, (Kit.rand() - 0.5) * 0.6))), P.spark().v(0.8));
                            pv(l, others, P.spark().o(), q, v);
                            pv(l, own, P.spark().o(), q, V.scale(v, 0.6));
                        }
                    }
                }
                if (tt == 2) spray(l, others, P.glint(), V.add(V.add(base, e1, hw), e2, -hh * 0.35), 3, 0.1, 0.5);
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
            p(l, who, dust(i % 2 == 0 ? P.main() : P.light(), 0.9 + q * 0.5), new Vec3(p.x + Math.cos(a) * rr, p.y + Math.sin(a * 2) * 0.08, p.z + Math.sin(a) * rr), 1, 0.02,
                    0.02, 0.02, 0);
        }
    }

    /** 힘 모으기 끝 (번쩍): 작은 터짐 · 반짝임 */
    static void chargeEnd(ServerLevel l, PhantomFx.Lib L, Vec3 p, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null) return;
        burst(l, L, p, Math.min(1.1, size * 1.6), who);
        spray(l, who, P.glint(), p, 4, 0.15, 0.8);
    }

    /** 칼끝 반짝임 (PhantomFx.glint): 반짝임 둘 · 밝은 먼지 */
    static void glint(ServerLevel l, PhantomFx.Lib L, Vec3 at, double size, Predicate<ServerPlayer> who) {
        Pal P = pal(L);
        if (P == null || !go("glint", 4)) return;
        spray(l, who, P.glint(), at, 2, 0.05, 0.25);
        p(l, who, dust(P.light(), Math.max(0.6, Math.min(1.1, size * 2))), at, 1, 0.04, 0.04, 0.04, 0);
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
