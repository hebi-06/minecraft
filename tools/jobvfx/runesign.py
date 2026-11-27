"""2026-11-27 룬 시전 서명 (사용자 "룬 쓸때 룬 글자 띄울거임"): 룬을 쓸 때 그 룬의 글자가 번쩍 — 남에게는 머리 위, 쓴 사람의 1인칭에는 화면 가운데 아래
(fabric rune.RuneSign). 위르드식 도트 (꽉 찬 텍셀 · 반투명 없음 — 처음부터 도트로 그림, 피해 숫자 rpg:dmg 와 같은 밀도).

글자 · 색 = 룬 아이템 그림 (tools/rune_set_icons.py 의 G · RUNES — 엘더 푸타르크 · 묶음 글자, 9 × 15 칸의 선 · 두 칸 굵기) 과 같음 — 가방에서 보던 글자가 쓸 때
그대로 뜸. 한정 룬 다섯은 아이템 그림이 글자가 아니라 여기서 정함 (무간참역 나란한 빗금 셋 · 대지 가르기 세운 칼 · 세계수의 가호 뿌리 내린 나무 · 심연의 낙인 꿰뚫린 눈 ·
얼음 여왕의 분노 얼음 왕관).

칸 차례 = rules 의 RUNE_FAMILY_ORDER (룬 30 — rules_runes.json) 다음 RUNE_LIMITED_ORDER (한정 5 — rules_more.json), 룬마다 FRAMES 칸
(0 번쩍 — 흰 글자 · 밝은 고리 · 빛점 / 1 · 2 빛나는 글자 + 끊긴 고리 (2 는 고리를 반 칸 돌림 — 번갈아 돌아 보임) / 3 · 4 흩어짐 — 텍셀이 빠지며 위로).
RuneSign.java 가 같은 차례로 칸을 고름.

  cd tools && python3 -m jobvfx.runesign --preview 폴더
"""
import json
import math
import os
import sys

from PIL import Image

_TOOLS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _TOOLS not in sys.path:
    sys.path.insert(0, _TOOLS)
import rune_set_icons as icons  # noqa: E402

ROOT = os.path.dirname(_TOOLS)
RULES = os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg")
FRAMES = 5
W = 32  # 칸 (정사각 — 고리 지름 26 · 글자 10 × 15)
GW, GH = 10, 15  # 글자 크기 (9 × 15 칸의 선 + 오른쪽 굵기 한 칸)
OX, OY = (W - GW) // 2, (W - GH) // 2 + 1
CX = CY = W / 2.0
RING = 12.5  # 고리 반지름 (픽셀)

# 한정 룬 다섯 — 아이템 그림 (그림 문양) 대신 쓸 글자 (같은 9 × 15 칸의 선) · 색 (그 룬의 빛)
LIMITED = {
    # 무간참역: 끝없이 몰아치는 참격 — 나란한 빗금 셋 (버서커의 X 와 겹치지 않게)
    "endless": ([[(0, 6), (3, 0)], [(0, 13), (7, 0)], [(4, 14), (8, 6)]], (255, 110, 104)),
    # 대지 가르기: 땅에 박힌 대검 — 세운 칼날 · 코등이 · 갈라진 땅
    "terra": ([[(4, 0), (4, 11)], [(1, 4), (7, 4)], [(0, 14), (2, 11), (4, 14), (6, 11), (8, 14)]], (236, 186, 104)),
    # 세계수의 가호: 뿌리 내린 나무 (ᛦ 와 ᛉ 를 이음)
    "yggdrasil": ([[(4, 0), (4, 14)], [(4, 5), (0, 1)], [(4, 5), (8, 1)], [(4, 10), (0, 14)], [(4, 10), (8, 14)]], (150, 236, 120)),
    # 심연의 낙인: 꿰뚫린 눈 (마름모 + 세로 사슬)
    "abyss": ([[(4, 0), (4, 14)], [(4, 3), (8, 7), (4, 11), (0, 7), (4, 3)]], (232, 96, 236)),
    # 얼음 여왕의 분노: 얼음 왕관 (솟은 고드름 셋)
    "glacier": ([[(0, 14), (0, 4), (2, 9), (4, 0), (6, 9), (8, 4), (8, 14)], [(0, 14), (8, 14)]], (172, 230, 255)),
}


def _load(name):
    with open(os.path.join(RULES, name), encoding="utf-8") as f:
        return json.load(f)


def order():
    """룬 차례 (RuneSign.cellOf 와 같음): rules 를 합친 것과 같게 — rules_runes 가 rules_more 위"""
    more = _load("rules_more.json")
    runes = _load("rules_runes.json")
    fam = runes.get("RUNE_FAMILY_ORDER") or more.get("RUNE_FAMILY_ORDER") or []
    lim = runes.get("RUNE_LIMITED_ORDER") or more.get("RUNE_LIMITED_ORDER") or []
    return list(fam) + list(lim)


def glyph(family):
    """룬 → (글자 픽셀 집합, 색) — 없으면 None"""
    if family in icons.RUNES:
        _, g, col = icons.RUNES[family]
        return icons.glyph_pixels(g), col
    if family in LIMITED:
        lines, col = LIMITED[family]
        pts = set()
        for poly in lines:
            for a, b in zip(poly, poly[1:]):
                for x, y in icons.line(a, b):
                    pts.add((x, y))
                    pts.add((x + 1, y))
        return pts, col
    return None


def _mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _dark(c, k=0.3):
    return tuple(int(v * k) for v in c)


