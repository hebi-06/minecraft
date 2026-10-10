package mmorpg.fabric.npc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.shop.Store;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.ui.DialogForm;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * RPG NPC 누르기 · 연출 (npc.js): NPC 설정 도구를 들고 누름 → 그 NPC 설정 창 (운영자 · 크리에이티브), RPG NPC 누름 → 대화 (인사말 · 퀘스트 · 상점),
 * 크리에이티브에서 웅크리고 누름 → 그냥 (바닐라). 0.5초마다: 머리 위 표시 (받을 퀘스트 노랑 · 보고할 퀘스트 흰 반짝임 · 반복 의뢰 파랑 —
 * 그 사람에게만, 상점 초록 · 대장장이 불똥 · 룬술사 보랏빛), 가까이 가면 화면 하단 안내 · 소리, 이름표의 [!] 맞추기, 예전 메인 NPC 치우기.
 * 자바 마네킹은 스스로 고개를 돌리지 않아서 가까운 사람을 바라보게 함 (베드락 NPC 처럼).
 * 퀘스트 쪽 (quest) 이 badgeCount · questMark · talkTo 를 채움 (그 전에는 인사말 + 상점 · 대장장이 · 룬술사만).
 */
public final class Npcs {
    private Npcs() {}

    private static final Obj NPC = Rules.get().obj("NPC");
    private static final boolean EFFECTS = Rules.get().obj("CONFIG").truthy("NPC_EFFECTS");

    // ------------------------------------------------------------ 퀘스트 쪽이 채우는 것
    /** 이 NPC 번호가 주는 퀘스트 수 (quest.js badgeCount) */
    public static ToIntFunction<String> badgeCount = k -> 0;
    /** 이 NPC 에 퀘스트가 있는지 (quest.js hasQuestFor) */
    public static Predicate<String> hasQuestFor = k -> false;
    /** 이 사람에게 이 NPC 의 표시: "ready" · "available" · "repeat" · null (quest.js questMark) */
    public static BiFunction<ServerPlayer, String, String> questMark = (sp, k) -> null;
    /** 대화 (quest.js talkTo — 창 흐름) */
    public static BiConsumer<ServerPlayer, Mannequin> talkTo = Npcs::basicTalk;
    /** 메인 퀘스트 NPC 번호인지 (mainquest.js mainNpcByKey — 예전에 세운 중복을 치울 때) */
    public static Predicate<String> isMainKey = k -> k != null && k.startsWith("main_");
    /** NPC 설정 창 (editor.js openNpcSettings) */
    public static BiConsumer<ServerPlayer, Mannequin> openSettings = (sp, npc) -> Notify.say(sp, "§7NPC 설정 창 준비 중 (번호 " + NpcData.key(npc, true) + ")");
    /** NPC 관리 창 (editor.js openNpcManager) */
    public static Consumer<ServerPlayer> openManager = sp -> Notify.say(sp, "§7NPC 관리 창 준비 중");

    /** 설정 도구로 NPC 를 누른 틱 (같은 누름의 아이템 사용으로 관리 창이 열리지 않게) */
    private static final Map<UUID, Long> TOOL_TAP = new HashMap<>();
    /** "플레이어|NPC" → 가까이 온 틱 */
    private static final Map<String, Long> NEAR_SINCE = new HashMap<>();

