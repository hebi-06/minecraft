"""베드락 무기 모델 (.geo.json 의 weapon 뼈) + 텍스처 읽기 — weapon_models.py (자바 아이템 모델) · weapon_art_more.py (아이콘) · java_fx_art.py
(스킬 그림 — 날아가는 무기) 가 함께 씀.

받는 모양
- 애드온 · 임시 디자인: 16 × 16 팔레트 텍스처, 상자마다 면 UV 한 칸 (uv_size 1 × 1) — 지금까지와 똑같이 바꿈
- 다듬은 디자인 (클로드 디자인 · Blockbench): 아무 크기 텍스처 (description.texture_width · height = UV 단위, PNG 가 그 배수여도 됨),
  상자 UV (uv: [u, v] — mirror · 소수 크기는 베드락처럼 내림) 나 면 UV (uv: {면: {uv, uv_size}} — uv_size 가 음수면 뒤집힘), inflate,
  weapon 뼈 아래 자식 뼈 (회전 없음 — 자리만, 회전이 있으면 상자 회전처럼), fx 로케이터 ([x, y, z] · {"offset": [...]})
- 회전: 자바 아이템 모델 상자는 한 축 · 22.5° 단위 · ±45° 만 → 그 밖은 가장 큰 축 · 가장 가까운 값으로 바꾸고 경고 (warnings)
- 빛나는 칸: 알파가 0 보다 크고 255 보다 작은 픽셀 (애드온은 102) — 자바 아이템 모델은 상자 단위로 빛나므로 상자 면 넓이의 절반 넘게 빛나면 그 상자가 빛남

좌표: 베드락 모델 좌표 그대로 (weapon_models.py 가 three.js · 자바 모델 좌표로 바꿈). 면 이름은 베드락 그대로 — east = -x 쪽 면, west = +x 쪽 면
(클라이언트 BedrockLooks 와 같은 대응), 면 UV 의 뜻도 베드락 (그대로 자바 아이템 모델 면 uv [u, v, u + w, v + h] 가 됨 — 위 · 아래 면 포함).

자바 모델 디자인 (0.12.7 — 사용자 리워크, tools/weapon_pack_import.py 가 가져옴): <디자인>.geo.json 옆에 <디자인>.java.json (+ .java.png ·
.java.png.mcmeta) 이 있으면 그것을 씀 (load_java). 뼈대 (손 자세) 와 지금 fx 자리는 .geo.json 에서, 상자는 자바 모델에서:
팩 좌표 → `mmorpg` 자리 맞춤 (anchor → to, scale 배) 으로 베드락 디자인 좌표. weapon_models.py 가 자바 모델로 바꿀 때 x 가 뒤집히므로
(베드락 → 자바) 결과는 팩 모델의 x 거울상 = 베드락 디자인 좌표로 그린 지금 디자인들과 같은 쪽 (방패 판 · 활 시위 · 날 -z):
면 이름 east ↔ west, 면 UV 가로 뒤집기 (면 rotation 90 · 270 이면 세로), 회전 각 x · z 는 부호 반대. 빛 = light_emission 그대로 (Cube.glow),
면 rotation (Cube.face_rot), 텍스처는 프레임 띠 · 반투명 그대로 (Model.java).
"""
import json
import math
import os
from dataclasses import dataclass, field

import numpy as np
from PIL import Image

FACES = ("north", "east", "south", "west", "up", "down")
ALLOWED_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


@dataclass
class Cube:
    lo: tuple  # 베드락 좌표 최소 (inflate 반영)
    hi: tuple  # 최대
    faces: dict  # 면 → (u, v, w, h) UV 단위 (베드락 면 UV 뜻), 없는 면은 빠짐
    rot: tuple = None  # (축 'x' · 'y' · 'z', 베드락 각 (도), 회전 중심 (베드락 좌표)) — 한 축 · 22.5° 단위로 맞춘 것
    glow: int = None  # 자바 모델 디자인: light_emission (0 ~ 15) 그대로 — None 이면 텍스처 알파 (빛나는 칸) 로 정함
    face_rot: dict = field(default_factory=dict)  # 자바 모델 디자인: 면 → 면 rotation (90 · 180 · 270)
    rescale: bool = False
    name: str = None


