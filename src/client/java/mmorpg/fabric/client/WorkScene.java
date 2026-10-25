package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mmorpg.fabric.smith.SmithNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 0.45.0 가게 창 왼쪽 패널의 장면 — 대장간 ({@link ForgeScene}) · 룬 공방 ({@link RuneScene}) · 가게 ({@link ShopScene}). {@link SmithScreen} 이 차례대로 부름:
 * 뒤 ({@link #back} — 배경 · 받침 · 올린 것) → NPC (3D — 이 장면의 자리 · 몸 방향 · 쉬는 자세 · 든 것) → 앞 ({@link #front} — 불꽃 · 빛 · 계산대 같은 앞 그림).
 * 결과 연출 · 이름표 · 테는 창이 그림 ({@link #resultX} · {@link #resultY} 자리에). 좌표는 패널 기준 (112 × 194 — x0 · y0 = 패널 왼쪽 위 + 흔들림).
 */
abstract class WorkScene {
    /** 튀는 것 하나 (불꽃 · 조각 · 반짝이) — 시작 자리 · 속도 (px/초) · 태어난 때 · 수명 (밀리초) · 색 · 굵기 · 무게 (아래로 당김 px/초²) */
    record Spark(float x, float y, float vx, float vy, long born, int life, int rgb, float size, float gravity) {}

    final Random rnd = new Random();
    final List<Spark> sparks = new ArrayList<>();
    long shakeUntil;
    int shakeMs = 170;

    // ---- NPC 자리 (패널 기준)

    /** 발 x */
    abstract float npcX();

    /** 발 y */
    abstract float feetY();

    /** 1칸 크기 (px) */
    float fig() {
        return 44F;
    }

    /** 몸 방향 (정면 = 0 — 음수면 오른쪽으로 돎) */
    abstract float face();

    /** 카메라 기울기 (도 — 음수 = 조금 위에서 내려다봄) */
    abstract float cam();

    /** 쉬는 자세 (동작 이름 — 그 동작의 열쇠 {@link #idleKey}) */
    abstract String idleMotion();

    int idleKey() {
        return 1;
    }

    /** 늘 드는 것 (item_model id — null 이면 빈손: NPC 가 든 것을 그대로 두지 않고 비움) */
    String hold() {
        return null;
    }

    /** 일하는 동안 손에 든 것 (상인이 선반에서 꺼낸 물건 — null 이면 hold) */
    ItemStack handItem(ClientSmith.Work w, double t) {
        return null;
    }

    /** NPC 가 없을 때 이름표 */
    abstract String roleName();

    /** 큰 단추 글 (일하는 중 — fx 마다) */
    abstract String busyLabel(String fx);

    /** 상세의 재료 줄 머리 (탭마다 — 사기는 "값", 팔기는 "팔 것") */
    String costLabel(String tab) {
        return "재료";
    }

    /** 결과 연출의 가운데 (패널 기준) */
    abstract float resultX();

    abstract float resultY();

    // ---- 그리기

    /** 뒤 (NPC 아래): 배경 · 받침 · 올린 것 */
    abstract void back(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, SmithNet.Entry sel, int x0, int y0, long now, float partial);

    /** 앞 (NPC 위 — 다음 층): 빛 · 튀는 것 · 앞 그림 */
    abstract void front(SmithScreen s, GuiGraphicsExtractor g, SmithNet.ViewPayload v, int x0, int y0, long now, float partial);

    /** 닿음 (ClientSmith 틱 — i 번째, 마지막이면 last) */
    abstract void onHit(int i, boolean last, ClientSmith.Work w);

    /** 가끔 들리는 소리 (창이 열려 있는 동안) */
    void ambience(long now) {}

    // ---- 도움

    /** 흔들림 (닿은 바로 뒤 — {dx, dy}) */
    int[] shake(long now) {
        if (now >= shakeUntil) return new int[] {0, 0};
        float k = (shakeUntil - now) / (float) shakeMs;
        return new int[] {Math.round((rnd.nextFloat() - 0.5F) * 3F * k), Math.round((rnd.nextFloat() - 0.5F) * 2.4F * k)};
    }

    void shake(long now, int ms) {
        shakeMs = Math.max(1, ms);
        shakeUntil = now + ms;
    }

    /** 튀는 것 n 개 (각 = 위쪽 반원에서 고름, 속도 lo ~ hi) */
    void burst(float x, float y, int n, float lo, float hi, int life, int[] colors, float gravity, double a0, double a1) {
        long now = net.minecraft.util.Util.getMillis();
        for (int k = 0; k < n; k++) {
            double a = Math.toRadians(a0 + rnd.nextDouble() * (a1 - a0));
            float sp = lo + rnd.nextFloat() * (hi - lo);
            int col = colors[rnd.nextInt(colors.length)];
            sparks.add(new Spark(x + (rnd.nextFloat() - 0.5F) * 3, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, now, life / 2 + rnd.nextInt(Math.max(1, life)),
                    col, rnd.nextFloat() < 0.3F ? 2F : 1F, gravity));
        }
    }

    /** 튀는 것 그리기 (짧은 줄 — 나는 쪽으로 · 끝에 어두워짐, 패널 밖은 그리지 않음) */
    void drawSparks(GuiGraphicsExtractor g, int x0, int y0, long now, int fadeTo) {
        sparks.removeIf(p -> now - p.born() > p.life());
        for (Spark p : sparks) {
            float t = (now - p.born()) / 1000F;
            float px = p.x() + p.vx() * t;
            float py = p.y() + p.vy() * t + 0.5F * p.gravity() * t * t;
            if (px < 2 || px > SmithScreen.SW - 2 || py < 2 || py > SmithScreen.SH - 2) continue;
            float a = 1F - (now - p.born()) / (float) p.life();
            float vx = p.vx();
            float vy = p.vy() + p.gravity() * t;
            float ang = (float) Math.atan2(vy, vx);
            float len = Math.min(5F, 1.5F + (float) Math.hypot(vx, vy) / 40F);
            int col = GachaArt.mix(p.rgb(), fadeTo, Math.min(1, (now - p.born()) / (double) p.life() * 1.2));
            g.pose().pushMatrix();
            g.pose().translate(x0 + px, y0 + py);
            g.pose().rotate(ang);
            g.fill(Math.round(-len), 0, 0, Math.max(1, Math.round(p.size())), GachaArt.argb((int) (255 * a), col));
            g.pose().popMatrix();
        }
    }

    /** 둥근 그림 (마법진 · 빛) 을 납작하게 (바닥에 누운 것 — k = 세로 비율) · 돌림 */
    static void flat(GuiGraphicsExtractor g, Identifier id, float cx, float cy, float size, float k, float angle, int argb) {
        if (size <= 0.5F || (argb >>> 24) == 0) return;
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(1F, k);
        if (angle != 0) g.pose().rotate(angle);
        int s = Math.max(1, Math.round(size));
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, -s / 2, -s / 2, s, s, argb);
        g.pose().popMatrix();
    }

    /** 아이템 id 의 한 개 (없으면 EMPTY) */
    static ItemStack stack(String id) {
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return ItemStack.EMPTY;
        Item it = BuiltInRegistries.ITEM.getValue(rl);
        return it == null || it == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    /** 일의 진행 (0 ~ 1 — 없으면 -1) */
    static double progress(float partial) {
        return ClientSmith.work != null ? ClientSmith.progress(partial) : -1;
    }

    /** 0 ~ 1 사이로 */
    static float clamp01(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }
}
