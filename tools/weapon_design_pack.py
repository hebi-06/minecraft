#!/usr/bin/env python3
"""무기 디자인 묶음 (다듬어 주실 분께 넘기는 파일) → 폴더 + zip.

  python3 tools/weapon_design_pack.py <출력 폴더> [--game <게임 화면 그림>]            # 12단계 새 무기 20개 (창 · 낫 · 전투 망치 · 성스러운 홀)
  python3 tools/weapon_design_pack.py <출력 폴더> --all [--shots <CI 사진 폴더>]      # 무기 디자인 전부 51개 (기존 6종 · 마검 · 새 4종)
  python3 tools/weapon_design_pack.py <출력 폴더> --all --skip lance,scythe,hammer,scepter --paint --name <이름> [--shots …]
                                                                      # 일부 종류만 · 칠하기용 텍스처 (상자 면마다 따로 칸)

디자인마다 모델 (.geo.json) · 텍스처 (.png) · 아이콘 (.icon.png) · 미리보기 (옆 · 앞 · 비스듬히 · 텍스처), 한눈에 보기 · 안내 (지킬 것 · 돌려주는 법).
--all 은 종류별 폴더 · 등급 표 모양 한눈에 보기 · 디자인마다 게임 화면 (--shots: CI 사진첩의 손에 든 모습 — ci-logs 의 shots/ui_*_w_<디자인>_fp|3p.png ·
shots/w12_*_wm_hold_<종류>_<등급>[_3p].png, 잘라서 JPEG).
"""
import argparse
import glob
import json
import os
import re
import shutil
import sys
import zipfile

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402
import weapon_paint  # noqa: E402
import weapon_preview  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
RULES = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules.json")
RULES_MORE = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules_more.json")

TYPES = [("lance", "창", "힘 + 민첩"), ("scythe", "낫", "민첩 + 마력"), ("hammer", "전투 망치", "체력 + 힘"), ("scepter", "성스러운 홀", "마력 + 체력")]
GRADES = [("common", "일반"), ("rare", "희귀"), ("noble", "고귀"), ("hero", "영웅"), ("transcend", "초월")]
STAT_KO = {"str": "힘", "dex": "민첩", "vit": "체력", "int": "마력"}
# 등급 글자 색 (자바 모드 — §f · §9 · §d · §6 · §c, BedrockText)
GRADE_RGB = {"common": (255, 255, 255), "rare": (85, 85, 255), "noble": (255, 85, 255), "hero": (255, 170, 0), "transcend": (255, 85, 85)}


def design_of(t, g):
    return f"{t}_lv0" if g == "common" else f"{t}_{g}_lv0"


def level_text(lv):
    return f"{lv[0]} ~ {lv[-1]}렙" if len(lv) > 3 else " · ".join(str(x) for x in lv) + "렙"


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

