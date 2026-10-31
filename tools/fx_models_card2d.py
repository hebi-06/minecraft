"""0.50.0 2D 물체 판 (사용자 "직업 스킬들 중에서 지금 사신 궁 제외 3d로 되어있는 애들 2d로 다시 바꾸자 대신 엄청 고퀄로 다듬어야 함"):
tools/fx_models.py 가 add(fx) 로 부름.

0.47.0 입체 효과 물체 (환영 검 · 환영 도끼 · 창 · 빛의 화살 · 비수 · 마탄 · 비전 구체 · 금서 · 책장 · 번개 대망치 · 큰 종 · 둥근 방패 · 연 방패 ·
빛의 활) 대신, v2 2D 그림 (tools/job_fx_art_v2.py — 빛 · 심지 두 겹, 흰색 = 알파) 을 판 한 장에 붙인 card_*. 입체 모델과 같은 자리 · 같은 크기:
그림의 몸 (빛 알파 > 0.5 — 흐린 빛 무리는 빼고) 의 세로 범위를 입체 모델의 세로 범위 (모델 칸) 에 맞추고 가운데 줄 (또는 도끼 자루) 을 x = 0 에 둠 →
칼끝 · 쥔 곳 · 가운데가 그대로라 JobFx3d 의 휘두름 · 둘레 돌기 · 날아감 · 따라감 · 꽂힘이 그대로 맞음. 넓은 면은 늘 보는 사람 쪽 (JobFx3d.cardOpt 가
faceCam 을 켬 — 판은 제 축 둘레로만 돌아 카메라를 봄).

여러 장 그림은 부분 f0 … 에 두고 움직임이 장을 차례로 켬 (idle = 이어 돌림 — 날을 따라 흐르는 빛 · 맥동, 그 밖 이름 = 입체 모델의 움직임 이름 그대로:
spin · flip · close · swing · draw · rest · loose). 도는 그림 (도끼 · 비수 · 방패 — rotate_xyz 한 바퀴, 원 꼬리가 그려짐) 대신 꼬리 없이 똑바로 선 한 장
(rfx_j*_card) 을 씀 (판은 Fx3d 가 돌림). 빛 (TINT — 직업 겉빛) 위에 심지 (GLOW — 속빛, 앞 · 뒤 두 장) — 참격 판 (slash_job) 과 같은 꼴.

판 이름 · 움직임은 JobFx3d.CARDS · CARD_ANIMS 와 같아야 함.
"""
import math
import os

import numpy as np
from PIL import Image, ImageFilter

fx = None  # tools/fx_models.py (add 가 채움)

# 그림의 원본 (tools/job_fx_art_v2.py 가 씀 — 판 그림은 모델 아틀라스로 구워지므로 파티클로 설치하지 않아도 됨: 칼 · 대검 · 도끼 · 비수 · 방패 판 그림)
SRC = "tools/java_fx/textures/particle/rpg/fx/"

