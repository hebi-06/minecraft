package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.smith.SmithNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 0.45.0 상인 창의 가게 패널: 나무 벽 · 물건 선반 · 무기 걸이 · 창의 햇빛 · 등, 상인 (보는 사람 쪽 — 계산대 뒤) 과 계산대 (앞 그림 — 허리 아래를 가림).
 * <ul>
 *   <li>사기 (buy): 상인이 뒤 선반에서 물건을 꺼내 (손에 듦) 계산대에 올리고 (첫 닿음 — 툭) 보는 사람 쪽으로 밀어 건넴 (둘째 — 물건이 커지며 다가옴 · 은화가
 *       접시로)</li>
 *   <li>팔기 (sell): 전리품이 계산대에 올라와 있고, 상인이 은화를 세어 놓음 (닿을 때마다 접시에 은화가 쌓임) → 은화를 밀어 줌</li>
 * </ul>
 */
final class ShopScene extends WorkScene {
    // 패널 안 자리 (tools/workshop_art.py 의 SHOP 과 같은 값 — 손이 계산대 윗판에 닿게)
    static final int SMITH_X = 54;
    static final int FEET_Y = 176;
    static final float FACE = -18F;
    static final float CAM = -6F;
    static final int COUNTER_Y = 142;
    static final int COUNTER_H = 52;
    /** 계산대 위 물건 자리 · 은화 접시 */
    static final int GOODS_X = 64;
    static final int GOODS_Y = 138;
    static final int COIN_X = 26;
    static final int COIN_Y = 143;

    static final Identifier BACKDROP = Mmorpg.id("smith/shop_backdrop");
    static final Identifier COUNTER = Mmorpg.id("smith/counter");

