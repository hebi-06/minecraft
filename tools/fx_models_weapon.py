"""0.48.0 무기 스킬의 입체 효과 물체 (사용자 "효과 물체를 입체 모델로" — 무기 스킬 차례): tools/fx_models.py 가 add(fx) 로 부름.

까마귀 (후긴과 무닌) · 오딘의 눈 · 작은 태양 · 일식 · 달 · 세계수 · 뿌리 · 씨앗 · 가시덤불 · 꽃 · 불꽃 날개 · 시계판 · 뱀 · 바위 가시 · 망령 기사 · 얼음 돔 ·
사슬 · 혼 · 먹구름 · 불기둥 · 빛기둥 · 검기 · 얼음 껍질 · 그림자 분신 · 바람 정령. 축 (fx_models.py 와 같음): +Y = 길이 (부리 · 끝), +Z = 넓은 면 (등 · 앞면), +X = 오른쪽.
"""
import math

import numpy as np

fx = None  # tools/fx_models.py (add 가 채움)


def add(mod):
    global fx
    fx = mod
    for f in (raven, odin_eye, sun, eclipse, moon, world_tree, root, seed, thorns, flower, fire_wings, clock, serpent, rock_spike, ghost_knight, hex_dome, chain,
              soul, storm_cloud, flame, pillar, crescent_wave, ice_shell, shade, sylph):
        fx.MODELS[f.__name__] = f


# ------------------------------------------------------------ 도움 (모양)


def v3(*a):
    return np.array(a if len(a) == 3 else a[0], dtype=float)


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def ellipse(rx, rz, n=8, phase=0.0):
    return [(rx * math.cos(2 * math.pi * k / n + phase), rz * math.sin(2 * math.pi * k / n + phase)) for k in range(n)]


def frames(path):
    """곡선 (점 목록) 의 단면 틀: 점마다 (접선, 옆 a, 옆 b) — 앞 틀을 이어 돌림 (비틀림 없이)"""
    out = []
    t0 = unit(path[1] - path[0])
    ref = v3(0, 0, 1) if abs(t0[2]) < 0.9 else v3(1, 0, 0)
    a = unit(np.cross(ref, t0))
    for i, p in enumerate(path):
        if i == 0:
            t = t0
        elif i == len(path) - 1:
            t = unit(path[i] - path[i - 1])
        else:
            t = unit(path[i + 1] - path[i - 1])
        a = unit(a - t * np.dot(a, t))
        if np.linalg.norm(a) < 1e-6:
            a = unit(np.cross(v3(0, 0, 1) if abs(t[2]) < 0.9 else v3(1, 0, 0), t))
        b = unit(np.cross(t, a))
        out.append((t, a, b))
    return out


def tube(m, path, radii, sides, mat, uv, rz=None, cap0=True, cap1=True, smooth=True, mats=None):
    """곡선을 따라가는 관 (radii = 점마다 반지름 — 0 이면 끝이 한 점, rz = 옆 b 쪽 반지름 배)"""
    path = [v3(p) for p in path]
    fr = frames(path)
    rings = []
    for p, (t, a, b), r in zip(path, fr, radii):
        k = rz if rz is not None else 1.0
        rings.append([p + a * r * math.cos(2 * math.pi * s / sides) + b * r * k * math.sin(2 * math.pi * s / sides) for s in range(sides)])
    m.loft(rings, mats if mats else mat, uv, cap0=cap0, cap1=cap1, smooth=smooth)


def sphere(m, center, r, mat, uv, segs=12, rings=8, ry=None, axis_rot=None, mats=None):
    """구 (ry = 세로 반지름)"""
    ry = r if ry is None else ry
    prof = []
    for j in range(rings + 1):
        a = -math.pi / 2 + math.pi * j / rings
        prof.append((max(0.0, r * math.cos(a)), ry * math.sin(a)))
    prof[0] = (0.0, prof[0][1])
    prof[-1] = (0.0, prof[-1][1])
    m.lathe(prof, segs, mat, uv, center=center, axis_rot=axis_rot, mats=mats)


def spike(m, base, tip, r, sides, mat, uv, bend=None, segs=4, tip_mat=None, tip_uv=None):
    """뾰족한 가시 (base → tip, 밑동 반지름 r — bend 는 가운데 점을 그만큼 옮김: 휜 가시)"""
    base, tip = v3(base), v3(tip)
    pts = []
    for i in range(segs + 1):
        q = i / segs
        p = base + (tip - base) * q
        if bend is not None:
            p = p + v3(bend) * math.sin(math.pi * q)
        pts.append(p)
    radii = [r * (1 - (i / segs)) ** 1.15 for i in range(segs + 1)]
    radii[-1] = 0.0
    tube(m, pts, radii, sides, mat, uv, cap1=False, smooth=False)


def quad2(m, ps, uv, mat):
    """두 면 사각형 (uv 칸 전체)"""
    u0, v0, u1, v1 = uv
    m.quad(ps, [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], mat, double=True)


# ------------------------------------------------------------ 무늬


def p_down(u, v, rnd):
    """까마귀 몸 깃 (짧은 비늘 깃)"""
    row = int(v * 9)
    cu = (u * 7 + (0.5 if row % 2 else 0)) % 1
    cv = (v * 9) % 1
    edge = math.hypot(cu - 0.5, cv * 0.9) * 1.6
    return 0.62 + 0.32 * max(0.0, 1 - edge) + fx.noise(rnd, 0.03), 1


def p_vane(u, v, rnd):
    """날개깃 (끝이 둥근 긴 깃 — 가장자리 비침): u = 깃 폭, v = 길이 (0 = 끝)"""
    c = abs(u - 0.5) * 2
    tipr = 0.5 * (1 - v) if v < 0.12 else 0
    if c > 0.92 - tipr * 3:
        return 0.0, 0
    if c < 0.07:
        return 1.0, 1
    barb = 0.7 + 0.22 * math.sin(v * 30 + c * 7)
    return barb + fx.noise(rnd, 0.03), 1


def p_sclera(u, v, rnd):
    """눈 흰자 (가는 핏줄)"""
    vein = 0.0
    for k in range(5):
        a = k * 1.3
        if abs((u * 3 + math.sin(v * 9 + a)) % 1 - 0.5) < 0.012:
            vein = 0.18
    return 0.97 - vein + fx.noise(rnd, 0.015), 1


def p_iris(u, v, rnd):
    """눈동자 (동심 무늬 · 방사 결 · 가운데 어두운 동공)"""
    x, y = u - 0.5, v - 0.5
    r = math.hypot(x, y) * 2
    a = math.atan2(y, x)
    if r < 0.32:
        return 0.12, 1
    streak = 0.12 * math.sin(a * 24 + r * 6)
    ring = 0.2 if 0.88 < r < 1.0 else 0.0
    return 0.8 + streak - ring + 0.15 * (1 - r) + fx.noise(rnd, 0.02), 1


def p_ray(u, v, rnd):
    """빛살 (가운데 밝은 줄 — 끝으로 흐려짐)"""
    c = abs(u - 0.5) * 2
    lum = 1.0 - 0.35 * c
    return lum + fx.noise(rnd, 0.02), max(0.0, 1 - v ** 3 * 0.6)


def p_sun(u, v, rnd):
    """해 겉 (끓는 무늬)"""
    s = math.sin(u * 37 + math.sin(v * 21) * 2) * math.sin(v * 29 + math.sin(u * 17))
    return 0.86 + 0.14 * s + fx.noise(rnd, 0.03), 1


def p_crater(u, v, rnd):
    """달 겉 (구덩이 — 테두리 밝고 안 어두움)"""
    lum = 0.88 + fx.noise(rnd, 0.025)
    for cx, cy, r in ((0.2, 0.3, 0.08), (0.62, 0.22, 0.06), (0.45, 0.62, 0.1), (0.8, 0.7, 0.05), (0.1, 0.78, 0.06), (0.33, 0.1, 0.04), (0.9, 0.35, 0.07)):
        d = math.hypot(u - cx, (v - cy) * 2) / r
        if d < 0.8:
            lum -= 0.16 * (1 - d / 0.8)
        elif d < 1.0:
            lum += 0.07
    return lum, 1


def p_bark(u, v, rnd):
    """나무껍질 (세로로 갈라진 결 · 빛나는 룬 줄은 따로 — GLOW)"""
    fiss = abs(math.sin(u * 40 + math.sin(v * 7) * 2.5))
    lum = 0.55 + 0.35 * fiss ** 0.4
    return lum + fx.noise(rnd, 0.04), 1


def p_leaves(u, v, rnd):
    """잎 무리 (밝고 어두운 잎 무늬 · 빛 점)"""
    s = math.sin(u * 53 + math.sin(v * 31) * 3) * math.sin(v * 47 + math.sin(u * 19) * 2)
    lum = 0.68 + 0.22 * s
    if rnd.random() < 0.025:
        lum = 1.0
    return lum + fx.noise(rnd, 0.04), 1


def p_membrane(u, v, rnd):
    """날개 막 (핏줄 · 가장자리로 비침)"""
    vein = 0.25 if abs((u * 5 + v * 0.8) % 1 - 0.5) < 0.04 else 0.0
    return 0.78 + vein - 0.18 * v + fx.noise(rnd, 0.03), 0.88 - 0.2 * v


def p_dial(u, v, rnd):
    """시계판 (u, v = 원판 자리): 바깥 테 · 열두 눈금 (셋 · 여섯 · 아홉 · 열둘은 굵게) · 안쪽 고리 · 가운데 무늬"""
    x, y = u - 0.5, v - 0.5
    r = math.hypot(x, y) * 2
    if r > 1.0:
        return 0.0, 0
    a = (math.degrees(math.atan2(x, -y)) + 360) % 360  # 12 시 = 위
    lum = 0.42 + 0.08 * math.sin(r * 30)
    if r > 0.93:
        lum = 1.0
    elif 0.72 < r < 0.9:
        k = (a / 30) % 1
        big = int(round(a / 30)) % 3 == 0
        if min(k, 1 - k) < (0.06 if big else 0.03) and (r > 0.76 if big else r > 0.8):
            lum = 1.0
        elif min((a / 6) % 1, 1 - (a / 6) % 1) < 0.08 and r > 0.86:
            lum = 0.85
    elif 0.6 < r < 0.64 or 0.22 < r < 0.25:
        lum = 0.95
    elif r < 0.22:
        lum = 0.6 + 0.3 * (abs(math.sin(a / 180 * math.pi * 4)) > 0.7)
    return lum + fx.noise(rnd, 0.02), 1


def p_scales(u, v, rnd):
    """뱀 비늘"""
    row = int(v * 14)
    cu = (u * 8 + (0.5 if row % 2 else 0)) % 1
    cv = (v * 14) % 1
    d = math.hypot(cu - 0.5, cv - 0.15) * 1.7
    return 0.6 + 0.35 * max(0.0, 1 - d) + fx.noise(rnd, 0.03), 1


def p_rock(u, v, rnd):
    """바위 (모난 면 · 얼룩 · 금)"""
    s = math.sin(u * 23 + math.sin(v * 11) * 2) * 0.08 + math.sin(v * 31 + u * 7) * 0.06
    crack = 0.22 if abs((u * 3 + math.sin(v * 8) * 0.3) % 1 - 0.5) < 0.025 else 0.0
    return 0.7 + s - crack + fx.noise(rnd, 0.05), 1


def p_lava(u, v, rnd):
    """용암 줄 (밝음 — 가운데 더 밝게)"""
    c = abs(u - 0.5) * 2
    return 1.0 - 0.3 * c + fx.noise(rnd, 0.03), 1


def p_armor(u, v, rnd):
    """망령 갑옷 (판 이음매 · 테 · 아래로 갈수록 비침)"""
    seam = 0.25 if (v * 6) % 1 < 0.06 else 0.0
    edge = 0.15 if min(u, 1 - u) < 0.05 else 0.0
    return 0.78 - seam + edge + 0.06 * math.sin(u * 17) + fx.noise(rnd, 0.03), 1


def p_wisp(u, v, rnd):
    """망령 옷자락 (아래 v=1 로 갈수록 투명 · 갈래)"""
    tear = 0.5 + 0.5 * math.sin(u * 2 * math.pi * 5)
    a = max(0.0, 1 - v * (1.2 + 0.4 * tear))
    return 0.82 + 0.12 * math.sin(u * 9 + v * 6) + fx.noise(rnd, 0.03), a


