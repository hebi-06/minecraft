package mmorpg.fabric.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.KeyMapping;

/**
 * 2026-11-18 축복의 길: 활 · 완드는 좌클릭으로 쏨 (우클릭은 스킬 키 — SkillKeys). 사진첩의 "쏘기" 는 공격 키를 한 번 누른 것과 같게 —
 * 누름 수 (KeyMapping.clickCount) 를 하나 올리면 다음 틱 Minecraft.handleKeybinds 가 진짜 좌클릭과 같은 길 (startAttack — 허공 · 블록이면 휘두르기 패킷,
 * 몹이면 공격 패킷) 으로 처리 (26.x 는 이름이 그대로라 리플렉션으로 찾음 — 모드 없는 클라이언트 시험 Probe 의 Q 와 같은 방법)
 */
final class ClientClicks {
    private ClientClicks() {}

    /** 좌클릭 한 번 (다음 틱에 처리) */
    static void leftClick(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> click(mc.options.keyAttack));
    }

    static void click(KeyMapping k) {
        try {
            java.lang.reflect.Field f = KeyMapping.class.getDeclaredField("clickCount");
            f.setAccessible(true);
            f.setInt(k, f.getInt(k) + 1);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("KeyMapping.clickCount", e);
        }
    }
}
