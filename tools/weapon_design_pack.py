#!/usr/bin/env python3
"""새 무기 디자인 묶음 (다듬어 주실 분께 넘기는 파일): tools/weapon_art 의 12단계 디자인 20개 (창 · 낫 · 전투 망치 · 성스러운 홀 × 일반 · 희귀 ·
고귀 · 영웅 · 초월) → 폴더 + zip. 디자인마다 모델 (.geo.json) · 텍스처 (.png) · 아이콘 (.icon.png) · 미리보기 (옆 · 앞 · 비스듬히 · 텍스처),
한눈에 보기 · 안내 (지킬 것 · 돌려주는 법).

  python3 tools/weapon_design_pack.py <출력 폴더> [--game <게임 화면 그림>]
"""
import argparse
import json
import os
import shutil
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import weapon_preview  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
RULES_MORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules_more.json")

TYPES = [("lance", "창", "힘 + 민첩"), ("scythe", "낫", "민첩 + 마력"), ("hammer", "전투 망치", "체력 + 힘"), ("scepter", "성스러운 홀", "마력 + 체력")]
GRADES = [("common", "일반"), ("rare", "희귀"), ("noble", "고귀"), ("hero", "영웅"), ("transcend", "초월")]


def design_of(t, g):
    return f"{t}_lv0" if g == "common" else f"{t}_{g}_lv0"


