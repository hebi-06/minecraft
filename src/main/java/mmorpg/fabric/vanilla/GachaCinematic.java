package mmorpg.fabric.vanilla;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mmorpg.core.Gacha;
import mmorpg.core.Runes;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gacha.GachaSystem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 룬 뽑기 연출 "룬 각성" (모드 없는 클라이언트 — 메뉴 뽑기 쪽의 확인 창 "네" 뒤). 사용자 2026-11-08: "네 버튼을 누르면 2번, 3번 사진과 똑같이는 아니고
 * 우리만의 뽑기 연출" (화면 흔들림 없음 · 무지개 없음 — 색은 등급 색 하나씩).
 *
 * <p>화면 전체를 덮는 그림: 제목 (4배) = 어두운 바탕 글자 하나 (처음 6틱 차오름 · 끝 8틱 걷힘), 부제 (2배) = 틱마다 새로 짠 그림 글자
 * (tools/vanilla_gui_cine.py — cine.json: 글꼴마다 세로 자리가 정해진 그림 · 룬 그림판 · 글자). 부제의 글자 1 = 화면 2px (fu), 가로는 빈 글자로
 * 옮기고 끝에서 0 으로 돌아와 (너비 0) 화면 가운데가 x 0. HUD (체력 · 핫바 · 액션바 · 보스 막대) 는 제목 아래라 가려지고 채팅만 위에 보임.
 *
 * <pre>
 * 처음   (0 ~ 37틱) 별 · 마법진이 그려짐 (금) → 돌며 빛이 모임 (가장 높은 등급 색으로 바뀜) → 번쩍
 * 봉인   룬석 (1회 = 가운데 큰 것, 10회 = 다섯 × 두 줄 — 차례로 나타남) · 문양은 그 룬의 등급 색 (숨쉬듯 밝아졌다 어두워짐)
 *        "좌클릭: 열기 · Shift: 건너뛰기"
 * 열기   (좌클릭) 금 → 깨짐 → 룬 · 빛 · 이름 (1회) / 차례로 (10회 — 영웅 이상은 한 박자 쉼) · 그때 결과 알림 (GachaSystem.revealNow)
 * 초월   10회에 초월이 있으면 하나씩 가운데 크게 ("좌클릭: 계속")
 * 끝     결과 ("좌클릭: 닫기") → 걷힘 → 메뉴 뽑기 쪽 (최근 결과)
 * </pre>
 * 입력 (GachaCinematicMixin · VanillaInput.onSwapKey): 좌클릭 (휘두르기 · 공격) · 우클릭 · F = 다음, Shift (누르는 순간) = 건너뛰기 (결과로 —
 * 결과에서는 닫기). 연출 동안 걷기 · 뛰기 막음 (이동 속도 · 점프 힘 −100% — 잠깐 붙는 것), 블록 · 아이템 · 몹을 건드리는 패킷은 받지 않음,
 * 다른 큰 글자 (Notify.title) 를 보내지 않음. 맞으면 · 죽으면 · 나가면 바로 끝 (메뉴를 다시 열지 않음). 오래 두면 저절로 넘어감.
 */
public final class GachaCinematic {
    private GachaCinematic() {}

    private static final Identifier FREEZE = Mmorpg.id("gacha_cinematic_freeze");
    /** 봉인에서 저절로 열기 · 결과에서 저절로 닫기 (틱) */
    private static final int AUTO_OPEN = 240, AUTO_CLOSE = 600;
    /** 누름 사이 최소 (틱 — 한 번 누름이 두 번 들어오지 않게) */
    private static final int DEBOUNCE = 4;
    private static final int GOLD = 0xFFD678, MUTED = 0x969ABE, CREAM = 0xF0E4C8;

    enum Phase { INTRO, SEALED, OPENING, SPOT, SUMMARY, ENDING }