    public static void init() {
        UseEntityCallback.EVENT.register(Npcs::onUse);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = Tasks.now();
            if (now % 10 == 0) effectsTick(server, now);
            lookTick(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOOL_TAP.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            TOOL_TAP.clear();
            NEAR_SINCE.clear();
        });
    }

    /** 설정 도구로 NPC (또는 허수아비) 를 눌렀다고 적음 */
    public static void markToolTap(ServerPlayer sp) {
        TOOL_TAP.put(sp.getUUID(), Tasks.now());
    }

    /** 방금 (2틱 안에) 설정 도구로 NPC 를 눌렀는지 */
    public static boolean recentToolTap(ServerPlayer sp) {
        return Tasks.now() - TOOL_TAP.getOrDefault(sp.getUUID(), -100L) <= 2;
    }

    /** NPC 설정 도구로 허공을 누름 (main.js itemUse) — NPC 를 누른 같은 때가 아니면 관리 창 */
    public static void onToolUse(ServerPlayer sp) {
        if (!recentToolTap(sp)) openManager.accept(sp);
    }

    private static InteractionResult onUse(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand, Entity target,
            net.minecraft.world.phys.EntityHitResult hit) {
        if (!(target instanceof Mannequin npc)) return InteractionResult.PASS;
        boolean tool = player.getItemInHand(hand).is(GearItems.NPC_TOOL);
        if (!tool && !NpcData.isRpgNpc(npc)) return InteractionResult.PASS;
        if (!tool && player.isCreative() && player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
        if (tool) {
            markToolTap(sp);
            Tasks.later(1, () -> {
                if (!sp.isRemoved() && npc.isAlive()) openSettings.accept(sp, npc);
            });
            return InteractionResult.SUCCESS;
        }
        if (Forms.isOpen(sp)) return InteractionResult.SUCCESS;
        Tasks.later(1, () -> {
            if (!sp.isRemoved() && npc.isAlive() && !Forms.isOpen(sp)) talkTo.accept(sp, npc);
        });
        return InteractionResult.SUCCESS;
    }

    /** 퀘스트가 없을 때의 대화 (인사말 + 상점 · 대장장이 · 룬술사) */
    private static void basicTalk(ServerPlayer sp, Mannequin npc) {
        NpcData.key(npc, true);
        String name = NpcData.name(npc);
        int lv = NpcData.shopLevel(npc);
        boolean smith = NpcData.isSmith(npc);
        boolean runesmith = NpcData.isRunesmith(npc);
        String greet = NpcData.greeting(npc);
        int services = (lv >= 0 ? 1 : 0) + (smith ? 1 : 0) + (runesmith ? 1 : 0);
        if (greet.isEmpty() && services == 1) {
            if (smith) Smith.smithLoop(sp, "enhance", null);
            else if (runesmith) RuneSmith.runesmithLoop(sp, "grind", null);
            else Store.storeLoop(sp, lv, "weapons", null);
            return;
        }
        DialogForm.showPages(sp, name, greet, "…", last -> {
            if (last == null) return;
            DialogForm d = new DialogForm(name, last);
            List<String> kinds = new java.util.ArrayList<>();
            if (lv >= 0) {
                d.choice("§a무기 · 갑옷 상점 §7(" + lv + "렙)", DialogForm.SHOP);
                kinds.add("shop");
            }
            if (smith) {
                d.choice("§6무기 강화 · 레벨 올리기", mmorpg.fabric.ui.Icon.item(net.minecraft.world.item.Items.ANVIL));
                kinds.add("smith");
            }
            if (runesmith) {
                d.choice("§d룬 갈기 · 뽑기권", mmorpg.fabric.ui.Icon.item("rpg:rune_shard"));
                kinds.add("runesmith");
            }
            d.choice("§7안녕히 계세요", DialogForm.BYE);
            d.show(sp, sel -> {
                String k = sel >= 0 && sel < kinds.size() ? kinds.get(sel) : "";
                switch (k) {
                    case "shop" -> Store.storeLoop(sp, lv, "weapons", null);
                    case "smith" -> Smith.smithLoop(sp, "enhance", null);
                    case "runesmith" -> RuneSmith.runesmithLoop(sp, "grind", null);
                    default -> {}
                }
            });
        });
    }

    // ------------------------------------------------------------ 연출

    private static String promptText(String mark, int lv, String name, boolean smith, boolean runesmith) {
        if ("ready".equals(mark)) return "§a§l? §r§f" + name + " §7- 눌러서 퀘스트 완료 보고";
        if ("available".equals(mark)) return "§e§l! §r§f" + name + " §7- 눌러서 퀘스트 받기";
        if ("repeat".equals(mark)) return "§b§l! §r§f" + name + " §7- 눌러서 반복 의뢰 받기";
        if (smith) return "§6§l[대장장이] §r§f" + name + " §7- 눌러서 무기 강화·레벨 올리기";
        if (runesmith) return "§d§l[룬술사] §r§f" + name + " §7- 눌러서 룬 갈기·뽑기권 사기";
        if (lv >= 0) return "§a§l[상점] §r§f" + name + " §7- 눌러서 무기·갑옷 상점·전리품 팔기";
        return "§f" + name + " §7- 눌러서 대화하기";
    }

    /** 애드온 파티클 이름 → 자바 (머리 위 표시) */
    private static void markParticle(ServerPlayer only, ServerLevel level, String bedrock, Vec3 at) {
        ParticleOptions p = switch (bedrock) {
            case "minecraft:totem_particle" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "minecraft:endrod" -> ParticleTypes.END_ROD;
            case "minecraft:blue_flame_particle" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "minecraft:villager_happy" -> ParticleTypes.HAPPY_VILLAGER;
            case "minecraft:lava_particle" -> ParticleTypes.LAVA;
            case "minecraft:mob_portal" -> ParticleTypes.PORTAL;
            default -> ParticleTypes.END_ROD;
        };
        int n = p == ParticleTypes.TOTEM_OF_UNDYING ? 3 : p == ParticleTypes.PORTAL ? 4 : 1;
        double speed = p == ParticleTypes.TOTEM_OF_UNDYING ? 0.08 : p == ParticleTypes.PORTAL ? 0.3 : 0.01;
        if (only != null) level.sendParticles(only, p, true, false, at.x, at.y, at.z, n, 0.12, 0.08, 0.12, speed);
        else level.sendParticles(p, at.x, at.y, at.z, n, 0.12, 0.08, 0.12, speed);
    }

    /** 0.5초마다 (npcEffectsTick) */
    private static void effectsTick(MinecraftServer server, long now) {
        if (!EFFECTS) return;
        double range = NPC.num("particleRange");
        double prompt = NPC.num("promptRange");
        Set<UUID> shopDone = new HashSet<>();
        Set<UUID> checked = new HashSet<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ServerLevel level = sp.level();
            List<Mannequin> npcs = level.getEntitiesOfClass(Mannequin.class, sp.getBoundingBox().inflate(range), NpcData::isRpgNpc);
            for (Mannequin npc : npcs) {
                if (npc.distanceTo(sp) > range) continue;
                String key = NpcData.key(npc);
                if (checked.add(npc.getUUID())) {
                    // 같은 번호의 메인 NPC 를 다른 곳에 새로 세웠으면 예전 것은 치움
                    if (staleMainNpc(npc, key)) {
                        Vec3 h = npc.getEyePosition();
                        SkillFx.vanilla(level, "minecraft:death_explosion_emitter", new Vec3(h.x, h.y - 0.6, h.z));
                        npc.discard();
                        continue;
                    }
                    int count = badgeCount.applyAsInt(key);
                    if ((count > 0) != npc.entityTags().contains("rpg_quest") || npc.getCustomName() == null) NpcData.refresh(npc, count);
                    if (!key.isEmpty()) NpcData.notePos(npc, key, null, null);
                }
                if (!npc.isAlive()) continue;
                String mark = questMark.apply(sp, key);
                int lv = NpcData.shopLevel(npc);
                boolean smith = NpcData.isSmith(npc);
                boolean runesmith = NpcData.isRunesmith(npc);
                Vec3 h = npc.getEyePosition();
                Vec3 at = new Vec3(h.x, h.y + 0.75, h.z);
                if (mark != null) {
                    markParticle(sp, level, "ready".equals(mark) ? NPC.str("readyParticle") : "repeat".equals(mark) ? NPC.str("repeatParticle") : NPC.str("questParticle"), at);
                } else if ((lv >= 0 || smith || runesmith) && shopDone.add(npc.getUUID())) {
                    markParticle(null, level, smith ? NPC.str("smithParticle") : runesmith ? NPC.str("runesmithParticle") : NPC.str("shopParticle"), at);
                }
                String pk = sp.getUUID() + "|" + npc.getUUID();
                double d = npc.position().distanceTo(sp.position());
                if (d <= prompt) {
                    Long since = NEAR_SINCE.get(pk);
                    if (since == null) {
                        NEAR_SINCE.put(pk, now);
                        if (mark != null) SkillFx.playFor(sp, "random.orb", "ready".equals(mark) ? 1.8 : 1.4, 0.7);
                        else if (smith) SkillFx.playFor(sp, "random.anvil_land", 1.4, 0.35);
                        else if (runesmith) SkillFx.playFor(sp, "chime.amethyst_block", 1.2, 0.6);
                        else if (lv >= 0) SkillFx.playFor(sp, "mob.villager.yes", 1, 0.8);
                    }
                    boolean due = since == null || (now - since) % 40 < 10;
                    if (due && !Forms.isOpen(sp) && Notify.actionbarFree(sp)) {
                        Notify.notify(sp, promptText(mark, lv, NpcData.name(npc), smith, runesmith), 12);
                    }
                } else if (NEAR_SINCE.containsKey(pk) && d > prompt + 1) {
                    NEAR_SINCE.remove(pk);
                }
            }
        }
        if (NEAR_SINCE.size() > 500) NEAR_SINCE.clear();
        if (TOOL_TAP.size() > 200) TOOL_TAP.clear();
    }

    /** 예전에 세운 메인 NPC (같은 번호로 더 나중에 세운 것이 있음) 인지 */
    public static boolean staleMainNpc(Entity npc, String key) {
        if (!isMainKey.test(key)) return false;
        NpcData.Spot rec = NpcData.mainRecord(npc.level().getServer(), key);
        return rec != null && !rec.id().equals(npc.getUUID().toString());
    }

    /**
     * 틱마다: RPG NPC 는 6칸 안의 가장 가까운 사람을 바라봄 (없으면 몸 방향으로). 남은 각도의 절반씩 (한 틱에 30° 까지), 움직이는 사람은
     * 3틱 앞 자리를 봄 (움직임 → 서버 → 머리 패킷 → 클라이언트 보간으로 늦는 만큼) — 옆을 걸어 지나가는 사람을 늦지 않게 따라감
     * (화면 시험: 2틱마다 35% 로는 걷는 사람보다 32° · 틱마다 50% 만으로는 22° 늦었음)
     */
    private static void lookTick(MinecraftServer server) {
        Set<UUID> done = new HashSet<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator()) continue;
            for (Mannequin npc : sp.level().getEntitiesOfClass(Mannequin.class, sp.getBoundingBox().inflate(8), NpcData::isRpgNpc)) {
                if (!done.add(npc.getUUID())) continue;
                ServerPlayer near = null;
                double best = 6 * 6;
                for (ServerPlayer p : sp.level().players()) {
                    if (p.isSpectator() || p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) continue;
                    double dd = p.distanceToSqr(npc);
                    if (dd < best) {
                        best = dd;
                        near = p;
                    }
                }
                float body = npc.getYRot();
                float wantYaw = body;
                float wantPitch = 0;
                if (near != null) {
                    // 클라이언트가 알려 준 한 틱 움직임 (순간이동 같은 큰 값은 0.5칸으로 줄임) × 3틱
                    Vec3 move = near.getKnownMovement();
                    double mh = Math.hypot(move.x, move.z);
                    double k = mh > 0.5 ? 0.5 / mh : 1;
                    Vec3 at = near.getEyePosition().add(move.x * k * 3, 0, move.z * k * 3);
                    float[] r = Kit.lookRotation(npc.getEyePosition(), at);
                    // 몸 방향에서 75° 넘게는 돌리지 않음 (사람 고개처럼)
                    float diff = wrap(r[0] - body);
                    wantYaw = body + Math.max(-75, Math.min(75, diff));
                    wantPitch = Math.max(-30, Math.min(30, r[1]));
                }
                float yaw = npc.getYHeadRot() + Math.max(-30F, Math.min(30F, wrap(wantYaw - npc.getYHeadRot()) * 0.5F));
                float pitch = npc.getXRot() + (wantPitch - npc.getXRot()) * 0.5F;
                if (Math.abs(wrap(yaw - npc.getYHeadRot())) > 0.5F) npc.setYHeadRot(yaw);
                if (Math.abs(pitch - npc.getXRot()) > 0.5F) npc.setXRot(pitch);
            }
        }
    }

    private static float wrap(float a) {
        a %= 360F;
        if (a >= 180F) a -= 360F;
        if (a < -180F) a += 360F;
        return a;
    }

    /** 시험용: 이 영역의 RPG NPC */
    public static List<Mannequin> within(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(Mannequin.class, box, NpcData::isRpgNpc);
    }
}
