"""갑옷 모델 짓기 도구 (0.36.0 공허의 갑옷 — tools/void_armor.py 가 씀. 베드락 좌표: 위 +y, 앞 -z, 사람의 오른쪽 -x, 픽셀).

Piece: 뼈 · 상자 (부풀림 · 면마다 칠하기) → 베드락 모델 (.geo.json) + 텍스처 + 빛 그림.
칠하기 함수 paint(face, x, y, w, h) → (r, g, b[, a]) 또는 None (투명). 면 자리 (밖에서 본 그대로):
north (앞) 왼쪽 = 사람의 오른쪽 (-x) · south (뒤) 왼쪽 = +x · west (-x 옆) 왼쪽 = 뒤 · east (+x 옆) 왼쪽 = 앞 ·
top 위쪽 줄 = 뒤 (+z), 아래쪽 줄 = 앞.
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_items  # noqa: E402

FACES = ("top", "bottom", "west", "north", "east", "south")


def regions(u, v, dx, dy, dz):
    return {
        "top": (u + dz, v, dx, dz), "bottom": (u + dz + dx, v, dx, dz),
        "west": (u, v + dz, dz, dy), "north": (u + dz, v + dz, dx, dy),
        "east": (u + dz + dx, v + dz, dz, dy), "south": (u + 2 * dz + dx, v + dz, dx, dy),
    }


def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, x * k)) for x in c[:3])


def noise(x, y, seed=0):
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


class Piece:
    def __init__(self, name, tw=128, th=128):
        self.name = name
        self.tw, self.th = tw, th
        self.bones = []
        self.cubes = []

    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=(0, 0, 0)):
        b = {"name": name, "pivot": [float(x) for x in pivot]}
        if parent:
            b["parent"] = parent
        if any(rotation):
            b["rotation"] = [float(x) for x in rotation]
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, paint, glow=None, inflate=0.0):
        assert all(float(s).is_integer() for s in size), (self.name, size)
        self.cubes.append((bone, [float(x) for x in origin], [int(s) for s in size], paint, glow, float(inflate)))

    def build(self):
        order = sorted(range(len(self.cubes)), key=lambda i: (-(self.cubes[i][2][2] + self.cubes[i][2][1]), -(self.cubes[i][2][0] + self.cubes[i][2][2])))
        uv = {}
        x = y = row_h = 0
        for i in order:
            dx, dy, dz = self.cubes[i][2]
            w, h = 2 * (dx + dz), dz + dy
            if x + w > self.tw:
                x, y, row_h = 0, y + row_h, 0
            if y + h > self.th:
                raise SystemExit(f"{self.name}: texture {self.tw}x{self.th} full")
            uv[i] = (x, y)
            x += w
            row_h = max(row_h, h)
        tex = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        glow = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        has_glow = False
        tp, gp = tex.load(), glow.load()
        by_bone = {}
        for i, (bone, origin, size, paint, gpaint, inflate) in enumerate(self.cubes):
            u, v = uv[i]
            dx, dy, dz = size
            for face, (fx, fy, fw, fh) in regions(u, v, dx, dy, dz).items():
                for yy in range(fh):
                    for xx in range(fw):
                        c = paint(face, xx, yy, fw, fh)
                        if c is not None:
                            c = tuple(c) + ((255,) if len(c) == 3 else ())
                            tp[fx + xx, fy + yy] = tuple(int(max(0, min(255, round(k)))) for k in c)
                        if gpaint is not None:
                            g = gpaint(face, xx, yy, fw, fh)
                            if g is not None:
                                g = tuple(g) + ((255,) if len(g) == 3 else ())
                                gp[fx + xx, fy + yy] = tuple(int(max(0, min(255, round(k)))) for k in g)
                                has_glow = True
            c = {"origin": origin, "size": size, "uv": [u, v]}
            if inflate:
                c["inflate"] = inflate
            by_bone.setdefault(bone, []).append(c)
        bones = []
        for b in self.bones:
            b = dict(b)
            if b["name"] in by_bone:
                b["cubes"] = by_bone[b["name"]]
            bones.append(b)
        geo = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.mmorpg.{self.name}", "texture_width": self.tw, "texture_height": self.th},
            "bones": bones}]}
        return geo, tex, (glow if has_glow else None)

    def save(self, folder, texref_prefix="mmorpg:textures/entity/armor/"):
        """베드락 모델 · 그림 → folder, 자바 조각 JSON (렌더용) 을 돌려줌"""
        geo, tex, glow = self.build()
        os.makedirs(folder, exist_ok=True)
        gpath = os.path.join(folder, f"{self.name}.geo.json")
        with open(gpath, "w", encoding="utf-8") as f:
            json.dump(geo, f, ensure_ascii=False, indent=1)
        tex.save(os.path.join(folder, f"{self.name}.png"))
        gl = os.path.join(folder, f"{self.name}.glow.png")
        if glow is not None:
            glow.save(gl)
        elif os.path.exists(gl):
            os.remove(gl)
        m = armor_items.convert_geo(gpath)
        m["texture"] = texref_prefix + f"{self.name}.png"
        if glow is not None:
            m["emissive"] = texref_prefix + f"{self.name}.glow.png"
        return m


# ------------------------------------------------------------------ 사람 뼈 (베드락 자리)

HUMAN_BONES = {
    "head": ((0, 24, 0), None),
    "body": ((0, 24, 0), None),
    "rightArm": ((-5, 22, 0), None),
    "leftArm": ((5, 22, 0), None),
    "rightLeg": ((-1.9, 12, 0), None),
    "leftLeg": ((1.9, 12, 0), None),
}


def roots(p, names):
    for n in names:
        p.bone(n, pivot=HUMAN_BONES[n][0])


# ------------------------------------------------------------------ 칠하기 도우미

def ascii_face(rows, legend, flip=False):
    """그림 글자 (줄마다) → (x, y) 색. legend[글자] = 색 · None (투명)"""
    def get(x, y):
        if y >= len(rows):
            return None
        r = rows[y]
        if flip:
            r = r[::-1]
        if x >= len(r):
            return None
        return legend.get(r[x])
    return get


def faces_paint(table, fallback):
    """table[face] = get(x, y) (ascii_face) — 그 면은 그림대로, 없는 면은 fallback"""
    def f(face, x, y, w, h):
        g = table.get(face)
        if g is not None:
            c = g(x, y)
            return c
        return fallback(face, x, y, w, h)
    return f


def split(shader):
    """shader(face, x, y, w, h) → (색 또는 None, 빛 색 또는 None) 를 칠하기 · 빛 함수 둘로"""
    def paint(face, x, y, w, h):
        return shader(face, x, y, w, h)[0]

    def glow(face, x, y, w, h):
        return shader(face, x, y, w, h)[1]
    return paint, glow


def art(rows_by_face, legend, fallback=None):
    """rows_by_face[face] = 글자 줄들 (위에서부터), legend[글자] = (색 또는 None, 빛 또는 None). 없는 면은 fallback (shader)"""
    def s(face, x, y, w, h):
        rows = rows_by_face.get(face)
        if rows is None:
            return fallback(face, x, y, w, h) if fallback else (None, None)
        if y >= len(rows) or x >= len(rows[y]):
            return (None, None)
        e = legend[rows[y][x]]
        return e
    return s


def flip_rows(rows):
    return [r[::-1] for r in rows]


def plate(pal, seed=0, bevel=True, sides=0.88, top=1.12, bottom=0.62, rough=0.07):
    """판금: pal = (가장 어두움, 어두움, 가운데, 밝음, 가장자리 빛)"""
    def s(face, x, y, w, h):
        c = pal[2]
        if bevel and w > 2 and h > 2:
            if y == 0:
                c = pal[3]
            elif y == h - 1:
                c = pal[1]
            elif x == 0:
                c = mix(pal[2], pal[3], 0.45)
            elif x == w - 1:
                c = mix(pal[2], pal[1], 0.6)
        k = {"top": top, "bottom": bottom}.get(face, sides if face in ("west", "east") else 1.0)
        k *= 1 - rough + 2 * rough * noise(x, y, seed + FACES.index(face))
        return (shade(c, k), None)
    return s


def with_glow(base, glowset):
    """glowset(face, x, y, w, h) → (색, 빛) 또는 None — 있으면 그 칸을 덮음"""
    def s(face, x, y, w, h):
        g = glowset(face, x, y, w, h)
        if g is not None:
            return g
        return base(face, x, y, w, h)
    return s


def mirror_x(origin, size):
    """오른쪽 (-x) 상자 → 왼쪽 상자 origin"""
    return [-(origin[0] + size[0]), origin[1], origin[2]]
