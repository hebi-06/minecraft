"""0.48.2 초월 룬의 입체 효과 물체 (사용자 "룬도 좀 손봐야 하나? 직업이랑 무기 스킬 처럼" → "초월 31종 먼저"): tools/fx_models.py 가 add(fx) 로 부름.

모바일 (애드온) 룬 그림 (textures/particle/addon/rpg/fx/rfx_*.png) 을 입체로 옮긴 것 — 0.48.1 그람 날개 (fire_wings) 와 같은 방법: 그림을 칸으로 나눠
빈 칸은 버리고, 칸 모서리를 휜 판 위 자리로 옮겨 그림을 그대로 붙임 (빛깔 겹 TINT + 속빛 겹 GLOW — 앞뒤 어느 쪽에서 보아도 속빛이 위에 오게 세 장).
축 (fx_models.py 와 같음): +Y = 위 (길이), +Z = 넓은 면 (등 · 앞면), +X = 오른쪽.

  천사 날개 (angel_wings) — rfx_wings 첫 칸 (흰 깃털 날개 한 쌍) · 대천사 강림
"""
import math

import numpy as np

fx = None  # tools/fx_models.py (add 가 채움)

FX_DIR = "src/main/resources/assets/mmorpg/textures/particle/addon/rpg/fx/"


def add(mod):
    global fx
    fx = mod
    for f in (angel_wings,):
        fx.MODELS[f.__name__] = f


def v3(*a):
    return np.array(a if len(a) == 3 else a[0], dtype=float)


# ------------------------------------------------------------ 그림 읽기


class Art:
    """모바일 그림 한 칸 (box = x0, y0, x1, y1 픽셀) — RGBA 0 ~ 1, 알파를 곱한 색으로도 둠 (부드럽게 읽을 때 가장자리가 검게 물들지 않게)"""

    _cache = {}

    def __init__(self, png, box):
        key = (png, box)
        if key not in Art._cache:
            from PIL import Image
            im = Image.open(fx.os.path.join(fx.ROOT, FX_DIR + png)).convert("RGBA")
            arr = np.asarray(im.crop(box), dtype=float) / 255.0
            pre = arr.copy()
            pre[:, :, :3] *= arr[:, :, 3:4]
            Art._cache[key] = (arr, pre)
        self.arr, self.pre = Art._cache[key]
        self.h, self.w = self.arr.shape[:2]

    def sample(self, u, v):
        """자리 (u · v 0 ~ 1) 의 색 (R, G, B, A)"""
        x, y = u * (self.w - 1), v * (self.h - 1)
        x0, y0 = int(math.floor(x)), int(math.floor(y))
        x1, y1 = min(self.w - 1, x0 + 1), min(self.h - 1, y0 + 1)
        fx_, fy = x - x0, y - y0
        p = self.pre
        c = p[y0, x0] * (1 - fx_) * (1 - fy) + p[y0, x1] * fx_ * (1 - fy) + p[y1, x0] * (1 - fx_) * fy + p[y1, x1] * fx_ * fy
        a = c[3]
        if a < 1e-4:
            return 0.0, 0.0, 0.0, 0.0
        return c[0] / a, c[1] / a, c[2] / a, a

    def empty(self, px0, py0, px1, py1, cut=0.04):
        reg = self.arr[int(py0):int(math.ceil(py1)) + 1, int(px0):int(math.ceil(px1)) + 1, 3]
        return reg.size == 0 or reg.max() < cut


def sheet(model, art, name, layer_fn, n=8):
    """그림을 아틀라스 칸 하나에 (n × n 셀) — layer_fn(r, g, b, a) → (밝기, 알파)"""

    def p(u, v, rnd):
        r, g, b, a = art.sample(u, v)
        if a <= 0:
            return 0.0, 0.0
        return layer_fn(r, g, b, a)

    return model.atlas.swatch(name, p, n, n)


def card(model, art, uv, mat, pt, cols, rows, dz=0.0, flip=False, normal=(0, 0, 1)):
    """그림을 cols × rows 칸으로 나눠 붙임 — pt(px, py) → 모델 자리 (휜 판). 빈 칸은 버림. flip = 감기는 쪽을 뒤집음 (거울에 비친 반쪽)"""
    w, h = art.w - 1, art.h - 1
    u0, v0, u1, v1 = uv
    for j in range(rows):
        for i in range(cols):
            px0, px1 = w * i / cols, w * (i + 1) / cols
            py0, py1 = h * j / rows, h * (j + 1) / rows
            if art.empty(px0, py0, px1, py1):
                continue
            corners = [(px0, py0), (px0, py1), (px1, py1), (px1, py0)]
            if flip:
                corners = corners[::-1]
            ps = [pt(px, py) + v3(0, 0, dz) for px, py in corners]
            uvs = [(u0 + (u1 - u0) * px / w, v0 + (v1 - v0) * py / h) for px, py in corners]
            model.quad(ps, uvs, mat, [v3(*normal)] * 4)


