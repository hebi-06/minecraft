#!/usr/bin/env python3
"""2026-11-16 입은 갑옷 그림 (사용자 "바닐라 아이템은 다 빼버려 다 빼고 너가 직접 그려서 다른 걸로 넣어"): 모드 없는 클라이언트가 입은 갑옷을 바닐라 갑옷
(철 · 사슬 · 다이아 · 금 · 네더라이트) 그림으로 그리던 것을 직접 그린 장비 그림으로. 계열마다 한 벌 (사람 모델의 갑옷 UV — 64 × 32 두 장):

- str    힘 (전쟁의 여신 · 불)      진홍 칠 판금 · 금 테 · 가슴 불꽃 문장 · 투구 금 볏
- dex    민첩 (풍요의 여신 · 번개)  짙은 가죽 · 보랏빛 번개 줄 · 끈 · 두건
- vit    체력 (규율의 신 · 땅)      청동 빛 두꺼운 판 · 짙은 쇠 징 · 넓은 어깨
- strvit 힘 + 체력                 푸른 강철 판 · 진홍 천 · 은 테
- int    마력 (얼음 여왕)           얼음빛 · 흰 로브 갑옷 · 은 테 · 빛나는 룬

서버 (vanilla.VanillaItems) 가 장비 칸의 equippable 을 rpg:armor_<계열> (assets/rpg/equipment/armor_<계열>.json) 로. 무기 · 갑옷 디자인은 사용자가 따로 만듦
(Claude Design) — 그때까지 쓰는 그림.

  python3 tools/armor_layers.py               # 씀 (equipment/armor_*.json · textures/entity/equipment/humanoid[_leggings]/armor_*.png)
  python3 tools/armor_layers.py --check
  python3 tools/armor_layers.py --preview <파일.png>
"""
import io
import json
import os
import sys
import zlib

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
W, H = 64, 32


def sd(*k):
    """정해진 씨앗 (실행마다 같은 그림 — 파이썬 hash 는 실행마다 다름)"""
    return zlib.crc32("|".join(map(str, k)).encode()) & 0xFFFF


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# 계열 색: 판 (그늘 · 가운데 · 밝음 · 반짝) · 테 · 천 · 문장
SETS = {
    "str": dict(plate=["#5A0E16", "#9C1C26", "#D2343C", "#F27A6E"], trim=["#7A4A0C", "#C8901E", "#F2C84A", "#FFF0B0"],
                cloth=["#2A1418", "#40202A", "#5A3040"], emblem="flame"),
    "dex": dict(plate=["#20160F", "#3A2A1E", "#584232", "#7A604A"], trim=["#3A1E6E", "#6A3CC8", "#A27CFF", "#E2D4FF"],
                cloth=["#14181C", "#222A30", "#323C44"], emblem="bolt"),
    "vit": dict(plate=["#3A2810", "#6A4A1E", "#98703A", "#C8A066"], trim=["#26282C", "#44484E", "#6C7078", "#A4A8B0"],
                cloth=["#2A2418", "#3E3424", "#544834"], emblem="mountain"),
    "strvit": dict(plate=["#22304A", "#3E5478", "#6A84AC", "#AEC4E4"], trim=["#5A6070", "#9CA4B4", "#D4DAE6", "#FFFFFF"],
                   cloth=["#4A0E16", "#7A1A24", "#A42A34"], emblem="cross"),
    "int": dict(plate=["#6E88B4", "#A6C2E6", "#D8E8FA", "#FFFFFF"], trim=["#5A6478", "#9AA6BC", "#D2DAE8", "#FFFFFF"],
                cloth=["#1E3A6E", "#2E58A0", "#4A80D0"], emblem="rune"),
}


class Tex:
    def __init__(self):
        self.a = np.zeros((H, W, 4), np.uint8)

    def put(self, x, y, c, a=255):
        if 0 <= x < W and 0 <= y < H:
            self.a[y, x] = (*c, a)

    def rect(self, x0, y0, x1, y1, ramp, seed=0, grad=True, noise=True):
        """판 하나: 위가 밝은 단 · 텍셀 잡음 · 테두리 한 줄 그늘"""
        rng = np.random.default_rng(seed)
        for y in range(y0, y1):
            for x in range(x0, x1):
                t = (y - y0) / max(1, y1 - y0 - 1)
                k = 2 if (grad and t < 0.3) else 1
                if noise and rng.random() < 0.07:
                    k = max(0, k - 1)
                if y == y1 - 1 or x == x1 - 1:
                    k = 0
                if (y == y0 or x == x0) and k < 2:
                    k = 2
                self.put(x, y, hexc(ramp[k]))

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1):
            self.put(x, y, c)

    def clear(self, x0, y0, x1, y1):
        self.a[y0:y1, x0:x1] = 0

    def image(self):
        return Image.fromarray(self.a, "RGBA")


