package mmorpg.fabric.job;

import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-14 천벌의 사수의 3D 빛의 날개 (조각 물체 angel_wing — 대천사 강림과 같은 깃털 날개): 윙 스텝 (한 쌍 — 뛰어 물러나는 동안 활짝 펴 한 번 크게 내려침) · 세라핌 (세 쌍
 * 여섯 — 위 · 가운데 · 아래, 천천히 날갯짓) · 디바인 펀니시먼트 (한 쌍 — 조준하는 동안 크게 펼침). 등 뒤 날개 뿌리에서 접힌 채 솟아 (0 ~ open 틱) 활짝 편 뒤 every 틱마다 몸을
 * 따라옴 (앞질러 옮김 — every 틱 보간). 머리 위 빛 고리 (halo — 쓰면) 도 함께. 끝나거나 죽으면 위로 접히며 작아져 사라짐
 */
final class LightWings {
    /**
     * 쌍마다: 뿌리 높이 (발에서) · 옆 · 펼친 날개 위 각 · 뒤로 젖힘 · 크기 몫. 2026-11-14 영상 (세라핌): 여섯 날개가 겹쳐 깃털 덤불처럼 보였음 → 위 쌍은 더 세우고 작게 · 아래
     * 쌍은 더 내리고 작게 (세 층이 갈라져 보이게)
     */
    private static final double[][] PAIRS = {{1.36, 0.17, 18, 12, 1.0}, {1.6, 0.15, 58, 6, 0.82}, {1.1, 0.16, -36, 16, 0.62}};

    final ServerPlayer sp;
    final Vfx.Fx fx;
    final Vfx.Group[] wings;
    final int[] pairOf;
    final double ws;
    final Vfx.Prop halo;
    final double hs;
    final int open;
    final int every;
    final int total;
    final String life;
    Vec3 last;
    boolean done;
    int beatAt = -100;
    /** 이어 둘지 (null = 끝까지 — 세라핌은 태세가 남은 동안) */
    java.util.function.BooleanSupplier keep;

    /**
     * pairs = 1 (한 쌍) · 3 (여섯), span = 펼친 날개 하나의 너비 (칸), total = 틱 (그 뒤 접힘), open = 펴는 틱, every = 따라오는 틱 (1 = 빠르게 움직일 때), halo = 머리 위 빛 고리
     * (지름 칸 — 0 이면 없음), feather · glow = 깃털 · 빛
     */
    LightWings(ServerPlayer sp, int pairs, double span, int total, int open, int every, double halo, double[] feather, double[] glow) {
        this.sp = sp;
        this.total = total;
        this.open = Math.max(2, open);
        this.every = Math.max(1, every);
        this.life = RuneKit.lifeToken(sp);
        this.fx = RuneProps.fx(sp.level(), sp.position(), total + 20);
        this.ws = RuneProps.scaleFor(span, RuneProps.ANGEL_WING_SPAN);
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        int n = Math.max(1, Math.min(3, pairs)) * 2;
        this.wings = new Vfx.Group[n];
        this.pairOf = new int[n];
        for (int i = 0; i < n; i++) {
            int p = n == 2 ? 0 : i / 2;
            pairOf[i] = p;
            ArrowFx.WingPose w = pose(c0, f0, i, 80, -40, 0);
            wings[i] = RuneProps.shape(fx, RuneProps.ANGEL_WING, w.at(), w.rot(), ws * 0.25 * PAIRS[p][4], feather, glow);
        }
        this.hs = halo > 0 ? RuneProps.scaleFor(halo, RuneProps.HALO_D) : 0;
        this.halo = halo > 0 ? RuneProps.prop(fx, RuneProps.HALO, V.up(c0, 2.35), new double[] {8, 8, 8}, ArrowFx.haloRot(f0, 0, 14), hs * 0.15, glow, RuneKit.WHITE) : null;
        this.last = c0;
        fx.each(1, total + 6, this.every, this::step);
    }

