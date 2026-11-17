package mmorpg.fabric.job;

import java.util.function.Predicate;
import mmorpg.core.Jobs;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.PBridge;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-15 직업 전용 2D 이펙트 그림 (사용자 "직업마다 전용으로 그릴래" — 시범 팬텀 버스트 "헐 나은데? 이렇게 하자"): 미리 렌더링한 칠한 2D 프레임
 * (tools/jobvfx/&lt;직업&gt;.py — 글자 표시는 명암 계산이 없어 칠한 색 그대로 · 큰 그림은 2 × 2 조각 512 픽셀) · 날아가는 불씨 · 칼 조각 · 발광 외곽선 (리소스 팩
 * 후처리 post_effect/entity_outline — 자체 가우스 흐림 shaders/post/vfx_blur.fsh 로 부드러운 빛 번짐).
 * <p>
 * 그림 묶음 {@link Lib} = 그림판 이름 앞 (tools/jobvfx 의 LIB) · 발광 색: 팬텀 아처 (pa — 진홍 불) · 아스널 킹 (ak — 금빛 문 · 빛) · 블레이드 레인저 (br — 단조
 * 불빛 · 달아오른 베기). 다른 직업은 {@link #lib} 이 null (예전 길).
 */
final class PhantomFx {
    private PhantomFx() {}

    /** 칼 · 폭발의 발광 빛 (외곽선 → 빛 번짐 후처리) 색 */
    static final int GLOW_CRIMSON = 0xFF3A3A;
    static final int GLOW_CYAN = 0x7FD8FF;
    static final int GLOW_GOLD = 0xFFC23A;
    static final int GLOW_FORGE = 0xFF6A1A;

    /** 직업 전용 그림 묶음: 그림판 이름 앞 p (pa_boom …) · 칼 발광 색 glow · 투영 선 발광 색 trace · 날아가는 작은 그림 이름 ember (불씨 · 반짝임 · 불티) */
    record Lib(String p, int glow, int trace, String ember) {
        String n(String name) {
            return p + "_" + name;
        }
    }

    /** 팬텀 아처 (tools/jobvfx/phantom.py — 진홍 불 · 푸른 흰 투영 · 강철) */
    static final Lib PHANTOM = new Lib("pa", GLOW_CRIMSON, GLOW_CYAN, "ember");
    /** 2026-11-15 아스널 킹 (tools/jobvfx/arsenal.py — 금빛 문 · 빛 폭발 · 금가루 · 보석 반짝임) */
    static final Lib GOLD = new Lib("ak", GLOW_GOLD, GLOW_GOLD, "sparkle");
    /** 2026-11-15 블레이드 레인저 (tools/jobvfx/forge.py — 달아오른 베기 · 쇳물 폭발 · 불티 · 푸른 흰 투영) */
    static final Lib FORGE = new Lib("br", GLOW_FORGE, GLOW_CYAN, "spark");

    /** 2026-11-15 원소 공용 묶음 (tools/jobvfx/element.py — 사용자 "원소 공용 + 직업 대표") 의 발광 색 */
    static final int GLOW_STEEL = 0x8CC8FF;
    static final int GLOW_BLOOD = 0xE8283A;
    /** 강철 · 푸른 달빛 (검사 — el_steel: 칼빛 초승달 · 빛 폭발 · 한 바퀴 베기 · 땅 가르기 · 일섬 · 반짝임) */
    static final Lib STEEL = new Lib("el_steel", GLOW_STEEL, GLOW_STEEL, "ember");
    /** 피 (블러드 나이트 — el_blood: 핏빛 초승달 · 피 물보라 · 맞은 자리 피 튐 · 피 기둥 · 핏빛 달 · 핏방울) */
    static final Lib BLOOD = new Lib("el_blood", GLOW_BLOOD, GLOW_BLOOD, "ember");
    /** 2026-11-15 그림자 (암살자 — el_shadow: 짙은 보라 · 자홍 — 그림자 연기 · 엇갈린 베기 · 검은 장미 · 꽃 · 꽃잎 · 도는 비수 · 찌르기 · 일식, tools/jobvfx/shadow.py) */
    static final int GLOW_SHADOW = 0xC45CFF;
    static final Lib SHADOW = new Lib("el_shadow", GLOW_SHADOW, GLOW_SHADOW, "ember");
    /**
     * 2026-11-16 비전 (매지션 · 아크메이지 — el_arcane: 남빛 · 금빛 — 떠도는 별 · 별 마탄 · 광선 · 깨지는 방패 · 금서의 주문 · 눈꽃 · 순간 이동 줄기 · 솟는 별빛 ·
     * 별자리의 별 · 별똥 · 별의 소용돌이 · 빛 고리 · 빛의 방패 · 별빛 맞음, tools/jobvfx/arcane.py)
     */
    static final int GLOW_ARCANE = 0x8FA8FF;
    static final Lib ARCANE = new Lib("el_arcane", GLOW_ARCANE, GLOW_ARCANE, "ember");
    /** 2026-11-16 혼 (네크로맨서 — el_soul: 녹색 혼불 · 뼈빛 — 혼불 · 해골 · 갈라진 땅 · 뼈 창 · 망자의 손 · 망자의 연기 · 혼 고리 · 혼 터짐, tools/jobvfx/soul.py) */
    static final int GLOW_SOUL = 0x5CF08A;
    static final Lib SOUL = new Lib("el_soul", GLOW_SOUL, GLOW_SOUL, "ember");
    /**
     * 2026-11-16 환영 (트릭스터 — el_illusion: 자홍 · 짙은 보라 + 청록 유리빛 — 아치 거울 · 깨지는 거울 · 손거울 · 띠 고리, tools/jobvfx/mirror.py · 공용 그림을 환영 빛으로)
     */
    static final int GLOW_ILLUSION = 0xEE66C2;
    static final Lib ILLUSION = new Lib("el_illusion", GLOW_ILLUSION, 0x6FE6F4, "ember");
    /** 2026-11-16 서커스 (피에로 — el_circus: 진홍 · 크림 금빛, 무지개 없이 — 색종이 · 풍선 동물 · 불꽃놀이 · 조명, tools/jobvfx/circus.py · 공용 그림을 서커스 빛으로) */
    static final int GLOW_CIRCUS = 0xFF6A68;
    static final Lib CIRCUS = new Lib("el_circus", GLOW_CIRCUS, 0xFFE28A, "ember");
    /** 2026-11-16 분노 (전사 — 주황 · 진홍 불씨: tools/jobvfx/beast.py — 불꽃 갈기 늑대 · 회오리 · 도는 도끼날 · 불꽃 꼬리 · 방패 깨짐 · 갈라지는 땅 · 몸의 불꽃) */
    static final int GLOW_RAGE = 0xFF8F28;
    static final Lib RAGE = new Lib("el_rage", GLOW_RAGE, 0xFFD050, "ember");
    /** 2026-11-16 곰 (워로드 — 호박빛 · 갈색: 곰의 혼 · 앞발 · 갈라지는 땅 · 몸의 불꽃. 돌창 · 곰 변신은 3D 그대로) */
    static final int GLOW_BEAR = 0xFF9A30;
    static final Lib BEAR = new Lib("el_bear", GLOW_BEAR, 0xFFC266, "ember");
    /** 2026-11-16 서리 (프로스트 레이더 — 옅은 얼음빛 · 빙하 남빛: 서리 고리 · 얼음 결정 · 서리 띠. 도끼 · 결정 · 얼음 가시는 3D 그대로) */
    static final int GLOW_FROST = 0x7ED0F8;
    static final Lib FROST = new Lib("el_frost", GLOW_FROST, 0xE2F7FF, "ember");
    /** 2026-11-16 룬 (마검사 · 아케인 블레이드 — 보랏빛 · 청록: tools/jobvfx/rune.py · storm.py — 룬 띠 · 새긴 글자 · 아홉 글자 · 빛기둥 · 찢어짐 · 번개 · 빛의 창) */
    static final int GLOW_RUNE = 0xB484FF;
    static final Lib RUNE = new Lib("el_rune", GLOW_RUNE, 0x57F5F2, "ember");
    /** 2026-11-16 원소의 칼 (엘리멘탈 블레이드 — 금빛 주황 · 얼음 하늘빛: 번개 · 번개 고리 · 빛의 창 · 불꽃 혀 · 회오리 · 서리 띠). 물 · 얼음 빛은 서리 · 바위는 곰 묶음 */
    static final int GLOW_ELEMENT = 0xFFB84C;
    static final Lib ELEMENT = new Lib("el_element", GLOW_ELEMENT, 0x8CE6FF, "ember");
    /** 2026-11-16 성광 (사제 · 세인트 — 크림 금빛 · 회복의 연둣빛: 회복 십자 · 음표 · 성흔 · 종 · 연꽃 · 큰 꽃 · 나비 — tools/jobvfx/holy.py) */
    static final int GLOW_HOLY = 0xF2D27A;
    static final Lib HOLY = new Lib("el_holy", GLOW_HOLY, 0x8FF0A6, "ember");
    /** 2026-11-16 꽃잎 (세인트가 PETAL 빛으로 부르는 꽃 · 연꽃 · 나비 · 꽃잎 · 일섬 — 분홍 꽃잎 · 크림 금빛 속) */
    static final int GLOW_PETAL = 0xFFA6C8;
    static final Lib PETALS = new Lib("el_petal", GLOW_PETAL, 0xFFE6A0, "ember");
    /** 2026-11-16 혈주 (워록 — 진홍 피 · 금빛: 핏빛 창 · 웅덩이 · 핏물 기둥 · 피의 심장 · 핏빛 불기둥 — tools/jobvfx/hex.py) */
    static final int GLOW_HEX = 0xE0304C;
    static final Lib HEX = new Lib("el_hex", GLOW_HEX, 0xFFD070, "ember");
    /** 2026-11-16 수호 (수호기사 — 강철 푸른빛 · 흰 빛: 도는 방패 · 빛의 다리 · 빛의 사슬 · 빛의 방패 — tools/jobvfx/ward.py) */
    static final int GLOW_WARD = 0x8EAAE2;
    static final Lib WARD = new Lib("el_ward", GLOW_WARD, 0xF0F6FF, "ember");
    /** 2026-11-16 아가리 (다크 나이트 — 진홍 · 검은 공허: 방패의 아가리 · 검은 혀 · 땅의 아가리 · 불씨 기둥 — tools/jobvfx/maw.py) */
    static final int GLOW_MAW = 0xD8303C;
    static final Lib MAW = new Lib("el_maw", GLOW_MAW, 0xE8DED0, "ember");
    /** 2026-11-16 뇌광 (성기사 · 썬더로드 — 금빛 · 하늘빛 번개: 벼락 · 번개 고리 · 번개 줄 · 먹구름 · 돌 껍데기 · 번개 망치 · 번개 기둥 — tools/jobvfx/thunder.py · storm.py) */
    static final int GLOW_THUNDER = 0xFFD460;
    static final Lib THUNDER = new Lib("el_thunder", GLOW_THUNDER, 0x9CDCFF, "ember");
    /** 2026-11-16 폭군 (타이런트 — 보랏빛 · 핏빛 번개: 뇌광과 같은 그림 · 거인의 손) */
    static final int GLOW_TYRANT = 0xBC78FF;
    static final Lib TYRANT = new Lib("el_tyrant", GLOW_TYRANT, 0xFF8080, "ember");
    /** 2026-11-16 사신 라인 (사신 청록 혼 · 소울 리퍼 하늘빛 · 엑시큐셔너 금빛 · 자줏빛: 망자의 비명 · 사신 그림자 · 처형 표식 — tools/jobvfx/reap.py · soul.py) */
    static final Lib REAP = new Lib("el_reap", 0x62E8D6, 0x9AF6F0, "ember");
    static final Lib SOULREAP = new Lib("el_soulreap", 0x64B8F6, 0xA8E2FF, "ember");
    static final Lib EXEC = new Lib("el_exec", 0xF8CC68, 0xE07ADC, "ember");
    /** 2026-11-16 창술사 라인 (창술사 금빛 · 스톰 랜서 박하빛 · 드라군 주홍: 깃털 · 바람 고리 · 도는 창 · 바람 소용돌이 · 불꽃 덩이 · 용의 혼 — tools/jobvfx/lance.py) */
    static final Lib LANCE = new Lib("el_lance", 0xF8D06E, 0xFFFAEC, "ember");
    static final Lib GALE = new Lib("el_gale", 0x72F0C6, 0xF2FFFA, "ember");
    static final Lib DRAGONL = new Lib("el_dragon", 0xFF7E2E, 0xFFD060, "ember");
    /** 2026-11-16 푸른 불사조 (피닉스 가드 — 하늘빛 불꽃: 불꽃 날개 · 불사조 · 분노의 부채꼴 — tools/jobvfx/phoenix.py) */
    static final Lib PHOENIXL = new Lib("el_phoenix", 0x6EB0FF, 0xC8ECFF, "ember");

    /** 이 직업의 그림 묶음 (그리지 않은 직업은 null — 예전 길) */
    static Lib lib(Jobs.Job j) {
        if (j == null) return null;
        return switch (j.id()) {
            case "archer" -> PHANTOM;
            case "arsenalking", "swordmaster" -> GOLD;
            case "bladeranger" -> FORGE;
            case "swordsman" -> STEEL;
            case "bloodknight" -> BLOOD;
            case "assassin" -> SHADOW;
            case "mage", "arcmage" -> ARCANE;
            case "necromancer" -> SOUL;
            case "trickster" -> ILLUSION; // 2026-11-16 남은 직업 위르드식 (트릭스터 · 피에로 — 그림 도구는 2026-11-15 에 있었음)
            case "pierrot" -> CIRCUS;
            case "warrior" -> RAGE; // 2026-11-16 전사 라인 (tools/jobvfx/beast.py)
            case "warlord" -> BEAR;
            case "frostraider" -> FROST;
            case "spellblade", "arcaneblade" -> RUNE; // 2026-11-16 마검사 라인 (tools/jobvfx/rune.py)
            case "elementalblade" -> elementLib(j.color()[0]); // 2026-11-16 원소마다 물들인 직업 (ElementalbladeSkills.tint) 은 그 원소의 묶음
            case "priest", "saint" -> HOLY; // 2026-11-16 사제 라인 (tools/jobvfx/holy.py)
            case "warlock" -> HEX; // 2026-11-16 (tools/jobvfx/hex.py)
            case "guardian" -> WARD; // 2026-11-16 수호기사 (tools/jobvfx/ward.py)
            case "darkknight" -> MAW; // 2026-11-16 다크 나이트 (tools/jobvfx/maw.py)
            case "paladin", "thunderlord" -> THUNDER; // 2026-11-16 성기사 라인 (tools/jobvfx/thunder.py)
            case "tyrant" -> TYRANT;
            case "reaper" -> REAP; // 2026-11-16 사신 라인 (tools/jobvfx/reap.py)
            case "soulreaper" -> SOULREAP;
            case "executioner" -> EXEC;
            case "lancer" -> LANCE; // 2026-11-16 창술사 라인 (tools/jobvfx/lance.py)
            case "stormlancer" -> GALE;
            case "dragoon" -> DRAGONL;
            case "phoenix" -> PHOENIXL; // 2026-11-16 (tools/jobvfx/phoenix.py)
            default -> null;
        };
    }

    /** 원소 공용 묶음으로 그리는 직업 (JobFx 공통 함수 · JobImpact 가 이 묶음으로 갈라짐 — 팬텀 아처 줄기는 PhantomArcher 길이라 뺌) */
    static Lib elem(Jobs.Job j) {
        return j == null || PhantomArcher.family(j) ? null : lib(j);
    }

    /**
     * 2026-11-16 원소 빛 → 묶음 (엘리멘탈 블레이드가 원소마다 물들인 직업 · 원소 빛으로 그린 예전 rfx): 물 · 얼음 = 서리 (푸른 얼음빛), 바위 = 곰 (흙 · 호박빛), 불 · 전기 ·
     * 직업 빛 = 원소의 칼 (금빛 주황). 원소 빛 배열은 규칙을 다시 읽으면 바뀌므로 그때그때 비교
     */
    static Lib elementLib(double[] glow) {
        for (String id : mmorpg.core.Elements.order()) {
            mmorpg.core.Elements.Element e = mmorpg.core.Elements.get(id);
            if (e == null || (e.glow() != glow && e.core() != glow)) continue;
            return switch (id) {
                case "water", "ice" -> FROST;
                case "rock" -> BEAR;
                default -> ELEMENT;
            };
        }
        return ELEMENT;
    }

    /** 원소 빛이면 그 원소의 묶음 (아니면 null) */
    private static Lib elementOf(double[] glow) {
        for (String id : mmorpg.core.Elements.order()) {
            mmorpg.core.Elements.Element e = mmorpg.core.Elements.get(id);
            if (e != null && (e.glow() == glow || e.core() == glow)) return elementLib(glow);
        }
        return null;
    }

    /** 이 묶음에 그림판이 있는지 (조각 그림은 이름_00) */
    static boolean has(Lib L, String name) {
        return !Vfx.sheetNames(L.n(name)).isEmpty();
    }

    /**
     * 2026-11-17 이 묶음에 그 이름의 그림판이 꼭 그대로 있는지 (has 는 앞이 같은 이름도 셈 — 긴 일섬 cutl 이 생긴 뒤 has(L, "cut") 가 cutl 만 있는 묶음 (분노 · 곰 ·
     * 서리 · 피) 에서도 참이 되어 없는 cut 그림판을 찾았음)
     */
    static boolean hasArt(Lib L, String name) {
        return Vfx.hasSheet(L.n(name));
    }

    /**
     * 2026-11-16 가까운 묶음 (이 묶음에 없는 대표 그림을 빌림 — 같은 계열 · 비슷한 빛): 트릭스터 환영 → 암살자 그림자 (꽃잎 · 엇갈린 베기 · 검은 장미 · 찌르기 · 비수 ·
     * 연기 — CI 43872303193 트릭스터 영상에 예전 rfx_jpetal2 234 장이 남았었음). 없으면 null
     */
    static Lib kin(Lib L) {
        return L == ILLUSION ? SHADOW : null;
    }

    /** 이 묶음 (없으면 가까운 묶음) 에서 그림 name 이 있는 묶음 — 없으면 null */
    static Lib holder(Lib L, String name) {
        if (L == null) return null;
        if (has(L, name)) return L;
        Lib K = kin(L);
        return K != null && has(K, name) ? K : null;
    }

    // ------------------------------------------------------------ 보는 사람 (JobFx 의 view · owner 값)

    /** JobFx 보는 사람 값 (view 1 = 주인이 1인칭이면 숨김 · 2 = 주인의 1인칭에만, owner = 주인 id) → 걸러내기 (null = 모두) */
    static Predicate<ServerPlayer> who(Opt o) {
        int v = o == null ? 0 : (int) o.num("view", 0);
        if (v == 0) return null;
        int owner = (int) o.num("owner", 0);
        return v == 1 ? p -> !(p.getId() == owner && !PBridge.thirdPersonOf(p)) : p -> p.getId() == owner && !PBridge.thirdPersonOf(p);
    }

    /** 주인이 1인칭이면 숨김 (몸 앞 판 · 몸 둘레 그림) */
    static Predicate<ServerPlayer> hide1p(ServerPlayer sp) {
        int id = sp.getId();
        return p -> !(p.getId() == id && !PBridge.thirdPersonOf(p));
    }

    /** 주인의 1인칭에만 (눈앞에 따로 그리는 그림) */
    static Predicate<ServerPlayer> only1p(ServerPlayer sp) {
        int id = sp.getId();
        return p -> p.getId() == id && !PBridge.thirdPersonOf(p);
    }

    /**
     * 2026-11-15 눈 바로 앞 그림 거름 (1인칭 영상 2 — 블러드 문 · 새비지 프렌지 · 엑스칼리버): 그림 한 장은 256 픽셀이라 눈에서 가까우면 한 픽셀이 화면에서 수십 픽셀로
     * 늘어나 붉은 · 금빛 네모 덩어리로 화면을 덮었음 → 1인칭으로 보는 사람의 눈이 at 에서 r 칸 안이면 보내지 않음 (3인칭은 카메라가 몸 뒤라 그대로 — 쓴 사람의 눈앞에
     * 보여야 할 그림은 스킬이 1인칭 자리에 따로 둠)
     */
    static Predicate<ServerPlayer> farEye(Predicate<ServerPlayer> who, Vec3 at, double r) {
        if (r <= 0) return who;
        double r2 = r * r;
        Predicate<ServerPlayer> far = p -> PBridge.thirdPersonOf(p) || p.getEyePosition().distanceToSqr(at) >= r2;
        return who == null ? far : who.and(far);
    }

    /**
     * 눈앞 거름 반지름 — 한 장 그림 (256 픽셀) 크기 size 의 몇 배 (1080p · 시야 70도에서 한 픽셀 ≈ 3.45 × size / 거리 픽셀): 날이 선 피 그림 (물보라 가시 · 방울) 은
     * 약 9 픽셀 넘게 늘어나면 (0.38 — 새비지 프렌지 2.5칸 앞 맞은 자리 피 튐 6 픽셀은 그대로) · 부드러운 빛 (강철 · 금 · 불 섬광) 은 아주 가까울 때만 (0.25 — 엑스칼리버
     * 발 앞 큰 섬광은 그대로). 2 × 2 그림은 조각이 반 크기라 이 값의 반
     */
    static double nearR(Lib L, double size) {
        return size * (L == BLOOD ? 0.38 : 0.25);
    }

    /** 땅에 눕힌 그림 (그림 앞면이 위 — 그림 위쪽이 yaw 쪽) */
    static Quaternionf flat(double yawDeg) {
        return Vfx.flat(yawDeg);
    }

    // ------------------------------------------------------------ 바탕

    /**
     * 큰 그림 하나 (조각 k × k — 그림판 base_rc): 가운데 at · 한 변 size 칸 · facing (null 이면 카메라를 봄 · 아니면 그 방향에 고정) · 화면 돌림 roll 도 · from 틱에
     * 나타나 every 틱마다 칸을 넘기고 마지막 칸 뒤 사라짐
     */
    static Vfx.Fx tiled(ServerLevel level, String base, int k, Vec3 at, double size, Quaternionf facing, double roll, int from, int every) {
        return tiled(level, base, k, at, size, facing, roll, from, every, false);
    }

    /**
     * 위와 같고 twoSided 면 고정 그림 (facing) 의 뒷면도: 조각마다 거울 ({@link Vfx.Sprite#mirror}) 한 장 더 — 자리 (r, c) 에 조각 (r, k−1−c) 를 거울로 놓으면 그림 전체의
     * 거울이 됨, 미리 뒤집은 그림판 (base + "m") 이 있으면 그것을 써서 뒷면도 세계에서 같은 그림 (휘두르는 쪽이 칼과 같음)
     */
    static Vfx.Fx tiled(ServerLevel level, String base, int k, Vec3 at, double size, Quaternionf facing, double roll, int from, int every, boolean twoSided) {
        return tiled(level, base, k, at, size, facing, roll, from, every, twoSided, null, null, 1.0);
    }

    /**
     * 2026-11-15 같은 것 — 뒷면 그림판 back (null = base + "m" 이 있으면 그것 · 없으면 base: 앞면이 미리 뒤집은 slashm 이면 뒷면은 slash 라야 세계에서 같은 그림) ·
     * 볼 사람 who (null = 모두 — 붙이기 전에 정함) · 불투명 alpha
     */
    static Vfx.Fx tiled(ServerLevel level, String base, int k, Vec3 at, double size, Quaternionf facing, double roll, int from, int every, boolean twoSided,
            String back, Predicate<ServerPlayer> who, double alpha) {
        Vfx.Sheet s0 = Vfx.sheet(base + "_00");
        int frames = s0.frames();
        int life = from + frames * every + 4;
        Vfx.Fx fx = Vfx.at(level, at, life, who);
        double t = size / k;
        int sides = twoSided && facing != null ? 2 : 1;
        String backBase = back != null ? back : Vfx.sheetNames(base + "m_").isEmpty() ? base : base + "m";
        fx.on(Math.max(0, from), () -> {
            for (int r = 0; r < k; r++) {
                for (int c = 0; c < k; c++) {
                    for (int side = 0; side < sides; side++) {
                        Vfx.Sprite sp = fx.sprite(side == 0 ? base + "_" + r + c : backBase + "_" + r + (k - 1 - c), at);
                        if (facing != null) sp.fixed(facing);
                        if (side == 1) sp.mirror();
                        if (roll != 0) sp.roll(roll, 0);
                        if (alpha < 0.999) sp.alpha(alpha);
                        sp.size(t);
                        sp.offset((c - (k - 1) / 2.0) * t, ((k - 1) / 2.0 - r) * t);
                        sp.play(fx.age(), 0, frames, every, false);
                    }
                }
            }
        });
        return fx;
    }

    /** 그림 위쪽 ey · 앞면 ez 의 고정 방향 (ex = ey × ez) — ey 가 ez 와 나란하면 다른 위 */
    static Quaternionf face(Vec3 ez, Vec3 upHint) {
        Vec3 z = V.unit(ez);
        Vec3 u = V.sub(upHint, V.scale(z, V.dot(upHint, z)));
        if (V.len(u) < 1e-3) {
            Vec3 alt = Math.abs(z.y) > 0.9 ? new Vec3(0, 0, -1) : V.UP;
            u = V.sub(alt, V.scale(z, V.dot(alt, z)));
        }
        Vec3 y = V.unit(u);
        Vec3 x = V.cross(y, z);
        return Vfx.basis(x, y, z);
    }

    /** 한 장 그림 (그림판 name — 칸이 여럿이면 every 틱마다 넘김): 가운데 at · 크기 size 칸 · loop 면 되풀이 */
    static Vfx.Sprite single(Vfx.Fx fx, String name, Vec3 at, double size, double roll, int every, boolean loop) {
        Vfx.Sheet s = Vfx.sheet(name);
        Vfx.Sprite sp = fx.sprite(name, at);
        sp.size(size);
        if (roll != 0) sp.roll(roll, 0);
        if (s.frames() > 1) sp.play(fx.age(), 0, s.frames(), every, loop);
        return sp;
    }

    /** 땅 자리 (발밑 블록 위 0.04칸) */
    static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, p, p.y) + 0.04, p.z);
    }

    // ------------------------------------------------------------ 빛 · 섬광

    /** 섬광 (pb_flash → 묶음 flash — 흰 별 모양 · 굵은 빛살 · 퍼지는 고리): 가운데 at · 크기 size 칸 */
    static void flash(ServerLevel level, Vec3 at, double size) {
        flash(level, PHANTOM, at, size);
    }

    static void flash(ServerLevel level, Lib L, Vec3 at, double size) {
        flash(level, L, at, size, null);
    }

    /** 모으는 빛 (되풀이 — 스킬이 to · remove 로 다룸): fx 안 · 가운데 at · 크기 size */
    static Vfx.Sprite charge(Vfx.Fx fx, Lib L, Vec3 at, double size) {
        return charge(fx, L, at, size, true);
    }

    /**
     * 같은 것 — wyrd 면 2026-11-16 위르드식 바닐라 겹 (WyrdKit.chargeTick: 2틱마다 둘레에서 빨려 드는 묶음 알갱이 · 줄어드는 빛 점 고리) 이 그림을 따라감 (스킬이 그림을
     * 옮기고 키움 · 지우면 그침 — 효과 수명까지). 팬텀 아처 줄기는 이 그림을 직접 부름, JobFx.chargeLib 은 제 겹이 있어 false
     */
    static Vfx.Sprite charge(Vfx.Fx fx, Lib L, Vec3 at, double size, boolean wyrd) {
        Vfx.Sprite sp = single(fx, L.n("charge"), at, size, 0, 1, true);
        if (wyrd && WyrdKit.ON) {
            int a0 = fx.age();
            int end = fx.life() - 1;
            if (end > a0 + 1) {
                fx.each(a0 + 1, end, 2, age -> {
                    if (sp.gone()) return;
                    double q = Math.min(1, (age - a0) / (double) Math.max(1, end - a0));
                    WyrdKit.chargeTick(fx.level(), L, sp.pos(), q, Math.max(0.4, sp.sizeNow() * 0.5), null);
                });
            }
        }
        return sp;
    }

    // ------------------------------------------------------------ 날아가는 작은 그림

    /**
     * 불씨 n 개 (묶음 ember — 둥근 흰 금빛 심 · 주황 · 진홍 겉빛, 네 칸 깜박임. 1판 길쭉한 불티는 카메라를 보는 그림이라 늘 세로로 서서 촛불처럼 보였음):
     * 가운데 c 에서 사방 (위로 쏠림) 으로 dist 칸 솟았다가 떨어지며 작아지고 옅어짐 (life 틱)
     */
    static void embers(ServerLevel level, Vec3 c, int n, double dist, double size, int life) {
        embers(level, PHANTOM, c, n, dist, size, life);
    }

    static void embers(ServerLevel level, Lib L, Vec3 c, int n, double dist, double size, int life) {
        fly(level, c, n, dist, size, life, L.n(L.ember()), 1.0);
        if (WyrdKit.direct()) WyrdKit.embers(level, L, c, n, dist); // 2026-11-16 위르드식 바닐라 파티클 겹 (스킬이 직접 부를 때)
    }

    /** 칼 조각 n 개 (묶음 shard — 납작한 강철 조각이 구르는 여덟 칸 · 깨진 가장자리가 달아오름): 불씨보다 낮고 멀리 · 빨리 떨어짐 */
    static void shards(ServerLevel level, Vec3 c, int n, double dist, double size, int life) {
        shards(level, PHANTOM, c, n, dist, size, life);
    }

    static void shards(ServerLevel level, Lib L, Vec3 c, int n, double dist, double size, int life) {
        fly(level, c, n, dist, size, life, L.n("shard"), 0.55);
    }

    /**
     * 2026-11-16 위르드식 튀는 돌 (예전 RuneKit.rockBurst — 칠한 예전 돌덩이 rpg:boulder 대신 곰 묶음의 도트 조각 (흙 · 호박빛 면이 구르는 여덟 칸)): 예전 수의 8할 ·
     * 거리 ≈ 예전 속도 × 0.4 칸 · 크기 = 예전 반 크기 × 2.2 (불씨 · 바닐라 겹 없이 — 돌은 타지 않음)
     */
    static void rocks(ServerLevel level, Vec3 at, int n, double speed, double size) {
        if (!has(BEAR, "shard")) {
            RuneKit.rockBurst(level, at, n, speed, size);
            return;
        }
        shards(level, BEAR, at, Math.max(1, (int) Math.round(n * 0.8)), Math.max(0.6, speed * 0.4), Math.max(0.22, size * 2.2), 16);
    }

    /** 날아가는 작은 그림 (불씨 · 조각): 솟음 → 둥글게 넘어감 → 떨어짐 (세 토막 보간) · 칸은 되풀이 (불씨는 깜박임 · 조각은 구름) */
    private static void fly(ServerLevel level, Vec3 c, int n, double dist, double size, int life, String sheet, double lift) {
        if (n <= 0) return;
        int L = Math.max(8, life);
        Vfx.Fx fx = Vfx.at(level, c, L + 4);
        Vfx.Sheet sh = Vfx.sheet(sheet);
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double up = (0.2 + Kit.rand() * 0.9) * lift;
            Vec3 d = V.unit(new Vec3(Math.cos(a), up, Math.sin(a)));
            double dd = dist * (0.5 + Kit.rand() * 0.65);
            double sz = size * (0.6 + Kit.rand() * 0.7);
            Vec3 p1 = V.add(c, d, dd * 0.55);
            Vec3 p2 = V.up(V.add(c, V.flat(d), dd * 0.9), dd * 0.3 * up - 0.15);
            Vec3 p3 = V.up(V.add(c, V.flat(d), dd * 1.12), -dd * 0.4 - 0.4);
            Vfx.Sprite s = fx.sprite(sheet, c);
            s.size(sz * 0.4);
            if (sh.frames() > 1) s.play(0, (int) (Kit.rand() * sh.frames()), sh.frames(), 1 + (int) (Kit.rand() * 2), true);
            int t1 = 2 + (int) (Kit.rand() * 2);
            int t2 = Math.max(3, L / 3);
            fx.on(1, () -> s.to(p1, sz, t1));
            fx.on(1 + t1, () -> s.to(p2, sz * 0.8, t2));
            fx.on(1 + t1 + t2, () -> s.to(p3, sz * 0.35, Math.max(2, L - t1 - t2 - 1)));
            s.fade(L - 6, 6);
        }
    }

    // ------------------------------------------------------------ 맞음 · 폭발 · 땅

    /** 착탄 (예전 impact2d — BlockFx 상자 터짐 · 상자 조각 대신): 섬광 · 불씨 · 칼 조각 몇 · 크기 size 칸 */
    static void impact(ServerLevel level, Lib L, Vec3 at, double size) {
        WyrdKit.enter(); // 안의 섬광 · 불씨에 바닐라 겹을 다시 얹지 않음
        try {
            WyrdKit.burst(level, L, at, size * 1.3, null); // 2026-11-16 위르드식 바닐라 파티클 겹
            flash(level, L, at, size * 2.2);
            embers(level, L, at, 5 + (int) (size * 6), size * 1.6, 0.32 + size * 0.12, 14);
            if (size >= 0.9) shards(level, L, at, 2 + (int) (size * 2), size * 1.4, 0.32, 14);
        } finally {
            WyrdKit.leave();
        }
    }

    /**
     * 작은 폭발 (한 장 256 — boom_s · shock_s · crack_s: 꽂힌 칼 특성 · 블레이드 레인 칼마다): 가운데 c (몸 높이) · 크기 size 칸 (불덩이 지름) · 불씨 · 조각
     */
    static void boomSmall(ServerLevel level, Lib L, Vec3 c, double size) {
        WyrdKit.enter(); // 안의 섬광 · 불씨에 바닐라 겹을 다시 얹지 않음
        try {
            Vec3 g = ground(level, c);
            Vfx.Fx fx = Vfx.at(level, c, 20);
            single(fx, L.n("boom_s"), V.up(c, size * 0.15), size, Kit.rand() * 30 - 15, 1, false);
            WyrdKit.burst(level, L, V.up(c, size * 0.15), size, null); // 2026-11-16 위르드식 바닐라 파티클 겹
            if (c.y - g.y < 2.5) {
                WyrdKit.shock(level, L, g, size * 1.6, null);
                Vfx.Fx gx = Vfx.at(level, g, 18);
                Vfx.Sprite sh = single(gx, L.n("shock_s"), V.up(g, 0.02), size * 1.6, 0, 1, false);
                sh.fixed(flat(Kit.rand() * 360));
                Vfx.Fx cx = Vfx.at(level, g, 40);
                Vfx.Sprite cr = cx.sprite(L.n("crack_s"), V.up(g, 0.01));
                cr.fixed(flat(Kit.rand() * 360));
                cr.size(size * 0.9);
                cr.play(2, 0, Vfx.sheet(L.n("crack_s")).frames(), 6, false);
            }
            embers(level, L, c, 8 + (int) (size * 3), size * 0.9, 0.42, 18);
            shards(level, L, c, 3 + (int) size, size, 0.4, 16);
        } finally {
            WyrdKit.leave();
        }
    }

    /**
     * 큰 폭발 (2 × 2 — boom · shock · crack: 팬텀 버스트 · 블레이드 레인 끝 · 소드 생추어리 끝): 가운데 c (몸 높이) · 반지름 rad 칸 — 불덩이 rad × 1.9 · 땅 충격파
     * rad × 3.2 · 그을린 땅 rad × 1.7 · 불씨 · 칼 조각
     */
    static void boom(ServerLevel level, Lib L, Vec3 c, double rad) {
        boom(level, L, c, rad, null);
    }

    /**
     * 같은 것 — 불덩이를 볼 사람 who (null = 모두). 2026-11-16 도트 그림 (라이징 엣지 · 블레이드 이클립스 1인칭 영상): 1인칭 눈이 불덩이 그림 한 변의 0.6 안이면
     * (카메라를 보는 그림이 세로 시야 70도를 넘게 덮음) 안쪽 도트 칠이 화면 전체를 한 빛 (살구빛) 으로 덮었음 → 그 사람에게는 불덩이를 보내지 않음 (충격파 · 땅 자국 ·
     * 불씨는 그대로)
     */
    static void boom(ServerLevel level, Lib L, Vec3 c, double rad, Predicate<ServerPlayer> who) {
        WyrdKit.enter(); // 안의 섬광 · 불씨에 바닐라 겹을 다시 얹지 않음
        try {
            Vec3 gc = ground(level, c);
            Vec3 ball = V.up(c, 0.9);
            tiled(level, L.n("boom"), 2, ball, rad * 1.9, null, Kit.rand() * 30 - 15, 0, 1, false, null, farEye(who, ball, rad * 1.9 * 0.6), 1.0);
            // 2026-11-15 영상 (팬텀 버스트 3인칭 · 1인칭): 지름 3.2 × rad 는 고리 앞 날이 카메라 밑까지 와서 화면 아래를 흰 띠로 가로질렀음 → 2.5 × rad
            tiled(level, L.n("shock"), 2, V.up(gc, 0.02), rad * 2.5, flat(Kit.rand() * 360), 0, 0, 1);
            WyrdKit.burst(level, L, V.up(c, 0.9), rad * 1.9, null); // 2026-11-16 위르드식 바닐라 파티클 겹
            WyrdKit.shock(level, L, gc, rad * 2.5, null);
            if (has(L, "crack_00")) tiled(level, L.n("crack"), 2, gc, rad * 1.7, flat(Kit.rand() * 360), 0, 2, 7);
            else crackS(level, L, gc, rad * 1.7); // 원소 공용 묶음은 한 장 땅 자국만
            embers(level, L, V.up(c, 0.4), 26, rad * 1.1, 0.55, 26);
            shards(level, L, V.up(c, 0.3), 12, rad * 1.2, 0.5, 22);
        } finally {
            WyrdKit.leave();
        }
    }

    /** 땅 충격파만 (2 × 2 — 속이 빈 고리가 퍼짐): 땅 g · 지름 size 칸 · every 틱마다 한 칸 */
    static void shock(ServerLevel level, Lib L, Vec3 g, double size, int every) {
        shock(level, L, g, size, every, null);
    }

    /**
     * 같은 것 — 볼 사람 who (null = 모두). 2026-11-16 블레이드 이클립스 1인칭 영상: 가슴 높이에 눕힌 충격파는 눈 (0.5칸 위) 에서 보면 고리 안쪽 도트 칠이 화면 아래
     * 절반을 덮었음 → 스킬이 주인의 1인칭을 뺄 때
     */
    static void shock(ServerLevel level, Lib L, Vec3 g, double size, int every, Predicate<ServerPlayer> who) {
        tiled(level, L.n("shock"), 2, V.up(g, 0.02), size, flat(Kit.rand() * 360), 0, 0, Math.max(1, every), false, null, who, 1.0);
        WyrdKit.shock(level, L, g, size, who); // 2026-11-16 위르드식 바닐라 파티클 겹
    }

    /** 그을린 땅 (2 × 2 — 달아오른 금이 식음): 땅 g · 지름 size 칸 · from 틱부터 every 틱마다 */
    static void crack(ServerLevel level, Lib L, Vec3 g, double size, int from, int every) {
        tiled(level, L.n("crack"), 2, V.up(g, 0.01), size, flat(Kit.rand() * 360), 0, from, Math.max(1, every));
    }

    /**
     * 땅 고리 (2 × 2 ring — 예고 · 테두리, 반짝임 되풀이): 가운데 c (땅) · 반지름 r 칸 — grow 틱에 작게서 커지고 hold 틱 머문 뒤 4틱에 옅어짐. 반환 = 효과
     */
    static Vfx.Fx ring(ServerLevel level, Lib L, Vec3 c, double r, int grow, int hold) {
        int G = Math.max(1, grow), Hh = Math.max(1, hold);
        Vfx.Fx fx = Vfx.at(level, c, G + Hh + 8);
        double size = r * 2.0 / 0.9; // 그림의 고리는 반지름 0.9 (테 가운데)
        double t = size / 2;
        Quaternionf q = flat(Kit.rand() * 360);
        String base = L.n("ring");
        int frames = Vfx.sheet(base + "_00").frames();
        for (int rr = 0; rr < 2; rr++) {
            for (int cc = 0; cc < 2; cc++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + rr + cc, V.up(c, 0.03));
                sp.fixed(q);
                double ox = (cc - 0.5) * t;
                double oy = (0.5 - rr) * t;
                sp.size(t * 0.15);
                sp.offset(ox * 0.15, oy * 0.15);
                sp.play(0, 0, frames, 3, true);
                fx.on(1, () -> {
                    sp.offset(ox, oy);
                    sp.to(null, t, G);
                });
                sp.fade(G + Hh, 4);
            }
        }
        return fx;
    }

    /** 칼이 땅에 꽂힘 · 솟음 (dust — 흙먼지 · 밑의 진홍 번쩍): 땅 g · 크기 size 칸 (그림의 땅 줄이 아래 1/4 이라 그만큼 올림) */
    static void dust(ServerLevel level, Lib L, Vec3 g, double size) {
        Vfx.Fx fx = Vfx.at(level, g, 14);
        single(fx, L.n("dust"), V.up(g, size * 0.25), size, 0, 1, false);
        if (WyrdKit.direct()) WyrdKit.groundPuff(level, L, g, size); // 2026-11-16 위르드식 바닐라 파티클 겹 (스킬이 직접 부를 때)
    }

    /** X 베기 (2 × 2 xslash — 두 줄기가 차례로 그어지고 교차점 섬광): 가운데 c · 크기 size 칸 · 화면 돌림 roll 도 (카메라를 봄) */
    static void xslash(ServerLevel level, Lib L, Vec3 c, double size, double roll) {
        tiled(level, L.n("xslash"), 2, c, size, null, roll, 0, 1, false, null, farEye(null, c, nearR(L, size) * 0.5), 1.0);
    }

    // ------------------------------------------------------------ 2026-11-15 아스널 킹 · 블레이드 레인저

    /**
     * 금빛 문 (ak_gate — 녹은 금 판의 물결 고리 · 금 테 · 보석, 되풀이 8칸 · 2틱마다): 가운데 c · 보물이 나오는 쪽 facing (앞면 — 뒷면도 한 장이라 어느 쪽에서도 보임) ·
     * 테 반지름 r 칸. open 틱에 작게서 열리고 (가운데 섬광) hold 틱 머문 뒤 4틱에 오므라들며 옅어짐. 반환 = 효과
     */
    static Vfx.Fx gate(ServerLevel level, Lib L, Vec3 c, Vec3 facing, double r, int open, int hold) {
        Vec3 f = V.len(facing) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(facing);
        Quaternionf q = face(f, V.UP);
        int G = Math.max(1, open);
        int Hh = Math.max(1, hold);
        Vfx.Fx fx = Vfx.at(level, c, G + Hh + 8);
        String name = L.n("gate");
        int frames = Vfx.sheet(name).frames();
        double side = r * 2.0 / 0.8; // 그림의 테는 반지름 0.8 (그림 한 변 = 2)
        int start = (int) (Kit.rand() * frames);
        for (int b = 0; b < 2; b++) {
            Vfx.Sprite sp = fx.sprite(name, c);
            sp.fixed(q);
            if (b == 1) sp.mirror(); // 뒷면 (문 그림은 거의 좌우 같음)
            sp.size(side * 0.12);
            sp.play(0, start, frames, 2, true);
            fx.on(1, () -> sp.to(null, side, G));
            fx.on(1 + G + Hh, () -> sp.to(null, side * 0.25, 4));
            sp.fade(1 + G + Hh, 4);
        }
        fx.on(1 + G / 2, () -> flash(level, L, V.add(c, f, 0.05), r * 1.5));
        WyrdKit.gate(level, L, c, f, r, G + Hh); // 2026-11-16 위르드식 바닐라 파티클 겹
        return fx;
    }

    /**
     * 단조 베기 (br_slash 2 × 2 — 달아오른 초승달이 휘둘러지고 꼬리부터 식으며 거둬짐 · 불티): 휘두르는 가운데 c (몸) · 볼록한 쪽 bulge (칼이 지나는 앞) · 휘두르는 쪽
     * sweep (그림 왼쪽 → 오른쪽 = sweep 쪽) · 반지름 r 칸 — 휘두른 면에 고정 · 앞 · 뒤 두 장. 그림의 호는 가운데 (0, −0.4) · 반지름 0.9 (그림 한 변 = 2)
     */
    static void slash(ServerLevel level, Lib L, Vec3 c, Vec3 bulge, Vec3 sweep, double r) {
        Vec3 ey = V.unit(bulge);
        Vec3 ex = V.sub(sweep, V.scale(ey, V.dot(sweep, ey)));
        if (V.len(ex) < 1e-3) ex = V.rightOf(V.flat(ey));
        ex = V.unit(ex);
        Vec3 ez = V.cross(ex, ey);
        Quaternionf q = Vfx.basis(ex, ey, ez);
        double side = r * 2.0 / 0.9;
        tiled(level, L.n("slash"), 2, V.add(c, ey, r * 0.4 / 0.9), side, q, 0, 0, 1, true);
    }

    // ------------------------------------------------------------ 불꽃 (세로로 서는 그림)

    /**
     * 불꽃 혀 하나 (flame — 솟았다 사그라드는 10칸, 세로축 빌보드): 발 자리 foot · 높이 h 칸 · every 틱마다 한 칸 (loop 면 끝까지 되풀이 — 효과 life 틱)
     */
    static Vfx.Sprite flame(Vfx.Fx fx, Lib L, Vec3 foot, double h, int every, boolean loop, int startFrame) {
        String name = L.n("flame");
        Vfx.Sheet s = Vfx.sheet(name);
        Vfx.Sprite sp = fx.sprite(name, V.up(foot, h * 0.5));
        sp.vertical();
        sp.size(h);
        sp.frame(startFrame);
        sp.play(fx.age(), startFrame, s.frames(), Math.max(1, every), loop);
        return sp;
    }

    /** 불꽃 하나 따로 (한 번 솟았다 사그라듦): 발 자리 foot · 높이 h · every 틱마다 */
    static void flame(ServerLevel level, Lib L, Vec3 foot, double h, int every) {
        int frames = Vfx.sheet(L.n("flame")).frames();
        Vfx.Fx fx = Vfx.at(level, foot, frames * Math.max(1, every) + 4);
        flame(fx, L, foot, h, every, false, 0);
    }

    // ------------------------------------------------------------ 2026-11-15 원소 공용 묶음 — JobFx 공통 함수 · 검사 라인 스킬이 부름

    /**
     * 그림 속 칼빛 초승달이 그림 한 변에서 차지하는 너비 (tools/jobvfx/element.py — 호 반지름 0.9 · 그림 반 변 1): 칼빛 너비 w 칸이면 그림 한 변 = w / SLASH_FILL
     */
    static final double SLASH_FILL = 0.9;

    /** 섬광 — 볼 사람을 정함 (1인칭 눈 바로 앞이면 거름 — {@link #farEye}) */
    static void flash(ServerLevel level, Lib L, Vec3 at, double size, Predicate<ServerPlayer> who) {
        Vfx.Fx fx = Vfx.at(level, at, 12, farEye(who, at, nearR(L, size)));
        single(fx, L.n("flash"), at, size, Kit.rand() * 40 - 20, 1, false);
        if (WyrdKit.direct()) WyrdKit.flash(level, L, at, size, who); // 2026-11-16 위르드식 바닐라 파티클 겹 (스킬이 직접 부를 때)
    }

    /**
     * 1인칭 눈앞 큰 베기 (주인의 1인칭에만 — 카메라를 봄): 가운데 at · 칼빛 너비 width 칸 · 기울기 tilt (JobFx.slash 와 같은 뜻 — 0 = 왼쪽에서 아래로 불룩하게 오른쪽으로 ·
     * 180 = 위로 불룩하게 오른쪽에서 왼쪽으로 · 95 = 위에서 아래로 내리침 · 275 = 아래에서 위로): 미리 뒤집은 그림판 (slashm — 위로 불룩 · 오른쪽 → 왼쪽 = 예전 판 모델
     * slash_job 의 그림) 을 180 + tilt 돌림 (화면에서 반시계 — 예전 rfx_jslash 의 spin · JobFx.slashCard FRONT 의 불룩한 날 = 화면 아래를 tilt 도 반시계로 돌린 쪽과 같음).
     * 2026-11-15 사용자 "이전은 참격이 앞을 잘보고 있는데 왜 새로 만든건 위를 보고 있고 아래를보고 있고 옆을 보고 있고" — 180 − tilt 는 예전의 좌우 거울이었음
     * (기울기 95 내리침이 오른쪽 대신 왼쪽으로 불룩 · 200 이 왼쪽 위 대신 오른쪽 위로)
     */
    static Vfx.Fx eyeSlash(ServerPlayer sp, Lib L, Vec3 at, double width, double tilt) {
        return eyeSlash(sp, L, at, width, tilt, 1.0);
    }

    /** 같은 것 — 불투명 alpha (옅은 잔상 겹) */
    static Vfx.Fx eyeSlash(ServerPlayer sp, Lib L, Vec3 at, double width, double tilt, double alpha) {
        return tiled(sp.level(), L.n("slashm"), 2, at, width / SLASH_FILL, null, 180 + tilt, 0, 1, false, null, only1p(sp), alpha);
    }

    /**
     * 2026-11-15 카메라를 보는 칼빛 초승달 (JobFx.slash · slashCard — 1인칭 · 3인칭 · 다른 사람 모두): 가운데 at · 칼빛 너비 width 칸 · 기울기 tilt (eyeSlash 와 같은 뜻).
     * 첫 영상 (CI 43693784623) 에서 쓴 사람 뒤를 보게 고정한 판은 옆 · 앞에서 비스듬히 얇게 보였음 → 늘 보는 사람 쪽 (어느 쪽에서도 화면에서 같은 기울기)
     */
    static void slashBill(ServerLevel level, Lib L, Vec3 at, double width, double tilt, Predicate<ServerPlayer> who) {
        tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, null, 180 + tilt, 0, 1, false, null, who, 1.0);
    }

    /**
     * 맞은 자리 그림을 몸 앞으로 (때린 사람 쪽 — 몸 겉 너머): 몸 가운데의 카메라를 보는 그림은 몸에 가려 거의 안 보였음 (CI 43693784623 — X 베기 · 섬광). 때린 사람을 모르면 가장
     * 가까운 사람 쪽 · 둘 다 없으면 몸 가운데
     */
    static Vec3 front(LivingEntity e, Vec3 b) {
        LivingEntity by = e.getLastHurtByMob();
        Vec3 eye = by != null && by.distanceToSqr(e) < 400 ? by.getEyePosition() : null;
        if (eye == null && e.level() instanceof ServerLevel lv) {
            net.minecraft.world.entity.player.Player p = lv.getNearestPlayer(e, 16);
            if (p != null) eye = p.getEyePosition();
        }
        if (eye == null) return b;
        Vec3 d = V.sub(eye, b);
        double len = V.len(d);
        if (len < 0.6) return b;
        return V.add(b, V.scale(d, 1 / len), Math.min(len * 0.5, Kit.bodyPad(e) + 0.55));
    }

    /**
     * 발키리의 날개 (el_steel_wings 2 × 2 — 카메라를 봄 · 날갯짓 되풀이): 등 뒤 (몸 뒤 0.35 · 높이 1.3) 를 2틱마다 따라감 · 4틱에 커지며 나타나 ticks 틱 뒤 6틱에 옅어짐 ·
     * 주인 1인칭에서는 숨김. 그림판이 없으면 false (예전 길)
     */
    static boolean wings(ServerPlayer sp, Lib L, double size, int ticks, double alpha) {
        if (!has(L, "wings_")) return false;
        String base = L.n("wings");
        String life = RuneKit.lifeToken(sp);
        Vec3 at0 = wingAt(sp);
        int T = Math.max(8, ticks);
        Vfx.Fx fx = Vfx.at(sp.level(), at0, T + 10, hide1p(sp));
        int frames = Vfx.sheet(base + "_00").frames();
        double t = size * 2.4 / 2;
        java.util.List<Vfx.Sprite> tiles = new java.util.ArrayList<>();
        int start = (int) (Kit.rand() * frames);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                Vfx.Sprite w = fx.sprite(base + "_" + r + c, at0);
                double ox = (c - 0.5) * t;
                double oy = (0.5 - r) * t;
                w.size(t * 0.6);
                w.offset(ox * 0.6, oy * 0.6);
                if (alpha < 0.999) w.alpha(alpha);
                w.play(0, start, frames, 2, true);
                fx.on(1, () -> {
                    w.offset(ox, oy);
                    w.to(null, t, 4);
                });
                tiles.add(w);
            }
        }
        fx.each(2, T, 2, age -> {
            if (!RuneKit.sameLife(sp, life)) {
                fx.end();
                return;
            }
            Vec3 at = wingAt(sp);
            for (Vfx.Sprite w : tiles) w.to(at, -1, 2);
        });
        fx.on(T, () -> fx.fadeAll(6));
        return true;
    }

    private static Vec3 wingAt(ServerPlayer sp) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.3);
    }

    /** 화면 위쪽 축 (dir 에 수직 — dir 이 거의 위 · 아래면 북쪽) · 오른쪽 축 */
    private static Vec3[] screenAxes(Vec3 dir) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : new Vec3(0, 0, 1);
        Vec3 up = V.sub(V.UP, V.scale(d, V.dot(V.UP, d)));
        up = V.len(up) < 1e-3 ? new Vec3(0, 0, -1) : V.unit(up);
        Vec3 right = V.unit(V.cross(d, up));
        return new Vec3[] {d, up, right};
    }

    /**
     * 몸 앞 참격 판 (세상에 고정 · 앞뒤 두 면 — 3인칭 · 다른 사람): 가운데 at · 앞면이 dir 의 반대 (쓴 사람 뒤에서 보면 앞면 — eyeSlash 와 같은 모습이 같은 자리에) ·
     * 기울기 tilt (JobFx.slash 의 뜻) · 칼빛 너비 width 칸. 앞면 slashm · 뒷면 slashm 의 거울 (예전 모드 없는 클라이언트 판과 같은 모습 — 아래 card 참고)
     */
    static void card(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who) {
        card(level, L, at, dir, tilt, width, who, 1.0, 1);
    }

    /**
     * 같은 것 — 불투명 alpha · 칸 넘김 every 틱 (날아가는 판은 수명에 맞춤). 반환 = 효과 (날아가는 판은 shift). 2026-11-15 사용자 "참격이 앞을 바라봐야 하는데 위를
     * 보는데?" — 카메라를 보는 판 (slashBill) 은 높은 3인칭 · 영상 카메라 쪽으로 뒤로 누워 위를 보는 것처럼 보였음 → 3인칭 · 다른 사람은 다시 이 앞을 보는 판
     * (2026-11-14 사용자 메모 "참격이 앞을 안보고 있음" 때 정한 FRONT)
     */
    static Vfx.Fx card(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who, double alpha, int every) {
        Vec3[] ax = screenAxes(dir);
        Quaternionf q = Vfx.basis(ax[2], ax[1], V.scale(ax[0], -1));
        // 돌림 = tilt (2026-11-15 셋째 고침 — 예전 영상 CI 43560411558 (81ca2389) 과 장면마다 맞춤): 예전 모드 없는 클라이언트의 판 (PBridge.Card) 은 판 모델
        // slash_job 을 글자로 구울 때 (tools/vfx_particles.py card_layers) 모델의 180° 돌림을 버리고 rfx_jslash2 그림을 그대로 (아래로 불룩 · 왼쪽 → 오른쪽) 판
        // (X = Y × Z · Y = 불룩한 날 −위 · cos(tilt) + 오른쪽 · sin(tilt) · Z = dir 의 반대) 에 붙였음 → 화면에 보인 불룩한 날은 그 반대 (tilt 0 = 위로 불룩 ∩ ·
        // 172 = 아래로 불룩 ∪). rfx_jslash2 = slashm 을 180° 돌린 그림이라 slashm 을 tilt 만큼 돌리면 예전과 같음. 180 + tilt 는 예전과 위아래가 뒤집혔음
        // (블레이드 아포칼립스 첫 베기 · 블러드 클레이브 큰 판). 뒷면 = 같은 그림판 slashm 의 거울 (예전 판은 한 면 — 보는 사람 쪽으로 판 세로 축 둘레 반 바퀴 돌려
        // 그림이 늘 바로 읽혔음)
        return tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, q, tilt, 0, Math.max(1, every), true, L.n("slashm"), who, alpha);
    }

    /**
     * 같은 것 — 수명 life 초에 칸을 맞춤 (짧으면 한 틱에 한 칸) · 판이 vel (칸/초) 로 같이 움직임 (예전 판의 vel — 몸과 같이 움직이는 눈앞 판 · 패링 막기 판)
     */
    static Vfx.Fx card(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who, double alpha, double life, Vec3 vel) {
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        int every = Math.max(1, (int) Math.round(Math.max(1, life * 20) / frames));
        Vfx.Fx fx = card(level, L, at, dir, tilt, width, who, alpha, every);
        follow(fx, vel, frames * every + 1);
        return fx;
    }

    /** 효과의 그림을 모두 vel (칸/초) 로 ticks 틱 동안 옮김 (0 이면 그대로) */
    static void follow(Vfx.Fx fx, Vec3 vel, int ticks) {
        if (vel == null || V.len(vel) < 1e-3) return;
        fx.shift(V.scale(vel, ticks / 20.0), ticks);
    }

    /**
     * 날아가는 앞을 보는 참격 판 (JobFx.wave 의 3인칭 · 다른 사람 — 예전 SWING 판 대신): at 에서 dir 쪽으로 speed 칸/초 · life 초 · 칼빛 너비 width · 기울기 tilt
     * (JobFx.slash 와 같은 뜻 — 0 = 아래로 불룩) — 면이 dir 의 반대 (뒤 · 3인칭에서 넓은 면)
     */
    static void cardFly(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double speed, double width, double tilt, double life, Predicate<ServerPlayer> who,
            double alpha) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        int ticks = Math.max(2, (int) Math.round(life * 20));
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        Vfx.Fx fx = card(level, L, at, d, tilt, width, who, alpha, (int) Math.round(ticks / (double) frames));
        if (speed > 0) fx.shift(V.scale(d, speed * ticks / 20.0), ticks);
    }

    /**
     * 2026-11-15 날아가는 검기 판 — 예전 JobFx.wave 의 3인칭 판 (CardFace.SWING + RuneKit.faceCamIfFlat) 그대로 (사용자 "이전은 참격이 앞을 잘보고 있는데"): 불룩한 날 =
     * 날아가는 쪽 dir (그림 위쪽 Y), 면 = 위 (Z) 를 dir 둘레로 −tilt 돌림 — 다만 판이 누운 쪽이면 (cos tilt 의 크기 0.5 이상) 면이 보는 사람의 눈 (영상이면 영상 카메라 —
     * PBridge.eyeOf) 쪽 · 돌림 0 / 180. 그림 = slashm 을 판 안에서 반 바퀴 (예전 모드 없는 클라이언트 판의 rfx_jslash2 그대로 — 불룩한 날이 판의 −Y), 앞뒤 두 면. at 에서 speed 칸/초 · life 초
     */
    static void cardSwing(ServerLevel level, Lib L, ServerPlayer owner, Vec3 at, Vec3 dir, double speed, double width, double tilt, double life,
            Predicate<ServerPlayer> who, double alpha) {
        Vec3 Y = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        // RuneKit.screenSpin(d, UP, −tilt) 와 같은 셈 (주인 눈에서 본 판의 기울기)
        Vec3 r = V.cross(Y, V.UP);
        double spin;
        if (V.len(r) < 1e-4) spin = tilt;
        else {
            r = V.unit(r);
            Vec3 u = V.cross(r, Y);
            spin = -(Math.toDegrees(Math.atan2(V.dot(V.UP, r), V.dot(V.UP, u))) - tilt);
        }
        double c = Math.cos(Math.toRadians(-spin));
        Vec3 Z;
        double roll;
        if (Math.abs(c) >= 0.5) {
            ServerPlayer viewer = owner;
            if (viewer == null && level.getNearestPlayer(at.x, at.y, at.z, 64, false) instanceof ServerPlayer np) viewer = np;
            Vec3 eye = viewer != null ? mmorpg.fabric.vfx.PBridge.eyeOf(viewer) : V.add(at, Y, -4.0);
            Z = V.sub(eye, at);
            roll = c >= 0 ? 0 : 180;
        } else {
            Z = V.UP;
            roll = -tilt;
        }
        Z = V.sub(Z, V.scale(Y, V.dot(Z, Y)));
        if (V.len(Z) < 1e-4) {
            Z = Math.abs(Y.y) < 0.9 ? V.UP : new Vec3(1, 0, 0);
            Z = V.sub(Z, V.scale(Y, V.dot(Z, Y)));
        }
        Z = V.unit(Z);
        Vec3 X = V.unit(V.cross(Y, Z));
        // 판 Y 둘레로 roll 도 (예전 R.rotateY(roll)): X' = X cos − Z sin · Z' = X sin + Z cos
        double a = Math.toRadians(roll);
        Vec3 X2 = V.add(V.scale(X, Math.cos(a)), Z, -Math.sin(a));
        Vec3 Z2 = V.add(V.scale(X, Math.sin(a)), Z, Math.cos(a));
        Quaternionf q = Vfx.basis(V.unit(X2), Y, V.unit(Z2));
        int ticks = Math.max(2, (int) Math.round(life * 20));
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        // 판 안 돌림 180 (2026-11-15 셋째 고침): 예전 모드 없는 클라이언트 판은 rfx_jslash2 그림을 그대로 붙여 (card 참고) 불룩한 날이 판의 −Y — slashm 을 반 바퀴
        // 돌린 것과 같음. 뒷면 = 같은 그림판의 거울 (예전 한 면 판이 보는 사람 쪽으로 반 바퀴 돌던 것과 같음)
        Vfx.Fx fx = tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, q, 180, 0, Math.max(1, (int) Math.round(ticks / (double) frames)), true, L.n("slashm"),
                who, alpha);
        if (speed > 0) fx.shift(V.scale(Y, speed * ticks / 20.0), ticks);
    }

    /**
     * 2026-11-15 1인칭 눈앞 칼자국 하나 (주인의 1인칭에만 — 카메라를 봄, 예전 rfx_jcuts 대신 일섬 그림 cut): 가운데 at · 길이 len 칸 · 화면에서 줄의 각 deg (반시계 —
     * 0 = 가로) — 그어짐 → 잠깐 머묾 → 터짐 (8틱)
     */
    static void cutEye(ServerPlayer sp, Lib L, Vec3 at, double len, double deg) {
        if (!hasArt(L, "cut")) return;
        Vfx.Fx fx = Vfx.at(sp.level(), at, 14, only1p(sp));
        single(fx, L.n("cut"), at, len / 0.92, deg, 1, false);
    }

    /** 땅에 눕힌 참격 판 (JobFx.CardFace.UP — 면이 위): 불룩한 날 = dir 의 수평을 위에서 tilt 도 돌린 쪽 · 칼빛 너비 width 칸 */
    static void cardUp(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who) {
        Vec3 h = V.flat(dir);
        h = V.len(h) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(h);
        Vec3 bulge = V.turnY(h, tilt);
        Quaternionf q = Vfx.basis(V.unit(V.cross(bulge, V.UP)), bulge, V.UP);
        // 판 안 돌림 180 (2026-11-15 셋째 고침 — card 참고): 예전 모드 없는 클라이언트 판의 그림은 rfx_jslash2 그대로라 보인 불룩한 날은 bulge 의 반대 (쓴 사람 쪽).
        // 앞뒤 두 면 (뒷면 = 같은 그림판의 거울 — 예전 한 면 판이 보는 사람 쪽으로 반 바퀴 돌던 것과 같음)
        tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, q, 180, 0, 1, true, L.n("slashm"), who, 1.0);
    }

    // ------------------------------------------------------------ 2026-11-16 앞을 보는 반달 (사용자 "검사 왜 자꾸 앞을 안보고 위나 아래를 보고 있냐니깐? 반달 모양으로")

    /**
     * 앞을 보는 반달 — 휘두른 면에 놓인 칼빛 반달 (불룩한 날 = 앞): 호의 가운데 C · 반지름 R (칼빛 바깥 날) · 그림 오른쪽 X · 불룩한 쪽 Y (둘 다 단위 · 서로 수직) ·
     * mirror = 그림판 slashm (그림에서 오른쪽 → 왼쪽으로 휘두름 — 아니면 slash: 왼쪽 → 오른쪽). 앞면 = X × Y 쪽. 예전 앞을 보고 선 판 (card — 면이 보는 쪽) 은 3인칭 ·
     * 영상 카메라에서 위로 불룩 ∩ · 아래로 불룩 ∪ 만 보였음 (위 · 아래를 봄 — 사용자 화면 0.49초 블레이드 아포칼립스 첫 베기의 그물 침대 모양)
     */
    record Moon(Vec3 C, Vec3 X, Vec3 Y, double R, boolean mirror) {
        /** 그림 한 변의 반 (호 반지름 = 반 변 × 0.9 — tools/jobvfx/element.py SLASH_R) */
        double half() {
            return R / SLASH_R;
        }

        /** 그림 가운데 (호의 가운데가 그림 가운데보다 반 변 × 0.4 아래 — SLASH_CY) */
        Vec3 center() {
            return V.add(C, Y, SLASH_CY * half());
        }

        /** 휘두르는 차례 s (0 = 처음 끝 · 1 = 마지막 끝) 의 날 자리 — 반지름 몫 dr 만큼 바깥 (− 면 안쪽) */
        Vec3 at(double s, double dr) {
            double a = Math.toRadians(mirror ? -4 + 188 * s : 184 - 188 * s);
            double r = R * (1 + dr);
            return V.add(V.add(C, X, r * Math.cos(a)), Y, r * Math.sin(a));
        }

        /** 휘두르는 쪽 (s 자리의 날이 나아가는 방향 — 단위) */
        Vec3 tangent(double s) {
            double a = Math.toRadians(mirror ? -4 + 188 * s : 184 - 188 * s);
            double k = mirror ? 1 : -1;
            return V.unit(V.add(V.scale(X, -Math.sin(a) * k), Y, Math.cos(a) * k));
        }

        /** 앞면 (X × Y) */
        Vec3 normal() {
            return V.unit(V.cross(X, Y));
        }

        /** 같은 모양을 옮김 */
        Moon shift(Vec3 d) {
            return new Moon(V.add(C, d), X, Y, R, mirror);
        }

        /** 그림 가운데가 P 가 되게 옮김 (예전 판의 가운데 자리를 그대로 쓰는 부름) */
        Moon centered(Vec3 P) {
            return shift(V.sub(P, center()));
        }

        /** 가장 낮은 날 (빛 번짐 몫 1.08) 이 땅 groundY + 0.15 아래면 반달째 올림 */
        Moon above(double groundY) {
            double lo = Double.MAX_VALUE;
            for (int i = 0; i <= 16; i++) lo = Math.min(lo, at(i / 16.0, 0.08).y);
            double need = groundY + 0.15 - lo;
            return need > 0 ? shift(new Vec3(0, need, 0)) : this;
        }
    }

    /** 칼빛 초승달 그림의 호 (tools/jobvfx/element.py SLASH_R · SLASH_CY — 그림 반 변의 몫) */
    static final double SLASH_R = 0.9;
    static final double SLASH_CY = 0.4;
    /**
     * 가로 반달이 보는 눈 쪽으로 앞 축 둘레를 더 도는 끝 (도 — 2026-11-16 첫 영상 CI 43865007580: 45 는 영상 카메라에서 불룩한 날이 왼쪽 위 (위를 섞어 봄) → 65 로
     * 더 세워 앞으로 불룩한 "(" 에 가깝게) · 세로 반달 · 앞 날을 드는 각
     */
    static final double MOON_ROLL = 65;
    static final double MOON_ROLL_V = 30;
    static final double MOON_LIFT = 18;

    /**
     * 반달의 방향: 호의 가운데 C · 앞 fwd (수평으로 씀) · 기울기 tilt (JobFx.slash 의 뜻 — 1인칭 화면에서 기운 각) · 반지름 R. cos tilt 의 크기 0.5 이상 = 가로 휘두름
     * (누운 반달 — 끝 둘이 몸 양옆 · 0 근처 = 왼쪽 → 오른쪽 · 180 근처 = 오른쪽 → 왼쪽, 0 · 180 에서 벗어난 만큼 앞 축 둘레로 기욺 — 반시계 = 오른쪽이 올라감), 아니면 세로
     * 휘두름 (선 반달 — 90 근처 = 위 → 아래 내리침 · 270 근처 = 아래 → 위 올려 베기). 보는 눈 eye (null = 없음 — {@link #viewEye}) 쪽으로 앞 축 둘레를 더 돌림
     * (가로 ±45° · 세로 ±30°: 옆에서 보면 판이 눈 쪽으로 서서 앞으로 불룩한 "(" · 뒤 · 앞에서 보면 누운 반달), 가로 반달은 앞 날을 lift 도 들어 올림 (뒤에서 얇은 줄로
     * 보이지 않게). extraRoll = 앞 축 둘레로 더 기울임 (잔상 겹)
     */
    static Moon moon(Vec3 C, Vec3 fwd, double tilt, double R, Vec3 eye, double lift, double extraRoll) {
        Vec3 h = V.flat(fwd);
        Vec3 r = V.cross(h, V.UP); // 오른쪽
        double c = Math.cos(Math.toRadians(tilt));
        Vec3 e = eye == null ? null : V.sub(eye, C);
        if (Math.abs(c) >= 0.5) {
            double dev = wrap180(c >= 0 ? tilt : tilt - 180);
            double beta = 0;
            if (e != null) beta = clamp(wrap90(Math.toDegrees(Math.atan2(-V.dot(e, r), V.dot(e, V.UP)))), MOON_ROLL);
            double g = Math.toRadians(clamp(dev + beta + extraRoll, MOON_ROLL + 15));
            Vec3 r1 = V.add(V.scale(r, Math.cos(g)), V.UP, Math.sin(g));
            Vec3 n1 = V.add(V.scale(V.UP, Math.cos(g)), r, -Math.sin(g));
            double la = Math.toRadians(lift);
            Vec3 Y = V.unit(V.add(V.scale(h, Math.cos(la)), n1, Math.sin(la)));
            Vec3 N = V.unit(V.add(V.scale(n1, Math.cos(la)), h, -Math.sin(la)));
            Vec3 X = V.unit(V.cross(Y, N));
            // 0 근처 (왼쪽 → 오른쪽) = slash 그대로 (그림 오른쪽 = 몸 오른쪽) · 180 근처 = 미리 뒤집은 slashm
            return new Moon(C, X, Y, R, c < 0);
        }
        boolean down = Math.sin(Math.toRadians(tilt)) > 0; // 90 근처 = 내리침
        double dev = wrap180(tilt - (down ? 90 : 270));
        double d = 0;
        if (e != null) d = clamp(wrap90(Math.toDegrees(Math.atan2(V.dot(e, V.UP), -V.dot(e, r)))), MOON_ROLL_V);
        double a = Math.toRadians(d + dev * 0.5 + extraRoll);
        // 앞 축 둘레로 a: 그림 오른쪽 X = 위 (a = 0) · 앞면 = 왼쪽 (영상 카메라 쪽)
        Vec3 X = V.unit(V.add(V.scale(V.UP, Math.cos(a)), r, Math.sin(a)));
        // 내리침은 몸 오른쪽 · 올려 베기는 왼쪽으로 조금 (1인칭 그림의 불룩한 쪽). 선 반달은 0.85배 (땅 위로 올리면 키가 반지름의 두 배 — 누운 반달보다 커 보였음)
        return new Moon(V.add(C, r, down ? 0.3 : -0.3), X, h, R * 0.85, down);
    }

    private static double wrap180(double deg) {
        double d = ((deg + 180) % 360 + 360) % 360 - 180;
        return d;
    }

    /** −90 ~ 90 으로 (두 면 판이라 반 바퀴 돌린 것은 같은 판) */
    private static double wrap90(double deg) {
        double d = wrap180(deg);
        if (d > 90) d -= 180;
        if (d < -90) d += 180;
        return d;
    }

    private static double clamp(double v, double lim) {
        return Math.max(-lim, Math.min(lim, v));
    }

    /**
     * 반달 판을 돌릴 눈: 시험 영상 3인칭이면 영상 카메라 (PBridge.eyeOf), 아니면 가까운 다른 사람 (24칸 안 — 주인은 1인칭 눈앞 베기를 봄), 없으면 null (돌리지 않음)
     */
    static Vec3 viewEye(ServerPlayer owner, ServerLevel level, Vec3 at) {
        if (owner != null && PBridge.thirdPersonOf(owner)) return PBridge.eyeOf(owner);
        ServerPlayer best = null;
        double bd = 24 * 24;
        for (ServerPlayer p : level.players()) {
            if (owner != null && p.getId() == owner.getId()) continue;
            if (p.isSpectator() && !PBridge.thirdPersonOf(p)) continue;
            double d = p.distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        if (best == null) return null;
        return PBridge.thirdPersonOf(best) ? PBridge.eyeOf(best) : best.getEyePosition();
    }

    /**
     * 반달 판 (앞뒤 두 면 — 뒷면은 같은 그림판의 거울이라 뒤에서도 그림이 바로 읽힘): 수명 life 초에 칸을 맞춤 · vel (칸/초) 로 같이 움직임 (몸과 같이) · 불투명 alpha
     */
    static Vfx.Fx moonCard(ServerLevel level, Lib L, Moon m, Predicate<ServerPlayer> who, double alpha, double life, Vec3 vel) {
        String sheet = L.n(m.mirror() ? "slashm" : "slash");
        int frames = Vfx.sheet(sheet + "_00").frames();
        int every = Math.max(1, (int) Math.round(Math.max(1, life * 20) / frames));
        Quaternionf q = Vfx.basis(m.X(), m.Y(), m.normal());
        Vfx.Fx fx = tiled(level, sheet, 2, m.center(), m.half() * 2, q, 0, 0, every, true, sheet, who, alpha);
        follow(fx, vel, frames * every + 1);
        return fx;
    }

    /** 날아가는 반달 (JobFx.wave · bigWave 의 3인칭 · 다른 사람): 반달 m 이 dir 쪽으로 speed 칸/초 · life 초 (칸을 수명에 맞춤) */
    static void moonFly(ServerLevel level, Lib L, Moon m, Vec3 dir, double speed, double life, Predicate<ServerPlayer> who, double alpha) {
        String sheet = L.n(m.mirror() ? "slashm" : "slash");
        int ticks = Math.max(2, (int) Math.round(life * 20));
        int frames = Vfx.sheet(sheet + "_00").frames();
        int every = Math.max(1, (int) Math.round(ticks / (double) frames));
        Quaternionf q = Vfx.basis(m.X(), m.Y(), m.normal());
        Vfx.Fx fx = tiled(level, sheet, 2, m.center(), m.half() * 2, q, 0, 0, every, true, sheet, who, alpha);
        if (speed > 0) fx.shift(V.scale(V.unit(dir), speed * ticks / 20.0), ticks);
    }

    /**
     * 날아가는 검기 (JobFx.bigWave · slashWave · groundWave · wave — 카메라를 보는 초승달): at 에서 dir 쪽으로 speed 칸/초 · life 초 · 반폭 half 칸 · 기울기 waveTilt
     * (bigWave 의 뜻 — 0 = 위로 불룩 · 90 = 세로 · 180 = 아래로 불룩: slashm 을 −waveTilt 돌림) — 날아가는 동안 베기 칸이 차례로 (그어짐 → 거둬짐)
     */
    static void wave(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double speed, double half, double waveTilt, double life, Predicate<ServerPlayer> who,
            double alpha) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        int ticks = Math.max(2, (int) Math.round(life * 20));
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        int every = Math.max(1, (int) Math.round(ticks / (double) frames));
        // +waveTilt (화면에서 반시계 — 예전 rfx_jwave 의 spin: 0 = 위로 불룩 · 90 = 왼쪽으로 불룩). 2026-11-15 −waveTilt 는 좌우 거울이었음
        Vfx.Fx fx = tiled(level, L.n("slashm"), 2, at, half * 2, null, waveTilt, 0, every, false, null, who, alpha);
        if (speed > 0) fx.shift(V.scale(d, speed * ticks / 20.0), ticks);
    }

    /** 맞은 자리 (JobFx.hit · JobImpact): X 베기 · 섬광 (피 = 맞은 자리 피 튐) · 날아가는 작은 그림 — 몸 가운데 · power (1 = 보통) */
    static void hit(ServerLevel level, Lib L, LivingEntity e, double power) {
        WyrdKit.enter(); // 안의 섬광 · 불씨에 바닐라 겹을 다시 얹지 않음
        try {
            Vec3 b = front(e, Kit.bodyPoint(e));
            double size = (0.75 + Kit.bodyPad(e) * 0.6) * power;
            xslash(level, L, b, size * 1.7, Kit.rand() * 360);
            flash(level, L, b, size * 1.9);
            embers(level, L, b, 3 + (int) Math.round(power * 2), size * 1.1, 0.3, 12);
            WyrdKit.hit(level, L, e, power); // 2026-11-16 위르드식 바닐라 파티클 겹
        } finally {
            WyrdKit.leave();
        }
    }

    /** 터짐 (JobFx.burst): 작으면 섬광 · 보통이면 작은 폭발 (boom_s) · 크면 큰 폭발 (2 × 2) — 크기 size (예전 빛 반 크기) */
    static void burst(ServerLevel level, Lib L, Vec3 at, double size, Predicate<ServerPlayer> who) {
        WyrdKit.enter(); // 안의 섬광 · 불씨에 바닐라 겹을 다시 얹지 않음
        try {
            WyrdKit.burst(level, L, at, size * 1.7, who); // 2026-11-16 위르드식 바닐라 파티클 겹 (지름 — 섬광 · 작은 · 큰 폭발에 맞춤)
            if (size < 0.7) {
                flash(level, L, at, size * 2.6, who);
                return;
            }
            if (size < 2.0) {
                Vfx.Fx fx = Vfx.at(level, at, 20, farEye(who, at, nearR(L, size * 1.8)));
                single(fx, L.n("boom_s"), at, size * 1.8, Kit.rand() * 30 - 15, 1, false);
                flash(level, L, at, size * 1.5, who);
                if (who == null) embers(level, L, at, 4 + (int) (size * 3), size, 0.35, 16);
                return;
            }
            tiled(level, L.n("boom"), 2, at, size * 1.6, null, Kit.rand() * 30 - 15, 0, 1, false, null, farEye(who, at, nearR(L, size * 1.6) * 0.5), 1.0);
            if (who == null) {
                embers(level, L, at, 14, size * 0.8, 0.45, 22);
                shards(level, L, at, 6, size * 0.8, 0.4, 18);
            }
        } finally {
            WyrdKit.leave();
        }
    }

    /** 땅 자국 한 장 (crack_s — 눕힘, 2틱부터 6틱마다): 땅 g · 지름 size 칸 */
    static void crackS(ServerLevel level, Lib L, Vec3 g, double size) {
        crackS(level, L, g, size, null);
    }

    static void crackS(ServerLevel level, Lib L, Vec3 g, double size, Predicate<ServerPlayer> who) {
        Vfx.Fx cx = Vfx.at(level, g, 40, who);
        Vfx.Sprite cr = cx.sprite(L.n("crack_s"), V.up(g, 0.012));
        cr.fixed(flat(Kit.rand() * 360));
        cr.size(size);
        cr.play(2, 0, Vfx.sheet(L.n("crack_s")).frames(), 6, false);
    }

    /** 땅 내려찍기 (JobFx.smash — 반지름 radius 칸): 충격파 · 땅 자국 · 흙먼지 · 날아가는 작은 그림 */
    static void smash(ServerLevel level, Lib L, Vec3 at, double radius, Predicate<ServerPlayer> who) {
        WyrdKit.enter(); // 안의 섬광 · 불씨에 바닐라 겹을 다시 얹지 않음
        try {
            Vec3 g = ground(level, at);
            tiled(level, L.n("shock"), 2, V.up(g, 0.03), radius * 2.3, flat(Kit.rand() * 360), 0, 0, 1, false, null, who, 1.0);
            crackS(level, L, g, radius * 1.5, who);
            Vfx.Fx dx = Vfx.at(level, g, 14, who);
            double ds = Math.min(4.5, radius * 1.2);
            single(dx, L.n("dust"), V.up(g, ds * 0.25), ds, 0, 1, false);
            if (who == null) embers(level, L, V.up(g, 0.3), 6 + (int) (radius * 2), radius * 0.8, 0.4, 18);
            WyrdKit.smash(level, L, g, radius, who); // 2026-11-16 위르드식 바닐라 파티클 겹
        } finally {
            WyrdKit.leave();
        }
    }

    /** 칼끝 반짝임 (JobFx.glint): 짧으면 섬광 · 길면 반짝임이 깜박이며 머묾 (vel 칸/초로 같이 움직임) */
    static void glint(ServerLevel level, Lib L, Vec3 at, double size, double life, Vec3 vel, Predicate<ServerPlayer> who) {
        WyrdKit.enter(); // 안의 섬광 · 불씨에 바닐라 겹을 다시 얹지 않음
        try {
            int ticks = Math.max(2, (int) Math.round(life * 20));
            flash(level, L, at, size * 2.0, who);
            WyrdKit.glint(level, L, at, size, who); // 2026-11-16 위르드식 바닐라 파티클 겹
            if (ticks <= 7) return;
            Vfx.Fx fx = Vfx.at(level, at, ticks + 6, farEye(who, at, nearR(L, size * 1.5)));
            Vfx.Sprite s = single(fx, L.n(L.ember()), at, size * 1.5, Kit.rand() * 40 - 20, 1, true);
            if (vel != null && V.len(vel) > 1e-3) s.to(V.add(at, vel, ticks / 20.0), -1, Math.min(59, ticks));
            s.fade(Math.max(1, ticks - 4), 4);
        } finally {
            WyrdKit.leave();
        }
    }

    /**
     * 한 바퀴 베기 (JobFx.ring — 땅 · 허리 높이에 눕힘): 가운데 c · 반지름 radius 칸 · reverse = 반시계 (미리 뒤집은 spinm) · 불투명 alpha. 묶음에 spin 이 없거나
     * 오래 머무는 고리 (hold 12틱 넘게 — 범위 예고) 면 땅 고리 (ring — 커지며 나타나 hold 틱 머묾)
     */
    static void spin(ServerLevel level, Lib L, Vec3 c, double radius, boolean reverse, double alpha, int hold, Predicate<ServerPlayer> who) {
        if (!has(L, "spin_") || hold > 12) {
            ring(level, L, c, radius, 3, Math.max(2, hold - 3), who, alpha);
            return;
        }
        tiled(level, L.n(reverse ? "spinm" : "spin"), 2, V.up(c, 0.02), radius * 2 / 0.8, flat(Kit.rand() * 360), 0, 0, 1, false, null, who, alpha);
    }

    /** 땅 고리 — 볼 사람 · 불투명을 정함 */
    static Vfx.Fx ring(ServerLevel level, Lib L, Vec3 c, double r, int grow, int hold, Predicate<ServerPlayer> who, double alpha) {
        int G = Math.max(1, grow), Hh = Math.max(1, hold);
        Vfx.Fx fx = Vfx.at(level, c, G + Hh + 8, who);
        double size = r * 2.0 / 0.9;
        double t = size / 2;
        Quaternionf q = flat(Kit.rand() * 360);
        String base = L.n("ring");
        int frames = Vfx.sheet(base + "_00").frames();
        for (int rr = 0; rr < 2; rr++) {
            for (int cc = 0; cc < 2; cc++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + rr + cc, V.up(c, 0.03));
                sp.fixed(q);
                double ox = (cc - 0.5) * t;
                double oy = (0.5 - rr) * t;
                sp.size(t * 0.15);
                sp.offset(ox * 0.15, oy * 0.15);
                if (alpha < 0.999) sp.alpha(alpha);
                sp.play(0, 0, frames, 3, true);
                fx.on(1, () -> {
                    sp.offset(ox, oy);
                    sp.to(null, t, G);
                });
                sp.fade(G + Hh, 4);
            }
        }
        return fx;
    }

    /**
     * 2026-11-17 던진 덩이 하나 (예전 rpg:glob · rpg:boulder — 포물선으로 나는 그림 한 장): 그림판 art 가 at 에서 vel (칸/초) · 아래로 g (칸/초²) 로 life 초 날아감
     * (2틱마다 다음 자리로 옮김 — 표시 엔티티 보간) · 크기 size 칸 · 칸은 2틱마다 되풀이 · 끝 4틱 옅어짐
     */
    static void lob(ServerLevel level, Lib L, String art, Vec3 at, Vec3 vel, double g, double life, double size, Predicate<ServerPlayer> who) {
        int ticks = Math.max(2, (int) Math.round(life * 20));
        Vfx.Fx fx = Vfx.at(level, at, ticks + 2, who);
        Vfx.Sprite sp = fx.sprite(L.n(art), at);
        sp.size(size);
        sp.play(0, 0, Vfx.sheet(L.n(art)).frames(), 2, true);
        for (int t = 2; t <= ticks; t += 2) {
            double s = t / 20.0;
            Vec3 p = new Vec3(at.x + vel.x * s, at.y + vel.y * s - 0.5 * g * s * s, at.z + vel.z * s);
            fx.on(t - 2, () -> sp.to(p, 0, 2));
        }
        sp.fade(Math.max(0, ticks - 4), 4);
    }

    /**
     * 2026-11-17 세운 고리 (예전 rfx_sigil 의 면이 위가 아닌 문양 — 심연 룬의 눈 · 손바닥 문양, 보스 문양): 가운데 c · 반지름 r · 면 normal 쪽 (앞뒤 두 면 — 뒷면은 거울 ·
     * 조각 자리도 거울이라 뒤에서도 같은 그림). 커지기 grow 틱 · 머묾 hold 틱
     */
    static Vfx.Fx ringFacing(ServerLevel level, Lib L, Vec3 c, double r, Vec3 normal, int grow, int hold, Predicate<ServerPlayer> who, double alpha) {
        int G = Math.max(1, grow), Hh = Math.max(1, hold);
        Vfx.Fx fx = Vfx.at(level, c, G + Hh + 8, who);
        double size = r * 2.0 / 0.9;
        double t = size / 2;
        Vec3 ez = V.unit(normal);
        Vec3 ex = V.cross(V.UP, ez);
        if (V.len(ex) < 1e-4) ex = new Vec3(1, 0, 0);
        ex = V.unit(ex);
        Vec3 ey = V.unit(V.cross(ez, ex));
        Quaternionf q = Vfx.basis(ex, ey, ez).rotateZ((float) Math.toRadians(Kit.rand() * 360));
        String base = L.n("ring");
        int frames = Vfx.sheet(base + "_00").frames();
        for (int s = 0; s < 2; s++) {
            for (int rr = 0; rr < 2; rr++) {
                for (int cc = 0; cc < 2; cc++) {
                    Vfx.Sprite sp = fx.sprite(base + "_" + rr + cc, c);
                    sp.fixed(q);
                    if (s == 1) sp.mirror();
                    double ox = (cc - 0.5) * t * (s == 1 ? -1 : 1);
                    double oy = (0.5 - rr) * t;
                    sp.size(t * 0.15);
                    sp.offset(ox * 0.15, oy * 0.15);
                    if (alpha < 0.999) sp.alpha(alpha);
                    sp.play(0, 0, frames, 3, true);
                    fx.on(1, () -> {
                        sp.offset(ox, oy);
                        sp.to(null, t, G);
                    });
                    sp.fade(G + Hh, 4);
                }
            }
        }
        return fx;
    }

    /**
     * 일섬 · 걸린 칼자국 (JobFx.line · 블레이드 에코 — cut: 0 ~ 1 그어짐 → 2 ~ 4 머묾 (되풀이) → 5 ~ 7 터져 벌어짐): from → to 를 품은 한 판이 줄을 축으로 보는 사람의
     * 눈 (가장 가까운 사람 — 영상이면 영상 카메라, PBridge.eyeOf) 쪽을 봄 (앞뒤 두 면) — 예전 rfx_jline2 · rfx_jecho (가는 쪽으로 누워 눈 쪽을 봄) 와 같음. 2026-11-15 사용자
     * "위를 보고 있고 … 옆을 보고 있고" — 전에는 줄을 축으로 눕힌 판 · 세운 판 둘이라 영상에서 위 · 옆을 보는 판으로 보였음. hold 틱 머문 뒤 터짐 (burst false 면 옅어짐)
     */
    static Vfx.Fx cut(ServerLevel level, Lib L, Vec3 from, Vec3 to, int hold, boolean burst, Predicate<ServerPlayer> who) {
        return cut(level, L, from, to, hold, burst, who, "cut");
    }

    /**
     * 같은 것 — art = 그림판 (cut = 정사각 일섬 · 2026-11-17 cutl = 가로로 긴 일섬: 같은 모양을 6.4 : 1 칸에 — 칸 높이 = 길이 ÷ 0.92 ÷ 가로세로 비. 정사각 일섬을
     * 9칸 넘게 키우면 한 텍셀 줄이 점선처럼 끊겼음 — CI 43977395799 무간참역)
     */
    static Vfx.Fx cut(ServerLevel level, Lib L, Vec3 from, Vec3 to, int hold, boolean burst, Predicate<ServerPlayer> who, String art) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2 || !hasArt(L, art)) return null;
        Vec3 ex = V.scale(d, 1 / len);
        Vec3 mid = V.lerp(from, to, 0.5);
        net.minecraft.world.entity.player.Player near = level.getNearestPlayer(mid.x, mid.y, mid.z, 64, false);
        Vec3 eye = near instanceof ServerPlayer np ? mmorpg.fabric.vfx.PBridge.eyeOf(np) : V.up(mid, 2.0);
        Vec3 ez = V.sub(eye, mid);
        ez = V.sub(ez, V.scale(ex, V.dot(ez, ex)));
        if (V.len(ez) < 1e-4) {
            ez = V.cross(ex, V.UP);
            if (V.len(ez) < 1e-4) ez = new Vec3(1, 0, 0);
        }
        ez = V.unit(ez);
        Vec3 ey = V.unit(V.cross(ez, ex));
        Quaternionf faceQ = Vfx.basis(ex, ey, ez);
        int H = Math.max(1, hold);
        int life = 2 + H + (burst ? 4 : 5) + 2;
        Vfx.Fx fx = Vfx.at(level, mid, life, who);
        String name = L.n(art);
        double size = len / 0.92 / Math.max(1, Vfx.sheet(name).aspect());
        for (Quaternionf q : new Quaternionf[] {faceQ}) {
            for (int s = 0; s < 2; s++) {
                Vfx.Sprite sp = fx.sprite(name, mid);
                sp.fixed(q);
                if (s == 1) sp.mirror();
                sp.size(size);
                sp.play(0, 0, 2, 1, false);
                fx.on(2, () -> sp.play(fx.age(), 2, 3, 2, true));
                if (burst) fx.on(2 + H, () -> sp.play(fx.age(), 5, 3, 1, false));
                else sp.fade(2 + H, 5);
            }
        }
        return fx;
    }

    /**
     * 땅 가르기 (검사 라그나로크 · 블러드 나이트 — rift 여섯 칸을 눕혀 이어 붙임): 땅 g 에서 f 쪽 (수평) 으로 len 칸 — piece 칸 토막마다 그 자리에 닿는 틱 (speed 칸/초) 에 열림
     */
    static void rift(ServerLevel level, Lib L, Vec3 g, Vec3 f, double len, double speed, double piece) {
        if (!has(L, "rift")) return;
        Vec3 h = V.flat(f);
        if (V.len(h) < 1e-6) return;
        h = V.unit(h);
        String name = L.n("rift");
        int frames = Vfx.sheet(name).frames();
        for (double s = 0.3; s < len; s += piece * 0.78) {
            int at = (int) Math.round(s / Math.max(1, speed) * 20);
            Vec3 m = ground(level, V.add(g, h, s + piece * 0.4));
            Vec3 ex = Kit.rand() < 0.5 ? h : V.scale(h, -1);
            Quaternionf q = Vfx.basis(ex, V.cross(V.UP, ex), V.UP);
            Vfx.Fx fx = Vfx.at(level, m, at + frames * 7 + 6);
            fx.on(at, () -> {
                Vfx.Sprite sp = fx.sprite(name, V.up(m, 0.015));
                sp.fixed(q);
                sp.size(piece * 1.15);
                sp.play(fx.age(), 0, 1, 2, false);
                fx.on(fx.age() + 2, () -> sp.play(fx.age(), 1, frames - 1, 7, false));
            });
        }
    }

    /**
     * 2026-11-17 대지 가르기의 균열 (RuneTerra.Seg — rfx_fissure 세 겹: 어두운 틈 · 호박빛 · 쇳물, 땅에 누움 · 반 길이 size · 길 방향 spin = atan2(f.z, −f.x)): 계열 묶음
     * (대지 = 곰) 의 갈라지는 땅 (rift) 토막을 같은 줄에 — 번쩍 · 벌어짐 (0 ~ 2) 을 바로, 벌어진 칸으로 life 동안 머문 뒤 식으며 사라짐 (3 ~ 5). 첫 겹 (cell 0) 에
     * 그리고 나머지 두 겹은 그리지 않음. 빛이 계열 빛이 아니어도 (어두운 흙 · 불빛) 계열 묶음 — 균열은 이 룬의 대표 그림 (CI 43989923127 예전 9)
     */
    static boolean fissure(ServerLevel level, Vec3 at, Opt o) {
        String tag = mmorpg.fabric.Tasks.tag();
        if (tag == null) return false;
        int c = tag.indexOf(':');
        Lib L = runeLib(c < 0 ? tag : tag.substring(0, c));
        if (L == null || !hasArt(L, "rift")) return false;
        Vec3 dir = o.vec("dir", V.UP);
        if (V.len(dir) > 1e-6 && V.unit(dir).y < 0.97) return false;
        if ((int) Math.round(o.num("cell", 0)) != 0) return true;
        String name = L.n("rift");
        int frames = Vfx.sheet(name).frames();
        double half = Math.max(0.5, o.num("size", 1));
        double th = Math.toRadians(o.num("spin", 0));
        Vec3 h = new Vec3(-Math.cos(th), 0, Math.sin(th));
        int ticks = Math.max(14, (int) Math.round(o.num("life", 1) * 20));
        double piece = Math.min(2.6, half);
        Predicate<ServerPlayer> who = who(o);
        for (double s = -half + piece * 0.45; s < half; s += piece * 0.78) {
            Vec3 m = V.up(V.add(at, h, s), 0.015);
            Vec3 ex = Kit.rand() < 0.5 ? h : V.scale(h, -1);
            Quaternionf q = Vfx.basis(ex, V.cross(V.UP, ex), V.UP);
            Vfx.Fx fx = Vfx.at(level, m, ticks + 2, who);
            Vfx.Sprite sp = fx.sprite(name, m);
            sp.fixed(q);
            sp.size(piece * 1.15);
            sp.play(0, 0, Math.min(3, frames), 2, false);
            int cool = Math.max(8, ticks - 5 * Math.max(1, frames - 3));
            if (frames > 3) fx.on(cool, () -> sp.play(fx.age(), 3, frames - 3, 5, false));
        }
        return true;
    }

    /** 핏빛 달 (moon 2 × 2 되풀이 — 카메라를 봄): 가운데 at · 지름 size 칸 · life 틱 (grow 틱에 커지며 나타남 · 끝 10틱 옅어짐) */
    static Vfx.Fx moon(ServerLevel level, Lib L, Vec3 at, double size, int grow, int life) {
        Vfx.Fx fx = Vfx.at(level, at, life + 4);
        String base = L.n("moon");
        int frames = Vfx.sheet(base + "_00").frames();
        double t = size / 2;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + r + c, at);
                double ox = (c - 0.5) * t;
                double oy = (0.5 - r) * t;
                sp.size(t * 0.6);
                sp.offset(ox * 0.6, oy * 0.6);
                sp.play(0, 0, frames, 3, true);
                fx.on(1, () -> {
                    sp.offset(ox, oy);
                    sp.to(null, t, Math.max(1, grow));
                });
                sp.fade(Math.max(2, life - 10), 10);
            }
        }
        return fx;
    }

    /** 세로 그림 하나 (pillar — 빛기둥 · 피 기둥, 세로축 빌보드): 발 자리 foot · 높이 h 칸 · every 틱마다 한 칸 */
    static void pillar(ServerLevel level, Lib L, Vec3 foot, double h, int every) {
        pillar(level, L, foot, h, every, null);
    }

    /** 같은 것 — 볼 사람을 정함 (1인칭 눈 바로 앞 기둥은 거름: 그림 128 × 256 이라 한 픽셀 = h / 256 칸) */
    static void pillar(ServerLevel level, Lib L, Vec3 foot, double h, int every, Predicate<ServerPlayer> who) {
        String name = L.n("pillar");
        if (Vfx.sheetNames(name).isEmpty()) return;
        Vfx.Sheet s = Vfx.sheet(name);
        Vfx.Fx fx = Vfx.at(level, foot, s.frames() * Math.max(1, every) + 4, farEye(who, V.up(foot, h * 0.5), nearR(L, h)));
        Vfx.Sprite sp = fx.sprite(name, V.up(foot, h * 0.5));
        sp.vertical();
        sp.size(h);
        sp.play(0, 0, s.frames(), Math.max(1, every), false);
    }

    /**
     * 빛의 대검 (ak_excal 1 × 4 — 위를 보는 칼, 소드마스터 엑스칼리버): 손 hands 가 손잡이를 쥠 · 칼끝 쪽 dir · 길이 len 칸 — 칼의 면은 쓴 사람의 오른쪽 축 right 를 품음
     * (뒤 3인칭에서 넓게 · 내리치면 위에서 넓게) · 앞뒤 두 면. 반환 = 조각 그림 넷 × 두 면 (excalPose 로 돌림 · 늘림)
     */
    static Vfx.Sprite[] excal(Vfx.Fx fx, Vec3 hands, Vec3 dir, Vec3 right, double len) {
        Vfx.Sprite[] out = new Vfx.Sprite[8];
        String base = GOLD.n("excal");
        int frames = Vfx.sheet(base + "_00").frames();
        for (int r = 0; r < 4; r++) {
            for (int s = 0; s < 2; s++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + r + "0", hands);
                if (s == 1) sp.mirror();
                sp.play(fx.age(), (int) (Kit.rand() * frames), frames, 2, true);
                out[r * 2 + s] = sp;
            }
        }
        excalPose(out, hands, dir, right, len, 0);
        return out;
    }

    /**
     * 땅에서 솟구치는 빛의 검 (ak_excal 1 × 4 — 세로축 빌보드, 엑스칼리버 검격 뒤): 땅 g · 높이 h 칸 — 땅속에서 3틱에 솟아 hold 틱 머문 뒤 5틱에 옅어짐 · 밑동 섬광
     */
    static void risingSword(ServerLevel level, Vec3 g, double h, int hold) {
        String base = GOLD.n("excal");
        if (!has(GOLD, "excal_00")) return;
        int frames = Vfx.sheet(base + "_00").frames();
        Vfx.Fx fx = Vfx.at(level, g, 3 + hold + 10);
        double t = h / 4;
        Vec3 low = V.up(g, -h * 0.6);
        Vec3 high = V.up(g, h * 0.385); // 코등이 (그림 아래에서 0.115) 가 땅에 — 손잡이는 땅속
        for (int r = 0; r < 4; r++) {
            Vfx.Sprite sp = fx.sprite(base + "_" + r + "0", low);
            sp.vertical();
            sp.size(t);
            sp.offset(0, (1.5 - r) * t);
            sp.play(0, (int) (Kit.rand() * frames), frames, 2, true);
            fx.on(1, () -> sp.to(high, t, 3));
            sp.fade(3 + hold, 5);
        }
        flash(level, GOLD, V.up(g, 0.3), h * 0.45);
    }

    /** 불꽃 고리 (br_flame — 단조 불꽃을 땅 고리를 따라 세움, 소드마스터 용의 피): 가운데 땅 c · 반지름 R · 불꽃 높이 h · 한 번 솟았다 사그라듦 */
    static void flameRing(ServerLevel level, Vec3 c, double R, double h) {
        int count = Math.max(10, (int) Math.round(2 * Math.PI * R / 0.9));
        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count;
            Vec3 p = ground(level, new Vec3(c.x + Math.cos(a) * R, c.y + 1, c.z + Math.sin(a) * R));
            flame(level, FORGE, p, h * (0.8 + Kit.rand() * 0.4), 1);
        }
    }

    /** 빛의 대검 모습: 손 hands · 칼끝 쪽 dir · 오른쪽 축 right · 길이 len (그림 높이 — 손은 그림 아래에서 0.075 높이) · ticks 틱 보간 */
    static void excalPose(Vfx.Sprite[] tiles, Vec3 hands, Vec3 dir, Vec3 right, double len, int ticks) {
        Vec3 ey = V.unit(dir);
        Vec3 ex = V.sub(right, V.scale(ey, V.dot(right, ey)));
        ex = V.len(ex) < 1e-3 ? V.rightOf(V.flat(ey)) : V.unit(ex);
        Vec3 ez = V.cross(ex, ey);
        Quaternionf q = Vfx.basis(ex, ey, ez);
        double t = len / 4;
        Vec3 center = V.add(hands, ey, len * (0.5 - 0.075));
        for (int r = 0; r < 4; r++) {
            for (int s = 0; s < 2; s++) {
                Vfx.Sprite sp = tiles[r * 2 + s];
                if (sp == null) continue;
                sp.offset(0, (1.5 - r) * t);
                if (ticks <= 0) sp.fixed(q);
                else sp.face(q, ticks);
                sp.to(center, t, ticks);
            }
        }
    }

    // ------------------------------------------------------------ 2026-11-15 예전 rfx 파티클을 칠한 그림판 한 장으로 (암살자 그림자 묶음)

    /** rfx 방향: 카메라를 봄 (rotate_xyz) */
    static final int BILL = 0;
    /** rfx 방향: 면이 dir (direction_z — 위 = 세계 위를 dir 에 수직으로, 뒷면은 거울) */
    static final int FACE_DIR = 1;
    /** rfx 방향: 세로축만 카메라를 봄 (lookat_y) */
    static final int VERT = 2;
    /**
     * 2026-11-16 rfx 방향: 가는 쪽으로 누워 보는 사람의 눈 쪽을 봄 (lookat_direction — 그림 +x = dir · 면 = 눈 쪽을 dir 에 수직으로): 보는 사람마다 한 장
     * (PBridge 는 기준 눈 하나 — 다른 사람에게는 옆 · 뒤가 보였음)
     */
    static final int LOOK_DIR = 3;

    /** 2026-11-16 예전 rfx 이름 → 묶음의 칠한 그림 (조각 k · 방향 · 세로 몫 sy · 크기 몫 — 예전 그림과 같은 틀) */
    record Paint(String art, int k, int facing, double sy, double scale, String mirrorArt, int mirrorFrom) {
        Paint(String art, int k, int facing, double sy, double scale) {
            this(art, k, facing, sy, scale, null, 0);
        }
    }

    /**
     * 2026-11-16 매지션 라인 (사용자 "다음 직업으로 마법사 작업"): 칠한 그림이 있는 예전 rfx — 예전 그림판의 칸 크기 (세로 몫) · 머리 자리 · 고리 반지름에 맞춰 그림.
     * jburst2 (색을 정한 터짐 — 프리즘 레이 갈래 끝) 는 섬광 (PhantomFx.burst 의 작은 터짐과 같은 크기 몫 1.3), jring2 (세운 고리 · 눈앞 고리) 는 한 바퀴 고리
     * (예전 고리 반지름 0.88 · 그림 0.8)
     */
    static final java.util.Map<String, Paint> PAINT = java.util.Map.ofEntries(
            java.util.Map.entry("jstar", new Paint("star", 1, BILL, 1, 1)),
            java.util.Map.entry("jbolt2", new Paint("bolt", 1, LOOK_DIR, 1 / 3.0, 1)),
            java.util.Map.entry("jbeam2", new Paint("beam", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jshatter2", new Paint("shatter", 1, BILL, 1, 1)),
            java.util.Map.entry("jfireball", new Paint("fireball", 1, LOOK_DIR, 0.375, 1)),
            java.util.Map.entry("jicelance", new Paint("icelance", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jsparkorb", new Paint("sparkorb", 1, LOOK_DIR, 0.5, 1)),
            java.util.Map.entry("jfrost", new Paint("frost", 1, BILL, 1, 1)),
            java.util.Map.entry("jdash2", new Paint("dash", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jaura2", new Paint("aura", 1, VERT, 2, 1)),
            java.util.Map.entry("jstarnode", new Paint("starnode", 1, BILL, 1, 1)),
            java.util.Map.entry("jfallstar", new Paint("fallstar", 1, LOOK_DIR, 0.375, 1)),
            java.util.Map.entry("jgalaxy", new Paint("galaxy", 2, FACE_DIR, 1, 1)),
            java.util.Map.entry("jhalo", new Paint("halo", 2, FACE_DIR, 1, 1)),
            java.util.Map.entry("jaegis2", new Paint("aegis", 1, VERT, 1.25, 1)),
            java.util.Map.entry("jsoul2", new Paint("soul", 1, BILL, 1, 1)),
            java.util.Map.entry("jskull", new Paint("skull", 1, BILL, 1, 1)),
            java.util.Map.entry("jrift", new Paint("rift", 1, FACE_DIR, 2.5, 1)),
            java.util.Map.entry("jbonespear", new Paint("bonespear", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jhand2", new Paint("hand", 1, VERT, 2, 1)),
            java.util.Map.entry("jsmoke2", new Paint("smoke", 1, BILL, 1, 1)),
            java.util.Map.entry("jburst2", new Paint("flash", 1, BILL, 1, 1.3)),
            java.util.Map.entry("jring2", new Paint("spin", 2, FACE_DIR, 1, 0.88 / 0.8, "spinm", 2 * 8)),
            // 2026-11-16 트릭스터 (환영 — tools/jobvfx/mirror.py: 예전 rfx 와 같은 칸 · 칸 수 · 가로세로 비 — 세로 몫 = 높이 / 너비)
            java.util.Map.entry("jmirror", new Paint("mirror", 1, BILL, 1, 1)),
            java.util.Map.entry("jpane", new Paint("pane", 1, FACE_DIR, 224 / 128.0, 1)),
            java.util.Map.entry("jmshatter", new Paint("mshatter", 1, FACE_DIR, 320 / 256.0, 1)),
            java.util.Map.entry("jribbon", new Paint("ribbon", 1, FACE_DIR, 1, 1)),
            // 2026-11-16 피에로 (서커스 — tools/jobvfx/circus.py)
            java.util.Map.entry("jconfetti", new Paint("confetti", 1, BILL, 1, 1)),
            java.util.Map.entry("jballoon", new Paint("balloon", 1, BILL, 1, 1)),
            java.util.Map.entry("jfirework", new Paint("firework", 1, BILL, 1, 1)),
            java.util.Map.entry("jspot", new Paint("spot", 1, VERT, 384 / 128.0, 1)),
            // 2026-11-16 전사 라인 (tools/jobvfx/beast.py — 예전 rfx 와 같은 칸 수 · 가로세로 비 · 머리 자리 · 땅 자리 · 도는 쪽)
            java.util.Map.entry("jwolf", new Paint("wolf", 1, LOOK_DIR, 0.5, 1)),
            java.util.Map.entry("jtwister2", new Paint("twister", 1, VERT, 2, 1)),
            java.util.Map.entry("jwhirl2", new Paint("whirl", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jcomet2", new Paint("comet", 1, LOOK_DIR, 0.375, 1)),
            java.util.Map.entry("jbreak2", new Paint("shieldbreak", 1, BILL, 1, 1)),
            java.util.Map.entry("jquake", new Paint("quake", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jbear", new Paint("bear", 1, BILL, 1, 1)),
            java.util.Map.entry("jpaw", new Paint("paw", 1, BILL, 1, 1)),
            java.util.Map.entry("jspike", new Paint("spike", 1, VERT, 1, 1)),
            // 2026-11-16 타격 섬광 (JobImpact 예전 길 · 워로드가 직접 부름 — 흰 심지 · 빛 가시 · 고리) · 반짝임 (jglint — 네 뿔 별) 은 묶음의 섬광으로
            // 2026-11-16 마검사 라인 (tools/jobvfx/rune.py · storm.py — 예전 rfx 와 같은 칸 수 · 가로세로 비 · 땅 · 머리 자리)
            java.util.Map.entry("jrunes", new Paint("runes", 1, LOOK_DIR, 0.125, 1)),
            java.util.Map.entry("jglyph", new Paint("glyph", 1, BILL, 1, 1)),
            java.util.Map.entry("jninerunes", new Paint("ninerunes", 1, BILL, 0.5, 1)),
            java.util.Map.entry("jpillar", new Paint("rpillar", 1, VERT, 4, 1)),
            java.util.Map.entry("jtear", new Paint("tear", 1, BILL, 0.5, 1)),
            java.util.Map.entry("jthunder2", new Paint("thunder", 1, VERT, 4, 1)),
            java.util.Map.entry("jvolt2", new Paint("volt", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jspear2", new Paint("spear", 1, LOOK_DIR, 1 / 6.0, 1)),
            java.util.Map.entry("jimpact", new Paint("flash", 1, BILL, 1, 1)),
            // 2026-11-16 빛을 정해 부르는 엇갈린 베기 · 찌르기 · 도는 비수 (트릭스터 거울 속 나의 X 자국 · 피에로 상자의 비수 — JobFx.cross(glow) 등 · 예전 그림과 같은 틀 —
            // tools/jobvfx/shadow.py 를 묶음 빛으로)
            java.util.Map.entry("jcross2", new Paint("cross", 1, BILL, 1, 1)),
            java.util.Map.entry("jstab2", new Paint("stab", 1, BILL, 1, 1)),
            java.util.Map.entry("jdagger2", new Paint("dagger", 1, BILL, 1, 1)),
            // 2026-11-16 사제 라인 (tools/jobvfx/holy.py — 예전 rfx 와 같은 칸 수 · 가로세로 비 · 가운데): 회복 십자 · 음표 · 성흔 · 종 (세움) · 연꽃 (땅에 누움) · 큰 꽃 ·
            // 나비 · 표식 꽃 (jpetal2 0 ~ 3) · 꽃잎 (4 ~ 7)
            java.util.Map.entry("jheal2", new Paint("heal", 1, BILL, 1, 1)),
            java.util.Map.entry("jnote2", new Paint("note", 1, BILL, 1, 1)),
            java.util.Map.entry("jstigma2", new Paint("stigma", 1, BILL, 1, 1)),
            java.util.Map.entry("jbell2", new Paint("bell", 1, VERT, 1, 1)),
            java.util.Map.entry("jlotus", new Paint("lotus", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jbloom", new Paint("bloom", 1, BILL, 1, 1)),
            java.util.Map.entry("jfly", new Paint("fly", 1, BILL, 1, 1)),
            java.util.Map.entry("jpetal2", new Paint("blossom", 1, BILL, 1, 1, "petal", 4)),
            // 2026-11-16 워록 (tools/jobvfx/hex.py — 같은 틀): 핏빛 창 (가는 쪽으로 누움 · 96 / 320) · 웅덩이 (땅에 누움) · 핏물 기둥 (세움 · 384 / 192) · 심장 · 핏빛 불기둥
            // (세움 · 448 / 192)
            java.util.Map.entry("jlance", new Paint("lance", 1, LOOK_DIR, 96 / 320.0, 1)),
            java.util.Map.entry("jpool", new Paint("pool", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jgeyser", new Paint("geyser", 1, VERT, 2, 1)),
            java.util.Map.entry("jheart", new Paint("heart", 1, BILL, 1, 1)),
            java.util.Map.entry("jpyre", new Paint("pyre", 1, VERT, 448 / 192.0, 1)),
            // 2026-11-16 수호기사 (tools/jobvfx/ward.py — 같은 틀): 도는 방패 · 빛의 다리 (가는 쪽으로 누움 · 64 / 512) · 빛의 사슬 (32 / 256)
            java.util.Map.entry("jbuckler2", new Paint("buckler", 1, BILL, 1, 1)),
            java.util.Map.entry("jbifrost", new Paint("bifrost", 1, LOOK_DIR, 0.125, 1)),
            java.util.Map.entry("jchain2", new Paint("chain", 1, LOOK_DIR, 0.125, 1)),
            // 2026-11-16 다크 나이트 (tools/jobvfx/maw.py — 같은 틀): 방패의 아가리 · 검은 혀 (가는 쪽으로 누움 · 96 / 512) · 땅의 아가리 (세움) · 불씨 기둥 (세움 · 384 / 192)
            java.util.Map.entry("jdmaw", new Paint("dmaw", 1, BILL, 1, 1)),
            java.util.Map.entry("jtendril", new Paint("tendril", 1, LOOK_DIR, 96 / 512.0, 1)),
            java.util.Map.entry("jabyss", new Paint("abyss", 1, VERT, 1, 1)),
            java.util.Map.entry("jcinder", new Paint("cinder", 1, VERT, 2, 1)),
            // 2026-11-16 성기사 라인 (tools/jobvfx/thunder.py — 같은 틀): 번개 줄 (가는 쪽으로 누움 · 128 / 512) · 먹구름 (192 / 384) · 돌 껍데기 (세움 · 256 / 192) ·
            // 번개 문장 · 바퀴 자국 (땅에 누움 · 64 / 256) · 번개 망치 · 거인의 손. 번개 기둥 jpillar 는 위 rpillar (묶음마다 그 묶음의 기둥)
            java.util.Map.entry("jarc", new Paint("arc", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jcloud", new Paint("cloud", 1, BILL, 0.5, 1)),
            java.util.Map.entry("jstone", new Paint("stone", 1, VERT, 256 / 192.0, 1)),
            java.util.Map.entry("jjudge2", new Paint("judge", 1, BILL, 1, 1)),
            java.util.Map.entry("jwheel", new Paint("wheel", 1, FACE_DIR, 0.25, 1)),
            java.util.Map.entry("jmaulswing", new Paint("maulswing", 1, BILL, 1, 1)),
            java.util.Map.entry("jgrip", new Paint("grip", 1, BILL, 1, 1)),
            // 2026-11-16 사신 라인 (tools/jobvfx/reap.py — 같은 틀): 망자의 비명 · 사신 그림자 (세움 · 256 / 192) · 처형 표식 (땅에 누움)
            java.util.Map.entry("jwail", new Paint("wail", 1, BILL, 1, 1)),
            java.util.Map.entry("jreaper2", new Paint("reaper", 1, VERT, 256 / 192.0, 1)),
            java.util.Map.entry("jxmark", new Paint("xmark", 1, FACE_DIR, 1, 1)),
            // 2026-11-16 창술사 라인 (tools/jobvfx/lance.py — 같은 틀): 깃털 · 바람 고리 (땅에 누움) · 도는 창 (면이 normal — 공용 한 바퀴 고리 spin 과 이름을 나눔) ·
            // 바람 소용돌이 · 불꽃 덩이 · 용의 혼 · 서 있는 불꽃 (세움 · 256 / 128 — forge.py)
            java.util.Map.entry("jfeather", new Paint("feather", 1, BILL, 1, 1)),
            java.util.Map.entry("jwind2", new Paint("wind", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jspin2", new Paint("lspin", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jswirl", new Paint("swirl", 1, BILL, 1, 1)),
            java.util.Map.entry("jfire", new Paint("fire", 1, BILL, 1, 1)),
            java.util.Map.entry("jdragon", new Paint("dragon", 1, BILL, 1, 1)),
            java.util.Map.entry("jflame", new Paint("flame", 1, VERT, 2, 1)),
            // 2026-11-16 피닉스 가드 (tools/jobvfx/phoenix.py — 같은 틀): 불꽃 날개 (416 / 512) · 불사조 · 분노의 부채꼴 (땅에 누움)
            java.util.Map.entry("jfwings", new Paint("fwings", 1, BILL, 416 / 512.0, 1)),
            java.util.Map.entry("jphoenix", new Paint("phoenix", 1, BILL, 1, 1)),
            java.util.Map.entry("jfury", new Paint("fury", 1, FACE_DIR, 1, 1)),
            java.util.Map.entry("jglint", new Paint("flash", 1, BILL, 1, 0.7)));

    /** 칠한 직업 (그 직업의 두 빛으로 그린 예전 rfx 를 그 묶음의 칠한 그림으로 — 직업 빛 배열은 jobs.json 을 다시 읽으면 바뀌므로 그때그때 비교) */
    private static final String[] PAINTED_JOBS = {"mage", "arcmage", "necromancer", "trickster", "pierrot", "warrior", "warlord", "frostraider", "spellblade",
            "arcaneblade", "elementalblade", "priest", "saint", "warlock", "guardian", "darkknight", "paladin", "thunderlord", "tyrant", "reaper", "soulreaper",
            "executioner", "lancer", "stormlancer", "dragoon", "phoenix"};
    /** 스킬이 직업 빛 대신 쓰는 빛 묶음 (같은 배열) → 묶음: 아크메이지 밤빛 · 금서의 세 주문 빛 · 프리즘 레이 다섯 빛 (무지개 대신 비전 광선) · 망자의 연기 */
    private static java.util.Map<double[], Lib> palette;

    /** 빛 glow 로 그린 예전 rfx 를 칠할 묶음 (없으면 null — 예전 길) */
    static Lib painter(double[] glow) {
        return painter(glow, true);
    }

    /**
     * 2026-11-16 위르드식 다리 (SkillFx.painted — JobSystem.init 이 등록): 칠한 직업 빛 (원소 빛은 빼고 — 룬 · 보스 · 원소 반응은 아직 예전 그림) 으로 부른 예전
     * 파티클을 그 묶음의 그림 · 바닐라 겹으로. 색 먼지 불티 (SkillFx.sparks 한 무리 · rpg:arrow_spark 하나 — 모드 없는 클라이언트에서 둘레 빛을 받는 먼지) = 묶음의
     * 스스로 빛나는 불티 (그림판 없이 — 스킬 하나에 수백 개라 표시 엔티티 대신 파티클), 퍼지는 땅 고리 (rpg:shock_ring) = 칠한 충격파 (지름 = 예전 크기 × 2), 터짐
     * (rpg:bash_burst) = 칠한 섬광. 묶음에 그 그림이 없으면 false (예전 길)
     */
    static boolean oldFx(ServerLevel level, String id, Vec3 at, Opt o) {
        boolean group = id.equals("sparks");
        if (id.equals("arc")) {
            // 2026-11-17 SkillFx.arc (룬이 바로 부른 휘두른 자국 판 — 예전 slash_thin · heavy · arc 카드): runeFx 의 반달과 같음. 주인 1인칭 카드 (view 2) 는 3인칭 판의
            // 다리가 1인칭 초승달도 그리므로 그리지 않고 됨으로 셈
            if (!(o.get("color") instanceof double[] ag)) return false;
            if (o.num("view", 0) == 2) {
                Lib A = jobLib(ag);
                return A != null && Vfx.hasSheet(A.n("slash_00")) && Vfx.hasSheet(A.n("slashm_00")) && V.len(o.vec("dir", V.UP)) > 1e-6
                        && Math.abs(V.unit(o.vec("dir", V.UP)).y) <= 0.9;
            }
            // 2026-11-17 판이 위를 보는 것 (무간참역의 검은 초승달 — dir 위 · 화면의 기울기 spin): 카메라를 보는 초승달 (wave — 같은 기울기)
            Vec3 ad = o.vec("dir", V.UP);
            String kind = V.len(ad) < 1e-6 || Math.abs(V.unit(ad).y) > 0.9 ? "wave" : "arc";
            if (runeFx(level, kind, at, o, ag)) return true;
            // 2026-11-17 먹빛 판 (빛은 먹빛 INK · 겉 aura 가 진홍): 겉빛으로 칠함 (CI 43982885199 slash_thin aura 10)
            return o.get("aura") instanceof double[] au && runeFx(level, kind, at, o, au);
        }
        if (id.startsWith("rpg:slash_") && id.endsWith("_glow") && !o.truthy("basic")) {
            // 2026-11-17 휘두른 자국 판의 빛 장을 바로 부른 것 (보스 · 몹 — SkillFx.fx("rpg:slash_heavy_r_glow") 한 장): 판 모양 · 좌우를 이름에서 → 같은 반달
            String[] parts = id.substring(10).split("_"); // heavy · r · glow
            if (parts.length != 3 || !(o.get("color") instanceof double[] sg)) return false;
            return runeFx(level, "arc", at, o.with("shape", parts[0], "flip", parts[1].equals("l")), sg);
        }
        if (!group && !id.equals("rpg:arrow_spark") && !id.equals("rpg:shock_ring") && !id.equals("rpg:bash_burst") && !id.equals("rpg:arrow_trail")
                && !id.equals("rpg:bash_ring") && !id.equals("rpg:light_cut") && !id.equals("rpg:slash_x") && !id.equals("rpg:skill_rune")
                && !id.equals("rpg:ground_crack") && !id.equals("rpg:dust") && !id.equals("rpg:tyr_ghostfire") && !id.equals("rpg:ember")
                && !id.equals("rpg:fire_wall") && !id.equals("rpg:fire_wave") && !id.equals("rpg:splash") && !id.equals("rpg:snowfall")
                && !id.equals("rpg:meteor") && !id.equals("rpg:glob") && !id.equals("rpg:boulder") && !id.equals("rpg:ice_spike") && !id.equals("rpg:drop")
                && !id.equals("rpg:breath") && !id.equals("rpg:arrow_shot")) return false;
        if (!(o.get("color") instanceof double[] glow)) return false;
        Lib L = jobLib(glow);
        if (L == null || WyrdKit.pal(L) == null) return false;
        Predicate<ServerPlayer> who = who(o);
        switch (id) {
            case "sparks" -> WyrdKit.sparks(level, L, at, (int) o.num("n", 4), o.num("speed", 3), who);
            case "rpg:arrow_spark" -> WyrdKit.spark(level, L, at, o.vec("dir", V.UP), o.num("speed", 1.5), who);
            case "rpg:arrow_trail" -> WyrdKit.trail(level, L, at, o.vec("dir", V.UP), o.num("size", 0.3), who);
            case "rpg:light_cut" -> {
                // 2026-11-16 빛의 일섬 (예전 rpg:light_cut — 방향으로 누운 가는 줄, 반 길이 size): 그 묶음의 일섬 (PhantomFx.cut — 앞뒤 두 면).
                // 2026-11-17 긴 일섬 (cutl — 룬 계열 · 원소 묶음) 이 있으면 그것 (길어도 줄이 촘촘함)
                String art = Vfx.hasSheet(L.n("cutl")) ? "cutl" : "cut";
                if (!Vfx.hasSheet(L.n(art))) return false;
                Vec3 d = o.vec("dir", V.UP);
                if (V.len(d) < 1e-6) return false;
                double half = Math.max(0.15, o.num("size", 0.5));
                int hold = Math.max(1, (int) Math.round(o.num("life", 0.4) * 20) - 9);
                cut(level, L, V.add(at, V.unit(d), -half), V.add(at, V.unit(d), half), hold, false, who, art);
            }
            case "rpg:slash_x" -> {
                // X 베기 (예전 rpg:slash_x — 카메라를 봄, 반 크기 size): 그 묶음의 X 베기 (2 × 2)
                if (!Vfx.hasSheet(L.n("xslash_00"))) return false;
                xslash(level, L, at, Math.max(0.4, o.num("size", 0.5) * 2.2), o.num("spin", Kit.rand() * 360));
            }
            case "rpg:skill_rune" -> {
                // 땅의 룬 원 (예전 rpg:skill_rune — 땅에 눕힘, 반 크기 size): 그 묶음의 땅 고리 (예고 · 테두리)
                double r = Math.max(0.4, o.num("size", 1) * (1 + o.num("grow", 0)) * 0.9);
                if (!Vfx.hasSheet(L.n("ring_00"))) {
                    // 2026-11-17 땅 고리가 없는 비전 묶음 (마력 룬 마법진 — 글꼴 무게로 뺐음): 별빛 고리 (halo — jhalo 와 같은 틀, 땅에 눕힘 · 되풀이)
                    if (!Vfx.hasSheet(L.n("halo_00"))) return false;
                    double life = Math.max(0.2, o.num("life", 0.6));
                    Opt b = Opt.of("dir", V.UP, "size", r / 0.9, "life", life, "frames", 8.0, "fps", 12.0, "loop", true, "fade", 0.3, "alpha",
                            Math.min(1, o.num("alpha", 0.9)), "spin", o.num("spin", 0), "view", o.num("view", 0), "owner", o.num("owner", 0));
                    return paintedWith(level, PAINT.get("jhalo"), L, 0, V.up(ground(level, at), 0.03), b);
                }
                ring(level, L, ground(level, at), r, 4, Math.max(2, (int) Math.round(o.num("life", 0.6) * 20) - 8), who, Math.min(1, o.num("alpha", 0.9)));
            }
            case "rpg:ground_crack" -> {
                // 땅 금 (예전 rpg:ground_crack — 땅에 눕힘): 그 묶음의 땅 자국
                if (!Vfx.hasSheet(L.n("crack_s"))) return false;
                crackS(level, L, ground(level, at), Math.max(0.6, o.num("size", 1) * 2), who);
            }
            case "rpg:fire_wall" -> {
                // 2026-11-17 서 있는 불꽃 하나 (예전 rpg:fire_wall — 세로축 빌보드 · 밑동 at · 높이 size): 묶음의 불꽃 혀 (flame — 용 · 원소 묶음), 한 번 솟았다 사그라듦
                if (!Vfx.hasSheet(L.n("flame"))) return false;
                int frames = Vfx.sheet(L.n("flame")).frames();
                flame(level, L, at, Math.max(0.4, o.num("size", 1)), Math.max(1, (int) Math.round(o.num("life", 0.6) * 20 / frames)));
            }
            case "rpg:fire_wave" -> {
                // 2026-11-17 퍼지는 불꽃 고리 (예전 rpg:fire_wave — life 초 동안 반지름 speed × 시간 (0.5 부터) 의 둘레에 높이 size 불꽃이 늘어섬 · 수르트): 묶음의 솟는
                // 바닐라 알갱이 고리를 2틱마다 (WyrdKit.ringRise — 둘레 길이에 맞춘 수, 48 까지)
                if (WyrdKit.pal(L) == null) return false;
                double speed = o.num("speed", 10);
                double h = Math.max(0.4, o.num("size", 1));
                int ticks = Math.max(2, (int) Math.round(o.num("life", 1) * 20));
                for (int t = 0; t < ticks; t += 2) {
                    double r = Math.max(0.5, speed * t / 20.0);
                    int n = (int) Math.max(8, Math.min(48, r * 1.4));
                    mmorpg.fabric.Tasks.later(t, () -> WyrdKit.ringRise(level, L, at, r, n, h, who));
                }
            }
            case "rpg:splash" -> {
                // 2026-11-17 물보라 · 튐 (예전 rpg:splash — 세로축 빌보드 한 장 · 반 크기 size): 묶음의 작은 터짐 (boom_s — 물 묶음이 없어 바다 · 얼음 · 독 빛의 묶음)
                if (!Vfx.hasSheet(L.n("boom_s"))) return false;
                double size = Math.max(0.5, o.num("size", 1) * 2);
                Vfx.Fx fx = Vfx.at(level, at, 20, who);
                single(fx, L.n("boom_s"), at, size, Kit.rand() * 30 - 15, 1, false);
                WyrdKit.burst(level, L, at, size, who);
            }
            case "rpg:meteor" -> {
                // 2026-11-17 날아오는 운석 (예전 rpg:meteor — 가는 쪽을 보는 판 · 길이 size · dir 쪽 speed 칸/초, 수르트): 묶음의 불꽃 꼬리 (comet — JobFx.comet 과 같은 틀,
                // 길이 = 반 크기 × 2)
                if (!Vfx.hasSheet(L.n("comet"))) return false;
                Vec3 d = o.vec("dir", V.DOWN);
                if (V.len(d) < 1e-6) return false;
                Opt b = Opt.of("dir", V.unit(d), "speed", o.num("speed", 0), "size", Math.max(0.3, o.num("size", 1) * 0.5), "life", Math.max(0.1, o.num("life", 1)),
                        "frames", 4.0, "fps", 16.0, "loop", true, "alpha", Math.min(1, o.num("alpha", 1)), "view", o.num("view", 0), "owner", o.num("owner", 0));
                return paintedWith(level, PAINT.get("jcomet2"), L, 0, at, b);
            }
            case "rpg:arrow_shot" -> {
                // 2026-11-17 빛 줄기 (예전 rpg:arrow_shot — 가는 쪽을 보는 줄 · 반 길이 size): 멈춘 세로 줄 (표식 위 빛기둥) = 스스로 빛나는 바닐라 점 기둥
                // (WyrdKit.beam — 빛살 다리와 같음), 날아가는 줄 = 묶음의 돌진 줄기 (dash — 같은 길 · 빠르기). 빛 · 심지 두 줄 (RuneKit.flyStreak) 은 flystreak 다리가 한 번에
                Vec3 d = o.vec("dir", V.UP);
                if (V.len(d) < 1e-6) return false;
                Vec3 u = V.unit(d);
                double half = Math.max(0.1, o.num("size", 0.5));
                double speed = o.num("speed", 0);
                if (speed <= 0.01) {
                    if (half * 2 < 0.6) return false;
                    WyrdKit.beam(level, L, V.add(at, u, half), V.add(at, u, -half), who);
                    return true;
                }
                if (!Vfx.hasSheet(L.n("dash"))) return false;
                double life = Math.max(0.05, o.num("life", 0.3));
                int frames = Vfx.sheet(L.n("dash")).frames();
                return paintedWith(level, PAINT.get("jdash2"), L, 0, at, Opt.of("dir", u, "speed", speed, "size", half, "life", life, "frames", (double) frames,
                        "fps", frames / life, "alpha", Math.min(1, o.num("alpha", 1)), "view", o.num("view", 0), "owner", o.num("owner", 0)));
            }
            case "rpg:breath" -> {
                // 2026-11-17 숨결 뭉게 하나 (예전 rpg:breath — dir 쪽 speed 칸/초 · 끌림 1.1 · 반 크기 size 의 0.45 → 1.6 배로 커지며 옅어짐 · 이미르 서리 숨결 · 니드호그):
                // 묶음의 뭉게 연기 한 장 (smoke — 같은 길 · 크기, 2틱마다 옮김)
                if (!Vfx.hasSheet(L.n("smoke"))) return false;
                double size = Math.max(0.3, o.num("size", 1) * 2);
                double life = Math.max(0.2, o.num("life", 1));
                double speed = o.num("speed", 0);
                Vec3 d = V.len(o.vec("dir", V.ZERO)) < 1e-6 ? V.ZERO : V.unit(o.vec("dir", V.ZERO));
                int ticks = Math.max(2, (int) Math.round(life * 20));
                Vfx.Fx fx = Vfx.at(level, at, ticks + 2, who);
                String name = L.n("smoke");
                int frames = Vfx.sheet(name).frames();
                Vfx.Sprite sp = fx.sprite(name, at);
                sp.size(size * 0.45);
                int f0 = (int) (Kit.rand() * frames);
                sp.play(0, 0, frames, 3, true);
                sp.frame(f0);
                for (int t = 2; t <= ticks; t += 2) {
                    double sec = t / 20.0;
                    Vec3 p = V.add(at, d, speed / 1.1 * (1 - Math.exp(-1.1 * sec)));
                    double sz = size * (0.45 + 1.15 * t / (double) ticks);
                    fx.on(t - 2, () -> sp.to(p, sz, 2));
                }
                sp.fade(ticks / 2, ticks - ticks / 2);
            }
            case "rpg:ice_spike" -> {
                // 2026-11-17 솟는 얼음 가시 (예전 rpg:ice_spike — 세로축 빌보드 · 밑동 at · 높이 size, 솟았다 머묾 · 이미르 · 헬): 묶음의 솟는 가시 (spike — 솟았다 가라앉는
                // 여덟 칸을 life 동안 · 서리 · 혼 · 곰 묶음)
                if (!Vfx.hasSheet(L.n("spike"))) return false;
                double size = Math.max(0.4, o.num("size", 1));
                double life = Math.max(0.2, o.num("life", 1));
                int frames = Vfx.sheet(L.n("spike")).frames();
                Opt b = Opt.of("size", size * 0.5, "life", life, "frames", (double) frames, "fps", frames / life, "fade", 0.15, "alpha", Math.min(1, o.num("alpha", 1)),
                        "view", o.num("view", 0), "owner", o.num("owner", 0));
                return paintedWith(level, PAINT.get("jspike"), L, 0, V.up(at, size * 0.5), b);
            }
            case "rpg:glob", "rpg:boulder", "rpg:drop" -> {
                // 2026-11-17 던진 덩이 (예전 rpg:glob 용암 덩이 — 반 크기 size · dir 쪽 speed 칸/초 · 아래로 20 · 수르트 / rpg:boulder 돌덩이 — 같은 틀 / rpg:drop 방울 —
                // 아래로 30): 묶음의 불꽃 덩이 (fire) · 조각 (shard) · 불씨 (ember) 한 장이 같은 포물선으로
                String art = id.equals("rpg:glob") ? "fire" : id.equals("rpg:boulder") ? "shard" : L.ember();
                // 돌덩이는 늘 곰 묶음의 흙 조각 (PhantomFx.rocks 와 같음 — 맥락의 묶음 빛으로 칠한 조각은 돌로 보이지 않음)
                Lib LL = id.equals("rpg:boulder") && has(BEAR, "shard") ? BEAR : L;
                if (!Vfx.hasSheet(LL.n(art))) return false;
                Vec3 d = o.vec("dir", V.UP);
                Vec3 vel = V.len(d) < 1e-6 ? V.ZERO : V.scale(V.unit(d), o.num("speed", 0));
                lob(level, LL, art, at, vel, id.equals("rpg:drop") ? 30 : 20, Math.max(0.1, o.num("life", 1)), Math.max(0.2, o.num("size", 0.5) * 2), who);
            }
            case "rpg:snowfall" -> {
                // 2026-11-17 눈보라 (예전 rpg:snowfall — life 초 동안 초당 30 개를 size × 3 × size 상자에서 dir 쪽 speed 칸/초 · 이미르): 묶음의 날리는 알갱이 (서리 = 눈송이)
                double size = Math.max(0.5, o.num("size", 4));
                Vec3 vel = V.scale(V.unit(o.vec("dir", V.DOWN)), o.num("speed", 3) / 20.0);
                int ticks = Math.max(1, (int) Math.round(o.num("life", 1) * 20));
                for (int t = 0; t < ticks; t += 4) mmorpg.fabric.Tasks.later(t, () -> WyrdKit.drift(level, L, at, size * 0.5, 1.5, 6, vel, who));
            }
            case "rpg:ember" -> {
                // 2026-11-17 솟는 불씨 (예전 rpg:ember — life 초 동안 초당 10 + 4 × size 개 (90 까지) 를 size × 1 × size 상자에서 위로 · 수르트 왕관 · 분노 · 용암):
                // 묶음의 솟는 바닐라 알갱이를 4틱마다 (WyrdKit.rise)
                double size = Math.max(0.2, o.num("size", 1));
                double life = Math.max(0.1, o.num("life", 1));
                double speed = o.num("speed", 1);
                int per = Math.max(1, (int) Math.round(Math.min(90, 10 + size * 4) / 5.0));
                int ticks = Math.max(1, (int) Math.round(life * 20));
                for (int t = 0; t < ticks; t += 4) mmorpg.fabric.Tasks.later(t, () -> WyrdKit.rise(level, L, at, size * 0.5, per, speed, who));
            }
            case "rpg:tyr_ghostfire" -> {
                // 2026-11-17 녹색 무덤불 (네크로맨서 · 망자 선원 — 예전 tyr_ghostfire: 세로축 빌보드 · 밑동 at · 높이 size · 너비 size/2, CI 43979933145 블라이트 136):
                // 묶음의 혼불 (soul — 불꽃 밑동이 칸 아래에서 0.29 · 불꽃 높이는 칸의 0.62)
                if (!Vfx.hasSheet(L.n("soul"))) return false;
                double H = Math.max(0.3, o.num("size", 1)) / 0.62;
                double life = Math.max(0.15, o.num("life", 0.5));
                int frames = Vfx.sheet(L.n("soul")).frames();
                Opt b = Opt.of("size", H / 2, "life", life, "frames", (double) frames, "fps", 12.0, "loop", true, "phase", Math.floor(Kit.rand() * frames), "fade",
                        0.35, "alpha", Math.min(1, o.num("alpha", 0.9)), "view", o.num("view", 0), "owner", o.num("owner", 0));
                rfx(level, L.n("soul"), 1, 0, V.up(at, 0.21 * H), b, VERT, 1);
            }
            case "rpg:dust" -> {
                // 흙먼지 (예전 rpg:dust): 그 묶음의 흙먼지 (꽂힌 자리 먼지)
                if (!Vfx.hasSheet(L.n("dust"))) return false;
                dust(level, L, ground(level, at), Math.max(0.5, o.num("size", 1) * 1.6));
            }
            case "rpg:bash_ring" -> {
                // 2026-11-16 정한 쪽을 보는 고리 (SkillFx.ring — 자기 강화 표시 · 눈앞에서 앞으로 퍼짐): 칠한 작은 충격파 (shock_s — 면이 dir, 앞뒤 두 면 · 같은 움직임)
                if (!Vfx.hasSheet(L.n("shock_s"))) return false;
                int frames = Vfx.sheet(L.n("shock_s")).frames();
                double life = Math.max(0.1, o.num("life", 0.4));
                // 예전 고리는 반 크기 size × (1 + 1.4 × 시간 몫) 까지 (rpg:bash_ring) — 칠한 고리는 그림 안에서 퍼지므로 끝 크기로
                Opt b = Opt.of("dir", o.vec("dir", V.UP), "speed", o.num("speed", 0), "size", o.num("size", 1) * 2.4, "life", life, "frames", (double) frames, "fps",
                        frames / life, "fade", 0.3, "alpha", o.num("alpha", 0.9), "view", o.num("view", 0), "owner", o.num("owner", 0));
                rfx(level, L.n("shock_s"), 1, 0, at, b, FACE_DIR, 1);
            }
            case "rpg:shock_ring" -> {
                if (!Vfx.hasSheet(L.n("shock_00"))) {
                    // 2026-11-17 큰 충격파 (2 × 2) 가 없는 묶음 (혼 — 네크로맨서): 작은 충격파 한 장 (shock_s — 땅에 눕힘 · 같은 끝 지름). has(L, "shock") 는
                    // 앞이 같은 shock_s 도 세어 2 × 2 를 찾다 멈출 수 있어 꼭 그대로 확인
                    if (!Vfx.hasSheet(L.n("shock_s"))) return false;
                    int frames = Vfx.sheet(L.n("shock_s")).frames();
                    double life = Math.max(0.1, o.num("life", 0.4));
                    Opt b = Opt.of("dir", V.UP, "speed", 0.0, "size", Math.max(0.6, o.num("size", 1) * 4.6) / 2, "life", life, "frames", (double) frames, "fps",
                            frames / life, "fade", 0.3, "alpha", Math.min(1, o.num("alpha", 0.9)), "view", o.num("view", 0), "owner", o.num("owner", 0));
                    rfx(level, L.n("shock_s"), 1, 0, ground(level, at), b, FACE_DIR, 1);
                    return true;
                }
                // 2026-11-16 예전 고리는 반 크기 size × (1 + 1.6 × 시간 몫) 까지 퍼짐 (rpg:shock_ring) → 끝 지름 약 4.6 × size (그림의 테 0.9). 1판 (× 2) 은 고리가 예전의
                // 0.4배라 룬 영상에서 영역 고리 (zonePulse) 가 거의 보이지 않았음 (CI 43915612802 암살 · 검 영웅)
                shock(level, L, V.up(at, -0.12), Math.max(0.6, o.num("size", 1) * 4.6), o.num("life", 0.4) >= 0.5 ? 2 : 1, who);
            }
            default -> {
                if (!has(L, "flash")) return false;
                flash(level, L, at, Math.max(0.4, o.num("size", 1) * 3.0), who); // 예전 터짐은 반 크기 size × 1.5 까지 (rpg:bash_burst)
            }
        }
        return true;
    }

    /** 2026-11-16 직업 스킬 · 태세 동안의 칠할 묶음 맥락 (Tasks.paint — JobSkills.press · JobTraits 태세 연출이 정함): 직업 sp 의 묶음 (그리지 않은 직업은 null) */
    static Lib ctxOf(ServerPlayer sp) {
        return sp == null ? null : lib(JobSystem.job(sp));
    }

    /**
     * 2026-11-16 빛 glow 로 부른 예전 불티 · 공용 룬 그림을 칠할 묶음: 칠한 빛 배열 (painter) → 직업 스킬 맥락 (Tasks.paint — 룬을 쓰는 동안은 빼고) 이면 원소 빛은 그 원소의
     * 묶음, 다른 빛 (속빛 · 겉빛 · 테 빛) 은 그 직업 묶음. 없으면 null (예전 길)
     */
    static Lib jobLib(double[] glow) {
        String tag = mmorpg.fabric.Tasks.tag();
        if (tag != null) return tagLib(tag, glow); // 2026-11-16 룬을 쓰는 동안 (룬 계열 묶음) · 2026-11-17 보스 싸움 (보스 빛 묶음)
        Lib L = painter(glow, false);
        if (L != null) return L;
        if (!(mmorpg.fabric.Tasks.paint() instanceof Lib K)) return null;
        Lib E = elementOf(glow);
        return E != null ? E : K;
    }

    /**
     * 2026-11-16 룬 계열 → 빛이 가까운 칠한 묶음 (룬 위르드식 1판 — 공용 그림: 불티 · 충격 고리 · 터짐 · 섬광 · 반짝임 · 빛살 · 금 · 문양 · 날아가는 꼬리): 힘 = 분노
     * (주황 · 진홍), 민첩 · 정령 · 활 = 질풍 (박하빛), 마력 = 비전 (푸른빛), 도끼 = 피, 암살 = 그림자 (자홍), 검 = 사신 (청록), 체력 = 꽃잎 (분홍), 무간참역 = 아가리 (진홍 ·
     * 공허), 대지 가르기 = 곰 (호박빛), 세계수 = 성광 (금빛 · 연둣빛), 심연 = 폭군 (보랏빛 · 핏빛), 만년빙하 = 서리. 숨긴 계열은 예전 그림 (null)
     */
    static Lib runeLib(String fam) {
        if (!JobSystem.RUNE_WYRD || fam == null) return null;
        return switch (fam) {
            case "str" -> RAGE;
            case "dex", "spirit", "bow" -> GALE;
            case "int" -> ARCANE;
            case "axe" -> BLOOD;
            case "assassin" -> SHADOW;
            case "blade" -> REAP;
            case "vit" -> PETALS;
            case "endless" -> MAW;
            case "terra" -> BEAR;
            case "yggdrasil" -> HOLY;
            case "abyss" -> TYRANT;
            case "glacier" -> FROST;
            default -> null;
        };
    }

    /**
     * 룬 (꼬리표 tag = "계열:등급") 이 빛 glow 로 부른 것을 칠할 묶음: 계열 빛 · 속빛 · 흰빛에 가까우면 계열 묶음, 원소 빛이면 그 원소의 묶음, 다른 빛 (바위 · 불 · 얼음처럼
     * 뜻이 있는 빛) 은 null (예전 그림 — 계열 빛으로 바꾸면 뜻이 사라짐)
     */
    static Lib runeColorLib(String tag, double[] glow) {
        int c = tag.indexOf(':');
        String fam = c < 0 ? tag : tag.substring(0, c);
        Lib L = runeLib(fam);
        if (L == null || glow == null || glow.length < 3) return null;
        double[][] col = RuneKit.colorOf(fam);
        if (near(glow, col[0]) || near(glow, col[1]) || glow[0] + glow[1] + glow[2] > 2.7) return L;
        // 2026-11-17 묶음 빛과 같은 색조 (40° 안) — 한정 룬 (심연 · 무간참역 · 대지 · 빙하 · 세계수) 은 RUNE.color 에 계열 빛이 없어 흰빛만 맞았음
        // (CI 43982885199 심연 자홍 · 와인빛 불티 · 반짝임 · 문양 · 마법진 · 가시)
        if (libNear(glow, L)) return L;
        Lib E = elementOf(glow);
        if (E != null) return JobSystem.RUNE_WYRD ? E : null;
        return semanticLib(glow);
    }

    /**
     * 2026-11-17 뜻이 있는 빛의 묶음 (계열 빛이 아닌 불 · 얼음 · 흙 빛 — 뜻은 그대로 두고 그 뜻의 칠한 묶음으로): 불빛 (색조 0 ~ 48° · 채도 0.55 · 밝기 0.7 넘게) = 원소
     * (금빛 주황 불꽃), 얼음빛 (180 ~ 215° · 채도 0.2 ~ 0.75 · 밝기 0.75 넘게) = 서리, 흙빛 (15 ~ 45° · 채도 0.25 ~ 0.65 · 밝기 0.35 ~ 0.75) = 곰. 그 밖은 null (예전 그림).
     * CI 43982885199: 정령 초월의 불벽 · 금빛 불티 · 힘 초월의 돌덩이가 계열 빛이 아니라 예전 그림이었음
     */
    static Lib semanticLib(double[] glow) {
        if (!JobSystem.RUNE_WYRD || glow == null || glow.length < 3) return null;
        double[] h = hsv(glow);
        if (h[0] <= 48 && h[1] > 0.55 && h[2] > 0.7) return ELEMENT;
        // 옅은 금빛 (색조 38 ~ 62° · 채도 0.3 ~ 0.55 · 밝기 0.85 넘게 — 마력 초월의 금빛 불티 · 일섬, CI 43987516768 32) = 성광
        if (h[0] >= 38 && h[0] <= 62 && h[1] >= 0.3 && h[1] <= 0.55 && h[2] > 0.85) return HOLY;
        if (h[0] >= 180 && h[0] <= 215 && h[1] >= 0.2 && h[1] <= 0.75 && h[2] > 0.75) return FROST;
        if (h[0] >= 15 && h[0] <= 45 && h[1] >= 0.25 && h[1] <= 0.65 && h[2] >= 0.35 && h[2] <= 0.75) return BEAR;
        return null;
    }

    /** 2026-11-17 꼬리표 맥락의 칠할 묶음: 보스 싸움 ("boss:key") 이면 bossLib, 아니면 룬 ("계열:등급") */
    static Lib tagLib(String tag, double[] glow) {
        return tag.startsWith("boss:") ? bossLib(tag.substring(5), glow) : runeColorLib(tag, glow);
    }

    /**
     * 2026-11-17 보스마다 칠할 묶음들 (앞 = 대표 — 흰빛): 보스가 부르는 빛 (상수 · 설정의 rgb · 섞은 빛) 을 색조가 가장 가까운 묶음으로. 헬 = 혼 (푸른 혼불) · 금빛,
     * 공허 기사 = 그림자 · 폭군 (보랏빛) · 환영 (분홍), 요르문간드 = 질풍 (청록 바다) · 혼 (독) · 폭군 · 분노 · 피, 크라켄 = 서리 (바다) · 폭군 (먹물) · 성광 · 혼 (회복),
     * 니드호그 = 폭군 · 혼 · 피 · 분노, 허수아비 킹 = 분노 (호박 불) · 그림자 (까마귀) · 성광, 전갈 = 곰 (모래) · 혼 (독) · 피, 스콜과 하티 = 용 (해) · 성광 · 서리 (달) ·
     * 폭군 (노을), 수르트 = 용 (불) · 성광 · 서리 · 피, 거대 웜 = 곰 (흙) · 분노 · 피 · 서리 (소리), 이미르 = 서리 · 용 · 피
     */
    private static java.util.Map<String, Lib[]> bossLibs;

    static Lib bossLib(String key, double[] glow) {
        if (!JobSystem.BOSS_WYRD || glow == null || glow.length < 3) return null;
        if (bossLibs == null) {
            java.util.Map<String, Lib[]> m = new java.util.HashMap<>();
            m.put("hel", new Lib[] {SOULREAP, HOLY});
            m.put("void_knight", new Lib[] {SHADOW, TYRANT, ILLUSION});
            m.put("jormungandr", new Lib[] {GALE, SOUL, TYRANT, RAGE, BLOOD});
            m.put("kraken", new Lib[] {FROST, TYRANT, HOLY, SOUL});
            m.put("nidhogg", new Lib[] {TYRANT, SOUL, BLOOD, RAGE});
            m.put("scarecrow_king", new Lib[] {RAGE, SHADOW, HOLY});
            m.put("scorpions", new Lib[] {BEAR, SOUL, BLOOD});
            m.put("skoll_hati", new Lib[] {DRAGONL, HOLY, FROST, TYRANT});
            m.put("surtr", new Lib[] {DRAGONL, HOLY, FROST, BLOOD});
            m.put("great_worm", new Lib[] {BEAR, RAGE, BLOOD, FROST});
            m.put("ymir", new Lib[] {FROST, DRAGONL, BLOOD});
            bossLibs = m;
        }
        Lib[] pal = bossLibs.get(key);
        if (pal == null) return null;
        Lib E = elementOf(glow);
        if (E != null) return E;
        double[] h = hsv(glow);
        if (h[1] < 0.3 && h[2] > 0.85) return pal[0]; // 흰빛 · 옅은 빛 (거품 · 눈 — CI 43982885199 요르문간드 물보라 · 이미르 흙먼지) → 대표 묶음
        if (h[1] < 0.35 || h[2] < 0.55) return null; // 잿빛 · 흙먼지 · 먹물 · 뼈 (뜻이 있는 빛 — 예전 그림)
        Lib best = null;
        double bd = 1e9;
        for (Lib L : pal) {
            double[] lh = hsv(new double[] {(L.glow() >> 16 & 255) / 255.0, (L.glow() >> 8 & 255) / 255.0, (L.glow() & 255) / 255.0});
            double d = Math.abs(h[0] - lh[0]);
            d = Math.min(d, 360 - d);
            if (d < bd) {
                bd = d;
                best = L;
            }
        }
        return bd <= 36 ? best : null; // 묶음 빛과 먼 빛 (독 연둣빛 · 쓸개 빛 …) 은 예전 그림
    }

    /** 2026-11-17 묶음 L 의 두 빛 (glow · trace) 중 하나와 색조 40° 안 (채도 0.3 · 밝기 0.2 넘게) */
    static boolean libNear(double[] glow, Lib L) {
        double[] h = hsv(glow);
        if (h[1] < 0.3 || h[2] < 0.2) return false;
        for (int c : new int[] {L.glow(), L.trace()}) {
            double[] lh = hsv(new double[] {(c >> 16 & 255) / 255.0, (c >> 8 & 255) / 255.0, (c & 255) / 255.0});
            if (lh[1] < 0.25) continue;
            double d = Math.abs(h[0] - lh[0]);
            if (Math.min(d, 360 - d) <= 40) return true;
        }
        return false;
    }

    /** 가까운 빛: RGB 거리 0.3 안, 또는 같은 색조 (22° 안 · 둘 다 채도 0.25 넘게 — 계열 빛을 어둡게 · 밝게 섞은 빛) */
    private static boolean near(double[] a, double[] b) {
        double dr = a[0] - b[0], dg = a[1] - b[1], db = a[2] - b[2];
        if (dr * dr + dg * dg + db * db < 0.09) return true;
        double[] ha = hsv(a), hb = hsv(b);
        if (ha[1] < 0.25 || hb[1] < 0.25 || ha[2] < 0.2) return false;
        double dh = Math.abs(ha[0] - hb[0]);
        return Math.min(dh, 360 - dh) < 22;
    }

    private static double[] hsv(double[] c) {
        double r = c[0], g = c[1], b = c[2];
        double mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b)), d = mx - mn;
        double h = d < 1e-9 ? 0 : mx == r ? 60 * (((g - b) / d) % 6) : mx == g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
        return new double[] {h < 0 ? h + 360 : h, mx < 1e-9 ? 0 : d / mx, mx};
    }

    /**
     * 2026-11-16 RuneKit.painted (공용 룬 그림의 다리 — JobSystem.init 이 등록): 직업 스킬이 부른 예전 rfx 를 그 묶음의 그림으로 — 반짝임 (prop 의 glint 칸 · 손의 검빛
     * sword) = 칠한 섬광 + 바닐라 반짝임, 섬광 (flare) = 칠한 섬광, 빛살 (rays — 세운 빛줄기) = 칠한 빛줄기 (beam 을 세움), 충격 고리 (nova) = 칠한 충격파, 빛나는 금 (crack)
     * = 칠한 땅 자국, 영역 문양 (sigil) = 칠한 땅 고리. 땅에 눕힌 것만 (dir 이 위). 묶음에 그림이 없으면 false (예전 길)
     */
    static boolean runeFx(ServerLevel level, String name, Vec3 at, Opt o, double[] glow) {
        if (name.equals("fissure")) return fissure(level, at, o);
        Paint rp = RUNE_PAINT.get(name);
        if (rp != null) { // 2026-11-16 계열 대표 그림 (예전 rfx 와 같은 자리 · 움직임 — 묶음에 그 그림이 있을 때만)
            Lib L = jobLib(glow);
            if (L == null || !Vfx.hasSheet(L.n(rp.art()))) return false;
            int cell = (int) Math.round(o.num("cell", 0)) - RuneKit.RFX.getOrDefault(name, new int[] {0, 1})[0];
            if (name.equals("thorn")) {
                // 가시는 칸 고르기 (예전 손 셋) 대신 솟았다 가라앉는 칸을 다 — 밑동 at 에서 높이 2 × size
                int n = Vfx.sheet(L.n(rp.art())).frames();
                double life = Math.max(0.1, o.num("life", 0.5));
                double size = Math.max(0.2, o.num("size", 0.5));
                return paintedWith(level, rp, L, 0, V.up(at, size), o.with("frames", (double) n, "fps", n / life, "grow", 0.0));
            }
            if (name.equals("fountain")) {
                // 2026-11-17 불기둥 (pyre — 붙음 · 솟음 · 타오름 · 무너짐 · 재 연기 여덟 칸) 은 예전 분수 (비슷한 칸 넷을 돌림) 와 달리 처음과 끝이 있어 life 동안 다
                int n = Vfx.sheet(L.n(rp.art())).frames();
                return paintedWith(level, rp, L, 0, at, o.with("frames", (double) n, "fps", n / Math.max(0.1, o.num("life", 0.5))));
            }
            return paintedWith(level, rp, L, Math.max(0, cell), at, o);
        }
        switch (name) {
            // 2026-11-17 얼음 마법진 (RuneGlacier.frostSigil — rfx_frost, 땅에 눕힘 · 반지름 size: 만년빙하 4) 은 문양과 같은 길 (묶음의 땅 고리)
            case "prop", "sword", "flare", "rays", "nova", "crack", "sigil", "frost", "rockburst", "arc", "wavecard", "wave", "shade", "hitmark", "flystreak" -> {
            }
            default -> {
                return false;
            }
        }
        if (name.equals("rockburst")) { // 튀는 돌 (RuneKit.rockBurst — 돌 빛): 칠한 직업 · 룬 맥락이면 곰 묶음 도트 조각
            String tag = mmorpg.fabric.Tasks.tag();
            int c = tag == null ? -1 : tag.indexOf(':');
            boolean on = tag != null ? runeLib(c < 0 ? tag : tag.substring(0, c)) != null : mmorpg.fabric.Tasks.paint() != null;
            if (!on || !Vfx.hasSheet(BEAR.n("shard"))) return false;
            rocks(level, at, (int) o.num("n", 6), o.num("speed", 4), o.num("size", 0.2));
            return true;
        }
        Lib L = jobLib(glow);
        if (L == null) return false;
        Predicate<ServerPlayer> who = who(o);
        double size = o.num("size", 0.5);
        double life = Math.max(0.05, o.num("life", 0.4));
        double grow = o.num("grow", 0);
        Vec3 dir = o.vec("dir", V.UP);
        boolean flat = V.len(dir) < 1e-6 || V.unit(dir).y > 0.97;
        Vec3 vel = o.has("vel") ? o.vec("vel", V.ZERO) : null;
        switch (name) {
            case "prop" -> {
                if ((int) Math.round(o.num("cell", -1)) != RuneKit.RFX.get("glint")[0] || !Vfx.hasSheet(L.n("flash"))) return false;
                glint(level, L, at, size, life, vel, who);
            }
            case "sword" -> {
                if (!Vfx.hasSheet(L.n("flash"))) return false;
                glint(level, L, at, size * 0.6, life, vel, who);
            }
            case "flare" -> {
                if (!Vfx.hasSheet(L.n("flash"))) return false;
                flash(level, L, at, Math.max(0.4, size * 2 * (1 + grow * 0.5)), who);
            }
            case "rays" -> {
                // 예전 빛살 (세로 빌보드 · 밑동 at · 높이 2 × size, 너비 = 높이의 1/8) → 온 높이를 따라 스스로 빛나는 바닐라 점 (WyrdKit.beam — 위에서 땅으로 · 끝 반짝임).
                // 칠한 빛줄기 (beam) 를 세운 1판은 굵기가 길이의 1/4 이라 둘레의 빛살이 넓은 판자처럼 보였음 (CI 43903024474 프리스트 궁)
                if (WyrdKit.pal(L) == null) return false;
                double h = size * 2 * (1 + grow * 0.5);
                if (h > 0.6) WyrdKit.beam(level, L, V.up(at, h), at, who);
            }
            case "nova" -> {
                if (flat) {
                    if (!Vfx.hasSheet(L.n("shock_00"))) return false;
                    shock(level, L, ground(level, at), Math.max(0.6, 2 * size * (1 + grow)), life >= 0.5 ? 2 : 1, who);
                } else {
                    // 2026-11-17 세운 충격 고리 (면이 dir — 정한 쪽으로 퍼짐): 칠한 작은 충격파 (shock_s — bash_ring 다리와 같은 틀, 앞뒤 두 면)
                    if (!Vfx.hasSheet(L.n("shock_s"))) return false;
                    int frames = Vfx.sheet(L.n("shock_s")).frames();
                    Opt b = Opt.of("dir", V.unit(dir), "speed", 0.0, "size", Math.max(0.6, 2 * size * (1 + grow)), "life", life, "frames", (double) frames,
                            "fps", frames / life, "fade", 0.3, "alpha", Math.min(1, o.num("alpha", 0.9)), "view", o.num("view", 0), "owner", o.num("owner", 0));
                    rfx(level, L.n("shock_s"), 1, 0, at, b, FACE_DIR, 1);
                }
            }
            case "crack" -> {
                if (!Vfx.hasSheet(L.n("crack_s"))) return false;
                if (flat) {
                    crackS(level, L, ground(level, at), size * 2, who);
                } else {
                    // 2026-11-17 세운 금 (하늘이 갈라짐 — RuneKit.skyPlate: 면이 쓴 사람의 눈 쪽, 마력 초월 메테오 · 암살 · 힘 · 무간 초월의 하늘): 칠한 땅 금 한 장을
                    // 그 쪽으로 세움 (같은 자리 · 반 크기 · 자람 · 옅어짐 — 밝은 금이 식어 가는 칸을 life 동안)
                    int frames = Vfx.sheet(L.n("crack_s")).frames();
                    Opt b = Opt.of("dir", V.unit(dir), "speed", 0.0, "size", size, "life", life, "grow", grow, "frames", (double) frames, "fps", frames / life, "fade",
                            o.num("fade", 0.3), "alpha", Math.min(1, o.num("alpha", 1)), "view", o.num("view", 0), "owner", o.num("owner", 0));
                    rfx(level, L.n("crack_s"), 1, 0, at, b, FACE_DIR, 1);
                }
            }
            case "arc", "wavecard" -> {
                // 휘두른 자국 판 (RuneKit.arc — 판 모양 arc · heavy · thin, 불룩한 날 = dir) · 날아가는 검기 판 (waveCard — 눕힌 판): 그 묶음의 반달 (moon — JobFx.slash 의 3인칭
                // 반달과 같은 틀: 가로 휘두름은 눕고 보는 눈 쪽으로 기욺) 이 dir 쪽으로 날아감. 기울기 = 판의 돌림 (arc: flip ? 180 + β − spin : −(β + spin) · waveCard: roll)
                // 의 반대. 반지름 = 판 크기 (예전 판 모델 반너비 arc 1.06 · thin 0.56 × ARC_CARD 0.7 · 날아가는 판 1) 의 0.9. 주인의 1인칭은 카메라를 보는 칼빛 (예전 원래 파티클 자리)
                if (!Vfx.hasSheet(L.n("slash_00")) || !Vfx.hasSheet(L.n("slashm_00"))) return false;
                if (V.len(dir) < 1e-6 || Math.abs(V.unit(dir).y) > 0.9) return false;
                Vec3 d = V.unit(dir);
                boolean arc = name.equals("arc");
                String shape = o.str("shape", "arc");
                double beta = !arc ? 0 : shape.equals("heavy") ? 90 : shape.equals("thin") ? 45 : 0;
                double spin = arc ? o.num("spin", 0) : -o.num("roll", 0);
                boolean flip = arc && o.truthy("flip");
                double tilt = flip ? 180 + spin - beta : spin + beta;
                double R = size * (!arc ? 0.85 : shape.equals("thin") ? 0.36 : 0.68);
                double speed = arc ? o.num("speed", 0) / 5.0 * (1 - Math.exp(-5 * life)) / life : o.num("speed", 0);
                int own = mmorpg.fabric.Tasks.owner();
                ServerPlayer owner = own != 0 && level.getEntity(own) instanceof ServerPlayer sp ? sp : null;
                Moon m = moon(at, d, tilt, R, viewEye(owner, level, at), MOON_LIFT, 0).centered(at);
                m = m.above(ground(level, at).y);
                double alpha = Math.min(1, o.num("alpha", 1));
                moonFly(level, L, m, d, speed, life, owner != null ? hide1p(owner) : who, alpha);
                if (owner != null) wave(level, L, at, d, speed, R / SLASH_R, spin + beta, life, only1p(owner), alpha);
            }
            case "flystreak" -> {
                // 2026-11-17 날아가는 빛 (RuneKit.flyStreak — 예전 arrow_shot 빛 · 심지 두 줄): 묶음의 돌진 줄기 한 장 (dash — 같은 길 · 빠르기 · 반 길이 size)
                if (!Vfx.hasSheet(L.n("dash")) || V.len(dir) < 1e-6) return false;
                int frames = Vfx.sheet(L.n("dash")).frames();
                return paintedWith(level, PAINT.get("jdash2"), L, 0, at, Opt.of("dir", V.unit(dir), "speed", o.num("speed", 0), "size", Math.max(0.1, size), "life", life,
                        "frames", (double) frames, "fps", frames / life, "alpha", 1.0));
            }
            case "hitmark" -> {
                // 룬 맞은 자국 (RuneBoost.mark — 예전 rfx_jhits 계열 줄 + rfx_jburst2 + 색 불티): 그 묶음의 X 베기 + 섬광 + 불씨 + 바닐라 맞음 겹 (PhantomFx.hit 과 같은 틀)
                if (!Vfx.hasSheet(L.n("xslash_00")) || !Vfx.hasSheet(L.n("flash"))) return false;
                double power = o.num("power", 1);
                WyrdKit.enter();
                try {
                    xslash(level, L, at, size * 1.25, o.num("spin", 0) + 45);
                    flash(level, L, at, size * 1.4);
                    embers(level, L, at, 2 + (int) Math.round(power * 2), size * 0.9, 0.3, 12);
                } finally {
                    WyrdKit.leave();
                }
                WyrdKit.sparks(level, L, at, 3 + (int) Math.round(power * 2), 3.5 + power, who);
            }
            case "shade" -> {
                // 분신 (RuneKit.shadeClone — 발 고정 · 세로축 빌보드 · 칸 = 자세 + 왼쪽이면 4): 그 묶음의 분신 (JobFx.shade 와 같은 틀 · 가운데 = 발 + 크기)
                if (!Vfx.hasSheet(L.n("shade"))) return false;
                Opt b = Opt.of("size", size, "life", life, "fade", o.num("fade", 0.5), "grow", o.num("grow", 0), "alpha", Math.min(1, o.num("alpha", 0.92)), "frames",
                        1.0, "vel", o.vec("vel", V.ZERO), "view", o.num("view", 0), "owner", o.num("owner", 0));
                rfx(level, L.n("shade"), 1, Math.max(0, Math.min(7, (int) Math.round(o.num("cell", 0)))), V.up(at, size), b, VERT, 1);
            }
            case "wave" -> {
                // 카메라를 보는 초승달 (예전 rfx_wave — spin 0 = 위로 불룩): 칠한 초승달 (PhantomFx.wave — 같은 뜻의 기울기)
                if (!Vfx.hasSheet(L.n("slashm_00"))) return false;
                wave(level, L, at, dir, o.num("speed", 0), size, o.num("spin", 0), life, who, Math.min(1, o.num("alpha", 1)));
            }
            default -> { // sigil
                if (!Vfx.hasSheet(L.n("ring_00"))) return false;
                int hold = Math.max(4, (int) Math.round(life * 20) - 10);
                // 2026-11-17 세운 문양 (면이 위가 아님 — 심연 룬의 눈 문양 182 · 보스): 그 쪽을 보는 고리 (앞뒤 두 면)
                if (!flat) ringFacing(level, L, at, size * 0.9, dir, 6, hold, who, Math.min(1, o.num("alpha", 0.9)));
                else ring(level, L, ground(level, at), size * 0.9, 6, hold);
            }
        }
        return true;
    }

    /** 같은 것 — elements 면 원소 빛도 (엘리멘탈 블레이드가 원소마다 물들인 rfx) */
    static Lib painter(double[] glow, boolean elements) {
        if (glow == null) return null;
        if (palette == null) {
            java.util.Map<double[], Lib> m = new java.util.IdentityHashMap<>();
            m.put(JobTraits8.NIGHT, ARCANE);
            for (double[][] c : MageSkills.SPELLS) m.put(c[0], ARCANE);
            for (double[][] c : MageSkills.PRISM) m.put(c[0], ARCANE);
            m.put(NecroCrew.DEEP, SOUL); // 네크로맨서 망자의 연기 (짙은 녹색 · 혼불)
            m.put(NecroCrew.FIRE, SOUL); // 2026-11-17 녹색 무덤불 (rpg:tyr_ghostfire — 망자 선원 틱에도: 맥락 없이 이 빛이면 혼 묶음)
            m.put(JobTraits8.GRAVE_SMOKE, SOUL); // 망자의 군대 무덤이 솟을 때 흙먼지
            m.put(ArcmageSkills.STAR_BLUE, ARCANE); // 헤일로 배리어의 별빛 고리
            for (double[][] c : JobFx.CIRCUS) { // 2026-11-16 피에로 색종이 · 풍선 · 불꽃놀이의 서커스 빛 (무지개 대신 서커스 그림)
                m.put(c[0], CIRCUS);
                m.put(c[1], CIRCUS); // 속빛 (쇼타임 불티 — CI 43887309301 피에로 궁 색 먼지 192)
            }
            m.put(TricksterSkills.ILLUSION, ILLUSION); // 2026-11-16 트릭스터 거울 · 깨지는 거울 · X 자국의 청록 환영빛
            m.put(TricksterSkills.ILLUSION_CORE, ILLUSION);
            // 2026-11-16 피에로의 금빛 · 붉은 · 흰 빛 (조명 · 터짐 · 연기 — CI 43872303193 에 예전 jspot · jburst2 가 남았었음)
            m.put(PierrotSkills.GOLD, CIRCUS);
            m.put(PierrotSkills.RED, CIRCUS);
            m.put(PierrotSkills.WHITE, CIRCUS);
            // 2026-11-16 프로스트 레이더의 얼음 · 서리 흰빛 (서리 고리 JobFx15.halo · 얼음 결정)
            m.put(JobTraits16.ICE, FROST);
            m.put(JobTraits16.FROST_WHITE, FROST);
            // 2026-11-16 엘리멘탈 블레이드 바위 (돌가시 · 갈라진 땅의 흙빛 · 틈빛) → 곰 묶음
            m.put(ElementalbladeSkills.ROCK_BODY, BEAR);
            m.put(ElementalbladeSkills.ROCK_SEAM, BEAR);
            // 2026-11-16 사제 · 세인트: 금빛 · 새벽빛 · 잎빛 → 성광, 꽃잎 빛 → 꽃잎 묶음
            m.put(PriestSkills.GOLD, HOLY);
            m.put(PriestSkills.DAWN, HOLY);
            m.put(JobTraits14.GOLD, HOLY);
            m.put(JobTraits14.GOLD_CORE, HOLY);
            m.put(JobTraits14.LEAF, HOLY);
            m.put(JobTraits14.LEAF_CORE, HOLY);
            m.put(JobTraits14.PETAL, PETALS);
            m.put(JobTraits14.PETAL_CORE, PETALS);
            // 2026-11-16 워록: 피 · 금빛 · 불씨 빛 → 혈주
            m.put(JobTraits14.BLOOD, HEX);
            m.put(JobTraits14.BLOOD_CORE, HEX);
            m.put(JobTraits14.WGOLD, HEX);
            m.put(JobTraits14.WGOLD_CORE, HEX);
            m.put(JobTraits14.EMBER, HEX);
            m.put(JobTraits14.EMBER_CORE, HEX);
            // 2026-11-16 다크 나이트: 공허 · 피 · 진홍 → 아가리
            m.put(JobTraits15.VOID, MAW);
            m.put(JobTraits15.BLOOD, MAW);
            m.put(JobTraits15.CRIMSON, MAW);
            m.put(JobTraits15.CRIMSON_CORE, MAW);
            // 2026-11-16 성기사 · 썬더로드 (금빛 · 하늘빛 · 먹구름 · 새벽빛 · 돌) → 뇌광, 타이런트 (보랏빛 · 핏빛) → 폭군
            for (double[] c : new double[][] {JobTraits13.GOLD, JobTraits13.GOLD_CORE, JobTraits13.SKY, JobTraits13.SKY_CORE, JobTraits13.CLOUD, JobTraits13.CLOUD_CORE,
                    JobTraits13.DAWN, JobTraits13.DAWN_CORE, JobTraits13.STONE, JobTraits13.STONE_CORE, PaladinSkills.CLOUD, PaladinSkills.CLOUD_CORE})
                m.put(c, THUNDER);
            for (double[] c : new double[][] {JobTraits13.VIOLET, JobTraits13.VIOLET_CORE, JobTraits13.CRIMSON, JobTraits13.CRIMSON_CORE})
                m.put(c, TYRANT);
            // 2026-11-16 소울 리퍼 (혼 · 그늘) → 소울 리퍼 묶음, 엑시큐셔너 (황혼 · 자줏빛 · 칼날 · 까마귀 · 그림자) → 처형 묶음
            for (double[] c : new double[][] {JobTraits12.SOUL, JobTraits12.SOUL_CORE, SoulreaperSkills.SHADE, SoulreaperSkills.SHADE_CORE})
                m.put(c, SOULREAP);
            for (double[] c : new double[][] {JobTraits12.DUSK, JobTraits12.DUSK_CORE, JobTraits12.VIOLET, ExecutionerSkills.VIOLET_CORE, ExecutionerSkills.DARK,
                    ExecutionerSkills.BLADE, ExecutionerSkills.RAVEN, ExecutionerSkills.SHADOW, ExecutionerSkills.SHADOW_CORE})
                m.put(c, EXEC);
            // 2026-11-16 드라군 (용 · 불) → 용 묶음
            for (double[] c : new double[][] {DragoonSkills.DRAGON_GLOW, DragoonSkills.DRAGON_CORE, DragoonSkills.FIRE_GLOW, DragoonSkills.FIRE_CORE})
                m.put(c, DRAGONL);
            // 2026-11-16 피닉스 가드 (하늘빛 · 깊은 푸른빛) → 불사조 묶음
            for (double[] c : new double[][] {JobTraits15.AZURE, JobTraits15.AZURE_CORE, JobTraits15.DEEP, JobTraits15.DEEP_CORE})
                m.put(c, PHOENIXL);
            palette = m;
        }
        Lib L = palette.get(glow);
        if (L != null) return L;
        Lib E = elements ? elementOf(glow) : null; // 2026-11-16 원소 빛 (엘리멘탈 블레이드)
        if (E != null) return E;
        for (String id : PAINTED_JOBS) {
            Jobs.Job j = Jobs.job(id);
            if (j != null && (j.color()[0] == glow || j.color()[1] == glow)) return lib(j);
        }
        return null;
    }

    /**
     * 2026-11-16 예전 rfx 한 벌 (JobFx.two · cells — 빛 glow · 값 b) 을 칠한 그림 한 장으로: glow 가 칠한 직업의 빛 (painter) 이고 그 묶음에 이 rfx 의 그림 (PAINT) 이
     * 있으면 rfx 다리로 같은 자리 · 크기 · 방향 · 움직임에 그리고 true (사용자 "예전 방향 · 자리 · 크기는 그대로, 그림만")
     */
    /** 2026-11-16 이 rfx 를 빛 glow 로 부르면 칠한 그림으로 그리는지 (칠한 면 판은 앞뒤 두 면 — 예전 한 면 판을 앞 · 뒤 두 번 부르던 곳은 한 번만) */
    static boolean paints(String rfx, double[] glow) {
        Paint p = PAINT.get(rfx);
        Lib L = p == null ? null : painter(glow);
        return L != null && Vfx.hasSheet(p.k() > 1 ? L.n(p.art()) + "_00" : L.n(p.art()));
    }

    static boolean painted(ServerLevel level, String rfx, int first, Vec3 at, Opt b, double[] glow) {
        Paint p = PAINT.get(rfx);
        if (p == null) return false;
        Lib L = painter(glow);
        if (L == null && mmorpg.fabric.Tasks.tag() != null) L = tagLib(mmorpg.fabric.Tasks.tag(), glow); // 2026-11-16 룬을 쓰는 동안 (계열 묶음) · 보스 싸움
        if (L == null) return false;
        return paintedWith(level, p, L, first, at, b);
    }

    /**
     * 2026-11-16 룬 계열 대표 그림 (예전 RuneKit rfx 이름 → 묶음 그림 — PAINT 와 같은 틀): 몸의 기운 (rfx_aura — 세로축) · 문장 (rfx_emblem) · 잎 (rfx_leaf) ·
     * 육각 판 (rfx_hex — 면이 dir)
     */
    static final java.util.Map<String, Paint> RUNE_PAINT = java.util.Map.ofEntries(
            java.util.Map.entry("aura", new Paint("aura", 1, VERT, 2, 1)),
            java.util.Map.entry("emblem", new Paint("aegis", 1, BILL, 1.25, 1)),
            // 2026-11-17 방패 문장 (RuneKit.shieldEmblem — 화면을 봄 · CI 43979933145 체력 일반 10): 묶음의 빛의 방패 (aegis — 문장과 같은 몫)
            java.util.Map.entry("shield", new Paint("aegis", 1, BILL, 1.25, 1)),
            java.util.Map.entry("leaf", new Paint("petal", 1, BILL, 1, 1)),
            java.util.Map.entry("hex", new Paint("aegis", 1, FACE_DIR, 1.25, 1)),
            // 2026-11-17 연막 구름 (rfx_cloud 네 칸 — 암살 룬 연막 · CI 43977395799 암살 고귀 71): 묶음의 연기 (그림자 smoke — 없는 묶음은 예전)
            java.util.Map.entry("cloud", new Paint("smoke", 1, BILL, 1, 1)),
            // 2026-11-17 날아가는 고드름 (rfx_icicle — 만년빙하 · 마력 · 원소 얼음 룬, CI 43982885199 14 + 6): 얼음 창 (icelance — 비전 · 서리 묶음, jicelance 와 같은 몫)
            java.util.Map.entry("icicle", new Paint("icelance", 1, LOOK_DIR, 0.25, 1)),
            // 2026-11-17 검은 가시 손 (rfx_thorn — 심연 낙인 · 밑동 고정, CI 43982885199 19): 솟는 가시 (spike — 폭군 묶음, 솟았다 가라앉는 여덟 칸을 life 동안)
            java.util.Map.entry("thorn", new Paint("spike", 1, VERT, 1, 1)),
            // 2026-11-17 소용돌이 (rfx_vortex — 민첩 영웅 10): 묶음의 소용돌이 (swirl — 질풍 묶음, jswirl 과 같은 몫)
            java.util.Map.entry("vortex", new Paint("swirl", 1, BILL, 1, 1)),
            // 2026-11-17 솟는 분수 (rfx_fountain 128 × 256 — 아래 끝 = 땅, 정령왕 불 원소 · CI 43987516768 정령 초월 6): 불빛이면 원소 묶음의 불기둥 (pyre — 같은 세움 ·
            // 아래 끝 = 땅). 체력 룬의 녹색 분수는 꽃잎 묶음에 불기둥이 없어 예전
            java.util.Map.entry("fountain", new Paint("pyre", 1, VERT, 2, 1)),
            // 2026-11-17 받는 피해 증가 표식 (RuneMarks — 머리 위 금 간 방패, 빛 장만 — 심지 장은 RuneMarks 가 부르지 않음): 계열 묶음의 방패 깨짐
            java.util.Map.entry("jbreak", new Paint("shieldbreak", 1, BILL, 1, 1)));

    static boolean paintedWith(ServerLevel level, Paint p, Lib L, int first, Vec3 at, Opt b) {
        String art = p.art();
        int fst = first;
        if (p.mirrorArt() != null && first >= p.mirrorFrom()) { // 예전 그림판의 좌우를 뒤집은 장면 (jring2 거꾸로 도는 고리) → 미리 뒤집은 그림
            art = p.mirrorArt();
            fst = first - p.mirrorFrom();
        }
        String sheet = L.n(art);
        String probe = p.k() > 1 ? sheet + "_00" : sheet;
        if (!Vfx.hasSheet(probe)) {
            Lib K = kin(L); // 2026-11-16 이 묶음에 없는 그림은 가까운 묶음 (트릭스터 환영 → 그림자 꽃잎 · X · 장미 · 찌르기)
            if (K == null) return false;
            sheet = K.n(art);
            probe = p.k() > 1 ? sheet + "_00" : sheet;
            if (!Vfx.hasSheet(probe)) return false;
        }
        int n = Vfx.sheet(probe).frames();
        int f0 = Math.floorMod(fst, n);
        int count = Math.max(1, Math.min((int) b.num("frames", 1), n - f0));
        Opt bb = b.with("frames", (double) count);
        if (p.scale() != 1) bb = bb.with("size", b.num("size", 0) * p.scale());
        // 2026-11-16 땅에 눕힌 큰 그림 (지름 6칸 넘게 — 연꽃 들판 · 웅덩이 · 문장): 예전 그림은 부드러운 빛 무리라 바닥이 비쳤는데 칠한 도트는 꽉 찬 칸이라 땅을 덮었음
        // (CI 43903024474 세인트 궁 — 들판 전체가 노란 꽃 판) → 반투명 위험 구역처럼 절반 불투명
        if (p.facing() == FACE_DIR) {
            Vec3 dd = b.vec("dir", V.UP);
            double hh = 2 * bb.num("size", 0) * p.sy();
            if (V.len(dd) > 1e-6 && V.unit(dd).y > 0.9 && hh > 6) bb = bb.with("alpha", b.num("alpha", 1) * 0.5);
        }
        rfx(level, sheet, p.k(), f0, at, bb, p.facing(), p.sy());
        return true;
    }

    /**
     * 예전 rfx 파티클 한 벌 (빛 + 심지 두 장 — JobFx.two · cells) 을 칠한 그림판 한 장으로 (사용자 "예전 방향 · 자리 · 크기는 그대로, 그림만"): 모드 없는
     * 클라이언트 다리 (PBridge · PSim) 의 셈 그대로 — 자리 = at + (dir × speed + vel) × 시간 · 그림 높이 = 반 크기 size × 2 × sy (세로 몫) × (1 + grow × 시간 몫) ·
     * 화면 돌림 = spin + spinRate × 시간 (반시계) · 방향 facing (BILL · FACE_DIR · VERT) · 칸 = first + (loop 면 ⌊시간 × fps + phase⌋ mod count, 아니면
     * min(count − 1, ⌊시간 × fps⌋)) · 마지막 fade 몫 동안 옅어짐 · 볼 사람 view · owner. 그림판 sheet (k = 2 면 조각 sheet_rc 넷). b = two · cells 가 만든 값
     */
    static Vfx.Fx rfx(ServerLevel level, String sheet, int k, int first, Vec3 at, Opt b, int facing, double sy) {
        if (facing != LOOK_DIR) return rfx1(level, sheet, k, first, at, b, facing, sy, who(b), null);
        // 가는 쪽으로 누운 그림: 보는 사람마다 그 사람의 눈 쪽을 보는 한 장
        Predicate<ServerPlayer> base = who(b);
        Vfx.Fx last = null;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 128 * 128 || (base != null && !base.test(p))) continue;
            int id = p.getId();
            Predicate<ServerPlayer> one = q -> q.getId() == id;
            last = rfx1(level, sheet, k, first, at, b, facing, sy, base == null ? one : base.and(one), p);
        }
        return last;
    }

    private static Vfx.Fx rfx1(ServerLevel level, String sheet, int k, int first, Vec3 at, Opt b, int facing, double sy, Predicate<ServerPlayer> viewer,
            ServerPlayer looker) {
        double life = Math.max(0.05, b.num("life", 0.3));
        int ticks = Math.max(1, (int) Math.round(life * 20));
        int count = Math.max(1, (int) b.num("frames", 1));
        double fps = b.num("fps", 0);
        boolean loop = b.truthy("loop");
        double phase = b.num("phase", 0);
        Vec3 dir = b.vec("dir", V.UP);
        double dl = V.len(dir);
        Vec3 d = dl > 1e-6 ? V.scale(dir, 1 / dl) : V.UP;
        Vec3 mv = V.add(V.scale(dir, b.num("speed", 0)), b.vec("vel", V.ZERO));
        double h0 = 2 * b.num("size", 0) * sy;
        double grow = b.num("grow", 0);
        double spin = b.num("spin", 0);
        double spinRate = b.num("spinRate", 0);
        double fade = Math.max(0, Math.min(1, b.num("fade", 0)));
        double alpha = b.num("alpha", 1);
        // 1인칭 눈 바로 앞 거름 (CI 43716691507 1인칭 — 눈앞을 지나는 연기 · 꽃잎이 픽셀 덩어리로 화면을 덮음): 그림 크기에 맞춘 반지름 (nearR — 빛 0.25 × 높이) ·
        // 예전 다리가 가루로 바꾸던 작은 알갱이 (반 크기 0.11 아래) 는 가루처럼 눈에서 1.3칸 (PBridge.DUST_NEAR_EYE) 안이면 그 사람에게 보내지 않음
        double half = b.num("size", 0) * sy;
        Predicate<ServerPlayer> who = farEye(viewer, at, Math.max(half < 0.11 ? 1.3 : 0, h0 * 0.25 / k));
        Vfx.Fx fx = Vfx.at(level, at, ticks + 2, who);
        boolean look = facing == LOOK_DIR;
        Quaternionf q = facing == FACE_DIR ? dirFace(d) : look ? lookDir(d, at, looker) : null;
        int sides = facing == FACE_DIR ? 2 : 1;
        java.util.List<Vfx.Sprite> all = new java.util.ArrayList<>();
        double[] off = new double[k * k * sides * 2];
        int i = 0;
        for (int r = 0; r < k; r++) {
            for (int c = 0; c < k; c++) {
                for (int side = 0; side < sides; side++) {
                    String name = k == 1 ? sheet : sheet + "_" + r + (side == 0 ? c : k - 1 - c);
                    Vfx.Sprite sp = fx.sprite(name, at);
                    if (facing == VERT) sp.vertical();
                    else if (q != null) sp.fixed(q);
                    if (side == 1) sp.mirror();
                    off[i * 2] = c - (k - 1) / 2.0;
                    off[i * 2 + 1] = (k - 1) / 2.0 - r;
                    all.add(sp);
                    i++;
                }
            }
        }
        java.util.function.IntUnaryOperator frameAt = t -> {
            if (count <= 1) return first;
            double u = t / 20.0 * fps;
            return first + (loop ? Math.floorMod((int) Math.floor(u + phase), count) : Math.min(count - 1, (int) Math.floor(u)));
        };
        for (int n = 0; n < all.size(); n++) {
            Vfx.Sprite sp = all.get(n);
            double t0 = h0 / k;
            sp.size(t0);
            if (k > 1) sp.offset(off[n * 2] * t0, off[n * 2 + 1] * t0);
            if (spin != 0 && !look) sp.roll(spin, 0);
            sp.frame(frameAt.applyAsInt(0));
            if (alpha < 0.999) sp.alpha(alpha);
        }
        boolean moves = V.len(mv) > 1e-4;
        boolean turns = Math.abs(spinRate) > 1e-3 && !look;
        boolean grows = Math.abs(grow) > 1e-4;
        int step = turns ? (Math.abs(spinRate) > 400 ? 1 : 2) : look ? 2 : 4;
        int[] shown = {frameAt.applyAsInt(0)};
        fx.each(1, ticks, 1, t -> {
            int f = frameAt.applyAsInt(t);
            if (f != shown[0]) {
                shown[0] = f;
                for (Vfx.Sprite sp : all) sp.frame(f);
            }
            if ((moves || turns || grows || look) && (t - 1) % step == 0) {
                int t1 = Math.min(ticks, t - 1 + step);
                int dur = t1 - (t - 1);
                Vec3 p = V.add(at, mv, t1 / 20.0);
                double h = h0 * (1 + grow * t1 / (double) ticks);
                Quaternionf qq = look ? lookDir(d, p, looker) : null;
                for (int n = 0; n < all.size(); n++) {
                    Vfx.Sprite sp = all.get(n);
                    double tt = h / k;
                    if (k > 1) sp.offset(off[n * 2] * tt, off[n * 2 + 1] * tt);
                    sp.to(moves ? p : null, tt, dur);
                    if (turns) sp.roll(spin + spinRate * t1 / 20.0, dur);
                    if (qq != null) sp.face(qq, dur);
                }
            }
        });
        int from = fade > 0 ? (int) Math.round((1 - fade) * ticks) : ticks;
        for (Vfx.Sprite sp : all) {
            if (fade > 0) sp.fade(Math.max(1, from), Math.max(1, ticks - from));
            else sp.until(ticks + 1);
        }
        return fx;
    }

    /**
     * 2026-11-16 rfx lookat_direction 의 판 방향 (PBridge.fixed 와 같은 셈 — 기준 눈 대신 보는 사람 looker 의 눈 PBridge.eyeOf): 그림 +x = d · 면 = 눈 쪽을 d 에 수직으로 ·
     * 그림 +y = 면 × d
     */
    static Quaternionf lookDir(Vec3 d, Vec3 at, ServerPlayer looker) {
        Vec3 eye = looker != null ? PBridge.eyeOf(looker) : V.up(at, 2.0);
        Vec3 to = V.sub(eye, at);
        Vec3 n = V.sub(to, V.scale(d, V.dot(to, d)));
        if (V.len(n) < 1e-5) {
            Vec3 r = Math.abs(d.y) < 0.9 ? V.UP : new Vec3(1, 0, 0);
            n = V.sub(r, V.scale(d, V.dot(r, d)));
        }
        n = V.unit(n);
        return Vfx.basis(d, V.unit(V.cross(n, d)), n);
    }

    /** rfx direction_z 의 판 방향 (PBridge.fixed — 위 = 세계 위 (dir 이 거의 위 · 아래면 남쪽) 를 dir 에 수직으로 · X = 위 × dir · 앞면 = dir) */
    static Quaternionf dirFace(Vec3 d) {
        Vec3 ref = Math.abs(d.y) < 0.999 ? V.UP : new Vec3(0, 0, 1);
        Vec3 y = V.unit(V.sub(ref, V.scale(d, V.dot(ref, d))));
        Vec3 x = V.unit(V.cross(y, d));
        return Vfx.basis(x, y, d);
    }
}
