"""사람 모델 조각 (갑옷 · 장식 — assets/mmorpg/armor · cosmetic 의 JSON) 을 그리기 — z 버퍼 (numpy).

cosmetic_art.py (장식 아이콘 · 견본 확인 그림) 가 씀. 게임 (ModelPart) 과 같은 규칙:
- 뼈: 자리 (pivot, 픽셀) 로 옮기고 회전 (rotationZYX — x 먼저, 다음 y, 다음 z), 아래 뼈는 부모 기준
- 상자: from · size (픽셀), 부풀림 (inflate) 은 모양만 키우고 UV 는 원래 크기, 거울 (mirror) 은 x 양끝을 바꿈
- 면 UV (texOffs u, v · 크기 dx dy dz): 아래 (u+dz, v)~(u+dz+dx, v+dz) · 위 (u+dz+dx, v+dz)~(u+dz+2dx, v) · 서 (-x) (u, v+dz)~(u+dz, v+dz+dy) ·
  북 (-z, 앞) (u+dz, v+dz)~(u+dz+dx, v+dz+dy) · 동 (+x) (u+dz+dx, v+dz)~(u+2dz+dx, …) · 남 (+z, 뒤) (u+2dz+dx, v+dz)~(u+2dz+2dx, …)
- 모델 좌표: y 아래 · 앞 = -z · 사람의 오른쪽 = -x. 그림 좌표: 앞에서 보면 오른쪽 = 모델 +x, 위 = 모델 -y
뒷면도 그림 (갑옷 렌더 타입은 뒷면을 자르지 않음). 반투명 픽셀은 알파 0.1 미만만 버림 (cutout).
"""
import math

import numpy as np
from PIL import Image

# 사람 모델 뼈 자리 (자바 PlayerModel — 넓은 팔)
HUMAN = {
    "head": ((0.0, 0.0, 0.0), [(-4, -8, -4, 8, 8, 8, 0, 0)]),
    "body": ((0.0, 0.0, 0.0), [(-4, 0, -2, 8, 12, 4, 16, 16)]),
    "right_arm": ((-5.0, 2.0, 0.0), [(-3, -2, -2, 4, 12, 4, 40, 16)]),
    "left_arm": ((5.0, 2.0, 0.0), [(-1, -2, -2, 4, 12, 4, 32, 48)]),
    "right_leg": ((-1.9, 12.0, 0.0), [(-2, 0, -2, 4, 12, 4, 0, 16)]),
    "left_leg": ((1.9, 12.0, 0.0), [(-2, 0, -2, 4, 12, 4, 16, 48)]),
}


def rot_zyx(rx, ry, rz):
    """라디안 (x, y, z) → 3×3 (Rz · Ry · Rx — 자바 Quaternionf.rotationZYX)"""
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def cube_quads(fr, size, uv, inflate, mirror):
    """상자 하나 → [(꼭짓점 4 (모델 픽셀), uv 4 (텍스처 픽셀), 면 이름)]"""
    x0, y0, z0 = fr
    dx, dy, dz = size
    g = inflate
    minx, miny, minz = x0 - g, y0 - g, z0 - g
    maxx, maxy, maxz = x0 + dx + g, y0 + dy + g, z0 + dz + g
    if mirror:
        minx, maxx = maxx, minx
    v = [
        (minx, miny, minz), (maxx, miny, minz), (maxx, maxy, minz), (minx, maxy, minz),
        (minx, miny, maxz), (maxx, miny, maxz), (maxx, maxy, maxz), (minx, maxy, maxz),
    ]
    u, w = uv
    # 자바는 UV 에 정수 크기를 씀 (floor 아님 — 상자 크기 그대로, 소수면 소수)
    f4, f5, f6, f7 = u, u + dz, u + dz + dx, u + dz + dx + dx
    f8, f9 = u + dz + dx + dz, u + dz + dx + dz + dx
    f10, f11, f12 = w, w + dz, w + dz + dy
    faces = [
        ("down", (5, 4, 0, 1), (f5, f10, f6, f11)),
        ("up", (2, 3, 7, 6), (f6, f11, f7, f10)),
        ("west", (0, 4, 7, 3), (f4, f11, f5, f12)),
        ("north", (1, 0, 3, 2), (f5, f11, f6, f12)),
        ("east", (5, 1, 2, 6), (f6, f11, f8, f12)),
        ("south", (4, 5, 6, 7), (f8, f11, f9, f12)),
    ]
    out = []
    for name, idx, (ua, va, ub, vb) in faces:
        quad = [v[i] for i in idx]
        uvs = [(ub, va), (ua, va), (ua, vb), (ub, vb)]
        out.append((np.array(quad, float), np.array(uvs, float), name))
    return out