def p_hex(u, v, rnd):
    """육각 격자 (줄은 밝고 불투명 · 칸은 옅게 비침): u, v 를 반복"""
    x = u * 6
    y = v * 6 * 2 / math.sqrt(3)
    # 육각 격자: 가장 가까운 중심까지와 둘째 중심까지의 차이로 테두리
    best = []
    for cx in range(-1, 8):
        for cy in range(-1, 9):
            px = cx + (0.5 if cy % 2 else 0.0)
            py = cy * math.sqrt(3) / 2 * 2 / math.sqrt(3)
            best.append(math.hypot(x - px, (y - py) * math.sqrt(3) / 2))
    best.sort()
    edge = best[1] - best[0]
    if edge < 0.07:
        return 1.0, 0.95
    return 0.7 + 0.2 * (1 - min(1, best[0] * 1.6)) + fx.noise(rnd, 0.02), 0.32


def p_link(u, v, rnd):
    return 0.72 + 0.25 * abs(math.sin(u * math.pi * 2)) + fx.noise(rnd, 0.03), 1


def p_cloud(u, v, rnd):
    s = math.sin(u * 19 + math.sin(v * 13) * 2) * math.sin(v * 17 + u * 5)
    return 0.62 + 0.16 * s + 0.12 * (1 - v) + fx.noise(rnd, 0.03), 1


def p_petal(u, v, rnd):
    c = abs(u - 0.5) * 2
    if c > 1 - 0.25 * v ** 2 and v > 0.7:
        return 0.0, 0
    return 0.9 - 0.25 * v + 0.08 * math.sin(c * 9) + fx.noise(rnd, 0.02), 1


# ------------------------------------------------------------ 모델