# 판: 그림 (rpg:<png>) · 칸 (너비, 높이) · 장 수 (빛 장 = 칸 0 ~ n-1, 심지 = n ~ 2n-1) · 쓸 장 · 돌림 (도, 반시계 — 그림의 위가 모델 +Y) · 좌우 뒤집기 ·
# 입체 모델의 범위 (x0, x1, y0, y1 — tools/fx_models.py 의 모델) · 맞춤 (y = 세로 범위 · x = 가로 범위 · max = 긴 쪽, 가운데끼리) · 가운데 줄 axis (없음 =
# 몸의 가로 가운데 · handle = 아래 1/4 의 가운데 — 도끼 자루 · 수 = 그림의 그 열 (픽셀) — 활의 줌통) → x = 0 · 줄임 (그림 크기 비율) · 꼬리 지움 (몸 둘레 몇
# 픽셀만 빛을 남김) · 움직임
# {이름: (장들 (쓸 장 안의 차례), 되풀이, 길이 — 움직임 1바퀴의 몫: 서버 animSpeed 가 다른 움직임과 같을 때 이 움직임만 빠르게 (닫힘 · 놓음))}
SPECS = {
    "card_sword": dict(png="rfx_jsword2", cell=(256, 64), frames=4, use=[0, 1, 2, 3], rot=90, box=(-0.26, 0.26, -0.35, 1.32), fit="y",
                       anims={"idle": ([0, 1, 2, 3], True)}),
    "card_greatsword": dict(png="rfx_jgreatsword", cell=(512, 128), frames=4, use=[0, 1, 2, 3], rot=90, box=(-0.26, 0.26, -0.35, 1.32), fit="y",
                            anims={"idle": ([0, 1, 2, 3], True)}),
    # 도끼 · 비수 · 방패: 도는 그림 (rotate_xyz — 원 꼬리) 대신 똑바로 선 한 장 그림 (_card — 꼬리 없음, 판은 Fx3d 가 돌림)
    "card_axe": dict(png="rfx_jaxe_card", cell=(192, 192), frames=1, use=[0], rot=0, box=(-0.21, 0.41, -0.75, 0.77), fit="y", axis="handle"),
    "card_spear": dict(png="rfx_jspear2", cell=(384, 64), frames=4, use=[0, 1, 2, 3], rot=90, box=(-0.14, 0.14, -1.36, 1.47), fit="y",
                       anims={"idle": ([0, 1, 2, 3], True)}),
    "card_arrow": dict(png="rfx_jarrow2", cell=(256, 64), frames=4, use=[0, 1, 2, 3], rot=90, box=(-0.07, 0.07, -0.56, 0.64), fit="y",
                       anims={"idle": ([0, 1, 2, 3], True)}),
    "card_dagger": dict(png="rfx_jdagger_card", cell=(128, 128), frames=1, use=[0], rot=0, box=(-0.13, 0.13, -0.21, 0.50), fit="y"),
    "card_bolt": dict(png="rfx_jbolt2", cell=(192, 64), frames=4, use=[0, 1, 2, 3], rot=90, box=(-0.13, 0.13, -0.36, 0.34), fit="y",
                      anims={"idle": ([0, 1, 2, 3], True)}),
    "card_orb": dict(png="rfx_jstar", cell=(192, 192), frames=8, use=list(range(8)), rot=0, box=(-0.47, 0.47, -0.47, 0.47), fit="max",
                     anims={"idle": (list(range(8)), True), "spin": (list(range(8)), True)}),
    # 금서: 0 ~ 2 열림 · 3 ~ 8 책장 넘김 (이어 돌림) · 9 ~ 11 닫힘 — 입체 금서의 flip (넘김) · close (닫힘), 처음 (idle) 은 펼쳐 넘김
    "card_tome": dict(png="rfx_jtome", cell=(256, 256), frames=12, use=list(range(12)), rot=0, box=(-0.58, 0.58, -0.42, 0.42), fit="x", scale=0.75,
                      anims={"idle": ([3, 4, 5, 6, 7, 8], True), "open": ([0, 1, 2], False, 0.4), "flip": ([3, 4, 5, 6, 7, 8], True),
                             "close": ([9, 10, 11], False, 0.4)}),
    "card_page": dict(png="rfx_jpage2", cell=(64, 64), frames=4, use=[0, 1, 2, 3], rot=0, box=(-0.16, 0.16, -0.22, 0.22), fit="max",
                      anims={"idle": ([0, 1, 2, 3], True)}),
}

# 그림이 아직 없을 수 있는 판 (tools/job_fx_art_v2.py 가 다 그린 뒤에 SPECS 로 옮김)
LATER = {
    # 번개 대망치: 그림은 머리가 아래 → 180° (입체 모델은 머리 가운데가 0 · 자루가 -Y — dir = 아래로 쓰면 머리가 땅 쪽)
    "card_maul": dict(png="rfx_jmaul2", cell=(256, 384), frames=4, use=[0, 1, 2, 3], rot=180, box=(-0.42, 0.42, -2.2, 0.29), fit="y",
                      anims={"idle": ([0, 1, 2, 3], True)}),
    "card_bell": dict(png="rfx_jbell2", cell=(256, 256), frames=8, use=list(range(8)), rot=0, box=(-0.7, 0.7, -1.3, 0.15), fit="y",
                      anims={"idle": ([2], False), "swing": (list(range(8)), True)}),
    "card_buckler": dict(png="rfx_jbuckler_card", cell=(128, 128), frames=1, use=[0], rot=0, box=(-0.48, 0.48, -0.48, 0.48), fit="max"),
    # 연 방패: 0 ~ 2 빛 줄에서 나타남 · 3 ~ 5 버팀 (빛이 지나감) — 처음 (idle) 은 버팀 이어 돌림, appear = 나타남 한 번
    "card_aegis": dict(png="rfx_jaegis2", cell=(256, 320), frames=6, use=list(range(6)), rot=0, box=(-0.56, 0.56, -0.98, 0.78), fit="y",
                       anims={"idle": ([3, 4, 5], True), "appear": ([0, 1, 2, 3], False)}),
    # 빛의 활: 0 ~ 5 시위를 당김 · 6 ~ 7 놓음 — 입체 활의 draw · rest · loose
    "card_bow": dict(png="rfx_jbow2", cell=(256, 256), frames=8, use=list(range(8)), rot=0, box=(-0.5, 0.5, -1.15, 1.15), fit="y", axis=86,
                     anims={"idle": ([0], False), "rest": ([0], False), "draw": ([0, 1, 2, 3, 4, 5], False), "loose": ([6, 7], False, 0.3)}),
}


