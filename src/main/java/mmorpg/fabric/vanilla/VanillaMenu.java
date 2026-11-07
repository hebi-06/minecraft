package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.SimpleGuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 모드 없는 클라이언트의 메뉴 (사용자 2026-11-07 — 위르드 서버 사진 다섯 장: "쉬프트 f 누르면 이렇게 메인페이지가 뜨고 장비 들어가면 장비 장터 들어가면 장터
 * 뽑기 들어가면 뽑기 … 스탯에 들어가면 5번째 사진처럼 깔끔하게 … 화살표 … 스탯 트리로 … 스킬 세팅도 왼쪽에 박고 갑옷 패이지도 2번째 사진처럼").
 *
 * <p>4줄 상자 메뉴 (9 × 4, sgui) 하나에 쪽을 바꿔 그림 (제목 · 칸만 바꿈 — 다시 열지 않음). 칸 0 열 = 왼쪽 탭 (홈 · 스탯 · 스킬 · 장비), 칸 1 ~ 8 = 쪽 내용,
 * 아래 = 진짜 가방 (잠금 — 누르면 쪽마다 할 일: 스킬 쪽 = 룬 고르기, 장비 쪽 = 갑옷 · 아티팩트 끼기). 바탕 · 글자는 VanillaGui (제목 글꼴 그림).
 * <ul>
 *   <li>홈: 카드 넷 (장비 · 스킬 · 장터 · 뽑기 — 칸 둘 × 줄 셋) + 작은 단추 여덟 (퀘스트 · 창고 · 탈것 · 탈것 고르기 · 유물 · 스탯 트리 · 꾸미기 · 쓰레기통),
 *   제목 줄에 레벨 · 직업 · 금화</li>
 *   <li>스탯 (VanillaStats) · 스킬 (VanillaSkillBar) · 장비 (VanillaEquip) · 뽑기 (VanillaGacha)</li>
 * </ul>
 * 여는 곳: Shift + F (VanillaInput), 스탯 책 (RpgNet.openStats → 스탯 쪽), /rpg 메뉴 등.
 */
public final class VanillaMenu {
    private VanillaMenu() {}

    public enum Page { HOME, STATS, SKILLS, EQUIP, GACHA }

    /** 홈 카드 그림 (VanillaPack 이 rpg:vtile/&lt;이름&gt; 아이템 모델로 — 그림 = 아이템 그림 경로) */
    public static final Map<String, String> TILES = new LinkedHashMap<>();

    static {
        TILES.put("equip", Mmorpg.NS + ":item/armor/str_l40_transcend_chest");
        TILES.put("skill", Mmorpg.NS + ":item/rune/rune_blade_hero");
        TILES.put("market", Mmorpg.NS + ":item/material/gold_coin");
        TILES.put("gacha", Mmorpg.NS + ":item/rune/rune_ticket");
    }

    /** 열린 메뉴 (사람마다 하나) */
    private static final Map<UUID, MenuGui> OPEN = new ConcurrentHashMap<>();

    /** 메뉴를 그 쪽으로 엶 (이미 열려 있으면 그 자리에서 쪽만 바꿈) */
    public static void open(ServerPlayer sp, Page page) {
        if (sp == null || sp.isRemoved()) return;
        MenuGui g = OPEN.get(sp.getUUID());
        if (g != null && g.isOpen()) {
            g.show(page);
            return;
        }
        g = new MenuGui(sp);
        OPEN.put(sp.getUUID(), g);
        g.page = page;
        g.refresh();
        g.open();
        SkillFx.playFor(sp, "ui.button.click", 1.4, 0.35);
    }

    /** 열린 메뉴 (없으면 null — 시험 · 쪽 그리는 곳) */
    static MenuGui current(ServerPlayer sp) {
        MenuGui g = sp == null ? null : OPEN.get(sp.getUUID());
        return g != null && g.isOpen() ? g : null;
    }

    /** 칸 번호 (줄 r 0 ~ 3 · 칸 c) */
    static int idx(int r, int c) {
        return r * 9 + c;
    }

    /** 가방 칸 → 메뉴의 칸 번호 (가방 9 ~ 35 = 36 ~ 62, 핫바 0 ~ 8 = 63 ~ 71) */
    static int invIndex(int slot) {
        return slot < Inventory.SELECTION_SIZE ? 63 + slot : 36 + slot - Inventory.SELECTION_SIZE;
    }

    /** 가방의 빈 칸에 넣음 (가방 9 ~ 35 먼저, 그다음 핫바). 넣었으면 true */
    static boolean bag(ServerPlayer sp, ItemStack stack) {
        Inventory inv = sp.getInventory();
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? Inventory.SELECTION_SIZE : 0;
            int to = pass == 0 ? Inventory.INVENTORY_SIZE : Inventory.SELECTION_SIZE;
            for (int i = from; i < to; i++) {
                if (!inv.getItem(i).isEmpty()) continue;
                inv.setItem(i, stack);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    /** 메뉴 하나 (쪽 · 쪽마다의 상태) */
    static final class MenuGui extends SimpleGui {
        final ServerPlayer sp;
        Page page = Page.HOME;
        /** 스킬 쪽: 고른 룬 (룬 칸 또는 가방 칸) */
        VanillaSkillBar.Pick skillPick;
        /** 뽑기 쪽: 고른 뽑기 · 보이는 결과 · 펼친 수 */
        String gachaKey;
        List<ItemStack> gachaResults = List.of();
        int gachaShown;
        long gachaSeq;

        MenuGui(ServerPlayer sp) {
            super(MenuType.GENERIC_9x4, sp, false);
            this.sp = sp;
            setLockPlayerInventory(true);
        }

        void show(Page p) {
            if (p != page) {
                skillPick = null;
                SkillFx.playFor(sp, "ui.button.click", 1.7, 0.3);
            }
            page = p;
            refresh();
        }

        /** 쪽을 다시 그림 (칸 · 제목) */
        void refresh() {
            for (int i = 0; i < 36; i++) clearSlot(i);
            VanillaGui.Title t = new VanillaGui.Title().background(switch (page) {
                case HOME -> VanillaGui.Bg.HOME;
                case STATS -> VanillaGui.Bg.STATS;
                case SKILLS -> VanillaGui.Bg.SKILLS;
                case EQUIP -> VanillaGui.Bg.EQUIP;
                case GACHA -> VanillaGui.Bg.GACHA;
            });
            tabs();
            switch (page) {
                case HOME -> home(t);
                case STATS -> VanillaStats.render(this, t);
                case SKILLS -> VanillaSkillBar.render(this, t);
                case EQUIP -> VanillaEquip.render(this, t);
                case GACHA -> VanillaGacha.render(this, t);
            }
            setTitle(t.build());
        }

        /** 칸 하나 (줄 r · 칸 c) */
        void set(int r, int c, ItemStack icon, GuiElement.ClickCallback cb) {
            setSlot(idx(r, c), new SimpleGuiElement(icon, cb == null ? GuiElement.EMPTY_CALLBACK : cb));
        }

        /** 왼쪽 클릭이면 run */
        static GuiElement.ClickCallback left(Runnable run) {
            return (ix, type, a, g) -> {
                if (type.isLeft) run.run();
            };
        }

        /** 메뉴를 닫고 그다음 (채팅 · 다른 창을 여는 단추) */
        void closeThen(Consumer<ServerPlayer> next) {
            close();
            next.accept(sp);
        }

        private void tabs() {
            String[][] tabs = {{"홈", "카드 · 단추 모음"}, {"스탯", "스탯 올리기 · 스탯 트리"}, {"스킬", "룬 스킬 칸 · 직업 스킬"}, {"장비", "갑옷 · 무기 · 아티팩트"}};
            Page[] pages = {Page.HOME, Page.STATS, Page.SKILLS, Page.EQUIP};
            for (int r = 0; r < 4; r++) {
                Page p = pages[r];
                boolean on = page == p || (page == Page.GACHA && p == Page.HOME);
                set(r, 0, VanillaGui.blank((on ? "§e" : "§f") + "[ " + tabs[r][0] + " ]", List.of("§7" + tabs[r][1])), left(() -> show(p)));
            }
        }

        private void home(VanillaGui.Title t) {
            // 제목 줄: 레벨 · 직업 (왼쪽) · 금화 (오른쪽)
            String lv = "Lv." + sp.experienceLevel;
            t.text(31, 0, lv, VanillaGui.GOLD_INK);
            mmorpg.core.Jobs.Job job = mmorpg.fabric.job.JobSystem.job(sp);
            t.text(31 + VanillaGui.width(lv) + 5, 0, job == null ? "직업 없음" : job.name(), job == null ? VanillaGui.SUB : VanillaGui.INK);
            long gold = Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD);
            String g = VanillaGui.commas(gold);
            t.right(168, 0, g, VanillaGui.INK);
            t.text(168 - VanillaGui.width(g) - VanillaGui.width(VanillaGui.COIN) - 2, 0, VanillaGui.COIN, VanillaGui.WHITE);

            card(0, "equip", "§b장비", "§7갑옷 · 무기 · 아티팩트 끼고 빼기", () -> show(Page.EQUIP));
            card(1, "skill", "§a스킬", "§7룬 스킬 칸 · 직업 스킬 (F 로 스킬 모드)", () -> show(Page.SKILLS));
            card(2, "market", "§c장터", "§7다른 사람이 올린 물건을 사고 팖", () -> closeThen(p -> RpgNet.statAction(p, "market")));
            card(3, "gacha", "§d뽑기", "§7룬 뽑기 (1회 · " + mmorpg.core.Gacha.multi() + "회)", () -> show(Page.GACHA));

            small(1, VanillaGui.ui("q_active", "", List.of()), "§e퀘스트", "§7받은 퀘스트 · 진행", true, p -> RpgNet.statAction(p, "quest"));
            small(2, VanillaGui.ui("ender_chest", "", List.of()), "§d창고", "§7엔더 창고 (큰 상자 5개)", true, p -> RpgNet.statAction(p, "ender"));
            small(3, VanillaGui.ui("icon_mount", "", List.of()), "§6탈것", "§7고른 탈것을 불러 탐 · 타고 있으면 돌려보냄", true, p -> RpgNet.statAction(p, "mount"));
            small(4, new ItemStack(Items.SADDLE), "§6탈것 고르기", "§7탈 것 고르기", true, p -> RpgNet.statAction(p, "mount_select"));
            small(5, VanillaGui.icon(Mmorpg.rpg("relic_aegishjalmr_hero"), "", List.of()), "§6유물", "§7꽂은 유물 요약 (채팅)", true,
                    mmorpg.fabric.relic.RelicSystem::open);
            small(6, new ItemStack(Items.OAK_SAPLING), "§a스탯 트리", "§7스탯마다 받은 칸 · 고를 갈래 (채팅)", true,
                    p -> mmorpg.fabric.stattree.StatTrees.open(p, null));
            small(7, VanillaGui.icon(Mmorpg.rpg("cosmetic_angel_wings"), "", List.of()), "§8꾸미기", "§8모드 없는 접속은 준비 중", false, p -> {});
            small(8, new ItemStack(Items.LAVA_BUCKET), "§c쓰레기통", "§7넣고 닫으면 지워집니다", true, p -> RpgNet.statAction(p, "trash"));
        }

        /** 카드 (칸 둘 × 줄 셋 — 왼쪽 위 칸에 큰 그림, 나머지는 같은 설명 · 같은 일) */
        private void card(int i, String tile, String name, String tip, Runnable run) {
            int c0 = 1 + 2 * i;
            List<String> lore = List.of(tip, "", "§e눌러서 열기");
            GuiElement.ClickCallback cb = left(run);
            for (int r = 0; r < 3; r++) {
                for (int c = c0; c < c0 + 2; c++) {
                    ItemStack icon = r == 0 && c == c0 ? VanillaGui.icon(Mmorpg.rpg("vtile/" + tile), name, lore) : VanillaGui.blank(name, lore);
                    set(r, c, icon, cb);
                }
            }
        }

        /** 작은 단추 (줄 3 · 칸 c) — 다른 창 · 채팅을 여는 것은 메뉴를 닫고 */
        private void small(int c, ItemStack icon, String name, String tip, boolean on, Consumer<ServerPlayer> run) {
            set(3, c, VanillaGui.named(icon, name, List.of(tip)), left(() -> {
                if (on) closeThen(run);
            }));
        }

        /** 칸 · 가방 누름: 메뉴 칸은 sgui 가 (true), 가방 칸은 쪽마다 (아이템은 움직이지 않음) */
        @Override
        public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
            if (index < 36) return true;
            if (index >= 72) return false;
            int slot = index < 63 ? index - 36 + Inventory.SELECTION_SIZE : index - 63;
            switch (page) {
                case SKILLS -> VanillaSkillBar.inventoryClick(this, slot, type);
                case EQUIP -> VanillaEquip.inventoryClick(this, slot, type);
                default -> {
                }
            }
            return false;
        }

        @Override
        public void onPlayerClose(boolean success) {
            OPEN.remove(sp.getUUID(), this);
        }
    }
}