@dataclass
class Model:
    name: str
    geo: dict
    tex: Image.Image
    tw: float  # UV 단위 너비 (description.texture_width)
    th: float
    cubes: list
    fx: list = None  # fx 로케이터 (베드락 좌표)
    warnings: list = field(default_factory=list)
    java: dict = None  # 자바 모델 디자인: elements (팩 좌표) · anchor · to · scale · strip (프레임 띠) · mcmeta · frame (한 프레임 크기)
    fx_colors: list = None  # 자바 모델 디자인: 빛 알갱이 색 (fx 자리 상자의 빛나는 색)

    @property
    def palette(self):
        """애드온 팔레트 모양 (면마다 1 × 1 칸) 인지"""
        if self.java:
            return False
        return all(abs(abs(r[2]) - 1) < 1e-9 and abs(abs(r[3]) - 1) < 1e-9 for c in self.cubes for r in c.faces.values())

    # ------------------------------------------------------------ 텍스처 뽑기

    def pixel(self, u, v):
        """UV 단위 (u, v) 의 픽셀 (PNG 가 UV 크기의 배수여도)"""
        w, h = self.tex.size
        x = min(w - 1, max(0, int(math.floor(u * w / self.tw))))
        y = min(h - 1, max(0, int(math.floor(v * h / self.th))))
        return self.tex.getpixel((x, y))

    def face_pixel(self, rect, s, t):
        """면 rect (u, v, w, h) 의 (s, t) ∈ [0, 1]² 자리 (s = 면 UV 의 가로, t = 세로) 의 픽셀 — 칸 가운데로 맞춤"""
        u, v, w, h = rect
        uu = u + w * min(0.999, max(0.001, s))
        vv = v + h * min(0.999, max(0.001, t))
        return self.pixel(uu, vv)

    def glow_fraction(self, cube):
        """상자 면들의 픽셀 가운데 빛나는 칸 (0 < 알파 < 255) 의 비율"""
        n = g = 0
        w, h = self.tex.size
        for u, v, fw, fh in cube.faces.values():
            x0, x1 = sorted((u * w / self.tw, (u + fw) * w / self.tw))
            y0, y1 = sorted((v * h / self.th, (v + fh) * h / self.th))
            for y in range(int(math.floor(y0 + 1e-6)), max(int(math.floor(y0 + 1e-6)) + 1, int(math.ceil(y1 - 1e-6)))):
                for x in range(int(math.floor(x0 + 1e-6)), max(int(math.floor(x0 + 1e-6)) + 1, int(math.ceil(x1 - 1e-6)))):
                    a = self.tex.getpixel((min(w - 1, max(0, x)), min(h - 1, max(0, y))))[3]
                    if a == 0:
                        continue
                    n += 1
                    if a < 255:
                        g += 1
        return g / n if n else 0.0

    def side_color(self, cube, y, z):
        """옆모습 (베드락 +x 에서 -x 쪽으로 봄 — 보이는 면 = west) 의 (y, z) 자리 색. 그 면이 없으면 north"""
        r = cube.faces.get("west") or cube.faces.get("east") or cube.faces.get("north")
        if r is None:
            return None
        (_, y0, z0), (_, y1, z1) = cube.lo, cube.hi
        s = (z - z0) / max(1e-9, z1 - z0)  # west 면: 가로 = +z 쪽으로
        t = (y1 - y) / max(1e-9, y1 - y0)  # 세로 = 아래로
        return self.face_pixel(r, s, t)


# ------------------------------------------------------------ 읽기

def box_faces(u, v, size, mirror):
    """상자 UV → 면 UV (베드락 면 UV 뜻): 소수 크기는 내림 (베드락), mirror 면 동 · 서를 바꾸고 가로로 뒤집음"""
    w, h, d = (math.floor(x + 1e-9) for x in size)
    f = {
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
        "up": (u + d + w, v + d, -w, -d),
        "down": (u + d + w + w, v, -w, d),
    }
    if mirror:
        f["east"], f["west"] = f["west"], f["east"]
        f = {k: (a + c, b, -c, e) for k, (a, b, c, e) in f.items()}
    return f


def single_axis(rot, where, warnings):
    """베드락 회전 [x, y, z] → (축, 각) 한 축 · 22.5° 단위 (자바 아이템 모델) — 맞지 않으면 바꾸고 경고. 회전 없으면 None"""
    if not rot:
        return None
    nz = [(i, float(a)) for i, a in enumerate(rot) if abs(float(a)) > 1e-6]
    if not nz:
        return None
    if len(nz) > 1:
        warnings.append(f"{where}: 회전이 두 축 이상 {list(rot)} — 가장 큰 축만 씀 (자바 아이템 모델은 한 축)")
    i, a = max(nz, key=lambda t: abs(t[1]))
    snapped = min(ALLOWED_ANGLES, key=lambda x: abs(x - a))
    if abs(snapped - a) > 1e-6:
        warnings.append(f"{where}: 회전 {a:g}° → {snapped:g}° (자바 아이템 모델은 22.5° 단위 · -45 ~ 45°)")
    if snapped == 0:
        return None
    return "xyz"[i], snapped


