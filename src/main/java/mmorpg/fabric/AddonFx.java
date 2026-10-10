package mmorpg.fabric;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 애드온 파티클 보내기 (베드락 dimension.spawnParticle(id, 자리, MolangVariableMap) 의 자바 판).
 * 변수 이름은 애드온과 같음 (variable. 뒤): dir · speed · spin · size · life · thick · delay · grow · progress · color (r g b a) ·
 * move · vel · spin_rate · cell · frames · fps · loop · phase · fade · … (AddonFxTable.VARS — 어느 파티클 식에도 없는 이름은 버림).
 * skills.js fx() · runes.js rfx() 와 같은 기본값을 넣는 도우미 (fx · rfx) 도 있음.
 */
public final class AddonFx {
    private AddonFx() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-addon-fx");
    private static final Set<String> WARNED = new HashSet<>();
    public static final Vec3 UP = new Vec3(0, 1, 0);

    /** 변수 묶음 (MolangVariableMap) */
    public static final class Vars {
        private int[] slots = new int[24];
        private float[] values = new float[24];
        private int n;

        public Vars set(String name, double value) {
            int slot = AddonFxTable.var(name);
            if (slot < 0) return this;
            for (int i = 0; i < n; i++) {
                if (slots[i] == slot) {
                    values[i] = (float) value;
                    return this;
                }
            }
            if (n == slots.length) {
                slots = java.util.Arrays.copyOf(slots, n * 2);
                values = java.util.Arrays.copyOf(values, n * 2);
            }
            slots[n] = slot;
            values[n++] = (float) value;
            return this;
        }

        /** setVector3: name.x · name.y · name.z */
        public Vars vec(String name, Vec3 v) {
            return set(name + ".x", v.x).set(name + ".y", v.y).set(name + ".z", v.z);
        }

        /** setColorRGBA: name.r · .g · .b · .a */
        public Vars color(String name, double[] rgb, double alpha) {
            return set(name + ".r", rgb[0]).set(name + ".g", rgb[1]).set(name + ".b", rgb[2]).set(name + ".a", alpha);
        }

        AddonParticleOptions options(int particle) {
            return new AddonParticleOptions(particle, java.util.Arrays.copyOf(slots, n), java.util.Arrays.copyOf(values, n));
        }
    }

    public static Vars vars() {
        return new Vars();
    }

    private static int index(String id) {
        int p = AddonFxTable.particle(id);
        if (p < 0 && WARNED.add(id)) LOG.warn("[mmorpg] unknown add-on particle {}", id);
        return p;
    }

    /** 파티클 하나 (모두에게 — 거리 · 파티클 설정 무시) */
    public static void spawn(ServerLevel level, String id, Vec3 at, Vars vars) {
        int p = index(id);
        if (p < 0) return;
        level.sendParticles(vars.options(p), true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    /** 그 플레이어에게만 보이는 파티클 (player.spawnParticle) */
    public static void spawnFor(ServerPlayer sp, String id, Vec3 at, Vars vars) {
        int p = index(id);
        if (p < 0) return;
        sp.level().sendParticles(sp, vars.options(p), true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------ skills.js fx() 의 변수 (dir · speed · spin · size · life · thick · delay · grow · progress · color)

    /** skills.js fx 의 o: 기본 dir 위 · 나머지 0 · 흰색 · 불투명 */
    public static final class Args {
        public Vec3 dir = UP;
        public double speed;
        public double spin;
        public double size;
        public double life;
        public double thick;
        public double delay;
        public double grow;
        public double progress;
        public double[] color = {1, 1, 1};
        public double alpha = 1;

        public Args dir(Vec3 v) {
            dir = v;
            return this;
        }

        public Args speed(double v) {
            speed = v;
            return this;
        }

        public Args spin(double v) {
            spin = v;
            return this;
        }

        public Args size(double v) {
            size = v;
            return this;
        }

        public Args life(double v) {
            life = v;
            return this;
        }

        public Args thick(double v) {
            thick = v;
            return this;
        }

        public Args delay(double v) {
            delay = v;
            return this;
        }

        public Args grow(double v) {
            grow = v;
            return this;
        }

        public Args progress(double v) {
            progress = v;
            return this;
        }

        public Args color(double[] rgb) {
            color = rgb;
            return this;
        }

        public Args alpha(double v) {
            alpha = v;
            return this;
        }

        public Args copy() {
            Args a = new Args();
            a.dir = dir;
            a.speed = speed;
            a.spin = spin;
            a.size = size;
            a.life = life;
            a.thick = thick;
            a.delay = delay;
            a.grow = grow;
            a.progress = progress;
            a.color = color;
            a.alpha = alpha;
            return a;
        }

        Vars vars() {
            return new Vars().vec("dir", dir).set("speed", speed).set("spin", spin).set("size", size).set("life", life).set("thick", thick)
                    .set("delay", delay).set("grow", grow).set("progress", progress).color("color", color, alpha);
        }
    }

    public static Args args() {
        return new Args();
    }

    /** skills.js fx(dim, id, at, o) */
    public static void fx(ServerLevel level, String id, Vec3 at, Args o) {
        spawn(level, id, at, o.vars());
    }

    /** fx 에 변수를 더 붙임 (o 의 변수 + extra) */
    public static void fx(ServerLevel level, String id, Vec3 at, Args o, java.util.function.Consumer<Vars> extra) {
        Vars v = o.vars();
        extra.accept(v);
        spawn(level, id, at, v);
    }

    // ------------------------------------------------------------ runes.js rfx() (rpg:rfx_<name>)

    /** runes.js rfx 의 o */
    public static final class Rfx {
        public Vec3 dir = UP;
        public double size;
        public double life;
        public double[] color = {1, 1, 1};
        public double alpha = 1;
        public double spin;
        public double spinRate;
        public double speed;
        public double grow;
        public double cell;
        public double frames = 1;
        public double fps;
        public boolean loop;
        public double phase;
        public double fade;
        public Vec3 vel = Vec3.ZERO;

        public Rfx copy() {
            Rfx r = new Rfx();
            r.dir = dir;
            r.size = size;
            r.life = life;
            r.color = color;
            r.alpha = alpha;
            r.spin = spin;
            r.spinRate = spinRate;
            r.speed = speed;
            r.grow = grow;
            r.cell = cell;
            r.frames = frames;
            r.fps = fps;
            r.loop = loop;
            r.phase = phase;
            r.fade = fade;
            r.vel = vel;
            return r;
        }
    }

    /** runes.js rfx(dim, name, at, o): 움직임 = dir × speed + vel (그림 방향 dir 과 따로 variable.move · speed) */
    public static void rfx(ServerLevel level, String name, Vec3 at, Rfx o) {
        Vec3 d = o.dir == null ? UP : o.dir;
        Vec3 mv = d.scale(o.speed).add(o.vel == null ? Vec3.ZERO : o.vel);
        double ms = mv.length();
        Vars v = new Vars().vec("dir", d).set("size", o.size).set("life", o.life).color("color", o.color, o.alpha).set("spin", o.spin)
                .set("speed", ms).set("grow", o.grow);
        v.vec("move", ms > 1e-6 ? mv.scale(1 / ms) : d);
        v.vec("vel", o.vel == null ? Vec3.ZERO : o.vel);
        v.set("spin_rate", o.spinRate).set("cell", o.cell).set("frames", o.frames).set("fps", o.fps).set("loop", o.loop ? 1 : 0)
                .set("phase", o.phase).set("fade", o.fade);
        spawn(level, "rpg:rfx_" + name, at, v);
    }
}
