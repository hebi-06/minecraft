package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.core.Economy;
import mmorpg.core.Jobs;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.client.ClientJobBar;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobTrainer;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.job.JobTraits3;
import mmorpg.fabric.job.JobTraits4;
import mmorpg.fabric.job.JobTraitsMore;
import mmorpg.fabric.rune.RuneCast;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.27.0 직업 사진첩 (화면 시험, only=job — 실제 키 · 마우스): 직업 교관 창 (카드 열하나 · 카드 설명 · 흐린 준비 중 카드 · 확인 창) → 전직 연출, 직업 칸 HUD
 * (직업 이름 그라데이션 · 잠긴 칸 · 검세 보석 · 태세 남은 시간), 검사 · 마검사 스킬 다섯 (Shift + 룬 칸 키, 1인칭 · 3인칭 — 정한 틱에 사진
 * job_&lt;스킬&gt;_&lt;틱&gt;[_3p]), 명경지수 · 마검 각성 뒤 평타 (왼쪽 클릭), 직업 바꾸기 확인 (300 은화), 스탯 창 「마력 검술」 줄.
 * 확인 항목은 로그 [job-gallery] OK / FAIL (써졌는지 — 재사용 대기 · 피해 · 태세).
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgJobGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-job-gallery");
    /** 과녁: 앞 무리 (3 ~ 6칸) + 옆 둘 — 평평한 세계 땅 y = -60 */
    private static final Vec3[] TARGETS = {
        new Vec3(0.5, -60, 3.2), new Vec3(-1.2, -60, 4.5), new Vec3(2.2, -60, 4.5), new Vec3(0.5, -60, 6.0), new Vec3(-3.0, -60, 2.5), new Vec3(4.0, -60, 2.5)
    };

    /**
     * 스킬 하나: 칸 · 사진 틱 (1인칭 · 3인칭) · 끝까지 기다릴 틱 · 피해가 없는 태세인지 (태세 · 강화 — 뒤에 평타를 누름) · 0.28.0 태세가 아닌 강화 (바람 타기 — 평타만 확인) ·
     * 평타 뒤 사진 틱 (완드는 마법진을 다 그린 뒤)
     */
    /** 3인칭 사진을 앞 카메라로 찍는 스킬 (과녁 사이를 지나감) */
    private static final java.util.Set<String> FRONT_3P = java.util.Set.of("lancer_vault", "reaper_wraith", "priest_step");
    /** 0.30.0 피해가 없는 스킬 (회복 · 보호막 · 성흔 — 써졌는지만 확인) */
    private static final java.util.Set<String> NO_DAMAGE = java.util.Set.of("priest_heal", "priest_step", "priest_stigma", "saint_accord", "saint_veil");
    /** 순간 이동이 끝난 뒤 (4틱) 뒤로 돌아 찍는 스킬 — 잔영: 과녁 너머로 간 뒤 분신 · 끌려간 과녁을 1인칭 · 3인칭 뒤 카메라로 (앞 카메라는 몸이 분신을 가림) */
    private static final java.util.Set<String> TURN_AFTER = java.util.Set.of("assassin_shade");

    private record Case(int slot, int[] shots, int[] shots3p, int total, boolean stance, boolean buff, int hitAt) {
        Case(int slot, int[] shots, int[] shots3p, int total, boolean stance) {
            this(slot, shots, shots3p, total, stance, false, 3);
        }
    }

    private static final Case[] CASES = {
        new Case(0, new int[] {2, 12, 22}, new int[] {12, 22}, 30, false),
        new Case(1, new int[] {1, 3, 8}, new int[] {2, 6}, 16, false),
        new Case(2, new int[] {2, 12}, new int[] {2, 12}, 22, false),
        new Case(3, new int[] {4, 20}, new int[] {6}, 24, true),
        new Case(4, new int[] {4, 7, 17, 27, 37, 46, 51, 56}, new int[] {7, 27, 51}, 66, false), // 0.50.0 라그나로크: 돌진 · 네 베기 · 내리꽂음
    };
    private static final Case[] CASES_SB = {
        new Case(0, new int[] {2, 6, 12}, new int[] {3, 12}, 22, false),
        new Case(1, new int[] {1, 4, 12}, new int[] {2, 12}, 20, false),
        new Case(2, new int[] {4, 18, 34}, new int[] {18}, 42, false),
        new Case(3, new int[] {3, 6, 12, 18, 20}, new int[] {6, 18}, 28, false), // 0.51.0 스펠 가이저 (6 꽂음 · 18 기둥)
        new Case(4, new int[] {10, 22, 32, 42, 56, 62}, new int[] {22, 56}, 72, false),
    };

    /** 0.28.0 전사 · 매지션 · 궁수 */
    private static final Case[] CASES_WA = {
        new Case(0, new int[] {3, 8, 14}, new int[] {8, 14}, 24, false),
        new Case(1, new int[] {2, 5, 10}, new int[] {3, 10}, 22, false),
        new Case(2, new int[] {6, 20, 40}, new int[] {10, 30}, 54, false),
        new Case(3, new int[] {4, 20}, new int[] {6}, 24, true),
        new Case(4, new int[] {8, 12, 30, 48, 62, 66}, new int[] {6, 12, 36, 62}, 80, false),
    };
    private static final Case[] CASES_MG = {
        new Case(0, new int[] {2, 6, 12}, new int[] {3, 8}, 18, false),
        new Case(1, new int[] {1, 6, 12}, new int[] {2, 12}, 18, false),
        new Case(2, new int[] {6, 25, 50, 71}, new int[] {20, 71}, 76, false),
        new Case(3, new int[] {6, 8, 18, 28, 38, 48, 50}, new int[] {18, 48}, 58, false), // 0.51.0 프리즘 레이 (8 ~ 38 광선 · 48 다섯 갈래)
        new Case(4, new int[] {10, 22, 34, 46, 58, 70, 74}, new int[] {22, 46, 70}, 84, false),
    };
    private static final Case[] CASES_AR = {
        new Case(0, new int[] {2, 6, 12}, new int[] {3, 9}, 18, false),
        new Case(1, new int[] {1, 3, 8}, new int[] {2, 6}, 30, false, true, 4),
        new Case(2, new int[] {3, 10, 26, 46}, new int[] {10, 30}, 52, false),
        new Case(3, new int[] {4, 10, 18}, new int[] {6, 14}, 40, true, false, 4), // 2026-11-07 와일드 헌트: 늑대 정령 둘이 깨어남 → 4 활 평타 → 늑대가 뛰어올라 물어뜯음
        new Case(4, new int[] {8, 18, 25, 27, 36, 43, 50}, new int[] {12, 22, 26, 30, 44}, 64, false), // 스피릿 스탬피드: 0 ~ 24 사슴 왕 깨어남 → 24 내달림 → 43 꽃이 터짐
    };

    /** 0.29.0 암살자 · 창술사 · 사신 (암영은 태세지만 평타가 풀므로 강화처럼 — 평타가 들어갔는지만) */
    private static final Case[] CASES_AS = {
        new Case(0, new int[] {2, 6, 12}, new int[] {3, 10}, 20, false),
        new Case(1, new int[] {2, 6, 14, 30, 42}, new int[] {4, 20, 42}, 50, false),
        new Case(2, new int[] {2, 4, 7, 10, 24}, new int[] {4, 8}, 50, false), // 0.50.0 요르문간드의 독니: S 자로 지나감 (8틱) · 독 넷 (0.5초마다)
        new Case(3, new int[] {4, 16}, new int[] {6}, 24, false, true, 4),
        new Case(4, new int[] {3, 8, 18, 28, 34, 41, 46}, new int[] {10, 30, 42}, 58, false),
    };
    private static final Case[] CASES_LA = {
        new Case(0, new int[] {1, 6, 11, 16}, new int[] {2, 11}, 20, false),
        new Case(1, new int[] {3, 7, 11, 14}, new int[] {5, 11}, 22, false),
        new Case(2, new int[] {2, 8, 14}, new int[] {4, 12}, 24, false),
        new Case(3, new int[] {4, 12, 22}, new int[] {6, 22}, 30, true),
        new Case(4, new int[] {4, 8, 17, 27, 37, 43, 48, 51, 55}, new int[] {9, 27, 47, 51}, 66, false), // 0.50.0 흐레스벨그의 발톱: 찌르기 폭풍 넷 · 뒤로 뜀 · 돌진 · 꿰뚫음
    };
    private static final Case[] CASES_RE = {
        new Case(0, new int[] {2, 6, 12, 16}, new int[] {3, 13}, 22, false),
        new Case(1, new int[] {1, 3, 6, 12}, new int[] {2, 6}, 34, false),
        new Case(2, new int[] {1, 4, 7, 12}, new int[] {4, 8}, 16, false),
        new Case(3, new int[] {4, 20}, new int[] {6}, 24, true),
        new Case(4, new int[] {4, 14, 24, 34, 38, 40, 42, 44, 48, 52, 66, 76, 80}, new int[] {14, 44, 76}, 90, false),
    };

    /** 0.30.0 성기사 · 사제 · 수호기사 */
    private static final Case[] CASES_PA = {
        new Case(0, new int[] {2, 6, 11, 14}, new int[] {3, 12}, 20, false),
        new Case(1, new int[] {1, 3, 6, 12}, new int[] {3, 8}, 18, false),
        new Case(2, new int[] {2, 12, 22, 26}, new int[] {2, 22}, 30, false),
        new Case(3, new int[] {4, 16}, new int[] {6, 16}, 24, true),
        new Case(4, new int[] {6, 14, 19, 21, 34, 48, 52, 70}, new int[] {14, 21, 48, 70}, 90, false),
    };
    private static final Case[] CASES_PR = {
        new Case(0, new int[] {2, 8}, new int[] {3, 8}, 14, false),
        new Case(1, new int[] {1, 3, 6, 12}, new int[] {2, 8}, 16, false),
        new Case(2, new int[] {4, 20, 40, 60}, new int[] {10, 40}, 76, false),
        new Case(3, new int[] {3, 10, 20}, new int[] {6, 20}, 24, false),
        new Case(4, new int[] {6, 12, 16, 18, 32, 48, 52}, new int[] {16, 32, 50}, 60, false),
    };
    private static final Case[] CASES_GU = {
        new Case(0, new int[] {1, 3, 8}, new int[] {2, 8}, 14, false),
        new Case(1, new int[] {1, 3, 5, 10}, new int[] {3, 6}, 16, false),
        new Case(2, new int[] {2, 5, 9, 14, 20}, new int[] {5, 14}, 30, false),
        new Case(3, new int[] {4, 16}, new int[] {6, 16}, 24, true),
        new Case(4, new int[] {6, 12, 30, 80, 161, 166, 172}, new int[] {12, 40, 163, 172}, 180, false),
    };

    /**
     * 0.51.0 2차 직업 — 검사 각성 소드마스터 · 반전 블러드 나이트 (재설계안 2판): 칼집 섬광 (6 뽑음 · 10 큰 가로) · 받아넘기기 (4 에 맞음 →
     * 5 쨍 · 11 · 13 · 15 받아침 — beforeCast) · 블레이드 에코 (2026-11-07 3판 — 6 ~ 18 둘레 적 사이를 옮겨 가며 칼자국 · 26 칼집에 넣어 터짐) · 용의 피 ·
     * 엑스칼리버 (2026-11-07 3판 — 4 ~ 12 빛의 대검이 자람 · 22 ~ 26 내리침 · 26 검격 · 36 빛의 검) / 핏빛 대검 (0 · 12 · 24 다시 누름 → 5 · 17 · 31) · 피의 돌진 (6) · 피의 사슬 (4 박음 · 36 끌어당김) · 광란 · 블러드 문 (10 빨아들임 · 16 ~ 31
     * 뛰어올라 내리꽂음 · 41 ~ 피의 기둥)
     */
    private static final Case[] CASES_SM = {
        new Case(0, new int[] {3, 7, 9, 12}, new int[] {7, 11}, 28, false),
        new Case(1, new int[] {2, 5, 11, 15}, new int[] {5, 13}, 26, false),
        new Case(2, new int[] {3, 8, 12, 18, 26, 28}, new int[] {12, 26}, 36, false),
        new Case(3, new int[] {4, 12}, new int[] {6}, 26, true),
        new Case(4, new int[] {4, 12, 20, 24, 26, 30, 36, 40}, new int[] {20, 26, 36}, 52, false),
    };
    private static final Case[] CASES_BK = {
        new Case(0, new int[] {3, 5, 15, 17, 29, 31}, new int[] {5, 17, 31}, 42, false),
        new Case(1, new int[] {1, 3, 6, 10}, new int[] {3, 6}, 18, false),
        new Case(2, new int[] {2, 5, 20, 35, 38}, new int[] {5, 36}, 46, false),
        new Case(3, new int[] {3, 10}, new int[] {4}, 26, true),
        new Case(4, new int[] {2, 10, 12, 20, 26, 31, 33, 41, 44}, new int[] {10, 26, 32, 42}, 52, false),
    };
    /** 0.51.0 다시 쓰는 스킬 (JobRecast): 쓴 뒤 이 틱들에 같은 칸을 다시 누름 (핏빛 대검 둘째 · 셋째 · 데드아이 세 발 — 사진첩 · 영상 모두) */
    private static final java.util.Map<String, int[]> AGAIN = java.util.Map.ofEntries(java.util.Map.entry("bloodknight_cleave", new int[] {12, 24}),
            java.util.Map.entry("necromancer_call", new int[] {16}), java.util.Map.entry("sniper_ult", new int[] {22}),
            java.util.Map.entry("trickster_swap", new int[] {12}), java.util.Map.entry("trickster_dash", new int[] {16}), // 2026-11-06 트릭스터 2판: 섀도 스와프 자리 바꿈 · 미라주 대시 돌아옴
            java.util.Map.entry("stormlancer_drive", new int[] {12}), java.util.Map.entry("stormlancer_rend", new int[] {20}),
            java.util.Map.entry("dragoon_jump", new int[] {16}), // 2026-11-06 창술사 2차: 돌아서며 바람 창 · 뽑아 찢음 · 내리꽂음
            java.util.Map.entry("executioner_ult", new int[] {36}), java.util.Map.entry("darkknight_crunch", new int[] {12}), // 2026-11-06 엑시큐셔너: 앞 과녁을 처형 → 비포 선셋 다시 · 다크 나이트: 크게 물어 찢음
            java.util.Map.entry("frostraider_throw", new int[] {14})); // 2026-11-07 프로스트 레이더: 박힌 도끼를 되부름

    /**
     * 0.51.0 워로드 (재설계안 2판): 대지 가르기 (24 까지 모음 → 28 내려침 · 돌창) · 동면 (40 깨어남) · 곰의 포옹 (2 ~ 8 끌어옴 · 12 내려찍음) ·
     * 베어 스톰프 (4 ~ 16 뛰어올라 내려찍음 · 26 갈라짐) · 마지막 휘두름 (24 한 바퀴) / 2026-11-07 프로스트 레이더 (재설계안 3판 — 글래디에이터 대신): 프로스트 바이트
     * (2 던짐 · 6 박힘 → 14 다시 누름 · 19 뽑혀 벰 · 23 받음) · 아이스 바운드 (0 ~ 10 날아가 10 내리찍음) · 샤터 (8 내려찍어 가둠 · 22 ~ 27 비틂 · 28 깨부숨) · 요툰 그립
     * (태세 — 평타마다 거인의 도끼) · 핌불윈터 (6 하늘로 던짐 · 12 빙하 도끼 · 26 내리꽂힘 · 42 되부름 · 46 깨짐)
     */
    private static final Case[] CASES_WL = {
        new Case(0, new int[] {6, 16, 24, 29, 33}, new int[] {16, 29}, 46, false),
        new Case(1, new int[] {4, 20, 40, 42, 46}, new int[] {20, 42}, 56, false),
        new Case(2, new int[] {2, 6, 9, 12, 14}, new int[] {6, 13}, 28, false),
        new Case(3, new int[] {3, 8, 12, 16, 18, 26}, new int[] {10, 16, 26}, 36, false),
        new Case(4, new int[] {6, 14, 22, 24, 26, 30, 36}, new int[] {14, 25, 30}, 50, false),
    };
    private static final Case[] CASES_FR = {
        new Case(0, new int[] {2, 6, 10, 16, 19, 23}, new int[] {6, 19}, 34, false),
        new Case(1, new int[] {2, 5, 8, 10, 12}, new int[] {6, 10}, 26, false),
        new Case(2, new int[] {4, 8, 12, 20, 26, 28, 30}, new int[] {8, 20, 28}, 42, false),
        new Case(3, new int[] {4, 12}, new int[] {6}, 26, true),
        new Case(4, new int[] {4, 6, 12, 20, 26, 30, 42, 46, 48}, new int[] {12, 26, 46}, 66, false),
    };

    /**
     * 0.51.0 아케인 블레이드 (룬 스미스 대신): 크레센트 엣지 (0 ~ 6 감음 → 6 가로 검기 · 7 맞음 → 16 세로 검기 · 17 맞음) · 팬텀 크로스 (0 순간 이동 → 1 X 새김 →
     * 12 X 검기로 터짐) · 아케인 템페스트 (0 ~ 8 머리 위로 돌려 모음 → 8 한 바퀴 → 폭풍 → 20 터짐) · 디멘션 슬래시 (0 ~ 6 감음 → 6 가로로 갈라 틈 →
     * 빨아들임 → 22 닫히며 폭발) · 월드 클리버 (0 ~ 21 하늘을 찌르는 대검으로 자라남 → 21 내려쳐 가름 → 30 ~ 갈라진 땅이 솟구침)
     */
    private static final Case[] CASES_AB = {
        new Case(0, new int[] {2, 5, 6, 7, 9, 14, 16, 17, 19}, new int[] {5, 7, 17}, 28, false),
        new Case(1, new int[] {1, 2, 4, 9, 12, 13, 15}, new int[] {2, 12}, 22, false),
        new Case(2, new int[] {3, 7, 8, 9, 13, 18, 20, 21, 24}, new int[] {7, 9, 20}, 32, false),
        new Case(3, new int[] {3, 6, 7, 12, 18, 22, 24}, new int[] {6, 14, 22}, 30, false),
        new Case(4, new int[] {4, 10, 16, 19, 21, 22, 24, 28, 31, 33, 38}, new int[] {12, 21, 31}, 48, false),
    };
    /**
     * 0.51.0 재설계안 2판 소드 콜러: 베어라 (2 칼이 떠남 → 3 · 13 맴돌며 벰 → 22 꽂힘) · 데려가라 (꽂아 둔 칼로 날아감 — 지나가며 벰) · 꿰뚫고 맴돌아라 (3 꿰뚫음 →
     * 13 · 23 · 33 · 43 맴돎) · 둘로 나뉘어라 (뒤 평타 — 칼 둘이 날아가 X 자) · 칼날 감옥 (10 둘러쌈 → 10 ~ 60 벰 → 70 큰 한 방)
     */
    private static final Case[] CASES_SC = {
        new Case(0, new int[] {1, 3, 6, 13, 16, 23}, new int[] {3, 13, 23}, 30, false),
        new Case(1, new int[] {1, 2, 3, 5, 8, 12}, new int[] {2, 5}, 20, false),
        new Case(2, new int[] {2, 3, 8, 13, 23, 33, 43, 47}, new int[] {3, 23, 43}, 56, false),
        new Case(3, new int[] {2, 4, 6, 12}, new int[] {6}, 20, true, false, 4),
        new Case(4, new int[] {3, 6, 9, 10, 20, 30, 40, 50, 60, 67, 70, 72}, new int[] {6, 10, 40, 70}, 84, false),
    };

    /**
     * 0.51.0 아크메이지 · 네크로맨서 (재설계안 2판 — 2026-11-06): 스타 체인 (2 첫 번개 → 3틱마다 튐) · 헤일로 배리어 (4 던짐 · 10칸 · 19 돌아옴 → 받음) · 스타 볼텍스
     * (4 펼쳐짐 · 10 ~ 60 별빛 · 70 무너짐 — 연쇄 4 + 별 넷이라 다섯째 별에 별자리) · 2026-11-07 셀레스티얼 오빗 (별 다섯이 돎 · 10 · 30 · 50 · 70 별이 떨어져 터짐 —
     * 땅에 별자리) · 콘스텔레이션 (8 ~ 44 하늘에 일곱 별 · 별빛 · 50 무너져 내림 · 56 터짐) / 그레이브 콜 (6 내리꽂힘 · 16 다시 눌러 무덤 셋에서 망자) · 그레이브야드 (6 솟음 · 66 무너짐) · 소울 미스트 (4 던짐 · 8 터짐 · 18 ~ 38 안개 — 망자 둘이
     * 표식으로 몰려듦) · 블라이트 (10틱마다 · 6초만 찍음) · 팬텀 갤리온 (10 솟음 · 14 ~ 34 나아감 · 30 선장이 뛰어내림 · 40 내려앉음 · 56 혼불)
     */
    private static final Case[] CASES_AM = {
        new Case(0, new int[] {2, 5, 9, 14, 20}, new int[] {5, 14}, 28, false),
        new Case(1, new int[] {3, 6, 10, 14, 18, 23}, new int[] {6, 14, 22}, 32, false),
        new Case(2, new int[] {4, 8, 20, 40, 64, 71}, new int[] {12, 40, 71}, 82, false),
        new Case(3, new int[] {4, 10, 28, 30, 50, 70}, new int[] {10, 30, 70}, 80, false),
        new Case(4, new int[] {6, 10, 20, 32, 44, 48, 52, 56, 60, 66}, new int[] {20, 44, 56}, 80, false),
    };
    private static final Case[] CASES_NC = {
        new Case(0, new int[] {2, 5, 7, 16, 20, 28}, new int[] {6, 20}, 36, false),
        new Case(1, new int[] {3, 7, 12, 40, 67, 72}, new int[] {8, 40, 68}, 80, false),
        new Case(2, new int[] {3, 6, 9, 18, 30, 44}, new int[] {9, 28}, 56, false),
        new Case(3, new int[] {3, 12, 40, 80, 120}, new int[] {12, 80}, 124, false),
        new Case(4, new int[] {6, 12, 20, 28, 33, 38, 41, 48, 56, 60, 72}, new int[] {12, 34, 41, 57}, 90, false),
    };

    /**
     * 0.51.0 스나이퍼 · 배틀 아처 (재설계안 2판 — 2026-11-06): 하트시커 (0 ~ 20 끝까지 당김 → 20 저절로 놓음 · 번쩍 → 21 큰 한 발) · 스네어 샷 (2 그물 화살 · 반동으로 뒤로
     * 뜀 → 3 감김 → 12 내려앉음 · 교감 +3) · 트릭 샷 (3 쏨 → 4 맞음 → 3틱마다 튐) · 헌터스 마크 (태세 → 같은 과녁 셋째 활 평타 — 은빛 화살, 사진첩은 둘째까지 맞힌 것으로) ·
     * 사일런트 아울 (2026-11-07 3판 — 조준 · 등 뒤 위 부엉이 정령 → 22 에 다시 눌러 부엉이 화살 → 24 급소 → 34 정령빛) / 호넷 스웜 (2 쏘아 올림 → 7 꽂힘) · 팰컨 다이브 (0 ~ 12 뛰어 매와 내리꽂힘 → 12 둘레 화살) ·
     * 스플린터 샷 (0 ~ 6 당김 → 6 부채꼴 화살 조각) · 스톰 스트라이드 (태세 → 서서 쏜 평타가 세 갈래) · 퀴버 스톰 (2 ~ 62 화살 물결 · 10 ~ 60 여섯 번 → 62 하늘로 다발 →
     * 64 화살비 → 70 내리꽂힘)
     */
    private static final Case[] CASES_SN = {
        new Case(0, new int[] {4, 12, 19, 21, 24}, new int[] {12, 21}, 34, false),
        new Case(1, new int[] {2, 4, 7, 12, 20}, new int[] {4, 12}, 30, false),
        new Case(2, new int[] {3, 5, 8, 11, 16}, new int[] {5, 11}, 24, false),
        new Case(3, new int[] {4, 16}, new int[] {6}, 30, true, false, 4),
        new Case(4, new int[] {6, 14, 22, 24, 30, 36, 42}, new int[] {12, 23, 35}, 52, false), // 2026-11-07 사일런트 아울: 22 에 다시 눌러 부엉이 화살 → 24 꿰뚫음 → 34 정령빛
    };
    private static final Case[] CASES_BA = {
        new Case(0, new int[] {1, 3, 5, 7, 10}, new int[] {4, 7}, 20, false),
        new Case(1, new int[] {3, 6, 9, 12, 14, 20}, new int[] {6, 12, 14}, 30, false),
        new Case(2, new int[] {2, 5, 7, 9, 12}, new int[] {4, 7}, 18, false),
        new Case(3, new int[] {4, 16}, new int[] {6}, 30, true, false, 4),
        new Case(4, new int[] {4, 10, 20, 30, 40, 50, 60, 63, 66, 70, 74}, new int[] {10, 30, 50, 63, 70}, 84, false),
    };

    /**
     * 0.51.0 트릭스터 · 피에로 (재설계안 2판 — 2026-11-06): 섀도 스와프 (1 환영을 던짐 → 4 꿰뚫음 → 12 다시 눌러 자리를 바꿈 → 14 등 뒤에서 찌름 · X) · 미라주 대시
     * (1 ~ 4 뛰어듦 → 4 내려서며 거울이 깨지듯 벰 → 16 다시 눌러 돌아옴) · 사우전드 팬텀 (환영 여섯 → 4 · 14 · 24 엇갈려 꿰뚫음 → 28 등 뒤로 → 34 거대한 X) ·
     * 미러 팰리스 (태세 → 단검 평타 — 4틱 뒤 거울 베기) · 와일드파이어 피스트 (순간 이동 → 2 벰 → 12 ~ 42 들불 환영 → 62 터짐) / 잭 인 더 박스 (2 던짐 · 4 떨어짐 ·
     * 6 열림 → 10 ~ 50 칼) · 핸드스프링 (0 ~ 8 재주넘기 → 8 내려침) · 벌룬 퍼레이드 (풍선 여섯 → 14 감음 → 24 펑) · 할루시네이트 (조명 → 10 인형이 내려섬 →
     * 20 · 40 · 60 뿅망치) · 그랜드 피날레 (4 거대한 상자 넷 → 10 열림 · 10 ~ 80 불꽃놀이 → 90 대폭죽)
     */
    private static final Case[] CASES_TR = {
        new Case(0, new int[] {2, 4, 8, 12, 14, 18}, new int[] {4, 14}, 28, false),
        new Case(1, new int[] {1, 3, 5, 10, 16, 18}, new int[] {4, 16}, 26, false),
        new Case(2, new int[] {2, 4, 14, 24, 28, 32, 34, 38}, new int[] {4, 24, 34}, 46, false),
        new Case(3, new int[] {4, 16}, new int[] {6}, 24, true, false, 8),
        new Case(4, new int[] {2, 6, 12, 22, 32, 42, 56, 62, 66}, new int[] {12, 42, 62}, 76, false),
    };
    private static final Case[] CASES_PI = {
        new Case(0, new int[] {2, 4, 6, 8, 10, 20, 30, 50}, new int[] {6, 10, 30}, 58, false),
        new Case(1, new int[] {1, 3, 5, 8, 10, 14}, new int[] {4, 8}, 22, false),
        new Case(2, new int[] {2, 6, 10, 14, 18, 24, 26}, new int[] {10, 24}, 34, false),
        new Case(3, new int[] {4, 10, 12, 16, 20, 30, 40, 60}, new int[] {10, 20, 40}, 70, false),
        new Case(4, new int[] {4, 8, 10, 20, 40, 60, 84, 90, 94}, new int[] {10, 40, 90}, 100, false),
    };

    /**
     * 2026-11-06 스톰 랜서 · 드라군: 크로스 게일 (2 · 6 비껴 찌름 → 엇갈린 자리의 소용돌이 → 26 터짐) · 게일 드라이브 (0 ~ 5 꿰뚫고 지나감 → 12 다시 눌러 돌아서며
     * 바람 창) · 템페스트 드릴 (6 ~ 36 바람 드릴 → 36 터짐) · 렌딩 게일 (2 휩쓸어 바람 창이 박힘 → 20 다시 눌러 뽑아 찢음) · 아이 오브 더 스톰 (10 폭풍의 눈 →
     * 14 ~ 54 찌름 → 70 터짐) / 웜테일 (6 휩쓸기 → 뜀 → 18 내리꽂기) · 하이 점프 (솟구침 · 떠 있음 → 16 다시 눌러 내리꽂음) · 임페일 (6 꿰어 들어 올림 → 20 내리꽂음) ·
     * 드래곤 브레스 (용의 혼 · 숨 → 14 불길) · 드래곤폴 (12 솟구침 → 40 내리꽂힘 → 46 충돌 → 56 불기둥)
     */
    private static final Case[] CASES_SL = {
        new Case(0, new int[] {2, 4, 6, 9, 14, 20, 26, 28}, new int[] {6, 14, 26}, 36, false),
        new Case(1, new int[] {1, 3, 5, 8, 12, 14, 16}, new int[] {4, 14}, 28, false),
        new Case(2, new int[] {2, 6, 12, 16, 26, 34, 36, 38}, new int[] {12, 26, 36}, 48, false),
        new Case(3, new int[] {1, 2, 4, 10, 20, 22, 24}, new int[] {2, 22}, 34, false),
        new Case(4, new int[] {4, 10, 12, 14, 24, 34, 44, 54, 64, 70, 74}, new int[] {10, 34, 70}, 86, false),
    };
    private static final Case[] CASES_DG = {
        new Case(0, new int[] {2, 5, 6, 8, 12, 16, 18, 20}, new int[] {6, 12, 18}, 30, false),
        new Case(1, new int[] {2, 6, 10, 14, 16, 18, 21, 24}, new int[] {10, 21}, 34, false),
        new Case(2, new int[] {2, 6, 10, 14, 18, 20, 22}, new int[] {6, 14, 20}, 32, false),
        new Case(3, new int[] {2, 6, 10, 14, 16, 18, 22}, new int[] {10, 16}, 30, false),
        new Case(4, new int[] {4, 10, 14, 18, 26, 34, 40, 43, 46, 50, 56, 60}, new int[] {10, 26, 46, 56}, 72, false),
    };

    /**
     * 2026-11-06 소울 리퍼 · 엑시큐셔너: 리핑 크레센트 (0 ~ 4 내달림 → 4 · 14 두 바퀴 — 영혼 14 라 첫 바퀴에 영혼 해방 · 망령 여섯) · 엄브럴 트레스패스 (2 스며듦 → 18 찢고
     * 나옴) · 바운티풀 하베스트 (6 꽂음 → 10 ~ 40 빨아들임 → 50 뜯어냄) · 랜턴 오브 소울즈 (8 · 24 · 40 · 56 사슬) · 페일 하베스트 (4 사신이 솟음 → 20 · 30 휩쓸기 →
     * 46 내리꽂음) / 컨뎀 (6 벰 — 앞 과녁은 체력 10% 라 처형) · 섀도 스토크 (0 ~ 8 뛰어듦 → 8 벰) · 길로틴 (단두대 날 → 22 내리침) · 원 라스트 데이 (태세) · 비포 선셋
     * (10 순간 이동 → 12 · 22 X — 앞 과녁 처형 → 36 다시 → 48 · 58)
     */
    private static final Case[] CASES_SR = {
        new Case(0, new int[] {1, 3, 4, 6, 10, 14, 16, 20}, new int[] {4, 14}, 30, false),
        new Case(1, new int[] {1, 2, 8, 14, 17, 18, 20}, new int[] {8, 18}, 30, false),
        new Case(2, new int[] {2, 6, 10, 20, 30, 46, 50, 52}, new int[] {10, 30, 50}, 62, false),
        new Case(3, new int[] {2, 8, 24, 40, 56}, new int[] {8, 40}, 70, false),
        new Case(4, new int[] {2, 4, 10, 16, 20, 24, 30, 40, 46, 50, 56}, new int[] {10, 20, 46}, 66, false),
    };
    private static final Case[] CASES_EX = {
        new Case(0, new int[] {2, 4, 6, 8, 12}, new int[] {4, 6}, 26, false),
        new Case(1, new int[] {1, 3, 6, 8, 10}, new int[] {3, 8}, 24, false),
        new Case(2, new int[] {2, 8, 14, 18, 20, 22, 24}, new int[] {14, 22}, 34, false),
        new Case(3, new int[] {4, 12}, new int[] {6}, 26, true),
        new Case(4, new int[] {2, 6, 10, 12, 16, 22, 26, 38, 48, 58}, new int[] {10, 22, 48}, 72, false),
    };

    /**
     * 2026-11-06 썬더로드 · 타이런트: 아크 스매시 (6 휩쓸기 → 16 갈래 — 과녁마다 심판 3 이라 벼락 · 갈라지는 벼락 · 보호막) · 썬더스텝 (사라짐 → 6 벼락으로 내리꽂힘) ·
     * 스톰 스파이어 (먹구름 · 조여 드는 고리 → 16 번개 기둥 → 26 물결) · 썬더헤드 (태세 — 평타에 먹구름 벼락) · 던브레이커 (10 사슬 → 30 새벽 · 돌 → 46 깨짐) /
     * 필리지 (8 내려침) · 섀클 볼트 (4 감음 → 10 들이받음) · 킹스 그립 (6 움켜쥠 → 10 · 20 · 30 쥐어짬 → 40 내리꽂음) · 콜로서스 (태세 — 거대해짐 · 왕관) ·
     * 스톨른 썬더 (10 · 20 · 30 빨아들임 → 32 뛰어오름 → 44 내려침) — 타이런트는 힘 4 에서
     */
    private static final Case[] CASES_TL = {
        new Case(0, new int[] {2, 6, 10, 16, 18}, new int[] {6, 16}, 28, false),
        new Case(1, new int[] {1, 3, 6, 8, 12}, new int[] {3, 6}, 24, false),
        new Case(2, new int[] {4, 10, 14, 16, 20, 26, 28}, new int[] {16, 26}, 40, false),
        new Case(3, new int[] {4, 16}, new int[] {6, 16}, 24, true),
        new Case(4, new int[] {4, 10, 14, 20, 30, 34, 40, 46, 50}, new int[] {10, 30, 46}, 66, false),
    };
    private static final Case[] CASES_TY = {
        new Case(0, new int[] {2, 5, 8, 10, 14}, new int[] {5, 8}, 22, false),
        new Case(1, new int[] {1, 4, 7, 10, 12}, new int[] {4, 10}, 24, false),
        new Case(2, new int[] {3, 6, 10, 20, 30, 38, 40, 44}, new int[] {10, 40}, 54, false),
        new Case(3, new int[] {4, 16}, new int[] {6, 16}, 24, true),
        new Case(4, new int[] {4, 10, 20, 30, 34, 40, 44, 48}, new int[] {20, 34, 44}, 62, false),
    };

    /**
     * 2026-11-06 세인트 · 워록 (앞 왼쪽에 아군 — 가짜 연결의 두 번째 플레이어: 세인트는 체력 45% 의 다친 아군을 고르게 · 감싸고 · 잇고 · 나비가 들름, 워록은 그 아군의 피를
     * 빼앗아 씀): 하트 어코드 (연꽃 → 6 빛줄기로 고르게 → 12 넘치는 빛) · 페탈 베일 (꽃잎이 아군에게 흘러가 4 감쌈) · 글리머윙 (8 · 24 · 40 적 · 16 · 32 · 48 아군) ·
     * 소울본드 (태세 — 금빛 리본) · 에버블룸 (꽃밭 · 꽃봉오리 → 20 · 40 · 60 꽃잎 폭풍 → 80 꽃이 핌) / 생귄 랜스 (8 날아감 → 12 꿰뚫음 · 피를 돌려줌) · 크림슨 마이어
     * (녹아내림 → 10 · 20 · 30 · 40 웅덩이 → 40 솟구침) · 블러드 노바 (10 · 20 · 30 빨아들임 → 40 터짐) · 애시본 (태세 — 불기둥 셋) · 블러드 뱅큇 (10 · 30 · 50 고동 →
     * 62 잔치 → 70 건배 · 터짐)
     */
    private static final Case[] CASES_SA = {
        new Case(0, new int[] {2, 6, 9, 12, 16}, new int[] {6, 12}, 24, false),
        new Case(1, new int[] {1, 3, 4, 8, 14}, new int[] {3, 8}, 22, false),
        new Case(2, new int[] {2, 8, 16, 24, 32, 40, 48, 52}, new int[] {8, 24, 40}, 60, false),
        new Case(3, new int[] {4, 16}, new int[] {6, 16}, 24, true),
        new Case(4, new int[] {4, 10, 20, 40, 60, 76, 80, 84, 92}, new int[] {20, 60, 80, 90}, 104, false),
    };
    private static final Case[] CASES_WK = {
        new Case(0, new int[] {2, 6, 8, 10, 12, 16}, new int[] {6, 12}, 26, false),
        new Case(1, new int[] {1, 2, 6, 10, 20, 30, 38, 40, 44}, new int[] {10, 40}, 54, false),
        new Case(2, new int[] {4, 10, 20, 30, 36, 40, 44}, new int[] {20, 40}, 54, false),
        new Case(3, new int[] {2, 6, 12, 16}, new int[] {6, 12}, 24, true),
        new Case(4, new int[] {4, 10, 30, 50, 62, 66, 70, 74}, new int[] {10, 50, 70}, 90, false),
    };
    /** 2026-11-06 세인트 · 워록 영상의 아군 자리 (앞 왼쪽 — 눈길에서 0.85칸 옆이라 겨눌 수 있음 · 앞 과녁을 가리지 않음) */
    private static final Vec3 ALLY_AT = new Vec3(-0.35, -60, 2.6);

    /**
     * 2026-11-06 피닉스 가드 · 다크 나이트 (다시 — 푸른 불사조 · 타락한 흑기사. 아군 — 가짜 연결의 두 번째 플레이어를 스킬마다 다른 자리에: prepare): 피닉스 배시 (방패를
     * 치켜들면 날개가 펼쳐짐 → 6 내리침 · 푸른 불꽃 부채 · 깃털) · 윙 커버 (앞 오른쪽 아군에게 불꽃 날개로 날아들어 → 10 내려앉음 · 날개가 아군을 감쌈) · 블루 인페르노
     * (불꽃을 모음 → 10 불꽃 고리 · 기둥 열둘 → 날개 둘이 고리를 돎 → 30 바깥으로 터짐) · 이그니션 (태세 — 등의 날개 · 8 막음 → 불꽃 · 불사조의 재가 아군 (35%) 에게 → 18
     * 맞음 → 불꽃) · 피닉스 디센트 (10 불기둥 → 13 불사조가 깨어나 하늘을 돎 → 34 내리꽂음 → 40 땅에 닿아 터짐 · 아군 보호막) / 크런치 (4 방패의 아가리 물기 → 12 다시
     * 누름 → 15 크게 물어 찢음) · 헝거 런지 (굶주림 가득 — 겨눈 뒤 과녁에게 뛰어들어 8 통째로 삼킴 → 끌고 감) · 피스트 (6 검은 혀 → 16 거대한 아가리) · 언홀리 프렌지
     * (태세 — 방패 평타가 물어뜯기) · 어비설 메이 (10 땅이 갈라지며 검은 혀 → 26 땅의 아가리가 삼킴 → 짓씹음 → 44 터뜨림)
     */
    private static final Case[] CASES_PH = {
        new Case(0, new int[] {2, 4, 6, 8, 12}, new int[] {4, 7}, 22, false),
        new Case(1, new int[] {1, 3, 6, 8, 10, 12, 16, 22}, new int[] {5, 10, 16}, 32, false),
        new Case(2, new int[] {4, 8, 10, 12, 16, 20, 26, 30, 32, 36}, new int[] {10, 20, 31}, 46, false),
        new Case(3, new int[] {4, 8, 10, 14, 18, 22}, new int[] {9, 19}, 28, true),
        new Case(4, new int[] {4, 10, 12, 16, 20, 26, 32, 35, 38, 40, 42, 46, 52}, new int[] {12, 24, 37, 41}, 64, false),
    };
    private static final Case[] CASES_DK = {
        new Case(0, new int[] {2, 4, 6, 10, 13, 15, 17, 22}, new int[] {4, 15}, 30, false),
        new Case(1, new int[] {1, 3, 5, 8, 10, 12, 15, 20}, new int[] {5, 9, 14}, 26, false),
        new Case(2, new int[] {2, 4, 6, 8, 11, 14, 16, 18, 22}, new int[] {7, 16}, 30, false),
        new Case(3, new int[] {4, 8, 12, 16}, new int[] {8, 14}, 22, true),
        new Case(4, new int[] {4, 10, 14, 20, 24, 26, 28, 34, 40, 44, 46, 50, 56}, new int[] {12, 26, 36, 45}, 70, false),
    };

    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active()) return;
        boolean film = RpgFilm.on() && (RpgFilm.listed("filmjob1") || RpgFilm.listed("filmjob2") || RpgFilm.listed("filmjob3")
                || RpgFilm.listed("filmjob4") || RpgFilm.listed("filmjob5") || RpgFilm.listed("filmjob6")
                || RpgFilm.listed("filmjob7") || RpgFilm.listed("filmjob8") || RpgFilm.listed("filmjob9") || RpgFilm.listed("filmjob10") || RpgFilm.listed("filmjob11")
                || RpgFilm.listed("filmjob12") || RpgFilm.listed("filmjob13") || RpgFilm.listed("filmjob14")); // 0.48.2 스킬 영상 (0.51.0 넷째 ~ 열넷째 묶음 = 2차 직업 — 검사 · 전사 · 마검사 · 매지션 · 궁수 · 암살자 · 창술사 · 사신 · 성기사 · 사제 · 수호기사)
        if (!film && (RpgFilm.on() || !RpgProdSmoke.only("job"))) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("xp set @a 40 levels");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.waitTicks(10);
            for (String s : List.of("str", "int", "vit", "dex")) server.runCommand("execute as @a run rpg set " + s + " 150");
            server.runCommand("item replace entity @a hotbar.0 with rpg:sword_l40_hero");
            ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
            home(server);
            ctx.waitTicks(10);
            if (film) {
                film(ctx, server, conn);
                ctx.setScreen(() -> null);
                LOG.info("[job-gallery] film done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
                if (!failures.isEmpty()) throw new AssertionError("job film: " + String.join(" / ", failures));
                return;
            }
            part(ctx, "trainer", () -> trainer(ctx, server, conn));
            part(ctx, "hud", () -> hud(ctx, server, conn));
            part(ctx, "swordsman", () -> skills(ctx, server, conn, "swordsman", CASES));
            part(ctx, "change", () -> change(ctx, server, conn));
            part(ctx, "spellblade", () -> skills(ctx, server, conn, "spellblade", CASES_SB));
            part(ctx, "stat", () -> stat(ctx));
            // 0.28.0 전사 · 매지션 · 궁수: 직업 · 무기 (핫바 셋째 칸) → 자원 HUD → 스킬 다섯
            part(ctx, "warrior", () -> {
                newJob(ctx, server, conn, "warrior", "rpg:axe_l40_hero", 100, sp -> JobTraitsMore.setRage(sp, 60));
                skills(ctx, server, conn, "warrior", CASES_WA);
            });
            part(ctx, "mage", () -> {
                newJob(ctx, server, conn, "mage", "rpg:wand_l40_hero", 3, sp -> JobTraitsMore.setChain(sp, 2));
                skills(ctx, server, conn, "mage", CASES_MG);
            });
            part(ctx, "archer", () -> {
                newJob(ctx, server, conn, "archer", "rpg:bow_l40_hero", 5, sp -> JobTraitsMore.setBreath(sp, 3));
                skills(ctx, server, conn, "archer", CASES_AR);
            });
            // 0.29.0 암살자 · 창술사 · 사신 (핫바 셋째 칸 — 단검 · 창 · 낫)
            part(ctx, "assassin", () -> {
                newJob(ctx, server, conn, "assassin", "rpg:dagger_l40_hero", 5, sp -> markHusks(sp, 3));
                skills(ctx, server, conn, "assassin", CASES_AS);
            });
            part(ctx, "lancer", () -> {
                newJob(ctx, server, conn, "lancer", "rpg:lance_l40_hero", 1, sp -> JobTraits3.setMomentum(sp, true));
                skills(ctx, server, conn, "lancer", CASES_LA);
            });
            part(ctx, "reaper", () -> {
                newJob(ctx, server, conn, "reaper", "rpg:scythe_l40_hero", 10, sp -> JobTraits3.setSouls(sp, 6));
                skills(ctx, server, conn, "reaper", CASES_RE);
            });
            // 0.30.0 성기사 · 사제 · 수호기사 (핫바 셋째 칸 — 망치 · 홀 · 방패)
            part(ctx, "paladin", () -> {
                newJob(ctx, server, conn, "paladin", "rpg:hammer_l40_hero", 5, sp -> judgeHusks(sp, 3));
                skills(ctx, server, conn, "paladin", CASES_PA);
            });
            part(ctx, "priest", () -> {
                newJob(ctx, server, conn, "priest", "rpg:scepter_l40_hero", 5, sp -> JobTraits4.setFaith(sp, 3));
                skills(ctx, server, conn, "priest", CASES_PR);
            });
            part(ctx, "guardian", () -> {
                newJob(ctx, server, conn, "guardian", "rpg:shield_l40_hero", 100, sp -> JobTraits4.setCounter(sp, JobTraits4.counterCap(sp, Jobs.job("guardian")) * 0.6));
                skills(ctx, server, conn, "guardian", CASES_GU);
            });
            server.runOnServer(s -> JobSystem.setJob(player(s), null));
            ctx.setScreen(() -> null);
        }
        LOG.info("[job-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("job gallery: " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 직업 교관 창 → 전직

    private void trainer(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> JobTrainer.open(player(s), null));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("직업 교관"), "trainer window")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("swordsman")));
        shot(ctx, "job_trainer_swordsman");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("spellblade")));
        shot(ctx, "job_trainer_spellblade");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("paladin")));
        shot(ctx, "job_trainer_paladin");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("swordsman")));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("확인"), "confirm window")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(13));
        shot(ctx, "job_trainer_confirm");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(11));
        ctx.waitTicks(2);
        shot(ctx, "job_change_02");
        ctx.waitTicks(8);
        shot(ctx, "job_change_10");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(4);
        shot(ctx, "job_change_14_3p");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(20);
        check(server.computeOnServer(s -> "swordsman".equals(JobSystem.jobOf(player(s)))), "trainer: swordsman (first job free)");
        check(ctx.computeOnClient(mc -> ClientJobBar.jobId().equals("swordsman") && ClientJobBar.gaugeMax() == 5 && !ClientJobBar.empty(0)),
                "client job bar: swordsman, 검세 max 5, slots filled");
    }

    // ------------------------------------------------------------ HUD

    private void hud(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        // 20렙 + 20렙 칼 (둘째 칸): 칸 4 · 5 (30 · 40렙) 만 잠김
        server.runCommand("xp set @a 20 levels");
        server.runCommand("item replace entity @a hotbar.1 with rpg:sword_l20_rare");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            mmorpg.fabric.RpgPlayers.update(sp, mmorpg.fabric.RpgData.of(sp).withLvmax(20));
            JobSystem.refresh(sp, true);
        });
        settle(ctx, conn);
        ctx.waitTicks(6);
        shot(ctx, "job_hud_locked");
        check(ctx.computeOnClient(mc -> ClientJobBar.lock(3).equals("30렙") && ClientJobBar.lock(4).equals("40렙") && ClientJobBar.lock(0).isEmpty()),
                "locks at 20: '" + ctx.computeOnClient(mc -> ClientJobBar.lock(0) + "' '" + ClientJobBar.lock(3) + "' '" + ClientJobBar.lock(4)) + "'");
        // 20렙에 40렙 칼 (첫째 칸): 칸 1 ~ 3 은 든 무기의 레벨로 잠김 ("무기 40렙" — HUD 는 "무기")
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
        shot(ctx, "job_hud_weapon_level");
        check(ctx.computeOnClient(mc -> ClientJobBar.lock(0).equals("무기 40렙") && ClientJobBar.lock(3).equals("30렙")),
                "weapon level lock: '" + ctx.computeOnClient(mc -> ClientJobBar.lock(0)) + "'");
        server.runCommand("xp set @a 40 levels");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            mmorpg.fabric.RpgPlayers.update(sp, mmorpg.fabric.RpgData.of(sp).withLvmax(40));
            JobTraits.setStacks(sp, 5);
            JobSystem.refresh(sp, true);
        });
        settle(ctx, conn);
        ctx.waitTicks(6);
        shot(ctx, "job_hud_stacks");
        check(ctx.computeOnClient(mc -> ClientJobBar.gaugeValue() == 5 && ClientJobBar.lock(4).isEmpty()), "HUD: 5 stacks, all unlocked at 40");
        // 맨손: 칸 모두 잠김 (칼)
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[4]);
        ctx.waitTicks(10);
        shot(ctx, "job_hud_nosword");
        check(ctx.computeOnClient(mc -> ClientJobBar.lock(0).equals("칼")), "bare hand locks the slots ('" + ctx.computeOnClient(mc -> ClientJobBar.lock(0)) + "')");
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.waitTicks(10);
        // 스킬 칸 창의 직업 줄 (칸 설명)
        ctx.getInput().pressKey(MmorpgClient.skillBarKey);
        if (waitScreen(ctx, sc -> sc instanceof mmorpg.fabric.client.SkillBarScreen, "skill bar window")) {
            hover(ctx, sc -> ((mmorpg.fabric.client.SkillBarScreen) sc).jobCenter(1));
            shot(ctx, "job_window_slot");
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    // ------------------------------------------------------------ 스킬 다섯

    private void skills(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String job, Case[] cases) {
        Jobs.Job j = Jobs.job(job);
        for (Case c : cases) {
            for (boolean third : new boolean[] {false, true}) {
                Jobs.Skill k = j.skill(c.slot());
                int[] shots = third ? c.shots3p() : c.shots();
                home(server);
                server.runOnServer(s -> prepare(player(s), job, k.id()));
                // 0.29.0 과녁 사이를 지나가는 스킬 (잔영 · 장대 도약 · 망령 걸음) 은 3인칭을 앞에서 — 뒤 카메라는 지나간 뒤 과녁 몸 안에 들어감
                CameraType cam3 = FRONT_3P.contains(k.id()) ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK;
                ctx.runOnClient(mc -> mc.options.setCameraType(third ? cam3 : CameraType.FIRST_PERSON));
                ctx.waitTicks(12);
                if (TURN_AFTER.contains(k.id())) {
                    // 사진첩만: 과녁을 등지고 씀 (순간 이동이 과녁에서 멀어지고 분신이 과녁 앞에 남음) — 순간 이동 뒤 다시 돌아서 찍음
                    ctx.runOnClient(mc -> turn(mc, 180F));
                    ctx.waitTicks(3);
                }
                ctx.getInput().holdKey(o -> o.keyShift);
                ctx.waitTicks(2);
                server.runOnServer(s -> beforeCast(player(s), k.id()));
                ctx.getInput().pressKey(MmorpgClient.skillKeys[c.slot()]);
                ctx.getInput().releaseKey(o -> o.keyShift);
                int t = 0;
                boolean turned = false;
                for (int shot : shots) {
                    t = againUntil(ctx, c, k.id(), t, shot);
                    if (shot > t) {
                        ctx.waitTicks(shot - t);
                        t = shot;
                    }
                    if (!turned && shot >= 4 && TURN_AFTER.contains(k.id())) {
                        // 사진첩만: 다시 돌아섬 (클라이언트 시점 — 스킬은 이미 끝난 순간 이동이라 그대로) → 분신 · 끌려오는 과녁이 보임
                        ctx.runOnClient(mc -> turn(mc, 170F));
                        ctx.waitTicks(1);
                        t++;
                        turned = true;
                    }
                    ctx.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "job_%s_%02d%s", k.id(), shot, third ? "_3p" : "")).withSize(1280, 720));
                    if (k.id().startsWith("reaper_ult") || k.id().startsWith("reaper_avatar")) {
                        // 0.29.0 solo 표 (같은 주인 · 번호의 그림은 하나만 — 문이 빈 사진 원인 찾기)
                        String solo = ctx.computeOnClient(mc -> mmorpg.fabric.client.AddonParticle.soloDebug());
                        LOG.info("[job-gallery] solo {} {}{}: {}", k.id(), shot, third ? " 3p" : "", solo);
                    }
                }
                if (c.stance() || c.buff()) {
                    // 태세 · 강화 뒤 평타 (근접 = 왼쪽 클릭 — 눈앞 과녁, 활 · 완드 = 오른쪽 클릭 — 바람 타기로 물러나면 과녁이 손 닿는 거리 밖): 명경지수 두 번째 베기 ·
                    // 마검 각성 잔상 · 투혼 · 마나 과부하 광선 · 정조준 관통 · 바람 타기 두 발
                    boolean ranged = ranged(job);
                    ctx.getInput().pressMouse(ranged ? InputConstants.MOUSE_BUTTON_RIGHT : InputConstants.MOUSE_BUTTON_LEFT);
                    ctx.waitTicks(c.hitAt());
                    t += c.hitAt();
                    ctx.takeScreenshot(TestScreenshotOptions.of("job_" + k.id() + "_hit" + (third ? "_3p" : "")).withSize(1280, 720));
                }
                t = againUntil(ctx, c, k.id(), t, c.total());
                if (c.total() > t) ctx.waitTicks(c.total() - t);
                boolean cast = server.computeOnServer(s -> JobSkills.cooldownLeft(player(s), k.id()) > 0);
                double dealt = server.computeOnServer(s -> {
                    double sum = 0;
                    for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(30), h -> true)) sum += TestDummies.dealt(h);
                    return sum;
                });
                boolean stance = server.computeOnServer(s -> JobTraits.stanceOn(player(s), k.id()));
                LOG.info("[job-gallery] {} {} cast={} dealt={} stance={}", k.id(), third ? "3p" : "1p", cast, String.format(Locale.ROOT, "%.1f", dealt), stance);
                String tag = k.id() + (third ? " (3p)" : "");
                check(cast, tag + " cast");
                if (NO_DAMAGE.contains(k.id())) {
                    // 0.30.0 회복 · 보호막 · 성흔: 써졌는지만 (위의 cast)
                } else if (c.stance()) check(stance && dealt > 0, tag + " stance on, hit after it");
                else if (c.buff()) check(dealt > 0, tag + " buffed basic hit (" + String.format(Locale.ROOT, "%.1f", dealt) + ")");
                else check(dealt > 0, tag + " dealt damage (" + String.format(Locale.ROOT, "%.1f", dealt) + ")");
            }
        }
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runOnServer(s -> JobSystem.setJob(player(s), job)); // 태세 · 자원 지움
        ctx.waitTicks(5);
    }

    /** 활 · 완드 직업 (태세 · 강화 뒤 평타 = 오른쪽 클릭) — 0.51.0 아크메이지 · 네크로맨서도 완드, 스나이퍼 · 배틀 아처는 활 */
    private static boolean ranged(String job) {
        return job.equals("archer") || job.equals("mage") || job.equals("arcmage") || job.equals("necromancer") || job.equals("sniper") || job.equals("battlearcher");
    }

    /** 스킬 하나 쓰기 전 (서버): 직업 다시 (앞 칸의 태세 지움) · 재사용 대기 · 체력 · 자원 · 과녁 · 표식 */
    private static void prepare(ServerPlayer sp, String job, String id) {
        JobSystem.setJob(sp, job); // 0.29.0 앞 칸의 태세를 지움 (창벽 자동 찌르기 · 사신 현신 그림자가 궁극기 사진에 남았음)
        JobSkills.resetCooldowns(sp);
        RuneCast.resetCooldowns(sp);
        RpgHealth.healFull(sp);
        if (id.equals("swordsman_flurry")) JobTraits.setStacks(sp, 3); // 검기
        if (id.equals("warrior_crush")) JobTraitsMore.setRage(sp, 60); // 분노 30 을 씀
        if (id.equals("archer_rapid")) JobTraitsMore.setBreath(sp, 3); // 4발
        JobTraitsMore.setChain(sp, 0);
        spawnHusks(sp.level());
        if (id.equals("assassin_bloom") || id.equals("assassin_ult")) markHusks(sp, 5); // 낙화 · 흑야: 표식 가득
        if (id.equals("reaper_burst")) JobTraits3.setSouls(sp, 10); // 혼 가득 → 큰 폭발
        if (id.equals("paladin_smite")) judgeHusks(sp, 3); // 심판 3 + 2 → 벼락 · 기절 별
        if (id.equals("priest_hymn") || id.equals("priest_ult")) JobTraits4.setFaith(sp, 3); // 신앙 3 → +30%
        if (id.equals("guardian_bash")) JobTraits4.setCounter(sp, 30); // 반격 게이지가 첫 타에
        // 0.51.0 2차 직업 (재설계안 2판): 칼집 섬광 — 투지 3 (6칸 · 열 줄) · 불의 울타리 — 투지 8 (치명 — 금빛 치명 숫자) · 블러드 나이트는 체력 60% (피의 갈증 —
        // 공격 속도 · 흡혈이 오른 모습)
        if (id.equals("swordmaster_draw")) JobTraits.setStacks(sp, 3);
        if (id.equals("swordmaster_echo")) JobTraits.setStacks(sp, 8);
        if (id.startsWith("bloodknight_")) sp.setHealth(sp.getMaxHealth() * 0.6F);
        // 0.51.0 재설계안 2판 전사 2차: 워로드는 분노 60 (게이지) · 2026-11-07 프로스트 레이더는 앞 과녁 서리 3 (게이지 — 스킬이 서리를 더해 얼어붙는 모습)
        if (id.startsWith("warlord_")) JobTraitsMore.setRage(sp, 60);
        if (id.startsWith("frostraider_")) {
            List<Husk> near = sp.level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 3.2, 0.5, -60, 3.2).inflate(0.8), h -> true);
            if (!near.isEmpty()) mmorpg.fabric.job.JobTraits16.setFrost(sp, near.get(0), 3);
        }
        // 0.51.0 소드 콜러: 데려가라는 앞 7칸에 꽂아 둔 칼 (그리로 날아감)
        if (id.equals("swordcaller_shift")) mmorpg.fabric.job.JobTraits7.testStuck(sp, new Vec3(0.5, -60, 7.5), 200);
        // 0.51.0 아크메이지 (재설계안 2판): 스타 볼텍스는 연쇄 4 + 별 넷 (다섯째 별이 떠 별자리 완성)
        if (id.equals("arcmage_vortex")) {
            JobTraitsMore.setChain(sp, 4);
            mmorpg.fabric.job.JobTraits8.testStars(sp, 4);
        }
        // 0.51.0 네크로맨서 (재설계안 2판): 무덤은 스킬마다 비움 — 그레이브 콜은 둘레에 무덤 셋 (16 에 다시 눌러 망자 셋), 소울 미스트는 뒤에 다 솟은 망자 둘 (표식으로 몰려듦)
        if (job.equals("necromancer")) {
            mmorpg.fabric.job.JobTraits8.clearGraves();
            mmorpg.fabric.job.NecroCrew.testClear(sp);
        }
        if (id.equals("necromancer_call")) {
            for (Vec3 g : new Vec3[] {new Vec3(-2.5, -60, 1.0), new Vec3(3.5, -60, 1.0), new Vec3(0.5, -60, -2.5)}) mmorpg.fabric.job.JobTraits8.testGrave(sp.level(), g, 200);
        }
        if (id.equals("necromancer_mist")) {
            mmorpg.fabric.job.NecroCrew.testRaise(sp, new Vec3(-1.5, -60, -1.0));
            mmorpg.fabric.job.NecroCrew.testRaise(sp, new Vec3(2.5, -60, -1.0));
        }
        // 0.51.0 스나이퍼: 정령 교감 5 (하트시커 +30% · 스네어 샷이 내려앉으면 8 — 게이지)
        if (id.startsWith("sniper_")) JobTraitsMore.setBreath(sp, 5);
        // 2026-11-06 트릭스터 2판: 섀도 스와프는 표식 6 (꿰뚫어 7 — 가득 찬 터뜨림) · 와일드파이어 피스트는 표식 3 (들불 환영 넷이 7 까지)
        if (id.equals("trickster_swap")) markHusks(sp, 6);
        if (id.equals("trickster_ult")) markHusks(sp, 3);
        // 2026-11-06 스톰 랜서: 창끝 바람 3 (게이지 — 다음 끝자락 평타가 바람 4 → 질풍 창)
        if (job.equals("stormlancer")) mmorpg.fabric.job.JobTraits11.setWind(sp, 3);
        // 2026-11-06 소울 리퍼: 영혼 6 (리핑 크레센트는 14 — 첫 바퀴에 영혼 해방 · 망령 여섯), 엑시큐셔너: 컨뎀 · 비포 선셋은 앞 과녁이 체력 10% 의 쓰러지는 몹
        // (처형 — 큰 숫자 · 해골 · 재사용 대기 초기화 · 비포 선셋 다시 쓰기)
        if (job.equals("soulreaper")) JobTraits3.setSouls(sp, id.equals("soulreaper_crescent") ? 14 : 6);
        // 2026-11-06 썬더로드: 아크 스매시 · 스톰 스파이어는 과녁마다 심판 3 (휩쓸기 · 갈래 / 기둥 +2 → 벼락 — 갈라지는 벼락 · 보호막), 타이런트: 빼앗은 힘 4 (게이지 · 몸 크기)
        if (id.equals("thunderlord_arc") || id.equals("thunderlord_spire")) judgeHusks(sp, 3);
        if (job.equals("tyrant")) mmorpg.fabric.job.JobTraits13.setPower(sp, 4);
        // 2026-11-06 세인트 · 워록: 앞 왼쪽 아군 (세인트는 다친 아군 45% — 고르게 · 나비가 들름, 워록은 가득 — 빼앗을 피) · 세인트 글리머윙 · 에버블룸은 신앙 3 (+30%)
        if (job.equals("saint") || job.equals("warlock")) {
            ServerPlayer a = filmAlly(sp);
            mmorpg.fabric.job.JobTraits14.testClear(a);
            mmorpg.fabric.job.JobTraits14.testClear(sp);
            a.snapTo(ALLY_AT.x, ALLY_AT.y, ALLY_AT.z, 180F, 0F);
            a.setYHeadRot(180F);
            a.setYBodyRot(180F);
            a.setDeltaMovement(Vec3.ZERO);
            a.removeAllEffects();
            a.setHealth(a.getMaxHealth() * (job.equals("saint") ? 0.45F : 1F));
        }
        if (id.equals("saint_wing") || id.equals("saint_ult")) JobTraits4.setFaith(sp, 3);
        if (id.equals("executioner_condemn") || id.equals("executioner_ult")) {
            for (Husk x : sp.level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 3.2, 0.5, -60, 3.2).inflate(0.8), x -> true)) {
                x.removeTag(TestDummies.TAG);
                x.setHealth(x.getMaxHealth() * 0.1F);
            }
        }
        // 2026-11-06 피닉스 가드 · 다크 나이트: 과녁을 처음 자리에 새로 세우고 (앞 스킬이 옮긴 과녁) 굶주림 · 보호막을 지우고 아군을 스킬마다 다른 자리에 — 윙 커버는 앞 오른쪽
        // 멀리 (겨눠 곁으로 날아듦 — 앞 과녁을 비킴), 이그니션은 오른쪽 앞 가까이 체력 35% (가장 위험 — 막은 피해가 불꽃 깃털로 날아감), 디센트는 왼쪽 앞 (날개가 덮음), 나머지는
        // 오른쪽 뒤. 헝거 런지도 앞 과녁을 비킴 (뒤 과녁에게 멀리 뛰어듦). 피닉스 가드는 반격 60% · 다크 나이트는 굶주림 40% (헝거 런지는 가득 — 통째로 삼킴)
        if (job.equals("phoenix") || job.equals("darkknight")) {
            spawnHusks(sp.level());
            mmorpg.fabric.job.JobTraits15.testClear(sp);
            ServerPlayer a = filmAlly(sp);
            RpgHealth.clearShield(sp);
            RpgHealth.clearShield(a);
            Vec3 at = switch (id) {
                case "phoenix_wing" -> new Vec3(1.6, -60, 6.8);
                case "phoenix_ignition" -> new Vec3(-1.8, -60, 1.6);
                case "phoenix_ult" -> new Vec3(3.0, -60, 3.6);
                default -> new Vec3(-2.5, -60, -1.5);
            };
            a.snapTo(at.x, at.y, at.z, 180F, 0F);
            a.setYHeadRot(180F);
            a.setYBodyRot(180F);
            a.setDeltaMovement(Vec3.ZERO);
            a.removeAllEffects();
            a.setHealth(a.getMaxHealth() * (id.equals("phoenix_ignition") ? 0.35F : 1F));
            if (id.equals("phoenix_wing") || id.equals("darkknight_lunge")) {
                moveHusk(sp, new Vec3(0.5, -60, 3.2), new Vec3(-1.6, -60, 2.0));
                // 앞으로 움직이는 스킬: 따라가는 카메라 바로 앞에 서는 오른쪽 과녁을 왼쪽 뒤로 (영상 1 — 내려앉음 · 물기가 그 과녁에 가렸음)
                moveHusk(sp, new Vec3(4.0, -60, 2.5), new Vec3(-4.2, -60, 5.4));
            }
            if (job.equals("phoenix")) JobTraits4.setCounter(sp, JobTraits4.counterCap(sp, Jobs.job("phoenix")) * 0.6);
            else mmorpg.fabric.job.JobTraits15.setHunger(sp, id.equals("darkknight_lunge") ? 100 : 40);
        }
    }

    /** 2026-11-06 수호기사 2차 영상: from 에 가장 가까운 과녁 허스크를 to 로 옮김 (나를 봄) */
    private static void moveHusk(ServerPlayer sp, Vec3 from, Vec3 to) {
        Husk h = huskNear(sp, from);
        if (h == null) return;
        h.snapTo(to.x, to.y, to.z, 180F, 0F);
        h.setYHeadRot(180F);
        h.setYBodyRot(180F);
    }

    /** 2026-11-06 수호기사 2차 영상: at 에 가장 가까운 과녁 허스크 (없으면 null) */
    private static Husk huskNear(ServerPlayer sp, Vec3 at) {
        List<Husk> near = sp.level().getEntitiesOfClass(Husk.class, new AABB(at, at).inflate(3), h -> h.isAlive());
        near.sort(java.util.Comparator.comparingDouble(h -> h.distanceToSqr(at)));
        return near.isEmpty() ? null : near.get(0);
    }

    /** 2026-11-06 수호기사 2차 영상: 과녁 from 이 who 를 침 (받는 피해 처리 — 과녁은 움직이지 않는 허스크라 직접) */
    private static void filmHit(ServerPlayer who, Husk from, float amount) {
        if (who == null || from == null || who.isRemoved() || from.isRemoved()) return;
        float hp = who.getHealth();
        who.setHealth(RpgHealth.onLoss(who, who.damageSources().mobAttack(from), hp, hp - amount));
    }

    /** 2026-11-06 이그니션 영상: 과녁 from 이 who 를 치고 who 가 웅크려 막음 (서버의 웅크림 — 그 틱만) */
    private static void filmBlock(ServerPlayer who, Husk from, float amount) {
        if (who == null || from == null || who.isRemoved() || from.isRemoved()) return;
        boolean was = who.isShiftKeyDown();
        who.setShiftKeyDown(true);
        float hp = who.getHealth();
        who.setHealth(RpgHealth.onLoss(who, who.damageSources().mobAttack(from), hp, hp - amount));
        who.setShiftKeyDown(was);
    }

    /** 0.51.0 키를 누르기 바로 전 (서버) */
    private static void beforeCast(ServerPlayer sp, String id) {
        if (id.equals("sniper_mark")) {
            // 0.51.0 헌터스 마크: 쓴 뒤 (태세가 이어 맞힌 수를 처음부터 셈) 가장 가까운 과녁에 둘째까지 맞힌 것으로 → 태세 뒤 활 평타가 셋째 — 은빛 화살 (머리 위 은빛 표식 둘)
            mmorpg.fabric.Tasks.later(2, () -> {
                List<Husk> near = sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(6), h -> true);
                near.sort(java.util.Comparator.comparingDouble(h -> h.distanceToSqr(sp)));
                if (!near.isEmpty() && !sp.isRemoved()) mmorpg.fabric.job.JobTraits9.testMarkHits(sp, near.get(0), 2);
            });
        }
        if (id.equals("swordmaster_parry")) {
            // 0.51.0 받아넘기기: 4틱 뒤 앞 과녁이 침 (받는 피해 처리 — 막아서 깎이지 않음) → 받아침 (과녁은 움직이지 않는 허스크라 직접)
            mmorpg.fabric.Tasks.later(4, () -> {
                List<Husk> near = sp.level().getEntitiesOfClass(Husk.class, sp.getBoundingBox().inflate(6), h -> true);
                near.sort(java.util.Comparator.comparingDouble(h -> h.distanceToSqr(sp)));
                if (near.isEmpty() || sp.isRemoved()) return;
                float hp = sp.getHealth();
                RpgHealth.onLoss(sp, sp.damageSources().mobAttack(near.get(0)), hp, hp - 4F);
            });
        }
        // 2026-11-06 이그니션: 맞는 장면 (과녁이 직접 침) — 8 에 막음 (불꽃 · 불사조의 재가 다친 아군에게) · 18 에 맞음 (불꽃)
        switch (id) {
            case "phoenix_ignition" -> {
                mmorpg.fabric.Tasks.later(8, () -> filmBlock(sp, huskNear(sp, new Vec3(0.5, -60, 3.2)), 8F));
                mmorpg.fabric.Tasks.later(18, () -> filmHit(sp, huskNear(sp, new Vec3(0.5, -60, 3.2)), 8F));
            }
            default -> {}
        }
    }

    /**
     * 0.51.0 다시 쓰는 스킬: 쓴 뒤 at 틱이 오면 같은 칸을 다시 누름 (사진첩 — 직업 스킬 키는 Shift + 룬 칸 키라 2틱 앞에서 Shift 를 누르고 있음. 영상 1: Shift 없이 눌러
     * 다시 쓰기가 나가지 않았음 — 창이 열린 채 재사용 대기가 0)
     */
    private static int againUntil(ClientGameTestContext ctx, Case c, String id, int t, int upTo) {
        for (int a : AGAIN.getOrDefault(id, new int[0])) {
            if (a <= t || a > upTo) continue;
            if (a - 2 > t) ctx.waitTicks(a - 2 - t);
            ctx.getInput().holdKey(o -> o.keyShift);
            ctx.waitTicks(Math.min(2, a - t));
            ctx.getInput().pressKey(MmorpgClient.skillKeys[c.slot()]);
            ctx.getInput().releaseKey(o -> o.keyShift);
            t = a;
        }
        return t;
    }

    // ------------------------------------------------------------ 0.48.2 스킬 영상 (only=film,filmjob1 · 2 · 3 · 4 · 5 · 6 · 7 · 8 · 9 · 10)

    /** 영상 묶음마다 직업 (넷 · 넷 · 셋 · 둘 · 둘 · 둘 · 둘 · 둘 · 둘 · 둘 — 넷째부터 2차 직업) */
    private static final String[][] FILM_JOBS = {{"swordsman", "spellblade", "warrior", "mage"}, {"archer", "assassin", "lancer", "reaper"},
        {"paladin", "priest", "guardian"}, {"swordmaster", "bloodknight"}, {"warlord", "frostraider"}, {"arcaneblade", "swordcaller"}, {"arcmage", "necromancer"},
        {"sniper", "battlearcher"}, {"trickster", "pierrot"}, {"stormlancer", "dragoon"}, {"soulreaper", "executioner"}, {"thunderlord", "tyrant"}, {"saint", "warlock"},
        {"phoenix", "darkknight"}};

    private void film(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        RpgFilm f = new RpgFilm(ctx);
        for (int g = 0; g < FILM_JOBS.length; g++) {
            if (!RpgFilm.listed("filmjob" + (g + 1))) continue;
            if (g == 3) part(ctx, "film advance window", () -> advanceWindow(ctx, server, conn)); // 0.51.0 2차 전직 창 (사진)
            for (String job : FILM_JOBS[g]) {
                part(ctx, "film " + job, () -> {
                    switch (job) {
                        case "swordsman" -> newJob(ctx, server, conn, job, "rpg:sword_l40_hero", 5, sp -> JobTraits.setStacks(sp, 5));
                        case "spellblade" -> newJob(ctx, server, conn, job, "rpg:sword_l40_hero", 3, sp -> {});
                        case "warrior" -> newJob(ctx, server, conn, job, "rpg:axe_l40_hero", 100, sp -> JobTraitsMore.setRage(sp, 60));
                        case "mage" -> newJob(ctx, server, conn, job, "rpg:wand_l40_hero", 3, sp -> JobTraitsMore.setChain(sp, 2));
                        case "archer" -> newJob(ctx, server, conn, job, "rpg:bow_l40_hero", 5, sp -> JobTraitsMore.setBreath(sp, 3));
                        case "assassin" -> newJob(ctx, server, conn, job, "rpg:dagger_l40_hero", 5, sp -> markHusks(sp, 3));
                        case "lancer" -> newJob(ctx, server, conn, job, "rpg:lance_l40_hero", 1, sp -> JobTraits3.setMomentum(sp, true));
                        case "reaper" -> newJob(ctx, server, conn, job, "rpg:scythe_l40_hero", 10, sp -> JobTraits3.setSouls(sp, 6));
                        case "paladin" -> newJob(ctx, server, conn, job, "rpg:hammer_l40_hero", 5, sp -> judgeHusks(sp, 3));
                        case "priest" -> newJob(ctx, server, conn, job, "rpg:scepter_l40_hero", 5, sp -> JobTraits4.setFaith(sp, 3));
                        case "swordmaster" -> newJob(ctx, server, conn, job, "rpg:sword_l40_hero", 8, sp -> JobTraits.setStacks(sp, 6));
                        case "bloodknight" -> newJob(ctx, server, conn, job, "rpg:sword_l40_hero", 5, sp -> {});
                        case "warlord" -> newJob(ctx, server, conn, job, "rpg:axe_l40_hero", 100, sp -> JobTraitsMore.setRage(sp, 60));
                        case "frostraider" -> newJob(ctx, server, conn, job, "rpg:axe_l40_hero", 5, sp -> {});
                        case "arcaneblade", "swordcaller" -> newJob(ctx, server, conn, job, "rpg:sword_l40_hero", 5, sp -> {});
                        case "arcmage" -> newJob(ctx, server, conn, job, "rpg:wand_l40_hero", 5, sp -> JobTraitsMore.setChain(sp, 2));
                        case "necromancer" -> newJob(ctx, server, conn, job, "rpg:wand_l40_hero", 4, sp -> {});
                        case "sniper" -> newJob(ctx, server, conn, job, "rpg:bow_l40_hero", 8, sp -> JobTraitsMore.setBreath(sp, 5));
                        case "battlearcher" -> newJob(ctx, server, conn, job, "rpg:bow_l40_hero", 3, sp -> {});
                        case "trickster" -> newJob(ctx, server, conn, job, "rpg:dagger_l40_hero", 7, sp -> markHusks(sp, 4));
                        case "pierrot" -> newJob(ctx, server, conn, job, "rpg:dagger_l40_hero", 4, sp -> {}); // 2026-11-06 쇼타임 게이지 = 소환물 수 (넷까지)
                        case "stormlancer" -> newJob(ctx, server, conn, job, "rpg:lance_l40_hero", 4, sp -> mmorpg.fabric.job.JobTraits11.setWind(sp, 3)); // 2026-11-06 창끝 바람 (넷까지)
                        case "dragoon" -> newJob(ctx, server, conn, job, "rpg:lance_l40_hero", 6, sp -> {}); // 2026-11-06 비상 게이지 = 발밑 땅에서의 높이 (여섯까지)
                        case "soulreaper" -> newJob(ctx, server, conn, job, "rpg:scythe_l40_hero", 15, sp -> JobTraits3.setSouls(sp, 10)); // 2026-11-06 영혼 (열다섯까지 — 막대)
                        case "executioner" -> newJob(ctx, server, conn, job, "rpg:scythe_l40_hero", 10, sp -> {}); // 2026-11-06 처형 게이지 = 마지막으로 친 적의 잃은 체력
                        case "thunderlord" -> newJob(ctx, server, conn, job, "rpg:hammer_l40_hero", 5, sp -> judgeHusks(sp, 3)); // 2026-11-06 심판 (다섯 — 성기사와 같음)
                        case "tyrant" -> newJob(ctx, server, conn, job, "rpg:hammer_l40_hero", 10, sp -> mmorpg.fabric.job.JobTraits13.setPower(sp, 6)); // 2026-11-06 빼앗은 힘 (열 — 왕관 칸)
                        case "saint" -> newJob(ctx, server, conn, job, "rpg:scepter_l40_hero", 5, sp -> {
                            JobTraits4.setFaith(sp, 3); // 2026-11-06 신앙 (다섯 — 사제와 같음)
                            filmAlly(sp);
                        });
                        case "warlock" -> newJob(ctx, server, conn, job, "rpg:scepter_l40_hero", 5, sp -> filmAlly(sp)); // 2026-11-06 생명 착취 게이지 = 다음 스킬이 빼앗을 몫 (다섯까지)
                        case "phoenix" -> newJob(ctx, server, conn, job, "rpg:shield_l40_hero", 100, sp -> { // 2026-11-06 반격 (수호기사와 같음 — 최대 대비 100)
                            JobTraits4.setCounter(sp, JobTraits4.counterCap(sp, Jobs.job("phoenix")) * 0.6);
                            filmAlly(sp);
                        });
                        case "darkknight" -> newJob(ctx, server, conn, job, "rpg:shield_l40_hero", 100, sp -> { // 2026-11-06 굶주림 (100)
                            mmorpg.fabric.job.JobTraits15.setHunger(sp, 40);
                            filmAlly(sp);
                        });
                        default -> newJob(ctx, server, conn, job, "rpg:shield_l40_hero", 100,
                                sp -> JobTraits4.setCounter(sp, JobTraits4.counterCap(sp, Jobs.job("guardian")) * 0.6));
                    }
                    filmSkills(ctx, server, job, casesOf(job), f);
                    if (job.equals("saint") || job.equals("warlock") || job.equals("phoenix") || job.equals("darkknight")) server.runOnServer(s -> {
                        ServerPlayer me = player(s);
                        for (ServerPlayer p : new ArrayList<>(s.getPlayerList().getPlayers())) if (p != me) s.getPlayerList().remove(p);
                    });
                });
            }
        }
        server.runOnServer(s -> JobSystem.setJob(player(s), null));
    }

    /**
     * 2026-11-06 영상 카메라 (거리 · 높이 · 보는 높이 · 따라가는 몫 — 없으면 RpgFilm 처음 값): 피닉스 가드 · 다크 나이트의 앞으로 움직이는 스킬은 조금 멀리 · 덜 따라감, 둘레
     * 5칸 고리는 조금 멀리, 하늘을 도는 불사조 (불기둥 9칸 · 가슴 7.5칸 · 날개 끝 10.5칸 — 볏이 13칸쯤) 는 멀리서 올려다봄, 땅의 거대한 아가리 (높이 약 8칸) 는 멀리 · 높이
     */
    private static double[] filmView(String id) {
        return switch (id) {
            case "swordmaster_ult" -> new double[] {11.5, 3.0, 5.6, 0.4}; // 2026-11-07 엑스칼리버: 15칸 빛의 대검을 올려다봄 · 앞 24칸 검격
            case "swordmaster_echo" -> new double[] {8.5, 3.2, 1.2, 0.3}; // 블레이드 에코: 둘레를 옮겨 다니다 처음 자리로 돌아옴 — 덜 따라감
            case "archer_ult" -> new double[] {11.0, 3.4, 2.8, 0.4}; // 2026-11-07 스피릿 스탬피드: 등 뒤 사슴 정령 왕 (4.4칸) · 앞으로 내달리는 무리 · 정령 꽃
            case "archer_focus" -> new double[] {7.5, 2.8, 1.2, 0.6}; // 와일드 헌트: 곁의 늑대 정령 둘 · 과녁에 뛰어오름
            case "sniper_ult" -> new double[] {9.0, 3.4, 3.0, 0.5}; // 사일런트 아울: 등 뒤 위 부엉이 정령 (2.5칸 위) · 부엉이 화살
            case "arcmage_ult" -> new double[] {12.0, 3.6, 5.0, 0.4}; // 콘스텔레이션: 과녁 위 8.5칸 하늘의 국자 일곱 별 · 내리꽂히는 별빛 — 올려다봄
            case "arcmage_orbit" -> new double[] {7.0, 2.8, 1.6, 0.6}; // 셀레스티얼 오빗: 둘레를 도는 별 · 과녁에 떨어지는 별똥 · 땅의 별자리
            case "frostraider_ult" -> new double[] {12.0, 3.4, 5.0, 0.4}; // 핌불윈터: 하늘의 빙하 도끼 (18칸 위) 가 내리꽂혀 빙하가 솟음 — 올려다봄
            case "frostraider_throw" -> new double[] {8.0, 3.0, 2.2, 0.6}; // 프로스트 바이트: 던져 박히고 되돌아오는 도끼가 다 보이게
            case "phoenix_ult" -> new double[] {12.5, 3.0, 7.0, 0.4};
            case "darkknight_ult" -> new double[] {9.5, 3.0, 3.4, 0.5};
            case "phoenix_inferno" -> new double[] {8.0, 3.0, 1.4, 0.6};
            case "phoenix_wing", "darkknight_lunge" -> new double[] {7.0, 2.6, 1.3, 0.7};
            default -> null;
        };
    }

    private static Case[] casesOf(String job) {
        return switch (job) {
            case "swordsman" -> CASES;
            case "spellblade" -> CASES_SB;
            case "warrior" -> CASES_WA;
            case "mage" -> CASES_MG;
            case "archer" -> CASES_AR;
            case "assassin" -> CASES_AS;
            case "lancer" -> CASES_LA;
            case "reaper" -> CASES_RE;
            case "paladin" -> CASES_PA;
            case "priest" -> CASES_PR;
            case "swordmaster" -> CASES_SM;
            case "bloodknight" -> CASES_BK;
            case "warlord" -> CASES_WL;
            case "frostraider" -> CASES_FR;
            case "arcaneblade" -> CASES_AB;
            case "swordcaller" -> CASES_SC;
            case "arcmage" -> CASES_AM;
            case "necromancer" -> CASES_NC;
            case "sniper" -> CASES_SN;
            case "battlearcher" -> CASES_BA;
            case "trickster" -> CASES_TR;
            case "pierrot" -> CASES_PI;
            case "stormlancer" -> CASES_SL;
            case "dragoon" -> CASES_DG;
            case "soulreaper" -> CASES_SR;
            case "executioner" -> CASES_EX;
            case "thunderlord" -> CASES_TL;
            case "tyrant" -> CASES_TY;
            case "saint" -> CASES_SA;
            case "warlock" -> CASES_WK;
            case "phoenix" -> CASES_PH;
            case "darkknight" -> CASES_DK;
            default -> CASES_GU;
        };
    }

    /** 직업 스킬 다섯을 같은 입력 (Shift + 칸 키) 으로 쓰고 틱마다 한 장 — 태세 · 강화는 뒤에 평타까지 */
    private void filmSkills(ClientGameTestContext ctx, TestServerContext server, String job, Case[] cases, RpgFilm f) {
        Jobs.Job j = Jobs.job(job);
        for (Case c : cases) {
            Jobs.Skill k = j.skill(c.slot());
            home(server);
            server.runOnServer(s -> prepare(player(s), job, k.id()));
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(12);
            double[] cam = filmView(k.id());
            if (cam != null) f.view(cam[0], cam[1], cam[2]).follow(cam[3]);
            f.begin(server, k.id(), k.name(), j.name() + " · 스킬 " + (c.slot() + 1) + (c.slot() == 4 ? " (궁극기)" : ""), 2.5);
            f.frames(4);
            if (TURN_AFTER.contains(k.id())) {
                // 0.51.0 영상: 과녁을 등지고 씀 (사진첩과 같음) — 영상 카메라는 처음 방향 그대로 몸을 따라가므로 카메라 쪽으로 순간 이동하면 원래 자리의 분신 · 환영과
                // 과녁이 카메라 앞에 남음 (쓴 뒤 돌아서는 것은 카메라가 돌지 않아 환영이 화면 밖 그대로였음 — 영상 3)
                ctx.runOnClient(mc -> turn(mc, 180F));
            }
            ctx.getInput().holdKey(o -> o.keyShift);
            f.frames(2);
            server.runOnServer(s -> beforeCast(player(s), k.id()));
            ctx.getInput().pressKey(MmorpgClient.skillKeys[c.slot()]);
            f.cast();
            ctx.getInput().releaseKey(o -> o.keyShift);
            for (int a : AGAIN.getOrDefault(k.id(), new int[0])) {
                // 0.51.0 다시 쓰는 스킬: 그 틱에 같은 칸을 다시 누름 (Shift + 칸 키 — 2틱 앞에서 Shift · 영상의 0초는 처음 누른 때 그대로)
                f.until(a - 2);
                ctx.getInput().holdKey(o -> o.keyShift);
                f.until(a);
                ctx.getInput().holdKey(MmorpgClient.skillKeys[c.slot()]);
                ctx.getInput().releaseKey(MmorpgClient.skillKeys[c.slot()]);
                f.frames(1);
                ctx.getInput().releaseKey(o -> o.keyShift);
            }
            f.until(c.total());
            if (c.stance() || c.buff()) {
                boolean ranged = ranged(job);
                ctx.getInput().pressMouse(ranged ? InputConstants.MOUSE_BUTTON_RIGHT : InputConstants.MOUSE_BUTTON_LEFT);
                f.frames(c.hitAt() + 10);
            }
            double dealt = server.computeOnServer(s -> {
                double sum = 0;
                for (Husk h : player(s).level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(30), h -> true)) sum += TestDummies.dealt(h);
                return sum;
            });
            f.end(dealt);
            if (cam != null) f.view(6.0, 2.1, 1.0).follow(1.0); // RpgFilm 처음 값으로
            // 2026-11-06 엑시큐셔너 컨뎀: 앞 과녁을 처형하면 그 스킬의 재사용 대기가 초기화 (영상 1 — 쓴 것을 재사용 대기로 볼 수 없음)
            boolean cast = server.computeOnServer(s -> JobSkills.cooldownLeft(player(s), k.id()) > 0) || k.id().equals("executioner_condemn");
            check(cast, k.id() + " (film) cast");
        }
        server.runOnServer(s -> JobSystem.setJob(player(s), job));
        ctx.waitTicks(5);
    }

    /** 0.51.0 2차 전직 창 (사진): 50렙 검사 → 직업 교관 → 위 줄 2차 전직 칸 → 각성 · 1차 · 반전 카드 (설명) → 확인 창 → 각성 (연출) */
    private void advanceWindow(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        home(server);
        server.runCommand("xp set @a 50 levels");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            mmorpg.fabric.RpgPlayers.update(sp, mmorpg.fabric.RpgData.of(sp).withLvmax(50));
            JobSystem.setJob(sp, "swordsman");
            JobTrainer.open(sp, null);
        });
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("직업 교관"), "trainer window (advance)")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.ADV_SLOT));
        shot(ctx, "job_trainer_advance_button");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.ADV_SLOT));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("2차 전직"), "advance window")) return;
        shot(ctx, "job_advance_window");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.AWAKEN_SLOT));
        shot(ctx, "job_advance_awaken");
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.INVERT_SLOT));
        shot(ctx, "job_advance_invert");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.AWAKEN_SLOT));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("확인"), "advance confirm")) return;
        shot(ctx, "job_advance_confirm");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(11));
        ctx.waitTicks(4);
        shot(ctx, "job_advance_04");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(10);
        shot(ctx, "job_advance_14_3p");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(16);
        check(server.computeOnServer(s -> "swordmaster".equals(JobSystem.jobOf(player(s)))), "advance: swordmaster (first second job free)");
        check(ctx.computeOnClient(mc -> ClientJobBar.jobId().equals("swordmaster") && ClientJobBar.jobName().contains("소드마스터") && ClientJobBar.gaugeMax() == 8),
                "client bar: 소드마스터, 투지 max 8 — '" + ctx.computeOnClient(mc -> ClientJobBar.jobName()) + "'");
        shot(ctx, "job_hud_swordmaster");
    }

    // ------------------------------------------------------------ 0.28.0 새 직업 (운영자 /job set 과 같은 길) · 자원 HUD

    private void newJob(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String job, String item, int gaugeMax,
            java.util.function.Consumer<ServerPlayer> gauge) {
        home(server);
        server.runCommand("item replace entity @a hotbar.2 with " + item);
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[2]);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            JobSystem.setJob(sp, job);
            gauge.accept(sp);
            JobSystem.refresh(sp, true);
        });
        settle(ctx, conn);
        ctx.waitTicks(8);
        shot(ctx, "job_hud_" + job);
        check(ctx.computeOnClient(mc -> ClientJobBar.jobId().equals(job) && ClientJobBar.gaugeMax() == gaugeMax && !ClientJobBar.empty(4) && ClientJobBar.lock(0).isEmpty()),
                job + ": client bar (gauge max " + gaugeMax + ", slots, unlocked) — '" + ctx.computeOnClient(mc -> ClientJobBar.jobId() + "' max "
                        + ClientJobBar.gaugeMax() + " lock '" + ClientJobBar.lock(0)) + "'");
    }

    // ------------------------------------------------------------ 직업 바꾸기 (300 은화)

    private void change(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        home(server);
        server.runOnServer(s -> {
            Wallet.set(player(s), Economy.Kind.SILVER, 500);
            JobTrainer.open(player(s), null);
        });
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("직업 교관"), "trainer window (change)")) return;
        hover(ctx, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("spellblade")));
        shot(ctx, "job_trainer_change_card");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(JobTrainer.slotOf("spellblade")));
        if (!waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && c.form().title().contains("바꾸기"), "change confirm")) return;
        shot(ctx, "job_trainer_change_confirm");
        click(ctx, conn, sc -> ((ChestFormScreen) sc).slotCenter(11));
        ctx.waitTicks(6);
        shot(ctx, "job_change_sb_06");
        ctx.waitTicks(20);
        check(server.computeOnServer(s -> "spellblade".equals(JobSystem.jobOf(player(s))) && Wallet.get(player(s), Economy.Kind.SILVER) == 200),
                "changed to spellblade for 300 silver");
        check(ctx.computeOnClient(mc -> ClientJobBar.jobId().equals("spellblade") && ClientJobBar.gaugeMax() == 3), "client: spellblade, 각인 max 3");
        shot(ctx, "job_hud_spellblade");
    }

    // ------------------------------------------------------------ 스탯 창

    private void stat(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window")) {
            ctx.waitTicks(4);
            shot(ctx, "job_stat_spellblade");
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    // ------------------------------------------------------------ 도구

    private static void home(TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 10");
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 2026-11-06 세인트 · 워록 영상의 아군 (가짜 연결의 두 번째 플레이어 — RpgDuelGallery.rival, 없으면 세움 · 직업 영상이 끝나면 내보냄) */
    private static ServerPlayer filmAlly(ServerPlayer sp) {
        MinecraftServer s = sp.level().getServer();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) if (p != sp) return p;
        ServerPlayer a = RpgDuelGallery.rival(s, "Ally", ALLY_AT, 180F);
        // 나와 같은 40렙 · 스탯 150 (영상 1: 새 플레이어의 작은 최대 체력이라 회복 · 빼앗음 숫자가 +1 · −0.8 로 보였음)
        a.setExperienceLevels(40);
        mmorpg.fabric.RpgPlayers.update(a, mmorpg.fabric.RpgData.of(a).withLvmax(40));
        for (String k : new String[] {"str", "dex", "vit", "int"}) mmorpg.fabric.RpgPlayers.setStat(a, k, 150);
        return a;
    }

    /** 0.29.0 암살자: 과녁 허스크 (없으면 세움) 마다 표식 n — 가까운 것이 게이지 대상 (마지막에 찍음) */
    /** 사진첩만: 클라이언트 시점을 deg 도 돌림 (보간 없이 바로) */
    private static void turn(net.minecraft.client.Minecraft mc, float deg) {
        if (mc.player == null) return;
        float yaw = mc.player.getYRot() + deg;
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setYHeadRot(yaw);
    }

    /** 0.30.0 성기사: 과녁 허스크마다 심판 n — 가까운 것이 게이지 대상 (마지막에 찍음) */
    private static void judgeHusks(ServerPlayer sp, int n) {
        List<Husk> list = sp.level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(30), h -> true);
        if (list.isEmpty()) {
            spawnHusks(sp.level());
            list = sp.level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(30), h -> true);
        }
        list.sort(java.util.Comparator.comparingDouble(h -> -h.distanceToSqr(sp)));
        for (Husk h : list) JobTraits4.setJudges(sp, h, n);
    }

    private static void markHusks(ServerPlayer sp, int n) {
        List<Husk> list = sp.level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(30), h -> true);
        if (list.isEmpty()) {
            spawnHusks(sp.level());
            list = sp.level().getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(30), h -> true);
        }
        list.sort(java.util.Comparator.comparingDouble(h -> -h.distanceToSqr(sp)));
        for (Husk h : list) JobTraits3.setMarks(sp, h, n);
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt) — 나를 봄 */
    private static void spawnHusks(ServerLevel level) {
        for (Husk h : level.getEntitiesOfClass(Husk.class, new AABB(0.5, -60, 4, 0.5, -60, 4).inflate(48), h -> true)) h.discard();
        TestDummies.reset();
        for (Vec3 at : TARGETS) {
            Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
            if (h == null) continue;
            TestDummies.make(h);
            h.snapTo(at.x, at.y, at.z, 180F, 0F);
            h.setYHeadRot(180F);
            h.setYBodyRot(180F);
            level.addFreshEntity(h);
        }
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[job-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[job-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.getInput().releaseKey(o -> o.keyShift);
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[job-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[job-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private boolean waitScreen(ClientGameTestContext ctx, java.util.function.Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 60);
            ctx.waitTicks(3);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open");
            return false;
        }
    }

    private static void settle(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] q = sc == null ? null : where.apply(sc);
            if (q == null) throw new AssertionError("no point on screen");
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        hover(ctx, where);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        settle(ctx, conn);
    }

    @SuppressWarnings("unused")
    private static boolean waitFor(ClientGameTestContext ctx, java.util.function.Predicate<Minecraft> test, int ticks) {
        try {
            ctx.waitFor(mc -> mc.player != null && test.test(mc), ticks);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }
}
