#!/usr/bin/env python3
"""2026-11-16 위르드식 3D 소품 (사용자 "일반 바닐라 아이템은 쓰지 말고 만드는게 더 좋을듯 바닐라 아이템 스킨은 금지임 이제 위르드식으로 만들어보자").

위르드 화면 (도트 2D + 바닐라 파티클 + 3D) 의 3D 겹: 바닐라 아이템 그림 대신 직접 만든 복셀 소품 — 1 모델 픽셀 = 1 텍셀 (블록 · 몹과 같은 16 텍셀/칸),
납작한 도트 칠 (회색 견본 · 몇 단 명암) 을 서버가 원소 색으로 물들임 (아이템 정의 tints — custom_model_data 색: 0 = 몸, 1 = 밝은 심 · 테).
아이템 표시 (vfx.Vfx.Prop) 로 띄움 → 모드 · 모드 없는 클라이언트 같은 모습. 그림 메모리는 아이템 아틀라스의 작은 견본뿐 (글꼴 그림판이 아님).

  python3 tools/wyrd_props.py                      # 씀 (assets/rpg/models/item/vfx/wy_*.json · textures/item/vfx/wy_*.png · items/vfx/wy_*.json)
  python3 tools/wyrd_props.py --check              # 쓸 것이 없는지
  python3 tools/wyrd_props.py --preview <폴더> [이름…]

소품
- wy_star  다섯 갈래 별 (가운데가 두꺼운 빗면 · 밝은 심 · 짙은 테)
- wy_shard 결정 조각 (길쭉한 팔면체 — 면마다 밝기가 다른 깎은 결)
- wy_cube  에너지 상자 (반투명 면 + 빛나는 모서리 테)
- wy_panel 테 두른 판 (짙은 판 · 밝은 테 · 가운데 룬)
- wy_orb   빛 덩이 (위가 밝은 띠 명암 · 흰 반짝임)
"""
import argparse
import io
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vfx_props as P  # noqa: E402
from PIL import Image  # noqa: E402

SW = P.SW


def sw_px(v, var=0.05, alpha=1.0, seed=1, edge=None):
    """도트 견본: 회색 v ± var (텍셀마다 — 부드러운 잡음 없이 또렷한 점), edge = 가장자리 한 줄 밝기 (테)"""
    def gen(frame, rnd):
        im = Image.new("RGBA", (SW, SW))
        px = im.load()
        for y in range(SW):
            for x in range(SW):
                n = P.hash3(x, y, 0, seed) - 0.5
                c = v + n * var * 2
                if edge is not None and min(x, y, SW - 1 - x, SW - 1 - y) == 0:
                    c = edge
                px[x, y] = P.rgba((c, c, c), alpha)
        return im
    return gen


def star_poly(R, r, n=5, rot=math.pi / 2):
    pts = []
    for i in range(2 * n):
        a = rot + i * math.pi / n
        rr = R if i % 2 == 0 else r
        pts.append((math.cos(a) * rr, math.sin(a) * rr))
    return pts


def poly_sdf(x, y, pts):
    """다각형까지의 거리 (안 −)"""
    d = float("inf")
    inside = False
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % n]
        ex, ey = bx - ax, by - ay
        wx, wy = x - ax, y - ay
        t = max(0.0, min(1.0, (wx * ex + wy * ey) / (ex * ex + ey * ey)))
        dx, dy = wx - ex * t, wy - ey * t
        d = min(d, math.hypot(dx, dy))
        if (ay > y) != (by > y) and x < ax + (y - ay) * (bx - ax) / (by - ay):
            inside = not inside
    return -d if inside else d


# ------------------------------------------------------------------ 별

