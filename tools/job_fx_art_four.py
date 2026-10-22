#!/usr/bin/env python3
"""0.30.0 직업 스킬 연출 그림 — 성기사 · 사제 · 수호기사 (메이플식 2D 그림, tools/job_fx_art.py 와 같은 꼴: 흰색 = 알파 · 빛 장면들 다음에 심지 장면들).
tools/job_fx_art.py 가 함께 부름 (python3 tools/job_fx_art.py [--sheet <폴더>]).

- rfx_jthunder  내리치는 벼락 (세워 카메라 쪽으로 돎, 128×512 × 6장): 위에서 내려오는 줄기 → 땅에 닿아 번쩍 · 가지 → 깜빡임 → 사라짐 (아래 끝이 땅)
- rfx_jvolt     땅에 눕는 번개 고리 (면이 위, 192×192 × 8장): 고리 (반 크기의 0.8) 를 따라 지직거리는 번개 · 튀는 불꽃 — 안쪽 무늬 없음
- rfx_jmaul     번개 대망치 (세워 카메라 쪽으로 돎, 192×256 × 4장 — 이어 돌림): 머리가 아래인 큰 망치 · 망치를 타고 흐르는 번개 줄 · 튀는 불꽃
- rfx_jwing     빛의 날개 (면이 방향 쪽 · 세로는 위, 256×192 × 4장 — 이어 돌림): 등에서 양옆 위로 펼친 깃털 날개가 천천히 날갯짓
- rfx_jstun     기절 별 (카메라를 봄, 128×64 × 8장 — 이어 돌림): 머리 위 타원을 따라 도는 별 다섯 (앞은 크고 밝게)
- rfx_jjudge    심판 표시 (카메라를 봄, 64×64 × 4장 — 이어 돌림): 작은 번개 문장이 깜빡임
- rfx_jheal     회복 십자 (카메라를 봄, 128×128 × 6장): 십자가 피어나 반짝이며 조금 떠오르고 사라짐
- rfx_jnote     음표 (카메라를 봄, 64×64 × 4칸 — 칸마다 다른 음표 한 장): 4분 · 8분 · 이어진 8분 둘 · 이어진 16분 둘
- rfx_jstigma   성흔 (카메라를 봄, 128×128 × 4장 — 이어 돌림): 빛나는 십자가와 뒤에서 천천히 도는 빛살
- rfx_jbell     큰 종 (세워 카메라 쪽으로 돎, 192×192 × 8장): 0 = 가만히 매달림, 1 ~ 7 = 크게 흔들려 울리고 양옆으로 소리 물결
- rfx_jchain    빛의 사슬 (방향으로 누움, 256×32 × 4장 — 이어 돌림): 고리가 이어진 사슬 · 따라 흐르는 빛
- rfx_jbuckler  둥근 방패 (카메라를 봄, 128×128 × 4장 — 이어 돌림): 테 · 판자 · 가운데 돌기 · 테를 도는 반짝임 (던진 방패 — 화면에서 돎)
- rfx_jaegis    빛의 방패 (세워 카메라 쪽으로 돎, 192×256 × 6장): 0 ~ 1 = 테가 그려지며 나타나 번쩍, 2 ~ 3 = 버팀 (빛이 비스듬히 지나감), 4 ~ 5 = 금이 가며 조각나 사라짐
모두 이 스크립트가 처음부터 그림 (다른 게임 그림을 쓰지 않음). 룬 문양 · 마법진은 그리지 않음.
"""
import math
import os
import random
import sys

from PIL import ImageChops

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from job_fx_art import SS, Cell, blur, ease_out, star  # noqa: E402
from job_fx_art_more import glowify, jagged, rot, two_sheets  # noqa: E402

TAU = math.tau


def star5(m, cx, cy, r, rot_deg=0.0, v=255, inner=0.45):
    """다섯 뿔 별"""
    pts = []
    for i in range(10):
        a = math.radians(rot_deg - 90 + i * 36)
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    m.poly(pts, v)


def round_line(m, pts, w, v=255):
    """끝이 둥근 줄"""
    m.line(pts, w, v)
    for x, y in (pts[0], pts[-1]):
        m.disc(x, y, w / 2, v)


# ------------------------------------------------------------ 내리치는 벼락 (rfx_jthunder — 아래 끝이 땅)