GUIDE_ALL = """# {title} — {count}개

{intro} 다듬어 같은 이름으로 돌려주시면 그대로 바꿔 넣습니다. 일부만 주셔도 됩니다.

## 들어 있는 것
- 종류별 폴더 {folders}개 ({first} … {last}) — 디자인마다
  - `<이름>.geo.json` 베드락 모델 (애드온 무기 모델과 같은 형식 — Blockbench 에서 열림)
  - `<이름>.png` 텍스처 · `<이름>.icon.png` 가방 아이콘 (32 × 32)
  - `<이름>_미리보기.png` 옆 · 앞 · 비스듬히 · 텍스처 (노란 띠 = 손이 잡는 자리)
{files_extra}{shots_line}- `한눈에_보기.png` — {count}개 (줄 = 무기 종류, 칸 = 등급)
- 디자인 하나를 같은 종류 · 등급의 여러 레벨 무기가 같이 씁니다 (예: 철검 0 ~ 40렙 7개 = `sword_lv0` 하나).

## 이름 → 무기
{table}

## 지켜 주실 것 (게임에 그대로 들어가게)
1. **뼈대**: 파일 안의 뼈 (root · body · rightArm · rightItem …) 와 `weapon` 뼈의 회전 중심 `(-6, 13, 0)` · 회전은 그대로 둡니다.
   회전은 종류마다 다릅니다 — {rotations}.
   손에 든 자세 (1인칭 · 3인칭) 가 이 뼈대로 계산됩니다. 상자는 `weapon` 뼈 안에 (또는 그 아래 자식 뼈).
   뼈대가 빠진 파일 (weapon 뼈만) 도 받습니다 (종류마다 위 회전으로 맞춤).
2. {shield_rule}
3. **손잡이 자리**: 손은 `weapon` 뼈 회전 중심 `(-6, 13, 0)` 둘레를 잡습니다 (미리보기의 노란 띠 — y 8 ~ 18). 긴 무기는 끝이 +y,
   도끼 · 낫 · 망치처럼 한쪽으로 치는 무기는 치는 쪽이 -z (앞) 입니다. 활 · 방패 · 완드는 지금 파일의 방향을 따라 주세요.
   길이 · 손잡이 자리를 크게 바꾸셔도 되지만 그러면 손 자세를 다시 맞춥니다 (말씀만 해 주세요).
4. {texture_rule}
5. **빛나는 칸**: 알파 102 (반투명) 픽셀이 빛나는 칸입니다 (밤에도 밝게). 자바 아이템 모델은 상자 단위로 빛나므로, 빛나는 부분은 따로
   상자로 만들어 주세요 (상자 면 넓이의 절반 넘게 빛나는 픽셀이면 그 상자 전체가 빛남).
6. **투명 픽셀** (알파 0) 은 구멍입니다 (보이지 않음).
7. **회전**: 자바 아이템 모델의 상자 회전은 한 축 · 22.5° 단위 (-45 · -22.5 · 22.5 · 45°) 만 됩니다. 그 밖의 회전은 가장 가까운 값으로
   바뀝니다 (바꿔 넣을 때 알려 드림). 비스듬한 모양은 지금처럼 상자를 계단처럼 쌓는 것이 가장 안전합니다.
8. **빛 알갱이 자리** (영웅 · 초월): `weapon` 뼈의 로케이터 `fx` — 무기에서 빛 알갱이가 떠오르는 곳입니다. 옮기셔도 됩니다.
9. **크기**: 지금 크기 정도를 권합니다 (너무 크면 1인칭에서 화면을 가림 — 크기 · 자세는 받은 뒤 맞출 수 있음).

## 돌려주실 때
- 같은 파일 이름으로 `.geo.json` · `.png` (아이콘을 직접 그리셨으면 `.icon.png` 도 — 없으면 모델에서 다시 그림).
- 받으면: 바꿔 넣기 → 변환 (맞지 않는 회전 등은 알려 드림) → 게임 화면 시험으로 1 · 3인칭 · 스킬 그림 (던지는 창 · 망치 등 날아가는 무기
  그림도 모델에서 다시 그림) 확인 → 릴리스.

## Blockbench 로 여실 때
파일 → 열기 → `<이름>.geo.json` (Bedrock Model) → 같은 폴더의 `<이름>.png` 를 텍스처 칸에 끌어다 놓기.
"""


# ------------------------------------------------------------ 규칙 (rules.json + rules_more.json — 둘째 단계는 통째로 바꿈)

def load_rules():
    base = json.load(open(RULES, encoding="utf-8"))
    more = json.load(open(RULES_MORE, encoding="utf-8"))

    def merged(key):
        out = dict(base.get(key, {}))
        out.update({k: v for k, v in more.get(key, {}).items()})
        return out

    return {"TYPES": merged("TYPES"), "TYPE_ONLY": merged("TYPE_ONLY"), "WEAPON_NAMES": merged("WEAPON_NAMES"),
            "GRADE_LEVELS": base["GRADE_LEVELS"], "TYPE_ORDER": base["TYPE_ORDER"]}


def catalog():
    """무기 종류 (게임 순서 — Weapons.allTypes: TYPE_ORDER 다음 TYPE_ONLY 종류) × 등급 → 디자인.
    [{type, name, stats, rows: [{grade, gname, design, weapon, levels}]}] — 디자인은 designs.json weapons (무기 ID → 디자인) 에서"""
    r = load_rules()
    weapons = json.load(open(os.path.join(ART, "designs.json"), encoding="utf-8"))["weapons"]
    order = list(r["TYPE_ORDER"]) + [t for t in r["TYPE_ONLY"] if not t.startswith("_") and t not in r["TYPE_ORDER"]]
    out = []
    for t in order:
        td = r["TYPES"][t]
        stats = " + ".join(STAT_KO[s] for s in [td["stat"]] + list((td.get("reqExtra") or {}).keys()))
        rows = []
        for g, gname in GRADES:
            only = r["TYPE_ONLY"].get(t)
            levels = (only or {}).get(g) if only is not None else r["GRADE_LEVELS"].get(g)
            if not levels:
                continue
            designs = {weapons.get(f"rpg:{t}_l{lv}_{g}") for lv in levels}
            if len(designs) != 1 or None in designs:
                raise SystemExit(f"{t} {g}: 레벨마다 디자인이 다르거나 없음 {designs}")
            rows.append({"grade": g, "gname": gname, "design": designs.pop(), "weapon": r["WEAPON_NAMES"][t][g], "levels": levels})
        out.append({"type": t, "name": td["name"], "stats": stats, "rows": rows})
    return out


