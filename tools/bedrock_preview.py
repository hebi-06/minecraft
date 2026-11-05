#!/usr/bin/env python3
"""베드락 엔티티 모델 확인용 그림 (애드온 미리보기 없이 — numpy · PIL): .geo.json + 텍스처 + 동작 (자세 하나) → 그림.

  python3 tools/bedrock_preview.py <geo.json> <텍스처.png> <동작.json> <나갈.png> [--anims idle,pop --t 0.4 --scale 1]

게임 (자바 BedrockRenderer — BedrockLooks) 과 같은 규칙: 뼈 자리는 자바 좌표 (y 아래쪽 — 베드락 (x, 24 − y, z)), 회전은 같은 수 (도) 를 ZYX 로
(ModelPart.translateAndRotate), 동작의 위치는 y 를 뒤집어 더함 · 회전은 더함 · 크기는 곱함, 상자 UV 는 베드락 상자 펼침 (east = −x 쪽 면 · west = +x 쪽 면 ·
north = 앞 −z). 몰랑 식은 q.life_time · q.anim_time · q.modified_* 만 (math.sin/cos 는 도) — 나머지는 0.
세 방향 (앞 3/4 · 옆 · 뒤 3/4) 을 한 장에. 텍셀마다 작은 사각형을 깊이 차례로 그림 (뒷면은 거름 · 면 방향 밝기).
"""
import json
import math
import re
import sys

import numpy as np
from PIL import Image, ImageDraw

FACES = ("up", "down", "east", "north", "west", "south")


def molang(expr, env):
    if isinstance(expr, (int, float)):
        return float(expr)
    s = str(expr)
    s = s.replace("math.sin", "_sin").replace("math.cos", "_cos").replace("math.abs", "abs").replace("math.min", "min").replace("math.max", "max")
    s = s.replace("math.clamp", "_clamp").replace("math.pi", str(math.pi))
    s = re.sub(r"(q|query|v|variable)\.([a-z_]+)", lambda m: repr(env.get(m.group(2), 0.0)), s)
    try:
        return float(eval(s, {"_sin": lambda d: math.sin(math.radians(d)), "_cos": lambda d: math.cos(math.radians(d)), "abs": abs, "min": min, "max": max,
                              "_clamp": lambda x, a, b: max(a, min(b, x))}))
    except Exception:
        return 0.0


def sample(track, t, env):
    """키프레임 (시각 → [x, y, z]) 이나 식 [x, y, z] → [x, y, z] (선형)"""
    if isinstance(track, list):
        return [molang(v, env) for v in track]
    if isinstance(track, dict):
        keys = sorted((float(k), v) for k, v in track.items())
        if t <= keys[0][0]:
            v = keys[0][1]
        elif t >= keys[-1][0]:
            v = keys[-1][1]
        else:
            for (t0, a), (t1, b) in zip(keys, keys[1:]):
                if t0 <= t <= t1:
                    a = a.get("post", a) if isinstance(a, dict) else a
                    b = b.get("pre", b) if isinstance(b, dict) else b
                    u = (t - t0) / max(1e-9, t1 - t0)
                    return [molang(x, env) * (1 - u) + molang(y, env) * u for x, y in zip(a, b)]
        if isinstance(v, dict):
            v = v.get("post", v.get("pre"))
        return [molang(x, env) for x in v]
    return [molang(track, env)] * 3


def pose(anims, names, t, env):
    rot, pos, scl = {}, {}, {}
    for n in names:
        a = anims.get(n)
        if a is None:
            continue
        length = a.get("animation_length")
        tt = t if n != "idle" and n != "walk" else env.get("life_time", 0)
        if a.get("loop") is True and length:
            tt = tt % length
        elif length:
            tt = min(tt, length)
        e = dict(env, anim_time=tt)
        for bone, tr in a.get("bones", {}).items():
            if "rotation" in tr:
                r = sample(tr["rotation"], tt, e)
                rot[bone] = [a + b for a, b in zip(rot.get(bone, [0, 0, 0]), r)]
            if "position" in tr:
                p = sample(tr["position"], tt, e)
                pos[bone] = [a + b for a, b in zip(pos.get(bone, [0, 0, 0]), p)]
            if "scale" in tr:
                s = sample(tr["scale"], tt, e)
                scl[bone] = [a * b for a, b in zip(scl.get(bone, [1, 1, 1]), s)]
    return rot, pos, scl