def bolt_path(seed, W, H):
    """위 → 아래 번개 줄기 (가운데 점 밀기 여섯 번 — 크고 작은 꺾임이 섞임, 아래 끝은 가운데)"""
    rnd = random.Random(seed)
    pts = [(W * 0.5 + rnd.uniform(-0.1, 0.1) * W, 0.0), (W * 0.5, H - 4.0)]
    disp = W * 0.34
    for _ in range(6):
        new = [pts[0]]
        for a, b in zip(pts, pts[1:]):
            mx = (a[0] + b[0]) / 2 + rnd.choice((-1, 1)) * rnd.uniform(0.35, 1.0) * disp
            my = (a[1] + b[1]) / 2 + rnd.uniform(-0.15, 0.15) * (b[1] - a[1])
            new += [(min(W * 0.86, max(W * 0.14, mx)), my), b]
        pts = new
        disp *= 0.58
    return pts


def bolt_branches(seed, pts, W, H):
    rnd = random.Random(seed + 500)
    out = []
    n = len(pts)
    for frac in (0.14, 0.3, 0.46, 0.62, 0.78):
        x, y = pts[int(n * frac)]
        side = rnd.choice((-1, 1))
        ang = math.pi / 2 + side * rnd.uniform(0.5, 1.0)
        out.append(jagged(rnd, x, y, ang, H * rnd.uniform(0.07, 0.14), 7, 0.5))
    return out


def thunder_frame(f, frames=6, W=128, H=512, core=False):
    m = Cell(W, H)
    main = bolt_path(11, W, H)
    alt = bolt_path(23, W, H)
    wk = [0.75, 1.15, 1.0, 0.8, 0.62, 0.42][f]
    v = [200, 255, 255, 225, 190, 110][f]
    base = W * (0.085 if not core else 0.032) * wk
    path = alt if f == 3 else main
    if f == 0:
        path = path[: int(len(path) * 0.55) + 1]
    m.line(path, base, v)
    if f in (2, 3, 4):
        for br in bolt_branches(11 if f != 3 else 23, path, W, H):
            m.line(br, base * 0.55, v * 0.9)
    if f in (1, 2):
        # 땅에 닿은 번쩍 · 위의 구름 빛
        bx, by = path[-1]
        m.disc(bx, by - W * 0.04, W * (0.3 if f == 1 else 0.2) * (1 if not core else 0.6), 255)
        star(m, bx, by - W * 0.05, W * (0.48 if f == 1 else 0.36) * (1 if not core else 0.75), 0.14, 0, 255)
        if not core:
            m.disc(path[0][0], 0, W * 0.3, 150)
    if f == 0 and not core:
        m.disc(path[-1][0], path[-1][1], W * 0.08, 200)
    out = m.out()
    return glowify(out, 0.75, 6, 1.35) if not core else out


# ------------------------------------------------------------ 번개 고리 (rfx_jvolt — 땅에 눕힘)

def volt_frame(f, frames=8, S=192, core=False):
    m = Cell(S, S)
    c = S / 2
    R = 0.4 * S
    env = [0.55, 1.0, 1.0, 0.95, 0.9, 0.85, 0.6, 0.3][f]
    rnd = random.Random(300 + f)
    n = 84
    pts = []
    for i in range(n + 1):
        a = i / n * TAU
        rr = R + (rnd.uniform(-0.028, 0.028) * S if i < n else 0)
        if i == n:
            pts.append(pts[0])
        else:
            pts.append((c + math.cos(a) * rr, c + math.sin(a) * rr))
    w = S * (0.032 if not core else 0.012) * (0.7 + 0.3 * env)
    m.line(pts, w, 255 * env)
    if f >= 1:
        # 고리에서 튀어나가는 짧은 번개 (안 · 밖)
        for k in range(7):
            a = rnd.uniform(0, TAU)
            out_ = rnd.random() < 0.6
            x0, y0 = c + math.cos(a) * R, c + math.sin(a) * R
            L = S * rnd.uniform(0.05, 0.11)
            arc = jagged(rnd, x0, y0, a if out_ else a + math.pi, L, 3, 0.55)
            m.line(arc, w * 0.7, 235 * env)
        for k in range(5):
            a = rnd.uniform(0, TAU)
            star(m, c + math.cos(a) * R, c + math.sin(a) * R, S * 0.04 * env * (1 if not core else 0.7), 0.2, rnd.uniform(0, 90), 255 * env)
    out = m.out()
    return glowify(out, 0.75, 5, 1.3) if not core else out


