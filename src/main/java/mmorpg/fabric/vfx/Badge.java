package mmorpg.fabric.vfx;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 2026-11-25 엔티티 머리 위에 태운 그림 하나 (그림판 rpg:vfx 의 한 칸 — 글자 표시 엔티티를 그 엔티티에 태움): 클라이언트가 엔티티와 같이 옮겨 그려서 늦거나
 * 앞서지 않음 (몬스터 이름표와 같은 방법 — 모드 없는 클라이언트는 대역 갑옷 거치대 꼭대기 · VanillaEntities). 카메라를 보는 판 안에서 lift 칸 위 (이름표 글자도
 * 같은 판에서 위로 자라므로 어느 각도에서 봐도 이름표 바로 위). 원소 표시 (ElementFx) — 사용자 "속성이 날라다니면서 다른 속성으로 바뀌고 난리도 아니거든":
 * 예전에는 10틱마다 새 그림을 띄워 (수명 16틱 — 겹침) 맞은 몹의 속도 (넉백) 로 날아갔고, 서버 틱 끝에 띄운 그림은 첫 틱에 0번 칸 (불) 이 보였음.
 */
public final class Badge extends ElementHolder {
    /** 크기가 바뀌는 보간 (틱) */
    private static final int EASE = 3;

    private final Entity owner;
    private final Vfx.Sheet sheet;
    private final TextDisplayElement e;
    private final double lift;
    private int frame;
    private double size;
    private boolean attached;
    private boolean gone;
    /** 처음 띄울 때 작게 → 이 크기로 · 잠깐 크게 한 뒤 돌아갈 크기 (0 이하면 없음) — growIn 번째 틱에 (그 앞 모습이 한 틱은 보내지게) */
    private double growTo;
    private int growIn;

    private Badge(Entity owner, Vfx.Sheet sheet, int frame, double size, double lift) {
        this.owner = owner;
        this.sheet = sheet;
        this.frame = frame;
        this.lift = lift;
        this.size = size * 0.45;
        this.growTo = size;
        this.growIn = 2;
        e = new TextDisplayElement();
        e.setBillboardMode(Display.BillboardConstraints.CENTER);
        e.setBackground(0);
        e.setDefaultBackground(false);
        e.setShadow(false);
        e.setSeeThrough(false);
        e.setLineWidth(4096);
        e.setBrightness(Brightness.FULL_BRIGHT);
        e.setViewRange(1.0F);
        e.ignorePositionUpdates();
        text();
        transform();
        addPassengerElement(e); // 띄우기 전에 그림 · 크기를 정함 — 처음 패킷에 담김 (예전 그림의 첫 틱 0번 칸)
    }

    /**
     * owner 에 태운 그림 (그림판 sheetName 의 칸 frame · 높이 size 칸 · 탄 자리에서 lift 칸 위 — 카메라를 보는 판 안). 그림판이 없거나 owner 가 없으면 null.
     * 끝나면 {@link #remove} (owner 가 사라지거나 죽으면 스스로 지움)
     */
    public static Badge on(Entity owner, String sheetName, int frame, double size, double lift) {
        Vfx.Sheet s = Vfx.SHEETS.get(sheetName);
        if (s == null || owner == null || owner.isRemoved()) return null;
        Badge b = new Badge(owner, s, frame, size, lift);
        EntityAttachment.ofTicking(b, owner); // 이미 보던 사람은 여기서 startWatching (띄움)
        b.attached = true;
        // 태우기: 홀더가 엔티티에 붙은 뒤에야 Polymer 가 태우는 패킷에 이 그림 번호를 실음 (VanillaModels.attach 와 같음) → 이미 보던 사람에게 한 번
        if (!b.getWatchingPlayers().isEmpty()) {
            ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(owner);
            for (ServerGamePacketListenerImpl c : new ArrayList<>(b.getWatchingPlayers())) c.send(mount);
        }
        return b;
    }

    @Override
    public boolean startWatching(ServerGamePacketListenerImpl player) {
        if (gone || !super.startWatching(player)) return false;
        if (attached) player.send(new ClientboundSetPassengersPacket(owner)); // 붙인 뒤 짝을 맺은 사람 (Polymer 도 보냄 — 겹쳐도 같음)
        return true;
    }

    @Override
    protected void onTick() {
        if (gone) return;
        if (owner.isRemoved() || (owner instanceof LivingEntity le && (!le.isAlive() || le.getHealth() <= 0))) {
            remove();
            return;
        }
        if (growTo > 0 && --growIn <= 0) {
            double t = growTo;
            growTo = 0;
            size(t);
        }
    }

    /** 칸을 바꿈 (그림판의 다른 그림) */
    public Badge frame(int f) {
        if (gone || f == frame) return this;
        frame = f;
        text();
        return this;
    }

    /** 높이 (칸 — EASE 틱 보간) */
    public Badge size(double s) {
        if (gone || Math.abs(s - size) < 1e-4) return this;
        size = s;
        growTo = 0;
        e.setInterpolationDuration(EASE);
        transform();
        e.startInterpolationIfDirty();
        return this;
    }

    /** 잠깐 크게 했다가 size 로 (바뀐 것을 알림) */
    public Badge pop(double big, double s) {
        if (gone) return this;
        size = big;
        e.setInterpolationDuration(0);
        transform();
        e.startInterpolationIfDirty();
        growTo = s;
        growIn = 2;
        return this;
    }

    public int frame() {
        return frame;
    }

    public double sizeNow() {
        return size;
    }

    /** 처음 띄운 뒤 · 잠깐 크게 한 뒤 다음 틱의 크기를 기다림 */
    public boolean busy() {
        return growTo > 0;
    }

    public boolean gone() {
        return gone;
    }

    public Entity owner() {
        return owner;
    }

    /** 지움 (보던 사람에게서 바로) */
    public void remove() {
        if (gone) return;
        gone = true;
        destroy();
    }

    private void text() {
        e.setText(Component.literal(sheet.glyph(frame)).withStyle(Style.EMPTY.withFont(Vfx.FONT).withColor(0xFFFFFF)));
    }

    /** Vfx.Sprite.transform 과 같은 셈 (글꼴 그림 높이 · 자른 칸 자리) + 위로 lift (카메라를 보는 판 안) */
    private void transform() {
        float s = (float) (size / (Vfx.glyphHeight * Vfx.TEXT_UNIT));
        e.setTranslation(new Vector3f((float) (sheet.cx() * size), (float) (sheet.cy() * size + lift), 0F));
        e.setLeftRotation(new Quaternionf());
        e.setScale(new Vector3f(s, s, s));
        e.setRightRotation(new Quaternionf());
    }
}
