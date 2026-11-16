"""2026-11-16 머리 위 표시 (위르드식 — 피해 숫자 rpg:dmg 와 같은 도트): 원소 표시 (예전 rfx_jelem — 불 · 물 · 전기 · 얼음 · 바위 다섯 칸) · 기절 별 (예전 rfx_jstun2 —
머리 위를 도는 별 다섯, 8칸 되풀이).

칠한 그림을 도트로 옮기는 다른 묶음과 달리 처음부터 도트로 그림 (원소 표시 16 × 16 · 기절 별 40 × 20 — 칸 하나가 도트 하나, 글꼴은 가장 가까운 칸으로 그려 늘려도
또렷함). 색: 어두운 테두리 (빛 × 0.28) · 몸 (빛) · 그늘 (빛 × 0.72 — 오른쪽 아래) · 속 (심지) · 흰 빛점. 원소 빛은 rules_more ELEMENTS.list 의 color 와 같음.

  cd tools && python3 -m jobvfx.icons --preview 폴더
"""
import math
import sys

import numpy as np
from PIL import Image

# rules_more.json ELEMENTS.list (cell 차례 — Elements.Element.cell)
ELEMENTS = [
    ("fire", (1, 0.42, 0.1), (1, 0.9, 0.55)),
    ("water", (0.18, 0.5, 1), (0.7, 0.92, 1)),
    ("thunder", (0.72, 0.38, 1), (0.97, 0.88, 1)),
    ("ice", (0.5, 0.9, 1), (0.94, 0.99, 1)),
    ("rock", (0.8, 0.56, 0.2), (1, 0.86, 0.5)),
]
N = 16


def _grid(n=N):
    y, x = np.mgrid[0:n, 0:n]
    return x + 0.5, y + 0.5


def _poly(x, y, pts):
    """점 (x, y) 가 다각형 안인지 (짝홀 규칙)"""
    inside = np.zeros_like(x, dtype=bool)
    k = len(pts)
    for i in range(k):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % k]
        cond = (y1 > y) != (y2 > y)
        xi = x1 + (y - y1) * (x2 - x1) / (y2 - y1 + 1e-9)
        inside ^= cond & (x < xi)
    return inside


def _drop(x, y, cx, top, bottom, r):
    """물방울 · 불꽃 몸 (위가 뾰족): 밑 원 (가운데 bottom − r) + 꼭지 top 까지 이은 두 접선"""
    cy = bottom - r
    circ = (x - cx) ** 2 + (y - cy) ** 2 <= r * r
    tri = _poly(x, y, [(cx, top), (cx - r * 0.98, cy), (cx + r * 0.98, cy)])
    return circ | tri


def _dilate(m):
    o = m.copy()
    o[1:, :] |= m[:-1, :]
    o[:-1, :] |= m[1:, :]
    o[:, 1:] |= m[:, :-1]
    o[:, :-1] |= m[:, 1:]
    return o


def _erode(m):
    return ~_dilate(~m)


def _paint(body, core, light, glow, corec, shade=None):
    """칸 마스크 → RGBA (테두리 · 몸 · 그늘 · 속 · 빛점)"""
    g = np.array(glow, float)
    c = np.array(corec, float)
    img = np.zeros(body.shape + (4,), np.uint8)
    edge = _dilate(body) & ~body

    def put(m, col):
        col = np.clip(np.array(col, float), 0, 1)
        img[m] = (np.append(col * 255, 255)).astype(np.uint8)
    put(edge, g * 0.28)
    put(body, g)
    if shade is not None:
        put(body & shade, g * 0.72)
    put(core & body, c)
    put(light & body, c * 0.4 + 0.6)
    return Image.fromarray(img, "RGBA")


def fire(glow, core):
    x, y = _grid()
    body = _drop(x, y, 7.6, 1.2, 14.6, 4.9)
    tongue = _poly(x, y, [(12.6, 3.0), (10.0, 8.8), (13.4, 9.6)])  # 오른쪽 위 작은 불길
    body = body | tongue
    core = _drop(x, y, 7.6, 6.2, 13.4, 2.9)
    light = _drop(x, y, 7.6, 9.6, 12.6, 1.3)
    shade = (x - 7.6) * 0.7 + (y - 9) * 0.7 > 3.0
    return _paint(body, core, light, glow, core_col(core, glow, 1), shade)


def core_col(_m, glow, k):
    return glow


