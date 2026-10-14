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
"""
import json
import math
from dataclasses import dataclass, field

from PIL import Image

FACES = ("north", "east", "south", "west", "up", "down")
ALLOWED_ANGLES = (-45.0, -22.5, 0.0, 22.5, 45.0)


@dataclass
class Cube:
    lo: tuple  # 베드락 좌표 최소 (inflate 반영)
    hi: tuple  # 최대
    faces: dict  # 면 → (u, v, w, h) UV 단위 (베드락 면 UV 뜻), 없는 면은 빠짐
    rot: tuple = None  # (축 'x' · 'y' · 'z', 베드락 각 (도), 회전 중심 (베드락 좌표)) — 한 축 · 22.5° 단위로 맞춘 것


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

    @property
    def palette(self):
        """애드온 팔레트 모양 (면마다 1 × 1 칸) 인지"""
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


def load(name, geo_path, tex_path, bone="weapon"):
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