def wy_star():
    m = P.Model("wy_star", "위르드식 별 (2026-11-16 — 바닐라 아이템 대신 직접 만든 소품 · 몸 = 물들임 0, 심 = 물들임 1)", tex=64)
    m.mat("rim", P.Mat(sw_px(0.66, 0.04, seed=11), tint=0, glow=True))
    m.mat("body", P.Mat(sw_px(0.93, 0.04, seed=12), tint=0, glow=True))
    m.mat("core", P.Mat(sw_px(1.0, 0.03, seed=14), tint=1, glow=True))
    R, r = 7.6, 3.1
    pts = star_poly(R, r)

    def solid(x, y, z):
        px, py = x - 8, y - 8
        if poly_sdf(px, py, pts) > 0:
            return False
        rr = math.hypot(px, py)
        half = 0.6 + 1.0 * max(0.0, 1 - rr / R)  # 갈래 끝 2 텍셀 · 가운데 4 텍셀 두께
        return abs(z - 8) <= half

    def matof(x, y, z):
        px, py = x - 8, y - 8
        if math.hypot(px, py) < 2.1:
            return "core"
        if poly_sdf(px, py, pts) > -0.9:
            return "rim"
        return "body"
    P.carve(m, (0, 0, 4), (16, 16, 12), 1.0, solid, matof)
    return m


# ------------------------------------------------------------------ 결정 조각

def wy_shard():
    m = P.Model("wy_shard", "위르드식 결정 조각 (2026-11-16 — 길쭉한 팔면체 · 면마다 밝기 · 끝은 밝은 심)", tex=64)
    m.mat("lit", P.Mat(sw_px(1.0, 0.04, seed=21), tint=0, glow=True))
    m.mat("mid", P.Mat(sw_px(0.78, 0.04, seed=22), tint=0, glow=True))
    m.mat("dark", P.Mat(sw_px(0.56, 0.04, seed=23), tint=0, glow=True))
    m.mat("tip", P.Mat(sw_px(1.0, 0.02, seed=24), tint=1, glow=True))
    a, b, yc = 3.6, 10.0, 8.0

    def solid(x, y, z):
        return abs(x - 8) / a + abs(z - 8) / a + abs(y - yc) / b <= 1.0

    def matof(x, y, z):
        if abs(y - yc) > b * 0.72:
            return "tip"
        sx, sz = x > 8, z > 8
        if sx and not sz:
            return "lit"
        if sx == sz:
            return "mid"
        return "dark"
    P.carve(m, (4, -2, 4), (12, 18, 12), 1.0, solid, matof)
    return m


# ------------------------------------------------------------------ 에너지 상자

def wy_cube():
    m = P.Model("wy_cube", "위르드식 에너지 상자 (2026-11-16 — 반투명 면 · 빛나는 모서리 테)", tex=32)
    m.mat("glass", P.Mat(sw_px(0.95, 0.03, alpha=0.38, seed=31, edge=1.0), tint=0, glow=True))
    m.mat("frame", P.Mat(sw_px(1.0, 0.02, seed=32), tint=1, glow=True))
    lo, hi, t = 2.0, 14.0, 1.0
    m.box("glass", (lo + 0.1, lo + 0.1, lo + 0.1), (hi - 0.1, hi - 0.1, hi - 0.1))
    for (ax, ay) in ((lo, lo), (lo, hi - t), (hi - t, lo), (hi - t, hi - t)):
        m.box("frame", (ax, ay, lo), (ax + t, ay + t, hi))          # z 모서리
        m.box("frame", (ax, lo, ay), (ax + t, hi, ay + t))          # y 모서리
        m.box("frame", (lo, ax, ay), (hi, ax + t, ay + t))          # x 모서리
    return m


# ------------------------------------------------------------------ 테 두른 판

def wy_panel():
    m = P.Model("wy_panel", "위르드식 테 두른 판 (2026-11-16 — 짙은 판 · 밝은 테 · 가운데 룬)", tex=32)
    m.mat("plate", P.Mat(sw_px(0.32, 0.05, seed=41), tint=0, glow=True))
    m.mat("frame", P.Mat(sw_px(1.0, 0.02, seed=42), tint=1, glow=True))
    m.box("plate", (1.5, 1.5, 7.6), (14.5, 14.5, 8.4))
    w = 1.2
    m.box("frame", (1.0, 1.0, 7.3), (15.0, 1.0 + w, 8.7))
    m.box("frame", (1.0, 15.0 - w, 7.3), (15.0, 15.0, 8.7))
    m.box("frame", (1.0, 1.0 + w, 7.3), (1.0 + w, 15.0 - w, 8.7))
    m.box("frame", (15.0 - w, 1.0 + w, 7.3), (15.0, 15.0 - w, 8.7))
    # 가운데 룬 (마름모 + 세로 획 — 앞뒤로 조금 나옴)
    for (x0, y0, x1, y1) in ((7.4, 4.0, 8.6, 12.0), (5.0, 7.4, 11.0, 8.6), (6.0, 5.6, 7.2, 6.8), (8.8, 5.6, 10.0, 6.8), (6.0, 9.2, 7.2, 10.4),
                             (8.8, 9.2, 10.0, 10.4)):
        m.box("frame", (x0, y0, 7.45), (x1, y1, 8.55))
    return m