    private static final class Run {
        final ServerPlayer sp;
        final GachaSystem.Drawn d;
        final Runnable after;
        final List<Gacha.Pull> pulls;
        final List<Boolean> fresh;
        final List<String> tex = new ArrayList<>();
        final int top;
        final boolean ten;
        /** 10회 열기에서 칸마다 깨지는 때 (열기 시작부터 틱) */
        final int[] openAt;
        final List<Integer> spots = new ArrayList<>();
        int t, phaseT, spot, lastInput = -100;
        Phase phase = Phase.INTRO;
        boolean shift, announced;
        Component lastSub;

        Run(ServerPlayer sp, GachaSystem.Drawn d, Runnable after) {
            this.sp = sp;
            this.d = d;
            this.after = after;
            this.pulls = d.pulls().size() > 10 ? d.pulls().subList(0, 10) : d.pulls();
            this.fresh = d.fresh();
            int best = 0;
            for (Gacha.Pull p : pulls) {
                best = Math.max(best, Runes.GRADE_ORDER.indexOf(p.grade()));
                tex.add(VanillaGacha.modelOf(RuneItems.stack(p.family(), p.grade())).getPath());
            }
            this.top = best;
            this.ten = pulls.size() > 1;
            this.openAt = new int[pulls.size()];
            int at = 0;
            for (int i = 0; i < pulls.size(); i++) {
                openAt[i] = at;
                at += 3 + (gi(i) >= 3 ? 5 : 0);
                if (ten && gi(i) >= 4) spots.add(i);
            }
        }

        int gi(int i) {
            return Math.max(0, Runes.GRADE_ORDER.indexOf(pulls.get(i).grade()));
        }

        boolean isFresh(int i) {
            return i < fresh.size() && fresh.get(i);
        }

        int openEnd() {
            return pulls.isEmpty() ? 0 : openAt[pulls.size() - 1] + 8;
        }
    }

