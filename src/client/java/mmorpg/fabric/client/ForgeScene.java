package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.rpgui.RpgUi;
import mmorpg.fabric.smith.SmithNet;
import mmorpg.fabric.smith.SmithUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 0.44.0 대장장이 창의 대장간 패널 (0.45.0 에 SmithScreen 에서 옮김): 돌벽 · 화덕 불 · 불씨, 대장장이 (대장장이 망치를 듦) 와 모루 (고른 것이 올라감 —
 * 무기는 눕히고 그 밖은 세움). 망치가 닿을 때 불꽃 · 번쩍임 · 흔들림.
 */
final class ForgeScene extends WorkScene {
    // 패널 안 자리 (패널 기준). 대장장이는 1칸 = FIG 로, 모루는 1칸 높이 (세상의 모루와 같은 비율) — 망치질 (poses.json smith_hammer) 의 망치 치는 면이
    // 이 카메라로 모루 윗면 (HIT) 에 닿게 맞춤 (tools/smith_art.py 의 panel_layout 과 같은 계산)
    static final int SMITH_X = 32;
    static final int FEET_Y = 168;
    static final float FACE = -70F;
    static final float CAM = -8F;
    static final int ANVIL_X = 55;
    static final int ANVIL_Y = 125;
    static final int ANVIL_W = 58;
    static final int ANVIL_H = 48;
    /** 모루 윗면의 망치가 닿는 곳 · 올린 것이 놓이는 곳 */
    static final int HIT_X = 79;
    static final int HIT_Y = 127;
    static final int ITEM_X = 79;
    static final int ITEM_Y = 125;
    static final int FORGE_X = 80;
    static final int FORGE_Y = 106;

    static final Identifier BACKDROP = Mmorpg.id("smith/backdrop");
    static final Identifier ANVIL = Mmorpg.id("smith/anvil");
    static final String HAMMER = "rpg:smith_hammer";