def icon_path(t, row):
    """가방 아이콘 (32 × 32): tools/weapon_art/<디자인>.icon.png (12단계 디자인), 없으면 게임 아이템 그림 (애드온 아이콘 — 레벨마다 같음)"""
    p = os.path.join(ART, f"{row['design']}.icon.png")
    if os.path.exists(p):
        return p
    return os.path.join(ROOT, "src", "main", "resources", "assets", "rpg", "textures", "item", t, f"{t}_l{row['levels'][0]}_{row['grade']}.png")


def weapon_rotation_of(design):
    geo = json.load(open(os.path.join(ART, f"{design}.geo.json"), encoding="utf-8"))["minecraft:geometry"][0]
    w = next(b for b in geo["bones"] if b["name"].lower() == "weapon")
    return tuple(int(v) if float(v).is_integer() else float(v) for v in (w.get("rotation") or geo_tex.weapon_rotation(design)))


# ------------------------------------------------------------ 게임 화면 (CI 사진첩의 손에 든 모습)

def game_shots(folder):
    """디자인 → {"fp": 경로, "3p": 경로}. 기존 디자인: ui_NNNN_w_<디자인>_fp|3p.png (밤 사진 _night_ 은 뺌), 12단계: w12_NNNN_wm_hold_<종류>_<등급>[_3p].png"""
    out = {}
    for p in sorted(glob.glob(os.path.join(folder, "*.png"))):
        n = os.path.basename(p)[:-4]
        m = re.match(r"ui_\d+_w_(.+)_(fp|3p)$", n)
        if m and "_night" not in m.group(1):
            out.setdefault(m.group(1), {})[m.group(2)] = p
            continue
        m = re.match(r"w12_\d+_wm_hold_([a-z]+)_([a-z]+?)(_3p)?$", n)
        if m:
            out.setdefault(design_of(m.group(1), m.group(2)), {})["3p" if m.group(3) else "fp"] = p
    return out


def crop_shot(path, view):
    """사진에서 무기가 보이는 곳만: 1인칭 = 오른쪽 (무기를 든 손), 3인칭 = 가운데 (플레이어). 아래 알림 글자 줄은 뺌"""
    im = Image.open(path).convert("RGB")
    w, h = im.size
    bottom = 0.926 if w >= 900 else 1.0  # 12단계 사진 (960 × 540) 은 아래에 레벨 업 알림 글자
    if view == "fp":
        box = (0.36, 0.0, 1.0, bottom)
    elif w >= 900:
        box = (0.3125, 0.0, 0.6875, bottom)
    else:
        box = (0.3125, 0.1667, 0.6875, 0.9167)
    im = im.crop((int(box[0] * w), int(box[1] * h), int(box[2] * w), int(box[3] * h)))
    if im.size[1] > 360:
        im = im.resize((round(im.size[0] * 360 / im.size[1]), 360), Image.LANCZOS)
    return im


# ------------------------------------------------------------ 한눈에 보기 (줄 = 종류, 칸 = 등급)