# ------------------------------------------------------------------ 빛 덩이

def wy_orb():
    m = P.Model("wy_orb", "위르드식 빛 덩이 (2026-11-16 — 위가 밝은 띠 명암 · 흰 반짝임)", tex=64)
    m.mat("lit", P.Mat(sw_px(1.0, 0.03, seed=51), tint=0, glow=True))
    m.mat("body", P.Mat(sw_px(0.8, 0.04, seed=52), tint=0, glow=True))
    m.mat("shade", P.Mat(sw_px(0.58, 0.04, seed=53), tint=0, glow=True))
    m.mat("spark", P.Mat(sw_px(1.0, 0.0, seed=54), tint=1, glow=True))
    R = 5.6

    def solid(x, y, z):
        return math.sqrt((x - 8) ** 2 + (y - 8) ** 2 + (z - 8) ** 2) <= R

    def matof(x, y, z):
        if y > 10.2 and x < 8 and z < 8:
            return "spark"
        if y > 9.4:
            return "lit"
        if y > 6.4:
            return "body"
        return "shade"
    P.carve(m, (2, 2, 2), (14, 14, 14), 1.0, solid, matof)
    return m


BUILD = {"wy_star": wy_star, "wy_shard": wy_shard, "wy_cube": wy_cube, "wy_panel": wy_panel, "wy_orb": wy_orb}


def outputs(names=None):
    files = {}
    built = {}
    for name, fn in BUILD.items():
        if names and name not in names:
            continue
        m = fn()
        tex, animated = m.texture()
        buf = io.BytesIO()
        tex.save(buf, "PNG", optimize=True)
        js = m.json()
        js["credit"] = js["credit"].replace("tools/vfx_props.py", "tools/wyrd_props.py")
        for e in js["elements"]:
            for v in e["from"] + e["to"]:
                assert -16 <= v <= 32, (name, e["name"], v)
        files[os.path.join(P.RES, "textures", "item", "vfx", name + ".png")] = buf.getvalue()
        files[os.path.join(P.RES, "models", "item", "vfx", name + ".json")] = (json.dumps(js, ensure_ascii=False, indent=1) + "\n").encode()
        files[os.path.join(P.RES, "items", "vfx", name + ".json")] = (json.dumps(m.item_json(), indent=2) + "\n").encode()
        built[name] = (m, js, tex)
    return files, built


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--preview")
    ap.add_argument("names", nargs="*")
    a = ap.parse_args()
    files, built = outputs(a.names or None)
    if a.preview:
        tints = {"wy_star": [(1.0, 0.82, 0.35), (1.0, 0.97, 0.88)], "wy_shard": [(0.42, 0.3, 1.0), (0.84, 0.88, 1.0)],
                 "wy_cube": [(0.62, 0.7, 1.0), (1.0, 1.0, 1.0)], "wy_panel": [(0.25, 0.34, 0.85), (0.62, 0.78, 1.0)], "wy_orb": [(0.25, 0.34, 0.85), (1, 1, 1)]}
        for p, n in P.preview(a.preview, built, tints):
            print(p, n, "elements")
        return
    stale = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            stale.append(os.path.relpath(path, P.ROOT))
            if not a.check:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    if a.check:
        if stale:
            print("wyrd_props: 다시 만들어야 함:", ", ".join(stale))
            sys.exit(1)
        print("wyrd_props: ok (%d)" % len(built))
    else:
        for name, (m, js, tex) in built.items():
            print("wyrd_props: %-9s %4d elements · tints %d" % (name, len(js["elements"]), m.tints))
        print("wyrd_props: 씀", len(stale))


if __name__ == "__main__":
    main()