# ------------------------------------------------------------------ 상자 면 (사람 모델 UV)

def box_faces(ox, oy, w, h, d):
    """상자 (원점 ox, oy · 크기 w × h × d) 의 면 → {이름: (x0, y0, x1, y1)}"""
    return {
        "top": (ox + d, oy, ox + d + w, oy + d), "bottom": (ox + d + w, oy, ox + d + 2 * w, oy + d),
        "right": (ox, oy + d, ox + d, oy + d + h), "front": (ox + d, oy + d, ox + d + w, oy + d + h),
        "left": (ox + d + w, oy + d, ox + 2 * d + w, oy + d + h), "back": (ox + 2 * d + w, oy + d, ox + 2 * d + 2 * w, oy + d + h),
    }


HEAD = box_faces(0, 0, 8, 8, 8)
BODY = box_faces(16, 16, 8, 12, 4)
ARM = box_faces(40, 16, 4, 12, 4)
LEG = box_faces(0, 16, 4, 12, 4)


def emblem(T, kind, x0, y0, S):
    """가슴 문장 (앞 면 8 × 12 의 가운데)"""
    g = hexc(S["trim"][2])
    hi = hexc(S["trim"][3])
    dk = hexc(S["trim"][0])
    cx = x0 + 3
    if kind == "flame":
        for (dx, dy, c) in [(1, 0, hi), (1, 1, g), (0, 2, g), (1, 2, hi), (2, 2, g), (0, 3, g), (1, 3, hi), (2, 3, g), (1, 4, dk), (0, 4, g), (2, 4, g)]:
            T.put(cx + dx, y0 + 3 + dy, c)
    elif kind == "bolt":
        for (dx, dy) in [(2, 0), (1, 1), (0, 2), (1, 2), (2, 2), (1, 3), (0, 4)]:
            T.put(cx + dx, y0 + 3 + dy, hi if dy % 2 == 0 else g)
    elif kind == "mountain":
        for (dx, dy) in [(1, 0), (0, 1), (1, 1), (2, 1), (-1, 2), (0, 2), (1, 2), (2, 2), (3, 2)]:
            T.put(cx + dx, y0 + 4 + dy, g if dy < 2 else dk)
        T.put(cx + 1, y0 + 4, hi)
    elif kind == "cross":
        T.vline(cx + 1, y0 + 3, y0 + 8, g)
        T.hline(cx - 1, cx + 4, y0 + 4, g)
        T.put(cx + 1, y0 + 4, hi)
    elif kind == "rune":
        for (dx, dy) in [(1, 0), (0, 1), (2, 1), (1, 2), (1, 3), (0, 4), (2, 4)]:
            T.put(cx + dx, y0 + 3 + dy, hexc(S["cloth"][2]) if dy != 2 else hi)


