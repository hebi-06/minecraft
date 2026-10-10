package mmorpg.fabric.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Brightness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 훈련용 허수아비 (dummy.js — v1.2.2, v1.7.0 이름 바꿈): 죽지 않는 샌드백. 맞은 피해를 모아 머리 위에 피해 합 · 초당 피해 · 횟수 · 최고 한 방.
 * - 소환: /dummy (운영자, 눈앞 3칸에 나를 보게), 소환 알 "훈련용 허수아비 소환" (크리에이티브: RPG 장비 · 재료 탭 끝 · 생성 알 탭)
 * - 치우기: /dummy clear (주변 48칸), NPC 설정 도구를 들고 허수아비 누르기 (운영자 · 크리에이티브), /kill
 * - 5초 동안 안 맞으면 기록을 지움. 밀려나도 제자리 (anchor) 로 돌아옴
 * 자바에서 달라진 점: 머리 위 표시는 여러 줄 이름표 대신 글자 표시 엔티티 (저장하지 않고 허수아비가 다시 만듦), 제자리 돌아오기는 맞은 2틱 뒤
 * 대신 틱마다 (밀린 만큼 바로), 명령어 · 순간이동으로 8칸 넘게 옮기면 그곳을 새 제자리로. 엔티티 · 모델은 TrainingDummy · 클라이언트 DummyRenderer.
 */
public final class Dummy {
    private Dummy() {}

    /** 엔티티 ID (애드온과 같음) */
    public static final String ID = "rpg:training_dummy";
    /** 무리 (베드락 type_family — 엔티티 태그로: Kit.hasFamily(e, "rpg_dummy")) */
    public static final String FAMILY = "rpg_dummy";
    public static final String NAME = "훈련용 허수아비";
    /** 이만큼 (틱) 안 맞으면 기록을 지움 (DUMMY_IDLE_TICKS) */
    public static final int IDLE_TICKS = 100;
    /** 머리 위 표시 엔티티의 태그 */
    public static final String LABEL_TAG = "mmorpg_dummy_label";
    private static final String ANCHOR = "rpg:anchor";
    /** 허수아비 키 (2.3) 위 표시 자리 */
    private static final double LABEL_Y = 2.65;
    /** 피해 숫자를 띄울 높이 (발에서 — 몸통) */
    private static final double NUMBER_Y = 1.25;
    /** 이보다 멀리 옮겨졌으면 밀린 것이 아니라 옮긴 것 (새 제자리) */
    private static final double MOVED_FAR = 8;

    public static EntityType<TrainingDummy> TYPE;
    public static Item EGG;

    /** 허수아비 → 이번 기록 */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** 기록 하나 (dummy.js sessions 의 값): 처음 · 마지막으로 맞은 틱, 피해 합, 횟수, 최고 한 방 */
    public static final class Session {
        TrainingDummy e;
        final long first;
        long last;
        double total;
        int hits;
        double max;

        Session(TrainingDummy e, long now) {
            this.e = e;
            this.first = now;
            this.last = now;
        }

        public long first() {
            return first;
        }

        public long last() {
            return last;
        }

        public double total() {
            return total;
        }

        public int hits() {
            return hits;
        }

        public double max() {
            return max;
        }
    }

    // ------------------------------------------------------------ 등록

