package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.rpgui.RpgUi;
import mmorpg.fabric.smith.SmithNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 0.45.0 룬술사 창의 룬 공방 패널: 남보라 돌벽 · 달밤 창 · 책과 수정 선반, 룬술사 (오른쪽 받침 쪽을 봄) 와 룬 받침 — 고른 룬이 받침 위에 떠서 천천히 돎,
 * 바닥 마법진이 은은히 빛남.
 * <ul>
 *   <li>갈기 (grind): 마법진이 빨리 돌며 밝아짐 → 기운을 밀어 넣을 때마다 룬이 부풀고 빛 조각 · 금 (세 번째에 깨져 조각이 사방으로)</li>
 *   <li>돌파 (merge): 같은 룬 둘이 양쪽에서 다가와 두 번 부딪혀 번쩍 → 하나</li>
 *   <li>뽑기권 (ticket): 룬 조각이 둘레에서 돌며 모여 들다 번쩍 → 뽑기권</li>
 * </ul>
 */
final class RuneScene extends WorkScene {
    // 패널 안 자리 (tools/workshop_art.py 의 RUNE 과 같은 값 — 손이 받침 위 룬 앞에 닿게)
    static final int SMITH_X = 36;
    static final int FEET_Y = 168;
    static final float FACE = -70F;
    static final float CAM = -8F;
    static final int ALTAR_X = 54;
    static final int ALTAR_Y = 122;
    static final int ALTAR_W = 40;
    static final int ALTAR_H = 50;
    /** 룬이 떠 있는 곳 · 바닥 마법진 가운데 */
    static final int RUNE_X = 74;
    static final int RUNE_Y = 104;
    static final int CIRCLE_X = 74;
    static final int CIRCLE_Y = 170;

    static final Identifier BACKDROP = Mmorpg.id("smith/rune_backdrop");
    static final Identifier ALTAR = Mmorpg.id("smith/altar");
    private static final int PURPLE = 0xB46CFF;
    private static final int CYAN = 0x78E8FF;

    private final List<Spark> motes = new ArrayList<>();
    private long nextMote;
    private long nextChime;
    private ItemStack shard = ItemStack.EMPTY;

    @Override
    float npcX() {
        return SMITH_X;
    }

    @Override
    float feetY() {
        return FEET_Y;
    }

    @Override
    float face() {
        return FACE;
    }

    @Override
    float cam() {
        return CAM;
    }

    @Override
    String idleMotion() {
        return "rune_channel";
    }

    @Override
    String roleName() {
        return "룬술사";
    }

    @Override
    String busyLabel(String fx) {
        return switch (fx) {
            case "merge" -> "합치는 중…";
            case "ticket" -> "빚는 중…";
            default -> "가는 중…";
        };
    }

    @Override
    float resultX() {
        return RUNE_X;
    }

    @Override
    float resultY() {
        return RUNE_Y;
    }

    private ItemStack shard() {
        if (shard.isEmpty()) shard = stack("rpg:rune_shard");
        return shard;
    }

    @Override
    void onHit(int i, boolean last, ClientSmith.Work w) {
        int rgb = RpgUi.gradeRgb(w == null ? 0 : w.grade());
        long now = Util.getMillis();
        switch (w == null ? "grind" : w.fx()) {
            case "merge" -> {
                // 부딪힘: 가운데서 하늘빛 · 별 조각
                burst(RUNE_X, RUNE_Y, last ? 26 : 14, 30F, last ? 120F : 80F, last ? 700 : 450, new int[] {CYAN, 0xFFFFFF, rgb}, 30F, 0, 360);
                shake(now, last ? 160 : 90);
            }
            case "ticket" -> {
                burst(RUNE_X, RUNE_Y, last ? 22 : 10, 20F, last ? 90F : 50F, last ? 650 : 400, new int[] {0xFFD24A, PURPLE, 0xFFF0C0}, 20F, 0, 360);
                if (last) shake(now, 120);
            }
            default -> {
                // 기운: 보랏빛 조각 (마지막은 깨져 크게 — 아래로 떨어짐)
                burst(RUNE_X, RUNE_Y, last ? 30 : 10, last ? 40F : 25F, last ? 140F : 70F, last ? 750 : 420, new int[] {PURPLE, rgb, 0xFFFFFF}, last ? 160F : 40F, 0,
                        360);
                shake(now, last ? 170 : 80);
            }
        }
    }