    private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(GachaCinematic::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RUNS.remove(handler.player.getUUID());
            VanillaGacha.forget(handler.player.getUUID());
        });
    }

    /** 지금 단계 이름 (시험 — 연출 중이 아니면 null) */
    static String phase(ServerPlayer sp) {
        Run r = sp == null ? null : RUNS.get(sp.getUUID());
        return r == null ? null : r.phase.name();
    }

    /** 연출 중인지 (입력 · 큰 글자 막기) */
    public static boolean active(ServerPlayer sp) {
        return sp != null && RUNS.containsKey(sp.getUUID());
    }

    /** 연출 시작 (메뉴는 닫힌 뒤) — after = 끝나고 할 일 (메뉴 뽑기 쪽 다시 열기) */
    public static void play(ServerPlayer sp, GachaSystem.Drawn d, Runnable after) {
        if (sp == null || sp.isRemoved() || d == null || d.pulls().isEmpty() || meta() == null) {
            if (sp != null && d != null) GachaSystem.revealNow(sp, d.id());
            if (after != null) after.run();
            return;
        }
        Run r = new Run(sp, d, after);
        RUNS.put(sp.getUUID(), r);
        freeze(sp, true);
        r.shift = sp.isShiftKeyDown();
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(6, 72000, 0));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        Cine bg = new Cine();
        bg.glyph("backdrop", 0, 0xFFFFFF);
        sp.connection.send(new ClientboundSetTitleTextPacket(bg.build()));
        SkillFx.playFor(sp, "block.beacon.activate", 1.5, 0.6);
    }

    /** 입력 (좌클릭 · 우클릭 · F): 연출 중이면 다음으로 넘기고 true (그 패킷은 버림) */
    public static boolean input(ServerPlayer sp) {
        Run r = sp == null ? null : RUNS.get(sp.getUUID());
        if (r == null) return false;
        if (r.t - r.lastInput < DEBOUNCE) return true;
        r.lastInput = r.t;
        advance(r);
        return true;
    }

    private static void advance(Run r) {
        switch (r.phase) {
            case INTRO -> go(r, Phase.SEALED);
            case SEALED -> go(r, Phase.OPENING);
            case OPENING -> afterOpening(r);
            case SPOT -> nextSpot(r);
            case SUMMARY -> go(r, Phase.ENDING);
            default -> {
            }
        }
    }

    /** 건너뛰기 (Shift): 결과로 — 결과에서는 닫기 */
    private static void skip(Run r) {
        if (r.phase == Phase.SUMMARY) {
            go(r, Phase.ENDING);
        } else if (r.phase != Phase.ENDING) {
            announce(r);
            go(r, Phase.SUMMARY);
            SkillFx.playFor(r.sp, r.top >= 3 ? "entity.player.levelup" : "block.amethyst_block.chime", 1.2, 0.6);
        }
    }

    private static void go(Run r, Phase p) {
        r.phase = p;
        r.phaseT = 0;
        r.lastInput = r.t;
    }

    private static void afterOpening(Run r) {
        announce(r);
        if (!r.spots.isEmpty()) {
            r.spot = 0;
            go(r, Phase.SPOT);
        } else {
            go(r, Phase.SUMMARY);
        }
    }

    private static void nextSpot(Run r) {
        if (r.spot + 1 < r.spots.size()) {
            r.spot++;
            r.phaseT = 0;
            r.lastInput = r.t;
        } else {
            go(r, Phase.SUMMARY);
        }
    }

    /** 결과 알림 (채팅 · 영웅 이상 모두에게 · 빛기둥) — 룬이 보일 때 한 번 */
    private static void announce(Run r) {
        if (r.announced) return;
        r.announced = true;
        GachaSystem.revealNow(r.sp, r.d.id());
    }

    private static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator(); it.hasNext(); ) {
            Run r = it.next().getValue();
            ServerPlayer sp = r.sp;
            if (sp.isRemoved() || sp.isDeadOrDying() || sp.hasDisconnected()) {
                it.remove();
                announce(r);
                continue;
            }
            if (r.t > 2 && sp.hurtTime >= 9) { // 맞음 (맞은 틱 hurtTime 10): 바로 끝 (세계를 봐야 함)
                it.remove();
                announce(r);
                stop(sp);
                continue;
            }
            boolean shift = sp.isShiftKeyDown();
            if (shift && !r.shift && r.t - r.lastInput >= DEBOUNCE) {
                r.lastInput = r.t;
                skip(r);
            }
            r.shift = shift;
            step(r);
            if (r.phase == Phase.ENDING && r.phaseT > 9) {
                it.remove();
                stop(sp);
                if (r.after != null) r.after.run();
                continue;
            }
            Component sub = frame(r);
            if (!sub.equals(r.lastSub)) {
                r.lastSub = sub;
                sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            }
            r.t++;
            r.phaseT++;
        }
    }

    /** 틱마다 소리 · 저절로 넘어감 */
    private static void step(Run r) {
        ServerPlayer sp = r.sp;
        int pt = r.phaseT;
        switch (r.phase) {
            case INTRO -> {
                if (pt == 2) SkillFx.playFor(sp, "block.enchantment_table.use", 0.9, 0.9);
                if (pt == 8) SkillFx.playFor(sp, "block.amethyst_block.chime", 0.9, 0.8);
                if (pt == 14) SkillFx.playFor(sp, "block.amethyst_block.chime", 1.2, 0.8);
                if (pt == 18) SkillFx.playFor(sp, "block.beacon.power_select", 1.0 + r.top * 0.08, 0.7);
                if (pt == 26) SkillFx.playFor(sp, "block.respawn_anchor.charge", 0.9 + r.top * 0.1, 0.6);
                if (pt == 30 && r.top >= 3) SkillFx.playFor(sp, "entity.evoker.prepare_summon", 1.3, 0.5);
                if (pt == 34) SkillFx.playFor(sp, "entity.firework_rocket.blast", 0.8, 0.7);
                if (pt >= 38) go(r, Phase.SEALED);
            }
            case SEALED -> {
                if (r.ten) {
                    if (pt < r.pulls.size()) SkillFx.playFor(sp, "block.note_block.pling", 0.8 + pt * 0.07, 0.35);
                } else if (pt == 0) {
                    SkillFx.playFor(sp, "block.end_portal_frame.fill", 1.0, 0.8);
                }
                if (pt >= AUTO_OPEN) go(r, Phase.OPENING);
            }
            case OPENING -> {
                if (!r.ten) {
                    if (pt == 0) SkillFx.playFor(sp, "block.amethyst_block.break", 0.7, 0.8);
                    if (pt == 3) SkillFx.playFor(sp, "block.amethyst_block.break", 1.0, 0.9);
                    if (pt == 6) {
                        SkillFx.playFor(sp, "block.glass.break", 0.8, 0.6);
                        revealSound(sp, r.gi(0), 1.0);
                        announce(r);
                    }
                    if (pt >= 10) go(r, Phase.SUMMARY);
                } else {
                    for (int i = 0; i < r.pulls.size(); i++) {
                        int u = pt - r.openAt[i];
                        if (u == 0) SkillFx.playFor(sp, "block.amethyst_block.break", 0.9 + i * 0.04, 0.4);
                        if (u == 1) revealSound(sp, r.gi(i), 0.55);
                    }
                    if (pt >= r.openEnd()) afterOpening(r);
                }
            }
            case SPOT -> {
                if (pt == 0) {
                    SkillFx.playFor(sp, "ui.toast.challenge_complete", 1.0, 0.8);
                    SkillFx.playFor(sp, "item.totem.use", 1.3, 0.4);
                }
                if (pt >= AUTO_CLOSE) nextSpot(r);
            }
            case SUMMARY -> {
                if (pt >= AUTO_CLOSE) go(r, Phase.ENDING);
            }
            case ENDING -> {
                if (pt == 0) {
                    r.sp.connection.send(new ClientboundSetTitlesAnimationPacket(0, 0, 8));
                    SkillFx.playFor(sp, "block.amethyst_block.resonate", 1.4, 0.5);
                }
            }
        }
    }

    /** 등급마다 소리 (높을수록 큼 — 초월 = 팡파르) */
    private static void revealSound(ServerPlayer sp, int gi, double vol) {
        if (gi >= 4) {
            SkillFx.playFor(sp, "ui.toast.challenge_complete", 1.0, vol);
            SkillFx.playFor(sp, "item.totem.use", 1.4, vol * 0.5);
        } else if (gi == 3) {
            SkillFx.playFor(sp, "entity.player.levelup", 1.2, vol * 0.8);
        } else if (gi == 2) {
            SkillFx.playFor(sp, "block.note_block.bell", 1.2, vol * 0.7);
        } else if (gi == 1) {
            SkillFx.playFor(sp, "block.note_block.chime", 1.3, vol * 0.6);
        } else {
            SkillFx.playFor(sp, "block.note_block.pling", 1.5, vol * 0.4);
        }
    }

    // ------------------------------------------------------------ 그림 짜기 (부제)

    private static Component frame(Run r) {
        Cine c = new Cine();
        int pt = r.phaseT;
        int topRgb = VanillaGacha.gradeColor(Runes.GRADE_ORDER.get(r.top));
        String title = VanillaGacha.strip(r.d.def() == null ? "룬 뽑기" : r.d.def().name());
        c.glyph("stars", 0, 0xFFFFFF);
        switch (r.phase) {
            case INTRO -> {
                if (pt < 2) break;
                if (pt < 18) {
                    c.glyph("circle_in" + Math.min(7, (pt - 2) / 2), 0, GOLD);
                } else if (pt < 34) {
                    double k = (pt - 18) / 16.0;
                    c.glyph("circle_spin" + ((pt - 18) / 2) % 6, 0, mix(GOLD, topRgb, k));
                    c.glyph("spark" + Math.min(5, (pt - 18) / 3), 0, topRgb);
                } else {
                    c.glyph("flash" + Math.min(3, pt - 34), 0, mix(topRgb, 0xFFFFFF, 0.55));
                }
            }
            case SEALED -> {
                if (r.ten) {
                    c.text("cine_sub", title + " · " + r.pulls.size() + "회", GOLD, 0);
                    for (int i = 0; i < r.pulls.size() && i <= pt; i++) sealSmall(c, r, i, pulse(r.t + i * 3));
                } else {
                    c.text("cine_sub", title, GOLD, 0);
                    c.glyph("seal_big", 0, 0xFFFFFF);
                    c.glyph("mark_big", 0, scale(VanillaGacha.gradeColor(r.pulls.get(0).grade()), pulse(r.t)));
                }
                if (pt > 6) c.text("cine_hint", "좌클릭: 열기 · Shift: 건너뛰기", blink(r.t, MUTED), 0);
            }
            case OPENING -> {
                if (!r.ten) {
                    if (pt < 6) {
                        c.glyph("seal_big", 0, 0xFFFFFF);
                        c.glyph("mark_big", 0, VanillaGacha.gradeColor(r.pulls.get(0).grade()));
                        if (pt >= 3) c.glyph("crack_big1", 0, mix(VanillaGacha.gradeColor(r.pulls.get(0).grade()), 0xFFFFFF, 0.5));
                        else c.glyph("crack_big0", 0, mix(VanillaGacha.gradeColor(r.pulls.get(0).grade()), 0xFFFFFF, 0.5));
                    } else {
                        big(c, r, 0, r.t, pt - 6);
                    }
                } else {
                    c.text("cine_sub", title + " · " + r.pulls.size() + "회", GOLD, 0);
                    for (int i = 0; i < r.pulls.size(); i++) {
                        int u = pt - r.openAt[i];
                        if (u < 0) sealSmall(c, r, i, pulse(r.t + i * 3));
                        else if (u == 0) {
                            sealSmall(c, r, i, 1.0);
                            c.glyph("crack_s" + i / 5, x(i), mix(VanillaGacha.gradeColor(r.pulls.get(i).grade()), 0xFFFFFF, 0.5));
                        } else {
                            cardSmall(c, r, i, u);
                        }
                    }
                }
            }
            case SPOT -> {
                int i = r.spots.get(r.spot);
                big(c, r, i, r.t, pt);
                c.text("cine_hint", "좌클릭: 계속", blink(r.t, MUTED), 0);
            }
            case SUMMARY, ENDING -> {
                if (r.ten) {
                    c.text("cine_sub", title + " · " + r.pulls.size() + "회 결과", GOLD, 0);
                    for (int i = 0; i < r.pulls.size(); i++) cardSmall(c, r, i, 99);
                } else {
                    big(c, r, 0, r.t, 99);
                }
                if (r.phase == Phase.SUMMARY) c.text("cine_hint", "좌클릭: 닫기", blink(r.t, MUTED), 0);
            }
        }
        return c.build();
    }

    /** 큰 룬 (가운데): 빛 (등급 색 — 6틱마다 바뀜) · 깨진 조각 (처음 4틱) · 룬 · 이름 · 등급 · NEW · 픽업 */
    private static void big(Cine c, Run r, int i, int t, int u) {
        Gacha.Pull p = r.pulls.get(i);
        int rgb = VanillaGacha.gradeColor(p.grade());
        c.glyph("aura_big" + ((t / 6) % 2), 0, scale(rgb, 0.85 + 0.15 * pulse(t)));
        if (u < 2) c.glyph("shard_big0", 0, 0xFFFFFF);
        else if (u < 4) c.glyph("shard_big1", 0, 0xFFFFFF);
        c.rune(r.tex.get(i), -1, 0);
        Runes.Rune rr = Runes.parseRune(p.id());
        String name = rr == null ? p.id() : rr.def().str("name");
        c.text("cine_name", name, rgb, 0);
        mmorpg.core.Grade gd = mmorpg.core.Rules.get().grade(p.grade());
        List<String> sub = new ArrayList<>();
        sub.add(gd == null ? p.grade() : gd.name());
        if (p.featured()) sub.add("픽업");
        if (p.lost()) sub.add("픽업을 놓침");
        c.text("cine_sub", String.join(" · ", sub), p.featured() ? GOLD : CREAM, 0);
        if (r.isFresh(i)) c.glyph("new_big", c.textWidth("cine_name", name) / 2 + 10, 0xFFFFFF);
    }

    /** 10회 칸 x (fu) */
    private static int x(int i) {
        return meta().gridX[i % 5];
    }

    private static void sealSmall(Cine c, Run r, int i, double glow) {
        int row = i / 5;
        c.glyph("seal_s" + row, x(i), 0xFFFFFF);
        c.glyph("mark_s" + row, x(i), scale(VanillaGacha.gradeColor(r.pulls.get(i).grade()), glow));
    }

    /** 깨진 뒤의 칸 (u = 깨진 뒤 틱): 빛 (고귀 이상) · 테 · 룬 · NEW, 처음 2틱은 조각 */
    private static void cardSmall(Cine c, Run r, int i, int u) {
        int row = i / 5;
        int rgb = VanillaGacha.gradeColor(r.pulls.get(i).grade());
        if (r.gi(i) >= 2) c.glyph("aura_s" + row + "_" + ((r.t / 6 + i) % 2), x(i), scale(rgb, r.gi(i) >= 3 ? 1.0 : 0.7));
        c.glyph("ring_s" + row, x(i), rgb);
        c.rune(r.tex.get(i), row, x(i));
        if (u <= 2) c.glyph("shard_s" + row, x(i), 0xFFFFFF);
        if (r.isFresh(i)) c.glyph("new_s" + row, x(i) + 6, 0xFFFFFF);
    }

    /** 숨쉬듯 (0.6 ~ 1.0) */
    private static double pulse(int t) {
        return 0.8 + 0.2 * Math.sin(t * 0.35);
    }

    private static int blink(int t, int rgb) {
        return scale(rgb, (t / 10) % 2 == 0 ? 1.0 : 0.7);
    }

    static int mix(int a, int b, double k) {
        k = Math.max(0, Math.min(1, k));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) Math.round((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    static int scale(int rgb, double k) {
        return mix(0, rgb, k);
    }

    // ------------------------------------------------------------ 멈춤 · 끝

    private static void freeze(ServerPlayer sp, boolean on) {
        for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance a = sp.getAttribute(attr);
            if (a == null) continue;
            if (on) a.addOrUpdateTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else a.removeModifier(FREEZE);
        }
    }

    private static void stop(ServerPlayer sp) {
        freeze(sp, false);
        if (!sp.hasDisconnected()) sp.connection.send(new ClientboundClearTitlesPacket(true));
    }

    // ------------------------------------------------------------ 그림 글자 (cine.json)

    private record G(Identifier font, String ch, int adv, int x) {}

    private record RuneG(String ch, int big, int small) {}

    private static final class Meta {
        final Map<String, G> glyphs = new HashMap<>();
        final Map<String, RuneG> runes = new HashMap<>();
        final Map<String, double[]> text = new HashMap<>();
        int[] gridX = {-50, -25, 0, 25, 50};
        int bigRune = 64, smallRune = 16;
    }

    private static Meta meta;
    private static boolean metaTried;

    private static synchronized Meta meta() {
        if (metaTried) return meta;
        metaTried = true;
        try (InputStream in = GachaCinematic.class.getResourceAsStream("/mmorpg/vanilla/gui/cine/cine.json")) {
            if (in == null) return null;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Meta m = new Meta();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("glyphs").entrySet()) {
                JsonObject g = e.getValue().getAsJsonObject();
                m.glyphs.put(e.getKey(), new G(Mmorpg.rpg(g.get("font").getAsString()), g.get("char").getAsString(), g.get("adv").getAsInt(), g.get("x").getAsInt()));
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("runes").entrySet()) {
                JsonObject g = e.getValue().getAsJsonObject();
                m.runes.put(e.getKey(), new RuneG(g.get("char").getAsString(), g.get("big").getAsInt(), g.get("small").getAsInt()));
            }
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("text").entrySet()) {
                JsonObject g = e.getValue().getAsJsonObject();
                m.text.put(e.getKey(), new double[]{g.get("scale").getAsDouble(), g.get("space").getAsDouble()});
            }
            JsonArray gx = o.getAsJsonObject("grid").getAsJsonArray("x");
            m.gridX = new int[gx.size()];
            for (int i = 0; i < gx.size(); i++) m.gridX[i] = gx.get(i).getAsInt();
            JsonObject big = o.getAsJsonObject("big");
            m.bigRune = big.get("rune").getAsInt();
            m.smallRune = big.get("small").getAsInt();
            meta = m;
        } catch (Exception e) {
            Mmorpg.LOG.warn("[vanilla] gacha cinematic glyphs: {}", e.toString());
        }
        return meta;
    }

    /** 부제 한 장 (x = fu, 화면 가운데 0 — 끝에서 0 으로 돌아와 너비 0) */
    static final class Cine {
        private final MutableComponent root = Component.empty().withStyle(Style.EMPTY.withColor(0xFFFFFF).withShadowColor(0));
        private int cursor;

        private void move(int x) {
            if (x == cursor) return;
            root.append(Component.literal(VanillaGui.spaces(x - cursor)).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(VanillaGui.SPACE))));
            cursor = x;
        }

        private void put(Identifier font, String ch, int rgb) {
            root.append(Component.literal(ch).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(font)).withColor(rgb)));
        }

        /** 그림 (이름) 의 가운데를 x 에 */
        void glyph(String name, int x, int rgb) {
            G g = meta().glyphs.get(name);
            if (g == null) return;
            move(x + g.x());
            put(g.font(), g.ch(), rgb);
            cursor += g.adv();
        }

        /** 룬 그림 (row -1 = 가운데 큰 것, 0 · 1 = 10회 줄) 의 가운데를 x 에 */
        void rune(String tex, int row, int x) {
            Meta m = meta();
            RuneG g = m.runes.get(tex);
            if (g == null) return;
            int size = row < 0 ? m.bigRune : m.smallRune;
            move(x - size / 2);
            put(Mmorpg.rpg(row < 0 ? "cine_c" : "cine_g" + row), g.ch(), 0xFFFFFF);
            cursor += row < 0 ? g.big() : g.small();
        }

        int textWidth(String font, String s) {
            double[] f = meta().text.get(font);
            if (f == null) return 0;
            int w = 0;
            String d = VanillaGui.drawable(s);
            for (int i = 0; i < d.length(); ) {
                int cp = d.codePointAt(i);
                i += Character.charCount(cp);
                w += cp == ' ' ? (int) f[1] : (int) (0.5 + (VanillaGui.charWidth(cp) - 1) * f[0]) + 1;
            }
            return w;
        }

        /** 글자의 가운데를 x 에 */
        void text(String font, String s, int rgb, int x) {
            if (s == null || s.isEmpty() || !meta().text.containsKey(font)) return;
            int w = textWidth(font, s);
            move(x - w / 2);
            put(Mmorpg.rpg(font), VanillaGui.drawable(s), rgb);
            cursor += w;
        }

        Component build() {
            move(0);
            return root;
        }
    }
}