# ------------------------------------------------------------ 번개 대망치 (rfx_jmaul — 머리가 아래, 이어 돌림)

def maul_outline(W, H):
    """망치 머리 (아래) — 가운데 띠 · 양끝 판이 조금 큼"""
    cx = W / 2
    y0, y1 = H * 0.64, H * 0.92
    hw = W * 0.4
    cut = W * 0.05
    face = H * 0.03
    return [(cx - hw + cut, y0 - face), (cx - hw + W * 0.12, y0 - face), (cx - hw + W * 0.12, y0), (cx + hw - W * 0.12, y0), (cx + hw - W * 0.12, y0 - face),
            (cx + hw - cut, y0 - face), (cx + hw, y0 - face + cut), (cx + hw, y1 + face - cut), (cx + hw - cut, y1 + face), (cx + hw - W * 0.12, y1 + face),
            (cx + hw - W * 0.12, y1), (cx - hw + W * 0.12, y1), (cx - hw + W * 0.12, y1 + face), (cx - hw + cut, y1 + face), (cx - hw, y1 + face - cut),
            (cx - hw, y0 - face + cut)]


def maul_frame(f, frames=4, W=192, H=256, core=False):
    m = Cell(W, H)
    cx = W / 2
    head = maul_outline(W, H)
    # 자루 · 자루 끝 구슬
    if not core:
        m.poly(head, 95)
        m.line([(cx, H * 0.06), (cx, H * 0.66)], W * 0.075, 120)
        m.disc(cx, H * 0.06, W * 0.06, 200)
    m.line(head + [head[0]], W * (0.03 if not core else 0.012), 255)
    m.line([(cx - W * 0.035, H * 0.08), (cx - W * 0.035, H * 0.62)], W * (0.014 if not core else 0.007), 230)
    m.line([(cx + W * 0.035, H * 0.08), (cx + W * 0.035, H * 0.62)], W * (0.014 if not core else 0.007), 230)
    m.ring(cx, H * 0.06, W * 0.05, W * (0.016 if not core else 0.008), 255)
    # 가운데 띠
    m.line([(cx - W * 0.1, H * 0.66), (cx - W * 0.1, H * 0.9)], W * (0.016 if not core else 0.008), 230)
    m.line([(cx + W * 0.1, H * 0.66), (cx + W * 0.1, H * 0.9)], W * (0.016 if not core else 0.008), 230)
    # 망치를 타고 흐르는 번개 (장마다 다름) · 바깥으로 튀는 불꽃
    rnd = random.Random(70 + f)
    for k in range(3):
        x0 = cx - W * 0.36 + rnd.uniform(0, W * 0.2)
        y0 = rnd.uniform(H * 0.68, H * 0.88)
        pts = jagged(rnd, x0, y0, rnd.uniform(-0.3, 0.3), W * rnd.uniform(0.35, 0.6), 5, 0.6)
        pts = [(min(cx + W * 0.38, max(cx - W * 0.38, x)), min(H * 0.9, max(H * 0.66, y))) for x, y in pts]
        m.line(pts, W * (0.014 if not core else 0.007), 255)
    pts = jagged(rnd, cx, H * 0.1, math.pi / 2, H * 0.52, 7, 0.5)
    pts = [(min(cx + W * 0.04, max(cx - W * 0.04, x)), y) for x, y in pts]
    m.line(pts, W * (0.012 if not core else 0.006), 255)
    for k in range(5):
        side = rnd.choice((-1, 1))
        y = rnd.uniform(H * 0.62, H * 0.95)
        x = cx + side * W * 0.4
        arc = jagged(rnd, x, y, (0 if side > 0 else math.pi) + rnd.uniform(-0.8, 0.8), W * rnd.uniform(0.06, 0.1), 3, 0.6)
        m.line(arc, W * (0.012 if not core else 0.006), 240)
    star(m, cx + rnd.uniform(-W * 0.3, W * 0.3), H * rnd.uniform(0.66, 0.9), W * 0.07, 0.18, rnd.uniform(0, 90), 255)
    out = m.out()
    return glowify(out, 0.78, 5, 1.25) if not core else out


# ------------------------------------------------------------ 빛의 날개 (rfx_jwing — 이어 돌림)

