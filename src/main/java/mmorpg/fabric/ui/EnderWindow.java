package mmorpg.fabric.ui;

import java.util.concurrent.ThreadLocalRandom;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 엔더 상자 창 (enderui.js — 스탯 창 아랫줄 "엔더 상자"): 0.22.0 부터 엔더 창고 (storage.StorageWindow — 큰 상자 54칸 × 상자 5개) 를 엶.
 * 아래는 0.21.0 까지의 설명 — 어디서나 내 엔더 상자 27칸 (놓인 엔더 상자와 같은 칸).
 * 애드온은 상자 모양 창에서 칸을 눌러 인벤토리와 서로 옮겼지만 (베드락은 스크립트로 바닐라 상자 창을 열 수 없음),
 * 자바는 바닐라 상자 창 (끌어 놓기 · Shift + 누르기 · 숫자 키 그대로) 으로 엶. 연출은 애드온 그대로: 열 때 발밑 보랏빛 룬 · 고리 · 거꾸로 포털 알갱이 +
 * 엔더 상자 여는 소리, 닫을 때 닫는 소리.
 */
public final class EnderWindow {
    private EnderWindow() {}

    /** 연출 색 (보랏빛 — 엔더, rules.json ENDER.color) */
    public static final double[] COLOR = mmorpg.fabric.boss.BossKit.rgb(mmorpg.core.Rules.get().obj("ENDER"), "color");

    public static void init() {
        RpgNet.onStatAction("ender", EnderWindow::open);
    }

    /** 스탯 창 아랫줄 단추 · 예전 호출: 0.22.0 부터 엔더 창고 (큰 상자 · 상자 5개 — storage.StorageWindow) 를 마지막으로 본 상자로 엶 */
    public static boolean open(ServerPlayer sp) {
        return mmorpg.fabric.storage.StorageWindow.open(sp, -1, null, null);
    }

    /** 열 때: 발밑 보랏빛 룬 · 고리 · 거꾸로 포털 알갱이 + 여는 소리 */
    public static void openFx(ServerPlayer sp) {
        ServerLevel level = sp.level();
        Vec3 feet = sp.position();
        SkillFx.rune(level, feet, Opt.of("size", 1.3, "life", 0.6, "color", COLOR, "alpha", 0.9, "progress", 1, "rate", 160, "grow", 0.25));
        SkillFx.shock(level, feet, COLOR, 1.8, 0.4);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            SkillFx.vanilla(level, "minecraft:portal_reverse_particle", feet.add((r.nextDouble() - 0.5) * 1.4, 0.3 + r.nextDouble() * 1.5, (r.nextDouble() - 0.5) * 1.4));
        }
        SkillFx.play(level, "random.enderchestopen", V.up(feet, 1), 1, 0.8);
    }

    /** 닫을 때: 닫는 소리 */
    public static void closeFx(ServerPlayer sp) {
        SkillFx.play(sp.level(), "random.enderchestclosed", V.up(sp.position(), 1), 1, 0.8);
    }
}
