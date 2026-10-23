#!/usr/bin/env python3
"""몬스터 모델 도구 (9단계 — 새 몬스터 7종을 코드로 그림): 베드락 모델 (뼈 · 상자 UV) · 텍스처 (픽셀 그림) · 애니메이션.

tools/monster_art.py 가 몬스터마다 이 도구로 모델을 짜서 tools/monster_art/<키>/ 에 씀
(<키>.geo.json · <키>.png · <키>.animation.json — 애드온 art/monsters/<키>/ 와 같은 모양이라 클로드 디자인 파일로 바꿔 끼울 수 있음).

- Model.bone(이름, 회전 중심, 부모, 회전) → Bone.box(원점, 크기, 재질, faces={면: 재질}, decals=[(면, x, y, 색, 빛)]) — 크기는 정수 칸
- 상자 UV: 상자마다 2·(w+d) × (d+h) 칸을 텍스처에 차례로 깔고 (선반 쌓기), 텍스처 크기는 128 × (64 · 128 · 256)
- 면 (베드락 상자 UV): up (u+d, v) · down (u+d+w, v) · east (-x = 몬스터 오른쪽, u, v+d) · north (앞 -z, u+d, v+d) ·
  west (+x = 왼쪽, u+d+w, v+d) · south (뒤, u+2d+w, v+d). 앞면의 왼쪽 열 = 몬스터 오른쪽 (-x), 윗면의 첫 줄 = 뒤 (+z)
- 재질 Mat: 바탕색 · 무늬 (flat · fur · bristle · chitin · plates · scales · skin · ice · knit · wood · bone · metal) · 얼룩 · 테두리 · 빛 (glow — 베드락
  entity_emissive: 알파가 낮을수록 빛남, tools/more_monsters.py 가 <키>_emissive.png 로 나눔)
- 미리보기: preview(모델들, 그림) — 애드온의 generator/tools/monster_preview.py 그리기를 씀 (RPG_ADDON, 기본 ../rpg_addon)
"""
import json
import math
import os
import random
import sys

from PIL import Image

FACES = ("up", "down", "east", "north", "west", "south")


def clamp(v):
    return max(0, min(255, int(round(v))))