class Mesh:
    """사각형들 (세계 = 모델 픽셀 좌표) + 텍스처"""

    def __init__(self):
        self.quads = []  # (4×3 꼭짓점, 4×2 uv, 텍스처 번호, 빛남)
        self.textures = []

    def add_texture(self, img):
        self.textures.append(np.asarray(img.convert("RGBA"), dtype=np.float64))
        return len(self.textures) - 1

    def add_part(self, part, tex, M=None, t=None, glow=False, anim=None, name=""):
        """조각 JSON 의 뼈 하나 (아래 뼈 포함). M · t = 부모 변환 (3×3 · 이동). anim(name) → 덧붙일 회전 (라디안 x, y, z) · 이동 (픽셀)"""
        M = np.eye(3) if M is None else M
        t = np.zeros(3) if t is None else t
        p = np.array(part["pivot"], float)
        r = [math.radians(a) for a in part["rot"]]
        extra_t = np.zeros(3)
        if anim is not None:
            add = anim(name)
            if add:
                ar, at = add
                r = [r[0] + ar[0], r[1] + ar[1], r[2] + ar[2]]
                extra_t = np.array(at, float)
        R = rot_zyx(*r)
        M2 = M @ R
        t2 = t + M @ (p + extra_t)
        for c in part["cubes"]:
            for quad, uvs, _ in cube_quads(c["from"], c["size"], c["uv"], c.get("inflate", 0), c.get("mirror", False)):
                world = (M2 @ quad.T).T + t2
                self.quads.append((world, uvs, tex, glow))
        for child, cp in part.get("children", {}).items():
            self.add_part(cp, tex, M2, t2, glow, anim, child)

    def add_model(self, model, img, root_pose=None, glow_img=None, anim=None):
        """조각 JSON 전체: 뿌리 뼈마다 사람 모델의 뼈 자리 (root_pose[name] = (자리, 회전)) 를 따름"""
        tex = self.add_texture(img)
        for name, part in model["parts"].items():
            pose = (root_pose or {}).get(name)
            M, t = np.eye(3), np.zeros(3)
            if pose is not None:
                pos, rot = pose
                part = dict(part, pivot=list(pos), rot=[math.degrees(a) for a in rot])
            self.add_part(part, tex, M, t, False, anim, name)
            if glow_img is not None:
                g = self.add_texture(glow_img)
                self.add_part(part, g, M, t, True, anim, name)

    def add_human(self, skin_img, pose=None):
        tex = self.add_texture(skin_img)
        for name, (pivot, cubes) in HUMAN.items():
            pp = (pose or {}).get(name)
            part = {"pivot": list(pp[0]) if pp else list(pivot), "rot": [math.degrees(a) for a in pp[1]] if pp else [0, 0, 0],
                    "cubes": [{"from": c[0:3], "size": c[3:6], "uv": c[6:8]} for c in cubes], "children": {}}
            self.add_part(part, tex)


def view_matrix(yaw_deg, pitch_deg):
    """그림 좌표: 앞에서 (yaw 0) 보면 오른쪽 = 모델 +x · 위 = 모델 -y · 깊이 = 모델 +z (멀수록 큼).
    yaw 는 보는 사람이 사람 둘레를 도는 각 (180 = 뒤), pitch 는 위에서 내려다보는 각"""
    a = math.radians(yaw_deg)
    b = math.radians(pitch_deg)
    # 모델 → 앞에서 본 좌표 (x 오른쪽, y 위, z 앞쪽이 가까움 → 깊이 = +z)
    base = np.diag([1.0, -1.0, 1.0])
    Ry = np.array([[math.cos(a), 0, -math.sin(a)], [0, 1, 0], [math.sin(a), 0, math.cos(a)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    return Rx @ Ry @ base


SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.62, "west": 0.62}


def render(mesh, size, scale, center, yaw=0.0, pitch=0.0, ss=3, bg=(0, 0, 0, 0)):
    """mesh → RGBA 그림 (size = (가로, 세로) 점, scale = 모델 픽셀 하나가 몇 점, center = 그림 가운데에 올 모델 자리)"""
    W, H = size
    V = view_matrix(yaw, pitch)
    c = V @ np.array(center, float)
    SW, SH = W * ss, H * ss
    color = np.zeros((SH, SW, 4))
    depth = np.full((SH, SW), -np.inf)
    # 빛 방향 (그림 좌표 — 앞 왼쪽 위에서)
    L = np.array([-0.35, 0.75, 0.55])
    L /= np.linalg.norm(L)
    glow_layer = np.zeros((SH, SW, 4))
    for world, uvs, ti, glow in mesh.quads:
        P = (V @ world.T).T - c
        sx = P[:, 0] * scale * ss + SW / 2
        sy = -P[:, 1] * scale * ss + SH / 2
        z = P[:, 2]
        # 면 밝기: 법선 (그림 좌표) 과 빛
        n = np.cross(P[1] - P[0], P[3] - P[0])
        nn = np.linalg.norm(n)
        if nn < 1e-12:
            continue
        n /= nn
        shade = 0.55 + 0.45 * abs(float(n @ L))
        tex = mesh.textures[ti]
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], z[list(tri)]
            us, vs = uvs[list(tri), 0], uvs[list(tri), 1]
            x0, x1 = int(max(0, math.floor(xs.min()))), int(min(SW - 1, math.ceil(xs.max())))
            y0, y1 = int(max(0, math.floor(ys.min()))), int(min(SH - 1, math.ceil(ys.max())))
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-12:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-9) & (w1 >= -1e-9) & (w2 >= -1e-9)
            if not inside.any():
                continue
            zz = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            uu = w0 * us[0] + w1 * us[1] + w2 * us[2]
            vv = w0 * vs[0] + w1 * vs[1] + w2 * vs[2]
            px = np.clip(np.floor(uu).astype(int), 0, tw - 1)
            py = np.clip(np.floor(vv).astype(int), 0, th - 1)
            texel = tex[py, px]
            a = texel[..., 3] / 255.0
            if glow:
                ok = inside & (a > 0.02)
                if not ok.any():
                    continue
                yy, xx = np.nonzero(ok)
                gl = glow_layer[yy + y0, xx + x0]
                add = texel[ok][:, :3] * a[ok][:, None]
                gl[:, :3] = np.minimum(255, gl[:, :3] + add)
                gl[:, 3] = np.maximum(gl[:, 3], a[ok] * 255)
                glow_layer[yy + y0, xx + x0] = gl
                continue
            ok = inside & (a >= 0.1)
            sub_depth = depth[y0:y1 + 1, x0:x1 + 1]
            closer = ok & (-zz > sub_depth)  # 가까울수록 z 가 작음 (카메라는 -z 쪽) → 깊이 = -z
            if not closer.any():
                continue
            sub_depth[closer] = -zz[closer]
            col = texel[..., :3] * shade
            sub = color[y0:y1 + 1, x0:x1 + 1]
            sub[closer, :3] = col[closer]
            sub[closer, 3] = 255
    out = color.copy()
    # 빛나는 층은 알파대로 덮기 (26.3 eyes 렌더 타입 — 밝기 최대 · 반투명 섞기, 0.15.0 전에는 더하기라 아이콘이 하얗게 보였음)
    m = glow_layer[..., 3] > 0
    ga = (glow_layer[m, 3] / 255.0)[:, None]
    out[m, :3] = np.minimum(255, out[m, :3] * (1 - ga) + glow_layer[m, :3])
    out[m, 3] = np.maximum(out[m, 3], glow_layer[m, 3])
    img = Image.fromarray(out.astype(np.uint8), "RGBA")
    if bg[3]:
        base = Image.new("RGBA", img.size, bg)
        base.alpha_composite(img)
        img = base
    return img.resize((W, H), Image.LANCZOS) if ss > 1 else img