def feather(m, x0, y0, ang, L, w, v):
    """깃털 하나: (x0, y0) 에서 ang 쪽으로 길이 L · 폭 w (끝이 둥글게 가늘어짐)"""
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    left, right = [], []
    for i in range(10):
        t = i / 9
        ww = w * (math.sin(math.pi * (0.12 + 0.88 * t)) ** 0.6) * (1 - 0.35 * t)
        left.append((x0 + ux * L * t + px * ww, y0 + uy * L * t + py * ww))
        right.append((x0 + ux * L * t - px * ww, y0 + uy * L * t - py * ww))
    m.poly(left + right[::-1], v)


def wing_frame(f, frames=4, W=256, H=192, core=False):
    m = Cell(W, H)
    lift = [0.0, 0.5, 1.0, 0.5][f]
    for side in (-1, 1):
        root = (W / 2 + side * W * 0.035, H * 0.6)
        # 날개 뼈: 뿌리 → 팔꿈치 → 끝 (날갯짓에 따라 끝이 오르내림)
        elbow = (W / 2 + side * W * 0.22, H * (0.34 - 0.04 * lift))
        tip = (W / 2 + side * W * 0.47, H * (0.12 - 0.06 * lift + 0.04))
        bone = []
        for i in range(9):
            t = i / 8
            if t <= 0.5:
                q = t / 0.5
                bone.append((root[0] + (elbow[0] - root[0]) * q, root[1] + (elbow[1] - root[1]) * q))
            else:
                q = (t - 0.5) / 0.5
                bone.append((elbow[0] + (tip[0] - elbow[0]) * q, elbow[1] + (tip[1] - elbow[1]) * q))
        # 깃털: 뼈를 따라 아래 (바깥쪽으로 기울게) — 끝으로 갈수록 길게
        for i in range(1, 9):
            t = i / 8
            x, y = bone[i]
            L = H * (0.2 + 0.42 * t ** 1.2)
            ang = math.pi / 2 - side * (0.15 + 0.85 * t)  # 아래에서 바깥쪽으로 기욺
            feather(m, x, y, ang, L, W * (0.03 if not core else 0.012) * (1 + 0.4 * t), 200 if not core else 255)
        # 덮깃 (짧은 줄 — 뼈 바로 아래)
        for i in range(1, 8):
            t = i / 8
            x, y = bone[i]
            feather(m, x, y + H * 0.01, math.pi / 2 - side * (0.3 + 0.6 * t), H * 0.12, W * (0.022 if not core else 0.009), 230 if not core else 255)
        m.line(bone, W * (0.02 if not core else 0.008), 255)
    out = m.out()
    return glowify(out, 0.7, 5, 1.2) if not core else out


# ------------------------------------------------------------ 기절 별 (rfx_jstun — 이어 돌림)

def stun_frame(f, frames=8, W=128, H=64, core=False):
    m = Cell(W, H)
    cx, cy = W / 2, H / 2
    rx, ry = W * 0.4, H * 0.28
    if not core:
        pts = [(cx + math.cos(a / 40 * TAU) * rx, cy + math.sin(a / 40 * TAU) * ry) for a in range(41)]
        m.line(pts, H * 0.03, 70)
    stars = []
    for i in range(5):
        a = TAU * i / 5 + f * (TAU / 5) / frames
        depth = (math.sin(a) + 1) / 2  # 0 = 뒤 (위) · 1 = 앞 (아래)
        stars.append((depth, a))
    for depth, a in sorted(stars):
        x, y = cx + math.cos(a) * rx, cy + math.sin(a) * ry
        r = H * 0.2 * (0.65 + 0.35 * depth) * (1 if not core else 0.62)
        star5(m, x, y, r, a * 30, 150 + 105 * depth)
    out = m.out()
    return glowify(out, 0.8, 2, 1.1) if not core else out


# ------------------------------------------------------------ 심판 표시 (rfx_jjudge — 이어 돌림)

BOLT = [(0.62, 0.04), (0.24, 0.56), (0.47, 0.56), (0.36, 0.96), (0.78, 0.40), (0.55, 0.40), (0.70, 0.04)]


def judge_frame(f, frames=4, S=64, core=False):
    m = Cell(S, S)
    k = [1.0, 0.92, 1.06, 1.0][f] * (1 if not core else 0.62)
    cx = sum(x for x, _ in BOLT) / len(BOLT)
    cy = sum(y for _, y in BOLT) / len(BOLT)
    pts = [((cx + (x - cx) * k) * S, (cy + (y - cy) * k) * S) for x, y in BOLT]
    m.poly(pts, 255)
    if f in (1, 3):
        rnd = random.Random(90 + f)
        for _ in range(2):
            star(m, rnd.uniform(0.15, 0.85) * S, rnd.uniform(0.15, 0.85) * S, S * 0.12, 0.2, 0, 230)
    out = m.out()
    return glowify(out, 0.8, 2, 1.2) if not core else out


