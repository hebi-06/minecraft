package mmorpg.fabric.skill;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import mmorpg.fabric.AddonFx;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgNet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 스킬 · 룬 연출 도구 (skills.js 의 fx · beam · bolt · arc · cutMark · rune · burst · ring · shock · sparks · shake, fx.js play):
 * 애드온 파티클 (AddonFx — 애드온과 같은 변수), 베드락 소리 이름 → 자바 소리, 베드락 바닐라 파티클 이름 → 자바 파티클.
 */
public final class SkillFx {
    private SkillFx() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-skill-fx");
    private static final Set<String> WARNED = new HashSet<>();
    public static final double[] WHITE = {1, 1, 1};

    // ------------------------------------------------------------ 파티클 (skills.js fx — 변수 dir · speed · spin · size · life · thick · delay · grow · progress · color · alpha)

    /** 늘 넣는 변수 (skills.js vars — 없으면 0) */
    private static final String[] STD = {"speed", "spin", "size", "life", "thick", "delay", "grow", "progress"};
    private static final Set<String> SKIP = Set.of("dir", "color", "alpha", "speed", "spin", "size", "life", "thick", "delay", "grow", "progress");

    /**
     * 파티클 변수 (skills.js vars): dir (기본 위) · speed · spin · size · life · thick · delay · grow · progress (기본 0) · color (기본 흰) · alpha (기본 1),
     * 그 밖의 숫자 · 방향 (move · vel · seg · fade · wax · close · frame · phase …) 도 그 이름의 변수로 (어느 파티클 식에도 없는 이름은 버림)
     */
    public static AddonFx.Vars vars(Opt o) {
        AddonFx.Vars v = AddonFx.vars().vec("dir", o.vec("dir", V.UP));
        for (String k : STD) v.set(k, o.num(k, 0));
        v.color("color", o.color("color", WHITE), o.num("alpha", 1));
        for (String k : o.keys()) {
            if (SKIP.contains(k)) continue;
            Object val = o.get(k);
            if (val instanceof Number n) v.set(k, n.doubleValue());
            else if (val instanceof Vec3 vec) v.vec(k, vec);
        }
        return v;
    }

    /** 애드온 파티클 하나 (모두에게) */
    public static void fx(ServerLevel level, String id, Vec3 at, Opt o) {
        AddonFx.spawn(level, id, at, vars(o));
    }

    /** 그 플레이어에게만 보이는 파티클 (player.spawnParticle) */
    public static void fxFor(ServerPlayer sp, String id, Vec3 at, Opt o) {
        AddonFx.spawnFor(sp, id, at, vars(o));
    }

    static double[] whiten(double[] c, double t) {
        return new double[] {c[0] + (1 - c[0]) * t, c[1] + (1 - c[1]) * t, c[2] + (1 - c[2]) * t};
    }

