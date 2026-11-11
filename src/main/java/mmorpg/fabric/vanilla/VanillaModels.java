package mmorpg.fabric.vanilla;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import mmorpg.core.Json;
import mmorpg.core.bedrock.Animator;
import mmorpg.core.bedrock.Ctx;
import mmorpg.core.bedrock.Geo;
import mmorpg.core.bedrock.RenderCtl;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 모드 없는 클라이언트의 몬스터 · 보스 · 소품 · 소환물 모습 (2026-11-07 로드맵 1번 2단계): 베드락 모델의 뼈마다 아이템 표시 엔티티 (뼈 아이템 모델 —
 * tools/vanilla_models.py) 를 엔티티 (모드 없는 클라이언트에는 보이지 않는 갑옷 거치대) 에 태우고, 동작은 서버가 클라이언트 BedrockRenderer 와 같은 계산
 * (core bedrock.Animator · RenderCtl) 으로 2 ~ 8틱마다 변환을 보냄 (그 사이는 표시 엔티티의 보간). 맞는 칸은 상호작용 엔티티 (때리면 그 엔티티를 때림).
 * 모드 클라이언트에는 보내지 않음 (지금 그리기 그대로).
 *
 * <p>변환 (블록 · 엔티티 발 기준, 자바 LivingEntityRenderer 와 같은 차례를 x 거울 j 공간 (j = (-x, y, z) 베드락) 으로):
 * T(0, -탄 높이) · S(크기) · Ry(180 - 몸 방향) · Rz(쓰러짐) · T(0, 1.501) · 뼈 사슬 [T(자리 / 16) · Rz(z) · Ry(-y) · Rx(-x) · S(크기)] ·
 * S(1 / k) · T(-(c0 - 8) / 16). 뼈 자리: 뿌리 (-px, py - 24, pz), 자식은 부모 pivot 과의 차이 (x 반대), 동작 위치 (-x, y, z) 를 더함.
 */
final class VanillaModels {
    private VanillaModels() {}

    /** 엔티티 ID → 모습 */
    private static final Map<String, Look> LOOKS = new HashMap<>();
    private static final Map<Entity, MobModel> MODELS = new WeakHashMap<>();

    /** 엔티티 종류 하나의 모습 (뼈 아이템 모델 · 동작) */
    static final class Look {
        final String id;
        final Geo geo;
        final Animator animator;
        final RenderCtl ctl;
        final String path;
        /** 뼈 번호마다 아이템 모델 이름 (상자가 없으면 null) · 줄인 배 · 가운데 · 빛 겹 */
        final String[] model;
        final float[] k;
        final float[][] c0;
        final boolean[] glow;
        final int[] parent;
        final int[] order;
        /** 텍스처 이름 (default …) → 모델 경로 앞 ("" = 첫 텍스처, "<텍스처>/" = 다른 텍스처) */
        final Map<String, String> variant;
        final boolean ownDeath;
        final boolean saddled;

        Look(String id, Geo geo, Animator animator, RenderCtl ctl, String path, String[] model, float[] k, float[][] c0, boolean[] glow,
                int[] parent, int[] order, Map<String, String> variant, boolean ownDeath, boolean saddled) {
            this.id = id;
            this.geo = geo;
            this.animator = animator;
            this.ctl = ctl;
            this.path = path;
            this.model = model;
            this.k = k;
            this.c0 = c0;
            this.glow = glow;
            this.parent = parent;
            this.order = order;
            this.variant = variant;
            this.ownDeath = ownDeath;
            this.saddled = saddled;
        }