def _icon(name, glow, core):
    x, y = _grid()
    if name == "fire":
        body = _drop(x, y, 7.6, 1.2, 14.6, 4.9) | _poly(x, y, [(12.8, 2.6), (10.0, 8.8), (13.6, 9.8)])
        cm = _drop(x, y, 7.6, 6.0, 13.6, 3.0)
        lm = _drop(x, y, 7.6, 9.4, 12.8, 1.4)
        shade = (x - 7.6) * 0.7 + (y - 9) * 0.7 > 3.2
    elif name == "water":
        body = _drop(x, y, 8, 1.0, 15.0, 5.2)
        cm = ((x - 6.2) ** 2 + (y - 10.2) ** 2 <= 4.4) & ~((x - 7.4) ** 2 + (y - 11.2) ** 2 <= 4.0)  # 왼쪽 위 초승달 빛
        lm = ((x - 5.8) ** 2 + (y - 8.6) ** 2 <= 0.8)
        shade = (x - 8) * 0.6 + (y - 10) * 0.8 > 2.6
    elif name == "thunder":
        pts = [(10.5, 0.8), (3.2, 9.0), (7.6, 9.0), (4.6, 15.2), (12.8, 6.2), (8.4, 6.2), (11.6, 0.8)]
        body = _poly(x, y, pts)
        cm = _erode(body)
        lm = _poly(x, y, [(10.2, 1.8), (5.6, 8.2), (7.0, 8.2)]) & cm
        shade = None
    elif name == "ice":
        body = np.zeros_like(x, dtype=bool)
        cx, cy = 8.0, 8.0
        for k in range(3):  # 세 줄 (60° 씩) · 끝 가지
            a = math.pi / 2 + k * math.pi / 3
            ux, uy = math.cos(a), math.sin(a)
            along = (x - cx) * ux + (y - cy) * uy
            across = -(x - cx) * uy + (y - cy) * ux
            body |= (np.abs(along) <= 7.2) & (np.abs(across) <= 0.9)
            for s in (-1, 1):
                for side in (-1, 1):  # 가지: 끝에서 2.6칸 안쪽에서 바깥 · 옆으로
                    bx, by = cx + ux * s * 4.6, cy + uy * s * 4.6
                    va = a + side * math.pi / 3 * s
                    vx, vy = math.cos(va), math.sin(va)
                    t = (x - bx) * vx + (y - by) * vy
                    d = np.abs(-(x - bx) * vy + (y - by) * vx)
                    body |= (t >= 0) & (t <= 2.4) & (d <= 0.6)
        body |= (np.abs(x - cx) + np.abs(y - cy)) <= 2.2  # 가운데 마름모
        cm = (np.abs(x - cx) + np.abs(y - cy)) <= 1.6
        lm = (np.abs(x - cx) + np.abs(y - cy)) <= 0.8
        shade = None
    else:  # rock
        pts = [(2.4, 8.0), (5.0, 3.0), (10.6, 2.4), (14.0, 6.6), (13.4, 12.2), (8.6, 14.4), (3.6, 12.6)]
        body = _poly(x, y, pts)
        cm = _poly(x, y, [(4.2, 7.6), (6.0, 4.2), (10.2, 3.8), (9.0, 7.4), (5.4, 9.4)])  # 왼쪽 위 밝은 면
        lm = _poly(x, y, [(6.2, 4.8), (8.8, 4.4), (7.6, 6.2)])
        shade = (x - 8) * 0.55 + (y - 8) * 0.85 > 2.4
        crack = (np.abs((x - 9.0) - (y - 8.0) * 0.45) <= 0.5) & (y > 8.0) & (y < 13.0)
        img = _paint(body, cm, lm, glow, core, shade)
        a = np.array(img)
        g = np.array(glow, float)
        a[crack & body] = np.append(np.clip(g * 0.35, 0, 1) * 255, 255).astype(np.uint8)
        return Image.fromarray(a, "RGBA")
    return _paint(body, cm, lm, glow, core, shade)


def element_frames():
    """원소 표시 다섯 칸 (cell 차례)"""
    return [_icon(n, g, c) for n, g, c in ELEMENTS]


# ------------------------------------------------------------------ 기절 별 (예전 rfx_jstun2 — 128 × 64 · 8칸 되풀이)

STUN_FRAMES = 8
SW, SH = 40, 20
GOLD = (1.0, 0.86, 0.3)
GOLD_CORE = (1.0, 1.0, 0.82)


def _star(img, cx, cy, big):
    """네 갈래 별 (큰 별 5 × 5 · 작은 별 3 × 3) + 어두운 테두리"""
    pts = [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)] + ([(2, 0), (-2, 0), (0, 2), (0, -2)] if big else [])
    cells = {(int(round(cx)) + dx, int(round(cy)) + dy) for dx, dy in pts}
    edge = set()
    for (px, py) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            q = (px + dx, py + dy)
            if q not in cells:
                edge.add(q)
    out = np.clip(np.array(GOLD) * 0.3, 0, 1)
    for (px, py) in edge:
        if 0 <= px < SW and 0 <= py < SH and img[py, px, 3] == 0:
            img[py, px] = np.append(out * 255, 255)
    for (px, py) in cells:
        if 0 <= px < SW and 0 <= py < SH:
            col = GOLD_CORE if (px, py) == (int(round(cx)), int(round(cy))) or not big else GOLD
            img[py, px] = np.append(np.array(col) * 255, 255)


def stun_frames():
    out = []
    for f in range(STUN_FRAMES):
        img = np.zeros((SH, SW, 4), np.uint8)
        stars = []
        for i in range(5):
            a = 2 * math.pi * i / 5 + f / STUN_FRAMES * (2 * math.pi / 5)  # 8칸에 1/5 바퀴 — 되풀이가 이어짐
            stars.append((math.sin(a), 20 + math.cos(a) * 15.5, 10.5 + math.sin(a) * 4.5))
        for depth, sx, sy in sorted(stars):  # 뒤 (위) 부터
            _star(img, sx, sy, depth > -0.2)
        out.append(Image.fromarray(img, "RGBA"))
    return out


def sprites():
    return [("el_icon_elem", element_frames, True), ("el_icon_stun", stun_frames, True)]


if __name__ == "__main__":
    if "--preview" in sys.argv:
        import os
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        el = element_frames()
        sheet = Image.new("RGBA", (N * 5 * 8, N * 8), (40, 120, 40, 255))
        for i, im in enumerate(el):
            sheet.alpha_composite(im.resize((N * 8, N * 8), Image.NEAREST), (i * N * 8, 0))
        sheet.save(os.path.join(out, "elem.png"))
        st = stun_frames()
        s2 = Image.new("RGBA", (SW * 6 * 4, SH * 6 * 2), (40, 120, 40, 255))
        for i, im in enumerate(st):
            s2.alpha_composite(im.resize((SW * 6, SH * 6), Image.NEAREST), ((i % 4) * SW * 6, (i // 4) * SH * 6))
        s2.save(os.path.join(out, "stun.png"))
        print(out)