def locator_pos(v):
    if isinstance(v, dict):
        v = v.get("offset")
    if isinstance(v, list) and len(v) == 3:
        return [float(x) for x in v]
    return None


def java_paths(geo_path):
    """<디자인>.geo.json → (<디자인>.java.json, <디자인>.java.png)"""
    base = geo_path[:-len(".geo.json")] if geo_path.endswith(".geo.json") else os.path.splitext(geo_path)[0]
    return base + ".java.json", base + ".java.png"


def load(name, geo_path, tex_path, bone="weapon", java=True):
    if java and bone == "weapon":
        jp, jt = java_paths(geo_path)
        if os.path.exists(jp):
            return load_java(name, jp, jt, geo_path, tex_path)
    geo = json.load(open(geo_path, encoding="utf-8"))["minecraft:geometry"][0]
    tex = Image.open(tex_path).convert("RGBA")
    pre = []
    if any(b["name"].lower() == bone for b in geo["bones"]) and bone == "weapon":
        geo = with_skeleton(geo, pre, name)
    desc = geo.get("description", {})
    tw = float(desc.get("texture_width") or tex.size[0])
    th = float(desc.get("texture_height") or tex.size[1])
    bones = {b["name"].lower(): b for b in geo["bones"]}
    root = bones.get(bone)
    if root is None:
        raise ValueError(f"{name}: '{bone}' 뼈가 없음")
    # weapon 뼈와 그 아래 뼈들 (부모를 따라 올라가 weapon 이 나오는 뼈)
    def under(b):
        seen = set()
        while b is not None and b["name"].lower() not in seen:
            if b["name"].lower() == bone:
                return True
            seen.add(b["name"].lower())
            b = bones.get((b.get("parent") or "").lower())
        return False
    members = [b for b in geo["bones"] if under(b)]
    m = Model(name, geo, tex, tw, th, [], warnings=pre)
    for b in members:
        bname = b["name"]
        brot = None
        if b is not root:
            brot = single_axis(b.get("rotation"), f"뼈 {bname}", m.warnings)
            p = (b.get("parent") or "").lower()
            if brot is not None and p != bone and single_axis(bones[p].get("rotation"), "", []) is not None:
                m.warnings.append(f"뼈 {bname}: 회전한 뼈 안의 회전한 뼈 — 바깥 회전은 빼고 씀")
            if b.get("mirror"):
                m.warnings.append(f"뼈 {bname}: 뼈의 mirror 는 쓰지 않음 (상자마다 mirror 로)")
        for ci, c in enumerate(b.get("cubes", [])):
            o = [float(x) for x in c["origin"]]
            s = [float(x) for x in c["size"]]
            g = float(c.get("inflate", 0) or 0)
            lo = tuple(o[i] - g for i in range(3))
            hi = tuple(o[i] + s[i] + g for i in range(3))
            uv = c.get("uv")
            if isinstance(uv, dict):
                faces = {}
                for f in FACES:
                    fd = uv.get(f)
                    if not fd:
                        continue
                    fu, fv = (float(x) for x in fd["uv"])
                    fw, fh = (float(x) for x in fd.get("uv_size", [1, 1]))
                    faces[f] = (fu, fv, fw, fh)
            else:
                u, v = (float(x) for x in (uv or [0, 0]))
                faces = box_faces(u, v, s, bool(c.get("mirror", b.get("mirror", False))))
            rot = single_axis(c.get("rotation"), f"뼈 {bname} 상자 {ci}", m.warnings)
            pivot = c.get("pivot")
            if rot is not None:
                rot = (rot[0], rot[1], tuple(float(x) for x in (pivot or b.get("pivot") or [0, 0, 0])))
                if brot is not None:
                    m.warnings.append(f"뼈 {bname} 상자 {ci}: 회전한 뼈 안의 회전한 상자 — 상자 회전만 씀")
            elif brot is not None:
                rot = (brot[0], brot[1], tuple(float(x) for x in (b.get("pivot") or [0, 0, 0])))
            m.cubes.append(Cube(lo, hi, faces, rot))
        loc = (b.get("locators") or {}).get("fx")
        if loc is not None and m.fx is None:
            m.fx = locator_pos(loc)
    if not m.cubes:
        raise ValueError(f"{name}: weapon 뼈에 상자가 없음")
    return m