# ------------------------------------------------------------ 회복 십자 (rfx_jheal)

def heal_frame(f, frames=6, S=128, core=False):
    m = Cell(S, S)
    k = [0.45, 1.0, 1.0, 0.95, 0.9, 0.85][f]
    up = [0.0, 0.0, 0.0, 0.04, 0.08, 0.12][f] * S
    v = [210, 255, 255, 230, 160, 80][f]
    c = S / 2
    cy = c - up
    L = S * 0.3 * k
    w = S * (0.15 if not core else 0.06) * k
    round_line(m, [(c, cy - L), (c, cy + L)], w, v)
    round_line(m, [(c - L, cy), (c + L, cy)], w, v)
    if f <= 1:
        star(m, c, cy, S * (0.42 if f == 1 else 0.3) * (1 if not core else 0.7), 0.12, 45, 255)
    rnd = random.Random(140 + f)
    for i in range(4 if f >= 1 else 0):
        a = rnd.uniform(0, TAU)
        rr = S * rnd.uniform(0.3, 0.44)
        star(m, c + math.cos(a) * rr, cy + math.sin(a) * rr - up * 0.5, S * 0.05 * (1 if not core else 0.7), 0.2, 0, v)
    out = m.out()
    return glowify(out, 0.78, 4, 1.25) if not core else out


# ------------------------------------------------------------ 음표 (rfx_jnote — 칸마다 다른 음표)

def note_head(m, cx, cy, rx, ry, v=255):
    pts = []
    for i in range(24):
        a = i / 24 * TAU
        x, y = math.cos(a) * rx, math.sin(a) * ry
        t = math.radians(-22)
        pts.append((cx + x * math.cos(t) - y * math.sin(t), cy + x * math.sin(t) + y * math.cos(t)))
    m.poly(pts, v)


def note_frame(f, frames=4, S=64, core=False):
    m = Cell(S, S)
    k = 1.0 if not core else 0.7
    sw = S * 0.055 * k
    if f in (0, 1):
        # 4분 · 8분 음표: 머리 · 기둥 (· 꼬리)
        hx, hy = S * 0.38, S * 0.76
        note_head(m, hx, hy, S * 0.15 * (1 if not core else 0.8), S * 0.105 * (1 if not core else 0.8))
        sx = hx + S * 0.125
        m.line([(sx, hy - S * 0.03), (sx, S * 0.14)], sw)
        if f == 1:
            pts = [(sx, S * 0.14), (sx + S * 0.14, S * 0.26), (sx + S * 0.17, S * 0.36), (sx + S * 0.12, S * 0.48)]
            m.line(pts, sw * 1.1)
    else:
        # 이어진 둘 (8분 · 16분)
        h1 = (S * 0.27, S * 0.8)
        h2 = (S * 0.68, S * 0.72)
        for hx, hy in (h1, h2):
            note_head(m, hx, hy, S * 0.13 * (1 if not core else 0.8), S * 0.09 * (1 if not core else 0.8))
        s1 = h1[0] + S * 0.11
        s2 = h2[0] + S * 0.11
        m.line([(s1, h1[1] - S * 0.03), (s1, S * 0.22)], sw)
        m.line([(s2, h2[1] - S * 0.03), (s2, S * 0.14)], sw)
        bw = S * 0.08 * k
        m.poly([(s1 - sw / 2, S * 0.22 - bw / 2), (s2 + sw / 2, S * 0.14 - bw / 2), (s2 + sw / 2, S * 0.14 + bw / 2), (s1 - sw / 2, S * 0.22 + bw / 2)])
        if f == 3:
            o = S * 0.12
            m.poly([(s1 - sw / 2, S * 0.22 + o - bw / 2), (s2 + sw / 2, S * 0.14 + o - bw / 2), (s2 + sw / 2, S * 0.14 + o + bw / 2),
                    (s1 - sw / 2, S * 0.22 + o + bw / 2)])
    out = m.out()
    return glowify(out, 0.8, 2, 1.2) if not core else out


# ------------------------------------------------------------ 성흔 (rfx_jstigma — 이어 돌림)