def rot_zyx(x, y, z):
    cx, sx = math.cos(math.radians(x)), math.sin(math.radians(x))
    cy, sy = math.cos(math.radians(y)), math.sin(math.radians(y))
    cz, sz = math.cos(math.radians(z)), math.sin(math.radians(z))
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def face_rects(uv, size):
    u, v = uv
    w, h, d = size
    w, h, d = math.floor(w), math.floor(h), math.floor(d)
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h), "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h),
            "south": (u + 2 * d + w, v + d, w, h)}


def quads(geo, tex, rot, pos, scl, scale=1.0):
    """세상 좌표 (블록 — y 위) 의 텍셀 사각형들: [(네 꼭짓점 (4×3), 색 (r, g, b, a), 법선)]"""
    g = geo["minecraft:geometry"][0]
    bones = {b["name"]: b for b in g["bones"]}
    tw = g["description"]["texture_width"]
    th = g["description"]["texture_height"]
    px = tex.load()
    sx, sy = tex.size[0] / tw, tex.size[1] / th
    mats = {}

    def world(name):
        if name in mats:
            return mats[name]
        b = bones[name]
        par = b.get("parent")
        piv = b["pivot"]
        if par:
            pp = bones[par]["pivot"]
            off = np.array([piv[0] - pp[0], -(piv[1] - pp[1]), piv[2] - pp[2]], float)
        else:
            off = np.array([piv[0], 24 - piv[1], piv[2]], float)
        ap = pos.get(name, [0, 0, 0])
        off = off + np.array([ap[0], -ap[1], ap[2]])
        r0 = b.get("rotation", [0, 0, 0])
        ar = rot.get(name, [0, 0, 0])
        R = rot_zyx(r0[0] + ar[0], r0[1] + ar[1], r0[2] + ar[2])
        S = np.diag(scl.get(name, [1, 1, 1]))
        M = np.eye(4)
        M[:3, :3] = R @ S
        M[:3, 3] = off
        if par:
            M = world(par) @ M
        mats[name] = M
        return M

    out = []
    for name, b in bones.items():
        M = world(name)
        piv = b["pivot"]
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            inf = c.get("inflate", 0)
            # 자바 좌표 (뼈 중심에서 — y 아래)
            x0, x1 = o[0] - piv[0] - inf, o[0] + s[0] - piv[0] + inf
            y0, y1 = piv[1] - o[1] - s[1] - inf, piv[1] - o[1] + inf  # y0 = 위 (자바 y 작을수록 위)
            z0, z1 = o[2] - piv[2] - inf, o[2] + s[2] - piv[2] + inf
            rects = face_rects(c["uv"], s)

            def P(x, y, z):
                v = M @ np.array([x, y, z, 1.0])
                # 자바 모델 (y 아래 · 16 단위) → 세상 (몸이 +Z 를 봄): X = x/16 · Y = 1.501 − y/16 · Z = −z/16
                return np.array([v[0] / 16 * scale, (1.501 - v[1] / 16) * scale, -v[2] / 16 * scale])

            # 면: (텍스처 사각형, 왼쪽 위 · 오른쪽 위 · 왼쪽 아래 꼭짓점) — 바깥에서 볼 때
            corners = {
                "north": (P(x0, y0, z0), P(x1, y0, z0), P(x0, y1, z0)),
                "south": (P(x1, y0, z1), P(x0, y0, z1), P(x1, y1, z1)),
                "east": (P(x0, y0, z1), P(x0, y0, z0), P(x0, y1, z1)),
                "west": (P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z0)),
                "up": (P(x0, y0, z1), P(x1, y0, z1), P(x0, y0, z0)),
                "down": (P(x0, y1, z0), P(x1, y1, z0), P(x0, y1, z1)),
            }
            for f in FACES:
                u, v, w, h = rects[f]
                if w <= 0 or h <= 0:
                    continue
                a, bb, cc = corners[f]
                du = (bb - a) / w
                dv = (cc - a) / h
                n = np.cross(dv, du)  # 바깥쪽 (u 오른쪽 × v 아래 는 안쪽)
                ln = np.linalg.norm(n)
                if ln < 1e-12:
                    continue
                n = n / ln
                for j in range(h):
                    for i in range(w):
                        col = px[int((u + i) * sx), int((v + j) * sy)]
                        if col[3] == 0:
                            continue
                        p0 = a + du * i + dv * j
                        out.append((np.array([p0, p0 + du, p0 + du + dv, p0 + dv]), col, n))
    return out