def _hash(x, y, salt):
    """칸마다 같은 0 ~ 1 (흩어짐 차례 — 다시 만들어도 같은 그림)"""
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return (h & 0xFFFF) / 65536.0


def _ring(offset_deg, thick, dash, gap):
    """고리 픽셀 (끊긴 고리 — dash 도 이어지고 gap 도 빔, offset 만큼 돌림)"""
    out = set()
    period = dash + gap
    for y in range(W):
        for x in range(W):
            d = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
            if abs(d - RING) > thick / 2:
                continue
            if gap > 0:
                a = (math.degrees(math.atan2(y + 0.5 - CY, x + 0.5 - CX)) - offset_deg) % period
                if a >= dash:
                    continue
            out.add((x, y))
    return out


def _notches():
    """고리 바깥 네 쪽 짧은 눈금 (룬 문양 느낌)"""
    out = set()
    c = int(CX)
    for k in range(2):
        out |= {(c - 1 + k, 1), (c - 1 + k, 2), (c - 1 + k, W - 2), (c - 1 + k, W - 3), (1, c - 1 + k), (2, c - 1 + k), (W - 2, c - 1 + k), (W - 3, c - 1 + k)}
    return out


def _sparks():
    """번쩍 칸의 빛점 넷 (대각선 — 작은 마름모)"""
    out = set()
    for sx, sy in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        x = int(round(CX + sx * 11.5))
        y = int(round(CY + sy * 11.5))
        out |= {(x, y), (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)}
    return out


def _outline(pts):
    out = set()
    for (x, y) in pts:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in pts:
                    out.add(q)
    return out


def _put(px, pts, col):
    for (x, y) in pts:
        if 0 <= x < W and 0 <= y < W:
            px[x, y] = col + (255,)


def frames_of(family, salt):
    """룬 하나의 FRAMES 칸"""
    g = glyph(family)
    if g is None:
        return [Image.new("RGBA", (W, W), (0, 0, 0, 0)) for _ in range(FRAMES)]
    pts0, col = g
    body = {(OX + x, OY + y) for (x, y) in pts0}
    edge = {(x, y) for (x, y) in body if (x - 1, y) not in body or (x, y - 1) not in body}
    line = _outline(body)
    light = _mix(col, (255, 255, 255), 0.5)
    dark = _dark(col, 0.3)
    ring_col = _mix(col, (255, 255, 255), 0.15)
    out = []

    # 0 번쩍: 흰 글자 · 빛 테 · 두꺼운 밝은 고리 · 빛점
    im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    px = im.load()
    _put(px, _ring(0, 2.0, 360, 0), _mix(col, (255, 255, 255), 0.6))
    _put(px, _sparks(), (255, 255, 255))
    _put(px, line, light)
    _put(px, body, (255, 255, 255))
    out.append(im)

    # 1 · 2 빛나는 글자 (어두운 테 · 몸 · 왼쪽 위 밝은 가장자리) + 끊긴 고리 · 눈금 (2 는 반 칸 돌림)
    for off in (0.0, 15.0):
        im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        px = im.load()
        _put(px, _ring(off, 1.2, 22, 8), ring_col)
        _put(px, _notches(), _dark(col, 0.55))
        _put(px, line, dark)
        _put(px, body, col)
        _put(px, edge, light)
        out.append(im)

    # 3 · 4 흩어짐: 텍셀이 빠지고 남은 빛 알갱이가 위로 (3 = 몸 55% · 고리 35%, 4 = 몸 20% · 고리 10%)
    ring = _ring(7.5, 1.2, 22, 8)
    for keep, ring_keep, lift in ((0.55, 0.35, 2), (0.2, 0.1, 5)):
        im = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        px = im.load()
        _put(px, {p for p in ring if _hash(p[0], p[1], salt + 7) < ring_keep}, ring_col)
        kept = {p for p in body if _hash(p[0], p[1], salt) < keep}
        _put(px, {p for p in _outline(kept) if p not in body or p not in kept}, dark)
        _put(px, kept, col)
        _put(px, {p for p in kept if p in edge}, light)
        # 빠진 텍셀 일부가 빛 알갱이로 떠오름
        for (x, y) in body:
            if (x, y) in kept:
                continue
            h = _hash(x, y, salt + 3)
            if h < 0.22:
                ny = y - lift - int(h * 10)
                if 0 <= ny < W:
                    px[x, ny] = light + (255,)
        out.append(im)
    return out


def all_frames():
    out = []
    for i, fam in enumerate(order()):
        out += frames_of(fam, i + 1)
    return out


def sprites():
    """tools/vfx_sprites.py 목록 — 미리 칠한 그림 (colored)"""
    return [("rn_sign", all_frames, True)]


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        out_dir = sys.argv[2]
        os.makedirs(out_dir, exist_ok=True)
        fams = order()
        S = 6
        sheet = Image.new("RGBA", (FRAMES * (W * S + 6), len(fams) * (W * S + 6)), (52, 58, 70, 255))
        for i, fam in enumerate(fams):
            for k, im in enumerate(frames_of(fam, i + 1)):
                sheet.alpha_composite(im.resize((W * S, W * S), Image.NEAREST), (k * (W * S + 6), i * (W * S + 6)))
        p = os.path.join(out_dir, "rune_sign.png")
        sheet.save(p)
        missing = [f for f in fams if glyph(f) is None]
        print(p, len(fams), "runes", "missing", missing)