def grid_sheet(cat, out, scale=4.0):
    font = weapon_preview.font
    cells = {}
    for t in cat:
        for row in t["rows"]:
            m = geo_tex.load(row["design"], os.path.join(ART, f"{row['design']}.geo.json"), os.path.join(ART, f"{row['design']}.png"))
            cells[(t["type"], row["grade"])] = (weapon_preview.render(m, (0.55, -0.35, 0.75), scale, pad=14, grip=False, min_w=0, bg=(0, 0, 0, 0)), row)
    cw = max(im.size[0] for im, _ in cells.values()) + 16
    lw, head, lab = 150, 46, 30
    heights = [max([cells[(t["type"], r["grade"])][0].size[1] for r in t["rows"]]) + lab for t in cat]
    W, H = lw + cw * len(GRADES), head + sum(heights)
    sheet = Image.new("RGBA", (W, H), weapon_preview.BG)
    d = ImageDraw.Draw(sheet)
    for i, (g, gname) in enumerate(GRADES):
        d.text((lw + i * cw + cw // 2, head // 2), gname, font=font(22), fill=GRADE_RGB[g] + (255,), anchor="mm")
    y = head
    for t, h in zip(cat, heights):
        d.line([(0, y), (W, y)], fill=(64, 67, 80, 255), width=1)
        d.text((14, y + 14), t["name"], font=font(22), fill=(255, 228, 150, 255))
        d.text((14, y + 44), t["stats"], font=font(15), fill=(190, 190, 200, 255))
        for i, (g, _) in enumerate(GRADES):
            c = cells.get((t["type"], g))
            if c is None:
                continue
            im, row = c
            x0 = lw + i * cw
            sheet.alpha_composite(im, (x0 + (cw - im.size[0]) // 2, y + (h - lab - im.size[1])))
            d.text((x0 + cw // 2, y + h - lab // 2 - 2), row["weapon"], font=font(15), fill=GRADE_RGB[g] + (255,), anchor="mm")
        y += h
    sheet.save(out)
    return out


# ------------------------------------------------------------ 묶기

def write_zip(out_dir, root):
    zpath = os.path.join(out_dir, os.path.basename(root) + ".zip")
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for base, dirs, files in os.walk(root):
            dirs.sort()
            for f in sorted(files):
                p = os.path.join(base, f)
                z.write(p, os.path.relpath(p, out_dir))
    print(zpath, os.path.getsize(zpath))
    return zpath


def pack_12(a):
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
            rows.append(f"| `{d}` | {names[t][g]} | {tname} ({stat}) | {gname} {level_text(levels[t][g])} |")
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
    return write_zip(a.out, root)


SHIELD_RULE = ("**방패의 `weapon_off` 뼈** (왼손) 는 애드온 (베드락) 용입니다. 자바 모드는 `weapon` 뼈만 쓰므로 `weapon` 만 고치셔도 "
               "됩니다.")
SHIELD_RULE_PAINT = "**방패**: 왼손용 `weapon_off` 뼈 (애드온 전용) 는 이 묶음에서 뺐습니다. 자바 모드는 `weapon` 뼈만 씁니다."
TEXTURE_RULE = ("**텍스처**: 지금은 16 × 16 팔레트 (칸 하나 = 색 하나, 상자 면마다 한 칸) 입니다. 다듬으실 때는 크기를 키워 (32 · 64 · 128 …) 직접\n"
                "   칠하셔도 됩니다 — 면 UV (per-face) · 상자 UV (box UV) 모두 받습니다. `description` 의 `texture_width` · `texture_height` 를 텍스처 크기와\n"
                "   같게 (또는 같은 비율로) 해 주세요.")
TEXTURE_RULE_PAINT = (
    "**텍스처 (칠하기용)**: 상자 면마다 따로 칸이 있는 텍스처로 바꿔 두었습니다 (모델 1단위 = {px}픽셀, 지금 색이 칠해져 있어 게임 모습은\n"
    "   그대로). 원래 팔레트는 여러 면이 한 칸을 같이 써서 한 면만 칠할 수 없었습니다.\n"
    "   - Blockbench: 그리기 (Paint) 탭에서 모델에 바로 칠합니다. 같은 색을 한꺼번에 바꾸려면 채우기 도구를 '색' 모드로.\n"
    "   - 그림 프로그램: `<이름>_텍스처_칸.png` (칸 지도 — 어느 칸이 어느 면인지, N E S W U D) 를 보고 `<이름>.png` 의 칸을 칠합니다.\n"
    "   - 더 세밀하게 칠하려면 PNG 를 2배 · 4배로 키워도 됩니다 (UV 는 그대로 — 게임이 맞춰 읽음). 칸을 옮기거나 새로 펼쳐도 됩니다\n"
    "     (면 UV · 상자 UV 모두 받음 — `texture_width` · `texture_height` 는 UV 기준 크기).")
FILES_PAINT = "  - `<이름>_텍스처_칸.png` 칸 지도 (어느 칸이 어느 면인지 — 그림 프로그램으로 칠할 때)\n"


def pack_all(a):
    cat = catalog()
    skip = {s.strip() for s in (a.skip or "").split(",") if s.strip()}
    unknown = skip - {t["type"] for t in cat}
    if unknown:
        raise SystemExit(f"모르는 종류: {', '.join(sorted(unknown))} (종류: {', '.join(t['type'] for t in cat)})")
    cat = [t for t in cat if t["type"] not in skip]
    shots = game_shots(a.shots) if a.shots else {}
    root = os.path.join(a.out, a.name or "무기_디자인_모음")
    if os.path.exists(root):
        shutil.rmtree(root)
    table = ["| 파일 이름 | 무기 | 종류 · 스탯 | 등급 · 레벨 | weapon 뼈 회전 |", "|---|---|---|---|---|"]
    rot_kinds = {}
    folders = []
    count = 0
    missing = []
    for i, t in enumerate(cat):
        folder = f"{i + 1:02d}_{t['name'].replace(' ', '_')}"
        folders.append(folder)
        sub = os.path.join(root, folder)
        os.makedirs(sub)
        for row in t["rows"]:
            d = row["design"]
            rot = weapon_rotation_of(d)
            rot_kinds.setdefault(rot, [])
            if t["name"] not in rot_kinds[rot]:
                rot_kinds[rot].append(t["name"])
            note = " (창 모양)" if not d.startswith(t["type"] if t["type"] != "wand" else "staff") else ""
            table.append(f"| `{d}` | {row['weapon']}{note} | {t['name']} ({t['stats']}) | {row['gname']} {level_text(row['levels'])} | "
                         f"({', '.join(str(v) for v in rot)}) |")
            if a.paint:
                weapon_paint.write(d, sub, px=a.px, font=weapon_preview.font)
            else:
                for ext in (".geo.json", ".png"):
                    shutil.copy(os.path.join(ART, d + ext), os.path.join(sub, d + ext))
            shutil.copy(icon_path(t["type"], row), os.path.join(sub, d + ".icon.png"))
            img, m = weapon_preview.preview(d, art=sub, title=f"{d} — {row['weapon']} ({t['name']} · {row['gname']})")
            img.save(os.path.join(sub, f"{d}_미리보기.png"))
            for w in m.warnings:
                print(f"경고 {d}: {w}")
            for view, label in (("fp", "1인칭"), ("3p", "3인칭")):
                p = shots.get(d, {}).get(view)
                if p:
                    crop_shot(p, view).save(os.path.join(sub, f"{d}_게임_{label}.jpg"), quality=88)
                elif a.shots:
                    missing.append(f"{d} {view}")
            count += 1
    if missing:
        print("게임 화면 없음:", ", ".join(missing))
    grid_sheet(cat, os.path.join(root, "한눈에_보기.png"))
    rotations = ", ".join(f"{' · '.join(names)} `({', '.join(str(v) for v in rot)})`" for rot, names in rot_kinds.items())
    shots_line = "  - `<이름>_게임_1인칭.jpg` · `<이름>_게임_3인칭.jpg` 게임에서 손에 든 모습 (게임 화면 시험 사진)\n" if a.shots else ""
    kinds = " · ".join(t["name"] for t in cat)
    if skip:
        title, intro = f"무기 디자인 ({kinds})", f"지금 게임 (자바 모드) 에 들어 있는 {kinds} 디자인입니다."
    else:
        title, intro = "무기 디자인 모음 (기존 6종 · 마검 · 12단계 새 4종)", "지금 게임 (자바 모드) 에 들어 있는 무기 디자인 전부입니다."
    guide = (GUIDE_ALL.replace("{title}", title).replace("{intro}", intro).replace("{count}", str(count))
             .replace("{folders}", str(len(folders))).replace("{first}", f"`{folders[0]}`").replace("{last}", f"`{folders[-1]}`")
             .replace("{files_extra}", FILES_PAINT if a.paint else "").replace("{shots_line}", shots_line)
             .replace("{shield_rule}", SHIELD_RULE_PAINT if a.paint else SHIELD_RULE)
             .replace("{texture_rule}", (TEXTURE_RULE_PAINT if a.paint else TEXTURE_RULE).replace("{px}", str(a.px)))
             .replace("{rotations}", rotations).replace("{table}", "\n".join(table)))
    with open(os.path.join(root, "안내.md"), "w", encoding="utf-8") as fh:
        fh.write(guide)
    return write_zip(a.out, root)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--all", action="store_true", help="무기 디자인 전부 (종류별 폴더)")
    ap.add_argument("--game", help="(12단계) 게임 화면 그림 (손에 든 모습 모음)")
    ap.add_argument("--shots", help="(--all) CI 사진 폴더 — 디자인마다 게임 화면 1 · 3인칭")
    ap.add_argument("--skip", help="(--all) 뺄 종류 (쉼표로 — 예: lance,scythe,hammer,scepter)")
    ap.add_argument("--paint", action="store_true", help="(--all) 칠하기용 텍스처 (상자 면마다 따로 칸 — tools/weapon_paint.py)")
    ap.add_argument("--px", type=int, default=2, help="(--paint) 모델 1단위당 픽셀")
    ap.add_argument("--name", help="(--all) 폴더 · zip 이름 (기본 무기_디자인_모음)")
    a = ap.parse_args()
    pack_all(a) if a.all else pack_12(a)


if __name__ == "__main__":
    main()