    private ItemStack silver = ItemStack.EMPTY;
    private ItemStack gold = ItemStack.EMPTY;
    /** 쉴 때 계산대 접시의 은화 (몇 닢 — 가끔 늘었다 줄어듦: 손님이 다녀간 듯) */
    private final List<float[]> coins = new ArrayList<>();
    private long nextDust;
    private final List<Spark> dust = new ArrayList<>();

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
        return "shop_hand";
    }

    @Override
    String roleName() {
        return "상인";
    }

    @Override
    String busyLabel(String fx) {
        return fx.equals("sell") ? "세는 중…" : "건네는 중…";
    }

    @Override
    float resultX() {
        return GOODS_X;
    }

    @Override
    float resultY() {
        return GOODS_Y - 4;
    }

    private ItemStack silver() {
        if (silver.isEmpty()) silver = stack("rpg:silver_coin");
        return silver;
    }

    private ItemStack gold() {
        if (gold.isEmpty()) gold = stack("rpg:gold_coin");
        return gold;
    }

    /** 사기: 선반에서 꺼낸 물건을 계산대에 올리기 전까지 손에 듦 (shop_hand 의 꺼냄 0.18 ~ 올림 0.45) */
    @Override
    ItemStack handItem(ClientSmith.Work w, double t) {
        if (w == null || !w.fx().equals("buy") || t < 0.2 || t >= 0.44) return null;
        return w.item();
    }

    @Override
    void onHit(int i, boolean last, ClientSmith.Work w) {
        long now = Util.getMillis();
        if (w != null && w.fx().equals("sell")) {
            // 은화 한 줌이 접시에 · 반짝
            for (int k = 0; k < 3; k++) coins.add(new float[] {COIN_X - 6 + rnd.nextFloat() * 12, COIN_Y - 2 - coins.size() * 0.6F, now});
            burst(COIN_X, COIN_Y - 4, 6, 20F, 50F, 300, new int[] {0xE6EEFF, 0xFFFFFF}, 120F, -160, -20);
            return;
        }
        if (i == 0) {
            // 계산대에 툭: 먼지 · 작은 흔들림
            burst(GOODS_X, GOODS_Y + 3, 8, 15F, 40F, 380, new int[] {0xC8A878, 0xA88858}, 60F, -170, -10);
            shake(now, 80);
        }
        if (last) {
            // 건넴: 은화가 접시로 · 금빛 반짝이
            for (int k = 0; k < 4; k++) coins.add(new float[] {COIN_X - 6 + rnd.nextFloat() * 12, COIN_Y - 2 - coins.size() * 0.6F, now});
            burst(GOODS_X, GOODS_Y - 6, 12, 25F, 70F, 450, new int[] {0xFFE6A0, 0xFFD24A, 0xFFFFFF}, 60F, 0, 360);
        }
    }

    /** 패널 뒤 (상인보다 아래): 배경 · 등 빛 · 햇빛 먼지 */
    @Override
    void back(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, SmithNet.Entry sel, int x0, int y0, long now, float partial) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BACKDROP, x0, y0, SmithScreen.SW, SmithScreen.SH);
        double fl = 0.5 + 0.2 * Math.sin(now / 400.0) + 0.1 * Math.sin(now / 97.0);
        GachaArt.sprite(g, GachaArt.GLOW, x0 + 66, y0 + 22, 46F + (float) fl * 6F, 0F, GachaArt.argb((int) (70 + 40 * fl), 0xFFC870));
        // 햇빛에 떠다니는 먼지 (창에서 비스듬히)
        if (nextDust < now - 2000) nextDust = now;
        while (nextDust <= now) {
            dust.add(new Spark(14 + rnd.nextFloat() * 40, 50 + rnd.nextFloat() * 70, (rnd.nextFloat() - 0.5F) * 3F, -1F - rnd.nextFloat() * 3F, nextDust,
                    2500 + rnd.nextInt(2500), 0xFFF0C8, 1F, 0F));
            nextDust += 260 + rnd.nextInt(300);
        }
        dust.removeIf(p -> now - p.born() > p.life());
        for (Spark p : dust) {
            float t = (now - p.born()) / 1000F;
            float px = p.x() + p.vx() * t + (float) Math.sin(t * 1.3 + p.born() % 13) * 3F;
            float py = p.y() + p.vy() * t;
            float life = (now - p.born()) / (float) p.life();
            int alpha = (int) (150 * Math.sin(Math.PI * life));
            g.fill(x0 + Math.round(px), y0 + Math.round(py), x0 + Math.round(px) + 1, y0 + Math.round(py) + 1, GachaArt.argb(alpha, p.rgb()));
        }
    }

    /** 패널 앞 (상인 위): 계산대 · 접시의 은화 · 계산대 위 물건 (일 · 결과에 따라) · 튀는 것 */
    @Override
    void front(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, int x0, int y0, long now, float partial) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, COUNTER, x0, y0 + COUNTER_Y, SmithScreen.SW, COUNTER_H);
        ClientSmith.Work w = ClientSmith.work;
        SmithNet.Result r = ClientSmith.result;
        double prog = w != null ? Math.max(0, ClientSmith.progress(partial)) : -1;
        boolean working = w != null && prog < 1;
        boolean showResult = r != null && now - ClientSmith.resultAt < SmithScreen.RESULT_MS;
        // 접시의 은화 (일이 끝나고 한참 지나면 줄어듦)
        if (!working && !showResult && coins.size() > 3 && now - coins.get(coins.size() - 1)[2] > 4000) coins.remove(coins.size() - 1);
        while (coins.size() < 3) coins.add(new float[] {COIN_X - 5 + coins.size() * 4F, COIN_Y - 2, 0});
        for (float[] c : coins) {
            float dropT = c[2] > 0 ? Math.min(1F, (now - c[2]) / 180F) : 1F;
            float cy = c[1] - (1 - dropT) * 12F;
            GachaArt.item(g, silver(), x0 + c[0], y0 + cy, 0.5F);
        }
        SmithNet.Entry sel = s.selectedEntry();
        float gx = x0 + GOODS_X;
        float gy = y0 + GOODS_Y;
        if (working) {
            int hits = ClientSmith.hits();
            int n = w.impacts().size();
            if (w.fx().equals("sell")) {
                // 전리품이 계산대에 (처음부터) — 세는 동안 그대로, 끝에 은화를 밀어 줌
                GachaArt.item(g, w.item(), gx, gy, 1.0F);
                if (hits >= n) GachaArt.item(g, silver(), gx - 14, gy + 2, 0.8F + 0.3F * clamp01((prog - 0.75) * 4));
            } else if (hits >= 1) {
                // 사기: 계산대에 놓였다가 (첫 닿음) 건넬 때 (둘째) 커지며 다가옴
                double t1 = n > 1 ? w.impacts().get(n - 1) / (double) w.ticks() : 0.7;
                float k = hits >= n ? clamp01((prog - t1) / 0.2) : 0F;
                float sc = (lying(w.item()) ? 1.0F : 0.9F) * (1F + 0.6F * k);
                drawGoods(g, w.item(), gx, gy + 6F * k, sc);
                if (k > 0) GachaArt.sprite(g, GachaArt.GLOW, gx, gy + 6F * k, 30F * k, 0F, GachaArt.argb((int) (120 * (1 - k)), 0xFFE6A0));
            }
        } else {
            ItemStack item = showResult && !r.item().isEmpty() ? r.item() : sel != null ? sel.icon() : ItemStack.EMPTY;
            float pop = 1F;
            if (showResult && r.kind().equals("done")) {
                float p = (now - ClientSmith.resultAt) / 1000F;
                pop = 1F + 0.4F * (float) Math.max(0, Math.sin(Math.min(1, p / 0.35F) * Math.PI)) * Math.max(0, 1 - p);
            }
            if (!item.isEmpty()) drawGoods(g, item, gx, gy, (lying(item) ? 1.0F : 0.9F) * pop);
        }
        drawSparks(g, x0, y0, now, 0x806040);
    }

    /** 계산대 위 물건: 무기는 눕혀서 (끝이 오른쪽), 그 밖은 세워서 */
    private static void drawGoods(GuiGraphicsExtractor g, ItemStack item, float cx, float cy, float sc) {
        if (item.isEmpty()) return;
        g.pose().pushMatrix();
        if (lying(item)) {
            g.pose().translate(cx, cy);
            g.pose().scale(sc * 1.15F, sc * 0.7F);
            g.pose().rotate((float) (Math.PI * 0.75));
            g.item(item, -8, -8);
        } else {
            g.pose().translate(cx, cy - 6F * sc);
            g.pose().scale(sc, sc);
            g.item(item, -8, -8);
        }
        g.pose().popMatrix();
    }

    private static boolean lying(ItemStack s) {
        return ForgeScene.lying(s);
    }

    private long nextBell;

    /** 가게 소리 (아주 가끔 은화 짤랑 — 작게) */
    @Override
    void ambience(long now) {
        if (now < nextBell) return;
        if (nextBell > 0) ClientSmith.sound(SoundEvents.CHAIN_STEP, 1.6F + rnd.nextFloat() * 0.3F, 0.08F);
        nextBell = now + 5000 + rnd.nextInt(5000);
    }
}
