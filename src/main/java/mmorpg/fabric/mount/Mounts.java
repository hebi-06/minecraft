package mmorpg.fabric.mount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mmorpg.core.Json;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.RpgEntities;
import mmorpg.fabric.mob.RpgMount;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.MenuForm;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 탈것 (mount.js 의 자바 판 + 6단계 여러 탈것). 스탯 창 탈것 칸을 누르면 고른 탈것을 불러 바로 태움 → 내리면 (웅크리기 · 죽음 · 순간이동 · 나가기) 사라짐.
 * <ul>
 * <li>여러 탈것 (6단계 — 사용자 요청 "탈것 아이템을 사용하면 탈것이 추가되고 쉬프트 누르고 탈것 클릭하면 탈것 변경"): 탈것 목록은 mounts.json ({@link MountDefs}).
 * 내 탈것 = 처음부터 가진 탈것 (starter) + 탈것 아이템으로 얻은 것 (플레이어 데이터 rpg:mounts {owned, current} — 죽어도 그대로).
 * 스탯 창 탈것 칸 Shift + 누르기 (또는 /mount select) = 탈것 고르기 창 (탄 채로 고르면 바로 갈아탐)</li>
 * <li>소환: 발밑에 그 탈것 색의 룬 · 퍼지는 고리 두 겹 · 빛기둥 · 터짐 · 반짝이 · 파티클 + 소리 (mounts.json — 다이어울프는 큰 늑대 으르렁 + 바람). 보던 방향으로</li>
 * <li>탄 동안: 달리면 발밑에 반짝이 (6틱마다). 공격 · 스킬 · 룬은 막힘 ({@link Riding}, 탄 사람이 주는 피해도 막음). 탈것은 다치지 않음</li>
 * <li>사라짐: 몸에서 터짐 · 고리 · 반짝이 · 파티클 · 연기 + 소리. 다시 부르기는 {@link #COOLDOWN} 틱 뒤</li>
 * <li>탄 사람이 없는 탈것 (부른 기록이 없는 것 — /summon 등) 은 스스로 치움 (탄 플레이어가 있으면 그 사람의 탈것으로 기록)</li>
 * <li>발 위 칸부터 탄 사람의 머리 끝까지 막혀 있으면 부르지 않음 (천장에 머리가 끼어 숨 막힘 피해를 받지 않게 — 충돌 없는 블록 (풀 · 꽃 · 횃불 …) · 액체는 괜찮음, 불은 막힘)</li>
 * </ul>
 */
public final class Mounts {
    private Mounts() {}

    /** 플레이어 데이터: 탈것 아이템으로 얻은 탈것 · 고른 탈것 (JSON {"owned": [키…], "current": 키}) */
    public static final String DATA = "rpg:mounts";
    /** 탈것에 적는 주인 (플레이어 UUID) */
    public static final String OWNER = "rpg:mount_owner";
    /** 애드온 탈것 설정 (rules.json MOUNT — 대기 · 태우기 재시도 · 안내 간격) */
    static final mmorpg.core.Obj MOUNT = mmorpg.core.Rules.get().obj("MOUNT");
    /** 사라진 뒤 다시 부를 수 있을 때까지 (틱 — MOUNT.cooldownTicks 40) */
    public static final int COOLDOWN = (int) MOUNT.num("cooldownTicks");
    /** 부른 뒤 탄 사람을 확인하기 시작할 때까지 (틱 — 그동안 태우기를 다시 시도, MOUNT.graceTicks 10) */
    public static final int GRACE = (int) MOUNT.num("graceTicks");
    /** 달릴 때 반짝이 간격 (틱) · 그만큼 움직였을 때만 (칸) */
    static final int TRAIL_EVERY = 6;
    static final double TRAIL_MOVE = 0.6;
    /** 탄 사람의 키 (자리에서 머리 끝까지 — 플레이어 1.8) · 자바 플레이어가 탈것에 붙는 높이 (발 위 0.6) */
    static final double RIDER_H = 1.8;
    static final double RIDER_ATTACH = 0.6;
    static final double[] WHITE = {1, 1, 1};

    /** 부른 탈것 하나 (seated = 한 번이라도 탄 것을 확인함) */
    static final class Active {
        final RpgMount e;
        final String key;
        final long born;
        boolean seated;
        Vec3 last;

        Active(RpgMount e, String key, long born, boolean seated) {
            this.e = e;
            this.key = key;
            this.born = born;
            this.seated = seated;
            this.last = e.position();
        }
    }

    /** 플레이어 → 부른 탈것 */
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    /** 플레이어 → 탈것이 사라진 틱 (다시 부르기 대기) */
    private static final Map<UUID, Long> GONE_AT = new HashMap<>();

    public static void init() {
        RpgMount.manager = new RpgMount.Manager() {
            @Override
            public void tick(RpgMount m) {
                strayCheck(m);
            }

            @Override
            public void jump(RpgMount m, int power) {
                jumpFx(m, power);
            }
        };
        Loot.mounted = Riding::isMounted; // 탈것에 탄 동안 지속 피해로 잡아도 잡은 것이 아님
        ServerTickEvents.END_SERVER_TICK.register(Mounts::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ACTIVE.clear();
            GONE_AT.clear();
            Riding.clear();
        });
        // 탄 사람이 주는 피해는 모두 막음 (평타 · 활 · 완드 · 스킬 · 룬 · 남아 있던 장판 · 화살 — mount.js 피해 훅)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(source.getEntity() instanceof ServerPlayer a) || a == entity || !Riding.isMounted(a)) return true;
            if (source.isDirect() || source.is(DamageTypeTags.IS_PROJECTILE)) Tasks.later(1, () -> {
                if (!a.isRemoved()) Riding.blocked(a, "attack");
            });
            return false;
        });
        RpgNet.onStatAction("mount", Mounts::toggle);
        RpgNet.onStatAction("mount_select", sp -> openSelect(sp, true));
        RpgNet.beforeStatsOpen(Mounts::sync);
        MountCommands.init();
    }

    // ------------------------------------------------------------ 내 탈것 (플레이어 데이터)

    private record Data(Set<String> owned, String current) {}

    private static Data data(ServerPlayer sp) {
        Set<String> owned = new LinkedHashSet<>();
        String cur = null;
        String raw = Props.get(sp, DATA);
        if (raw != null && !raw.isEmpty()) {
            try {
                Map<String, Object> m = Json.parseObject(raw);
                if (m.get("owned") instanceof List<?> l) for (Object o : l) owned.add(String.valueOf(o));
                if (m.get("current") != null) cur = String.valueOf(m.get("current"));
            } catch (RuntimeException e) {
                // 잘못된 데이터면 처음부터
            }
        }
        return new Data(owned, cur);
    }

    private static void save(ServerPlayer sp, Data d) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("owned", new ArrayList<>(d.owned()));
        if (d.current() != null) m.put("current", d.current());
        Props.set(sp, DATA, Json.stringify(m));
    }

    /** 가진 탈것 키들 (탈것 목록 순서): 처음부터 가진 것 + 탈것 아이템으로 얻은 것 (목록에 없는 키는 뺌) */
    public static List<String> owned(ServerPlayer sp) {
        Set<String> got = data(sp).owned();
        List<String> out = new ArrayList<>();
        for (MountDefs.Def d : MountDefs.all().values()) if (d.starter() || got.contains(d.key())) out.add(d.key());
        return out;
    }

    public static boolean owns(ServerPlayer sp, String key) {
        MountDefs.Def d = MountDefs.get(key);
        return d != null && (d.starter() || data(sp).owned().contains(key));
    }

    /** 지금 고른 탈것 키 (고른 것이 없거나 더는 없으면 가진 것 중 첫째, 하나도 없으면 null) */
    public static String current(ServerPlayer sp) {
        String cur = data(sp).current();
        if (cur != null && owns(sp, cur)) return cur;
        List<String> o = owned(sp);
        return o.isEmpty() ? null : o.get(0);
    }

    /** 탈것을 내 목록에 (탈것 아이템 · /mount unlock). 새로 들어갔으면 true */
    public static boolean unlock(ServerPlayer sp, String key) {
        MountDefs.Def def = MountDefs.get(key);
        if (def == null || owns(sp, key)) return false;
        Data d = data(sp);
        Set<String> owned = new LinkedHashSet<>(d.owned());
        owned.add(key);
        save(sp, new Data(owned, d.current()));
        sync(sp);
        return true;
    }

    /** 탈것 아이템으로 얻은 탈것을 뺌 (/mount lock — 처음부터 가진 탈것은 못 뺌). 뺐으면 true */
    public static boolean lock(ServerPlayer sp, String key) {
        Data d = data(sp);
        if (!d.owned().contains(key)) return false;
        Set<String> owned = new LinkedHashSet<>(d.owned());
        owned.remove(key);
        save(sp, new Data(owned, key.equals(d.current()) ? null : d.current()));
        sync(sp);
        return true;
    }

    /** 탈것 고르기 (가진 것만). 바뀌었으면 true */
    public static boolean select(ServerPlayer sp, String key) {
        if (!owns(sp, key) || key.equals(current(sp))) return false;
        Data d = data(sp);
        save(sp, new Data(d.owned(), key));
        sync(sp);
        return true;
    }

    /** 탈것 이름 (없으면 키) */
    public static String nameOf(String key) {
        MountDefs.Def d = MountDefs.get(key);
        return d != null ? d.name() : String.valueOf(key);
    }

    /** 클라이언트에 탈것 상태 (스탯 창 탈것 칸) */
    public static void sync(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return;
        String cur = current(sp);
        RpgNet.mount(sp, new RpgNet.MountPayload(cur == null ? "" : cur, owned(sp), (int) cooldownLeft(sp)));
    }

    // ------------------------------------------------------------ 탈것 아이템

    /** 탈것 아이템 쓰기: 내 목록에 넣고 1개 씀 (크리에이티브는 그대로). 이미 있으면 그대로 (false) */
    public static boolean useItem(ServerPlayer sp, ItemStack stack, String key) {
        MountDefs.Def def = MountDefs.get(key);
        if (def == null) return false;
        if (owns(sp, key)) {
            Notify.warn(sp, "§7이미 가진 탈것입니다: §b" + def.name() + " §8(스탯 창 탈것 칸 Shift + 누르기 = 탈것 고르기)");
            return false;
        }
        unlock(sp, key);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        unlockFx(sp, def);
        Notify.title(sp, "§b§l" + def.name(), "§f새 탈것을 얻었습니다!");
        Notify.say(sp, "§b" + def.name() + " §f탈것을 얻었습니다! §7(가진 탈것 " + owned(sp).size() + "종 · 스탯 창 탈것 칸을 Shift + 누르면 바꿀 수 있습니다)");
        return true;
    }

    // ------------------------------------------------------------ 부르기 · 돌려보내기

    /** 다시 부를 수 있을 때까지 남은 틱 (0 = 지금) */
    public static long cooldownLeft(ServerPlayer sp) {
        Long gone = GONE_AT.get(sp.getUUID());
        return gone == null ? 0 : Math.max(0, COOLDOWN - (Tasks.now() - gone));
    }

    /** 이 플레이어가 부른 탈것 (없으면 null) */
    public static RpgMount mountOf(ServerPlayer sp) {
        Active a = ACTIVE.get(sp.getUUID());
        return a != null && !a.e.isRemoved() ? a.e : null;
    }

    /** 부른 탈것이 있거나 우리 탈것을 타고 있는지 (스탯 창 탈것 칸이 돌려보내기인지) */
    public static boolean hasMount(ServerPlayer sp) {
        return Riding.isMounted(sp) || mountOf(sp) != null;
    }

    /** 스탯 창 탈것 칸 · /mount: 탄 탈것이 있으면 돌려보내기, 아니면 부르기 */
    public static void toggle(ServerPlayer sp) {
        if (hasMount(sp)) dismiss(sp);
        else summon(sp);
    }

    /**
     * 고른 탈것을 불러 바로 태움. 불렀으면 (이미 타고 있어도) true, 못 부르면 까닭을 화면 하단에
     */
    public static boolean summon(ServerPlayer sp) {
        if (sp == null || sp.isRemoved() || !sp.isAlive()) return false;
        if (sp.isSpectator()) return false;
        if (Riding.isMounted(sp)) return true;
        if (sp.isPassenger()) {
            Notify.warn(sp, "§c다른 것을 타고 있어 탈것을 부를 수 없습니다");
            return false;
        }
        String key = current(sp);
        MountDefs.Def def = key == null ? null : MountDefs.get(key);
        if (def == null) {
            Notify.warn(sp, "§c가진 탈것이 없습니다 §7(탈것 아이템을 쓰면 얻음)");
            return false;
        }
        EntityType<? extends BedrockMob> type = RpgEntities.type(def.entity());
        if (type == null) {
            Notify.warn(sp, "§c탈것을 부를 수 없습니다 §7(" + def.entity() + " 엔티티 없음)");
            return false;
        }
        long left = cooldownLeft(sp);
        if (left > 0) {
            Notify.warn(sp, String.format(java.util.Locale.ROOT, "§7탈것은 §f%.1f초§7 뒤에 다시 부를 수 있습니다", left / 20.0));
            return false;
        }
        if (!headroom(sp, def)) {
            Notify.warn(sp, "§c머리 위가 막혀 탈것을 부를 수 없습니다 §7(탄 사람의 머리가 들어갈 자리가 없음)");
            return false;
        }
        return spawnAndRide(sp, def, type, true);
    }

    /** 탈것을 세우고 태움 (fx = 소환 연출) */
    private static boolean spawnAndRide(ServerPlayer sp, MountDefs.Def def, EntityType<? extends BedrockMob> type, boolean fx) {
        Active prev = ACTIVE.get(sp.getUUID());
        if (prev != null) despawn(sp.getUUID(), prev.e, false, false); // 안 탄 채 남은 것
        ServerLevel level = sp.level();
        Vec3 at = sp.position();
        BedrockMob made = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(made instanceof RpgMount e)) {
            Notify.warn(sp, "§c탈것을 부를 수 없습니다 §7(" + def.entity() + " 은 탈것이 아님)");
            return false;
        }
        float yaw = sp.getYRot();
        e.snapTo(at.x, at.y, at.z, yaw, 0F);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        Props.set(e, OWNER, sp.getUUID().toString());
        Active a = new Active(e, def.key(), Tasks.now(), false);
        ACTIVE.put(sp.getUUID(), a);
        if (!level.addFreshEntity(e)) {
            ACTIVE.remove(sp.getUUID());
            Notify.warn(sp, "§c탈것을 부를 수 없습니다");
            return false;
        }
        RuneKit.lifeEnded(sp.getUUID()); // 하던 룬 (대기 중인 이어 쓰기 · 남은 타격 · 순간이동) 은 멈춤 — 탄 채로 나가지 않게
        if (fx) summonFx(level, at, def);
        // 못 태우면 tick 이 GRACE 동안 다시 해 보고, 그래도 안 되면 치움
        if (sp.startRiding(e, true, true) && e.hasPassenger(sp)) a.seated = true;
        sync(sp);
        return true;
    }

    /** 탄 탈것을 돌려보냄 (내려 놓고 사라짐). 돌려보냈으면 true */
    public static boolean dismiss(ServerPlayer sp) {
        Active a = ACTIVE.get(sp.getUUID());
        RpgMount e = a != null && !a.e.isRemoved() ? a.e : (sp.getVehicle() instanceof RpgMount m ? m : null);
        if (e == null) return false;
        despawn(sp.getUUID(), e, true, true);
        return true;
    }

    /**
     * 탈것을 치움 (탄 사람은 내려 놓음). pid = 주인 (모르면 null), fx = 사라지는 연출, cooldown = 다시 부르기 대기를 셈
     */
    static void despawn(UUID pid, RpgMount e, boolean fx, boolean cooldown) {
        if (pid != null) {
            ACTIVE.remove(pid);
            if (cooldown) GONE_AT.put(pid, Tasks.now());
        }
        Vec3 at = e.position();
        ServerLevel level = e.level() instanceof ServerLevel sl ? sl : null;
        MountDefs.Def def = MountDefs.byEntity(e.def.id());
        e.ejectPassengers();
        e.discard();
        if (fx && level != null && def != null) goneFx(level, at, def);
        if (pid != null && level != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(pid);
            if (p != null) sync(p);
        }
    }

    /** 고른 탈것으로 갈아탐 (탄 채로 탈것 고르기 — 다시 부르기 대기 없이) */
    static void swap(ServerPlayer sp) {
        Active a = ACTIVE.get(sp.getUUID());
        if (a == null || a.e.isRemoved()) return;
        String key = current(sp);
        MountDefs.Def def = key == null ? null : MountDefs.get(key);
        if (def == null || def.key().equals(a.key)) return;
        EntityType<? extends BedrockMob> type = RpgEntities.type(def.entity());
        if (type == null) return;
        despawn(sp.getUUID(), a.e, true, false);
        spawnAndRide(sp, def, type, true);
    }

    /**
     * 발이 있는 칸 바로 위부터 탄 사람의 머리 끝 (발 + 자리 높이 − 붙는 높이 + 키) 이 있는 칸까지 비었는지 (불러오지 않은 곳은 막지 않음).
     * 충돌 없는 블록 (풀 · 꽃 · 덩굴 · 횃불 · 표지판 · 작물 …) · 액체는 괜찮음, 불은 막힘 (머리가 탐)
     */
    static boolean headroom(ServerPlayer sp, MountDefs.Def def) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        EntityType<?> type = RpgEntities.type(def.entity());
        double seat = type == null ? 1.6 : seatHeight(type);
        int base = (int) Math.floor(c.y + 1e-3);
        int top = (int) Math.floor(c.y + seat - RIDER_ATTACH + RIDER_H - 1e-3);
        for (int y = base + 1; y <= top; y++) {
            BlockPos p = BlockPos.containing(c.x, y, c.z);
            if (!level.isLoaded(p)) continue;
            BlockState b = level.getBlockState(p);
            if (b.is(BlockTags.FIRE)) return false;
            if (b.isAir() || !b.getFluidState().isEmpty() || b.getCollisionShape(level, p).isEmpty()) continue;
            return false;
        }
        return true;
    }

    /** 탈것 종류의 탄 사람 자리 높이 (발에서 — 붙는 점) */
    static double seatHeight(EntityType<?> type) {
        Vec3 p = type.getDimensions().attachments().getNullable(net.minecraft.world.entity.EntityAttachment.PASSENGER, 0, 0F);
        return p == null ? type.getDimensions().height() : p.y;
    }

    // ------------------------------------------------------------ 확인 (틱마다)

    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        long now = Tasks.now();
        for (Map.Entry<UUID, Active> en : new ArrayList<>(ACTIVE.entrySet())) {
            UUID pid = en.getKey();
            Active a = en.getValue();
            RpgMount e = a.e;
            if (e.isRemoved()) {
                ACTIVE.remove(pid); // 청크가 내려가 사라짐 · /kill 등
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(pid);
            boolean ownerOn = p != null && e.hasPassenger(p);
            if (ownerOn) {
                a.seated = true;
                if (now % TRAIL_EVERY == 0) trailFx(a);
                continue;
            }
            // 부른 직후 아직 한 번도 태우지 못했으면 다시 (GRACE 안). 한 번 탔다가 내렸으면 (곧바로 웅크려도) 바로 치움
            if (!a.seated && now - a.born < GRACE && p != null && !p.isRemoved() && p.isAlive()) {
                p.startRiding(e, true, true);
                continue;
            }
            despawn(pid, e, true, true);
        }
    }

    /** 부른 기록이 없는 탈것 (탈것 스스로 매 틱): 탄 플레이어가 있으면 그 사람의 탈것으로 기록, 없으면 치움 (/summon · 오류로 남은 것) */
    static void strayCheck(RpgMount m) {
        if (m.isRemoved() || m.tickCount < 5) return;
        for (Active a : ACTIVE.values()) if (a.e == m) return;
        if (m.getFirstPassenger() instanceof ServerPlayer rider) {
            Active old = ACTIVE.get(rider.getUUID());
            if (old != null && old.e != m && !old.e.isRemoved()) despawn(null, old.e, false, false); // 남아 있던 다른 탈것
            MountDefs.Def def = MountDefs.byEntity(m.def.id());
            ACTIVE.put(rider.getUUID(), new Active(m, def != null ? def.key() : "", Tasks.now() - GRACE, true));
            return;
        }
        UUID owner = null;
        try {
            String s = Props.get(m, OWNER);
            if (s != null) owner = UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {
            owner = null;
        }
        despawn(owner != null && !ACTIVE.containsKey(owner) ? owner : null, m, true, false);
    }

    /** 플레이어가 나감: 그 탈것을 치움 */
    static void forget(ServerPlayer sp) {
        Active a = ACTIVE.get(sp.getUUID());
        if (a != null && !a.e.isRemoved()) despawn(sp.getUUID(), a.e, true, false);
        ACTIVE.remove(sp.getUUID());
        GONE_AT.remove(sp.getUUID());
        Riding.forget(sp.getUUID());
    }

    // ------------------------------------------------------------ 고르기 창

    /** 탈것 고르기 (스탯 창 탈것 칸 Shift + 누르기 · /mount select): 가진 탈것을 누르면 그것으로 (탄 채면 바로 갈아탐), 잠긴 것은 얻는 법 안내 */
    public static void openSelect(ServerPlayer sp, boolean backToStats) {
        List<MountDefs.Def> all = new ArrayList<>(MountDefs.all().values());
        List<String> owned = owned(sp);
        String cur = current(sp);
        String text = "§7지금 탈것: " + (cur != null ? "§b" + nameOf(cur) : "§8없음") + "\n§7가진 탈것 §f" + owned.size() + " §7/ " + all.size()
                + " §8· 탈것 아이템을 쓰면 늘어납니다";
        MenuForm f = new MenuForm("§b탈것 고르기", text);
        List<String> keys = new ArrayList<>();
        for (MountDefs.Def d : all) {
            boolean has = owned.contains(d.key());
            String label;
            if (!has) label = "§8" + d.name() + " §7(잠김)";
            else if (d.key().equals(cur)) label = "§a✔ §b§l" + d.name() + " §r§7(지금 탈것)";
            else label = "§b" + d.name();
            f.button(label, d.item().isEmpty() ? Icon.sprite("icon_mount") : Icon.item(d.item()));
            keys.add(d.key());
        }
        f.button("§7닫기", Icon.sprite("icon_cross"));
        f.show(sp, sel -> {
            if (sel >= 0 && sel < keys.size()) pick(sp, keys.get(sel));
            if (backToStats && !sp.isRemoved() && !Forms.isOpen(sp)) RpgNet.openStats(sp);
        });
    }

    /** 고르기 창에서 누름 */
    static void pick(ServerPlayer sp, String key) {
        MountDefs.Def d = MountDefs.get(key);
        if (d == null) return;
        if (!owns(sp, key)) {
            Notify.warn(sp, "§7아직 없는 탈것입니다 §8— §b" + d.name() + " §f탈것 §7아이템을 쓰면 얻습니다");
            return;
        }
        if (!select(sp, key)) {
            Notify.notify(sp, "§7이미 고른 탈것입니다: §b" + d.name(), 40);
            return;
        }
        SkillFx.playFor(sp, "random.orb", 1.3, 0.7);
        Notify.notify(sp, "§f탈것을 §b" + d.name() + "§f(으)로 바꿨습니다", 60);
        if (Riding.isMounted(sp)) swap(sp);
    }

    // ------------------------------------------------------------ 연출

    private static Vec3 jitter(Vec3 at, double w, double y0, double h) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return at.add((r.nextDouble() - 0.5) * w, y0 + r.nextDouble() * h, (r.nextDouble() - 0.5) * w);
    }

    private static void sounds(ServerLevel level, List<MountDefs.Sound> list, Vec3 at) {
        for (MountDefs.Sound s : list) BossKit.play(level, s.name(), at, s.pitch(), s.volume());
    }

    /** 소환: 발밑 룬 · 고리 두 겹 · 빛기둥 · 터짐 · 반짝이 · 파티클 + 소리 */
    static void summonFx(ServerLevel level, Vec3 at, MountDefs.Def d) {
        double[] c = d.color();
        SkillFx.rune(level, at, Opt.of("size", 2.6, "life", 0.9, "color", c, "alpha", 0.95, "progress", 1, "rate", 120, "grow", 0.3));
        SkillFx.shock(level, at, c, 3.4, 0.5);
        SkillFx.shock(level, at, WHITE, 2.0, 0.35);
        SkillFx.fx(level, "rpg:arrow_shot", V.up(at, 2.4), Opt.of("dir", V.UP, "speed", 0, "size", 2.4, "thick", 0.55, "life", 0.55, "color", c, "alpha", 0.8));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(at, 2.4), Opt.of("dir", V.UP, "speed", 0, "size", 2.3, "thick", 0.18, "life", 0.45, "color", WHITE, "alpha", 0.95));
        SkillFx.burst(level, V.up(at, 1.2), c, 1.8, 0.35);
        SkillFx.sparks(level, V.up(at, 1.0), c, 14, 5);
        if (!d.particle().isEmpty()) for (int i = 0; i < 6; i++) SkillFx.vanilla(level, d.particle(), jitter(at, 2.4, 0.5, 1.8));
        sounds(level, d.summon(), at);
    }

    /** 사라짐: 몸에서 터짐 · 고리 · 반짝이 (흰 · 탈것 색) · 파티클 · 연기 + 소리 */
    static void goneFx(ServerLevel level, Vec3 at, MountDefs.Def d) {
        double[] c = d.color();
        Vec3 mid = V.up(at, 1.1);
        SkillFx.burst(level, mid, c, 2.0, 0.4);
        SkillFx.shock(level, at, c, 2.8, 0.45);
        SkillFx.sparks(level, mid, WHITE, 10, 4);
        SkillFx.sparks(level, mid, c, 10, 3);
        if (!d.particle().isEmpty()) for (int i = 0; i < 5; i++) SkillFx.vanilla(level, d.particle(), jitter(at, 2.0, 0.4, 1.6));
        SkillFx.vanilla(level, "minecraft:white_smoke_particle", mid);
        sounds(level, d.gone(), at);
    }

    /** 달리는 동안 발밑에 반짝이 두 개 (TRAIL_EVERY 틱마다, TRAIL_MOVE 칸 넘게 움직였을 때) */
    static void trailFx(Active a) {
        Vec3 loc = a.e.position();
        double moved = Math.hypot(loc.x - a.last.x, loc.z - a.last.z);
        a.last = loc;
        if (moved < TRAIL_MOVE || !(a.e.level() instanceof ServerLevel level)) return;
        MountDefs.Def d = MountDefs.get(a.key);
        double[] c = d != null ? d.color() : WHITE;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 2; i++) {
            Vec3 s = loc.add((r.nextDouble() - 0.5) * 1.2, 0.1, (r.nextDouble() - 0.5) * 1.2);
            SkillFx.fx(level, "rpg:arrow_spark", s, Opt.of("dir", V.unit(new Vec3((r.nextDouble() - 0.5) * 0.4, 1, (r.nextDouble() - 0.5) * 0.4)), "speed", 1.2,
                    "size", 0.08, "grow", 0.3, "life", 0.5, "color", i == 1 ? WHITE : c, "alpha", 0.85));
        }
    }

    /** 뛰기 (점프 키를 뗌 — 세기 0 ~ 100): 발밑 고리 + 반짝이 (세게 뛸수록 크게) */
    static void jumpFx(RpgMount m, int power) {
        if (!(m.level() instanceof ServerLevel level)) return;
        MountDefs.Def d = MountDefs.byEntity(m.def.id());
        double[] c = d != null ? d.color() : WHITE;
        double k = Math.max(0.2, Math.min(1, power / 100.0));
        Vec3 at = m.position();
        SkillFx.shock(level, at, c, 1.2 + 1.4 * k, 0.35);
        SkillFx.sparks(level, V.up(at, 0.2), c, 6 + (int) Math.round(6 * k), 2 + 2 * k);
        if (d != null && !d.particle().isEmpty()) for (int i = 0; i < 3; i++) SkillFx.vanilla(level, d.particle(), jitter(at, 1.6, 0.1, 0.4));
    }

    /** 탈것 아이템을 써서 얻음: 발밑 룬 · 빛기둥 · 반짝이 + 종소리 */
    static void unlockFx(ServerPlayer sp, MountDefs.Def d) {
        ServerLevel level = sp.level();
        Vec3 at = sp.position();
        double[] c = d.color();
        SkillFx.rune(level, at, Opt.of("size", 1.8, "life", 1.0, "color", c, "alpha", 0.9, "progress", 1, "rate", 90, "grow", 0.2));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(at, 1.8), Opt.of("dir", V.UP, "speed", 0, "size", 1.8, "thick", 0.4, "life", 0.6, "color", c, "alpha", 0.75));
        SkillFx.sparks(level, V.up(at, 1.0), c, 12, 4);
        SkillFx.sparks(level, V.up(at, 1.4), WHITE, 8, 3);
        SkillFx.play(level, "random.levelup", at, 1.4, 0.7);
        SkillFx.play(level, "note.chime", at, 1.2, 0.9);
    }

    // ------------------------------------------------------------ 시험용

    /** 시험용: 다시 부르기 대기를 지움 */
    public static void resetCooldown(Player p) {
        GONE_AT.remove(p.getUUID());
    }

    /** 시험용: 부른 탈것 수 */
    public static int activeCount() {
        return ACTIVE.size();
    }
}