def layer1(name, S):
    """투구 · 흉갑 · 장화 (humanoid)"""
    T = Tex()
    P, R, C = S["plate"], S["trim"], S["cloth"]
    # 투구
    for f, r in HEAD.items():
        T.rect(*r, P if name != "dex" else C, seed=sd(name, f))
    x0, y0, x1, y1 = HEAD["front"]
    if name == "dex":  # 두건: 얼굴 자리 비움 · 가죽 테
        T.clear(x0 + 1, y0 + 3, x1 - 1, y1)
        T.hline(x0, x1, y0 + 2, hexc(P[2]))
        T.put(x0 + 3, y0 + 1, hexc(R[2])), T.put(x0 + 4, y0 + 1, hexc(R[3]))
    elif name == "int":  # 머리띠 관: 얼굴 비움 · 은 띠 · 가운데 보석
        T.clear(x0 + 1, y0 + 3, x1 - 1, y1)
        T.hline(x0, x1, y0 + 2, hexc(R[2]))
        T.put(x0 + 3, y0 + 2, hexc(C[2])), T.put(x0 + 4, y0 + 2, hexc(C[2]))
        T.put(x0 + 3, y0 + 1, hexc(R[3])), T.put(x0 + 4, y0 + 1, hexc(R[3]))
    else:  # 닫힌 투구: 눈 틈 · 가운데 줄 · 볏
        T.hline(x0 + 1, x1 - 1, y0 + 4, hexc("#120A0E"))
        T.vline(x0 + 3, y0 + 5, y1 - 1, hexc(R[1])), T.vline(x0 + 4, y0 + 5, y1 - 1, hexc(R[2]))
        T.hline(x0, x1, y0, hexc(R[2]))
        tx0, ty0, tx1, ty1 = HEAD["top"]
        T.vline(tx0 + 3, ty0, ty1, hexc(R[2])), T.vline(tx0 + 4, ty0, ty1, hexc(R[3]))
        if name == "vit":
            for (xx, yy) in [(x0 + 1, y0 + 6), (x1 - 2, y0 + 6)]:
                T.put(xx, yy, hexc(R[3]))
    for f in ("right", "left", "back"):
        fx0, fy0, fx1, fy1 = HEAD[f]
        T.hline(fx0, fx1, fy0, hexc(R[2] if name not in ("dex",) else P[2]))
        if name not in ("dex", "int"):
            T.hline(fx0, fx1, fy1 - 1, hexc(R[1]))
    # 흉갑 (몸)
    for f, r in BODY.items():
        T.rect(*r, P, seed=sd(name, "b", f))
    x0, y0, x1, y1 = BODY["front"]
    T.hline(x0, x1, y0, hexc(R[2]))
    T.hline(x0, x1, y1 - 1, hexc(R[1]))
    T.vline(x0, y0, y1, hexc(R[1]))
    T.vline(x1 - 1, y0, y1, hexc(R[1]))
    if name == "dex":  # 엇갈린 끈 · 번개 줄
        for i in range(8):
            T.put(x0 + i, y0 + 2 + i, hexc(C[2]))
        T.hline(x0, x1, y0 + 8, hexc(C[1]))
    if name == "int":  # 로브 앞 여밈 (천 띠)
        T.vline(x0 + 3, y0 + 1, y1, hexc(C[1])), T.vline(x0 + 4, y0 + 1, y1, hexc(C[2]))
    emblem(T, S["emblem"], x0, y0, S)
    bx0, by0, bx1, by1 = BODY["back"]
    T.hline(bx0, bx1, by0, hexc(R[2]))
    T.vline(bx0 + 3, by0 + 2, by1 - 1, hexc(P[0])), T.vline(bx0 + 4, by0 + 2, by1 - 1, hexc(P[0]))
    # 팔 (어깨 판 · 팔뚝 띠)
    for f, r in ARM.items():
        T.rect(*r, P if name != "int" else C, seed=sd(name, "a", f))
    for f in ("right", "front", "left", "back"):
        ax0, ay0, ax1, ay1 = ARM[f]
        T.hline(ax0, ax1, ay0, hexc(R[2]))
        T.hline(ax0, ax1, ay0 + 3, hexc(R[1]))
        T.hline(ax0, ax1, ay1 - 2, hexc(R[2]))
        if name == "vit":
            T.hline(ax0, ax1, ay0 + 1, hexc(P[3]))
    tx0, ty0, tx1, ty1 = ARM["top"]
    for x in range(tx0, tx1):
        T.put(x, ty0, hexc(R[3]))
    # 장화 (다리 아래 5 줄 · 밑면)
    for f in ("right", "front", "left", "back"):
        lx0, ly0, lx1, ly1 = LEG[f]
        T.rect(lx0, ly1 - 5, lx1, ly1, P if name not in ("dex", "int") else C, seed=sd(name, "l", f))
        T.hline(lx0, lx1, ly1 - 5, hexc(R[2]))
    T.rect(*LEG["bottom"], P if name not in ("dex", "int") else C, grad=False)
    fx0, fy0, fx1, fy1 = LEG["front"]
    T.hline(fx0, fx1, fy1 - 2, hexc(R[1]))
    return T