# ------------------------------------------------------------ 손에 드는 뼈대 (애드온 무기 attachable 과 같은 뼈 — 손 자세 계산에 씀)

SKELETON = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "waist", "parent": "root", "pivot": [0, 12, 0]},
    {"name": "body", "parent": "waist", "pivot": [0, 24, 0]},
    {"name": "head", "parent": "body", "pivot": [0, 24, 0]},
    {"name": "leftArm", "parent": "body", "pivot": [5, 22, 0]},
    {"name": "leftItem", "parent": "leftArm", "pivot": [6, 15, 1]},
    {"name": "rightArm", "parent": "body", "pivot": [-5, 22, 0]},
    {"name": "rightItem", "parent": "rightArm", "pivot": [-6, 15, 1]},
    {"name": "leftLeg", "parent": "root", "pivot": [1.9, 12, 0]},
    {"name": "rightLeg", "parent": "root", "pivot": [-1.9, 12, 0]},
]
WEAPON_PIVOT = [-6, 13, 0]
WEAPON_ROTATION = [60, 0, 0]
# 종류마다 weapon 뼈 회전 (애드온 디자인 그대로 — 디자인 이름 앞부분 <종류>_ 로 고름, 나머지는 WEAPON_ROTATION)
WEAPON_ROTATION_KIND = {"bow": [82, 0, 0], "staff": [30, 0, 0], "shield": [0, 0, 0]}


def weapon_rotation(name):
    """디자인 이름 (bow_rare_lv0 …) → 뼈대가 빠진 파일에 채울 weapon 뼈 회전"""
    kind = (name or "").split("_")[0]
    return list(WEAPON_ROTATION_KIND.get(kind, WEAPON_ROTATION))


def with_skeleton(geo, warnings, name=None):
    """뼈대가 빠진 파일 (weapon 뼈만 있는 등) 이면 애드온 뼈대를 채움 — weapon 뼈의 부모 · 회전 중심 · 회전이 없으면 기본값 (경고,
    회전은 종류마다 — weapon_rotation). geo = minecraft:geometry[0] (고친 사본을 돌려줌)"""
    import copy

    g = copy.deepcopy(geo)
    have = {b["name"].lower() for b in g["bones"]}
    added = [b["name"] for b in SKELETON if b["name"].lower() not in have]
    if added:
        warnings.append(f"뼈대가 없어 애드온 뼈대를 채움: {', '.join(added)} (손 자세는 그 뼈대 기준)")
        g["bones"] = [copy.deepcopy(b) for b in SKELETON if b["name"].lower() not in have] + g["bones"]
    w = next(b for b in g["bones"] if b["name"].lower() == "weapon")
    if (w.get("parent") or "").lower() != "rightitem":
        warnings.append(f"weapon 뼈의 부모 {w.get('parent')!r} → rightItem")
        w["parent"] = "rightItem"
    if w.get("pivot") is None:
        warnings.append(f"weapon 뼈 회전 중심이 없음 → {WEAPON_PIVOT}")
        w["pivot"] = list(WEAPON_PIVOT)
    if w.get("rotation") is None:
        rot = weapon_rotation(name)
        warnings.append(f"weapon 뼈 회전이 없음 → {rot}")
        w["rotation"] = rot
    return g


# ------------------------------------------------------------ 자바 모델 디자인 (사용자 리워크 — weapon_pack)

JAVA_FACE = {"east": "west", "west": "east"}  # 팩 (자바) 면 → 베드락 면 이름 (x 거울)
JAVA_ANGLE = {"x": -1.0, "y": 1.0, "z": -1.0}  # 팩 회전 각 × 이것 = 베드락 각 (weapon_models 가 자바로 되돌리면 x 거울상의 회전: x 그대로 · y · z 반대)