    /** Mmorpg.onInitialize (레지스트리가 얼기 전): 엔티티 · 기본 속성 · 소환 알 · 크리에이티브 탭 · 명령어 · 누르기 · 틱 */
    public static void init() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Mmorpg.rpg("training_dummy"));
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC)
                .sized(0.8F, 2.3F)
                .eyeHeight(2.0F)
                .fireImmune()
                .clientTrackingRange(10)
                .noLootTable()
                .build(key));
        FabricDefaultAttributeRegistry.register(TYPE, TrainingDummy.attributes());
        ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, Mmorpg.rpg("training_dummy_spawn_egg"));
        EGG = Registry.register(BuiltInRegistries.ITEM, eggKey, new EggItem(new Item.Properties().spawnEgg(TYPE).setId(eggKey)));
        CreativeModeTabEvents.modifyOutputEvent(GearItems.TAB).register(output -> output.accept(EGG));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(EGG));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
        UseEntityCallback.EVENT.register(Dummy::onUse);
        // 피해 숫자는 몸통 높이에서 (머리 위에 기록이 떠 있어서 — fx.js showDamageText)
        java.util.function.Function<net.minecraft.world.entity.LivingEntity, Vec3> prev = DamageNumbers.anchor;
        DamageNumbers.anchor = e -> e instanceof TrainingDummy ? new Vec3(e.getX(), e.getY() + NUMBER_Y, e.getZ()) : prev.apply(e);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 10 == 0) idleTick();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    /** 소환 알 (이름은 애드온 그대로) */
    static final class EggItem extends SpawnEggItem {
        EggItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.literal(NAME + " 소환");
        }
    }

    /** 훈련용 허수아비인지 (퀘스트 · 길 안내 등에서 — 몬스터로 세지 않게) */
    public static boolean isDummy(Entity e) {
        return e instanceof TrainingDummy;
    }

    // ------------------------------------------------------------ 표시 (dummyNum · dummyLabel)

    /** 1234.5 → "1,234.5" (100 이상은 정수 — dummyNum) */
    public static String dummyNum(double n) {
        double v = Math.max(0, n);
        String s = v >= 100 ? Js.str(Js.round(v)) : Js.toFixed(Js.round(v * 10) / 10, 1);
        int dot = s.indexOf('.');
        String i = dot < 0 ? s : s.substring(0, dot);
        StringBuilder withComma = new StringBuilder();
        for (int k = 0; k < i.length(); k++) {
            if (k > 0 && (i.length() - k) % 3 == 0 && Character.isDigit(i.charAt(k - 1))) withComma.append(',');
            withComma.append(i.charAt(k));
        }
        return dot < 0 ? withComma.toString() : withComma + s.substring(dot);
    }

    /** 머리 위 표시 (기록이 없으면 안내 — dummyLabel) */
    public static String dummyLabel(Session s) {
        if (s == null || s.hits == 0) return "§6§l훈련용 허수아비§r\n§7때리면 피해 합·초당 피해가 나옵니다";
        double secs = Math.max(1, (s.last - s.first) / 20.0 + 0.5);
        return "§6§l훈련용 허수아비§r\n§f합 §e" + dummyNum(s.total) + " §7· §f초당 §e" + dummyNum(s.total / secs) + "\n§7" + s.hits + "회 · 최고 한 방 §f"
                + dummyNum(s.max);
    }

    /** 이 허수아비의 이번 기록 (없으면 null — dummySession) */
    public static Session dummySession(Entity e) {
        return SESSIONS.get(e.getUUID());
    }

    /** 지금 머리 위 글자 (베드락 서식, 표시가 없으면 null) */
    public static String labelOf(Entity e) {
        return e instanceof TrainingDummy d ? d.labelText : null;
    }

    /** 머리 위 표시 엔티티 (없으면 null — 시험용) */
    public static Display.TextDisplay labelEntity(Entity e) {
        return e instanceof TrainingDummy d && d.label != null && !d.label.isRemoved() ? d.label : null;
    }

    private static Vec3 labelPos(TrainingDummy e) {
        return new Vec3(e.getX(), e.getY() + LABEL_Y, e.getZ());
    }

    /** 머리 위 글자를 바꿈 (표시가 없으면 만듦) */
    static void setLabel(TrainingDummy e, String text) {
        e.labelText = text;
        if (!(e.level() instanceof ServerLevel level) || e.isRemoved()) return;
        if (e.label == null || e.label.isRemoved()) {
            e.label = spawnLabel(level, labelPos(e), text);
            return;
        }
        e.label.setText(Component.literal(BedrockText.toJava(text)));
    }

    /**
     * 글자 표시 엔티티 (여러 줄 — 늘 카메라를 보고, 밤에도 밝게, 이름표처럼 반투명 검은 바탕). 저장하지 않음 (서버가 꺼지거나 청크가 내려가면
     * 사라지고 허수아비가 다시 만듦)
     */
    private static Display.TextDisplay spawnLabel(ServerLevel level, Vec3 at, String text) {
        Display.TextDisplay td = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level) {
            @Override
            public boolean shouldBeSaved() {
                return false;
            }
        };
        td.setText(Component.literal(BedrockText.toJava(text)));
        td.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        td.setFlags(Display.TextDisplay.FLAG_SHADOW);
        td.setBrightnessOverride(Brightness.FULL_BRIGHT);
        td.setPosRotInterpolationDuration(2);
        td.snapTo(at.x, at.y, at.z, 0F, 0F);
        td.addTag(LABEL_TAG);
        return level.addFreshEntity(td) ? td : null;
    }

    /** 표시를 치움 (허수아비를 치울 때) */
    static void dropLabel(TrainingDummy e) {
        if (e.label != null && !e.label.isRemoved()) e.label.discard();
        e.label = null;
    }

    // ------------------------------------------------------------ 제자리 (anchor)

    /** 제자리 (없으면 null) */
    public static Vec3 anchorOf(Entity e) {
        String raw = Props.get(e, ANCHOR);
        if (raw == null) return null;
        try {
            JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
            return new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    static void setAnchor(Entity e, Vec3 loc) {
        Props.set(e, ANCHOR, "{\"x\":" + Js.str(loc.x) + ",\"y\":" + Js.str(loc.y) + ",\"z\":" + Js.str(loc.z) + "}");
    }

    /** 밀려났으면 제자리로 (높이는 그대로 두어 땅에 서게 — snapBack) */
    static void snapBack(TrainingDummy e) {
        if (e.isRemoved()) return;
        Vec3 a = anchorOf(e);
        if (a == null) return;
        double d = Math.hypot(e.getX() - a.x, e.getZ() - a.z);
        if (d <= 0.05) return;
        if (d > MOVED_FAR) {
            setAnchor(e, e.position()); // 명령어 · 순간이동으로 옮긴 것: 그곳이 새 제자리
            return;
        }
        e.teleportTo(a.x, e.getY(), a.z);
        e.setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------ 맞음 · 틱

    /** 맞음 (TrainingDummy.actuallyHurt — 방어 · 저항 뒤 피해, 체력은 깎지 않음): 기록 · 머리 위 표시 (onHurtAfter) */
    static void onHurt(TrainingDummy e, double damage) {
        double d = Math.max(0, damage);
        long now = Tasks.now();
        Session s = SESSIONS.get(e.getUUID());
        if (s == null || now - s.last > IDLE_TICKS) {
            s = new Session(e, now);
            SESSIONS.put(e.getUUID(), s);
        }
        s.e = e;
        s.last = now;
        s.total += d;
        s.hits += 1;
        s.max = Math.max(s.max, d);
        setLabel(e, dummyLabel(s));
        if (anchorOf(e) == null) setAnchor(e, e.position());
    }

    /** 허수아비의 서버 틱: 체력 · 제자리 · 머리 위 표시 (알 · /summon 으로 세운 것도 처음 틱에 제자리 · 안내 표시) */
    static void serverTick(TrainingDummy e) {
        if (e.isRemoved()) return;
        if (e.getHealth() < e.getMaxHealth() && !e.isDeadOrDying()) e.setHealth(e.getMaxHealth()); // 명령어로 줄인 체력 등
        if (anchorOf(e) == null) setAnchor(e, e.position());
        snapBack(e);
        if (e.label == null || e.label.isRemoved()) setLabel(e, e.labelText != null ? e.labelText : dummyLabel(SESSIONS.get(e.getUUID())));
        else {
            Vec3 want = labelPos(e);
            if (e.label.position().distanceToSqr(want) > 1e-4) e.label.setPos(want.x, want.y, want.z);
        }
    }

    /** 5초 동안 안 맞은 기록 지우기 (10틱마다 — tick) */
    static void idleTick() {
        long now = Tasks.now();
        for (Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator(); it.hasNext(); ) {
            Session s = it.next().getValue();
            if (now - s.last <= IDLE_TICKS) continue;
            it.remove();
            if (s.e == null || s.e.isRemoved()) continue;
            setLabel(s.e, dummyLabel(null));
            snapBack(s.e);
        }
    }

    // ------------------------------------------------------------ 세우기 · 치우기

    /** 터지는 연기 + 뽁 소리 (poof) */
    private static void poof(ServerLevel level, Vec3 loc) {
        SkillFx.vanilla(level, "minecraft:death_explosion_emitter", new Vec3(loc.x, loc.y + 1, loc.z));
        SkillFx.play(level, "random.pop", loc, 0.8, 0.9);
    }

    /** 눈앞 3칸에 허수아비 (플레이어를 보게 — spawnDummy). 못 세웠으면 null */
    public static TrainingDummy spawnDummy(ServerPlayer sp) {
        ServerLevel level = sp.level();
        Vec3 v = sp.getViewVector(1F);
        double l = Math.hypot(v.x, v.z);
        if (l < 1e-6) l = 1;
        Vec3 p = sp.position();
        Vec3 loc = new Vec3(p.x + v.x / l * 3, p.y, p.z + v.z / l * 3);
        TrainingDummy e = TYPE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (e == null) return null;
        float yaw = Kit.lookRotation(loc, new Vec3(p.x, loc.y, p.z))[0];
        e.snapTo(loc.x, loc.y, loc.z, yaw, 0F);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        if (!level.addFreshEntity(e)) return null;
        setAnchor(e, loc);
        setLabel(e, dummyLabel(null));
        poof(level, loc);
        return e;
    }

    /** 허수아비 치우기 (기록 · 표시도 — removeDummy) */
    public static void removeDummy(TrainingDummy e) {
        if (e.isRemoved()) return;
        SESSIONS.remove(e.getUUID());
        Vec3 loc = e.position();
        ServerLevel level = e.level() instanceof ServerLevel l ? l : null;
        e.discard();
        if (level != null) poof(level, loc);
    }

    /** 주변 허수아비 치우기. 치운 수 (clearDummies) */
    public static int clearDummies(ServerPlayer sp, double radius) {
        Vec3 c = sp.position();
        List<TrainingDummy> list = new ArrayList<>(sp.level().getEntitiesOfClass(TrainingDummy.class, new AABB(c, c).inflate(radius),
                e -> e.position().distanceTo(c) <= radius));
        for (TrainingDummy e : list) removeDummy(e);
        return list.size();
    }

    /** 운영자이거나 크리에이티브 (canManage) */
    static boolean canManage(ServerPlayer sp) {
        return sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || sp.isCreative();
    }

    /** NPC 설정 도구를 들고 허수아비 누르기 → 치우기 (운영자 · 크리에이티브). 같은 누름의 도구 사용이 NPC 관리 창을 열지 않게 표시 */
    private static InteractionResult onUse(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand, Entity target,
            net.minecraft.world.phys.EntityHitResult hit) {
        if (!(target instanceof TrainingDummy dummy) || GearItems.NPC_TOOL == null || !player.getItemInHand(hand).is(GearItems.NPC_TOOL)) return InteractionResult.PASS;
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Npcs.markToolTap(sp);
        Tasks.later(1, () -> {
            if (sp.isRemoved()) return;
            if (!canManage(sp)) {
                Notify.warn(sp, "§c훈련용 허수아비는 운영자 또는 크리에이티브 모드에서만 치울 수 있습니다");
                return;
            }
            removeDummy(dummy);
            Notify.notify(sp, "§7훈련용 허수아비를 치웠습니다", 30);
        });
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------ 명령어 (commands.js /dummy [spawn|clear] — 운영자)

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("dummy").requires(Dummy::admin)
                .executes(Dummy::spawnCommand)
                .then(Commands.literal("spawn").executes(Dummy::spawnCommand))
                .then(Commands.literal("clear").executes(Dummy::clearCommand)));
    }

    private static int spawnCommand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        if (spawnDummy(sp) == null) {
            Notify.say(sp, "§c훈련용 허수아비를 세우지 못했습니다.");
            return 0;
        }
        Notify.say(sp, "훈련용 허수아비를 세웠습니다. §7(때리면 머리 위에 피해 합·초당 피해, 5초 동안 안 맞으면 초기화)");
        return 1;
    }

    private static int clearCommand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int n = clearDummies(sp, 48);
        Notify.say(sp, "훈련용 허수아비 " + n + "개를 치웠습니다.");
        return n;
    }

    /** 시험용: 지금 기록 수 */
    public static int sessionCount() {
        return SESSIONS.size();
    }
}