    /** 패널 뒤 (룬술사보다 아래): 배경 · 떠오르는 빛 알갱이 · 바닥 마법진 · 받침 · 받침 위의 룬 */
    @Override
    void back(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, SmithNet.Entry sel, int x0, int y0, long now, float partial) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKDROP, x0, y0, SmithScreen.SW, SmithScreen.SH);
        ClientSmith.Work w = ClientSmith.work;
        double prog = w != null ? Math.max(0, ClientSmith.progress(partial)) : -1;
        boolean working = w != null && prog < 1;
        String fx = w != null ? w.fx() : "";
        float sec = now / 1000F;
        int col = w != null ? RpgUi.gradeRgb(w.grade()) : sel != null ? RpgUi.gradeRgb(RpgUi.grade(sel.icon())) : PURPLE;
        // 바닥 마법진: 쉴 때는 천천히 · 일할 때는 빨리 돌며 밝게
        float spin = working ? sec * 2.4F : sec * 0.35F;
        int ca = working ? (int) (120 + 110 * Math.min(1, prog * 2)) : (int) (70 + 20 * Math.sin(sec * 1.6));
        WorkScene.flat(g, GachaArt.CIRCLE[9], x0 + CIRCLE_X, y0 + CIRCLE_Y, 62F, 0.28F, spin, GachaArt.argb(ca, working && fx.equals("ticket") ? 0xFFD24A : col));
        WorkScene.flat(g, GachaArt.GLOW, x0 + CIRCLE_X, y0 + CIRCLE_Y, 70F, 0.3F, 0F, GachaArt.argb(working ? 120 : 50, PURPLE));
        // 빛 알갱이 (마법진에서 위로)
        if (nextMote < now - 1000) nextMote = now;
        while (nextMote <= now) {
            motes.add(new Spark(CIRCLE_X - 22 + rnd.nextFloat() * 44, CIRCLE_Y - 4 + rnd.nextFloat() * 6, (rnd.nextFloat() - 0.5F) * 6F, -10F - rnd.nextFloat() * 14F,
                    nextMote, 1400 + rnd.nextInt(1000), rnd.nextFloat() < 0.5F ? PURPLE : CYAN, 1F, 0F));
            nextMote += (working ? 50 : 160) + rnd.nextInt(working ? 60 : 200);
        }
        motes.removeIf(p -> now - p.born() > p.life());
        for (Spark p : motes) {
            float t = (now - p.born()) / 1000F;
            float px = p.x() + p.vx() * t + (float) Math.sin(t * 3 + p.born() % 11) * 2F;
            float py = p.y() + p.vy() * t;
            float a = 1F - (now - p.born()) / (float) p.life();
            if (py < 12) continue;
            int alpha = (int) (220 * a * (0.6 + 0.4 * Math.sin(now / 90.0 + p.born())));
            g.fill(x0 + Math.round(px), y0 + Math.round(py), x0 + Math.round(px) + 1, y0 + Math.round(py) + 1, GachaArt.argb(alpha, p.rgb()));
        }
        // 받침
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ALTAR, x0 + ALTAR_X, y0 + ALTAR_Y, ALTAR_W, ALTAR_H);
        // 받침 위의 빛 (룬 뒤)
        float bob = (float) Math.sin(sec * 2.2) * 2F;
        GachaArt.sprite(g, GachaArt.GLOW, x0 + RUNE_X, y0 + RUNE_Y + bob, working ? 40F + 14F * (float) prog : 34F, 0F, GachaArt.argb(working ? 170 : 110, col));
        if (working && !fx.equals("merge")) {
            // 룬 앞에 선 마법진 (돎 — 일하는 동안)
            float k = clamp01(prog * 3);
            GachaArt.sprite(g, GachaArt.CIRCLE[Math.min(9, (int) (k * 9))], x0 + RUNE_X, y0 + RUNE_Y + bob, 30F + 6F * k, sec * 3F,
                    GachaArt.argb((int) (150 * k), fx.equals("ticket") ? 0xFFD24A : col));
        }
    }

    /** 패널 앞: 떠 있는 룬 (일 · 결과에 따라) · 튀는 조각 */
    @Override
    void front(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, int x0, int y0, long now, float partial) {
        ClientSmith.Work w = ClientSmith.work;
        SmithNet.Result r = ClientSmith.result;
        double prog = w != null ? Math.max(0, ClientSmith.progress(partial)) : -1;
        boolean working = w != null && prog < 1;
        boolean showResult = r != null && now - ClientSmith.resultAt < SmithScreen.RESULT_MS;
        float sec = now / 1000F;
        float bob = (float) Math.sin(sec * 2.2) * 2F;
        float cx = x0 + RUNE_X;
        float cy = y0 + RUNE_Y + bob;
        SmithNet.Entry sel = s.selectedEntry();
        if (working) {
            int hits = ClientSmith.hits();
            int n = w.impacts().size();
            long dt = now - ClientSmith.hitAt;
            float pulse = ClientSmith.hitAt > 0 && dt < 220 ? 1F - dt / 220F : 0F;
            switch (w.fx()) {
                case "merge" -> {
                    // 둘이 양쪽에서 다가옴 (첫 부딪힘까지) → 튕김 → 다시 → 합침
                    double t0 = n > 0 ? w.impacts().get(0) / (double) w.ticks() : 0.5;
                    double gap;
                    if (hits == 0) gap = 1 - clamp01(prog / t0);
                    else if (hits < n) gap = 0.35 * Math.sin(Math.PI * clamp01((prog - t0) / Math.max(0.05, (w.impacts().get(n - 1) / (double) w.ticks()) - t0)));
                    else gap = 0;
                    float d = (float) (6 + 22 * gap);
                    float spin = sec * 4F;
                    if (hits < n) {
                        GachaArt.sprite(g, GachaArt.GLOW, cx - d, cy, 22F, 0F, GachaArt.argb(140, CYAN));
                        GachaArt.sprite(g, GachaArt.GLOW, cx + d, cy, 22F, 0F, GachaArt.argb(140, CYAN));
                        GachaArt.item(g, w.item(), cx - d, cy + (float) Math.sin(spin) * 2F, 0.95F);
                        GachaArt.item(g, w.item(), cx + d, cy - (float) Math.sin(spin) * 2F, 0.95F);
                    } else {
                        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 52F, sec, GachaArt.argb(150, CYAN));
                        GachaArt.item(g, w.item(), cx, cy, 1.25F + 0.4F * pulse);
                    }
                    if (pulse > 0) {
                        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 26F + 30F * (1 - pulse), 0F, GachaArt.argb((int) (230 * pulse), 0xFFFFFF));
                        GachaArt.sprite(g, GachaArt.RING, cx, cy, 10F + 40F * (1 - pulse), 0F, GachaArt.argb((int) (200 * pulse), CYAN));
                    }
                }
                case "ticket" -> {
                    // 조각 다섯이 둘레를 돌며 좁혀 듦 → 마지막에 뽑기권
                    if (hits < n) {
                        float rad = 22F * (1 - 0.7F * clamp01(prog));
                        for (int k = 0; k < 5; k++) {
                            double a = sec * 3.2 + k * Math.PI * 2 / 5;
                            GachaArt.item(g, shard(), cx + (float) Math.cos(a) * rad, cy + (float) Math.sin(a) * rad * 0.55F, 0.55F);
                        }
                    } else {
                        GachaArt.sprite(g, GachaArt.RAYS, cx, cy, 50F, sec, GachaArt.argb(150, 0xFFD24A));
                        GachaArt.item(g, w.item(), cx, cy, 1.2F + 0.35F * pulse);
                    }
                    if (pulse > 0) GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 20F + 26F * (1 - pulse), 0F, GachaArt.argb((int) (220 * pulse), 0xFFE6A0));
                }
                default -> {
                    // 룬이 떨며 부풀었다 돌아옴 · 금 (닿은 수만큼) → 마지막에 사라짐 (조각은 튀는 것)
                    if (hits < n) {
                        float shakeX = (float) Math.sin(now / 18.0) * (0.6F + hits * 0.5F);
                        float sc = 1.1F + 0.35F * pulse + 0.06F * hits;
                        GachaArt.item(g, w.item(), cx + shakeX, cy, sc);
                        for (int k = 0; k < hits; k++) {
                            // 금: 가운데에서 바깥으로 짧은 흰 줄
                            double a = 0.7 + k * 2.1;
                            float len = 5F + 2F * k;
                            g.pose().pushMatrix();
                            g.pose().translate(cx + shakeX, cy);
                            g.pose().rotate((float) a);
                            g.fill(0, 0, Math.round(len), 1, GachaArt.argb(230, 0xFFFFFF));
                            g.pose().popMatrix();
                        }
                    }
                    if (pulse > 0) {
                        GachaArt.sprite(g, GachaArt.GLOW, cx, cy, 24F + 26F * (1 - pulse), 0F, GachaArt.argb((int) (220 * pulse), 0xE6C8FF));
                        GachaArt.sprite(g, GachaArt.RING, cx, cy, 8F + 34F * (1 - pulse), 0F, GachaArt.argb((int) (190 * pulse), PURPLE));
                    }
                }
            }
        } else {
            // 쉴 때: 고른 것 (결과가 있으면 결과 아이템) 이 떠서 천천히 흔들림
            ItemStack item = showResult && !r.item().isEmpty() ? r.item() : sel != null ? sel.icon() : ItemStack.EMPTY;
            float pop = 1F;
            if (showResult && (r.kind().equals("success") || r.kind().equals("done"))) {
                float p = (now - ClientSmith.resultAt) / 1000F;
                pop = 1F + 0.5F * (float) Math.max(0, Math.sin(Math.min(1, p / 0.35F) * Math.PI)) * Math.max(0, 1 - p);
            }
            if (!item.isEmpty()) GachaArt.item(g, item, cx, cy, 1.1F * pop);
        }
        drawSparks(g, x0, y0, now, 0x301060);
    }

    /** 공방 소리 (가끔 맑은 울림 — 아주 작게) */
    @Override
    void ambience(long now) {
        if (now < nextChime) return;
        if (nextChime > 0) ClientSmith.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F + rnd.nextFloat() * 0.5F, 0.12F);
        nextChime = now + 2600 + rnd.nextInt(3200);
    }
}