    private final List<Spark> embers = new ArrayList<>();
    private long nextEmber;
    private long nextCrackle;

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
        return SmithUi.MOTION;
    }

    @Override
    String hold() {
        return HAMMER;
    }

    @Override
    String roleName() {
        return "대장장이";
    }

    @Override
    String busyLabel(String fx) {
        return "망치질 중…";
    }

    @Override
    float resultX() {
        return ITEM_X;
    }

    @Override
    float resultY() {
        return ITEM_Y;
    }

    @Override
    void onHit(int i, boolean last, ClientSmith.Work w) {
        int rgb = RpgUi.gradeRgb(w == null ? 0 : w.grade());
        burst(HIT_X, HIT_Y, last ? 24 : 15, 35F, last ? 130F : 105F, last ? 560 : 440, new int[] {0xFFE6A0, 0xFFE6A0, 0xFF9A3C, rgb}, 190F, -170, -10);
        shake(Util.getMillis(), last ? 170 : 110);
    }

    /** 패널 뒤 (대장장이보다 아래): 배경 · 화덕 빛 · 불씨 · 모루 · 모루 위의 것 · 달군 빛 */
    @Override
    void back(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, SmithNet.Entry sel, int x0, int y0, long now, float partial) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKDROP, x0, y0, SmithScreen.SW, SmithScreen.SH);
        // 화덕 불 (깜빡임)
        double fl = 0.5 + 0.25 * Math.sin(now / 130.0) + 0.25 * Math.sin(now / 47.0 + 1.3);
        GachaArt.sprite(g, GachaArt.GLOW, x0 + FORGE_X, y0 + FORGE_Y, 54F + (float) fl * 6F, 0F, GachaArt.argb((int) (90 + 70 * fl), 0xFF9A3C));
        GachaArt.sprite(g, GachaArt.GLOW, x0 + FORGE_X, y0 + FORGE_Y + 6, 26F, 0F, GachaArt.argb((int) (120 + 60 * fl), 0xFFE08A));
        // 불씨 (화덕 입에서 위로)
        if (nextEmber < now - 1000) nextEmber = now;
        while (nextEmber <= now) {
            embers.add(new Spark(FORGE_X - 10 + rnd.nextFloat() * 20, FORGE_Y - 8, (rnd.nextFloat() - 0.5F) * 10F, -12F - rnd.nextFloat() * 12F, nextEmber,
                    1100 + rnd.nextInt(900), rnd.nextFloat() < 0.5F ? 0xFFC860 : 0xFF8A30, 1F, 0F));
            nextEmber += 120 + rnd.nextInt(160);
        }
        embers.removeIf(p -> now - p.born() > p.life());
        for (Spark p : embers) {
            float t = (now - p.born()) / 1000F;
            float px = p.x() + p.vx() * t + (float) Math.sin(t * 5 + p.born() % 7) * 2F;
            float py = p.y() + p.vy() * t;
            float a = 1F - (now - p.born()) / (float) p.life();
            if (py < 12) continue;
            int alpha = (int) (255 * a * (0.6 + 0.4 * Math.sin(now / 60.0 + p.born())));
            g.fill(x0 + Math.round(px), y0 + Math.round(py), x0 + Math.round(px) + 1, y0 + Math.round(py) + 1, GachaArt.argb(alpha, p.rgb()));
        }
        // 모루
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ANVIL, x0 + ANVIL_X, y0 + ANVIL_Y, ANVIL_W, ANVIL_H);
        // 모루 위의 것: 망치질 중 = 올린 것 (달굼 빛), 결과 = 결과 아이템, 아니면 고른 것
        ClientSmith.Work w = ClientSmith.work;
        SmithNet.Result r = ClientSmith.result;
        boolean showResult = r != null && now - ClientSmith.resultAt < SmithScreen.RESULT_MS;
        ItemStack item = w != null && ClientSmith.progress(0) < 1 ? w.item() : showResult && !r.item().isEmpty() ? r.item() : sel != null ? sel.icon() : ItemStack.EMPTY;
        double prog = w != null ? Math.max(0, ClientSmith.progress(partial)) : 0;
        if (w != null && prog < 1) {
            GachaArt.sprite(g, GachaArt.GLOW, x0 + ITEM_X, y0 + ITEM_Y + 2, 30F, 0F, GachaArt.argb((int) (60 + 150 * prog), 0xFF7A28));
        }
        if (!item.isEmpty()) {
            float pop = 1F;
            float dx = 0;
            if (showResult) {
                float p = (now - ClientSmith.resultAt) / 1000F;
                if (r.kind().equals("success") || r.kind().equals("done")) pop = 1F + 0.5F * (float) Math.max(0, Math.sin(Math.min(1, p / 0.35F) * Math.PI)) * Math.max(0, 1 - p);
                if (r.kind().equals("down")) dx = (float) Math.sin(p * 60) * 2.5F * Math.max(0, 1 - p * 2);
            }
            g.pose().pushMatrix();
            if (lying(item)) {
                // 무기 (그림이 왼쪽 위 끝 → 오른쪽 아래 손잡이): 모루 위에 눕힘 — 끝이 오른쪽 (뿔 쪽) · 손잡이가 대장장이 쪽,
                // 돌린 뒤 위에서 비스듬히 본 만큼 납작하게 (돌리기 → 줄이기 차례: 줄이기를 먼저 곱함)
                g.pose().translate(x0 + ITEM_X + dx, y0 + ITEM_Y);
                g.pose().scale(pop * 1.15F, pop * 0.8F);
                g.pose().rotate((float) (Math.PI * 0.75));
                g.item(item, -8, -8);
            } else {
                // 그 밖 (갑옷 · 장신구 · 아티팩트 · 활 · 방패): 모루 위에 세워 둠 (조금 작게 — 아래가 윗면에 닿음)
                float sc = 0.75F * pop;
                g.pose().translate(x0 + ITEM_X + dx, y0 + HIT_Y - 8F * sc);
                g.pose().scale(sc, sc);
                g.item(item, -8, -8);
            }
            g.pose().popMatrix();
        }
    }

    /** 모루 위에 눕혀 놓는 것: RPG 무기 중 그림이 대각선인 것 (활 · 방패는 세움) */
    static boolean lying(ItemStack s) {
        String type = CosmeticRender.weaponType(s);
        return !type.isEmpty() && !type.equals("bow") && !type.equals("shield");
    }

    /** 패널 앞 (대장장이 위): 망치가 닿은 번쩍임 · 고리 · 불꽃 */
    @Override
    void front(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, int x0, int y0, long now, float partial) {
        float pz = ClientSmith.pulse(now);
        if (pz > 0) {
            float k = 1F - pz;
            GachaArt.sprite(g, GachaArt.GLOW, x0 + HIT_X, y0 + HIT_Y, 22F + 26F * k, 0F, GachaArt.argb((int) (230 * (1 - k)), 0xFFE6A0));
            GachaArt.sprite(g, GachaArt.RING, x0 + HIT_X, y0 + HIT_Y, 8F + 34F * k, 0F, GachaArt.argb((int) (190 * (1 - k)), 0xFFC860));
        }
        drawSparks(g, x0, y0, now, 0xFF3010);
    }

    /** 대장간 소리 (가끔 불 타닥 — 작게) */
    @Override
    void ambience(long now) {
        if (now < nextCrackle) return;
        if (nextCrackle > 0) ClientSmith.sound(SoundEvents.CAMPFIRE_CRACKLE, 0.85F + rnd.nextFloat() * 0.3F, 0.16F);
        nextCrackle = now + 1400 + rnd.nextInt(2200);
    }
}