def render(qs, eye, target, W=360, H=360, fov=40, bg=(150, 186, 232)):
    eye = np.array(eye, float)
    target = np.array(target, float)
    fwd = target - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = (H / 2) / math.tan(math.radians(fov / 2))
    im = Image.new("RGB", (W, H), bg)
    d = ImageDraw.Draw(im)
    light = np.array([0.35, 0.85, -0.4])
    light /= np.linalg.norm(light)
    items = []
    for pts, col, n in qs:
        c = pts.mean(0)
        if np.dot(n, eye - c) <= 0:
            continue
        rel = pts - eye
        z = rel @ fwd
        if (z <= 0.05).any():
            continue
        xs = W / 2 + (rel @ right) / z * f
        ys = H / 2 - (rel @ up) / z * f
        k = 0.62 + 0.38 * max(0.0, float(np.dot(n, light)))
        glow = col[3] < 255
        cc = tuple(int(min(255, v * (1.0 if glow else k))) for v in col[:3])
        items.append((float(np.linalg.norm(c - eye)), list(zip(xs, ys)), cc))
    items.sort(key=lambda t: -t[0])
    for _, poly, cc in items:
        d.polygon(poly, fill=cc)
    return im


def sheet(geo, tex, anims, names, t, out, scale=1.0, label=""):
    env = {"life_time": 0.4, "anim_time": t, "modified_distance_moved": 0.0, "modified_move_speed": 0.0}
    rot, pos, scl = pose(anims, names, t, env)
    qs = quads(geo, tex, rot, pos, scl, scale)
    allp = np.array([p for q in qs for p in q[0]])
    lo, hi = allp.min(0), allp.max(0)
    c = (lo + hi) / 2
    ext = float(max(hi - lo)) * 1.35 + 0.4
    views = (("앞", (0.75, 0.42, 1.0)), ("옆", (1.0, 0.22, 0.0)), ("뒤", (-0.7, 0.5, -1.0)))
    ims = []
    for name, dv in views:
        dv = np.array(dv, float)
        dv /= np.linalg.norm(dv)
        im = render(qs, c + dv * ext * 1.55, c)
        ImageDraw.Draw(im).text((6, 4), f"{label} {name} · {'+'.join(names)} {t:.2f}s", fill=(20, 20, 30))
        ims.append(im)
    W = sum(i.size[0] for i in ims)
    sh = Image.new("RGB", (W, ims[0].size[1]))
    x = 0
    for i in ims:
        sh.paste(i, (x, 0))
        x += i.size[0]
    sh.save(out)
    return out


def main():
    a = sys.argv[1:]
    geo = json.load(open(a[0], encoding="utf-8"))
    tex = Image.open(a[1]).convert("RGBA")
    adata = json.load(open(a[2], encoding="utf-8"))["animations"]
    anims = {k.rsplit(".", 1)[1]: v for k, v in adata.items()}
    out = a[3]
    names = a[a.index("--anims") + 1].split(",") if "--anims" in a else ["idle"]
    t = float(a[a.index("--t") + 1]) if "--t" in a else 0.0
    scale = float(a[a.index("--scale") + 1]) if "--scale" in a else 1.0
    print(sheet(geo, tex, anims, names, t, out, scale))


if __name__ == "__main__":
    main()