def load_java(name, java_path, tex_path, geo_path, geo_tex_path):
    import java_model_view as jmv

    base = load(name, geo_path, geo_tex_path, java=False)  # 뼈대 (손 자세) · 지금 fx 자리
    jm = json.load(open(java_path, encoding="utf-8"))
    tr = jm["mmorpg"]
    A = np.array(tr["anchor"], float)
    B = np.array(tr["to"], float)
    k = float(tr["scale"])

    def G(p):
        return B + k * (np.asarray(p, float) - A)

    strip = Image.open(tex_path).convert("RGBA")
    mc_path = tex_path + ".mcmeta"
    mcmeta = json.load(open(mc_path, encoding="utf-8")) if os.path.exists(mc_path) else None
    W = strip.size[0]
    fh = W if mcmeta else strip.size[1]
    if mcmeta and strip.size[1] % W:
        raise ValueError(f"{name}: 맥동 텍스처 높이 {strip.size[1]} 가 너비 {W} 의 배수가 아님")
    m = Model(name, base.geo, strip.crop((0, 0, W, fh)), 16.0, 16.0, [], warnings=list(base.warnings))
    views = []
    for e in jm["elements"]:
        el = jmv.Element(e)
        lo, hi = G(el.f), G(el.t)
        faces, frot = {}, {}
        for jname, (uv, r) in el.faces.items():
            u1, v1, u2, v2 = uv
            if r in (90, 270):
                rect = (u1, v2, u2 - u1, v1 - v2)  # 돌린 면: 텍스처 세로가 면의 가로 → 세로를 뒤집어 거울
            else:
                rect = (u2, v1, u1 - u2, v2 - v1)  # 가로를 뒤집어 거울
            b = JAVA_FACE.get(jname, jname)
            faces[b] = rect
            if r:
                frot[b] = r
        rot = None
        jr = e.get("rotation")
        if jr and float(jr.get("angle", 0)):
            rot = (jr["axis"], JAVA_ANGLE[jr["axis"]] * float(jr["angle"]), tuple(float(x) for x in G(jr.get("origin", [8, 8, 8]))))
        m.cubes.append(Cube(tuple(float(x) for x in lo), tuple(float(x) for x in hi), faces, rot, glow=el.glow, face_rot=frot,
                            rescale=bool(jr and jr.get("rescale")), name=el.name or None))
        views.append(el)
    # 빛 알갱이 자리: 지금 fx 자리에서 가장 가까운 빛나는 상자의 가운데 (빛나는 상자가 없으면 지금 자리)
    if base.fx is not None:
        old = np.array(base.fx, float)
        best = None
        for el in views:
            if el.glow <= 0:
                continue
            pts = np.array([G(p) for p in el.corners()])
            lo_, hi_ = pts.min(0), pts.max(0)
            d = float(np.linalg.norm(np.maximum(0, np.maximum(lo_ - old, old - hi_))))
            c = G(el.center())
            score = d + 0.01 * float(np.linalg.norm(c - old))
            if best is None or score < best[0]:
                best = (score, c, el)
        if best is not None:
            m.fx = [round(float(x), 4) for x in best[1]]
            m.fx_colors = [glow_color(strip, W, fh, best[2])]
        else:
            m.fx = list(base.fx)
    m.java = {"elements": jm["elements"], "anchor": A, "to": B, "scale": k, "strip": strip, "mcmeta": mcmeta, "frame": (W, fh),
              "path": java_path}
    return m


def glow_color(strip, W, fh, el):
    """빛나는 상자의 색: 모든 프레임 · 모든 면 픽셀을 밝기로 무게 매긴 평균 → 가장 큰 값이 1"""
    arr = np.asarray(strip, dtype=np.float64)
    frames = max(1, arr.shape[0] // fh)
    acc = np.zeros(3)
    wsum = 0.0
    for uv, _ in el.faces.values():
        u0, u1 = sorted((uv[0], uv[2]))
        v0, v1 = sorted((uv[1], uv[3]))
        x0, x1 = int(math.floor(u0 / 16 * W)), max(int(math.floor(u0 / 16 * W)) + 1, int(math.ceil(u1 / 16 * W)))
        y0, y1 = int(math.floor(v0 / 16 * fh)), max(int(math.floor(v0 / 16 * fh)) + 1, int(math.ceil(v1 / 16 * fh)))
        for f in range(frames):
            px = arr[f * fh + y0:f * fh + y1, x0:x1].reshape(-1, 4)
            px = px[px[:, 3] > 0]
            if not len(px):
                continue
            lum = px[:, :3].max(1) / 255.0
            w = lum ** 2
            acc += (px[:, :3] * w[:, None]).sum(0)
            wsum += float(w.sum())
    if wsum <= 0:
        return [1.0, 1.0, 1.0]
    c = acc / wsum
    c = c / max(1e-9, c.max())
    return [round(float(x), 3) for x in c]