def stigma_frame(f, frames=4, S=128, core=False):
    m = Cell(S, S)
    c = S / 2
    cy = S * 0.4
    # 뒤의 빛살 여덟 (긴 것 · 짧은 것이 번갈아 — 장마다 조금씩 돎)
    for i in range(8):
        a = math.radians(i * 45 + f * 45 / frames)
        L = S * (0.47 if i % 2 == 0 else 0.32)
        ux, uy = math.cos(a), math.sin(a)
        px, py = -uy, ux
        bw = S * (0.045 if not core else 0.018)
        m.poly([(c + px * bw, cy + py * bw), (c + ux * L, cy + uy * L), (c - px * bw, cy - py * bw)], 150 if not core else 120)
    # 십자가 (세로가 긺)
    w = S * (0.11 if not core else 0.045)
    round_line(m, [(c, S * 0.12), (c, S * 0.86)], w, 255)
    round_line(m, [(S * 0.27, S * 0.34), (S * 0.73, S * 0.34)], w, 255)
    star(m, c, S * 0.34, S * (0.16 if not core else 0.11), 0.16, 45, 255)
    out = m.out()
    return glowify(out, 0.78, 4, 1.25) if not core else out


# ------------------------------------------------------------ 큰 종 (rfx_jbell)

BELL_L = [(-0.13, 0.11), (-0.15, 0.2), (-0.16, 0.32), (-0.19, 0.45), (-0.25, 0.56), (-0.31, 0.62), (-0.31, 0.665)]


def bell_frame(f, frames=8, S=192, core=False):
    m = Cell(S, S)
    px_, py_ = S * 0.5, S * 0.1
    ang = math.radians([0, 12, 20, 9, -8, -15, -7, 0][f])

    def tr(x, y):
        xx, yy = x * S, y * S
        return (px_ + xx * math.cos(ang) - yy * math.sin(ang), py_ + xx * math.sin(ang) + yy * math.cos(ang))

    right = [(-x, y) for x, y in BELL_L[::-1]]
    body = [tr(x, y) for x, y in BELL_L + right]
    if not core:
        m.poly(body, 170)
    m.line(body + [body[0]], S * (0.03 if not core else 0.012), 255)
    # 꼭대기 고리 · 허리 띠 · 입술 띠 · 추
    cx_, cy_ = tr(0, 0.04)
    m.ring(cx_, cy_, S * 0.05, S * (0.02 if not core else 0.01), 255)
    m.line([tr(-0.165, 0.36), tr(0.165, 0.36)], S * (0.02 if not core else 0.009), 230)
    m.line([tr(-0.28, 0.59), tr(0.28, 0.59)], S * (0.02 if not core else 0.009), 230)
    a2 = ang * 0.7  # 추는 조금 늦게 따라감
    kx, ky = px_ - 0.7 * S * math.sin(a2), py_ + 0.7 * S * math.cos(a2)
    m.line([tr(0, 0.5), (kx, ky)], S * (0.018 if not core else 0.008), 230)
    m.disc(kx, ky, S * (0.045 if not core else 0.025), 255)
    if f == 2:
        lx, ly = tr(0.31, 0.665)
        star(m, lx, ly, S * (0.16 if not core else 0.11), 0.14, 0, 255)
    if f >= 3:
        # 소리 물결 (양옆 호 — 장마다 퍼지며 흐려짐)
        q = (f - 3) / (frames - 3)
        v = 255 * (1 - q) ** 1.2
        ox, oy = S * 0.5, S * 0.58
        for k in range(2):
            r = S * (0.3 + 0.11 * (f - 3) + 0.09 * k)
            if r > S * 0.49:
                continue
            for side in (0, math.pi):
                pts = [(ox + math.cos(side + a) * r, oy + math.sin(side + a) * r * 0.9) for a in [math.radians(d) for d in range(-35, 36, 5)]]
                m.line(pts, S * (0.025 if not core else 0.01) * (1 - 0.3 * k), v * (1 - 0.35 * k))
    out = m.out()
    return glowify(out, 0.78, 5, 1.25) if not core else out


# ------------------------------------------------------------ 빛의 사슬 (rfx_jchain — 방향으로 누움, 이어 돌림)

