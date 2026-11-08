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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 모드 없는 클라이언트의 메뉴 (Shift + F). 2판 (2026-11-08 — 사용자 "너무 똑같은데 조금은 다르게", "색이나 배열이나 그런거 다르게 해서 그리고
 * 바닐라 아이템으로 꾸미는가 너무 밤티남", 세 창 (뽑기 · 스탯 트리 · 스킬 장착) 은 "Claude 가 새로 디자인").
 *
 * <p>6줄 상자 메뉴 (9 × 6, sgui) 하나에 쪽을 바꿔 그림 (제목 · 칸만 바꿈 — 다시 열지 않음). 모드 창과 같은 남색 판 · 금테 (tools/vanilla_gui.py).
 * 줄 0 = 위 탭 (홈 · 스탯 · 장비 · 스킬 — 칸 둘씩) + 닫기 (칸 8), 줄 1 ~ 5 = 쪽 내용, 아래 (가방 자리 · 줄 6 ~ 9) = 쪽마다:
 * 진짜 가방을 비춤 (홈 · 스탯 · 장비 · 스킬 — 누르면 쪽마다 할 일: 스킬 쪽 = 룬 고르기, 장비 쪽 = 갑옷 · 아티팩트 끼기 · 왼손) 또는 그 쪽의 단추판
 * (스탯 트리 = 방향 키 · 탭, 뽑기 = 최근 결과 · 단추 — 3판 2026-11-08 사용자 그림). 가방 자리까지 메뉴가 그리므로 (sgui manipulatePlayerSlots)
 * 가방은 비춘 그림 (아이템 복사본 — 5틱마다 진짜 가방과 맞춤). 그림 조각은 모두 직접 그린 것 (바닐라 아이템 그림 없음 — 큰 타일 그림만 게임 아이템 그림).
 * <ul>
 *   <li>홈: 큰 타일 넷 (장비 · 스킬 · 장터 · 뽑기 — 칸 넷 × 줄 둘, 왼쪽 그림 + 이름 · 설명) + 빠른 단추 아홉 (줄 5), 제목 줄에 레벨 · 직업 · 금화</li>
 *   <li>스탯 (VanillaStats) · 스탯 트리 (VanillaTree) · 장비 (VanillaEquip) · 스킬 (VanillaSkillBar) · 뽑기 (VanillaGacha)</li>
 * </ul>
 */
public final class VanillaMenu {
    private VanillaMenu() {}

    public enum Page { HOME, STATS, EQUIP, SKILLS, TREE, GACHA }

    /** 홈 타일 그림 (VanillaPack 이 rpg:vtile/&lt;이름&gt; 아이템 모델로 — 게임 아이템 그림, 칸 밖까지 1.5배) */
    public static final Map<String, String> TILES = new LinkedHashMap<>();

    static {
        TILES.put("equip", Mmorpg.NS + ":item/sword/sword_l40_hero");
        TILES.put("skill", Mmorpg.NS + ":item/rune/rune_blade_hero");
        TILES.put("market", Mmorpg.NS + ":item/material/gold_coin");
        TILES.put("gacha", Mmorpg.NS + ":item/rune/rune_ticket");
    }

    /** 메뉴 칸 수 (9 × 6) — 그 뒤 가방 27 (54 ~ 80) · 핫바 9 (81 ~ 89) */
    static final int SIZE = 54;
    /** 가방 자리까지 (메뉴가 그림 — 줄 6 ~ 9) */
    static final int ALL = SIZE + 36;

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

    /** 스탯 트리 쪽으로 (그 나무 — StatTrees.open 의 모드 없는 클라이언트) */
    public static void openTree(ServerPlayer sp, String stat) {
        MenuGui g = current(sp);
        if (g == null) {
            open(sp, Page.TREE);
            g = current(sp);
        }
        if (g == null) return;
        if (stat != null) g.treeStat = stat;
        g.treeSel = -1;
        g.treeTop = -1;
        g.show(Page.TREE);
    }

    /** 열린 메뉴 (없으면 null — 시험 · 쪽 그리는 곳) */
    static MenuGui current(ServerPlayer sp) {
        MenuGui g = sp == null ? null : OPEN.get(sp.getUUID());
        return g != null && g.isOpen() ? g : null;
    }

    /** 칸 번호 (줄 r 0 ~ 9 — 6 ~ 8 가방 자리, 9 핫바 자리 · 칸 c) */
    static int idx(int r, int c) {
        return r * 9 + c;
    }

    /** 가방 칸 → 메뉴의 칸 번호 (가방 9 ~ 35 = 54 ~ 80, 핫바 0 ~ 8 = 81 ~ 89) */
    static int invIndex(int slot) {
        return slot < Inventory.SELECTION_SIZE ? SIZE + 27 + slot : SIZE + slot - Inventory.SELECTION_SIZE;
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

    /** 쪽 제목 (제목 줄 왼쪽 — 금색) */
    static void title(VanillaGui.Title t, String name) {
        t.text(10, 0, name, VanillaGui.GOLD);
    }

    /** 메뉴 하나 (쪽 · 쪽마다의 상태) */
    static final class MenuGui extends SimpleGui {
        final ServerPlayer sp;
        Page page = Page.HOME;
        /** 스킬 쪽: 고른 룬 (룬 칸 또는 가방 칸) */
        VanillaSkillBar.Pick skillPick;
        /** 뽑기 쪽: 고른 뽑기 (null = 마지막 뽑기 → 한정 → 일반) */
        String gachaKey;
        /** 뽑기 쪽: 스탯 뽑기의 스탯 · 확인 창 (0 = 없음, 1 · 10 = 그 수로 뽑을지 묻는 중) */
        String gachaStat;
        int gachaConfirm;
        /** 스탯 트리 쪽: 보는 나무 ("sum" = 합계) · 고른 칸 (단계 × 2 + 갈래 (0 · 1, 갈래 아님 = 0) — -1 = 저절로) · 보이는 맨 윗줄 · 초기화 누른 때 (틱) */
        String treeStat;
        int treeSel = -1;
        int treeTop = -1;
        long treeArm;
        /** 가방 자리에 비춘 가방 (진짜 가방과 다르면 다시 비춤) */
        private final ItemStack[] mirrored = new ItemStack[36];
        private int ticks;

        MenuGui(ServerPlayer sp) {
            super(MenuType.GENERIC_9x6, sp, true);
            this.sp = sp;
            setLockPlayerInventory(true);
        }

        /** 이 쪽이 가방 자리에 진짜 가방을 비추는지 (스탯 트리는 그 자리를 단추판으로, 뽑기 확인 창은 어둡게 덮음) */
        boolean showsBag() {
            return page != Page.TREE && !(page == Page.GACHA && gachaConfirm > 0);
        }

        void show(Page p) {
            if (p != page) {
                skillPick = null;
                gachaConfirm = 0;
                SkillFx.playFor(sp, "ui.button.click", 1.7, 0.3);
            }
            page = p;
            refresh();
        }

        /** 쪽을 다시 그림 (칸 · 제목) */
        void refresh() {
            for (int i = 0; i < ALL; i++) clearSlot(i);
            VanillaGui.Title t = new VanillaGui.Title().background(switch (page) {
                case HOME -> VanillaGui.Bg.HOME;
                case STATS -> VanillaGui.Bg.STATS;
                case EQUIP -> VanillaGui.Bg.EQUIP;
                case SKILLS -> VanillaGui.Bg.SKILLS;
                case TREE -> VanillaGui.Bg.TREE;
                case GACHA -> gachaConfirm > 0 ? VanillaGui.Bg.GACHA_CONFIRM : VanillaGui.Bg.GACHA;
            });
            if (page != Page.GACHA) tabs(); // 뽑기는 전용 창 (위 탭 없음 — 돌아가기 단추)
            if (showsBag()) mirrorBag();
            switch (page) {
                case HOME -> home(t);
                case STATS -> VanillaStats.render(this, t);
                case EQUIP -> VanillaEquip.render(this, t);
                case SKILLS -> VanillaSkillBar.render(this, t);
                case TREE -> VanillaTree.render(this, t);
                case GACHA -> VanillaGacha.render(this, t);
            }
            setTitle(t.build());
        }

        /** 칸 하나 (줄 r 0 ~ 9 · 칸 c) */
        void set(int r, int c, ItemStack icon, GuiElement.ClickCallback cb) {
            setSlot(idx(r, c), new SimpleGuiElement(icon, cb == null ? GuiElement.EMPTY_CALLBACK : cb));
        }

        /** 가방 자리에 진짜 가방을 비춤 (복사본 — 누르면 쪽마다 할 일, 아이템은 움직이지 않음) */
        private void mirrorBag() {
            Inventory inv = sp.getInventory();
            for (int s = 0; s < 36; s++) mirrorSlot(inv, s);
        }

        private void mirrorSlot(Inventory inv, int s) {
            ItemStack st = inv.getItem(s);
            mirrored[s] = st.copy();
            int slot = s;
            setSlot(invIndex(s), new SimpleGuiElement(st.copy(), (ix, type, a, g) -> bagClick(slot, type)));
        }

        /** 비춘 가방 칸 하나를 빛나게 (고른 룬 — 스킬 쪽) */
        void markBag(int slot) {
            if (slot < 0 || slot >= 36) return;
            ItemStack st = sp.getInventory().getItem(slot).copy();
            st.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            setSlot(invIndex(slot), new SimpleGuiElement(st, (ix, type, a, g) -> bagClick(slot, type)));
        }

        private void bagClick(int slot, ClickType type) {
            switch (page) {
                case SKILLS -> VanillaSkillBar.inventoryClick(this, slot, type);
                case EQUIP -> VanillaEquip.inventoryClick(this, slot, type);
                default -> {
                }
            }
        }

        /** 틱마다: 비춘 가방을 진짜 가방과 맞춤 (5틱마다 — 주운 것 · 뽑은 룬) · 쪽마다 움직임 (뽑기 결과 펼침은 Tasks) */
        @Override
        public void onTick() {
            if (++ticks % 5 != 0 || !showsBag()) return;
            Inventory inv = sp.getInventory();
            for (int s = 0; s < 36; s++) {
                if (mirrored[s] == null || !ItemStack.matches(inv.getItem(s), mirrored[s])) mirrorSlot(inv, s);
            }
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

        /** 위 탭 (줄 0 · 칸 둘씩) + 닫기 (칸 8) */
        private void tabs() {
            String[][] tabs = {{"홈", "타일 · 빠른 단추"}, {"스탯", "스탯 올리기 · 스탯 트리"}, {"장비", "갑옷 · 무기 · 아티팩트"}, {"스킬", "룬 스킬 칸 · 직업 스킬"}};
            Page[] pages = {Page.HOME, Page.STATS, Page.EQUIP, Page.SKILLS};
            for (int i = 0; i < 4; i++) {
                Page p = pages[i];
                boolean on = page == p || (page == Page.GACHA && p == Page.HOME) || (page == Page.TREE && p == Page.STATS);
                ItemStack icon = VanillaGui.blank((on ? "§6" : "§f") + tabs[i][0], List.of("§7" + tabs[i][1]));
                GuiElement.ClickCallback cb = left(() -> show(p));
                set(0, 2 * i, icon, cb);
                set(0, 2 * i + 1, icon, cb);
            }
            set(0, 8, VanillaGui.blank("§c닫기", List.of("§7Shift + F 로 다시 열기")), left(this::close));
        }

        private void home(VanillaGui.Title t) {
            // 제목 줄: 금화만 (오른쪽 — 사용자 "글자를 최대한 줄여서", 레벨 · 직업은 장비 타일 설명)
            String g = VanillaGui.commas(Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD));
            t.right(166, 0, g, VanillaGui.GOLD);
            t.text(166 - VanillaGui.width(g) - VanillaGui.width(VanillaGui.COIN) - 2, 0, VanillaGui.COIN, VanillaGui.WHITE);
            mmorpg.core.Jobs.Job job = mmorpg.fabric.job.JobSystem.job(sp);
            String who = "§7Lv." + sp.experienceLevel + " · " + (job == null ? "직업 없음" : job.name());

            tile(0, 1, "equip", "§b장비", "§7갑옷 · 무기 · 아티팩트 끼고 빼기\n" + who, () -> show(Page.EQUIP));
            tile(5, 1, "skill", "§a스킬", "§7룬 스킬 칸 · 직업 스킬 (F 로 스킬 모드)", () -> show(Page.SKILLS));
            tile(0, 3, "market", "§6장터", "§7다른 사람이 올린 물건을 사고 팖", () -> closeThen(p -> RpgNet.statAction(p, "market")));
            tile(5, 3, "gacha", "§d뽑기", "§7룬 뽑기 전용 창 (1회 · " + mmorpg.core.Gacha.multi() + "회)", () -> show(Page.GACHA));

            quick(0, "§e퀘스트", "§7받은 퀘스트 · 진행", p -> RpgNet.statAction(p, "quest"));
            quick(1, "§d창고", "§7엔더 창고 (큰 상자 5개)", p -> RpgNet.statAction(p, "ender"));
            quick(2, "§6탈것", "§7고른 탈것을 불러 탐 · 타고 있으면 돌려보냄", p -> RpgNet.statAction(p, "mount"));
            quick(3, "§6탈것 고르기", "§7탈 것 고르기", p -> RpgNet.statAction(p, "mount_select"));
            quick(4, "§6유물", "§7꽂은 유물 요약 (채팅)", mmorpg.fabric.relic.RelicSystem::open);
            set(5, 5, VanillaGui.blank("§a스탯 트리", List.of("§7스탯마다 열리는 칸 · 고를 갈래", "", "§e눌러서 열기")), left(() -> show(Page.TREE)));
            set(5, 6, VanillaGui.blank("§8꾸미기", List.of("§8모드 없는 접속은 준비 중")), null);
            quick(7, "§c쓰레기통", "§7넣고 닫으면 지워집니다", p -> RpgNet.statAction(p, "trash"));
            set(5, 8, VanillaGui.blank("§b조작", List.of(
                    "§fF §7스킬 모드: 일반 → 룬 → 직업 → 일반",
                    "§7스킬 모드에서 §f숫자 키§7로 스킬",
                    "§f우클릭 · Shift + 우클릭 §7무기 스킬",
                    "§fShift + F §7이 메뉴",
                    "§7왼손: 장비 쪽에서 가방 아이템 §f우클릭")), null);
        }

        /** 타일 (칸 넷 × 줄 둘 — 왼쪽 위 칸에 큰 그림, 나머지는 같은 설명 · 같은 일). tip 은 줄바꿈 (\n) 으로 여러 줄 */
        private void tile(int c0, int r0, String key, String name, String tip, Runnable run) {
            List<String> lore = new java.util.ArrayList<>(List.of(tip.split("\n")));
            lore.add("");
            lore.add("§e눌러서 열기");
            GuiElement.ClickCallback cb = left(run);
            for (int r = r0; r < r0 + 2; r++) {
                for (int c = c0; c < c0 + 4; c++) {
                    ItemStack icon = r == r0 && c == c0 ? VanillaGui.icon(Mmorpg.rpg("vtile/" + key), name, lore) : VanillaGui.blank(name, lore);
                    set(r, c, icon, cb);
                }
            }
        }

        /** 빠른 단추 (줄 5 · 칸 c — 그림은 바탕에) — 다른 창 · 채팅을 여는 것은 메뉴를 닫고 */
        private void quick(int c, String name, String tip, Consumer<ServerPlayer> run) {
            set(5, c, VanillaGui.blank(name, List.of(tip, "", "§e눌러서 열기")), left(() -> closeThen(run)));
        }

        /** 누름은 모두 칸의 할 일 (가방 자리도 메뉴가 그린 칸 — sgui manipulatePlayerSlots 는 가방을 늘 잠금) */
        @Override
        public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
            return true;
        }

        @Override
        public void onPlayerClose(boolean success) {
            OPEN.remove(sp.getUUID(), this);
        }
    }
}