    /** 날개 i 의 자세 (c · f · 펼침 sweep · 듦 flap 은 그 쌍의 기본에 더함 · k = 0 접힘 → 1 펼침) */
    private ArrowFx.WingPose pose(Vec3 c, Vec3 f, int i, double sweep, double flap, double k) {
        double[] p = PAIRS[pairOf == null ? 0 : pairOf[i]];
        int s = i % 2 == 0 ? 1 : -1;
        double sw = k <= 0 ? sweep : sweep + p[3] * k;
        double fl = k <= 0 ? flap : flap + p[2] * k;
        return ArrowFx.wingPose(c, f, s, sw, fl, p[0], p[1]);
    }

    /** 크게 한 번 내려침 (다음 몇 틱 — 윙 스텝의 박차고 날아오름 · 쏘는 순간) */
    void beat() {
        beatAt = fx.age();
    }

    /** 지금 접음 (먼저 끝냄) */
    void end() {
        if (!done) fold(last, Kit.facingOf(sp), fx.age());
    }

    private void step(int t) {
        if (done) return;
        boolean gone = !RuneKit.sameLife(sp, life) || sp.isRemoved() || sp.level() != fx.level() || (keep != null && !keep.getAsBoolean());
        Vec3 c = gone ? last : sp.position();
        Vec3 lead = V.sub(c, last);
        last = c;
        if (V.len(lead) > 3) lead = Vec3.ZERO;
        Vec3 cc = V.add(c, lead);
        Vec3 f = Kit.facingOf(sp);
        if (gone || t >= total) {
            fold(cc, f, t);
            return;
        }
        double u = Math.min(1, t / (double) open);
        double k = RuneSwords.ease(u, "out");
        double sweep;
        double flap;
        int since = t - beatAt;
        if (since >= 0 && since < 7) {
            // 크게 내려침: 위로 치켜 (0 ~ 2) → 수평까지 내려침 (3 ~ 4) → 돌아옴. 2026-11-14 영상: −40° 까지 내리면 날개가 땅에 눕듯 앞으로 엎어져 보였음 → −20°
            double q = since / 7.0;
            flap = since < 3 ? 24 : since < 5 ? -20 : -8 * (1 - q);
            sweep = since < 3 ? 4 : since < 5 ? -6 : 6;
        } else {
            // 천천히 날갯짓 (24틱 한 번)
            double ph = 2 * Math.PI * (t % 24) / 24.0;
            flap = -14 * Math.cos(ph) * k;
            sweep = 6 + 5 * Math.cos(ph);
        }
        double base = 80 * (1 - k);
        double lift = -40 * (1 - k);
        for (int i = 0; i < wings.length; i++) {
            ArrowFx.WingPose w = pose(cc, f, i, base + sweep * k, lift + flap, k);
            wings[i].to(w.at(), w.rot(), ws * PAIRS[pairOf[i]][4] * (0.25 + 0.75 * k), every);
        }
        if (halo != null) {
            double hk = t < 3 ? 0.15 : Math.min(1, 0.15 + 0.85 * (t - 3) / 5.0);
            halo.to(V.add(V.up(cc, 2.35), f, -0.08), ArrowFx.haloRot(f, t, 14), hs * hk, every);
        }
    }

    /** 접힘: 위 뒤로 모이며 작아짐 → 지움 */
    private void fold(Vec3 cc, Vec3 f, int t) {
        done = true;
        for (int i = 0; i < wings.length; i++) {
            ArrowFx.WingPose w = pose(cc, f, i, 70, 55, 0);
            wings[i].to(V.up(w.at(), 0.4), w.rot(), ws * 0.12, 6);
        }
        if (halo != null) halo.to(V.up(cc, 2.7), ArrowFx.haloRot(f, t, 14), hs * 0.1, 6);
        fx.keep(10);
        fx.on(t + 7, () -> {
            for (Vfx.Group x : wings) x.remove();
            if (halo != null) halo.remove();
        });
    }
}