def raven():
    """빛의 까마귀 (후긴 · 무닌 — 정령의 매로도): 가운데가 원점, 부리 +Y (0.47), 등 +Z, 날개 ±X (펼치면 0.78). 날개 wing_r · wing_l (어깨 축 —
    움직임 flap 되풀이: 위 → 아래, glide: 조금만 떨림), 몸 = 빛깔 (짧은 깃) · 날개깃 = 빛깔 (끝 = 속빛) · 눈 = 흰빛 · 부리 = 속빛"""
    m = fx.Model("raven", tex=128, cell=32)
    a = m.atlas
    down = a.swatch("down", p_down, 2, 2)
    vane = a.swatch("vane", p_vane, 1, 2)
    beak = a.swatch("beak", fx.p_edge)
    eye = a.swatch("eye", fx.p_gem)
    # 몸: 꼬리 → 가슴 → 목 → 머리 (타원 단면)
    secs = [(-0.3, 0.035, 0.022), (-0.18, 0.075, 0.06), (-0.04, 0.105, 0.09), (0.1, 0.1, 0.092), (0.2, 0.07, 0.07), (0.27, 0.068, 0.07), (0.34, 0.05, 0.05),
            (0.37, 0.03, 0.032)]
    rings = []
    for y, rx, rz in secs:
        rings.append([v3(x, y, z + (0.01 if y > 0.15 else 0.0)) for x, z in ellipse(rx, rz, 10)])
    m.loft(rings, fx.TINT, down, smooth=True)
    # 부리 (위아래 납작한 쐐기)
    m.loft([[v3(x, 0.36, z + 0.005) for x, z in ellipse(0.032, 0.024, 6)], [v3(x, 0.43, z - 0.004) for x, z in ellipse(0.014, 0.012, 6)],
            [v3(0, 0.47, -0.012)] * 6], fx.GLOW, beak, cap1=False)
    # 눈 (양옆 — 흰빛)
    for s in (-1, 1):
        m.box((s * 0.052, 0.3, 0.035), (0.02, 0.03, 0.024), fx.WHITE, eye)
    # 꼬리깃 (부채꼴 다섯 — 두 면)
    u0, v0, u1, v1 = vane
    for k in range(5):
        ang = math.radians((k - 2) * 13)
        d = v3(math.sin(ang), -math.cos(ang), 0.06)
        side = v3(math.cos(ang), math.sin(ang), 0) * 0.04
        base = v3(0, -0.27, 0.0)
        tip = base + d * 0.3
        m.quad([base - side, base + side, tip + side * 1.3, tip - side * 1.3], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.TINT, double=True)
    # 날개 (오른쪽 — 어깨 (0.06, 0.06, 0.04) 에서 +X 로): 팔 덮깃 (넓은 판) + 첫째 깃 다섯 (끝으로 갈라짐)
    for side, part in ((1, "wing_r"), (-1, "wing_l")):
        sh = v3(side * 0.06, 0.07, 0.035)
        m.part(part, tuple(sh))
        # 덮깃: 어깨 → 손목 (0.42) — 앞 가장자리 곧게, 뒤로 넓게
        wrist = sh + v3(side * 0.4, 0.02, 0.0)
        p0, p1 = sh + v3(0, 0.05, 0), wrist + v3(0, 0.035, 0)
        p2, p3 = wrist + v3(0, -0.12, 0), sh + v3(0, -0.2, 0)
        ps = [p0, p1, p2, p3] if side > 0 else [p1, p0, p3, p2]
        du0, dv0, du1, dv1 = down
        m.quad(ps, [(du0, dv0), (du1, dv0), (du1, dv1), (du0, dv1)], fx.TINT, double=True)
        # 둘째 깃 (덮깃 뒤 — 짧게 넷)
        for k in range(4):
            q = (k + 0.5) / 4
            root = sh + v3(side * 0.4 * q, -0.12 - 0.04 * (1 - q), -0.004)
            d = unit(v3(side * 0.12, -1, 0))
            w = v3(side * 0.05, 0, 0)
            tip = root + d * 0.16
            ps = [root - w, root + w, tip + w, tip - w] if side > 0 else [root + w, root - w, tip - w, tip + w]
            m.quad(ps, [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.TINT, double=True)
        # 첫째 깃 (손목에서 바깥 · 뒤로 부채꼴 — 끝 속빛)
        for k in range(5):
            ang = math.radians(8 + k * 17)
            d = v3(side * math.cos(ang), -math.sin(ang), 0.0)
            w = v3(-d[1], d[0], 0) * 0.035 * (1 if side > 0 else -1)
            root = wrist + v3(0, -0.02 * k, 0)
            ln = 0.36 - k * 0.035
            tip = root + d * ln
            ps = [root - w, root + w, tip + w, tip - w]
            m.quad(ps, [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.GLOW if k < 2 else fx.TINT, double=True)
    m.part("main")
    m.anims["flap"] = {"len": 0.42, "tracks": {
        "wing_r": {"rot": [[0, 0, -42, 0], [0.45, 0, 32, 0], [1, 0, -42, 0]]},
        "wing_l": {"rot": [[0, 0, 42, 0], [0.45, 0, -32, 0], [1, 0, 42, 0]]}}}
    m.anims["glide"] = {"len": 1.2, "tracks": {
        "wing_r": {"rot": [[0, 0, -6, 0], [0.5, 0, 6, 0], [1, 0, -6, 0]]},
        "wing_l": {"rot": [[0, 0, 6, 0], [0.5, 0, -6, 0], [1, 0, 6, 0]]}}}
    m.anims["dive"] = {"len": 0.3, "tracks": {
        "wing_r": {"rot": [[0, 0, -10, 0], [1, 0, -62, -20]]},
        "wing_l": {"rot": [[0, 0, 10, 0], [1, 0, 62, 20]]}}}
    m.add_glow(0, 0.0, 0.02, 0.24).add_glow(0.55, -0.1, 0.03, 0.2).add_glow(-0.55, -0.1, 0.03, 0.2).add_glow(0, 0.3, 0.04, 0.12)
    return m


def odin_eye():
    """오딘의 눈: 가운데가 원점, 앞 +Z (눈동자), 위 +Y. 눈알 (흰자 · 빛깔 눈동자 · 동공) · 눈꺼풀 둘 (lid_top · lid_bot — x 축으로 닫힘) · 금빛 테 · 빛살 열둘
    (rays — 천천히 돎). 움직임 open (감음 → 뜸) · close (뜸 → 감음) · blink · spin (빛살만)"""
    m = fx.Model("odin_eye", tex=128, cell=32)
    a = m.atlas
    sclera = a.swatch("sclera", p_sclera, 2, 1)
    iris = a.swatch("iris", p_iris, 1, 1)
    lid = a.swatch("lid", fx.p_metal, lum=0.86)
    rim = a.swatch("rim", fx.p_rim)
    ray = a.swatch("ray", p_ray, 1, 2)
    band = a.swatch("band", fx.p_rune_band, 2, 1)
    Rz = fx.rot_x(90)  # 축 +Y → +Z
    m.part("ball", (0, 0, 0))
    sphere(m, (0, 0, 0), 0.36, fx.WHITE, sclera, segs=16, rings=10, axis_rot=Rz)
    # 눈동자: 앞에 볼록한 원판 (빛깔) — 반지름 0.2
    segs = 16
    for k in range(segs):
        a0, a1 = 2 * math.pi * k / segs, 2 * math.pi * (k + 1) / segs
        r, z0, zc = 0.2, 0.31, 0.37
        p0 = v3(0, 0, zc)
        p1 = v3(r * math.cos(a0), r * math.sin(a0), z0)
        p2 = v3(r * math.cos(a1), r * math.sin(a1), z0)
        u0, v0, u1, v1 = iris
        uvf = lambda p: (u0 + (u1 - u0) * (0.5 + p[0] / (2 * r)), v0 + (v1 - v0) * (0.5 - p[1] / (2 * r)))
        m.tri(p0, p1, p2, [uvf(p0), uvf(p1), uvf(p2)], fx.TINT, inside=(0, 0, 0))
    # 눈꺼풀 (위 · 아래 — 반구 껍질 0.39, 닫히면 앞을 덮음 · 열면 x 축으로 뒤로 젖혀짐)
    for part, sgn in (("lid_top", 1), ("lid_bot", -1)):
        m.part(part, (0, 0, 0))
        prof = []
        for j in range(7):
            ang = math.radians(90 * j / 6)
            prof.append((0.395 * math.cos(ang) if j < 6 else 0.0, 0.395 * math.sin(ang)))
        # 반구 (축 +Y 위쪽) → 앞쪽 덮개가 되게: 축을 +Z 로 돌린 뒤 위 · 아래 반으로 자름 — 간단히: 축 +Y 반구 그대로 (위 · 아래), 앞 반만
        rings = []
        for j in range(len(prof)):
            r, y = prof[j]
            ring = []
            for k in range(9):
                ang = math.pi * k / 8  # 앞 반 (z > 0)
                ring.append(v3(r * math.cos(ang), sgn * y, r * math.sin(ang)))
            rings.append(ring)
        m.loft(rings, fx.GOLD, lid, close=False, cap0=False, cap1=False, smooth=False)
        # 눈꺼풀 가장자리 띠 (금 · 빛)
        edge = [v3(0.4 * math.cos(math.pi * k / 8), sgn * 0.004, 0.4 * math.sin(math.pi * k / 8)) for k in range(9)]
        tube(m, edge, [0.018] * 9, 5, fx.GLOW, rim, cap0=True, cap1=True)
    # 금빛 테 (원 — 눈 둘레) · 룬 띠 고리
    m.part("frame", (0, 0, 0))
    m.torus(0.5, 0.045, 28, 6, fx.GOLD, rim, axis_rot=Rz)
    m.torus(0.58, 0.022, 32, 5, fx.GLOW, rim, axis_rot=Rz)
    # 룬 띠 (테 안쪽 얇은 고리 판)
    segs = 28
    u0, v0, u1, v1 = band
    for k in range(segs):
        a0, a1 = 2 * math.pi * k / segs, 2 * math.pi * (k + 1) / segs
        r0, r1 = 0.41, 0.47
        ps = [v3(r0 * math.cos(a0), r0 * math.sin(a0), 0.02), v3(r1 * math.cos(a0), r1 * math.sin(a0), 0.02), v3(r1 * math.cos(a1), r1 * math.sin(a1), 0.02),
              v3(r0 * math.cos(a1), r0 * math.sin(a1), 0.02)]
        m.quad(ps, [(u0 + (u1 - u0) * k / segs, v1), (u0 + (u1 - u0) * k / segs, v0), (u0 + (u1 - u0) * (k + 1) / segs, v0), (u0 + (u1 - u0) * (k + 1) / segs, v1)],
               fx.TINT, double=True)
    # 빛살 열둘 (테 바깥 — 길고 짧게 번갈아, 두 면 판)
    m.part("rays", (0, 0, 0))
    u0, v0, u1, v1 = ray
    for k in range(12):
        ang = 2 * math.pi * k / 12
        ln = 0.42 if k % 2 == 0 else 0.26
        d = v3(math.cos(ang), math.sin(ang), 0)
        w = v3(-d[1], d[0], 0) * 0.045
        base = d * 0.62
        tip = d * (0.62 + ln)
        m.quad([base - w, base + w, tip + w * 0.15, tip - w * 0.15], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.GLOW, double=True)
    m.part("main")
    m.anims["open"] = {"len": 0.5, "tracks": {"lid_top": {"rot": [[0, 0, 0, 0], [1, -78, 0, 0]]}, "lid_bot": {"rot": [[0, 0, 0, 0], [1, 62, 0, 0]]}}}
    m.anims["close"] = {"len": 0.35, "tracks": {"lid_top": {"rot": [[0, -78, 0, 0], [1, 0, 0, 0]]}, "lid_bot": {"rot": [[0, 62, 0, 0], [1, 0, 0, 0]]},
                                                 "rays": {"scale": [[0, 1], [1, 0.6]]}}}
    m.anims["glare"] = {"len": 2.0, "tracks": {"lid_top": {"rot": [[0, -78, 0, 0], [1, -78, 0, 0]]}, "lid_bot": {"rot": [[0, 62, 0, 0], [1, 62, 0, 0]]},
                                                "rays": {"rot": [[0, 0, 0, 0], [0.5, 0, 0, 15], [1, 0, 0, 30]]},
                                                "ball": {"rot": [[0, 0, 0, 0], [0.25, 8, -10, 0], [0.5, -4, 8, 0], [0.75, 6, 4, 0], [1, 0, 0, 0]]}}}
    m.add_glow(0, 0, 0.36, 0.32).add_glow(0, 0, 0.1, 0.62)
    return m


def sun():
    """작은 태양: 가운데가 원점, 반지름 0.5 (끓는 겉 — 속빛 · 흰빛) · 앞 판의 빛살 두 겹 (rays · rays2 — 엇갈려 돎, faceCam 으로 늘 보는 사람 쪽) ·
    고리 둘 (ring — 비스듬히 돎). 움직임 spin (되풀이) · burst (빛살이 길어지며 커짐)"""
    m = fx.Model("sun", tex=128, cell=32)
    a = m.atlas
    surf = a.swatch("surf", p_sun, 2, 2)
    ray = a.swatch("ray", p_ray, 1, 2)
    rim = a.swatch("rim", fx.p_rim)
    m.part("core", (0, 0, 0))
    sphere(m, (0, 0, 0), 0.5, fx.GLOW, surf, segs=16, rings=10)
    sphere(m, (0, 0, 0), 0.42, fx.WHITE, surf, segs=12, rings=8)  # 안 (흰빛 — 겉 사이로)
    # 빛살: 앞 (+Z — faceCam 이면 늘 보는 사람 쪽) 판에 열여섯 (길고 짧게 번갈아) + 뒤 겹 열여섯 (rays2 — 엇갈려 거꾸로 돎)
    u0, v0, u1, v1 = ray
    for part, n, z, ln0, ln1, w0, off in (("rays", 16, 0.02, 0.62, 0.36, 0.11, 0.0), ("rays2", 16, -0.02, 0.45, 0.28, 0.07, math.pi / 16)):
        m.part(part, (0, 0, 0))
        for k in range(n):
            ang = 2 * math.pi * k / n + off
            d = v3(math.cos(ang), math.sin(ang), 0)
            w = v3(-d[1], d[0], 0) * w0
            ln = ln0 if k % 2 == 0 else ln1
            base = d * 0.4 + v3(0, 0, z)
            tip = d * (0.5 + ln) + v3(0, 0, z)
            m.quad([base - w, base + w, tip + w * 0.06, tip - w * 0.06], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.TINT if part == "rays" else fx.GLOW,
                   double=True)
    # 고리 둘 (비스듬히)
    m.part("ring", (0, 0, 0))
    m.torus(0.78, 0.025, 32, 5, fx.GLOW, rim, axis_rot=fx.rot_z(24))
    m.torus(0.7, 0.02, 32, 5, fx.TINT, rim, axis_rot=fx.rot_x(70) @ fx.rot_z(-30))
    m.part("main")
    m.anims["spin"] = {"len": 4.0, "tracks": {"rays": {"rot": [[0, 0, 0, 0], [0.5, 0, 0, 180], [1, 0, 0, 360]]},
                                               "rays2": {"rot": [[0, 0, 0, 0], [0.5, 0, 0, -180], [1, 0, 0, -360]],
                                                         "scale": [[0, 1], [0.25, 1.12], [0.5, 1], [0.75, 1.12], [1, 1]]},
                                               "ring": {"rot": [[0, 0, 0, 0], [0.5, 0, -180, 0], [1, 0, -360, 0]]},
                                               "core": {"scale": [[0, 1], [0.25, 1.05], [0.5, 1], [0.75, 1.05], [1, 1]]}}}
    m.anims["burst"] = {"len": 0.4, "tracks": {"rays": {"scale": [[0, 1], [0.6, 1.8], [1, 2.2]], "rot": [[0, 0, 0, 0], [1, 0, 0, 60]]},
                                                "rays2": {"scale": [[0, 1], [0.6, 2.0], [1, 2.5]], "rot": [[0, 0, 0, 0], [1, 0, 0, -60]]},
                                                "core": {"scale": [[0, 1], [0.5, 1.4], [1, 1.6]]}, "ring": {"scale": [[0, 1], [1, 2.4]]}}}
    m.add_glow(0, 0, 0, 1.1).add_glow(0, 0, 0, 0.55)
    return m


def eclipse():
    """일식 (해를 가린 얼음 돔 위): 가운데 어두운 원판 (그림자 — 앞 +Z) · 그 둘레로 새어 나오는 빛 고리 (속빛) · 빛살 여덟 (rays). 움직임 reveal (원판이
    작아지며 해가 드러남)"""
    m = fx.Model("eclipse", tex=64, cell=32)
    a = m.atlas
    flat = a.swatch("flat", fx.p_flat, lum=0.95)
    ray = a.swatch("ray", p_ray, 1, 1)
    Rz = fx.rot_x(90)
    m.part("disc", (0, 0, 0))
    sphere(m, (0, 0, 0.02), 0.5, fx.SHADOW, flat, segs=16, rings=8, ry=0.5, axis_rot=Rz)
    m.part("corona", (0, 0, 0))
    m.torus(0.53, 0.05, 32, 6, fx.GLOW, flat, axis_rot=Rz)
    m.torus(0.6, 0.025, 32, 5, fx.WHITE, flat, axis_rot=Rz)
    u0, v0, u1, v1 = ray
    for k in range(8):
        ang = 2 * math.pi * k / 8 + math.pi / 8
        d = v3(math.cos(ang), math.sin(ang), 0)
        w = v3(-d[1], d[0], 0) * 0.07
        m.quad([d * 0.55 - w, d * 0.55 + w, d * 1.15 + w * 0.1, d * 1.15 - w * 0.1], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.TINT, double=True)
    m.part("main")
    m.anims["reveal"] = {"len": 0.5, "tracks": {"disc": {"scale": [[0, 1], [0.6, 0.55], [1, 0.01]]}, "corona": {"scale": [[0, 1], [1, 1.8]]}}}
    m.anims["pulse"] = {"len": 1.0, "tracks": {"corona": {"scale": [[0, 1], [0.5, 1.08], [1, 1]], "rot": [[0, 0, 0, 0], [1, 0, 0, 45]]}}}
    m.add_glow(0, 0, -0.05, 0.85)
    return m


def moon():
    """만월 (은빛): 가운데가 원점, 반지름 0.5 (구덩이 · 흰빛) · 둘레를 감은 초승달 날 (crescent — 빛깔) · 작은 별 넷. 움직임 spin (천천히)"""
    m = fx.Model("moon", tex=128, cell=32)
    a = m.atlas
    surf = a.swatch("surf", p_crater, 2, 2)
    edge = a.swatch("edge", fx.p_edge)
    blade = a.swatch("blade", fx.p_blade, 1, 2, runes=False)
    gem = a.swatch("gem", fx.p_gem)
    m.part("ball", (0, 0, 0))
    sphere(m, (0, 0, 0), 0.5, fx.WHITE, surf, segs=18, rings=12)
    # 초승달 날: 앞 (+Z) 왼쪽을 감싸는 굵은 호 (안 0.56 · 바깥 0.78, 두께 ±0.05)
    m.part("crescent", (0, 0, 0))
    fx.arc_band(m, 0.56, 0.82, 100, 260, 18, -0.05, 0.05, fx.TINT, fx.WHITE, blade, edge, tip=0.85)
    # 별 넷 (작은 팔면체)
    m.part("stars", (0, 0, 0))
    for x, y, z, r in ((0.75, 0.55, 0.1, 0.06), (0.95, -0.2, -0.1, 0.045), (-0.2, 0.92, 0.0, 0.05), (0.4, -0.85, 0.15, 0.04)):
        m.loft([[v3(x, y - r, z)] * 4, [v3(x + r * 0.5, y, z), v3(x, y, z + r * 0.5), v3(x - r * 0.5, y, z), v3(x, y, z - r * 0.5)], [v3(x, y + r, z)] * 4],
               fx.GLOW, gem, cap0=False, cap1=False)
    m.part("main")
    m.anims["spin"] = {"len": 6.0, "tracks": {"ball": {"rot": [[0, 0, 0, 0], [1, 0, 360, 0]]}, "crescent": {"rot": [[0, 0, 0, 0], [0.5, 0, 0, 8], [1, 0, 0, 0]]},
                                               "stars": {"rot": [[0, 0, 0, 0], [1, 0, -360, 0]]}}}
    m.add_glow(0, 0, 0, 0.8).add_glow(-0.68, 0, 0.05, 0.3)
    return m


def world_tree():
    """세계수: 밑동이 원점, 위 +Y (높이 3.3 — 줄기 · 가지 1.4 ~ 2.4 · 잎 무리 2.1 ~ 3.3), 뿌리 바닥에 여섯. 줄기 · 가지 · 뿌리 = 나무 (껍질) + 빛나는 룬 줄
    (속빛), 잎 무리 = 빛깔 (canopy — 밑 2.2 가 축). 움직임 grow (줄기가 솟고 잎이 피어남) · sway (잎이 흔들림 · 되풀이)"""
    m = fx.Model("world_tree", tex=128, cell=32)
    a = m.atlas
    bark = a.swatch("bark", p_bark, 1, 2)
    leaves = a.swatch("leaves", p_leaves, 2, 2)
    glow = a.swatch("glow", p_lava)
    m.part("trunk", (0, 0, 0))
    # 줄기: 밑이 넓게 퍼지고 (뿌리 부푼 곳) 비틀리며 올라감
    path = [v3(0.04 * math.sin(i * 0.9), i * 0.2, 0.04 * math.cos(i * 0.7)) for i in range(12)]
    radii = [0.34, 0.24, 0.2, 0.18, 0.17, 0.16, 0.15, 0.14, 0.13, 0.12, 0.11, 0.09]
    tube(m, path, radii, 9, fx.WOOD, bark, cap0=True, cap1=True)
    # 가지 다섯 (줄기 1.3 ~ 2.1 에서 바깥 위로 휨)
    for k in range(5):
        ang = 2 * math.pi * k / 5 + 0.4
        d = v3(math.cos(ang), 0, math.sin(ang))
        y0 = 1.35 + 0.17 * k
        pts = [v3(0, y0, 0) + d * 0.05, v3(0, y0 + 0.2, 0) + d * 0.35, v3(0, y0 + 0.48, 0) + d * 0.62, v3(0, y0 + 0.75, 0) + d * 0.78]
        tube(m, pts, [0.075, 0.055, 0.04, 0.022], 6, fx.WOOD, bark, cap1=True)
    # 뿌리 여섯 (바닥을 따라 바깥으로 — 끝이 땅속으로)
    for k in range(6):
        ang = 2 * math.pi * k / 6 + 0.2
        d = v3(math.cos(ang), 0, math.sin(ang))
        pts = [v3(0, 0.22, 0) + d * 0.12, v3(0, 0.08, 0) + d * 0.45, v3(0, 0.03, 0) + d * 0.85, v3(0, -0.05, 0) + d * 1.15]
        tube(m, pts, [0.12, 0.085, 0.05, 0.018], 6, fx.WOOD, bark, cap1=True)
    # 빛나는 룬 줄 (줄기를 감는 나선 · 뿌리 위 — 속빛)
    helix = [v3(0.2 * math.cos(t * 1.9) * (1 - t / 14), 0.15 + t * 0.15, 0.2 * math.sin(t * 1.9) * (1 - t / 14)) for t in range(12)]
    tube(m, [p * v3(1.02, 1, 1.02) for p in helix], [0.02] * 12, 4, fx.GLOW, glow, cap0=True, cap1=True)
    # 잎 무리 (canopy — 밑 2.2 가 축: 크게 부푼 둥근 덩이 일곱)
    m.part("canopy", (0, 2.2, 0))
    blobs = [(0, 2.85, 0, 0.75, 0.55), (0.62, 2.55, 0.1, 0.5, 0.4), (-0.55, 2.6, 0.25, 0.52, 0.42), (0.1, 2.6, 0.62, 0.5, 0.4), (-0.15, 2.55, -0.62, 0.52, 0.4),
             (0.45, 3.05, -0.35, 0.45, 0.36), (-0.4, 3.1, -0.15, 0.42, 0.34)]
    for x, y, z, r, ry in blobs:
        sphere(m, (x, y, z), r, fx.TINT, leaves, segs=12, rings=8, ry=ry)
    # 빛 열매 (잎 사이 — 흰빛)
    for x, y, z in ((0.5, 2.3, 0.45), (-0.6, 2.35, -0.3), (0.2, 3.2, 0.35), (-0.25, 2.4, 0.7), (0.7, 2.9, -0.4)):
        sphere(m, (x, y, z), 0.06, fx.WHITE, glow, segs=6, rings=4)
    m.part("main")
    m.anims["grow"] = {"len": 1.0, "tracks": {"trunk": {"scale": [[0, 0.15], [0.55, 1.05], [0.7, 1]]},
                                               "canopy": {"scale": [[0, 0.01], [0.35, 0.01], [0.85, 1.1], [1, 1]], "pos": [[0, 0, -1.6, 0], [0.4, 0, -0.9, 0], [0.7, 0, 0, 0]]}}}
    m.anims["sway"] = {"len": 3.0, "tracks": {"canopy": {"rot": [[0, 0, 0, 0], [0.25, 2.5, 0, 1.5], [0.5, 0, 0, 0], [0.75, -2.5, 0, -1.5], [1, 0, 0, 0]]}}}
    m.add_glow(0, 2.7, 0, 1.0).add_glow(0, 1.2, 0, 0.45).add_glow(0, 0.2, 0, 0.5)
    return m


def root():
    """달려가는 뿌리 (땅을 따라 +Y 로 — 길이 2, 위 +Z): 굽이치며 가늘어지는 뿌리 + 곁뿌리 넷 · 빛나는 줄. 움직임 grow (밑에서 끝까지 자람)"""
    m = fx.Model("root", tex=64, cell=32)
    a = m.atlas
    bark = a.swatch("bark", p_bark, 1, 2)
    glow = a.swatch("glow", p_lava)
    m.part("body", (0, 0, 0))
    pts = [v3(0.12 * math.sin(i * 0.9), i * 0.2, 0.05 + 0.04 * math.sin(i * 1.7)) for i in range(11)]
    tube(m, pts, [0.11 - 0.0095 * i for i in range(10)] + [0.0], 6, fx.WOOD, bark)
    for k, i in enumerate((2, 4, 6, 8)):
        s = 1 if k % 2 == 0 else -1
        base = pts[i]
        tube(m, [base, base + v3(s * 0.22, 0.12, 0.02), base + v3(s * 0.38, 0.22, -0.03)], [0.045, 0.025, 0.0], 5, fx.WOOD, bark)
    tube(m, [p + v3(0, 0, 0.07 - 0.005 * i) for i, p in enumerate(pts[:9])], [0.016] * 9, 4, fx.GLOW, glow)
    m.anims["grow"] = {"len": 1.0, "tracks": {"body": {"scale": [[0, 0.05], [1, 1]]}}}
    m.add_glow(0, 1.0, 0.1, 0.35)
    return m


def seed():
    """빛나는 씨앗 (가운데가 원점, 위 +Y): 길쭉한 알 (흰빛 · 속빛 결) · 감은 덩굴 (빛깔) · 위의 떡잎 둘 (leaves — sprout 로 펼쳐짐)"""
    m = fx.Model("seed", tex=64, cell=32)
    a = m.atlas
    shell = a.swatch("shell", fx.p_crystal)
    vine = a.swatch("vine", p_bark)
    leaf = a.swatch("leaf", p_petal)
    sphere(m, (0, 0, 0), 0.14, fx.GLOW, shell, segs=10, rings=8, ry=0.2)
    sphere(m, (0, 0.02, 0), 0.1, fx.WHITE, shell, segs=8, rings=6, ry=0.15)
    helix = [v3(0.15 * math.cos(t * 0.9), -0.18 + t * 0.03, 0.15 * math.sin(t * 0.9)) for t in range(13)]
    tube(m, helix, [0.018] * 13, 4, fx.TINT, vine)
    m.part("leaves", (0, 0.18, 0))
    u0, v0, u1, v1 = leaf
    for s in (-1, 1):
        base = v3(0, 0.18, 0)
        tip = base + v3(s * 0.2, 0.16, 0)
        w = v3(0, 0, 0.07)
        m.quad([base - w, base + w, tip + w * 0.4, tip - w * 0.4], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.TINT, double=True)
    m.part("main")
    m.anims["sprout"] = {"len": 0.5, "tracks": {"leaves": {"scale": [[0, 0.2], [1, 1.4]]}}}
    m.anims["spin"] = {"len": 1.0, "tracks": {"leaves": {"rot": [[0, 0, 0, 0], [1, 0, 360, 0]]}}}
    m.add_glow(0, 0, 0, 0.3)
    return m


def thorns():
    """가시덤불 (가운데 밑이 원점, 위 +Y): 굽은 가시 일곱이 사방 · 위로 솟음 (빛깔 — 끝 흰빛) + 작은 곁가시. 가시 spikes (밑이 축 — burst 로 솟음)"""
    m = fx.Model("thorns", tex=64, cell=32)
    a = m.atlas
    bark = a.swatch("bark", p_bark, 1, 2)
    edge = a.swatch("edge", fx.p_edge)
    m.part("spikes", (0, 0, 0))
    for k in range(7):
        ang = 2 * math.pi * k / 7 + 0.3
        d = v3(math.cos(ang), 0, math.sin(ang))
        h = 0.95 + 0.25 * (k % 3) / 2
        out = 0.35 + 0.15 * (k % 2)
        base = d * 0.08
        tip = d * out + v3(0, h, 0)
        spike(m, base, tip, 0.075, 5, fx.TINT, bark, bend=d * 0.18 + v3(0, 0.05, 0))
        # 곁가시 둘
        for q, s in ((0.35, 1), (0.6, -1)):
            p = base + (tip - base) * q + d * 0.18 * math.sin(math.pi * q)
            side = unit(np.cross(d, v3(0, 1, 0))) * s
            spike(m, p, p + side * 0.14 + v3(0, 0.09, 0), 0.022, 4, fx.WHITE, edge, segs=2)
    spike(m, v3(0, 0, 0), v3(0.05, 1.3, -0.04), 0.09, 6, fx.TINT, bark, bend=v3(0.08, 0, 0.05))
    m.part("main")
    m.anims["burst"] = {"len": 0.35, "tracks": {"spikes": {"scale": [[0, 0.05], [0.7, 1.15], [1, 1]]}}}
    m.anims["sink"] = {"len": 0.4, "tracks": {"spikes": {"scale": [[0, 1], [1, 0.05]]}}}
    m.add_glow(0, 0.5, 0, 0.45).add_glow(0, 1.1, 0, 0.25)
    return m


def flower():
    """꽃 (가운데가 원점, 앞 +Y — 꽃잎이 위로 펼쳐짐): 꽃잎 여덟 (petals — bloom 으로 봉오리 → 활짝) · 가운데 꽃술 (흰빛 · 속빛)"""
    m = fx.Model("flower", tex=64, cell=32)
    a = m.atlas
    petal = a.swatch("petal", p_petal)
    gem = a.swatch("gem", fx.p_gem)
    for k in range(8):
        part = "petal%d" % k
        ang = 2 * math.pi * k / 8
        d = v3(math.cos(ang), 0, math.sin(ang))
        side = v3(-d[2], 0, d[0])
        m.part(part, (0, 0.02, 0))
        base = v3(0, 0.02, 0) + d * 0.03
        mid = base + d * 0.22 + v3(0, 0.08, 0)
        tip = base + d * 0.4 + v3(0, 0.12, 0)
        u0, v0, u1, v1 = petal
        m.quad([base - side * 0.04, base + side * 0.04, mid + side * 0.13, mid - side * 0.13], [(u0, v1), (u1, v1), (u1, (v0 + v1) / 2), (u0, (v0 + v1) / 2)],
               fx.TINT if k % 2 == 0 else fx.GLOW, double=True)
        m.quad([mid - side * 0.13, mid + side * 0.13, tip + side * 0.02, tip - side * 0.02], [(u0, (v0 + v1) / 2), (u1, (v0 + v1) / 2), (u1, v0), (u0, v0)],
               fx.TINT if k % 2 == 0 else fx.GLOW, double=True)
    m.part("main")
    sphere(m, (0, 0.06, 0), 0.07, fx.WHITE, gem, segs=8, rings=6)
    tracks = {}
    for k in range(8):
        ang = 360 * k / 8
        # 꽃잎을 바깥 → 위로 오므림 (봉오리) → 펼침: 자기 축 (가운데에서 바깥으로) 에 수직인 축으로 돌림 — 근사: 꽃잎 하나를 x · z 로 나눠 돌림
        ax = math.radians(ang)
        cx, sz = -math.sin(ax), math.cos(ax)
        tracks["petal%d" % k] = {"rot": [[0, 70 * cx, 0, 70 * sz], [0.6, -8 * cx, 0, -8 * sz], [1, 0, 0, 0]]}
    m.anims["bloom"] = {"len": 0.5, "tracks": tracks}
    m.add_glow(0, 0.08, 0, 0.32)
    return m


def fire_wings():
    """불꽃 날개 (용의 날개 둘 — 등 가운데가 원점, 위 +Y, 뒤 +Z, 날개 ±X): 팔뼈 · 손가락뼈 넷 · 사이의 막 (빛깔 — 비침, 핏줄) · 뼈 끝 발톱 (흰빛).
    wing_r · wing_l (어깨 ±0.12 — 움직임 spread (접힘 → 펼침) · flap (되풀이) · fold)"""
    m = fx.Model("fire_wings", tex=128, cell=32)
    m.emissive = True  # 불꽃 날개 (금빛 날개) — 0.48.0 사진첩: 아래에서 보면 그늘져 갈색으로 보임
    a = m.atlas
    mem = a.swatch("mem", p_membrane, 2, 2)
    bone = a.swatch("bone", fx.p_bone)
    edge = a.swatch("edge", fx.p_edge)
    for side, part in ((1, "wing_r"), (-1, "wing_l")):
        sh = v3(side * 0.12, 0.05, 0.08)
        m.part(part, tuple(sh))
        elbow = sh + v3(side * 0.55, 0.42, 0.1)
        wrist = sh + v3(side * 1.05, 0.62, 0.12)
        tube(m, [sh, elbow, wrist], [0.06, 0.045, 0.035], 5, fx.GLOW, bone)
        fingers = []
        for k, (dx, dy) in enumerate(((0.75, 0.38), (0.95, -0.05), (0.85, -0.5), (0.6, -0.85))):
            tipp = wrist + v3(side * dx, dy, 0.08 - 0.03 * k)
            tube(m, [wrist, (wrist + tipp) / 2 + v3(0, 0.05, 0.02), tipp], [0.03, 0.022, 0.0], 4, fx.GLOW, bone)
            fingers.append(tipp)
        # 막: 손목 → 손가락 사이 · 마지막 손가락 → 몸 (팔 아래)
        chain_pts = [sh + v3(0, -0.55, 0.0)] + [elbow]
        u0, v0, u1, v1 = mem
        tips = [wrist + v3(side * 0.75, 0.38, 0.08)] + fingers[1:]
        poly = [wrist] + fingers  # 손목 둘레 부채꼴
        for i in range(len(fingers) - 1):
            p0, p1 = fingers[i], fingers[i + 1]
            mid0 = wrist + (p0 - wrist) * 0.55
            mid1 = wrist + (p1 - wrist) * 0.55
            # 손가락 사이 막 (끝이 안쪽으로 패임)
            inner = (p0 + p1) / 2 + (wrist - (p0 + p1) / 2) * 0.28
            m.quad([wrist, mid0, inner, mid1] if side > 0 else [wrist, mid1, inner, mid0],
                   [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], fx.TINT, double=True)
            m.quad([mid0, p0, inner, inner] if side > 0 else [mid0, inner, p0, p0], [(u0, v0), (u1, v0), (u1, v1), (u1, v1)], fx.TINT, double=True)
            m.quad([mid1, inner, p1, p1] if side > 0 else [mid1, p1, inner, inner], [(u0, v0), (u1, v0), (u1, v1), (u1, v1)], fx.TINT, double=True)
        # 팔 아래 막 (몸 쪽)
        last = fingers[-1]
        body_low = sh + v3(side * 0.05, -0.6, 0.0)
        m.quad([sh, elbow, wrist, last] if side > 0 else [sh, last, wrist, elbow], [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], fx.TINT, double=True)
        m.quad([sh, last, body_low, body_low] if side > 0 else [sh, body_low, last, last], [(u0, v0), (u1, v0), (u1, v1), (u1, v1)], fx.TINT, double=True)
        # 손목 발톱 (흰빛)
        spike(m, wrist, wrist + v3(side * 0.05, 0.22, 0.02), 0.03, 4, fx.WHITE, edge, segs=2)
    m.part("main")
    m.anims["spread"] = {"len": 0.45, "tracks": {"wing_r": {"rot": [[0, 0, -70, 55], [1, 0, 0, 0]], "scale": [[0, 0.5], [1, 1]]},
                                                  "wing_l": {"rot": [[0, 0, 70, -55], [1, 0, 0, 0]], "scale": [[0, 0.5], [1, 1]]}}}
    m.anims["flap"] = {"len": 0.6, "tracks": {"wing_r": {"rot": [[0, 0, 0, 18], [0.5, 0, -10, -26], [1, 0, 0, 18]]},
                                               "wing_l": {"rot": [[0, 0, 0, -18], [0.5, 0, 10, 26], [1, 0, 0, -18]]}}}
    m.anims["dive"] = {"len": 0.25, "tracks": {"wing_r": {"rot": [[0, 0, 0, 0], [1, 0, -35, 40]]}, "wing_l": {"rot": [[0, 0, 0, 0], [1, 0, 35, -40]]}}}
    m.add_glow(0.9, 0.55, 0.1, 0.5).add_glow(-0.9, 0.55, 0.1, 0.5).add_glow(1.5, 0.3, 0.1, 0.35).add_glow(-1.5, 0.3, 0.1, 0.35).add_glow(0, 0.1, 0.05, 0.3)
    return m


def clock():
    """시계판 (가운데가 원점, 판 앞 +Z — 땅에 눕히면 face = 위): 판 (반지름 1 — 눈금 · 고리 무늬, 빛깔) · 테 (금 · 속빛) · 톱니 둘 (gear_a · gear_b — 판 뒤에서
    돎) · 시침 (hour) · 분침 (minute — 가운데가 축, +Y = 12 시). 움직임 tick (분침 한 바퀴 · 시침 30° · 톱니) · midnight (두 바늘이 12 시로 겹침)"""
    m = fx.Model("clock", tex=128, cell=32)
    a = m.atlas
    dial = a.swatch("dial", p_dial, 2, 2)
    rim = a.swatch("rim", fx.p_rim)
    metal = a.swatch("metal", fx.p_metal, lum=0.86)
    gem = a.swatch("gem", fx.p_gem)
    blade = a.swatch("blade", fx.p_edge)
    # 판 (원판 — 앞 +Z, 무늬는 원판 좌표, 두 면)
    segs = 32
    u0, v0, u1, v1 = dial
    for k in range(segs):
        a0, a1 = 2 * math.pi * k / segs, 2 * math.pi * (k + 1) / segs
        ps = [v3(0, 0, 0.0), v3(math.cos(a0), math.sin(a0), 0.0), v3(math.cos(a1), math.sin(a1), 0.0)]
        uvf = lambda p: (u0 + (u1 - u0) * (0.5 + 0.5 * p[0]), v0 + (v1 - v0) * (0.5 - 0.5 * p[1]))
        m.tri(ps[0], ps[1], ps[2], [uvf(p) for p in ps], fx.TINT, inside=(0, 0, -0.1))
        m.tri(ps[0], ps[2], ps[1], [uvf(p) for p in (ps[0], ps[2], ps[1])], fx.DARK, inside=(0, 0, 0.1))
    Rz = fx.rot_x(90)
    m.torus(1.02, 0.045, 40, 6, fx.GOLD, rim, axis_rot=Rz)
    m.torus(0.62, 0.02, 32, 4, fx.GLOW, rim, center=(0, 0, 0.01), axis_rot=Rz)
    # 열두 자리의 작은 보석 (흰빛)
    for k in range(12):
        ang = math.pi / 2 - 2 * math.pi * k / 12
        r = 0.9
        s = 0.05 if k % 3 == 0 else 0.03
        m.box((r * math.cos(ang), r * math.sin(ang), 0.02), (s, s, 0.03), fx.WHITE, gem, rot=fx.rot_z(math.degrees(ang)))
    # 톱니 둘 (판 뒤 — 테 밖으로 이가 보임)
    for part, (cx, cy, R, teeth) in (("gear_a", (0.55, 0.45, 0.62, 14)), ("gear_b", (-0.6, -0.4, 0.5, 11))):
        m.part(part, (cx, cy, -0.06))
        m.torus(R * 0.82, 0.04, 24, 4, fx.GOLD, metal, center=(cx, cy, -0.06), axis_rot=Rz)
        for t in range(teeth):
            ang = 2 * math.pi * t / teeth
            m.box((cx + R * 0.93 * math.cos(ang), cy + R * 0.93 * math.sin(ang), -0.06), (0.09, 0.1, 0.05), fx.GOLD, metal,
                  rot=fx.rot_z(math.degrees(ang)))
        for t in range(4):
            ang = 2 * math.pi * t / 4
            m.box((cx + R * 0.4 * math.cos(ang), cy + R * 0.4 * math.sin(ang), -0.06), (R * 0.8, 0.04, 0.03), fx.GOLD, metal, rot=fx.rot_z(math.degrees(ang)))
    # 바늘 (가운데가 축 — 판 앞 0.04 · 0.07)
    m.part("hour", (0, 0, 0))
    m.loft([[v3(-0.05, -0.12, 0.05), v3(0.05, -0.12, 0.05), v3(0.05, -0.12, 0.08), v3(-0.05, -0.12, 0.08)],
            [v3(-0.065, 0.3, 0.05), v3(0.065, 0.3, 0.05), v3(0.065, 0.3, 0.08), v3(-0.065, 0.3, 0.08)],
            [v3(0, 0.5, 0.05), v3(0, 0.5, 0.05), v3(0, 0.5, 0.08), v3(0, 0.5, 0.08)]], fx.GLOW, blade)
    m.part("minute", (0, 0, 0))
    m.loft([[v3(-0.035, -0.18, 0.09), v3(0.035, -0.18, 0.09), v3(0.035, -0.18, 0.115), v3(-0.035, -0.18, 0.115)],
            [v3(-0.045, 0.55, 0.09), v3(0.045, 0.55, 0.09), v3(0.045, 0.55, 0.115), v3(-0.045, 0.55, 0.115)],
            [v3(0, 0.82, 0.09), v3(0, 0.82, 0.09), v3(0, 0.82, 0.115), v3(0, 0.82, 0.115)]], fx.WHITE, blade)
    m.part("main")
    sphere(m, (0, 0, 0.11), 0.06, fx.WHITE, gem, segs=8, rings=6)
    m.anims["tick"] = {"len": 3.0, "tracks": {"minute": {"rot": [[0, 0, 0, 0], [0.25, 0, 0, -90], [0.5, 0, 0, -180], [0.75, 0, 0, -270], [1, 0, 0, -360]]},
                                               "hour": {"rot": [[0, 0, 0, 60], [1, 0, 0, 30]]},
                                               "gear_a": {"rot": [[0, 0, 0, 0], [1, 0, 0, 240]]}, "gear_b": {"rot": [[0, 0, 0, 0], [1, 0, 0, -300]]}}}
    m.anims["midnight"] = {"len": 0.4, "tracks": {"minute": {"rot": [[0, 0, 0, -330], [1, 0, 0, -360]]}, "hour": {"rot": [[0, 0, 0, 30], [1, 0, 0, 0]]},
                                                   "gear_a": {"rot": [[0, 0, 0, 0], [1, 0, 0, 90]]}, "gear_b": {"rot": [[0, 0, 0, 0], [1, 0, 0, -110]]}}}
    m.anims["stop"] = {"len": 1.0, "tracks": {"minute": {"rot": [[0, 0, 0, -120], [0.1, 0, 0, -126], [0.2, 0, 0, -120], [1, 0, 0, -120]]},
                                               "hour": {"rot": [[0, 0, 0, 40], [1, 0, 0, 40]]}}}
    m.add_glow(0, 0, 0.1, 0.6).add_glow(0, 0, 0.05, 1.1)
    return m


def serpent():
    """뱀 (머리 +Y — 길이 1.4, 등 +Z): S 자로 굽은 몸 (비늘 — 빛깔, 배 = 속빛 줄) · 머리 (흰빛 눈) · 아래턱 (jaw — bite 로 벌렸다 닫음) · 갈래 혀"""
    m = fx.Model("serpent", tex=128, cell=32)
    a = m.atlas
    scales = a.swatch("scales", p_scales, 2, 2)
    belly = a.swatch("belly", fx.p_band)
    eye = a.swatch("eye", fx.p_gem)
    edge = a.swatch("edge", fx.p_edge)
    n = 16
    path = [v3(0.13 * math.sin(i / (n - 1) * math.pi * 2.2), -0.85 + 1.0 * i / (n - 1), 0.0) for i in range(n)]
    radii = [0.012 + 0.058 * math.sin(min(1, i / (n - 1) * 1.25) * math.pi * 0.62) for i in range(n)]
    tube(m, path, radii, 8, fx.TINT, scales, rz=0.85)
    # 배 줄 (속빛 — 아래쪽 가는 띠)
    tube(m, [p + v3(0, 0, -r * 0.7) for p, r in zip(path, radii)], [r * 0.45 for r in radii], 4, fx.GLOW, belly)
    # 머리 (납작한 쐐기 — 목에서 위로)
    neck = path[-1]
    head = [(neck[1] - 0.01, 0.06, 0.05), (neck[1] + 0.08, 0.085, 0.055), (neck[1] + 0.17, 0.07, 0.045), (neck[1] + 0.25, 0.03, 0.025)]
    rings = [[v3(neck[0] + x, y, z) for x, z in ellipse(rx, rz, 8)] for y, rx, rz in head]
    m.loft(rings, fx.TINT, scales, smooth=True)
    for s in (-1, 1):
        m.box((neck[0] + s * 0.06, neck[1] + 0.11, 0.03), (0.025, 0.035, 0.025), fx.WHITE, eye)
    # 아래턱 (턱 축에서 벌어짐) · 혀
    m.part("jaw", (neck[0], neck[1] + 0.04, -0.02))
    jaw = [(neck[1] + 0.03, 0.055, 0.02), (neck[1] + 0.15, 0.05, 0.018), (neck[1] + 0.22, 0.02, 0.01)]
    m.loft([[v3(neck[0] + x, y, z - 0.035) for x, z in ellipse(rx, rz, 6)] for y, rx, rz in jaw], fx.GLOW, belly, smooth=True)
    tube(m, [v3(neck[0], neck[1] + 0.12, -0.02), v3(neck[0], neck[1] + 0.3, -0.02)], [0.008, 0.006], 3, fx.WHITE, edge)
    for s in (-1, 1):
        tube(m, [v3(neck[0], neck[1] + 0.3, -0.02), v3(neck[0] + s * 0.03, neck[1] + 0.35, -0.02)], [0.006, 0.0], 3, fx.WHITE, edge)
    m.part("main")
    m.anims["bite"] = {"len": 0.4, "tracks": {"jaw": {"rot": [[0, 0, 0, 0], [0.4, 40, 0, 0], [0.7, 0, 0, 0], [1, 0, 0, 0]]}}}
    m.anims["hiss"] = {"len": 0.8, "tracks": {"jaw": {"rot": [[0, 0, 0, 0], [0.5, 22, 0, 0], [1, 0, 0, 0]]}}}
    m.add_glow(0, 0.2, 0, 0.3).add_glow(0, -0.3, 0, 0.25)
    return m


def rock_spike():
    """바위 가시 무리 (밑 가운데가 원점, 위 +Y — 높이 1.3): 모난 바위 기둥 셋 (빛깔 — 바위 결) · 갈라진 틈의 용암 줄 (속빛) · 밑의 깨진 돌 조각. spikes
    (rise 로 땅에서 솟음 · sink 로 꺼짐)"""
    m = fx.Model("rock_spike", tex=64, cell=32)
    a = m.atlas
    rock = a.swatch("rock", p_rock, 1, 2)
    lava = a.swatch("lava", p_lava)
    m.part("spikes", (0, 0, 0))
    for (x, z, h, r, tilt), seedv in zip(((0, 0, 1.3, 0.3, (0.05, 0.03)), (0.32, 0.12, 0.85, 0.2, (0.25, 0.1)), (-0.25, -0.2, 0.7, 0.18, (-0.2, -0.18))),
                                         (0, 1, 2)):
        rnd = np.random.RandomState(7 + seedv)
        n = 6
        rings = []
        for j, q in enumerate((0.0, 0.45, 0.8)):
            rr = r * (1 - q * 0.85)
            off = v3(tilt[0] * q * h, q * h, tilt[1] * q * h)
            ring = []
            for k in range(n):
                ang = 2 * math.pi * k / n + 0.3 * seedv
                jit = 1 + (rnd.rand() - 0.5) * 0.35
                ring.append(v3(x, 0, z) + off + v3(rr * jit * math.cos(ang), 0, rr * jit * math.sin(ang)))
            rings.append(ring)
        rings.append([v3(x + tilt[0] * h, h, z + tilt[1] * h)] * n)
        m.loft(rings, fx.TINT, rock, cap0=True, cap1=False)
        # 용암 줄 (모서리 둘을 따라 — 겉에서 조금 바깥)
        for k in (0, 3):
            pts = [rings[j][k] + unit(rings[j][k] - (v3(x, 0, z) + v3(tilt[0] * (j / 3) * h, rings[j][k][1], tilt[1] * (j / 3) * h))) * 0.012 for j in range(3)]
            tube(m, pts, [0.018, 0.014, 0.008], 3, fx.GLOW, lava)
    # 깨진 돌 조각 (밑 둘레)
    for k in range(6):
        ang = 2 * math.pi * k / 6 + 0.5
        p = v3(0.45 * math.cos(ang), 0.04, 0.45 * math.sin(ang))
        m.box(tuple(p), (0.12, 0.08, 0.1), fx.TINT, rock, rot=fx.rot_y(k * 37) @ fx.rot_z(15))
    m.part("main")
    m.anims["rise"] = {"len": 0.3, "tracks": {"spikes": {"pos": [[0, 0, -1.3, 0], [0.75, 0, 0.08, 0], [1, 0, 0, 0]]}}}
    m.anims["sink"] = {"len": 0.4, "tracks": {"spikes": {"pos": [[0, 0, 0, 0], [1, 0, -1.3, 0]]}}}
    m.add_glow(0, 0.4, 0, 0.4).add_glow(0, 0.05, 0, 0.55)
    return m


def ghost_knight():
    """망령 기사 (발밑 가운데가 원점, 위 +Y — 키 2.0, 앞 +Z): 투구 (흰빛 눈 틈) · 갑옷 몸 (빛깔) · 어깨 갑 · 왼팔의 둥근 방패 · 아래로 갈수록 흩어지는 옷자락 (비침).
    칼 든 오른팔 (arm — 어깨 (−0.3, 1.5) 축: slash 로 머리 위 → 앞 아래로 내리침, raise 로 칼을 하늘로). 앞 +Z 를 보면 오른쪽이 −X"""
    m = fx.Model("ghost_knight", tex=128, cell=32)
    a = m.atlas
    armor = a.swatch("armor", p_armor, 2, 2)
    wisp = a.swatch("wisp", p_wisp, 2, 1)
    eye = a.swatch("eye", fx.p_gem)
    blade = a.swatch("blade", fx.p_blade, 1, 2, runes=True)
    edge = a.swatch("edge", fx.p_edge)
    rim = a.swatch("rim", fx.p_rim)
    # 옷자락 (허리 1.0 → 아래 0.05 — 넓어지며 흩어짐)
    rings = []
    for y, r in ((1.02, 0.24), (0.7, 0.3), (0.38, 0.34), (0.05, 0.3)):
        rings.append([v3(r * math.cos(2 * math.pi * k / 12) * (1 + 0.08 * math.sin(k * 2.1 + y * 5)), y, r * math.sin(2 * math.pi * k / 12)) for k in range(12)])
    m.loft(rings[::-1], fx.TINT, wisp, cap0=False, cap1=False, smooth=True)
    # 몸통 (허리 → 가슴 → 어깨)
    torso = [(0.98, 0.2, 0.15), (1.25, 0.26, 0.17), (1.48, 0.29, 0.18), (1.58, 0.22, 0.15)]
    m.loft([[v3(x, y, z) for x, z in ellipse(rx, rz, 10)] for y, rx, rz in torso], fx.TINT, armor, smooth=True)
    # 가슴 문장 · 허리띠 (속빛)
    m.box((0, 1.3, 0.17), (0.12, 0.2, 0.03), fx.GLOW, rim)
    m.torus(0.21, 0.025, 14, 4, fx.GLOW, rim, center=(0, 1.0, 0))
    # 어깨 갑 (둥근 판 둘)
    for s in (-1, 1):
        sphere(m, (s * 0.3, 1.56, 0), 0.13, fx.TINT, armor, segs=8, rings=6, ry=0.09)
    # 투구 (네모진 통 · 앞 눈 틈 · 위 볏)
    m.loft([[v3(x, y, z) for x, z in ellipse(rx, rz, 8, phase=math.pi / 8)] for y, rx, rz in ((1.6, 0.11, 0.11), (1.72, 0.13, 0.13), (1.86, 0.12, 0.12),
                                                                                              (1.92, 0.08, 0.08))], fx.TINT, armor)
    m.box((0, 1.77, 0.125), (0.17, 0.035, 0.02), fx.WHITE, eye)
    m.box((0, 1.96, -0.02), (0.03, 0.12, 0.2), fx.GLOW, rim)
    # 왼팔 + 방패 (몸 왼쪽 앞 — 앞 +Z 를 보면 왼쪽이 +X)
    tube(m, [v3(0.3, 1.5, 0), v3(0.36, 1.22, 0.08), v3(0.3, 1.05, 0.2)], [0.07, 0.06, 0.05], 6, fx.TINT, armor)
    Rz = fx.rot_x(90)
    shield_c = v3(0.36, 1.12, 0.3)
    sphere(m, tuple(shield_c), 0.26, fx.TINT, armor, segs=12, rings=6, ry=0.06, axis_rot=Rz)
    m.torus(0.26, 0.025, 16, 4, fx.GLOW, rim, center=tuple(shield_c + v3(0, 0, 0.02)), axis_rot=Rz)
    # 오른팔 + 칼 (어깨 축 — 기본: 칼을 앞 위로 듦)
    m.part("arm", (-0.3, 1.5, 0))
    tube(m, [v3(-0.3, 1.5, 0), v3(-0.38, 1.3, 0.15), v3(-0.36, 1.22, 0.38)], [0.07, 0.06, 0.05], 6, fx.TINT, armor)
    hilt = v3(-0.36, 1.22, 0.42)
    tube(m, [hilt - v3(0, 0.1, 0), hilt + v3(0, 0.05, 0)], [0.025, 0.025], 5, fx.DARK, armor)
    m.box(tuple(hilt + v3(0, 0.06, 0)), (0.22, 0.04, 0.05), fx.GLOW, rim)
    secs = fx.blade_sections(0.0, 0.85, 0.06, 0.05, 0.016, 0.012, 1.05, n=3)
    m.loft([(hilt[1] + 0.08 + y, [(x + hilt[0], z + hilt[2]) for x, z in poly]) for y, poly in secs[:-1]] +
           [[v3(hilt[0], hilt[1] + 0.08 + secs[-1][0], hilt[2])] * 6], fx.BLADE_MATS, blade, cap1=False)
    m.part("main")
    m.anims["slash"] = {"len": 0.5, "tracks": {"arm": {"rot": [[0, -150, 0, -10], [0.35, -160, 0, -15], [0.6, 20, 0, 10], [1, 10, 0, 5]]}}}
    m.anims["raise"] = {"len": 0.6, "tracks": {"arm": {"rot": [[0, 0, 0, 0], [1, -150, 0, -10]]}}}
    m.anims["float"] = {"len": 2.0, "tracks": {"arm": {"rot": [[0, -10, 0, 0], [0.5, -16, 0, -2], [1, -10, 0, 0]]}}}
    m.add_glow(0, 1.77, 0.15, 0.2).add_glow(0, 1.2, 0.1, 0.45).add_glow(-0.36, 1.8, 0.42, 0.25)
    return m


def hex_dome():
    """얼음 돔 (밑 가운데가 원점, 반지름 1 — 반구): 육각 격자 (줄은 밝고 칸은 비침 — 안팎 두 면) · 밑 테 (속빛). shell (fold 로 접혀 줄어듦 · pulse)"""
    m = fx.Model("hex_dome", tex=128, cell=32)
    a = m.atlas
    hexes = a.swatch("hex", p_hex, 2, 2)
    rim = a.swatch("rim", fx.p_rim)
    m.part("shell", (0, 0, 0))
    segs, rings = 24, 8
    u0, v0, u1, v1 = hexes
    for j in range(rings):
        a0 = math.pi / 2 * j / rings
        a1 = math.pi / 2 * (j + 1) / rings
        for k in range(segs):
            b0 = 2 * math.pi * k / segs
            b1 = 2 * math.pi * (k + 1) / segs
            p = lambda aa, bb: v3(math.cos(aa) * math.cos(bb), math.sin(aa), math.cos(aa) * math.sin(bb))
            ps = [p(a0, b0), p(a0, b1), p(a1, b1), p(a1, b0)]
            uu0 = u0 + (u1 - u0) * ((k % 4) / 4)
            uu1 = u0 + (u1 - u0) * ((k % 4 + 1) / 4)
            vv0 = v1 - (v1 - v0) * ((j % 4) / 4)
            vv1 = v1 - (v1 - v0) * ((j % 4 + 1) / 4)
            uvs = [(uu0, vv0), (uu1, vv0), (uu1, vv1), (uu0, vv1)]
            if np.linalg.norm(ps[2] - ps[3]) < 1e-9:
                ps = [ps[0], ps[1], ps[2], ps[2]]
            ns = [unit(q) for q in ps]
            fn = np.cross(ps[1] - ps[0], ps[2] - ps[0])
            if np.dot(fn, ns[0]) < 0:
                ps, uvs, ns = ps[::-1], uvs[::-1], ns[::-1]
            m.quad(ps, uvs, fx.TINT, ns)
            m.quad(ps[::-1], uvs[::-1], fx.GLOW, [-n for n in ns[::-1]])
    m.torus(1.0, 0.03, 40, 4, fx.GLOW, rim)
    m.part("main")
    m.anims["fold"] = {"len": 0.4, "tracks": {"shell": {"scale": [[0, 1], [0.6, 0.55], [1, 0.01]]}}}
    m.anims["rise"] = {"len": 0.4, "tracks": {"shell": {"scale": [[0, 0.05], [0.8, 1.04], [1, 1]]}}}
    m.add_glow(0, 0.95, 0, 0.35)
    return m


def chain():
    """망자의 사슬 (끝 +Y — 길이 2, 고리 열넷): 엇갈린 고리 (빛깔 · 쇠 결) + 끝 갈고리 (흰빛). 길이가 다르면 크기로"""
    m = fx.Model("chain", tex=64, cell=32)
    a = m.atlas
    link = a.swatch("link", p_link)
    edge = a.swatch("edge", fx.p_edge)
    n = 14
    for i in range(n):
        y = -1.0 + (i + 0.5) * 2.0 / n
        rot = fx.rot_z(90) @ (fx.rot_x(90) if i % 2 else np.eye(3))
        # 고리: 길쭉한 고리 (반지름 0.06 · 굵기 0.018) — torus 를 y 로 늘림 대신 두 개의 반 고리 + 곧은 줄: 근사로 torus
        m.torus(0.065, 0.018, 10, 4, fx.TINT, link, center=(0, y, 0), axis_rot=fx.rot_x(90) @ fx.rot_y(90 * (i % 2)))
    spike(m, v3(0, 1.0, 0), v3(0, 1.22, 0), 0.05, 4, fx.WHITE, edge, segs=2)
    tube(m, [v3(0, 1.08, 0), v3(0.09, 1.0, 0), v3(0.12, 0.88, 0)], [0.02, 0.018, 0.0], 4, fx.WHITE, edge)
    m.add_glow(0, 0, 0, 0.18).add_glow(0, 1.05, 0, 0.16)
    return m


def soul():
    """혼 (끝 +Y 로 날아감 — 머리가 앞): 둥근 머리 (흰빛 눈구멍 둘은 그림자) · 뒤로 흩어지는 꼬리 (비침 · 빛깔)"""
    m = fx.Model("soul", tex=64, cell=32)
    a = m.atlas
    skin = a.swatch("skin", fx.p_flat, lum=0.95)
    wisp = a.swatch("wisp", p_wisp, 2, 1)
    m.part("head", (0, 0.15, 0))
    sphere(m, (0, 0.15, 0), 0.13, fx.GLOW, skin, segs=10, rings=8)
    for s in (-1, 1):
        sphere(m, (s * 0.045, 0.2, 0.105), 0.03, fx.SHADOW, skin, segs=6, rings=4)
    m.part("tail", (0, 0.1, 0))
    rings = []
    for j, (y, r) in enumerate(((0.12, 0.12), (-0.05, 0.1), (-0.25, 0.07), (-0.45, 0.035))):
        rings.append([v3(r * math.cos(2 * math.pi * k / 8) + 0.03 * math.sin(j * 1.7), y, r * math.sin(2 * math.pi * k / 8)) for k in range(8)])
    rings.append([v3(0.06, -0.62, 0)] * 8)
    m.loft(rings[::-1], fx.TINT, wisp, cap0=False, cap1=False, smooth=True)
    m.part("main")
    m.anims["wail"] = {"len": 0.6, "tracks": {"tail": {"rot": [[0, 0, 0, -12], [0.5, 0, 0, 12], [1, 0, 0, -12]]}, "head": {"scale": [[0, 1], [0.5, 1.08], [1, 1]]}}}
    m.add_glow(0, 0.15, 0, 0.28).add_glow(0, -0.2, 0, 0.18)
    return m


def storm_cloud():
    """먹구름 (가운데 밑이 원점, 위 +Y — 너비 2.4): 부푼 덩이 아홉 (그림자 · 짙은 빛깔 — 아래는 어둡게) · 속에서 번쩍이는 빛 (flash — 속빛 덩이, 되풀이로 커졌다 작아짐)"""
    m = fx.Model("storm_cloud", tex=64, cell=32)
    a = m.atlas
    cloud = a.swatch("cloud", p_cloud, 2, 2)
    m.part("body", (0, 0.3, 0))
    blobs = [(0, 0.45, 0, 0.62, 0.42), (0.75, 0.35, 0.15, 0.5, 0.34), (-0.7, 0.38, -0.1, 0.52, 0.36), (0.3, 0.32, -0.6, 0.48, 0.32), (-0.25, 0.3, 0.6, 0.46, 0.3),
             (1.15, 0.25, -0.35, 0.36, 0.26), (-1.1, 0.25, 0.4, 0.38, 0.26), (0.55, 0.6, 0.45, 0.4, 0.3), (-0.5, 0.62, -0.5, 0.38, 0.28)]
    for i, (x, y, z, r, ry) in enumerate(blobs):
        sphere(m, (x, y, z), r, fx.SHADOW if i % 3 else fx.DARK, cloud, segs=10, rings=6, ry=ry)
    m.part("flash", (0.1, 0.35, 0.05))
    sphere(m, (0.1, 0.35, 0.05), 0.35, fx.GLOW, cloud, segs=8, rings=6, ry=0.25)
    m.part("main")
    m.anims["rumble"] = {"len": 1.0, "tracks": {"flash": {"scale": [[0, 0.3], [0.12, 1.3], [0.25, 0.4], [0.55, 0.3], [0.62, 1.1], [0.7, 0.3], [1, 0.3]]},
                                                 "body": {"scale": [[0, 1], [0.5, 1.03], [1, 1]]}}}
    m.add_glow(0.1, 0.3, 0.05, 0.9)
    return m


# ------------------------------------------------------------ 0.48.0 둘째 묶음: 불기둥 · 빛기둥 · 검기 · 얼음 껍질 · 그림자 분신 · 바람 정령


def p_flame(u, v, rnd):
    """불꽃 혀 (v = 0 끝 → 1 밑동, u = 폭): 밑이 넓고 끝이 뾰족 · 가장자리가 일렁임 · 가운데가 밝음"""
    w = 0.08 + 0.92 * (v ** 0.7)
    wob = 0.08 * math.sin(v * 17 + u * 3) + 0.05 * math.sin(v * 31)
    c = abs(u - 0.5) * 2
    if c > w + wob:
        return 0.0, 0
    k = c / max(0.05, w + wob)
    lum = 1.0 - 0.45 * k ** 1.5 + fx.noise(rnd, 0.03)
    a = min(1.0, (1 - k) * 2.4) * min(1.0, 0.35 + v * 1.6)
    return lum, a


def p_streak(u, v, rnd):
    """빛기둥 결 (v = 0 위 → 1 아래, u = 둘레): 가는 세로 빛살 (빛살 사이는 거의 투명) · 위로 갈수록 투명 — 0.48.0 사진첩: 대리석 기둥처럼 보여 옅게"""
    s = abs(math.sin(u * math.pi * 9 + math.sin(v * 7) * 0.6)) ** 3
    a = (0.06 + 0.3 * s) * min(1.0, 0.1 + v * 1.2) ** 1.4
    return 0.9 + 0.1 * s + fx.noise(rnd, 0.02), a


def p_beam_rim(u, v, rnd):
    """빛기둥 밑 테 · 떠 도는 고리 (반투명 — 사진첩: 불투명한 밑 테가 크림빛 도넛처럼 보였음)"""
    return 1.0, 0.45 + 0.15 * abs(math.sin(u * math.pi * 8))


def p_beam_core(u, v, rnd):
    """빛기둥 심지 (v = 0 위 → 1 아래): 밝고 반투명 · 위로 갈수록 투명"""
    return 1.0, (0.18 + 0.2 * abs(math.sin(u * math.pi * 3))) * min(1.0, 0.1 + v * 1.2) ** 1.2


def p_wave(u, v, rnd):
    """검기 (u = 둘레, v = 안 → 바깥): 바깥 날로 갈수록 밝고 불투명 · 결"""
    s = 0.5 + 0.5 * math.sin(u * 40 + v * 3)
    return 0.72 + 0.2 * v + 0.08 * s + fx.noise(rnd, 0.02), 0.55 + 0.45 * v


def p_ice(u, v, rnd):
    """얼음 결정 면 (u, v): 비스듬한 빛줄기 · 안쪽 금"""
    streak = 0.25 if abs((u * 2.2 + v * 1.1) % 1 - 0.5) < 0.06 else 0.0
    crack = 0.18 if abs((u * 4.0 - v * 2.7 + math.sin(v * 9) * 0.2) % 1 - 0.5) < 0.02 else 0.0
    return 0.72 + streak + crack + 0.12 * (1 - v) + fx.noise(rnd, 0.025), 0.82


def p_shade(u, v, rnd):
    """그림자 몸 (거의 검고 결이 흐름)"""
    return 0.55 + 0.25 * math.sin(u * 13 + v * 21) * math.sin(v * 9) + fx.noise(rnd, 0.05), 1


def p_cloak(u, v, rnd):
    """그림자 옷자락 (아래 v=1 로 갈수록 투명 · 찢긴 끝)"""
    tear = 0.5 + 0.5 * math.sin(u * 2 * math.pi * 7 + 1.3)
    a = max(0.0, 1 - v * (1.05 + 0.5 * tear))
    return 0.55 + 0.2 * math.sin(u * 17 + v * 4) + fx.noise(rnd, 0.04), a


def p_leaf(u, v, rnd):
    """잎 (가운데 잎맥 · 끝이 뾰족)"""
    c = abs(u - 0.5) * 2
    w = math.sin(math.pi * min(1.0, v * 1.05)) ** 0.8
    if c > w:
        return 0.0, 0
    vein = 0.2 if c < 0.06 else 0.0
    return 0.7 + vein + 0.1 * (1 - c) + fx.noise(rnd, 0.03), 1


def ribbon(m, path, widths, normal_fn, uv, mat, flip_v=True):
    """곡선을 따라가는 두 면 띠 (점마다 폭 · 옆 방향 = normal_fn(i, 점, 접선)) — uv: 위 (끝) 가 v0"""
    u0, v0, u1, v1 = uv
    n = len(path)
    pts = [v3(p) for p in path]
    for i in range(n - 1):
        a, b = pts[i], pts[i + 1]
        ta = unit(pts[min(n - 1, i + 1)] - pts[max(0, i - 1)])
        tb = unit(pts[min(n - 1, i + 2)] - pts[i])
        sa = unit(normal_fn(i, a, ta)) * widths[i] / 2
        sb = unit(normal_fn(i + 1, b, tb)) * widths[i + 1] / 2
        va = v1 - (v1 - v0) * i / (n - 1) if flip_v else v0 + (v1 - v0) * i / (n - 1)
        vb = v1 - (v1 - v0) * (i + 1) / (n - 1) if flip_v else v0 + (v1 - v0) * (i + 1) / (n - 1)
        m.quad([a - sa, a + sa, b + sb, b - sb], [(u0, va), (u1, va), (u1, vb), (u0, vb)], mat, double=True)


def p_fire(u, v, rnd):
    """불꽃 몸 (v = 0 위 → 1 밑, u = 둘레): 세로로 일렁이는 결 · 위로 갈수록 투명"""
    s = 0.5 + 0.5 * math.sin(u * math.pi * 2 * 4 + v * 7 + math.sin(u * 13) * 0.8)
    a = min(1.0, max(0.0, v * 1.5 - 0.1)) * (0.45 + 0.4 * s)
    return 0.8 + 0.2 * s + fx.noise(rnd, 0.03), a


def flame():
    """불기둥 (밑 가운데가 원점, 위 +Y — 높이 1 · 폭 약 0.7): 흰 심지 (뾰족한 관 — 위로 갈수록 투명) · 빛깔 몸 (비틀린 관) · 둘레에 비틀려 솟는 불꽃 혀 일곱
    (tongues — flicker 로 일렁임 · rise 로 솟음). stretch 로 높이만 길게 (불기둥 · 용암 분수)"""
    m = fx.Model("flame", tex=64, cell=32)
    m.emissive = True
    a = m.atlas
    tongue = a.swatch("tongue", p_flame)
    fire = a.swatch("fire", p_fire)
    qs = [i / 6 for i in range(7)]
    path = [v3(0.02 * math.sin(q * 5), q * 0.55, 0.02 * math.cos(q * 4)) for q in qs]
    tube(m, path, [0.09 * (1 - q) ** 0.7 if q < 1 else 0.0 for q in qs], 7, fx.WHITE, fire, cap0=False, cap1=False)
    qs = [i / 8 for i in range(9)]
    path = [v3(0.04 * math.sin(q * 6 + 1), q * 0.82, 0.04 * math.cos(q * 5)) for q in qs]
    tube(m, path, [0.17 * math.sin(math.pi * min(1, 0.4 + q * 0.6)) ** 0.9 if q < 1 else 0.0 for q in qs], 8, fx.GLOW, fire, cap0=False, cap1=False)
    m.part("tongues", (0, 0, 0))
    for k in range(7):
        th0 = 2 * math.pi * k / 7
        h = 0.7 + 0.32 * ((k * 3) % 7) / 6
        twist = 1.3 + 0.35 * (k % 3)
        pts = []
        for i in range(9):
            q = i / 8
            r = 0.13 + 0.12 * math.sin(math.pi * q * 0.8) - 0.1 * q
            th = th0 + twist * q
            pts.append(v3(r * math.cos(th), h * q, r * math.sin(th)))
        widths = [0.42 * (1 - q) ** 0.65 + 0.02 for q in qs]

        def side(i, p, t, th0=th0, twist=twist):
            th = th0 + twist * i / 8
            return v3(-math.sin(th), 0, math.cos(th))
        ribbon(m, pts, widths, side, tongue, fx.GLOW if k % 2 == 0 else fx.TINT)
    m.part("main")
    m.anims["flicker"] = {"len": 0.6, "tracks": {"tongues": {"rot": [[0, 0, 0, 0], [0.5, 0, 40, 0], [1, 0, 80, 0]], "scale": [[0, 1], [0.3, 1.08], [0.7, 0.94],
                                                                                                                         [1, 1]]}}}
    m.anims["rise"] = {"len": 0.25, "tracks": {"tongues": {"scale": [[0, 0.1], [0.7, 1.15], [1, 1]]}}}
    m.add_glow(0, 0.25, 0, 0.45).add_glow(0, 0.6, 0, 0.3)
    return m


def pillar():
    """빛기둥 (밑 가운데가 원점, 위 +Y — 높이 1 · 반지름 0.5): 빛깔 겉 (세로 빛살 · 위로 갈수록 투명 — 안팎 두 면) · 흰 심지 · 밑 테 · 떠 도는 고리 셋 (rings — spin).
    stretch 로 높이만 길게 (하늘에서 내리꽂는 빛 · 성역)"""
    m = fx.Model("pillar", tex=64, cell=32)
    m.emissive = True
    a = m.atlas
    streak = a.swatch("streak", p_streak)
    core = a.swatch("core", p_beam_core)
    rim = a.swatch("rim", p_beam_rim)
    n = 16
    for r, mat, uv in ((0.5, fx.GLOW, streak), (0.17, fx.WHITE, core)):
        rings = [[v3(r * math.cos(2 * math.pi * k / n), y, r * math.sin(2 * math.pi * k / n)) for k in range(n)] for y in (0.0, 0.5, 1.0)]
        m.loft(rings, mat, uv, cap0=False, cap1=False, smooth=True)
        m.loft(rings[::-1], mat, uv, cap0=False, cap1=False, smooth=True)
    m.torus(0.56, 0.04, 20, 4, fx.GLOW, rim, center=(0, 0.02, 0))
    m.part("rings", (0, 0, 0))
    for y, r in ((0.22, 0.66), (0.5, 0.62), (0.78, 0.58)):
        m.torus(r, 0.018, 22, 3, fx.WHITE, rim, center=(0, y, 0))
    m.part("main")
    m.anims["spin"] = {"len": 1.5, "tracks": {"rings": {"rot": [[0, 0, 0, 0], [0.5, 0, 180, 0], [1, 0, 360, 0]]}}}
    m.anims["rise"] = {"len": 0.3, "tracks": {"rings": {"scale": [[0, 0.2], [0.7, 1.2], [1, 1]]}}}
    m.add_glow(0, 0.05, 0, 0.7).add_glow(0, 0.5, 0, 0.5)
    return m


def crescent_wave():
    """검기 (가운데가 원점, 나아가는 쪽 +Y — 볼록한 쪽이 앞, 너비 2 (±1) · 두께 0.12): 빛깔 초승달 띠 (바깥 날 흰빛) + 흰 심지 띠 + 뒤로 끌리는 빛 꼬리 셋.
    face (+Z) 를 위로 두면 땅과 나란히 날아감"""
    m = fx.Model("crescent_wave", tex=64, cell=32)
    m.emissive = True
    a = m.atlas
    face = a.swatch("face", p_wave)
    edge = a.swatch("edge", fx.p_edge)
    trail = a.swatch("trail", p_flame)
    # 반지름 1 의 원에서 20° ~ 160° — 가운데를 원점 가까이 (cy = -0.62)
    fx.arc_band(m, 0.7, 1.0, 18, 162, 20, -0.06, 0.06, fx.GLOW, fx.WHITE, face, edge, cy=-0.62, tip=0.85)
    fx.arc_band(m, 0.84, 0.97, 30, 150, 16, -0.075, 0.075, fx.WHITE, fx.WHITE, edge, edge, cy=-0.62, tip=0.7)
    # 꼬리: 날의 가운데 · 양옆에서 뒤로 (−Y) 가늘어지는 빛 띠 (두 면 — 위아래)
    for ang, ln, w in ((90, 0.5, 0.16), (62, 0.34, 0.1), (118, 0.34, 0.1)):
        t = math.radians(ang)
        p0 = v3(0.86 * math.cos(t), 0.86 * math.sin(t) - 0.62, 0)
        d = unit(v3(math.cos(t), math.sin(t), 0))
        u0, v0, u1, v1 = trail
        s = v3(-d[1], d[0], 0) * w / 2
        p1 = p0 - d * ln
        m.quad([p1 - s * 0.2, p1 + s * 0.2, p0 + s, p0 - s], [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], fx.GLOW, double=True)
    m.add_glow(0, 0.3, 0, 0.6).add_glow(-0.6, 0.0, 0, 0.35).add_glow(0.6, 0.0, 0, 0.35)
    return m


def ice_shell():
    """얼음 껍질 (밑 가운데가 원점, 위 +Y — 적 하나를 감싸는 높이 약 1.7 · 반지름 0.5): 바깥으로 기울어 솟은 육각 결정 아홉 + 작은 결정 다섯 (빛깔 얼음 ·
    끝 흰빛) — crystals (form 으로 솟음 · crack 으로 떨림)"""
    m = fx.Model("ice_shell", tex=64, cell=32)
    a = m.atlas
    ice = a.swatch("ice", p_ice)
    tip = a.swatch("tip", fx.p_edge)
    m.part("crystals", (0, 0, 0))
    for k in range(9):
        th = 2 * math.pi * k / 9 + 0.2
        d = v3(math.cos(th), 0, math.sin(th))
        h = 1.15 + 0.45 * ((k * 4) % 7) / 6
        base = d * 0.42
        top = d * (0.52 + 0.1 * (k % 2)) + v3(0, h, 0) - d * 0.18
        r = 0.11 + 0.03 * (k % 3)
        pts = [base, base + (top - base) * 0.78, top]
        tube(m, pts, [r, r * 0.9, 0.0], 6, fx.TINT, ice, cap1=False, smooth=False)
        spike(m, top - (top - base) * 0.06, top + unit(top - base) * 0.1, r * 0.35, 6, fx.WHITE, tip, segs=1)
    for k in range(5):
        th = 2 * math.pi * k / 5 + 0.9
        d = v3(math.cos(th), 0, math.sin(th))
        base = d * 0.62
        top = d * 0.9 + v3(0, 0.45 + 0.12 * (k % 2), 0)
        tube(m, [base, base + (top - base) * 0.7, top], [0.08, 0.07, 0.0], 6, fx.TINT, ice, cap1=False, smooth=False)
    m.part("main")
    m.anims["form"] = {"len": 0.25, "tracks": {"crystals": {"scale": [[0, 0.1], [0.7, 1.06], [1, 1]]}}}
    m.anims["crack"] = {"len": 0.3, "tracks": {"crystals": {"rot": [[0, 0, 0, 0], [0.25, 2, 4, -2], [0.5, -2, -3, 2], [0.75, 2, 2, -1], [1, 0, 0, 0]]}}}
    m.add_glow(0, 0.9, 0, 0.55)
    return m


def shade():
    """그림자 분신 (발밑 가운데가 원점, 위 +Y — 키 1.8, 앞 +Z — 오른쪽이 −X): 두건 쓴 날렵한 몸 (그림자 · 결) · 찢긴 옷자락 (아래로 갈수록 투명) ·
    두건 속 빛나는 눈 (속빛) · 양손의 단검 (날 흰빛). 팔 arm_r (어깨 (−0.24, 1.42)) · arm_l (어깨 (0.24, 1.42)): lunge (오른손 찌르기) · cross (두 팔로 X 베기)"""
    m = fx.Model("shade", tex=64, cell=32)
    a = m.atlas
    body = a.swatch("body", p_shade)
    cloak = a.swatch("cloak", p_cloak)
    eye = a.swatch("eye", fx.p_gem)
    edge = a.swatch("edge", fx.p_edge)
    # 다리 (앞뒤로 벌린 자세)
    for s, fwd in ((-1, 0.12), (1, -0.1)):
        tube(m, [v3(s * 0.1, 0.95, 0), v3(s * 0.12, 0.5, fwd * 0.6), v3(s * 0.12, 0.05, fwd)], [0.08, 0.065, 0.05], 6, fx.SHADOW, body)
    # 몸통 (허리 → 어깨 — 조금 앞으로 숙임)
    torso = [(0.9, 0.17, 0.12, 0.0), (1.15, 0.2, 0.13, 0.03), (1.4, 0.24, 0.14, 0.06), (1.5, 0.16, 0.12, 0.07)]
    m.loft([[v3(x, y, z + dz) for x, z in ellipse(rx, rz, 10)] for y, rx, rz, dz in torso], fx.SHADOW, body, smooth=True)
    # 옷자락 (허리에서 뒤 아래로 펄럭임 — 두 면)
    rings = []
    for y, r, back in ((1.0, 0.2, 0.0), (0.7, 0.26, 0.1), (0.4, 0.3, 0.22), (0.12, 0.32, 0.36)):
        rings.append([v3(r * math.cos(math.pi * (0.15 + 0.7 * k / 9)), y, -back - r * 0.6 * math.sin(math.pi * (0.15 + 0.7 * k / 9))) for k in range(10)])
    m.loft(rings, fx.SHADOW, cloak, close=False, cap0=False, cap1=False, smooth=True)
    m.loft(rings[::-1], fx.SHADOW, cloak, close=False, cap0=False, cap1=False, smooth=True)
    # 두건 머리 (앞으로 뾰족) · 눈 둘
    m.loft([[v3(x, y, z + 0.08) for x, z in ellipse(rx, rz, 8)] for y, rx, rz in ((1.5, 0.12, 0.12), (1.62, 0.14, 0.15), (1.76, 0.13, 0.14), (1.86, 0.06, 0.08))],
           fx.SHADOW, body, smooth=True)
    for s in (-1, 1):
        m.box((s * 0.05, 1.68, 0.225), (0.05, 0.022, 0.02), fx.GLOW, eye)
    # 팔 + 단검 (어깨 축)
    for name, s in (("arm_r", -1), ("arm_l", 1)):
        m.part(name, (s * 0.24, 1.42, 0.05))
        sh = v3(s * 0.24, 1.42, 0.05)
        hand = v3(s * 0.3, 1.1, 0.3)
        tube(m, [sh, v3(s * 0.3, 1.25, 0.16), hand], [0.06, 0.055, 0.045], 6, fx.SHADOW, body)
        # 단검 (손에서 앞 위로 — 날 흰빛 · 속빛 테)
        tip_ = hand + v3(0, 0.08, 0.42)
        spike(m, hand + v3(0, 0.02, 0.05), tip_, 0.045, 4, fx.WHITE, edge, segs=2)
        m.box(tuple(hand + v3(0, 0.02, 0.04)), (0.12, 0.03, 0.03), fx.GLOW, eye)
    m.part("main")
    m.anims["lunge"] = {"len": 0.4, "tracks": {"arm_r": {"rot": [[0, -60, 0, 0], [0.45, 20, 0, 0], [1, 10, 0, 0]]},
                                               "arm_l": {"rot": [[0, 20, 0, 0], [1, 30, 0, 10]]}}}
    m.anims["cross"] = {"len": 0.45, "tracks": {"arm_r": {"rot": [[0, -90, 0, 40], [0.5, 10, 0, -30], [1, 20, 0, -45]]},
                                                "arm_l": {"rot": [[0, -90, 0, -40], [0.5, 10, 0, 30], [1, 20, 0, 45]]}}}
    m.add_glow(0, 1.68, 0.2, 0.22).add_glow(0, 1.1, 0.1, 0.4)
    return m


def sylph():
    """바람 정령 (가운데가 원점, 위 +Y — 키 약 0.6, 앞 +Z): 가녀린 몸 (빛깔) · 빛나는 머리 (흰빛) · 뒤로 흩날리는 잎 머리칼 · 잎 치마 ·
    등의 날개 두 쌍 (wing_r · wing_l — 비치는 막, flap 으로 날갯짓)"""
    m = fx.Model("sylph", tex=64, cell=32)
    a = m.atlas
    skin = a.swatch("skin", fx.p_flat, lum=0.92, k=0.03)
    leaf = a.swatch("leaf", p_leaf)
    wing = a.swatch("wing", p_membrane)
    glow = a.swatch("glow", fx.p_gem)
    # 몸 (허리가 가는 돌림체)
    m.lathe([(0.0, -0.32), (0.03, -0.3), (0.05, -0.12), (0.035, 0.0), (0.06, 0.1), (0.045, 0.16), (0.0, 0.18)], 8, fx.TINT, skin)
    sphere(m, (0, 0.25, 0.01), 0.075, fx.WHITE, glow, segs=10, rings=6)
    # 잎 머리칼 (머리 뒤로 흩날림)
    for k in range(5):
        th = -0.6 + 1.2 * k / 4
        base = v3(0.05 * math.sin(th), 0.29, -0.03)
        d = unit(v3(math.sin(th) * 0.5, 0.25, -1))
        u0, v0, u1, v1 = leaf
        s = unit(np.cross(d, v3(0, 1, 0))) * 0.035
        tip_ = base + d * (0.2 + 0.04 * (k % 2))
        m.quad([base - s, base + s, tip_ + s * 0.2, tip_ - s * 0.2], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.GLOW, double=True)
    # 잎 치마 (허리에서 아래로 여섯 장)
    for k in range(6):
        th = 2 * math.pi * k / 6
        d = v3(math.cos(th), 0, math.sin(th))
        top = d * 0.045 + v3(0, -0.02, 0)
        bot = d * 0.13 + v3(0, -0.2, 0)
        s = v3(-d[2], 0, d[0]) * 0.05
        u0, v0, u1, v1 = leaf
        m.quad([bot - s, bot + s, top + s * 0.4, top - s * 0.4], [(u0, v1), (u1, v1), (u1, v0), (u0, v0)], fx.TINT, double=True)
    # 날개 두 쌍 (등 −Z 에서 옆 · 위로 — 위 날개가 크고 아래 날개는 작음)
    for name, s in (("wing_r", -1), ("wing_l", 1)):
        m.part(name, (s * 0.02, 0.1, -0.05))
        for up, ln, wd in ((1, 0.34, 0.16), (-1, 0.22, 0.11)):
            root_ = v3(s * 0.02, 0.1 + 0.02 * up, -0.05)
            tip_ = root_ + v3(s * ln, up * ln * 0.55, -0.08)
            mid = (root_ + tip_) / 2 + v3(0, up * wd * 0.6, -0.02)
            mid2 = (root_ + tip_) / 2 - v3(0, up * wd * 0.35, 0)
            quad2(m, [root_, mid2, tip_, mid], wing, fx.GLOW)
    m.part("main")
    m.anims["flap"] = {"len": 0.3, "tracks": {"wing_r": {"rot": [[0, 0, -35, 0], [0.5, 0, 25, 0], [1, 0, -35, 0]]},
                                              "wing_l": {"rot": [[0, 0, 35, 0], [0.5, 0, -25, 0], [1, 0, 35, 0]]}}}
    m.add_glow(0, 0.25, 0, 0.2).add_glow(0, 0.0, 0, 0.3)
    return m