GUIDE = """# 12단계 새 무기 디자인 — 창 · 낫 · 전투 망치 · 성스러운 홀

지금 게임에 들어 있는 것은 임시 디자인입니다. 다듬어 같은 이름으로 돌려주시면 그대로 바꿔 넣습니다.

## 들어 있는 것
- `고귀_영웅_초월/` — 12개
- `일반_희귀/` — 8개 (일반 · 희귀 임시 디자인 — 같이 다듬으셔도 되고, 그대로 두셔도 됩니다)
- 디자인마다 `<이름>.geo.json` (베드락 모델 — 애드온 무기 모델과 같은 형식, Blockbench 에서 열림), `<이름>.png` (텍스처),
  `<이름>.icon.png` (가방 아이콘 32 × 32), `<이름>_미리보기.png` (옆 · 앞 · 비스듬히 · 텍스처, 노란 띠 = 손이 잡는 자리)
- `한눈에_보기.png` (20개), `게임_화면.png` (손에 든 모습 — 게임 화면 시험에서 찍은 사진)

## 이름 → 무기
{table}

## 지켜 주실 것 (게임에 그대로 들어가게)
1. **뼈대**: 파일 안의 뼈 (root · body · rightArm · rightItem …) 와 `weapon` 뼈의 회전 중심 `(-6, 13, 0)` · 회전 `(60, 0, 0)` 은 그대로 둡니다.
   손에 든 자세 (1인칭 · 3인칭) 가 이 뼈대로 계산됩니다. 상자는 `weapon` 뼈 안에 (또는 그 아래 자식 뼈 — 회전 없이).
   뼈대가 빠진 파일 (weapon 뼈만) 도 받지만 그때는 애드온 뼈대 기준으로 맞춥니다.
2. **손잡이 자리**: 손이 잡는 곳은 y 8 ~ 18, x = -6 · z = 0 둘레입니다 (미리보기의 노란 띠). 무기 끝은 +y, 베는 날 · 때리는 면은 -z 쪽 (앞) 입니다.
   길이 · 손잡이 자리를 크게 바꾸셔도 되지만 그러면 손 자세를 다시 맞춥니다 (말씀만 해 주세요).
3. **텍스처**: 지금은 16 × 16 팔레트 (칸 하나 = 색 하나, 상자 면마다 한 칸) 입니다. 다듬으실 때는 크기를 키워 (32 · 64 · 128 …) 직접
   칠하셔도 됩니다 — 면 UV (per-face) · 상자 UV (box UV) 모두 받습니다. `description` 의 `texture_width` · `texture_height` 를 텍스처 크기와
   같게 (또는 같은 비율로) 해 주세요.
4. **빛나는 칸**: 알파 102 (반투명) 픽셀이 빛나는 칸입니다 (애드온과 같음 — 밤에도 밝게). 자바 아이템 모델은 상자 단위로 빛나므로,
   빛나는 부분은 따로 상자로 만들어 주세요 (상자 면 넓이의 절반 넘게 빛나는 픽셀이면 그 상자 전체가 빛남).
5. **투명 픽셀** (알파 0) 은 구멍입니다 (보이지 않음).
6. **회전**: 자바 아이템 모델의 상자 회전은 한 축 · 22.5° 단위 (-45 · -22.5 · 22.5 · 45°) 만 됩니다. 그 밖의 회전은 가장 가까운 값으로
   바뀝니다 (바꿔 넣을 때 알려 드림). 비스듬한 모양은 지금처럼 상자를 계단처럼 쌓는 것이 가장 안전합니다.
7. **빛 알갱이 자리** (영웅 · 초월): `weapon` 뼈의 로케이터 `fx` — 무기에서 빛 알갱이가 떠오르는 곳입니다. 옮기셔도 됩니다.
8. **크기**: 지금 크기 정도를 권합니다 (너무 크면 1인칭에서 화면을 가림 — 크기 · 자세는 받은 뒤 맞출 수 있음).

## 돌려주실 때
- 같은 파일 이름으로 `.geo.json` · `.png` (아이콘을 직접 그리셨으면 `.icon.png` 도 — 없으면 모델에서 다시 그림). 일부만 주셔도 됩니다.
- 받으면: 바꿔 넣기 → 변환 (맞지 않는 회전 등은 알려 드림) → 게임 화면 시험으로 1 · 3인칭 · 스킬 그림 (던지는 창 · 망치 등 날아가는 무기
  그림도 모델에서 다시 그림) 확인 → 릴리스.

## Blockbench 로 여실 때
파일 → 열기 → `<이름>.geo.json` (Bedrock Model) → 같은 폴더의 `<이름>.png` 를 텍스처 칸에 끌어다 놓기.
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--game", help="게임 화면 그림 (손에 든 모습 모음)")
    a = ap.parse_args()
    names = json.load(open(RULES_MORE, encoding="utf-8"))["WEAPON_NAMES"]
    levels = json.load(open(RULES_MORE, encoding="utf-8"))["TYPE_ONLY"]
    root = os.path.join(a.out, "무기_디자인_12단계")
    if os.path.exists(root):
        shutil.rmtree(root)
    rows = ["| 파일 이름 | 무기 | 종류 · 스탯 | 등급 · 레벨 |", "|---|---|---|---|"]
    labels = {}
    order = []
    for t, tname, stat in TYPES:
        for g, gname in GRADES:
            d = design_of(t, g)
            lv = levels[t][g]
            lv_text = f"{lv[0]} ~ {lv[-1]}렙" if len(lv) > 3 else " · ".join(str(x) for x in lv) + "렙"
            rows.append(f"| `{d}` | {names[t][g]} | {tname} ({stat}) | {gname} {lv_text} |")
            labels[d] = f"{names[t][g]} ({gname})"
            order.append((d, g))
    for d, g in order:
        sub = os.path.join(root, "일반_희귀" if g in ("common", "rare") else "고귀_영웅_초월")
        os.makedirs(sub, exist_ok=True)
        for ext in (".geo.json", ".png", ".icon.png"):
            shutil.copy(os.path.join(ART, d + ext), os.path.join(sub, d + ext))
        img, m = weapon_preview.preview(d)
        img.save(os.path.join(sub, f"{d}_미리보기.png"))
        for w in m.warnings:
            print(f"경고 {d}: {w}")
    weapon_preview.sheet_of([d for d, _ in order], os.path.join(root, "한눈에_보기.png"), cols=5, labels=labels)
    if a.game:
        shutil.copy(a.game, os.path.join(root, "게임_화면.png"))
    with open(os.path.join(root, "안내.md"), "w", encoding="utf-8") as fh:
        fh.write(GUIDE.replace("{table}", "\n".join(rows)))
    zpath = os.path.join(a.out, "무기_디자인_12단계.zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for base, _, files in os.walk(root):
            for f in sorted(files):
                p = os.path.join(base, f)
                z.write(p, os.path.relpath(p, a.out))
    print(zpath, os.path.getsize(zpath))


if __name__ == "__main__":
    main()