    /** a → b 빛줄기 (선 조각을 이어 붙임). delay 초 뒤에 켜지고 life 초 동안 가늘어지며 사라짐. o: gap · delay · sweep · half · thick · life · alpha */
    public static void beam(ServerLevel level, Vec3 a, Vec3 b, double[] color, Opt o) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        Vec3 u = V.scale(d, 1 / len);
        double gap = o.num("gap", 0.5);
        for (double s = gap / 2; s <= len; s += gap) {
            double delay = o.num("delay", 0) + (o.has("sweep") ? (s / len) * o.num("sweep", 0) : 0);
            fx(level, "rpg:arrow_trail", V.add(a, u, s), Opt.of("dir", u, "size", o.num("half", 0.32), "thick", o.num("thick", 0.08), "life",
                    delay + o.num("life", 0.4), "delay", delay, "color", color, "alpha", o.num("alpha", 0.9)));
        }
    }

    /** a → b 빛줄기에서 눈 near 칸 안의 부분은 빼고 그림 (1인칭 화면을 덮지 않게) — 궁니르 · 세계수의 도끼 · 아폴론 */
    public static void beamAway(ServerLevel level, Vec3 eye, Vec3 a, Vec3 b, double[] color, Opt o, double near) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        Vec3 u = V.unit(d);
        double t0 = 0;
        double t1 = len;
        // 선에서 눈까지 near 칸 안인 구간을 잘라냄 (앞쪽이나 뒤쪽 한쪽만 남김)
        Vec3 w = V.sub(eye, a);
        double tc = V.dot(w, u);
        double off2 = V.dot(w, w) - tc * tc;
        if (off2 < near * near) {
            double hw = Math.sqrt(near * near - off2);
            if (tc - hw <= 0) t0 = Math.max(t0, tc + hw);
            else t1 = Math.min(t1, tc - hw);
        }
        if (t1 - t0 > 0.3) beam(level, V.add(a, u, t0), V.add(a, u, t1), color, o);
    }

    public static void beamAway(ServerLevel level, Vec3 eye, Vec3 a, Vec3 b, double[] color, Opt o) {
        beamAway(level, eye, a, b, color, o, 1.6);
    }

    /** a → b 번개 (지그재그 선 조각). o: jag · thick · delay · life */
    public static void bolt(ServerLevel level, Vec3 a, Vec3 b, double[] color, Opt o) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.1) return;
        Vec3 u = V.unit(d);
        Vec3 side = V.unit(Math.abs(u.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(-u.z, 0, u.x));
        int n = Math.max(3, (int) Math.ceil(len / 1.1));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double jag = o.num("jag", 0.45);
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = a;
        for (int i = 1; i < n; i++) {
            double j = (r.nextDouble() * 2 - 1) * jag;
            double k = (r.nextDouble() * 2 - 1) * jag * 0.6;
            pts[i] = V.add(V.add(V.add(a, u, (len * i) / n), side, j), V.UP, k);
        }
        pts[n] = b;
        double delay = o.num("delay", 0);
        for (int i = 0; i < n; i++) {
            Vec3 s = V.sub(pts[i + 1], pts[i]);
            double sl = V.len(s);
            Vec3 mid = V.add(pts[i], s, 0.5);
            fx(level, "rpg:arrow_trail", mid, Opt.of("dir", V.unit(s), "size", sl / 2 + 0.08, "thick", o.num("thick", 0.1), "life", delay + o.num("life", 0.3),
                    "delay", delay, "color", color, "alpha", 1.0));
        }
    }

    /**
     * 초승달 참격 한 개 (빛 + 흰 심지 + 초월 기운). o: shape (arc · thin · heavy) · flip · speed · spin · size · life · alpha · aura (색) · color · core (false 면 심지 없이)
     */
    public static void arc(ServerLevel level, Vec3 at, Vec3 dir, Opt o) {
        String id = "rpg:slash_" + o.str("shape", "arc") + "_" + (o.truthy("flip") ? "l" : "r");
        Opt base = Opt.of("dir", dir, "speed", o.num("speed", 0), "spin", o.num("spin", 0), "size", o.num("size", 0), "life", o.num("life", 0.3));
        double a = o.num("alpha", 1);
        double size = o.num("size", 0);
        double[] color = o.color("color", WHITE);
        if (o.has("aura")) fx(level, id + "_aura", V.add(at, dir, 0.04), base.with("size", size * 1.03, "color", o.color("aura", WHITE), "alpha", 0.8 * a));
        fx(level, id + "_glow", at, base.with("color", color, "alpha", 0.95 * a));
        if (!(o.get("core") instanceof Boolean b && !b)) fx(level, id + "_core", V.add(at, dir, -0.04), base.with("size", size * 0.99, "color", whiten(color, 0.8), "alpha", a));
    }

    /** 빛의 베인 자국 (rpg:light_cut): 몸에 그어진 뒤 hold 초 동안 떠 있다가 굵어지며 사라짐 */
    public static void cutMark(ServerLevel level, Vec3 at, Vec3 view, double angle, double hold, double[] glow, double[] core, double scale) {
        Vec3 dir = V.screenDir(view, angle);
        fx(level, "rpg:light_cut", at, Opt.of("dir", dir, "size", 0.95 * scale, "thick", 0.36 * scale, "life", hold + 0.16, "delay", hold, "color", glow, "alpha", 0.85));
        fx(level, "rpg:light_cut", V.add(at, view, -0.05), Opt.of("dir", dir, "size", 0.9 * scale, "thick", 0.13 * scale, "life", hold + 0.16, "delay", hold,
                "color", core, "alpha", 1.0));
    }

    /** 바닥 룬 (progress 0~1 = 그려진 단계). o: size · life · spin · rate · grow · progress · color · alpha */
    public static void rune(ServerLevel level, Vec3 at, Opt o) {
        fx(level, "rpg:skill_rune", V.up(at, 0.06), Opt.of("size", o.num("size", 0), "life", o.num("life", 0), "spin",
                o.has("spin") ? o.num("spin", 0) : ThreadLocalRandom.current().nextDouble() * 360, "speed", o.num("rate", 40), "grow", o.num("grow", 0), "progress",
                o.num("progress", 1), "color", o.color("color", WHITE), "alpha", o.num("alpha", 0.9)));
    }

    public static void burst(ServerLevel level, Vec3 at, double[] color, double size, double life) {
        fx(level, "rpg:bash_burst", at, Opt.of("dir", V.SOUTH, "spin", ThreadLocalRandom.current().nextDouble() * 360, "size", size, "life", life, "color", color,
                "alpha", 0.95));
    }

    public static void ring(ServerLevel level, Vec3 at, Vec3 dir, double[] color, double size, double life, double speed) {
        fx(level, "rpg:bash_ring", at, Opt.of("dir", dir, "speed", speed, "size", size, "life", life, "color", color, "alpha", 0.9));
    }

    public static void shock(ServerLevel level, Vec3 feet, double[] color, double size, double life) {
        fx(level, "rpg:shock_ring", V.up(feet, 0.08), Opt.of("dir", V.ZERO, "size", size, "life", life, "color", color, "alpha", 0.9));
    }

    public static void sparks(ServerLevel level, Vec3 at, double[] color, int n, double speed) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = ((double) i / n) * Math.PI * 2;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.35 + r.nextDouble() * 0.5, Math.sin(a)));
            fx(level, "rpg:arrow_spark", at, Opt.of("dir", d, "speed", speed, "size", 0.1, "life", 0.3, "color", color, "alpha", 0.9));
        }
    }

    /** 화면 흔들림 (camerashake add @s 세기 초 rotational) */
    public static void shake(ServerPlayer sp, double intensity, double seconds) {
        RpgNet.shake(sp, intensity, seconds);
    }

    /** count 번을 gap 틱 간격으로 (0번째는 바로 — skills.js every) */
    public static void every(int count, int gap, java.util.function.IntConsumer fn) {
        for (int i = 0; i < count; i++) {
            if (i == 0) fn.accept(0);
            else {
                int k = i;
                mmorpg.fabric.Tasks.later(i * gap, () -> fn.accept(k));
            }
        }
    }

    // ------------------------------------------------------------ 소리 (베드락 이름 → 자바)

    private static final Map<String, String> SOUND = new HashMap<>();

    static {
        String[][] map = {
            // 4단계 (상점 · 대장장이 · 룬술사 · NPC · 퀘스트)
            {"mob.villager.yes", "entity.villager.yes"}, {"mob.villager.no", "entity.villager.no"}, {"mob.villager.idle", "entity.villager.ambient"},
            {"random.anvil_break", "block.anvil.break"}, {"block.grindstone.use", "block.grindstone.use"}, {"vault.eject_item", "block.vault.eject_item"},
            {"random.chestopen", "block.chest.open"}, {"random.chestclosed", "block.chest.close"}, {"item.book.page_turn", "item.book.page_turn"},
            {"note.harp", "block.note_block.harp"}, {"note.chime", "block.note_block.chime"}, {"note.bell", "block.note_block.bell"},
            {"random.toast", "ui.toast.in"}, {"ui.toast.challenge_complete", "ui.toast.challenge_complete"},
            {"item.trident.thunder", "item.trident.thunder"}, {"random.explode", "entity.generic.explode"},
            {"mace.heavy_smash_ground", "item.mace.smash_ground_heavy"}, {"mace.smash_ground", "item.mace.smash_ground"}, {"mace.smash_air", "item.mace.smash_air"},
            {"item.spear.attack_miss", "item.spear.attack"}, {"beacon.power", "block.beacon.power_select"}, {"beacon.activate", "block.beacon.activate"},
            {"beacon.deactivate", "block.beacon.deactivate"}, {"beacon.ambient", "block.beacon.ambient"}, {"item.trident.throw", "item.trident.throw"},
            {"item.trident.hit", "item.trident.hit"}, {"item.trident.return", "item.trident.return"}, {"item.trident.riptide_1", "item.trident.riptide_1"},
            {"item.trident.riptide_3", "item.trident.riptide_3"}, {"chime.amethyst_block", "block.amethyst_block.chime"},
            {"resonate.amethyst_block", "block.amethyst_block.resonate"}, {"break.amethyst_cluster", "block.amethyst_cluster.break"},
            {"mob.endermen.portal", "entity.enderman.teleport"}, {"random.bow", "entity.arrow.shoot"}, {"random.bowhit", "entity.arrow.hit"},
            {"mob.evocation_illager.prepare_attack", "entity.evoker.prepare_attack"}, {"mob.evocation_illager.cast_spell", "entity.evoker.cast_spell"},
            {"mob.evocation_illager.prepare_summon", "entity.evoker.prepare_summon"}, {"firework.large_blast", "entity.firework_rocket.large_blast"},
            {"firework.blast", "entity.firework_rocket.blast"}, {"firework.twinkle", "entity.firework_rocket.twinkle"}, {"random.anvil_land", "block.anvil.land"},
            {"random.anvil_use", "block.anvil.use"}, {"mob.blaze.shoot", "entity.blaze.shoot"}, {"ambient.weather.lightning.impact", "entity.lightning_bolt.impact"},
            {"ambient.weather.thunder", "entity.lightning_bolt.thunder"}, {"game.player.attack.critical", "entity.player.attack.crit"},
            {"game.player.attack.strong", "entity.player.attack.strong"}, {"mob.ravager.roar", "entity.ravager.roar"}, {"mob.breeze.whirl", "entity.breeze.whirl"},
            {"mob.breeze.charge", "entity.breeze.charge"}, {"mob.breeze.jump", "entity.breeze.jump"}, {"fire.ignite", "item.flintandsteel.use"},
            {"fire.fire", "block.fire.ambient"}, {"random.fizz", "block.fire.extinguish"}, {"crossbow.shoot", "item.crossbow.shoot"},
            {"crossbow.loading.start", "item.crossbow.loading_start"}, {"crossbow.loading.middle", "item.crossbow.loading_middle"}, {"block.bell.hit", "block.bell.use"},
            {"random.totem", "item.totem.use"}, {"random.glass", "block.glass.break"}, {"particle.soul_escape", "particle.soul_escape"},
            {"mob.vex.charge", "entity.vex.charge"}, {"mob.enderdragon.flap", "entity.ender_dragon.flap"}, {"mob.enderdragon.growl", "entity.ender_dragon.growl"},
            {"item.shield.block", "item.shield.block"}, {"mob.warden.sonic_charge", "entity.warden.sonic_charge"}, {"mob.warden.sonic_boom", "entity.warden.sonic_boom"},
            {"mob.warden.heartbeat", "entity.warden.heartbeat"}, {"mob.ghast.fireball", "entity.ghast.shoot"}, {"armor.equip_iron", "item.armor.equip_iron"},
            {"armor.equip_gold", "item.armor.equip_gold"}, {"random.orb", "entity.experience_orb.pickup"}, {"mob.zombie.unfect", "entity.zombie_villager.cure"},
            {"mob.player.hurt_freeze", "entity.player.hurt_freeze"}, {"dig.stone", "block.stone.break"}, {"wind_charge.burst", "entity.wind_charge.wind_burst"},
            {"use.chain", "block.chain.place"}, {"place.chain", "block.chain.place"}, {"respawn_anchor.charge", "block.respawn_anchor.charge"},
            {"respawn_anchor.deplete", "block.respawn_anchor.deplete"}, {"random.levelup", "entity.player.levelup"}, {"mob.wither.spawn", "entity.wither.spawn"},
            {"mob.wither.shoot", "entity.wither.shoot"}, {"mob.shulker.bullet.hit", "entity.shulker_bullet.hit"}, {"mob.phantom.flap", "entity.phantom.flap"},
            {"mob.allay.idle", "entity.allay.ambient_without_item"}, {"item.firecharge.use", "item.firecharge.use"}, {"bucket.empty_water", "item.bucket.empty"},
            {"step.powder_snow", "block.powder_snow.step"}, {"random.splash", "entity.generic.splash"}, {"random.click", "ui.button.click"},
            {"portal.trigger", "block.portal.trigger"}, {"note.pling", "block.note_block.pling"}, {"note.bass", "block.note_block.bass"},
            {"random.pop", "entity.item.pickup"}, {"mob.zombie.woodbreak", "entity.zombie.break_wooden_door"}, {"random.door_open", "block.wooden_door.open"},
            {"block.enchanting_table.use", "block.enchantment_table.use"},
            {"conduit.activate", "block.conduit.activate"}, {"crossbow.loading.end", "item.crossbow.loading_end"}, {"mob.allay.item_given", "entity.allay.item_given"},
            {"mob.breeze.shoot", "entity.breeze.shoot"}, {"mob.irongolem.repair", "entity.iron_golem.repair"}, {"mob.ravager.stun", "entity.ravager.stunned"},
            {"mob.shulker.shoot", "entity.shulker.shoot"}, {"item.trident.riptide_2", "item.trident.riptide_2"},
            // 0.12.4 세계수의 도끼 (자바 이름 그대로 — 대응표에 두어 시험이 소리가 있는지 확인)
            {"block.roots.break", "block.roots.break"}, {"block.azalea_leaves.break", "block.azalea_leaves.break"},
            {"item.bone_meal.use", "item.bone_meal.use"}, {"entity.snowball.throw", "entity.snowball.throw"},
            // 0.16.0 한정 초월 룬 (천공의 눈 광선)
            {"beacon.ambient", "block.beacon.ambient"},
            // 0.19.0 세계수의 가호 (자라는 소리 · 6돌 꽃잎)
            {"block.chorus_flower.grow", "block.chorus_flower.grow"}, {"block.cherry_leaves.break", "block.cherry_leaves.break"},
        };
        for (String[] m : map) SOUND.put(m[0], m[1]);
    }

    /** 이름이 크게 다른 소리는 자바 상수로 바로 (이름 짐작 없이) */
    private static final Map<String, java.util.function.Supplier<SoundEvent>> CONST = new HashMap<>();

    static {
        CONST.put("item.enchant.lunge1", () -> SoundEvents.LUNGE_1.value());
        CONST.put("item.enchant.lunge2", () -> SoundEvents.LUNGE_2.value());
        CONST.put("item.enchant.lunge3", () -> SoundEvents.LUNGE_3.value());
        CONST.put("item.spear.attack_miss", () -> SoundEvents.SPEAR_ATTACK.value());
        CONST.put("hit.amethyst_cluster", () -> SoundEvents.AMETHYST_CLUSTER_HIT);
        CONST.put("mob.irongolem.hit", () -> SoundEvents.IRON_GOLEM_HURT);
        CONST.put("mob.irongolem.crack", () -> SoundEvents.IRON_GOLEM_DAMAGE);
        CONST.put("mob.elderguardian.curse", () -> SoundEvents.ELDER_GUARDIAN_CURSE);
        CONST.put("mob.warden.emerge", () -> SoundEvents.WARDEN_EMERGE);
        CONST.put("mob.phantom.bite", () -> SoundEvents.PHANTOM_BITE);
        // 6단계 (탈것 · 엔더 상자)
        CONST.put("breeze_wind_charge.burst", () -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value());
        CONST.put("mob.wolf.shake", () -> SoundEvents.WOLF_SHAKE);
        CONST.put("random.enderchestopen", () -> SoundEvents.ENDER_CHEST_OPEN);
        CONST.put("random.enderchestclosed", () -> SoundEvents.ENDER_CHEST_CLOSE);
        // 0.25.0 두 스탯 룬 계열 (해류 · 골렘 · 무투 · 사냥꾼 · 화산 · 투사 — 자바 이름 그대로, 상수로 확인)
        CONST.put("entity.generic.splash", () -> SoundEvents.GENERIC_SPLASH);
        CONST.put("entity.dolphin.splash", () -> SoundEvents.DOLPHIN_SPLASH);
        CONST.put("entity.hostile.splash", () -> SoundEvents.HOSTILE_SPLASH);
        CONST.put("block.bubble_column.bubble_pop", () -> SoundEvents.BUBBLE_POP);
        CONST.put("block.bubble_column.whirlpool_inside", () -> SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE);
        CONST.put("block.bubble_column.upwards_inside", () -> SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE);
        CONST.put("entity.guardian.attack", () -> SoundEvents.GUARDIAN_ATTACK);
        CONST.put("block.conduit.attack.target", () -> SoundEvents.CONDUIT_ATTACK_TARGET);
        CONST.put("ambient.underwater.enter", () -> SoundEvents.AMBIENT_UNDERWATER_ENTER);
        CONST.put("weather.rain", () -> SoundEvents.WEATHER_RAIN);
        CONST.put("block.deepslate.break", () -> SoundEvents.DEEPSLATE_BREAK);
        CONST.put("block.deepslate_bricks.break", () -> SoundEvents.DEEPSLATE_BRICKS_BREAK);
        CONST.put("block.stone.break", () -> SoundEvents.STONE_BREAK);
        CONST.put("entity.iron_golem.attack", () -> SoundEvents.IRON_GOLEM_ATTACK);
        CONST.put("entity.iron_golem.step", () -> SoundEvents.IRON_GOLEM_STEP);
        CONST.put("entity.warden.dig", () -> SoundEvents.WARDEN_DIG);
        CONST.put("entity.warden.roar", () -> SoundEvents.WARDEN_ROAR);
        CONST.put("entity.goat.ram_impact", () -> SoundEvents.GOAT_RAM_IMPACT);
        CONST.put("entity.player.attack.strong", () -> SoundEvents.PLAYER_ATTACK_STRONG);
        CONST.put("entity.player.attack.knockback", () -> SoundEvents.PLAYER_ATTACK_KNOCKBACK);
        CONST.put("entity.player.attack.crit", () -> SoundEvents.PLAYER_ATTACK_CRIT);
        CONST.put("entity.fox.bite", () -> SoundEvents.FOX_BITE);
        CONST.put("entity.polar_bear.warning", () -> SoundEvents.POLAR_BEAR_WARNING);
        CONST.put("entity.parrot.fly", () -> SoundEvents.PARROT_FLY);
        CONST.put("block.iron_trapdoor.close", () -> SoundEvents.IRON_TRAPDOOR_CLOSE);
        CONST.put("entity.fishing_bobber.throw", () -> SoundEvents.FISHING_BOBBER_THROW);
        CONST.put("block.lava.pop", () -> SoundEvents.LAVA_POP);
        CONST.put("block.lava.extinguish", () -> SoundEvents.LAVA_EXTINGUISH);
        CONST.put("block.lava.ambient", () -> SoundEvents.LAVA_AMBIENT);
        CONST.put("item.bucket.empty_lava", () -> SoundEvents.BUCKET_EMPTY_LAVA);
        CONST.put("block.basalt.break", () -> SoundEvents.BASALT_BREAK);
        CONST.put("entity.magma_cube.squish", () -> SoundEvents.MAGMA_CUBE_SQUISH);
        CONST.put("block.chain.place", () -> SoundEvents.CHAIN_PLACE);
        CONST.put("block.chain.hit", () -> SoundEvents.CHAIN_HIT);
        CONST.put("block.chain.break", () -> SoundEvents.CHAIN_BREAK);
        CONST.put("item.armor.equip_chain", () -> SoundEvents.ARMOR_EQUIP_CHAIN.value());
        CONST.put("block.anvil.land", () -> SoundEvents.ANVIL_LAND);
        CONST.put("entity.blaze.shoot", () -> SoundEvents.BLAZE_SHOOT);
        CONST.put("entity.ghast.shoot", () -> SoundEvents.GHAST_SHOOT);
        CONST.put("entity.ravager.roar", () -> SoundEvents.RAVAGER_ROAR);
        CONST.put("entity.breeze.jump", () -> SoundEvents.BREEZE_JUMP);
        CONST.put("item.mace.smash_ground_heavy", () -> SoundEvents.MACE_SMASH_GROUND_HEAVY);
    }

    /** 베드락 소리 이름 → 자바 소리 (없으면 null — 한 번 로그) */
    public static SoundEvent sound(String bedrock) {
        java.util.function.Supplier<SoundEvent> c = CONST.get(bedrock);
        if (c != null) return c.get();
        String java = SOUND.getOrDefault(bedrock, bedrock);
        SoundEvent s = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(java));
        if (s == null && WARNED.add(bedrock)) LOG.warn("[mmorpg] no Java sound for {} ({})", bedrock, java);
        return s;
    }

    /** 대응표의 베드락 이름 모두 (시험용 — 모두 자바 소리로 풀리는지) */
    public static java.util.Set<String> soundNames() {
        java.util.Set<String> all = new java.util.TreeSet<>(SOUND.keySet());
        all.addAll(CONST.keySet());
        return all;
    }

    /** 그 자리에서 소리 (fx.js play(dim, 이름, 자리, pitch, volume)) */
    public static void play(ServerLevel level, String bedrock, Vec3 at, double pitch, double volume) {
        SoundEvent s = sound(bedrock);
        if (s == null) return;
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, (float) volume, (float) pitch);
    }

    /** 그 사람에게만 들리는 소리 (player.playSound) */
    public static void playFor(ServerPlayer sp, String bedrock, double pitch, double volume) {
        SoundEvent s = sound(bedrock);
        if (s != null) Notify.sound(sp, s, (float) volume, (float) pitch);
    }

    // ------------------------------------------------------------ 베드락 바닐라 파티클

    /** 베드락 바닐라 파티클 (minecraft:heart_particle 등) → 자바 파티클 하나 (emitter 는 여러 개) */
    public static void vanilla(ServerLevel level, String bedrock, Vec3 at) {
        switch (bedrock) {
            case "minecraft:heart_particle" -> level.sendParticles(ParticleTypes.HEART, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            case "minecraft:totem_particle" -> level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.3);
            case "minecraft:snowflake_particle" -> level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.02);
            case "minecraft:huge_explosion_emitter" -> level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            case "minecraft:lava_particle" -> level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            case "minecraft:critical_hit_emitter" -> level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.3);
            case "minecraft:basic_flame_particle" -> level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0, 0, 0, 0.01);
            case "minecraft:basic_smoke_particle" -> level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 1, 0, 0, 0, 0.01);
            case "minecraft:villager_happy" -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 5, 0.3, 0.2, 0.3, 0);
            case "minecraft:villager_angry" -> level.sendParticles(ParticleTypes.ANGRY_VILLAGER, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0);
            case "minecraft:mob_portal" -> level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.4);
            case "minecraft:endrod" -> level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
            case "minecraft:blue_flame_particle" -> level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.01);
            case "minecraft:death_explosion_emitter" -> level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.05);
            case "minecraft:explosion_particle" -> level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            case "minecraft:note_particle" -> level.sendParticles(ParticleTypes.NOTE, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            case "minecraft:enchanting_table_particle" -> level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.5);
            case "minecraft:white_smoke_particle" -> level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
            case "minecraft:portal_reverse_particle" -> level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.02);
            default -> {
                if (WARNED.add(bedrock)) LOG.warn("[mmorpg] no Java particle for {}", bedrock);
            }
        }
    }

    public static void particle(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }
}