# ------------------------------------------------------------ 천사 날개


def angel_wings():
    """천사 날개 — 모바일 대천사 강림의 날개 그림 (rfx_wings 첫 칸: 부채처럼 펼친 흰 깃털 한 쌍, 깃대 줄 · 둘레의 부드러운 빛) 을 입체로.
    등 가운데가 원점, 위 +Y, 뒤 +Z, 날개 ±X (한 날개 약 2.3 — 대천사 강림의 2D 날개 크기). 날개마다 그 그림의 오른쪽 반을 어깨에 매단 휜 판 (바깥으로 갈수록
    뒤로 젖혀짐 · 위아래 끝이 조금 뒤로) 에 붙이고 왼쪽은 거울. 색은 두 겹 (빛깔 = 깃털 몸, 속빛 = 둘레의 빛 · 밝은 깃털 — 앞뒤 세 장).
    wing_r · wing_l (어깨 축) 움직임 spread · flap · fold — fire_wings 와 같은 이름"""
    m = fx.Model("angel_wings", tex=512, cell=32)
    m.emissive = True
    art = Art("rfx_wings.png", (128, 0, 252, 150))  # 첫 칸의 오른쪽 날개 (어깨 = 그림 가운데 x 128)
    root = (6.0, 83.0)  # 그 그림 안의 어깨 (픽셀)
    k = 0.0205
    # 빛깔: 깃털 몸 (알파 0.5 위 — 둘레 빛은 옅게), 밝기 = 그림 밝기 (깃대 줄이 어둡게 남음)
    body = sheet(m, art, "wing_body", lambda r, g, b, a: (min(1.0, (r + g + b) / 3 * 1.08), min(1.0, max(0.0, (a - 0.25) / 0.6))))
    # 속빛: 둘레의 빛 (알파가 낮은 가장자리) + 깃털 밝은 곳 조금
    glow = sheet(m, art, "wing_glow", lambda r, g, b, a: (1.0, min(1.0, a * (0.55 if a < 0.6 else 0.3))))
    span = (art.w - root[0]) * k
    cols, rows = 10, 8

    def pt(side):
        def f(px, py):
            x = 0.06 + (px - root[0]) * k
            y = 0.06 + (root[1] - py) * k
            t = min(1.0, max(0.0, x / (0.06 + span)))
            yn = y / 1.3
            z = 0.04 + 0.26 * t * t + 0.06 * yn * yn
            return v3(side * x, y, z)
        return f

    for side, part in ((1, "wing_r"), (-1, "wing_l")):
        m.part(part, tuple(pt(side)(*root)))
        for layer, uv, mat, dz in (("glow_f", glow, fx.GLOW, -0.012), ("body", body, fx.TINT, 0.0), ("glow_b", glow, fx.GLOW, 0.012)):
            card(m, art, uv, mat, pt(side), cols, rows, dz=dz, flip=side < 0)
    m.part("main")
    m.anims["spread"] = {"len": 0.45, "tracks": {"wing_r": {"rot": [[0, 0, -70, 55], [0.75, 0, 4, -6], [1, 0, 0, 0]], "scale": [[0, 0.5], [1, 1]]},
                                                  "wing_l": {"rot": [[0, 0, 70, -55], [0.75, 0, -4, 6], [1, 0, 0, 0]], "scale": [[0, 0.5], [1, 1]]}}}
    m.anims["flap"] = {"len": 1.2, "tracks": {"wing_r": {"rot": [[0, 0, 3, 10], [0.45, 0, -8, -14], [1, 0, 3, 10]]},
                                               "wing_l": {"rot": [[0, 0, -3, -10], [0.45, 0, 8, 14], [1, 0, -3, -10]]}}}
    m.anims["fold"] = {"len": 0.4, "tracks": {"wing_r": {"rot": [[0, 0, 0, 0], [1, 0, -70, 55]], "scale": [[0, 1], [1, 0.5]]},
                                               "wing_l": {"rot": [[0, 0, 0, 0], [1, 0, 70, -55]], "scale": [[0, 1], [1, 0.5]]}}}
    m.add_glow(0.7, 0.4, 0.12, 0.5).add_glow(-0.7, 0.4, 0.12, 0.5).add_glow(0, 0.15, 0.06, 0.4)
    return m
