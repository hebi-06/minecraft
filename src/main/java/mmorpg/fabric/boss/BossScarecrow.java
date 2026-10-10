package mmorpg.fabric.boss;

import static mmorpg.fabric.boss.Bosses.step;
import static mmorpg.fabric.boss.BossKit.bossHit;
import static mmorpg.fabric.boss.BossKit.bossSize;
import static mmorpg.fabric.boss.BossKit.disc;
import static mmorpg.fabric.boss.BossKit.effect;
import static mmorpg.fabric.boss.BossKit.faceAll;
import static mmorpg.fabric.boss.BossKit.fighters;
import static mmorpg.fabric.boss.BossKit.hdist;
import static mmorpg.fabric.boss.BossKit.knock;
import static mmorpg.fabric.boss.BossKit.lineMarks;
import static mmorpg.fabric.boss.BossKit.nearSegment;
import static mmorpg.fabric.boss.BossKit.okTarget;
import static mmorpg.fabric.boss.BossKit.play;
import static mmorpg.fabric.boss.BossKit.random;
import static mmorpg.fabric.boss.BossKit.rnd;
import static mmorpg.fabric.boss.BossKit.say;
import static mmorpg.fabric.boss.BossKit.showFaceText;
import static mmorpg.fabric.boss.BossKit.shuffle;
import static mmorpg.fabric.boss.BossKit.snd;
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
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 허수아비 킹 (boss_scarecrow.js — v1.8.0, Lv.10, 수확의 왕): 수치는 config BOSSES.scarecrow_king.
 * <ul>
 * <li>평타 낫 베기: 앞 120° 부채꼴 (엔진)</li>
 * <li>까마귀 떼: 플레이어 3명을 지나는 선 (필드 끝에서 끝) → 까마귀가 선을 따라 날아가며 피해 + 실명</li>
 * <li>호박 폭탄: 플레이어 2명 발밑 + 무작위 3곳에 호박 → 원이 다 차면 폭발</li>
 * <li>짚단 감옥 (체력 80% 아래): 가장 먼 플레이어를 속박 + 짚단 감옥 (체력 prison.hp). 부수면 풀림, 못 부수면 불타며 큰 피해</li>
 * <li>분신 (체력 60% · 25%): 허수아비 넷 사이로 숨음. 진짜는 어깨에 까마귀 → 진짜를 때리면 5초 비틀거림 (받는 피해 +50%), 가짜를 때리면 침묵 6초 + 작은 피해,
 * 15초 안에 못 찾으면 '수확의 밤' (모두 큰 피해)</li>
 * <li>수확의 낫 (체력 40% 아래, 45초마다): 가운데서 낫을 들어 올림 → 5초 뒤 허수아비 토템 둘레 (금빛 원) 밖은 즉사. 토템 하나에 2명까지</li>
 * </ul>
 * 엔진 함수 (cast · hold · anim · 소품) 는 enc.api 로 (애드온과 같이). 패턴 함수 (crows · bombs · prison · decoys · reaping) 는 시험 · 사진첩이 바로 부를 수 있게 공개,
 * {@link #forceNext} = 다음 pick 에서 그 패턴.
 */
public final class BossScarecrow implements BossModule {
    static final String KEY = "scarecrow_king";
    public static final String DECOY = "rpg:decoy_scarecrow_king";
    public static final String BOMB = "rpg:prop_pumpkin_bomb";
    /** 정령이 노리지 않는 표시 (runes.js NO_AUTO_TAG) */
    static final String NO_AUTO = "rpg_no_auto";
    public static final String CAGE = "rpg:prop_straw_cage";
    static final double[] GOLD = {1.0, 0.82, 0.25};
    static final double[] ORANGE = {1.0, 0.5, 0.12};
    static final double[] CROW = {0.45, 0.2, 0.62};
    static final double[] WHITE = {1, 1, 1};

    /** 시험 · 사진첩: 다음 pick 에서 이 패턴 (crows · bombs · prison · decoys · reaping — 쓰면 비움) */
    public static String forceNext;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<String> propIds() {
        return List.of(DECOY, BOMB, CAGE);
    }

    // ------------------------------------------------------------ 기억

    /** 싸움의 기억 (애드온 enc.mem): 다음 패턴 틱 · 분신을 쓴 체력 · 다음 수확의 낫 · 감옥 · 분신 · 폭탄 · 비틀거림 · 까마귀 날갯짓 */
    public static final class M extends Bosses.Mem {
        public final Map<String, Long> next = new HashMap<>();
        public final Set<Double> decoyDone = new HashSet<>();
        public Long reapAt;
        public Cage cage;
        public Decoys decoys;
        public List<Bomb> bombs = new ArrayList<>();
        public boolean staggering;
        public long tell;
    }

    /** 짚단 감옥: 갇힌 사람 · 감옥 엔티티 · 끝나는 틱 */
    public static final class Cage {
        public final ServerPlayer p;
        public final Entity e;
        public final long until;

        Cage(ServerPlayer p, Entity e, long until) {
            this.p = p;
            this.e = e;
            this.until = until;
        }
    }

    /** 분신: 진짜 자리 번호 · 자리들 · 끝나는 틱 · 분신 엔티티 · 나타났는지 · 나타난 틱 */
    public static final class Decoys {
        public final int real;
        public final List<Vec3> spots;
        public final long until;
        public final List<Entity> props = new ArrayList<>();
        public boolean shown;
        public long shownAt;

        Decoys(int real, List<Vec3> spots, long until) {
            this.real = real;
            this.spots = spots;
            this.until = until;
        }
    }

    /** 터질 호박들: 터지는 틱 · 자리 · 호박 엔티티 */
    public record Bomb(long at, List<Vec3> spots, List<Entity> props) {}

    /** 이 싸움의 기억 (시작 전이면 null) */
    public static M mem(Bosses.Unit u) {
        return u != null && u.mem instanceof M m ? m : null;
    }

    static double floorY(Bosses.Unit enc) {
        return enc.center.y;
    }

    /** 움직일 수 있는 사람 (감옥에 갇힌 사람은 피할 수 없으므로 까마귀 · 폭탄이 노리지 않음) */
    static List<ServerPlayer> free(Bosses.Unit enc, List<ServerPlayer> list) {
        M m = mem(enc);
        ServerPlayer caged = m != null && m.cage != null ? m.cage.p : null;
        if (caged == null) return list;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : list) if (p != caged) out.add(p);
        return out;
    }

    private static <T> List<T> head(List<T> list, int n) {
        return new ArrayList<>(list.subList(0, Math.max(0, Math.min(n, list.size()))));
    }

    // ------------------------------------------------------------ 까마귀 떼

    private record Line(Vec3 a, Vec3 b, Vec3 dir) {}

    public static void crows(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        Obj d = enc.def.obj("crows");
        double R = Arena.arenaSize(enc.a).radius();
        Bosses.Api api = enc.api;
        api.hold(enc, true);
        api.anim(enc, "cast");
        sound(enc, "mob.phantom.swoop", enc.boss.position(), 0.7, 1);
        Vec3 c = enc.center;
        double width = d.num("width");
        int windup = (int) d.num("windup");
        List<Line> lines = new ArrayList<>();
        for (ServerPlayer p : head(shuffle(free(enc, list)), (int) d.num("targets"))) {
            double ang = random() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 m = p.position();
            // 플레이어를 지나는 선을 필드 원에서 자름 (필드 끝에서 끝까지)
            double ox = m.x - c.x;
            double oz = m.z - c.z;
            double bb = ox * dir.x + oz * dir.z;
            double h = Math.sqrt(Math.max(0, bb * bb - (ox * ox + oz * oz - R * R)));
            Vec3 a = new Vec3(m.x + dir.x * (-bb - h), floorY(enc), m.z + dir.z * (-bb - h));
            Vec3 b = new Vec3(m.x + dir.x * (-bb + h), floorY(enc), m.z + dir.z * (-bb + h));
            lineMarks(enc.dim, a, b, width, windup, CROW);
            lines.add(new Line(a, b, dir));
        }
        double damage = d.num("damage");
        int blind = (int) d.num("blind");
        api.cast(enc, "crows", windup + 12, step(windup, (e, t) -> {
            for (Line L : lines) {
                double len = hdist(L.a(), L.b());
                int n = Math.max(1, (int) Math.ceil(len / 1.5));
                for (int i = 0; i <= n; i++) {
                    double k = (double) i / n;
                    SkillFx.fx(e.dim, "rpg:crow", new Vec3(L.a().x + (L.b().x - L.a().x) * k, floorY(e) + 1.1 + random() * 0.8, L.a().z + (L.b().z - L.a().z) * k),
                            Opt.of("dir", new Vec3(L.dir().x, 0.05, L.dir().z), "speed", 6.0, "size", 0.35, "life", 0.5 + k * 0.2, "delay", k * 0.2, "color", WHITE,
                                    "alpha", 1.0));
                }
            }
            sound(e, "mob.phantom.bite", e.center, 1.2, 1);
            for (ServerPlayer p : fighters(e)) {
                boolean on = false;
                for (Line L : lines) if (nearSegment(p.position(), L.a(), L.b(), width)) on = true;
                if (!on) continue;
                bossHit(e, p, damage, Opt.of("effect", effect("blindness", blind)));
            }
        }), step(windup + 8, (e, t) -> api.hold(e, false)));
    }

    // ------------------------------------------------------------ 호박 폭탄

    public static void bombs(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        Obj d = enc.def.obj("bombs");
        double R = Arena.arenaSize(enc.a).radius();
        Bosses.Api api = enc.api;
        api.anim(enc, "cast");
        sound(enc, "random.fuse", enc.center, 0.8, 1);
        double radius = d.num("radius");
        int fuse = (int) d.num("fuse");
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer p : head(shuffle(free(enc, list)), (int) d.num("onPlayers"))) spots.add(new Vec3(p.getX(), floorY(enc), p.getZ()));
        for (int tries = 0; spots.size() < d.num("count") && tries < 60; tries++) {
            double ang = random() * Math.PI * 2;
            double r = 4 + random() * (R - 6);
            Vec3 s = new Vec3(enc.center.x + Math.cos(ang) * r, floorY(enc), enc.center.z + Math.sin(ang) * r);
            boolean near = false;
            for (Vec3 o : spots) if (hdist(o, s) < radius * 1.5) near = true;
            if (near) continue;
            spots.add(s);
        }
        List<Entity> props = new ArrayList<>();
        for (Vec3 s : spots) {
            disc(enc.dim, s, radius, fuse, ORANGE);
            BedrockMob e = BossKit.spawn(enc.dim, BOMB, s, (float) (random() * 360));
            if (e == null) continue; // 소품 없이 원만
            api.addProp(enc, e);
            props.add(e);
        }
        M m = mem(enc);
        if (m != null) m.bombs.add(new Bomb(now + fuse, spots, props));
        api.cast(enc, "bombs", 14);
    }

    static void explode(Bosses.Enc enc, Bomb b) {
        Obj d = enc.def.obj("bombs");
        double radius = d.num("radius");
        for (Entity e : b.props()) enc.api.dropProp(enc, e);
        for (Vec3 s : b.spots()) {
            SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(s.x, s.y + 0.6, s.z), Opt.of("dir", new Vec3(0, 0, 1), "size", radius, "life", 0.4, "color", ORANGE,
                    "alpha", 0.95));
            SkillFx.fx(enc.dim, "rpg:shock_ring", new Vec3(s.x, s.y + 0.1, s.z), Opt.of("dir", Vec3.ZERO, "size", radius / 2.1, "life", 0.4, "color", ORANGE,
                    "alpha", 0.9));
            play(enc.dim, "random.explode", s, 1.1, 0.9);
        }
        for (ServerPlayer p : fighters(enc)) {
            Vec3 hit = null;
            for (Vec3 s : b.spots()) {
                if (hdist(p.position(), s) <= radius && Math.abs(p.getY() - s.y) < 3) {
                    hit = s;
                    break;
                }
            }
            if (hit == null) continue;
            double dx = p.getX() - hit.x;
            double dz = p.getZ() - hit.z;
            double L = Math.hypot(dx, dz);
            if (L == 0) L = 1;
            bossHit(enc, p, d.num("damage"), Opt.of("cause", "entityExplosion", "knock", knock((dx / L) * 1.1, (dz / L) * 1.1, 0.45)));
        }
    }

    // ------------------------------------------------------------ 짚단 감옥

    public static void prison(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        Obj d = enc.def.obj("prison");
        Bosses.Api api = enc.api;
        Vec3 b = enc.boss.position();
        ServerPlayer tgt = null;
        double far = -1;
        for (ServerPlayer p : list) {
            double k = hdist(p.position(), b);
            if (k > far) {
                far = k;
                tgt = p;
            }
        }
        if (tgt == null) return;
        api.anim(enc, "point");
        sound(enc, "mob.evocation_illager.prepare_attack", b, 0.8, 1);
        showFaceText(tgt, "§6§l짚단 감옥이 노립니다!", Opt.of("dist", 2.2, "up", 0.3));
        ServerPlayer target = tgt;
        int hold = (int) d.num("hold");
        // 노린 사람을 따라다니는 금빛 원 (피할 수 없는 기믹: 갇히면 동료가 부숨)
        Bosses.StepFn mark = (e, t) -> {
            if (!target.isRemoved()) disc(e.dim, new Vec3(target.getX(), floorY(e), target.getZ()), 1.3, 6, GOLD, Opt.of("fill", false));
        };
        mark.run(enc, now);
        api.cast(enc, "prison", 26, step(5, mark), step(10, mark), step(15, mark), step(20, (e, t) -> {
            if (!okTarget(target) || !fighters(e).contains(target)) return;
            BedrockMob cage = BossKit.spawn(e.dim, CAGE, target.position(), target.getYRot());
            if (cage == null) return;
            api.own(cage);
            Status.root(target, hold, "짚단 감옥");
            M m = mem(e);
            if (m != null) m.cage = new Cage(target, cage, Tasks.now() + hold);
            say(e, "§6[보스] §e" + target.getName().getString() + "§f님이 짚단 감옥에 갇혔습니다! §7감옥을 부수면 풀려납니다 (" + Js.str(Js.round(hold / 20.0)) + "초)");
            play(e.dim, "dig.grass", target.position(), 0.7, 1);
        }));
    }

    static void cageTick(Bosses.Enc enc, long now) {
        M m = mem(enc);
        Cage c = m == null ? null : m.cage;
        if (c == null) return;
        Obj d = enc.def.obj("prison");
        double hp = c.e instanceof LivingEntity le && !le.isRemoved() ? le.getHealth() : 0;
        if (!okTarget(c.p)) {
            endCage(enc, false);
            return;
        }
        if (hp <= 0) {
            // 부서짐: 풀려남
            say(enc, "§a[보스] " + c.p.getName().getString() + "님이 짚단 감옥에서 풀려났습니다");
            SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(c.p.getX(), c.p.getY() + 1, c.p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.6, "life", 0.35, "color",
                    GOLD, "alpha", 0.9));
            endCage(enc, false);
            return;
        }
        long left = c.until - now;
        if (left > 0) {
            if (left % 20 == 0) showFaceText(c.p, "§6감옥 §f" + (long) Math.ceil(left / 20.0), Opt.of("dist", 2.2, "up", -0.3));
            return;
        }
        // 시간 끝: 불타며 큰 피해
        SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(c.p.getX(), c.p.getY() + 1, c.p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.0, "life", 0.45, "color",
                ORANGE, "alpha", 0.95));
        play(enc.dim, "mob.blaze.shoot", c.p.position(), 0.8, 1);
        bossHit(enc, c.p, d.num("damage"), Opt.of("cause", "magic")); // 마법: 갑옷 · 불 저항 물약으로 피할 수 없음
        endCage(enc, true);
    }

    static void endCage(Bosses.Enc enc, boolean burned) {
        M m = mem(enc);
        Cage c = m == null ? null : m.cage;
        if (c == null) return;
        m.cage = null;
        Status.unroot(c.p);
        enc.api.disown(c.e);
        if (!c.e.isRemoved()) c.e.discard();
    }

    // ------------------------------------------------------------ 분신

    public static void decoys(Bosses.Enc enc, long now) {
        Obj d = enc.def.obj("decoys");
        Bosses.Api api = enc.api;
        List<Vec3> spots = head(enc.feat.decoys(), (int) d.num("count"));
        if (spots.size() < 2) return;
        int real = (int) Math.floor(random() * spots.size());
        int time = (int) d.num("time");
        api.hold(enc, true);
        api.anim(enc, "vanish");
        sound(enc, "mob.evocation_illager.prepare_summon", enc.boss.position(), 0.8, 1);
        title(enc, "§6§l허수아비들 사이로!", "§e진짜는 어깨에 까마귀가 있습니다");
        say(enc, "§6[보스] §f허수아비 킹이 숨었습니다! §e진짜(어깨에 까마귀)§f를 찾아 때리세요 §7(가짜를 때리면 침묵, " + Js.str(Js.round(time / 20.0))
                + "초 안에 못 찾으면 수확의 밤)");
        M m = mem(enc);
        if (m != null) m.decoys = new Decoys(real, spots, now + 12 + time);
        hideFromSummons(enc.boss, true);
        api.cast(enc, "decoys", 12 + time + 2, step(12, (e, t) -> {
            M em = mem(e);
            Decoys dm = em == null ? null : em.decoys;
            if (dm == null) return;
            dm.shown = true;
            dm.shownAt = Tasks.now();
            for (int i = 0; i < spots.size(); i++) {
                Vec3 s = spots.get(i);
                Vec3 face = new Vec3(e.center.x, s.y, e.center.z);
                SkillFx.fx(e.dim, "rpg:bash_burst", new Vec3(s.x, s.y + 2, s.z), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.2, "life", 0.4, "color", GOLD, "alpha", 0.85));
                Vec3 at = new Vec3(s.x, s.y + 0.1, s.z);
                if (i == dm.real) {
                    BossKit.teleport(e.boss, at, face);
                    api.anim(e, "appear");
                    continue;
                }
                BedrockMob dec = BossKit.spawn(e.dim, DECOY, at, BossKit.yawTo(at, face));
                if (dec == null) continue;
                // 이름도 진짜와 같게 (애드온 dec.nameTag = boss.nameTag)
                dec.setCustomName(e.boss.getCustomName());
                dec.setCustomNameVisible(true);
                api.addProp(e.enc, dec);
                dm.props.add(dec);
                api.anim(e, "appear", dec);
            }
        }), step(12 + time, (e, t) -> decoyFail(e.enc)));
    }

    /**
     * v2.14.0 숨은 동안은 정령 (정령사 룬) 이 스스로 노리지 않게 (정령이 진짜를 찾아 주지 않게): runes.js 의 NO_AUTO_TAG 와 같은 이름표.
     * 남은 보스 엔티티는 세계를 다시 불러올 때 치우므로 표시가 남지 않음
     */
    static void hideFromSummons(Entity e, boolean on) {
        if (e == null) return;
        if (on) e.addTag(NO_AUTO);
        else e.removeTag(NO_AUTO);
    }

    static void clearDecoys(Bosses.Enc enc) {
        M m = mem(enc);
        Decoys dm = m == null ? null : m.decoys;
        if (dm == null) return;
        m.decoys = null;
        hideFromSummons(enc.boss, false);
        for (Entity e : new ArrayList<>(dm.props)) {
            if (!e.isRemoved()) {
                SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(e.getX(), e.getY() + 2, e.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.0, "life", 0.35, "color",
                        GOLD, "alpha", 0.8));
            }
            enc.api.dropProp(enc, e);
        }
    }

    /** 진짜를 찾음: 분신이 흩어지고 5초 비틀거림 (받는 피해 +50%) */
    static void decoyFound(Bosses.Enc enc, ServerPlayer p) {
        Obj d = enc.def.obj("decoys");
        clearDecoys(enc);
        enc.cast = null;
        enc.staggerUntil = Tasks.now() + (long) d.num("stagger");
        M m = mem(enc);
        if (m != null) m.staggering = true;
        enc.api.anim(enc, "stagger");
        say(enc, "§a[보스] §e" + p.getName().getString() + "§f님이 진짜를 찾았습니다! §7허수아비 킹이 비틀거립니다 (받는 피해 +" + Js.str(Js.round(d.num("staggerBonus") * 100))
                + "%, " + Js.str(Js.round(d.num("stagger") / 20)) + "초)");
        sound(enc, "random.anvil_land", enc.boss.position(), 1.2, 0.8);
    }

    /** 못 찾음: 수확의 밤 (모두 큰 피해), 가운데로 돌아옴 */
    static void decoyFail(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null || m.decoys == null) return;
        Obj d = enc.def.obj("decoys");
        clearDecoys(enc);
        title(enc, "§4§l수확의 밤", "§7진짜를 찾지 못했습니다");
        sound(enc, "mob.wither.spawn", enc.center, 1.2, 0.8);
        for (ServerPlayer p : fighters(enc)) {
            SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(p.getX(), p.getY() + 1, p.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 1.4, "life", 0.35, "color", CROW,
                    "alpha", 0.9));
            bossHit(enc, p, d.num("failDamage"), Opt.of("cause", "magic"));
        }
        BossKit.teleport(enc.boss, new Vec3(enc.center.x, enc.center.y + 1, enc.center.z));
        enc.api.anim(enc, "appear");
        enc.api.hold(enc, false);
    }

    /** 가짜를 때림: 그 사람 침묵 + 작은 피해, 가짜는 흩어짐 */
    static void decoyWrong(Bosses.Enc enc, Entity prop, ServerPlayer p) {
        Obj d = enc.def.obj("decoys");
        M m = mem(enc);
        if (m == null || m.decoys == null) return;
        SkillFx.fx(enc.dim, "rpg:bash_burst", new Vec3(prop.getX(), prop.getY() + 2, prop.getZ()), Opt.of("dir", new Vec3(0, 0, 1), "size", 2.2, "life", 0.35, "color", CROW,
                "alpha", 0.9));
        m.decoys.props.remove(prop);
        enc.api.dropProp(enc, prop);
        Status.silence(p, (int) d.num("silence"));
        bossHit(enc, p, d.num("wrongDamage"), Opt.of("cause", "magic"));
        showFaceText(p, "§5§l가짜였습니다!", Opt.of("dist", 2.2, "up", 0.4));
        play(enc.dim, "mob.witch.celebrate", p.position(), 0.8, 1);
    }

    // ------------------------------------------------------------ 수확의 낫 (즉사)

    public static void reaping(Bosses.Enc enc, long now) {
        Obj d = enc.def.obj("reaping");
        Bosses.Api api = enc.api;
        double R = Arena.arenaSize(enc.a).radius();
        List<Vec3> totems = enc.feat.totems();
        int count = (int) d.num("count");
        double safe = d.num("safe");
        api.hold(enc, true);
        BossKit.teleport(enc.boss, new Vec3(enc.center.x, enc.center.y + 1, enc.center.z));
        api.anim(enc, "raise");
        sound(enc, snd(enc, "roar"), enc.center, 0.6, 1);
        List<ServerPlayer> warned = reapWarned(enc);
        String per = Js.str(d.num("perTotem"));
        title(enc, "§4§l수확의 낫", "§e허수아비 토템 곁 금빛 원으로! §7(한 곳에 " + per + "명까지)", warned);
        say(enc, "§4[보스] 수확의 낫! §f" + Js.str(Js.round(count / 20.0)) + "초 뒤 금빛 원 밖은 즉사합니다. §7토템 하나에 " + per + "명까지 — 넘치면 그 원도 즉사 (필드 밖으로 달아나도 닿음)",
                warned);
        List<Bosses.Step> steps = new ArrayList<>();
        for (int t = 0; t < count; t += 10) {
            steps.add(step(t, (e, n) -> {
                disc(e.dim, e.center, R, 12, new double[] {0.9, 0.08, 0.06}, Opt.of("fill", false, "alpha", 0.5));
                for (Vec3 s : totems) disc(e.dim, s, safe, 12, GOLD, Opt.of("fill", false, "alpha", 0.95));
            }));
        }
        for (int k = 5; k >= 1; k--) {
            int t = count - k * 20;
            int kk = k;
            if (t >= 0) steps.add(step(t, (e, n) -> faceAll(e, "§4§l" + kk, Opt.of("dist", 2.4, "up", 0.5), reapWarned(e.enc))));
        }
        steps.add(step(count, (e, n) -> reapJudge(e.enc)));
        steps.add(step(count + 20, (e, n) -> api.hold(e, false)));
        api.cast(enc, "reaping", count + 22, steps);
    }

    /**
     * 낫이 닿는 사람: 필드 둘레 (+8칸) 모두 + 최근 30초 안에 보스를 때린 사람은 64칸 안 어디든 (필드 밖으로 달아나 피할 수 없게).
     * 때린 뒤 쓰러진 사람은 다시 때리기 전까지 빠짐 (멀리서 다시 살아나 모르는 채 또 죽지 않게). 경고도 이 사람들에게 모두 감
     */
    static List<ServerPlayer> reapTargets(Bosses.Enc enc) {
        Map<UUID, ServerPlayer> who = new LinkedHashMap<>();
        for (ServerPlayer p : Arena.playersIn(enc.a, 8)) who.put(p.getUUID(), p);
        long now = Tasks.now();
        for (Map.Entry<UUID, Bosses.Part> e : enc.parts.entrySet()) {
            Bosses.Part part = e.getValue();
            ServerPlayer p = part.p;
            if (p == null || p.isRemoved()) continue;
            if (now - part.last <= 600 && part.last > part.diedAt && NpcData.dimId(p.level()).equals(enc.a.dim) && hdist(p.position(), enc.center) <= 64) {
                who.put(e.getKey(), p);
            }
        }
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : who.values()) if (okTarget(p)) out.add(p);
        return out;
    }

    /** 경고를 받을 사람: 필드 둘레 + 낫이 닿는 사람 */
    static List<ServerPlayer> reapWarned(Bosses.Enc enc) {
        Map<UUID, ServerPlayer> who = new LinkedHashMap<>();
        for (ServerPlayer p : Arena.playersIn(enc.a, 8)) who.put(p.getUUID(), p);
        for (ServerPlayer p : reapTargets(enc)) who.put(p.getUUID(), p);
        return new ArrayList<>(who.values());
    }

    static void reapJudge(Bosses.Enc enc) {
        Obj d = enc.def.obj("reaping");
        List<Vec3> totems = enc.feat.totems();
        double safe = d.num("safe");
        List<List<ServerPlayer>> groups = new ArrayList<>();
        for (int i = 0; i < totems.size(); i++) groups.add(new ArrayList<>());
        List<ServerPlayer> doomed = new ArrayList<>();
        for (ServerPlayer p : reapTargets(enc)) {
            int at = -1;
            for (int i = 0; i < totems.size(); i++) {
                if (hdist(p.position(), totems.get(i)) <= safe + 0.3) {
                    at = i;
                    break;
                }
            }
            if (at < 0) doomed.add(p);
            else groups.get(at).add(p);
        }
        for (int i = 0; i < groups.size(); i++) {
            List<ServerPlayer> g = groups.get(i);
            if (g.size() > d.num("perTotem")) {
                doomed.addAll(g);
                say(enc, "§4[보스] 토템 " + (i + 1) + "에 " + g.size() + "명 — 제물이 넘쳤습니다");
            }
        }
        // 낫 한 바퀴
        double R = Arena.arenaSize(enc.a).radius();
        for (int i = 0; i < 12; i++) {
            double a = (i / 12.0) * Math.PI * 2;
            SkillFx.fx(enc.dim, "rpg:slash_heavy_r_glow", new Vec3(enc.center.x + Math.cos(a) * R * 0.5, enc.center.y + 1, enc.center.z + Math.sin(a) * R * 0.5),
                    Opt.of("dir", new Vec3(-Math.sin(a), 0, Math.cos(a)), "speed", 0.0, "spin", 0.0, "size", R * 0.35, "life", 0.3, "color",
                            new double[] {0.9, 0.1, 0.08}, "alpha", 0.95));
        }
        sound(enc, "mob.wither.break_block", enc.center, 0.7, 1);
        for (ServerPlayer p : doomed) {
            showFaceText(p, "§4§l즉사", Opt.of("dist", 2.2, "up", 0.3));
            Status.kill(p, enc.boss);
        }
    }

    // ------------------------------------------------------------ 모듈

    /** 원거리 견제 (v2.3.0): 분신 동안 (숨어 있음) · 수확의 낫 · 짚단 감옥 (갇힌 사람을 구해야 함) · 비틀거리는 동안은 쉼 */
    @Override
    public boolean farOk(Bosses.Unit u, long now) {
        M m = mem(u);
        if (m == null) return true;
        String cast = u.cast == null ? null : u.cast.id;
        return m.decoys == null && !m.staggering && m.cage == null && !"reaping".equals(cast) && !"prison".equals(cast);
    }

    @Override
    public void start(Bosses.Enc enc) {
        long now = Tasks.now();
        M m = new M();
        m.next.put("crows", now + 120);
        m.next.put("bombs", now + 220);
        m.next.put("prison", now + 160);
        enc.mem = m;
    }

    @Override
    public boolean pick(Bosses.Unit u, long now, List<ServerPlayer> list) {
        Bosses.Enc enc = u.enc;
        M m = mem(enc);
        if (m == null) return false;
        Obj d = enc.def;
        String forced = forceNext;
        if (forced != null) {
            forceNext = null;
            switch (forced) {
                case "crows" -> crows(enc, now, list);
                case "bombs" -> bombs(enc, now, list);
                case "prison" -> prison(enc, now, list);
                case "decoys" -> decoys(enc, now);
                case "reaping" -> reaping(enc, now);
                default -> {
                    return false;
                }
            }
            return true;
        }
        double r = enc.api.ratio(enc);
        for (Double at : d.obj("decoys").numbersOrNull("at")) {
            if (r <= at && !m.decoyDone.contains(at)) {
                m.decoyDone.add(at);
                decoys(enc, now);
                return true;
            }
        }
        // 감옥에 갇힌 사람이 있으면 기다림 (갇힌 채로는 토템까지 갈 수 없음)
        Obj reap = d.obj("reaping");
        if (r <= reap.num("from") && m.cage == null && (m.reapAt == null || now >= m.reapAt)) {
            m.reapAt = now + (long) reap.num("every");
            reaping(enc, now);
            return true;
        }
        List<String> ready = new ArrayList<>();
        if (now >= m.next.get("crows")) ready.add("crows");
        if (now >= m.next.get("bombs")) ready.add("bombs");
        if (r <= d.obj("prison").num("from") && now >= m.next.get("prison") && m.cage == null && !list.isEmpty()) ready.add("prison");
        if (ready.isEmpty()) return false;
        String id = ready.get((int) Math.floor(random() * ready.size()));
        double[] every = BossKit.pair(d.obj(id), "every");
        m.next.put(id, now + rnd((int) every[0], (int) every[1]));
        if (id.equals("crows")) crows(enc, now, list);
        else if (id.equals("bombs")) bombs(enc, now, list);
        else prison(enc, now, list);
        return true;
    }

    /** 받는 피해: 분신 중 진짜를 때리면 찾은 것, 비틀거리는 동안 +50% */
    @Override
    public Bosses.Hurt hurt(Bosses.Unit u, ServerPlayer p, double amount, Bosses.Hit h) {
        Bosses.Enc enc = u.enc;
        Obj d = enc.def.obj("decoys");
        M m = mem(enc);
        if (m != null && m.decoys != null) {
            if (!m.decoys.shown) return Bosses.Hurt.of(0); // 숨는 중 (분신이 나오기 전)
            decoyFound(enc, p);
            return Bosses.Hurt.of(amount * (1 + d.num("staggerBonus")));
        }
        return Tasks.now() < enc.staggerUntil ? Bosses.Hurt.of(amount * (1 + d.num("staggerBonus"))) : null;
    }

    @Override
    public void propHit(Bosses.Enc enc, Entity prop, ServerPlayer p, double amount, double left) {
        if (Kit.isType(prop, DECOY)) decoyWrong(enc, prop, p);
    }

    @Override
    public void tick(Bosses.Enc enc, long now, List<ServerPlayer> list) {
        M m = mem(enc);
        if (m == null) return;
        cageTick(enc, now);
        List<Bomb> due = new ArrayList<>();
        List<Bomb> keep = new ArrayList<>();
        for (Bomb b : m.bombs) (now >= b.at() ? due : keep).add(b);
        if (!due.isEmpty()) {
            m.bombs = keep;
            for (Bomb b : due) explode(enc, b);
        }
        if (m.staggering && now >= enc.staggerUntil) {
            m.staggering = false;
            enc.api.hold(enc, false);
        }
        // 분신 중: 진짜 어깨의 까마귀가 가끔 날갯짓 (알아볼 단서). 나타난 뒤 1초부터 (사라지기 · 나타나기 동작을 덮지 않게)
        Decoys dm = m.decoys;
        if (dm != null && dm.shown && now - dm.shownAt >= 20 && now - m.tell >= 40) {
            m.tell = now;
            enc.api.anim(enc, "tell");
            Vec3 l = enc.boss.position();
            double h = bossSize(enc.def).height() * 0.8; // 어깨 높이
            play(enc.dim, "mob.parrot.idle", l, 0.6, 0.5);
            // 깃털 까마귀 둘이 어깨에서 날아오름 (멀리서도 보이는 단서)
            for (int i = 0; i < 2; i++) {
                double a = random() * Math.PI * 2;
                SkillFx.fx(enc.dim, "rpg:crow", new Vec3(l.x, l.y + h, l.z), Opt.of("dir", new Vec3(Math.cos(a) * 0.6, 0.8, Math.sin(a) * 0.6), "speed", 3.0, "size", 0.45,
                        "life", 0.9, "delay", i * 0.15, "color", WHITE, "alpha", 1.0));
            }
        }
    }

    @Override
    public void end(Bosses.Enc enc) {
        M m = mem(enc);
        if (m == null) return;
        endCage(enc, false);
        clearDecoys(enc);
        for (Bomb b : m.bombs) for (Entity e : b.props()) enc.api.dropProp(enc, e);
        m.bombs = new ArrayList<>();
    }
}