def generic_skin():
    """확인 그림용 사람 피부 (64×64 — 직접 그린 단순한 모습, 게임 피부 아님): 살색 머리 · 눈, 청록 윗옷, 남색 바지, 갈색 신"""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()

    def box(u, v, dx, dy, dz, col_side, col_top=None, col_bottom=None, col_front=None):
        regions = {
            "top": (u + dz, v, dx, dz), "bottom": (u + dz + dx, v, dx, dz),
            "west": (u, v + dz, dz, dy), "front": (u + dz, v + dz, dx, dy), "east": (u + dz + dx, v + dz, dz, dy), "back": (u + 2 * dz + dx, v + dz, dx, dy),
        }
        for k, (x, y, w, h) in regions.items():
            col = {"top": col_top, "bottom": col_bottom, "front": col_front}.get(k) or col_side
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    shade = 1.0 - 0.04 * ((xx + yy) % 2)
                    px[xx, yy] = tuple(int(c * shade) for c in col) + (255,)
        return regions

    skin = (222, 176, 140)
    hair = (92, 62, 40)
    r = box(0, 0, 8, 8, 8, skin, col_top=hair)
    # 머리카락 (위쪽 두 줄) · 눈 · 입
    for (x, y, w, h) in (r["west"], r["front"], r["east"], r["back"]):
        for xx in range(x, x + w):
            for yy in range(y, y + 2):
                px[xx, yy] = hair + (255,)
    fx, fy = r["front"][0], r["front"][1]
    for xx, yy, c in ((1, 4, (255, 255, 255)), (2, 4, (60, 70, 160)), (5, 4, (60, 70, 160)), (6, 4, (255, 255, 255)), (3, 6, (150, 90, 80)), (4, 6, (150, 90, 80))):
        px[fx + xx, fy + yy] = c + (255,)
    bx, by, bw, _ = r["back"]
    for xx in range(bx, bx + bw):
        for yy in range(by, by + 5):
            px[xx, yy] = hair + (255,)
    shirt = (54, 160, 160)
    pants = (52, 60, 140)
    shoe = (90, 64, 44)
    box(16, 16, 8, 12, 4, shirt)
    for u, v in ((40, 16), (32, 48)):
        rr = box(u, v, 4, 12, 4, shirt)
        for (x, y, w, h) in (rr["west"], rr["front"], rr["east"], rr["back"]):
            for xx in range(x, x + w):
                for yy in range(y + 9, y + 12):
                    px[xx, yy] = skin + (255,)
    for u, v in ((0, 16), (16, 48)):
        rr = box(u, v, 4, 12, 4, pants)
        for (x, y, w, h) in (rr["west"], rr["front"], rr["east"], rr["back"]):
            for xx in range(x, x + w):
                for yy in range(y + 10, y + 12):
                    px[xx, yy] = shoe + (255,)
    return img