def layer2(name, S):
    """각반 (humanoid_leggings): 몸 아래 허리띠 · 다리 (장화 위까지)"""
    T = Tex()
    P, R, C = S["plate"], S["trim"], S["cloth"]
    for f in ("right", "front", "left", "back"):
        x0, y0, x1, y1 = BODY[f]
        T.rect(x0, y1 - 4, x1, y1, C, seed=sd(name, "w", f))
        T.hline(x0, x1, y1 - 4, hexc(R[2]))
    x0, y0, x1, y1 = BODY["front"]
    T.put(x0 + 3, y1 - 3, hexc(R[3])), T.put(x0 + 4, y1 - 3, hexc(R[2]))
    T.rect(*BODY["bottom"], C, grad=False)
    for f, r in LEG.items():
        if f in ("top", "bottom"):
            continue
        lx0, ly0, lx1, ly1 = r
        T.rect(lx0, ly0, lx1, ly1 - 3, P if name not in ("dex", "int") else C, seed=sd(name, "g", f))
    fx0, fy0, fx1, fy1 = LEG["front"]
    # 무릎 판
    for x in range(fx0, fx1):
        T.put(x, fy0 + 5, hexc(R[2]))
        T.put(x, fy0 + 6, hexc(R[1]))
    if name == "dex":
        T.vline(fx0 + 1, fy0, fy1 - 3, hexc(R[2]))
    if name == "int":
        T.vline(fx0 + 2, fy0, fy1 - 3, hexc(R[2]))
    return T


def outputs():
    files = {}
    for name, S in SETS.items():
        for kind, fn in (("humanoid", layer1), ("humanoid_leggings", layer2)):
            buf = io.BytesIO()
            fn(name, S).image().save(buf, "PNG", optimize=True)
            files[os.path.join(RES, "textures", "entity", "equipment", kind, "armor_" + name + ".png")] = buf.getvalue()
        eq = {"layers": {"humanoid": [{"texture": "rpg:armor_" + name}], "humanoid_leggings": [{"texture": "rpg:armor_" + name}]}}
        files[os.path.join(RES, "equipment", "armor_" + name + ".json")] = (json.dumps(eq, indent=2) + "\n").encode()
    return files


def figure(l1, l2, back=False):
    """앞 (또는 뒤) 모습 미리 보기: 머리 · 몸 · 두 팔 · 두 다리의 앞 면을 사람 꼴로 (8 배)"""
    f = "back" if back else "front"
    im = Image.new("RGBA", (16, 32), (0, 0, 0, 0))

    def face(src, r, at, flip=False):
        c = src.crop(r)
        if flip:
            c = c.transpose(Image.FLIP_LEFT_RIGHT)
        im.alpha_composite(c, at)
    skin = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    base = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
    for (x, y, w, h, col) in [(4, 0, 8, 8, (200, 160, 130, 255)), (4, 8, 8, 12, (70, 90, 140, 255)), (0, 8, 4, 12, (200, 160, 130, 255)),
                              (12, 8, 4, 12, (200, 160, 130, 255)), (4, 20, 4, 12, (60, 60, 80, 255)), (8, 20, 4, 12, (60, 60, 80, 255))]:
        base.paste(col, (x, y, x + w, y + h))
    im.alpha_composite(base)
    face(l2, LEG[f], (4, 20)), face(l2, LEG[f], (8, 20), True)
    face(l2, BODY[f], (4, 8))
    face(l1, LEG[f], (4, 20)), face(l1, LEG[f], (8, 20), True)
    face(l1, BODY[f], (4, 8))
    face(l1, ARM[f], (0, 8)), face(l1, ARM[f], (12, 8), True)
    face(l1, HEAD[f], (4, 0))
    return im.resize((16 * 8, 32 * 8), Image.NEAREST)


def preview(path):
    figs = []
    for name, S in SETS.items():
        l1, l2 = layer1(name, S).image(), layer2(name, S).image()
        figs += [figure(l1, l2), figure(l1, l2, back=True)]
    out = Image.new("RGBA", (len(figs) * 136, 264), (88, 120, 60, 255))
    for i, f in enumerate(figs):
        out.alpha_composite(f, (i * 136 + 4, 4))
    out.save(path)


def main():
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
        return 0
    check = "--check" in sys.argv
    stale = []
    for path, data in outputs().items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            stale.append(os.path.relpath(path, ROOT))
            if not check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    if check:
        if stale:
            print("armor_layers: 다시 만들어야 함:", ", ".join(stale))
            return 1
        print("armor_layers: ok (%d)" % len(SETS))
        return 0
    print("armor_layers: %d 벌 · 씀 %d" % (len(SETS), len(stale)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