def chain_frame(f, frames=4, W=256, H=32, core=False):
    m = Cell(W, H)
    n = 8
    cw = W / n
    cy = H / 2
    for i in range(n):
        cx = (i + 0.5) * cw
        if i % 2 == 0:
            # 옆에서 본 고리 (가로 타원 테)
            pts = [(cx + math.cos(a / 24 * TAU) * cw * 0.6, cy + math.sin(a / 24 * TAU) * H * 0.3) for a in range(25)]
            m.line(pts, H * (0.13 if not core else 0.06), 230)
        else:
            # 모서리로 본 고리 (가로 막대)
            round_line(m, [(cx - cw * 0.55, cy), (cx + cw * 0.55, cy)], H * (0.16 if not core else 0.07), 230)
    # 따라 흐르는 빛 (한 장에 W / 4 — 이어 돌리면 끊김 없이)
    x = (f / frames + 0.125) * W
    if not core:
        m.disc(x, cy, H * 0.45, 255)
    else:
        star(m, x, cy, H * 0.45, 0.2, 0, 255)
    out = m.out()
    return glowify(out, 0.8, 2, 1.2) if not core else out


# ------------------------------------------------------------ 둥근 방패 (rfx_jbuckler — 이어 돌림)

def buckler_frame(f, frames=4, S=128, core=False):
    m = Cell(S, S)
    c = S / 2
    if not core:
        m.disc(c, c, S * 0.4, 80)
    m.ring(c, c, S * 0.43, S * (0.075 if not core else 0.03), 255)
    m.ring(c, c, S * 0.25, S * (0.03 if not core else 0.012), 200)
    for i in range(6):
        a = i * TAU / 6 + math.radians(30)
        m.disc(c + math.cos(a) * S * 0.34, c + math.sin(a) * S * 0.34, S * (0.035 if not core else 0.02), 230)
    m.disc(c, c, S * (0.12 if not core else 0.065), 255)
    # 테를 도는 반짝임 (장마다 90°)
    a = math.radians(f * 90 + 45)
    pts = [(c + math.cos(a + d) * S * 0.43, c + math.sin(a + d) * S * 0.43) for d in [x * 0.05 for x in range(-6, 7)]]
    m.line(pts, S * (0.05 if not core else 0.03), 255)
    star(m, c + math.cos(a) * S * 0.43, c + math.sin(a) * S * 0.43, S * 0.13, 0.18, 0, 255)
    out = m.out()
    return glowify(out, 0.8, 3, 1.2) if not core else out


# ------------------------------------------------------------ 빛의 방패 (rfx_jaegis)

def aegis_outline(W, H, k=1.0):
    """연 모양 방패 (위 가장자리가 조금 볼록 · 아래가 뾰족) — 가운데 (0.5W, 0.5H) 기준 k 배"""
    pts = []
    for i in range(11):
        t = i / 10
        x = 0.14 + 0.72 * t
        y = 0.12 - 0.045 * math.sin(math.pi * t)
        pts.append((x, y))
    right = [(0.88, 0.3), (0.84, 0.48), (0.74, 0.66), (0.62, 0.81), (0.5, 0.95)]
    left = [(1 - x, y) for x, y in right[::-1][1:]]
    pts += right + left
    return [((0.5 + (x - 0.5) * k) * W, (0.5 + (y - 0.5) * k) * H) for x, y in pts]