def rgb(h):
    if isinstance(h, (tuple, list)):
        return tuple(int(x) for x in h[:3])
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mul(c, k):
    return tuple(clamp(x * k) for x in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Mat:
    """재질: base 바탕색, pattern 무늬, noise 얼룩 세기, edge 테두리 어둡기 (1 = 없음), glow 빛 (0 ~ 1 — 알파 255·(1-glow·0.75)),
    accent 무늬 색 (없으면 바탕의 어둡고 밝은 색), band 줄 간격, seed"""

    # 0.32.0 공허 몬스터: 바탕 위에 빛나는 무늬 (없으면 그리지 않음 — 예전 몬스터 그림은 그대로)
    #   seam 이음새 줄 (색 · seam_band 간격 · seam_dir "h" 가로 / "v" 세로 / "grid" · seam_gap 끊김 확률) · veins 핏줄 (수 · vein 색) ·
    #   spots 반점 (수 · spot 색) · rim 테두리 (색) — 각각 _glow (0 ~ 1) 만큼 빛남 (알파 255·(1-glow·0.75) — 빛나는 칸)
    EXTRA = {"seam": None, "seam_glow": 0.85, "seam_band": 4, "seam_dir": "h", "seam_gap": 0.15, "veins": 0, "vein": None, "vein_glow": 0.8,
             "spots": 0, "spot": None, "spot_glow": 0.9, "rim": None, "rim_glow": 0.7}

    def __init__(self, base, pattern="flat", noise=7, edge=0.84, glow=0.0, accent=None, band=3, seed=0, light=1.0, top=1.1, bottom=0.7, **extra):
        self.base = rgb(base)
        self.pattern = pattern
        self.noise = noise
        self.edge = edge
        self.glow = glow
        self.accent = rgb(accent) if accent else None
        self.band = band
        self.seed = seed
        self.light = light
        self.top = top
        self.bottom = bottom
        for k, v in Mat.EXTRA.items():
            setattr(self, k, v)
        for k, v in extra.items():
            if k not in Mat.EXTRA:
                raise TypeError(f"Mat: 모르는 값 {k}")
            setattr(self, k, rgb(v) if k in ("seam", "vein", "spot", "rim") and v is not None else v)

    def with_(self, **kw):
        m = Mat(self.base, self.pattern, self.noise, self.edge, self.glow, self.accent, self.band, self.seed, self.light, self.top, self.bottom)
        for k in Mat.EXTRA:
            setattr(m, k, getattr(self, k))
        for k, v in kw.items():
            setattr(m, k, rgb(v) if k in ("base", "accent", "seam", "vein", "spot", "rim") and v is not None else v)
        return m


class Box:
    def __init__(self, origin, size, mat, faces=None, decals=None, inflate=0.0):
        self.origin = [float(v) for v in origin]
        self.size = [int(v) for v in size]
        assert all(s >= 0 for s in self.size), size
        self.mat = mat
        self.faces = faces or {}
        self.decals = decals or []
        self.inflate = inflate
        self.uv = None

    def footprint(self):
        w, h, d = self.size
        return 2 * (w + d), d + h


class Bone:
    def __init__(self, name, pivot, parent=None, rotation=None):
        self.name = name
        self.pivot = [float(v) for v in pivot]
        self.parent = parent
        self.rotation = rotation
        self.boxes = []

    def box(self, origin, size, mat, faces=None, decals=None, inflate=0.0):
        b = Box(origin, size, mat, faces, decals, inflate)
        self.boxes.append(b)
        return b


class Model:
    def __init__(self, key, tex_w=128):
        self.key = key
        self.tex_w = tex_w
        self.tex_h = 64
        self.bones = []

    def bone(self, name, pivot, parent=None, rotation=None):
        b = Bone(name, pivot, parent, rotation)
        self.bones.append(b)
        return b

    def get(self, name):
        for b in self.bones:
            if b.name == name:
                return b
        raise KeyError(name)

    # ------------------------------------------------------------ UV (선반 쌓기 — 큰 것부터)
    def pack(self):
        boxes = [b for bone in self.bones for b in bone.boxes]
        order = sorted(boxes, key=lambda b: (-(b.footprint()[1]), -(b.footprint()[0])))
        W = self.tex_w
        x = y = shelf = 0
        for b in order:
            fw, fh = b.footprint()
            if fw > W:
                raise ValueError(f"{self.key}: box too wide for texture {b.size}")
            if x + fw > W:
                y += shelf
                x = shelf = 0
            b.uv = (x, y)
            x += fw
            shelf = max(shelf, fh)
        used = y + shelf
        h = 64
        while h < used:
            h *= 2
        self.tex_h = h
        return used

    # ------------------------------------------------------------ 텍스처
    def paint(self):
        if any(b.uv is None for bone in self.bones for b in bone.boxes):
            self.pack()
        im = Image.new("RGBA", (self.tex_w, self.tex_h), (0, 0, 0, 0))
        px = im.load()
        n = 0
        for bone in self.bones:
            for b in bone.boxes:
                n += 1
                paint_box(px, b, n)
        return im

    # ------------------------------------------------------------ JSON
    def geo_json(self, identifier=None):
        if any(b.uv is None for bone in self.bones for b in bone.boxes):
            self.pack()
        bones = []
        lo = [1e9, 1e9, 1e9]
        hi = [-1e9, -1e9, -1e9]
        for bone in self.bones:
            j = {"name": bone.name, "pivot": [r(v) for v in bone.pivot]}
            if bone.parent:
                j["parent"] = bone.parent
            if bone.rotation:
                j["rotation"] = [r(v) for v in bone.rotation]
            cubes = []
            for b in bone.boxes:
                c = {"origin": [r(v) for v in b.origin], "size": b.size, "uv": list(b.uv)}
                if b.inflate:
                    c["inflate"] = b.inflate
                cubes.append(c)
                for i in range(3):
                    lo[i] = min(lo[i], b.origin[i])
                    hi[i] = max(hi[i], b.origin[i] + b.size[i])
            if cubes:
                j["cubes"] = cubes
            bones.append(j)
        vw = max(hi[0] - lo[0], hi[2] - lo[2]) / 16 + 2
        vh = (hi[1] - min(0, lo[1])) / 16 + 1.5
        desc = {"identifier": identifier or f"geometry.rpg.monster.{self.key}", "texture_width": self.tex_w, "texture_height": self.tex_h,
                "visible_bounds_width": round(vw, 1), "visible_bounds_height": round(vh, 1), "visible_bounds_offset": [0, round(vh / 2, 1), 0]}
        return {"format_version": "1.16.0", "minecraft:geometry": [{"description": desc, "bones": bones}]}


def r(v):
    return int(v) if float(v).is_integer() else round(v, 3)


# ---------------------------------------------------------------- 그리기

FACE_LIGHT = {"up": "top", "down": "bottom", "north": 1.0, "east": 0.93, "west": 0.93, "south": 0.88}


def face_rects(b):
    u, v = b.uv
    w, h, d = b.size
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def value_noise(rng, w, h, cell):
    """칸 cell 마다 정한 값을 부드럽게 이은 얼룩 (0 ~ 1)"""
    gw, gh = w // cell + 2, h // cell + 2
    g = [[rng.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx = tx * tx * (3 - 2 * tx)
            ty = ty * ty * (3 - 2 * ty)
            a = g[y0][x0] * (1 - tx) + g[y0][x0 + 1] * tx
            b2 = g[y0 + 1][x0] * (1 - tx) + g[y0 + 1][x0 + 1] * tx
            out[y][x] = a * (1 - ty) + b2 * ty
    return out


def paint_face(m, face, w, h, rng):
    """한 면 (w × h) 의 색 [y][x] = (r, g, b, a)"""
    if w <= 0 or h <= 0:
        return []
    if m.pattern == "crystal":
        return paint_crystal(m, face, w, h, rng)
    base = m.base
    lf = FACE_LIGHT[face]
    k_face = m.top if lf == "top" else m.bottom if lf == "bottom" else lf
    blot = value_noise(rng, w, h, 3)
    dark_acc = m.accent if m.accent else mul(base, 0.72)
    out = [[None] * w for _ in range(h)]
    side = face in ("north", "south", "east", "west")
    for y in range(h):
        for x in range(w):
            c = base
            k = k_face * m.light
            if side and h > 2:
                k *= 1.07 - 0.16 * (y / (h - 1))
            k *= 0.94 + 0.12 * blot[y][x]
            p = m.pattern
            if p == "fur":
                pass
            elif p == "bristle":
                if (x * 7 + y // 2 * 3 + m.seed) % 5 == 0:
                    c = dark_acc
            elif p == "chitin":
                if y % m.band == 0:
                    k *= 0.74
                elif y % m.band == 1:
                    k *= 1.12
            elif p == "plates":
                if y % m.band == 0 or (x + (y // m.band) * 2) % (m.band + 2) == 0:
                    c = dark_acc
            elif p == "scales":
                if (y % 2 == 0 and x % 3 == 0) or (y % 2 == 1 and x % 3 == 1):
                    k *= 0.8
            elif p == "skin":
                if blot[y][x] > 0.82:
                    c = mix(c, dark_acc, 0.6)
            elif p == "ice":
                if (x + y + m.seed) % 7 == 0:
                    c = mix(c, (255, 255, 255), 0.55)
                elif (x - y + m.seed) % 11 == 0:
                    k *= 0.78
            elif p == "knit":
                k *= 0.9 if y % 2 == 0 else 1.04
                if x % 2 == y % 4 // 2:
                    k *= 0.95
            elif p == "wood":
                if (x + m.seed) % 3 == 0:
                    k *= 0.86
            elif p == "bone":
                if rng.random() < 0.05:
                    k *= 0.85
            elif p == "metal":
                if y == 0:
                    k *= 1.2
            c = mul(c, k)
            n = rng.randint(-m.noise, m.noise)
            c = tuple(clamp(v + n) for v in c)
            out[y][x] = c
    if m.pattern in ("fur", "bristle"):
        # 털 결: 세로 짧은 줄 (어둡게 · 밝게)
        for _ in range(max(1, w * h // 5)):
            x = rng.randrange(w)
            y = rng.randrange(h)
            ln = rng.randint(1, 3)
            kk = 0.8 if rng.random() < 0.6 else 1.15
            if m.pattern == "bristle":
                kk = 0.68 if rng.random() < 0.7 else 1.2
            for i in range(ln):
                if y + i < h:
                    out[y + i][x] = mul(out[y + i][x], kk)
    if m.edge < 1 and w > 2 and h > 2:
        for y in range(h):
            for x in range(w):
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    out[y][x] = mul(out[y][x], m.edge)
    a = 255 if m.glow <= 0 else clamp(255 * (1 - m.glow * 0.75))
    cells = [[(c[0], c[1], c[2], a) for c in row] for row in out]
    if m.seam is not None or m.veins or m.spots or m.rim is not None:
        paint_glow(m, face, w, h, rng, cells)
    return cells


def glow_alpha(g):
    return 255 if g <= 0 else clamp(255 * (1 - g * 0.75))


def paint_crystal(m, face, w, h, rng):
    """0.32.0 공허 수정 (pattern "crystal" — 깎은 면): 옆면은 위로 갈수록 밝고 왼쪽 모서리가 밝게 빛나며 오른쪽 모서리는 그늘,
    폭 4 칸 넘는 면은 가운데 심이 빛남 (아래로 옅어짐), 윗면은 밝은 끝 · 아랫면은 어둡게. 얼룩 · 줄무늬 없이 (체크무늬로 보이지 않게).
    seam = 심 · 모서리 빛 색 (없으면 바탕을 밝게), glow = 몸의 빛"""
    side = face in ("north", "south", "east", "west")
    base = m.base
    hi = mix(base, (255, 255, 255), 0.5)
    core = m.seam if m.seam is not None else mix(base, (255, 225, 255), 0.65)
    # 면마다 밝은 모서리를 돌려서 (북 · 동은 왼쪽, 남 · 서는 오른쪽) 둘러봐도 깎은 면처럼
    lit_left = face in ("north", "east")
    cells = [[None] * w for _ in range(h)]
    for y in range(h):
        t = y / (h - 1) if h > 1 else 0.0  # 0 위 → 1 아래
        for x in range(w):
            if side:
                c = mul(base, (1.2 - 0.45 * t) * (0.97 + 0.06 * rng.random()))
                a = glow_alpha(m.glow * (1 - 0.35 * t))
                lit = x == (0 if lit_left else w - 1)
                shade = w >= 3 and x == (w - 1 if lit_left else 0)
                if w >= 2 and lit:
                    c = mix(c, hi, 0.7 - 0.35 * t)
                    a = glow_alpha(min(1.0, m.glow + 0.35) * (1 - 0.3 * t))
                elif shade:
                    c = mul(c, 0.7)
                    a = glow_alpha(m.glow * 0.4)
                if w >= 4 and x == w // 2 - (0 if lit_left else 1):
                    c = mix(c, core, 0.75 * (1 - 0.55 * t))
                    a = glow_alpha(1.0 - 0.45 * t)
            elif face == "up":
                c = mix(base, core, 0.55)
                a = glow_alpha(min(1.0, m.glow + 0.3))
            else:
                c = mul(base, 0.55)
                a = 255
            n = rng.randint(-m.noise, m.noise)
            c = tuple(clamp(v + n) for v in c)
            cells[y][x] = (c[0], c[1], c[2], a)
    return cells


def paint_glow(m, face, w, h, rng, cells):
    """0.32.0 빛나는 무늬 (공허 몬스터): 이음새 줄 · 핏줄 · 반점 · 테두리 — 칸 색을 바꾸고 알파를 낮춰 빛나게"""
    def put(x, y, col, g, k=1.0):
        if 0 <= x < w and 0 <= y < h:
            c = mul(col, k)
            cells[y][x] = (c[0], c[1], c[2], glow_alpha(g))

    side = face in ("north", "south", "east", "west")
    if m.seam is not None and w > 1 and h > 1:
        band = max(2, int(m.seam_band))
        horiz = m.seam_dir in ("h", "grid") or not side
        vert = m.seam_dir in ("v", "grid")
        if horiz:
            for y in range(band - 1, h - 1, band):
                for x in range(w):
                    if rng.random() >= m.seam_gap:
                        put(x, y, m.seam, m.seam_glow, 0.85 + 0.15 * rng.random())
        if vert:
            for x in range(band - 1, w - 1, band):
                for y in range(h):
                    if rng.random() >= m.seam_gap:
                        put(x, y, m.seam, m.seam_glow, 0.85 + 0.15 * rng.random())
    if m.veins and w > 2 and h > 2:
        col = m.vein or m.seam or (255, 80, 220)
        n = max(1, int(round(m.veins * (w * h) / 64.0)))
        for _ in range(n):
            x = rng.randrange(w)
            y = rng.randrange(h)
            ln = rng.randint(3, 6)
            dx, dy = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
            for i in range(ln):
                put(x, y, col, m.vein_glow * (1 - i / (ln + 2)), 1 - 0.08 * i)
                if rng.random() < 0.4:
                    dx, dy = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
                x += dx
                y += dy
    if m.spots:
        col = m.spot or m.seam or (255, 120, 230)
        n = max(1, int(round(m.spots * (w * h) / 64.0)))
        for _ in range(n):
            put(rng.randrange(w), rng.randrange(h), col, m.spot_glow)
    if m.rim is not None and w > 2 and h > 2:
        for y in range(h):
            for x in range(w):
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    put(x, y, m.rim, m.rim_glow, 0.9 + 0.1 * rng.random())


def paint_box(px, b, n):
    rects = face_rects(b)
    for face in FACES:
        u, v, w, h = rects[face]
        m = b.faces.get(face, b.mat)
        if m is None:
            continue
        rng = random.Random(m.seed * 1000003 + n * 7919 + FACES.index(face) * 131 + w * 17 + h)  # 늘 같은 그림 (해시 순서와 무관)
        cells = paint_face(m, face, w, h, rng)
        for y in range(h):
            for x in range(w):
                px[u + x, v + y] = cells[y][x]
    # 무늬 · 눈 같은 점: (면, x, y, 색, 빛 0~1)
    for d in b.decals:
        face, x, y, col = d[:4]
        glow = d[4] if len(d) > 4 else 0
        u, v, w, h = rects[face]
        if 0 <= x < w and 0 <= y < h:
            c = rgb(col)
            px[u + x, v + y] = (c[0], c[1], c[2], 255 if glow <= 0 else clamp(255 * (1 - glow * 0.75)))


# ---------------------------------------------------------------- 애니메이션 도움

def anim_file(key, anims):
    """{이름: 동작} → 애니메이션 파일 (ID animation.rpg.monster.<키>.<이름>)"""
    return {"format_version": "1.8.0", "animations": {f"animation.rpg.monster.{key}.{n}": a for n, a in anims.items()}}


def kf(frames):
    """[(시각, [x, y, z]), …] → 키프레임"""
    return {f"{t:.2f}".rstrip("0").rstrip(".") if t else "0.0": [r(v) if isinstance(v, (int, float)) else v for v in vec] for t, vec in frames}


# ---------------------------------------------------------------- 미리보기 (애드온 미리보기 그리기를 씀)

def _preview_mod():
    addon = os.environ.get("RPG_ADDON", os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "..", "rpg_addon"))
    tools = os.path.join(os.path.abspath(addon), "generator", "tools")
    if not os.path.isdir(tools):
        raise SystemExit(f"미리보기에는 애드온 소스가 필요함 (RPG_ADDON): {tools}")
    sys.path.insert(0, tools)
    import monster_preview  # noqa: E402
    return monster_preview


def preview(entries, out, poses=(("idle", None),), walk=False):
    """entries = [(키, 이름, geo dict, 텍스처 PIL, {이름: 동작}, 크기, (가로, 키), 레벨)] → 몬스터마다 앞 · 옆 (poses 마다 한 줄)"""
    mp = _preview_mod()
    import numpy as np
    from PIL import ImageDraw
    W, H = 360, 400
    rows = []
    for key, name, geo, tex, amap, scale, (bw, bh), level in entries:
        g = geo["minecraft:geometry"][0]
        row = []
        for pose, t in poses:
            env = {"q.life_time": 0.4, "q.modified_distance_moved": 3.3 if walk else 0.0, "q.modified_move_speed": 1.0 if walk else 0.0, "q.hurt_time": 0,
                   "q.anim_time": t or 0, "q.target_x_rotation": 0, "q.target_y_rotation": 0}
            names = ["idle"] + (["walk"] if walk else []) + ([pose] if pose != "idle" else [])
            p = mp.pose_of(amap, names, t or 0.0, env)
            faces, holes = mp.monster_faces(g, tex, p, scale, np.zeros(3))
            px = bw / 2 + 1.2
            pf = [(bn, q / 16.0 + np.array([px, 0, 0]), c, k) for bn, q, c, k in mp.player_faces()]
            ys = [pt[1] for _, q, _, _ in faces for pt in q]
            top = max(ys + [1.8])
            span = max(top, bw + px + 0.6)
            ground = mp.ground_faces(-bw - 2, px + 2, -3, 3)
            for view, pos in (("앞", (-span * 0.9 + px * 0.35, top * 0.75, -span * 2.1)), ("옆", (-span * 2.2, top * 0.6, -0.2))):
                cam = mp.Cam(pos, (px * 0.35, top * 0.45, 0), 42, W, H)
                im = mp.rasterize(ground + faces + pf, cam, (150, 186, 232))
                mp.box_outline(im, cam, 0, bw, bh)
                d = ImageDraw.Draw(im)
                d.rectangle([0, 0, W, 50], fill=(20, 18, 30))
                d.text((8, 4), f"Lv.{level} {name} · {view} · {pose}" + (f" {t:.2f}s" if t else ""), font=mp.font(19), fill=(255, 255, 255))
                d.text((8, 29), f"rpg:{key} ×{scale:g} · 충돌 {bw:.2f}×{bh:.2f} · 키 {top:.2f}" + (f" · 빈 면 {holes}" if holes else ""), font=mp.font(13),
                       fill=(200, 200, 210))
                row.append(im)
        rows.append(row)
    cols = max(len(rw) for rw in rows)
    sheet = Image.new("RGB", (W * cols, H * len(rows)), (30, 28, 40))
    for i, rw in enumerate(rows):
        for j, im in enumerate(rw):
            sheet.paste(im, (j * W, i * H))
    sheet.save(out)
    return out


def texture_sheet(im, out, scale=4):
    """텍스처를 크게 (빛나는 칸은 분홍 테두리)"""
    big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
    bg = Image.new("RGBA", big.size, (60, 60, 70, 255))
    bg.alpha_composite(big.point(lambda v: v))
    bg.convert("RGB").save(out)


def write_json(path, data, indent=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)
        f.write("\n")

def closeup(entries, out, poses=(("idle", None),), walk=False, size=340):
    """몬스터만 크게: 앞 3/4 · 옆 · 뒤 3/4 (몸에 맞춘 카메라) — entries 는 preview 와 같음"""
    mp = _preview_mod()
    import numpy as np
    from PIL import ImageDraw
    W = H = size
    rows = []
    for key, name, geo, tex, amap, scale, (bw, bh), level in entries:
        g = geo["minecraft:geometry"][0]
        for pose, t in poses:
            env = {"q.life_time": 0.4, "q.modified_distance_moved": 3.3 if walk else 0.0, "q.modified_move_speed": 1.0 if walk else 0.0, "q.hurt_time": 0,
                   "q.anim_time": t or 0, "q.target_x_rotation": 0, "q.target_y_rotation": 0}
            names = ["idle"] + (["walk"] if walk else []) + ([pose] if pose != "idle" else [])
            p = mp.pose_of(amap, names, t or 0.0, env)
            faces, holes = mp.monster_faces(g, tex, p, scale, np.zeros(3))
            pts = np.array([pt for _, q, _, _ in faces for pt in q])
            lo, hi = pts.min(0), pts.max(0)
            c = (lo + hi) / 2
            ext = float(max(hi - lo)) * 1.25 + 0.3
            ground = mp.ground_faces(lo[0] - 2, hi[0] + 2, lo[2] - 2, hi[2] + 2)
            row = []
            for view, d in (("앞", (-0.75, 0.45, -1.0)), ("옆", (-1.0, 0.25, 0.0)), ("뒤", (0.7, 0.45, 1.0))):
                dv = np.array(d, float)
                dv = dv / np.linalg.norm(dv)
                cam = mp.Cam(tuple(c + dv * ext * 1.55), tuple(c), 42, W, H)
                im = mp.rasterize(ground + faces, cam, (150, 186, 232))
                dr = ImageDraw.Draw(im)
                dr.rectangle([0, 0, W, 26], fill=(20, 18, 30))
                dr.text((6, 3), f"{name} · {view} · {pose}" + (f" {t:.2f}" if t else "") + (f" · 빈 면 {holes}" if holes else ""), font=mp.font(16), fill=(255, 255, 255))
                row.append(im)
            rows.append(row)
    sheet = Image.new("RGB", (W * 3, H * len(rows)), (30, 28, 40))
    for i, rw in enumerate(rows):
        for j, im in enumerate(rw):
            sheet.paste(im, (j * W, i * H))
    sheet.save(out)
    return out