        ItemStack stack(int bone, String variantPrefix, boolean glowLayer) {
            ItemStack s = new ItemStack(Items.PAPER);
            s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Mmorpg.NS,
                    "vmob/" + path + "/" + variantPrefix + model[bone] + (glowLayer ? "_glow" : "")));
            return s;
        }
    }

    static void init() {
        Map<String, Object> data = read("/assets/mmorpg/bedrock/entities.json");
        Map<String, Object> manifest = read("/mmorpg/vanilla_models.json");
        Map<String, Object> ents = map(data.get("entities"));
        Map<String, Object> models = map(data.get("models"));
        Map<String, Object> anims = map(data.get("animations"));
        Map<String, Object> ctls = map(data.get("render_controllers"));
        Map<String, Object> mModels = map(manifest.get("models"));
        Map<String, Object> mTex = map(manifest.get("textures"));
        for (Map.Entry<String, Object> en : ents.entrySet()) {
            try {
                Look look = look(en.getKey(), map(en.getValue()), models, anims, ctls, mModels, mTex);
                if (look != null) LOOKS.put(en.getKey(), look);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[vanilla] model {}: {}", en.getKey(), e.toString());
            }
        }
        Mmorpg.LOG.info("[vanilla] mob models {}", LOOKS.size());
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BedrockMob mob) attach(mob);
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            MobModel m = MODELS.remove(entity);
            if (m != null) m.destroy();
        });
    }

    private static Look look(String id, Map<String, Object> d, Map<String, Object> models, Map<String, Object> anims, Map<String, Object> ctls,
            Map<String, Object> mModels, Map<String, Object> mTex) {
        String geoId = String.valueOf(map(d.get("geometry")).get("default"));
        Map<String, Object> mm = map(mModels.get(geoId));
        if (mm.isEmpty()) return null;
        Geo geo = Geo.parse(map(models.get(geoId)));
        MobData.Def def = MobData.get(id);
        List<String> props = def == null ? List.of() : def.propNames();
        Animator animator = new Animator(geo, d, anims, props);
        RenderCtl ctl = RenderCtl.defaults();
        if (d.get("render_controllers") instanceof List<?> rcs && !rcs.isEmpty() && ctls.get(String.valueOf(rcs.get(0))) instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked") Map<String, Object> cm = (Map<String, Object>) m;
            ctl = RenderCtl.parse(cm, animator.ctx);
        }
        int n = geo.bones.size();
        String[] model = new String[n];
        float[] k = new float[n];
        float[][] c0 = new float[n][];
        boolean[] glow = new boolean[n];
        Map<String, Object> bones = map(mm.get("bones"));
        for (Geo.Bone b : geo.bones) {
            Map<String, Object> bo = map(bones.get(b.name));
            if (bo.isEmpty()) continue;
            model[b.index] = String.valueOf(bo.get("model"));
            k[b.index] = (float) num(bo.get("k"), 1);
            List<?> c = bo.get("c0") instanceof List<?> l ? l : List.of(8, 8, 8);
            c0[b.index] = new float[]{(float) num(c.get(0), 8), (float) num(c.get(1), 8), (float) num(c.get(2), 8)};
            glow[b.index] = Boolean.TRUE.equals(bo.get("glow"));
        }
        int[] parent = new int[n];
        for (Geo.Bone b : geo.bones) {
            Geo.Bone p = b.parent == null ? null : geo.bone(b.parent);
            parent[b.index] = p == null || p == b ? -1 : p.index;
        }
        int[] order = new int[n];
        boolean[] done = new boolean[n];
        int[] at = {0};
        for (int i = 0; i < n; i++) visit(i, parent, done, order, at, 0);
        List<?> slugs = mm.get("textures") instanceof List<?> l ? l : List.of();
        String base = slugs.isEmpty() ? "" : String.valueOf(slugs.get(0));
        Map<String, String> variant = new LinkedHashMap<>();
        for (Map.Entry<String, Object> t : map(d.get("textures")).entrySet()) {
            String slug = String.valueOf(mTex.getOrDefault(String.valueOf(t.getValue()), base));
            variant.put(t.getKey().toLowerCase(Locale.ROOT), slug.equals(base) ? "" : slug + "/");
        }
        boolean saddled = def != null && def.components().containsKey("minecraft:is_saddled");
        return new Look(id, geo, animator, ctl, String.valueOf(mm.get("path")), model, k, c0, glow, parent, order, variant,
                animator.anim("die") != null, saddled);
    }

    private static void visit(int i, int[] parent, boolean[] done, int[] order, int[] at, int depth) {
        if (done[i] || depth > parent.length) return;
        if (parent[i] >= 0) visit(parent[i], parent, done, order, at, depth + 1);
        if (done[i]) return;
        done[i] = true;
        order[at[0]++] = i;
    }

    static void attach(BedrockMob mob) {
        if (MODELS.containsKey(mob)) return;
        Look look = LOOKS.get(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        if (look == null) return;
        MobModel m = new MobModel(mob, look);
        MODELS.put(mob, m);
        EntityAttachment.ofTicking(m, mob);
        // 2026-11-11: 엔티티를 불러온 때 (ENTITY_LOAD) 는 곁의 사람과 이미 짝을 맺은 뒤 — Polymer 는 태우는 패킷을 짝을 맺을 때 (addPairing) 만 보내므로
        // 이미 보던 사람에게는 뼈가 태워지지 않고 처음 자리 (발 − 키) 에 머묾 (곁에서 솟은 샐러맨더가 땅에 묻히고 따라오지 않던 원인 — 이름표 (진짜 탄 것) 가 있는
        // 몬스터는 이름표가 탈 때의 패킷에 뼈가 같이 실려 괜찮았음). 지금 보는 사람에게 한 번 보냄 (Polymer 가 이 사람이 보는 뼈 번호를 더함)
        if (!m.getWatchingPlayers().isEmpty()) {
            ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(mob);
            for (ServerGamePacketListenerImpl c : new ArrayList<>(m.getWatchingPlayers())) c.send(mount);
        }
    }

    /** 엔티티 하나의 뼈 표시 엔티티 묶음 — 모드 없는 클라이언트만 봄 */
    static final class MobModel extends ElementHolder {
        private final BedrockMob mob;
        private final Look look;
        private final ItemDisplayElement[] bones;
        private final ItemDisplayElement[] glows;
        private final InteractionElement hit;
        private final boolean[] shown;
        private String variant = "";
        private int age;
        private int every = 2;
        private float hitW = -1;
        private float hitH = -1;

        MobModel(BedrockMob mob, Look look) {
            this.mob = mob;
            this.look = look;
            int n = look.geo.bones.size();
            bones = new ItemDisplayElement[n];
            glows = new ItemDisplayElement[n];
            shown = new boolean[n];
            float w = Math.max(1F, mob.getBbWidth() * 2F);
            float h = Math.max(1F, mob.getBbHeight() * 2F);
            for (int i = 0; i < n; i++) {
                if (look.model[i] == null) continue;
                bones[i] = element(look.stack(i, variant, false), w, h);
                shown[i] = true;
                if (look.glow[i]) glows[i] = element(look.stack(i, variant, true), w, h);
            }
            hit = InteractionElement.redirect(mob);
            resizeHit();
            addElement(hit);
            pose(); // 처음 보일 때부터 자세 (뼈가 한 점에 모였다 퍼지지 않게)
        }

        private ItemDisplayElement element(ItemStack stack, float w, float h) {
            ItemDisplayElement e = new ItemDisplayElement(stack);
            e.setInterpolationDuration(every);
            e.setDisplaySize(w, h);
            e.ignorePositionUpdates();
            addPassengerElement(e);
            return e;
        }

        private void resizeHit() {
            float w = mob.getBbWidth();
            float h = mob.getBbHeight();
            if (w != hitW || h != hitH) {
                hitW = w;
                hitH = h;
                hit.setSize(Math.max(0.1F, w), Math.max(0.1F, h));
            }
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl player) {
            if (VanillaClients.isModded(player.getPlayer())) return false;
            return super.startWatching(player);
        }

        @Override
        protected void onTick() {
            if (getWatchingPlayers().isEmpty()) return;
            age++;
            resizeHit();
            int rate = rate();
            if (rate != every) {
                every = rate;
                for (ItemDisplayElement e : bones) if (e != null) e.setInterpolationDuration(rate);
                for (ItemDisplayElement e : glows) if (e != null) e.setInterpolationDuration(rate);
            }
            if (age % every != 0 && age > 1) return;
            pose();
        }

        /** 가장 가까운 모드 없는 클라이언트까지 — 가까우면 2틱, 멀면 4 · 8틱마다 */
        private int rate() {
            double best = Double.MAX_VALUE;
            for (ServerGamePacketListenerImpl c : getWatchingPlayers()) {
                ServerPlayer p = c.getPlayer();
                best = Math.min(best, p.distanceToSqr(mob));
            }
            return best < 32 * 32 ? 2 : best < 64 * 64 ? 4 : 8;
        }

        private void pose() {
            Look L = look;
            Ctx ctx = L.animator.ctx;
            double[] v = new double[ctx.size()];
            float speed = mob.walkAnimation.speed();
            v[Ctx.LIFE_TIME] = mob.tickCount / 20.0;
            v[Ctx.MOVE_SPEED] = speed;
            v[Ctx.DISTANCE_MOVED] = mob.walkAnimation.position();
            v[Ctx.HURT_TIME] = mob.hurtTime;
            v[Ctx.TARGET_X] = mob.getXRot();
            v[Ctx.TARGET_Y] = Mth.wrapDegrees(mob.getYHeadRot() - mob.yBodyRot);
            v[Ctx.ON_GROUND] = mob.onGround() ? 1 : 0;
            v[Ctx.IN_WATER] = mob.isInWater() ? 1 : 0;
            v[Ctx.HEALTH] = mob.getHealth();
            v[Ctx.MAX_HEALTH] = mob.getMaxHealth();
            v[Ctx.GROUND_SPEED] = mob.getDeltaMovement().horizontalDistance() * 20;
            v[Ctx.IS_MOVING] = speed > 0.01 ? 1 : 0;
            v[Ctx.IS_SADDLED] = L.saddled ? 1 : 0;
            for (String p : ctx.props()) {
                int slot = ctx.prop(p);
                if (slot >= 0) v[slot] = mob.prop(p);
            }
            long now = mob.level().getGameTime();
            List<Animator.Playing> playing = new ArrayList<>();
            for (BedrockMob.Playing p : mob.syncedAnims()) playing.add(new Animator.Playing(p.anim(), Math.max(0, (now - p.start()) / 20.0), p.stop()));
            int n = L.geo.bones.size();
            Animator.Pose pose = new Animator.Pose(n);
            L.animator.apply(v, playing, pose);
            boolean[] vis = null;
            if (L.ctl.hasVisibility()) {
                vis = new boolean[n];
                L.ctl.visibility(v, L.geo, vis);
            }
            String key = L.ctl.texture(v);
            String var = key == null ? "" : L.variant.getOrDefault(key.toLowerCase(Locale.ROOT), "");
            boolean retexture = !var.equals(variant);
            variant = var;
            boolean hidden = mob.isInvisible();

            float scale = mob.getScale();
            Matrix4f root = new Matrix4f().translation(0, (float) -VanillaEntities.riderHeight(mob), 0)
                    .scale(scale)
                    .rotateY((float) Math.toRadians(180F - mob.yBodyRot));
            if (mob.deathTime > 0 && !L.ownDeath) {
                float f = Math.min(1F, Mth.sqrt((mob.deathTime - 1F) / 20F * 1.6F));
                root.rotateZ((float) Math.toRadians(f * 90F));
            }
            root.translate(0, 1.501F, 0);
            Matrix4f[] world = new Matrix4f[n];
            for (int idx = 0; idx < n; idx++) {
                int i = L.order[idx];
                Geo.Bone b = L.geo.bones.get(i);
                int p = L.parent[i];
                double ox;
                double oy;
                double oz;
                if (p < 0) {
                    ox = -b.pivot[0];
                    oy = b.pivot[1] - 24;
                    oz = b.pivot[2];
                } else {
                    Geo.Bone pb = L.geo.bones.get(p);
                    ox = -(b.pivot[0] - pb.pivot[0]);
                    oy = b.pivot[1] - pb.pivot[1];
                    oz = b.pivot[2] - pb.pivot[2];
                }
                int k3 = i * 3;
                ox -= pose.pos[k3];
                oy += pose.pos[k3 + 1];
                oz += pose.pos[k3 + 2];
                double rx = b.rotation[0] + pose.rot[k3];
                double ry = b.rotation[1] + pose.rot[k3 + 1];
                double rz = b.rotation[2] + pose.rot[k3 + 2];
                Matrix4f m = new Matrix4f(p < 0 || world[p] == null ? root : world[p]);
                m.translate((float) (ox / 16), (float) (oy / 16), (float) (oz / 16));
                m.rotateZ((float) Math.toRadians(rz)).rotateY((float) -Math.toRadians(ry)).rotateX((float) -Math.toRadians(rx));
                m.scale((float) pose.scale[k3], (float) pose.scale[k3 + 1], (float) pose.scale[k3 + 2]);
                world[i] = m;
            }
            for (int i = 0; i < n; i++) {
                if (bones[i] == null) continue;
                boolean show = !hidden && (vis == null || vis[i]);
                if (retexture || show != shown[i]) {
                    bones[i].setItem(show ? L.stack(i, variant, false) : ItemStack.EMPTY);
                    if (glows[i] != null) glows[i].setItem(show ? L.stack(i, variant, true) : ItemStack.EMPTY);
                    shown[i] = show;
                }
                if (!show) continue;
                float kk = L.k[i] <= 0 ? 1F : L.k[i];
                float[] c = L.c0[i];
                Matrix4f d = new Matrix4f(world[i]).scale(1F / kk).translate(-(c[0] - 8F) / 16F, -(c[1] - 8F) / 16F, -(c[2] - 8F) / 16F);
                bones[i].setTransformation(d);
                bones[i].startInterpolationIfDirty();
                if (glows[i] != null) {
                    glows[i].setTransformation(d);
                    glows[i].startInterpolationIfDirty();
                }
            }
        }
    }

    // ------------------------------------------------------------ 읽기

    private static Map<String, Object> read(String resource) {
        try (InputStream in = VanillaModels.class.getResourceAsStream(resource)) {
            if (in == null) return Map.of();
            return Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vanilla] {}: {}", resource, e.toString());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static double num(Object o, double def) {
        return o instanceof Number x ? x.doubleValue() : def;
    }
}