def aegis_frame(f, frames=6, W=192, H=256, core=False):
    m = Cell(W, H)
    full = aegis_outline(W, H)
    inner = aegis_outline(W, H, 0.84)
    rim = W * (0.045 if not core else 0.016)
    if f == 0:
        # 위 가운데에서 양쪽으로 테가 그려짐
        n = len(full)
        top = 5
        for side in (-1, 1):
            pts = [full[(top + side * i) % n] for i in range(0, n // 2 + 1)]
            m.line(pts[: max(2, int(len(pts) * 0.6))], rim, 255)
        if not core:
            m.poly(full, 50)
    elif f in (1, 2, 3):
        if not core:
            m.poly(full, 170 if f == 1 else 90)
        m.line(full + [full[0]], rim, 255 if f != 3 else 235)
        m.line(inner + [inner[0]], rim * 0.45, 200)
        m.line([(W * 0.5, H * 0.16), (W * 0.5, H * 0.86)], rim * 0.5, 200)
        gx, gy = W * 0.5, H * 0.36
        g = W * 0.07
        m.poly([(gx, gy - g * 1.3), (gx + g, gy), (gx, gy + g * 1.3), (gx - g, gy)], 255)
        if f == 1:
            star(m, gx, gy, W * (0.42 if not core else 0.3), 0.12, 0, 255)
        else:
            # 비스듬히 지나가는 빛 (방패 안만)
            band = Cell(W, H)
            p = 0.25 if f == 2 else 0.7
            x0 = W * (p - 0.3)
            band.poly([(x0, H), (x0 + W * 0.16, H), (x0 + W * 0.66, 0), (x0 + W * 0.5, 0)], 120 if not core else 90)
            clip = Cell(W, H)
            clip.poly(inner, 255)
            m.im = ImageChops.lighter(m.im, ImageChops.multiply(band.im, clip.im))
    else:
        # 금 → 조각
        rnd = random.Random(61)
        gx, gy = W * 0.5, H * 0.36
        cracks = [jagged(rnd, gx, gy, a, W * rnd.uniform(0.3, 0.45), 4, 0.35) for a in (-2.4, -0.7, 0.4, 1.3, 2.0, 2.9)]
        if f == 4:
            if not core:
                m.poly(full, 80)
            m.line(full + [full[0]], rim, 230)
            for cr in cracks:
                m.line(cr, rim * 0.6, 255)
        else:
            # 테를 여섯 조각으로 나눠 바깥으로 밀고 흐리게
            n = len(full)
            for i in range(6):
                seg = [full[(i * n // 6 + j) % n] for j in range(n // 6 + 2)]
                mx = sum(x for x, _ in seg) / len(seg)
                my = sum(y for _, y in seg) / len(seg)
                dx, dy = mx - W * 0.5, my - H * 0.5
                L = math.hypot(dx, dy) or 1
                moved = [(x + dx / L * W * 0.08, y + dy / L * W * 0.08) for x, y in seg]
                moved = rot(moved, mx, my, rnd.uniform(-0.3, 0.3))
                m.line(moved, rim * 0.8, 170)
            for _ in range(10):
                a = rnd.uniform(0, TAU)
                rr = rnd.uniform(0.1, 0.45) * W
                x, y = W * 0.5 + math.cos(a) * rr, H * 0.5 + math.sin(a) * rr * 1.2
                sz = W * 0.025
                m.poly(rot([(x, y - sz), (x + sz * 0.8, y + sz * 0.6), (x - sz * 0.8, y + sz * 0.6)], x, y, rnd.uniform(0, TAU)), 170)
    out = m.out()
    return glowify(out, 0.75, 5, 1.2) if not core else out


# ------------------------------------------------------------ 모두

def main():
    made = {
        "rfx_jthunder": (two_sheets(thunder_frame, 6, 6, "rfx_jthunder", 128, 512, "lookat_y"), 6),
        "rfx_jvolt": (two_sheets(volt_frame, 8, 4, "rfx_jvolt", 192, 192, "direction_z"), 8),
        "rfx_jmaul": (two_sheets(maul_frame, 4, 4, "rfx_jmaul", 192, 256, "lookat_y"), 4),
        "rfx_jwing": (two_sheets(wing_frame, 4, 4, "rfx_jwing", 256, 192, "direction_z"), 4),
        "rfx_jstun": (two_sheets(stun_frame, 8, 4, "rfx_jstun", 128, 64, "rotate_xyz"), 8),
        "rfx_jjudge": (two_sheets(judge_frame, 4, 4, "rfx_jjudge", 64, 64, "rotate_xyz"), 4),
        "rfx_jheal": (two_sheets(heal_frame, 6, 6, "rfx_jheal", 128, 128, "rotate_xyz"), 6),
        "rfx_jnote": (two_sheets(note_frame, 4, 4, "rfx_jnote", 64, 64, "rotate_xyz"), 4),
        "rfx_jstigma": (two_sheets(stigma_frame, 4, 4, "rfx_jstigma", 128, 128, "rotate_xyz"), 4),
        "rfx_jbell": (two_sheets(bell_frame, 8, 4, "rfx_jbell", 192, 192, "lookat_y"), 8),
        "rfx_jchain": (two_sheets(chain_frame, 4, 2, "rfx_jchain", 256, 32, "lookat_direction"), 4),
        "rfx_jbuckler": (two_sheets(buckler_frame, 4, 4, "rfx_jbuckler", 128, 128, "rotate_xyz"), 4),
        "rfx_jaegis": (two_sheets(aegis_frame, 6, 6, "rfx_jaegis", 192, 256, "lookat_y"), 6),
    }
    return made


if __name__ == "__main__":
    main()