def add(mod):
    global fx
    fx = mod
    for name in list(SPECS) + list(LATER):
        spec = SPECS.get(name) or LATER[name]
        if not os.path.exists(os.path.join(fx.ROOT, SRC + spec["png"] + ".png")):
            continue  # 그림이 아직 없음
        fx.MODELS[name] = _fn(name, spec)


def _fn(name, spec):
    def f():
        return card(name, spec)

    f.__name__ = name
    return f


# ------------------------------------------------------------ 그림 읽기


def _cells(spec):
    """쓸 장마다 (빛 알파, 심지 알파) — 0 ~ 1 배열, 돌림 · 뒤집기 · 줄임을 한 뒤"""
    im = Image.open(os.path.join(fx.ROOT, SRC + spec["png"] + ".png")).convert("RGBA")
    cw, ch = spec["cell"]
    cols = im.size[0] // cw
    n = spec["frames"]
    out = []
    for f in spec["use"]:
        pair = []
        for c in (f, n + f):
            box = ((c % cols) * cw, (c // cols) * ch, (c % cols) * cw + cw, (c // cols) * ch + ch)
            a = im.crop(box).getchannel("A")
            if spec.get("mirror"):
                a = a.transpose(Image.FLIP_LEFT_RIGHT)
            if spec.get("rot"):
                a = a.rotate(spec["rot"], resample=Image.BICUBIC, expand=True)
            sc = spec.get("scale", 1.0)
            if sc != 1.0:
                a = a.resize((int(round(a.size[0] * sc)), int(round(a.size[1] * sc))), Image.LANCZOS)
            pair.append(np.asarray(a, dtype=float) / 255.0)
        out.append(pair)
    return out


def _trim(glow, core, px):
    """돌며 그린 그림의 원 꼬리 (도는 잔상) 를 지움: 몸 (빛 > 0.5 · 심지 > 0.1) 둘레 px 픽셀 안의 빛만 남김 (부드럽게)"""
    body = ((glow > 0.5) | (core > 0.1)).astype(np.uint8) * 255
    m = Image.fromarray(body, "L").filter(ImageFilter.MaxFilter(2 * (px // 2) + 1)).filter(ImageFilter.GaussianBlur(px * 0.6))
    mask = np.clip(np.asarray(m, dtype=float) / 255.0 * 1.6, 0, 1)
    return glow * mask, core * mask


# ------------------------------------------------------------ 판


def _alloc(atlas, name, w, h):
    """아틀라스에서 w × h 칸 자리를 잡음 (fx.Atlas.swatch 와 같은 차례 — 그림은 바로 붙임): 정규 UV · 왼쪽 위 픽셀"""
    per = atlas.size // atlas.cell
    for row in range(per - h + 1):
        for col in range(per - w + 1):
            if all(not atlas.used[row + j][col + i] for j in range(h) for i in range(w)):
                for j in range(h):
                    for i in range(w):
                        atlas.used[row + j][col + i] = True
                x0, y0 = col * atlas.cell, row * atlas.cell
                s = float(atlas.size)
                uv = ((x0 + 0.5) / s, (y0 + 0.5) / s, (x0 + w * atlas.cell - 0.5) / s, (y0 + h * atlas.cell - 0.5) / s)
                atlas.named[name] = uv
                return uv, (x0, y0)
    raise RuntimeError("atlas full (%s)" % name)


def _paste(atlas, name, alpha):
    """알파 그림 (흰색) 을 칸 크기로 늘려 (가장자리는 비움) 붙임: UV · 칸 크기 (픽셀)"""
    h, w = alpha.shape
    cw = int(math.ceil(w / atlas.cell))
    chh = int(math.ceil(h / atlas.cell))
    uv, (x0, y0) = _alloc(atlas, name, cw, chh)
    pw, ph = cw * atlas.cell, chh * atlas.cell
    full = np.zeros((ph, pw), dtype=float)
    ox, oy = (pw - w) // 2, (ph - h) // 2
    full[oy:oy + h, ox:ox + w] = alpha
    rgba = np.zeros((ph, pw, 4), dtype=np.uint8)
    rgba[:, :, :3] = 255
    rgba[:, :, 3] = np.clip(full * 255 + 0.5, 0, 255).astype(np.uint8)
    atlas.img.paste(Image.fromarray(rgba, "RGBA"), (x0, y0))
    return uv, (pw, ph), (ox, oy)


def _atlas_size(spec, cells):
    """모든 칸이 들어가는 가장 작은 정사각 아틀라스 (16픽셀 칸, 256 · 512 · 1024 · 2048)"""
    h, w = cells[0][0].shape
    n = 2 * len(cells)
    for size in (256, 512, 1024, 2048):
        per_row = size // (int(math.ceil(w / 16)) * 16)
        rows = size // (int(math.ceil(h / 16)) * 16)
        if per_row * rows >= n:
            return size
    raise RuntimeError("too big")


def card(name, spec):
    cells = _cells(spec)
    if spec.get("trim"):
        cells = [list(_trim(g, c, spec["trim"])) for g, c in cells]
    m = fx.Model(name, tex=_atlas_size(spec, cells), cell=16)
    m.emissive = True
    # 몸 (빛 > 0.5 — 모든 장을 합친 것) 의 범위 → 모델 칸
    union = np.zeros_like(cells[0][0])
    for g, c in cells:
        union = np.maximum(union, np.maximum(g, c * 0.0 + (c > 0.2) * 1.0))
    ys, xs = np.nonzero(union > 0.5)
    sx0, sx1, sy0, sy1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    bx0, bx1, by0, by1 = spec["box"]
    fit = spec.get("fit", "y")
    if fit == "y":
        k = (by1 - by0) / (sy1 - sy0)
    elif fit == "x":
        k = (bx1 - bx0) / (sx1 - sx0)
    else:
        k = max(bx1 - bx0, by1 - by0) / max(sx1 - sx0, sy1 - sy0)
    # 세로: 몸의 아래 끝 = by0 (맞춤 y) 또는 가운데끼리 (x · max)
    if fit == "y":
        top = by0 + sy1 * k  # 그림 위 끝 (픽셀 0) 의 모델 y
    else:
        top = (by0 + by1) / 2 + (sy0 + sy1) / 2 * k
    # 가로: 가운데 줄 → (bx0 + bx1) / 2 (대칭) · 도끼 자루 → 0
    axis = spec.get("axis")
    if axis == "handle":
        lo = union[int(sy0 + (sy1 - sy0) * 0.75):sy1]
        yy, xx = np.nonzero(lo > 0.5)
        line = xx.mean()
        left = -line * k
    elif isinstance(axis, (int, float)):
        left = -axis * k  # 그림의 이 열 (픽셀) 이 x = 0 — 활의 줌통
    else:
        left = (bx0 + bx1) / 2 - (sx0 + sx1) / 2 * k
    h, w = cells[0][0].shape
    single = len(cells) == 1
    for i, (g, c) in enumerate(cells):
        if not single:
            m.part("f%d" % i)
        guv, (pw, ph), (ox, oy) = _paste(m.atlas, "g%d" % i, g)
        cuv, _, _ = _paste(m.atlas, "c%d" % i, c)
        # 칸 (pw × ph — 그림은 ox, oy 에서 시작) 의 픽셀 가운데들을 모델 칸으로
        x0 = left + (0.5 - ox) * k
        x1 = left + (pw - 0.5 - ox) * k
        y1 = top - (0.5 - oy) * k
        y0 = top - (ph - 0.5 - oy) * k
        for uv, mat, z in ((cuv, fx.GLOW, -0.004), (guv, fx.TINT, 0.0), (cuv, fx.GLOW, 0.004)):
            u0, v0, u1, v1 = uv
            ps = [fx.v3(x0, y1, z), fx.v3(x0, y0, z), fx.v3(x1, y0, z), fx.v3(x1, y1, z)]
            m.quad(ps, [(u0, v0), (u0, v1), (u1, v1), (u1, v0)], mat, [fx.v3(0, 0, 1)] * 4)
    if not single:
        m.part("main")
        n = len(cells)
        for an, val in spec.get("anims", {}).items():
            seq = val[0]
            ln = val[2] if len(val) > 2 else 1.0
            tracks = {}
            L = len(seq)
            for i in range(n):
                if i not in seq:
                    tracks["f%d" % i] = {"scale": [[0, 0], [1, 0]]}
                    continue
                p = seq.index(i)
                tracks["f%d" % i] = {"scale": _slot(p, L)}
            m.anims[an] = {"len": ln, "tracks": tracks}
    return m


def _slot(p, n):
    """차례 p (n 장 중) 만 켬 — 시간 0 ~ 1 을 n 으로 나눔 (참격 판 _steps 와 같은 꼴)"""
    if n == 1:
        return [[0, 1], [1, 1]]
    a, b = p / n, (p + 1) / n
    e = 0.002
    if p == 0:
        return [[0, 1], [b - e, 1], [b, 0], [1, 0]]
    if p == n - 1:
        return [[0, 0], [a - e, 0], [a, 1], [1, 1]]
    return [[0, 0], [a - e, 0], [a, 1], [b - e, 1], [b, 0], [1, 0]]
