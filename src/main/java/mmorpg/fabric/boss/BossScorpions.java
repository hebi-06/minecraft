package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.faceAll;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.say;
import static mmorpg.fabric.boss.BossKit.showFaceText;
import static mmorpg.fabric.boss.BossKit.shuffle;
import static mmorpg.fabric.boss.BossKit.sound;
import static mmorpg.fabric.boss.BossKit.title;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 사막 전갈 + 바위 전갈 (boss_scorpions.js — v1.9.0, Lv.18, 한 번에 둘): 수치는 config BOSSES.scorpions.units.
 * <ul>
 * <li>사막 전갈 (서쪽 모래, 빠름): 평타 집게 (앞 90°, 엔진) · 독침 (가장 먼 사람 발밑 초록 원 → 꼬리에서 독침, 피해 + 독) ·
 * 모래 잠행 (모래 속으로 사라짐 — 무적, 흙먼지 줄기가 한 사람을 쫓다가 멈춘 자리에 원 → 튀어나오며 띄움) ·
 * 맹독 표식 (체력 70% 아래: 한 사람에게 표식, 6초 뒤 둘레 3칸 안의 사람끼리 피해를 나눠 받음 (마법), 혼자면 즉사)</li>
 * <li>바위 전갈 (동쪽 바위, 느리고 셈): 평타 집게 내려찍기 (앞 120°, 엔진) · 바위 던지기 (세 사람 발밑 갈색 원 → 꼬리로 던진 바위가 포물선으로) ·
 * 대지의 파동 (세 번 내려찍으며 퍼지는 고리 → 고리가 지나갈 때 땅에 있으면 맞음, 점프로 피함) ·
 * 석화의 포효 (체력 70% 아래: 2.5초 울부짖음 → 그때 바위 전갈 쪽을 보던 사람은 4초 석화 = 속박 + 침묵)</li>
 * <li>함께: 공생 (둘이 7칸 안이면 둘 다 받는 피해 -60%, 금빛 끈) · 짝 잃은 분노 (하나가 쓰러지면 남은 쪽 피해 ×1.4 · 재사용 ×0.7 · 빠른 걸음 · 붉은 몸) ·
 * 큰 패턴 (모래 잠행 · 맹독 표식 · 대지의 파동 · 석화의 포효) 은 둘 사이에서도 겹치지 않음 (mem.bigUntil) · 처음 나오는 패턴은 채팅으로 규칙</li>
 * </ul>
 * 엔진 함수 (cast · hold · anim · setFast · other · ratio) 는 u.api 로 (애드온과 같이). 패턴 함수는 그 마리 (u) 를 받음 — 시험 · 사진첩이 바로 부를 수 있게 공개,
 * {@link #forceNext} = 그 패턴을 가진 마리의 다음 pick 에서 그 패턴.
 * 자바에서 달라진 점: 없음 (모양 · 수치 · 글자 그대로). 패턴 중 몸을 돌리는 것은 바닐라 시선 목표 (행동 팩의 look_at_player · melee_attack) 가 함 — 애드온과 같음.
 */
public final class BossScorpions implements BossModule {
    static final String KEY = "scorpions";
    public static final String DESERT = "desert_scorpion";
    public static final String ROCK = "rock_scorpion";
    static final double[] SAND = {0.96, 0.78, 0.4};
    static final double[] DUST = {0.95, 0.55, 0.2};
    static final double[] POISON = {0.45, 0.95, 0.3};
    static final double[] STONE = {0.72, 0.6, 0.48};
    static final double[] GRAY = {0.62, 0.62, 0.66};
    static final double[] GOLD = {1.0, 0.85, 0.35};
    static final double[] RAGE = {1.0, 0.2, 0.12};
    static final double[] WHITE = {1, 1, 1};
    /** 땅속에 있는 동안 유지 (다음 동작 = 튀어나오기가 같은 틀에서 바꿈) */
    static final String BURROW_CONTROLLER = "rpg_burrow";

    /**
     * 시험 · 사진첩: 다음 pick 에서 이 패턴 (사막 sting · burrow · venom / 바위 boulders · tremor · roar — 그 패턴을 가진 마리가 쓰면 비움,
     * 대상이 없으면 그냥 비움)
     */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    // ------------------------------------------------------------ 기억

    /**
     * 싸움의 기억 (애드온 enc.mem — 두 마리가 같이 봄): 다음 패턴 틱 · 큰 패턴이 끝나는 틱 · 공생 · 공생 알림 (때린 사람 → 틱) · 알린 규칙 ·
     * 모래 잠행 · 맹독 표식 · 떨어질 바위 · 퍼지는 파동 · 석화된 사람 · 분노한 마리
     */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new LinkedHashMap<>();
        public long bigUntil;
        public boolean bond;
        public final Map<UUID, Long> bondTold = new HashMap<>();
        public final Set<String> told = new HashSet<>();
        public Burrow burrow;
        public Venom venom;
        public List<Boulder> boulders = new ArrayList<>();
        public List<Wave> waves = new ArrayList<>();
        public final Map<UUID, Stone> stone = new LinkedHashMap<>();
        public Bosses.Unit enraged;
    }

    /** 모래 잠행: 그 마리 · 쫓는 사람 · 줄기 머리 · 쫓기 시작 · 멈추는 틱 · 멈춘 자리 (null = 쫓는 중) · 튀어나오는 틱 */
    public static final class Burrow {
        public final Bosses.Unit u;
        public final ServerPlayer target;
        public Vec3 head;
        public final long start;
        public final long stop;
        public Vec3 spot;
        public long hitAt;

        Burrow(Bosses.Unit u, ServerPlayer target, Vec3 head, long start, long stop) {
            this.u = u;
            this.target = target;
            this.head = head;
            this.start = start;
            this.stop = stop;
        }
    }

    /** 맹독 표식: 그 마리 · 표식을 받은 사람 · 터지는 틱 */
    public record Venom(Bosses.Unit u, ServerPlayer p, long at) {}

    /** 떨어질 바위: 던진 마리 · 자리 · 떨어지는 틱 */
    public record Boulder(Bosses.Unit u, List<Vec3> spots, long at) {}

    /** 퍼지는 파동: 마리 · 가운데 · 시작 틱 · 끝 반지름 · 맞은 사람 */
    public record Wave(Bosses.Unit u, Vec3 o, long t0, double maxR, Set<UUID> hit) {}

    /** 석화된 사람 · 끝나는 틱 */
    public record Stone(ServerPlayer p, long until) {}

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    /** 그 키의 마리 (없으면 null) */
    public static Bosses.Unit unitOf(Bosses.Enc enc, String key) {
        if (enc == null) return null;
        for (Bosses.Unit u : enc.units) if (key.equals(u.key)) return u;
        return null;
    }

    public static Bosses.Unit desert(Bosses.Enc enc) {
        return unitOf(enc, DESERT);
    }

    public static Bosses.Unit rock(Bosses.Enc enc) {
        return unitOf(enc, ROCK);
    }

    /** 다음 패턴까지 (분노면 cdMult 배) */
    static long every(Bosses.Unit u, Obj d) {
        double[] r = BossKit.pair(d, "every");
        return (long) Js.round(rnd((int) r[0], (int) r[1]) * u.cdMult);
    }

    /** 바닥 높이 (필드 가운데) */
    static Vec3 floorAt(Bosses.Unit u, double x, double z) {
        return new Vec3(x, u.center.y, z);
    }

    /**
     * 공중인지 (대지의 파동을 점프로 피함): 땅에 서 있지 않거나, 바닥보다 0.25칸 넘게 떠 있고 발밑이 비었음
     * (발밑 블록을 보는 것은 땅에 닿았다는 값이 늦게 바뀌는 경우를 위해. 제단 · 바위 위에 서 있으면 땅)
     */
    public static boolean airborne(ServerPlayer p, double floorY) {
        if (!p.onGround()) return true;
        if (p.getY() - floorY <= 0.25) return false;
        return p.level().getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.3, p.getZ())).isAir();
    }

    /** 처음 나오는 패턴은 채팅으로 규칙을 알림 (싸움마다 한 번) */
    static void explain(Bosses.Unit u, String id, String text) {
        M m = mem(u);
        if (m == null || !m.told.add(id)) return;
        say(u, text);
    }

    /** 몸 가운데 높이 (끈 · 연출용) */
    static Vec3 mid(Bosses.Unit u) {
        Vec3 l = u.boss.position();
        return new Vec3(l.x, l.y + bossSize(u.def).height() * 0.6, l.z);
    }

    /** 꼬리 끝 (독침 · 바위를 던지는 곳): 몸 위 조금 앞 */
    public static Vec3 tailTip(Bosses.Unit u) {
        Vec3 l = u.boss.position();
        Vec3 v = u.boss.getViewVector(1F);
        double L = Math.hypot(v.x, v.z);
        if (L == 0) L = 1;
        double fx = v.x / L;
        double fz = v.z / L;
        double s = u.def.numOr("scale", 1);
        return new Vec3(l.x + fx * 0.3 * s, l.y + 1.75 * s, l.z + fz * 0.3 * s);
    }

    // ============================================================ 사막 전갈
    // ------------------------------------------------------------ 독침

    /** 가장 먼 사람 (range 안) */
    public static ServerPlayer stingTarget(Bosses.Unit u, List<ServerPlayer> list) {
        Obj d = u.def.obj("sting");
        ServerPlayer tgt = null;
        double far = -1;
        for (ServerPlayer p : list) {
            double k = hdist(p.position(), u.boss.position());
            if (k <= d.num("range") && k > far) {
                far = k;
                tgt = p;
            }
        }
        return tgt;
    }

    public static void sting(Bosses.Unit u, long now, ServerPlayer target) {
        Obj d = u.def.obj("sting");
        Bosses.Api api = u.api;
        Vec3 spot = floorAt(u, target.getX(), target.getZ());
        double radius = d.num("radius");
        int windup = (int) d.num("windup");
        api.hold(u, true);
        api.anim(u, "sting");
        sound(u, "mob.spider.say", u.boss.position(), 1.3, 1);
        disc(u.dim, spot, radius, windup, POISON);
        double damage = d.num("damage");
        int poison = (int) d.num("poison");
        api.cast(u, "sting", windup + 10, step(windup - 3, (x, t) -> {
            // 꼬리에서 독침이 날아감
            SkillFx.beam(x.dim, tailTip(x), new Vec3(spot.x, spot.y + 0.3, spot.z), POISON, Opt.of("life", 0.3, "half", 0.3, "thick", 0.12, "gap", 0.45));
        }), step(windup, (x, t) -> {
            SkillFx.fx(x.dim, "rpg:bash_burst", new Vec3(spot.x, spot.y + 0.5, spot.z), Opt.of("dir", new Vec3(0, 0, 1), "size", radius * 1.2, "life", 0.35, "color",
                    POISON, "alpha", 0.9));
            play(x.dim, "mob.bee.sting", spot, 0.7, 1);
            for (ServerPlayer p : fighters(x)) {
                if (hdist(p.position(), spot) > radius + 0.3 || Math.abs(p.getY() - spot.y) > 3) continue;
                bossHit(x, p, damage, Opt.of("effect", BossKit.effect("poison", poison)));
            }
        }), step(windup + 6, (x, t) -> api.hold(x, false)));
    }

    // ------------------------------------------------------------ 모래 잠행

    /** 쫓을 사람: 사막 전갈이 닿는 곳 (제 자리 둘레 + 3칸) 안의 아무나 */
    public static ServerPlayer burrowTarget(Bosses.Unit u, List<ServerPlayer> list) {
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : list) if (hdist(p.position(), u.home) <= u.leash + 3) near.add(p);
        return near.isEmpty() ? null : near.get((int) Math.floor(random() * near.size()));
    }

    public static void burrow(Bosses.Unit u, long now, ServerPlayer target) {
        Obj d = u.def.obj("burrow");
        Bosses.Api api = u.api;
        M m = mem(u);
        if (m == null) return;
        long sink = (long) d.num("sink");
        long trail = (long) d.num("trail");
        long windup = (long) d.num("windup");
        m.bigUntil = now + sink + trail + windup + 40;
        api.hold(u, true);
        api.anim(u, "burrow", null, BURROW_CONTROLLER, "0");
        sound(u, "mob.warden.dig", u.boss.position(), 1.3, 1);
        Vec3 l = u.boss.position();
        for (int i = 0; i < 3; i++) {
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(l.x + (random() - 0.5) * 2, u.center.y + 0.4, l.z + (random() - 0.5) * 2), Opt.of("dir", new Vec3(0, 0, 1),
                    "size", 1.6, "life", 0.45, "color", SAND, "alpha", 0.9));
        }
        showFaceText(target, "§6§l땅속에서 쫓아옵니다! §e흙먼지를 보세요", Opt.of("dist", 2.2, "up", 0.35));
        explain(u, "burrow", "§6[보스] 모래 잠행: §f사막 전갈이 땅속으로 사라져(무적) 흙먼지 줄기로 한 사람을 쫓습니다. §e줄기가 멈춘 자리의 원 밖으로 피하세요");
        m.burrow = new Burrow(u, target, floorAt(u, l.x, l.z), now + sink, now + sink + trail);
        // 끝 (튀어나오기) 은 burrowTick 이 틀을 바꿔서: 그때까지 이 마리는 다른 행동 없음
        api.cast(u, "burrow", (int) (sink + trail + windup + 30), step(6, (x, t) -> x.immune = true));
    }

    /** 흙먼지 줄기 한 칸 (제 자리 둘레 안으로 잘라 둠: 튀어나온 뒤 끌려가지 않게) */
    static Vec3 clampHome(Bosses.Unit u, Vec3 p) {
        Vec3 h = u.home;
        double dx = p.x - h.x;
        double dz = p.z - h.z;
        double L = Math.hypot(dx, dz);
        double max = u.leash - 1;
        if (L <= max) return p;
        return new Vec3(h.x + (dx / L) * max, p.y, h.z + (dz / L) * max);
    }

    static void burrowTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Burrow b = m == null ? null : m.burrow;
        if (b == null) return;
        Bosses.Unit u = b.u;
        Obj d = u.def.obj("burrow");
        if (!u.alive || !Bosses.valid(u.boss)) {
            m.burrow = null;
            return;
        }
        if (now < b.start) return;
        if (b.spot == null) {
            // 쫓기: 대상 쪽으로 speed 칸/초
            ServerPlayer t = b.target;
            boolean alive = okTarget(t);
            if (alive) {
                double dx = t.getX() - b.head.x;
                double dz = t.getZ() - b.head.z;
                double L = Math.hypot(dx, dz);
                double st = Math.min(L, d.num("speed") / 20);
                if (L > 0.01) b.head = clampHome(u, new Vec3(b.head.x + (dx / L) * st, b.head.y, b.head.z + (dz / L) * st));
            }
            if (now % 2 == 0) {
                SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(b.head.x, b.head.y + 0.35, b.head.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.1, "life", 0.35, "color",
                        SAND, "alpha", 0.9));
                BossKit.teleport(u.boss, new Vec3(b.head.x, b.head.y + 0.05, b.head.z));
            }
            if (now % 4 == 0) disc(u.dim, b.head, 0.9, 8, DUST, Opt.of("alpha", 0.7));
            if (now % 6 == 0) play(u.dim, "dig.sand", b.head, 0.8, 1);
            boolean reached = alive && hdist(t.position(), b.head) <= 0.8;
            if (reached || !alive || now >= b.stop) {
                // 멈춘 자리: 원이 차오르면 튀어나옴
                b.spot = b.head;
                b.hitAt = now + (long) d.num("windup");
                disc(u.dim, b.spot, d.num("radius"), (int) d.num("windup"), DUST);
                play(u.dim, "mob.warden.emerge", b.spot, 1.4, 0.9);
            }
            return;
        }
        if (now < b.hitAt) {
            double radius = d.num("radius");
            if ((b.hitAt - now) % 6 == 0) {
                SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(b.spot.x + (random() - 0.5) * radius, b.spot.y + 0.3, b.spot.z + (random() - 0.5) * radius),
                        Opt.of("dir", new Vec3(0, 0, 1), "size", 1.0, "life", 0.3, "color", SAND, "alpha", 0.85));
            }
            return;
        }
        emerge(enc, b);
    }

    /** 튀어나옴: 원 안은 피해 + 띄움, 전갈이 그 자리에 나타남 */
    static void emerge(Bosses.Enc enc, Burrow b) {
        Bosses.Unit u = b.u;
        Obj d = u.def.obj("burrow");
        Bosses.Api api = u.api;
        Vec3 s = b.spot;
        double radius = d.num("radius");
        M m = mem(enc);
        if (m != null) m.burrow = null;
        u.immune = false;
        Vec3 face = new Vec3(s.x, s.y + 1, s.z - 1);
        if (okTarget(b.target)) face = new Vec3(b.target.getX(), s.y + 1, b.target.getZ());
        BossKit.teleport(u.boss, new Vec3(s.x, s.y + 0.1, s.z), face);
        api.anim(u, "emerge", null, BURROW_CONTROLLER, null);
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(s.x, s.y + 1, s.z), Opt.of("dir", new Vec3(0, 0, 1), "size", radius * 1.3, "life", 0.5, "color", SAND, "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(s.x, s.y + 0.1, s.z), Opt.of("dir", Vec3.ZERO, "size", radius / 2.1, "life", 0.45, "color", DUST, "alpha", 0.9));
        for (int i = 0; i < 6; i++) {
            double a = (i / 6.0) * Math.PI * 2;
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(s.x + Math.cos(a) * radius * 0.7, s.y + 0.5, s.z + Math.sin(a) * radius * 0.7), Opt.of("dir", new Vec3(0, 0, 1),
                    "size", 1.2, "life", 0.4, "color", SAND, "alpha", 0.85));
        }
        play(u.dim, "random.explode", s, 1.3, 0.8);
        play(u.dim, "dig.sand", s, 0.6, 1);
        for (ServerPlayer p : fighters(u)) {
            if (hdist(p.position(), s) > radius + 0.3 || Math.abs(p.getY() - s.y) > 3) continue;
            double dx = p.getX() - s.x;
            double dz = p.getZ() - s.z;
            double L = Math.hypot(dx, dz);
            if (L == 0) L = 1;
            bossHit(u, p, d.num("damage"), Opt.of("knock", knock((dx / L) * 0.4, (dz / L) * 0.4, d.num("lift"))));
        }
        // 틀을 바꿈: 잠깐 뒤 다시 움직임
        api.cast(u, "emerge", 20, step(16, (x, t) -> api.hold(x, false)));
    }

    // ------------------------------------------------------------ 맹독 표식

    public static void venom(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("venom");
        Bosses.Api api = u.api;
        M m = mem(u);
        if (m == null || list.isEmpty()) return;
        ServerPlayer p = list.get((int) Math.floor(random() * list.size()));
        long time = (long) d.num("time");
        m.bigUntil = now + time + 20;
        m.venom = new Venom(u, p, now + time);
        api.hold(u, true);
        api.anim(u, "hiss");
        sound(u, "mob.spider.say", u.boss.position(), 0.6, 1);
        String radius = Js.str(d.num("radius"));
        title(u, "§2§l맹독 표식", "§a" + p.getName().getString() + "§f 곁으로 모이세요! §7(" + radius + "칸 안에서 나눠 받음, 혼자면 즉사)", fighters(u));
        showFaceText(p, "§2§l맹독 표식! §f동료 곁으로", Opt.of("dist", 2.2, "up", 0.35));
        explain(u, "venom", "§2[보스] 맹독 표식: §f" + Js.str(Js.round(time / 20.0)) + "초 뒤 표식이 터집니다. §a표식 둘레 " + radius + "칸 안에 모인 사람끼리 피해를 나눠 받고, §4혼자면 즉사§f합니다");
        api.cast(u, "venom", 24, step(20, (x, t) -> api.hold(x, false)));
    }

    static void venomTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Venom v = m == null ? null : m.venom;
        if (v == null) return;
        Bosses.Unit u = v.u();
        Obj d = u.def.obj("venom");
        double radius = d.num("radius");
        // 표식은 필드 밖으로 달아나도 따라감 (싸움에서 아주 멀어지면 · 다른 차원이면 · 쓰러지면 사라짐)
        boolean gone = !u.alive || !okTarget(v.p());
        gone = gone || !NpcData.dimId(v.p().level()).equals(enc.a.dim) || hdist(v.p().position(), enc.center) > 64;
        if (gone) {
            m.venom = null;
            return;
        }
        Vec3 at = v.p().position();
        if (now % 5 == 0) {
            disc(u.dim, floorAt(u, at.x, at.z), radius, 6, POISON, Opt.of("fill", false, "alpha", 0.95));
            Vec3 h = v.p().getEyePosition();
            SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(h.x, h.y + 0.75, h.z), Opt.of("dir", new Vec3(0, 1, 0), "size", 0.5, "life", 0.3, "spin", (double) ((now * 14) % 360),
                    "progress", 1.0, "color", POISON, "alpha", 0.95));
        }
        long left = v.at() - now;
        if (left > 0) {
            if (left % 20 == 0 && left <= 60) faceAll(u, "§2§l맹독 " + (left / 20), Opt.of("dist", 2.4, "up", 0.5), fighters(u));
            return;
        }
        m.venom = null;
        // 나눠 받는 사람: 표식 둘레의 모든 플레이어 (필드 밖 성문 자리에 함께 있어도 셈)
        List<ServerPlayer> group = new ArrayList<>();
        for (ServerPlayer q : u.dim.players()) {
            if (okTarget(q) && hdist(q.position(), at) <= radius + 0.3 && Math.abs(q.getY() - at.y) < 3) group.add(q);
        }
        if (!group.contains(v.p())) group.add(v.p());
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(at.x, at.y + 1, at.z), Opt.of("dir", new Vec3(0, 0, 1), "size", radius * 1.2, "life", 0.5, "color", POISON, "alpha",
                0.95));
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(at.x, at.y + 0.1, at.z), Opt.of("dir", Vec3.ZERO, "size", radius / 2.1, "life", 0.5, "color", POISON, "alpha", 0.9));
        play(u.dim, "mob.slime.big", at, 0.5, 1);
        play(u.dim, "random.explode", at, 1.6, 0.6);
        if (group.size() <= 1) {
            showFaceText(v.p(), "§4§l즉사 §7— 혼자였습니다", Opt.of("dist", 2.2, "up", 0.3));
            say(u, "§4[보스] " + v.p().getName().getString() + "님이 맹독 표식을 혼자 받았습니다");
            Status.kill(v.p(), u.boss);
            return;
        }
        double each = d.num("total") / group.size();
        for (ServerPlayer q : group) {
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(q.getX(), q.getY() + 1, q.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.2, "life", 0.35, "color", POISON,
                    "alpha", 0.9));
            showFaceText(q, "§2맹독 §f÷" + group.size(), Opt.of("dist", 2.4, "side", 0.5, "up", 0.2));
            bossHit(u, q, each, Opt.of("cause", "magic")); // 마법: 갑옷으로 줄지 않음 (나눠 받는 게 요령)
        }
    }

    // ============================================================ 바위 전갈
    // ------------------------------------------------------------ 바위 던지기

    /** from → to 로 t 초 동안 포물선 (파티클 속도 · 방향 — rpg:boulder 의 중력 20) */
    public static BossKit.Arc boulderArc(Vec3 from, Vec3 to, double t) {
        return BossKit.lobArc(from, to, t, BossKit.LOB_GRAVITY);
    }

    public static void boulders(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Obj d = u.def.obj("boulders");
        List<Vec3> spots = new ArrayList<>();
        List<ServerPlayer> picked = shuffle(list);
        for (int i = 0; i < Math.min((int) d.num("count"), picked.size()); i++) spots.add(floorAt(u, picked.get(i).getX(), picked.get(i).getZ()));
        bouldersAt(u, now, spots);
    }

    /** 바위 던지기를 정한 자리 (바닥) 로 — boulders 가 사람 발밑으로 부름 (사진첩: 한 사람으로 원 셋을 보이려고) */
    public static void bouldersAt(Bosses.Unit u, long now, List<Vec3> spots) {
        Obj d = u.def.obj("boulders");
        Bosses.Api api = u.api;
        M m = mem(u);
        int windup = (int) d.num("windup");
        double radius = d.num("radius");
        api.hold(u, true);
        api.anim(u, "throw");
        sound(u, "mob.irongolem.throw", u.boss.position(), 0.6, 1);
        for (Vec3 s : spots) disc(u.dim, s, radius, windup, STONE);
        int launch = 8;
        api.cast(u, "boulders", launch + 14, step(launch, (x, t) -> {
            Vec3 from = tailTip(x);
            double sec = (windup - launch) / 20.0;
            for (Vec3 s : spots) {
                BossKit.Arc arc = boulderArc(from, new Vec3(s.x, s.y + 0.4, s.z), sec);
                SkillFx.fx(x.dim, "rpg:boulder", from, Opt.of("dir", arc.dir(), "speed", arc.speed(), "size", 0.75, "life", sec, "spin", random() * 360, "color", WHITE,
                        "alpha", 1.0));
            }
        }), step(launch + 12, (x, t) -> api.hold(x, false)));
        if (m != null) m.boulders.add(new Boulder(u, spots, now + windup));
    }

    static void boulderTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (m == null || m.boulders.isEmpty()) return;
        List<Boulder> due = new ArrayList<>();
        List<Boulder> keep = new ArrayList<>();
        for (Boulder b : m.boulders) (now >= b.at() ? due : keep).add(b);
        if (due.isEmpty()) return;
        m.boulders = keep;
        for (Boulder b : due) {
            Bosses.Unit u = b.u();
            if (!u.alive) continue;
            Obj d = u.def.obj("boulders");
            double radius = d.num("radius");
            for (Vec3 s : b.spots()) {
                SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(s.x, s.y + 0.6, s.z), Opt.of("dir", new Vec3(0, 0, 1), "size", radius, "life", 0.4, "color", STONE, "alpha",
                        0.95));
                SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(s.x, s.y + 0.1, s.z), Opt.of("dir", Vec3.ZERO, "size", radius / 2.1, "life", 0.4, "color", STONE, "alpha", 0.9));
                play(u.dim, "random.explode", s, 1.2, 0.8);
                play(u.dim, "dig.stone", s, 0.6, 1);
            }
            for (ServerPlayer p : fighters(u)) {
                boolean in = false;
                for (Vec3 s : b.spots()) if (hdist(p.position(), s) <= radius + 0.3 && Math.abs(p.getY() - s.y) < 3) in = true;
                if (!in) continue;
                bossHit(u, p, d.num("damage"), Opt.of("cause", "entityExplosion", "effect", BossKit.effect("slowness", (int) d.num("slow"), 1)));
            }
        }
    }

    // ------------------------------------------------------------ 대지의 파동

    public static void tremor(Bosses.Unit u, long now) {
        Obj d = u.def.obj("tremor");
        Bosses.Api api = u.api;
        M m = mem(u);
        if (m == null) return;
        int waves = (int) d.num("waves");
        int first = (int) d.num("first");
        int gap = (int) d.num("gap");
        int len = first + gap * (waves - 1) + 20;
        m.bigUntil = now + len + 30;
        api.hold(u, true);
        api.anim(u, "stomp");
        sound(u, "mob.ravager.roar", u.boss.position(), 0.5, 1);
        title(u, "§6§l대지의 파동", "§e고리가 발밑을 지날 때 점프!", fighters(u));
        explain(u, "tremor", "§6[보스] 대지의 파동: §f바위 전갈이 " + waves + "번 내려찍으며 땅이 고리 모양으로 퍼집니다. §e고리가 지나가는 순간 땅에 있으면 맞습니다 (점프로 피함)");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int i = 0; i < waves; i++) steps.add(step(first + (long) i * gap, (x, t) -> startWave(x)));
        steps.add(step(len, (x, t) -> api.hold(x, false)));
        api.cast(u, "tremor", len + 2, steps);
    }

    static void startWave(Bosses.Unit u) {
        Obj d = u.def.obj("tremor");
        M m = mem(u);
        if (m == null) return;
        Vec3 l = u.boss.position();
        Vec3 o = floorAt(u, l.x, l.z);
        double speed = d.num("speed");
        // 필드 끝 (성문 자리) 까지 닿는 반지름
        double maxR = hdist(o, u.center) + u.enc.def.obj("arena").num("radius") + 3;
        SkillFx.fx(u.dim, "rpg:tele_wave", new Vec3(o.x, o.y + 0.08, o.z), Opt.of("dir", Vec3.ZERO, "speed", speed, "size", 0.7, "life", maxR / speed, "color", DUST,
                "alpha", 0.95));
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(o.x, o.y + 0.1, o.z), Opt.of("dir", Vec3.ZERO, "size", 1.6, "life", 0.4, "color", STONE, "alpha", 0.9));
        SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(o.x, o.y + 0.6, o.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.6, "life", 0.4, "color", STONE, "alpha", 0.9));
        play(u.dim, "random.explode", o, 0.5, 1);
        play(u.dim, "dig.stone", o, 0.5, 1);
        m.waves.add(new Wave(u, o, Tasks.now(), maxR, new HashSet<>()));
    }

    /** 파동: 고리가 이번 틱에 지나간 거리에 있고 땅에 서 있으면 맞음 (파동마다 한 번) */
    static void waveTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (m == null || m.waves.isEmpty()) return;
        List<ServerPlayer> list = fighters(enc);
        List<Wave> keep = new ArrayList<>();
        for (Wave w : m.waves) {
            Bosses.Unit u = w.u();
            Obj d = u.def.obj("tremor");
            if (!u.alive) continue;
            double speed = d.num("speed");
            long age = now - w.t0();
            double r1 = (speed * age) / 20;
            double r0 = (speed * Math.max(0, age - 1)) / 20;
            if (r0 > w.maxR()) continue;
            keep.add(w);
            for (ServerPlayer p : list) {
                if (w.hit().contains(p.getUUID())) continue;
                double k = hdist(p.position(), w.o());
                if (k < r0 - 0.35 || k > r1 + 0.35 || Math.abs(p.getY() - w.o().y) > 3) continue;
                if (airborne(p, w.o().y)) continue;
                w.hit().add(p.getUUID());
                double L = k == 0 ? 1 : k;
                bossHit(u, p, d.num("damage"), Opt.of("knock", knock(((p.getX() - w.o().x) / L) * 0.3, ((p.getZ() - w.o().z) / L) * 0.3, 0.35)));
                SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 0.4, p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.0, "life", 0.3, "color", STONE,
                        "alpha", 0.9));
            }
        }
        m.waves = keep;
    }

    // ------------------------------------------------------------ 석화의 포효

    public static void roar(Bosses.Unit u, long now) {
        Obj d = u.def.obj("roar");
        Bosses.Api api = u.api;
        M m = mem(u);
        if (m == null) return;
        int windup = (int) d.num("windup");
        int hold = (int) d.num("hold");
        // 석화가 풀릴 때까지 큰 패턴 없음 (움직이지도 뛰지도 못하는 동안 파동 · 모래 잠행이 오면 피할 수 없으므로)
        m.bigUntil = now + windup + hold + 10;
        api.hold(u, true);
        api.anim(u, "roar");
        sound(u, "mob.ravager.roar", u.boss.position(), 0.6, 1);
        title(u, "§6§l석화의 포효", "§e바위 전갈에게서 눈을 돌리세요!", fighters(u));
        explain(u, "roar", "§6[보스] 석화의 포효: §f울부짖음이 끝날 때 §e바위 전갈 쪽을 보고 있으면 " + Js.str(Js.round(hold / 20.0)) + "초 석화§f(움직임·스킬 봉인)됩니다. 등을 돌리세요");
        List<Bosses.Step> steps = new ArrayList<>();
        for (int t = 0; t < windup; t += 10) {
            double k = (double) t / windup;
            steps.add(step(t, (x, n) -> roarCharge(x, k)));
        }
        for (int k = 2; k >= 1; k--) {
            int kk = k;
            steps.add(step(windup - k * 20L, (x, n) -> faceAll(x, "§6§l" + kk, Opt.of("dist", 2.4, "up", 0.5), fighters(x))));
        }
        steps.add(step(windup, (x, n) -> roarRelease(x)));
        steps.add(step(windup + 24, (x, n) -> api.hold(x, false)));
        api.cast(u, "roar", windup + 26, steps);
    }

    /** 모이는 고리 + 발밑 마법진 (k = 0~1 진행) */
    static void roarCharge(Bosses.Unit u, double k) {
        Vec3 l = u.boss.position();
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(l.x, u.center.y + 0.1, l.z), Opt.of("dir", Vec3.ZERO, "size", Math.max(0.6, 5 - k * 4.4), "life", 0.5, "color", GRAY,
                "alpha", 0.9));
        SkillFx.fx(u.dim, "rpg:skill_rune", new Vec3(l.x, u.center.y + 0.12, l.z), Opt.of("size", 3.2, "life", 0.55, "spin", k * 180, "speed", 40.0, "grow", 0.0,
                "progress", k, "color", STONE, "alpha", 0.9));
    }

    /** 울부짖음 끝: 바위 전갈 쪽을 보던 사람은 석화 (속박 + 침묵) */
    static void roarRelease(Bosses.Unit u) {
        Obj d = u.def.obj("roar");
        M m = mem(u);
        Vec3 head = mid(u);
        double cosMax = Math.cos(((d.num("cone") / 2) * Math.PI) / 180);
        int hold = (int) d.num("hold");
        SkillFx.fx(u.dim, "rpg:shock_ring", new Vec3(head.x, u.center.y + 0.2, head.z), Opt.of("dir", Vec3.ZERO, "size", 8.0, "life", 0.6, "color", GRAY, "alpha", 0.9));
        SkillFx.fx(u.dim, "rpg:bash_burst", head, Opt.of("dir", new Vec3(0, 0, 1), "size", 4.0, "life", 0.5, "color", GRAY, "alpha", 0.9));
        sound(u, "mob.warden.roar", head, 0.8, 1);
        List<String> stoned = new ArrayList<>();
        for (ServerPlayer p : fighters(u)) {
            Vec3 eye = p.getEyePosition();
            Vec3 v = p.getViewVector(1F);
            Vec3 to = new Vec3(head.x - eye.x, head.y - eye.y, head.z - eye.z);
            double L = to.length();
            if (L > d.num("range")) continue;
            double vl = v.length();
            if (vl == 0) vl = 1;
            double cos = L < 0.5 ? 1 : (v.x * to.x + v.y * to.y + v.z * to.z) / (L * vl);
            if (cos < cosMax) continue;
            Status.root(p, hold, "석화");
            Status.silence(p, hold);
            if (m != null) m.stone.put(p.getUUID(), new Stone(p, Tasks.now() + hold));
            SkillFx.fx(u.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.6, "life", 0.45, "color", GRAY,
                    "alpha", 0.95));
            play(u.dim, "mob.elderguardian.curse", p.position(), 1, 0.7);
            stoned.add(p.getName().getString());
        }
        if (!stoned.isEmpty()) say(u, "§7[보스] 석화: §f" + String.join(", ", stoned));
    }

    /** 석화된 사람: 몸에 돌 조각 (끝나면 뺌) */
    static void stoneTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (m == null || m.stone.isEmpty()) return;
        for (Map.Entry<UUID, Stone> e : new ArrayList<>(m.stone.entrySet())) {
            Stone s = e.getValue();
            if (now >= s.until() || s.p().isRemoved()) {
                m.stone.remove(e.getKey());
                continue;
            }
            if (now % 8 == 0) {
                SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(s.p().getX(), s.p().getY() + 0.9, s.p().getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.0, "life", 0.4,
                        "color", GRAY, "alpha", 0.9));
            }
        }
    }

    // ============================================================ 함께

    /** 공생: 둘이 가까우면 금빛 끈 + 받는 피해 감소 (처음 이어질 때 알림) */
    static void bondTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        if (m == null) return;
        Bosses.Unit a = desert(enc);
        Bosses.Unit b = rock(enc);
        Obj S = enc.def.obj("symbiosis");
        boolean was = m.bond;
        m.bond = a != null && b != null && a.alive && b.alive && Bosses.valid(a.boss) && Bosses.valid(b.boss)
                && hdist(a.boss.position(), b.boss.position()) <= S.num("distance");
        if (!m.bond) return;
        String cut = Js.str(Js.round(S.num("cut") * 100));
        if (!was) {
            if (!m.told.contains("bond")) title(enc, "§e§l공생", "§f두 전갈이 가까우면 받는 피해 -" + cut + "% §7— 떨어뜨리세요!", fighters(enc));
            explain(a, "bond", "§e[보스] 공생: §f두 전갈이 " + Js.str(S.num("distance")) + "칸 안에 있으면 금빛 끈으로 이어져 둘 다 받는 피해가 " + cut
                    + "% 줄어듭니다. §e파티를 나눠 서쪽(모래)과 동쪽(바위)으로 떨어뜨리세요");
            play(enc.dim, "mob.evocation_illager.cast_spell", enc.center, 1.4, 0.6);
        }
        // 끈: 0.3초마다 새로 (수명 0.5초 → 끊기지 않고 보임), 두 몸에 금빛 반짝임
        if (now % 6 == 0) SkillFx.beam(enc.dim, mid(a), mid(b), GOLD, Opt.of("life", 0.5, "half", 0.3, "thick", 0.24, "gap", 0.55));
        if (now % 12 == 0) {
            for (Bosses.Unit x : List.of(a, b)) {
                SkillFx.fx(enc.dim, "rpg:bash_burst", mid(x), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.3, "life", 0.35, "color", GOLD, "alpha", 0.8));
            }
        }
    }

    /** 분노한 마리: 등에서 붉은 기운 */
    static void rageTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Bosses.Unit o = m == null ? null : m.enraged;
        if (o == null || !o.alive || !Bosses.valid(o.boss) || now % 16 != 0) return;
        Vec3 p = mid(o);
        SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(p.x, p.y + 0.8, p.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.4, "life", 0.4, "color", RAGE, "alpha", 0.85));
    }

    static void endBurrow(Bosses.Enc enc) {
        M m = mem(enc);
        Burrow b = m == null ? null : m.burrow;
        if (b == null) return;
        m.burrow = null;
        b.u.immune = false;
    }

    // ------------------------------------------------------------ 모듈

    /** 원거리 견제 (v2.3.0, 바위 전갈): 꼬리 끝에서 바위 조각을 날림 */
    @Override
    public Vec3 farFrom(Bosses.Unit u, Obj style) {
        return Bosses.valid(u.boss) ? tailTip(u) : null;
    }

    /** 맹독 표식 동안은 쉼 (표식이 붙은 사람 곁으로 모여야 함) */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        return m == null || m.venom == null;
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        Obj dd = desert(enc).def;
        Obj rd = rock(enc).def;
        M m = new M();
        m.next.put("sting", now + 100);
        m.next.put("burrow", now + 260);
        m.next.put("venom", now + (long) dd.obj("venom").num("first"));
        m.next.put("boulders", now + 140);
        m.next.put("tremor", now + 360);
        m.next.put("roar", now + (long) rd.obj("roar").num("first"));
        m.bigUntil = now + 120;
        enc.mem = m;
    }

    /** 시험 · 사진첩이 정한 패턴 (그 패턴을 가진 마리만 — 시작했으면 true) */
    private static boolean forced(Bosses.Unit u, long now, List<ServerPlayer> list) {
        String f = forceNext;
        if (f == null) return false;
        boolean mine = DESERT.equals(u.key) ? List.of("sting", "burrow", "venom").contains(f) : List.of("boulders", "tremor", "roar").contains(f);
        if (!mine) return false;
        forceNext = null;
        switch (f) {
            case "sting" -> {
                ServerPlayer t = stingTarget(u, list);
                if (t == null) return false;
                sting(u, now, t);
            }
            case "burrow" -> {
                ServerPlayer t = burrowTarget(u, list);
                if (t == null) return false;
                burrow(u, now, t);
            }
            case "venom" -> {
                if (list.isEmpty()) return false;
                venom(u, now, list);
            }
            case "boulders" -> {
                if (list.isEmpty()) return false;
                boulders(u, now, list);
            }
            case "tremor" -> tremor(u, now);
            default -> roar(u, now);
        }
        return true;
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        M m = mem(u);
        if (m == null) return false;
        if (forced(u, now, list)) return true;
        Obj d = u.def;
        double r = u.api.ratio(u);
        boolean big = now >= m.bigUntil;
        if (DESERT.equals(u.key)) {
            Obj venom = d.obj("venom");
            if (big && r <= venom.num("from") && m.venom == null && now >= m.next.get("venom") && !list.isEmpty()) {
                m.next.put("venom", now + (long) Js.round(venom.num("every") * u.cdMult));
                venom(u, now, list);
                return true;
            }
            if (big && now >= m.next.get("burrow")) {
                ServerPlayer t = burrowTarget(u, list);
                if (t != null) {
                    m.next.put("burrow", now + every(u, d.obj("burrow")));
                    burrow(u, now, t);
                    return true;
                }
            }
            if (now >= m.next.get("sting")) {
                ServerPlayer t = stingTarget(u, list);
                if (t != null) {
                    m.next.put("sting", now + every(u, d.obj("sting")));
                    sting(u, now, t);
                    return true;
                }
            }
            return false;
        }
        Obj roar = d.obj("roar");
        if (big && r <= roar.num("from") && now >= m.next.get("roar") && !list.isEmpty()) {
            m.next.put("roar", now + (long) Js.round(roar.num("every") * u.cdMult));
            roar(u, now);
            return true;
        }
        if (big && now >= m.next.get("tremor")) {
            m.next.put("tremor", now + every(u, d.obj("tremor")));
            tremor(u, now);
            return true;
        }
        if (now >= m.next.get("boulders") && !list.isEmpty()) {
            m.next.put("boulders", now + every(u, d.obj("boulders")));
            boulders(u, now, list);
            return true;
        }
        return false;
    }

    /** 받는 피해: 공생이면 줄어듦 (때린 사람에게 가끔 알림) */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        M m = mem(u);
        if (m == null || !m.bond || u.api.other(u) == null) return null;
        long now = Tasks.now();
        double cut = u.enc.def.obj("symbiosis").num("cut");
        if (now - m.bondTold.getOrDefault(p.getUUID(), -100L) >= 30) {
            m.bondTold.put(p.getUUID(), now);
            showFaceText(p, "§e공생 §7피해 -" + Js.str(Js.round(cut * 100)) + "%", Opt.of("dist", 2.4, "side", -0.5, "up", 0.25));
        }
        return Bosses.Hurt.of(amount * (1 - cut));
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        if (mem(enc) == null) return;
        bondTick(enc, now);
        burrowTick(enc, now);
        venomTick(enc, now);
        boulderTick(enc, now);
        waveTick(enc, now);
        stoneTick(enc, now);
        rageTick(enc, now);
    }

    /** 한 마리가 쓰러짐: 그 마리의 패턴을 거두고, 남은 쪽은 짝 잃은 분노 */
    @Override
    public void unitDied(Bosses.Enc enc, Bosses.Unit u) {
        M m = mem(enc);
        if (m == null) return;
        long now = Tasks.now();
        if (DESERT.equals(u.key)) {
            endBurrow(enc);
            m.venom = null;
        } else {
            m.boulders.removeIf(b -> b.u() == u);
            m.waves.removeIf(w -> w.u() == u);
        }
        m.bond = false;
        Bosses.Unit o = u.api.other(u);
        if (o == null) return;
        Obj E = enc.def.obj("enrage");
        o.dmgMult = E.num("damage");
        o.cdMult = E.num("cd");
        u.api.setFast(o);
        // 이미 잡혀 있던 다음 패턴 시각도 당김
        for (Map.Entry<String, Long> e : m.next.entrySet()) {
            if (e.getValue() > now) e.setValue(now + (long) Js.round((e.getValue() - now) * E.num("cd")));
        }
        m.enraged = o;
        // 계속 옅게 붉은 몸 (렌더 컨트롤러: rpg:rage)
        if (Bosses.valid(o.boss)) o.boss.setProp("rpg:rage", true);
        // 땅속에 있으면 (모래 잠행) 동작을 틀지 않음: 가라앉은 모습이 풀려 땅 위로 보이지 않게
        if (m.burrow == null || m.burrow.u != o) u.api.anim(o, "rage");
        title(o, "§4§l짝 잃은 분노", "§c" + o.def.str("name") + ": 피해 +" + Js.str(Js.round((E.num("damage") - 1) * 100)) + "% · 패턴이 빨라짐");
        if (Bosses.valid(o.boss)) {
            sound(o, "mob.ravager.roar", o.boss.position(), 0.7, 1);
            SkillFx.fx(enc.dim, "rpg:shock_ring", new Vec3(o.boss.getX(), enc.center.y + 0.1, o.boss.getZ()), Opt.of("dir", Vec3.ZERO, "size", 4.0, "life", 0.6, "color",
                    RAGE, "alpha", 0.9));
        }
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        endBurrow(enc);
        m.venom = null;
        m.boulders = new ArrayList<>();
        m.waves = new ArrayList<>();
        m.stone.clear();
    }
}
